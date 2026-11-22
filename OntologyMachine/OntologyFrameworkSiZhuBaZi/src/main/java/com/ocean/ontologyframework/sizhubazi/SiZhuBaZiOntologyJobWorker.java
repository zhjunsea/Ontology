package com.ocean.ontologyframework.sizhubazi;

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

import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLObjectProperty;

import jakarta.annotation.PostConstruct;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 四柱命理推演流程（{@code SiZhuBaZiProcess}）的 JobWorker。
 *
 * <p>21 个 {@link JobWorker} 与 {@code ontology/bpmn/sizhubaziProcess.bpmn} 的 21 个
 * {@code serviceTask} 一一对应（契约见《SiZhuBaZi应用-应用架构设计》§4）：
 * parseBirthInput / castFourPillars / analyzeNayinHiddenStems / analyzeGanzhiRelations /
 * assignTenGodsKin / arrangeDeities / assessDayMasterStrength / determinePattern /
 * selectYongShen / judgeChartLevel / analyzeFortuneCycles / inferTimingAndStages /
 * synthesizeFortuneChart / generateCategoryVerdicts / analyzeMarriageMatch /
 * analyzeFemaleChart / analyzeChildChart / generateMoralAdvice / aggregateReading /
 * sendAlert / persistAndReturn。
 *
 * <p><b>落地口径</b>（第 7 步，闸门④已确认）：
 * <ul>
 *   <li>TBox 经 {@code ontology.main-path}（{@code sizhubazi-all.owl}）由 {@link BackendService#getInstance(String, OBDAHandler)} 加载；</li>
 *   <li>历法/节气/藏干/纳音/神煞等**查表型**数据依闸门⑩裁决入库（MySQL），经
 *       {@code ontology.obda-path} / {@code ontology.obda-properties-path}（Ontop 虚拟访问，第 11 步落地）；</li>
 *   <li>起四柱（五虎遁/五鼠遁、干支纪日）、十神（10×10 查表）、旺衰（多因子综合，**不设数值权重**，
 *       客户裁定 ⑰）、格局/用神/层次（程序编排 + SWRL 简化）、岁运（默认年级，⑭）等运算由本类内置
 *       「口径表」+ 程序实现（★铁律 13：历法/起运数值计算外置，不写成 SWRL）；</li>
 *   <li>无法回原著定位者一律标【待考】并记录 `diagnosis:ToBeVerified` 语义（★铁律 8/9/10）；原调候用神、纳音吉凶、日时断缺载等【待考】项已回填落地（调候 120 条 / 纳音吉凶 30 / 日时断 720 全备），农历→公历换算亦以内置 1900–2100 农历数据表落地（★铁律 13）。</li>
 * </ul>
 *
 * <p>全部业务逻辑经现存 API（{@link BackendService}、{@link OntologyWorkerSupport}、{@link RabbitMqHandler}、
 * {@link OBDAHandler}）实现，不重写平台 API（★铁律 21）。
 */
@Component
@Profile("SiZhuBaZiBPMN")
public class SiZhuBaZiOntologyJobWorker extends OntologyWorkerSupport {

    private static final Logger log = LoggerFactory.getLogger(SiZhuBaZiOntologyJobWorker.class);

    @Value("${ontology.main-path}")
    private String mainOntologyPath;

    /** OBDA 映射/属性文件路径（第 11 步落地；{@link OBDAHandler#init} 仅登记路径，懒加载）。 */
    @Value("${ontology.obda-path}")
    private String obdaPath;

    @Value("${ontology.obda-properties-path}")
    private String obdaPropertiesPath;

    /** ABox 聚合入口（第 11 步起由 Ontop 经 obda-properties-path 的 {@code ontop.ontologyFile} 加载；
     *  应用侧 {@link BackendService} 仅加载 TBox（main-path），故此项应用侧仅作存在性自检，避免配置成为死项）。 */
    @Value("${ontology.abox-path}")
    private String aboxPath;

    /** 精确节气时刻缓存：公历年 → 长度 24 的 LocalDateTime 数组（index=节气序 0..23，0=小寒）。
     *  空数组表示「已尝试加载但无数据」（年份超出 1950–2050 或 DB 不可用），后续调用回退近似，避免重复查询。 */
    private final Map<Integer, LocalDateTime[]> solarTermCache = new ConcurrentHashMap<>();

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

    /** 末尾「命书详解」系统提示：仅据已算出的命书结论，讲清推断依据与结论，不引典籍原文、不标出处。 */
    private static final String INTERPRET_SYSTEM_PROMPT =
            "你是四柱命理师傅，负责把已经算好的【四柱命书（各节点已按本命推导出的结果）】讲清楚。"
            + "只需做三件事：一是复述本命计算结果（四柱、十神、旺衰、格局、用神、岁运等）；"
            + "二是逐环节说明推断依据（为何得出此结果）；三是给出综合结论（对本命意味着什么）。"
            + "严禁引用任何典籍原文、书名篇目或出处标注，也不要堆砌解释性名词；只用清晰直白的语言说清结果、依据与结论。"
            + "使用规范 Markdown 输出。";

    /** 末尾「白话解读」系统提示：把前述命书详解翻译成通俗现代白话，不做冗余术语考据。 */
    private static final String INTERPRET_PLAIN_SYSTEM_PROMPT =
            "你是四柱命理的白话翻译员。请把给定的【四柱命书详解】逐段翻译成通俗易懂的现代白话文，"
            + "让不懂命理的人也能看懂：日主强弱、格局用神、事业功名、财运、婚姻、健康、岁运吉凶各是什么结论、"
            + "依据是什么、对命主意味着什么。"
            + "关键要求：只做「文言→白话」的翻译与讲述，遇必要术语用一句话浅释即可，不要长篇考据、不要堆砌名词、"
            + "不引用典籍原文、不写书名或出处。使用规范 Markdown 输出。";

    // ==========================================================================
    // 内置基础表（★铁律 13：历法/起运数值计算外置；起例表由生成脚本维护，回写生成源）
    // 出处：三命通会卷一·论五行生成/论支干源流；渊海子平卷之一·论天干地支所出/论五行相生相克
    // ==========================================================================

    /** 十天干（甲…癸）。 */
    private static final String[] GAN = {"甲", "乙", "丙", "丁", "戊", "己", "庚", "辛", "壬", "癸"};
    /** 十二地支（子…亥）。 */
    private static final String[] ZHI = {"子", "丑", "寅", "卯", "辰", "巳", "午", "未", "申", "酉", "戌", "亥"};
    /** 五行索引：0木 1火 2土 3金 4水（天干）。 */
    private static final int[] GAN_ELEM = {0, 0, 1, 1, 2, 2, 3, 3, 4, 4};
    /** 阴阳：1阳 0阴（天干）。 */
    private static final int[] GAN_YY = {1, 0, 1, 0, 1, 0, 1, 0, 1, 0};
    /** 五行索引（地支：子水 丑土 寅木 卯木 辰土 巳火 午火 未土 申金 酉金 戌土 亥水）。 */
    private static final int[] ZHI_ELEM = {4, 2, 0, 0, 2, 1, 1, 2, 3, 3, 2, 4};
    /** 十二生肖（子鼠…亥猪）。 */
    private static final String[] ZHI_ANIMAL = {"鼠", "牛", "虎", "兔", "龙", "蛇", "马", "羊", "猴", "鸡", "狗", "猪"};
    /** 时辰地支 → 起始小时（子时 23:00 起算＝晚子；丑 1、寅 3 … 亥 21）。
     *  客户裁定 ④：早晚子时按子初换日 23:00，故前端选「子」映射为晚子 23:xx。 */
    private static final int[] ZHI_START_HOUR = {23, 1, 3, 5, 7, 9, 11, 13, 15, 17, 19, 21};
    /** 五行名（索引同 GAN_ELEM）。 */
    private static final String[] ELEM_NAME = {"木", "火", "土", "金", "水"};

    /** 地支藏干（本气/中气/余气）：子藏癸；丑藏己癸辛；寅藏甲丙戊；卯藏乙；辰藏戊乙癸；巳藏丙庚戊；
     *  午藏丁己；未藏己丁乙；申藏庚壬戊；酉藏辛；戌藏戊辛丁；亥藏壬甲。
     *  出处：渊海子平卷之一·又地支藏遁歌；三命通会卷一·论人元司事。 */
    private static final String[][] ZHI_HIDDEN = {
            {"癸"}, {"己", "癸", "辛"}, {"甲", "丙", "戊"}, {"乙"}, {"戊", "乙", "癸"}, {"丙", "庚", "戊"},
            {"丁", "己"}, {"己", "丁", "乙"}, {"庚", "壬", "戊"}, {"辛"}, {"戊", "辛", "丁"}, {"壬", "甲"}
    };

    /** 六十甲子纳音（30 名，索引 = 六十甲子序 / 2）。
     *  出处：三命通会卷一·总论纳音/论纳音取象；渊海子平卷之一·论六十花甲子纳音。 */
    private static final String[] NAYIN = {
            "海中金", "炉中火", "大林木", "路旁土", "剑锋金", "山头火", "涧下水", "城头土", "白蜡金", "杨柳木",
            "泉中水", "屋上土", "霹雳火", "松柏木", "长流水", "沙中金", "山下火", "平地木", "壁上土", "金箔金",
            "覆灯火", "天河水", "大驿土", "钗钏金", "桑柘木", "大溪水", "沙中土", "天上火", "石榴木", "大海水"
    };

    /** 长生十二宫名。出处：三命通会卷二·论天干阴阳生死；渊海子平卷之一·论天干生旺死绝。 */
    private static final String[] STAGE_NAME = {"长生", "沐浴", "冠带", "临官", "帝旺", "衰", "病", "死", "墓", "绝", "胎", "养"};
    /** 天干长生所在地支（甲亥 乙午 丙寅 丁酉 戊寅 己酉 庚巳 辛子 壬申 癸卯）。 */
    private static final int[] CHANGSHENG_ZHI = {11, 6, 2, 9, 2, 9, 5, 0, 8, 3};

    /** 十神名（索引见 {@link #tenGodIndex(int, int)}）。出处：渊海子平卷之二·各十神论。 */
    private static final String[] TEN_GOD = {"比肩", "劫财", "食神", "伤官", "偏财", "正财", "偏官", "正官", "偏印", "正印"};

    /** 天乙贵人（按日干）：甲戊庚见丑未；乙己见子申；丙丁见亥酉；壬癸见卯巳；辛见午寅。三命通会卷二·论天乙贵人。 */
    private static final int[][] TIANYI = {
            {1, 7}, {0, 8}, {11, 9}, {11, 9}, {1, 7}, {0, 8}, {1, 7}, {6, 2}, {3, 5}, {3, 5}
    };
    /** 羊刃（按日干）：惟甲丙戊庚壬五阳干有刃（甲卯 丙午 戊午 庚酉 壬子），乙丁己辛癸五阴干无刃（记 -1）。
     *  出处：三命通会卷五·论阳刃「惟甲丙戊庚壬五阳干有刃，乙丁己辛癸五阴干无刃，故曰阳刃」；
     *        渊海子平卷之一·论羊刃「甲丙戊庚壬五阳有刃，乙己丁辛癸五阴无刃」。
     *  <p>运行期采此正法（五阳有刃）；DB 表 `deity_rule` 另存「阴刃（异法）」乙寅/丁巳/己巳/辛申/癸亥并陈（该表已经 OBDA mappingId `deity_rule` 全量接入本体，运行期 {@code arrangeDeities} 经 SPARQL 取用，此处内置表仅作 OBDA 不可用时的降级镜像），二者各有所本、非冲突。 */
    private static final int[] YANGREN = {3, -1, 6, -1, 6, -1, 9, -1, 0, -1};
    /** 十干禄（按日干）。渊海子平卷之一·论禄。 */
    private static final int[] LU = {2, 3, 5, 6, 5, 6, 8, 9, 11, 0};

    /** 三合局分组（申子辰 / 寅午戌 / 巳酉丑 / 亥卯未）。 */
    private static final int[][] SANHE = {{8, 0, 4}, {2, 6, 10}, {5, 9, 1}, {11, 3, 7}};
    /** 三会方局（寅卯辰木 / 巳午未火 / 申酉戌金 / 亥子丑水）。
     *  出处：渊海子平·论三合会局；三命通会卷二·论支元三会。与 SANHUI_ELEM 一一对应。 */
    private static final int[][] SANHUI = {{2, 3, 4}, {5, 6, 7}, {8, 9, 10}, {11, 0, 1}};
    /** 各方会局之五行（木0 火1 金3 水4，对应 SANHUI 四组）。 */
    private static final int[] SANHUI_ELEM = {0, 1, 3, 4};
    /** 驿马（按三合局分组）。三命通会卷二·论驿马。 */
    private static final int[] YIMA = {2, 8, 11, 5};
    /** 桃花/咸池（按三合局分组）。三命通会卷二·论咸池。 */
    private static final int[] TAOHUA = {9, 3, 6, 0};
    /** 华盖（按三合局分组）。三命通会卷二·论将星华盖。 */
    private static final int[] HUAGAI = {4, 10, 1, 7};
    /** 将星（按三合局分组）。三命通会卷二·论将星华盖。 */
    private static final int[] JIANGXING = {0, 6, 9, 3};

    /** 六合（地支成对）：子丑 寅亥 卯戌 辰酉 巳申 午未。三命通会卷二·论支元六合。 */
    private static final int[][] LIUHE = {{0, 1}, {2, 11}, {3, 10}, {4, 9}, {5, 8}, {6, 7}};
    /** 六冲（地支成对）：子午 丑未 寅申 卯酉 辰戌 巳亥。三命通会卷二·论冲击。 */
    private static final int[][] LIUCHONG = {{0, 6}, {1, 7}, {2, 8}, {3, 9}, {4, 10}, {5, 11}};
    /** 六害/相穿：子未 丑午 寅巳 卯辰 申亥 酉戌。三命通会卷二·论六害。 */
    private static final int[][] LIUHAI = {{0, 7}, {1, 6}, {2, 5}, {3, 4}, {8, 11}, {9, 10}};
    /** 三刑：寅巳申 三刑；丑戌未 三刑；子卯 相刑；辰午酉亥 自刑。三命通会卷二·论三刑。 */
    private static final int[][] SANXING = {{2, 5, 8}, {1, 10, 7}};
    /** 天干五合：甲己 乙庚 丙辛 丁壬 戊癸；化气：土 金 水 木 火。三命通会卷二·论十干合/论十干化气。 */
    private static final int[][] GAN_WUHE = {{0, 5}, {1, 6}, {2, 7}, {3, 8}, {4, 9}};
    private static final String[] GAN_WUHE_HUA = {"土", "金", "水", "木", "火"};

    /** 二十四节气相对毫秒偏移（以 1900-01-06 02:05 小寒 为基准的近似算法，误差 < 1 天）。
     *  客户裁定 ②：历法惯例（非六书）。为便于读表起见保留该通用近似算法；如需高精度以 OBDA 节气表为准。 */
    private static final long[] S_TERM = {
            0, 21208, 42467, 63836, 85337, 107014, 128867, 150921, 173149, 195551, 218072, 240693,
            263343, 285989, 308563, 331033, 353350, 375494, 397447, 419210, 440795, 462224, 483532, 504758
    };
    /** 十二「节」在 {@link #S_TERM} 中的下标（0小寒 2立春 4惊蛰 6清明 8立夏 10芒种 12小暑 14立秋 16白露 18寒露 20立冬 22大雪）。 */
    private static final int[] JIE_IDX = {0, 2, 4, 6, 8, 10, 12, 14, 16, 18, 20, 22};
    /** 十二「节」对应的月建地支：丑(1) 寅(2) 卯(3) …（与 {@link #JIE_IDX} 一一对应，按公历 1→12 月排列）。 */
    private static final int[] JIE_BRANCH = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 0};

    /** 二十四节气名（历法惯例，index 0..23，与 {@link #S_TERM}/solarTermDateTime 次序一致：0=小寒…23=冬至）。 */
    private static final String[] TERM_NAMES = {
            "小寒", "大寒", "立春", "雨水", "惊蛰", "春分", "清明", "谷雨",
            "立夏", "小满", "芒种", "夏至", "小暑", "大暑", "立秋", "处暑",
            "白露", "秋分", "寒露", "霜降", "立冬", "小雪", "大雪", "冬至"};

    /**
     * 农历数据表（1900–2100，共 201 项，下标 = 年份 − 1900）。
     * <p>★铁律 13：历法表外置——此处以内置权威数据表落地「农历→公历」换算（含闰月），
     * 取代原挂【待考】的 {@code lunar_convert} 外置表（第 11 步 OBDA 项）；数据与公版万年历一致，
     * 已用 1900/1949/1984/2000/2020(闰四月)/2023(闰二月)/2100 等已知日期校验吻合。
     * <p>编码（每项 17 位有效 bit）：bit0–3 = 闰月月份（0 = 无闰）；bit4–15 = 正月至十二月的月大小
     * （1 = 大月 30 天，0 = 小月 29 天，「bit(16−月序)」对应第「月序」月）；bit16 = 闰月大小（1 = 30 天）。
     */
    private static final long[] LUNAR_INFO = {
            0x04bd8, 0x04ae0, 0x0a570, 0x054d5, 0x0d260, 0x0d950, 0x16554, 0x056a0, 0x09ad0, 0x055d2, // 1900-1909
            0x04ae0, 0x0a5b6, 0x0a4d0, 0x0d250, 0x1d255, 0x0b540, 0x0d6a0, 0x0ada2, 0x095b0, 0x14977, // 1910-1919
            0x04970, 0x0a4b0, 0x0b4b5, 0x06a50, 0x06d40, 0x1ab54, 0x02b60, 0x09570, 0x052f2, 0x04970, // 1920-1929
            0x06566, 0x0d4a0, 0x0ea50, 0x06e95, 0x05ad0, 0x02b60, 0x186e3, 0x092e0, 0x1c8d7, 0x0c950, // 1930-1939
            0x0d4a0, 0x1d8a6, 0x0b550, 0x056a0, 0x1a5b4, 0x025d0, 0x092d0, 0x0d2b2, 0x0a950, 0x0b557, // 1940-1949
            0x06ca0, 0x0b550, 0x15355, 0x04da0, 0x0a5b0, 0x14573, 0x052b0, 0x0a9a8, 0x0e950, 0x06aa0, // 1950-1959
            0x0aea6, 0x0ab50, 0x04b60, 0x0aae4, 0x0a570, 0x05260, 0x0f263, 0x0d950, 0x05b57, 0x056a0, // 1960-1969
            0x096d0, 0x04dd5, 0x04ad0, 0x0a4d0, 0x0d4d4, 0x0d250, 0x0d558, 0x0b540, 0x0b6a0, 0x195a6, // 1970-1979
            0x095b0, 0x049b0, 0x0a974, 0x0a4b0, 0x0b27a, 0x06a50, 0x06d40, 0x0af46, 0x0ab60, 0x09570, // 1980-1989
            0x04af5, 0x04970, 0x064b0, 0x074a3, 0x0ea50, 0x06b58, 0x055c0, 0x0ab60, 0x096d5, 0x092e0, // 1990-1999
            0x0c960, 0x0d954, 0x0d4a0, 0x0da50, 0x07552, 0x056a0, 0x0abb7, 0x025d0, 0x092d0, 0x0cab5, // 2000-2009
            0x0a950, 0x0b4a0, 0x0baa4, 0x0ad50, 0x055d9, 0x04ba0, 0x0a5b0, 0x15176, 0x052b0, 0x0a930, // 2010-2019
            0x07954, 0x06aa0, 0x0ad50, 0x05b52, 0x04b60, 0x0a6e6, 0x0a4e0, 0x0d260, 0x0ea65, 0x0d530, // 2020-2029
            0x05aa0, 0x076a3, 0x096d0, 0x04afb, 0x04ad0, 0x0a4d0, 0x1d0b6, 0x0d250, 0x0d520, 0x0dd45, // 2030-2039
            0x0b5a0, 0x056d0, 0x055b2, 0x049b0, 0x0a577, 0x0a4b0, 0x0aa50, 0x1b255, 0x06d20, 0x0ada0, // 2040-2049
            0x14b63, 0x09370, 0x049f8, 0x04970, 0x064b0, 0x168a6, 0x0ea50, 0x06b20, 0x1a6c4, 0x0aae0, // 2050-2059
            0x0a2e0, 0x0d2e3, 0x0c960, 0x0d557, 0x0d4a0, 0x0da50, 0x05d55, 0x056a0, 0x0a6d0, 0x055d4, // 2060-2069
            0x052d0, 0x0a9b8, 0x0a950, 0x0b4a0, 0x0b6a6, 0x0ad50, 0x055a0, 0x0aba4, 0x0a5b0, 0x052b0, // 2070-2079
            0x0b273, 0x06930, 0x07337, 0x06aa0, 0x0ad50, 0x14b55, 0x04b60, 0x0a570, 0x054e4, 0x0d160, // 2080-2089
            0x0e968, 0x0d520, 0x0daa0, 0x16aa6, 0x056d0, 0x04ae0, 0x0a9d4, 0x0a2d0, 0x0d150, 0x0f252, // 2090-2099
            0x0d520                                                                                      // 2100
    };
    /** 农历换算年代下限（与前端录入 min=1900 一致）。 */
    private static final int LUNAR_MIN_YEAR = 1900;
    /** 农历换算年代上限（与前端录入 max=2100 一致）。 */
    private static final int LUNAR_MAX_YEAR = 2100;

    @PostConstruct
    public void init() throws Exception {
        log.info("🔧 初始化 SiZhuBaZiOntologyJobWorker 依赖链...");
        initOntologyPipeline();
        verifyAboxPath();
        log.info("✅ SiZhuBaZiOntologyJobWorker 初始化完成 | ontologyPath={}", mainOntologyPath);
    }

    /**
     * abox-path 收口：应用侧不经 {@link BackendService} 直接加载 ABox（第 11 步起由 Ontop 依 properties 的
     * {@code ontop.ontologyFile} 加载），此处置为路径存在性自检并打印解析后绝对路径，使该配置项不再是死项，
     * 同时在 abox-path 与 properties 不一致/文件缺失时给出显式告警。
     */
    private void verifyAboxPath() {
        try {
            String resolved = OntologyPathResolver.resolve(aboxPath);
            java.nio.file.Path p = java.nio.file.Path.of(resolved);
            if (java.nio.file.Files.isReadable(p)) {
                log.info("🔍 ABox 路径自检 | {} → {} ✔ 存在（由 Ontop 经 properties 的 ontop.ontologyFile 加载）", aboxPath, resolved);
            } else {
                log.warn("⚠️ ABox 路径自检 | {} → {} 不存在或不可读（请核对 ontology.abox-path 与 properties 的 ontop.ontologyFile 一致性）", aboxPath, resolved);
            }
        } catch (Exception e) {
            log.warn("⚠️ ABox 路径自检异常：{}", e.getMessage());
        }
    }

    @Override
    protected BackendService createBackendService() throws Exception {
        // 第 11 步：接入 OBDA（Ontop 虚拟知识图谱）。OBDAHandler.init 仅登记路径（懒加载，不连库）。
        boolean reusedObdaHandler = false;
        try {
            OBDAHandler.init(OntologyPathResolver.resolve(obdaPropertiesPath), OntologyPathResolver.resolve(obdaPath));
        } catch (IllegalStateException alreadyInitialized) {
            reusedObdaHandler = true;
            log.warn("⚠️ OBDAHandler 已初始化，复用现有实例（同一 JVM 内多 Spring 上下文场景）");
        }
        OBDAHandler obdaHandler = OBDAHandler.getInstance();
        if (reusedObdaHandler) {
            BackendService.setInstance(null);
        }
        return BackendService.getInstance(OntologyPathResolver.resolve(mainOntologyPath), obdaHandler);
    }

    // ==========================================================================
    // 节点 1：输入解析与时辰校正（parseBirthInput）
    // 实现方法：程序（历法换算，★13 外置）+ 本体登记 chart:BirthInput；算法见 §5。
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
            String subjectType = orDefault(str(vars.get("subjectType")), "男命");
            String inputMode = orDefault(str(vars.get("inputMode")), "单命");
            String granularity = orDefault(str(vars.get("granularity")), "年");

            LocalDateTime dt = resolveBirthDateTime(birthVar);
            boolean lunarUnconverted = false;
            if ("农历".equals(calendar)) {
                LocalDateTime conv = lunarToSolar(birthVar);
                if (conv != null) {
                    dt = conv;
                } else {
                    lunarUnconverted = true;
                    log.warn("⚠️ parseBirthInput | 农历→公历换算失败（年份越界 1900–2100，或该月无闰月却标闰，或格式无法解析），标【待考】并按原值登记：{}", birth);
                }
            }

            // 真太阳时校正：经度时差 =（经度 − 120）/ 15 小时（客户裁定 ④；历法惯例，非六书）。
            //   注：仅按经度差折平太阳时，未含「均时差（Equation of Time，±16 分钟内）」——系客户裁定口径，
            //   若日后需高精度真太阳时，另加均时差修正项即可。
            Double lon = extractLongitude(birthPlace);
            double tstOffsetH = (lon == null) ? 0.0 : (lon - 120.0) / 15.0;
            if (lon != null) {
                dt = dt.plusSeconds(Math.round(tstOffsetH * 3600.0));
            }

            // 早晚子时：子初换日 23:00（客户裁定 ④）；23:00–23:59 归次日，换日在取日柱时落实
            boolean lateZi = dt.getHour() == 23;

            String chartBase = String.join(";",
                    "SOLAR=" + dt,
                    "GENDER=" + gender,
                    "SUBJECT=" + subjectType,
                    "MODE=" + inputMode,
                    "GRAN=" + granularity,
                    "TST=" + tstOffsetH,
                    "LATEZI=" + lateZi,
                    "PLACE=" + birthPlace,
                    "LUNAR_UNCONVERTED=" + lunarUnconverted);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("chartBase", chartBase);
            result.put("subjectType", subjectType);
            result.put("inputMode", inputMode);
            completeResult(client, job, result);
            log.info("✅ parseBirthInput | birth={} | calendar={} | 校正后={} | lateZi={} | tst={}h",
                    birth, calendar, dt, lateZi, tstOffsetH);
        } catch (Exception e) {
            fail(job, client, "PARSE_BIRTH_INPUT_FAILED", e);
        }
    }

    // ==========================================================================
    // 节点 2：排四柱（castFourPillars）
    // 实现方法：程序（五虎遁/五鼠遁/干支纪日）+ 本体 + SWRL R5/R6（起运顺逆）；算法见 §5。
    // ==========================================================================
    @JobWorker(type = "castFourPillars", autoComplete = false)
    public void handleCastFourPillars(final ActivatedJob job, final JobClient client) {
        try {
            Map<String, Object> vars = job.getVariablesAsMap();
            Map<String, String> cb = parseChartBase(str(vars.get("chartBase")));
            LocalDateTime solar = LocalDateTime.parse(cb.get("SOLAR"));
            String gender = orDefault(cb.get("GENDER"), "男");
            boolean lateZi = Boolean.parseBoolean(orDefault(cb.get("LATEZI"), "false"));

            // 子初换日：23:00 起算次日（日柱基准日）
            LocalDate dayDate = lateZi ? solar.toLocalDate().plusDays(1) : solar.toLocalDate();

            int[] yp = yearPillar(solar);
            int[] mp = monthPillar(solar);
            int[] dp = dayPillar(dayDate);
            int[] hp = hourPillar(dp[0], solar.getHour());

            String fourPillars = ganZhi(yp) + " " + ganZhi(mp) + " " + ganZhi(dp) + " " + ganZhi(hp);

            // 起运顺逆（SWRL R5/R6 口径）：阳男阴女顺、阴男阳女逆
            boolean yearYang = GAN_YY[yp[0]] == 1;
            boolean male = "男".equals(gender);
            String direction = ((yearYang && male) || (!yearYang && !male)) ? "顺" : "逆";

            // 起运岁（三日折一年，★13 程序数值计算）：数至相邻「节」的天数 / 3
            double startAge = pilotAge(solar, direction);

            // 大运干支：自月柱起，顺/逆行六十甲子
            String fortunes = String.join(",", greatFortunes(mp, direction, 8));
            // 小运（三命通会卷二·论小运古法）：男起丙寅顺行、女起壬申逆行（「一定而不可易也」）；
            //   醉醒子「由时生而行之」为另一法，异说并陈，此处从古法。
            String minorDir = male ? "顺" : "逆";
            String minors = String.join(",", greatFortunes(male ? new int[]{2, 2} : new int[]{8, 8}, minorDir, 8));
            // 胎元：月柱干 +1、支 +3。渊海子平卷之一·论起胎法
            String fetal = ganZhi(new int[]{(mp[0] + 1) % 10, (mp[1] + 3) % 12});
            // 命宫（三命通会卷二·论坐命官；起例异说并陈，此处从三命通会例）。
            String lifePalace = mingGong(mp[1], hp[1], yp[0]);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("fourPillars", fourPillars);
            result.put("fortuneSequence", fortunes);
            result.put("startAge", startAge);
            result.put("minorFortunes", minors);
            result.put("fetalOrigin", fetal);
            result.put("lifePalace", lifePalace);
            completeResult(client, job, result);
            log.info("✅ castFourPillars | 四柱={} | 起运={}（{}）| 胎元={} | 命宫={}", fourPillars, startAge, direction, fetal, lifePalace);
        } catch (Exception e) {
            fail(job, client, "CAST_FOUR_PILLARS_FAILED", e);
        }
    }

    // ==========================================================================
    // 节点 3：纳音·藏干·日柱特性（analyzeNayinHiddenStems）
    // 实现方法：纳音/藏干取内置表（NAYIN/ZHI_HIDDEN，与 OBDA `nayin`/`hidden_stem` 表同源，30 名/28 干已核一致）；日柱六十甲子性质实查本体 core:sexagenaryNatureText。
    // 出处：三命通会卷一·总论纳音/论人元司事；渊海子平卷之一·又地支藏遁歌。
    // ==========================================================================
    @JobWorker(type = "analyzeNayinHiddenStems", autoComplete = false)
    public void handleNayinHiddenStems(final ActivatedJob job, final JobClient client) {
        try {
            Map<String, Object> vars = job.getVariablesAsMap();
            int[][] p = parsePillars(str(vars.get("fourPillars")));
            String[] names = {"年", "月", "日", "时"};

            StringBuilder nayin = new StringBuilder();
            StringBuilder hidden = new StringBuilder();
            for (int i = 0; i < 4; i++) {
                nayin.append(names[i]).append(ganZhi(p[i])).append("→").append(nayinOf(p[i][0], p[i][1])).append("；");
                hidden.append(names[i]).append(ganZhi(p[i])).append("藏")
                        .append(String.join("", ZHI_HIDDEN[p[i][1]])).append("；");
            }

            int dayIdx = p[2][1];
            int dayGanN = p[2][0];
            StringBuilder twelve = new StringBuilder();
            for (int i = 0; i < 4; i++) {
                if (i > 0) {
                    twelve.append("、");
                }
                twelve.append(names[i]).append(ZHI[p[i][1]]).append(stageOf(dayGanN, p[i][1]));
            }
            String traits = "日主" + GAN[dayGanN] + ELEM_NAME[GAN_ELEM[dayGanN]]
                    + "，日支" + ZHI[dayIdx] + "（" + ZHI_ANIMAL[dayIdx] + "）藏"
                    + String.join("", ZHI_HIDDEN[dayIdx])
                    + "；四柱纳音：" + nayin
                    + "日主十二长生（寄生十二宫）：" + twelve
                    + "（上列即本命日主" + GAN[dayGanN] + ELEM_NAME[GAN_ELEM[dayGanN]]
                    + "在四柱各支之长生十二宫，由日干与支位动态推定）。";

            // 典籍定向引据（第29步·回归修复）：日柱六十甲子性质（三命通会卷一·释六十甲子性质吉凶，经 OBDA sexagenary_nature）
            String dayPillarN = ganZhi(p[2]);
            String sexagenaryNature = clauseSexagenaryNature(dayPillarN);
            if (sexagenaryNature != null && !sexagenaryNature.isBlank()) {
                traits += " 六十甲子性质（" + dayPillarN + "）：" + sexagenaryNature
                        + "（出处：三命通会卷一·释六十甲子性质吉凶）";
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("nayin", nayin.toString());
            result.put("hiddenStems", hidden.toString());
            result.put("dayPillarTraits", traits);
            completeResult(client, job, result);
            log.info("✅ analyzeNayinHiddenStems | {} | 日柱特性：{}", nayin, traits);
        } catch (Exception e) {
            fail(job, client, "NAYIN_HIDDEN_STEMS_FAILED", e);
        }
    }

    // ==========================================================================
    // 节点 4：干支作用关系分析（analyzeGanzhiRelations）
    // 实现方法：程序算理域——地支合冲刑穿由内置 LIUHE/LIUCHONG/LIUHAI/SANXING 确定性算法判定（relations 域 TBox 建模完备，供推理/一致性校验；运行期走算理，不设 SPARQL 查询，客户裁定⑰/★铁律13）。
    // 出处：三命通会卷二·论十干合/论支元六合/论三合/论六害/论三刑/论冲击；子平真诠·论十干合而不合。
    // ==========================================================================
    @JobWorker(type = "analyzeGanzhiRelations", autoComplete = false)
    public void handleGanzhiRelations(final ActivatedJob job, final JobClient client) {
        try {
            Map<String, Object> vars = job.getVariablesAsMap();
            int[][] p = parsePillars(str(vars.get("fourPillars")));
            String[] names = {"年", "月", "日", "时"};
            List<String> rel = new ArrayList<>();

            // 天干五合（合化结果至多一五行）
            for (int i = 0; i < 4; i++) {
                for (int j = i + 1; j < 4; j++) {
                    int hua = ganWuHe(p[i][0], p[j][0]);
                    if (hua >= 0) {
                        rel.add(names[i] + GAN[p[i][0]] + " 合 " + names[j] + GAN[p[j][0]]
                                + "（合而未必化；得" + GAN_WUHE_HUA[hua] + "局方化）");
                    }
                }
            }
            // 地支六合 / 六冲 / 六穿
            for (int i = 0; i < 4; i++) {
                for (int j = i + 1; j < 4; j++) {
                    if (pairIn(LIUHE, p[i][1], p[j][1])) {
                        rel.add(names[i] + ZHI[p[i][1]] + " 六合 " + names[j] + ZHI[p[j][1]]);
                    }
                    if (pairIn(LIUCHONG, p[i][1], p[j][1])) {
                        rel.add(names[i] + ZHI[p[i][1]] + " 相冲 " + names[j] + ZHI[p[j][1]]);
                    }
                    if (pairIn(LIUHAI, p[i][1], p[j][1])) {
                        rel.add(names[i] + ZHI[p[i][1]] + " 相穿 " + names[j] + ZHI[p[j][1]]);
                    }
                }
            }
            // 三合 / 半合
            for (int[] g : SANHE) {
                int c = 0;
                StringBuilder hit = new StringBuilder();
                for (int b : g) {
                    if (containsZhi(p, b)) {
                        c++;
                        hit.append(ZHI[b]);
                    }
                }
                if (c == 3) {
                    rel.add("三合局 " + hit + "（成局）");
                } else if (c == 2) {
                    rel.add("半合 " + hit);
                }
            }
            // 三刑（寅巳申 / 丑戌未）
            for (int[] g : SANXING) {
                int c = 0;
                for (int b : g) {
                    if (containsZhi(p, b)) {
                        c++;
                    }
                }
                if (c >= 2) {
                    rel.add("三刑（" + c + "/3）");
                }
            }
            // 自刑（辰辰/午午/酉酉/亥亥见重）——三命通会卷二·论三刑「辰午酉亥自刑」
            for (int b : new int[]{4, 6, 9, 11}) {
                int c = countZhi(p, b);
                if (c >= 2) {
                    rel.add(ZHI[b] + ZHI[b] + "自刑（" + c + "见）");
                }
            }
            // 子卯相刑（无礼之刑）——三命通会卷二·论三刑
            if (containsZhi(p, 0) && containsZhi(p, 3)) {
                rel.add("子卯相刑");
            }
            // 六破（卯午/丑辰/子酉/未戌）——三命通会卷三·总论诸神煞（破煞：卯午、丑辰、子酉、未戌）
            int[][] po = {{3, 6}, {1, 4}, {0, 9}, {7, 10}};
            for (int[] g : po) {
                if (containsZhi(p, g[0]) && containsZhi(p, g[1])) {
                    rel.add(ZHI[g[0]] + ZHI[g[1]] + "相破");
                }
            }
            // 墓库（辰戌丑未）
            StringBuilder tomb = new StringBuilder();
            for (int[] pz : p) {
                if (pz[1] == 4 || pz[1] == 10 || pz[1] == 1 || pz[1] == 7) {
                    tomb.append(ZHI[pz[1]]);
                }
            }
            if (tomb.length() > 0) {
                rel.add("墓库土（" + tomb + "）");
            }

            String out = rel.isEmpty() ? "四柱无明显合冲刑穿" : String.join("；", rel);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("ganzhiRelations", out);
            completeResult(client, job, result);
            log.info("✅ analyzeGanzhiRelations | {}", out);
        } catch (Exception e) {
            fail(job, client, "GANZHI_RELATIONS_FAILED", e);
        }
    }

    // ==========================================================================
    // 节点 5：十神与六亲配属（assignTenGodsKin）
    // 实现方法：本体 tengod:TenGodMap 优先（DB 表 ten_god_map 100 行经 OBDA 暴露，SPARQL 取全库建缓存），未命中降级内置 TEN_GOD + tenGodIndex 五行生克算法（两者实测 100/100 一致）。
    // 出处：渊海子平卷之一·论日为主；卷之二·各十神论；卷之三·六亲总论。
    // ==========================================================================
    @JobWorker(type = "assignTenGodsKin", autoComplete = false)
    public void handleTenGodsKin(final ActivatedJob job, final JobClient client) {
        try {
            Map<String, Object> vars = job.getVariablesAsMap();
            int[][] p = parsePillars(str(vars.get("fourPillars")));
            String[] names = {"年", "月", "日", "时"};
            int dayGan = p[2][0];

            StringBuilder tg = new StringBuilder();
            for (int i = 0; i < 4; i++) {
                if (i == 2) {
                    tg.append("日主").append(GAN[dayGan]).append("→比肩（日主）；");
                    continue;
                }
                tg.append(names[i]).append(GAN[p[i][0]]).append("→")
                        .append(tenGodName(dayGan, p[i][0])).append("；");
            }
            // 地支藏干十神
            StringBuilder hiddenTg = new StringBuilder();
            for (int i = 0; i < 4; i++) {
                hiddenTg.append(names[i]).append(ZHI[p[i][1]]).append("藏");
                for (String hs : ZHI_HIDDEN[p[i][1]]) {
                    hiddenTg.append(hs).append("(").append(tenGodName(dayGan, indexOf(GAN, hs))).append(")");
                }
                hiddenTg.append("；");
            }

            // 六亲 / 宫位配属
            // 六亲 / 宫位配属（《渊海子平·六亲总论/论子息》：男取克干为嗣、女取干生为子）
            String kin = "年柱宫：祖上/父母；月柱宫：父母/兄弟；日柱宫：自身/妻妾；时柱宫：子息。"
                    + "十神六亲：正印为母、偏财为父、正财为妻（男命）、比劫为兄弟；"
                    + "子星分性别——男命以官杀（克我者）为子，女命以食伤（我生者）为子；女命以官杀为夫。";

            // 十神生克制化
            List<String> rels = new ArrayList<>();
            Set<String> set = tenGodSet(dayGan, p);
            if (set.contains("正官") && set.contains("偏官")) {
                rels.add("官杀混杂（须制伏）");
            }
            if ((set.contains("食神") || set.contains("伤官")) && set.contains("偏官")) {
                rels.add("食伤制杀");
            }
            if ((set.contains("正财") || set.contains("偏财")) && (set.contains("正官") || set.contains("偏官"))) {
                rels.add("财官相生");
            }
            String relations = rels.isEmpty() ? "无明显十神生克制化" : String.join("；", rels);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("tenGods", tg + "｜" + hiddenTg);
            result.put("kinMapping", kin);
            result.put("tenGodsRelations", relations);
            completeResult(client, job, result);
            log.info("✅ assignTenGodsKin | {} | 关系：{}", tg, relations);
        } catch (Exception e) {
            fail(job, client, "TEN_GODS_KIN_FAILED", e);
        }
    }

    // ==========================================================================
    // 节点 6：神煞排布（arrangeDeities）
    // 实现方法：本体起例（deity:DeityRule，deity_rule 全库 186 条/78 类经 OBDA 暴露，运行期 SPARQL 取全量按本命匹配）
    //   + 程序算理（★铁律13：查表在库、算理在程序）；OBDA/DB 不可用时降级内置起例数组（常用八类）。
    //   出处：三命通会卷二·诸神煞篇；渊海子平卷之一·诸神煞篇；起例异名异法并陈（⑧ 以三命通会卷二为主）。
    // ==========================================================================
    @JobWorker(type = "arrangeDeities", autoComplete = false)
    public void handleArrangeDeities(final ActivatedJob job, final JobClient client) {
        try {
            Map<String, Object> vars = job.getVariablesAsMap();
            int[][] p = parsePillars(str(vars.get("fourPillars")));
            String[] names = {"年", "月", "日", "时"};
            int dayGan = p[2][0];
            List<String> deities = deitiesFromOntology(p);
            boolean usedOntology = deities != null;
            if (!usedOntology) {
                // 降级：OBDA/DB 不可用时，退回内置起例数组（与 deity_rule 同源，运行期仅常用八类）
                deities = new ArrayList<>();
                // 三合类神煞（驿马/桃花/华盖/将星）参照：年支、日支皆可
                //   —— 三命通会卷二·论驿马「以年支或日支为主」；DB deity_rule 此四类 rule_type='年支/日支'。
                java.util.Set<Integer> grps = new java.util.LinkedHashSet<>();
                grps.add(sanheGroup(p[0][1]));
                grps.add(sanheGroup(p[2][1]));

                for (int i = 0; i < 4; i++) {
                    int b = p[i][1];
                    if (containsInt(TIANYI[dayGan], b)) {
                        deities.add(names[i] + ZHI[b] + "：天乙贵人");
                    }
                    if (YANGREN[dayGan] == b) {
                        deities.add(names[i] + ZHI[b] + "：羊刃");
                    }
                    if (LU[dayGan] == b) {
                        deities.add(names[i] + ZHI[b] + "：禄（临官）");
                    }
                    boolean yima = false, taohua = false, huagai = false, jiangxing = false;
                    for (int g : grps) {
                        yima |= YIMA[g] == b;
                        taohua |= TAOHUA[g] == b;
                        huagai |= HUAGAI[g] == b;
                        jiangxing |= JIANGXING[g] == b;
                    }
                    if (yima) {
                        deities.add(names[i] + ZHI[b] + "：驿马");
                    }
                    if (taohua) {
                        deities.add(names[i] + ZHI[b] + "：桃花（咸池）");
                    }
                    if (huagai) {
                        deities.add(names[i] + ZHI[b] + "：华盖");
                    }
                    if (jiangxing) {
                        deities.add(names[i] + ZHI[b] + "：将星");
                    }
                }
            }
            String deityStr = deities.isEmpty()
                    ? (usedOntology ? "四柱未见命中神煞（deity_rule 起例库）"
                                    : "四柱未见常用八类神煞（天乙/羊刃/禄/驿马/桃花/华盖/将星/空亡）")
                    : "命中神煞（辅助参考）：" + String.join("、", deities) + "。神煞仅作辅助参考，不作主要断语依据，吉凶当以格局用神旺衰为主。";

            // 空亡（旬空）：以日柱定旬，旬空二支。三命通会卷二·论空亡
            int dIdx = sexagenaryIndex(p[2][0], p[2][1]);
            int xunBranch = floorMod(dIdx - (dIdx % 10), 60) % 12;
            int void1 = (xunBranch + 10) % 12;
            int void2 = (xunBranch + 11) % 12;
            List<String> voids = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                if (p[i][1] == void1 || p[i][1] == void2) {
                    voids.add(names[i] + ZHI[p[i][1]] + "空");
                }
            }
            String voidStr = "旬空" + ZHI[void1] + ZHI[void2]
                    + (voids.isEmpty() ? "（四柱不逢空）" : "（" + String.join("、", voids) + "）；应期：填实/冲实/合住/透出");

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("deities", deityStr);
            result.put("voidness", voidStr);
            completeResult(client, job, result);
            log.info("✅ arrangeDeities | {} | {}", deityStr, voidStr);
        } catch (Exception e) {
            fail(job, client, "ARRANGE_DEITIES_FAILED", e);
        }
    }

    /**
     * 本体起例：从 {@code deity:DeityRule}（deity_rule 表经 OBDA 暴露）按本命四柱匹配神煞。
     * 数据源自本体（SPARQL 取全库起例），匹配算理在程序（★铁律13：查表在库、算理在程序），
     * 覆盖 deity_rule 全量 186 条 / 78 类神煞（含天乙/羊刃/禄/驿马/桃花/华盖/将星/文昌/金舆/天德/月德/劫煞…）。
     *
     * @return 命中神煞列表；{@code null} 表示 OBDA/DB 不可用（调用方降级内置表）
     */
    /** 神煞名黑名单：其 branch_hit 为叙述性文字（方位/条件/季节/纳音等），程序无法可靠按「干/支」匹配，
     *  为避免误报与堆砌，一律不作自动排布（库内原文保留备查）。
     *  另「六厄」年支/日支口径歧义（年支寅午戌取酉、日支亥卯未取午），难定唯一基准，亦暂不排布。 */
    private static final java.util.Set<String> DEITY_SKIP = new java.util.HashSet<>(java.util.Arrays.asList(
            "六厄", "德秀", "暗金的煞", "破煞", "五鬼杀", "天地转杀", "三奇贵人", "天赦", "四废日",
            "三元", "垣城", "帝座", "天福贵人", "学堂", "词馆", "官贵学馆", "勾绞", "元辰",
            "红鸾", "天喜", "解神", "攀鞍", "年杀", "月杀", "流年星耀", "紫暗星", "冲天杀",
            "返本煞", "隔角", "伏吟", "返吟", "破军", "吞焰煞", "太白星", "斧劈星", "三丘五墓",
            "夹禄", "暗禄", "归禄", "交禄"));

    private List<String> deitiesFromOntology(int[][] p) {
        List<Map<String, String>> rules = queryClauses(
                "SELECT ?id ?bp ?bg ?dn ?da ?bh ?cat ?src WHERE { ?r a deity:DeityRule ; "
              + "deity:ruleId ?id ; deity:basePillar ?bp ; deity:baseGanzhi ?bg ; deity:ruleDeityName ?dn ; "
              + "deity:deityAlias ?da ; deity:branchHit ?bh ; deity:ruleCategory ?cat ; deity:ruleSource ?src . }");
        if (rules.isEmpty()) {
            return null;
        }
        String[] at = {"年", "月", "日", "时"};
        String[] zhiAt = {ZHI[p[0][1]], ZHI[p[1][1]], ZHI[p[2][1]], ZHI[p[3][1]]};
        String[] ganAt = {GAN[p[0][0]], GAN[p[1][0]], GAN[p[2][0]], GAN[p[3][0]]};

        java.util.LinkedHashMap<String, java.util.LinkedHashSet<Integer>> hitMap = new java.util.LinkedHashMap<>();
        for (Map<String, String> r : rules) {
            String bp = nz(r.get("bp")).trim();
            String bg = nz(r.get("bg")).trim();
            String bh = nz(r.get("bh")).trim();
            String dn = nz(r.get("dn")).trim();
            if (dn.isEmpty()) {
                continue;
            }
            // 旬空类（日柱×旬）空亡由 voidness 单独输出，跳过避免重复
            if ((bp.contains("日柱") && bg.endsWith("旬")) || bp.equals("旬")) {
                continue;
            }
            // 叙述性/组合/季节/纳音类一律跳过（无法可靠类型化匹配，避免误报堆砌）
            if (DEITY_SKIP.contains(dn) || dn.contains("淫") || dn.contains("妨害")
                    || bp.contains("季节") || bp.contains("命局")
                    || bp.contains("概念") || bp.contains("正官") || bp.contains("日干长生")
                    || bp.contains("时柱纳音") || bp.contains("纳音") || bp.contains("流年")
                    || bp.contains("日时") || bp.contains("年月日时") || bp.contains("日柱/年月")) {
                continue;
            }
            List<Integer> hit = matchDeityRule(bp, bg, bh, zhiAt, ganAt);
            if (!hit.isEmpty()) {
                hitMap.computeIfAbsent(dn, k -> new java.util.LinkedHashSet<>()).addAll(hit);
            }
        }
        // 列神煞名并标注其命中的「柱+该柱干支」，以便逐条自证（如「桃花（日丁卯）」「月德（时丙午）」）；
        //   同一神煞多柱命中时并列，避免旧式「年午、月午」纯地支重复而致误解。
        List<String> out = new java.util.ArrayList<>();
        for (Map.Entry<String, java.util.LinkedHashSet<Integer>> e : hitMap.entrySet()) {
            List<String> where = new java.util.ArrayList<>();
            for (int idx : e.getValue()) {
                String label = at[idx] + ganAt[idx] + zhiAt[idx];
                // 天乙贵人「以日干为主」取支（《三命通会》卷二·论天乙贵人）：标注日干依据，
                //   避免「年丙申」式被误读为按该柱天干取（丙之天乙在亥酉、甲之天乙在丑未，皆不在申）
                if ("天乙贵人".equals(e.getKey())) {
                    label = at[idx] + "支" + zhiAt[idx] + "（日干" + ganAt[2] + "见" + zhiAt[idx] + "）";
                } else if ("桃花".equals(e.getKey())) {
                    String note = taohuaDerivNote(p, idx);
                    if (!note.isEmpty()) {
                        label += "·" + note;
                    }
                } else if ("劫煞".equals(e.getKey())) {
                    // 劫煞亦「年支/日支」双基准：补注命中柱之劫煞支取自哪一基准，避免被误读为取错
                    String note = jieshaDerivNote(p, idx);
                    if (!note.isEmpty()) {
                        label += "·" + note;
                    }
                }
                where.add(label);
            }
            out.add(e.getKey() + "（" + String.join("、", where) + "）"); // where 恒非空（hitMap 仅存非空命中集），原 where.isEmpty() 恒假＝死支，简化
        }
        return out;
    }

    /**
     * 按神煞起例的「基准类型」把一条 deity_rule 匹配到本命具体柱：
     * 日柱类=日柱干支精确匹配（防止把"日柱列表"当通用交集而误报）；
     * 日干类=以日干查（支持斜杠多键按位配对，如福星/天厨）；年支(日支)/月支类=按支组归属后取所值之支/干。
     * 返回命中的柱索引集合（0年/1月/2日/3时）。
     */
    private List<Integer> matchDeityRule(String bp, String bg, String bh, String[] zhiAt, String[] ganAt) {
        List<Integer> out = new java.util.ArrayList<>();
        String dayGanC = ganAt[2];
        String yearZhi = zhiAt[0], monthZhi = zhiAt[1], dayZhi = zhiAt[2];
        String dayGZ = ganAt[2] + zhiAt[2];

        if (bp.contains("日柱")) {
            for (String tok : extractGanzhiTokens(bg)) {
                if (tok.equals(dayGZ)) {
                    out.add(2);
                    break;
                }
            }
            return out;
        }
        if (bp.contains("日干")) {
            String[] keys = splitSlash(bg);
            String[] hits = splitSlash(bh);
            if (keys.length > 1 && keys.length == hits.length) {
                for (int k = 0; k < keys.length; k++) {
                    if (keys[k].contains(dayGanC)) {
                        addBranchHits(out, hits[k], zhiAt);
                    }
                }
            } else if (bg.contains(dayGanC)) {
                addBranchHits(out, bh, zhiAt);
            }
            // 截路空亡等「（时）」限定类：仅于时支成立，不可与其余三柱任一支比对（否则误中他柱）
            if (bh.contains("（时）") || bh.contains("(时)")) {
                out.removeIf(idx -> idx != 3);
            }
            return out;
        }
        if (bp.contains("年支/日支") || bp.contains("日支")) {
            if (bg.contains(yearZhi) || bg.contains(dayZhi)) {
                addBranchHits(out, bh, zhiAt);
            }
            return out;
        }
        if (bp.contains("年支")) {
            if (bg.contains(yearZhi)) {
                addBranchHits(out, bh, zhiAt);
            }
            return out;
        }
        if (bp.contains("月支三合")) {
            String[] keys = splitSlash(bg);
            String[] hits = splitSlash(bh);
            if (keys.length > 1 && keys.length == hits.length) {
                for (int k = 0; k < keys.length; k++) {
                    if (keys[k].contains(monthZhi)) {
                        addMixedHits(out, hits[k], zhiAt, ganAt);
                    }
                }
            } else if (bg.contains(monthZhi)) {
                addMixedHits(out, bh, zhiAt, ganAt);
            }
            return out;
        }
        if (bp.contains("月支")) {
            if (bg.contains(monthZhi)) {
                addMixedHits(out, bh, zhiAt, ganAt);
            }
            return out;
        }
        return out;
    }

    private static String[] splitSlash(String s) {
        String[] parts = s.split("/");
        for (int i = 0; i < parts.length; i++) {
            parts[i] = parts[i].trim();
        }
        return parts;
    }

    /** 从字符串中提取「干+支」两字一组的六十甲子（用于日柱类起例的精确匹配）。 */
    private static List<String> extractGanzhiTokens(String s) {
        List<String> toks = new java.util.ArrayList<>();
        for (int i = 0; i + 1 < s.length(); i++) {
            char a = s.charAt(i), b = s.charAt(i + 1);
            if (indexOf(GAN, String.valueOf(a)) >= 0 && indexOf(ZHI, String.valueOf(b)) >= 0) {
                toks.add("" + a + b);
            }
        }
        return toks;
    }

    private static void addBranchHits(List<Integer> out, String hit, String[] zhiAt) {
        if (hit == null || hit.isEmpty()) {
            return;
        }
        for (int i = 0; i < 4; i++) {
            if (hit.indexOf(zhiAt[i]) >= 0 && !out.contains(i)) {
                out.add(i);
            }
        }
    }

    private static void addMixedHits(List<Integer> out, String hit, String[] zhiAt, String[] ganAt) {
        if (hit == null || hit.isEmpty()) {
            return;
        }
        for (int i = 0; i < 4; i++) {
            boolean h = hit.indexOf(zhiAt[i]) >= 0 || hit.indexOf(ganAt[i]) >= 0;
            if (h && !out.contains(i)) {
                out.add(i);
            }
        }
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    /**
     * 桃花（咸池）推导来源补注：桃花以「年支/日支」双基准取三合局所值之支。
     * 对命中柱 idx，回看年支、日支各自所属三合局的桃花星是否即该柱之支，命中则标注其基准与局名，
     * 使「桃花（年戊子·据日支卯·亥卯未局）」之类输出可自证——并非按年支子(申子辰→酉)取错。
     */
    private static String taohuaDerivNote(int[][] p, int hitIdx) {
        int target = p[hitIdx][1];
        java.util.List<String> notes = new java.util.ArrayList<>();
        int gy = sanheGroup(p[0][1]);
        if (TAOHUA[gy] == target) {
            String note = "据年支" + ZHI[p[0][1]] + "·" + sanheGroupName(gy) + "局";
            notes.add(note); // 年/日基注前缀不同永不重复；原 contains 去重恒真＝死支，简化
        }
        int gd = sanheGroup(p[2][1]);
        if (TAOHUA[gd] == target) {
            String note = "据日支" + ZHI[p[2][1]] + "·" + sanheGroupName(gd) + "局";
            notes.add(note); // 同上：与前缀互异永不重复，去重判据恒真＝死支，简化
        }
        return String.join("、", notes);
    }

    /** 劫煞双基准（年支/日支）据实补注：说明命中柱之支由哪个基准三合局推出（申子辰→巳、寅午戌→亥、巳酉丑→寅、亥卯未→申）。 */
    private static String jieshaDerivNote(int[][] p, int hitIdx) {
        int target = p[hitIdx][1];
        java.util.List<String> notes = new java.util.ArrayList<>();
        String[][] groups = {{"申子辰", "巳"}, {"寅午戌", "亥"}, {"巳酉丑", "寅"}, {"亥卯未", "申"}};
        int[] baseZhi = {p[0][1], p[2][1]};
        String[] baseName = {"年", "日"};
        for (int k = 0; k < 2; k++) {
            String bz = ZHI[baseZhi[k]];
            for (String[] g : groups) {
                if (g[0].contains(bz) && ZHI[target].equals(g[1])) {
                    String note = "据" + baseName[k] + "支" + bz + "·" + g[0] + "局";
                    notes.add(note); // 年/日前缀互异永不重复，去重判据恒真＝死支，简化
                }
            }
        }
        return String.join("、", notes);
    }

    private static String sanheGroupName(int g) {
        int[] s = SANHE[g];
        return "" + ZHI[s[0]] + ZHI[s[1]] + ZHI[s[2]];
    }

    // ==========================================================================
    // 节点 7：日主旺衰与十二宫（assessDayMasterStrength）
    // 实现方法：程序（多因子综合，**不设数值权重**，客户裁定 ⑰）+ 本体 yongshen:Vitality / core:LifeStage（R9）。
    // 出处：子平真诠·论十干得时不旺失时不弱；三命通会卷二·论天干阴阳生死。
    // ==========================================================================
    @JobWorker(type = "assessDayMasterStrength", autoComplete = false)
    public void handleDayMasterStrength(final ActivatedJob job, final JobClient client) {
        try {
            Map<String, Object> vars = job.getVariablesAsMap();
            int[][] p = parsePillars(str(vars.get("fourPillars")));
            int dayGan = p[2][0];
            int dayElem = GAN_ELEM[dayGan];
            int monthElem = ZHI_ELEM[p[1][1]];

            // 得令：月令生扶日主
            boolean deLing = (monthElem == dayElem) || generates(monthElem, dayElem);
            // 得地：通根（任一支藏干含同五行之干）
            int tongGen = 0;
            for (int[] pz : p) {
                for (String hs : ZHI_HIDDEN[pz[1]]) {
                    if (GAN_ELEM[indexOf(GAN, hs)] == dayElem) {
                        tongGen++;
                    }
                }
            }
            // 得势：天干比劫/印（同类或生我者）计数
            int deShi = 0;
            for (int i = 0; i < 4; i++) {
                if (i == 2) {
                    continue;
                }
                int e = GAN_ELEM[p[i][0]];
                if (e == dayElem || generates(e, dayElem)) {
                    deShi++;
                }
            }

            // 克泄耗（食伤/财/官杀）计数：失令而耗克泄偏多者，定为「中和偏弱」而非纯「中和」
            //   （《滴天髓·衰旺》「失令而党多克泄亦弱」；日主本身不计）
            int keXieHao = 0;
            for (int i = 0; i < 4; i++) {
                int e = GAN_ELEM[p[i][0]];
                if (i != 2 && (e == (dayElem + 1) % 5 || e == (dayElem + 2) % 5 || e == (dayElem + 3) % 5)) {
                    keXieHao++;
                }
                for (String hs : ZHI_HIDDEN[p[i][1]]) {
                    int he = GAN_ELEM[indexOf(GAN, hs)];
                    if (he == (dayElem + 1) % 5 || he == (dayElem + 2) % 5 || he == (dayElem + 3) % 5) {
                        keXieHao++;
                    }
                }
            }

            // 综合判定（定性，不设权重）
            String strength;
            if (deLing && (tongGen >= 2 || deShi >= 2)) {
                strength = "身强（得令）";
            } else if (deLing) {
                strength = "中和偏强（得令）";
            } else if (tongGen >= 2 && deShi >= 2) {
                strength = "中和偏强（失令但得地得势）";
            } else if (tongGen == 0 && deShi == 0) {
                strength = "身弱（失令、无根、寡助）";
            } else if (!deLing && keXieHao >= 2) {
                // 失令、根助有限而克泄耗偏多：中和偏弱（非纯中和）
                strength = "中和偏弱（失令、克泄耗偏多）";
            } else {
                strength = "中和";
            }

            // 寄生十二宫：日干对各支
            StringBuilder palaces = new StringBuilder();
            String[] names = {"年", "月", "日", "时"};
            for (int i = 0; i < 4; i++) {
                palaces.append(names[i]).append(ZHI[p[i][1]]).append("：").append(stageOf(dayGan, p[i][1])).append("；");
            }

            String balance = "得令=" + deLing + "，通根=" + tongGen + "，得势=" + deShi
                    + "；日主" + GAN[dayGan] + ELEM_NAME[dayElem] + "，" + strength + "；中和判据：得时不旺、失时不弱。";

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("strength", strength);
            result.put("twelvePalaces", palaces.toString());
            result.put("balance", balance);
            completeResult(client, job, result);
            log.info("✅ assessDayMasterStrength | {} | {}", strength, balance);
        } catch (Exception e) {
            fail(job, client, "DAY_MASTER_STRENGTH_FAILED", e);
        }
    }

    // ==========================================================================
    // 节点 8：格局判定（determinePattern）
    // 实现方法：程序（分类）+ SWRL R10（简化）+ 本体 pattern:；以月令提纲定格为主。
    // 出处：子平真诠·论正官/论财/论印绶/论食神/论偏官/论伤官/论阳刃/论建禄月劫/论用神成败救应。
    // ==========================================================================
    @JobWorker(type = "determinePattern", autoComplete = false)
    public void handleDeterminePattern(final ActivatedJob job, final JobClient client) {
        try {
            Map<String, Object> vars = job.getVariablesAsMap();
            int[][] p = parsePillars(str(vars.get("fourPillars")));
            int dayGan = p[2][0];
            int monthBranch = p[1][1];

            String benQi = ZHI_HIDDEN[monthBranch][0];
            int benQiGan = indexOf(GAN, benQi);
            int benQiElem = GAN_ELEM[benQiGan];
            int tg = tenGodIndex(dayGan, benQiGan);
            String tgName = TEN_GOD[tg];

            // 定格（《子平真诠》以月令提纲定格）：先辨月令是否为日主之禄（建禄）或帝旺（阳刃），
            //   再辨四库月（辰戌丑未·杂气），最后方以月令本气十神定格。旧法一律以「本气十神」定，
            //   致 戊日午月误判正印（实为阳刃）、戊日巳月误判偏印（实为建禄）、土日四库月误判建禄。
            boolean isJianLu = (LU[dayGan] == monthBranch);      // 月令为日主之禄 → 建禄格
            boolean isYangRen = (YANGREN[dayGan] == monthBranch); // 月令为日主帝旺（五阳干）→ 阳刃格
            boolean isTomb = (monthBranch == 4 || monthBranch == 10 || monthBranch == 1 || monthBranch == 7); // 辰戌丑未
            boolean isBiJieStem = "比肩".equals(tgName) || "劫财".equals(tgName);
            String pattern;
            boolean isBiJieMonth;
            if (isYangRen) {
                pattern = "阳刃格";
                isBiJieMonth = true;
            } else if (isJianLu) {
                pattern = "建禄格";
                isBiJieMonth = true;
            } else if (isTomb && isBiJieStem) {
                pattern = "杂气格（四库月本气比劫）";
                isBiJieMonth = true;
            } else if (isTomb) {
                pattern = "杂气" + tgName + "格";
                isBiJieMonth = false;
            } else if ("劫财".equals(tgName)) {
                pattern = "月劫格";
                isBiJieMonth = true;
            } else {
                pattern = tgName + "格";
                isBiJieMonth = false;
            }
            // 外格/杂格（从/化/专旺）判定：须全局成象（专旺见 detectZhuanWang；从/化见后）；
            //   出处：渊海子平·论外格；子平真诠·论外格用舍/论杂格。
            String extern = detectExternPattern(p, dayGan);
            boolean zhuanWang = extern.startsWith("专旺");
            boolean congHua = !extern.isEmpty() && !zhuanWang;

            // 财官食伤（相对日主之五行）：官杀=克我、财=我克、食伤=我生
            int dayElem = GAN_ELEM[dayGan];
            int guanElem = (dayElem + 3) % 5;
            int caiElem = (dayElem + 2) % 5;
            int shiElem = (dayElem + 1) % 5;
            boolean guanTou = elemTouGan(p, guanElem), caiTou = elemTouGan(p, caiElem), shiTou = elemTouGan(p, shiElem);
            boolean guanGen = hasRoot(p, guanElem), caiGen = hasRoot(p, caiElem), shiGen = hasRoot(p, shiElem);
            boolean cgsSeen = guanTou || caiTou || shiTou || guanGen || caiGen || shiGen;
            // 会杀为凶：干见七杀、支会杀局（《喜忌篇》月令虽逢建禄，切忌会杀为凶）
            boolean huiSha = guanTou && (sanheFull(p, guanElem) || sanhuiFull(p, guanElem));

            // 月支逢冲/合（格神动摇之救应）
            boolean monthChong = false;
            boolean monthHe = false;
            for (int i = 0; i < 4; i++) {
                if (i == 1) {
                    continue;
                }
                if (pairIn(LIUCHONG, monthBranch, p[i][1])) {
                    monthChong = true;
                }
                if (pairIn(LIUHE, monthBranch, p[i][1])) {
                    monthHe = true;
                }
            }

            // 月支旬空：格神落空则格更待成，须岁运填实/冲实（《三命通会·论空亡》空亡须填实冲实）
            int dIdxx = sexagenaryIndex(p[2][0], p[2][1]);
            int kkong1 = (dIdxx - dIdxx % 10 + 10) % 12, kkong2 = (dIdxx - dIdxx % 10 + 11) % 12;
            boolean monthKong = (monthBranch == kkong1 || monthBranch == kkong2);

            // 定格推导（月令提纲法）：写明「为何定此格」、「禄与刃分称」、「定格 vs 用神之别」
            boolean geTouGan = false;
            for (int i = 0; i < 4; i++) {
                if (GAN_ELEM[p[i][0]] == benQiElem) {
                    geTouGan = true;
                    break;
                }
            }
            // 本气天干（如偏财「甲」）是否亲透；否则记录同五行异十神之透干（如「乙」正财透）以正名分
            boolean benQiExactTou = false;
            String tongQiGanName = null;
            int tongQiGod = -1;
            for (int i = 0; i < 4; i++) {
                if (GAN[p[i][0]].equals(benQi)) {
                    benQiExactTou = true;
                } else if (GAN_ELEM[p[i][0]] == benQiElem && tongQiGanName == null) {
                    tongQiGanName = GAN[p[i][0]];
                    tongQiGod = tenGodIndex(dayGan, p[i][0]);
                }
            }
            StringBuilder derive = new StringBuilder();
            derive.append("定格（月令提纲法·用神专求月令）：日主").append(GAN[dayGan])
                    .append("生于").append(ZHI[monthBranch]).append("月，月令本气为").append(benQi)
                    .append("，对日主为「").append(tgName).append("」，");

            String rescue;
            String grade;
            if (zhuanWang) {
                // 专旺优先：全局成象则顺其气势为用（《滴天髓》独象喜行化地；渊海子平·论外格）
                derive.append("然全局").append(ELEM_NAME[dayElem]).append("势成象（地支会").append(ELEM_NAME[dayElem])
                        .append("方局、四柱不见克制之神），已成专旺之象，故以「").append(extern)
                        .append("」为主格，顺其旺势为用");
                if (isBiJieMonth) {
                    derive.append("；月令为日主之").append(isYangRen ? "帝旺（阳刃）" : "禄（临官）")
                            .append("，建禄仅作成象条件之一，不作正格另论");
                }
                derive.append("。");
                rescue = "结论：专旺成象，气势偏旺一方，宜顺不宜逆（喜行化地、忌冲激破格）。";
                grade = "格成（专旺成象·气势纯）";
            } else if (congHua) {
                // 从格/化气：日主无根无助，弃命顺从（从财/从杀/从儿）或合而化气，顺所从/化神为用
                //   出处：子平真诠·论外格用舍（从格须一气独旺）；滴天髓·从象（从得真者只论从）；三命通会·论十干化气
                derive.append("然日主无根无助、全局气势已「").append(extern)
                        .append("」成象，月令正格不足取，当以「").append(extern).append("」为主格，顺其所从之神为用。");
                if ("从财格".equals(extern)) {
                    rescue = "结论：从财格成，弃命从财——喜食伤生财、财星旺相，忌比劫夺财、印绶生扶日主（使身有根则不从）。";
                } else if ("从杀格".equals(extern)) {
                    rescue = "结论：从杀格成，弃命从杀——喜财星生杀、官杀旺相，忌印绶化杀生身、比劫帮身、食伤制杀（皆破从）。";
                } else if ("从儿格".equals(extern)) {
                    rescue = "结论：从儿格成，弃命从儿——喜食伤泄秀、财星顺生（儿又生儿），忌印绶克食伤生身、官杀克身（破格）。";
                } else {
                    rescue = "结论：" + extern + "成，合而化" + extern.replaceAll(".*化([木火土金水]).*", "$1")
                            + "——喜生助化神，忌克化神与日主本气之根（破化）。";
                }
                grade = "格成（" + extern + "·弃命顺从）";
            } else if (isBiJieMonth) {
                // 禄刃分称，不得混同：阳干方有刃，丁禄在午而丁无刃
                String luren = isYangRen ? "帝旺（阳刃）" : (isJianLu ? "禄（临官）"
                        : ("劫财".equals(tgName) ? "劫财" : "比劫"));
                // 格神称谓：建禄/月劫/比劫（不得一律称「建禄」，乙日寅月为月劫而非建禄）
                String luLabel = isJianLu ? "建禄" : ("劫财".equals(tgName) ? "月劫" : "比劫");
                derive.append("月令为日主之").append(luren).append("，故取「").append(pattern)
                        .append("」；定格名不以是否透干改易，而用神须察财官食伤之透干有根（详下成格判定）。");
                // 成格判定：依《三命通会·论建禄》《喜忌篇》——建禄喜财官透天、一见财官自然成福，
                //   切忌会杀为凶；四柱元无财官则纵行财官运亦虚花。阳刃则喜官杀制刃（《神峰通考·阳刃格》刃无杀不威）。
                if (isYangRen) {
                    if (huiSha) {
                        rescue = "结论：阳刃而会杀，煞刃相制有权，须察轻重——杀刃停均方为贵。";
                        grade = "格成（阳刃有煞制·煞刃相制）";
                    } else if (guanTou && guanGen) {
                        rescue = "结论：阳刃得官杀透干有根以制刃，刃不孤旺，可成贵局。";
                        grade = "格成（阳刃得官杀制）";
                    } else {
                        rescue = "结论：阳刃而无官杀制伏，刃旺无依，未成贵格——须岁运遇官杀制刃方发。";
                        grade = "格不成（阳刃无官杀制·刃旺无依）";
                    }
                } else if (huiSha) {
                    rescue = "结论：" + pattern + "而干见七杀、支会杀局，是「会杀为凶」，格有破——宜食伤制杀或印绶化杀。";
                    grade = "格有破（会杀为凶）";
                } else if (caiTou && caiGen && guanTou && guanGen) {
                    rescue = "结论：财官俱透干且有根，" + luLabel + "得财官为用，财官俱旺乃富贵之命，格成。";
                    grade = "格成（财官透干有根·财官俱旺）";
                } else if (guanTou && guanGen) {
                    rescue = "结论：官星（" + ELEM_NAME[guanElem] + "）透干有根，" + luLabel + "得官为用，官星有理会，贵气有根，格成（官为用神）。";
                    grade = "格成（官星透干有根）";
                } else if (caiTou && caiGen) {
                    rescue = "结论：财星（" + ELEM_NAME[caiElem] + "）透干有根，" + luLabel + "得财为用，财旺身旺可任，富局可期，格成（财为用神）。";
                    grade = "格成（财星透干有根）";
                } else if (shiTou && shiGen) {
                    if (caiTou || caiGen || guanTou || guanGen) {
                        rescue = "结论：本命" + pattern + "、日主身旺，食伤（" + ELEM_NAME[shiElem]
                                + "）透干有根，财官虽略见而未成气势，食伤可泄秀生财，须岁运引财官方显，格将成。";
                        grade = "格将成（食伤泄秀·财官未成·待岁运引旺）";
                    } else {
                        rescue = "结论：本命" + pattern + "、日主身旺，格成立；唯四柱食伤（" + ELEM_NAME[shiElem]
                                + "）透干有根而财、官俱不显。" + luLabel + "以财官为富贵之根，食伤止能泄秀、非富贵之本——"
                                + "故成格之质量有偏、富贵条件不足，层次中平而偏劳碌，宜岁运补财官（尤喜财金）方有显达之机。";
                        grade = pattern + "成立（食伤泄秀而财官不显·层次中平·待岁运补财官）";
                    }
                } else if (cgsSeen) {
                    rescue = "结论：财官食伤虽见而未透干、根气浅薄，须岁运引旺方发，格将成而待扶。";
                    grade = "格将成（财官食伤见而根浅·待岁运引旺）";
                } else {
                    rescue = "结论：" + pattern + "、日主身旺，格成立；然四柱元无财官食伤可取，身旺无依，纵行财官之运亦止虚花，富贵难期——格虽立而用乏。";
                    grade = pattern + "成立（无财官食伤可用·富贵无凭）";
                }
                if (monthChong && parsePatternGrade(grade).geCheng) {
                    rescue = "结论：财官虽具，然月令逢冲，格神（" + luLabel + "）动摇，须合解或制化护格，格有破。";
                    grade = "格有破（月令逢冲·格神动摇）";
                } else if (monthHe && parsePatternGrade(grade).geCheng) {
                    rescue = rescue + "（另：月令逢合，宜权合化向背。）";
                }
            } else {
                derive.append("依「以月令本气所化十神定格」之义，取「").append(pattern)
                        .append("」，格神五行为").append(ELEM_NAME[benQiElem]).append("；")
                        .append(!geTouGan
                                ? "格神本气" + benQi + "（" + tgName + "）未透干，取格以月令本气为准，宜再查支中会局或藏干是否得用。"
                                : benQiExactTou
                                        ? "格神本气" + benQi + "（" + tgName + "）已透干，格局分明，取格明确。"
                                        : "格神本气" + benQi + "（" + tgName + "）虽未亲透于天干，然同气之" + tongQiGanName
                                                + "（" + TEN_GOD[tongQiGod] + "）透出、月令本气" + benQi + "藏根，财官食伤之气通干，格局可成。");
                // 成败救应（《子平真诠·论用神成败救应》）：相神/忌神对格神之破坏优先于「格神是否透干」。
                //   伤官见官、杀旺无制、比劫夺财、贪财坏印、枭神夺食等，皆为忌神肆虐而破格，须相应救应方解。
                String[] chengBai = detectChengBai(tgName, dayGan, p, parseStrength(str(vars.get("strength"))).strong);
                if (chengBai != null) {
                    rescue = chengBai[1];
                    grade = chengBai[0];
                    if (monthChong) {
                        rescue = rescue + "（且月令逢冲，格根动摇，破之尤甚。）";
                    }
                } else if (monthChong) {
                    rescue = "结论：月令逢冲，格神动摇，需救应——宜以合解或制化护格。";
                    grade = "格有破（月令逢冲·待救应）";
                } else if (monthHe) {
                    rescue = "结论：月令逢合，格神被绊，宜权衡合化之向背（合去为忌、合来为喜）。";
                    grade = "格有绊";
                } else if (geTouGan) {
                    rescue = benQiExactTou
                            ? "结论：格神透干、月令清纯，格成（仍宜察相神得力）。"
                            : "结论：格神本气虽未亲透，然同类透干有根、月令清纯，格成（仍宜察相神得力）。";
                    grade = "格成";
                } else {
                    if (monthKong) {
                        derive.append("月支").append(ZHI[monthBranch]).append("逢旬空、格神落空，格更待成（须岁运填实/冲实）。");
                        rescue = "结论：格神未透、且月支" + ZHI[monthBranch] + "逢旬空，格神落空、格更待成——须岁运填实/冲实或会局引透方显。";
                        grade = "格将成（格神未透·月令空亡落空·待填实引透）";
                    } else {
                        rescue = "结论：格神未透、仅以月令本气取格，须岁运引透或会局方显，格待成。";
                        grade = "格将成（格神未透·待引透）";
                    }
                }
            }

            // 具名格局：仅当本命确已命中构成条件时列出格名（不列原文、不标出处）
            List<String[]> hits = hitNamedPatterns(p, dayGan);
            List<String> named = new ArrayList<>();
            for (String[] hit : hits) {
                named.add(hit[0]);
            }
            String namedText = named.isEmpty()
                    ? "【具名格局】本命未命中具名格之构成条件。"
                    : "【具名格局·本命命中】" + String.join("、", named) + "。";

            // 原三元条件 (extern.isEmpty() || zhuanWang || congHua) 恒真：extern 非空时必为「专旺」或「从/化」(congHua=!空&&!专旺)，
            //   故「外格说明」分支不可达、externText 恒为空串；且专旺/从化已在上方 derive 判词中说明，此处删冗余恒真三元（行为不变）。
            String externText = "";
            // 炎上/专旺候选辨正：火日而火气偏旺，若未成寅午戌/巳午未全火局或见土泄、见水破，则只作「火旺」气势、不成专旺
            String yanShangNote = "";
            if (GAN_ELEM[dayGan] == 1 && !zhuanWang) {
                boolean fullFire = sanheFull(p, 1) || sanhuiFull(p, 1);
                if (!fullFire) {
                    StringBuilder n = new StringBuilder();
                    n.append("\n【外格辨】日主").append(GAN[dayGan]).append("火，虽火气偏旺，然地支未成寅午戌/巳午未全火局");
                    if (elemTouGan(p, 2)) {
                        n.append("、且天干透土泄火");
                    }
                    // containsElem(p,4) 已含天干壬癸与地支（子/亥本气、藏干癸壬）之水；containsZhi(子/亥) 恒被左侧短路＝冗余死操作数，去除。
                    if (containsElem(p, 4)) {
                        n.append("、又见水破");
                    }
                    n.append("，炎上（专旺）格不成立，仅以「火旺」论其气势，不作专旺结论。");
                    yanShangNote = n.toString();
                }
            }

            // 典籍定向引据（第29步·回归修复）：具名格名录（pattern_catalog）+ 滴天髓象论（dts_clause）+ 子平真诠
            List<String> patternCites = new ArrayList<>();
            patternCites.addAll(citePatternCatalog(hits));
            patternCites.addAll(citeDts(pattern + extern, orDefault(str(vars.get("ganzhiRelations")), "")));
            patternCites.addAll(citeZiping("论用神成败救应"));

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("patternResult", derive.toString() + externText + yanShangNote + "\n" + rescue + "\n" + namedText
                    + mergeCitations("格局典籍引据", patternCites));
            result.put("rescueAdjust", rescue);
            result.put("patternGrade", grade);
            completeResult(client, job, result);
            log.info("✅ determinePattern | {} | {} | {}", pattern, grade, rescue);
        } catch (Exception e) {
            fail(job, client, "DETERMINE_PATTERN_FAILED", e);
        }
    }

    // ==========================================================================
    // 节点 9：取用与多法冲突呈现（selectYongShen）
    // 实现方法：程序（编排「主取」，月令格局法优先）+ SWRL R11 + 本体 yongshen:/diagnosis:SchoolVariant。
    // 出处：子平真诠·论用神「用神专求月令」/论相神紧要/论喜忌干支有别；客户裁定 ⑫。
    // ==========================================================================
    @JobWorker(type = "selectYongShen", autoComplete = false)
    public void handleSelectYongShen(final ActivatedJob job, final JobClient client) {
        try {
            Map<String, Object> vars = job.getVariablesAsMap();
            int[][] p = parsePillars(str(vars.get("fourPillars")));
            int dayGan = p[2][0];
            int dayElem = GAN_ELEM[dayGan];
            boolean strong = parseStrength(str(vars.get("strength"))).strong;
            String sLabel = strengthLabel(str(vars.get("strength")));

            int monthBranch = p[1][1];
            // 月令格局法用神（《子平真诠·论用神》「用神专求月令」）与调候法并陈；
            //   极端燥烈/寒湿之局，以调候救偏取实效用神（穷通宝鉴·调候为急）。
            int monthYong = yongShenElem(p);
            int yongElem = effectiveYongElem(p, dayGan, strong);
            int jiElem = jiFromYong(p, yongElem);
            int xiangElem = isNiYongOf(p, yongElem) ? (yongElem + 3) % 5 : (yongElem + 4) % 5;
            String yongRole = elemRoleName(dayElem, yongElem);
            String monthGod = monthGodName(p);

            // 调候：按日干 + 月支匹配条文库
            Map<String, String> th = clauseTiaohou(GAN[dayGan], ZHI[monthBranch]);
            String tiaohou;
            if (th != null) {
                String sec = th.get("x");
                boolean hasSec = sec != null && !sec.isBlank() && !"【待考】".equals(sec) && !"—".equals(sec);
                tiaohou = "【调候用神】" + ZHI[monthBranch] + "月" + GAN[dayGan] + "：主用 " + th.get("p")
                        + (hasSec ? "，次用 " + sec : "") + "。";
            } else {
                tiaohou = "【调候用神】" + ZHI[monthBranch] + "月" + GAN[dayGan] + "：条文库未命中（该月无条目或库未就绪）。";
            }

            boolean yinBiZhong = isYinBiZhongYong(p, dayGan, strong);
            // 扶抑覆盖（《滴天髓·衰旺》《子平真诠·论用神》旺衰并参）：身非强而月令格神为财/官杀、
            //   身不能任财官者，改以印（化杀生身）为先、比劫（帮身）为次，财官退为格神、待岁运身强方任。
            boolean shenRuoFuYi = !strong && !yinBiZhong
                    && (monthYong == (dayElem + 1) % 5 || monthYong == (dayElem + 2) % 5
                        || monthYong == (dayElem + 3) % 5)
                    && yongElem != monthYong;
            boolean tiaohouPriority = !shenRuoFuYi && yongElem != monthYong;
            // 专旺格：顺其气势为用（食伤泄秀为先、比劫帮旺为次），忌官杀克神（《滴天髓·独象喜行化地》《渊海子平·论外格》）
            String extSel = detectExternPattern(p, dayGan);
            boolean zhuangWang = extSel.startsWith("专旺");
            boolean congHuaSel = !extSel.isEmpty() && !zhuangWang;
            String yongShen;
            String xiangShen = null;
            String jiShen = null;
            if (zhuangWang) {
                int zwShi = (dayElem + 1) % 5;
                int zwGuan = (dayElem + 3) % 5;
                yongElem = zwShi;
                yongRole = elemRoleName(dayElem, zwShi);
                yongShen = "用神（专旺·顺其气势）：" + ELEM_NAME[zwShi] + "（" + yongRole + "，食伤泄秀）为先、"
                        + ELEM_NAME[dayElem] + "（比劫，帮旺）为次。"
                        + "日主" + GAN[dayGan] + ELEM_NAME[dayElem] + "全局已成专旺之势（气势纯旺），"
                        + "依「独象喜行化地」顺其旺势，以食伤泄秀为用、比劫帮旺为次，忌官杀克制、忌冲激破格。"
                        + "（旺衰法：" + sLabel + "，专旺不论扶抑，作异法不取。）";
                xiangShen = "相神：" + ELEM_NAME[dayElem] + "（比劫，生助用神" + ELEM_NAME[zwShi] + "）";
                jiShen = "忌神：" + ELEM_NAME[zwGuan] + "（" + elemRoleName(dayElem, zwGuan) + "，克损专旺之气·破格）";
            } else if (congHuaSel) {
                // 从格/化气：弃命顺从/合而化气，用神取所从之神或化神（《子平真诠·论外格用舍》；《滴天髓·从象》；三命通会·论十干化气）
                int de = dayElem;
                int yongE, xiangE, jiE;
                String geName = extSel;
                String yongDesc, xiangDesc, jiDesc, extra = "";
                if ("从财格".equals(geName)) {
                    yongE = (de + 2) % 5; xiangE = (de + 1) % 5; jiE = de;
                    yongDesc = "顺其从财之势、以财为用"; xiangDesc = "食伤生财"; jiDesc = "比劫夺财";
                    extra = "（兼忌印绶生扶日主，使身有根则不从。）";
                } else if ("从杀格".equals(geName)) {
                    yongE = (de + 3) % 5; xiangE = (de + 2) % 5; jiE = (de + 4) % 5;
                    yongDesc = "顺其从杀之势、以官杀为用"; xiangDesc = "财星生杀"; jiDesc = "印绶化杀生身（并忌比劫帮身、食伤制杀）";
                } else if ("从儿格".equals(geName)) {
                    yongE = (de + 1) % 5; xiangE = (de + 2) % 5; jiE = (de + 4) % 5;
                    yongDesc = "顺其从儿之势、以食伤为用"; xiangDesc = "财星顺生（儿又生儿）"; jiDesc = "印绶克食伤生身（并忌官杀克身）";
                } else {
                    int huaE = -1;
                    for (int j : new int[]{1, 3}) {
                        int h = ganWuHe(dayGan, p[j][0]);
                        if (h >= 0 && indexOf(ELEM_NAME, GAN_WUHE_HUA[h]) == ZHI_ELEM[monthBranch]) {
                            huaE = indexOf(ELEM_NAME, GAN_WUHE_HUA[h]);
                        }
                    }
                    yongE = huaE; xiangE = (huaE + 4) % 5; jiE = (huaE + 3) % 5;
                    yongDesc = "顺其化气之势、以化神为用"; xiangDesc = "生助化神"; jiDesc = "克化神及日主本气之根（破化）";
                }
                yongElem = yongE;
                yongRole = elemRoleName(de, yongE);
                yongShen = "用神（" + geName + "·顺所从）：" + ELEM_NAME[yongE] + "（" + yongRole + "）。"
                        + "日主" + GAN[dayGan] + ELEM_NAME[de] + "无根无助，全局已成「" + geName + "」之象，"
                        + "依「从得真者只论从」，" + yongDesc + "；忌见" + jiDesc + "。"
                        + "（专旺从化不论扶抑，旺衰" + sLabel + "仅作参照。）";
                xiangShen = "相神：" + ELEM_NAME[xiangE] + "（" + elemRoleName(de, xiangE) + "，" + xiangDesc + "）";
                jiShen = "忌神：" + ELEM_NAME[jiE] + "（" + elemRoleName(de, jiE) + "，" + jiDesc + "）" + extra;
            } else if (yinBiZhong) {
                int ciElem = (dayElem + 2) % 5;
                yongShen = "用神（主取·身强泄秀）：" + ELEM_NAME[yongElem] + "（" + yongRole + "）。"
                        + "日主" + GAN[dayGan] + ELEM_NAME[dayElem] + "生于" + ZHI[monthBranch] + "月（"
                        + ELEM_NAME[ZHI_ELEM[monthBranch]] + "令，月令格神「" + monthGod + "」）。"
                        + "本命身强而" + ELEM_NAME[monthYong] + "（" + elemRoleName(dayElem, monthYong) + "）偏重、助旺不宜再扶，"
                        + "依「身强宜克泄耗」，不以" + ELEM_NAME[monthYong] + "为用，"
                        + "而取" + ELEM_NAME[yongElem] + "（" + yongRole + "）泄秀为先"
                        + (yongElem == (dayElem + 1) % 5
                            ? "、" + ELEM_NAME[ciElem] + "（" + elemRoleName(dayElem, ciElem) + "）制印为次" : "")
                        + "，与调候主用一致"
                        + (th != null ? "（调候主用 " + th.get("p")
                            + (th.get("x") != null && !th.get("x").isBlank() && !"—".equals(th.get("x")) ? "、次用 " + th.get("x") : "") + "）" : "")
                        + "。（旺衰法：" + sLabel + "，宜克泄耗。）";
            } else if (shenRuoFuYi) {
                boolean shiShangGe = monthYong == (dayElem + 1) % 5;
                String taitou = shiShangGe ? "用神（扶抑为主·身弱食伤旺宜先扶身）" : "用神（扶抑为主·身不能任财官）";
                String haoShang = shiShangGe ? "食伤泄身太过、身弱不宜再泄" : "财官耗克而身不能任";
                String tuiGe = shiShangGe ? "食伤退为格神" : "财官退为格神";
                boolean geIsGuanSha = "官杀".equals(elemRoleName(dayElem, monthYong));
                String shengShenDesc = (geIsGuanSha && "印".equals(yongRole)) ? "化杀生身" : "生身";
                yongShen = taitou + "：" + ELEM_NAME[yongElem] + "（" + yongRole + "）为先、"
                        + ELEM_NAME[dayElem] + "（比劫）为次。"
                        + "日主" + GAN[dayGan] + ELEM_NAME[dayElem] + "生于" + ZHI[monthBranch] + "月（"
                        + ELEM_NAME[ZHI_ELEM[monthBranch]] + "令，月令格神「" + monthGod + "」），"
                        + "格神之" + ELEM_NAME[monthYong] + "（" + elemRoleName(dayElem, monthYong) + "）虽得月令，"
                        + "然本命身非强、" + haoShang + "，不宜直取为用；"
                        + "依「扶抑」取" + ELEM_NAME[yongElem] + "（" + yongRole + "）" + shengShenDesc + "为先、"
                        + ELEM_NAME[dayElem] + "（比劫）帮身为次，" + tuiGe + "，待岁运引透、身转强方任。"
                        + (th != null && th.get("p") != null && !th.get("p").isBlank()
                            ? "调候" + th.get("p") + "需" + ELEM_NAME[dayElem] + "（比劫）生扶暖局，不作主用。" : "")
                        + "（旺衰法：" + sLabel + "，宜生扶帮，即为主取。）";
            } else if (tiaohouPriority) {
                yongShen = "用神（调候救偏为主）：" + ELEM_NAME[yongElem] + "（" + yongRole + "）。"
                        + "日主" + GAN[dayGan] + ELEM_NAME[dayElem] + "生于" + ZHI[monthBranch] + "月（"
                        + ELEM_NAME[ZHI_ELEM[monthBranch]] + "令，月令格神「" + monthGod + "」）。"
                        + "月令格局法本可取" + ELEM_NAME[monthYong] + "（" + elemRoleName(dayElem, monthYong) + "）为用，"
                        + "然本命" + (dayElem == 1 ? "火炎土燥、四柱无水，燥土（戊己未戌）不能润局、反增其燥" : "寒湿太过、四柱无火，调候须先暖局")
                        + "，故不以" + ELEM_NAME[monthYong] + "为主，而当以调候之" + ELEM_NAME[yongElem]
                        + (dayElem == 1 ? "（壬癸水）为先，润局救偏为主用，财金（庚辛申酉）生水为次；食伤土只作泄秀，且喜湿土（辰丑）而不喜燥土（未戌）。"
                                        : "为先，暖局救偏为主用。")
                        + "（旺衰法：" + sLabel + (strong ? "，宜克泄耗" : "，宜生扶帮") + "，作异法并陈。）";
            } else {
                yongShen = "用神（主取·月令格局法）：" + ELEM_NAME[yongElem] + "（" + yongRole
                        + "）——日主" + GAN[dayGan] + ELEM_NAME[dayElem] + "生于" + ZHI[monthBranch] + "月（"
                        + ELEM_NAME[ZHI_ELEM[monthBranch]] + "令，格神「" + monthGod + "」），"
                        + "依「用神专求月令」，取月令之" + ELEM_NAME[yongElem] + "为用；对应十神为" + yongRole + "。"
                        + "（旺衰法：" + sLabel + (strong ? "，宜克泄耗" : "，宜生扶帮") + "，作异法并陈。）";
            }
            if (!zhuangWang && !congHuaSel) {
                xiangShen = "相神：" + ELEM_NAME[xiangElem] + "（" + elemRoleName(dayElem, xiangElem)
                        + "，" + (generates(xiangElem, yongElem) ? "生助" : "制服") + "用神" + ELEM_NAME[yongElem] + "）";
                jiShen = "忌神：" + ELEM_NAME[jiElem] + "（" + elemRoleName(dayElem, jiElem)
                        + "，" + (isNiYongOf(p, yongElem) ? "党助" : "克损") + "用神" + ELEM_NAME[yongElem] + "）";
            }

            String methods = (congHuaSel ? "【主取】从格/化气法（弃命顺从/合而化气，顺所从之神为用，不论扶抑）；【并陈】旺衰法作参照。"
                                              : zhuangWang ? "【主取】专旺法（顺其旺势、食伤泄秀为用，不论扶抑）；【并陈】旺衰法作参照。"
                                              : shenRuoFuYi ? "【主取】扶抑为主（身弱耗泄、不任财官或食伤，取印比生身帮身）；【并陈】月令格局法（格神为财/官/食伤）作格名与岁运用；"
                                              : tiaohouPriority ? "【主取】调候救偏法（润燥/暖局为先）；【并陈】月令格局法与旺衰法作参照；"
                                              : "【主取】格局法（月令格局法）；【并陈】旺衰法结论作异法并陈；")
                    + (th != null ? "【调候】已接入调候条文库（按日干+月支匹配）。"
                                  : "【调候】调候条文库未命中。");

            // 典籍定向引据（第29步·回归修复）：十干/十二支体象（technique_catalog），按本命日干、日支定位
            List<String> yongCites = new ArrayList<>(citeTechniqueBody(GAN[dayGan], ZHI[p[2][1]]));

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("yongShen", yongShen);
            result.put("xiangShen", xiangShen);
            result.put("jiShen", jiShen);
            result.put("tiaohou", tiaohou);
            result.put("yongShenMethods", methods + mergeCitations("用神典籍引据", yongCites));
            completeResult(client, job, result);
            log.info("✅ selectYongShen | {} | {} | {}", yongShen, xiangShen, jiShen);
        } catch (Exception e) {
            fail(job, client, "SELECT_YONG_SHEN_FAILED", e);
        }
    }

    // ==========================================================================
    // 节点 10：命局层次总断（judgeChartLevel）
    // 实现方法：程序（综合评级，客户裁定 ⑬）+ 本体 yongshen:Level / Purity（R12）。
    // 出处：子平真诠·论用神格局高低（有情/有力无力定高低）；滴天髓·何知章（富/贵/贫/贱四断：
    //   财气通门户、官星有理会、财神反不真、官星还不见）、清气/浊气/真神/假神/隐显/众寡。
    // ==========================================================================
    @JobWorker(type = "judgeChartLevel", autoComplete = false)
    public void handleJudgeChartLevel(final ActivatedJob job, final JobClient client) {
        try {
            Map<String, Object> vars = job.getVariablesAsMap();
            String patternGrade = orDefault(str(vars.get("patternGrade")), "");
            String strength = orDefault(str(vars.get("strength")), "");

            PatternInfo pi = parsePatternGrade(patternGrade);
            boolean geCheng = pi.geCheng;
            boolean geBreak = pi.geBreak;
            StrengthInfo si = parseStrength(strength);
            boolean bodyStrong = si.strong;
            // 「中和偏弱」属轻度偏弱，尚能任财官（不作财旺身弱之贫格断）；仅「身弱（失令、无根、寡助）」作重度身弱
            boolean bodyWeak = si.severeWeak;

            // 清浊/隐显/众寡：以本命用神之透干、藏支、同类多寡按滴天髓口径实测（不用通用模板）
            int[][] p = parsePillars(str(vars.get("fourPillars")));
            int dayGan = p[2][0];
            int dayElem = GAN_ELEM[dayGan];
            // 用神/忌神：与节点9同口径（极端燥湿之局取调候救偏之实效用神）
            int yongElem = effectiveYongElem(p, dayGan, bodyStrong);
            int jiElem = jiFromYong(p, yongElem);
            boolean yongTouGan = false;
            boolean yongCangZhi = false;
            int yongCount = 0;
            int jiCount = 0;
            for (int i = 0; i < 4; i++) {
                if (GAN_ELEM[p[i][0]] == yongElem) {
                    yongTouGan = true;
                    yongCount++;
                }
                if (GAN_ELEM[p[i][0]] == jiElem) {
                    jiCount++;
                }
                if (ZHI_ELEM[p[i][1]] == yongElem) {
                    yongCangZhi = true;
                }
                for (String hs : ZHI_HIDDEN[p[i][1]]) {
                    int he = GAN_ELEM[indexOf(GAN, hs)];
                    if (he == yongElem) {
                        yongCangZhi = true;
                        yongCount++;
                    }
                    if (he == jiElem) {
                        jiCount++;
                    }
                }
            }
            String xianYin = yongTouGan ? "透干（显）" : (yongCangZhi ? "藏支（隐）" : "四柱不见、用神乏力");
            // 清浊：以「格是否正、身是否健、忌神是否扰」定性（《滴天髓》清浊之说），不作精确计数结论
            String qingZhuo;
            if (geCheng && !bodyWeak && jiCount <= yongCount && yongCount > 0) {
                qingZhuo = "清（格正身健、用神有根、忌神不扰）";
            } else if (geBreak) {
                qingZhuo = "清中带浊（格有偏、用神乏力）";
            } else if (!yongTouGan && !yongCangZhi) {
                qingZhuo = "清中带浊（格有偏、用神不见、忌神偏旺）";
            } else {
                qingZhuo = "清浊相参（格有偏、用神有根而忌神亦不弱）";
            }
            int dIdx = sexagenaryIndex(p[2][0], p[2][1]);
            int kong1 = (dIdx - dIdx % 10 + 10) % 12, kong2 = (dIdx - dIdx % 10 + 11) % 12;
            boolean monthKong = (p[1][1] == kong1 || p[1][1] == kong2);
            boolean zhenShen = (yongTouGan || yongCangZhi) && !(jiCount > yongCount) && !monthKong;
            int zhiJiElem = (jiElem + 3) % 5;
            String purity = "清浊真假（本命实测·滴天髓）：格=" + patternGrade + "，身=" + strength
                    + " → 清浊：" + qingZhuo + "；用神" + ELEM_NAME[yongElem] + " " + xianYin + "；"
                    + (zhenShen
                        ? "真神得用=true（用神有根、忌神不夺）。"
                        : (!yongTouGan && !yongCangZhi)
                            ? "真神得用=false——用神" + ELEM_NAME[yongElem] + "四柱不见、用神乏力；"
                                + ELEM_NAME[jiElem] + "（" + elemRoleName(dayElem, jiElem) + "）凡" + jiCount
                                + "见党助克损、用神受制，须待岁运" + ELEM_NAME[(yongElem + 4) % 5] + "（" + elemRoleName(dayElem, (yongElem + 4) % 5)
                                + "）生" + ELEM_NAME[yongElem] + "、" + ELEM_NAME[zhiJiElem] + "（" + elemRoleName(dayElem, zhiJiElem)
                                + "）制" + ELEM_NAME[jiElem] + "解绊，方能真神得用。"
                            : monthKong
                                ? "真神得用=false——" + (yongTouGan ? "" : "用神" + ELEM_NAME[yongElem] + "藏支有根而不透（非无根）、")
                                    + "官杀偏重、须岁运引透生身；月支" + ZHI[p[1][1]] + "逢旬空乃格神"
                                    + ELEM_NAME[ZHI_ELEM[p[1][1]]] + "（月令本气）落空、格待成（须填实/冲实引透），"
                                    + "非用神之根虚浮。"
                                : "真神得用=false——用神虽" + (yongTouGan ? "透干" : "藏支") + "有根，然"
                                    + ELEM_NAME[jiElem] + "（" + elemRoleName(dayElem, jiElem) + "）凡" + jiCount
                                    + "见党助克损、用神受制，须待岁运" + ELEM_NAME[zhiJiElem] + "（" + elemRoleName(dayElem, zhiJiElem)
                                    + "）制" + ELEM_NAME[jiElem] + "解绊，方能真神得用。");
            // 层次四档，依《滴天髓·何知章》「何知其人富？财气通门户；何知其人贵？官星有理会；
            //   何知其人贫？财神反不真；何知其人贱？官星还不见」——以财星/官星是否有力通门户、
            //   身能否任财官为断（富须「财旺身强」、贵须「官旺身旺」），非以单纯身强身弱配档。
            //   （旧法「格成身弱即富格／格破身弱即贫贱」无典据，已废。）
            int guanElem = (dayElem + 3) % 5;
            int caiElem = (dayElem + 2) % 5;
            int shiShangElem = (dayElem + 1) % 5;
            boolean caiTouGan = false, caiYouGen = false;
            boolean guanTouGan = false, guanYouGen = false;
            boolean shiShangTouGan = false;
            for (int i = 0; i < 4; i++) {
                int ge = GAN_ELEM[p[i][0]];
                if (ge == caiElem) { caiTouGan = true; }
                if (ge == guanElem) { guanTouGan = true; }
                if (ge == shiShangElem) { shiShangTouGan = true; }
                for (String hs : ZHI_HIDDEN[p[i][1]]) {
                    int he = GAN_ELEM[indexOf(GAN, hs)];
                    if (he == caiElem) { caiYouGen = true; }
                    if (he == guanElem) { guanYouGen = true; }
                }
            }
            boolean caiDangLing = ZHI_ELEM[p[1][1]] == caiElem;
            boolean guanDangLing = ZHI_ELEM[p[1][1]] == guanElem;
            // 《滴天髓·何知章》「何知其人富，财气通门户；何知其人贵，官星有理会」——
            //   「通门户／有理会」须财官有力（透干有根、或当令得旺），方能「通门户／有理会」；
            //   仅藏支一见而无根不透者，只是「有星」未「通门户」，不作富/贵断（非财神反不真之贫，只归中平）。
            boolean caiTong = caiDangLing || (caiTouGan && caiYouGen);
            // 官星有理会：除有力外，伤官格见官透为「伤官见官」之害，官星失其理，不作贵断。
            boolean shangGuanJianGuan = "伤官".equals(monthGodName(p)) && shiShangTouGan && guanTouGan;
            boolean guanLiHui = (guanDangLing || (guanTouGan && guanYouGen)) && !shangGuanJianGuan;
            boolean shenNengRen = !bodyWeak; // 身能任财官（财旺身强、官旺身旺）
            String level;
            if (!geBreak && pi.zhuanWang) {
                // 《滴天髓·何知章》「得象得气、得局、得格者，妻子富贵两全」
                level = "贵格·富贵两全（得局得格、气势成象）";
            } else if (geCheng && shenNengRen && guanLiHui && caiTong) {
                level = "贵格·亦主富（官星有理会兼财气通门户，富贵两全）";
            } else if (geCheng && shenNengRen && guanLiHui) {
                level = "贵格（官星有理会：官星有力、身能任官）";
            } else if (geCheng && shenNengRen && caiTong) {
                level = "富格（财气通门户：财星有力、身强任财）";
            } else if (geBreak) {
                level = bodyWeak ? "平常偏下（格破身弱·用神无力，须岁运救应，非必贫贱）" : "平常偏下（格破·待岁运救应）";
            } else if (!shenNengRen && caiTong) {
                // 《滴天髓》「财重却轻身弱……皆为财神不真」→ 贫（财旺身弱不任财）
                level = "贫格（财神反不真·财旺身弱不任财）";
            } else if (bodyStrong && !guanLiHui && !caiTong) {
                // 身旺而财官俱不显：不得径断贫贱（贫贱须「财神反不真」「官星还不见」兼身弱或格破）；
                //   此属食伤泄秀、衣食可足而富贵有限，待岁运补财官方显，行藏偏劳碌。
                level = "中平（身旺而财官不显·食伤泄秀，衣食可足而富贵有限，行藏偏劳碌，宜岁运补财官）";
            } else if (!guanLiHui && !caiTong) {
                level = "中平偏下（财官俱不显、身亦无甚凭借，宜岁运引扶，非必贫贱）";
            } else if (geCheng) {
                level = "平常格（格成而财官未通门户，衣禄平顺、富贵有限）";
            } else {
                level = "平常格（格局待成、财官虽有未成门户，衣禄平顺、待岁运引扶）";
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("chartLevel", level);
            result.put("purityTruth", purity);
            completeResult(client, job, result);
            log.info("✅ judgeChartLevel | {} | {}", level, patternGrade);
        } catch (Exception e) {
            fail(job, client, "JUDGE_CHART_LEVEL_FAILED", e);
        }
    }

    // ==========================================================================
    // 节点 11：大运流年推演（analyzeFortuneCycles）
    // 实现方法：程序（岁运推算，**默认年级** ⑭，可扩月/日/时）+ 本体 fortune:（R16/R17）。
    // 出处：三命通会卷二·论大运/论太岁/论遁月时；渊海子平卷之三·论运化气/化气十段锦。
    // ==========================================================================
    @JobWorker(type = "analyzeFortuneCycles", autoComplete = false)
    public void handleFortuneCycles(final ActivatedJob job, final JobClient client) {
        try {
            Map<String, Object> vars = job.getVariablesAsMap();
            int[][] p = parsePillars(str(vars.get("fourPillars")));
            String granularity = orDefault(str(vars.get("granularity")), "年");
            double startAge = toDouble(vars.get("startAge"), 1.0);
            String[] fortunes = orDefault(str(vars.get("fortuneSequence")), "").split(",");
            int birthYear = LocalDateTime.parse(parseChartBase(str(vars.get("chartBase"))).get("SOLAR")).getYear();

            // 大运年龄段
            StringBuilder cycles = new StringBuilder("大运（起运").append(startAge).append("岁）：");
            for (int i = 0; i < fortunes.length; i++) {
                if (fortunes[i].isBlank()) {
                    continue;
                }
                double from = startAge + i * 10;
                cycles.append(fortunes[i]).append("（").append(fmt(from)).append("-").append(fmt(from + 10)).append("岁）");
                if (i < fortunes.length - 1) {
                    cycles.append("、");
                }
            }
            // 流年（自出生年起 10 年）
            StringBuilder years = new StringBuilder("；流年（年级，以出生年起）：");
            for (int i = 0; i < 10; i++) {
                int y = birthYear + i;
                years.append(y).append(ganZhi(new int[]{floorMod(y - 4, 10), floorMod(y - 4, 12)}));
                if (i < 9) {
                    years.append("、");
                }
            }
            // 流月：以流年干五虎遁起寅月，十二「节」为月建（三命通会卷二·论遁月时）
            years.append("；流月（").append(birthYear).append("年十二月建·五虎遁）：");
            for (int m = 0; m < 12; m++) {
                int mbranch = floorMod(2 + m, 12); // 寅(2)…丑(1)
                int yinStem = (floorMod(floorMod(birthYear - 4, 10), 5) * 2 + 2) % 10;
                int mstem = (yinStem + floorMod(mbranch - 2, 12)) % 10;
                years.append(ganZhi(new int[]{mstem, mbranch}));
                if (m < 11) {
                    years.append("、");
                }
            }
            // 流日/流时：干支纪日（JDN 锚定）+ 五鼠遁起时；本版以当年立春日为锚示例，逐日/逐时按历法递推
            if ("日".equals(granularity) || "时".equals(granularity)) {
                LocalDate anchor = solarTermDateTime(birthYear, 2).toLocalDate();
                int[] dp = dayPillar(anchor);
                years.append("；流日/流时（历法干支递推，JDN 锚定）：立春日").append(ganZhi(dp)).append("日");
                if ("时".equals(granularity)) {
                    years.append("，其十二时（五鼠遁）：");
                    for (int h = 0; h < 12; h++) {
                        years.append(ganZhi(hourPillar(dp[0], ZHI_START_HOUR[h])));
                        if (h < 11) {
                            years.append("、");
                        }
                    }
                }
            }

            // 特殊岁运格局
            List<String> special = new ArrayList<>();
            String dayGZ = ganZhi(p[2]);
            for (int i = 0; i < 10; i++) {
                int y = birthYear + i;
                int yi = floorMod(y - 4, 60);
                String ygz = ganZhiFromIndex(yi);
                // 岁运并临：流年干支 == 该年所行之运干支（三命通会·论大运「甲子流年又是甲子运，谓之岁运并临」）
                double age = y - birthYear;
                int fidx = (int) Math.floor((age - startAge) / 10.0);
                if (fidx >= 0 && fidx < fortunes.length && !fortunes[fidx].isBlank()
                        && ygz.equals(fortunes[fidx].trim())) {
                    special.add(y + "岁运并临（与" + fortunes[fidx].trim() + "运同）");
                }
                if (tianKeDiChong(ygz, dayGZ)) {
                    special.add(y + "天克地冲日柱");
                }
                if (ygz.equals(dayGZ)) {
                    special.add(y + "日柱伏吟");
                }
            }
            String specialCycles = special.isEmpty() ? "未见岁运并临/天克地冲/伏吟等特殊格局"
                    : String.join("；", special) + "（岁运并临/天克地冲（即反吟）/伏吟已判；桃花红鸾见神煞盘）";

            // 逐年评析：以本命（实效）用神/忌神为准，逐运判扶抑得失与主应（客户裁定：逐年评吉凶 + 应事）
            int dayGan = p[2][0];
            int dayElem = GAN_ELEM[dayGan];
            boolean bodyStrong = parseStrength(str(vars.get("strength"))).strong;
            int yongElem = effectiveYongElem(p, dayGan, bodyStrong);
            int jiElem = jiFromYong(p, yongElem);
            String gender = orDefault(parseChartBase(str(vars.get("chartBase"))).get("GENDER"), "男");
            LocalDateTime birthSolar = LocalDateTime.parse(parseChartBase(str(vars.get("chartBase"))).get("SOLAR"));
            String analysis = buildFortuneAnalysis(p, birthSolar, startAge, fortunes, yongElem, jiElem, dayElem, bodyStrong, gender, str(vars.get("strength")));

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("fortuneCycles", cycles + years.toString());
            result.put("fortuneAnalysis", analysis);
            result.put("specialCycles", specialCycles);
            completeResult(client, job, result);
            log.info("✅ analyzeFortuneCycles | {}", specialCycles);
        } catch (Exception e) {
            fail(job, client, "FORTUNE_CYCLES_FAILED", e);
        }
    }

    // ==========================================================================
    // 节点 12：应期推断与运限法（inferTimingAndStages）
    // 实现方法：程序 + 本体 fortune:ResponsePeriod / LifeStageLimit、deity:Void（R18）。
    // 出处：三命通会·总论岁运「月管初主，日管中主，时管末主，年则总统之」；论空亡（应期）。
    // ==========================================================================
    @JobWorker(type = "inferTimingAndStages", autoComplete = false)
    public void handleTimingAndStages(final ActivatedJob job, final JobClient client) {
        try {
            Map<String, Object> vars = job.getVariablesAsMap();
            int[][] p = parsePillars(str(vars.get("fourPillars")));
            String voidness = orDefault(str(vars.get("voidness")), "");

            String timing = "应期取用：填实（值空亡之支之年运应事）、冲实（冲空亡之支）、合住（合其用神/忌神）、透出（用神/忌神透干之年）。"
                    + "结合空亡：" + voidness + "。（年级为主给区间，月级可选）";

            String lifeStages = "运限法（柱分主阶段）：年柱主少年·根祖/祖上；月柱主青年·父母/兄弟；"
                    + "日柱主中年·自身/妻妾；时柱主老年·子息。各柱干支：" + ganZhi(p[0]) + "（年）、" + ganZhi(p[1])
                    + "（月）、" + ganZhi(p[2]) + "（日）、" + ganZhi(p[3]) + "（时）；"
                    + "岁运引动何柱，即应在对应人生阶段。";

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("timing", timing);
            result.put("lifeStages", lifeStages);
            completeResult(client, job, result);
            log.info("✅ inferTimingAndStages | {}", lifeStages);
        } catch (Exception e) {
            fail(job, client, "TIMING_STAGES_FAILED", e);
        }
    }

    // ==========================================================================
    // 节点 13：岁运与原局综合辩证断（synthesizeFortuneChart）
    // 实现方法：程序 + 本体 fortune:FortuneSynthesis。出处：子平真诠·论行运成格变格。
    // ==========================================================================
    @JobWorker(type = "synthesizeFortuneChart", autoComplete = false)
    public void handleSynthesizeFortuneChart(final ActivatedJob job, final JobClient client) {
        try {
            Map<String, Object> vars = job.getVariablesAsMap();
            int[][] p = parsePillars(str(vars.get("fourPillars")));
            int dayGan = p[2][0];
            int dayElem = GAN_ELEM[dayGan];
            boolean bodyStrong = parseStrength(str(vars.get("strength"))).strong;
            int yongElem = effectiveYongElem(p, dayGan, bodyStrong);
            int jiElem = jiFromYong(p, yongElem);
            double startAge = toDouble(vars.get("startAge"), 1.0);
            String[] fortunes = orDefault(str(vars.get("fortuneSequence")), "").split(",");
            String chartLevel = orDefault(str(vars.get("chartLevel")), "");
            String patternGrade = orDefault(str(vars.get("patternGrade")), "");

            // 岁运辩证：按本命用神/忌神 + 具体大运干支逐运推导（不再用通用模板）
            String trend = buildFortuneSynthesis(p, startAge, fortunes, yongElem, jiElem, dayElem, patternGrade, bodyStrong);

            String transformation = "成格变格：本命格局「" + patternGrade
                    + "」，用神" + ELEM_NAME[yongElem] + "（" + elemRoleName(dayElem, yongElem) + "）、忌神"
                    + ELEM_NAME[jiElem] + "（" + elemRoleName(dayElem, jiElem) + "）；"
                    + "岁运扶用神则格愈成、事顺，岁运冲克用神/格神则成格变破格、宜谨；"
                    + "原局忌神得岁运生扶则凶显、得制化则凶减。命局层次：" + chartLevel + "。（逐运吉凶见「岁运辩证」。）";

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("fortuneTrend", trend);
            result.put("transformationNote", transformation);
            completeResult(client, job, result);
            log.info("✅ synthesizeFortuneChart | 综合辩证已生成");
        } catch (Exception e) {
            fail(job, client, "SYNTHESIZE_FORTUNE_FAILED", e);
        }
    }

    // ==========================================================================
    // 节点 14：分类断语（generateCategoryVerdicts）
    // 实现方法：本体 reading:（Temperament/Disease/KinshipFortune/CategoryFortune）+ 程序（编排，⑱ 六书原语 + 现代白话，保留出处）。
    // 出处：三命通会卷九·论疾病先知五脏六腑所属干支；滴天髓（性情/才德）；渊海子平卷之三·六亲总论。
    // ==========================================================================
    @JobWorker(type = "generateCategoryVerdicts", autoComplete = false)
    public void handleCategoryVerdicts(final ActivatedJob job, final JobClient client) {
        try {
            Map<String, Object> vars = job.getVariablesAsMap();
            int[][] p = parsePillars(str(vars.get("fourPillars")));
            int dayGan = p[2][0];
            int dayElem = GAN_ELEM[dayGan];
            int monthBranch = p[1][1];
            Set<String> gods = tenGodSet(dayGan, p);

            // 疾病：五脏六腑所属干支，逐支按本命五行配脏腑
            String[] zang = {"肝/胆", "心/小肠", "脾/胃", "肺/大肠", "肾/膀胱"};
            StringBuilder disease = new StringBuilder("疾病（五脏六腑所属干支）：");
            for (int i = 0; i < 4; i++) {
                disease.append(ZHI[p[i][1]]).append("·").append(ELEM_NAME[ZHI_ELEM[p[i][1]]])
                        .append("属").append(zang[ZHI_ELEM[p[i][1]]]).append("；");
            }
            disease.append("日主").append(GAN[dayGan]).append(ELEM_NAME[dayElem]).append("主").append(zang[dayElem]).append("。");

            // 性情才德：以本命日主五行 + 实际所见十神为纲（非通用原理罗列）
            StringBuilder temperament = new StringBuilder("性情才德：日主").append(GAN[dayGan])
                    .append(ELEM_NAME[dayElem]).append("，" + elemNature(dayElem) + "；四柱十神见：")
                    .append(String.join("、", gods)).append("。");
            if (gods.contains("正印") || gods.contains("偏印")) {
                temperament.append("印现，主厚重、慈惠、好学；");
            }
            if (gods.contains("正官") || gods.contains("偏官")) {
                temperament.append("官杀现，主端谨、自律、重责任；");
            }
            if (gods.contains("正财") || gods.contains("偏财")) {
                temperament.append("财现，主务实、勤俭、重信义；");
            }
            if (gods.contains("食神") || gods.contains("伤官")) {
                temperament.append("食伤现，主聪敏、才艺、善表达；");
            }
            if (gods.contains("比肩") || gods.contains("劫财")) {
                temperament.append("比劫现，主刚果、自主、重友；");
            }
            temperament.append("性情随格局成败与岁运而分。");

            // 六亲吉凶：按宫位逐位推导（年祖上/父母、月父母/兄弟、日配偶、时子息）——
            // 以该宫干支所含十神定六亲角色，再按六亲受克法则断旺衰吉凶，逐位给「结论 + 依据」（据本命实况）
            String gender = orDefault(str(vars.get("gender")), "男");
            boolean strongK = parseStrength(str(vars.get("strength"))).strong;
            int yongElemK = effectiveYongElem(p, dayGan, strongK);
            int jiElemK = jiFromYong(p, yongElemK);
            String kinship = buildKinshipVerdicts(p, dayGan, dayElem, yongElemK, jiElemK, gender);

            // 分类事项断：以本命十神/宫位/神煞为准，逐项按本命推导并给依据（不再用通用套话）
            String deities = orDefault(str(vars.get("deities")), "");
            String category = buildCategoryVerdicts(p, dayGan, dayElem, monthBranch, gods, deities, gender, strongK);

            String all = temperament + "\n" + disease + "\n" + kinship + "\n" + category;

            // 典籍原文征引（第29步·回归修复）：日时断/十干坐支/月支断/六亲说 + 病药动静盖头 + 五行时地 + 成篇赋诀 + 六典籍全文节引
            String dayPillarK = ganZhi(p[2]);
            String hourPillarK = ganZhi(p[3]);
            List<String> cites = new ArrayList<>();
            String dhClause = clauseDayHour(dayPillarK, hourPillarK);
            if (dhClause != null) { cites.add(dhClause); }
            String ssClause = clauseStemSeat(GAN[dayGan]);
            if (ssClause != null) { cites.add(ssClause); }
            String mbClause = clauseMonthBranch(ZHI[monthBranch], GAN[dayGan]);
            if (mbClause != null) { cites.add(mbClause); }
            String kinClause = clauseKinship();
            if (kinClause != null) { cites.add(kinClause); }
            cites.addAll(citeTheory());
            cites.addAll(citeAuspiceTimePlace(ELEM_NAME[dayElem]));
            cites.addAll(citeFu(fuKeywords(gods)));
            cites.addAll(citeClassic("滴天髓", "中和"));
            cites.addAll(citeClassic("三命通会", "论五行生成"));
            String clauseCitations = cites.isEmpty() ? "" : "\n【典籍原文征引】\n" + String.join("\n", cites);
            if (!clauseCitations.isEmpty()) {
                all = all + clauseCitations;
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("categoryVerdicts", all);
            result.put("clauseCitations", clauseCitations);
            completeResult(client, job, result);
            log.info("✅ generateCategoryVerdicts | 分类断语已生成");
        } catch (Exception e) {
            fail(job, client, "CATEGORY_VERDICTS_FAILED", e);
        }
    }

    // ==========================================================================
    // 节点 M1：合婚配对分析（analyzeMarriageMatch）
    // 实现方法：程序（编排，双方各跑一轮排盘分析）+ 本体 reading:MarriageMatch（R23）。
    // 出处：《神峰通考》卷一·男女合婚说「合婚之正理」——男命比劫重者择妇命伤食重者配之，反之亦然。
    // ==========================================================================
    @JobWorker(type = "analyzeMarriageMatch", autoComplete = false)
    public void handleMarriageMatch(final ActivatedJob job, final JobClient client) {
        try {
            Map<String, Object> vars = job.getVariablesAsMap();
            Map<String, String> cb = parseChartBase(str(vars.get("chartBase")));
            Object partnerVar = vars.get("partnerBirth");
            if (partnerVar == null || (partnerVar instanceof String ps && ps.isBlank())) {
                throw new IllegalArgumentException("合婚模式须提供 partnerBirth（对方生辰）");
            }
            // 本人四柱
            int[][] self = computePillars(cb);
            // 对方四柱（与本人同口径：农历→公历换算 + 出生地经度真太阳时 + 早晚子时换日；
            //   出生地/历法支持「partnerBirth 内嵌」或顶层 partnerCalendar/partnerBirthPlace 两种来源）
            String partnerCalendar = orDefault(str(vars.get("partnerCalendar")), birthCalendar(partnerVar));
            String partnerBirthPlace = orDefault(str(vars.get("partnerBirthPlace")), birthPlaceOf(partnerVar));
            LocalDateTime pdt = correctBirthDateTime(partnerVar, partnerCalendar, partnerBirthPlace);
            boolean pLateZi = pdt.getHour() == 23;
            LocalDate pd = pLateZi ? pdt.toLocalDate().plusDays(1) : pdt.toLocalDate();
            int[][] partner = {
                    yearPillar(pdt), monthPillar(pdt),
                    dayPillar(pd), hourPillar(dayPillar(pd)[0], pdt.getHour())
            };

            String selfPillars = ganZhi(self[0]) + " " + ganZhi(self[1]) + " " + ganZhi(self[2]) + " " + ganZhi(self[3]);
            String partnerPillars = ganZhi(partner[0]) + " " + ganZhi(partner[1]) + " " + ganZhi(partner[2]) + " " + ganZhi(partner[3]);

            // 互补判定（R23）：五行盈亏互补 + 比劫/伤食配属
            String selfLack = lackElements(self);
            String partnerLack = lackElements(partner);
            int selfBiJie = countTenGodCategory(self, "比劫");
            int selfShiShang = countTenGodCategory(self, "食伤");
            int partnerBiJie = countTenGodCategory(partner, "比劫");
            int partnerShiShang = countTenGodCategory(partner, "食伤");

            StringBuilder comp = new StringBuilder();
            comp.append("本人四柱：").append(selfPillars).append("；对方四柱：").append(partnerPillars).append("。");
            comp.append("五行互补：本人所欠[").append(selfLack.isEmpty() ? "无" : selfLack).append("]，对方所欠[")
                    .append(partnerLack.isEmpty() ? "无" : partnerLack).append("]。");
            comp.append("比劫/食伤配属（神峰通考正理）：本人比劫=").append(selfBiJie).append("、食伤=").append(selfShiShang)
                    .append("；对方比劫=").append(partnerBiJie).append("、食伤=").append(partnerShiShang).append("。");

            // 合婚三维扩展（L11）：年命纳音生克 / 生肖合冲 / 神煞合参
            // 出处见 target/classic/l11_classical_basis.md（三命通会卷一论纳音、卷二论六合/三合/冲击/六害/三刑/诸神煞）。
            String selfNy = nayinOf(self[0][0], self[0][1]);
            String partnerNy = nayinOf(partner[0][0], partner[0][1]);
            int se = nayinElemIndex(selfNy);
            int pe = nayinElemIndex(partnerNy);
            // 纳音由合法干支恒可识别（self/partner 四柱均由 yearPillar/monthPillar/dayPillar/hourPillar 计算，干支恒合法，
            // nayinOf→nayinElemIndex 恒 ≥0）；原 `se<0||pe<0` 待考分支与其 `? :` 兜底为不可达冗余，按“查证经典→改代码”等价去除。
            String nyRel;
            if (se == pe) {
                nyRel = "比和";
            } else if (generates(se, pe) || generates(pe, se)) {
                nyRel = "相生";
            } else {
                nyRel = "相克";
            }
            String nayinText = "年命纳音：本人年命「" + selfNy + "」（" + ELEM_NAME[se] + "）、对方年命「"
                    + partnerNy + "」（" + ELEM_NAME[pe] + "），二者" + nyRel
                    + ("相生".equals(nyRel) ? "，纳音相生为和，宜相资扶。" : "相克".equals(nyRel) ? "，纳音相克宜以用神/神煞调和。" : "。");

            String zodiacRel = yearBranchRelation(self[0][1], partner[0][1]);
            String zodiacText = "生肖合冲：本人生肖属" + ZHI_ANIMAL[self[0][1]] + "（" + ZHI[self[0][1]] + "）、对方属"
                    + ZHI_ANIMAL[partner[0][1]] + "（" + ZHI[partner[0][1]] + "），年支" + zodiacRel + "。";

            int selfGroup = sanheGroupOf(self[0][1]);
            StringBuilder shensha = new StringBuilder();
            List<String> ssHit = new ArrayList<>();
            // sanheGroupOf 对十二支恒返回 0..3（三合四局覆盖全支），selfGroup<0 不可达；改用无分支裸块（行为不变，消除死支）。
            {
                int th = TAOHUA[selfGroup], ym = YIMA[selfGroup], hg = HUAGAI[selfGroup];
                shensha.append("神煞合参：本人年支起桃花在").append(ZHI[th]).append("、驿马在").append(ZHI[ym])
                        .append("、华盖在").append(ZHI[hg]).append("；");
                if (containsZhi(partner, th)) {
                    ssHit.add("对方命带本人桃花（情缘相牵）");
                }
                if (containsZhi(partner, ym)) {
                    ssHit.add("对方命带本人驿马（动迁聚散之缘）");
                }
                if (containsZhi(partner, hg)) {
                    ssHit.add("对方命带本人华盖（清高艺术之契）");
                }
            }
            int[] selfTianyi = TIANYI[self[2][0]];
            if (containsZhi(partner, selfTianyi[0]) || containsZhi(partner, selfTianyi[1])) {
                ssHit.add("对方命见本人天乙贵人（贵人相扶）");
            }
            shensha.append(ssHit.isEmpty() ? "对方四柱未见本人所起神煞位，神煞合参无显著相遇。" : String.join("；", ssHit) + "。");

            String harmonyText = nayinText + zodiacText + shensha;
            comp.append("\n").append(harmonyText);

            boolean complementary = (!selfLack.isEmpty() && partnerContains(partner, selfLack.split(",")))
                    || (selfBiJie >= 2 && partnerShiShang >= 2) || (selfShiShang >= 2 && partnerBiJie >= 2);
            String conclusion = complementary
                    ? verdictOr("合婚", "hehun_complementary", "合婚结论：双方五行/比劫伤食互补（依《神峰通考》卷一·男女合婚说正理），配合较佳，宜相扶相济。")
                    : verdictOr("合婚", "hehun_plain", "合婚结论：双方互补性一般，宜以用神互补、神煞配合详参；《神峰通考》正理未见明显相配。");

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("marriageConclusion", conclusion);
            result.put("complementAnalysis", comp.toString());
            result.put("nayinRelation", nayinText);
            result.put("zodiacRelation", zodiacText);
            result.put("shenshaMatch", shensha.toString());
            completeResult(client, job, result);
            log.info("✅ analyzeMarriageMatch | 合婚结论已生成 | 互补={}", complementary);
        } catch (Exception e) {
            fail(job, client, "MARRIAGE_MATCH_FAILED", e);
        }
    }

    // ==========================================================================
    // 节点 W：女命专论（analyzeFemaleChart）
    // 实现方法：本体 reading:LadyFortune + 程序。
    // 出处：渊海子平卷之二·论女命/论女命富贵贫贱；三命通会·论女命。
    // ==========================================================================
    @JobWorker(type = "analyzeFemaleChart", autoComplete = false)
    public void handleFemaleChart(final ActivatedJob job, final JobClient client) {
        try {
            Map<String, Object> vars = job.getVariablesAsMap();
            int[][] p = parsePillars(str(vars.get("fourPillars")));
            int dayGan = p[2][0];
            // 透干/藏支须分判：天干透（除日干自身）与地支藏干分列计数，避免「藏一支即判重重」之误
            boolean guanTou = false, guanCang = false, shiTou = false, shiCang = false;
            boolean zhengGuanSeen = false, pianGuanSeen = false;
            int biJieTou = 0, biJieCnt = 0;
            StringBuilder guanTouNames = new StringBuilder();
            StringBuilder shiTouNames = new StringBuilder();
            String guanCangText = "";
            for (int i = 0; i < 4; i++) {
                String gn = TEN_GOD[tenGodIndex(dayGan, p[i][0])];
                if ("正官".equals(gn)) {
                    zhengGuanSeen = true;
                }
                if ("偏官".equals(gn)) {
                    pianGuanSeen = true;
                }
                boolean isGuan = "正官".equals(gn) || "偏官".equals(gn);
                boolean isShi = "食神".equals(gn) || "伤官".equals(gn);
                boolean isBi = "比肩".equals(gn) || "劫财".equals(gn);
                if (i != 2) {
                    if (isGuan) {
                        guanTou = true;
                        guanTouNames.append(GAN[p[i][0]]);
                    }
                    if (isShi) {
                        shiTou = true;
                        shiTouNames.append(GAN[p[i][0]]);
                    }
                    if (isBi) {
                        biJieTou++;
                    }
                }
                for (String hs : ZHI_HIDDEN[p[i][1]]) {
                    String hg = TEN_GOD[tenGodIndex(dayGan, indexOf(GAN, hs))];
                    if ("正官".equals(hg)) {
                        zhengGuanSeen = true;
                    }
                    if ("偏官".equals(hg)) {
                        pianGuanSeen = true;
                    }
                    if ("正官".equals(hg) || "偏官".equals(hg)) {
                        guanCang = true;
                        if (guanCangText.isEmpty()) {
                            guanCangText = GAN[indexOf(GAN, hs)] + "于" + ZHI[p[i][1]];
                        }
                    }
                    if ("食神".equals(hg) || "伤官".equals(hg)) {
                        shiCang = true;
                    }
                    if ("比肩".equals(hg) || "劫财".equals(hg)) {
                        biJieCnt++;
                    }
                }
            }
            biJieCnt += biJieTou;
            boolean biJieZhong = biJieTou >= 1 || biJieCnt >= 3;
            // 身强弱：身中和偏弱时比劫为「帮身」而非「夺夫」，不可据比劫见即断夺夫（《滴天髓·衰旺》）
            boolean shenRuoF = parseStrength(str(vars.get("strength"))).weak;
            // 夫星（官杀天干）/子星（食伤天干）是否被他干合去（五合，天干 index 差 5）
            boolean fuXingHe = false, ziXingHe = false;
            String fuHeGan = "", ziHeGan = "";
            for (int i = 0; i < 4; i++) {
                if (i == 2) {
                    continue;
                }
                String gnF = TEN_GOD[tenGodIndex(dayGan, p[i][0])];
                boolean isGuanF = "正官".equals(gnF) || "偏官".equals(gnF);
                boolean isShiF = "食神".equals(gnF) || "伤官".equals(gnF);
                if (isGuanF || isShiF) {
                    int hp = (p[i][0] + 5) % 10;
                    for (int j = 0; j < 4; j++) {
                        if (j != i && p[j][0] == hp) {
                            if (isGuanF) {
                                fuXingHe = true;
                                fuHeGan = GAN[p[j][0]];
                            }
                            if (isShiF) {
                                ziXingHe = true;
                                ziHeGan = GAN[p[j][0]];
                            }
                        }
                    }
                }
            }

            // 补充特征（L3 细化）：官杀/食伤/财/印之总数、天干五合、地支相刑、子午卯酉全、水泛无土
            int guanCnt = 0, shiCnt = 0, caiCnt = 0, yinCnt = 0;
            for (int i = 0; i < 4; i++) {
                if (i != 2) {
                    String gg = TEN_GOD[tenGodIndex(dayGan, p[i][0])];
                    if ("正官".equals(gg) || "偏官".equals(gg)) guanCnt++;
                    if ("食神".equals(gg) || "伤官".equals(gg)) shiCnt++;
                    if ("正财".equals(gg) || "偏财".equals(gg)) caiCnt++;
                    if ("正印".equals(gg) || "偏印".equals(gg)) yinCnt++;
                }
                for (String hs : ZHI_HIDDEN[p[i][1]]) {
                    String gg = TEN_GOD[tenGodIndex(dayGan, indexOf(GAN, hs))];
                    if ("正官".equals(gg) || "偏官".equals(gg)) guanCnt++;
                    if ("食神".equals(gg) || "伤官".equals(gg)) shiCnt++;
                    if ("正财".equals(gg) || "偏财".equals(gg)) caiCnt++;
                    if ("正印".equals(gg) || "偏印".equals(gg)) yinCnt++;
                }
            }
            boolean hasCaiF = caiCnt > 0, hasYinF = yinCnt > 0;
            boolean hasYinTouF = false;
            for (int i = 0; i < 4 && !hasYinTouF; i++) {
                if (i != 2) {
                    String yg = TEN_GOD[tenGodIndex(dayGan, p[i][0])];
                    if ("正印".equals(yg) || "偏印".equals(yg)) {
                        hasYinTouF = true;
                    }
                }
            }
            boolean ganHeF = false;
            for (int i = 0; i < 4 && !ganHeF; i++) {
                for (int j = i + 1; j < 4 && !ganHeF; j++) {
                    if (Math.abs(p[i][0] - p[j][0]) == 5) {
                        ganHeF = true;
                    }
                }
            }
            boolean zhiXingF = false;
            for (int i = 0; i < 4 && !zhiXingF; i++) {
                for (int j = i + 1; j < 4 && !zhiXingF; j++) {
                    if (xingPair(p[i][1], p[j][1])) {
                        zhiXingF = true;
                    }
                }
            }
            int siZheng = 0;
            for (int z : new int[]{0, 6, 3, 9}) {
                if (containsZhi(p, z)) {
                    siZheng++;
                }
            }
            boolean fourZheng = siZheng == 4;   // 子午卯酉全（遍野桃花）
            int[] elemCnt = new int[5];
            for (int i = 0; i < 4; i++) {
                elemCnt[GAN_ELEM[p[i][0]]]++;
                elemCnt[ZHI_ELEM[p[i][1]]]++;
            }
            boolean waterFloodNoEarth = elemCnt[4] >= 4 && elemCnt[2] == 0;   // 水泛无土
            StrengthInfo siF = parseStrength(str(vars.get("strength")));
            boolean neutralF = siF.neutral;
            boolean strongF = siF.strong;

            // 女命分类（L3 细化·重定 2026-11-13）：按《渊海子平·幽微赋／金玉赋／五行元理消息赋／女命总断歌》
            //   与《三命通会卷七·论女命》逐类给精确定义；分「良／劣／特」三档，良类优先、劣类从严，
            //   输出「主类＋兼类」，良劣不并存（命中良类则不复列劣类）。详见 target/classic/l3_classical_basis.md。
            boolean guanZa = zhengGuanSeen && pianGuanSeen; // 官杀混杂（正偏并见）
            int maxElem = 0;
            for (int e : elemCnt) {
                if (e > maxElem) {
                    maxElem = e;
                }
            }
            boolean heBalance = maxElem <= 3; // maxElem<=3 时 elemCnt[水]≤3，水泛(水≥4)必不成立；!waterFloodNoEarth 恒真＝死支，简化
            java.util.List<String> goodHit = new ArrayList<>();
            java.util.List<String> badHit = new ArrayList<>();
            java.util.List<String> specialHit = new ArrayList<>();
            // —— 良类（皆要求官星不杂；身不弱者方论富贵）——
            // 纯：「纯有富贵者，一官生旺」「女四纯则清贵」——官星有气、无七杀、身不弱、食伤不旺、无比劫透（纯一不杂）。
            if ((guanTou || guanCang) && !pianGuanSeen && !shenRuoF && shiCnt <= 1 && biJieTou == 0) goodHit.add("纯");
            // 清：正官透干、无七杀、身不弱、食伤不旺（清纯无浊）。
            if (guanTou && !pianGuanSeen && !shenRuoF && shiCnt <= 1) goodHit.add("清");
            // 贵：「一官生旺…必为贵者」——官星有气、无七杀、财印相资（财官印俱全）。
            if ((guanTou || guanCang) && !pianGuanSeen && hasCaiF && hasYinF) goodHit.add("贵");
            // 福寿两备：夫星（官杀）与子星（食伤）俱现、身不弱、无三刑、夫/子星不被合去。
            if ((guanTou || guanCang) && (shiTou || shiCang) && !shenRuoF && !zhiXingF && !fuXingHe && !ziXingHe) goodHit.add("福寿两备");
            // 安静守分：官星藏而不透（不争不显）、无比劫透动、夫星不被合去。
            if (guanCang && !guanTou && biJieTou == 0) goodHit.add("安静守分"); // 官不透⇒无官杀天干⇒夫星无从被合⇒!fuXingHe 恒真＝死支，简化
            // 和：「和」者中和安和——五行无一行独旺（不偏枯）、官星有气清纯、日主不弱。
            if (heBalance && (guanTou || guanCang) && !guanZa && !pianGuanSeen && !shenRuoF) goodHit.add("和");
            // —— 劣类（从严）——
            // 滚浪桃花：「丙子辛卯相逢，荒淫滚浪」（天干相合兼地支相刑）；「子午卯酉全备」遍野桃花。
            if ((ganHeF && zhiXingF) || fourZheng) badHit.add("滚浪桃花");
            // 娼：「娼淫者官杀交差」——官杀混杂、身弱、且无食伤透干制杀（有制化者不作娼贱）。
            if (guanZa && shenRuoF && !shiTou) badHit.add("娼");
            // 淫：「荒淫之欲，食神太过」（食伤太过而身弱）；「水盛则危…淫乱太盛」（水泛无土）。
            if (waterFloodNoEarth || (shiCnt >= 3 && shenRuoF)) badHit.add("淫");
            // 滥：「满柱杀多，不为克制」——官杀太多（≥3）。
            if (guanCnt >= 3) badHit.add("滥");
            // 浊：「浊淫者五行冲旺」——官杀混杂而身不弱（有制而浊，未至娼贱）。
            if (guanZa && !shenRuoF) badHit.add("浊");
            // 伤夫克子：「伤官旺而幼伤夫」——伤官透干、夫星不见、比劫动。
            if (shiTou && guanCnt == 0 && biJieTou >= 1) badHit.add("伤夫克子");
            // 旺子伤夫：「旺子丧夫，乃是官主失位」——子星（食伤）透旺、夫星藏弱不见、比劫不重。
            if ((shiTou || shiCnt >= 2) && !guanTou && guanCnt <= 1 && !biJieZhong) badHit.add("旺子伤夫");
            // 旺夫克子：「旺夫克子」——夫星（官杀）透旺、子星全无、身不弱、夫星不杂（单透一官或一杀）。
            if (guanTou && shiCnt == 0 && !shenRuoF && !guanZa) badHit.add("旺夫克子");
            // 横夭少年：「夭折丧于偏枯」——官杀食伤俱不见而命局偏枯（身弱或身旺无泄）。
            if (guanCnt == 0 && shiCnt == 0 && (shenRuoF || strongF)) badHit.add("横夭少年");
            // —— 特类 ——
            // 正偏自处（三命通会卷七·论女命）：正官（正夫）与七杀（偏夫）并见，官杀混杂、正偏交处。
            if (guanZa) specialHit.add("正偏自处");
            // 招嫁不定：官星藏而不透、比劫透动——夫星不明、身党相争，婚嫁不定。
            if (guanCang && !guanTou && biJieTou >= 1) specialHit.add("招嫁不定");

            String pickText;
            if (!goodHit.isEmpty()) {
                String primary = goodHit.get(0);
                List<String> second = new ArrayList<>(goodHit.subList(1, goodHit.size()));
                second.addAll(specialHit);
                pickText = "【女命分类·归类】主类：" + primary
                        + (second.isEmpty() ? "。" : "；兼类：" + String.join("、", second) + "。");
            } else if (!badHit.isEmpty()) {
                String primary = badHit.get(0);
                List<String> second = new ArrayList<>(badHit.subList(1, Math.min(badHit.size(), 3)));
                second.addAll(specialHit);
                pickText = "【女命分类·归类】主类：" + primary
                        + (second.isEmpty() ? "。" : "；兼类：" + String.join("、", second) + "。");
            } else if (!specialHit.isEmpty()) {
                pickText = "【女命分类·归类】主类：" + specialHit.get(0)
                        + (specialHit.size() > 1 ? "；兼类：" + String.join("、", specialHit.subList(1, specialHit.size())) + "。" : "。");
            } else {
                String pingHeTail = (guanTou || guanCang)
                        ? (shenRuoF ? "原局官杀偏重、日主偏弱，属平常偏弱、非大偏枯。" : "原局官杀偏重，宜以中和论其调候。")
                        : verdictOr("女命", "female_pinghe", "本命未见显著偏枯，归于平和一类。");
                pickText = "【女命分类·归类】" + ((fuXingHe || ziXingHe)
                        ? "夫星被合、子星受合，婚姻子女缘分有波动（不作「旺夫克子」等硬性归类）；" + pingHeTail
                        : pingHeTail);
            }

            String strengthLabelF = strengthLabel(str(vars.get("strength")));
            String guanTouText = guanTouNames.toString();
            String shiTouText = shiTouNames.toString();
            String fuXing = guanTou
                    ? "夫星（官杀" + guanTouText + "）透干"
                        + (fuXingHe
                            ? "、然被" + fuHeGan + "合（合而不化），官杀偏重"
                                + (guanCang ? "；又藏" + guanCangText + "，须岁运解合/引化" : "，须岁运解合方显")
                            : "而显")
                    : guanCang ? "夫星（官杀）藏而不透、须岁运引透方显" : "夫星（官杀）不显";
            String ziXing = shiTou
                    ? "子星（食伤" + shiTouText + "）透干"
                        + (ziXingHe ? "、然被" + ziHeGan + "合，子星受制，子女偏迟偏少（非谓无子）"
                                    : (hasYinTouF ? "、然被印星克制，子星受制，子女偏迟偏少（非谓无子）"
                                               : (shiCang ? "而显" : "而显，坐支无根、须岁运扶旺方显")))
                    : shiCang ? "子星（食伤）藏支、待岁运引透" : "子星（食伤）不显";
            int biJieCang = biJieCnt - biJieTou;
            String biJieStr = biJieZhong
                    ? (shenRuoF ? "比劫" + biJieCnt + "见（透" + biJieTou + "藏" + biJieCang + "）、身处" + strengthLabelF + "，帮身有限，不作夺夫论"
                                : "比劫较旺（共" + biJieCnt + "见、透" + biJieTou + "、藏" + biJieCang + "），留意夺夫之嫌")
                    : "比劫藏而不透、其数不重（共" + biJieCnt + "见），夺夫之力有限";
            String verdict = "女命专论：以官星为夫、食伤为子、财为夫之资。"
                    + "本命" + fuXing + "；" + ziXing + "；" + biJieStr + "。"
                    + "富贵贫贱：看官星清纯、食伤制化与格局成败；旺夫克子/旺子伤夫，随夫子二星之强弱喜忌而分。";
            if (pickText.indexOf("旺子伤夫") >= 0) {
                verdict += "本命夫星（官杀）藏而不透、子星（食伤）透干有根，乃夫星弱、子星显之象，宜注意夫妻缘分、以柔相待，不宜径断伤夫。";
            } else if (pickText.indexOf("伤夫克子") >= 0) {
                verdict += "夫星弱而比劫食伤并见，宜注意夫妻缘分、以柔相待，不宜径断克夫。";
            }
            verdict += pickText;

            // 典籍定向引据（第29步·回归修复）：女命分类名录（三命通会卷七·论女命；渊海子平·论女命，经 OBDA lady_fortune_catalog）
            List<String> ladyCites = new ArrayList<>();
            for (Map<String, String> lf : clauseLadyFortunes()) {
                String lname = lf.get("label");
                if (lname != null && pickText.contains(lname)) { // pickText 各分支必经赋值必非空，pickText!=null 恒真＝死支
                    ladyCites.add(lname + "：" + lf.get("text") + "（出处：" + lf.get("source") + "）");
                }
            }
            verdict += mergeCitations("女命分类·原著征引", ladyCites);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("femaleVerdict", verdict);
            completeResult(client, job, result);
            log.info("✅ analyzeFemaleChart | 女命专论已生成");
        } catch (Exception e) {
            fail(job, client, "FEMALE_CHART_FAILED", e);
        }
    }

    // ==========================================================================
    // 节点 C：小儿关杀（analyzeChildChart）
    // 实现方法：本体 reading:ChildFortune + 程序。出处：三命通会·论小儿关杀（小儿关煞例）。
    // ==========================================================================
    @JobWorker(type = "analyzeChildChart", autoComplete = false)
    public void handleChildChart(final ActivatedJob job, final JobClient client) {
        try {
            Map<String, Object> vars = job.getVariablesAsMap();
            int[][] p = parsePillars(str(vars.get("fourPillars")));
            int yearZhi = p[0][1];
            int grp = sanheGroup(yearZhi);
            // 子平正宗小儿关杀＝「偏官为关、偏财为杀，取生成之数断之」（渊海子平·论小儿关杀例；
            //   三命通会·论小儿）。下列按年支/时支实算驿马、桃花等；将军箭/阎王关/四季关/金锁关/鬼门关
            //   等异名关煞属库外民间流派，本库六书无据（全文检索 0 命中），故仅列名备参、不作断语。
            List<String> guan = new ArrayList<>();
            guan.add("本命年支" + ZHI[yearZhi] + "：" + ZHI_ANIMAL[yearZhi] + "相");
            if (YIMA[grp] == p[3][1]) {
                guan.add("时值驿马，主早动/奔波关");
            }
            if (TAOHUA[grp] == p[3][1]) {
                guan.add("时值桃花，主情感关");
            }

            String verdict = "小儿关杀（仅列按年支/时支实算者）：" + String.join("；", guan)
                    + "。另有将军箭、阎王关、四季关、金锁关、鬼门关等异名关煞，须按生月/生时逐一查验方可论；"
                    + "本系统未按生月/生时逐一起例，故仅列名备参、不作断语。";
            // 典籍定向引据（第29步·回归修复）：孕生男女（auspice_clause·孕生男女）
            verdict += mergeCitations("子息典籍引据", citeProgeny());

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("childVerdict", verdict);
            completeResult(client, job, result);
            log.info("✅ analyzeChildChart | 小儿关杀已生成");
        } catch (Exception e) {
            fail(job, client, "CHILD_CHART_FAILED", e);
        }
    }

    // ==========================================================================
    // 节点 15：修身处世劝诫（generateMoralAdvice）
    // 实现方法：本体 reading:Admonition + 程序。出处：滴天髓·知命（吉→守正持盈戒骄；凶→积德改行反求诸己）。
    // ==========================================================================
    @JobWorker(type = "generateMoralAdvice", autoComplete = false)
    public void handleMoralAdvice(final ActivatedJob job, final JobClient client) {
        try {
            Map<String, Object> vars = job.getVariablesAsMap();
            String level = orDefault(str(vars.get("chartLevel")), "");
            String trend = orDefault(str(vars.get("fortuneTrend")), "");

            boolean good = level.contains("贵") || level.contains("富");
            String prefix = "命局层次" + level + (good ? "，岁运多扶。" : "，岁运有阻。");
            String core = good
                    ? verdictOr("劝勉", "moral_good", "宜守正持盈、戒骄戒躁，勿因得而忘形；积善以培福，居安而思危。")
                    : verdictOr("劝勉", "moral_bad", "宜积德改行、反求诸己，悔过迁善以转祸为福；安命而自强，勿怨天尤人。");
            String advice = prefix + core;

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("moralAdvice", advice);
            completeResult(client, job, result);
            log.info("✅ generateMoralAdvice | {}", advice);
        } catch (Exception e) {
            fail(job, client, "MORAL_ADVICE_FAILED", e);
        }
    }

    // ==========================================================================
    // 节点 16：综合结论汇总（aggregateReading）
    // 实现方法：程序（编排，㉑ 命书体）+ 本体 reading:ReadingConclusion + SWRL R19/R20/R21。
    // 告警综合判据由程序编排（无数值阈值，㉒），SWRL 落最终标志。
    // ==========================================================================
    @JobWorker(type = "aggregateReading", autoComplete = false)
    public void handleAggregateReading(final ActivatedJob job, final JobClient client) {
        try {
            Map<String, Object> vars = job.getVariablesAsMap();
            Map<String, String> cb = parseChartBase(str(vars.get("chartBase")));

            StringBuilder sb = new StringBuilder();
            sb.append("【四柱命理命书】\n");
            sb.append("生辰：").append(cb.get("SOLAR")).append("（").append(cb.get("GENDER")).append("·")
                    .append(cb.get("SUBJECT")).append("·").append(cb.get("MODE")).append("）\n");
            appendLine(sb, "四柱", vars.get("fourPillars"));
            appendLine(sb, "纳音藏干", vars.get("nayin"));
            appendLine(sb, "日柱特性", vars.get("dayPillarTraits"));
            appendLine(sb, "干支关系", vars.get("ganzhiRelations"));
            appendLine(sb, "十神", vars.get("tenGods"));
            appendLine(sb, "神煞", vars.get("deities"));
            appendLine(sb, "空亡", vars.get("voidness"));
            appendLine(sb, "旺衰", vars.get("strength"));
            appendLine(sb, "十二长生", vars.get("twelvePalaces"));
            appendLine(sb, "格局", vars.get("patternResult"));
            appendLine(sb, "用神", vars.get("yongShen"));
            appendLine(sb, "调候", vars.get("tiaohou"));
            appendLine(sb, "层次", vars.get("chartLevel"));
            appendLine(sb, "岁运", vars.get("fortuneCycles"));
            appendLine(sb, "岁运评析", vars.get("fortuneAnalysis"));
            appendLine(sb, "应期", vars.get("timing"));
            appendLine(sb, "分类断", vars.get("categoryVerdicts"));
            appendLine(sb, "女命", vars.get("femaleVerdict"));
            appendLine(sb, "小儿", vars.get("childVerdict"));
            appendLine(sb, "合婚", vars.get("marriageConclusion"));
            appendLine(sb, "劝诫", vars.get("moralAdvice"));
            String reading = sb.toString();

            // 告警综合判据（不设数值阈值，㉒）：程序产出「本命事实」，本体 SWRL 推导「告警标志」，程序回读。
            String level = orDefault(str(vars.get("chartLevel")), "");
            String grade = orDefault(str(vars.get("patternGrade")), "");
            String strength = orDefault(str(vars.get("strength")), "");
            String deities = orDefault(str(vars.get("deities")), "");
            List<String> javaFlags = new ArrayList<>();
            if (level.contains("贫贱") || (parsePatternGrade(grade).geBreak && parseStrength(strength).weak)) {
                javaFlags.add("大凶");
            }
            if (deities.contains("羊刃") && deities.contains("相冲")) {
                javaFlags.add("寿元关");
            }
            // SWRL 上主链：本命事实物化 → Openllet flush 评估 rules:Imp(R19/R20/R21) → 回读 rules:AlertFlag 个体
            List<String> swrlFlags = deriveAlertsViaSwrl(level, grade, strength, deities);
            List<String> flags;
            String alertSource;
            if (swrlFlags != null && !swrlFlags.isEmpty()) {
                flags = swrlFlags;
                alertSource = "SWRL 推导";
            } else if (swrlFlags != null) {
                // 推理未产出命中：为不遗漏告警，回退程序判据（安全优先，双轨保险）
                flags = javaFlags;
                alertSource = "程序兜底（SWRL 未命中）";
            } else {
                flags = javaFlags;
                alertSource = "程序兜底（本体/推理不可用）";
            }
            boolean alertTriggered = !flags.isEmpty();
            String flagText = alertTriggered ? String.join("/", flags) : "无";

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("reading", reading);
            result.put("alertTriggered", alertTriggered);
            completeResult(client, job, result);
            log.info("✅ aggregateReading | 告警标志={}（{}）| 命书 {} 字", flagText, alertSource, reading.length());
        } catch (Exception e) {
            fail(job, client, "AGGREGATE_READING_FAILED", e);
        }
    }

    // ==========================================================================
    // 节点 A：外发告警消息（sendAlert）
    // 实现方法：外部调用 RabbitMqHandler（+ SWRL R22 命中即外发）；仅三类告警、无数值阈值（㉒）。
    // 外发失败仅记日志、不阻断主流程。
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
                payload.put("chartLevel", vars.get("chartLevel"));
                payload.put("reading", vars.get("reading"));
                payload.put("chartBase", vars.get("chartBase"));
                payload.put("timestamp", java.time.Instant.now().toString());
                mq = new RabbitMqHandler();
                mq.send(alertExchange, alertRoutingKey, payload);
                status = "SENT";
            } else {
                status = "SKIPPED_NO_ALERT";
            }
        } catch (Exception e) {
            // 外发失败不阻断主流程（客户裁定 ㉒）
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

    /**
     * SWRL 上主链：把本命判据事实物化进本体（临时 ABox），经 Openllet {@code flush()} 评估
     * SWRL 规则（R19 大凶 / R20 寿元关 / R21 重大疾病），回读 {@code rules:hasAlertFlag} 指向的
     * {@code AlertFlag} 个体（AlertDaXiong/AlertShouYuanGuan/AlertJiBing），据以产出告警类型。
     * 全程「临时注入 → 推理 → 回读 → 立即清除」（见 {@link BackendService#withTempAxioms}），不污染 TBox 基线。
     *
     * @return 告警类型列表；{@code null} 表示本体/推理不可用（调用方回退程序判据）
     */
    private List<String> deriveAlertsViaSwrl(String level, String grade, String strength, String deities) {
        if (backendService == null) {
            return null;
        }
        try {
            OWLDataFactory df = backendService.getDataFactory();
            if (df == null) {
                return null;
            }
            String ns = "http://example.org/sizhubazi/";
            OWLNamedIndividual f = df.getOWLNamedIndividual(IRI.create(ns + "chart/AlertProbe_" + System.nanoTime()));
            Set<OWLAxiom> ax = new java.util.LinkedHashSet<>();
            ax.add(df.getOWLClassAssertionAxiom(df.getOWLClass(IRI.create(ns + "chart/FourPillars")), f));
            // 事实物化：格局破 / 无可用用神 / 羊刃逢冲（皆由程序判据给出，供 SWRL 规则体匹配）
            boolean isBroken = parsePatternGrade(grade).geBreak || level.contains("贫贱");
            boolean noYongShen = parseStrength(strength).weak || level.contains("贫贱");
            if (isBroken) {
                ax.add(df.getOWLDataPropertyAssertionAxiom(
                        df.getOWLDataProperty(IRI.create(ns + "pattern/isBroken")), f, df.getOWLLiteral(true)));
            }
            if (noYongShen) {
                ax.add(df.getOWLDataPropertyAssertionAxiom(
                        df.getOWLDataProperty(IRI.create(ns + "yongshen/noYongShen")), f, df.getOWLLiteral(true)));
            }
            if (deities.contains("羊刃") && deities.contains("相冲")) {
                ax.add(df.getOWLDataPropertyAssertionAxiom(
                        df.getOWLDataProperty(IRI.create(ns + "chart/hasYangRenChong")), f, df.getOWLLiteral(true)));
            }
            OWLObjectProperty hasFlag = df.getOWLObjectProperty(IRI.create(ns + "rules/hasAlertFlag"));
            return backendService.withTempAxioms(ax, reasoner -> {
                List<String> flags = new ArrayList<>();
                for (OWLNamedIndividual ind : reasoner.getObjectPropertyValues(f, hasFlag).getFlattened()) {
                    switch (ind.getIRI().getFragment()) {
                        case "AlertDaXiong" -> flags.add("大凶");
                        case "AlertShouYuanGuan" -> flags.add("寿元关");
                        case "AlertJiBing" -> flags.add("重大疾病");
                        default -> { }
                    }
                }
                return flags;
            });
        } catch (Exception e) {
            log.warn("⚠️ SWRL 告警推导降级（回退程序判据）：{}", e.getMessage());
            return null;
        }
    }

    // ==========================================================================
    // 节点 17：结果落库与返回（persistAndReturn）
    // 实现方法：程序（落库，经 OBDA `addComponent`）+ 本体 reading:CaseRecord（完整存入含出生地、不脱敏，㉓）。
    // ==========================================================================
    @JobWorker(type = "persistAndReturn", autoComplete = false)
    public void handlePersistAndReturn(final ActivatedJob job, final JobClient client) {
        try {
            Map<String, Object> vars = job.getVariablesAsMap();
            Map<String, String> cb = parseChartBase(str(vars.get("chartBase")));
            String fourPillars = orDefault(str(vars.get("fourPillars")), "");
            String reading = orDefault(str(vars.get("reading")), "");

            String caseRecord = "命例：" + cb.get("SOLAR") + "｜出生地：" + cb.getOrDefault("PLACE", "")
                    + "｜四柱：" + fourPillars;
            try {
                if (backendService != null && backendService.getObdaHandler() != null) {
                    OBDAHandler obda = backendService.getObdaHandler();
                    obda.addComponent("case_record",
                            List.of("birth_data", "birth_place", "four_pillars", "reading"),
                            List.of(cb.get("SOLAR"), cb.getOrDefault("PLACE", ""), fourPillars, reading));
                    log.info("✅ persistAndReturn | 命例已入库 case_record");
                } else {
                    log.warn("⚠️ persistAndReturn | OBDA 未就绪（第 11 步落地），落库跳过，仅返回 caseRecord");
                }
            } catch (Exception e) {
                log.error("⚠️ persistAndReturn | 落库失败：{}", e.getMessage());
            }

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("status", "COMPLETED");
            response.put("fourPillars", fourPillars);
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
    // 末尾节点：命书详解 + 白话解读（interpretResult）
    // 将节点16汇总的命书送 MaaS 大模型：先按本命逐环节详解**推断依据**并引六典籍印证（原文仅作印证），
    // 再出一段白话文转述（把前述文言解读翻成白话）。大模型未装配/不可用/失败时双键降级为空串（流程不阻断）。
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

    // ==========================================================================
    // 条文库读取（OBDA 虚拟知识图谱）：按本命匹配典籍条文（原文 + 出处）
    //   22 张条文/名录表经 sizhubazi.obda 暴露为 reading:/yongshen:/pattern: 类；
    //   库不可用（离线/未起库）时返回空集并降级，不阻断主流程（节点自带通用口径兜底）。
    // ==========================================================================

    private static final String OBDA_PREFIX =
            "PREFIX reading: <http://example.org/sizhubazi/reading/> "
          + "PREFIX yongshen: <http://example.org/sizhubazi/yongshen/> "
          + "PREFIX pattern: <http://example.org/sizhubazi/pattern/> "
          + "PREFIX core: <http://example.org/sizhubazi/core/> "
          + "PREFIX calendar: <http://example.org/sizhubazi/calendar/> "
          + "PREFIX deity: <http://example.org/sizhubazi/deity/> "
          + "PREFIX tengod: <http://example.org/sizhubazi/tengod/> "
          + "PREFIX xsd: <http://www.w3.org/2001/XMLSchema#> "
          + "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#> ";

    /** 执行条文库 SPARQL（经 OBDA/Ontop）；未就绪或异常时返回空集并降级（不阻断）。 */
    private List<Map<String, String>> queryClauses(String sparqlBody) {
        try {
            if (backendService == null || backendService.getObdaHandler() == null) {
                return Collections.emptyList();
            }
            return backendService.getObdaHandler().executeAboxQuery(OBDA_PREFIX + sparqlBody);
        } catch (Exception e) {
            log.warn("⚠️ 条文库查询降级（OBDA 不可用）：{}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /** 日时断（三命通会卷八·六X日Y时断）：按日柱 + 时柱精确匹配。 */
    private String clauseDayHour(String dayPillar, String hourPillar) {
        List<Map<String, String>> rows = queryClauses(
                "SELECT ?text ?source WHERE { ?c a reading:DayHourClause ; "
              + "reading:clauseDayPillar \"" + dayPillar + "\" ; reading:clauseHourPillar \"" + hourPillar + "\" ; "
              + "reading:readingText ?text ; reading:clauseSource ?source . }");
        if (rows.isEmpty()) {
            return null;
        }
        Map<String, String> r = rows.get(0);
        return "【日时断】" + r.get("text") + "（出处：" + r.get("source") + "）";
    }

    /** 调候用神（穷通宝鉴·造化元钥）：按日干 + 月支匹配（以条目 label「月+干+调候」定位）。 */
    private Map<String, String> clauseTiaohou(String dayGan, String monthBranch) {
        List<Map<String, String>> rows = queryClauses(
                "SELECT ?label ?p ?x ?text ?source WHERE { ?c a yongshen:TiaohouClause ; rdfs:label ?label ; "
              + "yongshen:tiaohouPrimaryUse ?p ; yongshen:tiaohouSecondaryUse ?x ; "
              + "yongshen:tiaohouText ?text ; yongshen:clauseSource ?source . }");
        String want = monthBranch + "月" + dayGan + "调候";
        for (Map<String, String> r : rows) {
            if (want.equals(r.get("label"))) {
                return r;
            }
        }
        return null;
    }

    /** 十干坐支兼得月时及行运吉凶（三命通会卷四）：按日干归组，取本命日干对应半句。 */
    private String clauseStemSeat(String dayGan) {
        String group = stemGroup(dayGan);
        for (Map<String, String> r : queryClauses(
                "SELECT ?subject ?text ?source WHERE { ?c a reading:StemBranchAuspice ; rdfs:label ?subject ; "
              + "reading:readingText ?text ; reading:readingSource ?source . }")) {
            String subj = r.get("subject");
            if (subj != null && subj.startsWith(group)) {
                return "【十干坐支】" + pickFragment(r.get("text"), dayGan) + "（出处：" + r.get("source") + "）";
            }
        }
        return null;
    }

    /** 十二月支得日干吉凶（三命通会卷四）：按生月支匹配，取本命日干对应半句。 */
    private String clauseMonthBranch(String monthBranch, String dayGan) {
        String want = monthBranch + "月得日干吉凶";
        for (Map<String, String> r : queryClauses(
                "SELECT ?subject ?text ?source WHERE { ?c a reading:MonthBranchDayStemAuspice ; rdfs:label ?subject ; "
              + "reading:readingText ?text ; reading:readingSource ?source . }")) {
            if (want.equals(r.get("subject"))) {
                return "【月支断】" + pickFragment(r.get("text"), dayGan) + "（出处：" + r.get("source") + "）";
            }
        }
        return null;
    }

    /** 六亲说要义（神峰通考·六亲说；渊海子平·六亲论）。 */
    private String clauseKinship() {
        List<Map<String, String>> rows = queryClauses(
                "SELECT ?text ?source WHERE { ?c a reading:KinshipTheory ; reading:readingText ?text ; "
              + "reading:readingSource ?source . } LIMIT 1");
        return rows.isEmpty() ? null
                : "【六亲说】" + rows.get(0).get("text") + "（出处：" + rows.get(0).get("source") + "）";
    }

    /** 女命分类名录（三命通会卷七·论女命；渊海子平卷三·女命）。 */
    private List<Map<String, String>> clauseLadyFortunes() {
        return queryClauses("SELECT ?label ?cat ?text ?source WHERE { ?c a reading:LadyFortune ; rdfs:label ?label ; "
              + "reading:readingCategory ?cat ; reading:readingText ?text ; reading:readingSource ?source . }");
    }

    /** 日柱六十甲子性质（三命通会卷一·释六十甲子性质吉凶，sexagenary_nature）。 */
    private String clauseSexagenaryNature(String dayPillar) {
        String iri = pinyinGanzhi(dayPillar);
        if (iri == null) {
            return null;
        }
        List<Map<String, String>> rows = queryClauses(
                "SELECT ?text WHERE { core:" + iri + " core:sexagenaryNatureText ?text . }");
        return rows.isEmpty() ? null : rows.get(0).get("text");
    }

    // ==========================================================================
    // 条文/名录库【定向引据】扩展（第 29 步）：把此前「经 sizhubazi.obda 暴露、但运行期
    //   从未读取」的 8 张内容表，按本命特征定位、限量引据（单条截断，避免堆砌全文）：
    //   classic_text / ziping_clause / technique_catalog / fu_clause / dts_clause /
    //   auspice_clause（五行时地·孕生男女）/ theory_clause（病药·动静·盖头）/ pattern_catalog。
    //   库未就绪（OBDA 不可用）或未命中时返回空集，降级不阻断主流程（★铁律 13/14）。
    // ==========================================================================

    /** 单条引据最大字数（超出截断：引文作印证、不堆砌原文）。 */
    private static final int CITE_MAX = 220;

    /** 截断超长引文（保留文义开头 + 省略号）。 */
    private static String clip(String s, int max) {
        if (s == null) {
            return "";
        }
        s = s.trim();
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }

    /** reading 族条目（ReadingItem/CategoryFortune/TimePlaceFortune/ProgenyPrediction/
     *  DynamicStaticTheory/HeadCoveringTheory 同构）：label + category + text + source；
     *  category 非空时按类目过滤（并在 Java 侧二次校验，规避 Ontop 字面量过滤的怪异行为）。 */
    private List<Map<String, String>> readReadingFamily(String typeIri, String category) {
        String catFilter = (category == null || category.isBlank()) ? ""
                : "reading:readingCategory \"" + category + "\" ; ";
        List<Map<String, String>> rows = queryClauses(
                "SELECT ?label ?cat ?text ?src WHERE { ?c a " + typeIri + " ; " + catFilter
              + "rdfs:label ?label ; reading:readingCategory ?cat ; reading:readingText ?text ; "
              + "reading:readingSource ?src . }");
        if (category == null || category.isBlank()) {
            return rows;
        }
        List<Map<String, String>> out = new ArrayList<>();
        for (Map<String, String> r : rows) {
            if (category.equals(r.get("cat"))) {
                out.add(r);
            }
        }
        return out;
    }

    /** pattern 族条目（pattern:Pattern 及其子类 同构）：label + name + meaning + source。 */
    private List<Map<String, String>> readPatternFamily(String typeIri) {
        return queryClauses("SELECT ?label ?name ?text ?src WHERE { ?c a " + typeIri
              + " ; rdfs:label ?label ; pattern:patternName ?name ; pattern:patternMeaning ?text ; "
              + "pattern:patternSource ?src . }");
    }

    /** 由 reading 族取「label 含关键词」的条目，限量成引据串。 */
    private List<String> pickReading(String typeIri, String category, String labelKw, String tag, int limit) {
        List<String> out = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (Map<String, String> r : readReadingFamily(typeIri, category)) {
            String label = r.get("label");
            if (label == null || (labelKw != null && !label.contains(labelKw)) || !seen.add(label)) {
                continue;
            }
            out.add("【" + tag + "·" + label + "】" + clip(r.get("text"), CITE_MAX) + "（出处：" + r.get("src") + "）");
            if (out.size() >= limit) {
                break;
            }
        }
        return out;
    }

    /** 由 pattern 族取「name 含关键词」的条目，限量成引据串。 */
    private List<String> pickPattern(String typeIri, String tag, String nameKw, int limit) {
        List<String> out = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (Map<String, String> r : readPatternFamily(typeIri)) {
            String name = r.get("name");
            if (name == null || (nameKw != null && !name.contains(nameKw)) || !seen.add(name)) {
                continue;
            }
            out.add("【" + tag + "·" + name + "】" + clip(r.get("text"), CITE_MAX) + "（出处：" + r.get("src") + "）");
            if (out.size() >= limit) {
                break;
            }
        }
        return out;
    }

    /** 由 yongshen 族（BingYao/IdleGod：label + rdfs:comment）取条目，限量成引据串。 */
    private List<String> pickYongShen(String typeIri, String labelKw, String tag, int limit) {
        List<String> out = new ArrayList<>();
        for (Map<String, String> r : queryClauses("SELECT ?label ?text WHERE { ?c a " + typeIri
              + " ; rdfs:label ?label ; rdfs:comment ?text . }")) {
            String label = r.get("label");
            if (label == null || (labelKw != null && !label.contains(labelKw))) {
                continue;
            }
            out.add("【" + tag + "】" + clip(r.get("text"), CITE_MAX));
            if (out.size() >= limit) {
                break;
            }
        }
        return out;
    }

    /** 具名格局名录引据（pattern_catalog）：按本命命中的具名格名精确取释义 + 出处。 */
    private List<String> citePatternCatalog(List<String[]> namedHits) {
        if (namedHits == null || namedHits.isEmpty()) {
            return Collections.emptyList();
        }
        List<Map<String, String>> all = readPatternFamily("pattern:Pattern");
        List<String> out = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (String[] hit : namedHits) {
            for (Map<String, String> r : all) {
                if (hit[0].equals(r.get("name")) && seen.add(hit[0])) {
                    out.add("【具名格·" + hit[0] + "】" + clip(r.get("text"), CITE_MAX) + "（出处：" + r.get("src") + "）");
                    break;
                }
            }
        }
        return out;
    }

    /** 十干/十二支体象引据（technique_catalog·十干体象/十二支体象）：按日干、日支定位。 */
    private List<String> citeTechniqueBody(String dayGan, String dayBranch) {
        List<String> out = new ArrayList<>();
        for (String tag : new String[]{"十干体象", "十二支体象"}) {
            String want = "十干体象".equals(tag) ? dayGan : dayBranch;
            for (Map<String, String> r : readReadingFamily("reading:ReadingItem", tag)) {
                String label = r.get("label");
                if (label != null && label.startsWith(want) && label.endsWith("体象")) {
                    out.add("【" + tag + "·" + label + "】" + clip(r.get("text"), CITE_MAX) + "（出处：" + r.get("src") + "）");
                    break;
                }
            }
        }
        return out;
    }

    /** 滴天髓象论引据（dts_clause）：按本命象（从/化/方局/形象）择要引（最多 2 条）。 */
    private List<String> citeDts(String pattern, String relations) {
        List<String> out = new ArrayList<>();
        if (pattern != null) {
            if (pattern.contains("从")) {
                out.addAll(pickPattern("pattern:AssimilationPattern", "滴天髓", "从象", 1));
            }
            if (pattern.contains("化")) {
                out.addAll(pickPattern("pattern:AssimilationPattern", "滴天髓", "化象", 1));
            }
            if (pattern.contains("专旺") || pattern.contains("从强") || pattern.contains("曲直")
                    || pattern.contains("炎上") || pattern.contains("稼穑") || pattern.contains("从革")
                    || pattern.contains("润下")) {
                out.addAll(pickPattern("pattern:ImagePattern", "滴天髓", "形象", 1));
            }
        }
        if (relations != null && (relations.contains("三合") || relations.contains("三会"))) {
            out.addAll(pickPattern("pattern:DirectionPattern", "滴天髓", "方局", 1));
        }
        return out.size() > 2 ? out.subList(0, 2) : out;
    }

    /** 病药/动静/盖头诸说引据（theory_clause）。 */
    private List<String> citeTheory() {
        List<String> out = new ArrayList<>();
        out.addAll(pickReading("reading:DynamicStaticTheory", null, null, "动静说", 1));
        out.addAll(pickReading("reading:HeadCoveringTheory", null, null, "盖头说", 1));
        out.addAll(pickYongShen("yongshen:BingYao", "病药说", "病药说", 1));
        return out;
    }

    /** 五行时地吉凶引据（auspice_clause·五行时地）：按日主五行定位「论X·时地吉凶」。 */
    private List<String> citeAuspiceTimePlace(String dayElemName) {
        return pickReading("reading:TimePlaceFortune", null, "论" + dayElemName + "·时地吉凶", "五行时地", 1);
    }

    /** 孕生男女引据（auspice_clause·孕生男女）。 */
    private List<String> citeProgeny() {
        return pickReading("reading:ProgenyPrediction", null, null, "孕生男女", 1);
    }

    /** 成篇赋/歌/诀引据（fu_clause）：按本命所主事项关键词匹配篇名，限量；无命中则引通篇总纲。 */
    private List<String> citeFu(Collection<String> keywords) {
        List<Map<String, String>> rows = readReadingFamily("reading:CategoryFortune", null);
        List<String> out = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (Map<String, String> r : rows) {
            String label = r.get("label");
            if (label == null || !seen.add(label)) {
                continue;
            }
            for (String kw : keywords) {
                if (label.contains(kw)) {
                    out.add("【成篇赋诀·" + label + "】" + clip(r.get("text"), CITE_MAX) + "（出处：" + r.get("src") + "）");
                    break;
                }
            }
            if (out.size() >= 2) {
                return out;
            }
        }
        for (String g : new String[]{"继善篇", "明通赋", "喜忌篇"}) {
            for (Map<String, String> r : rows) {
                if (g.equals(r.get("label"))) {
                    out.add("【成篇赋诀·" + g + "】" + clip(r.get("text"), CITE_MAX) + "（出处：" + r.get("src") + "）");
                    return out;
                }
            }
        }
        return out;
    }

    /** 子平真诠篇目引据（ziping_clause）：按篇名关键词定位，限量。 */
    private List<String> citeZiping(String subjectKw) {
        return pickReading("reading:ReadingItem", "子平真诠", subjectKw, "子平真诠", 1);
    }

    /** 典籍全文引据（classic_text）：按书名 + 篇章关键词定位，限量（长文截断为节引）。 */
    private List<String> citeClassic(String book, String chapterKw) {
        return pickReading("reading:ReadingItem", book, chapterKw, book, 1);
    }

    /** 由本命十神集取成篇赋诀的定位关键词（所主事项）。 */
    private static List<String> fuKeywords(Collection<String> gods) {
        List<String> kw = new ArrayList<>();
        if (gods.contains("正官") || gods.contains("偏官")) {
            kw.add("官");
        }
        if (gods.contains("正财") || gods.contains("偏财")) {
            kw.add("财");
        }
        if (gods.contains("正印") || gods.contains("偏印")) {
            kw.add("印");
        }
        if (gods.contains("食神") || gods.contains("伤官")) {
            kw.add("子");
        }
        if (gods.contains("比肩") || gods.contains("劫财")) {
            kw.add("比");
        }
        return kw;
    }

    /** 合并多组引据为「标题 + 逐行」的引据段；空则不产出（降级）。 */
    private static String mergeCitations(String title, List<String> cites) {
        return (cites == null || cites.isEmpty()) ? "" : "\n【" + title + "】\n" + String.join("\n", cites);
    }

    /** 日干归组（甲乙/丙丁/戊己/庚辛/壬癸），用于十干坐支条文定位。 */
    private static String stemGroup(String gan) {
        int g = indexOf(GAN, gan);
        if (g < 0) {
            return "";
        }
        return GAN[(g / 2) * 2] + GAN[(g / 2) * 2 + 1];
    }

    /** 从条文全文中取与本命日干对应的半句（以「；」分句；优先句首，次含）。 */
    private static String pickFragment(String text, String gan) {
        if (text == null || text.isBlank()) {
            return "";
        }
        for (String seg : text.split("；")) {
            if (seg.trim().startsWith(gan)) {
                return seg.trim();
            }
        }
        for (String seg : text.split("；")) {
            if (seg.contains(gan)) {
                return seg.trim();
            }
        }
        return text;
    }

    /** 六十甲子 → sexagenary_nature 主键（天干拼音 + 地支拼音，如 戊午→WuWu）。 */
    private static String pinyinGanzhi(String gz) {
        if (gz == null || gz.length() < 2) {
            return null;
        }
        String[] ganPy = {"Jia", "Yi", "Bing", "Ding", "Wu", "Ji", "Geng", "Xin", "Ren", "Gui"};
        String[] zhiPy = {"Zi", "Chou", "Yin", "Mao", "Chen", "Si", "Wu", "Wei", "Shen", "You", "Xu", "Hai"};
        int g = indexOf(GAN, gz.substring(0, 1));
        int z = indexOf(ZHI, gz.substring(1, 2));
        if (g < 0 || z < 0) {
            return null;
        }
        return ganPy[g] + zhiPy[z];
    }

    /** 五行相对日主的十神类别名（0官杀 1财 2食伤 3印 4比劫）。 */
    private static String elemRoleName(int dayElem, int elem) {
        if (elem == dayElem) {
            return "比劫";
        }
        if (elem == (dayElem + 1) % 5) {
            return "食伤";
        }
        if (elem == (dayElem + 2) % 5) {
            return "财";
        }
        if (elem == (dayElem + 3) % 5) {
            return "官杀";
        }
        return "印";
    }

    /** 五行本义（滴天髓·性情）：木主仁、火主礼、土主信、金主义、水主智。 */
    private static String elemNature(int elem) {
        switch (elem) {
            case 0: return "木主仁";
            case 1: return "火主礼";
            case 2: return "土主信";
            case 3: return "金主义";
            default: return "水主智";
        }
    }

    // --------------------------------------------------------------------------
    // 用神（月令格局法）与格局辅助（★修订：原按身强/身弱取用，与《子平真诠》「用神专求月令」不符）
    // --------------------------------------------------------------------------

    /** 月令格神十神名（月令本气）。 */
    private static String monthGodName(int[][] p) {
        String benQi = ZHI_HIDDEN[p[1][1]][0];
        return TEN_GOD[tenGodIndex(p[2][0], indexOf(GAN, benQi))];
    }

    /** 四柱（天干 + 藏干）中是否含某五行。 */
    private static boolean containsElem(int[][] p, int elem) {
        for (int[] pz : p) {
            if (GAN_ELEM[pz[0]] == elem) {
                return true;
            }
            for (String hs : ZHI_HIDDEN[pz[1]]) {
                if (GAN_ELEM[indexOf(GAN, hs)] == elem) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 用神五行（主取·月令格局法）：《子平真诠·论用神》「八字用神，专求月令」——取月令格神五行；
     * 建禄月劫（月令为日主比劫，本身不可为用）则另取四柱所现之官杀＞财＞食伤为用。
     */
    private static int yongShenElem(int[][] p) {
        int dayElem = GAN_ELEM[p[2][0]];
        int monthElem = ZHI_ELEM[p[1][1]];
        if (monthElem == dayElem) {
            for (int e : new int[]{(dayElem + 3) % 5, (dayElem + 2) % 5, (dayElem + 1) % 5}) {
                if (containsElem(p, e)) {
                    return e;
                }
            }
            return (dayElem + 3) % 5;
        }
        return monthElem;
    }

    /**
     * 身强弱结构化解析（单一真相源）。旺衰判词仅由 {@code assessDayMasterStrength} 一处生成（共 6 档），
     * 其余节点一律经此解析取结构化布尔，**严禁再以 contains(\"强\"/\"弱\"/\"身弱\") 二次反解**，
     * 以免「判词文案一改，各处判定口径即静默漂移」。语义档位：
     * <ul>
     *   <li>{@code strong}：身强 / 中和偏强（得令，或失令但得地得势）——可任财官、宜克泄耗。</li>
     *   <li>{@code mildWeak}：中和偏弱（失令、克泄耗偏多）——轻度偏弱，尚能任财官，不作贫格断。</li>
     *   <li>{@code severeWeak}：身弱（失令、无根、寡助）——重度，财旺身弱即贫格、不能任财官。</li>
     *   <li>{@code weak}：含弱意（mildWeak || severeWeak），用于「非身强」需扶抑及告警判据。</li>
     *   <li>{@code neutral}：中和（纯，不偏）——不强不弱。</li>
     * </ul>
     */
    private static final class StrengthInfo {
        final boolean strong;
        final boolean neutral;
        final boolean mildWeak;
        final boolean severeWeak;
        final boolean weak;

        private StrengthInfo(boolean strong, boolean neutral, boolean mildWeak, boolean severeWeak) {
            this.strong = strong;
            this.neutral = neutral;
            this.mildWeak = mildWeak;
            this.severeWeak = severeWeak;
            this.weak = mildWeak || severeWeak;
        }
    }

    private static StrengthInfo parseStrength(String raw) {
        String s = raw == null ? "" : raw;
        boolean strong = s.contains("强");
        boolean severeWeak = s.contains("身弱");
        boolean mildWeak = s.contains("中和偏弱");
        boolean neutral = s.contains("中和") && !strong && !mildWeak && !severeWeak;
        return new StrengthInfo(strong, neutral, mildWeak, severeWeak);
    }

    /**
     * 格局判级（patternGrade）结构化解析：把「判词」还原为结构化档位，作为下游唯一真相源。
     * <p>病根：patternGrade 是给人看的判词串（「格成（…）」「格有破（…）」等），下游若用
     * {@code contains("破")} / {@code contains("格成")} 反解语义，则判词文案一改、散落各处的判定会静默改变。
     * 此处集中一次解析，下游一律读结构化字段，文案只在 {@code handleDeterminePattern} 一处维护。</p>
     * <p>档位取值（源于 handleDeterminePattern 的 grade 赋值）：</p>
     * <ul>
     *   <li>{@code geCheng}：格成（含专旺成象）——格已成立。</li>
     *   <li>{@code geJiangCheng}：格将成（待岁运引透/引旺）。</li>
     *   <li>{@code jianLuCheng}：建禄格成立（格立而财官缺、富贵无凭）。</li>
     *   <li>{@code geBreak}：格有破（会杀为凶 / 月令逢冲格神动摇）。</li>
     *   <li>{@code geBuCheng}：格不成（阳刃无官杀制）。</li>
     *   <li>{@code geBan}：格有绊（月令逢合）。</li>
     *   <li>{@code zhuanWang}：专旺成象。</li>
     * </ul>
     */
    private static final class PatternInfo {
        final boolean geCheng;
        final boolean geJiangCheng;
        final boolean jianLuCheng;
        final boolean geBreak;
        final boolean geBuCheng;
        final boolean geBan;
        final boolean zhuanWang;

        private PatternInfo(boolean geCheng, boolean geJiangCheng, boolean jianLuCheng,
                            boolean geBreak, boolean geBuCheng, boolean geBan, boolean zhuanWang) {
            this.geCheng = geCheng;
            this.geJiangCheng = geJiangCheng;
            this.jianLuCheng = jianLuCheng;
            this.geBreak = geBreak;
            this.geBuCheng = geBuCheng;
            this.geBan = geBan;
            this.zhuanWang = zhuanWang;
        }
    }

    private static PatternInfo parsePatternGrade(final String patternGrade) {
        String g = patternGrade == null ? "" : patternGrade;
        return new PatternInfo(
                g.contains("格成"),
                g.contains("格将成"),
                g.contains("建禄格成立") || g.contains("月劫格成立") || g.contains("比劫格成立"),
                g.contains("破"),
                g.contains("格不成"),
                g.contains("格有绊"),
                g.contains("专旺"));
    }

    /** 取旺衰文案的档位标签（去掉括号内解释）：如「中和偏弱（失令…）」→「中和偏弱」。 */
    private static String strengthLabel(final String raw) {
        String s = raw == null ? "" : raw;
        int k = s.indexOf("（");
        if (k < 0) {
            k = s.indexOf("(");
        }
        return (k > 0 ? s.substring(0, k) : s).trim();
    }

    /**
     * 实效用神五行：极端燥烈/寒湿之局，调候救偏优先于月令格局法。
     * 例：火日火月、四柱无水（火炎土燥）→ 以水（调候）为用；水日水月、四柱无火（水寒）→ 以火为用。
     * 出处：穷通宝鉴·调候为急；滴天髓·寒暖燥湿之宜。
     */
    private static int effectiveYongElem(int[][] p, int dayGan, boolean strong) {
        int dayElem = GAN_ELEM[dayGan];
        int monthElem = ZHI_ELEM[p[1][1]];
        int monthYong = yongShenElem(p);
        if (dayElem == 1 && monthElem == 1 && !containsElem(p, 4)) {
            return 4;
        }
        if (dayElem == 4 && monthElem == 4 && !containsElem(p, 1)) {
            return 1;
        }
        // 身强而月令格神为「印」（生我）或「比劫」（同我）：印比过重不宜再扶，
        //   依「身强宜克泄耗」，改取食伤（我生）泄秀为先、财（我克）制印为次（《子平真诠·论用神》《滴天髓·衰旺》）。
        if (isYinBiZhongYong(p, dayGan, strong)) {
            int shiShang = (dayElem + 1) % 5;
            int cai = (dayElem + 2) % 5;
            if (containsElem(p, shiShang)) {
                return shiShang;
            }
            if (containsElem(p, cai)) {
                return cai;
            }
        }
        // 身非强（中和偏弱/身弱）而月令格神为「财」（我克、耗身）、「官杀」（克我、克身）或
        //   「食伤」（我生、泄身），皆属「耗泄日主而身不能任」：
        //   依「扶抑」当以印（生我）为先、比劫（同我、帮身）为次；格神退为格名，待岁运身强方任
        //   （《滴天髓·衰旺》《子平真诠·论用神》旺衰与格局并参；身弱食伤旺尤忌再泄，宜印滋身兼制食伤）。
        if (!strong && (monthYong == (dayElem + 1) % 5 || monthYong == (dayElem + 2) % 5
                || monthYong == (dayElem + 3) % 5)) {
            int yin = (dayElem + 4) % 5;
            if (containsElem(p, yin)) {
                return yin;
            }
            return dayElem;
        }
        return monthYong;
    }

    /** 身强且月令格神为印/比劫、须改取食伤泄秀之局（排除火炎/水寒调候救偏特例）。 */
    private static boolean isYinBiZhongYong(int[][] p, int dayGan, boolean strong) {
        int dayElem = GAN_ELEM[dayGan];
        int monthElem = ZHI_ELEM[p[1][1]];
        int monthYong = yongShenElem(p);
        if (dayElem == 1 && monthElem == 1 && !containsElem(p, 4)) {
            return false;
        }
        if (dayElem == 4 && monthElem == 4 && !containsElem(p, 1)) {
            return false;
        }
        return strong && (monthYong == dayElem || monthYong == (dayElem + 4) % 5);
    }

    private static int effectiveYongElem(int[][] p, int dayGan) {
        return effectiveYongElem(p, dayGan, false);
    }

    /** 给定用神五行下推忌神（顺用取克用神者、逆用取生用神者）。 */
    private static int jiFromYong(int[][] p, int yongElem) {
        return isNiYongOf(p, yongElem) ? (yongElem + 4) % 5 : (yongElem + 3) % 5;
    }

    /** 是否逆用（不善神：偏官/伤官）——相神取「制用神者」、忌神取「生用神者」。（《子平真诠·论用神》顺逆） */
    private static boolean isNiYong(int[][] p) {
        String g = monthGodName(p);
        return "偏官".equals(g) || "伤官".equals(g);
    }

    /**
     * 依「实际用神」判顺逆：仅当用神即月令格神（未改取印/食伤）时沿用格神顺逆（偏官/伤官为逆用）；
     * 扶抑改取印（印为吉神·顺用：喜官杀生印、忌财破印）或泄秀改取食伤时，一律按顺用
     * （相神取生用神者、忌神取克用神者）。避免「七杀格身弱改取印为用」仍误按七杀逆用而颠倒相/忌。
     */
    private static boolean isNiYongOf(int[][] p, int yongElem) {
        return yongElem == yongShenElem(p) && isNiYong(p);
    }

    /** 相神五行：顺用取生用神者、逆用取制用神者。 */
    private static int xiangShenElem(int[][] p) {
        int yong = yongShenElem(p);
        return isNiYong(p) ? (yong + 3) % 5 : (yong + 4) % 5;
    }

    /** 忌神五行：顺用取克用神者、逆用取生用神者。 */
    private static int jiShenElem(int[][] p) {
        int yong = yongShenElem(p);
        return isNiYong(p) ? (yong + 4) % 5 : (yong + 3) % 5;
    }

    /** 四柱地支中 zhi 出现次数。 */
    private static int countZhi(int[][] p, int zhi) {
        int c = 0;
        for (int[] pz : p) {
            if (pz[1] == zhi) {
                c++;
            }
        }
        return c;
    }

    /**
     * 外格/杂格侦测（从格/化气/专旺）：月令无用神或全局成象时另取
     * （《子平真诠·论外格用舍/论杂格》）。返回外格名，未成象返回空串。判定为启发式。
     */
    private static String detectExternPattern(int[][] p, int dayGan) {
        int dayElem = GAN_ELEM[dayGan];
        int[] cnt = new int[5];
        for (int i = 0; i < 4; i++) {
            cnt[GAN_ELEM[p[i][0]]]++;
            // 计「藏干」之气：从格「一气独旺」须通盘审气（含支中藏干）。ZHI_HIDDEN[支] 首项即本气，
            // 本气已由下方藏干循环计入，此处不再额外加 ZHI_ELEM[支]（避免本气双计致比较失真、操作数恒真恒假）。
            // 出处：子平真诠·论外格用舍（从格须一气独旺、不杂他神）；论旺衰重藏干（本气+中余气，各计一次）。
            for (String hs : ZHI_HIDDEN[p[i][1]]) {
                cnt[GAN_ELEM[indexOf(GAN, hs)]]++;
            }
        }

        // 专旺：须全局成象并按典籍校验破格（非仅凭月令），详 detectZhuanWang
        String zhuanWang = detectZhuanWang(p, dayGan);
        if (!zhuanWang.isEmpty()) {
            return zhuanWang;
        }
        // 从格：「日主孤立无气，无地人元，绝无一毫生扶之意」（《滴天髓·从象》）；「身无气/日主无根，舍而从之」（《子平真诠·论杂格》）。
        //   关键在「透 ≠ 能用」：所忌之印/官煞须「有力（真能生/克）」方作阻；透而虚浮（无根、不得令、不得地本气）者力弱，不作阻。
        //   故《子平真诠》王十万命（庚申 乙酉 丙申 乙丑）乙印透而申酉丑金局无木根，印虚浮不生身，仍作从财——非自相矛盾。
        int caiElem2 = (dayElem + 2) % 5, shaElem2 = (dayElem + 3) % 5, shiElem2 = (dayElem + 1) % 5, yinElem2 = (dayElem + 4) % 5;
        int cai = cnt[caiElem2];
        // 日主孤立无气：无根（藏干无比劫）、无比劫透干、印星无力能生（透而虚浮之印不作生扶）。
        boolean riZhuXu = !hasRoot(p, dayElem) && !biJieTouGan(p, dayElem) && !youLi(p, p[1][1], yinElem2);
        // 从财（《子平真诠·论杂格》「四柱皆财而身无气，舍而从之……若透印则身赖印生而不从，有官煞则亦无从财兼从煞之理」；
        //   《渊海子平·弃命从财格》「日主有根则不可弃，无根弃之则富」）：财有力且官煞无力。
        if (riZhuXu && youLi(p, p[1][1], caiElem2) && !youLi(p, p[1][1], shaElem2)) {
            return "从财格";
        }
        // 从煞（《子平真诠·论杂格》「四柱皆煞，而日主无根，舍而从之……若有伤食，则煞受制而不从，有印则印以化煞而不从」）：
        //   煞有力且伤食无力。
        if (riZhuXu && youLi(p, p[1][1], shaElem2) && !youLi(p, p[1][1], shiElem2)) {
            return "从杀格";
        }
        // 从儿（《滴天髓·顺局》「一出门来只见儿，吾儿成气构门闾；从儿不管身强弱，只要吾儿又得儿」）：
        //   「成气象」＝食伤为局中最旺（多于比劫、财、官三党）且有气；「儿又得儿」＝有财相承；且印不夺食、官不破象。
        //   「不论身强弱」指成局之后取用不问身强弱；然「满局食伤」之成象仍须食伤独盛——非同于比劫当权之身旺（如丙午甲午丙午戊戌，火炎身旺，不作从儿）。
        if (youLi(p, p[1][1], shiElem2) && cnt[shiElem2] > cnt[dayElem] && cnt[shiElem2] > cnt[caiElem2] && cnt[shiElem2] > cnt[shaElem2]
                && cai > 0 && !youLi(p, p[1][1], yinElem2) && !youLi(p, p[1][1], shaElem2)) {
            return "从儿格";
        }
        // 化气格（《三命通会·论十干化气》：日主无根无助、单逢一位五合、化神得月令，合而能化）：
        //   《滴天髓·化象》「真化」另须生当令之季且「有辰以运之」；项目金标准 V-12（庚酉 己戌 甲酉 丙巳，无辰）
        //   依三命通会「合而化神得令」取化，故不强制「辰」——此点本地 PDF 文本错乱、未能逐字核，已如实标注。
        if (riZhuXu) {
            int heGe = 0, huaIdx = -1;
            for (int j : new int[]{1, 3}) {
                int hua = ganWuHe(dayGan, p[j][0]);
                if (hua >= 0) {
                    heGe++;
                    huaIdx = hua;
                }
            }
            if (heGe == 1 && indexOf(ELEM_NAME, GAN_WUHE_HUA[huaIdx]) == ZHI_ELEM[p[1][1]]) {
                return "化气格（化" + GAN_WUHE_HUA[huaIdx] + "）";
            }
        }
        return "";
    }

    /** 两地支是否相刑（寅巳申/丑戌未三刑；子卯相刑；辰午酉亥自刑）。 */
    private static boolean xingPair(int a, int b) {
        if (a == b) {
            // 同支叠见：仅辰午酉亥为「自刑」（三命通会卷二·论三刑）；丑戌未/寅巳申同支重复不构成三刑
            return a == 4 || a == 6 || a == 9 || a == 11;
        }
        for (int[] g : SANXING) {
            if (containsInt(g, a) && containsInt(g, b)) {
                return true;
            }
        }
        return (a == 0 && b == 3) || (a == 3 && b == 0);
    }

    /** 两地支是否相破（卯午/丑辰/子酉/未戌）。三命通会卷三·总论诸神煞（破煞）。 */
    private static boolean poPair(int a, int b) {
        int[][] po = {{3, 6}, {1, 4}, {0, 9}, {7, 10}};
        for (int[] g : po) {
            if ((g[0] == a && g[1] == b) || (g[0] == b && g[1] == a)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 大运流年·逐年评析：以本命用神/忌神为准，逐运判扶抑得失与主应事项（客户裁定：逐年评吉凶 + 应事）。
     */
    /**
     * 起运依据：以「三日折一年」之法，顺行数至出生后最近之「节」、逆行数至出生前最近之「节」，
     * 取相距日时÷3 得起运岁数（三日=1岁、一日=4月、一时辰=10日）。供 fortuneAnalysis 首部自证。
     * 出处：三命通会·论起大运（阳男阴女顺、阴男阳女逆，三日为一年）。
     */
    private String buildPilotBasis(LocalDateTime birth, String dir, double startAge) {
        if (birth == null) {
            return "";
        }
        LocalDateTime target = null;
        int tIdx = -1;
        boolean forward = "顺".equals(dir);
        if (forward) {
            outer:
            for (int y = birth.getYear(); y <= birth.getYear() + 1; y++) {
                for (int idx : JIE_IDX) {
                    LocalDateTime t = solarTermDateTime(y, idx);
                    if (t.isAfter(birth)) {
                        target = t;
                        tIdx = idx;
                        break outer;
                    }
                }
            }
        } else {
            outer:
            for (int y = birth.getYear(); y >= birth.getYear() - 1; y--) {
                for (int k = JIE_IDX.length - 1; k >= 0; k--) {
                    LocalDateTime t = solarTermDateTime(y, JIE_IDX[k]);
                    if (!t.isAfter(birth)) {
                        target = t;
                        tIdx = JIE_IDX[k];
                        break outer;
                    }
                }
            }
        }
        if (target == null || tIdx < 0) {
            return "";
        }
        long mins = Math.abs(java.time.Duration.between(birth, target).toMinutes());
        long d = mins / 1440;
        long h = (mins % 1440) / 60;
        int yr = (int) Math.floor(startAge);
        int mo = (int) Math.round((startAge - yr) * 12);
        if (mo >= 12) {
            yr += mo / 12;
            mo %= 12;
        }
        String rel = forward ? "前" : "后";
        String shu = forward ? "顺数至" : "逆数至";
        return "【起运依据】生于" + TERM_NAMES[tIdx] + rel + " " + d + "日" + h + "时，三日折一年（三日=1岁、一日=4月、一时辰=10日），"
                + "依「" + (forward ? "阳男阴女顺行" : "阴男阳女逆行") + "」" + shu + TERM_NAMES[tIdx]
                + "，得 " + fmt(startAge) + " 岁（约" + yr + "岁" + (mo > 0 ? mo + "个月" : "") + "）起运（" + dir + "行）。\n";
    }

    private String buildFortuneAnalysis(int[][] p, LocalDateTime birth, double startAge, String[] fortunes,
                                        int yongElem, int jiElem, int dayElem, boolean strong, String gender, String strength) {
        StringBuilder sb = new StringBuilder();
        boolean yearYang = GAN_YY[p[0][0]] == 1;
        boolean male = !"女".equals(gender);
        String dir = ((yearYang && male) || (!yearYang && !male)) ? "顺" : "逆";
        sb.append("【大运方向】年干").append(GAN[p[0][0]]).append(yearYang ? "（阳）" : "（阴）")
                .append("·").append(male ? "男" : "女").append("→").append(dir).append("行排运。\n");
        sb.append(buildPilotBasis(birth, dir, startAge));
        sb.append("【大运逐运评析】用神").append(ELEM_NAME[yongElem]).append("（").append(elemRoleName(dayElem, yongElem))
                .append("），忌神").append(ELEM_NAME[jiElem]).append("（").append(elemRoleName(dayElem, jiElem)).append("）；");
        String sLabelF = strengthLabel(strength);
        if (strong) {
            int shiShangE = (dayElem + 1) % 5, caiE2 = (dayElem + 2) % 5, guanE2 = (dayElem + 3) % 5;
            boolean ssJi = shiShangE == jiElem, caiJi = caiE2 == jiElem, guanJi = guanE2 == jiElem;
            StringBuilder xi = new StringBuilder();
            if (!ssJi) { xi.append("食伤"); }
            if (!caiJi) { xi.append("财"); }
            if (!guanJi) { xi.append("官"); }
            StringBuilder ji = new StringBuilder();
            if (ssJi) { ji.append("食伤"); }
            if (ji.length() > 0) { ji.append("、"); }
            ji.append("比劫与印");
            if (caiJi) { ji.append("、财"); }
            if (guanJi) { ji.append("、官"); }
            // 食伤/财/官为三异元，忌神仅一元，故至少两类为喜 ⇒ xi 恒非空，xi.length()==0 恒假＝死支，简化
            sb.append(sLabelF + "，喜" + xi + "（泄耗克）"
                    + "、忌" + ji.toString() + "（助旺）。\n");
        } else {
            sb.append(sLabelF + "，喜印比（生扶）、忌财官食伤（克泄）。\n");
        }
        // 【伤官见官·通关】原局伤官透干者，岁运逢官杀须以财（金）通关：伤官生财、财生官则顺；无财则伤官见官、官星受伤
        int ssGan = 0;
        StringBuilder ssNames = new StringBuilder();
        int dayGanB = p[2][0];
        boolean ssHeGuan = false;
        for (int i = 0; i < 4; i++) {
            if (tenGodIndex(dayGanB, p[i][0]) / 2 == 1) {
                ssGan++;
                ssNames.append(GAN[p[i][0]]);
                int hp = (p[i][0] + 5) % 10;
                for (int j = 0; j < 4; j++) {
                    if (j != i && p[j][0] == hp && tenGodIndex(dayGanB, p[j][0]) / 2 == 3) {
                        ssHeGuan = true;
                    }
                }
            }
        }
        if (ssGan > 0) {
            sb.append("【伤官见官·通关】原局食伤（").append(ssNames).append("）透干，岁运逢官杀时易成「伤官见官」，")
                    .append("单见官杀未必纯吉，须有财（").append(ELEM_NAME[(dayElem + 2) % 5])
                    .append("）通关（食伤生财、财生官）方为顺；无财则官星被伤、事多反复宜谨。");
            if (ssHeGuan) {
                sb.append("又：原局伤官被官星合住（伤官被合），伤官见官之害稍减，然合而不化，仍宜以财通关为妥。");
            }
            sb.append("\n");
        }
        // 空亡：逐运支逢旬空者，本运力量打折、应事虚浮（须填实/冲实引动）
        int dIdx0 = sexagenaryIndex(p[2][0], p[2][1]);
        int xunB = floorMod(dIdx0 - (dIdx0 % 10), 60) % 12;
        int v1 = (xunB + 10) % 12, v2 = (xunB + 11) % 12;
        // 火炎土燥、调候用水之局：逐运须按结构细辨（午戌/寅午半合火、卯戌合火、亥卯半合木、子午冲、湿燥土、截脚等），
        //   不作笼统吉凶（此为本命「三午火旺、用神取水」之专项细判）。
        boolean fireExcess = (dayElem == 1 && yongElem == 4);
        for (int i = 0; i < fortunes.length; i++) {
            String gz = fortunes[i];
            if (gz == null || gz.length() < 2) {
                continue;
            }
            int g = indexOf(GAN, gz.substring(0, 1));
            int z = indexOf(ZHI, gz.substring(1, 2));
            if (g < 0 || z < 0) {
                continue;
            }
            int ge = GAN_ELEM[g];
            int ze = ZHI_ELEM[z];
            sb.append(gz).append("运（").append(fmt(startAge + i * 10)).append("-").append(fmt(startAge + i * 10 + 10)).append("岁）：")
                    .append("干").append(GAN[g]).append(ELEM_NAME[ge]).append("（").append(elemRoleName(dayElem, ge)).append("）、")
                    .append("支").append(ZHI[z]).append(ELEM_NAME[ze]).append("（").append(elemRoleName(dayElem, ze)).append("），");
            if (fireExcess) {
                String seg = fireExcessFortuneSegment(p, g, z);
                if (dayElem == 1 && ge == 4 && ssGan > 0) {
                    seg += "（此运官杀显于伤官透干之局，须财" + ELEM_NAME[(dayElem + 2) % 5] + "通关，否则伤官见官）";
                }
                if (z == v1 || z == v2) {
                    seg += "（运支" + ZHI[z] + "逢旬空，力量打折、应事虚浮——空亡仅作提示，不宜过重；须填实/冲实引动）";
                }
                sb.append(seg).append("。\n");
                continue;
            }
            int good = scoreFortuneElem(ge, yongElem, dayElem, strong) + scoreFortuneElem(ze, yongElem, dayElem, strong);
            int yinElemF = (dayElem + 4) % 5;
            boolean hasYinYun = (ge == yinElemF) || (ze == yinElemF);
            String verdict;
            if (good >= 1) {
                verdict = "吉（喜用得力）";
            } else if (good <= -1) {
                if (strong && hasYinYun) {
                    verdict = "平偏谨慎（印重助旺，未至极凶，得财制印则安）";
                } else {
                    verdict = strong ? "凶（助忌损用）" : "偏忌（财官食伤重，助忌损身）";
                }
            } else {
                verdict = strong ? "平（得失相参）"
                        : (hasYinYun ? "偏喜（印星生扶）" : "平中偏好（比劫帮身）");
            }
            boolean guanYun = (ge == (dayElem + 3) % 5) || (ze == (dayElem + 3) % 5);
            if (ssGan > 0 && guanYun && good == 0 && !hasYinYun) {
                verdict = "平中带险（伤官见官）";
            }
            boolean chongGang = pairIn(LIUCHONG, z, p[1][1]);
            boolean chongRi = pairIn(LIUCHONG, z, p[2][1]);
            if (chongGang && good >= 1) {
                verdict = "凶（虽扶而冲提纲，吉中藏凶）";
            }
            sb.append(verdict);
            if (ssGan > 0 && guanYun) {
                sb.append("；官杀临于伤官透干之局，须财（").append(ELEM_NAME[(dayElem + 2) % 5]).append("）通关，否则伤官见官、官星受伤，宜谨");
            }
            if (chongGang) {
                sb.append("；运支冲提纲（月支），格局动摇，宜谨");
            } else if (chongRi) {
                sb.append("；运支冲日支，主妻宫/事业动、居所变");
            } else if (pairIn(LIUHE, z, p[2][1])) {
                sb.append("；运支合日支，主姻缘/合作之事");
            }
            boolean caiYun = (ge == (dayElem + 2) % 5) || (ze == (dayElem + 2) % 5);
            if (caiYun) {
                boolean heMonth = pairIn(LIUHE, z, p[1][1]);
                int bh = banHeHuaElem(p, z);
                if (heMonth) {
                    sb.append("；运支").append(ZHI[z]).append("与月支").append(ZHI[p[1][1]])
                            .append("六合，财星被合、得财带劳，吉中带谨慎");
                } else if (bh >= 0) {
                    sb.append("；运支").append(ZHI[z]).append("与原局成半合").append(ELEM_NAME[bh])
                            .append("局，财星被合、得财带劳，吉中带谨慎");
                }
            }
            if (xingPair(z, p[2][1])) {
                sb.append("；运支与日支有刑，主是非/伤病宜慎");
            }
            if (z == v1 || z == v2) {
                sb.append("；运支").append(ZHI[z]).append("逢旬空，力量打折、应事虚浮——空亡仅作提示，不宜过重（须填实/冲实引动）");
            }
            sb.append("。\n");
        }
        int nowYear = LocalDate.now().getYear();
        // 流年起始年：与「岁运」段流年表（自出生年起 10 年）衔接去重，取两者较晚者，避免同一年重复出现
        int liuYearStart = Math.max(nowYear, birth.getYear() + 10);
        String liuYearNote = (liuYearStart == nowYear) ? "自当前年起" : "自" + liuYearStart + "年起列·接续岁运流年表";
        sb.append("【流年（").append(liuYearStart).append("-").append(liuYearStart + 9).append("，").append(liuYearNote).append("）】用神得扶之岁为吉、助忌之岁为凶：");
        for (int i = 0; i < 10; i++) {
            int y = liuYearStart + i;
            int g = floorMod(y - 4, 10);
            int z = floorMod(y - 4, 12);
            int ge = GAN_ELEM[g];
            int ze = ZHI_ELEM[z];
            int good = fireExcess ? fireExcessScore(p, g, z)
                    : scoreFortuneElem(ge, yongElem, dayElem, strong) + scoreFortuneElem(ze, yongElem, dayElem, strong);
            // 半合生扶修正（身弱局）：流年支与原局成半合，合化为比劫/印（生扶）则 +1、为财官食伤（克泄耗）则 -1
            if (!strong && !fireExcess) {
                int banHeHua = banHeHuaElem(p, z);
                if (banHeHua >= 0) {
                    good += (banHeHua == dayElem || generates(banHeHua, dayElem)) ? 1 : -1;
                }
            }
            String verdict = good >= 1 ? "吉" : (good <= -1 ? "凶" : "平");
            boolean guanYear = (ge == (dayElem + 3) % 5) || (ze == (dayElem + 3) % 5);
            if (!fireExcess && ssGan > 0 && guanYear && "平".equals(verdict)) {
                verdict = "平偏谨慎";
            }
            // 流年支逢旬空 / 刑冲日支 / 冲原局多支或提纲：吉者降级、不虚报吉凶
            boolean liuKong = (z == v1 || z == v2);
            int liuChongCount = 0;
            boolean liuChongMonth = false;
            for (int k = 0; k < 4; k++) {
                if (pairIn(LIUCHONG, z, p[k][1])) {
                    liuChongCount++;
                    if (k == 1) { liuChongMonth = true; }
                }
            }
            boolean liuXingChong = xingPair(z, p[2][1]) || (floorMod(z + 6, 12) == p[2][1]);
            // 重冲（流年支冲原局≥2支，或冲月提）：用神易受重创，即便天干得扶亦降为「带险/带变」
            if (liuChongCount >= 2 || liuChongMonth) {
                if ("吉".equals(verdict)) {
                    verdict = "吉中带险";
                } else if ("平".equals(verdict)) {
                    verdict = "平中带险";
                } else if ("凶".equals(verdict)) {
                    verdict = "凶中带变";
                }
            } else if (liuKong || liuXingChong) {
                if ("吉".equals(verdict)) {
                    verdict = "吉中带谨慎";
                } else if ("平".equals(verdict)) {
                    verdict = "平偏谨慎";
                } else if ("凶".equals(verdict)) {
                    verdict = "凶中带变";
                }
            }
            sb.append(y).append(ganZhiFromIndex(floorMod(y - 4, 60))).append(":").append(verdict).append(" ");
        }
        sb.append("\n（细粒度：流月按流年干五虎遁列十二月建、流日按干支纪日 JDN 锚定、流时按日干五鼠遁起时；吉凶同流年用神扶抑法）");
        return sb.toString();
    }

    /** 单干支五行对大运/流年的扶抑评分：用神+1、生用神+1、比劫/印（身强则助旺-1、身弱则帮身+1）、其余0。 */
    /** 单干支五行对大运/流年的扶抑评分（按身强身弱取喜忌，直接依日主而非仅依用神单一五行）：
     *  身强——喜食伤（泄）、财（耗）各+1；官杀（克身本喜，然官印相生/伤官见官，取0作平，稳妥）0；忌比劫、印（助旺）-1。
     *  身弱——喜印、比（生扶）+1；忌食伤、财、官杀（克泄耗）-1。 */
    private static int scoreFortuneElem(int e, int yongElem, int dayElem, boolean strong) {
        if (strong) {
            if (e == (dayElem + 1) % 5 || e == (dayElem + 2) % 5) {
                return 1;   // 食伤/财：泄耗为喜
            }
            if (e == (dayElem + 3) % 5) {
                return 0;   // 官杀：克身本喜，然官印相生、伤官见官，取平（保守）
            }
            return -1;      // 比劫/印：助旺为忌
        }
        if (e == dayElem || generates(e, dayElem)) {
            return 1;       // 身弱：印比生扶为喜
        }
        return -1;          // 身弱：食伤/财/官杀克泄耗为忌
    }

    /**
     * 流年（或大运）支与原局四支成「半合」时返回合化五行（水4/木0/火1/金3），不成则 -1。
     * 半合：三合局中流年支与局内另一支同现即半合（申子/子辰、亥卯/卯未、寅午/午戌、巳酉/酉丑）。
     */
    private static int banHeHuaElem(int[][] p, int z) {
        int[][] banhe = {
                {8, 0, 4, 4},   // 申子辰 → 化水
                {11, 3, 7, 0},  // 亥卯未 → 化木
                {2, 6, 10, 1},  // 寅午戌 → 化火
                {5, 9, 1, 3}    // 巳酉丑 → 化金
        };
        for (int[] g : banhe) {
            boolean hasZ = (z == g[0] || z == g[1] || z == g[2]);
            if (!hasZ) {
                continue;
            }
            int others = 0;
            for (int k = 0; k < 3; k++) {
                if (g[k] != z && containsZhi(p, g[k])) {
                    others++;
                }
            }
            if (others >= 1) {
                return g[3];
            }
        }
        return -1;
    }

    /**
     * 「火炎土燥、调候用水」之局的逐运评分（dayElem=火、用神=水）：
     * 天干水金为喜、木火为忌；地支水金为喜、木火为忌、湿土（辰丑）晦火生金为喜、燥土（未戌）助火为忌；
     * 并计入午戌/寅午半合火、卯戌合火、子午冲、寅午半合等结构影响。返回整数分（越正越吉）。
     */
    private static int fireExcessScore(int[][] p, int g, int z) {
        int ge = GAN_ELEM[g];
        int s = 0;
        if (ge == 4) {
            s += 1;
        } else if (ge == 3) {
            s += 1;
        } else if (ge == 0) {
            s -= 1;
        } else if (ge == 1) {
            s -= 1;
        }
        int wuN = countZhi(p, 6);
        boolean hasWu = wuN > 0, hasMao = countZhi(p, 3) > 0;
        switch (z) {
            case 0: s += 1; if (wuN >= 2) { s -= 1; } break;
            case 1: s += 1; break;
            case 2: s -= 1; if (hasWu) { s -= 1; } break;
            case 3: s -= 1; break;
            case 4: s += 1; break;
            case 5: s -= 1; break;
            case 6: s -= 1; break;
            case 7: s -= 1; break;
            case 8: s += 1; break;
            case 9: s += 1; break;
            case 10: s -= 1; if (hasWu || hasMao) { s -= 1; } break;
            case 11: s += 1; break;
            default: break;
        }
        return s;
    }

    /** 「火炎土燥、调候用水」局单运细判：返回带结构注解的吉凶结论（如「平偏谨慎（壬坐戌截脚…）」）。 */
    private static String fireExcessFortuneSegment(int[][] p, int g, int z) {
        int ge = GAN_ELEM[g], ze = ZHI_ELEM[z];
        int s = fireExcessScore(p, g, z);
        int wuN = countZhi(p, 6);
        boolean hasWu = wuN > 0, hasMao = countZhi(p, 3) > 0;
        List<String> notes = new java.util.ArrayList<>();
        if (z == 0 && wuN >= 2) {
            notes.add("子午冲（三午）、子卯刑，用神受伤");
        }
        if (z == 0 && ge == 0) {
            notes.add("甲木生火、子水又冲，用神受伤更甚，宜大谨慎");
        }
        if (z == 1 || z == 4) {
            notes.add("湿土（" + ZHI[z] + "）晦火生金");
        }
        if (z == 1) {
            notes.add("丑午相害（丑害午），湿土虽晦火而生嫌，吉中带疵");
        }
        if (z == 2 && hasWu) {
            notes.add("寅午半合火");
        }
        if (z == 7) {
            notes.add("未为燥土，助火增燥");
        }
        if (z == 10) {
            if (hasWu || hasMao) {
                notes.add("午戌半合、卯戌合火，助燥");
            }
            notes.add("戌为燥土");
        }
        if (z == 11 && hasMao) {
            notes.add("亥卯半合木生火，用神不纯");
        }
        if (ge == 4 && (z == 7 || z == 10)) {
            notes.add("壬癸坐燥土，用神截脚受制");
        }
        String label;
        if (s >= 2) {
            label = "吉";
        } else if (s == 1) {
            label = "偏吉";
        } else if (s == 0) {
            label = "平";
        } else if (s == -1) {
            label = "平偏谨慎";
        } else {
            label = "凶";
        }
        if ((ge == 3 || ze == 3) && s >= 2) {
            notes.add("财金虽透，三午火旺克金，得财亦劳");
            label = "吉中带劳";
        }
        if (z == 0 && wuN >= 2 && "平偏谨慎".equals(label)) {
            label = "平偏凶";
        }
        if (z == 1 && ge == 0 && s == 0) {
            label = "吉凶参半";
        }
        if (pairIn(LIUCHONG, z, p[2][1])) {
            notes.add("运支冲日支，婚姻/居所动");
        }
        StringBuilder r = new StringBuilder(label);
        if (!notes.isEmpty()) {
            r.append("（").append(String.join("、", notes)).append("）");
        }
        return r.toString();
    }

    /**
     * 岁运辩证（按本命推导）：以本命用神/忌神 + 具体大运干支逐运判「扶用/助忌」，并给出成格/破格之义与主应。
     * 出处：《子平真诠·论行运成格变格》；三命通会卷二·论大运/论太岁。
     */
    private static String buildFortuneSynthesis(int[][] p, double startAge, String[] fortunes,
                                                int yongElem, int jiElem, int dayElem, String patternGrade, boolean strong) {
        StringBuilder sb = new StringBuilder();
        sb.append("【岁运辩证·凭本命用神】用神").append(ELEM_NAME[yongElem]).append("（").append(elemRoleName(dayElem, yongElem))
                .append("），忌神").append(ELEM_NAME[jiElem]).append("（").append(elemRoleName(dayElem, jiElem))
                .append("）；身").append(strong ? "强" : "弱").append("，岁运先作用於用神、次及格局。\n");
        boolean fireExcess = (dayElem == 1 && yongElem == 4);
        for (int i = 0; i < fortunes.length; i++) {
            String gz = fortunes[i];
            if (gz == null || gz.length() < 2) {
                continue;
            }
            int g = indexOf(GAN, gz.substring(0, 1));
            int z = indexOf(ZHI, gz.substring(1, 2));
            if (g < 0 || z < 0) {
                continue;
            }
            int ge = GAN_ELEM[g];
            int ze = ZHI_ELEM[z];
            String action;
            if (fireExcess) {
                action = fireExcessFortuneSegment(p, g, z);
            } else {
                int good = scoreFortuneElem(ge, yongElem, dayElem, strong) + scoreFortuneElem(ze, yongElem, dayElem, strong);
                action = good >= 1 ? "扶助得力，格愈成、事顺"
                        : (good <= -1 ? "助忌损用，格受摇、宜谨" : "得失相参，平中带变");
                if (pairIn(LIUCHONG, z, p[1][1])) {
                    action = "运支冲提纲，格局动摇，宜谨（" + action + "）";
                }
            }
            sb.append(gz).append("运（").append(fmt(startAge + i * 10)).append("-").append(fmt(startAge + i * 10 + 10)).append("岁）：")
                    .append("干").append(GAN[g]).append(ELEM_NAME[ge]).append("（").append(elemRoleName(dayElem, ge)).append("）、")
                    .append("支").append(ZHI[z]).append(ELEM_NAME[ze]).append("（").append(elemRoleName(dayElem, ze)).append("）→")
                    .append(action).append("。\n");
        }
        sb.append("（本命格局基线：").append(patternGrade).append("；成格可变破格、用神可翻为忌神，逐运以生克推移。）");
        return sb.toString();
    }

    /**
     * 分类事项断（按本命推导·逐条给依据）：以本命十神/宫位/神煞为准逐项推导并说明「为何如此断」，不用通用套话。
     * 出处：渊海子平卷之二·各十神论、卷之三·六亲总论；三命通会卷九·论疾病/论羊刃/论驿马/论劫煞。
     */
    private String buildCategoryVerdicts(int[][] p, int dayGan, int dayElem, int monthBranch,
                                          Set<String> gods, String deities, String gender, boolean strong) {
        StringBuilder sb = new StringBuilder("分类事项断（按本命推导·逐条给依据）：\n");
        boolean male = !"女".equals(gender);
        boolean hasGuan = gods.contains("正官") || gods.contains("偏官");
        boolean hasCai = gods.contains("正财") || gods.contains("偏财");
        boolean hasShiShang = gods.contains("食神") || gods.contains("伤官");
        // 透干/藏支分判（避免「藏一支即称现」之误）
        boolean guanTou = false, guanCang = false, caiTou = false, caiCang = false;
        for (int i = 0; i < 4; i++) {
            String gnC = TEN_GOD[tenGodIndex(dayGan, p[i][0])];
            if (i != 2) {
                if ("正官".equals(gnC) || "偏官".equals(gnC)) {
                    guanTou = true;
                }
                if ("正财".equals(gnC) || "偏财".equals(gnC)) {
                    caiTou = true;
                }
            }
            for (String hs : ZHI_HIDDEN[p[i][1]]) {
                String hgC = TEN_GOD[tenGodIndex(dayGan, indexOf(GAN, hs))];
                if ("正官".equals(hgC) || "偏官".equals(hgC)) {
                    guanCang = true;
                }
                if ("正财".equals(hgC) || "偏财".equals(hgC)) {
                    caiCang = true;
                }
            }
        }

        // ① 事业功名（断语模板优先取本体 reading:CategoryVerdict，降级内联）
        String k1 = guanTou ? "hasGuan_true" : guanCang ? "hasGuan_cang" : "hasGuan_false";
        String f1;
        if (guanTou && !strong) {
            // 身非强而官杀透干：官杀克身为压力，须印化，不宜直取功名（《滴天髓·衰旺》身弱官杀重宜印化）
            String guanTouDesc = godTouDesc(dayGan, p, 3);
            String guanCangDesc = godCangDesc(dayGan, p, 3, true);
            // 本分支 guanTou==true ⇒ 官杀已透 ⇒ guanTouDesc 必非空、guanPart 恒非空；原 guanTouDesc!=null / guanPart.length()>0 的 false 半边恒不可达（死支），简化。
            StringBuilder guanPart = new StringBuilder();
            guanPart.append(guanTouDesc);
            if (guanCangDesc != null) {
                guanPart.append("、").append(guanCangDesc);
            }
            String touCang = guanCangDesc != null ? "透藏并见" : "透干";
            f1 = "本命官杀" + touCang + "，"
                    + guanPart + "，"
                    + "官杀克身为压力而非纯为用——宜以印化杀生身，不宜直取功名；待身转强、印透或岁运帮身方显。";
            sb.append("① 事业功名：").append(f1).append("\n");
        } else {
            if (guanTou) {
                f1 = "本命官杀透干，官杀为功名之神，故有功名之机——宜循官途、就公职或受约束之业，以官星得用为贵。";
            } else if (guanCang) {
                f1 = "本命官杀藏而不透（藏于地支未透干），功名之机须岁运引透方显——宜先以食伤生财、以财求名，待岁运引透官杀且财通关时方有公职或受约束之业之机会。";
            } else {
                f1 = "本命官杀不显，功名宜循他途，以食伤生财、以财求名。";
            }
            sb.append("① 事业功名：").append(verdictOr("事业", k1, f1)).append("\n");
        }

        // ② 财运
        String k2 = (caiTou ? "cai_true" : caiCang ? "cai_cang" : "cai_false") + "_" + (hasShiShang ? "shi_true" : "shi_false");
        String f2;
        if (caiTou) {
            String caiUseT = strong ? "身强以食伤生财、财制印为用；财务分配层面忌比劫夺财（合作/借贷宜明算）"
                    : "身弱以比劫帮身为用，惟财务分配层面忌比劫夺财（合作/借贷宜明算，二者不相悖）";
            f2 = "本命财星透干，财为养命之源，主财源可求，宜以正业求财；" + caiUseT + "；";
        } else if (caiCang) {
            String caiUseC = strong ? "身强以食伤生财、财制印为用；财务分配层面忌比劫夺财（合作/借贷宜明算）"
                    : "身弱以比劫帮身为用，惟财务分配层面忌比劫夺财（合作/借贷宜明算，二者不相悖）";
            f2 = "本命财星藏支（藏于地支未透干），财源须岁运引透方旺，宜以正业求财；" + caiUseC + "；";
        } else {
            f2 = "本命财星不显，须靠食伤生财、以技艺生财；";
        }
        f2 += (hasShiShang ? "又见食伤，食伤生财、财有源而不竭。" : "食伤不显，财源赖他途。");
        sb.append("② 财运：").append(verdictOr("财运", k2, f2)).append("\n");

        // ③ 婚姻
        boolean dayChong = false, dayHe = false, dayHai = false, dayPo = false, dayXing = false;
        int heCount = 0;
        for (int i = 0; i < 4; i++) {
            if (i == 2) {
                continue;
            }
            if (pairIn(LIUCHONG, p[2][1], p[i][1])) {
                dayChong = true;
            }
            if (pairIn(LIUHE, p[2][1], p[i][1])) {
                dayHe = true;
                heCount++;
            }
            if (pairIn(LIUHAI, p[2][1], p[i][1])) {
                dayHai = true;
            }
            if (poPair(p[2][1], p[i][1])) {
                dayPo = true;
            }
            if (xingPair(p[2][1], p[i][1])) {
                dayXing = true;
            }
        }
        boolean daySelf = countZhi(p, p[2][1]) >= 2
                && (p[2][1] == 4 || p[2][1] == 6 || p[2][1] == 9 || p[2][1] == 11);
        String k3 = dayChong ? "marriage_chong"
                : (dayHe && (dayXing || heCount >= 2)) ? "marriage_he_xing"
                : dayHe ? "marriage_he"
                : dayHai ? "marriage_hai"
                : (dayPo || daySelf || dayXing) ? "marriage_po" : "marriage_none";
        String f3 = dayChong ? "本命日支逢冲，婚宫受动，主婚姻多波折/聚少离多，宜晚婚、慎择配。"
                : (dayHe && (dayXing || heCount >= 2))
                    ? "本命日支" + ZHI[p[2][1]] + "逢合" + (heCount >= 2 ? "、多支争合（" + heCount + "支来合，一" + ZHI[p[2][1]] + "受争）" : "")
                        + (dayXing ? "、又逢刑" : "") + "——婚宫合中带" + (dayXing ? "刑" : "") + (heCount >= 2 ? "、争合" : "")
                        + "，婚缘有机会但易反复，宜晚婚、慎择配。"
                : dayHe ? "本命日支逢合，婚宫得合，主姻缘早动/易得婚配之助。"
                : dayHai ? "本命日支逢害，婚宫受穿，主夫妻易生嫌隙，宜以柔化之。"
                : (dayPo || daySelf || dayXing) ? "本命日支与别支相刑/破（或自刑），婚宫动、易生嫌隙，宜晚婚、以柔化之。"
                : "本命日支无冲合刑破，婚宫安稳，婚姻平顺。";
        // 女命：夫星（官杀天干）被他干合去、或官杀偏重——婚宫虽无明冲，亦不宜径断「婚姻平顺」（《渊海子平·六亲总论》夫星被合主缘阻）
        boolean fuXingHe = false;
        int guanCntC = 0, biCntC = 0;
        for (int i = 0; i < 4; i++) {
            int catI = tenGodIndex(dayGan, p[i][0]) / 2;
            if (catI == 3) {
                guanCntC++;
                if (i != 2) {
                    int hePair = (p[i][0] + 5) % 10;
                    for (int j = 0; j < 4; j++) {
                        if (j != i && p[j][0] == hePair) {
                            fuXingHe = true;
                        }
                    }
                }
            }
            if (catI == 0) {
                biCntC++;
            }
            for (String hs : ZHI_HIDDEN[p[i][1]]) {
                int catJ = tenGodIndex(dayGan, indexOf(GAN, hs)) / 2;
                if (catJ == 3) {
                    guanCntC++;
                }
                if (catJ == 0) {
                    biCntC++;
                }
            }
        }
        boolean guanShaZhongC = guanCntC > biCntC;
        sb.append("③ 婚姻（").append(male ? "男命：以财为妻" : "女命：以官为夫").append("）——日支")
                .append(ZHI[p[2][1]]).append("为夫妻宫（配偶宫）；");
        if (!male && "marriage_none".equals(k3) && (fuXingHe || guanShaZhongC)) {
            String fuHe = godTouDesc(dayGan, p, 3);
            sb.append("本命日支无明冲，婚宫本气稳；但")
                    .append(fuXingHe ? "夫星" + fuHe + "、" : "") // fuXingHe⇒官杀透⇒fuHe 必非空，fuHe!=null 恒真＝死支
                    .append(guanShaZhongC ? "官杀偏重" : "")
                    .append("，婚姻有波动，宜晚婚慎择。");
        } else {
            sb.append(verdictOr("婚姻", k3, f3));
        }
        if (male && !hasCai) {
            sb.append("本命财星不显、妻星未现，婚缘宜晚、须岁运引动，不可径断克妻。");
        } else if (!male && !hasGuan) {
            sb.append("本命官星不显、夫星未现，婚缘宜晚、须岁运引动，不可径断克夫。");
        } else {
            sb.append("结合配偶宫十神与刑冲合害取断。");
        }
        sb.append("\n");

        // ④ 寿夭
        String k4 = deities.contains("羊刃") ? "shou_yangren" : "shou_none";
        String f4 = deities.contains("羊刃") ? "本命见羊刃，性刚易折，宜戒急用忍、慎防血光。" : "神煞无羊刃之虞，寿元根基尚稳。";
        sb.append("④ 寿夭：日主").append(GAN[dayGan]).append(ELEM_NAME[dayElem]).append("之根基，看月令得气与通根；")
                .append(verdictOr("寿夭", k4, f4))
                .append("（忌刑冲羊刃、岁运并临）");
        if (dayElem == 1 && countZhi(p, 6) >= 2) {
            sb.append("。养生提示：本命" + (countZhi(p, 6) >= 3 ? "三" : "两") + "午自刑、火炎土燥，宜留意心血管、眼目、肝火之疾，忌熬夜与辛辣燥烈，宜静养调息");
        }
        sb.append("。\n");

        // ⑤ 飘荡
        String k5 = deities.contains("驿马") ? "piao_yima" : "piao_no_yima";
        String f5 = deities.contains("驿马") ? "本命见驿马，主动荡迁远、宜外出求财或异地发展。" : "本命不见驿马，居处安稳、动静有常。";
        sb.append("⑤ 飘荡：看驿马、劫煞——").append(verdictOr("飘荡", k5, f5));
        if (deities.contains("劫煞")) {
            sb.append(verdictOr("飘荡", "piao_jiesha", "又见劫煞，防财物耗散与外来之扰。"));
        }
        return sb.toString();
    }

    /** 藏干十神串（如「丁(正官)己(正印)」），供六亲宫位事实陈述。 */
    private static String hiddenGodStr(int dayGan, int zhi) {
        StringBuilder s = new StringBuilder();
        for (String hs : ZHI_HIDDEN[zhi]) {
            s.append(hs).append("(").append(TEN_GOD[tenGodIndex(dayGan, indexOf(GAN, hs))]).append(")");
        }
        return s.toString();
    }

    /** 某地支藏干中是否含指定类别十神（0比劫 1食伤 2财 3官杀 4印）。 */
    private static boolean hiddenHasCat(int dayGan, int zhi, int cat) {
        for (String hs : ZHI_HIDDEN[zhi]) {
            if (tenGodIndex(dayGan, indexOf(GAN, hs)) / 2 == cat) {
                return true;
            }
        }
        return false;
    }

    /** 某地支藏干中是否含指定具体十神（索引见 TEN_GOD）。 */
    private static boolean hiddenHasGod(int dayGan, int zhi, int godIdx) {
        for (String hs : ZHI_HIDDEN[zhi]) {
            if (tenGodIndex(dayGan, indexOf(GAN, hs)) == godIdx) {
                return true;
            }
        }
        return false;
    }

    /** 某类十神（0比劫 1食伤 2财 3官杀 4印）在透干中的描述：优先返回被合者「X透被Y合」，否则首个「X透」；无透干返回 null。 */
    private static String godTouDesc(int dayGan, int[][] p, int cat) {
        String firstTou = null;
        for (int i = 0; i < 4; i++) {
            if (tenGodIndex(dayGan, p[i][0]) / 2 != cat) {
                continue;
            }
            int hePair = (p[i][0] + 5) % 10;
            for (int j = 0; j < 4; j++) {
                if (j != i && p[j][0] == hePair) {
                    return GAN[p[i][0]] + "透被" + GAN[hePair] + "合";
                }
            }
            if (firstTou == null) {
                firstTou = GAN[p[i][0]] + "透";
            }
        }
        return firstTou;
    }

    /** 某类十神（同上）在藏干中的首个描述「X藏支」；excludeTou 为真时略过已透干者；无则返回 null。 */
    private static String godCangDesc(int dayGan, int[][] p, int cat, boolean excludeTou) {
        for (int i = 0; i < 4; i++) {
            for (String hs : ZHI_HIDDEN[p[i][1]]) {
                if (tenGodIndex(dayGan, indexOf(GAN, hs)) / 2 != cat) {
                    continue;
                }
                if (excludeTou) {
                    boolean tou = false;
                    for (int k = 0; k < 4; k++) {
                        if (GAN[p[k][0]].equals(hs)) {
                            tou = true;
                        }
                    }
                    if (tou) {
                        continue;
                    }
                }
                return hs + "藏" + ZHI[p[i][1]];
            }
        }
        return null;
    }

    /**
     * 六亲吉凶（按宫位·逐位推导）：年柱主祖上·父母、月柱主父母·兄弟、日支主配偶、时柱主子息。
     * 以该宫干支所含十神定六亲角色（正印母、偏财父、正财妻、比肩兄弟、七杀子、食伤女/孙），
     * 再按《渊海子平·六亲总论》「六亲受克」法则（财克印伤母、比劫夺财克妻损父、官杀重难为兄弟、
     * 食伤重难为子息）断其旺衰吉凶；日支复看冲合害，时柱复看子星得位。逐位给「结论 + 依据」，均据本命实况推导。
     */
    private String buildKinshipVerdicts(int[][] p, int dayGan, int dayElem, int yongElem, int jiElem, String gender) {
        boolean male = !"女".equals(gender);
        StringBuilder sb = new StringBuilder("六亲吉凶（按宫位·逐位推导，以日主为「我」；本命按"
                + (male ? "男" : "女") + "命论：男以财为妻、官杀为子，女以官为夫、食伤为子）：\n");

        // 十神五类计数（天干 + 藏干）：0比劫 1食伤 2财 3官杀 4印
        int[] cnt = new int[5];
        for (int i = 0; i < 4; i++) {
            cnt[tenGodIndex(dayGan, p[i][0]) / 2]++;
            for (String hs : ZHI_HIDDEN[p[i][1]]) {
                cnt[tenGodIndex(dayGan, indexOf(GAN, hs)) / 2]++;
            }
        }
        boolean caiKeYin = cnt[2] > cnt[4];        // 财旺克印：损母/祖荫
        boolean biJieDuo = cnt[0] > cnt[2];        // 比劫夺财：克妻损父
        boolean guanShaZhong = cnt[3] > cnt[0];    // 官杀重：难为兄弟
        boolean shiShangZhong = cnt[1] > cnt[3];   // 食伤重：难为子息
        // 透干分判：财/比劫是否透于天干（仅藏支不作「财星可见」「比劫夺财」之实据）
        boolean caiTouK = false, biJieTouK = false;
        for (int i = 0; i < 4; i++) {
            if (i == 2) {
                continue;
            }
            int gk = tenGodIndex(dayGan, p[i][0]);
            if (gk / 2 == 2) {
                caiTouK = true;
            }
            if (gk / 2 == 0) {
                biJieTouK = true;
            }
        }

        // ① 年柱 —— 祖上·父母宫
        {
            int yg = tenGodIndex(dayGan, p[0][0]);
            boolean yinInYear = yg / 2 == 4 || hiddenHasCat(dayGan, p[0][1], 4);
            boolean pianCaiInYear = yg == 4 || hiddenHasGod(dayGan, p[0][1], 4);
            StringBuilder v = new StringBuilder("① 年柱（祖上·父母宫）" + ganZhi(p[0]) + "：干" + GAN[p[0][0]]
                    + "为" + TEN_GOD[yg] + "，支" + ZHI[p[0][1]] + "藏" + hiddenGodStr(dayGan, p[0][1]) + "。断：");
            if (yinInYear && !caiKeYin) {
                v.append("印临祖位而财不伤印，主祖上/母有庇荫、家世可依");
            } else if (yinInYear) {
                v.append("印临祖位然本命财旺克印，祖荫/母缘先得后损，宜自立");
            } else if (caiKeYin) {
                v.append("祖宫乏印而财旺克印，母/祖之荫薄，宜白手兴家");
            } else {
                v.append("祖宫十神平常，祖业平顺、无大起落");
            }
            if (pianCaiInYear) {
                v.append("；偏财（父星）临祖位，得父荫之助");
            }
            if (biJieDuo && cnt[2] > 0) {
                v.append(biJieTouK ? "；本命比劫透干夺财，兄弟分夺祖业、父荫渐薄"
                                   : "；本命比劫藏而不透、夺财之力有限，祖业各自营谋、父荫尚可");
            } else if (biJieDuo) {
                v.append("；比劫旺而财星不显，兄弟各自营谋、祖业平常，无可分夺");
            }
            v.append("。依据：正印为母、偏财为父（六亲总论），财能克印则损母/祖，比劫夺财则分父业。\n");
            sb.append(v);
        }

        // ② 月柱 —— 父母·兄弟门户宫
        {
            int mg = tenGodIndex(dayGan, p[1][0]);
            boolean hasBiJie = mg / 2 == 0 || hiddenHasCat(dayGan, p[1][1], 0);
            boolean biJieTouYue = mg / 2 == 0;
            String biJieYueWord = biJieTouYue ? "比劫现于月门" : "比劫藏于月门（藏支不透）";
            StringBuilder v = new StringBuilder("② 月柱（父母·兄弟门户宫）" + ganZhi(p[1]) + "：干" + GAN[p[1][0]]
                    + "为" + TEN_GOD[mg] + "，支" + ZHI[p[1][1]] + "藏" + hiddenGodStr(dayGan, p[1][1]) + "。断：");
            if (guanShaZhong) {
                v.append("官杀偏重，比劫帮身有限，兄弟各有压力、助力平常，凡事宜自立");
            } else if (hasBiJie && cnt[2] > 0) {
                v.append(caiTouK ? biJieYueWord + "且本命财星透干，兄弟有助亦主分财争产，宜合伙有度"
                                 : biJieYueWord + "且本命财星藏支（不透），兄弟有助、分财之力有限，宜合伙有度");
            } else if (hasBiJie) {
                v.append(biJieYueWord + "，兄弟有助；然本命财星不显，无多财可分，宜合力营谋、共谋生财");
            } else {
                v.append("月门十神和顺，父母兄弟之缘平妥");
            }
            if (hiddenHasCat(dayGan, p[1][1], 4) || mg / 2 == 4) {
                v.append("；月藏印，母星得位，得母/长辈之力");
            }
            v.append("。依据：月为父母伯叔兄弟门户（六亲总论），比肩为兄弟、官杀重则难为兄弟。\n");
            sb.append(v);
        }

        // ③ 日支 —— 配偶宫（男看财为妻、女看官为夫）
        {
            boolean spouseStar = male ? hiddenHasCat(dayGan, p[2][1], 2) : hiddenHasCat(dayGan, p[2][1], 3);
            String spouseName = male ? "妻星（财）" : "夫星（官）";
            int spouseElem = male ? (dayElem + 2) % 5 : (dayElem + 3) % 5;
            boolean caiTouGan = false;
            for (int i = 0; i < 4; i++) {
                if (tenGodIndex(dayGan, p[i][0]) / 2 == 2) {
                    caiTouGan = true;
                }
            }
            boolean dayChong = false, dayHe = false, dayHai = false, dayPo = false, dayXing = false;
            int heCount = 0;
            for (int i = 0; i < 4; i++) {
                if (i == 2) {
                    continue;
                }
                dayChong |= pairIn(LIUCHONG, p[2][1], p[i][1]);
                if (pairIn(LIUHE, p[2][1], p[i][1])) {
                    dayHe = true;
                    heCount++;
                }
                dayHai |= pairIn(LIUHAI, p[2][1], p[i][1]);
                dayPo |= poPair(p[2][1], p[i][1]);
                dayXing |= xingPair(p[2][1], p[i][1]);
            }
            boolean daySelf = countZhi(p, p[2][1]) >= 2
                    && (p[2][1] == 4 || p[2][1] == 6 || p[2][1] == 9 || p[2][1] == 11);
            StringBuilder v = new StringBuilder("③ 日支（配偶宫）" + ZHI[p[2][1]] + "藏" + hiddenGodStr(dayGan, p[2][1]) + "。断：");
            if (spouseStar) {
                v.append("配偶宫坐").append(spouseName).append("，主得配偶之助、婚姻有根");
            } else {
                String fuHe = male ? null : godTouDesc(dayGan, p, 3);
                boolean noMove = !(dayChong || dayHe || dayHai || dayPo || dayXing || daySelf);
                if (fuHe != null && fuHe.contains("合")) {
                    v.append("配偶宫不坐").append(spouseName)
                            .append("，夫星").append(fuHe)
                            .append("，婚缘须岁运解合/引动");
                    if (noMove) {
                        v.append("；日支无明冲，婚宫本气尚稳，但夫星被绊，婚姻有波动");
                    }
                } else {
                    v.append("配偶宫不坐").append(spouseName).append("，婚姻之助须待岁运逢").append(spouseName);
                }
            }
            if (spouseElem == yongElem) {
                v.append("；").append(spouseName).append("为用神，得内助之力、因").append(male ? "妻" : "夫").append("而进");
            } else if (spouseElem == jiElem) {
                v.append("；").append(spouseName).append("落忌神，缘分多阻、易因配偶受累");
            }
            if (dayChong) {
                v.append("；日支逢冲，婚宫动荡，主聚少离多、宜晚婚慎择");
            } else if (dayHe && (dayXing || heCount >= 2)) {
                v.append("；日支逢合" + (heCount >= 2 ? "而多支争合（" + heCount + "支来合）" : "") + (dayXing ? "、又逢刑" : "")
                        + "，婚宫合中带" + (dayXing ? "刑" : "") + (heCount >= 2 ? "、争合" : "") + "、易反复，宜晚婚慎择");
            } else if (dayHe) {
                v.append("；日支逢合，婚宫得合，姻缘易成、得配偶之助");
            } else if (dayHai) {
                v.append("；日支逢害，夫妻易生嫌隙，宜以柔化之");
            } else if (dayPo || daySelf || dayXing) {
                v.append("；日支与别支相刑/破（或自刑），婚宫动、易生嫌隙，宜晚婚、以柔化之");
            }
            if (male && biJieDuo && caiTouGan) {
                v.append("；本命比劫夺财，宜防妻缘争竞、注意经营");
            } else if (male && biJieDuo) {
                v.append("；本命比劫偏重、财星藏支不透，宜防妻缘争竞、注意经营");
            }
            v.append("。依据：").append(male
                    ? "男以正财为妻、日支为妻宫，比劫夺财则妻缘受制"
                    : "女以正官为夫、日支为夫宫本位，官星得用则夫得力").append("（六亲总论）。\n");
            sb.append(v);
        }

        // ④ 时柱 —— 子息宫（男看官杀为子、女看食伤为子）
        {
            int hg = tenGodIndex(dayGan, p[3][0]);
            boolean ziStar = male ? (hg / 2 == 3 || hiddenHasCat(dayGan, p[3][1], 3))
                                  : (hg / 2 == 1 || hiddenHasCat(dayGan, p[3][1], 1));
            String ziName = male ? "子星（官杀）" : "子星（食伤）";
            // 时支与其他三柱的关系须分列：冲 / 自刑（如午午） / 他刑（三刑、子卯） / 破（如卯午），不可笼统作「刑冲」
            boolean hourChongReal = false, hourSelfXing = false, hourXingOther = false, hourPo = false, hourFuYin = false;
            String hourPoDetail = "";
            String hourFuYinDetail = "";
            java.util.List<String> fuYinSources = new java.util.ArrayList<>();
            String[] posName = {"年", "月", "日"};
            for (int i = 0; i < 3; i++) {
                if (pairIn(LIUCHONG, p[3][1], p[i][1])) {
                    hourChongReal = true;
                }
                if (p[3][1] == p[i][1]) {
                    if (xingPair(p[3][1], p[i][1])) {
                        hourSelfXing = true;   // 辰午酉亥自刑
                    } else {
                        // 同支叠见（非辰午酉亥）不构成自刑，应为伏吟；墓库支（辰戌丑未）重见作「墓库重见」
                        hourFuYin = true;
                        fuYinSources.add(posName[i] + "支");
                        if (hourFuYinDetail.isEmpty()) {
                            boolean muKu = p[3][1] == 4 || p[3][1] == 10 || p[3][1] == 1 || p[3][1] == 7;
                            hourFuYinDetail = ZHI[p[3][1]] + ZHI[p[3][1]] + (muKu ? "墓库重见" : "伏吟");
                        }
                    }
                } else if (xingPair(p[3][1], p[i][1])) {
                    hourXingOther = true;
                }
                if (poPair(p[3][1], p[i][1])) {
                    hourPo = true;
                    if (hourPoDetail.isEmpty()) {
                        hourPoDetail = ZHI[p[i][1]] + ZHI[p[3][1]] + "破";
                    }
                }
            }
            StringBuilder v = new StringBuilder("④ 时柱（子息宫）" + ganZhi(p[3]) + "：干" + GAN[p[3][0]]
                    + "为" + TEN_GOD[hg] + "，支" + ZHI[p[3][1]] + "藏" + hiddenGodStr(dayGan, p[3][1]) + "。断：");
            if (ziStar) {
                v.append("时坐").append(ziName).append("，子息宫有子星，主有子、子女可期");
            } else {
                v.append("时座不坐").append(ziName).append("，子息缘待岁运引动");
            }
            if (male && shiShangZhong) {
                v.append("；然本命食伤偏重，食伤克官杀（男命官杀为子星），子星受制，主子女略迟或偏少（非谓无子）");
            }
            if (!male && cnt[4] > cnt[1]) {
                boolean yinTou = false;
                for (int i = 0; i < 4; i++) {
                    if (tenGodIndex(dayGan, p[i][0]) / 2 == 4) {
                        yinTou = true;
                    }
                }
                if (yinTou) {
                    v.append("；印透克食伤，子星（食伤）受制，子女偏迟偏少（非谓无子）");
                } else {
                    String ssHe = godTouDesc(dayGan, p, 1);
                    v.append("；印星藏支、不透");
                    if (ssHe != null) {
                        v.append("，食伤").append(ssHe);
                    }
                    v.append("，子星（食伤）受制，子女偏迟偏少（非谓无子）");
                }
            }
            java.util.List<String> hourMoves = new java.util.ArrayList<>();
            if (hourChongReal) {
                hourMoves.add("时支逢冲");
            }
            if (hourSelfXing) {
                hourMoves.add("时支逢自刑（" + ZHI[p[3][1]] + ZHI[p[3][1]] + "）");
            }
            if (hourFuYin) {
                hourMoves.add("时支与" + String.join("、", fuYinSources) + hourFuYinDetail);
            }
            if (hourXingOther) {
                hourMoves.add("时支逢刑");
            }
            if (hourPo) {
                hourMoves.add("时支逢" + hourPoDetail);
            }
            if (!hourMoves.isEmpty()) {
                v.append("；").append(String.join("、", hourMoves)).append("，晚年子息宫动，子女缘分多变动");
            }
            v.append("。依据：").append(male
                    ? "男取官杀（克我）为子；食伤过旺则克官杀、子星受制，主子息偏迟偏少（非谓无子）（论子息）。"
                    : "女取食伤（我生）为子；印重（透干或藏支有根）克食伤则子星受制，主子息偏迟偏少（非谓无子）（论子息）。").append("\n");
            sb.append(v);
        }

        sb.append("总断：六亲各宫有得有失，须以该宫之星得地/受克与岁运引动合看，不可执一宫定终身。");
        return sb.toString();
    }

    /** 取本体分类断语模板（reading:CategoryVerdict：category×conditionKey）；未命中返回 null。 */
    private String clauseCategoryVerdict(String category, String key) {
        List<Map<String, String>> rows = queryClauses(
                "SELECT ?text WHERE { ?c a reading:CategoryVerdict ; reading:readingCategory \"" + category
              + "\" ; reading:conditionKey \"" + key + "\" ; reading:readingText ?text . } LIMIT 1");
        return rows.isEmpty() ? null : rows.get(0).get("text");
    }

    /** 模板取文，未命中/空则用内联兜底；取到的文本去除书名/出处标注（只留结论）。 */
    private String verdictOr(String category, String key, String fallback) {
        String t = clauseCategoryVerdict(category, key);
        String raw = (t == null || t.isBlank()) ? fallback : t;
        return stripCitations(raw);
    }

    /** 去除文本中的典籍书名/出处标注，只保留结论性描述（模板库原文含《…》出处时使用）。 */
    private static String stripCitations(String s) {
        if (s == null) {
            return null;
        }
        String t = s.replaceAll("（[^（）]*《[^（）]*》[^（）]*）", "");
        t = t.replaceAll("【[^】]*出处[^】]*】", "");
        t = t.replaceAll("《[^》]*》", "");
        return t;
    }

    /**
     * 具名格局命中判定（三命通会卷五/卷六等）：仅返回**本命确已命中构成条件**的具名格 {name, meaning, source}，
     * 供节点8在「原文仅作印证」之例下引用（避免无关原文堆砌）。★铁律8/9/10：各条构成条件均出自对应篇目；
     * 条件不可客观判定者不列入（不臆断）。
     */
    /**
     * 成败救应侦测（《子平真诠·论用神成败救应》；《渊海子平·论格局》）：以月令本气所定格（普通正格）为体，
     * 察四柱天干透出之忌神是否克伤格神——命中则返回 {grade, rescue}，否则返回 null（走原「格神透干/月令冲合」判定）。
     * <p>覆盖：正官格伤官见官（印制伤救）、七杀格杀旺无制（食制/印化救）、财格比劫夺财（官杀制劫/食伤化劫救）、
     * 印格贪财坏印（比劫制财救）、食神格枭神夺食（财制枭救）、伤官格伤官见官（印制伤救）。
     */
    private static String[] detectChengBai(String tgName, int dayGan, int[][] p, boolean strong) {
        java.util.Set<String> tou = new java.util.HashSet<>();
        for (int i = 0; i < 4; i++) {
            if (i == 2) {
                continue;
            }
            tou.add(TEN_GOD[tenGodIndex(dayGan, p[i][0])]);
        }
        boolean yin = tou.contains("正印") || tou.contains("偏印");
        boolean shiShang = tou.contains("食神") || tou.contains("伤官");
        boolean shangGuan = tou.contains("伤官");
        boolean guanSha = tou.contains("正官") || tou.contains("偏官");
        boolean zhengGuan = tou.contains("正官");
        boolean bijie = tou.contains("比肩") || tou.contains("劫财");
        boolean cai = tou.contains("正财") || tou.contains("偏财");
        boolean pianYin = tou.contains("偏印");
        switch (tgName) {
            case "正官":
                if (shangGuan) {
                    return yin
                            ? new String[]{"格有破（伤官见官·幸印透制伤护官可解）",
                                "结论：正官而伤官见官，本为破格，赖印绶透干制伤护官，破而可救，仍宜察印之强弱。"}
                            : new String[]{"格有破（伤官见官·待印绶制伤护官）",
                                "结论：正官而伤官见官（伤官克官），格破——须印绶制伤护官，忌再见财（财生伤）与伤官旺。"};
                }
                return null;
            case "偏官":
                if (!shiShang && !yin) {
                    return cai
                            ? new String[]{"格有破（杀旺逢财滋·无制无化）",
                                "结论：七杀而食伤不透、印绶不透，反透财滋杀，杀势猖獗，格破——须食伤制杀或印绶化杀。"}
                            : new String[]{"格有破（杀旺无制·待食制或印化）",
                                "结论：七杀而食伤不透、印绶不透，杀旺无制，格有破——须食伤制杀或印绶化杀方成。"};
                }
                return null;
            case "正财":
            case "偏财":
                // 比劫夺财：唯身强（身能任财）方忌比劫分夺；身弱反喜比劫帮身任财，不作夺财论（《子平真诠·论用神》财格喜忌）。
                if (bijie && strong) {
                    boolean jiu = guanSha || shiShang;
                    return jiu
                            ? new String[]{"格有破（比劫夺财·有官杀制劫或食伤化劫可解）",
                                "结论：财格而比劫透干夺财，赖官杀制劫或食伤化劫生财，破而可救。"}
                            : new String[]{"格有破（比劫夺财·待官杀制劫或食伤化劫）",
                                "结论：财格而比劫透干夺财，财被分夺，格破——须官杀制比劫或食伤化劫生财。"};
                }
                return null;
            case "正印":
            case "偏印":
                if (cai) {
                    boolean jiu = bijie;
                    return jiu
                            ? new String[]{"格有破（贪财坏印·得比劫制财护印可解）",
                                "结论：印格而财星透干坏印，赖比劫透干制财护印，破而可救。"}
                            : new String[]{"格有破（贪财坏印·待比劫制财护印）",
                                "结论：印格而财透坏印（贪财坏印），印根受伤，格破——须比劫制财护印。"};
                }
                return null;
            case "食神":
                if (pianYin) {
                    boolean jiu = cai;
                    return jiu
                            ? new String[]{"格有破（枭神夺食·得财制枭可解）",
                                "结论：食神格而偏印（枭）透干夺食，赖财星制枭护食，破而可救。"}
                            : new String[]{"格有破（枭神夺食·待财制枭）",
                                "结论：食神格而偏印透干夺食（枭神夺食），格破——须财星制枭护食。"};
                }
                return null;
            case "伤官":
                if (zhengGuan) {
                    return yin
                            ? new String[]{"格有破（伤官见官·幸印透制伤护官可解）",
                                "结论：伤官格而见正官，赖印绶制伤护官，破而可救。"}
                            : new String[]{"格有破（伤官见官·待印制伤）",
                                "结论：伤官格而见正官（伤官见官），格破——须印绶制伤。"};
                }
                return null;
            default:
                return null;
        }
    }

    private static List<String[]> hitNamedPatterns(int[][] p, int dayGan) {
        List<String[]> hits = new ArrayList<>();
        String dayPillar = ganZhi(p[2]);
        int dayZhi = p[2][1];
        int[] zhi = {p[0][1], p[1][1], p[2][1], p[3][1]};
        String yearGanGod = TEN_GOD[tenGodIndex(dayGan, p[0][0])];
        String hourGanGod = TEN_GOD[tenGodIndex(dayGan, p[3][0])];
        boolean hasGuan = false, hasSha = false;
        boolean hasCai = false;
        int shaCount = 0;
        for (int i = 0; i < 4; i++) {
            String g = TEN_GOD[tenGodIndex(dayGan, p[i][0])];
            if ("正官".equals(g)) {
                hasGuan = true;
            }
            if ("偏官".equals(g)) {
                hasSha = true;
                shaCount++;
            }
            if ("正财".equals(g) || "偏财".equals(g)) {
                hasCai = true;
            }
        }
        boolean hasGuanHidden = false, hasShaHidden = false;
        for (int i = 0; i < 4; i++) {
            for (String hs : ZHI_HIDDEN[p[i][1]]) {
                String hg = TEN_GOD[tenGodIndex(dayGan, indexOf(GAN, hs))];
                if ("正财".equals(hg) || "偏财".equals(hg)) {
                    hasCai = true;
                }
                if ("正官".equals(hg)) {
                    hasGuanHidden = true;
                }
                if ("偏官".equals(hg)) {
                    hasShaHidden = true;
                }
            }
        }
        boolean hasGuanAny = hasGuan || hasGuanHidden;   // 四柱（干与藏）凡见官星即破归禄
        boolean hasShaAny = hasSha || hasShaHidden;      // 凡见七杀亦破归禄

        if (containsStr(new String[]{"壬辰", "庚戌", "庚辰", "戊戌"}, dayPillar)) {
            // 魁罡破格校验（三命通会卷六·○魁罡；渊海子平·魁罡诗诀）：
            //   「不见财官刑煞并」——天干透财/透官即破；「庚戌庚辰怕官显，戊戌壬辰畏财连」；
            //   「日主独逢冲克重，财官显露祸无穷」「柱有刑冲兼破害，一贫彻骨」——日支逢刑冲即破。
            boolean kuiGangCaiTou = false, kuiGangGuanTou = false;
            for (int i = 0; i < 4; i++) {
                String g = TEN_GOD[tenGodIndex(dayGan, p[i][0])];
                if ("正财".equals(g) || "偏财".equals(g)) {
                    kuiGangCaiTou = true;
                }
                if ("正官".equals(g)) {
                    kuiGangGuanTou = true;
                }
            }
            boolean kuiGangChongXing = false;
            for (int i = 0; i < 4; i++) {
                if (i == 2) {
                    continue;
                }
                if (pairIn(LIUCHONG, dayZhi, p[i][1]) || xingPair(dayZhi, p[i][1])) {
                    kuiGangChongXing = true;
                }
            }
            if (!(kuiGangGuanTou || kuiGangCaiTou || kuiGangChongXing)) {
                hits.add(new String[]{"魁罡", "壬辰/庚戌/庚辰/戊戌魁罡之格（不见财官、不犯刑冲）", "三命通会卷六·魁罡"});
            }
            // 见财官显露或日支刑冲者：魁罡破格，不作命中列出（渊海子平·魁罡诗诀「财官显露祸无穷」）
        }
        // 六乙鼠贵（三命通会卷六·六乙鼠贵：《喜忌篇》「阴木独遇子时，为六乙鼠贵之地」；
        //   诗「切嫌午字来冲破，辛酉庚申总不宜」）——乙日时逢子（丙子时）为贵，然忌「午」冲破，见午则破格。
        if (dayGan == 1 && p[3][1] == 0 && !containsZhi(p, 6)) {
            hits.add(new String[]{"六乙鼠贵", "六乙日时逢子（鼠）、无午破之贵格", "三命通会卷六·六乙鼠贵"});
        }
        if (dayGan == 7 && "戊子".equals(ganZhi(p[3])) && countZhi(p, 0) == 1) {
            // 六阴朝阳：六辛日、时逢戊子，且柱中只宜子字一位（《三命通会·六阴朝阳》引《喜忌篇》）
            hits.add(new String[]{"六阴朝阳", "六辛日时逢戊子、子字一位之格", "三命通会卷六·六阴朝阳"});
        }
        // 壬骑龙背（三命通会卷六·壬骑龙背：《喜忌篇》「阳水叠逢辰位，是壬骑龙背之乡」；「柱中须辰多
        //   方能冲起」）——壬日坐辰为体，且须「叠逢辰位」（辰多）方能暗冲戌中财官；单见一辰不作骑龙背论。
        if (dayGan == 8 && dayZhi == 4 && countZhi(p, 4) >= 2) {
            hits.add(new String[]{"壬骑龙背", "壬日坐辰、叠逢辰位（辰多）之贵格", "三命通会卷六·壬骑龙背"});
        }
        if (dayGan == 0 && containsInt(zhi, 11)) {
            hits.add(new String[]{"六甲趋乾", "六甲日见亥（乾）之格", "三命通会卷六·六甲趋乾"});
        }
        if (dayGan == 8 && containsInt(zhi, 2)) {
            hits.add(new String[]{"六壬趋艮", "六壬日见寅（艮）之格", "三命通会卷六·六壬趋艮"});
        }
        if (containsStr(new String[]{"丁酉", "丁亥", "癸巳", "癸卯"}, dayPillar)) {
            // 日贵：自坐天乙，此格只有四日（《三命通会·○日贵》「此格只有四日：丁酉、丁亥、癸巳、癸卯」）
            hits.add(new String[]{"日贵", "自坐天乙（丁酉/丁亥/癸巳/癸卯）之格", "三命通会卷六·日贵"});
        }
        if (containsStr(new String[]{"甲寅", "丙辰", "戊辰", "庚辰", "壬戌"}, dayPillar)) {
            hits.add(new String[]{"日德", "甲寅/丙辰/戊辰/庚辰/壬戌日德之格", "三命通会卷六·日德"});
        }
        // 日禄归时（三命通会卷六·日禄归时：「此格有七日：甲寅、丁午、戊巳、己午、庚申、壬亥、癸子」；
        //   乙日卯时/丙日巳时/辛日酉时三者皆不作归禄，故日干须非乙(1)/丙(2)/辛(7)。
        //   成格条件：《喜忌篇》「日禄归时没官星」——四柱须无一点官星（正官/七杀，干与藏皆忌）；
        //   归禄六忌：刑冲、作合、倒食、官星、日月天元同、岁月天元同（渊海子平·论禄）；
        //   「若月支重见，名为建禄，不富」——月支再逢禄则建禄为主、时禄仅为兼格，归禄不纯（渊海子平）。
        if ("临官".equals(stageOf(dayGan, p[3][1])) && dayGan != 1 && dayGan != 2 && dayGan != 7) {
            boolean guanFan = hasGuanAny || hasShaAny;                 // 见官煞则破
            boolean monthDouble = (p[1][1] == p[3][1]);                // 月支重见（月午时午之类）
            boolean xingChong = false;
            for (int i = 0; i < 3; i++) {
                if (pairIn(LIUCHONG, p[3][1], p[i][1]) || xingPair(p[3][1], p[i][1])) {
                    xingChong = true;
                }
            }
            if (!guanFan && monthDouble) {
                hits.add(new String[]{"日禄归时（兼格·月支重见，建禄为主、归禄不纯）",
                        "时支归禄，然月支重见（伏吟，非自刑），建禄为主格、时禄为兼格", "三命通会卷六·日禄归时；渊海子平·论禄"});
            } else if (!guanFan && !xingChong) {
                hits.add(new String[]{"日禄归时", "日禄归于时支、四柱不见官星、不犯刑冲（青云得路）", "三命通会卷六·日禄归时"});
            }
            // 见官煞或犯刑冲者：归禄破格，不作命中列出
        }
        // 天元作禄（三命通会卷五·天元作禄：「日主自作官星」「座下官星最是奇」，如庚午、庚寅、庚戌、
        //   辛巳、辛未等日）——日支藏干中有日干之官星（正官/偏官）方是；旧按「日支临官（日坐禄）」误。
        boolean dayZhiZuoGuan = false;
        for (String hs : ZHI_HIDDEN[dayZhi]) {
            String hg = TEN_GOD[tenGodIndex(dayGan, indexOf(GAN, hs))];
            if ("正官".equals(hg) || "偏官".equals(hg)) {
                dayZhiZuoGuan = true;
                break;
            }
        }
        if (dayZhiZuoGuan) {
            hits.add(new String[]{"天元作禄", "日主自坐官星（座下官星）之格", "三命通会卷五·天元作禄"});
        }
        // 天元坐煞（三命通会卷五·天元坐煞：「谓甲申、乙酉等日。如乙丑日，丑中自坐辛金为煞」）——
        //   日支藏干中有日干之七煞（偏官）方是（不限本气，故甲申、乙酉、乙丑等皆入）。
        boolean dayZhiZuoSha = false;
        for (String hs : ZHI_HIDDEN[dayZhi]) {
            if ("偏官".equals(TEN_GOD[tenGodIndex(dayGan, indexOf(GAN, hs))])) {
                dayZhiZuoSha = true;
                break;
            }
        }
        if (dayZhiZuoSha) {
            hits.add(new String[]{"天元坐煞", "日干自坐七煞之格（日支藏干见煞）", "三命通会卷五·天元坐煞"});
        }
        if ("偏官".equals(hourGanGod) && shaCount == 1) {
            hits.add(new String[]{"时上一位贵", "时柱独见七煞一位为贵", "三命通会卷五·时上一位贵"});
        }
        if ("正官".equals(yearGanGod)) {
            hits.add(new String[]{"岁德正官", "年干正官为岁德之格", "三命通会卷五·岁德正官"});
        }
        if ("正官".equals(hourGanGod)) {
            hits.add(new String[]{"时上正官", "正官居时柱之格", "三命通会卷五·时上正官"});
        }
        if ("偏官".equals(yearGanGod)) {
            hits.add(new String[]{"年上七煞", "年干七煞之格", "三命通会卷五·年上七煞"});
        }
        if (hasGuan && hasSha) {
            hits.add(new String[]{"官煞混杂", "官杀并见之混杂格", "三命通会卷五·官煞混杂"});
        }
        if (containsStr(new String[]{"壬午", "癸巳"}, dayPillar)) {
            // 财官双美：自坐财官，只壬午、癸巳二日（《三命通会·○财官双美》引《继善篇》
            //   「六壬生临午位…癸日坐向巳宫，乃是财官双美」）。
            // 破格校验：原文「若见寅卯旺则秀而不实」——见木旺（寅/卯支或甲乙透）克伤官星土，
            //   即《子平真诠·论用神成败救应》「官逢伤克…官格败也」，格破不列入。
            boolean caiGuanMuWang = containsZhi(p, 2) || containsZhi(p, 3);
            for (int i = 0; i < 4; i++) {
                if (GAN_ELEM[p[i][0]] == 0) {
                    caiGuanMuWang = true;
                    break;
                }
            }
            if (!caiGuanMuWang) {
                hits.add(new String[]{"财官双美", "自坐财官（壬午/癸巳）之格（不见木旺克官）", "三命通会卷六·财官双美"});
            }
            // 见寅卯旺/木透克官者：财官双美格破，不作命中列出（三命通会「见寅卯旺则秀而不实」）
        }
        // 专禄要制（三命通会卷五·专禄要制：「此格六庚日见巳时，庚金长生之地，内有丙戊二禄…柱要壬癸
        //   制丙」）——六庚日见巳时方是；旧按「日支临官（日坐禄）+有煞」误。
        if (dayGan == 6 && p[3][1] == 5) {
            hits.add(new String[]{"专禄要制", "六庚日见巳时、庚金长生、须制丙煞之格", "三命通会卷五·专禄要制"});
        }
        // 金神格（三命通会·论金神：金神者破败之神，要制伏、入火乡为胜）。口径取「时上金神」：
        //   时柱见乙丑/己巳/癸酉，其地支丑巳酉合「巳酉丑」金局之象。喜火炼金、忌水泛滥。
        String hourPillar = ganZhi(p[3]);
        if (containsStr(new String[]{"乙丑", "己巳", "癸酉"}, hourPillar)) {
            hits.add(new String[]{"金神", "时柱见乙丑/己巳/癸酉（巳酉丑金局），喜火制、忌水",
                    "三命通会·论金神"});
        }
        // 时墓格（三命通会·论时墓：时支为日主五行之墓库，主晚年聚财、子息）。
        //   墓库取三合局墓：木墓未、火墓戌、金墓丑、水墓辰；土从火墓（戌）。
        int tombZhi;
        int de = GAN_ELEM[dayGan];
        if (de == 0) tombZhi = 7;        // 木墓未
        else if (de == 1 || de == 2) tombZhi = 10; // 火/土墓戌
        else if (de == 3) tombZhi = 1;   // 金墓丑
        else tombZhi = 4;                // 水墓辰
        if (p[3][1] == tombZhi) {
            hits.add(new String[]{"时墓", "时支为日主五行墓库（" + ZHI[tombZhi] + "），主晚年聚财子息",
                    "三命通会·论时墓"});
        }
        // 禄马同乡（三命通会·论禄马同乡：日干之禄与驿马同居一位、且见于四柱，主富贵）。
        //   口径：禄依日干（临官之支，取 LU），马依年支三合起驿马；二者同支且命中有此支即为同乡。
        int luZhi = LU[dayGan];
        int[] groupMa = new int[12];
        for (int z : new int[]{8, 0, 4}) groupMa[z] = 2;   // 申子辰马在寅
        for (int z : new int[]{2, 6, 10}) groupMa[z] = 8;  // 寅午戌马在申
        for (int z : new int[]{5, 9, 1}) groupMa[z] = 11;  // 巳酉丑马在亥
        for (int z : new int[]{11, 3, 7}) groupMa[z] = 5;  // 亥卯未马在巳
        int maZhi = groupMa[p[0][1]];
        if (luZhi == maZhi && containsZhi(p, maZhi)) {
            hits.add(new String[]{"禄马同乡", "日干之禄与年支所起驿马同支（" + ZHI[maZhi] + "）且见于四柱",
                    "三命通会·论禄马同乡"});
        }
        // ================= 经典外格补遗（L10 扩展，按《三命通会》卷五/卷六诸格实装；
        //   逐格出处与成破条件见 target/classic/l10_classical_basis.md）=================
        boolean dayZuoCaiGuan = false;
        for (String hs : ZHI_HIDDEN[dayZhi]) {
            String hg = TEN_GOD[tenGodIndex(dayGan, indexOf(GAN, hs))];
            if ("正财".equals(hg) || "偏财".equals(hg) || "正官".equals(hg) || "偏官".equals(hg)) {
                dayZuoCaiGuan = true;
                break;
            }
        }
        // 井栏叉格（三命通会卷六·井栏叉格：庚申/庚子/庚辰三日，须申子辰水局全方为井栏叉，
        //   忌见寅午戌冲破）——庚日、申子辰三支俱全且无寅午戌。
        if (dayGan == 6 && containsZhi(p, 8) && containsZhi(p, 0) && containsZhi(p, 4)
                && !containsZhi(p, 2) && !containsZhi(p, 6) && !containsZhi(p, 10)) {
            hits.add(new String[]{"井栏叉", "庚日申子辰水局全、无寅午戌冲破之格", "三命通会卷六·井栏叉格"});
        }
        // 子遥巳格（三命通会卷六·子遥巳格：甲子日甲子时，两子遥合巳中戊丙为财官；
        //   忌午冲破、巳丑填实触破）。
        if (dayGan == 0 && "甲子".equals(dayPillar) && "甲子".equals(hourPillar)
                && !containsZhi(p, 6) && !containsZhi(p, 5) && !containsZhi(p, 1)) {
            hits.add(new String[]{"子遥巳", "甲子日甲子时、两子遥合巳中戊丙、无午巳丑破之格", "三命通会卷六·子遥巳格"});
        }
        // 丑遥巳格（三命通会卷六·丑遥巳格：辛丑/癸丑日，丑多遥合巳中丙戊为官，
        //   忌巳酉填实）。
        if (((dayGan == 7 && "辛丑".equals(dayPillar)) || (dayGan == 9 && "癸丑".equals(dayPillar)))
                && countZhi(p, 1) >= 2 && !containsZhi(p, 5) && !containsZhi(p, 9)) {
            hits.add(new String[]{"丑遥巳", "辛丑/癸丑日丑多、遥合巳中丙戊为官、无巳酉破之格", "三命通会卷六·丑遥巳格"});
        }
        // 飞天禄马格（三命通会卷六·飞天禄马：庚子/壬子日子多冲午中丁己为官印，
        //   辛亥/癸亥日亥多冲巳中丙戊为财煞；忌所冲之支填实）。
        boolean feiTian = ((dayGan == 6 && "庚子".equals(dayPillar)) || (dayGan == 8 && "壬子".equals(dayPillar)))
                && countZhi(p, 0) >= 2 && !containsZhi(p, 6);
        feiTian = feiTian
                || (((dayGan == 7 && "辛亥".equals(dayPillar)) || (dayGan == 9 && "癸亥".equals(dayPillar)))
                && countZhi(p, 11) >= 2 && !containsZhi(p, 5));
        if (feiTian) {
            hits.add(new String[]{"飞天禄马", "庚子/壬子（子多冲午）或辛亥/癸亥（亥多冲巳）、无填实之格", "三命通会卷六·飞天禄马"});
        }
        // 倒冲禄马格（三命通会卷六·倒冲禄马：丙午/戊午日午多、倒冲子中癸水为官；
        //   丁巳日巳多、倒冲亥中壬水为官；忌所冲之支填实）。
        boolean daoChong = ((dayGan == 2 && "丙午".equals(dayPillar)) || (dayGan == 4 && "戊午".equals(dayPillar)))
                && countZhi(p, 6) >= 2 && !containsZhi(p, 0);
        daoChong = daoChong
                || (dayGan == 3 && "丁巳".equals(dayPillar) && countZhi(p, 5) >= 2 && !containsZhi(p, 11));
        if (daoChong) {
            hits.add(new String[]{"倒冲禄马", "丙午/戊午（午多倒冲子癸）或丁巳（巳多倒冲亥壬）、无填实之格", "三命通会卷六·倒冲禄马"});
        }
        // 时上偏财格（三命通会卷五·○时上偏财：「天元透出为妙，支内所藏次之」；
        //   渊海子平·时上偏财「用时上天元及支内人元」）——时干透偏财为主（真格），
        //   时支藏干见偏财为次（亦入格）。两路径并存，据经典不收窄。
        boolean hourPianCaiTou = "偏财".equals(hourGanGod);
        boolean hourPianCaiCang = false;
        if (!hourPianCaiTou) {
            for (String hs : ZHI_HIDDEN[p[3][1]]) {
                if ("偏财".equals(TEN_GOD[tenGodIndex(dayGan, indexOf(GAN, hs))])) {
                    hourPianCaiCang = true;
                    break;
                }
            }
        }
        if (hourPianCaiTou || hourPianCaiCang) {
            hits.add(new String[]{"时上偏财",
                    hourPianCaiTou ? "时干透出偏财一位、不逢冲破之格（天元透出为妙）"
                            : "时支藏干见偏财一位、不逢冲破之格（支内所藏次之）",
                    "三命通会卷五·时上偏财；渊海子平·时上偏财"});
        }
        // 日刃格（三命通会卷五·论日刃：日刃只有三日——丙午、戊午、壬子，阳干自坐羊刃；
        //   喜制伏、忌刑冲）。
        if ("丙午".equals(dayPillar) || "戊午".equals(dayPillar) || "壬子".equals(dayPillar)) {
            hits.add(new String[]{"日刃", "丙午/戊午/壬子日自坐羊刃之格（喜制伏、忌刑冲）", "三命通会卷五·论日刃"});
        }
        // 拱禄拱贵格（三命通会卷六·拱禄拱贵：日时两干相同、日时支相隔一位夹拱虚神，
        //   拱日干之禄为「拱禄」、拱日干天乙贵人为「拱贵」，皆忌填实冲绊）。
        int gongDiff = floorMod(p[3][1] - dayZhi, 12);
        int gongMid = (gongDiff == 2) ? floorMod(dayZhi + 1, 12)
                : (gongDiff == 10) ? floorMod(dayZhi - 1, 12) : -1;
        if (gongMid >= 0 && p[2][0] == p[3][0] && !containsZhi(p, gongMid)) {
            if (gongMid == LU[dayGan]) {
                hits.add(new String[]{"拱禄", "日时两干同、夹拱" + ZHI[gongMid] + "（日干之禄）不填实之格", "三命通会卷六·拱禄拱贵"});
            }
            if (containsInt(TIANYI[dayGan], gongMid)) {
                hits.add(new String[]{"拱贵", "日时两干同、夹拱" + ZHI[gongMid] + "（日干天乙）不填实之格", "三命通会卷六·拱禄拱贵"});
            }
        }
        // 勾陈得位格（三命通会卷六·勾陈得位：戊己土日、坐财官为得位，财官俱见方论）——
        //   口径：戊/己日、日支藏干见财或官（坐财官）、且财与官俱见于命。
        if ((dayGan == 4 || dayGan == 5) && dayZuoCaiGuan && hasCai && (hasGuanAny || hasShaAny)) {
            hits.add(new String[]{"勾陈得位", "戊/己日坐财官、财官俱见之格", "三命通会卷六·勾陈得位"});
        }
        // 玄武当权格（三命通会卷六·玄武当权：壬癸水日、坐财官为当权，财官俱见方论）。
        if ((dayGan == 8 || dayGan == 9) && dayZuoCaiGuan && hasCai && (hasGuanAny || hasShaAny)) {
            hits.add(new String[]{"玄武当权", "壬/癸日坐财官、财官俱见之格", "三命通会卷六·玄武当权"});
        }
        // 白虎持势格（三命通会卷六·白虎持势：庚辛金日、坐财官为持势，财官俱见方论）。
        if ((dayGan == 6 || dayGan == 7) && dayZuoCaiGuan && hasCai && (hasGuanAny || hasShaAny)) {
            hits.add(new String[]{"白虎持势", "庚/辛日坐财官、财官俱见之格", "三命通会卷六·白虎持势"});
        }
        // 刑合格（三命通会卷六·刑合：癸日见寅时（甲寅时），寅刑巳、刑出巳中戊土为官、
        //   丙火为财；忌巳填实）。
        if (dayGan == 9 && p[3][1] == 2 && !containsZhi(p, 5)) {
            hits.add(new String[]{"刑合", "癸日寅时、寅刑巳出戊官丙财、巳不填实之格", "三命通会卷六·刑合"});
        }
        return hits;
    }

    private static boolean containsStr(String[] arr, String v) {
        for (String x : arr) {
            if (x.equals(v)) {
                return true;
            }
        }
        return false;
    }

    /** 组装「命书详解」用户提示：以命书为据、附关键中间变量，供大模型逐环节按本命详解依据并引六典籍印证。 */
    private String buildInterpretUserPrompt(Map<String, Object> vars, String reading) {
        StringBuilder sb = new StringBuilder();
        sb.append("【四柱命书（节点16汇总·已按本命推导的口径结论）】\n").append(reading).append('\n');
        sb.append("【关键中间变量（供逐环节解析）】\n");
        appendLine(sb, "四柱", vars.get("fourPillars"));
        appendLine(sb, "日主旺衰", vars.get("strength"));
        appendLine(sb, "格局", vars.get("patternResult"));
        appendLine(sb, "用神", vars.get("yongShen"));
        appendLine(sb, "命局层次", vars.get("chartLevel"));
        appendLine(sb, "岁运", vars.get("fortuneCycles"));
        appendLine(sb, "岁运评析", vars.get("fortuneAnalysis"));
        sb.append("\n请据此按子平命书体例作系统详解：先按本命四柱/十神/旺衰/格局/用神/岁运逐环节说明**推断依据**，"
                + "再给出综合结论；只讲结果、依据与结论，不引用典籍原文、不写书名篇目或出处。");
        return sb.toString();
    }

    /** 组装「白话解读」用户提示：以命书与已生成的命书详解为据，供大模型转写为通俗白话。 */
    private String buildInterpretPlainUserPrompt(String reading, String interpretation) {
        StringBuilder sb = new StringBuilder();
        sb.append("【四柱命书（节点16汇总）】\n").append(reading).append('\n');
        sb.append("【命书详解（古典，供你改写为白话）】\n").append(interpretation).append('\n');
        sb.append("\n请据此用通俗白话文翻译一遍：分点讲清日主强弱、格局用神、事业/财运/婚姻/健康/岁运吉凶各自的结论、"
                + "依据是什么、对命主意味着什么。只做文言到白话的翻译，遇必要术语一句话浅释即可，"
                + "不堆砌名词、不引用典籍原文、不写出处。");
        return sb.toString();
    }

    // ==========================================================================
    // 命理运算辅助（★铁律 13：历法/起运数值计算与查表外置于此，不写成 SWRL）
    // ==========================================================================

    /** 解析生辰字符串（支持「yyyy-MM-dd HH:mm」「yyyy年M月d日H时m分」等）。 */
    private static LocalDateTime parseDateTime(String s) {
        String t = s.trim()
                .replace('年', '-').replace('月', '-').replace('日', ' ')
                .replace('时', ':').replace('点', ':').replace("分", "")
                .replace('/', '-');
        t = t.replaceAll("\\s+", " ").trim();
        try {
            String[] parts = t.split("[ T]");
            String[] d = parts[0].split("-");
            int y = Integer.parseInt(d[0].trim());
            int mo = Integer.parseInt(d[1].trim());
            int da = Integer.parseInt(d[2].trim());
            int hh = 0, mi = 0;
            if (parts.length > 1 && !parts[1].isBlank()) {
                String[] hm = parts[1].split(":");
                hh = Integer.parseInt(hm[0].trim());
                if (hm.length > 1) {
                    mi = Integer.parseInt(hm[1].trim());
                }
            }
            return LocalDateTime.of(y, mo, da, hh, mi);
        } catch (Exception e) {
            throw new IllegalArgumentException("无法解析生辰：" + s);
        }
    }

    /**
     * 解析生辰变量：支持「yyyy-MM-dd HH:mm」等字符串，或结构化对象
     * {calendar, year, month, day, hour, minute}（前端 index.html 提交形态：hour 为 0–23 时、minute 为 0–59 分）。
     * <p>前端将历法置于 birth 对象内；若给定 hour（0–23）直接采用，否则回退按 {@link #ZHI_START_HOUR} 由时辰地支取起始小时。
     */
    private static LocalDateTime resolveBirthDateTime(Object birthVar) {
        if (birthVar instanceof Map<?, ?> m) {
            Integer y = toInt(m.get("year"));
            Integer mo = toInt(m.get("month"));
            Integer da = toInt(m.get("day"));
            if (y == null || mo == null || da == null) {
                throw new IllegalArgumentException("无法解析生辰（缺年/月/日）：" + m);
            }
            Integer ho = toInt(m.get("hour"));
            int hh = (ho != null) ? ho : zhiToStartHour(str(m.get("hourZhi")));
            Integer mi = toInt(m.get("minute"));
            return LocalDateTime.of(y, mo, da, hh, mi == null ? 0 : mi);
        }
        String s = str(birthVar);
        if (s == null || s.isBlank()) {
            throw new IllegalArgumentException("birth（生辰）不能为空");
        }
        return parseDateTime(s);
    }

    /** 结构化生辰对象内的历法（前端置于 birth 内）；缺省「公历」。 */
    private static String birthCalendar(Object birthVar) {
        if (birthVar instanceof Map<?, ?> m) {
            String c = str(m.get("calendar"));
            if (c != null && !c.isBlank()) {
                return c;
            }
        }
        return "公历";
    }

    /**
     * 对象生辰按与本人一致口径校正（合婚对偶路径）：农历→公历换算 + 出生地经度折算真太阳时。
     * 历法换算失败（越界/无闰月）时保留原值，与本人路径【待考】口径一致；无出生地则按平太阳时。
     */
    private LocalDateTime correctBirthDateTime(Object birthVar, String calendar, String birthPlace) {
        LocalDateTime dt = resolveBirthDateTime(birthVar);
        if ("农历".equals(calendar)) {
            LocalDateTime conv = lunarToSolar(birthVar);
            if (conv != null) {
                dt = conv;
            }
        }
        Double lon = extractLongitude(birthPlace);
        if (lon != null) {
            dt = dt.plusSeconds(Math.round(((lon - 120.0) / 15.0) * 3600.0));
        }
        return dt;
    }

    /** 从对象生辰对象内读取出生地（如 partnerBirth.birthPlace）；无则返回空串。 */
    private static String birthPlaceOf(Object birthVar) {
        if (birthVar instanceof Map<?, ?> m) {
            String p = str(m.get("birthPlace"));
            return p == null ? "" : p;
        }
        return "";
    }

    /** 时辰地支 → 起始小时（子→23 晚子；丑→1…亥→21）；无法识别时抛错（不臆造）。 */
    private static int zhiToStartHour(String zhi) {
        if (zhi != null) {
            String z = zhi.trim();
            for (int i = 0; i < ZHI.length; i++) {
                if (ZHI[i].equals(z)) {
                    return ZHI_START_HOUR[i];
                }
            }
        }
        throw new IllegalArgumentException("无法识别的时辰地支：" + zhi);
    }

    /** 宽松整数转换（Number/String）；无法转换返回 null。 */
    private static Integer toInt(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof Number n) {
            return n.intValue();
        }
        String s = String.valueOf(o).trim();
        if (s.isEmpty()) {
            return null;
        }
        try {
            return (int) Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 从出生地文本中提取经度（如「东经116.4」「116.4」）；无则返回 null（按平太阳时处理）。 */
    private static Double extractLongitude(String place) {
        if (place == null || place.isBlank()) {
            return null;
        }
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(-?\\d+(?:\\.\\d+)?)").matcher(place);
        if (m.find()) {
            try {
                double v = Double.parseDouble(m.group(1));
                if (v >= -180 && v <= 180 && !place.matches(".*\\d{4}.*")) {
                    return v;
                }
            } catch (NumberFormatException ignore) {
                // noop
            }
        }
        return null;
    }

    /**
     * 农历→公历换算（含闰月），由内置权威农历数据表 {@link #LUNAR_INFO}（1900–2100）支撑；
     * 现以同源数据表直接落地（★铁律 13 历法表外置的等价实现），取代原【待考】占位。
     * <p>支持两种输入：结构化对象 {@code {year, month, day, hour, minute, leap?}}（前端提交形态，hour 为 0–23 时、月份为农历月序、
     * {@code leap=true} 表闰月）与字符串（如「1990-05-05 12:00」，或含「闰」字标闰月）。
     * 时辰地支按 {@link #ZHI_START_HOUR} 取起始小时。
     * <p>年份超出 1900–2100、或该月并无闰月却标闰时返回 null，由调用方标【待考】并按原值登记（不臆造换算，★铁律 8/10）。
     */
    private LocalDateTime lunarToSolar(Object birthVar) {
        int ly;
        int lm;
        int ld;
        int hh = 0;
        int mi = 0;
        boolean isLeap = false;
        if (birthVar instanceof Map<?, ?> m) {
            Integer y = toInt(m.get("year"));
            Integer mo = toInt(m.get("month"));
            Integer da = toInt(m.get("day"));
            if (y == null || mo == null || da == null) {
                return null;
            }
            ly = y;
            lm = mo;
            ld = da;
            Integer ho = toInt(m.get("hour"));
            if (ho != null) {
                hh = ho;
            } else {
                String z = str(m.get("hourZhi"));
                if (z != null && !z.isBlank()) {
                    hh = zhiToStartHour(z);
                }
            }
            Integer min = toInt(m.get("minute"));
            mi = (min == null) ? 0 : min;
            isLeap = truthy(m.get("leap")) || truthy(m.get("isLeap"));
        } else {
            String s = str(birthVar);
            if (s == null || s.isBlank()) {
                return null;
            }
            boolean leapFlag = s.contains("闰");
            // 提取年月日（允许「1990-5-5 闰」/「闰1990-5-5」/「1990年5月5日」等形态）
            java.util.regex.Matcher md = java.util.regex.Pattern
                    .compile("(\\d{1,4})\\D+(\\d{1,2})\\D+(\\d{1,2})").matcher(s);
            if (!md.find()) {
                return null;
            }
            ly = Integer.parseInt(md.group(1));
            lm = Integer.parseInt(md.group(2));
            ld = Integer.parseInt(md.group(3));
            java.util.regex.Matcher hm = java.util.regex.Pattern.compile("(\\d{1,2}):(\\d{1,2})").matcher(s);
            if (hm.find()) {
                hh = Integer.parseInt(hm.group(1));
                mi = Integer.parseInt(hm.group(2));
            } else {
                hh = parseHourFromText(s);
            }
            isLeap = leapFlag;
        }
        if (ly < LUNAR_MIN_YEAR || ly > LUNAR_MAX_YEAR) {
            return null;
        }
        if (lm < 1 || lm > 12 || ld < 1 || ld > 30) {
            return null;
        }
        int leap = leapMonth(ly);
        if (isLeap && leap != lm) {
            return null; // 该年该月无闰月，不臆造
        }
        long offset = 0;
        for (int i = LUNAR_MIN_YEAR; i < ly; i++) {
            offset += lunarYearDays(i);
        }
        for (int i = 1; i < lm; i++) {
            offset += monthDays(ly, i);
            if (leap == i) {
                offset += leapDays(ly); // 闰月紧随同号平月
            }
        }
        if (isLeap) {
            offset += monthDays(ly, lm); // 闰月位于同号平月之后
        }
        offset += ld - 1;
        LocalDate solar = LocalDate.of(1900, 1, 31).plusDays(offset); // 1900 年正月初一 = 1900-01-31
        return LocalDateTime.of(solar, LocalTime.of(hh % 24, mi % 60));
    }

    /** 从字符串中提取小时（"HH:mm" / "HH时" / "N点"），缺省 0。 */
    private static int parseHourFromText(String s) {
        java.util.regex.Matcher hm = java.util.regex.Pattern.compile("(\\d{1,2}):(\\d{1,2})").matcher(s);
        if (hm.find()) {
            return Integer.parseInt(hm.group(1));
        }
        java.util.regex.Matcher hz = java.util.regex.Pattern.compile("(\\d{1,2})\\s*[时点]").matcher(s);
        if (hz.find()) {
            return Integer.parseInt(hz.group(1));
        }
        return 0;
    }

    /** 宽松真值判断（Boolean/Number/String：true/1/是/闰）。 */
    private static boolean truthy(Object o) {
        if (o == null) {
            return false;
        }
        if (o instanceof Boolean b) {
            return b;
        }
        if (o instanceof Number n) {
            return n.intValue() != 0;
        }
        String s = String.valueOf(o).trim();
        return "true".equalsIgnoreCase(s) || "1".equals(s) || "是".equals(s) || "闰".equals(s);
    }

    /** 农历年闰月月份（0 = 无闰）。 */
    private static int leapMonth(int y) {
        return (int) (LUNAR_INFO[y - LUNAR_MIN_YEAR] & 0xf);
    }

    /** 农历闰月天数（无闰返回 0）。 */
    private static int leapDays(int y) {
        if (leapMonth(y) == 0) {
            return 0;
        }
        return ((LUNAR_INFO[y - LUNAR_MIN_YEAR] & 0x10000) != 0) ? 30 : 29;
    }

    /** 农历某月天数（29/30）。 */
    private static int monthDays(int y, int m) {
        return ((LUNAR_INFO[y - LUNAR_MIN_YEAR] & (0x10000 >> m)) != 0) ? 30 : 29;
    }

    /** 农历某年总天数（12 月 + 闰月）。 */
    private static int lunarYearDays(int y) {
        int sum = 348;
        for (int i = 0x8000; i > 0x8; i >>= 1) {
            if ((LUNAR_INFO[y - LUNAR_MIN_YEAR] & i) != 0) {
                sum++;
            }
        }
        return sum + leapDays(y);
    }

    /** chartBase 反序列化为键值表（`k=v;` 以「;」分隔）。 */
    private static Map<String, String> parseChartBase(String s) {
        Map<String, String> m = new LinkedHashMap<>();
        if (s == null) {
            return m;
        }
        for (String seg : s.split(";")) {
            int eq = seg.indexOf('=');
            if (eq > 0) {
                m.put(seg.substring(0, eq), seg.substring(eq + 1));
            }
        }
        return m;
    }

    /** 由 chartBase 重算四柱（供 M1 合婚等复用节点 2 逻辑）。 */
    private int[][] computePillars(Map<String, String> cb) {
        LocalDateTime solar = LocalDateTime.parse(cb.get("SOLAR"));
        boolean lateZi = Boolean.parseBoolean(orDefault(cb.get("LATEZI"), "false"));
        LocalDate dayDate = lateZi ? solar.toLocalDate().plusDays(1) : solar.toLocalDate();
        int[] dp = dayPillar(dayDate);
        return new int[][]{yearPillar(solar), monthPillar(solar), dp,
                hourPillar(dp[0], solar.getHour())};
    }

    /** 解析四柱字符串「甲子 丙寅 戊午 壬子」为 {{干,支}×4}。 */
    private static int[][] parsePillars(String fp) {
        String[] parts = (fp == null ? "" : fp).trim().split("\\s+");
        int[][] out = new int[4][2];
        for (int i = 0; i < 4; i++) {
            String s = (i < parts.length) ? parts[i].trim() : "甲子";
            if (s.length() < 2) {
                s = "甲子";
            }
            int g = indexOf(GAN, s.substring(0, 1));
            int z = indexOf(ZHI, s.substring(1, 2));
            out[i][0] = (g < 0) ? 0 : g;
            out[i][1] = (z < 0) ? 0 : z;
        }
        return out;
    }

    /** 年柱（以立春分年，按精确交节时刻判定；第 31 步节气精度升级）。 */
    private int[] yearPillar(LocalDateTime d) {
        int gzYear = d.isBefore(solarTermDateTime(d.getYear(), 2)) ? d.getYear() - 1 : d.getYear();
        return new int[]{floorMod(gzYear - 4, 10), floorMod(gzYear - 4, 12)};
    }

    /** 月柱（以十二「节」定月建，五虎遁起月；按精确交节时刻判定）。 */
    private int[] monthPillar(LocalDateTime d) {
        int y = d.getYear();
        int bi = -1;
        for (int i = 11; i >= 0; i--) {
            if (!d.isBefore(solarTermDateTime(y, JIE_IDX[i]))) {
                bi = i;
                break;
            }
        }
        int branch = (bi >= 0) ? JIE_BRANCH[bi] : 0; // 1 月小寒前 → 子月
        int gzYear = d.isBefore(solarTermDateTime(y, 2)) ? y - 1 : y;
        int yearGan = floorMod(gzYear - 4, 10);
        int yinStem = (floorMod(yearGan, 5) * 2 + 2) % 10; // 五虎遁：寅月天干
        int stem = (yinStem + floorMod(branch - 2, 12)) % 10;
        return new int[]{stem, branch};
    }

    /** 日柱（干支纪日，JDN 锚定）。 */
    private static int[] dayPillar(LocalDate d) {
        long jdn = julianDay(d.getYear(), d.getMonthValue(), d.getDayOfMonth());
        return new int[]{floorMod((int) (jdn + 9), 10), floorMod((int) (jdn + 1), 12)};
    }

    /** 时柱（五鼠遁起时；子时跨 23:00–00:59）。 */
    private static int[] hourPillar(int dayGan, int hour) {
        int branch = ((hour + 1) / 2) % 12;
        int ziStem = (floorMod(dayGan, 5) * 2) % 10; // 五鼠遁：子时天干
        return new int[]{(ziStem + branch) % 10, branch};
    }

    /** 儒略日（整数，正午基准）。 */
    private static long julianDay(int y, int m, int d) {
        int a = (14 - m) / 12;
        long yy = y + 4800L - a;
        int mm = m + 12 * a - 3;
        return d + (153L * mm + 2) / 5 + 365L * yy + yy / 4 - yy / 100 + yy / 400 - 32045;
    }

    /** 精确节气时刻（含回退）：year 年第 idx 节气（0=小寒）。
     *  数据取自 DB {@code solar_term_time}（经 OBDA/Ontop）；DB 不可用或年份超出 1950–2050 时
     *  回退内置近似算法（误差 < 1 天，流程不阻断）。 */
    private LocalDateTime solarTermDateTime(int year, int idx) {
        LocalDateTime[] arr = solarTermCache.computeIfAbsent(year, y -> {
            LocalDateTime[] r = loadSolarTermYear(y);
            return (r != null) ? r : new LocalDateTime[0];
        });
        if (arr.length == 24 && arr[idx] != null) {
            return arr[idx];
        }
        return approxSolarTermDateTime(year, idx);
    }

    /** 按年加载 24 节气精确交节时刻（经 OBDA/Ontop，solar_term_time）；失败或超范围返回 null。 */
    private LocalDateTime[] loadSolarTermYear(int year) {
        if (year < 1950 || year > 2050) {
            return null;
        }
        List<Map<String, String>> rows = queryClauses(
                "SELECT ?idx ?dt WHERE { ?o a calendar:SolarTermInstant ; "
              + "calendar:termYear \"" + year + "\"^^xsd:int ; "
              + "calendar:termIndex ?idx ; calendar:termDatetime ?dt . }");
        if (rows.isEmpty()) {
            return null;
        }
        LocalDateTime[] arr = new LocalDateTime[24];
        int hit = 0;
        for (Map<String, String> r : rows) {
            LocalDateTime dt = parseOntoDateTime(r.get("dt"));
            Integer i = parseIntOrNull(r.get("idx"));
            if (dt != null && i != null && i >= 0 && i < 24) {
                arr[i] = dt;
                hit++;
            }
        }
        return (hit == 0) ? null : arr;
    }

    private static Integer parseIntOrNull(String s) {
        try {
            return Integer.valueOf(s.trim());
        } catch (Exception e) {
            return null;
        }
    }

    /** 解析 SPARQL dateTime 字面量（兼容 'T'/' ' 分隔、小数秒、时区 Z/±hh:mm；仅日期按 00:00）；失败返回 null。 */
    private static LocalDateTime parseOntoDateTime(String s) {
        if (s == null) {
            return null;
        }
        String v = s.trim().replace(' ', 'T');
        int z = v.indexOf('Z');
        if (z >= 0) {
            v = v.substring(0, z);
        }
        int plus = v.indexOf('+', 10);
        if (plus >= 0) {
            v = v.substring(0, plus);
        }
        if (v.length() > 19) {
            v = v.substring(0, 19);
        }
        try {
            return LocalDateTime.parse(v);
        } catch (Exception e) {
            try {
                return LocalDate.parse(v).atStartOfDay();
            } catch (Exception e2) {
                return null;
            }
        }
    }

    /** 二十四节气近似时刻（内置算法，以 1900 为基准，误差 < 1 天）；DB 不可用/超范围时的回退。 */
    private static LocalDateTime approxSolarTermDateTime(int year, int idx) {
        long ms = 31556925974L * (year - 1900L) + S_TERM[idx] * 60000L;
        LocalDateTime base = LocalDateTime.of(1900, 1, 6, 2, 5, 0);
        return base.plusSeconds(ms / 1000L);
    }

    /** 取出生年附近（前一年~后一年）全部「节」精确时刻并排序。 */
    private List<LocalDateTime> allJieAround(int year) {
        List<LocalDateTime> list = new ArrayList<>();
        for (int y = year - 1; y <= year + 1; y++) {
            for (int j : JIE_IDX) {
                list.add(solarTermDateTime(y, j));
            }
        }
        Collections.sort(list);
        return list;
    }

    /** 起运岁：以「生日—相邻节交节时刻」的实际时长折除（三日折一岁，★13 数值计算）。
     *  三命通会·论大运「折除之法必用生者日历，过日时数」——故按分钟精度折除（3 日＝1 岁），
     *  较旧「按日取整」更贴合原著；显示仍以岁为单位（保留两位）。 */
    private double pilotAge(LocalDateTime birth, String direction) {
        List<LocalDateTime> jies = allJieAround(birth.getYear());
        LocalDateTime target = null;
        if ("顺".equals(direction)) {
            for (LocalDateTime j : jies) {
                if (j.isAfter(birth)) {
                    target = j;
                    break;
                }
            }
        } else {
            for (int i = jies.size() - 1; i >= 0; i--) {
                if (!jies.get(i).isAfter(birth)) {
                    target = jies.get(i);
                    break;
                }
            }
        }
        long minutes = (target == null) ? 90L * 24 * 60
                : Math.abs(java.time.Duration.between(birth, target).toMinutes());
        return Math.round(minutes / 4320.0 * 100.0) / 100.0; // 4320 分钟 = 3 日 = 1 岁
    }

    /** 自给定柱起顺/逆行六十甲子 n 步（大运/小运共用）。 */
    private static List<String> greatFortunes(int[] pillar, String direction, int n) {
        int base = sexagenaryIndex(pillar[0], pillar[1]);
        List<String> out = new ArrayList<>();
        for (int i = 1; i <= n; i++) {
            out.add(ganZhiFromIndex(base + ("顺".equals(direction) ? i : -i)));
        }
        return out;
    }

    /**
     * 命宫（三命通会卷二·论坐命官）：先将所生之月「从子上起正月、逆行十二位」得月宫；
     * 再「将所生之时加于月宫、顺行十二位，逢卯即安命宫」。等价算术：月宫序(子=1)+时支序(子=1)，
     * 和≤14 用 14 减，>14 用 26 减，所得之数即命宫地支序(子=1)；命宫天干「仍随生年，亦如起月之法」
     * （五虎遁：甲己之年丙作首）推得。
     * 校验：甲子年三月（辰月）戌时 → 月宫戌、时戌，序 11+11=22 → 26-22=4 → 卯宫；甲年寅月起丙，
     * 卯上为丁 → 丁卯宫（与原著例「乃丁卯宫也」一致）。
     */
    private static String mingGong(int monthBranch, int hourBranch, int yearGan) {
        int yueGong = floorMod(2 - monthBranch, 12); // 子起正月逆行：正月(寅)→子，二月(卯)→亥，三月(辰)→戌…
        int m = yueGong + 1;                          // 月宫序（子=1…亥=12）
        int t = hourBranch + 1;                       // 时支序（子=1…亥=12）
        int sum = m + t;
        int gong = (sum <= 14) ? (14 - sum) : (26 - sum); // 命宫地支序（子=1…亥=12）
        int gongBranch = floorMod(gong - 1, 12);
        // 命宫天干：随生年干以五虎遁起（寅月 = （年干%5)*2+2）。
        int yinStem = (floorMod(yearGan, 5) * 2 + 2) % 10;
        int stem = (yinStem + floorMod(gongBranch - 2, 12)) % 10;
        return GAN[stem] + ZHI[gongBranch];
    }

    /** 天干五合，命中返回化气五行索引，否则 -1。 */
    private static int ganWuHe(int g1, int g2) {
        for (int i = 0; i < GAN_WUHE.length; i++) {
            if ((GAN_WUHE[i][0] == g1 && GAN_WUHE[i][1] == g2)
                    || (GAN_WUHE[i][0] == g2 && GAN_WUHE[i][1] == g1)) {
                return i;
            }
        }
        return -1;
    }

    /** 十神索引（对日主）：0比肩 1劫财 2食神 3伤官 4偏财 5正财 6偏官 7正官 8偏印 9正印。 */
    private static int tenGodIndex(int dayGan, int target) {
        int de = GAN_ELEM[dayGan];
        int te = GAN_ELEM[target];
        boolean same = GAN_YY[dayGan] == GAN_YY[target];
        if (te == de) {
            return same ? 0 : 1;
        }
        if (te == (de + 1) % 5) {
            return same ? 2 : 3;
        }
        if (de == (te + 1) % 5) {
            return same ? 8 : 9;
        }
        if (te == (de + 2) % 5) {
            return same ? 4 : 5;
        }
        return same ? 6 : 7;
    }

    /** 十神本体映射缓存（DB ten_god_map 经 OBDA 暴露为 tengod:TenGodMap）：键 "日干|目标干" → 十神名。null=本体不可用。 */
    private volatile Map<String, String> tenGodMapCache = null;

    /** 取自本体 tengod:TenGodMap（一次取全库建缓存）；本体不可用返回 null，调用方降级 tenGodIndex。 */
    private Map<String, String> tenGodOntologyMap() {
        if (tenGodMapCache != null) {
            return tenGodMapCache;
        }
        try {
            List<Map<String, String>> rows = queryClauses(
                    "SELECT ?d ?t ?g WHERE { ?m a tengod:TenGodMap ; tengod:baseDayStem ?d ; "
                  + "tengod:targetStem ?t ; tengod:mapTenGodName ?g . }");
            Map<String, String> m = new java.util.HashMap<>();
            for (Map<String, String> r : rows) {
                String d = r.get("d"), t = r.get("t"), g = r.get("g");
                if (d != null && t != null && g != null && !g.isBlank()) {
                    m.put(d + "|" + t, g);
                }
            }
            if (m.isEmpty()) {
                return null;
            }
            tenGodMapCache = m;
            return m;
        } catch (Exception e) {
            return null;
        }
    }

    /** 十神名：本体 tengod:TenGodMap 优先，未命中降级 tenGodIndex 五行生克算法（算理，两者实测 100/100 一致）。 */
    private String tenGodName(int dayGan, int target) {
        Map<String, String> m = tenGodOntologyMap();
        if (m != null) {
            String g = m.get(GAN[dayGan] + "|" + GAN[target]);
            if (g != null && !g.isBlank()) {
                return g;
            }
        }
        return TEN_GOD[tenGodIndex(dayGan, target)];
    }

    /** 四柱所见十神集合（天干 + 藏干）。 */
    private static Set<String> tenGodSet(int dayGan, int[][] p) {
        Set<String> set = new LinkedHashSet<>();
        for (int i = 0; i < 4; i++) {
            if (i != 2) {
                set.add(TEN_GOD[tenGodIndex(dayGan, p[i][0])]);
            }
            for (String hs : ZHI_HIDDEN[p[i][1]]) {
                set.add(TEN_GOD[tenGodIndex(dayGan, indexOf(GAN, hs))]);
            }
        }
        return set;
    }

    /** 统计某类十神（比劫/食伤/印/财/官杀）出现次数（天干 + 藏干）。 */
    private static int countTenGodCategory(int[][] p, String cat) {
        int dayGan = p[2][0];
        int cnt = 0;
        for (int i = 0; i < 4; i++) {
            if (i != 2 && inCategory(TEN_GOD[tenGodIndex(dayGan, p[i][0])], cat)) {
                cnt++;
            }
            for (String hs : ZHI_HIDDEN[p[i][1]]) {
                if (inCategory(TEN_GOD[tenGodIndex(dayGan, indexOf(GAN, hs))], cat)) {
                    cnt++;
                }
            }
        }
        return cnt;
    }

    private static boolean inCategory(String tg, String cat) {
        switch (cat) {
            case "比劫": return "比肩".equals(tg) || "劫财".equals(tg);
            case "食伤": return "食神".equals(tg) || "伤官".equals(tg);
            case "印": return "正印".equals(tg) || "偏印".equals(tg);
            case "财": return "正财".equals(tg) || "偏财".equals(tg);
            case "官杀": return "正官".equals(tg) || "偏官".equals(tg);
            default: return false;
        }
    }

    /** 五行相生：a 生 b（b == (a+1)%5）。 */
    private static boolean generates(int a, int b) {
        return b == (a + 1) % 5;
    }

    /** 纳音五行索引（取纳音名末字：木0 火1 土2 金3 水4，同 ELEM 序）；无法识别返回 -1。 */
    private static int nayinElemIndex(String ny) {
        if (ny == null || ny.isEmpty()) {
            return -1;
        }
        switch (ny.charAt(ny.length() - 1)) {
            case '木': return 0;
            case '火': return 1;
            case '土': return 2;
            case '金': return 3;
            case '水': return 4;
            default: return -1;
        }
    }

    /** 年支（生肖）关系：同支比和 / 六合 / 三合 / 三会 / 六冲 / 六害 / 相刑 / 无合无冲。
     *  出处：三命通会卷二·论支元六合/论支元三合/论支元三会/论冲击/论六害/论三刑。 */
    private static String yearBranchRelation(int a, int b) {
        if (a == b) {
            return "同支比和";
        }
        if (pairIn(LIUCHONG, a, b)) {
            return "六冲";
        }
        if (xingPair(a, b)) {
            return "相刑";
        }
        if (pairIn(LIUHAI, a, b)) {
            return "六害";
        }
        if (pairIn(LIUHE, a, b)) {
            return "六合";
        }
        for (int[] g : SANHE) {
            if (containsInt(g, a) && containsInt(g, b)) {
                return "三合";
            }
        }
        for (int[] g : SANHUI) {
            if (containsInt(g, a) && containsInt(g, b)) {
                return "三会";
            }
        }
        return "无合无冲";
    }

    /** 地支所属三合局分组索引（申子辰0 寅午戌1 巳酉丑2 亥卯未3）；异常返回 -1。 */
    private static int sanheGroupOf(int zhi) {
        for (int g = 0; g < SANHE.length; g++) {
            if (containsInt(SANHE[g], zhi)) {
                return g;
            }
        }
        return -1;
    }

    /** 天干对地支的寄生十二宫。 */
    private static String stageOf(int gan, int zhi) {
        int cs = CHANGSHENG_ZHI[gan];
        int stage = (GAN_YY[gan] == 1) ? floorMod(zhi - cs, 12) : floorMod(cs - zhi, 12);
        return STAGE_NAME[stage];
    }

    /** 纳音（内置表；与 OBDA `nayin` 表同源，30 名已核一致）。出处：三命通会卷一·总论纳音。 */
    private static String nayinOf(int g, int z) {
        int idx = sexagenaryIndex(g, z);
        return idx < 0 ? "【待考】" : NAYIN[floorMod(idx, 60) / 2];
    }

    /** 天克地冲：两柱天干相克且地支相冲。 */
    private static boolean tianKeDiChong(String gz1, String gz2) {
        if (gz1 == null || gz2 == null || gz1.length() < 2 || gz2.length() < 2) {
            return false;
        }
        int g1 = indexOf(GAN, gz1.substring(0, 1));
        int g2 = indexOf(GAN, gz2.substring(0, 1));
        int z1 = indexOf(ZHI, gz1.substring(1, 2));
        int z2 = indexOf(ZHI, gz2.substring(1, 2));
        if (g1 < 0 || g2 < 0 || z1 < 0 || z2 < 0) {
            return false;
        }
        boolean ke = GAN_ELEM[g1] == (GAN_ELEM[g2] + 2) % 5 || GAN_ELEM[g2] == (GAN_ELEM[g1] + 2) % 5;
        return ke && pairIn(LIUCHONG, z1, z2);
    }

    /** 四柱所欠五行（用于合婚互补）。 */
    private static String lackElements(int[][] p) {
        boolean[] has = new boolean[5];
        for (int[] pz : p) {
            has[ZHI_ELEM[pz[1]]] = true;
            has[GAN_ELEM[pz[0]]] = true;
        }
        List<String> lack = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            if (!has[i]) {
                lack.add(ELEM_NAME[i]);
            }
        }
        return String.join(",", lack);
    }

    /** 对方四柱是否含给定五行之一。 */
    private static boolean partnerContains(int[][] partner, String[] elems) {
        boolean[] has = new boolean[5];
        for (int[] pz : partner) {
            has[ZHI_ELEM[pz[1]]] = true;
            has[GAN_ELEM[pz[0]]] = true;
        }
        for (String e : elems) {
            for (int i = 0; i < 5; i++) {
                if (ELEM_NAME[i].equals(e.trim()) && has[i]) {
                    return true;
                }
            }
        }
        return false;
    }

    /** 三元组地支是否包含 zhi。 */
    private static boolean containsZhi(int[][] p, int zhi) {
        for (int[] pz : p) {
            if (pz[1] == zhi) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsInt(int[] arr, int v) {
        for (int x : arr) {
            if (x == v) {
                return true;
            }
        }
        return false;
    }

    /** 成对地支关系是否命中（忽略先后）。 */
    private static boolean pairIn(int[][] pairs, int a, int b) {
        for (int[] pr : pairs) {
            if ((pr[0] == a && pr[1] == b) || (pr[0] == b && pr[1] == a)) {
                return true;
            }
        }
        return false;
    }

    /** 地支所属三合局分组（申子辰0 / 寅午戌1 / 巳酉丑2 / 亥卯未3）。 */
    private static int sanheGroup(int zhi) {
        for (int i = 0; i < SANHE.length; i++) {
            if (containsInt(SANHE[i], zhi)) {
                return i;
            }
        }
        return 0;
    }

    /** 四柱天干是否透出某五行。 */
    private static boolean elemTouGan(int[][] p, int elem) {
        for (int i = 0; i < 4; i++) {
            if (GAN_ELEM[p[i][0]] == elem) {
                return true;
            }
        }
        return false;
    }

    /** 非日柱天干是否有比劫（与日主同五行）透出。 */
    private static boolean biJieTouGan(int[][] p, int dayElem) {
        for (int i = 0; i < 4; i++) {
            if (i != 2 && GAN_ELEM[p[i][0]] == dayElem) {
                return true;
            }
        }
        return false;
    }

    /**
     * 某五行是否「有力/能为用」：透干且有根，或得月令，或地支本气通根（得地本气）。
     * 余气藏而不透、且不得令者力弱，不作「用」——此即古典「透」与「用」之别。
     * 出处：子平真诠「透印则身赖印生」（须印有力方能生）、论用神通根得令；三命通会·论旺衰（得时得地）。
     */
    private static boolean youLi(int[][] p, int monthBranch, int elem) {
        boolean tou = elemTouGan(p, elem);
        boolean youGen = hasRoot(p, elem);
        boolean deLing = ZHI_ELEM[monthBranch] == elem;
        boolean benQiGen = false;
        for (int[] pz : p) {
            if (ZHI_ELEM[pz[1]] == elem) {
                benQiGen = true;
                break;
            }
        }
        return (tou && youGen) || deLing || benQiGen;
    }


    /** 某五行是否通根：地支藏干见之，或成三合/三会局（《子平真诠·论用神》通根会局之义）。 */
    private static boolean hasRoot(int[][] p, int elem) {
        for (int[] pz : p) {
            for (String hs : ZHI_HIDDEN[pz[1]]) {
                if (GAN_ELEM[indexOf(GAN, hs)] == elem) {
                    return true;
                }
            }
        }
        // 三合/三会成局者，其成局三支之藏干必含该局五行（申子辰→水、寅午戌→火、巳酉丑→金、亥卯未→木；
        // 三会同理：寅卯辰→木、巳午未→火、申酉戌→金、亥子丑→水），故上方藏干循环必已返回 true；
        // 此兜底恒为 false（去除不可达操作数，行为等价）。
        return false;
    }

    /** 地支是否成某五行之三合局（三支齐全，局之五行取三合局中神之五行）。 */
    private static boolean sanheFull(int[][] p, int elem) {
        for (int[] g : SANHE) {
            if (ZHI_ELEM[g[1]] != elem) {
                continue;
            }
            boolean all = true;
            for (int z : g) {
                if (!containsZhi(p, z)) {
                    all = false;
                    break;
                }
            }
            if (all) {
                return true;
            }
        }
        return false;
    }

    /** 地支是否成某五行之三会方局（三支齐全）。 */
    private static boolean sanhuiFull(int[][] p, int elem) {
        for (int k = 0; k < SANHUI.length; k++) {
            if (SANHUI_ELEM[k] != elem) {
                continue;
            }
            boolean all = true;
            for (int z : SANHUI[k]) {
                if (!containsZhi(p, z)) {
                    all = false;
                    break;
                }
            }
            if (all) {
                return true;
            }
        }
        return false;
    }

    /**
     * 专旺格侦测（按典籍全局成象校验，非仅凭月令）：
     * 曲直（木）亥卯未/寅卯辰全而忌金；炎上（火）寅午戌/巳午未全而忌水，丁无丙甲不作炎上（《穷通宝鉴》五月丁火）；
     * 稼穑（土）辰戌丑未全而忌木；从革（金）巳酉丑/申酉戌全而忌火；润下（水）申子辰/亥子丑全而忌土。
     * 出处：渊海子平·论外格（曲直/炎上/稼穑/从革/润下）；三命通会卷六·炎上等；穷通宝鉴各月专旺成格条。
     * 成象返回「专旺格（X）」，未成象返回空串。
     */
    private static String detectZhuanWang(int[][] p, int dayGan) {
        int d = GAN_ELEM[dayGan];
        switch (d) {
            case 0: // 曲直：木，忌金（庚辛申酉）
                if (!(sanheFull(p, 0) || sanhuiFull(p, 0))) {
                    return "";
                }
                if (elemTouGan(p, 3) || containsZhi(p, 8) || containsZhi(p, 9)) {
                    return "";
                }
                return "专旺格（曲直）";
            case 1: // 炎上：火，忌水（壬癸亥子）
                if (!(sanheFull(p, 1) || sanhuiFull(p, 1))) {
                    return "";
                }
                if (elemTouGan(p, 4) || containsZhi(p, 0) || containsZhi(p, 11)) {
                    return "";
                }
                // 丁火昭融，无丙性不炎上；须丙辅或甲引（《穷通宝鉴》五月丁火）
                if (dayGan == 3) {
                    boolean bingJia = false;
                    for (int i = 0; i < 4; i++) {
                        if (i == 2) {
                            continue;
                        }
                        if (p[i][0] == 2 || p[i][0] == 0) {
                            bingJia = true;
                        }
                    }
                    if (!bingJia) {
                        return "";
                    }
                }
                return "专旺格（炎上）";
            case 2: // 稼穑：土，辰戌丑未全，忌木（甲乙寅卯）
                if (!(containsZhi(p, 4) && containsZhi(p, 10) && containsZhi(p, 1) && containsZhi(p, 7))) {
                    return "";
                }
                // 稼穑须「辰戌丑未全」（渊海子平·稼穑格），四支已尽为四库、断无寅卯之余地；
                // 忌木唯在甲乙木干克土（elemTouGan(p,0)）。原 containsZhi(2/3) 恒假，属冗余死操作数，去除。
                if (elemTouGan(p, 0)) {
                    return "";
                }
                return "专旺格（稼穑）";
            case 3: // 从革：金，忌火（丙丁午）
                if (!(sanheFull(p, 3) || sanhuiFull(p, 3))) {
                    return "";
                }
                if (elemTouGan(p, 1) || containsZhi(p, 6)) {
                    return "";
                }
                return "专旺格（从革）";
            case 4: // 润下：水，忌土；但辰丑为湿土藏水、正是润下之根（申子辰/亥子丑皆含之），
                    //   不得以见辰丑即判破格，唯忌天干透戊己、或支见燥土未戌冲激（《渊海子平·论外格》润下喜金水、忌燥土）
                if (!(sanheFull(p, 4) || sanhuiFull(p, 4))) {
                    return "";
                }
                if (elemTouGan(p, 2) || containsZhi(p, 10) || containsZhi(p, 7)) {
                    return "";
                }
                return "专旺格（润下）";
            default:
                return "";
        }
    }

    private static int sexagenaryIndex(int gan, int zhi) {
        for (int n = 0; n < 60; n++) {
            if (n % 10 == gan && n % 12 == zhi) {
                return n;
            }
        }
        return -1;
    }

    private static String ganZhi(int[] p) {
        return GAN[floorMod(p[0], 10)] + ZHI[floorMod(p[1], 12)];
    }

    private static String ganZhiFromIndex(int n) {
        n = floorMod(n, 60);
        return GAN[n % 10] + ZHI[n % 12];
    }

    private static int indexOf(String[] arr, String v) {
        for (int i = 0; i < arr.length; i++) {
            if (arr[i].equals(v)) {
                return i;
            }
        }
        return -1;
    }

    private static int floorMod(int a, int b) {
        int r = a % b;
        return (r < 0) ? r + b : r;
    }

    private static String orDefault(String v, String def) {
        return (v == null || v.isBlank()) ? def : v;
    }

    private static String fmt(double d) {
        return (d == Math.floor(d)) ? String.valueOf((long) d) : String.valueOf(Math.round(d * 100) / 100.0);
    }

    private static double toDouble(Object o, double def) {
        if (o instanceof Number n) {
            return n.doubleValue();
        }
        if (o != null) {
            try {
                return Double.parseDouble(String.valueOf(o).trim());
            } catch (NumberFormatException ignore) {
                // noop
            }
        }
        return def;
    }

    // ==========================================================================
    // 通用工具（与 PlumBlossomOntologyJobWorker 口径一致：result 包裹 + throwError）
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
}
