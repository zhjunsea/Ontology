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
 * 四柱命理 · 分支矩阵补充（第五阶段）· 干支关系全类 / 纳音藏干十二长生 / 大运流年结构 / 应期运限（纯离线）。
 *
 * <p>前四批覆盖算理主链、下游网关、神煞成败具名格局、十神宫位·旺衰六档·取用多法；本类补齐四处仍可加深的维度：
 * <ul>
 *   <li>干支关系（analyzeGanzhiRelations）：天干五合 / 地支六合·六冲·六穿 / 三合成局·半合 /
 *       三刑·自刑·子卯刑 / 六破·墓库 / 无明显。</li>
 *   <li>纳音藏干（analyzeNayinHiddenStems）：纳音名（六十甲子表）/ 地支藏干 / 十二长生 / 日柱特性。</li>
 *   <li>大运流年（analyzeFortuneCycles）：年粒度结构、日/时粒度追加流日流时、岁运特殊格局 + 逐运评析。</li>
 *   <li>应期运限（inferTimingAndStages）：应期四法文本、运限四柱主阶段与干支。</li>
 * </ul>
 *
 * <p>断言以程序结构化字段为唯一真相源：Handler 经 Mockito 承接 completeResult 读回 result。
 */
@DisplayName("四柱命理 · 分支矩阵补充（第五阶段·干支关系/纳音藏干/大运流年/应期运限）")
class SiZhuBaZiBranchMatrix5Test {

    private static final Class<?> WORKER = SiZhuBaZiOntologyJobWorker.class;

    private static final String[] GAN = {"甲", "乙", "丙", "丁", "戊", "己", "庚", "辛", "壬", "癸"};
    private static final String[] ZHI = {"子", "丑", "寅", "卯", "辰", "巳", "午", "未", "申", "酉", "戌", "亥"};

    // ============================================================ 反射 / 工具

    private static SiZhuBaZiOntologyJobWorker newWorker() throws Exception {
        Constructor<?> c = WORKER.getDeclaredConstructor();
        c.setAccessible(true);
        return (SiZhuBaZiOntologyJobWorker) c.newInstance();
    }

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

    private static String gz(String fourPillars) throws Exception {
        return str(run("handleGanzhiRelations", map("fourPillars", fourPillars)).get("ganzhiRelations"));
    }

    // ============================================================ 一、干支关系（analyzeGanzhiRelations）

    @Test
    @DisplayName("干支·天干五合 / 地支六合 / 六冲")
    void ganzhiWuHeLiuHeLiuChong() throws Exception {
        assertThat(gz("甲寅 己卯 甲子 甲子")).as("甲己合").contains("年甲 合 月己").contains("合而未必化");
        assertThat(gz("甲子 乙丑 甲寅 甲寅")).as("子丑六合").contains("年子 六合 月丑");
        assertThat(gz("甲子 庚午 甲寅 甲寅")).as("子午相冲").contains("年子 相冲 月午");
    }

    @Test
    @DisplayName("干支·地支六穿 / 三合成局 / 半合")
    void ganzhiLiuChuanSanheBanhe() throws Exception {
        assertThat(gz("甲子 乙未 甲寅 甲寅")).as("子未相穿").contains("年子 相穿 月未");
        assertThat(gz("甲申 甲子 甲辰 甲寅")).as("申子辰成局").contains("三合局 申子辰");
        assertThat(gz("甲申 甲子 甲寅 甲寅")).as("申子半合").contains("半合 申子");
    }

    @Test
    @DisplayName("干支·三刑（寅巳申）/ 自刑（辰辰）/ 子卯相刑")
    void ganzhiSanxingZixingZimao() throws Exception {
        assertThat(gz("甲寅 乙巳 甲寅 甲寅")).as("寅巳三刑 2/3").contains("三刑（2/3）");
        assertThat(gz("甲辰 甲辰 甲寅 甲寅")).as("辰辰自刑").contains("辰辰自刑");
        assertThat(gz("甲子 乙卯 甲寅 甲寅")).as("子卯相刑").contains("子卯相刑");
    }

    @Test
    @DisplayName("干支·六破（卯午）/ 墓库（辰）/ 无明显关系")
    void ganzhiLiupoTombNone() throws Exception {
        assertThat(gz("丁卯 甲午 甲寅 甲寅")).as("卯午相破").contains("卯午相破");
        assertThat(gz("甲辰 甲子 甲寅 甲寅")).as("墓库土").contains("墓库土");
        assertThat(gz("甲子 甲子 甲子 甲子")).as("无明显").isEqualTo("四柱无明显合冲刑穿");
    }

    // ============================================================ 二、纳音藏干 / 十二长生（analyzeNayinHiddenStems）

    @Test
    @DisplayName("纳音·六十甲子表 + 地支藏干：四柱纳音名与藏干串")
    void nayinHiddenStems() throws Exception {
        Map<String, Object> r = run("handleNayinHiddenStems", map("fourPillars", "甲子 丙寅 戊午 庚申"));
        String nayin = str(r.get("nayin"));
        assertThat(nayin).as("年甲子海中金").contains("年甲子→海中金");
        assertThat(nayin).as("月丙寅炉中火").contains("月丙寅→炉中火");
        assertThat(nayin).as("时庚申石榴木").contains("时庚申→石榴木");
        String hidden = str(r.get("hiddenStems"));
        assertThat(hidden).as("年子藏癸").contains("年甲子藏癸");
        assertThat(hidden).as("月寅藏甲丙戊").contains("月丙寅藏甲丙戊");
        assertThat(hidden).as("日午藏丁己").contains("日戊午藏丁己");
    }

    @Test
    @DisplayName("十二长生·日主在四柱各支之长生十二宫 + 日柱特性")
    void twelveStagesTraits() throws Exception {
        Map<String, Object> r = run("handleNayinHiddenStems", map("fourPillars", "甲子 丙寅 戊午 庚申"));
        String traits = str(r.get("dayPillarTraits"));
        assertThat(traits).as("日主戊土").contains("日主戊土");
        assertThat(traits).as("日支午生肖马藏丁己").contains("日支午（马）藏丁己");
        assertThat(traits).as("十二长生标题").contains("十二长生");
        assertThat(traits).as("戊在寅为长生").contains("月寅长生");
        assertThat(traits).as("戊在午为帝旺").contains("日午帝旺");
    }

    // ============================================================ 三、大运流年（analyzeFortuneCycles）

    private static Map<String, Object> fortuneVars(String granularity) {
        return map("fourPillars", "甲子 丙寅 戊午 庚申",
                "chartBase", "SOLAR=1990-05-15T10:00;GENDER=男",
                "fortuneSequence", "甲子,乙丑,丙寅",
                "startAge", "3",
                "granularity", granularity,
                "strength", "身强（得令）");
    }

    @Test
    @DisplayName("大运流年·年粒度：大运年龄段 + 流年 + 流月，且不追加流日流时")
    void fortuneCyclesYearGranularity() throws Exception {
        String fc = str(run("handleFortuneCycles", fortuneVars("年")).get("fortuneCycles"));
        assertThat(fc).as("大运起运").contains("大运（起运");
        assertThat(fc).as("首运甲子").contains("甲子（");
        assertThat(fc).as("流年").contains("流年（年级");
        assertThat(fc).as("流月").contains("流月");
        assertThat(fc).as("年粒度不追加流日流时").doesNotContain("流日/流时");
    }

    @Test
    @DisplayName("大运流年·日/时粒度：追加流日/流时（JDN 锚定）+ 十二时（五鼠遁）")
    void fortuneCyclesDayHourGranularity() throws Exception {
        String day = str(run("handleFortuneCycles", fortuneVars("日")).get("fortuneCycles"));
        assertThat(day).as("日粒度追加流日").contains("流日/流时").contains("立春日");
        assertThat(day).as("日粒度不含十二时").doesNotContain("其十二时");
        String hour = str(run("handleFortuneCycles", fortuneVars("时")).get("fortuneCycles"));
        assertThat(hour).as("时粒度追加十二时五鼠遁").contains("其十二时（五鼠遁）");
    }

    @Test
    @DisplayName("大运流年·逐运评析 + 特殊岁运格局负/正分支（未见 / 岁运并临命中）")
    void fortuneAnalysisAndSpecial() throws Exception {
        Map<String, Object> r = run("handleFortuneCycles", fortuneVars("年"));
        String analysis = str(r.get("fortuneAnalysis"));
        assertThat(analysis).as("大运方向").contains("大运方向");
        assertThat(analysis).as("逐运评析").contains("大运逐运评析");
        assertThat(str(r.get("specialCycles"))).as("负分支·未见特殊格局").contains("未见岁运并临");

        Map<String, Object> hit = run("handleFortuneCycles", map(
                "fourPillars", "甲子 丙寅 戊午 庚申",
                "chartBase", "SOLAR=1990-05-15T10:00;GENDER=男",
                "fortuneSequence", "庚午,辛未,丙寅",
                "startAge", "0",
                "granularity", "年",
                "strength", "身强（得令）"));
        assertThat(str(hit.get("specialCycles"))).as("正分支·岁运并临命中").contains("岁运并临（与庚午运同）");
    }

    // ============================================================ 四、应期运限（inferTimingAndStages）

    @Test
    @DisplayName("应期运限·应期四法 + 运限四柱主阶段与干支")
    void timingAndStages() throws Exception {
        Map<String, Object> r = run("handleTimingAndStages",
                map("fourPillars", "甲子 丙寅 戊午 庚申", "voidness", "年戌亥空"));
        String timing = str(r.get("timing"));
        assertThat(timing).as("应期四法").contains("填实").contains("冲实").contains("合住").contains("透出");
        assertThat(timing).as("结合空亡").contains("年戌亥空");
        String stages = str(r.get("lifeStages"));
        assertThat(stages).as("年柱主少年").contains("年柱主少年");
        assertThat(stages).as("日柱主中年").contains("日柱主中年");
        assertThat(stages).as("时柱主老年").contains("时柱主老年");
        assertThat(stages).as("日柱干支").contains("戊午（日）");
        assertThat(stages).as("时柱干支").contains("庚申（时）");
    }
}
