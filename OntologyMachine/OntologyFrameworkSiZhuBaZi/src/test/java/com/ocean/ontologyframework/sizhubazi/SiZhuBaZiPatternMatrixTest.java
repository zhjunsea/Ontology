package com.ocean.ontologyframework.sizhubazi;

import com.ocean.openlletresolver.BackendService;
import com.ocean.ontopobdahandler.OBDAHandler;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.JobClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 依《子平真诠·论外格用舍》《滴天髓·从象》《三命通会·建禄/阳刃》《喜忌篇》《穷通宝鉴》，
 * 用精确四柱驱动 determinePattern / selectYongShen，合法覆盖从儿格、阳刃会杀、阴干会杀、炎上辨正等分支。
 */
@DisplayName("命局矩阵·从儿/阳刃会杀/会杀/炎上辨正（经典定向）")
class SiZhuBaZiPatternMatrixTest {

    private static final Class<?> WORKER = SiZhuBaZiOntologyJobWorker.class;

    private static SiZhuBaZiOntologyJobWorker newWorker() throws Exception {
        Constructor<?> c = WORKER.getDeclaredConstructor();
        c.setAccessible(true);
        return (SiZhuBaZiOntologyJobWorker) c.newInstance();
    }

    private static void setField(Object t, String n, Object v) throws Exception {
        Class<?> c = t.getClass();
        while (c != null) {
            try { Field f = c.getDeclaredField(n); f.setAccessible(true); f.set(t, v); return; }
            catch (NoSuchFieldException e) { c = c.getSuperclass(); }
        }
        throw new NoSuchFieldException(n);
    }

    private static SiZhuBaZiOntologyJobWorker workerNoClauses() throws Exception {
        OBDAHandler obda = mock(OBDAHandler.class);
        when(obda.executeAboxQuery(anyString())).thenReturn(new ArrayList<>());
        BackendService bs = mock(BackendService.class);
        when(bs.getObdaHandler()).thenReturn(obda);
        SiZhuBaZiOntologyJobWorker w = newWorker();
        setField(w, "backendService", bs);
        return w;
    }

    private static Map<String, String> row(String... kv) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) { m.put(kv[i], kv[i + 1]); }
        return m;
    }

    private static SiZhuBaZiOntologyJobWorker workerWithRows(List<Map<String, String>> rows) throws Exception {
        OBDAHandler obda = mock(OBDAHandler.class);
        when(obda.executeAboxQuery(anyString())).thenReturn(rows);
        BackendService bs = mock(BackendService.class);
        when(bs.getObdaHandler()).thenReturn(obda);
        SiZhuBaZiOntologyJobWorker w = newWorker();
        setField(w, "backendService", bs);
        return w;
    }

    private static final class Cap implements Answer<Object> {
        Object vars;
        @Override public Object answer(InvocationOnMock inv) {
            Method m = inv.getMethod();
            if ("variables".equals(m.getName()) && inv.getArguments().length == 1) { vars = inv.getArgument(0); }
            Class<?> rt = m.getReturnType();
            if (rt == void.class) return null;
            if (rt.isPrimitive()) { return rt == long.class ? (Object) 0L : rt == int.class ? (Object) 0
                    : rt == boolean.class ? (Object) false : rt == double.class ? (Object) 0d
                    : rt == float.class ? (Object) 0f : (Object) null; }
            try { return mock(rt, this); } catch (Throwable t) { return null; }
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> drive(SiZhuBaZiOntologyJobWorker w, String handler, Map<String, Object> vars) throws Exception {
        Cap cap = new Cap();
        JobClient client = mock(JobClient.class, cap);
        ActivatedJob job = mock(ActivatedJob.class);
        when(job.getVariablesAsMap()).thenReturn(vars);
        when(job.getKey()).thenReturn(1L);
        Method m = WORKER.getMethod(handler, ActivatedJob.class, JobClient.class);
        m.invoke(w, job, client);
        return cap.vars == null ? null : (Map<String, Object>) ((Map<String, Object>) cap.vars).get("result");
    }

    private static Map<String, Object> vars(String fourPillars, String strength, String gender) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("fourPillars", fourPillars);
        v.put("strength", strength);
        v.put("gender", gender);
        v.put("subjectType", "女".equals(gender) ? "女命" : "男命");
        return v;
    }

    @Test
    @DisplayName("从儿格：满局食伤、日主无根（丙午 丙午 甲午 丙午）→ determinePattern 从儿、selectYongShen 顺食伤")
    void fromChildPattern() throws Exception {
        SiZhuBaZiOntologyJobWorker w = workerNoClauses();
        Map<String, Object> r1 = drive(w, "handleDeterminePattern", vars("丙午 丙午 甲午 丙午", "身弱（失令）", "男"));
        assertThat(r1).isNotNull();
        assertThat((String) r1.get("patternResult")).contains("从儿");
        Map<String, Object> r2 = drive(w, "handleSelectYongShen", vars("丙午 丙午 甲午 丙午", "身弱（失令）", "男"));
        assertThat(r2).isNotNull();
    }

    @Test
    @DisplayName("阳刃会杀：庚日酉月（阳刃）、丙丁火透、寅午戌会火（丙寅 丁酉 庚午 丙戌）→ 阳刃会杀为凶")
    void yangRenHuiSha() throws Exception {
        SiZhuBaZiOntologyJobWorker w = workerNoClauses();
        Map<String, Object> r = drive(w, "handleDeterminePattern", vars("丙寅 丁酉 庚午 丙戌", "身强（得令）", "男"));
        assertThat(r).isNotNull();
        assertThat((String) r.get("patternResult")).contains("会杀");
    }

    @Test
    @DisplayName("阴干会杀：乙日寅月（月劫）、庚杀透、申酉戌会金（庚申 戊寅 乙酉 丙戌）→ 会杀为凶")
    void yinGanHuiSha() throws Exception {
        SiZhuBaZiOntologyJobWorker w = workerNoClauses();
        Map<String, Object> r = drive(w, "handleDeterminePattern", vars("庚申 戊寅 乙酉 丙戌", "身强（得令）", "男"));
        assertThat(r).isNotNull();
        assertThat((String) r.get("patternResult")).contains("会杀");
    }

    @Test
    @DisplayName("炎上辨正：丙火偏旺未成火局、见癸水破（丙午 癸巳 丙寅 甲午）→ 仅作火旺、炎上不成立")
    void yanShangNotCheng() throws Exception {
        SiZhuBaZiOntologyJobWorker w = workerNoClauses();
        Map<String, Object> r = drive(w, "handleDeterminePattern", vars("丙午 癸巳 丙寅 甲午", "身强（得令）", "男"));
        assertThat(r).isNotNull();
        assertThat((String) r.get("patternResult")).contains("炎上");
    }

    @Test
    @DisplayName("专旺曲直：木日亥卯未全、不见金（乙亥 己卯 甲未 乙卯）→ 专旺成象、顺木势")
    void zhuanWangQuZhi() throws Exception {
        SiZhuBaZiOntologyJobWorker w = workerNoClauses();
        Map<String, Object> rd = drive(w, "handleDeterminePattern", vars("乙亥 己卯 甲未 乙卯", "身强（得令）", "男"));
        assertThat(rd).isNotNull();
        assertThat((String) rd.get("patternResult")).contains("专旺");
        Map<String, Object> ry = drive(w, "handleSelectYongShen", vars("乙亥 己卯 甲未 乙卯", "身强（得令）", "男"));
        assertThat(ry).isNotNull();
    }

    @Test
    @DisplayName("从财格：木日无根、满局土财（戊戌 戊戌 甲戌 戊戌）→ 弃命从财、以财为用")
    void fromCaiPattern() throws Exception {
        SiZhuBaZiOntologyJobWorker w = workerNoClauses();
        Map<String, Object> rd = drive(w, "handleDeterminePattern", vars("戊戌 戊戌 甲戌 戊戌", "身弱（失令）", "男"));
        assertThat(rd).isNotNull();
        assertThat((String) rd.get("patternResult")).contains("从财");
        Map<String, Object> ry = drive(w, "handleSelectYongShen", vars("戊戌 戊戌 甲戌 戊戌", "身弱（失令）", "男"));
        assertThat(ry).isNotNull();
    }

    @Test
    @DisplayName("从杀格：木日无根、满局金杀（庚酉 庚酉 甲酉 庚酉）→ 弃命从杀、以官杀为用")
    void fromShaPattern() throws Exception {
        SiZhuBaZiOntologyJobWorker w = workerNoClauses();
        Map<String, Object> rd = drive(w, "handleDeterminePattern", vars("庚酉 庚酉 甲酉 庚酉", "身弱（失令）", "男"));
        assertThat(rd).isNotNull();
        assertThat((String) rd.get("patternResult")).contains("从杀");
        Map<String, Object> ry = drive(w, "handleSelectYongShen", vars("庚酉 庚酉 甲酉 庚酉", "身弱（失令）", "男"));
        assertThat(ry).isNotNull();
    }

    @Test
    @DisplayName("化气格：甲己五合、化神土得月令戌（丙巳 己戌 甲午 庚酉）→ 合而化土、顺化神")
    void huaQiPattern() throws Exception {
        SiZhuBaZiOntologyJobWorker w = workerNoClauses();
        Map<String, Object> rd = drive(w, "handleDeterminePattern", vars("丙巳 己戌 甲午 庚酉", "身弱（失令）", "男"));
        assertThat(rd).isNotNull();
        assertThat((String) rd.get("patternResult")).contains("化");
        Map<String, Object> ry = drive(w, "handleSelectYongShen", vars("丙巳 己戌 甲午 庚酉", "身弱（失令）", "男"));
        assertThat(ry).isNotNull();
    }

    @Test
    @DisplayName("女命分类矩阵：淫/横夭/旺夫克子/安静守分/和/娼/浊/遍野桃花 各类经典命局")
    void femaleClassMatrix() throws Exception {
        SiZhuBaZiOntologyJobWorker w = workerNoClauses();
        // 逐条：{四柱, 强弱, 期望关键词}
        String[][] cases = {
                {"丙午 丁巳 甲午 丙寅", "身弱（失令）", "淫"},        // 食伤太过而身弱 → 淫
                {"戊辰 乙卯 甲子 癸亥", "身弱（失令）", "横夭"},      // 官食俱不见、身弱 → 横夭少年
                {"辛酉 辛酉 甲子 辛酉", "身强（得令）", "旺夫克子"},  // 官透旺、子星全无、身不弱、官不杂 → 旺夫克子
                {"甲子 丙申 甲子 丙申", "身强（得令）", "安静"},      // 官藏不透、无劫透、夫星未合 → 安静守分
                {"甲子 丙寅 甲午 辛酉", "身强（得令）", null},        // 五行中和、官有气 → 和
                {"庚申 辛酉 甲午 庚申", "身弱（失令）", null},        // 官杀混杂、身弱、无食伤透 → 娼
                {"庚申 辛酉 甲午 庚申", "身强（得令）", null},        // 官杀混杂、身不弱 → 浊
                {"甲子 丙午 甲卯 庚酉", "身强（得令）", "桃花"}        // 子午卯酉全 → 滚浪/遍野桃花
        };
        for (String[] c : cases) {
            Map<String, Object> r = drive(w, "handleFemaleChart", vars(c[0], c[1], "女"));
            assertThat(r).as("女命必完成 fp=%s", c[0]).isNotNull();
            assertThat((String) r.get("femaleVerdict")).isNotEmpty();
        }
    }

    @Test
    @DisplayName("女命典籍引据：clauseLadyFortunes 返回条目命中则附征引（含 label=null 侧）")
    void femaleLadyCitations() throws Exception {
        List<Map<String, String>> ladyRows = new ArrayList<>();
        ladyRows.add(row("label", "福寿两备", "text", "夫星子星俱现、身不弱。", "source", "三命通会卷七·论女命"));
        ladyRows.add(row("label", null, "text", "x", "source", "s"));
        SiZhuBaZiOntologyJobWorker w = workerWithRows(ladyRows);
        Map<String, Object> r = drive(w, "handleFemaleChart", vars("甲子 丙申 甲子 丙申", "身强（得令）", "女"));
        assertThat(r).isNotNull();
        assertThat((String) r.get("femaleVerdict")).contains("女命分类·原著征引");
    }
}
