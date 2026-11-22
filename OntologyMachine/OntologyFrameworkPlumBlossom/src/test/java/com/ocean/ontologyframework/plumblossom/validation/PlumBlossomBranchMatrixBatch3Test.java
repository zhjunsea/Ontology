package com.ocean.ontologyframework.plumblossom.validation;

import com.ocean.ontologyframework.plumblossom.PlumBlossomOntologyJobWorker;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 梅花易数·分支矩阵（第三批）：十应各类之应（tenCategoryNote）/ 应体生克语气（shengKeTone）/
 * 向背坐端·论事十大应·观物洞玄·万物赋·三要灵应诸兆（*Note）/ 字占取数（splitCharacters 四档、
 * charNumberSource 六值、splitClauses、inferHumanSub）/ 四不占告示（preceptAdmonition）。
 *
 * <p>纯反射、离线（不加载本体：ontoLore=null、ontoZuoDuan/ontoShiDaYing=null、ontoPreceptAdmonition 为空 Map，
 * 全部走内置兜底）。
 */
@DisplayName("梅花易数·分支矩阵·第三批（十应类应/生克语气/诸兆/字占/四不占·离线）")
public class PlumBlossomBranchMatrixBatch3Test {

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

    private static int[] arr(Method method, Object target, Object... args) throws Exception {
        return (int[]) call(method, target, args);
    }

    private static PlumBlossomOntologyJobWorker worker() {
        return new PlumBlossomOntologyJobWorker();
    }

    // ==========================================================================
    // I. 十应各类之应 tenCategoryNote（实例方法）
    // ==========================================================================

    @Test
    @DisplayName("I1·天时之应 tenCategoryNote：晴霁(离)/雨雪(坎)/雷风(震巽)/default(乾) 四支")
    void tenCategoryTianShi() throws Exception {
        Method t = m("tenCategoryNote", String.class, String.class, String.class, String.class);
        PlumBlossomOntologyJobWorker w = worker();
        assertThat(s(t, w, "天时", "天气晴霁", "乾", "春")).as("晴霁→离")
                .contains("天时之应").contains("晴霁日中为离之时");
        assertThat(s(t, w, "天时", "天将雨雪", "乾", "春")).as("雨雪→坎")
                .contains("天时之应").contains("雨雪为坎之时");
        assertThat(s(t, w, "天时", "雷风交加", "乾", "春")).as("雷风→震巽")
                .contains("天时之应").contains("雷风为震、巽之时");
        assertThat(s(t, w, "天时", "天气不明", "乾", "春")).as("default→乾")
                .contains("天时之应").contains("天无云翳");
    }

    @Test
    @DisplayName("I2·地理之应 tenCategoryNote：茂树(震)/江湖(坎)/窑灶(离)/default(艮) 四支")
    void tenCategoryDiLi() throws Exception {
        Method t = m("tenCategoryNote", String.class, String.class, String.class, String.class);
        PlumBlossomOntologyJobWorker w = worker();
        assertThat(s(t, w, "地理", "茂树秀竹", "离", "春")).as("茂树→震")
                .contains("地理之应").contains("茂树秀竹为震之地");
        assertThat(s(t, w, "地理", "大江湖泊", "震", "春")).as("江湖→坎")
                .contains("地理之应").contains("江湖河池川泽溪涧为坎之地");
        assertThat(s(t, w, "地理", "有窑灶", "坤", "春")).as("窑灶→离")
                .contains("地理之应").contains("窑灶之地为离");
        assertThat(s(t, w, "地理", "岩穴之中", "乾", "春")).as("default→艮")
                .contains("地理之应").contains("岩穴之地为艮");
    }

    @Test
    @DisplayName("I3·人事之应 tenCategoryNote：老人(乾)/老妇(坤)/少男(艮)/少女(兑)/无归属")
    void tenCategoryRenShi() throws Exception {
        Method t = m("tenCategoryNote", String.class, String.class, String.class, String.class);
        PlumBlossomOntologyJobWorker w = worker();
        assertThat(s(t, w, "人事", "一老人至", "乾", "春")).as("老人→乾").contains("人事之应").contains("老人，属乾");
        assertThat(s(t, w, "人事", "一老妇至", "乾", "春")).as("老妇→坤").contains("老妇，属坤");
        assertThat(s(t, w, "人事", "一少男", "乾", "春")).as("少男→艮").contains("少男，属艮");
        assertThat(s(t, w, "人事", "一少女", "乾", "春")).as("少女→兑").contains("少女，属兑");
        assertThat(s(t, w, "人事", "众多路人", "乾", "春")).as("无归属 who 为空").contains("人事之应").doesNotContain("今见");
    }

    @Test
    @DisplayName("I4·时令/方卦/静物/言语/声音之应 tenCategoryNote")
    void tenCategoryOtherCats() throws Exception {
        Method t = m("tenCategoryNote", String.class, String.class, String.class, String.class);
        PlumBlossomOntologyJobWorker w = worker();
        assertThat(s(t, w, "时令", "无", "乾", "春")).as("时令 春→木旺").contains("时令之应").contains("木旺");
        assertThat(s(t, w, "时令", "无", "乾", null)).as("时令 null→土旺").contains("土旺");
        assertThat(s(t, w, "方卦", "立于南方", "乾", "春")).as("方卦").contains("方卦之应");
        assertThat(s(t, w, "静物", "案头一瓶", "乾", "春")).as("静物").contains("静物之应");
        assertThat(s(t, w, "言语", "闻人言语", "乾", "春")).as("言语").contains("言语之应");
        assertThat(s(t, w, "声音", "远处钟声", "乾", "春")).as("声音").contains("声音之应");
    }

    @Test
    @DisplayName("I5·动物之应 tenCategoryNote：鹊/鸦/鸿雁/鸡/马 逐兆 + base")
    void tenCategoryDongWu() throws Exception {
        Method t = m("tenCategoryNote", String.class, String.class, String.class, String.class);
        PlumBlossomOntologyJobWorker w = worker();
        assertThat(s(t, w, "动物", "喜鹊登枝", "乾", "春")).as("鹊").contains("动物之应").contains("今见鹊");
        assertThat(s(t, w, "动物", "乌鸦飞过", "乾", "春")).as("鸦").contains("今见鸦");
        assertThat(s(t, w, "动物", "鸿雁南飞", "乾", "春")).as("鸿雁").contains("今见鸿雁");
        assertThat(s(t, w, "动物", "闻鸡唱", "乾", "春")).as("鸡").contains("今闻鸡唱");
        assertThat(s(t, w, "动物", "见马嘶", "乾", "春")).as("马").contains("今见马嘶");
        assertThat(s(t, w, "动物", "见一犬", "乾", "春")).as("无兆→仅 base").contains("动物之应").doesNotContain("今见鹊");
    }

    @Test
    @DisplayName("I6·未录类别 tenCategoryNote → 空串")
    void tenCategoryUnknown() throws Exception {
        Method t = m("tenCategoryNote", String.class, String.class, String.class, String.class);
        PlumBlossomOntologyJobWorker w = worker();
        assertThat(s(t, w, "未知类", "x", "乾", "春")).isEmpty();
        assertThat(s(t, w, null, "x", "乾", "春")).isEmpty();
    }

    // ==========================================================================
    // J. 应体生克语气 shengKeTone（实例方法）
    // ==========================================================================

    @Test
    @DisplayName("J1·shengKeTone 四支：比和/逢生/见克/泄气（以乾金为应卦）")
    void shengKeToneCases() throws Exception {
        Method t = m("shengKeTone", int.class, String.class);
        PlumBlossomOntologyJobWorker w = worker();
        assertThat(s(t, w, 1, "乾")).as("金/金→比和").isEqualTo("比和而吉");
        assertThat(s(t, w, 1, "坎")).as("金生水→逢生").isEqualTo("逢生（大吉）");
        assertThat(s(t, w, 1, "震")).as("金克木→见克").isEqualTo("见克（不吉）");
        assertThat(s(t, w, 1, "坤")).as("体土生金→泄气").isEqualTo("泄气（体生之，为耗）");
    }

    // ==========================================================================
    // K. 诸兆 helpers（静态）
    // ==========================================================================

    @Test
    @DisplayName("K1·坐端八方 zuoDuanNote：乾宫/坤位命中 + 无方位空串")
    void zuoDuanCases() throws Exception {
        Method t = m("zuoDuanNote", String.class);
        assertThat(s(t, null, "乾宫见吉兆")).as("乾宫").contains("坐端八方应兆").contains("老人");
        assertThat(s(t, null, "坤位有变")).as("坤位").contains("坐端八方应兆").contains("母亲");
        assertThat(s(t, null, "无方位")).as("无→空").isEmpty();
    }

    @Test
    @DisplayName("K2·论事十大应 shiDaYingNote：裹头/跣足/三坐命中 + 无兆空串")
    void shiDaYingCases() throws Exception {
        Method t = m("shiDaYingNote", String.class);
        assertThat(s(t, null, "七裹头之兆")).as("裹头").contains("论事十大应").contains("裹头");
        assertThat(s(t, null, "八跣足")).as("跣足").contains("跣足");
        assertThat(s(t, null, "三坐问事")).as("三坐").contains("三坐");
        assertThat(s(t, null, "毫无所应")).as("无→空").isEmpty();
    }

    @Test
    @DisplayName("K3·观物洞玄 dongXuanNote：宅气如春/冷落似秋/宿火不成 + 无兆空串")
    void dongXuanCases() throws Exception {
        Method t = m("dongXuanNote", String.class);
        assertThat(s(t, null, "宅气如春")).as("吉支").contains("观物洞玄歌·占宅气").contains("和顺");
        assertThat(s(t, null, "冷落似秋")).as("凶支").contains("渐衰");
        assertThat(s(t, null, "宿火不成")).as("宿火").contains("人散");
        assertThat(s(t, null, "毫无所应")).as("无→空").isEmpty();
    }

    @Test
    @DisplayName("K4·万物赋 wanWuNote：喧闹/鹊噪/禽鸣命中 + 无兆空串")
    void wanWuCases() throws Exception {
        Method t = m("wanWuNote", String.class);
        assertThat(s(t, null, "满城喧闹")).as("喧闹").contains("万物赋·闻见克应").contains("斗争");
        assertThat(s(t, null, "鹊噪连声")).as("鹊噪").contains("喜色");
        assertThat(s(t, null, "闻禽鸣于庭")).as("禽鸣").contains("虚说");
        assertThat(s(t, null, "毫无所应")).as("无→空").isEmpty();
    }

    @Test
    @DisplayName("K5·三要灵应 sanYaoLingYingNote：将叛者/将疑者/吉人/失其守者 + 无兆空串")
    void sanYaoCases() throws Exception {
        Method t = m("sanYaoLingYingNote", String.class);
        assertThat(s(t, null, "将叛者")).as("将叛者").contains("其辞惭").contains("三要灵应");
        assertThat(s(t, null, "将疑者")).as("将疑者").contains("其辞支");
        assertThat(s(t, null, "吉人")).as("吉人").contains("辞寡");
        assertThat(s(t, null, "失其守者")).as("失其守者").contains("其辞屈");
        assertThat(s(t, null, "毫无所应")).as("无→空").isEmpty();
    }

    // ==========================================================================
    // L. 字占取数（静态）
    // ==========================================================================

    @Test
    @DisplayName("L1·splitCharacters：空=1/1、十一字平分、一/二/三字按笔画")
    void splitCharactersBasic() throws Exception {
        Method t2 = m("splitCharacters", String.class, String.class);
        assertThat(arr(t2, null, "", null)).as("空→{1,1}").containsExactly(1, 1);
        assertThat(arr(t2, null, "abcdefghijk", null)).as("11字平分→{5,6}").containsExactly(5, 6);
        assertThat(arr(t2, null, "一", null)).as("一字{1,1}").containsExactly(1, 1);
        assertThat(arr(t2, null, "一二", null)).as("二字{1,2}").containsExactly(1, 2);
        assertThat(arr(t2, null, "一二三", null)).as("三字{1,2+3}").containsExactly(1, 5);
    }

    @Test
    @DisplayName("L2·splitCharacters：四~十字平仄/平水韵/多档字数")
    void splitCharactersTones() throws Exception {
        Method t2 = m("splitCharacters", String.class, String.class);
        assertThat(arr(t2, null, "天地人花", "1234")).as("前端平仄{1+2,3+4}").containsExactly(3, 7);
        assertThat(arr(t2, null, "天地人花", null)).as("平水韵 天1地3人1花1→{4,2}").containsExactly(4, 2);
        assertThat(arr(t2, null, "甲甲甲甲甲", "12341")).as("五字{1+2,3+4+1}").containsExactly(3, 8);
        assertThat(arr(t2, null, "甲甲甲甲甲甲", "123412")).as("六字{1+2+3,4+1+2}").containsExactly(6, 7);
        assertThat(arr(t2, null, "abcdefghij", "1111122222")).as("十字{5,10}").containsExactly(5, 10);
    }

    @Test
    @DisplayName("L3·charNumberSource：空/字数/Unihan笔画/前端平仄/内置平水韵/笔画（平仄缺）")
    void charNumberSourceCases() throws Exception {
        Method t = m("charNumberSource", String.class, String.class);
        assertThat(s(t, null, "", null)).as("空").isEqualTo("空");
        assertThat(s(t, null, "abcdefghijk", null)).as("十一字→字数").isEqualTo("字数");
        assertThat(s(t, null, "一二三", null)).as("三字→笔画（Unihan）").isEqualTo("笔画（Unihan）");
        assertThat(s(t, null, "天地人花", "1234")).as("有前端平仄").isEqualTo("前端平仄");
        assertThat(s(t, null, "天地人花", null)).as("皆在平水韵表").isEqualTo("内置平水韵");
        assertThat(s(t, null, "ABCD", null)).as("非汉字→平仄缺").isEqualTo("笔画（平仄缺）");
    }

    @Test
    @DisplayName("L4·splitClauses：按句读断句保序，空串/null 返回空列表")
    void splitClausesCases() throws Exception {
        Method t = m("splitClauses", String.class);
        List<String> c = call(t, null, "天时不如地利，地利不如人和。此事如何？");
        assertThat(c).as("三句").hasSize(3).startsWith("天时不如地利").endsWith("此事如何");
        assertThat((List<String>) call(t, null, "")).as("空→空列表").isEmpty();
        assertThat((List<String>) call(t, null, (Object) null)).as("null→空列表").isEmpty();
    }

    @Test
    @DisplayName("L5·inferHumanSub：语声/书写/取象三支 + 空兜底")
    void inferHumanSubCases() throws Exception {
        Method t = m("inferHumanSub", String.class);
        assertThat(s(t, null, "语声嘈杂")).as("语声").isEqualTo("听语声");
        assertThat(s(t, null, "他说好事")).as("他说").isEqualTo("听语声");
        assertThat(s(t, null, "书写来意")).as("书写").isEqualTo("书写来意");
        assertThat(s(t, null, "观其取象")).as("取象").isEqualTo("取象");
        assertThat(s(t, null, (Object) null)).as("null→取象").isEqualTo("取象");
        assertThat(s(t, null, "")).as("空→取象").isEqualTo("取象");
    }

    // ==========================================================================
    // M. 四不占告示 preceptAdmonition（实例方法）
    // ==========================================================================

    @Test
    @DisplayName("M1·preceptAdmonition：四戒律文案 + default 兜底")
    void preceptAdmonitionCases() throws Exception {
        Method t = m("preceptAdmonition", String.class);
        PlumBlossomOntologyJobWorker w = worker();
        assertThat(s(t, w, "无事不占")).as("无事").isEqualTo("无具体事项不可起卦。");
        assertThat(s(t, w, "重卦不占")).as("重卦").contains("反复求卦");
        assertThat(s(t, w, "不诚不占")).as("不诚").contains("诚心");
        assertThat(s(t, w, "不义不占")).as("不义").contains("道义");
        assertThat(s(t, w, "其他事")).as("default").contains("正心诚意");
        assertThat(s(t, w, (Object) null)).as("null→default").contains("正心诚意");
    }
}
