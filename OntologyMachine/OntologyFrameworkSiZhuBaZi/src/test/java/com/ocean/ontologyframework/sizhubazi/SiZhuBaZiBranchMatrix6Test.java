package com.ocean.ontologyframework.sizhubazi;

import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.JobClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 四柱命理 · 分支矩阵补充（第六阶段）· 输入解析 / 排四柱 / 岁运综合 / 分类断语 / 劝诫 / 汇总编排（纯离线）。
 *
 * <p>前五批覆盖算理主链与各分析节点内部算理；本类补齐此前仅在端到端流程中隐性跑过、
 * 未做分支级断言的四类「输入/输出编排」节点：
 * <ul>
 *   <li>生辰解析（handleParseBirthInput）：chartBase 组装、真太阳时校正、早晚子时、农历换算成功/越界【待考】、缺参负向。</li>
 *   <li>排四柱（handleCastFourPillars）：四柱结构、子初换日、起运顺逆（阳男阴女顺 / 阴男阳女逆）。</li>
 *   <li>岁运综合（handleSynthesizeFortuneChart）：岁运辩证 trend + 成格变格注记。</li>
 *   <li>分类断语（handleCategoryVerdicts）：性情才德/疾病/六亲吉凶/分类事项断四段编排。</li>
 *   <li>劝诫（handleMoralAdvice）：贵富 / 贫贱 两支劝勉。</li>
 *   <li>综合汇总（handleAggregateReading）：命书拼装 + 告警 javaFlags（大凶 / 寿元关 / 无）。</li>
 * </ul>
 *
 * <p>断言以程序结构化字段为唯一真相源；Handler 经 Mockito 承接 completeResult 读回 result，失败路径承接 newThrowErrorCommand 的 errorCode。
 */
@DisplayName("四柱命理 · 分支矩阵补充（第六阶段·输入解析/排四柱/岁运综合/分类断语/劝诫/汇总）")
class SiZhuBaZiBranchMatrix6Test {

    private static final Class<?> WORKER = SiZhuBaZiOntologyJobWorker.class;

    private static SiZhuBaZiOntologyJobWorker newWorker() throws Exception {
        Constructor<?> c = WORKER.getDeclaredConstructor();
        c.setAccessible(true);
        return (SiZhuBaZiOntologyJobWorker) c.newInstance();
    }

    private static final class Cap implements Answer<Object> {
        Object vars;
        String errorCode;

        @Override
        public Object answer(InvocationOnMock inv) {
            Method m = inv.getMethod();
            if ("variables".equals(m.getName()) && inv.getArguments().length == 1) {
                vars = inv.getArgument(0);
            }
            if ("errorCode".equals(m.getName()) && inv.getArguments().length == 1) {
                errorCode = String.valueOf((Object) inv.getArgument(0));
            }
            Class<?> rt = m.getReturnType();
            if (rt == void.class) {
                return null;
            }
            if (rt.isPrimitive()) {
                if (rt == long.class) {
                    return 0L;
                }
                if (rt == int.class) {
                    return 0;
                }
                if (rt == boolean.class) {
                    return false;
                }
                if (rt == double.class) {
                    return 0d;
                }
                return null;
            }
            try {
                return mock(rt, this);
            } catch (Throwable t) {
                return null;
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> result(String handler, Map<String, Object> vars) throws Exception {
        SiZhuBaZiOntologyJobWorker w = newWorker();
        Cap cap = new Cap();
        JobClient client = mock(JobClient.class, cap);
        ActivatedJob job = mock(ActivatedJob.class);
        when(job.getVariablesAsMap()).thenReturn(vars);
        when(job.getKey()).thenReturn(1L);
        when(job.getProcessInstanceKey()).thenReturn(1L);
        Method m = WORKER.getMethod(handler, ActivatedJob.class, JobClient.class);
        m.invoke(w, job, client);
        assertThat(cap.vars).as("handler 应调用 completeResult：%s", handler).isNotNull();
        return (Map<String, Object>) ((Map<String, Object>) cap.vars).get("result");
    }

    private static String errorCodeOf(String handler, Map<String, Object> vars) throws Exception {
        SiZhuBaZiOntologyJobWorker w = newWorker();
        Cap cap = new Cap();
        JobClient client = mock(JobClient.class, cap);
        ActivatedJob job = mock(ActivatedJob.class);
        when(job.getVariablesAsMap()).thenReturn(vars);
        when(job.getKey()).thenReturn(1L);
        when(job.getProcessInstanceKey()).thenReturn(1L);
        Method m = WORKER.getMethod(handler, ActivatedJob.class, JobClient.class);
        m.invoke(w, job, client);
        assertThat(cap.vars).as("失败路径不应调用 completeResult：%s", handler).isNull();
        return cap.errorCode;
    }

    private static Map<String, Object> map(Object... kv) {
        Map<String, Object> v = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            v.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return v;
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private static String chartBase(String handler, Map<String, Object> vars) throws Exception {
        return str(result(handler, vars).get("chartBase"));
    }

    // ============================================================ 一、生辰解析（handleParseBirthInput）

    @Test
    @DisplayName("生辰解析·chartBase 组装：SOLAR/GENDER/SUBJECT/MODE/GRAN + 公历标记")
    void parseBirthBasicChartBase() throws Exception {
        Map<String, Object> r = result("handleParseBirthInput", map(
                "birth", "1990-05-15 08:30",
                "gender", "女",
                "subjectType", "女命",
                "inputMode", "单命",
                "granularity", "月"));
        String cb = str(r.get("chartBase"));
        assertThat(cb).contains("SOLAR=1990-05-15T08:30")
                .contains("GENDER=女")
                .contains("SUBJECT=女命")
                .contains("MODE=单命")
                .contains("GRAN=月")
                .contains("LATEZI=false")
                .contains("LUNAR_UNCONVERTED=false");
        assertThat(str(r.get("subjectType"))).isEqualTo("女命");
        assertThat(str(r.get("inputMode"))).isEqualTo("单命");
    }

    @Test
    @DisplayName("生辰解析·早晚子时：23:xx → LATEZI=true")
    void parseBirthLateZi() throws Exception {
        String cb = chartBase("handleParseBirthInput", map("birth", "1990-05-15 23:30"));
        assertThat(cb).contains("SOLAR=1990-05-15T23:30").contains("LATEZI=true");
    }

    @Test
    @DisplayName("生辰解析·真太阳时校正：东经116.4 → 时差 -0.24h，12:00 → 11:45:36")
    void parseBirthTrueSolarTime() throws Exception {
        String cb = chartBase("handleParseBirthInput", map(
                "birth", "1990-05-15 12:00",
                "birthPlace", "东经116.4"));
        assertThat(cb).contains("TST=-0.239").contains("SOLAR=1990-05-15T11:45:36").contains("PLACE=东经116.4");
    }

    @Test
    @DisplayName("生辰解析·农历换算成功：1990 五月十五 → 公历（LUNAR_UNCONVERTED=false）")
    void parseBirthLunarConvert() throws Exception {
        Map<String, Object> birth = map("calendar", "农历", "year", 1990, "month", 5, "day", 15, "hour", 12);
        String cb = chartBase("handleParseBirthInput", map("birth", birth));
        assertThat(cb).contains("LUNAR_UNCONVERTED=false").contains("SOLAR=1990-06-07T12:00");
    }

    @Test
    @DisplayName("生辰解析·农历越界【待考】：1899 → LUNAR_UNCONVERTED=true 且保留原值")
    void parseBirthLunarUnconverted() throws Exception {
        Map<String, Object> birth = map("calendar", "农历", "year", 1899, "month", 5, "day", 15, "hour", 12);
        String cb = chartBase("handleParseBirthInput", map("birth", birth));
        assertThat(cb).contains("LUNAR_UNCONVERTED=true").contains("SOLAR=1899-05-15T12:00");
    }

    @Test
    @DisplayName("生辰解析·缺参负向：birth 缺失/空串 → PARSE_BIRTH_INPUT_FAILED")
    void parseBirthMissingFails() throws Exception {
        assertThat(errorCodeOf("handleParseBirthInput", map())).isEqualTo("PARSE_BIRTH_INPUT_FAILED");
        assertThat(errorCodeOf("handleParseBirthInput", map("birth", ""))).isEqualTo("PARSE_BIRTH_INPUT_FAILED");
    }

    // ============================================================ 二、排四柱（handleCastFourPillars）

    @Test
    @DisplayName("排四柱·结构：四柱四字 + 大运/小运各 8 步 + 胎元/命宫非空")
    void castFourPillarsStructure() throws Exception {
        Map<String, Object> r = result("handleCastFourPillars", map(
                "chartBase", "SOLAR=1990-05-15T08:30;GENDER=男;LATEZI=false"));
        String fp = str(r.get("fourPillars"));
        assertThat(fp.split(" ")).as("四柱四字").hasSize(4);
        assertThat(str(r.get("fortuneSequence")).split(",")).as("大运 8 步").hasSize(8);
        assertThat(str(r.get("minorFortunes")).split(",")).as("小运 8 步").hasSize(8);
        assertThat(str(r.get("fetalOrigin"))).as("胎元").hasSize(2);
        assertThat(str(r.get("lifePalace"))).as("命宫").hasSize(2);
        assertThat(Double.parseDouble(str(r.get("startAge")))).as("起运岁非负").isGreaterThanOrEqualTo(0.0);
    }

    @Test
    @DisplayName("排四柱·子初换日：LATEZI=true 相比 false，日柱（第 3 字）前进一位")
    void castLateZiDayAdvances() throws Exception {
        String no = str(result("handleCastFourPillars", map(
                "chartBase", "SOLAR=1990-05-15T23:30;GENDER=男;LATEZI=false")).get("fourPillars"));
        String yes = str(result("handleCastFourPillars", map(
                "chartBase", "SOLAR=1990-05-15T23:30;GENDER=男;LATEZI=true")).get("fourPillars"));
        assertThat(no.split(" ")).hasSize(4);
        assertThat(yes.split(" ")).hasSize(4);
        assertThat(yes.split(" ")[2]).as("换日后日柱").isNotEqualTo(no.split(" ")[2]);
    }

    @Test
    @DisplayName("排四柱·起运顺逆：阳年（庚午）男顺女逆，首运干支不同")
    void castDirectionByGender() throws Exception {
        String male = str(result("handleCastFourPillars", map(
                "chartBase", "SOLAR=1990-05-15T08:30;GENDER=男;LATEZI=false")).get("fortuneSequence"));
        String female = str(result("handleCastFourPillars", map(
                "chartBase", "SOLAR=1990-05-15T08:30;GENDER=女;LATEZI=false")).get("fortuneSequence"));
        assertThat(male.split(",")).hasSize(8);
        assertThat(female.split(",")).hasSize(8);
        assertThat(male.split(",")[0]).as("阳年男顺 vs 女逆 · 首运不同").isNotEqualTo(female.split(",")[0]);
    }

    @Test
    @DisplayName("排四柱·起运顺逆：阴年（辛丑）男逆女顺，首运不同（补阴年分支）")
    void castDirectionYinYear() throws Exception {
        String male = str(result("handleCastFourPillars", map(
                "chartBase", "SOLAR=2021-05-15T08:30;GENDER=男;LATEZI=false")).get("fortuneSequence"));
        String female = str(result("handleCastFourPillars", map(
                "chartBase", "SOLAR=2021-05-15T08:30;GENDER=女;LATEZI=false")).get("fortuneSequence"));
        assertThat(male.split(",")).hasSize(8);
        assertThat(female.split(",")).hasSize(8);
        assertThat(male.split(",")[0]).as("阴年男逆 vs 女顺 · 首运不同").isNotEqualTo(female.split(",")[0]);
    }

    // ============================================================ 三、岁运综合（handleSynthesizeFortuneChart）

    @Test
    @DisplayName("岁运综合·岁运辩证 + 成格变格注记（含格局/用神/忌神/层次）")
    void synthesizeTransformation() throws Exception {
        Map<String, Object> r = result("handleSynthesizeFortuneChart", map(
                "fourPillars", "甲子 丙寅 戊午 庚申",
                "strength", "身强（得令）",
                "startAge", "3",
                "fortuneSequence", "甲子,乙丑,丙寅",
                "chartLevel", "贵格",
                "patternGrade", "正官格"));
        String trend = str(r.get("fortuneTrend"));
        assertThat(trend).contains("【岁运辩证·凭本命用神】").contains("本命格局基线：正官格");
        String note = str(r.get("transformationNote"));
        assertThat(note).contains("成格变格：本命格局「正官格」").contains("用神").contains("忌神")
                .contains("命局层次：贵格").contains("岁运辩证");
    }

    // ============================================================ 四、分类断语（handleCategoryVerdicts）

    @Test
    @DisplayName("分类断语·四段编排：性情才德 / 疾病 / 六亲吉凶 / 分类事项断")
    void categoryVerdictsSections() throws Exception {
        String cv = str(result("handleCategoryVerdicts", map(
                "fourPillars", "甲子 丙寅 戊午 庚申",
                "strength", "身强（得令）",
                "gender", "男",
                "deities", "")).get("categoryVerdicts"));
        assertThat(cv).contains("性情才德：日主").contains("疾病（五脏六腑所属干支）：")
                .contains("六亲吉凶（按宫位").contains("分类事项断（按本命推导")
                .contains("性情随格局成败与岁运而分。");
    }

    // ============================================================ 五、劝诫（handleMoralAdvice）

    @Test
    @DisplayName("劝诫·贵富 / 贫贱 两支：岁运多扶·守正持盈 vs 岁运有阻·积德改行")
    void moralAdviceGoodBad() throws Exception {
        String good = str(result("handleMoralAdvice", map("chartLevel", "贵格", "fortuneTrend", "x")).get("moralAdvice"));
        assertThat(good).contains("命局层次贵格").contains("岁运多扶").contains("宜守正持盈");
        String bad = str(result("handleMoralAdvice", map("chartLevel", "贫格", "fortuneTrend", "x")).get("moralAdvice"));
        assertThat(bad).contains("命局层次贫格").contains("岁运有阻").contains("宜积德改行");
    }

    // ============================================================ 六、综合汇总（handleAggregateReading）

    private static Map<String, Object> aggregateVars(String chartLevel, String patternGrade, String strength, String deities) {
        return map(
                "chartBase", "SOLAR=1990-05-15T08:30;GENDER=男;SUBJECT=男命;MODE=单命",
                "fourPillars", "甲子 丙寅 戊午 庚申",
                "chartLevel", chartLevel,
                "patternGrade", patternGrade,
                "strength", strength,
                "deities", deities);
    }

    @Test
    @DisplayName("汇总·大凶告警：层次含「贫贱」→ alertTriggered=true，命书含四柱行")
    void aggregateAlertDaXiong() throws Exception {
        Map<String, Object> r = result("handleAggregateReading",
                aggregateVars("贫贱格", "正官格", "身强（得令）", ""));
        assertThat(str(r.get("reading"))).contains("【四柱命理命书】").contains("四柱：甲子 丙寅 戊午 庚申");
        assertThat((Boolean) r.get("alertTriggered")).as("贫贱 → 大凶").isTrue();
    }

    @Test
    @DisplayName("汇总·寿元关告警：神煞含「羊刃」且「相冲」→ alertTriggered=true")
    void aggregateAlertShouYuan() throws Exception {
        Map<String, Object> r = result("handleAggregateReading",
                aggregateVars("贵格", "正官格", "身强（得令）", "羊刃 相冲"));
        assertThat((Boolean) r.get("alertTriggered")).as("羊刃逢冲 → 寿元关").isTrue();
    }

    @Test
    @DisplayName("汇总·无告警：贵格身强无羊刃冲 → alertTriggered=false")
    void aggregateNoAlert() throws Exception {
        Map<String, Object> r = result("handleAggregateReading",
                aggregateVars("贵格", "正官格", "身强（得令）", ""));
        assertThat((Boolean) r.get("alertTriggered")).as("无告警").isFalse();
    }
}
