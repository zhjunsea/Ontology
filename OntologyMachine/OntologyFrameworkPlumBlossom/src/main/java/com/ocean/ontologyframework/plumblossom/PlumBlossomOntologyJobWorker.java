package com.ocean.ontologyframework.plumblossom;

import com.ocean.openlletresolver.BackendService;
import com.ocean.ontopobdahandler.OBDAHandler;
import com.ocean.utilities.OntologyWorkerSupport;
import com.ocean.utilities.RabbitMqHandler;
import io.camunda.client.annotation.JobWorker;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.JobClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 梅花易数占断流程（{@code PlumBlossomDivinationProcess}）的 JobWorker。
 *
 * <p>18 个 {@link JobWorker} 与 {@code ontology/bpmn/plumBlossomProcess.bpmn} 的 18 个
 * {@code serviceTask} 一一对应（契约见《PlumBlossom应用-应用架构设计》§4）：
 * preceptCheck / preceptAlert / routeDivination / characterSplit / castHexagram /
 * determineBodyUse / deriveMutualHexagram / deriveChangedHexagram / analyzeShengKe /
 * analyzeVitality / extractImageSymbol / categorizeDivination / analyzeOmen /
 * judgeAuspiciousness / determineResponsePeriod / generateAdvice / sendAlert / aggregateResult。
 *
 * <p><b>落地口径</b>（第 11 步）：TBox 经 {@code ontology.main-path}（{@code plumblossom-all.owl}，
 * 依铁律 11 仅聚合 TBox）由 {@link BackendService#getInstance(String, OBDAHandler)} 加载；
 * ABox 的历法/干支与周易文本（卦名/卦辞/爻辞）依闸门⑩裁决**入库**，经
 * {@code ontology.obda-path} / {@code ontology.obda-properties-path}（{@code plumBlossom.obda/.properties}，
 * Ontop 虚拟知识图谱，{@code ontop.ontologyFile=ontology/plumblossom-abox.owl}）虚拟访问，
 * 不再迁入本体文件（设计《本体核心设计》§8）。
 * 起卦法的结构运算（先天数→八卦、卦以八除、爻以六除、五行生克、卦气旺衰）由本类内置表
 * （即设计 §4 所述 R15「内置映射表」）实现；本体提供六十四卦名录（经 OBDA 自库读取
 * {@code yijing:HexagramSequence}）、八卦五行/方位/性情（{@code core:Trigram} / {@code rel:FivePhase}）、
 * 四不占（{@code div:Precept}）、吉凶等级（{@code jud:Auspiciousness}）等域知识基线。
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

    /** 四不占「重卦」判定：本次 JVM 会话内已占断过的 subject（跨会话历史不追溯，见 §10-1）。 */
    private static final Set<String> SESSION_SUBJECTS = ConcurrentHashMap.newKeySet();

    // ==================== 内置表：先天八卦（乾1兑2离3震4巽5坎6艮7坤8） ====================
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

    /** 八卦万物属类关键词（原著卷之一「八卦万物属类」主象提炼），用于由象取卦。 */
    private static final Map<Integer, String[]> TRI_KEYWORDS = Map.of(
            1, new String[]{"天", "父", "老人", "官", "贵", "头", "骨", "马", "金", "宝", "珠", "玉", "圆", "刚"},
            2, new String[]{"泽", "少女", "巫", "舌", "羊", "口", "悦", "毁折", "饮食"},
            3, new String[]{"火", "日", "目", "电", "中女", "甲胄", "文书", "炉", "灶", "文彩", "枯槁"},
            4, new String[]{"雷", "长男", "龙", "竹", "足", "动", "声"},
            5, new String[]{"风", "长女", "僧", "尼", "股", "木", "草", "林", "绳直"},
            6, new String[]{"水", "雨", "雪", "中男", "耳", "血", "盗", "鱼", "盐", "酒", "溪", "井"},
            7, new String[]{"山", "少男", "童", "狗", "手", "指", "门", "石", "土"},
            8, new String[]{"地", "母", "老妇", "土", "牛", "布", "帛", "文章", "腹", "方"});

    /** 六十四卦名录（内置 R15「trigramPairMap」：[上卦][下卦]，与 yijing:Seq_01..64 互参）。 */
    private static final String[][] HEX = new String[9][9];
    private static final Map<String, int[]> NAME2PAIR = new HashMap<>();
    /** 由卦名反查 King-Wen 序（与本体 yijing:HexagramSequence 互校）。 */
    private static final Map<String, Integer> NAME2SEQ = new HashMap<>();

    private static final Map<Integer, Integer> MASK2TRI = Map.of(
            0b111, 1, 0b011, 2, 0b101, 3, 0b001, 4, 0b110, 5, 0b010, 6, 0b100, 7, 0b000, 8);

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
    }

    @PostConstruct
    public void init() throws Exception {
        log.info("🔧 初始化 PlumBlossomOntologyJobWorker 依赖链...");
        initOntologyPipeline();
        verifyOntologyKnowledge();
        log.info("✅ PlumBlossomOntologyJobWorker 初始化完成 | ontologyPath={}", mainOntologyPath);
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

    /**
     * 以本体/库中六十四卦名录（{@code yijing:HexagramSequence}）与内置表互校（第 11 步：经 OBDA）。
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

            String triggered = null;
            if (subject == null || subject.isBlank()) {
                triggered = "无事不占";
            } else if (!promise) {
                triggered = "不诚不占";
            } else if (!SESSION_SUBJECTS.add(subject.trim())) {
                triggered = "重卦不占";
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
                moving = up + low;
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
                // 乾坤无互，互其变卦（纯阳/纯阴，取变卦为互）
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
            String pBody = TRI_FIVE[triNum(body)];
            String pUse = TRI_FIVE[triNum(use)];

            int level;
            String typeText;
            if (generates(pUse, pBody)) {
                level = 1; typeText = "用生体（进益之喜·最吉）";
            } else if (pUse.equals(pBody)) {
                level = 2; typeText = "体用比和（百事顺遂·次吉）";
            } else if (overcomes(pBody, pUse)) {
                level = 3; typeText = "体克用（诸事吉·小吉）";
            } else if (generates(pBody, pUse)) {
                level = 4; typeText = "体生用（耗失之患·有耗）";
            } else {
                level = 5; typeText = "用克体（诸事凶·最凶）";
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("shengKeType", level + "·" + typeText);
            completeResult(client, job, result);
            log.info("✅ analyzeShengKe | 体={}({}) 用={}({}) → {}级 {}", TRI_NAME[triNum(body)], pBody,
                    TRI_NAME[triNum(use)], pUse, level, typeText);
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
            String pBody = TRI_FIVE[triNum(body)];
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

            int[] pair = pairOf(name);
            StringBuilder sb = new StringBuilder();
            sb.append("本卦").append(name).append("（上").append(TRI_NAME[pair[0]]).append(TRI_MAIN[pair[0]])
                    .append("、下").append(TRI_NAME[pair[1]]).append(TRI_MAIN[pair[1]]).append("）");
            if (mutual != null && !mutual.isBlank()) {
                int[] mp = pairOf(mutual);
                sb.append("；互卦").append(mutual).append("（中间过程：")
                        .append(TRI_MAIN[mp[0]]).append(TRI_MAIN[mp[1]]).append("）");
            }
            if (changed != null && !changed.isBlank()) {
                int[] cp = pairOf(changed);
                sb.append("；变卦").append(changed).append("（末后趋向：")
                        .append(TRI_MAIN[cp[0]]).append(TRI_MAIN[cp[1]]).append("）");
            }
            if (category != null && !category.isBlank()) {
                sb.append("。紧扣所问「").append(category).append("」取象，优先动象与应象。");
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("imageInterpretation", sb.toString());
            completeResult(client, job, result);
            log.info("✅ extractImageSymbol | 本卦={} → 类象解读已生成", name);
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
                String pBody = TRI_FIVE[triNum(body)];
                String pOmen = TRI_FIVE[omenTri];
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
                verdict = "三要十应参断：" + tone + "（见「" + omenDesc + "」→取象" + TRI_NAME[omenTri] + "）。";
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

            // 基准：用生体→吉、比和→吉、体克用→平、体生用→凶、用克体→凶（再校旺衰与外应）
            int score = switch (shengKeLevel) {
                case 1 -> 4;   // 吉
                case 2 -> 4;   // 吉
                case 3 -> 3;   // 平
                case 4 -> 2;   // 凶
                case 5 -> 1;   // 大凶
                default -> 3;
            };
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
            String level = scoreToLevel(score);
            String process = "先看体用生克（" + shengKeTypeToString(shengKeLevel) + "）→ 次察卦气旺衰（" + vitality
                    + "）→ 复参外应（" + (omen == null || omen.isBlank() ? "无" : "有") + "），以理统数，定为「" + level + "」。";

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("auspiciousness", level);
            result.put("processDescription", process);
            completeResult(client, job, result);
            log.info("✅ judgeAuspiciousness | shengKe={} vitality={} → {}", shengKeLevel, vitality, level);
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
            int total = pair[0] + pair[1]; // 全卦之数（先天数之和）
            String responsePeriod = responsePeriodOf(total, motion);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("responsePeriod", responsePeriod);
            completeResult(client, job, result);
            log.info("✅ determineResponsePeriod | 全卦之数={} motion={} → {}", total, motion, responsePeriod);
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
                String t = str(vars.get("yicuoText"));
                up = stroke(t == null || t.isEmpty() ? '乾' : t.charAt(0));
                low = stroke(t == null || t.length() < 2 ? '坤' : t.charAt(1));
                moving = up + low;
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

    private static int triNum(String name) {
        if (name != null) {
            for (int i = 1; i <= 8; i++) {
                if (TRI_NAME[i].equals(name)) {
                    return i;
                }
            }
            if (name.length() == 1) {
                for (int i = 1; i <= 8; i++) {
                    if (TRI_NAME[i].equals(name)) {
                        return i;
                    }
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

    /** 由农历月推季节（1-3春/4-6夏/7-9秋/10-12冬）。 */
    private static String seasonFromLunarMonth(int month) {
        if (month >= 1 && month <= 3) {
            return "春";
        }
        if (month <= 6) {
            return "夏";
        }
        if (month <= 9) {
            return "秋";
        }
        if (month <= 12) {
            return "冬";
        }
        return "四季";
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

    /** 后天八卦方位 → 先天卦数（乾西北/兑西/离南/震东/巽东南/坎北/艮东北/坤西南）。 */
    private static int dirToTrigram(String dir) {
        return switch (dir == null ? "" : dir) {
            case "东" -> 4;
            case "南" -> 3;
            case "西" -> 2;
            case "北" -> 6;
            case "东南" -> 5;
            case "西南" -> 8;
            case "东北" -> 7;
            case "西北" -> 1;
            default -> 1;
        };
    }

    private static int dirFromTextOrDefault(String text, int fallback) {
        int d = dirToTrigram(text);
        return d == 1 && (text == null || !text.contains("西") && !text.contains("北")) ? fallback : d;
    }

    /** 物象类属 → 卦数（依原著卷之一「八卦万物属类」主象）。 */
    private static int xiangToNumber(String text) {
        if (text == null || text.isBlank()) {
            return 1;
        }
        for (Map.Entry<Integer, String[]> e : TRI_KEYWORDS.entrySet()) {
            for (String kw : e.getValue()) {
                if (text.contains(kw)) {
                    return e.getKey();
                }
            }
        }
        return 1;
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

    private static int parseShengKeLevel(String shengKeType) {
        if (shengKeType != null && !shengKeType.isEmpty()) {
            char c = shengKeType.charAt(0);
            if (c >= '1' && c <= '5') {
                return c - '0';
            }
        }
        return 3;
    }

    private static String shengKeTypeToString(int level) {
        return switch (level) {
            case 1 -> "用生体";
            case 2 -> "体用比和";
            case 3 -> "体克用";
            case 4 -> "体生用";
            case 5 -> "用克体";
            default -> "体用未定";
        };
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

    /** 应期：按全卦之数分「近/中/远」三级，给范围，依动静调整。 */
    private static String responsePeriodOf(int total, String motion) {
        int t = Math.max(1, total);
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
        String motionNote = switch (motion == null ? "" : motion) {
            case "行", "走" -> "行则速，宜就低限";
            case "卧" -> "卧则迟，宜就高限";
            default -> "坐则迟，居中而应";
        };
        return level + "·约" + low + "~" + high + unit + "内（" + motionNote + "）";
    }

    /** 分类占断：以体为主、用为所应，据类别与生克给分项断语。 */
    private static String categoryVerdict(String category, String hexagram, String shengKe) {
        String tone = shengKe == null ? "体用未定" : shengKe;
        String cat = (category == null || category.isBlank()) ? "所问之事" : category;
        return "依「" + cat + "」分项断法（本卦" + hexagram + "）：" + tone
                + "。以体卦为主、用卦为所应，参互卦之中间、变卦之末后综合断之。";
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
