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
 * 第8步覆盖·纯函数缺口批次（一）：事件→事项宫期望、命格等第、空劫判定/空劫夹、
 * 同垣断语 OR 条件（含否定守卫与专称）。
 * 纯反射离线，无需任何外部环境；每例均对返回值做业务断言。
 */
@DisplayName("第8步覆盖·纯函数缺口批次（事件期望/命格等第/空劫/同垣OR）")
class ZiWeiDouShuBranchGapBatchTest {

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

    // ---------------- eventPalaceExpectation：15 类事件全分支 ----------------

    @Test
    @DisplayName("eventPalaceExpectation：结婚/离婚/生子/父母/事业/财(凶·吉)/迁移/疾厄/兄弟/田宅/学业/奴仆/null/未识别")
    void eventPalaceExpectationAllBranches() {
        Class<?>[] s = {String.class};
        assertThat((Object) invoke("eventPalaceExpectation", s, (String) null)).as("null→null").isNull();
        assertThat((int[]) invoke("eventPalaceExpectation", s, "结婚")).containsExactly(2, 1);
        assertThat((int[]) invoke("eventPalaceExpectation", s, "离婚")).containsExactly(2, -1);
        assertThat((int[]) invoke("eventPalaceExpectation", s, "生子")).containsExactly(3, 1);
        assertThat((int[]) invoke("eventPalaceExpectation", s, "丧父")).containsExactly(11, -1);
        assertThat((int[]) invoke("eventPalaceExpectation", s, "升职")).containsExactly(8, 1);
        assertThat((int[]) invoke("eventPalaceExpectation", s, "破财")).containsExactly(4, -1);
        assertThat((int[]) invoke("eventPalaceExpectation", s, "发财")).containsExactly(4, 1);
        assertThat((int[]) invoke("eventPalaceExpectation", s, "搬家")).containsExactly(6, 0);
        assertThat((int[]) invoke("eventPalaceExpectation", s, "疾病")).containsExactly(5, -1);
        assertThat((int[]) invoke("eventPalaceExpectation", s, "兄弟")).containsExactly(1, 0);
        assertThat((int[]) invoke("eventPalaceExpectation", s, "购房")).containsExactly(9, 1);
        assertThat((int[]) invoke("eventPalaceExpectation", s, "考试")).containsExactly(8, 1);
        assertThat((int[]) invoke("eventPalaceExpectation", s, "合伙")).containsExactly(7, 0);
        assertThat((Object) invoke("eventPalaceExpectation", s, "无关描述")).as("未识别→null").isNull();
    }

    // ---------------- gradeOf：命格等第全分支 ----------------

    @SuppressWarnings("unchecked")
    private static String gradeOf(List<String> hit, Map<Integer, List<String>> at, int mingIdx) {
        return (String) invoke("gradeOf", new Class<?>[]{List.class, Map.class, int.class}, hit, at, mingIdx);
    }

    @Test
    @DisplayName("gradeOf：贵(无煞/会煞无夹/会煞羊陀夹)/富/贫/空hit四方/杂局")
    void gradeOfAllBranches() {
        // 贵局 + 三方无煞 → 上格
        Map<Integer, List<String>> a1 = at();
        put(a1, 0, "紫微");
        assertThat(gradeOf(List.of("贵局"), a1, 0)).contains("上格");
        // 贵局 + 三方会煞 + 羊陀不夹 → 中格（无夹注）
        Map<Integer, List<String>> a2 = at();
        put(a2, 0, "紫微");
        put(a2, 4, "火星");
        assertThat(gradeOf(List.of("贵局"), a2, 0)).contains("中格").doesNotContain("羊陀相夹");
        // 贵局 + 三方会煞 + 羊陀夹命 → 中格（含夹注）
        Map<Integer, List<String>> a3 = at();
        put(a3, 0, "紫微");
        put(a3, 4, "火星");
        put(a3, 11, "擎羊");
        put(a3, 1, "陀罗");
        assertThat(gradeOf(List.of("贵局"), a3, 0)).contains("中格").contains("羊陀相夹");
        // 富局
        assertThat(gradeOf(List.of("火贪"), at(), 0)).contains("中上格");
        // 贫贱局
        assertThat(gradeOf(List.of("贫贱局"), at(), 0)).contains("下格");
        // 空hit + 三方皆吉无煞 → 上格
        Map<Integer, List<String>> a4 = at();
        put(a4, 4, "文昌");
        assertThat(gradeOf(List.of(), a4, 0)).contains("上格");
        // 空hit + 吉煞交参 → 中格
        Map<Integer, List<String>> a5 = at();
        put(a5, 4, "文昌");
        put(a5, 8, "擎羊");
        assertThat(gradeOf(List.of(), a5, 0)).contains("中格");
        // 空hit + 有煞无吉 → 下格
        Map<Integer, List<String>> a6 = at();
        put(a6, 4, "擎羊");
        assertThat(gradeOf(List.of(), a6, 0)).contains("下格");
        // 空hit + 无吉无煞 → 常格
        assertThat(gradeOf(List.of(), at(), 0)).contains("常格");
        // 非空hit 但无贵/富/贫关键词 → 杂局
        assertThat(gradeOf(List.of("某杂格"), at(), 0)).contains("杂局");
    }

    // ---------------- isKongJie / jiaPairKongJie ----------------

    @Test
    @DisplayName("isKongJie：地劫/天空/地空/无空曜 → 各分支")
    void isKongJieBranches() {
        Class<?>[] s = {Set.class};
        assertThat((Boolean) invoke("isKongJie", s, new HashSet<>(List.of("地劫")))).isTrue();
        assertThat((Boolean) invoke("isKongJie", s, new HashSet<>(List.of("天空")))).isTrue();
        assertThat((Boolean) invoke("isKongJie", s, new HashSet<>(List.of("地空")))).isTrue();
        assertThat((Boolean) invoke("isKongJie", s, new HashSet<>(List.of("紫微")))).isFalse();
    }

    @Test
    @DisplayName("jiaPairKongJie：空左劫右 / 劫左空右 / 不成夹")
    void jiaPairKongJieBranches() {
        Class<?>[] s = {List.class, List.class};
        assertThat((Boolean) invoke("jiaPairKongJie", s, List.of("地空"), List.of("地劫"))).isTrue();
        assertThat((Boolean) invoke("jiaPairKongJie", s, List.of("地劫"), List.of("天空"))).isTrue();
        assertThat((Boolean) invoke("jiaPairKongJie", s, List.of("地劫"), List.of("地空"))).isTrue();
        assertThat((Boolean) invoke("jiaPairKongJie", s, List.of("紫微"), List.of("地劫"))).isFalse();
    }

    // ---------------- tongYuanOrNote：否定守卫 + 专称 + 未命中/边界 ----------------

    @Test
    @DisplayName("tongYuanOrNote：加吉/加煞(含无吉·无煞否定守卫)/专称全组/未命中/空→null/边界")
    void tongYuanOrNoteBranches() {
        Class<?>[] s = {String.class, int.class, Map.class};
        Map<Integer, List<String>> a = at();
        put(a, 0, "左辅", "右弼", "文昌", "文曲", "天魁", "天钺", "禄存", "天马", "擎羊", "陀罗", "火星", "铃星");
        // 加吉（含「吉曜」且非「无吉」）
        assertThat((String) invoke("tongYuanOrNote", s, "本宫加吉曜", 0, a)).isNotNull();
        // 得吉 / 会吉 别名分支
        assertThat((String) invoke("tongYuanOrNote", s, "得吉会吉", 0, a)).isNotNull();
        // 「吉曜」+「无吉」→ 否定守卫不判加吉（借「昌曲」保持非空，覆盖 &&false 分支）
        assertThat((String) invoke("tongYuanOrNote", s, "吉曜无吉昌曲", 0, a)).isNotNull();
        // 加煞
        assertThat((String) invoke("tongYuanOrNote", s, "煞气加煞冲", 0, a)).isNotNull();
        // 逢煞 / 会煞 别名分支
        assertThat((String) invoke("tongYuanOrNote", s, "逢煞会煞", 0, a)).isNotNull();
        // 「加煞」+「无煞」→ 否定守卫不判加煞（借「羊陀」保持非空，覆盖 &&false 分支）
        assertThat((String) invoke("tongYuanOrNote", s, "加煞无煞羊陀", 0, a)).isNotNull();
        // 「会吉」「得吉」单独分支（不含「加吉」）
        assertThat((String) invoke("tongYuanOrNote", s, "会吉之星", 0, a)).isNotNull();
        assertThat((String) invoke("tongYuanOrNote", s, "得吉", 0, a)).isNotNull();
        // 纯「吉曜」且非「无吉」→ 第二操作数成立
        assertThat((String) invoke("tongYuanOrNote", s, "本有吉曜", 0, a)).isNotNull();
        // 「加杀」别名分支
        assertThat((String) invoke("tongYuanOrNote", s, "本宫加杀", 0, a)).isNotNull();
        // 「会煞 / 逢煞」经左链命中后、再因「无煞」右侧短路为假（借专称保持非空）
        assertThat((String) invoke("tongYuanOrNote", s, "会煞无煞羊陀", 0, a)).isNotNull();
        assertThat((String) invoke("tongYuanOrNote", s, "逢煞无煞火铃", 0, a)).isNotNull();
        // at==null → 守护返回 null
        assertThat((Object) invoke("tongYuanOrNote", s, "加吉", 0, (Map<Integer, List<String>>) null)).isNull();
        // 专称全组：昌曲/左右/魁钺/禄马/羊陀/火铃
        assertThat((String) invoke("tongYuanOrNote", s, "昌曲左右魁钺禄马羊陀火铃", 0, a)).isNotNull();
        // 专称命中但本盘无此星 → 未命中分支（仍返回非空）
        assertThat((String) invoke("tongYuanOrNote", s, "昌曲", 0, at())).isNotNull();
        // 无任何条件 → null
        assertThat((Object) invoke("tongYuanOrNote", s, "只是普通描述", 0, a)).isNull();
        // 边界：text null / anchorBranch<0 → null
        assertThat((Object) invoke("tongYuanOrNote", s, (String) null, 0, a)).isNull();
        assertThat((Object) invoke("tongYuanOrNote", s, "加吉", -1, a)).isNull();
    }
}
