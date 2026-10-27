package com.ocean.ontologyframework.industryinsight.validation;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.StmtIterator;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.vocabulary.OWL;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * IndustryInsight SKOS 术语表结构与交叉校验（纯 Jena，离线）。
 *
 * <p>第 13 步（闸门⑥）产出 {@code ontology/industryinsight-terminology.ttl}：SKOS 词汇表用于解决
 * 「不同名同义、同义不同名」——规范概念来源于本体，同义表述（别名/缩写/习惯用法）以
 * {@code skos:altLabel} 归并到对应概念。
 *
 * <p>本类校验：①SKOS 结构（ConceptScheme / prefLabel）；②每个概念带中文首选标签；
 * ③与 OWL 本体**双向交叉校验**（{@code skos:exactMatch} 目标必为本体实体，本体带标签实体须有概念）。
 *
 * <p>术语表未产出时本类整体跳过（不失败），产出后自动生效。
 */
@DisplayName("行业洞察业务术语表(SKOS)：结构校验 + 与 OWL 本体交叉校验（纯 Jena）")
public class IndustryInsightSkosTerminologyTest {

    private static final String SKOS_NS = "http://www.w3.org/2004/02/skos/core#";
    private static final String SCHEME_IRI = "http://example.org/industryinsight/term";
    private static final String TERM_FILE = "ontology/industryinsight-terminology.ttl";

    private static Model skosModel;
    private static Model owlModel;
    private static Property pPrefLabel;
    private static Property pExactMatch;
    private static Resource cConcept;
    private static Resource cConceptScheme;
    private static Set<String> conceptIris;
    private static Set<String> owlEntityIris;

    @BeforeAll
    static void loadModels() throws IOException {
        Path termFile = Path.of(TERM_FILE);
        // 词汇表由第 13 步产出；未产出前本类整体跳过（不失败），词汇表就绪后自动生效。
        Assumptions.assumeTrue(Files.isRegularFile(termFile),
                "SKOS 词汇表尚未产出（" + TERM_FILE + "，第 13 步），本类跳过");

        skosModel = RDFDataMgr.loadModel(termFile.toUri().toString());
        pPrefLabel = skosModel.createProperty(SKOS_NS + "prefLabel");
        pExactMatch = skosModel.createProperty(SKOS_NS + "exactMatch");
        cConcept = skosModel.createResource(SKOS_NS + "Concept");
        cConceptScheme = skosModel.createResource(SKOS_NS + "ConceptScheme");

        conceptIris = skosModel.listResourcesWithProperty(RDF.type, cConcept)
                .filterKeep(Resource::isURIResource)
                .mapWith(Resource::getURI)
                .toSet();

        // 加载本体侧（ontology 目录下全部 .owl）用于交叉校验
        owlModel = ModelFactory.createDefaultModel();
        List<Path> owlFiles;
        try (Stream<Path> s = Files.list(Path.of("ontology"))) {
            owlFiles = s.filter(p -> p.getFileName().toString().endsWith(".owl"))
                    .sorted().collect(Collectors.toList());
        }
        assertThat(owlFiles).as("ontology 目录应存在 .owl 文件").isNotEmpty();
        for (Path p : owlFiles) {
            owlModel.add(RDFDataMgr.loadModel(p.toUri().toString()));
        }
        owlEntityIris = new HashSet<>();
        for (Resource type : List.of(OWL.Class, OWL.ObjectProperty, OWL.DatatypeProperty)) {
            owlModel.listResourcesWithProperty(RDF.type, type)
                    .filterKeep(Resource::isURIResource)
                    .mapWith(Resource::getURI)
                    .forEachRemaining(owlEntityIris::add);
        }
    }

    @Test
    @DisplayName("P-1 术语表声明为 SKOS ConceptScheme 且概念非空")
    void terminologyParses() {
        Resource scheme = skosModel.createResource(SCHEME_IRI);
        assertThat(scheme.hasProperty(RDF.type, cConceptScheme))
                .as("术语表根应声明为 skos:ConceptScheme").isTrue();
        assertThat(conceptIris).as("SKOS 概念集合不应为空").isNotEmpty();
    }

    @Test
    @DisplayName("P-2 每个概念均带中文首选标签（skos:prefLabel @zh）")
    void coreClassesHavePrefLabel() {
        List<String> missing = new ArrayList<>();
        for (String iri : conceptIris) {
            Resource c = skosModel.createResource(iri);
            boolean hasZh = c.listProperties(pPrefLabel).toList().stream()
                    .anyMatch(st -> st.getObject().isLiteral()
                            && "zh".equals(st.getObject().asLiteral().getLanguage()));
            if (!hasZh) {
                missing.add(iri);
            }
        }
        assertThat(missing).as("以下概念缺少 @zh 首选标签").isEmpty();
    }

    @Test
    @DisplayName("P-3 术语表与 OWL 本体双向交叉校验（exactMatch 目标存在；带标签实体有概念）")
    void prefLabelCrossChecksWithOwl() {
        Set<String> noMatch = new TreeSet<>();
        Set<String> dangling = new TreeSet<>();
        Set<String> exactTargets = new TreeSet<>();
        for (String iri : conceptIris) {
            Resource c = skosModel.createResource(iri);
            List<String> targets = c.listProperties(pExactMatch).toList().stream()
                    .filter(st -> st.getObject().isURIResource())
                    .map(st -> st.getObject().asResource().getURI())
                    .collect(Collectors.toList());
            if (targets.isEmpty()) {
                noMatch.add(iri);
            }
            for (String t : targets) {
                exactTargets.add(t);
                if (!owlEntityIris.contains(t)) {
                    dangling.add(t);
                }
            }
        }
        assertThat(noMatch).as("以下概念缺少 skos:exactMatch（孤立词条）").isEmpty();
        assertThat(dangling).as("以下 exactMatch 目标不在本体中（类/对象属性/数据属性）").isEmpty();

        Set<String> uncovered = new TreeSet<>();
        for (String entity : owlEntityIris) {
            boolean labelled = owlModel.listStatements(
                    owlModel.createResource(entity), RDFS.label, (RDFNode) null).hasNext();
            if (labelled && !exactTargets.contains(entity)) {
                uncovered.add(entity);
            }
        }
        assertThat(uncovered).as("以下带 rdfs:label 的本体实体未被术语表覆盖").isEmpty();
    }
}
