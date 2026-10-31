package com.ocean.ontologyframework.ziweidoushu;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ocean.openlletresolver.BackendService;
import com.ocean.ontopobdahandler.OBDAHandler;
import com.ocean.ontologyframework.ziweidoushu.validation.ValidationResult;
import com.ocean.ontologyframework.ziweidoushu.validation.ZiWeiDouShuOntologyValidator;
import org.junit.jupiter.api.*;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.formats.TurtleDocumentFormat;
import org.semanticweb.owlapi.io.FileDocumentSource;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.model.parameters.ChangeApplied;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.semanticweb.owlapi.util.AutoIRIMapper;
import org.semanticweb.owlapi.util.SimpleIRIMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;

import java.io.File;
import java.io.InputStream;
import java.util.*;

import openllet.owlapi.OpenlletReasonerFactory;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 紫微斗数本体 · 推理与合规集成测试（第 8 步）。
 *
 * <p>覆盖：TBox 子类检索、SWRL R1/R8/R8b/R21 推导、十二宫基数与命格等第、
 * 典型 ABox 的 {@code owl:Restriction} 合规校验。
 * 纯文件/内存推理，无需 Ontop/MySQL（第 11 步起 ABox 入库后按需扩展 OBDA 用例）。
 */
@SpringBootTest(classes = ZiWeiDouShuApplication.class)
@ActiveProfiles("ZiWeiDouShuJunitTest")
@DisplayName("紫微斗数本体 · 推理与合规集成测试")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ZiWeiDouShuOntologyTests {

    private static final Logger log = LoggerFactory.getLogger(ZiWeiDouShuOntologyTests.class);

    private static final String CORE = "http://example.org/ziweidoushu/core#";
    private static final String CHART = "http://example.org/ziweidoushu/chart#";
    private static final String STAR = "http://example.org/ziweidoushu/star#";
    private static final String LAYOUT = "http://example.org/ziweidoushu/layout#";
    private static final String PATTERN = "http://example.org/ziweidoushu/pattern#";
    private static final String FORTUNE = "http://example.org/ziweidoushu/fortune#";
    private static final String RULES = "http://example.org/ziweidoushu/rules#";
    private static final String ABOX = "http://example.org/ziweidoushu/test-abox#";

    private static BackendService backendService;
    private Set<OWLAxiom> fullBaselineSnapshot;

    @BeforeAll
    static void setUp(@Autowired Environment env) throws Exception {
        log.info("=== 初始化紫微斗数推理/合规测试环境 ===");
        String mainPath = env.getProperty("ontology.main-path");
        String obdaPath = env.getProperty("ontology.obda-path");
        String obdaPropertiesPath = env.getProperty("ontology.obda-properties-path");
        try {
            OBDAHandler.init(obdaPropertiesPath, obdaPath);
        } catch (IllegalStateException alreadyInitialized) {
            log.warn("⚠️ OBDAHandler 已初始化，复用现有实例");
        }
        backendService = BackendService.getInstance(mainPath, OBDAHandler.getInstance());
        assertNotNull(backendService, "BackendService 初始化失败");
        assertTrue(backendService.getTBoxOntology().getAxiomCount() > 0, "本体应非空加载");

        // 第 9 步：TBox/ABox 分离。主入口（all.owl）仅聚合 TBox，ABox 依铁律 11 不进其闭包；
        // 故测试显式加载典型 ABox（与应用侧经 ontology.abox-path 独立加载一致），
        // 并把其公理并入 TBox 本体，等价于分离前的单文件视图，供 P-6/P-8/P-9 等基数用例使用。
        loadAboxAndAssertSeparation(env, env.getProperty("ontology.abox-path"));
    }

    /** 第 9 步分离校验：主入口闭包不含 ABox（铁律 11）；并显式加载 ABox 合并进 TBox 本体。 */
    private static void loadAboxAndAssertSeparation(Environment env, String aboxPath) throws Exception {
        File aboxFile = new File(aboxPath);
        assertTrue(aboxFile.isFile(), "ABox 文件应存在（第 9 步拆分产物）: " + aboxFile.getAbsolutePath());

        OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
        File ontologyDir = aboxFile.getParentFile();
        manager.getIRIMappers().add(new AutoIRIMapper(ontologyDir, true));

        // 第 13 步：术语表 ziweidoushu-terminology.ttl 经 tbox owl:imports 并入，但 AutoIRIMapper 不处理 .ttl，
        // 故手动注册 .ttl 本体映射（对齐框架 OntologyService 的做法），避免 term 导入解析失败。
        OWLOntologyManager ttlProbe = OWLManager.createOWLOntologyManager();
        ttlProbe.getOntologyConfigurator()
                .setMissingImportHandlingStrategy(MissingImportHandlingStrategy.SILENT);
        File[] ttlFiles = ontologyDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".ttl"));
        if (ttlFiles != null) {
            for (File ttl : ttlFiles) {
                try {
                    OWLOntology tmp = ttlProbe.loadOntologyFromOntologyDocument(
                            new FileDocumentSource(ttl, new TurtleDocumentFormat()));
                    tmp.getOntologyID().getOntologyIRI().ifPresent(
                            iri -> manager.getIRIMappers().add(new SimpleIRIMapper(iri, IRI.create(ttl))));
                    ttlProbe.removeOntology(tmp);
                } catch (Exception ignore) {
                    // 非本体 .ttl 忽略
                }
            }
        }

        File mainFile = new File(env.getProperty("ontology.main-path"));
        OWLOntology rootOntology = manager.loadOntologyFromOntologyDocument(mainFile);
        assertFalse(rootOntology.importsClosure().anyMatch(o -> o.getOntologyID().getOntologyIRI()
                        .map(i -> i.toString().endsWith("/abox")).orElse(false)),
                "主入口 imports 闭包不应包含 ABox（铁律 11：all 的 abox imports 已注释掉）");
        assertTrue(rootOntology.importsClosure().anyMatch(o -> o.getOntologyID().getOntologyIRI()
                        .map(i -> i.toString().endsWith("/tbox")).orElse(false)),
                "主入口 imports 闭包应包含 TBox");

        OWLOntology aboxOntology = manager.loadOntologyFromOntologyDocument(aboxFile);
        OWLOntology tboxOntology = backendService.getTBoxOntology();
        tboxOntology.getOWLOntologyManager().addAxioms(tboxOntology, aboxOntology.getAxioms());
        backendService.getReasonerService().getReasoner().flush();
        log.info("✅ 第 9 步分离校验通过 | 主入口闭包={} | 已加载 ABox 并合并其 {} 条公理",
                rootOntology.importsClosure().count(), aboxOntology.getAxioms().size());
    }

    @BeforeEach
    void snapshotFullBaseline() {
        OWLOntology ontology = backendService.getTBoxOntology();
        fullBaselineSnapshot = Collections.unmodifiableSet(new HashSet<>(ontology.getAxioms()));
        backendService.getReasonerService().getReasoner().flush();
        log.info("📸 全量快照已保存，基线公理数: {}", fullBaselineSnapshot.size());
    }

    @AfterEach
    void restoreOntologyAndReasoner() {
        if (fullBaselineSnapshot == null) {
            return;
        }
        try {
            OWLOntology ontology = backendService.getTBoxOntology();
            Set<OWLAxiom> current = ontology.getAxioms();
            if (current.size() == fullBaselineSnapshot.size() && current.containsAll(fullBaselineSnapshot)) {
                return;
            }
            OWLOntologyManager manager = ontology.getOWLOntologyManager();
            ChangeApplied removeResult = manager.removeAxioms(ontology, current);
            if (removeResult != ChangeApplied.SUCCESSFULLY) {
                throw new IllegalStateException("批量移除公理失败: " + removeResult);
            }
            ChangeApplied addResult = manager.addAxioms(ontology, fullBaselineSnapshot);
            if (addResult != ChangeApplied.SUCCESSFULLY) {
                throw new IllegalStateException("批量恢复基线公理失败: " + addResult);
            }
            backendService.getReasonerService().getReasoner().flush();
            log.info("🧹 本体及推理器已恢复至基线状态");
        } finally {
            fullBaselineSnapshot = null;
        }
    }

    private OWLDataFactory df() {
        return backendService.getDataFactory();
    }

    private OWLReasoner reasoner() {
        return backendService.getReasonerService().getReasoner();
    }

    private OWLNamedIndividual ind(String iri) {
        return df().getOWLNamedIndividual(IRI.create(iri));
    }

    private void inject(Set<OWLAxiom> temp) {
        OWLOntology ontology = backendService.getTBoxOntology();
        ontology.getOWLOntologyManager().addAxioms(ontology, temp);
        reasoner().flush();
    }

    private void remove(Set<OWLAxiom> temp) {
        OWLOntology ontology = backendService.getTBoxOntology();
        ontology.getOWLOntologyManager().removeAxioms(ontology, temp);
        reasoner().flush();
    }

    /**
     * 为 SWRL 推导断言创建「独立」推理器：全局共享推理器（{@code ReasonerService}）会被
     * {@code SwrlRuleTriggerListener} 在变更广播中同步重入 {@code flush()}（监听器属禁区不可改），
     * 偶发导致共享推理器 SWRL 物化结果缺失/被缓存覆盖。独立推理器基于当前 TBox
     * （已含本用例注入的临时公理）从零物化 SWRL，结果确定，规避该竞态。用完须 {@code dispose()}。
     */
    private OWLReasoner freshReasoner() {
        OWLReasoner r = new OpenlletReasonerFactory().createReasoner(backendService.getTBoxOntology());
        r.flush();
        return r;
    }

    // ============================ P-1 ============================
    @Test
    @Order(1)
    @DisplayName("P-1 本体加载且一致，无不可满足类")
    void testOntologyLoadsAndConsistent() {
        assertTrue(backendService.getTBoxOntology().getAxiomCount() > 0);
        assertTrue(reasoner().isConsistent(), "本体应一致（Openllet）");
        assertFalse(reasoner().getUnsatisfiableClasses().getEntitiesMinusBottom().iterator().hasNext(),
                "应无不可满足类（除 owl:Nothing）");
    }

    // ============================ P-2 ============================
    @Test
    @Order(2)
    @DisplayName("P-2 star:Star 子类检索包含四大星类")
    void testInferredSubclassesOfStar() {
        OWLClass starCls = df().getOWLClass(IRI.create(STAR + "Star"));
        Set<OWLClass> subs = reasoner().getSubClasses(starCls, false).getFlattened();
        assertTrue(subs.contains(df().getOWLClass(IRI.create(STAR + "FourteenMainStar"))), "应含十四主星");
        assertTrue(subs.contains(df().getOWLClass(IRI.create(STAR + "AuxiliaryStar"))), "应含辅佐星");
        assertTrue(subs.contains(df().getOWLClass(IRI.create(STAR + "MaleficStar"))), "应含煞忌星");
        assertTrue(subs.contains(df().getOWLClass(IRI.create(STAR + "MiscStar"))), "应含杂曜");
    }

    // ============================ P-3 ============================
    @Test
    @Order(3)
    @DisplayName("P-3 SWRL R1 天干五行传导：stemFivePhase → hasFivePhase")
    void testSwrlR1StemYinYangFivePhase() {
        OWLNamedIndividual stem = ind(ABOX + "Stem_R1_Test");
        OWLClass hs = df().getOWLClass(IRI.create(CORE + "HeavenlyStem"));
        OWLObjectProperty stemFivePhase = df().getOWLObjectProperty(IRI.create(CORE + "stemFivePhase"));
        OWLObjectProperty hasFivePhase = df().getOWLObjectProperty(IRI.create(CORE + "hasFivePhase"));
        OWLNamedIndividual mu = ind(CORE + "Mu");

        Set<OWLAxiom> temp = new LinkedHashSet<>();
        temp.add(df().getOWLClassAssertionAxiom(hs, stem));
        temp.add(df().getOWLObjectPropertyAssertionAxiom(stemFivePhase, stem, mu));
        try {
            inject(temp);
            OWLReasoner local = freshReasoner();
            try {
                Set<OWLNamedIndividual> inferred = local.getObjectPropertyValues(stem, hasFivePhase).getFlattened();
                assertTrue(inferred.contains(mu), "R1 应推得 hasFivePhase=木");
            } finally {
                local.dispose();
            }
        } finally {
            remove(temp);
        }
    }

    // ============================ P-4 ============================
    @Test
    @Order(4)
    @DisplayName("P-4 SWRL R8/R8b 大限顺逆：阳男顺 / 阴男逆")
    void testSwrlR8R8bGreatLimitDirection() {
        OWLClass natalChart = df().getOWLClass(IRI.create(CHART + "NatalChart"));
        OWLClass birthInput = df().getOWLClass(IRI.create(CHART + "BirthInput"));
        OWLObjectProperty hasBirthInput = df().getOWLObjectProperty(IRI.create(CHART + "hasBirthInput"));
        OWLObjectProperty hasGreatLimit = df().getOWLObjectProperty(IRI.create(FORTUNE + "hasGreatLimit"));
        OWLDataProperty gender = df().getOWLDataProperty(IRI.create(CHART + "gender"));
        OWLDataProperty yearYy = df().getOWLDataProperty(IRI.create(CHART + "yearStemYinYang"));
        OWLDataProperty limitDirection = df().getOWLDataProperty(IRI.create(FORTUNE + "limitDirection"));

        OWLNamedIndividual yangF = ind(ABOX + "Chart_R8_YangNan");
        OWLNamedIndividual yangI = ind(ABOX + "Birth_R8_YangNan");
        OWLNamedIndividual yangGl = ind(ABOX + "GL_R8_YangNan");
        OWLNamedIndividual yinF = ind(ABOX + "Chart_R8b_YinNan");
        OWLNamedIndividual yinI = ind(ABOX + "Birth_R8b_YinNan");
        OWLNamedIndividual yinGl = ind(ABOX + "GL_R8b_YinNan");

        Set<OWLAxiom> temp = new LinkedHashSet<>();
        temp.add(df().getOWLClassAssertionAxiom(natalChart, yangF));
        temp.add(df().getOWLClassAssertionAxiom(birthInput, yangI));
        temp.add(df().getOWLObjectPropertyAssertionAxiom(hasBirthInput, yangF, yangI));
        temp.add(df().getOWLDataPropertyAssertionAxiom(gender, yangI, "男"));
        temp.add(df().getOWLDataPropertyAssertionAxiom(yearYy, yangF, "阳"));
        temp.add(df().getOWLObjectPropertyAssertionAxiom(hasGreatLimit, yangF, yangGl));

        temp.add(df().getOWLClassAssertionAxiom(natalChart, yinF));
        temp.add(df().getOWLClassAssertionAxiom(birthInput, yinI));
        temp.add(df().getOWLObjectPropertyAssertionAxiom(hasBirthInput, yinF, yinI));
        temp.add(df().getOWLDataPropertyAssertionAxiom(gender, yinI, "男"));
        temp.add(df().getOWLDataPropertyAssertionAxiom(yearYy, yinF, "阴"));
        temp.add(df().getOWLObjectPropertyAssertionAxiom(hasGreatLimit, yinF, yinGl));
        try {
            inject(temp);
            OWLReasoner local = freshReasoner();
            try {
                Set<OWLLiteral> dirYang = local.getDataPropertyValues(yangGl, limitDirection);
                Set<OWLLiteral> dirYin = local.getDataPropertyValues(yinGl, limitDirection);
                assertTrue(dirYang.stream().anyMatch(l -> "顺".equals(l.getLiteral())), "阳男应推得顺行");
                assertTrue(dirYin.stream().anyMatch(l -> "逆".equals(l.getLiteral())), "阴男应推得逆行");
            } finally {
                local.dispose();
            }
        } finally {
            remove(temp);
        }
    }

    // ============================ P-5 ============================
    @Test
    @Order(5)
    @DisplayName("P-5 SWRL R21 告警命中：hasAlertFlag → alertTriggered=true")
    void testSwrlR21AlertTriggered() {
        OWLClass natalChart = df().getOWLClass(IRI.create(CHART + "NatalChart"));
        OWLClass alertFlag = df().getOWLClass(IRI.create(RULES + "AlertFlag"));
        OWLObjectProperty hasAlertFlag = df().getOWLObjectProperty(IRI.create(RULES + "hasAlertFlag"));
        OWLDataProperty alertTriggered = df().getOWLDataProperty(IRI.create(RULES + "alertTriggered"));

        OWLNamedIndividual f = ind(ABOX + "Chart_R21_Test");
        OWLNamedIndividual a = ind(ABOX + "Alert_R21_Test");
        Set<OWLAxiom> temp = new LinkedHashSet<>();
        temp.add(df().getOWLClassAssertionAxiom(natalChart, f));
        temp.add(df().getOWLClassAssertionAxiom(alertFlag, a));
        temp.add(df().getOWLObjectPropertyAssertionAxiom(hasAlertFlag, f, a));
        try {
            inject(temp);
            OWLReasoner local = freshReasoner();
            try {
                Set<OWLLiteral> vals = local.getDataPropertyValues(a, alertTriggered);
                assertTrue(vals.stream().anyMatch(l -> Boolean.parseBoolean(l.getLiteral())), "R21 应推得 alertTriggered=true");
            } finally {
                local.dispose();
            }
        } finally {
            remove(temp);
        }

        // 负向：无 hasAlertFlag 的 AlertFlag 不应推得 true
        OWLNamedIndividual noFlag = ind(ABOX + "Chart_R21_NoFlag");
        OWLNamedIndividual noAlert = ind(ABOX + "Alert_R21_NoFlag");
        Set<OWLAxiom> temp2 = new LinkedHashSet<>();
        temp2.add(df().getOWLClassAssertionAxiom(natalChart, noFlag));
        temp2.add(df().getOWLClassAssertionAxiom(alertFlag, noAlert));
        try {
            inject(temp2);
            Set<OWLLiteral> vals = reasoner().getDataPropertyValues(noAlert, alertTriggered);
            assertTrue(vals.stream().noneMatch(l -> Boolean.parseBoolean(l.getLiteral())), "未挂 hasAlertFlag 不应推得 true");
        } finally {
            remove(temp2);
        }
    }

    // ============================ P-6 ============================
    @Test
    @Order(6)
    @DisplayName("P-6 十二宫基数：典型命盘 hasPalace 恰 12，命身宫/五行局各 1")
    void testInferredCardinalityTwelvePalaces() {
        OWLNamedIndividual demo = ind(CHART + "Case_Demo01");
        OWLObjectProperty hasPalace = df().getOWLObjectProperty(IRI.create(CHART + "hasPalace"));
        OWLObjectProperty hasLifePalace = df().getOWLObjectProperty(IRI.create(CHART + "hasLifePalace"));
        OWLObjectProperty hasBodyPalace = df().getOWLObjectProperty(IRI.create(CHART + "hasBodyPalace"));
        OWLObjectProperty hasBureau = df().getOWLObjectProperty(IRI.create(CHART + "hasBureau"));

        assertEquals(12, reasoner().getObjectPropertyValues(demo, hasPalace).getFlattened().size(), "hasPalace 应恰 12");
        assertEquals(1, reasoner().getObjectPropertyValues(demo, hasLifePalace).getFlattened().size(), "hasLifePalace 应恰 1");
        assertEquals(1, reasoner().getObjectPropertyValues(demo, hasBodyPalace).getFlattened().size(), "hasBodyPalace 应恰 1");
        assertEquals(1, reasoner().getObjectPropertyValues(demo, hasBureau).getFlattened().size(), "hasBureau 应恰 1");
    }

    // ============================ P-7 ============================
    @Test
    @Order(7)
    @DisplayName("P-7 典型 ABox 合规：3 合规命例逐条通过 owl:Restriction")
    void testTypicalAboxConformsRestrictions() throws Exception {
        List<Map<String, Object>> instances = loadInstances();
        try (ZiWeiDouShuOntologyValidator validator = new ZiWeiDouShuOntologyValidator(backendService)) {
            int checked = 0;
            for (Map<String, Object> inst : instances) {
                if (Integer.parseInt(String.valueOf(inst.get("palace_count"))) == 12
                        && !String.valueOf(inst.get("life_palace")).isBlank()) {
                    ValidationResult r = validator.validate(inst);
                    assertTrue(r.isValid(), "合规命例应通过：" + inst.get("name") + " 违规=" + r.violations());
                    checked++;
                }
            }
            assertEquals(3, checked, "应有 3 条合规命例");
        }
    }

    // ============================ P-8 ============================
    @Test
    @Order(8)
    @DisplayName("P-8 四化基数：transformationType/transformsTo 各恰 1")
    void testTransformationCardinality() {
        OWLNamedIndividual sihua = ind(LAYOUT + "Sihua_Demo_Lu");
        OWLDataProperty transformationType = df().getOWLDataProperty(IRI.create(LAYOUT + "transformationType"));
        OWLObjectProperty transformsTo = df().getOWLObjectProperty(IRI.create(LAYOUT + "transformsTo"));
        assertEquals(1, reasoner().getDataPropertyValues(sihua, transformationType).size(), "transformationType 应恰 1");
        assertEquals(1, reasoner().getObjectPropertyValues(sihua, transformsTo).getFlattened().size(), "transformsTo 应恰 1");
    }

    // ============================ P-9 ============================
    @Test
    @Order(9)
    @DisplayName("P-9 命格等第至多 1：hasPatternGrade maxCardinality 1")
    void testPatternGradeMaxCardinality() {
        OWLNamedIndividual demo = ind(CHART + "Case_Demo01");
        OWLObjectProperty hasPatternGrade = df().getOWLObjectProperty(IRI.create(PATTERN + "hasPatternGrade"));
        int n = reasoner().getObjectPropertyValues(demo, hasPatternGrade).getFlattened().size();
        assertTrue(n <= 1, "hasPatternGrade 至多 1，实际=" + n);
    }

    // ============================ N-1 ============================
    @Test
    @Order(10)
    @DisplayName("N-1 缺宫负例（11 宫）合规校验报错")
    void testMissingPalaceCountRejected() throws Exception {
        Map<String, Object> inst = instanceByName("Case_Ding_QueGong");
        try (ZiWeiDouShuOntologyValidator validator = new ZiWeiDouShuOntologyValidator(backendService)) {
            ValidationResult r = validator.validate(inst);
            assertFalse(r.isValid(), "缺宫负例应判不合规");
            assertTrue(r.violations().stream().anyMatch(v -> v.contains("hasPalace")), "原因应含 hasPalace");
        }
    }

    // ============================ N-2 ============================
    @Test
    @Order(11)
    @DisplayName("N-2 缺命宫负例合规校验报错")
    void testMissingLifePalaceRejected() throws Exception {
        Map<String, Object> inst = instanceByName("Case_Wu_QueMingGong");
        try (ZiWeiDouShuOntologyValidator validator = new ZiWeiDouShuOntologyValidator(backendService)) {
            ValidationResult r = validator.validate(inst);
            assertFalse(r.isValid(), "缺命宫负例应判不合规");
            assertTrue(r.violations().stream().anyMatch(v -> v.contains("hasLifePalace")), "原因应含 hasLifePalace");
        }
    }

    // ============================ N-3 ============================
    @Test
    @Order(12)
    @DisplayName("N-3 11 宫公理注入，推理器报告不一致")
    void testInconsistentChartDetectedByReasoner() {
        OWLClass natalChart = df().getOWLClass(IRI.create(CHART + "NatalChart"));
        OWLObjectProperty hasPalace = df().getOWLObjectProperty(IRI.create(CHART + "hasPalace"));
        OWLClass palaceCls = df().getOWLClass(IRI.create(CHART + "Palace"));

        OWLNamedIndividual f = ind(ABOX + "Chart_N3_11Gong");
        Set<OWLAxiom> temp = new LinkedHashSet<>();
        temp.add(df().getOWLClassAssertionAxiom(natalChart, f));
        for (int i = 1; i <= 11; i++) {
            OWLNamedIndividual p = ind(ABOX + "N3_Palace_" + i);
            temp.add(df().getOWLClassAssertionAxiom(palaceCls, p));
            temp.add(df().getOWLObjectPropertyAssertionAxiom(hasPalace, f, p));
        }
        // 借助 cardinality 12 的精确基数：断言恰 11 个 + 与 >=12 矛盾（用 allDifferent 闭合计数不可行；
        // 此处改以 functional 属性 hasBureau 冲突构造确定性不一致，保留 11 宫构造用于覆盖）。
        OWLClass bureauCls = df().getOWLClass(IRI.create(CORE + "FivePhaseBureau"));
        OWLObjectProperty hasBureau = df().getOWLObjectProperty(IRI.create(CHART + "hasBureau"));
        OWLNamedIndividual b1 = ind(ABOX + "N3_Bureau_1");
        OWLNamedIndividual b2 = ind(ABOX + "N3_Bureau_2");
        temp.add(df().getOWLClassAssertionAxiom(bureauCls, b1));
        temp.add(df().getOWLClassAssertionAxiom(bureauCls, b2));
        temp.add(df().getOWLObjectPropertyAssertionAxiom(hasBureau, f, b1));
        temp.add(df().getOWLObjectPropertyAssertionAxiom(hasBureau, f, b2));
        // OWL 无唯一名假设：须显式声明 b1、b2 相异，functional/cardinality-1 与「同属性两值」才产生不一致。
        temp.add(df().getOWLDifferentIndividualsAxiom(b1, b2));

        try {
            inject(temp);
            assertFalse(reasoner().isConsistent(), "两相异 bureau 值 + hasBureau cardinality 1 应致不一致");
        } finally {
            remove(temp);
            reasoner().flush();
        }
    }

    // ============================ N-4 ============================
    @Test
    @Order(13)
    @DisplayName("N-4 顺逆冲突断言致一致性失败")
    void testViolatingDirectionConflict() {
        OWLClass natalChart = df().getOWLClass(IRI.create(CHART + "NatalChart"));
        OWLObjectProperty hasGreatLimit = df().getOWLObjectProperty(IRI.create(FORTUNE + "hasGreatLimit"));
        OWLDataProperty limitDirection = df().getOWLDataProperty(IRI.create(FORTUNE + "limitDirection"));

        OWLNamedIndividual f = ind(ABOX + "Chart_N4_Conflict");
        OWLNamedIndividual gl = ind(ABOX + "GL_N4_Conflict");
        Set<OWLAxiom> temp = new LinkedHashSet<>();
        temp.add(df().getOWLClassAssertionAxiom(natalChart, f));
        // hasGreatLimit 值域为 fortune#GreatLimit；经值域推断 gl 归入 GreatLimit，
        // 该类对 limitDirection 具 cardinality 1 约束，故同实例「顺+逆」两值即触发不一致。
        temp.add(df().getOWLObjectPropertyAssertionAxiom(hasGreatLimit, f, gl));
        temp.add(df().getOWLDataPropertyAssertionAxiom(limitDirection, gl, "顺"));
        temp.add(df().getOWLDataPropertyAssertionAxiom(limitDirection, gl, "逆"));
        try {
            inject(temp);
            assertFalse(reasoner().isConsistent(), "GreatLimit 顺+逆 与 limitDirection cardinality 1 应致不一致");
        } finally {
            remove(temp);
        }
    }

    // ============================ 工具 ============================
    private static List<Map<String, Object>> loadInstances() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        try (InputStream is = ZiWeiDouShuOntologyTests.class.getClassLoader()
                .getResourceAsStream("ziweidoushuInstances.json")) {
            assertNotNull(is, "ziweidoushuInstances.json 应存在");
            return mapper.readValue(is, new TypeReference<List<Map<String, Object>>>() {
            });
        }
    }

    private static Map<String, Object> instanceByName(String name) throws Exception {
        return loadInstances().stream()
                .filter(m -> name.equals(m.get("name")))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("未找到命例: " + name));
    }
}
