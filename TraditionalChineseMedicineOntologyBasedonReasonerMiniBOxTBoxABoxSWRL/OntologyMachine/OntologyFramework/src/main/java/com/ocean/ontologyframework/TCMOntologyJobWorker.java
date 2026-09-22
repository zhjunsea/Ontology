package com.ocean.ontologyframework;

import com.ocean.ontopobdahandler.ObdaQueryUtils;
import com.ocean.openlletresolver.*;
import com.ocean.ontologyframework.tcm.app.SymptomMappingService;

import io.camunda.client.annotation.JobWorker;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.JobClient;

import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
@Profile("TCMBPMN")
public class TCMOntologyJobWorker {

    private static final Logger log = LoggerFactory.getLogger(TCMOntologyJobWorker.class);

    @Value("${ontology.main-path}")
    private String mainOntologyPath;

    @Value("${ontology.obda-path}")
    private String obdaPath;

    @Value("${ontology.obda-properties-path}")
    private String obdaPropertiesPath;

    /**
     * ABox 查表本体路径（症状 / 舌象 / 脉象个体字典）。
     *
     * <p>ABox 已从 TBox 入口 {@code tcm-all.owl} 移出：{@code gettBoxOntology()} 会合并
     * 「所有已加载本体」的全部公理，ABox 混入会让 Openllet 分类对每个类做 ABox 一致性检查
     * （jstack: {@code CDOptimizedTaxonomyBuilder.checkSatisfiability → ABoxImpl.isConsistent}），
     * 988 个体使 {@code CLASS_HIERARCHY} 达 345s。移出后实测 345s → 47s。
     */
    @Value("${ontology.abox-path:}")
    private String aboxPath;

    private BackendService backendService;
    private QueryService queryService;
    private OWLDataFactory tboxDf;

    /**
     * 单独加载的 ABox 查表本体（独立 manager，绝不并入 {@code gettBoxOntology()}）。
     *
     * <p>诊断链路只把它当「字典」用：{@code collectIndividualTypes} 查
     * {@code Fare_instance : Fare}，{@code addObjectAssertionsAndCopyTypes} 把该类型拷进患者 ABox。
     * 二者都是按 IRI 取公理，不需要推理器，因此放在独立本体里完全等价。
     */
    private OWLOntology aboxLookupOntology;

    /** 症状实例个体映射服务（三层策略：确定性 / LLM / 置信度门控） */
    @Autowired(required = false)
    private SymptomMappingService symptomMappingService;

    // ==================== 分类元数据 ====================
    private Set<OWLClass> bagangSubclasses;
    private Set<OWLClass> liujingSubclasses;
    private Set<OWLClass> fangzhengSubclasses;
    private Set<OWLClass> jianJiaSubclasses;

    /**
     * 八纲判据类（{@code BagangPanju} 全部命名子类）。
     *
     * <p>{@code collectClassClosure} 只沿 {@code ≡}/{@code ⊑} 向上走，不会反向拉入
     * 「引用了该症状的判据类」。故须把判据层显式加入推理模块 seed，
     * 否则患者永远不被分类到判据 → 八纲/六经读不到（步 6–7 修复）。
     */
    private Set<OWLClass> bagangPanjuSubclasses;

    /** 症状 → 包含该症状限制的方证集合 */
    private Map<String, Set<OWLClass>> symptomToFangzhengIndex;

    /** 方证 → 必需 fragment 总数（结构化 gap 的「空患者」值：AND→Σ、OR→min） */
    private Map<OWLClass, Integer> fangzhengRequiredCount = new HashMap<>();

    /** 方证 → 或然症类集合（possibleSymptom 注解，仅自身声明） */
    private Map<OWLClass, Set<OWLClass>> fangzhengPossibleMap = new HashMap<>();

    /** 或然症 fragment → 声明该或然症的方证集合 */
    private Map<String, Set<OWLClass>> possibleToFangzhengIndex = new HashMap<>();

    /** 复合脉 → 组成原子脉 */
    private Map<OWLClass, Set<OWLClass>> compositePulseMap = new HashMap<>();

    /** 复合症状 → 组成原子症状 */
    private Map<OWLClass, Set<OWLClass>> compositeSymptomMap = new HashMap<>();

    /** 十八反 */
    private Map<String, Set<String>> shibafanMap = new HashMap<>();

    /** 十九畏 */
    private Map<String, Set<String>> shijiuweiMap = new HashMap<>();

    /** 药物 fragment → 中文 label */
    private Map<String, String> yaowuLabelMap = new HashMap<>();

    /** 方证 → 六经集合 */
    private Map<OWLClass, Set<OWLClass>> fangzhengLiujingMap = new HashMap<>();

    /** 方证 → 八纲属性 fragment 集合（来自 bagangAttr 注解：Biao/Li/Banbiaobanli/Yang/Yin/…） */
    private Map<OWLClass, Set<String>> fangzhengBagangMap = new HashMap<>();

    /** 八纲判据 fragment → 其 ⊑ 的病位 fragment 集合（Biao/Li/Banbiaobanli） */
    private Map<String, Set<String>> panjuBingweiMap = new HashMap<>();

    /** 八纲判据 fragment → 其 ⊑ 的病性 fragment 集合（Yang/Yin） */
    private Map<String, Set<String>> panjuBingxingMap = new HashMap<>();

    // ==================== 方后注加减规则引擎（v2.7 rules.owl） ====================
    /** 方证 fragment → 该方证的方后注加减规则（按 rules.owl 文档顺序，与 ruleSource 一一配对） */
    private final Map<String, List<HerbRule>> fangzhengRuleIndex = new HashMap<>();

    // ============================================================
    // 【问题1 修复】候选数量扩大
    // ============================================================
    private static final int FALLBACK_TOP_N = 25;
    private static final int CANDIDATE_DISPLAY_TOP_N = 10;

    /**
     * 追问阈值：只对「缺口 ≤ 2」的判据/方证生成追问。
     *
     * <p>缺口越小越省力（患者只需再答 1–2 个问题）。这是<b>结构量</b>（还差几个症状），
     * 不是主观权重；超过阈值的问题不生成，避免让患者面对一长串无望的追问。
     */
    private static final int GAP_MAX = 2;

    private static final Set<String> SIX_CHANNEL_WHITELIST = Set.of(
            "Taiyangbing", "Yangmingbing", "Shaoyangbing",
            "Taiyinbing", "Shaoyinbing", "Jueyinbing"
    );

    private static final Set<String> TOP_LEVEL_CLASSES = Set.of(
            "Huanzhe", "SizhenXinxi", "Zhengzhuang", "Maixiang",
            "Shexiang", "Fuzheng", "Tizhi", "Bagang", "Liujingbing",
            "Fangzheng", "Fangji", "Yaowu", "Yaozheng", "JianJiaZheng"
    );

    // ============================================================
    // 【问题1 修复】方证父子层级表
    // 依据：中医临床"方随证转、随证加减"，子方要求症状更具体，
    // 排序时当母方与子方得分接近，应优先子方。
    // key   = 母方 fragment
    // value = 子方 fragment 集合（直接子方）
    // ============================================================
    private static final Map<String, Set<String>> FANGZHENG_PARENT_TO_CHILDREN = Map.ofEntries(
            // 白虎汤类
            Map.entry("Baihutangzheng", Set.of(
                    "Baihujiarenshentangzheng",
                    "Baihujiaguizhitangzheng")),
            // 柴胡汤类
            Map.entry("Xiaochaihutangzheng", Set.of(
                    "Dachaihutangzheng",
                    "Chaihujiamangxiaotangzheng",
                    "Chaihujialonggumulitangzheng",
                    "Chaihuguizhiganjiangtangzheng",
                    "Chaihuqubanxiajiagualoutangzheng",
                    "Chaihuguizhitangzheng",
                    "Chaihubaihutangzheng")),
            // 理中汤类
            Map.entry("Lizhongtangzheng", Set.of(
                    "Shengjiangxiexintangzheng",
                    "Zhishishaoyaosanzheng",
                    "Dajianzhongtangzheng")),
            // 栀子豉汤类
            Map.entry("Zhizichitangzheng", Set.of(
                    "Zhizigancaochitangzheng",
                    "Zhizishengjiangchitangzheng",
                    "Zhizihoupotangzheng",
                    "Zhiziganjiangtangzheng",
                    "Zhishizhizichitangzheng")),

            // 半夏泻心汤类
            Map.entry("Banxiaxiexintangzheng", Set.of(
                    "Shengjiangxiexintangzheng",
                    "Gancaoxiexintangzheng",
                    "Gancaoxiexintangzheng_huhuo"))
    );

    // ============================================================
    // 【问题1 修复】广谱高分方证惩罚集
    // 五苓散证等价类只有 4 个约束（Taiyangbing ∩ Xiaobianbuli ∩ Kouke ∩ Fumai），
    // 含 Fumai+Kouke 的任意输入都能得高分，容易在非相关方证上误命中。
    // ============================================================
    private static final Set<String> BROAD_MATCH_PENALTY = Set.of(
            "Wulingsanzheng"
    );

    // ==================== 缓存 ====================
    private final MiniReasoningContextManager miniCtxMgr = new MiniReasoningContextManager();
    private final Map<String, PatientInput> patientInputs = new ConcurrentHashMap<>();
    private final Map<String, Set<OWLClass>> fangzhengCandidatesCache = new ConcurrentHashMap<>();

    // ==================== 常量 ====================
    private static final String BASE_NS = "http://www.tcm-classics.org/jingfang#";
    private static final String HAS_SYMPTOM = BASE_NS + "you_zhengzhuang";
    private static final String HAS_PULSE = BASE_NS + "you_maixiang";
    private static final String HAS_TONGUE = BASE_NS + "you_shexiang";
    private static final String HAS_ABDOMINAL = BASE_NS + "you_fuzheng";
    /**
     * 或然症注解属性（{@code tcm-core.owl} 声明为 {@code owl:AnnotationProperty}，label「或然症」）。
     *
     * <p>或然症是脉象/舌象/腹证进入方证匹配的<b>唯一通道</b>——方证定义层 {@code equivalentClass}
     * 只用 {@code you_zhengzhuang}。它<b>不进</b>定义层（避免外延膨胀），只在排序层作为第二键参与。
     */
    private static final String HAS_POSSIBLE_SYMPTOM = BASE_NS + "possibleSymptom";

    /** 参与方证匹配的属性 IRI（症状/脉象/舌象/腹证）——索引与 gap 判定共用。 */
    private static final Set<IRI> FANGZHENG_PROP_IRIS = Set.of(
            IRI.create(HAS_SYMPTOM),
            IRI.create(HAS_PULSE),
            IRI.create(HAS_TONGUE),
            IRI.create(HAS_ABDOMINAL));
    private static final String HAS_PRESCRIPTION = BASE_NS + "you_chufang";
    /**
     * 方证 → 主治方剂 的注解属性（现行写法）。
     *
     * <p>原以名义量 {@code 方证 ⊑ ∃you_chufang.{方剂}} 表达，会把 262 个方剂个体拖入 TBox；
     * Openllet 对 SROIQ 名义量做分类时与 366 条等价定义相互作用，
     * CLASS_HIERARCHY 由 15s 膨胀到 385s（实测）。改为注解后：
     * <ul>
     *   <li>处方映射内容不变（方证相应：有是证用是方）；</li>
     *   <li>方剂个体不再进入 TBox，分类回到 15s 量级；</li>
     *   <li>顺带修正 duli 5 例「处方写进等价定义」导致该方证永不可被推出的缺陷。</li>
     * </ul>
     */
    private static final String HAS_PRESCRIPTION_ANNO = BASE_NS + "chufang";

    private static final String STAGE_BG = "#STAGE_BG";
    private static final String STAGE_LJ = "#STAGE_LJ";
    private static final String STAGE_FZ = "#STAGE_FZ";
    private static final String INSTANCE_SUFFIX = "_instance";

    /**
     * 六经互斥对 —— <b>已废止（2026-09-20）</b>，保留此说明以免后人重蹈。
     *
     * <p>原实现：少阳与厥阴同属半表半里，一阳一阴，若同时推出则「阴覆盖阳」，
     * 移除少阳、保留厥阴。
     *
     * <p><b>废止理由（医理）</b>：
     * <ol>
     *   <li>少阳（半表半里阳证）与厥阴（半表半里阴证）<b>可以并见</b>，
     *       即「少阳厥阴合病」——本类 {@code buildCombinedDiseaseMark} 中
     *       本就登记了该合病名，互斥规则与之自相矛盾。</li>
     *   <li>胡希恕明言「<b>四逆散非少阴，根本是少阳病</b>」（阳郁热厥）：
     *       四逆散证见往来寒热、胸胁苦满、口苦（少阳），其手足冷为阳郁而非真寒。
     *       互斥规则会把少阳误删、只留厥阴，与胡希恕之论相悖。</li>
     *   <li>百合病（《金匮·百合狐惑阴阳毒》）依本工程锚点归少阳；
     *       互斥规则同样会把少阳误删。</li>
     * </ol>
     *
     * <p>故六经判定<b>只做白名单过滤，不再做互斥消解</b>；同现即报合病。
     */
    private static final Map<String, String> LIUJING_MUTEX_PAIRS = Map.of();

    // ==================== 内部类型 ====================

    private static class PatientInput {
        final String patientIri;
        final List<String> symptomIris;
        final List<String> pulseIris;
        final List<String> tongueIris;
        final List<String> fuzhengIris;

        /**
         * 物化八纲（fragment）。八纲推理完成后写入，下一阶段（六经）把它断言到患者，
         * 使六经推理无需再依赖判据层 → 模块更小、更快。
         */
        final List<String> bagangTypes = new ArrayList<>();

        /**
         * 物化六经（fragment）。六经推理完成后写入，方证推理把它断言到患者，
         * 从而「固定这些值」直接参与方证匹配。
         */
        final List<String> liujingTypes = new ArrayList<>();

        /**
         * 物化八纲判据（fragment）。八纲推理完成后写入。
         *
         * <p>用途：六经 ≡ 病位 ⊓ 病性，而本体未声明病位/病性互斥，故当患者同时具备
         * 多个病位与多个病性时会推出「病位×病性」交叉积。判据类同时 ⊑ 病位与 ⊑ 病性者，
         * 即把该病位与该病性锁定为一经（如 Panju_C6 ⊑ Banbiaobanli ⊓ Yang → 少阳）。
         * 六经阶段据此消解交叉积（见 {@link #resolveLiujingCrossProduct}）。
         */
        final List<String> panjuTypes = new ArrayList<>();

        PatientInput(String p, List<String> s, List<String> pl, List<String> t, List<String> f) {
            patientIri = p;
            symptomIris = s;
            pulseIris = pl;
            tongueIris = t;
            fuzhengIris = f;
        }
    }

    private static class ScoredFangzheng {
        final OWLClass cls;
        /** 主证命中数（索引口径：OR 分支任一命中即计） */
        final int hits;
        /** 必需条件数（结构化：AND→Σ、OR→min；即 gap 的「空患者」值） */
        final int required;
        /** 主证缺口（结构化：0 ⟺ 定义被完全满足，即 realize 命中） */
        final int gap;
        final int patientSize;
        final int clinicalPriority;
        /** 或然症命中数（纯计数） */
        final int possHits;
        /** 该方证声明的或然症总数 */
        final int possTotal;

        ScoredFangzheng(OWLClass cls, int hits, int required, int gap, int patientSize,
                        int clinicalPriority, int possHits, int possTotal) {
            this.cls = cls;
            this.hits = hits;
            this.required = required;
            this.gap = gap;
            this.patientSize = patientSize;
            this.clinicalPriority = clinicalPriority;
            this.possHits = possHits;
            this.possTotal = possTotal;
        }

        String fragment() { return cls.getIRI().getFragment(); }

        /**
         * 证据等级：{@code MAIN}（有主证命中）/ {@code POSS_ONLY}（仅或然症命中）/
         * {@code NONE}（无任何证据）。
         *
         * <p>依铁律 16：{@code POSS_ONLY} 比「仅候选」更弱一档，<b>永不</b>可写入结论性变量。
         */
        String evidence() {
            if (hits > 0) return "MAIN";
            return possHits > 0 ? "POSS_ONLY" : "NONE";
        }

        /**
         * 证据门控序（0=MAIN，1=POSS_ONLY，2=NONE）——排序首键。
         *
         * <p>为何必须门控：或然症按定义「非主证」，是弱证据（{@link #evidence()}）。
         * 若只按 {@code gap} 排序，一个「仅或然症命中、gap=1」的方证会压过
         * 「有主证命中、gap=2」的方证；若把 {@code possHits} 降序放在 gap 之后，
         * 同 gap 时 {@code POSS_ONLY}（possHits≥1）同样会压过 {@code MAIN}（possHits=0）。
         * 两者都与「或然症是第二键、不得压主证」相悖。
         *
         * <p>故先按证据分级，再在同一级内以 {@code gap} 为主键——既保证主证证据优先，
         * 又保证「gap=0（realize 命中）自然排在最顶」。
         */
        int evidenceRank() {
            if (hits > 0) return 0;
            return possHits > 0 ? 1 : 2;
        }

        /**
         * Jaccard 相似度 —— <b>已废弃（2026-09-21）</b>，保留仅供日志对照，排序不再使用。
         *
         * <p>两处医理硬伤：① 分母含 {@code patientSize}，患者多报一个无关症状即令所有方证
         * 齐降、排序漂移甚至翻转；② {@code required} 大者吃亏，与「定义越精确越应优先」相反。
         * 排序已改用结构化 {@link #gap}（见 {@code rankCandidatesWithScores}）。
         */
        @Deprecated
        double jaccard() {
            int union = patientSize + required - hits;
            return union == 0 ? 0.0 : (double) hits / union;
        }

        @Override
        public String toString() {
            return String.format("%s(hits=%d/%d, gap=%d, poss=%d/%d, prio=%d)",
                    fragment(), hits, required, gap, possHits, possTotal, clinicalPriority);
        }
    }

    // ==================== 方后注加减规则模型 ====================

    /** 单条「加药」动作：herb = 药物 fragment；dose = 括号内剂量原文（可为 null）。 */
    private static class AddAction {
        final String herb;
        final String dose;
        AddAction(String herb, String dose) { this.herb = herb; this.dose = dose; }
    }

    /**
     * 一条方后注加减规则。
     * 原文形如：IF &lt;cond&gt; [AND &lt;cond&gt;]* THEN remove A + add B (剂量) + add C (剂量)
     * condGroups：外层 AND，内层 OR（如 (Dabianying OR Xiali)）。
     */
    private static class HerbRule {
        final String raw;
        final String source;                 // ruleSource 出处（可为 null）
        final List<List<String>> condGroups = new ArrayList<>();
        final List<String> removes = new ArrayList<>();
        final List<AddAction> adds = new ArrayList<>();
        final List<String> retains = new ArrayList<>();

        HerbRule(String raw, String source) { this.raw = raw; this.source = source; }

        /** 规则是否被患者症状集合满足：所有 AND 组均需命中，OR 组命中任一即可。 */
        boolean satisfiedBy(Set<String> patientFrags) {
            if (condGroups.isEmpty()) return false;
            for (List<String> group : condGroups) {
                boolean any = false;
                for (String tok : group) {
                    if (patientFrags.contains(tok)) { any = true; break; }
                }
                if (!any) return false;
            }
            return true;
        }

        String condText() {
            List<String> parts = new ArrayList<>();
            for (List<String> g : condGroups) parts.add(String.join(" OR ", g));
            return String.join(" AND ", parts);
        }
    }

    /** 规则引擎作用于母方后的派生结果。 */
    private static class DerivedResult {
        boolean derived = false;
        final List<String> herbIris = new ArrayList<>();     // 最终组成（IRI）
        final List<String> herbCn = new ArrayList<>();       // 最终组成（中文）
        final List<String> addedIris = new ArrayList<>();    // 规则新增（IRI）
        final List<String> addedCn = new ArrayList<>();
        final List<String> removedIris = new ArrayList<>();  // 规则删除（IRI）
        final List<String> removedCn = new ArrayList<>();
        final List<String> dosageChanges = new ArrayList<>();// 剂量调整说明
        final List<String> appliedRules = new ArrayList<>(); // 命中的规则原文
        final List<String> ruleSources = new ArrayList<>();  // 命中的规则出处
    }

    /**
     * 加减药（方后注派生）的完整结果。
     *
     * <p>由 {@code prescription-recommendation} 与 {@code herb-modification} 两个步骤共用，
     * 保证两步输出逐字段一致。
     */
    private static class HerbModOutcome {
        final DerivedResult derived;
        final List<String> removedHerbIris;
        final List<String> allAddedHerbs;
        final List<String> finalHerbIris;
        final List<String> finalHerbCn;

        HerbModOutcome(DerivedResult derived,
                       List<String> removedHerbIris,
                       List<String> allAddedHerbs,
                       List<String> finalHerbIris,
                       List<String> finalHerbCn) {
            this.derived = derived;
            this.removedHerbIris = removedHerbIris;
            this.allAddedHerbs = allAddedHerbs;
            this.finalHerbIris = finalHerbIris;
            this.finalHerbCn = finalHerbCn;
        }
    }

    // ==================== 初始化 ====================

    @PostConstruct
    public void init() {
        try {
            log.info("==================== 初始化开始 ====================");
            log.info("初始化 TCMOntologyJobWorker（模式: 两阶段 + 精确抽取 + 递归闭包）...");

            long tBackend = System.currentTimeMillis();
            com.ocean.ontopobdahandler.OBDAHandler.init(obdaPropertiesPath, obdaPath);
            backendService = BackendService.getInstance(
                    mainOntologyPath,
                    com.ocean.ontopobdahandler.OBDAHandler.getInstance());
            queryService = new QueryService(backendService);
            tboxDf = backendService.getOntologyService().gettBoxOntology()
                    .getOWLOntologyManager().getOWLDataFactory();
            log.info("[init] BackendService 初始化完成，耗时 {} ms",
                    System.currentTimeMillis() - tBackend);

            long tAbox = System.currentTimeMillis();
            loadAboxLookupOntology();
            log.info("[init] ABox 查表本体加载完成，耗时 {} ms",
                    System.currentTimeMillis() - tAbox);

            long tMeta = System.currentTimeMillis();
            liujingSubclasses = backendService.getAllNamedSubclasses(
                    IRI.create(BASE_NS + "Liujingbing"));
            fangzhengSubclasses = backendService.getAllNamedSubclasses(IRI.create(BASE_NS + "Fangzheng"))
                    .stream()
                    .filter(c -> !isAbstractClass(c))
                    .collect(Collectors.toSet());
            jianJiaSubclasses = backendService.getAllNamedSubclasses(
                    IRI.create(BASE_NS + "JianJiaZheng"));
            bagangSubclasses = backendService.getDirectNamedSubclasses(
                    IRI.create(BASE_NS + "Bagang"));
            bagangPanjuSubclasses = backendService.getAllNamedSubclasses(
                    IRI.create(BASE_NS + "BagangPanju"));

            log.info("[init] Bagang 直接子类 {} 个: {}",
                    bagangSubclasses.size(),
                    bagangSubclasses.stream()
                            .map(c -> c.getIRI().getFragment())
                            .sorted().collect(Collectors.toList()));
            log.info("[init] 元数据扫描完成，耗时 {} ms", System.currentTimeMillis() - tMeta);
            log.info("八纲子类={}(已过滤方证/六经/兼夹), 六经全部子树={}, 方证={}, 兼夹证={}, 八纲判据={}",
                    bagangSubclasses.size(), liujingSubclasses.size(),
                    fangzhengSubclasses.size(), jianJiaSubclasses.size(),
                    bagangPanjuSubclasses.size());

            long tIdx = System.currentTimeMillis();
            buildSymptomIndex();
            log.info("[init] 症状-方证倒排索引构建完成，耗时 {} ms",
                    System.currentTimeMillis() - tIdx);

            long tComp = System.currentTimeMillis();
            buildCompositeMaps();
            log.info("[init] 复合-原子映射构建完成，耗时 {} ms",
                    System.currentTimeMillis() - tComp);

            long tInc = System.currentTimeMillis();
            loadIncompatibilitiesFromObda();
            log.info("[init] 配伍禁忌索引构建完成，耗时 {} ms",
                    System.currentTimeMillis() - tInc);

            long tFzLj = System.currentTimeMillis();
            buildFangzhengLiujingMap();
            log.info("[init] 方证-六经缓存构建完成，耗时 {} ms",
                    System.currentTimeMillis() - tFzLj);

            long tFzBa = System.currentTimeMillis();
            buildFangzhengBagangMap();
            log.info("[init] 方证-八纲属性缓存构建完成（{} 个方证），耗时 {} ms",
                    fangzhengBagangMap.size(), System.currentTimeMillis() - tFzBa);

            long tPanju = System.currentTimeMillis();
            buildPanjuBindingMap();
            log.info("[init] 判据-病位/病性绑定缓存构建完成（{} 个判据），耗时 {} ms",
                    panjuBingweiMap.size(), System.currentTimeMillis() - tPanju);

            long tRule = System.currentTimeMillis();
            loadHerbRules();
            log.info("[init] 方后注加减规则加载完成（{} 个方证 / {} 条规则），耗时 {} ms",
                    fangzhengRuleIndex.size(),
                    fangzhengRuleIndex.values().stream().mapToInt(List::size).sum(),
                    System.currentTimeMillis() - tRule);

            log.info("==================== 初始化完成 ====================");
        } catch (Exception e) {
            log.error("初始化失败", e);
            throw new RuntimeException("初始化失败", e);
        }
    }

    // ============================================================
    // ABox 查表本体（症状 / 舌象 / 脉象个体字典）
    // ============================================================

    /**
     * 单独加载 ABox 查表本体。
     *
     * <p>复用主本体<b>完全相同</b>的加载方式 —— {@link OntologyService} 构造器
     * （{@code loadOntologyFilesWithOWL}：AutoIRIMapper + TTL 映射 + {@code owl:imports} 解析，
     * 再由 {@code getPrefixSpaceAndInjectToOntology} 把所有已加载本体合并成一份扁平本体），
     * 只是换成<b>独立实例</b>，使这些个体不会进入 {@code backendService} 的
     * {@code gettBoxOntology()}，从而不再拖累全局推理器的 {@code CLASS_HIERARCHY} 分类。
     *
     * <p>扁平化是必需的：{@code OWLOntology.classAssertionAxioms()} <b>不遍历 owl:imports</b>，
     * 而 {@code tcm-all-abox.owl} 只有 import 语句。
     *
     * <p>诊断链路对 ABox 的用法只有「按 IRI 取类型断言」这一种
     * （{@code collectIndividualTypes} / {@code addObjectAssertionsAndCopyTypes}），
     * 不经过推理器，故放在独立本体里语义完全等价。
     */
    private void loadAboxLookupOntology() {
        if (aboxPath == null || aboxPath.isBlank()) {
            log.warn("[ABox] 未配置 ontology.abox-path —— 症状/舌象/脉象个体查表将不可用");
            return;
        }
        File f = new File(aboxPath);
        if (!f.exists()) {
            log.error("[ABox] 文件不存在: {}", aboxPath);
            return;
        }
        try {
            OntologyService aboxService = new OntologyService(aboxPath);
            aboxLookupOntology = aboxService.gettBoxOntology();
            log.info("[ABox] 查表本体加载完成: {} 公理, {} 个体, {} 类, 文件={}",
                    aboxLookupOntology.getAxiomCount(),
                    aboxLookupOntology.individualsInSignature().count(),
                    aboxLookupOntology.classesInSignature().count(),
                    f.getName());
        } catch (Exception e) {
            log.error("[ABox] 加载失败: {}", aboxPath, e);
        }
    }

    /**
     * 返回 ABox 查表本体；若未加载成功则退回 {@code gettBoxOntology()}（此时查表结果为空，
     * 但不会抛异常，便于定位问题）。
     */
    private OWLOntology aboxLookup() {
        return aboxLookupOntology != null
                ? aboxLookupOntology
                : backendService.getOntologyService().gettBoxOntology();
    }

    // ============================================================
    // 症状-方证倒排索引
    // ============================================================

    private void buildSymptomIndex() {
        OWLOntology tbox = backendService.getOntologyService().gettBoxOntology();
        Map<String, Set<OWLClass>> index = new HashMap<>();
        Map<OWLClass, Integer> requiredCount = new HashMap<>();
        Map<OWLClass, Set<OWLClass>> possibleMap = new HashMap<>();
        Map<String, Set<OWLClass>> possIndex = new HashMap<>();
        Set<IRI> propIris = Set.of(
                IRI.create(HAS_SYMPTOM),
                IRI.create(HAS_PULSE),
                IRI.create(HAS_TONGUE),
                IRI.create(HAS_ABDOMINAL));
        IRI possIri = IRI.create(HAS_POSSIBLE_SYMPTOM);

        int covered = 0;
        int possCovered = 0;
        for (OWLClass fz : fangzhengSubclasses) {
            // 索引用途：收集 OR 分支的全部填充符（命中任一即登记该方证）
            Set<String> syms = OntologyModuleUtils.collectRestrictionFillers(tbox, fz, propIris);
            if (!syms.isEmpty()) covered++;
            for (String s : syms) {
                index.computeIfAbsent(s, k -> new HashSet<>()).add(fz);
            }
            // 判定用途：结构化 gap 的「空患者」值（AND→Σ、OR→min），即「必需条件数」
            requiredCount.put(fz, OntologyModuleUtils.definitionGap(
                    tbox, fz, Collections.emptySet(), propIris));

            // 或然症：仅自身声明，不继承
            Set<OWLClass> poss = OntologyModuleUtils.collectPossibleSymptoms(tbox, fz, possIri);
            if (!poss.isEmpty()) {
                possCovered++;
                possibleMap.put(fz, poss);
                for (OWLClass p : poss) {
                    possIndex.computeIfAbsent(p.getIRI().getFragment(), k -> new HashSet<>()).add(fz);
                }
            }
        }
        symptomToFangzhengIndex = index;
        fangzhengRequiredCount = requiredCount;
        fangzhengPossibleMap = possibleMap;
        possibleToFangzhengIndex = possIndex;
        log.info("[init] 症状-方证索引: 键数={}, 覆盖方证={}/{}",
                index.size(), covered, fangzhengSubclasses.size());
        log.info("[init] 或然症索引: 键数={}, 覆盖方证={}/{}",
                possIndex.size(), possCovered, fangzhengSubclasses.size());
    }

    private Set<OWLClass> topBySymptomOverlap(Set<String> patientSymptoms, int topN) {
        if (symptomToFangzhengIndex == null
                || patientSymptoms == null || patientSymptoms.isEmpty()) {
            return Collections.emptySet();
        }
        Map<OWLClass, Integer> scores = new HashMap<>();
        for (String sym : patientSymptoms) {
            Set<OWLClass> fzs = symptomToFangzhengIndex.get(sym);
            if (fzs == null) continue;
            for (OWLClass fz : fzs) scores.merge(fz, 1, Integer::sum);
        }
        return scores.entrySet().stream()
                .sorted((a, b) -> {
                    int c = Integer.compare(b.getValue(), a.getValue());
                    if (c != 0) return c;
                    return a.getKey().getIRI().getFragment()
                            .compareTo(b.getKey().getIRI().getFragment());
                })
                .limit(topN)
                .map(Map.Entry::getKey)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    // ============================================================
    // 候选方证排序（2026-09-21 改：证据门控 + gap 主键 + 或然症第二键，零加权）
    // 排序规则（依次）：
    //   0. 证据门控（MAIN 优先于 POSS_ONLY，或然症不得压主证）；
    //   1. 主证缺口 gap 升序（还差几个症状才能命中；gap=0 ⟺ realize 命中）；
    //   2. 父子层级（gap 相同时子方优先，依「方随证转」）；
    //   3. 广谱高分方证惩罚（五苓散后置）；
    //   4. 方证六经与患者六经对称差升序（先辨六经，继辨方证）；
    //   5. 或然症命中数降序（或然症只在同级同 gap 时参与，纯计数）；
    //   6. 或然症声明数升序（声明少者更特异）；
    //   7. clinicalPriority 升序；
    //   8. 字典序兜底。
    // 全程无权重常数——或然症以「排序键」接入，而非与主证分数加权求和。
    // ============================================================
    private List<ScoredFangzheng> rankCandidatesWithScores(
            Set<OWLClass> candidates, Set<String> patientSymptoms, int topN) {
        return rankCandidatesWithScores(candidates, patientSymptoms, topN, null, null, null);
    }

    private List<ScoredFangzheng> rankCandidatesWithScores(
            Set<OWLClass> candidates, Set<String> patientSymptoms, int topN,
            Set<OWLClass> realizedClasses) {
        return rankCandidatesWithScores(candidates, patientSymptoms, topN, realizedClasses, null, null);
    }

    private List<ScoredFangzheng> rankCandidatesWithScores(
            Set<OWLClass> candidates, Set<String> patientSymptoms, int topN,
            Set<OWLClass> realizedClasses, Set<OWLClass> patientLiujing) {
        return rankCandidatesWithScores(
                candidates, patientSymptoms, topN, realizedClasses, patientLiujing, null);
    }

    /**
     * 带「realize 命中集」「患者六经」「患者八纲」的候选排序。
     *
     * <p><b>主键为结构化「主证缺口」{@code gap}</b>（{@link OntologyModuleUtils#definitionGap}）：
     * AND→Σ、OR→min，纯整数计数、零自由参数，与方证定义（充要条件）同构。
     * {@code gap = 0} ⟺ 定义被完全满足（realize 命中）。
     *
     * <p>此前用 Jaccard 作主键，有两处医理硬伤：分母含 {@code patientSize}（患者多报一个
     * 无关症状即令排序漂移甚至翻转）、{@code required} 大者吃亏。已弃用。
     *
     * <p><b>或然症</b>（{@code possibleSymptom}）作为第 5 键参与：只在 {@code gap} 相同
     * （含同为 0 / 同为 1）时生效。gap 是粗粒度整数、平局频繁，故或然症<b>经常能生效</b>。
     *
     * @param patientLiujing 患者已定的六经集合（{@code input.liujingTypes} 对应类）；可为 null。
     * @param patientBagang  患者已定的八纲 fragment 集合（{@code input.bagangTypes}）；可为 null。
     */
    private List<ScoredFangzheng> rankCandidatesWithScores(
            Set<OWLClass> candidates, Set<String> patientSymptoms, int topN,
            Set<OWLClass> realizedClasses, Set<OWLClass> patientLiujing,
            Set<String> patientBagang) {
        if (candidates == null || candidates.isEmpty()) return Collections.emptyList();

        int patientSize = (patientSymptoms == null) ? 0 : patientSymptoms.size();

        // 患者「已满足」的类 fragment：四诊 + 推理所得六经/八纲。
        // gap 的叶子判定（gap(叶子 类 C) = 0 若 C ∈ 患者推理类型）依赖此集合。
        Set<String> satisfiedFrags = new HashSet<>();
        if (patientSymptoms != null) satisfiedFrags.addAll(patientSymptoms);
        if (patientLiujing != null) {
            for (OWLClass c : patientLiujing) satisfiedFrags.add(c.getIRI().getFragment());
        }
        if (patientBagang != null) satisfiedFrags.addAll(patientBagang);

        if (patientSymptoms == null || patientSymptoms.isEmpty()) {
            return candidates.stream()
                    .sorted(Comparator.comparing(c -> c.getIRI().getFragment()))
                    .limit(topN)
                    .map(c -> scoreOf(c, 0, satisfiedFrags, 0, patientSize))
                    .collect(Collectors.toList());
        }

        Map<OWLClass, Integer> hitCounts = new HashMap<>();
        for (String sym : patientSymptoms) {
            Set<OWLClass> fzs = symptomToFangzhengIndex.get(sym);
            if (fzs == null) continue;
            for (OWLClass fz : fzs) {
                if (candidates.contains(fz)) {
                    hitCounts.merge(fz, 1, Integer::sum);
                }
            }
        }

        Map<OWLClass, Integer> possHitCounts = new HashMap<>();
        for (String sym : patientSymptoms) {
            Set<OWLClass> fzs = possibleToFangzhengIndex.get(sym);
            if (fzs == null) continue;
            for (OWLClass fz : fzs) {
                if (candidates.contains(fz)) {
                    possHitCounts.merge(fz, 1, Integer::sum);
                }
            }
        }

        return candidates.stream()
                .map(c -> scoreOf(c, hitCounts.getOrDefault(c, 0), satisfiedFrags,
                        possHitCounts.getOrDefault(c, 0), patientSize))
                .sorted((a, b) -> {
                    // 0. 证据门控：主证命中（MAIN）优先于仅或然症命中（POSS_ONLY）
                    //    —— 或然症是弱证据，不得压过主证（见 evidenceRank 注释）
                    int cmp = Integer.compare(a.evidenceRank(), b.evidenceRank());
                    if (cmp != 0) return cmp;
                    // 1. 主证缺口 gap 升序（还差几个症状才能命中；gap=0 ⟺ realize 命中）
                    cmp = Integer.compare(a.gap, b.gap);
                    if (cmp != 0) return cmp;
                    // 2. 父子层级：gap 相同时子方优先（方随证转）
                    cmp = compareSpecialization(a.cls, b.cls);
                    if (cmp != 0) return cmp;
                    // 2.5 定义精确度：gap 相同时，约束愈多（定义愈精确）者优先。
                    //     「方证相应，贵在精当」——泛应之方虽亦完全满足，然特异性低，当居其次。
                    //     例：小柴胡汤证「但见一证便是」（required=2）不得压四逆散证（required=6）；
                    //         茯苓戎盐汤证「阳明病+小便不利」（required=2）不得压猪苓汤证、五苓散证；
                    //         甘草汤证（required=2）不得压桔梗汤证、排脓汤证。
                    //     此键即旧 Jaccard 注释所指「定义越精确越应优先」，纯整数计数、无自由参数。
                    cmp = Integer.compare(b.required, a.required);
                    if (cmp != 0) return cmp;
                    // 3. 广谱高分方证惩罚（五苓散后置）
                    boolean aBroad = BROAD_MATCH_PENALTY.contains(a.fragment());
                    boolean bBroad = BROAD_MATCH_PENALTY.contains(b.fragment());
                    if (aBroad != bBroad) return aBroad ? 1 : -1;
                    // 4. 方证六经与患者六经相符度：对称差小者优先
                    //    （方证相应须六经相应；任一方无六经归属时不介入）
                    if (patientLiujing != null && !patientLiujing.isEmpty()) {
                        Set<OWLClass> la = liujingOf(a.cls);
                        Set<OWLClass> lb = liujingOf(b.cls);
                        if (!la.isEmpty() && !lb.isEmpty()) {
                            cmp = Integer.compare(symmetricDiff(la, patientLiujing),
                                                  symmetricDiff(lb, patientLiujing));
                            if (cmp != 0) return cmp;
                        }
                    }
                    // 5. 或然症命中数降序（纯计数，无权重）
                    cmp = Integer.compare(b.possHits, a.possHits);
                    if (cmp != 0) return cmp;
                    // 6. 或然症声明数升序（声明少者更特异）
                    cmp = Integer.compare(a.possTotal, b.possTotal);
                    if (cmp != 0) return cmp;
                    // 7. clinicalPriority 升序
                    cmp = Integer.compare(a.clinicalPriority, b.clinicalPriority);
                    if (cmp != 0) return cmp;
                    // 8. 字典序兜底
                    return a.fragment().compareTo(b.fragment());
                })
                .limit(topN)
                .collect(Collectors.toList());
    }

    /** 组装单个候选的打分（hits / required / gap / possHits / possTotal）。 */
    private ScoredFangzheng scoreOf(OWLClass c, int hits, Set<String> satisfiedFrags,
                                    int possHits, int patientSize) {
        OWLOntology tbox = backendService.getOntologyService().gettBoxOntology();
        int gap = OntologyModuleUtils.definitionGap(tbox, c, satisfiedFrags, FANGZHENG_PROP_IRIS);
        Set<OWLClass> poss = fangzhengPossibleMap.getOrDefault(c, Collections.emptySet());
        return new ScoredFangzheng(c, hits,
                fangzhengRequiredCount.getOrDefault(c, 0), gap, patientSize,
                getClinicalPriority(c), possHits, poss.size());
    }

    /**
     * 【问题1 修复】判断 child 是否是 parent 的直接子方。
     */
    private boolean isChildOf(String childName, String parentName) {
        Set<String> children = FANGZHENG_PARENT_TO_CHILDREN.get(parentName);
        return children != null && children.contains(childName);
    }

    /**
     * 【问题1 修复】比较两个方证的特化关系。
     * 返回 -1 表示 a 是 b 的子方（a 优先）；
     * 返回 1  表示 b 是 a 的子方（b 优先）；
     * 返回 0  表示无父子关系。
     */
    private int compareSpecialization(OWLClass a, OWLClass b) {
        String fa = a.getIRI().getFragment();
        String fb = b.getIRI().getFragment();
        if (isChildOf(fa, fb)) return -1;
        if (isChildOf(fb, fa)) return 1;
        return 0;
    }

    /**
     * 方证归属的六经集合（来自 belongsToLiujing 注解）。
     */
    private Set<OWLClass> liujingOf(OWLClass cls) {
        Set<OWLClass> lj = fangzhengLiujingMap.get(cls);
        return lj == null ? Collections.emptySet() : lj;
    }

    /**
     * 方证归属的六经个数（来自 belongsToLiujing 注解）。
     */
    private int liujingCount(OWLClass cls) {
        return liujingOf(cls).size();
    }

    /**
     * 两个六经集合的对称差大小：|A Δ B|。
     *
     * <p>用于「方证相应」末位裁决 —— 方证六经与患者六经越吻合（对称差越小）越优先。
     * 例：患者太阳太阴合病时，桂枝人参汤证（太阳+太阴，Δ=0）应优先于
     * 赤石脂禹余粮汤证（太阴，Δ=1），二者症状集相同、仅六经可别。
     */
    private int symmetricDiff(Set<OWLClass> a, Set<OWLClass> b) {
        int d = 0;
        for (OWLClass c : a) if (!b.contains(c)) d++;
        for (OWLClass c : b) if (!a.contains(c)) d++;
        return d;
    }

    /** 读取方证的 clinicalPriority 注解；缺省值用 Integer.MAX_VALUE（排最后） */
    private int getClinicalPriority(OWLClass cls) {
        OWLOntology tbox = backendService.getOntologyService().gettBoxOntology();
        for (OWLAnnotationAssertionAxiom ax :
                tbox.annotationAssertionAxioms(cls.getIRI()).collect(Collectors.toList())) {
            if (!ax.getProperty().getIRI().getFragment().equals("clinicalPriority")) continue;
            OWLAnnotationValue v = ax.getValue();
            if (v instanceof OWLLiteral lit) {
                try {
                    return Integer.parseInt(lit.getLiteral().trim());
                } catch (NumberFormatException ignored) { }
            }
        }
        return Integer.MAX_VALUE;
    }

    private boolean hasBagangOrLiujing(Set<OWLClass> stage1Types) {
        if (stage1Types == null || stage1Types.isEmpty()) return false;
        for (OWLClass c : stage1Types) {
            if (bagangSubclasses.contains(c) || liujingSubclasses.contains(c)) {
                return true;
            }
        }
        return false;
    }

    // ============================================================
    // 复合-原子映射（通用化）
    // ============================================================

    private void buildCompositeMaps() {
        OWLOntology tbox = backendService.getOntologyService().gettBoxOntology();

        IRI pulseIri = IRI.create(BASE_NS + "Maixiang");
        IRI symIri = IRI.create(BASE_NS + "Zhengzhuang");

        compositePulseMap = OntologyModuleUtils.buildIntersectionCompositeMap(
                tbox, pulseIri, backendService.getAllNamedSubclasses(pulseIri));
        compositeSymptomMap = OntologyModuleUtils.buildIntersectionCompositeMap(
                tbox, symIri, backendService.getAllNamedSubclasses(symIri));

        log.info("[init] 复合脉映射: {} 个", compositePulseMap.size());
        log.info("[init] 复合症状映射: {} 个", compositeSymptomMap.size());
    }

    // ============================================================
    // 十八反/十九畏（通用化 OBDA 工具）
    // ============================================================

    private void loadIncompatibilitiesFromObda() {
        try {
            String yaowuQ = """
                PREFIX : <http://www.tcm-classics.org/jingfang#>
                PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
                SELECT ?iri ?label WHERE {
                    ?iri a :Yaowu .
                    OPTIONAL { ?iri rdfs:label ?label . }
                }
                """;
            for (Map<String, String> row :
                    backendService.getObdaHandler().executeAboxQuery(yaowuQ)) {
                String iri = row.get("iri");
                String label = row.get("label");
                if (iri == null) continue;
                String frag = ObdaQueryUtils.fragmentOf(iri);
                if (frag == null) continue;
                yaowuLabelMap.put(frag,
                        (label != null && !label.isBlank()) ? label.trim() : frag);
            }

            String antQ = """
                PREFIX : <http://www.tcm-classics.org/jingfang#>
                SELECT ?a ?b WHERE { ?a :antagonistic ?b . }
                """;
            shibafanMap = ObdaQueryUtils.loadUndirectedRelationFromObda(
                    backendService.getObdaHandler(), antQ);

            String fearQ = """
                PREFIX : <http://www.tcm-classics.org/jingfang#>
                SELECT ?a ?b WHERE { ?a :fearing ?b . }
                """;
            shijiuweiMap = ObdaQueryUtils.loadUndirectedRelationFromObda(
                    backendService.getObdaHandler(), fearQ);

            log.info("[init] 十八反: {} 种药物, {} 组关系",
                    shibafanMap.size(),
                    shibafanMap.values().stream().mapToInt(Set::size).sum() / 2);
            log.info("[init] 十九畏: {} 种药物, {} 组关系",
                    shijiuweiMap.size(),
                    shijiuweiMap.values().stream().mapToInt(Set::size).sum() / 2);
            log.info("[init] 药物 label 映射: {} 条", yaowuLabelMap.size());

        } catch (Exception e) {
            log.error("[init] 通过 OBDA 加载配伍禁忌失败", e);
            shibafanMap = Collections.emptyMap();
            shijiuweiMap = Collections.emptyMap();
            yaowuLabelMap = Collections.emptyMap();
        }
    }

    private String labelOf(String fragment) {
        return yaowuLabelMap.getOrDefault(fragment, fragment);
    }

    // ============================================================
    // 阶段 0：八纲（专用最小模块）
    // ============================================================

    /**
     * 八纲专用 minibox：只含「判据层 + 八纲」。
     * <p>不含六经白名单、兼夹证、方证 —— 八纲只需 判据 → 八纲 的传导，
     * 模块显著小于六经模块，推理更快。
     */
    private Set<OWLAxiom> extractBagangModule(PatientInput input) {
        OWLOntology tbox = backendService.getOntologyService().gettBoxOntology();
        OWLDataFactory df = tbox.getOWLOntologyManager().getOWLDataFactory();

        Set<OWLClass> initial = new HashSet<>();

        Set<String> patientFrags = collectPatientFrags(input);
        for (String f : patientFrags) {
            initial.add(df.getOWLClass(IRI.create(BASE_NS + f)));
        }

        // 八纲类 + 判据层（判据引用了症状，闭包只向上走，须显式加入 seed）
        initial.addAll(bagangSubclasses);
        initial.addAll(bagangPanjuSubclasses);

        for (String t : TOP_LEVEL_CLASSES) {
            initial.add(df.getOWLClass(IRI.create(BASE_NS + t)));
        }

        initial.addAll(OntologyModuleUtils.collectIndividualTypes(
                aboxLookup(), collectAllIndividualIris(input), BASE_NS));

        Set<OWLClass> keepClasses = OntologyModuleUtils.collectClassClosure(tbox, initial);
        Set<OWLAxiom> tboxOnly = OntologyModuleUtils.extractTBoxModule(tbox, keepClasses);

        log.info("[阶段0] 患者症状={}, 初始={}, 闭包={}, mini 公理={}",
                patientFrags.size(), initial.size(), keepClasses.size(), tboxOnly.size());
        return tboxOnly;
    }

    // ============================================================
    // 阶段 1：六经 + 兼夹证
    // ============================================================

    private Set<OWLAxiom> extractLiujingModule(PatientInput input) {
        OWLOntology tbox = backendService.getOntologyService().gettBoxOntology();
        OWLDataFactory df = tbox.getOWLOntologyManager().getOWLDataFactory();

        Set<OWLClass> initial = new HashSet<>();

        Set<String> patientFrags = collectPatientFrags(input);
        for (String f : patientFrags) {
            initial.add(df.getOWLClass(IRI.create(BASE_NS + f)));
        }

        for (String six : SIX_CHANNEL_WHITELIST) {
            initial.add(df.getOWLClass(IRI.create(BASE_NS + six)));
        }

        initial.addAll(bagangSubclasses);
        initial.addAll(jianJiaSubclasses);
        // ⑥ 判据层入模块：判据类引用了症状，但闭包只向上走，须显式加入 seed。
        // 若八纲已物化（即本阶段是「六经」而非「八纲」），判据层可省 → 模块更小、推理更快。
        if (input.bagangTypes.isEmpty()) {
            initial.addAll(bagangPanjuSubclasses);
        } else {
            log.info("[阶段1] 八纲已物化 {}，跳过判据层 seed", input.bagangTypes);
        }

        for (String t : TOP_LEVEL_CLASSES) {
            initial.add(df.getOWLClass(IRI.create(BASE_NS + t)));
        }

        initial.addAll(OntologyModuleUtils.collectIndividualTypes(
                aboxLookup(), collectAllIndividualIris(input), BASE_NS));

        Set<OWLClass> keepClasses = OntologyModuleUtils.collectClassClosure(tbox, initial);
        Set<OWLAxiom> tboxOnly = OntologyModuleUtils.extractTBoxModule(tbox, keepClasses);

        log.info("[阶段1] 患者症状={}, 初始={}, 闭包={}, mini 公理={}",
                patientFrags.size(), initial.size(), keepClasses.size(), tboxOnly.size());
        return tboxOnly;
    }

    // ============================================================
    // 阶段 2：方证
    // ============================================================

    private Set<OWLAxiom> extractFangzhengModule(PatientInput input,
                                                 Set<OWLClass> stage1Types) {
        OWLOntology tbox = backendService.getOntologyService().gettBoxOntology();
        OWLDataFactory df = tbox.getOWLOntologyManager().getOWLDataFactory();

        Set<OWLClass> initial = new HashSet<>();

        Set<String> patientFrags = collectPatientFrags(input);
        for (String f : patientFrags) {
            initial.add(df.getOWLClass(IRI.create(BASE_NS + f)));
        }

        Set<OWLClass> relevantFangzheng = filterFangzheng(
                tbox, stage1Types, patientFrags, input.bagangTypes);
        fangzhengCandidatesCache.put(input.patientIri, relevantFangzheng);
        initial.addAll(relevantFangzheng);

        // 物化的八纲 / 六经作为「固定值」进入模块 seed，方证匹配直接消费
        for (String f : input.bagangTypes) {
            initial.add(df.getOWLClass(IRI.create(BASE_NS + f)));
        }
        for (String f : input.liujingTypes) {
            initial.add(df.getOWLClass(IRI.create(BASE_NS + f)));
        }

        for (String t : TOP_LEVEL_CLASSES) {
            initial.add(df.getOWLClass(IRI.create(BASE_NS + t)));
        }

        initial.addAll(OntologyModuleUtils.collectIndividualTypes(
                aboxLookup(), collectAllIndividualIris(input), BASE_NS));

        Set<OWLClass> keepClasses = OntologyModuleUtils.collectClassClosure(tbox, initial);
        Set<OWLAxiom> tboxOnly = OntologyModuleUtils.extractTBoxModule(tbox, keepClasses);

        log.info("[阶段2] 筛选方证={}/{}, 初始={}, 闭包={}, mini 公理={}",
                relevantFangzheng.size(), fangzhengSubclasses.size(),
                initial.size(), keepClasses.size(), tboxOnly.size());
        return tboxOnly;
    }

    private List<String> collectAllIndividualIris(PatientInput input) {
        List<String> all = new ArrayList<>();
        all.addAll(input.symptomIris);
        all.addAll(input.pulseIris);
        all.addAll(input.tongueIris);
        all.addAll(input.fuzhengIris);
        return all;
    }

    private Set<String> collectPatientFrags(PatientInput input) {
        Set<String> frags = new HashSet<>();
        input.symptomIris.forEach(i -> frags.add(frag(i)));
        input.pulseIris.forEach(i -> frags.add(frag(i)));
        input.tongueIris.forEach(i -> frags.add(frag(i)));
        input.fuzhengIris.forEach(i -> frags.add(frag(i)));
        // 复合（脉/症状）合成：患者已具备某复合类的全部原子成分时，该复合类亦应计入患者片段。
        // 依据：推理机已通过 addSynthesizedComposites 合成该复合个体并断言给患者（方证 realize 可见），
        //       打分用的 hits 若不随之计入，「realize 命中」与「打分命中」口径就不一致
        //       （例：浮脉+紧脉 → 浮紧脉；麻黄汤证 require 浮紧脉，realize 通过却计不到 hits）。
        expandComposites(frags, compositePulseMap);
        expandComposites(frags, compositeSymptomMap);
        return frags;
    }

    /** 迭代展开复合类：只要某复合类的全部原子成分均在 frags 中，就把该复合类加入 frags（支持链式复合）。 */
    private void expandComposites(Set<String> frags, Map<OWLClass, Set<OWLClass>> compositeMap) {
        if (compositeMap == null || compositeMap.isEmpty()) return;
        boolean changed = true;
        int guard = 0;
        while (changed && guard++ < 5) {
            changed = false;
            for (Map.Entry<OWLClass, Set<OWLClass>> e : compositeMap.entrySet()) {
                String compFrag = e.getKey().getIRI().getFragment();
                if (frags.contains(compFrag)) continue;
                boolean allPresent = true;
                for (OWLClass comp : e.getValue()) {
                    if (!frags.contains(comp.getIRI().getFragment())) {
                        allPresent = false;
                        break;
                    }
                }
                if (allPresent) {
                    frags.add(compFrag);
                    changed = true;
                }
            }
        }
    }

    private Set<OWLClass> filterFangzheng(OWLOntology tbox,
                                          Set<OWLClass> stage1Types,
                                          Set<String> patientSymptomFrags,
                                          Collection<String> patientBagang) {

        // ========== Step 1: 提取阶段 1 确定的六经 ==========
        Set<OWLClass> patientLiujing = stage1Types.stream()
                .filter(liujingSubclasses::contains)
                .collect(Collectors.toSet());

        // ========== Step 2: 从方证-六经映射拉出六经池 ==========
        Set<OWLClass> liujingPool;
        if (patientLiujing.isEmpty()) {
            liujingPool = fangzhengSubclasses;
            log.warn("[阶段2] 六经不定，池 = 全库 {} 个", liujingPool.size());
        } else {
            liujingPool = new HashSet<>();
            for (OWLClass fz : fangzhengSubclasses) {
                Set<OWLClass> fzLj = fangzhengLiujingMap.get(fz);
                if (fzLj == null) {
                    fzLj = OntologyModuleUtils.findRelatedClasses(tbox, fz, liujingSubclasses);
                }
                if (!Collections.disjoint(fzLj, patientLiujing)) {
                    liujingPool.add(fz);
                }
            }
            log.info("[阶段2] 六经 {} → 池 = {} 个",
                    patientLiujing.stream().map(c -> c.getIRI().getFragment())
                            .sorted().collect(Collectors.toList()),
                    liujingPool.size());
        }

        // ========== Step 3: 只在六经池里做症状匹配 ==========
        // 主证命中（equivalentClass 的 you_zhengzhuang/you_maixiang/you_shexiang/you_fuzheng）
        Set<OWLClass> symptomMatching = new HashSet<>();
        for (String sym : patientSymptomFrags) {
            Set<OWLClass> fzs = symptomToFangzhengIndex.get(sym);
            if (fzs == null) continue;
            for (OWLClass fz : fzs) {
                if (liujingPool.contains(fz)) symptomMatching.add(fz);
            }
        }
        int mainMatched = symptomMatching.size();

        // ========== Step 3b: 或然症命中并入池（决策 D2）==========
        // 或然症是脉象/舌象/腹证进入方证匹配的唯一通道；允许「主证零命中」的方证进候选
        // （因为没有别的可以定方证了）。这些方证在排序时被 evidence 门控为 POSS_ONLY，
        // 永远排在 MAIN 之后，且永不写入结论性变量（铁律 16）。
        Set<OWLClass> possMatching = new HashSet<>();
        for (String sym : patientSymptomFrags) {
            Set<OWLClass> fzs = possibleToFangzhengIndex.get(sym);
            if (fzs == null) continue;
            for (OWLClass fz : fzs) {
                if (liujingPool.contains(fz)) possMatching.add(fz);
            }
        }
        possMatching.removeAll(symptomMatching);   // 已由主证命中者不重复计入
        symptomMatching.addAll(possMatching);

        log.info("[阶段2] 池内症状命中 = {}（主证 {} + 仅或然症 {}）",
                symptomMatching.size(), mainMatched, possMatching.size());

        // ========== Step 5: 取 Top N ==========
        List<ScoredFangzheng> ranked = rankCandidatesWithScores(
                symptomMatching, patientSymptomFrags, FALLBACK_TOP_N, null, patientLiujing,
                patientBagang == null ? null : new HashSet<>(patientBagang));

        Set<OWLClass> top = ranked.stream()
                .map(s -> s.cls)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        log.info("[阶段2] Top{} = {}", FALLBACK_TOP_N, top.size());
        return top;
    }

    // ============================================================
    // 上下文构建（走 MiniReasoningContextManager）
    // ============================================================

    private <T> T withBagangReasoner(PatientInput input, Function<MiniContext, T> action) {
        final String cacheKey = input.patientIri + STAGE_BG;
        return miniCtxMgr.withContext(
                cacheKey,
                () -> extractBagangModule(input),
                df -> buildPatientAxioms(df, input),
                action);
    }

    private <T> T withLiujingReasoner(PatientInput input, Function<MiniContext, T> action) {
        final String cacheKey = input.patientIri + STAGE_LJ;
        return miniCtxMgr.withContext(
                cacheKey,
                () -> extractLiujingModule(input),
                df -> buildPatientAxioms(df, input),
                action);
    }

    private <T> T withFangzhengReasoner(PatientInput input,
                                        Set<OWLClass> stage1Types,
                                        Function<MiniContext, T> action) {
        final String cacheKey = input.patientIri + STAGE_FZ;
        return miniCtxMgr.withContext(
                cacheKey,
                () -> extractFangzhengModule(input, stage1Types),
                df -> buildPatientAxioms(df, input),
                action);
    }

    // ==================== 患者 ABox ====================

    private Set<OWLAxiom> buildPatientAxioms(OWLDataFactory df, PatientInput input) {
        Set<OWLAxiom> axioms = new HashSet<>();
        OWLNamedIndividual patient = df.getOWLNamedIndividual(IRI.create(input.patientIri));
        axioms.add(df.getOWLClassAssertionAxiom(
                df.getOWLClass(IRI.create(BASE_NS + "Huanzhe")), patient));

        OWLObjectProperty hasSymptom = df.getOWLObjectProperty(IRI.create(HAS_SYMPTOM));
        OWLObjectProperty hasPulse = df.getOWLObjectProperty(IRI.create(HAS_PULSE));
        OWLObjectProperty hasTongue = df.getOWLObjectProperty(IRI.create(HAS_TONGUE));
        OWLObjectProperty hasAbdominal = df.getOWLObjectProperty(IRI.create(HAS_ABDOMINAL));

        // 类型拷贝的来源是「ABox 查表本体」，不是 gettBoxOntology()（后者已不含 ABox）。
        OntologyModuleUtils.addObjectAssertionsAndCopyTypes(
                aboxLookup(), df, axioms, hasSymptom, patient, input.symptomIris, BASE_NS);
        OntologyModuleUtils.addObjectAssertionsAndCopyTypes(
                aboxLookup(), df, axioms, hasPulse, patient, input.pulseIris, BASE_NS);
        OntologyModuleUtils.addObjectAssertionsAndCopyTypes(
                aboxLookup(), df, axioms, hasTongue, patient, input.tongueIris, BASE_NS);
        OntologyModuleUtils.addObjectAssertionsAndCopyTypes(
                aboxLookup(), df, axioms, hasAbdominal, patient, input.fuzhengIris, BASE_NS);

        OntologyModuleUtils.addSynthesizedComposites(
                df, axioms, patient, input.symptomIris, hasSymptom,
                compositeSymptomMap, BASE_NS, INSTANCE_SUFFIX, log);
        OntologyModuleUtils.addSynthesizedComposites(
                df, axioms, patient, input.pulseIris, hasPulse,
                compositePulseMap, BASE_NS, INSTANCE_SUFFIX, log);
        OntologyModuleUtils.addSynthesizedComposites(
                df, axioms, patient, input.tongueIris, hasTongue,
                null, BASE_NS, INSTANCE_SUFFIX, log);
        OntologyModuleUtils.addSynthesizedComposites(
                df, axioms, patient, input.fuzhengIris, hasAbdominal,
                null, BASE_NS, INSTANCE_SUFFIX, log);

        // 步 6–7 修复：把四诊发现类直接断言到患者自身。
        // addObjectAssertionsAndCopyTypes 只把类型拷到「被指向的个体 obj」，不拷到患者；
        // 而「白名单单症状 / 复合脉 ⊑ 八纲」的传导要求患者自身持有发现类。
        assertFindingTypes(df, axioms, patient, input);

        // 物化：把上一阶段已定的八纲 / 六经固定到患者，供本阶段推理直接使用。
        assertMaterializedTypes(df, axioms, patient, input);

        return axioms;
    }

    /**
     * 把物化的八纲 / 六经断言到患者个体。
     *
     * <p>这是「每步做完后物化」的落地：八纲阶段结束后 {@code input.bagangTypes} 被填充，
     * 六经阶段结束后 {@code input.liujingTypes} 被填充；后续阶段重建上下文时，
     * 这些值作为既定事实进入 ABox，无需重新推导。
     */
    private void assertMaterializedTypes(OWLDataFactory df, Set<OWLAxiom> axioms,
                                         OWLNamedIndividual patient, PatientInput input) {
        int n = 0;
        for (List<String> group : List.of(input.bagangTypes, input.liujingTypes)) {
            for (String f : group) {
                if (f == null || f.isBlank()) continue;
                axioms.add(df.getOWLClassAssertionAxiom(
                        df.getOWLClass(IRI.create(BASE_NS + f)), patient));
                n++;
            }
        }
        if (n > 0) {
            log.info("[物化] 断言到患者: 八纲={} 六经={}",
                    input.bagangTypes, input.liujingTypes);
        }
    }

    /**
     * 把四诊发现类（症状 / 脉象 / 舌象 / 腹证）直接断言到患者个体。
     *
     * <p>发现类由个体 IRI 去实例后缀得到（如 {@code #Ehan_instance → #Ehan}），
     * 与 {@code addObjectAssertionsAndCopyTypes} 拷到 obj 的类一致，
     * 从而使「发现类 {@code ⊑} 八纲」可经患者传导。
     */
    private void assertFindingTypes(OWLDataFactory df, Set<OWLAxiom> axioms,
                                    OWLNamedIndividual patient, PatientInput input) {
        List<List<String>> groups = new ArrayList<>();
        groups.add(input.symptomIris);
        groups.add(input.pulseIris);
        groups.add(input.tongueIris);
        groups.add(input.fuzhengIris);

        int n = 0;
        for (List<String> list : groups) {
            if (list == null) continue;
            for (String iri : list) {
                String f = frag(iri);
                if (f == null || f.isBlank()) continue;
                axioms.add(df.getOWLClassAssertionAxiom(
                        df.getOWLClass(IRI.create(BASE_NS + f)), patient));
                n++;
            }
        }
        log.info("[患者断言] 发现类直接断言到患者 {} 个", n);
    }

    // ============================================================
    // JobWorkers
    // ============================================================

    /**
     * 症状映射：自然语言 → 症状实例个体。
     *
     * <p>输入变量：{@code userInput}（自然语言）、{@code confirmed} / {@code rejected} / {@code extra}
     * （人工确认结果，可选）、{@code mappingRound}（重跑轮次，可选）。
     *
     * <p>输出变量：{@code symptomIris} / {@code pulseIris} / {@code tongueIris} / {@code fuzhengIris}、
     * {@code needsConfirmation}、{@code ambiguousSymptoms}、{@code unmatchedTexts}、
     * {@code mappingDetail}、{@code mappingSummary}、{@code mappingRound}。
     *
     * <p><b>幂等</b>：可被人工确认任务回环重跑，重跑时 confirmed/rejected/extra 生效。
     */
    @JobWorker(type = "symptom-mapping", autoComplete = false)
    public void handleSymptomMapping(final ActivatedJob job, final JobClient client) {
        try {
            if (symptomMappingService == null) {
                throw new IllegalStateException("症状映射服务未就绪（SymptomMappingService 未注入）");
            }
            Map<String, Object> vars = job.getVariablesAsMap();
            String userInput = strOf(vars.get("userInput"));
            List<String> confirmed = ObdaQueryUtils.getList(vars, "confirmed");
            List<String> rejected = ObdaQueryUtils.getList(vars, "rejected");
            List<String> extra = ObdaQueryUtils.getList(vars, "extra");
            int round = intOf(vars.get("mappingRound"), 0);

            if (userInput == null || userInput.isBlank()) {
                userInput = String.join("，", confirmed);
            }

            SymptomMappingService.MappingResult res =
                    symptomMappingService.map(userInput, confirmed, rejected, extra, round);

            Map<String, Object> out = res.toVariables();
            // 本轮结束后轮次 +1，供网关判断回环上限
            out.put("mappingRound", round + 1);
            // 透传人工确认结果，供下一轮复用
            out.put("confirmed", confirmed);
            out.put("rejected", rejected);
            out.put("extra", extra);

            client.newCompleteCommand(job.getKey()).variables(out).send().join();
            log.info("症状映射[第{}轮]: {} | 症状={} 脉象={} 舌象={} 需确认={} 未匹配={}",
                    round, res.summary, res.symptomIris, res.pulseIris, res.tongueIris,
                    res.needsConfirmation, res.unmatched);
        } catch (Exception e) {
            log.error("symptom-mapping 失败", e);
            client.newThrowErrorCommand(job.getKey())
                    .errorCode("SYMPTOM_MAPPING_FAILED")
                    .errorMessage(e.getMessage()).send().join();
        }
    }

    @JobWorker(type = "sizhen-input", autoComplete = false)
    public void handleSizhenInput(final ActivatedJob job, final JobClient client) {
        try {
            Map<String, Object> vars = job.getVariablesAsMap();
            String patientIri = BASE_NS + "Patient_" + job.getKey();
            clearPatientCache(patientIri);

            PatientInput input = new PatientInput(
                    patientIri,
                    ObdaQueryUtils.getList(vars, "symptomIris"),
                    ObdaQueryUtils.getList(vars, "pulseIris"),
                    ObdaQueryUtils.getList(vars, "tongueIris"),
                    ObdaQueryUtils.getList(vars, "fuzhengIris"));

            boolean consistent = withLiujingReasoner(input, MiniContext::isConsistent);
            patientInputs.put(patientIri, input);

            Map<String, Object> out = new LinkedHashMap<>();
            out.put("patientIri", patientIri);
            out.put("recorded", consistent);
            out.put("inconsistent", !consistent);
            client.newCompleteCommand(job.getKey()).variables(out).send().join();
            log.info("四诊录入完成: {} 一致={}", patientIri, consistent);
        } catch (Exception e) {
            log.error("sizhen-input 失败", e);
            client.newThrowErrorCommand(job.getKey())
                    .errorCode("SIZHEN_INPUT_FAILED")
                    .errorMessage(e.getMessage()).send().join();
        }
    }

    @JobWorker(type = "ontology-consistency-check", autoComplete = false)
    public void handleConsistencyCheck(final ActivatedJob job, final JobClient client) {
        try {
            String patientIri = (String) job.getVariablesAsMap().get("patientIri");
            PatientInput input = patientInputs.get(patientIri);
            if (input == null) throw new IllegalStateException("患者输入缓存丢失: " + patientIri);

            boolean consistent = withLiujingReasoner(input, MiniContext::isConsistent);

            Map<String, Object> out = new LinkedHashMap<>();
            out.put("consistent", consistent);
            out.put("unsatisfiableClasses", Collections.emptyList());
            client.newCompleteCommand(job.getKey()).variables(out).send().join();
            log.info("一致性检查: {}", consistent);
        } catch (Exception e) {
            log.error("一致性检查失败", e);
            client.newThrowErrorCommand(job.getKey())
                    .errorCode("CONSISTENCY_FAILED")
                    .errorMessage(e.getMessage()).send().join();
        }
    }

    /**
     * 八纲维度归属（元知识，中医基础理论定义）。
     */
    private String bagangDimensionOf(String fragment) {
        if (fragment == null || fragment.isEmpty()) return null;
        switch (fragment.toLowerCase()) {
            case "biao":
            case "li":
            case "banbiaobanli":
                return "表里";
            case "han":
            case "re":
                return "寒热";
            case "xu":
            case "shi":
                return "虚实";
            case "yin":
            case "yang":
                return "阴阳";
            default:
                return null;
        }
    }

    @JobWorker(type = "bagang-classification", autoComplete = false)
    public void handleBagangClassification(final ActivatedJob job, final JobClient client) {
        try {
            String patientIri = (String) job.getVariablesAsMap().get("patientIri");
            PatientInput input = patientInputs.get(patientIri);
            if (input == null) throw new IllegalStateException("患者输入缓存丢失: " + patientIri);

            Set<OWLClass> patientTypes = withBagangReasoner(
                    input, ctx -> ctx.getTypes(input.patientIri));
            // 严格单向：八纲只由患者推理得出（判据 → 八纲），不再用方证兜底反推。
            Set<OWLClass> allBagang = patientTypes.stream()
                    .filter(bagangSubclasses::contains)
                    .collect(Collectors.toSet());

            log.info("八纲来源: 患者推理={}",
                    allBagang.stream().map(c -> c.getIRI().getFragment())
                            .sorted().collect(Collectors.toList()));

            List<OWLClass> sortedBagang = allBagang.stream()
                    .sorted(Comparator.comparing(c -> c.getIRI().getFragment()))
                    .collect(Collectors.toList());

            List<String> bagangFragments = sortedBagang.stream()
                    .map(c -> c.getIRI().getFragment())
                    .collect(Collectors.toList());
            List<String> bagangTypesCn = sortedBagang.stream()
                    .map(this::resolveClassLabel)
                    .collect(Collectors.toList());

            // 物化八纲：固定到患者输入，并清掉本患者的推理上下文，
            // 使六经阶段重建时带上物化八纲（且可省去判据层 seed）。
            input.bagangTypes.clear();
            input.bagangTypes.addAll(bagangFragments);
            // 物化八纲判据：供六经阶段消解「病位×病性」交叉积（判据绑定病位与病性）。
            input.panjuTypes.clear();
            input.panjuTypes.addAll(patientTypes.stream()
                    .filter(bagangPanjuSubclasses::contains)
                    .map(c -> c.getIRI().getFragment())
                    .sorted()
                    .collect(Collectors.toList()));
            log.info("[物化] 八纲判据: {}", input.panjuTypes);
            miniCtxMgr.clearByPrefix(input.patientIri);
            log.info("[物化] 八纲已固定: {}", bagangFragments);

            Map<String, List<String>> grouped = new LinkedHashMap<>();
            grouped.put("表里", new ArrayList<>());
            grouped.put("寒热", new ArrayList<>());
            grouped.put("虚实", new ArrayList<>());
            grouped.put("阴阳", new ArrayList<>());

            for (OWLClass c : sortedBagang) {
                String frag = c.getIRI().getFragment();
                String dim = bagangDimensionOf(frag);
                if (dim != null) {
                    grouped.get(dim).add(resolveClassLabel(c));
                } else {
                    log.warn("[八纲分组] 未知维度 fragment: {}", frag);
                }
            }

            List<String> biaoli = grouped.get("表里");
            List<String> hanre = grouped.get("寒热");
            List<String> xushi = grouped.get("虚实");
            List<String> yinyang = grouped.get("阴阳");

            // 阴阳消解：阴阳双现时按医理裁定（见 resolveYinYang 注释）。
            boolean shaoyinOutline = patientTypes.stream()
                    .anyMatch(c -> "Panju_E1".equals(c.getIRI().getFragment()));
            List<String> yinyangResolved =
                    resolveYinYang(biaoli, hanre, xushi, yinyang, shaoyinOutline);
            if (!yinyangResolved.equals(yinyang)) {
                log.info("[阴阳消解] 原={} → 裁定={}（表里={}, 寒热={}, 虚实={}, 少阴提纲={}）",
                        yinyang, yinyangResolved, biaoli, hanre, xushi, shaoyinOutline);
                yinyang = yinyangResolved;
            }

            Map<String, Object> bagangResult = new LinkedHashMap<>();
            bagangResult.put("表里", biaoli);
            bagangResult.put("寒热", hanre);
            bagangResult.put("虚实", xushi);
            bagangResult.put("阴阳", yinyang);
            bagangResult.put("bagangTypes", bagangFragments);
            bagangResult.put("bagangTypesCn", bagangTypesCn);
            bagangResult.put("complete",
                    !biaoli.isEmpty() && !hanre.isEmpty()
                            && !xushi.isEmpty() && !yinyang.isEmpty());

            client.newCompleteCommand(job.getKey())
                    .variables(Map.of("bagangResult", bagangResult)).send().join();
            log.info("八纲完成: {}", bagangResult);
        } catch (Exception e) {
            log.error("八纲失败", e);
            client.newThrowErrorCommand(job.getKey())
                    .errorCode("BAGANG_FAILED")
                    .errorMessage(e.getMessage()).send().join();
        }
    }

    @JobWorker(type = "liujing-classification", autoComplete = false)
    public void handleLiujingClassification(final ActivatedJob job, final JobClient client) {
        try {
            String patientIri = (String) job.getVariablesAsMap().get("patientIri");
            PatientInput input = patientInputs.get(patientIri);
            if (input == null) throw new IllegalStateException("患者输入缓存丢失: " + patientIri);

            // 严格单向：六经只由患者推理得出（物化八纲 → 六经 ≡ 病位 ⊓ 病性），
            // 不再用方证反推六经。
            Set<String> fromPatient = withLiujingReasoner(input, ctx ->
                    ctx.getTypes(input.patientIri).stream()
                            .filter(c -> SIX_CHANNEL_WHITELIST.contains(c.getIRI().getFragment()))
                            .map(c -> c.getIRI().getFragment())
                            .collect(Collectors.toSet()));

            List<String> mergedLiujing = fromPatient.stream()
                    .sorted().collect(Collectors.toList());

            log.info("六经来源: 患者推理={}（物化八纲={}）", fromPatient, input.bagangTypes);

            // 交叉积消解：病位×病性自由组合会推出冗余六经，按判据绑定重新配对。
            List<String> paired = resolveLiujingCrossProduct(mergedLiujing, input);

            List<String> liujingTypes = resolveLiujingMutex(paired);

            // 物化六经：固定到患者输入，并清掉推理上下文，
            // 使方证阶段重建时带上物化的八纲 + 六经。
            input.liujingTypes.clear();
            input.liujingTypes.addAll(liujingTypes);
            miniCtxMgr.clearByPrefix(input.patientIri);
            log.info("[物化] 六经已固定: {}", liujingTypes);

            String sixChannel;
            String sixChannelCn;
            String combinedDiseaseMark = null;
            boolean isCombined = liujingTypes.size() > 1;
            if (liujingTypes.isEmpty()) {
                sixChannel = null;
                sixChannelCn = "六经难定";
            } else if (liujingTypes.size() == 1) {
                sixChannel = liujingTypes.get(0);
                sixChannelCn = backendService.resolveLabel(sixChannel, BASE_NS);
            } else {
                combinedDiseaseMark = buildCombinedDiseaseMark(liujingTypes);
                sixChannel = combinedDiseaseMark;
                sixChannelCn = combinedDiseaseMark;
            }

            Map<String, Object> out = new LinkedHashMap<>();
            out.put("sixChannel", sixChannel);
            out.put("sixChannelCn", sixChannelCn);
            out.put("liujingTypes", liujingTypes);
            out.put("liujingTypesCn", backendService.resolveLabels(liujingTypes, BASE_NS));
            out.put("isCombinedChannel", isCombined);
            out.put("combinedDiseaseMark", combinedDiseaseMark);
            client.newCompleteCommand(job.getKey()).variables(out).send().join();
            log.info("六经完成: {} 合病={} 标记={}", liujingTypes, isCombined, combinedDiseaseMark);
        } catch (Exception e) {
            log.error("六经失败", e);
            client.newThrowErrorCommand(job.getKey())
                    .errorCode("LIUJING_FAILED")
                    .errorMessage(e.getMessage()).send().join();
        }
    }

    /**
     * 阴阳消解（胡希恕《六经八纲》）。
     *
     * <p>阴阳为八纲之总纲，本应由病性（寒热虚实）决定：热/实属阳，寒/虚属阴。
     * 但「表证」的阴阳另有规定 —— 表阳证为太阳病，表阴证为少阴病，
     * 二者以 <b>281 条少阴提纲「脉微细，但欲寐」</b>（{@code Panju_E1}）为别，
     * <b>不以虚实论</b>：太阳中风（桂枝汤证）虽属表虚（脉浮缓），仍为表阳证（太阳病）；
     * 太阳伤寒（麻黄汤证）虽见恶寒，亦为表阳证。
     *
     * <p>故当「阳证」与「阴证」同时出现时，按下列优先级裁定：
     * <ol>
     *   <li><b>纯表证</b>（表里仅「表证」）：{@code Panju_E1} 成立 → 阴（少阴）；
     *       否则 → 阳（太阳）。</li>
     *   <li><b>其余</b>（含里证或半表半里）：热证 → 阳；寒证 → 阴；
     *       实证 → 阳；虚证 → 阴；皆无 → 阳。</li>
     * </ol>
     * 阴阳未双现时原样返回，不做任何改动。
     */
    private List<String> resolveYinYang(List<String> biaoli, List<String> hanre,
                                        List<String> xushi, List<String> yinyang,
                                        boolean shaoyinOutline) {
        if (yinyang.size() < 2
                || !yinyang.contains("阳证") || !yinyang.contains("阴证")) {
            return yinyang;
        }

        boolean keepYang;
        if (biaoli.size() == 1 && biaoli.contains("表证")) {
            // 纯表证：以少阴提纲（281 条）别太阳（阳）/少阴（阴）
            keepYang = !shaoyinOutline;
        } else if (hanre.contains("热证")) {
            keepYang = true;
        } else if (hanre.contains("寒证")) {
            keepYang = false;
        } else if (xushi.contains("实证")) {
            keepYang = true;
        } else if (xushi.contains("虚证")) {
            keepYang = false;
        } else {
            keepYang = true;
        }
        return keepYang ? List.of("阳证") : List.of("阴证");
    }

    /**
     * 六经消解 —— 现已<b>停用</b>（{@link #LIUJING_MUTEX_PAIRS} 为空表）。
     *
     * <p>保留方法骨架是为了让「曾经存在互斥规则」这件事在代码里可见；
     * 当前实现恒等于恒等函数（原样返回），六经同现即报合病。
     */
    private List<String> resolveLiujingMutex(List<String> liujingTypes) {
        if (LIUJING_MUTEX_PAIRS.isEmpty()) return liujingTypes;
        Set<String> set = new HashSet<>(liujingTypes);
        boolean changed = false;

        for (Map.Entry<String, String> entry : LIUJING_MUTEX_PAIRS.entrySet()) {
            String yang = entry.getKey();
            String yin = entry.getValue();
            if (set.contains(yang) && set.contains(yin)) {
                set.remove(yang);
                changed = true;
                log.info("[六经消解] 半表半里互斥（阴覆盖阳）：移除 {}，保留 {}", yang, yin);
            }
        }
        if (!changed) return liujingTypes;
        return set.stream().sorted().collect(Collectors.toList());
    }

    private void buildFangzhengLiujingMap() {
        OWLOntology tbox = backendService.getOntologyService().gettBoxOntology();
        Map<OWLClass, Set<OWLClass>> map = new HashMap<>();

        for (OWLClass fz : fangzhengSubclasses) {
            Set<OWLClass> ljs = OntologyModuleUtils.findRelatedClasses(
                    tbox, fz, liujingSubclasses);
            if (!ljs.isEmpty()) map.put(fz, ljs);
        }
        fangzhengLiujingMap = map;
        log.info("[init] 方证-六经缓存: {} 个方证有六经归属", map.size());
    }

    /**
     * 构建「方证 → 八纲属性」缓存（读 bagangAttr 注解）。
     *
     * <p>bagangAttr 是本体对每个方证的八纲归属标注（表/里/半表半里、阴/阳、寒/热、虚/实），
     * 依《伤寒论》六经八纲辨证体系。用于「方证相应」判定：方证病位须覆盖患者病位
     * （合病须合治，不可漏一经），见 {@link #compareBingweiCoverage}。
     */
    private void buildFangzhengBagangMap() {
        OWLOntology tbox = backendService.getOntologyService().gettBoxOntology();
        Map<OWLClass, Set<String>> map = new HashMap<>();

        for (OWLClass fz : fangzhengSubclasses) {
            Set<String> attrs = new HashSet<>();
            for (OWLAnnotationAssertionAxiom ax :
                    tbox.annotationAssertionAxioms(fz.getIRI()).collect(Collectors.toList())) {
                if (!ax.getProperty().getIRI().getFragment().equals("bagangAttr")) continue;
                OWLAnnotationValue v = ax.getValue();
                if (v instanceof IRI iri) {
                    attrs.add(iri.getFragment());
                }
            }
            if (!attrs.isEmpty()) map.put(fz, attrs);
        }
        fangzhengBagangMap = map;
    }

    // ============================================================
    // 六经交叉积消解（病位-病性配对）
    // ============================================================

    /** 病位 fragment（表里维度） */
    private static final Set<String> BINGWEI_FRAGMENTS =
            Set.of("Biao", "Li", "Banbiaobanli");

    /** 病性 fragment（阴阳维度） */
    private static final Set<String> BINGXING_FRAGMENTS =
            Set.of("Yang", "Yin");

    /**
     * 构建「判据 → 病位/病性」绑定缓存。
     *
     * <p>八纲判据类同时 ⊑ 病位与 ⊑ 病性者（如 {@code Panju_C6 ⊑ Banbiaobanli ⊓ Yang}），
     * 即把该病位与该病性锁定为一经。六经交叉积消解据此配对。
     */
    private void buildPanjuBindingMap() {
        OWLOntology tbox = backendService.getOntologyService().gettBoxOntology();
        Map<String, Set<String>> bingwei = new HashMap<>();
        Map<String, Set<String>> bingxing = new HashMap<>();

        for (OWLClass pj : bagangPanjuSubclasses) {
            String frag = pj.getIRI().getFragment();
            Set<String> bw = new HashSet<>();
            Set<String> bx = new HashSet<>();
            for (OWLSubClassOfAxiom ax : tbox.subClassAxiomsForSubClass(pj)
                    .collect(Collectors.toList())) {
                OWLClassExpression sc = ax.getSuperClass();
                if (!sc.isOWLClass()) continue;
                String sf = sc.asOWLClass().getIRI().getFragment();
                if (BINGWEI_FRAGMENTS.contains(sf)) bw.add(sf);
                if (BINGXING_FRAGMENTS.contains(sf)) bx.add(sf);
            }
            if (!bw.isEmpty()) bingwei.put(frag, bw);
            if (!bx.isEmpty()) bingxing.put(frag, bx);
        }
        panjuBingweiMap = bingwei;
        panjuBingxingMap = bingxing;
    }

    /**
     * 六经交叉积消解（病位-病性配对）。
     *
     * <p><b>问题</b>：六经 ≡ 病位 ⊓ 病性（太阳=表⊓阳、阳明=里⊓阳、少阳=半表半里⊓阳、
     * 太阴=里⊓阴、少阴=表⊓阴、厥阴=半表半里⊓阴）。本体未声明病位之间、病性之间互斥，
     * 故当患者同时具备 ≥2 个病位与 ≥2 个病性时，推理机会推出「病位×病性」的交叉积。
     * 例：柴胡桂枝干姜汤证（147 条「胸胁满微结，小便不利，渴而不呕，但头汗出，
     * 往来寒热，心烦」）患者八纲 = 半表半里+里+虚+阳+阴，交叉积推出
     * 少阳/厥阴/阳明/太阴四经，而实际当为「少阳太阴合病」。
     *
     * <p><b>医理依据</b>：胡希恕《六经八纲》——病性与病位并非自由组合，而由「判据」绑定。
     * 判据类同时 ⊑ 病位与 ⊑ 病性者即锁定该组合：
     * {@code Panju_C6}（往来寒热+胸胁苦满）⊑ 半表半里 ⊓ 阳 → 半表半里配阳 = 少阳；
     * {@code Panju_B8}（腹满+虚寒脉）⊑ 里（里虚寒）→ 里配阴 = 太阴。
     *
     * <p><b>算法</b>（仅当推理机推出的六经恰为「病位 × 病性」的<b>完全交叉积</b>时启用；
     * 单经、正常合病、以及三阳合病等「部分交叉」均原样返回）：
     * <ol>
     *   <li>对每个病位 p：若患者命中的某判据 J ⊑ p 且 J ⊑ 某病性 s，则 p↔s；</li>
     *   <li>未被判据绑定的病位，与未被占用的病性配对（按病位、病性排序稳定分配）；</li>
     *   <li>病位+病性 → 六经，仅保留推理机已推出的六经。</li>
     * </ol>
     */
    private List<String> resolveLiujingCrossProduct(List<String> liujingFromReasoner,
                                                    PatientInput input) {
        if (liujingFromReasoner == null || liujingFromReasoner.size() <= 2) {
            return liujingFromReasoner;   // 单经 / 正常合病：非交叉积，不动
        }

        List<String> bingwei = input.bagangTypes.stream()
                .filter(BINGWEI_FRAGMENTS::contains).distinct().sorted()
                .collect(Collectors.toList());
        List<String> bingxing = input.bagangTypes.stream()
                .filter(BINGXING_FRAGMENTS::contains).distinct().sorted()
                .collect(Collectors.toList());
        if (bingwei.size() < 2 || bingxing.size() < 2) {
            return liujingFromReasoner;   // 病位或病性单一：非交叉积
        }

        // 仅当推出的六经恰为「病位 × 病性」的全部组合时，才判定为交叉积伪影。
        // 例：三阳合病（病位=表+里+半表半里，病性=阳）推出太阳/阳明/少阳，是正常合病而非交叉积。
        Set<String> crossProduct = new HashSet<>();
        for (String p : bingwei) {
            for (String s : bingxing) {
                String lj = liujingOfBingweiBingxing(p, s);
                if (lj != null) crossProduct.add(lj);
            }
        }
        if (!new HashSet<>(liujingFromReasoner).equals(crossProduct)) {
            return liujingFromReasoner;   // 非完全交叉积：不动
        }

        // 1) 判据绑定：病位 → 病性
        Map<String, String> bound = new LinkedHashMap<>();
        Set<String> used = new HashSet<>();
        for (String p : bingwei) {
            for (String j : input.panjuTypes) {
                if (!panjuBingweiMap.getOrDefault(j, Collections.emptySet()).contains(p)) continue;
                for (String s : panjuBingxingMap.getOrDefault(j, Collections.emptySet())) {
                    if (bingxing.contains(s) && !used.contains(s)) {
                        bound.put(p, s);
                        used.add(s);
                        break;
                    }
                }
                if (bound.containsKey(p)) break;
            }
        }
        // 2) 未绑定病位 → 剩余病性
        List<String> rest = bingxing.stream().filter(s -> !used.contains(s))
                .collect(Collectors.toList());
        int ri = 0;
        for (String p : bingwei) {
            if (!bound.containsKey(p) && ri < rest.size()) bound.put(p, rest.get(ri++));
        }

        // 3) 病位+病性 → 六经
        List<String> out = new ArrayList<>();
        for (String p : bingwei) {
            String lj = liujingOfBingweiBingxing(p, bound.get(p));
            if (lj != null && liujingFromReasoner.contains(lj) && !out.contains(lj)) {
                out.add(lj);
            }
        }
        if (out.isEmpty() || out.size() == liujingFromReasoner.size()) {
            return liujingFromReasoner;
        }
        out.sort(Comparator.naturalOrder());
        log.info("[六经交叉积消解] 病位={} 病性={} 判据={} → 配对={} 六经 {} → {}",
                bingwei, bingxing, input.panjuTypes, bound, liujingFromReasoner, out);
        return out;
    }

    /** 病位 + 病性 → 六经 fragment。 */
    private String liujingOfBingweiBingxing(String bingwei, String bingxing) {
        if (bingwei == null || bingxing == null) return null;
        return switch (bingwei + "|" + bingxing) {
            case "Biao|Yang" -> "Taiyangbing";
            case "Li|Yang" -> "Yangmingbing";
            case "Banbiaobanli|Yang" -> "Shaoyangbing";
            case "Li|Yin" -> "Taiyinbing";
            case "Biao|Yin" -> "Shaoyinbing";
            case "Banbiaobanli|Yin" -> "Jueyinbing";
            default -> null;
        };
    }

    private String buildCombinedDiseaseMark(List<String> liujingTypes) {
        if (liujingTypes == null || liujingTypes.size() < 2) return null;
        if (liujingTypes.size() == 3 && liujingTypes.containsAll(
                List.of("Taiyangbing", "Yangmingbing", "Shaoyangbing"))) return "三阳合病";
        if (liujingTypes.size() == 2 && liujingTypes.containsAll(
                List.of("Taiyangbing", "Shaoyinbing"))) return "太少两感";
        if (liujingTypes.size() == 2 && liujingTypes.containsAll(
                List.of("Taiyangbing", "Yangmingbing"))) return "太阳阳明合病";
        if (liujingTypes.size() == 2 && liujingTypes.containsAll(
                List.of("Taiyangbing", "Shaoyangbing"))) return "太阳少阳合病";
        if (liujingTypes.size() == 2 && liujingTypes.containsAll(
                List.of("Taiyangbing", "Taiyinbing"))) return "太阳太阴合病";
        if (liujingTypes.size() == 2 && liujingTypes.containsAll(
                List.of("Taiyangbing", "Jueyinbing"))) return "太阳厥阴合病";
        if (liujingTypes.size() == 2 && liujingTypes.containsAll(
                List.of("Shaoyangbing", "Yangmingbing"))) return "少阳阳明合病";
        if (liujingTypes.size() == 2 && liujingTypes.containsAll(
                List.of("Shaoyangbing", "Taiyinbing"))) return "少阳太阴合病";
        if (liujingTypes.size() == 2 && liujingTypes.containsAll(
                List.of("Shaoyangbing", "Shaoyinbing"))) return "少阳少阴合病";
        if (liujingTypes.size() == 2 && liujingTypes.containsAll(
                List.of("Shaoyangbing", "Jueyinbing"))) return "少阳厥阴合病";
        if (liujingTypes.size() == 2 && liujingTypes.containsAll(
                List.of("Yangmingbing", "Taiyinbing"))) return "阳明太阴合病";
        if (liujingTypes.size() == 2 && liujingTypes.containsAll(
                List.of("Yangmingbing", "Shaoyinbing"))) return "阳明少阴合病";
        if (liujingTypes.size() == 2 && liujingTypes.containsAll(
                List.of("Yangmingbing", "Jueyinbing"))) return "阳明厥阴合病";
        if (liujingTypes.size() == 2 && liujingTypes.containsAll(
                List.of("Taiyinbing", "Shaoyinbing"))) return "太阴少阴合病";
        if (liujingTypes.size() == 2 && liujingTypes.containsAll(
                List.of("Taiyinbing", "Jueyinbing"))) return "太阴厥阴合病";
        if (liujingTypes.size() == 2 && liujingTypes.containsAll(
                List.of("Shaoyinbing", "Jueyinbing"))) return "少阴厥阴合病";

        Map<String, Integer> orderMap = new LinkedHashMap<>();
        orderMap.put("Taiyangbing", 0);
        orderMap.put("Yangmingbing", 1);
        orderMap.put("Shaoyangbing", 2);
        orderMap.put("Taiyinbing", 3);
        orderMap.put("Shaoyinbing", 4);
        orderMap.put("Jueyinbing", 5);
        Map<String, String> nameMap = new LinkedHashMap<>();
        nameMap.put("Taiyangbing", "太阳");
        nameMap.put("Yangmingbing", "阳明");
        nameMap.put("Shaoyangbing", "少阳");
        nameMap.put("Taiyinbing", "太阴");
        nameMap.put("Shaoyinbing", "少阴");
        nameMap.put("Jueyinbing", "厥阴");

        List<String> sorted = new ArrayList<>(liujingTypes);
        sorted.sort(Comparator.comparingInt(t -> orderMap.getOrDefault(t, Integer.MAX_VALUE)));
        StringBuilder sb = new StringBuilder();
        for (String t : sorted) sb.append(nameMap.getOrDefault(t, t));
        sb.append("合病");
        return sb.toString();
    }

    @JobWorker(type = "fangzheng-classification", autoComplete = false)
    public void handleFangzhengClassification(final ActivatedJob job, final JobClient client) {
        try {
            String patientIri = (String) job.getVariablesAsMap().get("patientIri");
            PatientInput input = patientInputs.get(patientIri);
            if (input == null) throw new IllegalStateException("患者输入缓存丢失: " + patientIri);

            Set<OWLClass> stage1Types = withLiujingReasoner(
                    input, ctx -> ctx.getTypes(input.patientIri));
            List<String> realizedMatches = withFangzhengReasoner(input, stage1Types, ctx ->
                    OntologyModuleUtils.extractFragmentsByMetaClass(
                            ctx.getTypes(input.patientIri), fangzhengSubclasses));
            Set<String> patientFrags = collectPatientFrags(input);

            Set<OWLClass> realizedClasses = realizedMatches.stream()
                    .map(f -> tboxDf.getOWLClass(IRI.create(BASE_NS + f)))
                    .collect(Collectors.toSet());
            Set<OWLClass> cachedCandidates = fangzhengCandidatesCache
                    .getOrDefault(patientIri, Collections.emptySet());
            Set<OWLClass> displaySet = new HashSet<>(realizedClasses);
            displaySet.addAll(cachedCandidates);

            // 患者已定六经（六经阶段物化结果）与八纲，用于「方证相应」裁决。
            Set<OWLClass> patientLiujing = input.liujingTypes.stream()
                    .map(f -> tboxDf.getOWLClass(IRI.create(BASE_NS + f)))
                    .collect(Collectors.toSet());
            Set<String> patientBagang = new HashSet<>(input.bagangTypes);

            List<ScoredFangzheng> displayScored = rankCandidatesWithScores(
                    displaySet, patientFrags, CANDIDATE_DISPLAY_TOP_N, realizedClasses,
                    patientLiujing, patientBagang);

            if (!realizedMatches.isEmpty()) {
                log.info("[阶段2] realize 命中 {} 个，候选 Top{} 打分:",
                        realizedMatches.size(), displayScored.size());
            } else {
                log.warn("[阶段2] realize 无匹配，从候选 {} 个中排序输出 Top{}:",
                        cachedCandidates.size(), displayScored.size());
            }
            for (int i = 0; i < displayScored.size(); i++) {
                ScoredFangzheng s = displayScored.get(i);
                boolean isHit = realizedClasses.contains(s.cls);
                log.info("  #{} {} {}", i + 1, s, isHit ? "[realize命中]" : "[仅候选]");
            }

            String fangzheng;
            List<String> topCandidates = new ArrayList<>();
            List<String> candidateScores = new ArrayList<>();

            if (!realizedMatches.isEmpty()) {
                List<ScoredFangzheng> realizedScored = rankCandidatesWithScores(
                        realizedClasses, patientFrags, CANDIDATE_DISPLAY_TOP_N, realizedClasses,
                        patientLiujing, patientBagang);
                fangzheng = realizedScored.isEmpty()
                        ? realizedMatches.get(0)
                        : realizedScored.get(0).fragment();
                log.info("[阶段2] 命中路径 Top1={}（hits={}）",
                        fangzheng, realizedScored.isEmpty() ? "?" : realizedScored.get(0));
                topCandidates.add(fangzheng);
                ScoredFangzheng firstScore = displayScored.stream()
                        .filter(s -> s.fragment().equals(fangzheng))
                        .findFirst()
                        .orElse(null);
                candidateScores.add(firstScore != null ? firstScore.toString() : fangzheng);
            } else {
                // 铁律 16：realize 零命中时，任何「仅候选」都不得冒充结论。
                // 此前此处把 displayScored.get(0) 写进 fangzheng，直接产生「百合洗方证」这类错误输出。
                fangzheng = "方证未定";
                log.warn("[阶段2] realize 无匹配 → 方证未定；候选 {} 个仅供临床参考（不写入结论）",
                        displayScored.size());
            }

            for (ScoredFangzheng s : displayScored) {
                String f = s.fragment();
                if (!f.equals(fangzheng)
                        && topCandidates.size() < CANDIDATE_DISPLAY_TOP_N) {
                    topCandidates.add(f);
                    candidateScores.add(s.toString());
                }
            }

            log.info("方证完成: {} 匹配数={} 候选Top{}={}",
                    fangzheng, realizedMatches.size(), topCandidates.size(), topCandidates);
            log.info("方证打分: {}", candidateScores);

            // ---- 结果形态与双路径（无确定结论时）----
            String outcome = realizedMatches.isEmpty() ? "NO_MAIN_MATCH" : "CONFIRMED";
            Map<String, Object> pathA = null;
            Map<String, Object> pathB = null;
            if (realizedMatches.isEmpty()) {
                Map<String, Object> paths = analyzeGaps(
                        patientFrags, patientLiujing, patientBagang, displayScored);
                pathA = asMap(paths.get("pathA"));
                pathB = asMap(paths.get("pathB"));
            }

            Map<String, Object> out = new LinkedHashMap<>();
            out.put("fangzheng", fangzheng);
            out.put("fangzhengCn", backendService.resolveLabel(fangzheng, BASE_NS));
            out.put("fangzhengTypes", topCandidates);
            out.put("candidateFangzhengs", topCandidates);
            out.put("candidateFangzhengsCn",
                    backendService.resolveLabels(topCandidates, BASE_NS));
            out.put("candidateScores", candidateScores);
            out.put("outcome", outcome);
            if (pathA != null) out.put("pathA", pathA);
            if (pathB != null) out.put("pathB", pathB);
            // ---- 供 Gateway_HasRecommendation 判定「结果形态」----
            // fangzhengRealized：方证是否被推理机「完全命中」(realize)。
            // fangzhengCandidates：可展示的候选方证列表（realize 命中 ∪ 阶段1候选，按打分排序）。
            //   · realized=true                          → 走「完全命中」分支 (EndEvent_Success)
            //   · realized=false 且 candidates 非空       → 走「仅有候选」分支 (EndEvent_Candidates)
            //   · realized=false 且 candidates 为空       → 走「无候选」分支 (EndEvent_NoResult)
            // 注意：这两个变量此前从未被任何 Worker 输出，导致网关三条 FEEL 条件全部为 false，
            // Camunda 抛 "Expected at least one condition to evaluate to true, or to have a default flow"。
            out.put("fangzhengRealized", !realizedMatches.isEmpty());
            out.put("fangzhengCandidates", displayScored.stream()
                    .map(ScoredFangzheng::fragment)
                    .collect(Collectors.toList()));
            client.newCompleteCommand(job.getKey()).variables(out).send().join();
        } catch (Exception e) {
            log.error("方证失败", e);
            client.newThrowErrorCommand(job.getKey())
                    .errorCode("FANGZHENG_FAILED")
                    .errorMessage(e.getMessage()).send().join();
        }
    }

    // ============================================================
    // 双路径分析（无确定结论时）
    //   路A：追问（判据缺口 → 定八纲/六经；方证主证缺口 → 定方证）
    //   路B：或然症候选（evidence = POSS_ONLY，永不写入结论）
    // 全程只用结构化 gap 与整数计数，无权重常数。
    // ============================================================

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object o) {
        return (o instanceof Map) ? (Map<String, Object>) o : null;
    }

    private static List<String> asStringList(Object o) {
        List<String> out = new ArrayList<>();
        if (o instanceof List<?> l) {
            for (Object e : l) if (e != null) out.add(String.valueOf(e));
        } else if (o != null) {
            out.add(String.valueOf(o));
        }
        return out;
    }

    /** 读取某类的注解字面量（按属性 fragment 匹配）；无则返回 null。 */
    private String annotationLiteral(OWLClass cls, String propFragment) {
        OWLOntology tbox = backendService.getOntologyService().gettBoxOntology();
        for (OWLAnnotationAssertionAxiom ax :
                tbox.annotationAssertionAxioms(cls.getIRI()).collect(Collectors.toList())) {
            if (!ax.getProperty().getIRI().getFragment().equals(propFragment)) continue;
            OWLAnnotationValue v = ax.getValue();
            if (v instanceof OWLLiteral lit) return lit.getLiteral();
        }
        return null;
    }

    /**
     * 无确定结论时生成双路径。
     *
     * @param patientFrags   患者四诊 fragment（含复合展开）
     * @param patientLiujing 患者已定六经类
     * @param patientBagang  患者已定八纲 fragment
     * @param displayScored  已排序的候选（MAIN 在前，POSS_ONLY 在后）
     */
    private Map<String, Object> analyzeGaps(Set<String> patientFrags, Set<OWLClass> patientLiujing,
                                            Set<String> patientBagang,
                                            List<ScoredFangzheng> displayScored) {
        OWLOntology tbox = backendService.getOntologyService().gettBoxOntology();
        Set<String> satisfied = new HashSet<>(patientFrags);
        if (patientLiujing != null) {
            for (OWLClass c : patientLiujing) satisfied.add(c.getIRI().getFragment());
        }
        if (patientBagang != null) satisfied.addAll(patientBagang);

        // ---------- 路A：追问 ----------
        // 以「缺口症状集合」为键合并两层问题（同一症状可能既能定经、又能定方证）
        LinkedHashMap<String, Map<String, Object>> merged = new LinkedHashMap<>();

        Set<OWLClass> bagangUniverse = new HashSet<>();
        if (bagangSubclasses != null) bagangUniverse.addAll(bagangSubclasses);
        if (liujingSubclasses != null) bagangUniverse.addAll(liujingSubclasses);

        // 八纲 / 六经是「推理所得」，不是可追问的四诊发现。
        // 方证定义里常含六经项（如 Taiyinbing ≡ Li ⊓ Yin），definitionGapLeaves 会递归
        // 展开成 八纲 类（Li/Yin/Banbiaobanli/Yang…）当叶子，从而问出
        // 「是否有 半表半里、阴证？」这类患者无法回答的问题。故追问清单必须剔除这些抽象类；
        // 它们对应的「如何定六经」由第一层判据缺口（Panju_* 的症状叶子）负责追问。
        Set<String> abstractFrags = new HashSet<>();
        if (bagangSubclasses != null) {
            for (OWLClass c : bagangSubclasses) abstractFrags.add(c.getIRI().getFragment());
        }
        if (liujingSubclasses != null) {
            for (OWLClass c : liujingSubclasses) abstractFrags.add(c.getIRI().getFragment());
        }

        // 第一层：判据缺口（定八纲/六经）
        if (bagangPanjuSubclasses != null) {
            for (OWLClass p : bagangPanjuSubclasses) {
                int gap = OntologyModuleUtils.definitionGap(tbox, p, satisfied, FANGZHENG_PROP_IRIS);
                if (gap <= 0 || gap > GAP_MAX) continue;
                List<OWLClass> leaves = OntologyModuleUtils.definitionGapLeaves(
                        tbox, p, satisfied, FANGZHENG_PROP_IRIS);
                leaves.removeIf(c -> abstractFrags.contains(c.getIRI().getFragment()));
                if (leaves.isEmpty()) continue;
                List<String> frags = leaves.stream()
                        .map(c -> c.getIRI().getFragment()).collect(Collectors.toList());
                String key = String.join(",", frags);
                Map<String, Object> q = merged.get(key);
                if (q == null) {
                    List<String> cn = backendService.resolveLabels(frags, BASE_NS);
                    q = new LinkedHashMap<>();
                    q.put("ask", "是否有 " + String.join("、", cn) + "？");
                    q.put("symptoms", frags);
                    q.put("symptomsCn", cn);
                    q.put("gap", gap);
                    q.put("basis", annotationLiteral(p, "comment"));
                    q.put("kind", "PANJU");
                    merged.put(key, q);
                }
                // 该判据 ⊑ 的目标八纲（进而可定六经）
                Set<OWLClass> targets = OntologyModuleUtils.findRelatedClasses(tbox, p, bagangUniverse);
                List<String> tFrags = targets.stream()
                        .map(c -> c.getIRI().getFragment()).sorted().collect(Collectors.toList());
                if (!tFrags.isEmpty()) {
                    q.put("unlocks", tFrags);
                    q.put("unlocksCn", backendService.resolveLabels(tFrags, BASE_NS));
                    Set<String> plus = new HashSet<>(satisfied);
                    plus.addAll(tFrags);
                    List<String> lj = new ArrayList<>();
                    if (liujingSubclasses != null) {
                        for (OWLClass l : liujingSubclasses) {
                            if (OntologyModuleUtils.definitionGap(
                                    tbox, l, plus, FANGZHENG_PROP_IRIS) == 0) {
                                lj.add(l.getIRI().getFragment());
                            }
                        }
                    }
                    if (!lj.isEmpty()) {
                        q.put("unlocksLiujing", lj);
                        q.put("unlocksLiujingCn", backendService.resolveLabels(lj, BASE_NS));
                    }
                }
            }
        }

        // 第二层：方证主证缺口（定方证）
        int k = 0;
        for (ScoredFangzheng s : displayScored) {
            if (k++ >= CANDIDATE_DISPLAY_TOP_N) break;
            if (s.gap <= 0 || s.gap > GAP_MAX) continue;
            List<OWLClass> leaves = OntologyModuleUtils.definitionGapLeaves(
                    tbox, s.cls, satisfied, FANGZHENG_PROP_IRIS);
            leaves.removeIf(c -> abstractFrags.contains(c.getIRI().getFragment()));
            if (leaves.isEmpty()) continue;
            List<String> frags = leaves.stream()
                    .map(c -> c.getIRI().getFragment()).collect(Collectors.toList());
            String key = String.join(",", frags);
            Map<String, Object> q = merged.get(key);
            if (q == null) {
                List<String> cn = backendService.resolveLabels(frags, BASE_NS);
                q = new LinkedHashMap<>();
                q.put("ask", "是否有 " + String.join("、", cn) + "？");
                q.put("symptoms", frags);
                q.put("symptomsCn", cn);
                q.put("gap", s.gap);
                q.put("basis", annotationLiteral(s.cls, "differentialAxis"));
                q.put("kind", "FANGZHENG");
                merged.put(key, q);
            }
            List<String> then = asStringList(q.get("thenFangzheng"));
            if (!then.contains(s.fragment())) {
                then.add(s.fragment());
                q.put("thenFangzheng", then);
                q.put("thenFangzhengCn", backendService.resolveLabels(then, BASE_NS));
            }
        }

        List<Map<String, Object>> questions = new ArrayList<>(merged.values());
        // 排序：能一举命中方证者优先 → 缺口小者优先 → 问题短者优先（全为整数，无权重）
        questions.sort((a, b) -> {
            boolean ta = a.containsKey("thenFangzheng");
            boolean tb = b.containsKey("thenFangzheng");
            if (ta != tb) return ta ? -1 : 1;
            int c = Integer.compare((int) a.get("gap"), (int) b.get("gap"));
            if (c != 0) return c;
            return Integer.compare(((List<?>) a.get("symptoms")).size(),
                                   ((List<?>) b.get("symptoms")).size());
        });

        // ---------- 路B：或然症候选 ----------
        List<Map<String, Object>> possCands = new ArrayList<>();
        for (ScoredFangzheng s : displayScored) {
            if (s.possHits <= 0) continue;
            List<String> hit = new ArrayList<>();
            for (OWLClass p : fangzhengPossibleMap.getOrDefault(s.cls, Collections.emptySet())) {
                String f = p.getIRI().getFragment();
                if (patientFrags.contains(f)) hit.add(f);
            }
            Map<String, Object> c = new LinkedHashMap<>();
            c.put("name", s.fragment());
            c.put("nameCn", backendService.resolveLabel(s.fragment(), BASE_NS));
            c.put("evidence", s.evidence());
            c.put("possHits", hit);
            c.put("possHitsCn", backendService.resolveLabels(hit, BASE_NS));
            c.put("possHitCount", s.possHits);
            c.put("possTotal", s.possTotal);
            c.put("gap", s.gap);
            c.put("note", String.format("主证缺口 %d，或然症命中 %d/%d",
                    s.gap, s.possHits, s.possTotal));
            possCands.add(c);
        }

        Map<String, Object> pathA = new LinkedHashMap<>();
        pathA.put("title", "补充症状，进一步确定");
        pathA.put("questions", questions);
        Map<String, Object> pathB = new LinkedHashMap<>();
        pathB.put("title", "按或然症给出的候选（仅供临床参考，非确定结论）");
        pathB.put("candidates", possCands);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("pathA", pathA);
        out.put("pathB", pathB);
        return out;
    }

    @JobWorker(type = "jianjiazheng-classification", autoComplete = false)
    public void handleJianJiaZhengClassification(final ActivatedJob job, final JobClient client) {
        try {
            String patientIri = (String) job.getVariablesAsMap().get("patientIri");
            PatientInput input = patientInputs.get(patientIri);
            if (input == null) throw new IllegalStateException("患者输入缓存丢失: " + patientIri);

            List<String> jianJiaTypes = withLiujingReasoner(input, ctx ->
                    OntologyModuleUtils.extractFragmentsByMetaClass(
                            ctx.getTypes(input.patientIri), jianJiaSubclasses));

            Map<String, Object> out = new LinkedHashMap<>();
            out.put("jianJiaZhengs", jianJiaTypes);
            out.put("jianJiaZhengsCn", backendService.resolveLabels(jianJiaTypes, BASE_NS));
            client.newCompleteCommand(job.getKey()).variables(out).send().join();
            log.info("兼夹证完成: {}", jianJiaTypes);
        } catch (Exception e) {
            log.error("兼夹证失败", e);
            client.newThrowErrorCommand(job.getKey())
                    .errorCode("JIANJIAZHENG_FAILED")
                    .errorMessage(e.getMessage()).send().join();
        }
    }

    // ============================================================
    // 方后注加减规则引擎：加载 / 解析 / 求值 / 派生
    // ============================================================

    private static final Pattern RULE_BLOCK =
            Pattern.compile("<owl:Class\\s+rdf:about=\"#([^\"]+)\">(.*?)</owl:Class>",
                    Pattern.DOTALL);
    private static final Pattern RULE_LITERAL =
            Pattern.compile("<(addHerbRule|removeHerbRule|replaceHerbRule|dosageChangeRule)[^>]*>(.*?)</\\1>",
                    Pattern.DOTALL);
    private static final Pattern RULE_SOURCE =
            Pattern.compile("<ruleSource[^>]*>(.*?)</ruleSource>", Pattern.DOTALL);
    private static final Pattern IF_THEN =
            Pattern.compile("(?is)^\\s*IF\\s+(.+?)\\s+THEN\\s+(.+?)\\s*$");

    /**
     * 从 rules.owl 加载方后注加减规则。
     * 关键点：OWLAPI 的 annotationAssertionAxioms() 不保证文档顺序，无法把 addHerbRule 与其
     * ruleSource 按位置配对；因此这里直接按 XML 文档顺序解析源文件，保证「规则↔出处」一一对应。
     */
    private void loadHerbRules() {
        fangzhengRuleIndex.clear();
        java.io.File rulesFile = locateRulesOwl();
        if (rulesFile == null) {
            log.warn("[规则] 未找到 rules.owl，方后注加减规则引擎不可用");
            return;
        }
        String xml;
        try {
            xml = new String(java.nio.file.Files.readAllBytes(rulesFile.toPath()),
                    java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("[规则] 读取 rules.owl 失败: {}", rulesFile, e);
            return;
        }

        Matcher bm = RULE_BLOCK.matcher(xml);
        while (bm.find()) {
            String host = bm.group(1);
            String body = bm.group(2);

            List<String> raws = new ArrayList<>();
            List<Integer> rawStarts = new ArrayList<>();
            Matcher lm = RULE_LITERAL.matcher(body);
            while (lm.find()) {
                raws.add(unescapeXml(lm.group(2)).trim());
                rawStarts.add(lm.start());
            }
            if (raws.isEmpty()) continue;

            List<String> sources = new ArrayList<>();
            List<Integer> srcStarts = new ArrayList<>();
            Matcher sm = RULE_SOURCE.matcher(body);
            while (sm.find()) {
                sources.add(unescapeXml(sm.group(1)).trim());
                srcStarts.add(sm.start());
            }

            List<HerbRule> parsed = new ArrayList<>();
            for (int i = 0; i < raws.size(); i++) {
                int ruleStart = rawStarts.get(i);
                int nextRuleStart = (i + 1 < rawStarts.size()) ? rawStarts.get(i + 1) : Integer.MAX_VALUE;
                String src = null;
                for (int j = 0; j < srcStarts.size(); j++) {
                    int ss = srcStarts.get(j);
                    if (ss >= ruleStart && ss < nextRuleStart) { src = sources.get(j); break; }
                }
                HerbRule r = parseHerbRule(raws.get(i), src);
                if (r != null) parsed.add(r);
            }
            if (!parsed.isEmpty()) fangzhengRuleIndex.put(host, parsed);
        }
    }

    /** 定位 rules.owl：优先 ontology/fangzheng/rules.owl，兜底递归查找。 */
    private java.io.File locateRulesOwl() {
        try {
            java.io.File main = new java.io.File(mainOntologyPath);
            java.io.File dir = main.isDirectory() ? main : main.getParentFile();
            if (dir == null) return null;
            java.io.File direct = new java.io.File(new java.io.File(dir, "fangzheng"), "rules.owl");
            if (direct.isFile()) return direct;
            return findFileRecursive(dir, "rules.owl", 4);
        } catch (Exception e) {
            log.warn("[规则] 定位 rules.owl 异常", e);
            return null;
        }
    }

    private java.io.File findFileRecursive(java.io.File dir, String name, int depth) {
        if (dir == null || depth < 0) return null;
        java.io.File[] children = dir.listFiles();
        if (children == null) return null;
        for (java.io.File c : children) {
            if (c.isFile() && c.getName().equalsIgnoreCase(name)) return c;
        }
        for (java.io.File c : children) {
            if (c.isDirectory()) {
                java.io.File r = findFileRecursive(c, name, depth - 1);
                if (r != null) return r;
            }
        }
        return null;
    }

    private String unescapeXml(String s) {
        if (s == null) return null;
        return s.replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"").replace("&apos;", "'")
                .replace("&amp;", "&");
    }

    /** 解析单条规则字面量 → HerbRule。 */
    private HerbRule parseHerbRule(String raw, String source) {
        String flat = raw.replaceAll("\\s+", " ").trim();
        Matcher m = IF_THEN.matcher(flat);
        if (!m.matches()) {
            log.warn("[规则] 无法解析规则字面量: {}", raw);
            return null;
        }
        HerbRule r = new HerbRule(raw, source);

        // ---- 条件：外层 AND，内层 OR ----
        for (String atom : splitTopLevelAnd(m.group(1))) {
            String a = atom.trim();
            if (a.isEmpty()) continue;
            if (a.startsWith("(") && a.endsWith(")")) {
                a = a.substring(1, a.length() - 1).trim();
                List<String> ors = new ArrayList<>();
                for (String o : a.split("(?i)\\s+OR\\s+")) {
                    String t = o.trim();
                    if (!t.isEmpty()) ors.add(t);
                }
                if (!ors.isEmpty()) r.condGroups.add(ors);
            } else {
                r.condGroups.add(List.of(a));
            }
        }

        // ---- 动作：remove / add / retain，无关键字者继承前一个动作 ----
        String cur = "add";
        for (String seg : m.group(2).split("\\s*\\+\\s*")) {
            String s = seg.trim();
            if (s.isEmpty()) continue;
            String low = s.toLowerCase(Locale.ROOT);
            if (low.startsWith("remove ")) { cur = "remove"; s = s.substring(7).trim(); }
            else if (low.startsWith("add ")) { cur = "add"; s = s.substring(4).trim(); }
            else if (low.startsWith("retain ")) { cur = "retain"; s = s.substring(7).trim(); }
            else if (low.startsWith("replace ")) { cur = "replace"; s = s.substring(8).trim(); }

            String herb;
            String dose = null;
            int lp = s.indexOf('(');
            if (lp >= 0) {
                herb = s.substring(0, lp).trim();
                int rp = s.lastIndexOf(')');
                dose = (rp > lp) ? s.substring(lp + 1, rp).trim() : s.substring(lp + 1).trim();
            } else {
                herb = s.trim();
            }
            if (herb.isEmpty()) continue;
            herb = herb.split("\\s+")[0].trim();
            if (herb.isEmpty()) continue;

            switch (cur) {
                case "remove" -> r.removes.add(herb);
                case "retain" -> r.retains.add(herb);
                case "replace" -> { /* 当前规则库无换药数据，忽略 */ }
                default -> r.adds.add(new AddAction(herb, dose));
            }
        }

        if (r.condGroups.isEmpty()) return null;
        return r;
    }

    /** 按顶层 AND 切分条件串（括号内的 OR 组不参与切分）。 */
    private List<String> splitTopLevelAnd(String s) {
        List<String> parts = new ArrayList<>();
        int depth = 0;
        StringBuilder cur = new StringBuilder();
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == '(') { depth++; cur.append(c); i++; continue; }
            if (c == ')') { depth--; cur.append(c); i++; continue; }
            boolean atAnd = depth == 0
                    && s.regionMatches(true, i, "AND", 0, 3)
                    && (i == 0 || Character.isWhitespace(s.charAt(i - 1)))
                    && (i + 3 >= s.length() || Character.isWhitespace(s.charAt(i + 3)));
            if (atAnd) {
                parts.add(cur.toString().trim());
                cur.setLength(0);
                i += 3;
                continue;
            }
            cur.append(c);
            i++;
        }
        parts.add(cur.toString().trim());
        parts.removeIf(String::isEmpty);
        return parts;
    }

    /** 取患者症状/脉象/舌象/腹证 fragment 集合（优先缓存，兜底流程变量）。 */
    private Set<String> resolvePatientFrags(Map<String, Object> vars) {
        String patientIri = (String) vars.get("patientIri");
        if (patientIri != null) {
            PatientInput cached = patientInputs.get(patientIri);
            if (cached != null) return collectPatientFrags(cached);
        }
        PatientInput fallback = new PatientInput(
                patientIri != null ? patientIri : "inline",
                ObdaQueryUtils.getList(vars, "symptomIris"),
                ObdaQueryUtils.getList(vars, "pulseIris"),
                ObdaQueryUtils.getList(vars, "tongueIris"),
                ObdaQueryUtils.getList(vars, "fuzhengIris"));
        return collectPatientFrags(fallback);
    }

    /**
     * 母方证 + 症状超出标准证候 → 应用该方证的方后注加减规则，派生出新方。
     * 语义：本方已有该药 → 视为剂量调整；本方无该药 → 新增；去药命中则移除。
     */
    private DerivedResult deriveFormula(OWLClass fzClass, Set<String> patientFrags,
                                        List<String> motherHerbIris, List<String> motherHerbCn) {
        DerivedResult res = new DerivedResult();
        res.herbIris.addAll(motherHerbIris);
        res.herbCn.addAll(motherHerbCn);

        List<HerbRule> rules = fangzhengRuleIndex.get(fzClass.getIRI().getFragment());
        if (rules == null || rules.isEmpty() || patientFrags == null || patientFrags.isEmpty()) {
            return res;
        }

        for (HerbRule rule : rules) {
            if (!rule.satisfiedBy(patientFrags)) continue;
            boolean changed = false;

            // 1) 去药
            for (String herb : rule.removes) {
                int idx = indexOfHerb(res.herbIris, herb);
                if (idx >= 0) {
                    String cn = res.herbCn.get(idx);
                    res.herbIris.remove(idx);
                    res.herbCn.remove(idx);
                    String full = ObdaQueryUtils.toFullIri(herb, BASE_NS);
                    if (full != null && !res.removedIris.contains(full)) {
                        res.removedIris.add(full);
                        res.removedCn.add(cn);
                    }
                    changed = true;
                }
            }

            // 2) 加药 / 剂量调整
            for (AddAction a : rule.adds) {
                String cn = labelOf(a.herb);
                int idx = indexOfHerb(res.herbIris, a.herb);
                if (idx >= 0) {
                    String note = cn + (a.dose != null ? "：" + a.dose : "：加量");
                    if (!res.dosageChanges.contains(note)) res.dosageChanges.add(note);
                    changed = true;
                } else {
                    String full = ObdaQueryUtils.toFullIri(a.herb, BASE_NS);
                    if (full == null) continue;
                    res.herbIris.add(full);
                    res.herbCn.add(cn);
                    if (!res.addedIris.contains(full)) {
                        res.addedIris.add(full);
                        res.addedCn.add(cn);
                    }
                    changed = true;
                }
            }

            // 3) 保留（retain）：本有则不动，本无则补入
            for (String herb : rule.retains) {
                if (indexOfHerb(res.herbIris, herb) < 0) {
                    String full = ObdaQueryUtils.toFullIri(herb, BASE_NS);
                    if (full == null) continue;
                    res.herbIris.add(full);
                    res.herbCn.add(labelOf(herb));
                }
            }

            if (changed) {
                res.derived = true;
                res.appliedRules.add(rule.raw);
                if (rule.source != null && !rule.source.isBlank()) res.ruleSources.add(rule.source);
            }
        }
        return res;
    }

    private int indexOfHerb(List<String> iris, String herbFragment) {
        for (int i = 0; i < iris.size(); i++) {
            if (herbFragment.equals(ObdaQueryUtils.fragmentOf(iris.get(i)))) return i;
        }
        return -1;
    }

    /** 派生方名：母方名 + 去X + 加Y。 */
    private String buildDerivedName(String baseCn, DerivedResult d) {
        if (!d.derived) return baseCn;
        StringBuilder sb = new StringBuilder(baseCn);
        if (!d.removedCn.isEmpty()) sb.append("去").append(String.join("、", d.removedCn));
        if (!d.addedCn.isEmpty()) sb.append("加").append(String.join("、", d.addedCn));
        if (d.removedCn.isEmpty() && d.addedCn.isEmpty() && !d.dosageChanges.isEmpty()) {
            sb.append("加减");
        }
        return sb.toString();
    }

    // ==================== 规则引擎测试钩子（public，不依赖 Spring/Ontop/DB） ====================
    // 说明：测试类位于 com.ocean.ontologyframework.tcm 子包，与生产类不同包，
    //       故这两个钩子必须为 public 才能被跨包调用（仅测试使用，生产链路不经过它们）。

    /** 加载 rules.owl 并返回「方证 fragment → 规则条数」。 */
    public Map<String, Integer> loadHerbRulesForTest(String ontologyMainPath) {
        this.mainOntologyPath = ontologyMainPath;
        loadHerbRules();
        Map<String, Integer> m = new LinkedHashMap<>();
        fangzhengRuleIndex.forEach((k, v) -> m.put(k, v.size()));
        return m;
    }

    /** 返回某方证在给定患者症状下的派生结果（Map 形式，便于断言）。 */
    public Map<String, Object> deriveFormulaForTest(String fangzhengFragment, Set<String> patientFrags,
                                                    List<String> motherHerbFrags) {
        OWLClass cls = OWLManager.getOWLDataFactory()
                .getOWLClass(IRI.create(BASE_NS + fangzhengFragment));
        List<String> iris = motherHerbFrags.stream()
                .map(h -> BASE_NS + h).collect(Collectors.toList());
        DerivedResult d = deriveFormula(cls, patientFrags, iris, new ArrayList<>(motherHerbFrags));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("derived", d.derived);
        m.put("herbsCn", d.herbCn);
        m.put("addedCn", d.addedCn);
        m.put("removedCn", d.removedCn);
        m.put("dosageChanges", d.dosageChanges);
        m.put("appliedRules", d.appliedRules);
        m.put("ruleSources", d.ruleSources);
        return m;
    }

    @JobWorker(type = "prescription-recommendation", autoComplete = false)
    public void handlePrescriptionRecommendation(final ActivatedJob job, final JobClient client) {
        try {
            Map<String, Object> vars = job.getVariablesAsMap();
            String fangzhengFragment = (String) vars.get("fangzheng");
            if (fangzhengFragment == null || "方证未定".equals(fangzhengFragment)) {
                client.newCompleteCommand(job.getKey()).variables(emptyRx()).send().join();
                return;
            }

            OWLClass fzClass = tboxDf.getOWLClass(IRI.create(BASE_NS + fangzhengFragment));
            String formulaIri = extractFormula(fzClass);
            if (formulaIri == null) {
                client.newCompleteCommand(job.getKey()).variables(emptyRx()).send().join();
                return;
            }
            formulaIri = ObdaQueryUtils.toFullIri(formulaIri, BASE_NS);
            OWLClass formulaCls = tboxDf.getOWLClass(IRI.create(formulaIri));

            // ==================== 本方组成（OBDA 查 you_yaowu） ====================
            List<String[]> herbPairs = queryHerbsWithLabels(formulaIri);
            List<String> herbIris = herbPairs.stream().map(p -> p[0]).collect(Collectors.toList());
            List<String> herbCn   = herbPairs.stream().map(p -> p[1]).collect(Collectors.toList());

            // ==================== 方剂加减（读本体 addedHerb / removedHerb 注解） ====================
            Set<IRI> removedIris = queryRemovedHerbs(formulaCls);
            Set<IRI> addedIris = queryAddedHerbs(formulaCls);

            List<String> addedFromFormulaIris = addedIris.stream()
                    .map(i -> ObdaQueryUtils.toFullIri(i.toString(), BASE_NS))
                    .filter(Objects::nonNull)
                    .distinct()
                    .collect(Collectors.toList());

            // ==================== 兼夹证加味 ====================
            List<String> addHerbFromJianJia = new ArrayList<>();
            @SuppressWarnings("unchecked")
            List<String> jianJiaZhengs = (List<String>) vars.get("jianJiaZhengs");
            if (jianJiaZhengs != null) {
                for (String jz : jianJiaZhengs) {
                    OWLClass jzCls = tboxDf.getOWLClass(IRI.create(BASE_NS + jz));
                    for (IRI h : getAddHerbs(jzCls)) {
                        String full = ObdaQueryUtils.toFullIri(h.toString(), BASE_NS);
                        if (!addHerbFromJianJia.contains(full)) addHerbFromJianJia.add(full);
                    }
                }
            }

            // ==================== 方后注加减规则派生（母方证 + 症状超出标准证候） ====================
            // 与 herb-modification 步骤共用 computeHerbModification，保证两步结果逐字段一致
            Set<String> patientFrags = resolvePatientFrags(vars);
            HerbModOutcome mod = computeHerbModification(
                    fzClass, herbIris, herbCn, addedFromFormulaIris, addHerbFromJianJia,
                    removedIris, patientFrags);
            DerivedResult derived = mod.derived;
            List<String> removedHerbIris = mod.removedHerbIris;
            List<String> allAddedHerbs = mod.allAddedHerbs;
            List<String> finalHerbIris = mod.finalHerbIris;
            List<String> finalHerbCn = mod.finalHerbCn;

            // ==================== 配伍禁忌检查（本方 + 加味 + 规则加减） ====================
            List<String> herbsForCheck = new ArrayList<>(finalHerbIris);
            herbsForCheck.addAll(allAddedHerbs);
            List<String> warnings = checkIncompatibilities(herbsForCheck);

            // ==================== 中文标签 ====================
            List<String> addHerbCn = allAddedHerbs.stream()
                    .map(this::queryLabel).collect(Collectors.toList());
            List<String> removedHerbCn = removedHerbIris.stream()
                    .map(this::queryLabel).collect(Collectors.toList());
            String baseFormulaCn = queryLabel(formulaIri);
            String formulaCn = derived.derived ? buildDerivedName(baseFormulaCn, derived) : baseFormulaCn;

            Map<String, Object> out = new LinkedHashMap<>();
            out.put("finalFormula", formulaIri);
            out.put("finalFormulaCn", formulaCn);
            out.put("baseFormulaCn", baseFormulaCn);
            // ---- 母方（供 herb-modification 步骤复用，向后兼容：仅新增键） ----
            out.put("baseFormula", formulaIri);
            out.put("baseHerbs", herbIris);
            out.put("baseHerbsCn", herbCn);
            out.put("candidateFormulas", List.of(formulaIri));
            out.put("herbs", finalHerbIris);
            out.put("herbsCn", finalHerbCn);
            out.put("addedHerb", allAddedHerbs);
            out.put("addedHerbCn", addHerbCn);
            out.put("removedHerb", removedHerbIris);
            out.put("removedHerbCn", removedHerbCn);
            out.put("warnings", warnings);
            // ---- 方后注加减派生信息 ----
            out.put("derived", derived.derived);
            out.put("derivedFormulaName", derived.derived ? formulaCn : null);
            out.put("appliedRules", derived.appliedRules);
            out.put("ruleSources", derived.ruleSources);
            out.put("dosageChanges", derived.dosageChanges);
            client.newCompleteCommand(job.getKey()).variables(out).send().join();
            log.info("方剂完成: {} → {} 药物={} 加味={} 减味={} 剂量调整={} 命中规则={} 警告={}",
                    baseFormulaCn, formulaCn, finalHerbIris, allAddedHerbs, removedHerbIris,
                    derived.dosageChanges, derived.appliedRules.size(), warnings);
        } catch (Exception e) {
            log.error("方剂失败", e);
            client.newThrowErrorCommand(job.getKey())
                    .errorCode("PRESCRIPTION_FAILED")
                    .errorMessage(e.getMessage()).send().join();
        }
    }

    /**
     * 加减药（方后注派生）步骤。
     *
     * <p>临床语义：母方证确定后，若患者症状<b>超出母方标准证候</b>，依《伤寒论》方后注加减法
     * （{@code rules.owl} v2.7，46 个方证 / 84 条规则）派生最终方剂组成。
     *
     * <p>输入变量：{@code fangzheng}（母方证 fragment）、{@code baseFormula}（母方 IRI，
     * 由 {@code prescription-recommendation} 输出）、{@code addedHerb} / {@code removedHerb}（可选）、
     * 以及患者症状（{@code symptomIris} 等）。
     *
     * <p>输出变量：{@code finalFormula} / {@code finalFormulaCn} / {@code herbs} / {@code herbsCn} /
     * {@code derived} / {@code appliedRules} / {@code ruleSources} / {@code dosageChanges} /
     * {@code removedHerb} / {@code removedHerbCn} / {@code addedHerbCn} / {@code warnings} /
     * {@code herbModificationApplied} / {@code herbModificationSummary}。
     *
     * <p>与 {@code prescription-recommendation} 共用 {@link #computeHerbModification}，
     * 结果逐字段一致。
     */
    @JobWorker(type = "herb-modification", autoComplete = false)
    public void handleHerbModification(final ActivatedJob job, final JobClient client) {
        try {
            Map<String, Object> vars = job.getVariablesAsMap();
            String fangzhengFragment = strOf(vars.get("fangzheng"));
            String baseFormulaIri = strOf(vars.get("baseFormula"));
            if (baseFormulaIri == null) baseFormulaIri = strOf(vars.get("finalFormula"));

            Map<String, Object> out = new LinkedHashMap<>();

            if (fangzhengFragment == null || "方证未定".equals(fangzhengFragment) || baseFormulaIri == null) {
                out.put("derived", false);
                out.put("herbModificationApplied", false);
                out.put("herbModificationSummary", "无母方证或母方，跳过加减药");
                client.newCompleteCommand(job.getKey()).variables(out).send().join();
                log.info("加减药: 无母方，跳过");
                return;
            }

            OWLClass fzClass = tboxDf.getOWLClass(IRI.create(BASE_NS + fangzhengFragment));
            String formulaIri = ObdaQueryUtils.toFullIri(baseFormulaIri, BASE_NS);

            // 母方组成（以 prescription-recommendation 的输出为准，缺失时回查 OBDA）
            List<String> herbIris = ObdaQueryUtils.getList(vars, "baseHerbs");
            List<String> herbCn = ObdaQueryUtils.getList(vars, "baseHerbsCn");
            if (herbIris.isEmpty()) {
                List<String[]> pairs = queryHerbsWithLabels(formulaIri);
                herbIris = pairs.stream().map(p -> p[0]).collect(Collectors.toList());
                herbCn = pairs.stream().map(p -> p[1]).collect(Collectors.toList());
            }

            String baseFormulaCn = strOf(vars.get("baseFormulaCn"));
            if (baseFormulaCn == null) baseFormulaCn = queryLabel(formulaIri);

            // 本体注解的加减味（母方自带）
            OWLClass formulaCls = tboxDf.getOWLClass(IRI.create(formulaIri));
            Set<IRI> removedIris = queryRemovedHerbs(formulaCls);
            Set<IRI> addedIris = queryAddedHerbs(formulaCls);
            List<String> addedFromFormulaIris = addedIris.stream()
                    .map(i -> ObdaQueryUtils.toFullIri(i.toString(), BASE_NS))
                    .filter(Objects::nonNull)
                    .distinct()
                    .collect(Collectors.toList());

            // 兼夹证加味
            List<String> addHerbFromJianJia = new ArrayList<>();
            @SuppressWarnings("unchecked")
            List<String> jianJiaZhengs = (List<String>) vars.get("jianJiaZhengs");
            if (jianJiaZhengs != null) {
                for (String jz : jianJiaZhengs) {
                    OWLClass jzCls = tboxDf.getOWLClass(IRI.create(BASE_NS + jz));
                    for (IRI h : getAddHerbs(jzCls)) {
                        String full = ObdaQueryUtils.toFullIri(h.toString(), BASE_NS);
                        if (!addHerbFromJianJia.contains(full)) addHerbFromJianJia.add(full);
                    }
                }
            }

            // ==================== 方后注加减派生 ====================
            Set<String> patientFrags = resolvePatientFrags(vars);
            HerbModOutcome mod = computeHerbModification(
                    fzClass, herbIris, herbCn, addedFromFormulaIris, addHerbFromJianJia,
                    removedIris, patientFrags);
            DerivedResult derived = mod.derived;

            List<String> herbsForCheck = new ArrayList<>(mod.finalHerbIris);
            herbsForCheck.addAll(mod.allAddedHerbs);
            List<String> warnings = checkIncompatibilities(herbsForCheck);

            List<String> addHerbCn = mod.allAddedHerbs.stream().map(this::queryLabel).collect(Collectors.toList());
            List<String> removedHerbCn = mod.removedHerbIris.stream().map(this::queryLabel).collect(Collectors.toList());
            String formulaCn = derived.derived ? buildDerivedName(baseFormulaCn, derived) : baseFormulaCn;

            out.put("finalFormula", formulaIri);
            out.put("finalFormulaCn", formulaCn);
            out.put("baseFormula", formulaIri);
            out.put("baseFormulaCn", baseFormulaCn);
            out.put("herbs", mod.finalHerbIris);
            out.put("herbsCn", mod.finalHerbCn);
            out.put("addedHerb", mod.allAddedHerbs);
            out.put("addedHerbCn", addHerbCn);
            out.put("removedHerb", mod.removedHerbIris);
            out.put("removedHerbCn", removedHerbCn);
            out.put("warnings", warnings);
            out.put("derived", derived.derived);
            out.put("derivedFormulaName", derived.derived ? formulaCn : null);
            out.put("appliedRules", derived.appliedRules);
            out.put("ruleSources", derived.ruleSources);
            out.put("dosageChanges", derived.dosageChanges);
            out.put("herbModificationApplied", derived.derived);
            out.put("herbModificationSummary", derived.derived
                    ? "依方后注加减法派生，命中 " + derived.appliedRules.size() + " 条规则"
                    : "症状未超出母方标准证候，保持母方原组成");

            client.newCompleteCommand(job.getKey()).variables(out).send().join();
            log.info("加减药: {} → {} 命中规则={} 加味={} 减味={} 剂量调整={} 警告={}",
                    baseFormulaCn, formulaCn, derived.appliedRules.size(),
                    mod.allAddedHerbs, mod.removedHerbIris, derived.dosageChanges, warnings);
        } catch (Exception e) {
            log.error("herb-modification 失败", e);
            client.newThrowErrorCommand(job.getKey())
                    .errorCode("HERB_MODIFICATION_FAILED")
                    .errorMessage(e.getMessage()).send().join();
        }
    }

    /**
     * 方后注加减派生 + 最终组成计算。
     *
     * <p>{@code prescription-recommendation} 与 {@code herb-modification} 共用本方法，
     * 保证两个步骤的输出逐字段一致，不存在语义分叉。
     *
     * @param fzClass               母方证类
     * @param herbIris              母方组成（IRI）
     * @param herbCn                母方组成（中文）
     * @param addedFromFormulaIris  方剂本体注解 addedHerb
     * @param addHerbFromJianJia    兼夹证加味
     * @param removedIris           方剂本体注解 removedHerb
     * @param patientFrags          患者症状 fragment 集合
     */
    private HerbModOutcome computeHerbModification(OWLClass fzClass,
                                                   List<String> herbIris,
                                                   List<String> herbCn,
                                                   List<String> addedFromFormulaIris,
                                                   List<String> addHerbFromJianJia,
                                                   Set<IRI> removedIris,
                                                   Set<String> patientFrags) {
        List<String> removedHerbIris = removedIris.stream()
                .map(i -> ObdaQueryUtils.toFullIri(i.toString(), BASE_NS))
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

        List<String> allAddedHerbs = new ArrayList<>(addedFromFormulaIris);
        for (String h : addHerbFromJianJia) {
            if (!allAddedHerbs.contains(h)) allAddedHerbs.add(h);
        }

        DerivedResult derived = deriveFormula(fzClass, patientFrags, herbIris, herbCn);

        for (String h : derived.addedIris) {
            if (!allAddedHerbs.contains(h)) allAddedHerbs.add(h);
        }
        for (String h : derived.removedIris) {
            if (!removedHerbIris.contains(h)) removedHerbIris.add(h);
        }

        // 最终组成：命中规则时 = 母方 ± 方后注加减；未命中时 = 母方原组成
        List<String> finalHerbIris = derived.derived ? derived.herbIris : herbIris;
        List<String> finalHerbCn = derived.derived ? derived.herbCn : herbCn;

        return new HerbModOutcome(derived, removedHerbIris, allAddedHerbs, finalHerbIris, finalHerbCn);
    }

    /**
     * 从本体读取某方剂的"减味"药物。
     */
    private Set<IRI> queryRemovedHerbs(OWLClass formulaCls) {
        Set<IRI> res = new HashSet<>();
        OWLOntology tbox = backendService.getOntologyService().gettBoxOntology();
        Set<String> props = Set.of("removed_herb", "removedHerb");

        for (OWLAnnotationAssertionAxiom ax :
                tbox.annotationAssertionAxioms(formulaCls.getIRI())
                        .collect(Collectors.toList())) {
            String prop = ax.getProperty().getIRI().getFragment();
            if (props.contains(prop) && ax.getValue() instanceof IRI) {
                res.add((IRI) ax.getValue());
            }
        }
        for (OWLObjectPropertyAssertionAxiom ax :
                tbox.objectPropertyAssertionAxioms(
                                tbox.getOWLOntologyManager().getOWLDataFactory()
                                        .getOWLNamedIndividual(formulaCls.getIRI()))
                        .collect(Collectors.toList())) {
            String prop = ax.getProperty().getNamedProperty().getIRI().getFragment();
            if (props.contains(prop) && ax.getObject().isNamed()) {
                res.add(ax.getObject().asOWLNamedIndividual().getIRI());
            }
        }
        return res;
    }

    /**
     * 从本体读取某方剂的"组成药物"。
     */
    private Set<IRI> queryHasHerbs(OWLClass formulaCls) {
        Set<IRI> res = new HashSet<>();
        OWLOntology tbox = backendService.getOntologyService().gettBoxOntology();
        Set<String> props = Set.of("has_herb", "hasHerb");

        for (OWLAnnotationAssertionAxiom ax :
                tbox.annotationAssertionAxioms(formulaCls.getIRI())
                        .collect(Collectors.toList())) {
            String prop = ax.getProperty().getIRI().getFragment();
            if (props.contains(prop) && ax.getValue() instanceof IRI) {
                res.add((IRI) ax.getValue());
            }
        }
        for (OWLObjectPropertyAssertionAxiom ax :
                tbox.objectPropertyAssertionAxioms(
                                tbox.getOWLOntologyManager().getOWLDataFactory()
                                        .getOWLNamedIndividual(formulaCls.getIRI()))
                        .collect(Collectors.toList())) {
            String prop = ax.getProperty().getNamedProperty().getIRI().getFragment();
            if (props.contains(prop) && ax.getObject().isNamed()) {
                res.add(ax.getObject().asOWLNamedIndividual().getIRI());
            }
        }
        return res;
    }

    /**
     * 找当前方剂的"母方"。
     */
    private Set<IRI> queryParentHerbs(OWLClass formulaCls) {
        Set<IRI> res = new HashSet<>();
        OWLOntology tbox = backendService.getOntologyService().gettBoxOntology();
        Set<OWLClass> fangjiSubs = backendService.getAllNamedSubclasses(
                IRI.create(BASE_NS + "Fangji"));

        for (OWLSubClassOfAxiom ax :
                tbox.subClassAxiomsForSubClass(formulaCls).collect(Collectors.toList())) {
            OWLClassExpression sup = ax.getSuperClass();
            if (!sup.isOWLClass()) continue;
            OWLClass parent = sup.asOWLClass();
            if (fangjiSubs.contains(parent)
                    && !parent.getIRI().getFragment().equals("Fangji")) {
                res.addAll(queryHasHerbs(parent));
            }
        }
        return res;
    }

    private Map<String, Object> emptyRx() {
        Map<String, Object> o = new LinkedHashMap<>();
        o.put("finalFormula", null);
        o.put("finalFormulaCn", null);
        o.put("herbs", new ArrayList<>());
        o.put("herbsCn", new ArrayList<>());
        o.put("addedHerb", new ArrayList<>());
        o.put("addedHerbCn", new ArrayList<>());
        o.put("removedHerb", new ArrayList<>());
        o.put("removedHerbCn", new ArrayList<>());
        o.put("warnings", new ArrayList<>());
        return o;
    }

    private String extractFormula(OWLClass cls) {
        OWLOntology ont = backendService.getOntologyService().gettBoxOntology();
        // ① 主治方剂注解（现行写法：方证相应的治疗映射，元数据形式）
        for (OWLAnnotationAssertionAxiom ax : ont.getAnnotationAssertionAxioms(cls.getIRI())) {
            if (ax.getProperty().getIRI().toString().equals(HAS_PRESCRIPTION_ANNO)
                    && ax.getValue() instanceof IRI iri) {
                return iri.toString();
            }
        }
        // ② 兼容旧写法：∃you_chufang.{方剂} 名义量（等价定义或子类公理中）
        for (OWLEquivalentClassesAxiom ax : ont.getEquivalentClassesAxioms(cls)) {
            for (OWLClassExpression e : ax.getClassExpressions()) {
                if (e.equals(cls)) continue;
                String f = findHasValue(e);
                if (f != null) return f;
            }
        }
        for (OWLSubClassOfAxiom ax : ont.getSubClassAxiomsForSubClass(cls)) {
            String f = findHasValue(ax.getSuperClass());
            if (f != null) return f;
        }
        return null;
    }

    private String findHasValue(OWLClassExpression expr) {
        if (expr instanceof OWLObjectHasValue hv) {
            if (hv.getProperty().asOWLObjectProperty().getIRI().toString()
                    .equals(HAS_PRESCRIPTION)) {
                OWLIndividual ind = hv.getFiller();
                if (ind.isNamed()) return ind.asOWLNamedIndividual().getIRI().toString();
            }
        } else if (expr instanceof OWLObjectIntersectionOf inter) {
            for (OWLClassExpression op : inter.getOperands()) {
                String f = findHasValue(op);
                if (f != null) return f;
            }
        }
        return null;
    }

    private Set<IRI> getAddHerbs(OWLClass cls) {
        Set<IRI> res = new HashSet<>();
        OWLOntology ont = backendService.getOntologyService().gettBoxOntology();
        for (OWLAnnotationAssertionAxiom ax : ont.getAnnotationAssertionAxioms(cls.getIRI())) {
            if (ax.getProperty().getIRI().getFragment().equals("addHerb")
                    && ax.getValue() instanceof IRI) res.add((IRI) ax.getValue());
        }
        return res;
    }

    private List<String[]> queryHerbsWithLabels(String formulaIri) {
        String sparql = """
            PREFIX : <http://www.tcm-classics.org/jingfang#>
            PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
            SELECT ?herb ?label WHERE {
                <%s> :you_yaowu ?herb .
                OPTIONAL { ?herb rdfs:label ?label . }
            }
            ORDER BY ?herb
            """.formatted(ObdaQueryUtils.toFullIri(formulaIri, BASE_NS));

        List<String[]> result = new ArrayList<>();
        for (Map<String, String> row :
                backendService.getObdaHandler().executeAboxQuery(sparql)) {
            String herb = row.get("herb");
            if (herb == null || herb.isBlank()) continue;
            String full = ObdaQueryUtils.toFullIri(herb.trim(), BASE_NS);
            String label = row.get("label");
            if (label == null || label.isBlank()) {
                label = ObdaQueryUtils.fragmentOf(full);
            } else {
                label = label.trim();
            }
            result.add(new String[]{ full, label });
        }
        return result;
    }

    private String resolveClassLabel(OWLClass cls) {
        String fullIri = cls.getIRI().toString();
        String label = backendService.resolveLabel(fullIri, BASE_NS);
        return (label != null && !label.isBlank()) ? label : cls.getIRI().getFragment();
    }

    private String queryLabel(String iri) {
        return ObdaQueryUtils.queryLabel(
                backendService.getObdaHandler(), iri, BASE_NS, log);
    }

    private List<String> checkIncompatibilities(List<String> herbIris) {
        List<String> warnings = new ArrayList<>();
        if (herbIris == null || herbIris.size() < 2) return warnings;

        Set<String> herbs = herbIris.stream()
                .map(i -> ObdaQueryUtils.toFullIri(i, BASE_NS))
                .filter(Objects::nonNull)
                .map(ObdaQueryUtils::fragmentOf)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        checkPairs(herbs, shibafanMap,  "十八反", warnings);
        checkPairs(herbs, shijiuweiMap, "十九畏", warnings);
        return warnings;
    }

    private void checkPairs(Set<String> herbs,
                            Map<String, Set<String>> relMap,
                            String label,
                            List<String> warnings) {
        if (relMap == null || relMap.isEmpty()) return;
        Set<String> seen = new HashSet<>();
        for (String h : herbs) {
            Set<String> others = relMap.get(h);
            if (others == null) continue;
            for (String o : others) {
                if (!herbs.contains(o)) continue;
                String key = h.compareTo(o) <= 0 ? h + "|" + o : o + "|" + h;
                if (!seen.add(key)) continue;
                warnings.add(label + "：" + labelOf(h)
                        + (label.equals("十八反") ? " 反 " : " 畏 ")
                        + labelOf(o));
            }
        }
    }

    @JobWorker(type = "diagnosis-explanation", autoComplete = false)
    public void handleDiagnosisExplanation(final ActivatedJob job, final JobClient client) {
        try {
            Map<String, Object> vars = job.getVariablesAsMap();
            String patientIri = (String) vars.get("patientIri");
            String sixChannelCn = (String) vars.get("sixChannelCn");
            String fangzhengCn = (String) vars.get("fangzhengCn");
            String finalFormulaCn = (String) vars.get("finalFormulaCn");

            String liujingDisplay = (sixChannelCn != null && !sixChannelCn.isEmpty())
                    ? sixChannelCn : "六经难定";
            String fangzhengDisplay = (fangzhengCn != null && !fangzhengCn.isEmpty())
                    ? fangzhengCn : "方证未定";
            String formulaDisplay = (finalFormulaCn != null && !finalFormulaCn.isEmpty())
                    ? finalFormulaCn : "未定";

            String explanation = String.format("六经：%s，方证：%s，推荐方剂：%s。",
                    liujingDisplay, fangzhengDisplay, formulaDisplay);

            // 若推荐方剂由母方证方后注加减派生而来，附上依据
            Object derivedFlag = vars.get("derived");
            if (Boolean.TRUE.equals(derivedFlag)) {
                Object baseCn = vars.get("baseFormulaCn");
                Object srcs = vars.get("ruleSources");
                StringBuilder sb = new StringBuilder(explanation);
                if (baseCn != null) {
                    sb.append("本方由").append(baseCn).append("依方后注加减法派生");
                }
                if (srcs instanceof List<?> list && !list.isEmpty()) {
                    sb.append("（依据：").append(list.get(0)).append("）");
                }
                sb.append("。");
                explanation = sb.toString();
            }

            Map<String, Object> out = new LinkedHashMap<>();
            out.put("explanation", explanation);
            client.newCompleteCommand(job.getKey()).variables(out).send().join();
            log.info("诊断解释: {}", explanation);

            if (patientIri != null) {
                clearPatientCache(patientIri);
            }
        } catch (Exception e) {
            log.error("诊断解释失败", e);
            client.newThrowErrorCommand(job.getKey())
                    .errorCode("EXPLANATION_FAILED")
                    .errorMessage(e.getMessage()).send().join();
        }
    }

    // ==================== 工具方法 ====================

    private void clearPatientCache(String patientIri) {
        patientInputs.remove(patientIri);
        fangzhengCandidatesCache.remove(patientIri);
        miniCtxMgr.clearByPrefix(patientIri);
    }

    /** 变量取值：null / 空串 → null。 */
    private static String strOf(Object v) {
        if (v == null) return null;
        String s = v.toString().trim();
        return s.isEmpty() ? null : s;
    }

    /** 变量取值：非数字或缺失时返回默认值。 */
    private static int intOf(Object v, int def) {
        if (v instanceof Number n) return n.intValue();
        if (v != null) {
            try {
                return Integer.parseInt(v.toString().trim());
            } catch (NumberFormatException ignored) {
                // fallthrough
            }
        }
        return def;
    }

    private String frag(String iri) {
        return ObdaQueryUtils.frag(iri, BASE_NS, INSTANCE_SUFFIX);
    }

    /**
     * 判断某个类是否是抽象类（带 isAbstract=true 注解）。
     */
    private boolean isAbstractClass(OWLClass cls) {
        OWLOntology tbox = backendService.getOntologyService().gettBoxOntology();
        return tbox.annotationAssertionAxioms(cls.getIRI())
                .anyMatch(ax -> {
                    if (!ax.getProperty().getIRI().getFragment().equals("isAbstract")) {
                        return false;
                    }
                    OWLAnnotationValue v = ax.getValue();
                    if (v instanceof OWLLiteral lit) {
                        return "true".equalsIgnoreCase(lit.getLiteral().trim());
                    }
                    return false;
                });
    }

    /**
     * 从本体读取某方剂的 addedHerb 注解。
     */
    private Set<IRI> queryAddedHerbs(OWLClass formulaCls) {
        Set<IRI> res = new HashSet<>();
        OWLOntology tbox = backendService.getOntologyService().gettBoxOntology();
        Set<String> props = Set.of("added_herb", "addedHerb");
        for (OWLAnnotationAssertionAxiom ax :
                tbox.annotationAssertionAxioms(formulaCls.getIRI())
                        .collect(Collectors.toList())) {
            String prop = ax.getProperty().getIRI().getFragment();
            if (props.contains(prop) && ax.getValue() instanceof IRI) {
                res.add((IRI) ax.getValue());
            }
        }
        return res;
    }
}