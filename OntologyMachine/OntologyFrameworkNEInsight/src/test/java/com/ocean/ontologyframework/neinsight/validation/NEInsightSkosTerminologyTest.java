package com.ocean.ontologyframework.neinsight.validation;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.StmtIterator;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.vocabulary.RDF;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * NEInsight 业务术语表(SKOS)：结构校验 + 与 OWL 本体交叉校验。
 *
 * <p>对应《NEInsight应用-测试设计文档》§6.4：P-1（SKOS 结构）、P-2（OWL 交叉校验）。
 * 纯 Jena 解析，无需外部环境（离线）。术语来源于本体，每条概念须对应本体 IRI（★10）。
 */
@DisplayName("NEInsight 业务术语表(SKOS)：结构校验 + 与 OWL 本体交叉校验")
public class NEInsightSkosTerminologyTest {

    private static final String SKOS_NS = "http://www.w3.org/2004/02/skos/core#";
    private static final String TERM_NS = "http://example.org/neinsight/term/";
    private static final String SCHEME_IRI = "http://example.org/neinsight/term";
    private static final String TERM_FILE = "ontology/neinsight-terminology.ttl";

    private static Model skosModel;
    private static Model owlModel;

    private static Property pPrefLabel;
    private static Property pAltLabel;
    private static Property pBroader;
    private static Property pExactMatch;
    private static Resource cConcept;
    private static Resource cConceptScheme;

    private static Set<String> conceptIris;

    @BeforeAll
    static void loadModels() throws IOException {
        Path termFile = Path.of("ontology", "neinsight-terminology.ttl");
        assertThat(Files.isRegularFile(termFile))
                .as("术语表文件应存在: %s (cwd=%s)", termFile.toAbsolutePath(), Path.of("").toAbsolutePath())
                .isTrue();

        skosModel = RDFDataMgr.loadModel(termFile.toUri().toString());
        pPrefLabel = skosModel.createProperty(SKOS_NS + "prefLabel");
        pAltLabel = skosModel.createProperty(SKOS_NS + "altLabel");
        pBroader = skosModel.createProperty(SKOS_NS + "broader");
        cConcept = skosModel.createResource(SKOS_NS + "Concept");
        cConceptScheme = skosModel.createResource(SKOS_NS + "ConceptScheme");

        conceptIris = skosModel.listResourcesWithProperty(RDF.type, cConcept)
                .filterKeep(Resource::isURIResource)
                .mapWith(Resource::getURI)
                .toSet();

        owlModel = ModelFactory.createDefaultModel();
        List<Path> owlFiles;
        try (Stream<Path> s = Files.list(Path.of("ontology"))) {
            owlFiles = s.filter(p -> p.getFileName().toString().endsWith(".owl"))
                    .sorted()
                    .collect(Collectors.toList());
        }
        assertThat(owlFiles).as("ontology 目录应至少存在一个 .owl 文件").isNotEmpty();
        for (Path p : owlFiles) {
            owlModel.add(RDFDataMgr.loadModel(p.toUri().toString()));
        }
        pExactMatch = owlModel.createProperty(SKOS_NS + "exactMatch");
    }

    // ==================== P-1 SKOS 结构 ====================

    @Test
    @DisplayName("P-1a 术语表声明为 SKOS ConceptScheme")
    void declaresConceptScheme() {
        Resource scheme = skosModel.createResource(SCHEME_IRI);
        assertThat(scheme.hasProperty(RDF.type, cConceptScheme))
                .as("%s 应声明为 skos:ConceptScheme", SCHEME_IRI)
                .isTrue();
    }

    @Test
    @DisplayName("P-1b 概念集合非空，且每个概念均带中文与英文首选标签")
    void everyConceptHasZhAndEnPrefLabel() {
        assertThat(conceptIris).as("SKOS 概念集合不应为空").isNotEmpty();

        List<String> missingZh = new ArrayList<>();
        List<String> missingEn = new ArrayList<>();
        for (String iri : conceptIris) {
            Resource c = skosModel.createResource(iri);
            boolean hasZh = c.listProperties(pPrefLabel).toList().stream()
                    .anyMatch(st -> st.getObject().isLiteral()
                            && "zh".equals(st.getObject().asLiteral().getLanguage()));
            boolean hasEn = c.listProperties(pPrefLabel).toList().stream()
                    .anyMatch(st -> st.getObject().isLiteral()
                            && "en".equals(st.getObject().asLiteral().getLanguage()));
            if (!hasZh) missingZh.add(iri);
            if (!hasEn) missingEn.add(iri);
        }
        assertThat(missingZh).as("以下概念缺少 @zh 首选标签").isEmpty();
        assertThat(missingEn).as("以下概念缺少 @en 首选标签").isEmpty();
    }

    @Test
    @DisplayName("P-1c skos:broader 目标均已定义，且不存在自环")
    void broaderTargetsAreWellFormed() {
        Set<String> dangling = new TreeSet<>();
        Set<String> selfLoop = new TreeSet<>();

        StmtIterator it = skosModel.listStatements(null, pBroader, (RDFNode) null);
        while (it.hasNext()) {
            Statement st = it.nextStatement();
            RDFNode o = st.getObject();
            if (!o.isURIResource()) {
                dangling.add(String.valueOf(st.getSubject()));
                continue;
            }
            String target = o.asResource().getURI();
            if (!conceptIris.contains(target)) {
                dangling.add(target);
            }
            if (st.getSubject().isURIResource() && target.equals(st.getSubject().getURI())) {
                selfLoop.add(target);
            }
        }
        assertThat(dangling).as("skos:broader 指向了未定义的术语").isEmpty();
        assertThat(selfLoop).as("skos:broader 不应存在自环").isEmpty();
    }

    // ==================== P-2 OWL 交叉校验 ====================

    @Test
    @DisplayName("P-2a 交叉校验：OWL 中所有指向 term/ 的 skos:exactMatch 目标均在术语表有定义")
    void owlExactMatchTargetsAreAllDefined() {
        Set<String> owlTargets = new TreeSet<>();
        StmtIterator it = owlModel.listStatements(null, pExactMatch, (RDFNode) null);
        while (it.hasNext()) {
            RDFNode o = it.nextStatement().getObject();
            if (o.isURIResource() && o.asResource().getURI().startsWith(TERM_NS)) {
                owlTargets.add(o.asResource().getURI());
            }
        }
        assertThat(owlTargets)
                .as("OWL 本体中应存在指向术语表(%s)的 skos:exactMatch", TERM_NS)
                .isNotEmpty();

        Set<String> missing = new TreeSet<>(owlTargets);
        missing.removeAll(conceptIris);
        assertThat(missing)
                .as("以下术语被 OWL 的 skos:exactMatch 引用，但未在 %s 中定义", TERM_FILE)
                .isEmpty();
    }

    @Test
    @DisplayName("P-2b 无孤立词条：每个术语概念均有 OWL skos:exactMatch 反向引用")
    void everyConceptHasOwlBackReference() {
        Set<String> owlTargets = new TreeSet<>();
        StmtIterator it = owlModel.listStatements(null, pExactMatch, (RDFNode) null);
        while (it.hasNext()) {
            RDFNode o = it.nextStatement().getObject();
            if (o.isURIResource() && o.asResource().getURI().startsWith(TERM_NS)) {
                owlTargets.add(o.asResource().getURI());
            }
        }
        Set<String> orphans = new TreeSet<>(conceptIris);
        orphans.removeAll(owlTargets);
        assertThat(orphans)
                .as("以下术语概念无对应本体 IRI（疑似造词）")
                .isEmpty();
    }

    @Test
    @DisplayName("P-2c 关键业务术语已纳入（五看三定核心概念）")
    void keyBusinessTermsPresent() {
        assertThat(conceptIris).contains(
                TERM_NS + "Opportunity",
                TERM_NS + "IndustryTrend",
                TERM_NS + "Market",
                TERM_NS + "Customer",
                TERM_NS + "CompetitiveLandscape",
                TERM_NS + "SelfAssessment",
                TERM_NS + "SPANQuadrant",
                TERM_NS + "StrategicControlPoint",
                TERM_NS + "Objective",
                TERM_NS + "Strategy");
    }

    @Test
    @DisplayName("P-2d 同义词已登记（如 机会点↔战略机会点）")
    void synonymsRegistered() {
        Resource opp = skosModel.createResource(TERM_NS + "Opportunity");
        Set<String> alts = opp.listProperties(pAltLabel).toList().stream()
                .filter(st -> st.getObject().isLiteral())
                .map(st -> st.getObject().asLiteral().getString())
                .collect(Collectors.toSet());
        assertThat(alts).as("Opportunity 应登记中文同义词").contains("战略机会点");
    }
}
