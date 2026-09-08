package com.ocean.openlletresolver;

import com.clarkparsia.owlapi.explanation.BlackBoxExplanation;
import openllet.owlapi.OpenlletReasonerFactory;
import org.semanticweb.owl.explanation.api.Explanation;
import org.semanticweb.owl.explanation.api.ExplanationGenerator;
import org.semanticweb.owl.explanation.api.ExplanationGeneratorFactory;
import org.semanticweb.owl.explanation.impl.blackbox.checker.InconsistentOntologyExplanationGeneratorFactory;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.reasoner.*;
import org.semanticweb.owlapi.vocab.OWLFacet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * OWL 本体推理服务
 * 提供安全的推理器生命周期管理、一致性校验以及通用的查询方法
 */
public class ReasonerService {

    private static final Logger log = LoggerFactory.getLogger(ReasonerService.class);

    private final OWLDataFactory dataFactory;
    private final OWLReasonerFactory factory;
    private OWLReasoner reasoner;

    public ReasonerService(OntologyService ontologySrv) {
        this.factory = new OpenlletReasonerFactory();
        this.dataFactory = ontologySrv.getDataFactory();
        this.reasoner = factory.createReasoner(ontologySrv.gettBoxOntology());
        this.reasoner.flush();
        this.reasoner.precomputeInferences(
                InferenceType.CLASS_HIERARCHY,
                InferenceType.OBJECT_PROPERTY_HIERARCHY,
                InferenceType.DATA_PROPERTY_HIERARCHY,
                InferenceType.DATA_PROPERTY_ASSERTIONS,
                InferenceType.DIFFERENT_INDIVIDUALS,
                InferenceType.CLASS_ASSERTIONS,
                InferenceType.DISJOINT_CLASSES,
                InferenceType.OBJECT_PROPERTY_ASSERTIONS,
                InferenceType.SAME_INDIVIDUAL
        );

        if (!reasoner.isConsistent()) {
            ExplainInconsistencyWithOWLExplanation(ontologySrv);
        }
    }

    // ==================== Getters / Setters ====================

    public OWLReasonerFactory getFactory() { return factory; }
    public OWLReasoner getReasoner() { return reasoner; }
    public void setReasoner(OWLReasoner reasoner) { this.reasoner = reasoner; }

    // ==================== 一致性解释：OWL Explanation API ====================

    public void ExplainInconsistencyWithOWLExplanation(OntologyService ontologySrv) {
        log.error("本体不一致！开始生成冲突解释...");
        try {
            Supplier<OWLOntologyManager> managerSupplier = ontologySrv::getManager;
            ExplanationGeneratorFactory<OWLAxiom> genFac =
                    new InconsistentOntologyExplanationGeneratorFactory(
                            factory, dataFactory, managerSupplier, 5000);
            ExplanationGenerator<OWLAxiom> gen =
                    genFac.createExplanationGenerator(ontologySrv.gettBoxOntology());

            OWLAxiom inconsistencyAxiom = dataFactory.getOWLSubClassOfAxiom(
                    dataFactory.getOWLThing(), dataFactory.getOWLNothing());

            Set<Explanation<OWLAxiom>> explanations = gen.getExplanations(inconsistencyAxiom, 5);
            int idx = 0;
            for (Explanation<OWLAxiom> exp : explanations) {
                log.error("--- 冲突解释 #{} ---", ++idx);
                exp.getAxioms().forEach(a -> log.error("  → {}", a));
            }
            if (explanations.isEmpty()) {
                log.error("未生成任何解释，请检查本体或增加解释数量限制。");
            }
        } catch (Exception e) {
            log.error("生成解释时发生异常：{}", e.getMessage(), e);
        }
        throw new InconsistentOntologyException("本体不一致，已输出冲突解释（见上方），推理终止。");
    }

    // ==================== 一致性解释：BlackBox + 语法预扫描 ====================

    /**
     * 诊断本体不一致原因。
     * ✅ 复用类成员 reasoner，不再新建实例。
     * ✅ 优先执行语法级扫描，精确定位 DatatypeRestriction 违规后提前返回。
     */
    public void ExplainInconsistencyWithBlackBoxExplanation(OWLOntology ontology) {
        log.error("\n🔍 [诊断] 正在分析不一致原因...");

        // ⭐ 调用链追踪，确认代码确实执行到此处
        log.error("🔎 [调试-入口] 准备调用 findSyntaxLevelViolations, ontology={}",
                ontology != null ? "非空(axioms=" + ontology.getAxiomCount() + ")" : "NULL");

        // ====== 第一步：语法级预扫描（不依赖推理器状态）======
        List<String> datatypeViolations;
        try {
            datatypeViolations = findSyntaxLevelViolations(ontology);
            log.error("🔎 [调试-返回] findSyntaxLevelViolations 返回 {} 条违规", datatypeViolations.size());
        } catch (Exception e) {
            log.error("❌ [调试-异常] findSyntaxLevelViolations 执行失败!", e);
            throw e;
        }

        if (!datatypeViolations.isEmpty()) {
            // ⭐ 明确打印命中详情
            log.error("🎯 [诊断-命中] 数据类型违规已定位! 共 {} 条：", datatypeViolations.size());
            datatypeViolations.forEach(v -> log.error("   → {}", v));
            log.error("💡 此类不一致由数据值超出范围引起，请修正 ABox 字面量或调整 TBox 约束。");
            return; // ← 已精确定位，跳过 BlackBox
        }

        // ====== 第二步：BlackBox 逻辑矛盾解释（复用 this.reasoner）======
        log.error("ℹ️ 未发现数据类型违规，尝试 BlackBox 逻辑矛盾解释...");
        BlackBoxExplanation explainer = null;
        try {
            explainer = new BlackBoxExplanation(ontology, factory, reasoner);
            Set<OWLAxiom> inconsistentAxioms = explainer.getExplanation(dataFactory.getOWLThing());

            if (inconsistentAxioms == null || inconsistentAxioms.isEmpty()) {
                log.error("⚠️ 推理器报告不一致，但 BlackBox 未提取到具体公理");
                log.error("   可能原因: 隐式推导链过长或 Openllet 对当前矛盾类型支持有限");
            } else {
                log.error("❌ [BlackBox] 发现 {} 条导致矛盾的公理：", inconsistentAxioms.size());
                inconsistentAxioms.forEach(a -> log.error("   → {}", a));
            }
        } catch (InconsistentOntologyException e) {
            log.error("⚠️ BlackBox 因本体不一致状态无法运行: {}", e.getMessage());
        } catch (Exception e) {
            log.error("⚠️ 解释器执行异常: {}", e.getMessage(), e);
        } finally {
            if (explainer != null) {
                try { explainer.dispose(); } catch (Exception ignored) {}
            }
        }
    }

    // ==================== 语法级违规扫描（核心增强）====================

    /**
     * 在不启动推理器的情况下，静态扫描本体中的三类常见违规：
     * 1. DataPropertyAssertion 违反 DatatypeRestriction（allValuesFrom + min/maxInclusive）
     * 2. Functional DataProperty 多值冲突
     * 3. Named Individual 同时属于 Disjoint Classes
     */
    private List<String> findSyntaxLevelViolations(OWLOntology ontology) {
        List<String> violations = new ArrayList<>();

        // 预提取所有 DatatypeRestriction 约束，避免在内层循环重复遍历
        Map<IRI, List<DatatypeConstraint>> propertyConstraints = extractDatatypeConstraints(ontology);

        for (OWLAxiom axiom : ontology.getAxioms()) {

            // 1️⃣ DatatypeRestriction 违规（allValuesFrom + facet）
            if (axiom instanceof OWLDataPropertyAssertionAxiom dpaa) {
                OWLDataPropertyExpression propExpr = dpaa.getProperty();
                if (propExpr instanceof OWLDataProperty prop) {
                    List<DatatypeConstraint> constraints = propertyConstraints.get(prop.getIRI());
                    if (constraints != null) {
                        // ⭐ 获取该个体实际声明的所有类名（shortForm）
                        Set<String> declaredTypes = ontology.getClassAssertionAxioms(dpaa.getSubject())
                                .stream()
                                .map(OWLClassAssertionAxiom::getClassExpression)
                                .filter(OWLClassExpression::isOWLClass)
                                .map(ce -> ce.asOWLClass().getIRI().getShortForm())
                                .collect(Collectors.toSet());

                        // ⭐ 未声明类型时退化为全量校验（兜底），避免漏报
                        boolean noDeclaredType = declaredTypes.isEmpty();

                        OWLLiteral lit = dpaa.getObject();
                        for (DatatypeConstraint c : constraints) {
                            // ⭐ 仅校验该个体声明类型对应的约束，跳过无关类
                            if (!noDeclaredType && !declaredTypes.contains(c.sourceClassName)) {
                                continue;
                            }
                            String err = c.validate(lit, dpaa.getSubject(), prop.getIRI());
                            if (err != null) violations.add(err);
                        }
                    }
                }
            }

            // 2️⃣ Functional Property 多值冲突
            if (axiom instanceof OWLDataPropertyAssertionAxiom dpaa) {
                OWLDataPropertyExpression propExpr = dpaa.getProperty();
                if (propExpr instanceof OWLDataProperty prop) {
                    boolean isFunctional = ontology.getFunctionalDataPropertyAxioms(prop).stream()
                            .anyMatch(a -> a.getProperty().equals(prop));
                    if (isFunctional) {
                        long count = ontology.getDataPropertyAssertionAxioms(dpaa.getSubject()).stream()
                                .filter(a -> a.getProperty().equals(prop))
                                .count();
                        if (count > 1) {
                            violations.add(String.format("[Functional] %s 是函数型属性，但个体 %s 有 %d 个值",
                                    prop.getIRI().getShortForm(), dpaa.getSubject().toStringID(), count));
                        }
                    }
                }
            }

            // 3️⃣ Disjoint Classes 冲突
            if (axiom instanceof OWLClassAssertionAxiom caa && caa.getIndividual().isNamed()) {
                OWLNamedIndividual ind = caa.getIndividual().asOWLNamedIndividual();
                Set<OWLClass> assertedClasses = ontology.getClassAssertionAxioms(ind).stream()
                        .map(OWLClassAssertionAxiom::getClassExpression)
                        .filter(OWLClassExpression::isOWLClass)
                        .map(OWLClassExpression::asOWLClass)
                        .collect(Collectors.toSet());

                for (OWLDisjointClassesAxiom dca : ontology.getAxioms(AxiomType.DISJOINT_CLASSES)) {
                    Set<OWLClass> disjointSet = dca.classExpressions()
                            .filter(OWLClassExpression::isOWLClass)
                            .map(OWLClassExpression::asOWLClass)
                            .collect(Collectors.toSet());

                    Set<OWLClass> intersection = new HashSet<>(assertedClasses);
                    intersection.retainAll(disjointSet);
                    if (intersection.size() >= 2) {
                        violations.add(String.format("[Disjoint] 个体 %s 同时属于互斥类: %s",
                                ind.getIRI().getShortForm(), intersection));
                    }
                }
            }
        }
        return violations;
    }

    // ==================== DatatypeRestriction 提取与校验 ====================

    /**
     * 从本体中提取所有 SubClassOf 公理里的数据类型约束
     * 同时支持 OWLDataAllValuesFrom（数据属性）和 OWLObjectAllValuesFrom（对象属性）
     */
    private Map<IRI, List<DatatypeConstraint>> extractDatatypeConstraints(OWLOntology ontology) {
        Map<IRI, List<DatatypeConstraint>> constraintMap = new HashMap<>();

        for (OWLSubClassOfAxiom subAx : ontology.getAxioms(AxiomType.SUBCLASS_OF)) {
            OWLClassExpression superExpr = subAx.getSuperClass();

            OWLDataProperty dataProp = null;
            OWLDataRange dataRange = null;

            // ⭐ 分别处理数据属性和对象属性的 allValuesFrom
            if (superExpr instanceof OWLDataAllValuesFrom dataAllValues) {
                dataProp = dataAllValues.getProperty().asOWLDataProperty();
                dataRange = dataAllValues.getFiller();
            } else if (superExpr instanceof OWLObjectAllValuesFrom objAllValues) {
                if (objAllValues.getProperty() instanceof OWLDataProperty dp
                        && objAllValues.getFiller() instanceof OWLDataRange dr) {
                    dataProp = dp;
                    dataRange = dr;
                }
            }

            if (dataProp == null || dataRange == null) {
                continue;
            }

            OWLDatatypeRestriction restriction = resolveDatatypeRestriction(dataRange, ontology);
            if (restriction == null) {
                log.warn("⚠️ 无法从 filler [{}] 中解析出 DatatypeRestriction, 属性={}",
                        dataRange.getClass().getSimpleName(),
                        dataProp.getIRI().getShortForm());
                continue;
            }

            String sourceClass = subAx.getSubClass().isOWLClass()
                    ? subAx.getSubClass().asOWLClass().getIRI().getShortForm()
                    : "Anonymous";

            DatatypeConstraint constraint = DatatypeConstraint.from(restriction, sourceClass);
            if (constraint != null) {
                constraintMap.computeIfAbsent(dataProp.getIRI(), k -> new ArrayList<>()).add(constraint);
            }
        }

        // ⭐ 诊断日志：确认提取结果
        if (constraintMap.isEmpty()) {
            log.info("ℹ️ 未从本体中提取到任何 DatatypeRestriction 约束");
        } else {
            constraintMap.forEach((iri, constraints) ->
                    log.info("🔎 [诊断-提取] 属性={} 共提取 {} 条约束: {}",
                            iri.getShortForm(),
                            constraints.size(),
                            constraints));
        }

        return constraintMap;
    }

    /**
     * ⭐ 核心解包方法：从各种可能的 OWLDataRange 实现中提取 OWLDatatypeRestriction
     */
    private OWLDatatypeRestriction resolveDatatypeRestriction(OWLDataRange filler, OWLOntology ontology) {
        // 情况1: 直接就是 DatatypeRestriction
        if (filler instanceof OWLDatatypeRestriction restriction) {
            return restriction;
        }

        // 情况2: filler 是 OWLDatatype（包括匿名内联的 rdfs:Datatype）
        if (filler instanceof OWLDatatype dt) {
            // 2a: 尝试通过 DatatypeDefinition 公理查找
            for (OWLDatatypeDefinitionAxiom defAx : ontology.getDatatypeDefinitions(dt)) {
                if (defAx.getDataRange() instanceof OWLDatatypeRestriction restriction) {
                    return restriction;
                }
            }

            // 2b: 检查该 OWLDatatype 本身是否携带 facet 信息
            if (!dt.isBuiltIn()) {
                for (OWLSubClassOfAxiom subAx : ontology.getAxioms(AxiomType.SUBCLASS_OF)) {
                    OWLClassExpression superExpr = subAx.getSuperClass();
                    if (superExpr instanceof OWLDataAllValuesFrom avf
                            && avf.getFiller() instanceof OWLDatatypeRestriction restriction
                            && restriction.getDatatype().equals(dt)) {
                        return restriction;
                    }
                }
            }
        }

        log.warn("⚠️ 无法从 filler [{}] 中解析出 DatatypeRestriction",
                filler.getClass().getSimpleName());
        return null;
    }

    // ==================== ⭐ 安全数值解析工具方法 ====================

    /**
     * 安全地从 OWLLiteral 中提取 double 值。
     * 兼容 xsd:float / xsd:double / xsd:decimal / xsd:integer 等所有数值类型。
     *
     * OWL API 5.x 的 parseDouble() 对 xsd:float 类型会抛出 NumberFormatException，
     * 即使其词法值是合法的数值字符串。此方法通过 fallback 到词法值手动解析来规避该问题。
     */
    private static double safeParseDouble(OWLLiteral literal) {
        try {
            return literal.parseDouble();
        } catch (NumberFormatException e) {
            // fallback: 通过词法值手动解析（兼容 xsd:float / xsd:decimal / xsd:integer）
            return Double.parseDouble(literal.getLiteral());
        }
    }

    // ==================== DatatypeConstraint 内部类 ====================

    /**
     * 封装单个 DatatypeRestriction 的边界校验
     */
    private static class DatatypeConstraint {
        final double min, max;
        final boolean hasMin, hasMax;
        final String sourceClassName;

        private DatatypeConstraint(double min, boolean hasMin, double max, boolean hasMax, String sourceClass) {
            this.min = min; this.hasMin = hasMin;
            this.max = max; this.hasMax = hasMax;
            this.sourceClassName = sourceClass;
        }

        static DatatypeConstraint from(OWLDatatypeRestriction restriction, String sourceClass) {
            Double min = null, max = null;
            for (OWLFacetRestriction facet : restriction.getFacetRestrictions()) {
                // ⭐ 修复：使用 safeParseDouble 替代 parseDouble，兼容 xsd:float
                if (facet.getFacet() == OWLFacet.MIN_INCLUSIVE) {
                    min = safeParseDouble(facet.getFacetValue());
                } else if (facet.getFacet() == OWLFacet.MAX_INCLUSIVE) {
                    max = safeParseDouble(facet.getFacetValue());
                }
            }
            if (min == null && max == null) return null;
            return new DatatypeConstraint(
                    min != null ? min : Double.NEGATIVE_INFINITY, min != null,
                    max != null ? max : Double.POSITIVE_INFINITY, max != null,
                    sourceClass);
        }

        String validate(OWLLiteral literal, OWLIndividual individual, IRI propIRI) {
            try {
                // ⭐ 修复：使用 safeParseDouble 替代 parseDouble，兼容 xsd:float
                double value = safeParseDouble(literal);
                if (hasMin && value < min) {
                    return String.format("[DatatypeRestriction] 个体 %s 的属性 <%s> 值 %.2f < 最小值 %.2f (来源类: %s)",
                            individual.toStringID(), propIRI.getShortForm(), value, min, sourceClassName);
                }
                if (hasMax && value > max) {
                    return String.format("[DatatypeRestriction] 个体 %s 的属性 <%s> 值 %.2f > 最大值 %.2f (来源类: %s)",
                            individual.toStringID(), propIRI.getShortForm(), value, max, sourceClassName);
                }
            } catch (NumberFormatException e) {
                // 非数值字面量由其他校验处理
                log.warn("⚠️ 无法将字面量 '{}' 解析为数值, 属性={}", literal.getLiteral(), propIRI.getShortForm());
            }
            return null;
        }
    }

    // ==================== 通用推理执行模板 ====================

    public <T> T withReasoner(OWLOntology ontology, Function<OWLReasoner, T> queryFn)
            throws OWLOntologyCreationException {
        OWLReasoner localReasoner = OpenlletReasonerFactory.getInstance().createReasoner(ontology);
        try {
            localReasoner.flush();
            localReasoner.precomputeInferences(
                    InferenceType.CLASS_HIERARCHY, InferenceType.CLASS_ASSERTIONS,
                    InferenceType.OBJECT_PROPERTY_HIERARCHY, InferenceType.OBJECT_PROPERTY_ASSERTIONS,
                    InferenceType.DATA_PROPERTY_HIERARCHY, InferenceType.DATA_PROPERTY_ASSERTIONS,
                    InferenceType.DISJOINT_CLASSES, InferenceType.DIFFERENT_INDIVIDUALS,
                    InferenceType.SAME_INDIVIDUAL);

            if (!localReasoner.isConsistent()) {
                log.error("本体不一致，开始输出诊断信息...");
                ExplainInconsistencyWithBlackBoxExplanation(ontology);
                throw new IllegalStateException("一致性检查失败！新数据与现有本体存在矛盾，已拦截写入。");
            }
            log.info("✅ 预校验通过，无逻辑矛盾");
            return queryFn.apply(localReasoner);
        } finally {
            localReasoner.dispose();
        }
    }

    // ==================== 通用查询方法 ====================

    public Set<String> getInferredTypes(OWLReasoner r, OWLDataFactory df, String classIRI) {
        OWLClass targetClass = df.getOWLClass(IRI.create(classIRI));
        return r.getInstances(targetClass, false).entities()
                .flatMap(ind -> r.getTypes(ind, false).entities()
                        .filter(cls -> !cls.equals(targetClass) && !cls.isOWLThing())
                        .map(cls -> ind.getIRI().getShortForm() + " ⇒ " + cls.getIRI().getShortForm()))
                .collect(Collectors.toSet());
    }

    public Set<String> getInferredPropertyValues(OWLReasoner r, OWLDataFactory df,
                                                 String individualIRI, String propertyIRI) {
        OWLNamedIndividual individual = df.getOWLNamedIndividual(IRI.create(individualIRI));
        OWLObjectProperty property = df.getOWLObjectProperty(IRI.create(propertyIRI));
        return r.getObjectPropertyValues(individual, property).entities()
                .map(i -> i.getIRI().getShortForm())
                .collect(Collectors.toSet());
    }

    public Set<OWLClass> getSuperClassesIncludingSelf(String classIRI) {
        if (classIRI == null || classIRI.isBlank()) return Collections.emptySet();
        try {
            if (reasoner == null) {
                log.error("getSuperClassesIncludingSelf: reasoner 未初始化");
                return Collections.emptySet();
            }
            OWLClass targetClass = dataFactory.getOWLClass(IRI.create(classIRI));
            Set<OWLClass> result = reasoner.getSuperClasses(targetClass, false)
                    .entities().collect(Collectors.toCollection(LinkedHashSet::new));
            result.add(targetClass);
            return Collections.unmodifiableSet(result);
        } catch (Exception e) {
            log.error("getSuperClassesIncludingSelf 异常 | classIRI={}, error={}", classIRI, e.getMessage());
            return Collections.emptySet();
        }
    }
}