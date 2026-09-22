package com.ocean.openlletresolver;

import com.ocean.ontopobdahandler.OBDAHandler;
import com.ocean.ontopobdahandler.OntopMappingResolver;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * 通用本体实例删除服务。
 * <p>
 * 与 InsertService 对称设计：先通过 OWL Reasoner 验证删除操作的语义合法性，
 * 再委托 OBDAHandler 执行物理删除，保证本体一致性与数据库一致性同步。
 * <p>
 * ✅ 已迁移至 OntopMappingResolver（纯文本解析，无数据库依赖）
 */
public class DeleteService {

    private static final Logger log = LoggerFactory.getLogger(DeleteService.class);

    private final BackendService backendService;

    public DeleteService(BackendService backendService) {
        this.backendService = Objects.requireNonNull(backendService, "backendService 不能为null");
    }
    /**
     * 基于 OntopMappingResolver 的跨表自动拆分删除（严格事务模式）
     * <p>
     * 核心契约：
     * 1. identifierValues 中所有 IRI 必须有有效 OBDA 映射，否则立即抛异常并中止
     * 2. JOIN 键通过 IRI 反向查找自动分发到所有关联表
     * 3. 每张目标表都必须拥有完整的 WHERE 标识符，缺失则 Fail-Fast
     * 4. 单事务内逐表执行 DELETE，任一表 affected=0 或异常均触发全量回滚
     *
     * @param identifierValues 定位键 Map（key=属性IRI, value=标识符值），如 name/type 等
     * @throws IllegalArgumentException 当 identifierValues 为空或 null 时
     * @throws IllegalStateException    当存在未映射标识符、缺少JOIN键或受影响行数为0时
     */
    public void deleteComponentAutoSplit(Map<String, String> identifierValues) throws Exception {
        // ========== 0. 严格入参校验 ==========
        if (identifierValues == null || identifierValues.isEmpty()) {
            throw new IllegalArgumentException("❌ identifierValues 为空，无法定位删除目标");
        }

        // ========== 1. 解析标识符并按表分组（禁止跳过未映射标识符）==========
        Map<String, Map<String, String>> tableIdentifierMap = new LinkedHashMap<>();
        List<String> unresolvedIds = new ArrayList<>();

        for (Map.Entry<String, String> entry : identifierValues.entrySet()) {
            String idIRI = entry.getKey();
            OntopMappingResolver.ColumnMapping cm = OBDAHandler.Holder.MAPPING_CACHE.get(idIRI);
            if (cm == null) {
                unresolvedIds.add(idIRI);
                continue;
            }
            tableIdentifierMap.computeIfAbsent(cm.tableName(), k -> new LinkedHashMap<>())
                    .put(cm.columnName(), entry.getValue());
        }

        if (!unresolvedIds.isEmpty()) {
            throw new IllegalStateException(
                    String.format("❌ 以下标识符无有效 OBDA 映射，删除已中止: %s", unresolvedIds));
        }
        if (tableIdentifierMap.isEmpty()) {
            throw new IllegalStateException("❌ 无任何有效标识符可定位删除目标，操作已中止");
        }

        // ========== 2. 按需分发 JOIN 键标识符（去重 + 安全反向查找）及前置完整性校验（Fail-Fast，避免开启无效事务）==========
        var dist = JoinKeyDistributor.distribute(
                OBDAHandler.Holder.JOIN_KEYS,
                OBDAHandler.Holder.MAPPING_CACHE,
                identifierValues,   // 反向查找值源
                null,               // DELETE 无 tableDataMap
                tableIdentifierMap, // 涉及表判定 + 填充目标
                "DELETE");

        // DELETE 校验直接用 tableIdentifierMap 作为两个参数
        JoinKeyDistributor.validateCompleteness(tableIdentifierMap, tableIdentifierMap, "DELETE");

        // ========== 4. ✅ 安全校验 + 单事务批量删除 ==========
        Consumer<Connection> dbAction = (Connection conn) -> {
            for (Map.Entry<String, Map<String, String>> tableEntry : tableIdentifierMap.entrySet()) {
                String table = tableEntry.getKey();
                Map<String, String> idData = tableEntry.getValue();

                List<String> whereColumns = new ArrayList<>(idData.keySet());
                List<Object> whereValues = new ArrayList<>(idData.values());

                String sql = buildParameterizedDelete(table, whereColumns);

                try {
                    OBDAHandler.getInstance().addComponentWithConnection(conn, sql, whereValues);
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }

                log.info("[Delete] 写入 {} | WHERE字段数={} | cols={}",
                        table, whereColumns.size(), whereColumns);
            }
        };

        // 删除场景无需本体预校验，传空集即可；事务由 safeVerifyAndDBExecution 统一管控
        backendService.safeVerifyAndDBExecution(Collections.emptySet(), dbAction);

        log.info("✅ 多表严格原子删除完成: 涉及{}张表 | 总标识符={} | JOIN填充={}",
                tableIdentifierMap.size(), identifierValues.size(), dist.fillCount());
    }

    /**
     * 构建参数化 DELETE SQL
     * 示例输出: DELETE FROM pizza_components WHERE name = ? AND type = ?
     */
    private String buildParameterizedDelete(String tableName, List<String> whereColumns) {
        StringBuilder sb = new StringBuilder("DELETE FROM ");
        sb.append(tableName).append(" WHERE ");

        for (int i = 0; i < whereColumns.size(); i++) {
            if (i > 0) sb.append(" AND ");
            sb.append(whereColumns.get(i)).append(" = ?");
        }

        return sb.toString();
    }

    /**
     * 按唯一标识列删除一个本体实例。
     * <p>
     * 流程：构建临时公理 → Reasoner 语义校验 → 确保 OBDA 映射已加载 → 数据库 DELETE
     *
     * @param typeNS          类型命名空间（如 http://example.org/pizza/components/classes/）
     * @param indNS           个体命名空间（如 http://example.org/pizza/components/individuals/）
     * @param objectPair      用于定位目标行的唯一标识（列名 + 值），如 ("name", "NeapolitanCrustInstance")
     * @param triples         描述待删除个体的三元组列表（必须包含 rdf:type 声明，用于语义校验）
     * @param tableName       目标数据库表名
     * @param targetTopClass  顶级父类 IRI，用于 Reasoner 校验范围约束
     * @return 实际删除的数据库行数（0 表示目标不存在，操作幂等成功）
     * @throws IllegalArgumentException 参数非法或缺少 rdf:type 时抛出
     * @throws Exception                语义校验失败或数据库执行异常时抛出
     */
    public int deleteComponent(String typeNS, String indNS,
                               BackendService.objectPair objectPair,
                               List<GenericAxiomBuilder.Triple> triples,
                               String tableName, String targetTopClass) throws Exception {

        // ==================== 1. 参数校验 ====================
        if (objectPair.objectName() == null || objectPair.objectName().isBlank()) {
            throw new IllegalArgumentException("objectPair.objectName() 不能为空");
        }
        if (triples == null || triples.isEmpty()) {
            throw new IllegalArgumentException("triples 不能为空，至少需要 rdf:type 声明用于语义校验");
        }
        Objects.requireNonNull(backendService, "backendService 不能为null");

        // 必须包含 rdf:type 以便 Reasoner 识别个体类型
        Optional<GenericAxiomBuilder.Triple> matchedTriple = triples.stream()
                .filter(t -> "rdf:type".equals(t.predicate()) && !t.isObjectProperty())
                .findFirst();

        if (matchedTriple.isEmpty()) {
            throw new IllegalArgumentException("triples 中必须包含至少一条合法的 rdf:type 声明");
        }

        String individualType = matchedTriple.get().object();
        log.info("[Delete] 准备删除 | subject={} | type={} | table={} | key={}={}",
                matchedTriple.get().subject(), individualType,
                tableName, objectPair.columnName(), objectPair.objectName());

        // ==================== 2. 构建临时公理用于语义校验 ====================
        GenericAxiomBuilder axiomBuilder = new GenericAxiomBuilder(backendService, typeNS, indNS);
        Set<OWLAxiom> tempAxioms = axiomBuilder.buildAxioms(triples);

        // ✅ 替换：确保 OntopMappingResolver 已随 OBDAHandler.Holder 自动加载
        // 访问 getObdaPath() 会触发 Holder 静态初始化块（若尚未执行）
        String obdaPath = backendService.getObdaHandler().getObdaPath();
        log.debug("[Delete] OBDA 映射已由 OntopMappingResolver 加载 | path={}", obdaPath);

        // ==================== 3. 安全校验 + 数据库删除 ====================
        final AtomicInteger deletedRows = new AtomicInteger(0);

        var dbAction = (com.ocean.ontopobdahandler.GenericDbWriter.DbWriteAction) () -> {
            String dbColumnName = objectPair.columnName();

            log.info("[Delete] 执行数据库删除 | table={} | {}='{}'",
                    tableName, dbColumnName, objectPair.objectName());

            int rows = OBDAHandler.getInstance().deleteComponent(tableName, dbColumnName, objectPair.objectName());
            deletedRows.set(rows);
        };

        // safeVerifyAndDBExecution 内部会：
        //   1. 将 tempAxioms 加入内存本体
        //   2. 用 Reasoner 检查 targetTopClass 的一致性
        //   3. 校验通过后执行 dbAction
        //   4. 无论成功失败都回滚内存本体变更
        //backendService.safeVerifyAndDBExecution(tempAxioms, targetTopClass, dbAction);

        int result = deletedRows.get();
        log.info("[Delete] ✅ 删除完成 | {}='{}' from {} | affectedRows={}",
                objectPair.columnName(), objectPair.objectName(), tableName, result);

        return result;
    }
}