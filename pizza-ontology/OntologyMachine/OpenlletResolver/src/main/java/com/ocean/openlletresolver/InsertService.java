package com.ocean.openlletresolver;

import com.ocean.ontopobdahandler.OBDAHandler;
import com.ocean.ontopobdahandler.OntopMappingResolver;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * 通用本体实例插入服务。
 * <p>
 * 先通过 OWL Reasoner 验证插入操作的语义合法性，
 * 再委托 OBDAHandler 执行物理写入，保证本体一致性与数据库一致性同步。
 * <p>
 * ✅ 已迁移至 OntopMappingResolver（纯文本解析，无数据库依赖）
 */
public class InsertService {

    private static final Logger log = LoggerFactory.getLogger(InsertService.class);

    private final BackendService backendService;

    public InsertService(BackendService backendService) {
        this.backendService = Objects.requireNonNull(backendService, "backendService 不能为null");
    }

    // ==================== ✅ 跨表事务写入（适配 OntopMappingResolver）====================
    /**
     * 自动拆分多表写入（单参数版本）。
     * 根据预计算的 OBDA 映射元数据，自动将属性路由到对应物理表，
     * 基于 Subject Template Variable 精确识别 JOIN 键并冗余填充，
     * 全部操作在同一事务中完成。
     *
     * @param propertyValues 属性 IRI -> 值 的映射（可包含多张表的属性）
     */
    public void insertComponentAutoSplit(Map<String, String> propertyValues) {
        if (propertyValues == null || propertyValues.isEmpty()) {
            log.warn("⚠️ propertyValues 为空，跳过写入");
            return;
        }

        // ========== 1. 按表分组（使用结构化 ColumnMapping 缓存）==========
        Map<String, Map<String, String>> tableDataMap = new LinkedHashMap<>();
        List<String> unresolved = new ArrayList<>();

        for (Map.Entry<String, String> entry : propertyValues.entrySet()) {
            String propIRI = entry.getKey();
            String value = entry.getValue();

            OntopMappingResolver.ColumnMapping cm = OBDAHandler.Holder.MAPPING_CACHE.get(propIRI);
            if (cm == null) {
                unresolved.add(propIRI);
                continue;
            }

            tableDataMap.computeIfAbsent(cm.tableName(), k -> new LinkedHashMap<>())
                    .put(cm.columnName(), value);
        }

        if (!unresolved.isEmpty()) {
            log.warn("⚠️ 以下属性无有效 OBDA 映射，已跳过: {}", unresolved);
        }
        if (tableDataMap.isEmpty()) {
            log.warn("⚠️ 无任何有效属性可写入，终止操作");
            return;
        }

        // ========== 2. 基于 OBDA Subject Variable 精确填充 JOIN 键 ==========
        int joinKeyFillCount = 0;
        for (OntopMappingResolver.JoinKeyInfo jk : OBDAHandler.Holder.JOIN_KEYS) {
            // Step A: 从待写入数据中找到该 JOIN 键的值
            String joinValue = null;
            for (String tableCol : jk.tableColumns()) {
                String[] parts = tableCol.split("\\.", 2);
                Map<String, String> tableData = tableDataMap.get(parts[0]);
                if (tableData != null && tableData.containsKey(parts[1])) {
                    joinValue = tableData.get(parts[1]);
                    break;
                }
            }

            // Step B: 将该值冗余填充到所有缺少此列的相关表
            if (joinValue != null) {
                for (String tableCol : jk.tableColumns()) {
                    String[] parts = tableCol.split("\\.", 2);
                    String tbl = parts[0];
                    String col = parts[1];

                    Map<String, String> tableData = tableDataMap.computeIfAbsent(tbl, k -> new LinkedHashMap<>());
                    if (tableData.putIfAbsent(col, joinValue) == null) {
                        joinKeyFillCount++;
                    }
                }
            }
        }

        if (joinKeyFillCount > 0) {
            log.info("🔗 JOIN 键自动填充: {}个字段被冗余写入相关表", joinKeyFillCount);
        }

        // ========== 3. 单事务批量写入所有表 ==========
        backendService.getObdaHandler().executeInTransaction(conn -> {
            for (Map.Entry<String, Map<String, String>> tableEntry : tableDataMap.entrySet()) {
                String table = tableEntry.getKey();
                Map<String, String> data = tableEntry.getValue();

                if (data.isEmpty()) continue;

                List<String> columns = new ArrayList<>(data.keySet());
                List<Object> values = new ArrayList<>(data.values());
                String sql = backendService.getObdaHandler().buildParameterizedInsert(table, columns);
                backendService.getObdaHandler().executeUpdate(conn, sql, values.toArray());

                log.info("📊 事务内写入成功: table={} | cols={}", table, columns);
            }
        });

        log.info("✅ 多表自动拆分写入完成: 涉及{}张表 | 总属性={} | JOIN填充={} | 未解析={}",
                tableDataMap.size(), propertyValues.size(), joinKeyFillCount, unresolved.size());
    }

    /**
     * 插入一个本体实例到指定数据库表。
     *
     * @param typeNS         类型命名空间
     * @param indNS          个体命名空间
     * @param objectPair     主键标识（列名 + 值）
     * @param triples        描述个体的三元组列表（必须包含 rdf:type）
     * @param tableName      目标数据库表名
     * @param targetTopClass 顶级父类 IRI，用于 Reasoner 校验
     * @throws Exception 语义校验失败或数据库异常时抛出
     */
    public void insertComponent(String typeNS, String indNS,
                                BackendService.objectPair objectPair,
                                List<GenericAxiomBuilder.Triple> triples,
                                String tableName, String targetTopClass) throws Exception {

        // ==================== 1. 参数校验 ====================
        if (objectPair.objectName() == null || objectPair.objectName().isBlank()) {
            throw new IllegalArgumentException("objectPair.objectName() 不能为空");
        }
        if (triples == null || triples.isEmpty()) {
            throw new IllegalArgumentException("triples 不能为空");
        }
        Objects.requireNonNull(backendService, "backendService 不能为null");

        Optional<GenericAxiomBuilder.Triple> matchedTriple = triples.stream()
                .filter(t -> "rdf:type".equals(t.predicate()) && !t.isObjectProperty())
                .findFirst();

        if (matchedTriple.isEmpty()) {
            throw new IllegalArgumentException("triples 中必须包含至少一条合法的 rdf:type 声明");
        }

        String individualType = matchedTriple.get().object();
        log.info("[Insert] 准备插入 | subject={} | type={} | table={} | key={}={}",
                matchedTriple.get().subject(), individualType,
                tableName, objectPair.columnName(), objectPair.objectName());

        // ==================== 2. 构建临时公理用于语义校验 ====================
        GenericAxiomBuilder axiomBuilder = new GenericAxiomBuilder(backendService, typeNS, indNS);
        Set<OWLAxiom> tempAxioms = axiomBuilder.buildAxioms(triples);

        // ✅ OntopMappingResolver 已由 OBDAHandler.Holder 自动加载，无需手动调用
        // 访问 getObdaPath() 可确保 Holder 已完成初始化
        String obdaPath = backendService.getObdaHandler().getObdaPath();
        log.debug("[Insert] OBDA 映射已由 OntopMappingResolver 加载 | path={}", obdaPath);

        // ==================== 3. 安全校验 + 数据库写入 ====================
        var dbAction = (com.ocean.ontopobdahandler.GenericDbWriter.DbWriteAction) () -> {
            Map<String, Object> rowData = new LinkedHashMap<>();
            // 主键字段始终写入
            rowData.put(objectPair.columnName(), objectPair.objectName());

            // 获取缓存的映射关系（属性IRI → SQL变量集合）
            Map<String, Set<String>> mappingCache = OBDAHandler.getInstance().getAllMappedPropertiesWithVariables();

            for (GenericAxiomBuilder.Triple t : triples) {
                // 跳过 rdf:type，它不对应数据库列
                if ("rdf:type".equals(t.predicate())) continue;

                Set<String> variables = mappingCache.get(t.predicate());
                if (variables == null || variables.isEmpty()) {
                    log.warn("[Insert] ⚠️ 属性无 OBDA 映射，已跳过 | predicate={}", t.predicate());
                    continue;
                }

                // 取第一个变量作为列名（标准 OBDA 映射中属性通常对应单一变量）
                String columnName = variables.iterator().next();
                // ✅ 注意：OntopMappingResolver 不提供类型转换，值以原始字符串写入
                // 类型适配由数据库 JDBC 驱动或 Ontop 运行时在查询侧处理
                rowData.put(columnName, t.object());
            }

            log.info("[Insert] 写入 {} | name={} | 字段数={}", tableName, objectPair.objectName(), rowData.size());

            List<String> columns = new ArrayList<>(rowData.keySet());
            List<Object> values = new ArrayList<>(rowData.values());
            OBDAHandler.getInstance().addComponent(tableName, columns, values);
        };

        backendService.safeVerifyAndDBExecution(tempAxioms, targetTopClass, dbAction);

        log.info("[Insert] ✅ 插入完成 | {}='{}' into {}",
                objectPair.columnName(), objectPair.objectName(), tableName);
    }
}