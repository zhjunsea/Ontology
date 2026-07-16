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

import java.util.Set;
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
        try {
            // ⭐ 使用 Clark & Parsia 版本的 BlackBoxExplanation
            // 构造函数签名: (OWLOntology, OWLReasonerFactory, OWLReasoner)
            BlackBoxExplanation explainer = new BlackBoxExplanation(
                    ontology,
                    OpenlletReasonerFactory.getInstance(),
                    reasoner
            );

            // ⭐ 本体级不一致 = owl:Thing 不可满足
            OWLDataFactory df = ontology.getOWLOntologyManager().getOWLDataFactory();
            Set<OWLAxiom> inconsistentAxioms = explainer.getExplanation(df.getOWLThing());

            if (inconsistentAxioms == null || inconsistentAxioms.isEmpty()) {
                log.error("   ⚠️ 推理器报告不一致，但未提取到具体公理");
            } else {
                log.error("   ❌ 发现 " + inconsistentAxioms.size() + " 条导致矛盾的公理：");
                inconsistentAxioms.forEach(a -> log.error("      → {}", a));
            }
            // ⭐ 该实现内部会创建临时推理器，用完必须 dispose
            explainer.dispose();
        } catch (Exception e) {
            log.error("   ⚠️ 解释器执行异常: " + e.getMessage());
            e.printStackTrace();
        }
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
}