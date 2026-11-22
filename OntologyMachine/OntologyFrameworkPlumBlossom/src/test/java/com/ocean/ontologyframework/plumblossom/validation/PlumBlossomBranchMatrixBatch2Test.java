package com.ocean.ontologyframework.plumblossom.validation;

import com.ocean.ontologyframework.plumblossom.PlumBlossomOntologyJobWorker;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 梅花易数·分支矩阵（第二批）：十应分类与取象 / 天时占诸卦众寡 / 起卦法 encodeStartNumbers 多起例 /
 * 分类断语 categoryVerdict 吉凶与分项细则。
 *
 * <p>纯反射、离线（不加载本体：ontologyDomainReady=false、ontoLore=null 走内置兜底）。
 */
@DisplayName("梅花易数·分支矩阵·第二批（十应/天时/起卦法/分类断语·离线）")
public class PlumBlossomBranchMatrixBatch2Test {

    private static Method m(String name, Class<?>... params) throws Exception {
        Method mm = PlumBlossomOntologyJobWorker.class.getDeclaredMethod(name, params);
        mm.setAccessible(true);
        return mm;
    }

    @SuppressWarnings("unchecked")
    private static <T> T call(Method method, Object target, Object... args) throws Exception {
        return (T) method.invoke(target, args);
    }

    private static int i(Method method, Object target, Object... args) throws Exception {
        return (Integer) call(method, target, args);
    }

    private static Map<String, Object> vars(Object... kv) {
        Map<String, Object> v = new LinkedHashMap<>();
        for (int x = 0; x + 1 < kv.length; x += 2) {
            v.put((String) kv[x], kv[x + 1]);
        }
        return v;
    }

    // ==========================================================================
    // E. 十应分类与取象（纯函数）
    // ==========================================================================

    @Test
    @DisplayName("E1·十应类目 tenResponseCategory：十一类关键词映射 + 未入类/空兜底「外」")
    void tenResponseCategoryCases() throws Exception {
        Method t = m("tenResponseCategory", String.class);
        assertThat((String) call(t, null, "春寒")).as("时令").isEqualTo("时令");
        assertThat((String) call(t, null, "天降大雨")).as("天时").isEqualTo("天时");
        assertThat((String) call(t, null, "远山流水")).as("地理").isEqualTo("地理");
        assertThat((String) call(t, null, "一老人至")).as("人事").isEqualTo("人事");
        assertThat((String) call(t, null, "喜鹊登枝")).as("动物").isEqualTo("动物");
        assertThat((String) call(t, null, "瓶花在案")).as("静物").isEqualTo("静物");
        assertThat((String) call(t, null, "闻人言语")).as("言语").isEqualTo("言语");
        assertThat((String) call(t, null, "远处钟响")).as("声音").isEqualTo("声音");
        assertThat((String) call(t, null, "见赤光")).as("五色").isEqualTo("五色");
        assertThat((String) call(t, null, "见一题字")).as("写字").isEqualTo("写字");
        assertThat((String) call(t, null, "立于南")).as("方卦").isEqualTo("方卦");
        assertThat((String) call(t, null, "莫名其妙")).as("未入类→外").isEqualTo("外");
        assertThat((String) call(t, null, (Object) null)).as("null→外").isEqualTo("外");
        assertThat((String) call(t, null, "")).as("空→外").isEqualTo("外");
    }

    @Test
    @DisplayName("E2·动象判定 isMovingOmen：飞/走/鸣/落为动，静坐为静，空为静")
    void isMovingOmenCases() throws Exception {
        Method t = m("isMovingOmen", String.class);
        assertThat((boolean) call(t, null, "鸟飞去")).as("飞→动").isTrue();
        assertThat((boolean) call(t, null, "人走过")).as("走→动").isTrue();
        assertThat((boolean) call(t, null, "马鸣")).as("鸣→动").isTrue();
        assertThat((boolean) call(t, null, "叶落")).as("落→动").isTrue();
        assertThat((boolean) call(t, null, "静坐一室")).as("无动词→静").isFalse();
        assertThat((boolean) call(t, null, "")).as("空→静").isFalse();
    }

    @Test
    @DisplayName("E3·静物直断 stillObjectDirectVerdict：物圆事成/器缺事败/圆缺参半/无征空串")
    void stillObjectDirectCases() throws Exception {
        Method t = m("stillObjectDirectVerdict", String.class);
        assertThat((String) call(t, null, "见圆镜")).as("圆→成").contains("事成");
        assertThat((String) call(t, null, "见破损之碗")).as("缺→败").contains("事败");
        assertThat((String) call(t, null, "圆而缺")).as("圆缺→参半").contains("成败参半");
        assertThat((String) call(t, null, "无形之兆")).as("无征→空").isEmpty();
        assertThat((String) call(t, null, (Object) null)).as("null→空").isEmpty();
    }

    @Test
    @DisplayName("E4·五色→五行 fivePhaseOfColor：青碧绿木/红紫赤火/白金/黑水/黄(及兜底)土")
    void fivePhaseOfColorCases() throws Exception {
        Method t = m("fivePhaseOfColor", String.class);
        assertThat((String) call(t, null, "青碧")).isEqualTo("木");
        assertThat((String) call(t, null, "赤红")).isEqualTo("火");
        assertThat((String) call(t, null, "素白")).isEqualTo("金");
        assertThat((String) call(t, null, "乌黑")).isEqualTo("水");
        assertThat((String) call(t, null, "杏黄")).isEqualTo("土");
        assertThat((String) call(t, null, "无色可辨")).as("无彩→兜底土").isEqualTo("土");
    }

    @Test
    @DisplayName("E5·听声辨物声 soundToTrigram：语兑2/扣击震4/金声乾1/火声离3/土声坤8/未知0")
    void soundToTrigramCases() throws Exception {
        Method t = m("soundToTrigram", String.class);
        assertThat(i(t, null, "人语声")).as("口出声→兑2").isEqualTo(2);
        assertThat(i(t, null, "敲门")).as("扣击→震4").isEqualTo(4);
        assertThat(i(t, null, "闻钟磬")).as("金声→乾1").isEqualTo(1);
        assertThat(i(t, null, "火爆")).as("火声→离3").isEqualTo(3);
        assertThat(i(t, null, "土崩")).as("土声→坤8").isEqualTo(8);
        assertThat(i(t, null, "无声")).as("未知→0").isEqualTo(0);
        assertThat(i(t, null, (Object) null)).as("null→0").isEqualTo(0);
    }

    @Test
    @DisplayName("E6·外应取象卦 omenToTrigram=xiangToNumber：马乾1/羊兑2/火离3/雷震4/风巽5/水坎6/山艮7/地坤8")
    void omenToTrigramCases() throws Exception {
        Method t = m("omenToTrigram", String.class);
        assertThat(i(t, null, "马")).isEqualTo(1);
        assertThat(i(t, null, "羊")).isEqualTo(2);
        assertThat(i(t, null, "火")).isEqualTo(3);
        assertThat(i(t, null, "雷")).isEqualTo(4);
        assertThat(i(t, null, "风")).isEqualTo(5);
        assertThat(i(t, null, "水")).isEqualTo(6);
        assertThat(i(t, null, "山")).isEqualTo(7);
        assertThat(i(t, null, "地")).isEqualTo(8);
        assertThat(i(t, null, "")).as("空→乾1").isEqualTo(1);
    }

    @Test
    @DisplayName("E7·形物/验色取卦 shapeToTrigram、colorToTrigram 全表覆盖")
    void shapeAndColorCases() throws Exception {
        Method s = m("shapeToTrigram", String.class);
        assertThat(i(s, null, "圆")).isEqualTo(1);
        assertThat(i(s, null, "刚")).isEqualTo(2);
        assertThat(i(s, null, "方")).isEqualTo(8);
        assertThat(i(s, null, "柔")).isEqualTo(5);
        assertThat(i(s, null, "仰")).isEqualTo(4);
        assertThat(i(s, null, "覆")).isEqualTo(7);
        assertThat(i(s, null, "长")).isEqualTo(5);
        assertThat(i(s, null, "中刚外柔")).isEqualTo(6);
        assertThat(i(s, null, "文彩")).isEqualTo(3);
        assertThat(i(s, null, "破")).isEqualTo(2);
        assertThat(i(s, null, "奇形")).as("未知→1").isEqualTo(1);

        Method c = m("colorToTrigram", String.class);
        assertThat(i(c, null, "青")).isEqualTo(4);
        assertThat(i(c, null, "红紫赤")).isEqualTo(3);
        assertThat(i(c, null, "黄")).isEqualTo(8);
        assertThat(i(c, null, "白")).isEqualTo(2);
        assertThat(i(c, null, "黑")).isEqualTo(6);
        assertThat(i(c, null, "灰")).as("未知→1").isEqualTo(1);
    }

    @Test
    @DisplayName("E8·向背 xiangBeiNote：来/至为向、去/过为背、无征空串")
    void xiangBeiCases() throws Exception {
        Method t = m("xiangBeiNote", String.class);
        assertThat((String) call(t, null, "鸦飞来")).as("来→向").contains("相向而来");
        assertThat((String) call(t, null, "鸦飞去")).as("去→背").contains("相背而去");
        assertThat((String) call(t, null, "静默")).as("无征→空").isEmpty();
        assertThat((String) call(t, null, (Object) null)).as("null→空").isEmpty();
    }

    @Test
    @DisplayName("E9·方位→先天卦数 dirToTrigram/dirFromTextOrDefault（含默认兜底）")
    void directionTrigramCases() throws Exception {
        PlumBlossomOntologyJobWorker w = new PlumBlossomOntologyJobWorker();
        Method t = m("dirToTrigram", String.class);
        assertThat(i(t, w, "东南")).isEqualTo(5);
        assertThat(i(t, w, "西南")).isEqualTo(8);
        assertThat(i(t, w, "东北")).isEqualTo(7);
        assertThat(i(t, w, "西北")).isEqualTo(1);
        assertThat(i(t, w, "东")).isEqualTo(4);
        assertThat(i(t, w, "南")).isEqualTo(3);
        assertThat(i(t, w, "西")).isEqualTo(2);
        assertThat(i(t, w, "北")).isEqualTo(6);
        assertThat(i(t, w, "无")).as("无方位→兜底1").isEqualTo(1);

        Method f = m("dirFromTextOrDefault", String.class, int.class);
        assertThat(i(f, w, "向东走", 9)).as("含方位→取卦").isEqualTo(4);
        assertThat(i(f, w, "无所指", 9)).as("无方位→回退").isEqualTo(9);
        assertThat(i(f, w, (Object) null, 9)).as("null→回退").isEqualTo(9);
    }

    // ==========================================================================
    // F. 天时占 tianShiVerdict：数本/互/变六卦八卦众寡
    // ==========================================================================

    @Test
    @DisplayName("F1·天时占：卦象未备时返回提示")
    void tianShiEmpty() throws Exception {
        Method t = m("tianShiVerdict", String.class, String.class, String.class);
        String v = call(t, null, "", "", "");
        assertThat(v).contains("卦象未备");
    }

    @Test
    @DisplayName("F2·天时占：坎多主雨 / 离多主晴 / 坎离均主时晴时雨")
    void tianShiRainClear() throws Exception {
        Method t = m("tianShiVerdict", String.class, String.class, String.class);
        assertThat((String) call(t, null, "坎为水", "坎为水", "")).as("坎4离0→主雨").contains("主雨");
        assertThat((String) call(t, null, "离为火", "离为火", "")).as("离4坎0→主晴").contains("主晴");
        assertThat((String) call(t, null, "坎为水", "离为火", "")).as("坎2离2→时晴时雨").contains("时晴时雨");
    }

    @Test
    @DisplayName("F3·天时占：乾主晴明 / 坤主阴晦 / 不见坎离晴雨未定")
    void tianShiQianKun() throws Exception {
        Method t = m("tianShiVerdict", String.class, String.class, String.class);
        String qian = call(t, null, "乾为天", "", "");
        assertThat(qian).contains("乾（2）主晴明").contains("不见坎离");
        String kun = call(t, null, "坤为地", "", "");
        assertThat(kun).contains("坤（2）主阴晦");
    }

    // ==========================================================================
    // G. 起卦法 encodeStartNumbers 多起例（16 法之代表）
    // ==========================================================================

    @Test
    @DisplayName("G1·年月日时起例：年支+月+日为上卦，加时数为下卦与动爻")
    void encodeYearMonthDayHour() throws Exception {
        PlumBlossomOntologyJobWorker w = new PlumBlossomOntologyJobWorker();
        Method e = m("encodeStartNumbers", String.class, Map.class);
        Map<String, Object> v = vars("lunarYear", 2024, "lunarMonth", 1, "lunarDay", 1, "hourZhi", "子");
        // 年支辰=5，total=5+1+1=7；up=7、low=7+1=8、moving=8
        assertThat((String) call(e, w, "年月日时起例", v)).isEqualTo("7|8|8");
    }

    @Test
    @DisplayName("G2·物占数：物体数为上卦、时数为下卦、和取动")
    void encodeObjectCount() throws Exception {
        PlumBlossomOntologyJobWorker w = new PlumBlossomOntologyJobWorker();
        Method e = m("encodeStartNumbers", String.class, Map.class);
        Map<String, Object> v = vars("objectCount", 3, "hourZhi", "子");
        assertThat((String) call(e, w, "物占数", v)).isEqualTo("3|1|4");
    }

    @Test
    @DisplayName("G3·声音占：单声（声数为上、时数为下）/ 双声（两声分上下，加时取动）")
    void encodeSound() throws Exception {
        PlumBlossomOntologyJobWorker w = new PlumBlossomOntologyJobWorker();
        Method e = m("encodeStartNumbers", String.class, Map.class);
        Map<String, Object> single = vars("soundCount", 1, "hourZhi", "酉"); // 酉=10
        assertThat((String) call(e, w, "声音占", single)).as("up1 low10 moving11").isEqualTo("1|10|11");
        Map<String, Object> dbl = vars("soundCount", 1, "soundCount2", 5, "hourZhi", "酉");
        assertThat((String) call(e, w, "声音占", dbl)).as("up1 low5 moving16").isEqualTo("1|5|16");
    }

    @Test
    @DisplayName("G4·丈尺占：丈为上、尺为下、合数取动")
    void encodeZhangChi() throws Exception {
        PlumBlossomOntologyJobWorker w = new PlumBlossomOntologyJobWorker();
        Method e = m("encodeStartNumbers", String.class, Map.class);
        Map<String, Object> v = vars("zhang", 5, "chi", 6);
        assertThat((String) call(e, w, "丈尺占", v)).isEqualTo("5|6|11");
    }

    @Test
    @DisplayName("G5·尺寸物占：尺为上、寸为下，加时取动")
    void encodeChiCun() throws Exception {
        PlumBlossomOntologyJobWorker w = new PlumBlossomOntologyJobWorker();
        Method e = m("encodeStartNumbers", String.class, Map.class);
        Map<String, Object> v = vars("chi2", 3, "cun", 4, "hourZhi", "子");
        assertThat((String) call(e, w, "尺寸物占", v)).isEqualTo("3|4|8");
    }

    @Test
    @DisplayName("G6·风觉占：风（巽=5）为上卦、来向之卦为下卦，加时取动")
    void encodeWind() throws Exception {
        PlumBlossomOntologyJobWorker w = new PlumBlossomOntologyJobWorker();
        Method e = m("encodeStartNumbers", String.class, Map.class);
        Map<String, Object> south = vars("windDirection", "南", "hourZhi", "子"); // 南=3
        assertThat((String) call(e, w, "风觉占", south)).as("5|3|9").isEqualTo("5|3|9");
        Map<String, Object> east = vars("windDirection", "东", "hourZhi", "子"); // 东=4
        assertThat((String) call(e, w, "风觉占", east)).as("5|4|10").isEqualTo("5|4|10");
    }

    @Test
    @DisplayName("G7·形物占：物形取卦为上、时数为下")
    void encodeShape() throws Exception {
        PlumBlossomOntologyJobWorker w = new PlumBlossomOntologyJobWorker();
        Method e = m("encodeStartNumbers", String.class, Map.class);
        Map<String, Object> v = vars("objectShape", "方", "hourZhi", "子"); // 方=8
        assertThat((String) call(e, w, "形物占", v)).isEqualTo("8|1|9");
    }

    @Test
    @DisplayName("G8·验色占：物色取卦为上、时数为下")
    void encodeColor() throws Exception {
        PlumBlossomOntologyJobWorker w = new PlumBlossomOntologyJobWorker();
        Method e = m("encodeStartNumbers", String.class, Map.class);
        Map<String, Object> v = vars("objectColor", "青", "hourZhi", "子"); // 青=4
        assertThat((String) call(e, w, "验色占", v)).isEqualTo("4|1|5");
    }

    @Test
    @DisplayName("G9·一撮金法：上字内卦为下、下字外卦为上，总画六除取动（与 stroke 自洽）")
    void encodeYiCuoJin() throws Exception {
        PlumBlossomOntologyJobWorker w = new PlumBlossomOntologyJobWorker();
        Method e = m("encodeStartNumbers", String.class, Map.class);
        Method st = m("stroke", char.class);
        int nei = i(st, null, '乾'); // 上字→内卦
        int wai = i(st, null, '坤'); // 下字→外卦
        Map<String, Object> v = vars("yicuoText", "乾坤");
        assertThat((String) call(e, w, "一撮金法", v)).as("up=外 外卦、low=内 内卦").isEqualTo(wai + "|" + nei + "|" + (nei + wai));
    }

    @Test
    @DisplayName("G10·未知起卦法走默认：上下皆时数、动取两倍")
    void encodeDefault() throws Exception {
        PlumBlossomOntologyJobWorker w = new PlumBlossomOntologyJobWorker();
        Method e = m("encodeStartNumbers", String.class, Map.class);
        Map<String, Object> v = vars("hourZhi", "子");
        assertThat((String) call(e, w, "某未知法", v)).isEqualTo("1|1|2");
    }

    @Test
    @DisplayName("G11·占静物：静物取象为上、时数为下")
    void encodeStillObject() throws Exception {
        PlumBlossomOntologyJobWorker w = new PlumBlossomOntologyJobWorker();
        Method e = m("encodeStartNumbers", String.class, Map.class);
        Map<String, Object> v = vars("stillInfo", "马", "hourZhi", "子"); // 马=1
        assertThat((String) call(e, w, "占静物", v)).isEqualTo("1|1|2");
    }

    @Test
    @DisplayName("G12·占动物：动物取象为上、文中方位为下、加时取动")
    void encodeAnimal() throws Exception {
        PlumBlossomOntologyJobWorker w = new PlumBlossomOntologyJobWorker();
        Method e = m("encodeStartNumbers", String.class, Map.class);
        Map<String, Object> v = vars("animalInfo", "马在东", "hourZhi", "子"); // 马=1 东=4
        assertThat((String) call(e, w, "占动物", v)).isEqualTo("1|4|6");
    }

    @Test
    @DisplayName("G13·为人占·取象子法：取象为上、时数为下")
    void encodeHumanByImage() throws Exception {
        PlumBlossomOntologyJobWorker w = new PlumBlossomOntologyJobWorker();
        Method e = m("encodeStartNumbers", String.class, Map.class);
        Map<String, Object> v = vars("humanInfo", "马", "humanSub", "观人品", "hourZhi", "子"); // 马=1
        assertThat((String) call(e, w, "为人占", v)).isEqualTo("1|1|2");
    }

    // ==========================================================================
    // H. 分类断语 categoryVerdict：吉断/凶断 + 分项细则
    // ==========================================================================

    @Test
    @DisplayName("H1·分类吉断：求财·用生体(level1) → 取吉断「财可得」")
    void categoryGood() throws Exception {
        PlumBlossomOntologyJobWorker w = new PlumBlossomOntologyJobWorker();
        Method c = m("categoryVerdict", String.class, String.class, String.class,
                String.class, String.class, String.class, String.class, int.class);
        String v = call(c, w, "求财", "乾为天", "1·用生体", "乾", "坤", "", "", 0);
        assertThat(v).contains("依「求财」分项断法").contains("用生体·大吉").contains("财可得");
    }

    @Test
    @DisplayName("H2·分类凶断：求财·用克体(level5) → 取凶断「损财」")
    void categoryBad() throws Exception {
        PlumBlossomOntologyJobWorker w = new PlumBlossomOntologyJobWorker();
        Method c = m("categoryVerdict", String.class, String.class, String.class,
                String.class, String.class, String.class, String.class, int.class);
        String v = call(c, w, "求财", "乾为天", "5·用克体", "乾", "坤", "", "", 0);
        assertThat(v).contains("用克体·大凶").contains("损财");
    }

    @Test
    @DisplayName("H3·婚姻分项：用卦性情容貌（乾→端正而长）")
    void categoryMarriage() throws Exception {
        PlumBlossomOntologyJobWorker w = new PlumBlossomOntologyJobWorker();
        Method c = m("categoryVerdict", String.class, String.class, String.class,
                String.class, String.class, String.class, String.class, int.class);
        String v = call(c, w, "婚姻", "乾为天", "2·体用比和", "乾", "乾", "", "", 0);
        assertThat(v).contains("〔论婚·对方（用卦乾）").contains("端正而长");
    }

    @Test
    @DisplayName("H4·生产分项：辨男女（乾为天阳多主生男 / 坤为地阴多主生女）")
    void categoryBirth() throws Exception {
        PlumBlossomOntologyJobWorker w = new PlumBlossomOntologyJobWorker();
        Method c = m("categoryVerdict", String.class, String.class, String.class,
                String.class, String.class, String.class, String.class, int.class);
        String male = call(c, w, "生产", "乾为天", "1·用生体", "乾", "坤", "", "", 0);
        assertThat(male).contains("辨男女").contains("主生男");
        String female = call(c, w, "生产", "坤为地", "1·用生体", "乾", "坤", "", "", 0);
        assertThat(female).contains("主生女");
    }

    @Test
    @DisplayName("H5·疾病分项：克体之卦测鬼神（坤土克坎水→坤卦克体西南之神）")
    void categoryDisease() throws Exception {
        PlumBlossomOntologyJobWorker w = new PlumBlossomOntologyJobWorker();
        Method c = m("categoryVerdict", String.class, String.class, String.class,
                String.class, String.class, String.class, String.class, int.class);
        String v = call(c, w, "疾病", "天地否", "4·体生用", "坎", "坤", "", "", 0);
        assertThat(v).contains("测鬼神").contains("坤卦克体");
    }

    @Test
    @DisplayName("H6·未录类别走兜底断语「以体为主、用为所应，参互变综合断之」")
    void categoryDefault() throws Exception {
        PlumBlossomOntologyJobWorker w = new PlumBlossomOntologyJobWorker();
        Method c = m("categoryVerdict", String.class, String.class, String.class,
                String.class, String.class, String.class, String.class, int.class);
        String v = call(c, w, "未录之事", "乾为天", "1·用生体", "乾", "坤", "", "", 0);
        assertThat(v).contains("以体为主、用为所应，参互变综合断之");
    }
}
