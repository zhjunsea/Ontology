package com.ocean.ontologyframework.ziweidoushu;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code judgeMingGongGe}（卷二·命宫 c3 L459-976）**深条件分支注入补测**。
 *
 * <p>全环境实测该法残余 9 条分支（jacoco：L3927/L3944/L3995–L3998/L4006），均为「真数据才触发」的深条件：
 * 语料缺「命宫」块（rows==null）、末星块（end=size）、煞凑布尔（羊陀火铃冲会×含/不含陷地）、
 * 二次待考置位。本类以 {@code PALACE_CLASSIC} 反射注入**照《全書》真实句式**的合成语料驱动之，
 * 证明这些经典确有表达下逻辑正确、可达（非臆造：L722「辰宫安命六庚生人吉利，羊陀火铃冲会，又在陷地残疾下局」即原型）。</p>
 */
@DisplayName("judgeMingGongGe 深条件分支注入补测（空语料/末块/煞凑/待考）")
class ZiWeiDouShuJudgeMingGongGeInjectionTest {

    private static Object invoke(String name, Class<?>[] types, Object... args) {
        try {
            Method m = ZiWeiDouShuOntologyJobWorker.class.getDeclaredMethod(name, types);
            m.setAccessible(true);
            return m.invoke(null, args);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private static void setStatic(String field, Object value) {
        try {
            Field f = ZiWeiDouShuOntologyJobWorker.class.getDeclaredField(field);
            f.setAccessible(true);
            f.set(null, value);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    @AfterEach
    void reset() {
        setStatic("PALACE_CLASSIC", null);
    }

    private static final Class<?>[] FOUR = {String.class, int.class, int.class, Map.class};

    private static String ge(String star, int palaceZhi, int gan, Map<Integer, List<String>> at) {
        return (String) invoke("judgeMingGongGe", FOUR, star, palaceZhi, gan, at);
    }

    private static List<String[]> rows(String[]... rs) {
        return new ArrayList<>(Arrays.asList(rs));
    }

    private static String[] row(String no, String text) {
        return new String[]{no, text};
    }

    private static void palace(List<String[]> ming) {
        Map<String, List<String[]>> m = new LinkedHashMap<>();
        m.put("命宫", new ArrayList<>(ming));
        setStatic("PALACE_CLASSIC", m);
    }

    /** 含四煞（羊陀火铃）于子宫——令煞凑「三方四正」悉见。 */
    private static Map<Integer, List<String>> shaAt() {
        Map<Integer, List<String>> m = new LinkedHashMap<>();
        m.put(0, Arrays.asList("擎羊", "陀罗", "火星", "铃星"));
        return m;
    }

    @Test
    @DisplayName("rows==null 与 star==null 两侧守卫")
    void nullGuards() {
        // 无「命宫」块 → rows==null
        setStatic("PALACE_CLASSIC", new LinkedHashMap<String, List<String[]>>());
        assertThat(ge("紫微", 0, 6, shaAt())).isEmpty();
        // 有「命宫」块但 star==null
        palace(rows(row("9001", "紫微，子宫庚又生人平吉。")));
        assertThat(ge(null, 0, 6, shaAt())).isEmpty();
    }

    @Test
    @DisplayName("末星块 end=size + 煞凑布尔(不含陷/含陷，满足与不满足)")
    void shaCouBoolean() {
        // 单/双行、紫微为唯一（末）块：覆盖 end 循环耗尽分支
        palace(rows(
                row("722", "紫微，子宫庚又生人羊陀火铃冲会，主残疾下局。"),
                row("722b", "续句无星名开头。")));
        String hit = ge("紫微", 0, 6, shaAt());
        assertThat(hit).contains("煞凑").contains("羊陀火铃冲会").contains("→凶");

        // 含「陷」→ needXian=true，叠验庙陷
        palace(rows(row("723", "紫微，子宫庚又生人羊陀火铃冲会又在陷地。")));
        String xian = ge("紫微", 0, 6, shaAt());
        assertThat(xian).contains("煞凑").contains("在陷地=");

        // 煞曜不齐 → sat=false（不满足）
        palace(rows(row("724", "紫微，子宫庚又生人羊陀火铃冲会。")));
        Map<Integer, List<String>> none = new LinkedHashMap<>();
        none.put(5, Arrays.asList("天府"));
        String unsat = ge("紫微", 0, 6, none);
        assertThat(unsat).contains("煞凑").contains("不满足");
    }

    @Test
    @DisplayName("二次待考：tentative 首置位(true) 与 已置位(false)")
    void tentativeTwice() {
        palace(rows(row("730", "紫微，子宫庚又生人平吉，子宫庚再生人平常。")));
        String r = ge("紫微", 0, 6, new LinkedHashMap<>());
        assertThat(r).contains("待考");
    }

    @Test
    @DisplayName("needXian=true 且命宫本星落陷 → xianOk=true（『在陷地=是』→凶）")
    void xianFallenTrue() {
        // 天机@丑 为「陷」：needXian 真 + isFallen 真 → 补齐 xianOk 真侧与描述「是」侧
        palace(rows(row("725", "天机，丑宫庚又生人羊陀火铃冲会又在陷地。")));
        Map<Integer, List<String>> at = new LinkedHashMap<>();
        at.put(1, Arrays.asList("擎羊", "陀罗", "火星", "铃星"));
        String r = ge("天机", 1, 6, at);
        assertThat(r).contains("煞凑").contains("在陷地=是").contains("→凶");
    }

}
