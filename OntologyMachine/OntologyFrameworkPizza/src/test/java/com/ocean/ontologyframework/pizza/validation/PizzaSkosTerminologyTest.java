package com.ocean.ontologyframework.pizza.validation;

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

@DisplayName("披萨业务术语表(SKOS)：结构校验 + 与 OWL 本体交叉校验")
class PizzaSkosTerminologyTest {

    private static final String SKOS_NS = "http://www.w3.org/2004/02/skos/core#";
    private static final String TERM_NS = "http://example.org/pizza/term/";
    private static final String SCHEME_IRI = "http://example.org/pizza/term";
    private static final String TERM_FILE = "ontology/pizza-terminology.ttl";

    private static Model skosModel;
    private static Model owlModel;

    private static Property pPrefLabel;
    private static Property pBroader;
    private static Property pExactMatch;
    private static Resource cConcept;
    private static Resource cConceptScheme;

    private static Set<String> conceptIris;

    @BeforeAll
    static void loadModels() throws IOException {
        Path termFile = Path.of("ontology", "pizza-terminology.ttl");
        assertThat(Files.isRegularFile(termFile))
                .as("术语表文件应存在: %s (cwd=%s)", termFile.toAbsolutePath(), Path.of("").toAbsolutePath())
                .isTrue();

        skosModel = RDFDataMgr.loadModel(termFile.toUri().toString());
        pPrefLabel = skosModel.createProperty(SKOS_NS + "prefLabel");
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

    @Test
    @DisplayName("术语表声明为 SKOS ConceptScheme")
    void declaresConceptScheme() {
        Resource scheme = skosModel.createResource(SCHEME_IRI);
        assertThat(scheme.hasProperty(RDF.type, cConceptScheme))
                .as("%s 应声明为 skos:ConceptScheme", SCHEME_IRI)
                .isTrue();
    }

    @Test
    @DisplayName("概念集合非空，且每个概念均带中文首选标签")
    void everyConceptHasChinesePrefLabel() {
        assertThat(conceptIris).as("SKOS 概念集合不应为空").isNotEmpty();

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
    @DisplayName("skos:broader 目标均已定义，且不存在自环")
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

    @Test
    @DisplayName("交叉校验：OWL 中所有指向 term/ 的 skos:exactMatch 目标均在术语表有定义")
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
    @DisplayName("历史缺口回归：hasCrust/hasSauce/hasCheese/hasTopping/ItalianStyleWhiteSeafoodPizza 已补齐")
    void previouslyMissingTermsArePresent() {
        assertThat(conceptIris).contains(
                TERM_NS + "hasCrust",
                TERM_NS + "hasSauce",
                TERM_NS + "hasCheese",
                TERM_NS + "hasTopping",
                TERM_NS + "ItalianStyleWhiteSeafoodPizza");
    }

    @Test
    @DisplayName("融合概念 ItalianStyleWhiteSeafoodPizza 同时归属白酱与海鲜风味")
    void fusionConceptHasBothBroader() {
        Resource fusion = skosModel.createResource(TERM_NS + "ItalianStyleWhiteSeafoodPizza");
        Set<String> parents = fusion.listProperties(pBroader).toList().stream()
                .filter(st -> st.getObject().isURIResource())
                .map(st -> st.getObject().asResource().getURI())
                .collect(Collectors.toSet());
        assertThat(parents).contains(TERM_NS + "WhiteSaucePizza", TERM_NS + "SeafoodPizza");
    }
}
