package com.ocean.ontologyframework.ziweidoushu;

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
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 紫微斗数推命流程（{@code ZiWeiDouShuChartProcess}）的 JobWorker。
 *
 * <p>22 个 {@link JobWorker} 与 {@code ontology/bpmn/ziweidoushuProcess.bpmn} 的 22 个
 * {@code serviceTask} 一一对应（契约见《紫微斗数应用-应用架构设计》§4）：
 * parseBirthInput / determineLifePalaces / placeMainStars / placeAuxiliaryStars /
 * placeMiscStars / placeFourTransform / verifyChart / assessBrightness / interpretStarNature /
 * handleEmptyPalace / analyzeSurroundPalaces / determinePattern / generateNatalReading /
 * analyzeFemaleReading / analyzeChildReading / analyzeMajorLimit / analyzeAnnualFortune /
 * analyzePalaceOverlay / aggregateReading / sendAlert / interpretResult / persistAndReturn。
 *
 * <p><b>落地口径</b>（第 7 步，闸门④已确认）：
 * <ul>
 *   <li>TBox 经 {@code ontology.main-path}（{@code ziweidoushu-all.owl}）由
 *       {@link BackendService#getInstance(String, OBDAHandler)} 加载；</li>
 *   <li>安星起例（紫微定位/庙陷/四化/宫干/流曜）等**查表型**数据依闸门⑩裁决入库（MySQL），经
 *       {@code ontology.obda-path} / {@code ontology.obda-properties-path}（Ontop 虚拟访问，第 11 步落地）；
 *       第 11 步前以内置「口径表」兜底，不阻断流程；</li>
 *   <li>历法换算（公农历/真太阳时）、取宫叠合、综合评级等由程序编排、数值计算外置（★铁律 13/14），
 *       不写成 SWRL；简单判定/分类用 SWRL（R1~R25）。</li>
 *   <li>无法回原著定位者一律标【待考】并记录 {@code diagnosis:ToBeVerified} 语义（★铁律 8/9/10）。</li>
 * </ul>
 *
 * <p>全部业务逻辑经现存 API（{@link BackendService}、{@link OntologyWorkerSupport}、
 * {@link RabbitMqHandler}、{@link OBDAHandler}）实现，不重写平台 API（★铁律 21）。
 * 出处一律为《紫微斗數全書》（[宋]陈抟（陈希夷）撰）具体篇章。
 */
@Component
@Profile("ZiWeiDouShuBPMN")
public class ZiWeiDouShuOntologyJobWorker extends OntologyWorkerSupport {

    private static final Logger log = LoggerFactory.getLogger(ZiWeiDouShuOntologyJobWorker.class);

    @Value("${ontology.main-path}")
    private String mainOntologyPath;

    /** OBDA 映射/属性文件路径（第 11 步落地；{@link OBDAHandler#init} 仅登记路径，懒加载）。 */
    @Value("${ontology.obda-path}")
    private String obdaPath;

    @Value("${ontology.obda-properties-path}")
    private String obdaPropertiesPath;

    @Value("${env-prepare.rabbitmq.exchange}")
    private String alertExchange;

    @Value("${env-prepare.rabbitmq.routing-key}")
    private String alertRoutingKey;

    /**
     * 末尾「命书详解」节点（{@code interpretResult}）调用的大模型客户端（通用能力，见
     * {@link com.ocean.utilities.ChatAgent}）。未配置 {@code spring.ai.openai.*}（未装配或不可用）时
     * 节点降级：不生成详解/白话、以空串完成，流程不阻断（客户裁定）。
     */
    @Autowired(required = false)
    private ChatAgent llm;

    /** 末尾「命书详解」系统提示：以紫微斗数命师（陈希夷）身份、依《紫微斗數全書》义理按本命推导详解，严禁编造出处。 */
    private static final String INTERPRET_SYSTEM_PROMPT =
            "你是精通《紫微斗數全書》（[宋]陈抟（陈希夷）撰）的紫微斗数命理大师。"
            + "请严格依据《紫微斗數全書》的义理与命书体例，对给定的【紫微斗数命书（节点 16 已按本命推导的口径结论）】作系统详解："
            + "先按命盘十二宫、十四主星与辅佐煞忌诸曜的落宫与庙旺利陷、星曜化气所主、四化飞星、格局成败、三方四正与夹拱、"
            + "大限流年逐环节说明**推断依据**（为何如此断），再引述《紫微斗數全書》相关篇章（卷一·諸星問答論 / 卷三·談星要論、諸星庙陷 等）的论断加以**印证**；"
            + "引文只作印证，不得整段照抄与本命无关的原文。"
            + "要求：论述详实、条理清晰、有据可依。严禁编造《全書》中并不存在的篇目、条文或断语；"
            + "若某一环节确无明文可引，须明确标注「此处依常理推之」，不得杜撰出处。使用规范 Markdown 输出。";

    /** 末尾「白话解读」系统提示：把前述命书详解（含引文）转写为通俗现代白话，术语随文浅释。 */
    private static final String INTERPRET_PLAIN_SYSTEM_PROMPT =
            "你是紫微斗数的白话讲解员。请把给定的【紫微斗数命书】与随附的古典命书详解，"
            + "用通俗易懂的现代白话文重新讲一遍：分点讲清命盘格局、性格心性、事业功名、财运、婚姻感情、六亲、健康、大限流年吉凶"
            + "各自的含义与对命主意味着什么。"
            + "关键要求：凡命书中已定的专业术语（各个主星/辅星/煞星、四化（化禄/化权/化科/化忌）、庙旺利陷、格局、十二宫、三方四正、大限流年等），"
            + "必须逐项用一句话白话解释——先说该词「是什么、怎么来的」，再说「对命主意味着什么、有什么影响」，"
            + "力求一句一义、避免只堆砌名词；命书中未出现的术语不必强解。"
            + "只作白话转述，严禁编造《全書》中并不存在的篇目、条文或断语。使用规范 Markdown 输出。";

    // ==========================================================================
    // 内置基础表（★铁律 13/14：历法/安星数值计算外置；第 11 步换 OBDA `star_layout`/`brightness_map` 等）
    // 出处：《紫微斗數全書》卷二·安身命例 / 安十二宫例 / 起五行寅例 / 六十花甲子納音歌 /
    //       安南北斗諸星訣 / 安紫微定位棋盘图 / 安文昌文曲星訣 / …；卷三·諸星庙陷。
    // ==========================================================================

    /** 十天干（甲…癸）。 */
    private static final String[] GAN = {"甲", "乙", "丙", "丁", "戊", "己", "庚", "辛", "壬", "癸"};
    /** 十二地支（子…亥）。 */
    private static final String[] ZHI = {"子", "丑", "寅", "卯", "辰", "巳", "午", "未", "申", "酉", "戌", "亥"};
    /** 十二生肖（子鼠…亥猪）。 */
    private static final String[] ZHI_ANIMAL = {"鼠", "牛", "虎", "兔", "龙", "蛇", "马", "羊", "猴", "鸡", "狗", "猪"};
    /** 阴阳：1 阳 0 阴（天干）。 */
    private static final int[] GAN_YY = {1, 0, 1, 0, 1, 0, 1, 0, 1, 0};
    /** 五行索引：0木 1火 2土 3金 4水（天干：甲乙木 丙丁火 戊己土 庚辛金 壬癸水）。 */
    private static final int[] GAN_ELEM = {0, 0, 1, 1, 2, 2, 3, 3, 4, 4};
    /** 五行索引（地支：子水 丑土 寅木 卯木 辰土 巳火 午火 未土 申金 酉金 戌土 亥水）。 */
    private static final int[] ZHI_ELEM = {4, 2, 0, 0, 2, 1, 1, 2, 3, 3, 2, 4};
    private static final String[] ELEM_NAME = {"木", "火", "土", "金", "水"};
    /** 十二宫（自命宫起逆行，见卷二·安十二宫例）。 */
    private static final String[] PALACE_NAME = {"命宫", "兄弟", "妻妾", "子女", "财帛", "疾厄",
            "迁移", "奴仆", "官禄", "田宅", "福德", "父母"};

    /** 五虎遁（卷二·起五行寅例）：年干 %5 → 寅宫天干索引（甲己丙寅 乙庚戊寅 丙辛庚寅 丁壬壬寅 戊癸甲寅）。 */
    private static final int[] WUHU_YIN_GAN = {2, 4, 6, 8, 0};

    /** 六十甲子纳音（30 项，甲子乙丑起）——卷二·六十花甲子納音歌。 */
    private static final String[] NAYIN = {
            "海中金", "炉中火", "大林木", "路旁土", "剑峰金",
            "山头火", "涧下水", "城头土", "白蜡金", "杨柳木",
            "泉中水", "屋上土", "霹雳火", "松柏木", "长流水",
            "沙中金", "山下火", "平地木", "壁上土", "金箔金",
            "覆灯火", "天河水", "大驿土", "钗钏金", "桑拓木",
            "大溪水", "沙中土", "天上火", "石榴木", "大海水"};
    /** 纳音五行 → 五行局（水二/木三/金四/土五/火六）。 */
    private static final String[] NAYIN_ELEM = {
            "金", "火", "木", "土", "金", "火", "水", "土", "金", "木",
            "水", "土", "火", "木", "水", "金", "火", "木", "土", "金",
            "火", "水", "土", "金", "木", "水", "土", "火", "木", "水"};

    /**
     * 紫微星定位表（卷二·安紫微定位棋盘图）：局数（2..6）× 生日（1..30）→ 地支索引（子=0…亥=11）。
     * 行序：水二局 / 木三局 / 金四局 / 土五局 / 火六局（对应 {@link #ZIWEI_BUREAU} 2..6）。
     */
    private static final int[] ZIWEI_BUREAU = {2, 3, 4, 5, 6};
    private static final int[][] ZIWEI_TABLE = {
            // 水二局：初一丑，顺行一宫安二日（卷二 棋盤图）
            {1, 2, 2, 3, 3, 4, 4, 5, 5, 6, 6, 7, 7, 8, 8, 9, 9, 10, 10, 11, 11, 0, 0, 1, 1, 2, 2, 3, 3, 4},
            // 木三局：初一起龙（辰）初二牛（丑）
            {4, 1, 2, 5, 2, 3, 6, 3, 4, 7, 4, 5, 8, 5, 6, 9, 6, 7, 10, 7, 8, 11, 8, 9, 0, 9, 10, 1, 10, 11},
            // 金四局：初一寻猪（亥）初二龙（辰）
            {11, 4, 1, 2, 0, 5, 2, 3, 1, 6, 3, 4, 2, 7, 4, 5, 3, 8, 5, 6, 4, 9, 6, 7, 5, 10, 7, 8, 6, 11},
            // 土五局：初一午上二亥宫
            {6, 11, 4, 1, 2, 7, 0, 5, 2, 3, 8, 1, 6, 3, 4, 9, 2, 7, 4, 5, 10, 3, 8, 5, 6, 11, 4, 9, 6, 7},
            // 火六局：初二骑马初四龙
            {9, 6, 11, 4, 1, 2, 10, 7, 0, 5, 2, 3, 11, 8, 1, 6, 3, 4, 0, 9, 2, 7, 4, 5, 1, 10, 3, 8, 5, 6}};

    /** 紫微星系（自紫微逆行）相对宫位偏移（卷二·安南北斗諸星訣：紫微天机逆行旁，隔一阳武天同当，又隔二位廉贞地）。 */
    private static final String[] ZIWEI_SERIES = {"紫微", "天机", "太阳", "武曲", "天同", "廉贞"};
    private static final int[] ZIWEI_SERIES_OFF = {0, -1, -3, -4, -5, -8};
    /** 天府星系（自天府顺行）相对宫位偏移（卷二·安南北斗諸星訣：天府太阴与贪狼，巨门天相及天梁，七杀空三破军位）。 */
    private static final String[] TIANFU_SERIES = {"天府", "太阴", "贪狼", "巨门", "天相", "天梁", "七杀", "破军"};
    private static final int[] TIANFU_SERIES_OFF = {0, 1, 2, 3, 4, 5, 6, 10};

    /** 命主（依命宫地支；卷二·安命主）。 */
    private static final String[] LIFE_LORD = {"贪狼", "巨门", "禄存", "文曲", "廉贞", "武曲",
            "破军", "武曲", "廉贞", "文曲", "禄存", "巨门"};
    /** 身主（依生年支；卷二·安身主）。 */
    private static final String[] BODY_LORD = {"火星", "天相", "天梁", "天同", "文昌", "天机",
            "铃星", "天相", "天梁", "天同", "文昌", "天机"};

    /** 天魁/天钺（依年干；卷二·安天魁天钺诀：甲戊庚牛羊，乙己鼠猴乡，六辛逢虎马，壬癸兔蛇藏，丙丁猪狗位）。 */
    private static final int[][] KUI_YUE = {{1, 7}, {0, 8}, {11, 10}, {11, 10}, {1, 7},
            {0, 8}, {1, 7}, {2, 6}, {3, 5}, {3, 5}};
    /** 禄存（依年干；卷二·安禄存星诀）。 */
    private static final int[] LUCUN = {2, 3, 5, 6, 5, 6, 8, 9, 11, 0};
    /** 天马（依年支三合；卷二·安天马星诀：寅午戌马居申，申子辰马居寅，巳酉丑马居亥，亥卯未马居巳）。 */
    private static final int[] TIANMA = {2, 11, 8, 5, 2, 11, 8, 5, 2, 11, 8, 5};
    /** 火星（依年支三合；卷二·安火铃二星诀：寅午戌丑 申子辰寅 巳酉丑卯 亥卯未酉）。 */
    private static final int[] HUOXING = {2, 3, 1, 9, 2, 3, 1, 9, 2, 3, 1, 9};
    /** 铃星（依年支三合；卷二·安火铃二星诀：寅午戌卯 余三合戌）。 */
    private static final int[] LINGXING = {10, 10, 3, 10, 10, 10, 3, 10, 10, 10, 3, 10};

    // ---- 诸杂曜补遗之依干/支表（★外部通行法·非《全書》原文；《全書》语料 c0–c4 检索 0 命中）----
    /** 天官（依生年干：甲未 乙辰 丙巳 丁寅 戊卯 己酉 庚亥 辛酉 壬戌 癸午）。〔外部通行法·非《全書》原文〕 */
    private static final int[] TIAN_GUAN_BR = {7, 4, 5, 2, 3, 9, 11, 9, 10, 6};
    /** 天福（依生年干：甲酉 乙申 丙子 丁亥 戊卯 己寅 庚午 辛巳 壬午 癸巳）。〔外部通行法·非《全書》原文〕 */
    private static final int[] TIAN_FU_BR = {9, 8, 0, 11, 3, 2, 6, 5, 6, 5};
    /** 天厨（依生年干：甲丁巳 乙戊辛午 丙子 己申 庚寅 壬酉 癸亥）。〔外部通行法·非《全書》原文〕 */
    private static final int[] TIAN_CHU_BR = {5, 6, 0, 5, 6, 8, 2, 6, 9, 11};
    /** 天巫（依生月：正五九巳 二六十申 三七十一寅 四八十二亥）。索引＝(生月-1)%4。〔外部通行法·非《全書》原文〕 */
    private static final int[] TIAN_WU_BR = {5, 8, 2, 11};
    /** 孤辰（依生年支三会：亥子丑→寅 寅卯辰→巳 巳午未→申 申酉戌→亥）。〔外部通行法·非《全書》原文〕 */
    private static final int[] GU_CHEN_BR = {2, 2, 5, 5, 5, 8, 8, 8, 11, 11, 11, 2};
    /** 寡宿（依生年支三会：亥子丑→戌 寅卯辰→丑 巳午未→辰 申酉戌→未）。〔外部通行法·非《全書》原文〕 */
    private static final int[] GUA_SU_BR = {10, 10, 1, 1, 1, 4, 4, 4, 7, 7, 7, 10};
    /** 华盖（依生年支三合之墓：申子辰→辰 巳酉丑→丑 寅午戌→戌 亥卯未→未）。〔外部通行法·非《全書》原文〕 */
    private static final int[] HUA_GAI_BR = {4, 1, 10, 7, 4, 1, 10, 7, 4, 1, 10, 7};
    /** 咸池（依生年支三合之四败：申子辰→酉 巳酉丑→午 寅午戌→卯 亥卯未→子）。〔外部通行法·非《全書》原文〕 */
    private static final int[] XIAN_CHI_BR = {9, 6, 3, 0, 9, 6, 3, 0, 9, 6, 3, 0};
    /** 宫支六合配对偏移之补数（合宫 = floorMod(13 - idx, 12)：子丑 寅亥 卯戌 辰酉 巳申 午未）。 */
    private static final int SIX_HE_SUM = 13;

    /**
     * 四化（禄/权/科/忌，依年干；卷二·安禄权科忌四星变化诀）。列序＝年干 甲…癸。
     * 注：庚干、壬干（化科）各家有异说；本表一律取《全書》卷二诀字面（壬→「梁紫府武」，即天府化科；
     * 通行「壬左辅化科」属异说，未采，DB `sihua_map` 同步为天府并以 source_note 备注）。
     */
    private static final String[][] SIHUA = {
            {"廉贞", "破军", "武曲", "太阳"},   // 甲：廉破武阳
            {"天机", "天梁", "紫微", "太阴"},   // 乙：机梁紫月
            {"天同", "天机", "文昌", "廉贞"},   // 丙：同机昌廉
            {"太阴", "天同", "天机", "巨门"},   // 丁：月同机巨
            {"贪狼", "太阴", "右弼", "天机"},   // 戊：贪月弼机
            {"武曲", "贪狼", "天梁", "文曲"},   // 己：武贪梁曲
            {"太阳", "武曲", "太阴", "天同"},   // 庚：日武阴同
            {"巨门", "太阳", "文曲", "文昌"},   // 辛：巨阳曲昌
            {"天梁", "紫微", "天府", "武曲"},   // 壬：梁紫府武
            {"破军", "巨门", "太阴", "贪狼"}};  // 癸：破巨阴贪

    /** 十二长生名（卷二·安长生…）。 */
    private static final String[] TWELVE_STAGE = {"长生", "沐浴", "冠带", "临官", "帝旺", "衰",
            "病", "死", "墓", "绝", "胎", "养"};
    /** 五行局起长生支（卷二：火局寅 木局亥 水局申 金局巳 土局申）。索引＝五行局数 2..6。 */
    private static final int[] CHANGSHENG_BRANCH = {-1, -1, 8, 11, 5, 8, 2};
    /** 五行局 → 长生起始天干索引（用于十二长生干，卷二：火寅起丙？以局五行定，取通行：水申起庚 木亥起辛 金巳起庚 土申起庚 火寅起丙）。 */
    private static final int[] CHANGSHENG_GAN = {-1, -1, 6, 7, 6, 6, 2};

    /** 博士十二神（卷二·安十二宫太岁杀禄诀）。 */
    private static final String[] DEITY_12 = {"博士", "力士", "青龙", "小耗", "将军", "奏书",
            "蜚廉", "喜神", "病符", "大耗", "伏兵", "官府"};
    /** 小限起始支（卷二·安小限诀：寅午戌起辰，申子辰起戌，巳酉丑起未，亥卯未起丑）。按年支索引。 */
    private static final int[] SMALL_LIMIT_START = {10, 7, 4, 1, 10, 7, 4, 1, 10, 7, 4, 1};

    // ==========================================================================
    // 卷三·论太岁小限星辰庙陷遇十二宫中吉凶（原文 c4 L103-172）——按流年太岁&小限所到之宫支查三段：
    //   [0] 入庙化吉（按生年干）；[1] 不入庙化凶（按生年干）；[2] 太岁所值吉凶星（吉星/凶星清单）。
    // 原文「依此判断其人行年灾患应如神」。列序：子丑寅卯辰巳午未申酉戌亥（含 OCR 噪声字照录）。
    // ==========================================================================
    private static final String[][] TAISUI_XIAOXIAN = {
            {"七杀破军在子宫守岁限癸庚己生人发福，巨门天机乙癸生人发福，天府天相天梁丁己庚人财旺遂心，又天同丙丁生人财官双美。",
                    "紫微在子宫守命及岁限丙戊生人悔吝，破财灾殃。",
                    "禄存天机天同太阴昌曲辅弼破军天相廉真武曲天府巨门七杀，可断其年人财两美事事遂心。若遇贪狼紫微天梁忌星太阳擎羊，便断人财耗散孝服，本身灾晦不宁，减半论之。"},
            {"天机在丑守命丙辛生人发旺，天相戊生人发旺，太阴武曲丙戊生人发旺，天府廉贞戊生人发旺，天梁丙戊辛生人发旺。",
                    "太阴在丑宫守命戊生人悔吝，太阳星甲乙生人悔吝，天机丙辛癸生人悔吝，天同廉贞丁庚生人招官非。",
                    "紫微天相天梁太阴天府禄存廉贞破军昌曲天机辅弼，可断其年事事遂心。若遇天同巨门武曲贪狼忌宿太阳擎羊，便断其年人财耗散，官灾口舌孝服，本身遭晦，减半论之。"},
            {"紫微太阳武曲天梁七杀，甲庚丁己生人财官双美。",
                    "廉贞贪狼破军在寅，丙戊生人招官非，甲子生人不喜寅申岁限。",
                    "紫微天府天机太阴武曲七杀天同天相太阳巨门天梁，便断其年人财进益作事遂心。若遇贪狼陀忌，便断其年人财破散，官非孝服，本身见灾，减半论之。"},
            {"紫微天机太阳天相天府天同武曲在命乙辛生人发旺。",
                    "廉贞甲丙生人横破财，太阴甲乙生人财破灾害，庚生人亦不宜主灾害。",
                    "太阴天梁紫微天机天同天府贪狼巨门七杀，即断其年人财兴旺，婚姻喜事重重，诸事称心。若遇廉贞破军太阴天相擎羊忌宿，其年破财，官灾口舌，本身见晦，减半论之。"},
            {"紫微贪狼七杀在辰宫守命限癸甲生人财官禄旺，天机太阳丁庚癸生人财禄发旺，天同戊庚癸生人顺遂，巨门丙辛生人遂意。",
                    "贪狼武曲在辰壬癸生人灾晦，天同巨门丁庚生人灾晦，廉贞壬癸生人主灾晦至重，太阴太阳天机甲乙戊己生人灾晦。",
                    "太阳天机天梁七杀贪狼文昌左辅右弼，便断其年财禄大进益，家道更兴隆，添丁进口，婚姻喜庆重重。若遇紫微天同廉贞天府太阴巨门天相破军忌宿，便断其年破财孝服，官灾口舌。"},
            {"紫微天府天同巨门天相天梁破军丙戊辛人发福，太阴天机丁壬辛丙生人破财，贪狼甲戊生人平平。",
                    "巨门贪狼癸丙生人口舌灾晦，太阴破军灾晦多端。",
                    "紫微太阳天同天府天梁禄存，便断其年人财称意，喜事重重。若遇武曲廉贞太阴贪狼巨门天相破军忌星，便断其年人财损失，官灾口舌，本身病患，减半论之。"},
            {"紫微太阳武曲天同天梁廉贞七杀破军丁己甲癸生人进财遂心。",
                    "贪狼在丙午壬癸生人破财官灾口舌。",
                    "紫微天府天机太阳武曲廉贞天相巨门天梁破军禄存，便断其年人财兴旺，婚姻喜事重重。若值太阴贪狼天同羊陀忌星，便断其年人财破败，官灾口舌孝服，本身灾厄可免。"},
            {"紫微天机天府天相天梁壬乙生人发福，太阴庚壬生人发福生财。",
                    "太阳甲乙生人多灾晦，天同丁庚生人多灾，武曲壬癸生人生灾招官非横祸。",
                    "紫微天府廉贞天机破军天相，便断其年人财增益，作事如意，婚姻产育之喜。若遇太阴太阳武曲天同贪狼巨门羊陀忌宿，便断其年人财耗散，孝服官灾，阴人小口不宁，本人灾厄难免。"},
            {"廉侦破军紫微甲庚癸生人发福，巨门甲庚癸生人发福，天机丁甲癸生人发福，庚生人亦发财发福。",
                    "天机乙戊生人灾晦，巨门丁生人不宜，廉贞丙壬生人有灾，天同甲庚生人灾祸，贪狼癸丙生人有灾祸。",
                    "紫微太阳廉贞天府巨门七杀文昌武曲禄存，便断其年人财利益，喜事重重。若遇天机天同天梁天相太阴破军忌星，便断其年人财散失，官非孝服，本身灾病。"},
            {"紫微天机太阴酉宫守命丙戊乙辛生人进财吉利。",
                    "太阳天同甲乙生人不宜，武曲庚壬生人不宜，天相甲庚生人不宜，廉贞甲庚丙辛生人不宜，天府甲庚壬生人不宜。",
                    "禄存太阴紫微天府昌曲左右，便断其年人财兴旺，作事遂心。若值天机巨门武曲廉贞擎羊陀忌，便断其年人离财散，口舌官非。"},
            {"紫微壬甲丁己生人进财，太阴丁己生人吉庆，武曲丁己甲庚生人吉庆，天机甲乙丁己生人发福，巨门己辛癸生人发福，天同廉贞破军七杀丁己甲生人发财。",
                    "贪狼癸生人不宜，天同庚生人不宜，天机戊生人不宜，巨门丁生人不宜，太阳甲生人不宜，廉贞丙生人不宜，武曲壬生人不宜。",
                    "天机太阴天梁天府武曲七杀贪狼左右天同，便断其年人财利益，作事遂心，家道兴隆。如遇巨门太阳破军紫微天相忌宿，便断其年人财退失，孝服官灾，本身见病，减半论之。"},
            {"紫微天同巨门天梁壬癸戊生人吉庆，天机壬生人吉美，天相丁己生人及丙戊生人发福，太阴戊己生人财官双美。",
                    "廉贞丙壬癸生人不宜，武曲壬丙生人不宜，太阳甲生人不宜。",
                    "天同太阴天梁紫微天府昌曲禄存，便断其年人财进益，喜气重重，谋事俱称心怀。若遇廉贞破军七杀，便断其年人财耗散，小口死亡，本身灾晦。"}};

    /**
     * 抽取某段原文中的「生干条件」天干集合。
     * 规则：天干字符若**紧跟地支**（干支如「甲子」「丙午」）则不取（其为年月而非生干条件）；其余天干字符取之。
     */
    static Set<String> extractGanConditions(String s) {
        Set<String> out = new LinkedHashSet<>();
        String tg = "甲乙丙丁戊己庚辛壬癸";
        String dz = "子丑寅卯辰巳午未申酉戌亥";
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            char nxt = (i + 1 < s.length()) ? s.charAt(i + 1) : ' ';
            if (tg.indexOf(c) >= 0 && dz.indexOf(nxt) < 0) {
                out.add(String.valueOf(c));
            }
        }
        return out;
    }

    /**
     * 卷三·论太岁小限星辰庙陷遇十二宫中吉凶（c4 L105-177）：按**本命生干**择段——
     * 原文「入庙化吉」「不入庙化凶」两段各明列生干条件（如「…癸庚己生人发福」），故以生干判其见吉段/凶段；两段未列者依原文「减半论之」。
     */
    static String taisuiXiaoxianGanHit(int birthGan, int flowBranch) {
        if (birthGan < 0 || birthGan > 9 || flowBranch < 0 || flowBranch > 11) {
            return "";
        }
        String[] seg = TAISUI_XIAOXIAN[flowBranch];
        String g = GAN[birthGan];
        boolean inJi = extractGanConditions(seg[0]).contains(g);
        boolean inXiong = extractGanConditions(seg[1]).contains(g);
        StringBuilder sb = new StringBuilder("生干择段（原文按生干列吉凶）：本命生干").append(g);
        if (inJi && inXiong) {
            sb.append(" 两段皆见（吉凶并参）");
        } else if (inJi) {
            sb.append(" 见「入庙化吉」段（发福/财官双美）");
        } else if (inXiong) {
            sb.append(" 见「不入庙化凶」段（悔吝/招官非）");
        } else {
            sb.append(" 两段未列（依原文「减半论之」）");
        }
        return sb.toString();
    }

    // ==========================================================================
    // 卷三·论大小限星辰过十二宫遇十二支所忌诀（原文 c4 L92-101）——按生年支索引的所忌岁限/星。
    // ==========================================================================
    private static final String[] LIMIT_TABOO = {
            "子年生人切忌寅申岁限（灾晦至重），及忌子午岁限相冲。",
            "丑年生人忌午丑岁限，午生人亦忌丑午岁限，及忌七杀星（灾晦极重）。",
            "寅卯之人忌巳亥岁限，及忌卯酉寅申相冲。",
            "卯年生人亦忌（巳亥岁限），及忌卯酉寅申相冲。",
            "辰生人忌行辰年、又忌行到辰限为天罗，又忌行到戌为地网；巳生人忌逢巳年及忌行到巳限。限遇此灾晦疾厄之险、官非破财忧制连连。",
            "巳生人忌逢巳年及忌行到巳限（龙蛇切忌本身临）。",
            "午生人忌丑午岁限，及忌七杀星（灾晦极重）。",
            "未生人忌逢酉戌岁限，又忌见擎羊在四墓宫。",
            "申生人忌逢火铃二星（灾晦至重），及忌寅年冲。",
            "酉生人亦忌羊陀岁限及忌行卯宫限，及卯年岁君相冲。",
            "戌生人忌遇羊陀灾重，又行到戌宫岁限为地网，又忌行到辰宫岁限为天罗（辰戌相冲不美）。",
            "亥生人忌遇羊陀灾重（戌亥羊陀须避忌）。"};

    // ==========================================================================
    // 卷三·论立命行限宫歌（原文 c4 L102-104）——按命宫纳音五行遇八卦方位之宫断吉凶。
    //   八卦方位宫：坎子 / 离午 / 艮丑寅 / 兑酉 / 震卯 / 巽巳 / 坤未申 / 乾亥。
    // ==========================================================================
    private static final String XINGXIAN_GE = "立命行限宫歌（卷三）：金人遇坎命须伤，木命落离有祸殃，"
            + "水遇艮宫应蹇滞，火来兑上祸难藏，土到东南逢震巽，须防脓血及惊慌，纵然吉曜相逢照，未免官灾闹一场。"
            + "〔释读：按命宫纳音五行遇大限/流年所到宫之八卦方位（坎子/离午/艮丑寅/兑酉/震卯/巽巳）断，行限到该方主蹇滞官灾〕";

    /** 论行限分南北斗（卷三 c4 L79-82）：阳男阴女南斗为福，阴男阳女北斗为福；北斗断大限上五年/小限上半年，南斗断大限下五年/小限下半年。 */
    private static String nanBeiDouSong(int yearGan, boolean male) {
        boolean yangGan = GAN_YY[yearGan] == 1;
        boolean nanDouFu = (yangGan && male) || (!yangGan && !male);  // 阳男阴女 → 南斗为福
        return "论行限分南北斗（卷三）：阳男阴女南斗为福，阴男阳女北斗为福（本命为"
                + (yangGan ? "阳" : "阴") + (male ? "男" : "女") + " → " + (nanDouFu ? "南斗为福" : "北斗为福") + "）；"
                + "北斗诸星吉凶断大限上五年、小限上半年，南斗诸星吉凶断大限下五年、小限下半年。";
    }

    // ==========================================================================
    // 星辰庙陷表（★权威）：星 × 十二宫（子=0…亥=11）→ 庙陷等第
    // 出处：卷二·星辰庙陷（庙/旺/得地/利益/平和/不得地/陷）。
    // 与 ontology/database/data.sql 的 `brightness_map`（240 条**可行**组合）同一真源：
    //   14 主星 × 12 宫 = 168；文昌/文曲/火星/铃星 × 12 = 48；禄存/擎羊/陀罗各入 8 宫 = 24；
    //   共 240（禄存不入辰戌丑未、擎羊不入寅巳申亥、陀罗不入子卯午酉，故非 21×12=252）。
    // 表内缺项＝该星结构上不可入之宫（永不命中），运行时以「平和」兜底。
    // 第 11 步经 OBDA `brightness_map` 取数后本表作为离线兜底保留（★铁律 13/14）。
    // ==========================================================================
    /** 庙陷表列序星（与 {@link #BRIGHTNESS} 行序一一对应）。 */
    private static final String[] BRIGHTNESS_STARS = {
            "紫微", "天机", "太阳", "武曲", "天同", "廉贞", "天府", "太阴", "贪狼", "巨门",
            "天相", "天梁", "七杀", "破军", "文昌", "文曲", "禄存", "擎羊", "陀罗", "火星", "铃星"};
    /** 星 → 12 宫庙陷等第（列序：子丑寅卯辰巳午未申酉戌亥；卷二·星辰庙陷）。 */
    private static final String[][] BRIGHTNESS = {
            {"平和", "庙", "旺", "旺", "得地", "旺", "庙", "庙", "旺", "旺", "得地", "旺"},   // 紫微
            {"庙", "陷", "得地", "旺", "利益", "平和", "庙", "陷", "得地", "旺", "利益", "平和"}, // 天机
            {"陷", "不得地", "旺", "庙", "旺", "旺", "旺", "得地", "得地", "平和", "不得地", "陷"}, // 太阳
            {"旺", "庙", "得地", "利益", "庙", "平和", "旺", "庙", "得地", "利益", "庙", "平和"}, // 武曲
            {"旺", "不得地", "利益", "平和", "平和", "庙", "陷", "不得地", "旺", "平和", "平和", "庙"}, // 天同
            {"平和", "利益", "庙", "平和", "利益", "陷", "平和", "利益", "庙", "平和", "利益", "陷"}, // 廉贞
            {"庙", "庙", "庙", "得地", "庙", "得地", "旺", "庙", "得地", "旺", "庙", "得地"}, // 天府
            {"庙", "庙", "旺", "陷", "陷", "陷", "不得地", "不得地", "利益", "旺", "旺", "庙"}, // 太阴
            {"旺", "庙", "平和", "利益", "庙", "陷", "旺", "庙", "平和", "利益", "庙", "陷"}, // 贪狼
            {"旺", "不得地", "庙", "庙", "陷", "旺", "旺", "不得地", "庙", "庙", "陷", "旺"}, // 巨门
            {"庙", "庙", "庙", "陷", "得地", "得地", "庙", "得地", "庙", "陷", "得地", "得地"}, // 天相
            {"庙", "旺", "庙", "庙", "庙", "陷", "庙", "旺", "陷", "得地", "庙", "陷"}, // 天梁
            {"旺", "庙", "庙", "旺", "庙", "平和", "旺", "庙", "庙", "旺", "庙", "平和"}, // 七杀
            {"庙", "旺", "得地", "陷", "旺", "平和", "庙", "旺", "得地", "陷", "旺", "平和"}, // 破军
            {"得地", "庙", "陷", "利益", "得地", "庙", "陷", "利益", "得地", "庙", "陷", "利益"}, // 文昌
            {"得地", "庙", "平和", "旺", "得地", "庙", "陷", "旺", "得地", "庙", "陷", "旺"}, // 文曲
            {"庙", "平和", "庙", "庙", "平和", "庙", "庙", "平和", "庙", "庙", "平和", "庙"}, // 禄存
            {"陷", "庙", "平和", "陷", "庙", "平和", "陷", "庙", "平和", "陷", "庙", "平和"}, // 擎羊
            {"平和", "庙", "陷", "平和", "庙", "陷", "平和", "庙", "陷", "平和", "庙", "陷"}, // 陀罗
            {"陷", "得地", "庙", "利益", "陷", "得地", "庙", "利益", "陷", "得地", "庙", "利益"}, // 火星
            {"陷", "得地", "庙", "利益", "陷", "得地", "庙", "利益", "陷", "得地", "庙", "利益"}}; // 铃星

    // 星辰落闲（卷二·定十二宫星辰落闲）：{星, 落闲之地支…}
    private static final String[][] IDLE_STAR = {
            {"紫微", "子", "辰", "亥"}, {"贪狼", "寅", "辰"}, {"天相", "辰", "戌"}, {"七杀", "辰", "亥"},
            {"天梁", "巳", "酉"}, {"天机", "巳"}, {"破军", "巳", "申"}, {"武曲", "申"}};

    // 卷二·十二宫各星吉凶诀中「落闲宫」之断语（与「定十二宫星辰落闲」交叉复核之原文登记）。
    // 列：{星, c3 行号, 原文断语}。★仅原文登记、不臆造分支：表内星（天机/天梁/七杀）落闲即引断语；
    //   表外星（天同/廉贞/擎羊/火星/铃星）原文未定闲支，仅在该星落陷（闲弱近似）时以〔举例式·待考〕引文。
    private static final String[][] IDLE_VERSE = {
            {"天机", "494", "天机化忌落闲宫，纵有财官亦不荣，退尽家财兼寿夭，飘蓬僧道住山中。"},
            {"天同", "560", "天同守命落闲宫，火铃杀凑更为凶，天机梁月来相会，只好空门度岁终。"},
            {"廉贞", "585", "廉贞坐命落闲宫，贪破羊火忌相逢，纵有财官也不美，平生何以得从容。"},
            {"廉贞", "586", "廉贞落陷入闲宫，吉曜相逢也有凶，腰足灾残难脱厄，更加恶杀命该终。"},
            {"天梁", "712", "天梁遇火落闲宫，陀杀重逢更是凶，孤刑带疾破家财，空门技艺可营工。"},
            {"七杀", "733", "七杀居命落闲宫，巨宿羊陀更照冲，若不伤肢必损骨，空门僧道可兴隆。"},
            {"擎羊", "850", "擎羊一曜落闲宫，陀火冲兮便是凶，更若身命同劫杀，定然夭绝在途中。"},
            {"火星", "889", "火铃在命落闲宫，西北生人作事庸，破尽家财终不久，须教带疾免灾凶。"},
            {"铃星", "889", "火铃在命落闲宫，西北生人作事庸，破尽家财终不久，须教带疾免灾凶。"}};

    // 卷二·星辰庙陷（第一权威，命盘取用）与卷三·诸星庙陷（汇总）之异文登记（★主基线优先、异说并陈，⑲）。
    //   逐支比对结果：卷二↔卷三**正相左**者唯二（其余为卷三未列、非矛盾，卷二更完整，仍以卷二为准）。
    private static final String[][] BRIGHTNESS_VARIANTS = {
            {"天机", "辰", "卷二作「利益」，卷三·诸星庙陷汇总作「旺」"},
            {"文曲", "寅", "卷二作「平和」，卷三·诸星庙陷汇总作「陷」"}};

    /** 某星落宫若存卷二↔卷三庙陷异文，返回异说说明（否则 null）。卷二为取用口径，卷三作异说并陈。 */
    private static String brightnessVariantNote(String star, int branchIdx) {
        if (branchIdx < 0 || branchIdx > 11) {
            return null;
        }
        String zhi = ZHI[branchIdx];
        for (String[] v : BRIGHTNESS_VARIANTS) {
            if (v[0].equals(star) && v[1].equals(zhi)) {
                return star + "@" + zhi + "：" + v[2];
            }
        }
        return null;
    }

    // ==========================================================================
    // 验盘（定盘）特征词表〔外部通行法·非《全書》原文〕——节点 V verifyChart（I·61）。
    //   《全書》未见系统验盘论述；依后世通行定盘法（大德山人《紫微斗数论定盘》、91demi《时辰校正》、
    //   「文墨天机」《论定盘》等）：以命宫主星性情/相貌、时系星（昌曲）痣癍、五行局体质等
    //   多维比对候补时辰。★外部来源仅作补遗、逐处标注，不改《全書》第一权威。
    // ==========================================================================
    /** 命宫主星 → 验盘特征关键词（性情/相貌/痣癍）。〔外部通行法·非《全書》原文〕 */
    private static final String[][] VERIFY_STAR_TRAITS = {
            {"紫微", "忠厚", "老成", "谦恭", "耿直", "腰背肥满"},
            {"天机", "性急", "心慈", "机谋", "身长"},
            {"太阳", "面方", "圆满", "面色红", "心慈", "好济施"},
            {"武曲", "刚毅", "面白", "性刚"},
            {"天同", "温和", "圆润", "仁慈", "耿直"},
            {"廉贞", "性刚", "面紫"},
            {"天府", "温和", "忠厚"},
            {"太阴", "皮肤白", "清秀", "温良"},
            {"贪狼", "毛发浓黑", "长耸", "多才", "好花酒"},
            {"巨门", "多疑", "善辩"},
            {"天相", "敦厚", "面白"},
            {"天梁", "老成", "稳重", "面长"},
            {"七杀", "果断", "刚毅", "背厚", "眉宽", "性急"},
            {"破军", "背厚", "眉宽", "性刚"}};
    /** 五行局 → 验盘体质关键词（水二局皮肤白 / 火六局面色偏红）。〔外部通行法·非《全書》原文〕 */
    private static final String[][] VERIFY_BUREAU_TRAITS = {
            {"水二局", "皮肤白"},
            {"火六局", "面色偏红"}};
    /** 时系星（昌曲坐命/身）附加痣癍特征（加铃星更甚）。〔外部通行法·非《全書》原文〕 */
    private static final String VERIFY_CHANGQU_MOLE = "面上有痣";

    // ==========================================================================
    // 运限序列模块（卷一·定杂局「步数无依 / 水上驾星」运限判定；原文 c2 L646-647）
    //   原文：步数无依「前限接后限连绵不分是也」；水上驾星「一年好一年不好是也」。
    //   ★落地口径〔释读·非逐字原文，依原文句意客观化〕：
    //     以「限/年三方四正吉凶符号」构成运限序列，再按序列形态判定——
    //       步数无依＝大限十二限吉凶同号、全序无转折（前限接后限连绵不分，一生运程连续无起落）；
    //       水上驾星＝流年吉凶逐年交替（一年好一年不好）。
    //   吉凶符号由本命盘三方四正（吉星/凶星＋主星庙陷＋生年四化）客观复算，不依主观；此为
    //   本命盘可判之部分，运限重算（大限/流年实际星曜）时按同法复算即可。
    // ==========================================================================
    /** 运限吉星（三方四正见之增吉）。 */
    private static final Set<String> YUNXIAN_JI_STARS = Set.of(
            "禄存", "天魁", "天钺", "左辅", "右弼", "文昌", "文曲", "天官", "天福", "恩光", "天贵");
    /** 运限凶星（三方四正见之增凶）。 */
    private static final Set<String> YUNXIAN_SHA_STARS = Set.of(
            "擎羊", "陀罗", "火星", "铃星", "地劫", "天空");

    // ==========================================================================
    // 初始化
    // ==========================================================================
    @PostConstruct
    public void init() throws Exception {
        log.info("🔧 初始化 ZiWeiDouShuOntologyJobWorker 依赖链...");
        initOntologyPipeline();
        log.info("✅ ZiWeiDouShuOntologyJobWorker 初始化完成 | ontologyPath={}", mainOntologyPath);
    }

    @Override
    protected BackendService createBackendService() throws Exception {
        // 第 11 步：接入 OBDA（Ontop 虚拟知识图谱）。OBDAHandler.init 仅登记路径（懒加载，不连库）。
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

    // ==========================================================================
    // 节点 1：输入解析与时辰校正（parseBirthInput）
    // 实现方法：程序（历法换算，★13 外置）+ 本体登记 chart:BirthInput/calendar:*；算法见《应用架构设计》§5。
    // 出处：卷二·安身命例（闰月按下月）；卷三·論人生時要審的確（时辰交界前归）。
    // ==========================================================================
    @JobWorker(type = "parseBirthInput", autoComplete = false)
    public void handleParseBirthInput(final ActivatedJob job, final JobClient client) {
        try {
            Map<String, Object> vars = job.getVariablesAsMap();
            Object birthVar = vars.get("birth");
            if (birthVar == null || (birthVar instanceof String bs && bs.isBlank())) {
                throw new IllegalArgumentException("birth（生辰）不能为空");
            }
            String birth = str(birthVar);
            String calendar = orDefault(str(vars.get("calendar")), birthCalendar(birthVar));
            String gender = orDefault(str(vars.get("gender")), "男");
            String birthPlace = orDefault(str(vars.get("birthPlace")), "");
            String subjectType = orDefault(str(vars.get("subjectType")), "成人男");
            String inputMode = orDefault(str(vars.get("inputMode")), "直接排盘");
            String depth = orDefault(str(vars.get("depth")), "本命");
            String granularity = orDefault(str(vars.get("granularity")), "年");

            LocalDateTime dt = resolveBirthDateTime(birthVar);
            boolean lunarUnconverted = false;
            if ("农历".equals(calendar)) {
                LocalDateTime conv = lunarToSolar(birth);
                if (conv != null) {
                    dt = conv;
                } else {
                    lunarUnconverted = true;
                    log.warn("⚠️ parseBirthInput | 农历→公历表未就绪（第 11 步 OBDA 落地），标【待考】并按原值登记：{}", birth);
                }
            }

            // 真太阳时校正：时差 =（经度 − 120）/ 15 小时（客户裁定 ②；历法惯例）
            Double lon = extractLongitude(birthPlace);
            double tstOffsetH = (lon == null) ? 0.0 : (lon - 120.0) / 15.0;
            if (lon != null) {
                dt = dt.plusSeconds(Math.round(tstOffsetH * 3600.0));
            }

            // 昼夜判定（客户裁定 ④）：卯至申为昼（卯5:00–申17:00）
            int hour = dt.getHour();
            String dayNight = (hour >= 5 && hour < 17) ? "昼" : "夜";

            // 安星用「农历月/日」：农历输入直接取；公历输入经农历转换（第 11 步 OBDA 落地前以原值近似并标【待考】）
            int[] lunarMD = resolveLunarMonthDay(birthVar, calendar);
            boolean lunarApprox = "公历".equals(calendar);

            String chartBase = String.join(";",
                    "SOLAR=" + dt,
                    "BIRTH=" + birth,
                    "CAL=" + calendar,
                    "GENDER=" + gender,
                    "SUBJECT=" + subjectType,
                    "MODE=" + inputMode,
                    "DEPTH=" + depth,
                    "GRAN=" + granularity,
                    "TST=" + tstOffsetH,
                    "PLACE=" + birthPlace,
                    "DAYNIGHT=" + dayNight,
                    "LM=" + lunarMD[0],
                    "LD=" + lunarMD[1],
                    "LUNAR_APPROX=" + lunarApprox,
                    "LUNAR_UNCONVERTED=" + lunarUnconverted);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("chartBase", chartBase);
            result.put("dayNight", dayNight);
            result.put("inputMode", inputMode);
            result.put("subjectType", subjectType);
            result.put("depth", depth);
            completeResult(client, job, result);
            log.info("✅ parseBirthInput | birth={} | calendar={} | 校正后={} | 昼夜={} | tst={}h",
                    birth, calendar, dt, dayNight, tstOffsetH);
        } catch (Exception e) {
            fail(job, client, "PARSE_BIRTH_INPUT_FAILED", e);
        }
    }

    // ==========================================================================
    // 节点 2：定命身宫与十二宫（determineLifePalaces）
    // 实现方法：程序数值计算（安命/宫序/纳音）+ 本体 chart:MingChartSkeleton/LifePalace/BodyPalace/…。
    // 出处：卷二·安身命例（寅上起正月，逆至生时安命、顺至生时安身）/ 安十二宫例（男女俱从逆转）/
    //       起五行寅例（五虎遁）/ 六十花甲子納音歌（五行局）/ 安命主 / 安身主。
    // ==========================================================================
    @JobWorker(type = "determineLifePalaces", autoComplete = false)
    public void handleDetermineLifePalaces(final ActivatedJob job, final JobClient client) {
        try {
            Map<String, Object> vars = job.getVariablesAsMap();
            Map<String, String> cb = parseChartBase(str(vars.get("chartBase")));
            LocalDateTime solar = LocalDateTime.parse(cb.get("SOLAR"));
            String gender = orDefault(cb.get("GENDER"), "男");

            // 生月取安星用「农历月」（chartBase.LM）；生时取时辰地支
            int month = toInt(cb.get("LM"), solar.getMonthValue());
            int hourBranch = hourBranchOf(solar.getHour());

            // 安命宫：寅起正月顺数至生月，再自生月宫起子时逆数至生时
            int mingIdx = floorMod(BRANCH_YIN + (month - 1) - hourBranch, 12);
            // 安身宫：自生月宫起子时顺数至生时
            int shenIdx = floorMod(BRANCH_YIN + (month - 1) + hourBranch, 12);

            // 五虎遁：由年干起寅宫天干，推十二宫天干；命宫天干用于纳音定五行局
            int yearGan = yearGanOf(solar);
            int yinGan = WUHU_YIN_GAN[yearGan % 5];
            int mingGan = floorMod(yinGan + floorMod(mingIdx - BRANCH_YIN, 12), 10);

            // 五行局：命宫干支纳音 → 局数（水二/木三/金四/土五/火六）
            int bureau = bureauOf(mingGan, mingIdx);
            String bureauName = bureauName(bureau);

            // 命主/身主
            String lifeLord = LIFE_LORD[mingIdx];
            int yearBranch = yearBranchOf(solar);
            String bodyLord = BODY_LORD[yearBranch];

            // 十二宫排布（自命宫逆行：命、兄弟、妻妾、子女、财帛、疾厄、迁移、奴仆、官禄、田宅、福德、父母）
            StringBuilder skeleton = new StringBuilder();
            for (int i = 0; i < 12; i++) {
                int idx = floorMod(mingIdx - i, 12);
                skeleton.append(PALACE_NAME[i]).append("=").append(ZHI[idx]).append("；");
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("chartSkeleton", "命宫=" + ZHI[mingIdx] + "｜身宫=" + ZHI[shenIdx]
                    + "｜五行局=" + bureauName + "｜" + skeleton);
            result.put("lifeLord", lifeLord);
            result.put("bodyLord", bodyLord);
            completeResult(client, job, result);
            log.info("✅ determineLifePalaces | 命宫={} 身宫={} 五行局={} 命主={} 身主={}",
                    ZHI[mingIdx], ZHI[shenIdx], bureauName, lifeLord, bodyLord);
        } catch (Exception e) {
            fail(job, client, "DETERMINE_LIFE_PALACES_FAILED", e);
        }
    }

    // ==========================================================================
    // 节点 3：安十四主星（placeMainStars）
    // 实现方法：程序查表（ZIWEI_TABLE 紫微定位表）+ 本体 star:FourteenMainStar/layout:StarPlacement。
    // 出处：卷二·安南北斗諸星訣（星序与斗分）/ 安紫微定位棋盘图（局+日 → 紫微）；天府惟寅申二宫紫府同宫。
    // ==========================================================================
    @JobWorker(type = "placeMainStars", autoComplete = false)
    public void handlePlaceMainStars(final ActivatedJob job, final JobClient client) {
        try {
            Map<String, Object> vars = job.getVariablesAsMap();
            Map<String, String> cb = parseChartBase(str(vars.get("chartBase")));
            String skeleton = str(vars.get("chartSkeleton"));
            int bureau = bureauFromSkeleton(skeleton);
            int day = toInt(cb.get("LD"), 1);

            if (bureau < 2 || bureau > 6 || day < 1 || day > 30) {
                throw new IllegalStateException("五行局/生日越界，无法安紫微（局=" + bureau + " 日=" + day + "）");
            }
            int ziweiIdx = ZIWEI_TABLE[bureau - 2][day - 1];
            int tianfuIdx = floorMod(4 - ziweiIdx, 12); // 紫微 ↔ 天府 以寅申为轴对称

            Map<Integer, List<String>> at = new TreeMap<>();
            StringBuilder sb = new StringBuilder();
            sb.append("〔北斗〕");
            for (int i = 0; i < ZIWEI_SERIES.length; i++) {
                int idx = floorMod(ziweiIdx + ZIWEI_SERIES_OFF[i], 12);
                at.computeIfAbsent(idx, k -> new ArrayList<>()).add(ZIWEI_SERIES[i]);
                sb.append(ZIWEI_SERIES[i]).append("@").append(ZHI[idx]).append("；");
            }
            sb.append("〔南斗〕");
            for (int i = 0; i < TIANFU_SERIES.length; i++) {
                int idx = floorMod(tianfuIdx + TIANFU_SERIES_OFF[i], 12);
                at.computeIfAbsent(idx, k -> new ArrayList<>()).add(TIANFU_SERIES[i]);
                sb.append(TIANFU_SERIES[i]).append("@").append(ZHI[idx]).append("；");
            }
            String mainStars = "紫微在" + ZHI[ziweiIdx] + "，天府在" + ZHI[tianfuIdx] + "｜" + sb;

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("mainStars", mainStars);
            completeResult(client, job, result);
            log.info("✅ placeMainStars | 五行局={} 生日={} | {}", bureau, day, mainStars);
        } catch (Exception e) {
            fail(job, client, "PLACE_MAIN_STARS_FAILED", e);
        }
    }

    // ==========================================================================
    // 节点 4：安辅佐煞忌星（placeAuxiliaryStars）
    // 实现方法：程序查表 + 本体 star:AuxiliaryStar/MaleficStar。
    // 出处：卷二·安文昌文曲星訣/安左辅右弼星诀/安天魁天钺诀/安天马星诀/安禄存星诀/
    //       安擎羊陀罗二星诀/安火铃二星诀/天空地劫诀/安天伤天使诀。
    // ==========================================================================
    @JobWorker(type = "placeAuxiliaryStars", autoComplete = false)
    public void handlePlaceAuxiliaryStars(final ActivatedJob job, final JobClient client) {
        try {
            Map<String, Object> vars = job.getVariablesAsMap();
            Map<String, String> cb = parseChartBase(str(vars.get("chartBase")));
            String skeleton = str(vars.get("chartSkeleton"));
            LocalDateTime solar = LocalDateTime.parse(cb.get("SOLAR"));
            int hourBranch = hourBranchOf(solar.getHour());
            int month = toInt(cb.get("LM"), solar.getMonthValue());
            int yearGan = yearGanOf(solar);
            int yearBranch = yearBranchOf(solar);
            int mingIdx = palaceBranch(skeleton, "命宫");

            Map<Integer, List<String>> at = new TreeMap<>();
            int chang = floorMod(10 - hourBranch, 12);      // 文昌：戌上起子逆至生时
            int qu = floorMod(4 + hourBranch, 12);          // 文曲：辰上起子顺至生时
            int zuo = floorMod(4 + (month - 1), 12);        // 左辅：辰上起正月顺
            int you = floorMod(10 - (month - 1), 12);       // 右弼：戌上起正月逆
            int kui = KUI_YUE[yearGan][0];
            int yue = KUI_YUE[yearGan][1];
            int ma = TIANMA[yearBranch];
            int lu = LUCUN[yearGan];
            int yang = floorMod(lu + 1, 12);                // 擎羊：禄前
            int tuo = floorMod(lu - 1, 12);                 // 陀罗：禄后
            int huo = HUOXING[yearBranch];
            int ling = LINGXING[yearBranch];
            int jie = floorMod(10 + hourBranch, 12);        // 地劫：亥上起子顺
            int kong = floorMod(10 - hourBranch, 12);       // 天空：亥上起子逆
            int shang = floorMod(mingIdx - 7, 12);          // 天伤：奴仆宫
            int shi = floorMod(mingIdx - 5, 12);            // 天使：疾厄宫

            place(at, chang, "文昌"); place(at, qu, "文曲"); place(at, zuo, "左辅"); place(at, you, "右弼");
            place(at, kui, "天魁"); place(at, yue, "天钺"); place(at, ma, "天马"); place(at, lu, "禄存");
            place(at, yang, "擎羊"); place(at, tuo, "陀罗"); place(at, huo, "火星"); place(at, ling, "铃星");
            place(at, jie, "地劫"); place(at, kong, "天空"); place(at, shang, "天伤"); place(at, shi, "天使");

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("auxiliaryStars", formatPlacements(at));
            completeResult(client, job, result);
            log.info("✅ placeAuxiliaryStars | {}", result.get("auxiliaryStars"));
        } catch (Exception e) {
            fail(job, client, "PLACE_AUXILIARY_STARS_FAILED", e);
        }
    }

    // ==========================================================================
    // 节点 5：安杂曜与太岁诸神（placeMiscStars）
    // 实现方法：程序查表（deityRuleMap）+ 本体 star:MiscStar/YearlyDeity/FourFlyingStar/FlyingTriSha。
    // 出处：卷二·安天刑天姚星诀/安三台八座二星诀/安天哭天虚星诀/安龙池凤阁诀/安台辅封诀/安红鸾天喜诀/
    //       安天德月德解神诀/安截路空亡诀/安旬中空亡诀/安十二宫太岁杀禄诀/安斗君诀。
    // ==========================================================================
    @JobWorker(type = "placeMiscStars", autoComplete = false)
    public void handlePlaceMiscStars(final ActivatedJob job, final JobClient client) {
        try {
            Map<String, Object> vars = job.getVariablesAsMap();
            Map<String, String> cb = parseChartBase(str(vars.get("chartBase")));
            String skeleton = str(vars.get("chartSkeleton"));
            LocalDateTime solar = LocalDateTime.parse(cb.get("SOLAR"));
            int hourBranch = hourBranchOf(solar.getHour());
            int month = toInt(cb.get("LM"), solar.getMonthValue());
            int day = toInt(cb.get("LD"), 1);
            int yearGan = yearGanOf(solar);
            int yearBranch = yearBranchOf(solar);
            boolean male = "男".equals(cb.get("GENDER"));
            boolean yearYang = GAN_YY[yearGan] == 1;

            Map<Integer, List<String>> at = new TreeMap<>();
            int taiSan = floorMod(6 + hourBranch, 12);          // 台辅：午上起子顺至生时
            int fengGao = floorMod(2 + hourBranch, 12);         // 封诰：寅起子顺至生时
            // 三台/八座需左辅/右弼宫
            int zuo = floorMod(4 + (month - 1), 12);
            int you = floorMod(10 - (month - 1), 12);
            int sanTai = floorMod(zuo + (day - 1), 12);         // 三台寻左辅初一加左辅宫顺至生日
            int baZuo = floorMod(you - (day - 1), 12);          // 八座寻右弼初一加右弼宫逆至生日
            int tianXing = floorMod(9 + (month - 1), 12);       // 天刑：酉上起正月顺
            int tianYao = floorMod(1 + (month - 1), 12);        // 天姚：丑上起正月顺
            int tianKu = floorMod(6 - yearBranch, 12);          // 天哭：午起子逆至生年
            int tianXu = floorMod(6 + yearBranch, 12);          // 天虚：午起子顺至生年
            // 龙池/凤阁（卷二·安龙池凤阁诀）——起例见 longChi()/fengGe()。
            int longChi = longChi(yearBranch);
            int fengGe = fengGe(yearBranch);
            int hongLuan = floorMod(3 - yearBranch, 12);        // 红鸾：卯起子逆至太岁
            int tianXi = floorMod(hongLuan + 6, 12);            // 天喜：红鸾对宫
            int tianDe = floorMod(9 + yearBranch, 12);          // 天德（出生年基准）：酉起子顺至太岁
            int yueDe = floorMod(0 + yearBranch, 12);           // 月德：子起子顺至太岁
            int jieShen = floorMod(10 - yearBranch, 12);        // 解神：戌起子逆至太岁
            place(at, taiSan, "台辅"); place(at, fengGao, "封诰"); place(at, sanTai, "三台"); place(at, baZuo, "八座");
            place(at, tianXing, "天刑"); place(at, tianYao, "天姚"); place(at, tianKu, "天哭"); place(at, tianXu, "天虚");
            place(at, longChi, "龙池"); place(at, fengGe, "凤阁"); place(at, hongLuan, "红鸾"); place(at, tianXi, "天喜");
            place(at, tianDe, "天德"); place(at, yueDe, "月德"); place(at, jieShen, "解神");

            // ---- 诸杂曜补遗（恩光/天贵/天官/天福/天厨/天巫/天才/天寿/孤辰/寡宿/华盖/咸池）----
            //   ★出处：《全書》无此诸曜安星诀（语料 c0–c4 检索 0 命中），采外部通行安星法，
            //   一律标注〔外部通行法·非《全書》原文〕，不改《全書》第一权威（★8/10 可追溯）。
            int mingIdxM = palaceBranch(skeleton, "命宫");
            int shenIdxM = palaceBranch(skeleton, "身宫");
            int changM = floorMod(10 - hourBranch, 12);       // 文昌：戌上起子逆至生时（同节点 4）
            int quM = floorMod(4 + hourBranch, 12);           // 文曲：辰上起子顺至生时
            int enGuang = floorMod(changM + day - 2, 12);     // 恩光：文昌起初一顺至生日再退一步
            int tianGui = floorMod(quM + day - 2, 12);        // 天贵：文曲起初一顺至生日再退一步
            place(at, enGuang, "恩光"); place(at, tianGui, "天贵");
            place(at, TIAN_GUAN_BR[yearGan], "天官"); place(at, TIAN_FU_BR[yearGan], "天福");
            place(at, TIAN_CHU_BR[yearGan], "天厨");
            place(at, TIAN_WU_BR[floorMod(month - 1, 4)], "天巫");
            if (mingIdxM >= 0) place(at, floorMod(mingIdxM + yearBranch, 12), "天才"); // 天才：命宫起子顺至生年支
            if (shenIdxM >= 0) place(at, floorMod(shenIdxM + yearBranch, 12), "天寿"); // 天寿：身宫起子顺至生年支
            place(at, GU_CHEN_BR[yearBranch], "孤辰"); place(at, GUA_SU_BR[yearBranch], "寡宿");
            place(at, HUA_GAI_BR[yearBranch], "华盖"); place(at, XIAN_CHI_BR[yearBranch], "咸池");

            // 截路空亡（依年干）与旬中空亡（依年柱旬）
            int[] jieKong = JIEluKongwang(yearGan);
            int[] xunKong = xunZhongKongwang(yearGan, yearBranch);
            place(at, jieKong[0], "截空"); place(at, jieKong[1], "截空");
            place(at, xunKong[0], "旬空"); place(at, xunKong[1], "旬空");

            // 丧门/白虎/吊客/官府（四飞星，依出生年太岁；卷二·安丧门白虎吊客官府四飞星诀）
            //   流年太岁前二位丧门，后二位吊客；丧门对照白虎、吊客对照官府（对宫）。
            int sangMen = floorMod(yearBranch + 2, 12);
            int diaoKe = floorMod(yearBranch - 2, 12);
            place(at, sangMen, "丧门");
            place(at, floorMod(sangMen + 6, 12), "白虎");
            place(at, diaoKe, "吊客");
            place(at, floorMod(diaoKe + 6, 12), "官府");

            // 飞天神煞（奏书/将军/直符）：三合飞入（卷二·安飞天三杀诀，原文 c3 L103-106）。
            //   组内序《全書》未明列，采〔外部通行法·非《全書》原文〕：奏书/将军/直符依次＝长生/帝旺/墓。
            int[] feiSha = feiTianSanSha(yearBranch);
            place(at, feiSha[0], "奏书"); place(at, feiSha[1], "将军"); place(at, feiSha[2], "直符");

            // 出生年（太岁）流曜基准盘：博士十二神（禄存起，阳男阴女顺/阴男阳女逆）+ 流禄/流羊/流陀 + 斗君
            int lu = LUCUN[yearGan];
            boolean forward = (yearYang && male) || (!yearYang && !male);
            StringBuilder deities = new StringBuilder();
            for (int i = 0; i < 12; i++) {
                int idx = floorMod(lu + (forward ? i : -i), 12);
                deities.append(DEITY_12[i]).append("@").append(ZHI[idx]).append("；");
            }
            int douJun = douJun(yearBranch, month, hourBranch);
            String taiSuiBase = "基准太岁=" + ZHI[yearBranch] + "（生年干支 " + GAN[yearGan] + ZHI[yearBranch] + "）"
                    + "｜流禄=" + ZHI[lu] + " 流羊=" + ZHI[floorMod(lu + 1, 12)] + " 流陀=" + ZHI[floorMod(lu - 1, 12)]
                    + "｜斗君=" + ZHI[douJun] + "｜十二神：" + deities;

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("miscStars", formatPlacements(at));
            result.put("taiSuiBase", taiSuiBase);
            result.put("miscStarsNote", "〔外部通行法·非《全書》原文〕恩光/天贵/天官/天福/天厨/天巫/天才/天寿/孤辰/寡宿/华盖/咸池："
                    + "《全書》无安星诀（语料 0 命中），采外部通行安星法落地；《全書》第一权威不改。"
                    + "恩光＝文昌起初一顺至生日退一位，天贵＝文曲同法；天官/天福/天厨依生年干；天巫依生月；"
                    + "天才＝命宫起子顺至生年支，天寿＝身宫起子顺至生年支；孤辰寡宿/华盖/咸池依生年支三会三合。");
            completeResult(client, job, result);
            log.info("✅ placeMiscStars | {}", result.get("miscStars"));
        } catch (Exception e) {
            fail(job, client, "PLACE_MISC_STARS_FAILED", e);
        }
    }

    // ==========================================================================
    // 节点 6：安四化与宫干、十二长生（placeFourTransform）
    // 实现方法：程序查表（SIHUA 四化表 / 五虎遁宫干）+ SWRL R7/R14 + 本体 layout:Transformation/…。
    // 出处：卷二·安禄权科忌四星变化诀（生年干四化）/ 起五行寅例（宫干五虎遁）/
    //       安长生…（五行局起长生，男顺女逆）。〔飞星轨〕分野点。
    // ==========================================================================
    @JobWorker(type = "placeFourTransform", autoComplete = false)
    public void handlePlaceFourTransform(final ActivatedJob job, final JobClient client) {
        try {
            Map<String, Object> vars = job.getVariablesAsMap();
            Map<String, String> cb = parseChartBase(str(vars.get("chartBase")));
            String skeleton = str(vars.get("chartSkeleton"));
            String mainStars = str(vars.get("mainStars"));
            LocalDateTime solar = LocalDateTime.parse(cb.get("SOLAR"));
            int yearGan = yearGanOf(solar);
            boolean male = "男".equals(cb.get("GENDER"));
            int bureau = bureauFromSkeleton(skeleton);

            // 生年干四化（禄/权/科/忌）所落之宫
            String[] hua = SIHUA[yearGan];
            String[] huaName = {"化禄", "化权", "化科", "化忌"};
            StringBuilder ft = new StringBuilder();
            for (int i = 0; i < 4; i++) {
                Integer idx = starBranch(mainStars, hua[i]);
                ft.append(hua[i]).append(huaName[i]).append("→")
                        .append(idx == null ? "【待考·该星未安】" : ZHI[idx]).append("；");
            }
            String fourTransform = "生年干" + GAN[yearGan] + "：" + ft;

            // 宫干五虎遁（年干起寅宫干，顺布十二宫）；为飞星派宫干飞化之基
            int yinGan = WUHU_YIN_GAN[yearGan % 5];
            StringBuilder ps = new StringBuilder();
            for (int i = 0; i < 12; i++) {
                int branch = floorMod(BRANCH_YIN + i, 12);
                int gan = floorMod(yinGan + i, 10);
                ps.append(ZHI[branch]).append("宫=").append(GAN[gan]).append("；");
            }
            String palaceStems = ps.toString();

            // 十二长生：依五行局起长生，男命顺数、女命逆数
            int startBranch = CHANGSHENG_BRANCH[bureau];
            int startGan = CHANGSHENG_GAN[bureau];
            StringBuilder ts = new StringBuilder();
            for (int i = 0; i < 12; i++) {
                int branch = floorMod(startBranch + (male ? i : -i), 12);
                int gan = floorMod(startGan + (male ? i : -i), 10);
                ts.append(TWELVE_STAGE[i]).append("@").append(GAN[gan]).append(ZHI[branch]).append("；");
            }
            String twelveStages = "五行局" + bureauName(bureau) + "（" + (male ? "男命顺数" : "女命逆数") + "）：" + ts
                    + "〔卷二·安长生诀惟定起宫（火局寅/木局亥/水局申/金局巳/土局申）与男顺女逆、十二神名，"
                    + "不言天干；所附天干系通行取象，非原著明据，标【待考】〕";

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("fourTransform", fourTransform);
            result.put("palaceStems", palaceStems);
            result.put("twelveStages", twelveStages);
            completeResult(client, job, result);
            log.info("✅ placeFourTransform | {} | {}", fourTransform, twelveStages);
        } catch (Exception e) {
            fail(job, client, "PLACE_FOUR_TRANSFORM_FAILED", e);
        }
    }

    // ==========================================================================
    // 节点 V：验盘校正（verifyChart）— 条件性任务（G1 验盘支，居预排盘主干之后）
    // 实现方法：程序（多维比对加权：命宫主星特征 w3 / 时系星昌曲痣癍 w2 / 五行局体质 w1）+ 本体 diagnosis:ChartVerification。
    // 出处：I·61（《全書》未见系统验盘论述，采〔外部通行法·非《全書》原文〕——大德山人《紫微斗数论定盘》等）；轮次上限 5（超限标记需人工复核并终止）。
    // ==========================================================================
    @JobWorker(type = "verifyChart", autoComplete = false)
    public void handleVerifyChart(final ActivatedJob job, final JobClient client) {
        try {
            Map<String, Object> vars = job.getVariablesAsMap();
            String traits = str(vars.get("subjectTraits"));
            if (traits == null || traits.isBlank()) {
                throw new IllegalArgumentException("验盘模式须提供 subjectTraits（命主自述的相貌/性情/已知事件特征）");
            }
            int rounds = toInt(str(vars.get("verifyRounds")), 0) + 1;
            Map<String, String> cb = parseChartBase(str(vars.get("chartBase")));
            LocalDateTime solar = LocalDateTime.parse(cb.get("SOLAR"));
            int month = toInt(cb.get("LM"), solar.getMonthValue());
            int day = toInt(cb.get("LD"), 1);
            int yearGan = yearGanOf(solar);
            int hourBranch = hourBranchOf(solar.getHour());

            Map<String, Object> result = new LinkedHashMap<>();
            if (rounds > 5) {
                // BPMN G1b 契约：chartVerdict ∈ {「需校正」「无需校正」}（Gateway_ChartVerdict 唯二分支、无 default 流）。
                //   超限不自动重排 → 走「无需校正」入 J1，人工复核详情另存 verifyVerdict。
                result.put("chartVerdict", "无需校正");
                result.put("verifyVerdict", "需人工复核（轮次已达上限）");
                result.put("correctedTime", "");
                result.put("candidateTimes", "");
                result.put("verifyNote", "闭环轮次已达上限 5，标记「需人工复核」并终止（依《应用架构设计》§5 边界）");
                result.put("verifyRounds", rounds);
                completeResult(client, job, result);
                log.info("✅ verifyChart | verdict=需人工复核 rounds={}", rounds);
                return;
            }

            // ---- 候补时辰：输入 candidateTimes（「卯,辰」等）优先，否则默认 原始时辰±1 ----
            int[] cands = parseCandidateHours(str(vars.get("candidateTimes")), hourBranch);

            // ---- 多维比对加权〔外部通行法·非《全書》原文；I·61〕：自述特征 vs 各候补盘（命宫主星/时系星/五行局）----
            Set<String> universe = verifyTraitUniverse();
            Set<String> stated = extractStatedTraits(traits, universe);

            StringBuilder candSb = new StringBuilder();
            int best = -1;
            int bestScore = 0;
            boolean tie = false;
            for (int hb : cands) {
                StringBuilder d = new StringBuilder();
                int sc = verifyCandidateScore(month, day, hb, yearGan, stated, d);
                candSb.append(ZHI[hb]).append("时=").append(sc).append("分");
                if (d.length() > 0) {
                    candSb.append("[").append(d).append("]");
                }
                candSb.append("；");
                if (sc > bestScore) {
                    bestScore = sc;
                    best = hb;
                    tie = false;
                } else if (sc == bestScore && sc > 0) {
                    tie = true;
                }
            }

            String verdict;
            String corrected;
            if (stated.isEmpty() || bestScore <= 0) {
                verdict = "特征不足·需人工复核";
                corrected = "";
            } else if (tie) {
                verdict = "候补并列·需人工复核";
                corrected = "";
            } else if (best == hourBranch) {
                verdict = "定盘确认（原时辰相符）";
                corrected = "";
            } else {
                verdict = "建议改时辰→" + ZHI[best] + "时";
                corrected = ZHI[best] + "时";
            }

            String note = "验盘（定盘）〔外部通行法·非《全書》原文；I·61〕：以命宫主星性情/相貌（权重3）"
                    + "＋时系星昌曲坐命身痣癍（权重2）＋五行局体质（权重1）多维加权，比对候补时辰。"
                    + "原时辰=" + ZHI[hourBranch] + "时｜自述命中特征=" + (stated.isEmpty() ? "（无）" : String.join("、", stated))
                    + "｜候补评分=" + candSb + "｜轮次=" + rounds + "。"
                    + "〔《全書》未见系统验盘论述，本判据属实务通行法，仅作时辰修正辅助、不替代人工定盘〕";

            // BPMN G1b 契约：Gateway_ChartVerdict 仅认「需校正」「无需校正」且无 default 流 →
            //   「建议改时辰→X时」⇒ 需校正（回流节点 2 重排）；其余（定盘确认/候补并列/特征不足·需人工复核）⇒ 无需校正（入 J1，不自动重排）。
            //   四态详情另存 verifyVerdict，供人工复核与展示（chartVerdict=流程网关值，verifyVerdict=判读详情）。
            String gateVerdict = verdict.startsWith("建议改时辰") ? "需校正" : "无需校正";
            result.put("chartVerdict", gateVerdict);
            result.put("verifyVerdict", verdict);
            result.put("correctedTime", corrected);
            result.put("candidateTimes", candSb.toString());
            result.put("verifyNote", note);
            result.put("verifyRounds", rounds);
            completeResult(client, job, result);
            log.info("✅ verifyChart | gate={} verdict={} best={} score={} rounds={}",
                    gateVerdict, verdict, best < 0 ? "-" : ZHI[best], bestScore, rounds);
        } catch (Exception e) {
            fail(job, client, "VERIFY_CHART_FAILED", e);
        }
    }

    // ==========================================================================
    // 节点 7：定庙旺利陷与星辰生克（assessBrightness）
    // 实现方法：程序查表（brightnessMap 内置表 ↔ OBDA brightness_map）+ SWRL R6 + 本体 state:Brightness/PhaseRelation/PalaceStrength。
    // 出处：卷二·星辰庙陷（第一权威，13 主星等第表）/ 卷二·定十二宫弱强 / 卷二·定十二宫星辰落闲。〔三合轨〕
    // ==========================================================================
    @JobWorker(type = "assessBrightness", autoComplete = false)
    public void handleAssessBrightness(final ActivatedJob job, final JobClient client) {
        try {
            Map<String, Object> vars = job.getVariablesAsMap();
            Map<String, String> cb = parseChartBase(str(vars.get("chartBase")));
            String skeleton = str(vars.get("chartSkeleton"));
            Map<Integer, List<String>> at = mergePlacements(str(vars.get("mainStars")),
                    str(vars.get("auxiliaryStars")), str(vars.get("miscStars")));
            boolean male = "男".equals(cb.get("GENDER"));

            // 逐星定庙陷等第：依卷二·星辰庙陷表（与 DB brightness_map 同源），取代此前五行生克近似。
            StringBuilder bt = new StringBuilder();
            StringBuilder idle = new StringBuilder();
            for (int idx = 0; idx < 12; idx++) {
                List<String> stars = at.getOrDefault(idx, List.of());
                for (String st : stars) {
                    bt.append(st).append("@").append(ZHI[idx]).append("=").append(brightnessOf(st, idx)).append("；");
                    if (isIdleStar(st, idx)) {
                        idle.append(st).append("@").append(ZHI[idx]).append("；");
                    }
                }
            }
            // 卷二↔卷三庙陷异文（★异说并陈，⑲；卷二为第一权威/取用口径，不改基线）
            StringBuilder bv = new StringBuilder();
            for (int idx = 0; idx < 12; idx++) {
                for (String st : at.getOrDefault(idx, List.of())) {
                    String vn = brightnessVariantNote(st, idx);
                    if (vn != null) {
                        bv.append(vn).append("；");
                    }
                }
            }
            String brightnessVariants = "卷二·星辰庙陷与卷三·诸星庙陷汇总之异文（卷二为第一权威，卷三作异说并陈）："
                    + (bv.length() == 0 ? "本盘所坐之星无卷二↔卷三相左者。" : bv.toString());
            String brightnessTable = "【庙陷口径】依卷二·星辰庙陷表（与 DB brightness_map 同源）逐星定等第"
                    + "（庙/旺/得地/利益/平和/不得地/陷；辅佐杂曜不入表者标『平和』）。" + bt
                    + "｜" + brightnessVariants;
            // 星辰落闲（卷二·定十二宫星辰落闲）
            String idleStars = idle.length() == 0 ? "无星辰落闲" : idle.toString();

            // 落闲断语交叉复核（卷二·十二宫各星吉凶诀 × 定十二宫星辰落闲）：
            //   表内星（天机/天梁/七杀）实际落闲 → 引原文断语（附 c3 行号）；
            //   表外星（天同/廉贞/擎羊/火星/铃星）原文无定闲支，仅在该星落陷（闲弱近似）时以〔举例式·待考〕引文，不硬断。
            StringBuilder iv = new StringBuilder();
            for (int idx = 0; idx < 12; idx++) {
                for (String st : at.getOrDefault(idx, List.of())) {
                    String[] verse = idleVerseOf(st);
                    if (verse == null) {
                        continue;
                    }
                    if (isIdleStar(st, idx)) {
                        // 表内星落闲断语：若断语含「巨宿羊陀／羊陀火铃冲会」等煞凑条件，据实际盘面做布尔核验，
                        // 不再仅标「附加煞曜条件据原文再核」（客观化；不改《全書》第一权威）。
                        ShaCou scI = detectShaCou(verse[2]);
                        String condNote;
                        if (scI != null) {
                            boolean satI = shaCouSatisfied(scI, idx, at);
                            condNote = "煞凑条件「" + scI.phrase + "」客观核验：" + shaCouDescribe(scI, satI)
                                    + (satI ? "→煞凑已成，凶断成立" : "→煞凑未成，断语条件未足（不可径断）");
                        } else {
                            condNote = "附加煞曜条件据原文再核";
                        }
                        iv.append(st).append("@").append(ZHI[idx]).append("落闲（卷二·定十二宫星辰落闲）→ 断语：")
                                .append(verse[2]).append("（卷二·命宫·").append(st).append("吉凶诀，c3 L").append(verse[1])
                                .append("；断语原文，").append(condNote).append("）；");
                    } else if (!inIdleTable(st) && isFallen(st, idx)) {
                        iv.append(st).append("@").append(ZHI[idx]).append("落陷→〔断语·举例式·原文无定闲支·待考〕")
                                .append(verse[2]).append("（c3 L").append(verse[1]).append("）；");
                    }
                }
            }
            String idleVerseNote = iv.length() == 0
                    ? "本盘无「落闲宫」断语适用（表内星未落闲；表外星亦未落陷）。" : iv.toString();

            // 星曜间五行生克（同宫星辰相生/相克）
            StringBuilder sr = new StringBuilder();
            for (int idx = 0; idx < 12; idx++) {
                List<String> stars = at.getOrDefault(idx, List.of());
                for (int i = 0; i < stars.size(); i++) {
                    for (int j = i + 1; j < stars.size(); j++) {
                        int a = starElement(stars.get(i));
                        int b = starElement(stars.get(j));
                        if (a < 0 || b < 0) {
                            continue;
                        }
                        if (b == (a + 1) % 5) {
                            sr.append(stars.get(i)).append("生").append(stars.get(j)).append("（").append(ZHI[idx]).append("）；");
                        } else if (a == (b + 1) % 5) {
                            sr.append(stars.get(j)).append("生").append(stars.get(i)).append("（").append(ZHI[idx]).append("）；");
                        } else if (b == (a + 2) % 5) {
                            sr.append(stars.get(i)).append("克").append(stars.get(j)).append("（").append(ZHI[idx]).append("）；");
                        } else if (a == (b + 2) % 5) {
                            sr.append(stars.get(j)).append("克").append(stars.get(i)).append("（").append(ZHI[idx]).append("）；");
                        }
                    }
                }
            }
            String starRelations = sr.length() == 0 ? "同宫星辰无明显五行生克" : sr.toString();

            // 星曜落宫五行生克制化（卷二·论星辰生克制化「星曜全明生克制化之机，次看落于何宫，如廉真属火在寅宫，
            //   乃木乡能生廉真之火…」；「金入火乡 火入水乡 水入土乡 土入木乡 俱为受制」；卷一·星垣论同旨）。
            //   以宫位地支五行对星曜五行判生/克/受制，对应本体 state:PhaseRelation / state:phaseRelationType（生/克/制/化）。
            StringBuilder ph = new StringBuilder();
            for (int idx = 0; idx < 12; idx++) {
                for (String st : at.getOrDefault(idx, List.of())) {
                    if (starElement(st) < 0) {
                        continue;
                    }
                    String rel = palaceStarRelation(st, idx);
                    if (rel != null) {
                        ph.append(rel).append("；");
                    }
                }
            }
            String phaseRelations = ph.length() == 0 ? "星曜落宫无明显五行生克" : ph.toString();

            // 十二宫弱强（卷二·定十二宫弱强：男/女分列）+ 星辰落闲（卷二·定十二宫星辰落闲）
            String palaceStrength = male
                    ? "男命强宫：财帛、官禄、福德、迁移、田宅；弱宫：子女、奴仆、兄弟、父母。"
                    : "女命强宫：夫妻、子息、财帛、田宅、福德；余宫皆弱。";

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("brightnessTable", brightnessTable);
            result.put("brightnessVariants", brightnessVariants);
            result.put("starRelations", starRelations);
            result.put("phaseRelations", phaseRelations);
            result.put("palaceStrength", palaceStrength);
            result.put("idleStars", idleStars);
            result.put("idleVerseNote", idleVerseNote);
            completeResult(client, job, result);
            log.info("✅ assessBrightness | 同宫={} | 落宫={} | 落闲={} | 落闲断语={}", starRelations, phaseRelations, idleStars, idleVerseNote);
        } catch (Exception e) {
            fail(job, client, "ASSESS_BRIGHTNESS_FAILED", e);
        }
    }

    // ==========================================================================
    // 节点 8：星曜化气与诸星所主（interpretStarNature）
    // 实现方法：本体 state:StarCharacter（化气·静态）/pattern:StarMeaning + 程序编排（保留出处）。
    // 出处：卷一·諸星問答論（十四主星化气）；★化气（静态）与四化（动态）严格分列。
    // ==========================================================================
    @JobWorker(type = "interpretStarNature", autoComplete = false)
    public void handleInterpretStarNature(final ActivatedJob job, final JobClient client) {
        try {
            Map<String, Object> vars = job.getVariablesAsMap();
            String mainStars = str(vars.get("mainStars"));
            String auxStars = str(vars.get("auxiliaryStars"));
            Map<Integer, List<String>> at = parsePlacements(mainStars);

            StringBuilder qi = new StringBuilder();
            StringBuilder nature = new StringBuilder();
            for (String[] row : STAR_CHARACTER) {
                Integer idx = starBranch(mainStars, row[0]);
                if (idx != null) {
                    qi.append(row[0]).append("化气为").append(row[1]).append("；");
                    nature.append(row[0]).append("@").append(ZHI[idx]).append("：")
                            .append(row[2]).append("（出处：卷一·諸星問答論）").append('\n');
                }
            }
            // 卷一·形性賦（相貌体态/性情心性）——与「化气」并出分列
            StringBuilder xingXing = new StringBuilder();
            for (String[] row : STAR_XINGXING) {
                Integer idx = starBranch(mainStars, row[0]);
                if (idx == null && auxStars != null && !auxStars.isBlank()) {
                    idx = starBranch(auxStars, row[0]);
                }
                if (idx != null) {
                    xingXing.append(row[0]).append("@").append(ZHI[idx]).append("：")
                            .append(row[1]).append("（出处：卷一·形性賦）").append('\n');
                }
            }
            String transformationQi = "【化气·静态】" + qi + "（依卷一·諸星問答論）"
                    + "｜【四化·动态】见节点 6 fourTransform（化禄/权/科/忌，strict 分列，不混用）";

            // 卷二·十二宫各星吉凶诀引用：按「十二宫 × 该宫主星」取原文首句印证（全文见 palace_classic.tsv）
            StringBuilder palaceClassic = new StringBuilder();
            int mingIdxPc = -1;
            java.util.regex.Matcher skm = java.util.regex.Pattern
                    .compile("命宫=([子丑寅卯辰巳午未申酉戌亥])").matcher(str(vars.get("chartSkeleton")));
            if (skm.find()) {
                mingIdxPc = indexOf(ZHI, skm.group(1));
            }
            if (mingIdxPc >= 0) {
                for (int p = 0; p < 12; p++) {
                    int br = floorMod(mingIdxPc - p, 12);
                    List<String> pStars = at.get(br);
                    if (pStars == null || pStars.isEmpty()) {
                        continue;
                    }
                    for (String st : pStars) {
                        String q = palaceStarQuote(PALACE_NAME[p], st);
                        if (!q.isEmpty()) {
                            palaceClassic.append(PALACE_NAME[p]).append("·").append(st).append("：")
                                    .append(q).append('\n');
                        }
                    }
                }
            }

            // 卷二·命宫「宫位 × 生年干 → 财官格/贵格/凶」可客观判定（judgeMingGongGe）
            // 口径：命宫主星 + 命宫支 + 生年干 三重命中原文句方断，未命中空缺不臆断；单一真源＝palace_classic.tsv。
            StringBuilder mingGongGe = new StringBuilder();
            int birthGanMg = -1;
            Map<String, String> cbMg = parseChartBase(str(vars.get("chartBase")));
            if (cbMg.get("SOLAR") != null && !cbMg.get("SOLAR").isBlank()) {
                try {
                    birthGanMg = yearGanOf(LocalDateTime.parse(cbMg.get("SOLAR")));
                } catch (Exception ignore) {
                    birthGanMg = -1;
                }
            }
            if (mingIdxPc >= 0 && birthGanMg >= 0) {
                Set<String> mainSetMg = new HashSet<>();
                mainSetMg.addAll(Arrays.asList(ZIWEI_SERIES));
                mainSetMg.addAll(Arrays.asList(TIANFU_SERIES));
                List<String> pStarsMg = at.get(mingIdxPc);
                if (pStarsMg != null) {
                    for (String st : pStarsMg) {
                        if (!mainSetMg.contains(st)) {
                            continue;
                        }
                        String g = judgeMingGongGe(st, mingIdxPc, birthGanMg, at);
                        if (!g.isEmpty()) {
                            mingGongGe.append(g).append('\n');
                        }
                    }
                }
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("starNature", nature.toString().trim());
            result.put("starXingXing", xingXing.length() == 0 ? "" : xingXing.toString().trim());
            result.put("transformationQi", transformationQi);
            result.put("palaceClassic", palaceClassic.toString().trim());
            result.put("mingGongGe", mingGongGe.toString().trim());
            completeResult(client, job, result);
            log.info("✅ interpretStarNature | {} | 命宫支={} 生年干={} 命宫主星={} mingGongGe={}",
                    qi, mingIdxPc >= 0 ? ZHI[mingIdxPc] : "?", birthGanMg >= 0 ? GAN[birthGanMg] : "?",
                    mingIdxPc >= 0 && at.get(mingIdxPc) != null ? at.get(mingIdxPc) : "?",
                    mingGongGe.toString().trim());
        } catch (Exception e) {
            fail(job, client, "INTERPRET_STAR_NATURE_FAILED", e);
        }
    }

    // ==========================================================================
    // 节点 9：空宫处理（handleEmptyPalace）— 前置：先于节点 10
    // 实现方法：程序（闭世界否定：本宫无主星）+ SWRL R11 + 本体 palace:EmptyPalace。
    // 出处：I·62（采后世通行借星 + 借光 + 四化随星规则〔外部通行法·非《全書》原文〕；《全書》未见成条借星口诀）。
    // ==========================================================================
    @JobWorker(type = "handleEmptyPalace", autoComplete = false)
    public void handleEmptyPalace(final ActivatedJob job, final JobClient client) {
        try {
            Map<String, Object> vars = job.getVariablesAsMap();
            Map<Integer, List<String>> main = parsePlacements(str(vars.get("mainStars")));
            Set<String> mainStarSet = new HashSet<>(Arrays.asList(ZIWEI_SERIES));
            mainStarSet.addAll(Arrays.asList(TIANFU_SERIES));

            StringBuilder sb = new StringBuilder();
            for (int idx = 0; idx < 12; idx++) {
                boolean hasMain = main.getOrDefault(idx, List.of()).stream().anyMatch(mainStarSet::contains);
                if (!hasMain) {
                    int opp = floorMod(idx + 6, 12);
                    List<String> borrowed = borrowMainStars(main, idx);
                    sb.append(ZHI[idx]).append("宫空（无主星）→借对宫").append(ZHI[opp]).append("之十四主星：")
                            .append(borrowed.isEmpty() ? "【对宫亦空·无可借主星】" : String.join("、", borrowed))
                            .append("；借光（庙陷随对宫").append(ZHI[opp]).append("原地）：").append(borrowedBrightness(main, idx))
                            .append("；四化随星借入（借来之四化，仍以本盘生年四化论）；");
                }
            }
            String emptyPalaceResult = sb.length() == 0 ? "无空宫（十二宫皆有主星）"
                    : sb + "（借星+借光+四化随星规则〔外部通行法·非《全書》原文；I·62〕：借对宫十四主星、" +
                    "辅煞杂曜不借、庙陷随对宫原地；《全書》未见成条借星口诀）";

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("emptyPalaceResult", emptyPalaceResult);
            completeResult(client, job, result);
            log.info("✅ handleEmptyPalace | {}", emptyPalaceResult);
        } catch (Exception e) {
            fail(job, client, "HANDLE_EMPTY_PALACE_FAILED", e);
        }
    }

    // ==========================================================================
    // 节点 10：三方四正·夹宫·星曜组合（analyzeSurroundPalaces）
    // 实现方法：程序（取宫变换）+ SWRL R9/R10/R12 + 本体 palace:SanFangSiZheng/Adjoining/pattern:StarCombination。
    // 出处：卷三·諸格断语（夹宫诸格：紫府夹命/日月夹命/昌曲夹命/羊陀夹忌/火铃夹命/魁钺夹命/左右夹命/劫空夹命）。〔三合轨〕
    // ==========================================================================
    @JobWorker(type = "analyzeSurroundPalaces", autoComplete = false)
    public void handleAnalyzeSurroundPalaces(final ActivatedJob job, final JobClient client) {
        try {
            Map<String, Object> vars = job.getVariablesAsMap();
            String skeleton = str(vars.get("chartSkeleton"));
            Map<Integer, List<String>> at = mergePlacements(str(vars.get("mainStars")),
                    str(vars.get("auxiliaryStars")), str(vars.get("emptyPalaceResult")));
            int mingIdx = palaceBranch(skeleton, "命宫");
            int shenIdx = palaceBranch(skeleton, "身宫");

            // 三方四正（本宫 + 对宫 + 三合二宫，三合按 +4/+8 取）
            StringBuilder sfsz = new StringBuilder();
            for (int p = 0; p < 12; p++) {
                int[] box = {p, floorMod(p + 4, 12), floorMod(p + 8, 12), floorMod(p + 6, 12)};
                sfsz.append(ZHI[p]).append("宫三方四正：");
                for (int b : box) {
                    sfsz.append(ZHI[b]).append("[").append(String.join("", at.getOrDefault(b, List.of()))).append("]");
                }
                sfsz.append("；");
            }

            // 夹宫（邻宫）：紫府/日月/昌曲/羊陀/火铃/魁钺/左右/劫空 夹命/夹身
            StringBuilder clamp = new StringBuilder();
            clampPattern(clamp, at, mingIdx, "命宫");
            if (shenIdx != mingIdx) {
                clampPattern(clamp, at, shenIdx, "身宫");
            }
            String clampRelations = clamp.length() == 0 ? "无成格之夹" : clamp.toString();

            String combinationMeaning = "同宫双星/三星、三方四正复合象义（星曜组合断法）；"
                    + "组合断法与辅煞交互同节（I·52/I·64）。";

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("surroundPalaces", sfsz.toString());
            result.put("clampRelations", clampRelations);
            result.put("combinationMeaning", combinationMeaning);
            completeResult(client, job, result);
            log.info("✅ analyzeSurroundPalaces | 夹={}", clampRelations);
        } catch (Exception e) {
            fail(job, client, "ANALYZE_SURROUND_PALACES_FAILED", e);
        }
    }

    // ==========================================================================
    // 节点 11：格局判定（determinePattern）—〔飞星轨〕汇合点
    // 实现方法：程序（多格并见取高格编排、流派主取编排）+ SWRL R12/R13/R23 + 本体 pattern:*。
    // 出处：卷一·諸格论/定富局·定贵局·定贫贱局·定杂局；卷三·諸格断语；④取高格余格备参、⑲主基线优先+异说并陈。
    // ==========================================================================
    @JobWorker(type = "determinePattern", autoComplete = false)
    public void handleDeterminePattern(final ActivatedJob job, final JobClient client) {
        try {
            Map<String, Object> vars = job.getVariablesAsMap();
            Map<String, String> cb = parseChartBase(str(vars.get("chartBase")));
            String skeleton = str(vars.get("chartSkeleton"));
            Map<Integer, List<String>> at = mergePlacements(str(vars.get("mainStars")),
                    str(vars.get("auxiliaryStars")), str(vars.get("miscStars")));
            int mingIdx = palaceBranch(skeleton, "命宫");
            String fourTransform = str(vars.get("fourTransform"));

            List<String> hit = new ArrayList<>();
            String ziwei = branchOfStar(at, "紫微");
            String tianfu = branchOfStar(at, "天府");
            String taiyang = branchOfStar(at, "太阳");
            String taiyin = branchOfStar(at, "太阴");
            String tianxiang = branchOfStar(at, "天相");
            String tianji = branchOfStar(at, "天机");
            String pojun = branchOfStar(at, "破军");
            String qisha = branchOfStar(at, "七杀");
            String tanlang = branchOfStar(at, "贪狼");
            String huo = branchOfStar(at, "火星");
            String ling = branchOfStar(at, "铃星");
            String zuo = branchOfStar(at, "左辅");
            String you = branchOfStar(at, "右弼");
            String chang = branchOfStar(at, "文昌");
            String qu = branchOfStar(at, "文曲");

            if (eq(ziwei, tianfu)) hit.add("紫府同宫");
            if (inSanFang(mingIdx, ziwei) && inSanFang(mingIdx, tianfu)) hit.add("紫府朝垣");
            if (eq(taiyang, taiyin)) hit.add("日月同临");
            if (inSanFang(mingIdx, taiyang) && inSanFang(mingIdx, taiyin)) hit.add("日月并明");
            if (inSanFang(mingIdx, tianfu) && inSanFang(mingIdx, tianxiang)) hit.add("府相朝垣");
            if (inSanFang(mingIdx, tianji) && inSanFang(mingIdx, taiyin)
                    && inSanFang(mingIdx, "天同") && inSanFang(mingIdx, "天梁")) hit.add("机月同梁格");
            // 火贵格（诸格论·卷一「论贪狼遇火名为火贵格 三合照身命是也」；卷三「火遇贪狼照命宫，
            //   封侯食禄是英雄，三方倘若无凶杀」）——贪狼与火星于命宫三方四正相会（非必同宫）。
            //   同宫且庙旺者另见定贵局「贪火相逢」。
            if (inSanFang(mingIdx, huo) && inSanFang(mingIdx, tanlang)) {
                hit.add("火贵格（诸格论·卷一）：贪狼遇火，三合照身命");
            }
            if (eq(ling, tanlang)) hit.add("铃贪格");
            if (inSanFang(mingIdx, zuo) && inSanFang(mingIdx, you)) hit.add("左右朝垣格");
            if (inSanFang(mingIdx, chang) && inSanFang(mingIdx, qu)) hit.add("文星朝命格");
            if (eq(ziwei, qisha) && fourTransform.contains("七杀化权")) hit.add("紫微七杀化权格");
            if (containsAny(fourTransform, "化禄", "化权", "化科") && isHuaInMing(mingIdx, at, fourTransform)) {
                hit.add("科权禄主格");
            }
            if (eq(pojun, qisha)) {
                hit.add("杀破狼动态格（破军七杀同宫，备参）");
            }

            // ---- 卷一·定富局 / 定贵局 / 定贫贱局（成格条件逐条判定；「见前批注」而条件不明确者留待考，不臆断）----
            int shenIdx = palaceBranch(skeleton, "身宫");
            int tianZhaiIdx = floorMod(mingIdx - 9, 12);           // 田宅宫（十二宫逆布：命-9）
            List<String> mingList = at.getOrDefault(mingIdx, List.of());
            Set<String> mingSet = new HashSet<>(mingList);
            Set<String> mingMain = new HashSet<>();
            for (String s0 : mingList) {
                if (isMainStar(s0)) {
                    mingMain.add(s0);
                }
            }
            List<String> jia = new ArrayList<>(at.getOrDefault(floorMod(mingIdx - 1, 12), List.of()));
            jia.addAll(at.getOrDefault(floorMod(mingIdx + 1, 12), List.of()));
            Set<String> sanFang = new HashSet<>();
            for (int b : new int[]{mingIdx, floorMod(mingIdx + 4, 12), floorMod(mingIdx + 8, 12), floorMod(mingIdx + 6, 12)}) {
                sanFang.addAll(at.getOrDefault(b, List.of()));
            }
            Set<Integer> kongIdx = new HashSet<>();
            for (Map.Entry<Integer, List<String>> e : at.entrySet()) {
                if (e.getValue().contains("截空") || e.getValue().contains("旬空")) {
                    kongIdx.add(e.getKey());
                }
            }
            List<String> tianZhaiList = at.getOrDefault(tianZhaiIdx, List.of());
            List<String> duiList = at.getOrDefault(floorMod(mingIdx + 6, 12), List.of());

            // 定富局（卷一·定富局，6 格）
            if (mingSet.contains("天相") && jia.contains("武曲") && jia.contains("天梁")) {
                hit.add("财荫夹印（富局·卷一）：天相守命，武曲天梁来夹");
            }
            if (mingSet.contains("武曲") && jia.contains("太阳") && jia.contains("太阴")) {
                hit.add("日月夹财（富局·卷一）：武曲守命，日月来夹");
            }
            if (mingSet.contains("天马") && jia.contains("武曲") && jia.contains("禄存")) {
                hit.add("财禄夹马（富局·卷一）：天马守命，武曲禄存来夹");
            }
            if (shenIdx >= 0 && shenIdx == tianZhaiIdx && inSanFang(tianZhaiIdx, "天梁") && inSanFang(tianZhaiIdx, "天相")) {
                hit.add("荫印拱身（富局·卷一）：身临田宅，天梁天相拱冲");
            }
            if (tianZhaiList.contains("太阳") && tianZhaiList.contains("太阴")) {
                hit.add("日月照璧（富局·卷一）：日月临田宅宫");
            }
            if (mingIdx == indexOf(ZHI, "午") && mingMain.contains("太阳") && mingMain.size() == 1) {
                hit.add("金灿光辉（富局·卷一）：太阳单守命在午");
            }

            // 定贵局（卷一·定贵局；条件明确者判定）
            if (jia.contains("太阳") && jia.contains("太阴")) {
                hit.add("日月夹命（贵局·卷一）");
            }
            if (mingIdx == indexOf(ZHI, "卯") && mingMain.contains("太阳")) {
                hit.add("日出扶桑（贵局·卷一）：太阳守命在卯");
            }
            if (mingIdx == indexOf(ZHI, "亥") && mingMain.contains("太阴")) {
                hit.add("月落亥宫·月朗天门（贵局·卷一）：太阴守命在亥");
            }
            if (tianZhaiIdx == indexOf(ZHI, "子") && tianZhaiList.contains("太阴")) {
                hit.add("月生沧海（贵局·卷一）：太阴在子守田宅");
            }
            if (mingMain.contains("紫微")
                    && (inSanFang(mingIdx, "左辅") || jia.contains("左辅"))
                    && (inSanFang(mingIdx, "右弼") || jia.contains("右弼"))) {
                hit.add("辅弼拱主（贵局·卷一）：紫微守命，辅弼来拱/夹");
            }
            if (mingMain.contains("紫微") && mingSet.contains("左辅") && mingSet.contains("右弼")) {
                hit.add("君臣庆会（贵局·卷一）：紫微左右同守命");
            }
            if (mingSet.contains("禄存") && jia.contains("天梁") && jia.contains("天相")) {
                hit.add("财印夹禄（贵局·卷一）：禄存守命，天梁天相来夹");
            }
            if ((mingSet.contains("天魁") && duiList.contains("天钺"))
                    || (mingSet.contains("天钺") && duiList.contains("天魁"))
                    || (mingSet.contains("天魁") && mingSet.contains("天钺"))) {
                hit.add("坐贵向贵（贵局·卷一）：魁钺坐拱命宫");
            }
            if (mingSet.contains("天刑") && mingSet.contains("廉贞")) {
                hit.add("刑囚夹印（贵局·卷一）：天刑廉贞同临命宫");
            }
            if (mingIdx == indexOf(ZHI, "午") && mingSet.contains("天马") && mingSet.contains("擎羊")) {
                hit.add("马头带剑（贵局·卷一）：天马带刃居午");
            }
            // 七杀朝斗（贵局·卷一）：卷三·谈星要论「七杀寅申子午一生爵禄荣昌，为七杀朝斗格」
            //   ⇒ 七杀守命在 寅申子午 四宫（旧仅判 子午，漏 寅申）。见 isQiShaChaoDou()。
            if (isQiShaChaoDou(mingIdx) && mingMain.contains("七杀")) {
                hit.add("七杀朝斗（贵局·卷一）：七杀守命寅申子午（卷三·谈星要论「七杀寅申子午一生爵禄荣昌」）");
            }
            if (mingSet.contains("贪狼") && mingSet.contains("火星")
                    && !"陷".equals(brightnessOf("贪狼", mingIdx)) && !"陷".equals(brightnessOf("火星", mingIdx))) {
                hit.add("贪火相逢（贵局·卷一）：贪狼火星同宫庙旺");
            }
            if (mingIdx == indexOf(ZHI, "卯") && mingMain.contains("武曲")) {
                hit.add("武曲守垣（贵局·卷一）：武曲守命在卯");
            }
            if (mingIdx == indexOf(ZHI, "卯") && mingMain.contains("巨门") && mingMain.contains("天机")) {
                hit.add("巨机居卯（贵局·卷一）：巨门天机守命在卯");
            }
            if (mingSet.contains("擎羊") && (mingIdx == indexOf(ZHI, "辰") || mingIdx == indexOf(ZHI, "戌")
                    || mingIdx == indexOf(ZHI, "丑") || mingIdx == indexOf(ZHI, "未"))) {
                hit.add("羊刃入庙（贵局·卷一）：擎羊辰戌丑未守命");
            }
            if (mingMain.contains("紫微") && jia.contains("太阳") && jia.contains("太阴")) {
                hit.add("金舆扶驾（贵局·卷一）：紫微守命，日月来夹");
            }
            if (isHuaInSanFang(mingIdx, at, fourTransform)) {
                hit.add("科权禄拱（贵局·卷一）：命宫三方四正会科/权/禄");
            }
            for (String s0 : mingList) {
                if (fourTransform.contains(s0 + "化权") || fourTransform.contains(s0 + "化禄")) {
                    String bl = brightnessOf(s0, mingIdx);
                    if ("庙".equals(bl) || "旺".equals(bl)) {
                        hit.add("权禄生逢（贵局·卷一）：化权/化禄守命庙旺");
                        break;
                    }
                }
            }

            // ---- 卷一·太微賦 / 重補斗數彀率 具名格（格名+客观条件明确者逐条落地；第十七轮）----
            // 出处：卷一·太微賦（c2「太陽居午，謂之日麗中天…」「太陰居子，號曰水澄桂萼…」「文耗居寅卯，謂之眾水朝東」
            //   「帝坐金車則曰金輿捧櫛」）；重補斗數彀率「紫微坐命庫則曰金輿捧櫛輦」。
            //   原文为论断总纲，具名格按原文条件落地；仅举格名而无客观条件者见 taiweiPending（取文不臆断）。
            hit.addAll(judgeTaiweiMingGe(mingIdx, mingMain, mingSet));
            String taiweiPending = taiweiPendingText();

            // 诸格论·卷一（对面朝斗/石中隐玉/兼文武）明录条件者
            if ((mingIdx == indexOf(ZHI, "子") || mingIdx == indexOf(ZHI, "午")) && mingSet.contains("禄存")) {
                hit.add("对面朝斗格（诸格论·卷一）：子午宫逢禄存（「禄有对面在迁移，子午逢之利禄宜」）");
            }
            if ((mingIdx == indexOf(ZHI, "子") || mingIdx == indexOf(ZHI, "午")) && mingMain.contains("巨门")) {
                hit.add("石中隐玉格（诸格论·卷一）：命在子午逢巨门（「巨门子午二宫逢，身命逢之必贵荣」）");
            }
            List<String> shenList = at.getOrDefault(shenIdx, List.of());
            if ((mingSet.contains("文曲") && mingSet.contains("武曲"))
                    || (shenList.contains("文曲") && shenList.contains("武曲"))) {
                hit.add("兼文武格（诸格论·卷一）：文曲武曲同守身命");
            }

            // 定贵局·禄马佩印（卷一「马前有禄印星同宫是也」）——天马顺前一宫逢禄存、天相同宫。
            Integer maPeiYinIdx = branchIndexOf(at, "天马");
            if (maPeiYinIdx != null) {
                List<String> maFrontStars = at.getOrDefault(floorMod(maPeiYinIdx + 1, 12), List.of());
                if (maFrontStars.contains("禄存") && maFrontStars.contains("天相")) {
                    hit.add("禄马佩印（贵局·卷一）：马前有禄印（禄存·天相）同宫〔释读·待考〕");
                }
            }

            // 定贫贱局（卷一·定贫贱局；条件明确者判定）
            if (kongIdx.contains(mingIdx) && mingSet.contains("廉贞")) {
                hit.add("生不逢时（贫贱局·卷一）：命坐空亡逢廉贞");
            }
            Integer luIdx = branchIndexOf(at, "禄存");
            if (luIdx != null && kongIdx.contains(luIdx)
                    && (at.getOrDefault(luIdx, List.of()).contains("地劫") || at.getOrDefault(luIdx, List.of()).contains("天空"))) {
                hit.add("禄逢两杀（贫贱局·卷一）：禄存坐空亡又逢空劫");
            }
            Integer maIdx = branchIndexOf(at, "天马");
            if (maIdx != null && kongIdx.contains(maIdx)) {
                hit.add("马落空亡（贫贱局·卷一）：天马落空亡");
            }
            boolean shenWuZhen = shenIdx >= 0 && at.getOrDefault(shenIdx, List.of()).contains("武曲")
                    && at.getOrDefault(shenIdx, List.of()).contains("廉贞");
            if ((mingSet.contains("武曲") && mingSet.contains("廉贞")) || shenWuZhen) {
                hit.add("财与囚仇（贫贱局·卷一）：武曲廉贞同守身命");
            }
            if (mingSet.contains("破军") && isFallen("破军", mingIdx)) {
                hit.add("一生孤贫（贫贱局·卷一）：破军守命星陷地");
            }
            long shaCnt = mingList.stream().filter(s0 -> List.of("擎羊", "陀罗", "火星", "铃星").contains(s0)).count();
            if (shaCnt >= 3 && mingList.stream().anyMatch(s0 -> isFallen(s0, mingIdx))) {
                hit.add("君子在野（贫贱局·卷一）：四杀守身命临陷地");
            }
            if (mingSet.contains("禄存") && (mingSet.contains("地劫") || mingSet.contains("天空"))) {
                hit.add("两重华盖（贫贱局·卷一）：禄存(化禄)坐命遇空劫");
            }
            // 日月藏辉（贫贱局·卷一「日月反背又逢巨暗是也」）——太阳、太阴俱落陷（反背），
            //   又逢巨门（暗）于命宫三方四正。〔释读·待考〕
            Integer sunIdxFB = branchIndexOf(at, "太阳");
            Integer moonIdxFB = branchIndexOf(at, "太阴");
            if (sunIdxFB != null && moonIdxFB != null && isFallen("太阳", sunIdxFB) && isFallen("太阴", moonIdxFB)
                    && inSanFang(mingIdx, "巨门")) {
                hit.add("日月藏辉（贫贱局·卷一）：日月反背又逢巨暗（巨门）〔释读·待考〕");
            }

            // 生年干（供得地合格诀生干条件、卷三同垣各司所宜共用）
            int tongYuanGan = -1;
            int hourBranch11 = -1;
            try {
                String solarStr = cb.get("SOLAR");
                if (solarStr != null && !solarStr.isBlank()) {
                    LocalDateTime solarDt = LocalDateTime.parse(solarStr);
                    tongYuanGan = yearGanOf(solarDt);
                    hourBranch11 = hourBranchOf(solarDt.getHour());
                }
            } catch (Exception ignore) {
                tongYuanGan = -1;
                hourBranch11 = -1;
            }

            // ---- 卷一·十二宫诸星得地合格诀 / 失陷破格诀 / 得地富贵论 / 失陷贫贱论（按命宫地支逐支判定；原文 c2 L556-596）----
            String palaceZhiGrid = judgePalaceZhiGrid(mingIdx, at, tongYuanGan);

            // ---- 卷三·论诸星同垣各司所宜分别富贵贫贱夭寿（星情格语逐条判定；原文 c4 L178-477）----
            String tongYuan = judgeTongYuan(mingIdx, at, tongYuanGan, skeleton,
                    "男".equals(cb.get("GENDER")), fourTransform);
            // 定贵局「明珠出海/文星暗拱/明禄暗禄/科明暗禄」：卷一·定贵局原作「见前批注」，成格条件未明。
            //   ★成格条件采外部通行法（非《全書》原文），落地并标注来源，不改《全書》第一权威（★8/10）。
            //   明珠出海：太阳在辰守命、戌宫太阴冲照（或太阴在戌守命、辰宫太阳冲照）。
            int anHeIdx = sixHePartner(mingIdx);                    // 命宫之暗合（六合）宫
            boolean mingZhuChuHai = "辰".equals(taiyang) && "戌".equals(taiyin)
                    && (mingIdx == indexOf(ZHI, "辰") || mingIdx == indexOf(ZHI, "戌"));
            // 文星暗拱：命宫坐吉星，迁/财/官三方（对宫＋三合二宫）有昌曲或化科朝拱。
            boolean mingHasJi = mingSet.stream().anyMatch(s -> List.of("禄存", "天魁", "天钺", "左辅", "右弼",
                    "文昌", "文曲", "天官", "天福", "恩光", "天贵").contains(s)) || isHuaInMing(mingIdx, at, fourTransform);
            boolean wenSanFang = false;
            for (int b : new int[]{floorMod(mingIdx + 6, 12), floorMod(mingIdx + 8, 12), floorMod(mingIdx + 4, 12)}) {
                List<String> bl = at.getOrDefault(b, List.of());
                if (bl.contains("文昌") || bl.contains("文曲") || huaInPalace(b, at, fourTransform, "化科")) {
                    wenSanFang = true;
                    break;
                }
            }
            boolean wenXingAnGong = mingHasJi && wenSanFang;
            // 明禄暗禄：命宫坐禄（禄存/化禄），且暗合（六合）宫亦坐禄。
            boolean mingLuAnLu = palaceHasLu(mingIdx, at, fourTransform) && palaceHasLu(anHeIdx, at, fourTransform);
            // 科明暗禄：化科守命，且暗合宫坐禄（禄存/化禄）。
            boolean keMingAnLu = huaInPalace(mingIdx, at, fourTransform, "化科") && palaceHasLu(anHeIdx, at, fourTransform);
            if (mingZhuChuHai) {
                hit.add("明珠出海（贵局·卷一）〔外部通行法·非《全書》原文：太阳在辰守命、戌宫太阴冲照，或反之〕");
            }
            if (wenXingAnGong) {
                hit.add("文星暗拱（贵局·卷一）〔外部通行法·非《全書》原文：命坐吉星、迁财官三方昌曲/化科朝拱〕");
            }
            if (mingLuAnLu) {
                hit.add("明禄暗禄（贵局·卷一）〔外部通行法·非《全書》原文：命宫坐禄、暗合（六合）宫亦坐禄，位至公卿〕");
            }
            if (keMingAnLu) {
                hit.add("科明暗禄（贵局·卷一）〔外部通行法·非《全書》原文：化科守命、暗合宫坐禄〕");
            }
            String guiGePending = "定贵局 明珠出海/文星暗拱/明禄暗禄/科明暗禄：原文「见前批注」，成格条件采〔外部通行法·非《全書》原文〕并已判定"
                    + (hit.stream().anyMatch(h -> containsAny(h, "明珠出海", "文星暗拱", "明禄暗禄", "科明暗禄"))
                            ? "。" : "（本盘未成）。");

            // 定富贵贫贱十等论（卷一 c2 L545-555）：按命宫/三方四正判十等类型，可多等并见
            List<String> tenGrades = judgeTenGrades(mingSet, mingMain, sanFang);
            String tenGradeText = "十等论（卷一·定富贵贫贱十等论）："
                    + (tenGrades.isEmpty() ? "未入十等（以星情宫位常规论断）" : String.join("；", tenGrades))
                    + "〔释读·条件原为举例式〕";

            // 取高格（贵局 > 富局 > 杂局；本内置优先级表，第 11 步可经 OBDA 精化）；余格备参
            // 卷三·谈星要论：命格等第（看命总纲/论人命入格）＋论格星数高下＋论人生时安命吉凶
            String mingGeGrade = judgeMingGeGrade(hit, mingMain, sanFang, at, mingIdx, fourTransform);
            String geXingShu = judgeGeXingShu(hit);
            String anMingJiXiong = hourBranch11 >= 0 ? judgeAnMingJiXiong(hourBranch11, mingIdx) : "（生时未定）";
            // 命宫三方四正吉曜会照布尔判定（昌曲/辅弼/魁钺/禄马/科权禄 + 三方无凶杀 + 会吉/会煞计数）
            String sanFangJiYao = judgeSanFangJiYao(mingIdx, at, fourTransform);
            // 卷三·谈星要论开篇（c4 L12-21）：八座六宫聚吉/聚凶 + 父母妻子三宫劫空杀忌→僧道
            String tanXingYaoLun = judgeTanXingYaoLun(mingIdx, at, skeleton);
            String patternResult = (hit.isEmpty() ? "未成诸格（以星情宫位常规论断）"
                    : "成格：" + String.join("、", hit) + "（多格并见取高格，余格备参，⑱）")
                    + "｜" + palaceZhiGrid + "｜" + mingGeGrade + "｜" + geXingShu + "｜" + anMingJiXiong
                    + "｜" + guiGePending + "｜" + tenGradeText + "｜" + tongYuan + "｜" + taiweiPending
                    + "｜" + sanFangJiYao + "｜" + tanXingYaoLun;
            String patternGrade = gradeOf(hit, at, mingIdx);

            // 四化飞星深层（飞星轨）：化入化出 / 宫干飞化 / 飞星落宫
            String flyingStars = "〔飞星轨〕" + fourTransform + "｜宫干（五虎遁）飞化之基见 palaceStems；"
                    + "飞星落宫以生年干四化所落之宫为准（化入本命/大限/流年之别）。";

            String schoolNote = "〔流派差异〕三合轨（节点 7/9/10）与飞星轨（节点 6/11）二轨并陈、不预设单轨；"
                    + "主取《全書》基线，异说并陈（⑲）。";

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("patternResult", patternResult);
            result.put("patternGrade", patternGrade);
            result.put("palaceZhiGrid", palaceZhiGrid);
            result.put("sanFangJiYao", sanFangJiYao);
            result.put("flyingStars", flyingStars);
            result.put("schoolNote", schoolNote);
            completeResult(client, job, result);
            log.info("✅ determinePattern | {} | {}", patternResult, patternGrade);
        } catch (Exception e) {
            fail(job, client, "DETERMINE_PATTERN_FAILED", e);
        }
    }

    // ==========================================================================
    // 节点 12：本命综合断（generateNatalReading）
    // 实现方法：本体 reading:Temperament/Kinship/Marriage/Career/Wealth/Disease/Longevity/GenderDifference + 程序编排。
    // 出处：卷一·諸星問答論；卷三·论诸星同垣各司所宜分别富贵贫贱夭寿；⑳ 命书体＋结构化并存、保留出处。
    // 已剥离运限（行限宫歌→15、阴骘延寿→16）。
    // ==========================================================================
    @JobWorker(type = "generateNatalReading", autoComplete = false)
    public void handleGenerateNatalReading(final ActivatedJob job, final JobClient client) {
        try {
            Map<String, Object> vars = job.getVariablesAsMap();
            String skeleton = str(vars.get("chartSkeleton"));
            Map<Integer, List<String>> at = mergePlacements(str(vars.get("mainStars")),
                    str(vars.get("auxiliaryStars")), str(vars.get("miscStars")));
            String starNature = str(vars.get("starNature"));
            String patternResult = str(vars.get("patternResult"));
            // 寿夭孤克残疾诸断（卷一·诸格论 c2 L510-544，12 格）→ reading:Longevity / reading:longevityType
            int mingIdx12 = palaceBranch(skeleton, "命宫");
            int shenIdx12 = palaceBranch(skeleton, "身宫");
            List<String> longevity = judgeLongevity(mingIdx12, shenIdx12, at);
            String longevityReading = "寿夭孤克残疾（卷一·诸格论）："
                    + (longevity.isEmpty() ? "本盘未命中原文所举诸格（以星情宫位常规论断）"
                    : String.join("；", longevity))
                    + "〔释读·原文多「如…」举例，取所举核心星曜/宫位为判据〕";

            StringBuilder r = new StringBuilder();
            r.append("【本命综合断】（命书体）\n");
            r.append("命宫").append(palaceView(skeleton, at, "命宫")).append("——性情形貌之所主；\n");
            r.append("兄弟").append(palaceView(skeleton, at, "兄弟")).append("——手足、助力；\n");
            r.append("夫妻").append(palaceView(skeleton, at, "妻妾")).append("——婚姻配偶；\n");
            r.append("子女").append(palaceView(skeleton, at, "子女")).append("——子息；\n");
            r.append("财帛").append(palaceView(skeleton, at, "财帛")).append("——财禄；\n");
            r.append("疾厄").append(palaceView(skeleton, at, "疾厄")).append("——疾病；\n");
            r.append("官禄").append(palaceView(skeleton, at, "官禄")).append("——事业功名；\n");
            r.append("田宅").append(palaceView(skeleton, at, "田宅")).append("——不动产；\n");
            r.append("福德").append(palaceView(skeleton, at, "福德")).append("——福泽、性情；\n");
            r.append("父母").append(palaceView(skeleton, at, "父母")).append("——父母长辈；\n");
            r.append("格局：").append(patternResult).append('\n');
            r.append("星情：").append(starNature.isEmpty() ? "（见节点 8）" : firstLine(starNature)).append('\n');
            r.append(longevityReading).append('\n');
            r.append("男女命同异：男重财官、女重夫子（详见女命/小儿专论节点）。\n");
            r.append("（结构化字段与命书体并存，⑳；运限另见大限/流年节点）");

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("natalReading", r.toString());
            result.put("longevityReading", longevityReading);
            completeResult(client, job, result);
            log.info("✅ generateNatalReading | 已产出本命综合断");
        } catch (Exception e) {
            fail(job, client, "GENERATE_NATAL_READING_FAILED", e);
        }
    }

    // ==========================================================================
    // 节点 W：女命专论（analyzeFemaleReading）
    // 出处：卷一·女命骨髓賦；卷三·論男女命同異。
    // ==========================================================================
    @JobWorker(type = "analyzeFemaleReading", autoComplete = false)
    public void handleFemaleReading(final ActivatedJob job, final JobClient client) {
        try {
            Map<String, Object> vars = job.getVariablesAsMap();
            String skeleton = str(vars.get("chartSkeleton"));
            Map<Integer, List<String>> at = mergePlacements(str(vars.get("mainStars")), str(vars.get("auxiliaryStars")));
            int mingIdx = palaceBranch(skeleton, "命宫");
            List<String> mingStars = at.getOrDefault(mingIdx, List.of());

            String femaleReading = "【女命专论】命宫（" + ZHI[mingIdx] + "）主星：" + String.join("、", mingStars) + "。"
                    + "论女命富贵贫贱、旺夫克子/旺子伤夫、纯和清贵/浊滥娼淫、滚浪桃花："
                    + "以命身宫及夫妻、子女、福德宫之吉曜煞曜定之（卷一·女命骨髓賦；卷三·論男女命同異）。"
                    + "夫妻宫见吉则旺夫、见煞则刑克；子女宫见吉则旺子。"
                    + "｜" + judgeNanNvTongYi(mingIdx, mingStars, at, skeleton);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("femaleReading", femaleReading);
            completeResult(client, job, result);
            log.info("✅ analyzeFemaleReading | {}", femaleReading);
        } catch (Exception e) {
            fail(job, client, "FEMALE_READING_FAILED", e);
        }
    }

    // ==========================================================================
    // 节点 C：小儿命专论（analyzeChildReading）
    // 出处：卷三·論小兒命/定小兒生時訣/論小兒剋親；㉑ P2 全纳（金锁铁蛇关/竹萝三限/克亲/童限）。
    // ==========================================================================
    @JobWorker(type = "analyzeChildReading", autoComplete = false)
    public void handleChildReading(final ActivatedJob job, final JobClient client) {
        try {
            Map<String, Object> vars = job.getVariablesAsMap();
            Map<String, String> cb = parseChartBase(str(vars.get("chartBase")));
            String skeleton = str(vars.get("chartSkeleton"));
            LocalDateTime solar = LocalDateTime.parse(cb.get("SOLAR"));
            int hourBranch = hourBranchOf(solar.getHour());
            int mingIdx = palaceBranch(skeleton, "命宫");

            // 金锁铁蛇关：戌上起子年顺至生年，年月日时逆推（卷二·论安命金锁铁蛇关）
            int guan = floorMod(10 + yearBranchOf(solar) - toInt(cb.get("LM"), 1) + toInt(cb.get("LD"), 1) - hourBranch, 12);
            String guanNote = "遇丑未宫病有救、辰戌宫死（卷二·论安命金锁铁蛇关）——本盘落" + ZHI[guan];

            String keQin = judgeKeQin(yearBranchOf(solar), hourBranch);
            // 卷三·论小儿命（c4 L58-61）：命宫庙旺（或大限未行而命坐庙旺）→ 灾少易养、父母无克；
            // 命坐恶杀缠陷弱地 → 灾多难养、刑克父母。
            Map<Integer, List<String>> at = mergePlacements(str(vars.get("mainStars")), str(vars.get("auxiliaryStars")));
            String xiaoErMing = judgeXiaoErMing(mingIdx, at);
            String childReading = "【小儿命专论】童限：一命二财三疾厄四妻五福六官禄，余年顺行（卷二·安童限诀）。"
                    + "金锁铁蛇关：" + guanNote + "。定男女竹萝三限（卷三·定小兒生時訣）："
                    + "以三方四正七杀破军俱作竹萝三限，若再加巨门暗曜凶星作三方四正定议，大小二限相遇作死限断。"
                    + "｜" + xiaoErMing + "｜" + keQin + "（P2 全纳，㉑）";

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("childReading", childReading);
            completeResult(client, job, result);
            log.info("✅ analyzeChildReading | {}", childReading);
        } catch (Exception e) {
            fail(job, client, "CHILD_READING_FAILED", e);
        }
    }

    // ==========================================================================
    // 节点 13：大限推演（analyzeMajorLimit）
    // 实现方法：程序（起限/顺逆推算）+ SWRL R8/R8b + 本体 fortune:GreatLimit/chart:PilotLimit。
    // 出处：卷二·安大限诀（阳男阴女从命前一宫起顺行、阴男阳女从命后一宫起逆行；局数即起限岁）。
    // ==========================================================================
    @JobWorker(type = "analyzeMajorLimit", autoComplete = false)
    public void handleAnalyzeMajorLimit(final ActivatedJob job, final JobClient client) {
        try {
            Map<String, Object> vars = job.getVariablesAsMap();
            Map<String, String> cb = parseChartBase(str(vars.get("chartBase")));
            String skeleton = str(vars.get("chartSkeleton"));
            LocalDateTime solar = LocalDateTime.parse(cb.get("SOLAR"));
            int yearGan = yearGanOf(solar);
            boolean male = "男".equals(cb.get("GENDER"));
            boolean yearYang = GAN_YY[yearGan] == 1;
            int bureau = bureauFromSkeleton(skeleton);
            int mingIdx = palaceBranch(skeleton, "命宫");

            boolean forward = (yearYang && male) || (!yearYang && !male);
            int startPalace = forward ? floorMod(mingIdx + 1, 12)     // 命前一宫 = 父母宫
                    : floorMod(mingIdx - 1, 12);                      // 命后一宫 = 兄弟宫
            int startAge = bureau;                                    // 局数即起限岁（水二局2岁起…）

            StringBuilder limits = new StringBuilder();
            for (int i = 0; i < 12; i++) {
                int palace = floorMod(startPalace + (forward ? i : -i), 12);
                int from = startAge + i * 10;
                limits.append("第").append(i + 1).append("限 ").append(from).append("-").append(from + 9)
                        .append("岁@").append(ZHI[palace]).append("宫").append(i == 0 ? "(童限/首限)" : "").append("；");
            }
            String direction = forward ? "顺行" : "逆行";
            String majorLimit = "起限岁=" + startAge + "（" + bureauName(bureau) + "）；"
                    + (yearYang ? "阳" : "阴") + (male ? "男" : "女") + " → " + direction + "；" + limits;
            String majorLimitReading = "大限宫星曜断十年吉凶：逐限以该宫主星、三方四正吉煞断之"
                    + "（卷二·安大限诀；大限十年祸福）；安限（童限/斗君/太岁杀禄）见 taiSuiBase。";

            // ---- 卷一·定杂局（运限格；原文 c2 L641-648）——依首限宫与本命/身宫，判可客观者 ----
            Map<Integer, List<String>> at = mergePlacements(str(vars.get("mainStars")),
                    str(vars.get("auxiliaryStars")), str(vars.get("miscStars")));
            String fourTransform = str(vars.get("fourTransform"));
            int shenIdx = palaceBranch(skeleton, "身宫");
            Set<Integer> kongIdx = new HashSet<>();
            for (Map.Entry<Integer, List<String>> e : at.entrySet()) {
                if (e.getValue().contains("截空") || e.getValue().contains("旬空")) {
                    kongIdx.add(e.getKey());
                }
            }
            Integer luIdx = branchIndexOf(at, "禄存");
            Integer maIdx = branchIndexOf(at, "天马");
            List<String> limitStars = at.getOrDefault(startPalace, List.of());
            int ageNow = LocalDate.now().getYear() - solar.getYear() + 1;
            boolean mingWeak = at.getOrDefault(mingIdx, List.of()).stream().noneMatch(s -> isMainStar(s));
            boolean shenWeak = shenIdx < 0 || at.getOrDefault(shenIdx, List.of()).stream().noneMatch(s -> isMainStar(s));
            String mingMain0 = at.getOrDefault(mingIdx, List.of()).stream().filter(s -> isMainStar(s)).findFirst().orElse(null);
            String limitMain = limitStars.stream().filter(s -> isMainStar(s)).findFirst().orElse(null);
            StringBuilder za = new StringBuilder();
            if ((mingWeak || shenWeak) && (limitStars.contains("禄存") || limitStars.contains("天马"))) {
                za.append("风云际会（身命虽弱、二限逢禄马）；");
            }
            if (limitStars.contains("七杀")
                    && ((luIdx != null && kongIdx.contains(luIdx)) || (maIdx != null && kongIdx.contains(maIdx)))) {
                za.append("禄衰马困（限逢七杀、禄马空亡）；");
            }
            if (ageNow > 40 && List.of("辰", "戌", "丑", "未").contains(ZHI[startPalace])) {
                za.append("衣锦还乡（四十后行墓运）；");
            }
            if (mingMain0 != null && isFallen(mingMain0, mingIdx) && limitMain != null && !isFallen(limitMain, startPalace)) {
                za.append("枯木逢春（命衰限好）；");
            }
            if (mingMain0 != null && limitMain != null && isFallen(limitMain, startPalace)) {
                za.append("吉凶相伴（命有主星、限衰不发）；");
            }
            // 定杂局余条（卷一·定杂局，原文 c2 L641-649）——原文确有明确定义，逐条落地（非「语义主观」）：
            //   锦上添花「谓限破恶星而行吉地是也」；步数无依「前限接后限连绵不分是也」；水上驾星「一年好一年不好是也」。
            List<String> limitSanFang = new ArrayList<>();
            for (int b : new int[]{startPalace, floorMod(startPalace + 4, 12), floorMod(startPalace + 8, 12), floorMod(startPalace + 6, 12)}) {
                limitSanFang.addAll(at.getOrDefault(b, List.of()));
            }
            boolean limitSha = limitStars.stream()
                    .anyMatch(s -> List.of("擎羊", "陀罗", "火星", "铃星", "地劫", "天空").contains(s));
            boolean limitJi = limitSanFang.stream()
                    .anyMatch(s -> List.of("禄存", "天魁", "天钺", "左辅", "右弼", "文昌", "文曲").contains(s))
                    || (startPalace >= 0 && limitStars.stream().anyMatch(s -> isMainStar(s) && !isFallen(s, startPalace)));
            if (limitSha && limitJi) {
                za.append("锦上添花（卷一·定杂局）「谓限破恶星而行吉地是也」（限宫带恶星而三方四正得吉）；");
            }

            // ---- 运限序列（卷一·定杂局「步数无依 / 水上驾星」；★落地口径〔释读〕见字段区「运限序列模块」）----
            // 大限十二限吉凶序列：逐限取该宫三方四正吉凶符号（+1吉/-1凶/0平）。
            int[] limitSigns = new int[12];
            StringBuilder limitSeqSb = new StringBuilder();
            for (int i = 0; i < 12; i++) {
                int palace = floorMod(startPalace + (forward ? i : -i), 12);
                limitSigns[i] = yunxianFortuneSign(at, palace, fourTransform);
                limitSeqSb.append(ZHI[palace]).append(fortuneGlyph(limitSigns[i])).append("；");
            }
            // 流年吉凶序列：自当前年起连续 6 个流年，逐年按流年支宫（太岁宫）＋流禄/流羊/流陀/流年四化取符号。
            int flowYear0 = LocalDate.now().getYear();
            int[] yearSigns = new int[6];
            StringBuilder yearSeqSb = new StringBuilder();
            for (int i = 0; i < yearSigns.length; i++) {
                int y = flowYear0 + i;
                int fg = ganOfYear(y);
                int fb = branchOfYear(y);
                yearSigns[i] = annualFortuneSign(at, fb, fg);
                yearSeqSb.append(y).append(ZHI[fb]).append(fortuneGlyph(yearSigns[i])).append("；");
            }
            boolean buShuWuYi = isBuShuWuYi(limitSigns);
            boolean shuiShangJiaXing = isShuiShangJiaXing(yearSigns);

            // 步数无依（卷一·定杂局「前限接后限连绵不分是也」）：大限十二限吉凶同号、全序无转折。
            if (buShuWuYi) {
                za.append("步数无依（卷一·定杂局）「前限接后限连绵不分是也」（大限十二限吉凶同号、连绵无转折：")
                        .append(fortuneWord(limitSigns[0])).append("）〔释读·运限序列〕；");
            } else {
                za.append("步数无依〔运限序列·本盘前后限有转折，未成〕；");
            }
            // 水上驾星（卷一·定杂局「一年好一年不好是也」）：流年吉凶逐年交替。
            if (shuiShangJiaXing) {
                za.append("水上驾星（卷一·定杂局）「一年好一年不好是也」（流年吉凶逐年交替）〔释读·运限序列〕；");
            } else {
                za.append("水上驾星〔运限序列·本盘流年未成逐年交替，未成〕；");
            }
            String miscPattern = "卷一·定杂局（运限格）：" + za;
            String yunxianSequence = "运限序列〔释读·非逐字原文〕：大限吉凶序列=" + limitSeqSb
                    + "｜流年吉凶序列（" + flowYear0 + "起连续6年）=" + yearSeqSb
                    + "｜步数无依=" + (buShuWuYi ? "成" : "未成") + "，水上驾星=" + (shuiShangJiaXing ? "成" : "未成")
                    + "（吉/凶/平按三方四正吉星凶星＋主星庙陷＋生年四化客观复算）";
            // 卷三·论命先贫后富（c4 L72-74）：由大限吉凶序列判「先贫后富 / 先富后贫」
            String xianPinHouFu = judgeXianPinHouFu(limitSigns);

            // ---- 运限重算：当前大限实际引动星曜（大限宫干四化 / 大限十二神 / 大限吉凶符号）----
            int birthYearBranch = yearBranchOf(solar);
            int dxPalace = daXianPalace(ageNow, startAge, startPalace, forward);
            StringBuilder dxRecalc = new StringBuilder();
            if (dxPalace < 0) {
                dxRecalc.append("尚未起限（虚龄").append(ageNow).append("<起限岁").append(startAge).append("，童限依安童限诀）");
            } else {
                int dxGan = palaceStemIndex(dxPalace, yearGan);
                String[] dxHua = SIHUA[dxGan];
                String[] dxHuaName = {"化禄", "化权", "化科", "化忌"};
                dxRecalc.append("第").append((ageNow - startAge) / 10 + 1).append("大限（").append(ZHI[dxPalace])
                        .append("宫）｜大限宫干=").append(GAN[dxGan]).append("｜大限四化（依大限宫干）：");
                for (int i = 0; i < 4; i++) {
                    Integer b = branchIndexOf(at, dxHua[i]);
                    dxRecalc.append(dxHua[i]).append(dxHuaName[i]).append("→").append(b == null ? "—" : ZHI[b]).append("；");
                }
                int dxLu = LUCUN[dxGan];
                boolean dxForward = (GAN_YY[dxGan] == 1 && male) || (GAN_YY[dxGan] == 0 && !male);
                dxRecalc.append("｜大限十二神（起禄存于").append(ZHI[dxLu]).append("，").append(dxForward ? "顺" : "逆").append("）：");
                for (int i = 0; i < 12; i++) {
                    dxRecalc.append(DEITY_12[i]).append("@").append(ZHI[floorMod(dxLu + (dxForward ? i : -i), 12)]).append("；");
                }
                dxRecalc.append("｜大限吉凶符号=").append(fortuneGlyph(yunxianFortuneSign(at, dxPalace, fourTransform)));
            }
            // 小限重算 + 大小二限相遇（卷二·安小限诀 + 定男女竹萝三限）
            int xxPalace = xiaoXianPalace(ageNow, birthYearBranch, male);
            boolean twoLimitsMeet = dxPalace >= 0 && dxPalace == xxPalace;
            dxRecalc.append("｜小限（").append(ageNow).append("岁）=").append(ZHI[xxPalace]).append("宫；")
                    .append("大小二限相遇=").append(twoLimitsMeet ? "是（大限宫=小限宫=" + ZHI[dxPalace] + "）" : "否");
            String majorLimitRecalc = dxRecalc.toString();

            // ---- 卷三·论二限太岁吉凶（c4 L77-78）：大限/小限/太岁各自独守及相逢、相冲吉凶 ----
            int tsIdx = branchOfYear(flowYear0);
            int dxSign = dxPalace < 0 ? 0 : yunxianFortuneSign(at, dxPalace, fourTransform);
            int xxSign = yunxianFortuneSign(at, xxPalace, fourTransform);
            int tsSign = annualFortuneSign(at, tsIdx, ganOfYear(flowYear0));
            String twoLimitsTaiSui = judgeTwoLimitsTaiSui(at, dxPalace, xxPalace, tsIdx, dxSign, xxSign, tsSign);

            // ---- 竹萝三限（卷二·定男女竹萝三限）：「三方四正七杀破军俱作竹萝三限，若再加巨暗凶星便作三方四正定议，
            //      若大小二限相遇作死限断」——三方四正七杀破军俱见 → 竹萝；命宫／当前大限宫分判 ----
            boolean zhuLuoMing = isZhuLuoSanXian(mingIdx, at);
            boolean zhuLuoDx = dxPalace >= 0 && isZhuLuoSanXian(dxPalace, at);
            StringBuilder zl = new StringBuilder("竹萝三限（卷二·定男女竹萝三限「三方四正七杀破军俱作竹萝三限」）：");
            if (zhuLuoMing) {
                zl.append("命宫三方四正七杀破军俱见→命宫竹萝三限");
                if (hasJuMenOrSha(mingIdx, at)) {
                    zl.append("（加巨暗/凶星，作三方四正定议）");
                }
                zl.append("；");
            }
            if (zhuLuoDx) {
                zl.append("当前大限宫（").append(ZHI[dxPalace]).append("）三方四正七杀破军俱见→大限竹萝三限");
                if (hasJuMenOrSha(dxPalace, at)) {
                    zl.append("（加巨暗/凶星，作三方四正定议）");
                }
                zl.append("；");
            }
            if (!zhuLuoMing && !zhuLuoDx) {
                zl.append("本盘命宫／当前大限宫三方四正未俱见七杀破军（未成）；");
            }
            zl.append(twoLimitsMeet
                    ? ((zhuLuoDx || zhuLuoMing) ? "大小二限相遇且见竹萝三限→作死限断〔释读·卷二明文〕"
                    : "大小二限相遇（然未俱见七杀破军，不作死限断）")
                    : "大小二限未相遇（不作死限断）");
            String zhuLuo = zl.toString();
            majorLimitReading = majorLimitReading + "｜" + zhuLuo
                    + "｜" + judgeDaXianShiNian(dxPalace, at, mingIdx);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("majorLimit", majorLimit);
            result.put("majorLimitReading", majorLimitReading);
            result.put("miscPattern", miscPattern);
            result.put("yunxianSequence", yunxianSequence);
            result.put("xianPinHouFu", xianPinHouFu);
            result.put("twoLimitsTaiSui", twoLimitsTaiSui);
            result.put("majorLimitRecalc", majorLimitRecalc);
            completeResult(client, job, result);
            log.info("✅ analyzeMajorLimit | {} | 杂局={}", majorLimit, za);
        } catch (Exception e) {
            fail(job, client, "ANALYZE_MAJOR_LIMIT_FAILED", e);
        }
    }

    // ==========================================================================
    // 节点 14：流年小限推演（analyzeAnnualFortune）
    // 实现方法：程序查表（按流年干支重算）+ SWRL R15/R24/R25 + 本体 fortune:AnnualFortune/SmallLimit/RecalculatedDeity。
    // 出处：卷二·安小限诀/安流禄流羊流陀诀/安十二宫太岁杀禄诀/安斗君诀。
    // 粒度 granularity∈{年,月,日}（默认年），本期至年、月/日 预留 R1 ㉓。
    // ==========================================================================
    @JobWorker(type = "analyzeAnnualFortune", autoComplete = false)
    public void handleAnalyzeAnnualFortune(final ActivatedJob job, final JobClient client) {
        try {
            Map<String, Object> vars = job.getVariablesAsMap();
            Map<String, String> cb = parseChartBase(str(vars.get("chartBase")));
            String skeleton = str(vars.get("chartSkeleton"));
            LocalDateTime solar = LocalDateTime.parse(cb.get("SOLAR"));
            boolean male = "男".equals(cb.get("GENDER"));
            int birthYearBranch = yearBranchOf(solar);
            Map<Integer, List<String>> at = mergePlacements(str(vars.get("mainStars")),
                    str(vars.get("auxiliaryStars")), str(vars.get("miscStars")));
            String granularity = orDefault(cb.get("GRAN"), "年");
            boolean wantMonth = "月".equals(granularity) || "日".equals(granularity);
            boolean wantDay = "日".equals(granularity);
            int granularityWarn = (!"年".equals(granularity) && !wantMonth) ? 1 : 0; // 未知粒度回退年级并标注（㉓）

            // 流年取当前年；按流年干支重算流曜（不得沿用出生年太岁流曜）
            int flowYear = LocalDate.now().getYear();
            int flowGan = ganOfYear(flowYear);
            int flowBranch = branchOfYear(flowYear);
            int flowLu = LUCUN[flowGan];
            StringBuilder recalc = new StringBuilder();
            recalc.append("流年").append(GAN[flowGan]).append(ZHI[flowBranch])
                    .append("（").append(flowYear).append("）｜流禄=").append(ZHI[flowLu])
                    .append(" 流羊=").append(ZHI[floorMod(flowLu + 1, 12)])
                    .append(" 流陀=").append(ZHI[floorMod(flowLu - 1, 12)]);
            boolean flowForward = (GAN_YY[flowGan] == 1 && male) || (GAN_YY[flowGan] == 0 && !male);
            recalc.append("｜流年十二神（起禄存，").append(flowForward ? "顺" : "逆").append("）：");
            for (int i = 0; i < 12; i++) {
                recalc.append(DEITY_12[i]).append("@").append(ZHI[floorMod(flowLu + (flowForward ? i : -i), 12)]).append("；");
            }
            int douJunIdx = douJun(flowBranch, toInt(cb.get("LM"), 1), hourBranchOf(solar.getHour()));
            recalc.append("｜斗君=").append(ZHI[douJunIdx]);
            // 流年四化（依流年干；卷二·安禄权科忌四星变化诀）——流年实际引动星曜（流禄/权/科/忌落宫）
            String[] fHua = SIHUA[flowGan];
            String[] fHuaName = {"化禄", "化权", "化科", "化忌"};
            recalc.append("｜流年四化（依流年干").append(GAN[flowGan]).append("）：");
            for (int i = 0; i < 4; i++) {
                Integer b = branchIndexOf(at, fHua[i]);
                recalc.append(fHua[i]).append(fHuaName[i]).append("→").append(b == null ? "—" : ZHI[b]).append("；");
            }

            // ---- 流月（卷二·安斗君诀「即月将星是也」：于流年太岁宫起正月逆至本生月，又从本生月起子顺数至本生时安斗君）
            //      斗君即流月正月命宫，逐月顺行一宫 → 目标流月命宫 ----
            int targetMonth = Math.min(12, Math.max(1, toInt(str(vars.get("targetMonth")), LocalDate.now().getMonthValue())));
            int flowMonthPl = flowMonthPalace(douJunIdx, targetMonth);
            StringBuilder monthGrid = new StringBuilder();
            for (int m = 1; m <= 12; m++) {
                monthGrid.append(m).append("月@").append(ZHI[flowMonthPalace(douJunIdx, m)]).append("；");
            }
            String flowMonth = "流月（卷二·安斗君诀「即月将星是也」：斗君为正月命宫、逐月顺行）：斗君=" + ZHI[douJunIdx]
                    + "｜目标流月=" + targetMonth + "月→命宫" + ZHI[flowMonthPl]
                    + "（吉凶符号=" + fortuneGlyph(annualFortuneSign(at, flowMonthPl, flowGan)) + "）｜十二月命宫：" + monthGrid;
            // ---- 流日〔外部通行法·非《全書》原文：《全書》仅言「日值恶杀」，未立流日起例〕：流月命宫起初一、逐日顺行一宫 ----
            int targetDay = Math.min(30, Math.max(1, toInt(str(vars.get("targetDay")), LocalDate.now().getDayOfMonth())));
            int flowDayPl = flowDayPalace(flowMonthPl, targetDay);
            String flowDay = "流日〔外部通行法·非《全書》原文：流月命宫起初一、逐日顺行一宫；《全書》仅言「日值恶杀」未立起例〕："
                    + "目标流日=" + targetDay + "日→命宫" + ZHI[flowDayPl]
                    + "（吉凶符号=" + fortuneGlyph(annualFortuneSign(at, flowDayPl, flowGan)) + "）";

            // 小限：按生年支三合定起宫，男顺女逆（卷二·安小限诀）
            int smallStart = SMALL_LIMIT_START[birthYearBranch];
            int age = flowYear - solar.getYear() + 1;
            int smallLimit = floorMod(smallStart + (male ? (age - 1) : -(age - 1)), 12);

            String annualChart = "推演粒度=" + granularity
                    + (granularityWarn == 1 ? "（未知粒度回退年级并标注，㉓）" : (wantMonth ? "（㉓ 月/日 粒度已支持）" : ""))
                    + "｜流年宫=" + ZHI[flowBranch] + "；小限（" + age + "岁）=" + ZHI[smallLimit]
                    + (wantMonth ? "；流月命宫=" + ZHI[flowMonthPl] + (wantDay ? "；流日命宫=" + ZHI[flowDayPl] : "") : "");
            String annualReading = "小限与太岁叠加断吉凶；流年太岁吉凶星杀、流曜引动；大小限过十二支所忌"
                    + "（卷二·安小限诀/安流禄流羊流陀诀）；不得沿用出生年太岁流曜。"
                    + "｜粒度㉓：年（流年干支重算流曜）／月（安斗君诀起流月）／日〔外部通行法·非《全書》原文〕。";

            // 卷三·论太岁小限星辰庙陷遇十二宫中吉凶（c4 L103-172）：太岁并小限所到之宫支 → 三段查表 + 按本命生干择段
            String[] tsxx = TAISUI_XIAOXIAN[flowBranch];
            int birthGanTsxx = ganOfYear(solar.getYear());
            String taisuiXiaoxian = "太岁并小限到" + ZHI[flowBranch] + "宫（卷三·论太岁小限星辰庙陷遇十二宫中吉凶）："
                    + "入庙化吉—" + tsxx[0] + "；不入庙化凶—" + tsxx[1] + "；太岁所值吉凶星—" + tsxx[2]
                    + "｜" + taisuiXiaoxianGanHit(birthGanTsxx, flowBranch)
                    + "〔释读：两段各按生年干条件择取，「依此判断其人行年灾患应如神」〕";
            // 卷三·论大小限星辰过十二宫遇十二支所忌诀（c4 L92-101）：按生年支查所忌岁限/星
            String limitTaboo = "大小限过十二支所忌（卷三·按生年支）：" + LIMIT_TABOO[birthYearBranch];

            // 重大事件·逐流年回推（subjectTraits 结构化事件表）〔外部通行法·释读·非《全書》原文〕
            String majorEventBacktrace = majorEventBacktrace(str(vars.get("subjectTraits")), at);
            if (majorEventBacktrace == null) {
                majorEventBacktrace = "无结构化事件（subjectTraits 未含「年份+事件」；如需逐流年回推，例：\"2010年结婚；2015年丧父；2018年升职\"）。";
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("annualChart", annualChart);
            result.put("annualReading", annualReading);
            result.put("annualRecomputedStars", recalc.toString());
            result.put("flowMonth", flowMonth);
            result.put("flowDay", flowDay);
            result.put("taisuiXiaoxian", taisuiXiaoxian);
            result.put("limitTaboo", limitTaboo);
            result.put("majorEventBacktrace", majorEventBacktrace);
            completeResult(client, job, result);
            log.info("✅ analyzeAnnualFortune | {}", annualChart);
        } catch (Exception e) {
            fail(job, client, "ANALYZE_ANNUAL_FORTUNE_FAILED", e);
        }
    }

    // ==========================================================================
    // 节点 15：叠宫断法与凶格引动（analyzePalaceOverlay）
    // 实现方法：程序（四盘叠合·体用编排）+ SWRL R16/R17 + 本体 fortune:OverlayReading/TriggeredPattern/LimitSong。
    // 出处：I·51（四盘叠合〔外部通行法·非《全書》原文：本命为体、大限/流年/流月/流日为用〕）；羊陀迭并/七杀重逢；行限宫歌（H·48，分南北斗）。
    // ==========================================================================
    @JobWorker(type = "analyzePalaceOverlay", autoComplete = false)
    public void handleAnalyzePalaceOverlay(final ActivatedJob job, final JobClient client) {
        try {
            Map<String, Object> vars = job.getVariablesAsMap();
            Map<Integer, List<String>> at = mergePlacements(str(vars.get("mainStars")), str(vars.get("auxiliaryStars")));
            String skeleton = str(vars.get("chartSkeleton"));
            int mingIdx = palaceBranch(skeleton, "命宫");
            Map<String, String> cb = parseChartBase(str(vars.get("chartBase")));
            boolean maleOverlay = "男".equals(cb.get("GENDER"));
            LocalDateTime solarOverlay = LocalDateTime.parse(cb.get("SOLAR"));
            int yearGanOverlay = yearGanOf(solarOverlay);
            int hourBranchOverlay = hourBranchOf(solarOverlay.getHour());

            // ---- 四盘定位（本命为体；大限/流年/流月/流日为用）----
            int bureauOverlay = bureauFromSkeleton(skeleton);
            boolean yearYangOverlay = GAN_YY[yearGanOverlay] == 1;
            boolean forwardOverlay = (yearYangOverlay && maleOverlay) || (!yearYangOverlay && !maleOverlay);
            int startPalaceOverlay = forwardOverlay ? floorMod(mingIdx + 1, 12) : floorMod(mingIdx - 1, 12);
            int ageNowOverlay = LocalDate.now().getYear() - solarOverlay.getYear() + 1;
            int dxIdx = daXianPalace(ageNowOverlay, bureauOverlay, startPalaceOverlay, forwardOverlay);
            int flowYearNow = LocalDate.now().getYear();
            int flowYearIdx = branchOfYear(flowYearNow);
            int flowMonthIdx = -1;
            int flowDayIdx = -1;
            int targetMonthOverlay = toInt(str(vars.get("targetMonth")), 0);
            int targetDayOverlay = toInt(str(vars.get("targetDay")), 0);
            if (targetMonthOverlay >= 1 && targetMonthOverlay <= 12) {
                // 斗君依安斗君诀（流年太岁宫起正月逆至本生月，再从本生月起子顺数至本生时）——与节点 14 同参
                int djOverlay = douJun(flowYearIdx, toInt(cb.get("LM"), 1), hourBranchOverlay);
                flowMonthIdx = flowMonthPalace(djOverlay, targetMonthOverlay);
                if (targetDayOverlay >= 1) {
                    flowDayIdx = flowDayPalace(flowMonthIdx, targetDayOverlay);
                }
            }

            String overlayReading = overlayStack(mingIdx, dxIdx, flowYearIdx, flowMonthIdx, flowDayIdx, at)
                    + "（I·51 四盘叠合；大小二限/叠宫断法 H·48）";

            // 凶格引动：羊陀迭并 / 七杀重逢（卷三·c4 L87-91——原文「本命羊陀/七杀 + 流年流羊流陀冲会」）
            int flowGanOverlay = yearGanOf(LocalDateTime.of(flowYearNow, 1, 1, 0, 0));
            boolean yangTuo = judgeYangTuoDieBing(mingIdx, at, flowGanOverlay);
            boolean qiShaRe = judgeQiShaReFeng(mingIdx, at, flowGanOverlay);
            String evilPatternTrigger;
            if (yangTuo || qiShaRe) {
                evilPatternTrigger = (yangTuo ? "羊陀迭并（本命羊/陀+流年流羊流陀冲会）；" : "")
                        + (qiShaRe ? "七杀重逢（本命七杀守照+流年流羊流陀冲照）；" : "")
                        + "凶格引动之年〔客观·原文流年迭并·释读〕";
            } else {
                evilPatternTrigger = "无重大凶格引动";
            }

            // 卷三·论立命行限宫歌（c4 L102-104，按命宫纳音五行遇八卦方位）＋论行限分南北斗（c4 L79-82）
            // ＋论流年太岁吉凶星杀（c4 L83-84）
            String limitPalaceSong = XINGXIAN_GE + "｜" + nanBeiDouSong(yearGanOverlay, maleOverlay)
                    + "｜" + judgeLiuNianTaiSui(mingIdx, flowYearIdx, at)
                    + "｜叠宫断法（H·48）：吉曜临限则吉、煞曜临限则凶。";

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("overlayReading", overlayReading);
            result.put("evilPatternTrigger", evilPatternTrigger);
            result.put("limitPalaceSong", limitPalaceSong);
            completeResult(client, job, result);
            log.info("✅ analyzePalaceOverlay | {}", evilPatternTrigger);
        } catch (Exception e) {
            fail(job, client, "ANALYZE_PALACE_OVERLAY_FAILED", e);
        }
    }

    // ==========================================================================
    // 节点 16：综合结论汇总（aggregateReading）
    // 实现方法：程序编排（㉕ 命书体＋JSON Schema）+ 本体 reading:ReadingConclusion/StructuredOutput/ChartGraphic + SWRL R18~R21。
    // 出处：卷一·诸格论；K·65 命盘图形化规范；H·49 阴骘延寿劝诫；㉖ 告警综合判据由程序编排、SWRL 落最终标志、不设数值阈值。
    // ==========================================================================
    @JobWorker(type = "aggregateReading", autoComplete = false)
    public void handleAggregateReading(final ActivatedJob job, final JobClient client) {
        try {
            Map<String, Object> vars = job.getVariablesAsMap();
            String skeleton = str(vars.get("chartSkeleton"));
            Map<Integer, List<String>> at = mergePlacements(str(vars.get("mainStars")), str(vars.get("auxiliaryStars")));
            int mingIdx = palaceBranch(skeleton, "命宫");
            List<String> mingStars = at.getOrDefault(mingIdx, List.of());

            StringBuilder reading = new StringBuilder();
            reading.append("【命书式结论】\n");
            reading.append("一、命盘：" ).append(skeleton).append('\n');
            reading.append("二、本命：").append(firstLine(str(vars.get("natalReading")))).append('\n');
            appendLine(reading, "三、女命专论", str(vars.get("femaleReading")));
            appendLine(reading, "四、小儿命", str(vars.get("childReading")));
            appendLine(reading, "五、大限", str(vars.get("majorLimitReading")));
            appendLine(reading, "六、流年", str(vars.get("annualReading")));
            appendLine(reading, "七、叠宫", str(vars.get("overlayReading")));
            reading.append("八、阴骘延寿劝诫（H·49）：天道福善祸淫，命虽前定而修德可回天，宜积阴骘以延寿（卷一劝善语）。\n");

            // 告警综合判据（程序编排，SWRL 落最终标志；不设数值阈值 ㉖）：仅三类——大凶/寿元关/重大疾病
            int shaCount = 0;
            for (String s : mingStars) {
                if (Arrays.asList("擎羊", "陀罗", "火星", "铃星", "地劫", "天空").contains(s)) {
                    shaCount++;
                }
            }
            boolean alertTriggered = shaCount >= 3;
            reading.append("九、告警判据：命宫煞曜数=").append(shaCount).append(alertTriggered ? "（触发外发）" : "（不触发）").append('\n');

            // 结构化输出（JSON Schema 形态，K·65 命盘图形化规范由此产出）
            Map<String, Object> structured = new LinkedHashMap<>();
            structured.put("skeleton", skeleton);
            structured.put("pattern", str(vars.get("patternResult")));
            structured.put("grade", str(vars.get("patternGrade")));
            structured.put("alertTriggered", alertTriggered);
            structured.put("chartGraphic", "十二宫方形布局：命宫定盘位，逆布十二宫，主星/辅煞/杂曜/四化逐宫标注（K·65）");

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("reading", reading.toString());
            result.put("structuredOutput", structured.toString());
            result.put("alertTriggered", alertTriggered);
            completeResult(client, job, result);
            log.info("✅ aggregateReading | alertTriggered={}", alertTriggered);
        } catch (Exception e) {
            fail(job, client, "AGGREGATE_READING_FAILED", e);
        }
    }

    // ==========================================================================
    // 节点 A：外发告警消息（sendAlert）
    // 实现方法：外部调用（RabbitMqHandler.send）+ SWRL R21；外发失败仅记日志、不阻断主流程。
    // 出处：exchange ziweidoushu.alert.exchange(direct) / routing-key ziweidoushu.alert / queue ziweidoushuQueue。
    // ==========================================================================
    @JobWorker(type = "sendAlert", autoComplete = false)
    public void handleSendAlert(final ActivatedJob job, final JobClient client) {
        RabbitMqHandler mq = null;
        String status;
        try {
            Map<String, Object> vars = job.getVariablesAsMap();
            boolean triggered = Boolean.TRUE.equals(vars.get("alertTriggered"))
                    || "true".equalsIgnoreCase(str(vars.get("alertTriggered")));
            if (triggered) {
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("instanceKey", job.getProcessInstanceKey());
                payload.put("reading", vars.get("reading"));
                payload.put("chartBase", vars.get("chartBase"));
                payload.put("alertType", "大凶/寿元关/重大疾病（三类之一）");
                payload.put("timestamp", java.time.Instant.now().toString());
                mq = new RabbitMqHandler();
                mq.send(alertExchange, alertRoutingKey, payload);
                status = "SENT";
            } else {
                status = "SKIPPED_NO_ALERT";
            }
        } catch (Exception e) {
            // 外发失败不阻断主流程
            log.error("⚠️ sendAlert | 告警外发失败，仅记日志不阻断主流程：{}", e.getMessage());
            status = "FAILED_LOGGED";
        } finally {
            if (mq != null) {
                try {
                    mq.destroy();
                } catch (Exception ignore) {
                    // noop
                }
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("messageStatus", status);
        completeResult(client, job, result);
        log.info("✅ sendAlert | messageStatus={}", status);
    }

    // ==========================================================================
    // 末尾节点 18：命书详解 + 白话解读（interpretResult）
    // 将节点 16 汇总的命书（reading）送 MaaS 大模型：先按本命逐环节详解**推断依据**并引《紫微斗數全書》
    // 篇章印证（原文仅作印证、勿整段照抄无关原文），再出一段白话文转述（把前述文言解读翻成白话）。
    // 大模型未装配/不可用/失败时双键（aiInterpretation/aiInterpretationPlain）降级为空串（流程不阻断）。
    // ==========================================================================
    @JobWorker(type = "interpretResult", autoComplete = false)
    public void handleInterpretResult(final ActivatedJob job, final JobClient client) {
        try {
            Map<String, Object> vars = job.getVariablesAsMap();
            String reading = orDefault(str(vars.get("reading")), "");

            String interpretation = "";
            String interpretationPlain = "";
            if (llm != null && llm.isAvailable() && !reading.isBlank()) {
                String user = buildInterpretUserPrompt(vars, reading);
                String r = llm.chat(INTERPRET_SYSTEM_PROMPT, user);
                if (r != null && !r.isBlank()) {
                    interpretation = r;
                    String plainUser = buildInterpretPlainUserPrompt(reading, interpretation);
                    String pl = llm.chat(INTERPRET_PLAIN_SYSTEM_PROMPT, plainUser);
                    if (pl != null && !pl.isBlank()) {
                        interpretationPlain = pl;
                    } else {
                        log.warn("⚠️ interpretResult | 白话解读生成失败/为空，降级为空串（流程不阻断）");
                    }
                } else {
                    log.warn("⚠️ interpretResult | 大模型不可用/失败，降级为空串（流程不阻断）");
                }
            } else {
                log.info("⏭️ interpretResult | 大模型未装配/不可用，跳过命书详解（流程不阻断）");
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("aiInterpretation", interpretation);
            result.put("aiInterpretationPlain", interpretationPlain);
            completeResult(client, job, result);
            log.info("✅ interpretResult | 命书详解（{} 字）· 白话解读（{} 字）",
                    interpretation.length(), interpretationPlain.length());
        } catch (Exception e) {
            fail(job, client, "INTERPRET_RESULT_FAILED", e);
        }
    }

    /** 组装「命书详解」用户提示：以命书为据、附关键中间变量，供大模型逐环节按本命详解依据并引《全書》印证。 */
    private String buildInterpretUserPrompt(Map<String, Object> vars, String reading) {
        StringBuilder sb = new StringBuilder();
        sb.append("【紫微斗数命书（节点 16 汇总·已按本命推导的口径结论）】\n").append(reading).append('\n');
        sb.append("【关键中间变量（供逐环节解析）】\n");
        appendLine(sb, "命盘骨架", vars.get("chartSkeleton"));
        appendLine(sb, "星曜化气与诸星所主", vars.get("starNature"));
        appendLine(sb, "四化盘", vars.get("fourTransform"));
        appendLine(sb, "庙旺利陷表", vars.get("brightnessTable"));
        appendLine(sb, "格局判定", vars.get("patternResult"));
        appendLine(sb, "命格高低", vars.get("patternGrade"));
        appendLine(sb, "大限断语", vars.get("majorLimitReading"));
        appendLine(sb, "流年断语", vars.get("annualReading"));
        appendLine(sb, "叠宫断法", vars.get("overlayReading"));
        sb.append("\n请据此按紫微斗数命书体例作系统详解：先按命盘十二宫/十四主星与辅佐煞忌落宫及庙旺利陷/"
                + "化气所主/四化飞星/格局/三方四正夹拱/大限流年逐环节说明**推断依据**，"
                + "再引《紫微斗數全書》（卷一·諸星問答論；卷三·談星要論、諸星庙陷 等）相应篇章的论断加以印证；"
                + "引文只作印证、勿整段照抄与本命无关之原文；不得编造出处。");
        return sb.toString();
    }

    /** 组装「白话解读」用户提示：以命书与已生成的命书详解为据，供大模型转写为通俗白话。 */
    private String buildInterpretPlainUserPrompt(String reading, String interpretation) {
        StringBuilder sb = new StringBuilder();
        sb.append("【紫微斗数命书（节点 16 汇总）】\n").append(reading).append('\n');
        sb.append("【命书详解（古典，供你改写为白话）】\n").append(interpretation).append('\n');
        sb.append("\n请据此用通俗白话文重新讲一遍：分点讲清命盘格局、性格心性、事业/财运/婚姻感情/六亲/健康/大限流年吉凶"
                + "各是什么含义、对命主意味着什么；对命书中已定的每个主星/辅星/煞星、四化（化禄/化权/化科/化忌）、"
                + "庙旺利陷、格局、十二宫、三方四正、大限流年等术语，逐项用白话说明它是什么、对命主意味着什么、有何影响；"
                + "遇术语随即浅释，避免只堆名词；只作白话转述，不得编造出处。");
        return sb.toString();
    }

    // ==========================================================================
    // 节点 17：结果落库与返回（persistAndReturn）
    // 实现方法：程序（落库，经 OBDA addComponent）+ 本体 reading:CaseRecord；㉗ 脱敏、仅存必要字段。
    // 出处：《应用架构设计》§5 节点 17。
    // ==========================================================================
    @JobWorker(type = "persistAndReturn", autoComplete = false)
    public void handlePersistAndReturn(final ActivatedJob job, final JobClient client) {
        try {
            Map<String, Object> vars = job.getVariablesAsMap();
            Map<String, String> cb = parseChartBase(str(vars.get("chartBase")));
            String skeleton = orDefault(str(vars.get("chartSkeleton")), "");
            String reading = orDefault(str(vars.get("reading")), "");

            // 脱敏：仅存必要字段（不落可识别身份信息）——生辰仅存「年」粒度、不存出生地明址（㉗）
            String solar = cb.getOrDefault("SOLAR", "");
            String yearOnly = solar.length() >= 4 ? solar.substring(0, 4) : solar;
            String caseRecord = "命例（脱敏）：生年≈" + yearOnly + "｜性别=" + cb.getOrDefault("GENDER", "")
                    + "｜五行局/命宫：" + firstLine(skeleton);
            try {
                if (backendService != null && backendService.getObdaHandler() != null) {
                    OBDAHandler obda = backendService.getObdaHandler();
                    obda.addComponent("case_record",
                            List.of("birth_year", "gender", "chart_summary", "reading"),
                            List.of(yearOnly, cb.getOrDefault("GENDER", ""), skeleton, reading));
                    log.info("✅ persistAndReturn | 命例已入库 case_record（脱敏）");
                } else {
                    log.warn("⚠️ persistAndReturn | OBDA 未就绪（第 11 步落地），落库跳过，仅返回 caseRecord");
                }
            } catch (Throwable e) {
                log.warn("⚠️ persistAndReturn | 落库未就绪（第 11 步落地），落库跳过，仅返回 caseRecord：{}", e.getMessage());
            }

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("status", "COMPLETED");
            response.put("chart", skeleton);
            response.put("reading", reading);
            response.put("messageStatus", str(vars.get("messageStatus")));

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("caseRecord", caseRecord);
            result.put("response", response.toString());
            completeResult(client, job, result);
            log.info("✅ persistAndReturn | {}", caseRecord);
        } catch (Exception e) {
            fail(job, client, "PERSIST_RETURN_FAILED", e);
        }
    }

    // ==========================================================================
    // 十四主星「化气·所主」（卷一·諸星問答論）。{星, 化气, 所主}
    // ==========================================================================
    private static final String[][] STAR_CHARACTER = {
            {"紫微", "尊", "帝座，主尊贵、统御，宜居子午，喜左右夹辅"},
            {"天机", "善", "主智慧、机变，善星，宜庙旺"},
            {"太阳", "贵", "主贵、显达，司昼，男命、父星"},
            {"武曲", "财", "主财帛、刚毅，金性，司财"},
            {"天同", "福", "主福泽、安逸，水性，宜庙旺"},
            {"廉贞", "囚", "主囚、是非，亦主桃花与权术，火性"},
            {"天府", "令", "主令、库藏，库星，主富贵稳重"},
            {"太阴", "富", "主富、清洁，司夜，女命、母星"},
            {"贪狼", "桃花", "主桃花、欲，亦主才艺、寿"},
            {"巨门", "暗", "主暗、口舌、是非，水性"},
            {"天相", "印", "主印、辅佐、衣禄"},
            {"天梁", "荫", "主荫、寿、清高，土性"},
            {"七杀", "权", "主权、刚勇，金性，肃杀"},
            {"破军", "耗", "主耗、变动、开创，水性"}};

    /**
     * 卷一·形性賦（c2 L63-95）：星曜形性（相貌体态 / 性情心性）原文断语——节点 8 与「化气·諸星問答論」**并出分列**。
     * 「形」主相貌体态、「性」主性情心性；与「化气」（十四主星化气）严格分列，不混用。
     * 原文逐字摘录（繁体从原文），括注为原文他句补充。
     */
    private static final String[][] STAR_XINGXING = {
            {"紫微", "帝座，生为厚重之容；肥满（紫微肥满）"},
            {"天府", "尊星，也作纯和之体；精神（天府精神）"},
            {"太阳", "金乌，圆满（金乌圆满）"},
            {"太阴", "玉兔，清奇（玉兔清奇）"},
            {"天机", "为不长不短之姿，情怀好善"},
            {"武曲", "乃至要至紧之操，心性果决"},
            {"天同", "肥满，目秀清奇（同陀忌则肥满而目眇）"},
            {"廉贞", "眉宽，口阔面横，为人性暴，好忿好争"},
            {"贪狼", "善恶之星，入庙必应长耸，出垣必定顽嚣（同武曲形小声高而量大）"},
            {"巨门", "是非之曜，在庙敦厚温良"},
            {"天相", "精神（天相精神）"},
            {"天梁", "稳重，心事玉洁冰清"},
            {"七杀", "如子路暴虎冯河，目太凶狠"},
            {"破军", "不仁，背重眉宽，行坐腰斜，奸诈好行惊险"},
            {"文昌", "俊雅，眉清目秀"},
            {"文曲", "磊落，口舌便佞，在庙定生异痣，失陷必有斑痕"},
            {"左辅", "温良规模，端庄高士"},
            {"右弼", "温良规模，端庄高士"},
            {"天魁", "具足威仪，重合三台则十全模范"},
            {"天钺", "具足威仪，重合三台则十全模范"},
            {"擎羊", "形丑貌粗，有矫诈体态"},
            {"陀罗", "形丑貌粗，有矫诈体态"},
            {"火星", "似豫让吞炭装哑，暗狼声沉"},
            {"铃星", "似豫让吞炭装哑，暗狼声沉"},
            {"禄存", "性貌如春和蔼（禄存之情德）"}};

    // ==========================================================================
    // 卷二·十二宫各星吉凶诀（原文 c3 L458-1282，共 813 行）——「原文语料」落地与引用。
    // 内容：十二宫（命宫/兄弟/妻妾/子女/财帛/疾厄/迁移/奴仆/官禄/田宅/福德/父母）各宫所载
    //       「某星（论/入男命吉凶诀/入女命吉凶诀/入限吉凶诀）」断语判词。
    // 口径：此类为**断语类判词**，原文为准（《全書》第一权威，不改写、不臆断）；程序按「宫 × 主星」
    //       取原文首句作印证并回到原行号（c3 Lxxx），全文见资源 {@code /ziweidoushu/palace_classic.tsv}。
    // 出处：卷二·一命宫～十二父母（c0 目录 2.2–2.13）。
    // ==========================================================================
    private static volatile Map<String, List<String[]>> PALACE_CLASSIC;

    /** 加载卷二·十二宫原文语料（宫名 → [行号, 原文]*）；资源缺失时返回空表，不阻断流程。 */
    static Map<String, List<String[]>> loadPalaceClassic() {
        Map<String, List<String[]>> m = PALACE_CLASSIC;
        if (m != null) {
            return m;
        }
        synchronized (ZiWeiDouShuOntologyJobWorker.class) {
            if (PALACE_CLASSIC != null) {
                return PALACE_CLASSIC;
            }
            m = new LinkedHashMap<>();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(
                    ZiWeiDouShuOntologyJobWorker.class.getResourceAsStream("/ziweidoushu/palace_classic.tsv"),
                    StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    if (line.isBlank()) {
                        continue;
                    }
                    String[] c = line.split("\t", 3);
                    if (c.length < 3) {
                        continue;
                    }
                    m.computeIfAbsent(c[0], k -> new ArrayList<>()).add(new String[]{c[1], c[2]});
                }
            } catch (Exception e) {
                // 资源缺失：返回空表，交由上层降级（不臆造原文）
            }
            PALACE_CLASSIC = m;
            return m;
        }
    }

    /** 取某宫某主星的原文首句（断语印证，附 c3 行号）；该宫无此星记载时返回空串。 */
    static String palaceStarQuote(final String palace, final String star) {
        List<String[]> rows = loadPalaceClassic().get(palace);
        if (rows == null) {
            return "";
        }
        for (String[] r : rows) {
            if (r[1].startsWith(star)) {
                return r[1] + "（卷二·" + palace + "，c3 L" + r[0] + "）";
            }
        }
        return "";
    }

    // --------------------------------------------------------------------------
    // 卷二·命宫「宫位 × 生年干 → 财官格/贵格/凶」可客观判定（原文 c3 L459-976）。
    // 口径：从已落地的 palace_classic.tsv 原文按「主星块 → 宫位（最近宫位）→ 生年干」择取，
    //       命中即回原文句并据原文断吉/凶；不臆造、可回溯（单一真源＝资源原文）。
    // --------------------------------------------------------------------------
    private static final String GAN_STR = "甲乙丙丁戊己庚辛壬癸";
    private static final String ZHI_STR = "子丑寅卯辰巳午未申酉戌亥";

    /** 诸星（十四主星 + 辅煞杂曜/四化/岁君斗君）之「论」块开篇名，用于界定命宫主星原文块边界。 */
    private static final String[] STAR_BLOCK_ALL = {
            "紫微", "天机", "太阳", "武曲", "天同", "廉贞", "天府", "太阴", "贪狼", "巨门",
            "天相", "天梁", "七杀", "破军", "文昌", "文曲", "左辅", "右弼", "禄存", "擎羊",
            "陀罗", "火星", "铃星", "天魁", "天钺", "天马", "天空", "地劫", "天伤", "天使",
            "化禄", "化权", "化科", "化忌", "岁君", "斗君"};

    static boolean isStarBlockOpen(final String text, final String starName) {
        if (text == null || starName == null || !text.startsWith(starName) || text.length() <= starName.length()) {
            return false;
        }
        char nx = text.charAt(starName.length());
        return "土木金火水属星，".indexOf(nx) >= 0;
    }

    static boolean isAnyStarBlockOpen(final String text) {
        for (String s : STAR_BLOCK_ALL) {
            if (isStarBlockOpen(text, s)) {
                return true;
            }
        }
        return false;
    }

    /** 取字符串中出现的地支（排除干支之支，如「甲子」之「子」）。 */
    static Set<Character> zhiChars(final String s) {
        Set<Character> z = new HashSet<>();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (ZHI_STR.indexOf(c) >= 0 && !(i > 0 && GAN_STR.indexOf(s.charAt(i - 1)) >= 0)) {
                z.add(c);
            }
        }
        if (s.contains("四墓")) {
            z.add('辰');
            z.add('戌');
            z.add('丑');
            z.add('未');
        }
        return z;
    }

    /** 取字符串中出现的天干（生年干条件，容 OCR 讹字如「丁庚甲入生人」之「入」）。 */
    static Set<Character> ganChars(final String s) {
        Set<Character> z = new HashSet<>();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (GAN_STR.indexOf(c) >= 0) {
                z.add(c);
            }
        }
        return z;
    }

    /** 依原文断语判吉/凶。 */
    static String classifyGe(final String after) {
        if (after.contains("不贵") || after.contains("不为")) {
            return "凶";
        }
        boolean good = false;
        for (String k : new String[]{"财官格", "贵格", "福厚", "大贵", "财官双美", "合局", "上格", "主贵", "富贵", "享福", "吉利", "吉", "利达"}) {
            if (after.contains(k)) {
                good = true;
                break;
            }
        }
        boolean bad = false;
        for (String k : new String[]{"困", "悔吝", "孤寡", "夭", "破", "凶", "祸", "不耐久", "成败", "下局", "灾", "贫", "残疾", "不宜", "招非", "有祸"}) {
            if (after.contains(k)) {
                bad = true;
                break;
            }
        }
        if (good && !bad) {
            return "吉";
        }
        if (bad && !good) {
            return "凶";
        }
        return good ? "吉凶参" : "平";
    }

    // --------------------------------------------------------------------------
    // 煞凑条件客观化（「羊陀火铃冲会」「巨宿羊陀」等）〔释读：以实际星曜盘核验煞曜是否凑会〕
    //   《全書》断语多以「羊陀火铃/巨宿羊陀/羊陀冲破」等煞凑为附加条件（如卷二·命宫 L722「辰宫安命
    //   六庚生人吉利，羊陀火铃冲会，又在陷地残疾下局」；L733「七杀居命落闲宫，巨宿羊陀更照冲」）。
    //   此处将「煞曜是否凑会」由待考文本升为**可复算布尔判定**：所需煞曜是否悉见于同宫／三方四正，
    //   据实际盘面给出 满足(是)/不满足(否)，不改《全書》第一权威、不臆造未载条件。
    // --------------------------------------------------------------------------
    /** 煞凑条件载体：短语、起点、所需煞曜、作用范围（同宫／三方四正）。 */
    static final class ShaCou {
        String phrase;
        int start;
        List<String> stars = new ArrayList<>();
        String scope = "三方四正";
    }

    /** 从断语文本识别「煞凑」短语（长匹配优先）；无则 null。 */
    static ShaCou detectShaCou(final String text) {
        if (text == null) {
            return null;
        }
        String[] pats = {
                "羊陀火铃忌冲合", "羊陀火铃忌", "羊陀火铃冲会", "羊陀火铃来冲会", "羊陀火铃来相会",
                "羊陀火铃三方会", "羊陀火铃三方照", "羊陀火铃会合", "羊陀火铃冲破", "羊陀火铃",
                "巨宿羊陀", "火铃羊陀", "羊陀火", "羊陀冲破", "羊陀", "火铃", "羊火"
        };
        for (String p : pats) {
            int idx = text.indexOf(p);
            if (idx < 0) {
                continue;
            }
            ShaCou sc = new ShaCou();
            sc.phrase = p;
            sc.start = idx;
            if (p.contains("羊")) {
                sc.stars.add("擎羊");
            }
            if (p.contains("陀")) {
                sc.stars.add("陀罗");
            }
            if (p.contains("火")) {
                sc.stars.add("火星");
            }
            if (p.contains("铃")) {
                sc.stars.add("铃星");
            }
            if (p.contains("巨")) {
                sc.stars.add("巨门");
            }
            if (sc.stars.isEmpty()) {
                continue;
            }
            sc.scope = (p.contains("同宫") || p.contains("同垣") || p.contains("同度")) ? "同宫" : "三方四正";
            return sc;
        }
        return null;
    }

    /** 煞凑是否成立：所需煞曜是否全部见于 同宫 或 三方四正（依 {@link ShaCou#scope}）。 */
    static boolean shaCouSatisfied(final ShaCou sc, final int palaceZhi, final Map<Integer, List<String>> at) {
        if (sc == null || at == null) {
            return false;
        }
        int[] scope = "同宫".equals(sc.scope)
                ? new int[]{palaceZhi}
                : new int[]{palaceZhi, (palaceZhi + 4) % 12, (palaceZhi + 8) % 12, (palaceZhi + 6) % 12};
        Set<String> present = new HashSet<>();
        for (int b : scope) {
            present.addAll(at.getOrDefault(b, List.of()));
        }
        for (String req : sc.stars) {
            if (!present.contains(req)) {
                return false;
            }
        }
        return true;
    }

    /** 煞凑布尔之可读描述，如「擎羊/陀罗/火星/铃星在三方四正=满足(是)」。 */
    static String shaCouDescribe(final ShaCou sc, final boolean sat) {
        StringBuilder bs = new StringBuilder();
        for (int i = 0; i < sc.stars.size(); i++) {
            if (i > 0) {
                bs.append("/");
            }
            bs.append(sc.stars.get(i));
        }
        return bs + "在" + sc.scope + "=" + (sat ? "满足(是)" : "不满足(否)");
    }

    /**
     * 卷二·命宫「宫位 × 生年干 → 财官格」判定：命宫主星＝star、命宫支＝palaceZhi、生年干＝birthGan，
     * 命中则回原文句（附 c3 行号）并据原文断吉/凶；未命中返回空串（不臆断）。
     */
    static String judgeMingGongGe(final String star, final int palaceZhi, final int birthGan) {
        return judgeMingGongGe(star, palaceZhi, birthGan, null);
    }

    /**
     * 重载（含实际星曜盘 at）：条件式命中时据盘面做**煞凑条件布尔判定**（羊陀火铃冲会／巨宿羊陀／不喜…），
     * 不再一律标〔条件式·待考〕；at==null 时退化为原「取文·待考」。
     */
    static String judgeMingGongGe(final String star, final int palaceZhi, final int birthGan,
                                  final Map<Integer, List<String>> at) {
        List<String[]> rows = loadPalaceClassic().get("命宫");
        if (rows == null || star == null) {
            return "";
        }
        String zhiChar = String.valueOf(ZHI[palaceZhi]);
        char ganChar = GAN[birthGan].charAt(0);
        // 定位主星原文块 [start, end)
        int start = -1;
        for (int i = 0; i < rows.size(); i++) {
            if (isStarBlockOpen(rows.get(i)[1], star)) {
                start = i;
                break;
            }
        }
        if (start < 0) {
            return "";
        }
        int end = rows.size();
        for (int i = start + 1; i < rows.size(); i++) {
            if (isAnyStarBlockOpen(rows.get(i)[1])) {
                end = i;
                break;
            }
        }
        // 逐句扫描：先按句号切「句」再按逗号切「子句」，carry（最近宫位集）按句作用域复位；
        // 纯结构化条件（宫/地/命坐 + 生年干，且无他会照附加条件）→ 硬判吉/凶；
        // 含他会照/逢遇等附加条件的「条件式」命中 → 取文并标〔条件式·待考〕，不硬断（不臆造）。
        final String pureBefore = "^(若在|若)?[子丑寅卯辰巳午未申酉戌亥]{0,3}(宫|地)?(入庙|庙旺|庙|旺|陷|得地|和平|利益|利)?[喜最宜]{0,2}六?[甲乙丙丁戊己庚辛壬癸入六、，]*$";
        String tentative = "";
        for (int i = start; i < end; i++) {
            String line = rows.get(i)[1];
            if (!line.contains("生人")) {
                continue;
            }
            for (String sent : line.split("(?<=。)")) {
                Set<Character> carry = new HashSet<>();
                for (String c : sent.split("(?<=[，。])")) {
                    Set<Character> clauseZhi = zhiChars(c);
                    if (!clauseZhi.isEmpty()) {
                        carry = clauseZhi;
                    }
                    int pi = c.indexOf("生人");
                    if (pi < 0) {
                        continue;
                    }
                    String before = c.substring(0, pi);
                    Set<Character> gans = ganChars(before);
                    Set<Character> branches = clauseZhi.isEmpty() ? carry : clauseZhi;
                    if (gans.isEmpty() || branches == null || !branches.contains(zhiChar.charAt(0)) || !gans.contains(ganChar)) {
                        continue;
                    }
                    String after = c.substring(pi + 2).replaceAll("[，。\\s]", "").trim();
                    String hit = "命宫" + star + "在" + zhiChar + "宫，" + ganChar + "年生人 → " + after;
                    if (before.matches(pureBefore)) {
                        return hit + "〔" + classifyGe(after) + "〕（卷二·命宫，c3 L" + rows.get(i)[0] + "）";
                    }
                    // ① 煞凑条件客观化：取该「句」中「生人」之后全文（含后续子句、截断于下一个生人），
                    //    据实际星曜盘核验「羊陀火铃／巨宿羊陀」等煞曜是否凑会（同宫／三方四正）；含「陷」者叠验庙陷。
                    int sentStart = sent.indexOf(c);
                    String afterFull = sentStart >= 0 ? sent.substring(sentStart + pi + 2) : after;
                    int nextSheng = afterFull.indexOf("生人");
                    if (nextSheng >= 0) {
                        afterFull = afterFull.substring(0, nextSheng);
                    }
                    String afterNorm = afterFull.replaceAll("[，。\\s]", "").trim();
                    ShaCou sc = detectShaCou(afterNorm);
                    if (sc != null && at != null) {
                        boolean sat = shaCouSatisfied(sc, palaceZhi, at);
                        boolean needXian = afterNorm.contains("陷");
                        boolean xianOk = !needXian || isFallen(star, palaceZhi);
                        boolean cond = sat && xianOk;
                        String judge = cond ? "凶" : "吉";
                        String desc = shaCouDescribe(sc, sat) + (needXian ? "；" + star + "在陷地=" + (xianOk ? "是" : "否") : "");
                        return "命宫" + star + "在" + zhiChar + "宫，" + ganChar + "年生人 → " + afterFull.trim()
                                + "〔煞凑·布尔：" + desc + "→" + judge + "〕（卷二·命宫，c3 L" + rows.get(i)[0] + "）";
                    }
                    // ② 直接「不喜…生人」（生年干/命宫支命中即不吉）→客观判凶
                    if (before.contains("不喜")) {
                        return hit + "〔不喜本人（生年干/命宫支命中）→凶〕（卷二·命宫，c3 L" + rows.get(i)[0] + "）";
                    }
                    if (tentative.isEmpty()) {
                        tentative = hit + "〔条件式·待考〕（卷二·命宫，c3 L" + rows.get(i)[0] + "）";
                    }
                }
            }
        }
        return tentative;
    }

    // ==========================================================================
    // 卷三·论诸星同垣各司所宜分别富贵贫贱夭寿（原文 c4 L178-477）——星情格语库。
    // 列：{星, 宫支集(可多字, "-"不限), 并见星(顿号, "(三方)"前缀表三方, "-"不限), 生干集("-"不限), 断语原文}
    // 落地口径：可客观判定者（居宫/并见/生干）逐条判定命中；仅举例式而无客观条件者标〔举例式·待考〕取文。
    // 出处：卷三·论诸星同垣各司所宜分别富贵贫贱夭寿。
    // ==========================================================================
    private static final String[][] STAR_TONGYUAN = {
            // 紫微（c4 L179-203）
            {"紫微", "午", "-", "甲丁己", "紫微居午无刑忌甲丁己命至公卿"},
            {"紫微", "子午", "-", "-", "紫微居子午科权禄照最为奇（仰面朝斗格）"},
            {"紫微", "亥寅", "-", "壬甲", "紫微男亥女寅宫壬甲生人富贵同"},
            {"紫微", "卯酉", "地劫、天空、擎羊、陀罗、火星、铃星", "-", "紫微卯酉劫空四杀多为脱俗之僧"},
            {"紫微", "-", "天府", "-", "紫微天府全依辅弼之功"},
            {"紫微", "-", "天府", "甲", "紫微天府同宫无杀凑甲人享福终身"},
            {"紫微", "-", "天府", "-", "紫府朝垣活禄逢终身福厚至三公"},
            {"紫微", "-", "天府、太阳、太阴", "-", "紫府日月居旺地必定出佳公卿器"},
            {"紫微", "-", "天府、武曲", "-", "紫府武曲临财宅更兼权禄富奢翁"},
            {"紫微", "-", "左辅、右弼", "-", "紫微辅弼同宫一呼百诺居上品"},
            {"紫微", "-", "天府、擎羊", "-", "紫府擎羊在巨商"},
            {"紫微", "-", "禄存", "-", "紫禄同宫日月照贵不可言"},
            {"紫微", "-", "七杀", "-", "紫微七杀化权反作桢梓"},
            {"紫微", "-", "破军", "-", "紫微破军无左右无吉曜凶恶胥吏之徒"},
            {"紫微", "-", "-", "-", "紫微权禄遇羊陀虽获吉而无道"},
            {"紫微", "辰戌丑未", "破军", "-", "紫破命临于辰戌丑未再加吉曜富贵堪期"},
            {"紫微", "-", "破军、贪狼", "-", "紫破贪狼为至淫男女邪淫"},
            {"紫微", "巳亥", "天府", "-", "紫府同临巳亥一朝富贵双全"},
            {"紫微", "-", "天府", "-", "紫府夹命为贵格"},
            {"紫微", "-", "-", "-", "紫微昌曲富贵可期"},
            {"紫微", "-", "太阴", "-", "紫微太阴杀曜逢一生曹吏逞英雄"},
            {"紫微", "-", "武曲、破军", "-", "紫微武曲破军会羊陀欺公祸乱"},
            {"紫微", "辰戌", "破军", "-", "紫破辰戌君臣不义"},
            {"紫微", "寅午申", "太阳", "-", "女命紫微太阳星早遇贤夫信可凭"},
            // 天府（c4 L204-211）
            {"天府", "戌", "-", "甲己", "天府戌宫无杀凑甲己人腰金又且富"},
            {"天府", "午戌", "天相", "甲", "天府居午戌天相来朝甲人一品之贵"},
            {"天府", "-", "-", "-", "府相朝垣千锺食禄"},
            {"天府", "-", "文昌、文曲、左辅、右弼", "-", "天府昌曲左右高第恩荣"},
            {"天府", "-", "天相、天梁", "-", "天府天相天梁同君臣庆会"},
            {"天府", "-", "禄存、文昌、文曲", "-", "天府禄存昌曲巨万之资"},
            {"天府", "-", "武曲", "-", "天府武曲居财宅更兼权禄富奢翁"},
            // 天相（c4 L212-215）
            {"天相", "-", "廉贞、擎羊", "-", "天相廉贞擎羊来多招刑杖难逃"},
            {"天相", "-", "-", "己甲庚", "天相之星女命缠必当子贵及夫贤"},
            {"天相", "-", "右弼", "-", "右弼天相福来临"},
            // 天梁（c4 L216-227）
            {"天梁", "-", "太阴", "-", "天梁月曜女淫贫"},
            {"天梁", "午", "-", "丁己癸", "天梁居午位官资清显朝堂"},
            {"天梁", "巳亥", "天同", "-", "梁同巳亥男多浪荡女多淫"},
            {"天梁", "-", "文昌", "-", "天梁文昌居庙旺位至台纲"},
            {"天梁", "-", "-", "-", "天梁守照吉相逢平生福寿"},
            {"天梁", "寅申", "天同、天机、太阴", "-", "梁同机月寅申位一生利业聪明"},
            {"天梁", "-", "太阳、文昌、禄存", "-", "天梁太阳昌禄会胪传第一名"},
            {"天梁", "-", "武曲、太阴、铃星", "-", "梁武阴铃拟作栋梁之客"},
            {"天梁", "-", "太阴", "-", "梁宿太阴却作飘蓬之客"},
            {"天梁", "-", "天马", "-", "天梁天马为人飘荡风流"},
            {"天梁", "-", "-", "-", "天梁加吉坐迁移臣商高贾"},
            // 天同（c4 L228-233）
            {"天同", "-", "-", "-", "天同会吉寿元长"},
            {"天同", "午", "贪狼、擎羊、陀罗", "丙戊", "天同贪羊陀居午位丙戊镇御边疆（马头带箭）"},
            {"天同", "-", "太阴", "-", "同月陷宫加杀重技艺羸黄"},
            {"天同", "戌", "-", "丁", "天同戌宫为反背丁人命遇反为佳"},
            {"天同", "寅卯戌", "-", "子辛丁", "女命天同必是贤"},
            // 天机（c4 L234-243）
            {"天机", "-", "天梁", "-", "机梁会合善谈兵，居戌亦为美论"},
            {"天机", "-", "天梁", "-", "机梁守命加吉曜富贵慈祥（加刑忌僧道）"},
            {"天机", "-", "天梁", "-", "机梁同照命身又逢劫空偏宜僧道"},
            {"天机", "-", "天梁、七杀、破军", "-", "机梁七杀破军冲羽客僧流命所逢"},
            {"天机", "寅申", "太阴、天同、天梁", "-", "机月同梁作吏人"},
            {"天机", "-", "天梁、贪狼、太阴、天同", "-", "机梁贪月同机会暮夜经商无眠睡"},
            {"天机", "-", "-", "-", "天机加恶杀同宫狗偷鼠窃"},
            {"天机", "巳酉", "-", "-", "天机巳宫酉逢好饮离宗奸狡重"},
            {"天机", "-", "-", "-", "巨陷天机为破格"},
            // 太阳（c4 L244-250）
            {"太阳", "子辰卯", "-", "-", "日照雷门子辰卯地昼生富贵声扬"},
            {"太阳", "-", "文昌", "-", "太阳文昌在官禄皇殿朝班"},
            {"太阳", "未申", "-", "-", "日落未申在命位为人先勤后懒"},
            {"太阳", "午", "-", "庚辛丁己", "太阳居午庚辛丁己人富贵双全"},
            {"太阳", "-", "-", "-", "太阳化忌是非日有目还伤"},
            {"太阳", "-", "-", "-", "女命端正太阳星早配贤夫信可凭（陷平常）"},
            // 太阴（c4 L251-271）
            {"太阴", "子", "-", "丙丁", "太阴居子丙丁富贵忠良（夜生人合局）"},
            {"太阴", "-", "武曲、禄存", "-", "太阴武曲禄存同左右相逢富贵翁"},
            {"太阴", "-", "文曲", "-", "太阴同文曲于妻宫蟾宫折桂"},
            {"太阴", "-", "擎羊、陀罗", "-", "太阴羊陀必主人离财散"},
            {"太阴", "亥", "-", "-", "月朗天门于亥地登云职掌大权"},
            {"太阴", "-", "天梁", "-", "月曜天梁女淫贫"},
            {"太阳、太阴", "-", "-", "-", "太阳太阴拱照／日月诸格（举例式·须按日/月各居支细判）：日巳月酉丑宫命步蟾宫、日卯月亥安命未宫多折桂、日月同未命安丑侯伯之材、日辰月戌并争耀权禄非浅、日月夹命夹财加吉曜不权则富、日月最嫌反背、日月陷宫逢恶杀劳碌奔波、日月羊陀多克亲〔举例式·待考〕"},
            {"太阳、太阴", "-", "-", "-", "日月命身居丑未三方无吉反为凶（子午辰戌身命更佳）〔举例式·待考〕"},
            {"太阳、太阴", "-", "-", "-", "日月守命不如照合并明〔举例式·待考〕"},
            {"太阳、太阴", "-", "-", "-", "阴阳左右合为佳〔举例式·待考〕"},
            {"太阳、太阴", "-", "-", "-", "日月更须贪杀会男多奸盗女多淫〔举例式·待考〕"},
            {"太阳、太阴", "-", "-", "-", "日月疾厄命宫空腰陀目瞽〔举例式·待考〕"},
            // 文昌（c4 L272-276）
            {"文昌", "-", "武曲", "-", "文昌武曲为人多学多能（论三方科权禄）"},
            {"文昌", "-", "-", "-", "文科拱照贾谊年少登科（论三方）"},
            {"文昌", "-", "左辅", "-", "左辅文昌位至三台"},
            {"文昌", "-", "武曲", "-", "文昌武曲于身命文正兼备"},
            // 文曲（c4 L277-293）
            {"文曲", "-", "左辅、右弼", "-", "二曲庙垣逢左右将相之财"},
            {"文曲", "-", "-", "-", "二曲旺宫威名赫奕"},
            {"文曲", "-", "贪狼", "-", "二曲贪狼午丑限防溺水之忧"},
            {"文曲", "-", "-", "-", "昌曲夹命最为奇（不贵即富）"},
            {"文曲", "-", "-", "-", "昌曲临于丑未时逢卯酉近天颜"},
            {"文曲", "巳亥", "-", "-", "昌曲巳亥临不贵即当大富"},
            {"文曲", "-", "-", "-", "昌曲吉星居福德谓之玉袖天香"},
            {"文曲", "-", "-", "-", "昌曲陷宫凶杀破虚誉之隆"},
            {"文曲", "-", "-", "-", "昌曲陷于天伤颜回夭折"},
            {"文曲", "-", "-", "己辛壬", "昌曲己辛壬生人限逢辰戌虑投河"},
            {"文曲", "巳亥", "廉贞", "-", "昌曲廉贞于巳亥遭刑不善且虚夸"},
            {"文曲", "-", "禄存", "-", "昌曲禄存犹为奇特"},
            {"文曲", "寅卯", "破军", "-", "昌曲破军临虎兔杀羊冲破奔波"},
            {"文曲", "-", "左辅、右弼、擎羊、陀罗", "-", "昌曲左右会羊陀当生异痣"},
            {"文曲", "-", "-", "-", "女人昌曲聪明富贵只多淫"},
            // 武曲（c4 L294-307）
            {"武曲", "辰戌丑未", "-", "-", "武曲庙垣威名赫奕（辰戌宫佳，丑未次之）"},
            {"武曲", "-", "文昌、文曲", "-", "武曲相遇昌曲逢聪明巧艺定无穷"},
            {"武曲", "-", "禄存、天马", "-", "武曲禄马交驰发财远郡"},
            {"武曲", "-", "-", "-", "武曲迁移巨商高贾"},
            {"武曲", "-", "廉贞、贪狼", "-", "武曲廉贞贪杀便作经商"},
            {"武曲", "-", "破军", "-", "武曲破军破祖破家劳碌"},
            {"武曲", "-", "地劫、擎羊", "-", "武曲劫杀会擎羊因财持刀"},
            {"武曲", "-", "天魁、天钺", "-", "武曲魁钺居庙旺财赋之官"},
            {"武曲", "-", "贪狼", "-", "武曲贪狼财宅位横发资财"},
            {"武曲", "-", "贪狼", "-", "武曲贪狼加杀忌技艺之人"},
            {"武曲", "卯", "破军、廉贞", "-", "武曲破贞于卯地木压雷惊"},
            {"武曲", "-", "擎羊、陀罗、火星", "-", "武曲羊陀兼火宿丧命因财"},
            {"武曲", "-", "-", "-", "武曲之星为寡宿（妇夺夫权方免刑克）"},
            // 贪狼（c4 L308-326）
            {"贪狼", "辰戌丑未", "铃星、火星", "-", "贪狼遇铃火四墓宫豪富家资侯伯贵"},
            {"贪狼", "-", "-", "-", "贪狼入庙寿元长"},
            {"贪狼", "-", "-", "-", "贪狼会杀无吉曜屠宰之人"},
            {"贪狼", "子午卯酉", "-", "-", "贪狼子午卯酉鼠窃狗偷之辈终身不能有为"},
            {"贪狼", "-", "-", "-", "贪狼加吉坐长生寿考永如彭祖"},
            {"贪狼", "巳亥", "-", "-", "贪狼巳亥加杀不为屠户亦遭刑"},
            {"贪狼", "-", "武曲", "-", "贪狼同行晚景边夷神服（坐命武曲守照）"},
            {"贪狼", "-", "武曲", "-", "贪武先贫而后富"},
            {"贪狼", "-", "-", "-", "贪狼加杀同乡女偷香而男鼠窃"},
            {"贪狼", "-", "武曲", "-", "贪狼武曲同守身无吉命反不长"},
            {"贪狼", "-", "太阴、天同、天梁、天机", "-", "贪月同杀会机梁贪财无厌作经商"},
            {"贪狼", "亥子", "擎羊、陀罗", "-", "贪遇羊陀居亥子名为泛水桃花"},
            {"贪狼", "寅", "陀罗", "-", "贪狼陀罗在寅宫号曰风流彩杖"},
            {"贪狼", "申", "武曲", "-", "贪武申宫为下格"},
            {"贪狼", "-", "武曲、破军", "-", "贪武破军无吉曜迷恋酒以忘身"},
            {"贪狼", "-", "廉贞", "-", "贪狼廉贞同度男多浪荡女多淫"},
            {"贪狼", "-", "-", "-", "女命贪狼多嫉妒（在亥子遇羊陀）"},
            {"贪狼", "-", "-", "-", "贪武四生四墓宫破军忌杀百工通〔举例式·待考〕"},
            // 廉贞（c4 L327-340）
            {"廉贞", "申未", "-", "-", "廉贞申未宫无杀富贵声扬播远名（雄宿朝元格）"},
            {"廉贞", "卯酉", "-", "-", "廉贞卯酉宫加杀公胥无面官人"},
            {"廉贞", "-", "-", "-", "廉贞暗臣曹吏贪婪"},
            {"廉贞", "-", "贪狼、七杀、破军、武曲", "-", "廉贞贪杀破军逢武曲迁移作具戎"},
            {"廉贞", "午", "七杀", "-", "廉贞七杀居庙旺反为积富之人"},
            {"廉贞", "-", "破军、火星", "-", "廉贞破火居陷地自缢投河"},
            {"廉贞", "巳亥", "七杀", "-", "廉贞七杀居巳亥流荡天涯"},
            {"廉贞", "-", "-", "-", "仲由威猛廉贞入庙遇将军"},
            {"廉贞", "-", "擎羊、陀罗、火星、铃星", "-", "廉贞四杀遭刑戮"},
            {"廉贞", "-", "白虎", "-", "廉贞白虎刑杖难逃"},
            {"廉贞", "-", "七杀、破军", "-", "廉贞杀破会迁移死于外道"},
            {"廉贞", "-", "擎羊", "-", "廉贞羊杀居官禄加纽难逃"},
            {"廉贞", "-", "-", "甲己庚癸", "廉贞清白能相守（女命）"},
            // 巨门（c4 L341-359）
            {"巨门", "申寅", "太阳", "-", "巨日寅宫立命申先驰名而食禄 / 巨日申宫立命寅驰名食禄"},
            {"巨门", "子午", "-", "辛癸", "巨门子午科权禄石中隐玉福兴隆"},
            {"巨门", "亥", "太阳", "-", "巨在亥宫日在巳食禄驰名"},
            {"巨门", "巳", "太阳", "-", "巨在巳宫日在亥反为不佳"},
            {"巨门", "-", "-", "-", "巨日命宫寅位食禄驰名〔举例式·待考〕"},
            {"巨门", "-", "-", "-", "巨日命立申宫亦妙〔举例式·待考〕"},
            {"巨门", "-", "太阳", "-", "巨日拱照亦为奇"},
            {"巨门", "卯", "天机", "乙辛己丙", "巨机居卯乙辛己丙至公卿（甲人平常）"},
            {"巨门", "酉", "天机", "-", "巨机酉上化吉者纵有财官也不终"},
            {"巨门", "辰", "-", "辛", "巨门辰宫化忌辛人命遇反为奇"},
            {"巨门", "丑未", "天机", "-", "巨机丑未为下格"},
            {"巨门", "-", "陀罗", "-", "巨门陀罗必生异痣"},
            {"巨门", "-", "擎羊、陀罗", "-", "巨门羊陀于身命疾厄羸黄困弱盗而娼"},
            {"巨门", "-", "四杀", "-", "巨门四杀陷而凶"},
            {"巨门", "-", "火星、擎羊、陀罗", "-", "巨火擎羊陀逢恶曜防缢死投河"},
            {"巨门", "-", "火星、铃星", "-", "巨火铃星逢恶限死于外道"},
            {"巨门", "-", "天机", "-", "巨宿天机为破荡（女命巨机于卯酉）"},
            // 七杀（c4 L360-374）
            {"七杀", "寅申子午", "-", "-", "七杀寅申子午一生爵禄荣昌（七杀朝斗格）"},
            {"七杀", "-", "破军", "-", "七杀破军专依羊铃之虐"},
            {"七杀", "-", "廉贞", "-", "七杀廉贞同位路上埋尸"},
            {"七杀", "-", "破军", "-", "七杀破军宜出外诸般手艺不能精"},
            {"七杀", "-", "-", "-", "七杀临身命流年刑忌灾伤"},
            {"七杀", "-", "擎羊、陀罗", "-", "杀临绝地会羊陀颜回夭折"},
            {"七杀", "-", "铃星、火星", "-", "七杀重逢四杀腰陀背曲阵中亡"},
            {"七杀", "-", "火星、擎羊", "-", "七杀火羊贪且贱屠宰之人"},
            {"七杀", "-", "擎羊、铃星", "-", "七杀羊铃流年白虎刑戮灾迍"},
            {"七杀", "-", "擎羊", "-", "七杀流羊二官符离乡遭配"},
            {"七杀", "卯酉", "擎羊", "午", "七杀守照岁限擎羊午生人命安卯酉宫主凶亡"},
            {"七杀", "-", "-", "-", "七杀沉吟福不荣"},
            {"七杀", "-", "-", "-", "七杀临身终是夭"},
            {"七杀", "-", "-", "-", "七杀单居福德女人切忌贱无疑"},
            // 破军（c4 L375-381）
            {"破军", "子午", "-", "甲癸", "破军子午宫无杀官资清显至三公"},
            {"破军", "-", "贪狼、禄存、天马", "-", "破军贪狼逢禄马男多浪荡女多淫"},
            {"破军", "-", "巨门", "-", "破军暗巨同乡水中作冢"},
            {"破军", "-", "火星、铃星", "-", "破军火铃奔波劳碌"},
            {"破军", "-", "-", "-", "破军一曜性难明"},
            {"破军", "-", "擎羊、铃星", "-", "破耗羊铃官禄位到处乞求"},
            // 擎羊（c4 L382-394）
            {"擎羊", "辰戌丑未", "-", "-", "擎羊入庙富贵声扬"},
            {"擎羊", "辰戌", "火星", "-", "羊火同宫威权压众"},
            {"擎羊", "-", "陀罗、铃星、火星", "-", "羊陀铃火守身命腰驼背曲之人"},
            {"擎羊", "子午卯酉", "-", "-", "擎羊子午卯酉非夭折而刑伤（子酉次之，马头带剑）"},
            {"擎羊", "卯午酉子", "-", "-", "擎羊逢力士李广难封"},
            {"擎羊", "-", "陀罗、火星、铃星", "-", "羊陀火铃逢吉发财凶则忌"},
            {"擎羊", "-", "铃星", "-", "羊铃坐命流年白虎灾伤"},
            {"擎羊", "酉", "-", "庚", "擎羊对守在酉宫岁迭羊陀庚命凶"},
            {"擎羊", "-", "忌", "-", "羊陀夹忌为败局"},
            {"擎羊", "-", "铃星、破军", "-", "羊陀流年铃破面字斑痕"},
            {"擎羊", "-", "火星", "-", "擎羊火星为下格"},
            {"擎羊", "酉", "-", "-", "擎羊重逢流羊西施倾殒身"},
            // 陀罗（c4 L395-396）
            {"陀罗", "巳亥寅申", "-", "-", "陀罗巳亥寅申非夭折而刑伤"},
            // 火星/铃星（c4 L397-402）
            {"火星", "寅午戌", "-", "-", "火星庙寅午戌"},
            {"铃星", "-", "-", "-", "火铃相遇名振诸邦"},
            {"火星、铃星", "-", "火星、铃星", "-", "火铃夹命为败局"},
            {"铃星", "-", "-", "-", "火铃旺宫亦为福论"},
            {"擎羊", "-", "火星、铃星", "-", "擎羊火铃为下格（女人庙旺犹可，陷地下贱）"},
            // 魁钺（c4 L403-408）
            {"天魁、天钺", "-", "天魁、天钺", "-", "魁钺夹命为奇格"},
            {"天魁、天钺", "-", "-", "-", "魁钺命身多折桂"},
            {"天魁、天钺", "-", "文昌、文曲、禄存", "-", "魁钺昌曲禄存扶刑杀无冲台辅贵"},
            {"天魁、天钺", "-", "擎羊、铃星、地劫、天空", "-", "魁钺重逢杀凑痼疾尤多"},
            {"天魁、天钺", "-", "-", "-", "魁钺辅星为福寿"},
            // 左辅右弼（c4 L409-418）
            {"左辅、右弼", "-", "文昌", "-", "左右文昌位至台辅"},
            {"左辅、右弼", "-", "左辅、右弼", "-", "左右夹命为贵格"},
            {"左辅、右弼", "-", "-", "-", "右弼左辅终身福厚（在命宫迁移）"},
            {"左辅、右弼", "-", "-", "-", "左右同宫披罗衣紫"},
            {"左辅、右弼", "-", "-", "-", "左右单守照命宫离宗庶出"},
            {"左辅、右弼", "-", "廉贞、擎羊", "-", "左右贞羊遭刑盗（左右昌曲逢羊陀当生暗痣）"},
            {"左辅、右弼", "-", "-", "-", "左右财官兼夹拱衣禄丰盈"},
            {"左辅、右弼", "-", "天魁、天钺", "-", "左右魁钺为福寿"},
            {"右弼", "-", "天相", "-", "右弼天相福来临"},
            // 禄存（c4 L419-426）
            {"禄存", "-", "-", "-", "禄存十二宫中皆入庙"},
            {"禄存", "-", "-", "-", "禄存守于财宅积玉堆金"},
            {"禄存", "子午", "-", "-", "禄存子午位迁移身命逢之利禄宜"},
            {"禄存", "-", "-", "-", "明禄暗禄位至公卿"},
            {"禄存", "-", "-", "-", "双禄重返终身富贵"},
            {"禄存", "-", "-", "-", "禄逢冲破吉也成凶"},
            {"禄存", "-", "-", "-", "双禄守命吕后专权"},
            {"禄存", "-", "-", "-", "禄存厚重多衣禄"},
            // 天马（c4 L427-430）
            {"天马", "-", "禄存", "-", "禄马最喜交驰（忌见杀羊火截路空亡）"},
            {"天马", "-", "-", "-", "天马四生妻宫富贵还当封赠"},
            {"天马", "-", "地劫、天空", "-", "马遇空亡终身奔走"},
            // 科权禄（c4 L431-441）
            {"化禄、化权、化科", "-", "-", "-", "科权禄合富贵双全"},
            {"化禄、化权", "-", "-", "-", "禄权命逢合吉威权压众相王朝"},
            {"化禄、化权", "-", "-", "-", "权禄重逢财官双美"},
            {"化科、化权", "-", "-", "-", "科命权朝登庸甲第"},
            {"化禄", "子午", "-", "-", "化禄子午位迁移夫子文章冠世（对面朝天子）"},
            {"化禄、化权", "-", "-", "-", "权禄重逢杀凑虚誉之隆"},
            {"化科", "-", "-", "-", "科名陷于凶神苗而不秀（日戌月卯化科陷地）"},
            {"化禄", "-", "-", "-", "禄主缠于弱地发不主财"},
            {"化禄、化权", "-", "-", "-", "权禄守财福之位处世荣华"},
            {"化禄、化权", "-", "-", "-", "权禄吉星奴仆位纵然官贵也奔波"},
            // 劫空（c4 L442-446）
            {"地劫、天空", "-", "地劫、天空", "-", "劫空夹命为败局"},
            {"地劫、天空", "-", "-", "-", "劫空临限楚王丧国绿珠亡"},
            {"地劫、天空", "-", "-", "-", "生处劫空犹如半天折翅"},
            {"地劫、天空", "-", "-", "-", "劫空临财福之乡生来贫贱"},
            // 伤使（c4 L447-448）
            {"天伤、天使", "-", "-", "-", "天伤加恶曜仲尼绝粮邓通亡"},
            // 命宫／身宫／纳音／财帛／财宅／财福（c4 L449-477，宫位类）
            {"命宫", "-", "-", "-", "三夹命凶六夹吉（三夹劫空火铃羊陀，六夹紫府左右昌曲魁钺科权禄日月）"},
            {"命宫", "-", "-", "-", "命无正曜二姓延生"},
            {"命宫", "-", "-", "-", "命逢吉曜松柏清秀以难凋"},
            {"命宫", "-", "-", "-", "限逢凶曜柳绿桃红而易谢"},
            {"命宫", "-", "-", "-", "命实运生如旱苗而得雨"},
            {"命宫", "-", "-", "-", "命衰运弱如嫩草而遭霜"},
            {"命宫", "-", "-", "-", "命有吉星官杀重纵有财官也辛苦"},
            {"身宫", "-", "-", "-", "三夹身凶六夹吉"},
            {"身宫", "-", "-", "-", "身命俱吉富贵双全"},
            {"身宫", "-", "-", "-", "身吉命凶亦为美论"},
            {"身宫", "-", "-", "-", "命弱身强财源不聚"},
            {"纳音", "-", "-", "-", "纳音墓库看何宫（水生人墓辰遇财官或禄存尤妙）"},
            {"纳音", "-", "-", "-", "生逢败地发也虚花"},
            {"纳音", "-", "-", "-", "绝处逢生花而不败"},
            {"财帛", "-", "-", "-", "日月夹财加吉曜不贵则富"},
            {"财帛", "-", "左辅、右弼", "-", "左右财官兼夹拱衣禄丰隆"},
            {"财帛", "-", "左辅、右弼", "-", "紫微辅弼多为财赋之官"},
            {"财帛", "-", "武曲、太阴", "-", "武曲太阴多居财赋之任"},
            {"财帛", "-", "紫微、天府、武曲", "-", "紫府武曲居财帛更兼权禄富奢翁"},
            {"财帛", "-", "武曲、贪狼", "-", "武曲贪狼财宅横发资财"},
            {"财帛", "-", "禄存", "-", "禄存守于财宅堆金积玉"},
            {"财帛", "-", "化禄、化权", "-", "权禄守财福之位出世荣华"},
            {"财帛", "-", "地劫、天空", "-", "劫空临财福之乡生来贫贱"}};

    // ==========================================================================
    // 通用工具
    // ==========================================================================

    private void completeResult(JobClient client, ActivatedJob job, Map<String, Object> result) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("result", result);
        client.newCompleteCommand(job.getKey()).variables(out).send().join();
    }

    private void fail(ActivatedJob job, JobClient client, String errorCode, Exception e) {
        log.error("❌ JobWorker 失败 | {} | jobKey={}", errorCode, job.getKey(), e);
        client.newThrowErrorCommand(job.getKey())
                .errorCode(errorCode)
                .errorMessage(e.getMessage() == null ? errorCode : e.getMessage())
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

    private static String orDefault(String v, String def) {
        return (v == null || v.isBlank()) ? def : v;
    }

    private static String firstLine(String s) {
        if (s == null) {
            return "";
        }
        int i = s.indexOf('\n');
        return i < 0 ? s : s.substring(0, i);
    }

    private static int toInt(String s, int def) {
        if (s == null) {
            return def;
        }
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static int floorMod(int a, int b) {
        int r = a % b;
        return (r < 0) ? r + b : r;
    }

    private static int indexOf(String[] arr, String v) {
        for (int i = 0; i < arr.length; i++) {
            if (arr[i].equals(v)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 卷一·太微賦 / 重補斗數彀率 具名格（客观条件明确者逐条判定）：日丽中天 / 水澄桂萼 / 众水朝东 / 金舆捧栉。
     * 出处：卷一·太微賦（c2「太陽居午，謂之日麗中天…」「太陰居子，號曰水澄桂萼…」「文耗居寅卯，謂之眾水朝東」
     *   「帝坐金車則曰金輿捧櫛」）；重補斗數彀率「紫微坐命庫則曰金輿捧櫛輦」。
     * 仅举格名而无客观成格条件者不在本表，见 {@link #taiweiPendingText()}（取文不臆断）。
     */
    static List<String> judgeTaiweiMingGe(int mingIdx, Set<String> mingMain, Set<String> mingSet) {
        List<String> out = new ArrayList<>();
        if (mingIdx == indexOf(ZHI, "午") && mingMain.contains("太阳")) {
            out.add("日丽中天（卷一·太微賦）：太阳居午守命（「太阳居午，谓之日丽中天，有专权之贵，敌国之富」）");
        }
        if (mingIdx == indexOf(ZHI, "子") && mingMain.contains("太阴")) {
            out.add("水澄桂萼（卷一·太微賦）：太阴居子守命（「太阴居子，号曰水澄桂萼，得清要之职，忠谏之材」）");
        }
        if ((mingIdx == indexOf(ZHI, "寅") || mingIdx == indexOf(ZHI, "卯")) && mingMain.contains("破军")
                && (mingSet.contains("文昌") || mingSet.contains("文曲"))) {
            out.add("众水朝东（卷一·太微賦）：文耗居寅卯（文昌/文曲·破军同守命于寅或卯）（「文耗居寅卯，谓之众水朝东」）");
        }
        if (mingMain.contains("紫微") && (mingIdx == indexOf(ZHI, "辰") || mingIdx == indexOf(ZHI, "戌")
                || mingIdx == indexOf(ZHI, "丑") || mingIdx == indexOf(ZHI, "未"))) {
            out.add("金舆捧栉（卷一·太微賦/重補斗數彀率）：紫微坐命于库辰戌丑未（「紫微坐命库则曰金舆捧栉辇」）");
        }
        return out;
    }

    /** 卷一·太微賦/重補斗數彀率 仅举格名、无客观成格条件者——依「取文·不臆断」登记文本（不另立个体）。 */
    static String taiweiPendingText() {
        return "卷一·太微賦/重補斗數彀率〔举例式·取文·不臆断〕：善蔭朝綱（仁慈之長）、貴入貴鄉（逢之富貴）、"
                + "財居財位（遇者富奢）、祿文拱命（貴而且賢）、善福居空位（天竺生涯）、桃花犯主為至淫、衣錦惹天香（臨官安文曜）"
                + "——原文仅举格名与吉断，未给可直接判定的成格条件；又「金殿傳臚＝太陽會文昌於官祿」「翰林清異＝太陰合文曲於妻宮」"
                + "「爛穀堆金＝祿合守田財」「高商豪客＝財蔭居遷移」诸名，与卷三·诸星同垣各司所宜已落地条（皇殿朝班/蟾宮折桂/堆金積玉/巨商高賈）同义，不另立";
    }

    private static boolean containsAny(String s, String... subs) {
        if (s == null) {
            return false;
        }
        for (String sub : subs) {
            if (s.contains(sub)) {
                return true;
            }
        }
        return false;
    }

    private static boolean eq(String a, String b) {
        return a != null && a.equals(b);
    }

    // ---- 历法/干支 ----

    private static int ganOfYear(int year) {
        return floorMod(year - 4, 10);
    }

    private static int branchOfYear(int year) {
        return floorMod(year - 4, 12);
    }

    private static int yearGanOf(LocalDateTime dt) {
        return ganOfYear(dt.getYear());
    }

    private static int yearBranchOf(LocalDateTime dt) {
        return branchOfYear(dt.getYear());
    }

    /** 时辰地支索引（子=0…亥=11）：23–1 子、1–3 丑、…。 */
    private static int hourBranchOf(int hour) {
        return ((hour + 1) / 2) % 12;
    }

    private static int sexagenaryIndex(int gan, int zhi) {
        for (int n = 0; n < 60; n++) {
            if (n % 10 == gan && n % 12 == zhi) {
                return n;
            }
        }
        return -1;
    }

    /** 由命宫干支（纳音）定五行局：水二/木三/金四/土五/火六。 */
    private static int bureauOf(int gan, int branch) {
        int idx = sexagenaryIndex(gan, branch);
        if (idx < 0) {
            return 0;
        }
        String elem = NAYIN_ELEM[idx / 2];
        switch (elem) {
            case "水": return 2;
            case "木": return 3;
            case "金": return 4;
            case "土": return 5;
            case "火": return 6;
            default: return 0;
        }
    }

    private static String bureauName(int bureau) {
        switch (bureau) {
            case 2: return "水二局";
            case 3: return "木三局";
            case 4: return "金四局";
            case 5: return "土五局";
            case 6: return "火六局";
            default: return "【待考·局未定】";
        }
    }

    private static int bureauFromSkeleton(String skeleton) {
        if (skeleton == null) {
            return 0;
        }
        for (int b = 2; b <= 6; b++) {
            if (skeleton.contains(bureauName(b))) {
                return b;
            }
        }
        return 0;
    }

    /** 从 chartSkeleton 中取某宫所在地支索引。 */
    private static int palaceBranch(String skeleton, String palaceName) {
        if (skeleton == null) {
            return -1;
        }
        int i = skeleton.indexOf(palaceName + "=");
        if (i < 0) {
            return -1;
        }
        int p = i + palaceName.length() + 1;
        if (p >= skeleton.length()) {
            return -1;
        }
        return indexOf(ZHI, String.valueOf(skeleton.charAt(p)));
    }

    // ---- 安星放置/解析 ----

    private static void place(Map<Integer, List<String>> at, int idx, String star) {
        at.computeIfAbsent(floorMod(idx, 12), k -> new ArrayList<>()).add(star);
    }

    /** 星曜落宫 → 统一「星@支；星@支；」文本（便于 {@link #parsePlacements} 反解）。 */
    private static String formatPlacements(Map<Integer, List<String>> at) {
        StringBuilder sb = new StringBuilder();
        for (int idx = 0; idx < 12; idx++) {
            for (String st : at.getOrDefault(idx, List.of())) {
                sb.append(st).append("@").append(ZHI[idx]).append("；");
            }
        }
        return sb.toString();
    }

    /** 解析「星@支」文本为 宫支索引 → 星表。 */
    private static Map<Integer, List<String>> parsePlacements(String s) {
        Map<Integer, List<String>> at = new TreeMap<>();
        if (s == null) {
            return at;
        }
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("([^@；｜〔〕\\s]+)@([子丑寅卯辰巳午未申酉戌亥])").matcher(s);
        while (m.find()) {
            int b = indexOf(ZHI, m.group(2));
            if (b >= 0) {
                at.computeIfAbsent(b, k -> new ArrayList<>()).add(m.group(1));
            }
        }
        return at;
    }

    private static Map<Integer, List<String>> mergePlacements(String... ss) {
        Map<Integer, List<String>> at = new TreeMap<>();
        for (String s : ss) {
            parsePlacements(s).forEach((k, v) -> at.computeIfAbsent(k, x -> new ArrayList<>()).addAll(v));
        }
        return at;
    }

    /** 主星串中某星所在地支（无则 null）。 */
    private static Integer starBranch(String s, String name) {
        if (s == null) {
            return null;
        }
        int i = s.indexOf(name + "@");
        if (i < 0) {
            return null;
        }
        int p = i + name.length() + 1;
        if (p >= s.length()) {
            return null;
        }
        int b = indexOf(ZHI, String.valueOf(s.charAt(p)));
        return b < 0 ? null : b;
    }

    private static String branchOfStar(Map<Integer, List<String>> at, String star) {
        for (Map.Entry<Integer, List<String>> e : at.entrySet()) {
            if (e.getValue().contains(star)) {
                return ZHI[e.getKey()];
            }
        }
        return null;
    }

    private static int countStar(Map<Integer, List<String>> at, String star) {
        int c = 0;
        for (List<String> v : at.values()) {
            if (v.contains(star)) {
                c++;
            }
        }
        return c;
    }

    // ---- 生克/庙陷/夹宫 ----

    /** 五行名（索引 0木 1火 2土 3金 4水，与 starElement / ZHI_ELEM / GAN_ELEM 同序）。 */
    private static final String[] ELEM_NAMES = {"木", "火", "土", "金", "水"};

    /**
     * 星曜落宫之五行生克制化（卷二·论星辰生克制化「星曜全明生克制化之机，次看落于何宫，如廉真属火在寅宫，
     * 乃木乡能生廉真之火…」；「金入火乡 火入水乡 水入土乡 土入木乡 俱为受制」；卷一·星垣论同旨）。
     *
     * <p>以宫位地支五行（{@link #ZHI_ELEM}）对星曜五行（{@link #starElement}）作生/克/受制判定，
     * 对应本体 {@code state:PhaseRelation} / {@code state:phaseRelationType}（生/克/制/化）。
     * 相生序 木→火→土→金→水→木；相克序 木克土、土克水、水克火、火克金、金克木。
     * 星曜无五行者返回 null。
     *
     * @return 形如「廉贞(火)在寅(木)→宫生星（得生）」/「武曲(金)在午(火)→宫克星（受制：金入火乡）」
     */
    private static String palaceStarRelation(String star, int branchIdx) {
        int se = starElement(star);
        if (se < 0 || branchIdx < 0 || branchIdx > 11) {
            return null;
        }
        int pe = ZHI_ELEM[branchIdx];
        String base = star + "(" + ELEM_NAMES[se] + ")在" + ZHI[branchIdx] + "(" + ELEM_NAMES[pe] + ")";
        if (pe == se) {
            return base + "→比和";
        }
        if ((pe + 1) % 5 == se) {          // 宫生星（得生）：木乡生廉贞火
            return base + "→宫生星（得生）";
        }
        if (se == (pe + 2) % 5) {          // 宫克星（受制）：金入火乡 / 火入水乡 / 水入土乡 / 土入木乡
            return base + "→宫克星（受制：" + ELEM_NAMES[se] + "入" + ELEM_NAMES[pe] + "乡）";
        }
        if ((se + 1) % 5 == pe) {          // 星生宫（泄气）
            return base + "→星生宫（泄气）";
        }
        if (pe == (se + 2) % 5) {          // 星克宫
            return base + "→星克宫";
        }
        return base + "→无明显生克";
    }

    /** 星辰五行（卷一·諸星問答論；卷二·星辰屬性）。 */
    private static int starElement(String star) {
        switch (star) {
            case "紫微": case "天府": case "天梁": case "左辅": case "禄存": return 2; // 土
            case "天机": case "贪狼": return 0;                                        // 木
            case "太阳": case "廉贞": case "天魁": case "天钺":
            case "火星": case "铃星": case "天马": case "地劫": case "天空": return 1;   // 火
            case "武曲": case "七杀": case "文昌": case "擎羊": case "陀罗": return 3;   // 金
            case "天同": case "太阴": case "巨门": case "天相": case "破军":
            case "文曲": case "右弼": return 4;                                        // 水
            default: return -1;
        }
    }

    /** 星辰庙陷等第（卷二·星辰庙陷）：星 × 宫支 → 庙/旺/得地/利益/平和/不得地/陷；表外之星返回「平和」。 */
    private static String brightnessOf(String star, int branchIdx) {
        int si = indexOf(BRIGHTNESS_STARS, star);
        if (si < 0 || branchIdx < 0 || branchIdx > 11) {
            return "平和";
        }
        return BRIGHTNESS[si][branchIdx];
    }

    /** 是否落陷（陷/不得地）。 */
    private static boolean isFallen(String star, int branchIdx) {
        String b = brightnessOf(star, branchIdx);
        return "陷".equals(b) || "不得地".equals(b);
    }

    /** 是否十四主星之一。 */
    private static boolean isMainStar(String star) {
        return indexOf(ZIWEI_SERIES, star) >= 0 || indexOf(TIANFU_SERIES, star) >= 0;
    }

    /** 该星于指定宫支是否落闲（卷二·定十二宫星辰落闲）。 */
    private static boolean isIdleStar(String star, int branchIdx) {
        if (branchIdx < 0) {
            return false;
        }
        String br = ZHI[branchIdx];
        for (String[] row : IDLE_STAR) {
            if (!row[0].equals(star)) {
                continue;
            }
            for (int i = 1; i < row.length; i++) {
                if (row[i].equals(br)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** 该星是否列于「定十二宫星辰落闲」规范表（8 星）。 */
    private static boolean inIdleTable(String star) {
        for (String[] row : IDLE_STAR) {
            if (row[0].equals(star)) {
                return true;
            }
        }
        return false;
    }

    /** 该星「落闲宫」断语（卷二·十二宫各星吉凶诀原文；无则 null）。 */
    private static String[] idleVerseOf(String star) {
        for (String[] row : IDLE_VERSE) {
            if (row[0].equals(star)) {
                return row;
            }
        }
        return null;
    }

    /** 三方四正：支是否在本宫之三方四正（本宫+三合二宫+对宫）。 */
    private static boolean inSanFang(int palaceIdx, String branchStr) {
        if (branchStr == null) {
            return false;
        }
        int b = indexOf(ZHI, branchStr);
        if (b < 0) {
            return false;
        }
        return b == palaceIdx || b == floorMod(palaceIdx + 4, 12)
                || b == floorMod(palaceIdx + 8, 12) || b == floorMod(palaceIdx + 6, 12);
    }

    /** 夹宫诸格（卷三·諸格断语）：紫府/日月/昌曲/羊陀/火铃/魁钺/左右/劫空 夹命/夹身。 */
    private static void clampPattern(StringBuilder sb, Map<Integer, List<String>> at, int idx, String label) {
        int left = floorMod(idx - 1, 12);
        int right = floorMod(idx + 1, 12);
        checkClamp(sb, at, left, right, "紫微", "天府", "紫府夹" + label);
        checkClamp(sb, at, left, right, "太阳", "太阴", "日月夹" + label);
        checkClamp(sb, at, left, right, "文昌", "文曲", "昌曲夹" + label);
        checkClamp(sb, at, left, right, "擎羊", "陀罗", "羊陀夹" + label);
        checkClamp(sb, at, left, right, "火星", "铃星", "火铃夹" + label);
        checkClamp(sb, at, left, right, "天魁", "天钺", "魁钺夹" + label);
        checkClamp(sb, at, left, right, "左辅", "右弼", "左右夹" + label);
        checkClamp(sb, at, left, right, "地劫", "天空", "劫空夹" + label);
    }

    private static void checkClamp(StringBuilder sb, Map<Integer, List<String>> at, int left, int right,
                                   String s1, String s2, String label) {
        List<String> l = at.getOrDefault(left, List.of());
        List<String> r = at.getOrDefault(right, List.of());
        if ((l.contains(s1) && r.contains(s2)) || (l.contains(s2) && r.contains(s1))) {
            sb.append(label).append("（").append(ZHI[left]).append("-").append(ZHI[right]).append("）；");
        }
    }

    private static boolean isHuaInMing(int mingIdx, Map<Integer, List<String>> at, String fourTransform) {
        if (fourTransform == null) {
            return false;
        }
        for (String st : at.getOrDefault(mingIdx, List.of())) {
            if (fourTransform.contains(st + "化禄") || fourTransform.contains(st + "化权")
                    || fourTransform.contains(st + "化科")) {
                return true;
            }
        }
        return false;
    }

    /** 命宫三方四正（本宫＋三合二宫＋对宫）是否坐科/权/禄。 */
    private static boolean isHuaInSanFang(int mingIdx, Map<Integer, List<String>> at, String fourTransform) {
        if (fourTransform == null) {
            return false;
        }
        int[] box = {mingIdx, floorMod(mingIdx + 4, 12), floorMod(mingIdx + 8, 12), floorMod(mingIdx + 6, 12)};
        for (int b : box) {
            for (String st : at.getOrDefault(b, List.of())) {
                if (fourTransform.contains(st + "化禄") || fourTransform.contains(st + "化权")
                        || fourTransform.contains(st + "化科")) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 命宫三方四正吉曜会照布尔判定〔释读：以实际星曜盘对「会吉/会煞」逐组核验〕：
     * 对 昌曲／辅弼／魁钺／禄马／科权禄 五组吉曜及「三方无凶杀」逐项给出布尔「是/否」，
     * 并统计会吉/会煞数，给出「会吉（三方无凶杀）/会煞偏重/吉煞交参/中和」综合——
     * 为定盘/成格提供客观可复算依据（依《全書》「三方四正会照」通例，非新增断法，不改第一权威）。
     */
    static String judgeSanFangJiYao(final int mingIdx, final Map<Integer, List<String>> at, final String fourTransform) {
        if (mingIdx < 0) {
            return "命宫三方四正吉曜会照〔布尔〕：命宫未定，不判";
        }
        Set<String> sf = new HashSet<>();
        for (int b : new int[]{mingIdx, floorMod(mingIdx + 4, 12), floorMod(mingIdx + 8, 12), floorMod(mingIdx + 6, 12)}) {
            sf.addAll(at.getOrDefault(b, List.of()));
        }
        boolean changQu = sf.contains("文昌") && sf.contains("文曲");
        boolean fuBi = sf.contains("左辅") && sf.contains("右弼");
        boolean kuiYue = sf.contains("天魁") && sf.contains("天钺");
        boolean luMa = sf.contains("禄存") && sf.contains("天马");
        boolean keQuanLu = isHuaInSanFang(mingIdx, at, fourTransform);
        int ji = 0;
        for (String s : YUNXIAN_JI_STARS) {
            if (sf.contains(s)) {
                ji++;
            }
        }
        int sha = 0;
        for (String s : YUNXIAN_SHA_STARS) {
            if (sf.contains(s)) {
                sha++;
            }
        }
        String hezong = sha == 0 && ji > 0 ? "会吉（三方无凶杀）"
                : (sha > ji ? "会煞偏重" : (ji > 0 ? "吉煞交参" : "中和（无吉无煞）"));
        return "命宫三方四正吉曜会照〔布尔〕：昌曲会命=" + yn(changQu)
                + "；辅弼拱命=" + yn(fuBi)
                + "；魁钺坐拱=" + yn(kuiYue)
                + "；禄马交驰=" + yn(luMa)
                + "；科权禄拱=" + yn(keQuanLu)
                + "｜会吉=" + ji + "、会煞=" + sha
                + "、三方无凶杀=" + yn(sha == 0)
                + "→综合=" + hezong;
    }

    /** 布尔→「是/否」。 */
    private static String yn(final boolean b) {
        return b ? "是" : "否";
    }

    /** 某宫（地支索引）内是否坐某类四化（huaType 如「化禄」「化科」）。 */
    private static boolean huaInPalace(int idx, Map<Integer, List<String>> at, String fourTransform, String huaType) {
        if (fourTransform == null || idx < 0) {
            return false;
        }
        for (String st : at.getOrDefault(idx, List.of())) {
            if (fourTransform.contains(st + huaType)) {
                return true;
            }
        }
        return false;
    }

    /** 某宫（地支索引）是否坐「禄」（禄存或化禄）。 */
    private static boolean palaceHasLu(int idx, Map<Integer, List<String>> at, String fourTransform) {
        return idx >= 0 && (at.getOrDefault(idx, List.of()).contains("禄存")
                || huaInPalace(idx, at, fourTransform, "化禄"));
    }

    /** 宫支六合之合宫（子丑/寅亥/卯戌/辰酉/巳申/午未）：合宫 = floorMod(13 - idx, 12)。 */
    private static int sixHePartner(int idx) {
        return idx < 0 ? -1 : floorMod(SIX_HE_SUM - idx, 12);
    }

    // --------------------------------------------------------------------------
    // 运限序列辅助（见字段区「运限序列模块」说明）
    // --------------------------------------------------------------------------
    /**
     * 运限吉凶符号：某宫（地支索引）之三方四正吉凶净差 → 符号（+1 吉 / -1 凶 / 0 平）。
     * 计入：吉星/凶星（{@link #YUNXIAN_JI_STARS}/{@link #YUNXIAN_SHA_STARS}）、主星庙旺(+)/落陷(-)、
     * 生年四化（禄权科 + / 忌 -）。
     */
    private static int yunxianFortuneSign(Map<Integer, List<String>> at, int idx, String fourTransform) {
        if (idx < 0 || idx > 11) {
            return 0;
        }
        int ji = 0;
        int sha = 0;
        for (int b : new int[]{idx, floorMod(idx + 4, 12), floorMod(idx + 8, 12), floorMod(idx + 6, 12)}) {
            List<String> stars = at.getOrDefault(b, List.of());
            for (String s : stars) {
                if (YUNXIAN_JI_STARS.contains(s)) {
                    ji++;
                }
                if (YUNXIAN_SHA_STARS.contains(s)) {
                    sha++;
                }
                if (isMainStar(s)) {
                    if (isFallen(s, b)) {
                        sha++;
                    } else if ("庙".equals(brightnessOf(s, b)) || "旺".equals(brightnessOf(s, b))) {
                        ji++;
                    }
                }
                if (fourTransform != null) {
                    if (fourTransform.contains(s + "化禄") || fourTransform.contains(s + "化权")
                            || fourTransform.contains(s + "化科")) {
                        ji++;
                    }
                    if (fourTransform.contains(s + "化忌")) {
                        sha++;
                    }
                }
            }
        }
        return Integer.compare(ji, sha);
    }

    /**
     * 流年吉凶符号（运限重算·流年实际引动星曜）：以流年支宫（太岁宫）为流年命宫，
     * 叠加流禄(+)/流羊(-)/流陀(-)与**流年四化**（流年干禄权科 + / 忌 -，{@link #SIHUA}）后取三方四正吉凶符号。
     */
    private static int annualFortuneSign(Map<Integer, List<String>> at, int flowBranch, int flowGan) {
        if (flowBranch < 0 || flowBranch > 11 || flowGan < 0 || flowGan > 9) {
            return 0;
        }
        int flowLu = LUCUN[flowGan];
        int flowYang = floorMod(flowLu + 1, 12);
        int flowTuo = floorMod(flowLu - 1, 12);
        String[] hua = SIHUA[flowGan];
        int[] huaBr = new int[4];
        for (int i = 0; i < 4; i++) {
            Integer b = branchIndexOf(at, hua[i]);
            huaBr[i] = b == null ? -1 : b;
        }
        int ji = 0;
        int sha = 0;
        for (int b : new int[]{flowBranch, floorMod(flowBranch + 4, 12),
                floorMod(flowBranch + 8, 12), floorMod(flowBranch + 6, 12)}) {
            List<String> stars = at.getOrDefault(b, List.of());
            for (String s : stars) {
                if (YUNXIAN_JI_STARS.contains(s)) {
                    ji++;
                }
                if (YUNXIAN_SHA_STARS.contains(s)) {
                    sha++;
                }
                if (isMainStar(s)) {
                    if (isFallen(s, b)) {
                        sha++;
                    } else if ("庙".equals(brightnessOf(s, b)) || "旺".equals(brightnessOf(s, b))) {
                        ji++;
                    }
                }
            }
            if (b == flowLu) {
                ji++;
            }
            if (b == flowYang || b == flowTuo) {
                sha++;
            }
            for (int i = 0; i < 3; i++) {
                if (huaBr[i] == b) {
                    ji++;
                }
            }
            if (huaBr[3] == b) {
                sha++;
            }
        }
        return Integer.compare(ji, sha);
    }

    /**
     * 卷三·论二限太岁吉凶（c4 L77-78）：以大限/小限/太岁三者之宫位与吉凶符号，
     * 判「独守吉凶」「相逢吉凶」「相冲（太岁冲大限/小限、太岁冲羊陀七杀）」。
     * 原文：「须详大限独守吉凶…小限独守…太岁独守…如岁限俱凶则凶。又看大限与小限相逢…大限逢太岁…
     *   小限逢太岁…又看太岁冲大限小限，太岁冲羊陀七杀，然后可断吉凶」。
     * 口径〔释读·运限序列〕：三宫吉凶符号（+1吉/0平/-1凶）客观复算，组合断吉凶。
     */
    private static String judgeTwoLimitsTaiSui(Map<Integer, List<String>> at, int dxPalace, int xxPalace, int tsIdx,
                                               int dxSign, int xxSign, int tsSign) {
        StringBuilder sb = new StringBuilder("论二限太岁吉凶（卷三）：");
        sb.append("大限").append(dxPalace < 0 ? "未起" : ZHI[dxPalace] + fortuneGlyph(dxSign))
                .append("｜小限").append(ZHI[xxPalace]).append(fortuneGlyph(xxSign))
                .append("｜太岁").append(ZHI[tsIdx]).append(fortuneGlyph(tsSign)).append("。");
        // 独守吉凶
        sb.append("独守：大限").append(fortuneWord(dxSign)).append("、小限").append(fortuneWord(xxSign))
                .append("、太岁").append(fortuneWord(tsSign)).append("；");
        // 岁限俱凶则凶
        boolean dxXxXiong = dxSign < 0 && xxSign < 0;
        boolean sanXiong = (dxSign <= 0 && xxSign <= 0 && tsSign <= 0);
        if (dxXxXiong) {
            sb.append("大小二限俱凶→凶；");
        }
        if (sanXiong) {
            sb.append("岁限俱凶（大限/小限/太岁皆非吉）→凶（原文「如岁限俱凶则凶」）；");
        }
        // 相逢
        if (dxPalace >= 0 && dxPalace == xxPalace) {
            sb.append("大限与小限相逢（同").append(ZHI[dxPalace]).append("宫）→").append(combineWord(dxSign, xxSign)).append("；");
        }
        if (dxPalace >= 0 && dxPalace == tsIdx) {
            sb.append("大限逢太岁（同").append(ZHI[tsIdx]).append("宫）→").append(combineWord(dxSign, tsSign)).append("；");
        }
        if (xxPalace == tsIdx) {
            sb.append("小限逢太岁（同").append(ZHI[tsIdx]).append("宫）→").append(combineWord(xxSign, tsSign)).append("；");
        }
        // 相冲（对宫）
        int tsOpp = floorMod(tsIdx + 6, 12);
        if (dxPalace >= 0 && dxPalace == tsOpp) {
            sb.append("太岁冲大限（太岁").append(ZHI[tsIdx]).append("冲大限").append(ZHI[dxPalace]).append("）→动；");
        }
        if (xxPalace == tsOpp) {
            sb.append("太岁冲小限（太岁").append(ZHI[tsIdx]).append("冲小限").append(ZHI[xxPalace]).append("）→动；");
        }
        // 太岁冲羊陀七杀（太岁宫或其冲宫见擎羊/陀罗/七杀）
        List<String> tsStars = at.getOrDefault(tsIdx, List.of());
        List<String> tsOppStars = at.getOrDefault(tsOpp, List.of());
        boolean sheep = tsStars.contains("擎羊") || tsStars.contains("陀罗") || tsStars.contains("七杀")
                || tsOppStars.contains("擎羊") || tsOppStars.contains("陀罗") || tsOppStars.contains("七杀");
        if (sheep) {
            sb.append("太岁（或其对宫）见羊陀/七杀→太岁冲羊陀七杀；");
        }
        sb.append("（吉/凶/平按宫位三方四正吉星凶星＋主星庙陷＋生年四化＋流曜客观复算）〔释读·运限序列〕");
        return sb.toString();
    }

    /** 两符号组合断词（吉+吉→吉、凶+凶→凶、吉凶相伴→平）。 */
    private static String combineWord(int a, int b) {
        if (a > 0 && b > 0) {
            return "吉";
        }
        if (a < 0 && b < 0) {
            return "凶";
        }
        return "吉凶相伴";
    }

    /**
     * 卷三·论命先贫后富（c4 L72-74）：由大限吉凶序列判先后富贫。
     * 原文：先成后败＝「限步逢大小二限及太岁相冲照，又加凶杀守临，故此破败…所谓先成后败，先大后小也」；
     *   先贫后富＝「初历艰辛…却仍中末平地升腾…因生在中庸之局，后因限步相扶，星辰逢吉曜兼庙旺，以此突然发达进禄…所谓先贫后富，先小后大是也」。
     * 口径〔释读·依句意客观化〕：取大限十二限吉凶符号（±1/0），前段（第1-6限）与后段（第7-12限）净号对比——
     *   前净<0 且 后净>0 → 先贫后富；前净>0 且 后净<0 → 先富后贫；否则不分先后。
     */
    private static String judgeXianPinHouFu(int[] limitSigns) {
        if (limitSigns == null || limitSigns.length < 12) {
            return "论命先贫后富（卷三）：大限序列不足，不判";
        }
        int early = 0;
        int late = 0;
        for (int i = 0; i < 12; i++) {
            if (i < 6) {
                early += limitSigns[i];
            } else {
                late += limitSigns[i];
            }
        }
        if (early < 0 && late > 0) {
            return "论命先贫后富（卷三）「先小后大」：初限偏凶（净" + early + "）、后限转吉（净" + late
                    + "）→先贫后富〔释读·运限序列〕";
        }
        if (early > 0 && late < 0) {
            return "论命先贫后富（卷三）「先成后败」：初限偏吉（净" + early + "）、后限转凶（净" + late
                    + "）→先富后贫〔释读·运限序列〕";
        }
        return "论命先贫后富（卷三）：本盘前后限净号（前" + early + "/后" + late + "）未呈单向转折，不分先后";
    }

    /**
     * 步数无依（卷一·定杂局「前限接后限连绵不分是也」）：
     * 大限序列十二限吉凶同号、全序无转折（含符号者全为吉或全为凶，无中性断点）→ true。
     */
    private static boolean isBuShuWuYi(int[] limitSigns) {
        if (limitSigns == null || limitSigns.length == 0) {
            return false;
        }
        int first = limitSigns[0];
        for (int s : limitSigns) {
            if (s == 0 || s != first) {
                return false;
            }
        }
        return true;
    }

    /**
     * 水上驾星（卷一·定杂局「一年好一年不好是也」）：
     * 流年序列吉凶严格交替（相邻年符号相异且皆非中性）→ true。要求至少 4 个连续流年以成「交替」之势。
     */
    private static boolean isShuiShangJiaXing(int[] yearSigns) {
        if (yearSigns == null || yearSigns.length < 4) {
            return false;
        }
        for (int i = 0; i + 1 < yearSigns.length; i++) {
            if (yearSigns[i] == 0 || yearSigns[i + 1] == 0 || yearSigns[i] == yearSigns[i + 1]) {
                return false;
            }
        }
        return true;
    }

    // --------------------------------------------------------------------------
    // 运限重算辅助（大限/流年实际引动星曜）与竹萝三限（卷二·定男女竹萝三限）
    // --------------------------------------------------------------------------
    /** 宫支之宫干索引（五虎遁·卷二·起五行寅例：年干起寅宫干，顺布十二宫）。 */
    private static int palaceStemIndex(int branch, int yearGan) {
        int yinGan = WUHU_YIN_GAN[yearGan % 5];
        return floorMod(yinGan + floorMod(branch - BRANCH_YIN, 12), 10);
    }

    /** 大限宫（依虚龄 age；未起限 age&lt;起限岁 返回 -1）。 */
    private static int daXianPalace(int age, int startAge, int startPalace, boolean forward) {
        if (age < startAge) {
            return -1;
        }
        int i = Math.min(11, (age - startAge) / 10);
        return floorMod(startPalace + (forward ? i : -i), 12);
    }

    /** 小限宫（依虚龄；卷二·安小限诀：寅午戌起辰/申子辰起戌/巳酉丑起未/亥卯未起丑，男顺女逆）。 */
    private static int xiaoXianPalace(int age, int birthBranch, boolean male) {
        int s = SMALL_LIMIT_START[birthBranch];
        return floorMod(s + (male ? age - 1 : -(age - 1)), 12);
    }

    /** 竹萝三限（卷二·定男女竹萝三限「三方四正七杀破军俱作竹萝三限」）：某宫三方四正七杀破军俱见。 */
    private static boolean isZhuLuoSanXian(int idx, Map<Integer, List<String>> at) {
        if (idx < 0 || idx > 11) {
            return false;
        }
        boolean qiSha = false;
        boolean poJun = false;
        for (int b : new int[]{idx, floorMod(idx + 4, 12), floorMod(idx + 8, 12), floorMod(idx + 6, 12)}) {
            List<String> l = at.getOrDefault(b, List.of());
            if (l.contains("七杀")) {
                qiSha = true;
            }
            if (l.contains("破军")) {
                poJun = true;
            }
        }
        return qiSha && poJun;
    }

    /** 竹萝三限加重判据：「再加巨暗凶星便作三方四正定议」——三方四正见巨门（暗）或煞星。 */
    private static boolean hasJuMenOrSha(int idx, Map<Integer, List<String>> at) {
        if (idx < 0 || idx > 11) {
            return false;
        }
        for (int b : new int[]{idx, floorMod(idx + 4, 12), floorMod(idx + 8, 12), floorMod(idx + 6, 12)}) {
            List<String> l = at.getOrDefault(b, List.of());
            if (l.contains("巨门")) {
                return true;
            }
            for (String s : l) {
                if (YUNXIAN_SHA_STARS.contains(s)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** 流月命宫（卷二·安斗君诀「即月将星是也」）：斗君为正月命宫，逐月顺行一宫。 */
    private static int flowMonthPalace(int douJunIdx, int month) {
        return floorMod(douJunIdx + (month - 1), 12);
    }

    /** 流日命宫〔外部通行法·非《全書》原文：《全書》仅言「日值恶杀」，未立流日起例〕：流月命宫起初一，逐日顺行一宫。 */
    private static int flowDayPalace(int flowMonthPalace, int day) {
        return floorMod(flowMonthPalace + (day - 1), 12);
    }

    /** 运限吉凶符号之文字（吉/凶/平）。 */
    private static String fortuneGlyph(int s) {
        return s > 0 ? "吉" : s < 0 ? "凶" : "平";
    }

    /** 运限吉凶符号之全称（吉/凶/平）。 */
    private static String fortuneWord(int s) {
        return s > 0 ? "全吉" : s < 0 ? "全凶" : "全平";
    }

    // --------------------------------------------------------------------------
    // 重大事件·逐流年回推（subjectTraits 结构化事件表）〔外部通行法·释读·非《全書》原文〕
    //   《全書》无「年份+事件→事项宫回溯校验」成条论述；此据实务通行法：由自述事件的年份取流年干支，
    //   按「事件性质→本命事项宫」定位该年该宫（流年命宫＝流年支，他宫逆布），重算三方四正吉凶符号与流年四化，
    //   仅供验盘/复盘参考，不替代人工定盘（不改《全書》第一权威）。
    // --------------------------------------------------------------------------
    /** 事件关键词 → {本命事项宫索引(PALACE_NAME), 期望吉凶(+1吉/-1凶/0中性)}；未识别返回 null。 */
    private static int[] eventPalaceExpectation(final String seg) {
        if (seg == null) {
            return null;
        }
        if (seg.matches(".*(结婚|婚姻|嫁|娶|配偶|订婚).*")) return new int[]{2, 1};      // 夫妻
        if (seg.matches(".*(离婚|离异|丧偶|分居).*")) return new int[]{2, -1};             // 夫妻（凶）
        if (seg.matches(".*(生子|生育|子女|怀孕|流产|添丁).*")) return new int[]{3, 1};     // 子女
        if (seg.matches(".*(丧父|丧母|父亲|母亲|父母|双亲|父亡|母亡).*")) return new int[]{11, -1}; // 父母
        if (seg.matches(".*(升职|升迁|晋升|事业|工作|创业|就业|调动|退休|公职|任职|考上).*")) return new int[]{8, 1}; // 官禄
        if (seg.matches(".*(财运|破财|投资|发财|亏损|负债|中奖|进财|耗财).*")) {
            boolean loss = seg.matches(".*(破财|亏损|负债|耗财).*");
            return new int[]{4, loss ? -1 : 1};                                             // 财帛
        }
        if (seg.matches(".*(搬家|迁移|迁居|出国|移民|出行|远行|留学).*")) return new int[]{6, 0}; // 迁移
        if (seg.matches(".*(疾病|住院|手术|生病|重病|受伤|灾).*")) return new int[]{5, -1};  // 疾厄
        if (seg.matches(".*(兄弟|姐妹|手足).*")) return new int[]{1, 0};                  // 兄弟
        if (seg.matches(".*(购房|置产|买房|置业|置地).*")) return new int[]{9, 1};         // 田宅
        if (seg.matches(".*(考试|毕业|升学|学业|考取).*")) return new int[]{8, 1};         // 官禄（学业）
        if (seg.matches(".*(合伙|交友|友人|结拜).*")) return new int[]{7, 0};             // 奴仆
        return null;
    }

    /**
     * 重大事件逐流年回推：解析 subjectTraits 中「年份+事件」，逐事件定位本命事项宫在该流年的宫支，
     * 重算三方四正吉凶符号与流年四化，给出「事件期望 vs 盘面吉凶」的合/待核参考。
     * 无结构化事件返回 null。全部标注〔外部通行法·释读·非《全書》原文〕。
     */
    static String majorEventBacktrace(final String traits, final Map<Integer, List<String>> at) {
        if (traits == null || traits.isBlank()) {
            return null;
        }
        // 按事件分隔符切块，避免相邻事件的年份窗口交叉导致误归类
        java.util.regex.Pattern yp = java.util.regex.Pattern.compile("(19\\d{2}|20\\d{2})");
        StringBuilder sb = new StringBuilder();
        int n = 0;
        String[] chunks = traits.split("[；;。\\n]");
        for (String chunk : chunks) {
            if (chunk == null || chunk.isBlank()) {
                continue;
            }
            String seg = chunk.trim();
            java.util.regex.Matcher ym = yp.matcher(seg);
            while (ym.find()) {
                int year = Integer.parseInt(ym.group(1));
                int[] pe = eventPalaceExpectation(seg);
                if (pe == null) {
                    continue;
                }
                int flowBranch = branchOfYear(year);
                int flowGan = ganOfYear(year);
                int targetBranch = floorMod(flowBranch - pe[0], 12); // 流年命宫＝流年支，他宫逆布
                int sign = annualFortuneSign(at, targetBranch, flowGan);
                String glyph = fortuneGlyph(sign);
                String match;
                if (pe[1] > 0) {
                    match = sign > 0 ? "合（盘吉）" : (sign < 0 ? "待核（盘凶·与期望逆）" : "待核（盘平）");
                } else if (pe[1] < 0) {
                    match = sign < 0 ? "合（盘凶）" : (sign > 0 ? "待核（盘吉·与期望逆）" : "待核（盘平）");
                } else {
                    match = "中性事项·参考";
                }
                // 流年四化（依该事件年份之流年干）引动
                String[] hua = SIHUA[flowGan];
                String[] huaName = {"禄", "权", "科", "忌"};
                StringBuilder hb = new StringBuilder();
                for (int i = 0; i < 4; i++) {
                    Integer b = branchIndexOf(at, hua[i]);
                    hb.append(hua[i]).append("化").append(huaName[i]).append("→").append(b == null ? "—" : ZHI[b]).append("；");
                }
                n++;
                sb.append(year).append("（").append(GAN[flowGan]).append(ZHI[flowBranch]).append("）")
                        .append("事项宫＝").append(PALACE_NAME[pe[0]]).append("在").append(ZHI[targetBranch])
                        .append("〔流年三方四正=").append(glyph).append("｜").append(match).append("〕")
                        .append("｜流年四化（").append(GAN[flowGan]).append("）：").append(hb).append("｜原述片段「")
                        .append(seg.replace("\n", " ").trim()).append("」；");
            }
        }
        if (n == 0) {
            return null;
        }
        return "重大事件逐流年回推〔外部通行法·释读·非《全書》原文；仅供验盘/复盘参考，不替代人工定盘〕：共 " + n + " 项。"
                + sb;
    }

    // --------------------------------------------------------------------------
    // 空宫借星（I·62）与四盘叠合（I·51）〔外部通行法·非《全書》原文〕
    //   《全書》语料 c0–c4 检索无成条「借星」「叠盘」口诀；此处采后世通行断法客观落地，
    //   逐处标注来源、不改《全書》第一权威（★8/10 可追溯）。
    // --------------------------------------------------------------------------
    /**
     * 空宫借星〔外部通行法·非《全書》原文〕：空宫借对宫之**十四主星**入本宫论断
     * （辅弼昌曲魁钺禄马等辅煞杂曜不借；《全書》未见成条借星口诀，属后世通行说法）。
     */
    private static List<String> borrowMainStars(Map<Integer, List<String>> at, int idx) {
        int opp = floorMod(idx + 6, 12);
        List<String> borrowed = new ArrayList<>();
        for (String s : at.getOrDefault(opp, List.of())) {
            if (isMainStar(s)) {
                borrowed.add(s);
            }
        }
        return borrowed;
    }

    /**
     * 借光〔外部通行法·非《全書》原文〕：借入主星之庙旺落陷**随对宫原地**（非在本宫重新安星），
     * 并注明四化随星借入（仍以本盘生年四化论）。对宫亦空（无主星）时返回「对宫亦空」标注。
     */
    private static String borrowedBrightness(Map<Integer, List<String>> at, int idx) {
        int opp = floorMod(idx + 6, 12);
        List<String> borrowed = borrowMainStars(at, idx);
        if (borrowed.isEmpty()) {
            return "【对宫亦空·无可借主星】";
        }
        StringBuilder sb = new StringBuilder();
        for (String s : borrowed) {
            sb.append(s).append("@").append(ZHI[opp]).append(brightnessOf(s, opp)).append("；");
        }
        return sb.toString().trim();
    }

    /**
     * 四盘叠合〔外部通行法·非《全書》原文：本命为体、大限/流年/流月/流日为用，钦天四化体用断法〕：
     * 以本命宫为体、逐层叠入大限宫（十年主题）→流年宫（触发）→流月宫→流日宫，落宫星曜合断。
     * 未定位之层（-1：如未起限、未给月/日）跳过。
     */
    private static String overlayStack(int mingIdx, int dxIdx, int flowYearIdx, int flowMonthIdx, int flowDayIdx,
                                       Map<Integer, List<String>> at) {
        StringBuilder sb = new StringBuilder("四盘叠合〔外部通行法·非《全書》原文：本命为体、大限/流年/流月/流日为用〕：");
        sb.append("本命体@").append(ZHI[mingIdx]).append("[")
                .append(String.join("", at.getOrDefault(mingIdx, List.of()))).append("]");
        if (dxIdx >= 0) {
            sb.append(" → 大限用@").append(ZHI[dxIdx]).append("[")
                    .append(String.join("", at.getOrDefault(dxIdx, List.of()))).append("]");
        } else {
            sb.append(" → 大限用（未起限）");
        }
        sb.append(" → 流年用@").append(ZHI[flowYearIdx]).append("[")
                .append(String.join("", at.getOrDefault(flowYearIdx, List.of()))).append("]");
        if (flowMonthIdx >= 0) {
            sb.append(" → 流月用@").append(ZHI[flowMonthIdx]).append("[")
                    .append(String.join("", at.getOrDefault(flowMonthIdx, List.of()))).append("]");
        }
        if (flowDayIdx >= 0) {
            sb.append(" → 流日用@").append(ZHI[flowDayIdx]).append("[")
                    .append(String.join("", at.getOrDefault(flowDayIdx, List.of()))).append("]");
        }
        sb.append("；体（本命格局）定基调、用（限·年·月·日）应凶吉（叠宫断法·H·48：吉曜临限则吉、煞曜临限则凶）。");
        return sb.toString();
    }

    // --------------------------------------------------------------------------
    // 验盘（定盘）多维比对加权〔外部通行法·非《全書》原文〕——节点 V verifyChart（I·61）
    // --------------------------------------------------------------------------
    /** 命宫支（依生月、生时支）——卷二·安身命例「寅起正月顺数至生月，再起子时逆数至生时」。 */
    private static int lifePalaceBranch(int month, int hourBranch) {
        return floorMod(BRANCH_YIN + (month - 1) - hourBranch, 12);
    }

    /** 身宫支（依生月、生时支）——自生月宫起子时顺数至生时。 */
    private static int bodyPalaceBranch(int month, int hourBranch) {
        return floorMod(BRANCH_YIN + (month - 1) + hourBranch, 12);
    }

    /** 验盘候补时辰：原始时辰及其前后各一（跨时界不确定之常用候补范围）。〔外部通行法〕 */
    private static int[] hourCandidates(int hourBranch) {
        return new int[]{floorMod(hourBranch - 1, 12), hourBranch, floorMod(hourBranch + 1, 12)};
    }

    /** 命宫主星之验盘特征关键词（表外之星返回空）。 */
    private static List<String> verifyStarTraits(String star) {
        for (String[] row : VERIFY_STAR_TRAITS) {
            if (row[0].equals(star)) {
                return Arrays.asList(row).subList(1, row.length);
            }
        }
        return List.of();
    }

    /** 五行局名之验盘体质关键词（表外返回空）。 */
    private static List<String> verifyBureauTraits(String name) {
        for (String[] row : VERIFY_BUREAU_TRAITS) {
            if (row[0].equals(name)) {
                return Arrays.asList(row).subList(1, row.length);
            }
        }
        return List.of();
    }

    /** 验盘特征词全集（供自述文本抽取用）。 */
    private static Set<String> verifyTraitUniverse() {
        Set<String> u = new HashSet<>();
        for (String[] row : VERIFY_STAR_TRAITS) {
            u.addAll(Arrays.asList(row).subList(1, row.length));
        }
        for (String[] row : VERIFY_BUREAU_TRAITS) {
            u.addAll(Arrays.asList(row).subList(1, row.length));
        }
        u.add(VERIFY_CHANGQU_MOLE);
        return u;
    }

    /** 自述文本 → 命中的已知特征关键词集（与验盘特征词表取交）。 */
    private static Set<String> extractStatedTraits(String text, Set<String> universe) {
        Set<String> hit = new HashSet<>();
        if (text == null || text.isBlank()) {
            return hit;
        }
        for (String kw : universe) {
            if (text.contains(kw)) {
                hit.add(kw);
            }
        }
        return hit;
    }

    /** 某候选时辰之命宫主星（以五行局+生日重排紫微定位，取命宫支所落十四主星）。 */
    private static List<String> candidateMingStars(int month, int day, int hourBranch, int yearGan) {
        int ming = lifePalaceBranch(month, hourBranch);
        int mingGan = floorMod(WUHU_YIN_GAN[yearGan % 5] + floorMod(ming - BRANCH_YIN, 12), 10);
        int bureau = bureauOf(mingGan, ming);
        List<String> stars = new ArrayList<>();
        if (bureau < 2 || bureau > 6 || day < 1 || day > 30) {
            return stars;
        }
        int ziweiIdx = ZIWEI_TABLE[bureau - 2][day - 1];
        int tianfuIdx = floorMod(4 - ziweiIdx, 12);
        for (int i = 0; i < ZIWEI_SERIES.length; i++) {
            if (floorMod(ziweiIdx + ZIWEI_SERIES_OFF[i], 12) == ming) {
                stars.add(ZIWEI_SERIES[i]);
            }
        }
        for (int i = 0; i < TIANFU_SERIES.length; i++) {
            if (floorMod(tianfuIdx + TIANFU_SERIES_OFF[i], 12) == ming) {
                stars.add(TIANFU_SERIES[i]);
            }
        }
        return stars;
    }

    /**
     * 验盘候补评分〔外部通行法·非《全書》原文〕：以候补时辰重排命宫，取该盘
     * 「命宫主星特征(权重3) + 时系星昌曲坐命/身→痣癍(权重2) + 五行局体质(权重1)」
     * 与命主自述特征之交集加权计分，并入明细 {@code detail}。
     */
    private static int verifyCandidateScore(int month, int day, int hourBranch, int yearGan,
                                            Set<String> stated, StringBuilder detail) {
        int score = 0;
        int ming = lifePalaceBranch(month, hourBranch);
        int shen = bodyPalaceBranch(month, hourBranch);
        // 命宫主星特征（权重 3）
        for (String s : candidateMingStars(month, day, hourBranch, yearGan)) {
            for (String kw : verifyStarTraits(s)) {
                if (stated.contains(kw)) {
                    score += 3;
                    detail.append(s).append('+').append(kw).append("(3);");
                }
            }
        }
        // 时系星：文昌/文曲 落命宫或身宫 → 面上有痣（权重 2）
        int chang = floorMod(10 - hourBranch, 12);   // 文昌：戌上起子逆至生时
        int qu = floorMod(4 + hourBranch, 12);       // 文曲：辰上起子顺至生时
        if ((chang == ming || chang == shen || qu == ming || qu == shen) && stated.contains(VERIFY_CHANGQU_MOLE)) {
            score += 2;
            detail.append("昌曲入命身+").append(VERIFY_CHANGQU_MOLE).append("(2);");
        }
        // 五行局体质（权重 1）
        int mingGan = floorMod(WUHU_YIN_GAN[yearGan % 5] + floorMod(ming - BRANCH_YIN, 12), 10);
        String bn = bureauName(bureauOf(mingGan, ming));
        for (String kw : verifyBureauTraits(bn)) {
            if (stated.contains(kw)) {
                score += 1;
                detail.append(bn).append('+').append(kw).append("(1);");
            }
        }
        return score;
    }

    /** 解析候选时辰串（「卯,辰,巳」）→ 地支索引数组；空则回退 默认时辰±1（{@link #hourCandidates}）。 */
    private static int[] parseCandidateHours(String s, int defaultHour) {
        if (s == null || s.isBlank()) {
            return hourCandidates(defaultHour);
        }
        List<Integer> list = new ArrayList<>();
        for (String tok : s.split("[,，、\\s]+")) {
            int idx = indexOf(ZHI, tok.replace("时", "").trim());
            if (idx >= 0) {
                list.add(idx);
            }
        }
        if (list.isEmpty()) {
            return hourCandidates(defaultHour);
        }
        int[] arr = new int[list.size()];
        for (int i = 0; i < arr.length; i++) {
            arr[i] = list.get(i);
        }
        return arr;
    }

    /** 某星所在地支索引（无则 null）。 */
    private static Integer branchIndexOf(Map<Integer, List<String>> at, String star) {
        String br = branchOfStar(at, star);
        return br == null ? null : indexOf(ZHI, br);
    }

    /** 命格高低（卷一·定富局/定贵局/定贫贱局 + 论人命入格/格星数高下/定富贵贫贱十等）。 */
    private static String gradeOf(List<String> hit, Map<Integer, List<String>> at, int mingIdx) {
        boolean gui = hit.stream().anyMatch(h -> containsAny(h, "贵局", "紫府朝垣", "府相朝垣", "日月并明",
                "科权禄", "左右朝垣", "文星朝命", "君臣庆会", "火贵", "对面朝斗", "石中隐玉", "兼文武"));
        boolean fu = hit.stream().anyMatch(h -> containsAny(h, "富局", "火贪", "铃贪", "日月同临"));
        boolean jian = hit.stream().anyMatch(h -> containsAny(h, "贫贱局"));
        if (gui) {
            return "上格（贵局）：命格较高，入格之星数高（卷一·定贵局/论格星数高下）";
        }
        if (fu) {
            return "中上格（富局）：主富（卷一·定富局）";
        }
        if (jian) {
            return "下格（贫贱局）：多凶少吉（卷一·定贫贱局）";
        }
        return hit.isEmpty() ? "常格（未成诸格）" : "中格（杂局，卷一·定杂局）";
    }

    /** 加吉曜（辅弼昌曲魁钺）。 */
    private static final String[] JIA_JI_STARS = {"左辅", "右弼", "文昌", "文曲", "天魁", "天钺"};

    /** 煞曜（羊陀火铃）。 */
    private static final String[] SHA_STARS = {"擎羊", "陀罗", "火星", "铃星"};

    /**
     * 卷三·谈星要论「看命总纲」（c4 L22-29）＋「论人命入格」（c4 L30-36）命格等第（合参）。
     * 判据：命宫正曜庙旺/落陷、科权禄守命、加吉（辅弼昌曲魁钺）、加杀化忌（羊陀火铃/化忌）、是否入格（成诸格）。
     */
    private static String judgeMingGeGrade(List<String> hit, Set<String> mingMain, Set<String> sanFang,
            Map<Integer, List<String>> at, int mingIdx, String fourTransform) {
        boolean ruGe = !hit.isEmpty();
        boolean miaoWang = mingMain.stream().anyMatch(s -> {
            String b = brightnessOf(s, mingIdx);
            return "庙".equals(b) || "旺".equals(b);
        });
        boolean xianDi = mingMain.stream().anyMatch(s -> isFallen(s, mingIdx));
        boolean keQuanLuMing = isHuaInMing(mingIdx, at, fourTransform);
        boolean keQuanLuSanFang = isHuaInSanFang(mingIdx, at, fourTransform);
        boolean jiaJi = java.util.Arrays.stream(JIA_JI_STARS).anyMatch(sanFang::contains);
        boolean jiaSha = java.util.Arrays.stream(SHA_STARS).anyMatch(sanFang::contains)
                || (fourTransform != null && fourTransform.contains("化忌"));

        if (xianDi && jiaSha) {
            return "下格之命（贫贱）：居陷地又加杀化忌（卷三·论人命入格/看命总纲）";
        }
        if (ruGe && miaoWang && keQuanLuMing) {
            return "上上之命：命入格·庙旺聚吉·科权禄守（卷三·论人命入格）";
        }
        if (!miaoWang && jiaJi && (keQuanLuMing || keQuanLuSanFang)) {
            return "上次之命：不入庙加吉化·科权禄（卷三·论人命入格/看命总纲）";
        }
        if (miaoWang && !jiaJi) {
            return "平等：入庙不加吉（卷三·论人命入格）";
        }
        if (!miaoWang && !jiaJi) {
            return "平常命：不入庙不加吉（卷三·论人命入格）";
        }
        return "中格：吉凶相伴·三方中等星辰（卷三·看命总纲）";
    }

    /**
     * 卷三「论格星数高下」（c4 L37-54）——「星」维三档（格局高低）。
     * 原文九等由「星（格局高低）×数（生克制化之数）」合成；「数」原文仅「阴阳相伴/数相生」等抽象语，
     * 无客观算法 → 数维标〔待考·释读〕，此处给出星维三档及其对应九等区间。
     * 外部比对（第十五轮联网）：通行释读多以「数＝宫」、「阴阳＝气机清浊」（18zw.com 宁雪天注：宫位与星曜阴阳相合之说「大谬」）；
     *   shunshi.ai 诚实指出原文对「数」未展开定义、不替古人编解释。诸说不一且原文无算法 → 维持取文不臆断。
     */
    private static String judgeGeXingShu(List<String> hit) {
        boolean shang = hit.stream().anyMatch(h -> containsAny(h, "贵局", "紫府朝垣", "府相朝垣", "日月并明",
                "科权禄", "左右朝垣", "文星朝命", "君臣庆会", "火贵", "对面朝斗", "石中隐玉", "兼文武"));
        boolean xia = hit.stream().anyMatch(h -> containsAny(h, "贫贱局"));
        String xing;
        String zone;
        if (shang) {
            xing = "上格（格局高）";
            zone = "第一~第三（位至极品之贵/三公/六卿）";
        } else if (xia) {
            xing = "下格（格局低）";
            zone = "第七~第九（富比陶朱/衣食无亏/辛苦奔波）";
        } else {
            xing = "中格（格局中）";
            zone = "第四~第六（监司/县令/异路前程）";
        }
        return "论格星数高下（卷三）：星得" + xing + "，依原文九等当在" + zone
                + "；数维（阴阳相伴/数相生相克）原文未明·〔待考·释读〕"
                + "（外部通行释读：数＝宫、阴阳＝气机清浊，然原文未展开定义，取文不臆断〔外部通行法·非《全書》原文〕）";
    }

    /**
     * 卷三「论人生时安命吉凶」（c4 L66-67）：六阳时（寅午戌申子辰）安命在六阳宫吉；
     * 六阴时（巳酉丑亥卯未）安命在六阴宫吉；反此则少遂。
     */
    private static String judgeAnMingJiXiong(int hourBranch, int mingIdx) {
        boolean hourYang = (hourBranch % 2 == 0);   // 子寅辰午申戌＝偶索引＝六阳支
        boolean mingYang = (mingIdx % 2 == 0);
        boolean ji = (hourYang == mingYang);
        return "论人生时安命吉凶（卷三）：生" + (hourYang ? "六阳时（寅午戌申子辰）" : "六阴时（巳酉丑亥卯未）")
                + "，安命" + ZHI[mingIdx] + "宫（" + (mingYang ? "阳" : "阴") + "）→"
                + (ji ? "吉（命安同组六宫）" : "少遂（反此）");
    }

    /** 卷三「论小儿克亲」（c4 L70-71）：按生年支×生时支判刑克父母（原文条件可交叠，多断并见）。 */
    private static String judgeKeQin(int yearBranch, int hourBranch) {
        boolean y1 = yearBranch == 2 || yearBranch == 6 || yearBranch == 5 || yearBranch == 9;    // 寅午巳酉
        boolean hDui = hourBranch == 4 || hourBranch == 10 || hourBranch == 1 || hourBranch == 7;  // 辰戌丑未
        boolean hCi = hourBranch == 0 || hourBranch == 8 || hourBranch == 11 || hourBranch == 3;   // 子申亥卯
        boolean y2 = yearBranch == 2 || yearBranch == 11 || yearBranch == 5;                       // 寅亥巳
        boolean hFu = hourBranch == 6 || hourBranch == 8 || hourBranch == 9 || hourBranch == 11;   // 午申酉亥
        boolean y3 = yearBranch == 4 || yearBranch == 5 || yearBranch == 1 || yearBranch == 7;     // 辰巳丑未
        boolean hMu = hourBranch == 0 || hourBranch == 6 || hourBranch == 3 || hourBranch == 5
                || hourBranch == 11 || hourBranch == 8 || hourBranch == 9;                          // 子午卯巳亥申酉
        List<String> k = new ArrayList<>();
        if (y1 && hDui) k.add("寅午巳酉生人见辰戌丑未时最毒");
        if (y1 && hCi) k.add("（寅午巳酉生人见子申亥卯时）次之");
        if (y2 && hFu) k.add("寅亥巳生人见午申酉亥时，主先克父（出十六岁则不妨）");
        if (y3 && hMu) k.add("辰巳丑未生人见子午卯巳亥申酉时，主先克母");
        return "论小儿克亲（卷三）：" + (k.isEmpty() ? "本盘生年支×生时支未见原文所举组合（不主刑克）" : String.join("；", k));
    }

    /** 卷三·论小儿命（c4 L58-61）：命坐庙旺（大小二限未行）→ 灾少易养、父母无克；
     *  命坐恶杀缠陷弱地 → 灾多难养、刑克父母。 */
    private static String judgeXiaoErMing(int mingIdx, Map<Integer, List<String>> at) {
        if (mingIdx < 0) { return "论小儿命（卷三·c4 L58-61）：命宫未定，不判"; }
        List<String> stars = at.getOrDefault(mingIdx, List.of());
        String main = stars.stream().filter(ZiWeiDouShuOntologyJobWorker::isMainStar).findFirst().orElse(null);
        boolean hasSha = stars.stream().anyMatch(YUNXIAN_SHA_STARS::contains);
        boolean miaoWang = main != null && ("庙".equals(brightnessOf(main, mingIdx)) || "旺".equals(brightnessOf(main, mingIdx)));
        boolean fallen = main != null && isFallen(main, mingIdx);
        String r;
        if (miaoWang && !hasSha) {
            r = "命坐庙旺、无恶杀 → 灾少易养、父母无克〔客观·释读〕";
        } else if (fallen || (hasSha && (main == null || !miaoWang))) {
            r = "命坐恶杀缠陷弱地 → 灾多难养、刑克父母〔客观·释读〕";
        } else {
            r = "命宫吉凶相参（主星" + (main == null ? "无" : main) + "，"
                    + (hasSha ? "见煞" : "无煞") + "）→ 灾福平常〔客观·释读〕";
        }
        return "论小儿命（卷三·c4 L58-61）：" + r;
    }

    /** 卷三·论男女命同异（c4 L55-57）：女命先看身命，贪狼/七杀/擎羊则不美；七杀单居福德必为娼婢。 */
    private static String judgeNanNvTongYi(int mingIdx, List<String> mingStars, Map<Integer, List<String>> at, String skeleton) {
        boolean buMei = mingStars.contains("贪狼") || mingStars.contains("七杀") || mingStars.contains("擎羊");
        int fuIdx = palaceBranch(skeleton, "福德");
        boolean qiShaFu = fuIdx >= 0 && at.getOrDefault(fuIdx, List.of()).contains("七杀")
                && at.getOrDefault(fuIdx, List.of()).size() == 1;
        StringBuilder sb = new StringBuilder("论男女命同异（卷三·c4 L55-57）：");
        sb.append(buMei ? "女命身命见贪狼/七杀/擎羊 → 不美〔客观·释读〕"
                : "女命身命未见贪狼/七杀/擎羊 → 无「不美」之忌〔客观·释读〕");
        sb.append("；").append(qiShaFu ? "七杀单居福德 → 主娼婢之嫌〔客观·释读〕"
                : "福德宫非七杀单居（不主娼婢）〔客观·释读〕");
        return sb.toString();
    }

    /** 卷三·论羊陀迭并（c4 L87-88）：本命命宫/迁移原坐羊陀，流年流羊/流陀又冲会同一宫（或对宫）→ 迭并。 */
    private static boolean judgeYangTuoDieBing(int mingIdx, Map<Integer, List<String>> at, int flowGan) {
        if (mingIdx < 0) { return false; }
        int lu = LUCUN[flowGan];
        int fYang = floorMod(lu + 1, 12), fTuo = floorMod(lu - 1, 12);
        int oppMing = floorMod(mingIdx + 6, 12);
        for (String star : new String[]{"擎羊", "陀罗"}) {
            Integer p = branchIndexOf(at, star);
            if (p == null) { continue; }
            if (!(p == mingIdx || p == oppMing)) { continue; }   // 原羊/陀须在命宫或迁移（对宫）
            int oppP = floorMod(p + 6, 12);
            if (fYang == p || fTuo == p || fYang == oppP || fTuo == oppP) { return true; }
        }
        return false;
    }

    /** 卷三·论七杀重逢（c4 L89-91）：本命命宫三方四正有七杀守照，流年又遇流羊/流陀冲照 → 重逢。 */
    private static boolean judgeQiShaReFeng(int mingIdx, Map<Integer, List<String>> at, int flowGan) {
        if (mingIdx < 0) { return false; }
        Integer q = branchIndexOf(at, "七杀");
        if (q == null) { return false; }
        int oppMing = floorMod(mingIdx + 6, 12);
        boolean inSanFang = q == mingIdx || q == floorMod(mingIdx + 4, 12)
                || q == floorMod(mingIdx + 8, 12) || q == oppMing;
        if (!inSanFang) { return false; }
        int lu = LUCUN[flowGan];
        int fYang = floorMod(lu + 1, 12), fTuo = floorMod(lu - 1, 12);
        int oppQ = floorMod(q + 6, 12);
        return fYang == q || fTuo == q || fYang == oppQ || fTuo == oppQ;
    }

    /** 卷三·论流年太岁吉凶星杀（c4 L83-84）：太岁到命宫祸福尤紧；三方对照吉凶定祸福。 */
    private static String judgeLiuNianTaiSui(int mingIdx, int flowYearIdx, Map<Integer, List<String>> at) {
        if (flowYearIdx < 0) { return "论流年太岁吉凶星杀（卷三·c4 L83-84）：太岁未定，不判"; }
        boolean onMing = mingIdx >= 0 && flowYearIdx == mingIdx;
        int ji = countInSanFang(at, flowYearIdx, YUNXIAN_JI_STARS);
        int sha = countInSanFang(at, flowYearIdx, YUNXIAN_SHA_STARS);
        String tone = (sha == 0 && ji > 0) ? "吉" : (sha > ji ? "凶" : (ji > 0 ? "吉凶交参" : "平"));
        return "论流年太岁吉凶星杀（卷三·c4 L83-84）：太岁到" + ZHI[flowYearIdx] + "宫"
                + (onMing ? "·正临命宫（祸福尤紧）" : "（对照命宫）")
                + "；太岁宫三方吉星" + ji + "、凶星" + sha + " → 太岁宫三方" + tone + "〔客观·释读〕";
    }

    /** 卷三·论大限十年祸福（c4 L75-76）：大限宫无羊陀火铃空劫→十年安静；见煞→成败不一；
     *  陷地又见煞（再逢流年恶杀凑合）→官灾死亡之虞。 */
    private static String judgeDaXianShiNian(int dxIdx, Map<Integer, List<String>> at, int mingIdx) {
        if (dxIdx < 0) { return "论大限十年祸福（卷三·c4 L75-76）：尚未起限，不判"; }
        List<String> stars = at.getOrDefault(dxIdx, List.of());
        String[] sha = {"擎羊", "陀罗", "火星", "铃星", "地空", "地劫"};
        int shaCnt = 0;
        for (String s : sha) { if (stars.contains(s)) { shaCnt++; } }
        boolean fallen = stars.stream().anyMatch(s -> isMainStar(s) && isFallen(s, dxIdx));
        String r;
        if (shaCnt == 0) {
            r = "十年安静、人财全美〔客观·释读〕";
        } else if (fallen) {
            r = "陷地又见煞、成败不一，值流年恶杀凑合则官灾死亡之虞〔客观·释读〕";
        } else {
            r = "限内见煞、成败不一〔客观·释读〕";
        }
        return "论大限十年祸福（卷三·c4 L75-76）：现走" + ZHI[dxIdx] + "宫大限，煞星" + shaCnt
                + (fallen ? "、主星落陷" : "") + " → " + r;
    }

    /** 卷三·谈星要论开篇（c4 L12-21）：①身命迁移财官福德「八座」六宫俱聚吉化吉→富贵高寿，六宫俱陷聚凶→孤寡夭寿；
     *  ②父母妻子三宫俱有劫空杀忌→僧道之命（否则孤独贫穷）；③命无正曜而财官有吉拱→富贵全美/偏房所生。 */
    private static String judgeTanXingYaoLun(int mingIdx, Map<Integer, List<String>> at, String skeleton) {
        if (mingIdx < 0) { return "谈星要论开篇（卷三·c4 L12-21）：命宫未定，不判"; }
        String[] eight = {"命宫", "身宫", "迁移", "财帛", "官禄", "福德"};
        int jiCnt = 0, shaCnt = 0, total = 0;
        for (String pn : eight) {
            int idx = "身宫".equals(pn) ? palaceBranch(skeleton, "身宫") : palaceBranch(skeleton, pn);
            if (idx < 0) { continue; }
            total++;
            List<String> stars = at.getOrDefault(idx, List.of());
            boolean hasJi = false, hasSha = false;
            for (String s : stars) {
                if (YUNXIAN_JI_STARS.contains(s)) { hasJi = true; }
                if (YUNXIAN_SHA_STARS.contains(s)) { hasSha = true; }
            }
            if (hasJi) { jiCnt++; }
            if (hasSha) { shaCnt++; }
        }
        StringBuilder sb = new StringBuilder("谈星要论开篇（卷三·c4 L12-21）：");
        if (total > 0 && shaCnt == 0 && jiCnt >= total / 2 + 1) {
            sb.append("八座六宫（身命迁移财官福德）聚吉多而无凶 → 富贵高寿〔客观·释读〕；");
        } else if (total > 0 && jiCnt == 0 && shaCnt >= total / 2 + 1) {
            sb.append("八座六宫俱陷聚凶 → 孤寡夭寿之虞〔客观·释读〕；");
        } else {
            sb.append("八座六宫吉凶交杂（聚吉" + jiCnt + "/" + total + "、见煞" + shaCnt + "）〔客观·释读〕；");
        }
        // 父母妻子三宫俱有劫空杀忌 → 僧道之命
        boolean allThree = true;
        for (String pn : new String[]{"父母", "夫妻", "子女"}) {
            int idx = palaceBranch(skeleton, pn);
            List<String> stars = idx < 0 ? List.of() : at.getOrDefault(idx, List.of());
            boolean bad = stars.contains("地空") || stars.contains("地劫")
                    || stars.contains("擎羊") || stars.contains("陀罗") || stars.contains("火星") || stars.contains("铃星");
            if (!bad) { allThree = false; }
        }
        sb.append(allThree ? "父母妻子三宫俱有劫空杀忌 → 僧道之命（否则孤独贫穷）〔客观·释读〕"
                : "父母妻子三宫未见俱带劫空杀忌（不主僧道之命）〔客观·释读〕");
        return sb.toString();
    }

    /** 卷一·定富贵贫贱十等论（10 论；原文 c2 L545-555）——十等类型名（顺序同原文）。 */
    private static final String[] TEN_GRADE_NAMES = {"福寿", "聪明", "威勇", "文职", "武职",
            "刑名", "富贵", "贫贱", "疾殀", "僧道"};

    /** 「天空/地空」「地劫」空曜命中判定（兼容两种命名）。 */
    private static boolean isKongJie(Set<String> s) {
        return s.contains("地劫") || s.contains("天空") || s.contains("地空");
    }

    /**
     * 定富贵贫贱十等论（卷一 c2 L545-555）：按命宫 / 三方四正星曜组合判十等类型（可多等并见）。
     * 原文各论皆以「如…」举例、条件宽泛，此处取原文所举核心星曜组合为判据，附〔释读〕以便复核。
     */
    private static List<String> judgeTenGrades(Set<String> mingSet, Set<String> mingMain, Set<String> sanFang) {
        List<String> g = new ArrayList<>();
        // 福寿论：南人天同天梁坐命庙旺；北人紫微/武曲/破军/贪狼坐命旺宫（北人项地域不可判，取天同天梁为主）
        if (mingMain.contains("天同") && mingMain.contains("天梁")) {
            g.add("福寿（天同天梁坐命庙旺）");
        }
        // 聪明论：文昌文曲（及左辅右弼）三合拱照
        boolean changQu = (sanFang.contains("文昌") || sanFang.contains("文曲"))
                || (mingSet.contains("文昌") || mingSet.contains("文曲"));
        if (sanFang.contains("文昌") && sanFang.contains("文曲")
                && (sanFang.contains("左辅") || sanFang.contains("右弼"))) {
            g.add("聪明（昌曲辅弼三合拱照）");
        }
        // 威勇论：武曲擎羊七杀坐命，得权禄三方又得紫微天府左右拱照
        if (mingSet.contains("武曲") && (mingSet.contains("七杀") || mingSet.contains("擎羊"))
                && (sanFang.contains("紫微") || sanFang.contains("天府"))) {
            g.add("威勇（武曲杀羊得权禄、紫府左右拱）");
        }
        // 文职论：文昌文曲左辅右弼天魁天钺坐命旺宫（取昌曲＋辅弼魁钺）
        if (changQu && (sanFang.contains("左辅") || sanFang.contains("右弼")
                || sanFang.contains("天魁") || sanFang.contains("天钺"))) {
            g.add("文职（昌曲辅弼魁钺守命）");
        }
        // 武职论：武曲七杀坐命庙旺宫（取武曲＋七杀于命）
        if (mingSet.contains("武曲") && mingSet.contains("七杀")) {
            g.add("武职（武曲七杀守命庙旺）");
        }
        // 刑名论：擎羊陀罗火铃武曲破军带杀凑合三方四正
        if ((sanFang.contains("擎羊") || sanFang.contains("陀罗")
                || sanFang.contains("火星") || sanFang.contains("铃星"))
                && (sanFang.contains("武曲") || sanFang.contains("破军"))) {
            g.add("刑名（杀曜武破带杀凑合）");
        }
        // 富贵论：紫微天府（禄权科）日月昌曲辅弼魁钺守照拱冲
        if (sanFang.contains("紫微") && sanFang.contains("天府") && sanFang.contains("禄存")
                && (sanFang.contains("左辅") || sanFang.contains("右弼")
                || sanFang.contains("文昌") || sanFang.contains("文曲"))) {
            g.add("富贵（紫府禄存昌曲辅弼拱命）");
        }
        // 贫贱论：擎羊陀罗廉贞七杀武曲破军（天空地劫）忌星三方四正并犯陷地
        if ((sanFang.contains("擎羊") || sanFang.contains("陀罗") || sanFang.contains("廉贞")
                || sanFang.contains("七杀") || sanFang.contains("破军")) && isKongJie(sanFang)) {
            g.add("贫贱（杀破廉忌逢空劫）");
        }
        // 疾殀论：贪狼廉贞擎羊陀罗天空地劫火铃三方守照
        if ((sanFang.contains("贪狼") || sanFang.contains("廉贞"))
                && (sanFang.contains("擎羊") || sanFang.contains("陀罗")
                || sanFang.contains("火星") || sanFang.contains("铃星")) && isKongJie(sanFang)) {
            g.add("疾殀（贪廉煞忌逢空劫）");
        }
        // 僧道论：天机天梁七杀破军天空地劫并犯帝座紫微或耗杀加临
        if ((sanFang.contains("天机") || sanFang.contains("天梁"))
                && (sanFang.contains("七杀") || sanFang.contains("破军")) && isKongJie(sanFang)) {
            g.add("僧道（机梁杀破逢空劫）");
        }
        return g;
    }

    /** 卷一·诸格论（c2 L510-544）12 格类别名（顺序同原文），对应本体 reading:longevityType 枚举。 */
    private static final String[] LONGEVITY_TYPES = {"商贾", "艺术", "僧道", "孤克", "寿夭淫荡",
            "残疾", "破相", "聪明", "富足", "贫贱", "盗贼", "一身驳杂"};

    /** 四杀（羊陀火铃）。 */
    private static boolean hasSha(List<String> stars) {
        return stars.stream().anyMatch(s -> s.equals("擎羊") || s.equals("陀罗") || s.equals("火星") || s.equals("铃星"));
    }

    /** 支索引是否等于某地支。 */
    private static boolean isBranch(int idx, String zhi) {
        return idx >= 0 && idx < 12 && ZHI[idx].equals(zhi);
    }

    /** 某宫是否「落陷 + 加杀」（既见四杀、又有星落陷）。 */
    private static boolean gongFallenWithSha(int idx, Map<Integer, List<String>> at) {
        if (idx < 0 || idx > 11) {
            return false;
        }
        List<String> stars = at.getOrDefault(idx, List.of());
        boolean sha = hasSha(stars);
        boolean fall = stars.stream().anyMatch(s -> isFallen(s, idx));
        return sha && fall;
    }

    /**
     * 卷一·诸格论（c2 L510-544）逐格判定（共 12 格）：论人有无商贾之命 / 论人命有无艺术者 / 论出家僧道之命 /
     * 论人命内犯孤克者 / 论寿夭淫荡 / 论定人残疾 / 论定人破相 / 论定人聪明 / 论定人富足 /
     * 论定人贫贱 / 论定人作盗贼 / 论定人一身驳杂。
     *
     * <p>对应本体 {@code reading:Longevity} / {@code reading:longevityType}。原文条件多「如…」举例，
     * 取原文所举核心星曜/宫位为判据，附〔释读〕以便复核（可多格并见）。
     */
    private static List<String> judgeLongevity(int mingIdx, int shenIdx, Map<Integer, List<String>> at) {
        List<String> g = new ArrayList<>();
        if (mingIdx < 0 || mingIdx > 11) {
            return g;
        }
        List<String> mingList = at.getOrDefault(mingIdx, List.of());
        Set<String> mingSet = new HashSet<>(mingList);
        Set<String> sanFang = new HashSet<>();
        for (int b : new int[]{mingIdx, floorMod(mingIdx + 4, 12), floorMod(mingIdx + 8, 12), floorMod(mingIdx + 6, 12)}) {
            sanFang.addAll(at.getOrDefault(b, List.of()));
        }
        Set<String> shenSet = new HashSet<>(shenIdx >= 0 && shenIdx <= 11 ? at.getOrDefault(shenIdx, List.of()) : List.of());
        boolean mingNoMain = mingList.stream().noneMatch(ZiWeiDouShuOntologyJobWorker::isMainStar);
        int fumuIdx = floorMod(mingIdx - 11, 12);   // 父母（相貌）
        int fuqiIdx = floorMod(mingIdx - 2, 12);    // 夫妻
        int zinvIdx = floorMod(mingIdx - 3, 12);    // 子女
        int jiEIdx = floorMod(mingIdx - 5, 12);     // 疾厄
        int qianYiIdx = floorMod(mingIdx + 6, 12);  // 迁移
        Integer tanIdx = branchIndexOf(at, "贪狼");
        boolean tanMiao = tanIdx != null && "庙".equals(brightnessOf("贪狼", tanIdx));

        // 1) 商贾：贪月同杀会机梁（因财计利作经商）；或紫府遇擎羊 / 武曲迁移利市场
        if (sanFang.contains("贪狼") && sanFang.contains("太阴") && sanFang.contains("天同")
                && sanFang.contains("七杀") && (sanFang.contains("天机") || sanFang.contains("天梁"))) {
            g.add("商贾（贪月同杀会机梁，因财计利作经商）");
        } else if ((mingSet.contains("紫微") && mingSet.contains("天府") && sanFang.contains("擎羊"))
                || at.getOrDefault(qianYiIdx, List.of()).contains("武曲")) {
            g.add("商贾（紫府遇擎羊 / 武曲迁移利市场〔释读〕）");
        }
        // 2) 艺术：寅申巳亥或丑未辰戌安命逢贪狼武曲（化忌加杀）；破武未宫 / 破军廉贞卯酉 / 天机天相命身
        boolean artPalace = isBranch(mingIdx, "寅") || isBranch(mingIdx, "申") || isBranch(mingIdx, "巳") || isBranch(mingIdx, "亥")
                || isBranch(mingIdx, "丑") || isBranch(mingIdx, "未") || isBranch(mingIdx, "辰") || isBranch(mingIdx, "戌");
        if ((artPalace && (mingSet.contains("贪狼") || mingSet.contains("武曲")))
                || (isBranch(mingIdx, "未") && mingSet.contains("破军") && mingSet.contains("武曲"))
                || ((isBranch(mingIdx, "卯") || isBranch(mingIdx, "酉")) && mingSet.contains("破军") && mingSet.contains("廉贞"))
                || (mingSet.contains("天机") && mingSet.contains("天相"))
                || (shenSet.contains("天机") && shenSet.contains("天相"))) {
            g.add("艺术（细巧艺术之人：寅申巳亥/丑未辰戌逢贪狼武曲，或破武未宫/破廉卯酉/机相命身）");
        }
        // 3) 僧道：紫微居卯酉遇劫空；命无正曜兼羊火劫空；机杀破梁会太阳帝座
        boolean kong = isKongJie(mingSet) || isKongJie(sanFang);
        if (((isBranch(mingIdx, "卯") || isBranch(mingIdx, "酉")) && mingSet.contains("紫微") && kong)
                || (mingNoMain && kong && (sanFang.contains("擎羊") || sanFang.contains("火星")))
                || (sanFang.contains("天机") && sanFang.contains("七杀") && sanFang.contains("破军")
                    && sanFang.contains("天梁") && sanFang.contains("太阳") && sanFang.contains("紫微"))) {
            g.add("僧道（极居卯酉遇劫空 / 命坐空乡 / 机杀破梁会太阳帝座——男僧道女尼姑）");
        }
        // 4) 孤克：父母/夫妻/子女宫落陷加杀 → 刑克鳏寡孤独
        if (gongFallenWithSha(fumuIdx, at) || gongFallenWithSha(fuqiIdx, at) || gongFallenWithSha(zinvIdx, at)) {
            g.add("孤克（父母/夫妻/子女宫落陷加杀，主刑克鳏寡孤独）");
        }
        // 5) 寿夭淫荡：七杀临身终是夭；贪狼入庙定为娼；身命两宫俱有杀贪花恋酒
        if (shenSet.contains("七杀") || mingSet.contains("七杀")) {
            g.add("寿夭（七杀临身终是夭）");
        }
        if ((mingSet.contains("贪狼") && "庙".equals(brightnessOf("贪狼", mingIdx))) || tanMiao) {
            g.add("淫荡（贪狼入庙定为娼）");
        }
        boolean shenSha = shenSet.contains("七杀") || hasSha(new ArrayList<>(shenSet));
        boolean mingSha = mingSet.contains("七杀") || hasSha(mingList);
        if (shenSha && mingSha) {
            g.add("淫荡（身命两宫俱有杀，贪花恋酒祸犹深）");
        }
        // 6) 残疾：命宫/身宫见羊陀火铃且落陷
        if (gongFallenWithSha(mingIdx, at) || gongFallenWithSha(shenIdx, at)) {
            g.add("残疾（命宫/身宫星落陷加羊陀火铃，陀腰曲背）");
        }
        // 7) 破相：相貌（父母）宫逢杀曜且三方逢刑；或疾厄擎羊
        if ((hasSha(at.getOrDefault(fumuIdx, List.of())) && inSanFang(fumuIdx, "天刑"))
                || at.getOrDefault(jiEIdx, List.of()).contains("擎羊")) {
            g.add("破相（相貌逢杀加刑 / 疾厄擎羊逢耗使，折伤肢体）");
        }
        // 8) 聪明：文曲天相破军 + 三方会昌曲
        if ((mingSet.contains("文曲") || mingSet.contains("天相") || mingSet.contains("破军"))
                && (sanFang.contains("文昌") || sanFang.contains("文曲"))) {
            g.add("聪明（文曲天相破军，三方昌曲会）");
        }
        // 9) 富足：太阴入庙 + 命宫不犯破耗凶星
        if (mingSet.contains("太阴") && "庙".equals(brightnessOf("太阴", mingIdx))
                && !(mingSet.contains("破军") || hasSha(mingList) || isKongJie(mingSet))) {
            g.add("富足（太阴入庙有光辉，破耗凶星皆不犯）");
        }
        // 10) 贫贱：三方火忌羊陀 + 武曲廉贞巨门破军
        if ((sanFang.contains("火星") || sanFang.contains("铃星") || sanFang.contains("擎羊") || sanFang.contains("陀罗"))
                && (sanFang.contains("武曲") || sanFang.contains("廉贞") || sanFang.contains("巨门") || sanFang.contains("破军"))) {
            g.add("贫贱（火忌羊陀四正侵，武廉巨破会，一生暴怒又身贫）");
        }
        // 11) 盗贼：命逢破耗与贪廉 + 七杀三方照及身
        if ((mingSet.contains("破军") || mingSet.contains("廉贞") || mingSet.contains("贪狼"))
                && sanFang.contains("七杀")) {
            g.add("盗贼（命逢破耗与贪廉，七杀三方照及身）");
        }
        // 12) 一身驳杂：吉曜相扶凶曜临
        boolean jiYao = sanFang.contains("左辅") || sanFang.contains("右弼") || sanFang.contains("文昌")
                || sanFang.contains("文曲") || sanFang.contains("天魁") || sanFang.contains("天钺") || sanFang.contains("禄存");
        boolean xiongYao = kong || hasSha(mingList) || sanFang.contains("擎羊") || sanFang.contains("陀罗")
                || sanFang.contains("火星") || sanFang.contains("铃星");
        if (jiYao && xiongYao) {
            g.add("一身驳杂（吉曜相扶凶曜临，百般巧艺不亨通）");
        }
        return g;
    }

    private static String palaceView(String skeleton, Map<Integer, List<String>> at, String palaceName) {
        int b = palaceBranch(skeleton, palaceName);
        if (b < 0) {
            return "（宫位未定）";
        }
        return "（" + ZHI[b] + "）" + String.join("、", at.getOrDefault(b, List.of()));
    }

    // ---- 神煞起例 ----

    /**
     * 龙池：辰上起子，顺数至生年支（卷二·安龙池凤阁诀「龙池子顺辰，凤阁子戌逆」——
     * 后句明示「凤阁·戌上起子·逆」，前句按同构读作「龙池·辰上起子·顺」）。
     */
    private static int longChi(int yearBranch) {
        return floorMod(4 + yearBranch, 12);
    }

    /** 凤阁：戌上起子，逆数至生年支（卷二·安龙池凤阁诀）。 */
    private static int fengGe(int yearBranch) {
        return floorMod(10 - yearBranch, 12);
    }

    /**
     * 七杀朝斗：七杀守命于 寅、申、子、午 四宫（卷三·谈星要论「七杀寅申子午一生爵禄荣昌，
     * 为七杀朝斗格」；卷一·諸格论「七杀朝斗 见前批注」）。
     */
    private static boolean isQiShaChaoDou(int mingIdx) {
        return mingIdx == indexOf(ZHI, "子") || mingIdx == indexOf(ZHI, "午")
                || mingIdx == indexOf(ZHI, "寅") || mingIdx == indexOf(ZHI, "申");
    }

    // ---- 卷一·十二宫诸星得地合格诀 / 失陷破格诀（按命宫地支；原文 c2 L556-578）----

    /**
     * 得地合格诀：{命宫支, 本宫须见诸星(顿号分隔), 三方四正须见诸星(顿号分隔,空则不限), 原文句, 生年干(顿号分隔,"-"不限)}。
     * 第5列生年干据《全書》原文各支末句「…生人美/皆为吉/…」逐支落地（c2 L556-568 十二宫诸星得地合格诀）：
     * 子「庚辛乙癸」、丑「丙戊」、卯「辛乙」、巳「戊辛壬丙」、午「甲丁己癸」、申「甲庚癸」、酉「辛乙」。
     * 寅行原文作「申巳庚」（「申巳」非天干，疑「甲己庚」之讹）→ 字面存疑，标〔取文·不臆断〕不以生干否决；
     * 原刻本「丙戍」之「戍」系「戊」之形讹，取「丙戊」（与卷二得地富贵论「戍宫」作「戌宫」同类字形修复）。
     */
    private static final String[][] PALACE_ZHI_GE = {
            {"子", "贪狼、太阴", "天机、天梁", "子宫贪狼杀阴星，机梁相拱福兴隆，庚辛乙癸生人美", "庚、辛、乙、癸"},
            {"丑", "", "太阳、太阴", "丑宫立命日月朝，丙戊生人福禄饶", "丙、戊"},
            {"寅", "巨门、太阳", "天梁", "寅宫巨日足丰隆，七杀天梁百事通，申巳庚人皆为吉", "申、巳、庚"},
            {"卯", "天机、巨门、武曲", "", "卯宫机巨武曲逢，辛乙生人福气隆", "辛、乙"},
            {"辰", "天机、天梁", "", "辰位机梁坐命宫", "-"},
            {"巳", "天机、天相", "紫微、天府", "巳位天机天相临，紫府朝垣福更深，戊辛壬丙皆为贵", "戊、辛、壬、丙"},
            {"午", "紫微、天府、太阳", "", "午宫紫府太阳同，甲丁己癸生人福", "甲、丁、己、癸"},
            {"未", "紫微、武曲、廉贞", "", "未宫紫武廉贞同", "-"},
            {"申", "紫微、廉贞、天梁", "", "申宫紫帝贞梁同，甲庚癸人如得喜", "甲、庚、癸"},
            {"酉", "太阴", "巨门、太阳", "酉宫最喜太阴逢，巨日又逢当面冲，辛乙生人为贵格", "辛、乙"},
            {"戌", "紫微", "", "戌宫紫微对冲辰", "-"},
            {"亥", "太阴", "", "亥宫最喜太阴逢", "-"}};

    /** 失陷破格诀：{命宫支, 所举破格诸星(顿号分隔), 原文句}——命宫见所列星且落陷者判破格。 */
    private static final String[][] PALACE_ZHI_PO = {
            {"子", "天机", "子午天机此为真，此星落陷果为真"},
            {"丑", "巨门、铃星", "丑巨铃，此星落陷果为真"},
            {"寅", "天机、文昌、文曲、太阴", "寅上机昌曲月逢，虽然吉拱不丰隆"},
            {"卯", "太阴、擎羊", "卯上太阴擎羊逢，纵然化吉非全美"},
            {"辰", "巨门、紫微", "辰宫巨宿紫微同，若非加杀到头凶"},
            {"巳", "武曲、太阴、天梁、巨门、贪狼、廉贞", "巳宫武月天梁巨，贪宿廉贞共到蛇"},
            {"午", "贪狼、巨门、太阴、文昌", "午宫贪巨月昌从，羊刃三合最嫌冲"},
            {"未", "巨门、太阳", "未宫巨宿太阳嫌，纵然化吉非全美"},
            {"申", "天机、巨门", "申宫机巨为破格，男人浪荡女人贫"},
            {"酉", "天机、巨门", "酉宫机巨桃花见，男女逢之总不荣"},
            {"戌", "紫微、破军", "戌上紫破若相逢，天同太阳皆主凶"},
            {"亥", "贪狼、火星、天梁", "亥宫贪火天梁同，飘荡浪子走西东"}};

    /** 得地富贵论（卷一·十二宫诸星得地富贵论 c2 L579-589）：{命宫支, 本宫所举吉星(顿号), 原文句}——命宫见其一即合〔释读·待考〕。 */
    private static final String[][] PALACE_FUGUI = {
            {"子", "太阴、七杀、破军、文昌、贪狼、文曲", "子宫得地太阴星，杀破昌贪文曲明"},
            {"丑", "紫微、破军、太阳、太阴", "丑未紫破朝日月"},
            {"寅", "太阳、巨门、七杀、天同、天梁", "寅宫最喜逢阳巨，七杀天同梁文清"},
            {"卯", "巨门、天机、武曲", "卯上巨机为贵格，武曲守卯福丰盈"},
            {"辰", "天机、天梁", "辰戌机梁非小补"},
            {"巳", "天机、天相、紫微、天府、巨门", "巳亥天机天相贵，紫府巨宿巳亥兴"},
            {"午", "紫微、天府、天梁、七杀", "午宫紫府梁俱荣，七杀子午逢左右"},
            {"未", "紫微、破军、廉贞、天梁", "丑未紫破朝日月，未贞梁丑福非轻"},
            {"申", "廉贞、巨门、太阴、七杀", "申宫贞巨阴杀美"},
            {"酉", "太阴", "酉戌亥上太阴停"},
            {"戌", "天机、天梁、天府", "辰戌机梁非小补，戌宫天府累千金"},
            {"亥", "天机、天相、太阴、紫微、天府、巨门、天梁", "巳亥天机天相贵，亥宫天府天梁吉"}};

    /** 失陷贫贱论（卷一·十二宫诸星失陷贫贱论 c2 L590-596）：{命宫支, 所举失陷诸星(顿号), 原文句}——命宫见所列星且落陷者判〔释读·待考〕。 */
    private static final String[][] PALACE_PINJIAN = {
            {"丑", "巨门、天机", "丑未巨机为值福，失陷此月福须轻"},
            {"未", "巨门、天机", "丑未巨机为值福，失陷此月福须轻"},
            {"卯", "擎羊、破军", "卯酉不喜逢羊刃，破军卯酉不为清"},
            {"酉", "擎羊、天机、巨门、太阳、破军", "酉逢机巨日无精，破军卯酉不为清"},
            {"辰", "紫微、破军、贪狼、廉贞", "辰戌紫破朝罗网，辰休戌囚贪贞陷"},
            {"戌", "紫微、破军、贪狼、廉贞、太阳", "辰戌紫破朝罗网，戌亥逢阳亦不容"},
            {"午", "太阴、巨门", "午宫阴巨不堪称"},
            {"申", "武曲", "申宫合武为下格"},
            {"巳", "贪狼、七杀、太阴", "贪杀巳亥居陷地，卯辰巳午逢阴宿"},
            {"亥", "贪狼、七杀、太阳", "贪杀巳亥居陷地，戌亥逢阳亦不容"}};

    /**
     * 十二宫诸星得地合格 / 失陷破格 / 得地富贵 / 失陷贫贱（卷一 c2 L556-596·四论）：按命宫地支，
     * 判本宫/三方是否合诀文所举诸星。得地合格＝本宫（及三方）并见诀文所列之星，且**生年干合诀文末句**（不载或字面存疑者不限）；
     * 失陷破格＝命宫见诀文所列之星且落陷；得地富贵＝命宫见得地富贵论所列吉星之一；失陷贫贱＝命宫见失陷贫贱论所列星且落陷。
     * 条件简奥处附〔释读·待考〕。yearGan 为生年干索引（-1 未定）。
     */
    private static String judgePalaceZhiGrid(int mingIdx, Map<Integer, List<String>> at, int yearGan) {
        if (mingIdx < 0) {
            return "十二宫得地/失陷格（卷一）：命宫未定，不判";
        }
        String z = ZHI[mingIdx];
        Set<String> ming = new HashSet<>(at.getOrDefault(mingIdx, List.of()));
        Set<String> sf = new HashSet<>();
        for (int b : new int[]{mingIdx, floorMod(mingIdx + 4, 12), floorMod(mingIdx + 8, 12), floorMod(mingIdx + 6, 12)}) {
            sf.addAll(at.getOrDefault(b, List.of()));
        }
        StringBuilder sb = new StringBuilder();
        for (String[] row : PALACE_ZHI_GE) {
            if (!row[0].equals(z)) {
                continue;
            }
            Set<String> needMing = splitStarSet(row[1]);
            Set<String> needSf = splitStarSet(row[2]);
            if (ming.containsAll(needMing) && (needSf.isEmpty() || sf.containsAll(needSf))) {
                sb.append("得地合格·").append(z).append("安命（").append(row[3]).append("）");
                // 生年干条件（原文各支末句「…生人美/皆为吉/…」）
                String rawGan = row.length > 4 ? row[4] : "-";
                if (!"-".equals(rawGan)) {
                    if (ganListHasSuspectToken(rawGan)) {
                        sb.append("·生干「").append(rawGan).append("」原文存疑〔取文·不臆断〕");
                    } else if (yearGan < 0) {
                        sb.append("·生干").append(rawGan).append("（生年干未定·不否决）");
                    } else if (ganListMatches(rawGan, yearGan)) {
                        sb.append("·生干").append(rawGan).append("相合（").append(GAN[yearGan]).append("）");
                    } else {
                        sb.append("·生干").append(rawGan).append("不合（").append(GAN[yearGan]).append("）·格减等");
                    }
                }
                sb.append("〔释读·待考〕；");
            }
        }
        for (String[] row : PALACE_ZHI_PO) {
            if (!row[0].equals(z)) {
                continue;
            }
            for (String s0 : splitStarSet(row[1])) {
                if (ming.contains(s0) && isFallen(s0, mingIdx)) {
                    sb.append("失陷破格·").append(z).append("安命（").append(row[2]).append("）〔释读·待考〕；");
                    break;
                }
            }
        }
        for (String[] row : PALACE_FUGUI) {
            if (!row[0].equals(z)) {
                continue;
            }
            for (String s0 : splitStarSet(row[1])) {
                if (ming.contains(s0)) {
                    sb.append("得地富贵·").append(z).append("安命（").append(row[2]).append("）〔释读·待考〕；");
                    break;
                }
            }
        }
        for (String[] row : PALACE_PINJIAN) {
            if (!row[0].equals(z)) {
                continue;
            }
            for (String s0 : splitStarSet(row[1])) {
                if (ming.contains(s0) && isFallen(s0, mingIdx)) {
                    sb.append("失陷贫贱·").append(z).append("安命（").append(row[2]).append("）〔释读·待考〕；");
                    break;
                }
            }
        }
        return sb.length() == 0 ? "十二宫得地/失陷格（卷一）：本支未见成格" : sb.toString();
    }

    /**
     * 卷三·论诸星同垣各司所宜分别富贵贫贱夭寿（c4 L178-477）：按本盘主星落宫/并见/生年干逐条判定。
     * 可客观判定者（居宫/并见星/生年干）命中列出；仅举例式而无客观条件者以〔举例式·待考〕取文，不臆断。
     * 外部比对（第十五轮联网）：古文岛（卷三第九十七）、zhouqianli、公众号白话直译等均照录全文，白话本亦仅逐句直译、
     *   未给「举例式」条（如「诸宫遇福」）补客观条件；具条件者（如左右夹命「命在丑、左在子、右在寅」）与本实现一致 → 维持取文口径。
     */
    private static String judgeTongYuan(int mingIdx, Map<Integer, List<String>> at, int yearGan, String skeleton) {
        return judgeTongYuan(mingIdx, at, yearGan, skeleton, null, null);
    }

    /** 6 参重载：携带性别（女命条）与四化（禄存/明禄暗禄/双禄等），供节点 11 与测试调用。 */
    private static String judgeTongYuan(int mingIdx, Map<Integer, List<String>> at, int yearGan, String skeleton,
            Boolean male, String fourTransform) {
        if (mingIdx < 0) {
            return "卷三·诸星同垣各司所宜（c4 L178-477）：命宫未定，不判";
        }
        List<String> hitted = new ArrayList<>();
        List<String> pend = new ArrayList<>();
        List<String> orJudged = new ArrayList<>();
        List<String> objJudged = new ArrayList<>();
        for (String[] row : STAR_TONGYUAN) {
            String star = row[0];
            int branch = -1;
            if (star.equals("命宫") || star.equals("纳音")) {
                branch = mingIdx;
            } else if (star.equals("身宫")) {
                branch = palaceBranch(skeleton, "身宫");
            } else if (star.equals("财帛") || star.equals("财宅")) {
                branch = palaceBranch(skeleton, "财帛");
            } else if (star.equals("财福")) {
                branch = palaceBranch(skeleton, "福德");
            } else {
                for (String s0 : splitStarSet(star)) {
                    Integer b = branchIndexOf(at, s0);
                    if (b != null) {
                        branch = b;
                        break;
                    }
                }
                if (branch < 0) {
                    continue;
                }
            }
            boolean hasCond = false;
            boolean condOk = true;
            if (!"-".equals(row[1])) {
                hasCond = true;
                if (branch < 0 || !row[1].contains(ZHI[branch])) {
                    condOk = false;
                }
            }
            if (!"-".equals(row[2])) {
                hasCond = true;
                for (String tok : splitStarSet(row[2])) {
                    String sn = tok;
                    boolean sanfang = sn.startsWith("(三方)");
                    if (sanfang) {
                        sn = sn.substring(4);
                    }
                    Integer sb = branchIndexOf(at, sn);
                    if (sb == null || (sanfang ? !inSanFang(branch, ZHI[sb]) : sb != branch)) {
                        condOk = false;
                        break;
                    }
                }
            }
            if (!"-".equals(row[3])) {
                hasCond = true;
                if (yearGan < 0) {
                    pend.add(star + "（生年干未定·待考）：" + row[4]);
                    continue;
                }
                if (!row[3].contains(GAN[yearGan])) {
                    condOk = false;
                }
            }
            if (!hasCond) {
                // 举例式行：先按断语「自含可客观判定条件」复核（如「命无正曜」「贪狼入庙」「武曲迁移」等），
                //   再按「加吉/加煞…」OR 复核；两者皆无客观条件者仍取文待考（不臆造）。
                String objNote = tongYuanObjectiveFull(row[4], mingIdx, at, yearGan, skeleton, male, fourTransform);
                if (objNote != null) {
                    objJudged.add(star + "（" + ZHI[branch] + "宫）：" + row[4] + objNote);
                } else {
                    String orNote = tongYuanOrNote(row[4], branch, at);
                    if (orNote != null) {
                        orJudged.add(star + "（" + ZHI[branch] + "宫）：" + row[4] + orNote);
                    } else {
                        pend.add(star + "：" + row[4]);
                    }
                }
            } else if (condOk) {
                hitted.add(star + "（" + ZHI[branch] + "宫）：" + row[4]);
            }
        }
        StringBuilder sb = new StringBuilder();
        sb.append("卷三·诸星同垣各司所宜（c4 L178-477；本盘命中 ").append(hitted.size()).append(" 条）：")
                .append(hitted.isEmpty() ? "（无客观命中）" : String.join("；", hitted));
        if (!objJudged.isEmpty()) {
            sb.append("｜〔自含条件客观判定 ").append(objJudged.size()).append(" 条·释读〕").append(String.join("；", objJudged));
        }
        if (!orJudged.isEmpty()) {
            sb.append("｜〔OR条件客观复核 ").append(orJudged.size()).append(" 条·释读非原文〕").append(String.join("；", orJudged));
        }
        if (!pend.isEmpty()) {
            sb.append("｜〔举例式·待考取文 ").append(pend.size()).append(" 条〕").append(String.join("；", pend));
            int cy = 0, cn = 0, cc = 0, cx = 0;
            for (String p : pend) {
                if (p.contains("纳音") || p.contains("败地") || p.contains("绝处")) {
                    cn++;
                } else if (p.contains("限") || p.contains("流年") || p.contains("运生") || p.contains("运弱")) {
                    cy++;
                } else if (p.contains("夹")) {
                    cc++;
                } else {
                    cx++;
                }
            }
            sb.append("〔待考·按因：运限/流年（转节点13-15核） ").append(cy)
                    .append("；纳音生旺死绝（宜历法节点统一） ").append(cn)
                    .append("；夹拱法原文未定义 ").append(cc)
                    .append("；性质/比喻（无客观条件·不臆造） ").append(cx).append("〕");
        }
        return sb.toString();
    }

    /**
     * 断语「自含可客观判定条件」者，据本盘客观复核（《全書》断语言明的星/宫/庙陷/四化事实之复算·释读，
     * 非外部新增条件、不改第一权威）。仅对断语中**明文可判**者做是/否核验；无客观条件返回 null（保持待考，不臆造）。
     */
    private static String tongYuanObjective(final String text, final int mingIdx,
            final Map<Integer, List<String>> at, final int yearGan, final String skeleton) {
        if (text == null || at == null) {
            return null;
        }
        // 1 命无正曜：命宫无十四主星
        if (text.contains("命无正曜")) {
            boolean noMain = true;
            for (String s : at.getOrDefault(mingIdx, List.of())) {
                if (isMainStar(s)) { noMain = false; break; }
            }
            return objNote("命宫无十四主星", noMain);
        }
        // 2 禄存十二宫中皆入庙：禄存无陷（在盘即庙）
        if (text.contains("禄存十二宫中皆入庙")) {
            return objNote("禄存入庙(禄存无陷)", branchIndexOf(at, "禄存") != null);
        }
        // 3 禄存守于财宅：禄存落财帛或田宅宫
        if (text.contains("禄存守于财宅")) {
            Integer lu = branchIndexOf(at, "禄存");
            int cai = palaceBranch(skeleton, "财帛");
            int tian = palaceBranch(skeleton, "田宅");
            if (lu == null || (cai < 0 && tian < 0)) { return null; }
            return objNote("禄存守财帛/田宅", lu == cai || lu == tian);
        }
        // 4 天机加恶杀同宫：天机同宫见羊陀火铃
        if (text.contains("天机加恶杀同宫")) {
            Integer j = branchIndexOf(at, "天机");
            if (j == null) { return null; }
            boolean sha = at.getOrDefault(j, List.of()).stream()
                    .anyMatch(s -> List.of("擎羊", "陀罗", "火星", "铃星").contains(s));
            return objNote("天机同宫见羊陀火铃", sha);
        }
        // 5 太阳化忌（生年太阳化忌）
        if (text.contains("太阳化忌")) {
            if (yearGan < 0) { return null; }
            return objNote("生年太阳化忌", "太阳".equals(SIHUA[yearGan][3]));
        }
        // 6 贪狼入庙（贪狼居庙）
        if (text.contains("贪狼入庙")) {
            Integer t = branchIndexOf(at, "贪狼");
            if (t == null) { return null; }
            return objNote("贪狼居庙", "庙".equals(brightnessOf("贪狼", t)));
        }
        // 7 武曲迁移（武曲在迁移宫）
        if (text.contains("武曲迁移")) {
            Integer w = branchIndexOf(at, "武曲");
            int qian = palaceBranch(skeleton, "迁移");
            if (w == null || qian < 0) { return null; }
            return objNote("武曲在迁移宫", w == qian);
        }
        // 8 文昌文科拱照（论三方）：文昌在命宫三方四正
        if (text.contains("文科拱照")) {
            Integer c = branchIndexOf(at, "文昌");
            if (c == null) { return null; }
            return objNote("文昌在命宫三方四正", inSanFang(mingIdx, ZHI[c]));
        }
        // 9 右弼左辅终身福厚（在命宫迁移）：左辅/右弼落命宫或迁移宫
        if (text.contains("右弼左辅") && text.contains("命宫迁移")) {
            int qian = palaceBranch(skeleton, "迁移");
            Integer fu = branchIndexOf(at, "左辅");
            Integer bi = branchIndexOf(at, "右弼");
            if (qian < 0 && mingIdx < 0) { return null; }
            boolean ok = (fu != null && (fu == mingIdx || fu == qian))
                    || (bi != null && (bi == mingIdx || bi == qian));
            return objNote("左辅/右弼在命宫或迁移宫", ok);
        }
        // 10 府相朝垣：天府、天相俱在命宫三方四正
        if (text.contains("府相朝垣")) {
            Integer f = branchIndexOf(at, "天府");
            Integer x = branchIndexOf(at, "天相");
            if (f == null || x == null) { return null; }
            return objNote("天府天相俱在命宫三方四正", inSanFang(mingIdx, ZHI[f]) && inSanFang(mingIdx, ZHI[x]));
        }
        return null;
    }

    /** 客观核验结果串：〔客观·是/否（依据）·释读〕。 */
    private static String objNote(final String basis, final boolean ok) {
        return "〔客观·" + (ok ? "是" : "否") + "（" + basis + "）·释读〕";
    }

    /** 带标签的核验串：tag 为「外部通行法」「释读」等；外部通行法统一后缀「非《全書》原文」。 */
    private static String objNoteExt(final String tag, final String basis, final boolean ok) {
        String tail = "外部通行法".equals(tag) ? "·非《全書》原文" : "·释读";
        return "〔" + tag + "·" + (ok ? "是" : "否") + "（" + basis + "）" + tail + "〕";
    }

    // ---- 三方四正吉煞计数辅助（供同垣「自含/强弱」类客观复核）----
    private static int countInSanFang(final Map<Integer, List<String>> at, final int idx, final Set<String> set) {
        int c = 0;
        for (int b : new int[]{idx, floorMod(idx + 4, 12), floorMod(idx + 8, 12), floorMod(idx + 6, 12)}) {
            for (String s : at.getOrDefault(b, List.of())) {
                if (set.contains(s)) { c++; }
            }
        }
        return c;
    }
    private static boolean hasJiInSanFang(final Map<Integer, List<String>> at, final int idx) {
        return countInSanFang(at, idx, YUNXIAN_JI_STARS) > 0;
    }
    private static int shaCountInSanFang(final Map<Integer, List<String>> at, final int idx) {
        return countInSanFang(at, idx, YUNXIAN_SHA_STARS);
    }
    private static int jiShaNet(final Map<Integer, List<String>> at, final int idx) {
        return countInSanFang(at, idx, YUNXIAN_JI_STARS) - countInSanFang(at, idx, YUNXIAN_SHA_STARS);
    }
    /** 命宫/身宫某宫三方四正是否皆吉（净吉且至少见一吉）。 */
    private static boolean palaceNetJi(final Map<Integer, List<String>> at, final int idx) {
        return idx >= 0 && jiShaNet(at, idx) >= 0 && hasJiInSanFang(at, idx);
    }

    /**
     * 批次②：对剩余「举例式·待考取文」中**可据本盘或外部通行法客观判定**者，逐条给出是/否。
     * 分级标注：原文自含条件→〔客观·…·释读〕；须补会照/强弱通则→〔释读·…〕；
     * 须外部通行法补条件（端正/女命/纳音墓库等）→〔外部通行法·…·非《全書》原文〕。无客观判据返回 null（保持待考，不臆造）。
     */
    private static String tongYuanObjectiveFull(final String text, final int mingIdx,
            final Map<Integer, List<String>> at, final int yearGan, final String skeleton,
            final Boolean male, final String fourTransform) {
        String n1 = tongYuanObjective(text, mingIdx, at, yearGan, skeleton);  // 批次①
        if (n1 != null) {
            return n1;
        }
        if (text == null || at == null) {
            return null;
        }
        // A1 天梁守照吉相逢平生福寿
        if (text.contains("天梁守照吉相逢")) {
            Integer liang = branchIndexOf(at, "天梁");
            if (liang == null) { return null; }
            boolean anchor = liang == mingIdx || inSanFang(mingIdx, ZHI[liang]);
            return objNote("天梁守/照命且三方见吉曜", anchor && hasJiInSanFang(at, mingIdx));
        }
        // A2 巨陷天机为破格
        if (text.contains("巨陷天机")) {
            Integer ju = branchIndexOf(at, "巨门");
            Integer ji = branchIndexOf(at, "天机");
            if (ju == null || ji == null) { return null; }
            return objNote("巨门落陷且会天机", isFallen("巨门", ju));
        }
        // A3 二曲旺宫威名赫奕
        if (text.contains("二曲旺宫")) {
            Integer qu = branchIndexOf(at, "文曲");
            if (qu == null) { return null; }
            String br = brightnessOf("文曲", qu);
            return objNote("文曲居庙旺（今" + br + "）", "庙".equals(br) || "旺".equals(br));
        }
        // A4 贪狼会杀无吉曜屠宰之人
        if (text.contains("贪狼会杀无吉曜")) {
            Integer t = branchIndexOf(at, "贪狼");
            if (t == null) { return null; }
            boolean sha = shaCountInSanFang(at, t) > 0 && !hasJiInSanFang(at, t);
            return objNote("贪狼三方会煞且无吉曜", sha);
        }
        // A5 巨日命宫寅位食禄驰名
        if (text.contains("巨日命宫寅位")) {
            Integer ju = branchIndexOf(at, "巨门");
            Integer sun = branchIndexOf(at, "太阳");
            if (ju == null || sun == null) { return null; }
            int yin = indexOf(ZHI, "寅");
            return objNote("巨门太阳同在寅且命立寅", ju == yin && sun == ju && mingIdx == ju);
        }
        // A6 巨日命立申宫亦妙
        if (text.contains("巨日命立申宫")) {
            Integer ju = branchIndexOf(at, "巨门");
            Integer sun = branchIndexOf(at, "太阳");
            if (ju == null || sun == null) { return null; }
            int shen = indexOf(ZHI, "申");
            return objNote("巨门太阳同在申", ju == shen && sun == ju);
        }
        // A7 七杀临身终是夭（七杀临命或身）
        if (text.contains("七杀临身终是夭")) {
            Integer q = branchIndexOf(at, "七杀");
            int shen = palaceBranch(skeleton, "身宫");
            if (q == null) { return null; }
            return objNote("七杀临命或身宫", q == mingIdx || q == shen);
        }
        // A8 明禄暗禄位至公卿
        if (text.contains("明禄暗禄")) {
            int anhe = sixHePartner(mingIdx);
            boolean ok = palaceHasLu(mingIdx, at, fourTransform) && palaceHasLu(anhe, at, fourTransform);
            return objNote("命坐禄且暗合（六合）宫坐禄", ok);
        }
        // A9 禄存厚重多衣禄
        if (text.contains("禄存厚重多衣禄")) {
            Integer lu = branchIndexOf(at, "禄存");
            if (lu == null) { return null; }
            return objNote("禄存守命宫", lu == mingIdx);
        }
        // A10 禄逢冲破吉也成凶
        if (text.contains("禄逢冲破")) {
            Integer lu = branchIndexOf(at, "禄存");
            if (lu == null) { return null; }
            return objNote("禄存之三方四正见煞（冲破）", shaCountInSanFang(at, lu) > 0);
        }
        // A11 生处劫空犹如半天折翅
        if (text.contains("生处劫空")) {
            List<String> ml = at.getOrDefault(mingIdx, List.of());
            return objNote("命宫见地劫/天空", ml.contains("地劫") || ml.contains("天空"));
        }
        // A12 劫空临财福之乡生来贫贱
        if (text.contains("劫空临财福")) {
            int cai = palaceBranch(skeleton, "财帛");
            int fu = palaceBranch(skeleton, "福德");
            if (cai < 0 && fu < 0) { return null; }
            boolean a = cai >= 0 && (at.getOrDefault(cai, List.of()).contains("地劫")
                    || at.getOrDefault(cai, List.of()).contains("天空"));
            boolean b = fu >= 0 && (at.getOrDefault(fu, List.of()).contains("地劫")
                    || at.getOrDefault(fu, List.of()).contains("天空"));
            return objNote("空劫落财帛或福德宫", a || b);
        }
        // A13 天马四生妻宫富贵还当封赠
        if (text.contains("天马四生妻宫")) {
            Integer ma = branchIndexOf(at, "天马");
            int qi = palaceBranch(skeleton, "妻妾");
            if (ma == null || qi < 0) { return null; }
            boolean siSheng = ma == indexOf(ZHI, "寅") || ma == indexOf(ZHI, "申")
                    || ma == indexOf(ZHI, "巳") || ma == indexOf(ZHI, "亥");
            return objNote("天马居四生（寅申巳亥）且落夫妻宫", siSheng && ma == qi);
        }
        // A14 命有吉星官杀重纵有财官也辛苦
        if (text.contains("命有吉星官杀重")) {
            boolean ji = hasJiInSanFang(at, mingIdx);
            int shaCnt = shaCountInSanFang(at, mingIdx);
            return objNote("命三方有吉星而煞重（煞≥2）", ji && shaCnt >= 2);
        }
        // A15 双禄守命吕后专权（命宫同见禄存与化禄）
        if (text.contains("双禄守命")) {
            List<String> ml = at.getOrDefault(mingIdx, List.of());
            boolean lu = ml.contains("禄存");
            boolean hua = false;
            if (fourTransform != null) {
                for (String s : ml) { if (fourTransform.contains(s + "化禄")) { hua = true; break; } }
            }
            return objNote("命宫同见禄存与化禄（双禄守命）", lu && hua);
        }
        // A16 双禄重返终身富贵（命三方同会禄存与化禄）
        if (text.contains("双禄重返")) {
            boolean lu = false;
            boolean hua = false;
            for (int b : new int[]{mingIdx, floorMod(mingIdx + 4, 12), floorMod(mingIdx + 8, 12), floorMod(mingIdx + 6, 12)}) {
                for (String s : at.getOrDefault(b, List.of())) {
                    if (s.equals("禄存")) { lu = true; }
                    if (fourTransform != null && fourTransform.contains(s + "化禄")) { hua = true; }
                }
            }
            return objNote("命三方同会禄存与化禄（双禄重返）", lu && hua);
        }
        // A17 日月命身居丑未三方无吉反为凶（子午辰戌身命更佳）
        if (text.contains("日月命身居丑未")) {
            int shen = palaceBranch(skeleton, "身宫");
            boolean anchor = mingIdx == indexOf(ZHI, "丑") || mingIdx == indexOf(ZHI, "未")
                    || shen == indexOf(ZHI, "丑") || shen == indexOf(ZHI, "未");
            return objNoteExt("释读", "命/身居丑未且命三方无吉", anchor && !hasJiInSanFang(at, mingIdx));
        }
        // B1 女命端正太阳星早配贤夫信可凭（陷平常）
        if (text.contains("女命端正太阳星")) {
            if (male == null) { return null; }
            Integer sun = branchIndexOf(at, "太阳");
            if (sun == null) { return null; }
            boolean zheng = "庙".equals(brightnessOf("太阳", sun)) || "旺".equals(brightnessOf("太阳", sun));
            return objNoteExt("外部通行法", "女命太阳庙旺守命（端正）", !male && sun == mingIdx && zheng);
        }
        // B2 七杀单居福德女人切忌贱无疑
        if (text.contains("七杀单居福德")) {
            if (male == null) { return null; }
            Integer q = branchIndexOf(at, "七杀");
            int fu = palaceBranch(skeleton, "福德");
            if (q == null || fu < 0) { return null; }
            return objNoteExt("外部通行法", "七杀独坐福德宫且女命", q == fu && !male);
        }
        // C1 身命俱吉富贵双全
        if (text.contains("身命俱吉富贵双全")) {
            int shen = palaceBranch(skeleton, "身宫");
            if (shen < 0) { return null; }
            return objNoteExt("释读", "命宫、身宫三方四正皆吉",
                    palaceNetJi(at, mingIdx) && palaceNetJi(at, shen));
        }
        // C2 身吉命凶亦为美论
        if (text.contains("身吉命凶亦为美论")) {
            int shen = palaceBranch(skeleton, "身宫");
            if (shen < 0) { return null; }
            return objNoteExt("释读", "身宫三方吉而命宫三方凶", jiShaNet(at, shen) > 0 && jiShaNet(at, mingIdx) < 0);
        }
        // C3 命弱身强财源不聚
        if (text.contains("命弱身强财源不聚")) {
            int shen = palaceBranch(skeleton, "身宫");
            if (shen < 0) { return null; }
            return objNoteExt("释读", "命宫三方弱而身宫三方强", jiShaNet(at, mingIdx) < 0 && jiShaNet(at, shen) > 0);
        }
        // D1 纳音墓库看何宫（水生人墓辰遇财官或禄存尤妙）
        if (text.contains("纳音墓库")) {
            int bureau = bureauFromSkeleton(skeleton);
            if (bureau == 0) { return null; }
            if (bureau != 2) {
                return objNoteExt("外部通行法", "命宫纳音非水（" + bureauName(bureau) + "），本条不适用", false);
            }
            List<String> chen = at.getOrDefault(indexOf(ZHI, "辰"), List.of());
            boolean cond = chen.contains("禄存") || chen.contains("武曲") || chen.contains("天府");
            return objNoteExt("外部通行法", "水命墓辰得禄存/财星", cond);
        }
        return null;
    }

    /**
     * 对「加吉/加煞/昌曲/左右/魁钺/禄马/羊陀/火铃」等 OR 并列条件，按 anchor 宫（该星/该宫所落之支）
     * 三方四正客观复核（《全書》「会照」通例之复算/释读，非原文新增条件、不改第一权威）：
     * 各组为「任一命中即成立」的 OR 语义（与 {@link #judgeTongYuan} 的居宫/并见/生干 AND 判定互补）。
     * 返回如「〔OR客观·加吉=是(文昌)｜加煞=否；锚辰宫三方四正·释读〕」；无 OR 关键词返回 null。
     */
    private static String tongYuanOrNote(final String text, final int anchorBranch, final Map<Integer, List<String>> at) {
        if (text == null || anchorBranch < 0 || at == null) {
            return null;
        }
        Set<String> sf = new HashSet<>();
        for (int b : new int[]{anchorBranch, floorMod(anchorBranch + 4, 12),
                floorMod(anchorBranch + 8, 12), floorMod(anchorBranch + 6, 12)}) {
            sf.addAll(at.getOrDefault(b, List.of()));
        }
        // 合称优先登记，再登记专称；同一 text 可多组并存。
        java.util.LinkedHashMap<String, List<String>> conds = new java.util.LinkedHashMap<>();
        // ★否定守卫：出现「无吉/无煞」等否定语时，不得据「吉曜/煞」字样误判为「加吉/加煞」（避免语义反转）。
        if ((text.contains("加吉") || text.contains("会吉") || text.contains("得吉"))
                || (text.contains("吉曜") && !text.contains("无吉"))) {
            conds.put("加吉", List.of("左辅", "右弼", "文昌", "文曲", "天魁", "天钺"));
        }
        if ((text.contains("加煞") || text.contains("加杀") || text.contains("会煞") || text.contains("逢煞"))
                && !text.contains("无煞")) {
            conds.put("加煞", List.of("擎羊", "陀罗", "火星", "铃星", "地空", "地劫"));
        }
        if (text.contains("昌曲")) conds.put("昌曲", List.of("文昌", "文曲"));
        if (text.contains("左右")) conds.put("左右", List.of("左辅", "右弼"));
        if (text.contains("魁钺")) conds.put("魁钺", List.of("天魁", "天钺"));
        if (text.contains("禄马")) conds.put("禄马", List.of("禄存", "天马"));
        if (text.contains("羊陀")) conds.put("羊陀", List.of("擎羊", "陀罗"));
        if (text.contains("火铃")) conds.put("火铃", List.of("火星", "铃星"));
        if (conds.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, List<String>> e : conds.entrySet()) {
            String hit = null;
            for (String s : e.getValue()) {
                if (sf.contains(s)) { hit = s; break; }
            }
            if (sb.length() > 0) sb.append("｜");
            sb.append(e.getKey()).append("=").append(hit == null ? "否" : "是(" + hit + ")");
        }
        return "〔OR客观·" + sb + "；锚" + ZHI[anchorBranch] + "宫三方四正·释读〕";
    }

    private static Set<String> splitStarSet(String s) {
        Set<String> set = new HashSet<>();
        if (s != null && !s.isEmpty()) {
            for (String p : s.split("、")) {
                if (!p.isEmpty()) {
                    set.add(p);
                }
            }
        }
        return set;
    }

    /** 得地合格诀生年干列是否含指定天干（yearGan 为 GAN 索引）；非天干之讹字（如「申」「巳」）忽略。 */
    private static boolean ganListMatches(String rawGan, int yearGan) {
        if (yearGan < 0) {
            return false;
        }
        for (String g : rawGan.split("、")) {
            g = g.trim();
            if (!g.isEmpty() && indexOf(GAN, g) == yearGan) {
                return true;
            }
        }
        return false;
    }

    /** 得地合格诀生年干列是否含非天干之讹字（原文「申巳庚」之「申」「巳」等）→ 字面存疑，不以生干否决。 */
    private static boolean ganListHasSuspectToken(String rawGan) {
        for (String g : rawGan.split("、")) {
            g = g.trim();
            if (!g.isEmpty() && indexOf(GAN, g) < 0) {
                return true;
            }
        }
        return false;
    }

    private static int[] JIEluKongwang(int gan) {
        switch (gan) {
            case 0: case 5: return new int[]{8, 9};   // 甲己申酉
            case 1: case 6: return new int[]{6, 7};   // 乙庚午未
            case 2: case 7: return new int[]{4, 5};   // 丙辛辰巳
            case 3: case 8: return new int[]{2, 3};   // 丁壬寅卯
            default: return new int[]{0, 1};          // 戊癸子丑
        }
    }

    private static int[] xunZhongKongwang(int gan, int branch) {
        int idx = sexagenaryIndex(gan, branch);
        int xun = idx < 0 ? 0 : idx / 10;
        switch (xun) {
            case 0: return new int[]{10, 11}; // 甲子旬空戌亥
            case 1: return new int[]{8, 9};   // 甲戌旬空申酉
            case 2: return new int[]{6, 7};   // 甲申旬空午未
            case 3: return new int[]{4, 5};   // 甲午旬空辰巳
            case 4: return new int[]{2, 3};   // 甲辰旬空寅卯
            default: return new int[]{0, 1};  // 甲寅旬空子丑
        }
    }

    private static int douJun(int yearBranch, int month, int hourBranch) {
        return floorMod(yearBranch - (month - 1) + hourBranch, 12);
    }

    /**
     * 飞天神煞（奏书/将军/直符）：依出生年支三合飞入（卷二·安飞天三杀诀，原文 c3 L103-106）——
     * 寅午戌年飞入亥卯未宫、申子辰年飞入巳酉丑宫、亥卯未年飞入申子辰宫、巳酉丑年飞入寅午戌宫。
     * 组内序（奏书/将军/直符）《全書》未明列，采〔外部通行法·非《全書》原文〕：奏书＝长生、将军＝帝旺、
     * 直符＝墓（三合局之长生/帝旺/墓），即奏书/将军/直符依次落于所飞三合之长生-帝旺-墓三宫。
     */
    private static int[] feiTianSanSha(int yearBranch) {
        switch (floorMod(yearBranch, 12)) {
            case 2: case 6: case 10: return new int[]{11, 3, 7};  // 寅午戌 → 亥卯未
            case 8: case 0: case 4:  return new int[]{5, 9, 1};   // 申子辰 → 巳酉丑
            case 11: case 3: case 7: return new int[]{8, 0, 4};   // 亥卯未 → 申子辰
            default:                 return new int[]{2, 6, 10};  // 巳酉丑 → 寅午戌
        }
    }

    // ---- 输入解析 ----

    private static final int BRANCH_YIN = 2;

    private static String birthCalendar(Object birthVar) {
        if (birthVar instanceof Map<?, ?> m) {
            Object c = m.get("calendar");
            if (c != null) {
                return String.valueOf(c);
            }
        }
        String s = str(birthVar);
        if (s != null && s.contains("农历")) {
            return "农历";
        }
        return "公历";
    }

    private static LocalDateTime resolveBirthDateTime(Object birthVar) {
        if (birthVar instanceof Map<?, ?> m) {
            String dt = str(m.get("datetime"));
            if (dt != null && !dt.isBlank()) {
                LocalDateTime parsed = tryParse(dt);
                if (parsed != null) {
                    return parsed;
                }
            }
            int y = toInt(str(m.get("year")), 0);
            int mo = toInt(str(m.get("month")), 1);
            int d = toInt(str(m.get("day")), 1);
            int h = toInt(str(m.get("hour")), 0);
            int mi = toInt(str(m.get("minute")), 0);
            if (y > 0) {
                return LocalDateTime.of(y, mo, d, h, mi);
            }
        }
        String s = str(birthVar);
        if (s != null) {
            LocalDateTime parsed = tryParse(s.trim());
            if (parsed != null) {
                return parsed;
            }
        }
        throw new IllegalArgumentException("无法解析生辰：" + birthVar);
    }

    private static LocalDateTime tryParse(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        String v = s.replace("T", " ").trim();
        String[] patterns = {"yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd HH:mm", "yyyy-MM-dd",
                "yyyy/MM/dd HH:mm", "yyyy/MM/dd", "yyyyMMddHHmm"};
        for (String p : patterns) {
            try {
                java.time.format.DateTimeFormatter f = java.time.format.DateTimeFormatter.ofPattern(p);
                if (p.contains("H")) {
                    return LocalDateTime.parse(v, f);
                }
                return LocalDate.parse(v, f).atStartOfDay();
            } catch (Exception ignore) {
                // try next
            }
        }
        return null;
    }

    /** 安星用农历月/日：农历输入直接取；公历输入经转换（无表时以原值近似，标 LUNAR_APPROX）。 */
    private static int[] resolveLunarMonthDay(Object birthVar, String calendar) {
        int month = 1;
        int day = 1;
        if (birthVar instanceof Map<?, ?> m) {
            month = toInt(str(m.get("month")), 1);
            day = toInt(str(m.get("day")), 1);
        } else {
            String s = str(birthVar);
            if (s != null) {
                String[] parts = s.split("[-/ T:]");
                if (parts.length >= 3) {
                    month = toInt(parts[1], 1);
                    day = toInt(parts[2], 1);
                }
            }
        }
        return new int[]{month, day};
    }

    /** 农历→公历：需 OBDA 农历表（第 11 步落地），此前返回 null 触发【待考】标注。 */
    private static LocalDateTime lunarToSolar(String birth) {
        return null;
    }

    /** 从出生地文本解析经度（东经为正、西经为负）；解析不到返回 null。 */
    private static Double extractLongitude(String birthPlace) {
        if (birthPlace == null || birthPlace.isBlank()) {
            return null;
        }
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("([0-9]+(?:\\.[0-9]+)?)").matcher(birthPlace);
        if (!m.find()) {
            return null;
        }
        double lon = Double.parseDouble(m.group(1));
        if (birthPlace.contains("西经") || birthPlace.contains("W") || birthPlace.contains("w")) {
            lon = -lon;
        }
        return lon;
    }

    private static Map<String, String> parseChartBase(String chartBase) {
        Map<String, String> map = new LinkedHashMap<>();
        if (chartBase == null) {
            return map;
        }
        for (String kv : chartBase.split(";")) {
            int i = kv.indexOf('=');
            if (i > 0) {
                map.put(kv.substring(0, i).trim(), kv.substring(i + 1).trim());
            }
        }
        return map;
    }
}

