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
 * 四柱命理 · 分支矩阵补充（第七阶段）· 合婚三维 / 小儿关杀 / 女命专论（纯离线）。
 *
 * <p>本类对三个「专项分析」节点做更深的子分支断言（此前二阶段仅有冒烟式覆盖）：
 * <ul>
 *   <li>合婚（analyzeMarriageMatch）：纳音生克三支（比和/相生/相克）、生肖关系（同支/三合/六冲）、
 *       神煞合参（桃花命中 vs 无显著）、缺参负向。</li>
 *   <li>小儿关杀（analyzeChildChart）：时值驿马 / 时值桃花 / 二者皆非 三支 + 关杀名目备参文案。</li>
 *   <li>女命专论（analyzeFemaleChart）：夫星/子星/比劫与分类结构、官杀混杂「正偏自处」特类。</li>
 * </ul>
 *
 * <p>断言以程序结构化字段为唯一真相源（`marriageConclusion`/`complementAnalysis`/`nayinRelation`/
 * `zodiacRelation`/`shenshaMatch`/`childVerdict`/`femaleVerdict`），不反向解析判词。
 */
@DisplayName("四柱命理 · 分支矩阵补充（第七阶段·合婚三维/小儿关杀/女命专论）")
class SiZhuBaZiBranchMatrix7Test {

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

    // ============================================================ 一、合婚（analyzeMarriageMatch）

    private static final String SELF_CB = "SOLAR=1990-05-15T10:00;GENDER=男;LATEZI=false";

    private static Map<String, Object> marriage(String partnerBirth) throws Exception {
        return result("handleMarriageMatch", map("chartBase", SELF_CB, "partnerBirth", partnerBirth));
    }

    @Test
    @DisplayName("合婚·缺参负向：无 partnerBirth → MARRIAGE_MATCH_FAILED")
    void marriageMissingPartnerFails() throws Exception {
        assertThat(errorCodeOf("handleMarriageMatch", map("chartBase", SELF_CB))).isEqualTo("MARRIAGE_MATCH_FAILED");
    }

    @Test
    @DisplayName("合婚·纳音比和 + 生肖同支：双方同年（庚午·路旁土）")
    void marriageNayinBiHe() throws Exception {
        Map<String, Object> r = marriage("1990-05-20 10:00");
        assertThat(str(r.get("nayinRelation"))).as("纳音比和").contains("比和");
        assertThat(str(r.get("zodiacRelation"))).as("生肖同支比和").contains("同支比和");
        assertThat(str(r.get("marriageConclusion"))).contains("合婚结论");
    }

    @Test
    @DisplayName("合婚·纳音相生 + 生肖三合：本人庚午土 / 对方丙寅火（火生土）、寅午戌三合")
    void marriageNayinXiangSheng() throws Exception {
        Map<String, Object> r = marriage("1986-05-20 10:00");
        assertThat(str(r.get("nayinRelation"))).as("火生土·相生").contains("相生");
        assertThat(str(r.get("zodiacRelation"))).as("寅午三合").contains("三合");
    }

    @Test
    @DisplayName("合婚·纳音相克：本人庚午土 / 对方戊辰木（木克土）")
    void marriageNayinXiangKe() throws Exception {
        assertThat(str(marriage("1988-05-20 10:00").get("nayinRelation"))).as("木克土·相克").contains("相克");
    }

    @Test
    @DisplayName("合婚·生肖六冲：本人午 / 对方子（子午冲）")
    void marriageZodiacLiuChong() throws Exception {
        assertThat(str(marriage("1984-05-20 10:00").get("zodiacRelation"))).as("子午六冲").contains("六冲");
    }

    @Test
    @DisplayName("合婚·神煞合参命中桃花 + 三维文本齐备：对方年支卯（本人寅午戌桃花在卯）")
    void marriageShenshaTaoHua() throws Exception {
        Map<String, Object> r = marriage("1987-05-20 10:00");
        assertThat(str(r.get("shenshaMatch"))).as("对方带本人桃花").contains("桃花");
        String comp = str(r.get("complementAnalysis"));
        assertThat(comp).contains("本人四柱").contains("五行互补").contains("比劫/食伤配属")
                .contains("年命纳音").contains("生肖合冲").contains("神煞合参");
    }

    // ============================================================ 二、小儿关杀（analyzeChildChart）

    @Test
    @DisplayName("小儿关杀·时值驿马：年支午（寅午戌驿马在申）时支申")
    void childYiMa() throws Exception {
        String v = str(result("handleChildChart", map("fourPillars", "甲午 丙寅 甲子 甲申")).get("childVerdict"));
        assertThat(v).contains("小儿关杀（仅列按年支/时支实算者）").contains("本命年支午：马相").contains("时值驿马");
    }

    @Test
    @DisplayName("小儿关杀·时值桃花：年支午（寅午戌桃花在卯）时支卯")
    void childTaoHua() throws Exception {
        String v = str(result("handleChildChart", map("fourPillars", "甲午 丙寅 甲子 乙卯")).get("childVerdict"));
        assertThat(v).contains("时值桃花");
    }

    @Test
    @DisplayName("小儿关杀·驿马桃花皆非：时支戌 → 仅列名备参、不作断语")
    void childNeither() throws Exception {
        String v = str(result("handleChildChart", map("fourPillars", "甲午 丙寅 甲子 甲戌")).get("childVerdict"));
        assertThat(v).contains("本命年支午：马相").contains("另有将军箭").doesNotContain("时值驿马").doesNotContain("时值桃花");
    }

    // ============================================================ 三、女命专论（analyzeFemaleChart）

    @Test
    @DisplayName("女命专论·结构与夫子星/比劫文本齐备")
    void femaleStructure() throws Exception {
        String v = str(result("handleFemaleChart", map(
                "fourPillars", "甲子 丙寅 戊午 庚申",
                "strength", "身强（得令）")).get("femaleVerdict"));
        assertThat(v).contains("女命专论：").contains("夫星").contains("子星").contains("比劫")
                .contains("【女命分类·归类】");
    }

    @Test
    @DisplayName("女命专论·官杀混杂 → 特类「正偏自处」呈现")
    void femaleGuanZaZhengPian() throws Exception {
        String v = str(result("handleFemaleChart", map(
                "fourPillars", "庚午 辛未 甲子 乙丑",
                "strength", "身强（得令）")).get("femaleVerdict"));
        assertThat(v).as("甲日见庚（偏官）辛（正官）→官杀混杂→正偏自处").contains("正偏自处");
    }
}
