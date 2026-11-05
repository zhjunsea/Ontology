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
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
            + "解析时务必结合所问之事与求测者自身（体卦、五行、卦气旺衰）的具体情况，因事、因体、因用而断，"
            + "讲明此事对此人的具体影响与吉凶，不作泛泛之谈或套话；"
            + "并须将破解方案（扶体/抑用/通关及方位·时令·色·物）转译为针对该事该主体的具体可行建议，"
            + "其中的方位·时令·色·物皆为取象喻示，须贴合现实场景转述为可操作做法，不可照抄术语或宣扬迷信。"
            + "使用规范 Markdown 输出。";

    /** 节点 17「邵康节解读」之白话文解释的系统提示：把古典解析转写为通俗现代白话，术语随文浅释。 */
    private static final String INTERPRET_PLAIN_SYSTEM_PROMPT =
            "你是《梅花易数》的白话讲解员。请把给定的「占断事项」「占断结论」以及邵康节式的古典解读，"
            + "用通俗易懂的现代白话文重新讲解一遍：分点讲清本卦/互卦/变卦、体卦与用卦、五行生克、卦气旺衰、"
            + "万物类象、外应、吉凶等级、应期、修身建议各自是什么含义、对问占者意味着什么；"
            + "遇文言与术语随即用一句话浅释，避免堆砌名词。"
            + "只作白话讲解与转述，严禁编造原著中并不存在的篇目、条文或占例。"
            + "面向问卦者本人：务必结合其所问之事与其自身（体卦、五行、旺衰）情况讲解，"
            + "并把破解方案讲成他针对这件事可以照着做的通俗建议（方位·时令·色·物只是取象喻示，勿宣扬迷信）。"
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
    /** 本体载入的四不占告示语：戒律名 → 告示文本（div:preceptAdmonition）。 */
    private volatile Map<String, String> ontoPreceptAdmonition = new LinkedHashMap<>();
    /** 本体载入的吉凶等级：名（大吉…大凶）→ 数值（5…1）（jud:Auspiciousness）。 */
    private volatile Map<String, Integer> ontoAuspiciousness = new LinkedHashMap<>();
    /** 吉凶评分权重：评分项键 → 分值/权重（jud:JudgeWeight；本体/DB 优先、{@link #JUDGE_W_BUILTIN} 兜底）。 */
    private volatile Map<String, Integer> ontoJudgeWeight = new LinkedHashMap<>();
    /** 内置兜底：吉凶评分权重（批次4；与 data.sql `judge_weight` 逐字同源）。 */
    private static final Map<String, Integer> JUDGE_W_BUILTIN = Map.ofEntries(
            Map.entry("base_1", 5), Map.entry("base_2", 4), Map.entry("base_3", 3),
            Map.entry("base_4", 2), Map.entry("base_5", 1),
            Map.entry("sheng_body", 1), Map.entry("bihe_body", 1),
            Map.entry("ke_body", -1), Map.entry("tishi_body", -1),
            Map.entry("vitality_wang_xiang", 1), Map.entry("vitality_si", -1),
            Map.entry("omen_good", 1), Map.entry("omen_bad", -1),
            Map.entry("clamp_min", 1), Map.entry("clamp_max", 5));

    // ==================== 批次2：五行生克/旺衰/体用/五行属性的本体缓存（static 供 static 方法访问；null=未装载则兜底） ====================
    /** 五行相生边：键 "生者>被生者"（如 "金>水"；rel:generates）。 */
    private static volatile Set<String> ontoGenerates = null;
    /** 五行相克边：键 "克者>被克者"（如 "金>木"；rel:overcomes）。 */
    private static volatile Set<String> ontoOvercomes = null;
    /** 五行名（金…水）→ 方位 / 五色 / 时令 / 器物（rel:fiveDirection/fiveColor/fiveSeason/fiveObject）。 */
    private static volatile Map<String, String> ontoFiveDir = null;
    private static volatile Map<String, String> ontoFiveColor = null;
    private static volatile Map<String, String> ontoFiveSeason = null;
    private static volatile Map<String, String> ontoFiveObject = null;
    /** 旺衰规则：键 "五行名|季节" → 旺相休囚死（rel:SeasonVitality）。 */
    private static volatile Map<String, String> ontoSeasonVitality = null;
    /** 体用 5 级：level → 生克类型名 / 吉凶标签（rel:BodyUseLevel）。 */
    private static volatile Map<Integer, String> ontoBodyUseName = null;
    private static volatile Map<Integer, String> ontoBodyUseLabel = null;
    /** 附录文献：本地名(ATXT_*) → {work,topic,text}（div:AppendixText；本体/DB 优先、APPENDIX_TEXT 兜底）。 */
    private static volatile Map<String, String[]> ontoAppendixText = null;
    /** 坐端八方应兆：卦名 → 断语（div:ZuoDuanEntry；本体/DB 优先、ZUO_DUAN 兜底）。 */
    private static volatile Map<String, String> ontoZuoDuan = null;
    /** 论事十大应：条目名 → 断语（ome:TenResponses；本体/DB 优先、SHI_DA_YING 兜底）。 */
    private static volatile Map<String, String> ontoShiDaYing = null;
    /** 卦应逐卦类象：经卦名 → 类象（sym:ImageSymbol；本体/DB 优先、GUA_YING 兜底）。 */
    private static volatile Map<String, String> ontoGuaYing = null;
    /** 相字断法：类目 → 条目名 → 诀文（sym:XiangZiRule；本体/DB 优先、XIANGZI_RULE 兜底）。 */
    private static volatile Map<String, Map<String, String>> ontoXiangZi = null;
    /** 域知识文本（通用载体）：集合名 → (键 → 文本)（div:LoreTextEntry；本体/DB 优先、各 Map 常量兜底）。 */
    private static volatile Map<String, Map<String, String>> ontoLore = null;
    /**
     * 五卷照录正文（第 52 轮 C-2 下沉）：章节编码（如 JUAN_SAN_GUAN_WU_ZHU_JUE）→ 全文
     * （div:JuanTextEntry；本体/DB 优先，{@code JUAN_*} 常量兜底）。装载后经
     * {@link #applyJuanTextOverrides} 以反射覆盖同名 {@code JUAN_*} 静态字段，调用点无需改动。
     */
    private static volatile Map<String, String> ontoJuanText = null;
    /** 五行个体本地名 → 中文名（rel:Jin→金…），用于把本体 IRI 还原为生克键。 */
    private static final Map<String, String> FIVE_LOCAL = Map.of(
            "Jin", "金", "Mu", "木", "Huo", "火", "Tu", "土", "Shui", "水");

    // ==================== 内置表：先天八卦（乾1兑2离3震4巽5坎6艮7坤8） ====================
    // 内置表为「本体不可用」时的兜底（B3）：运行时优先取本体/OBDA 装载的域知识。
    private static String[] TRI_NAME = {
            null, "乾", "兑", "离", "震", "巽", "坎", "艮", "坤"};
    private static String[] TRI_FIVE = {
            null, "金", "金", "火", "木", "木", "水", "土", "土"};
    private static String[] TRI_DIR = {
            null, "西北", "西", "南", "东", "东南", "北", "东北", "西南"};
    private static String[] TRI_ROLE = {
            null, "父", "少女", "中女", "长男", "长女", "中男", "少男", "母"};
    private static String[] TRI_NATURE = {
            null, "健", "说", "丽", "起", "入", "陷", "止", "顺"};
    private static String[] TRI_MAIN = {
            null, "天", "泽", "火", "雷", "风", "水", "山", "地"};

    /**
     * 八卦万物属类关键词（原著卷之一「八卦万物属类」主象提炼），用于由象取卦。
     *
     * <p>C3：改用 {@link LinkedHashMap} 固定顺序（原 {@code Map.of} 无序，多关键词命中时
     * 结果依 JVM 迭代顺序而异，同一输入可能起出不同卦）。{@link #xiangToNumber(String)}
     * 以「文本首见之象为准」按显式优先级（卦数 1→8）取象，确保可复现。
     */
    private static Map<Integer, String[]> TRI_KEYWORDS = new LinkedHashMap<>();

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

    /** 五行生数（水1火2木3金4土5，河图生数），供应期「五行生数」法（A7）。外置真源 collection=SHENG_SHU。 */
    private static final Map<String, Integer> SHENG_SHU =
            Map.of("水", 1, "火", 2, "木", 3, "金", 4, "土", 5);

    /** 当季五行内置兜底（春木/夏火/秋金/冬水/四季土），外置真源 collection=SEASON_FIVE。 */
    private static final Map<String, String> SEASON_FIVE = Map.of(
            "春", "木", "夏", "火", "秋", "金", "冬", "水", "四季", "土");

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
     * Ontop 对「同表双属性（含 OPTIONAL）」重写的缺陷。装载失败或条目不足 8 时回退内置表并记 INFO，
     * 保证起卦仍可运算（条目不足多为端点不可达或非本应用 Ontop，而非本体缺失）。
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
            Map<String, String> preceptAdm = new LinkedHashMap<>();
            for (Map<String, String> row : obda.executeAboxQuery(
                    "PREFIX div: <http://example.org/plumblossom/divination/>\n"
                            + "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>\n"
                            + "SELECT ?label ?adm WHERE { ?p a div:Precept ; rdfs:label ?label ; "
                            + "div:preceptAdmonition ?adm }")) {
                String plabel = row.get("label");
                if (plabel != null) {
                    precepts.add(plabel);
                    String padm = row.get("adm");
                    if (padm != null && !padm.isBlank()) {
                        preceptAdm.put(plabel, padm);
                    }
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
            Map<String, String[]> appendix = new LinkedHashMap<>();
            for (Map<String, String> row : obda.executeAboxQuery(
                    "PREFIX div: <http://example.org/plumblossom/divination/>\n"
                            + "SELECT ?s ?w ?tp ?t WHERE { ?s a div:AppendixText ; "
                            + "div:appendixWorkName ?w ; div:appendixSectionTopic ?tp ; "
                            + "div:appendixTextBody ?t }")) {
                String nm = iriLocal(row.get("s"));
                String aw = row.get("w");
                String at = row.get("t");
                if (nm != null && aw != null && at != null) {
                    String atp = row.get("tp");
                    appendix.put(nm, new String[]{aw, atp == null ? "" : atp, at});
                }
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
                // 装载条目不足 8 的真因多为 SPARQL 端点不可达、或指向了其它应用的 Ontop（如 8080 被他应用占用），
                // 而非「Ontop/OWL2QL 不投影本体侧 rdfs:label」——实测在正确端点下这三条查询均返回 8/8 且与内置表逐例一致
                // （回归见 PlumBlossomDomainLoadIntegrationTest）。此时回退内置权威表以保证起卦仍可运算。
                log.info("ℹ️ 本体八卦域知识经 OBDA 取到 先天数 {} / 五行 {} / 方位 {}（不足 8，多为端点不可达或非本应用 Ontop），"
                        + "回退内置权威表（起卦运算不受影响）", num.size(), five.size(), dir.size());
            }
            if (!precepts.isEmpty()) {
                this.ontoPrecepts = precepts;
            }
            if (!preceptAdm.isEmpty()) {
                this.ontoPreceptAdmonition = preceptAdm;
            }
            if (!aus.isEmpty()) {
                this.ontoAuspiciousness = aus;
            }
            if (!appendix.isEmpty()) {
                this.ontoAppendixText = appendix;
                applyAppendixFullText(appendix);
            }
            Map<String, String> zuoDuan = new LinkedHashMap<>();
            for (Map<String, String> row : obda.executeAboxQuery(
                    "PREFIX div: <http://example.org/plumblossom/divination/>\n"
                            + "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>\n"
                            + "SELECT ?k ?t WHERE { ?s a div:ZuoDuanEntry ; rdfs:label ?k ; div:zuoDuanText ?t }")) {
                if (row.get("k") != null && row.get("t") != null) {
                    zuoDuan.put(row.get("k"), row.get("t"));
                }
            }
            Map<String, String> shiDaYing = new LinkedHashMap<>();
            for (Map<String, String> row : obda.executeAboxQuery(
                    "PREFIX ome: <http://example.org/plumblossom/omens/>\n"
                            + "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>\n"
                            + "SELECT ?k ?t WHERE { ?s a ome:TenResponses ; rdfs:label ?k ; ome:omenText ?t . "
                            + "FILTER(STRSTARTS(STR(?s), \"http://example.org/plumblossom/omens/ShiDaYing_\")) }")) {
                if (row.get("k") != null && row.get("t") != null) {
                    shiDaYing.put(row.get("k"), row.get("t"));
                }
            }
            Map<String, String> guaYing = new LinkedHashMap<>();
            for (Map<String, String> row : obda.executeAboxQuery(
                    "PREFIX sym: <http://example.org/plumblossom/symbols/>\n"
                            + "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>\n"
                            + "SELECT ?k ?t WHERE { ?s a sym:ImageSymbol ; rdfs:label ?k ; sym:symbolText ?t . "
                            + "FILTER(STRSTARTS(STR(?s), \"http://example.org/plumblossom/symbols/GuaYing_\")) }")) {
                if (row.get("k") != null && row.get("t") != null) {
                    guaYing.put(row.get("k"), row.get("t"));
                }
            }
            if (!zuoDuan.isEmpty()) {
                this.ontoZuoDuan = zuoDuan;
            }
            if (!shiDaYing.isEmpty()) {
                this.ontoShiDaYing = shiDaYing;
            }
            if (!guaYing.isEmpty()) {
                this.ontoGuaYing = guaYing;
            }
            Map<String, Map<String, String>> xiangZi = new LinkedHashMap<>();
            for (Map<String, String> row : obda.executeAboxQuery(
                    "PREFIX sym: <http://example.org/plumblossom/symbols/>\n"
                            + "SELECT ?cat ?k ?t WHERE { ?s a sym:XiangZiRule ; "
                            + "sym:xiangZiCategory ?cat ; sym:xiangZiRuleKey ?k ; sym:xiangZiRuleText ?t }")) {
                String cat = row.get("cat");
                String xk = row.get("k");
                String xt = row.get("t");
                if (cat != null && xk != null && xt != null) {
                    xiangZi.computeIfAbsent(cat, x -> new LinkedHashMap<>()).put(xk, xt);
                }
            }
            if (!xiangZi.isEmpty()) {
                this.ontoXiangZi = xiangZi;
            }
            Map<String, Map<String, String>> lore = new LinkedHashMap<>();
            for (Map<String, String> row : obda.executeAboxQuery(
                    "PREFIX div: <http://example.org/plumblossom/divination/>\n"
                            + "SELECT ?c ?k ?t WHERE { ?s a div:LoreTextEntry ; "
                            + "div:loreCollection ?c ; div:loreKey ?k ; div:loreText ?t }")) {
                String lc = row.get("c");
                String lk = row.get("k");
                String lt = row.get("t");
                if (lc != null && lk != null && lt != null) {
                    lore.computeIfAbsent(lc, x -> new LinkedHashMap<>()).put(lk, lt);
                }
            }
            if (!lore.isEmpty()) {
                this.ontoLore = lore;
            }
            // 五卷照录正文（第 52 轮 C-2 下沉）：div:JuanTextEntry → code→全文；装载成功则反射覆盖 JUAN_* 兜底。
            Map<String, String> juanText = new LinkedHashMap<>();
            for (Map<String, String> row : obda.executeAboxQuery(
                    "PREFIX div: <http://example.org/plumblossom/divination/>\n"
                            + "SELECT ?c ?t WHERE { ?s a div:JuanTextEntry ; "
                            + "div:juanCode ?c ; div:juanText ?t }")) {
                String jc = row.get("c");
                String jt = row.get("t");
                if (jc != null && jt != null) {
                    juanText.put(jc, jt);
                }
            }
            if (!juanText.isEmpty()) {
                this.ontoJuanText = juanText;
                applyJuanTextOverrides(juanText);
            }
            Map<String, Integer> judgeW = new LinkedHashMap<>();
            for (Map<String, String> row : obda.executeAboxQuery(
                    "PREFIX jud: <http://example.org/plumblossom/judgement/>\n"
                            + "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>\n"
                            + "SELECT ?item ?w WHERE { ?s a jud:JudgeWeight ; "
                            + "jud:judgeWeightItem ?item ; jud:judgeWeightValue ?w }")) {
                putIfInt(judgeW, row.get("item"), row.get("w"));
            }
            if (judgeW.size() >= 12) {
                this.ontoJudgeWeight = judgeW;
            }
            loadDomainRelations(obda);
            loadTrigramKnowledge(obda);
            log.info("✅ 本体域知识装载 | 八卦先天数 {} | 五行 {} | 方位 {} | 四不占 {}（告示语 {}） | 吉凶等级 {} | 附录 {} | 坐端 {} | 十应 {} | 卦应 {} | 域知识 {} | ready={}",
                    num.size(), five.size(), dir.size(), precepts.size(), preceptAdm.size(), aus.size(),
                    appendix.size(), zuoDuan.size(), shiDaYing.size(), guaYing.size(), lore.size(), ontologyDomainReady);
        } catch (Exception e) {
            log.warn("⚠️ 本体域知识装载失败，五行/方位/先天数回退内置表：{}", e.getMessage());
        }
    }

    /**
     * 批次2：装载「五行生克边 / 卦气旺衰规则 / 体用五级 / 五行属性字典」四类关系域知识（本体优先、内置兜底）。
     *
     * <p>生克边复用本体 ABox 断言（{@code rel:generates}/{@code rel:overcomes}，实测 Ontop 可查属性断言）；
     * 旺衰规则/体用五级/五行属性字典/吉凶等级由外置 DB 表经 OBDA 映射（类实例须由 DB 映射产生方可查，
     * 纯 ABox 类断言在 Ontop 下不可查）。装载失败或条目不足则保持 {@code null}，各方法回退内置权威表/算法。
     */
    private void loadDomainRelations(OBDAHandler obda) {
        try {
            Set<String> gen = new LinkedHashSet<>();
            for (Map<String, String> r : obda.executeAboxQuery(
                    "PREFIX rel: <http://example.org/plumblossom/relations/>\n"
                            + "SELECT ?s ?o WHERE { ?s rel:generates ?o }")) {
                String sl = FIVE_LOCAL.get(iriLocal(r.get("s")));
                String ol = FIVE_LOCAL.get(iriLocal(r.get("o")));
                if (sl != null && ol != null) {
                    gen.add(sl + ">" + ol);
                }
            }
            Set<String> ov = new LinkedHashSet<>();
            for (Map<String, String> r : obda.executeAboxQuery(
                    "PREFIX rel: <http://example.org/plumblossom/relations/>\n"
                            + "SELECT ?s ?o WHERE { ?s rel:overcomes ?o }")) {
                String sl = FIVE_LOCAL.get(iriLocal(r.get("s")));
                String ol = FIVE_LOCAL.get(iriLocal(r.get("o")));
                if (sl != null && ol != null) {
                    ov.add(sl + ">" + ol);
                }
            }
            Map<String, String> dir = new LinkedHashMap<>();
            Map<String, String> col = new LinkedHashMap<>();
            Map<String, String> sea = new LinkedHashMap<>();
            Map<String, String> obj = new LinkedHashMap<>();
            for (Map<String, String> r : obda.executeAboxQuery(
                    "PREFIX rel: <http://example.org/plumblossom/relations/>\n"
                            + "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>\n"
                            + "SELECT ?lab ?d ?c ?sea ?ob WHERE { ?fp rel:fiveDirection ?d ; rel:fiveColor ?c ; "
                            + "rel:fiveSeason ?sea ; rel:fiveObject ?ob ; rdfs:label ?lab }")) {
                String lab = r.get("lab");
                if (lab == null) {
                    continue;
                }
                if (r.get("d") != null) dir.put(lab, r.get("d"));
                if (r.get("c") != null) col.put(lab, r.get("c"));
                if (r.get("sea") != null) sea.put(lab, r.get("sea"));
                if (r.get("ob") != null) obj.put(lab, r.get("ob"));
            }
            Map<String, String> vit = new LinkedHashMap<>();
            for (Map<String, String> r : obda.executeAboxQuery(
                    "PREFIX rel: <http://example.org/plumblossom/relations/>\n"
                            + "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>\n"
                            + "SELECT ?lab ?sea ?v WHERE { ?s a rel:SeasonVitality ; rel:forFivePhase ?fp ; "
                            + "rel:inSeason ?sea ; rel:vitality ?v . ?fp rdfs:label ?lab }")) {
                String lab = r.get("lab");
                String s = r.get("sea");
                String v = r.get("v");
                if (lab != null && s != null && v != null) {
                    vit.put(lab + "|" + s, v);
                }
            }
            Map<Integer, String> bnm = new LinkedHashMap<>();
            Map<Integer, String> bau = new LinkedHashMap<>();
            for (Map<String, String> r : obda.executeAboxQuery(
                    "PREFIX rel: <http://example.org/plumblossom/relations/>\n"
                            + "SELECT ?lv ?nm ?au WHERE { ?s a rel:BodyUseLevel ; rel:bodyUseLevel ?lv ; "
                            + "rel:shengKeName ?nm ; rel:auspiciousnessLabel ?au }")) {
                Integer lv = parseIntOrNull(r.get("lv"));
                if (lv == null) {
                    continue;
                }
                if (r.get("nm") != null) bnm.put(lv, r.get("nm"));
                if (r.get("au") != null) bau.put(lv, r.get("au"));
            }
            if (gen.size() >= 5) ontoGenerates = gen;
            if (ov.size() >= 5) ontoOvercomes = ov;
            if (dir.size() >= 5) ontoFiveDir = dir;
            if (col.size() >= 5) ontoFiveColor = col;
            if (sea.size() >= 5) ontoFiveSeason = sea;
            if (obj.size() >= 5) ontoFiveObject = obj;
            if (vit.size() >= 20) ontoSeasonVitality = vit;
            if (bnm.size() >= 5) ontoBodyUseName = bnm;
            if (bau.size() >= 5) ontoBodyUseLabel = bau;
            log.info("✅ 本体关系域装载 | 生 {} 克 {} | 五行属性 方位{} 色{} 时令{} 物{} | 旺衰 {} | 体用名 {} 体用吉凶 {}",
                    gen.size(), ov.size(), dir.size(), col.size(), sea.size(), obj.size(),
                    vit.size(), bnm.size(), bau.size());
        } catch (Exception e) {
            log.warn("⚠️ 本体关系域装载失败，生克/旺衰/体用5级/五行属性回退内置：{}", e.getMessage());
        }
    }

    /**
     * 批次2-续(3)：装载八卦结构知识——trigram 表（名/五行/方位/长幼/性情/主象，经 OBDA 映射为 core:Trigram
     * 个体数据属性）与 TRI_KEYWORDS（domain_lore 通用载体）。本体/DB 优先，装载成功（≥8 卦）则覆盖内置
     * TRI_* 数组与 TRI_KEYWORDS；失败或不足则保留内置兜底（静态方法据此取值，运行期即本体优先）。
     */
    private void loadTrigramKnowledge(OBDAHandler obda) {
        try {
            String[] name = new String[9];
            String[] five = new String[9];
            String[] dir = new String[9];
            String[] role = new String[9];
            String[] nature = new String[9];
            String[] main = new String[9];
            int ok = 0;
            for (Map<String, String> r : obda.executeAboxQuery(
                    "PREFIX core: <http://example.org/plumblossom/core/>\n"
                            + "PREFIX rel: <http://example.org/plumblossom/relations/>\n"
                            + "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>\n"
                            + "SELECT ?num ?lab ?fl ?dl ?role ?nat ?main WHERE {"
                            + " ?t a core:Trigram ; core:trigramNumber ?num ; rdfs:label ?lab ;"
                            + " core:trigramFamilyRole ?role ; core:trigramNature ?nat ; core:trigramMainImage ?main ;"
                            + " core:hasDirection ?d ; rel:hasFivePhase ?f ."
                            + " ?d rdfs:label ?dl . ?f rdfs:label ?fl }")) {
                Integer num = parseIntOrNull(r.get("num"));
                if (num == null || num < 1 || num > 8) {
                    continue;
                }
                name[num] = r.get("lab");
                five[num] = r.get("fl");
                dir[num] = r.get("dl");
                role[num] = r.get("role");
                nature[num] = r.get("nat");
                main[num] = r.get("main");
                ok++;
            }
            if (ok >= 8) {
                TRI_NAME = name;
                TRI_FIVE = five;
                TRI_DIR = dir;
                TRI_ROLE = role;
                TRI_NATURE = nature;
                TRI_MAIN = main;
                log.info("✅ 本体八卦结构装载 | 覆盖内置 TRI_* 数组 {} 卦", ok);
            }
            if (ontoLore != null) {
                Map<String, String> kw = ontoLore.get("TRI_KEYWORDS");
                if (kw != null && kw.size() >= 8) {
                    Map<Integer, String[]> m = new LinkedHashMap<>();
                    for (Map.Entry<String, String> e : kw.entrySet()) {
                        Integer num = parseIntOrNull(e.getKey());
                        String v = e.getValue();
                        if (num != null && v != null && !v.isBlank()) {
                            m.put(num, v.split("、"));
                        }
                    }
                    if (m.size() >= 8) {
                        TRI_KEYWORDS = m;
                        log.info("✅ 本体八卦万物属类装载 | 覆盖内置 TRI_KEYWORDS {} 卦", m.size());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("⚠️ 本体八卦结构装载失败，TRI_* 保留内置兜底：{}", e.getMessage());
        }
    }

    private static Integer parseIntOrNull(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 取 IRI 的本地名（最后一个 '/' 或 '#' 之后的部分）。 */
    private static String iriLocal(String iri) {
        if (iri == null) {
            return null;
        }
        int k = Math.max(iri.lastIndexOf('/'), iri.lastIndexOf('#'));
        return k >= 0 ? iri.substring(k + 1) : iri;
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
                triggered = resolvePreceptName("无事不占");
            } else if (!promise) {
                triggered = resolvePreceptName("不诚不占");
            } else if (!righteous) {
                triggered = resolvePreceptName("不义不占");
            } else {
                if (SESSION_SUBJECTS.size() > SESSION_SUBJECTS_LIMIT) {
                    SESSION_SUBJECTS.clear(); // C6：防无界增长
                }
                if (!SESSION_SUBJECTS.add(subject.trim())) {
                    triggered = resolvePreceptName("重卦不占");
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
            String alertText = preceptAdmonition(triggered);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("alertText", alertText);
            completeResult(client, job, result);
            log.info("✅ preceptAlert | triggered={} | alert={}", triggered, alertText);
        } catch (Exception e) {
            fail(job, client, "PRECEPT_ALERT_FAILED", e);
        }
    }

    /**
     * 四不占戒律名解析：本体优先（{@code div:Precept} 已声明的戒律名），内置同名兜底。
     * 本体未就绪或未声明该戒律时仍按内置戒律执行（安全优先，绝不因本体缺失而放行）。
     */
    private String resolvePreceptName(String canonical) {
        Set<String> s = ontoPrecepts;
        if (s != null && !s.isEmpty() && !s.contains(canonical)) {
            log.warn("⚠️ 四不占戒律「{}」未在本体 div:Precept 中声明，回退内置戒律（本体优先但不放弃校验）", canonical);
        }
        return canonical;
    }

    /**
     * 四不占告示语：本体优先（{@code div:preceptAdmonition}），内置兜底（呈现文本）。
     * 由节点 2'（preceptAlert）调用，使告警文案真正由本体驱动。
     */
    private String preceptAdmonition(String triggered) {
        String key = triggered == null ? "" : triggered;
        Map<String, String> m = ontoPreceptAdmonition;
        if (m != null && m.get(key) != null && !m.get(key).isBlank()) {
            return m.get(key);
        }
        return switch (key) {
            case "无事不占" -> "无具体事项不可起卦。";
            case "重卦不占" -> "一事一卦，反复求卦反失其机。";
            case "不诚不占" -> "意念不纯，请诚心再问。";
            case "不义不占" -> "此事悖于道义，不可占。";
            default -> "心不诚、事不明，未可轻占；请正心诚意而后再问。";
        };
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
            String tones = str(vars.get("toneNumbers"));
            int[] upLow = splitCharacters(text == null ? "" : text, tones);
            String source = charNumberSource(text == null ? "" : text, tones);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("upperNumber", upLow[0]);
            result.put("lowerNumber", upLow[1]);
            result.put("charNumberSource", source);
            completeResult(client, job, result);
            log.info("✅ characterSplit | text={} | upper={} | lower={} | 取数来源={}",
                    text, upLow[0], upLow[1], source);
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
            // B29–B31：占物类（占动物/占静物/物卦起例/形物占/验色占）另依卷之三·器物占、
            // 体用断物之妙、观物趣时诀断其物之「成毁/可用/贵贱」，补入类象解读；
            // B34–B37：并补卷之三·物数为体诀/观物看变爻为主/观物克应法/万物戏念。
            String season = str(vars.get("season"));
            if (season == null || season.isBlank()) {
                season = seasonFromLunarMonth(intv(vars.get("lunarMonth")));
            }
            String objectNote = objectDivinationText(str(vars.get("divinationMethod")), name,
                    intv(vars.get("movingLinePosition")), body, use, mutual, changed, season,
                    str(vars.get("omenDesc")));
            if (!objectNote.isEmpty()) {
                text = text + " " + objectNote;
            }
            // 第二十五轮·待考落地：卷之三·器物占 / 占物类例·看爻辞断物 / 观物用易例 / 变卦式八则
            // （占物类例示与断法，照录备考；随取象解读一并陈之）。
            String objDaikao = objectDaikaoText(str(vars.get("divinationMethod")));
            if (!objDaikao.isEmpty()) {
                text = text + " " + objDaikao;
            }
            // B63：卷之一·风觉占/鸟占/听声音占之断法——起卦既成，补其审时察色辨声势/听声察名义/
            // 辨物声察悲喜之断（三者取数已在位，本轮补断法）。
            String castNote = castMethodVerdictText(str(vars.get("divinationMethod")));
            if (!castNote.isEmpty()) {
                text = text + " " + castNote;
            }
            // B68：卷之四·字画指迷 / 卷之五·拆字杂编「相字」取象——依所书之字/偏旁/兆应取断；
            // 依《本体顶层设计》§56/§112，相字属「万物类象」取象来源（节点 9）；C12：节点 16 不设独立栏。
            String xzSubject = str(vars.get("text"));
            String xzNote = xiangZiNote((xzSubject == null ? "" : xzSubject) + " " + category);
            if (!xzNote.isEmpty()) {
                text = text + " " + xzNote + "（卷之四·字画指迷 / 卷之五·拆字杂编）。";
            } else if (isCharacterDivination(str(vars.get("divinationMethod")))) {
                text = text + " " + xiangZiDigestText();
            }
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
            // A14：天时占依原著「不分体用，全观诸卦，详推五行」——须取本/互/变六卦作断，
            //      不可套用其余诸占之体用生克 5 级（原著卷之二·天时占第一 / 卷三·体用）。
            String verdict;
            if ("天时".equals(category)) {
                verdict = tianShiVerdict(name, str(vars.get("mutualHexagram")), str(vars.get("changedHexagram")));
            } else {
                // B21/B22/B23：婚姻性情容貌、疾病测鬼神、饮食所食，均须取用/互/变诸卦之本象——
                // 故一并传体、用、互、变（用卦＝所问之事之应，互/变为中间与末后）。
                verdict = categoryVerdict(category, name, shengKe,
                        str(vars.get("bodyTrigram")), str(vars.get("useTrigram")),
                        str(vars.get("mutualHexagram")), str(vars.get("changedHexagram")),
                        intv(vars.get("movingLinePosition")));
            }
            // B67：附录·一撮金「以爻诀断之」——一撮金法专用断法，取本卦动爻之爻诀断辞
            //      （六等吉凶：上上/上中/中中/中平/下中/下下；原著「乃以爻诀断之，无不奇中」）。
            if ("一撮金法".equals(str(vars.get("divinationMethod")))) {
                String yicuoNote = yicuoJinYaoNote(name, intv(vars.get("movingLinePosition")));
                if (!yicuoNote.isEmpty()) {
                    verdict = verdict + " " + yicuoNote;
                }
                // B70：附录·八卦诀——一撮金起数得卦后，另据京房八宫名录标本卦所属之宫。
                String palace = baGuaJuePalace(name);
                if (!palace.isEmpty()) {
                    verdict = verdict + " 〔八卦诀〕" + name + "属" + palace + "宫。";
                }
            }
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
                String body = str(vars.get("bodyTrigram"));
                String pBody = fivePhaseOf(body);
                // A9/十应：区分十应类别与动/静象；外应之动象比卦中固有之象更直接、更紧迫，优先取用。
                String tenKind = tenResponseCategory(omenDesc);
                boolean movingOmen = isMovingOmen(omenDesc);
                // B17：静物之应「物之圆者事成，器之缺者事败」——「不论卦象」之直断
                //（原著卷之二·十应目论·静物；另占卜总诀「见圆物事易成、见缺物事终毁」）。
                String stillDirect = stillObjectDirectVerdict(omenDesc);
                if (!stillDirect.isEmpty() && "外".equals(tenKind)) {
                    tenKind = "静物";
                }
                // 五色之应（卷之二·十应目论）：五色不论卦象，但以所见之色直推五行；
                // 其余各类由外应取象卦，再推其五行。
                String pOmen;
                String omenSymbol;
                if ("五色".equals(tenKind)) {
                    pOmen = fivePhaseOfColor(omenDesc);
                    omenSymbol = "五色" + pOmen;
                } else {
                    int omenTri = omenToTrigram(omenDesc);
                    pOmen = fivePhaseOf(TRI_NAME[omenTri]);
                    omenSymbol = "取象" + TRI_NAME[omenTri];
                }
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
                verdict = "三要十应参断：〔十应·" + tenKind + "应〕〔" + (movingOmen ? "动象" : "静象") + "〕"
                        + tone + "（见「" + omenDesc + "」→" + omenSymbol + "，其五行" + pOmen + "）。"
                        + (movingOmen ? "此为动象，临事最急，优先取用；" : "此为静象，为背景之参；")
                        + "卦象为体、外应为用，二者合参方得全貌。";
                // B17：静物之应「不论卦象」之成败直断（物圆事成、器缺事败），与上之五行参断并行。
                if (!stillDirect.isEmpty()) {
                    verdict += "另依「静物之应」不论卦象直断：" + stillDirect + "。";
                }
                // 第 37 轮·B109：卷之二·十应目论·总纲（照录；在各类之应之前）。
                verdict += shiYingMuLunNote();
                // B38–B46：十应目论·各类之应分应断法（天时/地理/人事/时令/方卦/动物/静物/言语/声音）。
                String catNote = tenCategoryNote(tenKind, omenDesc, body, str(vars.get("season")));
                if (!catNote.isEmpty()) {
                    verdict += catNote;
                }
                // B47：向背（相向而来 / 相背而去，决灾祥将至或已过）。
                String xiangBei = xiangBeiNote(omenDesc);
                if (!xiangBei.isEmpty()) {
                    verdict += xiangBei;
                }
                // B49：十应奥论——十应出于三要而妙乎三要，须合内外卦而断。
                verdict += tenResponsePrinciple();
                // B50：卷之三·占卜十应诀——十应总目（正/互/变应＋方/日/刻/外/天时/地理/人事应）与结语。
                verdict += tenResponseJueNote();
                // 第 37 轮·B110–B111：卷之三·占卜十应诀·逐应原文（方应＋刻/外/天时/地理/人事应，照录）。
                verdict += zhanBuShiYingJueNote();
                verdict += tenResponseJueSummary(str(vars.get("bodyVitality")));
                // B51：卷之三·占卜坐端之诀——八宫八方应兆（依外应所述宫位）。
                String zuoDuan = zuoDuanNote(omenDesc);
                if (!zuoDuan.isEmpty()) {
                    verdict += zuoDuan;
                }
                // B52：卷之三·论事十大应（论日辰秘文）——依姿态/情态之无歧义键。
                String shiDaYing = shiDaYingNote(omenDesc);
                if (!shiDaYing.isEmpty()) {
                    verdict += shiDaYing;
                }
                // B55：卷之二·观物洞玄歌（占宅气兆应）——依所察兆象取断。
                String dongXuan = dongXuanNote(omenDesc);
                if (!dongXuan.isEmpty()) {
                    verdict += dongXuan;
                }
                // B58：卷之三·万物赋·闻见克应诸兆——依所闻所见之兆取断。
                String wanWu = wanWuNote(omenDesc);
                if (!wanWu.isEmpty()) {
                    verdict += wanWu;
                }
                // B62：卷之二·三要灵应篇·各类之应（天文/地理/人品/身/人事/器物/草木/禽兽/杂见/叶音/自己/动静）。
                String sanYao = sanYaoLingYingNote(omenDesc);
                if (!sanYao.isEmpty()) {
                    verdict += sanYao;
                    // B73：卷之二·三要灵应篇序（清灵子朱虚序，照录）——三要之妙在运耳目心三者之虚灵。
                    verdict += sanYaoXuNote();
                    // 第 36 轮：卷之二·三要灵应篇·正文导论与篇末总论/跋/诫语（照录，全篇首尾）。
                    verdict += sanYaoLingYingGaiLunNote();
                    verdict += sanYaoLingYingZongLunNote();
                }
                // 第二十五轮·待考落地：日应（须日支 dayZhi）/ 万物赋鸟兽时日兆（须日干 dayGan）/
                // 复明写字之应（观所书墨色笔误）——输入齐备即断，缺则不陈。
                String riYing = riYingNote(str(vars.get("dayZhi")));
                if (!riYing.isEmpty()) {
                    verdict += riYing;
                }
                String niaoShou = niaoShouShiRiNote(str(vars.get("dayGan")));
                if (!niaoShou.isEmpty()) {
                    verdict += niaoShou;
                }
                String xieZi = xieZiZhiYingNote(omenDesc);
                if (!xieZi.isEmpty()) {
                    verdict += xieZi;
                }
                // B60：卷之三·体用生克之诀——外卦真生真克之辨（形色不能克、以轻重分生克）。
                verdict += zhenShengZhenKeNote(body);
            } else {
                // B48：卷之二·静占——「凡占在静室，无所闻见，则无外卦，即不论外卦，
                //      但以全卦年月日值五行衰旺之气，以体用决之」（L1416–1417）。
                verdict = "〔静占〕无所闻见则无外卦，即不论外卦，但以全卦年、月、日值五行衰旺之气，"
                        + "以体用决之（原卷之二·静占）。";
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

            // 基准分由本体/DB 权重驱动（jud:JudgeWeight.base_<level>）；shengKeLevel 越界取 3（平）
            int baseLv = (shengKeLevel >= 1 && shengKeLevel <= 5) ? shengKeLevel : 3;
            int score = jw("base_" + baseLv);

            // A5：纳入互卦、变卦对体之生克（设计 R8「变卦克体→末后不吉」；
            //     「生体之卦宜多、克体之卦宜少」——原著卷之二·体用总诀/体用）。
            String useRel = relationToBody(use, body);
            String mutualRel = relationToBodyHex(mutual, body);
            String changedRel = relationToBodyHex(changed, body);
            int wMax = jw("clamp_max");
            int wMin = jw("clamp_min");
            int shengBodyCount = 0;
            int keBodyCount = 0;
            for (String rel : new String[]{useRel, mutualRel, changedRel}) {
                if (rel.equals("生体")) {
                    score = Math.min(wMax, score + jw("sheng_body"));
                    shengBodyCount++;
                } else if (rel.equals("比和")) {
                    score = Math.min(wMax, score + jw("bihe_body"));
                    shengBodyCount++;
                } else if (rel.equals("克体")) {
                    score = Math.max(wMin, score + jw("ke_body"));
                    keBodyCount++;
                } else if (rel.equals("体生")) {
                    score = Math.max(wMin, score + jw("tishi_body")); // 泄气
                }
            }

            if ("旺".equals(vitality) || "相".equals(vitality)) {
                score = Math.min(wMax, score + jw("vitality_wang_xiang"));
            } else if ("死".equals(vitality)) {
                score = Math.max(wMin, score + jw("vitality_si"));
            }
            if (omen != null && !omen.isBlank()) {
                if (omen.contains("生体") || omen.contains("比和") || omen.contains("喜")) {
                    score = Math.min(wMax, score + jw("omen_good"));
                } else if (omen.contains("克体") || omen.contains("忧")) {
                    score = Math.max(wMin, score + jw("omen_bad"));
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
            // B18：体用总诀·逐卦生体/克体之应（原著卷之二·体用总诀 L1023–1051）。
            String trigramNotes = bodyRelationNotes(body, use, mutual, changed);
            if (!trigramNotes.isEmpty()) {
                process += "逐卦应验（体用总诀）：" + trigramNotes + "。";
            }
            // B19：占卦诀·生体卦所在定迟速、克体卦定阻节（原著卷之三·占卦诀 L1583–1586）。
            String zhanGuaNote = zhanGuaJueNote(body, use, mutual, changed);
            if (!zhanGuaNote.isEmpty()) {
                process += "占卦诀：" + zhanGuaNote + "。";
            }
            // B56：衰旺之诀（卷之二·衰旺论 L1393–1397／卷之三·体用衰旺之诀 L1606–1607）。
            String shuaiWang = shuaiWangNote(body, use, mutual, changed, vitality);
            if (!shuaiWang.isEmpty()) {
                process += shuaiWang;
            }
            // B57：体用动静之诀（卷之二·动静 L1404–1412／卷之三·体用动静之诀 L1608–1612）。
            process += dongJingNote(str(vars.get("motion")));
            // B65：体用互变之诀·互卦分体之互/用之互、体互最紧用互次之（原著卷之三·体用互变之诀 L1587–1596）。
            String mutualBodyUse = mutualBodyUseNote(str(vars.get("originalHexagram")), body,
                    intv(vars.get("movingLinePosition")));
            if (!mutualBodyUse.isEmpty()) {
                process += mutualBodyUse + "。";
            }

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

    /** 吉凶评分权重取值（批次4）：本体/DB 装载优先，未装载或缺失则回退内置 {@link #JUDGE_W_BUILTIN}。 */
    private int jw(String item) {
        Map<String, Integer> m = ontoJudgeWeight;
        if (m != null && !m.isEmpty()) {
            Integer v = m.get(item);
            if (v != null) {
                return v;
            }
        }
        return JUDGE_W_BUILTIN.getOrDefault(item, 0);
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
            String responsePeriod = responsePeriodOf(total, bodyPhase, usePhase, motion, auspiciousness)
                    + wanWuYingQiNote()
                    + xianHouTianYingQiNote()
                    + zhanBuKeYingJueNote();
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
    // 节点 14：修身建议生成 + 破解（趋避）方案生成
    // ==========================================================================
    @JobWorker(type = "generateAdvice", autoComplete = false)
    public void handleGenerateAdvice(final ActivatedJob job, final JobClient client) {
        try {
            Map<String, Object> vars = job.getVariablesAsMap();
            String auspiciousness = str(vars.get("auspiciousness"));
            String body = str(vars.get("bodyTrigram"));
            String use = str(vars.get("useTrigram"));
            String advice = switch (auspiciousness == null ? "" : auspiciousness) {
                case "大吉", "吉" -> "吉兆当前，宜守正保泰、戒骄持盈，勿因得而忘形。";
                case "大凶", "凶" -> "凶兆当前，宜积德改行、反求诸己，悔过迁善以转祸为福。";
                default -> "吉凶未定，宜居中持正、谨言慎行，静以待时。";
            };
            // 破解（趋避）方案：凶/平时依体用五行推「扶体 / 抑用 / 通关」三法（吉不破解）。
            String breakdownPlan = breakdownNote(body, use, auspiciousness);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("advice", advice);
            result.put("breakdownPlan", breakdownPlan);
            completeResult(client, job, result);
            log.info("✅ generateAdvice | auspiciousness={} body={}({}) use={}({}) → 破解方案 {} 字",
                    auspiciousness, body, fivePhaseOf(body), use, fivePhaseOf(use), breakdownPlan.length());
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
            appendLine(sb, "卦义性情", hexagramNatureLine(
                    str(vars.get("originalHexagram")), str(vars.get("bodyTrigram")), str(vars.get("useTrigram")),
                    str(vars.get("mutualHexagram")), str(vars.get("changedHexagram"))));
            appendLine(sb, "体用生克", vars.get("shengKeType"));
            appendLine(sb, "卦气旺衰", vars.get("bodyVitality"));
            appendLine(sb, "万物类象", vars.get("imageInterpretation"));
            appendLine(sb, "分类占断", vars.get("categoryVerdict"));
            appendLine(sb, "外应解读", vars.get("omenVerdict"));
            appendLine(sb, "过程描述", vars.get("processDescription"));
            appendLine(sb, "应期", vars.get("responsePeriod"));
            appendLine(sb, "吉凶等级", vars.get("auspiciousness"));
            appendLine(sb, "修身建议", vars.get("advice"));
            appendLine(sb, "破解方案", vars.get("breakdownPlan"));
            appendLine(sb, "断占总诀", guanMeiShuJueNote());
            appendLine(sb, "卷首序", meiHuaYiShuXuNote());
            appendLine(sb, "心法诗", zhanFaWanFaNote());
            appendLine(sb, "遗论", yiLunNote());
            appendLine(sb, "卦断遗论", guaDuanYiLunNote());
            appendLine(sb, "卷之二诸论", juanErZhuLunNote());
            appendLine(sb, "洞玄歌序跋", dongXuanYinBaNote());
            appendLine(sb, "卷之一象数易理", juanYiXiangShuNote());
            appendLine(sb, "卷之一占例", juanYiZhanLiNote());
            appendLine(sb, "卷之一起卦法", juanYiQiGuaFaNote());
            appendLine(sb, "卷之一风觉鸟占", juanYiFengJueNiaoNote());
            appendLine(sb, "卷之一八卦万物类占", juanYiBaGuaLeiZhanAnNote());
            appendLine(sb, "卷之二体用总诀", juanErTiYongZongJueNote());
            appendLine(sb, "卷之二十八占", juanErShiBaZhanNote());
            appendLine(sb, "卷之二复明诸应", juanErFuMingNote());
            appendLine(sb, "卷之二洞玄歌", juanErDongXuanGeNote());
            appendLine(sb, "卷之二加数屋宅", juanErJiaShuWuZhaiNote());
            appendLine(sb, "心易占卜玄机", juanErXinYiXuanJiNote());
            appendLine(sb, "占卜总诀", juanErZhanBuZongJueNote());
            appendLine(sb, "占卜论理诀", juanErZhanBuLunLiJueNote());
            appendLine(sb, "先天后天论", juanErXianTianHouTianLunNote());
            appendLine(sb, "三要灵应篇·拆字之应", sanYaoChaiZiZhiYingNote());
            appendLine(sb, "八卦定阴阳次序", juanSanBaGuaDingYinYangNote());
            appendLine(sb, "变卦式八则", juanSanBianGuaShiBaZeNote());
            appendLine(sb, "占卦诀", juanSanZhanGuaJueNote());
            appendLine(sb, "体用互变之诀", juanSanTiYongHuBianNote());
            appendLine(sb, "体用生克之诀", juanSanTiYongShengKeNote());
            appendLine(sb, "体用衰旺之诀", juanSanTiYongShuaiWangNote());
            appendLine(sb, "体用动静之诀", juanSanTiYongDongJingNote());
            appendLine(sb, "占卜坐端之诀", juanSanZhanBuZuoDuanNote());
            appendLine(sb, "万物赋", juanSanWanWuFuNote());
            appendLine(sb, "饮食篇", juanSanYinShiPianNote());
            appendLine(sb, "观物玄妙歌诀", juanSanGuanWuXuanMiaoGeNote());
            appendLine(sb, "诸事响应歌", juanSanZhuShiXiangYingGeNote());
            appendLine(sb, "诸卦反对性情", juanSanZhuGuaFanDuiNote());
            appendLine(sb, "占物类例", juanSanZhanWuLeiLiNote());
            appendLine(sb, "体用断物之妙", juanSanTiYongDuanWuNote());
            appendLine(sb, "物数为体诀", juanSanWuShuWeiTiNote());
            appendLine(sb, "观物诸诀", juanSanGuanWuZhuJueNote());
            appendLine(sb, "万物戏念", juanSanWanWuXiNianNote());
            appendLine(sb, "占卜十应诀", juanSanZhanBuShiYingJueNote());
            appendLine(sb, "论事十大应", juanSanLunShiShiDaYingNote());
            appendLine(sb, "卦应", juanSanGuaYingNote());
            appendLine(sb, "字画指迷序", juanSiZiHuaZhiMiXuNote());
            appendLine(sb, "玄黄克应歌", juanSiXuanHuangNote());
            appendLine(sb, "花押赋", juanSiFuNote());
            appendLine(sb, "字画经验", juanSiZiHuaJingYanNote());
            appendLine(sb, "相字心易", juanSiXiangZiXinYiNote());
            appendLine(sb, "视势/象人/以时/以卦/", juanSiDuanFaNote());
            appendLine(sb, "六神笔法", juanSiLiuShenNote());
            appendLine(sb, "五行体格式", juanSiWuXingNote());
            appendLine(sb, "五行全备", juanWuWuXingQuanBeiNote());
            appendLine(sb, "神煞诸神", juanWuShenShaNote());
            appendLine(sb, "七言作用歌", juanWuQiYanZuoYongGeNote());
            appendLine(sb, "比例歌·西江月", juanWuBiLiGeXiJiangYueNote());
            appendLine(sb, "断例诸占", juanWuZhanLiNote());
            appendLine(sb, "卦象诸表", juanWuBiaoNote());
            appendLine(sb, "四言独步", juanWuSiYanDuBuNote());
            appendLine(sb, "五言作用歌", juanWuWuYanZuoYongGeNote());
            appendLine(sb, "别理篇", juanWuBieLiPianNote());
            appendLine(sb, "六言剖断歌", juanWuLiuYanPouDuanGeNote());
            appendLine(sb, "格物章", juanWuGeWuZhangNote());
            appendLine(sb, "物理论", juanWuWuLiLunNote());
            appendLine(sb, "五行六神辨别论", juanWuWuXingLiuShenLunNote());
            appendLine(sb, "金声章", juanWuJinShengZhangNote());
            appendLine(sb, "体用诀", baGuaXinYiTiYongJueNote());
            appendLine(sb, "附录文献", appendixDoctrineNote());
            appendLine(sb, "图象补录", tuXiangBuLuNote());

            // 第四十九轮·事实包：由本体应用把「所问之事＋主体（体卦五行/方位/旺衰）＋用卦＋本互变＋
            // 生克＋分项断语＋类象＋外应＋应期＋破解方案＋修身根本」结构化为「占断依据结构表」，
            // 一供节点 17 大模型依此解析（同一 `buildDivinationFactSheet`），二供前端展示/导出（`divinationFactSheet`）。
            String factSheet = buildDivinationFactSheet(subject, vars);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("divinationResult", sb.toString());
            result.put("divinationFactSheet", factSheet);
            completeResult(client, job, result);
            log.info("✅ aggregateResult | subject={} → 占断结果已汇总（占断依据结构表 {} 字）", subject, factSheet.length());
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
                    String plainUser = buildInterpretPlainUserPrompt(subject, vars, conclusion, interpretation);
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
     * 【本体应用生成的占断事实包】——把主体（体卦五行/方位/旺衰）、所问之事（用卦）、
     * 互变、生克、分项断语、万物类象、外应、应期、破解方案、修身根本等，结构化为一段
     * 事实清单，注入大模型提示。大模型据此「因事、因体」解析与建言，而非自由发挥（第四十九轮）。
     */
    private String buildDivinationFactSheet(String subject, Map<String, Object> vars) {
        // 单源复用：节点 16 已产出并写入 `divinationFactSheet` 变量者，直接用之，
        // 保证前端展示的「占断依据结构表」与大模型所见事实完全一致（第四十九轮）。
        String stored = str(vars.get("divinationFactSheet"));
        if (stored != null && !stored.isBlank()) {
            return stored;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("【所问之事】").append(subject == null ? "" : subject).append('\n');
        String body = str(vars.get("bodyTrigram"));
        String use = str(vars.get("useTrigram"));
        sb.append("【主体情况·体卦】");
        if (body != null && !body.isBlank()) {
            sb.append("体卦 ").append(body).append("（代表求测者自身；五行属").append(fivePhaseOf(body))
              .append("，方位").append(directionOf(body)).append("）");
        } else {
            sb.append("体卦未定");
        }
        String vitality = str(vars.get("bodyVitality"));
        if (vitality != null && !vitality.isBlank()) {
            sb.append("；卦气·").append(vitality);
        }
        sb.append('\n');
        sb.append("【所问之事·用卦】");
        if (use != null && !use.isBlank()) {
            sb.append("用卦 ").append(use).append("（代表所问之事；五行属").append(fivePhaseOf(use))
              .append("，方位").append(directionOf(use)).append("）");
        } else {
            sb.append("用卦未定");
        }
        sb.append('\n');
        appendLine(sb, "本卦", vars.get("originalHexagram"));
        appendLine(sb, "互卦", vars.get("mutualHexagram"));
        appendLine(sb, "变卦", vars.get("changedHexagram"));
        appendLine(sb, "体用生克", vars.get("shengKeType"));
        appendLine(sb, "吉凶等级", vars.get("auspiciousness"));
        appendLine(sb, "分项断语（因事而异）", vars.get("categoryVerdict"));
        appendLine(sb, "万物类象", vars.get("imageInterpretation"));
        appendLine(sb, "外应", vars.get("omenVerdict"));
        appendLine(sb, "应期", vars.get("responsePeriod"));
        String breakdown = str(vars.get("breakdownPlan"));
        if (breakdown != null && !breakdown.isBlank()) {
            sb.append("【破解方案·术（须转译为对该事该主体的具体建议）】\n").append(breakdown).append('\n');
        }
        appendLine(sb, "修身根本·道", vars.get("advice"));
        return sb.toString();
    }

    /**
     * 邵康节解读的因事因体建言要求（应用层提示词，非原著照录）。
     */
    private static String interpretTailorRules() {
        return "\n【解读要求】\n"
            + "1. 紧扣所问之事与主体情况：断卦解事必须因事、因体、因用而言，讲明此事对此主体的具体吉凶与影响，不作泛泛之谈或套话。\n"
            + "2. 逐环节说明依据：本卦、互卦、变卦、体用生克、五行、卦气旺衰、万物类象、外应、吉凶、应期各据何理，并引《梅花易数》原著相关篇目印证。\n"
            + "3. 将破解方案转译为对该事该主体的具体可行建议：扶体（如何补强自身）、抑用（如何削弱不利因素）、通关（如何引入中间方式化克为生）；方位、时令、色、物皆属取象喻示，须贴合现实场景转述为可操作做法，不可照抄术语或宣扬迷信。\n"
            + "4. 确有原著明文方可引述，严禁编造出处；无明文者标注（依常理推之）。\n"
            + "5. 用规范 Markdown 输出，条理清晰。\n";
    }

    /**
     * 白话文解释的因事因体建言要求（应用层提示词，非原著照录）。
     */
    private static String interpretPlainTailorRules() {
        return "\n【白话解释要求】\n"
            + "1. 面向问卦者本人，紧扣其所问之事与其自身（主体）情况，讲清此事对他意味着什么、为何如此断。\n"
            + "2. 分点讲清本卦/互卦/变卦、体卦与用卦、五行生克、卦气旺衰、万物类象、外应、吉凶、应期各自含义。\n"
            + "3. 重点把破解方案（扶体/抑用/通关，及方位·时令·色·物）转为该事该主体可落地的通俗建议；说明取象只是喻示，须结合现实场景理解，不宣扬迷信。\n"
            + "4. 术语随文浅释；不得编造出处。\n"
            + "5. 规范 Markdown 输出。\n";
    }

    /**
     * 组装「邵康节解读」的用户提示：占断事项 + 节点 16 汇总结论 + 本体应用生成的占断事实包
     * （含主体/体用五行旺衰、分项断语、破解方案等）+ 因事因体建言要求。
     */
    private String buildInterpretUserPrompt(String subject, Map<String, Object> vars, String conclusion) {
        StringBuilder sb = new StringBuilder();
        sb.append("【占断事项】").append(subject == null ? "" : subject).append('\n');
        sb.append("【占断结论·节点16汇总（以此为准绳）】\n").append(conclusion).append('\n');
        sb.append("\n【本体应用生成的占断事实包】\n").append(buildDivinationFactSheet(subject, vars)).append('\n');
        sb.append(interpretTailorRules());
        return sb.toString();
    }

    /**
     * 组装「白话文解释」的用户提示：占断事项 + 结论文本 + 事实包 + 已生成的古典解读，
     * 供大模型将其改写为贴身、可操作的白话建议。
     */
    private String buildInterpretPlainUserPrompt(String subject, Map<String, Object> vars,
                                                 String conclusion, String interpretation) {
        StringBuilder sb = new StringBuilder();
        sb.append("【占断事项】").append(subject == null ? "" : subject).append('\n');
        sb.append("【占断结论·节点16汇总】\n").append(conclusion).append('\n');
        sb.append("\n【本体应用生成的占断事实包】\n").append(buildDivinationFactSheet(subject, vars)).append('\n');
        sb.append("【邵康节古典解读（供你改写为白话）】\n").append(interpretation).append('\n');
        sb.append(interpretPlainTailorRules());
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
                // 原著卷之一·声音占：「凡闻声音，数得几数，起作上卦，加时数配作下卦」。
                // 闻声占例（邻夜扣门借物占）：「初扣一声…又扣五声…以一声属乾为上卦，以五声属巽为下卦，
                // 又以一乾五巽共六数，加酉时数…」——前后两声各取上、下卦（无第二声数时仍以时数配下卦）。
                int n = intv(vars.get("soundCount"));
                int n2 = intv(vars.get("soundCount2"));
                up = n;
                if (n2 > 0) {
                    low = n2;
                    moving = up + low + h;
                } else {
                    low = h;
                    moving = up + low;
                }
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
                // 原著卷之一·风觉占：「风从南方来者，为家人（南方属离火，合得风火家人卦）」——
                // 见风起卦，**以风（巽）为上卦、风来方位之卦为下卦**，后天定爻加时取动爻。
                // （原实现误以方位为上卦、时数为下卦，与原著例证「巽上离下=家人」相违。）
                up = 5; // 巽（风）
                low = dirToTrigram(str(vars.get("windDirection")));
                moving = up + low + h;
            }
            case "鸟占" -> {
                // 原著卷之一·鸟占：「数其只数者，如一只属乾，二只属兑，三只属离」（数→卦）为上卦，
                // 「看其方所者，即离南、坎北之数」为下卦，加时取动爻。
                String info = str(vars.get("birdInfo"));
                int cnt = firstNumber(info);
                up = (cnt > 0) ? cnt : xiangToNumber(info);
                low = dirFromTextOrDefault(info, h);
                moving = up + low + h;
            }
            case "听声音占" -> {
                // 原著卷之一·听声音占：「数其数（数目）者，如一声属乾，二声属兑」为上卦；
                // 「辨其物声，详其所属」（声自口出属兑、静物扣击属震、金声属乾、火声属离、
                // 土声属坤）或「验其方所（离南坎北）」为下卦，加时取动爻。
                String snd = str(vars.get("heardSound"));
                int cnt = firstNumber(snd);            // 数其数（声之数目）
                up = (cnt > 0) ? cnt : xiangToNumber(snd); // 声数→上卦；未言数则退取物象
                int st = soundToTrigram(snd);
                low = (st != 0) ? st : dirFromTextOrDefault(snd, h);
                moving = up + low + h;
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
                // 原著卷之一·为人占：「其例不一……或听语声起卦，或观其人品，或取诸身，或取诸物，
                // 或因其物色，触其外物，或以年月日时，或以书写来意」。其中「听语声」有分句取数
                // 之法、「书写来意」以字占之，二者须专法处理；余者（观人品/取诸身/取诸物/因其物色/
                // 触其外物）统以「取象为上卦、时数为下卦」。
                String info = str(vars.get("humanInfo"));
                if (info == null) {
                    info = str(vars.get("selfInfo"));
                }
                String sub = str(vars.get("humanSub")); // 为人占子法（听语声/书写来意/观人品…）
                if (sub == null || sub.isBlank()) {
                    sub = inferHumanSub(info);          // 未显式给出时按文义推断
                }
                if ("听语声".equals(sub)) {
                    // 原著卷之一·为人占·听语声：「如或一句，即如字数分之起卦（同字占）；
                    // 如人说两句，即用先一句为上卦，后一句为下卦；语多，则但用初听一句，
                    // 合末后闻一语，余句不用」。
                    String speech = str(vars.get("speechText"));
                    if (speech == null || speech.isBlank()) {
                        speech = info;
                    }
                    List<String> clauses = splitClauses(speech);
                    if (clauses.size() >= 3) {
                        up = clauses.get(0).length();                    // 初听一句（字数）
                        low = clauses.get(clauses.size() - 1).length();  // 末后闻一语（字数）
                    } else if (clauses.size() == 2) {
                        up = clauses.get(0).length();                    // 先一句为上卦
                        low = clauses.get(1).length();                   // 后一句为下卦
                    } else {
                        int[] ul = splitCharacters(speech == null ? "" : speech,
                                str(vars.get("toneNumbers")));           // 一句：如字数分（同字占）
                        up = ul[0];
                        low = ul[1];
                    }
                    moving = up + low + h;
                } else if ("书写来意".equals(sub)) {
                    // 原著卷之一·为人占·书写来意：「其人来占，或写来意，则以其字占之」——同字占。
                    String written = str(vars.get("text"));
                    if (written == null || written.isBlank()) {
                        written = info;
                    }
                    int[] ul = splitCharacters(written == null ? "" : written,
                            str(vars.get("toneNumbers")));
                    up = ul[0];
                    low = ul[1];
                    moving = up + low + h;
                } else {
                    // 观人品 / 取诸身 / 取诸物 / 因其物色 / 触其外物：取象为上卦、时数为下卦。
                    up = xiangToNumber(info);
                    low = h;
                    moving = up + low;
                }
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

    /**
     * 字占细分：按字数分子法产出上卦数、下卦数（原著卷之一·字占）。
     *
     * <p>取数口径依原著严格分层：①一字/二字/三字用**字画（笔画）**（Unihan `kTotalStrokes` 库）；
     * ②四字以上至十字「不必数画数，只以平仄声音调之」（平声一数、上声二数、去声三数、入声四数）：
     * 取数优先级＝**前端确认的逐字声调数** {@code tones} → **内置平水韵声调表**（`pingshui-tone.tsv`，第 28 轮引入）
     * → 二者皆缺则回退笔画近似并记注；
     * ③十一字以上「不用平仄声音调之，止用字数，平分上下，合二卦总数取爻」——即以**字数**平分。
     *
     * @param tones 前端确认的逐字平仄声调数串（1 平 / 2 上 / 3 去 / 4 入）；缺失时用内置平水韵表
     */
    private static int[] splitCharacters(String text, String tones) {
        int n = text.length();
        if (n == 0) {
            return new int[]{1, 1};
        }
        if (n >= 11) {
            // ③ 十一字以上：止用字数，平分上下（不再数画数、不用平仄）
            int up = n / 2;
            return new int[]{up, n - up};
        }
        int[] s = new int[n];
        // ② 四~十字：① 前端确认平仄 → ② 内置平水韵表 → ③ 回退笔画
        boolean toneMode = false;
        if (n >= 4) {
            toneMode = fillTones(tones, s) || fillTonesByRhyme(text, s);
        }
        if (!toneMode) {
            for (int i = 0; i < n; i++) {
                s[i] = stroke(text.charAt(i));
            }
        }
        switch (n) {
            case 1:
                // 一字占：原著「取其字画，以左为阳画、右为阴画…居左者取为上卦，居右者取为下卦，
                //         又以一字之阴阳全画取爻」。左/右半画需字库偏旁笔画分解，本流程以整字笔画
                //         （Unihan kTotalStrokes）近似（左右分画仍属字库分解层，见审查报告 C 类）。
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
                // 不可达（n≥11 已在上方按「止用字数」处理）：防御性回退，同「字数平分」
                int up = n / 2;
                return new int[]{up, n - up};
            }
        }
    }

    /** 单参重载（无前端平仄输入）：四~十字自动取内置平水韵声调表；不可得则回退笔画。 */
    private static int[] splitCharacters(String text) {
        return splitCharacters(text, null);
    }

    /**
     * 解析前端确认的逐字平仄声调数串（1 平 / 2 上 / 3 去 / 4 入）并填入 {@code out}。
     * 成功（去除非 1~4 字符后长度足够）返回 {@code true}。
     */
    private static boolean fillTones(String tones, int[] out) {
        if (tones == null) {
            return false;
        }
        String t = tones.replaceAll("[^1-4]", "");
        if (t.length() < out.length) {
            return false;
        }
        for (int i = 0; i < out.length; i++) {
            out[i] = t.charAt(i) - '0';
        }
        return true;
    }

    /**
     * 以内置平水韵声调表（资源 `plumblossom/pingshui-tone.tsv`）填逐字声调数（1平2上3去4入）。
     * 全字皆在表中返回 {@code true}；有字不在表中则返回 {@code false}（整体回退笔画）。
     */
    private static boolean fillTonesByRhyme(String text, int[] out) {
        for (int i = 0; i < out.length; i++) {
            int t = toneOf(text.charAt(i));
            if (t == 0) {
                return false;
            }
            out[i] = t;
        }
        return true;
    }

    /** 字占据数来源记注（供节点 2 日志/输出）：前端平仄 / 内置平水韵 / 笔画 / 字数。 */
    private static String charNumberSource(String text, String tones) {
        int n = text == null ? 0 : text.length();
        if (n == 0) {
            return "空";
        }
        if (n >= 11) {
            return "字数";
        }
        if (n >= 4) {
            if (tones != null && tones.replaceAll("[^1-4]", "").length() >= n) {
                return "前端平仄";
            }
            for (int i = 0; i < n; i++) {
                if (toneOf(text.charAt(i)) == 0) {
                    return "笔画（平仄缺）";
                }
            }
            return "内置平水韵";
        }
        return "笔画（Unihan）";
    }

    /**
     * 为人占·听语声分句（原著卷之一·为人占：「一句」「两句」「语多」三级）——
     * 按中文句读（，。！？；、）及空白断句，返回非空子句列表（保序）。
     */
    private static List<String> splitClauses(String speech) {
        List<String> clauses = new ArrayList<>();
        if (speech == null || speech.isBlank()) {
            return clauses;
        }
        for (String part : speech.split("[，。！？；、,.!?;\\s]+")) {
            if (!part.isBlank()) {
                clauses.add(part);
            }
        }
        return clauses;
    }

    /**
     * 为人占子法推断（未显式给出 {@code humanSub} 时）——由文义关键词判定「听语声」「书写来意」，
     * 其余一律归「取象」（观人品/取诸身/取诸物/因其物色/触其外物）。
     */
    private static String inferHumanSub(String info) {
        if (info == null || info.isBlank()) {
            return "取象";
        }
        if (containsAny(info, "语声", "说话", "他说", "闻其语", "听其语")) {
            return "听语声";
        }
        if (containsAny(info, "写来意", "书来意", "手书", "书写", "所写")) {
            return "书写来意";
        }
        return "取象";
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
        Set<String> g = ontoGenerates;
        if (g != null && !g.isEmpty()) {
            return g.contains(from + ">" + to);
        }
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
        Set<String> o = ontoOvercomes;
        if (o != null && !o.isEmpty()) {
            return o.contains(from + ">" + to);
        }
        return switch (from) {
            case "金" -> "木".equals(to);
            case "木" -> "土".equals(to);
            case "土" -> "水".equals(to);
            case "水" -> "火".equals(to);
            case "火" -> "金".equals(to);
            default -> false;
        };
    }

    /** 当季五行：春木/夏火/秋金/冬水/四季土（本体/DB 域知识优先，内置兜底）。 */
    private static String seasonFive(String season) {
        return loreMap("SEASON_FIVE", SEASON_FIVE).getOrDefault(season == null ? "" : season, "土");
    }

    /** 卦气旺衰：旺（当令）/相（当令所生）/休（生当令）/囚（克当令）/死（当令所克）。 */
    private static String vitalityOf(String phase, String season) {
        Map<String, String> v = ontoSeasonVitality;
        if (v != null && !v.isEmpty()) {
            String got = v.get(phase + "|" + season);
            if (got != null) {
                return got;
            }
        }
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

    // ==========================================================================
    // 破解（趋避）取材：十应四端（方卦 / 时令 / 五色 / 静物；卷之二·复明诸应）
    //   三法：扶体（增益体卦五行之气）、抑用（削克体卦之势）、
    //        通关（引中介五行，使克者贪生忘克）。接入节点 14（generateAdvice）。
    // ==========================================================================
    /** 五行枚举（供「生/克某五行」之反查；顺序为相生序，仅作遍历）。 */
    private static final String[] WU_XING = {"木", "火", "土", "金", "水"};

    /**
     * 五行→五方（卷之一·象数易理·十天干「甲乙东方木…壬癸北方水」；戊己中央土）。
     */
    private static final Map<String, String> FIVE_DIR = Map.of(
            "木", "东方", "火", "南方", "土", "中央（坤西南·艮东北）", "金", "西方", "水", "北方");

    /**
     * 五行→五色（卷之二·复明五色之应「青碧绿色属木，红紫赤色属火，白属金，黑属水，黄属土」）。
     */
    private static final Map<String, String> FIVE_COLOR = Map.of(
            "木", "青、碧、绿", "火", "红、紫、赤", "土", "黄", "金", "白", "水", "黑");

    /**
     * 五行→时令月（卷之二·复明时令之应「寅卯月木旺…辰戌丑未月土旺」）。
     */
    private static final Map<String, String> FIVE_SEASON = Map.of(
            "木", "春（寅卯月）", "火", "夏（巳午月）", "土", "四季（辰戌丑未月）",
            "金", "秋（申酉月）", "水", "冬（亥子月）");

    /**
     * 五行→器物（卷之一·八卦万物属类，取所属卦主象为器用之征）。
     */
    private static final Map<String, String> FIVE_OBJECT = Map.of(
            "木", "竹木花草之属", "火", "灯烛炉灶之属", "土", "山石田土布帛之属",
            "金", "金石珠玉之属", "水", "泉井鱼盐之属");

    /** 五行 X，使 {@code generates(X, target)}——即「生 target」之五行（扶体取材）。 */
    private static String generatorOf(String target) {
        for (String x : WU_XING) {
            if (generates(x, target)) {
                return x;
            }
        }
        return null;
    }

    /** 五行 X，使 {@code overcomes(X, target)}——即「克 target」之五行（抑用取材）。 */
    private static String overcomerOf(String target) {
        for (String x : WU_XING) {
            if (overcomes(x, target)) {
                return x;
            }
        }
        return null;
    }

    /** 通关五行：X 使克者 {@code generates(ke, X)} 且 {@code generates(X, victim)}（贪生忘克）。 */
    private static String mediatorBetween(String ke, String victim) {
        for (String x : WU_XING) {
            if (generates(ke, x) && generates(x, victim)) {
                return x;
            }
        }
        return null;
    }

    /** 五行→「方位 / 时令 / 色 / 物」四端一串（十应之方卦·时令·五色·静物四应）。 */
    private static String remedyLine(String five) {
        if (five == null) {
            return "";
        }
        String dir = fiveDirOf(five);
        if (dir == null) {
            return "";
        }
        return "方位「" + dir + "」、时令「" + fiveSeasonOf(five) + "」、色「"
                + fiveColorOf(five) + "」、物「" + fiveObjectOf(five) + "」";
    }

    /** 五行→方位：本体优先（rel:fiveDirection），内置兜底。 */
    private static String fiveDirOf(String five) {
        Map<String, String> m = ontoFiveDir;
        return (m != null && m.get(five) != null) ? m.get(five) : FIVE_DIR.get(five);
    }

    /** 五行→五色：本体优先（rel:fiveColor），内置兜底。 */
    private static String fiveColorOf(String five) {
        Map<String, String> m = ontoFiveColor;
        return (m != null && m.get(five) != null) ? m.get(five) : FIVE_COLOR.get(five);
    }

    /** 五行→时令：本体优先（rel:fiveSeason），内置兜底。 */
    private static String fiveSeasonOf(String five) {
        Map<String, String> m = ontoFiveSeason;
        return (m != null && m.get(five) != null) ? m.get(five) : FIVE_SEASON.get(five);
    }

    /** 五行→器物：本体优先（rel:fiveObject），内置兜底。 */
    private static String fiveObjectOf(String five) {
        Map<String, String> m = ontoFiveObject;
        return (m != null && m.get(five) != null) ? m.get(five) : FIVE_OBJECT.get(five);
    }

    /**
     * 凶/平之「破解（趋避）之法」（应用层增益，接入节点 14）。
     *
     * <p>原著依据：《观梅数诀》序「列图明五行生克衰旺之理，<b>分例指避凶趋吉之道</b>」；
     * 卷之二《洞玄歌》跋「人家可得警戒而<b>趋避之，或可转祸为福</b>」；《渔樵问对》
     * 「天降之灾，禳之奚益？<b>修德积善，君子常分</b>」（趋避以尽人事为本、禳解不取）。
     * 取材于十应四端：方卦之应（方位）、时令之应（时令）、五色之应（色）、静物之应（物）。
     *
     * <p>三法：①扶体——近「生体」之五行以壮体气；②抑用——就「克用」之五行以乘其衰
     * （卷之二·复明时令之应「克体卦气，宜乘衰」）；③通关——用克体时，引中介五行使
     * 「用贪生中介、中介转生体」，克患自解。
     */
    private String breakdownNote(String body, String use, String auspiciousness) {
        if (body == null || body.isBlank() || use == null || use.isBlank()) {
            return "";
        }
        boolean bad = "凶".equals(auspiciousness) || "大凶".equals(auspiciousness);
        boolean flat = "平".equals(auspiciousness);
        if (!bad && !flat) {
            return ""; // 吉/大吉：守正保泰即可，无需破解
        }
        String pb = fivePhaseOf(body);
        String pu = fivePhaseOf(use);
        if (pb == null || pu == null) {
            return "";
        }
        String y = generatorOf(pb);                              // 生体者（扶体）
        String z = overcomerOf(pu);                              // 克用者（抑用）
        String t = overcomes(pu, pb) ? mediatorBetween(pu, pb) : null; // 通关（惟用克体有之）

        StringBuilder sb = new StringBuilder();
        sb.append("趋避（破解）之理——《观梅数诀》序「分例指避凶趋吉之道」，")
          .append("《洞玄歌》云「人家可得警戒而趋避之，或可转祸为福」：");
        if (bad && t != null) {
            sb.append("①扶体——体卦「").append(body).append("」属").append(pb)
              .append("，宜近生").append(pb).append("之").append(y)
              .append("（").append(remedyLine(y)).append("）以壮体气；");
            sb.append("②抑用——用卦「").append(use).append("」属").append(pu)
              .append("，克体者").append(pu).append("也，宜就克").append(pu).append("之").append(z)
              .append("（").append(remedyLine(z)).append("）乘其衰，使克体之力不逞；");
            sb.append("③通关——").append(pu).append("克").append(pb).append("，宜引").append(t)
              .append("为中介（").append(remedyLine(t)).append("），则").append(pu).append("贪生").append(t)
              .append("、").append(t).append("转生").append(pb).append("，克患自解。");
        } else if (bad) {
            sb.append("①扶体——体卦「").append(body).append("」属").append(pb)
              .append("，体生用而泄气，宜近生").append(pb).append("之").append(y)
              .append("（").append(remedyLine(y)).append("）以补其元；");
            sb.append("②抑用——用卦「").append(use).append("」属").append(pu)
              .append("，宜就克").append(pu).append("之").append(z)
              .append("（").append(remedyLine(z)).append("）以减其耗。");
        } else {
            sb.append("①扶体——体卦「").append(body).append("」属").append(pb)
              .append("，体克用虽平而克物耗力，宜略近生").append(pb).append("之").append(y)
              .append("（").append(remedyLine(y)).append("）以固本；");
            sb.append("②持正——居中守正、静以待时，勿过用其刚。");
        }
        sb.append("根本（道）：《渔樵问对》云「天降之灾，禳之奚益？修德积善，君子常分」——")
          .append("趋避以尽人事，积善以培福田，二者兼之。");
        return sb.toString();
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

    /**
     * 听声音占·辨物声所属（原著卷之一）：声自口出（人语/动物鸣）属兑、静物扣击属震、
     * 金声属乾、火声属离、土声属坤。返回先天卦数，未识别返回 {@code 0}。
     */
    private static int soundToTrigram(String desc) {
        if (desc == null || desc.isBlank()) {
            return 0;
        }
        if (containsAny(desc, "语", "言", "笑", "哭", "鸣", "叫", "唱", "呼", "歌")) {
            return 2; // 兑——声自口出
        }
        if (containsAny(desc, "扣", "击", "敲", "拍", "槌", "板", "木", "鼓")) {
            return 4; // 震——静物扣击（木声）
        }
        if (containsAny(desc, "金", "钟", "磬", "钲", "铎", "铃", "铙")) {
            return 1; // 乾——金声
        }
        if (containsAny(desc, "火", "焰", "炬", "爆", "炮")) {
            return 3; // 离——火声
        }
        if (containsAny(desc, "土", "筑", "杵", "崩", "裂", "坡")) {
            return 8; // 坤——土声
        }
        return 0;
    }

    /**
     * 五色之应（原著卷之二·十应目论）：「五色不论卦象，但以所见之色推五行」——
     * 青/碧/绿属木、红/紫/赤属火、白属金、黑属水、黄属土。
     */
    private static String fivePhaseOfColor(String desc) {
        Map<String, String> m = ontoFiveColor;
        if (m != null && !m.isEmpty()) {
            for (Map.Entry<String, String> e : m.entrySet()) {
                for (String tok : e.getValue().split("[、,，/\\s]+")) {
                    if (!tok.isEmpty() && desc.contains(tok)) {
                        return e.getKey();
                    }
                }
            }
            return "土";
        }
        if (containsAny(desc, "青", "碧", "绿")) {
            return "木";
        }
        if (containsAny(desc, "红", "紫", "赤")) {
            return "火";
        }
        if (containsAny(desc, "白")) {
            return "金";
        }
        if (containsAny(desc, "黑")) {
            return "水";
        }
        if (containsAny(desc, "黄")) {
            return "土";
        }
        return "土";
    }

    /**
     * 静物之应·「不论卦象」之成败直断（原著卷之二·十应目论·静物：「物之圆者，事成；
     * 器之缺者，事败」——其「不分卦象者，但观其器物之兆」）。同类明文另见：
     * 占卜总诀（L997）「见圆物，事易成；见缺物，事终毁」、三要灵应篇（L1175）「物之圆者事成，
     * 缺者事败」、万物赋（L1638）「物团圆，指日而成；物破损，中途阻折」。
     *
     * <p>此类不推五行、不以卦象参断，仅以所见之物完缺直断成败。无圆缺之征则返回空串。
     */
    private static String stillObjectDirectVerdict(String desc) {
        if (desc == null || desc.isBlank()) {
            return "";
        }
        boolean round = containsAny(desc, "圆", "团圆");
        boolean broken = containsAny(desc, "缺", "破", "碎", "残");
        if (round && !broken) {
            return "物圆（团圆完整）者主事成，指日而成，吉";
        }
        if (broken && !round) {
            return "物缺（破损残缺）者主事败，中途阻折，不吉";
        }
        if (round && broken) {
            return "物有圆有缺，成败参半，宜详其主次";
        }
        return "";
    }

    /**
     * 取文本中首个整数（鸟占「数其只数」、听声音占「数其数目」等），无则 {@code 0}。
     * 兼容阿拉伯数字（3）与中文数词（三/十二/二十三）——原著例证作「一声属乾、二声属兑」。
     */
    private static int firstNumber(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        Matcher m = Pattern.compile("(\\d+)").matcher(text);
        int digitPos = m.find() ? m.start() : Integer.MAX_VALUE;
        int digitVal = 0;
        if (digitPos != Integer.MAX_VALUE) {
            try {
                digitVal = Integer.parseInt(m.group(1));
            } catch (NumberFormatException ignore) {
                /* 溢出不取 */
            }
        }
        int cnPos = -1;
        for (int i = 0; i < text.length(); i++) {
            if (cnDigit(text.charAt(i)) >= 0 || text.charAt(i) == '十' || text.charAt(i) == '两') {
                cnPos = i;
                break;
            }
        }
        if (cnPos >= 0 && cnPos < digitPos) {
            return parseChineseNumber(text, cnPos);
        }
        return digitVal;
    }

    /** 中文数词位值：一~九 → 1~9，十/两 由 {@link #parseChineseNumber} 特判；非数词返回 -1。 */
    private static int cnDigit(char c) {
        return switch (c) {
            case '一' -> 1; case '二' -> 2; case '三' -> 3; case '四' -> 4;
            case '五' -> 5; case '六' -> 6; case '七' -> 7; case '八' -> 8;
            case '九' -> 9;
            default -> -1;
        };
    }

    /** 自 {@code start} 起解析一段中文数词（一~九 / 十 / 两 / 十几 / 几十几），无有效数词返回 0。 */
    private static int parseChineseNumber(String text, int start) {
        int section = 0;
        int digit = -1;
        boolean any = false;
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '十') {
                section += (digit < 0 ? 1 : digit) * 10;
                digit = -1;
                any = true;
            } else if (c == '两') {
                digit = 2;
                any = true;
            } else {
                int v = cnDigit(c);
                if (v < 0) {
                    break;
                }
                digit = v;
                any = true;
            }
        }
        return any ? section + (digit < 0 ? 0 : digit) : 0;
    }

    /**
     * 外应 → 十应类目（原著卷之二·十应目论十一类：天时 / 地理 / 人事 / 时令 / 方卦 /
     * 动物 / 静物 / 言语 / 声音 / 五色 / 写字；未入类返回「外」）。
     */
    private static String tenResponseCategory(String desc) {
        if (desc == null || desc.isBlank()) {
            return "外";
        }
        if (containsAny(desc, "春", "夏", "秋", "冬", "时令", "节气", "月建", "寒", "暑")) {
            return "时令";
        }
        if (containsAny(desc, "晴", "雨", "雪", "风", "云", "雷", "日", "月", "阴", "天色", "天时")) {
            return "天时";
        }
        if (containsAny(desc, "山", "水", "河", "桥", "路", "井", "林", "野", "地理", "地")) {
            return "地理";
        }
        if (containsAny(desc, "老人", "老妇", "少女", "少男", "妇人", "男子", "僧", "尼", "童子", "官吏", "贵人", "尊长")) {
            return "人事";
        }
        if (containsAny(desc, "鸟", "雀", "鹊", "鸡", "犬", "牛", "马", "羊", "鱼", "虫", "兽", "禽")) {
            return "动物";
        }
        if (containsAny(desc, "花", "木", "器物", "桌椅", "衣", "屋", "门", "石", "钱")) {
            return "静物";
        }
        if (containsAny(desc, "语", "言", "呼", "笑", "哭", "叹", "骂")) {
            return "言语";
        }
        if (containsAny(desc, "声", "鸣", "响", "鼓", "钟", "磬", "铃", "歌")) {
            return "声音";
        }
        if (containsAny(desc, "色", "青", "碧", "绿", "赤", "黄", "白", "黑", "紫", "红")) {
            return "五色";
        }
        if (containsAny(desc, "字", "文", "榜", "联", "匾", "墨", "笔")) {
            return "写字";
        }
        // C13：方位独立成「方卦之应」（占者坐立方）——置于诸具体物象类之后，
        // 以免「见喜鹊鸣于东」之类由方位字误判为方卦（原「东」字单提即返方卦）。
        if (containsAny(desc, "东", "南", "西", "北", "方")) {
            return "方卦";
        }
        return "外";
    }

    /** A9：外应是否为「动象」（飞/走/鸣/坠/响/来等）；动象比静象更直接、更紧迫。 */
    private static boolean isMovingOmen(String desc) {
        return containsAny(desc, "飞", "走", "行", "鸣", "叫", "坠", "落", "响", "动", "来", "入", "过", "驰", "奔", "起", "摇");
    }

    /**
     * 卷之二·十应目论·各类之应「分应断法」（B38–B46）——原著于十应逐类明列「论卦象
     * 生克比和」与「不论卦象之兆」二途（L1350–1373）。此前节点 11 仅以取象卦统一泛断，
     * 未录各类专诀，此处依原著逐类照录补全（天时/地理/人事/时令/方卦/动物/静物/言语/声音）；
     * 五色已见 {@link #fivePhaseOfColor}，写字之应见 {@link #xieZiZhiYingNote}（复明写字之应，第二十五轮补录）。
     */
    private String tenCategoryNote(final String category, final String desc, final String body, final String season) {
        String d = desc == null ? "" : desc;
        final String cat = category == null ? "" : category;
        switch (cat) {
            case "天时": {
                final int t;
                final String key;
                if (containsAny(d, "晴霁", "日中")) {
                    t = 3;
                    key = "天时:晴霁";
                } else if (containsAny(d, "雨", "雪")) {
                    t = 6;
                    key = "天时:雨雪";
                } else if (containsAny(d, "雷", "风")) {
                    t = 4;
                    key = "天时:雷风";
                } else {
                    t = 1;
                    key = "天时:default";
                }
                return fillTpl(loreTpl("TEN_CATEGORY_NOTE", TEN_CATEGORY_NOTE, key),
                        "tri", TRI_NAME[t], "body", body, "tone", shengKeTone(t, body));
            }
            case "地理": {
                final int t;
                final String key;
                if (containsAny(d, "茂树", "秀竹", "竹", "林")) {
                    t = 4;
                    key = "地理:茂树";
                } else if (containsAny(d, "江湖", "河池", "川泽", "溪涧", "井", "水")) {
                    t = 6;
                    key = "地理:江湖";
                } else if (containsAny(d, "窑灶", "炉", "火")) {
                    t = 3;
                    key = "地理:窑灶";
                } else {
                    t = 7;
                    key = "地理:default";
                }
                return fillTpl(loreTpl("TEN_CATEGORY_NOTE", TEN_CATEGORY_NOTE, key),
                        "tri", TRI_NAME[t], "body", body, "tone", shengKeTone(t, body));
            }
            case "人事": {
                String who = "";
                if (containsAny(d, "老人", "尊长", "长者")) {
                    who = "今见老人，属乾";
                } else if (containsAny(d, "老妇", "老母", "妇人")) {
                    who = "今见老妇，属坤";
                } else if (containsAny(d, "少男", "童")) {
                    who = "今见少男，属艮";
                } else if (containsAny(d, "少女", "女子")) {
                    who = "今见少女，属兑";
                }
                return fillTpl(loreTpl("TEN_CATEGORY_NOTE", TEN_CATEGORY_NOTE, "人事:base"),
                        "who", who.isEmpty() ? "" : "（" + who + "）");
            }
            case "时令": {
                final String s = seasonFive(season);
                return fillTpl(loreTpl("TEN_CATEGORY_NOTE", TEN_CATEGORY_NOTE, "时令:base"), "five", s);
            }
            case "方卦": {
                final int dir = dirToTrigram(d);
                return fillTpl(loreTpl("TEN_CATEGORY_NOTE", TEN_CATEGORY_NOTE, "方卦:base"),
                        "dir", TRI_DIR[dir], "tri", TRI_NAME[dir]);
            }
            case "动物": {
                final String base = loreTpl("TEN_CATEGORY_NOTE", TEN_CATEGORY_NOTE, "动物:base");
                if (containsAny(d, "鹊")) {
                    return base + loreTpl("TEN_CATEGORY_NOTE", TEN_CATEGORY_NOTE, "动物:鹊");
                }
                if (containsAny(d, "鸦", "乌")) {
                    return base + loreTpl("TEN_CATEGORY_NOTE", TEN_CATEGORY_NOTE, "动物:鸦");
                }
                if (containsAny(d, "鸿雁", "雁")) {
                    return base + loreTpl("TEN_CATEGORY_NOTE", TEN_CATEGORY_NOTE, "动物:鸿雁");
                }
                if (containsAny(d, "鸡")) {
                    return base + loreTpl("TEN_CATEGORY_NOTE", TEN_CATEGORY_NOTE, "动物:鸡");
                }
                if (containsAny(d, "马")) {
                    return base + loreTpl("TEN_CATEGORY_NOTE", TEN_CATEGORY_NOTE, "动物:马");
                }
                return base;
            }
            case "静物":
                return loreTpl("TEN_CATEGORY_NOTE", TEN_CATEGORY_NOTE, "静物:base");
            case "言语":
                return loreTpl("TEN_CATEGORY_NOTE", TEN_CATEGORY_NOTE, "言语:base");
            case "声音":
                return loreTpl("TEN_CATEGORY_NOTE", TEN_CATEGORY_NOTE, "声音:base");
            default:
                return "";
        }
    }

    private static final Map<String, String> TEN_CATEGORY_NOTE = new LinkedHashMap<>();

    static {
        TEN_CATEGORY_NOTE.put("天时:晴霁", "〔天时之应〕晴霁日中为离之时（坤、兑为体则吉）；今时卦「{tri}」与体卦「{body}」：{tone}。");
        TEN_CATEGORY_NOTE.put("天时:雨雪", "〔天时之应〕雨雪为坎之时（震、巽为体则吉，离为体则不吉）；今时卦「{tri}」与体卦「{body}」：{tone}。");
        TEN_CATEGORY_NOTE.put("天时:雷风", "〔天时之应〕雷风为震、巽之时（离为体则吉，坤、艮为体则不吉）；今时卦「{tri}」与体卦「{body}」：{tone}。");
        TEN_CATEGORY_NOTE.put("天时:default", "〔天时之应〕天无云翳、明朗之际为乾之时（乾、兑为体比和而吉，坎为体逢生大吉，坤、艮为体泄气，震、巽为体见克不吉）；今时卦「{tri}」与体卦「{body}」：{tone}。");
        TEN_CATEGORY_NOTE.put("地理:茂树", "〔地理之应〕茂树秀竹为震之地（离与震、巽为体则吉，坤、艮为体则凶）；今地卦「{tri}」与体卦「{body}」：{tone}。");
        TEN_CATEGORY_NOTE.put("地理:江湖", "〔地理之应〕江湖河池川泽溪涧为坎之地（震、巽与坎为体则吉，离为体则不吉）；今地卦「{tri}」与体卦「{body}」：{tone}。");
        TEN_CATEGORY_NOTE.put("地理:窑灶", "〔地理之应〕窑灶之地为离（坤、艮并离为体则吉，乾、兑为体则不吉）；今地卦「{tri}」与体卦「{body}」：{tone}。");
        TEN_CATEGORY_NOTE.put("地理:default", "〔地理之应〕岩穴之地为艮（乾、兑与艮为体则吉，坎为体则不吉）；今地卦「{tri}」与体卦「{body}」：{tone}。");
        TEN_CATEGORY_NOTE.put("人事:base", "〔人事之应〕论卦象者，老人属乾、老妇属坤、艮为少男、兑为少女，生克比和同天时、地理之卦断之{who}；其不分卦象者，以人事之纷了见杂出、有吉有凶，随其吉凶而为之兆；又观其事，则亦为某人。");
        TEN_CATEGORY_NOTE.put("时令:base", "〔时令之应〕不必论卦象，但详其令、月日值之五行衰旺之气——{five}旺（旺者：寅卯之月日木旺、巳午火旺、申酉金旺、亥子水旺、辰戌丑未土旺；衰者：木旺则土衰、土旺则水衰、水旺则火衰、火旺则金衰、金旺则木衰）；是故生体之卦气宜值时之旺气、不宜衰气，如克体卦气宜乘衰。");
        TEN_CATEGORY_NOTE.put("方卦:base", "〔方卦之应〕占者所坐立之方位为{dir}（{tri}），以用卦参详——宜在本卦之方、为用卦生之方，不宜受用卦克；又凡气在之卦所在之方，又当审之——如水从坎来为坎卦气旺、水从坤艮来则坎之卦气衰，火从南来为离卦气旺、从北来则离之卦气衰，余皆仿此；大抵本卦之方、生为旺、受克为衰，宜以体卦参之——生体卦气宜受旺方，克体卦气宜受克方。又禁例：震、巽之方不论坤、艮，坤、艮之方不论坎，坎方不论离，离方不论乾，乾、兑之方不论震、巽（以其寓卦受方卦之克）。");
        TEN_CATEGORY_NOTE.put("动物:base", "〔动物之应〕论卦象者，乾马、坤牛、震龙、巽鸡、坎豕、离雉、艮狗、兑羊（螺蜂龟鳖为离象、鱼类为坎属），以体参详；不论卦象者，乌鸦报灾、灵鹊报喜、鸿雁主有书信、蛇虫防有毒害、鸡唱为家音、马嘶为动意。");
        TEN_CATEGORY_NOTE.put("动物:鹊", "今见鹊，传喜之兆。");
        TEN_CATEGORY_NOTE.put("动物:鸦", "今见鸦，报灾之兆。");
        TEN_CATEGORY_NOTE.put("动物:鸿雁", "今见鸿雁，主远信。");
        TEN_CATEGORY_NOTE.put("动物:鸡", "今闻鸡唱，为家音。");
        TEN_CATEGORY_NOTE.put("动物:马", "今见马嘶，为动意。");
        TEN_CATEGORY_NOTE.put("静物:base", "〔静物之应〕论卦象者，水属坎、火属离、木之气属震巽、金之气属乾兑、土之气属坤艮；其不分卦象者，但观器物之兆——物之圆者事成、器之缺者事败；又详器物为何物：笔砚主文书之事、袍笏主官职之事、樽俎之具有宴集、枷锁之具防官灾；百端不一，审其器物。");
        TEN_CATEGORY_NOTE.put("言语:base", "〔言语之应〕不论卦象，但详其所言之事绪——闻吉语则吉、闻凶语则凶；若闻斗丛言语喧集，难以决断；若定人少之处、言语可辨其事绪，则审其所言何事，心领而意会之；如说朝廷迁选可以求名、论江湖州郡主出行、言争讼之事主官司、言喜庆之事利婚姻。");
        TEN_CATEGORY_NOTE.put("声音:base", "〔声音之应〕论卦象者，雷为震、风声为巽、雨声为坎、水声为坎、鼓拍槌敲之声出于木者属震巽、钟磬铃铙之声出于金者属乾兑；又闻欢笑之声主有喜、悲愁之声主有忧、歌唱之声主快乐、怒号之声主争喧；物声则乌鸦报灾、鹊声传喜、鸿雁之声主远信、鸡凫之声为佳音。");
    }

    /** 应卦与体卦之五行生克比和（以体卦为主）：比和而吉 / 逢生 / 见克 / 泄气。 */
    private String shengKeTone(final int omenTri, final String body) {
        final String pOmen = fivePhaseOf(TRI_NAME[omenTri]);
        final String pBody = fivePhaseOf(body);
        if (pOmen.equals(pBody)) {
            return "比和而吉";
        }
        if (generates(pOmen, pBody)) {
            return "逢生（大吉）";
        }
        if (overcomes(pOmen, pBody)) {
            return "见克（不吉）";
        }
        return "泄气（体生之，为耗）";
    }

    /**
     * 卷之二·向背（B47）——「凡占卜求应，必须审其向背……向者相向而来、背者相背而去」（L1413–1415）。
     */
    private static String xiangBeiNote(final String desc) {
        String d = desc == null ? "" : desc;
        final boolean coming = containsAny(d, "来", "至", "入", "向");
        final boolean going = containsAny(d, "去", "过", "离", "背", "散");
        if (coming && !going) {
            return "〔向背〕应兆「相向而来」（向）：灾祥将至——如鸦报灾，鸦飞适来则灾将至；"
                    + "克体器物方来则祸将至，生体器物方来则吉。";
        }
        if (going && !coming) {
            return "〔向背〕应兆「相背而去」（背）：灾祥已过——如鸦飞而去则灾已过去；"
                    + "克体器物去则祸散，生体器物去则吉已过。";
        }
        return "";
    }

    /**
     * 卷之二·十应奥论 / 内外论（B49）——「十应固出于三要而妙乎三要」「内卦不可无外卦，
     * 外卦不可无内卦……必合内外卦而断之」（L1390–1404），不可「体用自体用，三要自三要」。
     */
    private static String tenResponsePrinciple() {
        return "〔十应奥论〕十应固出于三要而妙乎三要——三要吉而十应或反凶（如占产见少男、"
                + "占病见棺），须合内外卦而断，不可「体用自体用，三要自三要」（原卷之二·内外论/体用类）。";
    }

    /**
     * 卷之三·占卜十应诀·十应总目与正/互/变应（B50）——「凡占卜，以体卦为主，用为事应……
     * 必得十应之说而后可也……正应者，正卦之应也（即体用二卦决吉凶）。互应者，互卦之应也。
     * 变应者，变卦之应也」（L1779–1789）。
     */
    private static String tenResponseJueNote() {
        return "〔占卜十应诀〕十应者——正应（即体用二卦决吉凶）、互应（即互卦决吉凶）、"
                + "变应（即变卦决吉凶）、方应、日应、刻应、外应、天时应、地理应、人事应；"
                + "并以体卦为主，详其生克比和之理（卷之三·占卜十应诀）。";
    }

    /**
     * 卷之三·占卜十应诀·结语（B50）——「如占病症，互变中多有克体之卦，而本卦中又无生体之卦者，
     * 断不吉也。又看体衰旺，若体旺则庶几有望，体衰则无复生理。如是，又看诸应有生体者，
     * 险中有救；有克体则不可望安矣」（L1823）。
     */
    private static String tenResponseJueSummary(final String vitality) {
        StringBuilder sb = new StringBuilder("〔十应要旨〕以体卦为主，详其生克比和；"
                + "若体旺则险中有救、体衰则无复生理；诸应有生体者险中有救、有克体则不可望安");
        if (vitality != null && !vitality.isBlank()) {
            sb.append("（今体卦").append(vitality).append("）");
        }
        return sb.append("。").toString();
    }

    /**
     * 卷之三·占卜坐端之诀（B51）——「坐端者，以我之所坐为中，八位列于八方……随其方卦有生克
     * 之应者，以定所占之家吉凶也」（L1613–1624）。逐宫：乾（老人尊长）、坤（母亲阴人）、
     * 震（长子长孙）、坎（中男）、离（中女）、艮（少男）、兑（少女）；并附「问病受克之宫定病位」
     * （乾头／坤腹／震足／巽股／离目／坎耳及血／艮手指／兑口齿）。
     */
    // 【本体优先·内置兜底】坐端八方应兆已迁 div:ZuoDuanEntry + zuo_duan 表 + OBDA 映射；此表仅本体/DB 不可用时兜底。
    private static final Map<String, String> ZUO_DUAN = new LinkedHashMap<>();
    static {
        ZUO_DUAN.put("乾", "乾宫：土生之或有吉兆→主老人尊长分上见吉庆；火克之或有凶兆→主老人长上有忧");
        ZUO_DUAN.put("坤", "坤宫：火生之或有吉兆→主母亲阴人有吉利之喜；见克或有凶兆→主老母阴人有灾厄");
        ZUO_DUAN.put("震", "震宫：水生之及东方震宫有吉兆→喜在长子长孙；见克或见凶→长子长孙不利");
        ZUO_DUAN.put("坎", "坎宫：宜见五金及吉利之谶→喜在中男；土克或见凶→忧在中男");
        ZUO_DUAN.put("离", "离宫：喜木生之或有可应→中女有喜；遇克或见凶→中女有厄");
        ZUO_DUAN.put("艮", "艮宫：宜火生之、见吉→少男之喜；遇克或见凶→灾及少男，问产必不育");
        ZUO_DUAN.put("兑", "兑宫：土宜生之、见吉→少女有喜或有欢悦；遇克或见凶→少女有灾");
    }

    private static String zuoDuanNote(final String desc) {
        String d = desc == null ? "" : desc;
        Map<String, String> src = (ontoZuoDuan != null && !ontoZuoDuan.isEmpty()) ? ontoZuoDuan : ZUO_DUAN;
        for (Map.Entry<String, String> e : src.entrySet()) {
            if (d.contains(e.getKey() + "宫") || d.contains(e.getKey() + "方") || d.contains(e.getKey() + "位")) {
                return "〔坐端八方应兆〕" + e.getValue()
                        + "。问病以受克之宫定病位（乾头／坤腹／震足／巽股／离目／坎耳及血／艮手指／兑口齿）。";
            }
        }
        return "";
    }

    /**
     * 卷之三·论事十大应（论日辰秘文，L1824–1842）——「一行…十怒」逐姿态/情态之断语（照录）。
     * 十键俱全（裹头/跣足/担/券/卧/一行/二立/三坐/九喜/十怒；第二十五轮补足后五键，
     * 「行/立/坐/喜/怒」以序名键避与常语相混）。
     */
    // 【本体优先·内置兜底】论事十大应已迁 ome:TenResponses + shi_da_ying 表 + OBDA 映射；此表仅本体/DB 不可用时兜底。
    private static final Map<String, String> SHI_DA_YING = new LinkedHashMap<>();
    static {
        SHI_DA_YING.put("裹头", "七裹头：官司立见口舌。火，大官司，水土比和，财无，小人分上，口舌呕气。病主阴人小口灾。");
        SHI_DA_YING.put("跣足", "八跣足：官司破财，外人欺，心下惊慌。火主破财，土不凶。病有孝至。");
        SHI_DA_YING.put("担", "五担：官司被人自惊，与面说人成口舌。问信见水土得财。金木客至。病有犯，四肢沉重不能起。");
        SHI_DA_YING.put("券", "六券：官司不成，火有财，水土有灾，心下不安，有贵人，主口舌，不凶。");
        SHI_DA_YING.put("卧", "四卧：问官司，侧睡者，欲起必作，主阴人事。金有财，火事发破财。土水无财难就。土木有财。");
        // 第二十五轮·待考落地：补足「行/立/坐/喜/怒」五键，论事十大应始全（十键）。
        // 键取「一行/二立/三坐/九喜/十怒」之序名（避与「出行」「坐端」「喜鹊」等常语相混）。
        SHI_DA_YING.put("一行", "一行：问官司，属木，旺木有文书；属火，有官司；财金木，财有至。有客至问病，人火潮热，金水米浆。");
        SHI_DA_YING.put("二立", "二立：官司不发，水土无金，大小口舌，病不凶。财水土，有贵人至，文书发动。");
        SHI_DA_YING.put("三坐", "三坐：问官司，有讼不成。主财属火，主和劝。金败财，木得财。病脚，右足犯林木神，有祸不凶。");
        SHI_DA_YING.put("九喜", "九喜：官司自己无主，外人有请，劝官司。有酒肉，别人事。口舌纷纷，求财不许。不凶。");
        SHI_DA_YING.put("十怒", "十怒：官司主外人欺凌，不见官，主破财，倚人脱卸。火惊病凶。");
    }

    private static String shiDaYingNote(final String desc) {
        String d = desc == null ? "" : desc;
        Map<String, String> src = (ontoShiDaYing != null && !ontoShiDaYing.isEmpty()) ? ontoShiDaYing : SHI_DA_YING;
        for (Map.Entry<String, String> e : src.entrySet()) {
            if (d.contains(e.getKey())) {
                return "〔论事十大应〕" + e.getValue() + "（论日辰秘文，须合日辰参断）。";
            }
        }
        return "";
    }

    // ==========================================================================
    // 第二十五轮「待考全部落地」——卷之二/卷三「须专法输入 / 须爻辞 / 例示非诀」诸条：
    // 凡原著尚有文字者，一律照录归位（或依输入即断，或列备考），不再以【待考】悬置。
    // ==========================================================================

    /** 〔①〕卷之三·占卜十应诀·日应（L3589）：以体卦为主，看所占卦日属何、及体卦与本日衰旺；
     *  卦宜生体、宜比和，不宜克体，亦不宜体卦生之。本日所属卦气：寅卯木、巳午火、申酉金、
     *  亥子水、辰戌丑未土。须日支（或日干支）输入。 */
    private static final Map<String, String> RI_YING = new LinkedHashMap<>();

    static {
        String rule = "以体卦为主，看体卦与本日衰旺如何——卦宜生体、宜比和；不宜克体，亦不宜体卦生之。";
        RI_YING.put("木", "本日属木（寅卯日）。" + rule);
        RI_YING.put("火", "本日属火（巳午日）。" + rule);
        RI_YING.put("金", "本日属金（申酉日）。" + rule);
        RI_YING.put("水", "本日属水（亥子日）。" + rule);
        RI_YING.put("土", "本日属土（辰戌丑未日）。" + rule);
    }

    /** 地支 → 五行（日应/鸟兽兆用）。 */
    private static String zhiFive(final String zhi) {
        if (zhi == null || zhi.isBlank()) {
            return "";
        }
        char z = zhi.charAt(0);
        if ("寅卯".indexOf(z) >= 0) {
            return "木";
        }
        if ("巳午".indexOf(z) >= 0) {
            return "火";
        }
        if ("申酉".indexOf(z) >= 0) {
            return "金";
        }
        if ("亥子".indexOf(z) >= 0) {
            return "水";
        }
        if ("辰戌丑未".indexOf(z) >= 0) {
            return "土";
        }
        return "";
    }

    private static String riYingNote(final String dayZhi) {
        String f = zhiFive(dayZhi);
        if (f.isEmpty()) {
            return "";
        }
        return "〔日应〕" + dayZhi + "日，" + loreMap("RI_YING", RI_YING).get(f) + "（原卷之三·占卜十应诀）。";
    }

    /** 通用域知识载体取值：本体/DB 装载优先，未装载则回退内置 Map（{@code div:LoreTextEntry}）。 */
    private static Map<String, String> loreMap(final String collection, final Map<String, String> fallback) {
        if (ontoLore != null) {
            Map<String, String> m = ontoLore.get(collection);
            if (m != null && !m.isEmpty()) {
                return m;
            }
        }
        return fallback;
    }

    /** 整型域知识载体取值：本体/DB 装载且可解析优先，否则回退内置 Map（{@code div:LoreTextEntry}）。 */
    private static Map<String, Integer> loreMapInt(final String collection, final Map<String, Integer> fallback) {
        if (ontoLore != null) {
            Map<String, String> m = ontoLore.get(collection);
            if (m != null && !m.isEmpty()) {
                Map<String, Integer> r = new LinkedHashMap<>();
                for (Map.Entry<String, String> e : m.entrySet()) {
                    try {
                        r.put(e.getKey(), Integer.parseInt(e.getValue().trim()));
                    } catch (NumberFormatException ignored) {
                        // 非法数值跳过，交由内置兜底
                    }
                }
                if (!r.isEmpty()) {
                    return r;
                }
            }
        }
        return fallback;
    }

    /** 域知识模板取值：本体/DB 优先、回退内置；返回含 {占位符} 的断法模板（批次6）。 */
    private static String loreTpl(final String collection, final Map<String, String> fallback, final String key) {
        String t = loreMap(collection, fallback).get(key);
        return t == null ? "" : t;
    }

    /** 填充模板占位符 {name}（按 name,value 成对传入，批次6）。 */
    private static String fillTpl(final String template, final String... kv) {
        String r = template == null ? "" : template;
        for (int i = 0; i + 1 < kv.length; i += 2) {
            r = r.replace("{" + kv[i] + "}", kv[i + 1] == null ? "" : kv[i + 1]);
        }
        return r;
    }

    /** 批次2-续(3)：分类占断吉断/凶断（CATEGORY_RULE 拆为两个域知识集，本体/DB 优先、内置兜底）。 */
    private static String[] categoryRulePair(final String cat) {
        if (ontoLore != null) {
            Map<String, String> good = ontoLore.get("CATEGORY_RULE_GOOD");
            Map<String, String> bad = ontoLore.get("CATEGORY_RULE_BAD");
            if (good != null && bad != null) {
                String g = good.get(cat);
                String b = bad.get(cat);
                if (g != null && b != null) {
                    return new String[]{g, b};
                }
            }
        }
        return CATEGORY_RULE.getOrDefault(cat, new String[]{
                "以体为主、用为所应，参互变综合断之",
                "以体为主、用为所应，参互变综合断之"});
    }

    /** 批次2-续(3)：占病逐爻之断（QIAN_KUN_DISEASE_YAO 索引 1..6，本体/DB 优先、内置兜底）。 */
    private static String qianKunDiseaseYao(final int i) {
        if (ontoLore != null) {
            Map<String, String> m = ontoLore.get("QIAN_KUN_DISEASE_YAO");
            if (m != null && !m.isEmpty()) {
                String v = m.get(String.valueOf(i));
                if (v != null) {
                    return v;
                }
            }
        }
        return (i > 0 && i < QIAN_KUN_DISEASE_YAO.length) ? QIAN_KUN_DISEASE_YAO[i] : "";
    }

    /** 批次2-续(3)：图象补录条目（4 域知识集 NAME/DESC/TEXT/SRC 组装为 [篇目,说明,文,出处]，本体/DB 优先、内置兜底）。 */
    private static Map<String, String[]> tuXiangBuLu() {
        if (ontoLore != null) {
            Map<String, String> nm = ontoLore.get("TU_XIANG_NAME");
            if (nm != null && !nm.isEmpty()) {
                Map<String, String> de = ontoLore.getOrDefault("TU_XIANG_DESC", Map.of());
                Map<String, String> tx = ontoLore.getOrDefault("TU_XIANG_TEXT", Map.of());
                Map<String, String> sr = ontoLore.getOrDefault("TU_XIANG_SRC", Map.of());
                Map<String, String[]> out = new LinkedHashMap<>();
                for (Map.Entry<String, String> e : nm.entrySet()) {
                    String id = e.getKey();
                    out.put(id, new String[]{
                            e.getValue(),
                            de.getOrDefault(id, ""),
                            tx.getOrDefault(id, ""),
                            sr.getOrDefault(id, "")});
                }
                return out;
            }
        }
        return TU_XIANG_BU_LU;
    }

    /** 〔④〕卷之二·复明写字之应（L2748）——占卜之际，观所书墨色/笔误，以断贼盗丧服等应。 */
    private static String XIE_ZI_ZHI_YING =
            "淡中浓墨名为淬，浓墨中间薄似云，点画误书名鬼笔，定知贼在暗中缠。"
            + "涕为流泪防丧服，定主忧惊梦里眠。鬼笔误书防窃盗，定知方位与通传。此写字之应验也。";

    private static String wuZhaiZhanText() {
        return "〔屋宅之占诀〕" + "寅年十二月初一日午时起屋者，其家田姓，其占水风井变地风升，互见离、兑。"
                + "巽木为体，用卦坎水生之，虽兑金克木，得有离火，火虽无气，终是制金。"
                + "然有兑金，酉年月日亦当有损失之忧；亥子水年月日当有进益，或得水边之财，坎生体用也；"
                + "寅卯年当大快意，比和之气也。但家中必多口舌之聒，亦为兑也。木体近春，喜逢坎水，此居必能发旺。"
                + "二十九年后此屋当毁——盖二十九年者，全卦六卦之成数也。若非有兑在中，虽再见二十九年，屋当无恙也。"
                + "（同时王姓得雷火丰变震，二十二年后为火所焚；韩姓得益变中孚，三十一年后遇申酉年此居当毁。）"
                + "（占例示法——凡屋宅以全卦之数为年期、以生克卦气之年月断吉凶。）";
    }

    private static String qiWuZhanText() {
        return "〔器物占〕大抵占器物，并不喜见兑卦，盖兑为毁折也。若坎为体，则见兑无伤；乾卦为体亦无害。"
                + "其余卦体，逢兑不久即破。木之器物，或震、巽为体，见兑为用，必不禁耐用矣。破器之日，必申酉与卜年月日也。"
                + "又畜养之物，亦不宜乾、兑克体。种植之物，乾、兑克体必不成，如成必被斧斤之厄；种植之物宜见坎也。"
                + "又凡见器物，欲知其成毁，亦看卦体：无克者则久长，体逢克则不久。视其器物之气数，可久者以全卦之年数断之；"
                + "不可久者以月数断之；至速者以日数断之也。";
    }

    /** 〔⑧〕卷之三·变卦式八则（L3039–3147）——逐例示断（泽火革变泽山咸/地雷复变地泽临/天泽履变乾/
     *  泽火革变火雷噬嗑/归妹变火泽睽/泽天夬变兑/泽火革变艮/天水讼变兑）。例示非口诀，全录备考；
     *  「近取诸身／远取诸物」八卦配身、配畜之则并录。 */
    private static String bianGuaShiText() {
        return "〔变卦式八则·例示备考〕"
                + "一、泽火革变泽山咸：离卦初爻阳动变阴变艮卦；兑金为少女，离火克之，巽为股，乾金克之曰伤股，得艮土生入兑金，断曰不至于此。"
                + "二、地雷复变地泽临：木是用卦，断出软物、文章之体也，将出是罗经。"
                + "三、天泽履变乾：此卦断出是铁器之物，将出剃刀也。"
                + "四、泽火革变火雷噬嗑：此卦乃用爻木体、火夏，火得旺能出土，必土物也。"
                + "五、归妹卦变火泽睽：用爻属木，变火体卦属金，四爻变卦成艮土能生金，断出是铁也。"
                + "六、泽天夬变兑：此卦非金，是石，断是破磁碟也。"
                + "七、泽火革变艮：本卦得泽火革为少女，近物为口、远取羊；内离为中女，近目、远取雉；初爻变艮卦为土，"
                + "土能生金则扶起兑金之妹；次除去初爻移上四爻又成巽木，佐得伤股之灾；得初爻变艮土生于兑金，是故有救而不至于死也。"
                + "「近取诸身」：八卦乾头、坤腹、震足、巽股、坎耳、离目、兑口、艮手——人身；"
                + "「远取诸物」：乾马、坤牛、震龙、巽鸡、坎豕、离雉、艮狗、兑羊——畜道。"
                + "八、天水讼卦变兑。";
    }

    /** 〔③〕卷之三·占物类例·看爻辞断物（L3496）——观成卦、看爻辞以断所为何物（原文照录）。 */
    private static String ZHAN_WU_YAO_CI =
            "凡看物数，看其成卦，观其爻辞。如得乾，曰“潜龙勿用”，乃曰不可用之物；“见龙在田”，乃曰田中之物；"
            + "“或跃在渊”，乃曰水中之物；“亢龙有悔”，乃废物也。如得坤之“直、方、大”，乃曰直而方大之器物；"
            + "“括囊无咎”，乃曰包裹之物；“黄裳元吉”，乃曰黄色衣服之物，“其色玄黄”。“困为石”，乃曰石物或逢石而破；"
            + "“困于株林”，乃曰木物。又言爻辞，不言物类，而不能决者，须以八卦所属之象察之。";

    /** 〔⑦〕卷之三·观物用易例（L3538/L3540）——拈例示断（笼盛物→草木连根；钟覆物→玉绦环），须以爻辞物类训释（原文照录）。 */
    private static String GUAN_WU_YONG_YI =
            "有人以笼盛物者，算得地天泰之初变升，互见震、兑，曰：此必是草木类而生土中也。色青根黄，当连根之草木也。"
            + "盖爻辞曰：“拔茅连茹，以其汇。”乃曰：此乃干根之草木也。视之乃草木连根，新采于土中也。互震为青色，兑为黄根也。\n"
            + "又有以钟覆物者，令占之，得火风鼎之雷风恒。乃曰：此有声价气势之物，虽圆而今毁缺矣。其色白而可用。"
            + "盖其辞曰：“鼎玉铉，大吉。”互见乾、兑，虽圆而毁也。开视之，乃玉绦环，果破矣。";

    /** 〔⑩〕卷之三·万物赋·鸟兽时日兆（L3267）——依日干之禽兽兆断（须日干输入）。 */
    private static final Map<String, String> NIAO_SHOU_SHI_RI = new LinkedHashMap<>();

    static {
        NIAO_SHOU_SHI_RI.put("庚", "庚日见鸡鸣——此乃凶刃之杀（凶兆）。");
        NIAO_SHOU_SHI_RI.put("丁", "丁日见羊过——此乃凶刃之杀（凶兆）。");
        NIAO_SHOU_SHI_RI.put("己", "己日值马来——此皆食禄之兆（吉兆）。");
        NIAO_SHOU_SHI_RI.put("壬", "壬日有猪过——此皆食禄之兆（吉兆）。");
    }

    private static String niaoShouShiRiNote(final String dayGan) {
        if (dayGan == null || dayGan.isBlank()) {
            return "";
        }
        String t = loreMap("NIAO_SHOU_SHI_RI", NIAO_SHOU_SHI_RI).get(dayGan.substring(0, 1));
        if (t == null) {
            return "";
        }
        return "〔万物赋·鸟兽时日兆〕鹊噪而喜色已动、鸦鸣而祸事将临；牛羊猪犬日晨不见，金日遇之六畜有损，"
                + "木日见猪养猪必成。" + t + "（原卷之三·万物赋，须日干参断）。";
    }

    /** 〔⑨〕卷之三·起卦加数例·加姓（C16）——同日时数家求占则同卦，先生以「各姓加数」起卦，
     *  随各家之姓断之；凡冠婚葬事皆须加姓，婚姻必加二姓，无姓则数其声音。须姓氏/字数输入。 */
    /** 小整数 → 汉字（0–99；起卦加数例用）。 */
    private static String chineseNumber(final int n) {
        String[] d = {"零", "一", "二", "三", "四", "五", "六", "七", "八", "九"};
        if (n < 0 || n > 99) {
            return String.valueOf(n);
        }
        if (n < 10) {
            return d[n];
        }
        if (n < 20) {
            return "十" + (n % 10 == 0 ? "" : d[n % 10]);
        }
        return d[n / 10] + "十" + (n % 10 == 0 ? "" : d[n % 10]);
    }

    private static String jiaXingNote(final String surname, final int strokes) {
        StringBuilder sb = new StringBuilder("〔起卦加数例·加姓〕同一年月日时而数家求占，则同卦而吉凶莫辨。"
                + "故先生以各姓而加数——寅年三数、十二月十二、初一一，共十六；");
        if (surname != null && !surname.isBlank() && strokes > 0) {
            sb.append("今加").append(surname).append("姓").append(chineseNumber(strokes)).append("画，得")
              .append(chineseNumber(16 + strokes)).append("数：除二八一十六得四，震为上卦；")
              .append("又加所求之时数，总而除八得下卦、除六得动爻，遂成卦断之。");
        } else {
            sb.append("加王姓四画得二十数，除二八一十六得四，震为上卦；又加午时七数，总二十七数，"
                    + "除三八二十四得三，离为下卦；二十七中除四六二十四，零三为爻，得丰变震，互见兑、巽。"
                    + "（其田姓加以田字六画，得水风井变升；其韩姓加二十一画，得益变中孚。）");
        }
        sb.append("凡冠婚及葬事皆须加姓（冠葬加一姓足矣），婚姻男女大事必加二姓；"
                + "其人无姓亦必有名，不辨其字则数其声音，又无名则随所寓也（原卷之三·起卦加数例）。");
        return sb.toString();
    }

    /** 〔⑤〕附录·邵康节易数一撮金·起卦取爻与内外卦分配（L7723）——乾一兑二离三震四巽五坎六艮七坤八，
     *  上字为内卦、下字为外卦，合二字之画共成一卦，总数六除取爻（原文照录）。 */
    private static String YICUOJIN_ALLOC =
            "其法以楷书字，数其笔画，以起数得卦。须要诚心祈祷，随其所占信手写二字。数其笔画，即一点一撇亦算。"
            + "乾一、兑二、离三、震四、巽五、坎六、艮七、坤八。九复起乾，十复起兑，余仿此。"
            + "上字为内卦，下字为外卦，合二字之画共成一卦。即以总数六除取爻，如剩一数是初爻，剩二数是二爻，余仿此。"
            + "乃以爻诀断之，无不奇中。不可再占。";

    private static String xieZiZhiYingNote(final String desc) {
        String d = desc == null ? "" : desc;
        if (containsAny(d, "淡中浓墨", "浓墨", "鬼笔", "涕", "流泪", "误书", "写字")) {
            return "〔复明写字之应〕" + XIE_ZI_ZHI_YING + "（原卷之二·写字之应）。";
        }
        return "";
    }

    /** 占物类（占动物/占静物/物卦起例/形物占/验色占）之原【待考】诸条：器物占＋看爻辞断物＋观物用易例＋变卦式八则
     *  （第二十五轮已全数归位·照录备考；方法名沿用历史 {@code objectDaikaoText}）。 */
    private static String objectDaikaoText(final String method) {
        String m = method == null ? "" : method;
        if (!containsAny(m, "占物", "器物", "动物", "静物", "物卦", "形物", "验色")) {
            return "";
        }
        return qiWuZhanText() + "；" + ZHAN_WU_YAO_CI + "；" + GUAN_WU_YONG_YI + "；" + bianGuaShiText() + "。";
    }

    /**
     * 卷之二·观物洞玄歌（B55，L1418–1431）——「此歌多为**占宅气**而发……家之兴衰必有祯祥妖孽之谶」
     * 「五行金木水火土，生克先为主；青黄赤黑白之五形辨察要分明」「入门辨察见闻时，于此察兴衰」。
     * 逐兆照录（宅气/门墙/屋瓦/禽畜/草木/人物之兆）。
     */
    private static final Map<String, String> DONG_XUAN_GE = new LinkedHashMap<>();

    static {
        DONG_XUAN_GE.put("宅气如春", "「若还宅气如春意，家宅生和气」——主家宅和顺（吉）");
        DONG_XUAN_GE.put("冷落似秋", "「若然冷落似秋时，从此渐衰微」——主家门渐衰（凶）");
        DONG_XUAN_GE.put("馨香如兰", "「自然馨香如兰室，福至无虚日」——主福至不绝（吉）");
        DONG_XUAN_GE.put("秽薰腥", "「鸡豚猫犬秽薰腥，贫病至相侵」——主贫病交侵（凶）");
        DONG_XUAN_GE.put("男妆女饰", "「男妆女饰皆齐整，此去门风盛」——主门风兴盛（吉）");
        DONG_XUAN_GE.put("垢面", "「家人垢面与蓬头，定见有悲忧」——主有悲忧（凶）");
        DONG_XUAN_GE.put("儿啼妇叹", "「儿啼妇叹情怀悄，祸害道阴小」——主阴小有祸（凶）");
        DONG_XUAN_GE.put("老人无故泣", "「老人无故泣双垂，不日见愁悲」——主近日愁悲（凶）");
        DONG_XUAN_GE.put("墙壁缺", "「门前墙壁缺，家道中消歇」——主家道中落（凶）");
        DONG_XUAN_GE.put("向门流", "「溜漕水势向门流，财帛永难收」——主财帛难聚（凶）");
        DONG_XUAN_GE.put("屋上生奇草", "「忽然屋上生奇草，益荫人家好」——主益荫人家（吉）");
        DONG_XUAN_GE.put("绝尘埃", "「门户幽爽绝尘埃，必定出高才」——主出高才（吉）");
        DONG_XUAN_GE.put("破履当门", "「偶悬破履当门户，必有奴欺主」——主奴欺主（凶）");
        DONG_XUAN_GE.put("边门", "「长长破碎在边门，断不利家君」——主不利家长（凶）");
        DONG_XUAN_GE.put("桃花艳", "「遮门临井桃花艳，内有风情染」——主门内风情之染（凶）");
        DONG_XUAN_GE.put("高桐", "「屋前屋后有高桐，离别主人翁」——主离别主人（凶）");
        DONG_XUAN_GE.put("高梨树", "「井边倘种高梨树，长有离乡土」——主离乡（凶）");
        DONG_XUAN_GE.put("焚香", "「祠堂神主忽焚香，火厄恐相招」——主火厄（凶）");
        DONG_XUAN_GE.put("檐前瓦片", "「檐前瓦片当门堕，诸事愁崩破」——主诸事崩破（凶）");
        DONG_XUAN_GE.put("破碗", "「若抛破碗厕坑中，从此见贫穷」——主贫穷（凶）");
        DONG_XUAN_GE.put("灯在地", "「白昼不宜灯在地，死者还相继」——主丧事相继（凶）");
        DONG_XUAN_GE.put("鼠向日中", "「公然鼠向日中来，不日资财耗」——主资财耗散（凶）");
        DONG_XUAN_GE.put("牝鸡", "「牝鸡早晚鸣咿喔，阴盛家消索」——主阴盛家消（凶）");
        DONG_XUAN_GE.put("中堂犬吠", "「中堂犬吠立而啼，人眷有灾厄」——主人眷灾厄（凶）");
        DONG_XUAN_GE.put("鹊噪连声", "「清晨鹊噪连声继，远信行人至」——主远信行人至（吉）");
        DONG_XUAN_GE.put("蟒蛇", "「蟒蛇偶尔入人家，人病见妖邪」——主人病妖邪（凶）");
        DONG_XUAN_GE.put("雀群争逐", "「雀群争逐当门盛，口舌纷纷定」——主口舌纷争（凶）");
        DONG_XUAN_GE.put("鹏鸟", "「偶然鹏鸟（即鸱鸮也）叫当门，人口有迍逢」——主人口迍邅（凶）");
        DONG_XUAN_GE.put("群羊", "「入门若见有群羊，家主病瘟黄」——主家主病（凶）");
        DONG_XUAN_GE.put("舟船", "「舟船若安在平地，虽稳成淹滞」——主事成而淹滞");
        DONG_XUAN_GE.put("树荫过墙", "「他家树荫过墙来，多得横来财」——主得横财（吉）");
        DONG_XUAN_GE.put("石砌多残折", "「阶前石砌多残折，成事多衰灭」——主成事衰灭（凶）");
        DONG_XUAN_GE.put("茶果应声", "「入门茶果应声来，中馈主家财」——主中馈掌财（吉）");
        DONG_XUAN_GE.put("炊烟早", "「三餐时候炊烟早，家道渐基好」——主家道渐兴（吉）");
        DONG_XUAN_GE.put("宿火不成", "「连宵宿火不成时，人散与财离」——主人散财离（凶）");
    }

    /** 观物洞玄歌·占宅气兆应（卷之二 L1418–1431）；依所察之兆象取断。 */
    private static String dongXuanNote(final String desc) {
        String d = desc == null ? "" : desc;
        for (Map.Entry<String, String> e : loreMap("DONG_XUAN_GE", DONG_XUAN_GE).entrySet()) {
            if (d.contains(e.getKey())) {
                return "〔观物洞玄歌·占宅气〕" + e.getValue()
                        + "（卷之二·观物洞玄歌，多为占宅气而发，入门辨察见闻以察家之兴衰）。";
            }
        }
        return "";
    }

    /**
     * 卷之三·万物赋·闻见克应诸兆（B58，L1634–1638）——「未成卦而闻见之，乃已行之事；
     * 既定卦而观察之，乃未来之机」「声音言语，傍人谶兆」「逢吉兆而终知有喜，见凶谶而不免乎凶」。
     * 逐兆照录（闻见/言语/人物/器用/饮食之兆；涉「日干」之鸟兽兆另见 {@link #niaoShouShiRiNote}
     * （第二十五轮补录「待考落地」，依日干即断））。
     */
    private static final Map<String, String> WAN_WU_GE = new LinkedHashMap<>();

    static {
        WAN_WU_GE.put("喧闹", "「或闻何处喧闹，主有斗争」");
        WAN_WU_GE.put("笑语", "「或听此间笑语，必逢吉庆」");
        WAN_WU_GE.put("儿妇啼叹", "「儿妇啼叹，其家阴小有灾」");
        WAN_WU_GE.put("军来", "「吏至军来，必有官司词讼」");
        WAN_WU_GE.put("枷锁", "「或逢枷锁而枷锁临身」（主官灾缠身）");
        WAN_WU_GE.put("鞭杖", "「倘遇鞭杖而鞭杖必至」（主受刑责）");
        WAN_WU_GE.put("负肉", "「屠而负肉，此为骨肉有灾」");
        WAN_WU_GE.put("血光", "「倘逢血光，而又恐灾于孳畜」");
        WAN_WU_GE.put("药饵", "「师巫药饵，病患临门」");
        WAN_WU_GE.put("见饭", "「见饭则有犯家先」");
        WAN_WU_GE.put("逢酒", "「逢酒则欠神愿」");
        WAN_WU_GE.put("阴人至", "「阴人至则女子有厄」");
        WAN_WU_GE.put("阳人至", "「阳人至则男子当灾」");
        WAN_WU_GE.put("披麻带孝", "「披麻带孝，必然孝服临头」");
        WAN_WU_GE.put("持杖而号", "「持杖而号，定主号泣满室」");
        WAN_WU_GE.put("鼓乐", "「其或鼓乐声喧，又见酒杯器皿，若不迎婚嫁娶，定须会客宴酣」");
        WAN_WU_GE.put("酒杯器皿", "「又见酒杯器皿，若不迎婚嫁娶，定须会客宴酣」");
        WAN_WU_GE.put("柴炭", "「逢柴炭主忧」");
        WAN_WU_GE.put("折麦", "「折麦主悲」");
        WAN_WU_GE.put("禾与鞋", "「禾与鞋，万事和谐」");
        WAN_WU_GE.put("棋与药", "「棋与药，与人期约」");
        WAN_WU_GE.put("斧锯", "「斧锯必有修造」");
        WAN_WU_GE.put("粮储", "「粮储必有远行」");
        WAN_WU_GE.put("禽鸣", "「闻禽鸣，谋事虚说」");
        WAN_WU_GE.put("鼓声", "「听鼓声，交易空虚」");
        WAN_WU_GE.put("润睫", "「拭目润睫，内有哭泣之事」");
        WAN_WU_GE.put("持刃见血", "「持刃见血，外面有虫毒之谋」");
        WAN_WU_GE.put("柜箱", "「柜箱为藏财之用」");
        WAN_WU_GE.put("绳索", "「绳索为穿钱之物」");
        WAN_WU_GE.put("金帛宝货", "「逢金帛宝货之类，理必有成」");
        WAN_WU_GE.put("刀刃剑具", "「遇刀刃剑具之器，损而无益」");
        WAN_WU_GE.put("鸦鸣", "「鸦鸣而祸事将临」");
        WAN_WU_GE.put("鹊噪", "「鹊噪而喜色已动」");
        WAN_WU_GE.put("见水", "「见水为饮食酒汤」");
        WAN_WU_GE.put("遇火", "「遇火为煎炮烙炙」");
        WAN_WU_GE.put("见米", "「见米为一饭之得」");
        WAN_WU_GE.put("提壶", "「提壶为酌杯之礼」");
        WAN_WU_GE.put("刀砧", "「刀砧乃薰腥美味」");
    }

    /** 万物赋·闻见克应诸兆（卷之三 L1634–1638）；依所闻所见之兆取断。 */
    private static String wanWuNote(final String desc) {
        String d = desc == null ? "" : desc;
        for (Map.Entry<String, String> e : loreMap("WAN_WU_GE", WAN_WU_GE).entrySet()) {
            if (d.contains(e.getKey())) {
                return "〔万物赋·闻见克应〕" + e.getValue()
                        + "（卷之三·万物赋：未成卦而闻见为已行之事、既定卦而观察为未来之机，傍人谶兆，逢吉知喜、见凶不免）。";
            }
        }
        return "";
    }

    /**
     * 卷之三·万物赋·应期歌（B59，L1638）——「欲知应在何日，须观爻象值数。**巽五日而坤八日，
     * 离三朝而坎六朝**。又观远近克应，以断的实之相期：应远则全卦相同，应近而各时同断。
     * 假如天地否卦，上天一而下地八；设若泽火革卦，上兑二而下离三」。
     */
    private static String wanWuYingQiNote() {
        return "〔万物赋·应期〕欲知应在何日，须观爻象值数——巽五日、坤八日，离三朝、坎六朝；"
                + "应远则全卦相同、应近则各时同断（如天地否，上天一而下地八；泽火革，上兑二而下离三）"
                + "（卷之三·万物赋）。";
    }

    /**
     * 卷之三·体用生克之诀（B60，L1600–1605）——「生克之理，于内卦体用互变，一定之生克；
     * 若外卦，则须明其真生真克之五行，以分轻重，则祸福立应」：外应所值之卦，须辨「真」与「形色」，
     * 真者其克深、形色者其克浅（能克则不吉，不能克则不顺而已）；生体之卦亦当分辨。
     * 依体卦之五行举原著之例（金/木/火三体），余卦以类而推。
     */
    /**
     * 卷之二·先天后天论（B61，L1004）——「**先天之卦，定事应之期，则取之卦气**：如乾、兑则应
     * 庚、辛及五金之日，或乾为戌、亥之日时、兑为酉日时；如震、巽当应于甲、乙及五木之日，
     * 或震取卯、巽取辰之类。**后天则以卦数加时数**，总之而分行、卧、坐、立之迟速，以为事应之期。
     * 卦数时类应近，而不能决诸远者，必合先后之卦数取诀可也」。（余卦准五行干支配属，以类而推。）
     */
    private static final Map<String, String> XIAN_HOU_TIAN_YING_QI = new LinkedHashMap<>();

    static {
        XIAN_HOU_TIAN_YING_QI.put("应期", "〔先天后天论·应期〕先天之卦定事应之期，则取之卦气——乾、兑应庚、辛及五金之日"
                + "（或乾为戌、亥之日时、兑为酉日时）；震、巽应甲、乙及五木之日（或震取卯、巽取辰）；"
                + "余卦准五行干支配属以类而推（水壬癸、火丙丁、土戊己）。后天则以卦数加时数，"
                + "分行、卧、坐、立之迟速，以为事应之期；卦数时类应近，未能决诸远者，必合先后之卦数取诀"
                + "（卷之二·先天后天论）。");
    }

    private static String xianHouTianYingQiNote() {
        return loreMap("XIAN_HOU_TIAN_YING_QI", XIAN_HOU_TIAN_YING_QI).getOrDefault("应期", "");
    }

    private static final Map<String, String> ZHEN_SHENG_ZHEN_KE = new LinkedHashMap<>();

    static {
        ZHEN_SHENG_ZHEN_KE.put("PREFIX", "〔体用生克之诀·真生真克〕外卦须明其真生真克之五行以分轻重，则祸福立应——内卦体用互变为"
                + "一定之生克，若外卦（外应）则真者能克、形色者不能克（能克则不吉、不能克则不顺而已）。");
        ZHEN_SHENG_ZHEN_KE.put("金", "如乾、兑之金为体，见火则克——然炉中火、窑灶之火、烈焰巨炷，真火也，遇之不吉；"
                + "若色之红紫、形之中虚、槁木之离、日灶之火，则火之形色，不能克金，不为深忌；"
                + "一盏之灯、一炬之烛虽曰真火，微细而轻，小不利耳");
        ZHEN_SHENG_ZHEN_KE.put("木", "如震、巽之木体，遇金则克——然钗钏之金、金铂之金、成锭之金、杯盘之银、"
                + "器之锡、琐屑之铜铁，皆金也，此等之金岂能克木？木之所忌者，快刀锐刃、巨斧大锯，值之始有不吉");
        ZHEN_SHENG_ZHEN_KE.put("火", "如离火为体，见真水能克；然但见色之黑者、体之湿者、与夫血之类，皆坎之属，"
                + "终忌之而不深害");
        ZHEN_SHENG_ZHEN_KE.put("default", "乾、兑金体遇真火则克而形色之火不能克，震、巽木体遇真金（快刀巨斧）则克而"
                + "钗钏琐屑之金不能克，离火体遇真水则克而血、湿、黑色坎属终忌而不深害");
        ZHEN_SHENG_ZHEN_KE.put("SUFFIX", "。生体之卦亦当分辨：土与瓦器皆坤土，金遇之土能生金、瓦不能生；树木柴薪皆木，"
                + "离火值之柴薪生火之捷、树木之未伐者生火之迟；木为体，真水生木之福重，如豕如血虽坎之属，"
                + "生木之类轻也。余卦为体，所值外应克者皆以轻重克之；其余五行生克，并以类而推之"
                + "（卷之三·体用生克之诀）。");
    }

    private String zhenShengZhenKeNote(final String body) {
        final String p = fivePhaseOf(body);
        final Map<String, String> m = loreMap("ZHEN_SHENG_ZHEN_KE", ZHEN_SHENG_ZHEN_KE);
        String ex = m.get(p);
        if (ex == null) {
            ex = m.get("default");
        }
        return m.getOrDefault("PREFIX", "") + (ex == null ? "" : ex) + m.getOrDefault("SUFFIX", "");
    }

    /**
     * 卷之二·三要灵应篇·各类之应（B62，L1175–1305；第 36 轮逐兆补全）——「灵应」之兆象断诀：占物克应／
     * 仰观天文／俯察地理／人品／近取诸身／人事／器物／草木／禽兽／杂见观物／物叶音／自己／一动一静。逐类照录
     * （「拆字之应」属「相字」别系，C12 裁定不纳入，不录）。依所察兆象取断。
     *
     * <p><b>第 36 轮补录</b>：第二十轮落「类目」时，诸类「逐兆」有漏——地理漏「石乃坚心始得／沙乃放手即开」，
     * 人品漏「逢猎者／见渔者／见衽妇／遇丧者」，近取诸身漏「舌出掉者」，器物漏「络绎将成／笔墨俱在／升斗／
     * 尺剪／奕棋者／持斧锯／涤壶觞」，禽兽漏「乌鸦报灾」，杂见漏「凉扇」，自己漏「我适闲／我值忙」，
     * 一动一静漏「吉人之辞寡」，并补首类「占物克应」总纲。凡 20 条。此为「类目已落≠类中逐兆已落」之戒。
     */
    private static final Map<String, String> SAN_YAO_LING_YING = new LinkedHashMap<>();

    static {
        // 占物克应（L2341）——遇吉兆而顺有吉、见凶谶而不免乎凶；物之圆者事成、缺者事败。
        //（圆缺之直断另见 stillObjectDirectVerdict / B17；此处补其「吉兆/凶谶」总纲。第 36 轮补录。）
        SAN_YAO_LING_YING.put("吉兆", "〔三要灵应·占物克应〕遇吉兆而顺有吉");
        SAN_YAO_LING_YING.put("凶谶", "〔三要灵应·占物克应〕见凶谶而不免乎凶");
        // 仰观天文之应（L1184）
        SAN_YAO_LING_YING.put("云开见日", "〔三要灵应·天文之应〕云开见日，事必增辉（吉）");
        SAN_YAO_LING_YING.put("烟雾障空", "〔三要灵应·天文之应〕烟雾障空，物当失色（凶）");
        SAN_YAO_LING_YING.put("颠风", "〔三要灵应·天文之应〕忽颠风而飘荡");
        SAN_YAO_LING_YING.put("震雷", "〔三要灵应·天文之应〕遇震雷以虚惊");
        SAN_YAO_LING_YING.put("月当面", "〔三要灵应·天文之应〕月忽当面，宜近清光");
        SAN_YAO_LING_YING.put("沾衣", "〔三要灵应·天文之应〕雨乍沾衣，可蒙恩泽");
        // 俯察地理之应（L1192）
        SAN_YAO_LING_YING.put("重山", "〔三要灵应·地理之应〕重山为阻隔之际");
        SAN_YAO_LING_YING.put("重泽", "〔三要灵应·地理之应〕重泽为浸润之深");
        SAN_YAO_LING_YING.put("水流", "〔三要灵应·地理之应〕水流而事通");
        SAN_YAO_LING_YING.put("土积", "〔三要灵应·地理之应〕土积而事滞");
        SAN_YAO_LING_YING.put("浪激", "〔三要灵应·地理之应〕浪激主波涛之惊");
        SAN_YAO_LING_YING.put("坡崩", "〔三要灵应·地理之应〕坡崩主田土之失");
        SAN_YAO_LING_YING.put("旱沼", "〔三要灵应·地理之应〕旱沼之旁，心力俱竭");
        SAN_YAO_LING_YING.put("枯林", "〔三要灵应·地理之应〕枯林之下，相貌皆衰");
        // 第 36 轮补录（第二十轮类目已落、类中逐兆漏 2 条）
        SAN_YAO_LING_YING.put("石乃", "〔三要灵应·地理之应〕石乃坚心始得");
        SAN_YAO_LING_YING.put("沙乃", "〔三要灵应·地理之应〕沙乃放手即开");
        // 人品之应（L1203）
        SAN_YAO_LING_YING.put("显官", "〔三要灵应·人品之应〕荣宦显官，宜见其贵");
        SAN_YAO_LING_YING.put("富商", "〔三要灵应·人品之应〕富商巨贾，可问乎财");
        SAN_YAO_LING_YING.put("儿童哭泣", "〔三要灵应·人品之应〕儿童哭泣，忧子孙");
        SAN_YAO_LING_YING.put("吏卒", "〔三要灵应·人品之应〕吏卒叫嚣，忌官讼");
        SAN_YAO_LING_YING.put("二男二女", "〔三要灵应·人品之应〕二男二女，重婚之义");
        SAN_YAO_LING_YING.put("一僧一道", "〔三要灵应·人品之应〕一僧一道，独处之端");
        SAN_YAO_LING_YING.put("妇人笑语", "〔三要灵应·人品之应〕妇人笑语，则阴喜相逢");
        SAN_YAO_LING_YING.put("女子牵连", "〔三要灵应·人品之应〕女子牵连，则阴私见累");
        SAN_YAO_LING_YING.put("匠氏", "〔三要灵应·人品之应〕匠氏，主门庭改换");
        SAN_YAO_LING_YING.put("宰夫", "〔三要灵应·人品之应〕宰夫，则骨肉分离");
        // 第 36 轮补录（第二十轮类目已落、类中逐兆漏 4 条）
        SAN_YAO_LING_YING.put("逢猎", "〔三要灵应·人品之应〕逢猎者，得野外之财");
        SAN_YAO_LING_YING.put("见渔", "〔三要灵应·人品之应〕见渔者，有水边之利");
        SAN_YAO_LING_YING.put("衽妇", "〔三要灵应·人品之应〕见衽妇，则事萌于内");
        SAN_YAO_LING_YING.put("遇丧", "〔三要灵应·人品之应〕遇丧者，则虑根于心");
        // 近取诸身之应（L1218）
        SAN_YAO_LING_YING.put("摇手", "〔三要灵应·近取诸身之应〕摇手而莫为");
        SAN_YAO_LING_YING.put("掉头", "〔三要灵应·近取诸身之应〕掉头而不肯");
        SAN_YAO_LING_YING.put("喷嚏", "〔三要灵应·近取诸身之应〕拭目而喷嚏者方泣");
        SAN_YAO_LING_YING.put("搔首", "〔三要灵应·近取诸身之应〕搔首而弹垢者有忧");
        SAN_YAO_LING_YING.put("足动", "〔三要灵应·近取诸身之应〕足动者有行");
        SAN_YAO_LING_YING.put("交臂", "〔三要灵应·近取诸身之应〕交臂者有失");
        SAN_YAO_LING_YING.put("屈指", "〔三要灵应·近取诸身之应〕屈指者多阻节");
        SAN_YAO_LING_YING.put("嘘气", "〔三要灵应·近取诸身之应〕嘘气者主悲忧");
        SAN_YAO_LING_YING.put("背相向", "〔三要灵应·近取诸身之应〕背相向者防闪失");
        SAN_YAO_LING_YING.put("攘臂", "〔三要灵应·近取诸身之应〕偶攘臂者，争夺乃得");
        SAN_YAO_LING_YING.put("下膝", "〔三要灵应·近取诸身之应〕偶下膝者，屈抑而求");
        // 第 36 轮补录（第二十轮类目已落、类中逐兆漏 1 条）
        SAN_YAO_LING_YING.put("舌出掉", "〔三要灵应·近取诸身之应〕舌出掉者有是非");
        // 人事之应（L1226）
        SAN_YAO_LING_YING.put("童子授书", "〔三要灵应·人事之应〕童子授书，有词讼之端");
        SAN_YAO_LING_YING.put("笞仆", "〔三要灵应·人事之应〕主翁笞仆，防责罚之事");
        SAN_YAO_LING_YING.put("讲论经史", "〔三要灵应·人事之应〕讲论经史，事体徒间于虚说");
        SAN_YAO_LING_YING.put("语歌词曲", "〔三要灵应·人事之应〕语歌词曲，谋为转见于悠扬");
        SAN_YAO_LING_YING.put("博赌", "〔三要灵应·人事之应〕见博赌，主争斗之财");
        SAN_YAO_LING_YING.put("题写", "〔三要灵应·人事之应〕遇题写，主文书之事");
        SAN_YAO_LING_YING.put("携物", "〔三要灵应·人事之应〕偶携物者，受人提携");
        SAN_YAO_LING_YING.put("挽手", "〔三要灵应·人事之应〕适挽手者，遇事牵连");
        // 器物之应（L1234–1241）
        SAN_YAO_LING_YING.put("舟楫", "〔三要灵应·器物之应〕舟楫在水，凭其接引而行");
        SAN_YAO_LING_YING.put("车马", "〔三要灵应·器物之应〕车马登途，藉之负戴而往");
        SAN_YAO_LING_YING.put("张弓挟矢", "〔三要灵应·器物之应〕张弓挟矢者，必领荐");
        SAN_YAO_LING_YING.put("有箭无弓", "〔三要灵应·器物之应〕有箭无弓者，未可试");
        SAN_YAO_LING_YING.put("持刀执刃", "〔三要灵应·器物之应〕持刀执刃，须求快利之方");
        SAN_YAO_LING_YING.put("披甲操戈", "〔三要灵应·器物之应〕披甲操戈，可断刚强之柄");
        SAN_YAO_LING_YING.put("缫丝", "〔三要灵应·器物之应〕缫丝者，事务繁冗");
        SAN_YAO_LING_YING.put("围棋", "〔三要灵应·器物之应〕围棋者，眼目众多");
        SAN_YAO_LING_YING.put("妆花刻果", "〔三要灵应·器物之应〕妆花刻果，终非结实之因");
        SAN_YAO_LING_YING.put("画影描形", "〔三要灵应·器物之应〕画影描形，皆为妆点之类");
        SAN_YAO_LING_YING.put("倾盖", "〔三要灵应·器物之应〕偶倾盖者，主退权");
        SAN_YAO_LING_YING.put("临镜", "〔三要灵应·器物之应〕忽临镜者，可赴诏");
        SAN_YAO_LING_YING.put("抱贵器", "〔三要灵应·器物之应〕抱贵器者，有非常之用");
        SAN_YAO_LING_YING.put("负大木", "〔三要灵应·器物之应〕负大木者，有不小之财");
        SAN_YAO_LING_YING.put("蹴鞠", "〔三要灵应·器物之应〕见蹴鞠，有人拨剔");
        SAN_YAO_LING_YING.put("开锁钥", "〔三要灵应·器物之应〕开锁钥，遇事疏通");
        SAN_YAO_LING_YING.put("补器", "〔三要灵应·器物之应〕逢补器，终久难坚");
        SAN_YAO_LING_YING.put("磨镜", "〔三要灵应·器物之应〕值磨镜，再成始得");
        SAN_YAO_LING_YING.put("磨钢", "〔三要灵应·器物之应〕顽斧磨钢者，迟钝得利");
        SAN_YAO_LING_YING.put("快刀砍木", "〔三要灵应·器物之应〕快刀砍木者，利事伤材");
        SAN_YAO_LING_YING.put("裁衣", "〔三要灵应·器物之应〕裁衣服者，破后方成");
        SAN_YAO_LING_YING.put("造瓦器", "〔三要灵应·器物之应〕造瓦器者，成后乃破");
        SAN_YAO_LING_YING.put("张网", "〔三要灵应·器物之应〕张网者，摸之以空");
        SAN_YAO_LING_YING.put("挥扇", "〔三要灵应·器物之应〕或挥扇者，有相招之义");
        SAN_YAO_LING_YING.put("污衣", "〔三要灵应·器物之应〕或污衣者，防谋害之侵");
        // 第 36 轮补录（第二十轮类目已落、类中逐兆漏 7 条）
        SAN_YAO_LING_YING.put("络绎", "〔三要灵应·器物之应〕络绎将成，可以问职");
        SAN_YAO_LING_YING.put("笔墨", "〔三要灵应·器物之应〕笔墨俱在，可以求文");
        SAN_YAO_LING_YING.put("升斗", "〔三要灵应·器物之应〕升斗宜量料而前");
        SAN_YAO_LING_YING.put("尺剪", "〔三要灵应·器物之应〕尺剪可裁度以用");
        SAN_YAO_LING_YING.put("奕棋", "〔三要灵应·器物之应〕奕棋者，取之以计");
        SAN_YAO_LING_YING.put("持斧锯", "〔三要灵应·器物之应〕或持斧锯恐有伤");
        SAN_YAO_LING_YING.put("涤壶觞", "〔三要灵应·器物之应〕或涤壶觞恐有饮");
        // 草木之应（L1249）
        SAN_YAO_LING_YING.put("芝兰", "〔三要灵应·草木之应〕芝兰为物之瑞");
        SAN_YAO_LING_YING.put("松柏", "〔三要灵应·草木之应〕松柏为寿之坚");
        SAN_YAO_LING_YING.put("椿桧", "〔三要灵应·草木之应〕遇椿桧，则岁久年深");
        SAN_YAO_LING_YING.put("菌菰", "〔三要灵应·草木之应〕遇菌菰，则朝生暮死（占病占产得之即死之兆）");
        SAN_YAO_LING_YING.put("枝叶飘零", "〔三要灵应·草木之应〕枝叶飘零当萎谢");
        SAN_YAO_LING_YING.put("根核流落", "〔三要灵应·草木之应〕根核流落主牵连");
        SAN_YAO_LING_YING.put("奇葩", "〔三要灵应·草木之应〕奇葩端的虚花");
        SAN_YAO_LING_YING.put("嘉果", "〔三要灵应·草木之应〕嘉果可以结实");
        // 禽兽之应（L1257）
        // 第 36 轮补录（第二十轮类目已落、类中逐兆漏 1 条：乌鸦报灾）
        SAN_YAO_LING_YING.put("乌鸦", "〔三要灵应·禽兽之应〕乌鸦报灾");
        SAN_YAO_LING_YING.put("蟢虫", "〔三要灵应·禽兽之应〕蟢虫报喜");
        SAN_YAO_LING_YING.put("鸿雁", "〔三要灵应·禽兽之应〕鸿雁主朋友之信");
        SAN_YAO_LING_YING.put("蛇", "〔三要灵应·禽兽之应〕蛇虺防毒害之谋");
        SAN_YAO_LING_YING.put("鼠啮衣", "〔三要灵应·禽兽之应〕鼠啮衣，有小口之灾");
        SAN_YAO_LING_YING.put("雀噪檐", "〔三要灵应·禽兽之应〕雀噪檐，有远行之至");
        SAN_YAO_LING_YING.put("犬斗", "〔三要灵应·禽兽之应〕犬斗恐招盗贼");
        SAN_YAO_LING_YING.put("鸡斗", "〔三要灵应·禽兽之应〕鸡斗主有喧争");
        SAN_YAO_LING_YING.put("牵羊", "〔三要灵应·禽兽之应〕牵羊者，喜庆将临");
        SAN_YAO_LING_YING.put("骑马", "〔三要灵应·禽兽之应〕骑马者，出入皆利");
        SAN_YAO_LING_YING.put("猿猴攀木", "〔三要灵应·禽兽之应〕猿猴攀木，身心不定");
        SAN_YAO_LING_YING.put("鲤鱼出水", "〔三要灵应·禽兽之应〕鲤鱼出水，变化不凡");
        SAN_YAO_LING_YING.put("绳拴马", "〔三要灵应·禽兽之应〕绳拴马，疾病难安");
        SAN_YAO_LING_YING.put("架陷禽", "〔三要灵应·禽兽之应〕架陷禽，因人未脱");
        // 杂见观物之应（L1266）
        SAN_YAO_LING_YING.put("酒樽", "〔三要灵应·杂见观物之应〕酒樽忽破，乐极生悲");
        SAN_YAO_LING_YING.put("良医", "〔三要灵应·杂见观物之应〕良医道逢，难中有救");
        SAN_YAO_LING_YING.put("藤萝", "〔三要灵应·杂见观物之应〕藤萝之类堪依倚");
        SAN_YAO_LING_YING.put("虎豹", "〔三要灵应·杂见观物之应〕虎豹之象可施威");
        SAN_YAO_LING_YING.put("耕田锄地", "〔三要灵应·杂见观物之应〕耕田锄地者，事势必翻");
        SAN_YAO_LING_YING.put("破竹剖竿", "〔三要灵应·杂见观物之应〕破竹剖竿者，事势必顺");
        SAN_YAO_LING_YING.put("春花秋月", "〔三要灵应·杂见观物之应〕春花秋月，虽无实而阚景");
        SAN_YAO_LING_YING.put("夏绵冬葛", "〔三要灵应·杂见观物之应〕夏绵冬葛，虽有用而背时");
        SAN_YAO_LING_YING.put("晴伞", "〔三要灵应·杂见观物之应〕晴伞，渐逢闲废");
        SAN_YAO_LING_YING.put("泡影电光", "〔三要灵应·杂见观物之应〕泡影电光，虚幻难信");
        SAN_YAO_LING_YING.put("蛛丝蚕茧", "〔三要灵应·杂见观物之应〕蛛丝蚕茧，巧计方成");
        // 第 36 轮补录（第二十轮类目已落、类中逐兆漏 1 条：凉扇）
        SAN_YAO_LING_YING.put("凉扇", "〔三要灵应·杂见观物之应〕凉扇，多主气捐");
        // 物叶音之义（L1285）
        SAN_YAO_LING_YING.put("鹿", "〔三要灵应·物叶音〕见鹿可以问禄");
        SAN_YAO_LING_YING.put("蜂", "〔三要灵应·物叶音〕见蜂可以言封");
        SAN_YAO_LING_YING.put("梨", "〔三要灵应·物叶音〕梨主分离");
        SAN_YAO_LING_YING.put("桃", "〔三要灵应·物叶音〕桃主逃走");
        SAN_YAO_LING_YING.put("见李", "〔三要灵应·物叶音〕见李则问讼得理");
        SAN_YAO_LING_YING.put("逢冠", "〔三要灵应·物叶音〕逢冠则问名得官");
        SAN_YAO_LING_YING.put("鞋", "〔三要灵应·物叶音〕鞋为百事和谐");
        SAN_YAO_LING_YING.put("榼", "〔三要灵应·物叶音〕榼为诸事可合");
        // 自己之应（L1294）
        SAN_YAO_LING_YING.put("我心忧", "〔三要灵应·自己之应〕我心忧者，彼事亦忧");
        SAN_YAO_LING_YING.put("我心乐", "〔三要灵应·自己之应〕我心乐者，彼事亦乐");
        // 第 36 轮补录（第二十轮类目已落、类中逐兆漏 2 条）
        SAN_YAO_LING_YING.put("我适闲", "〔三要灵应·自己之应〕我适闲，彼当从容");
        SAN_YAO_LING_YING.put("我值忙", "〔三要灵应·自己之应〕我值忙，彼当窘迫");
        // 一动一静之应（观人辞气，L1302）
        SAN_YAO_LING_YING.put("将叛者", "〔三要灵应·动静之应〕将叛者，其辞惭");
        SAN_YAO_LING_YING.put("将疑者", "〔三要灵应·动静之应〕将疑者，其辞支");
        // 第 36 轮补录（第二十轮类目已落、类中逐兆漏 1 条：吉人之辞寡）
        SAN_YAO_LING_YING.put("吉人", "〔三要灵应·动静之应〕吉人之辞寡");
        SAN_YAO_LING_YING.put("躁人", "〔三要灵应·动静之应〕躁人之辞多");
        SAN_YAO_LING_YING.put("诬善", "〔三要灵应·动静之应〕诬善之人，其辞游");
        SAN_YAO_LING_YING.put("失其守者", "〔三要灵应·动静之应〕失其守者，其辞屈");
    }

    /** 三要灵应篇·各类之应（B62）；依所察兆象取断。 */
    private static String sanYaoLingYingNote(final String desc) {
        String d = desc == null ? "" : desc;
        for (Map.Entry<String, String> e : loreMap("SAN_YAO_LING_YING", SAN_YAO_LING_YING).entrySet()) {
            if (d.contains(e.getKey())) {
                return e.getValue() + "（卷之二·三要灵应篇）。";
            }
        }
        return "";
    }


    // ==========================================================================
    // 卷之四·字画指迷 / 卷之五·拆字杂编：「相字」断法（B68）
    // 依《本体顶层设计》§56（节点6 类象来源含「卷之四/卷之五」）与 §112（symbols.owl ← 万物类象…拆字），
    // 「相字（拆字）」为独立取象来源：照录 六神笔式/起六神卦诀/五行笔式/八卦断/六神形式/五行全备/
    // 神煞/笔画歌诀/玄黄克应歌/玄黄笔法歌/辨别五行歌/辨别六神歌/八卦辨/五行歌并式/相字短诀/
    // 分类断法/字例/赋文 共 18 类。仅作节点 9「万物类象取象」之取象与补断；
    // 依 C12 节点 16 不设独立「字画拆字」栏（字画取象并入「万物类象」）。
    // ==========================================================================
    private static final Map<String, Map<String, String>> XIANGZI_RULE = new LinkedHashMap<>();

    private static void putXz(String cat, String key, String text) {
        XIANGZI_RULE.computeIfAbsent(cat, k -> new LinkedHashMap<>()).put(key, text);
    }

    static {
        putXz("六神笔式", "青龙", "笔式：丿、乀（蚕头燕额）。主事：主喜事。丿、乀——青龙要停匀，百事皆吉。青龙笔动喜还生，谋用营求事事通。人口增添财禄厚，主人日下尽亨通。");
        putXz("六神笔式", "朱雀", "笔式：乂（尖短交加）。主事：主官事、文书动，主失财、有口舌、生横事。乂——朱雀临身文书动，主失财，有口舌，生横事。忌惹人，有忧惊之事。朱雀交加口舌多，令人家内不安和。若逢水命方无怪，他命逢时有怨疴。");
        putXz("六神笔式", "勾陈", "笔式：勹（弯弓斜月）。主事：主惊忧、迟滞、留连，忌土田，是非未决。勹——勾陈主惊忧、迟滞。忌土田。是非未决，并惹闲非。勾陈逢者事交加，谋事中间事事差。田宅官司多挠括，是非门内有喧哗。");
        putXz("六神笔式", "腾蛇", "笔式：乙、乞（长曲势如行）。主事：主忧虑、梦不祥、作事多阻、有喧争。乙、乞——腾蛇主忧虑，梦不祥，作事多阻，有喧争，惹旧愁，宜守静。腾蛇遇者主惊虚，家宅逢之尽不宁。出入官谋宜慎取，免教仆马有灾形。");
        putXz("六神笔式", "白虎", "笔式：兄、凡、圭（尾尖口阔）。主事：主丧灾、孝服、疾病、官鬼、口舌。兄、凡、圭——白虎主有不祥之招。产、病有孝服，及官鬼，惹口舌，在囚狱。白虎逢之灾孝来，出门凡事不和谐。便防失脱家财损，足疾忧人百事乖。");
        putXz("六神笔式", "玄武", "笔式：厶、么（体态方尖）。主事：主盗贼、失脱、家宅流离。厶、么——玄武贵人华盖，主盗财，亦难寻。玄武动时主失脱，家宅流离慎方活。更防阴小有灾危，又至小人生拮括。");
        putXz("起六神卦诀", "甲", "青龙");
        putXz("起六神卦诀", "乙", "青龙");
        putXz("起六神卦诀", "丙", "朱雀");
        putXz("起六神卦诀", "丁", "朱雀");
        putXz("起六神卦诀", "戊", "勾陈");
        putXz("起六神卦诀", "己", "腾蛇");
        putXz("起六神卦诀", "庚", "白虎");
        putXz("起六神卦诀", "辛", "白虎");
        putXz("起六神卦诀", "壬", "玄武");
        putXz("起六神卦诀", "癸", "玄武");
        putXz("五行笔式", "水", "水笔式：水圆多性巧。浊者定昏迷。水泛为不定。水走必东西。");
        putXz("五行笔式", "火", "火笔式：火重性不常。火燥见灾殃。火多攻心腹。火轻足衣粮。");
        putXz("五行笔式", "土", "土笔式：土重根基好。土轻离祖居。土滞破田宅。土定无虚图。");
        putXz("五行笔式", "金", "金笔式：金方利身主。金重性多刚。金走为神动。慷慨及门墙。");
        putXz("五行笔式", "木", "木笔式：木长性聪明。木短定功名。木多才学敏。木斜废支撑。");
        putXz("八卦断", "乾", "乾宫笔法如鸡脚，父母初年早见伤。若不早年离侍下，也须抱疾及为凶。");
        putXz("八卦断", "坤", "坤宫属母看荣华，切忌勾陈杀带斜。一点定分荣禄位，一生富贵最堪夸。");
        putXz("八卦断", "艮", "艮位排来兄弟宫，勾陈位笔性他凶。纵然不克并州破，也主商参吴楚中。");
        putXz("八卦断", "巽", "巽宫带口子难逢，见子须知有克刑。饶君五个与三个，未免难为一个成。");
        putXz("八卦断", "震", "震位东方一位间，要他笔正莫凋残。若逢祐断须沾疾，腰脚交他不得安。");
        putXz("八卦断", "离", "离是南方火位居，看他一点定荣枯。若还圆净荣官禄，燥火炎炎定不愚。");
        putXz("八卦断", "坎", "坎为财帛定卦位，水星笔横占他方。若见笔尖无大小，根基至老主荣昌。");
        putXz("八卦断", "兑", "兑位西方太白间，只宜正直莫凋残。若然坑陷并尖缺，妻子骄奢保守难。");
        putXz("六神形式", "青龙", "丿、乀。蚕头燕额是青龙。凡撇捺长而有头角之样，即作青龙，如撇短则不足以成青龙之式。不拘撇捺皆化木。如无须角，虽长亦非青龙。");
        putXz("六神形式", "朱雀", "乂、丿。尖短交加朱雀神。撇短而有尖嘴之形，则为朱雀。主文书事，原属火无化。");
        putXz("六神形式", "腾蛇", "乙、乞。腾蛇长曲势如行。其样如蛇，皆化火看，亦主文书及惊怪等。");
        putXz("六神形式", "勾陈", "勹、乙。弯弓斜月勾陈象。凡带长者是也，属土，无化，主羁滞。");
        putXz("六神形式", "白虎", "兄、凡、圭。尾尖口阔方为虎。口不开者，非虎也。化作金用，主疾病，凶兆也。");
        putXz("六神形式", "玄武", "厶、么、云。体态方尖玄武形。化木主盗贼事，又主波浪险阻等事。");
        putXz("五行全备", "一点", "丶：如一点端正，无破绽鸦嘴等形，则是五行全。如不合式，仍属水。");
        putXz("五行全备", "一画", "一：亦五行全。此象乃庖羲氏画卦之初而混元一气之数也。");
        putXz("五行全备", "太极", "〇：此太极未分时，亦五行全大之象也。");
        putXz("五行全备", "四匡", "口：歌曰「四匡无风全五行」，是亦五行全也。如「国」字、「园」字之类，四匡紧紧不透风乃是。如笔稀者不是；口小者属金，亦不是。此地之象也。");
        putXz("五行全备", "总诀", "一点一画五行全，试看首尾秘为占。点画若无疵笔露，功名发达享高年。");
        putXz("神煞", "贵神", "中、上、贝、日、月、大、人");
        putXz("神煞", "喜神", "士、口、言、鸟。士头口体喜神俱，嫁娶婚姻百事宜。只怕重重见火土，许多克伐反非奇。");
        putXz("神煞", "福星", "不、田（凡子孙动者亦作福星看）。");
        putXz("神煞", "文星", "二、乂、曰、子");
        putXz("神煞", "印星", "口、囗、子");
        putXz("神煞", "马星", "灬、辶、走。马星原是弯弓脚，四点原来用亦同。");
        putXz("神煞", "禄神", "甲禄在寅，乙禄在卯，丙戊禄在巳，丁己禄在午，庚禄在申，辛禄在酉，壬禄在亥，癸禄在子（俱以占者年庚本命，于求之笔画为准）。");
        putXz("神煞", "会神", "田、曰、云、禺。有田有日会神兴，见客逢人不必寻。");
        putXz("神煞", "生神", "一、丶、元、甲、子、初（一者数之始，元者鸿朦之初，甲子乃干支之首，故皆为生神）。");
        putXz("神煞", "亡神", "十、千、百、万、贞、亥、癸（十千百万皆数之终，贞乃元之尽，亥癸是干支之末，故为亡神）。");
        putXz("神煞", "家神", "宀、乇、火（灶神以四点同）、土（奥神）、堂（香火神）、水（井神，三点亦同用）。");
        putXz("神煞", "官符", "宀、付");
        putXz("神煞", "文书", "二、乂、丿、乙（朱雀、腾蛇皆是）。");
        putXz("神煞", "灾煞（病符）", "巛、宀、火、广、丙、矢（字中见旧太岁亦为病符星）。");
        putXz("神煞", "天狗煞", "字中见太岁，前年干支是也（如子午见戌，申年见壬，皆是）。");
        putXz("神煞", "科名星", "禾、斗（以本人年甲所属是科名，如甲乙以一直，丙人以一撇，皆科名也）。年干所属是科名，禾斗皆为首占星。有此求名皆遂意，如无考试定成空。");
        putXz("神煞", "丧门", "白、兄");
        putXz("神煞", "空亡", "即六甲空亡，「甲子旬中空戌亥」之类是也（假如甲子旬中空，占即以腰间一点为亥空，以长画为戌空）。");
        putXz("神煞", "宜神", "子为财之宜神，鬼为父之宜神，兄为子之宜神，财为鬼之宜神，父为兄之宜神是也。");
        putXz("神煞", "忌神", "子为鬼之忌神，鬼为兄之忌神，兄为财之忌神，财为父之忌神，父为子之忌神是也。");
        putXz("神煞", "主神", "眼前小事日千寻，代友占亲看纳音。疾病官非详本命，字中末笔主终身。");
        putXz("神煞", "用神", "官鬼父母才兄子，据事参详要仔细。认定一笔作用神，此为相字真消息。");
        putXz("笔画歌诀", "笔画稳重", "衣食丰隆");
        putXz("笔画歌诀", "笔画平直", "丰衣足食");
        putXz("笔画歌诀", "笔画端正", "衣禄铁定");
        putXz("笔画歌诀", "笔画分明", "决定前程");
        putXz("笔画歌诀", "笔画圆静", "富贵双并");
        putXz("笔画歌诀", "笔画肥浓", "富贵无穷");
        putXz("笔画歌诀", "笔画洁净", "功名可决");
        putXz("笔画歌诀", "笔画轻快", "诸事通泰");
        putXz("笔画歌诀", "笔画刚健", "力量识见");
        putXz("笔画歌诀", "笔画精神", "必有声名");
        putXz("笔画歌诀", "笔画光发", "荣显通达");
        putXz("笔画歌诀", "笔画气势", "慷慨意志");
        putXz("笔画歌诀", "笔画宽洪", "逞英逞雄");
        putXz("笔画歌诀", "笔画尖小", "其人必了");
        putXz("笔画歌诀", "笔画如线", "有识有见");
        putXz("笔画歌诀", "笔画似绳", "一世平宁");
        putXz("笔画歌诀", "笔画挑剔", "奸巧衣食");
        putXz("笔画歌诀", "笔画乌梅", "面相恢恢");
        putXz("笔画歌诀", "笔画懒淡", "兄弟离散");
        putXz("笔画歌诀", "笔画分扫", "破家必早");
        putXz("笔画歌诀", "笔画弯曲", "奸巧百出");
        putXz("笔画歌诀", "笔画迭荡", "一生浮浪");
        putXz("笔画歌诀", "笔画枯槁", "财物虚耗");
        putXz("笔画歌诀", "笔画糊涂", "愚蠢无谋");
        putXz("笔画歌诀", "笔画粘滞", "是非招怪");
        putXz("笔画歌诀", "笔画大小", "有歉有好");
        putXz("笔画歌诀", "笔画高低", "说是说非");
        putXz("笔画歌诀", "笔画淡泊", "疮痍克剥");
        putXz("笔画歌诀", "笔画反覆", "心常不定");
        putXz("笔画歌诀", "笔画破淬", "家事常退");
        putXz("笔画歌诀", "笔画欹斜", "飘泊生涯");
        putXz("笔画歌诀", "笔画恶浊", "无知无学");
        putXz("笔画歌诀", "笔画如蛇", "常不在宅");
        putXz("笔画歌诀", "笔画偏侧", "衣食断隔");
        putXz("笔画歌诀", "笔似鼓槌", "至老寒微");
        putXz("笔画歌诀", "笔势如针", "此人毒心");
        putXz("笔画歌诀", "笔势勾斜", "官事交加");
        putXz("笔画歌诀", "笔势如钩", "害人不休");
        putXz("笔画歌诀", "笔势散乱", "财谷绝断");
        putXz("笔画歌诀", "笔格常奇", "诀以别之");
        putXz("玄黄克应歌", "鹊声", "听得鹊声云有喜");
        putXz("玄黄克应歌", "鸦叫", "偶逢鸦叫祸无移");
        putXz("玄黄克应歌", "欢言", "倘是欢言多吉庆");
        putXz("玄黄克应歌", "愁语", "若闻愁语见伤悲");
        putXz("玄黄克应歌", "带花带酒", "带花带酒忧还退");
        putXz("玄黄克应歌", "遇醢逢醯", "遇醢逢醯事转迷");
        putXz("玄黄克应歌", "有人抱婴儿至", "好把阴阳两字推");
        putXz("玄黄克应歌", "男人抱子", "男人抱子占儿女");
        putXz("玄黄克应歌", "妇人抱子", "妇人抱子问熊罴");
        putXz("玄黄克应歌", "一女一子", "一女一子成好事");
        putXz("玄黄克应歌", "群阴相挽", "群阴相挽是仍非");
        putXz("玄黄克应歌", "女人携女子", "若见女人携女子，阴私连累主官非");
        putXz("玄黄克应歌", "写字宽衣带", "忽然写字宽衣带，诸事从今可解围");
        putXz("玄黄克应歌", "跛子瞽人持杖", "跛子瞽人持杖至，所谋蹇滞不能为");
        putXz("玄黄克应歌", "竹杖麻鞋", "竹杖麻鞋防孝服");
        putXz("玄黄克应歌", "权衡柄印", "权衡柄印主操持");
        putXz("玄黄克应歌", "见果", "见果断之能结果");
        putXz("玄黄克应歌", "逢衣", "逢衣须说问良医");
        putXz("玄黄克应歌", "丹青神鬼像", "若见丹青神鬼像，断他神鬼事相随");
        putXz("玄黄克应歌", "画翎毛花果", "若画翎毛花果类，必然妆点事须知");
        putXz("玄黄克应歌", "击磬敲椎", "有时击磬敲椎响，定有佳音早晚期");
        putXz("玄黄克应歌", "寺观铃铙钟鼓", "寺观铃铙钟鼓类，要知仙佛与禳祈");
        putXz("玄黄克应歌", "携鱼雁物", "倘是携来鱼雁物，友朋音信写相思");
        putXz("玄黄克应歌", "逢梅", "逢梅可说娣媒动");
        putXz("玄黄克应歌", "见李", "见李公私理不亏");
        putXz("玄黄克应歌", "见肉", "见肉定须忧骨肉");
        putXz("玄黄克应歌", "见梨", "见梨只怕有分离");
        putXz("玄黄克应歌", "仕宦官员至", "仕宦官员俄顷至，贵人相遇不移时");
        putXz("玄黄克应歌", "出笔拔毫", "出笔拔毫通远信");
        putXz("玄黄克应歌", "笔头落地", "笔头落地事皆迟");
        putXz("玄黄克应歌", "墨断", "墨断须防田土散");
        putXz("玄黄克应歌", "砚干池", "财空写砚忽干池");
        putXz("玄黄克应歌", "犬吠如号", "犬吠如号忧哭泣");
        putXz("玄黄克应歌", "猫呼哀绝", "猫呼哀绝有人欺");
        putXz("玄黄克应歌", "见鼠", "贼盗将临休见鼠");
        putXz("玄黄克应歌", "闻鸡", "喜人摧动爱闻鸡");
        putXz("玄黄克应歌", "马嘶", "马嘶必定有人至");
        putXz("玄黄克应歌", "鹊噪", "鹊噪还应远客归");
        putXz("玄黄克应歌", "字是硃书", "字是硃书忧血疾，不然火厄有忧危");
        putXz("玄黄克应歌", "蟢子", "笔下忽然来蟢子，分明吉庆喜无疑");
        putXz("玄黄克应歌", "叶上写", "叶上写来多怨望");
        putXz("玄黄克应歌", "花间书字", "花间书字色情迷");
        putXz("玄黄克应歌", "果树边", "果树边旁能结果");
        putXz("玄黄克应歌", "竹间", "竹间阻节事迟疑");
        putXz("玄黄克应歌", "晴书日雨书水", "晴宜书日雨宜水，夏火秋金总是时");
        putXz("玄黄笔法歌", "厂反", "反旁无一好，十个十重灾。旁里推详看，临机数上排。");
        putXz("玄黄笔法歌", "廴走", "走远字如何，须防失脱多。若还来问病，死兆不安和。");
        putXz("玄黄笔法歌", "孑系", "系绞同丝绊，干事主留连。却喜财公问，傍看日数言。");
        putXz("玄黄笔法歌", "阝卩", "附邑旁边事，当从左右推。兑宫知事定，震位事重为。");
        putXz("玄黄笔法歌", "灬二", "四点皆为火，逢寅过于通。若还书一画，百事尽成空。");
        putXz("玄黄笔法歌", "亻彳", "卓立人傍字，谋为倚傍成。若还来问病，死去又逢生。");
        putXz("玄黄笔法歌", "之辶", "之绕身必动，看其内必凶。问病也须忌，其余却少通。");
        putXz("玄黄笔法歌", "弓", "弓件休乾用，反处日难凭。先自无弦了，如何得箭行。");
        putXz("玄黄笔法歌", "山宂", "宂下灾祸字，占家更问官。更推从来用，凶吉就中看。");
        putXz("玄黄笔法歌", "人冫", "两点旁边字，还知凝滞攒。要问端的处，旁取吉凶看。");
        putXz("玄黄笔法歌", "吕叩", "双口相排立，因知恸哭声。各逢于戈日，亦主泪如倾。");
        putXz("玄黄笔法歌", "户尸", "户下尸不动，休来占病看。其余皆是吉，即断作平安。");
        putXz("玄黄笔法歌", "阝阜", "阜邑旁边字，当为仔细推。兑宫知事息，震位又重为。");
        putXz("玄黄笔法歌", "衣礼", "礼字旁边折，必定见生财。疋字如逢见，须从人正来。");
        putXz("玄黄笔法歌", "月骨", "骨傍人有祸，囚狱一重来。门内生荆棘，施设不和谐。");
        putXz("玄黄笔法歌", "身自", "自家身傍限，分明身不全。有谋难得遂，即日是多煎。");
        putXz("玄黄笔法歌", "反定", "定绕自来看，身必有所动。吉凶意如何，相里临时用。");
        putXz("玄黄笔法歌", "山山", "山下灾祥字，占家宜用官。更推从西用，凶吉数中安。");
        putXz("玄黄笔法歌", "人欠", "欠字从西体，须知望用难。吹嘘无首尾，不用滞眉看。");
        putXz("玄黄笔法歌", "禾禾", "禾边刀是利，春季则为殃。夏日宜更改，人中好举扬。");
        putXz("玄黄笔法歌", "耳耳", "耳畔虽有纪，轻则是虚声。旺事宜重用，取谋合有成。");
        putXz("玄黄笔法歌", "彐", "彐字作横山，取之衣禄渐明矣。又作日间防破。");
        putXz("玄黄笔法歌", "申", "申字作破田煞，常人不辨破田之说，用事重成之义也。");
        putXz("玄黄笔法歌", "田", "田字藏器待时，头足有所争，争而有所私，忌田产不宁。");
        putXz("玄黄笔法歌", "黄", "黄字作廿一后，方得萌芽；又作廿一用，可喜也。又云：上有一堆草，中有一条梁，撑杀由八郎。");
        putXz("辨别五行歌", "其1", "一：横画连勾作土称，一挑一捺俱为金。撇长撇短皆为火，横直交加土最深。有直不斜方是木，学者方明正五行。");
        putXz("辨别五行歌", "其2", "二：一点悬空土迸尘，三直相连化水名。孤直无依为冷木，腹中横短作囊金。点边得撇为炎火，五行变化在其中。");
        putXz("辨别五行歌", "其3", "三：三横两短若无钩，乃为湿木水中流。两点如挑金在水，八字相须火可求。空云独作寒金断，好把心勾比木舟。");
        putXz("辨别五行歌", "其4", "四：无勾之画土稍寒，直非端正木休参。围中横满无源水，口小金方莫错谈。四匡无风全五事，用心辨别莫疑难。");
        putXz("辨别五行歌", "其5", "五：穿心撇捺火陶金，走之平稳水溶溶。直中一捺金伤木，踢起无尖不是金。数点笔连休作火，奇奇偶偶水源清。");
        putXz("辨别五行歌", "其6", "六：无直无钩独有横，水因土化复何云。点挑撇捺同相聚，其总将来化土音。四点不连真化火，孤行一笔五行同。");
        putXz("辨别六神歌", "总诀", "蚕头燕额是青龙，尖短交加朱雀神。弯弓斜月勾陈象，腾蛇长曲势如行。尾尖口阔为白虎，体态方尖玄武行。此即六神真妙诀，断事详占要认真。");
        putXz("八卦辨", "总诀", "口形为兑捺为乾，三画无伤乾亦然。三点同来方是坎，撇如双见作离占。土山居上名为艮，居下为坤不必言。蛇形孤撇皆从巽，云首龙头震占先。详明八卦知凶吉，学者参求理自全。");
        putXz("五行歌并式", "总诀", "木瘦金方水主肥，土形敦厚背如龟。上尖下阔名为火，字像人形一样推。");
        putXz("相字短诀", "相字心易", "凡写两字，止看一字。盖字多必乱，若谋事之类，亦必移时方可再看。");
        putXz("相字短诀", "辨字式", "富人字多稳重无枯淡；贵人字多清奇、长画肥大；贫人之字多枯淡无精神；贱人字多散乱带空亡；百工字多挑跃；商贾字多远迩；男子字多开阔；妇人字多逼侧。余皆浓淡肥瘦、斜正分明之类断之。");
        putXz("相字短诀", "当添亦添", "如官员写「尹」字，乃君字首断，其人必见上位，定不禄而还，以君无口故也；如书「君」字，乃郡旁，其人当得郡守。");
        putXz("相字短诀", "当减亦减", "如「树」字，中有「吉」字，写得好者，则减去两边，只是言吉。");
        putXz("相字短诀", "以卦而断", "如「震」字，春则得时，冬则无气，皆以其卦言之。");
        putXz("相字短诀", "以时而言", "如草木字，春夏则生旺有财，秋冬则衰替多灾。风云气候之候亦然。");
        putXz("相字短诀", "以禽兽而断", "如「牛」字，则为人劳苦。春夏劳苦，秋冬安逸。");
        putXz("相字短诀", "取类而言", "如「楼」字，笔画多不可分解，以楼取义，乃重屋也；重屋拆开，乃千里尸至，问字人必有人死在外、尸至之事。");
        putXz("相字短诀", "偏旁侵客", "如宀乃家字头，若笔画破断，乃是破家宅，必退；如写作「山」字形，必兴门户；如「山」有缺笔，乃悬针之山，必大凶也。");
        putXz("相字短诀", "有所喜", "如问财，见金宝偏旁及禾斗之类，决好。");
        putXz("相字短诀", "有所忌", "如问病，见土木；及问讼见「血」「井」字，皆凶。");
        putXz("相字短诀", "有所闻", "如问病，忌闻悲泣声；占财不宜破碎声。");
        putXz("相字短诀", "有所见", "如「立」字见雨下或水声，则成「泣」字；又如「言」字见「犬」成「獄」字，问病讼皆忌之。");
        putXz("相字短诀", "视势", "每遇人写来，必别是何字：如「天」字乃「夫」字及「失」字基址——女人写妨夫，男子写有失。");
        putXz("相字短诀", "象人", "凡字必别是何人写，亦象人而言：如「天」字，秀才问科第则来年有望，官员求官宜勉力，庶人占病未安，讼者未了。");
        putXz("分类断法", "问婚姻", "凡字写得相粘者，可成。又字画直落成双者，可成。字中间阔而不粘，及直横成双者，偏旁长短者，不成。凡写字得脚匀齐者，皆就字。四齐者，尤吉字。上短下长者，日久方成字。乾上有破，父不从。坤宫破，母不从。左边长者，男家顺，女家不肯。右边长者，女家顺，男家未然。");
        putXz("分类断法", "官事", "或见文字或字脚，一「丿」一「乀」破碎，断有杖责。或见「牛」字，有牢狱之忧，主人大失。或「木」笔开口者，亦有杖责。字画散乱者，易了。或有「丿」「乀」长者耸者，亦有杖刑。或见杖竹之类，亦有打兆。火命人写「水」字来问，必有官灾。或字有草头者，说草头姓，得力之类。");
        putXz("分类断法", "疾病", "金笔多，心肺痰，脏腑疾。西方金神为衬。木笔多，心气疾，手足灾。木神林坛为祟。水笔多，泻痢吐呕之症。水鬼为凶。火笔潮热，伤寒时行，火鬼为怪。又云四肢疼，时气疾病。火笔多者，病不死。土笔多，脾胃兼疮疾，客亡，伏尸鬼，疼痛之疾。土笔多者，病死字，凡有丧字、虎字头，或两口字者，皆难救。");
        putXz("分类断法", "六甲", "字凡有「喜」字、「吉」字体者，皆吉。字凡带白虎，必难产，子必死。写得粘者，易产。字画纤断者，主有惊险。字有腾蛇笔者，主虚惊。字画直落成双者，阴喜；成单者，男喜。");
        putXz("分类断法", "求谋", "凡字写得中间阔者，所谋无成。谋字写得相粘者，二十四五前成，盖有隔字体故也。求字来问者，木命人吉，土人不利。");
        putXz("分类断法", "行人远信", "如「行」字写得脚短、一般齐者，人便至。字脚或不齐，行人皆不至。字画直落点多者，其人必陷身。字画少者，人便至。乃详字体格范。");
        putXz("分类断法", "官贵", "凡字有二数，一点当先者，无阻事济。所写之字，相粘伶俐者，贵人顺。点多者，事不成。");
        putXz("分类断法", "失物", "凡字有失字体，及字中皆难觅，朱雀动，有口舌，日久难寻。金笔多，艮上有破五金之物，宜速寻。土笔多，坎上有破碎之物，在北方古井，或窑边及坑坎之所，瓦器覆藏，五日见。坤上有一钩者，乃奴婢偷去，不可取得。兑上不足，乃妻妾为脚带，金人将去。离上一画不完者，乃南方火命人将去，见官方失物仍在。");
        putXz("分类断法", "问寿", "字画写得长而瘦者，寿耐久；如肥壮者，耐劳苦；短促者，无寿。");
        putXz("分类断法", "功名", "字有贵人头者，有功名。字金笔多而端正，及木笔轻而长者，皆贵。");
        putXz("分类断法", "行人", "「人」字潦倒，未动。写得「人」字起者，已动。人以「来」字问者，未至。「行」字问者，且待。凡字中有「言」字者，有信至，人未至也。");
        putXz("分类断法", "反体", "「喜」字来问者，未可言喜，有舌字脚也。有以「庆」字来问者，未可言庆，有忧字脚也。「星」字来问者，日在上，星辰不见，问病必凶。大凡文人，不可写武字，武人不可写文字。阴人不可写阳字，阳人不可写阴字。皆反常故也。");
        putXz("分类断法", "时辰断", "看字先须看时辰，时辰克应不相亲。时辰若遇生其用，作事何忧不趁心。（此字中笔一要用也。）");
        putXz("字例", "人", "正人作贵相，睡人作病疾，立人旁托人，双人旁动人，其人逆多顺少。从作两人相从，众作群党生事。坐人作阻隔，更作闲人。");
        putXz("字例", "心", "三点连珠，一钩新月，皆清奇之象。或竖心性情，作小人之状。近身作十字，作穿心六害，取凡百孤独。");
        putXz("字例", "寸", "亦心也。一寸乃十分，为人有十分之望，谋望有分付也，又作一十取之。");
        putXz("字例", "辛", "乃六七日内见，立见于求远；作六十一日，或云有宰相成也。");
        putXz("字例", "言", "有谋有信，取之。");
        putXz("字例", "草作木", "如草之作木，取之心不定也。");
        putXz("赋文", "指迷赋", "尝闻相字乃前贤妙术，古今秘文，为后学之成规，辨吉凶之易见。相人不如相字，相字即相其人。变化如神，精微入圣。自古结绳为政，如今花押成数。言，心声也；字，心画也。心形如笔，笔画一成，分八卦之休囚，定五行之贵贱，决平生之祸福，知目前之吉凶。富贵贫贱，荣枯得失，皆于笔画见之。或将吉为凶，或指凶为吉。先问人之五行，次看人之笔画。相生相旺则吉，相克相泄则凶。如此观之，万无一失。为官则笔满金鱼，致富则笔如宝库。一生孤独，见于字画之欹斜；半世贫穷，乃是笔端之愚浊，非夭即贱。三山削出，皆非显达之人；四大其亡，尽是寂寥之辈。父母俱存兮，乾坤笔肥；母早亡兮，坤笔乃破；父先逝兮，乾笔乃亏。坎是田园并祖宅，稳重加官；艮为男女及兄弟，不宜损折。兑土主妻宫之巧拙，离宫主官禄之荣枯。震为长男，巽为驿马。乾离囚走，壬主竞争。震若勾尖，常招是非。妻定须离，若是圆净，禄官亦要清明。离位昏蒙，乃是剥官之杀。兑宫破碎，宜婚硬命之妻。金命相逢火笔，克陷妻儿。木命亦怕逢金，破财常有。水命不宜土笔，不见男儿。火命若见水笔，定生口舌。土命若见木笔，祖产自消。相生相旺皆吉，相克相刑定凶。举一隅自反，遇五行而相之。略说根源，以示后学。");
        putXz("赋文", "玄黄叙", "龟形未判，此为太古之淳风；鸟迹既分，爰识当时之制字。虽具存于简牍，当深究其源流。成其始者，信不徒然。即其终之，岂无奥义？同田曰富，分贝为贫。两木相并以成林，每水东归是为海。虽纷纷而莫述，即一一而可知。不惟徒羡于简笺，亦可预占乎休咎。春蛇秋蚓，无非归笔下之功。白虎青龙，皆不离毫端之运。今生好癖，博学博文。少年与笔砚相亲，半世与诗书为侣。识鱼鲁之外，穷亥豕之讹，别贤愚之字。昭然于毫端，察祸福之机，了然于心目。鲜而当理，敢学说字之荆公；挟以动人，未逊后来之谢石。得失何劳于龟卜，依违须决于狐疑。岂徒笔下以推尊，亦至梦中而讲究。刀悬梁上，后操刺史之权；松出腹间，果至三公之位。皆前人之已验，非后学之私言。洞察其阴阳，深明乎爻象，则吉凶悔吝可知矣。");
        putXz("赋文", "玄黄歌", "大抵画乃由心出，以诚剖决要分明。出笔发毫逢定位，笔头若出干无成。墨断定知田土散，纸破须防不正人。犬吠一声防哭泣，鼠来又忌贼来侵。赤硃写字血光动，叶上书来有怨盟。忽见鸡鸣知可喜，人惊梦觉事通灵。马嘶必有行人至，猫过须防不正人。船上不宜书火字，楼头亦忌有官刑。有时戏在炉中写，遇火焚烧忽不宁。破器莫教添砚水，定知财散更伶仃。笔下偶然蝇蟢至，分明六甲动阴人。在左定生男子兆，右至当为添女人。曾见人家轻薄辈，口中含饭问灾迍。直饶目下千般喜，也问刑徒法里寻。花下写来为色欲，女人情意喜相亲。花开花落寻灾福，刻应之时勿目盲。麒麟凤凰为吉兆，猪羊牛马是凡形。此际真搜玄妙理，其中然后有分明。应验止须勤记取，灾祥议论觉风生。");
        putXz("赋文", "花押赋", "夫押字者，人之心印也。古人以结绳为证，今人以押字为名。大凡穷通之理，皆与阴阳相应。先观五行之衰旺，次察六神之强胜。五行者，立木、卧土、勾金、点火、曲水之象；六神者，青龙、朱雀、腾蛇、玄武、勾陈、白虎之形。上大阔方，火乃发用；坚瘦有兮，木乃生荣。金要方，水要圆，土要肥而木要正。故曰：炎炎火旺，玉堂拜相。洋洋水秀，金阙朝元。木盛兮，仁全义广；金旺兮，性急心刚。土薄而离巢破祖，土厚而福禄绵绵。故曰：土少木多，根根折挫。金少火多，两窟三窝。金斜而定然子少，木曲而中不财丰。盖画长兮，象天居上。土卧厚兮，象地居下。内木停兮，象人在于中央。三才全兮，如身居其大厦。无天有地兮，父早刑。有天无地兮，母先化。有孤木兮，昆弟难倚。天失兮，故基已罢。内实外虚兮，虽才高无成。外实内虚矣，终富贵而显赫。龙蟠古字，必有将相之权。不正偏斜，定是孤穷之客。腾蛇缠体，飘流万里之程。玄武克身，妨妻害子。身之土透天，常违父母之言，而有失兄弟之理。只将正印按五行，仔细推详大小吉凶，搜六神而无不验矣。");
        putXz("赋文", "探玄赋", "且夫天字者，乃乾健也，君子体之。地字者，乃坤顺也，庶人宜之。君子书天，得其理也。庶人书地，亦合宜也。夏木春花，此乃敷荣之日；冬梅秋菊，正是开发之时。一有背违，宁无困顿？日字要看停午，月来须问上弦。假如风雨，要逢长旺之时。若是雪霜，莫写炎蒸之候。牡丹芍药，只是虚花。野杏山桃，皆为结实。森森松柏，终为梁栋之材。郁郁蓬蒿，不过园篱之物。书来风竹，判以清虚。写到桑蚕，归于饱暖。锣鸣炮响，可言声势之家。波滚船行，俱作飘流之士。鱼龙上达，犬豕下流。泉石烟霞，自是清贫之士。轩窗台榭，难言暗昧之徒。河海江山，所为广大。涧溪沼沚，作事卑微。灯烛书于夜间，自然耀彩。月星写于日午，定是埋光。椒桂芝兰，岂出常人之口。桑麻禾麦，决非上达之人。黄白绿青红，许以相逢艳冶。宫商角徵羽，言他会遇知音。剑戟戈矛，终归武士。琴书笔砚，乃是文人。问贱与贫，因见自谦之德。书富乃贵，已萌妄想之心。金玉珍珠，不过守财之辈。荣华显达，欲寻及第之方。恩情欢爱，既出笔端。淫荡痴迷，常眠花下。酒浆脍炙，哺啜者必常书之。福寿康宁，老大者多应写此。且如龙蟠虎踞，宁无变化之时？凤翥鸾翔，终有飞腾之日。体如鹭立，孤贫之士无疑。势如鸦飞，饶舌之徒可测。惊蛇失道，只寻入穴之谋。舞鹤离巢，自有冲霄之志。急如鹊跳，是子轻浮。缓似鹅行，斯人稳重。如篁翁郁，休言豁达心怀。似水飘流，未免萧条家道。或若炎炎之火，或如点点之云，一生喜怒无常，终身成败不保。风摇嫩竹，早年卓立难成。雨洗桃花，晚岁羁栖无倚。为人潇洒，乃如千树之汪梅。赋性温柔，何异数株之岩柳。烟萝系树，卓立全倚于他人。霜叶辞柯，飘零不由自己。画似棱棱之枯木，孤苦伶仃。形如泛泛之浮萍，贫穷漂泊。无异巉岩之怪石，险营生。有如耸拔之奇峰，孤高处世。金绳铁索，此非岩谷之幽人。玉树瑶琴，定是邦家之良佐。乱丝缠结，定知公事牵连。利刃交加，即是私家格角。撇如罗带，除遇阴人。捺似拖勾，刑伤及己。勾似锦靴，遭逢官贵。画成横枕，疾病临身。切忌横冲半断，不保荣身。仍嫌直落中枯，难言高寿。剔成新月，出门便见光辉。点作星飞，守旧宁无晦滞。至若挥毫带煞，秉生死之重权。落纸无成，作奔趋之贱役。起腾腾之秀气，主有文章。生凛凛之寒光，宁无声价？半浓半淡，作事多乖。倚东倚西，撑持不暇。字短则沈沦不显，字长则潦倒无成。拾后拈前，所为险阻。忘前顿后，举动趦趄。且如偃仰，遇庶人则成号泣，君子必定飞腾。若是拘挛，逢君子乃是刑囚，庶人必能勤苦。造其理也，即此推之。余向遇异人，曾授玄黄诸篇。今遇异翁授此，赋毕问之曰：“愿得公之名姓。”公不答而去。");
        putXz("齐景至理论", "总诀", "天下之妙，无过一理。理既能明，在乎明学。学者穷究，莫难乎性。性既明达，其理昭然。且苍颉始制之时，观迹成象，以之运用，应变随机。且释老梵经，王勃佛记，迨乎今飞轮宝藏之内，既深且密，非高士莫得而闻。何由睹之？其汉高有荣阳之围，以木生火，终不能灭。有人梦腹上生松，丝悬山下，后为幽州刺史。松为十八公，不十为卒。《春秋》说“十四心为德”，《国志》云“口在天为吴”。《晋书》：黄头小人为恭，以人负吉为造。八女之解安禄山，两角女子绿衣裳。端坐太行邀君主，一正之月能灭亡。正月也。郭璞云：永昌有昌之象。其后昌隆。罗，四维也。其偶如此。且人禀阴阳造化，凭五行妙思，一言一语，一动一静，然后挥毫落楮，点画勾拔，岂不从于善恶？得之于心，悬之于手，心正则笔正，心乱则笔乱。笔正则万物咸安，笔乱则千灾竞起。由是考之，其来有自。达者，以理晓；昧者，以字拘。难莫难于立意，贵于言辞。立意须在一门，言词务在心中。余幼亲师友，温故知新，志在取进场屋，为祖宗之光。遂乃屈身假道，每以诗酒自娱。渡江乘兴，偶信卜于岩谷。观溪山之清流，闻禽鸟之好音，殆非人世。忽见一人道貌古怪，披头跣足，踞坐磻石之上。余由是坐之于侧，良久交谈之际，询余曰：“子非齐景乎？”予惊讶，预知姓名，疑其必异人也，遂答之曰：“然。”异人曰：“混沌判肇，苍颉制字者，余也。自传书契于天下，天下大定。后登天为东华帝君。今居于此，乃东华洞天。余曾有奇篇，昔付谢石，今当付汝。今子之来，可熟记速去。不然，尘世更矣。”于是拜而受之，退而观其奥妙，乃《玄黄妙诀神机》。解字之文，得其方妙，如谷之应声，善恶悉见，祸福显然，定死生于先知，决狐疑于预见，后之学者幸珍重之。");
        putXz("字画经验", "总诀", "敷字：昔在任宰，请折之，云此字十日内放笔。果以十日罢任。家字：凡人书此，家宅不宁。空字头，豕应在亥月者也。荆字：艹而刑，不利小人，大宜君子。砚字：有一字天，出之乱尔，见名之兆。典字：曲折多，四七日有兴进之兆。贵人必加官进禄，雅宜便，四十日有进纳之喜。果字：凡事善果披剃，盖口中无才，又云进小口。马字：昔有马雅官，写马字无点，马无足不可动。来字：来带两人之木，皆来见信，行人来应，三人同来，财午未年发。葵字：逢春发生，又占名利，逢癸可发，占病不宜，廿日有惊恐之兆。但字：如日初生，常人主孤，凡事未如意，十日身坦然。谦字：故人嫌，盖无廉耻，目下有事，多是非。亨字：高未高，了未了，须防小人，不足及外孝，不祥。达字：二十日未达，即日并不顺，少喜多忧。奇字：占婚奇偶未谐，应十日难，为兄弟事不全。俊字：一住一利，交友难为。父兄反覆，文书千连变易。凶。常字：占病堂上人灾，有异姓异母。上有堂字头，下有哭字头。每字：昔曹石遣人相此字，异日必为人母。后果然。城字：逢丁戊日，六神动，忌丁戊日，田土不足，进力成功。池字：凡事拖延有日，逢蛇必利，盖添虫为蛇。春字：宋高宗写此字时，秦桧用事。石云：秦头太重，压日无光。桧闻言召而遣之。一字：土字一字，王也。益字：有吏人书益字，廿八日有血光之厄。至期果然。田字：有人出此，相言：直看是王，横看是王，必主大贵。");
        putXz("字体诗诀", "总诀", "天字及二人，作事必有因。一天能庇盖，初主好安身。地字如多理，从此出他乡。心如蛇口毒，去就尽无妨。人字无凶祸，文书有人来。主人自卓立，凡事保和谐。金字得人力，屋下有多财。小人多不足，凡事要安排。木字人未到，初生六害临。未年财禄好，切莫要休心。水字可求望，中妨有是非。文书中有救，出入总相宜。火字小人相，中人发大财。灾忧须见过，日下有人来。土字日下旺，田财尽见之。穿心多不足，骨肉主分离。东字正好动，凡事早求人。牵连须有事，财禄自交欣。西字宜迁改，为事忌恶人。心情虽洒落，百事懒栖身。南字穿心重，还教骨肉轻。凡事却有幸，田土不安宁。北字本比和，不宜分彼此。欲休尚未休，问病必见死。身字主己事，侧伴更添弓。常藉人举荐，仍欣财禄丰。心字无非大，秋初阴小灾。小人多不足，夏见必灾来。头来须鄙衰，发可却近贵。要过子丑前，凡事皆顺利。病来如何疾，木命最非宜。过了丙丁日，方知定不危。言字如何拆？人来有信音。平生多计较，喜吉事应临。行字问出入，须知未可行。不如姑少待，方免有灾惊。到字若来推，出入尚颠倒。虽然吉未成，却于财上好。得来问日下，宁免带勾陈。凡事未分付，行人信不真。开字无分付，营谋尚未安。欲开开不得，进退两皆难。附字问行人，行人犹在路。为事却无凶，更喜有分付。事字事难了，更又带勾陈。手脚仍多犯，月中方可人。卜方求测事，停笔好推详。上下俱不足，所为宜不祥。望字逢寅日，所谋应可成。主须不正当，却喜有功名。福字来求测，须防不足来。相连祸逼迫，一口又兴灾。禄字无祖产，当知有五成。小人生不足，小口有灾惊。贵字多近贵，六六发田财。出入须无阻，宜防失落灾。用字主财用，有事必经州。谁识阴人事，姓王又姓周。康字未康泰，要防阴小灾。所为多不逮，财禄亦难来。宁字占家宅，家和人口增。财于中主发，目下尚伶仃。吉字来占问，反教吉又凶。因缘犹未就，作事每无终。宜字事且且，须知在目前。官非便了当，家下亦安然。似字众人事，所为应不成。独嫌人力短，从众则堪行。多字宜迁动，死中还得生。事成人侈靡，两日过方明。古字多还吉，难逃刑克灾。虽然似喜吉，口舌却终来。洪宜人共活，火命根基别。事还牵制多，应是离祖业。香字忌暗箭，木上是非来。十八二十八，好看音信回。清字贵人顺，财来蓄积盈。阴人是非事，不净更多年。虚惟头似虎，未免有虚惊。凡事亦可虑，仍妨家不宁。远字事多达，行人有信音。为事既皆遂，喜吉又来临。同字如难测，商量亦未然。两旬事方足，尚恐不周圆。众字人共事，亦多生是非。所为应不敛，小口有灾危。飞字须可喜，反覆亦多非。意有飞腾象，求名事即宜。秀字多不实，无事亦孤刑。五五加一岁，还生事不宁。风字事无宁，逢秋愈不吉。疾多风癣攻，更防辰戌日。天字已成天，亦多吞噬心。事皆蒙庇盖，行主二人临。元字二十日，所为应有成。平生刑克重，兀兀不安宁。秋字秋方吉，小人多是非。须知和气散，目下不为宜。申字是非长，道理亦有破。终然屈不伸，谋事难为祸。甲字利姓黄，求名黄甲宜。只恐田土上，还惹是和非。川字如来问，当知有重灾。仍防三十日，不足事还来。墟字若问事，虎头蛇尾惊。有人为遮盖，田土不安宁。辰字如写成，主有变化象。进退虽两难，功名却可望。青字事未顺，须知不静多。贵人仍不足，日久始安和。三字多迁改，为事亦无主。当知二生三，水由一生二。人如来问测，分字亦安让。凡事多费解，仍防公扰忧。字须有学识，初主似空虚。家下不了事，名因女子中。士为大夫体，末免犯穿心。拮括是非散，番多吉事临。");
        putXz("四季水笔", "总诀", "四季水笔春水昏浊　夏水枯涸　秋水澄清　冬水凝结水为财，忌居乾、兑、坎。、、、勹、点不为杀，必为贵人。");
        putXz("画有阴阳", "总诀", "画有阴阳长中有短为阳中阴，短中有长为阴中阳。粗细轻重，以此为例。阳中有阴则佳，阴中有阳反凶。壬字头画，是阳中有阴。任字头画，是阴中有阳。水笔不流，流则不佳。戴流珠，名映星，小人囚系。取福下至上一三，取祸上至下一三。");
        putXz("笔法筌蹄", "总诀", "凡书字法，有浓淡、肥瘦、长短、阔狭、反覆、顺逆、曲直、高低、小大、软硬、开合、清浊、虚实、凹凸、平正、斜侧、圆满、直牵、明白、轻快、稳重、跳跃、勾挽、破碎、枯槁、尖削、倒乱、鹘突、孤露、交加、肥满、尖瘦、刚建、精神、艳冶、气势、衰弱、小巧、软满、老硬、骨棱、草率、开阖之分，各有一体，难以尽述。学者变化，知机其神。");
        putXz("奴婢", "总诀", "奴　婢恰似霜天一叶飞，画如木檐两头垂。画轻点重君须记，画定前趋后拥儿。");
        putXz("阴人", "总诀", "阴　人阴人下笔意如何，只为多羞胆气虚。起处恰如争嘴样，却来下笔定徐徐。");
        putXz("隔手", "总诀", "隔　手隔手书来仔细详，见他纸墨字光芒。更看体骨苏黄格，淡有精神是贵郎。");
        putXz("笔画长短", "总诀", "笔画长短如“吉”字，上作士字，终作士人。如作土字，乃口在下，问病必死。若身命属木，自身无妨。屋下木土生，不过十日必亡。如“常”字，上作小字，只是主家内小口灾，略不为大害。若上草作小，如此写，乃是灾字头，中是门字，下是吊字，主其人大灾患临头，吊客入门，大凶。然亦须仔细仍观人之气色。象人而言，如土人，气色黑恶，其人必退，若土命者，必死，俱不过十日。");
        putXz("笔画犯煞", "总诀", "笔画犯煞——本版存目（诸式多为图象，诀文漫漶）：凤鳞、断伏、活法、用煞、连图带、曰隔伏、丁欹伏、冲伏、悬针、流金、活金、乙伏曲、曲伏、口死金、活火、死火、腾蛇、死土、蛇土、力隔伏。（共二十式；原刻有目无诀，照目存录，不臆补。）");
        putXz("七言作用歌", "总诀", "七言作用歌用神加直五行真，谋望营为百事成。疾病官非兼口舌，纵逢凶处不成凶。（凡金木水火土真字，皆宜用，乃五行真遂，诸事皆利。）年干所属是科名，禾斗皆为首占星。有此求名皆遂意，如无考试定成空。（凡占功名，必要科名入数，再兼官鬼文书动而旺相，功名可成。如无，科名莫许。）求谋之数禄神临，始断今科考事兴。若遇科名同在数，自然高荐遂生平。（禄神即甲禄在寅是也。）有田有日会神兴，见客逢人不必寻。马星原是弯弓脚，四点原来用亦同。（凡谒贵寻人，俱要会神，行人俱要马星妙。）士头口体喜神俱，嫁娶婚姻百事宜。只怕重重见火土，许多克伐反非奇。（“士”属土怕木，“口”属金怕火，所以见木土反非奇也。）笔清墨秀琢磨深，方正无偏必缙绅。疾走龙蛇心志远，行藏慷慨位三公。又字兼骨格有精神，窗下工夫用得深。笔迹丰肥金见火，诗书队里久陶镕。又金木重重见贵神，笔挥清楚主聪明。耸直一行冲宝盖，富贵荣华日日新。又方圆端正笔无尘，年少登科入翰林。只恐弱木逢金克，缠身病疾不明萌。又木形之字有精神，可云发达耀门庭。火多年少心多燥，水盛为人智必清。又一直居中勇更明，少年黾勉得功名。末笔再逢金土厚，为官享禄更廉明。又笔端势小事无成，粗俗须知业不精。起头落尾如莺嘴，心里奸谋刻薄人。又土形之字活而圆，用神清楚是英贤。笔底到头无间断，一家荣耀有余钱。又字贬无神笔更联，公门吏卒度余年。勉强操觚无实学，欺人长者被人嫌。又战兢惕厉若临渊，静里修持反有年。写笔果然无俗气，终须榜上有名填。又日月当头笔迹强，精神骨格字无伤。国家梁柱何消息，更有奇衷佐圣疆。又衣食身旁黑带浓，最嫌软弱与无神。字中人口如枯暗，莫待长年主恶终。又下笔头高志必雄，落头不是正经人。尖头秃尾人无智，老死衙门不得名。又一字忙忙写未全，有头无尾不须言。作事率然多失错，琢磨早失在当年。又字无骨格少精神，一生多耗病沉沉。问名带草索连就，满腹文章亦落空。又草写香花定主贫，弱软干枯受苦辛。于中若是为官客，几日新鲜一旦倾。");
        putXz("比例歌", "总诀", "比例歌一斗日来占事不差，无心书鬼状元家。功名第二推为政，舜字登科作探花。二辰时执笔若书才，大振声名事必来。正午书言真是许，水旁写半见泮开。三逢三书八士能成，照例推之理便通。申车不乱推联捷，数逢三一始为真。四二人同到独书余，一定其间事必徐。问失执金知是铁，始为一举反三隅。此例之类，不过详其理也。暂录四首，为后学之门。余仿此。");
        putXz("西江月", "总诀", "西江月要见卦爻衰旺，端详其内章图。欲知事物识天机，细把《玄黄篇》记。临占观形察物，叶音即义断之。若逢王者世为奇，君免猜疑直示。");
        putXz("易理玄微", "总诀", "易理玄微昔李淳风占赤黑二马入河，人问二马何先起。有人演得离卦云：“离为火，火赤色，赤马先起。”李曰：“火未然，烟先发，黑马先起。”果然。");
        putXz("断扇占", "总诀", "断扇占昔有一妇，其夫久客不归，因请李淳风先生处求断易数。适值他出，问其子，其子见妇手中携一扇，其扇面忽然落地，因断曰：“骨肉分离，不得相见矣。”妇泣而归，恰路遇李淳风先生。妇诉其故，李断曰：“穿衣见父，脱衣见夫。不妨，尔夫今日必到。”将晚，果然至家。可见各解不同，其断精微若此。");
        putXz("买香占", "总诀", "买香占酉年八月二十五日午时，有杨客买香。康节曰：“此香非沉香。”客曰：“此香真不可及。”康节曰：“火中有水，水泽之木，非沉香也。恐是久阴之木，用汤药煮之。”客怒而去。半月后有宾朋至，云是清尾人家做道场，沉香伪而不香。康节曰：“香是何人带来，但问其故，我已先知之矣。”伯温令人去问，果是杨客。康节曰：“前日到门首，因观之。未问之前先失手，其香坠地，故取年月日时占之，得睽之噬嗑。暌下卦属兑，兑为泽。噬嗑下卦属震，震为木，乃水泽之木，即非沉香。睽卦上互得坎，坎为水；下互得离，离为火。上有水即汤，噬嗑卦上互见坎，坎为水，下互见艮，艮为山，中有水，亦泽之象。此乃水泽久损污湿之木，以汤煮之。此理可晓。从此大小事，不可不较其时也。”");
        putXz("古人相字", "总诀", "古人相字昔谢石以拆字名天下，宋高宗私行遇石，以杖于土上书一“一”字，令相之。石思之曰：“土上加一画成王字，必非庶人。”疑信之间，帝又画一“问”字，令相之。为田土所梗，两旁俱斜侧飘飞。石尤惊曰：“左看是君字，右看是君字，必是主上。”遂下拜。上曰：“毋多言。”石俯伏谢恩，帝因召官之。次日，召见偏殿，书一“春”字命相。石奏曰：“秦头太重，压日无光。”上默然。时秦桧弄权，适忤桧，竟贬之边远。途间遇一女子，云能拆字。石怪曰：“世间复有如我拆字者乎？”遂书“谢”字，令相之。女曰：“不过一术士耳。”石曰：“何故？”女曰：“是寸言中立身尔。”石又书一“皮”字。女曰：“石逢皮即破矣。”盖押石之卒即皮姓也。石大惊服，曰：“吾亦能相字，汝可书字，吾相之。”女曰：“吾在此即字矣。请相。”石曰：“人傍山立，即仙字。汝非仙乎？”女笑而忽失。盖世有妙术，术有妙理，在人心耳。然数定，固莫能逃也。后石竟不返。张乘槎善相字，浙江旧有拱北楼，王参政莅淅，改为“来豐楼”。初揭匾，命槎占之，槎曰：“殀矣！尚何占哉！”是晚，讣音果至。异日叩之故，槎曰：“豐字之形，山者墓所也。二丰者，冢上树也。豆者，祭器也。其兆如此，岂非死乎！”刘尝心有所欲占，延槎而不言其事，但令射之，以验其术。槎曰：“书一字方可占。”适有小学生在旁习字，正写《千字文》，至“德建名立”一句，刘就指“德”字令占之。槎曰：“子欲占行人耳。”刘曰：“然。何时当至？”槎曰：“自今十四日必来。”刘曰：“恐事不了，不肯来。”槎曰：“一心要行。悉如所占。”刘问故，槎曰：“德字双立人，乃行人也，故知占行。旁有十四字头，故云十四日。其下又有一心字形，所以云一心要来也。”裴晋公征吴元济，掘地得一石，有字云：“鸡未肥，酒未熟。”相字者解曰：“鸡未肥，无肉也，为己；酒未熟，无水也，酒去‘氵’为酉。破贼在己酉。”果然。唐僖宗改为广明元年。相字者曰：“昔有一人，自崖下出来，姓黄氏，左足踏日，右足踏月，自此天下被扰也。”是年黄巢在长安作乱，天下不安。宋太宗改元太平兴国。相字者曰：“太平二字乃一人六十寿也。”太宗果享六十而崩。周尚干年终将换桃符，制十数联，皆不惬意。周梅坡扶箕降紫姑仙，得两句云：“门无公事往来少，家有阴功子孙多。”甚喜，大书于门。相字者曰：“每句用上三字，其兆不祥。”上句云“门无公”，是年尚干卒于官，乃父致政亦卒，乃兄卒，俱无子。“门无公”、“家有阴”，兆于先矣。");
        putXz("断富贵贫贱要诀", "总诀", "断富贵贫贱要诀凡字写得健壮，其人必发大财，有田土好产。二画一点者，多贵为官食禄，不然亦近贵。才字中或多了一画、一撇、一捺，亦主横发财禄多，遇异贵，得成名利。或少了一画、一撇、一捺，其人破荡弃祖，自立成败。如“名”、“目”字，写得如法正当，无缺折者，其人有名分。笔多清贵虚名。上笔多，富而贵。字中有画当短而长，其人慷慨，会使钱近贵。字画直长而短，其人鄙吝，一钱不使。字有悬针，或直落尖，皆刑六亲，伤害妻子。横画两头尖者，伤妻。直落两头尖者，伤子。字捩画少者，孤捩。画不沾者亦孤，为僧或九流。如见十字两头尖者，穿心六害，刑妻子兄弟，骨肉皆空。字中点多者，主人淫滥漂荡，贪花好色，居止不定。十字下面脚不失者，晚得子方。如见上一画重者，平头杀，亦难为六亲。轻者初年不足，中、末如意。或点重者，为商旅发财，离乡失井，出外卓立。若水命、金命见点画轻者，或早年有水灾，捩者无安身之地，作事成败，主恶死不善终。直落多者，聪明机巧，为手艺之人，白手求财。画多者，必有心胁脾胃之疾。木多有心气之疾，晚年见之。写口字或四围有口开者，有口舌，旬日见之，或破财不足。发字头见者，末主发财。一字分作三截，上中下三主断之。士头文脚，主有文学。金笔灵，或见于干戈字脚者，必是用武之士。凡妇人写来字画不正者，必是偏室，或带三点，必有动意，如三之类。凡写字之人偶然出了笔头，此事破而无成。或近火边写字，必心下不宁。或写字用破器添砚水，家破人亡。或写字时，犬来左右吠，不吉。或取纸来写破碎者，主有口舌。或写字时猫叫，此人有添丁之喜。或在楼上写来问者，主有重叠之事。或在船上写来问者，主有虚惊。或扇上写来问者，夏吉冬不吉。如本命属金，金笔多者贵，土笔多者富。五行生克亦然。余仿此。");
        putXz("五行相生地支", "总诀", "五行相生地支木生在亥火生于寅金生于巳水土长生居申");
        putXz("天干地支所属五行", "总诀", "天干地支所属五行甲乙寅卯属木丙丁巳午属火戊己辰戌丑未属土庚申辛酉属金壬癸亥子属水");
        putXz("论八卦性情", "总诀", "论八卦性情乾健也坤顺也震起也艮止也坎陷也离丽也兑说也巽入也");
        putXz("八卦取象", "总诀", "八卦取象乾为天坤为地震为雷巽为风坎为水离为火艮为山兑为泽");
        putXz("六十甲子纳音", "总诀", "歌甲子乙丑海中金，丙寅丁卯炉中火。戊辰己巳大林木，庚午辛未路旁土。壬申癸酉剑锋金，甲戌乙亥山头火。丙子丁丑涧下水，戊寅己卯城头土。庚辰辛巳白腊金，壬午癸未杨柳木。甲申乙酉井泉水，丙戌丁亥屋上土。戊子己丑霹雳火，庚寅辛卯松柏木。壬辰癸巳长流水，甲午乙未沙中金。丙申丁酉山下火，戊戌己亥平地木。庚子辛丑壁上土，壬寅癸卯金箔金。甲辰乙巳覆灯火，丙午丁未天河水。戊申己酉大驿土，庚戌辛亥钗钏金。壬子癸丑桑柘木，甲寅乙卯大溪水。丙辰丁巳沙中土，戊午己未天上火。庚申辛酉石榴木，壬戌癸亥大海水。");
        putXz("五行四时旺相休囚表", "总诀", "（本版正文为整页图象、文字阙如；据别本补录全文，见 图象补录·TX_WU_XING_SI_SHI。）");
        putXz("六十四卦次第歌", "总诀", "乾坤屯蒙需讼师，比小畜兮履泰否。同人大有谦豫随，蛊临观兮噬嗑贲。剥复无妄大畜颐，大过坎离三十备。咸恒遁兮及大壮，晋与明夷家人睽。蹇解损益夬姤萃，升困井革鼎震继。艮渐归妹丰旅巽，兑涣节兮中孚至。小过既济兼未济，是为下经三十四。");
        putXz("系辞八卦类象歌", "总诀", "乾为君兮首与马，卦属老阳体至刚。坎虽为耳又为豕，艮为手狗男之详。震卦但为龙与足，三卦皆名曰少阳。阳刚终极资阴济，造化因知不易量。坤为臣兮腹与牛，卦属老阴体至柔。离虽为目又为雉，兑为口羊女之流。巽卦但为鸡与股，少阴三卦皆相眸。阴柔终极资阳济，万象搜罗靡不周。");
        putXz("八反格", "总诀", "问喜何曾喜，问忧未必忧。问乐何曾乐，问愁何曾愁。问死何曾死，（心怀死必活。）问生不曾生。问官官不谐，见财财不成。");
        putXz("四言独步", "总诀", "看字之法，毫不可差。下笔是我，其余是他。子孙父母，官鬼要财。兄弟之类，次序安排。详占一事，先看用神。或强或弱，详断吉凶。用神健旺，事所必宜。用神衰弱，必失其机。字无用神，始推末笔。末笔参差，诸事不立。土头中贝，日月大人。字中有像，便是贵人。贵人在爻，祸事必消。逢险可救，财利必招。左右有人，功名可许。笔法轩昂，上人荐举。求财取债，金忌火多。再逢夏月，本利消磨。五行俱全，人事宜然。用神清楚，妙不可言。相争词讼，字详结尾。两笔分明，胜负立剖。字可平分，讼不成凶。人居圈内，缧绁之中。青龙在数，求谋不误。若无水来，反为无助。玄武自来，水上生财。白虎同至，惹祸招灾。朱雀临头，文书已动。事在公门，不与人共。末勾叠叠，口舌重重。若无救助，毕竟成凶。水冷金寒，亲戚无缘。求谋末遂，作事迁延。五行正旺，财利可求。吉神相助，万事无忧。土内埋金，功名未遂。或者水多，前行可贵。人病在床，木被金伤。六神不动，毕竟无妨。字不出头，蹭蹬乖蹇。五行有救，渐渐可展。字无勾踢，人必平安。凶神乱动，好处成难。末后一笔，一身之原。如无破绽，福寿绵绵。一字联络，骨肉同门。孤悬一点，游子飘蓬。金得炉锤，方成器皿。木无金制，可曰愚农。木从土出，受人培植。水中浮木，波浪成风。落笔小心，作事斟酌。小心太过，为人刻薄。写来粗草，放荡之人。笔端熟溜，书记佣工。字法龙蛇，仕途已往。秀而不俗，文章自广。风流笔法，好逞聪明。写来透古，腹内不空。墨迹滞涩，学问难夸。一笔无停，定是大家。灯前窗下，岁月蹉跎。禾麻菽麦，俱已发科。字无倚靠，不利六亲。字无筋节，事可让人。直仰两足，奔波劳碌。摆尾摇头，必满意足。字问日期，切勿妄许。有丁有日，方可说与。山田草木，咸不宜冬。星辰日月，乃怕朦胧。真正五行，不怕相克。直如用神，求谋易得。笔法未全，作事多难。行人不至，音信杳然。水火多源，木枯无枝。子孙宗派，于此可思。终身事业，我即用神。生我者吉，克我者凶。字只两笔，寿年不一。有撇七三，无撇六一。字如三笔，亦各有数。常为十六，变为念五。无勾为变，有勾为常。依斯立法，仔细推详。字不出头，寿增五岁。当头一点，须减三年。字若无勾，添九可求。字如无直，寿当增十。笔画过半，须知减点。一点三年，岁数可免。耳畔成三，口头除四。明彻斯传，始精相字。妙诀无多，功非一日。仔细详占，万无一失。");
        putXz("五言作用歌", "总诀", "断事不可泥，变通方是道。细细察根源，始识先贤奥。十人写一字，笔法各不同。一字占十事，情理自然别。六神无变乱，五行有假真。草木看时节，日月察晦明。字中有子孙，子孙必不少。详其盛与衰，便知贤不肖。我克不宜多，多必妻重娶。克我一般多，谐老又可许。青龙值用神，万事皆无阻。若是无水泽，犹为受用苦。白虎值用神，吉事反成凶。官事必受害，疾病重沉沉。用神见朱雀，利于公门中。君子功名吉，小人口舌凶。用神见腾蛇，俱是文书动。功名眼下宜，富贵如春梦。末笔是青龙，万事不成凶。名利皆如意，行人在路中。末笔是朱雀，公事有着落。只恐闺门中，有病无良药。末笔是勾陈，淹留费苦心。行人音信杳，官讼混如尘。末笔是腾蛇，远客即来家。忧疑终不免，官讼苦嗟吁。末笔是白虎，疾病须忧苦。狱讼必牵缠，出往多拦阻。末笔是玄武，盗贼须提防。水土行人利，家中六畜康。末笔看五行，所用看六神。先定吉凶主，然后字中寻。");
        putXz("别理篇", "总诀", "字义浑论，辨别之篇须下学。理研变化，至诚之道可前知。字同事不同，不宜此而宜彼。事同字亦同，倏变吉而变凶。设若中也者，天下之大本。问终身与昆仲，无缘信乎哉。人间之最要，欲要之于朋友。更切再如地天为泰，不遇阳间犹是否。雷火为丰如逢阴极可云临。既虚矣，复反而为盈；既危矣，复还而为安。时盛必衰，天地不逾其数；治极而乱，圣人能预为防。先则看其笔端，然后察其字义。须知字义古怪，学问宜深。笔走龙蛇，峥嵘已过。龙身草草，非正途显达之官。豹字昂昂，是执殳荷戈之职。志无心，定是漂蓬下士。斌不乱，始称文武全才。贝边月下定归期，足畔口头人必促。团团宝盖，多生富贵之家。济济冠裳，定是风云之客。无事生非因北字，有钱不享是亨来。合则婚事难成，力乃功名末妥。以他人问子，男女皆空。书本姓求官，声名远播。书先觅物终须失，写望追人定是亡。马字偏斜，惟恐落人之局。口头阔大，定招闲事之非。青字有人求作主，事可全于月杪。妙字一女欲于归，少亦可出闺门。天字相联，一对良缘先注定。好字相属，百年美眷预生成。丁寸等字，皆才不足之形。占吉之类，皆告不成之象。香开晨昏扬誉远，花占百事一番新。小为本分之人，大是虚名之士。赤子依亲，是每一例可推。大人盖小，因余仿斯可断。贝左一生多享福，空头半世受孤寒。东西南北，欲就其方。左右中前，乃择其地。一人旁立，求名是佐贰之官。一直居中，占身乃正途之士。草木逢春旺，鱼龙得水舒。远字走长人未到，动旁撇短去犹迟。赤子儿曹之类，必利见大人。公祖父师之称，则相逢贵人。孑则立身无寸地，永如立志有衣冠。操为一品之才，饮定大人之食。之非出往必求财，者不呼卢定六畜。奇欲立而不可，用非走而不通。口居中，俨然一颗方印。元落后，前程可定魁名。体用昂昂，功名之客。性情亟亟，荼苦之懦。朔邦还未入朝廊，田里多应在乡党。活泼泼鸢鱼，是飞腾之象；乐滔滔凫雁，为流荡之徒。川上皆圣贤游乐之余，周行是仕宦经由之道。崔巍远人犹在望，平安近事不能成。日小见天长，心粗知胆大。归则归兮归则止，笑如笑兮笑成悲。国字谓何？一口操戈在内。尔来何故？五人合伙同居。火字乃人在水中，一遇羊头为尽美。天字是人居土内，出头一日始逢春。以余字问，必有；以有字问，反无。龙虽在天在田，看笔迹如何布摆。师既容民畜众，察精神始识兴衰。盖载有人，终享皇家福。伞带全备，定是极品官。有撇断为兄弟，无点莫问儿孙。工欲善其事而成艺，何不见其人而亦可？女子并肩生意好，色糸同处病将亡。犯岁君之名，灾殃不小。书童问卜之日，财利可兴。理中变化深长，此乃规矩方圆之至。字理机关悠远，须认精粗为化造之原。");
        putXz("六言剖断歌", "总诀", "事从天地之义，字乃圣贤之心。静里功夫细阅，其中奥理无穷。圆融莫测其变，来去莫阻其通。笔法先详衰旺，得意始定吉凶。干枯软小为衰，清秀坚昂为旺。详其用神何如，吉凶自然的当。寿夭定于笔墨，取其多寡为占。字如十笔以上，一笔管之六年。字如十笔以下，一笔定其九岁。若在五笔之间，一笔管十六年。笔画过之十五，两笔折作一笔。带草一笔相连，问寿只在目前。笔迹清而拘束，必然游庠在学。笔端浊而放荡，功名必无着落。写来笔法圆活，为人处世谦和。笔底停而又写，为人性慢心多。举笔茫无所措，胸中学问不大。若无写罢复描，行事可为斟酌。富贵出于精神，英雄定于骨格。末后一笔丰隆，到老人称有德。占妻先看其妻，占子先看其子。妻子察其旺衰，据理定其生死。父兄官灾讼狱，父兄要值空亡。如若父兄在数，父兄反见灾殃。一切谋望营求，字要察其虚实。有声无物为虚，有物可见是实。书出眼前之物，察其司重司轻。司重断为有用，司轻大事无成。纳采于归等事，更要加意推详。笔画计其单双，字义察其阴阳。假如子字求子，须防日建逢女。子日如画女字，婚姻百事皆许。一字笔画未全，万事不必开言。字中若有余笔，必须用意详占。先用五行工夫，后穷增减字理。影响毫发无差，谬则难寻千里。学者变化细推，断事无不灵应。");
        putXz("格物章", "总诀", "物格而后知致，本末须详。事来必先见诚，始终可断。细而长者，以一尺为百年，计寸分而知寿算。方而圆者，以千金比一两，度轻重以定荣枯。落手银圈，放荡终不改。出囊珠石，峥嵘半世尚丰盈。石土不逢时，谓之无用。木金全失气，枉自徒劳。执墨问功名，研求之苦日见不足。端鼎比身命，近贵之体一世非轻。腰下佩觿，所求皆遂。道旁弃核，百事无成。取草问营谋，逢春须茂盛。将银问财帛，有本恐消磨。素纨无诗，当推结识疏。牙签托人，毕竟不顾我。数珠团圆到底，夫妻儿女皆宜。木鱼振作不常，父母兄弟难合。力下行人来得快，笔占远处有施为。求子息，圆者不宜空。占买卖，长者终须折。衣衫则包藏骨肉，葬祭之事宜然。绦带必系纨扇躯，牵缠之事未免。舟车骡马，用之则行。婢仆鸡鹅，呼之便至。金扇之类，收有复展之期。烹调之物，死无再生之理。瓜果问事，破不重圆。棋子求占，散而又聚。荡尘理乱，无逾金篦牙签。释罪沉冤，俱是何章刀笔。壶是主人之礼，觞则空而满，满而复空。锁为君子之防，匙则去而来，来而复去。文章书籍，非小人用之，筐筥犁耙，岂君子用之。惯执鞭所忻慕焉，富而必可求也。能弹琴，复长啸，尔乐亦在其中乎？娱指悬匏，功名少待。折来垂柳，意兴多狂。竹杖龙头，节义一生无愧怍。木锥莺嘴，钻谋万物有刚强。手不释正业经书，自知道德修诸己。问不离九流艺术，意在干戈省厥躬。指庭前向日之花，倏忽坐间移影。点槛外敲风之竹，晨昏静里闻音。君子执笙簧，陶陶其乐，舌鼓终须不免。女人拈针线，刺刺不休，心牵毕竟难蠲。出匣图书行欲方，眼下可分玉石；执来宝剑心从利，手中立剖疑难。羽扇纶巾，须知人自山中去；奇珍异宝，可断人从海上来。百草可活人，不识者不可妄用；六经能裨世，未精者焉敢施为？指盂中之水，久不耗而则倾。顾冶内之金，须知积而复用。事非容易，一首词两下欣逢。学识源渊，几句话三生有幸。执金学道，借服为聚物之囊。割爱延师，重身如无价之宝。明心受业，既行束上之修。寄束言传，莫废师尊之礼。斯其人也，斯其义也，可以为之，非其重焉，非其道焉，孰轻与尔？");
        putXz("物理论", "总诀", "三才始判，八卦攸分。万物不离于五行，群生皆囿于生气。羲皇为文字之祖，苍颉肇书篆之端。鸟迹成章，不过象形会意。云龙结篆，传来竹简添书。秦汉而返，篆隶迭易。钟王既出，楷草出名。其文则见于今，其义犹法于古。人备万物之一数，物物相通。字泄诸人之寸灵，人人各异。欲穷吉凶之朕兆，先格物以致知。且云天为极大，能望而不能亲，毕竟虚空为体；海是最深，可观而不可测，由来消长有时。移山拔树莫如风，片纸遮窗可避。变谷迁陵惟是水，尺筒无底难充。小弹大盘，日之远近，不辨白衣苍狗，云之变化非常。雨本滋长禾苗，不及时，民皆额蹙。雪能冻压草木，如适中，人喜丰年。月行急疾映于江，莫向水中捞捉。星布循环周八极，谁从天上推移。露可比恩，厌浥行人多畏。霞虽似锦，膏肓隐士方宜。皜皜秋阳，炎火再逢为亢害。涓涓冬月，寒冰重见愈凄凉。顽金不畏洪炉，潦水须当堤岸。雾气空濛推障碍，电光倏忽喻浮生。月下美人来，只恐到头成梦。雪中寻客去，犹防中道而归。白露可以寄思，迅雷闻而必变。履霜为忧虞之渐，当慎始焉。临渊有战惕之心，保厥终矣。蝃莫指，闺门之事不宜。霖雨既零，稼穑之家有望。阳春白雪，只属孤音。流水高山，难逢知己。至于岩岩山石，生民具瞻。滚滚源泉，圣贤所乐。瀑布奔冲难收拾，溪流湍激不平宁。风水所以行舟，水涌风狂舟必破。雨露虽能长物，雨霪露结物遭伤。社稷自有人求，关津诚为客阻。烟雾迷林终有见，江河出峡去无回。桃夭取妇相宜，未利于买僮置畜。杨柳送行可折，尤喜于赴试求名。松柏堪问寿年，拟声名则飘香挺秀。丝罗可结姻好，比人品则倚势扳援。荷方出水，渐见舒张。梅可调羹，未免酸涩。李有道傍之苦，榄余齿末之甘。笔墨驱使，时日不长。盆盂装载，团圆难久。绠短汲深求未得，戈长力弱荷难成。屠刀割肉利为官，若问六亲多刑损。利刃剖瓜休作事，如占六甲即生男。无人棺椁必添丁，有印书函终见折。厘等则骨贮匣中，纵有出时还须入。算盘则子盈目下，任凭拨乱却成行。瓦只虑其难全，杯亦防其有缺。席可卷舒，终归人下。伞能开合，定出人头。钓乃小去大来，樵则任重道远。素珠团聚，可串而成。蜡烛风流，不能久固。针线若还缝即合，锹锄如用必然翻。凿则损而为材，亦当有斗，锯乃断而成器，岂谓无长。又若飞走之声沉，亦关人事之休咎。猢狲被系，还家终是无期。鹦鹉在囚，受用只因长舌。鸽乃随人饮啄，纵之仍入樊笼。马虽无胆驰驱，用之不离缰锁。鲤失江湖难变化，燕来堂屋转疑难。诉理伸冤，逢鸦不白。占身问寿，遇鹤修龄。万物纷纭，理则难尽。诸人愿欲，志各不同。若执一端以断人，是犹胶柱鼓瑟。能反三隅而悟理，方称活法圆机。心同金鉴之悬空，妍媸自别。智若玉川之入海，活泼自如。鬼谷子曰：“人动我静，人言我听。”旨哉斯言！胡可忽诸。");
        putXz("五行六神辨别论", "总诀", "先以五行为主，次向字中详祸福。继将六神作用，方观笔迹察原因。生克不容情，莫以字音称独美。宜忌须着意，休将文义恃能言。勿取吉字言吉，当认吉中多忌煞。漫将凶字言凶，须详凶处有元神。假如青龙与白虎同行，求功名大得其宜。如庶人得之，反不免相争之咎。父母与妻子聚面，问赴选难从其志。若游子占之，又可触思远之忧。勾陈最忌小金连，惟恐事无间断。朱雀若逢傍水克，须防祸有牵缠。水在木中流，替人濯垢。木从水中出，脱体犹难。五行全不犯凶神，问自身德建名立。六神动再加吉将，若求官体贵身荣。旧事重新，朱、腾双发动；倾家复创，金、土两重临。微火镕金，难成器皿。弱金克木，反自损伤。求济于人，要看水火会合。营谋于众，还祈土木齐登。金多子多，非土不得。土厚财厚，无火不生。水冷木孤，弟兄难靠。金寒土薄，祖业凋零。玄武形青龙得水，连登两榜。白虎尾朱雀衔金，位列三公。玄武临渊，时中之化雨。青龙捧日，阙下之云腾。水非白而无源，金不秋而失气。有勾陈，难结案头文。见朱雀，忖量堂上语。田下土深，思还故里。月边水盛，意在归湖。玄武居中，出外不宜行陆路。勾陈定位，居官虽在受皇恩。白虎重重，不敢保今年无事。青龙两两，定不是此日燕居。字中见母母无忧，笔下从兄兄定在。水土形青龙翘首，何忧不得功名。木相金白虎当头，毕竟难逃灾害。重重金火不逢时，百事徒劳。叠叠青黄非见日，几番隆替。贵显招土木，万福皆隆。方体隐龙蛇，千祥并集。朱、勾相合，主唇舌干戈之事。龙、虎同行，风云际会之荣。玄武不遇火，阴中不美。腾蛇无水渡，郊外生悲。纯土自能生官，福从天至。寒金不但无禄，灾自幽来。天贵专权，问功名必登黄甲。文书不动，赴场闱定值空亡。问子须求子在爻，占妻定要妻入数。笔迹孤寒金带水，六亲一个难招。字形丰满土生金，百岁百年易盛。看五行之旺弱切记，卜词讼以官鬼为先。定六将之机微须知，占家宅以本命为主。五行俱有，凡谋皆遂。六神不动，万事咸宁。细玩辞占影响，无差毫发。密搜奥义规绳，不爽纤微。");
        putXz("金声章", "总诀", "混沌未开，一元含于太极。无形之始，乾坤既判，万物成于文章著见之中。故未有其事，而先有象，可预得其体而兆其来。所以苍颉制字，接云霞蝌蚪之文。至圣著书，采随宜义理而用。一字之善，千古流传。半点之疵，万年不泯。君子哉，非挥毫而莫辨；小人焉，一执笔而即知。是以消长盛衰，因极而知变；吉凶祸福，至诚而见神。写来江汉秋阳，皜皜乎不可尚已。意在螽斯诜羽，绳绳兮与其宜焉。惟存好利喜衰，则落笔终须各别。必欲离尘脱俗，而开首自是不同。若烟雾云霞，则聚散去来神变化；风雷日月，其盈虚消息妙裁成。鹦鹉等禽，人皆云其舌巧；虎豹之类，谁不惧其张威？生息蕃盛者，乃稼穑禾苗；与物浮沉者，是江河湖海。渊中鱼跃，水向东流何沮止？天上鸢飞，日从西落四时同。群兽俱胎之生，独报麟祥之喜；诸禽皆飞腾之物，只言凤德之衰。禽之鸣也噪也，有形小体大之分；兽之利也钝也，有轻清重浊之别。香花灯烛，偏宜于朔望之时；铃铎鼓钟，独可于晨昏之际。点点滴滴，万里征衫游子泪；层层叠叠，九行密线老人心。至于犬豕牛羊，叱之即便去；鸡鱼鹅鸭，欲用则不生。狐貉羔裘，无济于夏；红炉黑炭，偏喜于冬。幽林深圃夜无人，情不诬也；楼阁厅堂时有位，理之必然。琴书剑箱，可断儒生负笈；轻裘肥马，当推志士同袍。墨有渐减之虞，笔恐久坚而弱。书成笔架，几上岷山。写到砚池，寓中闷海。如在其上，秋到一天皆皎月；如在其下，春临遍地产黄金。挥出琵琶，到底是写怨之具；描来箫管，终须为耗气之端。假如云雨雾皆能蔽日之光，天正阴时犹是吉。又若精气神本是扶身之主，人来问病反为凶。水急流清，意偕游鱼；烟飞篆渺，心从云树茫茫。农家落笔，草盛田禾实不足；商者书笺，丝多交易乱如麻。紫绶金章，无者不必写出。蜗名蝇利，有者即便书成。锁钥金汤，必任国家之重寄；羽毛干戚，是祈海甸以清宁。挂锦扬幡，风顺之方必利；舒衾洒帐，雨到之候成欢。礼乐射御书数，如求一艺可执。孝友睦姻任恤，定其六事皆宜。草木逢雨，时生而旺，要详春秋气候。轿马行际，日近而远，亦揆寒暑光阴。试看画饼望梅，何止饥渴？镜花水月，竟是空虚。欲造字相之微，请明章中之理。");
        putXz("浑天甲子定局", "总诀", "（本版正文为整页图象、文字阙如；据别本补录全文，见 图象补录·TX_HUN_TIAN_JIA_ZI。正文『以上四宫属阳，皆从顺数／以上四宫属阴，皆从逆数』『右诀从下念上，一如点画卦爻法』。）");
        putXz("后天时方", "总诀", "（本版正文阙如，仅存校勘记『①「败」，堂本作「散」②「生」，校本作「位」』；据别本补录全文，见 图象补录·TX_HOU_TIAN_SHI_FANG。）");
        putXz("干支辨", "甲乙", "直长为甲亦为寅，细短均为乙卯身。孤直心钩兼湿木，干支无位不须论。假如车字中央一直，彻上彻下，强健无损，则属阳，所以为甲木、寅木。余皆仿此。如幸字上一直下一直，皆短弱属阴，所以作乙木、卯木论也。凡一直，细弱不健，即长如车之直，亦作乙卯木看。其心钩舟船木，并三横两短木，一概不在干支论，因其不正故也。");
        putXz("干支辨", "丙丁", "撇长丙巳短为丁，午火同居短撇中。八字腾蛇兼四点，天干不合地支冲。假如庐字撇长，则取为丙火、巳火用。丙巳属阳，故用撇长者当之。余仿此。");
        putXz("干支辨", "戊己", "横中有直戊居中，画短横轻作己身。末点勾陈皆丑未，长而粗者戊辰同。");
        putXz("干支辨", "庚辛", "口字为金亦作申，挑从酉用捺从辛。空头顽钝囊金妙，不在干支数内寻。");
        putXz("干支辨", "壬癸", "点在当头作癸称，腹中为子要分明。点足为壬腰在亥，余皆野水不同群。假如「文」字一点，即为癸水。癸水乃雨露之源，因在上故也。余仿此。");
    }

    // ------------------------------------------------------------------
    // 图象补录（卷之一／三／四／五 整页图象；底本无文字层）
    //   底本诸篇为整页图象（div.kindle-cn-image）、无文字层，前作「图象存目」。
    //   遵用户明令「图像页不用 OCR，可联网找到相关内容」，据别本回补其文字，
    //   出处逐条标注；此非底本照录。接入节点 16。
    // ------------------------------------------------------------------
    /** 键＝图号，值＝[篇目, 图象说明, 补录文, 出处]。 */
    private static final Map<String, String[]> TU_XIANG_BU_LU = new LinkedHashMap<>();
    private static void putTx(String id, String name, String desc, String text, String src) {
        TU_XIANG_BU_LU.put(id, new String[]{name, desc, text, src});
    }
    static {
        putTx("TX_ZHOUYI_GUASHU", "周易卦数（卷之一·象数易理；Image00003）", "图象·先天八卦次序图",
            "乾，一；兑，二；离，三；震，四；巽，五；坎，六；艮，七；坤，八。",
            "维基文库《梅花易數》卷一·象数易理篇之一");
        putTx("TX_BAGUA_XIANGLI", "八卦象例（卷之一·象数易理；Image00004）", "图象·八卦卦画之象",
            "乾三连，坤六断，震仰盂，艮覆碗，离中虚，坎中满，兑上缺，巽下断。",
            "维基文库《梅花易數》卷一·象数易理篇之一");
        putTx("TX_BAGUA_FANGWEI_1", "八卦方位图（卷之一·象数易理篇之二；Image00007）", "图象·后天八卦方位图",
            "离南、坎北、震东、兑西、巽东南、坤西南、艮东北、乾西北，人则介乎其中。",
            "维基文库《梅花易數》卷一（图）、道教之音《梅花易数·八卦方位图》");
        putTx("TX_BAGUA_FANGWEI_3", "八卦方位图（卷之三·断占总诀篇之一；Image00008）", "图象·八卦方位图",
            "离南、坎北、震东、兑西、巽东南、坤西南、艮东北、乾西北，人则介乎其中。",
            "维基文库《梅花易數》卷三；道教之音《梅花易数·八卦方位图》");
        putTx("TX_BIAN_GUA_SHI", "变卦式八则·卦画（卷之三·断占总诀篇之一；Image00024–31）", "图象·八则各配本卦／互卦／变卦之卦画（六爻阴阳）",
            "泽火革变泽山咸、地雷复变地泽临、天泽履变乾卦、泽火革变火雷噬嗑、雷泽归妹变火泽睽、泽天夬变兑卦、泽火革变艮卦、天水讼变兑卦。（断辞已见卷之三照录，此处补其卦画之目。）",
            "维基文库《梅花易數》卷三·变卦式八则");
        putTx("TX_QI_LIU_SHEN", "起六神卦诀·附例（卷之四·字画指迷；Image00076）", "图象·六神配六爻附例表",
            "附例（今以甲乙、丙丁日附载为式，余仿此）：六爻、五爻、四爻、三爻、二爻、初爻——甲乙日例：玄武、白虎、螣蛇、勾陈、朱雀、青龙；丙丁日例：青龙、玄武、白虎、螣蛇、勾陈、朱雀。",
            "易学网《梅花易數》卷四·占字总诀一；汉同文《梅花观梅拆字数全集》卷之四");
        putTx("TX_WU_XING_SI_SHI", "五行四时旺相休囚表（卷之五·拆字杂编；Image00108）", "图象·五行四时旺相休囚例（金木水火土×春夏秋冬四季月）",
            "春：金囚、木旺、水休、火相、土死；夏：金死、木休、水囚、火旺、土相；秋：金旺、木死、水相、火囚、土休；冬：金休、木相、水旺、火死、土囚；四季月：金相、木囚、水死、火休、土旺。（当令者旺，我生者相，生我者休，克我者囚，被我克者死。）",
            "易学网《梅花易數》卷五·占字总诀二；《欽定古今圖書集成·藝術典》卷七四八");
        putTx("TX_HUN_TIAN_JIA_ZI", "浑天甲子定局·纳甲（卷之五·拆字杂编；Image00109–110）", "图象·浑天甲子（纳甲）表",
            "乾：壬戌土、壬申金、壬午火（上卦），甲辰土、甲寅木、甲子水（下卦）；坎：戊子水、戊戌土、戊申金（上卦），戊午火、戊辰土、戊寅木（下卦）；艮：丙寅木、丙子水、丙戌土（上卦），丙申金、丙午火、丙辰土（下卦）；震：庚戌土、庚申金、庚午火（上卦），庚辰土、庚寅木、庚子水（下卦）。以上四宫属阳，皆从顺数。巽：辛卯木、辛巳火、辛未土（上卦），辛酉金、辛亥水、辛丑土（下卦）；离：己巳火、己未土、己酉金（上卦），己亥水、己丑土、己卯木（下卦）；坤：癸酉金、癸亥水、癸丑土（上卦），乙卯木、乙巳火、乙未土（下卦）；兑：丁未土、丁酉金、丁亥水（上卦），丁丑土、丁卯木、丁巳火（下卦）。以上四宫属阴，皆从逆数。",
            "《欽定古今圖書集成·藝術典》卷七四八；易学网《梅花易數》卷五");
        putTx("TX_HOU_TIAN_SHI_FANG", "后天时方（卷之五·拆字杂编；Image00111–114）", "图象·六十甲子时方吉凶表（凡四页）",
            "子阳，辰丑阳，戊己下皆吉。子日子罡起，灭迹四位中。五败七破位，十祸日习同。（首行甲子：子罡、丑墓、寅吉、卯灭、辰败、巳吉、午破、未绝、申吉、酉祸、戌孤苦、亥空亡。全表六十甲子×十二支，异本互有出入，底本校勘记已注“败”一作“散”、“生”一作“位”；照实存目，详原图。）",
            "《欽定古今圖書集成·藝術典》卷七四八；易学网《梅花易數》卷五");
    }

    /** 图象补录（据别本·非底本照录；接入节点 16）。 */
    private static String tuXiangBuLuNote() {
        StringBuilder sb = new StringBuilder("图象补录（底本诸篇为整页图象、无文字层；据别本回补，出处逐条标注，非底本照录）：");
        for (Map.Entry<String, String[]> e : tuXiangBuLu().entrySet()) {
            String[] v = e.getValue();
            sb.append("\n").append(v[0]).append("【").append(v[1]).append("】")
              .append(v[2]).append("（据：").append(v[3]).append("）");
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------
    // 附录·渔樵对问 / 宋史·邵雍传（附录文献；第 26 轮补录）
    //   依《本体顶层设计》原著范围（附录三篇＝一撮金·渔樵对问·宋史·邵雍传）；
    //   一撮金爻诀见 YICUOJIN_YAO，此二篇为 DB appendix_text 内置同源兜底（OBDA 优先）。
    // ------------------------------------------------------------------
    /** 键＝段名，值＝[篇名, 段主题, 原文]。 */
    private static final Map<String, String[]> APPENDIX_TEXT = new LinkedHashMap<>();
    private static void putAt(String name, String work, String topic, String text) {
        APPENDIX_TEXT.put(name, new String[]{work, topic, text});
    }
    static {
        putAt("ATXT_YQ_01", "渔樵对问", "渔者", "渔者垂钓于伊水之上。樵者过之，弛担息肩，坐于磐石之上，而问于渔者。");
        putAt("ATXT_YQ_02", "渔樵对问", "答", "曰：“鱼可钩取乎？”");
        putAt("ATXT_YQ_03", "渔樵对问", "答", "曰：“然。”");
        putAt("ATXT_YQ_04", "渔樵对问", "答", "曰：“钩非饵可乎？”");
        putAt("ATXT_YQ_05", "渔樵对问", "答", "曰：“否。”");
        putAt("ATXT_YQ_06", "渔樵对问", "答", "曰：“非钩也，饵也。鱼利食而见害，人利鱼而蒙利，其利同也，其害异也。敢问何故？”");
        putAt("ATXT_YQ_07", "渔樵对问", "渔者", "渔者曰：“子樵者也，与吾异治，安得侵吾事乎？然亦可以为子试言之。彼之利，犹此之利也；彼之害，亦犹此之害也。子知其小，未知其大。鱼之利食，吾亦利乎食也；鱼之害食，吾亦害乎食也。子知鱼终日得食为利，又安知鱼终日不得食为害？如是，则食之害也重，而钩之害也轻。子知吾终日得鱼为利，又安知吾终日不得鱼不为害也？如是，则吾之害也重，鱼之害也轻。以鱼之一身，当人之食，是鱼之害多矣；以人之一身，当鱼之一食，则人之害亦多矣。又安知钓乎大江大海，则无易地之患焉？鱼利乎水，人利乎陆，水与陆异，其利一也；鱼害乎饵，人害乎财，饵与财异，其害一也。又何必分乎彼此哉！子之言，体也，独不知用尔。”");
        putAt("ATXT_YQ_08", "渔樵对问", "樵者", "樵者又问曰：“鱼可生食乎？”");
        putAt("ATXT_YQ_09", "渔樵对问", "答", "曰：“烹之可也。”");
        putAt("ATXT_YQ_10", "渔樵对问", "答", "曰：“必吾薪济子之鱼乎？”");
        putAt("ATXT_YQ_11", "渔樵对问", "答", "曰：“然。”");
        putAt("ATXT_YQ_12", "渔樵对问", "答", "曰：“吾知有用乎子矣。”");
        putAt("ATXT_YQ_13", "渔樵对问", "答", "曰：“然则子知子之薪，能济吾之鱼，不知子之薪所以能济吾之鱼也。薪之能济鱼久矣，不待子而后知。苟世未知火之能用薪，则子之薪虽积丘山，独且奈何哉？”");
        putAt("ATXT_YQ_14", "渔樵对问", "樵者", "樵者曰：“愿闻其方。”");
        putAt("ATXT_YQ_15", "渔樵对问", "答", "曰：“火生于动，水生于静。动静之相生，水火之相息。水火，用也；草木，体也。用生于利，体生于害。利害见乎情，体用隐乎性。一性一情，圣人能成。子之薪犹吾之鱼，微火则皆为腐臭败坏，而无所用矣，又安能养人七尺之躯哉？”");
        putAt("ATXT_YQ_16", "渔樵对问", "樵者", "樵者曰：“火之功大于薪，固已知之矣。敢问善灼物，何必待薪而后传？”");
        putAt("ATXT_YQ_17", "渔樵对问", "答", "曰：“薪，火之体也。火，薪之用也。火无体，待薪然后为体；薪无用，待火然后为用。是故凡有体之物，皆可焚之矣。”");
        putAt("ATXT_YQ_18", "渔樵对问", "答", "曰：“水有体乎？”");
        putAt("ATXT_YQ_19", "渔樵对问", "答", "曰：“然。”");
        putAt("ATXT_YQ_20", "渔樵对问", "答", "曰：“火能焚水乎？”");
        putAt("ATXT_YQ_21", "渔樵对问", "答", "曰：“火之性，能迎而不能随，故灭。水之体，能随而不能迎，故热。是故有温泉而无寒火，相息之谓也。”");
        putAt("ATXT_YQ_22", "渔樵对问", "答", "曰：“火之道生于用，亦有体乎？”");
        putAt("ATXT_YQ_23", "渔樵对问", "答", "曰：“火以用为本，以体为末，故动。水以体为本，以用为末，故静。是火亦有体，水亦有用也，故能相济又能相息。非独水火则然，天下之事皆然，在乎用之何如尔。”");
        putAt("ATXT_YQ_24", "渔樵对问", "樵者", "樵者曰：“用可得闻乎？”");
        putAt("ATXT_YQ_25", "渔樵对问", "答", "曰：“可以意得者，物之性也。可以言传者，物之情也。可以象求者，物之形也。可以数取者，物之体也。用也者，妙万物为言者也，可以意得，而不可以言传。”");
        putAt("ATXT_YQ_26", "渔樵对问", "答", "曰：“不可以言传，则子恶得而知之乎？”");
        putAt("ATXT_YQ_27", "渔樵对问", "答", "曰：“吾所以得而知之者，固不能言传，非独吾不能传之以言，圣人亦不能传之以言也。”");
        putAt("ATXT_YQ_28", "渔樵对问", "答", "曰：“圣人既不能传之以言，则六经非言也耶？”");
        putAt("ATXT_YQ_29", "渔樵对问", "答", "曰：“时然后言，何言之有？”");
        putAt("ATXT_YQ_30", "渔樵对问", "樵者", "樵者赞曰：“天地之道备于人，万物之道备于身，众妙之道备于神，天下之能事毕矣，又何思何虑！吾而今而后，知事心践形之为大。不及子之门，则几至于殆矣。”");
        putAt("ATXT_YQ_31", "渔樵对问", "叙", "乃析薪烹鱼而食之，饫而论《易》。");
        putAt("ATXT_YQ_32", "渔樵对问", "渔者", "渔者与樵者游于伊水之上。渔者叹曰：“熙熙乎万物之多，而未始有杂。吾知游乎天地之间，万物皆可以无心而致之矣。非子则吾孰与归焉？”");
        putAt("ATXT_YQ_33", "渔樵对问", "樵者", "樵者曰：“敢问无心致天地万物之方？”");
        putAt("ATXT_YQ_34", "渔樵对问", "渔者", "渔者曰：“无心者，无意之谓也。无意之意，不我物也。不我物，然后定能物物。”");
        putAt("ATXT_YQ_35", "渔樵对问", "答", "曰：“何谓我，何谓物？”");
        putAt("ATXT_YQ_36", "渔樵对问", "答", "曰：“以我徇物，则我亦物也；以物徇我，则物亦我也。我物皆致，意由是明。天地亦万物也，何天地之有焉？万物亦天地也，何万物之有焉？万物亦我也，何万物之有焉？我亦万物也，何我之有焉？何物不我？何我不物？如是则可以宰天地，可以司鬼神，而况于人乎？况于物乎？”");
        putAt("ATXT_YQ_37", "渔樵对问", "樵者", "樵者问渔者曰：“天何依？”");
        putAt("ATXT_YQ_38", "渔樵对问", "答", "曰：“依乎地。”");
        putAt("ATXT_YQ_39", "渔樵对问", "答", "曰：“地何附？”");
        putAt("ATXT_YQ_40", "渔樵对问", "答", "曰：“附乎天。”");
        putAt("ATXT_YQ_41", "渔樵对问", "答", "曰：“然则天地何依何附？”");
        putAt("ATXT_YQ_42", "渔樵对问", "答", "曰：“自相依附。天依形，地附气。其形也有涯，其气也无涯。有无之相生，形气之相息。终则有始，终始之间，其天地之所存乎？天以用为本，以体为末；地以体为本，以用为末。利用出入之谓神，名体有无之谓圣。唯神与圣，能参乎天地者也。小人则日用而不知，故有害生实丧之患也。夫名也者，实之客也；利也者，害之主也。名生于不足，利丧于有余。害生于有余，实丧于不足。此理之常也。养身者必以利，贪夫则以身殉，故有害生焉。立身必以名，众人则以身殉名，故有实丧焉。窃人之财谓之盗，其始取之也，唯恐其不多也。及其败露也，唯恐其多矣。夫贿之与赃，一物而两名者，利与害故也。窃人之美谓之徼，其始取之也，唯恐其不多也。及其败露也，唯恐其多矣。夫誉与毁，一事而两名者，名与实故也。凡言朝者，萃名之地也；市者，聚利之地也。能不以争处乎其间，虽一日九迁，一货十倍，何害生实丧之有耶？是知争也者取利之端也，让也者趋名之本也。利至则害生，名兴则实丧。利至名兴，而无害生实丧之患，唯有德者能之。天依地，地会天，岂相远哉！”");
        putAt("ATXT_YQ_43", "渔樵对问", "渔者", "渔者谓樵者曰：“天下将治，则人必尚行也；天下将乱，则人必尚言也。尚行，则笃实之风行焉；尚言，则诡谲之风行焉。天下将治，则人必尚义也；天下将乱，则人必尚利也。尚义，则谦让之风行焉；尚利，则攘夺之风行焉。三王，尚行者也；五霸，尚言者也。尚行者必入于义也，尚言者必入于利也。义利之相去，一何如是之远耶？是知言之于口，不若行之于身，行之于身，不若尽之于心。言之于口，人得而闻之，行之于身，人得而见之，尽之于心，神得而知之。人之聪明犹不可欺，况神之聪明乎？是知无愧于口，不若无愧于身，无愧于身，不若无愧于心。无口过易，无身过难，无身过易，无心过难。既无心过，何难之有？吁！安得无心过之人，与之语心哉！”");
        putAt("ATXT_YQ_44", "渔樵对问", "渔者", "渔者谓樵者曰：“子知观天地万物之道乎？”");
        putAt("ATXT_YQ_45", "渔樵对问", "樵者", "樵者曰：“未也。愿闻其方。”");
        putAt("ATXT_YQ_46", "渔樵对问", "渔者", "渔者曰：“夫所以谓之观物者，非以目观之也。非观之以目，而观之以心也；非观之以心，而观之以理也。天下之物，莫不有理焉，莫不有性焉，莫不有命焉。所以谓之理者，穷之而后可知也；所以谓之性者，尽之而后可知也；所似谓之命者，至之而后可知也。此三知也，天下之真知也，虽圣人无以过之也。而过之者，非所以谓之圣人也。夫鉴之所以能为明者，谓其能不隐万物之形也。虽然鉴之能不隐万物之形，未若水之能一万物之形也。虽然水之能一万物之形，又未若圣人之能一万物情也。圣人之所以能一万物之情者，谓其圣人之能反观也。所以谓之反观者，不以我观物也。不以我观物者，以物观物之谓也。既能以物观物，又安有我于其间哉？是知我亦人也，人亦我也，我与人皆物也。此所以能用天下之目为己之目，其目无所不观矣。用天下之耳为己之耳，其耳无所不听矣。用天下之口为己之口，其口无所不言矣。用天下之心为己之心，其心无所不谋矣。天下之观，其于见也，不亦广乎？天下之听，其于闻也，不亦远乎？天下之言，其于论也，不亦高乎？天下之谋，其于乐也，不亦大乎？夫其见至广，其闻至远，其论至高，其乐至大，能为至广、至远、至高、至大之事，而中无一为焉，岂不谓至神至圣者乎？非唯吾谓之至神至圣者乎，而天下谓之至神至圣者乎。非唯一时之天下谓之至神至圣者乎，而千万世之天下谓之至神圣者乎。过此以往，未之或知也已。”");
        putAt("ATXT_YQ_47", "渔樵对问", "樵者", "樵者问渔者曰：“子以何道而得鱼？”");
        putAt("ATXT_YQ_48", "渔樵对问", "答", "曰：“吾以六物具而得鱼。”");
        putAt("ATXT_YQ_49", "渔樵对问", "答", "曰：“六物具也，岂由天乎？”");
        putAt("ATXT_YQ_50", "渔樵对问", "答", "曰：“具六物而得鱼者，人也。具六物而所以得鱼者，非人也。”");
        putAt("ATXT_YQ_51", "渔樵对问", "樵者", "樵者未达，请问其方。");
        putAt("ATXT_YQ_52", "渔樵对问", "渔者", "渔者曰：“六物者，竿也，纶也，浮也，沉也，钩也，饵也。一不具，则鱼不可得。然而六物具而不得鱼者，非人也。六物具而不得鱼者有焉，未有六物不具而得鱼者也。是知具六物者，人也。得鱼与不得鱼，天也。六物不具而不得鱼者，非天也，人也。”");
        putAt("ATXT_YQ_53", "渔樵对问", "樵者", "樵者曰：“人有祷鬼神而求福者，福可祷而求耶？求之而可得耶？敢问其所以。”");
        putAt("ATXT_YQ_54", "渔樵对问", "答", "曰：“语善恶者，人也；福祸者，天也。天道福善而祸淫，鬼神岂能违天乎？自作之咎，固难逃已。天降之灾，禳之奚益？修德积善，君子常分。安有余事于其间哉！”");
        putAt("ATXT_YQ_55", "渔樵对问", "樵者", "樵者曰：“有为善而遇祸，有为福而获福者，何也？”");
        putAt("ATXT_YQ_56", "渔樵对问", "渔者", "渔者曰：“有幸与不幸也。幸不幸，命也；当不当，分也。一命一分，人其逃乎？”");
        putAt("ATXT_YQ_57", "渔樵对问", "答", "曰：“何谓分？何谓命？”");
        putAt("ATXT_YQ_58", "渔樵对问", "答", "曰：“小人之遇福，非分也，有命也；当祸，分也，非命也。君子之遇祸，非分也，有命也；当福，分也，非命也。”");
        putAt("ATXT_YQ_59", "渔樵对问", "渔者", "渔者谓樵者曰：“人之所谓亲，莫如父子也；人之所渭疏，莫如路人也。利害在心，则父子过路人远矣。父子之道，天性也。利害犹或夺之，况非天性者乎？夫利害之移人，如是之深也，可不慎乎？路人之相逢则过之，固无相害之心焉，无利害在前故也。有利害在前，则路人与父子，又奚择焉？路人之能相交以义，又何况父子之亲乎？夫义者，让之本也；利者，争之端也。让则有仁，争则有害，仁与害，何相去之远也！尧、舜亦人也。桀、纣亦人也。人与人同而仁与害异尔。仁因义而起，害因利而生。利不以义，则臣弑其君者有焉，子弑其父者有焉。岂若路人之相逢，一目而交袂于中逵者哉！”");
        putAt("ATXT_YQ_60", "渔樵对问", "樵者", "樵者谓渔者曰：“吾尝负薪矣，举百斤而无伤吾之身，加十斤则遂伤吾之身，敢问何故？”");
        putAt("ATXT_YQ_61", "渔樵对问", "渔者", "渔者曰：“樵则吾不知之矣。以吾之事观之，则易地皆然。吾尝钓而得大鱼，与吾交战。欲弃之，则不能舍，欲取之，则未能胜。终日而后获，几有没溺之患矣。非直有身伤之患耶？鱼与薪则异也，其贪而为伤则一也。百斤，力分之内者也；十斤，力分之外者也。力分之外，虽一毫犹且为害，而况十斤乎？吾之贪鱼亦何以异子之贪薪乎？”");
        putAt("ATXT_YQ_62", "渔樵对问", "樵者", "樵者叹曰：“吾而今而后，知量力而动者智矣哉！”");
        putAt("ATXT_YQ_63", "渔樵对问", "樵者", "樵者谓渔者曰：“子可谓知《易》之道矣。吾敢问：《易》有太极，太极何物也？”");
        putAt("ATXT_YQ_64", "渔樵对问", "答", "曰：“无为之本也。”");
        putAt("ATXT_YQ_65", "渔樵对问", "答", "曰：“太极生两仪，两仪，天地之谓乎？”");
        putAt("ATXT_YQ_66", "渔樵对问", "答", "曰：“两仪，天地之祖也，非止为天地而已也。太极分而为二，先得一为一，后得一为二。一二谓两仪。”");
        putAt("ATXT_YQ_67", "渔樵对问", "答", "曰：“两仪生四象，四象何物也？”");
        putAt("ATXT_YQ_68", "渔樵对问", "答", "曰：“大象谓阴阳刚柔。有阴阳然后可以生天，有刚柔然后可以生地。立功之本，于斯为极。”");
        putAt("ATXT_YQ_69", "渔樵对问", "答", "曰：“四象生八卦，八卦何谓也？”");
        putAt("ATXT_YQ_70", "渔樵对问", "答", "曰：“谓乾、坤、离、坎、兑、艮、震、巽之谓也。迭相盛衰终始于其间矣。因而重之，则六十四卦由是而生也，而《易》之道始备矣。”");
        putAt("ATXT_YQ_71", "渔樵对问", "樵者", "樵者问渔者曰：“复何以见天地之心乎？”");
        putAt("ATXT_YQ_72", "渔樵对问", "答", "曰：“先阳已尽，后阳始生，则天地始生之际。中则当日月始周之际，末则当星辰始终之际。万物死生，寒暑代谢，昼夜迁变，非此无以见之。当天地穷极之所必变，变则通，通则久，故《象》言‘先王以至日闭关，商旅不行，后不省方’，顺天故也。”");
        putAt("ATXT_YQ_73", "渔樵对问", "樵者", "樵者谓渔者曰：“无妄，灾也。敢问其故？”");
        putAt("ATXT_YQ_74", "渔樵对问", "答", "曰：“妄则欺也，得之必有祸。斯有妄也，顺天而动，有祸及者，非祸也，灾也。犹农有思丰而不勤稼穑者，其荒也，不亦祸乎？农有勤稼穑而复败诸水旱者，其荒也，不亦灾乎？故《象》言‘先王以茂对时育万物’，贵不妄也。”");
        putAt("ATXT_YQ_75", "渔樵对问", "樵者", "樵者问曰：“姤，何也？”");
        putAt("ATXT_YQ_76", "渔樵对问", "答", "曰：“姤，遇也，柔遇刚也，与夬正反。夫始逼壮，姤始遇壮，阴始遇阳，故称姤焉。观其姤，天地之心亦可见矣。圣人以德化及此，罔有不昌。故《象》言‘施命诰四方’，履霜之慎，其在此也。”");
        putAt("ATXT_YQ_77", "渔樵对问", "渔者", "渔者谓樵者曰：“春为阳始，夏为阳极，秋为阴始，冬为阴极。阳始则温，阳极则热；阴始则凉，阴极则寒。温则生物，热则长物，凉则收物，寒则杀物。皆一气别而为四焉。其生万物也亦然。”");
        putAt("ATXT_YQ_78", "渔樵对问", "樵者", "樵者问渔者曰：“人之所以能灵于万物者，何以知其然耶？”");
        putAt("ATXT_YQ_79", "渔樵对问", "渔者", "渔者对曰：“谓其目能收万物之色，耳能收万物之声，鼻能收万物之气，口能收万物之味。声色气味者，万物之体也。目耳口鼻者，万人之用也。体无定用，惟变是用。用无定体，惟化是体。体用交而人物之道于是乎备矣。然则人亦物也，圣亦人也。有一物之物，有十物之物，有百物之物，有千物之物，有万物之物，有亿物之物，有兆物之物。生一一之物，当兆物之物者，岂非人乎？有一人之人，有十人之人，有百人之人，有千人之人，有万人之人，有亿人之人，有兆人之人。生一一之人，当兆人之人者，岂非圣乎？是知人也者，物之至者也。圣也者，人之至者也。物之至者始得谓之物之物也。人之至者始得谓之人之人也。夫物之物者，至物之谓也。而人之至者，至人之谓也。以一至物而当一至人，则非圣人而何？人谓之不圣，则吾不信也。何哉？谓其能以一心观万心，一身观万身，一物观万物，一世观万世者焉。又谓其能以心代天意，口代天言，手代天工，身代天事者焉。又谓其能以上识天时，下尽地理，中尽物情，通照人事者焉。又谓其能以弥纶天地，出入造化，进退今古，表里人物者焉。噫！圣人者，非世世而效圣焉。吾不得而目见之也。虽然吾不得而目见之，察其心，观其迹，探其体，潜其用，虽亿万千年亦可以理知之也。人或告我曰：‘天地之外，别有天地万物，异乎此天地万物。’则吾不得而知之也。非唯吾不得而知之也，圣人亦不得而知之也。凡言知者，谓其心得而知之也。言言者，谓其口得而言之也。既心尚不得而知之，口又恶得而言之乎？以心不可得知而知之，是谓妄知也。以不可得言而言之，是谓妄言也。吾又安能从妄人而行妄知妄言者乎！”");
        putAt("ATXT_YQ_80", "渔樵对问", "渔者", "渔者谓樵者曰：“仲尼有言曰，殷因于夏礼，所捐益可知也，周因于殷礼，所损益可知也。其或继周者，虽百世可知也。夫如是，则何止于百世而已哉！亿千万世，皆可得而知之也。人皆知仲尼之为仲尼，不知仲尼之所以为仲尼。不欲知仲尼之所以为仲尼则已，如其必欲知仲尼之所以为仲尼，则舍天地将奚之焉？人皆知天地之为天地，不知天地之所以为天地。不欲知天地之所以为天地则已，如其必欲知天地之所以为天地，则舍动静将奚之焉？夫一动一静者，天地至妙者欤？夫一动一静之间者，天地人至妙者欤？是知仲尼之所以能尽三才之道者，谓其行无辙迹也。故有言曰：‘予欲无言。’又曰：‘天何言哉！四时行焉，百物生焉。’其此之谓与？”");
        putAt("ATXT_YQ_81", "渔樵对问", "渔者", "渔者谓樵者曰：“大哉！权之与变乎，非圣人无以尽之。变然后知天地之消长，权然后知天下之轻重。消长，时也；轻重，事也。时有否泰，事有损益。圣人不知随时否泰之道，奚由知变之所为乎？圣人不知随时损益之道，奚由知权之所为乎？运消长者，变也；处轻重者，权也。是知权之与变，圣人之一道耳。”");
        putAt("ATXT_YQ_82", "渔樵对问", "樵者", "樵者问渔者曰：“人谓死而有知，有诸？”");
        putAt("ATXT_YQ_83", "渔樵对问", "答", "曰：“有之。”");
        putAt("ATXT_YQ_84", "渔樵对问", "答", "曰：“何以知其然？”");
        putAt("ATXT_YQ_85", "渔樵对问", "答", "曰：“以人知之。”");
        putAt("ATXT_YQ_86", "渔樵对问", "答", "曰：“何者谓之人？”");
        putAt("ATXT_YQ_87", "渔樵对问", "答", "曰：“目耳鼻口心胆脾肾之气全，谓之人。心之灵曰神，胆之灵曰魄，脾之灵曰魂，肾之灵曰精。心之神发乎目，则谓之视；肾之精发乎耳，则谓之听；脾之魂发乎鼻，则谓之臭；胆之魄发乎口，则谓之言。八者具备，然后谓之人。夫人也者，天地万物之秀气也。然而亦有不中者，各求其类也。若全得人类，则谓之曰全人之人。夫全类者，天地万物之中气也，谓之曰全德之人也。全德之人者，人之人者也。夫人之人者，仁人之谓也。唯全人，然后能当之。人之生也，谓其气行；人之死也，谓其形返。气行则神魂交，形返则精魄存。神魂行于天，精魄返于地。行于天，则谓之曰阳行；返于地，则谓之曰阴返。阳行则昼见而夜伏者也，阴返则夜见而昼伏者也。是故知日者月之形也，月者日之影也。阳者阴之形也，阴者阳之影也。人者鬼之形也，鬼者人之影也。人谓鬼无形而无知者，吾不信也。”");
        putAt("ATXT_YQ_88", "渔樵对问", "樵者", "樵者问渔者曰：“小人可绝乎？”");
        putAt("ATXT_YQ_89", "渔樵对问", "答", "曰：“不可。君子禀阳正气而生，小人禀阴邪气而生。无阴则阳不成，无小人则君子亦不成，唯以盛衰乎其间也。阳六分，则阴四分；阴六分，则阳四分。阳阴相半，则各五分矣。由是知君子小人之时有盛衰也。治世则君子六分。君子六分，则小人四分，小人固不能胜君子矣。乱世则反是。君君，臣臣，父父，子子，兄兄，弟弟，夫夫，妇妇，谓各安其分也。君不君，臣不臣，父不父，子不子，兄不兄，弟不弟，夫不夫，妇不妇，谓各失其分也。此则由世治世乱使之然也。君子常行胜言，小人常言胜行。故世治则笃实之士多，世乱则缘饰之士众。笃实鲜不成事，缘饰鲜不败事。成多国兴，败多国亡。家亦由是而兴亡也。夫兴家与兴国之人，与亡国亡家之人，相去一何远哉！”");
        putAt("ATXT_YQ_90", "渔樵对问", "樵者", "樵者问渔者曰：“人所谓才者，有利焉，有害焉者，何也？”");
        putAt("ATXT_YQ_91", "渔樵对问", "渔者", "渔者曰：“才一也，利害二也。有才之正者，有才之不正者。才之正者，利乎人而及乎身者也；才之不正者，利乎身而害乎人者也。”");
        putAt("ATXT_YQ_92", "渔樵对问", "答", "曰：“不正，则安得谓之才？”");
        putAt("ATXT_YQ_93", "渔樵对问", "答", "曰：“人所不能而能之，安得不谓之才？圣人所以惜乎才之难者，谓其能成天下之事而归之正者寡也。若不能归之以正，才则才矣，难乎语其仁也。譬犹药疗疾也，毒药亦有时而用也，可一而不可再也，疾愈则速已，不已则杀人矣。平药则常日而用之可也，重疾非所以能治也。能驱重疾而无害人之毒者，古今人所谓良药也。《易》曰：‘大君有命，开国承家，小人勿用。’如是，则小人亦有时而用之。时平治定，用之则否。《诗》云：‘它山之石，可以攻玉。’其小人之才乎？”");
        putAt("ATXT_YQ_94", "渔樵对问", "樵者", "樵者谓渔者曰：“国家之兴亡，与夫才之邪正，则固得闻命矣。然则何不择其人而用之？”");
        putAt("ATXT_YQ_95", "渔樵对问", "渔者", "渔者曰：“择臣者，君也；择君者，臣也。贤愚各从其类而为。奈何有尧舜之君，必有尧舜之臣；有桀纣之君，而必有桀纣之臣。尧舜之臣，生乎桀纣之世，桀纣之臣，生于尧舜之世，必非其所用也，虽欲为祸为福，其能行乎？夫上之所好，下必好之。其若影响，岂待驱率而然耶？上好义，则下必好义，而不义者远矣；上好利，下必好利，而不利者远矣。好利者众，则天下日削矣；好义者众，则天下日盛矣。日盛则昌，日削则亡。盛之与削，昌之与亡，岂其远乎？在上之所好耳。夫治世何尝无小人，乱世何尝无君子，不用则善恶何由而行也？”");
        putAt("ATXT_YQ_96", "渔樵对问", "樵者", "樵者曰：“善人常寡，而不善人常众；治世常少，而乱世常多，何以知其然耶？”");
        putAt("ATXT_YQ_97", "渔樵对问", "答", "曰：“观之于物，何物不然？譬诸五谷，耘之而不苗者有矣。蓬莠不耘而犹生，耘之而求其尽也，亦未如之何矣。由是知君子小人之道，有自来矣。君子见善则喜之，见不善则远之；小人见善则疾之，见不善则喜之。善恶各从其类也。君子见善则就之，见不善则违之；小人见善则违之，见不善则就之。君子见义则迁，见利则止；小人见义则止，见利则迁。迁义则利人，迁利则害人。利人与害人，相去一何远耶？家与国一也，其兴也，君子常多而小人常鲜；其亡也，小人常多而君子常鲜。君子多而去之者，小人也；小人多而去之者，君子也。君子好生，小人好杀。好生则世治，好杀则世乱。君子好义，小人好利。治世则好义，乱世则好利，其理一也。”");
        putAt("ATXT_YQ_98", "渔樵对问", "渔者", "钓者谈已，樵者曰：“吾闻古有伏羲，今日如睹其面焉。”拜而谢之，及旦而去。");
        putAt("ATXT_SY_01", "宋史·邵雍传", "籍贯·早年", "邵雍字尧夫。其先范阳人，父古徙衡漳，又徙共城。雍年三十，游河南，葬其亲伊水上，遂为河南人。");
        putAt("ATXT_SY_02", "宋史·邵雍传", "励志游学", "雍少时自雄其才，慷慨欲树功名，于书无所不读。始为学，即坚苦刻厉，寒不炉，暑不扇，夜不就席者数年。已而叹曰：“昔人尚友于古，而吾独未及四方。”于是逾河、汾，涉淮、汉，周流齐、鲁、宋、郑之墟，久之，幡然来归，曰：“道在是矣。”遂不复出。");
        putAt("ATXT_SY_03", "宋史·邵雍传", "受学李之才·先天之旨", "北海李之才摄共城令，闻雍好学，尝造其庐，谓曰：“子亦闻物理性命之学乎？”雍对曰：“幸受教。”乃事之才，受《河图》、《洛书》、宓羲八卦六十四卦图像。之才之传，远有端绪，而雍探赜索隐，妙悟神契，洞彻蕴奥，汪洋浩博，多其所自得者。及其学益老，德益邵，玩心高明，以观夫天地之运化，阴阳之消长，远而古今世变，微而走飞草木之性情，深造曲畅，庶几所谓不惑，而非依仿象类、亿则屡中者。遂衍宓羲先天之旨，著书十余万言行于世，然世之知其道者鲜矣。");
        putAt("ATXT_SY_04", "宋史·邵雍传", "安乐窝·行窝", "初至洛，蓬荜环堵，不芘风雨，躬樵爨以事父母，虽平居屡空，而怡然有所甚乐，人莫能窥也。及执亲丧，哀毁尽礼。富弼、司马光、吕公著诸贤退居洛中，雅敬雍，恒相从游，为市园宅。雍岁时耕稼，仅给衣食。名其居曰“安乐窝”，因自号安乐先生。旦则焚香燕坐，晡时酌酒三四瓯，微醺即止，常不及醉也，兴至辄哦诗自咏。春秋时出游城中，风雨常不出，出则乘小车，一人挽之，惟意所适。士大夫家识其车音，争相迎候，童孺厮隶皆欢相谓曰：“吾家先生至也。”不复称其姓字。或留信宿乃去。好事者别作屋如雍所居，以候其至，名曰“行窝”。");
        putAt("ATXT_SY_05", "宋史·邵雍传", "洛中师友·德化", "司马光兄事雍，而二人纯德尤乡里所慕向，父子昆弟每相饬曰：“毋为不善，恐司马端明、邵先生知。”士之道洛者，有不之公府，必之雍。雍德气粹然，望之知其贤，然不事表襮，不设防畛，群居燕笑终日，不为甚异。与人言，乐道其善而隐其恶。有就问学则答之，未尝强以语人。人无贵贱少长，一接以诚，故贤者悦其德，不贤者服其化。一时洛中人才特盛，而忠厚之风闻天下。");
        putAt("ATXT_SY_06", "宋史·邵雍传", "熙宁新法·宽民", "熙宁行新法，吏牵迫不可为，或投劾去。雍门生故友居州县者，皆贻书访雍，雍曰：“此贤者所当尽力之时，新法固严，能宽一分，则民受一分赐矣。投劾何益耶？”");
        putAt("ATXT_SY_07", "宋史·邵雍传", "应诏辞官·赐谥康节", "嘉佑诏求遗逸，留守王拱辰以雍应诏，授将作监主簿，复举逸士，补颍州团练推官，皆固辞乃受命，竟称疾不之官。熙宁十年，卒，年六十七，赠秘书省著作郎。元佑中赐谥康节。");
        putAt("ATXT_SY_08", "宋史·邵雍传", "内圣外王之学", "雍高明英迈，迥出千古，而坦夷浑厚，不见圭角，是以清而不激，和而不流，人与交久，益尊信之。河南程颢初侍其父识雍，论议终日，退而叹曰：“尧夫，内圣外王之学也。”");
        putAt("ATXT_SY_09", "宋史·邵雍传", "前知·玩世之辨", "雍知虑绝人，遇事能前知。程颐尝曰：“其心虚明，自能知之。”当时学者因雍超诣之识，务高雍所为，至谓雍有玩世之意。又因雍之前知，谓雍于凡物声气之所感触，辄以其动而推其变焉。于是摭世事之已然者，皆以雍言先之，雍盖未必然也。");
        putAt("ATXT_SY_10", "宋史·邵雍传", "临终·著述", "雍疾病，司马光、张载、程颢、程颐晨夕候之。将终，共议丧葬事外庭，雍皆能闻众人所言，召子伯温谓曰：“诸君欲葬我近城地，当从先茔尔。”既葬，颢为铭墓，称雍之道纯一不杂，就其所至，可谓安且成矣。所著书曰《皇极经世》、《观物内外篇》、《渔樵问对》，诗曰《伊川击壤集》。");
    }

    /** 附录·渔樵对问/宋史·邵雍传 义理提要（接入节点 16 汇总；OBDA 优先、内置兜底）。 */
    /** 附录条目：本体/DB（div:AppendixText）优先，未装载回退内置 APPENDIX_TEXT。 */
    private static Map<String, String[]> appendixEntries() {
        Map<String, String[]> o = ontoAppendixText;
        return (o != null && !o.isEmpty()) ? o : APPENDIX_TEXT;
    }

    /**
     * 批次3：把 DB/OBDA 装载的原著全文（{@code appendix_text} 中 name==常量名 的条目）反射回写对应内置常量字段，
     * 实现「本体/DB 优先、Java 内置常量兜底」。{@code ATXT_*} 等无同名字段的条目自动跳过。
     */
    private static void applyAppendixFullText(Map<String, String[]> m) {
        if (m == null || m.isEmpty()) {
            return;
        }
        int n = 0;
        for (Map.Entry<String, String[]> e : m.entrySet()) {
            String[] v = e.getValue();
            if (v == null || v.length < 3 || v[2] == null) {
                continue;
            }
            try {
                java.lang.reflect.Field f = PlumBlossomOntologyJobWorker.class.getDeclaredField(e.getKey());
                if (f.getType() == String.class && java.lang.reflect.Modifier.isStatic(f.getModifiers())) {
                    f.setAccessible(true);
                    f.set(null, v[2]);
                    n++;
                }
            } catch (NoSuchFieldException ignore) {
                // 附录段名（ATXT_*）无同名字段，跳过
            } catch (Exception ignore) {
                // 反射失败不影响内置兜底
            }
        }
        if (n > 0) {
            log.info("✅ 原著全文外置装载 | 覆盖内置全文常量 {} 个", n);
        }
    }

    /**
     * 五卷照录正文以本体/DB 覆盖内置 {@code JUAN_*} 兜底常量（第 52 轮 C-2 下沉）。
     *
     * <p>与 {@link #applyAppendixFullText} 同法：仅覆盖同名静态 String 字段；
     * 缺字段或反射失败则保留内置兜底，保证离线可用。条目以「章节编码＝字段名」一一对应。
     */
    private static void applyJuanTextOverrides(Map<String, String> m) {
        if (m == null || m.isEmpty()) {
            return;
        }
        int n = 0;
        for (Map.Entry<String, String> e : m.entrySet()) {
            String code = e.getKey();
            String text = e.getValue();
            if (code == null || text == null || text.isBlank() || !code.startsWith("JUAN_")) {
                continue;
            }
            try {
                java.lang.reflect.Field f = PlumBlossomOntologyJobWorker.class.getDeclaredField(code);
                if (f.getType() == String.class && java.lang.reflect.Modifier.isStatic(f.getModifiers())) {
                    f.setAccessible(true);
                    f.set(null, text);
                    n++;
                }
            } catch (NoSuchFieldException ignore) {
                // 非 JUAN_* 条目或字段名不符，跳过
            } catch (Exception ignore) {
                // 反射失败不影响内置兜底
            }
        }
        if (n > 0) {
            log.info("✅ 五卷照录正文外置装载 | 覆盖内置 JUAN_* 常量 {} 章", n);
        }
    }

    public static String appendixDoctrineNote() {
        int yq = 0, sy = 0;
        for (String[] v : appendixEntries().values()) {
            if ("渔樵对问".equals(v[0])) { yq++; } else if ("宋史·邵雍传".equals(v[0])) { sy++; }
        }
        int[] bgj = baGuaJueCounts();
        return "附录·一撮金（起数得卦＋爻诀 192 首，B67）／八卦诀（" + bgj[0] + "宫×" + bgj[1] + "卦，京房八宫卦序，B70）"
             + "；附录·渔樵对问（" + yq + "则：体用相息·以物观物·太极两仪四象八卦·权变性命）"
             + "；附录·宋史·邵雍传（" + sy + "段：邵雍生平学术——受学李之才河图洛书、衍先天之旨、安乐窝、内圣外王）"
             + "——依《本体顶层设计》附录（一撮金 B67／八卦诀 B70／渔樵对问·邵雍传第 26 轮补录）。";
    }

    /** 取某附录篇提要（work 含「渔樵」/「邵雍」/「八卦」时命中，否则空）。 */
    public static String appendixTextNote(String work) {
        if (work == null) { return ""; }
        if (work.contains("八卦")) { return "；" + baGuaJueText(); }
        String w = work.contains("渔樵") ? "渔樵对问" : (work.contains("邵雍") ? "宋史·邵雍传" : null);
        if (w == null) { return ""; }
        int n = 0;
        for (String[] v : appendixEntries().values()) {
            if (w.equals(v[0])) { n++; }
        }
        return "；附录·" + w + "（全录 " + n + " 段）";
    }

    /** 附录文献段数（自检用）。 */
    private static int appendixTextCount() { return appendixEntries().size(); }

    // ------------------------------------------------------------------
    // 附录·八卦诀（京房八宫卦序；第 27 轮补录 B70）
    //   原著附录「八卦诀」列八卦各统一宫八卦（本宫卦＋一世～五世＋游魂＋归魂共八卦），
    //   与卷之五「六十四卦次第歌」（文王卦序）互参，为附录取卦之目录性名录。
    //   至此《本体顶层设计》附录四篇（一撮金 / 八卦诀 / 渔樵对问 / 邵雍传）皆有着落。
    // ------------------------------------------------------------------
    /** 键＝宫（八纯卦名），值＝该宫八卦（自本宫卦起，依京房世卦序）。 */
    private static final Map<String, String[]> BA_GUA_JUE = new LinkedHashMap<>();
    static {
        BA_GUA_JUE.put("乾", new String[]{"乾为天", "天泽履", "天火同人", "天雷无妄", "天风姤", "天水讼", "天山遁", "天地否"});
        BA_GUA_JUE.put("兑", new String[]{"兑为泽", "泽天夬", "泽火革", "泽雷随", "泽风大过", "泽水困", "泽山咸", "泽地萃"});
        BA_GUA_JUE.put("离", new String[]{"离为火", "火天大有", "火泽睽", "火雷噬嗑", "火风鼎", "火水未济", "火山旅", "火地晋"});
        BA_GUA_JUE.put("震", new String[]{"震为雷", "雷天大壮", "雷泽归妹", "雷火丰", "雷风恒", "雷水解", "雷山小过", "雷地豫"});
        BA_GUA_JUE.put("巽", new String[]{"巽为风", "风天小畜", "风泽中孚", "风火家人", "风雷益", "风水涣", "风山渐", "风地观"});
        BA_GUA_JUE.put("坎", new String[]{"坎为水", "水天需", "水泽节", "水火既济", "水雷屯", "水风井", "水山蹇", "水地比"});
        BA_GUA_JUE.put("艮", new String[]{"艮为山", "山天大畜", "山泽损", "山火贲", "山雷颐", "山风蛊", "山水蒙", "山地剥"});
        BA_GUA_JUE.put("坤", new String[]{"坤为地", "地天泰", "地泽临", "地火明夷", "地雷复", "地风升", "地水师", "地山谦"});
    }

    /** 附录·八卦诀 全录文本（八宫各统八卦；接入节点 16 汇总「附录文献」）。 */
    public static String baGuaJueText() {
        StringBuilder sb = new StringBuilder("附录·八卦诀（京房八宫卦序，八宫各统八卦）——");
        for (Map.Entry<String, String[]> e : BA_GUA_JUE.entrySet()) {
            sb.append(e.getKey()).append("宫：").append(String.join("、", e.getValue())).append("；");
        }
        sb.setLength(sb.length() - 1);
        sb.append("。");
        return sb.toString();
    }

    /** 某六画卦所属之宫（京房八宫；附录·八卦诀）；不在名录返回空串。 */
    private static String baGuaJuePalace(final String hexagram) {
        if (hexagram == null || hexagram.isBlank()) {
            return "";
        }
        for (Map.Entry<String, String[]> e : BA_GUA_JUE.entrySet()) {
            for (String h : e.getValue()) {
                if (h.equals(hexagram)) {
                    return e.getKey();
                }
            }
        }
        return "";
    }

    /** 附录·八卦诀 宫数/卦数（自检用）。 */
    private static int[] baGuaJueCounts() {
        int n = 0;
        for (String[] v : BA_GUA_JUE.values()) {
            n += v.length;
        }
        return new int[]{BA_GUA_JUE.size(), n};
    }

    /** 相字断法条目（类目 → 条目名 → 诀文）：本体/DB（sym:XiangZiRule）优先，未装载回退内置 XIANGZI_RULE。 */
    private static Map<String, Map<String, String>> xiangZiRuleMap() {
        Map<String, Map<String, String>> o = ontoXiangZi;
        return (o != null && !o.isEmpty()) ? o : XIANGZI_RULE;
    }

    /** 相字断法：类目数、条目总数（自检用）。 */
    private static int[] xiangZiCounts() {
        int n = 0;
        for (Map<String, String> m : xiangZiRuleMap().values()) {
            n += m.size();
        }
        return new int[]{xiangZiRuleMap().size(), n};
    }

    /** 相字·按类目与条目名精确取诀（如 xiangZiRule("六神笔式", "青龙")）。 */
    private static String xiangZiRule(final String category, final String key) {
        Map<String, String> m = xiangZiRuleMap().get(category);
        if (m == null) {
            return "";
        }
        return m.getOrDefault(key, "");
    }

    /**
     * 相字取象（卷之四/卷之五，B68）：依所书之字 / 偏旁 / 写字时所遇兆应，于相字诸诀中取断。
     * 取「分类断法 / 相字短诀 / 神煞 / 玄黄克应歌」四类中条目名（皆为多字、特异之键），
     * 命中即引其诀（照录原著）；无命中则返空（不臆断）。
     */
    private static String xiangZiNote(final String subject) {
        String s = subject == null ? "" : subject;
        if (s.isBlank()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (String cat : new String[]{"分类断法", "相字短诀", "神煞", "玄黄克应歌"}) {
            Map<String, String> m = xiangZiRuleMap().get(cat);
            if (m == null) {
                continue;
            }
            for (Map.Entry<String, String> e : m.entrySet()) {
                if (e.getKey().length() >= 2 && s.contains(e.getKey())) {
                    sb.append("〔相字·").append(cat).append("·").append(e.getKey()).append("〕")
                      .append(e.getValue()).append("；");
                }
            }
        }
        return sb.length() == 0 ? "" : sb.toString();
    }

    /** 相字取象纲要：列所录诸类目及条目数（证明「相字」已全录之貌），供节点 9 展示。 */
    private static String xiangZiDigestText() {
        StringBuilder sb = new StringBuilder("（相字取象：卷之四·字画指迷 / 卷之五·拆字杂编）");
        for (Map.Entry<String, Map<String, String>> e : xiangZiRuleMap().entrySet()) {
            sb.append(e.getKey()).append("(").append(e.getValue().size()).append(")").append("、");
        }
        sb.setLength(sb.length() - 1);
        sb.append("。");
        return sb.toString();
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
     * 卷之一·风觉占/鸟占/听声音占之断法（B63）——既得卦后依所察之文断其吉凶：
     * <ul>
     *   <li>风觉占：审其时、察其色、辨其声势（L525–527）；</li>
     *   <li>鸟占：听其声音（喧啾/悲咽/嘹亮）、察其名义（鸦报灾/鹊报喜/鸾鹤祥瑞/鹗鹏妖孽）（L529）；</li>
     *   <li>听声音占：辨物声所属、察其悲喜以助断吉凶（L531–532）。</li>
     * </ul>
     * 起卦法（取数）已在位（见 {@code encodeStartNumbers}），此处补其断法，照录诀文。
     */
    private static final Map<String, String> CAST_METHOD_VERDICT = new LinkedHashMap<>();

    static {
        CAST_METHOD_VERDICT.put("风觉占", "〔风觉占·断法〕既见风起，须审其时、察其色、辨其声势，然后可断其吉凶——"
                + "审时：春为发生和畅之风，夏为长养之风，秋为肃杀，冬为凛冽；"
                + "察色：带埃烟云气，可见其色黄者祥瑞之气、青者半凶半吉、白主刃、气黑昏者凶、赤色者灾、红紫者吉；"
                + "辨声势：其风声如阵马主斗争、如波涛者有惊险、如悲咽者有忧虞、如奏乐者有喜事、"
                + "如喧呼者主闹哄、如烈焰者有火惊；其声洋洋而来、徐徐而去者，吉庆之兆（卷之一·风觉占）。");
        CAST_METHOD_VERDICT.put("鸟占", "〔鸟占·断法〕既见鸟，须听其声音、察其名义——"
                + "听声音：鸣叫之喧啾者主口舌、鸣叫悲咽者主忧愁、鸣叫嘹亮者吉庆；"
                + "察名义：鸦报灾、鹊报喜、鸾鹤为祥瑞、鹗鹏为妖孽（卷之一·鸟占）。");
        CAST_METHOD_VERDICT.put("听声音占", "〔听声音占·断法〕既闻声，须辨其物声所属、察其悲喜以助断吉凶——"
                + "辨物声：声自口出者属兑、静物扣击属震、金声属乾、火声属离、土声属坤；"
                + "察悲喜：闻人语笑声、又说吉语、娱笑者有喜；人悲泣声与怨声、愁语及骂詈、穷叹等声不吉"
                + "（卷之一·听声音占）。");
    }

    private static String castMethodVerdictText(final String method) {
        return loreMap("CAST_METHOD_VERDICT", CAST_METHOD_VERDICT).getOrDefault(method == null ? "" : method, "");
    }

    // ----------------------------------------------------------------------
    // B67：附录·邵康节易数一撮金·爻诀（64卦×3爻＝192首）。
    //   原著附录「乃以爻诀断之，无不奇中」——一撮金法之断，专用所得动爻之爻诀，
    //   与《周易》卦爻辞别系（见《本体核心设计》§5「两类辞」）。DB（yicuo_jin_yao）
    //   为唯一真源，此处为同源内置兜底（离线可用、供自检核对）。键＝卦名；
    //   值＝长度 7 数组，下标＝爻位(1..6)，元素＝"等级|爻题|断辞"。
    // ----------------------------------------------------------------------
    private static final Map<String, String[]> YICUOJIN_YAO = new LinkedHashMap<>();

    private static void putYicuoJin(String hexagram, int position, String grade, String yaoLabel, String text) {
        YICUOJIN_YAO.computeIfAbsent(hexagram, k -> new String[7])[position] =
                grade + "|" + yaoLabel + "|" + text;
    }

    static {

        putYicuoJin("乾为天", 2, "上上", "九二二爻", "得意宜逢贵，前程去有缘。利名终有望，三五月团圆。");
        putYicuoJin("乾为天", 4, "中中", "九四四爻", "欲行不止，徘徊不已。藏玉怀珠，片帆千里。");
        putYicuoJin("乾为天", 6, "下下", "上九六爻", "心戚戚，口啾啾。一番思量一番忧，说了休时又不休。");
        putYicuoJin("坤为地", 2, "上上", "六二二爻", "师征千里福绵绵，造化生成信自然。功名得就神明助，蛇鬼相逢自变迁。万顷波涛无点化，一天风月更幽闲。大明在在红轮照，名利何须自作难。");
        putYicuoJin("坤为地", 4, "下下", "六四四爻", "路不通，门闭塞。谨慎提防，月藏云黑。");
        putYicuoJin("坤为地", 6, "下中", "上六六爻", "月缺花残，镜破钗分。休来休往，事始安宁。");
        putYicuoJin("水雷屯", 2, "上中", "六二二爻", "事迟志速，而且反复。直待岁寒，花残果熟。");
        putYicuoJin("水雷屯", 4, "上上", "六四四爻", "乘马班如，求婚媾吉。随时谐义，缺月重明。");
        putYicuoJin("水雷屯", 6, "下下", "上六六爻", "不足不足，难伸心曲。野塘雨过月如钩，梦断邯郸眉黛蹙。");
        putYicuoJin("山水蒙", 1, "下中", "初六初爻", "门外起干戈，亲朋两不和。朱衣临日月，始觉笑呵呵。");
        putYicuoJin("山水蒙", 3, "下下", "六三三爻", "娶女无攸利，花间又及秋。严霜将荐至，退步不存留。");
        putYicuoJin("山水蒙", 5, "下下", "六五五爻", "乘病马上，危坡防失，跌见蹉跎。");
        putYicuoJin("水天需", 1, "上中", "初九初爻", "过尽山前后，艰阻往来难。若得清风便，扁舟过远山。");
        putYicuoJin("水天需", 3, "上中", "九三三爻", "君子升，小人阻，征战生离苦。前途自有吉人逢，信在马羊人在楚。事可忧，要营求。");
        putYicuoJin("水天需", 5, "中中", "九五五爻", "久历惊涛，东风便好。太平身退，目下尚早。");
        putYicuoJin("天水讼", 1, "上中", "初六初爻", "嘹呖征鸿独出群，高飞羽翼更斜分。云程正北堪图进，好个名声处处闻。");
        putYicuoJin("天水讼", 3, "上中", "六三三爻", "贵各相逢便可期，庭前枯木凤来仪。好将短事求长事，休听旁人说是非。");
        putYicuoJin("天水讼", 5, "上上", "九五五爻", "檐前鹊噪正翩翩，忧虑洗消喜自然。一人进了一人退，下梢总有好因缘。");
        putYicuoJin("地水师", 2, "上上", "九二二爻", "秋月云开后，薰风雨过时。若逢楚国旧知己，等闲一荐不须疑。");
        putYicuoJin("地水师", 4, "下中", "六四四爻", "青毡终复旧，枝上果生风。莫谓一时喜，还疑此象凶。");
        putYicuoJin("地水师", 6, "上上", "上六六爻", "谋已定，事何忧。金鳞已上钩，功名一网收。");
        putYicuoJin("水地比", 2, "中中", "六二二爻", "一人去，一人来，清风明月两相猜，获得金鳞下钓台。");
        putYicuoJin("水地比", 4, "下中", "六四四爻", "无端风雨吹春去，落尽枝头桃李花。枕畔有人歌且笑，教君心事乱如麻。");
        putYicuoJin("水地比", 6, "下下", "上六六爻", "喜未稳，愁已遭，大雨狂风临古木，人人尽道不坚牢。");
        putYicuoJin("风天小畜", 2, "上中", "九二二爻", "金鳞入手，得还防走。若论周旋，谨慎戒口。");
        putYicuoJin("风天小畜", 4, "中中", "六四四爻", "上下不和同，劳而未有功。出门通大道，从此保初终。");
        putYicuoJin("风天小畜", 6, "上中", "上九六爻", "拟欲迁而未可迁，提防喜处惹勾连。前途若得阴人引，变化鱼龙出大渊。");
        putYicuoJin("天泽履", 1, "上中", "初九初爻", "不远不近，似易似难。等闲入手，云中笑看。");
        putYicuoJin("天泽履", 3, "下下", "六三三爻", "桃李谢春风，西去又复东。家中无意绪，船在浪涛中。");
        putYicuoJin("天泽履", 5, "中中", "九五五爻", "狂风吹起黑云飞，月在天心遮不得。时闲无事暂相关，到底依然无刻剥。");
        putYicuoJin("地天泰", 1, "上中", "初九初爻", "东边事，西边成，风物月华明，高楼弄笛声。");
        putYicuoJin("地天泰", 3, "下下", "九三三爻", "和不和，同不同，翻云覆雨几成空，进退须防终少功。");
        putYicuoJin("地天泰", 5, "上上", "六五五爻", "添一人，获一宝。事团圆，门外讨。");
        putYicuoJin("天地否", 1, "上中", "初六初爻", "相引更相牵，殷勤喜自然。施为无不利，愁事变团圆。");
        putYicuoJin("天地否", 3, "下下", "六三三爻", "无踪又无迹，远近终难觅。平地起风云，似笑还成泣。");
        putYicuoJin("天地否", 5, "下下", "九五五爻", "身不安，心不安，动静两三番，终期事必欢。");
        putYicuoJin("天火同人", 2, "上上", "六二二爻", "心和同，事和同，门外好施工，交加事有终。");
        putYicuoJin("天火同人", 4, "中平", "九四四爻", "意孜孜，心戚戚，要平安，防出入。");
        putYicuoJin("天火同人", 6, "上中", "上九六爻", "一水绕一水，一山绕一山。水尽山穷处，名利不为难。");
        putYicuoJin("火天大有", 2, "上中", "九二二爻", "一重水，一重山，风波道坦然，壶中别有天。");
        putYicuoJin("火天大有", 4, "上上", "九四四爻", "遇险不为忧，风波何足惧。若遇草头人，咫尺青云路。");
        putYicuoJin("火天大有", 6, "上上", "上九六爻", "奇奇奇，地利与天时。灯花传信后，动静总相宜。");
        putYicuoJin("地山谦", 1, "下中", "初六初爻", "恐怕正忧煎，忧非满目前。若逢明鉴照，挠括信空传。");
        putYicuoJin("地山谦", 3, "上中", "九三三爻", "劳心慢劳心，劳心终有成。清风来借力，欢笑见前程。");
        putYicuoJin("地山谦", 5, "下中", "六五五爻", "莺语燕呢喃，花开满院间。倚阑春梦觉，无语敛愁颜。");
        putYicuoJin("雷地豫", 2, "中中", "六二二爻", "凿石得玉，淘沙得珠。眼前目下，何用踌躇。");
        putYicuoJin("雷地豫", 4, "上上", "九四四爻", "名成利遂勿忧煎，菊里秋风道坦然。得意便垂三尺钓，长竿获得锦鳞鲜。");
        putYicuoJin("雷地豫", 6, "下下", "上六六爻", "独钓寒潭涧，中途兴已阑。水寒鱼不饵，小舟明月还。");
        putYicuoJin("泽雷随", 2, "中平", "六二二爻", "一事已成空，一事还成喜。若遇口边人，心下堪凭委。");
        putYicuoJin("泽雷随", 4, "下中", "九四四爻", "鱼上钩，丝纶弱。收拾难，力再著。");
        putYicuoJin("泽雷随", 6, "上上", "上六六爻", "可蓄可储，片玉寸珠。停停稳稳，前遇良图。");
        putYicuoJin("山风蛊", 2, "上中", "九二二爻", "暗去又明来，忧心事可谐。终须成一笑，目下莫疑猜。");
        putYicuoJin("山风蛊", 4, "中中", "六四四爻", "可以委，可以托。事迟迟，无差错。");
        putYicuoJin("山风蛊", 6, "上中", "上九六爻", "深渊鱼可钓，幽林鸟可获。只用久长心，不用生疑惑。");
        putYicuoJin("地泽临", 2, "上上", "九二二爻", "和合事，笑谈成。喜音在半程，平步踏青云。");
        putYicuoJin("地泽临", 4, "上上", "六四四爻", "事团圆，物周旋。一往一来，平步青天。");
        putYicuoJin("地泽临", 6, "上中", "上六六爻", "朦胧秋月映朱扃，林处鸡声远处生。自有贵人相接引，何须巧语似流莺。");
        putYicuoJin("风地观", 1, "上上", "初六初爻", "野鬼暗张弧，射中主人惊。红日沉江海，空中事不成。");
        putYicuoJin("风地观", 3, "上上", "六四三爻", "双燕衔书舞，指日一齐来。寂寞淹留客，从兹下钓台。");
        putYicuoJin("风地观", 5, "下中", "九五五爻", "云叆叆，月朦胧。一雁入云中，残花谢晓风。");
        putYicuoJin("火雷噬嗑", 1, "上中", "初九初爻", "人倚楼，许多愁。淡然进步，事始无忧。");
        putYicuoJin("火雷噬嗑", 3, "上中", "六三三爻", "暗中防霹雳，猜疑浑未实。转眼黑云收，拥出扶桑日。");
        putYicuoJin("火雷噬嗑", 5, "下下", "六五五爻", "堪叹枕边忧，更嗟门里闹。意绪甚索缠，心神亦颠倒。");
        putYicuoJin("山火贲", 2, "上上", "六二二爻", "月已圆，花再发。事悠悠，无不合。");
        putYicuoJin("山火贲", 4, "上中", "六四四爻", "曲中应有直，心事还成戚。云散月重圆，千里风帆急。");
        putYicuoJin("山火贲", 6, "上上", "上九六爻", "明月重圆，颜色欣然。风云相送，和合万年。");
        putYicuoJin("山地剥", 1, "下下", "初六初爻", "上接不稳，下接不和。相缠相扰，平地风波。");
        putYicuoJin("山地剥", 3, "上上", "六三三爻", "玉石犹蒙昧，那堪小悔多。终无咎，笑呵呵。");
        putYicuoJin("山地剥", 5, "中中", "六五五爻", "圆又缺，缺又圆，低低密密要周旋，时来始见缘。");
        putYicuoJin("地雷复", 2, "上中", "六二二爻", "悲后笑嘻嘻，中行道最宜。所求终有望，不必皱双眉。");
        putYicuoJin("地雷复", 4, "上中", "六四四爻", "临渊放钓，清绝点埃。巨鳌随得，何用疑猜。");
        putYicuoJin("地雷复", 6, "下下", "上六六爻", "进步且徘徊，春风柳絮催。水边行客倦，枕畔有忧怀。");
        putYicuoJin("天雷无妄", 1, "上上", "初九初爻", "事相扶，在半途，翻覆终可免，风波一点无。");
        putYicuoJin("天雷无妄", 3, "下下", "六三三爻", "浅水起波澜，平地生荆棘。言语虚参商，犹恐无端的。");
        putYicuoJin("天雷无妄", 5, "上上", "九五五爻", "喜喜喜，春风生桃李。不用强忧煎，明月人千里。");
        putYicuoJin("山天大畜", 2, "中中", "九二二爻", "蜗角蝇头利，而今已变通。草头人笑后，宜始不宜终。");
        putYicuoJin("山天大畜", 4, "下中", "六四四爻", "鹊噪高枝上，人行古渡头。半途不了事，日暮转生愁。");
        putYicuoJin("山天大畜", 6, "上上", "上九六爻", "事有喜，物有光，始终好商量，壶中日月长。");
        putYicuoJin("山雷颐", 1, "下下", "初九初爻", "红叶无颜色，凋零一夜风。邻鸡醒午梦，心事总成空。");
        putYicuoJin("山雷颐", 3, "下下", "六三三爻", "事宜休，理多错。日掩云中，空成担阁。");
        putYicuoJin("山雷颐", 5, "下中", "六五五爻", "进不安，退不可。上下相从，明珠一颗。");
        putYicuoJin("泽风大过", 1, "中中", "初六初爻", "心有余，力不足。倚仗春风，一歌一曲。");
        putYicuoJin("泽风大过", 3, "下下", "九三三爻", "荆棘生平地，风波起四方。倚阑惆怅望，无语对斜阳。");
        putYicuoJin("泽风大过", 5, "中中", "九五五爻", "一人两事，一人两心。新花枯树，须待交春。");
        putYicuoJin("坎为水", 2, "下中", "九二二爻", "梦里说江水，波深下钓难。利名终有望，目下未开颜。");
        putYicuoJin("坎为水", 4, "上上", "六四四爻", "莫怪事迟留，休言不到头。长竿终入手，一钓上金钩。");
        putYicuoJin("坎为水", 6, "下下", "上六六爻", "疑疑疑，一番笑后一番悲。落红满地无人扫，独对西风抹黛眉。");
        putYicuoJin("离为火", 2, "上上", "六二二爻", "谋已定，事何忧，明月上重楼，云中客点头。");
        putYicuoJin("离为火", 4, "下下", "九四四爻", "遇不遇，逢不逢，月沉水底，人在梦中。");
        putYicuoJin("离为火", 6, "中中", "上九六爻", "诛战中邦利出征，一朝获丑在王庭。凤衔一诏堤场畔，得个佳名四海荣。");
        putYicuoJin("泽山咸", 1, "中中", "初六初爻", "意在关中信未来，故人千里自徘徊。天边雁足传书至，一点梅花喜色开。");
        putYicuoJin("泽山咸", 3, "下下", "九三三爻", "休道事无论讹，其中进退多。桂轮圆又缺，光彩更揩磨。");
        putYicuoJin("泽山咸", 5, "下中", "九五五爻", "事了物未了，人圆物未圆。要知端的信，月影上阑干。");
        putYicuoJin("雷风恒", 1, "下下", "初六初爻", "深潭月，明镜影。一场空，妄报信。");
        putYicuoJin("雷风恒", 3, "下下", "九三三爻", "桥已断，路不通。登舟理楫，又遇狂风。");
        putYicuoJin("雷风恒", 5, "下下", "六五五爻", "湖水悠悠，孤舟浪头。来人未识，残月山楼。");
        putYicuoJin("天山遁", 2, "中中", "六二二爻", "兀兀尘埃又待时，幽窗静处有谁知。若逢青紫人相引，财利功名自可期。");
        putYicuoJin("天山遁", 4, "上中", "九四四爻", "一得一虑，宜后欲先。路通大道，心事安然。");
        putYicuoJin("天山遁", 6, "上上", "上九六爻", "一番桃李一番新，谁识阳和气象明。林下水边多活计，见山了了称人情。");
        putYicuoJin("雷天大壮", 1, "下下", "初九初爻", "江阔渡无船，惊涛恐拍天。月斜云淡处，音信有人传。");
        putYicuoJin("雷天大壮", 3, "下中", "九三三爻", "平地起风烟，时下未能安，高处觅因缘。");
        putYicuoJin("雷天大壮", 5, "下中", "六五五爻", "正直宜守，妄动生灾。名通利达，叶落花开。");
        putYicuoJin("火地晋", 1, "上中", "初六初爻", "须著力，莫游遨。长竿钓向蟾蜍窟，直欲云中得巨鳌。");
        putYicuoJin("火地晋", 3, "上上", "六三三爻", "两两意和同，轻帆遇顺风。道逢人得意，歌唱急流中。");
        putYicuoJin("火地晋", 5, "中中", "六五五爻", "万里涉江山，风波尽日闲。已吞钩上饵，何必遇波澜。");
        putYicuoJin("地火明夷", 1, "中中", "初九初爻", "乘翼遥飞去，皆因避远行。一途经济意，又是满园春。");
        putYicuoJin("地火明夷", 3, "上中", "九三三爻", "虚名虚位久沉沉，禄马当头未见真。一片彩云秋后至，去年风物一时新。");
        putYicuoJin("地火明夷", 5, "下下", "六五五爻", "重关深锁闭，谨慎要提防。小节不知戒，因循成大殃。");
        putYicuoJin("风火家人", 2, "下中", "六二二爻", "一镜破，照两人。心中结，合同心。");
        putYicuoJin("风火家人", 4, "上上", "六四四爻", "珠玉走盘中，田园足阜丰。休言谋未遂，此去一时通。");
        putYicuoJin("风火家人", 6, "上中", "上九六爻", "心下事悠然，周旋尚未全。遇龙终有吉，人月永团圆。");
        putYicuoJin("火泽睽", 1, "下中", "初九初爻", "上下分，忧愁决。千嶂云，一轮月。");
        putYicuoJin("火泽睽", 3, "下下", "六三三爻", "鼎沸风波，孤舟渡河。巧中藏拙，人事蹉跎。");
        putYicuoJin("火泽睽", 5, "下中", "六五五爻", "船棹中流急，花开春又迟。事宁心不静，惹起许多疑。");
        putYicuoJin("水山蹇", 1, "下中", "初六初爻", "岸阔水深舟易落，路遥山险步难行。行行自有通津日，目下香消月未明。");
        putYicuoJin("水山蹇", 3, "下下", "九三三爻", "事虑淹留，人不彻头。往来闭塞，要见无由。");
        putYicuoJin("水山蹇", 5, "中中", "九五五爻", "道路任招呼，风波一点无。时间心绪乱，全仗贵人扶。");
        putYicuoJin("雷水解", 2, "上上", "九二二爻", "万里波涛静，一天风雨闲。利名无阻隔，行路出重关。");
        putYicuoJin("雷水解", 4, "上上", "九四四爻", "湖海意悠悠，烟波下钓钩。若逢龙与兔，名利一齐周。");
        putYicuoJin("雷水解", 6, "上中", "上六六爻", "一箭青云路，营求指日成。许多闲口语，翻作笑歌声。");
        putYicuoJin("山泽损", 1, "下中", "初九初爻", "喜喜喜，终防否。获得骊龙颔下珠，忽然失却还沉水。");
        putYicuoJin("山泽损", 3, "中中", "六三三爻", "事未完，心未安。疑虑久，得安然。");
        putYicuoJin("山泽损", 5, "中中", "六五五爻", "莫言枳棘恶，终为鸾凤栖。目前应有待，何用早踌躇。");
        putYicuoJin("风雷益", 1, "上中", "初九初爻", "大事可成功，有益还无咎。云中执鞭人，报在三秋后。");
        putYicuoJin("风雷益", 3, "下下", "六三三爻", "无踪又无迹，远近终难觅。旱海莫行舟，何劳空费力。");
        putYicuoJin("风雷益", 5, "上上", "九五五爻", "子结花残，花开枯树。屋头春意，喜笑嘻嘻。");
        putYicuoJin("泽天夬", 1, "下中", "初九初爻", "神黯黯，意悠悠。收却线，莫下钩。");
        putYicuoJin("泽天夬", 3, "中平", "九三三爻", "虎伏在前途，行人莫乱呼。路旁须仔细，灾祸自然无。");
        putYicuoJin("泽天夬", 5, "下下", "九五五爻", "所事纵云难，平地起波澜。笑谈终有忌，事回心觉喜。");
        putYicuoJin("天风姤", 2, "下中", "九二二爻", "欲济未济，欲求强求。心无一定，一车两头。");
        putYicuoJin("天风姤", 4, "下下", "九四四爻", "居下当亲上，人心易散离。事机才一失，万事尽皆灰。");
        putYicuoJin("天风姤", 6, "下下", "上九六爻", "见不见，也防人背面。遇不遇，到底无凭据。");
        putYicuoJin("泽地萃", 2, "上中", "六二二爻", "笑中生不足，内外见愁哭。云散月光辉，转祸当成福。");
        putYicuoJin("泽地萃", 4, "中中", "九四四爻", "言语参商，波涛鼎沸。事久无伤，时间不利。");
        putYicuoJin("泽地萃", 6, "中中", "上六六爻", "无非应有忌，未为先恐踬。路险风波事更非，要得称心须借势。");
        putYicuoJin("地风升", 1, "上上", "初六初爻", "明月为钩，清风作线。举网烟波，锦鳞易见。");
        putYicuoJin("地风升", 3, "上中", "九三三爻", "舟离古渡月离云，人出重关好问津。且向前行求去住，何须疑虑两三旬。");
        putYicuoJin("地风升", 5, "上上", "六五五爻", "佳信至，见笑颜。飞腾一去，披云上天。");
        putYicuoJin("泽水困", 2, "上中", "九二二爻", "足不安，心不安，两两事相得，忧来却又欢。");
        putYicuoJin("泽水困", 4, "上中", "九四四爻", "目下志难舒，有客来徐徐。金车虽历险，吝必有终欤。");
        putYicuoJin("泽水困", 6, "下下", "上六六爻", "缺月恐难圆，残花不再鲜，苦求名与利，到处遇迍邅。");
        putYicuoJin("水风井", 1, "中中", "初六初爻", "月在云间，昏迷道路。云散月明，渐宜进步。");
        putYicuoJin("水风井", 3, "下下", "九三三爻", "安静事难疑，云中一鹤飞。桃花逢骤雨，水畔女频啼。");
        putYicuoJin("水风井", 5, "上中", "九五五爻", "美在甘，甘有美。始有终，终有始。");
        putYicuoJin("泽火革", 1, "下中", "初九初爻", "意迷心不迷，事宽心不宽。要知端的事，犹隔两重山。");
        putYicuoJin("泽火革", 3, "中中", "九三三爻", "道路迢遥，门庭闭塞。雪拥未分明，云开方见日。");
        putYicuoJin("泽火革", 5, "上中", "九五五爻", "虎变高山别有期，望人目下尚狐疑。雁来嘹呖黄花发，此际声名达帝畿。");
        putYicuoJin("火风鼎", 2, "上上", "九二二爻", "小子出门庭，青衣久问程。贵人来助力，花谢子还成。");
        putYicuoJin("火风鼎", 4, "下中", "九四四爻", "鼎折足，舆脱辙。有贵人，重整续。");
        putYicuoJin("火风鼎", 6, "上上", "上九六爻", "贵客自相亲，功名唾手成。获金须积德，仰望太阳升。");
        putYicuoJin("震为雷", 2, "下下", "六二二爻", "无踪又无迹，远近终难觅。旱海莫行舟，何劳空著力。");
        putYicuoJin("震为雷", 4, "上中", "九四四爻", "白玉隐尘，黄金埋土。久久光辉，也要人举。");
        putYicuoJin("震为雷", 6, "下中", "上六六爻", "细雨濛濛湿，江边路不通。道途人未达，凭仗借东风。");
        putYicuoJin("艮为山", 2, "中中", "六二二爻", "易非易，难非难，忽然平地起波澜，歌笑须教三两番。");
        putYicuoJin("艮为山", 4, "中中", "六四四爻", "山山有路，有终有始。似月如花，守成而已。");
        putYicuoJin("艮为山", 6, "上上", "上九六爻", "宝镜无尘染，金貂已剪裁。也逢天意合，终不染尘埃。");
        putYicuoJin("风山渐", 2, "上上", "六二二爻", "阆苑一时春，庭前花柳新。鹊声传好信，草木尽欣欣。");
        putYicuoJin("风山渐", 4, "上上", "六四四爻", "欲捉月中兔，须凭桃李梯。高山来接引，双喜照双眉。");
        putYicuoJin("风山渐", 6, "中中", "上九六爻", "意迷心不迷，事宽心不宽。一场双喜会，不久出重关。");
        putYicuoJin("雷泽归妹", 2, "下中", "九二二爻", "门外事重叠，阴人多遇合。贤女虽助巧，渺渺终难洽。");
        putYicuoJin("雷泽归妹", 4, "下中", "九四四爻", "缺月重圆，枯枝又鲜。一条坦路，翘首青天。");
        putYicuoJin("雷泽归妹", 6, "中中", "上六六爻", "美红颜，休挂怀。人在车中，舟行海底。");
        putYicuoJin("雷火丰", 1, "上中", "初九初爻", "过尽风波三五重，谁知浪尽又无风。须知明达青云路，用舍行藏不费功。");
        putYicuoJin("雷火丰", 3, "下下", "九三三爻", "纷纷复纷纷，欷嘘独掩门。敛眉望灯火，伴我坐黄昏。");
        putYicuoJin("雷火丰", 5, "下中", "六五五爻", "门内佳音来，生涯应有庆。名利有更迁，雁行终拆阵。");
        putYicuoJin("火山旅", 2, "上上", "六二二爻", "悲脸笑颜开，秋月挂高台，人从千里来。");
        putYicuoJin("火山旅", 4, "中中", "九四四爻", "花落正逢春，行人在半程。事成还不就，索绊两三旬。");
        putYicuoJin("火山旅", 6, "中中", "上九六爻", "憔悴无人问，林间听杜鹃。一声山月笛，千里暗销魂。");
        putYicuoJin("巽为风", 2, "下下", "九二二爻", "着着占先机，其中路不迷。目前无舍意，争免是和非。");
        putYicuoJin("巽为风", 4, "上上", "六四四爻", "江海悠悠，烟波下钩。六鳌连获，歌笑中流。");
        putYicuoJin("巽为风", 6, "下下", "上九六爻", "一月圆，一镜缺。少团圆，无可说。");
        putYicuoJin("兑为泽", 2, "上上", "九二二爻", "玉出昆冈石，舟离古渡滩。行藏终有望，用舍不为难。");
        putYicuoJin("兑为泽", 4, "下下", "九四四爻", "难难难，忽然平地起波澜。易易易，谈笑平常终有忌。");
        putYicuoJin("兑为泽", 6, "下下", "上六六爻", "乐之极矣悲相望，引兑来兮吉与凶。未能光大终须暗，日落西山返照中。");
        putYicuoJin("风水涣", 1, "上上", "初六初爻", "云静日当中，光辉到处通。道途逢水顺，千里快如风。");
        putYicuoJin("风水涣", 3, "中中", "六三三爻", "望处应重山，高深渐可攀。举头天上看，明月出人间。");
        putYicuoJin("风水涣", 5, "中中", "九五五爻", "不归一，劳心力。贵人旁，宜借力。");
        putYicuoJin("水泽节", 2, "下下", "九二二爻", "休眷恋，奔前程。终闹乱，出门庭。");
        putYicuoJin("水泽节", 4, "上上", "六四四爻", "用则行，舍则藏。一骑出重关，佳音咫尺间。");
        putYicuoJin("水泽节", 6, "下下", "上六六爻", "渴穿井，饥画饼。漫劳心，如捕影。遇龙虎，方可省。");
        putYicuoJin("风泽中孚", 1, "上中", "初九初爻", "一点著阳春，枯枝朵朵荣。志专方遇合，初忌二三心。");
        putYicuoJin("风泽中孚", 3, "中中", "六三三爻", "欲行还止，徘徊不已。动摇莫强，得止且止。");
        putYicuoJin("风泽中孚", 5, "上上", "九五五爻", "倾一杯，转愁眉。天地合，好施为。");
        putYicuoJin("雷山小过", 1, "下下", "初六初爻", "物不牢，人断桥。重整理，慢心高。");
        putYicuoJin("雷山小过", 3, "下下", "九三三爻", "深户要牢扃，须防暗里人。莫言无外事，纵好定邅迍。");
        putYicuoJin("雷山小过", 5, "上上", "六五五爻", "空空空，空里得成功。蟠桃千年熟，不怕五更风。");
        putYicuoJin("水火既济", 1, "上上", "初九初爻", "鹿逐云中出，人从月下归。新欣盈笑脸，不用皱双眉。");
        putYicuoJin("水火既济", 3, "上中", "九三三爻", "入而易，出而难。厌厌到再三，交加意不堪。");
        putYicuoJin("水火既济", 5, "中中", "九五五爻", "积德施功有子孙，杀牛祭佛及西邻。功名两字垂成日，回首山前万物新。");
        putYicuoJin("火水未济", 1, "中中", "初六初爻", "桑榆催晚景，缺月恐难圆。若遇刀锥客，方知有喜缘。");
        putYicuoJin("火水未济", 3, "下中", "六三三爻", "万里片帆轻，波平浪不惊。行行无阴滞，远虑更通津。");
        putYicuoJin("火水未济", 5, "上上", "六五五爻", "芰荷香里许恩材，桂魄圆时印绶来。从此威名山岳重，光辉玉节位三台。");
    }
    /**
     * B67：附录·一撮金「以爻诀断之」——取本卦动爻之爻诀断辞（附六等吉凶）。
     *
     * <p>一撮金法专用断法：以所得动爻对逐卦断辞，六等吉凶（上上/上中/中中/中平/下中/下下）。
     * DB（{@code yicuo_jin_yao}）为唯一真源，经 OBDA 取；OBDA/库不可用时降级为内置同源表
     * {@link #YICUOJIN_YAO}（流程不阻断）。
     */
    private String yicuoJinYaoNote(String hexagram, int movingLine) {
        if (hexagram == null || hexagram.isBlank() || movingLine < 1 || movingLine > 6) {
            return "";
        }
        String grade = "";
        String yao = "";
        String text = "";
        if (backendService != null && backendService.getObdaHandler() != null) {
            try {
                String sparql = "PREFIX jud: <http://example.org/plumblossom/judgement/>\n"
                        + "SELECT ?t ?g ?y WHERE {\n"
                        + "  ?a a jud:YicuoJinAdmonition ;\n"
                        + "     jud:yicuoJinAdmonitionHexagram \"" + hexagram + "\" ;\n"
                        + "     jud:yicuoJinLinePosition " + movingLine + " ;\n"
                        + "     jud:yicuoJinDictumText ?t ;\n"
                        + "     jud:yicuoJinGradeText ?g ;\n"
                        + "     jud:yicuoJinYaoLabel ?y .\n"
                        + "}";
                List<Map<String, String>> rows = backendService.getObdaHandler().executeAboxQuery(sparql);
                if (rows != null && !rows.isEmpty()) {
                    Map<String, String> r = rows.get(0);
                    grade = r.getOrDefault("g", "");
                    yao = r.getOrDefault("y", "");
                    text = r.getOrDefault("t", "");
                }
            } catch (Exception e) {
                log.warn("⚠️ yicuoJinYaoNote | 爻诀查询失败，降级内置同源表：{}", e.getMessage());
            }
        }
        if (text == null || text.isBlank()) {
            String[] arr = YICUOJIN_YAO.get(hexagram);
            if (arr != null && arr[movingLine] != null) {
                String[] parts = arr[movingLine].split("\\|", 3);
                grade = parts[0];
                yao = parts.length > 1 ? parts[1] : "";
                text = parts.length > 2 ? parts[2] : "";
            }
        }
        if (text == null || text.isBlank()) {
            return "";
        }
        return "〔一撮金·爻诀断法（本卦" + hexagram + (yao.isBlank() ? "" : "·" + yao)
                + "，得「" + grade + "」）：「" + text + "」——附录·邵康节易数一撮金「乃以爻诀断之」〕";
    }

    /**
     * 笔画数取值（第 28 轮·引入外部资源 Unihan 笔画库）。
     *
     * <p>数据源：**Unicode Han Database（Unihan）`kTotalStrokes`**（unicode.org UCD），
     * 经脚本生成资源 `resources/plumblossom/unihan-strokes.tsv`（102 999 字），
     * 类路径懒加载为 {@code STROKE_TABLE}；一撮金、字占（一字/二字/三字）、相字所需笔画数皆准此。
     * 表内无者（生僻字/非汉字符）回退「常用字内置表」，再回退「码位取模」，保证起卦可运算。
     */
    private static int stroke(char c) {
        Integer s = strokeTable().get(c);
        if (s != null) {
            return s;
        }
        switch (c) {
            case '一': return 1; case '二': return 2; case '三': return 3; case '四': return 5;
            case '五': return 4; case '六': return 4; case '七': return 2; case '八': return 2;
            case '九': return 2; case '十': return 2; case '乾': return 11; case '坤': return 8;
            case '震': return 15; case '巽': return 12; case '坎': return 7; case '离': return 10;
            case '艮': return 6; case '兑': return 7;
            default: return Math.floorMod(c, 8) + 1;
        }
    }

    // ------------------------------------------------------------------
    // 外部资源装载（第 28 轮）：Unihan 笔画库 + 平水韵声调表（类路径懒加载）
    // ------------------------------------------------------------------
    private static volatile Map<Character, Integer> STROKE_TABLE;
    private static volatile Map<Character, Integer> TONE_TABLE;

    /** 笔画表（`plumblossom/unihan-strokes.tsv`：char&lt;TAB&gt;strokes），首用装载一次。 */
    private static Map<Character, Integer> strokeTable() {
        Map<Character, Integer> t = STROKE_TABLE;
        if (t == null) {
            synchronized (PlumBlossomOntologyJobWorker.class) {
                t = STROKE_TABLE;
                if (t == null) {
                    t = loadTsv("/plumblossom/unihan-strokes.tsv");
                    STROKE_TABLE = t;
                }
            }
        }
        return t;
    }

    /** 平水韵声调表（`plumblossom/pingshui-tone.tsv`：char&lt;TAB&gt;tone，1平2上3去4入），首用装载一次。 */
    private static Map<Character, Integer> toneTable() {
        Map<Character, Integer> t = TONE_TABLE;
        if (t == null) {
            synchronized (PlumBlossomOntologyJobWorker.class) {
                t = TONE_TABLE;
                if (t == null) {
                    t = loadTsv("/plumblossom/pingshui-tone.tsv");
                    TONE_TABLE = t;
                }
            }
        }
        return t;
    }

    /** 读取 `char&lt;TAB&gt;int` 之 TSV（跳过 `#` 注释行）；资源缺失时返回空表（不抛异常）。 */
    private static Map<Character, Integer> loadTsv(String res) {
        Map<Character, Integer> m = new HashMap<>();
        try (InputStream in = PlumBlossomOntologyJobWorker.class.getResourceAsStream(res)) {
            if (in == null) {
                return m;
            }
            try (BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    if (line.isEmpty() || line.charAt(0) == '#') {
                        continue;
                    }
                    int tab = line.indexOf('\t');
                    if (tab <= 0) {
                        continue;
                    }
                    m.put(line.charAt(0), Integer.parseInt(line.substring(tab + 1).trim()));
                }
            }
        } catch (Exception e) {
            log.warn("⚠ 外部资源装载失败 {}：{}", res, e.getMessage());
        }
        return m;
    }

    /** 平水韵声调（1平/2上/3去/4入）；表中无者返 0。 */
    private static int toneOf(char c) {
        Integer t = toneTable().get(c);
        return t == null ? 0 : t;
    }

    /** 平水韵声调名（用于记注）。 */
    private static String toneName(int t) {
        return switch (t) {
            case 1 -> "平";
            case 2 -> "上";
            case 3 -> "去";
            case 4 -> "入";
            default -> "?";
        };
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
        Map<Integer, String> m = ontoBodyUseName;
        if (m != null && m.get(level) != null) {
            return m.get(level);
        }
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
        Map<Integer, String> m = ontoBodyUseLabel;
        if (m != null && m.get(level) != null) {
            return m.get(level);
        }
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

    /**
     * 分值（1–5）→ 吉凶等级词。**本体等级驱动**：优先按 {@code ontoAuspiciousness}
     * （{@code jud:Auspiciousness}：rdfs:label→auspiciousnessLevel）反查分值对应的等级词，
     * 本体未就绪时回退内置五级（大凶/凶/平/吉/大吉）。
     */
    private String scoreToLevel(int score) {
        int lv = Math.max(1, Math.min(5, score));
        Map<String, Integer> aus = ontoAuspiciousness;
        if (aus != null && !aus.isEmpty()) {
            for (Map.Entry<String, Integer> e : aus.entrySet()) {
                if (e.getKey() != null && e.getValue() != null && e.getValue() == lv) {
                    return e.getKey();
                }
            }
        }
        return switch (lv) {
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
     * 体用互变之诀·互卦分「体之互/用之互」、体互最紧用互次之（原著卷之三·体用互变之诀 L1587–1596）。
     *
     * <p>「大凡占卜，以体为其主，互用变皆为应卦。用最紧，互次之，变卦又次之……然互卦则
     * 分其有体之互，有用之互。如体在上，则上互为体之互，下互为用之互；体卦在下，则下互为
     * 体之互，上互为用之互。体互最紧，用互次之。」互卦＝本卦中间四爻：下互＝二三四爻、
     * 上互＝三四五爻。本诀按体位（动爻在下卦则上体下用，见 {@link #handleDetermineBodyUse}）定
     * 体之互属上互或下互，断时以体之互为重、用之互次之。末以变卦定事之末后（变克体则末后不吉，
     * 变生体及比和则临终吉利）。乾坤无互（互其变卦）者，本诀不适用，返回空串。</p>
     */
    private String mutualBodyUseNote(String hexagram, String body, int movingLine) {
        if (hexagram == null || hexagram.isBlank() || body == null || body.isBlank()) {
            return "";
        }
        int[] pair = pairOf(hexagram);
        if (pair[0] == pair[1] && (pair[0] == 1 || pair[0] == 8)) {
            return ""; // 乾坤无互，互其变卦——无「体互/用互」之分
        }
        int[] lines = reconstructLines(hexagram);
        int upMutual = trigramOfBits(lines[2], lines[3], lines[4]);   // 上互＝三、四、五爻
        int lowMutual = trigramOfBits(lines[1], lines[2], lines[3]);  // 下互＝二、三、四爻
        boolean bodyUpper = movingLine <= 3; // 动爻在下卦 → 上体下用（与 determineBodyUse 同口径）
        String bodyMutualName = TRI_NAME[bodyUpper ? upMutual : lowMutual];
        String useMutualName = TRI_NAME[bodyUpper ? lowMutual : upMutual];
        String bodyMutualRel = relationToBody(bodyMutualName, body);
        String useMutualRel = relationToBody(useMutualName, body);
        return "〔体用互变之诀：用最紧、互次之、变又次之；互卦分体之互/用之互——"
                + (bodyUpper ? "体在上，上互为体之互、下互为用之互" : "体在下，下互为体之互、上互为用之互")
                + "。体互最紧：体之互「" + bodyMutualName + "」与体「" + body + "」为" + bodyMutualRel
                + "（此为先取）；用互次之：用之互「" + useMutualName + "」与体为" + useMutualRel
                + "（此为次取）。变卦主末后之应：变克体则末后不吉，变生体及比和则临终吉利〕";
    }

    /**
     * 应期（A7）：以全卦之数（先天卦数之和）分「近/中/远」，并并列「五行生数」（水1火2木3金4土5）
     * 与卦气二法互参，注明数系以免混淆（生数 ≠ 先天卦数）；依吉凶取吉期/凶期，依动静调迟速。
     */
    private static String responsePeriodOf(int guaTotal, String bodyPhase, String usePhase,
                                           String motion, String auspiciousness) {
        int t = Math.max(1, guaTotal);
        // 原著卷之一·占例结语（老人有忧色占）：「行则应速，以遂成卦之数，中分而取其半也；
        // 坐则事应迟，当倍其成卦之数而定之也；立则半迟半速，止以成卦之数定之可也。」
        // （另卷之三·动静：「坐则应迟，行则应速，走则愈速，卧则愈迟。」）
        int num;
        int den;
        String motionNote;
        switch (motion == null ? "" : motion) {
            case "行" -> { num = 1; den = 2; motionNote = "行则应速，中分取半其数"; }
            case "走" -> { num = 1; den = 2; motionNote = "走则愈速，亦从其半而其应更急"; }
            case "坐" -> { num = 2; den = 1; motionNote = "坐则事应迟，倍其成卦之数"; }
            case "卧" -> { num = 2; den = 1; motionNote = "卧则愈迟，亦倍其成卦之数"; }
            case "立" -> { num = 1; den = 1; motionNote = "立则半迟半速，即以成卦之数"; }
            default -> { num = 1; den = 1; motionNote = "动静未详，即以成卦之数"; }
        }
        int base = Math.max(1, (int) Math.round((double) t * num / den));
        String level;
        String unit;
        if (base <= 6) {
            level = "近";
            unit = "日";
        } else if (base <= 11) {
            level = "中";
            unit = "月";
        } else {
            level = "远";
            unit = "年";
        }
        int low = Math.max(1, base - 3);
        int high = base + 3;
        Map<String, Integer> shengShu = loreMapInt("SHENG_SHU", SHENG_SHU);
        int shengBody = shengShu.getOrDefault(bodyPhase == null ? "" : bodyPhase, 5);
        int shengUse = shengShu.getOrDefault(usePhase == null ? "" : usePhase, 5);
        boolean bad = auspiciousness != null && auspiciousness.contains("凶");
        String kind = bad ? "凶期（有克体之卦则事败于克体卦气之月日）"
                : "吉期（以生体、比和之年月为吉期）";
        return level + "·约" + low + "~" + high + unit + "内"
                + "【取数系：全卦之数（先天卦数之和＝" + t + "）"
                + (base != t ? "，按〈" + motionNote + "〉折为" + base : "，〈" + motionNote + "〉不改其数") + "】；"
                + "参五行生数（体" + bodyPhase + "＝" + shengBody + "、用" + usePhase + "＝" + shengUse + "），"
                + kind + "。";
    }

    /**
     * 天时占（A14）：原著卷之二·天时占第一「凡占天时，**不分体用**，全观诸卦，详推五行」，
     * 与其余诸占「详观体用」相别（另卷三·诸事响应歌「乾明坤晦巽多风，震主雷霆定莫疑」；
     * 卷三·体用「占天时有震则有雷，有巽则有风，逢坎则有雨，逢离则晴」）。
     *
     * <p>故取本卦、互卦、变卦之上下共六卦，数八卦之众寡而推晴雨：离多主晴、坎多主雨、
     * 坤乃阴晦、乾主晴明、震多则雷、巽多则风、艮多则止（久雨必晴）、兑多则不雨亦阴。
     * 此占不论体用、不以生克吉凶断。
     */
    private static String tianShiVerdict(String name, String mutual, String changed) {
        int[] cnt = new int[9];
        StringBuilder who = new StringBuilder();
        int seen = 0;
        for (String h : new String[]{name, mutual, changed}) {
            if (h == null || h.isBlank()) {
                continue;
            }
            int[] p = pairOf(h);
            cnt[p[0]]++;
            cnt[p[1]]++;
            seen += 2;
            if (who.length() > 0) {
                who.append('、');
            }
            who.append(h).append('(').append(TRI_NAME[p[0]]).append(TRI_NAME[p[1]]).append(')');
        }
        if (seen == 0) {
            return "天时之占：卦象未备，难以详推。";
        }
        int qian = cnt[1];
        int dui = cnt[2];
        int li = cnt[3];
        int zhen = cnt[4];
        int xun = cnt[5];
        int kan = cnt[6];
        int gen = cnt[7];
        int kun = cnt[8];

        StringBuilder sb = new StringBuilder();
        sb.append("依「天时占第一」凡占天时不分体用、全观诸卦（本/互/变六卦：").append(who).append("），详推五行：");
        if (kan > li) {
            sb.append("坎（").append(kan).append("）多于离（").append(li).append("），主雨");
        } else if (li > kan) {
            sb.append("离（").append(li).append("）多于坎（").append(kan).append("），主晴");
        } else if (kan > 0) {
            sb.append("坎离均见（各").append(kan).append("），主时晴时雨");
        } else {
            sb.append("不见坎离，晴雨未定");
        }
        sb.append("；");
        if (qian > 0) {
            sb.append("乾（").append(qian).append("）主晴明；");
        }
        if (kun > 0) {
            sb.append("坤（").append(kun).append("）主阴晦；");
        }
        if (zhen > 0) {
            sb.append("震（").append(zhen).append("）主雷（春夏雷轰）；");
        }
        if (xun > 0) {
            sb.append("巽（").append(xun).append("）主风；");
        }
        if (gen > 0) {
            sb.append("艮（").append(gen).append("）主止（久雨得艮则晴）；");
        }
        if (dui > 0) {
            sb.append("兑（").append(dui).append("）主不雨亦阴；");
        }
        sb.append("此天时之应，不论体用，亦不以生克定吉凶。");
        // B66：体用类·心易生克之例（不论体用之相制，原著卷之二·体用类 L1390–1392）。
        sb.append(tiYongLeiRestraint(qian, dui, zhen, xun, kan, li, gen, kun));
        return sb.toString();
    }

    /**
     * 体用类·心易生克之例——「不论体用」之相制（原著卷之二·体用类 L1390–1392）。
     *
     * <p>「然卦之生克，有不论体用者……又如乾、兑多则巽无风；坤、艮多则坎无雨；坎多，
     * 则离亦不晴。盖以乾、兑之金，克震、巽之木；坤、艮之土，克坎水；坎水克离火也。」
     * 此非以体用生克论，乃以「党多者制其所属」之五行相制并观诸卦而后断。占饮食亦然
     * （见 {@link #dietNote}：遇坤、艮则坎亦无酒，离值则兑亦无食）。</p>
     */
    private static String tiYongLeiRestraint(int qian, int dui, int zhen, int xun,
                                             int kan, int li, int gen, int kun) {
        List<String> parts = new ArrayList<>();
        if (qian + dui >= 2 && zhen + xun > 0) {
            parts.add("乾、兑金多（" + (qian + dui) + "）克震、巽之木，虽有震、巽亦主无风");
        }
        if (gen + kun >= 2 && kan > 0) {
            parts.add("坤、艮土多（" + (gen + kun) + "）克坎水，虽有坎亦主无雨");
        }
        if (kan >= 2 && li > 0) {
            parts.add("坎水多（" + kan + "）克离火，虽有离亦不晴");
        }
        return parts.isEmpty() ? "" : "〔体用类·心易生克之例（不论体用之相制）：" + String.join("；", parts) + "〕";
    }

    /**
     * 分类占断（A4）：依原著卷之二/卷三「逐卦生体克体之应」，为事项类别各立主断依据，
     * 据体用生克 5 级取「吉断」或「凶断」。吉断用于 level 1/2/3，凶断用于 level 4/5。
     */
    private String categoryVerdict(String category, String hexagram, String shengKe,
                                   String body, String use, String mutual, String changed,
                                   int movingLine) {
        int level = parseShengKeLevel(shengKe);
        String cat = (category == null || category.isBlank()) ? "所问之事" : category;
        String tone = shengKeName(level) + "·" + shengKeLabel(level);
        String[] pair = categoryRulePair(cat);
        boolean good = level <= 3;
        String outcome = good ? pair[0] : pair[1];
        StringBuilder extra = new StringBuilder();
        // B20：占生产·辨男女（原著卷之二·生产占第六）——阳卦阳爻多者生男、阴多者生女。
        if ("生产".equals(cat) && hexagram != null && !hexagram.isBlank()) {
            extra.append("〔辨男女：").append(birthGenderHint(hexagram)).append("〕");
        }
        // B21：婚姻占·八卦性情容貌（原著卷之二·婚姻占第五 L1081–1088）——以用卦（所婚之家）之象推对方。
        if ("婚姻".equals(cat) && use != null && !use.isBlank() && loreMap("MARRIAGE_APPEARANCE", MARRIAGE_APPEARANCE).containsKey(use)) {
            extra.append("〔论婚·对方（用卦").append(use).append("）性情容貌：")
                    .append(loreMap("MARRIAGE_APPEARANCE", MARRIAGE_APPEARANCE).get(use)).append("〕");
        }
        // B22：疾病占·测鬼神（原著卷之二·疾病占第十六 L1131–1132）——
        //      「如卦有克体者，即可测其鬼神……卦中无克体之卦者，不必论之」。
        if ("疾病".equals(cat)) {
            String spirit = diseaseSpiritDetail(body, use, mutual, changed);
            if (!spirit.isEmpty()) {
                extra.append("〔测鬼神：").append(spirit).append("〕");
            }
            // B64：疾病占·「乾上坤下」六爻动逐爻断病之例（原著卷之二·疾病占第十六 L1133–1134）。
            extra.append(diseaseYaoNote(hexagram, movingLine));
        }
        // B23：饮食占·坎酒兑食与逐卦所食（原著卷之二·饮食占第七 L1097 / 卷之三·饮食篇 L1641–1653）。
        if ("饮食".equals(cat)) {
            extra.append("〔").append(dietNote(body, use, mutual, changed)).append("〕");
        }
        // B24：失物占·以变卦方位定失物所在（原著卷之二·失物占第十五 L1127–1128）。
        if ("失物".equals(cat)) {
            String d = lostItemDetail(changed);
            if (!d.isEmpty()) {
                extra.append("〔").append(d).append("〕");
            }
        }
        // B25：出行占·逐卦行止与所宜（原著卷之二·出行占第十二 L1115）。
        if ("出行".equals(cat)) {
            String d = travelDetail(body, use, mutual, changed);
            if (!d.isEmpty()) {
                extra.append("〔").append(d).append("〕");
            }
        }
        // B26：行人占·用卦为行人之盛旺与逐卦（原著卷之二·行人占第十三 L1122）。
        if ("行人".equals(cat)) {
            String d = travelerDetail(body, use, mutual, changed);
            if (!d.isEmpty()) {
                extra.append("〔").append(d).append("〕");
            }
        }
        // B27：求名占·变卦方道与在任忌克体（原著卷之二·求名占第九 L1106）。
        if ("求名".equals(cat)) {
            String d = fameDetail(body, use, mutual, changed);
            if (!d.isEmpty()) {
                extra.append("〔").append(d).append("〕");
            }
        }
        // B28：求财占·卦气定得财/破财之日（原著卷之二·求财占第十 L1108–1110）。
        if ("求财".equals(cat)) {
            String d = wealthDetail(body, use, mutual, changed);
            if (!d.isEmpty()) {
                extra.append("〔").append(d).append("〕");
            }
        }
        // B33（节点10）：卦义性情（原著卷之三·诸卦反对性情）——本卦之杂卦反对性情，以见所问之卦义。
        String opp = hexagramOppositeNature(hexagram);
        if (!opp.isEmpty()) {
            extra.append("〔卦义性情：本卦").append(hexagram).append("（").append(opp).append("）〕");
        }
        return "依「" + cat + "」分项断法（本卦" + hexagram + "）：" + tone + "。断曰：" + outcome + extra
                + "。（体卦为主＝己身之兆，用卦为事＝应事之端，互卦为中间，变卦为末后。）";
    }

    /**
     * 十八类事项分项断语：{吉断(生体/比和/体克用), 凶断(体生用/用克体)}，据原著卷之二占例。
     *
     * <p><b>C11 口径注（卷二/卷三异文两存）</b>：本表出行/疾病/坟墓诸断之碎句，取自**卷之三·诸事响应歌**
     * （L1705「坎则乘舟离旱途，乾震动则坤艮止」、L1715「离宜服热坎服冷，坤土卦温补料亨」、
     * L1721–1722「坤则平阳巽林里，乾宜高葬艮临山，离近人烟兑兴废」）；而**卷之二**本占另有异文——
     * 出行 L1115「巽宜乘船、离宜陆行」（无「坎乘舟」）、疾病 L1130「艮温补、乾兑凉药」（非「坤温补」）。
     * 二者皆出原著、互有出入，今**两存**：本表仍录卷之三歌诀原句，卷二之别诀另见
     * {@link #travelDetail} 等；不擅改经文、不偏废一说。
     */
    private static final Map<String, String[]> CATEGORY_RULE = new LinkedHashMap<>();

    static {
        // 天时（A14）：原著「不分体用」，改由 {@link #tianShiVerdict} 全观诸卦断之；
        // 此条仅作 categoryVerdict 直调时的兜底，正常流程不经此分支。
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
    // 卷之二·分项占断细则：婚姻性情容貌 / 疾病测鬼神 / 饮食所食（B21/B22/B23）
    // ==========================================================================

    /** 婚姻占·八卦性情容貌（原著卷之二·婚姻占第五 L1081–1088）。 */
    private static final Map<String, String> MARRIAGE_APPEARANCE = new LinkedHashMap<>();

    /** 疾病占·克体之卦测鬼神（原著卷之二·疾病占第十六 L1131–1132）。 */
    private static final Map<String, String> SPIRIT_NOTE = new LinkedHashMap<>();

    /** 饮食占·逐卦所食何物（原著卷之三·饮食篇 L1641–1653）。 */
    private static final Map<String, String> DIET_FOOD = new LinkedHashMap<>();

    static {
        MARRIAGE_APPEARANCE.put("乾", "端正而长");
        MARRIAGE_APPEARANCE.put("坎", "邪淫、黑色、嫉妒、奢侈");
        MARRIAGE_APPEARANCE.put("艮", "色黄多巧");
        MARRIAGE_APPEARANCE.put("震", "美貌难犯");
        MARRIAGE_APPEARANCE.put("巽", "发少稀疏，丑陋心贪");
        MARRIAGE_APPEARANCE.put("离", "短赤色，性不常");
        MARRIAGE_APPEARANCE.put("坤", "貌丑，大腹而黄");
        MARRIAGE_APPEARANCE.put("兑", "高长，语话喜悦，白色");

        SPIRIT_NOTE.put("乾", "主有西北方之神，或兵刀之鬼，或天行时气，或称正之邪神");
        SPIRIT_NOTE.put("坤", "主西南之神，或旷野之鬼，或连亲之鬼，或水土里社之神，或犯方隅，或无主之祟");
        SPIRIT_NOTE.put("震", "主东方之神，或木下之神，或妖怪百端，或影响时见");
        SPIRIT_NOTE.put("巽", "主东南之鬼，或自缢戕生，或枷锁致命");
        SPIRIT_NOTE.put("坎", "主北方之鬼，或水旁之神，或没溺而亡，或血疾之鬼");
        SPIRIT_NOTE.put("离", "主南方之鬼，或猛勇之神，或犯灶司，或得愆于香火，或焚烧之鬼，或遇热病而亡");
        SPIRIT_NOTE.put("艮", "主东北之神，或山林之祟，或山魈木客，或土怪石精");
        SPIRIT_NOTE.put("兑", "主西方之神，或阵亡之鬼，或废疾之鬼，或刎颈戕生之鬼");

        DIET_FOOD.put("乾", "圆坚而味辛，为牲之首，为马为猪；有坎乃江湖海味，有水而蔬果珍羞；秋得之而食禄盛");
        DIET_FOOD.put("坤", "所用瓦器，米果之味；静则梨枣茄芋，动则鱼虾鲜羊；其色黑黄，其味甘甜");
        DIET_FOOD.put("震", "木属，大树之果、园林之蔬；色青而味酸；同离多主盐茶，见坎或为盐醋");
        DIET_FOOD.put("巽", "色白青、性曲直、味酸；桃李木瓜、斋辣素食，为鱼为鸡，其豆其面");
        DIET_FOOD.put("坎", "水族之味，味香有细鳞或四足，凡水簇必可饮食；色黑味咸；克出饮酒、生回食鱼");
        DIET_FOOD.put("离", "煎煿炙烧，或茶盐；为鸡为雉、为蟹为蛇，色赤味苦、性热气香；逢坎而酒请有争");
        DIET_FOOD.put("艮", "土物同烹；秋为蟹、春为马；内多肉、味辛；于菜为芹，于物带羽");
        DIET_FOOD.put("兑", "属白金，味辛色白；动物刀砧、辛辣、包里腌藏；盛而有腥臭、旺而有羊鹅");
    }

    /** 婚姻占·八卦性情容貌（原著卷之二·婚姻占第五 L1081–1088）。 */
    private static String marriageAppearance(String trigram) {
        return trigram == null ? "" : loreMap("MARRIAGE_APPEARANCE", MARRIAGE_APPEARANCE).getOrDefault(trigram, "");
    }

    /** 疾病占·某克体之卦所测鬼神（原著卷之二·疾病占第十六 L1131–1132）。 */
    private static String diseaseSpiritNote(String trigram) {
        return trigram == null ? "" : loreMap("SPIRIT_NOTE", SPIRIT_NOTE).getOrDefault(trigram, "");
    }

    /**
     * 疾病占·测鬼神（原著卷之二·疾病占第十六 L1131–1132）：「如卦有克体者，即可测其鬼神……
     * 卦中无克体之卦者，不必论之。」取用/互/变诸卦中「克体」者，各录其方位鬼神之应。
     */
    private String diseaseSpiritDetail(String body, String use, String mutual, String changed) {
        if (body == null || body.isBlank()) {
            return "";
        }
        LinkedHashMap<String, String> notes = new LinkedHashMap<>();
        for (String t : nonBodyTrigrams(use, mutual, changed, body)) {
            if ("克体".equals(relationToBody(t, body))) {
                String n = diseaseSpiritNote(t);
                if (!n.isEmpty()) {
                    notes.putIfAbsent(t, t + "卦克体（" + n + "）");
                }
            }
        }
        return String.join("；", notes.values());
    }

    /**
     * 疾病占·「乾上坤下」六爻动逐爻断病之例（原著卷之二·疾病占第十六 L1133–1134）。
     *
     * <p>原著以「乾上坤下」（＝天地否，乾☰上、坤☷下）为例，逐爻动而演示占病之断法：
     * 初爻动变震木（互巽艮，俱生成之义→不灾、逢生之日即愈）；二爻动变坎水（泄体败金，
     * 互巽离，风火扇炉俱克体→焚尸之象，断之死）；三爻动坤变艮土（俱生体→吉）；四爻动
     * 乾变巽木（金木俱克体，互吉亦凶；木有扛尸、金为砖椁→尸必定）；五爻动乾变离
     * （反能生体，互变俱生体→吉）；六爻动乾变兑（能泄体，互巽艮，一凶一吉→非死必危）。
     * 末云「余卦皆仿此断，则心易无不验矣」——即占病当以动爻之变、互，逐爻以其与体之生克断之，
     * 不徒执一体用。本应用承此，于占疾病时输出该法；若本卦正为「天地否」，则并标出当前动爻。
     */
    private static final String[] QIAN_KUN_DISEASE_YAO = {
            "",
            "初爻动：变为震木，互见巽、艮，俱是生成之义，是谓不灾，逢生之日即愈",
            "二爻动：变为坎水，乃泄体败金之义（金入水乡），互见巽、离（风火扇炉），俱为克体之义"
                    + "——即为焚尸之象，断之死无疑（更看占时外应、春夏秋冬四时推之）",
            "三爻动：坤变艮土，俱在生体之义，不问互卦，亦断其吉无疑",
            "四爻动：乾变巽木，金木俱有克体之义，互吉亦凶；木有扛尸之义、金为砖椁之推"
                    + "——是尸必定之理",
            "五爻动：乾变离，反能生体，互、变俱生体，其吉无疑（更有吉兆则愈吉，凶则迟而忍死）",
            "六爻动：乾变兑，则能泄体，互见巽、艮，一凶一吉，其病非死必危"
                    + "（宜看兆之吉凶：吉则言吉、凶则言凶）"
    };

    /** 疾病占·逐爻断病之例（原著卷之二·疾病占第十六 L1133–1134）。 */
    private static String diseaseYaoNote(String hexagram, int movingLine) {
        boolean isQianKun = "天地否".equals(hexagram);
        StringBuilder sb = new StringBuilder();
        sb.append("〔占病逐爻之断（原著卷之二·疾病占第十六「乾上坤下」六爻动之例）：「乾上坤下」＝天地否，"
                + "逐爻动之断——");
        for (int i = 1; i <= 6; i++) {
            if (i > 1) {
                sb.append("；");
            }
            if (isQianKun && movingLine == i) {
                sb.append("【今动】");
            }
            sb.append(qianKunDiseaseYao(i));
        }
        sb.append("。余卦皆仿此断（以动爻之变、互，逐爻以其与体之生克断之），则心易无不验矣〕");
        return sb.toString();
    }

    /**
     * 饮食占·坎酒兑食与逐卦所食（原著卷之二·饮食占第七 L1097：「又卦中有坎则有酒，有兑则有食。
     * 无坎无兑，则皆无。兑、坎生身，酒肉醉饱」；「欲知所食何物，以饮食推之」，
     * 所食何物依卷之三·饮食篇 L1641–1653 以用卦（饮食之应）推之）。
     */
    private String dietNote(String body, String use, String mutual, String changed) {
        LinkedHashSet<String> tris = new LinkedHashSet<>();
        if (body != null && !body.isBlank()) {
            tris.add(body);
        }
        tris.addAll(nonBodyTrigrams(use, mutual, changed, null));
        boolean hasKan = tris.contains("坎");
        boolean hasDui = tris.contains("兑");
        StringBuilder sb = new StringBuilder();
        if (hasKan) {
            sb.append("有坎则有酒");
        }
        if (hasDui) {
            if (sb.length() > 0) {
                sb.append("、");
            }
            sb.append("有兑则有食");
        }
        if (!hasKan && !hasDui) {
            sb.append("无坎无兑，则酒食皆无");
        } else if (hasKan && hasDui) {
            sb.append("（坎兑生身，酒肉醉饱）");
        }
        // B66：体用类·心易生克之例（占饮食之相制，原著卷之二·体用类 L1392）——
        //      「如遇坤、艮，则坎亦无酒，离值则兑亦无食。」
        if (hasKan && (tris.contains("艮") || tris.contains("坤"))) {
            sb.append("；然遇坤、艮，则坎亦无酒（土克水）");
        }
        if (hasDui && tris.contains("离")) {
            sb.append("；离值则兑亦无食（火克金）");
        }
        if (use != null && !use.isBlank() && loreMap("DIET_FOOD", DIET_FOOD).containsKey(use)) {
            sb.append("；以用卦").append(use).append("（饮食之应）推所食何物：").append(loreMap("DIET_FOOD", DIET_FOOD).get(use));
        }
        return sb.toString();
    }

    /** 汇集「用、互（上下）、变（上下）」所含八卦名（去重、保序，可去除体卦）。 */
    private static List<String> nonBodyTrigrams(String use, String mutual, String changed, String body) {
        LinkedHashSet<String> set = new LinkedHashSet<>();
        if (use != null && !use.isBlank()) {
            set.add(use);
        }
        for (String h : new String[]{mutual, changed}) {
            if (h != null && !h.isBlank()) {
                int[] p = pairOf(h);
                set.add(TRI_NAME[p[0]]);
                set.add(TRI_NAME[p[1]]);
            }
        }
        if (body != null) {
            set.remove(body);
        }
        return new ArrayList<>(set);
    }

    /** 含重复地汇集「体＋用＋互（上下）＋变（上下）」所含八卦名（用于「某卦多」计数）。 */
    private List<String> allTrigramsMulti(String body, String use, String mutual, String changed) {
        List<String> list = new ArrayList<>();
        if (body != null && !body.isBlank()) {
            list.add(body);
        }
        if (use != null && !use.isBlank()) {
            list.add(use);
        }
        for (String h : new String[]{mutual, changed}) {
            if (h != null && !h.isBlank()) {
                int[] p = pairOf(h);
                list.add(TRI_NAME[p[0]]);
                list.add(TRI_NAME[p[1]]);
            }
        }
        return list;
    }

    /** 统计 list 中属于 names 的个数（用于「震多/艮多」等）。 */
    private static int countOf(List<String> list, String... names) {
        int c = 0;
        for (String s : list) {
            for (String n : names) {
                if (n.equals(s)) {
                    c++;
                }
            }
        }
        return c;
    }

    /** 用/互/变中五行**克体**之卦名（去重、保序）；无体则空。 */
    private List<String> keBodyTrigrams(String body, String use, String mutual, String changed) {
        List<String> out = new ArrayList<>();
        if (body == null || body.isBlank()) {
            return out;
        }
        for (String t : nonBodyTrigrams(use, mutual, changed, body)) {
            if ("克体".equals(relationToBody(t, body))) {
                out.add(t);
            }
        }
        return out;
    }

    // ==========================================================================
    // 卷之二·诸占逐卦细则：失物方位 / 出行行止 / 行人盛衰 / 求名方道 / 求财定日（B24–B28）
    // ==========================================================================

    /** 失物占·以变卦定失物之所在（原著卷之二·失物占第十五 L1127–1128）。 */
    private static final Map<String, String> LOST_ITEM_PLACE = new LinkedHashMap<>();

    static {
        LOST_ITEM_PLACE.put("乾", "西北，或公廨楼阁之所，或金石之旁，或圆器之中，或高亢之地");
        LOST_ITEM_PLACE.put("坤", "西南，或田野之所，或仓廪之处，或稼穑之处，或土窖穴藏之所，或瓦器方器之中");
        LOST_ITEM_PLACE.put("震", "东方，或山林之所，或丛棘之内、钟鼓之旁，或闹市之间，或大途之所");
        LOST_ITEM_PLACE.put("巽", "东南，或山林之所，或寺观之地，或菜蔬之园，或舟车之间，或木器之内");
        LOST_ITEM_PLACE.put("坎", "北方，多藏于水边或溪井沟渠之处，或酒醋之边，或鱼盐之地");
        LOST_ITEM_PLACE.put("离", "南方，或庖厨之间，或炉冶之旁，或在明窗，或遗虚室，或在文书之侧，或在烟火之地");
        LOST_ITEM_PLACE.put("艮", "东北，或山林之内，或近路边，或岩石旁，或藏土穴");
        LOST_ITEM_PLACE.put("兑", "西方，或居泽畔，或败垣破壁之所、废井缺沼之中");
    }

    /**
     * 失物占·以变卦为失物之所在（原著卷之二·失物占第十五 L1127–1128）：「又以变卦为失物之所在。
     * 如变是乾，则觅于西北……（八卦逐定）」。取变卦上下二卦之方并录（变卦为末后之应，其方即物所在）。
     */
    private static String lostItemDetail(String changed) {
        if (changed == null || changed.isBlank()) {
            return "";
        }
        int[] p = pairOf(changed);
        LinkedHashMap<String, String> m = new LinkedHashMap<>();
        for (int t : new int[]{p[0], p[1]}) {
            String tn = TRI_NAME[t];
            String place = loreMap("LOST_ITEM_PLACE", LOST_ITEM_PLACE).get(tn);
            if (place != null) {
                m.putIfAbsent(tn, tn + "（" + place + "）");
            }
        }
        return "以变卦" + changed + "定失物之所在：" + String.join("；", m.values());
    }

    /**
     * 出行占·逐卦行止与所宜（原著卷之二·出行占第十二 L1115）：「又凡出行，体宜乘旺，诸卦宜生体。
     * **体卦乾、震多，主动；坤、艮多，不动。巽宜乘船，离宜陆行。坎防失脱，兑主纷争之应也。**」
     * （另卷之三·诸事响应歌 L1705「坎则乘舟离旱途，乾震动则坤艮止」——两存之，见 C11 口径注。）
     */
    private String travelDetail(String body, String use, String mutual, String changed) {
        List<String> tris = allTrigramsMulti(body, use, mutual, changed);
        if (tris.isEmpty()) {
            return "";
        }
        int dong = countOf(tris, "乾", "震");
        int jing = countOf(tris, "坤", "艮");
        List<String> parts = new ArrayList<>();
        if (dong > jing) {
            parts.add("体卦乾、震多，主动");
        } else if (jing > dong) {
            parts.add("坤、艮多，不动");
        } else if (dong > 0) {
            parts.add("乾震与坤艮相当，动静参半");
        }
        if (tris.contains("巽")) {
            parts.add("巽宜乘船");
        }
        if (tris.contains("离")) {
            parts.add("离宜陆行");
        }
        if (tris.contains("坎")) {
            parts.add("坎防失脱");
        }
        if (tris.contains("兑")) {
            parts.add("兑主纷争之应");
        }
        return parts.isEmpty() ? "" : String.join("；", parts);
    }

    /**
     * 行人占·用卦为行人之盛旺（原著卷之二·行人占第十三 L1122）：「又以用卦为行人之盛旺。逢生，
     * 在外顺快；逢衰，受克，在外灾殃。震多不宁，艮多有阻。坎有险难，兑主纷争之应。」
     * 以用卦（行人）对体之关系断：体生用→用得生助而顺快；体克用→用受制而灾殃。
     */
    private String travelerDetail(String body, String use, String mutual, String changed) {
        List<String> parts = new ArrayList<>();
        if (use != null && !use.isBlank() && body != null && !body.isBlank()) {
            String rel = relationToBody(use, body);
            if ("体生".equals(rel)) {
                parts.add("用卦（行人）逢体之生，在外顺快");
            } else if ("体克".equals(rel)) {
                parts.add("用卦（行人）受体之克而衰，在外灾殃");
            } else {
                parts.add("用卦（行人）气平，行止如常");
            }
        }
        List<String> tris = allTrigramsMulti(body, use, mutual, changed);
        if (countOf(tris, "震") >= 2) {
            parts.add("震多不宁");
        }
        if (countOf(tris, "艮") >= 2) {
            parts.add("艮多有阻");
        }
        if (tris.contains("坎")) {
            parts.add("坎有险难");
        }
        if (tris.contains("兑")) {
            parts.add("兑主纷争之应");
        }
        return parts.isEmpty() ? "" : String.join("；", parts);
    }

    /**
     * 求名占·变卦方道与在任忌克体（原著卷之二·求名占第九 L1106）：「欲知名成之日，生体之卦气详之。
     * 欲知职任之处，变卦之方道决之。若无克体之卦，则名易就…若在任占卜，最忌见克体之卦。如卦有
     * 克体者，即居官见祸，轻则上司责罚，重则剥官退职。」
     */
    private String fameDetail(String body, String use, String mutual, String changed) {
        List<String> parts = new ArrayList<>();
        if (changed != null && !changed.isBlank()) {
            int[] p = pairOf(changed);
            parts.add("变卦" + changed + "之方道决职任之处：" + TRI_NAME[p[0]] + "（" + directionOf(TRI_NAME[p[0]])
                    + "）、" + TRI_NAME[p[1]] + "（" + directionOf(TRI_NAME[p[1]]) + "）");
        }
        List<String> keBody = keBodyTrigrams(body, use, mutual, changed);
        if (keBody.isEmpty()) {
            parts.add("无克体之卦，则名易就");
        } else {
            parts.add("卦有克体之卦（" + String.join("、", keBody) + "），名成有阻；若在任占之最忌此，"
                    + "即居官见祸，轻则上司责罚，重则剥官退职");
        }
        return parts.isEmpty() ? "" : String.join("；", parts);
    }

    /**
     * 求财占·卦气定得财/破财之日（原著卷之二·求财占第十 L1108–1110）：「又若卦中有体克用之卦，
     * 及生体之卦，则有财，此卦气即见财之日。若卦中有克体之卦，及体生用之卦，即破财，此卦气即
     * 破财之日。」
     */
    private static final Map<String, String> WEALTH_DETAIL = new LinkedHashMap<>();

    static {
        WEALTH_DETAIL.put("有财", "有体克用之卦或生体之卦，则有财（此卦气即见财之日）");
        WEALTH_DETAIL.put("破财", "有克体之卦或体生用之卦，即破财（此卦气即破财之日）");
    }

    private String wealthDetail(String body, String use, String mutual, String changed) {
        List<String> tris = allTrigramsMulti(body, use, mutual, changed);
        boolean tiKe = false;
        boolean sheng = false;
        boolean ke = false;
        boolean tiSheng = false;
        for (String t : tris) {
            if (t == null || t.equals(body)) {
                continue;
            }
            String rel = relationToBody(t, body);
            switch (rel) {
                case "体克" -> tiKe = true;
                case "生体" -> sheng = true;
                case "克体" -> ke = true;
                case "体生" -> tiSheng = true;
                default -> { }
            }
        }
        Map<String, String> w = loreMap("WEALTH_DETAIL", WEALTH_DETAIL);
        List<String> parts = new ArrayList<>();
        if (tiKe || sheng) {
            parts.add(w.getOrDefault("有财", ""));
        }
        if (ke || tiSheng) {
            parts.add(w.getOrDefault("破财", ""));
        }
        return parts.isEmpty() ? "" : String.join("；", parts);
    }

    // ==========================================================================
    // 体用总诀·逐卦生体/克体之应（原著卷之二·体用总诀，L1023–1051）
    // ==========================================================================

    /** 逐卦**生体**之应（原著卷之二·体用总诀「又看全卦中有生体之卦，看是何卦」）。 */
    private static final Map<String, String> SHENG_BODY_NOTE = new LinkedHashMap<>();

    /** 逐卦**克体**之应（原著卷之二·体用总诀「又看卦中有克体之卦者，看是何卦」）。 */
    private static final Map<String, String> KE_BODY_NOTE = new LinkedHashMap<>();

    static {
        SHENG_BODY_NOTE.put("乾", "主公门中有喜益，或功名上有喜，或因官有财，或问讼得理，或有金宝之利，或有老人尊长进财、官贵之喜");
        SHENG_BODY_NOTE.put("坤", "主有田土之喜，或于田土进财，或得乡人之财，或得阴人之利，或有果谷布帛之喜");
        SHENG_BODY_NOTE.put("震", "主山林之益，或因山林得财，或进东方之财，或因动中有喜，或有木货交易之利、草木姓氏人称心");
        SHENG_BODY_NOTE.put("巽", "亦主山林之益，或因山林得财，或于东南得财，或因草木人进利，或以茶果得利，或茶果菜蔬馈送之喜");
        SHENG_BODY_NOTE.put("坎", "有北方之喜，或受北方之财，或水边人进利，或因点水人称心，或因鱼盐酒货文书交易之利");
        SHENG_BODY_NOTE.put("离", "主有南方之财，或有文书之喜，或有炉冶场之利，或因火姓人而得财");
        SHENG_BODY_NOTE.put("艮", "有东北方之财，或山田之喜，或因山林田土获财，或宫音带土人之财；物当安稳，事有终始");
        SHENG_BODY_NOTE.put("兑", "有西方之财，或喜悦事，或有食物玉金货利之源，或商音之人、市口之人欣逢，或主宾之乐、朋友讲习之喜");

        KE_BODY_NOTE.put("乾", "主有公事之扰，或门户之扰，或有财宝之失，或于金谷有损，或有怒于尊长，或得罪于贵人");
        KE_BODY_NOTE.put("坤", "主有田土之扰，或于田土有损，或有小人之害，或有阴人之侵，或失布帛之财，或丧谷粟之利");
        KE_BODY_NOTE.put("震", "主有虚惊，常多恐惧，或身心不能安静，或家宅见妖灾，或草木姓氏之人相侵，或于山林有所失");
        KE_BODY_NOTE.put("巽", "亦有草木姓人相害，或于山林上生忧；谋事乃东南方之人，处家忌阴人小口之厄");
        KE_BODY_NOTE.put("坎", "主险陷之事，或寇盗之忧，或失意于水边人，或生灾于酒后，或点水人相害，或北方人见殃");
        KE_BODY_NOTE.put("离", "主文书之扰，或失火之惊，或有南方之忧，或火人相害");
        KE_BODY_NOTE.put("艮", "诸事多迍，百谋中阻；或有山林田土之失，或带土人相侵，防东北方之祸害，或忧坟墓不当安稳");
        KE_BODY_NOTE.put("兑", "不利西方，主口舌事之纷争；或带口人侵欺，或有毁折之患，或因饮食而生忧");
    }

    /**
     * 体用总诀·逐卦生体/克体之应（原著卷之二·体用总诀 L1023–1051）：对**用、互（上下）、变（上下）**
     * 五卦中五行「生体」「克体」者，各录其本卦所属吉凶断语（去重、保序）。
     */
    private String bodyRelationNotes(String body, String use, String mutual, String changed) {
        if (body == null || body.isBlank()) {
            return "";
        }
        LinkedHashMap<String, String> notes = new LinkedHashMap<>();
        List<String> tris = new ArrayList<>();
        if (use != null && !use.isBlank()) {
            tris.add(use);
        }
        for (String h : new String[]{mutual, changed}) {
            if (h != null && !h.isBlank()) {
                int[] p = pairOf(h);
                tris.add(TRI_NAME[p[0]]);
                tris.add(TRI_NAME[p[1]]);
            }
        }
        for (String t : tris) {
            if (t == null || t.isBlank() || t.equals(body)) {
                continue;
            }
            String rel = relationToBody(t, body);
            if ("生体".equals(rel)) {
                String n = loreMap("SHENG_BODY_NOTE", SHENG_BODY_NOTE).get(t);
                if (n != null) {
                    notes.putIfAbsent(t, t + "卦生体（" + n + "）");
                }
            } else if ("克体".equals(rel)) {
                String n = loreMap("KE_BODY_NOTE", KE_BODY_NOTE).get(t);
                if (n != null) {
                    notes.putIfAbsent(t, t + "卦克体（" + n + "）");
                }
            }
        }
        return String.join("；", notes.values());
    }

    /**
     * 占卦诀（原著卷之三·占卦诀 L1583–1586）：
     * <ul>
     *   <li>迟速——「生体是用卦，则事即成就；生体是互卦，则渐成；生体是变卦，则稍稍迟耳」；</li>
     *   <li>阻节——「若有生体之卦，又有克体之卦，则事有阻节……便看克体卦气阻于几日，
     *       若乾克体阻一日、兑克体阻二日之类推之」（阻日依先天卦数：乾1/兑2/离3/震4/巽5/坎6/艮7/坤8）。</li>
     * </ul>
     */
    private String zhanGuaJueNote(String body, String use, String mutual, String changed) {
        if (body == null || body.isBlank()) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        if ("生体".equals(relationToBody(use, body))) {
            parts.add("生体是用卦，则事即成就（应速）");
        } else if ("生体".equals(relationToBodyHex(mutual, body))) {
            parts.add("生体是互卦，则渐成（应中）");
        } else if ("生体".equals(relationToBodyHex(changed, body))) {
            parts.add("生体是变卦，则稍稍迟（应迟）");
        }
        LinkedHashMap<String, Integer> blockers = new LinkedHashMap<>();
        if (use != null && !use.isBlank() && "克体".equals(relationToBody(use, body))) {
            blockers.putIfAbsent(use, triNum(use));
        }
        for (String h : new String[]{mutual, changed}) {
            if (h == null || h.isBlank()) {
                continue;
            }
            int[] p = pairOf(h);
            for (int t : p) {
                String tn = TRI_NAME[t];
                if ("克体".equals(relationToBody(tn, body))) {
                    blockers.putIfAbsent(tn, triNum(tn));
                }
            }
        }
        if (!blockers.isEmpty()) {
            int total = 0;
            StringBuilder who = new StringBuilder();
            for (Map.Entry<String, Integer> e : blockers.entrySet()) {
                total += e.getValue();
                if (who.length() > 0) {
                    who.append('、');
                }
                who.append(e.getKey()).append('(').append(e.getValue()).append("日)");
            }
            parts.add("克体之卦阻节：共" + total + "日（" + who + "）");
        }
        return String.join("；", parts);
    }

    /**
     * 衰旺之诀（原著卷之二·衰旺论 L1393–1397 ＋ 卷之三·体用衰旺之诀 L1606–1607）：
     * 「既明生克，当看衰旺……凡占卜，**体卦宜盛旺。气旺而又逢生则吉，重遇克则凶；
     * 若体衰而逢克，则其凶甚矣；体衰而有生体之卦，则衰稍吉**」「**凡体卦宜乘旺，克体之卦宜衰……
     * 用互变卦，乘旺皆吉，但不要克体之卦气旺。**……故体用之卦，必须详其盛衰也」。
     */
    private String shuaiWangNote(String body, String use, String mutual, String changed, String vitality) {
        if (body == null || body.isBlank()) {
            return "";
        }
        String v = vitality == null ? "" : vitality;
        boolean wang = v.contains("旺") || v.contains("相");
        boolean shuai = v.contains("衰") || v.contains("死") || v.contains("囚") || v.contains("休");
        boolean hasSheng = "生体".equals(relationToBody(use, body))
                || "生体".equals(relationToBodyHex(mutual, body))
                || "生体".equals(relationToBodyHex(changed, body));
        StringBuilder sb = new StringBuilder("衰旺之诀：体卦宜乘旺、克体之卦宜衰");
        if (!v.isBlank()) {
            sb.append("（今体卦").append(v).append("）");
        }
        if (wang) {
            sb.append("——体既乘旺，气旺逢生则吉、虽有他卦克之亦无大害");
        } else if (shuai) {
            sb.append(hasSheng ? "——体虽衰而有生体之卦，则衰稍吉" : "——体衰而逢克，其凶甚矣");
        } else {
            sb.append("——当详体之盛衰：体旺逢生则吉、体衰逢克凶甚、体衰有生体则衰稍吉");
        }
        return sb.append("；用互变乘旺皆吉，惟忌克体之卦气旺（必须详其盛衰而后断）。").toString();
    }

    /**
     * 体用动静之诀（原著卷之二·动静 L1404–1412 ＋ 卷之三·体用动静之诀 L1608–1612）：
     * 「**不动不占，亦不断。其吉凶悔吝，生乎动也。夫体卦为静，互卦为静，用卦变卦则动也**……
     * 动而吉者应吉之速，动而凶者应凶之速；不动而应者，吉凶之未见也」「**坐则应迟，行则应速，
     * 走则愈速，卧则愈迟，立则半迟半速**」。
     */
    private static String dongJingNote(String motion) {
        String m = motion == null ? "" : motion;
        String speed = switch (m) {
            case "行" -> "「行则应速」";
            case "走" -> "「走则愈速」";
            case "坐" -> "「坐则应迟」";
            case "卧" -> "「卧则愈迟」";
            case "立" -> "「立则半迟半速」";
            default -> "";
        };
        return "体用动静之诀：体卦为静、互卦为静，用卦变卦则为动；不动不占、亦不断，吉凶悔吝生乎动——"
                + "动而吉者应吉之速、动而凶者应凶之速；不动而应者，吉凶之未见也"
                + (speed.isEmpty() ? "" : "；" + speed)
                + "（原卷之二·动静／卷之三·体用动静之诀）。";
    }

    /**
     * 占生产·辨男女（原著卷之二·生产占第六 L1094）：「若欲辨其男女，当于前卦审之：
     * **阳卦阳爻多者则生男，阴卦阴爻多者则生女**。阴阳卦爻相生，则察所占左右人之奇偶以证之。」
     * 以前卦（本卦）六爻之阳阴多寡判之。
     */
    private static String birthGenderHint(String name) {
        int[] lines = reconstructLines(name);
        int yang = 0;
        for (int l : lines) {
            if (l == 1) {
                yang++;
            }
        }
        int yin = 6 - yang;
        if (yang > yin) {
            return "阳爻多（阳" + yang + "/阴" + yin + "）→主生男";
        }
        if (yin > yang) {
            return "阴爻多（阳" + yang + "/阴" + yin + "）→主生女";
        }
        return "阴阳爻均（阳" + yang + "/阴" + yin + "）→则察所占左右人之奇偶以证之";
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
        // B54：卦应（卷之三 L1847–2132）——按体/用之卦补引逐卦类象（天文/人物/官贵/疾病/禽兽/器用/财/禄…）。
        String gyBody = guaYingNote(bTri);
        if (!gyBody.isEmpty()) {
            sb.append(gyBody);
        }
        if (!uTri.equals(bTri)) {
            String gyUse = guaYingNote(uTri);
            if (!gyUse.isEmpty()) {
                sb.append(gyUse);
            }
        }
        // B71：八卦所属内外动静之图（卷之一第三张类象表）——按体/用之卦补引内外动静物象。
        String nwBody = neiWaiDongJingNote(bTri);
        if (!nwBody.isEmpty()) {
            sb.append(nwBody);
        }
        if (!uTri.equals(bTri)) {
            String nwUse = neiWaiDongJingNote(uTri);
            if (!nwUse.isEmpty()) {
                sb.append(nwUse);
            }
        }
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
    // 卷之三·占物类断法：器物占 / 体用断物之妙 / 观物趣时诀（B29–B31）
    // ==========================================================================

    /** 占物类起卦法（卷之一·占动物/占静物/物卦起例/形物占/验色占）。 */
    private static boolean isObjectDivination(String method) {
        return switch (method == null ? "" : method) {
            case "占动物", "占静物", "物卦起例", "形物占", "验色占" -> true;
            default -> false;
        };
    }

    /** 字类起卦法（卷之一·字占 / 附录·一撮金法）；此二者可有「相字」取象（卷之四/卷之五）。 */
    private static boolean isCharacterDivination(String method) {
        return switch (method == null ? "" : method) {
            case "字占", "一撮金法" -> true;
            default -> false;
        };
    }

    /**
     * 占物类断法（原著卷之三）：
     * <ul>
     *   <li><b>体用断物之妙</b>（L1755–1756）：「生体者为可食之物；克体者为可近人之秽物。体生者为
     *       不成之器；体克者为破碎损折之物；比和者为有用成器之物。又生体象者为贵物，克体象者为贱物，
     *       所泄为废物也。」</li>
     *   <li><b>器物占</b>（L1451–1454）：「大抵占器物，并不喜见兑卦，盖兑为毁折也。若坎为体，则见兑
     *       无伤。乾卦为体亦无害。其余卦体，逢兑不久即破。木之器物，或震、巽为体，见兑为用，必不禁
     *       耐用矣。破器之日，必申酉与卜年月日也。」</li>
     *   <li><b>观物趣时诀</b>（L1772 / L1778）：「以春得震、离为花，夏得震为有声之物，秋得兑为毁折
     *       成器之物，冬得坤为无用土物也」「凡占物，以春震巽、夏离、秋乾兑、冬坎，皆当以为可用之物、
     *       成器之物。否则为无用之物。」</li>
     *   <li><b>物数为体诀</b>（L1761–1762）：「凡算物数者，不但以体卦为体，凡卦之多者，皆可为体。
     *       如乾金多，以金为体，则多刚；坤多以土为体，多柔……观物观爻，如八卦中阳爻多，乃多刚之物；
     *       阴爻多，乃多柔之物。」</li>
     *   <li><b>观物看变爻为主</b>（L1765–1767）：「凡观物，以变卦为主，应用之应验也。如得乾，初爻
     *       变为巽，金刀削过木之物；二爻动，变为离，乃火中煅炼之金；三爻动，变为兑，乃毁折五金之器，
     *       虽圆而破处多也。」（另有「观物变在五六爻，多是能飞动之物」）</li>
     *   <li><b>观物克应法</b>（L1768–1769）：「凡算物之成败，又看体卦克应如何……见圆物相遇，即断是
     *       圆物。见有负土者过，即断为土中之物。见刚健之物，即言是刚健之物。见有柔腐之物，即断是柔
     *       腐之物。」</li>
     *   <li><b>万物戏念</b>（L1776–1778）：「凡猜手中物，乾金为圆白之物……坎为黑色、性柔、近水之物……
     *       艮为土中之物、瓦石之类……巽、震为竹木……离色赤、性柔……坤为土中之物，色黄而性温。兑为
     *       毁折之物，带口。」</li>
     * </ul>
     */
    private String objectDivinationText(String method, String original, int moving,
                                        String body, String use, String mutual, String changed,
                                        String season, String omen) {
        if (!isObjectDivination(method)) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        boolean hasBody = body != null && !body.isBlank();
        boolean hasUse = use != null && !use.isBlank();
        if (hasBody && hasUse) {
            String rel = relationToBody(use, body);
            String kindKey = switch (rel) {
                case "生体" -> "体用断物:生体";
                case "克体" -> "体用断物:克体";
                case "体生" -> "体用断物:体生";
                case "体克" -> "体用断物:体克";
                default -> "体用断物:default";
            };
            parts.add(loreTpl("OBJECT_DIVINATION", OBJECT_DIVINATION, kindKey));
            boolean dui = "兑".equals(body) || "兑".equals(use);
            if (!dui) {
                parts.add(loreTpl("OBJECT_DIVINATION", OBJECT_DIVINATION, "器物占:兑"));
            } else if ("坎".equals(body) || "乾".equals(body)) {
                parts.add(fillTpl(loreTpl("OBJECT_DIVINATION", OBJECT_DIVINATION, "器物占:坎乾"), "body", body));
            } else if ("震".equals(body) || "巽".equals(body)) {
                parts.add(fillTpl(loreTpl("OBJECT_DIVINATION", OBJECT_DIVINATION, "器物占:震巽"), "body", body));
            } else {
                parts.add(loreTpl("OBJECT_DIVINATION", OBJECT_DIVINATION, "器物占:default"));
            }
        }
        // B34：物数为体诀（卷之三 L1761–1762）——「凡卦之多者，皆可为体」；以全卦（体/用/互/变）
        //      五行多寡定所体之五行，以本卦阴阳爻多寡定物之刚柔（阳多则刚、阴多则柔）。
        List<String> allTri = allTrigramsMulti(body, use, mutual, changed);
        if (!allTri.isEmpty()) {
            LinkedHashMap<String, Integer> fiveCnt = new LinkedHashMap<>();
            for (String t : allTri) {
                if (t != null && !t.isBlank()) {
                    fiveCnt.merge(fivePhaseOf(t), 1, Integer::sum);
                }
            }
            StringBuilder fc = new StringBuilder();
            int max = 0;
            String major = null;
            for (Map.Entry<String, Integer> e : fiveCnt.entrySet()) {
                if (fc.length() > 0) {
                    fc.append("、");
                }
                fc.append(e.getKey()).append(e.getValue());
                if (e.getValue() > max) {
                    max = e.getValue();
                    major = e.getKey();
                }
            }
            int[] yy = yinYangCount(original);
            String gangRou = yy[0] > yy[1] ? "阳爻多（" + yy[0] + "阳" + yy[1] + "阴）→多刚之物"
                    : yy[1] > yy[0] ? "阴爻多（" + yy[0] + "阳" + yy[1] + "阴）→多柔之物"
                    : "阴阳各半（" + yy[0] + "阳" + yy[1] + "阴）→刚柔相济之物";
            parts.add(fillTpl(loreTpl("OBJECT_DIVINATION", OBJECT_DIVINATION, "物数为体诀:base"),
                    "fc", fc.toString(), "major", major, "gangRou", gangRou));
        }
        // B35：观物看变爻为主（卷之三 L1765–1767）——「凡观物，以变卦为主，应用之应验也」。
        if (changed != null && !changed.isBlank()) {
            String s = fillTpl(loreTpl("OBJECT_DIVINATION", OBJECT_DIVINATION, "观物看变爻:base"),
                    "original", original, "changed", changed);
            if (moving >= 5) {
                s += loreTpl("OBJECT_DIVINATION", OBJECT_DIVINATION, "观物看变爻:56");
            }
            parts.add(s);
        }
        // B36：观物克应法（卷之三 L1768–1769）——「凡算物之成败，又看体卦克应如何」。
        if (omen != null && !omen.isBlank()) {
            String s = objectOmenVerdict(omen);
            if (!s.isEmpty()) {
                parts.add(fillTpl(loreTpl("OBJECT_DIVINATION", OBJECT_DIVINATION, "观物克应:prefix"), "verdict", s));
            }
        }
        // B37：万物戏念（卷之三 L1776–1778）——逐卦「手中物」之象（色/性/类）。
        String playTri = use != null && !use.isBlank() ? use : body;
        if (playTri != null && !playTri.isBlank() && loreMap("OBJECT_PLAY_NOTE", OBJECT_PLAY_NOTE).containsKey(playTri)) {
            parts.add("万物戏念（" + playTri + "）：" + loreMap("OBJECT_PLAY_NOTE", OBJECT_PLAY_NOTE).get(playTri));
        }
        // B53：观物玄妙歌诀（卷之三 L1656–1667）——逐卦物之形色性位体，兼交互生克（见某卦为某物）。
        if (hasBody && loreMap("OBJECT_MIAO_JUE", OBJECT_MIAO_JUE).containsKey(body)) {
            parts.add("观物玄妙歌诀（" + body + "）：" + loreMap("OBJECT_MIAO_JUE", OBJECT_MIAO_JUE).get(body));
        }
        if (season != null && !season.isBlank()) {
            String seasonKey = switch (season) {
                case "春" -> "观物趣时:春";
                case "夏" -> "观物趣时:夏";
                case "秋" -> "观物趣时:秋";
                case "冬" -> "观物趣时:冬";
                default -> null;
            };
            if (seasonKey != null) {
                String t = loreTpl("OBJECT_DIVINATION", OBJECT_DIVINATION, seasonKey);
                if (!t.isEmpty()) {
                    parts.add(t);
                }
            }
        }
        return parts.isEmpty() ? "" : "占物类断法——" + String.join("；", parts) + "。";
    }

    /** 占物类断法模板（批次6）：体用断物/器物占/物数为体诀/观物看变爻/观物克应/观物趣时，
     *  固定诀文入本体 domain_lore，运行期计算以 {占位符} 填充。 */
    private static final Map<String, String> OBJECT_DIVINATION = new LinkedHashMap<>();

    static {
        OBJECT_DIVINATION.put("体用断物:生体", "体用断物之妙：生体者为可食之物（生体象者为贵物）");
        OBJECT_DIVINATION.put("体用断物:克体", "体用断物之妙：克体者为可近人之秽物（克体象者为贱物）");
        OBJECT_DIVINATION.put("体用断物:体生", "体用断物之妙：体生者为不成之器（所泄为废物）");
        OBJECT_DIVINATION.put("体用断物:体克", "体用断物之妙：体克者为破碎损折之物");
        OBJECT_DIVINATION.put("体用断物:default", "体用断物之妙：比和者为有用成器之物");
        OBJECT_DIVINATION.put("器物占:兑", "器物占：卦中不见兑，器中无毁折之虞");
        OBJECT_DIVINATION.put("器物占:坎乾", "器物占：兑虽为毁折，然{body}为体见兑无伤");
        OBJECT_DIVINATION.put("器物占:震巽", "器物占：木器（{body}为体）见兑为用必不禁用，破器之日多在申酉");
        OBJECT_DIVINATION.put("器物占:default", "器物占：见兑则器不久即破，破器之日多在申酉");
        OBJECT_DIVINATION.put("物数为体诀:base", "物数为体诀：凡卦之多者皆可为体（{fc}），以「{major}」为体（金多以金为体则多刚、土多以土为体多柔）；{gangRou}；体互变并无生旺之气者，为不入五行之物");
        OBJECT_DIVINATION.put("观物看变爻:base", "观物看变爻为主：凡观物，以变卦为主，应用之应验也（本卦{original}→变卦{changed}）。如得乾，初爻变为巽，金刀削过木之物。二爻动，变为离，乃火中煅炼之金。三爻动，变为兑，乃毁折五金之器，虽圆而破处多也。");
        OBJECT_DIVINATION.put("观物看变爻:56", "；观物变在五、六爻者，多是能飞动之物");
        OBJECT_DIVINATION.put("观物克应:prefix", "观物克应：{verdict}");
        OBJECT_DIVINATION.put("观物趣时:春", "观物趣时：春得震、离为花（春震巽为可用成器之物）；余则无用之物");
        OBJECT_DIVINATION.put("观物趣时:夏", "观物趣时：夏得震为有声之物（夏离为可用成器之物）；余则无用之物");
        OBJECT_DIVINATION.put("观物趣时:秋", "观物趣时：秋得兑为毁折成器之物（秋乾兑为可用成器之物）；余则无用之物");
        OBJECT_DIVINATION.put("观物趣时:冬", "观物趣时：冬得坤为无用土物（冬坎为可用成器之物）；余则无用之物");
    }

    /** 万物戏念（卷之三 L1776–1778）：逐卦「手中物」之象（色/性/类）。 */
    private static final Map<String, String> OBJECT_PLAY_NOTE = new LinkedHashMap<>();

    static {
        OBJECT_PLAY_NOTE.put("乾", "金为圆白之物，其色白、其性刚，为宝货之物；有气为无价物");
        OBJECT_PLAY_NOTE.put("坎", "黑色、性柔，近水之物");
        OBJECT_PLAY_NOTE.put("艮", "土中之物，瓦石之类，有气为成器之物，其色黄；逢兑克、无气，折伤之物");
        OBJECT_PLAY_NOTE.put("巽", "竹木，有气为有用之物、为可食之物，无气为竹木之属；遇兑之属可食、当时之果物、色青；有气柔、无气刚");
        OBJECT_PLAY_NOTE.put("震", "竹木，有气为有用之物、为可食之物；遇坎为污湿物，无气为烂朽之木");
        OBJECT_PLAY_NOTE.put("离", "色赤、性柔，有水有木而火焚之，必炭之类；有气为价值可货之物");
        OBJECT_PLAY_NOTE.put("坤", "土中之物，色黄而性温");
        OBJECT_PLAY_NOTE.put("兑", "毁折之物，带口");
    }

    /** 观物玄妙歌诀（卷之三 L1656–1667）：逐卦物之形色性位体，兼交互生克（见某卦为某物）。
     *  原文序列为 乾/坤/震/巽/坎/离/兑，然全篇独缺「艮」；而标作「离之为卦也」一段所述
     *  （山石、土瓦、小石于大山、为门途之处）实为艮象，且「其性则上刚下柔」正合艮☶（一阳在上），
     *  当系原刻讹「离」为「艮」，故系于艮；离本卦之歌诀此本阙（宁阙勿滥，不强补）。 */
    private static final Map<String, String> OBJECT_MIAO_JUE = new LinkedHashMap<>();

    static {
        OBJECT_MIAO_JUE.put("乾", "刚而圆、贵而坚，为金为玉、为赤为圜、为大为首、为木之果；"
                + "物见兑为毁折、逢坎而沉溺、见离为炼煅之金、震为有动之物、巽为木果为圆，"
                + "坤艮土中之石得火而成器；兑为剑锋之锐，秋得而价高、夏得之而衰矣");
        OBJECT_MIAO_JUE.put("坤", "其形直而方，其色黑而黄；为文为布、为舆为金，其物象牛，其性恶动。"
                + "得乾乃可圆可方、可贵可贱；震巽为长器，离为文章，兑为土中出之金，艮为带刚之土石也");
        OBJECT_MIAO_JUE.put("震", "其色玄黄而多青；为木为声、为竹为萑苇、为蕃鲜及生形。上柔下刚，是性震动而可惊；"
                + "得乾乃为声价之物，得兑为无用之木，见艮山林间之石，见坎有气之类，"
                + "巽为有枝叶，见离为带花");
        OBJECT_MIAO_JUE.put("巽", "其色白，其气香；为草木，为刚为柔。见离为文书，见兑乾为不用（乃遇金刀之物）；"
                + "坤艮为草木之类，坎兑为可食之物。为长为直，并震而春生夏长，草木之果蔬");
        OBJECT_MIAO_JUE.put("坎", "其色黑，亦可圆可方物；为柔为腐，内则刚物。得之卑湿之所，多为水中之物。"
                + "见乾亦圆，见兑亦毁（又乃污湿）；得震巽而可食，离水火既济、假水而出、假火而成，"
                + "又为滞于物，兑为带口也，震巽为带枝叶、为带花也");
        OBJECT_MIAO_JUE.put("艮", "其色黄而青，体燥，其性则上刚下柔；为山石之物、土瓦之类、小石于大山，为门途之处。"
                + "为物见乾而刚，兑而毁折，坤而土块，巽为草之物，而震为木物类也；"
                + "坎并为河岸之物，离并为瓦器，震巽并见篱壁之物");
        OBJECT_MIAO_JUE.put("兑", "其色白，其性少柔而多刚；为毁折而下，全带口而圆。见乾先圆后缺，见艮则金石废器，"
                + "见震为剥削之物，见坎为水之类。得乾而多刚，得坤而多柔；"
                + "长于西泽之内、于水中之类，得柔而成器也");
    }

    /** 卦应（卷之三 L1847–2132）：逐卦之天文/天气/地理方所/人物/凶盗/官贵/身体生育/性情/婚姻/
     *  声音/信音事意/疾病/食物谷果/禽兽/器用衣物/财/禄/字/色/策/轨等类象（与「八卦属类」互参）。
     *  「禄/字/策/轨」为占卜外数，照录备考（第 34 轮据底本补全：乾·战吉/天威所发、震·缠带、
     *  巽·手足厥会和之气候三十日/下卦为鹅鸭/上卦飞下卦走/那免、离·手足二君太阳明三相火、
     *  兑·上为雨下为露；策/轨原用阿拉伯数字，已改从底本汉字数）。 */
    // 【本体优先·内置兜底】卦应逐卦类象已迁 sym:ImageSymbol + gua_ying 表 + OBDA 映射；此表仅本体/DB 不可用时兜底。
    private static final Map<String, String> GUA_YING = new LinkedHashMap<>();

    static {
        GUA_YING.put("乾", "天文雪、老阳；天气寒；凶盗军弓手贼强横停尸；官贵朝贵盐司太守座主；"
                + "身体顶面颊辅；性情刚健正直尊重好高战吉；声音正清商；信音朝信改召命荐举关升义亲；"
                + "事意上卦为形象之家下卦为强横之辈；疾病手太阳脉弦紧、天威所发、上壅目热、寒热；附药丸子；"
                + "食物饼子之赤者手饼馒头荷包羹猪头脑骨头珍粉馄钝；谷果粟栗瓜豆龙眼荔；禽兽雀鹇鹗鹏鹰；"
                + "衣服赤玄色；器用圆物盏注子盘水晶玉环定器球；财恩义交货钱马之类；禄壬申；"
                + "字方圆形字有头者须旁八卦；策二百一十六；轨七百六十八");
        GUA_YING.put("坤", "天文雾露云阴；地理郡国宫阙城邑墙壁；人物母妻儒农僧；凶盗奴婢藏在僻处；"
                + "官贵大臣教官考校文字；生育女肥厚；性情顺缓不信事顽钝无慈爱；声音宫音；"
                + "事意迟滞顽懦悭吝从容；疾病手、太阴侯、腹痛、脾胃闭、脉沉伏；"
                + "饮食藜羹烧熬冻物鹅鸭肺太牢饮食饴糖；五味苦辣甘；果品取物件；"
                + "音信顺遂可许为捷应、辰戌丑未月日；财物束修抄题僧衣布裳；"
                + "婚姻富家庄家商家丑拙性吝大腹壮迟钝面黄；器用轿车瓦器田具沙器；禽兽牛牝马鸥雀鸦鸽；"
                + "字圭金四牛旁；禄癸酉；策一百四十四；轨六百七十一");
        GUA_YING.put("震", "天文雷虹霓电；地理屋市宅门户枋；方所正东；人物商旅将帅工匠；凶盗东去男人盗；"
                + "官贵监司郡守刑幕巡检法官；生育长男转动虚惊怪异；性情始刚故决断、急于动故躁；"
                + "婚姻官宦家技巧工、女容心神好、动静易转；声音上下角、上平声、三音七声；信音所许不至；"
                + "事意旧事重叠有名无实；病疾气积冷伤胃、四体劳倦、温冷伤食；药足太阳脉洪浮；"
                + "宴会酒会玩赏期集；食物面食包子酒时新之物；谷果芋小豆稼时新之果；禽兽蜂蝶白鹭鹤；"
                + "器用木器盘竹器筐算盘舟车兵车轿器皿瓶盏瓯乐器鼓；衣物裙腰带缠带；缠带绳匹帛青玄黄之；财阴人取索竹木钱；"
                + "禄庚；字走竹旁立画偏；色青玄黄；策一百六十八；轨七百四");
        GUA_YING.put("巽", "天文风；地理林苑园囿；人物命妇药婆工术女；凶盗奴婢商量取去宜急求之；"
                + "官贵典狱考校干官休究；身体耳目胆发命口肢；生育长女胎月少莹白；"
                + "性情鄙野悭吝艰苦号咷；婚姻命妇宗室女委望进退；声音角音、角仄声、三声四声上下；"
                + "信音召命报捷辟差举状；事意荐举呈发申审号令听命；"
                + "疾病手足厥会和之气候三十日、脉濡弱、饮食伤胃、宿酒、痞膈、为臭、水谷不化；药草药；宴会家筵客不齐；"
                + "谷果麻粉茶；食物长面粉羹脍鸡鱼肠肚酸物、下卦为鹅鸭；器用竹木草具绳丝弦索乐器；"
                + "禽兽鸡鹅鸭鱼善鸣之虫禽、上卦飞下卦走；衣物衣绳丝；色青绿碧白紫色；财利市喜羹、租钱、料钱、那免；禄辛；"
                + "字草木竹旁、丝、鱼、菜、舟、齿足疾、大豆、辣；策百九十二；轨七百三十六");
        GUA_YING.put("坎", "天文月虹云霜；地理海阔水泉沟渎厕；方所正北、丘墓中、狐兔穴中；人物僧道；"
                + "凶盗乘便而来、脱头露尾、易败必获；官贵漕运钱粮漕官运属；身体发膏血；"
                + "生产难产、中男、清秀；性情心机阴险、智随圆、委曲；婚姻富家酒家亲家用性；"
                + "声音羽中上卦、羽平六声下卦；信音反复犹豫、小人欺诈、佞、狡狯、盗贼、狱讼；"
                + "疾病足太阴之气、脉滑芤；附药补肾药、或酒水下；食物酒咸物豕鱼海味中硬而核腰子；"
                + "谷果麦枣梅李桃、外柔内坚、有核；禽兽鹿豕象豚狐燕螺；器用酒器车轮败车；衣物青黑色；"
                + "财争讼之财和合打偏财；字两头点水、全水、月、小弓之属；禄戌；色黑皂白；策百六十八；轨七百零四");
        GUA_YING.put("离", "天文日霞电晴；地理殿堂中堂檐厨灶；方所正南；人物为将帅兵戈甲之士；"
                + "凶盗妇人盗、从南方去；官贵翰苑教官通判、任官南方；身体三焦小肠目心；"
                + "生育次女、多性燥啼哭；性情聪明、见事明了；信音朝信、文书、报捷、契券；"
                + "事意忧疑、聒拓、喧哄、性急、虚忧；疾病手足二君太阳明三相火、眼病、气燥、热疾、发狂；"
                + "禽兽凤有文彩、鳖螺蚌蟹螯蛤蠃、鹑鹤飞鸟、牝羊；食物馄饨蟹鳖蚌、介虫之属、中虚物、炙煎物；"
                + "谷果谷实粱藕、外坚内柔之物、棘木之花叶枯枝；"
                + "器用灯火之具、外坚内柔之物、屏幕帘旗帜戈兵甲胄、盘甑瓶缴壁一应中虚之物、窑灶炉冶盒子瓮笼；"
                + "衣物赤红紫色；财远旧取索、意外之物；字火日旁；禄巳；策百九十二；轨七百六十三");
        GUA_YING.put("艮", "天文星烟；地理山径墙巷丘园门墙阑阍寺宗庙；方所东北方、艮门墙、寺；"
                + "人物阍寺仆隶官僚保人；凶盗以下所使警迹人；官贵山郡无迁转；身体手指鼻肋脾胃；"
                + "生产损胎次男；性情濡滞多疑、优游、内刚中软；声音清上平、一音、十二音、三声；"
                + "事意反复进退、去就多疑；疾病手太阳、久患脾胃、股疾、脉沉伏；附药湿土石药；"
                + "宴会常酣期集；谷果豆大小粟；食物妆点之物、所食不一、酒浆、杂蒸之物、冻物、杂羹、"
                + "有汁物、鸭鹅、甘味；禽兽牝牛子母牛、鹄鹘鸦鹊雀鹜鸥、鼠；"
                + "器用轿舆犁具兵甲器陶冶瓦器锅釜瓶瓮簋伞钱袋磁器踏镫螺蜔盒子、内柔外刚之物；"
                + "衣物黄裳僧衣黑皂彩帛袋布；禄丙；财旧钱、置转货买田土、趁钱；字土牛田旁；策百六十八；轨七百零四");
        GUA_YING.put("兑", "天文雨露、春雾细雨、夏秋重雾、冬大雪、上为雨下为露；地理井泉泗泽；方所西方；"
                + "人物先生客人巫匠媒人牙人少女妾娼；官贵学官将帅县令考校乐友、赴任西方；"
                + "凶盗家使僮仆藏于僻地；身体口肺膀胱大肠辅颊舌太阳；生育少女一胎、月不足、多奇异；"
                + "性情喜悦口舌多美；声音商上、下商之浊四声；婚姻平常之家、少女媚悦；信音喜酉丑时日至；"
                + "事意唇吻、口舌、谗谤相欺、争打妇人、暗昧；疾病口痛、唇齿、咽喉、危困；附药剉剂；"
                + "宴会讲书会友请先生吟赏；食物包子、有口舌物、糖饼烧饼肝肺；谷果栗黍枣李胡桃石榴；"
                + "禽兽羔羊鹿猿虎豹豺鹭鱼；器用席铁铜钱器皿酒盏瓶瓯、有口器或损缺；衣物彩；"
                + "财束修含水；禄丁；字家金钩口旁；色素白；策一百九十二；轨七百三十六");
    }

    /** 卦应·逐卦类象（卷之三 L1847–2132），用于万物类象按体/用之卦补引。 */
    private static String guaYingNote(final String tri) {
        Map<String, String> src = (ontoGuaYing != null && !ontoGuaYing.isEmpty()) ? ontoGuaYing : GUA_YING;
        if (tri == null || tri.isBlank() || !src.containsKey(tri)) {
            return "";
        }
        return "卦应（" + tri + "）：" + src.get(tri) + "。";
    }

    // ------------------------------------------------------------------
    // 卷之一·八卦所属内外动静之图（第 27 轮补录 B71）
    //   原著卷之一第三张类象表（八卦万物属类／八卦万物类占 之外）；
    //   逐卦列「内外动静」物象，供节点 9 万物类象按体/用之卦补引。
    // ------------------------------------------------------------------
    /** 键＝经卦，值＝内外动静物象（照录原著）。 */
    private static final Map<String, String> NEI_WAI_DONG_JING = new LinkedHashMap<>();
    static {
        NEI_WAI_DONG_JING.put("乾", "玄黄、大赤色、金玉、宝珠、镜、狮、圆物、木果、贵物、冠、象、马、天鹅、刚物");
        NEI_WAI_DONG_JING.put("坎", "水带子、带核之物、豕、鱼、弓轮、水具、水中之物、盐、黑色、酒");
        NEI_WAI_DONG_JING.put("艮", "土石、黄色、虎、狗、土中之物、瓜果、百禽、鼠、黔喙之物");
        NEI_WAI_DONG_JING.put("震", "竹木、青绿碧色、龙、蛇、萑苇、竹木乐器、草、蕃鲜之物");
        NEI_WAI_DONG_JING.put("巽", "木、蛇、长物、青碧绿、山木之禽鸟、香、鸡、直物、竹木之器、工巧之器");
        NEI_WAI_DONG_JING.put("离", "火、文书、干戈、雉、龟、蟹、槁木、甲胄、螺、蚌、鳖、物赤色");
        NEI_WAI_DONG_JING.put("坤", "土、万物、五谷、柔物、丝绵、百禽、牛、布帛、舆、釜、瓦器、黄色");
        NEI_WAI_DONG_JING.put("兑", "金刃、金器、乐器、泽中之物、白色、有口缺之物、羊");
    }

    /** 八卦所属内外动静之图·某经卦之内外动静物象（卷之一 L1073–1157）。 */
    private static String neiWaiDongJingNote(final String tri) {
        if (tri == null || tri.isBlank() || !loreMap("NEI_WAI_DONG_JING", NEI_WAI_DONG_JING).containsKey(tri)) {
            return "";
        }
        return "内外动静（" + tri + "）：" + loreMap("NEI_WAI_DONG_JING", NEI_WAI_DONG_JING).get(tri) + "。";
    }

    // ------------------------------------------------------------------
    // 卷之三·观梅数诀序（第 27 轮补录 B72）
    //   序文含邵子先天八卦数（乾一兑二离三震四巽五坎六艮七坤八）与「易有太极…」之引；
    //   次列「八卦定阴阳次序」（乾坤六子，已成 TRI_ROLE）、「相生极美比和次之」、
    //   「体用于变爻作动静取之」诸则；末之「此是某几卦」为原刻图象残注（漫漶难辨）存目。
    // ------------------------------------------------------------------
    private static String GUAN_MEI_SHU_JUE_SEQ =
        "嗟乎，《易》岂易言哉！盖《易》之为书，至精微，至玄妙。然数者，不外乎易理也。有先天后天之殊，"
      + "有叶音取音之辨，明忧虞得失之机，取互变迟速之应。数有前定，祸福难测。易理灼然可察，予求得《先天》、"
      + "《玄黄》、《灵应》诸篇，外采《易》辞曰：观梅数诀，列图明五行生克衰旺之理，分例指避凶趋吉之道。"
      + "后学君子幸鉴焉。《易》辞曰：“易有太极，是生两仪，两仪生四象，四象生八卦，八卦生万物。”"
      + "邵子曰：“一分为二，二分为四，四分为八也。”《说卦传》曰：“易逆数也。”邵子曰：“乾一、兑二、离三、"
      + "震四、巽五、坎六、艮七、坤八，自乾至坤，皆得未生之卦，若逆推四时之比也。后天六十四卦仿此。”";

    /** 卷之三·观梅数诀序（序文义理＋先天八卦数；「此是某几卦」原刻图象漫漶存目）。 */
    private static String guanMeiShuJueNote() {
        return "观梅数诀序（卷之三）——" + GUAN_MEI_SHU_JUE_SEQ
             + "〔原刻「八卦定阴阳次序」「此是某几卦」诸图象漫漶，照实存目。〕";
    }

    // ------------------------------------------------------------------
    // 卷之二·三要灵应篇序（第 27 轮补录 B73）
    //   清灵子朱虚（宝庆四年）序；论三要之妙在运耳目心三者之虚灵、求诸吾心易之妙。
    //   三要灵应篇（断法）见 SAN_YAO_LING_YING；此录其序以全篇首。
    // ------------------------------------------------------------------
    private static String SAN_YAO_XU =
        "夫《易》者，性理之学也。性理，具于人心者也。当其方寸湛然，灵台皎洁，无一毫之干，无一尘之累，"
      + "斯时也，性理具在而《易》存吾心，浑然是《易》也，其先天之《易》也。及夫虑端一起，事根忽萌，"
      + "物之著心，如云之蔽空，如尘之蒙镜，斯时也，汨没茫昧，而向之《易》存吾心者，泯焉尔。故三要之妙，"
      + "在于运耳、目、心三者之虚灵，俾应于事物也。耳之聪，目之明，吾心实总乎聪明。盖事根于心，心该乎事。"
      + "然事之未萌也，虽鬼神莫测其端，而吉凶祸福，无门可入。故先师曰：“思虑未动，鬼神未知，不由乎我，"
      + "更由乎谁？”若夫事萌于心，鬼神知之矣。吉凶悔吝有其数，然吾预之知何道与？必曰：求诸吾心易之妙而已矣。"
      + "于是寂然不动，静虑诚存，观变玩占，运乎三要。必使视之不见者，吾见之；听之不闻者，吾闻之；"
      + "如形之见示，如音之见告，吾之了然鉴之。则《易》之为卜筮之道，而《易》在吾心矣。三要不虚，"
      + "而灵应之妙斯得也。是道也，寓至精至神之理，百姓日用而不知，安得圆通三昧者与之论？"
      + "此先师刘先生（江浙人，号湛然子）得之王屋山人高处士云岩。宝庆四年仲夏既望　　清灵子朱虚拜首序";

    /** 卷之二·三要灵应篇序（序文照录，接入节点 11 参外应）。 */
    private static String sanYaoXuNote() {
        return "三要灵应篇序——" + SAN_YAO_XU;
    }

    // ------------------------------------------------------------------
    // 卷之二·三要灵应篇·正文照录（第 36 轮补录）
    //   第二十轮（B62）仅落本篇「类应之目」，而正文本身（开篇定义、编纂源流、天高地厚之论、
    //   各类引言/结语、篇末总论、篇跋、诫语）未录——是为「类目已落≠通篇已落」之戒。此照录
    //   全篇正文备考（惟「拆字之应」一段依 C12「相字别系不纳」之裁剔除，故断为二段）。
    //   兆象之断法索引另见 SAN_YAO_LING_YING（map）。
    // ------------------------------------------------------------------
    private static String SAN_YAO_LING_YING_GAILUN =
        "三要者，运耳、目、心三者之要也。灵应者，灵妙而应验也。夫耳之于听，目之于视，心之于思，三者为人一"
      + "身之要，而万物之理不出于视听之外。占决之际，寂闻澄虑，静观万物，而听其音，知吉凶，见其形，知善恶"
      + "，察其理，知祸福，皆可为占卜之验。如谷之应声，如影之随形，灼然可见也。其理出于用《易》“远取诸物"
      + "，近取诸身”之法。是编则出于先贤先师，采世俗之语为之例用之者：鬼谷子、严君平、东方朔、诸葛孔明、"
      + "郭璞、管辂、李淳风、袁天罡、皇甫真人、麻衣仙、陈希夷；继而得者：邵康节、邵伯温、刘伯温、牛思晦、"
      + "牛思继、高处士、刘湛然、富寿子、泰然子、朱清灵子。其年代相传不一，而不知其姓名者不与焉。原夫天高"
      + "地厚，万物散殊，阴浊阳清，五气顺布，祸福莫逃乎数，吉凶皆有其机。人为万物之灵，心乃一身之主，目寓"
      + "而为形于色，耳得而为音于声，三要总之，万物备矣。右乃天地万物之灵，而耳、目、心三者之要，故曰三要"
      + "灵应也。是以遇吉兆而顺有吉，见凶谶而不免乎凶。物之圆者事成，缺者事败。此理断然，夫复何疑？右乃占"
      + "物克应，见吉则吉，遇凶则凶。是以云开见日，事必增辉；烟雾障空，物当失色。忽颠风而飘荡，遇震雷以虚"
      + "惊。月忽当面，宜近清光。雨乍沾衣，可蒙恩泽。右乃仰观天文，以验人事。重山为阻隔之际，重泽为浸润之"
      + "深。水流而事通，土积而事滞。石乃坚心始得，沙乃放手即开。浪激主波涛之惊，坡崩主田土之失。旱沼之旁"
      + "，心力俱竭，枯林之下，相貌皆衰。右乃俯察地理，以验人事。适逢人品之来，实为事体之应。故荣宦显官，"
      + "宜见其贵；富商巨贾，可问乎财。儿童哭泣忧子孙，吏卒叫嚣忌官讼。二男二女，重婚之义；一僧一道，独处"
      + "之端。妇人笑语，则阴喜相逢；女子牵连，则阴私见累。匠氏，主门庭改换；宰夫，则骨肉分离。逢猎者，得"
      + "野外之财；见渔者，有水边之利。见衽妇，则事萌于内；遇丧者，则虑根于心。右乃人品之应，以验人事。至"
      + "于摇手而莫为，或掉头而不肯，拭目而喷嚏者方泣，搔首而弹垢者有忧。足动者有行，交臂者有失。屈指者多"
      + "阻节，嘘气者主悲忧。舌出掉者有是非，背相向者防闪失。偶攘臂者，争夺乃得；偶下膝者，屈抑而求。右乃"
      + "近取诸身之应。若逢童子授书，有词讼之端；主翁笞仆，防责罚之事。讲论经史，事体徒间于虚说；语歌词曲"
      + "，谋为转见于悠扬。见博赌，主争斗之财；遇题写，主文书之事。偶携物者，受人提携；适挽手者，遇事牵连"
      + "。右乃人事之应。及夫舟楫在水，凭其接引而行；车马登途，藉之负戴而往。张弓挟矢者，必领荐；有箭无弓"
      + "者，未可试。持刀执刃，须求快利之方；披甲操戈，可断刚强之柄。缫丝者，事务繁冗；围棋者，眼目众多。"
      + "妆花刻果，终非结实之因。画影描形，皆为妆点之类。络绎将成，可以问职。笔墨俱在，可以求文。偶倾盖者"
      + "，主退权。忽临镜者，可赴诏。抱贵器者，有非常之用。负大木者，有不小之财。升斗宜量料而前，尺剪可裁"
      + "度以用。见蹴鞠，有人拨剔。开锁钥，遇事疏通。逢补器，终久难坚。值磨镜，再成始得。顽斧磨钢者，迟钝"
      + "得利。快刀砍木者，利事伤材。裁衣服者，破后方成。造瓦器者，成后乃破。奕棋者，取之以计。张网者，摸"
      + "之以空。或持斧锯恐有伤，或涤壶觞恐有饮。或挥扇者，有相招之义。或污衣者，防谋害之侵。右乃器物之应"
      + "，即远取诸物之义。虽云草木之无情，亦与卜筮而又应。故芝兰为物之瑞，松柏为寿之坚。遇椿桧，则岁久年"
      + "深；遇菌菰，则朝生暮死。占病占产，得之即死之兆。枝叶飘零当萎谢，根核流落主牵连。奇葩端的虚花，嘉"
      + "果可以结实。右乃草木之应。至于飞走，最有祯祥。故乌鸦报灾，蟢虫报喜。鸿雁主朋友之信，蛇虺防毒害之"
      + "谋。鼠啮衣，有小口之灾；雀噪檐，有远行之至。犬斗恐招盗贼，鸡斗主有喧争。牵羊者，喜庆将临；骑马者"
      + "，出入皆利。猿猴攀木，身心不定；鲤鱼出水，变化不凡。绳拴马，疾病难安；架陷禽，因人未脱。右乃禽兽"
      + "之应。酒乃忘忧之物，药乃祛病之方。故酒樽忽破，乐极生悲；良医道逢，难中有救。藤萝之类堪依倚，虎豹"
      + "之象可施威。耕田锄地者，事势必翻。破竹剖竿者，事势必顺。春花秋月，虽无实而阚景，夏绵冬葛，虽有用"
      + "而背时。凉扇，多主气捐；晴伞，渐逢闲废。泡影电光，虚幻难信；蛛丝蚕茧，巧计方成。右乃杂见观物之应"
      + "。";   // L2321–L2539（1681 字）

    private static String SAN_YAO_LING_YING_ZONGLUN =
        "复指物名，以叶音义。如见鹿可以问禄，见蜂可以言封。梨主分离，桃主逃走。见李则问讼得理，逢冠则问名"
      + "得官。鞋为百事和谐，榼为诸事可合。难以详备，在于变通。右即物叶音之义。及夫在我之身，实为彼事之应"
      + "。故我心忧者，彼事亦忧；我心乐者，彼事亦乐。我适闲，彼当从容；我值忙，彼当窘迫。右即自己之应，近"
      + "取诸身之义。欲究观人之道，须详系《易》之辞。将叛者，其辞惭；将疑者，其辞支。吉人之辞寡，躁人之辞"
      + "多。诬善之人，其辞游；失其守者，其辞屈。右一动一静之应，近取诸身之义。又推五行，须详八卦。卦吉而"
      + "应吉终吉，卦凶而应凶终凶。卦应一吉一凶，事体半吉半凶。明生克之理，察动静之机，事事相关，物物相应"
      + "，此五行八卦及克应动静之理。活法更存乎方寸，玄机又在于师传。纵万象之纷纭，惟一理而融贯。务要相机"
      + "而发，须要临事而详。右言占卜之理在人变通之妙。嗟夫！方朔覆射，知事物之隐微。诸葛马前，定吉凶于顷"
      + "刻。皇甫坐端之妙，淳风鸟觉之占，虽所用之有殊，诚此理之无异。右言三要灵应妙处。可以契鬼神之妙，可"
      + "以会蓍龟之灵。然人非三世，莫能造其玄；心非七窍，莫能悟其奥。故得其说者，宜秘；非其人者，莫传。轻"
      + "泄天机，重遭阴谴。造之深，可以入道。用之久，可以通神。右言灵应之妙，不可轻传妄授，宜秘之重之，以"
      + "重斯道也。";   // L2561–L2657（533 字）

    /** 卷之二·三要灵应篇·正文导论与类引言/结语（照录；接入节点 11，在各类之应之前）。 */
    private static String sanYaoLingYingGaiLunNote() {
        return "三要灵应篇·导论——" + SAN_YAO_LING_YING_GAILUN;
    }

    /** 卷之二·三要灵应篇·篇末总论/跋/诫语（照录；接入节点 11，在各类之应之后）。 */
    private static String sanYaoLingYingZongLunNote() {
        return "三要灵应篇·总论——" + SAN_YAO_LING_YING_ZONGLUN;
    }

    // ------------------------------------------------------------------
    // 第 37 轮·「相对原著之遗漏」补录（B109–B111）：
    //   ① 卷之二·十应目论·总纲（L1348–1349）——十应并以体卦为主、内外合断之纲；
    //   ② 卷之三·占卜十应诀·〔方应〕（L1791–1796，照录）；
    //   ③ 卷之三·占卜十应诀·〔刻应〕〔外应〕〔天时应〕〔地理应〕〔人事应〕（L1801–1822，照录）。
    //   此前仅落 B50 十应总目（正/互/变应之转述）与结语，逐应原文未录，故补。
    //   第 37 轮起，L 行号一律依底本**单制**（`wc -l design/_epub_dump.txt` = 5203 行；
    //   前三十六轮注释所用 ~2× 之号为编辑器万能换行双制，非底本真行号，详见报告 §65.5/教训⑱）。
    // ------------------------------------------------------------------
    private static String SHI_YING_MU_LUN =
        "十应并以体卦为主，诸用卦为用。每以内分外体，用卦参看为妙。内卦不吉而外卦又吉，可以解其不吉；"
      + "内卦吉而外卦不吉，反破其吉。若内外卦全吉，则断然吉，全凶则断然凶。其内吉外凶、外吉内凶，"
      + "又须详理以断吉凶，慎不可胶柱鼓瑟也。外卦十应之目，则有天时、地理及写字等，其十一类之应，"
      + "并以体卦为主，而随其所应以为用也。";   // 卷之二·十应目论（L1348–1349）

    private static String ZHAN_BU_SHI_YING_FANG =
        "〔方应〕方应者，以体为主，看来占之人在何方位上，即看其所坐立之方位。卦宜生体卦，又宜与体比和，"
      + "则吉；如克体卦则凶；如体卦生之，亦不吉矣。";   // 卷之三·占卜十应诀·方应（L1791–1796）

    private static String ZHAN_BU_SHI_YING_YU =
        "〔刻应〕刻应者，即三要之诀也。占卜之顷，随所闻所见吉凶之兆以为吉凶之应也。"
      + "〔外应〕外应者，外卦之应也。占卜之际，偶见外物之来者，即看其物属何卦。如火得离、水得坎之类。"
      + "如见老人、马、金、玉、圆物，得乾。见老妇、牛、土瓦物，得坤之类。又如见此者，为外应之卦。"
      + "并看其卦与体卦生克比和之理，以决吉凶。"
      + "〔天时应〕天时之应，占卜之际，晴明为离，雨雪为坎，风为巽，雷为震。故离为体，宜晴。坎为体，宜雨。"
      + "巽为体，宜风。震为体，宜雷。火见雷为比和，参之生克，以定吉凶。"
      + "〔地理应〕地理之应，凡占卜之时，偶在竹林间，为震、巽之地；在江河溪涧池沼之上为坎；"
      + "在五金之处为乾、兑之乡；在窑灶炉火之所为离；在土瓦之所为坤、艮，并为体卦，论生克比和之理以决之。"
      + "〔人事应〕人事之应，即三要中人事之克应也。盖占卜之际，偶遇人事之吉为吉，偶遇人事之凶为凶。"
      + "如闻笑语，主有吉庆之事；遇哭泣，主有悲愁之事。又以人事之属于卦者论之：老人为乾，老妇为坤，"
      + "少男为艮，少女为兑。并看此人事之卦与体卦生克比和，以决吉凶。";   // 卷之三·占卜十应诀·刻/外/天时/地理/人事应（L1801–1822）

    /** 卷之二·十应目论·总纲（照录；接入节点 11，在各类之应之前）。 */
    private static String shiYingMuLunNote() {
        return "十应目论——" + SHI_YING_MU_LUN;
    }

    /** 卷之三·占卜十应诀·逐应原文（方应＋刻/外/天时/地理/人事应，照录；接于十应总目之后）。 */
    private static String zhanBuShiYingJueNote() {
        return ZHAN_BU_SHI_YING_FANG + ZHAN_BU_SHI_YING_YU;
    }

    // ------------------------------------------------------------------
    // 第 37 轮·「相对原著之遗漏」补录（B112–B118）：卷之二·三要灵应篇附属诸论（照录）。
    //   此前诸论或仅以转述落断法（向背/静占/衰旺/内外/体用类），或全未落（十应奥论/体用/动静），
    //   今一律照录归位（接入节点 16「卷之二诸论」），与断法并存不悖。
    // ------------------------------------------------------------------
    private static String SHI_YING_AO_LUN =
        "十应奥论十应固出于三要，而妙乎三要。但以耳目所得，如见吉兆而终须吉，逢凶谶不免乎凶，"
      + "理之自然也。然止以此而遇吉凶，亦有未然者也。黄金白银，为世之宝，三要得之，必凶。注："
      + "十应之诀，遇金有不吉者，利刃锐兵，世谓凶器，三要得之，亦以为凶；十应之说，遇兵刃反有"
      + "吉者。又若占产见少男，三要得之为生子之喜，十应见少男凶。占病遇棺，三要占之必死，十应"
      + "以为有生意。例多若此，是占卜物者，不可无十应也。";   // 卷之二·十应奥论（L1336–1345）

    private static String TI_YONG_LUN =
        "体用凡占卜成卦，即画成三重：本卦、互卦、变卦也。使于本卦分体用，此一体一用也。以卦五"
      + "行明生克比和之理，此一用卦。最切看画卦变卦，互变亦用也。此内之体用也。又次看应卦，亦"
      + "用也。此合内外之体用也。然则不止一体一用，所谓体一用百也。生克即分体用，则论生克，生"
      + "体则吉，克体则凶，比和则吉，不必论矣。生体多者则愈吉，克体多者则愈凶。然此卦生体者，"
      + "卦有克此卦者，颇减其吉。此卦克体，诸卦又有克此卦者，稍解其穷。有生此卦者吉，有克此卦"
      + "者凶。此体用之生克也。然卦之生克，有不论体用者。如占天时，有震则有雷，有巽则有风，逢"
      + "坎则有雨，逢离则晴。此一定之理。又有不然者，如论卦中乾、兑多，则震无雷，巽亦无风，又"
      + "必有他诀也，皆隐然外卦之意。如观梅有女折花，算牡丹有马践，地风升有饮食兆。此又非外应"
      + "之兆不能决也。";   // 卷之二·体用（L1385–1389）

    private static String TI_YONG_LEI_LUN =
        "体用类心易寓物之用，以体为主。然人知一体一用之常，不知一体百用之变。并体之变，全卦为"
      + "内，卦内亦不知一用而互变皆用也。三要十应之卦，外卦也，外亦不一，无非用也。学寓物者，"
      + "得体用以为至术，十应则罕有之，后则三要以为全术。且谓体用自体用，三要自三要，遂以体用"
      + "决吉凶，以三要为吉凶之兆。孰知三要、十应、体用之致。鸣乎！体用不可无三要，十应不可无"
      + "体用。体用、三要、十应，理无间然也。如此者，是谓心易之全术，而可以尽占卜之道也。又如"
      + "乾、兑多则巽无风；坤、艮多则坎无雨；坎多，则离亦不晴。盖以乾、兑之金，克震、巽之木；"
      + "坤、艮之土，克坎水；坎水克离火也。此又须通变而推验之。又若占饮食，有坎则有酒，有兑则"
      + "有食。如遇坤、艮，则坎亦无酒，离值则兑亦无食。余皆可以类推。故举此二类，为心易生克之"
      + "例耳。";   // 卷之二·体用类（L1390–1392）

    private static String SHUAI_WANG_LUN =
        "衰旺论既明生克，当看衰旺。旺者，如春震、巽木，夏离火，秋乾、兑金，冬坎水，四季之月坤"
      + "、艮土是也。衰者，如春坤、艮，夏乾、兑，秋震、巽，冬离，四季之月坎是也。凡占卜，体卦"
      + "宜盛旺。气旺而又逢生则吉，重遇克则凶。若体衰而逢克，则其凶甚矣。体衰而有生体之卦，则"
      + "衰稍吉。大抵体之卦宜旺，生体之卦气亦宜旺，克体之卦气宜衰。此心易论衰旺之诀也。";   // 卷之二·衰旺论（L1393–1397）

    private static String NEI_WAI_LUN =
        "内外论凡占卜，体用为内，诸应卦为外卦，此占卜之例也。诸应卦与三要之应、与十应之应，必"
      + "合内外卦而断之也。苟不知合内外卦为断，谓体用自体用、三要十应自三要十应，如此则鲜见其"
      + "有验者。然十应罕有知者，如待明论云：金银为世宝，三要为吉者，若震、巽为体，则金克木，"
      + "反为不吉。兵刃为世凶，三要为凶者，若坎为体，则金生水，反为不凶。占产见男子，谓有生子"
      + "兆。设坎为体，少男为艮土，土克水，产反不吉。占疾见棺必死，若遇离体，则木生火而反吉。"
      + "似此之类，则内卦不可无外卦，外卦不可无内卦。占卜之精者，无非合内外之道也。";   // 卷之二·内外论（L1399–1403）

    private static String DONG_JING_LUN =
        "动静凡占诀，虽明动静之机，然有理之常，有事之变。阳动而阴静，一动一静者，理之常；此静"
      + "而彼动，一静百动者，事之变也。天下之事物，纷纷群动，我则以一静而待之。事物之动，各有"
      + "其端，我则以一静而测之。不动不占，不因事不占。占卜之际，察其群物之事，物动而凶者，兆"
      + "吾卦之凶；物动而吉者，兆吾卦之吉。然于闹喧市尘之地，人物杂扰，群物满前，拈何事何物为"
      + "吉？吾占卜之应乎此，又推乎理而合其事。盖于群动之中，或观其身临吾耳目之近者，或以先见"
      + "者，或以群事分明者，或吾之一念所在者，此发占之所用。若求名，则于群动之中，或遇官府，"
      + "或有文书及袍笏仪卫之物，则为得官之应。若求财利，则遇巨富商贾，或有钱宝货财之物，则厥"
      + "为获利之应。若占讼事，而忽逢笞杖枷锁之具，则讼终不吉。占病而不见衰麻棺椁之物者，病当"
      + "无恙。凡此所谓事事相关，物物相应，是以验吾占卜之切要也。至若坐则应迟，行则应速，走则"
      + "愈速，卧则愈迟，此则察其动之端也。吾心本静，人来占卜，起念以应之，即动也。以此动而测"
      + "彼动，于方之念而求彼之验，诚而神知之。知此者，可以知动静之机矣。";   // 卷之二·动静（L1404–1412）

    private static String XIANG_BEI_LUN =
        "向背凡占卜求应，必须审其向背。向者，为事物之应，相向而来。背者，谓事物之应，相背而去"
      + "也。如鸦报灾，鸦飞适来，其灾将至；鸦飞而去，则灾已过去也。如鹊报喜，鹊飞适来，其喜将"
      + "至；鹊飞已去，则喜已过去也。至于外应之卦皆然。其克体之卦，器物方来，其祸将至，去则祸"
      + "散。其生体之卦，器物方来则吉，去则吉已过矣。其他应兆皆然。此为占卜向背至当之理矣。";   // 卷之二·向背（L1413–1415）

    private static String JING_ZHAN_LUN =
        "静占凡占在静室，无所闻见，则无外卦，即不论外卦，但以全卦年月日值五行衰旺之气，以体用"
      + "决之。";   // 卷之二·静占（L1416–1417）

    private static String DONG_XUAN_YIN =
        "洞玄歌者，洞达玄妙之说也。此歌多为占宅气而发。昔牛思晦尝入人家，知其吉凶先兆，盖此术"
      + "云。是故家之兴衰必有祯祥妖孽之谶，识者鉴之，不识者昧之。故此歌发其蕴奥，皆理之必然者"
      + "，切勿以浅近目之也。";   // 卷之二·观物洞玄歌·引言（L1419）

    private static String DONG_XUAN_BA =
        "右《洞玄歌》与《灵应》，同出而小异。彼篇多为占卜而诀，盖占卜之际，随所出所见，以为克"
      + "应之兆。此歌则不特为占卜之事，一时而入人家，有此事，必有此理。盖多寓观察之术也。然有"
      + "数端，人家可得警戒而趋避之，或可转祸为福。偶不知所因而宥于数中，俾吾见之，则善恶不逃"
      + "乎明鉴矣。";   // 卷之二·观物洞玄歌·跋（L1429–1431）

    /** 卷之二·三要灵应篇附属诸论（十应奥论/体用/体用类/衰旺论/内外论/动静/向背/静占；照录；接入节点 16）。 */
    private static String juanErZhuLunNote() {
        return "卷之二·三要灵应篇附属诸论（照录）——"
                + SHI_YING_AO_LUN + "；" + TI_YONG_LUN + "；" + TI_YONG_LEI_LUN + "；"
                + SHUAI_WANG_LUN + "；" + NEI_WAI_LUN + "；" + DONG_JING_LUN + "；"
                + XIANG_BEI_LUN + "；" + JING_ZHAN_LUN;
    }

    /** 卷之二·观物洞玄歌·引言与跋（照录；接入节点 16）。 */
    private static String dongXuanYinBaNote() {
        return "观物洞玄歌——" + DONG_XUAN_YIN + "……" + DONG_XUAN_BA;
    }


    // ------------------------------------------------------------------
    // 第 45 轮·「从头复查」补录（B176）：卷之一·象数易理开篇诸篇（照录）。
    //   卷之一卷首「象数易理」先列：周易卦数〔图象〕、五行生克、八宫所属五行、卦气旺、卦气衰、
    //   十天干、十二地支、八卦象例〔图象〕；次列占法/玩法（第 28 轮已录）、卦数起例以下起卦法
    //   （第 38 轮已录）。此前仅「八宫所属五行/卦气旺衰」以断法或注释散见（index.html 提示、
    //   vitalityOf 规则文本），成篇原文全程未落；今照录归位（接入节点 16）。
    //   周易卦数（Image00003）／八卦象例（Image00004）二篇底本为整页图象、无文字层，照实存目。
    //   「五行生克」底本为五格表（每格一事），照录依底本行序连书、不加分隔（教训⑨一字一标点）。
    //   L 行号依底本单制（`wc -l design/_epub_dump.txt` = 5203）：周易卦数 L191／五行生克 L194–208／
    //   八宫所属五行 L212–213／卦气旺 L214–220／卦气衰 L221–222／十天干 L223–224／十二地支 L225–226／
    //   八卦象例 L228。
    // ------------------------------------------------------------------
    private static String JUAN_YI_XIANG_SHU =
        "周易卦数五行生克金生水水生木木生火火生土土生金；金克木木克土土克水水克火火克金。"
      + "八宫所属五行乾、兑金，坤、艮土，震、巽木，坎水，离火。"
      + "卦气旺震、巽木旺于春。离火旺于夏。乾、兑金旺于秋。坎水旺于冬。坤、艮土旺于辰戌丑未月。"
      + "卦气衰春坤、艮。夏乾、兑。秋震、巽。冬离。辰戌丑未坎。"
      + "十天干甲乙东方木。丙丁南方火。戊己中央土。庚辛西方金。壬癸北方水。"
      + "十二地支子水鼠，丑土牛，寅木虎，卯木兔，辰土龙，巳火蛇，午火马，未土羊，申金猴，酉金鸡，戌土犬，亥水猪。"
      + "八卦象例";

    /** 卷之一·象数易理开篇诸篇（照录；周易卦数·八卦象例图象存目；接入节点 16）。 */
    private static String juanYiXiangShuNote() {
        return "卷之一·象数易理开篇（周易卦数〔图象存目〕；八卦象例〔图象存目〕）——" + JUAN_YI_XIANG_SHU;
    }


    // ------------------------------------------------------------------
    // 第 45 轮·补录（B177）：卷之一·八卦万物类占·篇名与篇末按语（照录）。
    //   八卦万物类占（卷之一第三张类象表：「乾卦一金 乾为天…」逐卦逐维）之表体已结构化落于
    //   ABox/OWL（取象 8 卦×27 维＝216，第九器 verify_symbols 逐维精确相等）；其篇名与篇末总
    //   按语为散文，此前未录（连文扫描亦因表体 tokenized 而不可见），今照录归位（接入节点 16）。
    //   表体已外置，故此处仅录篇名＋按语，不重录表体。
    //   L 行号依底本单制：篇名 L584／按语 L971。
    // ------------------------------------------------------------------
    private static String JUAN_YI_BA_GUA_LEI_ZHAN_AN =
        "右万物之象，庶事之多不止于此，占者宜各以其类而推之耳。";

    /** 卷之一·八卦万物类占·篇名与篇末按语（照录；表体取象见 ABox；接入节点 16）。 */
    private static String juanYiBaGuaLeiZhanAnNote() {
        return "卷之一·八卦万物类占（篇名「八卦万物类占」＋篇末按语；表体取象见 ABox）——" + JUAN_YI_BA_GUA_LEI_ZHAN_AN;
    }


    // ------------------------------------------------------------------
    // 第 38 轮·「相对原著之遗漏」补录（B119–B120）：卷之一·占例十则（照录）。
    //   卷之一「占例」原为先天五例（观梅占/牡丹占/邻夜扣门借物占/今日动静如何/西林寺牌额占）
    //   ＋后天五例（老人有忧色占/少年有喜色占/牛哀鸣占/鸡悲鸣占/枯枝坠地占），共十则；
    //   此前仅以转述散见断法（占牡丹/马践等），成篇原文未录，今照录归位（接入节点 16「卷之一占例」）。
    //   L 行号依底本单制（`wc -l design/_epub_dump.txt` = 5203）。
    // ------------------------------------------------------------------
    private static String JUAN_YI_XIAN_TIAN_LI =
        "观梅占（年月日时占例）辰年十二月十七日申时，康节先生偶观梅，见二雀争枝坠地。先生曰：“不动不"
      + "占，不因事不占。今二雀争枝坠地，怪也。”因占之，辰年五数，十二月十二数，十七日十七数，共三十"
      + "四数，除四八三十二，得二，属兑，为上卦，加申时九数，总得四十三数，五八除四十，零得三数，为离"
      + "，作下卦。又上下总四十三数，以六除，六七除四十二，得一零为动爻，是为泽火革。初爻变咸，互见乾"
      + "巽。断之曰：详此卦，明晚当有女子折花，园丁不知而逐之，女子失惊坠地，遂伤其股。右兑金为体，离"
      + "火克之。互中巽木，复生起离火，则克体之卦气盛。兑为少女，固知女子之被伤。而互中巽木，又逢乾金"
      + "兑金克之，则巽木被伤，而巽为股，故有伤股之应。幸变为艮土，兑金得生，知女子但被伤，而不至凶危"
      + "也。牡丹占巳年三月十六日卯时，先生与客往司马公家共观牡丹。时值花开甚盛，客曰：“花盛如此，亦"
      + "有数乎？”先生曰：“莫不有数。且因问而可占矣。”遂占之。以巳年六数，三月三数，十六日十六数，"
      + "总得二十五数，除三八二十四数，零一数为乾，为上卦。加卯时得四数，共得二十九数，又除三八二十四"
      + "数，零五为巽卦，作下卦，得天风姤。又以总计二十九数，以六除之，四六二十四，零五爻动，变鼎卦，"
      + "互见重乾。遂与客曰：“怪哉！此花明日午时，当为马所践毁。”众客愕然不信。次日午时，果有贵官观"
      + "牡丹，二马斗啮，群惊花间驰骤，花尽为之践毁。断之曰：巽木为体，乾金克之，互卦又见重乾，克体之"
      + "卦多矣，卦中无生意，固知牡丹必为践毁。所谓马者，乾为马也。午时者，离明之象，是以知之也。邻夜"
      + "扣门借物占（系闻声占例）冬夕酉时，先生方拥炉，有扣门者，初扣一声而止，继而又扣五声，且云借物"
      + "。先生令勿言，令其子占之，试所借何物。以一声属乾为上卦，以五声属巽为下卦，又以一乾五巽共六数"
      + "，加酉时数共得十六数，以六除之，二六一十二，得天风姤。第四爻变巽卦，互见重乾。卦中三乾金，二"
      + "巽木，为金木而已。又以乾金短而巽木长，是借斧也。子乃断曰：“金短木长者，器也，所借锄也。”先"
      + "生曰：“非锄必斧也。”问之，果借斧。其子问何故，先生曰：“起数又须明理。以卦推之，斧亦可也，"
      + "锄亦可也。以理推之，夕晚安用锄？必借斧。盖斧切于劈柴之用耳。”推数又须明理，为卜占之切要也。"
      + "盖数不推理，是不得也。学数者志之。今日动静如何（系声音占例）有客问曰：“今日动静如何？”遂将"
      + "此六字占之。以平分“今日动”三字为上卦。“今”平声，一数；“日”入声，四数；“动”去声，三数"
      + "，共八数，得坤为上卦。以“静如何”为下卦。“静”去声，三数；“如”平声，一数；“何”平声，一"
      + "数，共五数，为巽，作下卦。又以八五总为十三数，除二六一十二，零得一数，为地风升。初爻动，变泰"
      + "卦，互见震、兑。遂谓客曰：“今日有人相请，客不多，酒不醉，味止鸡黍而已。”当晚果然。断曰：升"
      + "者，有升阶之义，互震、兑，有东西席之分。卦中兑为口，坤为腹，为口腹之事，故知有人相请。客不多"
      + "者，坤土独立，无同类之卦气也。酒不醉，卦中无坎。味止鸡黍者，坤为黍稷耳。盖卦无相生之义，故知"
      + "酒不多，食品不丰也。西林寺牌额占（系字画占例）先生偶见西林寺之额，“林”字无两勾，因占之。以"
      + "西字七画为艮，作上卦；以林八画为坤，作下卦。以上七画下八画总十五画，除二六一十二，零数得三，"
      + "是山地剥卦。第三爻动，变艮，互见重坤。断曰：寺者，纯阳之所居。今卦得重阴之爻，而又有群阴剥阳"
      + "之兆。详此，则寺中当有阴人之祸。询之果然，遂谓寺僧曰：“何不添‘林’字两勾，则自然无阴人之祸"
      + "矣。”僧信然，即添‘林’字两勾，寺果无事。右纯阳之人，所居得纯阴之卦，故不吉。又有群阴剥阳之"
      + "义，故有阴人之祸。若添“林”字两勾，则十画，除八得二为兑卦，合上艮，是为山泽损。第五爻变动为"
      + "中孚卦，互卦见坤、震，损者益之，始用互俱生体，为吉卦，可以得安矣。右已上并是先得数，以数起卦"
      + "，所谓先天之数也。";   // 卷之一·先天五占例（L375–460，照录）

    private static String JUAN_YI_HOU_TIAN_LI =
        "老人有忧色占（端法占例）己丑日卯时，偶在途行，有老人往巽方，有忧色。问其何以有忧，曰无。怪而"
      + "占之，以老人属乾为上卦，巽方为下卦，是天风姤。又以乾一巽五之数，加卯时四数，总十数，除六得四"
      + "为动爻，是为天风姤之九四。《易》曰：“包无鱼，凶。”是《易》辞不吉矣。以卦论之，巽木为体，乾"
      + "金克之，互卦又见重乾，俱是克体，并无生气，且时在途行，其应速。遂以成卦之数中分而取其半，谓老"
      + "人曰：“汝于五日内谨慎出入，恐有重祸。”果五日，此老赴吉席，因鱼骨鲠而终。右凡占卜，克应之期"
      + "看自己之动静，以决事之迟速，故行则应速，以遂成卦之数，中分而取其半也。坐则事应迟，当倍其成卦"
      + "之数而定之也。立则半迟半速，止以成卦之数定之可也。虽然如是，又在变通，如占牡丹及观梅之类，则"
      + "二花皆朝夕之故，岂特成数之久也。少年有喜色占壬申日午时，有少年从离方来，喜形于色。问有何喜，"
      + "曰无。遂占之，以少年属艮为上卦，离为下卦，得山火贲。以艮七离三加午时七，总十七数，除十二，零"
      + "五为动爻，是贲之六五爻，曰：“贲于丘园，束帛戋戋，吉。”《易》辞已吉矣。卦则贲之家人，互见震"
      + "、坎，离为体，互变俱生之。断曰：子于十七日内必有聘币之喜。至期，果然定亲。牛哀鸣占癸卯日午时"
      + "，有牛鸣于坎方，声极悲，因占之。牛属坤，为上卦，坎方为下卦。坎六坤八，加午时七，共二十一数，"
      + "除三六一十八，三爻动得地水师之三爻。《易》辞曰：“师或舆尸，凶。”卦则师变升，互坤、震，乃坤"
      + "为体，互变俱克之，并无生气。断曰：此牛二十一日内必遭屠杀。后二十日，人果买此牛，杀以犒众，悉"
      + "皆异之。鸡悲鸣占甲申日卯时，有鸡鸣于乾方，声极悲怆，因占之。鸡属巽，为上卦，乾方为下卦，得风"
      + "天小畜。以巽五乾一共六数，加卯时四数，总十数，除六，得四爻动变乾，是为小畜之六四。《易》曰："
      + "“有孚，血去惕出。”以血推之，割鸡之义。卦则小畜之乾，互见离、兑。乾金为体，离火克之。卦中巽"
      + "木离火，有烹饪之象。断曰：此鸡十日当烹。果十日，客至，有烹鸡之验。枯枝坠地占戊子日辰时，偶行"
      + "至中途，有树蔚然，无风，枯枝自坠地于兑方。占之，槁木为离，作上卦，兑方为下卦，得火泽睽。以兑"
      + "二离三，加辰时五数，总十数，除六零四，变山泽损，是睽之九四。《易》曰：“睽孤，遇元夫。”卦火"
      + "泽睽变损，互见坎、离，兑金为体，离火克之，且睽、损卦名，俱有伤残之义。断曰：此树十日当伐。果"
      + "十日，伐树起公廨，而匠者适字“元夫”也。右已上诸占例，并是先得卦，以卦起数，所谓后天之数也。";   // 卷之一·后天五占例（L463–520，照录）

    /** 卷之一·占例十则（先天五例＋后天五例；照录；接入节点 16）。 */
    private static String juanYiZhanLiNote() {
        return "卷之一·占例（先天之数）——" + JUAN_YI_XIAN_TIAN_LI
                + "；卷之一·占例（后天之数）——" + JUAN_YI_HOU_TIAN_LI;
    }


    // ------------------------------------------------------------------
    // 第 38 轮·续（B121–B122）：卷之一·起卦法与风觉鸟占（照录）。
    //   ① 起卦法（卦数起例/爻以六除/互卦止用八卦/年月日时起例/物占数比/声音占/字占一字~十一字以上/
    //      丈尺占/尺寸物占/为人占/自己占/占动物/占静物/物卦起例/八卦万物属类/八卦方位图）；
    //   ② 风觉鸟占（风觉鸟占/风觉占/鸟占/听声音占/形物占/验色占/八卦所属内外动静之图）。
    //   此前仅以断法或片段引文散见，成篇原文未录，今照录归位（接入节点 16）。
    //   L 行号依底本单制（`wc -l design/_epub_dump.txt` = 5203）。
    // ------------------------------------------------------------------
    private static String JUAN_YI_QI_GUA_FA =
        "卦数起例卦以八除，凡起卦不问数多少，即以八作卦数，过八数即以八数退除，以零数作卦。如一八除不"
      + "尽，再除二八、三八，直除尽八数，以零数作卦。如得八数整，即坤卦，更不必除也。爻以六除凡起动爻"
      + "，以重卦总数除六，以零作动爻，如不满六，止用此数为动爻，不必再除。如过六数，则除之一六，不尽"
      + "再除二六、三六，直除尽，以零数作动爻。若一爻动，则看此一爻，是阳爻则变阴爻，阴爻则变阳爻。取"
      + "爻当以时加之。互卦止用八卦不必取六十四卦名互卦以重卦去了初爻及第六爻，以中间四爻分作两卦，看"
      + "得何卦。又云：乾坤无互，互其变卦。年月日时起例年月日为上卦，年月日加时总数为下卦。又以年月日"
      + "时总数取爻。如子年一数，丑年二数，直至亥年十二数。月如正月一数，直至十二月亦作十二数。日数如"
      + "初一一数，直至三十日为三十数。已上年月日共计几数，以八除之，以零数作上卦。时如子时一数，直至"
      + "亥时为十二数，就将年月日数加时之数，总计几数，以八除之，零数作下卦，就以除六数作动爻。物占数"
      + "比见有可数之物，即以此数起作上卦，以时数配作下卦。却以卦数并时数，总除六，取动爻。声音占凡闻"
      + "声音，数得几数，起作上卦，加时数配作下卦。又以声音，如闻动物鸣叫之声，或闻人敲击之声，皆可作"
      + "数起卦。字占凡见字数，如停匀，即平分一半为上卦，一半为下卦。如字数不均，即少一字为上卦，取天"
      + "轻清之义，以多一字为下卦，取地重浊之义。一字占一字为太极未判。如草混沌不明，不可得卦。如楷书"
      + "，则取其字画，以左为阳画，右为阴画。居左者看几数，取为上卦。居右者看几数，取为下卦。又以一字"
      + "之阴阳，全画取爻。彳、丿，此为左者，一、乁、、丶，此为右者。二字占二字为两仪平分。以一字为上"
      + "卦，以一字为下卦。三字占三字为三才。以一字为上卦，二字为下卦。四字占四字为四象。平分上下为卦"
      + "。又四字以上，不必数画数，只以平仄声音调之。平声为一数，上声为二数，去声为三数，入声为四数。"
      + "五字占五字为五行。以二字为上卦，三字为下卦。六字占六字为六爻之象。平分上下为卦。七字占七字为"
      + "齐七政。以三字为上卦，四字为下卦。八字占八字为八卦定位。平分上下为卦。九字占九字为九畴之义。"
      + "以四字为上卦，五字为下卦。十字占十字为成数。平分上下为卦。十一字占十一字以上至于百余字，皆可"
      + "起卦。但十一字以上，又不用平仄声音调之，止用字数。如字数均平，则以半为上卦，以半为下卦。又合"
      + "二卦总数取爻。丈尺占丈尺之物，以丈数为上卦，尺数为下卦。合尺丈之数取爻。数寸不系。尺寸物占以"
      + "尺数为上卦，寸数为下卦。合尺寸之数，加时取爻。分数不用。为人占凡为人占，其例不一。或听语声起"
      + "卦，或观其人品，或取诸身，或取诸物，或因其物色，触其外物，或以年月日时，或以书写来意。右听其"
      + "语声者，如或一句，即如字数分之起卦，如人说两句，即用先一句为上卦，后一句为下卦。语多，则但用"
      + "初听一句，合末后闻一语。余句不用。观其人品者，如老人为乾，少女为兑之类。取诸其身者，如头动为"
      + "乾，足动为震，目动为离之类。取诸其物者，如人手中偶有何物，如金玉及圆物之属为乾，土瓦及方物之"
      + "属为坤之类。因其物色者，如其人青衣为震，赤衣为离之类。触其外物者，起卦之时见水为坎卦，见火为"
      + "离卦之类。年月日时，如望梅之类推之。书写来意者。其人来占，或写来意，则以其字占之。自己占凡自"
      + "己欲占，以年月日时或闻有声音，或观当时有所触之外物，皆可起卦。已上三例，与前章为人占法同。占"
      + "动物凡占群物之动，不可起卦。如见一物，则就以此物为上卦，物来之方位为下卦。合物卦数及方位卦数"
      + "，加时数取爻，以此卦总断其物，如后天占牛鸣鸡叫之类。又凡牛马犬豕之类，初生，则以初生年月日时"
      + "占之。又或置买此物，亦可以初置买之时推之。占静物凡占静物，有如江河山石，不可起卦。若至屋宅、"
      + "树木之类，则屋宅初创之时，树木初置之时，皆可起卦。至于器物，则置成之时可占，如枕、椅之类是矣"
      + "。余则无故不占。若观梅，则见雀争枝坠地而占。牡丹，则自有问而占。茂树，则枝枯自坠而后占也。物"
      + "卦起例（端法后天起卦之例）后天端法，以物为上卦，方位为下卦，合物卦之数与方卦之数，加时数以取"
      + "动爻。八卦万物属类（并为上卦）乾卦：天、父、老人、官贵、头、骨、马、金、宝珠、玉、水果、圆物"
      + "、冠、镜、刚物、大赤色、水、寒。坤卦：地、母、老妇、土、牛、釜、布帛、文章、舆、辇、方物、柄"
      + "、黄色、瓦器、腹、裳、黑色、黍稷、书、米、谷。震卦：雷、长男、足、发、龙、百虫、蹄、竹、萑苇"
      + "、马鸣、母足、颡、稼、乐器之类、草木、青碧绿色、树、木核、柴、蛇。巽卦：风、长女、僧尼、、股"
      + "、百禽、百草、臼、香气、臭、绳、眼、羽毛、帆、扇、枝叶之类、仙道、工匠、直物、工巧之器。坎卦"
      + "：水、雨雪、工、豕、中男、沟渎、弓轮、耳、血、月、盗、宫律、栋、丛棘、狐、蒺藜、桎梏、水族、"
      + "鱼、盐、酒醢、有核之物、黑色。离卦：火、雉、日、目、电、霓、霞、中女、甲胄、戈兵、文书、槁木"
      + "、炉、灶、龟、蟹、蚌、凡有壳之物、红赤紫色、花、文人、干燥物。艮卦：山、土、少男、童子、狗、"
      + "手、指、径路、门阙、果蓏、阍寺、鼠、虎、狐、黔喙之物、木生之物、藤生之爪、鼻。兑卦：泽、少女"
      + "、巫、舌、妾、肺、羊、毁折之物、带口之器、属金者、废缺之物、奴、仆、婢。八卦方位图右离南坎北"
      + "，震东兑西，人则介乎其中。凡物之从花甲来，并起作下卦，加时取爻。";   // 卷之一·起卦法（照录）

    private static String JUAN_YI_FENG_JUE_NIAO =
        "风觉鸟占风觉鸟占者，谓见风而觉，见鸟而占也。然非风鸟二占，而谓风觉鸟占也。凡卦之寓物者，皆谓"
      + "之风觉鸟占。如易数，总谓之观梅之数也。风觉占风觉占者，谓其见风而觉也，见鸟而占也。凡见风起而"
      + "欲占之，便看风从何方而来，以之起卦。又须审其时，察其色，以推其声势，然后可断其吉凶。风从何方"
      + "来者，如风从南方来者，为家人（南方属离火，合得风火家人卦）。东来者，为益卦之类。审其时者，春"
      + "为发生和畅之风，夏为长养之风，秋为肃杀、冬为凛冽之类。察其色者，带埃烟云气，可见其色黄者，祥"
      + "瑞之气，青者半凶半吉，白主刃，气黑昏者凶，赤色者灾，红紫者吉。辨其声势者，其风声如阵马，主斗"
      + "争；如波涛者，有惊险；如悲咽者，有忧虞；如奏乐者，有喜事；如喧呼者，主闹哄；如烈焰者，有火惊"
      + "。其声洋洋而来，徐徐而去者，吉庆之兆也。鸟占鸟占者，见鸟可占也。凡见鸟群，数其只数，看其方所"
      + "，听其声音，辨其毛羽色，皆可起数。又须审其名义，察其鸣叫，取其吉凶。见鸟而占，数其只数者，如"
      + "一只属乾，二只属兑，三只属离。看其方所者，即离南、坎北之数。听其声音者，如鸟叫一声属乾，二声"
      + "属兑，三声属离之类，皆可起卦。听声音者，若夫鸣叫之喧啾者，主口舌；鸣叫悲咽者，主忧愁；鸣叫嘹"
      + "亮者，吉庆也。此取断吉凶之声音也。察其名义者，如鸦报灾，鹊报喜，鸾鹤为祥瑞，鹗鹏为妖孽之类是"
      + "也。听声音占声音者，如静室无所见，但于耳中所闻起卦。或数其数，验其方所；或辨其物声，详其所属"
      + "，皆可起卦。察其悲喜，助断吉凶。数其数目者，如一声属乾，二声属兑。验其方所者，离南、坎北之类"
      + "是也。如人语声及动物鸣叫之声，声自口出者属兑。而静物扣击属震，鼓拍槌敲、板木之声是也；金声属"
      + "乾，钟磬钲铎之声是也；火声属离，烈焰炬爆等声是也；土声属坤，筑基、杵垣、坡崩、山裂是也。此辨"
      + "其物声，详其所属也。察其悲喜，助断吉凶者，如闻人语声笑，又说吉语、娱笑者，有喜也；人悲泣声与"
      + "怨声、愁语及骂詈、穷叹等声，不吉也。形物占形物占者，凡见物形，可以起卦。如物之圆者属乾，刚者"
      + "属兑，方者属坤，柔者属巽，仰者属震，覆者属艮，长者属巽，中刚外柔者属坎，内柔外刚者属离，干燥"
      + "枯槁者属离，有文彩者亦属离，凡障碍之势、物之破者属兑。验色占凡占色之青者属震，红紫赤者属离，"
      + "黄色者属坤，白色属兑，黑色属坎之类是也。八卦所属内外动静之图乾：玄黄、大赤色、金玉、宝珠、镜"
      + "、狮、圆物、木果、贵物、冠、象、马、天鹅、刚物。坎：水带子、带核之物、豕、鱼、弓轮、水具、水"
      + "中之物、盐、黑色、酒。艮：土石、黄色、虎、狗、土中之物、瓜果、百禽、鼠、黔喙之物。震：竹木、"
      + "青绿碧色、龙、蛇、萑苇、竹木乐器、草、蕃鲜之物。巽：木、蛇、长物、青碧绿、山木之禽鸟、香、鸡"
      + "、直物、竹木之器、工巧之器。离：火、文书、干戈、雉、龟、蟹、槁木、甲胄、螺、蚌、鳖、物赤色。"
      + "坤：土、万物、五谷、柔物、丝绵、百禽、牛、布帛、舆、釜、瓦器、黄色。兑：金刃、金器、乐器、泽"
      + "中之物、白色、有口缺之物、羊。";   // 卷之一·风觉鸟占（照录）

    /** 卷之一·起卦法全篇（照录；接入节点 16）。 */
    private static String juanYiQiGuaFaNote() {
        return "卷之一·起卦法（照录）——" + JUAN_YI_QI_GUA_FA;
    }

    /** 卷之一·风觉鸟占全篇（照录；接入节点 16）。 */
    private static String juanYiFengJueNiaoNote() {
        return "卷之一·风觉鸟占（照录）——" + JUAN_YI_FENG_JUE_NIAO;
    }


    // ------------------------------------------------------------------
    // 第 38 轮·续 2（B123–B127）：卷之二·体用总诀/十八占/复明诸应/观物洞玄歌正文/起卦加数例+屋宅占（照录）。
    //   此前或仅以断法/片段引文散见（体用总诀 L1023–1051、天时占第一…），成篇原文未录；
    //   今一律照录归位（接入节点 16）。L 行号依底本单制。
    // ------------------------------------------------------------------
    private static String JUAN_ER_TI_YONG_ZONG_JUE =
        "体用总诀体用云者，如易卦具卜筮之道，则易卦为体，以卜筮用之。此所谓体用者，借体用二字以寓动静"
      + "之卦，以分主客之兆，以为占例之准则也。大抵体用之说，体卦为主，用卦为事，互卦为事之中间，刻应"
      + "变卦为事之终。应体之卦气宜盛不宜衰。盛者如春震、巽，秋乾、兑，夏离，冬坎，四季之月坤、艮是也"
      + "。衰者，春坤、艮，秋震、巽，夏艮、兑，冬离，四季之月坎是也。宜受他卦之生，不宜他卦之克。他卦"
      + "者，谓用互变也。生者，如乾、兑金体，坤、艮生之；坤、艮土体，离火生之。离火体，震、巽木生之。"
      + "余皆仿此。克者，如金体火克，火体水克之类。体用之说，动静之机，八卦主宾。五行生克，体为己身之"
      + "兆，用为应事之端。体宜受用卦之生，用宜见卦体之克。体盛则吉，体衰则凶。用克体固不宜，体生用亦"
      + "非利。体党多而体势盛，用党多则体势衰。如卦体是金，而互变皆金，则是体之党多。如用卦是金，而互"
      + "变皆金，则是用之党多。体生用，为之泄气，如夏火逢土，亦泄气。体用之间比和则吉，互乃中间之应，"
      + "变乃末后之期。故用吉变凶者，先吉后凶；用凶变吉者，先凶后吉。体克用，诸事吉；用克体，诸事凶。"
      + "体生用，有耗失之患；用生体，有进益之喜。体用比和，则百事顺遂。又看全卦中有生体之卦，看是何卦"
      + "。乾卦生体，则主公门中有喜益，或功名上有喜，或因官有财，或问讼得理，或有金宝之利，或有老人上"
      + "进财，或尊长惠送，或有官贵之喜。坤卦生体，主有田土之喜，或于田土进财，或得乡人之财，或得阴人"
      + "之利，或有果谷之利，或有布帛之喜。震卦生体，则主山林之益，或因山林得财，或进东方之财，或因动"
      + "中有喜，或有木货交易之利，或因草木姓氏人称心。巽卦生体，亦主山林之益，或因山林得财，或于东南"
      + "得财，或因草木人而进利，或以茶果得利，或茶果菜蔬馈送之喜。坎卦生体，有北方之喜，或受北方之财"
      + "，或水边人进利，或因点水人称心，或因鱼盐酒货文书交易之利，或有馈送鱼盐酒之喜。离卦生体，主有"
      + "南方之财，或有文书之喜，或有炉冶场之利，或因火姓人而得财。艮卦生体，有东北方之财，或山田之喜"
      + "，或因山林田土获财，或宫音带土人之财。物当安稳，事有终始。兑卦生体，有西方之财，或喜悦事，或"
      + "有食物玉金货利之源，或商音之人、市口之人欣逢，或主宾之乐，或朋友讲习之喜。又看卦中有克体之卦"
      + "者，看是何卦。如：乾卦克体，主有公事之扰，或门户之扰，或有财宝之失，或于金谷有损，或有怒于尊"
      + "长，或得罪于贵人。坤卦克体，主有田土之扰，或于田土有损，或有小人之害，或有阴人之侵，或失布帛"
      + "之财，或丧谷粟之利。震卦克体，主有虚惊，常多恐惧，或身心不能安静，或家宅见妖灾，或草木姓氏之"
      + "人相侵，或于山林有所失。巽卦克体，亦有草木姓人相害，或于山林上生忧。谋事，乃东南方之人；处家"
      + "，忌阴人小口之厄。坎卦克体，主险陷之事，或寇盗之忧，或失意于水边人，或生灾于酒后，或点水人相"
      + "害，或北方人见殃。离卦克体，主文书之扰，或失火之惊，或有南方之忧，或火人相害。艮卦克体，诸事"
      + "多迍，百谋中阻。或有山林田土之失，或带土人相侵，防东北方之祸害，或忧坟墓不当安稳。兑卦克体，"
      + "不利西方，主口舌事之纷争。或带口人侵欺，或有毁折之患，或因饮食而生忧。生克不逢，止随本卦而论"
      + "之。";   // 卷之二·体用总诀（照录）

    private static String JUAN_ER_SHI_BA_ZHAN =
        "天时占第一凡占天时，不分体用，全观诸卦，详推五行。离多主晴，坎多主雨，坤乃阴晦，乾主晴明，震"
      + "多则春夏雷轰，巽多则四时风冽，艮多则久雨必晴，兑多则不雨亦阴。夏占离多而无坎，则亢旱炎炎。冬"
      + "占坎多而无离，则雨雪飘飘。全观诸卦者，谓互变卦。五行谓离属火，主晴；坎为水，主雨；坤为地气，"
      + "主阴；乾为天，主晴明；震为雷，巽为风，秋冬震多无制，亦有非常之雷，有巽佐之，则为风撼雷动之应"
      + "；艮为山云之气，若雨久，得艮则当止。艮者，止也，亦土克水之义。兑为泽，故不雨亦阴。夫以造化之"
      + "辨固难测，理数之妙亦可凭，是以乾象乎天，四时晴明；坤体乎地，一气惨然。乾、坤两同，晴雨时变。"
      + "坤、艮两并，阴晦不常。卜数有阳有阴，卦象有奇有偶。阴雨阳晴，奇偶暗重。坤为老阴之极，而久晴必"
      + "雨。阴气而久雨必晴。若逢重坎重离，亦日时晴时雨。坎为水，必雨；离为火，必晴。乾、兑之金，秋明"
      + "晴，冬雪凛冽；坤、艮之土，春雨泽，夏火炎蒸。《易》曰：“云从龙，风从虎。”又曰：“艮为云，巽"
      + "为风。”艮、巽重逢，风云际会，飞沙走石，蔽日藏山，不以四时，不必二用。坎在艮上，布雾兴云，若"
      + "在兑上，凝霜作雪。乾、兑为霜雪霜雹，离火为日电虹霓。离为电，震为雷，重会而雷电俱作。坎为雨，"
      + "巽为风，相逢而风雨骤兴。震卦重逢，雷惊百里。坎爻叠见，润泽九垓。故卦体之两逢，亦爻象之总断。"
      + "地天泰、水天需，昏蒙之象。天地否、水地比，黑暗之形。八纯离，夏必旱，四季皆晴。八纯坎，冬必寒"
      + "，四时多雨。久雨不晴，逢艮必止。久晴不雨，得此亦然。又若水火既济、火水未济，四时不测风云；风"
      + "泽中孚、泽风大过，三冬必然雨雪。水山蹇、山水蒙，百步必须执盖。地风升、风地观，四时不可行船。"
      + "离在艮上，暮雨朝晴；离互艮宫，暮晴朝雨。巽、坎互离，虹霞乃见；巽、离互坎，造化亦同。又须推测"
      + "四时，不可执迷一理。震、离为电、为雷，应在夏天；乾、兑为霜、为雪，验于冬月。天地之理大矣哉！"
      + "理数之妙至矣哉！得斯文者，当敬宝之。人事占第二人事之占，详观体用。体卦为主，用卦为宾。用克体"
      + "不宜，体克用则吉。用生体有进益之喜，体生用有耗失之患。体用比和，谋为吉利。更详观互卦、变卦，"
      + "以断吉凶。复究盛衰，以明休咎。人事之占，则以前体用总章同决吉凶。若有生体之卦，即看前章八卦中"
      + "生体之卦有何吉，又看克体之卦有何凶，即看前章克体之卦。无生克，止断本卦。家宅占第三凡占家宅，"
      + "以体为主，用为家宅。体克用，则家宅多吉；用克体，则家宅多凶。体生用，多耗散，或防失盗之忧。用"
      + "生体，多进益，或有馈送之喜。体用比和，家宅安稳。如有生体之卦，即以前章人事占断之。屋舍占第四"
      + "（此占卜遇创之吉凶）凡占屋舍，以体为主，用为屋舍。体克用，居之吉；用克体，居之凶。体生用，主"
      + "资财冷退；用生体，则门户兴隆。体用比和，自然安稳。婚姻占第五占婚姻，以体为主，用为婚姻。用生"
      + "体，婚易成，或因婚有得；体生用，婚难成，或因婚有失。体克用，可成但成迟；用克体，不可成，成亦"
      + "有害。体用比和，婚姻吉利。占婚，体为所占之家，用为所婚之家。体卦旺，则此家门户胜；用卦旺，则"
      + "彼家资盛。生体，则得婚姻之财，或彼有相就之意；体生，则无嫁奁之资，或此去求婚方谐。若体用比和"
      + "，则彼此相就，良配无疑。乾：端正而长。坎：邪淫、黑色、嫉妒、奢侈。艮：色黄多巧。震：美貌难犯"
      + "。巽：发少稀疏，丑陋心贪。离：短赤色，性不常。坤：貌丑，大腹而黄。兑：高长，语话喜悦，白色。"
      + "生产占第六占生产，以体为母，用为生。体用俱宜乘旺，不宜乘衰。宜相生，不宜相克。体克用不利于男"
      + "，用克体不利于母。体克用而用卦衰，则子难完；用克体而体卦衰，则母难保。用生体，易于母；体生用"
      + "，易于生。体用比和，生育顺快。若欲辨其男女，当于前卦审之：阳卦阳爻多者则生男，阴卦阴爻多者则"
      + "生女。阴阳卦爻相生，则察所占左右人之奇偶以证之。如欲决其日辰，则以用卦之气数参决之。日期用卦"
      + "之气数者，即看何为用卦，于八卦时序之类决之。饮食占第七凡占饮食，以体为主，用为饮食。用生体，"
      + "饮食必丰；体生用，饮食难就。体克用，则饮食有阻；用克体，饮食必无。体用比和，饮食丰足。又卦中"
      + "有坎则有酒，有兑则有食。无坎无兑，则皆无。兑、坎生身，酒肉醉饱。欲知所食何物，以饮食推之。欲"
      + "知席上何人，以互卦人事推之。饮食人事类者，即前八卦内万物属类是也。求谋占第八占求谋，以体为主"
      + "，用为所谋之应。体克用，谋虽可成，但成迟。用克体，求谋不成，成亦有害。用生体，不谋而成；体生"
      + "用，则多谋少遂。体用比和，求谋称意。求名占第九凡占求名，以体为主，用为名。体克用，名可成，但"
      + "成迟。用克体，名不可成。体生用，名不可就，或因名有丧。用生体，名易成，或因名有得。体用比和，"
      + "功名称意。欲知名成之日，生体之卦气详之。欲知职任之处，变卦之方道决之。若无克体之卦，则名易就"
      + "。止看卦体时序之类，以定日期。若在任占卜，最忌见克体之卦。如卦有克体者，即居官见祸，轻则上司"
      + "责罚，重则剥官退职。其日期克体之卦气者，于八卦所属时序类中断之。求财占第十占求财，以体为主，"
      + "以用为财。体克用，有财；用克体，无财。体生用，财有损耗之忧；用生体，财有进益之喜。体用比和，"
      + "财利快意。欲知得财之日，生体之卦气定之。欲知破财之日，克体之卦气定之。又若卦中有体克用之卦，"
      + "及生体之卦，则有财，此卦气即见财之日。若卦中有克体之卦，及体生用之卦，即破财，此卦气即破财之"
      + "日。交易占第十一占交易，以体为主，用为交易之应。体克用，交易成迟；用克体，不成。体生用，难成"
      + "，或有交易之失。用生体，即成，成必有财。体用比和，易成交易。出行占第十二占出行，以体为主，用"
      + "为所行之应。体克用，可行，所至多得意。用克体，出则有祸。体生用，出行有破耗之失；用生体，有意"
      + "外之财。体用比和，出行顺快。又凡出行，体宜乘旺，诸卦宜生体。体卦乾、震多，主动；坤、艮多，不"
      + "动。巽宜乘船，离宜陆行。坎防失脱，兑主纷争之应也。行人占第十三占行人，以体为主，用为行人。体"
      + "克用，行人归迟；用克体，行人不归。体生用，行人未归；用生体，行人即归。体用比和，归期不日矣。"
      + "又以用卦为行人之盛旺。逢生，在外顺快；逢衰，受克，在外灾殃。震多不宁，艮多有阻。坎有险难，兑"
      + "主纷争之应。谒见占第十四占谒见，以体为主，用为所见之人。体克用，可见；用克体，不见。体生用，"
      + "难见，见之而无益；用生体，可见，见之且有得。体用比和，欢然相见。失物占第十五占失物，以体为主"
      + "，用为失物。体克用，可寻迟得；用克体，不可寻。体生用，物难见；用生体，物易寻。体用比和，物不"
      + "失矣。又以变卦为失物之所在。如变是乾，则觅于西北，或公廨楼阁之所，或金石之旁，或圆器之中，或"
      + "高亢之地。变卦是坤，则觅于西南方，或田野之所，或仓廪之处，或稼穑之处，或土窖穴藏之所，或瓦器"
      + "方器之中。震则寻于东方，或山林之所，或丛棘之内、钟鼓之旁，或闹市之间，或大途之所。巽则寻于东"
      + "南方，或山林之所，或寺观之地，或菜蔬之园，或舟车之间，或木器之内。坎则寻于北方，多藏于水边或"
      + "溪井沟渠之处，或酒醋之边，或鱼盐之地。离则寻于南方，或庖厨之间，或炉冶之旁，或在明窗，或遗虚"
      + "室，或在文书之侧，或在烟火之地。艮则寻于东北方，或山林之内，或近路边，或岩石旁，或藏土穴。兑"
      + "则寻于西方，或居泽畔，或败垣破壁之所、废井缺沼之中。疾病占第十六凡占疾病，以体为病人，用为病"
      + "症。体卦宜旺不宜衰，体宜逢生，不宜见克。用宜生体，不宜克体。是故体克用，病易安；体生用，病难"
      + "愈。体克用者，勿药有喜；用克体者，虽药无功。若体逢克而乘旺，犹为庶几。体遇克而更衰，断无存日"
      + "。欲知凶中有救，生体之卦存焉。体生用者，迁延难好；用生体者，即愈。体用比和，疾病易安。若究平"
      + "和之候，生体之卦决之。若详危厄之期，克体之卦定之。若论医药之属，当审生体之卦。如离卦生体，宜"
      + "服热药；坎卦生体，宜服冷药。如艮温补，乾、兑凉药是也。又有信鬼神之说，虽非易体，然不可谓易道"
      + "之不该。姑以理推之。如卦有克体者，即可测其鬼神。乾卦克体，主有西北方之神，或兵刀之鬼，或天行"
      + "时气，或称正之邪神。坤则西南之神，或旷野之鬼，或连亲之鬼，或水土里社之神，或犯方隅，或无主之"
      + "祟。震则东方之神，或木下之神，或妖怪百端，或影响时见。巽则东南之鬼，或自缢戕生，或枷锁致命。"
      + "坎则北方之鬼，或水旁之神，或没溺而亡，或血疾之鬼。离则南方之鬼，或猛勇之神，或犯灶司，或得愆"
      + "于香火，或焚烧之鬼，或遇热病而亡。艮则东北之神，或是山林之祟，或山魈木客，或土怪石精。兑则西"
      + "方之神，或阵亡之鬼，或废疾之鬼，或刎颈戕生之鬼。卦中无克体之卦者，不必论之。又问曰：乾上坤下"
      + "，占病如何断？尧夫曰：乾上坤下第一爻动，便是生体之义。变为震木，互见巽艮，俱是生成之义，是谓"
      + "不灾，逢生之日即愈。又曰：第二爻动如何？曰：是变为坎水，乃泄体败金之义。金入水乡，互见巽、离"
      + "，乃为风火扇炉，俱为克体之义。更看占时外应如何，即为焚尸之象，断之死无疑矣。以春夏秋冬四季推"
      + "之，更见详理。又曰：第三爻动，坤变艮土，俱在生体之义，不问互卦，亦断其吉无疑。又曰：第四爻动"
      + "，乾变巽木，金木俱有克体之义，互吉亦凶。木有扛尸之义，金为砖椁之推。是理必定之推，是理尸必定"
      + "之理。又曰：第五爻动，乾变离，反能生体，互变俱生体，是其吉无疑。更有吉兆则愈吉，凶则迟而忍死"
      + "。其断明矣。又曰：第六爻动，乾变兑，则能泄体，互见巽、艮，一凶一吉，其病非死必危。亦宜看兆吉"
      + "凶，吉则言吉，凶则言凶。此断甚明。余卦皆仿此断，则心易无不验矣。官讼占第十七占官讼，以体为主"
      + "，用为对辞之人与官讼之应。体卦宜旺，用卦宜衰。体宜用生，不宜生用。宜生体，不宜克体。是故体克"
      + "用者，己胜人；用克体者，人胜己。体生用，非为失理，或因官有所丧；用生体，不止得理，或因讼有所"
      + "得。体用比和，官讼最吉。非但扶持之力，必有主和之义。坟墓占第十八占坟墓以体为主，用为坟墓。体"
      + "克用，葬之吉；用克体，葬之凶。体生用，葬之主冷退；用生体，葬之主兴隆，有荫益后嗣。体用比和，"
      + "永为吉地，大宜安葬，葬之吉昌。右用体之诀，始以十八章占例以示后学之法则。然庶务之多，岂止十八"
      + "占而已？然此十八占，乃大事之切要者，占者以类而推之可也。";   // 卷之二·十八占（照录）

    private static String JUAN_ER_FU_MING_ZHU_YING =
        "复明天时之应如天无云翳，明朗之际，为乾之时。乾、兑为体，则比和而吉；坎为体，则逢生而大吉。坤"
      + "、艮为体，则泄气。震、巽为体，则见克而不吉矣。晴霁日中，为离之时，坤、兑为体则吉。雨雪为坎之"
      + "时，震、巽为体则吉，离为体则不吉。雷风为震、巽之时，离为体则吉，坤、艮为体则不吉。此天时之应"
      + "也。复明地理之应茂树秀竹，为震之地。离与震、巽为体则吉，坤、艮为体则凶。江湖、河池、川泽、溪"
      + "涧为坎之地，震、巽与坎为体则吉，而离为体则不吉。窑灶之地为离，坤、艮并离为体则吉，乾、兑为体"
      + "则不吉。岩穴之地为艮，乾、兑与艮为体则吉，坎为体则不吉。此地理之应也。复明人事之应人事有论卦"
      + "象五行者，有不论卦象五行者。论卦象，则老人属乾，老妇属坤，艮为少男，兑为少女之类。五行生克、"
      + "比和之理，与前天时、地理之卦同断。其不分卦象五行者，则以人事之纷，了见杂出，有吉有凶，此应则"
      + "随其吉凶而为之兆也。又观其事，则亦为某人。此人事之应也。复明时令之应时令不必论卦象，但详其令"
      + "，月日值之五行衰旺之气。旺者，寅卯之月日则木旺，巳午之月日火旺，申酉之月日金旺，亥子之月日水"
      + "旺，辰戌丑未之月日则土旺。衰者，如木旺则土衰，土旺则水衰，水旺则火衰，火旺则金衰，金旺则木衰"
      + "。是故生体之卦气，宜值时之旺气，不宜衰气。如克体卦气，宜乘衰。此时令之应也。复明方卦之应即分"
      + "方之卦。如离南、坎北、震东、兑西、巽东南、乾西北、艮东北、坤西南类。论吉凶者，看来占之人在何"
      + "卦位，而以用卦参详。如坎为用卦，宜在坎与震、巽之位，在离则不吉。离为用卦，宜在离与坤、艮之位"
      + "，在乾、兑二位则不吉矣。盖宜在本卦之方，为用卦生之方，不宜受用卦克也。若夫气在之卦所在之方，"
      + "又当审之。如水从坎来，为坎卦气旺。水从坤、艮来，则坎之卦气衰。火从南来，为离卦气旺，从北来，"
      + "则离之卦气衰。余皆仿此。大抵本卦之方，生为旺，受克为衰。宜以体卦参之。生体卦气，宜受旺方；克"
      + "体卦气，宜受克方。此方卦之应也。又震、巽之方，不论坤、艮。坤、艮之方，不论坎。坎方不论离。离"
      + "方不论乾。乾、兑之方，不论震、巽。以其寓卦受方卦之克也。复明动物之应动物有论卦象者。乾为马，"
      + "坤为牛，震为龙，巽为鸡，坎为豕，离为雉，艮为狗，兑为羊，又螺蜂龟鳖为离之象，鱼类为坎之属。此"
      + "动物之卦，以体详与。又不论卦象五行者，如乌鸦报灾，灵鹊报喜，鸿雁主有书信，蛇虫防有毒害，鸡唱"
      + "为家音，马嘶为动意。此动物之应也。复明静物之应器物之类，有论卦象者。如水属坎，火属离，木之气"
      + "属震、巽，金之气属乾、兑，土之气属坤、艮。为体卦，要参详。其不分卦象者，但观其器物之兆。如物"
      + "之圆者，事成；器之缺者，事败。又详其器物是何物，如笔砚主文书之事，袍笏主官职之事，樽俎之具有"
      + "宴集，枷锁之具防官灾。百端不一，审其物器。此静物之应也。复明言语之应闻人言语，不论卦象，但详"
      + "其所言之事绪而占卜之。应闻吉语则吉，闻凶语则凶，若闻斗丛言语喧集，难以决断。若定人少之处，或"
      + "言语可辨其事绪，则审其所言何事，心领而意会之。如说朝廷迁选，可以求名；论江湖州郡，主出行；言"
      + "争讼之事，主官司；言喜庆之事，利婚姻。事绪不一，随所闻以依之。此言语之应也。复明声音之应耳所"
      + "闻之声音而论卦象，则雷为震，风声为巽，雨声为坎，水声为坎，鼓拍槌敲之声出于木者，皆属震、巽，"
      + "钟磬铃铙之声出于金者，皆属乾、兑。此声音之论卦象。若为体，参详决之。如闻声有欢笑之声，主有喜"
      + "；悲愁之声，主有忧；歌唱之声，主快乐；怒号之声，主争喧。至若物声，则乌鸦报灾，鹊声传喜，鸿雁"
      + "之声主远信，鸡凫之声为佳音。此类推声音之应也。复明五色之应五色不论卦象，但以所见之色推五行。"
      + "青、碧、绿色属木，红、紫、赤色属火，白属金，黑属水，黄属土。外应之五行，详于内卦之体用、生克"
      + "、比和，吉凶可见。此五色之应也。复明写字之应淡中浓墨名为淬，浓墨中间薄似云，点画误书名鬼笔，"
      + "定知贼在暗中缠。涕为流泪防丧服，定主忧惊梦里眠。鬼笔误书防窃盗，定知方位与通传。此写字之应验"
      + "也。";   // 卷之二·复明诸应（照录）

    private static String JUAN_ER_DONG_XUAN_GE =
        "世间万事无非数，理在其中，吉凶悔吝有其机，祸福可先知。五行金、木、水、火、土，生克先为主；青"
      + "、黄、赤、黑、白之五形辨察要分明。人家吉凶何堪见，只向玄中判。入门辨察见闻时，于此察兴衰。若"
      + "还宅气如春意，家宅生和气。若然冷落似秋时，从此渐衰微。自然馨香如兰室，福至无虚日。鸡豚猫犬秽"
      + "薰腥，贫病至相侵。男妆女饰皆齐整，此去门风盛。家人垢面与蓬头，定见有悲忧。儿啼妇叹情怀悄，祸"
      + "害道阴小。老人无故泣双垂，不日见愁悲。门前墙壁缺，家道中消歇。溜漕水势向门流，财帛永难收。忽"
      + "然屋上生奇草，益荫人家好。门户幽爽绝尘埃，必定出高才。偶悬破履当门户，必有奴欺主。长长破碎在"
      + "边门，断不利家君。遮门临井桃花艳，内有风情染。屋前屋后有高桐，离别主人翁。井边倘种高梨树，长"
      + "有离乡土。祠堂神主忽焚香，火厄恐相招。檐前瓦片当门堕，诸事愁崩破。若抛破碗厕坑中，从此见贫穷"
      + "。白昼不宜灯在地，死者还相继。公然鼠向日中来，不日资财耗。牝鸡早晚鸣咿喔，阴盛家消索。中堂犬"
      + "吠立而啼，人眷有灾厄。清晨鹊噪连声继，远信行人至。蟒蛇偶尔入人家，人病见妖邪。雀群争逐当门盛"
      + "，口舌纷纷定。偶然鹏鸟（即鸱鸮也）叫当门，人口有迍逢。入门若见有群羊，家主病瘟黄。舟船若安在"
      + "平地，虽稳成淹滞。他家树荫过墙来，多得横来财。阶前石砌多残折，成事多衰灭。入门茶果应声来，中"
      + "馈主家财。三餐时候炊烟早，家道渐基好。连宵宿火不成时，人散与财离。千门万户难详备，理在吾心地"
      + "。斯文引路入先天，深奥入玄玄。";   // 卷之二·观物洞玄歌正文（照录）

    private static String JUAN_ER_QI_GUA_JIA_SHU =
        "起卦加数例寅年十二月初一日午时，有数家起造，俱在邻市之间。有三家以此年月日时求占于先生，若同"
      + "一卦，则吉凶莫辨矣。先生以各姓而加数，遂断之而皆验。盖三家求占，有田姓者，有王姓者，有韩姓者"
      + "。若寅年三数、十二与一，共十六，加王姓四画，得二十数，除二八一十六，得四，震为上卦；又加午时"
      + "，七数，总二十七数，除三八二十四，得三，离为下卦。二十七中除四六二十四，零三为爻，得丰变震，"
      + "互见兑、巽。其田姓加以田字六画，得水风井，变升，互见离、兑；其韩姓加入二十一画之数，得益变中"
      + "孚，互见艮、坤。乃以各家之姓起卦，随各家之卦断之也。不特起屋之年月日时加姓也，凡冠婚及葬事皆"
      + "须加姓，然冠葬皆加一姓可矣。若婚姻，则男女大事，必加二姓可也。极比之人无姓，亦必有名，不辨其"
      + "字，则数其声音。又无名，则随所寓也。屋宅之占诀寅年十二月初一日午时起屋者，其家田姓，其占水风"
      + "井，变地风升，互见离、兑。巽木为体，用卦坎水生之，虽兑金克木，得有离火，火虽无气，终是制金。"
      + "然有兑金，酉年月日，亦当有损失之忧。亥子水年月日，当有进益，或得水边之财，坎生体用也。寅卯年"
      + "当大快意，比和之气也。但家中必多口舌之聒，亦为兑也。木体近春，喜逢坎水，此居必能发旺。二十九"
      + "年后，此屋当毁。盖二十九年者，全卦六卦之成数也。若非有兑在中，虽再见二十九年，屋当无恙也。同"
      + "时王姓之家起造，得雷火丰，变震，互见兑、巽。震木为体，离为用卦。兑为体之互，克体亦切。虽得离"
      + "火制兑金，亦不纯美。用火泄体之气，破耗资财。每遇火年月日，主见此事，或因妇人而有损失，家中亦"
      + "多女子是非。亥子寅卯之年月，却主进益田财。盖震木为体，虽不见坎，终是利水年。生体之气，不见震"
      + "、巽，亦逢寅、卯，为体卦得局之时也。凡有震有巽，寅卯与木之气运年月，必大得意。亦主得长子之力"
      + "，变重震也。二十二年后为火所焚。韩姓之居，得益变中孚。巽体，互见艮、坤，变兑克体。此居必有官"
      + "讼，见于酉年月后。申酉年连见病患，所喜用卦其震与巽体比和，当见寅卯年发。即此居先吉后凶。三十"
      + "一年之后，遇申酉年，此居当毁。若非有兑，或有一坎，再见三十一年，此居亦无恙也。器物占大抵占器"
      + "物，并不喜见兑卦，盖兑为毁折也。若坎为体，则见兑无伤。乾卦为体亦无害。其余卦体，逢兑不久即破"
      + "。木之器物，或震、巽为体，见兑为用，必不禁耐用矣。破器之日，必申酉与卜年月日也。又畜养之物，"
      + "亦不宜乾、兑克体。种植之物，乾、兑克体，必不成，如成，必被斧斤之厄。种植之物，宜见坎也。又凡"
      + "见器物，欲知其成毁，亦看卦体。无克者则久长，体逢克则不久。视其器物之气数，可久者，以全卦之年"
      + "数断之；不可久者，以月数断之；至速者，以日数断之也。";   // 卷之二·起卦加数例·屋宅占·器物占（照录）

    // ------------------------------------------------------------------
    // 第 43 轮·全书再核补录（§71 / 教训㉔）：卷之二开篇四论 + 三要灵应篇·拆字之应段（照录）。
    //   全书未落区间穷尽扫描（_gen/scan_whole.py, W=14）发现：卷之二「心易占卜玄机／占卜总诀／
    //   占卜论理诀／先天后天论」四篇正文此前仅以断法/片语散见（占卜总诀、占卜论理诀见于流程设计
    //   节点 12 依据；先天后天论仅存 B61 应期节引），成篇原文未录；又「三要灵应篇」正文照录（第
    //   36 轮）依 C12「相字别系不纳」剔除了「拆字之应」一段。今一律照录归位（接点 16）。
    //   L 行号依底本单制。
    // ------------------------------------------------------------------
    /** 卷之二·心易占卜玄机（照录）。 */
    private static String JUAN_ER_XIN_YI_XUAN_JI =
        "心易占卜玄机天下之事有吉凶，托占以明其机；天下之理无形迹，假象以显其义。故乾有健之理，于马之类见之；故占卜寓吉凶之理，于卦象内见之。然卦"
      + "象一定不易之理，而无变通之道，不可也。易者，变易而已矣。至如今日观梅复得革兆，有女子折花，异日果有女子折花，可乎？今日算牡丹得姤兆，为马"
      + "所践，异日果为马所践毁，可乎？且兑之属，非止女子；乾之属，非止马。谓他人折花有毁，皆可切验之真，是必有属矣。嗟乎！占卜之道要变通。得变通"
      + "之道者，在乎心易之妙耳。";   // 卷之二·心易占卜玄机（照录）

    /** 卷之二·占卜总诀（照录）。 */
    private static String JUAN_ER_ZHAN_BU_ZONG_JUE =
        "占卜总诀大抵占卜之法，成卦之后先看《周易》爻辞，以断吉凶。如《乾》卦初九“潜龙勿用”，则诸事未可为，宜隐伏之类；九二“见龙在田，利见大人"
      + "”，则宜谒见贵人之类。余皆仿此。次看卦之体用，以论五行生克。体用即动静之说。体为主，用为事。应用事体及比和，则吉；体生用及克体，则不吉。"
      + "又次看克应。如闻吉说见吉兆，则吉，闻凶说见凶兆，则凶；见圆物，事易成，见缺物，事终毁之类。复验己身之动静。坐则事应迟，行则事应速，走则愈"
      + "速，卧则愈迟之类。数者既备，可尽占卜之道，必须以易卦为主，克应次之。俱吉则大吉；俱凶则大凶；有凶有吉，则详审卦辞，及克用体应之类，以断吉"
      + "凶也。要在圆机，不可执滞。";   // 卷之二·占卜总诀（照录）

    /** 卷之二·占卜论理诀（照录）。 */
    private static String JUAN_ER_ZHAN_BU_LUN_LI =
        "占卜论理诀数说当也，必以理论之而后备。苟论数而不论理，则拘其一见而不验矣。且如饮食得震，则震为龙。以理论之，龙非可取，当取鲤鱼之类代之。"
      + "又以天时之得震，当有雷声，若冬月占得震，以理论之，冬月岂有雷声，当有风撼震动之类。既知已上数条之诀，复明乎理，则占卜之道无余蕴矣。";   // 卷之二·占卜论理诀（照录）

    /** 卷之二·先天后天论（照录）。 */
    private static String JUAN_ER_XIAN_TIAN_HOU_TIAN =
        "先天后天论先天卦断吉凶，止以卦论，不甚用《易》之爻辞。后天则用爻辞，兼用卦辞，何也？盖先天者未得卦先得数，是未有《易》书，先有《易》理，"
      + "辞前之《易》也。故不必用《易》书之辞，专以卦断。后天则以先得卦，必用卦画，辞后之《易》也。故用爻之辞，兼《易》卦辞以断之也。又后天起卦，"
      + "与先天不同，其数不一。今人多以坎一、坤二、震三、巽四、中五、乾六、兑七、艮八、离九此数为用。盖圣人作《易》画卦，始以太极、两仪、四象、八"
      + "卦加一倍数，自成乾一、兑二、离三、震四、巽五、坎六、艮七、坤八，故占卜起卦，合以此数为用。又今人起后天卦，多不加时，得此一卦，止此一爻动"
      + "，更无移易变通之道。故后天起卦定爻必加时而后可。又先天之卦，定事应之期，则取之卦气。如乾、兑则应如庚、辛及五金之日，或乾为戌、亥之日时，"
      + "兑为酉日时。如震、巽当应于甲、乙及五木之日，或震取卯，巽取辰之类。后天则以卦数加时数，总之而分行卧坐立之迟速，以为事应之期。卦数时类应近"
      + "，而不能决诸远者，必合先后之卦数取诀可也。又凡占卦中决断吉凶，其理洞见，止于全卦体用生克之理，及参《易》辞，斯可矣。今日以后天卦，却于六"
      + "十甲子之日，取其时方之魁，破败亡灭迹等以助断决。盖乃历象选时，并于《周易》不相干涉，不可用也。";   // 卷之二·先天后天论（照录）

    /** 卷之二·三要灵应篇·拆字之应段（照录；相字别系，作取象备考，不注入节点 16 独立栏）。 */
    private static String SAN_YAO_CHAI_ZI =
        "若见物形，可知字体。故石逢皮则破，人傍木为休。笠漂水畔，泣字分明。火入山林，焚形可见。三女有姦私之扰，三牛有犇走之忧。一木两火，榮耀之光。"
      + "一水四鱼，鳏寡之象。人继牛倒防失脱，人言犬中忧狱囚。一斗入空门者，斗争。两丝挂白木者，樂事。一人立门，诸事有闪。二人夹木，所问必来。右为"
      + "拆字之应。";   // 卷之二·三要灵应篇·拆字之应段（照录）

    /** 卷之二·心易占卜玄机（照录；接入节点 16）。 */
    private static String juanErXinYiXuanJiNote() {
        return "卷之二·心易占卜玄机（照录）——" + JUAN_ER_XIN_YI_XUAN_JI;
    }

    /** 卷之二·占卜总诀（照录；接入节点 16）。 */
    private static String juanErZhanBuZongJueNote() {
        return "卷之二·占卜总诀（照录）——" + JUAN_ER_ZHAN_BU_ZONG_JUE;
    }

    /** 卷之二·占卜论理诀（照录；接入节点 16）。 */
    private static String juanErZhanBuLunLiJueNote() {
        return "卷之二·占卜论理诀（照录）——" + JUAN_ER_ZHAN_BU_LUN_LI;
    }

    /** 卷之二·先天后天论（照录；接入节点 16）。 */
    private static String juanErXianTianHouTianLunNote() {
        return "卷之二·先天后天论（照录）——" + JUAN_ER_XIAN_TIAN_HOU_TIAN;
    }

    /** 卷之二·三要灵应篇·拆字之应段（照录；相字别系，取象备考）。 */
    private static String sanYaoChaiZiZhiYingNote() {
        return "卷之二·三要灵应篇·拆字之应（照录）——" + SAN_YAO_CHAI_ZI;
    }

    /** 卷之二·体用总诀（照录；接入节点 16）。 */
    private static String juanErTiYongZongJueNote() {
        return "卷之二·体用总诀（照录）——" + JUAN_ER_TI_YONG_ZONG_JUE;
    }

    /** 卷之二·十八占（照录；接入节点 16）。 */
    private static String juanErShiBaZhanNote() {
        return "卷之二·十八占（照录）——" + JUAN_ER_SHI_BA_ZHAN;
    }

    /** 卷之二·复明诸应（照录；接入节点 16）。 */
    private static String juanErFuMingNote() {
        return "卷之二·复明诸应（照录）——" + JUAN_ER_FU_MING_ZHU_YING;
    }

    /** 卷之二·观物洞玄歌正文（照录；接入节点 16）。 */
    private static String juanErDongXuanGeNote() {
        return "卷之二·观物洞玄歌（照录）——" + JUAN_ER_DONG_XUAN_GE;
    }

    /** 卷之二·起卦加数例·屋宅占·器物占（照录；接入节点 16）。 */
    private static String juanErJiaShuWuZhaiNote() {
        return "卷之二·起卦加数例·屋宅占·器物占（照录）——" + JUAN_ER_QI_GUA_JIA_SHU;
    }


    // ------------------------------------------------------------------
    // 第 39 轮·卷之三·断占总诀 逐篇照录（B128–B148，共 21 篇）：
    //   此前卷之三仅「观梅数诀序／占卜克应之诀／占卜十应诀逐应」三处成篇照录，
    //   余多沉于断法片段（bianGuaShiText/ZHAN_WU_YAO_CI/GUAN_WU_YONG_YI/GUA_YING map 等转述或表型载体），
    //   成篇原文未录。今依底本逐篇照录归位（接入节点 16「卷之三断占总诀」），与断法/表型并存不悖。
    //   L 行号依底本单制；凡「又诀」等底本自有之过渡题并录。
    // ------------------------------------------------------------------
    private static String JUAN_SAN_BA_GUA_DING_YIN_YANG =
        "八卦定阴阳次序乾为父震长男坎中男艮少男坤为母巽长女离中女兑少女相生极美比和次之体用于变爻，作"
      + "动静取之。动者为用，静者为体。又此是离四卦。又此是乾九卦。此是坎三卦。此是坎八卦。此是震五卦"
      + "。此是巽六卦。此是巽七卦。此是兑一卦。此是艮二卦。";   // 卷之三·八卦定阴阳次序（乾统三男、坤统三女＋相生比和＋图象残注存目）

    private static String JUAN_SAN_BIAN_GUA_SHI_BA_ZE =
        "变卦式八则泽火革变泽山咸卦离卦初爻阳动，变阴变艮卦。兑金为少女，离火克之，巽为股，乾金克之，"
      + "曰伤股，得艮土生入兑金。断曰：不至于此。地雷复变地泽临卦木是用卦，断出软物，文章之体也，将出"
      + "是罗经。天泽履变乾卦此卦断出：是铁器之物，将出剃刀也。泽火革变火雷噬嗑卦此卦乃用爻木体，火夏"
      + "，火得旺能出土，必土物也。归妹卦变火泽睽卦用爻属木，变火体卦属金，四爻变卦，成艮土能生金，断"
      + "出是铁也。泽天夬变兑卦此卦非金，是石，断是破磁碟也。泽火革变艮卦本卦得泽火革，为少女。近物为"
      + "口，远取羊。内离为中女，近目，远取雉。初爻变艮卦为土，土能生金，则扶起兑金之妹。次除去初爻，"
      + "移上四爻，又成巽木，佐得伤股之灾。得初爻变艮土，生于兑金，是故有救而不至于死也。“近取诸身”"
      + "，八卦乾头、坤腹、震足、巽股、坎耳、离目、兑口、艮手——人身；“远取诸物”，乾马、坤牛、震龙"
      + "、巽鸡、坎豕、离雉、艮狗、兑羊——畜道。天水讼卦变兑卦天水讼卦变兑，欲要求财。盖卦是体生，而"
      + "乃泄己之气，其财空望。次得离卦属火，能克金。其日午时，客来食去酒，返自消耗也。";   // 卷之三·变卦式八则（逐例照录）

    private static String JUAN_SAN_ZHAN_GUA_JUE =
        "占卦诀又如占卦而问吉事，则看卦中有生体之卦，则吉事应之必速。便看生体之卦，于八卦时序类决其日"
      + "时。如生体是用卦，则事即成就。生体是互卦，则渐成。生体是变卦，则稍稍迟耳。若有生体之卦，又有"
      + "克体之卦，则事有阻节，好中不足。便看克体卦气阻于几日，若乾克体，阻一日，兑克体，阻二日之类推"
      + "之。如占吉事，无生体之卦，有克体之卦，则事不谐矣。无克体之卦，则吉事必可成就矣。又如占不吉之"
      + "事，卦中有生体之卦，则有救而无害；如无生体之卦，事必不吉矣。若以日期而论，看卦中有生体之卦，"
      + "则事应于生体卦气之日；有克体之卦，则事败于克体卦气之日。要在活法取用也。";   // 卷之三·占卦诀（生体迟速＋克体阻日）

    private static String JUAN_SAN_TI_YONG_HU_BIAN =
        "体用互变之诀大凡占卜，以体为其主，互用变皆为应卦。用最紧，互次之，变卦又次之。故曰用为占之即"
      + "应，互为中间之应，变为事占之终应。然互卦则分其有体之互，有用之互。如体在上，则上互为体之互，"
      + "下互为用之互；体卦在下，则下互为体之互，上互为用之互。体互最紧，用互次之。例如观梅恒卦，互兑"
      + "、乾，兑为体，互见女子折花。若乾为体，互则老人折花矣。盖兑、乾皆克体，但取兑而不取乾，此体互"
      + "用之分。大凡占卦，变卦克体，事于末后，必有不吉。变生体及比和，则事事临终有吉利。此用互变之诀"
      + "也。";   // 卷之三·体用互变之诀

    private static String JUAN_SAN_TI_YONG_SHENG_KE =
        "体用生克之诀占卦即以卦分体用互变，即以五行之理断其吉凶。然生克之理，于内卦体用互变，一定之生"
      + "克。若外卦，则须明其真生真克之五行，以分轻重，则祸福立应。何也？假如乾、兑之金为体，见火则克"
      + "，然有真火之体，有火之形色。真火能克金，形色则不能克。能克则不吉，不能克则不顺而已。盖见炉中"
      + "火、窑灶之火，真火也。烈焰巨炷，真火也。乾、兑为体，遇之不吉。若色之红紫，形之中虚，槁木之离"
      + "，日灶之火，则火之形色，非真火之体，乾、兑之体，不为深忌。又若一盏之灯，一炬之烛，虽曰真火，"
      + "微细而轻，小不利耳。又若震、巽之木体，遇金则克，然钗钏之金、金铂之金、成锭之金、杯盘之银，与"
      + "器之锡、琐屑之铜铁，皆金也。此等之金，岂能克木？木之所忌者，快刀锐刃，巨斧大锯。震、巽之体，"
      + "值之始有不吉。又若离火为体，见真水能克。然但见色之黑者，见体之湿者，与夫血之类，皆坎之属，终"
      + "忌之而不深害也。余卦为体，所值外应，克者皆以轻重克之。若夫生体之卦，亦当分辨。土与瓦器皆坤土"
      + "，金遇之，土能生金，瓦不能生也。树木柴薪，皆木也，离火值之，柴薪生火之捷，树木之未伐者，生火"
      + "之迟也。木为体，真水生木之福重，如豕如血虽坎之属，生木之类轻也。其余五行生克，并以类而推之。";   // 卷之三·体用生克之诀（真生真克轻重）

    private static String JUAN_SAN_TI_YONG_SHUAI_WANG =
        "体用衰旺之诀凡体卦宜乘旺，克体之卦宜衰。盖体卦之气，如春木、夏炎、秋金、冬水，四季之月土，此"
      + "得令之卦，乘旺之气，虽有他卦克之，亦无大害。用互变卦，乘旺皆吉，但不要克体之卦气旺。而体卦体"
      + "衰，是不吉之占。占者有此，若问病必死，问讼必败。若非问讼与病而常占，则防有官病之事。未临其期"
      + "，在于克体卦气之月日也。若卦体旺而复有生体之卦，吉事之来，可刻期而至矣。若内卦外卦有生体者众"
      + "，体卦虽衰，亦无大害也。内外并无生体，虽体之卦党多，皆是衰卦，终不吉也。故体用之卦，必须详其"
      + "盛衰也。";   // 卷之三·体用衰旺之诀

    private static String JUAN_SAN_TI_YONG_DONG_JING =
        "体用动静之诀占卦体用互变既分，必以内外之卦察其动不动。不动不占，亦不断。其吉凶悔吝，生乎动也"
      + "。夫体卦为静，互卦为静，用卦变卦则动也，此内卦之动静也。以外卦言之，方应之卦，天时地理之卦，"
      + "应皆静，若人事之应，器物之类，则有动者矣。器物本静，人持其器物而来，则动矣。若乾马、坤牛，皆"
      + "动者矣。盖水之井沼，土之山岩石，皆静者矣。人汲水担水而前，水之动也。又人持石负土而前，土之动"
      + "也。于外卦之应，观其动静而审其吉凶。动而吉者，应吉之速；动而凶者，应凶之速；不动而应者，吉凶"
      + "之未见也。此则外卦体用之动静也。若夫起卦之动静，亦以我之中静而观其动者而占之。如雀之争坠，如"
      + "牛鸡之哀鸣，如槁木之坠，皆物之动者，我以静而占之也。又若我坐，则事应之迟；我行而事应之速；我"
      + "立而半迟半速，此皆动静之理也。";   // 卷之三·体用动静之诀

    private static String JUAN_SAN_ZHAN_BU_ZUO_DUAN =
        "占卜坐端之诀坐端者，以我之所坐为中，八位列于八方，占卜决断之。须虚心待应，坐而端之，察其八卦"
      + "八方应兆，以为占卜事端之应。随其方卦有生克之应者，以定所占之家吉凶也。如乾上有土生之，或乾宫"
      + "有诸吉兆，则尊长老人分上见吉庆之事。若乾上有火克之，或有凶兆，则主老人长上有忧。坤上有火生之"
      + "，或坤上有吉兆，则主母亲分上或主阴人有吉利之喜。坤宫见克，或有凶兆，则主老母阴人有灾厄。震宫"
      + "有水生之，及东方震宫有吉兆，则喜在长子长孙。见克而或见凶，则长子长孙不利。坎宫宜见五金及有吉"
      + "利之谶，则喜在中男之位。若土克，若见凶，则忧在中男矣。离宫喜木生之，或有可喜之应，则中女有喜"
      + "。若遇克或见凶，则中女有厄矣。艮为少男之位，宜火生之，见吉则少男之喜。若遇克，或见凶，则灾及"
      + "少男。问产必不育矣。兑为少女，土宜生之，见吉则少女有喜，或有欢悦之事。若问病，如乾卦受克，病"
      + "在头，坤宫见克，病在腹，推之震足、巽股、离目、坎耳及血、艮手指、兑口齿，于其克者定见其病。至"
      + "于八端之中，有奇占巧卜者，乃在乎人。此引其端为之例也。";   // 卷之三·占卜坐端之诀

    private static String JUAN_SAN_WAN_WU_FU =
        "万物赋人禀阴阳，卦分先后。达时务者，近取诸身，远取诸物。观物理者，静则乎地，动则乎天。原夫万"
      + "物有数，易算无穷。动静可知，不出于玄天之外。吉凶必见，莫逃乎爻象之中。未成卦以前，必虚心而求"
      + "应；既成卦以后，复观刻应以用断。声音言语，傍人谶兆，当遇形影往来，我心指实皆是。及其六爻以定"
      + "，三天既生，始寻卦象之端，终测刻应之理。是以逢吉兆而终知有喜，见凶谶而不免乎凶。故欲知他人家"
      + "之事，必须凭我耳目之闻见。未成卦而闻见之，乃已行之事。既定卦而观察之，乃未来之机。或闻何处喧"
      + "闹，主有斗争；或听此间笑语，必逢吉庆；儿妇啼叹，其家阴小有灾；吏至军来，必有官司词讼；或逢枷"
      + "锁而枷锁临身；倘遇鞭杖而鞭杖必至。设若屠而负肉，此为骨肉有灾；倘逢血光，而又恐灾于孳畜。师巫"
      + "药饵，病患临门。见饭则有犯家先，逢酒则欠神愿。阴人至则女子有厄，阳人至则男子当灾。又须八卦中"
      + "分，不可一例而论。卦吉而爻象又吉，祸患终无；卦凶而谶兆又凶，灾殃难免。披麻带孝，必然孝服临头"
      + "；持杖而号，定主号泣满室。其人忧终是为忧，其人喜还须有喜。故当观色察形，以为决意断心。其或鼓"
      + "乐声喧，又见酒杯器皿，若不迎婚嫁娶，定须会客宴酣。欲知应在何日，须观爻象值数。巽五日而坤八日"
      + "，离三朝而坎六朝。又观远近克应，以断的实之相期。应远，则全卦相同；应近，而各时同断。假如天地"
      + "否卦，上天一而下地八；设若泽火革卦，上兑二而下离三。依此推之，万无一失。此人物之兆，察之可推"
      + "也。及其鸟兽之应，仍验之有准。鹊噪而喜色已动，鸦鸣而祸事将临。牛羊猪犬，日晨不见，金日遇之，"
      + "六畜有损。木日见猪，养猪必成。庚日见鸡鸣，丁日见羊过，此乃凶刃之杀。己日值马来，壬日有猪过，"
      + "此皆食禄之兆。见吉兆而百事亨通，逢凶谶而诸事阻滞。或若求财问利，须凭克应以言。柜箱为藏财之用"
      + "，绳索为穿钱之物。逢金帛宝货之类，理必有成。遇刀刃剑具之器，损而无益。又看元卦，不可执一。逢"
      + "财而有财，无财而无益。凡物成器，方系得全，缺损破碎，有之不足。或问婚姻，理亦相似。物团圆，指"
      + "日而成；物破损，中途阻折。此又是一家闻奥。斯理明，万事昭然。逢柴炭主忧，折麦主悲。米必奇，豆"
      + "必伤。禾与鞋，万事和谐；棋与药，与人期约。斧锯必有修造，粮储必有远行。闻禽鸣，谋事虚说。听鼓"
      + "声，交易空虚。拭目润睫，内有哭泣之事；持刃见血，外面有虫毒之谋。克应既明，饮食同断。见水为饮"
      + "食酒汤，遇火为煎炮烙炙。见米为一饭之得，提壶为酌杯之礼。水乃鱼虾水中物味，土乃牛羊土内菜蔬。"
      + "姜面为辛味辣羹，刀砧乃薰腥美味。此三天之克应，万物之枢机。能达此者，尚其秘诸。";   // 卷之三·万物赋

    private static String JUAN_SAN_YIN_SHI_PIAN =
        "饮食篇夫乾之为象也，圆坚而味辛，取象乎卯，为牲之首，为马为猪，秋得之而食禄盛，夏得之而食禄衰"
      + "。春为时新之物，果蔬菜之属；冬为冷物，隔宿之食。有坎乃江湖海味，有水而蔬果珍羞。艮为土物同烹"
      + "，离乃火边煎炙。秋为蟹，春为马。凡内必多肉，其味必辛，盛有瓦器，伴有金樽。其于菜也为芹，其于"
      + "物也带羽。克出生回，食必鹅鸭。生出克入，野菜无名。其于坤也，远客至，故人来，所用必瓦器，所食"
      + "米果之味。静则梨枣茄芋，动则鱼虾鲜羊。无骨肉脯，亦为腌藏，亦为肚肠。遇客必妇人，克此必主口舌"
      + "。克出生回，乃牲之味；克入生物，乃杂物之烹。见乾、兑，细切薄披；见震、巽而新生旧煮。其色黑黄"
      + "，其味甘甜，水火并之，蒸炊而已。四时皆为米麦之味，必带麻姜。仔细推详，必有验也。巽之为卦，主"
      + "文书柬约之间，讲论之际，外客婚姻，故人旧交。或主远信近期。其色白青，其性曲直，其味酸，其象长"
      + "。桃李木瓜，斋辣素食，为鱼为鸡，其豆其面，非济执而得之，必锄掘而得之。有乾、兑，食之而致病，"
      + "有坤，得之非难。炊为炒菜蔬，离为炒茶，带坎于中，酒汤共食。其无生，半斋半薰。其在艮也，会邻里"
      + "，有贵人。食物不多，适口而已。其桔柚菜果蔬，斫伐之山林带节，虎狗兔鹿，渔捕网罗，米麻面麦。克"
      + "入杂食，克出羊肉。克入口舌，是非阴灾，极不可食。其味甘甜，其色玄黄。坎为水象也，水近信至海内"
      + "，味香有细鳞，或四足。凡曰水簇，必可饮食也。或闻箫鼓之声，或在礼乐之所，其色黑，其味咸。克出"
      + "饮酒，生回食鱼。为豕为目，为耳为血。羹汤物味，酒食水酱。遇离而说文书，逢乾而为海味。震之为卦"
      + "，木属也。酒友疏狂，虚轻怪异。大树之果，园林之蔬。其色青而味酸，其数多，会客少。或有膻臭之气"
      + "，或有异香之肴。同离多，主盐茶；见坎，或为盐醋。离则文书交易，亲戚师儒，坐中多礼貌之人，筵上"
      + "总英才之士。其物乃煎煿炙烧，其间或茶盐。白日之夕，虽之以烛。春夏之际，凡物带花，老人莫食，心"
      + "事不宁。少者宜之。宜讲论，即有益。为鸡为雉，为蟹为蛇，色赤味苦，性热而气香。逢坎而酒请有争，"
      + "逢巽则炒菜而已。兑之为卦，其属白金，其味辛而色白。或远客暴至，或近交争来。凡动物刀砧，凡味必"
      + "有辛辣，凡包里腌藏。其于暴也，为为菱；其于菜也，为葱为菲。盛而有腥臭，旺而有羊鹅。坐间有僭越"
      + "之人，或有歌娼之女。单则必然口舌，重则必然欢喜。生出多食，克出好事。夫算其饮食，必须察其动静"
      + "。故动则有，静则无。以体卦下卦为己卦，上为人卦。下为变为客，互之上为酒，下为食物。取象体之下"
      + "为食何物，变为客体，下食之不终，生体下吉。互客体之不得食。他人克应亦难食。他人生，他人请。己"
      + "生体生下，己请人。互受生后不计杯杓。上体受生客不计数。变生互，客有后至者；互生克，有先去者。"
      + "取其日时，以互卦用矣。";   // 卷之三·饮食篇

    private static String JUAN_SAN_GUAN_WU_XUAN_MIAO_GE =
        "观物玄妙歌诀观物戏验者，虽云无益于世，学者以此验数，而知圣人作《易》之灵耳。物之于世，必有数"
      + "焉。故天圆地方，物之形也；天玄地黄，物之色也；天动地静，物之性也；天上地下，物之位也；乾刚坤"
      + "柔，物之体也。故乾之为卦，刚而圆，贵而坚，为金为玉，为赤为圜，为大为首，为木之果。物见兑为毁"
      + "折，逢坎而沉溺，见离为炼煅之金，震为有动之物，巽为木果为圆，坤、艮土中之石，得火而成器。兑为"
      + "剑锋之锐，秋得而价高，夏得之而衰矣。坤之为卦，其形直而方，其色黑而黄。为文为布，为舆为金。其"
      + "物象牛，其性恶动。得乾乃可圆可方，可贵可贱。震、巽为长器，离为文章，兑为土中出之金，艮为带刚"
      + "之土石也。震之为卦，其色玄黄而多青，为木为声，为竹为萑苇，为蕃鲜及生形。上柔下刚，是性震动而"
      + "可惊。得乾乃为声价之物，得兑为无用之木，见艮山林间之石，见坎有气之类，巽为有枝叶，见离为带花"
      + "。巽之为卦，其色白，其气香。为草木，为刚为柔。见离为文书，见兑、乾为不用，乃遇金刀之物。坤、"
      + "艮为草木之类，坎、兑为可食之物。为长为直，并震而春生夏长，草木之果蔬。坎之为卦，其色黑，亦可"
      + "圆可方物。为柔为腐，内则刚物。得之卑湿之所，多为水中之物。见乾亦圆，见兑亦毁。又乃污湿，得震"
      + "、巽而可食。离、水火既济，假水而出，假火而成。又为滞于物，兑为带口也。震、巽为带枝叶，为带花"
      + "也。离之为卦也，其色黄而青，体燥，其性则上刚下柔。为山石之物，土瓦之类，小石于大山，为门途之"
      + "处。为物见乾而刚，兑而毁折，坤而土块，巽为草之物，而震为木物类也。坎并为河岸之物，离并为瓦器"
      + "，震、巽并见篱壁之物。兑之为卦，其色白，其性少柔而多刚。为毁折而下，全带口而圆。见乾先圆后缺"
      + "，见艮则金石废器，见震为剥削之物，见坎为水之类。得乾而多刚，得坤而多柔。长于西泽之内，于水中"
      + "之类，得柔而成器也。";   // 卷之三·观物玄妙歌诀

    private static String JUAN_SAN_ZHU_SHI_XIANG_YING_GE =
        "诸事响应歌混沌开辟立人极，吉凶响应尤难避。先贤遗下预知音，皇极观梅出周易。玄微浩瀚总无涯，各"
      + "述繁言人莫记。大抵体宜用卦生，旺相谋为终有益。比和为吉克为凶，生用亦为凶兆矣。问雨天晴无坎兑"
      + "，亢旱言之终则是。天时连雨问晴明，艮离贲卦响应耳。乾明坤晦巽多风，震主雷霆定莫疑。凡占人事体"
      + "克用，诸事亨通须有幸。比和为妙克为凶，又看其中何卦证。乾主公门是老人，坤遇阴人田土应。震为东"
      + "方或山林，巽亦山林蔬果品。坎为北方并水姓，酒货鱼盐才取定。离言文书炉冶利，亦曰南方颜色赤。艮"
      + "为东北山林材，兑曰西方喜悦是。生体克体亦同方，编记以为诸事应。凡问家宅体为主，旺相须知进田土"
      + "。生用须云耗散财，比和家世安居处。克体为凶决断之，生产以体为其母。两宜生旺不宜衰，奇偶之中察"
      + "男女。乾坎为阳坤为阴，又看来人爻内取。阴多生女阳生男，此数分明具易理。婚姻生用必难成，比和克"
      + "用大吉利。若问饮食用生体，必知肴馔丰厚喜。生用克体饮食难，克用必无比和美。坎兑为酒震为鱼，八"
      + "卦推求衰旺取。求谋称意是比和，克用谋为迟可已。求名克用名可求，生体比和俱可取。求财克用曰有财"
      + "，生体比和俱称意。交易生体及比和，有利必成无后虑。出行克用用生体，所至其方多得意。坎则乘舟离"
      + "旱途，乾震动则坤艮止。行人克用必来迟，生体比和人即至。咸远恒迟升不回，艮阻坎险君须记。若去谒"
      + "人体克用，此和生体主相见。兑主外见讼不亲，乾利大人长者是。来问失物体克用，速可追寻依卦断。相"
      + "生比和终可寻，兑临残缺并井畔。离为冶炉及南方，坤主方器凭推看。疾病最宜体旺相，克用易安药有效"
      + "。比和凶则有救星，体卦受克为凶兆。离宜服热坎服冷，坤土卦温补料亨。亦把鬼神卦象推，震主娇怪为"
      + "状貌。巽为自缢并锁枷，坤艮落水及血衄。凡占公讼用宜克，体卦旺相终得理。比和助解最为奇，非止全"
      + "仗他人力。若问墓穴在何地，坤则平阳巽林里。乾宜高葬艮临山，离近人烟兑兴废。比和生体宜葬之，克"
      + "用尤为大吉利。若人临问听傍言，笑语鸡鸣亦吉美。美物是为祥瑞推，略举片言通万类。";   // 卷之三·诸事响应歌

    private static String JUAN_SAN_ZHU_GUA_FAN_DUI =
        "诸卦反对性情乾刚坤柔反其义，比卦欢欣师忧虑。临逢与物观求之，蒙卦难明屯不失。大畜其卦福之生，"
      + "无妄若遇祸之始。升者去而不复回，萃者聚而终不去。谦卦自尊豫怠人，震卦动而艮则止。兑主外遇巽内"
      + "藏，随前坎后偷安矣。剥体消烂复自生，蛊改前非而已矣。明夷内朗又逢伤，晋主外明并通理。益拟茂盛"
      + "损象衰，咸速恒迟涣远遁。同人内亲睽外疏，解卦从容蹇难启。离文美丽艮光明，遁退回身姤相遇。大有"
      + "曰众丰曰多，坎卦履险震卦起。需不进兮讼不宁，既济一定无后虑。未济之卦男之终，归妹之辞妇之始。"
      + "否遭大往而小来，泰卦大来而小去。革去旧故鼎从新，小畜曰寡噬嗑食。旅羁其外大过颠，夬卦分明曰快"
      + "利。要将字字考精详，杂卦性情反对是。";   // 卷之三·诸卦反对性情

    private static String JUAN_SAN_ZHAN_WU_LEI_LI =
        "占物类例凡看物数，看其成卦，观其爻辞。如得乾，曰“潜龙勿用”，乃曰不可用之物；“见龙在田”，"
      + "乃曰田中之物；“或跃在渊”，乃曰水中之物；“亢龙有悔”，乃废物也。如得坤之“直、方、大”，乃"
      + "曰直而方大之器物；“括囊无咎”，乃曰包裹之物；“黄裳元吉”，乃曰黄色衣服之物，“其色玄黄”。"
      + "“困为石”，乃曰石物或逢石而破；“困于株林”，乃曰木物。又言爻辞，不言物类，而不能决者，须以"
      + "八卦所属之象察之。又诀";   // 卷之三·占物类例

    private static String JUAN_SAN_TI_YONG_DUAN_WU =
        "体用断物之妙生克制化之妙，于诸诀中此诀极为美验。其所诀以生体者，为可食之物；克体者，为可近人"
      + "之秽物。体生者，为不成之器；体克者，为破碎损折之物；比和者，乃有用成器之物。又生体象者为贵物"
      + "，克体象者为贱物，所泄为废物也。又诀凡算此数，以体卦为主，看其刚柔。用卦看其有用无用。体生方"
      + "圆曲直，可作可用，如用生体，乃可食。用变互卦，看其色与数目。此互卦决其物之数目也。如互见重乾"
      + "、兑，决为一二之数。互见艮、坤，为七八之数也。但互卦重乾、重艮、重坤、重坎、重离之属，皆是两"
      + "件。物乘旺，物数又多，衰而物少。离为中虚之物，或空手无物。又决物之数者，如互艮卦，先天乃七数"
      + "，后天亦不出八数之外。";   // 卷之三·体用断物之妙

    private static String JUAN_SAN_WU_SHU_WEI_TI =
        "物数为体诀凡算物数者，不但以体卦为体，凡卦之多者，皆可为体。如乾金多，以金为体，则多刚；坤多"
      + "以土为体，多柔。乾卦体卦乾而用是乾，固曰金为体而刚矣，便是圆健刚硬之物。非金非石，此为体矣。";   // 卷之三·物数为体诀

    private static String JUAN_SAN_GUAN_WU_ZHU_JUE =
        "观物有体互变卦，并无生旺之气者，为不入五行之物。观物观爻，如八卦中阳爻多，乃多刚之物；阴爻多"
      + "，乃多柔之物。又诀观物变在五六爻，多是能飞动之物。观物看变爻为主凡观物，以变卦为主，应用之应"
      + "验也。如得乾，初爻变为巽，金刀削过木之物。二爻动，变为离，乃火中煅炼之金。三爻动，变为兑，乃"
      + "毁折五金之器，虽圆而破处多也。观物克应法凡算物之成败，又看体卦克应如何。成卦未决之际，有见圆"
      + "物相遇，即断是圆物。见有负土者过，即断为土中之物。见刚健之物，即言是刚健之物。见有柔腐之物，"
      + "即断是柔腐之物。观物趣时诀凡算物，趣时察理，无有不验。以春得震、离为花，夏得震为有声之物，秋"
      + "得兑为毁折成器之物，冬得坤为无用土物也。观物用易例有人以笼盛物者，算得地天泰之初变升，互见震"
      + "、兑，曰：此必是草木类而生土中也。色青根黄，当连根之草木也。盖爻辞曰：“拔茅连茹，以其汇。”"
      + "乃曰：此乃干根之草木也。视之乃草木连根，新采于土中也。互震为青色，兑为黄根也。又有以钟覆物者"
      + "，令占之，得火风鼎之雷风恒。乃曰：此有声价气势之物，虽圆而今毁缺矣。其色白而可用。盖其辞曰："
      + "“鼎玉铉，大吉。”互见乾、兑，虽圆而毁也。开视之，乃玉绦环，果破矣。";   // 卷之三·观物诸诀（有体互变/看变爻/克应法/趣时诀/用易例）

    private static String JUAN_SAN_WAN_WU_XI_NIAN =
        "万物戏念数中不可常为之凡猜手中物，乾金为圆白之物。其色白，其性刚，为宝货之物，有气为无价物。"
      + "坎为黑色，性柔，近水之物。又艮为土中之物，瓦石之类，有气为成器之物，其色黄。逢兑克，无气，折"
      + "伤之物。又巽、震为竹木，有气为有用之物，为可食之物，无气为竹木之属。遇兑之属可食，当时之果物"
      + "，色青。有气柔，无气刚。震、巽遇坎为污湿物，或有气；如无气，为烂朽之木。离色赤，性柔，有水有"
      + "木，而火焚之，必炭之类。有气，为价值可货之物。坤为土中之物，色黄而性温。兑为毁折之物，带口。"
      + "凡占物，以春震巽、夏离、秋乾兑、冬坎，皆当以为可用之物、成器之物。否则为无用之物。值六虚冲破"
      + "，必无物而空手矣。";   // 卷之三·万物戏念

    private static String JUAN_SAN_ZHAN_BU_SHI_YING_JUE =
        "占卜十应诀凡占卜，以体卦为主，用为事应，固然矣。但体卦既为主，用互变卦相应，参看祸福。然今日"
      + "得此一卦，体用互变中决之如此；明日复得此卦，体用一般，岂可又复以此决之？然则若何而可？必得十"
      + "应之说而后可也。盖十应之说，有正应、互应、变应、方应、日应、刻应、外应、天时应、地理应、人事"
      + "应，所谓十应也。若夫正应者，正卦之应也。互应者，互卦之应也。变应者，变卦之应也。此三卦之诀也"
      + "。卜者俱用之以断吉凶矣。至于诸应之理，人有不知者，故必得诸用之诀，卦无不验。不得其诀而占卜吉"
      + "凶，或验或不验矣。得此诀者，宜秘之。正应正应者，即体用二卦决吉凶。互应互应者，即互卦中决吉凶"
      + "。变应变应者，即变卦中决吉凶。方应方应者，以体为主，看来占之人在何方位上，即看其所坐立之方位"
      + "。卦宜生体卦，又宜与体比和，则吉；如克体卦则凶；如体卦生之，亦不吉矣。日应日应者，以体卦为主"
      + "，看所占卦日属何，及体卦与本日衰旺如何。盖卦宜生体，宜比和；不宜克体，亦不宜体卦生之也。本日"
      + "所属卦气，如寅卯木、巳午火、申酉金、亥子水、辰戌丑末土也。刻应刻应者，即三要之诀也。占卜之顷"
      + "，随所闻所见吉凶之兆以为吉凶之应也。外应外应者，外卦之应也。占卜之际，偶见外物之来者，即看其"
      + "物属何卦。如火得离、水得坎之类。如见老人、马、金、玉、圆物，得乾。见老妇、牛、土瓦物，得坤之"
      + "类。又如见此者，为外应之卦。并看其卦与体卦生克比和之理，以决吉凶。天时应天时之应，占卜之际，"
      + "晴明为离，雨雪为坎，风为巽，雷为震。故离为体，宜晴。坎为体，宜雨。巽为体，宜风。震为体，宜雷"
      + "。火见雷为比和，参之生克，以定吉凶。地理应地理之应，凡占卜之时，偶在竹林间，为震、巽之地；在"
      + "江河溪涧池沼之上为坎；在五金之处为乾、兑之乡；在窑灶炉火之所为离；在土瓦之所为坤、艮，并为体"
      + "卦，论生克比和之理以决之。人事应人事之应，即三要中人事之克应也。盖占卜之际，偶遇人事之吉为吉"
      + "，偶遇人事之凶为凶。如闻笑语，主有吉庆之事；遇哭泣，主有悲愁之事。又以人事之属于卦者论之：老"
      + "人为乾，老妇为坤，少男为艮，少女为兑。并看此人事之卦与体卦生克比和，以决吉凶。右十应之理，凡"
      + "占卜之际，耳闻目见以决吉凶，并以体卦为主，而详其生克比和之理。如占病症，互变中多有克体之卦，"
      + "而本卦中又无生体之卦者，断不吉也。又看体衰旺，若体旺则庶几有望，体衰则无复生理。如是，又看诸"
      + "应有生体者，险中有救；有克体则不可望安矣。其余占卜，并以类推之。";   // 卷之三·占卜十应诀全篇（序头＋正/互/变/方/日/刻/外/天时/地理/人事应＋右十应之理）

    private static String JUAN_SAN_LUN_SHI_SHI_DA_YING =
        "论事十大应（论日辰秘文）一行：问官司，属木，旺木有文书；属火，有官司；财金木，财有至。有客至"
      + "问病，人火潮热，金水米浆。二立：官司不发，水土无金，大小口舌，病不凶。财水土，有贵人至，文书"
      + "发动。三坐：问官司，有讼不成。主财属火，主和劝。金败财，木得财。病脚，右足犯林木神，有祸不凶"
      + "。四卧：问官司，侧睡者，欲起必作，主阴人事。金有财，火事发破财。土水无财难就。土木有财。五担"
      + "：官司被人自惊，与面说人成口舌。问信见水土得财。金木客至。病有犯，四肢沉重不能起。六券：官司"
      + "不成，火有财，水土有灾，心下不安，有贵人，主口舌，不凶。七裹头：官司立见口舌。火，大官司，水"
      + "土比和，财无，小人分上，口舌呕气。病主阴人小口灾。八跣足：官司破财，外人欺，心下惊慌。火主破"
      + "财，土不凶。病有孝至。九喜：官司自己无主，外人有请，劝官司。有酒肉，别人事。口舌纷纷，求财不"
      + "许。不凶。十怒：官司主外人欺凌，不见官，主破财，倚人脱卸。火惊病凶。";   // 卷之三·论事十大应（论日辰秘文）

    private static String JUAN_SAN_GUA_YING =
        "卦应（与前八卦属类大同小异，观者可以互参。）乾为天、为圜、为君父、为首、为金、为玉、为寒、为"
      + "冰、为大赤、为马、为良马、为老马、为瘠马、为驳马、为木果。（《九家易》云：“为龙、为直、为衣"
      + "、为言。”）如姤、遁、否、履、无妄、讼、同人七卦，乾在上，刚于外。如大有、泰、大壮、夬、需、"
      + "大畜、小畜七卦，乾在下，刚于内。乾坤刚柔，四发变八，惟六动随时有异，不拘于一。乾性温而刚直，"
      + "偏位西北，不居子午而居戌亥。附于礼法，则为刚善，为明；不附于礼法，则为刚恶，为凶暴。［天文］"
      + "：雪、老阳。［天气］：寒。［凶盗］：军、弓手、贼、强横、停尸。［官贵］：朝贵、盐司、太守、座"
      + "主。［身体］：顶、面颊、辅。［性情］：刚健正直、尊重、好高、战吉。［声音］：正清、商。［信音"
      + "］：朝信改、召命、荐举、关升、义亲。［事意］：上卦为形象之家、下卦为强横之辈。［疾病］：手太"
      + "阳脉弦紧、天威所发上壅目热、寒热。［附药］：丸子。［食物］：饼子之赤者、手饼、馒头、荷包、羹"
      + "、猪头脑骨头、珍粉、馄钝。［谷果］：粟、栗、瓜、豆、龙眼、荔。［禽兽］：雀、鹇、鹗、鹏鹰（余"
      + "备载前）。［衣服］：赤玄色。［器用］：圆物盏、注子盘、水晶、玉环、定器、球。［财］：恩义交货"
      + "、钱马之类。［禄］：壬、申。［字］：方圆形字，有头者，须旁八卦。［策］：二百一十六。［轨］："
      + "七百六十八。坤为地、为母、为布、为釜、为腹、为吝啬、为均、为牛、为子母牛、为大舆、为文、为众"
      + "、为柄。其于地也，为黑。坤上体柔，外于六卦，柔在下，柔在内。坤，厚位居，偏在西南申上。附于理"
      + "法则为圣贤，否则为邪荡。［天文］：雾、露、云、阴。［地理］：郡国、宫阙、城邑、墙壁。［人物］"
      + "：母、妻、儒、农、僧。［凶盗］：奴婢藏在僻处。［官贵］：大臣、教官、考校文字。［生育］：女、"
      + "肥厚。［性情］：顺缓不信事、顽钝无慈爱。［声音］：宫音。［事意］：迟滞、顽懦、悭吝、从容。［"
      + "疾病］：手、太阴侯、腹痛、脾胃闭、脉沉伏。［饮食］：藜羹、烧熬冻物、鹅、鸭、肺、太牢饮食、饴"
      + "糖。［五味］：苦、辣、甘。［果品］：取物件。［音信］：顺遂、可许为捷应、辰戌丑未月日。［财物"
      + "］：束修、抄题、僧衣、布裳。［婚姻］：富家、庄家、商家、丑拙、性吝、大腹、壮、迟钝、面黄。［"
      + "器用］：轿、车、瓦器、田具、沙器。［禽兽］：牛、牝马、鸥、雀、鸦、鸽。［字］：圭、金、四、牛"
      + "旁。［禄］：癸、酉。［策］：一百四十四。［轨］：六百七十一。震为雷、为龙、为玄黄、为旉、为大"
      + "涂、为长子、为足、为决躁、为苍筤竹，为萑苇。其于马也，为善鸣、为足、为作足、为的颡。其于稼也"
      + "，为反生。其究为健、为蕃鲜。（《九家易》云：“为王、为鹄、为鼓。”）春夏性严刚直，众所饮服；"
      + "秋冬刚而不威，不能制物。不好闲付，性偏而偶。附于理，则为威严；否则为躁暴。体用上卦为飞，下卦"
      + "为走。［天文］：雷、虹霓、电。［地理］：屋市宅、门户枋。［方所］：正东。［人物］：商旅、将帅"
      + "、工匠。［凶盗］：东去、男人盗。［官贵］：监司、郡守、刑幕、巡检、法官。［生育］：长男、转动"
      + "、虚惊、怪异。［性情］：始刚，故决断；急于动，故躁。［婚姻］：官宦家、技巧工、女容心神好、动"
      + "静易转。［声音］：上下角、上平声、三音七声。［信音］：所许不至。［事意］：旧事重叠、有名无实"
      + "。［病疾］：气积冷伤胃、四体劳倦、温冷伤食。［药］：足太阳、脉洪浮。［宴会］：酒会、玩赏、期"
      + "集。［食物］：面食、包子、酒、时新之物。［谷果］：芋、小豆、稼、时新之果。［禽兽］：蜂、蝶、"
      + "白鹭、鹤。［器用］：木器盘、竹器筐、算盘、舟车、兵车、轿、器皿、瓶盏瓯、乐器、鼓。［衣物］："
      + "裙、腰带、缠带。［缠带］：绳、匹帛、青玄黄之。［财］：阴人取索竹木钱。［禄］：庚。［字］：走"
      + "、竹旁，立画偏。［色］：青、玄、黄。［策］：一百六十八。［轨］：七百四。巽为风、为木、为长女"
      + "、为绳直、为工、为白、为长、为高、为进退、为不果、为臭、为鸡。其于人也，为寡发、为广颡、为多"
      + "白眼、为股。为近利市三倍。其究为躁卦。（《九家易》云：“为杨、为鹳。”）春夏有权，号令谋略；"
      + "秋冬刚柔不一，与物为害。巽人也，凡事敢为，不退避。巽阴，赋性偏，附于礼法，则为权谋；否则为奸"
      + "邪。［天文］：风。［地理］：林苑、园囿。［人物］：命妇、药婆、工术女。［凶盗］：奴婢商量取去"
      + "、宜急求之。［官贵］：典狱、考校、干官、休究。［身体］：耳、目、胆、发、命、口、肢。［生育］"
      + "：长女、胎月少、莹白。［性情］：鄙野、悭吝、艰苦、号咷。［婚姻］：命妇、宗室女、委望、进退。"
      + "［声音］：角音、角仄声、三声四声上下。［信音］：召命、报捷、辟差、举状。［事意］：荐举、呈发"
      + "、申审、号令、听命。［疾病］：手足厥会和之气候三十日、脉濡弱、饮食伤胃、宿酒、痞膈、为臭、水"
      + "谷不化。［药］：草药。［宴会］：家筵、客不齐。［谷果］：麻、粉、茶。［食物］：长面、粉羹、脍"
      + "、鸡、鱼、肠、肚、酸物。下卦为鹅、鸭。［器用］：竹木草具、绳、丝弦索、乐器。［禽兽］：鸡、鹅"
      + "、鸭、鱼、善鸣之虫禽。上卦飞、下卦走。［衣物］：衣、绳、丝。［色］：青、绿、碧、白、紫色。［"
      + "财］：利市喜羹，租钱、料钱，那免。［禄］：辛。［字］：草、木、竹旁。丝，鱼、菜，舟，齿、足疾"
      + "，大豆，辣。［策］：百九十二。［轨］：七百三十六。坎为水、为沟渎、为中男、为耳、为豕、为隐伏"
      + "、为矫、为弓轮。其于人也，为加忧、为心病、为耳痛、为血卦、为赤。其于马也，为美脊、为亟心、为"
      + "下首、为薄蹄、为曳。其于舆也，为多眚、为通、为月、为盗。其于木也，为坚多心。春夏性险，不顾危"
      + "亡，为事多暴；秋冬性静，先难后易，有谋略，有胆志。坎险，维心亨内，主坎陷，赋性而居北。坎之体"
      + "，为隐伏之物、水中之物。附于理法为刚，否则为险陷。［天文］：月、虹、云、霜。［地理］：海阔、"
      + "水泉、沟渎、厕。［方所］：正北、丘墓中、狐兔穴中。［人物］：僧道。［凶盗］：乘便而来，脱头露"
      + "尾，易败必获。［官贵］：漕运、钱粮、漕官运属。［身体］：发、膏、血。［生产］：难产、中男、清"
      + "秀。［性情］：心机阴险、智随圆、委曲。［婚姻］：富家、酒家、亲家用性。［声音］：羽中上卦、羽"
      + "平六声下卦。［信音］：反复犹豫、小人欺诈、佞、狡狯、盗贼、狱讼。［疾病］：足太阴之气、脉滑芤"
      + "。［附药］：补肾药、或酒水下。［食物］：酒、咸物、豕、鱼、海味、中硬而核、腰子。［谷果］：麦"
      + "、枣、梅、李、桃、外柔内坚、有核。［禽兽］：鹿、豕、象、豚、狐、燕、螺。［器用］：酒器、车轮"
      + "、败车。［衣物］：青黑色。［财］：争讼之财和合打偏财。［字］：两头点水、全水、月、小弓之属。"
      + "［禄］：戌。［色］：黑皂、白。［策］：百六十八。［轨］：七百零四。离为火、为日、为电、为中女"
      + "、为甲胄、为兵戈。其于人也，为大腹、为目。为乾卦、为雉、为鳖、为蟹、为蠃、为蚌、为龟。其于木"
      + "也，为科上槁。（《九家易》云：“为牧牛。正洙作牝牛。”）春夏性明，文彩有断：秋冬晦而不明，始"
      + "终不决。离，丽也。明察于心，赋性直而居正南。附于理法，则为文明；否则为非也。［天文］：日、霞"
      + "、电、晴。［地理］：殿堂、中堂、檐、厨灶。［方所］：正南。［人物］：为将帅、兵、戈、甲之士。"
      + "［凶盗］：妇人盗，从南方去。［官贵］：翰苑、教官、通判，任官南方。［身体］：三焦、小肠、目、"
      + "心。［生育］：次女、多性燥啼哭。［性情］：聪明、见事明了。［信音］：朝信、文书、报捷、契券。"
      + "［事意］：忧疑、聒拓、喧哄、性急、虚忧。［疾病］：手足二君太阳明三相火、眼病、气燥、热疾、发"
      + "狂。［禽兽］：凤有文彩、鳖、螺、蚌、蟹、螯、蛤、蠃、鹑、鹤、飞鸟、牝羊。［食物］：馄饨、蟹、"
      + "鳖、蚌、介虫之属、中虚物、炙煎物。［谷果］：谷实粱藕、外坚内柔之物、棘木之花叶枯枝。［器用］"
      + "：灯火之具、外坚内柔之物、屏幕、帘、旗帜、戈兵、甲胄、盘、甑、瓶缴壁一应中虚之物、窑灶、炉冶"
      + "、盒子、瓮、笼。［衣物］：赤红、紫色。［财］：远旧取索、意外之物。［字］：火、日旁。［禄］："
      + "巳。［策］：百九十二。［轨］：七百六十三。艮为山、为少男、为手、为径路、为小石、为门阙、为果"
      + "蓏、为阍寺、为指、为狗、（《汉》中作豹、熊、虎之子。）为鼠、为黔喙之属。其于木也，为坚多节。"
      + "（《九家易》云：“为鼻、为肤、为皮革、为虎、为狐。”）春夏性禀温和好善；秋冬执滞不常，为事迟"
      + "缓。艮，止也。有刚有柔，民阳赋性偏而居东偏。附于理法，为刚直；否则为顽梗。［天文］：星、烟。"
      + "［地理］：山径、墙巷、丘园、门墙、阑、阍寺、宗庙。［方所］：东北方、艮门墙、寺。［人物］：阍"
      + "寺仆隶、官僚、保人。［凶盗］：以下所使警迹人。［官贵］：山郡、无迁转。［身体］：手指、鼻、肋"
      + "、脾、胃。［生产］：损胎、次男。［性情］：濡滞多疑、优游、内刚中软。［声音］：清上平、一音、"
      + "十二音、三声。［事意］：反复进退、去就多疑。［疾病］：手太阳、久患脾胃、股疾、脉沉伏。［附药"
      + "］：湿土石药。［宴会］：常酣、期集。［谷果］：豆、大小粟。［食物］：妆点之物、所食不一、酒浆"
      + "、杂蒸之物、冻物、杂羹、有汁物、鸭、鹅、甘味。［禽兽］：牝牛、子母牛、鹄、鹘、鸦、鹊、雀、鹜"
      + "、鸥、鼠。［器用］：轿、舆、犁具、兵甲器、陶冶瓦器、锅釜、瓶、瓮、簋、伞、钱袋、磁器、踏镫、"
      + "螺蜔、盒子、内柔外刚之物。［衣物］：黄裳、僧衣、黑皂、彩帛、袋布。［禄］：丙。［财］：旧钱、"
      + "置转货买田土，趁钱。［字］：土、牛、田旁。［策］：百六十八。［轨］：七百零四。兑为泽、为少女"
      + "、为巫、为口舌、为毁折、为附决。其于地也，为刚卤、为妾、为羔。（《九家易》云：“为堂、为辅颊"
      + "。”）春夏性说好辩，秋冬奸雄。兑，说也。邪言伪行，无所不为，随波逐流。附于理法，则和顺；否则"
      + "邪伎淫滥。［天文］：雨露、春雾、细雨、夏秋重雾、冬大雪。上为雨、下为露。［地理］：井、泉、泗"
      + "泽。［方所］：西方。［人物］：先生、客人、巫匠、媒人、牙人、少女、妾娼。［官贵］：学官、将帅"
      + "、县令、考校、乐友，赴任西方。［凶盗］：家使僮仆，藏于僻地。［身体］：口、肺、膀胱、大肠、辅"
      + "颊、舌、太阳。［生育］：少女一胎、月不足、多奇异。［性情］：喜悦、口舌、多美。［声音］：商上"
      + "、下商之浊四声。［婚姻］：平常之家、少女媚悦。［信音］：喜酉丑时日至。［事意］：唇吻、口舌、"
      + "谗谤相欺、争打妇人、暗昧。［疾病］：口痛、唇齿、咽喉、危困。［附药］：剉剂。［宴会］：讲书、"
      + "会友、请先生、吟赏。［食物］：包子、有口舌物、糖饼、烧饼、肝肺。［谷果］：栗、黍、枣、李、胡"
      + "桃、石榴。［禽兽］：羔羊、鹿、猿、虎、豹、豺、鹭、鱼。［器用］：席、铁、铜、钱、器皿、酒盏、"
      + "瓶、瓯、有口器，或损缺。［衣物］：彩。［财］：束修、含水。［禄］：丁。［字］：家、金、钩、口"
      + "旁。［色］：素白。［策］：一百九十二。［轨］：七百三十六。";   // 卷之三·卦应全篇（八纯卦说卦引文＋性情论＋天文地理…策轨表）

    /** 卷之三·八卦定阴阳次序（乾统三男、坤统三女＋相生比和＋图象残注存目）（照录；接入节点 16）。 */
    private static String juanSanBaGuaDingYinYangNote() {
        return "卷之三·八卦定阴阳次序（乾统三男、坤统三女＋相生比和＋图象残注存目）——" + JUAN_SAN_BA_GUA_DING_YIN_YANG;
    }
    /** 卷之三·变卦式八则（逐例照录）（照录；接入节点 16）。 */
    private static String juanSanBianGuaShiBaZeNote() {
        return "卷之三·变卦式八则（逐例照录）——" + JUAN_SAN_BIAN_GUA_SHI_BA_ZE;
    }
    /** 卷之三·占卦诀（生体迟速＋克体阻日）（照录；接入节点 16）。 */
    private static String juanSanZhanGuaJueNote() {
        return "卷之三·占卦诀（生体迟速＋克体阻日）——" + JUAN_SAN_ZHAN_GUA_JUE;
    }
    /** 卷之三·体用互变之诀（照录；接入节点 16）。 */
    private static String juanSanTiYongHuBianNote() {
        return "卷之三·体用互变之诀——" + JUAN_SAN_TI_YONG_HU_BIAN;
    }
    /** 卷之三·体用生克之诀（真生真克轻重）（照录；接入节点 16）。 */
    private static String juanSanTiYongShengKeNote() {
        return "卷之三·体用生克之诀（真生真克轻重）——" + JUAN_SAN_TI_YONG_SHENG_KE;
    }
    /** 卷之三·体用衰旺之诀（照录；接入节点 16）。 */
    private static String juanSanTiYongShuaiWangNote() {
        return "卷之三·体用衰旺之诀——" + JUAN_SAN_TI_YONG_SHUAI_WANG;
    }
    /** 卷之三·体用动静之诀（照录；接入节点 16）。 */
    private static String juanSanTiYongDongJingNote() {
        return "卷之三·体用动静之诀——" + JUAN_SAN_TI_YONG_DONG_JING;
    }
    /** 卷之三·占卜坐端之诀（照录；接入节点 16）。 */
    private static String juanSanZhanBuZuoDuanNote() {
        return "卷之三·占卜坐端之诀——" + JUAN_SAN_ZHAN_BU_ZUO_DUAN;
    }
    /** 卷之三·万物赋（照录；接入节点 16）。 */
    private static String juanSanWanWuFuNote() {
        return "卷之三·万物赋——" + JUAN_SAN_WAN_WU_FU;
    }
    /** 卷之三·饮食篇（照录；接入节点 16）。 */
    private static String juanSanYinShiPianNote() {
        return "卷之三·饮食篇——" + JUAN_SAN_YIN_SHI_PIAN;
    }
    /** 卷之三·观物玄妙歌诀（照录；接入节点 16）。 */
    private static String juanSanGuanWuXuanMiaoGeNote() {
        return "卷之三·观物玄妙歌诀——" + JUAN_SAN_GUAN_WU_XUAN_MIAO_GE;
    }
    /** 卷之三·诸事响应歌（照录；接入节点 16）。 */
    private static String juanSanZhuShiXiangYingGeNote() {
        return "卷之三·诸事响应歌——" + JUAN_SAN_ZHU_SHI_XIANG_YING_GE;
    }
    /** 卷之三·诸卦反对性情（照录；接入节点 16）。 */
    private static String juanSanZhuGuaFanDuiNote() {
        return "卷之三·诸卦反对性情——" + JUAN_SAN_ZHU_GUA_FAN_DUI;
    }
    /** 卷之三·占物类例（照录；接入节点 16）。 */
    private static String juanSanZhanWuLeiLiNote() {
        return "卷之三·占物类例——" + JUAN_SAN_ZHAN_WU_LEI_LI;
    }
    /** 卷之三·体用断物之妙（照录；接入节点 16）。 */
    private static String juanSanTiYongDuanWuNote() {
        return "卷之三·体用断物之妙——" + JUAN_SAN_TI_YONG_DUAN_WU;
    }
    /** 卷之三·物数为体诀（照录；接入节点 16）。 */
    private static String juanSanWuShuWeiTiNote() {
        return "卷之三·物数为体诀——" + JUAN_SAN_WU_SHU_WEI_TI;
    }
    /** 卷之三·观物诸诀（有体互变/看变爻/克应法/趣时诀/用易例）（照录；接入节点 16）。 */
    private static String juanSanGuanWuZhuJueNote() {
        return "卷之三·观物诸诀（有体互变/看变爻/克应法/趣时诀/用易例）——" + JUAN_SAN_GUAN_WU_ZHU_JUE;
    }
    /** 卷之三·万物戏念（照录；接入节点 16）。 */
    private static String juanSanWanWuXiNianNote() {
        return "卷之三·万物戏念——" + JUAN_SAN_WAN_WU_XI_NIAN;
    }
    /** 卷之三·占卜十应诀全篇（序头＋正/互/变/方/日/刻/外/天时/地理/人事应＋右十应之理）（照录；接入节点 16）。 */
    private static String juanSanZhanBuShiYingJueNote() {
        return "卷之三·占卜十应诀全篇（序头＋正/互/变/方/日/刻/外/天时/地理/人事应＋右十应之理）——" + JUAN_SAN_ZHAN_BU_SHI_YING_JUE;
    }
    /** 卷之三·论事十大应（论日辰秘文）（照录；接入节点 16）。 */
    private static String juanSanLunShiShiDaYingNote() {
        return "卷之三·论事十大应（论日辰秘文）——" + JUAN_SAN_LUN_SHI_SHI_DA_YING;
    }
    /** 卷之三·卦应全篇（八纯卦说卦引文＋性情论＋天文地理…策轨表）（照录；接入节点 16）。 */
    private static String juanSanGuaYingNote() {
        return "卷之三·卦应全篇（八纯卦说卦引文＋性情论＋天文地理…策轨表）——" + JUAN_SAN_GUA_YING;
    }

    // ------------------------------------------------------------------
    // 第 40 轮·卷之四·字画指迷 成篇照录（B149–B156，共 8 组）：
    //   卷之四「相字」术之断法（六神笔式/五行笔式/八卦断/笔画歌诀/相字短诀等）已由 putXz（263 条）
    //   与 DB `xiangzi_rule` 结构化承载（C12 相字别系）；惟**成篇之赋/歌/叙/论/经验/诗诀**（教训⑲）
    //   及未入载体之断法段（问婚姻…行人等）原文未录，今依底本成篇照录归位（接入节点 16）。
    //   L 行号依底本单制；篇末校勘记（「原本无此句，据校本补」以下）为整理者按语，照实排除。
    // ------------------------------------------------------------------
    private static String JUAN_SI_ZI_HUA_ZHI_MI_XU =
        "字画指迷夫先天者，已露之机；后天者，未成之兆也。先天，则有事，始占一事之吉凶；后天，则有所未"
      + "知，而出仓猝之顷，而休咎验焉。故先天为易测，后天为难测也。先天，则有执蓍而成卦；后天，触物即"
      + "有卦。此全在人心神之所用也。其能推测之精，所用之活，则无一事一物，莫逃之数矣。我居者为中，现"
      + "于前者为离，现于后者为坎，出于左者为震，出于右者为兑，在我左角者为艮，在我右角者为乾，在乾左"
      + "角者为坤，在我左前角者为巽。此八卦位，八方而定吉凶，立八卦而定克应，取时日而定吉凶，观变爻而"
      + "定体用。故我坐，则其祸福应二卦成数之间；我立，则其祸福应于中分二卦之间。大抵坐则静，行则动，"
      + "立则半动半静。静则应迟，动则应速。凡有触于我而有意，以为我之吉凶，则吉凶在我，应验在人。意者"
      + "何如？盖八卦之画既定，六爻之断既明，仍予以生克之理，究以刑冲之蕴，万无一失矣。近取诸身，远取"
      + "诸物，仍当以心求，不可以迹求，不可拘泥物圆为天卦，物方为地卦。是为序。指迷赋尝闻相字乃前贤妙"
      + "术，古今秘文，为后学之成规，辨吉凶之易见。相人不如相字，相字即相其人。变化如神，精微入圣。自"
      + "古结绳为政，如今花押成数。言，心声也；字，心画也。心形如笔，笔画一成，分八卦之休囚，定五行之"
      + "贵贱，决平生之祸福，知目前之吉凶。富贵贫贱，荣枯得失，皆于笔画见之。或将吉为凶，或指凶为吉。"
      + "先问人之五行，次看人之笔画。相生相旺则吉，相克相泄则凶。如此观之，万无一失。为官则笔满金鱼，"
      + "致富则笔如宝库。一生孤独，见于字画之欹斜；半世贫穷，乃是笔端之愚浊，非夭即贱。三山削出，皆非"
      + "显达之人；四大其亡，尽是寂寥之辈。父母俱存兮，乾坤笔肥；母早亡兮，坤笔乃破；父先逝兮，乾笔乃"
      + "亏。坎是田园并祖宅，稳重加官；艮为男女及兄弟，不宜损折。兑土主妻宫之巧拙，离宫主官禄之荣枯。"
      + "震为长男，巽为驿马。乾离囚走，壬主竞争。震若勾尖，常招是非。妻定须离，若是圆净，禄官亦要清明"
      + "。离位昏蒙，乃是剥官之杀。兑宫破碎，宜婚硬命之妻。金命相逢火笔，克陷妻儿。木命亦怕逢金，破财"
      + "常有。水命不宜土笔，不见男儿。火命若见水笔，定生口舌。土命若见木笔，祖产自消。相生相旺皆吉，"
      + "相克相刑定凶。举一隅自反，遇五行而相之。略说根源，以示后学。";   // 卷之四·字画指迷序＋指迷赋

    private static String JUAN_SI_XUAN_HUANG =
        "玄黄克应歌玄者，天也；黄者，地也。应者，克应之期也；天地造化，克应之谓也。歌曰：凡是挥毫落楮"
      + "时，便将凶吉此中推。忽听傍语如何说，便把斯言究隐微。倘是欢言多吉庆，若闻愁语见伤悲。听得鹊声"
      + "云有喜，偶逢鸦叫祸无移。带花带酒忧还退，遇醢逢醯事转迷。更看来人何服色，五行深处说根基。有人"
      + "抱得婴儿至，好把阴阳两字推。男人抱子占儿女，妇人抱子问熊罴。一女一子成好事，群阴相挽是仍非。"
      + "若见女人携女子，阴私连累主官非。忽然写字宽衣带，诸事从今可解围。跛子瞽人持杖至，所谋蹇滞不能"
      + "为。竹杖麻鞋防孝服，权衡柄印主操持。见果断之能结果，逢衣须说问良医。若见丹青神鬼像，断他神鬼"
      + "事相随。若画翎毛花果类，必然妆点事须知。有时击磬敲椎响，定有佳音早晚期。寺观铃铙钟鼓类，要知"
      + "仙佛与禳祈。倘是携来鱼雁物，友朋音信写相思。逢梅可说娣媒动，见李公私理不亏。见肉定须忧骨肉，"
      + "见梨只怕有分离。仕宦官员俄顷至，贵人相遇不移时。出笔拔毫通远信，笔头落地事皆迟。墨断须防田土"
      + "散，财空写砚忽干池。犬吠如号忧哭泣，猫呼哀绝有人欺。贼盗将临休见鼠，喜人摧动爱闻鸡。马嘶必定"
      + "有人至，鹊噪还应远客归。字是硃书忧血疾，不然火厄有忧危。楼上不宜书火字，木边书古有枯枝。硃书"
      + "更向炉边写，荧惑为灾信有之。破器偶来添砚水，切忧财耗物空虚。笔下忽然来蟢子，分明吉庆喜无疑。"
      + "若在右边须弄瓦，左边必定产男儿。叶上写来多怨望，花间书字色情迷。果树边旁能结果，竹间阻节事迟"
      + "疑。晴宜书日雨宜水，夏火秋金总是时。更审事情分向背，玄黄克应细详推。玄黄叙龟形未判，此为太古"
      + "之淳风；鸟迹既分，爰识当时之制字。虽具存于简牍，当深究其源流。成其始者，信不徒然。即其终之，"
      + "岂无奥义？同田曰富，分贝为贫。两木相并以成林，每水东归是为海。虽纷纷而莫述，即一一而可知。不"
      + "惟徒羡于简笺，亦可预占乎休咎。春蛇秋蚓，无非归笔下之功。白虎青龙，皆不离毫端之运。今生好癖，"
      + "博学博文。少年与笔砚相亲，半世与诗书为侣。识鱼鲁之外，穷亥豕之讹，别贤愚之字。昭然于毫端，察"
      + "祸福之机，了然于心目。鲜而当理，敢学说字之荆公；挟以动人，未逊后来之谢石。得失何劳于龟卜，依"
      + "违须决于狐疑。岂徒笔下以推尊，亦至梦中而讲究。刀悬梁上，后操刺史之权；松出腹间，果至三公之位"
      + "。皆前人之已验，非后学之私言。洞察其阴阳，深明乎爻象，则吉凶悔吝可知矣。玄黄歌大抵画乃由心出"
      + "，以诚剖决要分明。出笔发毫逢定位，笔头若出干无成。墨断定知田土散，纸破须防不正人。犬吠一声防"
      + "哭泣，鼠来又忌贼来侵。赤硃写字血光动，叶上书来有怨盟。忽见鸡鸣知可喜，人惊梦觉事通灵。马嘶必"
      + "有行人至，猫过须防不正人。船上不宜书火字，楼头亦忌有官刑。有时戏在炉中写，遇火焚烧忽不宁。破"
      + "器莫教添砚水，定知财散更伶仃。笔下偶然蝇蟢至，分明六甲动阴人。在左定生男子兆，右至当为添女人"
      + "。曾见人家轻薄辈，口中含饭问灾迍。直饶目下千般喜，也问刑徒法里寻。花下写来为色欲，女人情意喜"
      + "相亲。花开花落寻灾福，刻应之时勿目盲。麒麟凤凰为吉兆，猪羊牛马是凡形。此际真搜玄妙理，其中然"
      + "后有分明。应验止须勤记取，灾祥议论觉风生。";   // 卷之四·玄黄克应歌＋玄黄叙＋玄黄歌

    private static String JUAN_SI_FU =
        "花押赋夫押字者，人之心印也。古人以结绳为证，今人以押字为名。大凡穷通之理，皆与阴阳相应。先观"
      + "五行之衰旺，次察六神之强胜。五行者，立木、卧土、勾金、点火、曲水之象；六神者，青龙、朱雀、腾"
      + "蛇、玄武、勾陈、白虎之形。上大阔方，火乃发用；坚瘦有兮，木乃生荣。金要方，水要圆，土要肥而木"
      + "要正。故曰：炎炎火旺，玉堂拜相。洋洋水秀，金阙朝元。木盛兮，仁全义广；金旺兮，性急心刚。土薄"
      + "而离巢破祖，土厚而福禄绵绵。故曰：土少木多，根根折挫。金少火多，两窟三窝。金斜而定然子少，木"
      + "曲而中不财丰。盖画长兮，象天居上。土卧厚兮，象地居下。内木停兮，象人在于中央。三才全兮，如身"
      + "居其大厦。无天有地兮，父早刑。有天无地兮，母先化。有孤木兮，昆弟难倚。天失兮，故基已罢。内实"
      + "外虚兮，虽才高无成。外实内虚矣，终富贵而显赫。龙蟠古字，必有将相之权。不正偏斜，定是孤穷之客"
      + "。腾蛇缠体，飘流万里之程。玄武克身，妨妻害子。身之土透天，常违父母之言，而有失兄弟之理。只将"
      + "正印按五行，仔细推详大小吉凶，搜六神而无不验矣。探玄赋且夫天字者，乃乾健也，君子体之。地字者"
      + "，乃坤顺也，庶人宜之。君子书天，得其理也。庶人书地，亦合宜也。夏木春花，此乃敷荣之日；冬梅秋"
      + "菊，正是开发之时。一有背违，宁无困顿？日字要看停午，月来须问上弦。假如风雨，要逢长旺之时。若"
      + "是雪霜，莫写炎蒸之候。牡丹芍药，只是虚花。野杏山桃，皆为结实。森森松柏，终为梁栋之材。郁郁蓬"
      + "蒿，不过园篱之物。书来风竹，判以清虚。写到桑蚕，归于饱暖。锣鸣炮响，可言声势之家。波滚船行，"
      + "俱作飘流之士。鱼龙上达，犬豕下流。泉石烟霞，自是清贫之士。轩窗台榭，难言暗昧之徒。河海江山，"
      + "所为广大。涧溪沼沚，作事卑微。灯烛书于夜间，自然耀彩。月星写于日午，定是埋光。椒桂芝兰，岂出"
      + "常人之口。桑麻禾麦，决非上达之人。黄白绿青红，许以相逢艳冶。宫商角徵羽，言他会遇知音。剑戟戈"
      + "矛，终归武士。琴书笔砚，乃是文人。问贱与贫，因见自谦之德。书富乃贵，已萌妄想之心。金玉珍珠，"
      + "不过守财之辈。荣华显达，欲寻及第之方。恩情欢爱，既出笔端。淫荡痴迷，常眠花下。酒浆脍炙，哺啜"
      + "者必常书之。福寿康宁，老大者多应写此。且如龙蟠虎踞，宁无变化之时？凤翥鸾翔，终有飞腾之日。体"
      + "如鹭立，孤贫之士无疑。势如鸦飞，饶舌之徒可测。惊蛇失道，只寻入穴之谋。舞鹤离巢，自有冲霄之志"
      + "。急如鹊跳，是子轻浮。缓似鹅行，斯人稳重。如篁翁郁，休言豁达心怀。似水飘流，未免萧条家道。或"
      + "若炎炎之火，或如点点之云，一生喜怒无常，终身成败不保。风摇嫩竹，早年卓立难成。雨洗桃花，晚岁"
      + "羁栖无倚。为人潇洒，乃如千树之汪梅。赋性温柔，何异数株之岩柳。烟萝系树，卓立全倚于他人。霜叶"
      + "辞柯，飘零不由自己。画似棱棱之枯木，孤苦伶仃。形如泛泛之浮萍，贫穷漂泊。无异巉岩之怪石，险营"
      + "生。有如耸拔之奇峰，孤高处世。金绳铁索，此非岩谷之幽人。玉树瑶琴，定是邦家之良佐。乱丝缠结，"
      + "定知公事牵连。利刃交加，即是私家格角。撇如罗带，除遇阴人。捺似拖勾，刑伤及己。勾似锦靴，遭逢"
      + "官贵。画成横枕，疾病临身。切忌横冲半断，不保荣身。仍嫌直落中枯，难言高寿。剔成新月，出门便见"
      + "光辉。点作星飞，守旧宁无晦滞。至若挥毫带煞，秉生死之重权。落纸无成，作奔趋之贱役。起腾腾之秀"
      + "气，主有文章。生凛凛之寒光，宁无声价？半浓半淡，作事多乖。倚东倚西，撑持不暇。字短则沈沦不显"
      + "，字长则潦倒无成。拾后拈前，所为险阻。忘前顿后，举动趦趄。且如偃仰，遇庶人则成号泣，君子必定"
      + "飞腾。若是拘挛，逢君子乃是刑囚，庶人必能勤苦。造其理也，即此推之。余向遇异人，曾授玄黄诸篇。"
      + "今遇异翁授此，赋毕问之曰：“愿得公之名姓。”公不答而去。齐景至理论天下之妙，无过一理。理既能"
      + "明，在乎明学。学者穷究，莫难乎性。性既明达，其理昭然。且苍颉始制之时，观迹成象，以之运用，应"
      + "变随机。且释老梵经，王勃佛记，迨乎今飞轮宝藏之内，既深且密，非高士莫得而闻。何由睹之？其汉高"
      + "有荣阳之围，以木生火，终不能灭。有人梦腹上生松，丝悬山下，后为幽州刺史。松为十八公，不十为卒"
      + "。《春秋》说“十四心为德”，《国志》云“口在天为吴”。《晋书》：黄头小人为恭，以人负吉为造。"
      + "八女之解安禄山，两角女子绿衣裳。端坐太行邀君主，一正之月能灭亡。正月也。郭璞云：永昌有昌之象"
      + "。其后昌隆。罗，四维也。其偶如此。且人禀阴阳造化，凭五行妙思，一言一语，一动一静，然后挥毫落"
      + "楮，点画勾拔，岂不从于善恶？得之于心，悬之于手，心正则笔正，心乱则笔乱。笔正则万物咸安，笔乱"
      + "则千灾竞起。由是考之，其来有自。达者，以理晓；昧者，以字拘。难莫难于立意，贵于言辞。立意须在"
      + "一门，言词务在心中。余幼亲师友，温故知新，志在取进场屋，为祖宗之光。遂乃屈身假道，每以诗酒自"
      + "娱。渡江乘兴，偶信卜于岩谷。观溪山之清流，闻禽鸟之好音，殆非人世。忽见一人道貌古怪，披头跣足"
      + "，踞坐磻石之上。余由是坐之于侧，良久交谈之际，询余曰：“子非齐景乎？”予惊讶，预知姓名，疑其"
      + "必异人也，遂答之曰：“然。”异人曰：“混沌判肇，苍颉制字者，余也。自传书契于天下，天下大定。"
      + "后登天为东华帝君。今居于此，乃东华洞天。余曾有奇篇，昔付谢石，今当付汝。今子之来，可熟记速去"
      + "。不然，尘世更矣。”于是拜而受之，退而观其奥妙，乃《玄黄妙诀神机》。解字之文，得其方妙，如谷"
      + "之应声，善恶悉见，祸福显然，定死生于先知，决狐疑于预见，后之学者幸珍重之。";   // 卷之四·花押赋＋探玄赋＋齐景至理论

    private static String JUAN_SI_ZI_HUA_JING_YAN =
        "字画经验敷字：昔在任宰，请折之，云此字十日内放笔。果以十日罢任。家字：凡人书此，家宅不宁。空"
      + "字头，豕应在亥月者也。荆字：艹而刑，不利小人，大宜君子。砚字：有一字天，出之乱尔，见名之兆。"
      + "典字：曲折多，四七日有兴进之兆。贵人必加官进禄，雅宜便，四十日有进纳之喜。果字：凡事善果披剃"
      + "，盖口中无才，又云进小口。马字：昔有马雅官，写马字无点，马无足不可动。来字：来带两人之木，皆"
      + "来见信，行人来应，三人同来，财午未年发。葵字：逢春发生，又占名利，逢癸可发，占病不宜，廿日有"
      + "惊恐之兆。但字：如日初生，常人主孤，凡事未如意，十日身坦然。谦字：故人嫌，盖无廉耻，目下有事"
      + "，多是非。亨字：高未高，了未了，须防小人，不足及外孝，不祥。达字：二十日未达，即日并不顺，少"
      + "喜多忧。奇字：占婚奇偶未谐，应十日难，为兄弟事不全。俊字：一住一利，交友难为。父兄反覆，文书"
      + "千连变易。凶。常字：占病堂上人灾，有异姓异母。上有堂字头，下有哭字头。每字：昔曹石遣人相此字"
      + "，异日必为人母。后果然。城字：逢丁戊日，六神动，忌丁戊日，田土不足，进力成功。池字：凡事拖延"
      + "有日，逢蛇必利，盖添虫为蛇。春字：宋高宗写此字时，秦桧用事。石云：秦头太重，压日无光。桧闻言"
      + "召而遣之。一字：土字一字，王也。益字：有吏人书益字，廿八日有血光之厄。至期果然。田字：有人出"
      + "此，相言：直看是王，横看是王，必主大贵。字体诗诀天字及二人，作事必有因。一天能庇盖，初主好安"
      + "身。地字如多理，从此出他乡。心如蛇口毒，去就尽无妨。人字无凶祸，文书有人来。主人自卓立，凡事"
      + "保和谐。金字得人力，屋下有多财。小人多不足，凡事要安排。木字人未到，初生六害临。未年财禄好，"
      + "切莫要休心。水字可求望，中妨有是非。文书中有救，出入总相宜。火字小人相，中人发大财。灾忧须见"
      + "过，日下有人来。土字日下旺，田财尽见之。穿心多不足，骨肉主分离。东字正好动，凡事早求人。牵连"
      + "须有事，财禄自交欣。西字宜迁改，为事忌恶人。心情虽洒落，百事懒栖身。南字穿心重，还教骨肉轻。"
      + "凡事却有幸，田土不安宁。北字本比和，不宜分彼此。欲休尚未休，问病必见死。身字主己事，侧伴更添"
      + "弓。常藉人举荐，仍欣财禄丰。心字无非大，秋初阴小灾。小人多不足，夏见必灾来。头来须鄙衰，发可"
      + "却近贵。要过子丑前，凡事皆顺利。病来如何疾，木命最非宜。过了丙丁日，方知定不危。言字如何拆？"
      + "人来有信音。平生多计较，喜吉事应临。行字问出入，须知未可行。不如姑少待，方免有灾惊。到字若来"
      + "推，出入尚颠倒。虽然吉未成，却于财上好。得来问日下，宁免带勾陈。凡事未分付，行人信不真。开字"
      + "无分付，营谋尚未安。欲开开不得，进退两皆难。附字问行人，行人犹在路。为事却无凶，更喜有分付。"
      + "事字事难了，更又带勾陈。手脚仍多犯，月中方可人。卜方求测事，停笔好推详。上下俱不足，所为宜不"
      + "祥。望字逢寅日，所谋应可成。主须不正当，却喜有功名。福字来求测，须防不足来。相连祸逼迫，一口"
      + "又兴灾。禄字无祖产，当知有五成。小人生不足，小口有灾惊。贵字多近贵，六六发田财。出入须无阻，"
      + "宜防失落灾。用字主财用，有事必经州。谁识阴人事，姓王又姓周。康字未康泰，要防阴小灾。所为多不"
      + "逮，财禄亦难来。宁字占家宅，家和人口增。财于中主发，目下尚伶仃。吉字来占问，反教吉又凶。因缘"
      + "犹未就，作事每无终。宜字事且且，须知在目前。官非便了当，家下亦安然。似字众人事，所为应不成。"
      + "独嫌人力短，从众则堪行。多字宜迁动，死中还得生。事成人侈靡，两日过方明。古字多还吉，难逃刑克"
      + "灾。虽然似喜吉，口舌却终来。洪宜人共活，火命根基别。事还牵制多，应是离祖业。香字忌暗箭，木上"
      + "是非来。十八二十八，好看音信回。清字贵人顺，财来蓄积盈。阴人是非事，不净更多年。虚惟头似虎，"
      + "未免有虚惊。凡事亦可虑，仍妨家不宁。远字事多达，行人有信音。为事既皆遂，喜吉又来临。同字如难"
      + "测，商量亦未然。两旬事方足，尚恐不周圆。众字人共事，亦多生是非。所为应不敛，小口有灾危。飞字"
      + "须可喜，反覆亦多非。意有飞腾象，求名事即宜。秀字多不实，无事亦孤刑。五五加一岁，还生事不宁。"
      + "风字事无宁，逢秋愈不吉。疾多风癣攻，更防辰戌日。天字已成天，亦多吞噬心。事皆蒙庇盖，行主二人"
      + "临。元字二十日，所为应有成。平生刑克重，兀兀不安宁。秋字秋方吉，小人多是非。须知和气散，目下"
      + "不为宜。申字是非长，道理亦有破。终然屈不伸，谋事难为祸。甲字利姓黄，求名黄甲宜。只恐田土上，"
      + "还惹是和非。川字如来问，当知有重灾。仍防三十日，不足事还来。墟字若问事，虎头蛇尾惊。有人为遮"
      + "盖，田土不安宁。辰字如写成，主有变化象。进退虽两难，功名却可望。青字事未顺，须知不静多。贵人"
      + "仍不足，日久始安和。三字多迁改，为事亦无主。当知二生三，水由一生二。人如来问测，分字亦安让。"
      + "凡事多费解，仍防公扰忧。字须有学识，初主似空虚。家下不了事，名因女子中。士为大夫体，末免犯穿"
      + "心。拮括是非散，番多吉事临。四季水笔春水昏浊夏水枯涸秋水澄清冬水凝结水为财，忌居乾、兑、坎。"
      + "、、、勹、点不为杀，必为贵人。画有阴阳长中有短为阳中阴，短中有长为阴中阳。粗细轻重，以此为例"
      + "。阳中有阴则佳，阴中有阳反凶。壬字头画，是阳中有阴。任字头画，是阴中有阳。水笔不流，流则不佳"
      + "。戴流珠，名映星，小人囚系。取福下至上一三，取祸上至下一三。八卦断乾宫笔法如鸡脚，父母初年早"
      + "见伤。若不早年离侍下，也须抱疾及为凶。坤宫属母看荣华，切忌勾陈杀带斜。一点定分荣禄位，一生富"
      + "贵最堪夸。艮位排来兄弟宫，勾陈位笔性他凶。纵然不克并州破，也主商参吴楚中。巽宫带口子难逢，见"
      + "子须知有克刑。饶君五个与三个，未免难为一个成。震位东方一位间，要他笔正莫凋残。若逢祐断须沾疾"
      + "，腰脚交他不得安。离是南方火位居，看他一点定荣枯。若还圆净荣官禄，燥火炎炎定不愚。坎为财帛定"
      + "卦位，水星笔横占他方。若见笔尖无大小，根基至老主荣昌。兑位西方太白间，只宜正直莫凋残。若然坑"
      + "陷并尖缺，妻子骄奢保守难。";   // 卷之四·字画经验＋字体诗诀＋短诀（四季水笔/画有阴阳/八卦断）

    private static String JUAN_SI_XIANG_ZI_XIN_YI =
        "相字心易凡写两字，止看一字。盖字多必乱，若谋事之类，亦必移时方可再看。辨字式富人字，多稳重无"
      + "枯淡。贵人字，多清奇，长画肥大。贫人之字，多枯淡无精神。贱人字，多散乱带空亡。百工字，多挑跃"
      + "。商贾字，多远迩。男子字多开阔。妇人字多逼侧。余皆浓淡肥瘦、斜正分明之类断之。笔法筌蹄凡书字"
      + "法，有浓淡、肥瘦、长短、阔狭、反覆、顺逆、曲直、高低、小大、软硬、开合、清浊、虚实、凹凸、平"
      + "正、斜侧、圆满、直牵、明白、轻快、稳重、跳跃、勾挽、破碎、枯槁、尖削、倒乱、鹘突、孤露、交加"
      + "、肥满、尖瘦、刚建、精神、艳冶、气势、衰弱、小巧、软满、老硬、骨棱、草率、开阖之分，各有一体"
      + "，难以尽述。学者变化，知机其神。歌曰笔画稳重，衣食丰隆。笔画平直，丰衣足食。笔画端正，衣禄铁"
      + "定。笔画分明，决定前程。笔画圆静，富贵双并。笔画肥浓，富贵无穷。笔画洁净，功名可决。笔画轻快"
      + "，诸事通泰。笔画刚健，力量识见。笔画精神，必有声名。笔画光发，荣显通达。笔画气势，慷慨意志。"
      + "笔画宽洪，逞英逞雄。笔画尖小，其人必了。笔画如线，有识有见。笔画似绳，一世平宁。笔画挑剔，好"
      + "巧衣食。笔画乌梅，面相恢恢。笔画懒淡，兄弟离散。笔画分扫，破家必早。笔画弯曲，奸巧百出。笔画"
      + "迭荡，一生浮浪。笔画枯槁，财物虚耗。笔画糊涂，愚蠢无谋。笔画粘滞，是非招怪。笔画大小，有歉有"
      + "好。笔画高低，说是说非。笔画淡泊，疮痍克剥。笔画反覆，心常不定。笔画破淬，家事常退。笔画欹斜"
      + "，飘泊生涯。笔画恶浊，无知无学。笔画如蛇，常不在宅。笔画偏侧，衣食断隔。笔似鼓槌，至老寒微。"
      + "笔势如针，此人毒心。笔势勾斜，官事交加。笔势如钩，害人不休。笔势散乱，财谷绝断。笔格常奇，诀"
      + "以别之。奴婢恰似霜天一叶飞，画如木檐两头垂。画轻点重君须记，画定前趋后拥儿。阴人阴人下笔意如"
      + "何，只为多羞胆气虚。起处恰如争嘴样，却来下笔定徐徐。隔手隔手书来仔细详，见他纸墨字光芒。更看"
      + "体骨苏黄格，淡有精神是贵郎。";   // 卷之四·相字心易＋辨字式＋笔法筌蹄＋奴婢/阴人/隔手

    private static String JUAN_SI_DUAN_FA =
        "视势每遇人写来，必别是何字，如“天”字乃是夫字及“失”字基址，女人写妨夫，男子写有失。象人凡"
      + "字必别是何人写，亦象人而言。如“天”字，秀才问科第，今年尚未，当勉力读书，来年有望及第；官员"
      + "求官，亦未，宜勉力政事，主来年得人举荐受恩；若庶人占之，病未安，用巫方愈；讼者未了主费力，必"
      + "被官劾断之。“天”加直成“未”，再加点成“来”。“来”、“力”成其“刺”。有所喜如问财，见金"
      + "宝偏旁及禾斗之类，决好。有所忌如问病，见土木，及问讼见“血”、“井”字，皆凶。有所闻如问病，"
      + "忌闻悲泣声。占财不宜破碎声。有所见如“立”字见雨下，或水声，则成“泣”字。又如“言”字，见“"
      + "犬”成“獄”字，问病讼皆忌之。以时而言如草木字，春夏则生旺有财，秋冬则衰替多灾。风云气候之候"
      + "亦然。以卦而断如“震”字，春则得时，冬则无气，皆以其卦言之。以禽兽而断如“牛”字，则为人劳苦"
      + "。春夏劳苦，秋冬安逸。取类而言如“楼”字，笔画多，不可分解。以楼取义，乃重屋也。重屋拆开，乃"
      + "千里尸至，问字人必有人死在外，尸至之事。以次而言如字先写笔画，喜则言吉，次则言凶，又次则言半"
      + "凶半吉。以次加减，亦察人之气也。当添亦添且如官员写“尹”字，乃君字首断，其人必见上位，定不禄"
      + "而还，以君无口故也。如书“君”字，乃是郡旁，其人当得郡守。当减亦减如“树”字，中有吉字，写得"
      + "好者，则减去两边，只是言吉。笔画长短如“吉”字，上作士字，终作士人。如作土字，乃口在下，问病"
      + "必死。若身命属木，自身无妨。屋下木土生，不过十日必亡。如“常”字，上作小字，只是主家内小口灾"
      + "，略不为大害。若上草作小，如此写，乃是灾字头，中是门字，下是吊字，主其人大灾患临头，吊客入门"
      + "，大凶。然亦须仔细仍观人之气色。象人而言，如土人，气色黑恶，其人必退，若土命者，必死，俱不过"
      + "十日。偏旁侵客如宀，乃家字头。如宀笔画破断写，乃是破家宅，无其家，必退。如写作“山”字形，必"
      + "兴门户。如“山”有缺笔，乃是悬针之山，必大凶也。字画指迷如“人”字，正人作贵相，睡人作病疾，"
      + "立人旁托人，双人旁动人，其人逆多顺少。从作两人相从，众作群党生事。坐人作阻隔，更作闲人。如“"
      + "申”字，作破田煞，常人不辨破田之说，用事重成之义也。如“田”字，藏器待时，头足有所争，争而有"
      + "所私，忌田产不宁。如“彐”字作横山，取之衣禄渐明矣。又作日间防破。如“黄”字，作廿一后，方得"
      + "萌芽；又作廿一用，可喜也。又云：上有一堆草，中有一条梁，撑杀由八郎。如“言”字，有谋有信，取"
      + "之如草之作木，取之心不定也。如“心”字，三点连珠，一钩新月，皆清奇之象。或竖心性情，作小人之"
      + "状。近身作十字，作穿心六害，取凡百孤独。如“寸”字，亦心也。一寸乃十分，为人有十分之望，谋望"
      + "有分付也，又作一十取之。如“辛”字，乃六七日内见，立见于求远；作六十一日，或云有宰相成也。问"
      + "婚姻凡字写得相粘者，可成。又字画直落成双者，可成。字中间阔而不粘，及直横成双者，偏旁长短者，"
      + "不成。凡写字得脚匀齐者，皆就字。四齐者，尤吉字。上短下长者，日久方成字。乾上有破，父不从。坤"
      + "宫破，母不从。左边长者，男家顺，女家不肯。右边长者，女家顺，男家未然。官事或见文字或字脚，一"
      + "“丿”一“乀”破碎，断有杖责。或见“牛”字，有牢狱之忧，主人大失。或“木”笔开口者，亦有杖责"
      + "。字画散乱者，易了。或有“丿”、“乀”长者耸者，亦有杖刑。或见杖竹之类，亦有打兆。火命人写“"
      + "水”字来问，必有官灾。或字有草头者，说草头姓，得力之类。疾病金笔多，心肺痰，脏腑疾。西方金神"
      + "为衬。木笔多，心气疾，手足灾。木神林坛为祟。水笔多，泻痢吐呕之症。水鬼为凶。火笔潮热，伤寒时"
      + "行，火鬼为怪。又云四肢疼，时气疾病。火笔多者，病不死。土笔多，脾胃兼疮疾，客亡，伏尸鬼，疼痛"
      + "之疾。土笔多者，病死字，凡有丧字、虎字头，或两口字者，皆难救。六甲字凡有“喜”字、“吉”字体"
      + "者，皆吉。字凡带白虎，必难产，子必死。写得粘者，易产。字画纤断者，主有惊险。字有腾蛇笔者，主"
      + "虚惊。字画直落成双者，阴喜；成单者，男喜。求谋凡字写得中间阔者，所谋无成。谋字写得相粘者，二"
      + "十四五前成，盖有隔字体故也。求字来问者，木命人吉，土人不利。行人远信如“行”字写得脚短、一般"
      + "齐者，人便至。字脚或不齐，行人皆不至。字画直落点多者，其人必陷身。字画少者，人便至。乃详字体"
      + "格范。官贵凡字有二数，一点当先者，无阻事济。所写之字，相粘伶俐者，贵人顺。点多者，事不成。失"
      + "物凡字有失字体，及字中皆难觅，朱雀动，有口舌，日久难寻。金笔多，艮上有破五金之物，宜速寻。土"
      + "笔多，坎上有破碎之物，在北方古井，或窑边及坑坎之所，瓦器覆藏，五日见。坤上有一钩者，乃奴婢偷"
      + "去，不可取得。兑上不足，乃妻妾为脚带，金人将去。离上一画不完者，乃南方火命人将去，见官方失物"
      + "仍在。问寿字画写得长而瘦者，寿耐久；如肥壮者，耐劳苦；短促者，无寿。功名字有贵人头者，有功名"
      + "。字金笔多而端正，及木笔轻而长者，皆贵。行人“人”字潦倒，未动。写得“人”字起者，已动。人以"
      + "“来”字问者，未至。“行”字问者，且待。凡字中有“言”字者，有信至，人未至也。反体“喜”字来"
      + "问者，未可言喜，有舌字脚也。有以“庆”字来问者，未可言庆，有忧字脚也。“星”字来问者，日在上"
      + "，星辰不见，问病必凶。大凡文人，不可写武字，武人不可写文字。阴人不可写阳字，阳人不可写阴字。"
      + "皆反常故也。";   // 卷之四·视势/象人/以时/以卦/以禽兽/取类/当添减/偏旁侵客＋问婚姻…行人

    private static String JUAN_SI_LIU_SHEN =
        "六神笔法“乀”：青龙木。“乂”：朱雀火。“勹”：勾陈土。“”：腾蛇（无正位）。“几”：白虎金"
      + "。“厶”：玄武水。蚕头燕额是青龙，雨笔交加朱雀凶。玄武怕他枯笔断，勾陈回笔怕乾宫。腾蛇草笔重"
      + "重带，白虎原来坤位逢。此是六神真数诀，前将断语未流通。六神主事青龙主喜事，白虎主丧灾，朱雀主"
      + "官事，勾陈主留连，腾蛇主妖怪，玄武主盗贼。六神都静，万事咸安。莫交一动之时，家长须忧不测。若"
      + "非财散，必主刑囚狱讼。青龙形式：丿、乀——青龙要停匀，百事皆吉。青龙笔动喜还生，谋用营求事事"
      + "通。人口增添财禄厚，主人日下尽亨通。朱雀形式：乂——朱雀临身文书动，主失财，有口舌，生横事。"
      + "忌惹人，有忧惊之事。朱雀交加口舌多，令人家内不安和。若逢水命方无怪，他命逢时有怨疴。勾陈形式"
      + "：勹——勾陈主惊忧、迟滞。忌土田。是非未决，并惹闲非。勾陈逢者事交加，谋事中间事事差。田宅官"
      + "司多挠括，是非门内有喧哗。腾蛇形式：——腾蛇主忧虑，梦不祥，作事多阻，有喧争，惹旧愁，宜守静"
      + "。腾蛇遇者主惊虚，家宅逢之尽不宁。出入官谋宜慎取，免教仆马有灾形。白虎形式：凡——白虎主有不"
      + "祥之招。产、病有孝服，及官鬼，惹口舌，在囚狱。白虎逢之灾孝来，出门凡事不和谐。便防失脱家财损"
      + "，足疾忧人百事乖。玄武形式：厶——玄武贵人华盖，主盗财，亦难寻。玄武动时主失脱，家宅流离慎方"
      + "活。更防阴小有灾危，又至小人生拮括。笔画犯煞凤鳞断伏活法用煞连图带曰隔伏丁欹伏冲伏悬针冲伏流"
      + "金活金乙伏曲曲伏口死金活火死火腾蛇死土蛇土力隔伏玄黄笔法歌厂、反——反旁无一好，十个十重灾。"
      + "旁里推详看，临机数上排。廴、走——走远字如何，须防失脱多。若还来问病，死兆不安和。孑、系——"
      + "系绞同丝绊，干事主留连。却喜财公问，傍看日数言。阝、卩——附邑旁边事，当从左右推。兑宫知事定"
      + "，震位事重为。灬、二——四点皆为火，逢寅过于通。若还书一画，百事尽成空。亻、彳——卓立人傍字"
      + "，谋为倚傍成。若还来问病，死去又逢生。之、辶——之绕身必动，看其内必凶。问病也须忌，其余却少"
      + "通。、弓——弓件休乾用，反处日难凭。先自无弦了，如何得箭行。山、宂——宂下灾祸字，占家更问官"
      + "。更推从来用，凶吉就中看。人、冫——两点旁边字，还知凝滞攒。要问端的处，旁取吉凶看。吕、叩—"
      + "—双口相排立，因知恸哭声。各逢于戈日，亦主泪如倾。户、尸——户下尸不动，休来占病看。其余皆是"
      + "吉，即断作平安。阝、阜——阜邑旁边字，当为仔细推。兑宫知事息，震位又重为。衣、礼——礼字旁边"
      + "折，必定见生财。疋字如逢见，须从人正来。月、骨——骨傍人有祸，囚狱一重来。门内生荆棘，施设不"
      + "和谐。身、自——自家身傍限，分明身不全。有谋难得遂，即日是多煎。反、定——定绕自来看，身必有"
      + "所动。吉凶意如何，相里临时用。山、山——山下灾祥字，占家宜用官。更推从西用，凶吉数中安。人、"
      + "欠——欠字从西体，须知望用难。吹嘘无首尾，不用滞眉看。禾、禾——禾边刀是利，春季则为殃。夏日"
      + "宜更改，人中好举扬。耳、耳——耳畔虽有纪，轻则是虚声。旺事宜重用，取谋合有成。";   // 卷之四·六神笔法＋笔画犯煞＋玄黄笔法歌

    private static String JUAN_SI_WU_XING =
        "五行体格式水笔式水圆多性巧。浊者定昏迷。水泛为不定。水走必东西。火笔式火重性不常。火燥见灾殃"
      + "。火多攻心腹。火轻足衣粮。土笔式一土重根基好。工土轻离祖居。土滞破田宅。土定无虚图。金笔式口"
      + "金方利身主。金重性多刚。金走为神动。慷慨及门墙。木笔式木长性聪明。木短定功名。川木多才学敏。"
      + "木斜废支撑。时辰断看字先须看时辰，时辰克应不相亲。时辰若遇生其用，作事何忧不趁心。（此字中笔"
      + "一要用也。）起六神卦诀甲乙起青龙，丙丁起朱雀。戊日起勾陈，己日起腾蛇。庚辛起白虎，壬癸起玄武"
      + "。附例：今以甲乙、丙丁日附载为式，余仿此。辨别五行歌一横画连勾作土称，一挑一捺俱为金。撇长撇"
      + "短皆为火，横直交加土最深。有直不斜方是木，学者方明正五行。二一点悬空土迸尘，三直相连化水名。"
      + "孤直无依为冷木，腹中横短作囊金。点边得撇为炎火，五行变化在其中。三三横两短若无钩，乃为湿木水"
      + "中流。两点如挑金在水，八字相须火可求。空云独作寒金断，好把心勾比木舟。四无勾之画土稍寒，直非"
      + "端正木休参。围中横满无源水，口小金方莫错谈。四匡无风全五事，用心辨别莫疑难。五穿心撇捺火陶金"
      + "，走之平稳水溶溶。直中一捺金伤木，踢起无尖不是金。数点笔连休作火，奇奇偶偶水源清。六无直无钩"
      + "独有横，水因土化复何云。点挑撇捺同相聚，其总将来化土音。四点不连真化火，孤行一笔五行同。辨别"
      + "六神歌蚕头燕额是青龙，尖短交加朱雀神。弯弓斜月勾陈象，腾蛇长曲势如行。尾尖口阔为白虎，体态方"
      + "尖玄武行。此即六神真妙诀，断事详占要认真。五行歌并式木瘦金方水主肥，土形敦厚背如龟。上尖下阔"
      + "名为火，字像人形一样推。木式有直不斜方是木，即此是也。凡字有木，不偏不倚，始为木。若无倚靠上"
      + "下左右者，此系冷木，故云直无倚为冷木。另作别看。此乃湿木也。歌曰：三横两短又无勾，此为湿木水"
      + "中流。此土化木也。如“聿”字下三横，“春”字上三横，皆为湿木。凡有勾之横，及三横不分短长者，"
      + "皆非木也。此舟船木也，象如勾陈，属土。邵子云：“好把心勾比木舟。”故借作舟船木用，如占在水面"
      + "土行等事，即作舟船木用。如占别用，论勾陈，仍作土看。在占者临时变化，切不可执一而论也。此木被"
      + "金伤也，一样属金。故云：“直中一捺金伤木。”凡占得此木，为用伤者，皆主不得其力也。干支辨直长"
      + "为甲亦为寅，细短均为乙卯身。孤直心钩兼湿木，干支无位不须论。假如车字中央一直，彻上彻下，强健"
      + "无损，则属阳，所以为甲木、寅木。余皆仿此。如幸字上一直下一直，皆短弱属阴，所以作乙木、卯木论"
      + "也。凡一直，细弱不健，即长如车之直，亦作乙卯木看。其心钩舟船木，并三横两短木，一概不在干支论"
      + "，因其不正故也。火式撇长撇短皆是火，此式是也。点边得撇为炎火，此即是也。要一点紧紧相连，始合"
      + "式。如不联属点，仍属水，非炎火看也。八字相须火可求，此余火也。如八字捺长，则一撇为火，一捺另"
      + "作金看。四点不连真化火，此真火也。如四点笔法牵连不断，则属水非火论也。干支辨撇长丙巳短为丁，"
      + "午火同居短撇中。八字腾蛇兼四点，天干不合地支冲。假如庐字撇长，则取为丙火、巳火用。丙巳属阳，"
      + "故用撇长者当之。余仿此。如“从”字，撇多皆短，则取为丁火、午火用。丁午属阴，故用短弱者当之。"
      + "邵之子作，皆有深理存焉。余仿此。如八字四点之类，皆火之余，俱不入干支论。土式此横画连勾，作土"
      + "称是也。如用画无勾，直无撇捺相辅，此为寒土化水用，故“无直无勾独有横，土寒化水复何云”也。如"
      + "“二”字、“但”字、“竺”字之类也。如“血”字、“土”字与直相连，仍作土看。十歌云“横直交加"
      + "土最深”，即此是也。凡横画有一直在内为木，非深厚之土不能培木，所以云土最深也。余仿此。歌云“"
      + "一点悬空土迸尘”，此乃尘沙土也。凡“求”字、“戈”字末后一点皆是。如“文”字、“章”字，当头"
      + "一点属水，不在此论。“凉”字、“减”字起头一点亦属水，不在此论。一此无勾之画，为寒土解。见前"
      + "。此“点挑撇捺同相聚，其总将来化土音”，作土看。干支辨横中有直戊居中，画短横轻作己身。末点勾"
      + "陈皆丑未，长而粗者戊辰同。聿例如“聿”字之类，第二画长，末后一画长，余画皆短，即长者为阳土用"
      + "，短者为阴土用。必取横中有直者为准，如无直者，及无依辅者，另看轻细，虽长亦作阴土。求假如“求"
      + "”字之点，可作己土丑未，用其挑撇点捺同相聚无名之土，不入于干支之论也。金式歌云“一挑一捺俱为"
      + "金”，即此是也。挑起定要有锋尖，始为金。如踢起无尖，又非金看也。捺要下垂始为金，如走之平平，"
      + "又变水看矣。学者辨之要明。口口小金方，即此是也。如“因”字、“国”字、“匡”字，四匡大者，皆"
      + "非。目歌曰“腹中横短是囊金”。假如目字中两横短，而作囊内之金看。如两横长满者，乃“围中横满无"
      + "源水”，又不作金用也。如目中用两点非横者，亦是水，非金也。余仿此。此两点加挑，金在水云金，乃"
      + "水中之金也。几此“空云独作寒金断”，乃寒金也。“穿心撇捺火陶金”，此金在火中也。干支辨口口字"
      + "为金亦作申，挑从酉用捺从辛。空头顽钝囊金妙，不在干支数内寻。喜假如“喜”字上下两口，皆属阳，"
      + "取其方正故也。俱为庚金、申金用。扒假如“扒”字，挑才一挑取为酉，用八字一捺取为辛。因其偏隘，"
      + "故作阴金用。余仿此。水式此“一点当头作水称”，乃雨露水也。歌出邵子旧本。又云：“有点笔清皆作"
      + "水”，云有点属水也。又“一点悬空土迸尘”，点在末后一点化水，解见前。四点相连又化作火，亦见于"
      + "前解也。川此三直相连化水，名“川”字之义也。此字中央一满画，乃无源之水也。如画短不满者，不是"
      + "水，另作别看。此“走之平稳水溶溶”，捺不下垂，故作水看也。此数点相连，野水也。即四点笔迹不断"
      + "，亦作水看。一此土寒化水也。凡有依附者即非，仍作土看也。干支辨点在当头作癸称，腹中为子要分明"
      + "。点足为壬腰在亥，余皆野水不同群。文假如“文”字一点，即为癸水。癸水乃雨露之源，因在上故也。"
      + "余仿此。月假如“月”字腹中之点，即为子水，因其在内故也。凡“勺”字、“自”字等字，皆同用。余"
      + "仿此。景如“景”字中央一点，乃亥水，下二点为壬水，故“点足为壬腰在亥”，取江河在下义也。余仿"
      + "此。";   // 卷之四·五行体格式＋时辰断＋起六神卦诀＋辨别五行歌＋辨别六神歌＋五行歌并式

    /** 卷之四·字画指迷序＋指迷赋（照录；接入节点 16）。 */
    private static String juanSiZiHuaZhiMiXuNote() {
        return "卷之四·字画指迷序＋指迷赋——" + JUAN_SI_ZI_HUA_ZHI_MI_XU;
    }
    /** 卷之四·玄黄克应歌＋玄黄叙＋玄黄歌（照录；接入节点 16）。 */
    private static String juanSiXuanHuangNote() {
        return "卷之四·玄黄克应歌＋玄黄叙＋玄黄歌——" + JUAN_SI_XUAN_HUANG;
    }
    /** 卷之四·花押赋＋探玄赋＋齐景至理论（照录；接入节点 16）。 */
    private static String juanSiFuNote() {
        return "卷之四·花押赋＋探玄赋＋齐景至理论——" + JUAN_SI_FU;
    }
    /** 卷之四·字画经验＋字体诗诀＋短诀（四季水笔/画有阴阳/八卦断）（照录；接入节点 16）。 */
    private static String juanSiZiHuaJingYanNote() {
        return "卷之四·字画经验＋字体诗诀＋短诀（四季水笔/画有阴阳/八卦断）——" + JUAN_SI_ZI_HUA_JING_YAN;
    }
    /** 卷之四·相字心易＋辨字式＋笔法筌蹄＋奴婢/阴人/隔手（照录；接入节点 16）。 */
    private static String juanSiXiangZiXinYiNote() {
        return "卷之四·相字心易＋辨字式＋笔法筌蹄＋奴婢/阴人/隔手——" + JUAN_SI_XIANG_ZI_XIN_YI;
    }
    /** 卷之四·视势/象人/以时/以卦/以禽兽/取类/当添减/偏旁侵客＋问婚姻…行人（照录；接入节点 16）。 */
    private static String juanSiDuanFaNote() {
        return "卷之四·视势/象人/以时/以卦/以禽兽/取类/当添减/偏旁侵客＋问婚姻…行人——" + JUAN_SI_DUAN_FA;
    }
    /** 卷之四·六神笔法＋笔画犯煞＋玄黄笔法歌（照录；接入节点 16）。 */
    private static String juanSiLiuShenNote() {
        return "卷之四·六神笔法＋笔画犯煞＋玄黄笔法歌——" + JUAN_SI_LIU_SHEN;
    }
    /** 卷之四·五行体格式＋时辰断＋起六神卦诀＋辨别五行歌＋辨别六神歌＋五行歌并式（照录；接入节点 16）。 */
    private static String juanSiWuXingNote() {
        return "卷之四·五行体格式＋时辰断＋起六神卦诀＋辨别五行歌＋辨别六神歌＋五行歌并式——" + JUAN_SI_WU_XING;
    }

    // ------------------------------------------------------------------
    // 第 41 轮·卷之五·拆字杂编 成篇照录（B157–B170，共 14 组）：
    //   卷之五「拆字杂编」全卷为「相字」专术，其断法（玄黄克应/六神笔式/五行笔式/笔画歌诀/
    //   相字短诀/分类断法…）已由 putXz（264 条）与 DB `xiangzi_rule` 结构化承载（C12 相字别系）；
    //   惟**成篇之歌/赋/叙/论/诀/章/占例**（教训⑲）须成篇照录，与卷之三、卷之四同例，
    //   今依底本成篇照录归位（接入节点 16）。
    //   底本卷之五＝「拆字杂编」；「五行全备」诸篇起，至「金声章…请明章中之理」止，
    //   下接「附录·一撮金」。篇中「后天时方」后①②校勘二则、及各篇小字夹注，皆底本正文，照录不删。
    // ------------------------------------------------------------------
    private static String JUAN_WU_WU_XING_QUAN_BEI =
        "拆字杂编五行全备一点一画五行全，试看首尾秘为占。点画若无疵笔露，功名发达享高年。“丶”：如一"
      + "点端正，无破绽鸦嘴等形，则是五行全。如不合式，仍属水。“一”：亦五行全。此象乃庖羲氏画卦之初"
      + "而混元一气之数也。“”：此太极未分时，亦五行全大之象也。“口”：歌曰：“四匡无风全五行”，是"
      + "亦五行全也。如“国”字、“园”字之类，四匡紧紧不透风乃是。如笔稀者不是；口小者属金，亦不是。"
      + "此地之象也。六神形式青龙：丿、乀“蚕头燕额是青龙”。凡撇捺长而有头角之样，即作青龙，如撇短则"
      + "不足以成青龙之式。“不拘撇捺皆化木”。如无须角，虽长亦非青龙。朱雀：乂、丿“尖短交加朱雀神”"
      + "。撇短而有尖嘴之形，则为朱雀。主文书事，原属火无化。腾蛇：乙、乞、、“腾蛇长曲势如行”。其样"
      + "如蛇，皆化火看，亦主文书及惊怪等。勾陈：勹、乙、“弯弓斜月勾陈象”。凡带长者是也，属土，无化"
      + "，主羁滞。白虎：兄、凡、圭“尾尖口阔方为虎”。口不开者，非虎也。化作金用，主疾病，凶兆也。玄"
      + "武：厶、么、、云“体态方尖玄武形”。化木主盗贼事，又主波浪险阻等事。八卦辨口形为兑捺为乾，三"
      + "画无伤乾亦然。三点同来方是坎，撇如双见作离占。土山居上名为艮，居下为坤不必言。蛇形孤撇皆从巽"
      + "，云首龙头震占先。详明八卦知凶吉，学者参求理自全。";   // 卷之五·拆字杂编·五行全备＋六神形式＋八卦辨

    private static String JUAN_WU_SHEN_SHA =
        "贵神中上贝日月大人喜神士口言鸟福星不田（凡子孙动者亦作福星看。）文星二乂曰子印星口囗子马星灬"
      + "辶走禄神甲禄在寅，乙禄在卯，丙戊禄在巳，丁己禄在午。庚禄在申，辛禄在酉，壬禄在亥，癸禄在子。"
      + "（俱以占者年庚本命，于求之笔画为准。如甲命人，即以字中长直为禄。余仿此。）会神田曰云禺生神一"
      + "丶元甲子初（盖一者数之始，元者鸿朦之初，甲子者乃干支之首，故皆为生神之用也。）亡神十千百万贞"
      + "亥癸（十千百万皆数之终，贞乃元之尽，亥癸是干支之末，故为亡神。）家神宀乇火（灶神以四点同。）"
      + "土（土者，奥神是也。）堂（堂者，香火神也。）水（水者，井神等，三点亦同用。）官符宀付文书二乂"
      + "丿乙（朱雀、腾蛇皆是。）灾煞（即病符）巛宀火广丙矢（字中见旧太岁亦为病符星。）天狗煞字中见太"
      + "岁，前年干支是也。（如子午见戌，申年见壬，皆是。）科名星禾斗（以本人年甲所属是科名，如甲乙以"
      + "一直，丙人以一撇，皆科名也。余仿此。）丧门白兄空亡即六甲空亡，“甲子旬中空戌亥”之类是也。（"
      + "假如甲子旬中空，占即以腰间一点为亥空，以长画为戌空。余皆仿此。）宜神子为财之宜神，鬼为父之宜"
      + "神，兄为子之宜神，财为鬼之宜神，父为兄之宜神是也。忌神子为鬼之忌神，鬼为兄之忌神，兄为财之忌"
      + "神，财为父之忌神，父为子之忌神是也。主神眼前小事日千寻，代友占亲看纳音。疾病官非详本命，字中"
      + "末笔主终身。（假如占眼前出行求财等事，俱以日干生克字中笔画为主。如替人问事，以本日纳音为主。"
      + "如疾病官非，又以本人年干为主。如占自己终身，俱以末后一笔为主，看生克衰旺而详占之。）用神官鬼"
      + "父母才兄子，据事参详要仔细。认定一笔作用神，此为相字真消息。（假如占功名用官鬼，占生意用财爻"
      + "，据事而取用神，只以一笔为主，详其旺相休囚以定吉凶。）";   // 卷之五·神煞诸神（贵神/喜神/福星/文星/印星/马星/禄神/会神/生神/亡神/家神/官符/灾煞/天狗煞/科名星/丧门/空亡/宜神/忌神/主神/用神）

    private static String JUAN_WU_QI_YAN_ZUO_YONG_GE =
        "七言作用歌用神加直五行真，谋望营为百事成。疾病官非兼口舌，纵逢凶处不成凶。（凡金木水火土真字"
      + "，皆宜用，乃五行真遂，诸事皆利。）年干所属是科名，禾斗皆为首占星。有此求名皆遂意，如无考试定"
      + "成空。（凡占功名，必要科名入数，再兼官鬼文书动而旺相，功名可成。如无，科名莫许。）求谋之数禄"
      + "神临，始断今科考事兴。若遇科名同在数，自然高荐遂生平。（禄神即甲禄在寅是也。）有田有日会神兴"
      + "，见客逢人不必寻。马星原是弯弓脚，四点原来用亦同。（凡谒贵寻人，俱要会神，行人俱要马星妙。）"
      + "士头口体喜神俱，嫁娶婚姻百事宜。只怕重重见火土，许多克伐反非奇。（“士”属土怕木，“口”属金"
      + "怕火，所以见木土反非奇也。）笔清墨秀琢磨深，方正无偏必缙绅。疾走龙蛇心志远，行藏慷慨位三公。"
      + "又字兼骨格有精神，窗下工夫用得深。笔迹丰肥金见火，诗书队里久陶镕。又金木重重见贵神，笔挥清楚"
      + "主聪明。耸直一行冲宝盖，富贵荣华日日新。又方圆端正笔无尘，年少登科入翰林。只恐弱木逢金克，缠"
      + "身病疾不明萌。又木形之字有精神，可云发达耀门庭。火多年少心多燥，水盛为人智必清。又一直居中勇"
      + "更明，少年黾勉得功名。末笔再逢金土厚，为官享禄更廉明。又笔端势小事无成，粗俗须知业不精。起头"
      + "落尾如莺嘴，心里奸谋刻薄人。又土形之字活而圆，用神清楚是英贤。笔底到头无间断，一家荣耀有余钱"
      + "。又字贬无神笔更联，公门吏卒度余年。勉强操觚无实学，欺人长者被人嫌。又战兢惕厉若临渊，静里修"
      + "持反有年。写笔果然无俗气，终须榜上有名填。又日月当头笔迹强，精神骨格字无伤。国家梁柱何消息，"
      + "更有奇衷佐圣疆。又衣食身旁黑带浓，最嫌软弱与无神。字中人口如枯暗，莫待长年主恶终。又下笔头高"
      + "志必雄，落头不是正经人。尖头秃尾人无智，老死衙门不得名。又一字忙忙写未全，有头无尾不须言。作"
      + "事率然多失错，琢磨早失在当年。又字无骨格少精神，一生多耗病沉沉。问名带草索连就，满腹文章亦落"
      + "空。又草写香花定主贫，弱软干枯受苦辛。于中若是为官客，几日新鲜一旦倾。";   // 卷之五·七言作用歌

    private static String JUAN_WU_BI_LI_GE_XI_JIANG_YUE =
        "比例歌一斗日来占事不差，无心书鬼状元家。功名第二推为政，舜字登科作探花。二辰时执笔若书才，大"
      + "振声名事必来。正午书言真是许，水旁写半见泮开。三逢三书八士能成，照例推之理便通。申车不乱推联"
      + "捷，数逢三一始为真。四二人同到独书余，一定其间事必徐。问失执金知是铁，始为一举反三隅。此例之"
      + "类，不过详其理也。暂录四首，为后学之门。余仿此。西江月要见卦爻衰旺，端详其内章图。欲知事物识"
      + "天机，细把《玄黄篇》记。临占观形察物，叶音即义断之。若逢王者世为奇，君免猜疑直示。";   // 卷之五·比例歌＋西江月

    private static String JUAN_WU_ZHAN_LI =
        "易理玄微昔李淳风占赤黑二马入河，人问二马何先起。有人演得离卦云：“离为火，火赤色，赤马先起。"
      + "”李曰：“火未然，烟先发，黑马先起。”果然。断扇占昔有一妇，其夫久客不归，因请李淳风先生处求"
      + "断易数。适值他出，问其子，其子见妇手中携一扇，其扇面忽然落地，因断曰：“骨肉分离，不得相见矣"
      + "。”妇泣而归，恰路遇李淳风先生。妇诉其故，李断曰：“穿衣见父，脱衣见夫。不妨，尔夫今日必到。"
      + "”将晚，果然至家。可见各解不同，其断精微若此。买香占酉年八月二十五日午时，有杨客买香。康节曰"
      + "：“此香非沉香。”客曰：“此香真不可及。”康节曰：“火中有水，水泽之木，非沉香也。恐是久阴之"
      + "木，用汤药煮之。”客怒而去。半月后有宾朋至，云是清尾人家做道场，沉香伪而不香。康节曰：“香是"
      + "何人带来，但问其故，我已先知之矣。”伯温令人去问，果是杨客。康节曰：“前日到门首，因观之。未"
      + "问之前先失手，其香坠地，故取年月日时占之，得睽之噬嗑。暌下卦属兑，兑为泽。噬嗑下卦属震，震为"
      + "木，乃水泽之木，即非沉香。睽卦上互得坎，坎为水；下互得离，离为火。上有水即汤，噬嗑卦上互见坎"
      + "，坎为水，下互见艮，艮为山，中有水，亦泽之象。此乃水泽久损污湿之木，以汤煮之。此理可晓。从此"
      + "大小事，不可不较其时也。”古人相字昔谢石以拆字名天下，宋高宗私行遇石，以杖于土上书一“一”字"
      + "，令相之。石思之曰：“土上加一画成王字，必非庶人。”疑信之间，帝又画一“问”字，令相之。为田"
      + "土所梗，两旁俱斜侧飘飞。石尤惊曰：“左看是君字，右看是君字，必是主上。”遂下拜。上曰：“毋多"
      + "言。”石俯伏谢恩，帝因召官之。次日，召见偏殿，书一“春”字命相。石奏曰：“秦头太重，压日无光"
      + "。”上默然。时秦桧弄权，适忤桧，竟贬之边远。途间遇一女子，云能拆字。石怪曰：“世间复有如我拆"
      + "字者乎？”遂书“谢”字，令相之。女曰：“不过一术士耳。”石曰：“何故？”女曰：“是寸言中立身"
      + "尔。”石又书一“皮”字。女曰：“石逢皮即破矣。”盖押石之卒即皮姓也。石大惊服，曰：“吾亦能相"
      + "字，汝可书字，吾相之。”女曰：“吾在此即字矣。请相。”石曰：“人傍山立，即仙字。汝非仙乎？”"
      + "女笑而忽失。盖世有妙术，术有妙理，在人心耳。然数定，固莫能逃也。后石竟不返。张乘槎善相字，浙"
      + "江旧有拱北楼，王参政莅淅，改为“来豐楼”。初揭匾，命槎占之，槎曰：“殀矣！尚何占哉！”是晚，"
      + "讣音果至。异日叩之故，槎曰：“豐字之形，山者墓所也。二丰者，冢上树也。豆者，祭器也。其兆如此"
      + "，岂非死乎！”刘尝心有所欲占，延槎而不言其事，但令射之，以验其术。槎曰：“书一字方可占。”适"
      + "有小学生在旁习字，正写《千字文》，至“德建名立”一句，刘就指“德”字令占之。槎曰：“子欲占行"
      + "人耳。”刘曰：“然。何时当至？”槎曰：“自今十四日必来。”刘曰：“恐事不了，不肯来。”槎曰："
      + "“一心要行。悉如所占。”刘问故，槎曰：“德字双立人，乃行人也，故知占行。旁有十四字头，故云十"
      + "四日。其下又有一心字形，所以云一心要来也。”裴晋公征吴元济，掘地得一石，有字云：“鸡未肥，酒"
      + "未熟。”相字者解曰：“鸡未肥，无肉也，为己；酒未熟，无水也，酒去‘氵’为酉。破贼在己酉。”果"
      + "然。唐僖宗改为广明元年。相字者曰：“昔有一人，自崖下出来，姓黄氏，左足踏日，右足踏月，自此天"
      + "下被扰也。”是年黄巢在长安作乱，天下不安。宋太宗改元太平兴国。相字者曰：“太平二字乃一人六十"
      + "寿也。”太宗果享六十而崩。周尚干年终将换桃符，制十数联，皆不惬意。周梅坡扶箕降紫姑仙，得两句"
      + "云：“门无公事往来少，家有阴功子孙多。”甚喜，大书于门。相字者曰：“每句用上三字，其兆不祥。"
      + "”上句云“门无公”，是年尚干卒于官，乃父致政亦卒，乃兄卒，俱无子。“门无公”、“家有阴”，兆"
      + "于先矣。断富贵贫贱要诀凡字写得健壮，其人必发大财，有田土好产。二画一点者，多贵为官食禄，不然"
      + "亦近贵。才字中或多了一画、一撇、一捺，亦主横发财禄多，遇异贵，得成名利。或少了一画、一撇、一"
      + "捺，其人破荡弃祖，自立成败。如“名”、“目”字，写得如法正当，无缺折者，其人有名分。笔多清贵"
      + "虚名。上笔多，富而贵。字中有画当短而长，其人慷慨，会使钱近贵。字画直长而短，其人鄙吝，一钱不"
      + "使。字有悬针，或直落尖，皆刑六亲，伤害妻子。横画两头尖者，伤妻。直落两头尖者，伤子。字捩画少"
      + "者，孤捩。画不沾者亦孤，为僧或九流。如见十字两头尖者，穿心六害，刑妻子兄弟，骨肉皆空。字中点"
      + "多者，主人淫滥漂荡，贪花好色，居止不定。十字下面脚不失者，晚得子方。如见上一画重者，平头杀，"
      + "亦难为六亲。轻者初年不足，中、末如意。或点重者，为商旅发财，离乡失井，出外卓立。若水命、金命"
      + "见点画轻者，或早年有水灾，捩者无安身之地，作事成败，主恶死不善终。直落多者，聪明机巧，为手艺"
      + "之人，白手求财。画多者，必有心胁脾胃之疾。木多有心气之疾，晚年见之。写口字或四围有口开者，有"
      + "口舌，旬日见之，或破财不足。发字头见者，末主发财。一字分作三截，上中下三主断之。士头文脚，主"
      + "有文学。金笔灵，或见于干戈字脚者，必是用武之士。凡妇人写来字画不正者，必是偏室，或带三点，必"
      + "有动意，如三之类。凡写字之人偶然出了笔头，此事破而无成。或近火边写字，必心下不宁。或写字用破"
      + "器添砚水，家破人亡。或写字时，犬来左右吠，不吉。或取纸来写破碎者，主有口舌。或写字时猫叫，此"
      + "人有添丁之喜。或在楼上写来问者，主有重叠之事。或在船上写来问者，主有虚惊。或扇上写来问者，夏"
      + "吉冬不吉。如本命属金，金笔多者贵，土笔多者富。五行生克亦然。余仿此。";   // 卷之五·易理玄微＋断扇占＋买香占＋古人相字＋断富贵贫贱要诀

    private static String JUAN_WU_BIAO =
        "五行四时旺相休囚表五行相生地支木生在亥火生于寅金生于巳水土长生居申天干地支所属五行甲乙寅卯属"
      + "木丙丁巳午属火戊己辰戌丑未属土庚申辛酉属金壬癸亥子属水论八卦性情乾健也坤顺也震起也艮止也坎陷"
      + "也离丽也兑说也巽入也八卦取象乾为天坤为地震为雷巽为风坎为水离为火艮为山兑为泽六十甲子纳音歌甲"
      + "子乙丑海中金，丙寅丁卯炉中火。戊辰己巳大林木，庚午辛未路旁土。壬申癸酉剑锋金，甲戌乙亥山头火"
      + "。丙子丁丑涧下水，戊寅己卯城头土。庚辰辛巳白腊金，壬午癸未杨柳木。甲申乙酉井泉水，丙戌丁亥屋"
      + "上土。戊子己丑霹雳火，庚寅辛卯松柏木。壬辰癸巳长流水，甲午乙未沙中金。丙申丁酉山下火，戊戌己"
      + "亥平地木。庚子辛丑壁上土，壬寅癸卯金箔金。甲辰乙巳覆灯火，丙午丁未天河水。戊申己酉大驿土，庚"
      + "戌辛亥钗钏金。壬子癸丑桑柘木，甲寅乙卯大溪水。丙辰丁巳沙中土，戊午己未天上火。庚申辛酉石榴木"
      + "，壬戌癸亥大海水。六十四卦次第歌乾坤屯蒙需讼师，比小畜兮履泰否。同人大有谦豫随，蛊临观兮噬嗑"
      + "贲。剥复无妄大畜颐，大过坎离三十备。咸恒遁兮及大壮，晋与明夷家人睽。蹇解损益夬姤萃，升困井革"
      + "鼎震继。艮渐归妹丰旅巽，兑涣节兮中孚至。小过既济兼未济，是为下经三十四。《系辞》八卦类象歌乾"
      + "为君兮首与马，卦属老阳体至刚。坎虽为耳又为豕，艮为手狗男之详。震卦但为龙与足，三卦皆名曰少阳"
      + "。阳刚终极资阴济，造化因知不易量。坤为臣兮腹与牛，卦属老阴体至柔。离虽为目又为雉，兑为口羊女"
      + "之流。巽卦但为鸡与股，少阴三卦皆相眸。阴柔终极资阳济，万象搜罗靡不周。浑天甲子定局（以上四宫"
      + "属阳，皆从顺数）（以上四宫属阴，皆从逆数）右诀从下念上，一如点画卦爻法，学者宜熟读之。后天时"
      + "方①“败”，堂本作“散”。②“生”，校本作“位”。八反格问喜何曾喜，问忧未必忧。问乐何曾乐，"
      + "问愁何曾愁。问死何曾死，（心怀死必活。）问生不曾生。问官官不谐，见财财不成。";   // 卷之五·五行四时旺相休囚表＋论八卦性情＋八卦取象＋六十甲子纳音歌＋六十四卦次第歌＋《系辞》八卦类象歌＋浑天甲子定局＋后天时方＋八反格

    private static String JUAN_WU_SI_YAN_DU_BU =
        "四言独步看字之法，毫不可差。下笔是我，其余是他。子孙父母，官鬼要财。兄弟之类，次序安排。详占"
      + "一事，先看用神。或强或弱，详断吉凶。用神健旺，事所必宜。用神衰弱，必失其机。字无用神，始推末"
      + "笔。末笔参差，诸事不立。土头中贝，日月大人。字中有像，便是贵人。贵人在爻，祸事必消。逢险可救"
      + "，财利必招。左右有人，功名可许。笔法轩昂，上人荐举。求财取债，金忌火多。再逢夏月，本利消磨。"
      + "五行俱全，人事宜然。用神清楚，妙不可言。相争词讼，字详结尾。两笔分明，胜负立剖。字可平分，讼"
      + "不成凶。人居圈内，缧绁之中。青龙在数，求谋不误。若无水来，反为无助。玄武自来，水上生财。白虎"
      + "同至，惹祸招灾。朱雀临头，文书已动。事在公门，不与人共。末勾叠叠，口舌重重。若无救助，毕竟成"
      + "凶。水冷金寒，亲戚无缘。求谋末遂，作事迁延。五行正旺，财利可求。吉神相助，万事无忧。土内埋金"
      + "，功名未遂。或者水多，前行可贵。人病在床，木被金伤。六神不动，毕竟无妨。字不出头，蹭蹬乖蹇。"
      + "五行有救，渐渐可展。字无勾踢，人必平安。凶神乱动，好处成难。末后一笔，一身之原。如无破绽，福"
      + "寿绵绵。一字联络，骨肉同门。孤悬一点，游子飘蓬。金得炉锤，方成器皿。木无金制，可曰愚农。木从"
      + "土出，受人培植。水中浮木，波浪成风。落笔小心，作事斟酌。小心太过，为人刻薄。写来粗草，放荡之"
      + "人。笔端熟溜，书记佣工。字法龙蛇，仕途已往。秀而不俗，文章自广。风流笔法，好逞聪明。写来透古"
      + "，腹内不空。墨迹滞涩，学问难夸。一笔无停，定是大家。灯前窗下，岁月蹉跎。禾麻菽麦，俱已发科。"
      + "字无倚靠，不利六亲。字无筋节，事可让人。直仰两足，奔波劳碌。摆尾摇头，必满意足。字问日期，切"
      + "勿妄许。有丁有日，方可说与。山田草木，咸不宜冬。星辰日月，乃怕朦胧。真正五行，不怕相克。直如"
      + "用神，求谋易得。笔法未全，作事多难。行人不至，音信杳然。水火多源，木枯无枝。子孙宗派，于此可"
      + "思。终身事业，我即用神。生我者吉，克我者凶。字只两笔，寿年不一。有撇七三，无撇六一。字如三笔"
      + "，亦各有数。常为十六，变为念五。无勾为变，有勾为常。依斯立法，仔细推详。字不出头，寿增五岁。"
      + "当头一点，须减三年。字若无勾，添九可求。字如无直，寿当增十。笔画过半，须知减点。一点三年，岁"
      + "数可免。耳畔成三，口头除四。明彻斯传，始精相字。妙诀无多，功非一日。仔细详占，万无一失。";   // 卷之五·四言独步

    private static String JUAN_WU_WU_YAN_ZUO_YONG_GE =
        "五言作用歌断事不可泥，变通方是道。细细察根源，始识先贤奥。十人写一字，笔法各不同。一字占十事"
      + "，情理自然别。六神无变乱，五行有假真。草木看时节，日月察晦明。字中有子孙，子孙必不少。详其盛"
      + "与衰，便知贤不肖。我克不宜多，多必妻重娶。克我一般多，谐老又可许。青龙值用神，万事皆无阻。若"
      + "是无水泽，犹为受用苦。白虎值用神，吉事反成凶。官事必受害，疾病重沉沉。用神见朱雀，利于公门中"
      + "。君子功名吉，小人口舌凶。用神见腾蛇，俱是文书动。功名眼下宜，富贵如春梦。末笔是青龙，万事不"
      + "成凶。名利皆如意，行人在路中。末笔是朱雀，公事有着落。只恐闺门中，有病无良药。末笔是勾陈，淹"
      + "留费苦心。行人音信杳，官讼混如尘。末笔是腾蛇，远客即来家。忧疑终不免，官讼苦嗟吁。末笔是白虎"
      + "，疾病须忧苦。狱讼必牵缠，出往多拦阻。末笔是玄武，盗贼须提防。水土行人利，家中六畜康。末笔看"
      + "五行，所用看六神。先定吉凶主，然后字中寻。";   // 卷之五·五言作用歌

    private static String JUAN_WU_BIE_LI_PIAN =
        "别理篇字义浑论，辨别之篇须下学。理研变化，至诚之道可前知。字同事不同，不宜此而宜彼。事同字亦"
      + "同，倏变吉而变凶。设若中也者，天下之大本。问终身与昆仲，无缘信乎哉。人间之最要，欲要之于朋友"
      + "。更切再如地天为泰，不遇阳间犹是否。雷火为丰如逢阴极可云临。既虚矣，复反而为盈；既危矣，复还"
      + "而为安。时盛必衰，天地不逾其数；治极而乱，圣人能预为防。先则看其笔端，然后察其字义。须知字义"
      + "古怪，学问宜深。笔走龙蛇，峥嵘已过。龙身草草，非正途显达之官。豹字昂昂，是执殳荷戈之职。志无"
      + "心，定是漂蓬下士。斌不乱，始称文武全才。贝边月下定归期，足畔口头人必促。团团宝盖，多生富贵之"
      + "家。济济冠裳，定是风云之客。无事生非因北字，有钱不享是亨来。合则婚事难成，力乃功名末妥。以他"
      + "人问子，男女皆空。书本姓求官，声名远播。书先觅物终须失，写望追人定是亡。马字偏斜，惟恐落人之"
      + "局。口头阔大，定招闲事之非。青字有人求作主，事可全于月杪。妙字一女欲于归，少亦可出闺门。天字"
      + "相联，一对良缘先注定。好字相属，百年美眷预生成。丁寸等字，皆才不足之形。占吉之类，皆告不成之"
      + "象。香开晨昏扬誉远，花占百事一番新。小为本分之人，大是虚名之士。赤子依亲，是每一例可推。大人"
      + "盖小，因余仿斯可断。贝左一生多享福，空头半世受孤寒。东西南北，欲就其方。左右中前，乃择其地。"
      + "一人旁立，求名是佐贰之官。一直居中，占身乃正途之士。草木逢春旺，鱼龙得水舒。远字走长人未到，"
      + "动旁撇短去犹迟。赤子儿曹之类，必利见大人。公祖父师之称，则相逢贵人。孑则立身无寸地，永如立志"
      + "有衣冠。操为一品之才，饮定大人之食。之非出往必求财，者不呼卢定六畜。奇欲立而不可，用非走而不"
      + "通。口居中，俨然一颗方印。元落后，前程可定魁名。体用昂昂，功名之客。性情亟亟，荼苦之懦。朔邦"
      + "还未入朝廊，田里多应在乡党。活泼泼鸢鱼，是飞腾之象；乐滔滔凫雁，为流荡之徒。川上皆圣贤游乐之"
      + "余，周行是仕宦经由之道。崔巍远人犹在望，平安近事不能成。日小见天长，心粗知胆大。归则归兮归则"
      + "止，笑如笑兮笑成悲。国字谓何？一口操戈在内。尔来何故？五人合伙同居。火字乃人在水中，一遇羊头"
      + "为尽美。天字是人居土内，出头一日始逢春。以余字问，必有；以有字问，反无。龙虽在天在田，看笔迹"
      + "如何布摆。师既容民畜众，察精神始识兴衰。盖载有人，终享皇家福。伞带全备，定是极品官。有撇断为"
      + "兄弟，无点莫问儿孙。工欲善其事而成艺，何不见其人而亦可？女子并肩生意好，色糸同处病将亡。犯岁"
      + "君之名，灾殃不小。书童问卜之日，财利可兴。理中变化深长，此乃规矩方圆之至。字理机关悠远，须认"
      + "精粗为化造之原。";   // 卷之五·别理篇

    private static String JUAN_WU_LIU_YAN_POU_DUAN_GE =
        "六言剖断歌事从天地之义，字乃圣贤之心。静里功夫细阅，其中奥理无穷。圆融莫测其变，来去莫阻其通"
      + "。笔法先详衰旺，得意始定吉凶。干枯软小为衰，清秀坚昂为旺。详其用神何如，吉凶自然的当。寿夭定"
      + "于笔墨，取其多寡为占。字如十笔以上，一笔管之六年。字如十笔以下，一笔定其九岁。若在五笔之间，"
      + "一笔管十六年。笔画过之十五，两笔折作一笔。带草一笔相连，问寿只在目前。笔迹清而拘束，必然游庠"
      + "在学。笔端浊而放荡，功名必无着落。写来笔法圆活，为人处世谦和。笔底停而又写，为人性慢心多。举"
      + "笔茫无所措，胸中学问不大。若无写罢复描，行事可为斟酌。富贵出于精神，英雄定于骨格。末后一笔丰"
      + "隆，到老人称有德。占妻先看其妻，占子先看其子。妻子察其旺衰，据理定其生死。父兄官灾讼狱，父兄"
      + "要值空亡。如若父兄在数，父兄反见灾殃。一切谋望营求，字要察其虚实。有声无物为虚，有物可见是实"
      + "。书出眼前之物，察其司重司轻。司重断为有用，司轻大事无成。纳采于归等事，更要加意推详。笔画计"
      + "其单双，字义察其阴阳。假如子字求子，须防日建逢女。子日如画女字，婚姻百事皆许。一字笔画未全，"
      + "万事不必开言。字中若有余笔，必须用意详占。先用五行工夫，后穷增减字理。影响毫发无差，谬则难寻"
      + "千里。学者变化细推，断事无不灵应。";   // 卷之五·六言剖断歌

    private static String JUAN_WU_GE_WU_ZHANG =
        "格物章物格而后知致，本末须详。事来必先见诚，始终可断。细而长者，以一尺为百年，计寸分而知寿算"
      + "。方而圆者，以千金比一两，度轻重以定荣枯。落手银圈，放荡终不改。出囊珠石，峥嵘半世尚丰盈。石"
      + "土不逢时，谓之无用。木金全失气，枉自徒劳。执墨问功名，研求之苦日见不足。端鼎比身命，近贵之体"
      + "一世非轻。腰下佩觿，所求皆遂。道旁弃核，百事无成。取草问营谋，逢春须茂盛。将银问财帛，有本恐"
      + "消磨。素纨无诗，当推结识疏。牙签托人，毕竟不顾我。数珠团圆到底，夫妻儿女皆宜。木鱼振作不常，"
      + "父母兄弟难合。力下行人来得快，笔占远处有施为。求子息，圆者不宜空。占买卖，长者终须折。衣衫则"
      + "包藏骨肉，葬祭之事宜然。绦带必系纨扇躯，牵缠之事未免。舟车骡马，用之则行。婢仆鸡鹅，呼之便至"
      + "。金扇之类，收有复展之期。烹调之物，死无再生之理。瓜果问事，破不重圆。棋子求占，散而又聚。荡"
      + "尘理乱，无逾金篦牙签。释罪沉冤，俱是何章刀笔。壶是主人之礼，觞则空而满，满而复空。锁为君子之"
      + "防，匙则去而来，来而复去。文章书籍，非小人用之，筐筥犁耙，岂君子用之。惯执鞭所忻慕焉，富而必"
      + "可求也。能弹琴，复长啸，尔乐亦在其中乎？娱指悬匏，功名少待。折来垂柳，意兴多狂。竹杖龙头，节"
      + "义一生无愧怍。木锥莺嘴，钻谋万物有刚强。手不释正业经书，自知道德修诸己。问不离九流艺术，意在"
      + "干戈省厥躬。指庭前向日之花，倏忽坐间移影。点槛外敲风之竹，晨昏静里闻音。君子执笙簧，陶陶其乐"
      + "，舌鼓终须不免。女人拈针线，刺刺不休，心牵毕竟难蠲。出匣图书行欲方，眼下可分玉石；执来宝剑心"
      + "从利，手中立剖疑难。羽扇纶巾，须知人自山中去；奇珍异宝，可断人从海上来。百草可活人，不识者不"
      + "可妄用；六经能裨世，未精者焉敢施为？指盂中之水，久不耗而则倾。顾冶内之金，须知积而复用。事非"
      + "容易，一首词两下欣逢。学识源渊，几句话三生有幸。执金学道，借服为聚物之囊。割爱延师，重身如无"
      + "价之宝。明心受业，既行束上之修。寄束言传，莫废师尊之礼。斯其人也，斯其义也，可以为之，非其重"
      + "焉，非其道焉，孰轻与尔？";   // 卷之五·格物章

    private static String JUAN_WU_WU_LI_LUN =
        "物理论三才始判，八卦攸分。万物不离于五行，群生皆囿于生气。羲皇为文字之祖，苍颉肇书篆之端。鸟"
      + "迹成章，不过象形会意。云龙结篆，传来竹简添书。秦汉而返，篆隶迭易。钟王既出，楷草出名。其文则"
      + "见于今，其义犹法于古。人备万物之一数，物物相通。字泄诸人之寸灵，人人各异。欲穷吉凶之朕兆，先"
      + "格物以致知。且云天为极大，能望而不能亲，毕竟虚空为体；海是最深，可观而不可测，由来消长有时。"
      + "移山拔树莫如风，片纸遮窗可避。变谷迁陵惟是水，尺筒无底难充。小弹大盘，日之远近，不辨白衣苍狗"
      + "，云之变化非常。雨本滋长禾苗，不及时，民皆额蹙。雪能冻压草木，如适中，人喜丰年。月行急疾映于"
      + "江，莫向水中捞捉。星布循环周八极，谁从天上推移。露可比恩，厌浥行人多畏。霞虽似锦，膏肓隐士方"
      + "宜。皜皜秋阳，炎火再逢为亢害。涓涓冬月，寒冰重见愈凄凉。顽金不畏洪炉，潦水须当堤岸。雾气空濛"
      + "推障碍，电光倏忽喻浮生。月下美人来，只恐到头成梦。雪中寻客去，犹防中道而归。白露可以寄思，迅"
      + "雷闻而必变。履霜为忧虞之渐，当慎始焉。临渊有战惕之心，保厥终矣。蝃莫指，闺门之事不宜。霖雨既"
      + "零，稼穑之家有望。阳春白雪，只属孤音。流水高山，难逢知己。至于岩岩山石，生民具瞻。滚滚源泉，"
      + "圣贤所乐。瀑布奔冲难收拾，溪流湍激不平宁。风水所以行舟，水涌风狂舟必破。雨露虽能长物，雨霪露"
      + "结物遭伤。社稷自有人求，关津诚为客阻。烟雾迷林终有见，江河出峡去无回。桃夭取妇相宜，未利于买"
      + "僮置畜。杨柳送行可折，尤喜于赴试求名。松柏堪问寿年，拟声名则飘香挺秀。丝罗可结姻好，比人品则"
      + "倚势扳援。荷方出水，渐见舒张。梅可调羹，未免酸涩。李有道傍之苦，榄余齿末之甘。笔墨驱使，时日"
      + "不长。盆盂装载，团圆难久。绠短汲深求未得，戈长力弱荷难成。屠刀割肉利为官，若问六亲多刑损。利"
      + "刃剖瓜休作事，如占六甲即生男。无人棺椁必添丁，有印书函终见折。厘等则骨贮匣中，纵有出时还须入"
      + "。算盘则子盈目下，任凭拨乱却成行。瓦只虑其难全，杯亦防其有缺。席可卷舒，终归人下。伞能开合，"
      + "定出人头。钓乃小去大来，樵则任重道远。素珠团聚，可串而成。蜡烛风流，不能久固。针线若还缝即合"
      + "，锹锄如用必然翻。凿则损而为材，亦当有斗，锯乃断而成器，岂谓无长。又若飞走之声沉，亦关人事之"
      + "休咎。猢狲被系，还家终是无期。鹦鹉在囚，受用只因长舌。鸽乃随人饮啄，纵之仍入樊笼。马虽无胆驰"
      + "驱，用之不离缰锁。鲤失江湖难变化，燕来堂屋转疑难。诉理伸冤，逢鸦不白。占身问寿，遇鹤修龄。万"
      + "物纷纭，理则难尽。诸人愿欲，志各不同。若执一端以断人，是犹胶柱鼓瑟。能反三隅而悟理，方称活法"
      + "圆机。心同金鉴之悬空，妍媸自别。智若玉川之入海，活泼自如。鬼谷子曰：“人动我静，人言我听。”"
      + "旨哉斯言！胡可忽诸。";   // 卷之五·物理论

    private static String JUAN_WU_WU_XING_LIU_SHEN_LUN =
        "五行六神辨别论先以五行为主，次向字中详祸福。继将六神作用，方观笔迹察原因。生克不容情，莫以字"
      + "音称独美。宜忌须着意，休将文义恃能言。勿取吉字言吉，当认吉中多忌煞。漫将凶字言凶，须详凶处有"
      + "元神。假如青龙与白虎同行，求功名大得其宜。如庶人得之，反不免相争之咎。父母与妻子聚面，问赴选"
      + "难从其志。若游子占之，又可触思远之忧。勾陈最忌小金连，惟恐事无间断。朱雀若逢傍水克，须防祸有"
      + "牵缠。水在木中流，替人濯垢。木从水中出，脱体犹难。五行全不犯凶神，问自身德建名立。六神动再加"
      + "吉将，若求官体贵身荣。旧事重新，朱、腾双发动；倾家复创，金、土两重临。微火镕金，难成器皿。弱"
      + "金克木，反自损伤。求济于人，要看水火会合。营谋于众，还祈土木齐登。金多子多，非土不得。土厚财"
      + "厚，无火不生。水冷木孤，弟兄难靠。金寒土薄，祖业凋零。玄武形青龙得水，连登两榜。白虎尾朱雀衔"
      + "金，位列三公。玄武临渊，时中之化雨。青龙捧日，阙下之云腾。水非白而无源，金不秋而失气。有勾陈"
      + "，难结案头文。见朱雀，忖量堂上语。田下土深，思还故里。月边水盛，意在归湖。玄武居中，出外不宜"
      + "行陆路。勾陈定位，居官虽在受皇恩。白虎重重，不敢保今年无事。青龙两两，定不是此日燕居。字中见"
      + "母母无忧，笔下从兄兄定在。水土形青龙翘首，何忧不得功名。木相金白虎当头，毕竟难逃灾害。重重金"
      + "火不逢时，百事徒劳。叠叠青黄非见日，几番隆替。贵显招土木，万福皆隆。方体隐龙蛇，千祥并集。朱"
      + "、勾相合，主唇舌干戈之事。龙、虎同行，风云际会之荣。玄武不遇火，阴中不美。腾蛇无水渡，郊外生"
      + "悲。纯土自能生官，福从天至。寒金不但无禄，灾自幽来。天贵专权，问功名必登黄甲。文书不动，赴场"
      + "闱定值空亡。问子须求子在爻，占妻定要妻入数。笔迹孤寒金带水，六亲一个难招。字形丰满土生金，百"
      + "岁百年易盛。看五行之旺弱切记，卜词讼以官鬼为先。定六将之机微须知，占家宅以本命为主。五行俱有"
      + "，凡谋皆遂。六神不动，万事咸宁。细玩辞占影响，无差毫发。密搜奥义规绳，不爽纤微。";   // 卷之五·五行六神辨别论

    private static String JUAN_WU_JIN_SHENG_ZHANG =
        "金声章混沌未开，一元含于太极。无形之始，乾坤既判，万物成于文章著见之中。故未有其事，而先有象"
      + "，可预得其体而兆其来。所以苍颉制字，接云霞蝌蚪之文。至圣著书，采随宜义理而用。一字之善，千古"
      + "流传。半点之疵，万年不泯。君子哉，非挥毫而莫辨；小人焉，一执笔而即知。是以消长盛衰，因极而知"
      + "变；吉凶祸福，至诚而见神。写来江汉秋阳，皜皜乎不可尚已。意在螽斯诜羽，绳绳兮与其宜焉。惟存好"
      + "利喜衰，则落笔终须各别。必欲离尘脱俗，而开首自是不同。若烟雾云霞，则聚散去来神变化；风雷日月"
      + "，其盈虚消息妙裁成。鹦鹉等禽，人皆云其舌巧；虎豹之类，谁不惧其张威？生息蕃盛者，乃稼穑禾苗；"
      + "与物浮沉者，是江河湖海。渊中鱼跃，水向东流何沮止？天上鸢飞，日从西落四时同。群兽俱胎之生，独"
      + "报麟祥之喜；诸禽皆飞腾之物，只言凤德之衰。禽之鸣也噪也，有形小体大之分；兽之利也钝也，有轻清"
      + "重浊之别。香花灯烛，偏宜于朔望之时；铃铎鼓钟，独可于晨昏之际。点点滴滴，万里征衫游子泪；层层"
      + "叠叠，九行密线老人心。至于犬豕牛羊，叱之即便去；鸡鱼鹅鸭，欲用则不生。狐貉羔裘，无济于夏；红"
      + "炉黑炭，偏喜于冬。幽林深圃夜无人，情不诬也；楼阁厅堂时有位，理之必然。琴书剑箱，可断儒生负笈"
      + "；轻裘肥马，当推志士同袍。墨有渐减之虞，笔恐久坚而弱。书成笔架，几上岷山。写到砚池，寓中闷海"
      + "。如在其上，秋到一天皆皎月；如在其下，春临遍地产黄金。挥出琵琶，到底是写怨之具；描来箫管，终"
      + "须为耗气之端。假如云雨雾皆能蔽日之光，天正阴时犹是吉。又若精气神本是扶身之主，人来问病反为凶"
      + "。水急流清，意偕游鱼；烟飞篆渺，心从云树茫茫。农家落笔，草盛田禾实不足；商者书笺，丝多交易乱"
      + "如麻。紫绶金章，无者不必写出。蜗名蝇利，有者即便书成。锁钥金汤，必任国家之重寄；羽毛干戚，是"
      + "祈海甸以清宁。挂锦扬幡，风顺之方必利；舒衾洒帐，雨到之候成欢。礼乐射御书数，如求一艺可执。孝"
      + "友睦姻任恤，定其六事皆宜。草木逢雨，时生而旺，要详春秋气候。轿马行际，日近而远，亦揆寒暑光阴"
      + "。试看画饼望梅，何止饥渴？镜花水月，竟是空虚。欲造字相之微，请明章中之理。";   // 卷之五·金声章

    /** 卷之五·拆字杂编·五行全备＋六神形式＋八卦辨（照录；接入节点 16）。 */
    private static String juanWuWuXingQuanBeiNote() {
        return "卷之五·拆字杂编·五行全备＋六神形式＋八卦辨——" + JUAN_WU_WU_XING_QUAN_BEI;
    }
    /** 卷之五·神煞诸神（贵神/喜神/福星/文星/印星/马星/禄神/会神/生神/亡神/家神/官符/灾煞/天狗煞/科名星/丧门/空亡/宜神/忌神/主神/用神）（照录；接入节点 16）。 */
    private static String juanWuShenShaNote() {
        return "卷之五·神煞诸神（贵神/喜神/福星/文星/印星/马星/禄神/会神/生神/亡神/家神/官符/灾煞/天狗煞/科名星/丧门/空亡/宜神/忌神/主神/用神）——" + JUAN_WU_SHEN_SHA;
    }
    /** 卷之五·七言作用歌（照录；接入节点 16）。 */
    private static String juanWuQiYanZuoYongGeNote() {
        return "卷之五·七言作用歌——" + JUAN_WU_QI_YAN_ZUO_YONG_GE;
    }
    /** 卷之五·比例歌＋西江月（照录；接入节点 16）。 */
    private static String juanWuBiLiGeXiJiangYueNote() {
        return "卷之五·比例歌＋西江月——" + JUAN_WU_BI_LI_GE_XI_JIANG_YUE;
    }
    /** 卷之五·易理玄微＋断扇占＋买香占＋古人相字＋断富贵贫贱要诀（照录；接入节点 16）。 */
    private static String juanWuZhanLiNote() {
        return "卷之五·易理玄微＋断扇占＋买香占＋古人相字＋断富贵贫贱要诀——" + JUAN_WU_ZHAN_LI;
    }
    /** 卷之五·五行四时旺相休囚表＋论八卦性情＋八卦取象＋六十甲子纳音歌＋六十四卦次第歌＋《系辞》八卦类象歌＋浑天甲子定局＋后天时方＋八反格（照录；接入节点 16）。 */
    private static String juanWuBiaoNote() {
        return "卷之五·五行四时旺相休囚表＋论八卦性情＋八卦取象＋六十甲子纳音歌＋六十四卦次第歌＋《系辞》八卦类象歌＋浑天甲子定局＋后天时方＋八反格——" + JUAN_WU_BIAO;
    }
    /** 卷之五·四言独步（照录；接入节点 16）。 */
    private static String juanWuSiYanDuBuNote() {
        return "卷之五·四言独步——" + JUAN_WU_SI_YAN_DU_BU;
    }
    /** 卷之五·五言作用歌（照录；接入节点 16）。 */
    private static String juanWuWuYanZuoYongGeNote() {
        return "卷之五·五言作用歌——" + JUAN_WU_WU_YAN_ZUO_YONG_GE;
    }
    /** 卷之五·别理篇（照录；接入节点 16）。 */
    private static String juanWuBieLiPianNote() {
        return "卷之五·别理篇——" + JUAN_WU_BIE_LI_PIAN;
    }
    /** 卷之五·六言剖断歌（照录；接入节点 16）。 */
    private static String juanWuLiuYanPouDuanGeNote() {
        return "卷之五·六言剖断歌——" + JUAN_WU_LIU_YAN_POU_DUAN_GE;
    }
    /** 卷之五·格物章（照录；接入节点 16）。 */
    private static String juanWuGeWuZhangNote() {
        return "卷之五·格物章——" + JUAN_WU_GE_WU_ZHANG;
    }
    /** 卷之五·物理论（照录；接入节点 16）。 */
    private static String juanWuWuLiLunNote() {
        return "卷之五·物理论——" + JUAN_WU_WU_LI_LUN;
    }
    /** 卷之五·五行六神辨别论（照录；接入节点 16）。 */
    private static String juanWuWuXingLiuShenLunNote() {
        return "卷之五·五行六神辨别论——" + JUAN_WU_WU_XING_LIU_SHEN_LUN;
    }
    /** 卷之五·金声章（照录；接入节点 16）。 */
    private static String juanWuJinShengZhangNote() {
        return "卷之五·金声章——" + JUAN_WU_JIN_SHENG_ZHANG;
    }

    // ------------------------------------------------------------------
    // 卷首·梅花易数序（第 28 轮补录 B74）
    //   序述邵康节得《易》书之由（鼠破瓦枕、陶家遗书、白金窖中营葬）、「观梅数」命名之始，
    //   并辨先天之数（未得卦先得数、以数起卦）与后天之数（未得数先得卦、以卦起数）。
    //   末署「秣陵德聚堂谨订」。接入节点 16「卷首序」。
    // ------------------------------------------------------------------
    private static String MEI_HUA_YI_SHU_XU =
        "宋庆历中，康节邵先生隐处山林，冬不炉，夏不扇，盖心在于《易》，忘乎其为寒暑也。犹以为未至，"
      + "糊《易》于壁，心致而目玩焉。邃于《易》理，欲造《易》之数而未有征也。一日午睡，有鼠走而前，"
      + "以所枕瓦枕投击之，鼠走而枕破。觉中有字，取视之：“此枕卖与贤人康节，某年月日某时，击鼠枕破。”"
      + "先生怪而询之陶家，其陶枕者曰：“昔一人手执《周易》憩坐，举枕其书，必此老也。今不至久矣。吾能识其家。”"
      + "先生偕陶往访焉，及门，则已不存矣，但遗书一册谓其家人曰：“某年某月某时，有一秀士至吾家，"
      + "可以此书授之，能终吾身后事矣。”其家以书授先生，先生阅之，乃《易》之文并有诀例，当推例演数，"
      + "谓其人曰：“汝父存日，有白金置睡床西北窖中，可以营葬事。”其家如言，果得金，先生受书以归。"
      + "后观梅，以雀争胜布算，知次晚有邻人女折花，堕伤其股。其卜盖始于此。后世相传，遂名“观梅数”。"
      + "又后算落花之日，午时为马所践毁；又算西林寺额，知有阴人之祸。凡此皆所谓先天之数也。盖未得卦，"
      + "先得数也。以数起卦，故曰先天。若夫见老人有忧色，卜而知老人有食鱼之祸；见少年有喜色，"
      + "卜而知有币聘之喜；闻鸡鸣知鸡必烹；听牛鸣知牛当杀。凡此，皆后天之数也。盖未得数先得卦也。"
      + "以卦起数，故曰后天。一日，置一椅，以数推之，书椅底曰：“某年月日，当为仙客坐破。”之期，"
      + "果有道者来访，坐破其椅。仙客愧谢，先生曰：“物之成毁有数，岂足介意，且公神仙也，幸坐以示教。”"
      + "因举椅下所书以验，道者愕然趋起，出忽不见。乃知数之妙，虽鬼神莫逃，而况于人乎？况于物乎？"
      + "秣陵德聚堂谨订";

    /** 卷首·梅花易数序（述得书之由与先天/后天之数之别；接入节点 16）。 */
    private static String meiHuaYiShuXuNote() {
        return "梅花易数序——" + MEI_HUA_YI_SHU_XU;
    }

    // ------------------------------------------------------------------
    // 卷之一·占法／玩法（第 28 轮补录 B75）
    //   卷之一卷首七言二首：占法戒轻传，玩法明「一物一乾坤、万物备于我」之心易大旨。
    //   接入节点 16「心法诗」。
    // ------------------------------------------------------------------
    private static String ZHAN_FA =
        "易中秘密穷天地，造化天机泄末然。中有神明司祸福，从来切莫教轻传。";
    private static String WAN_FA =
        "一物从来有一身，一身还有一乾坤。能知万物备于我，肯把三才别立根。"
      + "天向一中分造化，人于心上起经纶。仙人亦有两般话，道不虚传只在人。";

    /** 卷之一·占法／玩法（卷首七言二首，明心易大旨；接入节点 16）。 */
    private static String zhanFaWanFaNote() {
        return "占法／玩法（卷之一）——占法：" + ZHAN_FA + " 玩法：" + WAN_FA;
    }

    // ------------------------------------------------------------------
    // 卷之三·占卜克应之诀（第 28 轮补录 B76）
    //   论克应（刻期）为卦之切要：「有以数而刻之者，有以理而刻之者」；列屋宅/婚姻/坟墓/器物
    //   之刻期（远以年、近以月、又近以日），生体比和之月日为吉期、克体之月日为凶期。接入节点 13 定应期。
    // ------------------------------------------------------------------
    private static String ZHAN_BU_KE_YING_JUE =
        "克应者，所谓刻期应验也。占卜之道，无此诀，则吉凶成败之事不知应于何时。故克应为卦之切要也。"
      + "然刻期最难，有以数而刻之者，有以理而刻之者，皆要论也。以数而刻期，必详其理。如算屋宅之初创，"
      + "男女之始婚，坟墓之方葬，器物之新置，俱以此年月日时加事物之数而起卦。卦成，则于体用互变之中，"
      + "视全卦之数，以为约定之期，审其事端之迟速而刻之。如屋宅、坟墓，永久者也。屋宅则以全卦之数刻其期。"
      + "如屋宅之终应，盖屋宅有朽坏之期也。坟墓亦有损坏，然占墓但占吉凶，不计成败也。男女之婚，"
      + "远亦不过数年。年内之事，全卦之数可决，又不如屋宅之久也。然婚姻亦不过卜其吉凶，不必刻其期也。"
      + "若吉凶之期，但以生体及比和之年月为吉期，克体之年月为不吉之期也。器物之占，则金石之质终远，"
      + "草木之质终不久也。远者，以全卦之数为年期；近者，以全卦之数为月期；又近者，以全卦为日期也。"
      + "如置砚，则全卦之数为岁。计笔墨亦可以全卦为岁计乎？笔墨之小者，以日计之数可也。此器物刻期之占也。"
      + "如先天观梅与牡丹二花，俱旦夕之事，故以卦理推，则不必决其远日也。如后天老人、少年、鸡牛之占，"
      + "以方卦物卦之数合而计之。老少、鸡牛之占，亦只可以日计也。若永远之占，则以日为月，以月为年矣。"
      + "占者详吉，必又寻常之占事刻期，则于全卦中细观生体之卦为吉，应决期克体之卦为凶。应之期远，"
      + "则以年，近则以月，又近则以日也。如问求名则乾为体，看卦中有坤、艮，则断其辰、戌、丑、未之土月日。"
      + "盖乾、兑，金之体也。此为吉事生体之应。若问病而乾卦为体，则看卦中有离，又看卦中无坤、艮，"
      + "及有凶犯，则其体死于巳午火日，此克体为凶事之期也。又若问行人，以生体之日为归期，"
      + "无生体比和之日，当见归迟。若此例者，具难尽载，学者审焉。";

    /** 卷之三·占卜克应之诀（克应刻期之法；接入节点 13 定应期）。 */
    private static String zhanBuKeYingJueNote() {
        return "占卜克应之诀（卷之三）——" + ZHAN_BU_KE_YING_JUE;
    }

    // ------------------------------------------------------------------
    // 卷之二·遗论（第 28 轮补录 B77）
    //   论「未有《易》书，先有《易》理」；寓物卦数起例止用内卦，盖「十应为传授之诀」，
    //   须明其理、以外应决之（观梅得革不可日日同断）。接入节点 16「遗论」。
    // ------------------------------------------------------------------
    private static String YI_LUN =
        "物卦数，本由于《易》。今观此书，止用五行生克之理、十应三要之诀，例不同《易》，何也？"
      + "盖未有《易》书，先有《易》理。《易》书作于四圣之后，《易》理著于四圣之先。人心皆有《易》理，"
      + "则于《易》也，占卜无非用卦，卦即《易》也。若得《易》卦爻，观其爻辞卦以断吉凶悔吝，更为妙也，"
      + "未尝不用《易》。又观寓物卦数起例之篇，止用内卦，不用外卦，何也？盖起泛泛人起卦之诀，"
      + "十应为传授之诀。若观梅卦例曰：今日观梅得革，知女折花，有伤股。明日观梅得革，亦谓女子折花，可乎？"
      + "算牡丹例曰：今日算牡丹为马践毁，异日算牡丹亦为马所践，可乎？是必明其理。又于地风升卦，"
      + "无饮食之兆而知有人相请。此要外应诀之。";

    /** 卷之二·遗论（易理先于易书、须明理而外应决之；接入节点 16）。 */
    private static String yiLunNote() {
        return "遗论（卷之二）——" + YI_LUN;
    }

    // ------------------------------------------------------------------
    // 卷之二·卦断遗论（第 28 轮补录 B78）
    //   论「固以体用为主，然有不拘体用者」：西林寺额山地剥（群阴剥阳，虽比和不吉）、
    //   今日动静如何地风升（用克体而反有人请，取「如何」带口重兑之应）、少年喜色山火贲（互变俱生）、
    //   牛哀鸣地水师（互变俱克）——「用《易》断卦，当用理胜处验之，不可拘于执一」。接入节点 16。
    // ------------------------------------------------------------------
    private static String GUA_DUAN_YI_LUN =
        "凡占卜决断，固以体用为主，然有不拘体用者。如起例中西林寺额得山地剥，体用互变，俱比和，则为吉，"
      + "而乃不吉，何也？盖寺者，纯阳人居之地，而纯阴爻象，则群阴剥阳之义显然也。此理甚明，不必拘体用也。"
      + "又若有人问：“今日动静如何？”得地风升，初爻动，用克体卦，俱无饮食矣，而亦有人相请，"
      + "虽饮食不丰而终有请，何也？此人当时必有当日之应，又有“如何”二字带口，为重兑之义。"
      + "又有用不生体，互变生之而吉者，若少年有喜色，占得山火贲是也。又有用不生体，互变俱克之而凶者，"
      + "如牛哀鸣占得地水师是也。盖少年有喜色，占则略知其有喜，而《易》辞又有“束帛戋戋”之吉，"
      + "是二者俱吉，互变俱生，愈见其吉矣。虽用不生体不吉，不为其害也。牛鸣之哀，则略知其有凶，"
      + "而《易》爻复有“舆尸”之凶，互变俱克，愈见其凶，虽用爻不克，不能掩其凶也。盖用《易》断卦，"
      + "当用理胜处验之，不可拘于执一也。";

    /** 卷之二·卦断遗论（不拘体用、以理胜处验之；接入节点 16）。 */
    private static String guaDuanYiLunNote() {
        return "卦断遗论（卷之二）——" + GUA_DUAN_YI_LUN;
    }

    // ------------------------------------------------------------------
    // 卷之二·八卦心易体用诀（第 28 轮补录 B79）
    //   体用诀之序论：心易之数得之者众、体用之诀有之者罕；要在于分体用之卦、察五行生克比和之理。
    //   接入节点 16。
    // ------------------------------------------------------------------
    private static String BA_GUA_XIN_YI_TI_YONG_JUE =
        "心易之数，得之者众，体用之诀，有之者罕。余幼读《易》书，长参数学，始得心易卦数。"
      + "初见起例，以知占其吉凶。如以蠡测海，茫然无涯。后得智人见授体用心易之诀，而后占事之诀，疑始有定。"
      + "据验则验，如由基射的，百发百中。其要在于分体用之卦，察其五行生克、比和之理，而明乎吉凶悔吝之机也。"
      + "于是《易》数之妙始见，而《易》道之卦义备矣。乃世有真实，人罕遇之耳。得此者，幸甚秘之！";

    /** 卷之二·八卦心易体用诀（体用诀序论；接入节点 16）。 */
    private static String baGuaXinYiTiYongJueNote() {
        return "八卦心易体用诀（卷之二）——" + BA_GUA_XIN_YI_TI_YONG_JUE;
    }

    /** 本卦阴阳爻多寡（卷之三·物数为体诀「阳爻多，乃多刚之物；阴爻多，乃多柔之物」）。 */
    private static int[] yinYangCount(String hexagram) {
        int yang = 0;
        if (hexagram != null && !hexagram.isBlank()) {
            for (int t : pairOf(hexagram)) {
                for (int b : bits(t)) {
                    if (b == 1) {
                        yang++;
                    }
                }
            }
        }
        return new int[]{yang, 6 - yang};
    }

    /** 观物克应法（卷之三 L1768–1769）：见圆物断圆物、见负土者断土中之物、刚健/柔腐类推。 */
    private static String objectOmenVerdict(String omen) {
        if (omen.contains("圆")) {
            return "见圆物相遇，即断是圆物";
        }
        if (omen.contains("负土") || omen.contains("土")) {
            return "见有负土者过，即断为土中之物";
        }
        if (omen.contains("刚健") || omen.contains("刚")) {
            return "见刚健之物，即言是刚健之物";
        }
        if (omen.contains("柔腐") || omen.contains("柔") || omen.contains("腐")) {
            return "见有柔腐之物，即断是柔腐之物";
        }
        return "";
    }

    // ==========================================================================
    // 卷之三·诸卦反对性情 + 卷之五·论八卦性情（节点 16 汇总「卦义性情」；B32/B33）
    // ==========================================================================

    /**
     * 诸卦反对性情（原著卷之三·诸卦反对性情 L1726–1751「杂卦性情反对是」）：
     * 杂卦两两反对之卦义/性情（乾刚坤柔、比欣师忧、升去萃聚…）。
     *
     * <p>《本体顶层设计》§2.2-3：S6「诸卦反对性情」作为**卦义属性**（卦与卦的反对关系）
     * 引入，用于断验描述，**不构成独立推演环节**（对齐 `yijing:OppositeNature`）。
     *
     * <p>注：原著此歌为「略举」体，仅涉五十余卦；歌中「随前坎后偷安矣」一句句式漫漶，
     * **随卦之句已于第二十五轮补录**（`泽雷随`＝「前·偷安」，照录备考）；未涉之卦（天泽履/水风井/泽水困/水泽节/风火家人/
     * 雷天大壮/风泽中孚/雷山小过/风山渐/山雷颐/山火贲）歌中本无，返回空串。
     */
    private static final Map<String, String> OPPOSITE_NATURE = new LinkedHashMap<>();

    static {
        OPPOSITE_NATURE.put("乾为天", "刚");
        OPPOSITE_NATURE.put("坤为地", "柔");
        OPPOSITE_NATURE.put("水地比", "欢欣");
        OPPOSITE_NATURE.put("地水师", "忧虑");
        OPPOSITE_NATURE.put("地泽临", "逢与");
        OPPOSITE_NATURE.put("风地观", "求之");
        OPPOSITE_NATURE.put("山水蒙", "难明");
        OPPOSITE_NATURE.put("水雷屯", "不失");
        OPPOSITE_NATURE.put("山天大畜", "福之生");
        OPPOSITE_NATURE.put("天雷无妄", "祸之始");
        OPPOSITE_NATURE.put("地风升", "去而不复回");
        OPPOSITE_NATURE.put("泽地萃", "聚而终不去");
        OPPOSITE_NATURE.put("地山谦", "自尊");
        OPPOSITE_NATURE.put("雷地豫", "怠人");
        OPPOSITE_NATURE.put("震为雷", "动");
        OPPOSITE_NATURE.put("艮为山", "止");
        OPPOSITE_NATURE.put("兑为泽", "主外遇");
        OPPOSITE_NATURE.put("巽为风", "内藏");
        OPPOSITE_NATURE.put("泽雷随", "前·偷安（原文『随前坎后偷安矣』，句式漫漶，照录备考）");
        OPPOSITE_NATURE.put("山地剥", "体消烂");
        OPPOSITE_NATURE.put("地雷复", "自生");
        OPPOSITE_NATURE.put("山风蛊", "改前非");
        OPPOSITE_NATURE.put("地火明夷", "内朗又逢伤");
        OPPOSITE_NATURE.put("火地晋", "主外明并通理");
        OPPOSITE_NATURE.put("风雷益", "茂盛");
        OPPOSITE_NATURE.put("山泽损", "象衰");
        OPPOSITE_NATURE.put("泽山咸", "速");
        OPPOSITE_NATURE.put("雷风恒", "迟");
        OPPOSITE_NATURE.put("风水涣", "远遁");
        OPPOSITE_NATURE.put("天山遁", "退回身");
        OPPOSITE_NATURE.put("天火同人", "内亲");
        OPPOSITE_NATURE.put("火泽睽", "外疏");
        OPPOSITE_NATURE.put("雷水解", "从容");
        OPPOSITE_NATURE.put("水山蹇", "难启");
        OPPOSITE_NATURE.put("离为火", "文美丽");
        OPPOSITE_NATURE.put("火天大有", "众");
        OPPOSITE_NATURE.put("雷火丰", "多");
        OPPOSITE_NATURE.put("坎为水", "履险");
        OPPOSITE_NATURE.put("水天需", "不进");
        OPPOSITE_NATURE.put("天水讼", "不宁");
        OPPOSITE_NATURE.put("水火既济", "一定无后虑");
        OPPOSITE_NATURE.put("火水未济", "男之终");
        OPPOSITE_NATURE.put("雷泽归妹", "妇之始");
        OPPOSITE_NATURE.put("天地否", "大往而小来");
        OPPOSITE_NATURE.put("地天泰", "大来而小去");
        OPPOSITE_NATURE.put("泽火革", "去旧故");
        OPPOSITE_NATURE.put("火风鼎", "从新");
        OPPOSITE_NATURE.put("风天小畜", "寡");
        OPPOSITE_NATURE.put("火雷噬嗑", "食");
        OPPOSITE_NATURE.put("火山旅", "羁其外");
        OPPOSITE_NATURE.put("泽风大过", "颠");
        OPPOSITE_NATURE.put("泽天夬", "分明曰快利");
        OPPOSITE_NATURE.put("天风姤", "相遇");
    }

    /** 八卦性情（原著卷之五·论八卦性情 L3559–3568：乾健/坤顺/震起/艮止/坎陷/离丽/兑说/巽入）。 */
    private static String trigramNature(String trigram) {
        if (trigram == null || trigram.isBlank()) {
            return "";
        }
        for (int i = 1; i < TRI_NAME.length; i++) {
            if (trigram.equals(TRI_NAME[i])) {
                return TRI_NATURE[i];
            }
        }
        return "";
    }

    /** 诸卦反对性情·某六画卦之句（原著卷之三·诸卦反对性情，本体/DB 优先、内置兜底）；歌中未列者返回空串。 */
    private static String hexagramOppositeNature(String hexagram) {
        return hexagram == null ? "" : loreMap("OPPOSITE_NATURE", OPPOSITE_NATURE).getOrDefault(hexagram, "");
    }

    /**
     * 节点 16 汇总·卦义性情（原著卷之五·论八卦性情 + 卷之三·诸卦反对性情）：
     * 一为 体/用/互/变 四卦之八卦性情（健顺起止陷丽说入），
     * 一为 本卦、变卦 之杂卦反对性情（乾刚坤柔/比欣师忧…）。
     */
    private String hexagramNatureLine(String original, String body, String use, String mutual, String changed) {
        LinkedHashMap<String, String> tri = new LinkedHashMap<>();
        if (body != null && !body.isBlank()) {
            tri.putIfAbsent(body, body + "（" + trigramNature(body) + "）");
        }
        if (use != null && !use.isBlank()) {
            tri.putIfAbsent(use, use + "（" + trigramNature(use) + "）");
        }
        for (String h : new String[]{mutual, changed}) {
            if (h != null && !h.isBlank()) {
                for (int t : pairOf(h)) {
                    tri.putIfAbsent(TRI_NAME[t], TRI_NAME[t] + "（" + trigramNature(TRI_NAME[t]) + "）");
                }
            }
        }
        StringBuilder sb = new StringBuilder();
        if (!tri.isEmpty()) {
            sb.append("八卦性情——").append(String.join("、", tri.values()));
        }
        String on = hexagramOppositeNature(original);
        String cn = hexagramOppositeNature(changed);
        if (!on.isEmpty()) {
            if (sb.length() > 0) {
                sb.append("；");
            }
            sb.append("反对性情——本卦").append(original).append("（").append(on).append("）");
        }
        if (!cn.isEmpty() && !cn.equals(on)) {
            sb.append("、变卦").append(changed).append("（").append(cn).append("）");
        }
        return sb.toString();
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
