package com.ocean.ontologyframework.plumblossom.validation;

import com.ocean.ontologyframework.plumblossom.PlumBlossomOntologyJobWorker;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 梅花易数·分支矩阵（第五批）：卷之一~卷之五「照录备考」零参 note getter 全量前缀/关键字、
 * 十应/应期总纲、长文本片段（屋宅占/器物占/变卦式八则）、objectDaikaoText 分派、解读规则串、
 * 小整数中文化 {@code chineseNumber}。纯反射、离线（target 传 null / 空 worker，走内置兜底）。
 */
@DisplayName("梅花易数·分支矩阵·第五批（照录getter/十应应期/长文本/规则串/小整数·离线）")
public class PlumBlossomBranchMatrixBatch5Test {

    private static Method m(String name, Class<?>... params) throws Exception {
        Method mm = PlumBlossomOntologyJobWorker.class.getDeclaredMethod(name, params);
        mm.setAccessible(true);
        return mm;
    }

    @SuppressWarnings("unchecked")
    private static <T> T call(Method method, Object target, Object... args) throws Exception {
        return (T) method.invoke(target, args);
    }

    private static String s(String name) throws Exception {
        return (String) call(m(name), null);
    }

    private static void assertPrefix(String[] names, String prefix) throws Exception {
        for (String n : names) {
            assertThat(s(n)).as(n + " 应以「" + prefix + "」开头").startsWith(prefix);
        }
    }

    // ==========================================================================
    // P. 照录 getter / 总纲 / 长文本 / 规则串
    // ==========================================================================

    @Test
    @DisplayName("P1·卷之一系：象数易理/八卦类占按语/占例/起卦法/风觉鸟占 + 卷首序 + 占法玩法")
    void juanYiNotes() throws Exception {
        assertPrefix(new String[]{
                "juanYiXiangShuNote", "juanYiBaGuaLeiZhanAnNote", "juanYiZhanLiNote",
                "juanYiQiGuaFaNote", "juanYiFengJueNiaoNote"
        }, "卷之一");
        assertThat(s("meiHuaYiShuXuNote")).as("卷首序").startsWith("梅花易数序");
        assertThat(s("zhanFaWanFaNote")).as("占法玩法").startsWith("占法／玩法");
    }

    @Test
    @DisplayName("P2·卷之二系 note getter 前缀（三要诸论/心易玄机/占卜总诀…加数例 共11篇）")
    void juanErNotes() throws Exception {
        assertPrefix(new String[]{
                "juanErZhuLunNote", "juanErXinYiXuanJiNote", "juanErZhanBuZongJueNote",
                "juanErZhanBuLunLiJueNote", "juanErXianTianHouTianLunNote", "sanYaoChaiZiZhiYingNote",
                "juanErTiYongZongJueNote", "juanErShiBaZhanNote", "juanErFuMingNote",
                "juanErDongXuanGeNote", "juanErJiaShuWuZhaiNote"
        }, "卷之二");
    }

    @Test
    @DisplayName("P3·卷之三系 note getter 前缀（断占总诀诸篇 共21篇）")
    void juanSanNotes() throws Exception {
        assertPrefix(new String[]{
                "juanSanBaGuaDingYinYangNote", "juanSanBianGuaShiBaZeNote", "juanSanZhanGuaJueNote",
                "juanSanTiYongHuBianNote", "juanSanTiYongShengKeNote", "juanSanTiYongShuaiWangNote",
                "juanSanTiYongDongJingNote", "juanSanZhanBuZuoDuanNote", "juanSanWanWuFuNote",
                "juanSanYinShiPianNote", "juanSanGuanWuXuanMiaoGeNote", "juanSanZhuShiXiangYingGeNote",
                "juanSanZhuGuaFanDuiNote", "juanSanZhanWuLeiLiNote", "juanSanTiYongDuanWuNote",
                "juanSanWuShuWeiTiNote", "juanSanGuanWuZhuJueNote", "juanSanWanWuXiNianNote",
                "juanSanZhanBuShiYingJueNote", "juanSanLunShiShiDaYingNote", "juanSanGuaYingNote"
        }, "卷之三");
    }

    @Test
    @DisplayName("P4·卷之四系（字画指迷 相字成篇）note getter 前缀 共8篇")
    void juanSiNotes() throws Exception {
        assertPrefix(new String[]{
                "juanSiZiHuaZhiMiXuNote", "juanSiXuanHuangNote", "juanSiFuNote",
                "juanSiZiHuaJingYanNote", "juanSiXiangZiXinYiNote", "juanSiDuanFaNote",
                "juanSiLiuShenNote", "juanSiWuXingNote"
        }, "卷之四");
    }

    @Test
    @DisplayName("P5·卷之五系（拆字杂编 相字成篇）note getter 前缀 共14篇")
    void juanWuNotes() throws Exception {
        assertPrefix(new String[]{
                "juanWuWuXingQuanBeiNote", "juanWuShenShaNote", "juanWuQiYanZuoYongGeNote",
                "juanWuBiLiGeXiJiangYueNote", "juanWuZhanLiNote", "juanWuBiaoNote",
                "juanWuSiYanDuBuNote", "juanWuWuYanZuoYongGeNote", "juanWuBieLiPianNote",
                "juanWuLiuYanPouDuanGeNote", "juanWuGeWuZhangNote", "juanWuWuLiLunNote",
                "juanWuWuXingLiuShenLunNote", "juanWuJinShengZhangNote"
        }, "卷之五");
    }

    @Test
    @DisplayName("P6·三要灵应篇 序/导论/总论")
    void sanYaoNotes() throws Exception {
        assertThat(s("sanYaoXuNote")).as("序").startsWith("三要灵应篇序");
        assertThat(s("sanYaoLingYingGaiLunNote")).as("导论").startsWith("三要灵应篇·导论");
        assertThat(s("sanYaoLingYingZongLunNote")).as("总论").startsWith("三要灵应篇·总论");
    }

    @Test
    @DisplayName("P7·十应/应期总纲：奥论/占卜十应诀/万物赋应期/先天后天论/十应目论/方应")
    void shiYingNotes() throws Exception {
        assertThat(s("tenResponsePrinciple")).as("十应奥论").contains("十应奥论");
        assertThat(s("tenResponseJueNote")).as("十应总目").contains("十应者").contains("正应");
        assertThat(s("wanWuYingQiNote")).as("万物赋应期").contains("万物赋·应期");
        assertThat(s("xianHouTianYingQiNote")).as("先天后天论").contains("先天后天论·应期");
        assertThat(s("shiYingMuLunNote")).as("十应目论").startsWith("十应目论");
        assertThat(s("zhanBuShiYingJueNote")).as("逐应原文").contains("方应");
    }

    @Test
    @DisplayName("P8·长文本片段：屋宅占诀/器物占/变卦式八则")
    void longTexts() throws Exception {
        assertThat(s("wuZhaiZhanText")).as("屋宅占").startsWith("〔屋宅之占诀〕");
        assertThat(s("qiWuZhanText")).as("器物占").startsWith("〔器物占〕");
        assertThat(s("bianGuaShiText")).as("变卦式八则").startsWith("〔变卦式八则");
    }

    @Test
    @DisplayName("P9·objectDaikaoText：占物类命中（器物占+变卦式） vs 非占物/null → 空串")
    void objectDaikao() throws Exception {
        Method t = m("objectDaikaoText", String.class);
        assertThat((String) call(t, null, "占物")).as("占物类命中").contains("器物占").contains("变卦式八则");
        assertThat((String) call(t, null, "器物")).as("器物命中").contains("观其爻辞").contains("器物占");
        assertThat((String) call(t, null, "无关之词")).as("非占物→空").isEmpty();
        assertThat((String) call(t, null, (Object) null)).as("null→空").isEmpty();
    }

    @Test
    @DisplayName("P10·观物类：观梅数诀序/观物洞玄歌/图象补录/相字取象摘要")
    void miscNotes() throws Exception {
        assertThat(s("guanMeiShuJueNote")).as("观梅数诀序").startsWith("观梅数诀序");
        assertThat(s("dongXuanYinBaNote")).as("观物洞玄歌").startsWith("观物洞玄歌");
        assertThat(s("tuXiangBuLuNote")).as("图象补录").startsWith("图象补录");
        assertThat(s("xiangZiDigestText")).as("相字摘要").startsWith("（相字取象");
    }

    @Test
    @DisplayName("P11·解读规则串：解读要求 vs 白话解释要求（非空且互不包含对方标题）")
    void tailorRules() throws Exception {
        String interpret = s("interpretTailorRules");
        String plain = s("interpretPlainTailorRules");
        assertThat(interpret).as("解读要求").contains("解读要求").contains("严禁编造出处");
        assertThat(plain).as("白话要求").contains("白话解释要求").contains("不宣扬迷信");
        assertThat(interpret).as("解读不含白话标题").doesNotContain("白话解释要求");
        assertThat(plain).as("白话不含解读标题").doesNotContain("解读要求");
    }

    @Test
    @DisplayName("P12·chineseNumber 小整数：0/9/10/15/20/99 + 越界/负数回退")
    void chineseNumberCases() throws Exception {
        Method t = m("chineseNumber", int.class);
        assertThat((String) call(t, null, 0)).as("0").isEqualTo("零");
        assertThat((String) call(t, null, 9)).as("9").isEqualTo("九");
        assertThat((String) call(t, null, 10)).as("10").isEqualTo("十");
        assertThat((String) call(t, null, 15)).as("15").isEqualTo("十五");
        assertThat((String) call(t, null, 20)).as("20").isEqualTo("二十");
        assertThat((String) call(t, null, 99)).as("99").isEqualTo("九十九");
        assertThat((String) call(t, null, 100)).as("越界→String.valueOf").isEqualTo("100");
        assertThat((String) call(t, null, -1)).as("负数→String.valueOf").isEqualTo("-1");
    }

    @Test
    @DisplayName("P13·诸论 getter：占卜克应之诀/遗论/卦断遗论/八卦心易体用诀")
    void zhuLunNotes() throws Exception {
        assertThat(s("zhanBuKeYingJueNote")).as("克应").startsWith("占卜克应之诀（卷之三）");
        assertThat(s("yiLunNote")).as("遗论").startsWith("遗论（卷之二）");
        assertThat(s("guaDuanYiLunNote")).as("卦断遗论").startsWith("卦断遗论（卷之二）");
        assertThat(s("baGuaXinYiTiYongJueNote")).as("八卦心易体用诀").startsWith("八卦心易体用诀（卷之二）");
    }
}
