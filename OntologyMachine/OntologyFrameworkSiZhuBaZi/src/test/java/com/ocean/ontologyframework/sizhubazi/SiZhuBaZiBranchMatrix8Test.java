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
 * 四柱命理 · 分支矩阵补充（第八阶段）· 命局层次逐档 / 清浊真神 / 命书详解降级（纯离线）。
 *
 * <p>本类补齐此前仅被抽样覆盖的 {@code handleJudgeChartLevel} 十支「层次判定」边界分支、
 * {@code purityTruth} 的清浊与真神得用分支，以及 {@code handleInterpretResult} 的大模型未装配降级分支：
 * <ul>
 *   <li>层次：专旺富贵两全 / 贵格兼富 / 纯贵格 / 富格 / 格破身弱 / 格破待救 / 贫格 /
 *       中平（身旺财官不显）/ 中平偏下 / 平常格（格成财官未通）/ 平常格（格局待成），
 *       及「伤官见官」使官星失理而降为富格。</li>
 *   <li>清浊真神：清（格正身健、用神有根、忌神不扰）与真神得用=true；格破→清中带浊。</li>
 *   <li>命书详解：llm 未装配 → aiInterpretation/aiInterpretationPlain 双空串降级，流程不阻断。</li>
 * </ul>
 *
 * <p>断言以程序结构化字段为唯一真相源（`chartLevel`/`purityTruth`/`aiInterpretation`/
 * `aiInterpretationPlain`），不反向解析判词。
 */
@DisplayName("四柱命理 · 分支矩阵补充（第八阶段·命局层次逐档/清浊真神/命书详解降级）")
class SiZhuBaZiBranchMatrix8Test {

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

    // ==================================================== 命局层次（handleJudgeChartLevel）

    private static Map<String, Object> judge(String pillars, String grade, String strength) throws Exception {
        return result("handleJudgeChartLevel", map(
                "fourPillars", pillars, "patternGrade", grade, "strength", strength));
    }

    private static String level(String pillars, String grade, String strength) throws Exception {
        return str(judge(pillars, grade, strength).get("chartLevel"));
    }

    @Test
    @DisplayName("层次·专旺成象 → 贵格·富贵两全（得局得格、气势成象）")
    void levelZhuanWangFuGuiLiangQuan() throws Exception {
        String lv = level("甲子 丙寅 戊午 庚申", "格成·专旺成象", "身强（得令）");
        assertThat(lv).contains("贵格·富贵两全").contains("得局得格");
    }

    @Test
    @DisplayName("层次·格成身任官且有财 → 贵格·亦主富（官星有理会兼财气通门户）")
    void levelGuiJianFu() throws Exception {
        // 戊辰 己酉 甲子 戊辰：月令酉(金)官当令，戊(土)财透且有根 → 官通、财通
        String lv = level("戊辰 己酉 甲子 戊辰", "格成（正官格）", "身强（得令）");
        assertThat(lv).contains("贵格·亦主富").contains("官星有理会兼财气通门户");
    }

    @Test
    @DisplayName("层次·格成身任官而财不通 → 纯贵格（官星有理会）")
    void levelGui() throws Exception {
        // 甲子 癸酉 甲子 乙亥：月令酉(金)官当令，全局无土(财) → 财不通
        String lv = level("甲子 癸酉 甲子 乙亥", "格成（正官格）", "身强（得令）");
        assertThat(lv).contains("贵格（官星有理会").doesNotContain("亦主富");
    }

    @Test
    @DisplayName("层次·格成身任财而官不通 → 富格（财气通门户）")
    void levelFu() throws Exception {
        // 甲子 甲辰 甲子 甲子：月令辰(土)偏财当令，全局无金(官) → 官不通
        String lv = level("甲子 甲辰 甲子 甲子", "格成（偏财格）", "身强（得令）");
        assertThat(lv).contains("富格（财气通门户").doesNotContain("贵格");
    }

    @Test
    @DisplayName("层次·格破且身弱 → 平常偏下（格破身弱·用神无力，须岁运救应，非必贫贱）")
    void levelPoShenRuo() throws Exception {
        String lv = level("甲子 丙寅 戊午 庚申", "格有破（会杀为凶）", "身弱（失令、无根、寡助）");
        assertThat(lv).contains("平常偏下（格破身弱").contains("非必贫贱");
    }

    @Test
    @DisplayName("层次·格破而身不弱 → 平常偏下（格破·待岁运救应）")
    void levelPoDaiJiu() throws Exception {
        String lv = level("甲子 丙寅 戊午 庚申", "格有破（会杀为凶）", "中和偏强（失令但得地得势）");
        assertThat(lv).contains("平常偏下（格破·待岁运救应）");
    }

    @Test
    @DisplayName("层次·财重身弱不任财 → 贫格（财神反不真）")
    void levelPin() throws Exception {
        // 甲子 甲辰 甲子 甲子：月令辰(土)财当令，身弱不能任财
        String lv = level("甲子 甲辰 甲子 甲子", "格将成（待岁运引透）", "身弱（失令、无根、寡助）");
        assertThat(lv).contains("贫格").contains("财神反不真").contains("财旺身弱");
    }

    @Test
    @DisplayName("层次·身旺而财官俱不显 → 中平（食伤泄秀，衣食可足而富贵有限）")
    void levelZhongPing() throws Exception {
        // 甲子 乙亥 甲子 乙亥：全局无土(财)无金(官)，身强
        String lv = level("甲子 乙亥 甲子 乙亥", "格将成（待岁运）", "身强（得令）");
        assertThat(lv).contains("中平（身旺而财官不显").contains("食伤泄秀");
    }

    @Test
    @DisplayName("层次·财官俱不显而身不强 → 中平偏下（宜岁运引扶，非必贫贱）")
    void levelZhongPingPianXia() throws Exception {
        String lv = level("甲子 乙亥 甲子 乙亥", "格将成（待岁运）", "中和偏弱（失令、克泄耗偏多）");
        assertThat(lv).contains("中平偏下").contains("非必贫贱");
    }

    @Test
    @DisplayName("层次·格成身弱官星有理会而财不通 → 平常格（格成而财官未通门户）")
    void levelChangPingGeCheng() throws Exception {
        // 甲子 癸酉 甲子 乙亥：官当令（官星有理会）然身弱不能任、且财不通 → 平常格
        String lv = level("甲子 癸酉 甲子 乙亥", "格成（正官格）", "身弱（失令、无根、寡助）");
        assertThat(lv).contains("平常格（格成而财官未通门户").contains("衣禄平顺");
    }

    @Test
    @DisplayName("层次·格局待成财官未成门户 → 平常格（格局待成、待岁运引扶）")
    void levelChangPingDaiCheng() throws Exception {
        // 甲子 甲辰 甲子 甲子：财通但格未成（格将成），身强不任财官之贵 → 平常格（待成）
        String lv = level("甲子 甲辰 甲子 甲子", "格将成（待岁运引透）", "身强（得令）");
        assertThat(lv).contains("平常格（格局待成").contains("待岁运引扶");
    }

    @Test
    @DisplayName("层次·伤官见官使官星失理 → 官不通，由贵格降为富格")
    void levelShangGuanJianGuanJiangWeiFu() throws Exception {
        // 己酉 丙午 甲子 庚午：月令午(火)伤官当令、丙(火)伤官透、庚(金)正官透 → 伤官见官，
        // 官星虽透有根亦失其理（guanLiHui=false）；己(土)财透有根 → 财通 → 降判为富格
        String lv = level("己酉 丙午 甲子 庚午", "格成（伤官格）", "身强（得令）");
        assertThat(lv).contains("富格（财气通门户").doesNotContain("贵格");
    }

    // ==================================================== 清浊真神（purityTruth）

    @Test
    @DisplayName("清浊·格正身健用神有根忌神不扰 → 清 + 真神得用=true")
    void purityQing() throws Exception {
        // 戊辰 己酉 甲子 戊辰：格成、身强、用神金藏支有根、忌神火四柱不见
        String p = str(judge("戊辰 己酉 甲子 戊辰", "格成（正官格）", "身强（得令）").get("purityTruth"));
        assertThat(p).contains("清（格正身健、用神有根、忌神不扰）").contains("真神得用=true");
    }

    @Test
    @DisplayName("清浊·格破 → 清中带浊（格有偏、用神乏力）")
    void purityQingZhongDaiZhuo() throws Exception {
        String p = str(judge("甲子 丙寅 戊午 庚申", "格有破（会杀为凶）", "身强（得令）").get("purityTruth"));
        assertThat(p).contains("清中带浊（格有偏、用神乏力）");
    }

    // ==================================================== 命书详解降级（handleInterpretResult）

    @Test
    @DisplayName("命书详解·大模型未装配 → 双空串降级，流程不阻断")
    void interpretResultDegradeWithoutLlm() throws Exception {
        Map<String, Object> r = result("handleInterpretResult", map("reading", "命书详解内容：甲日主……"));
        assertThat(r).containsKey("aiInterpretation").containsKey("aiInterpretationPlain");
        assertThat(str(r.get("aiInterpretation"))).isEmpty();
        assertThat(str(r.get("aiInterpretationPlain"))).isEmpty();
    }
}
