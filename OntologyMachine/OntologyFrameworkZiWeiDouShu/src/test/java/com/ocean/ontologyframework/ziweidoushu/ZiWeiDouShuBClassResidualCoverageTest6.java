package com.ocean.ontologyframework.ziweidoushu;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * B 类补齐·⑥ 入参解析/历法残余分支批次（离线反射）。
 * 覆盖 birthCalendar / resolveBirthDateTime / parseChartBase / representativeHour / isBranch，
 * 以及 sihuaStars / ziweiBranch / lucunBranch / liuyangBranch / liutuoBranch / wuhuYinGan / deityName
 * 的 ONTO_* 缓存「命中/缺失回退」对侧分支。
 */
@DisplayName("B类补齐·入参解析历法与缓存回退残余分支批次")
class ZiWeiDouShuBClassResidualCoverageTest6 {

    private static final String[] ONTO_FIELDS = {
            "ONTO_SIHUA", "ONTO_ZIWEI", "ONTO_LUCUN", "ONTO_LIUYANG",
            "ONTO_LIUTUO", "ONTO_DEITY_12", "ONTO_WUHU"};

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
    void resetStatics() {
        for (String f : ONTO_FIELDS) {
            setStatic(f, null);
        }
    }

    @Test
    @DisplayName("birthCalendar：Map/字符串/空值 → 农历/公历")
    void birthCalendarBranches() {
        Map<String, Object> withCal = new LinkedHashMap<>();
        withCal.put("calendar", "农历");
        assertThat((String) invoke("birthCalendar", new Class<?>[]{Object.class}, withCal)).isEqualTo("农历");

        Map<String, Object> noCal = new LinkedHashMap<>();
        noCal.put("year", "1990");
        assertThat((String) invoke("birthCalendar", new Class<?>[]{Object.class}, noCal)).isEqualTo("公历");

        assertThat((String) invoke("birthCalendar", new Class<?>[]{Object.class}, (Object) "农历1990年正月初一")).isEqualTo("农历");
        assertThat((String) invoke("birthCalendar", new Class<?>[]{Object.class}, (Object) "1990-01-01")).isEqualTo("公历");
        assertThat((String) invoke("birthCalendar", new Class<?>[]{Object.class}, (Object) null)).isEqualTo("公历");
    }

    @Test
    @DisplayName("resolveBirthDateTime：datetime成功/数值回退/字符串解析/无法解析抛异常")
    void resolveBirthDateTimeBranches() {
        Map<String, Object> byDt = new LinkedHashMap<>();
        byDt.put("datetime", "1990-05-15 10:30");
        assertThat((LocalDateTime) invoke("resolveBirthDateTime", new Class<?>[]{Object.class}, byDt))
                .isEqualTo(LocalDateTime.of(1990, 5, 15, 10, 30));

        Map<String, Object> byFields = new LinkedHashMap<>();
        byFields.put("datetime", "坏值");
        byFields.put("year", "1990");
        byFields.put("month", "5");
        byFields.put("day", "15");
        byFields.put("hour", "8");
        byFields.put("minute", "9");
        assertThat((LocalDateTime) invoke("resolveBirthDateTime", new Class<?>[]{Object.class}, byFields))
                .isEqualTo(LocalDateTime.of(1990, 5, 15, 8, 9));

        Map<String, Object> bad = new LinkedHashMap<>();
        bad.put("datetime", "坏值");
        assertThatThrownBy(() -> invoke("resolveBirthDateTime", new Class<?>[]{Object.class}, bad))
                .hasRootCauseInstanceOf(IllegalArgumentException.class);

        assertThat((LocalDateTime) invoke("resolveBirthDateTime", new Class<?>[]{Object.class}, (Object) "1990-05-15 10:30:00"))
                .isEqualTo(LocalDateTime.of(1990, 5, 15, 10, 30));
        assertThat((LocalDateTime) invoke("resolveBirthDateTime", new Class<?>[]{Object.class}, (Object) "1990/05/15"))
                .isEqualTo(LocalDateTime.of(1990, 5, 15, 0, 0));
        assertThatThrownBy(() -> invoke("resolveBirthDateTime", new Class<?>[]{Object.class}, (Object) "not-a-date"))
                .hasRootCauseInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("parseChartBase：null/正常/无等号/首字符等号")
    @SuppressWarnings("unchecked")
    void parseChartBaseBranches() {
        Map<String, String> empty = (Map<String, String>) invoke("parseChartBase", new Class<?>[]{String.class}, (String) null);
        assertThat(empty).isEmpty();

        Map<String, String> m = (Map<String, String>) invoke("parseChartBase", new Class<?>[]{String.class}, "A=1;B=2");
        assertThat(m).containsEntry("A", "1").containsEntry("B", "2").hasSize(2);

        Map<String, String> partial = (Map<String, String>) invoke("parseChartBase", new Class<?>[]{String.class}, "noeq;X=9;=y");
        assertThat(partial).containsEntry("X", "9").hasSize(1);

        Map<String, String> trimmed = (Map<String, String>) invoke("parseChartBase", new Class<?>[]{String.class}, "  K = v  ");
        assertThat(trimmed).containsEntry("K", "v");
    }

    @Test
    @DisplayName("representativeHour：空/短/缺时/合法/非法字")
    void representativeHourBranches() {
        assertThat((Integer) invoke("representativeHour", new Class<?>[]{String.class}, (String) null)).isEqualTo(-1);
        assertThat((Integer) invoke("representativeHour", new Class<?>[]{String.class}, "时")).isEqualTo(-1);
        assertThat((Integer) invoke("representativeHour", new Class<?>[]{String.class}, "辰")).isEqualTo(-1);
        assertThat((Integer) invoke("representativeHour", new Class<?>[]{String.class}, "子时")).isEqualTo(0);
        assertThat((Integer) invoke("representativeHour", new Class<?>[]{String.class}, "辰时")).isEqualTo(8);
        assertThat((Integer) invoke("representativeHour", new Class<?>[]{String.class}, "亥时")).isEqualTo(22);
        assertThat((Integer) invoke("representativeHour", new Class<?>[]{String.class}, "甲时")).isEqualTo(-1);
    }

    @Test
    @DisplayName("isBranch：合法/越界/不匹配")
    void isBranchBranches() {
        assertThat((Boolean) invoke("isBranch", new Class<?>[]{int.class, String.class}, 0, "子")).isTrue();
        assertThat((Boolean) invoke("isBranch", new Class<?>[]{int.class, String.class}, -1, "子")).isFalse();
        assertThat((Boolean) invoke("isBranch", new Class<?>[]{int.class, String.class}, 12, "子")).isFalse();
        assertThat((Boolean) invoke("isBranch", new Class<?>[]{int.class, String.class}, 0, "丑")).isFalse();
    }

    @Test
    @DisplayName("ONTO 缓存分支：四化齐全/紫微缺键/禄羊陀五鼠遁缺失回退/十二神越界回退")
    @SuppressWarnings("unchecked")
    void ontoCacheBranches() {
        // sihuaStars：四化四项齐全 → 走缓存命中分支（非回退）
        Map<String, String> sh = new LinkedHashMap<>();
        sh.put("甲|化禄", "廉贞");
        sh.put("甲|化权", "破军");
        sh.put("甲|化科", "武曲");
        sh.put("甲|化忌", "太阳");
        setStatic("ONTO_SIHUA", sh);
        String[] hua = (String[]) invoke("sihuaStars", new Class<?>[]{int.class}, 0);
        assertThat(hua).containsExactly("廉贞", "破军", "武曲", "太阳");

        // 只给一项 → ok=false，回退内置 SIHUA（长度恒 4）
        Map<String, String> shPartial = new LinkedHashMap<>();
        shPartial.put("甲|化禄", "廉贞");
        setStatic("ONTO_SIHUA", shPartial);
        assertThat((String[]) invoke("sihuaStars", new Class<?>[]{int.class}, 0)).hasSize(4);

        // ziweiBranch：缓存存在但无对应键 → 回退 ZIWEI_TABLE
        setStatic("ONTO_ZIWEI", new LinkedHashMap<String, Integer>());
        assertThat((Integer) invoke("ziweiBranch", new Class<?>[]{int.class, int.class}, 2, 1)).isBetween(0, 11);
        // 命中
        Map<String, Integer> zi = new LinkedHashMap<>();
        zi.put("水二局|1", 7);
        setStatic("ONTO_ZIWEI", zi);
        assertThat((Integer) invoke("ziweiBranch", new Class<?>[]{int.class, int.class}, 2, 1)).isEqualTo(7);

        // lucunBranch：缺失键回退 + 命中
        setStatic("ONTO_LUCUN", new LinkedHashMap<String, Integer>());
        assertThat((Integer) invoke("lucunBranch", new Class<?>[]{int.class}, 0)).isBetween(0, 11);
        Map<String, Integer> lu = new LinkedHashMap<>();
        lu.put("甲", 5);
        setStatic("ONTO_LUCUN", lu);
        assertThat((Integer) invoke("lucunBranch", new Class<?>[]{int.class}, 0)).isEqualTo(5);

        // liuyangBranch：缺失回退 + 命中
        setStatic("ONTO_LIUYANG", new LinkedHashMap<String, Integer>());
        assertThat((Integer) invoke("liuyangBranch", new Class<?>[]{int.class}, 0)).isBetween(0, 11);
        Map<String, Integer> ly = new LinkedHashMap<>();
        ly.put("甲", 9);
        setStatic("ONTO_LIUYANG", ly);
        assertThat((Integer) invoke("liuyangBranch", new Class<?>[]{int.class}, 0)).isEqualTo(9);

        // liutuoBranch：缺失回退 + 命中
        setStatic("ONTO_LIUTUO", new LinkedHashMap<String, Integer>());
        assertThat((Integer) invoke("liutuoBranch", new Class<?>[]{int.class}, 0)).isBetween(0, 11);
        Map<String, Integer> lt = new LinkedHashMap<>();
        lt.put("甲", 3);
        setStatic("ONTO_LIUTUO", lt);
        assertThat((Integer) invoke("liutuoBranch", new Class<?>[]{int.class}, 0)).isEqualTo(3);

        // wuhuYinGan：缺失回退 + 命中
        setStatic("ONTO_WUHU", new LinkedHashMap<String, Integer>());
        assertThat((Integer) invoke("wuhuYinGan", new Class<?>[]{int.class}, 0)).isBetween(0, 9);
        Map<String, Integer> wu = new LinkedHashMap<>();
        wu.put("甲", 2);
        setStatic("ONTO_WUHU", wu);
        assertThat((Integer) invoke("wuhuYinGan", new Class<?>[]{int.class}, 0)).isEqualTo(2);

        // deityName：数组存在但索引越界 → 回退 DEITY_12[i]；索引命中 → 缓存值
        setStatic("ONTO_DEITY_12", new String[]{"测神"});
        assertThat((String) invoke("deityName", new Class<?>[]{int.class}, 0)).isEqualTo("测神");
        assertThat((String) invoke("deityName", new Class<?>[]{int.class}, 5)).isNotNull();
    }
}
