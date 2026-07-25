package com.ocean.openlletresolver;

import com.ocean.ontopobdahandler.OBDAHandler;
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