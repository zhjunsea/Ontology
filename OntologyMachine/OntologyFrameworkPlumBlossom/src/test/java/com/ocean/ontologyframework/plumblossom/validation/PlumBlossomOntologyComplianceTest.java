package com.ocean.ontologyframework.plumblossom.validation;

import openllet.owlapi.OpenlletReasonerFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.formats.TurtleDocumentFormat;
import org.semanticweb.owlapi.io.FileDocumentSource;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.semanticweb.owlapi.util.AutoIRIMapper;
import org.semanticweb.owlapi.util.SimpleIRIMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PlumBlossom 本体合规校验（纯 OWL API + Openllet，离线，不依赖任何外部服务）。
 *
 * <p>被测对象：第 9 步拆分后的两个入口
 * ——TBox 聚合入口 {@code ontology/plumblossom-all.owl}（显式 imports {@code plumblossom-tbox.owl}）
 * 与 ABox 聚合入口 {@code ontology/plumblossom-abox.owl}（imports {@code plumblossom-tbox.owl}）。
 * 依铁律 11，{@code plumblossom-all.owl} 已注释掉对 ABox 的 imports，故测试以与框架一致的
 * {@link AutoIRIMapper} 解析 imports，并把「main-path + abox-path」两条加载路径合并为闭包视图。
 *
 * <p>校验：可解析性、一致性、六十四卦名录完整性、八卦域知识、四不占、吉凶等级、Restriction/SWRL 结构。
 *
 * <p>第 11 步（闸门⑩）：历法/干支与周易文本（含六十四卦名录 {@code Seq_*}）已入库
 * （{@code ontology/database/{createdb_tables.ddl,data.sql}}），DB 为唯一真源。故 P-2 改为
 * 校验 {@code data.sql} 灌入 64 个卦序列个体，并断言本体 ABox 不再保留 {@code Seq_*}。
 *
 * <p>第 13 步（闸门⑥）：{@code plumblossom-tbox.owl} 增加对 SKOS 术语表
 * （{@code http://example.org/plumblossom/term} → {@code plumblossom-terminology.ttl}）的
 * {@code owl:imports}；因 {@link AutoIRIMapper} 不处理 {@code .ttl}，本类加载与框架 OntologyService
 * 一致，为 {@code .ttl} 补注册 {@link SimpleIRIMapper}（Turtle 显式解析）。
 *
 * <p>注：本体由带前缀的 OWLAPI 格式加载，实体 IRI 保留短前缀形式（如 {@code yijing:Seq_01}），
 * 故按签名 IRI 的**片段包含**进行断言，不硬编码完整 IRI 串。
 */
@DisplayName("梅花易数本体合规校验：解析 / 一致性 / 域知识完整性（离线）")
public class PlumBlossomOntologyComplianceTest {

    private static final Logger log = LoggerFactory.getLogger(PlumBlossomOntologyComplianceTest.class);

    private static final Path MAIN_FILE = Path.of("ontology", "plumblossom-all.owl");
    private static final Path ABOX_FILE = Path.of("ontology", "plumblossom-abox.owl");
    private static final Path DATA_SQL = Path.of("ontology", "database", "data.sql");

    /** SKOS 术语表命名空间（第 13 步）：其 skos:Concept 个体不计入 OWL 本体个体统计。 */
    private static final String SKOS_TERM_NS = "http://example.org/plumblossom/term";

    private static OWLOntology mergedOntology;
    private static List<OWLOntology> closure;
    private static Set<String> individualIris;

    @BeforeAll
    static void loadOntology() throws Exception {
        assertThat(Files.isRegularFile(MAIN_FILE))
                .as("TBox 入口应存在: %s (cwd=%s)", MAIN_FILE.toAbsolutePath(), Path.of("").toAbsolutePath())
                .isTrue();
        assertThat(Files.isRegularFile(ABOX_FILE))
                .as("ABox 入口应存在: %s", ABOX_FILE.toAbsolutePath())
                .isTrue();

        OWLOntologyManager manager = OWLManager.createOWLOntologyManager();

        // 与框架 OntologyService 一致：扫描本体目录，自动解析 owl:imports
        File parentDir = MAIN_FILE.toFile().getParentFile();
        AutoIRIMapper autoMapper = new AutoIRIMapper(parentDir, true);
        autoMapper.update();
        manager.getIRIMappers().add(autoMapper);

        // 与框架 OntologyService 一致：为 .ttl 本体（第 13 步 SKOS 术语表）注册 SimpleIRIMapper
        // ——AutoIRIMapper 不处理 .ttl，须显式以 Turtle 格式解析后按 ontologyIRI 映射。
        OWLOntologyManager tempManager = OWLManager.createOWLOntologyManager();
        tempManager.getOntologyConfigurator()
                .setMissingImportHandlingStrategy(MissingImportHandlingStrategy.SILENT);
        File[] ttlFiles = parentDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".ttl"));
        if (ttlFiles != null) {
            for (File ttlFile : ttlFiles) {
                try {
                    OWLOntology temp = tempManager.loadOntologyFromOntologyDocument(
                            new FileDocumentSource(ttlFile, new TurtleDocumentFormat()));
                    temp.getOntologyID().getOntologyIRI().ifPresent(
                            iri -> manager.getIRIMappers().add(new SimpleIRIMapper(iri, IRI.create(ttlFile))));
                    tempManager.removeOntology(temp);
                } catch (Exception e) {
                    log.warn("无法解析 TTL {}: {}", ttlFile.getName(), e.getMessage());
                }
            }
        }

        // 对应框架的两条加载路径：ontology.main-path（TBox）与 ontology.abox-path（ABox）
        OWLOntology main = manager.loadOntologyFromOntologyDocument(MAIN_FILE.toFile());
        OWLOntology abox = manager.loadOntologyFromOntologyDocument(ABOX_FILE.toFile());
        assertThat(main.getOntologyID().getOntologyIRI()).isPresent();
        assertThat(abox.getOntologyID().getOntologyIRI()).isPresent();

        // 合并视图：同时 imports main-path 与 abox-path，得到与分离前等价的闭包
        OWLDataFactory df = manager.getOWLDataFactory();
        mergedOntology = manager.createOntology(IRI.create("urn:plumblossom:test:merged"));
        manager.applyChange(new AddImport(mergedOntology,
                df.getOWLImportsDeclaration(IRI.create("http://example.org/plumblossom/all"))));
        manager.applyChange(new AddImport(mergedOntology,
                df.getOWLImportsDeclaration(IRI.create("http://example.org/plumblossom/abox"))));

        closure = mergedOntology.importsClosure().collect(Collectors.toList());
        individualIris = closure.stream()
                .flatMap(o -> o.individualsInSignature())
                .map(i -> i.getIRI().toString())
                // 第 13 步：SKOS 术语表的 skos:Concept 个体不计入 OWL 本体个体统计
                .filter(i -> !i.startsWith(SKOS_TERM_NS))
                .collect(Collectors.toSet());
        log.info("本体加载成功 | closureSize={} | axioms={} | individuals(OWL)={}",
                closure.size(), closure.stream().mapToInt(OWLOntology::getAxiomCount).sum(),
                individualIris.size());
    }

    @Test
    @DisplayName("P-1 本体可解析且逻辑一致（Openllet isConsistent）")
    void ontologyLoadsAndIsConsistent() {
        OWLReasoner reasoner = OpenlletReasonerFactory.getInstance().createReasoner(mergedOntology);
        try {
            reasoner.flush();
            assertThat(reasoner.isConsistent()).as("本体应逻辑一致（一致性的必要前提）").isTrue();
        } finally {
            reasoner.dispose();
        }
    }

    @Test
    @DisplayName("P-2 六十四卦名录完整（data.sql 灌入 64 行；本体 ABox 不再保留 Seq_*）")
    void hexagramSequenceComplete() throws Exception {
        // 第 11 步（闸门⑩）：卦名/卦序入库，DB 为唯一真源——名录完整性改由数据脚本校验。
        assertThat(Files.isRegularFile(DATA_SQL))
                .as("数据脚本应存在: %s (cwd=%s)", DATA_SQL.toAbsolutePath(), Path.of("").toAbsolutePath())
                .isTrue();
        String sql = Files.readString(DATA_SQL, StandardCharsets.UTF_8);
        Set<String> seqs = new HashSet<>();
        Matcher m = Pattern.compile("\\('(Seq_\\d{2})'").matcher(sql);
        while (m.find()) {
            seqs.add(m.group(1));
        }
        assertThat(seqs).as("data.sql 应灌入 64 个六十四卦序列个体").hasSize(64);
        assertThat(seqs).as("应含首尾卦").contains("Seq_01", "Seq_64");

        // ABox 侧应已移除 Seq_*（DB 为唯一真源）
        assertThat(individualIris.stream().filter(i -> i.contains("Seq_")))
                .as("本体 ABox 不应再保留 Seq_* 个体（已入库）").isEmpty();
    }

    @Test
    @DisplayName("P-3 八卦域知识完整（*Trigram 个体 = 8）")
    void trigramKnowledgeComplete() {
        long trigrams = individualIris.stream().filter(iri -> iri.contains("Trigram")).count();
        assertThat(trigrams).as("八卦个体应齐备").isEqualTo(8);

        for (String name : List.of("QianTrigram", "DuiTrigram", "LiTrigram", "ZhenTrigram",
                "XunTrigram", "KanTrigram", "GenTrigram", "KunTrigram")) {
            assertThat(individualIris).as("应含 %s", name).anyMatch(iri -> iri.contains(name));
        }
    }

    @Test
    @DisplayName("P-4 四不占条目齐备（div:Precept_* = 4）")
    void preceptEntriesPresent() {
        Set<String> precepts = individualIris.stream()
                .filter(iri -> iri.contains("Precept_"))
                .collect(Collectors.toSet());
        assertThat(precepts).as("四不占条目应齐备").hasSize(4);
        for (String name : List.of("BuCheng", "BuYi", "ChongGua", "WuShi")) {
            assertThat(precepts).as("应含预戒 %s", name).anyMatch(iri -> iri.contains("Precept_" + name));
        }
    }

    @Test
    @DisplayName("P-5 吉凶五级与事项类别齐备（jud:DaJi… + jud:Cat_*）")
    void auspiciousnessLevelsPresent() {
        for (String level : List.of("DaJi", "Ji", "Ping", "Xiong", "DaXiong")) {
            assertThat(individualIris).as("应含吉凶等级 %s", level).anyMatch(iri -> iri.contains(level));
        }
        long categories = individualIris.stream().filter(iri -> iri.contains("Cat_")).count();
        assertThat(categories).as("原著十八章事项类别应齐备").isGreaterThanOrEqualTo(18);
    }

    @Test
    @DisplayName("B-1 Restriction 与 SWRL 规则结构存在（imports 闭包内）")
    void restrictionsAndSwrlRulesPresent() {
        long subClassOf = closure.stream()
                .mapToLong(o -> o.axioms(AxiomType.SUBCLASS_OF).count()).sum();
        long swrlRules = closure.stream()
                .mapToLong(o -> o.axioms(AxiomType.SWRL_RULE).count()).sum();
        assertThat(subClassOf).as("应存在 SubClassOf（含 owl:Restriction）公理").isGreaterThan(0);
        assertThat(swrlRules).as("应存在 SWRL 规则（R1~R14）").isGreaterThan(0);
    }

    // ==========================================================================
    // B-2 逐例比对：本体生克推理 vs 内置计算（全 8×8 体用组合 ⇔ 64 卦 × 6 动爻）
    // ==========================================================================

    private static final List<String> TRIGRAM_FRAGMENTS = List.of(
            "QianTrigram", "DuiTrigram", "LiTrigram", "ZhenTrigram",
            "XunTrigram", "KanTrigram", "GenTrigram", "KunTrigram");

    /** 内置计算表（与 {@code PlumBlossomOntologyJobWorker} 内置表一致）：八卦五行。 */
    private static final Map<String, String> BUILTIN_TRI_PHASE = Map.of(
            "QianTrigram", "Jin", "DuiTrigram", "Jin", "LiTrigram", "Huo", "ZhenTrigram", "Mu",
            "XunTrigram", "Mu", "KanTrigram", "Shui", "GenTrigram", "Tu", "KunTrigram", "Tu");

    /** 内置五行相生（五元环：金→水→木→火→土→金）。 */
    private static final Set<String> BUILTIN_GEN =
            Set.of("Jin>Shui", "Shui>Mu", "Mu>Huo", "Huo>Tu", "Tu>Jin");

    /** 内置五行相克（五元环：金→木→土→水→火→金）。 */
    private static final Set<String> BUILTIN_KE =
            Set.of("Jin>Mu", "Mu>Tu", "Tu>Shui", "Shui>Huo", "Huo>Jin");

    private static String localName(IRI iri) {
        String s = iri.toString();
        int i = Math.max(Math.max(s.lastIndexOf('#'), s.lastIndexOf('/')), s.lastIndexOf(':'));
        return i >= 0 ? s.substring(i + 1) : s;
    }

    private static String resolveIndividualIri(String fragment) {
        return individualIris.stream()
                .filter(i -> localName(IRI.create(i)).equals(fragment))
                .findFirst()
                .orElseThrow(() -> new AssertionError("本体个体缺失：" + fragment));
    }

    /** 由体、用五行与生克对推 5 级生克类型（1 用生体 / 2 比和 / 3 体克用 / 4 体生用 / 5 用克体）。 */
    private static int shengKeLevel(String bodyPhase, String usePhase, Set<String> gen, Set<String> ke) {
        if (bodyPhase.equals(usePhase)) return 2;
        if (gen.contains(usePhase + ">" + bodyPhase)) return 1;
        if (ke.contains(bodyPhase + ">" + usePhase)) return 3;
        if (gen.contains(bodyPhase + ">" + usePhase)) return 4;
        if (ke.contains(usePhase + ">" + bodyPhase)) return 5;
        throw new AssertionError("无法归类体用生克：" + bodyPhase + "/" + usePhase);
    }

    /** 五行个体短名。 */
    private static final Set<String> PHASES = Set.of("Jin", "Mu", "Shui", "Huo", "Tu");

    private static void acc(Map<String, String> triPhase, Set<String> gen, Set<String> ke,
                            String prop, String subject, String value) {
        if (prop.equals("hasFivePhase")) {
            triPhase.put(subject, value);
        } else if (prop.equals("generates")) {
            if (PHASES.contains(subject) && PHASES.contains(value)) {
                gen.add(subject + ">" + value);
            }
        } else if (prop.equals("overcomes")) {
            if (PHASES.contains(subject) && PHASES.contains(value)) {
                ke.add(subject + ">" + value);
            }
        }
    }

    /** 从本体闭包读取：八卦→五行 映射、五行相生对、五行相克对（对象属性断言与注解断言皆纳入）。 */
    private static Object[] readOntologyShengKe() {
        Map<String, String> triPhase = new LinkedHashMap<>();
        Set<String> gen = new HashSet<>();
        Set<String> ke = new HashSet<>();
        for (OWLOntology o : closure) {
            for (OWLObjectPropertyAssertionAxiom a
                    : o.axioms(AxiomType.OBJECT_PROPERTY_ASSERTION).collect(Collectors.toList())) {
                if (a.getSubject().isAnonymous() || a.getObject().isAnonymous()
                        || !(a.getProperty() instanceof OWLObjectProperty)) {
                    continue;
                }
                OWLObjectProperty prop = (OWLObjectProperty) a.getProperty();
                acc(triPhase, gen, ke, localName(prop.getIRI()),
                        localName(a.getSubject().asOWLNamedIndividual().getIRI()),
                        localName(a.getObject().asOWLNamedIndividual().getIRI()));
            }
            for (OWLAnnotationAssertionAxiom a
                    : o.axioms(AxiomType.ANNOTATION_ASSERTION).collect(Collectors.toList())) {
                if (!(a.getSubject() instanceof IRI) || !(a.getValue() instanceof IRI)) {
                    continue;
                }
                acc(triPhase, gen, ke, localName(a.getProperty().getIRI()),
                        localName((IRI) a.getSubject()), localName((IRI) a.getValue()));
            }
        }
        return new Object[]{triPhase, gen, ke};
    }

    @Test
    @DisplayName("B-2 逐例比对：本体生克推理（R3~R7）vs 内置计算（全 8×8 体用组合）")
    @SuppressWarnings("unchecked")
    void ontologyShengKeMatchesBuiltin() {
        Object[] read = readOntologyShengKe();
        Map<String, String> ontoTriPhase = (Map<String, String>) read[0];
        Set<String> ontoGen = (Set<String>) read[1];
        Set<String> ontoKe = (Set<String>) read[2];

        // 本体应显式定义五元环相生/相克（B1：本体为唯一真源，供 R3~R7 推理）
        assertThat(ontoGen).as("本体应显式定义 5 组相生（金→水→木→火→土→金）").hasSize(5);
        assertThat(ontoKe).as("本体应显式定义 5 组相克（金→木→土→水→火→金）").hasSize(5);
        assertThat(ontoGen).as("本体相生应与内置一致").isEqualTo(BUILTIN_GEN);
        assertThat(ontoKe).as("本体相克应与内置一致").isEqualTo(BUILTIN_KE);
        for (String frag : TRIGRAM_FRAGMENTS) {
            assertThat(ontoTriPhase.get(frag)).as("本体应定义 %s 的五行", frag).isNotNull();
        }

        // 逐例比对全部 8×8 体用组合（覆盖 64 卦 × 6 动爻的全部体用配置）
        StringBuilder diff = new StringBuilder();
        int mismatches = 0;
        for (String b : TRIGRAM_FRAGMENTS) {
            for (String u : TRIGRAM_FRAGMENTS) {
                int viaOntology = shengKeLevel(ontoTriPhase.get(b), ontoTriPhase.get(u), ontoGen, ontoKe);
                int viaBuiltin = shengKeLevel(BUILTIN_TRI_PHASE.get(b), BUILTIN_TRI_PHASE.get(u),
                        BUILTIN_GEN, BUILTIN_KE);
                if (viaOntology != viaBuiltin) {
                    mismatches++;
                    diff.append(b).append('/').append(u).append("(本体=").append(viaOntology)
                            .append(",内置=").append(viaBuiltin).append("); ");
                }
            }
        }
        assertThat(mismatches).as("本体推理与内置计算应逐例一致，差异：%s", diff).isZero();
        log.info("✅ B-2 数据一致性：8×8=64 组体用组合全一致 | 本体相生={} | 本体相克={}", ontoGen, ontoKe);
    }

    @Test
    @DisplayName("B-2b 推理实跑：Openllet 经 R3~R7 推出 rel:shengKeType（构造全 8×8 体用个体）")
    @SuppressWarnings("unchecked")
    void ontologyShengKeInferredByReasoner() throws Exception {
        Object[] read = readOntologyShengKe();
        Map<String, String> ontoTriPhase = (Map<String, String>) read[0];
        Set<String> ontoGen = (Set<String>) read[1];
        Set<String> ontoKe = (Set<String>) read[2];

        OWLOntologyManager manager = mergedOntology.getOWLOntologyManager();
        OWLDataFactory df = manager.getOWLDataFactory();

        OWLObjectProperty hasBody = closure.stream().flatMap(o -> o.objectPropertiesInSignature())
                .filter(p -> localName(p.getIRI()).equals("hasBodyTrigram")).findFirst().orElseThrow();
        OWLObjectProperty hasUse = closure.stream().flatMap(o -> o.objectPropertiesInSignature())
                .filter(p -> localName(p.getIRI()).equals("hasUseTrigram")).findFirst().orElseThrow();
        OWLDataProperty shengKeType = closure.stream().flatMap(o -> o.dataPropertiesInSignature())
                .filter(p -> localName(p.getIRI()).equals("shengKeType")).findFirst().orElseThrow();
        OWLClass buClass = closure.stream().flatMap(o -> o.classesInSignature())
                .filter(c -> localName(c.getIRI()).equals("BodyUseRelation")).findFirst().orElseThrow();

        Map<String, OWLNamedIndividual> tri = new LinkedHashMap<>();
        for (String frag : TRIGRAM_FRAGMENTS) {
            tri.put(frag, df.getOWLNamedIndividual(IRI.create(resolveIndividualIri(frag))));
        }

        OWLOntology probe = manager.createOntology(IRI.create("urn:plumblossom:test:shengke-probe"));
        // 自包含探针：直接并入加载闭包的全部公理（含 SWRL 规则与 TBox 定义），
        // 规避「Openllet 是否处理 import 闭包内规则」的不确定性。
        Set<OWLAxiom> seed = new HashSet<>();
        for (OWLOntology o : closure) {
            seed.addAll(o.getAxioms());
        }
        manager.addAxioms(probe, seed);

        Map<String, OWLNamedIndividual> pairs = new LinkedHashMap<>();
        for (String b : TRIGRAM_FRAGMENTS) {
            for (String u : TRIGRAM_FRAGMENTS) {
                OWLNamedIndividual r = df.getOWLNamedIndividual(
                        IRI.create("urn:plumblossom:test:bu#" + b + "_" + u));
                pairs.put(b + "_" + u, r);
                manager.applyChange(new AddAxiom(probe, df.getOWLClassAssertionAxiom(buClass, r)));
                manager.applyChange(new AddAxiom(probe,
                        df.getOWLObjectPropertyAssertionAxiom(hasBody, r, tri.get(b))));
                manager.applyChange(new AddAxiom(probe,
                        df.getOWLObjectPropertyAssertionAxiom(hasUse, r, tri.get(u))));
            }
        }

        OWLReasoner reasoner = OpenlletReasonerFactory.getInstance().createReasoner(probe);
        try {
            reasoner.flush();
            StringBuilder diff = new StringBuilder();
            int missing = 0;
            for (Map.Entry<String, OWLNamedIndividual> e : pairs.entrySet()) {
                String[] bu = e.getKey().split("_");
                int expected = shengKeLevel(ontoTriPhase.get(bu[0]), ontoTriPhase.get(bu[1]), ontoGen, ontoKe);
                Set<Integer> inferred = reasoner.getDataPropertyValues(e.getValue(), shengKeType).stream()
                        .map(OWLLiteral::parseInteger).collect(Collectors.toSet());
                if (!inferred.contains(expected)) {
                    missing++;
                    diff.append(e.getKey()).append("(期望=").append(expected)
                            .append(",推理=").append(inferred).append("); ");
                }
            }
            assertThat(missing)
                    .as("Openllet 应经 R3~R7 为全部 8×8 体用对推出 rel:shengKeType，差异：%s", diff)
                    .isZero();
            log.info("✅ B-2b 推理实跑：Openllet 经 R3~R7 推出全部 8×8=64 组 rel:shengKeType");
        } finally {
            reasoner.dispose();
        }
    }
}
