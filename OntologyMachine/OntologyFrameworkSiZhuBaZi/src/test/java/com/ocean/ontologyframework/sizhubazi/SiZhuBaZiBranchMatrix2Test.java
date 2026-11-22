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
 * 四柱命理 · 分支矩阵补充（第二阶段）· 下游节点与三排他网关分支覆盖（纯离线）。
 *
 * <p>第一批 {@link SiZhuBaZiBranchMatrixTest} 覆盖算理主链（干支关系/定格/用神/纳音/大运/告警/历法）；
 * 本类补齐此前未系统触及的<b>下游节点/网关</b>分支：
 * <ul>
 *   <li>层次四档（judgeChartLevel）：贵格（官星有理会）/ 富格（财气通门户）/ 贫格（财旺身弱）/ 富贵两全（专旺）；</li>
 *   <li>合婚（analyzeMarriageMatch，G1 合婚网关）：互补与三维合参输出；</li>
 *   <li>女命专论（analyzeFemaleChart，G2 女命网关）：良类（纯/清）与劣·特类（滥/浊·正偏自处）；</li>
 *   <li>小儿关杀（analyzeChildChart）：时值驿马分支；</li>
 *   <li>劝诫（generateMoralAdvice）：吉（守正持盈）/ 凶（积德改行）两分支；</li>
 *   <li>外发告警（sendAlert，G3 层次网关）：无告警跳过分支；</li>
 *   <li>落库返回（persistAndReturn）：离线（backendService==null）仅返回 caseRecord 分支；</li>
 *   <li>应期人生阶段（inferTimingAndStages）/ 岁运综合辩证（synthesizeFortuneChart）/ 分类断语（generateCategoryVerdicts）。</li>
 * </ul>
 *
 * <p>断言一律以程序结构化字段为唯一真相源；受控入参经真跑生产 Handler（Mockito 模拟
 * ActivatedJob/JobClient 承接 completeResult），不反向解析判词、不伪造断言。
 */
@DisplayName("四柱命理 · 分支矩阵补充（第二阶段·下游节点与网关）")
class SiZhuBaZiBranchMatrix2Test {

    private static final Class<?> WORKER = SiZhuBaZiOntologyJobWorker.class;

    // ============================================================ 反射 / 工具

    private static SiZhuBaZiOntologyJobWorker newWorker() throws Exception {
        Constructor<?> c = WORKER.getDeclaredConstructor();
        c.setAccessible(true);
        return (SiZhuBaZiOntologyJobWorker) c.newInstance();
    }

    /** Mockito 应答：承接 completeResult 写入的 variables，其余方法返回类型安全的桩对象。 */
    private static final class Cap implements Answer<Object> {
        Object vars;

        @Override
        public Object answer(InvocationOnMock inv) {
            Method m = inv.getMethod();
            if ("variables".equals(m.getName()) && inv.getArguments().length == 1) {
                vars = inv.getArgument(0);
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
                if (rt == float.class) {
                    return 0f;
                }
                if (rt == short.class) {
                    return (short) 0;
                }
                if (rt == byte.class) {
                    return (byte) 0;
                }
                if (rt == char.class) {
                    return (char) 0;
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
    private static Map<String, Object> run(String handler, Map<String, Object> vars) throws Exception {
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

    // ============================================================ 一、层次四档（judgeChartLevel）

    private static Map<String, Object> levelVars(String four, String grade, String strength) {
        return map("fourPillars", four, "patternGrade", grade, "strength", strength);
    }

    @Test
    @DisplayName("层次·贵格（官星有理会）：官星当令有力、身能任官")
    void levelGuiGe() throws Exception {
        String level = str(run("handleJudgeChartLevel",
                levelVars("甲子 辛酉 甲子 甲子", "格成", "身强（得令）")).get("chartLevel"));
        assertThat(level).as("贵格·官星有理会").contains("贵格").contains("官星有理会");
    }

    @Test
    @DisplayName("层次·富格（财气通门户）：财星当令有力、身强任财、官不显")
    void levelFuGe() throws Exception {
        String level = str(run("handleJudgeChartLevel",
                levelVars("甲子 戊辰 甲子 甲子", "格成", "身强（得令）")).get("chartLevel"));
        assertThat(level).as("富格·财气通门户").contains("富格").contains("财气通门户");
    }

    @Test
    @DisplayName("层次·贫格（财神反不真）：财旺而身弱不任财")
    void levelPinGe() throws Exception {
        String level = str(run("handleJudgeChartLevel",
                levelVars("甲子 戊辰 甲子 甲子", "格成", "身弱（失令、无根、寡助）")).get("chartLevel"));
        assertThat(level).as("贫格·财旺身弱").contains("贫格");
    }

    @Test
    @DisplayName("层次·富贵两全（专旺成象）：格成且专旺 → 得局得格、气势成象")
    void levelZhuanWangFuGui() throws Exception {
        String level = str(run("handleJudgeChartLevel",
                levelVars("甲子 甲子 甲子 甲子", "格成（专旺成象）", "身强（得令）")).get("chartLevel"));
        assertThat(level).as("富贵两全·专旺").contains("富贵两全").contains("成象");
    }

    // ============================================================ 二、合婚（G1 合婚网关）

    @Test
    @DisplayName("合婚·双人排盘与三维合参（纳音/生肖/神煞）输出齐备")
    void marriageMatch() throws Exception {
        Map<String, Object> partner = new LinkedHashMap<>();
        partner.put("year", 1993);
        partner.put("month", 7);
        partner.put("day", 18);
        partner.put("hour", 15);
        Map<String, Object> r = run("handleMarriageMatch", map(
                "chartBase", "SOLAR=1990-05-15T10:00:00;GENDER=男;SUBJECT=男命;MODE=合婚",
                "partnerBirth", partner));
        assertThat(str(r.get("marriageConclusion"))).as("合婚结论").contains("合婚结论");
        String comp = str(r.get("complementAnalysis"));
        assertThat(comp).as("本人四柱").contains("本人四柱");
        assertThat(comp).as("对方四柱").contains("对方四柱");
        assertThat(comp).as("神煞合参").contains("神煞合参");
        assertThat(str(r.get("nayinRelation"))).as("纳音").contains("年命纳音");
        assertThat(str(r.get("zodiacRelation"))).as("生肖").contains("生肖合冲");
    }

    // ============================================================ 三、女命专论（G2 女命网关）

    @Test
    @DisplayName("女命·良类：官星透干不杂、身不弱、食伤不旺 → 主类「纯」")
    void femalePure() throws Exception {
        String v = str(run("handleFemaleChart",
                map("fourPillars", "辛酉 辛酉 甲子 戊辰", "strength", "身强（得令）", "gender", "女"))
                .get("femaleVerdict"));
        assertThat(v).contains("女命专论").contains("主类：纯");
    }

    @Test
    @DisplayName("女命·劣·特类：官杀混杂、身不弱 → 主类「滥」，兼「浊·正偏自处」")
    void femaleZhuoSpecial() throws Exception {
        String v = str(run("handleFemaleChart",
                map("fourPillars", "辛酉 庚申 甲子 甲子", "strength", "身强（得令）", "gender", "女"))
                .get("femaleVerdict"));
        assertThat(v).contains("主类：滥").contains("正偏自处");
    }

    // ============================================================ 四、小儿关杀

    @Test
    @DisplayName("小儿·年支申、时支寅 → 时值驿马关")
    void childYimaGuan() throws Exception {
        String v = str(run("handleChildChart",
                map("fourPillars", "甲申 甲寅 甲子 甲寅")).get("childVerdict"));
        assertThat(v).contains("小儿关杀").contains("本命年支申").contains("驿马");
    }

    // ============================================================ 五、劝诫（吉/凶分支）

    @Test
    @DisplayName("劝诫·命局层次含贵/富 → 守正持盈（吉）分支")
    void moralGood() throws Exception {
        String a = str(run("handleMoralAdvice",
                map("chartLevel", "贵格（官星有理会：官星有力、身能任官）", "fortuneTrend", "顺")).get("moralAdvice"));
        assertThat(a).contains("守正持盈").contains("贵格");
    }

    @Test
    @DisplayName("劝诫·命局层次非贵富 → 积德改行（凶）分支")
    void moralBad() throws Exception {
        String a = str(run("handleMoralAdvice",
                map("chartLevel", "平常偏下（格破·待岁运救应）", "fortuneTrend", "滞")).get("moralAdvice"));
        assertThat(a).contains("积德改行");
    }

    // ============================================================ 六、外发告警（G3 层次网关）

    @Test
    @DisplayName("告警外发·alertTriggered=false → SKIPPED_NO_ALERT（不触达 MQ）")
    void sendAlertSkipped() throws Exception {
        String st = str(run("handleSendAlert", map("alertTriggered", false)).get("messageStatus"));
        assertThat(st).isEqualTo("SKIPPED_NO_ALERT");
    }

    // ============================================================ 七、落库返回（离线分支）

    @Test
    @DisplayName("落库返回·backendService=null（离线）→ 不落库，仅返回 caseRecord/response")
    void persistOffline() throws Exception {
        Map<String, Object> r = run("handlePersistAndReturn", map(
                "chartBase", "SOLAR=1990-05-15T10:00:00;GENDER=男;SUBJECT=男命;MODE=单命;PLACE=北京",
                "fourPillars", "庚午 辛巳 甲子 己巳",
                "reading", "【四柱命理命书】示例"));
        String caseRecord = str(r.get("caseRecord"));
        assertThat(caseRecord).as("命例记录").contains("命例").contains("四柱：庚午 辛巳 甲子 己巳");
        assertThat(str(r.get("response"))).as("响应状态").contains("COMPLETED");
    }

    // ============================================================ 八、应期 / 岁运综合 / 分类断语

    @Test
    @DisplayName("应期与人生阶段·应期取用与四柱分主阶段输出")
    void timingStages() throws Exception {
        Map<String, Object> r = run("handleTimingAndStages",
                map("fourPillars", "甲子 乙丑 甲寅 丁卯", "voidness", "年空戌亥"));
        assertThat(str(r.get("timing"))).as("应期取用").contains("应期取用");
        String stages = str(r.get("lifeStages"));
        assertThat(stages).as("年柱主少年").contains("年柱主少年");
        assertThat(stages).as("时柱主老年").contains("时柱主老年");
    }

    @Test
    @DisplayName("岁运综合辩证·成格变格注记与逐运趋势输出")
    void synthesizeFortune() throws Exception {
        Map<String, Object> r = run("handleSynthesizeFortuneChart", map(
                "fourPillars", "甲子 乙丑 甲寅 丁卯",
                "strength", "身强（得令）",
                "startAge", 8.0,
                "fortuneSequence", "甲子,乙丑,丙寅,丁卯",
                "chartLevel", "平常格（格成而财官未通门户）",
                "patternGrade", "格成"));
        assertThat(str(r.get("transformationNote"))).as("成格变格").contains("成格变格");
        assertThat(str(r.get("fortuneTrend"))).as("逐运趋势").isNotBlank();
    }

    @Test
    @DisplayName("分类断语·性情才德/五脏六腑/六亲/分类事项齐备")
    void categoryVerdicts() throws Exception {
        String v = str(run("handleCategoryVerdicts", map(
                "fourPillars", "甲子 乙丑 甲寅 丁卯",
                "strength", "身强（得令）",
                "gender", "男",
                "deities", "")).get("categoryVerdicts"));
        assertThat(v).contains("性情才德").contains("五脏六腑");
        assertThat(v).contains("六亲");
    }
}
