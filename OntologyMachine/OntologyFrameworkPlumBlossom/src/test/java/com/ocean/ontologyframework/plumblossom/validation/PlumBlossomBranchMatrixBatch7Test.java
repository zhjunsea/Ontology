package com.ocean.ontologyframework.plumblossom.validation;

import com.ocean.ontologyframework.plumblossom.PlumBlossomOntologyJobWorker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 第七批·纯算理/文本 helper 分支矩阵（反射直达私有方法，逐方法铺「逻辑分支 × 数据分支」）。
 *
 * <p>前六批多经 handler 间接触达；本批改为反射直调 {@code PlumBlossomOntologyJobWorker} 的
 * 私有 helper，精准打击 JaCoCo 报告中未覆盖的大块算理/文本方法（categoryVerdict / objectDivinationText /
 * imageSymbolText / tenCategoryNote / dirToTrigram / 各类 detail / scoreToLevel 等）。
 * 纯离线：直接 {@code new PlumBlossomOntologyJobWorker()}，本体/LLM/OBDA 全为 null 走内置兜底，
 * 无需引擎、无需外部服务、不用 Mockito。
 */
public class PlumBlossomBranchMatrixBatch7Test {

    private PlumBlossomOntologyJobWorker worker;

    @BeforeEach
    void setUp() {
        worker = new PlumBlossomOntologyJobWorker();
    }

    /** 反射调用私有方法（静态/实例皆可；obj 对静态方法被忽略）。 */
    private Object call(String name, Class<?>[] types, Object... args) {
        try {
            Method m = PlumBlossomOntologyJobWorker.class.getDeclaredMethod(name, types);
            m.setAccessible(true);
            return m.invoke(worker, args);
        } catch (InvocationTargetException e) {
            Throwable c = e.getCause();
            throw new RuntimeException(name + " 抛出异常: " + c, c);
        } catch (Exception e) {
            throw new RuntimeException("反射调用失败: " + name, e);
        }
    }

    private String str(String name, Class<?>[] types, Object... args) {
        return String.valueOf(call(name, types, args));
    }

    private static final Class<?>[] S4 = {String.class, String.class, String.class, String.class};

    // ======================================================================
    // 一、十应分类之应 tenCategoryNote（天时/地理/人事/时令/方卦/动物/静物/言语/声音）
    // ======================================================================

    @Test
    @DisplayName("B7-01 tenCategoryNote·天时：晴霁/雨雪/雷风/default 四支")
    void tenCategoryNote_tianshi() {
        Class<?>[] t = S4;
        assertTrue(str("tenCategoryNote", t, "天时", "今日晴霁", "乾", "春").contains("天时之应"));
        assertTrue(str("tenCategoryNote", t, "天时", "有雨", "乾", "春").contains("雨雪为坎"));
        assertTrue(str("tenCategoryNote", t, "天时", "打雷", "乾", "春").contains("雷风"));
        assertTrue(str("tenCategoryNote", t, "天时", "无云明朗", "乾", "春").contains("天时之应"));
    }

    @Test
    @DisplayName("B7-02 tenCategoryNote·地理：茂树/江湖/窑灶/default 四支")
    void tenCategoryNote_dili() {
        Class<?>[] t = S4;
        assertTrue(str("tenCategoryNote", t, "地理", "茂树", "乾", "春").contains("茂树秀竹"));
        assertTrue(str("tenCategoryNote", t, "地理", "江湖", "乾", "春").contains("江湖河池"));
        assertTrue(str("tenCategoryNote", t, "地理", "窑灶", "乾", "春").contains("窑灶之地"));
        assertTrue(str("tenCategoryNote", t, "地理", "岩穴", "乾", "春").contains("地理之应"));
    }

    @Test
    @DisplayName("B7-03 tenCategoryNote·人事：老人/老妇/少男/少女/无 五支")
    void tenCategoryNote_renshi() {
        Class<?>[] t = S4;
        assertTrue(str("tenCategoryNote", t, "人事", "见老人", "乾", "春").contains("属乾"));
        assertTrue(str("tenCategoryNote", t, "人事", "见老妇", "乾", "春").contains("属坤"));
        assertTrue(str("tenCategoryNote", t, "人事", "见童", "乾", "春").contains("属艮"));
        assertTrue(str("tenCategoryNote", t, "人事", "见女子", "乾", "春").contains("属兑"));
        assertTrue(str("tenCategoryNote", t, "人事", "无事", "乾", "春").contains("人事之应"));
    }

    @Test
    @DisplayName("B7-04 tenCategoryNote·时令/方卦/静物/言语/声音/未知 六支")
    void tenCategoryNote_misc() {
        Class<?>[] t = S4;
        assertTrue(str("tenCategoryNote", t, "时令", "", "乾", "春").contains("时令之应"));
        assertTrue(str("tenCategoryNote", t, "方卦", "东南", "乾", "春").contains("方卦之应"));
        assertTrue(str("tenCategoryNote", t, "静物", "", "乾", "春").contains("静物之应"));
        assertTrue(str("tenCategoryNote", t, "言语", "", "乾", "春").contains("言语之应"));
        assertTrue(str("tenCategoryNote", t, "声音", "", "乾", "春").contains("声音之应"));
        assertEquals("", str("tenCategoryNote", t, "未知类", "", "乾", "春"));
    }

    @Test
    @DisplayName("B7-05 tenCategoryNote·动物：鹊/鸦/雁/鸡/马/默认 六支")
    void tenCategoryNote_dongwu() {
        Class<?>[] t = S4;
        assertTrue(str("tenCategoryNote", t, "动物", "见鹊", "乾", "春").contains("传喜"));
        assertTrue(str("tenCategoryNote", t, "动物", "见鸦", "乾", "春").contains("报灾"));
        assertTrue(str("tenCategoryNote", t, "动物", "见雁", "乾", "春").contains("远信"));
        assertTrue(str("tenCategoryNote", t, "动物", "闻鸡", "乾", "春").contains("家音"));
        assertTrue(str("tenCategoryNote", t, "动物", "见马", "乾", "春").contains("动意"));
        assertTrue(str("tenCategoryNote", t, "动物", "见牛", "乾", "春").contains("动物之应"));
    }

    // ======================================================================
    // 二、分类占断 categoryVerdict（十七类 × 吉/凶断）
    // ======================================================================

    @Test
    @DisplayName("B7-06 categoryVerdict：十七类目 × 吉断(级1)/凶断(级5) 双走，含各分项细则")
    void categoryVerdict_allCategories() {
        String[] cats = {"天时", "人事", "家宅", "屋舍", "婚姻", "生产", "饮食", "求谋",
                "求名", "求财", "交易", "出行", "行人", "谒见", "失物", "疾病", "官讼", "坟墓"};
        for (String cat : cats) {
            String good = str("categoryVerdict",
                    new Class<?>[]{String.class, String.class, String.class, String.class,
                            String.class, String.class, String.class, int.class},
                    cat, "乾为天", "1·大吉", "乾", "坤", "乾为天", "天风姤", 1);
            assertTrue(good.contains("依「" + cat + "」分项断法"), cat + " good");
            String bad = str("categoryVerdict",
                    new Class<?>[]{String.class, String.class, String.class, String.class,
                            String.class, String.class, String.class, int.class},
                    cat, "坤为地", "5·大凶", "乾", "离", "坤为地", "地天泰", 6);
            assertTrue(bad.contains("依「" + cat + "」分项断法"), cat + " bad");
        }
    }

    @Test
    @DisplayName("B7-07 categoryVerdict：生产辨男女 / 婚姻论对方 / 疾病测鬼神 特化分支")
    void categoryVerdict_specialBranches() {
        Class<?>[] t = {String.class, String.class, String.class, String.class,
                String.class, String.class, String.class, int.class};
        assertTrue(str("categoryVerdict", t, "生产", "乾为天", "1·大吉", "乾", "坤", "乾为天", "天风姤", 1).contains("辨男女"));
        assertTrue(str("categoryVerdict", t, "婚姻", "乾为天", "1·大吉", "乾", "乾", "乾为天", "天风姤", 1).contains("论婚·对方"));
        assertTrue(str("categoryVerdict", t, "疾病", "乾为天", "5·大凶", "乾", "离", "乾为天", "天风姤", 1).contains("疾病"));
        assertTrue(str("categoryVerdict", t, "饮食", "乾为天", "1·大吉", "乾", "坤", "乾为天", "天风姤", 1).contains("饮食"));
    }

    // ======================================================================
    // 三、占物类断法 objectDivinationText
    // ======================================================================

    private static final Class<?>[] OD = {String.class, String.class, int.class, String.class,
            String.class, String.class, String.class, String.class, String.class};

    @Test
    @DisplayName("B7-08 objectDivinationText：非占物法→空串")
    void objectDivinationText_nonObject() {
        assertEquals("", str("objectDivinationText", OD,
                "年月日时起例", "乾为天", 1, "乾", "坤", "乾为天", "天风姤", "春", "见圆物"));
    }

    @Test
    @DisplayName("B7-09 objectDivinationText：体用五关系 生体/克体/体生/体克/比和 五支")
    void objectDivinationText_relations() {
        // 乾(金) 体，用取 坤土(生体)/离火(克体)/坎水(体生)/巽木(体克)/乾金(比和)
        for (String use : new String[]{"坤", "离", "坎", "巽", "乾"}) {
            String r = str("objectDivinationText", OD,
                    "占动物", "乾为天", 1, "乾", use, "乾为天", "天风姤", "春", "见圆物");
            assertTrue(r.contains("占物类断法"), "use=" + use);
        }
    }

    @Test
    @DisplayName("B7-10 objectDivinationText：器物占 兑/坎乾/震巽/default 四支")
    void objectDivinationText_qiwuBranches() {
        // 无兑 → 器物占:兑
        assertTrue(str("objectDivinationText", OD, "占静物", "乾为天", 1, "坎", "震", "乾为天", "天风姤", "春", "").contains("器中无毁折"));
        // 兑为用、体坎 → 坎乾支
        assertTrue(str("objectDivinationText", OD, "占静物", "乾为天", 1, "坎", "兑", "乾为天", "天风姤", "春", "").contains("见兑无伤"));
        // 兑为用、体震 → 震巽支
        assertTrue(str("objectDivinationText", OD, "占静物", "乾为天", 1, "震", "兑", "乾为天", "天风姤", "春", "").contains("木器"));
        // 兑为体、体非坎乾震巽 → default 支
        assertTrue(str("objectDivinationText", OD, "占静物", "乾为天", 1, "兑", "坤", "乾为天", "天风姤", "春", "").contains("器不久即破"));
    }

    @Test
    @DisplayName("B7-11 objectDivinationText：四季趣时 + 动爻五六 + 观物克应")
    void objectDivinationText_seasonsAndOmen() {
        for (String season : new String[]{"春", "夏", "秋", "冬"}) {
            assertTrue(str("objectDivinationText", OD,
                    "物卦起例", "乾为天", 6, "乾", "坤", "乾为天", "天风姤", season, "见圆物")
                    .contains("观物趣时"), season);
        }
        String r = str("objectDivinationText", OD,
                "形物占", "乾为天", 6, "乾", "坤", "乾为天", "天风姤", "春", "见圆物");
        assertTrue(r.contains("五、六爻"), "动爻五六支");
    }

    // ======================================================================
    // 四、万物类象 imageSymbolText + categorySymbolHint
    // ======================================================================

    @Test
    @DisplayName("B7-12 imageSymbolText：各类目体卦应取之象（含 婚姻/疾病/求财/家宅/官讼/出行/失物/谒见/default）")
    void imageSymbolText_categoryHints() {
        String[] cats = {"疾病", "求财", "交易", "家宅", "屋舍", "坟墓", "婚姻",
                "官讼", "出行", "行人", "失物", "谒见", "人事"};
        Class<?>[] t = {String.class, String.class, String.class, String.class, String.class, String.class};
        for (String cat : cats) {
            String r = str("imageSymbolText", t, cat, "乾为天", "乾为天", "天风姤", "乾", "兑");
            assertTrue(r.contains("核心类象"), cat);
        }
    }

    @Test
    @DisplayName("B7-13 imageSymbolText：体用缺省由本卦推 + 无互无变")
    void imageSymbolText_blankBodyUse() {
        Class<?>[] t = {String.class, String.class, String.class, String.class, String.class, String.class};
        String r = str("imageSymbolText", t, "", "乾为天", "", "", "", "");
        assertTrue(r.contains("核心类象"));
        assertFalse(r.contains("动象"));
    }

    // ======================================================================
    // 五、方位/形色/声音/取象卦数
    // ======================================================================

    @Test
    @DisplayName("B7-14 dirToTrigram：八方 + 未知 九支")
    void dirToTrigram_variants() {
        Class<?>[] t = {String.class};
        assertEquals(5, ((Integer) call("dirToTrigram", t, "东南")).intValue());
        assertEquals(8, ((Integer) call("dirToTrigram", t, "西南")).intValue());
        assertEquals(7, ((Integer) call("dirToTrigram", t, "东北")).intValue());
        assertEquals(1, ((Integer) call("dirToTrigram", t, "西北")).intValue());
        assertEquals(4, ((Integer) call("dirToTrigram", t, "东")).intValue());
        assertEquals(3, ((Integer) call("dirToTrigram", t, "南")).intValue());
        assertEquals(2, ((Integer) call("dirToTrigram", t, "西")).intValue());
        assertEquals(6, ((Integer) call("dirToTrigram", t, "北")).intValue());
        assertEquals(1, ((Integer) call("dirToTrigram", t, "中")).intValue());
    }

    @Test
    @DisplayName("B7-15 dirFromTextOrDefault：空→fallback，有方位→方位卦")
    void dirFromTextOrDefault_variants() {
        Class<?>[] t = {String.class, int.class};
        assertEquals(9, ((Integer) call("dirFromTextOrDefault", t, "", 9)).intValue());
        assertEquals(4, ((Integer) call("dirFromTextOrDefault", t, "东来", 9)).intValue());
        assertEquals(9, ((Integer) call("dirFromTextOrDefault", t, "无人处", 9)).intValue());
    }

    @Test
    @DisplayName("B7-16 形/色/声/象 取卦数：shapeToTrigram/colorToTrigram/soundToTrigram/xiangToNumber")
    void shapeColorSoundXiang_variants() {
        Class<?>[] s1 = {String.class};
        assertEquals(1, ((Integer) call("shapeToTrigram", s1, "圆")).intValue());
        assertEquals(8, ((Integer) call("shapeToTrigram", s1, "方")).intValue());
        assertEquals(3, ((Integer) call("shapeToTrigram", s1, "文彩")).intValue());
        assertEquals(1, ((Integer) call("shapeToTrigram", s1, "未知")).intValue());

        assertEquals(4, ((Integer) call("colorToTrigram", s1, "青")).intValue());
        assertEquals(3, ((Integer) call("colorToTrigram", s1, "红紫赤")).intValue());
        assertEquals(6, ((Integer) call("colorToTrigram", s1, "黑")).intValue());
        assertEquals(1, ((Integer) call("colorToTrigram", s1, "未知")).intValue());

        assertEquals(2, ((Integer) call("soundToTrigram", s1, "人语笑")).intValue());
        assertEquals(4, ((Integer) call("soundToTrigram", s1, "敲木板")).intValue());
        assertEquals(1, ((Integer) call("soundToTrigram", s1, "钟磬金声")).intValue());
        assertEquals(3, ((Integer) call("soundToTrigram", s1, "火声爆")).intValue());
        assertEquals(8, ((Integer) call("soundToTrigram", s1, "土崩")).intValue());
        assertEquals(0, ((Integer) call("soundToTrigram", s1, "")).intValue());

        assertEquals(1, ((Integer) call("xiangToNumber", s1, "")).intValue());
    }

    @Test
    @DisplayName("B7-17 fivePhaseOfColor：青木/红火/白金/黑水/黄土/默认")
    void fivePhaseOfColor_variants() {
        Class<?>[] t = {String.class};
        assertEquals("木", str("fivePhaseOfColor", t, "青色"));
        assertEquals("火", str("fivePhaseOfColor", t, "红色"));
        assertEquals("金", str("fivePhaseOfColor", t, "白色"));
        assertEquals("水", str("fivePhaseOfColor", t, "黑色"));
        assertEquals("土", str("fivePhaseOfColor", t, "黄色"));
        assertEquals("土", str("fivePhaseOfColor", t, "无色"));
    }

    @Test
    @DisplayName("B7-18 stillObjectDirectVerdict：圆/缺/圆缺/无 四支")
    void stillObjectDirectVerdict_variants() {
        Class<?>[] t = {String.class};
        assertTrue(str("stillObjectDirectVerdict", t, "见圆物").contains("事成"));
        assertTrue(str("stillObjectDirectVerdict", t, "见缺物").contains("事败"));
        assertTrue(str("stillObjectDirectVerdict", t, "圆缺参半").contains("参半"));
        assertEquals("", str("stillObjectDirectVerdict", t, "无所见"));
    }

    @Test
    @DisplayName("B7-19 xiangBeiNote：来/去/来去/无 四支")
    void xiangBeiNote_variants() {
        Class<?>[] t = {String.class};
        assertTrue(str("xiangBeiNote", t, "飞来").contains("相向而来"));
        assertTrue(str("xiangBeiNote", t, "飞去").contains("相背而去"));
        assertEquals("", str("xiangBeiNote", t, "来而又去"));
        assertEquals("", str("xiangBeiNote", t, "静止"));
    }

    // ======================================================================
    // 六、体用生克、吉凶、应期相关 helper
    // ======================================================================

    @Test
    @DisplayName("B7-20 scoreToLevel：5→大吉 / 4→吉 / 3→平 / 2→凶 / 1→大凶 / 越界夹取")
    void scoreToLevel_variants() {
        Class<?>[] t = {int.class};
        assertEquals("大吉", str("scoreToLevel", t, 5));
        assertEquals("吉", str("scoreToLevel", t, 4));
        assertEquals("平", str("scoreToLevel", t, 3));
        assertEquals("凶", str("scoreToLevel", t, 2));
        assertEquals("大凶", str("scoreToLevel", t, 1));
        assertEquals("大凶", str("scoreToLevel", t, 0));
        assertEquals("大吉", str("scoreToLevel", t, 99));
    }

    @Test
    @DisplayName("B7-21 relationToBodyHex：空→比和 / 同卦→比和 / 上下取最著生体")
    void relationToBodyHex_variants() {
        Class<?>[] t = {String.class, String.class};
        assertEquals("比和", str("relationToBodyHex", t, "", "乾"));
        assertEquals("比和", str("relationToBodyHex", t, "乾为天", "乾"));
        assertEquals("生体", str("relationToBodyHex", t, "地天泰", "乾"));
    }

    @Test
    @DisplayName("B7-22 breakdownNote：缺体/缺用→空串；吉→空串；凶(用克体)→通关；平→持正")
    void breakdownNote_variants() {
        Class<?>[] t = {String.class, String.class, String.class};
        assertEquals("", str("breakdownNote", t, "", "离", "凶"));
        assertEquals("", str("breakdownNote", t, "乾", "离", "吉"));
        assertTrue(str("breakdownNote", t, "乾", "离", "凶").contains("趋避"));
        assertTrue(str("breakdownNote", t, "乾", "离", "凶").contains("通关"));
        assertTrue(str("breakdownNote", t, "乾", "坤", "平").contains("持正"));
    }

    @Test
    @DisplayName("B7-23 mutualBodyUseNote：乾坤无互→空串；常卦→体互用互之辨")
    void mutualBodyUseNote_variants() {
        Class<?>[] t = {String.class, String.class, int.class};
        assertEquals("", str("mutualBodyUseNote", t, "乾为天", "乾", 1));
        assertFalse(str("mutualBodyUseNote", t, "地天泰", "乾", 1).isBlank());
        assertFalse(str("mutualBodyUseNote", t, "地天泰", "乾", 6).isBlank());
    }

    @Test
    @DisplayName("B7-24 shuaiWangNote：缺体→空串；旺/衰有生/衰无生/未知 四支")
    void shuaiWangNote_variants() {
        Class<?>[] t = {String.class, String.class, String.class, String.class, String.class};
        assertEquals("", str("shuaiWangNote", t, "", "坤", "乾为天", "天风姤", "旺"));
        assertTrue(str("shuaiWangNote", t, "乾", "坤", "乾为天", "天风姤", "旺").contains("体既乘旺"));
        assertTrue(str("shuaiWangNote", t, "乾", "坤", "乾为天", "天风姤", "死").contains("生体之卦"));
        assertTrue(str("shuaiWangNote", t, "乾", "离", "坤为地", "地天泰", "死").contains("衰旺之诀"));
        assertTrue(str("shuaiWangNote", t, "乾", "坤", "乾为天", "天风姤", "").contains("详体之盛衰"));
    }

    @Test
    @DisplayName("B7-25 zhanGuaJueNote / bodyRelationNotes：缺体→空串；正常产断语")
    void zhanGuaBodyRelation_variants() {
        Class<?>[] t = {String.class, String.class, String.class, String.class};
        assertEquals("", str("zhanGuaJueNote", t, "", "坤", "乾为天", "天风姤"));
        assertTrue(str("zhanGuaJueNote", t, "乾", "坤", "乾为天", "天风姤").contains("生体是用卦"));
        assertEquals("", str("bodyRelationNotes", t, "", "坤", "乾为天", "天风姤"));
        assertNotNull(str("bodyRelationNotes", t, "乾", "离", "坤为地", "地天泰"));
    }

    // ======================================================================
    // 七、一撮金爻诀 / 笔画 / 相字 / 图象补录 / 起卦法断
    // ======================================================================

    @Test
    @DisplayName("B7-26 yicuoJinYaoNote：内置兜底命中 / 越界位 / 空卦 / 爻位0 四支")
    void yicuoJinYaoNote_variants() {
        Class<?>[] t = {String.class, int.class};
        assertTrue(str("yicuoJinYaoNote", t, "乾为天", 2).contains("一撮金·爻诀"));
        assertEquals("", str("yicuoJinYaoNote", t, "乾为天", 1));
        assertEquals("", str("yicuoJinYaoNote", t, "乾为天", 0));
        assertEquals("", str("yicuoJinYaoNote", t, "乾为天", 9));
        assertEquals("", str("yicuoJinYaoNote", t, "", 1));
    }

    @Test
    @DisplayName("B7-27 stroke：内置常用字/Unihan/码位回退")
    void stroke_variants() {
        Class<?>[] t = {char.class};
        assertEquals(1, ((Integer) call("stroke", t, '一')).intValue());
        assertEquals(11, ((Integer) call("stroke", t, '乾')).intValue());
        assertTrue(((Integer) call("stroke", t, 'A')).intValue() > 0);
    }

    @Test
    @DisplayName("B7-28 相字：xiangZiCounts 类目/条目数 / xiangZiRule 命中与未命中")
    void xiangZi_variants() {
        int[] counts = (int[]) call("xiangZiCounts", new Class<?>[]{});
        assertNotNull(counts);
        assertEquals(2, counts.length);
        assertTrue(counts[0] > 0);
        assertTrue(counts[1] > 0);
        Class<?>[] t = {String.class, String.class};
        assertNotNull(str("xiangZiRule", t, "不存在的类目", "不存在的键"));
    }

    @Test
    @DisplayName("B7-29 tuXiangBuLu：内置兜底非空")
    void tuXiangBuLu_nonEmpty() {
        Object o = call("tuXiangBuLu", new Class<?>[]{});
        assertNotNull(o);
        assertTrue(((Map<?, ?>) o).size() > 0);
    }

    @Test
    @DisplayName("B7-30 castMethodVerdictText：风觉占/鸟占/听声音占/未知")
    void castMethodVerdictText_variants() {
        Class<?>[] t = {String.class};
        assertTrue(str("castMethodVerdictText", t, "风觉占").contains("风觉占"));
        assertTrue(str("castMethodVerdictText", t, "鸟占").contains("鸟占"));
        assertTrue(str("castMethodVerdictText", t, "听声音占").contains("听声音占"));
        assertEquals("", str("castMethodVerdictText", t, "未知"));
    }

    // ======================================================================
    // 八、字占取数 splitCharacters（1~11 字 + 平仄声调）
    // ======================================================================

    @Test
    @DisplayName("B7-31 splitCharacters：空串/1字/2字/3字/11字+ 各支")
    void splitCharacters_lengths() {
        Class<?>[] t1 = {String.class};
        assertArrayEquals(new int[]{1, 1}, (int[]) call("splitCharacters", t1, ""));
        assertArrayEquals(new int[]{1, 1}, (int[]) call("splitCharacters", t1, "一"));
        assertArrayEquals(new int[]{1, 2}, (int[]) call("splitCharacters", t1, "一二"));
        assertArrayEquals(new int[]{1, 5}, (int[]) call("splitCharacters", t1, "一二三"));
        int[] long11 = (int[]) call("splitCharacters", t1, "一二三四五六七八九十甲");
        assertEquals(2, long11.length);
        assertEquals(5, long11[0]);
        assertEquals(6, long11[1]);
    }

    @Test
    @DisplayName("B7-32 splitCharacters：前端平仄串命中（1111）→ 4字各拆二")
    void splitCharacters_tones() {
        Class<?>[] t2 = {String.class, String.class};
        assertArrayEquals(new int[]{2, 2}, (int[]) call("splitCharacters", t2, "一二三四", "1111"));
    }

    @Test
    @DisplayName("B7-33 charNumberSource：空/字数/前端平仄/内置平水韵/笔画")
    void charNumberSource_variants() {
        Class<?>[] t = {String.class, String.class};
        assertEquals("空", str("charNumberSource", t, "", null));
        assertEquals("字数", str("charNumberSource", t, "一二三四五六七八九十甲", null));
        assertEquals("前端平仄", str("charNumberSource", t, "一二三四", "1111"));
    }

    @Test
    @DisplayName("B7-34 tenResponseJueSummary：旺/衰/空 vitality 三支")
    void tenResponseJueSummary_variants() {
        Class<?>[] t = {String.class};
        assertTrue(str("tenResponseJueSummary", t, "旺").contains("十应要旨"));
        assertTrue(str("tenResponseJueSummary", t, "衰").contains("十应要旨"));
        assertTrue(str("tenResponseJueSummary", t, "").contains("十应要旨"));
    }

    @Test
    @DisplayName("B7-35 fivePhaseOf / trigramNumberOf：八卦五行与先天数")
    void fivePhaseAndTrigramNumber_variants() {
        Class<?>[] t = {String.class};
        assertEquals("金", str("fivePhaseOf", t, "乾"));
        assertEquals("木", str("fivePhaseOf", t, "震"));
        assertEquals(1, ((Integer) call("trigramNumberOf", t, "乾")).intValue());
        assertEquals(8, ((Integer) call("trigramNumberOf", t, "坤")).intValue());
    }
}
