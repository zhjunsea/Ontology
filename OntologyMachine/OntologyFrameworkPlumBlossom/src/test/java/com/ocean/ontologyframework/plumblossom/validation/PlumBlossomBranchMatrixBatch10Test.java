package com.ocean.ontologyframework.plumblossom.validation;

import com.ocean.ontologyframework.plumblossom.PlumBlossomOntologyJobWorker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 第十批·纯工具/纯计算 helper 分支矩阵（反射直达私有方法，逐方法铺「逻辑分支 × 数据分支」）。
 *
 * <p>承接第九批 JaCoCo 分析：JobWorker 剩余未覆盖方法中，凡依赖 Openllet/Ontop/RabbitMQ/LLM 的环境块
 * （{@code loadDomainRelations}/{@code loadTrigramKnowledge}/{@code verifyOntologyKnowledge}/
 * {@code selectDictum*} 等）离线不可达；本批精准打击**纯工具/纯算理**方法（解析/取整/查表/五行生克/
 * 吉凶分级/方位映射），逐档补齐全分支。
 *
 * <p>纯离线：直接 {@code new PlumBlossomOntologyJobWorker()}，实例域知识 map 为空、静态本体域字段为 null，
 * 全部走内置兜底；无需引擎、无需外部服务、不用 Mockito。
 */
public class PlumBlossomBranchMatrixBatch10Test {

    private PlumBlossomOntologyJobWorker worker;

    @BeforeEach
    void setUp() {
        worker = new PlumBlossomOntologyJobWorker();
    }

    /** 反射调用私有/受保护方法（静态/实例皆可；对静态方法 target 被忽略）。 */
    private Object call(String name, Class<?>[] types, Object... args) {
        try {
            Method m = PlumBlossomOntologyJobWorker.class.getDeclaredMethod(name, types);
            m.setAccessible(true);
            return m.invoke(worker, args);
        } catch (java.lang.reflect.InvocationTargetException e) {
            Throwable c = e.getCause();
            throw new RuntimeException(name + " 抛出异常: " + c, c);
        } catch (Exception e) {
            throw new RuntimeException("反射调用失败: " + name, e);
        }
    }

    private static final Class<?>[] S = {String.class};
    private static final Class<?>[] SS = {String.class, String.class};
    private static final Class<?>[] SI = {String.class, int.class};
    private static final Class<?>[] I = {int.class};

    // ======================================================================
    // 一、解析与取整工具
    // ======================================================================

    @Test
    @DisplayName("B10-01 parseIntOrNull：null/空/空白→null，合法→整数，非法→null")
    void parseIntOrNull() {
        assertNull(call("parseIntOrNull", S, (Object) null));
        assertNull(call("parseIntOrNull", S, ""));
        assertNull(call("parseIntOrNull", S, "   "));
        assertEquals(Integer.valueOf(42), call("parseIntOrNull", S, "42"));
        assertEquals(Integer.valueOf(42), call("parseIntOrNull", S, " 42 "));
        assertNull(call("parseIntOrNull", S, "abc"));
        assertNull(call("parseIntOrNull", S, "4x"));
    }

    @Test
    @DisplayName("B10-02 iriLocal：null→null，含 '/'或'#'→取末段，无分隔→原样")
    void iriLocal() {
        assertNull(call("iriLocal", S, (Object) null));
        assertEquals("C", call("iriLocal", S, "http://a/b#C"));
        assertEquals("x", call("iriLocal", S, "http://example.org/x"));
        assertEquals("plain", call("iriLocal", S, "plain"));
        assertEquals("", call("iriLocal", S, "http://a/b#"));
    }

    @Test
    @DisplayName("B10-03 toneName：1/2/3/4→平上去入，其它→?")
    void toneName() {
        assertEquals("平", call("toneName", I, 1));
        assertEquals("上", call("toneName", I, 2));
        assertEquals("去", call("toneName", I, 3));
        assertEquals("入", call("toneName", I, 4));
        assertEquals("?", call("toneName", I, 0));
        assertEquals("?", call("toneName", I, 5));
    }

    @Test
    @DisplayName("B10-04 putIfInt：key/value null 或空白跳过，非法整数忽略，合法 trim 后入表")
    void putIfInt() {
        Map<String, Integer> m = new HashMap<>();
        call("putIfInt", new Class<?>[]{Map.class, String.class, String.class}, m, null, "3");
        assertEquals(0, m.size());
        call("putIfInt", new Class<?>[]{Map.class, String.class, String.class}, m, "k", null);
        assertEquals(0, m.size());
        call("putIfInt", new Class<?>[]{Map.class, String.class, String.class}, m, "k", "  ");
        assertEquals(0, m.size());
        call("putIfInt", new Class<?>[]{Map.class, String.class, String.class}, m, "k", "abc");
        assertEquals(0, m.size());
        call("putIfInt", new Class<?>[]{Map.class, String.class, String.class}, m, " k ", " 7 ");
        assertEquals(Integer.valueOf(7), m.get("k"));
    }

    @Test
    @DisplayName("B10-05 afterBackendServiceReady：OBDA 未就绪时安全降级（不抛异常）")
    void afterBackendServiceReady() {
        assertDoesNotThrow(() -> call("afterBackendServiceReady", new Class<?>[]{}));
    }

    // ======================================================================
    // 二、物象→卦数（形/色/声）
    // ======================================================================

    @Test
    @DisplayName("B10-06 shapeToTrigram：圆/刚/方/柔/仰/覆/长/中刚外柔/内柔外刚/枯槁/文彩/破/缺省")
    void shapeToTrigram() {
        assertEquals(1, call("shapeToTrigram", S, "圆"));
        assertEquals(2, call("shapeToTrigram", S, "刚"));
        assertEquals(8, call("shapeToTrigram", S, "方"));
        assertEquals(5, call("shapeToTrigram", S, "柔"));
        assertEquals(4, call("shapeToTrigram", S, "仰"));
        assertEquals(7, call("shapeToTrigram", S, "覆"));
        assertEquals(5, call("shapeToTrigram", S, "长"));
        assertEquals(6, call("shapeToTrigram", S, "中刚外柔"));
        assertEquals(3, call("shapeToTrigram", S, "内柔外刚"));
        assertEquals(3, call("shapeToTrigram", S, "枯槁"));
        assertEquals(3, call("shapeToTrigram", S, "文彩"));
        assertEquals(2, call("shapeToTrigram", S, "破"));
        assertEquals(1, call("shapeToTrigram", S, "未知形"));
        assertEquals(1, call("shapeToTrigram", S, (Object) null));
    }

    @Test
    @DisplayName("B10-07 colorToTrigram：青/红紫赤/黄/白/黑/缺省")
    void colorToTrigram() {
        assertEquals(4, call("colorToTrigram", S, "青"));
        assertEquals(3, call("colorToTrigram", S, "红紫赤"));
        assertEquals(8, call("colorToTrigram", S, "黄"));
        assertEquals(2, call("colorToTrigram", S, "白"));
        assertEquals(6, call("colorToTrigram", S, "黑"));
        assertEquals(1, call("colorToTrigram", S, "橙"));
        assertEquals(1, call("colorToTrigram", S, (Object) null));
    }

    @Test
    @DisplayName("B10-08 soundToTrigram：口声→兑2，扣击→震4，金→乾1，火→离3，土→坤8，未识别→0，空→0")
    void soundToTrigram() {
        assertEquals(2, call("soundToTrigram", S, "语"));
        assertEquals(4, call("soundToTrigram", S, "扣"));
        assertEquals(1, call("soundToTrigram", S, "金"));
        assertEquals(3, call("soundToTrigram", S, "火"));
        assertEquals(8, call("soundToTrigram", S, "土"));
        assertEquals(0, call("soundToTrigram", S, "無"));
        assertEquals(0, call("soundToTrigram", S, ""));
        assertEquals(0, call("soundToTrigram", S, (Object) null));
    }

    @Test
    @DisplayName("B10-09 stillObjectDirectVerdict：圆成/缺败/圆缺参半/无征空/null空")
    void stillObjectDirectVerdict() {
        assertTrue(((String) call("stillObjectDirectVerdict", S, "见圆物")).contains("事成"));
        assertTrue(((String) call("stillObjectDirectVerdict", S, "器物破缺")).contains("事败"));
        assertTrue(((String) call("stillObjectDirectVerdict", S, "物有圆有缺")).contains("参半"));
        assertEquals("", call("stillObjectDirectVerdict", S, "平常之物"));
        assertEquals("", call("stillObjectDirectVerdict", S, ""));
        assertEquals("", call("stillObjectDirectVerdict", S, (Object) null));
    }

    // ======================================================================
    // 三、中文数词
    // ======================================================================

    @Test
    @DisplayName("B10-10 firstNumber：阿拉伯/中文/十几/几十几/两/无/null")
    void firstNumber() {
        assertEquals(0, call("firstNumber", S, (Object) null));
        assertEquals(0, call("firstNumber", S, ""));
        assertEquals(3, call("firstNumber", S, "3声"));
        assertEquals(3, call("firstNumber", S, "三声"));
        assertEquals(12, call("firstNumber", S, "十二声"));
        assertEquals(23, call("firstNumber", S, "二十三点"));
        assertEquals(2, call("firstNumber", S, "两声"));
        assertEquals(0, call("firstNumber", S, "abc"));
    }

    @Test
    @DisplayName("B10-11 cnDigit：一~九映射，非数词→-1")
    void cnDigit() {
        Class<?>[] C = {char.class};
        char[] chars = {'一', '二', '三', '四', '五', '六', '七', '八', '九'};
        for (int i = 0; i < chars.length; i++) {
            assertEquals(i + 1, call("cnDigit", C, chars[i]));
        }
        assertEquals(-1, call("cnDigit", C, '十'));
        assertEquals(-1, call("cnDigit", C, 'x'));
    }

    @Test
    @DisplayName("B10-12 parseChineseNumber：十几/几十几/两/十/无有效→0")
    void parseChineseNumber() {
        assertEquals(12, call("parseChineseNumber", SI, "十二", 0));
        assertEquals(23, call("parseChineseNumber", SI, "二十三", 0));
        assertEquals(2, call("parseChineseNumber", SI, "两", 0));
        assertEquals(10, call("parseChineseNumber", SI, "十", 0));
        assertEquals(0, call("parseChineseNumber", SI, "abc", 0));
    }

    // ======================================================================
    // 四、分类与吉凶分级
    // ======================================================================

    @Test
    @DisplayName("B10-13 tenResponseCategory：十一类 + 外/null/空")
    void tenResponseCategory() {
        assertEquals("时令", call("tenResponseCategory", S, "春"));
        assertEquals("天时", call("tenResponseCategory", S, "晴"));
        assertEquals("地理", call("tenResponseCategory", S, "山"));
        assertEquals("人事", call("tenResponseCategory", S, "老人"));
        assertEquals("动物", call("tenResponseCategory", S, "鸟"));
        assertEquals("静物", call("tenResponseCategory", S, "花"));
        assertEquals("言语", call("tenResponseCategory", S, "语"));
        assertEquals("声音", call("tenResponseCategory", S, "鸣"));
        assertEquals("五色", call("tenResponseCategory", S, "青"));
        assertEquals("写字", call("tenResponseCategory", S, "字"));
        assertEquals("方卦", call("tenResponseCategory", S, "东"));
        assertEquals("外", call("tenResponseCategory", S, "無名之象"));
        assertEquals("外", call("tenResponseCategory", S, ""));
        assertEquals("外", call("tenResponseCategory", S, (Object) null));
    }

    @Test
    @DisplayName("B10-14 shengKeName：1~5 五级名 + 缺省")
    void shengKeName() {
        assertEquals("用生体", call("shengKeName", I, 1));
        assertEquals("体用比和", call("shengKeName", I, 2));
        assertEquals("体克用", call("shengKeName", I, 3));
        assertEquals("体生用", call("shengKeName", I, 4));
        assertEquals("用克体", call("shengKeName", I, 5));
        assertEquals("体用未定", call("shengKeName", I, 0));
    }

    @Test
    @DisplayName("B10-15 shengKeLabel：1~5→大吉/吉/平/凶/大凶 + 缺省平")
    void shengKeLabel() {
        assertEquals("大吉", call("shengKeLabel", I, 1));
        assertEquals("吉", call("shengKeLabel", I, 2));
        assertEquals("平", call("shengKeLabel", I, 3));
        assertEquals("凶", call("shengKeLabel", I, 4));
        assertEquals("大凶", call("shengKeLabel", I, 5));
        assertEquals("平", call("shengKeLabel", I, 0));
    }

    @Test
    @DisplayName("B10-16 parseShengKeLevel：首字符 1~5 取值，其它/null/空→3")
    void parseShengKeLevel() {
        assertEquals(1, call("parseShengKeLevel", S, "1"));
        assertEquals(3, call("parseShengKeLevel", S, "3x"));
        assertEquals(5, call("parseShengKeLevel", S, "5"));
        assertEquals(3, call("parseShengKeLevel", S, "9"));
        assertEquals(3, call("parseShengKeLevel", S, ""));
        assertEquals(3, call("parseShengKeLevel", S, (Object) null));
    }

    @Test
    @DisplayName("B10-17 scoreToLevel：1~5→大凶/凶/平/吉/大吉，越界 clamp")
    void scoreToLevel() {
        assertEquals("大凶", call("scoreToLevel", I, 1));
        assertEquals("凶", call("scoreToLevel", I, 2));
        assertEquals("平", call("scoreToLevel", I, 3));
        assertEquals("吉", call("scoreToLevel", I, 4));
        assertEquals("大吉", call("scoreToLevel", I, 5));
        assertEquals("大凶", call("scoreToLevel", I, 0));
        assertEquals("大吉", call("scoreToLevel", I, 9));
    }

    @Test
    @DisplayName("B10-18 fivePhaseOfColor：青木/红火/白金/黑水/黄土/未识别土")
    void fivePhaseOfColor() {
        assertEquals("木", call("fivePhaseOfColor", S, "青色"));
        assertEquals("火", call("fivePhaseOfColor", S, "红色"));
        assertEquals("金", call("fivePhaseOfColor", S, "白色"));
        assertEquals("水", call("fivePhaseOfColor", S, "黑色"));
        assertEquals("土", call("fivePhaseOfColor", S, "黄色"));
        assertEquals("土", call("fivePhaseOfColor", S, "无色"));
    }

    // ======================================================================
    // 五、五行生克与旺衰
    // ======================================================================

    @Test
    @DisplayName("B10-19 generates：相生五对 true，非相生/未知 false")
    void generates() {
        assertTrue((Boolean) call("generates", SS, "金", "水"));
        assertTrue((Boolean) call("generates", SS, "水", "木"));
        assertTrue((Boolean) call("generates", SS, "木", "火"));
        assertTrue((Boolean) call("generates", SS, "火", "土"));
        assertTrue((Boolean) call("generates", SS, "土", "金"));
        assertFalse((Boolean) call("generates", SS, "金", "木"));
        assertFalse((Boolean) call("generates", SS, "未知", "水"));
    }

    @Test
    @DisplayName("B10-20 overcomes：相克五对 true，非相克/未知 false")
    void overcomes() {
        assertTrue((Boolean) call("overcomes", SS, "金", "木"));
        assertTrue((Boolean) call("overcomes", SS, "木", "土"));
        assertTrue((Boolean) call("overcomes", SS, "土", "水"));
        assertTrue((Boolean) call("overcomes", SS, "水", "火"));
        assertTrue((Boolean) call("overcomes", SS, "火", "金"));
        assertFalse((Boolean) call("overcomes", SS, "金", "水"));
        assertFalse((Boolean) call("overcomes", SS, "未知", "x"));
    }

    @Test
    @DisplayName("B10-21 seasonFive：春木/夏火/秋金/冬水/其它与null→土")
    void seasonFive() {
        assertEquals("木", call("seasonFive", S, "春"));
        assertEquals("火", call("seasonFive", S, "夏"));
        assertEquals("金", call("seasonFive", S, "秋"));
        assertEquals("水", call("seasonFive", S, "冬"));
        assertEquals("土", call("seasonFive", S, "四季"));
        assertEquals("土", call("seasonFive", S, "無"));
        assertEquals("土", call("seasonFive", S, (Object) null));
    }

    @Test
    @DisplayName("B10-22 vitalityOf：春（木当令）下木旺/火相/水休/金囚/土死")
    void vitalityOf() {
        assertEquals("旺", call("vitalityOf", SS, "木", "春"));
        assertEquals("相", call("vitalityOf", SS, "火", "春"));
        assertEquals("休", call("vitalityOf", SS, "水", "春"));
        assertEquals("囚", call("vitalityOf", SS, "金", "春"));
        assertEquals("死", call("vitalityOf", SS, "土", "春"));
    }

    @Test
    @DisplayName("B10-23 generatorOf/overcomerOf/mediatorBetween：五行反查与通关")
    void fivePhaseRelations() {
        assertEquals("木", call("generatorOf", S, "火"));
        assertEquals("火", call("generatorOf", S, "土"));
        assertEquals("水", call("generatorOf", S, "木"));
        assertEquals("金", call("overcomerOf", S, "木"));
        assertEquals("木", call("overcomerOf", S, "土"));
        assertEquals("水", call("mediatorBetween", SS, "金", "木"));
        assertEquals("火", call("mediatorBetween", SS, "木", "土"));
    }

    @Test
    @DisplayName("B10-24 fiveDirOf/ColorOf/SeasonOf/ObjectOf：五行四端查表 + 未知→null")
    void fiveRelationsLookup() {
        assertTrue(((String) call("fiveDirOf", S, "木")).startsWith("东"));
        assertTrue(((String) call("fiveColorOf", S, "火")).contains("红"));
        assertTrue(((String) call("fiveSeasonOf", S, "水")).contains("冬"));
        assertTrue(((String) call("fiveObjectOf", S, "金")).contains("金"));
        assertNull(call("fiveDirOf", S, "未知"));
    }

    @Test
    @DisplayName("B10-25 remedyLine：合法五行→含方位，null/未知→空")
    void remedyLine() {
        assertTrue(((String) call("remedyLine", S, "木")).contains("东方"));
        assertTrue(((String) call("remedyLine", S, "金")).contains("西方"));
        assertEquals("", call("remedyLine", S, (Object) null));
        assertEquals("", call("remedyLine", S, "未知"));
    }

    // ======================================================================
    // 六、方位与物象取卦
    // ======================================================================

    @Test
    @DisplayName("B10-26 xiangToNumber：null/空/空白/无匹配→1")
    void xiangToNumber() {
        assertEquals(1, call("xiangToNumber", S, (Object) null));
        assertEquals(1, call("xiangToNumber", S, ""));
        assertEquals(1, call("xiangToNumber", S, "   "));
        assertEquals(1, call("xiangToNumber", S, "XYZ"));
    }

    @Test
    @DisplayName("B10-27 dirToTrigram：东南5/西南8/东北7/西北1/东4/南3/西2/北6/其它与null→1")
    void dirToTrigram() {
        assertEquals(5, call("dirToTrigram", S, "东南"));
        assertEquals(8, call("dirToTrigram", S, "西南"));
        assertEquals(7, call("dirToTrigram", S, "东北"));
        assertEquals(1, call("dirToTrigram", S, "西北"));
        assertEquals(4, call("dirToTrigram", S, "东方"));
        assertEquals(3, call("dirToTrigram", S, "南"));
        assertEquals(2, call("dirToTrigram", S, "西"));
        assertEquals(6, call("dirToTrigram", S, "北"));
        assertEquals(1, call("dirToTrigram", S, "中央"));
        assertEquals(1, call("dirToTrigram", S, (Object) null));
    }

    @Test
    @DisplayName("B10-28 dirFromTextOrDefault：无方位/null/空→fallback，有方位→卦数")
    void dirFromTextOrDefault() {
        assertEquals(0, call("dirFromTextOrDefault", SI, (Object) null, 0));
        assertEquals(9, call("dirFromTextOrDefault", SI, "", 9));
        assertEquals(9, call("dirFromTextOrDefault", SI, "无方位之词", 9));
        assertEquals(4, call("dirFromTextOrDefault", SI, "东方", 9));
    }
}
