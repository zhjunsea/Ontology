package com.ocean.openlletresolver;

import com.ocean.ontopobdahandler.OBDAHandler;
import com.ocean.ontopobdahandler.ObdaMappingParser;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * 通用本体实例删除服务。
 * <p>
 * 与 InsertService 对称设计：先通过 OWL Reasoner 验证删除操作的语义合法性，
 * 再委托 OBDAHandler 执行物理删除，保证本体一致性与数据库一致性同步。
 */
public class DeleteService {

    private static final Logger log = LoggerFactory.getLogger(DeleteService.class);

    private final BackendService backendService;

    public DeleteService(BackendService backendService) {
        this.backendService = Objects.requireNonNull(backendService, "backendService 不能为null");
    }

    /**
     * 按唯一标识列删除一个本体实例。
     * <p>
     * 流程：构建临时公理 → Reasoner 语义校验 → OBDA 映射解析 → 数据库 DELETE
     *
     * @param typeNS          类型命名空间（如 http://example.org/pizza/components/classes/）
     * @param indNS           个体命名空间（如 http://example.org/pizza/components/individuals/）
     * @param objectPair      用于定位目标行的唯一标识（列名 + 值），如 ("name", "NeapolitanCrustInstance")
     * @param triples         描述待删除个体的三元组列表（必须包含 rdf:type 声明，用于语义校验）
     * @param tableName       目标数据库表名
     * @param targetTopClass  顶级父类 IRI，用于 Reasoner 校验范围约束
     * @throws IllegalArgumentException 参数非法或缺少 rdf:type 时抛出
     * @throws Exception                语义校验失败或数据库执行异常时抛出
     */
    public void deleteComponent(String typeNS, String indNS,
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

        // 加载 OBDA 映射（确保 resolve 可用）
        ObdaMappingParser.load(backendService.getObdaHandler().getObdaPath());

        // ==================== 3. 安全校验 + 数据库删除 ====================
        var dbAction = (com.ocean.ontopobdahandler.GenericDbWriter.DbWriteAction) () -> {
            // 解析唯一标识列的 OBDA 映射，获取真实数据库列名
            // objectPair.columnName() 是业务层传入的逻辑列名（如 "name"）
            // 需确认该列名是否直接对应数据库列，或通过 OBDA 映射转换
            String dbColumnName = objectPair.columnName();

            log.info("[Delete] 执行数据库删除 | table={} | {}='{}'",
                    tableName, dbColumnName, objectPair.objectName());

            OBDAHandler.getInstance().deleteComponent(tableName, dbColumnName, objectPair.objectName());
        };

        // safeVerifyAndDBExecution 内部会：
        //   1. 将 tempAxioms 加入内存本体
        //   2. 用 Reasoner 检查 targetTopClass 的一致性
        //   3. 校验通过后执行 dbAction
        //   4. 无论成功失败都回滚内存本体变更
        backendService.safeVerifyAndDBExecution(tempAxioms, targetTopClass, dbAction);

        log.info("[Delete] ✅ 删除完成 | {}='{}' from {}",
                objectPair.columnName(), objectPair.objectName(), tableName);
    }
}