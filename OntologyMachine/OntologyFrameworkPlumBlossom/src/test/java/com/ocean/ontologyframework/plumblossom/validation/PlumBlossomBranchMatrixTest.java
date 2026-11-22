package com.ocean.ontologyframework.plumblossom.validation;

import com.ocean.ontologyframework.plumblossom.PlumBlossomOntologyJobWorker;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 梅花易数·核心算理分支矩阵（纯反射，离线；不依赖 MySQL/Ontop/Camunda/Spring）。
 *
 * <p>补 {@link PlumBlossomCastRuleTest}（偏重断语文本与起卦取数）未系统覆盖的「算理纯函数」：
 * 起卦模数（卦以八除/爻以六除）、八卦爻位与重卦合成、五行生克、卦气旺衰、体用生克五级、
 * 吉凶评分回映射、互变对体生克、应期分档、趋避（破解）三法、五行→方位/色/时/物与生克反查。
 */
@DisplayName("梅花易数·核心算理分支矩阵（离线）")
public class PlumBlossomBranchMatrixTest {

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

    // ==========================================================================
    // A. 起卦数与卦象工具（mod8 / mod6 / bits / trigramOfBits / 重卦合成 / 动爻反转）
    // ==========================================================================

    @Test
    @DisplayName("A1·卦以八除 mod8：n→((n-1)%8+8)%8+1，边界含 0 与负数")
    void mod8Boundary() throws Exception {
        Method mm = m("mod8", int.class);
        assertThat(i(mm, null, 1)).isEqualTo(1);
        assertThat(i(mm, null, 8)).as("8 仍为 8（坤）").isEqualTo(8);
        assertThat(i(mm, null, 9)).as("9→1（乾）").isEqualTo(1);
        assertThat(i(mm, null, 16)).as("16→8").isEqualTo(8);
        assertThat(i(mm, null, 0)).as("0→8（防御，坤）").isEqualTo(8);
        assertThat(i(mm, null, -1)).as("-1→7（艮，负数安全）").isEqualTo(7);
        assertThat(i(mm, null, 17)).as("17→1").isEqualTo(1);
    }

    @Test
    @DisplayName("A2·爻以六除 mod6：n→((n-1)%6+6)%6+1，边界含 0 与负数")
    void mod6Boundary() throws Exception {
        Method mm = m("mod6", int.class);
        assertThat(i(mm, null, 1)).isEqualTo(1);
        assertThat(i(mm, null, 6)).isEqualTo(6);
        assertThat(i(mm, null, 7)).as("7→1").isEqualTo(1);
        assertThat(i(mm, null, 12)).as("12→6").isEqualTo(6);
        assertThat(i(mm, null, 0)).as("0→6（防御）").isEqualTo(6);
        assertThat(i(mm, null, -1)).as("-1→5").isEqualTo(5);
    }

    @Test
    @DisplayName("A3·八卦爻位 bits：乾111 兑110 离101 震100 巽011 坎010 艮001 坤000")
    void bitsMapping() throws Exception {
        Method bm = m("bits", int.class);
        assertThat((int[]) call(bm, null, 1)).as("乾").containsExactly(1, 1, 1);
        assertThat((int[]) call(bm, null, 2)).as("兑").containsExactly(1, 1, 0);
        assertThat((int[]) call(bm, null, 3)).as("离").containsExactly(1, 0, 1);
        assertThat((int[]) call(bm, null, 4)).as("震").containsExactly(1, 0, 0);
        assertThat((int[]) call(bm, null, 5)).as("巽").containsExactly(0, 1, 1);
        assertThat((int[]) call(bm, null, 6)).as("坎").containsExactly(0, 1, 0);
        assertThat((int[]) call(bm, null, 7)).as("艮").containsExactly(0, 0, 1);
        assertThat((int[]) call(bm, null, 8)).as("坤").containsExactly(0, 0, 0);
    }

    @Test
    @DisplayName("A4·三爻合卦 trigramOfBits 与 bits 互逆（8/8 往返）")
    void trigramOfBitsRoundTrip() throws Exception {
        Method t = m("trigramOfBits", int.class, int.class, int.class);
        Method b = m("bits", int.class);
        for (int n = 1; n <= 8; n++) {
            int[] bits = call(b, null, n);
            assertThat(i(t, null, bits[0], bits[1], bits[2])).as("卦数 " + n + " 往返").isEqualTo(n);
        }
    }

    @Test
    @DisplayName("A5·本卦重建与重卦合成：乾坤往返 + 六爻合成还原卦名")
    void composeAndReconstruct() throws Exception {
        Method rc = m("reconstructLines", String.class);
        Method cp = m("composeHexagram", int[].class);
        assertThat((int[]) call(rc, null, "乾为天")).as("乾六爻皆阳").containsExactly(1, 1, 1, 1, 1, 1);
        assertThat((int[]) call(rc, null, "坤为地")).as("坤六爻皆阴").containsExactly(0, 0, 0, 0, 0, 0);
        assertThat((String) call(cp, null, (Object) new int[]{1, 1, 1, 1, 1, 1})).isEqualTo("乾为天");
        assertThat((String) call(cp, null, (Object) new int[]{0, 0, 0, 0, 0, 0})).isEqualTo("坤为地");
        // 上乾下巽：天风姤
        assertThat((String) call(cp, null, (Object) new int[]{0, 1, 1, 1, 1, 1})).isEqualTo("天风姤");
    }

    @Test
    @DisplayName("A6·动爻反转 flip：乾为天初爻动→天风姤、上爻动→泽天夬，且越界钳制到 1/6")
    void flipMovingLine() throws Exception {
        Method rc = m("reconstructLines", String.class);
        Method flip = m("flip", int[].class, int.class);
        Method cp = m("composeHexagram", int[].class);
        int[] qian = call(rc, null, "乾为天");
        assertThat((String) call(cp, null, (Object) call(flip, null, (Object) qian, 1)))
                .as("初爻动→下巽上乾=天风姤").isEqualTo("天风姤");
        assertThat((String) call(cp, null, (Object) call(flip, null, (Object) qian, 6)))
                .as("上爻动→下乾上兑=泽天夬").isEqualTo("泽天夬");
        assertThat((String) call(cp, null, (Object) call(flip, null, (Object) qian, 0)))
                .as("0 钳制为初爻").isEqualTo("天风姤");
        assertThat((String) call(cp, null, (Object) call(flip, null, (Object) qian, 9)))
                .as("9 钳制为上爻").isEqualTo("泽天夬");
    }

    @Test
    @DisplayName("A7·卦名→上下卦 pairOf：已知卦精确、未知卦兜底 {1,1}")
    void pairOfLookup() throws Exception {
        Method p = m("pairOf", String.class);
        assertThat((int[]) call(p, null, "乾为天")).containsExactly(1, 1);
        assertThat((int[]) call(p, null, "坤为地")).containsExactly(8, 8);
        assertThat((int[]) call(p, null, "天风姤")).as("上乾下巽").containsExactly(1, 5);
        assertThat((int[]) call(p, null, "火水未济")).as("上离下坎").containsExactly(3, 6);
        assertThat((int[]) call(p, null, "未知卦名")).as("兜底").containsExactly(1, 1);
    }

    @Test
    @DisplayName("A8·起卦原始数解析 decodeStartNumbers：正常/空白/缺字段/非法/空串")
    void decodeStartNumbers() throws Exception {
        Method d = m("decodeStartNumbers", String.class);
        assertThat((int[]) call(d, null, "3|4|5")).containsExactly(3, 4, 5);
        assertThat((int[]) call(d, null, " 3 | 4 | 5 ")).as("trim").containsExactly(3, 4, 5);
        assertThat((int[]) call(d, null, "1|2")).as("缺动爻→0").containsExactly(1, 2, 0);
        assertThat((int[]) call(d, null, "bad")).as("无分隔符→全 0").containsExactly(0, 0, 0);
        assertThat((int[]) call(d, null, (Object) null)).as("null→全 0").containsExactly(0, 0, 0);
        assertThat((int[]) call(d, null, "x|2|3")).as("首字段非数→up 保 0、其余未解析").containsExactly(0, 0, 0);
    }

    @Test
    @DisplayName("A9·八卦名→先天数 triNum 与未知兜底")
    void triNumLookup() throws Exception {
        Method t = m("triNum", String.class);
        assertThat(i(t, null, "乾")).isEqualTo(1);
        assertThat(i(t, null, "兑")).isEqualTo(2);
        assertThat(i(t, null, "坤")).isEqualTo(8);
        assertThat(i(t, null, "未知")).as("兜底 1").isEqualTo(1);
        assertThat(i(t, null, (Object) null)).as("null 兜底 1").isEqualTo(1);
    }

    // ==========================================================================
    // B. 五行生克、当季五行与诸象数映射
    // ==========================================================================

    @Test
    @DisplayName("B1·五行相生 generates：五正边为真、逆/异为非")
    void generatesRelations() throws Exception {
        Method g = m("generates", String.class, String.class);
        assertThat((boolean) call(g, null, "金", "水")).isTrue();
        assertThat((boolean) call(g, null, "水", "木")).isTrue();
        assertThat((boolean) call(g, null, "木", "火")).isTrue();
        assertThat((boolean) call(g, null, "火", "土")).isTrue();
        assertThat((boolean) call(g, null, "土", "金")).isTrue();
        assertThat((boolean) call(g, null, "金", "木")).as("相克非生").isFalse();
        assertThat((boolean) call(g, null, "水", "金")).as("逆生为假").isFalse();
        assertThat((boolean) call(g, null, "金", "金")).as("同气非生").isFalse();
        assertThat((boolean) call(g, null, "无关", "水")).as("未知为假").isFalse();
    }

    @Test
    @DisplayName("B2·五行相克 overcomes：五正边为真、生边为非")
    void overcomesRelations() throws Exception {
        Method o = m("overcomes", String.class, String.class);
        assertThat((boolean) call(o, null, "金", "木")).isTrue();
        assertThat((boolean) call(o, null, "木", "土")).isTrue();
        assertThat((boolean) call(o, null, "土", "水")).isTrue();
        assertThat((boolean) call(o, null, "水", "火")).isTrue();
        assertThat((boolean) call(o, null, "火", "金")).isTrue();
        assertThat((boolean) call(o, null, "金", "水")).as("相生非克").isFalse();
        assertThat((boolean) call(o, null, "木", "金")).as("被克非克").isFalse();
    }

    @Test
    @DisplayName("B3·当季五行 seasonFive：春木/夏火/秋金/冬水/四季土，未知兜底土")
    void seasonFiveMapping() throws Exception {
        Method s = m("seasonFive", String.class);
        assertThat((String) call(s, null, "春")).isEqualTo("木");
        assertThat((String) call(s, null, "夏")).isEqualTo("火");
        assertThat((String) call(s, null, "秋")).isEqualTo("金");
        assertThat((String) call(s, null, "冬")).isEqualTo("水");
        assertThat((String) call(s, null, "四季")).isEqualTo("土");
        assertThat((String) call(s, null, "未知")).as("兜底土").isEqualTo("土");
    }

    @Test
    @DisplayName("B4·卦气旺衰 vitalityOf 五支：旺(当令)/相/休/囚/死（以春季木为例）")
    void vitalityFiveBranches() throws Exception {
        Method v = m("vitalityOf", String.class, String.class);
        assertThat((String) call(v, null, "木", "春")).as("当令→旺").isEqualTo("旺");
        assertThat((String) call(v, null, "火", "春")).as("当令所生→相").isEqualTo("相");
        assertThat((String) call(v, null, "水", "春")).as("生当令→休").isEqualTo("休");
        assertThat((String) call(v, null, "金", "春")).as("克当令→囚").isEqualTo("囚");
        assertThat((String) call(v, null, "土", "春")).as("当令所克→死").isEqualTo("死");
        // 换季验证：夏季火旺
        assertThat((String) call(v, null, "火", "夏")).isEqualTo("旺");
        assertThat((String) call(v, null, "土", "夏")).as("夏土相").isEqualTo("相");
    }

    @Test
    @DisplayName("B5·农历月→季 seasonFromLunarMonth：寅卯春/巳午夏/申酉秋/亥子冬/辰戌丑未四季")
    void seasonFromLunarMonth() throws Exception {
        Method s = m("seasonFromLunarMonth", int.class);
        assertThat((String) call(s, null, 1)).isEqualTo("春");
        assertThat((String) call(s, null, 2)).isEqualTo("春");
        assertThat((String) call(s, null, 4)).isEqualTo("夏");
        assertThat((String) call(s, null, 5)).isEqualTo("夏");
        assertThat((String) call(s, null, 7)).isEqualTo("秋");
        assertThat((String) call(s, null, 8)).isEqualTo("秋");
        assertThat((String) call(s, null, 10)).isEqualTo("冬");
        assertThat((String) call(s, null, 11)).isEqualTo("冬");
        assertThat((String) call(s, null, 3)).as("辰月→四季").isEqualTo("四季");
        assertThat((String) call(s, null, 6)).as("未月→四季").isEqualTo("四季");
        assertThat((String) call(s, null, 12)).as("丑月→四季").isEqualTo("四季");
        assertThat((String) call(s, null, 0)).as("越界→四季").isEqualTo("四季");
        assertThat((String) call(s, null, 13)).as("越界→四季").isEqualTo("四季");
    }

    @Test
    @DisplayName("B6·时辰地支→时数 hourNum：子1…亥12，空/未知兜底1")
    void hourNumLookup() throws Exception {
        Method h = m("hourNum", String.class);
        assertThat(i(h, null, "子")).isEqualTo(1);
        assertThat(i(h, null, "丑")).isEqualTo(2);
        assertThat(i(h, null, "午")).isEqualTo(7);
        assertThat(i(h, null, "亥")).isEqualTo(12);
        assertThat(i(h, null, (Object) null)).as("空兜底1").isEqualTo(1);
        assertThat(i(h, null, "未知")).as("未知兜底1").isEqualTo(1);
    }

    @Test
    @DisplayName("B7·农历年→年支数 yearBranchNum：year-4 mod12 +1，非正兜底1")
    void yearBranchNumLookup() throws Exception {
        Method y = m("yearBranchNum", int.class);
        assertThat(i(y, null, 2024)).as("2024 甲辰→辰(5)").isEqualTo(5);
        assertThat(i(y, null, 2020)).as("2020 庚子→子(1)").isEqualTo(1);
        assertThat(i(y, null, 2025)).as("2025 乙巳→巳(6)").isEqualTo(6);
        assertThat(i(y, null, 0)).as("非正兜底1").isEqualTo(1);
        assertThat(i(y, null, -5)).as("负值兜底1").isEqualTo(1);
    }

    @Test
    @DisplayName("B8·八卦五行 fivePhaseOf：乾兑金/离火/震巽木/坎水/艮坤土")
    void fivePhaseOfTrigrams() throws Exception {
        PlumBlossomOntologyJobWorker w = new PlumBlossomOntologyJobWorker();
        Method f = m("fivePhaseOf", String.class);
        assertThat((String) call(f, w, "乾")).isEqualTo("金");
        assertThat((String) call(f, w, "兑")).isEqualTo("金");
        assertThat((String) call(f, w, "离")).isEqualTo("火");
        assertThat((String) call(f, w, "震")).isEqualTo("木");
        assertThat((String) call(f, w, "巽")).isEqualTo("木");
        assertThat((String) call(f, w, "坎")).isEqualTo("水");
        assertThat((String) call(f, w, "艮")).isEqualTo("土");
        assertThat((String) call(f, w, "坤")).isEqualTo("土");
    }

    @Test
    @DisplayName("B9·五行→方位/色/时令/器物 与四端串 remedyLine（本体缺失走内置）")
    void fiveAttributeMappings() throws Exception {
        Method dir = m("fiveDirOf", String.class);
        Method color = m("fiveColorOf", String.class);
        Method season = m("fiveSeasonOf", String.class);
        Method obj = m("fiveObjectOf", String.class);
        Method line = m("remedyLine", String.class);
        assertThat((String) call(dir, null, "木")).isEqualTo("东方");
        assertThat((String) call(dir, null, "水")).isEqualTo("北方");
        assertThat((String) call(color, null, "火")).isEqualTo("红、紫、赤");
        assertThat((String) call(color, null, "金")).isEqualTo("白");
        assertThat((String) call(season, null, "金")).isEqualTo("秋（申酉月）");
        assertThat((String) call(obj, null, "水")).isEqualTo("泉井鱼盐之属");
        String rl = call(line, null, "木");
        assertThat(rl).contains("方位「东方」").contains("色「青、碧、绿」").contains("物「竹木花草之属」");
        assertThat((String) call(line, null, (Object) null)).as("null→空串").isEmpty();
    }

    @Test
    @DisplayName("B10·生克反查 generatorOf/overcomerOf/mediatorBetween（扶体·抑用·通关取材）")
    void generatorOvercomerMediator() throws Exception {
        Method gen = m("generatorOf", String.class);
        Method ovr = m("overcomerOf", String.class);
        Method med = m("mediatorBetween", String.class, String.class);
        assertThat((String) call(gen, null, "水")).as("生水者金").isEqualTo("金");
        assertThat((String) call(gen, null, "火")).as("生火者木").isEqualTo("木");
        assertThat((String) call(ovr, null, "木")).as("克木者金").isEqualTo("金");
        assertThat((String) call(ovr, null, "水")).as("克水者土").isEqualTo("土");
        assertThat((String) call(med, null, "水", "火")).as("水克火，通关木").isEqualTo("木");
        assertThat((String) call(med, null, "金", "木")).as("金克木，通关水").isEqualTo("水");
        assertThat((Object) call(med, null, "水", "木")).as("水不生克土…非克关系无通关").isNull();
    }

    // ==========================================================================
    // C. 体用生克五级与吉凶回映射
    // ==========================================================================

    @Test
    @DisplayName("C1·体用五级 shengKeLevel：用生体1/比和2/体克用3/体生用4/用克体5")
    void shengKeLevelFive() throws Exception {
        Method s = m("shengKeLevel", String.class, String.class);
        assertThat(i(s, null, "金", "土")).as("土生金→用生体(1)").isEqualTo(1);
        assertThat(i(s, null, "金", "金")).as("比和(2)").isEqualTo(2);
        assertThat(i(s, null, "金", "木")).as("金克木→体克用(3)").isEqualTo(3);
        assertThat(i(s, null, "金", "水")).as("金生水→体生用(4)").isEqualTo(4);
        assertThat(i(s, null, "金", "火")).as("火克金→用克体(5)").isEqualTo(5);
    }

    @Test
    @DisplayName("C2·五级名称与吉凶标签 shengKeName/shengKeLabel（含未知兜底）")
    void shengKeNameAndLabel() throws Exception {
        Method nm = m("shengKeName", int.class);
        Method lb = m("shengKeLabel", int.class);
        assertThat((String) call(nm, null, 1)).isEqualTo("用生体");
        assertThat((String) call(nm, null, 2)).isEqualTo("体用比和");
        assertThat((String) call(nm, null, 3)).isEqualTo("体克用");
        assertThat((String) call(nm, null, 4)).isEqualTo("体生用");
        assertThat((String) call(nm, null, 5)).isEqualTo("用克体");
        assertThat((String) call(nm, null, 0)).as("非法级兜底").isEqualTo("体用未定");
        assertThat((String) call(lb, null, 1)).isEqualTo("大吉");
        assertThat((String) call(lb, null, 2)).isEqualTo("吉");
        assertThat((String) call(lb, null, 3)).isEqualTo("平");
        assertThat((String) call(lb, null, 4)).isEqualTo("凶");
        assertThat((String) call(lb, null, 5)).isEqualTo("大凶");
        assertThat((String) call(lb, null, 9)).as("非法级兜底平").isEqualTo("平");
    }

    @Test
    @DisplayName("C3·shengKeType 反解析 parseShengKeLevel：取首字符 1~5，否则兜底 3")
    void parseShengKeLevelCases() throws Exception {
        Method p = m("parseShengKeLevel", String.class);
        assertThat(i(p, null, "1·用生体（大吉）")).isEqualTo(1);
        assertThat(i(p, null, "5·用克体")).isEqualTo(5);
        assertThat(i(p, null, "3x")).isEqualTo(3);
        assertThat(i(p, null, "9·越界")).as("首字符非1~5→3").isEqualTo(3);
        assertThat(i(p, null, "abc")).as("非数→3").isEqualTo(3);
        assertThat(i(p, null, "")).as("空→3").isEqualTo(3);
        assertThat(i(p, null, (Object) null)).as("null→3").isEqualTo(3);
    }

    @Test
    @DisplayName("C4·分值→吉凶等级 scoreToLevel：clamp 1~5 后 5大吉…1大凶")
    void scoreToLevelCases() throws Exception {
        PlumBlossomOntologyJobWorker w = new PlumBlossomOntologyJobWorker();
        Method s = m("scoreToLevel", int.class);
        assertThat((String) call(s, w, 5)).isEqualTo("大吉");
        assertThat((String) call(s, w, 4)).isEqualTo("吉");
        assertThat((String) call(s, w, 3)).isEqualTo("平");
        assertThat((String) call(s, w, 2)).isEqualTo("凶");
        assertThat((String) call(s, w, 1)).isEqualTo("大凶");
        assertThat((String) call(s, w, 9)).as("上溢钳制→大吉").isEqualTo("大吉");
        assertThat((String) call(s, w, 0)).as("下溢钳制→大凶").isEqualTo("大凶");
        assertThat((String) call(s, w, -3)).as("负值钳制→大凶").isEqualTo("大凶");
    }

    @Test
    @DisplayName("C5·卦对体生克 relationToBody 五支：比和/生体/克体/体克/体生")
    void relationToBodyFive() throws Exception {
        PlumBlossomOntologyJobWorker w = new PlumBlossomOntologyJobWorker();
        Method r = m("relationToBody", String.class, String.class);
        assertThat((String) call(r, w, "坤", "坤")).as("土=土→比和").isEqualTo("比和");
        assertThat((String) call(r, w, "乾", "坎")).as("金生水→生体").isEqualTo("生体");
        assertThat((String) call(r, w, "乾", "震")).as("金克木→克体").isEqualTo("克体");
        assertThat((String) call(r, w, "坎", "坤")).as("土克水→体克").isEqualTo("体克");
        assertThat((String) call(r, w, "震", "坎")).as("水生木→体生").isEqualTo("体生");
    }

    @Test
    @DisplayName("C6·重卦对体 relationToBodyHex：空→比和、取最显著（克体优先）")
    void relationToBodyHexCases() throws Exception {
        PlumBlossomOntologyJobWorker w = new PlumBlossomOntologyJobWorker();
        Method r = m("relationToBodyHex", String.class, String.class);
        assertThat((String) call(r, w, (Object) null, "震")).as("空卦→比和").isEqualTo("比和");
        assertThat((String) call(r, w, "乾为天", "震")).as("上乾下乾皆金克木→克体").isEqualTo("克体");
        assertThat((String) call(r, w, "天水讼", "震")).as("上乾克木/下坎生木→取克体").isEqualTo("克体");
    }

    // ==========================================================================
    // D. 应期定应（动静分迟速 + 近中远档 + 吉凶期）
    // ==========================================================================

    @Test
    @DisplayName("D1·动静分迟速 responsePeriodOf：行/走半其数、坐/卧倍其数、立不改")
    void responsePeriodMotion() throws Exception {
        Method r = m("responsePeriodOf", int.class, String.class, String.class, String.class, String.class);
        String hang = call(r, null, 10, "水", "火", "行", "吉");
        assertThat(hang).contains("近·约2~8日内").contains("行则应速，中分取半其数").contains("吉期");
        String zou = call(r, null, 10, "水", "火", "走", "吉");
        assertThat(zou).contains("近·约2~8日内").contains("走则愈速");
        String zuo = call(r, null, 10, "水", "火", "坐", "凶");
        assertThat(zuo).contains("远·约17~23年内").contains("坐则事应迟，倍其成卦之数").contains("凶期");
        String li = call(r, null, 24, "水", "火", "立", "吉");
        assertThat(li).contains("远·约21~27年内").contains("立则半迟半速").contains("不改其数");
    }

    @Test
    @DisplayName("D2·应期分档 responsePeriodOf：近(≤6日)/中(7~11月)/远(>11年) 与五行生数参断")
    void responsePeriodLevels() throws Exception {
        Method r = m("responsePeriodOf", int.class, String.class, String.class, String.class, String.class);
        assertThat((String) call(r, null, 6, "木", "火", "立", "平")).as("6→近/日")
                .contains("近·约3~9日内");
        assertThat((String) call(r, null, 9, "木", "火", "立", "平")).as("9→中/月")
                .contains("中·约6~12月内");
        assertThat((String) call(r, null, 20, "木", "火", "立", "平")).as("20→远/年")
                .contains("远·约17~23年内");
        // 五行生数：水1 火2
        assertThat((String) call(r, null, 10, "水", "火", "立", "吉")).contains("体水＝1、用火＝2");
        // 凶判定来自 auspiciousness 含「凶」；「大凶」亦含
        assertThat((String) call(r, null, 10, "水", "火", "立", "大凶")).contains("凶期");
        assertThat((String) call(r, null, 10, "水", "火", "立", "平")).contains("吉期");
    }
}
