package com.ocean.ontologyframework.ziweidoushu;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 紫微斗数 JobWorker 纯逻辑工具方法·边界/守护分支单元测试（第 8 步·代码覆盖率补强）。
 *
 * <p>端到端流程只覆盖「正常盘」路径，本测试针对 {@link ZiWeiDouShuOntologyJobWorker} 中
 * 确定性纯函数（地支/天干抽取、格局吉凶判定、煞凑识别与判定、计数、解析兜底等）补齐
 * null/空/异常/边界输入分支，提升分支覆盖率。私有方法经自建反射助手调用。
 */
@DisplayName("紫微斗数 JobWorker 纯逻辑工具：边界与守护分支")
class ZiWeiDouShuJobWorkerLogicTest {

    /** 反射调用 JobWorker 私有静态方法。 */
    private static Object call(String name, Class<?>[] sig, Object... args) {
        try {
            Method m = ZiWeiDouShuOntologyJobWorker.class.getDeclaredMethod(name, sig);
            m.setAccessible(true);
            return m.invoke(null, args);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("反射调用失败: " + name, e);
        }
    }

    // ============ zhiChars：地支抽取（排除干支之支 + 四墓扩展） ============
    @Test
    @DisplayName("zhiChars：干支之支（甲子之子）被排除")
    void zhiChars_excludesGanZhiPair() {
        assertThat(ZiWeiDouShuOntologyJobWorker.zhiChars("甲子")).isEmpty();
    }

    @Test
    @DisplayName("zhiChars：普通地支全部抽取")
    void zhiChars_plain() {
        assertThat(ZiWeiDouShuOntologyJobWorker.zhiChars("子午卯酉"))
                .containsExactlyInAnyOrder('子', '午', '卯', '酉');
    }

    @Test
    @DisplayName("zhiChars：四墓展开为辰戌丑未")
    void zhiChars_fourTomb() {
        assertThat(ZiWeiDouShuOntologyJobWorker.zhiChars("四墓"))
                .containsExactlyInAnyOrder('辰', '戌', '丑', '未');
    }

    // ============ ganChars：天干抽取 ============
    @Test
    @DisplayName("ganChars：抽取丁庚甲，忽略讹字入")
    void ganChars_basic() {
        assertThat(ZiWeiDouShuOntologyJobWorker.ganChars("丁庚甲入生人"))
                .containsExactlyInAnyOrder('丁', '庚', '甲');
    }

    // ============ classifyGe：吉凶判定各分支 ============
    @Test
    @DisplayName("classifyGe：不贵/不为 → 凶")
    void classifyGe_badKeyword() {
        assertThat(ZiWeiDouShuOntologyJobWorker.classifyGe("此命不贵")).isEqualTo("凶");
    }

    @Test
    @DisplayName("classifyGe：吉象词 → 吉")
    void classifyGe_good() {
        assertThat(ZiWeiDouShuOntologyJobWorker.classifyGe("财官格，主富贵")).isEqualTo("吉");
    }

    @Test
    @DisplayName("classifyGe：凶象词 → 凶")
    void classifyGe_bad() {
        assertThat(ZiWeiDouShuOntologyJobWorker.classifyGe("一生困顿孤寡")).isEqualTo("凶");
    }

    @Test
    @DisplayName("classifyGe：吉凶并存 → 吉凶参")
    void classifyGe_mixed() {
        assertThat(ZiWeiDouShuOntologyJobWorker.classifyGe("财官格却又困顿")).isEqualTo("吉凶参");
    }

    @Test
    @DisplayName("classifyGe：无明显词 → 平")
    void classifyGe_plain() {
        assertThat(ZiWeiDouShuOntologyJobWorker.classifyGe("平常之命")).isEqualTo("平");
    }

    // ============ detectShaCou：煞凑识别各分支 ============
    @Test
    @DisplayName("detectShaCou：null → null")
    void detectShaCou_null() {
        assertThat(ZiWeiDouShuOntologyJobWorker.detectShaCou(null)).isNull();
    }

    @Test
    @DisplayName("detectShaCou：无煞词 → null")
    void detectShaCou_none() {
        assertThat(ZiWeiDouShuOntologyJobWorker.detectShaCou("此宫安星平稳")).isNull();
    }

    @Test
    @DisplayName("detectShaCou：羊陀火铃冲会 → 四煞齐")
    void detectShaCou_hits() {
        ZiWeiDouShuOntologyJobWorker.ShaCou sc =
                ZiWeiDouShuOntologyJobWorker.detectShaCou("辰宫羊陀火铃冲会");
        assertThat(sc).isNotNull();
        assertThat(sc.stars).contains("擎羊", "陀罗", "火星", "铃星");
    }

    @Test
    @DisplayName("detectShaCou：巨宿羊陀 → 巨门+羊陀")
    void detectShaCou_juSu() {
        ZiWeiDouShuOntologyJobWorker.ShaCou sc =
                ZiWeiDouShuOntologyJobWorker.detectShaCou("巨宿羊陀更照冲");
        assertThat(sc).isNotNull();
        assertThat(sc.stars).contains("擎羊", "陀罗", "巨门");
    }

    @Test
    @DisplayName("detectShaCou：作用域默认三方四正")
    void detectShaCou_defaultScope() {
        ZiWeiDouShuOntologyJobWorker.ShaCou sc =
                ZiWeiDouShuOntologyJobWorker.detectShaCou("羊陀同宫");
        assertThat(sc).isNotNull();
        assertThat(sc.scope).isEqualTo("三方四正");
    }

    // ============ shaCouSatisfied：煞凑成立判定 ============
    @Test
    @DisplayName("shaCouSatisfied：sc 为 null → false")
    void shaCouSatisfied_nullSc() {
        assertThat(ZiWeiDouShuOntologyJobWorker.shaCouSatisfied(null, 0, Map.of())).isFalse();
    }

    @Test
    @DisplayName("shaCouSatisfied：at 为 null → false")
    void shaCouSatisfied_nullAt() {
        assertThat(ZiWeiDouShuOntologyJobWorker.shaCouSatisfied(
                ZiWeiDouShuOntologyJobWorker.detectShaCou("羊陀"), 0, null)).isFalse();
    }

    @Test
    @DisplayName("shaCouSatisfied：所需煞曜齐见于宫 → true")
    void shaCouSatisfied_true() {
        ZiWeiDouShuOntologyJobWorker.ShaCou sc = ZiWeiDouShuOntologyJobWorker.detectShaCou("羊陀");
        Map<Integer, List<String>> at = new LinkedHashMap<>();
        at.put(0, List.of("擎羊", "陀罗"));
        assertThat(ZiWeiDouShuOntologyJobWorker.shaCouSatisfied(sc, 0, at)).isTrue();
    }

    @Test
    @DisplayName("shaCouSatisfied：煞曜不全 → false")
    void shaCouSatisfied_false() {
        ZiWeiDouShuOntologyJobWorker.ShaCou sc = ZiWeiDouShuOntologyJobWorker.detectShaCou("羊陀");
        Map<Integer, List<String>> at = new LinkedHashMap<>();
        at.put(0, List.of("擎羊"));
        assertThat(ZiWeiDouShuOntologyJobWorker.shaCouSatisfied(sc, 0, at)).isFalse();
    }

    // ============ countStar：星曜计数（反射） ============
    @Test
    @DisplayName("countStar：跨宫统计出现次数")
    void countStar_counts() {
        Map<Integer, List<String>> at = new LinkedHashMap<>();
        at.put(0, List.of("紫微", "天府"));
        at.put(1, List.of("紫微"));
        Object c = call("countStar", new Class<?>[]{Map.class, String.class}, at, "紫微");
        assertThat((Integer) c).isEqualTo(2);
        Object zero = call("countStar", new Class<?>[]{Map.class, String.class}, at, "天同");
        assertThat((Integer) zero).isZero();
    }

    // ============ toInt：解析兜底（反射） ============
    @Test
    @DisplayName("toInt：null/非法/正常三分支")
    void toInt_branches() {
        assertThat((Integer) call("toInt", new Class<?>[]{String.class, int.class}, null, 7)).isEqualTo(7);
        assertThat((Integer) call("toInt", new Class<?>[]{String.class, int.class}, "abc", -1)).isEqualTo(-1);
        assertThat((Integer) call("toInt", new Class<?>[]{String.class, int.class}, " 42 ", 0)).isEqualTo(42);
    }

    // ============ combineWord：吉凶组合（反射） ============
    @Test
    @DisplayName("combineWord：吉吉/凶凶/吉凶相伴")
    void combineWord_branches() {
        assertThat((String) call("combineWord", new Class<?>[]{int.class, int.class}, 1, 1)).isEqualTo("吉");
        assertThat((String) call("combineWord", new Class<?>[]{int.class, int.class}, -1, -1)).isEqualTo("凶");
        assertThat((String) call("combineWord", new Class<?>[]{int.class, int.class}, 1, -1)).isEqualTo("吉凶相伴");
        assertThat((String) call("combineWord", new Class<?>[]{int.class, int.class}, 0, 0)).isEqualTo("吉凶相伴");
    }

    // ============ tryParse：多格式时间解析（反射） ============
    @Test
    @DisplayName("tryParse：null/空白 → null")
    void tryParse_nullBlank() {
        assertThat((LocalDateTime) call("tryParse", new Class<?>[]{String.class}, new Object[]{null})).isNull();
        assertThat((LocalDateTime) call("tryParse", new Class<?>[]{String.class}, "   ")).isNull();
    }

    @Test
    @DisplayName("tryParse：带时分/仅日期/斜杠/紧凑/垃圾")
    void tryParse_formats() {
        assertThat((LocalDateTime) call("tryParse", new Class<?>[]{String.class}, "1990-05-15 14:30"))
                .isEqualTo(LocalDateTime.of(1990, 5, 15, 14, 30));
        assertThat((LocalDateTime) call("tryParse", new Class<?>[]{String.class}, "1990-05-15"))
                .isEqualTo(LocalDateTime.of(1990, 5, 15, 0, 0));
        assertThat((LocalDateTime) call("tryParse", new Class<?>[]{String.class}, "1990/05/15 14:30"))
                .isEqualTo(LocalDateTime.of(1990, 5, 15, 14, 30));
        assertThat((LocalDateTime) call("tryParse", new Class<?>[]{String.class}, "199005151430"))
                .isEqualTo(LocalDateTime.of(1990, 5, 15, 14, 30));
        assertThat((LocalDateTime) call("tryParse", new Class<?>[]{String.class}, "垃圾时间")).isNull();
    }

    // ============ extractLongitude：经度解析（反射） ============
    @Test
    @DisplayName("extractLongitude：null/无数字/东经/西经W/西经中文")
    void extractLongitude_branches() {
        assertThat((Double) call("extractLongitude", new Class<?>[]{String.class}, new Object[]{null})).isNull();
        assertThat((Double) call("extractLongitude", new Class<?>[]{String.class}, "北京")).isNull();
        assertThat((Double) call("extractLongitude", new Class<?>[]{String.class}, "北京 116.4E")).isEqualTo(116.4);
        assertThat((Double) call("extractLongitude", new Class<?>[]{String.class}, "纽约 74W")).isEqualTo(-74.0);
        assertThat((Double) call("extractLongitude", new Class<?>[]{String.class}, "西经 74")).isEqualTo(-74.0);
    }

    // ============ parseChartBase：盘面基础串解析（反射） ============
    @Test
    @DisplayName("parseChartBase：null → 空 map；正常键值对解析")
    @SuppressWarnings("unchecked")
    void parseChartBase_branches() {
        Map<String, String> empty = (Map<String, String>) call("parseChartBase", new Class<?>[]{String.class}, new Object[]{null});
        assertThat(empty).isEmpty();
        Map<String, String> m = (Map<String, String>) call("parseChartBase", new Class<?>[]{String.class}, "命宫=亥;身宫=丑");
        assertThat(m).containsEntry("命宫", "亥").containsEntry("身宫", "丑");
    }

    // ============ branchIndexByZhi：地支→索引（验盘校正「校正后时辰」回填用，反射） ============
    @Test
    @DisplayName("branchIndexByZhi：地支映射 0~11，非地支返回 -1")
    void branchIndexByZhi_all() {
        assertThat((Integer) call("branchIndexByZhi", new Class<?>[]{char.class}, '子')).isZero();
        assertThat((Integer) call("branchIndexByZhi", new Class<?>[]{char.class}, '辰')).isEqualTo(4);
        assertThat((Integer) call("branchIndexByZhi", new Class<?>[]{char.class}, '午')).isEqualTo(6);
        assertThat((Integer) call("branchIndexByZhi", new Class<?>[]{char.class}, '亥')).isEqualTo(11);
        assertThat((Integer) call("branchIndexByZhi", new Class<?>[]{char.class}, 'X')).isEqualTo(-1);
    }

    // ============ buildResponseMap：节点17 响应含大模型命书详解/白话解读双键（P1 数据接线） ============
    @Test
    @DisplayName("buildResponseMap：response 含 status/chart/reading/messageStatus + aiInterpretation/aiInterpretationPlain")
    void buildResponseMap_containsAiKeys() {
        Map<String, Object> r = ZiWeiDouShuOntologyJobWorker.buildResponseMap(
                "命宫=丑", "命书正文", "SENT", "详解文本", "白话文本");
        assertThat(r).containsEntry("status", "COMPLETED")
                .containsEntry("chart", "命宫=丑")
                .containsEntry("reading", "命书正文")
                .containsEntry("messageStatus", "SENT")
                .containsEntry("aiInterpretation", "详解文本")
                .containsEntry("aiInterpretationPlain", "白话文本");
        // AI 降级为空串时仍应保留键（前端可据此渲染占位）
        Map<String, Object> r2 = ZiWeiDouShuOntologyJobWorker.buildResponseMap("s", "rd", "SKIPPED_NO_ALERT", "", "");
        assertThat(r2).containsEntry("aiInterpretation", "").containsEntry("aiInterpretationPlain", "");
    }

    // ============ representativeHour：校正后时辰→代表小时（验盘校正回填，包级静态直调） ============
    @Test
    @DisplayName("representativeHour：子时0/辰时8/午时12/亥时22，非法或空返回 -1")
    void representativeHour_all() {
        assertThat(ZiWeiDouShuOntologyJobWorker.representativeHour("子时")).isZero();
        assertThat(ZiWeiDouShuOntologyJobWorker.representativeHour("辰时")).isEqualTo(8);
        assertThat(ZiWeiDouShuOntologyJobWorker.representativeHour("午时")).isEqualTo(12);
        assertThat(ZiWeiDouShuOntologyJobWorker.representativeHour("亥时")).isEqualTo(22);
        assertThat(ZiWeiDouShuOntologyJobWorker.representativeHour("子")).isEqualTo(-1);   // 缺「时」
        assertThat(ZiWeiDouShuOntologyJobWorker.representativeHour("X时")).isEqualTo(-1);  // 非地支
        assertThat(ZiWeiDouShuOntologyJobWorker.representativeHour(null)).isEqualTo(-1);
    }
}
