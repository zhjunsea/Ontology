package com.ocean.ontologyframework.ziweidoushu.web;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ocean.utilities.ServiceProbe;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 紫微斗数 · 真实应用「HTTP 入口」端到端测试（启动真实应用后，经 9089 REST 驱动全流程）。
 *
 * <p>与 {@code ZiWeiDouShuRealEngineDataValueTest}（经 CamundaClient 直连真实引擎）互补，
 * 本类补齐**真实 HTTP 入口**：{@code POST /api/ziweidoushu/chart} → 轮询 {@code GET /chart/{key}}，
 * 覆盖「所有类型星盘 / 命格」的数据分支与 Web 层逻辑分支，断言均为**真值**（非伪造）：
 * hunt(150) 命宫/五行局真值、matrix(51) 特征 token/格局真值、维度补齐、覆盖矩阵、Web 校验分支。
 * 应用未启动（9089 不可达）时自动跳过，不阻断离线构建。
 */
@Timeout(value = 3600, unit = TimeUnit.SECONDS)
@DisplayName("紫微斗数·真实应用 HTTP 端到端（全数据分支 + Web 逻辑分支，真值断言）")
class ZiWeiDouShuHttpE2ETest {

    private static final String BASE = "http://localhost:9089/api/ziweidoushu";
    private static final String PROCESS_ID = "ZiWeiDouShuChartProcess";
    private static final Set<String> ALERT_TYPES = Set.of("", "大凶", "寿元关", "重大疾病");
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() { };
    private static final TypeReference<List<Map<String, Object>>> LIST_TYPE = new TypeReference<>() { };
    private static final Pattern MING = Pattern.compile("命宫=([子丑寅卯辰巳午未申酉戌亥])");
    private static final Pattern BUREAU = Pattern.compile("五行局=([水木金土火][二三四五六]局)");

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).build();
    private static final ObjectMapper M = new ObjectMapper();

    @BeforeAll
    static void requireApp() {
        ServiceProbe.assumeAllReachable(
                "HTTP 端到端需「紫微斗数应用」已在 9089 启动（并含 Camunda/Ontop/MySQL）",
                ServiceProbe.LOCALHOST, 9089);
    }
    @Test
    @DisplayName("全数据分支：hunt(150) + matrix(51) + 维度补齐，逐例 COMPLETED 且与真值一致")
    void fullDataBranchE2E() throws Exception {
        List<Map<String, Object>> hunt = readList("hunt_cases.json");
        List<Map<String, Object>> matrix = readList("matrix_cases.json");
        List<String> fails = new ArrayList<>();
        Coverage cov = new Coverage();
        int total = 0, pass = 0;

        for (Map<String, Object> c : hunt) {
            total++;
            String id = str(c.get("id"));
            try {
                Map<String, Object> r = runCase(buildVars(c));
                List<String> errs = new ArrayList<>(structural(r, id));
                String skel = str(r.get("chartSkeleton"));
                String expMing = str(c.get("expectMing"));
                String expBureau = str(c.get("expectBureau"));
                if (expMing != null && !skel.contains("命宫=" + expMing)) {
                    errs.add(id + " 命宫≠" + expMing + " | " + cut(skel));
                }
                if (expBureau != null && !expBureau.isBlank() && !skel.contains("五行局=" + expBureau)) {
                    errs.add(id + " 五行局≠" + expBureau + " | " + cut(skel));
                }
                cov.add(c, r);
                if (errs.isEmpty()) pass++; else fails.addAll(errs);
            } catch (Throwable t) {
                fails.add(id + " 异常: " + t.getMessage());
            }
        }

        for (Map<String, Object> c : matrix) {
            total++;
            String id = str(c.get("id"));
            try {
                Map<String, Object> r = runCase(buildVars(c));
                List<String> errs = new ArrayList<>(structural(r, id));
                String feat = str(c.get("expectFeature"));
                if (feat != null && !feat.isBlank()) {
                    for (String tok : feat.split("\\|")) {
                        if (!featureOk(r, tok.trim())) errs.add(id + " 特征未命中: " + tok);
                    }
                }
                String pat = str(c.get("expectPattern"));
                if (!pat.isBlank() && !str(r.get("patternResult")).contains(pat)
                        && !haystack(r).contains(pat)) {
                    errs.add(id + " 格局未命中(patternResult): " + pat);
                }
                cov.add(c, r);
                if (errs.isEmpty()) pass++; else fails.addAll(errs);
            } catch (Throwable t) {
                fails.add(id + " 异常: " + t.getMessage());
            }
        }

        for (Map<String, Object> c : dimensionGaps()) {
            total++;
            String id = str(c.get("id"));
            try {
                Map<String, Object> r = runCase(buildVars(c));
                List<String> errs = new ArrayList<>(structural(r, id));
                Object mustObj = c.get("expectContains");
                String must = mustObj == null ? "" : str(mustObj);
                if (!must.isBlank()) {
                    for (String tok : must.split("\\|")) {
                        if (!haystack(r).contains(tok)) errs.add(id + " 未含: " + tok);
                    }
                }
                Object neObj = c.get("expectNonEmpty");
                String mustNonEmpty = neObj == null ? "" : str(neObj);
                if (!mustNonEmpty.isBlank()) {
                    for (String f : mustNonEmpty.split(",")) {
                        if (str(r.get(f)).isBlank()) errs.add(id + " 字段应非空: " + f);
                    }
                }
                cov.add(c, r);
                if (errs.isEmpty()) pass++; else fails.addAll(errs);
            } catch (Throwable t) {
                fails.add(id + " 异常: " + t.getMessage());
            }
        }

        cov.assertComplete(fails);
        System.out.println("[HTTP E2E] 覆盖矩阵: " + cov);
        System.out.println("[HTTP E2E] 通过 " + pass + " / " + total + " 例");
        assertThat(fails).as("HTTP 端到端失败：\n" + String.join("\n", fails)).isEmpty();
        assertThat(pass).as("全部用例应通过").isEqualTo(total);
    }
    @Test
    @DisplayName("Web 逻辑分支：POST 四类 400 校验 + 验盘成功 verifyVerdict 非空 + /health")
    void webLogicBranches() throws Exception {
        Map<String, Object> h = getJson("/health");
        assertThat(h.get("engineAvailable")).as("engineAvailable").isEqualTo(true);
        assertThat(str(h.get("processId"))).isEqualTo(PROCESS_ID);

        assertBadRequest(Map.of(), "birth");
        assertBadRequest(Map.of("birth", "1990-01-01 00:00", "mode", "乱写"), "mode");
        assertBadRequest(Map.of("birth", "1990-01-01 00:00", "depth", "乱写"), "depth");
        assertBadRequest(Map.of("birth", "1990-01-01 00:00", "mode", "验盘"), "subjectTraits");

        Map<String, Object> v = new LinkedHashMap<>();
        v.put("birth", "1985-03-03 09:30"); v.put("calendar", "公历"); v.put("gender", "女");
        v.put("birthPlace", "北京 116.4E"); v.put("mode", "验盘"); v.put("subjectType", "成人女");
        v.put("depth", "本命"); v.put("granularity", "年"); v.put("subjectTraits", "相貌清秀、性情温和、已知迁移");
        Map<String, Object> r = runCase(v);
        assertThat(str(r.get("verifyVerdict"))).as("验盘应产出 verifyVerdict").isNotBlank();
    }

    // ==================== 判定辅助 ====================

    /** 结构完备性：chartSkeleton/reading 非空、命格档合法、alertType 合法。 */
    private static List<String> structural(Map<String, Object> r, String id) {
        List<String> errs = new ArrayList<>();
        if (str(r.get("chartSkeleton")).isBlank()) errs.add(id + " chartSkeleton 为空");
        if (str(r.get("reading")).isBlank()) errs.add(id + " reading 为空");
        String g = str(r.get("patternGrade"));
        if (!legalGrade(g)) errs.add(id + " 命格档非法/空: " + cut(g));
        String alert = str(r.get("alertType"));
        if (!legalAlert(alert)) errs.add(id + " alertType 非法: " + alert);
        return errs;
    }

    /** 告警类型合法：空白(未触发) 或 由「/」组合的若干合法类型（如「寿元关/重大疾病」）。 */
    private static boolean legalAlert(String alert) {
        if (alert == null || alert.isBlank()) return true;
        for (String part : alert.split("/")) {
            if (!Set.of("大凶", "寿元关", "重大疾病").contains(part.trim())) return false;
        }
        return true;
    }

    private static boolean legalGrade(String g) {
        if (g == null || g.isBlank()) return false;
        for (String k : new String[]{"上上之命", "上次之命", "平等", "平常命", "下格", "中格",
                "上格", "常格", "杂局"}) {
            if (g.contains(k)) return true;
        }
        return false;
    }

    /** expectFeature token 命中：命宫/五行局→chartSkeleton，紫微→mainStars「紫微在X」，其余→全字段 haystack。 */
    private static boolean featureOk(Map<String, Object> r, String token) {
        int i = token.indexOf('=');
        if (i < 0) return haystack(r).contains(token);
        String label = token.substring(0, i);
        String val = token.substring(i + 1);
        switch (label) {
            case "命宫":   return str(r.get("chartSkeleton")).contains("命宫=" + val);
            case "五行局": return str(r.get("chartSkeleton")).contains("五行局=" + val);
            case "紫微":   return str(r.get("mainStars")).contains("紫微在" + val)
                                  || haystack(r).contains("紫微在" + val);
            case "空宫":   return str(r.get("emptyPalaceResult")).contains("宫空")
                                  || str(r.get("emptyPalaceResult")).contains("空宫");
            default:       return haystack(r).contains(val);
        }
    }

    /** 全字段 haystack：拼接顶层各字段值（含嵌套 variables 的值），供 token/格局子串命中。 */
    @SuppressWarnings("unchecked")
    private static String haystack(Map<String, Object> r) {
        StringBuilder sb = new StringBuilder();
        for (Object v : r.values()) {
            if (v == null) continue;
            if (v instanceof Map<?, ?> m) {
                for (Object mv : m.values()) sb.append(mv).append('\u0001');
            } else {
                sb.append(v).append('\u0001');
            }
        }
        return sb.toString();
    }
    // ==================== HTTP 调用与运行辅助 ====================

    /** POST 启动流程 → 轮询 GET 至 COMPLETED，返回最终响应 Map。 */
    private static Map<String, Object> runCase(Map<String, Object> vars) throws Exception {
        String json = M.writeValueAsString(vars);
        HttpResponse<String> pr = HTTP.send(HttpRequest.newBuilder(URI.create(BASE + "/chart"))
                        .timeout(Duration.ofSeconds(30))
                        .header("Content-Type", "application/json; charset=utf-8")
                        .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8)).build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (pr.statusCode() != 200) {
            throw new AssertionError("POST /chart " + pr.statusCode() + " -> " + cut(pr.body()));
        }
        long key = ((Number) M.readValue(pr.body(), MAP_TYPE).get("instanceKey")).longValue();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(180);
        while (System.nanoTime() < deadline) {
            HttpResponse<String> gr = HTTP.send(HttpRequest.newBuilder(URI.create(BASE + "/chart/" + key))
                            .timeout(Duration.ofSeconds(30)).GET().build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (gr.statusCode() != 200) {
                throw new AssertionError("GET /chart/" + key + " " + gr.statusCode() + " -> " + cut(gr.body()));
            }
            Map<String, Object> r = M.readValue(gr.body(), MAP_TYPE);
            String st = str(r.get("status"));
            if ("COMPLETED".equals(st)) return r;
            if ("INCIDENT".equals(st) || "TERMINATED".equals(st)) {
                throw new AssertionError("status=" + st + " key=" + key + " -> " + cut(gr.body()));
            }
            Thread.sleep(400);
        }
        throw new AssertionError("流程未在 180s 内完成 key=" + key);
    }

    private static Map<String, Object> getJson(String path) throws Exception {
        HttpResponse<String> gr = HTTP.send(HttpRequest.newBuilder(URI.create(BASE + path))
                        .timeout(Duration.ofSeconds(30)).GET().build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (gr.statusCode() != 200) throw new AssertionError("GET " + path + " " + gr.statusCode());
        return M.readValue(gr.body(), MAP_TYPE);
    }

    /** 提交应被控制器校验拒绝（400），且错误体包含 expect 关键字。 */
    private static void assertBadRequest(Map<String, Object> body, String expect) throws Exception {
        String json = M.writeValueAsString(body);
        HttpResponse<String> pr = HTTP.send(HttpRequest.newBuilder(URI.create(BASE + "/chart"))
                        .timeout(Duration.ofSeconds(30))
                        .header("Content-Type", "application/json; charset=utf-8")
                        .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8)).build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertThat(pr.statusCode()).as("应 400: " + json).isEqualTo(400);
        assertThat(pr.body()).as("错误体应含「" + expect + "」").contains(expect);
    }

    private static List<Map<String, Object>> readList(String name) throws Exception {
        try (java.io.InputStream in = ZiWeiDouShuHttpE2ETest.class.getResourceAsStream("/ziweidoushu/" + name)) {
            assertThat(in).as("测试资源应存在: " + name).isNotNull();
            return M.readValue(in, LIST_TYPE);
        }
    }
    // ==================== 变量装配与维度补齐 ====================

    /** 由用例 Map 装配提交 HTTP 的变量（缺省：公历/北京/直接排盘/成人男/本命/年）。 */
    private static Map<String, Object> buildVars(Map<String, Object> c) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("birth", str(c.get("birth")));
        v.put("calendar", def(c.get("calendar"), "公历"));
        v.put("gender", str(c.get("gender")));
        v.put("birthPlace", def(c.get("birthPlace"), "北京 116.4E"));
        v.put("mode", def(c.get("mode"), "直接排盘"));
        v.put("subjectType", def(c.get("subjectType"), "成人男"));
        v.put("depth", def(c.get("depth"), "本命"));
        v.put("granularity", def(c.get("granularity"), "年"));
        String traits = str(c.get("subjectTraits"));
        if (!traits.isBlank()) v.put("subjectTraits", traits);
        return v;
    }

    private static String def(Object o, String d) {
        String s = str(o);
        return s.isBlank() ? d : s;
    }

    /** 维度补齐用例：粒度月/日、农历、东经/西经 等 hunt/matrix 未覆盖的分支（断言真实观测字段）。 */
    private static List<Map<String, Object>> dimensionGaps() {
        List<Map<String, Object>> l = new ArrayList<>();
        l.add(gap("gap_gran_month", "1978-07-04 11:45", "男", "成人男", "直接排盘",
                "本命+大限+流年", "公历", "北京 116.4E", "月",
                "GRAN=月|DEPTH=本命+大限+流年", "annualChart,overlayReading"));
        l.add(gap("gap_gran_day", "1978-07-04 11:45", "男", "成人男", "直接排盘",
                "本命+大限+流年", "公历", "北京 116.4E", "日", "GRAN=日", "annualChart"));
        l.add(gap("gap_lunar", "1990-05-15 14:30", "男", "成人男", "直接排盘",
                "本命", "农历", "北京 116.4E", "年", "CAL=农历|SOLAR=1990-06-07", null));
        l.add(gap("gap_west", "1978-07-04 11:45", "男", "成人男", "直接排盘",
                "本命", "公历", "纽约 74W", "年", "PLACE=纽约 74W|DAYNIGHT=夜", null));
        l.add(gap("gap_east", "1978-07-04 11:45", "男", "成人男", "直接排盘",
                "本命", "公历", "东京 139.7E", "年", "PLACE=东京 139.7E", null));
        return l;
    }

    private static Map<String, Object> gap(String id, String birth, String gender, String subject,
                                            String mode, String depth, String cal, String place,
                                            String gran, String contains, String nonEmpty) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id); m.put("birth", birth); m.put("gender", gender); m.put("subjectType", subject);
        m.put("mode", mode); m.put("depth", depth); m.put("calendar", cal);
        m.put("birthPlace", place); m.put("granularity", gran);
        if (contains != null) m.put("expectContains", contains);
        if (nonEmpty != null) m.put("expectNonEmpty", nonEmpty);
        return m;
    }
    // ==================== 覆盖矩阵 ====================

    /** 汇总所跑用例覆盖的命宫/五行局/性别/深度/主体类型/模式/历法/粒度，断言全维度齐备。 */
    static class Coverage {
        final Set<String> ming = new TreeSet<>(), bureau = new TreeSet<>(), gender = new TreeSet<>(),
                depth = new TreeSet<>(), subject = new TreeSet<>(), mode = new TreeSet<>(),
                calendar = new TreeSet<>(), gran = new TreeSet<>();

        void add(Map<String, Object> c, Map<String, Object> r) {
            if (c.containsKey("gender")) gender.add(str(c.get("gender")));
            depth.add(def(c.get("depth"), "本命"));
            subject.add(def(c.get("subjectType"), "成人男"));
            mode.add(def(c.get("mode"), "直接排盘"));
            calendar.add(def(c.get("calendar"), "公历"));
            gran.add(def(c.get("granularity"), "年"));
            String skel = str(r.get("chartSkeleton"));
            Matcher mm = MING.matcher(skel);
            if (mm.find()) ming.add(mm.group(1));
            Matcher bm = BUREAU.matcher(skel);
            if (bm.find()) bureau.add(bm.group(1));
        }

        void assertComplete(List<String> fails) {
            if (ming.size() != 12) fails.add("覆盖不足: 命宫 " + ming.size() + "/12 -> " + ming);
            if (bureau.size() != 5) fails.add("覆盖不足: 五行局 " + bureau.size() + "/5 -> " + bureau);
            if (!gender.containsAll(Set.of("男", "女"))) fails.add("覆盖不足: gender=" + gender);
            if (!depth.containsAll(Set.of("本命", "本命+大限", "本命+大限+流年")))
                fails.add("覆盖不足: depth=" + depth);
            if (!subject.containsAll(Set.of("成人男", "成人女", "小儿")))
                fails.add("覆盖不足: subjectType=" + subject);
            if (!mode.containsAll(Set.of("直接排盘", "验盘"))) fails.add("覆盖不足: mode=" + mode);
            if (!calendar.containsAll(Set.of("公历", "农历"))) fails.add("覆盖不足: calendar=" + calendar);
            if (!gran.containsAll(Set.of("年", "月", "日"))) fails.add("覆盖不足: granularity=" + gran);
        }

        @Override
        public String toString() {
            return "命宫" + ming.size() + "/12=" + ming + "; 局" + bureau.size() + "/5=" + bureau
                    + "; 性别" + gender + "; 深度" + depth + "; 主体" + subject
                    + "; 模式" + mode + "; 历" + calendar + "; 粒度" + gran;
        }
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private static String cut(String s) {
        if (s == null) return "null";
        return s.length() > 80 ? s.substring(0, 80) + "…" : s;
    }
}

