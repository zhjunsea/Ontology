package com.ocean.ontologyframework.plumblossom.validation;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
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
 * PlumBlossom SKOS 术语表结构与交叉校验（纯 Jena，离线）。
 *
 * <p>第 13 步（闸门⑥）产出 {@code ontology/plumblossom-terminology.ttl}：SKOS 词汇表用于**解决
 * 「不同名同义、同义不同名」**——规范概念来源于本体，同义表述（别名/缩写/习惯用法/口语/书面语）
 * 以 {@code skos:altLabel} 归并到对应概念。
 *
 * <p>本类校验：①SKOS 结构（ConceptScheme / prefLabel / broader）；②与 OWL 本体**双向交叉校验**
 * （{@code skos:exactMatch} 目标必为本体实体，本体带标签实体 100% 有概念）；③同义词表非空、
 * 不与首选标签重复。
 *
 * <p>术语表未产出时本类整体跳过（不失败），产出后自动生效。
 */
@DisplayName("梅花易数业务术语表(SKOS)：结构校验 + 与 OWL 本体交叉校验（纯 Jena）")
public class PlumBlossomSkosTerminologyTest {

    private static final String SKOS_NS = "http://www.w3.org/2004/02/skos/core#";
    private static final String TERM_NS = "http://example.org/plumblossom/term/";
    private static final String SCHEME_IRI = "http://example.org/plumblossom/term";
    private static final String TERM_FILE = "ontology/plumblossom-terminology.ttl";

    private static Model skosModel;
    private static Model owlModel;
    private static Property pPrefLabel;
    private static Property pAltLabel;
    private static Property pBroader;
    private static Property pExactMatch;
    private static Resource cConcept;
    private static Resource cConceptScheme;
    private static Set<String> conceptIris;
    private static Set<String> owlEntityIris;
    private static boolean skosLoaded;

    @BeforeAll
    static void loadModels() throws IOException {
        Path termFile = Path.of(TERM_FILE);
        // 词汇表由第 13 步产出；未产出前本类整体跳过（不失败），词汇表就绪后自动生效。
        skosLoaded = Files.isRegularFile(termFile);
        Assumptions.assumeTrue(skosLoaded,
                "SKOS 词汇表尚未产出（" + TERM_FILE + "，第 13 步），本类跳过");

        skosModel = RDFDataMgr.loadModel(termFile.toUri().toString());
        pPrefLabel = skosModel.createProperty(SKOS_NS + "prefLabel");
        pAltLabel = skosModel.createProperty(SKOS_NS + "altLabel");
        pBroader = skosModel.createProperty(SKOS_NS + "broader");
        pExactMatch = skosModel.createProperty(SKOS_NS + "exactMatch");
        cConcept = skosModel.createResource(SKOS_NS + "Concept");
        cConceptScheme = skosModel.createResource(SKOS_NS + "ConceptScheme");

        conceptIris = skosModel.listResourcesWithProperty(RDF.type, cConcept)
                .filterKeep(Resource::isURIResource)
                .mapWith(Resource::getURI)
                .toSet();

        // 加载本体侧（TBox 九域 .owl）用于交叉校验
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

    // ==================== 结构校验 ====================

    @Test
    @DisplayName("P-1 术语表声明为 SKOS ConceptScheme 且概念非空")
    void declaresConceptScheme() {
        Resource scheme = skosModel.createResource(SCHEME_IRI);
        assertThat(scheme.hasProperty(RDF.type, cConceptScheme))
                .as("术语表根应声明为 skos:ConceptScheme").isTrue();
        assertThat(conceptIris).as("SKOS 概念集合不应为空").isNotEmpty();
    }

    @Test
    @DisplayName("P-2 每个概念均带中文首选标签")
    void everyConceptHasChinesePrefLabel() {
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
    @DisplayName("B-1 skos:broader 目标均已定义，且不存在自环")
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

    // ==================== 与 OWL 双向交叉校验 ====================

    @Test
    @DisplayName("X-1 每个概念的 skos:exactMatch 目标均存在，且为本体已声明的类/属性（无孤立词条）")
    void everyConceptExactMatchesAnOntologyEntity() {
        Set<String> noMatch = new TreeSet<>();
        Set<String> dangling = new TreeSet<>();
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
                if (!owlEntityIris.contains(t)) {
                    dangling.add(t);
                }
            }
        }
        assertThat(noMatch).as("以下概念缺少 skos:exactMatch（孤立词条）").isEmpty();
        assertThat(dangling).as("以下 exactMatch 目标不在本体中（类/对象属性/数据属性）").isEmpty();
    }

    @Test
    @DisplayName("X-2 本体中每个带 rdfs:label 的类/属性，均在术语表中有对应概念（无遗漏）")
    void everyLabelledOntologyEntityHasAConcept() {
        Set<String> exactTargets = new TreeSet<>();
        StmtIterator it = skosModel.listStatements(null, pExactMatch, (RDFNode) null);
        while (it.hasNext()) {
            RDFNode o = it.nextStatement().getObject();
            if (o.isURIResource()) {
                exactTargets.add(o.asResource().getURI());
            }
        }
        Set<String> missing = new TreeSet<>();
        for (String entity : owlEntityIris) {
            boolean labelled = owlModel.listStatements(
                    owlModel.createResource(entity), RDFS.label, (RDFNode) null).hasNext();
            if (labelled && !exactTargets.contains(entity)) {
                missing.add(entity);
            }
        }
        assertThat(missing).as("以下带 rdfs:label 的本体实体未被术语表覆盖").isEmpty();
    }

    // ==================== 同义词校验 ====================

    @Test
    @DisplayName("S-1 同义词（skos:altLabel）非空、为中文、且不与首选标签重复")
    void synonymsAreWellFormed() {
        long altCount = skosModel.listStatements(null, pAltLabel, (RDFNode) null).toList().size();
        assertThat(altCount).as("同义词（skos:altLabel）数量应大于 0").isGreaterThan(0);

        List<String> bad = new ArrayList<>();
        for (String iri : conceptIris) {
            Resource c = skosModel.createResource(iri);
            Set<String> pref = c.listProperties(pPrefLabel).toList().stream()
                    .filter(st -> st.getObject().isLiteral()
                            && "zh".equals(st.getObject().asLiteral().getLanguage()))
                    .map(st -> st.getObject().asLiteral().getString())
                    .collect(Collectors.toSet());
            for (Statement st : c.listProperties(pAltLabel).toList()) {
                if (!st.getObject().isLiteral()) {
                    bad.add(iri + " → 非字面量");
                    continue;
                }
                String lang = st.getObject().asLiteral().getLanguage();
                String val = st.getObject().asLiteral().getString();
                if (!"zh".equals(lang)) {
                    bad.add(iri + " → 同义词缺少 @zh：" + val);
                }
                if (pref.contains(val)) {
                    bad.add(iri + " → 同义词与首选标签重复：" + val);
                }
            }
        }
        assertThat(bad).as("以下同义词不合法（非中文/与首选标签重复）").isEmpty();
    }
}
