package com.ocean.ontologyframework.plumblossom.validation;

import com.ocean.ontologyframework.plumblossom.PlumBlossomOntologyJobWorker;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 梅花易数·分支矩阵（第四批）：日应·鸟兽时日·加姓·写字之应·真生真克·疾病测鬼神·饮食·占卦诀·
 * 衰旺·动静·卦应/内外动静·一撮金爻诀·体用互变诸「断法」纯函数/表查，及卷之一~卷之五「照录备考」
 * 零参 note getter 非空抽样。
 *
 * <p>纯反射、离线（不加载本体：ontoLore/hontoGuaYing=null 走内置兜底；backendService=null 时
 * {@code yicuoJinYaoNote} 直接走内置同源表）。
 */
@DisplayName("梅花易数·分支矩阵·第四批（断法纯函数/诸表查/照录getter·离线）")
public class PlumBlossomBranchMatrixBatch4Test {

    private static Method m(String name, Class<?>... params) throws Exception {
        Method mm = PlumBlossomOntologyJobWorker.class.getDeclaredMethod(name, params);
        mm.setAccessible(true);
        return mm;
    }

    @SuppressWarnings("unchecked")
    private static <T> T call(Method method, Object target, Object... args) throws Exception {
        return (T) method.invoke(target, args);
    }

    private static String s(Method method, Object target, Object... args) throws Exception {
        return (String) call(method, target, args);
    }

    private static PlumBlossomOntologyJobWorker worker() {
        return new PlumBlossomOntologyJobWorker();
    }

    // ==========================================================================
    // N. 断法纯函数 / 表查
    // ==========================================================================

    @Test
    @DisplayName("N1·日应 riYingNote：寅卯木/巳午火/申酉金/亥子水/辰戌丑未土 + 空/未知空串")
    void riYingCases() throws Exception {
        Method t = m("riYingNote", String.class);
        assertThat(s(t, null, "寅日")).as("寅→木").contains("日应").contains("本日属木");
        assertThat(s(t, null, "午日")).as("午→火").contains("本日属火");
        assertThat(s(t, null, "申日")).as("申→金").contains("本日属金");
        assertThat(s(t, null, "子日")).as("子→水").contains("本日属水");
        assertThat(s(t, null, "辰日")).as("辰→土").contains("本日属土");
        assertThat(s(t, null, "")).as("空→空").isEmpty();
        assertThat(s(t, null, "甲")).as("无地支→空").isEmpty();
    }

    @Test
    @DisplayName("N2·鸟兽时日 niaoShouShiRiNote：庚(凶)/丁(凶)/己(吉)/壬(吉) + 未知/空")
    void niaoShouCases() throws Exception {
        Method t = m("niaoShouShiRiNote", String.class);
        assertThat(s(t, null, "庚")).as("庚→凶刃").contains("凶刃").contains("鸟兽时日兆");
        assertThat(s(t, null, "丁")).as("丁→凶刃").contains("凶刃");
        assertThat(s(t, null, "己")).as("己→食禄").contains("食禄");
        assertThat(s(t, null, "壬")).as("壬→食禄").contains("食禄");
        assertThat(s(t, null, "乙")).as("未知→空").isEmpty();
        assertThat(s(t, null, "")).as("空→空").isEmpty();
    }

    @Test
    @DisplayName("N3·加姓 jiaXingNote：有姓名加数 vs 无姓名示例")
    void jiaXingCases() throws Exception {
        Method t = m("jiaXingNote", String.class, int.class);
        assertThat(s(t, null, "张", 11)).as("有姓名").contains("起卦加数例·加姓").contains("今加张姓");
        assertThat(s(t, null, null, 0)).as("无姓名→示例").contains("加王姓四画");
    }

    @Test
    @DisplayName("N4·写字之应 xieZiZhiYingNote：关键词命中 vs 未命中空串")
    void xieZiCases() throws Exception {
        Method t = m("xieZiZhiYingNote", String.class);
        assertThat(s(t, null, "浓墨写字")).as("命中").contains("复明写字之应");
        assertThat(s(t, null, "毫无所观")).as("未命中→空").isEmpty();
        assertThat(s(t, null, (Object) null)).as("null→空").isEmpty();
    }

    @Test
    @DisplayName("N5·真生真克 zhenShengZhenKeNote：金/木/火三体 + 土兜底")
    void zhenShengZhenKeCases() throws Exception {
        Method t = m("zhenShengZhenKeNote", String.class);
        PlumBlossomOntologyJobWorker w = worker();
        assertThat(s(t, w, "乾")).as("金体→真火").contains("真生真克").contains("炉中火");
        assertThat(s(t, w, "震")).as("木体→真金").contains("快刀锐刃");
        assertThat(s(t, w, "离")).as("火体→真水").contains("真水能克");
        assertThat(s(t, w, "坤")).as("土体→default").contains("形色之火不能克");
    }

    @Test
    @DisplayName("N6·疾病测鬼神 diseaseSpiritNote + 逐爻 diseaseYaoNote")
    void diseaseCases() throws Exception {
        Method spirit = m("diseaseSpiritNote", String.class);
        assertThat(s(spirit, null, "乾")).as("乾→西北").contains("西北方之神");
        assertThat(s(spirit, null, "坎")).as("坎→北方").contains("北方之鬼");
        assertThat(s(spirit, null, "无")).as("未知→空").isEmpty();
        assertThat(s(spirit, null, (Object) null)).as("null→空").isEmpty();

        Method yao = m("diseaseYaoNote", String.class, int.class);
        String qk = s(yao, null, "天地否", 1);
        assertThat(qk).as("天地否·初爻动标【今动】").contains("【今动】").contains("初爻动");
        String other = s(yao, null, "乾为天", 3);
        assertThat(other).as("他卦不标【今动】").contains("乾上坤下").doesNotContain("【今动】");
    }

    @Test
    @DisplayName("N7·饮食 dietNote：无坎兑/有坎/有兑/坎兑/坎逢坤")
    void dietCases() throws Exception {
        Method t = m("dietNote", String.class, String.class, String.class, String.class);
        PlumBlossomOntologyJobWorker w = worker();
        assertThat(s(t, w, "乾", null, null, null)).as("无坎兑").contains("无坎无兑");
        assertThat(s(t, w, "乾", "坎", null, null)).as("有坎").contains("有坎则有酒");
        assertThat(s(t, w, "乾", "兑", null, null)).as("有兑").contains("有兑则有食");
        assertThat(s(t, w, "坎", "兑", null, null)).as("坎兑").contains("酒肉醉饱");
        assertThat(s(t, w, "坎", "坤", null, null)).as("坎逢坤→无酒").contains("坎亦无酒");
    }

    @Test
    @DisplayName("N8·占卦诀 zhanGuaJueNote：用卦生体(速成) / 克体阻节 / null空")
    void zhanGuaJueCases() throws Exception {
        Method t = m("zhanGuaJueNote", String.class, String.class, String.class, String.class);
        PlumBlossomOntologyJobWorker w = worker();
        assertThat(s(t, w, "震", "坎", "", "")).as("坎水生震木→用卦生体").contains("生体是用卦");
        assertThat(s(t, w, "乾", "离", "", "")).as("离火克乾金→克体阻节").contains("克体之卦阻节");
        assertThat(s(t, w, null, "坎", "", "")).as("null→空").isEmpty();
    }

    @Test
    @DisplayName("N9·衰旺 shuaiWangNote：体旺/体衰有生/体衰逢克/未详/null空")
    void shuaiWangCases() throws Exception {
        Method t = m("shuaiWangNote", String.class, String.class, String.class, String.class, String.class);
        PlumBlossomOntologyJobWorker w = worker();
        assertThat(s(t, w, "乾", "", "", "", "旺")).as("体旺").contains("体既乘旺");
        assertThat(s(t, w, "震", "坎", "", "", "衰")).as("体衰有生→衰稍吉").contains("衰稍吉");
        assertThat(s(t, w, "乾", "离", "", "", "衰")).as("体衰逢克→凶甚").contains("凶甚");
        assertThat(s(t, w, "乾", "", "", "", "")).as("未详").contains("当详体之盛衰");
        assertThat(s(t, w, null, "", "", "", "旺")).as("null→空").isEmpty();
    }

    @Test
    @DisplayName("N10·动静 dongJingNote：行/走/坐/卧/立 迟速 + 未详")
    void dongJingCases() throws Exception {
        Method t = m("dongJingNote", String.class);
        assertThat(s(t, null, "行")).as("行速").contains("行则应速");
        assertThat(s(t, null, "走")).as("走愈速").contains("走则愈速");
        assertThat(s(t, null, "坐")).as("坐迟").contains("坐则应迟");
        assertThat(s(t, null, "卧")).as("卧愈迟").contains("卧则愈迟");
        assertThat(s(t, null, "立")).as("立半").contains("立则半迟半速");
        assertThat(s(t, null, "莫名其妙")).as("未详→无迟速句").contains("体用动静之诀").doesNotContain("应速");
    }

    @Test
    @DisplayName("N11·卦应 guaYingNote + 内外动静 neiWaiDongJingNote：命中/空/未知")
    void guaYingCases() throws Exception {
        Method g = m("guaYingNote", String.class);
        assertThat(s(g, null, "乾")).as("乾卦应").contains("卦应（乾）");
        assertThat(s(g, null, (Object) null)).as("null→空").isEmpty();
        assertThat(s(g, null, "无")).as("未知→空").isEmpty();

        Method n = m("neiWaiDongJingNote", String.class);
        assertThat(s(n, null, "乾")).as("乾内外动静").contains("内外动静（乾）").contains("玄黄");
        assertThat(s(n, null, "")).as("空→空").isEmpty();
        assertThat(s(n, null, "无")).as("未知→空").isEmpty();
    }

    @Test
    @DisplayName("N12·一撮金爻诀 yicuoJinYaoNote：乾为天二爻命中 / 越界/未知/空 → 空串")
    void yicuoJinCases() throws Exception {
        Method t = m("yicuoJinYaoNote", String.class, int.class);
        PlumBlossomOntologyJobWorker w = worker();
        String hit = s(t, w, "乾为天", 2);
        assertThat(hit).as("乾为天二爻").contains("得意宜逢贵").contains("上上").contains("九二二爻");
        assertThat(s(t, w, "乾为天", 0)).as("爻0→空").isEmpty();
        assertThat(s(t, w, "乾为天", 7)).as("爻7→空").isEmpty();
        assertThat(s(t, w, "不存在的卦", 1)).as("未知卦→空").isEmpty();
        assertThat(s(t, w, (Object) null, 1)).as("null→空").isEmpty();
    }

    @Test
    @DisplayName("N13·体用互变 mutualBodyUseNote：天地否命中 / 乾坤无互空 / null空")
    void mutualBodyUseCases() throws Exception {
        Method t = m("mutualBodyUseNote", String.class, String.class, int.class);
        PlumBlossomOntologyJobWorker w = worker();
        String hit = s(t, w, "天地否", "乾", 1);
        assertThat(hit).as("天地否定爻1").contains("体用互变之诀").contains("体在上，上互为体之互");
        assertThat(s(t, w, "乾为天", "乾", 1)).as("乾坤无互→空").isEmpty();
        assertThat(s(t, w, (Object) null, "乾", 1)).as("null→空").isEmpty();
    }

    @Test
    @DisplayName("N14·相字 xiangZiNote：空/未匹配 → 空串")
    void xiangZiCases() throws Exception {
        Method t = m("xiangZiNote", String.class);
        assertThat(s(t, null, "")).as("空→空").isEmpty();
        assertThat(s(t, null, (Object) null)).as("null→空").isEmpty();
        assertThat(s(t, null, "毫无所相")).as("未匹配→空").isEmpty();
    }

    @Test
    @DisplayName("N15·零参照录 getter 非空抽样（卷之一~卷之五/十应总纲/应期诀）")
    void zeroArgNoteGetters() throws Exception {
        String[] names = {
                "tenResponsePrinciple", "wanWuYingQiNote", "xianHouTianYingQiNote",
                "guanMeiShuJueNote", "sanYaoXuNote", "juanSanZhanGuaJueNote",
                "juanWuWuXingQuanBeiNote", "yiLunNote", "baGuaXinYiTiYongJueNote"
        };
        for (String name : names) {
            assertThat(s(m(name), null)).as(name + " 应非空").isNotBlank();
        }
    }
}
