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

    /** 末尾「命书详解」系统提示：以子平命师身份、依六典籍义理按本命推导详解，严禁编造出处。 */
    private static final String INTERPRET_SYSTEM_PROMPT =
            "你是精通《渊海子平》《三命通会》《滴天髓》《穷通宝鉴》《神峰通考》《子平真诠》六部典籍的命理大师。"
            + "请严格依据这六部典籍的义理与命书体例，对给定的【四柱命书（节点已按本命推导的口径结论）】作系统详解："
            + "先按本命四柱、十神、日主旺衰、格局成败、用神喜忌、岁运生克逐环节说明**推断依据**（为何如此断），"
            + "再引述相关典籍篇目的论断加以**印证**；引文只作印证，不得整段照抄与本命无关的原文。"
            + "要求：论述详实、条理清晰、有据可依。严禁编造典籍中并不存在的篇目、条文或断语；"
            + "若某一环节确无明文可引，须明确标注「此处依常理推之」，不得杜撰出处。使用规范 Markdown 输出。";

    /** 末尾「白话解读」系统提示：把前述命书详解（含引文）转写为通俗现代白话，术语随文浅释。 */
    private static final String INTERPRET_PLAIN_SYSTEM_PROMPT =
            "你是四柱命理的白话讲解员。请把给定的【四柱命书】与随附的古典命书详解，"
            + "用通俗易懂的现代白话文重新讲一遍：分点讲清日主强弱、格局用神、事业功名、财运、婚姻、健康、岁运吉凶"
            + "各自的含义与对命主意味着什么。"
            + "关键要求：凡命书中已定的专业术语（各个十神、格局、用神/相神/忌神、神煞、调候等），"
            + "必须逐项用一句话白话解释——先说该词「是什么、怎么来的」，再说「对命主意味着什么、有什么影响」，"
            + "力求一句一义、避免只堆砌名词；命书中未出现的术语不必强解。"
            + "只作白话转述，严禁编造典籍中并不存在的篇目、条文或断语。使用规范 Markdown 输出。";

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
     *        渊海子平卷之一·论羊刃「甲丙戊庚壬五阳有刃，乙己丁辛癸五阴无刃」。 */
    private static final int[] YANGREN = {3, -1, 6, -1, 6, -1, 9, -1, 0, -1};
    /** 十干禄（按日干）。渊海子平卷之一·论禄。 */
    private static final int[] LU = {2, 3, 5, 6, 5, 6, 8, 9, 11, 0};

    /** 三合局分组（申子辰 / 寅午戌 / 巳酉丑 / 亥卯未）。 */
    private static final int[][] SANHE = {{8, 0, 4}, {2, 6, 10}, {5, 9, 1}, {11, 3, 7}};
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
        log.info("✅ SiZhuBaZiOntologyJobWorker 初始化完成 | ontologyPath={}", mainOntologyPath);
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
    // 实现方法：查表（OBDA `nayin`/`hidden_stem`，库就绪前回退内置表）+ 本体 core:NaYin/HiddenStem。
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
            String dayPillar = ganZhi(p[2]);
            String traits = "日主" + GAN[p[2][0]] + ELEM_NAME[GAN_ELEM[p[2][0]]]
                    + "，日支" + ZHI[dayIdx] + "（" + ZHI_ANIMAL[dayIdx] + "）藏"
                    + String.join("", ZHI_HIDDEN[dayIdx])
                    + "；纳音" + nayinOf(p[2][0], p[2][1]) + "。";
            // 本命日柱六十甲子性质（三命通会卷一·释六十甲子性质吉凶，经 OBDA sexagenary_nature）
            String nature = clauseSexagenaryNature(dayPillar);
            if (nature != null) {
                traits += " 六十甲子性质（" + dayPillar + "）：" + nature + "（出处：三命通会卷一·释六十甲子性质吉凶）";
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
    // 实现方法：程序（合冲刑穿判定）+ SWRL R15 + 本体 relations:。
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
                                + "（化" + GAN_WUHE_HUA[hua] + "）");
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
    // 实现方法：查表（OBDA `ten_god_map`）+ 本体 tengod:（R7 由程序：10×10 查表）。
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
                    tg.append("日主").append(GAN[dayGan]).append("（日）");
                    continue;
                }
                tg.append(names[i]).append(GAN[p[i][0]]).append("→")
                        .append(TEN_GOD[tenGodIndex(dayGan, p[i][0])]).append("；");
            }
            // 地支藏干十神
            StringBuilder hiddenTg = new StringBuilder();
            for (int i = 0; i < 4; i++) {
                hiddenTg.append(names[i]).append(ZHI[p[i][1]]).append("藏");
                for (String hs : ZHI_HIDDEN[p[i][1]]) {
                    hiddenTg.append(hs).append("(").append(TEN_GOD[tenGodIndex(dayGan, indexOf(GAN, hs))]).append(")");
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
    // 实现方法：查表（OBDA `deity_rule`）+ 本体 deity:（R13/R14）；起例表庞大、异名异法并陈（⑧ 以三命通会卷二为主）。
    // ==========================================================================
    @JobWorker(type = "arrangeDeities", autoComplete = false)
    public void handleArrangeDeities(final ActivatedJob job, final JobClient client) {
        try {
            Map<String, Object> vars = job.getVariablesAsMap();
            int[][] p = parsePillars(str(vars.get("fourPillars")));
            String[] names = {"年", "月", "日", "时"};
            int dayGan = p[2][0];
            int refZhi = p[0][1]; // 三合类神煞以年支为参照
            int grp = sanheGroup(refZhi);

            List<String> deities = new ArrayList<>();
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
                if (YIMA[grp] == b) {
                    deities.add(names[i] + ZHI[b] + "：驿马");
                }
                if (TAOHUA[grp] == b) {
                    deities.add(names[i] + ZHI[b] + "：桃花（咸池）");
                }
                if (HUAGAI[grp] == b) {
                    deities.add(names[i] + ZHI[b] + "：华盖");
                }
                if (JIANGXING[grp] == b) {
                    deities.add(names[i] + ZHI[b] + "：将星");
                }
            }
            String deityStr = deities.isEmpty() ? "四柱未见常用神煞（以 OBDA deity_rule 详查）" : String.join("；", deities);

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
            // 外格/杂格（从/化/专旺）判定：月令无用神或成象时另取（《子平真诠·论外格用舍/论杂格》）
            String extern = detectExternPattern(p, dayGan);

            // 定格推导（月令提纲法）：写明「为何定此格」——格神是否透干、取格依据（供前端可读）
            boolean geTouGan = false;
            for (int i = 0; i < 4; i++) {
                if (GAN_ELEM[p[i][0]] == benQiElem) {
                    geTouGan = true;
                    break;
                }
            }
            StringBuilder derive = new StringBuilder();
            derive.append("定格（月令提纲法·《子平真诠》「用神专求月令」）：日主").append(GAN[dayGan])
                    .append("生于").append(ZHI[monthBranch]).append("月，月令本气为").append(benQi)
                    .append("，对日主为「").append(tgName).append("」，");
            if (isBiJieMonth) {
                derive.append("月令即日主之比劫（禄/刃当令），故取「").append(pattern)
                        .append("」；此格以月令禄刃定格，不以透干论。");
            } else {
                derive.append("依「以月令本气所化十神定格」之义，取「").append(pattern)
                        .append("」，格神五行为").append(ELEM_NAME[benQiElem]).append("；")
                        .append(geTouGan
                                ? "格神" + ELEM_NAME[benQiElem] + "已透干，格局分明，取格明确。"
                                : "格神" + ELEM_NAME[benQiElem] + "未透干，取格以月令本气为准，宜再查支中会局或藏干是否得用。");
            }

            // 成败救应（简化 R10）：月支逢冲/合 → 格神动摇，需救应；逐条给依据
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
            String rescue;
            String grade;
            if (monthChong) {
                rescue = "月令逢冲，格神动摇，需救应（以合解或制化护格）；据《子平真诠·论用神成败救应》。";
                grade = "格有破，待救应";
            } else if (monthHe) {
                rescue = "月令逢合，格神被绊，宜权衡合化之向背（合去为忌、合来为喜）；据《子平真诠·论用神成败救应》。";
                grade = "格有绊";
            } else {
                rescue = "月令清纯，格神无损，格成；据《子平真诠·论用神成败救应》。";
                grade = "格成";
            }

            // 具名格局（三命通会卷五/卷六等）：仅当本命**确已命中**该具名格之构成条件时才引用原文印证
            // （改：废弃旧「按 pattern_class 类别整段拼原文」的错配/堆砌做法——无关原文不再列出）
            List<String[]> hits = hitNamedPatterns(p, dayGan);
            List<String> named = new ArrayList<>();
            for (String[] hit : hits) {
                named.add(hit[0] + "（" + hit[1] + "，出处：" + hit[2] + "）");
            }
            String namedText = named.isEmpty()
                    ? "【具名格局】本命未命中《三命通会》所载具名格之构成条件；循「原文仅于命中时印证」之例，此处不列具名格原文。"
                    : "【具名格局·命中印证】" + String.join("；", named);

            // 典籍定向引据（第 29 步）：具名格名录（pattern_catalog）+ 子平真诠 + 滴天髓象论（dts_clause）
            List<String> patternCites = new ArrayList<>();
            patternCites.addAll(citePatternCatalog(hits));
            patternCites.addAll(citeDts(pattern + extern, orDefault(str(vars.get("ganzhiRelations")), "")));
            patternCites.addAll(citeZiping("论用神成败救应"));

            String externText = extern.isEmpty() ? ""
                    : "\n【外格/杂格】侦得「" + extern + "」（月令无用神或全局成象时另取，据《子平真诠·论外格用舍/论杂格》）。";

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("patternResult", derive.toString() + externText + "\n" + rescue + "\n" + namedText
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
            boolean strong = str(vars.get("strength")) != null && str(vars.get("strength")).contains("强");

            int monthBranch = p[1][1];
            // 主取（《子平真诠·论用神》「八字用神，专求月令」）：用神取月令格神五行；
            //   建禄月劫（月令为日主比劫）另取四柱透出之官杀/财/食伤为用；
            //   顺逆：四吉神（财官印食）顺用、四凶神（煞伤劫刃）逆用 → 定相神/忌神。
            int yongElem = yongShenElem(p);
            int xiangElem = xiangShenElem(p);
            int jiElem = jiShenElem(p);
            String yongRole = elemRoleName(dayElem, yongElem);
            String monthGod = monthGodName(p);

            String yongShen = "用神（主取·月令格局法）：" + ELEM_NAME[yongElem] + "（" + yongRole
                    + "）——日主" + GAN[dayGan] + ELEM_NAME[dayElem] + "生于" + ZHI[monthBranch] + "月（"
                    + ELEM_NAME[ZHI_ELEM[monthBranch]] + "令，格神「" + monthGod + "」），据《子平真诠·论用神》"
                    + "「用神专求月令」，取月令之" + ELEM_NAME[yongElem] + "为用；对应十神为" + yongRole + "。"
                    + "（旺衰法：" + (strong ? "身强，宜克泄耗" : "身弱，宜生扶帮") + "，作异法并陈。）";
            String xiangShen = "相神：" + ELEM_NAME[xiangElem] + "（" + elemRoleName(dayElem, xiangElem)
                    + "，" + (isNiYong(p) ? "制服" : "生助") + "用神" + ELEM_NAME[yongElem] + "）";
            String jiShen = "忌神：" + ELEM_NAME[jiElem] + "（" + elemRoleName(dayElem, jiElem)
                    + "，" + (isNiYong(p) ? "党助" : "克损") + "用神" + ELEM_NAME[yongElem] + "）";

            // 调候法（穷通宝鉴·造化元钥）：按日干 + 月支匹配条文库，落地原挂【待考】之调候用神
            Map<String, String> th = clauseTiaohou(GAN[dayGan], ZHI[monthBranch]);
            String tiaohou;
            if (th != null) {
                String sec = th.get("x");
                boolean hasSec = sec != null && !sec.isBlank() && !"【待考】".equals(sec) && !"—".equals(sec);
                tiaohou = "【调候用神·穷通宝鉴】" + ZHI[monthBranch] + "月" + GAN[dayGan] + "：主用 " + th.get("p")
                        + (hasSec ? "，次用 " + sec : "")
                        + "。原文：" + th.get("text") + "（出处：" + th.get("source") + "）";
                if (th.get("p") != null && th.get("p").contains("待考")) {
                    tiaohou += "；该月调候原文未标主用，挂 diagnosis:ToBeVerified。";
                }
            } else {
                tiaohou = "【调候用神·穷通宝鉴】" + ZHI[monthBranch] + "月" + GAN[dayGan]
                        + "：条文库未命中（OBDA 未就绪或该月无条目），相关条文挂 diagnosis:ToBeVerified（【待考】）。";
            }

            String methods = "【主取】格局法（子平真诠「用神专求月令」）；"
                    + "【并陈】旺衰法（滴天髓「衰旺」）结论挂 diagnosis:SchoolVariant；"
                    + (th != null ? "【调候】已接入穷通宝鉴条文库（按日干+月支匹配）。"
                                  : "【调候】穷通宝鉴条文库未命中，挂 diagnosis:ToBeVerified。");

            // 典籍定向引据（第 29 步）：十干/十二支体象（technique_catalog），按本命日干、日支定位
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
    // 出处：子平真诠·论用神格局高低；滴天髓·清气/浊气/真神/假神。
    // ==========================================================================
    @JobWorker(type = "judgeChartLevel", autoComplete = false)
    public void handleJudgeChartLevel(final ActivatedJob job, final JobClient client) {
        try {
            Map<String, Object> vars = job.getVariablesAsMap();
            String patternGrade = orDefault(str(vars.get("patternGrade")), "");
            String strength = orDefault(str(vars.get("strength")), "");

            boolean geCheng = patternGrade.contains("格成");
            boolean geBreak = patternGrade.contains("破");
            boolean bodyStrong = strength.contains("强");
            boolean bodyWeak = strength.contains("弱");

            // 清浊/隐显/众寡：以本命用神之透干、藏支、同类多寡按滴天髓口径实测（不用通用模板）
            int[][] p = parsePillars(str(vars.get("fourPillars")));
            int dayGan = p[2][0];
            int dayElem = GAN_ELEM[dayGan];
            // 用神/忌神与节点9同口径（月令格局法）
            int yongElem = yongShenElem(p);
            int jiElem = jiShenElem(p);
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
            String xianYin = yongTouGan ? "透干（显）" : (yongCangZhi ? "藏支（隐）" : "四柱不见（用神力薄）");
            String zhongGua = yongCount >= 3 ? "众（用神有力）" : (yongCount == 0 ? "寡（用神无助）" : "中和（用神有根）");
            String qingZhuo = (geCheng && !bodyWeak && jiCount <= yongCount) ? "清（格正身健、忌神不扰）"
                    : (geBreak || (jiCount > yongCount) ? "浊（格破或忌众用寡）" : "清浊相参");
            boolean zhenShen = yongTouGan || yongCangZhi;
            String purity = "清浊真假（本命实测·滴天髓）：格=" + patternGrade + "，身=" + strength
                    + " → 清浊：" + qingZhuo + "；用神" + ELEM_NAME[yongElem] + " " + xianYin + "，"
                    + "用神计数=" + yongCount + "、忌神计数=" + jiCount + " → 众寡：" + zhongGua
                    + "；真神得用=" + zhenShen + "。";

            String yongXian = yongTouGan ? "用神透干" : (yongCangZhi ? "用神藏支" : "用神不见");
            String yongLi = yongCount >= 3 ? "用神有力" : (yongCount == 0 ? "用神力薄" : "用神有根");
            String level;
            if (geCheng && !bodyWeak) {
                level = "贵格（格成身健，" + yongXian + "、" + yongLi + "）";
            } else if (geCheng && bodyWeak) {
                level = "富格（格成但身须扶，" + yongXian + "）";
            } else if (geBreak && bodyWeak) {
                level = "贫贱格（格破身弱，" + yongLi + "）";
            } else {
                level = "平常格（" + yongXian + "、" + yongLi + "）";
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
            StringBuilder years = new StringBuilder("；流年（年级）：");
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

            // 逐年评析：以本命用神/忌神为准（月令格局法），逐运判扶抑得失与主应（客户裁定：逐年评吉凶 + 应事）
            int dayGan = p[2][0];
            int dayElem = GAN_ELEM[dayGan];
            int yongElem = yongShenElem(p);
            int jiElem = jiShenElem(p);
            String analysis = buildFortuneAnalysis(p, startAge, fortunes, yongElem, jiElem, dayElem);

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
                    + "结合空亡：" + voidness + "。（年级为主给区间，月级可选；客户裁定 ⑯）";

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
            int yongElem = yongShenElem(p);
            int jiElem = jiShenElem(p);
            double startAge = toDouble(vars.get("startAge"), 1.0);
            String[] fortunes = orDefault(str(vars.get("fortuneSequence")), "").split(",");
            String chartLevel = orDefault(str(vars.get("chartLevel")), "");
            String patternGrade = orDefault(str(vars.get("patternGrade")), "");

            // 岁运辩证：按本命用神/忌神 + 具体大运干支逐运推导（不再用通用模板）
            String trend = buildFortuneSynthesis(p, startAge, fortunes, yongElem, jiElem, dayElem, patternGrade);

            String transformation = "成格变格（《子平真诠·论行运成格变格》）：本命格局「" + patternGrade
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
            String dayPillar = ganZhi(p[2]);
            String hourPillar = ganZhi(p[3]);
            Set<String> gods = tenGodSet(dayGan, p);

            // 疾病：五脏六腑所属干支（三命通会卷九），逐支按本命五行配脏腑
            String[] zang = {"肝/胆", "心/小肠", "脾/胃", "肺/大肠", "肾/膀胱"};
            StringBuilder disease = new StringBuilder("疾病（五脏六腑所属干支，三命通会卷九）：");
            for (int i = 0; i < 4; i++) {
                disease.append(ZHI[p[i][1]]).append("·").append(ELEM_NAME[ZHI_ELEM[p[i][1]]])
                        .append("属").append(zang[ZHI_ELEM[p[i][1]]]).append("；");
            }
            disease.append("日主").append(GAN[dayGan]).append(ELEM_NAME[dayElem]).append("主").append(zang[dayElem]).append("。");

            // 性情才德（滴天髓·性情/才德）：以本命日主五行 + 实际所见十神为纲（非通用原理罗列）
            StringBuilder temperament = new StringBuilder("性情才德（滴天髓·性情/才德）：日主").append(GAN[dayGan])
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
            temperament.append("性情随格局成败与岁运而分（相貌出身地位奋郁恩怨详参滴天髓）。");

            // 六亲吉凶：结合本命宫位干支 + 六亲说条文
            String kinTheory = clauseKinship();
            String kinship = "六亲吉凶（渊海子平卷之三·六亲总论）：年柱" + ganZhi(p[0]) + "看祖上/父母，月柱" + ganZhi(p[1])
                    + "看父母/兄弟，日支" + ZHI[p[2][1]] + "为妻（男）/夫（女），时柱" + ganZhi(p[3])
                    + "看子息；以对应宫位十神之喜忌与生克断吉凶。" + (kinTheory != null ? kinTheory : "");

            // 分类事项断：以本命十神/宫位/神煞为准，逐项按本命推导并给依据（不再用通用套话）
            String deities = orDefault(str(vars.get("deities")), "");
            String category = buildCategoryVerdicts(p, dayGan, dayElem, monthBranch, gods, deities);

            String all = temperament + "\n" + disease + "\n" + kinship + "\n" + category;

            // 典籍原文征引：按本命匹配日时断/十干坐支/月支断/六亲说（原文 + 出处）
            String dhClause = clauseDayHour(dayPillar, hourPillar);
            String ssClause = clauseStemSeat(GAN[dayGan]);
            String mbClause = clauseMonthBranch(ZHI[monthBranch], GAN[dayGan]);
            List<String> cites = new ArrayList<>();
            if (dhClause != null) {
                cites.add(dhClause);
            }
            if (ssClause != null) {
                cites.add(ssClause);
            }
            if (mbClause != null) {
                cites.add(mbClause);
            }
            if (kinTheory != null) {
                cites.add(kinTheory);
            }
            // 典籍定向引据（第 29 步）：病药/动静/盖头（theory_clause）+ 五行时地（auspice_clause）
            //   + 成篇赋诀（fu_clause）+ 六典籍全文节引（classic_text / technique_catalog）
            cites.addAll(citeTheory());
            cites.addAll(citeAuspiceTimePlace(ELEM_NAME[dayElem]));
            cites.addAll(citeFu(fuKeywords(gods)));
            cites.addAll(citeClassic("滴天髓", "中和"));
            cites.addAll(citeClassic("三命通会", "论五行生成"));
            String citations = cites.isEmpty()
                    ? "（条文库未就绪或未命中，相关原文挂【待考】）"
                    : String.join("\n", cites);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("categoryVerdicts", all);
            result.put("clauseCitations", citations);
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
            // 对方四柱
            LocalDateTime pdt = resolveBirthDateTime(partnerVar);
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

            boolean complementary = (!selfLack.isEmpty() && partnerContains(partner, selfLack.split(",")))
                    || (selfBiJie >= 2 && partnerShiShang >= 2) || (selfShiShang >= 2 && partnerBiJie >= 2);
            String conclusion = complementary
                    ? "合婚结论：双方五行/比劫伤食互补（依《神峰通考》卷一·男女合婚说正理），配合较佳，宜相扶相济。"
                    : "合婚结论：双方互补性一般，宜以用神互补、神煞配合详参；《神峰通考》正理未见明显相配。";

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("marriageConclusion", conclusion);
            result.put("complementAnalysis", comp.toString());
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
            Set<String> gods = tenGodSet(dayGan, p);
            boolean hasGuanSha = gods.contains("正官") || gods.contains("偏官");
            boolean hasShiShang = gods.contains("食神") || gods.contains("伤官");
            boolean jiShenChong = gods.contains("比肩") || gods.contains("劫财");

            // 女命分类（三命通会卷七·论女命）：按本命官杀/食伤/比劫之有无择要引条文（原文 + 出处）
            StringBuilder picks = new StringBuilder();
            for (Map<String, String> r : clauseLadyFortunes()) {
                String name = r.get("label");
                boolean rel;
                switch (name == null ? "" : name) {
                    case "纯", "清", "贵", "和", "福寿两备", "安静守分" -> rel = hasGuanSha && !jiShenChong;
                    case "浊", "滥", "娼", "淫" -> rel = jiShenChong && !hasGuanSha;
                    case "旺夫克子", "伤夫克子", "旺子伤夫", "招嫁不定", "滚浪桃花" -> rel = jiShenChong;
                    case "横夭少年" -> rel = !hasGuanSha && !hasShiShang;
                    default -> rel = false;
                }
                if (rel) {
                    picks.append(name).append("（").append(r.get("text")).append("，出处：").append(r.get("source")).append("）；");
                }
            }
            String pickText = picks.length() > 0 ? "【女命分类（三命通会卷七·论女命）】" + picks
                    : "【女命分类（三命通会卷七·论女命）】本命未见显著偏枯，归于平和一类。";

            String verdict = "女命专论（渊海子平卷之二·论女命）：以官星为夫、食伤为子、财为夫之资。"
                    + "本命" + (hasGuanSha ? "夫星（官杀）现" : "夫星（官杀）不显") + "；"
                    + (hasShiShang ? "子星（食伤）现" : "子星（食伤）不显") + "；"
                    + (jiShenChong ? "比劫重重，留意夺夫之嫌" : "比劫不重，夫妻宫较安") + "。"
                    + "富贵贫贱：看官星清纯、食伤制化与格局成败；旺夫克子/旺子伤夫，随夫子二星之强弱喜忌而分。"
                    + pickText;

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
            // 常用小儿关煞：将军箭/阎王关/四季关/金锁关等（按年支/月支/时支起例，异名异法并陈）
            List<String> guan = new ArrayList<>();
            guan.add("本命年支" + ZHI[yearZhi] + "：" + ZHI_ANIMAL[yearZhi] + "相");
            if (YIMA[grp] == p[3][1]) {
                guan.add("时值驿马，主早动/奔波关");
            }
            if (TAOHUA[grp] == p[3][1]) {
                guan.add("时值桃花，主情感关");
            }

            String verdict = "小儿关杀（三命通会·论小儿关杀）：" + String.join("；", guan)
                    + "。将军箭、阎王关、四季关、金锁关、鬼门关等按生年/月/时起例，异名异法并陈，"
                    + "宜结合格局旺衰与岁运综合断之（详参原著，起例表经 OBDA deity_rule 详查）。";
            // 典籍定向引据（第 29 步）：孕生男女（auspice_clause·孕生男女）
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
            String advice = good
                    ? "命局层次" + level + "，岁运多扶。宜守正持盈、戒骄戒躁，勿因得而忘形；积善以培福，居安而思危。"
                    : "命局层次" + level + "，岁运有阻。宜积德改行、反求诸己，悔过迁善以转祸为福；安命而自强，勿怨天尤人。"
                    + "";
            advice += "（溯《滴天髓·知命》：知命者不惑，顺其性而损益之。）";

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
            appendLine(sb, "干支关系", vars.get("ganzhiRelations"));
            appendLine(sb, "十神", vars.get("tenGods"));
            appendLine(sb, "神煞", vars.get("deities"));
            appendLine(sb, "空亡", vars.get("voidness"));
            appendLine(sb, "旺衰", vars.get("strength"));
            appendLine(sb, "格局", vars.get("patternResult"));
            appendLine(sb, "用神", vars.get("yongShen"));
            appendLine(sb, "调候", vars.get("tiaohou"));
            appendLine(sb, "层次", vars.get("chartLevel"));
            appendLine(sb, "岁运", vars.get("fortuneCycles"));
            appendLine(sb, "岁运评析", vars.get("fortuneAnalysis"));
            appendLine(sb, "应期", vars.get("timing"));
            appendLine(sb, "分类断", vars.get("categoryVerdicts"));
            appendLine(sb, "典籍征引", vars.get("clauseCitations"));
            appendLine(sb, "女命", vars.get("femaleVerdict"));
            appendLine(sb, "小儿", vars.get("childVerdict"));
            appendLine(sb, "合婚", vars.get("marriageConclusion"));
            appendLine(sb, "劝诫", vars.get("moralAdvice"));
            String reading = sb.toString();

            // 告警综合判据（程序编排；不设数值阈值）：大凶 / 寿元关 / 重大疾病
            String level = orDefault(str(vars.get("chartLevel")), "");
            String grade = orDefault(str(vars.get("patternGrade")), "");
            String strength = orDefault(str(vars.get("strength")), "");
            String deities = orDefault(str(vars.get("deities")), "");
            List<String> flags = new ArrayList<>();
            if (level.contains("贫贱") || (grade.contains("破") && strength.contains("弱"))) {
                flags.add("大凶");
            }
            if (deities.contains("羊刃") && deities.contains("相冲")) {
                flags.add("寿元关");
            }
            boolean alertTriggered = !flags.isEmpty();
            String flagText = alertTriggered ? String.join("/", flags) : "无";

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("reading", reading);
            result.put("alertTriggered", alertTriggered);
            completeResult(client, job, result);
            log.info("✅ aggregateReading | 告警标志={} | 命书 {} 字", flagText, reading.length());
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

    /** 具名格局（三命通会卷五等）：按格局类别匹配（pattern_catalog）。 */
    private List<Map<String, String>> clauseNamedPatterns(String patternClass) {
        return queryClauses("SELECT ?name ?meaning ?source WHERE { ?c a pattern:Pattern ; "
              + "pattern:patternClass \"" + patternClass + "\" ; pattern:patternName ?name ; "
              + "pattern:patternMeaning ?meaning ; pattern:patternSource ?source . }");
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

    /** 该五行是否扶助用神（同用神或生用神）。 */
    private static boolean supportsYong(int elem, int yongElem) {
        return elem == yongElem || generates(elem, yongElem);
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

    /** 是否逆用（不善神：偏官/伤官）——相神取「制用神者」、忌神取「生用神者」。（《子平真诠·论用神》顺逆） */
    private static boolean isNiYong(int[][] p) {
        String g = monthGodName(p);
        return "偏官".equals(g) || "伤官".equals(g);
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
            cnt[ZHI_ELEM[p[i][1]]]++;
        }
        int same = cnt[dayElem];
        int shengMe = cnt[(dayElem + 4) % 5];
        int guanSha = cnt[(dayElem + 3) % 5];
        int cai = cnt[(dayElem + 2) % 5];
        int shiShang = cnt[(dayElem + 1) % 5];

        // 专旺：同我+生我占压倒多数（≥6/8）且无官杀
        if (same + shengMe >= 6 && guanSha == 0) {
            String[] zhuan = {"曲直", "炎上", "稼穑", "从革", "润下"};
            return "专旺格（" + zhuan[dayElem] + "）";
        }
        // 从格：日主无根无助，某一类独旺（≥5/8）
        int tongGen = 0, deShi = 0;
        for (int[] pz : p) {
            for (String hs : ZHI_HIDDEN[pz[1]]) {
                if (GAN_ELEM[indexOf(GAN, hs)] == dayElem) {
                    tongGen++;
                }
            }
        }
        for (int i = 0; i < 4; i++) {
            if (i == 2) {
                continue;
            }
            int e = GAN_ELEM[p[i][0]];
            if (e == dayElem || generates(e, dayElem)) {
                deShi++;
            }
        }
        if (tongGen == 0 && deShi == 0) {
            if (cai >= 5) {
                return "从财格";
            }
            if (guanSha >= 5) {
                return "从杀格";
            }
            if (shiShang >= 5) {
                return "从儿格";
            }
        }
        // 化气格：日干与月干/时干五合、且化神得月令
        for (int j : new int[]{1, 3}) {
            int hua = ganWuHe(dayGan, p[j][0]);
            if (hua >= 0) {
                int huaElem = indexOf(ELEM_NAME, GAN_WUHE_HUA[hua]);
                if (huaElem == ZHI_ELEM[p[1][1]]) {
                    return "化气格（化" + GAN_WUHE_HUA[hua] + "）";
                }
            }
        }
        return "";
    }

    /** 两地支是否相刑（寅巳申/丑戌未三刑；子卯相刑；辰午酉亥自刑）。 */
    private static boolean xingPair(int a, int b) {
        for (int[] g : SANXING) {
            if (containsInt(g, a) && containsInt(g, b)) {
                return true;
            }
        }
        boolean ziMao = (a == 0 && b == 3) || (a == 3 && b == 0);
        boolean selfXing = a == b && (a == 4 || a == 6 || a == 9 || a == 11);
        return ziMao || selfXing;
    }

    /**
     * 大运流年·逐年评析：以本命用神/忌神为准，逐运判扶抑得失与主应事项（客户裁定：逐年评吉凶 + 应事）。
     */
    private String buildFortuneAnalysis(int[][] p, double startAge, String[] fortunes,
                                        int yongElem, int jiElem, int dayElem) {
        StringBuilder sb = new StringBuilder();
        sb.append("【大运逐运评析】用神").append(ELEM_NAME[yongElem]).append("（").append(elemRoleName(dayElem, yongElem))
                .append("），忌神").append(ELEM_NAME[jiElem]).append("（").append(elemRoleName(dayElem, jiElem)).append("）。\n");
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
            int good = (supportsYong(ge, yongElem) ? 1 : 0) + (supportsYong(ze, yongElem) ? 1 : 0)
                    - (ge == jiElem ? 1 : 0) - (ze == jiElem ? 1 : 0);
            String verdict = good >= 2 ? "吉（扶用神得力）" : (good <= -1 ? "凶（助忌损用）" : "平（得失相参）");
            sb.append(gz).append("运（").append(fmt(startAge + i * 10)).append("-").append(fmt(startAge + i * 10 + 10)).append("岁）：")
                    .append("干").append(GAN[g]).append(ELEM_NAME[ge]).append("（").append(elemRoleName(dayElem, ge)).append("）、")
                    .append("支").append(ZHI[z]).append(ELEM_NAME[ze]).append("（").append(elemRoleName(dayElem, ze)).append("），")
                    .append(verdict);
            if (pairIn(LIUCHONG, z, p[2][1])) {
                sb.append("；运支冲日支，主妻宫/事业动、居所变");
            } else if (pairIn(LIUHE, z, p[2][1])) {
                sb.append("；运支合日支，主姻缘/合作之事");
            }
            if (xingPair(z, p[2][1])) {
                sb.append("；运支与日支有刑，主是非/伤病宜慎");
            }
            sb.append("。\n");
        }
        int nowYear = LocalDate.now().getYear();
        sb.append("【流年（").append(nowYear).append("-").append(nowYear + 9).append("）】用神得扶之岁为吉、助忌之岁为凶：");
        for (int i = 0; i < 10; i++) {
            int y = nowYear + i;
            int g = floorMod(y - 4, 10);
            int z = floorMod(y - 4, 12);
            int ge = GAN_ELEM[g];
            int ze = ZHI_ELEM[z];
            int good = (supportsYong(ge, yongElem) ? 1 : 0) + (supportsYong(ze, yongElem) ? 1 : 0)
                    - (ge == jiElem ? 1 : 0) - (ze == jiElem ? 1 : 0);
            String verdict = good >= 1 ? "吉" : (good <= -1 ? "凶" : "平");
            sb.append(y).append(ganZhiFromIndex(floorMod(y - 4, 60))).append(":").append(verdict).append(" ");
        }
        sb.append("\n（细粒度：流月按流年干五虎遁列十二月建、流日按干支纪日 JDN 锚定、流时按日干五鼠遁起时；吉凶同流年用神扶抑法）");
        return sb.toString();
    }

    /**
     * 岁运辩证（按本命推导）：以本命用神/忌神 + 具体大运干支逐运判「扶用/助忌」，并给出成格/破格之义与主应。
     * 出处：《子平真诠·论行运成格变格》；三命通会卷二·论大运/论太岁。
     */
    private static String buildFortuneSynthesis(int[][] p, double startAge, String[] fortunes,
                                                int yongElem, int jiElem, int dayElem, String patternGrade) {
        StringBuilder sb = new StringBuilder();
        sb.append("【岁运辩证·凭本命用神】用神").append(ELEM_NAME[yongElem]).append("（").append(elemRoleName(dayElem, yongElem))
                .append("），忌神").append(ELEM_NAME[jiElem]).append("（").append(elemRoleName(dayElem, jiElem))
                .append("）；岁运先作用於用神、次及格局（《子平真诠·论行运成格变格》）。\n");
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
            int good = (supportsYong(ge, yongElem) ? 1 : 0) + (supportsYong(ze, yongElem) ? 1 : 0)
                    - (ge == jiElem ? 1 : 0) - (ze == jiElem ? 1 : 0);
            String action = good >= 2 ? "扶用得力，格愈成、事顺"
                    : (good <= -1 ? "助忌损用，格受摇、宜谨" : "得失相参，平中带变");
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
    private static String buildCategoryVerdicts(int[][] p, int dayGan, int dayElem, int monthBranch,
                                                Set<String> gods, String deities) {
        StringBuilder sb = new StringBuilder("分类事项断（按本命推导·逐条给依据）：\n");
        boolean hasGuan = gods.contains("正官") || gods.contains("偏官");
        boolean hasCai = gods.contains("正财") || gods.contains("偏财");
        boolean hasShiShang = gods.contains("食神") || gods.contains("伤官");

        // ① 事业功名
        sb.append("① 事业功名：本命官杀").append(hasGuan ? "现（十神见官/杀），官杀为功名之神" : "不显")
                .append(hasGuan ? "，故有功名之机——宜循官途、就公职或受约束之业，以官星得用为贵。"
                                : "，功名宜循他途，以食伤生财、以财求名（《渊海子平·论官星》）。")
                .append("\n");
        // ② 财运
        sb.append("② 财运：本命财星").append(hasCai ? "现，财为养命之源，主财源可求" : "不显")
                .append(hasCai ? "，宜以正业求财、忌比劫夺财；" : "，须靠食伤生财、以技艺生财；")
                .append(hasShiShang ? "又见食伤，食伤生财、财有源而不竭（《三命通会·论财》）。\n" : "食伤不显，财源赖他途。\n");
        // ③ 婚姻
        boolean dayChong = false, dayHe = false, dayHai = false;
        for (int i = 0; i < 4; i++) {
            if (i == 2) {
                continue;
            }
            if (pairIn(LIUCHONG, p[2][1], p[i][1])) {
                dayChong = true;
            }
            if (pairIn(LIUHE, p[2][1], p[i][1])) {
                dayHe = true;
            }
            if (pairIn(LIUHAI, p[2][1], p[i][1])) {
                dayHai = true;
            }
        }
        sb.append("③ 婚姻：男看财、女看官，日支").append(ZHI[p[2][1]]).append("为夫妻宫（配偶宫）；")
                .append(dayChong ? "本命日支逢冲，婚宫受动，主婚姻多波折/聚少离多，宜晚婚、慎择配。"
                        : dayHe ? "本命日支逢合，婚宫得合，主姻缘早动/易得婚配之助。"
                                : dayHai ? "本命日支逢害，婚宫受穿，主夫妻易生嫌隙，宜以柔化之。"
                                        : "本命日支无冲合，婚宫安稳，婚姻平顺。")
                .append("结合配偶宫十神与神煞取断。\n");
        // ④ 寿夭
        sb.append("④ 寿夭：日主").append(GAN[dayGan]).append(ELEM_NAME[dayElem]).append("之根基，看月令得气与通根；")
                .append(deities.contains("羊刃") ? "本命见羊刃，性刚易折，宜戒急用忍、慎防血光。" : "神煞无羊刃之虞，寿元根基尚稳。")
                .append("（忌刑冲羊刃、岁运并临；《三命通会·论羊刃》）。\n");
        // ⑤ 飘荡
        sb.append("⑤ 飘荡：看驿马、劫煞——")
                .append(deities.contains("驿马") ? "本命见驿马，主动荡迁远、宜外出求财或异地发展。" : "本命不见驿马，居处安稳、动静有常。")
                .append(deities.contains("劫煞") ? "又见劫煞，防财物耗散与外来之扰。" : "")
                .append("（《三命通会·论驿马/论劫煞》）。");
        return sb.toString();
    }

    /**
     * 具名格局命中判定（三命通会卷五/卷六等）：仅返回**本命确已命中构成条件**的具名格 {name, meaning, source}，
     * 供节点8在「原文仅作印证」之例下引用（避免无关原文堆砌）。★铁律8/9/10：各条构成条件均出自对应篇目；
     * 条件不可客观判定者不列入（不臆断）。
     */
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
        for (int i = 0; i < 4; i++) {
            for (String hs : ZHI_HIDDEN[p[i][1]]) {
                String hg = TEN_GOD[tenGodIndex(dayGan, indexOf(GAN, hs))];
                if ("正财".equals(hg) || "偏财".equals(hg)) {
                    hasCai = true;
                }
            }
        }

        if (containsStr(new String[]{"壬辰", "庚戌", "庚辰", "戊戌"}, dayPillar)) {
            hits.add(new String[]{"魁罡", "壬辰/庚戌/庚辰/戊戌魁罡之格", "三命通会卷六·魁罡"});
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
        // 日禄归时（三命通会卷六·日禄归时：「此格有七日：甲寅、丁午、戊巳、己午、庚申、壬亥、癸子」）——
        //   乙日卯时（己卯，时上偏财）、丙日巳时（癸巳，官星显露）、辛日酉时（丁酉，时上偏官）三者
        //   皆「不作归禄格看」，故日干须非乙（1）/丙（2）/辛（7）。
        if ("临官".equals(stageOf(dayGan, p[3][1])) && dayGan != 1 && dayGan != 2 && dayGan != 7) {
            hits.add(new String[]{"日禄归时", "日禄归于时支（七日之一；青云得路，喜忌篇「日禄归时没官星」）", "三命通会卷六·日禄归时"});
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
            //   「六壬生临午位…癸日坐向巳宫，乃是财官双美」）
            hits.add(new String[]{"财官双美", "自坐财官（壬午/癸巳）之格", "三命通会卷六·财官双美"});
        }
        // 专禄要制（三命通会卷五·专禄要制：「此格六庚日见巳时，庚金长生之地，内有丙戊二禄…柱要壬癸
        //   制丙」）——六庚日见巳时方是；旧按「日支临官（日坐禄）+有煞」误。
        if (dayGan == 6 && p[3][1] == 5) {
            hits.add(new String[]{"专禄要制", "六庚日见巳时、庚金长生、须制丙煞之格", "三命通会卷五·专禄要制"});
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
                + "再引六典籍（渊海子平/三命通会/滴天髓/穷通宝鉴/神峰通考/子平真诠）相应篇目的论断加以印证；"
                + "引文只作印证、勿整段照抄与本命无关之原文；不得编造出处。");
        return sb.toString();
    }

    /** 组装「白话解读」用户提示：以命书与已生成的命书详解为据，供大模型转写为通俗白话。 */
    private String buildInterpretPlainUserPrompt(String reading, String interpretation) {
        StringBuilder sb = new StringBuilder();
        sb.append("【四柱命书（节点16汇总）】\n").append(reading).append('\n');
        sb.append("【命书详解（古典，供你改写为白话）】\n").append(interpretation).append('\n');
        sb.append("\n请据此用通俗白话文重新讲一遍：分点讲清日主强弱、格局用神、事业/财运/婚姻/健康/岁运吉凶各是什么含义、"
                + "对命主意味着什么；对命书中已定的每个十神、格局、用神/忌神、神煞等术语，"
                + "逐项用白话说明它是什么、对命主意味着什么、有何影响；遇术语随即浅释，避免只堆名词；只作白话转述，不得编造出处。");
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
     * {calendar, year, month, day, hourZhi, minute}（前端 index.html 提交形态）。
     * <p>前端将历法置于 birth 对象内，时辰以十二地支给定时按 {@link #ZHI_START_HOUR} 取起始小时。
     */
    private static LocalDateTime resolveBirthDateTime(Object birthVar) {
        if (birthVar instanceof Map<?, ?> m) {
            Integer y = toInt(m.get("year"));
            Integer mo = toInt(m.get("month"));
            Integer da = toInt(m.get("day"));
            if (y == null || mo == null || da == null) {
                throw new IllegalArgumentException("无法解析生辰（缺年/月/日）：" + m);
            }
            int hh = zhiToStartHour(str(m.get("hourZhi")));
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
     * <p>支持两种输入：结构化对象 {@code {year, month, day, hourZhi, minute, leap?}}（前端提交形态，月份为农历月序、
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
            String z = str(m.get("hourZhi"));
            if (z != null && !z.isBlank()) {
                hh = zhiToStartHour(z);
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
        if (de == (te + 2) % 5) {
            return same ? 6 : 7;
        }
        return 0;
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

    /** 天干对地支的寄生十二宫。 */
    private static String stageOf(int gan, int zhi) {
        int cs = CHANGSHENG_ZHI[gan];
        int stage = (GAN_YY[gan] == 1) ? floorMod(zhi - cs, 12) : floorMod(cs - zhi, 12);
        return STAGE_NAME[stage];
    }

    /** 纳音（内置表；第 11 步可经 OBDA `nayin` 表替换）。出处：三命通会卷一·总论纳音。 */
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
