package com.ocean.ontologyframework.plumblossom;

import com.ocean.openlletresolver.BackendService;
import com.ocean.ontopobdahandler.OBDAHandler;
import com.ocean.utilities.ChatAgent;
import com.ocean.utilities.OntologyWorkerSupport;
import com.ocean.utilities.RabbitMqHandler;
import io.camunda.client.annotation.JobWorker;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.JobClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 梅花易数占断流程（{@code PlumBlossomDivinationProcess}）的 JobWorker。
 *
 * <p><b>20 个</b> {@link JobWorker} 与 {@code ontology/bpmn/plumBlossomProcess.bpmn} 的 <b>20</b> 个
 * {@code serviceTask} 一一对应（契约见《PlumBlossom应用-应用架构设计》§4）：
 * preceptCheck / preceptAlert / routeDivination / characterSplit / castHexagram /
 * determineBodyUse / deriveMutualHexagram / deriveChangedHexagram / analyzeShengKe /
 * analyzeVitality / extractImageSymbol / categorizeDivination / analyzeOmen /
 * judgeAuspiciousness / determineResponsePeriod / generateAdvice / sendAlert / aggregateResult /
 * interpretResult / fetchDictum。
 *
 * <p><b>落地口径</b>（第 11 步）：TBox 经 {@code ontology.main-path}（{@code plumblossom-all.owl}，
 * 依铁律 11 仅聚合 TBox）由 {@link BackendService#getInstance(String, OBDAHandler)} 加载；
 * ABox 的历法/干支与周易文本（卦名/卦辞/爻辞）依闸门⑩裁决**入库**，经
 * {@code ontology.obda-path} / {@code ontology.obda-properties-path}（{@code plumBlossom.obda/.properties}，
 * Ontop 虚拟知识图谱，{@code ontop.ontologyFile=ontology/plumblossom-abox.owl}）虚拟访问，
 * 不再迁入本体文件（设计《本体核心设计》§8）。
 *
 * <p><b>本体驱动</b>（B1/B3）：八卦域知识（{@code core:Trigram} 的先天数 {@code core:trigramNumber}、
 * 五行 {@code rel:hasFivePhase}、方位 {@code core:hasDirection}）、四不占（{@code div:Precept}）、
 * 吉凶等级（{@code jud:Auspiciousness}）与六十四卦名录（{@code yijing:HexagramSequence}）于
 * {@link #loadOntologyDomain()} / {@link #verifyOntologyKnowledge()} 中经 Ontop 虚拟知识图谱装载，
 * **运行时以本体为唯一真源**；OBDA 不可用时回退内置表（{@code TRI_*}）以保证起卦可运算，并记 WARN。
 * 起卦法的结构运算（先天数→八卦、卦以八除、爻以六除、互变取象）与 SWRL 判据 R1~R15 同源，
 * 五行生克/旺衰判据经 {@code rel:generates/rel:overcomes} 传导语义与 R3~R7 保持一致（B2 逐例互校见测试）。
 *
 * <p>全部业务逻辑经现存 API（{@link BackendService}、{@link OntologyWorkerSupport}、
 * {@link RabbitMqHandler}）实现，不重写平台 API（★铁律21）。
 */
@Component
@Profile("PlumBlossomBPMN")
public class PlumBlossomOntologyJobWorker extends OntologyWorkerSupport {

    private static final Logger log = LoggerFactory.getLogger(PlumBlossomOntologyJobWorker.class);

    @Value("${ontology.main-path}")
    private String mainOntologyPath;

    /**
     * OBDA（Ontop 虚拟知识图谱）映射文件与属性文件路径（第 11 步）。
     *
     * <p>历法/干支与周易文本（卦名/卦辞/爻辞）已依闸门⑩裁决入库（MySQL），
     * 由 Ontop 经 {@code plumBlossom.obda} / {@code plumBlossom.properties} 虚拟访问；
     * {@link OBDAHandler#init(String, String)} 仅登记路径（懒加载），真正连库发生在首次查询。
     */
    @Value("${ontology.obda-path}")
    private String obdaPath;

    @Value("${ontology.obda-properties-path}")
    private String obdaPropertiesPath;

    @Value("${env-prepare.rabbitmq.exchange}")
    private String alertExchange;

    @Value("${env-prepare.rabbitmq.routing-key}")
    private String alertRoutingKey;

    /**
     * 节点 17「邵康节解读」调用的大模型客户端（通用能力，见 {@link com.ocean.utilities.ChatAgent}）。
     * 未配置 {@code spring.ai.openai.*}（未装配或不可用）时节点降级：不生成解读、以空串完成，流程不阻断。
     */
    @Autowired(required = false)
    private ChatAgent llm;

    /** 节点 17「邵康节解读」的系统提示：以邵康节身份、依《梅花易数》原著义理详析，严禁编造出处。 */
    private static final String INTERPRET_SYSTEM_PROMPT =
            "你是北宋理学家邵雍（字尧夫，谥康节），《梅花易数》的创立者。请以邵康节本人的身份与口吻，"
            + "严格依据《梅花易数》原著义理与体例，对给定的「占断结论」作详细解析："
            + "逐环节说明其推断依据（本卦/互卦/变卦、体用、五行生克、卦气旺衰、万物类象、外应、吉凶、应期、修身建议各据何理），"
            + "并引述原著相关篇目的口诀或观点加以印证，可参照的篇目包括："
            + "卷之一·卦数起例、爻以六除、互卦止用八卦、八宫所属五行、卦气旺衰、八卦万物属类（八卦万物类占）；"
            + "卷之二·体用总诀、衰旺论、占卜总诀、占卜论理诀、三要灵应篇、观物洞玄歌、心易占卜玄机；"
            + "卷之三·断占总诀、克应、诸卦反对性情；卷之四、卷之五（含八卦取象、六十四卦次第歌）；附录·一撮金。"
            + "要求：论述详实、条理清晰、有据可依。严禁编造原著中并不存在的篇目、条文或占例；"
            + "若某一环节确无原著明文可引，须明确标注「此处依常理推之」，不得杜撰出处。"
            + "使用规范 Markdown 输出。";

    /** 节点 17「邵康节解读」之白话文解释的系统提示：把古典解析转写为通俗现代白话，术语随文浅释。 */
    private static final String INTERPRET_PLAIN_SYSTEM_PROMPT =
            "你是《梅花易数》的白话讲解员。请把给定的「占断事项」「占断结论」以及邵康节式的古典解读，"
            + "用通俗易懂的现代白话文重新讲解一遍：分点讲清本卦/互卦/变卦、体卦与用卦、五行生克、卦气旺衰、"
            + "万物类象、外应、吉凶等级、应期、修身建议各自是什么含义、对问占者意味着什么；"
            + "遇文言与术语随即用一句话浅释，避免堆砌名词。"
            + "只作白话讲解与转述，严禁编造原著中并不存在的篇目、条文或占例。"
            + "使用规范 Markdown 输出。";

    /**
     * 四不占「重卦」判定：本次 JVM 会话内已占断过的 subject（跨会话历史不追溯，见 §10-1）。
     * 设上限防无界增长（C6）：超过 {@link #SESSION_SUBJECTS_LIMIT} 时清空重置。
     */
    private static final Set<String> SESSION_SUBJECTS = ConcurrentHashMap.newKeySet();
    private static final int SESSION_SUBJECTS_LIMIT = 10_000;

    // ==================== 本体域知识缓存（B1/B3：本体为唯一真源，内置表兜底） ====================
    /** 八卦域知识是否已成功由本体/OBDA 装载。 */
    private volatile boolean ontologyDomainReady = false;
    /** 八卦名（乾…坤）→ 先天卦数（core:trigramNumber）。 */
    private volatile Map<String, Integer> ontoTrigramNumber = new LinkedHashMap<>();
    /** 八卦名 → 五行（rel:hasFivePhase → rdfs:label）。 */
    private volatile Map<String, String> ontoTrigramFive = new LinkedHashMap<>();
    /** 八卦名 → 后天方位（core:hasDirection → rdfs:label）。 */
    private volatile Map<String, String> ontoTrigramDir = new LinkedHashMap<>();
    /** 方位 → 八卦名（由 ontoTrigramDir 反查）。 */
    private volatile Map<String, String> ontoDirToTrigram = new LinkedHashMap<>();
    /** 本体载入的四不占条目名（div:Precept）。 */
    private volatile Set<String> ontoPrecepts = new LinkedHashSet<>();
    /** 本体载入的吉凶等级：名（大吉…大凶）→ 数值（5…1）（jud:Auspiciousness）。 */
    private volatile Map<String, Integer> ontoAuspiciousness = new LinkedHashMap<>();

    // ==================== 内置表：先天八卦（乾1兑2离3震4巽5坎6艮7坤8） ====================
    // 内置表为「本体不可用」时的兜底（B3）：运行时优先取本体/OBDA 装载的域知识。
    private static final String[] TRI_NAME = {
            null, "乾", "兑", "离", "震", "巽", "坎", "艮", "坤"};
    private static final String[] TRI_FIVE = {
            null, "金", "金", "火", "木", "木", "水", "土", "土"};
    private static final String[] TRI_DIR = {
            null, "西北", "西", "南", "东", "东南", "北", "东北", "西南"};
    private static final String[] TRI_ROLE = {
            null, "父", "少女", "中女", "长男", "长女", "中男", "少男", "母"};
    private static final String[] TRI_NATURE = {
            null, "健", "说", "丽", "起", "入", "陷", "止", "顺"};
    private static final String[] TRI_MAIN = {
            null, "天", "泽", "火", "雷", "风", "水", "山", "地"};

    /**
     * 八卦万物属类关键词（原著卷之一「八卦万物属类」主象提炼），用于由象取卦。
     *
     * <p>C3：改用 {@link LinkedHashMap} 固定顺序（原 {@code Map.of} 无序，多关键词命中时
     * 结果依 JVM 迭代顺序而异，同一输入可能起出不同卦）。{@link #xiangToNumber(String)}
     * 以「文本首见之象为准」按显式优先级（卦数 1→8）取象，确保可复现。
     */
    private static final Map<Integer, String[]> TRI_KEYWORDS = new LinkedHashMap<>();

    static {
        TRI_KEYWORDS.put(1, new String[]{"天", "父", "老人", "官", "贵", "头", "骨", "马", "金", "宝", "珠", "玉", "圆", "刚"});
        TRI_KEYWORDS.put(2, new String[]{"泽", "少女", "巫", "舌", "羊", "口", "悦", "毁折", "饮食"});
        TRI_KEYWORDS.put(3, new String[]{"火", "日", "目", "电", "中女", "甲胄", "文书", "炉", "灶", "文彩", "枯槁"});
        TRI_KEYWORDS.put(4, new String[]{"雷", "长男", "龙", "竹", "足", "动", "声"});
        TRI_KEYWORDS.put(5, new String[]{"风", "长女", "僧", "尼", "股", "木", "草", "林", "绳直"});
        TRI_KEYWORDS.put(6, new String[]{"水", "雨", "雪", "中男", "耳", "血", "盗", "鱼", "盐", "酒", "溪", "井"});
        TRI_KEYWORDS.put(7, new String[]{"山", "少男", "童", "狗", "手", "指", "门", "石", "土"});
        TRI_KEYWORDS.put(8, new String[]{"地", "母", "老妇", "土", "牛", "布", "帛", "文章", "腹", "方"});
    }

    /** 六十四卦名录（内置 R15「trigramPairMap」：[上卦][下卦]，与 yijing:Seq_01..64 互参）。 */
    private static final String[][] HEX = new String[9][9];
    private static final Map<String, int[]> NAME2PAIR = new HashMap<>();
    /** 由卦名反查 King-Wen 序（与本体 yijing:HexagramSequence 互校）。 */
    private static final Map<String, Integer> NAME2SEQ = new HashMap<>();

    private static final Map<Integer, Integer> MASK2TRI = Map.of(
            0b111, 1, 0b011, 2, 0b101, 3, 0b001, 4, 0b110, 5, 0b010, 6, 0b100, 7, 0b000, 8);

    /** 五行生数（水1火2木3金4土5，河图生数），供应期「五行生数」法（A7）。 */
    private static final Map<String, Integer> SHENG_SHU =
            Map.of("水", 1, "火", 2, "木", 3, "金", 4, "土", 5);

    static {
        // 行=上卦（外），列=下卦（内），列序同 TRI_NAME（乾兑离震巽坎艮坤）
        final String[][] rows = {
                // 上乾(1)
                {"乾为天", "天泽履", "天火同人", "天雷无妄", "天风姤", "天水讼", "天山遁", "天地否"},
                // 上兑(2)
                {"泽天夬", "兑为泽", "泽火革", "泽雷随", "泽风大过", "泽水困", "泽山咸", "泽地萃"},
                // 上离(3)
                {"火天大有", "火泽睽", "离为火", "火雷噬嗑", "火风鼎", "火水未济", "火山旅", "火地晋"},
                // 上震(4)
                {"雷天大壮", "雷泽归妹", "雷火丰", "震为雷", "雷风恒", "雷水解", "雷山小过", "雷地豫"},
                // 上巽(5)
                {"风天小畜", "风泽中孚", "风火家人", "风雷益", "巽为风", "风水涣", "风山渐", "风地观"},
                // 上坎(6)
                {"水天需", "水泽节", "水火既济", "水雷屯", "水风井", "坎为水", "水山蹇", "水地比"},
                // 上艮(7)
                {"山天大畜", "山泽损", "山火贲", "山雷颐", "山风蛊", "山水蒙", "艮为山", "山地剥"},
                // 上坤(8)
                {"地天泰", "地泽临", "地火明夷", "地雷复", "地风升", "地水师", "地山谦", "坤为地"}};
        for (int u = 1; u <= 8; u++) {
            for (int l = 1; l <= 8; l++) {
                String name = rows[u - 1][l - 1];
                HEX[u][l] = name;
                NAME2PAIR.put(name, new int[]{u, l});
            }
        }

        // King-Wen 六十四卦序（与库 yijing_hexagram_sequence / 本体 yijing:Seq_01..64 一致），
        // 供由卦名反查卦序以定位 yijing:Dictum_Hex_NN / yijing:LineDictum_NN_M。
        final String[] kingWen = {
                "乾为天", "坤为地", "水雷屯", "山水蒙", "水天需", "天水讼", "地水师", "水地比",
                "风天小畜", "天泽履", "地天泰", "天地否", "天火同人", "火天大有", "地山谦", "雷地豫",
                "泽雷随", "山风蛊", "地泽临", "风地观", "火雷噬嗑", "山火贲", "山地剥", "地雷复",
                "天雷无妄", "山天大畜", "山雷颐", "泽风大过", "坎为水", "离为火", "泽山咸", "雷风恒",
                "天山遁", "雷天大壮", "火地晋", "地火明夷", "风火家人", "火泽睽", "水山蹇", "雷水解",
                "山泽损", "风雷益", "泽天夬", "天风姤", "泽地萃", "地风升", "泽水困", "水风井",
                "泽火革", "火风鼎", "震为雷", "艮为山", "风山渐", "雷泽归妹", "雷火丰", "火山旅",
                "巽为风", "兑为泽", "风水涣", "水泽节", "风泽中孚", "雷山小过", "水火既济", "火水未济"};
        for (int i = 0; i < kingWen.length; i++) {
            NAME2SEQ.put(kingWen[i], i + 1);
        }
    }

    @PostConstruct
    public void init() throws Exception {
        log.info("🔧 初始化 PlumBlossomOntologyJobWorker 依赖链...");
        initOntologyPipeline();
        verifyOntologyKnowledge();
        log.info("✅ PlumBlossomOntologyJobWorker 初始化完成 | ontologyPath={} | 本体域知识={}",
                mainOntologyPath, ontologyDomainReady ? "已装载" : "回退内置表");
    }

    @Override
    protected BackendService createBackendService() throws Exception {
        // 第 11 步：接入 OBDA（Ontop 虚拟知识图谱）。
        // OBDAHandler.init 仅登记路径（懒加载，不连库）；多 Spring 上下文场景下复用单例并重建 BackendService。
        boolean reusedObdaHandler = false;
        try {
            OBDAHandler.init(obdaPropertiesPath, obdaPath);
        } catch (IllegalStateException alreadyInitialized) {
            reusedObdaHandler = true;
            log.warn("⚠️ OBDAHandler 已初始化，复用现有实例（同一 JVM 内多 Spring 上下文场景）");
        }
        OBDAHandler obdaHandler = OBDAHandler.getInstance();
        if (reusedObdaHandler) {
            BackendService.setInstance(null);
        }
        return BackendService.getInstance(mainOntologyPath, obdaHandler);
    }

    /** {@link BackendService}（及平台查询服务）就绪后装载本体域知识（B1/B3）。 */
    @Override
    protected void afterBackendServiceReady() {
        loadOntologyDomain();
    }

    /**
     * 由本体/OBDA 装载八卦域知识（先天数/五行/方位）、四不占、吉凶等级（B1/B3）。
     *
     * <p>以 Ontop 虚拟知识图谱为准（其 {@code ontop.ontologyFile} 指向 {@code plumblossom-abox.owl}，
     * 其断言的 {@code core:Trigram} 等个体随本体进入查询结果）；拆分为多条单属性查询以规避
     * Ontop 对「同表双属性（含 OPTIONAL）」重写的缺陷。装载失败或条目不足 8 时回退内置表并记 WARN，
     * 保证起卦仍可运算。
     */
    private void loadOntologyDomain() {
        if (backendService == null || backendService.getObdaHandler() == null) {
            log.warn("⚠️ OBDA 未就绪，本体域知识装载跳过，五行/方位/先天数回退内置表");
            return;
        }
        OBDAHandler obda = backendService.getObdaHandler();
        try {
            Map<String, Integer> num = new LinkedHashMap<>();
            for (Map<String, String> row : obda.executeAboxQuery(
                    "PREFIX core: <http://example.org/plumblossom/core/>\n"
                            + "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>\n"
                            + "SELECT ?label ?num WHERE { ?t a core:Trigram ; rdfs:label ?label ; core:trigramNumber ?num }")) {
                putIfInt(num, row.get("label"), row.get("num"));
            }
            Map<String, String> five = new LinkedHashMap<>();
            for (Map<String, String> row : obda.executeAboxQuery(
                    "PREFIX core: <http://example.org/plumblossom/core/>\n"
                            + "PREFIX rel: <http://example.org/plumblossom/relations/>\n"
                            + "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>\n"
                            + "SELECT ?label ?five WHERE { ?t a core:Trigram ; rdfs:label ?label ; "
                            + "rel:hasFivePhase ?fp . ?fp rdfs:label ?five }")) {
                if (row.get("label") != null && row.get("five") != null) {
                    five.put(row.get("label"), row.get("five"));
                }
            }
            Map<String, String> dir = new LinkedHashMap<>();
            for (Map<String, String> row : obda.executeAboxQuery(
                    "PREFIX core: <http://example.org/plumblossom/core/>\n"
                            + "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>\n"
                            + "SELECT ?label ?dir WHERE { ?t a core:Trigram ; rdfs:label ?label ; "
                            + "core:hasDirection ?d . ?d rdfs:label ?dir }")) {
                if (row.get("label") != null && row.get("dir") != null) {
                    dir.put(row.get("label"), row.get("dir"));
                }
            }
            Set<String> precepts = new LinkedHashSet<>();
            for (Map<String, String> row : obda.executeAboxQuery(
                    "PREFIX div: <http://example.org/plumblossom/divination/>\n"
                            + "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>\n"
                            + "SELECT ?label WHERE { ?p a div:Precept ; rdfs:label ?label }")) {
                if (row.get("label") != null) {
                    precepts.add(row.get("label"));
                }
            }
            Map<String, Integer> aus = new LinkedHashMap<>();
            for (Map<String, String> row : obda.executeAboxQuery(
                    "PREFIX jud: <http://example.org/plumblossom/judgement/>\n"
                            + "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>\n"
                            + "SELECT ?label ?lv WHERE { ?a a jud:Auspiciousness ; rdfs:label ?label ; "
                            + "jud:auspiciousnessLevel ?lv }")) {
                putIfInt(aus, row.get("label"), row.get("lv"));
            }

            if (num.size() >= 8 && five.size() >= 8 && dir.size() >= 8) {
                this.ontoTrigramNumber = num;
                this.ontoTrigramFive = five;
                this.ontoTrigramDir = dir;
                Map<String, String> rev = new LinkedHashMap<>();
                dir.forEach((tri, d) -> rev.put(d, tri));
                this.ontoDirToTrigram = rev;
                this.ontologyDomainReady = true;
            } else {
                log.warn("⚠️ 本体八卦域知识不完整（先天数 {} / 五行 {} / 方位 {}），回退内置表",
                        num.size(), five.size(), dir.size());
            }
            if (!precepts.isEmpty()) {
                this.ontoPrecepts = precepts;
            }
            if (!aus.isEmpty()) {
                this.ontoAuspiciousness = aus;
            }
            log.info("✅ 本体域知识装载 | 八卦先天数 {} | 五行 {} | 方位 {} | 四不占 {} | 吉凶等级 {} | ready={}",
                    num.size(), five.size(), dir.size(), precepts.size(), aus.size(), ontologyDomainReady);
        } catch (Exception e) {
            log.warn("⚠️ 本体域知识装载失败，五行/方位/先天数回退内置表：{}", e.getMessage());
        }
    }

    /**
     * 以本体/库中六十四卦名录（{@code yijing:HexagramSequence}）与内置表互校（第 11 步：经 OBDA），
     * 并校验八卦域知识与内置表一致（B2：本体推理 vs 内置计算逐例比对）。
     *
     * <p>卦名/卦序已依闸门⑩裁决入库，故互校改由 Ontop 虚拟知识图谱查询
     * （{@code yijing:HexagramSequence} 由 {@code yijing_hexagram_sequence} 表映射而来）。
     * 库/OBDA 不可用时降级跳过，不影响起卦运算。
     */
    private void verifyOntologyKnowledge() {
        try {
            if (backendService == null || backendService.getObdaHandler() == null) {
                log.warn("⚠️ OBDA 未就绪，六十四卦名录互校跳过（不影响起卦运算）");
                return;
            }
            String sparql = """
                    PREFIX yijing: <http://example.org/plumblossom/yijing/>
                    PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
                    SELECT ?label WHERE {
                        ?h a yijing:HexagramSequence ;
                           rdfs:label ?label .
                    }
                    """;
            List<Map<String, String>> rows = backendService.getObdaHandler().executeAboxQuery(sparql);
            int matched = 0;
            for (Map<String, String> row : rows) {
                String label = row.get("label");
                if (label == null || label.isBlank()) {
                    continue;
                }
                String name = label.contains(".") ? label.substring(label.indexOf('.') + 1) : label;
                if (NAME2PAIR.containsKey(name)) {
                    matched++;
                }
            }
            log.info("本体域知识（OBDA）：六十四卦序列条目 {} 条，与内置名录匹配 {} 条", rows.size(), matched);

            // B2：本体八卦域知识（五行/方位/先天数）与内置表逐例比对
            if (ontologyDomainReady) {
                int mism = 0;
                StringBuilder detail = new StringBuilder();
                for (int i = 1; i <= 8; i++) {
                    String name = TRI_NAME[i];
                    String oFive = ontoTrigramFive.get(name);
                    String oDir = ontoTrigramDir.get(name);
                    Integer oNum = ontoTrigramNumber.get(name);
                    if (!TRI_FIVE[i].equals(oFive) || !TRI_DIR[i].equals(oDir)
                            || oNum == null || oNum != i) {
                        mism++;
                        detail.append('[').append(name).append(" 内置(数").append(i).append('/')
                                .append(TRI_FIVE[i]).append('/').append(TRI_DIR[i]).append(")")
                                .append(" 本体(数").append(oNum).append('/').append(oFive).append('/').append(oDir)
                                .append(")] ");
                    }
                }
                if (mism == 0) {
                    log.info("本体域知识（OBDA）：八卦先天数/五行/方位与内置表逐例一致（8/8）");
                } else {
                    log.warn("⚠️ 本体域知识与内置表不一致 {} 处：{}", mism, detail.toString().trim());
                }
            }
        } catch (Exception e) {
            log.warn("⚠️ 本体六十四卦名录互校跳过（不影响起卦运算）：{}", e.getMessage());
        }
    }

    // ==========================================================================
    // 节点 1：心法戒律校验（四不占）
    // ==========================================================================
    @JobWorker(type = "preceptCheck", autoComplete = false)
    public void handlePreceptCheck(final ActivatedJob job, final JobClient client) {
        try {
            Map<String, Object> vars = job.getVariablesAsMap();
            String subject = str(vars.get("subject"));
            boolean promise = Boolean.TRUE.equals(vars.get("preceptPromise"));
            // A8：不义不占落地——由前端承诺带入（preceptRighteous 缺省为 true，仅显式 false 时触发）；
            //     「不义」属主观道义判断，交由前端/问占者自陈，后端不擅断。
            boolean righteous = !Boolean.FALSE.equals(vars.get("preceptRighteous"));

            String triggered = null;
            if (subject == null || subject.isBlank()) {
                triggered = "无事不占";
            } else if (!promise) {
                triggered = "不诚不占";
            } else if (!righteous) {
                triggered = "不义不占";
            } else {
                if (SESSION_SUBJECTS.size() > SESSION_SUBJECTS_LIMIT) {
                    SESSION_SUBJECTS.clear(); // C6：防无界增长
                }
                if (!SESSION_SUBJECTS.add(subject.trim())) {
                    triggered = "重卦不占";
                }
            }
            boolean pass = (triggered == null);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("pass", pass);
            result.put("triggered", triggered == null ? "" : triggered);

            completeResult(client, job, result);
            log.info("✅ preceptCheck | subject={} | pass={} | triggered={}", subject, pass, triggered);
        } catch (Exception e) {
            fail(job, client, "PRECEPT_CHECK_FAILED", e);
        }
    }

    // ==========================================================================
    // 节点 2'：返回戒律告警
    // ==========================================================================
    @JobWorker(type = "preceptAlert", autoComplete = false)
    public void handlePreceptAlert(final ActivatedJob job, final JobClient client) {
        try {
            Map<String, Object> vars = job.getVariablesAsMap();
            String triggered = str(vars.get("triggeredPrecept"));
            String alertText = switch (triggered == null ? "" : triggered) {
                case "无事不占" -> "无具体事项不可起卦。";
                case "重卦不占" -> "一事一卦，反复求卦反失其机。";
                case "不诚不占" -> "意念不纯，请诚心再占。";
                case "不义不占" -> "此事悖于道义，不可占。";
                default -> "心不诚、事不明，未可轻占；请正心诚意而后再问。";
            };
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("alertText", alertText);
            completeResult(client, job, result);
            log.info("✅ preceptAlert | triggered={} | alert={}", triggered, alertText);
        } catch (Exception e) {
            fail(job, client, "PRECEPT_ALERT_FAILED", e);
        }
    }

    // ==========================================================================
    // 节点 2：输入解析与起卦法路由
    // ==========================================================================
    @JobWorker(type = "routeDivination", autoComplete = false)
    public void handleRouteDivination(final ActivatedJob job, final JobClient client) {
        try {
            Map<String, Object> vars = job.getVariablesAsMap();
            String method = str(vars.get("divinationMethod"));
            boolean isCharacter = "字占".equals(method);
            String system = isInnateSystem(method) ? "先天" : "后天";
            String rawNumber = isCharacter ? "0|0|0" : encodeStartNumbers(method, vars);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("isCharacter", isCharacter);
            result.put("system", system);
            result.put("rawNumber", rawNumber);
            completeResult(client, job, result);
            log.info("✅ routeDivination | method={} | system={} | isCharacter={} | rawNumber={}",
                    method, system, isCharacter, rawNumber);
        } catch (Exception e) {
            fail(job, client, "ROUTE_DIVINATION_FAILED", e);
        }
    }

    // ==========================================================================
    // 节点 2-字占：字占细分
    // ==========================================================================
    @JobWorker(type = "characterSplit", autoComplete = false)
    public void handleCharacterSplit(final ActivatedJob job, final JobClient client) {
        try {
            Map<String, Object> vars = job.getVariablesAsMap();
            String text = str(vars.get("text"));
            int[] upLow = splitCharacters(text == null ? "" : text);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("upperNumber", upLow[0]);
            result.put("lowerNumber", upLow[1]);
            completeResult(client, job, result);
            log.info("✅ characterSplit | text={} | upper={} | lower={}", text, upLow[0], upLow[1]);
        } catch (Exception e) {
            fail(job, client, "CHARACTER_SPLIT_FAILED", e);
        }
    }

    // ==========================================================================
    // 节点 3：起卦成卦（卦以八除、爻以六除）
    // ==========================================================================
    @JobWorker(type = "castHexagram", autoComplete = false)
    public void handleCastHexagram(final ActivatedJob job, final JobClient client) {
        try {
            Map<String, Object> vars = job.getVariablesAsMap();
            boolean isChar = Boolean.TRUE.equals(vars.get("isCharacter"));

            int up, low, moving;
            if (isChar) {
                up = intv(vars.get("upperNumber"));
                low = intv(vars.get("lowerNumber"));
                // A1：字占动爻从通用总则「取爻当以时加之」（卷之一·爻以六除篇末），
                //     动爻 =（上数＋下数＋时数）÷6 取余。十一字以上虽有「合二卦总数取爻」之变体，
                //     本流程统一从「加时」总则（与 BPMN Task_Cast「取爻加时」契约一致）。
                moving = up + low + hourNum(str(vars.get("hourZhi")));
            } else {
                int[] raw = decodeStartNumbers(str(vars.get("rawNumber")));
                up = raw[0];
                low = raw[1];
                moving = raw[2];
            }

            int upperTri = mod8(up);
            int lowerTri = mod8(low);
            int movingLine = mod6(moving);
            String originalHexagram = HEX[upperTri][lowerTri];

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("originalHexagram", originalHexagram);
            result.put("movingLinePosition", movingLine);
            completeResult(client, job, result);
            log.info("✅ castHexagram | up={}({}) low={}({}) moving={} → 本卦={} 动爻={}",
                    up, TRI_NAME[upperTri], low, TRI_NAME[lowerTri], moving, originalHexagram, movingLine);
        } catch (Exception e) {
            fail(job, client, "CAST_HEXAGRAM_FAILED", e);
        }
    }

    // ==========================================================================
    // 节点 4：定体用（动者为用、静者为体）
    // ==========================================================================
    @JobWorker(type = "determineBodyUse", autoComplete = false)
    public void handleDetermineBodyUse(final ActivatedJob job, final JobClient client) {
        try {
            Map<String, Object> vars = job.getVariablesAsMap();
            String name = str(vars.get("originalHexagram"));
            int moving = intv(vars.get("movingLinePosition"));
            int[] pair = pairOf(name);
            int upper = pair[0];
            int lower = pair[1];

            int bodyTri, useTri;
            if (moving <= 3) { // 动爻在下卦 → 下用上体
                useTri = lower;
                bodyTri = upper;
            } else {           // 动爻在上卦 → 上用下体
                useTri = upper;
                bodyTri = lower;
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("bodyTrigram", TRI_NAME[bodyTri]);
            result.put("useTrigram", TRI_NAME[useTri]);
            completeResult(client, job, result);
            log.info("✅ determineBodyUse | 本卦={} 动爻={} → 体={} 用={}", name, moving, TRI_NAME[bodyTri], TRI_NAME[useTri]);
        } catch (Exception e) {
            fail(job, client, "DETERMINE_BODY_USE_FAILED", e);
        }
    }

    // ==========================================================================
    // 节点 5：察互卦（取中间四爻）
    // ==========================================================================
    @JobWorker(type = "deriveMutualHexagram", autoComplete = false)
    public void handleDeriveMutualHexagram(final ActivatedJob job, final JobClient client) {
        try {
            Map<String, Object> vars = job.getVariablesAsMap();
            String name = str(vars.get("originalHexagram"));
            int[] lines = reconstructLines(name);
            int[] pair = pairOf(name);
            String mutual;
            if (pair[0] == pair[1] && (pair[0] == 1 || pair[0] == 8)) {
                // 乾坤无互，互其变卦（纯阳/纯阴，取变卦为互）。
                // A10：此系本应用约定（卷之一「互卦止用八卦」附「又云：乾坤无互，互其变卦」），
                //      与「乾互乾、坤互坤」流派不同，故在此显式标注所据。
                int moving = intv(vars.get("movingLinePosition"));
                int[] changed = flip(lines, moving);
                mutual = composeHexagram(changed);
            } else {
                int mUp = trigramOfBits(lines[2], lines[3], lines[4]);
                int mLow = trigramOfBits(lines[1], lines[2], lines[3]);
                mutual = HEX[mUp][mLow];
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("mutualHexagram", mutual);
            completeResult(client, job, result);
            log.info("✅ deriveMutualHexagram | 本卦={} → 互卦={}", name, mutual);
        } catch (Exception e) {
            fail(job, client, "DERIVE_MUTUAL_FAILED", e);
        }
    }

    // ==========================================================================
    // 节点 6：观变卦（动爻阴阳反转）
    // ==========================================================================
    @JobWorker(type = "deriveChangedHexagram", autoComplete = false)
    public void handleDeriveChangedHexagram(final ActivatedJob job, final JobClient client) {
        try {
            Map<String, Object> vars = job.getVariablesAsMap();
            String name = str(vars.get("originalHexagram"));
            int moving = intv(vars.get("movingLinePosition"));
            int[] changed = flip(reconstructLines(name), moving);
            String changedName = composeHexagram(changed);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("changedHexagram", changedName);
            completeResult(client, job, result);
            log.info("✅ deriveChangedHexagram | 本卦={} 动爻={} → 变卦={}", name, moving, changedName);
        } catch (Exception e) {
            fail(job, client, "DERIVE_CHANGED_FAILED", e);
        }
    }

    // ==========================================================================
    // 节点 7：五行生克分析（体用 5 级）
    // ==========================================================================
    @JobWorker(type = "analyzeShengKe", autoComplete = false)
    public void handleAnalyzeShengKe(final ActivatedJob job, final JobClient client) {
        try {
            Map<String, Object> vars = job.getVariablesAsMap();
            String body = str(vars.get("bodyTrigram"));
            String use = str(vars.get("useTrigram"));
            String pBody = fivePhaseOf(body);
            String pUse = fivePhaseOf(use);

            int level = shengKeLevel(pBody, pUse);
            // C7：五级标签与评分口径统一（大吉/吉/平/凶/大凶），与 judgeAuspiciousness 完全一致。
            String typeText = shengKeName(level) + "（" + shengKeLabel(level) + "）";

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("shengKeType", level + "·" + typeText);
            completeResult(client, job, result);
            log.info("✅ analyzeShengKe | 体={}({}) 用={}({}) → {}级 {}",
                    TRI_NAME[triNum(body)], pBody, TRI_NAME[triNum(use)], pUse, level, typeText);
        } catch (Exception e) {
            fail(job, client, "ANALYZE_SHENGKE_FAILED", e);
        }
    }

    // ==========================================================================
    // 节点 8：卦气旺衰分析
    // ==========================================================================
    @JobWorker(type = "analyzeVitality", autoComplete = false)
    public void handleAnalyzeVitality(final ActivatedJob job, final JobClient client) {
        try {
            Map<String, Object> vars = job.getVariablesAsMap();
            String season = str(vars.get("season"));
            if (season == null || season.isBlank()) {
                season = seasonFromLunarMonth(intv(vars.get("lunarMonth")));
            }
            String body = str(vars.get("bodyTrigram"));
            if (body == null || body.isBlank()) {
                // 兜底：由本卦 + 动爻推体卦
                String name = str(vars.get("originalHexagram"));
                int[] pair = pairOf(name);
                int moving = intv(vars.get("movingLinePosition"));
                int bodyTri = (moving <= 3) ? pair[0] : pair[1];
                body = TRI_NAME[bodyTri];
            }
            String pBody = fivePhaseOf(body);
            String vitality = vitalityOf(pBody, season);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("bodyVitality", vitality);
            completeResult(client, job, result);
            log.info("✅ analyzeVitality | 体={}({}) 季={} → {}", body, pBody, season, vitality);
        } catch (Exception e) {
            fail(job, client, "ANALYZE_VITALITY_FAILED", e);
        }
    }

    // ==========================================================================
    // 节点 9：万物类象取象
    // ==========================================================================
    @JobWorker(type = "extractImageSymbol", autoComplete = false)
    public void handleExtractImageSymbol(final ActivatedJob job, final JobClient client) {
        try {
            Map<String, Object> vars = job.getVariablesAsMap();
            String name = str(vars.get("originalHexagram"));
            String mutual = str(vars.get("mutualHexagram"));
            String changed = str(vars.get("changedHexagram"));
            String category = str(vars.get("subjectCategory"));
            String body = str(vars.get("bodyTrigram"));
            String use = str(vars.get("useTrigram"));

            String text = imageSymbolText(category, name, mutual, changed, body, use);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("imageInterpretation", text);
            completeResult(client, job, result);
            log.info("✅ extractImageSymbol | 本卦={} 类别={} → 核心类象已筛选（二三象）", name, category);
        } catch (Exception e) {
            fail(job, client, "EXTRACT_IMAGE_SYMBOL_FAILED", e);
        }
    }

    // ==========================================================================
    // 节点 10：分类占断
    // ==========================================================================
    @JobWorker(type = "categorizeDivination", autoComplete = false)
    public void handleCategorizeDivination(final ActivatedJob job, final JobClient client) {
        try {
            Map<String, Object> vars = job.getVariablesAsMap();
            String category = str(vars.get("subjectCategory"));
            String name = str(vars.get("originalHexagram"));
            String shengKe = str(vars.get("shengKeType"));
            String verdict = categoryVerdict(category, name, shengKe);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("categoryVerdict", verdict);
            completeResult(client, job, result);
            log.info("✅ categorizeDivination | 类别={} → {}", category, verdict);
        } catch (Exception e) {
            fail(job, client, "CATEGORIZE_DIVINATION_FAILED", e);
        }
    }

    // ==========================================================================
    // 节点 11：参外应（三要十应）
    // ==========================================================================
    @JobWorker(type = "analyzeOmen", autoComplete = false)
    public void handleAnalyzeOmen(final ActivatedJob job, final JobClient client) {
        try {
            Map<String, Object> vars = job.getVariablesAsMap();
            String omenDesc = str(vars.get("omenDesc"));
            String verdict = "";
            if (omenDesc != null && !omenDesc.isBlank()) {
                int omenTri = omenToTrigram(omenDesc);
                String body = str(vars.get("bodyTrigram"));
                String pBody = fivePhaseOf(body);
                String pOmen = fivePhaseOf(TRI_NAME[omenTri]);
                String tone;
                if (pOmen.equals(pBody)) {
                    tone = "外应与体比和，内外相协，事顺";
                } else if (generates(pOmen, pBody)) {
                    tone = "外应生体，主有贵人相助、喜事临近";
                } else if (overcomes(pOmen, pBody)) {
                    tone = "外应克体，主有阻隔忧患，宜谨慎";
                } else {
                    tone = "外应与体相耗，主事有所费";
                }
                // A9：区分十应类别与动/静象；外应之动象比卦中固有之象更直接、更紧迫，优先取用。
                String tenKind = tenResponseCategory(omenDesc);
                boolean movingOmen = isMovingOmen(omenDesc);
                verdict = "三要十应参断：〔十应·" + tenKind + "应〕〔" + (movingOmen ? "动象" : "静象") + "〕"
                        + tone + "（见「" + omenDesc + "」→取象" + TRI_NAME[omenTri] + "）。"
                        + (movingOmen ? "此为动象，临事最急，优先取用；" : "此为静象，为背景之参；")
                        + "卦象为体、外应为用，二者合参方得全貌。";
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("omenVerdict", verdict);
            completeResult(client, job, result);
            log.info("✅ analyzeOmen | omenDesc={} → verdict={}", omenDesc, verdict);
        } catch (Exception e) {
            fail(job, client, "ANALYZE_OMEN_FAILED", e);
        }
    }

    // ==========================================================================
    // 节点 12：断吉凶·以理统数
    // ==========================================================================
    @JobWorker(type = "judgeAuspiciousness", autoComplete = false)
    public void handleJudgeAuspiciousness(final ActivatedJob job, final JobClient client) {
        try {
            Map<String, Object> vars = job.getVariablesAsMap();
            int shengKeLevel = parseShengKeLevel(str(vars.get("shengKeType")));
            String vitality = str(vars.get("bodyVitality"));
            String omen = str(vars.get("omenVerdict"));
            String body = str(vars.get("bodyTrigram"));
            String use = str(vars.get("useTrigram"));
            String mutual = str(vars.get("mutualHexagram"));
            String changed = str(vars.get("changedHexagram"));

            // 基准：用生体→大吉、比和→吉、体克用→平、体生用→凶、用克体→大凶（与 analyzeShengKe 统一口径）
            int score = switch (shengKeLevel) {
                case 1 -> 5;   // 用生体·大吉
                case 2 -> 4;   // 体用比和·吉
                case 3 -> 3;   // 体克用·平
                case 4 -> 2;   // 体生用·凶
                case 5 -> 1;   // 用克体·大凶
                default -> 3;
            };

            // A5：纳入互卦、变卦对体之生克（设计 R8「变卦克体→末后不吉」；
            //     「生体之卦宜多、克体之卦宜少」——原著卷之二·体用总诀/体用）。
            String useRel = relationToBody(use, body);
            String mutualRel = relationToBodyHex(mutual, body);
            String changedRel = relationToBodyHex(changed, body);
            int shengBodyCount = 0;
            int keBodyCount = 0;
            for (String rel : new String[]{useRel, mutualRel, changedRel}) {
                if (rel.equals("生体")) {
                    score = Math.min(5, score + 1);
                    shengBodyCount++;
                } else if (rel.equals("比和")) {
                    score = Math.min(5, score + 1);
                    shengBodyCount++;
                } else if (rel.equals("克体")) {
                    score = Math.max(1, score - 1);
                    keBodyCount++;
                } else if (rel.equals("体生")) {
                    score = Math.max(1, score - 1); // 泄气
                }
            }

            if ("旺".equals(vitality) || "相".equals(vitality)) {
                score = Math.min(5, score + 1);
            } else if ("死".equals(vitality)) {
                score = Math.max(1, score - 1);
            }
            if (omen != null && !omen.isBlank()) {
                if (omen.contains("生体") || omen.contains("比和") || omen.contains("喜")) {
                    score = Math.min(5, score + 1);
                } else if (omen.contains("克体") || omen.contains("忧")) {
                    score = Math.max(1, score - 1);
                }
            }

            // R8：变卦克体 → 末后不吉
            String processFlag = "";
            if ("克体".equals(changedRel)) {
                processFlag = "末后不吉（变卦克体）";
            } else if ("生体".equals(changedRel)) {
                processFlag = "末后得助（变卦生体）";
            } else if ("比和".equals(changedRel)) {
                processFlag = "末后平顺（变卦比和）";
            }
            if (keBodyCount > shengBodyCount) {
                processFlag = (processFlag.isEmpty() ? "" : processFlag + "；") + "克体之卦多，事多阻节";
            } else if (shengBodyCount > keBodyCount) {
                processFlag = (processFlag.isEmpty() ? "" : processFlag + "；") + "生体之卦多，事势转盛";
            }

            String level = scoreToLevel(score);
            String process = "先看体用生克（" + shengKeName(shengKeLevel) + "·" + shengKeLabel(shengKeLevel)
                    + "）→ 次纳入互变与体之生克（用" + useRel + "、互" + mutualRel + "、变" + changedRel
                    + "：生体之卦 " + shengBodyCount + " 个、克体之卦 " + keBodyCount + " 个）"
                    + "→ 复察卦气旺衰（" + vitality + "）→ 复参外应（"
                    + (omen == null || omen.isBlank() ? "无" : "有") + "），以理统数，定为「" + level + "」。"
                    + (processFlag.isEmpty() ? "" : "过程标记：" + processFlag + "。");

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("auspiciousness", level);
            result.put("processDescription", process);
            result.put("processFlag", processFlag);
            completeResult(client, job, result);
            log.info("✅ judgeAuspiciousness | shengKe={} 互={} 变={} vitality={} → {} | flag={}",
                    shengKeLevel, mutualRel, changedRel, vitality, level, processFlag);
        } catch (Exception e) {
            fail(job, client, "JUDGE_AUSPICIOUSNESS_FAILED", e);
        }
    }

    // ==========================================================================
    // 节点 13：定应期
    // ==========================================================================
    @JobWorker(type = "determineResponsePeriod", autoComplete = false)
    public void handleDetermineResponsePeriod(final ActivatedJob job, final JobClient client) {
        try {
            Map<String, Object> vars = job.getVariablesAsMap();
            String auspiciousness = str(vars.get("auspiciousness"));
            String motion = str(vars.get("motion"));
            int[] pair = pairOf(str(vars.get("originalHexagram")));
            int total = pair[0] + pair[1]; // 全卦之数（先天卦数之和）
            String bodyPhase = fivePhaseOf(str(vars.get("bodyTrigram")));
            String usePhase = fivePhaseOf(str(vars.get("useTrigram")));
            String responsePeriod = responsePeriodOf(total, bodyPhase, usePhase, motion, auspiciousness);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("responsePeriod", responsePeriod);
            completeResult(client, job, result);
            log.info("✅ determineResponsePeriod | 全卦之数={} 体{} 用{} motion={} → {}",
                    total, bodyPhase, usePhase, motion, responsePeriod);
        } catch (Exception e) {
            fail(job, client, "DETERMINE_RESPONSE_FAILED", e);
        }
    }

    // ==========================================================================
    // 节点 14：修身建议生成
    // ==========================================================================
    @JobWorker(type = "generateAdvice", autoComplete = false)
    public void handleGenerateAdvice(final ActivatedJob job, final JobClient client) {
        try {
            Map<String, Object> vars = job.getVariablesAsMap();
            String auspiciousness = str(vars.get("auspiciousness"));
            String advice = switch (auspiciousness == null ? "" : auspiciousness) {
                case "大吉", "吉" -> "吉兆当前，宜守正保泰、戒骄持盈，勿因得而忘形。";
                case "大凶", "凶" -> "凶兆当前，宜积德改行、反求诸己，悔过迁善以转祸为福。";
                default -> "吉凶未定，宜居中持正、谨言慎行，静以待时。";
            };
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("advice", advice);
            completeResult(client, job, result);
            log.info("✅ generateAdvice | auspiciousness={} → {}", auspiciousness, advice);
        } catch (Exception e) {
            fail(job, client, "GENERATE_ADVICE_FAILED", e);
        }
    }

    // ==========================================================================
    // 节点 15：外发告警消息（大吉/大凶）
    // ==========================================================================
    @JobWorker(type = "sendAlert", autoComplete = false)
    public void handleSendAlert(final ActivatedJob job, final JobClient client) {
        RabbitMqHandler mq = null;
        try {
            Map<String, Object> vars = job.getVariablesAsMap();
            String auspiciousness = str(vars.get("auspiciousness"));
            String status;
            if ("大吉".equals(auspiciousness) || "大凶".equals(auspiciousness)) {
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("instanceKey", job.getProcessInstanceKey());
                payload.put("subject", vars.get("subject"));
                payload.put("hexagramName", vars.get("originalHexagram"));
                payload.put("auspiciousness", auspiciousness);
                payload.put("summary", vars.get("processDescription"));
                payload.put("timestamp", java.time.Instant.now().toString());

                mq = new RabbitMqHandler();
                mq.send(alertExchange, alertRoutingKey, payload);
                status = "SENT";
            } else {
                status = "SKIPPED_NON_MAJOR";
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("messageStatus", status);
            completeResult(client, job, result);
            log.info("✅ sendAlert | auspiciousness={} → {}", auspiciousness, status);
        } catch (Exception e) {
            fail(job, client, "SEND_ALERT_FAILED", e);
        } finally {
            if (mq != null) {
                try { mq.destroy(); } catch (Exception ignore) { /* noop */ }
            }
        }
    }

    // ==========================================================================
    // 节点 16：汇总占断结果
    // ==========================================================================
    @JobWorker(type = "aggregateResult", autoComplete = false)
    public void handleAggregateResult(final ActivatedJob job, final JobClient client) {
        try {
            Map<String, Object> vars = job.getVariablesAsMap();
            String subject = str(vars.get("subject"));
            StringBuilder sb = new StringBuilder();
            sb.append("【占断事项】").append(subject == null ? "" : subject).append('\n');
            appendLine(sb, "本卦", vars.get("originalHexagram"));
            appendLine(sb, "体卦", vars.get("bodyTrigram"));
            appendLine(sb, "用卦", vars.get("useTrigram"));
            appendLine(sb, "互卦", vars.get("mutualHexagram"));
            appendLine(sb, "变卦", vars.get("changedHexagram"));
            appendLine(sb, "体用生克", vars.get("shengKeType"));
            appendLine(sb, "卦气旺衰", vars.get("bodyVitality"));
            appendLine(sb, "万物类象", vars.get("imageInterpretation"));
            appendLine(sb, "分类占断", vars.get("categoryVerdict"));
            appendLine(sb, "外应解读", vars.get("omenVerdict"));
            appendLine(sb, "过程描述", vars.get("processDescription"));
            appendLine(sb, "应期", vars.get("responsePeriod"));
            appendLine(sb, "吉凶等级", vars.get("auspiciousness"));
            appendLine(sb, "修身建议", vars.get("advice"));

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("divinationResult", sb.toString());
            completeResult(client, job, result);
            log.info("✅ aggregateResult | subject={} → 占断结果已汇总", subject);
        } catch (Exception e) {
            fail(job, client, "AGGREGATE_RESULT_FAILED", e);
        }
    }

    // ==========================================================================
    // 节点 17：邵康节解读占断结论（送 MaaS，大模型以邵康节身份依原著解析；另附白话文解释）
    // ==========================================================================
    @JobWorker(type = "interpretResult", autoComplete = false)
    public void handleInterpretResult(final ActivatedJob job, final JobClient client) {
        try {
            Map<String, Object> vars = job.getVariablesAsMap();
            String subject = str(vars.get("subject"));
            String conclusion = str(vars.get("divinationResult"));

            String interpretation = "";
            String interpretationPlain = "";
            if (llm != null && llm.isAvailable() && conclusion != null && !conclusion.isBlank()) {
                String user = buildInterpretUserPrompt(subject, vars, conclusion);
                String r = llm.chat(INTERPRET_SYSTEM_PROMPT, user);
                if (r != null && !r.isBlank()) {
                    interpretation = r;
                    String plainUser = buildInterpretPlainUserPrompt(subject, conclusion, interpretation);
                    String p = llm.chat(INTERPRET_PLAIN_SYSTEM_PROMPT, plainUser);
                    if (p != null && !p.isBlank()) {
                        interpretationPlain = p;
                    } else {
                        log.warn("⚠️ interpretResult | 白话文解释生成失败/为空，降级为空串（流程不阻断）| subject={}", subject);
                    }
                } else {
                    log.warn("⚠️ interpretResult | 大模型不可用/失败，降级为空串（流程不阻断）| subject={}", subject);
                }
            } else {
                log.info("⏭️ interpretResult | 大模型未装配/不可用，跳过解读（流程不阻断）| subject={}", subject);
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("aiInterpretation", interpretation);
            result.put("aiInterpretationPlain", interpretationPlain);
            completeResult(client, job, result);
            log.info("✅ interpretResult | subject={} → 邵康节解读已生成（{} 字）· 白话文解释（{} 字）",
                    subject, interpretation.length(), interpretationPlain.length());
        } catch (Exception e) {
            fail(job, client, "INTERPRET_RESULT_FAILED", e);
        }
    }

    // ==========================================================================
    // 节点 18：取周易卦爻辞（本卦卦辞 + 动爻爻辞，原文与解释）
    // ==========================================================================
    @JobWorker(type = "fetchDictum", autoComplete = false)
    public void handleFetchDictum(final ActivatedJob job, final JobClient client) {
        try {
            Map<String, Object> vars = job.getVariablesAsMap();
            String hexagram = str(vars.get("originalHexagram"));
            int moving = intv(vars.get("movingLinePosition"));
            Integer seq = hexagram == null ? null : NAME2SEQ.get(hexagram);

            String hexText = "";
            String hexInterp = "";
            String lineText = "";
            String lineInterp = "";

            if (seq != null && backendService != null && backendService.getObdaHandler() != null) {
                try {
                    OBDAHandler obda = backendService.getObdaHandler();
                    String hexIri = "yijing:Dictum_Hex_" + String.format("%02d", seq);
                    String[] hx = selectDictum(obda, hexIri,
                            "yijing:hexagramDictumText", "yijing:hexagramDictumInterpretation");
                    hexText = hx[0];
                    hexInterp = hx[1];
                    if (moving >= 1 && moving <= 6) {
                        String lineIri = "yijing:LineDictum_" + String.format("%02d_%d", seq, moving);
                        String[] ln = selectDictum(obda, lineIri,
                                "yijing:lineDictumText", "yijing:lineDictumInterpretation");
                        lineText = ln[0];
                        lineInterp = ln[1];
                    }
                } catch (Exception e) {
                    log.warn("⚠️ fetchDictum | 卦爻辞查询失败，降级为空串（流程不阻断）：{}", e.getMessage());
                }
            } else {
                log.warn("⚠️ fetchDictum | OBDA 未就绪或卦名未识别（hexagram={}），卦爻辞降级为空串（流程不阻断）", hexagram);
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("originalHexagramDictumText", hexText);
            result.put("originalHexagramDictumInterpretation", hexInterp);
            result.put("movingLineDictumText", lineText);
            result.put("movingLineDictumInterpretation", lineInterp);
            completeResult(client, job, result);
            log.info("✅ fetchDictum | 本卦={}(seq={}) 动爻={} → 卦辞 {} 字、爻辞 {} 字",
                    hexagram, seq, moving, hexText.length(), lineText.length());
        } catch (Exception e) {
            fail(job, client, "FETCH_DICTUM_FAILED", e);
        }
    }

    /**
     * 经 OBDA 查询某卦辞/爻辞个体的「原文」与「解释」；解释缺失时第二项为空串。
     * 卦辞解释为《周易》彖传 + 大象传，爻辞解释为小象传（逐条经典原文，非编造）。
     */
    private static String[] selectDictum(OBDAHandler obda, String subjectIri, String textProp, String interpProp)
            throws Exception {
        // 逐属性单值查询：Ontop 对「同表双属性（含 OPTIONAL）」的查询重写存在缺陷
        // （报 MinorOntopInternalBugException: unique and known DB term type），故拆为两条独立查询。
        String text = selectDictumValue(obda, subjectIri, textProp);
        String interp = selectDictumValue(obda, subjectIri, interpProp);
        return new String[]{text, interp};
    }

    /** 经 OBDA 查询某主体的单个数据属性值；无结果或缺失返回空串。 */
    private static String selectDictumValue(OBDAHandler obda, String subjectIri, String property)
            throws Exception {
        String sparql = "PREFIX yijing: <http://example.org/plumblossom/yijing/>\n"
                + "SELECT ?v WHERE {\n"
                + "  " + subjectIri + " " + property + " ?v\n"
                + "}";
        List<Map<String, String>> rows = obda.executeAboxQuery(sparql);
        if (rows == null || rows.isEmpty()) {
            return "";
        }
        String v = rows.get(0).get("v");
        return v == null ? "" : v;
    }

    /**
     * 组装「邵康节解读」的用户提示：以占断事项为引、节点 16 汇总的完整占断结论为据，
     * 关键中间变量一并随附，供大模型逐环节解析。
     */
    private String buildInterpretUserPrompt(String subject, Map<String, Object> vars, String conclusion) {
        StringBuilder sb = new StringBuilder();
        sb.append("【占断事项】").append(subject == null ? "" : subject).append('\n');
        sb.append("【占断结论（节点16汇总）】\n").append(conclusion).append('\n');
        sb.append("【关键中间变量（供逐环节解析）】\n");
        appendLine(sb, "本卦", vars.get("originalHexagram"));
        appendLine(sb, "体卦", vars.get("bodyTrigram"));
        appendLine(sb, "用卦", vars.get("useTrigram"));
        appendLine(sb, "互卦", vars.get("mutualHexagram"));
        appendLine(sb, "变卦", vars.get("changedHexagram"));
        appendLine(sb, "体用生克", vars.get("shengKeType"));
        appendLine(sb, "卦气旺衰", vars.get("bodyVitality"));
        appendLine(sb, "吉凶等级", vars.get("auspiciousness"));
        appendLine(sb, "应期", vars.get("responsePeriod"));
        sb.append("\n请据此以邵康节身份详细解析该占断结论，逐条说明依据并引述原著印证，不得编造出处。");
        return sb.toString();
    }

    /**
     * 组装「白话文解释」的用户提示：以占断事项与结论为据，附上已生成的古典解读，
     * 供大模型将其改写为通俗白话。
     */
    private String buildInterpretPlainUserPrompt(String subject, String conclusion, String interpretation) {
        StringBuilder sb = new StringBuilder();
        sb.append("【占断事项】").append(subject == null ? "" : subject).append('\n');
        sb.append("【占断结论（节点16汇总）】\n").append(conclusion).append('\n');
        sb.append("【邵康节古典解读（供你改写为白话）】\n").append(interpretation).append('\n');
        sb.append("\n请据此用通俗白话文解释本次占断，分点讲清各环节含义与对占问者的启示，不得编造出处。");
        return sb.toString();
    }

    // ==========================================================================
    // 起卦法路由与起卦原始数
    // ==========================================================================

    /** 以数起卦（先天）类：未得卦先得数，以数起卦。 */
    private static boolean isInnateSystem(String method) {
        if (method == null) {
            return false;
        }
        return switch (method) {
            case "年月日时起例", "物占数", "声音占", "字占", "丈尺占", "尺寸物占", "一撮金法" -> true;
            default -> false;
        };
    }

    /** 依起卦法类别产出「上数|下数|动数」编码，供节点 3 卦以八除。 */
    private String encodeStartNumbers(String method, Map<String, Object> vars) {
        int h = hourNum(str(vars.get("hourZhi")));
        int up, low, moving;
        switch (method == null ? "" : method) {
            case "年月日时起例" -> {
                int yz = yearBranchNum(intv(vars.get("lunarYear")));
                int total = yz + intv(vars.get("lunarMonth")) + intv(vars.get("lunarDay"));
                up = total;
                low = total + h;
                moving = total + h;
            }
            case "物占数" -> {
                int n = intv(vars.get("objectCount"));
                up = n; low = h; moving = n + h;
            }
            case "声音占" -> {
                int n = intv(vars.get("soundCount"));
                up = n; low = h; moving = n + h;
            }
            case "丈尺占" -> {
                int z = intv(vars.get("zhang"));
                int c = intv(vars.get("chi"));
                up = z; low = c; moving = z + c;
            }
            case "尺寸物占" -> {
                int c2 = intv(vars.get("chi2"));
                int cun = intv(vars.get("cun"));
                up = c2; low = cun; moving = c2 + cun + h;
            }
            case "物卦起例" -> {
                up = xiangToNumber(str(vars.get("objectCategory")));
                low = dirToTrigram(str(vars.get("direction")));
                moving = up + low + h;
            }
            case "占动物" -> {
                up = xiangToNumber(str(vars.get("animalInfo")));
                low = dirFromTextOrDefault(str(vars.get("animalInfo")), h);
                moving = up + low + h;
            }
            case "占静物" -> {
                up = xiangToNumber(str(vars.get("stillInfo")));
                low = h; moving = up + low;
            }
            case "风觉占" -> {
                up = dirToTrigram(str(vars.get("windDirection")));
                low = h; moving = up + low;
            }
            case "鸟占" -> {
                up = xiangToNumber(str(vars.get("birdInfo")));
                low = h; moving = up + low;
            }
            case "听声音占" -> {
                up = xiangToNumber(str(vars.get("heardSound")));
                low = h; moving = up + low;
            }
            case "形物占" -> {
                up = shapeToTrigram(str(vars.get("objectShape")));
                low = h; moving = up + low;
            }
            case "验色占" -> {
                up = colorToTrigram(str(vars.get("objectColor")));
                low = h; moving = up + low;
            }
            case "一撮金法" -> {
                // A2：附录·一撮金本法——「以楷书字，数其笔画…上字为内卦，下字为外卦，
                //     合二字之画共成一卦，即以总数六除取爻」（上字→内卦=下卦、下字→外卦=上卦，
                //     与卷之一二字占「以一字为上卦」之内外分配相反，见《流程设计》§7 待裁定项10）。
                String t = str(vars.get("yicuoText"));
                char upperChar = (t == null || t.isEmpty()) ? '乾' : t.charAt(0);   // 上字 → 内卦
                char lowerChar = (t == null || t.length() < 2) ? '坤' : t.charAt(1); // 下字 → 外卦
                int nei = stroke(upperChar); // 内卦数
                int wai = stroke(lowerChar); // 外卦数
                low = nei;         // 内卦 = 下卦
                up = wai;          // 外卦 = 上卦
                moving = nei + wai; // 总画数六除取爻（一撮金不加时）
            }
            case "为人占", "自己占" -> {
                String info = str(vars.get("humanInfo"));
                if (info == null) {
                    info = str(vars.get("selfInfo"));
                }
                up = xiangToNumber(info);
                low = h; moving = up + low;
            }
            default -> {
                up = h; low = h; moving = 2 * h;
            }
        }
        return up + "|" + low + "|" + moving;
    }

    private static int[] decodeStartNumbers(String raw) {
        int up = 0, low = 0, moving = 0;
        if (raw != null && raw.contains("|")) {
            String[] p = raw.split("\\|");
            try {
                up = Integer.parseInt(p[0].trim());
                low = Integer.parseInt(p[1].trim());
                moving = Integer.parseInt(p[2].trim());
            } catch (Exception ignore) { /* 保持 0 */ }
        }
        return new int[]{up, low, moving};
    }

    /** 字占细分：按字数分子法产出上卦数、下卦数。 */
    private static int[] splitCharacters(String text) {
        int n = text.length();
        if (n == 0) {
            return new int[]{1, 1};
        }
        int[] s = new int[n];
        for (int i = 0; i < n; i++) {
            s[i] = stroke(text.charAt(i));
        }
        switch (n) {
            case 1:
                return new int[]{s[0], s[0]};
            case 2:
                return new int[]{s[0], s[1]};
            case 3:
                return new int[]{s[0], s[1] + s[2]};
            case 4:
                return new int[]{s[0] + s[1], s[2] + s[3]};
            case 5:
                return new int[]{s[0] + s[1], s[2] + s[3] + s[4]};
            case 6:
                return new int[]{s[0] + s[1] + s[2], s[3] + s[4] + s[5]};
            case 7:
                return new int[]{s[0] + s[1] + s[2], s[3] + s[4] + s[5] + s[6]};
            case 8:
                return new int[]{s[0] + s[1] + s[2] + s[3], s[4] + s[5] + s[6] + s[7]};
            case 9:
                return new int[]{s[0] + s[1] + s[2] + s[3], s[4] + s[5] + s[6] + s[7] + s[8]};
            case 10:
                return new int[]{s[0] + s[1] + s[2] + s[3] + s[4], s[5] + s[6] + s[7] + s[8] + s[9]};
            default: {
                int half = n / 2;
                int up = 0, low = 0;
                for (int i = 0; i < half; i++) {
                    up += s[i];
                }
                for (int i = half; i < n; i++) {
                    low += s[i];
                }
                return new int[]{up, low};
            }
        }
    }

    // ==========================================================================
    // 卦象工具
    // ==========================================================================

    /** 六爻（下→上，1=阳 0=阴）重建自卦名（经内置 R15 反查表）。 */
    private static int[] reconstructLines(String name) {
        int[] pair = pairOf(name);
        int[] lowBits = bits(pair[1]);
        int[] upBits = bits(pair[0]);
        return new int[]{lowBits[0], lowBits[1], lowBits[2], upBits[0], upBits[1], upBits[2]};
    }

    private static int[] flip(int[] lines, int moving) {
        int idx = Math.max(1, Math.min(6, moving)) - 1;
        int[] out = lines.clone();
        out[idx] = 1 - out[idx];
        return out;
    }

    private static String composeHexagram(int[] lines) {
        int lower = trigramOfBits(lines[0], lines[1], lines[2]);
        int upper = trigramOfBits(lines[3], lines[4], lines[5]);
        return HEX[upper][lower];
    }

    private static int[] pairOf(String name) {
        int[] p = NAME2PAIR.get(name);
        return p == null ? new int[]{1, 1} : p;
    }

    private static int[] bits(int t) {
        return switch (t) {
            case 1 -> new int[]{1, 1, 1}; // 乾
            case 2 -> new int[]{1, 1, 0}; // 兑
            case 3 -> new int[]{1, 0, 1}; // 离
            case 4 -> new int[]{1, 0, 0}; // 震
            case 5 -> new int[]{0, 1, 1}; // 巽
            case 6 -> new int[]{0, 1, 0}; // 坎
            case 7 -> new int[]{0, 0, 1}; // 艮
            default -> new int[]{0, 0, 0}; // 坤
        };
    }

    private static int trigramOfBits(int b0, int b1, int b2) {
        int mask = b0 | (b1 << 1) | (b2 << 2);
        return MASK2TRI.getOrDefault(mask, 8);
    }

    /** 八卦名 → 先天卦数（C6：去掉原冗余的重复循环）。 */
    private static int triNum(String name) {
        if (name != null) {
            for (int i = 1; i <= 8; i++) {
                if (TRI_NAME[i].equals(name)) {
                    return i;
                }
            }
        }
        return 1;
    }

    private static int mod8(int n) {
        return ((n - 1) % 8 + 8) % 8 + 1;
    }

    private static int mod6(int n) {
        return ((n - 1) % 6 + 6) % 6 + 1;
    }

    // ==========================================================================
    // 五行与卦气
    // ==========================================================================

    private static boolean generates(String from, String to) {
        return switch (from) {
            case "金" -> "水".equals(to);
            case "水" -> "木".equals(to);
            case "木" -> "火".equals(to);
            case "火" -> "土".equals(to);
            case "土" -> "金".equals(to);
            default -> false;
        };
    }

    private static boolean overcomes(String from, String to) {
        return switch (from) {
            case "金" -> "木".equals(to);
            case "木" -> "土".equals(to);
            case "土" -> "水".equals(to);
            case "水" -> "火".equals(to);
            case "火" -> "金".equals(to);
            default -> false;
        };
    }

    /** 当季五行：春木/夏火/秋金/冬水/四季土。 */
    private static String seasonFive(String season) {
        return switch (season == null ? "" : season) {
            case "春" -> "木";
            case "夏" -> "火";
            case "秋" -> "金";
            case "冬" -> "水";
            default -> "土";
        };
    }

    /** 卦气旺衰：旺（当令）/相（当令所生）/休（生当令）/囚（克当令）/死（当令所克）。 */
    private static String vitalityOf(String phase, String season) {
        String s = seasonFive(season);
        if (phase.equals(s)) {
            return "旺";
        }
        if (generates(s, phase)) {
            return "相";
        }
        if (generates(phase, s)) {
            return "休";
        }
        if (overcomes(phase, s)) {
            return "囚";
        }
        return "死";
    }

    /**
     * 由农历月推季节（A6：依原著「寅卯月木旺、巳午月火旺、申酉月金旺、亥子月水旺、
     * 辰戌丑未月土旺」，辰戌丑未（三/六/九/十二月）为「四季」土旺之月，此前该分支永不触发）。
     */
    private static String seasonFromLunarMonth(int month) {
        if (month < 1 || month > 12) {
            return "四季";
        }
        int m = ((month - 1) % 12) + 1;
        return switch (m) {
            case 1, 2 -> "春";     // 寅卯
            case 4, 5 -> "夏";     // 巳午
            case 7, 8 -> "秋";     // 申酉
            case 10, 11 -> "冬";   // 亥子
            default -> "四季";     // 3/6/9/12 月：辰戌丑未，土旺四季
        };
    }

    // ==========================================================================
    // 地支/方位/象数
    // ==========================================================================

    private static final String[] ZHI = {"子", "丑", "寅", "卯", "辰", "巳", "午", "未", "申", "酉", "戌", "亥"};

    private static int hourNum(String hourZhi) {
        if (hourZhi != null) {
            for (int i = 0; i < ZHI.length; i++) {
                if (ZHI[i].equals(hourZhi)) {
                    return i + 1;
                }
            }
        }
        return 1;
    }

    /** 年支数（子1…亥12），由农历年推（year-4 mod 12）。 */
    private static int yearBranchNum(int lunarYear) {
        if (lunarYear <= 0) {
            return 1;
        }
        return ((lunarYear - 4) % 12 + 12) % 12 + 1;
    }

    /**
     * 后天八卦方位 → 先天卦数。B3：本体域知识就绪时优先取本体 {@code core:hasDirection} 反查，
     * 否则回退内置映射（乾西北/兑西/离南/震东/巽东南/坎北/艮东北/坤西南）。
     */
    private int dirToTrigram(String dir) {
        String d = dir == null ? "" : dir;
        if (ontologyDomainReady && !d.isBlank()) {
            for (Map.Entry<String, String> e : ontoDirToTrigram.entrySet()) { // 先双字方位
                if (e.getKey().length() >= 2 && d.contains(e.getKey())) {
                    return trigramNumberOf(e.getValue());
                }
            }
            for (Map.Entry<String, String> e : ontoDirToTrigram.entrySet()) {
                if (d.contains(e.getKey())) {
                    return trigramNumberOf(e.getValue());
                }
            }
        }
        if (d.contains("东南")) return 5;
        if (d.contains("西南")) return 8;
        if (d.contains("东北")) return 7;
        if (d.contains("西北")) return 1;
        if (d.contains("东")) return 4;
        if (d.contains("南")) return 3;
        if (d.contains("西")) return 2;
        if (d.contains("北")) return 6;
        return 1;
    }

    /**
     * C4：由文本取方位卦，无方位则回退（如占动物以时数配下卦）；
     * 显式判定「是否含方位字」，不再倚赖 {@code ||}/{@code &&} 优先级。
     */
    private int dirFromTextOrDefault(String text, int fallback) {
        if (text == null || text.isBlank()) {
            return fallback;
        }
        boolean hasDirection = text.contains("东") || text.contains("南")
                || text.contains("西") || text.contains("北");
        return hasDirection ? dirToTrigram(text) : fallback;
    }

    /**
     * 物象类属 → 卦数（依原著卷之一「八卦万物属类」主象）。
     * C3：以「文本首见之象为准」按显式优先级（卦数 1→8）取象，结果可复现。
     */
    private static int xiangToNumber(String text) {
        if (text == null || text.isBlank()) {
            return 1;
        }
        int bestPos = Integer.MAX_VALUE;
        int bestTri = -1;
        for (int t = 1; t <= 8; t++) {
            String[] kws = TRI_KEYWORDS.get(t);
            if (kws == null) {
                continue;
            }
            for (String kw : kws) {
                int pos = text.indexOf(kw);
                if (pos >= 0 && pos < bestPos) {
                    bestPos = pos;
                    bestTri = t;
                }
            }
        }
        return bestTri == -1 ? 1 : bestTri;
    }

    /** 物形 → 卦数（原著卷之一·形物占）。 */
    private static int shapeToTrigram(String shape) {
        return switch (shape == null ? "" : shape) {
            case "圆" -> 1;
            case "刚" -> 2;
            case "方" -> 8;
            case "柔" -> 5;
            case "仰" -> 4;
            case "覆" -> 7;
            case "长" -> 5;
            case "中刚外柔" -> 6;
            case "内柔外刚", "枯槁", "文彩" -> 3;
            case "破" -> 2;
            default -> 1;
        };
    }

    /** 物色 → 卦数（原著卷之一·验色占）。 */
    private static int colorToTrigram(String color) {
        return switch (color == null ? "" : color) {
            case "青" -> 4;
            case "红紫赤" -> 3;
            case "黄" -> 8;
            case "白" -> 2;
            case "黑" -> 6;
            default -> 1;
        };
    }

    /** 外应描述 → 取象卦（关键词匹配，无命中默认乾）。 */
    private static int omenToTrigram(String desc) {
        return xiangToNumber(desc);
    }

    /** A9：外应 → 十应类目（天时/地理/人事/身体/动物/静物/言语/声色/文字/方卦，依原著三要灵应篇·十应）。 */
    private static String tenResponseCategory(String desc) {
        if (desc == null || desc.isBlank()) {
            return "外";
        }
        if (containsAny(desc, "晴", "雨", "雪", "风", "云", "雷", "日", "月", "阴", "天色", "天时")) {
            return "天时";
        }
        if (containsAny(desc, "山", "水", "河", "桥", "路", "井", "林", "野", "地理", "地")) {
            return "地理";
        }
        if (containsAny(desc, "老人", "少女", "妇人", "男子", "僧", "尼", "童", "人", "官吏", "贵")) {
            return "人品";
        }
        if (containsAny(desc, "头", "手", "足", "身", "目", "耳", "口", "面")) {
            return "身体";
        }
        if (containsAny(desc, "鸟", "雀", "鹊", "鸡", "犬", "牛", "马", "羊", "鱼", "虫", "兽", "禽")) {
            return "动物";
        }
        if (containsAny(desc, "花", "木", "器物", "桌椅", "衣", "屋", "门", "石", "钱", "书")) {
            return "静物";
        }
        if (containsAny(desc, "闻", "语", "言", "呼", "笑", "哭", "叹", "骂", "声")) {
            return "言语";
        }
        if (containsAny(desc, "色", "青", "赤", "黄", "白", "黑", "紫", "五色")) {
            return "声色";
        }
        if (containsAny(desc, "字", "文", "榜", "联", "书", "匾")) {
            return "文字";
        }
        if (containsAny(desc, "东", "南", "西", "北", "方")) {
            return "方卦";
        }
        return "外";
    }

    /** A9：外应是否为「动象」（飞/走/鸣/坠/响/来等）；动象比静象更直接、更紧迫。 */
    private static boolean isMovingOmen(String desc) {
        return containsAny(desc, "飞", "走", "行", "鸣", "叫", "坠", "落", "响", "动", "来", "入", "过", "驰", "奔", "起", "摇");
    }

    private static boolean containsAny(String text, String... keys) {
        for (String k : keys) {
            if (text.contains(k)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 笔画数近似取值。
     *
     * <p>【说明】本期未接入字库/笔画库（待第 12 步数据扩全集或前端接入笔画），
     * 一撮金与一字占所需笔画数暂以「常用字内置表 + 码位取模」近似，
     * 保证起卦可运算；待笔画数据源接入后再精化。
     */
    private static int stroke(char c) {
        switch (c) {
            case '一': return 1; case '二': return 2; case '三': return 3; case '四': return 5;
            case '五': return 4; case '六': return 4; case '七': return 2; case '八': return 2;
            case '九': return 2; case '十': return 2; case '乾': return 11; case '坤': return 8;
            case '震': return 15; case '巽': return 12; case '坎': return 7; case '离': return 10;
            case '艮': return 6; case '兑': return 7;
            default: return Math.floorMod(c, 8) + 1;
        }
    }

    // ==========================================================================
    // 吉凶/应期/分类断语
    // ==========================================================================

    /** 五行生克 → 体用 5 级（1 用生体 / 2 比和 / 3 体克用 / 4 体生用 / 5 用克体）。 */
    private static int shengKeLevel(String pBody, String pUse) {
        if (generates(pUse, pBody)) {
            return 1;
        }
        if (pUse.equals(pBody)) {
            return 2;
        }
        if (overcomes(pBody, pUse)) {
            return 3;
        }
        if (generates(pBody, pUse)) {
            return 4;
        }
        return 5;
    }

    private static String shengKeName(int level) {
        return switch (level) {
            case 1 -> "用生体";
            case 2 -> "体用比和";
            case 3 -> "体克用";
            case 4 -> "体生用";
            case 5 -> "用克体";
            default -> "体用未定";
        };
    }

    /** C7：体用 5 级 → 吉凶标签（与 judgeAuspiciousness 评分口径统一）。 */
    private static String shengKeLabel(int level) {
        return switch (level) {
            case 1 -> "大吉";
            case 2 -> "吉";
            case 3 -> "平";
            case 4 -> "凶";
            case 5 -> "大凶";
            default -> "平";
        };
    }

    private static int parseShengKeLevel(String shengKeType) {
        if (shengKeType != null && !shengKeType.isEmpty()) {
            char c = shengKeType.charAt(0);
            if (c >= '1' && c <= '5') {
                return c - '0';
            }
        }
        return 3;
    }

    private static String scoreToLevel(int score) {
        return switch (Math.max(1, Math.min(5, score))) {
            case 5 -> "大吉";
            case 4 -> "吉";
            case 3 -> "平";
            case 2 -> "凶";
            default -> "大凶";
        };
    }

    /**
     * 某卦（用卦）对体卦的生克关系：生体 / 比和 / 体克 / 体生 / 克体。
     * （B3：五行取自本体域知识，回退内置表。）
     */
    private String relationToBody(String trigramName, String bodyName) {
        String pT = fivePhaseOf(trigramName);
        String pB = fivePhaseOf(bodyName);
        if (pT.equals(pB)) {
            return "比和";
        }
        if (generates(pT, pB)) {
            return "生体";
        }
        if (overcomes(pT, pB)) {
            return "克体";
        }
        if (overcomes(pB, pT)) {
            return "体克";
        }
        return "体生";
    }

    /**
     * 互卦/变卦（重卦）对体之生克：取其上下两卦对体关系中最显著者
     * （优先级 克体 &gt; 生体 &gt; 比和 &gt; 体克 &gt; 体生）。
     */
    private String relationToBodyHex(String hexName, String bodyName) {
        if (hexName == null || hexName.isBlank()) {
            return "比和";
        }
        int[] pair = pairOf(hexName);
        String rUp = relationToBody(TRI_NAME[pair[0]], bodyName);
        String rLow = relationToBody(TRI_NAME[pair[1]], bodyName);
        for (String rank : new String[]{"克体", "生体", "比和", "体克", "体生"}) {
            if (rank.equals(rUp) || rank.equals(rLow)) {
                return rank;
            }
        }
        return "比和";
    }

    /**
     * 应期（A7）：以全卦之数（先天卦数之和）分「近/中/远」，并并列「五行生数」（水1火2木3金4土5）
     * 与卦气二法互参，注明数系以免混淆（生数 ≠ 先天卦数）；依吉凶取吉期/凶期，依动静调迟速。
     */
    private static String responsePeriodOf(int guaTotal, String bodyPhase, String usePhase,
                                           String motion, String auspiciousness) {
        int t = Math.max(1, guaTotal);
        String level;
        String unit;
        if (t <= 6) {
            level = "近";
            unit = "日";
        } else if (t <= 11) {
            level = "中";
            unit = "月";
        } else {
            level = "远";
            unit = "年";
        }
        int low = Math.max(1, t - 3);
        int high = t + 3;
        int shengBody = SHENG_SHU.getOrDefault(bodyPhase == null ? "" : bodyPhase, 5);
        int shengUse = SHENG_SHU.getOrDefault(usePhase == null ? "" : usePhase, 5);
        String motionNote = switch (motion == null ? "" : motion) {
            case "行", "走" -> "行则速，宜就低限";
            case "卧" -> "卧则迟，宜就高限";
            default -> "坐则迟，居中而应";
        };
        boolean bad = auspiciousness != null && auspiciousness.contains("凶");
        String kind = bad ? "凶期（有克体之卦则事败于克体卦气之月日）"
                : "吉期（以生体、比和之年月为吉期）";
        return level + "·约" + low + "~" + high + unit + "内"
                + "【取数系：全卦之数（先天卦数之和＝" + t + "）】；"
                + "参五行生数（体" + bodyPhase + "＝" + shengBody + "、用" + usePhase + "＝" + shengUse + "），"
                + kind + "；" + motionNote + "。";
    }

    /**
     * 分类占断（A4）：依原著卷之二/卷三「逐卦生体克体之应」，为事项类别各立主断依据，
     * 据体用生克 5 级取「吉断」或「凶断」。吉断用于 level 1/2/3，凶断用于 level 4/5。
     */
    private static String categoryVerdict(String category, String hexagram, String shengKe) {
        int level = parseShengKeLevel(shengKe);
        String cat = (category == null || category.isBlank()) ? "所问之事" : category;
        String tone = shengKeName(level) + "·" + shengKeLabel(level);
        String[] pair = CATEGORY_RULE.getOrDefault(cat, new String[]{
                "以体为主、用为所应，参互变综合断之",
                "以体为主、用为所应，参互变综合断之"});
        boolean good = level <= 3;
        String outcome = good ? pair[0] : pair[1];
        return "依「" + cat + "」分项断法（本卦" + hexagram + "）：" + tone + "。断曰：" + outcome
                + "。（体卦为主＝己身之兆，用卦为事＝应事之端，互卦为中间，变卦为末后。）";
    }

    /** 十八类事项分项断语：{吉断(生体/比和/体克用), 凶断(体生用/用克体)}，据原著占例。 */
    private static final Map<String, String[]> CATEGORY_RULE = new LinkedHashMap<>();

    static {
        CATEGORY_RULE.put("天时", new String[]{
                "坎兑主雨、离主晴，卦宜生体比和则晴雨及时、天时顺遂",
                "克体则风雨失时、天时反常，宜顺天应候"});
        CATEGORY_RULE.put("人事", new String[]{
                "体克用诸事亨通，比和为妙，所谋顺遂",
                "比和为妙克为凶，用克体则人事多有阻滞"});
        CATEGORY_RULE.put("家宅", new String[]{
                "体为主，旺相须知进田土，比和则家世安居",
                "生用则耗散财、克体为凶，家宅不宁"});
        CATEGORY_RULE.put("屋舍", new String[]{
                "体旺相、生体比和，则屋宅稳固兴隆",
                "克体则屋宅有损、居之不安"});
        CATEGORY_RULE.put("婚姻", new String[]{
                "体用比和、体克用则婚可成、家道吉",
                "生用必难成，用克体则婚姻不就或多阻"});
        CATEGORY_RULE.put("生产", new String[]{
                "以体为母，体宜生旺不宜衰；生体比和则母子平安",
                "体受克、体衰则产有险，宜谨护"});
        CATEGORY_RULE.put("饮食", new String[]{
                "用生体必知肴馔丰厚，克用比和则和美；坎兑为酒、震为鱼",
                "生用克体则饮食难、席有缺"});
        CATEGORY_RULE.put("求谋", new String[]{
                "比和则称意，体克用谋为可成（惟稍迟）",
                "生用克体则谋事难成、费而无功"});
        CATEGORY_RULE.put("求名", new String[]{
                "克用则名可求，生体比和俱可取",
                "用克体、生用则功名难就"});
        CATEGORY_RULE.put("求财", new String[]{
                "克用曰有财，生体比和俱称意，财可得",
                "生用则破财、用克体则损财或无得"});
        CATEGORY_RULE.put("交易", new String[]{
                "生体及比和，有利必成、交易顺遂",
                "克体生用则交易难成、防有亏损"});
        CATEGORY_RULE.put("出行", new String[]{
                "克用、用生体，所至其方多得意；坎则乘舟、离则旱途",
                "用克体则行有阻、途多险，宜缓行"});
        CATEGORY_RULE.put("行人", new String[]{
                "生体比和则人即至；行人以生体之日为归期",
                "克用必来迟；咸远恒迟升不回，艮阻坎险"});
        CATEGORY_RULE.put("谒见", new String[]{
                "体克用、比和生体，则谒见遂意、得贵人助",
                "用克体则见而不得志、或为所拒"});
        CATEGORY_RULE.put("失物", new String[]{
                "生体比和则物可寻，以变卦方位定寻处：离冶炉南方、坤方器、兑残缺井畔",
                "克体则物难觅，随变卦方位远近推之"});
        CATEGORY_RULE.put("疾病", new String[]{
                "最宜体旺相，克用则易安、药有效；比和凶则有救星",
                "体卦受克为凶兆，宜谨药石；离宜服热、坎服冷、坤土温补"});
        CATEGORY_RULE.put("官讼", new String[]{
                "用宜克、体卦旺相终得理；比和助解最为奇",
                "用克体、体衰则讼不利，宜和解息争"});
        CATEGORY_RULE.put("坟墓", new String[]{
                "比和生体宜葬，克用尤为大吉利；坤则平阳、巽林、乾高、艮山、离人烟",
                "克体则葬地不吉，宜另择"});
    }

    // ==========================================================================
    // 本体域知识访问（B1/B3：本体优先、内置兜底）
    // ==========================================================================

    /** 八卦五行：本体优先，回退内置表。 */
    private String fivePhaseOf(String trigramName) {
        String v = ontoTrigramFive.get(trigramName);
        if (v != null) {
            return v;
        }
        return TRI_FIVE[triNum(trigramName)];
    }

    /** 八卦先天数：本体优先，回退内置表。 */
    private int trigramNumberOf(String trigramName) {
        Integer v = ontoTrigramNumber.get(trigramName);
        if (v != null) {
            return v;
        }
        return triNum(trigramName);
    }

    /** 八卦方位：本体优先，回退内置表。 */
    private String directionOf(String trigramName) {
        String v = ontoTrigramDir.get(trigramName);
        if (v != null) {
            return v;
        }
        return TRI_DIR[triNum(trigramName)];
    }

    private static void putIfInt(Map<String, Integer> map, String key, String value) {
        if (key == null || value == null || value.isBlank()) {
            return;
        }
        try {
            map.put(key.trim(), Integer.parseInt(value.trim()));
        } catch (NumberFormatException ignore) { /* 跳过非整数 */ }
    }

    // ==========================================================================
    // 类象筛选与文本工具
    // ==========================================================================

    /**
     * 万物类象（A3）：按「类象取舍三原则」（紧扣所问之事、呼应体用生克、优先动象应象）
     * 从本/互/变中筛出**核心二三象**，其余旁枝从略，杜绝「象的堆砌」。
     */
    private String imageSymbolText(String category, String name, String mutual, String changed,
                                   String body, String use) {
        int[] pair = pairOf(name);
        String bTri = (body == null || body.isBlank()) ? TRI_NAME[pair[0]] : body;
        String uTri = (use == null || use.isBlank()) ? TRI_NAME[pair[1]] : use;
        String cat = (category == null || category.isBlank()) ? "所问之事" : category;
        String bodySymbol = categorySymbolHint(cat, bTri);

        StringBuilder sb = new StringBuilder();
        sb.append("核心类象（取二三象，紧扣所问「").append(cat).append("」）：");
        sb.append("① 主象——体卦").append(bTri).append("之象「").append(TRI_MAIN[triNum(bTri)]).append("」，")
                .append(bodySymbol).append("；");
        sb.append("② 次象——用卦").append(uTri).append("之象「").append(TRI_MAIN[triNum(uTri)]).append("」，")
                .append("循体用生克取用（用克体则取其权威/压制之象，用生体则取其助益之象），不越生克框架选象；");
        if (mutual != null && !mutual.isBlank()) {
            int[] mp = pairOf(mutual);
            sb.append("③ 动象——互卦").append(mutual).append("「")
                    .append(TRI_MAIN[mp[0]]).append(TRI_MAIN[mp[1]]).append("」为过程之象（优先取用）；");
        }
        if (changed != null && !changed.isBlank()) {
            int[] cp = pairOf(changed);
            sb.append("④ 应象——变卦").append(changed).append("「")
                    .append(TRI_MAIN[cp[0]]).append(TRI_MAIN[cp[1]]).append("」为末后之象（优先取用）；");
        }
        sb.append("卦象为体、外应为用，二者合参；无关旁枝之象，纵贴切亦舍之，勿作堆砌。");
        return sb.toString();
    }

    /** 紧扣所问之事：为常见事项类别提示体卦应取之象（原著卷之四/卷之五·八卦取象）。 */
    private static String categorySymbolHint(String category, String bodyTri) {
        String main = TRI_MAIN[triNum(bodyTri)];
        return switch (category) {
            case "疾病" -> "问病取脏腑之象（" + bodyTri + "主" + main + "之属，如乾首肺、坤腹脾）";
            case "求财", "交易" -> "问财取金玉资本之象（" + bodyTri + "主" + main + "之财类）";
            case "家宅", "屋舍", "坟墓" -> "问宅取" + bodyTri + "所主之方位与人事（如乾老父西北、坤母西南）";
            case "婚姻" -> "问婚取" + bodyTri + "所主男女长幼之象（乾父震长男坎中男艮少男／坤母巽长女离中女兑少女）";
            case "官讼" -> "问讼取" + bodyTri + "之官贵、刑罚、文书之象";
            case "出行", "行人" -> "问行取" + bodyTri + "之车马、道路、方位之象";
            case "失物" -> "问失物取" + bodyTri + "之器物、方位之象以定寻处";
            case "谒见" -> "问谒见取" + bodyTri + "之贵人、尊长之象";
            default -> "取体卦" + bodyTri + "「" + main + "」之象以应所问";
        };
    }

    // ==========================================================================
    // 通用工具
    // ==========================================================================

    private void completeResult(JobClient client, ActivatedJob job, Map<String, Object> result) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("result", result);
        client.newCompleteCommand(job.getKey()).variables(out).send().join();
    }

    private void fail(ActivatedJob job, JobClient client, String errorCode, Exception e) {
        log.error("❌ JobWorker 失败 | type={} | jobKey={}", errorCode, job.getKey(), e);
        client.newThrowErrorCommand(job.getKey())
                .errorCode(errorCode)
                .errorMessage(e.getMessage())
                .send().join();
    }

    private static void appendLine(StringBuilder sb, String label, Object value) {
        if (value != null && !String.valueOf(value).isBlank()) {
            sb.append(label).append("：").append(value).append('\n');
        }
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static int intv(Object o) {
        if (o instanceof Number n) {
            return n.intValue();
        }
        if (o != null) {
            try {
                return Integer.parseInt(String.valueOf(o).trim());
            } catch (NumberFormatException ignore) { /* 返回 0 */ }
        }
        return 0;
    }
}
