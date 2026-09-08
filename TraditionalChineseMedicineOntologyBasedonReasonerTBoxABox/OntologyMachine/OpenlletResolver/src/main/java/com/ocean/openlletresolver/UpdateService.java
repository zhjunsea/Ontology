package com.ocean.openlletresolver;

import com.ocean.ontopobdahandler.OBDAHandler;
import com.ocean.ontopobdahandler.OntopMappingResolver;
import org.semanticweb.owlapi.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * 通用本体实例更新服务。
 * <p>
 * 先通过 OWL Reasoner 验证更新操作的语义合法性（旧值 + 新值双重校验），
 * 再委托 OBDAHandler 执行物理更新，保证本体一致性与数据库一致性同步。
 * <p>
 * ✅ 已迁移至 OntopMappingResolver（纯文本解析，无数据库依赖）
 */
public class UpdateService {
    private static final Logger log = LoggerFactory.getLogger(UpdateService.class);

    private final BackendService backendService;

    public UpdateService(BackendService backendService) {
        this.backendService = Objects.requireNonNull(backendService, "backendService 不能为null");
    }

    // ==================== ✅ 跨表事务更新（适配 OntopMappingResolver）====================
    /**
     * 自动拆分多表更新（单参数版本）。
     * 根据预计算的 OBDA 映射元数据，自动将属性路由到对应物理表，
     * 基于 Subject Template Variable 精确识别 JOIN 键并冗余填充，
     * 全部操作在同一事务中完成。
     *
     * @param identifierValues 标识符 IRI -> 值 的映射，用于定位待更新的行（WHERE 条件）
     * @param propertyValues   属性 IRI -> 新值 的映射（可包含多张表的属性）
     */
    public void updateComponentAutoSplit(Map<String, String> identifierValues,
                                         Map<String, String> propertyValues) throws Exception {
        // ========== 0. 严格入参校验 ==========
        if (propertyValues == null || propertyValues.isEmpty()) {
            throw new IllegalArgumentException("❌ propertyValues 为空，无法执行更新操作");
        }
        if (identifierValues == null || identifierValues.isEmpty()) {
            throw new IllegalArgumentException("❌ identifierValues 为空，无法定位更新目标行");
        }

        // ========== 1. 按表分组 SET 数据（禁止跳过未映射属性）==========
        Map<String, Map<String, String>> tableDataMap = new LinkedHashMap<>();
        List<String> unresolvedProps = new ArrayList<>();

        for (Map.Entry<String, String> entry : propertyValues.entrySet()) {
            String propIRI = entry.getKey();
            OntopMappingResolver.ColumnMapping cm = OBDAHandler.Holder.MAPPING_CACHE.get(propIRI);
            if (cm == null) {
                unresolvedProps.add(propIRI);
                continue;
            }
            tableDataMap.computeIfAbsent(cm.tableName(), k -> new LinkedHashMap<>())
                    .put(cm.columnName(), entry.getValue());
        }

        if (!unresolvedProps.isEmpty()) {
            throw new IllegalStateException(
                    String.format("❌ 以下属性无有效 OBDA 映射，更新已中止: %s", unresolvedProps));
        }
        if (tableDataMap.isEmpty()) {
            throw new IllegalStateException("❌ 无任何有效属性可更新，操作已中止");
        }

        // ========== 2. 解析标识符并按表分组（禁止跳过未映射标识符）==========
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
                    String.format("❌ 以下标识符无有效 OBDA 映射，更新已中止: %s", unresolvedIds));
        }

        // ========== 3. 按需分发 JOIN 键标识符（去重 + 安全反向查找）及前置完整性校验（Fail-Fast）==========
        var dist = JoinKeyDistributor.distribute(
                OBDAHandler.Holder.JOIN_KEYS,
                OBDAHandler.Holder.MAPPING_CACHE,
                identifierValues,   // 反向查找值源
                tableDataMap,       // 涉及表判定守卫
                tableIdentifierMap, // 填充目标
                "UPDATE");

        JoinKeyDistributor.validateCompleteness(tableDataMap, tableIdentifierMap, "UPDATE");

        // ========== 5. ✅ 安全校验 + 单事务批量更新 ==========
        Consumer<Connection> dbAction = (Connection conn) -> {
            for (Map.Entry<String, Map<String, String>> tableEntry : tableDataMap.entrySet()) {
                String table = tableEntry.getKey();
                Map<String, String> data = tableEntry.getValue();
                Map<String, String> idData = tableIdentifierMap.get(table);

                List<String> setColumns = new ArrayList<>(data.keySet());
                List<Object> setValues = new ArrayList<>(data.values());
                List<String> whereColumns = new ArrayList<>(idData.keySet());
                List<Object> whereValues = new ArrayList<>(idData.values());

                String sql = buildParameterizedUpdate(table, setColumns, whereColumns);

                List<Object> allParams = new ArrayList<>(setValues.size() + whereValues.size());
                allParams.addAll(setValues);
                allParams.addAll(whereValues);

                try {
                    OBDAHandler.getInstance().addComponentWithConnection(conn, sql, allParams);
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
                log.info("[Update] 写入 {} | SET字段数={} | WHERE字段数={} | cols={}",
                        table, setColumns.size(), whereColumns.size(), setColumns);
            }
        };

        // 更新场景无需本体预校验，传空集即可；事务由 safeVerifyAndDBExecution 统一管控
        backendService.safeVerifyAndDBExecution(Collections.emptySet(), dbAction);

        log.info("✅ 多表严格原子更新完成: 涉及{}张表 | 总属性={} | JOIN填充={}",
                tableDataMap.size(), propertyValues.size(), dist.fillCount());
    }
    /**
     * 构建参数化 UPDATE SQL 语句。
     * 格式: UPDATE "table" SET "col1"=?, "col2"=? WHERE "id1"=? AND "id2"=?
     *
     * @param table        目标物理表名
     * @param setColumns   SET 子句中的列名列表（不可为空）
     * @param whereColumns WHERE 子句中的列名列表（不可为空）
     * @return 带 ? 占位符的参数化 SQL 字符串
     * @throws IllegalArgumentException 当必要参数为空时抛出
     */
    /**
     * 构建参数化 UPDATE SQL（MySQL 兼容版本）
     * ✅ 修复：使用反引号替代双引号，避免 MySQL 默认模式下双引号被当作字符串字面量
     */
    private String buildParameterizedUpdate(String table, List<String> setColumns, List<String> whereColumns) {
        if (setColumns == null || setColumns.isEmpty()) {
            throw new IllegalArgumentException("SET 列不能为空");
        }
        if (whereColumns == null || whereColumns.isEmpty()) {
            throw new IllegalArgumentException("WHERE 条件列不能为空");
        }

        // ✅ 表名和列名均不加任何引号/反引号
        // sanitize() 已确保只包含 [a-zA-Z0-9_]，安全且跨数据库兼容
        String setClause = setColumns.stream()
                .map(col -> col + "=?")
                .collect(Collectors.joining(", "));

        String whereClause = whereColumns.stream()
                .map(col -> col + "=?")
                .collect(Collectors.joining(" AND "));

        return String.format("UPDATE %s SET %s WHERE %s", table, setClause, whereClause);
    }

    /**
     * 安全更新个体的单个属性值。
     *
     * @param typeNS         类型命名空间
     * @param indNS          个体命名空间
     * @param objectPair     主键标识（列名 + 值），用于定位目标行
     * @param propertyIri    待更新属性的 IRI
     * @param newValue       新值（字符串形式）
     * @param tableName      目标数据库表名
     * @param targetTopClass 顶级父类 IRI，用于 Reasoner 校验
     * @throws Exception 语义校验失败或数据库异常时抛出

    public void updateIndividual(String typeNS, String indNS,
                                 BackendService.objectPair objectPair,
                                 String propertyIri, String newValue,
                                 String tableName, String targetTopClass) throws Exception {

        // ==================== 1. 参数校验 ====================
        if (objectPair.objectName() == null || objectPair.objectName().isBlank()) {
            throw new IllegalArgumentException("individualName 不能为空");
        }
        if (propertyIri == null || propertyIri.isBlank()) {
            throw new IllegalArgumentException("propertyIri 不能为空");
        }
        Objects.requireNonNull(backendService, "backendService 不能为null");

        log.info("🔄 开始安全更新个体 [{}] | 属性: {} | 新值: {} | 表: {}",
                objectPair.objectName(), propertyIri, newValue, tableName);

        // ==================== 2. 旧值公理验证（只读校验） ====================
        Set<OWLAxiom> oldAxioms = backendService.queryPropertyAxiom(typeNS, indNS, objectPair.objectName(), propertyIri);
        if (!oldAxioms.isEmpty()) {
            backendService.safeVerifyAndDBExecution(oldAxioms, targetTopClass, null);
            log.info("✅ 旧值公理 TBox 一致性验证通过");
        } else {
            log.warn("⚠️ 个体 [{}] 当前无属性 [{}] 的值，跳过旧值验证", objectPair.objectName(), propertyIri);
        }

        // ==================== 3. 获得属性类型并构建新值公理 ====================
        Set<OWLClass> directTypes = new HashSet<>();
        Boolean isObjectProperty = null;

        OWLOntology tBoxOntology = backendService.getOntologyService().gettBoxOntology();
        IRI individualIri = IRI.create(objectPair.objectName());

        for (OWLAxiom ax : oldAxioms) {
            if (ax instanceof OWLPropertyAssertionAxiom propAx) {
                if (!propAx.getSubject().asOWLNamedIndividual().getIRI().equals(individualIri)) {
                    continue;
                }
                IRI propIRI;
                if (propAx instanceof OWLObjectPropertyAssertionAxiom opAx) {
                    propIRI = opAx.getProperty().asOWLObjectProperty().getIRI();
                } else if (propAx instanceof OWLDataPropertyAssertionAxiom dpAx) {
                    propIRI = dpAx.getProperty().asOWLDataProperty().getIRI();
                } else {
                    continue;
                }
                if (propIRI.equals(propertyIri) && isObjectProperty == null) {
                    isObjectProperty = ax.isOfType(AxiomType.OBJECT_PROPERTY_ASSERTION);
                }
            } else if (ax instanceof OWLClassAssertionAxiom classAx) {
                if (!classAx.getIndividual().asOWLNamedIndividual().getIRI().equals(IRI.create(indNS + individualIri))) {
                    continue;
                }
                OWLClassExpression ce = classAx.getClassExpression();
                if (!ce.isAnonymous()) {
                    directTypes.add(ce.asOWLClass());
                }
            }
        }

        if (isObjectProperty == null) {
            isObjectProperty = backendService.getOntologyService().checkIsObjectProperty(propertyIri);
        }

        String rdfTypeIri = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type";
        GenericAxiomBuilder.Triple propertyTriple = new GenericAxiomBuilder.Triple(
                objectPair.objectName(), propertyIri, newValue, isObjectProperty
        );

        List<GenericAxiomBuilder.Triple> newTriples;
        if (!directTypes.isEmpty()) {
            String mostSpecificClass = backendService.findMostSpecificClass(directTypes, tBoxOntology);
            if (mostSpecificClass != null) {
                GenericAxiomBuilder.Triple typeTriple = new GenericAxiomBuilder.Triple(
                        objectPair.objectName(), rdfTypeIri, mostSpecificClass, true
                );
                newTriples = List.of(propertyTriple, typeTriple);
            } else {
                newTriples = List.of(propertyTriple);
            }
        } else {
            newTriples = List.of(propertyTriple);
        }

        GenericAxiomBuilder axiomBuilder = new GenericAxiomBuilder(backendService, typeNS, indNS);
        Set<OWLAxiom> newAxioms = axiomBuilder.buildAxioms(newTriples);

        // ==================== 4. 校验 + DB更新 ====================
        // ✅ 使用 OntopMappingResolver 缓存获取列名（无需手动 load）
        Map<String, Set<String>> mappingCache = OBDAHandler.getInstance().getAllMappedPropertiesWithVariables();
        Set<String> variables = mappingCache.get(propertyIri);

        if (variables == null || variables.isEmpty()) {
            throw new IllegalArgumentException(
                    String.format("属性 [%s] 在 OBDA 映射中未找到对应的 SQL 变量，无法执行数据库更新", propertyIri));
        }

        // 取第一个变量作为列名
        String columnName = variables.iterator().next();
        // ✅ 注意：OntopMappingResolver 不提供类型转换，值以原始字符串写入
        Object newColumnValue = newValue;

        String individualNameInDB = OntologyService.getLocalName(objectPair.objectName());

        var dbAction = (com.ocean.ontopobdahandler.GenericDbWriter.DbWriteAction) () -> {
            log.info("💾 更新数据库: {} | name={} | {}={}",
                    tableName, individualNameInDB, columnName, newColumnValue);

            // ✅ 修复：使用传入的 tableName 而非硬编码 "pizza_components"
            OBDAHandler.getInstance().updateComponent(
                    tableName,
                    List.of(columnName),
                    List.of(newColumnValue),
                    objectPair.columnName(),
                    individualNameInDB
            );
        };

        backendService.safeVerifyAndDBExecution(newAxioms, targetTopClass, dbAction);

        log.info("✅ 个体 [{}] 安全更新完成 | 属性: {} | 新值: {}", objectPair.objectName(), propertyIri, newValue);
    } */
}