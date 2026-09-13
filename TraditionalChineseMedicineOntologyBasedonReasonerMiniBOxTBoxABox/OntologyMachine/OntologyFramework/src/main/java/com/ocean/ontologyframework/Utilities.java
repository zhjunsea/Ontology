package com.ocean.ontologyframework;

import com.ocean.openlletresolver.BackendService;
import org.semanticweb.owlapi.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * 本体标签匹配工具类
 * 提供基于个体类型闭包或类层级结构的 rdfs:label 候选匹配能力
 */
public final class Utilities {

    private static final Logger log = LoggerFactory.getLogger(Utilities.class);

    private Utilities() {} // 防止实例化

    /**
     * 根据实体 IRI 的类型闭包，从候选标签列表中匹配第一个命中的 rdfs:label
     *
     * @param entityIri       目标个体或类的完整 IRI
     * @param candidateLabels 候选标签有序列表，最后一个元素为缺省值
     * @param byIndividual    true=按个体类型闭包匹配; false=按类父类闭包匹配
     * @param backendService  后端服务（用于访问推理器和本体）
     * @return 匹配到的标签，或列表末尾的缺省值
     */
    public static String resolveMatchedWord(String entityIri,
                                            List<String> candidateLabels,
                                            boolean byIndividual,
                                            BackendService backendService) {
        // 1. 参数防御
        if (candidateLabels == null || candidateLabels.isEmpty()) {
            log.warn("resolveMatchedWord: candidateLabels 为空, 返回空字符串");
            return "";
        }
        String defaultValue = candidateLabels.get(candidateLabels.size() - 1);

        if (entityIri == null || entityIri.isBlank()) {
            log.warn("resolveMatchedWord: entityIri 为空, 返回缺省值={}", defaultValue);
            return defaultValue;
        }

        try {
            // 2. 获取类型闭包
            Set<OWLClass> allTypes;
            if (byIndividual) {
                OWLNamedIndividual individual = backendService.getIndividual(entityIri);
                allTypes = backendService.getIndividualAllTypes(individual);
            } else {
                allTypes = backendService.getReasonerService().getSuperClassesIncludingSelf(entityIri);
            }

            // 3. 预构建候选集合用于 O(1) 查找
            Set<String> candidateSet = new LinkedHashSet<>(candidateLabels);

            OWLOntology tbox = backendService.getOntologyService().gettBoxOntology();
            OWLDataFactory dataFactory = backendService.getOntologyService().getDataFactory();
            OWLAnnotationProperty labelProperty = dataFactory.getRDFSLabel();

            // 4. 遍历类型闭包，查找第一个在候选列表中的 rdfs:label（优先中文）
            for (OWLClass cls : allTypes) {
                Optional<String> labelOpt = tbox.annotationAssertionAxioms(cls.getIRI())
                        .filter(ax -> ax.getProperty().equals(labelProperty))
                        .map(ax -> ax.getValue().asLiteral().orElse(null))
                        .filter(lit -> lit != null && !lit.getLiteral().isBlank())
                        .sorted((a, b) -> Boolean.compare(
                                "zh".equals(b.getLang()), "zh".equals(a.getLang())))
                        .map(OWLLiteral::getLiteral)
                        .filter(candidateSet::contains)
                        .findFirst();

                if (labelOpt.isPresent()) {
                    log.debug("resolveMatchedWord 命中 | entity={} | class={} | label={}",
                            entityIri, cls.getIRI().getShortForm(), labelOpt.get());
                    return labelOpt.get();
                }
            }

            log.debug("resolveMatchedWord 未命中, 返回缺省值 | entity={} | types={} | default={}",
                    entityIri, allTypes.size(), defaultValue);
            return defaultValue;

        } catch (IllegalArgumentException e) {
            log.warn("resolveMatchedWord: 实体不存在, 返回缺省值 | iri={}, msg={}, default={}",
                    entityIri, e.getMessage(), defaultValue);
            return defaultValue;
        } catch (Exception e) {
            log.error("resolveMatchedWord 异常, 返回缺省值 | iri={}, error={}, default={}",
                    entityIri, e.getMessage(), defaultValue);
            return defaultValue;
        }
    }
}