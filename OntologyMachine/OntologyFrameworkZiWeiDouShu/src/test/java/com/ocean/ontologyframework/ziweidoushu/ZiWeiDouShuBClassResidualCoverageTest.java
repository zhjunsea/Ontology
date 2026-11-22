package com.ocean.ontologyframework.ziweidoushu;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * B 类（罕见数据组合·纯判断/工具函数）残余分支定向补齐。
 *
 * <p>仅覆盖「可离线枚举输入的纯静态函数」，每一例对返回值做业务断言；
 * 对经源码分析确为「恒不可达」的防御分支（如 {@code detectShaCou} 的
 * {@code stars.isEmpty()} 与「同宫」scope 侧），在用例注释中说明并不强求。
 */
@DisplayName("B类补齐·纯判断函数残余分支批次（金舆扶驾/太微格/夹星/煞凑）")
class ZiWeiDouShuBClassResidualCoverageTest {

    private static Object invoke(String name, Class<?>[] types, Object... args) {
        try {
            Method m = ZiWeiDouShuOntologyJobWorker.class.getDeclaredMethod(name, types);
            m.setAccessible(true);
            return m.invoke(null, args);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private static Map<Integer, List<String>> at() {
        return new LinkedHashMap<>();
    }

    private static void put(Map<Integer, List<String>> m, int p, String... stars) {
        m.put(p, new ArrayList<>(Arrays.asList(stars)));
    }

    // ---------------- isJinYuFuJia：紫微守命 + 日月分居前后两侧（全分支） ----------------

    private static boolean isJinYuFuJia(int mingIdx, Set<String> mingMain, Set<String> mingSet,
                                        String taiyang, String taiyin) {
        return (Boolean) invoke("isJinYuFuJia",
                new Class<?>[]{int.class, Set.class, Set.class, String.class, String.class},
                mingIdx, mingMain, mingSet, taiyang, taiyin);
    }

    @Test
    @DisplayName("isJinYuFuJia：无紫微→false、mingIdx越界、太阳/太阴无效、日月各侧成立/不成")
    void isJinYuFuJiaBranches() {
        Set<String> ziwei = new HashSet<>(List.of("紫微"));
        Set<String> empty = new HashSet<>();
        assertThat(isJinYuFuJia(0, empty, empty, "酉", "卯")).isFalse();          // 无紫微
        assertThat(isJinYuFuJia(0, empty, ziwei, "酉", "卯")).isTrue();            // 紫微仅在 mingSet
        assertThat(isJinYuFuJia(-1, ziwei, empty, "酉", "卯")).isFalse();          // mingIdx<0
        assertThat(isJinYuFuJia(12, ziwei, empty, "酉", "卯")).isFalse();          // mingIdx>11
        assertThat(isJinYuFuJia(0, ziwei, empty, "X", "卯")).isFalse();            // 太阳无效
        assertThat(isJinYuFuJia(0, ziwei, empty, "酉", "X")).isFalse();            // 太阴无效
        assertThat(isJinYuFuJia(0, ziwei, empty, "酉", "卯")).isTrue();            // 太阳后/太阴前
        assertThat(isJinYuFuJia(0, ziwei, empty, "寅", "酉")).isTrue();            // 太阳前/太阴后
        assertThat(isJinYuFuJia(0, ziwei, empty, "午", "午")).isFalse();          // 两侧皆不成立
    }

    // ---------------- isJiaBothSides：二星一居 ming−1、一居 ming+1（全分支） ----------------

    private static boolean isJiaBothSides(int mingIdx, Map<Integer, List<String>> at,
                                          String a, String b) {
        return (Boolean) invoke("isJiaBothSides",
                new Class<?>[]{int.class, Map.class, String.class, String.class}, mingIdx, at, a, b);
    }

    @Test
    @DisplayName("isJiaBothSides：mingIdx越界、星缺失、A左B右、A右B左、同侧不成")
    void isJiaBothSidesBranches() {
        assertThat(isJiaBothSides(-1, at(), "太阳", "太阴")).isFalse();
        assertThat(isJiaBothSides(12, at(), "太阳", "太阴")).isFalse();
        Map<Integer, List<String>> missA = at();
        put(missA, 1, "太阴");
        assertThat(isJiaBothSides(0, missA, "太阳", "太阴")).isFalse();
        Map<Integer, List<String>> missB = at();
        put(missB, 11, "太阳");
        assertThat(isJiaBothSides(0, missB, "太阳", "太阴")).isFalse();
        Map<Integer, List<String>> aLeft = at();
        put(aLeft, 11, "太阳");
        put(aLeft, 1, "太阴");
        assertThat(isJiaBothSides(0, aLeft, "太阳", "太阴")).isTrue();
        Map<Integer, List<String>> aRight = at();
        put(aRight, 1, "太阳");
        put(aRight, 11, "太阴");
        assertThat(isJiaBothSides(0, aRight, "太阳", "太阴")).isTrue();
        Map<Integer, List<String>> sameSide = at();
        put(sameSide, 1, "太阳", "太阴");
        assertThat(isJiaBothSides(0, sameSide, "太阳", "太阴")).isFalse();
    }

    // ---------------- judgeTaiweiMingGe：太微赋具名格全分支 ----------------

    @SuppressWarnings("unchecked")
    private static List<String> judgeTaiweiMingGe(int mingIdx, Set<String> mingMain, Set<String> mingSet) {
        return (List<String>) invoke("judgeTaiweiMingGe",
                new Class<?>[]{int.class, Set.class, Set.class}, mingIdx, mingMain, mingSet);
    }

    @Test
    @DisplayName("judgeTaiweiMingGe：日丽中天/水澄桂萼/众水朝东(寅卯)/金舆捧栉(辰未)/无格")
    void judgeTaiweiMingGeBranches() {
        Set<String> none = new HashSet<>();
        assertThat(judgeTaiweiMingGe(6, new HashSet<>(List.of("太阳")), none))
                .anyMatch(s -> s.contains("日丽中天"));
        assertThat(judgeTaiweiMingGe(0, new HashSet<>(List.of("太阴")), none))
                .anyMatch(s -> s.contains("水澄桂萼"));
        assertThat(judgeTaiweiMingGe(2, new HashSet<>(List.of("破军")), new HashSet<>(List.of("文昌"))))
                .anyMatch(s -> s.contains("众水朝东"));
        assertThat(judgeTaiweiMingGe(3, new HashSet<>(List.of("破军")), new HashSet<>(List.of("文曲"))))
                .anyMatch(s -> s.contains("众水朝东"));
        assertThat(judgeTaiweiMingGe(4, new HashSet<>(List.of("紫微")), none))
                .anyMatch(s -> s.contains("金舆捧栉"));
        assertThat(judgeTaiweiMingGe(7, new HashSet<>(List.of("紫微")), none))
                .anyMatch(s -> s.contains("金舆捧栉"));
        assertThat(judgeTaiweiMingGe(0, new HashSet<>(List.of("紫微")), none)).isEmpty();
    }

    // ---------------- detectShaCou：煞曜短语抽取全分支 ----------------

    private static Object detectShaCou(String text) {
        return invoke("detectShaCou", new Class<?>[]{String.class}, text);
    }

    @Test
    @DisplayName("detectShaCou：null→null、无匹配→null、羊陀火铃/巨宿羊陀/羊火/火铃各族命中")
    void detectShaCouBranches() {
        assertThat(detectShaCou(null)).isNull();                 // text==null
        assertThat(detectShaCou("太平无事")).isNull();             // 循环全 continue
        assertThat(detectShaCou("羊陀火铃忌冲合")).isNotNull();     // 羊/陀/火/铃 全含
        assertThat(detectShaCou("巨宿羊陀")).isNotNull();           // 巨分支
        assertThat(detectShaCou("羊火")).isNotNull();               // 羊+火
        assertThat(detectShaCou("火铃")).isNotNull();               // 火+铃
    }
}
