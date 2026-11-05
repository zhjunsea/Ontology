package com.ocean.ontologyframework.ziweidoushu.validation;

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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 紫微斗数业务术语表(SKOS)：结构校验 + 与 OWL 本体交叉校验（第 8 步占位，第 13 步启用）。
 *
 * <p>纯 Jena 离线校验，无需外部环境。待第 13 步产出 {@code ontology/ziweidoushu-terminology.ttl}
 * 后本类自动启用；在此之前 {@link #loadModels()} 经 {@code assumeTrue} 跳过全部用例。
 *
 * <p>用例清单见《ZiWeiDouShu 应用-测试设计文档》§6.5（6 项：P-1~P-4 + N-1~N-2）。
 */
@DisplayName("紫微斗数业务术语表(SKOS)：结构校验 + 与 OWL 本体交叉校验")
public class ZiWeiDouShuSkosTerminologyTest {

    private static final String SKOS_NS = "http://www.w3.org/2004/02/skos/core#";
    private static final String TERM_NS = "http://example.org/ziweidoushu/term/";
    private static final String SCHEME_IRI = "http://example.org/ziweidoushu/term";
    private static final String TERM_FILE = "ontology/ziweidoushu-terminology.ttl";

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
        Path termFile = Path.of("ontology", "ziweidoushu-terminology.ttl");
        assumeTrue(Files.isRegularFile(termFile),
                () -> "术语表文件尚未生成（第 13 步产出），跳过 SKOS 词汇表测试：" + termFile.toAbsolutePath());

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

    // ============================ P-1 ============================
    @Test
    @DisplayName("P-1 术语结构有效：声明 ConceptScheme，概念集合非空且每个概念有首选标签")
    void skosStructureValid() {
        Resource scheme = skosModel.createResource(SCHEME_IRI);
        assertThat(scheme.hasProperty(RDF.type, cConceptScheme))
                .as("%s 应声明为 skos:ConceptScheme", SCHEME_IRI).isTrue();

        assertThat(conceptIris).as("SKOS 概念集合不应为空").isNotEmpty();

        List<String> missing = new ArrayList<>();
        for (String iri : conceptIris) {
            if (!skosModel.createResource(iri).hasProperty(pPrefLabel)) {
                missing.add(iri);
            }
        }
        assertThat(missing).as("以下概念缺少 skos:prefLabel").isEmpty();
    }

    // ============================ P-2 ============================
    @Test
    @DisplayName("P-2 概念映射本体内 IRI：OWL 中指向 term/ 的 skos:exactMatch 目标均在术语表有定义")
    void conceptMapsToOntologyIri() {
        Set<String> owlTargets = new TreeSet<>();
        StmtIterator it = owlModel.listStatements(null, pExactMatch, (RDFNode) null);
        while (it.hasNext()) {
            RDFNode o = it.nextStatement().getObject();
            if (o.isURIResource() && o.asResource().getURI().startsWith(TERM_NS)) {
                owlTargets.add(o.asResource().getURI());
            }
        }
        assertThat(owlTargets)
                .as("OWL 本体应存在指向术语表(%s)的 skos:exactMatch", TERM_NS).isNotEmpty();

        Set<String> missing = new TreeSet<>(owlTargets);
        missing.removeAll(conceptIris);
        assertThat(missing).as("被 OWL 引用但未在术语表定义的术语").isEmpty();
    }

    // ============================ P-3 ============================
    @Test
    @DisplayName("P-3 规范名取自本体：每个概念的首选标签为中文且非空白")
    void prefLabelFromOntology() {
        List<String> bad = new ArrayList<>();
        for (String iri : conceptIris) {
            boolean hasZh = skosModel.createResource(iri).listProperties(pPrefLabel).toList().stream()
                    .anyMatch(st -> st.getObject().isLiteral()
                            && "zh".equals(st.getObject().asLiteral().getLanguage())
                            && !st.getObject().asLiteral().getString().isBlank());
            if (!hasZh) {
                bad.add(iri);
            }
        }
        assertThat(bad).as("以下概念缺少非空白的中文(@zh)首选标签").isEmpty();
    }

    // ============================ P-4 ============================
    @Test
    @DisplayName("P-4 别名标注来源：每条 altLabel 为字面量且带语言标签，且逐条带可追溯「# 源:」注释")
    void altLabelHasSource() throws IOException {
        List<String> bad = new ArrayList<>();
        StmtIterator it = skosModel.listStatements(null, pAltLabel, (RDFNode) null);
        while (it.hasNext()) {
            Statement st = it.nextStatement();
            RDFNode o = st.getObject();
            boolean ok = o.isLiteral() && o.asLiteral().getLanguage() != null && !o.asLiteral().getLanguage().isBlank();
            if (!ok) {
                bad.add(String.valueOf(st.getSubject()));
            }
        }
        assertThat(bad).as("以下概念的 altLabel 缺少语言标签（来源可追溯）").isEmpty();

        List<String> lines = Files.readAllLines(Path.of(TERM_FILE));
        long altLines = lines.stream().filter(l -> l.contains("skos:altLabel")).count();
        long withSource = lines.stream()
                .filter(l -> l.contains("skos:altLabel") && l.contains("# 源:")).count();
        assertThat(altLines).as("术语表应存在同义词（skos:altLabel）").isGreaterThan(0);
        assertThat(withSource)
                .as("每条 skos:altLabel 均须带「# 源:」来源注释（★8/10 可追溯）")
                .isEqualTo(altLines);
    }

    // ============================ N-1 ============================
    @Test
    @DisplayName("N-1 无孤立概念：每个术语概念均经 skos:exactMatch 关联到本体 IRI")
    void noOrphanConcept() {
        Set<String> matched = new TreeSet<>();
        StmtIterator it = owlModel.listStatements(null, pExactMatch, (RDFNode) null);
        while (it.hasNext()) {
            RDFNode o = it.nextStatement().getObject();
            if (o.isURIResource()) {
                matched.add(o.asResource().getURI());
            }
        }
        Set<String> orphans = new TreeSet<>(conceptIris);
        orphans.removeAll(matched);
        assertThat(orphans).as("以下概念未关联到本体 IRI（孤立概念）").isEmpty();
    }

    // ============================ N-2 ============================
    @Test
    @DisplayName("N-2 同义归一：同一语言下首选标签不重复（一名一概念）")
    void synonymMapsToCorrectConcept() {
        Map<String, String> firstOwner = new HashMap<>();
        List<String> dup = new ArrayList<>();
        for (String iri : conceptIris) {
            skosModel.createResource(iri).listProperties(pPrefLabel).toList().stream()
                    .filter(st -> st.getObject().isLiteral())
                    .map(st -> st.getObject().asLiteral().getLanguage() + "|"
                            + st.getObject().asLiteral().getString())
                    .forEach(key -> {
                        String owner = firstOwner.putIfAbsent(key, iri);
                        if (owner != null && !owner.equals(iri)) {
                            dup.add(key);
                        }
                    });
        }
        assertThat(dup).as("以下首选标签被多个概念重复使用（归一冲突）").isEmpty();
    }

    // ============================ S-1（B-2 追溯性） ============================
    @Test
    @DisplayName("S-1 SourceNote 声明质量：140 条（137 SN_* + 3 聚合）、137 个 SN_* 属性齐全/label==sourceVolume/无简体字（B-2 追溯性回归）")
    void sourceNoteDeclarationQuality() {
        final String DIAG = "http://example.org/ziweidoushu/diagnosis#";
        Resource cSourceNote = owlModel.createResource(DIAG + "SourceNote");
        Property pSourceBook = owlModel.createProperty(DIAG + "sourceBook");
        Property pSourceVolume = owlModel.createProperty(DIAG + "sourceVolume");

        List<Resource> notes = owlModel.listResourcesWithProperty(RDF.type, cSourceNote).toList();
        assertThat(notes).as("ABox SourceNote 个体共 140（137 个 SN_* 章节项 + 3 个全书/卷级聚合项）").hasSize(140);

        long snCount = notes.stream().filter(r -> r.getURI().contains("#SN_")).count();
        assertThat(snCount).as("SN_* 章节级 SourceNote 应为 137（J1 23 + J1Q 35 + J2 59 + J3 20）").isEqualTo(137);

        String[] simplified = {"问", "禄", "辅", "钺", "马", "权", "罗", "铃", "伤", "虚"};
        List<String> bad = new ArrayList<>();
        for (Resource r : notes) {
            String id = r.getURI();
            String book = r.hasProperty(pSourceBook) ? r.getProperty(pSourceBook).getString() : null;
            String vol = r.hasProperty(pSourceVolume) ? r.getProperty(pSourceVolume).getString() : null;
            boolean isSn = id.contains("#SN_");
            // 所有 SourceNote 均须有 sourceBook + sourceVolume
            if (book == null || book.isBlank() || vol == null || vol.isBlank()) {
                bad.add(id + " 缺 sourceBook/sourceVolume");
                continue;
            }
            if (!isSn) {
                continue; // 3 个聚合项无 rdfs:label（仅章节卷册），不强制 label
            }
            // 137 个章节项：label(zh) 必须存在、且 label==sourceVolume、且无简体常用字
            boolean hasZhLabel = r.listProperties(org.apache.jena.vocabulary.RDFS.label).toList().stream()
                    .anyMatch(st -> st.getObject().isLiteral()
                            && "zh".equals(st.getObject().asLiteral().getLanguage())
                            && !st.getObject().asLiteral().getString().isBlank());
            String lab = r.hasProperty(org.apache.jena.vocabulary.RDFS.label)
                    ? r.getProperty(org.apache.jena.vocabulary.RDFS.label).getString() : null;
            if (!hasZhLabel) {
                bad.add(id + " 缺中文 rdfs:label");
                continue;
            }
            if (!lab.equals(vol)) {
                bad.add(id + " label!=sourceVolume（" + lab + " / " + vol + "）");
            }
            for (String s : simplified) {
                if (lab.indexOf(s) >= 0) {
                    bad.add(id + " 含简体字「" + s + "」：" + lab);
                }
            }
        }
        assertThat(bad).as("SourceNote 声明质量问题（应为 0）：137 SN_* 繁简一致 + label==sourceVolume + 属性齐全").isEmpty();
    }
}
