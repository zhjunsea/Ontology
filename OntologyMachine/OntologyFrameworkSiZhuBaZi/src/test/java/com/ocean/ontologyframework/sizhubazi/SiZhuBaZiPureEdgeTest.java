package com.ocean.ontologyframework.sizhubazi;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 纯函数边界分支补齐：parsePillars / resolveBirthDateTime / birthCalendar / birthPlaceOf /
 * zhiToStartHour / toInt / extractLongitude / correctBirthDateTime / lunarToSolar / inCategory / tianKeDiChong。
 */
@DisplayName("纯函数边界·出生解析/历法/工具 全分支")
class SiZhuBaZiPureEdgeTest {

    private static final Class<?> WORKER = SiZhuBaZiOntologyJobWorker.class;

    private static Object stat(String name, Class<?>[] t, Object... a) throws Exception {
        Method m = WORKER.getDeclaredMethod(name, t);
        m.setAccessible(true);
        try { return m.invoke(null, a); }
        catch (java.lang.reflect.InvocationTargetException e) {
            Throwable c = e.getCause();
            if (c instanceof RuntimeException re) { throw re; }
            if (c instanceof Error er) { throw er; }
            throw e;
        }
    }

    private static Object inst(Object w, String name, Class<?>[] t, Object... a) throws Exception {
        Method m = WORKER.getDeclaredMethod(name, t);
        m.setAccessible(true);
        try { return m.invoke(w, a); }
        catch (java.lang.reflect.InvocationTargetException e) {
            Throwable c = e.getCause();
            if (c instanceof RuntimeException re) { throw re; }
            if (c instanceof Error er) { throw er; }
            throw e;
        }
    }

    private static SiZhuBaZiOntologyJobWorker newWorker() throws Exception {
        Constructor<?> c = WORKER.getDeclaredConstructor();
        c.setAccessible(true);
        return (SiZhuBaZiOntologyJobWorker) c.newInstance();
    }

    private static Map<String, Object> map(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) { m.put(String.valueOf(kv[i]), kv[i + 1]); }
        return m;
    }

    @Test
    @DisplayName("parsePillars：缺柱补甲子 / 单字补甲子 / 非法干→0 / 非法支→0 / null")
    void parsePillarsEdges() throws Exception {
        int[][] shortP = (int[][]) stat("parsePillars", new Class<?>[]{String.class}, "甲子 乙丑");
        assertThat(shortP[2]).containsExactly(0, 0);
        int[][] oneChar = (int[][]) stat("parsePillars", new Class<?>[]{String.class}, "甲 乙丑 丙寅 丁卯");
        assertThat(oneChar[0]).containsExactly(0, 0);
        int[][] badGan = (int[][]) stat("parsePillars", new Class<?>[]{String.class}, "X子 甲子 甲子 甲子");
        assertThat(badGan[0][0]).isEqualTo(0);
        int[][] badZhi = (int[][]) stat("parsePillars", new Class<?>[]{String.class}, "甲X 甲子 甲子 甲子");
        assertThat(badZhi[0][1]).isEqualTo(0);
        int[][] nullP = (int[][]) stat("parsePillars", new Class<?>[]{String.class}, (Object) null);
        assertThat(nullP[0]).containsExactly(0, 0);
    }

    @Test
    @DisplayName("resolveBirthDateTime：缺字段抛错 / 缺分默认0 / 有分 / 时支 / 空串抛错")
    void resolveBirthEdges() throws Exception {
        assertThatThrownBy(() -> stat("resolveBirthDateTime", new Class<?>[]{Object.class},
                map("month", 5, "day", 15))).isInstanceOf(IllegalArgumentException.class);
        LocalDateTime noMin = (LocalDateTime) stat("resolveBirthDateTime", new Class<?>[]{Object.class},
                map("year", 1990, "month", 5, "day", 15, "hour", 8));
        assertThat(noMin.getMinute()).isEqualTo(0);
        LocalDateTime withMin = (LocalDateTime) stat("resolveBirthDateTime", new Class<?>[]{Object.class},
                map("year", 1990, "month", 5, "day", 15, "hour", 8, "minute", 30));
        assertThat(withMin.getMinute()).isEqualTo(30);
        LocalDateTime zhiHr = (LocalDateTime) stat("resolveBirthDateTime", new Class<?>[]{Object.class},
                map("year", 1990, "month", 5, "day", 15, "hourZhi", "辰"));
        assertThat(zhiHr).isNotNull();
        assertThatThrownBy(() -> stat("resolveBirthDateTime", new Class<?>[]{Object.class}, "   "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("birthCalendar / birthPlaceOf：Map 有值/无值/非Map")
    void birthMiscEdges() throws Exception {
        assertThat((String) stat("birthCalendar", new Class<?>[]{Object.class}, map("calendar", "农历"))).isEqualTo("农历");
        assertThat((String) stat("birthCalendar", new Class<?>[]{Object.class}, map())).isEqualTo("公历");
        assertThat((String) stat("birthCalendar", new Class<?>[]{Object.class}, "str")).isEqualTo("公历");
        assertThat((String) stat("birthPlaceOf", new Class<?>[]{Object.class}, map("birthPlace", "北京"))).isEqualTo("北京");
        assertThat((String) stat("birthPlaceOf", new Class<?>[]{Object.class}, map())).isEqualTo("");
        assertThat((String) stat("birthPlaceOf", new Class<?>[]{Object.class}, "str")).isEqualTo("");
    }

    @Test
    @DisplayName("zhiToStartHour：合法/空抛错/非法抛错")
    void zhiToStartHourEdges() throws Exception {
        assertThat((Integer) stat("zhiToStartHour", new Class<?>[]{String.class}, "子")).isEqualTo(23);
        assertThat((Integer) stat("zhiToStartHour", new Class<?>[]{String.class}, "丑")).isEqualTo(1);
        assertThatThrownBy(() -> stat("zhiToStartHour", new Class<?>[]{String.class}, (Object) null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> stat("zhiToStartHour", new Class<?>[]{String.class}, "X"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("toInt：null/Number/数字串/空串/非数字/小数")
    void toIntEdges() throws Exception {
        assertThat(stat("toInt", new Class<?>[]{Object.class}, (Object) null)).isNull();
        assertThat((Integer) stat("toInt", new Class<?>[]{Object.class}, 5)).isEqualTo(5);
        assertThat((Integer) stat("toInt", new Class<?>[]{Object.class}, "7")).isEqualTo(7);
        assertThat(stat("toInt", new Class<?>[]{Object.class}, "  ")).isNull();
        assertThat(stat("toInt", new Class<?>[]{Object.class}, "abc")).isNull();
        assertThat((Integer) stat("toInt", new Class<?>[]{Object.class}, 3.9)).isEqualTo(3);
    }

    @Test
    @DisplayName("extractLongitude：空(null)/无匹配(null)/有效/四位年份(null)/越界(null)")
    void extractLongitudeEdges() throws Exception {
        Class<?>[] t = {String.class};
        assertThat(stat("extractLongitude", t, (Object) null)).isNull();
        assertThat(stat("extractLongitude", t, "  ")).isNull();
        assertThat((Double) stat("extractLongitude", t, "东经116.4")).isEqualTo(116.4);
        assertThat(stat("extractLongitude", t, "1990")).isNull();
        assertThat(stat("extractLongitude", t, "abc")).isNull();
        assertThat(stat("extractLongitude", t, "300")).isNull();
    }

    @Test
    @DisplayName("correctBirthDateTime：公历+经度 / 农历有效 / 农历越界保留原值(conv null)")
    void correctBirthEdges() throws Exception {
        SiZhuBaZiOntologyJobWorker w = newWorker();
        LocalDateTime a = (LocalDateTime) inst(w, "correctBirthDateTime",
                new Class<?>[]{Object.class, String.class, String.class}, "1990-05-15 12:00", "公历", "东经116.4");
        assertThat(a).isNotNull();
        LocalDateTime b = (LocalDateTime) inst(w, "correctBirthDateTime",
                new Class<?>[]{Object.class, String.class, String.class},
                map("year", 2020, "month", 4, "day", 15, "hour", 8), "农历", "");
        assertThat(b).isNotNull();
        LocalDateTime c = (LocalDateTime) inst(w, "correctBirthDateTime",
                new Class<?>[]{Object.class, String.class, String.class}, map("year", 1899, "month", 5, "day", 5, "hour", 8), "农历", "");
        assertThat(c).isNotNull(); // 农历越界→conv null→保留原值
    }

    @Test
    @DisplayName("lunarToSolar：缺年(null)/时支/时分")
    void lunarToSolarEdges() throws Exception {
        SiZhuBaZiOntologyJobWorker w = newWorker();
        Class<?>[] t = {Object.class};
        assertThat(inst(w, "lunarToSolar", t, map("month", 4, "day", 15))).isNull();
        assertThat(inst(w, "lunarToSolar", t, map("year", 2020, "month", 4, "day", 15, "hourZhi", "子"))).isNotNull();
        assertThat(inst(w, "lunarToSolar", t, map("year", 2020, "month", 4, "day", 15, "hour", 8, "minute", 20))).isNotNull();
    }

    @Test
    @DisplayName("inCategory / tianKeDiChong：双操作数/非法/反向 补充分支")
    void categoryAndTianKeEdges() throws Exception {
        Class<?>[] s2 = {String.class, String.class};
        assertThat((Boolean) stat("inCategory", s2, "劫财", "比劫")).isTrue();
        assertThat((Boolean) stat("inCategory", s2, "正印", "印")).isTrue();
        assertThat((Boolean) stat("inCategory", s2, "偏财", "财")).isTrue();
        assertThat((Boolean) stat("inCategory", s2, "正官", "官杀")).isTrue();
        assertThat((Boolean) stat("inCategory", s2, "食神", "印")).isFalse();
        assertThat((Boolean) stat("inCategory", s2, "正财", "官杀")).isFalse();
        assertThat((Boolean) stat("inCategory", s2, "比肩", "食伤")).isFalse();
        assertThat((Boolean) stat("tianKeDiChong", s2, "甲X", "庚午")).isFalse();
        assertThat((Boolean) stat("tianKeDiChong", s2, "庚午", "甲子")).isTrue();
        assertThat((Boolean) stat("tianKeDiChong", s2, "甲子", "丙戌")).isFalse();
        assertThat((Boolean) stat("tianKeDiChong", s2, "甲子", "甲子")).isFalse();
    }

    @Test
    @DisplayName("多操作数边界：resolveBirth缺月/缺日、lunarToSolar缺月/缺日与时支空白、extractLongitude四位串/负经度")
    void operandEdges() throws Exception {
        assertThatThrownBy(() -> stat("resolveBirthDateTime", new Class<?>[]{Object.class}, map("day", 15)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> stat("resolveBirthDateTime", new Class<?>[]{Object.class}, map("year", 1990, "month", 5)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> stat("resolveBirthDateTime", new Class<?>[]{Object.class}, map("year", 1990, "day", 15)))
                .isInstanceOf(IllegalArgumentException.class);
        SiZhuBaZiOntologyJobWorker w = newWorker();
        Class<?>[] t = {Object.class};
        assertThat(inst(w, "lunarToSolar", t, map("year", 2020, "day", 15))).isNull();   // 缺月
        assertThat(inst(w, "lunarToSolar", t, map("year", 2020, "month", 4))).isNull();  // 缺日
        assertThat(inst(w, "lunarToSolar", t, map("year", 2020, "month", 4, "day", 15, "hourZhi", " "))).isNotNull(); // 时支空白
        Class<?>[] s1 = {String.class};
        assertThat(stat("extractLongitude", s1, "116.4(1990)")).isNull();       // 含四位数字符串 → 排除
        assertThat((Double) stat("extractLongitude", s1, "东经-116.4")).isEqualTo(-116.4);
    }
}


