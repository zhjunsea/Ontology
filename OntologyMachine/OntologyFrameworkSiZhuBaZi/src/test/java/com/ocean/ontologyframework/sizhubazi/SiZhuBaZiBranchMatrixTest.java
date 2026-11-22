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
 * 四柱命理 · 逻辑分支 × 数据分支矩阵补充测试（离线，不依赖 DB / Ontop / RabbitMQ / 引擎）。
 *
 * <p>在 {@link SiZhuBaZiDataBranchCoverageTest}（十神/神煞/用神/命格/旺衰代表分支）之外，进一步铺满：
 * <ul>
 *   <li>干支作用关系：天干五合 / 地支六合·六冲·六穿·三合成局·半合·三刑·自刑·子卯刑·六破·墓库 / 无关系；</li>
 *   <li>定格：正八格（正官/偏官/正财/偏财/正印/偏印/食神/伤官）+ 建禄 / 阳刃 / 月劫 / 杂气（四库本气比劫）/ 杂气偏财；</li>
 *   <li>用神：印比重（isYinBiZhongYong 真/假两分支）、effectiveYongElem 单参重载、调候救偏、从财格顺用；</li>
 *   <li>纳音·藏干·十二长生；大运流年结构（大运/流年/流月/流日流时）；</li>
 *   <li>告警分支（大凶 / 寿元关 / 无告警）；晚子时换日进位。</li>
 * </ul>
 *
 * <p>断言一律以程序结构化字段为唯一真相源（不反向解析判词文案、不伪造断言）；受控入参经真跑生产
 * Handler（Mockito 模拟 ActivatedJob/JobClient 承接 completeResult），纯算理判定经反射直调私有静态方法。
 */
@DisplayName("四柱命理 · 分支矩阵补充（关系/定格/用神/纳音/大运/告警/历法）")
class SiZhuBaZiBranchMatrixTest {

    private static final Class<?> WORKER = SiZhuBaZiOntologyJobWorker.class;

    private static final String[] GAN = {"甲", "乙", "丙", "丁", "戊", "己", "庚", "辛", "壬", "癸"};
    private static final String[] ZHI = {"子", "丑", "寅", "卯", "辰", "巳", "午", "未", "申", "酉", "戌", "亥"};

    // ============================================================ 反射 / 工具

    private static SiZhuBaZiOntologyJobWorker newWorker() throws Exception {
        Constructor<?> c = WORKER.getDeclaredConstructor();
        c.setAccessible(true);
        return (SiZhuBaZiOntologyJobWorker) c.newInstance();
    }

    private static Object stat(String name, Class<?>[] types, Object... args) throws Exception {
        Method m = WORKER.getDeclaredMethod(name, types);
        m.setAccessible(true);
        return m.invoke(null, args);
    }

    private static int idx(String[] arr, String v) {
        for (int i = 0; i < arr.length; i++) {
            if (arr[i].equals(v)) {
                return i;
            }
        }
        return -1;
    }

    /** 由「甲寅 乙卯 ...」四柱串解析为 int[4][2]（与生产 parsePillars 同口径）。 */
    private static int[][] p(String four) {
        String[] parts = four.trim().split("\\s+");
        int[][] out = new int[4][2];
        for (int i = 0; i < 4; i++) {
            out[i][0] = idx(GAN, parts[i].substring(0, 1));
            out[i][1] = idx(ZHI, parts[i].substring(1, 2));
        }
        return out;
    }

    // ---- Handler 受控驱动（模拟 JobClient 承接 completeResult 的变量，读回 result） ----

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
        Method m = WORKER.getMethod(handler, ActivatedJob.class, JobClient.class);
        m.invoke(w, job, client);
        assertThat(cap.vars).as("handler 应调用 completeResult：%s", handler).isNotNull();
        return (Map<String, Object>) ((Map<String, Object>) cap.vars).get("result");
    }

    private static Map<String, Object> vars(String four, String strength) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("fourPillars", four);
        if (strength != null) {
            v.put("strength", strength);
        }
        return v;
    }

    private static Map<String, Object> map(Object... kv) {
        Map<String, Object> v = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            v.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return v;
    }

    private static String[] fourCols(Map<String, Object> r) {
        return str(r.get("fourPillars")).trim().split("\\s+");
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    // ============================================================ 一、干支作用关系全分支

    @Test
    @DisplayName("干支关系·地支六合+天干五合（甲己合、子丑六合）")
    void relHeWuhe() throws Exception {
        String out = str(run("handleGanzhiRelations", vars("甲子 己丑 甲子 甲子", null)).get("ganzhiRelations"));
        assertThat(out).as("天干五合").contains("合而未必化");
        assertThat(out).as("地支六合").contains("六合");
    }

    @Test
    @DisplayName("干支关系·地支六冲（子午冲）")
    void relChong() throws Exception {
        String out = str(run("handleGanzhiRelations", vars("甲子 甲午 甲子 甲子", null)).get("ganzhiRelations"));
        assertThat(out).contains("相冲");
    }

    @Test
    @DisplayName("干支关系·地支六穿/六害（子未穿）")
    void relChuan() throws Exception {
        String out = str(run("handleGanzhiRelations", vars("甲子 乙未 甲子 甲子", null)).get("ganzhiRelations"));
        assertThat(out).contains("相穿");
    }

    @Test
    @DisplayName("干支关系·三合成局（申子辰）与半合（申子缺辰）")
    void relSanheBanhe() throws Exception {
        String full = str(run("handleGanzhiRelations", vars("甲申 甲子 甲辰 甲子", null)).get("ganzhiRelations"));
        assertThat(full).as("三合成局").contains("三合局").contains("成局");
        String ban = str(run("handleGanzhiRelations", vars("甲申 甲子 甲子 甲子", null)).get("ganzhiRelations"));
        assertThat(ban).as("半合").contains("半合");
    }

    @Test
    @DisplayName("干支关系·三刑（寅巳 2/3）与自刑（辰辰）与子卯相刑")
    void relXing() throws Exception {
        String sanXing = str(run("handleGanzhiRelations", vars("甲寅 乙巳 甲子 甲子", null)).get("ganzhiRelations"));
        assertThat(sanXing).contains("三刑（2/3）");
        String ziXing = str(run("handleGanzhiRelations", vars("甲辰 甲辰 甲子 甲子", null)).get("ganzhiRelations"));
        assertThat(ziXing).contains("辰辰自刑");
        String ziMao = str(run("handleGanzhiRelations", vars("甲子 乙卯 甲子 甲子", null)).get("ganzhiRelations"));
        assertThat(ziMao).contains("子卯相刑");
    }

    @Test
    @DisplayName("干支关系·六破（卯午）与墓库（辰戌丑未）")
    void relPoMu() throws Exception {
        String po = str(run("handleGanzhiRelations", vars("乙卯 丙午 甲子 甲子", null)).get("ganzhiRelations"));
        assertThat(po).contains("相破");
        String mu = str(run("handleGanzhiRelations", vars("甲子 甲辰 甲戌 甲子", null)).get("ganzhiRelations"));
        assertThat(mu).contains("墓库土");
    }

    @Test
    @DisplayName("干支关系·四柱无明显合冲刑穿（兜底分支）")
    void relNone() throws Exception {
        String out = str(run("handleGanzhiRelations", vars("甲子 丙寅 甲子 甲子", null)).get("ganzhiRelations"));
        assertThat(out).contains("无明显合冲刑穿");
    }

    // ============================================================ 二、定格：正八格

    @Test
    @DisplayName("定格·正八格：正官/偏官/正财/偏财/正印/偏印/食神/伤官（月令本气十神定格）")
    void patternZhengBaGe() throws Exception {
        assertThat(str(run("handleDeterminePattern", vars("甲子 辛酉 甲子 甲子", "中和")).get("patternResult")))
                .as("正官格").contains("正官格");
        assertThat(str(run("handleDeterminePattern", vars("甲子 庚申 甲子 甲子", "中和")).get("patternResult")))
                .as("偏官格").contains("偏官格");
        assertThat(str(run("handleDeterminePattern", vars("丙子 辛酉 丙子 丙子", "中和")).get("patternResult")))
                .as("正财格").contains("正财格");
        assertThat(str(run("handleDeterminePattern", vars("丙子 庚申 丙子 丙子", "中和")).get("patternResult")))
                .as("偏财格").contains("偏财格");
        assertThat(str(run("handleDeterminePattern", vars("甲子 癸子 甲子 甲子", "中和")).get("patternResult")))
                .as("正印格").contains("正印格");
        assertThat(str(run("handleDeterminePattern", vars("甲子 乙亥 甲子 甲子", "中和")).get("patternResult")))
                .as("偏印格").contains("偏印格");
        assertThat(str(run("handleDeterminePattern", vars("甲子 丁巳 甲子 甲子", "中和")).get("patternResult")))
                .as("食神格").contains("食神格");
        assertThat(str(run("handleDeterminePattern", vars("甲子 庚午 甲子 甲子", "中和")).get("patternResult")))
                .as("伤官格").contains("伤官格");
    }

    @Test
    @DisplayName("定格·禄刃与月劫：建禄格/阳刃格/月劫格")
    void patternLuRenYueJie() throws Exception {
        assertThat(str(run("handleDeterminePattern", vars("甲子 甲寅 甲子 甲子", "身强（得令）")).get("patternResult")))
                .as("建禄格").contains("建禄格");
        assertThat(str(run("handleDeterminePattern", vars("甲子 乙卯 甲子 甲子", "身强（得令）")).get("patternResult")))
                .as("阳刃格").contains("阳刃格");
        assertThat(str(run("handleDeterminePattern", vars("乙丑 甲寅 乙丑 乙丑", "身强（得令）")).get("patternResult")))
                .as("月劫格").contains("月劫格");
    }

    @Test
    @DisplayName("定格·杂气：四库月本气比劫（杂气格）/ 四库月本气财官（杂气偏财格）")
    void patternZaQi() throws Exception {
        assertThat(str(run("handleDeterminePattern", vars("己丑 己丑 己丑 己丑", "身强（得令）")).get("patternResult")))
                .as("杂气格（四库本气比劫）").contains("杂气格");
        assertThat(str(run("handleDeterminePattern", vars("甲子 戊辰 甲子 甲子", "中和")).get("patternResult")))
                .as("杂气偏财格").contains("杂气偏财格");
    }

    // ============================================================ 三、用神补充分支

    @Test
    @DisplayName("用神·印比重判断：身强且月令为印/比 → true；月令为食伤 → false")
    void yinBiZhongBranches() throws Exception {
        Class<?>[] t = {int[][].class, int.class, boolean.class};
        assertThat((boolean) stat("isYinBiZhongYong", t, (Object) p("甲子 癸子 甲子 甲子"), 0, true))
                .as("身强月令印（子水）").isTrue();
        assertThat((boolean) stat("isYinBiZhongYong", t, (Object) p("甲子 丁巳 甲子 甲子"), 0, true))
                .as("身强月令食神（巳火）").isFalse();
    }

    @Test
    @DisplayName("用神·effectiveYongElem 单参重载（等价 strong=false）：身弱月令官杀取印")
    void effectiveYongElemOverload() throws Exception {
        assertThat((int) stat("effectiveYongElem", new Class<?>[]{int[][].class, int.class},
                (Object) p("甲子 辛酉 甲子 甲子"), 0)).isEqualTo(4);
    }

    @Test
    @DisplayName("用神·调候救偏：丙午火炎无水 → selectYongShen 主取调候（含「调候救偏」）")
    void yongShenTiaohouPriority() throws Exception {
        String yong = str(run("handleSelectYongShen", vars("丙午 丙午 丙午 辛酉", "身强（得令）")).get("yongShen"));
        assertThat(yong).contains("调候救偏");
    }

    @Test
    @DisplayName("用神·从财格顺用：日主无根从财 → selectYongShen 主取从财（含「从财格」）")
    void yongShenCongCai() throws Exception {
        String yong = str(run("handleSelectYongShen", vars("戊戌 戊戌 甲戌 戊戌", "身弱（失令、无根、寡助）")).get("yongShen"));
        assertThat(yong).contains("从财格");
    }

    // ============================================================ 四、纳音·藏干·十二长生

    @Test
    @DisplayName("纳音藏干·四柱纳音/地支藏干/日主十二长生输出齐备")
    void nayinHiddenTwelve() throws Exception {
        Map<String, Object> r = run("handleNayinHiddenStems", vars("甲子 乙丑 甲寅 丁卯", null));
        assertThat(str(r.get("nayin"))).as("纳音四柱").contains("年甲子").contains("时丁卯").contains("→");
        assertThat(str(r.get("hiddenStems"))).as("藏干").contains("子藏癸").contains("藏");
        assertThat(str(r.get("dayPillarTraits"))).as("日柱特性含十二长生").contains("日主甲").contains("十二长生");
    }

    // ============================================================ 五、大运流年结构

    private static Map<String, Object> fortuneVars(String granularity) {
        return map(
                "fourPillars", "甲子 乙丑 甲寅 丁卯",
                "startAge", 8.0,
                "fortuneSequence", "甲子,乙丑,丙寅,丁卯,戊辰,己巳,庚午,辛未",
                "strength", "身强（得令）",
                "granularity", granularity,
                "chartBase", "SOLAR=1990-01-01T00:00;GENDER=男;SUBJECT=男命;MODE=单命");
    }

    @Test
    @DisplayName("大运流年·结构：大运/流年/流月齐备；granularity=时 追加流日流时，=年 不含")
    void fortuneCyclesStructure() throws Exception {
        Map<String, Object> shi = run("handleFortuneCycles", fortuneVars("时"));
        String cycles = str(shi.get("fortuneCycles"));
        assertThat(cycles).as("大运段").contains("大运（起运8");
        assertThat(cycles).as("流年段").contains("流年");
        assertThat(cycles).as("流月段").contains("流月");
        assertThat(cycles).as("流日流时（时粒度）").contains("流日/流时");
        assertThat(str(shi.get("specialCycles"))).isNotBlank();

        Map<String, Object> nian = run("handleFortuneCycles", fortuneVars("年"));
        assertThat(str(nian.get("fortuneCycles"))).as("年粒度不含流日流时").doesNotContain("流日/流时");
    }

    // ============================================================ 六、告警分支（离线走程序兜底）

    private static Map<String, Object> alertVars(String chartLevel, String grade, String strength, String deities) {
        return map(
                "chartBase", "SOLAR=2000-01-01T00:00;GENDER=男;SUBJECT=男命;MODE=单命",
                "fourPillars", "甲子 甲子 甲子 甲子",
                "chartLevel", chartLevel,
                "patternGrade", grade,
                "strength", strength,
                "deities", deities);
    }

    @Test
    @DisplayName("告警·大凶分支：格有破兼身弱 → alertTriggered=true")
    void alertDaXiong() throws Exception {
        Map<String, Object> r = run("handleAggregateReading",
                alertVars("平常偏下（格破·待岁运救应）", "格有破（会杀为凶）", "身弱（失令、无根、寡助）", ""));
        assertThat(r.get("alertTriggered")).isEqualTo(true);
    }

    @Test
    @DisplayName("告警·寿元关分支：羊刃逢冲 → alertTriggered=true")
    void alertShouYuanGuan() throws Exception {
        Map<String, Object> r = run("handleAggregateReading",
                alertVars("平常格（格成而财官未通门户）", "格成", "中和", "羊刃、子午相冲"));
        assertThat(r.get("alertTriggered")).isEqualTo(true);
    }

    @Test
    @DisplayName("告警·无告警分支：格成身中和、无羊刃冲 → alertTriggered=false")
    void alertNone() throws Exception {
        Map<String, Object> r = run("handleAggregateReading",
                alertVars("平常格（格成而财官未通门户）", "格成", "中和", "天乙贵人"));
        assertThat(r.get("alertTriggered")).isEqualTo(false);
    }

    // ============================================================ 七、历法：晚子时换日

    @Test
    @DisplayName("起四柱·晚子时换日：LATEZI=true 时日柱较 LATEZI=false 进位（子初换日）")
    void lateZiRollover() throws Exception {
        Map<String, Object> normal = run("handleCastFourPillars",
                map("chartBase", "SOLAR=2020-06-15T23:30:00;GENDER=男;LATEZI=false"));
        Map<String, Object> late = run("handleCastFourPillars",
                map("chartBase", "SOLAR=2020-06-15T23:30:00;GENDER=男;LATEZI=true"));
        String[] n = fourCols(normal);
        String[] l = fourCols(late);
        assertThat(n).as("四柱应为四列").hasSize(4);
        assertThat(l).hasSize(4);
        assertThat(l[2]).as("晚子时换日 → 日柱进位").isNotEqualTo(n[2]);
        // 年、月柱不因晚子时改变
        assertThat(l[0]).isEqualTo(n[0]);
        assertThat(l[1]).isEqualTo(n[1]);
    }
}
