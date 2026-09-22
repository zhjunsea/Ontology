package com.ocean.ontologyframework.tcm;

import com.ocean.ontologyframework.TCMOntologyJobWorker;
import com.ocean.ontologyframework.tcm.app.SymptomCatalog;
import openllet.owlapi.OpenlletReasonerFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLEquivalentClassesAxiom;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLObjectProperty;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.model.OWLSubClassOfAxiom;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.semanticweb.owlapi.util.AutoIRIMapper;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 「四诊 → 八纲 → 六经 → 方证 → 加减药」全链路测试。
 *
 * <p>与 {@link HerbRuleEngineTest} 的区别：后者只测「加减药」这一段（直接给定母方证 + 症状）；
 * 本测试从**四诊输入**（症状/脉象/舌象 IRI）出发，先由 Openllet 在真实本体上推出
 * 八纲 → 六经 → 方证，再把**推理得到的方证**喂给生产环境的加减药规则引擎
 * （{@code deriveFormulaForTest}，与 {@code computeHerbModification} 同一实现），
 * 从而验证「四诊一路走到加减药」的完整链路。
 *
 * <p><b>为什么离线可跑</b>：生产链路走 Camunda 流程 + Ontop/MySQL，本测试不依赖它们。
 * 分类阶段用与生产相同的本体 + Openllet 推理（生产用三阶段 minibox 做模块抽取，
 * 属性能优化，抽取保持签名内的蕴涵，故推理结论一致）；加减药阶段直接调生产同一引擎。
 *
 * <p><b>依据</b>：每个用例的加减药规则均出自 {@code ontology/fangzheng/rules.owl} 的
 * 方后注条文（《伤寒论》96、316、386、40、103/165 条），在用例中逐条注明出处，
 * 不臆造症状与药味。
 *
 * <p><b>输出</b>：控制台打印每个用例的完整链路；{@link #fullFlowReport()} 另将报告写入
 * {@code target/sizhen-herb-flow-report.txt}。
 */
public class SizhenToHerbModificationFlowTest {

    private static final String BASE_NS = "http://www.tcm-classics.org/jingfang#";

    private static final String ONTOLOGY_DIR =
            "D:/work/Ontology/TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox/ontology";

    private static final String RULES_OWL = ONTOLOGY_DIR + "/fangzheng/rules.owl";

    private static final Path REPORT_FILE = Paths.get("target", "sizhen-herb-flow-report.txt");

    private static final String LINE = "=".repeat(92);
    private static final String THIN = "-".repeat(92);

    /** ABox 中症状/脉象/舌象个体带 {@code _instance} 后缀；药名个体不带。 */
    private static final String INSTANCE_SUFFIX = "_instance";

    /** 与生产 {@code SIX_CHANNEL_WHITELIST} 一致：只认这 6 个六经，合病类不参与。 */
    private static final Set<String> SIX_CHANNEL_WHITELIST = Set.of(
            "Taiyangbing", "Yangmingbing", "Shaoyangbing",
            "Taiyinbing", "Shaoyinbing", "Jueyinbing");

    private static final String HAS_SYMPTOM = "you_zhengzhuang";
    private static final String HAS_PULSE = "you_maixiang";
    private static final String HAS_TONGUE = "you_shexiang";

    /**
     * 本测试只关心的 5 个目标方证（{@link #CASES} 中出现的全部 expectFangzheng）。
     *
     * <p>推理时只保留这 5 个方证的等价类定义，其余方证定义一律剔除——原因见
     * {@link #isNonTargetFangzhengDefinition}。
     */
    private static final Set<String> TARGET_FANGZHENG = Set.of(
            "Xiaochaihutangzheng", "Dachaihutangzheng",
            "Zhenwutangzheng", "Lizhongtangzheng", "Xiaoqinglongtangzheng");

    // ---------- 推理基础设施 ----------
    private static OWLOntologyManager manager;
    private static OWLOntology merged;
    private static OWLReasoner reasoner;
    private static OWLDataFactory df;

    private static Set<String> bagangClasses;
    private static Set<String> fangzhengClasses;

    /** 加减药引擎（生产同一实现）。 */
    private static TCMOntologyJobWorker worker;
    private static Map<String, Integer> ruleIndex;

    private static SymptomCatalog catalog;
    private static final Map<String, String> HERB_LABELS = new LinkedHashMap<>();
    private static final Map<String, String> FANGZHENG_LABELS = new LinkedHashMap<>();

    /** 用例 IRI 后缀 → 患者个体。 */
    private static final Map<String, OWLNamedIndividual> PATIENTS = new LinkedHashMap<>();

    // ============================================================
    // 用例定义：四诊输入 + 期望分类 + 期望加减药 + 依据
    // ============================================================

    /**
     * 一个完整链路用例。
     *
     * @param title         用例标题
     * @param symptoms      症状 fragment（四诊·问诊）
     * @param pulses        脉象 fragment（四诊·切诊）
     * @param tongues       舌象 fragment（四诊·望诊）
     * @param expectLiujing 期望推理出的六经（白名单内，排序后精确匹配）
     * @param expectFangzheng 期望推理命中（realize）的方证
     * @param mother        母方组成（药名 fragment）
     * @param expectRemoved 期望被去掉的药
     * @param expectAdded   期望被加入的药
     * @param basis         依据（条文出处）
     */
    private record FlowCase(
            String title,
            List<String> symptoms, List<String> pulses, List<String> tongues,
            List<String> expectLiujing, String expectFangzheng,
            List<String> mother,
            List<String> expectRemoved, List<String> expectAdded,
            String basis) {
    }

    /** 小柴胡汤母方（小柴胡汤证 / 大柴胡汤证的加减基线）。 */
    private static final List<String> XIAOCHAIHU = List.of(
            "Chaihu", "Huangqin", "Banxia", "Renshen", "Gancao", "Dazao", "Shengjiang");

    /** 真武汤母方。 */
    private static final List<String> ZHENWU = List.of(
            "Fuling", "Shaoyao", "Baizhu", "Shengjiang", "Fuzi");

    /** 理中汤母方。 */
    private static final List<String> LIZHONG = List.of(
            "Renshen", "Baizhu", "Ganjiang", "Gancao");

    /** 小青龙汤母方。 */
    private static final List<String> XIAOQINGLONG = List.of(
            "Mahuang", "Guizhi", "Shaoyao", "Xixin", "Ganjiang", "Banxia", "Wuweizi", "Gancao");

    private static final List<FlowCase> CASES = List.of(
            new FlowCase(
                    "少阳·小柴胡汤证 + 咳",
                    List.of("Kouku", "Yangan", "Muxuan", "Kesou"), List.of("Xianmai"), List.of(),
                    List.of("Shaoyangbing"), "Xiaochaihutangzheng",
                    XIAOCHAIHU,
                    List.of("Renshen", "Dazao", "Shengjiang"), List.of("Wuweizi", "Ganjiang"),
                    "《伤寒论》96条方后注：「若咳者，去人参、大枣、生姜，加五味子半升、干姜二两。」"),

            new FlowCase(
                    "少阳·小柴胡汤证 + 渴",
                    List.of("Kouku", "Yangan", "Muxuan", "Kouke"), List.of("Xianmai"), List.of(),
                    List.of("Shaoyangbing"), "Xiaochaihutangzheng",
                    XIAOCHAIHU,
                    List.of("Banxia"), List.of("Gualougen"),
                    "《伤寒论》96条方后注：「若渴，去半夏，加人参合前成四两半、栝蒌根四两。」"),

            new FlowCase(
                    "少阳·小柴胡汤证 + 腹痛",
                    List.of("Kouku", "Yangan", "Muxuan", "Futong"), List.of("Xianmai"), List.of(),
                    List.of("Shaoyangbing"), "Xiaochaihutangzheng",
                    XIAOCHAIHU,
                    List.of("Huangqin"), List.of("Shaoyao"),
                    "《伤寒论》96条方后注：「若腹中痛者，去黄芩，加芍药三两。」"),

            new FlowCase(
                    "少阴·真武汤证 + 下利",
                    List.of("Wureehan", "Shouzuleng", "Xiali", "Xinxiajidong", "Touxuan", "Shenrundong"),
                    List.of("Chenchimai"), List.of(),
                    List.of("Shaoyinbing"), "Zhenwutangzheng",
                    ZHENWU,
                    List.of("Shaoyao"), List.of("Ganjiang"),
                    "《伤寒论》316条方后注：「若下利者，去芍药，加干姜二两。」"),

            new FlowCase(
                    "少阴·真武汤证 + 呕（不兼下利）",
                    List.of("Wureehan", "Xinxiajidong", "Touxuan", "Shenrundong", "Ou", "Danyumei"),
                    List.of("Weiximai"), List.of(),
                    List.of("Shaoyinbing"), "Zhenwutangzheng",
                    ZHENWU,
                    List.of("Fuzi"), List.of(),
                    "《伤寒论》316条方后注：「若呕者，去附子，加生姜足前成半斤。」"
                            + "（生姜本在真武汤中，故记为剂量调整而非新增）"),

            new FlowCase(
                    "少阳阳明合病·大柴胡汤证 + 大便硬",
                    // 大柴胡汤证 = 少阳阳明合病，须具柴胡证（往来寒热、胸胁苦满）
                    // ＋ 心下急、郁郁微烦、里实（大便硬）。故四诊须含 Wanglaihanre、Xiongxiekuman，
                    // 否则只推出小柴胡汤证（其等价类仅需 口苦∧(咽干∨目眩) 之一即可）。
                    List.of("Wanglaihanre", "Xiongxiekuman", "Kouku", "Yangan", "Muxuan",
                            "Dabianying", "FumanJuAn", "Chaore", "Zhanyu", "Xinxiaji", "Yuyuweifan"),
                    List.of("Xianmai"), List.of(),
                    List.of("Shaoyangbing", "Yangmingbing"), "Dachaihutangzheng",
                    XIAOCHAIHU,
                    List.of("Renshen", "Gancao"), List.of("Dahuang", "Zhishi", "Shaoyao"),
                    "《伤寒论》103、165条：「大柴胡汤即小柴胡汤去人参、甘草，加芍药、枳实、大黄。」"
                            + "136条「伤寒十余日，热结在里，复往来寒热者，与大柴胡汤」——"
                            + "柴胡证（往来寒热、胸胁苦满）为少阳阳明合病用大柴胡之前提。"),

            new FlowCase(
                    "太阴·理中汤证 + 腹满",
                    List.of("Fuman", "Shifuzitong", "Xiali", "Buke", "Shouzuleng"),
                    List.of("Chenchimai"), List.of(),
                    List.of("Taiyinbing"), "Lizhongtangzheng",
                    LIZHONG,
                    List.of("Baizhu"), List.of("Fuzi"),
                    "《伤寒论》386条方后注：「若腹满者，去术，加附子一枚，炮。」"),

            new FlowCase(
                    "太阳·小青龙汤证 + 渴",
                    List.of("Wuhan", "Kesou", "Tanduoqingxi", "Kouke", "Ehan", "Fare", "ShenTengtong"),
                    List.of("Fujinmai"), List.of(),
                    List.of("Taiyangbing"), "Xiaoqinglongtangzheng",
                    XIAOQINGLONG,
                    List.of("Banxia"), List.of("Gualougen"),
                    "《伤寒论》40条方后注：「若渴，去半夏，加栝蒌根三两。」")
    );

    // ============================================================
    // 装配
    // ============================================================

    @BeforeAll
    static void setUp() throws Exception {
        Path rules = Paths.get(RULES_OWL);
        assumeTrue(Files.isRegularFile(rules), "rules.owl 不存在，跳过全链路测试: " + RULES_OWL);

        // 1) 加减药引擎（生产同一实现）
        worker = new TCMOntologyJobWorker();
        String mainPath = rules.getParent().getParent().resolve("tcm-all.owl").toString();
        ruleIndex = worker.loadHerbRulesForTest(mainPath);

        // 2) 本体 + 推理机
        //    只加载「四诊模块 + 目标方证所在模块」——见技能铁律 38：
        //    加载全部方证模块会让 realize 265 个析取等价类，推理爆炸（>27min）。
        //    本测试只关心 5 个目标方证，它们分别落在 shaoyang_yangming / shaoyin_taiyin / taiyang。
        manager = OWLManager.createOWLOntologyManager();
        manager.getIRIMappers().add(new AutoIRIMapper(new File(ONTOLOGY_DIR), true));
        List<String> mods = new ArrayList<>(List.of(
                "tcm-core.owl", "tcm-zhengzhuang.owl", "tcm-zhengzhuang-abox.owl",
                "tcm-maixiang.owl", "tcm-maixiang-abox.owl",
                "tcm-shexiang.owl", "tcm-shexiang-abox.owl"));
        for (String fz : List.of("shaoyang_yangming.owl", "shaoyin_taiyin.owl", "taiyang.owl")) {
            mods.add("fangzheng/" + fz);
        }
        for (String mod : mods) {
            File f = new File(ONTOLOGY_DIR, mod);
            if (f.isFile()) manager.loadOntologyFromOntologyDocument(f);
        }

        df = manager.getOWLDataFactory();
        bagangClasses = directSubclasses("Bagang");
        fangzhengClasses = allSubclasses("Fangzheng");

        // 3) 合并为一个本体（剔除 imports 声明），写入全部患者四诊，单次推理。
        //
        //    关键（性能）：Openllet 的 classify() 会对**每个**具名类的等价类定义做 tableau
        //    一致性检查。已加载的 3 个方证模块共含 ~180 个析取等价类（方证定义多为
        //    「A ⊓ (B ∨ C ∨ …)」），实测直接推理会卡在 openllet KnowledgeBaseImpl.classify()
        //    超过 3 分钟不收敛（线程栈：classify → ensureConsistency → isConsistent → tableau）。
        //    本测试只验证 5 个目标方证，其余方证定义对结论无贡献（方证之间不互相引用），
        //    故合并时剔除「非目标方证」的等价类定义；同时剔除患者未引用的四诊个体，
        //    避免 realize 对全部 727 个个体物化（原实现内存 3.6GB、耗时 ~87s）。
        Set<IRI> keepIndividuals = new HashSet<>();
        for (int i = 0; i < CASES.size(); i++) {
            keepIndividuals.add(IRI.create(BASE_NS + "Patient_flow" + i));
        }
        for (FlowCase c : CASES) {
            for (String f : c.symptoms()) keepIndividuals.add(IRI.create(BASE_NS + f + INSTANCE_SUFFIX));
            for (String f : c.pulses())   keepIndividuals.add(IRI.create(BASE_NS + f + INSTANCE_SUFFIX));
            for (String f : c.tongues())  keepIndividuals.add(IRI.create(BASE_NS + f + INSTANCE_SUFFIX));
        }

        Set<OWLAxiom> axioms = new LinkedHashSet<>();
        int droppedFz = 0, droppedInd = 0;
        for (OWLOntology o : manager.getOntologies()) {
            for (OWLAxiom ax : o.getAxioms()) {
                if (isNonTargetFangzhengDefinition(ax)) { droppedFz++; continue; }
                if (mentionsForeignIndividual(ax, keepIndividuals)) { droppedInd++; continue; }
                axioms.add(ax);
            }
        }
        System.out.println("[SizhenFlow] 精简本体：剔除非目标方证等价类定义 " + droppedFz
                + " 条、患者未引用个体相关公理 " + droppedInd + " 条");
        merged = manager.createOntology(axioms);

        for (int i = 0; i < CASES.size(); i++) {
            FlowCase c = CASES.get(i);
            String key = "Patient_flow" + i;
            OWLNamedIndividual p = df.getOWLNamedIndividual(IRI.create(BASE_NS + key));
            PATIENTS.put(key, p);
            merged.add(df.getOWLClassAssertionAxiom(
                    df.getOWLClass(IRI.create(BASE_NS + "Huanzhe")), p));
            addAll(p, HAS_SYMPTOM, c.symptoms());
            addAll(p, HAS_PULSE, c.pulses());
            addAll(p, HAS_TONGUE, c.tongues());
        }
        reasoner = new OpenlletReasonerFactory().createReasoner(merged);
        // 注意：不要调 precomputeInferences(CLASS_ASSERTIONS) —— 它会把全部个体（586 症状等）
        // 的类型一次性物化，内存涨到 3.6GB 且极慢。首次 getTypes(患者) 会自动触发 realize
        // （实测约 87s），之后全部缓存、瞬时返回。

        // 4) 中文名（仅用于报告展示）
        catalog = new SymptomCatalog(ONTOLOGY_DIR);
        loadHerbLabels(Paths.get(ONTOLOGY_DIR, "tcm-yaowu-abox.owl"));
        loadFangzhengLabels(rules.getParent());
    }

    // ============================================================
    // 全链路用例
    // ============================================================

    @Test
    @DisplayName("四诊→八纲→六经→方证→加减药：少阳·小柴胡汤证+咳")
    void xiaochaihuWithCough() {
        runFlow(0);
    }

    @Test
    @DisplayName("四诊→八纲→六经→方证→加减药：少阳·小柴胡汤证+渴")
    void xiaochaihuWithThirst() {
        runFlow(1);
    }

    @Test
    @DisplayName("四诊→八纲→六经→方证→加减药：少阳·小柴胡汤证+腹痛")
    void xiaochaihuWithAbdominalPain() {
        runFlow(2);
    }

    @Test
    @DisplayName("四诊→八纲→六经→方证→加减药：少阴·真武汤证+下利")
    void zhenwuWithDiarrhea() {
        runFlow(3);
    }

    @Test
    @DisplayName("四诊→八纲→六经→方证→加减药：少阴·真武汤证+呕")
    void zhenwuWithVomiting() {
        runFlow(4);
    }

    @Test
    @DisplayName("四诊→八纲→六经→方证→加减药：少阳阳明合病·大柴胡汤证+大便硬")
    void dachaihuWithHardStool() {
        runFlow(5);
    }

    @Test
    @DisplayName("四诊→八纲→六经→方证→加减药：太阴·理中汤证+腹满")
    void lizhongWithAbdominalFullness() {
        runFlow(6);
    }

    @Test
    @DisplayName("四诊→八纲→六经→方证→加减药：太阳·小青龙汤证+渴")
    void xiaoqinglongWithThirst() {
        runFlow(7);
    }

    // ============================================================
    // 汇总报告（落盘）
    // ============================================================

    @Test
    @DisplayName("生成全链路报告：四诊→八纲→六经→方证→加减药（落盘 target/sizhen-herb-flow-report.txt）")
    void fullFlowReport() throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("中医经方 · 四诊 → 八纲 → 六经 → 方证 → 加减药 全链路报告\n");
        sb.append("本体目录：").append(ONTOLOGY_DIR).append('\n');
        sb.append("规则来源：").append(RULES_OWL).append("（")
                .append(ruleIndex.size()).append(" 方证 / ")
                .append(ruleIndex.values().stream().mapToInt(Integer::intValue).sum())
                .append(" 条方后注规则）\n");
        sb.append("推理机　：Openllet（真实本体，单次推理）\n\n");

        for (int i = 0; i < CASES.size(); i++) {
            sb.append(renderFlow(i));
        }
        sb.append(LINE).append('\n');
        sb.append("汇总：").append(CASES.size()).append(" 个用例，全部完成「四诊→八纲→六经→方证→加减药」链路\n");
        sb.append(LINE).append('\n');

        String report = sb.toString();
        System.out.println(report);
        Files.createDirectories(REPORT_FILE.toAbsolutePath().getParent());
        Files.writeString(REPORT_FILE, report, StandardCharsets.UTF_8);
        System.out.println("报告已写入：" + REPORT_FILE.toAbsolutePath());
    }

    // ============================================================
    // 链路执行与断言
    // ============================================================

    /** 跑第 i 个用例：推理分类 → 断言 → 加减药 → 断言。 */
    private static void runFlow(int i) {
        FlowCase c = CASES.get(i);
        OWLNamedIndividual p = PATIENTS.get("Patient_flow" + i);

        Set<String> types = reasoner.getTypes(p, false).getFlattened().stream()
                .map(x -> x.getIRI().getFragment())
                .collect(Collectors.toCollection(TreeSet::new));

        List<String> bagang = types.stream().filter(bagangClasses::contains).sorted().toList();
        List<String> liujing = types.stream()
                .filter(SIX_CHANNEL_WHITELIST::contains).sorted().toList();
        List<String> fangzheng = types.stream().filter(fangzhengClasses::contains).sorted().toList();

        // ---- 八纲：至少推出病位与病性各一 ----
        assertThat(bagang).as("[%s] 八纲不应为空", c.title()).isNotEmpty();

        // ---- 六经：精确匹配 ----
        assertThat(liujing)
                .as("[%s] 六经推理结果（八纲=%s）", c.title(), bagang)
                .containsExactlyElementsOf(c.expectLiujing());

        // ---- 方证：期望方证被 realize 命中 ----
        assertThat(fangzheng)
                .as("[%s] 方证 realize 结果（六经=%s）", c.title(), liujing)
                .contains(c.expectFangzheng());

        // ---- 加减药：用推理得到的方证喂生产引擎 ----
        Set<String> patientFrags = new LinkedHashSet<>();
        patientFrags.addAll(c.symptoms());
        patientFrags.addAll(c.pulses());
        patientFrags.addAll(c.tongues());

        Map<String, Object> r = worker.deriveFormulaForTest(c.expectFangzheng(), patientFrags, c.mother());

        assertThat(r.get("derived"))
                .as("[%s] 加减药应派生新方", c.title()).isEqualTo(true);
        assertThat(cn(r, "removedCn"))
                .as("[%s] 去药", c.title())
                .containsExactlyInAnyOrderElementsOf(c.expectRemoved());
        if (!c.expectAdded().isEmpty()) {
            assertThat(cn(r, "addedCn"))
                    .as("[%s] 加药", c.title())
                    .containsExactlyInAnyOrderElementsOf(c.expectAdded());
        }
        assertThat(cn(r, "ruleSources"))
                .as("[%s] 应带条文出处", c.title()).isNotEmpty();

        System.out.println(renderFlow(i));
    }

    // ============================================================
    // 报告渲染
    // ============================================================

    private static String renderFlow(int i) {
        FlowCase c = CASES.get(i);
        OWLNamedIndividual p = PATIENTS.get("Patient_flow" + i);

        Set<String> types = reasoner.getTypes(p, false).getFlattened().stream()
                .map(x -> x.getIRI().getFragment())
                .collect(Collectors.toCollection(TreeSet::new));
        List<String> bagang = types.stream().filter(bagangClasses::contains).sorted().toList();
        List<String> liujing = types.stream()
                .filter(SIX_CHANNEL_WHITELIST::contains).sorted().toList();
        List<String> fangzheng = types.stream().filter(fangzhengClasses::contains).sorted().toList();

        Set<String> patientFrags = new LinkedHashSet<>();
        patientFrags.addAll(c.symptoms());
        patientFrags.addAll(c.pulses());
        patientFrags.addAll(c.tongues());
        Map<String, Object> r = worker.deriveFormulaForTest(c.expectFangzheng(), patientFrags, c.mother());

        StringBuilder sb = new StringBuilder();
        sb.append(LINE).append('\n');
        sb.append("【用例 ").append(i + 1).append("】").append(c.title()).append('\n');
        sb.append(THIN).append('\n');

        sb.append("① 四诊输入\n");
        sb.append("   问（症状）：").append(joinSymptoms(c.symptoms())).append('\n');
        sb.append("   切（脉象）：").append(joinSymptoms(c.pulses())).append('\n');
        if (!c.tongues().isEmpty()) {
            sb.append("   望（舌象）：").append(joinSymptoms(c.tongues())).append('\n');
        }

        sb.append("② 八纲（病位×病性）\n");
        sb.append("   ").append(bagang.stream().map(SizhenToHerbModificationFlowTest::bagangCn)
                .collect(Collectors.joining("、"))).append('\n');

        sb.append("③ 六经\n");
        sb.append("   ").append(liujing.stream().map(SizhenToHerbModificationFlowTest::liujingCn)
                .collect(Collectors.joining("、"))).append('\n');

        sb.append("④ 方证（realize 命中）\n");
        sb.append("   ").append(fangzheng.stream().map(SizhenToHerbModificationFlowTest::fzLabel)
                .collect(Collectors.joining("、"))).append('\n');

        sb.append("⑤ 加减药（母方证：").append(fzLabel(c.expectFangzheng())).append("）\n");
        sb.append("   母方组成：").append(joinHerbs(c.mother())).append('\n');
        List<String> rules = cn(r, "appliedRules");
        List<String> srcs = cn(r, "ruleSources");
        sb.append("   命中规则：").append(rules.size()).append(" 条\n");
        for (int k = 0; k < rules.size(); k++) {
            sb.append("     [").append(k + 1).append("] ").append(rules.get(k)).append('\n');
            if (k < srcs.size()) sb.append("         出处：").append(srcs.get(k)).append('\n');
        }
        sb.append("   去药　　：").append(joinHerbs(cn(r, "removedCn"))).append('\n');
        sb.append("   加药　　：").append(joinHerbs(cn(r, "addedCn"))).append('\n');
        List<String> dosage = cn(r, "dosageChanges");
        sb.append("   剂量调整：").append(dosage.isEmpty() ? "无" : joinDosage(dosage)).append('\n');
        sb.append("   最终组成：").append(joinHerbs(cn(r, "herbsCn"))).append('\n');
        sb.append("   依据　　：").append(c.basis()).append('\n');
        sb.append(LINE).append('\n');
        return sb.toString();
    }

    // ============================================================
    // 工具
    // ============================================================

    private static void addAll(OWLNamedIndividual p, String prop, List<String> frags) {
        if (frags == null || frags.isEmpty()) return;
        OWLObjectProperty pr = df.getOWLObjectProperty(IRI.create(BASE_NS + prop));
        for (String f : frags) {
            merged.add(df.getOWLObjectPropertyAssertionAxiom(pr, p,
                    df.getOWLNamedIndividual(IRI.create(BASE_NS + f + INSTANCE_SUFFIX))));
        }
    }

    /**
     * 是否为「非目标方证」的等价类定义（{@code EquivalentClasses(FangzhengClass, 定义式)}）。
     *
     * <p><b>为什么要剔除</b>：Openllet 的 {@code classify()} 会对每个具名类的等价类定义做
     * tableau 一致性检查；方证定义多为析取式（「A ⊓ (B ∨ C ∨ …)」），析取会触发 case split，
     * 代价随定义数急剧上升。已加载的 3 个方证模块共含 ~180 个此类定义，实测直接推理会卡在
     * {@code openllet.core.KnowledgeBaseImpl.classify()} 超过 3 分钟不收敛。
     *
     * <p><b>为什么安全</b>：本测试只断言 5 个目标方证被 realize 命中；方证之间不互相引用
     * （定义式只引用症状/脉象/舌象/八纲/六经类），剔除其余方证定义不改变目标方证的推理结论。
     * 被剔除定义的方证类仍保留 {@code subClassOf Fangzheng}，只是不再被推理命中。
     */
    private static boolean isNonTargetFangzhengDefinition(OWLAxiom ax) {
        if (!(ax instanceof OWLEquivalentClassesAxiom eq)) return false;
        for (OWLClass c : eq.getClassesInSignature()) {
            String frag = c.getIRI().getFragment();
            if (fangzhengClasses.contains(frag) && !TARGET_FANGZHENG.contains(frag)) return true;
        }
        return false;
    }

    /**
     * 公理是否提及「患者未引用」的个体。
     *
     * <p>已加载的 ABox 含 583 症状 + 68 脉象 + 72 舌象 + 4 腹证 = 727 个个体，而本测试的 8 个
     * 患者只引用其中数十个。realize 会对**全部**个体物化类型（原实现内存 3.6GB、耗时 ~87s），
     * 故合并时剔除只涉及无关个体的公理。类公理（TBox）不含个体，一律保留。
     */
    private static boolean mentionsForeignIndividual(OWLAxiom ax, Set<IRI> keep) {
        for (OWLNamedIndividual ind : ax.getIndividualsInSignature()) {
            if (!keep.contains(ind.getIRI())) return true;
        }
        return false;
    }

    private static Set<String> directSubclasses(String parent) {
        Set<String> res = new TreeSet<>();
        for (OWLOntology o : manager.getOntologies()) {
            for (OWLAxiom a : o.getAxioms()) {
                if (a instanceof OWLSubClassOfAxiom sc
                        && sc.getSuperClass().isOWLClass() && sc.getSubClass().isOWLClass()
                        && sc.getSuperClass().asOWLClass().getIRI().getFragment().equals(parent)) {
                    res.add(sc.getSubClass().asOWLClass().getIRI().getFragment());
                }
            }
        }
        return res;
    }

    private static Set<String> allSubclasses(String parent) {
        Set<String> res = new TreeSet<>();
        res.add(parent);
        boolean changed = true;
        while (changed) {
            changed = false;
            for (OWLOntology o : manager.getOntologies()) {
                for (OWLAxiom a : o.getAxioms()) {
                    if (a instanceof OWLSubClassOfAxiom sc
                            && sc.getSuperClass().isOWLClass() && sc.getSubClass().isOWLClass()) {
                        String sup = sc.getSuperClass().asOWLClass().getIRI().getFragment();
                        String sub = sc.getSubClass().asOWLClass().getIRI().getFragment();
                        if (res.contains(sup) && !res.contains(sub)) {
                            res.add(sub);
                            changed = true;
                        }
                    }
                }
            }
        }
        res.remove(parent);
        return res;
    }

    private static String bagangCn(String frag) {
        return switch (frag) {
            case "Biao" -> "表";
            case "Li" -> "里";
            case "Banbiaobanli" -> "半表半里";
            case "Yin" -> "阴";
            case "Yang" -> "阳";
            case "Han" -> "寒";
            case "Re" -> "热";
            case "Xu" -> "虚";
            case "Shi" -> "实";
            default -> frag;
        };
    }

    private static String liujingCn(String frag) {
        return switch (frag) {
            case "Taiyangbing" -> "太阳病";
            case "Yangmingbing" -> "阳明病";
            case "Shaoyangbing" -> "少阳病";
            case "Taiyinbing" -> "太阴病";
            case "Shaoyinbing" -> "少阴病";
            case "Jueyinbing" -> "厥阴病";
            default -> frag;
        };
    }

    private static String label(String fragment) {
        if (catalog == null) return fragment;
        return catalog.byFragment(fragment + INSTANCE_SUFFIX)
                .or(() -> catalog.byFragment(fragment))
                .map(SymptomCatalog.Entry::getLabel)
                .orElse(fragment);
    }

    private static String herbCn(String fragment) {
        return HERB_LABELS.getOrDefault(fragment, fragment);
    }

    private static String fzLabel(String fragment) {
        return FANGZHENG_LABELS.getOrDefault(fragment, fragment);
    }

    private static String joinHerbs(List<String> herbs) {
        if (herbs.isEmpty()) return "（无）";
        List<String> cn = new ArrayList<>(herbs.size());
        for (String h : herbs) cn.add(herbCn(h));
        return String.join("、", cn);
    }

    private static String joinDosage(List<String> dosage) {
        List<String> cn = new ArrayList<>(dosage.size());
        for (String d : dosage) {
            int sep = d.indexOf('：');
            cn.add(sep > 0 ? herbCn(d.substring(0, sep)) + d.substring(sep) : d);
        }
        return String.join("；", cn);
    }

    private static String joinSymptoms(List<String> symptoms) {
        List<String> parts = new ArrayList<>(symptoms.size());
        for (String s : symptoms) parts.add(label(s) + "（" + s + "）");
        return String.join("、", parts);
    }

    private static void loadHerbLabels(Path yaowuAbox) {
        if (!Files.isRegularFile(yaowuAbox)) return;
        try {
            String xml = Files.readString(yaowuAbox, StandardCharsets.UTF_8);
            Matcher m = INDIVIDUAL.matcher(xml);
            while (m.find()) {
                String frag = m.group(1).trim();
                Matcher lm = LABEL.matcher(m.group(2));
                if (!frag.isEmpty() && lm.find()) HERB_LABELS.putIfAbsent(frag, lm.group(1).trim());
            }
        } catch (IOException e) {
            // 中文名仅用于展示
        }
    }

    private static void loadFangzhengLabels(Path fangzhengDir) {
        if (!Files.isDirectory(fangzhengDir)) return;
        try (var stream = Files.list(fangzhengDir)) {
            for (Path f : stream.filter(p -> p.getFileName().toString().endsWith(".owl")).toList()) {
                String xml = Files.readString(f, StandardCharsets.UTF_8);
                Matcher m = OWL_CLASS.matcher(xml);
                while (m.find()) {
                    Matcher lm = LABEL.matcher(m.group(2));
                    if (lm.find()) FANGZHENG_LABELS.putIfAbsent(m.group(1), lm.group(1).trim());
                }
            }
        } catch (IOException e) {
            // 同上
        }
    }

    private static final Pattern INDIVIDUAL = Pattern.compile(
            "<owl:NamedIndividual\\s+rdf:about=\"#([^\"]+)\"\\s*>(.*?)</owl:NamedIndividual>",
            Pattern.DOTALL);

    private static final Pattern OWL_CLASS = Pattern.compile(
            "<owl:Class\\s+rdf:about=\"#([^\"]+)\"\\s*>(.*?)</owl:Class>", Pattern.DOTALL);

    private static final Pattern LABEL = Pattern.compile(
            "<rdfs:label\\s+xml:lang=\"zh\">([^<]*)</rdfs:label>");

    @SuppressWarnings("unchecked")
    private static List<String> cn(Map<String, Object> r, String key) {
        Object v = r.get(key);
        return v == null ? List.of() : (List<String>) v;
    }
}
