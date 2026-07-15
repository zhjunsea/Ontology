package com.ocean.openlletresolver;
import openllet.owlapi.OpenlletReasonerFactory;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.reasoner.NodeSet;
import org.semanticweb.owlapi.reasoner.OWLReasoner;

import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.ocean.openlletresolver.ReasonerService.explainInconsistency;

public class OwlReasoningService {
    // ==================== 1. 通用推理执行模板（核心） ====================
    /**
     * 安全地创建推理器、预计算、执行业务逻辑并自动释放资源
     * @param ontology 已合并的本体
     * @param queryFn  具体的推理查询逻辑
     */
    public static <T> T withReasoner(OWLOntology ontology, Function<OWLReasoner, T> queryFn)
            throws OWLOntologyCreationException {
        OWLReasoner reasoner = OpenlletReasonerFactory.getInstance().createReasoner(ontology);
        //打印ontology中的实例
        printOntologyIndividuals(ontology);

        try {
            reasoner.flush();
            reasoner.precomputeInferences();

            Set<OWLNamedIndividual> explicitInstances = ontology.axioms(AxiomType.CLASS_ASSERTION)
                    .map(OWLClassAssertionAxiom::getIndividual)
                    .filter(OWLNamedIndividual.class::isInstance)
                    .map(OWLNamedIndividual.class::cast)
                    .collect(Collectors.toSet());
            System.out.println("显式类断言个体总数: " + explicitInstances.size());
            Set<OWLNamedIndividual> allInferredIndividuals = reasoner.getInstances(
                    ontology.getOWLOntologyManager().getOWLDataFactory().getOWLThing(), false
            ).getFlattened();
            System.out.println("推理机中所有个体总数: " + allInferredIndividuals.size());

            System.out.println("推理机是否一致: " + reasoner.isConsistent());
            System.out.println("一致性: " + reasoner.isConsistent());
            if (!reasoner.isConsistent()) {
                System.out.println("不可满足类:");
                explainInconsistency(ontology);
                throw new IllegalStateException("一致性检查失败！新数据与现有本体存在矛盾，已拦截写入。");
            }
            System.out.println("✅ 预校验通过，无逻辑矛盾");
            return queryFn.apply(reasoner);
        } finally {
            reasoner.dispose();
        }
    }
    private static void printOntologyIndividuals(OWLOntology ontology){
        // === 开始：打印 ABox 个体诊断信息 ===
        int maxOutputNum = 200;
        System.out.println("========== ABox 个体诊断 ==========");

        // 1. 打印本体中所有的命名个体总数
        Set<OWLNamedIndividual> allIndividuals = ontology.getIndividualsInSignature();
        System.out.println("签名中的命名个体总数: " + allIndividuals.size());

        if (allIndividuals.isEmpty()) {
            System.err.println("⚠️ 警告: 未检测到任何命名个体！请检查文件是否加载正确。");
        } else {
            // 2. 遍历并打印前 30 个个体的详细信息（防止数据量过大刷屏）
            allIndividuals.stream().limit(maxOutputNum).forEach(ind -> {
                // 获取该个体在 ABox 中显式声明的类型 (ClassAssertion)
                String assertedTypes = ontology.getClassAssertionAxioms(ind).stream()
                        .map(ax -> ax.getClassExpression().toString())
                        .collect(Collectors.joining(", "));

                // (可选) 如果已初始化 Reasoner，可获取推理后的直接类型
                // String inferredTypes = reasoner.getTypes(ind, true).getFlattened().stream()
                //         .map(OWLClass::toString)
                //         .collect(Collectors.joining(", "));

                System.out.printf("个体: %-50s | 显式类型: %s%n",
                        ind.getIRI().getFragment(),
                        assertedTypes.isEmpty() ? "(无)" : assertedTypes);
            });

            if (allIndividuals.size() > maxOutputNum) {
                System.out.println("... 还有 " + (allIndividuals.size() - maxOutputNum) + " 个个体未显示 ...");
            }
        }
        System.out.println("===================================");
        // === 结束：打印 ABox 个体诊断信息 ===
    }

    // ==================== 2. 通用类型查询（替代原硬编码逻辑） ====================
    /**
     * 获取指定类的所有实例的推断类型（排除自身及 owl:Thing）
     */
    public static Set<String> getInferredTypes(OWLReasoner reasoner, OWLDataFactory df, String classIRI) {
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
     */
    public static Set<String> getInferredPropertyValues(OWLReasoner reasoner, OWLDataFactory df,
                                                        String individualIRI, String propertyIRI) {
        OWLNamedIndividual individual = df.getOWLNamedIndividual(IRI.create(individualIRI));
        OWLObjectProperty property = df.getOWLObjectProperty(IRI.create(propertyIRI));

        return reasoner.getObjectPropertyValues(individual, property).entities()
                .map(i -> i.getIRI().getShortForm())
                .collect(Collectors.toSet());
    }
}
