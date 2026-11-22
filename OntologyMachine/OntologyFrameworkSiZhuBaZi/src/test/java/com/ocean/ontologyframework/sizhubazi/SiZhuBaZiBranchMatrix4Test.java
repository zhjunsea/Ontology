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
 * 四柱命理 · 分支矩阵补充（第四阶段）· 十神宫位 / 旺衰六档 / 取用多法冲突（纯离线）。
 *
 * <p>前三批覆盖算理主链、下游网关、神煞·成败救应·具名格局·月令冲合空；本类补齐三处深层维度：
 * <ul>
 *   <li>十神宫位（assignTenGodsKin）：十神 10 类映射（tenGodName）、天干/藏干十神文本与六亲宫位、
 *       十神生克制化 4 分支（官杀混杂 / 食伤制杀 / 财官相生 / 无明显）；</li>
 *   <li>旺衰（assessDayMasterStrength）：6 档逐档专门用例（身强得令 / 中和偏强得令 / 中和偏强失令但得地得势 /
 *       身弱 / 中和偏弱 / 中和）+ 结构化 balance 与十二宫；</li>
 *   <li>取用多法冲突（selectYongShen）：effectiveYongElem 调候（火炎/水寒）/ 印比重 / 扶抑 / 默认真实分支；
 *       yongShenElem 建禄改取（官杀＞财）；isYinBiZhongYong 判据；以及 handler 主取分支（格局法/扶抑/调候）。</li>
 * </ul>
 *
 * <p>断言以程序结构化字段为唯一真相源：Handler 经 Mockito 承接 completeResult 读回 result；
 * 纯算理方法（tenGodName/tenGodSet/effectiveYongElem/yongShenElem/isYinBiZhongYong）经反射直调。
 */
@DisplayName("四柱命理 · 分支矩阵补充（第四阶段·十神宫位/旺衰六档/取用多法冲突）")
class SiZhuBaZiBranchMatrix4Test {

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

    private static int[][] pf(String four) {
        String[] parts = four.trim().split(" ");
        int[][] out = new int[4][2];
        for (int i = 0; i < 4; i++) {
            out[i][0] = idx(GAN, parts[i].substring(0, 1));
            out[i][1] = idx(ZHI, parts[i].substring(1, 2));
        }
        return out;
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

    // ============================================================ 一、十神宫位（assignTenGodsKin）

    @Test
    @DisplayName("十神·十类映射：甲日对十天干→比/劫/食/伤/偏财/正财/偏官/正官/偏印/正印")
    void tenGodNameTenClasses() throws Exception {
        String[] expect = {"比肩", "劫财", "食神", "伤官", "偏财", "正财", "偏官", "正官", "偏印", "正印"};
        for (int t = 0; t < 10; t++) {
            String got = (String) inst("tenGodName", new Class<?>[]{int.class, int.class}, 0, t);
            assertThat(got).as("甲日对%s→%s", GAN[t], expect[t]).isEqualTo(expect[t]);
        }
    }

    @Test
    @DisplayName("十神·生克制化 4 分支：官杀混杂 / 食伤制杀 / 财官相生 / 无明显")
    void tenGodRelationsCombos() throws Exception {
        String r1 = str(run("handleTenGodsKin", map("fourPillars", "庚子 辛酉 甲子 甲子")).get("tenGodsRelations"));
        assertThat(r1).as("官杀混杂").contains("官杀混杂").doesNotContain("财官相生");

        String r2 = str(run("handleTenGodsKin", map("fourPillars", "丙子 庚子 甲子 甲子")).get("tenGodsRelations"));
        assertThat(r2).as("食伤制杀").contains("食伤制杀");

        String r3 = str(run("handleTenGodsKin", map("fourPillars", "戊子 辛子 甲子 甲子")).get("tenGodsRelations"));
        assertThat(r3).as("财官相生").contains("财官相生");

        String r4 = str(run("handleTenGodsKin", map("fourPillars", "甲子 甲子 甲子 甲子")).get("tenGodsRelations"));
        assertThat(r4).as("无明显").isEqualTo("无明显十神生克制化");
    }

    @Test
    @DisplayName("十神·天干/藏干文本与六亲宫位：日主比肩、透干十神、藏干十神、六亲固定口径齐备")
    void tenGodTextAndKinMapping() throws Exception {
        Map<String, Object> r = run("handleTenGodsKin", map("fourPillars", "丙子 庚子 甲子 甲子"));
        String tg = str(r.get("tenGods"));
        assertThat(tg).as("日主比肩").contains("日主甲→比肩（日主）");
        assertThat(tg).as("天干十神：年丙食神").contains("年丙→食神");
        assertThat(tg).as("天干十神：月庚偏官").contains("月庚→偏官");
        assertThat(tg).as("藏干十神：年子藏癸(正印)").contains("年子藏癸(正印)");

        String kin = str(r.get("kinMapping"));
        assertThat(kin).as("宫位").contains("年柱宫：祖上/父母").contains("时柱宫：子息");
        assertThat(kin).as("六亲分性别").contains("女命以官杀为夫");
    }

    // ============================================================ 二、旺衰六档（assessDayMasterStrength）

    private static String strengthOf(String four) throws Exception {
        return str(run("handleDayMasterStrength", map("fourPillars", four)).get("strength"));
    }

    @Test
    @DisplayName("旺衰档①身强（得令）：得令且通根≥2")
    void strengthShenQiangDeLing() throws Exception {
        assertThat(strengthOf("甲寅 丙寅 甲子 甲子")).as("得令+通根2").isEqualTo("身强（得令）");
    }

    @Test
    @DisplayName("旺衰档②中和偏强（得令）：得令而根助未达身强")
    void strengthZhongHePianQiangDeLing() throws Exception {
        assertThat(strengthOf("庚子 丙子 甲子 庚子")).as("得令而无根无助").isEqualTo("中和偏强（得令）");
    }

    @Test
    @DisplayName("旺衰档③中和偏强（失令但得地得势）：失令而通根≥2且得势≥2")
    void strengthZhongHePianQiangShiLing() throws Exception {
        assertThat(strengthOf("甲寅 壬申 甲寅 甲寅")).as("失令得地得势")
                .isEqualTo("中和偏强（失令但得地得势）");
    }

    @Test
    @DisplayName("旺衰档④身弱（失令、无根、寡助）：通根=0且得势=0")
    void strengthShenRuo() throws Exception {
        assertThat(strengthOf("庚申 辛酉 甲子 庚午")).as("无根寡助").contains("身弱");
    }

    @Test
    @DisplayName("旺衰档⑤中和偏弱（失令、克泄耗偏多）：失令且克泄耗≥2")
    void strengthZhongHePianRuo() throws Exception {
        assertThat(strengthOf("壬申 辛酉 甲子 丙午")).as("失令克泄耗多").contains("中和偏弱");
    }

    @Test
    @DisplayName("旺衰档⑥中和：失令而不偏强不偏弱（根助零散、克泄耗不足2）")
    void strengthZhongHe() throws Exception {
        assertThat(strengthOf("甲子 甲辰 甲子 甲子")).as("纯中和").isEqualTo("中和");
    }

    @Test
    @DisplayName("旺衰·结构化 balance 与十二宫：得令/通根/得势计数与逐支长生输出")
    void strengthBalancePalaces() throws Exception {
        Map<String, Object> r = run("handleDayMasterStrength", map("fourPillars", "甲寅 丙寅 甲子 甲子"));
        assertThat(str(r.get("balance"))).as("balance 计数").contains("得令=true").contains("通根=2").contains("得势=2");
        String pal = str(r.get("twelvePalaces"));
        assertThat(pal).as("十二宫逐支").contains("年寅：").contains("时子：");
    }

    // ============================================================ 三、取用多法冲突（selectYongShen）

    @Test
    @DisplayName("yongShenElem·月令格局法：普通月令取月令五行；建禄月令改取官杀＞财")
    void yongShenElemBranches() throws Exception {
        assertThat((int) stat("yongShenElem", new Class<?>[]{int[][].class}, (Object) pf("甲子 壬申 甲子 甲子")))
                .as("月令申金→用金(3)").isEqualTo(3);
        assertThat((int) stat("yongShenElem", new Class<?>[]{int[][].class}, (Object) pf("甲子 乙卯 甲子 庚午")))
                .as("建禄卯月→取官杀金(3)").isEqualTo(3);
        assertThat((int) stat("yongShenElem", new Class<?>[]{int[][].class}, (Object) pf("甲子 乙卯 甲子 戊午")))
                .as("建禄卯月无官→取财土(2)").isEqualTo(2);
    }

    @Test
    @DisplayName("effectiveYongElem·调候救偏（火炎）：火日火月无水→取水(4)")
    void effectiveYongTiaohouFire() throws Exception {
        int[][] p = pf("丙午 甲午 丙午 戊戌");
        assertThat((int) stat("effectiveYongElem", new Class<?>[]{int[][].class, int.class, boolean.class}, p, p[2][0], true))
                .as("火炎土燥取水").isEqualTo(4);
    }

    @Test
    @DisplayName("effectiveYongElem·调候救偏（水寒）：水日水月无火→取火(1)")
    void effectiveYongTiaohouWater() throws Exception {
        int[][] p = pf("壬子 壬子 壬子 壬辰");
        assertThat((int) stat("effectiveYongElem", new Class<?>[]{int[][].class, int.class, boolean.class}, p, p[2][0], true))
                .as("水寒取火").isEqualTo(1);
    }

    @Test
    @DisplayName("effectiveYongElem / isYinBiZhongYong·印比重：甲日印水月、身强→改取食伤泄秀/判据为真")
    void effectiveYongYinBiZhong() throws Exception {
        int[][] p = pf("甲子 癸子 甲子 甲子");
        assertThat((boolean) stat("isYinBiZhongYong", new Class<?>[]{int[][].class, int.class, boolean.class}, p, p[2][0], true))
                .as("印比重判据为真").isTrue();
        assertThat((int) stat("yongShenElem", new Class<?>[]{int[][].class}, (Object) p))
                .as("月令印水").isEqualTo(4);
    }

    @Test
    @DisplayName("effectiveYongElem·扶抑：身弱财月，四柱有印→取印(4)；无印→取日主比劫(0)")
    void effectiveYongFuYi() throws Exception {
        int[][] hasYin = pf("甲子 戊辰 甲子 甲子");
        assertThat((int) stat("effectiveYongElem", new Class<?>[]{int[][].class, int.class, boolean.class}, hasYin, hasYin[2][0], false))
                .as("身弱财月有印取水(4)").isEqualTo(4);
        int[][] noYin = pf("甲寅 戊戌 甲寅 甲寅");
        assertThat((int) stat("effectiveYongElem", new Class<?>[]{int[][].class, int.class, boolean.class}, noYin, noYin[2][0], false))
                .as("身弱财月无印取比劫木(0)").isEqualTo(0);
    }

    @Test
    @DisplayName("effectiveYongElem·默认：非调候非印比非扶抑→取月令格神五行")
    void effectiveYongDefault() throws Exception {
        int[][] p = pf("甲子 壬申 甲子 甲子");
        assertThat((int) stat("effectiveYongElem", new Class<?>[]{int[][].class, int.class, boolean.class}, p, p[2][0], true))
                .as("身强官杀月取月令金(3)").isEqualTo(3);
    }

    @Test
    @DisplayName("selectYongShen·主取格局法（月令格局法）：相神/忌神/调候/方法齐备")
    void selectYongShenGeJu() throws Exception {
        Map<String, Object> r = run("handleSelectYongShen", map("fourPillars", "甲子 壬申 甲子 甲子", "strength", "身强（得令）"));
        assertThat(str(r.get("yongShenMethods"))).as("主取格局法").contains("【主取】格局法（月令格局法）");
        assertThat(str(r.get("yongShen"))).as("用神").contains("月令格局法");
        assertThat(str(r.get("xiangShen"))).as("相神").startsWith("相神：");
        assertThat(str(r.get("jiShen"))).as("忌神").startsWith("忌神：");
        assertThat(str(r.get("tiaohou"))).as("调候").contains("调候用神");
    }

    @Test
    @DisplayName("selectYongShen·主取扶抑为主（身弱不任财官）：改取印为先、比劫为次")
    void selectYongShenFuYi() throws Exception {
        Map<String, Object> r = run("handleSelectYongShen", map("fourPillars", "甲子 戊辰 甲子 甲子", "strength", "身弱（失令、无根、寡助）"));
        assertThat(str(r.get("yongShenMethods"))).as("主取扶抑").contains("【主取】扶抑为主");
        assertThat(str(r.get("yongShen"))).as("不能任财官").contains("身不能任财官");
    }

    @Test
    @DisplayName("selectYongShen·主取调候救偏（火炎）：润局救偏为先")
    void selectYongShenTiaohou() throws Exception {
        Map<String, Object> r = run("handleSelectYongShen", map("fourPillars", "丙午 甲午 丙午 戊戌", "strength", "身强（得令）"));
        assertThat(str(r.get("yongShenMethods"))).as("主取调候").contains("【主取】调候救偏法");
        assertThat(str(r.get("yongShen"))).as("调候救偏为主").contains("调候救偏为主");
    }
}
