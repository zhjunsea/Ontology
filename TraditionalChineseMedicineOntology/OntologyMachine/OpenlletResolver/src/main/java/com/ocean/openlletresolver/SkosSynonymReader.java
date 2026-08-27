package com.ocean.openlletresolver;

import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.semanticweb.owlapi.search.EntitySearcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * SKOS 同义词读取服务
 * 复用 BackendService 已加载的 TBox 本体，不自行读取文件，不依赖 Jena
 */
public class SkosSynonymReader {

    private static final Logger log = LoggerFactory.getLogger(SkosSynonymReader.class);

    // SKOS 注解属性 IRI
    private static final String SKOS_PREF_LABEL = "http://www.w3.org/2004/02/skos/core#prefLabel";
    private static final String SKOS_ALT_LABEL = "http://www.w3.org/2004/02/skos/core#altLabel";
    private static final String SKOS_HIDDEN_LABEL = "http://www.w3.org/2004/02/skos/core#hiddenLabel";
    private static final String SKOS_EXACT_MATCH = "http://www.w3.org/2004/02/skos/core#exactMatch";
    private static final String SKOS_CONCEPT = "http://www.w3.org/2004/02/skos/core#Concept";
    private static final String SKOS_NOTE = "http://www.w3.org/2004/02/skos/core#note";

    /**
     * 从 BackendService 获取已加载的 TBox 本体
     */
    public static OWLOntology getTBox() throws Exception {
        return BackendService.getInstance().getOntologyService().gettBoxOntology();
    }

    private static OWLReasoner getReasoner() {
        return BackendService.getInstance().getReasonerService().getReasoner();
    }

    private static OWLDataFactory getDataFactory() throws Exception {
        return getTBox().getOWLOntologyManager().getOWLDataFactory();
    }

    // ==================== 核心公开方法 ====================

    /**
     * 获取某个 SKOS Concept 的所有中文表达形式（按语义角色分组）
     *
     * @param conceptUri SKOS Concept 的完整 IRI
     * @return key: preferred/alternative/hidden, value: 中文标签列表
     */
    public static Map<String, List<String>> getAllLabels(String conceptUri) throws Exception {
        OWLOntology ontology = getTBox();
        IRI conceptIRI = IRI.create(conceptUri);
        Map<String, List<String>> result = new LinkedHashMap<>();

        // ✅ Key 使用 SKOS property short form，与 buildSynonymDictionary 及测试代码保持一致
        Set<String> targetProps = Set.of("prefLabel", "altLabel", "hiddenLabel");

        // ✅ 一次性遍历 importsClosure，按属性分组缓存中文标签
        Map<String, List<String>> annotationCache = new HashMap<>();
        ontology.importsClosure()
                .flatMap(ont -> ont.axioms(AxiomType.ANNOTATION_ASSERTION))
                .filter(ax -> ax.getSubject().equals(conceptIRI))
                .filter(ax -> ax.getValue() instanceof OWLLiteral)
                .forEach(ax -> {
                    OWLLiteral lit = (OWLLiteral) ax.getValue();
                    if (!"zh".equals(lit.getLang())) return;
                    String propShort = ax.getProperty().getIRI().getShortForm();
                    if (!targetProps.contains(propShort)) return;
                    annotationCache
                            .computeIfAbsent(propShort, k -> new ArrayList<>())
                            .add(lit.getLiteral());
                });

        // 按 prefLabel → altLabel → hiddenLabel 顺序放入结果（保持可读性）
        for (String prop : List.of("prefLabel", "altLabel", "hiddenLabel")) {
            List<String> labels = annotationCache.get(prop);
            if (labels != null && !labels.isEmpty()) {
                result.put(prop, labels);
            }
        }

        return result;
    }

    /**
     * 获取扁平化的所有中文同义词列表（不区分角色，适合搜索/NLP标注）
     */
    public static List<String> getAllSynonyms(String conceptUri) throws Exception {
        Map<String, List<String>> labels = getAllLabels(conceptUri);
        return labels.values().stream()
                .flatMap(Collection::stream)
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * 已知 OWL 个体 URI，通过 skos:exactMatch 反向查找 SKOS Concept 的所有中文标签
     */
    public static List<String> getSynonymsByOwlIndividual(String owlIndividualUri) throws Exception {
        OWLOntology ontology = getTBox();
        IRI owlIRI = IRI.create(owlIndividualUri);

        Optional<IRI> skosConceptIRI = findSkosConceptByExactMatch(ontology, owlIRI);

        if (skosConceptIRI.isEmpty()) {
            log.warn("未找到与 OWL 个体 {} 关联的 SKOS Concept", owlIndividualUri);
            return Collections.emptyList();
        }

        return getAllSynonyms(skosConceptIRI.get().toString());
    }

    /**
     * 已知 OWL 个体 URI，通过 skos:exactMatch 双向查找关联的 SKOS Concept IRI
     * 支持两种声明方向：
     *   1. owlIndividual skos:exactMatch skosConcept  （正向）
     *   2. skosConcept skos:exactMatch owlIndividual  （反向）
     */
    private static Optional<IRI> findSkosConceptByExactMatch(OWLOntology ontology, IRI owlIRI) throws Exception {
        OWLAnnotationProperty exactMatch = getDataFactory()
                .getOWLAnnotationProperty(IRI.create(SKOS_EXACT_MATCH));

        // 方向1：owlIndividual 作为 subject，找 object 是 skosConcept 的断言
        Optional<IRI> forward = ontology.importsClosure()
                .flatMap(ont -> ont.annotationAssertionAxioms(owlIRI))
                .filter(ax -> ax.getProperty().equals(exactMatch))
                .map(OWLAnnotationAssertionAxiom::getValue)
                .filter(v -> v instanceof IRI)
                .map(v -> (IRI) v)
                .findFirst();

        if (forward.isPresent()) {
            return forward;
        }

        // 方向2：skosConcept 作为 subject，owlIndividual 作为 value
        // 需要遍历所有注解断言，按 value 过滤
        return ontology.importsClosure()
                .flatMap(ont -> ont.axioms(AxiomType.ANNOTATION_ASSERTION))
                .filter(ax -> ax.getProperty().equals(exactMatch)
                        && owlIRI.equals(ax.getValue()))
                .map(OWLAnnotationAssertionAxiom::getSubject)
                .filter(sub -> sub instanceof IRI)
                .map(sub -> (IRI) sub)
                .findFirst();
    }

    /**
     * 构建 "任意中文词 → 规范词" 的映射词典
     * 适用于 NLP 标注、搜索查询扩展
     * ⚠️ 建议在上层调用处缓存结果，避免重复构建
     */
    public static Map<String, String> buildSynonymDictionary() throws Exception {
        OWLOntology ontology = getTBox();
        Map<String, String> dict = new HashMap<>();

        log.info("🔍 本体中 ANNOTATION_ASSERTION 总数: {}",
                ontology.importsClosure()
                        .mapToLong(ont -> ont.axioms(AxiomType.ANNOTATION_ASSERTION).count())
                        .sum());

        log.info("🔍 skos:exactMatch 断言数: {}",
                ontology.importsClosure()
                        .flatMap(ont -> ont.axioms(AxiomType.ANNOTATION_ASSERTION))
                        .filter(ax -> ax.getProperty().getIRI().toString().equals(SKOS_EXACT_MATCH))
                        .count());

        // 遍历所有 skos:Concept 实例
        Set<OWLNamedIndividual> concepts = getReasoner()
                .getInstances(getDataFactory().getOWLClass(IRI.create(SKOS_CONCEPT)), true)
                .entities()
                .collect(Collectors.toSet());

        log.info("🔍 推理器识别到的 skos:Concept 数量: {}", concepts.size());  // 新增诊断日志

        OWLAnnotationProperty prefProp = getDataFactory().getOWLAnnotationProperty(IRI.create(SKOS_PREF_LABEL));
        OWLAnnotationProperty altProp = getDataFactory().getOWLAnnotationProperty(IRI.create(SKOS_ALT_LABEL));
        OWLAnnotationProperty hiddenProp = getDataFactory().getOWLAnnotationProperty(IRI.create(SKOS_HIDDEN_LABEL));
        // ⭐ 新增：note 属性
        OWLAnnotationProperty noteProp = getDataFactory().getOWLAnnotationProperty(IRI.create(SKOS_NOTE));

        for (OWLNamedIndividual concept : concepts) {
            IRI conceptIRI = concept.getIRI();

            //测试代码
            if (conceptIRI.toString().contains("HanChu")) {
                log.info("处理概念: {}", conceptIRI);
                // 打印 altLabel 提取过程
                ontology.importsClosure()
                        .flatMap(ont -> ont.axioms(AxiomType.ANNOTATION_ASSERTION))
                        .filter(ax -> ax.getSubject().equals(conceptIRI)
                                && ax.getProperty().getIRI().toString().equals(SKOS_ALT_LABEL))
                        .forEach(ax -> {
                            OWLLiteral lit = (OWLLiteral) ax.getValue();
                            log.info("找到 altLabel: {} (lang={})", lit.getLiteral(), lit.getLang());
                        });
            }

            // 提取中文 prefLabel
            String prefLabel = ontology.importsClosure()
                    .flatMap(ont -> ont.axioms(AxiomType.ANNOTATION_ASSERTION))
                    .filter(ax -> ax.getSubject().equals(conceptIRI)
                            && ax.getProperty().getIRI().toString().equals(SKOS_PREF_LABEL)
                            && ax.getValue() instanceof OWLLiteral)
                    .map(ax -> (OWLLiteral) ax.getValue())
                    .filter(lit -> "zh".equals(lit.getLang()))
                    .map(OWLLiteral::getLiteral)
                    .findFirst()
                    .orElse(null);

            if (prefLabel == null) {
                log.warn("⚠️ {} 无中文 prefLabel，跳过", conceptIRI);
                continue;
            }

            dict.put(prefLabel.toLowerCase(), prefLabel);

            // ⭐ 处理 altLabel、hiddenLabel 以及新增的 note
            for (String propIRI : Arrays.asList(SKOS_ALT_LABEL, SKOS_HIDDEN_LABEL, SKOS_NOTE)) {
                ontology.importsClosure()
                        .flatMap(ont -> ont.axioms(AxiomType.ANNOTATION_ASSERTION))
                        .filter(ax -> ax.getSubject().equals(conceptIRI)
                                && ax.getProperty().getIRI().toString().equals(propIRI)
                                && ax.getValue() instanceof OWLLiteral)
                        .map(ax -> (OWLLiteral) ax.getValue())
                        .filter(lit -> "zh".equals(lit.getLang()))
                        .map(OWLLiteral::getLiteral)
                        .forEach(label -> dict.put(label.toLowerCase(), prefLabel));
            }
        }

        log.info("✅ SKOS 同义词词典构建完成，共 {} 条映射", dict.size());
        return dict;
    }

    /**
     * 根据中文 prefLabel 反查对应的 SKOS Concept IRI。
     * 搜索范围覆盖 importsClosure，确保导入本体中的标签也能被匹配。
     *
     * @param prefLabel 中文首选标签（精确匹配）
     * @return 匹配的 Concept IRI 字符串，未找到返回 null
     */
    public static String findConceptIRIByPrefLabel(String prefLabel) throws Exception {
        OWLOntology ontology = getTBox();
        IRI prefLabelIRI = IRI.create(SKOS_PREF_LABEL);

        return ontology.importsClosure()
                .flatMap(ont -> ont.axioms(AxiomType.ANNOTATION_ASSERTION))
                .filter(ax -> ax.getProperty().getIRI().equals(prefLabelIRI))
                .filter(ax -> ax.getValue().asLiteral()
                        .map(lit -> prefLabel.equals(lit.getLiteral())
                                && "zh".equals(lit.getLang()))
                        .orElse(false))
                .map(ax -> ax.getSubject().toString())
                .findFirst()
                .orElse(null);
    }
}