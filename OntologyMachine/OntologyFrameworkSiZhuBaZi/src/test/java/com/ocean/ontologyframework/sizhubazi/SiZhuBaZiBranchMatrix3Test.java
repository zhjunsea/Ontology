package com.ocean.ontologyframework.sizhubazi;

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
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 四柱命理 · 分支矩阵补充（第三阶段）· 神煞起例 / 格局成败救应 / 具名格局 / 月令冲合空（纯离线）。
 *
 * <p>第一、二批覆盖算理主链与下游网关；本类补齐此前未系统触及的三组深层算理分支：
 * <ul>
 *   <li>神煞起例：arrangeDeities 离线降级八类 + 空亡 voidness 双分支；matchDeityRule 七类基准逐支；DEITY_SKIP 黑名单口径；</li>
 *   <li>成败救应：detectChengBai 正官/偏官/财/印/食神/伤官 各「有救 / 待救」两叉，及身弱不判比劫夺财的边界；</li>
 *   <li>具名格局：hitNamedPatterns 魁罡成立与破格、日贵/日德、日禄归时正格·兼格·破格、六乙鼠贵、壬骑龙背；</li>
 *   <li>月令冲/合/旬空：determinePattern 格神未透遇月令逢冲/逢合/旬空，及月令逢冲对已判成败只追加注记不覆盖 grade。</li>
 * </ul>
 *
 * <p>断言以程序结构化字段为唯一真相源：Handler 经 Mockito 模拟承接 completeResult 读回 result；
 * 纯算理方法（detectChengBai / hitNamedPatterns / matchDeityRule / taohuaDerivNote）经反射直调。
 */
@DisplayName("四柱命理 · 分支矩阵补充（第三阶段·神煞/成败救应/具名格局/月令冲合空）")
class SiZhuBaZiBranchMatrix3Test {

    private static final Class<?> WORKER = SiZhuBaZiOntologyJobWorker.class;

    private static final String[] GAN = {"甲", "乙", "丙", "丁", "戊", "己", "庚", "辛", "壬", "癸"};
    private static final String[] ZHI = {"子", "丑", "寅", "卯", "辰", "巳", "午", "未", "申", "酉", "戌", "亥"};

    // ============================================================ 反射 / 工具

    private static SiZhuBaZiOntologyJobWorker newWorker() throws Exception {
        Constructor<?> c = WORKER.getDeclaredConstructor();
        c.setAccessible(true);
        return (SiZhuBaZiOntologyJobWorker) c.newInstance();
    }

    /** 直调私有静态方法。 */
    private static Object stat(String name, Class<?>[] types, Object... args) throws Exception {
        Method m = WORKER.getDeclaredMethod(name, types);
        m.setAccessible(true);
        return m.invoke(null, args);
    }

    /** 直调私有实例方法（每次新建 worker 实例）。 */
    private static Object inst(String name, Class<?>[] types, Object... args) throws Exception {
        SiZhuBaZiOntologyJobWorker w = newWorker();
        Method m = WORKER.getDeclaredMethod(name, types);
        m.setAccessible(true);
        return m.invoke(w, args);
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
    private static int[][] pf(String four) {
        String[] parts = four.trim().split(" ");
        int[][] out = new int[4][2];
        for (int i = 0; i < 4; i++) {
            out[i][0] = idx(GAN, parts[i].substring(0, 1));
            out[i][1] = idx(ZHI, parts[i].substring(1, 2));
        }
        return out;
    }

    private static String[] arr(String csv) {
        String[] parts = csv.split(",");
        for (int i = 0; i < parts.length; i++) {
            parts[i] = parts[i].trim();
        }
        return parts;
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

    // ---- 纯算理直调封装 ----

    private static String[] cb(String tg, String four, boolean strong) throws Exception {
        int[][] p = pf(four);
        return (String[]) stat("detectChengBai",
                new Class<?>[]{String.class, int.class, int[][].class, boolean.class}, tg, p[2][0], p, strong);
    }

    @SuppressWarnings("unchecked")
    private static List<String[]> np(String four) throws Exception {
        int[][] p = pf(four);
        return (List<String[]>) stat("hitNamedPatterns",
                new Class<?>[]{int[][].class, int.class}, p, p[2][0]);
    }

    private static List<String> names(List<String[]> hits) {
        List<String> out = new ArrayList<>();
        for (String[] h : hits) {
            out.add(h[0]);
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static List<Integer> md(String bp, String bg, String bh, String zhiCsv, String ganCsv) throws Exception {
        return (List<Integer>) inst("matchDeityRule",
                new Class<?>[]{String.class, String.class, String.class, String[].class, String[].class},
                bp, bg, bh, arr(zhiCsv), arr(ganCsv));
    }

    // ============================================================ 一、神煞起例（离线降级八类 + 空亡）

    @Test
    @DisplayName("神煞·天乙贵人与将星：甲日见丑支→天乙；申子辰局将星在子")
    void deityTianyiAndJiangxing() throws Exception {
        Map<String, Object> r = run("handleArrangeDeities", map("fourPillars", "甲子 乙丑 甲子 甲子"));
        String d = str(r.get("deities"));
        assertThat(d).as("天乙贵人（月丑）").contains("天乙贵人");
        assertThat(d).as("将星（子）").contains("将星");
    }

    @Test
    @DisplayName("神煞·羊刃：甲日见卯月→羊刃（阳刃唯一在卯）")
    void deityYangren() throws Exception {
        String d = str(run("handleArrangeDeities", map("fourPillars", "甲子 丁卯 甲子 甲子")).get("deities"));
        assertThat(d).as("羊刃").contains("羊刃");
        assertThat(d).as("甲日不应见天乙（丑未皆无）").doesNotContain("天乙贵人");
    }

    @Test
    @DisplayName("神煞·禄与驿马：甲日禄在寅（临官）、申子辰局驿马亦在寅")
    void deityLuAndYima() throws Exception {
        String d = str(run("handleArrangeDeities", map("fourPillars", "甲子 丙寅 甲子 甲子")).get("deities"));
        assertThat(d).as("禄（临官）").contains("禄（临官）");
        assertThat(d).as("驿马").contains("驿马");
    }

    @Test
    @DisplayName("神煞·桃花与华盖：年卯组桃花在子、申子辰局华盖在辰")
    void deityTaohuaAndHuagai() throws Exception {
        String d1 = str(run("handleArrangeDeities", map("fourPillars", "乙卯 甲子 甲子 甲子")).get("deities"));
        assertThat(d1).as("桃花（咸池）").contains("桃花（咸池）");
        assertThat(d1).as("将星（年卯）").contains("将星");

        String d2 = str(run("handleArrangeDeities", map("fourPillars", "甲子 甲子 甲子 戊辰")).get("deities"));
        assertThat(d2).as("华盖（时辰）").contains("华盖");
    }

    @Test
    @DisplayName("神煞·桃花推导来源补注：taohuaDerivNote 应回看年支卯得亥卯未局")
    void deityTaohuaDerivNote() throws Exception {
        int[][] p = pf("乙卯 甲子 甲子 甲子");
        String note = (String) stat("taohuaDerivNote", new Class<?>[]{int[][].class, int.class}, p, 1);
        assertThat(note).as("桃花来源").contains("据年支卯").contains("亥卯未局");
    }

    @Test
    @DisplayName("空亡·不逢空（甲子旬空戌亥，四柱子丑子子不逢）与逢空（时亥空）两分支")
    void voidnessBranches() throws Exception {
        String v1 = str(run("handleArrangeDeities", map("fourPillars", "甲子 乙丑 甲子 甲子")).get("voidness"));
        assertThat(v1).as("旬空戌亥").contains("旬空戌亥").contains("四柱不逢空");

        String v2 = str(run("handleArrangeDeities", map("fourPillars", "甲子 甲子 甲子 乙亥")).get("voidness"));
        assertThat(v2).as("时亥空").contains("时亥空").contains("应期");
    }

    @Test
    @DisplayName("神煞·DEITY_SKIP 黑名单口径：含叙述性类、且不误伤常用八类")
    void deitySkipBlacklist() throws Exception {
        Field f = WORKER.getDeclaredField("DEITY_SKIP");
        f.setAccessible(true);
        @SuppressWarnings("unchecked")
        Set<String> skip = (Set<String>) f.get(null);
        assertThat(skip).as("含叙述性歧义类").contains("六厄", "红鸾", "天喜", "暗禄");
        assertThat(skip).as("不得误伤常用可排布八类")
                .doesNotContain("天乙贵人", "羊刃", "禄", "驿马", "桃花", "华盖", "将星");
    }

    @Test
    @DisplayName("matchDeityRule·日柱精确 / 日干单键 / 日干斜杠多键 / 日干（时）限定")
    void matchDeityRiZhuRiGan() throws Exception {
        assertThat(md("日柱", "丙寅", "", "子,丑,寅,卯", "甲,乙,丙,丁"))
                .as("日柱丙寅精确命中日柱索引2").containsExactly(2);
        assertThat(md("日干", "甲", "丑未", "子,丑,寅,卯", "甲,乙,甲,丁"))
                .as("日干甲→命中丑支索引1").containsExactly(1);
        assertThat(md("日干", "甲/乙", "丑未/子申", "子,寅,辰,午", "甲,乙,乙,丁"))
                .as("斜杠多键按位配对（乙→子申）命中子索引0").containsExactly(0);
        assertThat(md("日干", "甲", "戌亥（时）", "戌,子,寅,卯", "甲,乙,甲,丁"))
                .as("（时）限定：仅在非时柱命中→被剔除").isEmpty();
        assertThat(md("日干", "甲", "戌亥（时）", "子,丑,寅,戌", "甲,乙,甲,丁"))
                .as("（时）限定：正当命中时支索引3").containsExactly(3);
    }

    @Test
    @DisplayName("matchDeityRule·年支/日支 / 年支 / 月支三合 / 月支")
    void matchDeityZhiBases() throws Exception {
        assertThat(md("年支/日支", "子", "丑", "子,丑,寅,卯", "甲,乙,甲,丁"))
                .as("年支子基准→命中丑索引1").containsExactly(1);
        assertThat(md("日支", "寅", "卯", "子,丑,寅,卯", "甲,乙,甲,丁"))
                .as("日支寅基准→命中卯索引3").containsExactly(3);
        assertThat(md("年支", "子", "丑", "子,丑,寅,卯", "甲,乙,甲,丁"))
                .as("年支子基准→命中丑索引1").containsExactly(1);
        assertThat(md("年支", "午", "丑", "子,丑,寅,卯", "甲,乙,甲,丁"))
                .as("年支不匹配→空").isEmpty();
        assertThat(md("月支三合", "申/子/辰", "寅/寅/寅", "子,申,寅,卯", "甲,乙,甲,丁"))
                .as("月支申在多键中→命中寅索引2").containsExactly(2);
        assertThat(md("月支", "寅", "卯", "子,寅,午,卯", "甲,乙,甲,丁"))
                .as("月支寅→干支混比命中卯索引3").containsExactly(3);
    }

    // ============================================================ 二、成败救应（detectChengBai）

    @Test
    @DisplayName("成败·正官格伤官见官：无印待救 / 有印可解")
    void chengBaiZhengGuan() throws Exception {
        String[] dai = cb("正官", "丁卯 丁卯 甲子 丁卯", true);
        assertThat(dai).isNotNull();
        assertThat(dai[0]).as("伤官见官待印").contains("伤官见官").contains("待印");

        String[] jie = cb("正官", "壬寅 丁卯 甲子 丁卯", true);
        assertThat(jie).isNotNull();
        assertThat(jie[0]).as("伤官见官幸印可解").contains("伤官见官").contains("可解");
    }

    @Test
    @DisplayName("成败·偏官格杀旺：无财待食制或印化 / 有财滋杀难制")
    void chengBaiPianGuan() throws Exception {
        String[] dai = cb("偏官", "庚子 庚寅 甲子 庚午", true);
        assertThat(dai).isNotNull();
        assertThat(dai[0]).as("杀旺无制待食印").contains("杀旺无制").contains("待食制或印化");

        String[] zi = cb("偏官", "戊辰 庚寅 甲子 庚午", true);
        assertThat(zi).isNotNull();
        assertThat(zi[0]).as("杀旺逢财滋").contains("杀旺逢财滋");
    }

    @Test
    @DisplayName("成败·财格比劫夺财：身强有官杀可解 / 无救待制 / 身弱不判破(null)")
    void chengBaiCai() throws Exception {
        String[] jie = cb("正财", "乙丑 庚辰 甲子 乙丑", true);
        assertThat(jie).isNotNull();
        assertThat(jie[0]).as("比劫夺财有官杀制劫可解").contains("比劫夺财").contains("可解");

        String[] dai = cb("正财", "乙丑 乙丑 甲子 乙丑", true);
        assertThat(dai).isNotNull();
        assertThat(dai[0]).as("比劫夺财待官杀制劫").contains("比劫夺财").contains("待官杀制劫");

        assertThat(cb("正财", "乙丑 乙丑 甲子 乙丑", false))
                .as("身弱比劫不作夺财论→null").isNull();
    }

    @Test
    @DisplayName("成败·印格贪财坏印：有比劫制财护印可解 / 无待比劫")
    void chengBaiYin() throws Exception {
        String[] jie = cb("正印", "己丑 乙丑 甲子 戊辰", true);
        assertThat(jie).isNotNull();
        assertThat(jie[0]).as("贪财坏印得比劫可解").contains("贪财坏印").contains("可解");

        String[] dai = cb("正印", "己丑 己丑 甲子 戊辰", true);
        assertThat(dai).isNotNull();
        assertThat(dai[0]).as("贪财坏印待比劫").contains("贪财坏印").contains("待比劫制财");
    }

    @Test
    @DisplayName("成败·食神格枭神夺食：有财制枭可解 / 无待财")
    void chengBaiShiShen() throws Exception {
        String[] jie = cb("食神", "壬子 戊辰 甲子 戊辰", true);
        assertThat(jie).isNotNull();
        assertThat(jie[0]).as("枭神夺食得财制枭可解").contains("枭神夺食").contains("可解");

        String[] dai = cb("食神", "壬子 壬子 甲子 壬子", true);
        assertThat(dai).isNotNull();
        assertThat(dai[0]).as("枭神夺食待财制枭").contains("枭神夺食").contains("待财制枭");
    }

    @Test
    @DisplayName("成败·伤官格伤官见官：有印可解 / 无印待制")
    void chengBaiShangGuan() throws Exception {
        String[] jie = cb("伤官", "辛丑 壬子 甲子 癸丑", true);
        assertThat(jie).isNotNull();
        assertThat(jie[0]).as("伤官见官幸印可解").contains("伤官见官").contains("可解");

        String[] dai = cb("伤官", "辛丑 辛丑 甲子 辛丑", true);
        assertThat(dai).isNotNull();
        assertThat(dai[0]).as("伤官见官待印制伤").contains("伤官见官").contains("待印制伤");
    }

    // ============================================================ 三、具名格局（hitNamedPatterns）

    @Test
    @DisplayName("具名·魁罡：成立 / 透财破 / 日支逢冲破 三态")
    void namedKuiGang() throws Exception {
        assertThat(names(np("庚戌 辛巳 庚戌 辛巳"))).as("魁罡成立").contains("魁罡");
        assertThat(names(np("庚戌 甲申 庚戌 辛巳"))).as("透财破格不出").doesNotContain("魁罡");
        assertThat(names(np("庚戌 庚辰 庚戌 辛巳"))).as("日支逢辰冲破不出").doesNotContain("魁罡");
    }

    @Test
    @DisplayName("具名·日贵（丁酉）与日德（甲寅）")
    void namedRiGuiRiDe() throws Exception {
        assertThat(names(np("甲子 甲子 丁酉 甲子"))).as("日贵").contains("日贵");
        assertThat(names(np("甲子 甲子 甲寅 甲子"))).as("日德").contains("日德");
    }

    @Test
    @DisplayName("具名·日禄归时：正格 / 月支重见兼格 / 见官破格 三态")
    void namedRiLuGuiShi() throws Exception {
        assertThat(names(np("甲子 丙子 甲子 丙寅"))).as("日禄归时正格").contains("日禄归时");
        assertThat(names(np("甲子 丙寅 甲子 丙寅"))).as("月支重见兼格")
                .contains("日禄归时（兼格·月支重见，建禄为主、归禄不纯）");
        assertThat(names(np("甲子 丙子 甲子 辛寅"))).as("见官破格不出").doesNotContain("日禄归时");
    }

    @Test
    @DisplayName("具名·六乙鼠贵：乙日时子无午成立 / 时午破格不出")
    void namedLiuYiShuGui() throws Exception {
        assertThat(names(np("乙丑 乙丑 乙丑 丙子"))).as("六乙鼠贵成立").contains("六乙鼠贵");
        assertThat(names(np("乙丑 乙丑 乙丑 丙午"))).as("见午破格不出").doesNotContain("六乙鼠贵");
    }

    @Test
    @DisplayName("具名·壬骑龙背：辰多（≥2）成立 / 单辰不成立")
    void namedRenQiLongBei() throws Exception {
        assertThat(names(np("壬辰 甲辰 壬辰 甲辰"))).as("辰多成立").contains("壬骑龙背");
        assertThat(names(np("壬申 丙子 壬辰 甲子"))).as("单辰不出").doesNotContain("壬骑龙背");
    }

    // ============================================================ 四、月令冲 / 合 / 旬空（determinePattern）

    @Test
    @DisplayName("月令逢冲·格神未透 → 格有破（月令逢冲·待救应）")
    void patternMonthChong() throws Exception {
        Map<String, Object> r = run("handleDeterminePattern",
                map("fourPillars", "甲子 辛酉 甲子 乙卯", "strength", "身强（得令）"));
        assertThat(str(r.get("patternGrade"))).as("月令逢冲待救应").contains("月令逢冲").contains("待救应");
    }

    @Test
    @DisplayName("月令逢合·格神未透 → 格有绊")
    void patternMonthHe() throws Exception {
        Map<String, Object> r = run("handleDeterminePattern",
                map("fourPillars", "甲子 辛酉 甲子 乙辰", "strength", "身强（得令）"));
        assertThat(str(r.get("patternGrade"))).as("月令逢合为绊").contains("格有绊");
    }

    @Test
    @DisplayName("月支旬空·格神未透 → 格将成（格神未透·月令空亡落空·待填实引透）")
    void patternMonthKong() throws Exception {
        Map<String, Object> r = run("handleDeterminePattern",
                map("fourPillars", "甲子 乙亥 甲子 甲子", "strength", "身强（得令）"));
        assertThat(str(r.get("patternGrade"))).as("月令空亡落空").contains("月令空亡落空");
    }

    @Test
    @DisplayName("月令逢冲 + 已判成败救应：只追加注记、不覆盖 grade（伤官见官仍为主判）")
    void patternMonthChongAppendOnly() throws Exception {
        Map<String, Object> r = run("handleDeterminePattern",
                map("fourPillars", "甲子 辛酉 甲子 丁卯", "strength", "身强（得令）"));
        assertThat(str(r.get("patternGrade"))).as("grade 仍为伤官见官").contains("伤官见官");
        assertThat(str(r.get("patternGrade"))).as("grade 不被月令逢冲覆盖").doesNotContain("月令逢冲");
        assertThat(str(r.get("rescueAdjust"))).as("rescue 追加格根动摇注记").contains("格根动摇");
    }

    @Test
    @DisplayName("阳刃格月令逢冲 + 格成：覆写为格有破（月令逢冲·格神动摇）")
    void patternYangRenMonthChongOverride() throws Exception {
        Map<String, Object> r = run("handleDeterminePattern",
                map("fourPillars", "辛酉 乙卯 甲子 甲子", "strength", "身强（得令）"));
        assertThat(str(r.get("patternGrade"))).as("格神动摇").contains("格神动摇");
    }
}
