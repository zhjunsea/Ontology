package com.ocean.openlletresolver;
import openllet.owlapi.OpenlletReasonerFactory;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.reasoner.NodeSet;
import org.semanticweb.owlapi.reasoner.OWLReasoner;

import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

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
        try {
            reasoner.precomputeInferences();
            if (!reasoner.isConsistent()) {
                throw new IllegalStateException("本体不一致，无法进行推理");
            }
            return queryFn.apply(reasoner);
        } finally {
            reasoner.dispose();
        }
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
