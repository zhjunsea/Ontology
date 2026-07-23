package com.ocean.openlletresolver;

import com.clarkparsia.owlapi.explanation.BlackBoxExplanation;
import org.semanticweb.owl.explanation.api.Explanation;
import org.semanticweb.owl.explanation.api.ExplanationGenerator;
import org.semanticweb.owl.explanation.api.ExplanationGeneratorFactory;
import org.semanticweb.owl.explanation.impl.blackbox.checker.InconsistentOntologyExplanationGeneratorFactory;
import org.semanticweb.owlapi.reasoner.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import openllet.owlapi.OpenlletReasonerFactory;
import org.semanticweb.owlapi.model.*;

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

    public OWLReasonerFactory getFactory() {
        return factory;
    }

    private final OWLDataFactory dataFactory;

    public void setFactory(OWLReasonerFactory factory) {
        this.factory = factory;
    }

    /**
     * 当一致性检查失败时，提取并打印导致矛盾的最小公理集合
     */
    private OWLReasonerFactory factory;

    public OWLReasoner getReasoner() {
        return reasoner;
    }

    public void setReasoner(OWLReasoner reasoner) {
        this.reasoner = reasoner;
    }

    private OWLReasoner reasoner;

    public ReasonerService(OntologyService ontologySrv){
        factory = new OpenlletReasonerFactory();
        this.dataFactory = ontologySrv.getDataFactory();
        reasoner = factory.createReasoner(ontologySrv.gettBoxOntology());
        reasoner.flush();
        reasoner.precomputeInferences(
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

        // ================= 一致性检查与解释生成 =================
        if (!reasoner.isConsistent()) {
            ExplainInconsistencyWithOWLExplanation(ontologySrv);
        }
    }
    public void ExplainInconsistencyWithOWLExplanation(OntologyService ontologySrv){
        log.error("本体不一致！开始生成冲突解释...");
        try {
            Supplier<OWLOntologyManager> managerSupplier = ontologySrv::getManager;
            long timeout = 5000;
            ExplanationGeneratorFactory<OWLAxiom> genFac =
                    new InconsistentOntologyExplanationGeneratorFactory(
                            factory,
                            ontologySrv.getDataFactory(),
                            managerSupplier,
                            timeout
                    );
            ExplanationGenerator<OWLAxiom> gen = genFac.createExplanationGenerator(ontologySrv.gettBoxOntology());

            OWLAxiom inconsistencyAxiom = ontologySrv.getDataFactory().getOWLSubClassOfAxiom(
                    ontologySrv.getDataFactory().getOWLThing(),
                    ontologySrv.getDataFactory().getOWLNothing()
            );

            Set<Explanation<OWLAxiom>> explanations = gen.getExplanations(inconsistencyAxiom, 5);
            int idx = 0;
            for (Explanation<OWLAxiom> exp : explanations) {
                log.error("--- 冲突解释 #" + (++idx) + " ---");
                for (OWLAxiom axiom : exp.getAxioms()) {
                    log.error(axiom.toString());
                }
            }

            if (explanations.isEmpty()) {
                log.error("未生成任何解释，请检查本体或增加解释数量限制。");
            }

        } catch (Exception e) {
            log.error("生成解释时发生异常：" + e.getMessage());
            e.printStackTrace(System.err);
        }
        throw new InconsistentOntologyException("本体不一致，已输出冲突解释（见上方），推理终止。");
    }
    /* 应该复用service里面的reasoner，不应该新建
    public static boolean checkConsistency(OWLOntology ontology) {
        OWLReasoner reasoner = OpenlletReasonerFactory.getInstance()
                .createReasoner(ontology, new SimpleConfiguration());
        try {
            return reasoner.isConsistent();
        } finally {
            reasoner.dispose();
        }
    }*/

    public void ExplainInconsistencyWithBlackBoxExplanation(OWLOntology ontology) {
        log.error("\n🔍 [诊断] 正在分析不一致原因...");
        OWLDataFactory df = ontology.getOWLOntologyManager().getOWLDataFactory();

        // ✅ ====== 第一步：语法级预扫描（不需要推理器，不会抛异常）======
        List<String> datatypeViolations = findSyntaxLevelViolations(ontology, df);
        if (!datatypeViolations.isEmpty()) {
            log.error("   ❌ [数据类型违规] 发现 {} 条字面量不满足 datatype restriction：", datatypeViolations.size());
            datatypeViolations.forEach(v -> log.error("      → {}", v));
        }

        // ✅ ====== 第二步：尝试 BlackBox 解释 ======
        BlackBoxExplanation explainer = null;
        OWLReasoner freshReasoner = null;
        try {
            // ✅ 关键修复：创建一个全新的推理器实例供 BlackBox 使用
            // 不复用外部已处于 inconsistent 状态的 reasoner
            OpenlletReasonerFactory factory = OpenlletReasonerFactory.getInstance();
            freshReasoner = factory.createReasoner(ontology);

            explainer = new BlackBoxExplanation(ontology, factory, freshReasoner);
            Set<OWLAxiom> inconsistentAxioms = explainer.getExplanation(df.getOWLThing());

            if (inconsistentAxioms == null || inconsistentAxioms.isEmpty()) {
                if (datatypeViolations.isEmpty()) {
                    log.error("   ⚠️ 推理器报告不一致，但未提取到具体公理（BlackBox 和语法扫描均无结果）");
                } else {
                    log.error("   ℹ️ BlackBox 未找到逻辑矛盾公理，请以上方语法扫描的数据类型违规为准");
                }
            } else {
                log.error("   ❌ [BlackBox] 发现 {} 条导致矛盾的公理：", inconsistentAxioms.size());
                inconsistentAxioms.forEach(a -> log.error("      → {}", a));
            }
        } catch (InconsistentOntologyException e) {
            // Openllet 在不一致状态下拒绝执行解释算法
            if (!datatypeViolations.isEmpty()) {
                log.error("   ℹ️ BlackBox 因 Openllet 不一致状态被跳过（预期行为），请以上方语法扫描结果为准");
            } else {
                log.error("   ⚠️ BlackBox 因本体不一致无法运行，且语法扫描未发现数据类型违规");
                log.error("      可能原因: 逻辑矛盾（如 disjoint class 冲突）、基数约束冲突等非数据类型问题");
                log.error("      建议: 使用 HermiT 推理器以获取完整的 BlackBox 解释");
            }
        } catch (Exception e) {
            log.error("   ⚠️ 解释器执行异常: {}", e.getMessage());
            e.printStackTrace();
        } finally {
            // ✅ 必须释放资源，避免内存泄漏
            if (explainer != null) {
                try { explainer.dispose(); } catch (Exception ignored) {}
            }
            if (freshReasoner != null) {
                try { freshReasoner.dispose(); } catch (Exception ignored) {}
            }
        }
    }

    /**
     * ✅ 新增：语法级扫描所有 DataPropertyAssertion，检查字面量是否满足本体定义的 range/facet
     * 完全不依赖推理器，因此在本体不一致时也能安全执行
     */
    private List<String> findSyntaxLevelViolations(OWLOntology ontology, OWLDataFactory df) {
        List<String> violations = new ArrayList<>();

        for (OWLAxiom axiom : ontology.getAxioms()) {

            // 1️⃣ Datatype restriction 违规
            if (axiom instanceof OWLDataPropertyAssertionAxiom dpaa) {
                OWLLiteral lit = dpaa.getObject();
                OWLDataPropertyExpression propExpr = dpaa.getProperty();

                // ✅ 仅对具名数据属性查询 Range
                if (propExpr instanceof OWLDataProperty prop) {
                    OWLDataRange range = null;
                    for (OWLDataPropertyRangeAxiom rax : ontology.getDataPropertyRangeAxioms(prop)) {
                        range = rax.getRange();
                        break;
                    }
                    if (range instanceof OWLDatatype dt) {
                        try {
                            df.getOWLLiteral(lit.getLiteral(), dt);
                        } catch (IllegalArgumentException e) {
                            violations.add(String.format("[DataType] %s 值 \"%s\" 不满足 %s",
                                    prop.getIRI().getShortForm(),
                                    lit.getLiteral(), dt.getIRI().getShortForm()));
                        }
                    }
                }
            }

            // 2️⃣ Functional Property + 多值冲突
            if (axiom instanceof OWLDataPropertyAssertionAxiom dpaa) {
                OWLIndividual ind = dpaa.getSubject();
                OWLDataPropertyExpression propExpr = dpaa.getProperty();

                // ✅ 仅对具名数据属性检查 Functional 约束
                if (propExpr instanceof OWLDataProperty prop) {
                    boolean isFunctional = ontology.getFunctionalDataPropertyAxioms(prop).stream()
                            .anyMatch(a -> a.getProperty().equals(prop));
                    if (isFunctional) {
                        long count = ontology.getDataPropertyAssertionAxioms(ind).stream()
                                .filter(a -> a.getProperty().equals(prop))
                                .count();
                        if (count > 1) {
                            violations.add(String.format("[Functional] %s 是函数型属性，但个体 %s 有 %d 个值",
                                    prop.getIRI().getShortForm(),
                                    ind.toStringID(), count));
                        }
                    }
                }
            }

            // 3️⃣ Named Individual 同时属于 Disjoint Classes
            if (axiom instanceof OWLClassAssertionAxiom caa && caa.getIndividual().isNamed()) {
                OWLNamedIndividual ind = caa.getIndividual().asOWLNamedIndividual();
                OWLClassExpression cls = caa.getClassExpression();
                if (cls.isOWLThing() || cls.isAnonymous()) continue;

                Set<OWLClass> assertedClasses = ontology.getClassAssertionAxioms(ind).stream()
                        .map(OWLClassAssertionAxiom::getClassExpression)
                        .filter(OWLClassExpression::isOWLClass)
                        .map(OWLClassExpression::asOWLClass)
                        .collect(Collectors.toSet());

                // ✅ 修复：getClassesAsList() → getClasses()
                // ✅ 修复：使用 AxiomType 获取所有 DisjointClasses 公理
                for (OWLDisjointClassesAxiom dca : ontology.getAxioms(AxiomType.DISJOINT_CLASSES)) {
                    // ✅ 修复：getClasses() → classExpressions()
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

    /**
     * ✅ 修正版：使用 OWLAPI 原生词法空间验证，覆盖所有 XSD 内置类型
     */

    private boolean isLiteralCompatible(OWLLiteral literal, OWLDatatype range) {
        String lexValue = literal.getLiteral();

        // ⭐ 优先级1: IRI 精确匹配（最快路径，覆盖绝大多数正常场景）
        if (literal.getDatatype().getIRI().equals(range.getIRI())) {
            return true;
        }

        // ⭐ 优先级2: XSD 数值类型向上兼容（integer ⊆ decimal 等）
        if (isNumericCompatible(literal.getDatatype(), range)) {
            return true;
        }

        // ⭐ 优先级3: ✅ 用 dataFactory 尝试以目标 range 重新构建字面量
        // 如果值不在 range 的词法空间中，OWLAPI 会抛出 IllegalArgumentException
        try {
            dataFactory.getOWLLiteral(lexValue, range);
            return true;
        } catch (IllegalArgumentException e) {
            // 值不在该 datatype 的词法空间中
            return false;
        } catch (Exception e) {
            // 其他异常（如自定义 datatype 不支持），降级到 false
            log.debug("ℹ️ 词法验证异常 ({} vs {}): {}",
                    range.getIRI().getShortForm(), lexValue, e.getMessage());
            return false;
        }
    }

    /**
     * XSD 数值类型的派生兼容关系
     * 例如: xsd:integer 是 xsd:decimal 的子类型，"3" 作为 integer 应兼容 decimal range
     */
    private boolean isNumericCompatible(OWLDatatype actual, OWLDatatype expected) {
        String actualIRI = actual.getIRI().toString();
        String expectedIRI = expected.getIRI().toString();

        // xsd:integer ⊆ xsd:decimal ⊆ xsd:anyAtomicType
        if (expectedIRI.endsWith("#decimal") && actualIRI.endsWith("#integer")) return true;
        if (expectedIRI.endsWith("#decimal") && actualIRI.endsWith("#long")) return true;
        if (expectedIRI.endsWith("#decimal") && actualIRI.endsWith("#int")) return true;
        if (expectedIRI.endsWith("#decimal") && actualIRI.endsWith("#short")) return true;

        // xsd:double / xsd:float 与 xsd:decimal 之间不自动兼容（语义不同），但词法上可解析
        // 如果你的业务允许，可以取消下面的注释
        // if (expectedIRI.endsWith("#double") && actualIRI.endsWith("#decimal")) return true;

        return false;
    }

    // ==================== 1. 通用推理执行模板（核心） ====================
    /**
     * 安全地创建推理器、预计算、执行业务逻辑并自动释放资源
     *
     * @param ontology 已合并的本体对象
     * @param queryFn  具体的推理查询业务逻辑
     * @return 查询逻辑的返回值
     * @throws OWLOntologyCreationException 本体创建异常
     */
    public <T> T withReasoner(OWLOntology ontology, Function<OWLReasoner, T> queryFn)
            throws OWLOntologyCreationException {

        // 创建 Openllet 推理器实例
        OWLReasoner reasoner = OpenlletReasonerFactory.getInstance().createReasoner(ontology);
        try {
            // 刷新缓冲区并预计算推理结果
            reasoner.flush();
            reasoner.precomputeInferences(
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

            // 统计显式类断言的个体总数
            Set<OWLNamedIndividual> explicitInstances = ontology.axioms(AxiomType.CLASS_ASSERTION)
                    .map(OWLClassAssertionAxiom::getIndividual)
                    .filter(OWLNamedIndividual.class::isInstance)
                    .map(OWLNamedIndividual.class::cast)
                    .collect(Collectors.toSet());
            log.debug("显式类断言个体总数: {}", explicitInstances.size());

            // 统计推理机中推断出的所有个体总数
            Set<OWLNamedIndividual> allInferredIndividuals = reasoner.getInstances(
                    ontology.getOWLOntologyManager().getOWLDataFactory().getOWLThing(), false
            ).getFlattened();
            log.debug("推理机中所有个体总数: {}", allInferredIndividuals.size());

            // 【性能优化】只调用一次 isConsistent()，避免重复触发推理计算
            boolean consistent = reasoner.isConsistent();
            log.debug("推理机一致性检查结果: {}", consistent);

            if (!consistent) {
                log.error("本体不一致，开始输出不可满足类诊断信息...");
                ExplainInconsistencyWithBlackBoxExplanation(ontology);
                throw new IllegalStateException("一致性检查失败！新数据与现有本体存在矛盾，已拦截写入。");
            }

            log.info("✅ 预校验通过，无逻辑矛盾");
            // 执行传入的业务查询逻辑
            return queryFn.apply(reasoner);

        } finally {
            // 确保推理器资源被正确释放
            reasoner.dispose();
        }
    }

    // ==================== ABox 个体诊断（私有辅助方法） ====================
    /**
     * 打印本体中的个体诊断信息，用于排查数据加载问题
     *
     * @param ontology 本体对象
     */
    private void printOntologyIndividuals(OWLOntology ontology) {
        // 限制最大输出数量，防止数据量过大导致日志刷屏
        int maxOutputNum = 200;
        log.debug("========== ABox 个体诊断 ==========");

        Set<OWLNamedIndividual> allIndividuals = ontology.getIndividualsInSignature();
        log.debug("签名中的命名个体总数: {}", allIndividuals.size());

        if (allIndividuals.isEmpty()) {
            log.warn("⚠️ 未检测到任何命名个体！请检查文件是否加载正确。");
        } else {
            // 遍历并打印前 maxOutputNum 个个体的详细信息
            allIndividuals.stream().limit(maxOutputNum).forEach(ind -> {
                // 获取该个体在 ABox 中显式声明的类型
                String assertedTypes = ontology.getClassAssertionAxioms(ind).stream()
                        .map(ax -> ax.getClassExpression().toString())
                        .collect(Collectors.joining(", "));

                log.debug("个体: {} | 显式类型: {}",
                        ind.getIRI().getFragment(),
                        assertedTypes.isEmpty() ? "(无)" : assertedTypes);
            });

            // 如果个体总数超过限制，提示还有未显示的个体
            if (allIndividuals.size() > maxOutputNum) {
                log.debug("... 还有 {} 个个体未显示 ...", allIndividuals.size() - maxOutputNum);
            }
        }

        log.debug("===================================");
    }

    // ==================== 2. 通用类型查询 ====================
    /**
     * 获取指定类的所有实例的推断类型（排除自身及 owl:Thing）
     *
     * @param reasoner 推理器实例
     * @param df       OWL 数据工厂
     * @param classIRI 目标类的 IRI 字符串
     * @return 推断类型描述集合
     */
    public Set<String> getInferredTypes(OWLReasoner reasoner, OWLDataFactory df, String classIRI) {
        OWLClass targetClass = df.getOWLClass(IRI.create(classIRI));
        NodeSet<OWLNamedIndividual> instances = reasoner.getInstances(targetClass, false);

        return instances.entities()
                .flatMap(ind -> reasoner.getTypes(ind, false).entities()
                        .filter(cls -> !cls.equals(targetClass) && !cls.isOWLThing())
                        .map(cls -> ind.getIRI().getShortForm() + " ⇒ " + cls.getIRI().getShortForm()))
                .collect(Collectors.toSet());
    }

    // ==================== 3. 通用属性值查询 ====================
    /**
     * 获取指定个体的推断对象属性值
     *
     * @param reasoner      推理器实例
     * @param df            OWL 数据工厂
     * @param individualIRI 个体的 IRI 字符串
     * @param propertyIRI   对象属性的 IRI 字符串
     * @return 推断属性值短名称集合
     */
    public Set<String> getInferredPropertyValues(OWLReasoner reasoner, OWLDataFactory df,
                                                 String individualIRI, String propertyIRI) {
        OWLNamedIndividual individual = df.getOWLNamedIndividual(IRI.create(individualIRI));
        OWLObjectProperty property = df.getOWLObjectProperty(IRI.create(propertyIRI));

        return reasoner.getObjectPropertyValues(individual, property).entities()
                .map(i -> i.getIRI().getShortForm())
                .collect(Collectors.toSet());
    }
    /**
     * 获取指定类及其所有推断父类（含自身、owl:Thing）
     * <p>
     * 复用 ReasonerService 内部已预计算的 reasoner，无需重新创建推理器实例。
     *
     * @param classIRI 目标类的完整 IRI 字符串
     * @return 包含自身及所有父类的 OWLClass 集合；若类不存在或本体不一致则返回空集
     */
    public Set<OWLClass> getSuperClassesIncludingSelf(String classIRI) {
        if (classIRI == null || classIRI.isBlank()) {
            log.warn("getSuperClassesIncludingSelf: classIRI 为空");
            return Collections.emptySet();
        }

        try {
            // ⭐ 复用构造函数中已预计算好的 reasoner，避免重复创建和 flush
            if (reasoner == null) {
                log.error("getSuperClassesIncludingSelf: reasoner 未初始化，请检查 ReasonerService 构造是否成功");
                return Collections.emptySet();
            }

            OWLClass targetClass = dataFactory.getOWLClass(IRI.create(classIRI));

            // getSuperClasses(cls, false) → direct=false 表示获取所有传递闭包父类
            // .entities() 展平 NodeSet 为 Stream<OWLClass>
            // 手动加入自身，因为 getSuperClasses 不包含输入类本身
            Set<OWLClass> result = reasoner.getSuperClasses(targetClass, false)
                    .entities()
                    .collect(Collectors.toCollection(LinkedHashSet::new));

            // ✅ 将自身加入结果集（放在最前面，便于调用方优先匹配）
            result.add(targetClass);

            log.debug("getSuperClassesIncludingSelf | class={} | 父类数量={}",
                    targetClass.getIRI().getShortForm(), result.size());

            return Collections.unmodifiableSet(result);

        } catch (Exception e) {
            log.error("getSuperClassesIncludingSelf 异常 | classIRI={}, error={}", classIRI, e.getMessage());
            return Collections.emptySet();
        }
    }
}