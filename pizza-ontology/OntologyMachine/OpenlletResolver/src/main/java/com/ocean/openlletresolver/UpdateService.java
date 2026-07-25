package com.ocean.openlletresolver;

import com.ocean.ontopobdahandler.OBDAHandler;
import org.semanticweb.owlapi.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

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
     */
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
    }
}