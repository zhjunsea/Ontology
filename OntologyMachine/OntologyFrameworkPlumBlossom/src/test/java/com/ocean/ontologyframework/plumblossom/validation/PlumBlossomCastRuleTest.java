package com.ocean.ontologyframework.plumblossom.validation;

import com.ocean.ontologyframework.plumblossom.PlumBlossomOntologyJobWorker;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 梅花易数起卦规则·经典符合性单测（纯反射，离线；不依赖 MySQL/Ontop/Camunda/Spring）。
 *
 * <p>依原著卷之一（字占 / 风觉占 / 鸟占 / 听声音占 / 形物占 / 验色占）与卷之二（十应目论），
 * 对 {@link PlumBlossomOntologyJobWorker} 内的起卦规则助手作逐条断言。
 */
@DisplayName("梅花易数起卦规则：字占取数 / 风觉·鸟·听声 / 十应 / 五色（离线）")
public class PlumBlossomCastRuleTest {

    private static Method m(String name, Class<?>... params) throws Exception {
        Method mm = PlumBlossomOntologyJobWorker.class.getDeclaredMethod(name, params);
        mm.setAccessible(true);
        return mm;
    }

    @SuppressWarnings("unchecked")
    private static <T> T call(Method method, Object target, Object... args) throws Exception {
        return (T) method.invoke(target, args);
    }

    private static int[] split(String text, String tones) throws Exception {
        return call(m("splitCharacters", String.class, String.class), null, text, tones);
    }

    // ==========================================================================
    // 卷之一·字占：一字~十一字以上逐子法
    // ==========================================================================

    @Test
    @DisplayName("字占·十一字以上：止用字数、平分上下（不数画数、不用平仄）")
    void charSplitOverTenByCharCount() throws Exception {
        assertThat(split("甲乙丙丁戊己庚辛壬癸子丑", null)).as("十二字→半上卦6/半下卦6").containsExactly(6, 6);
        assertThat(split("甲乙丙丁戊己庚辛壬癸子", null)).as("十一字→半上卦5/余下卦6").containsExactly(5, 6);
        assertThat(split("甲乙丙丁戊己庚辛壬癸子丑寅卯", "99999999999999"))
                .as("十四字仍以字数为准（平仄无关）").containsExactly(7, 7);
    }

    @Test
    @DisplayName("字占·四~十字：以平仄声音调之（平1/上2/去3/入4）")
    void charSplitFourToTenByTones() throws Exception {
        assertThat(split("甲乙丙丁", "1234")).as("四字：上=1+2=3、下=3+4=7").containsExactly(3, 7);
        assertThat(split("甲乙丙丁戊", "12341")).as("五字：上=1+2=3、下=3+4+1=8").containsExactly(3, 8);
        assertThat(split("甲乙丙丁戊己庚辛壬癸", "1234123412"))
                .as("十字：上=1+2+3+4+1=11、下=2+3+4+1+2=12").containsExactly(11, 12);
    }

    @Test
    @DisplayName("字占·一~三字：以字画（笔画）取数")
    void charSplitLowByStrokes() throws Exception {
        // stroke(): 一=1、二=2、三=3
        assertThat(split("一二", null)).as("二字：一字上卦、一字下卦").containsExactly(1, 2);
        assertThat(split("一二三", null)).as("三字：一字上卦、二字下卦").containsExactly(1, 5);
    }

    @Test
    @DisplayName("第28轮·引入外部资源：Unihan笔画库 + 平水韵声调表")
    void externalResourceLanding() throws Exception {
        // Unihan kTotalStrokes：东=5、月=4、甲=5
        Method st = m("stroke", char.class);
        assertThat((Integer) call(st, null, '东')).as("东5画").isEqualTo(5);
        assertThat((Integer) call(st, null, '月')).as("月4画").isEqualTo(4);
        assertThat((Integer) call(st, null, '一')).as("一1画").isEqualTo(1);
        assertThat((Integer) call(st, null, '甲')).as("甲5画").isEqualTo(5);

        // 平水韵：四~十字无前端输入时自动以平仄取数（入>平>上>去）
        // 一=入4 二=去3 三=平1 四=去3 → 上=4+3=7、下=1+3=4
        assertThat(split("一二三四", null)).as("四字·内置平水韵").containsExactly(7, 4);
        // 东=平1 西=平1 南=平1 北=入4 → 上=1+1=2、下=1+4=5
        assertThat(split("东西南北", null)).as("四字·内置平水韵").containsExactly(2, 5);
        // 前端平仄优先于内置表
        assertThat(split("一二三四", "1234")).as("前端平仄优先").containsExactly(3, 7);

        Method src = m("charNumberSource", String.class, String.class);
        assertThat((String) call(src, null, "东西南北", null)).as("来源").isEqualTo("内置平水韵");
        assertThat((String) call(src, null, "一二", null)).as("来源").isEqualTo("笔画（Unihan）");
        assertThat((String) call(src, null, "一二三四", "1234")).as("来源").isEqualTo("前端平仄");
        assertThat((String) call(src, null, "甲乙丙丁戊己庚辛壬癸子", null)).as("来源").isEqualTo("字数");
    }

    // ==========================================================================
    // 卷之一·风觉占：以风（巽）为上卦、风来方位之卦为下卦
    // ==========================================================================

    @Test
    @DisplayName("风觉占：上卦取巽（风）、下卦取风来方位（南→离），加时取动爻")
    void windAwakeningCast() throws Exception {
        PlumBlossomOntologyJobWorker w = new PlumBlossomOntologyJobWorker();
        Method enc = m("encodeStartNumbers", String.class, Map.class);
        Map<String, Object> vars = new LinkedHashMap<>();
        vars.put("windDirection", "南");
        int[] r = parseRaw(call(enc, w, "风觉占", vars));
        assertThat(r[0]).as("上卦应为巽（先天数 5）").isEqualTo(5);
        assertThat(r[1]).as("下卦应为风来方位（南→离，先天数 3）").isEqualTo(3);
        assertThat(r[2]).as("动爻 = 上5+下3+时（默认子=1）").isEqualTo(9);

        vars.put("windDirection", "东");
        int[] r2 = parseRaw(call(enc, w, "风觉占", vars));
        assertThat(r2[1]).as("东→震（先天数 4）").isEqualTo(4);
    }

    // ==========================================================================
    // 卷之一·鸟占：数只数为上卦、看方所为下卦
    // ==========================================================================

    @Test
    @DisplayName("鸟占：数其只数为上卦、看其方所为下卦，加时取动爻")
    void birdCast() throws Exception {
        PlumBlossomOntologyJobWorker w = new PlumBlossomOntologyJobWorker();
        Method enc = m("encodeStartNumbers", String.class, Map.class);
        Map<String, Object> vars = new LinkedHashMap<>();
        vars.put("birdInfo", "3只鸟自南来");
        int[] r = parseRaw(call(enc, w, "鸟占", vars));
        assertThat(r[0]).as("只数 3 → 上卦").isEqualTo(3);
        assertThat(r[1]).as("方所南 → 离（3）").isEqualTo(3);
    }

    // ==========================================================================
    // 卷之一·听声音占：辨物声所属（口出兑/扣击震/金乾/火离/土坤）
    // ==========================================================================

    @Test
    @DisplayName("听声音占：辨物声所属为下卦（口出兑2/扣击震4/金乾1/火离3/土坤8）")
    void soundCast() throws Exception {
        Method st = m("soundToTrigram", String.class);
        assertThat((int) call(st, null, "人语之声")).as("口出→兑(2)").isEqualTo(2);
        assertThat((int) call(st, null, "扣击木板")).as("扣击→震(4)").isEqualTo(4);
        assertThat((int) call(st, null, "钟磬钲铎之声")).as("金声→乾(1)").isEqualTo(1);
        assertThat((int) call(st, null, "烈焰爆响")).as("火声→离(3)").isEqualTo(3);
        assertThat((int) call(st, null, "筑基杵声")).as("土声→坤(8)").isEqualTo(8);
        assertThat((int) call(st, null, "无从辨识")).as("未识别→0").isZero();

        PlumBlossomOntologyJobWorker w = new PlumBlossomOntologyJobWorker();
        Method enc = m("encodeStartNumbers", String.class, Map.class);
        Map<String, Object> vars = new LinkedHashMap<>();
        vars.put("heardSound", "人语之声");
        int[] r = parseRaw(call(enc, w, "听声音占", vars));
        assertThat(r[1]).as("听声音占下卦应取物声所属（兑=2）").isEqualTo(2);

        // 「数其数」为上卦：一声属乾、二声属兑、三声属离——声数直取上卦
        vars.put("heardSound", "闻钟磬之声三下");
        int[] r3 = parseRaw(call(enc, w, "听声音占", vars));
        assertThat(r3[0]).as("声数 3 → 上卦=3（离）").isEqualTo(3);
        assertThat(r3[1]).as("金声（钟磬）→ 下卦=乾(1)").isEqualTo(1);
    }

    // ==========================================================================
    // 卷之二·十应目论：十一类
    // ==========================================================================

    @Test
    @DisplayName("十应类别：原著十一类（时令/天时/地理/人事/方卦/动物/静物/言语/声音/五色/写字）")
    void tenResponseCategories() throws Exception {
        Method cat = m("tenResponseCategory", String.class);
        assertThat((String) call(cat, null, "春和景明")).as("时令").isEqualTo("时令");
        assertThat((String) call(cat, null, "晴空万里")).as("天时").isEqualTo("天时");
        assertThat((String) call(cat, null, "江湖河池")).as("地理").isEqualTo("地理");
        assertThat((String) call(cat, null, "见一老人")).as("人事（非人品）").isEqualTo("人事");
        assertThat((String) call(cat, null, "来自东方")).as("方卦").isEqualTo("方卦");
        assertThat((String) call(cat, null, "见喜鹊飞")).as("动物").isEqualTo("动物");
        assertThat((String) call(cat, null, "见桌椅器物")).as("静物").isEqualTo("静物");
        assertThat((String) call(cat, null, "闻人笑语")).as("言语").isEqualTo("言语");
        assertThat((String) call(cat, null, "闻钟磬之声")).as("声音").isEqualTo("声音");
        assertThat((String) call(cat, null, "见青色之物")).as("五色").isEqualTo("五色");
        assertThat((String) call(cat, null, "见榜文一字")).as("写字").isEqualTo("写字");
    }

    @Test
    @DisplayName("五色之应：不论卦象，直推五行（青木/红火/白金/黑水/黄土）")
    void fiveColorPhase() throws Exception {
        Method f = m("fivePhaseOfColor", String.class);
        assertThat((String) call(f, null, "青碧绿")).isEqualTo("木");
        assertThat((String) call(f, null, "红紫赤")).isEqualTo("火");
        assertThat((String) call(f, null, "白")).isEqualTo("金");
        assertThat((String) call(f, null, "黑")).isEqualTo("水");
        assertThat((String) call(f, null, "黄")).isEqualTo("土");
    }

    // ==========================================================================
    // 回归：形物占 / 验色占（未改动，防止回归）
    // ==========================================================================

    @Test
    @DisplayName("回归·形物占与验色占取象（圆乾/刚兑/方坤/枯槁离；青震/红紫赤离/黄坤/白兑/黑坎）")
    void shapeAndColorRegression() throws Exception {
        Method shape = m("shapeToTrigram", String.class);
        assertThat((int) call(shape, null, "圆")).isEqualTo(1);
        assertThat((int) call(shape, null, "刚")).isEqualTo(2);
        assertThat((int) call(shape, null, "方")).isEqualTo(8);
        assertThat((int) call(shape, null, "枯槁")).isEqualTo(3);
        Method color = m("colorToTrigram", String.class);
        assertThat((int) call(color, null, "青")).isEqualTo(4);
        assertThat((int) call(color, null, "红紫赤")).isEqualTo(3);
        assertThat((int) call(color, null, "黄")).isEqualTo(8);
        assertThat((int) call(color, null, "白")).isEqualTo(2);
        assertThat((int) call(color, null, "黑")).isEqualTo(6);
    }

    @Test
    @DisplayName("工具·firstNumber 取文本首个整数（阿拉伯数字与中文数词）")
    void firstNumberHelper() throws Exception {
        Method fn = m("firstNumber", String.class);
        assertThat((int) call(fn, null, "3只鸟自南来")).isEqualTo(3);
        assertThat((int) call(fn, null, "十二只")).as("中文数词十二→12").isEqualTo(12);
        assertThat((int) call(fn, null, "闻钟磬之声三下")).as("中文数词三→3").isEqualTo(3);
        assertThat((int) call(fn, null, "二十三声")).as("中文数词二十三→23").isEqualTo(23);
        assertThat((int) call(fn, null, "")).isZero();
    }

    // ==========================================================================
    // 卷之一·为人占·听语声：一句/两句/语多三级；及书写来意（同字占）
    // ==========================================================================

    @Test
    @DisplayName("为人占·听语声（一句）：如字数分之起卦（同字占）")
    void humanListenSpeechOneClause() throws Exception {
        PlumBlossomOntologyJobWorker w = new PlumBlossomOntologyJobWorker();
        Method enc = m("encodeStartNumbers", String.class, Map.class);
        Map<String, Object> vars = new LinkedHashMap<>();
        vars.put("humanSub", "听语声");
        vars.put("speechText", "甲乙丙丁");
        vars.put("toneNumbers", "1234");
        int[] r = parseRaw(call(enc, w, "为人占", vars));
        // 同字占四字：上=1+2=3、下=3+4=7；动爻=上+下+时（默认子=1）
        assertThat(r[0]).as("一句·上卦=3").isEqualTo(3);
        assertThat(r[1]).as("一句·下卦=7").isEqualTo(7);
        assertThat(r[2]).as("动爻=3+7+1=11").isEqualTo(11);
    }

    @Test
    @DisplayName("为人占·听语声（两句）：先一句为上卦、后一句为下卦（各以字数）")
    void humanListenSpeechTwoClauses() throws Exception {
        PlumBlossomOntologyJobWorker w = new PlumBlossomOntologyJobWorker();
        Method enc = m("encodeStartNumbers", String.class, Map.class);
        Map<String, Object> vars = new LinkedHashMap<>();
        vars.put("humanSub", "听语声");
        vars.put("speechText", "甲乙，丙丁戊");
        int[] r = parseRaw(call(enc, w, "为人占", vars));
        assertThat(r[0]).as("先句『甲乙』2字→上卦=2").isEqualTo(2);
        assertThat(r[1]).as("后句『丙丁戊』3字→下卦=3").isEqualTo(3);
        assertThat(r[2]).as("动爻=2+3+1=6").isEqualTo(6);
    }

    @Test
    @DisplayName("为人占·听语声（语多）：但用初听一句合末后闻一语，余句不用")
    void humanListenSpeechManyClauses() throws Exception {
        PlumBlossomOntologyJobWorker w = new PlumBlossomOntologyJobWorker();
        Method enc = m("encodeStartNumbers", String.class, Map.class);
        Map<String, Object> vars = new LinkedHashMap<>();
        vars.put("humanSub", "听语声");
        vars.put("speechText", "甲乙，丙丁，戊己庚辛"); // 3 句：初句2字、末句4字
        int[] r = parseRaw(call(enc, w, "为人占", vars));
        assertThat(r[0]).as("初听一句『甲乙』2字→上卦=2").isEqualTo(2);
        assertThat(r[1]).as("末后闻一语『戊己庚辛』4字→下卦=4").isEqualTo(4);
        assertThat(r[2]).as("动爻=2+4+1=7").isEqualTo(7);
    }

    @Test
    @DisplayName("为人占·书写来意：则以其字占之（同字占）")
    void humanWriteIntention() throws Exception {
        PlumBlossomOntologyJobWorker w = new PlumBlossomOntologyJobWorker();
        Method enc = m("encodeStartNumbers", String.class, Map.class);
        Map<String, Object> vars = new LinkedHashMap<>();
        vars.put("humanSub", "书写来意");
        vars.put("text", "甲乙丙丁");
        vars.put("toneNumbers", "1234");
        int[] r = parseRaw(call(enc, w, "为人占", vars));
        assertThat(r[0]).as("同字占·上卦=3").isEqualTo(3);
        assertThat(r[1]).as("同字占·下卦=7").isEqualTo(7);
    }

    @Test
    @DisplayName("为人占·观人品取象：取象为上卦、时数为下卦（回归）")
    void humanObservePersonRegression() throws Exception {
        PlumBlossomOntologyJobWorker w = new PlumBlossomOntologyJobWorker();
        Method enc = m("encodeStartNumbers", String.class, Map.class);
        Map<String, Object> vars = new LinkedHashMap<>();
        vars.put("humanInfo", "老人");
        int[] r = parseRaw(call(enc, w, "为人占", vars));
        assertThat(r[0]).as("老人属乾（先天数1）").isEqualTo(1);
        assertThat(r[1]).as("下卦取时数（默认子=1）").isEqualTo(1);
    }

    @Test
    @DisplayName("工具·splitClauses 按句读断句（保序、弃空）")
    void splitClausesHelper() throws Exception {
        Method sc = m("splitClauses", String.class);
        assertThat((java.util.List<String>) call(sc, null, "甲乙，丙丁。戊"))
                .containsExactly("甲乙", "丙丁", "戊");
        assertThat((java.util.List<String>) call(sc, null, "")).isEmpty();
    }

    // ==========================================================================
    // 卷之一·声音占：前后两声各取上下卦（邻夜扣门占例）；及应期·动静刻期倍半
    // ==========================================================================

    @Test
    @DisplayName("声音占：单一闻声数→上卦、时配下卦；前后两声→各取上下卦（邻夜扣门占例）")
    void soundCastTwoCounts() throws Exception {
        PlumBlossomOntologyJobWorker w = new PlumBlossomOntologyJobWorker();
        Method enc = m("encodeStartNumbers", String.class, Map.class);

        Map<String, Object> one = new LinkedHashMap<>();
        one.put("soundCount", 3);
        int[] r1 = parseRaw(call(enc, w, "声音占", one));
        assertThat(r1[0]).as("声数 3→上卦").isEqualTo(3);
        assertThat(r1[1]).as("无第二声数→下卦取时数（默认子=1）").isEqualTo(1);
        assertThat(r1[2]).as("动爻=3+1=4").isEqualTo(4);

        // 邻夜扣门借物占：初扣一声、又扣五声、酉时 → 乾上巽下（天风姤），四爻动
        Map<String, Object> two = new LinkedHashMap<>();
        two.put("soundCount", 1);
        two.put("soundCount2", 5);
        two.put("hourZhi", "酉");
        int[] r2 = parseRaw(call(enc, w, "声音占", two));
        assertThat(r2[0]).as("一声属乾→上卦=1").isEqualTo(1);
        assertThat(r2[1]).as("五声属巽→下卦=5").isEqualTo(5);
        assertThat(r2[2]).as("动爻=乾1+巽5+酉10=16（六除余四）").isEqualTo(16);
    }

    @Test
    @DisplayName("应期·动静刻期：行则半其数、坐/卧倍其数、立即其数（老人有忧色占结语）")
    void responsePeriodByMotion() throws Exception {
        Method rp = m("responsePeriodOf", int.class, String.class, String.class, String.class, String.class);
        // 全卦之数 t=10
        assertThat((String) call(rp, null, 10, "金", "火", "行", "吉"))
                .as("行→半其数=5→近·约2~8日内").contains("近").contains("约2~8日");
        assertThat((String) call(rp, null, 10, "金", "火", "坐", "吉"))
                .as("坐→倍其数=20→远·约17~23年内").contains("远").contains("约17~23年");
        assertThat((String) call(rp, null, 10, "金", "火", "立", "吉"))
                .as("立→其数=10→中·约7~13月内").contains("中").contains("约7~13月");
        assertThat((String) call(rp, null, 10, "金", "火", null, "吉"))
                .as("动静未详→其数=10→中·约7~13月内").contains("中");
    }

    // ==========================================================================
    // 卷之二·天时占：不分体用、全观诸卦（A14）；静物之应·圆缺直断（B17）
    // ==========================================================================

    @Test
    @DisplayName("天时占·不分体用、全观诸卦：坎多主雨、离多主晴（本/互/变六卦数八卦）")
    void tianShiVerdictByTrigramCount() throws Exception {
        Method t = m("tianShiVerdict", String.class, String.class, String.class);
        // 本卦水雷屯(坎上震下)＋互卦山地剥(艮上坤下)＋变卦水泽节(坎上兑下)：坎见 2 次 → 主雨
        String v = (String) call(t, null, "水雷屯", "山地剥", "水泽节");
        assertThat(v).as("坎2多于离0→主雨").contains("主雨").contains("坎（2）");
        assertThat(v).as("应显式声明不论体用").contains("不论体用");
        // 本卦火风鼎(离上巽下)＋互卦泽天夬(兑上乾下)＋变卦火天大有(离上乾下)：离见 2 次 → 主晴
        String v2 = (String) call(t, null, "火风鼎", "泽天夬", "火天大有");
        assertThat(v2).as("离2多于坎0→主晴").contains("主晴").contains("离（2）");
    }

    @Test
    @DisplayName("静物之应·不论卦象直断：物圆者事成、器缺者事败")
    void stillObjectDirectVerdict() throws Exception {
        Method s = m("stillObjectDirectVerdict", String.class);
        assertThat((String) call(s, null, "见圆物")).as("物圆→事成").contains("事成");
        assertThat((String) call(s, null, "器有破损")).as("物缺→事败").contains("事败");
        assertThat((String) call(s, null, "见圆物破损")).as("圆缺并见→参半").contains("参半");
        assertThat((String) call(s, null, "闻人笑语")).as("无圆缺之征→空").isEmpty();
    }

    // ==========================================================================
    // 卷之二·体用总诀逐卦生克之应；卷之三·占卦诀；卷之二·占生产辨男女（第六轮）
    // ==========================================================================

    @Test
    @DisplayName("体用总诀·逐卦生体/克体之应：坎（水）生木体→生体吉断、乾（金）克木体→克体凶断")
    void bodyRelationNotesByTrigram() throws Exception {
        PlumBlossomOntologyJobWorker w = new PlumBlossomOntologyJobWorker();
        Method f = m("bodyRelationNotes", String.class, String.class, String.class, String.class);
        // 体＝巽（木）：用坎（水）生木→生体；变乾为天（金）克木→克体
        String s = call(f, w, "巽", "坎", "", "乾为天");
        assertThat(s).as("坎卦生体").contains("坎卦生体").contains("北方之喜");
        assertThat(s).as("乾卦克体").contains("乾卦克体").contains("公事之扰");
        // 体＝乾（金）：用坤（土）生金→生体
        String s2 = call(f, w, "乾", "坤", "", "");
        assertThat(s2).contains("坤卦生体").contains("田土之喜");
    }

    @Test
    @DisplayName("占卦诀·生体卦所在定迟速 + 克体卦阻日（乾1/兑2/离3…依先天卦数）")
    void zhanGuaJueByPosition() throws Exception {
        PlumBlossomOntologyJobWorker w = new PlumBlossomOntologyJobWorker();
        Method f = m("zhanGuaJueNote", String.class, String.class, String.class, String.class);
        // 体＝巽（木）：生体在用卦（坎水生木）→事即成就；变卦乾为天（金克木）→阻一日
        String s = call(f, w, "巽", "坎", "", "乾为天");
        assertThat(s).as("生体是用卦").contains("生体是用卦").contains("事即成就");
        assertThat(s).as("乾克体阻一日").contains("阻节").contains("共1日").contains("乾(1日)");
        // 生体在互卦：体＝艮（土），互＝离为火（火生土）→渐成
        String s2 = call(f, w, "艮", "震", "离为火", "");
        assertThat(s2).as("生体是互卦").contains("生体是互卦").contains("渐成");
    }

    @Test
    @DisplayName("占生产·辨男女：阳爻多者生男、阴爻多者生女（本卦六爻）")
    void birthGenderHintByLines() throws Exception {
        Method f = m("birthGenderHint", String.class);
        assertThat((String) call(f, null, "乾为天")).as("六阳→生男").contains("生男");
        assertThat((String) call(f, null, "坤为地")).as("六阴→生女").contains("生女");
        // 地天泰（上坤下乾，乾三阳、坤三阴 → 阳3/阴3）→ 察左右奇偶
        assertThat((String) call(f, null, "地天泰")).as("阴阳均→察左右").contains("察所占左右");
    }

    // ==========================================================================
    // 卷之二·分项占断细则：婚姻性情容貌 / 疾病测鬼神 / 饮食所食（第七轮 B21/B22/B23）
    // ==========================================================================

    @Test
    @DisplayName("婚姻占·八卦性情容貌：乾端正而长、坎邪淫黑色、坤貌丑大腹而黄（卷之二·婚姻占第五）")
    void marriageAppearanceByTrigram() throws Exception {
        Method f = m("marriageAppearance", String.class);
        assertThat((String) call(f, null, "乾")).as("乾→端正而长").contains("端正而长");
        assertThat((String) call(f, null, "坎")).as("坎→邪淫黑色").contains("邪淫").contains("黑色");
        assertThat((String) call(f, null, "坤")).as("坤→貌丑大腹而黄").contains("貌丑").contains("大腹而黄");
        assertThat((String) call(f, null, "兑")).as("兑→高长语话喜悦").contains("高长").contains("语话喜悦");
    }

    @Test
    @DisplayName("疾病占·测鬼神：克体之卦测所犯鬼神方位（卷之二·疾病占第十六）")
    void diseaseSpiritByTrigram() throws Exception {
        Method f = m("diseaseSpiritNote", String.class);
        assertThat((String) call(f, null, "乾")).as("乾→西北方之神/兵刀之鬼").contains("西北方之神").contains("兵刀之鬼");
        assertThat((String) call(f, null, "离")).as("离→南方之鬼/犯灶司").contains("南方之鬼").contains("灶司");
        assertThat((String) call(f, null, "艮")).as("艮→东北之神/山魈木客").contains("东北之神").contains("山魈木客");
    }

    @Test
    @DisplayName("疾病占·测鬼神：无克体之卦则不论（原著「卦中无克体之卦者，不必论之」）")
    void diseaseSpiritDetailGate() throws Exception {
        PlumBlossomOntologyJobWorker w = new PlumBlossomOntologyJobWorker();
        Method f = m("diseaseSpiritDetail", String.class, String.class, String.class, String.class);
        // 体＝坎（水）：用兑（金生水→生体）、互乾为天/变兑为泽（皆金→生体）→ 无克体之卦 → 空
        String none = call(f, w, "坎", "兑", "乾为天", "兑为泽");
        assertThat(none).as("无克体卦→空").isEmpty();
        // 体＝坎（水）：互＝坤为地（坤土克水→克体）→ 出西南之神
        String hit = call(f, w, "坎", "兑", "坤为地", "");
        assertThat(hit).as("坤克体→西南之神").contains("坤卦克体").contains("西南之神");
    }

    @Test
    @DisplayName("饮食占·坎酒兑食与逐卦所食（卷之二·饮食占第七 / 卷之三·饮食篇）")
    void dietNoteByKanDui() throws Exception {
        PlumBlossomOntologyJobWorker w = new PlumBlossomOntologyJobWorker();
        Method f = m("dietNote", String.class, String.class, String.class, String.class);
        // 体＝坎（水）、用＝兑（金）：卦中有坎有兑 → 有酒有食 → 坎兑生身、酒肉醉饱
        String s = call(f, w, "坎", "兑", "", "");
        assertThat(s).as("有坎有兑→酒食醉饱").contains("有坎则有酒").contains("有兑则有食").contains("酒肉醉饱");
        // 体＝艮（土）、用＝坎（水）：有坎无兑 → 有酒；用坎→所食推为水族
        String s2 = call(f, w, "艮", "坎", "", "");
        assertThat(s2).as("有坎无兑→有酒").contains("有坎则有酒").doesNotContain("有兑则有食");
        assertThat(s2).as("用坎→水族味咸").contains("水簇").contains("味咸");
        // 体＝乾（金）、用＝震（木）：无坎无兑 → 酒食皆无
        String s3 = call(f, w, "乾", "震", "", "");
        assertThat(s3).as("无坎无兑→皆无").contains("无坎无兑").contains("皆无");
    }

    // ==========================================================================
    // 卷之二·诸占逐卦细则：失物方位 / 出行行止 / 行人盛衰 / 求名方道 / 求财定日（第八轮 B24–B28）
    // ==========================================================================

    @Test
    @DisplayName("失物占·以变卦定失物所在（乾西北公廨楼阁/坤西南仓廪/兑西方废井缺沼）")
    void lostItemDetailByChanged() throws Exception {
        Method f = m("lostItemDetail", String.class);
        assertThat((String) call(f, null, "乾为天")).as("乾→西北公廨楼阁").contains("西北").contains("公廨楼阁");
        assertThat((String) call(f, null, "坤为地")).as("坤→西南仓廪").contains("西南").contains("仓廪");
        assertThat((String) call(f, null, "兑为泽")).as("兑→西方废井缺沼").contains("西方").contains("废井缺沼");
    }

    @Test
    @DisplayName("出行占·乾震多主动、巽宜乘船、坎防失脱（卷二·出行占第十二）")
    void travelDetailByTrigram() throws Exception {
        PlumBlossomOntologyJobWorker w = new PlumBlossomOntologyJobWorker();
        Method f = m("travelDetail", String.class, String.class, String.class, String.class);
        // 体乾、用坎、互震为雷（震震）、变巽为风（巽巽）：乾震多→主动；见巽/坎
        String s = call(f, w, "乾", "坎", "震为雷", "巽为风");
        assertThat(s).as("乾震多→主动").contains("主动");
        assertThat(s).as("巽宜乘船").contains("巽宜乘船");
        assertThat(s).as("坎防失脱").contains("坎防失脱");
        // 体坤、用艮：坤艮多→不动
        String s2 = call(f, w, "坤", "艮", "", "");
        assertThat(s2).as("坤艮多→不动").contains("不动");
    }

    @Test
    @DisplayName("行人占·用卦为行人之盛旺（体生用→顺快、体克用→灾殃；震多不宁）")
    void travelerDetailByUse() throws Exception {
        PlumBlossomOntologyJobWorker w = new PlumBlossomOntologyJobWorker();
        Method f = m("travelerDetail", String.class, String.class, String.class, String.class);
        // 体艮（土）、用乾（金）：体生用（土生金）→ 用得生助在外顺快；互震为雷→震多不宁
        String s = call(f, w, "艮", "乾", "震为雷", "");
        assertThat(s).as("体生用→顺快").contains("顺快");
        assertThat(s).as("震多不宁").contains("震多不宁");
        // 体艮（土）、用坎（水）：体克用（土克水）→ 用受克在外灾殃；坎有险难
        String s2 = call(f, w, "艮", "坎", "", "");
        assertThat(s2).as("体克用→灾殃").contains("灾殃");
        assertThat(s2).as("坎有险难").contains("坎有险难");
    }

    @Test
    @DisplayName("求名占·变卦方道决职任之处、无克体则名易就、有克体则居官见祸（卷二·求名占第九）")
    void fameDetailByKeBody() throws Exception {
        PlumBlossomOntologyJobWorker w = new PlumBlossomOntologyJobWorker();
        Method f = m("fameDetail", String.class, String.class, String.class, String.class);
        // 体坎（水）：用兑（金生水→生体）、变震为雷（水生木→体生）→ 无克体 → 名易就
        String s = call(f, w, "坎", "兑", "", "震为雷");
        assertThat(s).as("变卦方道决职任").contains("职任");
        assertThat(s).as("无克体→名易就").contains("名易就");
        // 体坎（水）：变坤为地（坤土克水→克体）→ 在任最忌，居官见祸
        String s2 = call(f, w, "坎", "兑", "", "坤为地");
        assertThat(s2).as("有克体→剥官退职").contains("克体之卦").contains("剥官退职");
    }

    @Test
    @DisplayName("求财占·生体/体克用则有财、克体/体生用则破财（卷二·求财占第十）")
    void wealthDetailByRelation() throws Exception {
        PlumBlossomOntologyJobWorker w = new PlumBlossomOntologyJobWorker();
        Method f = m("wealthDetail", String.class, String.class, String.class, String.class);
        // 体乾（金）、用坤（土生金→生体）→ 有财
        String s = call(f, w, "乾", "坤", "", "");
        assertThat(s).as("生体→有财").contains("有财");
        // 体乾（金）、用离（火克金→克体）→ 破财
        String s2 = call(f, w, "乾", "离", "", "");
        assertThat(s2).as("克体→破财").contains("破财");
    }

    // ==========================================================================
    // 卷之三·占物类断法：器物占 / 体用断物之妙 / 观物趣时诀（第九轮 B29–B31）
    // ==========================================================================

    @Test
    @DisplayName("占物类·体用断物之妙（比和成器/生体可食/克体秽物）")
    void objectKindByRelation() throws Exception {
        PlumBlossomOntologyJobWorker w = new PlumBlossomOntologyJobWorker();
        Method f = m("objectDivinationText", String.class, String.class, int.class, String.class,
                String.class, String.class, String.class, String.class, String.class);
        assertThat((String) call(f, w, "占静物", "艮为山", 1, "艮", "坤", "", "", "", ""))
                .as("土土比和→有用成器").contains("比和者为有用成器之物");
        assertThat((String) call(f, w, "占动物", "水雷屯", 1, "坎", "兑", "", "", "", ""))
                .as("兑金生坎水→生体→可食").contains("可食之物");
        assertThat((String) call(f, w, "占静物", "震为雷", 1, "震", "兑", "", "", "", ""))
                .as("兑金克震木→克体→秽物").contains("秽物");
        assertThat((String) call(f, w, "占静物", "艮为山", 1, "艮", "坎", "", "", "", ""))
                .as("艮土克坎水→体克→破碎").contains("破碎损折之物");
    }

    @Test
    @DisplayName("占物类·器物占（兑为毁折：坎/乾为体无伤、震巽木器必破）与观物趣时")
    void objectDuanAndSeason() throws Exception {
        PlumBlossomOntologyJobWorker w = new PlumBlossomOntologyJobWorker();
        Method f = m("objectDivinationText", String.class, String.class, int.class, String.class,
                String.class, String.class, String.class, String.class, String.class);
        // 坎为体、兑为用 → 见兑无伤；春 → 震离为花
        String s = call(f, w, "占动物", "水雷屯", 1, "坎", "兑", "", "", "春", "");
        assertThat(s).as("坎为体见兑无伤").contains("无伤");
        assertThat(s).as("春→震离为花").contains("花");
        // 震为体见兑 → 木器必不禁用
        String s2 = call(f, w, "占静物", "震为雷", 1, "震", "兑", "", "", "", "");
        assertThat(s2).as("木器见兑必不禁用").contains("必不禁用");
        // 无兑 → 器中无毁折之虞
        String s3 = call(f, w, "占静物", "艮为山", 1, "艮", "坤", "", "", "", "");
        assertThat(s3).as("不见兑→无毁折").contains("不见兑");
        // 非占物法 → 空
        assertThat((String) call(f, w, "年月日时起例", "坎为水", 1, "坎", "兑", "", "", "春", ""))
                .as("非占物法→空").isEmpty();
    }

    @Test
    @DisplayName("占物类·物数为体诀/观物看变爻为主/观物克应法/万物戏念（卷之三 B34–B37）")
    void objectSupplementaryJue() throws Exception {
        PlumBlossomOntologyJobWorker w = new PlumBlossomOntologyJobWorker();
        Method f = m("objectDivinationText", String.class, String.class, int.class, String.class,
                String.class, String.class, String.class, String.class, String.class);
        // 乾为天：六阳→多刚；变卦天风姤、动爻5→飞动；外应见圆物；用卦乾→圆白之物
        String s = call(f, w, "占静物", "乾为天", 5, "乾", "乾", "", "天风姤", "秋", "见圆物");
        assertThat(s).as("物数为体诀·多刚").contains("物数为体诀").contains("多刚之物");
        assertThat(s).as("观物看变爻为主·五六爻飞动").contains("观物看变爻为主").contains("能飞动之物");
        assertThat(s).as("观物克应法·圆物").contains("观物克应").contains("圆物");
        assertThat(s).as("万物戏念·乾圆白").contains("万物戏念").contains("圆白之物");
        // 坤为地：六阴→多柔
        String s2 = call(f, w, "占静物", "坤为地", 1, "坤", "艮", "", "", "", "");
        assertThat(s2).as("物数为体诀·多柔").contains("多柔之物");
    }

    // ==========================================================================
    // 卷之五·论八卦性情 + 卷之三·诸卦反对性情（节点16 汇总「卦义性情」；第十轮 B32/B33）
    // ==========================================================================

    @Test
    @DisplayName("八卦性情：乾健/坤顺/震起/艮止/坎陷/离丽/兑说/巽入（卷之五·论八卦性情）")
    void trigramNatureByTrigram() throws Exception {
        Method f = m("trigramNature", String.class);
        assertThat((String) call(f, null, "乾")).isEqualTo("健");
        assertThat((String) call(f, null, "坤")).isEqualTo("顺");
        assertThat((String) call(f, null, "震")).isEqualTo("起");
        assertThat((String) call(f, null, "艮")).isEqualTo("止");
        assertThat((String) call(f, null, "坎")).isEqualTo("陷");
        assertThat((String) call(f, null, "离")).isEqualTo("丽");
        assertThat((String) call(f, null, "兑")).isEqualTo("说");
        assertThat((String) call(f, null, "巽")).isEqualTo("入");
    }

    @Test
    @DisplayName("诸卦反对性情：乾刚坤柔、比欣师忧、升去萃聚（卷之三·诸卦反对性情）")
    void hexagramOppositeNatureByHexagram() throws Exception {
        Method f = m("hexagramOppositeNature", String.class);
        assertThat((String) call(f, null, "乾为天")).isEqualTo("刚");
        assertThat((String) call(f, null, "坤为地")).isEqualTo("柔");
        assertThat((String) call(f, null, "水地比")).isEqualTo("欢欣");
        assertThat((String) call(f, null, "地水师")).isEqualTo("忧虑");
        assertThat((String) call(f, null, "地风升")).contains("去而不复回");
        assertThat((String) call(f, null, "泽地萃")).contains("聚而终不去");
        assertThat((String) call(f, null, "火水未济")).contains("男之终");
        assertThat((String) call(f, null, "天泽履")).as("歌中未列→空").isEmpty();
    }

    @Test
    @DisplayName("节点16·卦义性情行：八卦性情（体/用/互/变）＋反对性情（本卦/变卦）")
    void hexagramNatureLineGate() throws Exception {
        PlumBlossomOntologyJobWorker w = new PlumBlossomOntologyJobWorker();
        Method f = m("hexagramNatureLine", String.class, String.class, String.class, String.class, String.class);
        String s = call(f, w, "天风姤", "乾", "巽", "乾为天", "巽为风");
        assertThat(s).as("八卦性情·体乾健").contains("乾（健）");
        assertThat(s).as("八卦性情·用巽入").contains("巽（入）");
        assertThat(s).as("反对性情·本卦天风姤相遇").contains("本卦天风姤（相遇）");
        assertThat(s).as("反对性情·变卦巽为风内藏").contains("变卦巽为风（内藏）");
    }

    @Test
    @DisplayName("节点10·分类占断附卦义性情（本卦反对性情，卷之三·诸卦反对性情）")
    void categoryVerdictCarriesOppositeNature() throws Exception {
        PlumBlossomOntologyJobWorker w = new PlumBlossomOntologyJobWorker();
        Method f = m("categoryVerdict", String.class, String.class, String.class, String.class,
                String.class, String.class, String.class, int.class);
        String s = call(f, w, "求财", "地天泰", "比和", "乾", "坤", "", "", 3);
        assertThat(s).as("地天泰→大来而小去").contains("卦义性情").contains("地天泰（大来而小去）");
    }

    // ==========================================================================
    // 卷之二·十应目论·各类分应断法 + 向背/静占/十应奥论（第十二轮 B38–B49）
    // ==========================================================================

    @Test
    @DisplayName("十应目论·天时之应：时卦取象＋体卦生克（晴为乾之时：坎体逢生、震巽体见克）")
    void tenResponseTianShi() throws Exception {
        PlumBlossomOntologyJobWorker w = new PlumBlossomOntologyJobWorker();
        Method f = m("tenCategoryNote", String.class, String.class, String.class, String.class);
        String qian = call(f, w, "天时", "晴空明朗", "坎", "春");
        assertThat(qian).as("晴朗→乾之时").contains("天时之应").contains("乾之时");
        assertThat(qian).as("乾金生坎水→逢生大吉").contains("逢生");
        String lei = call(f, w, "天时", "雷声阵阵", "坤", "夏");
        assertThat(lei).as("雷风→震巽之时").contains("震、巽之时");
        assertThat(lei).as("震木克坤土→见克不吉").contains("见克");
    }

    @Test
    @DisplayName("十应目论·地理之应：地卦取象＋体卦生克（茂树秀竹为震之地）")
    void tenResponseDiLi() throws Exception {
        PlumBlossomOntologyJobWorker w = new PlumBlossomOntologyJobWorker();
        Method f = m("tenCategoryNote", String.class, String.class, String.class, String.class);
        String s = call(f, w, "地理", "茂树秀竹", "坤", "春");
        assertThat(s).as("茂树秀竹→震之地").contains("地理之应").contains("茂树秀竹为震之地");
        assertThat(call(f, w, "地理", "江湖溪涧", "离", "秋").toString()).as("坎之地").contains("坎之地");
    }

    @Test
    @DisplayName("十应目论·人事/时令/方卦/动物/静物/言语/声音诸类专诀")
    void tenResponseOtherCategories() throws Exception {
        PlumBlossomOntologyJobWorker w = new PlumBlossomOntologyJobWorker();
        Method f = m("tenCategoryNote", String.class, String.class, String.class, String.class);
        assertThat((String) call(f, w, "人事", "见一老人", "坎", "春"))
                .as("人事·老人属乾").contains("人事之应").contains("老人属乾");
        assertThat((String) call(f, w, "时令", "占于春季", "坎", "春"))
                .as("时令·木旺").contains("时令之应").contains("木旺");
        assertThat((String) call(f, w, "方卦", "坐于南方", "坎", "秋"))
                .as("方卦·南离").contains("方卦之应").contains("南（离）");
        assertThat((String) call(f, w, "动物", "见喜鹊", "坤", "春"))
                .as("动物·灵鹊报喜").contains("动物之应").contains("灵鹊报喜").contains("传喜");
        assertThat((String) call(f, w, "静物", "见桌椅器物", "坎", "春"))
                .as("静物·器物之事").contains("静物之应").contains("笔砚主文书");
        assertThat((String) call(f, w, "言语", "闻人笑语", "坎", "春"))
                .as("言语·吉语则吉").contains("言语之应").contains("闻吉语则吉");
        assertThat((String) call(f, w, "声音", "闻钟磬之声", "坎", "春"))
                .as("声音·怒号争喧").contains("声音之应").contains("怒号之声主争喧");
    }

    @Test
    @DisplayName("应卦与体卦五行生克比和：比和/逢生/见克/泄气")
    void shengKeToneByOmen() throws Exception {
        PlumBlossomOntologyJobWorker w = new PlumBlossomOntologyJobWorker();
        Method f = m("shengKeTone", int.class, String.class);
        assertThat((String) call(f, w, 1, "乾")).as("乾金/乾金→比和").contains("比和");
        assertThat((String) call(f, w, 1, "坎")).as("乾金生坎水→逢生").contains("逢生");
        assertThat((String) call(f, w, 6, "离")).as("坎水克离火→见克").contains("见克");
        assertThat((String) call(f, w, 6, "乾")).as("乾金生坎水（体生之）→泄气").contains("泄气");
    }

    @Test
    @DisplayName("十应目论·向背：相向而来（向）/相背而去（背）")
    void xiangBeiNoteByDirection() throws Exception {
        Method f = m("xiangBeiNote", String.class);
        assertThat((String) call(f, null, "鸦飞适来")).as("来→向（灾祥将至）").contains("向").contains("将至");
        assertThat((String) call(f, null, "鸦飞而去")).as("去→背（灾祥已过）").contains("背").contains("已过");
        assertThat((String) call(f, null, "见喜鹊鸣于东")).as("无向背字→空").isEmpty();
    }

    @Test
    @DisplayName("十应目论·十应奥论：十应固出于三要而妙乎三要、须合内外卦而断")
    void tenResponsePrincipleGate() throws Exception {
        Method f = m("tenResponsePrinciple");
        assertThat((String) call(f, null)).as("十应奥论").contains("十应固出于三要").contains("合内外卦");
    }

    // ==========================================================================
    // 卷之三·占卜十应诀 + 坐端之诀 + 论事十大应（第十三轮 B50–B52）
    // ==========================================================================

    @Test
    @DisplayName("占卜十应诀：十应总目（正/互/变应等）＋结语（体旺衰/诸应生克）")
    void tenResponseJueGate() throws Exception {
        Method jn = m("tenResponseJueNote");
        String n = call(jn, null);
        assertThat(n).as("十应总目").contains("占卜十应诀").contains("正应").contains("变应").contains("人事应");
        Method js = m("tenResponseJueSummary", String.class);
        String s = call(js, null, "旺");
        assertThat(s).as("结语·体旺险中有救").contains("体旺则险中有救").contains("今体卦旺");
        assertThat((String) call(js, null, "死")).contains("体衰则无复生理");
    }

    @Test
    @DisplayName("占卜坐端之诀：八宫八方应兆（乾宫老人/兑宫少女）＋问病定位")
    void zuoDuanGate() throws Exception {
        Method f = m("zuoDuanNote", String.class);
        String q = (String) call(f, null, "坐于乾宫见土");
        assertThat(q).as("乾宫→老人尊长").contains("坐端八方应兆").contains("乾宫").contains("问病");
        assertThat((String) call(f, null, "在震位上见水")).as("震宫→长子长孙").contains("震宫");
        assertThat((String) call(f, null, "见喜鹊鸣于东")).as("无宫位→空").isEmpty();
    }

    @Test
    @DisplayName("论事十大应：无歧义姿态键（裹头/跣足/担/券/卧）照录断语")
    void shiDaYingGate() throws Exception {
        Method f = m("shiDaYingNote", String.class);
        assertThat((String) call(f, null, "见一人跣足而来")).as("跣足→八跣足").contains("论事十大应").contains("八跣足");
        assertThat((String) call(f, null, "见人裹头行")).as("裹头→七裹头").contains("七裹头");
        assertThat((String) call(f, null, "见担物者")).as("担→五担").contains("五担");
        assertThat((String) call(f, null, "见喜鹊鸣于东")).as("无姿态键→空").isEmpty();
    }

    // ==========================================================================
    // 卷之三·观物玄妙歌诀（第十四轮 B53）
    // ==========================================================================

    @Test
    @DisplayName("观物玄妙歌诀：逐卦物之形色性位体＋交互生克（乾刚圆/坎水中物/艮山石土瓦）")
    void objectMiaoJueByBody() throws Exception {
        PlumBlossomOntologyJobWorker w = new PlumBlossomOntologyJobWorker();
        Method f = m("objectDivinationText", String.class, String.class, int.class, String.class,
                String.class, String.class, String.class, String.class, String.class);
        // 乾为体 → 刚而圆，为金为玉
        String qian = (String) call(f, w, "占静物", "乾为天", 1, "乾", "乾", "", "", "", "");
        assertThat(qian).as("乾·歌诀").contains("观物玄妙歌诀（乾）").contains("刚而圆").contains("为金为玉");
        // 坎为体 → 水中之物
        String kan = (String) call(f, w, "占静物", "坎为水", 1, "坎", "坎", "", "", "", "");
        assertThat(kan).as("坎·歌诀").contains("观物玄妙歌诀（坎）").contains("多为水中之物");
        // 艮为体 → 山石土瓦（原刻讹「离」，据象系于艮）
        String gen = (String) call(f, w, "占静物", "艮为山", 1, "艮", "艮", "", "", "", "");
        assertThat(gen).as("艮·歌诀·山石土瓦").contains("观物玄妙歌诀（艮）").contains("为山石之物").contains("为门途之处");
        // 离为体 → 此本阙歌诀（宁阙勿滥）→ 不含该行
        String li = (String) call(f, w, "占静物", "离为火", 1, "离", "离", "", "", "", "");
        assertThat(li).as("离·歌诀阙").doesNotContain("观物玄妙歌诀（离）");
    }

    // ==========================================================================
    // 卷之三·卦应（第十四轮 B54）
    // ==========================================================================

    @Test
    @DisplayName("卦应：逐卦类象（乾禄壬申/离禄巳/兑少女媚悦）＋万物类象按体用补引")
    void guaYingByTrigram() throws Exception {
        Method f = m("guaYingNote", String.class);
        assertThat((String) call(f, null, "乾")).as("乾·卦应").contains("卦应（乾）").contains("禄壬申").contains("官贵朝贵");
        assertThat((String) call(f, null, "离")).as("离·卦应").contains("禄巳").contains("方所正南");
        assertThat((String) call(f, null, "兑")).as("兑·卦应").contains("少女媚悦").contains("方所西方");
        assertThat((String) call(f, null, "金")).as("非卦→空").isEmpty();
        // 万物类象（节点9）按体/用补引卦应
        PlumBlossomOntologyJobWorker w = new PlumBlossomOntologyJobWorker();
        Method g = m("imageSymbolText", String.class, String.class, String.class,
                String.class, String.class, String.class);
        String s = (String) call(g, w, "求财", "乾为天", "", "", "乾", "坤");
        assertThat(s).as("类象含卦应·体乾用坤").contains("卦应（乾）").contains("卦应（坤）");
    }

    // ==========================================================================
    // 卷之二·观物洞玄歌（占宅气）＋ 卷之二/三·体用衰旺之诀、体用动静之诀（第十五轮 B55–B57）
    // ==========================================================================

    @Test
    @DisplayName("观物洞玄歌·占宅气兆应（墙壁缺/鹊噪/群羊）＋衰旺之诀＋体用动静之诀")
    void dongXuanAndTiYongJue() throws Exception {
        // B55 观物洞玄歌（占宅气兆应）
        Method dx = m("dongXuanNote", String.class);
        assertThat((String) call(dx, null, "见门前墙壁缺")).as("墙壁缺→家道消歇")
                .contains("观物洞玄歌").contains("消歇");
        assertThat((String) call(dx, null, "清晨鹊噪连声")).as("鹊噪→远信行人至").contains("远信行人至");
        assertThat((String) call(dx, null, "入门见群羊")).as("群羊→家主病").contains("家主病");
        assertThat((String) call(dx, null, "风清月朗")).as("无兆→空").isEmpty();
        // B56 衰旺之诀
        PlumBlossomOntologyJobWorker w = new PlumBlossomOntologyJobWorker();
        Method sw = m("shuaiWangNote", String.class, String.class, String.class, String.class, String.class);
        assertThat((String) call(sw, w, "乾", "坤", "", "", "旺")).as("体旺→气旺逢生则吉、虽克无大害")
                .contains("衰旺之诀").contains("乘旺").contains("亦无大害");
        assertThat((String) call(sw, w, "乾", "坤", "", "", "死")).as("体衰有生体（坤土生乾金）→衰稍吉")
                .contains("衰稍吉");
        assertThat((String) call(sw, w, "乾", "离", "", "", "死")).as("体衰逢克（离火克乾金）→凶甚")
                .contains("其凶甚矣");
        // B57 体用动静之诀
        Method dj = m("dongJingNote", String.class);
        assertThat((String) call(dj, null, "行")).as("行则应速＋动而吉应速")
                .contains("体卦为静").contains("行则应速").contains("动而吉者应吉之速");
        assertThat((String) call(dj, null, "坐")).as("坐则应迟").contains("坐则应迟");
        assertThat((String) call(dj, null, "")).as("动静未详→仍申「不动不占」").contains("不动不占");
    }

    // ==========================================================================
    // 卷之三·万物赋·闻见克应诸兆 ＋ 应期歌（第十六轮 B58/B59）
    // ==========================================================================

    @Test
    @DisplayName("万物赋·闻见克应（喧闹/笑语/枷锁/披麻带孝/鸦鸣）＋应期歌（巽五日坤八日离三朝）")
    void wanWuGeGate() throws Exception {
        Method f = m("wanWuNote", String.class);
        assertThat((String) call(f, null, "闻远处喧闹")).as("喧闹→主有斗争").contains("万物赋").contains("主有斗争");
        assertThat((String) call(f, null, "听此间笑语")).as("笑语→必逢吉庆").contains("必逢吉庆");
        assertThat((String) call(f, null, "见枷锁临身")).as("枷锁→官灾").contains("枷锁临身");
        assertThat((String) call(f, null, "见人披麻带孝而过")).as("披麻带孝→孝服临头").contains("孝服临头");
        assertThat((String) call(f, null, "闻鸦鸣")).as("鸦鸣→祸事将临").contains("祸事将临");
        assertThat((String) call(f, null, "风清月朗")).as("无兆→空").isEmpty();
        Method yq = m("wanWuYingQiNote");
        assertThat((String) call(yq, null)).as("应期歌·巽五日坤八日离三朝")
                .contains("巽五日").contains("离三朝").contains("天地否");
    }

    // ==========================================================================
    // 卷之三·体用生克之诀·真生真克之辨（第十七轮 B60）
    // ==========================================================================

    @Test
    @DisplayName("体用生克之诀·外卦真生真克（金体真火/木体真金/火体真水＋生体分辨）")
    void zhenShengZhenKeJue() throws Exception {
        PlumBlossomOntologyJobWorker w = new PlumBlossomOntologyJobWorker();
        Method f = m("zhenShengZhenKeNote", String.class);
        String gold = (String) call(f, w, "乾");
        assertThat(gold).as("金体：真火能克、形色之火不能克")
                .contains("体用生克之诀").contains("真火").contains("形色").contains("炉中火");
        String wood = (String) call(f, w, "震");
        assertThat(wood).as("木体：钗钏琐屑之金不能克木、木忌快刀锐刃巨斧大锯")
                .contains("钗钏之金").contains("快刀锐刃");
        String fire = (String) call(f, w, "离");
        assertThat(fire).as("火体：真水能克、血湿黑色坎属终忌而不深害")
                .contains("见真水能克").contains("不深害");
        assertThat(gold).as("生体分辨：土生金而瓦不能生、真水生木福重而坎属生木轻")
                .contains("瓦不能生").contains("生木之类轻").contains("并以类而推之");
    }

    // ==========================================================================
    // 卷之二·先天后天论·应期取卦气（第十九轮 B61）
    // ==========================================================================

    @Test
    @DisplayName("先天后天论·应期取卦气（乾兑庚辛五金·震巽甲乙五木·后天卦数加时）")
    void xianHouTianYingQi() throws Exception {
        Method f = m("xianHouTianYingQiNote");
        String s = (String) call(f, null);
        assertThat(s).as("先天应期取卦气·乾兑庚辛五金、乾戌亥兑酉")
                .contains("先天后天论").contains("庚、辛及五金").contains("戌、亥").contains("兑为酉日时");
        assertThat(s).as("震巽甲乙五木·震卯巽辰").contains("甲、乙及五木").contains("震取卯");
        assertThat(s).as("后天以卦数加时数、分行卧坐立之迟速")
                .contains("后天则以卦数加时数").contains("行、卧、坐、立");
    }

    // ==========================================================================
    // 卷之二·三要灵应篇·各类之应（第二十轮 B62）
    // ==========================================================================

    @Test
    @DisplayName("三要灵应篇·各类之应（天文/人品/器物/草木/禽兽/叶音/自己/动静）")
    void sanYaoLingYingGe() throws Exception {
        Method f = m("sanYaoLingYingNote", String.class);
        String tian = (String) call(f, null, "忽见云开见日");
        assertThat(tian).as("天文之应·云开见日")
                .contains("三要灵应").contains("云开见日").contains("事必增辉");
        String renPin = (String) call(f, null, "偶见匠氏");
        assertThat(renPin).as("人品之应·匠氏主门庭改换")
                .contains("人品之应").contains("匠氏").contains("门庭改换");
        String qiWu = (String) call(f, null, "见张弓挟矢者");
        assertThat(qiWu).as("器物之应·张弓挟矢者必领荐")
                .contains("器物之应").contains("领荐");
        String qinShou = (String) call(f, null, "忽见蟢虫");
        assertThat(qinShou).as("禽兽之应·蟢虫报喜").contains("禽兽之应").contains("报喜");
        String dongJing = (String) call(f, null, "将叛者其辞惭");
        assertThat(dongJing).as("动静之应·将叛者其辞惭").contains("动静之应").contains("其辞惭");
        String none = (String) call(f, null, "今日出门遇友");
        assertThat(none).as("无对应兆象则返回空串").isEmpty();
    }

    // 卷之二·三要灵应篇·逐兆补录 + 正文导论与总论（第三十六轮）
    // ==========================================================================

    @Test
    @DisplayName("第 36 轮·三要灵应篇逐兆补录（地理/人品/近取诸身/器物/禽兽/杂见/自己/动静/占物克应）")
    void sanYaoLingYingBuLu() throws Exception {
        Method f = m("sanYaoLingYingNote", String.class);
        assertThat((String) call(f, null, "见石乃坚心始得")).as("地理·石乃").contains("坚心始得");
        assertThat((String) call(f, null, "沙乃放手即开")).as("地理·沙乃").contains("放手即开");
        assertThat((String) call(f, null, "逢猎者得野外之财")).as("人品·逢猎者").contains("野外之财");
        assertThat((String) call(f, null, "见渔者")).as("人品·见渔者").contains("水边之利");
        assertThat((String) call(f, null, "见衽妇")).as("人品·见衽妇").contains("事萌于内");
        assertThat((String) call(f, null, "遇丧者")).as("人品·遇丧者").contains("虑根于心");
        assertThat((String) call(f, null, "偶舌出掉者")).as("近取诸身·舌出掉者").contains("有是非");
        assertThat((String) call(f, null, "偶下膝者")).as("近取诸身·屈抑而求").contains("屈抑而求");
        assertThat((String) call(f, null, "见络绎将成")).as("器物·络绎").contains("可以问职");
        assertThat((String) call(f, null, "笔墨俱在")).as("器物·笔墨").contains("可以求文");
        assertThat((String) call(f, null, "奕棋者")).as("器物·奕棋").contains("取之以计");
        assertThat((String) call(f, null, "或持斧锯恐有伤")).as("器物·持斧锯").contains("恐有伤");
        assertThat((String) call(f, null, "忽见乌鸦")).as("禽兽·乌鸦报灾").contains("乌鸦报灾");
        assertThat((String) call(f, null, "见凉扇")).as("杂见·凉扇").contains("多主气捐");
        assertThat((String) call(f, null, "我适闲")).as("自己·我适闲").contains("彼当从容");
        assertThat((String) call(f, null, "我值忙")).as("自己·我值忙").contains("彼当窘迫");
        assertThat((String) call(f, null, "吉人之辞寡")).as("动静·吉人之辞寡").contains("吉人之辞寡");
        assertThat((String) call(f, null, "遇吉兆")).as("占物克应·吉兆").contains("顺有吉");
    }

    @Test
    @DisplayName("第 36 轮·三要灵应篇·正文导论与篇末总论（照录）")
    void sanYaoGaiLun() throws Exception {
        Method f1 = m("sanYaoLingYingGaiLunNote");
        String s1 = (String) call(f1, null);
        assertThat(s1).as("导论·三要定义").contains("运耳、目、心三者之要也").contains("灵妙而应验也");
        Method f2 = m("sanYaoLingYingZongLunNote");
        String s2 = (String) call(f2, null);
        assertThat(s2).as("总论·卦应吉凶").contains("卦吉而应吉终吉").contains("活法更存乎方寸");
    }

    // ==========================================================================
    // 卷之一·风觉占/鸟占/听声音占·断法（第二十一轮 B63）
    // ==========================================================================

    @Test
    @DisplayName("风觉占/鸟占/听声音占·断法（审时察色辨声势/听声察名义/辨物声察悲喜）")
    void fengJueNiaoTingJue() throws Exception {
        Method f = m("castMethodVerdictText", String.class);
        String feng = (String) call(f, null, "风觉占");
        assertThat(feng).as("风觉占·审时察色辨声势")
                .contains("风觉占").contains("审其时").contains("祥瑞之气").contains("如阵马")
                .contains("洋洋而来");
        String niao = (String) call(f, null, "鸟占");
        assertThat(niao).as("鸟占·听声音察名义")
                .contains("喧啾").contains("悲咽").contains("鸦报灾").contains("鹊报喜");
        String ting = (String) call(f, null, "听声音占");
        assertThat(ting).as("听声音占·辨物声察悲喜")
                .contains("声自口出者属兑").contains("土声属坤").contains("娱笑");
        String none = (String) call(f, null, "年月日时起例");
        assertThat(none).as("他法无此断法则返回空串").isEmpty();
    }

    // ==========================================================================
    // 卷之二·疾病占第十六·「乾上坤下」六爻动逐爻断病之例（第二十二轮 B64）
    // ==========================================================================

    @Test
    @DisplayName("疾病占·乾上坤下六爻动逐爻断病之例（天地否·逐爻断/焚尸/扛尸/余卦仿此）")
    void diseaseYaoGe() throws Exception {
        Method f = m("diseaseYaoNote", String.class, int.class);
        String ku2 = (String) call(f, null, "天地否", 2);
        assertThat(ku2).as("天地否·二爻动·变坎水·焚尸之象")
                .contains("乾上坤下").contains("【今动】二爻动").contains("变为坎水")
                .contains("焚尸之象").contains("断之死无疑").contains("余卦皆仿此断");
        String ku1 = (String) call(f, null, "天地否", 1);
        assertThat(ku1).as("天地否·初爻动·变震木·逢生之日即愈")
                .contains("【今动】初爻动").contains("变为震木").contains("逢生之日即愈");
        String ku4 = (String) call(f, null, "天地否", 4);
        assertThat(ku4).as("天地否·四爻动·乾变巽木·扛尸砖椁")
                .contains("【今动】四爻动").contains("乾变巽木").contains("扛尸").contains("砖椁");
        String other = (String) call(f, null, "乾为天", 3);
        assertThat(other).as("非天地否之卦：仍录该法（余卦仿此），但不标【今动】")
                .contains("余卦皆仿此断").doesNotContain("【今动】");
    }

    // ==========================================================================
    // 卷之三·体用互变之诀·体之互/用之互、体互最紧（第二十二轮 B65）
    // ==========================================================================

    @Test
    @DisplayName("体用互变之诀·互卦分体之互/用之互（天地否·体在上/体在下两判；乾坤无互返空）")
    void mutualBodyUseGe() throws Exception {
        PlumBlossomOntologyJobWorker w = new PlumBlossomOntologyJobWorker();
        Method f = m("mutualBodyUseNote", String.class, String.class, int.class);
        String kuUpper = call(f, w, "天地否", "乾", 2); // 动爻2≤3 → 体在上（乾）=体之互取上互巽、用之互取下互艮
        assertThat(kuUpper).as("体在上：上互为体之互、下互为用之互")
                .contains("体用互变之诀").contains("体互最紧")
                .contains("体在上，上互为体之互、下互为用之互")
                .contains("体之互「巽」").contains("（此为先取）")
                .contains("用之互「艮」").contains("（此为次取）")
                .contains("变克体则末后不吉");
        String kuLower = call(f, w, "天地否", "坤", 4); // 动爻4≥4 → 体在下（坤）=体之互取下互艮、用之互取上互巽
        assertThat(kuLower).as("体在下：下互为体之互、上互为用之互")
                .contains("体在下，下互为体之互、上互为用之互")
                .contains("体之互「艮」").contains("用之互「巽」");
        String none = call(f, w, "乾为天", "乾", 3);
        assertThat(none).as("乾坤无互（互其变卦）→ 本诀不适用，返空").isEmpty();
    }

    // ==========================================================================
    // 卷之二·体用类·心易生克之例（不论体用之相制）（第二十二轮 B66）
    // ==========================================================================

    @Test
    @DisplayName("体用类·心易生克之例：金多制木（无风）/ 土多制水（无雨）/ 水多制火（不晴）")
    void tiYongLeiRestraintGe() throws Exception {
        Method f = m("tiYongLeiRestraint", int.class, int.class, int.class, int.class,
                int.class, int.class, int.class, int.class);
        // 乾3、兑1、震0、巽2 → 金4≥2 且 木2>0 → 制木无风
        assertThat((String) call(f, null, 3, 1, 0, 2, 0, 0, 0, 0))
                .as("金多制木").contains("不论体用之相制").contains("克震、巽之木").contains("无风");
        // 坎1、艮2、坤3 → 土5≥2 且 水1>0 → 制水无雨
        assertThat((String) call(f, null, 0, 0, 0, 0, 1, 0, 2, 3))
                .as("土多制水").contains("克坎水").contains("无雨");
        // 坎2、离1 → 水多制火 → 不晴
        assertThat((String) call(f, null, 0, 0, 0, 0, 2, 1, 0, 0))
                .as("水多制火").contains("克离火").contains("不晴");
        // 无党多 → 空
        assertThat((String) call(f, null, 1, 0, 1, 0, 1, 0, 1, 0)).as("无相制→空").isEmpty();
        // 接入天时占：泽天夬＋乾为天＋巽为风（金4木2）→ 天时断含相制
        Method g = m("tianShiVerdict", String.class, String.class, String.class);
        assertThat((String) call(g, null, "泽天夬", "乾为天", "巽为风"))
                .as("天时占并观相制").contains("天时之应").contains("体用类").contains("无风");
    }

    @Test
    @DisplayName("体用类·占饮食相制：遇坤艮则坎亦无酒、离值则兑亦无食")
    void dietRestraintGe() throws Exception {
        PlumBlossomOntologyJobWorker w = new PlumBlossomOntologyJobWorker();
        Method f = m("dietNote", String.class, String.class, String.class, String.class);
        String kan = call(f, w, "坎", "艮", "", "");
        assertThat(kan).as("有坎遇艮→坎亦无酒").contains("有坎则有酒").contains("遇坤、艮，则坎亦无酒");
        String dui = call(f, w, "兑", "离", "", "");
        assertThat(dui).as("有兑遇离→兑亦无食").contains("有兑则有食").contains("离值则兑亦无食");
    }

    // ==========================================================================
    // 附录·邵康节易数一撮金·爻诀（64卦×3爻＝192首）（第二十三轮 B67）
    // ==========================================================================

    @Test
    @DisplayName("一撮金·爻诀内置表：64卦×3爻＝192首、六等齐备（DB 同源兜底）")
    void yicuoJinYaoTable() throws Exception {
        java.lang.reflect.Field fld = PlumBlossomOntologyJobWorker.class.getDeclaredField("YICUOJIN_YAO");
        fld.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, String[]> tab = (Map<String, String[]>) fld.get(null);
        assertThat(tab).as("六十四卦齐备").hasSize(64);
        int n = 0;
        for (String[] arr : tab.values()) {
            for (int i = 1; i <= 6; i++) {
                if (arr[i] != null) {
                    n++;
                }
            }
        }
        assertThat(n).as("爻诀共192首").isEqualTo(192);
        assertThat(tab.get("乾为天")[2]).as("乾为天九二·上上")
                .isEqualTo("上上|九二二爻|得意宜逢贵，前程去有缘。利名终有望，三五月团圆。");
        assertThat(tab.get("乾为天")[4]).as("乾为天九四·中中").contains("中中|").contains("欲行不止");
        assertThat(tab.get("乾为天")[6]).as("乾为天上九·下下").contains("下下|").contains("心戚戚");
        assertThat(tab.get("天泽履")[3]).as("天泽履六三·下下").contains("下下|").contains("桃李谢春风");
        assertThat(tab.get("天地否")[5]).as("天地否九五·下下").contains("下下|").contains("身不安，心不安");
    }

    @Test
    @DisplayName("一撮金·以爻诀断之：按本卦动爻取爻诀断辞（附六等吉凶；非法输入返空）")
    void yicuoJinYaoNoteText() throws Exception {
        PlumBlossomOntologyJobWorker w = new PlumBlossomOntologyJobWorker();
        Method f = m("yicuoJinYaoNote", String.class, int.class);
        String q2 = call(f, w, "乾为天", 2);
        assertThat(q2).as("乾为天九二·上上之爻诀")
                .contains("一撮金·爻诀断法").contains("本卦乾为天").contains("九二二爻")
                .contains("得「上上」").contains("得意宜逢贵").contains("乃以爻诀断之");
        String lu3 = call(f, w, "天泽履", 3);
        assertThat(lu3).as("天泽履六三·下下之爻诀").contains("得「下下」").contains("桃李谢春风");
        assertThat((String) call(f, w, "乾为天", 1)).as("该卦未列之爻（乾为天初爻）返空").isEmpty();
        assertThat((String) call(f, w, "乾为天", 7)).as("越界返空").isEmpty();
        assertThat((String) call(f, w, "", 2)).as("空卦名返空").isEmpty();
        assertThat((String) call(f, w, null, 2)).as("null 卦名返空").isEmpty();
    }

    @Test
    @DisplayName("相字·断法内置表：卷之四/卷之五 18类共220条齐备（DB 同源兜底）")
    void xiangZiRuleTable() throws Exception {
        java.lang.reflect.Field fld = PlumBlossomOntologyJobWorker.class.getDeclaredField("XIANGZI_RULE");
        fld.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, Map<String, String>> tab = (Map<String, Map<String, String>>) fld.get(null);
        assertThat(tab).as("相字类目 57 类（十八 ＋ 第二十五轮残卷三十九）").hasSize(57);
        int n = 0;
        for (Map<String, String> mm : tab.values()) {
            n += mm.size();
        }
        assertThat(n).as("相字条目共 263 条").isEqualTo(263);
        assertThat(tab).containsKeys("六神笔式", "起六神卦诀", "五行笔式", "八卦断", "六神形式",
                "五行全备", "神煞", "笔画歌诀", "玄黄克应歌", "玄黄笔法歌", "辨别五行歌",
                "辨别六神歌", "八卦辨", "五行歌并式", "相字短诀", "分类断法", "字例", "赋文",
                // 第二十五轮·待考落地：卷之五残卷诸篇 ＋ 卷之四干支辨
                "六十四卦次第歌", "系辞八卦类象歌", "八反格", "四言独步", "五言作用歌",
                "别理篇", "六言剖断歌", "格物章", "物理论", "五行六神辨别论", "金声章",
                "浑天甲子定局", "后天时方", "干支辨",
                // 第二十五轮续：卷四余篇 ＋ 卷五余篇
                "齐景至理论", "字画经验", "字体诗诀", "四季水笔", "画有阴阳", "笔法筌蹄",
                "奴婢", "阴人", "隔手", "笔画长短", "笔画犯煞",
                "七言作用歌", "比例歌", "西江月", "易理玄微", "断扇占", "买香占", "古人相字",
                "断富贵贫贱要诀", "五行相生地支", "天干地支所属五行", "论八卦性情", "八卦取象",
                "六十甲子纳音", "五行四时旺相休囚表");
        assertThat(tab.get("字体诗诀").get("总诀")).contains("天字及二人").contains("青字事未顺");
        assertThat(tab.get("字画经验").get("总诀")).contains("敷字").contains("春字");
        assertThat(tab.get("七言作用歌").get("总诀")).isNotEmpty();
        assertThat(tab.get("赋文").get("指迷赋")).as("指迷赋全录").contains("略说根源，以示后学");
        assertThat(tab.get("赋文").get("探玄赋")).as("探玄赋全录").contains("造其理也，即此推之");
        assertThat(tab.get("赋文").get("花押赋")).as("花押赋全录").contains("搜六神而无不验矣");
        assertThat(tab.get("四言独步").get("总诀")).contains("看字之法，毫不可差").contains("始精相字");
        assertThat(tab.get("物理论").get("总诀")).contains("三才始判，八卦攸分").contains("鬼谷子曰");
        assertThat(tab.get("金声章").get("总诀")).contains("混沌未开，一元含于太极").contains("字相之微");
        assertThat(tab.get("干支辨")).as("干支辨 5 段").hasSize(5);
        assertThat(tab.get("干支辨").get("甲乙")).contains("直长为甲亦为寅").contains("车字中央一直");
        assertThat(tab.get("六十四卦次第歌").get("总诀")).contains("乾坤屯蒙需讼师").contains("是为下经三十四");
        assertThat(tab.get("六神笔式")).as("六神笔式 6 条").hasSize(6);
        assertThat(tab.get("六神笔式").get("青龙")).contains("蚕头燕额").contains("主喜事");
        assertThat(tab.get("起六神卦诀").get("甲")).as("甲乙起青龙").isEqualTo("青龙");
        assertThat(tab.get("五行笔式").get("水")).contains("水圆多性巧");
        assertThat(tab.get("八卦断").get("乾")).contains("乾宫笔法如鸡脚");
        assertThat(tab.get("八卦辨").get("总诀")).contains("口形为兑捺为乾").contains("三点同来方是坎");
        assertThat(tab.get("神煞").get("贵神")).contains("中、上、贝、日、月、大、人");
        assertThat(tab.get("笔画歌诀")).as("笔画歌诀 40 条").hasSize(40);
        assertThat(tab.get("笔画歌诀").get("笔画稳重")).isEqualTo("衣食丰隆");
        assertThat(tab.get("玄黄克应歌")).as("玄黄克应歌 45 条").hasSize(45);
        assertThat(tab.get("玄黄克应歌").get("鹊声")).isEqualTo("听得鹊声云有喜");
        assertThat(tab.get("分类断法").get("疾病")).contains("金笔多").contains("木笔多").contains("水笔多");
        assertThat(tab.get("相字短诀").get("相字心易")).contains("凡写两字，止看一字");
        assertThat(tab.get("赋文").get("指迷赋")).contains("相人不如相字").contains("字，心画也");
    }

    @Test
    @DisplayName("附录·内置同源兜底表：爻诀192／渔樵98＋宋史10／八卦诀8宫×8（与 DB 同源）")
    void appendixFallbackTables() throws Exception {
        java.lang.reflect.Field yf = PlumBlossomOntologyJobWorker.class.getDeclaredField("YICUOJIN_YAO");
        yf.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, String[]> yao = (Map<String, String[]>) yf.get(null);
        assertThat(yao).as("一撮金爻诀 64 卦").hasSize(64);
        int yn = 0;
        for (String[] arr : yao.values()) {
            for (String s : arr) {
                if (s != null) {
                    yn++;
                }
            }
        }
        assertThat(yn).as("爻诀共 192 首（64×3）").isEqualTo(192);
        assertThat(yao.get("乾为天")[2]).as("乾为天九二·上上").contains("上上").contains("得意宜逢贵");

        java.lang.reflect.Field af = PlumBlossomOntologyJobWorker.class.getDeclaredField("APPENDIX_TEXT");
        af.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, String[]> at = (Map<String, String[]>) af.get(null);
        assertThat(at).as("附录文献 108 则（渔樵 98＋宋史 10）").hasSize(108);
        assertThat(at).containsKeys("ATXT_YQ_01", "ATXT_YQ_98", "ATXT_SY_01", "ATXT_SY_10");
        assertThat(at.get("ATXT_YQ_01")[2]).as("渔樵首则").contains("渔者垂钓于伊水之上");

        java.lang.reflect.Field bf = PlumBlossomOntologyJobWorker.class.getDeclaredField("BA_GUA_JUE");
        bf.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, String[]> bgj = (Map<String, String[]>) bf.get(null);
        assertThat(bgj).as("八卦诀 八宫").hasSize(8);
        int bn = 0;
        for (String[] arr : bgj.values()) {
            bn += arr.length;
        }
        assertThat(bn).as("八卦诀 64 卦（8×8）").isEqualTo(64);
        assertThat(bgj.get("乾")).startsWith("乾为天").contains("天地否");
    }

    @Test
    @DisplayName("卷之一·象数易理开篇诸篇照录（B176）：周易卦数/五行生克/八宫所属五行/卦气旺/卦气衰/十天干/十二地支/八卦象例")
    void juanYiXiangShuOpening() throws Exception {
        java.lang.reflect.Field f = PlumBlossomOntologyJobWorker.class.getDeclaredField("JUAN_YI_XIANG_SHU");
        f.setAccessible(true);
        String s = (String) f.get(null);
        assertThat(s).as("非空").isNotBlank();
        assertThat(s).as("篇名齐全").contains("周易卦数").contains("五行生克")
                .contains("八宫所属五行").contains("卦气旺").contains("卦气衰")
                .contains("十天干").contains("十二地支").contains("八卦象例");
        assertThat(s).as("五行生克（底本五格表·行序连书）")
                .contains("金生水水生木木生火火生土土生金；金克木木克土土克水水克火火克金。");
        assertThat(s).as("八宫所属五行").contains("乾、兑金，坤、艮土，震、巽木，坎水，离火。");
        assertThat(s).as("卦气旺").contains("震、巽木旺于春。离火旺于夏。乾、兑金旺于秋。坎水旺于冬。坤、艮土旺于辰戌丑未月。");
        assertThat(s).as("卦气衰").contains("春坤、艮。夏乾、兑。秋震、巽。冬离。辰戌丑未坎。");
        assertThat(s).as("十天干").contains("甲乙东方木。丙丁南方火。戊己中央土。庚辛西方金。壬癸北方水。");
        assertThat(s).as("十二地支").contains("子水鼠，丑土牛，寅木虎，卯木兔，辰土龙，巳火蛇，午火马，未土羊，申金猴，酉金鸡，戌土犬，亥水猪。");

        Method note = m("juanYiXiangShuNote");
        String n = call(note, null);
        assertThat(n).as("note 接入").contains("卷之一·象数易理开篇").contains("图象存目");
    }

    @Test
    @DisplayName("卷之一·八卦万物类占篇名与篇末按语照录（B177）")
    void juanYiBaGuaLeiZhanAn() throws Exception {
        java.lang.reflect.Field f = PlumBlossomOntologyJobWorker.class.getDeclaredField("JUAN_YI_BA_GUA_LEI_ZHAN_AN");
        f.setAccessible(true);
        String s = (String) f.get(null);
        assertThat(s).as("篇末按语").isEqualTo(
                "右万物之象，庶事之多不止于此，占者宜各以其类而推之耳。");
        Method note = m("juanYiBaGuaLeiZhanAnNote");
        String n = call(note, null);
        assertThat(n).as("note 接入").contains("八卦万物类占").contains("ABox");
    }

    @Test
    @DisplayName("第46轮·图象补录（卷之一/三/四/五 整页图象据别本回补，B178）：九图齐全、出处标注、非底本照录")
    void tuXiangBuLu() throws Exception {
        java.lang.reflect.Field fld = PlumBlossomOntologyJobWorker.class.getDeclaredField("TU_XIANG_BU_LU");
        fld.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, String[]> tab = (Map<String, String[]>) fld.get(null);
        assertThat(tab).as("图象补录 9 图").hasSize(9);
        assertThat(tab.keySet()).as("图号齐全").contains(
                "TX_ZHOUYI_GUASHU", "TX_BAGUA_XIANGLI", "TX_BAGUA_FANGWEI_1", "TX_BAGUA_FANGWEI_3",
                "TX_BIAN_GUA_SHI", "TX_QI_LIU_SHEN", "TX_WU_XING_SI_SHI",
                "TX_HUN_TIAN_JIA_ZI", "TX_HOU_TIAN_SHI_FANG");
        assertThat(tab.get("TX_ZHOUYI_GUASHU")[2]).as("周易卦数·先天八卦数")
                .contains("乾，一；兑，二；离，三；震，四；巽，五；坎，六；艮，七；坤，八。");
        assertThat(tab.get("TX_BAGUA_XIANGLI")[2]).as("八卦象例").contains("乾三连").contains("巽下断");
        assertThat(tab.get("TX_QI_LIU_SHEN")[2]).as("六神附例").contains("甲乙日例").contains("玄武").contains("青龙");
        assertThat(tab.get("TX_WU_XING_SI_SHI")[2]).as("五行四时旺相休囚")
                .contains("春：金囚、木旺、水休、火相、土死").contains("当令者旺，我生者相");
        assertThat(tab.get("TX_HUN_TIAN_JIA_ZI")[2]).as("纳甲（艮·丙寅木，非丙寅水）")
                .contains("艮：丙寅木、丙子水").contains("以上四宫属阳，皆从顺数").contains("以上四宫属阴，皆从逆数。");
        assertThat(tab.get("TX_HOU_TIAN_SHI_FANG")[2]).as("后天时方诀")
                .contains("子日子罡起，灭迹四位中").contains("子罡、丑墓");
        for (String[] v : tab.values()) {
            assertThat(v[3]).as("出处标注非空").isNotBlank();
        }
        Method note = m("tuXiangBuLuNote");
        String n = call(note, null);
        assertThat(n).as("note 接入").contains("图象补录").contains("周易卦数").contains("乾三连").contains("丙寅木");
        assertThat(n).as("非底本照录声明").contains("非底本照录");
    }

    @Test
    @DisplayName("相字·取象：依所书之字/兆应于分类断法/短诀/神煞/玄黄克应中取断（无命中返空）")
    void xiangZiNoteText() throws Exception {
        Method f = m("xiangZiNote", String.class);
        String disease = call(f, null, "问疾病，见土木，字带金笔");
        assertThat(disease).as("命中分类断法·疾病").contains("相字·分类断法·疾病").contains("金笔多");
        String shen = call(f, null, "字中见贵神");
        assertThat(shen).as("命中神煞·贵神").contains("相字·神煞·贵神");
        String xh = call(f, null, "忽见鹊声");
        assertThat(xh).as("命中玄黄克应·鹊声").contains("相字·玄黄克应歌·鹊声").contains("有喜");
        assertThat((String) call(f, null, "无关之语")).as("无命中返空").isEmpty();
        assertThat((String) call(f, null, "")).as("空串返空").isEmpty();
        assertThat((String) call(f, null, (Object) null)).as("null 返空").isEmpty();
    }

    @Test
    @DisplayName("第二十五轮·论事十大应补足十键（一行/二立/三坐/九喜/十怒）")
    void shiDaYingTenKeys() throws Exception {
        java.lang.reflect.Field fld = PlumBlossomOntologyJobWorker.class.getDeclaredField("SHI_DA_YING");
        fld.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, String> tab = (Map<String, String>) fld.get(null);
        assertThat(tab).as("论事十大应 10 键").hasSize(10);
        Method f = m("shiDaYingNote", String.class);
        assertThat((String) call(f, null, "一行：问官司")).as("一行").contains("论事十大应").contains("一行").contains("文书");
        assertThat((String) call(f, null, "二立")).as("二立").contains("二立").contains("官司不发");
        assertThat((String) call(f, null, "三坐")).as("三坐").contains("三坐").contains("有讼不成");
        assertThat((String) call(f, null, "九喜")).as("九喜").contains("九喜").contains("求财不许");
        assertThat((String) call(f, null, "十怒")).as("十怒").contains("十怒").contains("倚人脱卸");
        assertThat((String) call(f, null, "见一人跣足而来")).as("旧键八跣足仍在").contains("八跣足");
    }

    @Test
    @DisplayName("第二十五轮·日应/鸟兽时日兆/写字之应/加姓/器物占等待考诸条齐备")
    void daikaoLanding() throws Exception {
        Method ri = m("riYingNote", String.class);
        assertThat((String) call(ri, null, "寅")).as("寅→木日应").contains("日应").contains("本日属木");
        assertThat((String) call(ri, null, "午")).as("午→火").contains("本日属火");
        assertThat((String) call(ri, null, "戌")).as("戌→土").contains("本日属土");
        assertThat((String) call(ri, null, "")).as("无日支返空").isEmpty();

        Method ns = m("niaoShouShiRiNote", String.class);
        assertThat((String) call(ns, null, "庚")).as("庚日鸡鸣凶刃").contains("鸟兽时日兆").contains("庚日见鸡鸣").contains("凶刃之杀");
        assertThat((String) call(ns, null, "壬")).as("壬日猪过食禄").contains("壬日有猪过").contains("食禄之兆");
        assertThat((String) call(ns, null, "甲")).as("未列之日返空").isEmpty();

        Method xz = m("xieZiZhiYingNote", String.class);
        assertThat((String) call(xz, null, "淡中浓墨名为淬")).as("写字之应").contains("复明写字之应").contains("鬼笔");
        assertThat((String) call(xz, null, "无关")).as("无兆返空").isEmpty();

        Method jx = m("jiaXingNote", String.class, int.class);
        assertThat((String) call(jx, null, "王", 4)).as("加姓起卦").contains("起卦加数例").contains("二十").contains("震为上卦");
        assertThat((String) call(jx, null, "", 0)).as("无姓亦陈总诀").contains("田姓").contains("婚姻");

        assertThat((String) call(m("qiWuZhanText"), null)).as("器物占").contains("器物占").contains("兑为毁折");
        assertThat((String) call(m("wuZhaiZhanText"), null)).as("屋宅之占诀").contains("屋宅之占诀").contains("全卦六卦之成数");
        assertThat((String) call(m("bianGuaShiText"), null)).as("变卦式八则").contains("变卦式八则").contains("近取诸身");
        assertThat((String) call(m("objectDaikaoText", String.class), null, "占动物")).as("占物类补断").contains("器物占").contains("看物数");
        assertThat((String) call(m("objectDaikaoText", String.class), null, "字占")).as("非占物类返空").isEmpty();
    }

    @Test
    @DisplayName("第二十五轮·诸卦反对性情补「随前坎后」（照录备考）")
    void oppositeNatureSui() throws Exception {
        assertThat((String) call(m("hexagramOppositeNature", String.class), null, "泽雷随"))
                .as("泽雷随·偷安").contains("偷安").contains("随前坎后");
    }

    @Test
    @DisplayName("第二十六轮·附录文献（渔樵对问/宋史·邵雍传）全录落地")
    void appendixLanding() throws Exception {
        java.lang.reflect.Field fld = PlumBlossomOntologyJobWorker.class.getDeclaredField("APPENDIX_TEXT");
        fld.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, String[]> tab = (Map<String, String[]>) fld.get(null);
        assertThat(tab).as("附录二篇 108 段（渔樵 98 ＋ 邵雍传 10）").hasSize(108);
        assertThat(tab).containsKey("ATXT_YQ_01").containsKey("ATXT_YQ_98").containsKey("ATXT_SY_01").containsKey("ATXT_SY_10");

        String doctrine = (String) call(m("appendixDoctrineNote"), null);
        assertThat(doctrine).as("义理提要").contains("渔樵对问").contains("98").contains("以物观物")
                .contains("宋史·邵雍传").contains("10").contains("先天之旨");

        Method an = m("appendixTextNote", String.class);
        assertThat((String) call(an, null, "渔樵对问")).as("渔樵").contains("全录 98 段");
        assertThat((String) call(an, null, "宋史·邵雍传")).as("邵雍传").contains("全录 10 段");
        assertThat((String) call(an, null, "一撮金")).as("非附录二篇返空").isEmpty();

        boolean hasGuanWu = false;
        boolean hasDiYu = false;
        for (String[] v : tab.values()) {
            if (v[2].contains("以物观物") && v[2].contains("不以我观物")) { hasGuanWu = true; }
            if (v[2].contains("渔者垂钓于伊水之上")) { hasDiYu = true; }
        }
        assertThat(hasGuanWu).as("『以物观物』原文在位").isTrue();
        assertThat(hasDiYu).as("渔樵对问首则原文在位").isTrue();
    }

    @Test
    @DisplayName("第二十七轮·附录·八卦诀（京房八宫卦序 8宫×8卦）全录落地")
    void baGuaJueLanding() throws Exception {
        java.lang.reflect.Field fld = PlumBlossomOntologyJobWorker.class.getDeclaredField("BA_GUA_JUE");
        fld.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, String[]> tab = (Map<String, String[]>) fld.get(null);
        assertThat(tab).as("京房八宫").hasSize(8);
        for (String[] v : tab.values()) {
            assertThat(v).as("每宫八卦").hasSize(8);
        }
        assertThat(tab.get("乾")).as("乾宫（本宫＋一世～五世＋游魂＋归魂）")
                .containsExactly("乾为天", "天泽履", "天火同人", "天雷无妄", "天风姤", "天水讼", "天山遁", "天地否");
        assertThat(tab.get("坤")).as("坤宫")
                .containsExactly("坤为地", "地天泰", "地泽临", "地火明夷", "地雷复", "地风升", "地水师", "地山谦");

        String txt = (String) call(m("baGuaJueText"), null);
        assertThat(txt).as("八卦诀全录").contains("乾宫").contains("乾为天").contains("坤宫").contains("地山谦");

        Method p = m("baGuaJuePalace", String.class);
        assertThat((String) call(p, null, "天风姤")).as("天风姤属乾宫").isEqualTo("乾");
        assertThat((String) call(p, null, "地水师")).as("地水师属坤宫").isEqualTo("坤");
        assertThat((String) call(p, null, "乾为天")).as("乾为天属乾宫").isEqualTo("乾");
        assertThat((String) call(p, null, "无此卦")).as("不在名录返空").isEmpty();

        String doctrine = (String) call(m("appendixDoctrineNote"), null);
        assertThat(doctrine).as("附录提要含八卦诀").contains("八卦诀").contains("8宫×64卦");
        assertThat((String) call(m("appendixTextNote", String.class), null, "八卦诀")).as("八卦诀提要")
                .contains("八宫各统八卦");
    }

    @Test
    @DisplayName("第二十七轮·卷之一八卦所属内外动静之图（第三张类象表）全录落地")
    void neiWaiDongJingLanding() throws Exception {
        java.lang.reflect.Field fld = PlumBlossomOntologyJobWorker.class.getDeclaredField("NEI_WAI_DONG_JING");
        fld.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, String> tab = (Map<String, String>) fld.get(null);
        assertThat(tab).as("八卦内外动静").hasSize(8);
        assertThat(tab.get("乾")).as("乾").contains("玄黄").contains("天鹅").contains("刚物");
        assertThat(tab.get("兑")).as("兑").contains("有口缺之物").contains("金刃");
        assertThat(tab.get("坤")).as("坤").contains("釜").contains("布帛");

        Method n = m("neiWaiDongJingNote", String.class);
        assertThat((String) call(n, null, "坎")).as("坎").contains("水带子").contains("带核之物");
        assertThat((String) call(n, null, "巽")).as("巽").contains("山木之禽鸟").contains("工巧之器");
        assertThat((String) call(n, null, "无")).as("非经卦返空").isEmpty();
    }

    @Test
    @DisplayName("第二十七轮·卷之三观梅数诀序（序文＋先天八卦数）落地")
    void guanMeiShuJueLanding() throws Exception {
        String note = (String) call(m("guanMeiShuJueNote"), null);
        assertThat(note).as("观梅数诀序").contains("观梅数诀").contains("易有太极")
                .contains("乾一、兑二、离三、震四、巽五、坎六、艮七、坤八")
                .contains("存目");

        String xu = (String) call(m("sanYaoXuNote"), null);
        assertThat(xu).as("三要灵应篇序").contains("性理之学").contains("运耳、目、心三者之虚灵")
                .contains("清灵子朱虚拜首序");
    }

    @Test
    @DisplayName("第二十八轮·书序/占法玩法/占卜克应之诀/遗论 落地")
    void round28Landing() throws Exception {
        String xu = (String) call(m("meiHuaYiShuXuNote"), null);
        assertThat(xu).as("梅花易数序").contains("康节邵先生隐处山林").contains("以数起卦，故曰先天")
                .contains("以卦起数，故曰后天").contains("秣陵德聚堂谨订");

        String shi = (String) call(m("zhanFaWanFaNote"), null);
        assertThat(shi).as("占法/玩法").contains("易中秘密穷天地").contains("一物从来有一身")
                .contains("道不虚传只在人");

        String ke = (String) call(m("zhanBuKeYingJueNote"), null);
        assertThat(ke).as("占卜克应之诀").contains("克应者").contains("有以数而刻之者")
                .contains("有以理而刻之者").contains("学者审焉");

        String yi = (String) call(m("yiLunNote"), null);
        assertThat(yi).as("遗论").contains("未有《易》书，先有《易》理").contains("此要外应诀之");

        String guaDuan = (String) call(m("guaDuanYiLunNote"), null);
        assertThat(guaDuan).as("卦断遗论").contains("不拘体用").contains("西林寺额得山地剥")
                .contains("不可拘于执一");

        String xinYi = (String) call(m("baGuaXinYiTiYongJueNote"), null);
        assertThat(xinYi).as("八卦心易体用诀").contains("得之者众，体用之诀，有之者罕")
                .contains("分体用之卦");
    }

    private static Object field(String name) throws Exception {
        java.lang.reflect.Field f = PlumBlossomOntologyJobWorker.class.getDeclaredField(name);
        f.setAccessible(true);
        return f.get(null);
    }

    @Test
    @DisplayName("第三十轮·原文引录逐字校验：标点/引号/漏句回归")
    void round30VerbatimLanding() throws Exception {
        // ① 原文常量须与底本逐字相符（引号用底本 “ ”，不用 「」）
        String[] originals = {"ZHAN_WU_YAO_CI", "GUAN_WU_YONG_YI", "YICUOJIN_ALLOC", "SAN_YAO_XU",
                "MEI_HUA_YI_SHU_XU", "GUAN_MEI_SHU_JUE_SEQ", "ZHAN_FA", "WAN_FA",
                "ZHAN_BU_KE_YING_JUE", "YI_LUN", "GUA_DUAN_YI_LUN", "BA_GUA_XIN_YI_TI_YONG_JUE"};
        for (String n : originals) {
            String v = (String) field(n);
            assertThat(v).as("原文常量 %s 不得混用「」（底本作 “ ”）", n)
                    .doesNotContain("「").doesNotContain("」");
        }

        // ② 补回被漏录的引句「其色玄黄」并订正标点
        String zhanWu = (String) field("ZHAN_WU_YAO_CI");
        assertThat(zhanWu).as("看爻辞断物·漏句已补").contains("黄色衣服之物，“其色玄黄”。“困为石”")
                .contains("又言爻辞，不言物类，而不能决者，须以八卦所属之象察之。");

        // ③ 观物用易例·回归底本标点（。／：／—— 复原）
        String guanWu = (String) field("GUAN_WU_YONG_YI");
        assertThat(guanWu).as("观物用易例").contains("此必是草木类而生土中也。色青根黄")
                .contains("盖爻辞曰：“拔茅连茹，以其汇。”乃曰：此乃干根之草木也")
                .contains("新采于土中也。互震为青色").contains("其色白而可用。盖其辞曰：“鼎玉铉，大吉。”互见乾、兑")
                .contains("开视之，乃玉绦环，果破矣。");

        // ④ 一撮金起例·回归底本句读
        String yc = (String) field("YICUOJIN_ALLOC");
        assertThat(yc).as("一撮金起例").contains("信手写二字。数其笔画，即一点一撇亦算。")
                .contains("坤八。九复起乾，十复起兑").contains("共成一卦。即以总数六除取爻");

        // ⑤ 序文末款不加句号（底本无）
        assertThat((String) field("SAN_YAO_XU")).as("三要灵应篇序末").endsWith("清灵子朱虚拜首序")
                .doesNotEndWith("。");
        assertThat((String) field("MEI_HUA_YI_SHU_XU")).as("梅花易数序末").endsWith("秣陵德聚堂谨订")
                .doesNotEndWith("。");

        // ⑥ 散见引文（Map 值）亦逐字合底本
        Map<?, ?> dongXuan = (Map<?, ?>) field("DONG_XUAN_GE");
        assertThat((String) dongXuan.get("鹏鸟")).as("洞玄歌·鹏鸟").contains("（即鸱鸮也）");
        Map<?, ?> wanWu = (Map<?, ?>) field("WAN_WU_GE");
        assertThat((String) wanWu.get("枷锁")).as("万物赋·枷锁").contains("或逢枷锁而枷锁临身");
        assertThat((String) wanWu.get("鞭杖")).as("万物赋·鞭杖").contains("倘遇鞭杖而鞭杖必至");
        assertThat((String) wanWu.get("鼓乐")).as("万物赋·鼓乐").contains("其或鼓乐声喧，又见酒杯器皿，若不迎婚嫁娶");
        assertThat((String) wanWu.get("酒杯器皿")).as("万物赋·酒杯器皿").contains("又见酒杯器皿，若不迎婚嫁娶");
    }

    @Test
    @DisplayName("第三十七轮·十应目论总纲与占卜十应诀逐应照录回归")
    void round37ShiYingLanding() throws Exception {
        // ① 卷之二·十应目论·总纲（此前未录）
        String mu = (String) field("SHI_YING_MU_LUN");
        assertThat(mu).as("十应目论·总纲").contains("十应并以体卦为主，诸用卦为用")
                .contains("内卦不吉而外卦又吉，可以解其不吉；内卦吉而外卦不吉，反破其吉")
                .contains("慎不可胶柱鼓瑟也").contains("其十一类之应，并以体卦为主，而随其所应以为用也")
                .doesNotContain("「").doesNotContain("」");

        // ② 卷之三·占卜十应诀·〔方应〕（照录）
        String fang = (String) field("ZHAN_BU_SHI_YING_FANG");
        assertThat(fang).as("占卜十应诀·方应").startsWith("〔方应〕方应者，以体为主")
                .contains("卦宜生体卦，又宜与体比和，则吉；如克体卦则凶；如体卦生之，亦不吉矣。");

        // ③ 卷之三·占卜十应诀·〔刻应〕〔外应〕〔天时应〕〔地理应〕〔人事应〕（照录）
        String yu = (String) field("ZHAN_BU_SHI_YING_YU");
        assertThat(yu).as("占卜十应诀·逐应").contains("〔刻应〕刻应者，即三要之诀也")
                .contains("〔外应〕外应者，外卦之应也").contains("如火得离、水得坎之类")
                .contains("〔天时应〕天时之应，占卜之际，晴明为离，雨雪为坎，风为巽，雷为震")
                .contains("〔地理应〕地理之应，凡占卜之时，偶在竹林间，为震、巽之地")
                .contains("〔人事应〕人事之应，即三要中人事之克应也")
                .contains("老人为乾，老妇为坤，少男为艮，少女为兑");

        // ④ 接入节点 11 之 note 方法输出
        assertThat((String) call(m("shiYingMuLunNote"), null)).as("十应目论 note")
                .startsWith("十应目论——").contains("其十一类之应");
        assertThat((String) call(m("zhanBuShiYingJueNote"), null)).as("十应诀逐应 note")
                .contains("〔方应〕").contains("〔刻应〕").contains("〔人事应〕");

        // ⑤ 卷之二·三要灵应篇附属诸论（照录，B112–B117）
        assertThat((String) field("SHI_YING_AO_LUN")).as("十应奥论")
                .contains("十应固出于三要，而妙乎三要").contains("是占卜物者，不可无十应也")
                .doesNotContain("「").doesNotContain("」");
        assertThat((String) field("TI_YONG_LUN")).as("体用")
                .contains("即画成三重：本卦、互卦、变卦也").contains("所谓体一用百也")
                .contains("此又非外应之兆不能决也");
        assertThat((String) field("TI_YONG_LEI_LUN")).as("体用类")
                .contains("心易寓物之用，以体为主").contains("体用不可无三要，十应不可无体用")
                .contains("为心易生克之例耳");
        assertThat((String) field("SHUAI_WANG_LUN")).as("衰旺论")
                .contains("既明生克，当看衰旺").contains("此心易论衰旺之诀也");
        assertThat((String) field("NEI_WAI_LUN")).as("内外论")
                .contains("体用为内，诸应卦为外卦").contains("则内卦不可无外卦，外卦不可无内卦");
        assertThat((String) field("DONG_JING_LUN")).as("动静")
                .contains("阳动而阴静").contains("不动不占，不因事不占").contains("可以知动静之机矣");
        assertThat((String) field("XIANG_BEI_LUN")).as("向背")
                .contains("必须审其向背").contains("此为占卜向背至当之理矣");
        assertThat((String) field("JING_ZHAN_LUN")).as("静占")
                .contains("凡占在静室，无所闻见，则无外卦").startsWith("静占");

        // ⑥ 观物洞玄歌·引言与跋（照录）
        assertThat((String) field("DONG_XUAN_YIN")).as("洞玄歌引言")
                .contains("洞玄歌者，洞达玄妙之说也").contains("切勿以浅近目之也");
        assertThat((String) field("DONG_XUAN_BA")).as("洞玄歌跋")
                .contains("右《洞玄歌》与《灵应》，同出而小异").contains("则善恶不逃乎明鉴矣");
        assertThat((String) call(m("juanErZhuLunNote"), null)).as("卷之二诸论 note")
                .contains("十应奥论").contains("动静").contains("静占");
        assertThat((String) call(m("dongXuanYinBaNote"), null)).as("洞玄歌序跋 note")
                .contains("洞玄歌者").contains("同出而小异");
    }

    @Test
    @DisplayName("第三十八轮·卷之一占例十则（先天五例＋后天五例）照录回归")
    void round38JuanYiZhanLiLanding() throws Exception {
        // ① 先天五例：观梅占 / 牡丹占 / 邻夜扣门借物占 / 今日动静如何 / 西林寺牌额占（照录）
        String xian = (String) field("JUAN_YI_XIAN_TIAN_LI");
        assertThat(xian).as("卷之一·先天五占例")
                .startsWith("观梅占（年月日时占例）辰年十二月十七日申时")
                .contains("见二雀争枝坠地").contains("明晚当有女子折花").contains("是为泽火革")
                .contains("牡丹占巳年三月十六日卯时").contains("当为马所践毁").contains("乾为马也")
                .contains("邻夜扣门借物占（系闻声占例）").contains("初扣一声而止，继而又扣五声")
                .contains("非锄必斧也").contains("推数又须明理，为卜占之切要也")
                .contains("今日动静如何（系声音占例）").contains("味止鸡黍而已").contains("坤为黍稷耳")
                .contains("西林寺牌额占（系字画占例）").contains("“林”字无两勾").contains("寺中当有阴人之祸")
                .endsWith("所谓先天之数也。")
                .doesNotContain("「").doesNotContain("」");

        // ② 后天五例：老人有忧色占 / 少年有喜色占 / 牛哀鸣占 / 鸡悲鸣占 / 枯枝坠地占（照录）
        String hou = (String) field("JUAN_YI_HOU_TIAN_LI");
        assertThat(hou).as("卷之一·后天五占例")
                .startsWith("老人有忧色占（端法占例）己丑日卯时")
                .contains("有老人往巽方，有忧色").contains("是天风姤").contains("因鱼骨鲠而终")
                .contains("右凡占卜，克应之期看自己之动静").contains("坐则事应迟，当倍其成卦之数而定之也")
                .contains("少年有喜色占").contains("得山火贲").contains("果然定亲")
                .contains("牛哀鸣占").contains("牛属坤，为上卦，坎方为下卦").contains("必遭屠杀")
                .contains("鸡悲鸣占").contains("鸡属巽，为上卦，乾方为下卦").contains("有烹饪之象")
                .contains("枯枝坠地占").contains("得火泽睽").contains("此树十日当伐")
                .endsWith("所谓后天之数也。")
                .doesNotContain("「").doesNotContain("」");

        // ③ 接入节点 16 之 note 方法输出
        String note = (String) call(m("juanYiZhanLiNote"), null);
        assertThat(note).as("卷之一占例 note")
                .startsWith("卷之一·占例（先天之数）——").contains("观梅占").contains("牡丹占")
                .contains("卷之一·占例（后天之数）——").contains("老人有忧色占").contains("枯枝坠地占");
    }

    @Test
    @DisplayName("第三十八轮·卷之一（起卦法/风觉鸟占）与卷之二（体用总诀/十八占/复明诸应/洞玄歌/加数屋宅）照录回归")
    void round38JuanYiJuanErExtraLanding() throws Exception {
        // ① 卷之一·起卦法全篇
        String qgfa = (String) field("JUAN_YI_QI_GUA_FA");
        assertThat(qgfa).as("卷之一·起卦法")
                .startsWith("卦数起例卦以八除").contains("爻以六除")
                .contains("互卦止用八卦不必取六十四卦名").contains("年月日时起例")
                .contains("声音占凡闻声音").contains("字占凡见字数")
                .contains("一字占一字为太极未判").contains("十一字占十一字以上")
                .contains("物卦起例（端法后天起卦之例）").contains("八卦万物属类")
                .contains("八卦方位图右离南坎北")
                .endsWith("凡物之从花甲来，并起作下卦，加时取爻。")
                .doesNotContain("「").doesNotContain("」");
        // ② 卷之一·风觉鸟占全篇
        String fjn = (String) field("JUAN_YI_FENG_JUE_NIAO");
        assertThat(fjn).as("卷之一·风觉鸟占")
                .startsWith("风觉鸟占风觉鸟占者，谓见风而觉")
                .contains("风觉占风觉占者").contains("鸟占鸟占者，见鸟可占也")
                .contains("听声音占声音者").contains("形物占形物占者").contains("验色占凡占色之青者属震")
                .contains("八卦所属内外动静之图")
                .endsWith("有口缺之物、羊。");
        // ③ 卷之二·体用总诀（含逐卦生体/克体）
        String tz = (String) field("JUAN_ER_TI_YONG_ZONG_JUE");
        assertThat(tz).as("卷之二·体用总诀")
                .startsWith("体用总诀体用云者").contains("体克用，诸事吉；用克体，诸事凶")
                .contains("乾卦生体，则主公门中有喜益").contains("乾卦克体，主有公事之扰")
                .endsWith("生克不逢，止随本卦而论之。");
        // ④ 卷之二·十八占
        String sb = (String) field("JUAN_ER_SHI_BA_ZHAN");
        assertThat(sb).as("卷之二·十八占")
                .startsWith("天时占第一凡占天时，不分体用").contains("占交易，以体为主")
                .contains("占行人，以体为主").contains("占谒见，以体为主")
                .contains("疾病占第十六").contains("官讼占第十七").contains("坟墓占第十八")
                .endsWith("占者以类而推之可也。");
        // ⑤ 卷之二·复明诸应
        String fm = (String) field("JUAN_ER_FU_MING_ZHU_YING");
        assertThat(fm).as("卷之二·复明诸应")
                .startsWith("复明天时之应如天无云翳").contains("复明地理之应").contains("复明人事之应")
                .contains("复明时令之应").contains("复明方卦之应").contains("复明动物之应")
                .contains("复明静物之应").contains("复明言语之应").contains("复明声音之应")
                .contains("复明五色之应").contains("复明写字之应")
                .endsWith("此写字之应验也。");
        // ⑥ 卷之二·观物洞玄歌正文
        assertThat((String) field("JUAN_ER_DONG_XUAN_GE")).as("洞玄歌正文")
                .startsWith("世间万事无非数").contains("斯文引路入先天，深奥入玄玄");
        // ⑦ 卷之二·起卦加数例·屋宅占·器物占
        assertThat((String) field("JUAN_ER_QI_GUA_JIA_SHU")).as("起卦加数例·屋宅")
                .startsWith("起卦加数例寅年十二月初一").contains("屋宅之占诀")
                .contains("器物占大抵占器物").endsWith("以日数断之也。");
        // ⑧ note 方法
        assertThat((String) call(m("juanYiQiGuaFaNote"), null)).as("起卦法 note").contains("卦数起例");
        assertThat((String) call(m("juanErShiBaZhanNote"), null)).as("十八占 note").contains("天时占第一");
        assertThat((String) call(m("juanErFuMingNote"), null)).as("复明诸应 note").contains("复明天时之应");
    }

    @Test
    @DisplayName("第三十九轮·卷之三·断占总诀逐篇照录回归（21 篇全录）")
    void round39JuanSanLanding() throws Exception {
        // ① 八卦定阴阳次序（乾统三男·坤统三女＋图象残注）
        assertThat((String) field("JUAN_SAN_BA_GUA_DING_YIN_YANG")).as("八卦定阴阳次序")
                .startsWith("八卦定阴阳次序乾为父震长男坎中男艮少男坤为母巽长女离中女兑少女")
                .contains("相生极美比和次之体用于变爻，作动静取之。动者为用，静者为体")
                .endsWith("此是艮二卦。").doesNotContain("「").doesNotContain("」");
        // ② 变卦式八则（逐例）
        assertThat((String) field("JUAN_SAN_BIAN_GUA_SHI_BA_ZE")).as("变卦式八则")
                .startsWith("变卦式八则泽火革变泽山咸卦").contains("得艮土生入兑金。断曰：不至于此")
                .contains("地雷复变地泽临").contains("泽天夬变兑").contains("天水讼卦变兑")
                .contains("近取诸身").contains("远取诸物")
                .endsWith("其日午时，客来食去酒，返自消耗也。");
        // ③ 占卦诀
        assertThat((String) field("JUAN_SAN_ZHAN_GUA_JUE")).as("占卦诀")
                .startsWith("占卦诀又如占卦而问吉事").contains("生体是用卦，则事即成就")
                .contains("若乾克体，阻一日，兑克体，阻二日之类推之")
                .endsWith("要在活法取用也。");
        // ④ 体用互变之诀 / 生克之诀 / 衰旺之诀 / 动静之诀
        assertThat((String) field("JUAN_SAN_TI_YONG_HU_BIAN")).as("体用互变之诀")
                .startsWith("体用互变之诀大凡占卜，以体为其主").contains("此体互用之分")
                .endsWith("此用互变之诀也。");
        assertThat((String) field("JUAN_SAN_TI_YONG_SHENG_KE")).as("体用生克之诀")
                .startsWith("体用生克之诀占卦即以卦分体用互变").contains("真火能克金，形色则不能克")
                .contains("土能生金，瓦不能生也").endsWith("其余五行生克，并以类而推之。");
        assertThat((String) field("JUAN_SAN_TI_YONG_SHUAI_WANG")).as("体用衰旺之诀")
                .startsWith("体用衰旺之诀凡体卦宜乘旺").contains("若问病必死，问讼必败")
                .endsWith("必须详其盛衰也。");
        assertThat((String) field("JUAN_SAN_TI_YONG_DONG_JING")).as("体用动静之诀")
                .startsWith("体用动静之诀占卦体用互变既分").contains("不动不占，亦不断")
                .endsWith("此皆动静之理也。");
        // ⑤ 占卜坐端之诀
        assertThat((String) field("JUAN_SAN_ZHAN_BU_ZUO_DUAN")).as("占卜坐端之诀")
                .startsWith("占卜坐端之诀坐端者，以我之所坐为中").contains("坤宫见克，病在腹")
                .contains("推之震足、巽股、离目、坎耳及血、艮手指、兑口齿")
                .endsWith("此引其端为之例也。");
        // ⑥ 万物赋 / 饮食篇
        assertThat((String) field("JUAN_SAN_WAN_WU_FU")).as("万物赋")
                .startsWith("万物赋人禀阴阳，卦分先后").contains("未成卦以前，必虚心而求应")
                .contains("巽五日而坤八日，离三朝而坎六朝").contains("逢柴炭主忧，折麦主悲")
                .endsWith("能达此者，尚其秘诸。");
        assertThat((String) field("JUAN_SAN_YIN_SHI_PIAN")).as("饮食篇")
                .startsWith("饮食篇夫乾之为象也，圆坚而味辛").contains("艮为土物同烹，离乃火边煎炙")
                .contains("坎为水象也").contains("震之为卦，木属也").contains("兑之为卦，其属白金")
                .endsWith("取其日时，以互卦用矣。");
        // ⑦ 观物玄妙歌诀 / 诸事响应歌 / 诸卦反对性情
        assertThat((String) field("JUAN_SAN_GUAN_WU_XUAN_MIAO_GE")).as("观物玄妙歌诀")
                .startsWith("观物玄妙歌诀观物戏验者").contains("故乾之为卦，刚而圆，贵而坚")
                .contains("坤之为卦，其形直而方").contains("兑之为卦，其色白，其性少柔而多刚")
                .endsWith("得柔而成器也。");
        assertThat((String) field("JUAN_SAN_ZHU_SHI_XIANG_YING_GE")).as("诸事响应歌")
                .startsWith("诸事响应歌混沌开辟立人极").contains("大抵体宜用卦生，旺相谋为终有益")
                .contains("咸远恒迟升不回，艮阻坎险君须记").contains("若问墓穴在何地，坤则平阳巽林里")
                .endsWith("略举片言通万类。");
        assertThat((String) field("JUAN_SAN_ZHU_GUA_FAN_DUI")).as("诸卦反对性情")
                .startsWith("诸卦反对性情乾刚坤柔反其义").contains("革去旧故鼎从新")
                .endsWith("杂卦性情反对是。");
        // ⑧ 占物类例 / 体用断物之妙 / 物数为体诀 / 观物诸诀 / 万物戏念
        assertThat((String) field("JUAN_SAN_ZHAN_WU_LEI_LI")).as("占物类例")
                .startsWith("占物类例凡看物数，看其成卦，观其爻辞").contains("“潜龙勿用”")
                .contains("“困于株林”，乃曰木物").endsWith("须以八卦所属之象察之。又诀");
        assertThat((String) field("JUAN_SAN_TI_YONG_DUAN_WU")).as("体用断物之妙")
                .startsWith("体用断物之妙生克制化之妙").contains("比和者，乃有用成器之物")
                .contains("所泄为废物也").endsWith("后天亦不出八数之外。");
        assertThat((String) field("JUAN_SAN_WU_SHU_WEI_TI")).as("物数为体诀")
                .startsWith("物数为体诀凡算物数者").contains("如乾金多，以金为体，则多刚；坤多以土为体，多柔")
                .endsWith("非金非石，此为体矣。");
        assertThat((String) field("JUAN_SAN_GUAN_WU_ZHU_JUE")).as("观物诸诀")
                .startsWith("观物有体互变卦").contains("阳爻多，乃多刚之物；阴爻多，乃多柔之物")
                .contains("观物看变爻为主").contains("观物克应法")
                .contains("观物趣时诀").contains("观物用易例").contains("拔茅连茹，以其汇")
                .endsWith("乃玉绦环，果破矣。");
        assertThat((String) field("JUAN_SAN_WAN_WU_XI_NIAN")).as("万物戏念")
                .startsWith("万物戏念数中不可常为之").contains("凡猜手中物，乾金为圆白之物")
                .endsWith("值六虚冲破，必无物而空手矣。");
        // ⑨ 占卜十应诀全篇 / 论事十大应 / 卦应全篇
        assertThat((String) field("JUAN_SAN_ZHAN_BU_SHI_YING_JUE")).as("占卜十应诀")
                .startsWith("占卜十应诀凡占卜，以体卦为主，用为事应")
                .contains("有正应、互应、变应、方应、日应、刻应、外应、天时应、地理应、人事应")
                .contains("正应者，即体用二卦决吉凶").contains("日应者，以体卦为主")
                .contains("刻应者，即三要之诀也").contains("右十应之理")
                .endsWith("其余占卜，并以类推之。");
        assertThat((String) field("JUAN_SAN_LUN_SHI_SHI_DA_YING")).as("论事十大应")
                .startsWith("论事十大应（论日辰秘文）").contains("一行：问官司，属木")
                .contains("五担：官司被人自惊").contains("十怒：官司主外人欺凌")
                .endsWith("火惊病凶。");
        assertThat((String) field("JUAN_SAN_GUA_YING")).as("卦应")
                .startsWith("卦应（与前八卦属类大同小异，观者可以互参。）乾为天、为圜、为君父")
                .contains("乾性温而刚直，偏位西北，不居子午而居戌亥")
                .contains("坤为地、为母、为布").contains("震为雷、为龙").contains("巽为风、为木")
                .contains("坎为水、为沟渎").contains("离为火、为日").contains("艮为山、为少男")
                .contains("兑为泽、为少女").contains("［策］：一百九十二。［轨］：七百三十六。");
        // ⑩ 接入节点 16 之 note 方法输出
        assertThat((String) call(m("juanSanZhanGuaJueNote"), null)).as("占卦诀 note")
                .startsWith("卷之三·占卦诀").contains("要在活法取用也");
        assertThat((String) call(m("juanSanGuaYingNote"), null)).as("卦应 note")
                .startsWith("卷之三·卦应全篇").contains("乾为天");
        assertThat((String) call(m("juanSanZhanBuShiYingJueNote"), null)).as("占卜十应诀 note")
                .contains("正应者，即体用二卦决吉凶");
    }

    @Test
    @DisplayName("第四十轮·卷之四·字画指迷 成篇照录回归（8 组全录）")
    void round40JuanSiLanding() throws Exception {
        assertThat((String) field("JUAN_SI_ZI_HUA_ZHI_MI_XU")).as("JUAN_SI_ZI_HUA_ZHI_MI_XU")
                .startsWith("字画指迷夫先天者，已露之机；后天").contains("神，精微入圣。自古结绳为政，如今")
                .endsWith("而相之。略说根源，以示后学。").doesNotContain("「").doesNotContain("」");
        assertThat((String) field("JUAN_SI_XUAN_HUANG")).as("JUAN_SI_XUAN_HUANG")
                .startsWith("玄黄克应歌玄者，天也；黄者，地也").contains("。玄黄叙龟形未判，此为太古之淳风")
                .endsWith("止须勤记取，灾祥议论觉风生。").doesNotContain("「").doesNotContain("」");
        assertThat((String) field("JUAN_SI_FU")).as("JUAN_SI_FU")
                .startsWith("花押赋夫押字者，人之心印也。古人").contains("他人。霜叶辞柯，飘零不由自己。画")
                .endsWith("疑于预见，后之学者幸珍重之。").doesNotContain("「").doesNotContain("」");
        assertThat((String) field("JUAN_SI_ZI_HUA_JING_YAN")).as("JUAN_SI_ZI_HUA_JING_YAN")
                .startsWith("字画经验敷字：昔在任宰，请折之，").contains("无祖产，当知有五成。小人生不足，")
                .endsWith("坑陷并尖缺，妻子骄奢保守难。").doesNotContain("「").doesNotContain("」");
        assertThat((String) field("JUAN_SI_XIANG_ZI_XIN_YI")).as("JUAN_SI_XIANG_ZI_XIN_YI")
                .startsWith("相字心易凡写两字，止看一字。盖字").contains("，荣显通达。笔画气势，慷慨意志。")
                .endsWith("体骨苏黄格，淡有精神是贵郎。").doesNotContain("「").doesNotContain("」");
        assertThat((String) field("JUAN_SI_DUAN_FA")).as("JUAN_SI_DUAN_FA")
                .startsWith("视势每遇人写来，必别是何字，如“").contains("亦心也。一寸乃十分，为人有十分之")
                .endsWith("阳人不可写阴字。皆反常故也。").doesNotContain("「").doesNotContain("」");
        assertThat((String) field("JUAN_SI_LIU_SHEN")).as("JUAN_SI_LIU_SHEN")
                .startsWith("六神笔法“乀”：青龙木。“乂”：").contains("蛇土力隔伏玄黄笔法歌厂、反——反")
                .endsWith("声。旺事宜重用，取谋合有成。").doesNotContain("「").doesNotContain("」");
        assertThat((String) field("JUAN_SI_WU_XING")).as("JUAN_SI_WU_XING")
                .startsWith("五行体格式水笔式水圆多性巧。浊者").contains("假如庐字撇长，则取为丙火、巳火用")
                .endsWith("”，取江河在下义也。余仿此。").doesNotContain("「").doesNotContain("」");
    }

    @Test
    @DisplayName("第四十一轮·卷之五·拆字杂编 成篇照录回归（14 组全录）")
    void round41JuanWuLanding() throws Exception {
        assertThat((String) field("JUAN_WU_WU_XING_QUAN_BEI")).as("JUAN_WU_WU_XING_QUAN_BEI")
                .startsWith("拆字杂编五行全备一点一画五行全，").contains("、丿“尖短交加朱雀神”。撇短而有")
                .endsWith("八卦知凶吉，学者参求理自全。").doesNotContain("「").doesNotContain("」");
        assertThat((String) field("JUAN_WU_SHEN_SHA")).as("JUAN_WU_SHEN_SHA")
                .startsWith("贵神中上贝日月大人喜神士口言鸟福").contains("所属是科名，如甲乙以一直，丙人以")
                .endsWith("主，详其旺相休囚以定吉凶。）").doesNotContain("「").doesNotContain("」");
        assertThat((String) field("JUAN_WU_QI_YAN_ZUO_YONG_GE")).as("JUAN_WU_QI_YAN_ZUO_YONG_GE")
                .startsWith("七言作用歌用神加直五行真，谋望营").contains("木逢金克，缠身病疾不明萌。又木形")
                .endsWith("若是为官客，几日新鲜一旦倾。").doesNotContain("「").doesNotContain("」");
        assertThat((String) field("JUAN_WU_BI_LI_GE_XI_JIANG_YUE")).as("JUAN_WU_BI_LI_GE_XI_JIANG_YUE")
                .startsWith("比例歌一斗日来占事不差，无心书鬼").contains("定其间事必徐。问失执金知是铁，始")
                .endsWith("逢王者世为奇，君免猜疑直示。").doesNotContain("「").doesNotContain("」");
        assertThat((String) field("JUAN_WU_ZHAN_LI")).as("JUAN_WU_ZHAN_LI")
                .startsWith("易理玄微昔李淳风占赤黑二马入河，").contains("曰：“自今十四日必来。”刘曰：“")
                .endsWith("者富。五行生克亦然。余仿此。").doesNotContain("「").doesNotContain("」");
        assertThat((String) field("JUAN_WU_BIAO")).as("JUAN_WU_BIAO")
                .startsWith("五行四时旺相休囚表五行相生地支木").contains("蒙需讼师，比小畜兮履泰否。同人大")
                .endsWith("生。问官官不谐，见财财不成。").doesNotContain("「").doesNotContain("」");
        assertThat((String) field("JUAN_WU_SI_YAN_DU_BU")).as("JUAN_WU_SI_YAN_DU_BU")
                .startsWith("四言独步看字之法，毫不可差。下笔").contains("蓬。金得炉锤，方成器皿。木无金制")
                .endsWith("非一日。仔细详占，万无一失。").doesNotContain("「").doesNotContain("」");
        assertThat((String) field("JUAN_WU_WU_YAN_ZUO_YONG_GE")).as("JUAN_WU_WU_YAN_ZUO_YONG_GE")
                .startsWith("五言作用歌断事不可泥，变通方是道").contains("舌凶。用神见腾蛇，俱是文书动。功")
                .endsWith("神。先定吉凶主，然后字中寻。").doesNotContain("「").doesNotContain("」");
        assertThat((String) field("JUAN_WU_BIE_LI_PIAN")).as("JUAN_WU_BIE_LI_PIAN")
                .startsWith("别理篇字义浑论，辨别之篇须下学。").contains("西南北，欲就其方。左右中前，乃择")
                .endsWith("关悠远，须认精粗为化造之原。").doesNotContain("「").doesNotContain("」");
        assertThat((String) field("JUAN_WU_LIU_YAN_POU_DUAN_GE")).as("JUAN_WU_LIU_YAN_POU_DUAN_GE")
                .startsWith("六言剖断歌事从天地之义，字乃圣贤").contains("精神，英雄定于骨格。末后一笔丰隆")
                .endsWith("学者变化细推，断事无不灵应。").doesNotContain("「").doesNotContain("」");
        assertThat((String) field("JUAN_WU_GE_WU_ZHANG")).as("JUAN_WU_GE_WU_ZHANG")
                .startsWith("格物章物格而后知致，本末须详。事").contains("来，来而复去。文章书籍，非小人用")
                .endsWith("其重焉，非其道焉，孰轻与尔？").doesNotContain("「").doesNotContain("」");
        assertThat((String) field("JUAN_WU_WU_LI_LUN")).as("JUAN_WU_WU_LI_LUN")
                .startsWith("物理论三才始判，八卦攸分。万物不").contains("社稷自有人求，关津诚为客阻。烟雾")
                .endsWith("我听。”旨哉斯言！胡可忽诸。").doesNotContain("「").doesNotContain("」");
        assertThat((String) field("JUAN_WU_WU_XING_LIU_SHEN_LUN")).as("JUAN_WU_WU_XING_LIU_SHEN_LUN")
                .startsWith("五行六神辨别论先以五行为主，次向").contains("有勾陈，难结案头文。见朱雀，忖量")
                .endsWith("发。密搜奥义规绳，不爽纤微。").doesNotContain("「").doesNotContain("」");
        assertThat((String) field("JUAN_WU_JIN_SHENG_ZHANG")).as("JUAN_WU_JIN_SHENG_ZHANG")
                .startsWith("金声章混沌未开，一元含于太极。无").contains("叱之即便去；鸡鱼鹅鸭，欲用则不生")
                .endsWith("欲造字相之微，请明章中之理。").doesNotContain("「").doesNotContain("」");
    }

    @Test
    @DisplayName("第四十二轮·附录 逐篇核校回归（一撮金/八卦诀/渔樵/宋史 全录）")
    void round42AppendixLanding() throws Exception {
        // 一撮金·起卦法（本法照录）
        assertThat((String) field("YICUOJIN_ALLOC")).as("一撮金起卦法")
                .startsWith("其法以楷书字，数其笔画，以起数得卦。")
                .contains("即一点一撇亦算。").contains("上字为内卦，下字为外卦，合二字之画共成一卦。")
                .endsWith("乃以爻诀断之，无不奇中。不可再占。");
        // 一撮金·爻诀 64 卦 × 3 爻 = 192 首
        java.lang.reflect.Field fYao = PlumBlossomOntologyJobWorker.class.getDeclaredField("YICUOJIN_YAO");
        fYao.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, String[]> yao = (Map<String, String[]>) fYao.get(null);
        assertThat(yao).as("爻诀 64 卦").hasSize(64);
        int yaoN = 0;
        for (String[] v : yao.values()) {
            assertThat(v).as("每卦七槽（下标＝爻位 1..6）").hasSize(7);
            for (int i = 1; i <= 6; i++) {
                if (v[i] != null) {
                    yaoN++;
                }
            }
        }
        assertThat(yaoN).as("爻诀 64 卦 × 3 爻 = 192 首").isEqualTo(192);
        // 八卦诀·京房八宫 8 宫 × 8 卦
        java.lang.reflect.Field fBg = PlumBlossomOntologyJobWorker.class.getDeclaredField("BA_GUA_JUE");
        fBg.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, String[]> bgj = (Map<String, String[]>) fBg.get(null);
        assertThat(bgj).as("八卦诀八宫").hasSize(8);
        for (String[] v : bgj.values()) {
            assertThat(v).as("每宫八卦").hasSize(8);
        }
        assertThat(bgj.get("坤")).as("坤宫末卦").containsExactly(
                "坤为地", "地天泰", "地泽临", "地火明夷", "地雷复", "地风升", "地水师", "地山谦");
        // 附录文献（渔樵对问 98 则 ＋ 宋史·邵雍传 10 段）
        java.lang.reflect.Field fAt = PlumBlossomOntologyJobWorker.class.getDeclaredField("APPENDIX_TEXT");
        fAt.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, String[]> at = (Map<String, String[]>) fAt.get(null);
        long yq = at.values().stream().filter(v -> "渔樵对问".equals(v[0])).count();
        long sy = at.values().stream().filter(v -> "宋史·邵雍传".equals(v[0])).count();
        assertThat(yq).as("渔樵对问 98 则").isEqualTo(98);
        assertThat(sy).as("宋史·邵雍传 10 段").isEqualTo(10);
        assertThat(at.get("ATXT_SY_01")[2]).as("邵雍传首段照录")
                .startsWith("邵雍字尧夫。其先范阳人").contains("遂为河南人。");
        assertThat(at.get("ATXT_SY_10")[2]).as("邵雍传末段照录").contains("诗曰《伊川击壤集》。");
        assertThat(at.get("ATXT_YQ_98")[2]).as("渔樵对问末则照录").contains("及旦而去。");
    }

    @Test
    @DisplayName("第四十三轮·全书再核：卷之二开篇四论＋三要灵应篇·拆字之应段 照录回归")
    void round43WholeBookLanding() throws Exception {
        assertThat((String) field("JUAN_ER_XIN_YI_XUAN_JI")).as("心易占卜玄机")
                .startsWith("心易占卜玄机天下之事有吉凶")
                .contains("得变通之道者，在乎心易之妙耳。");
        assertThat((String) field("JUAN_ER_ZHAN_BU_ZONG_JUE")).as("占卜总诀")
                .startsWith("占卜总诀大抵占卜之法")
                .contains("先看《周易》爻辞，以断吉凶").contains("要在圆机，不可执滞。");
        assertThat((String) field("JUAN_ER_ZHAN_BU_LUN_LI")).as("占卜论理诀")
                .startsWith("占卜论理诀数说当也").contains("则占卜之道无余蕴矣。");
        assertThat((String) field("JUAN_ER_XIAN_TIAN_HOU_TIAN")).as("先天后天论")
                .startsWith("先天后天论先天卦断吉凶")
                .contains("故后天起卦定爻必加时而后可").contains("并于《周易》不相干涉，不可用也。");
        assertThat((String) field("SAN_YAO_CHAI_ZI")).as("三要灵应篇·拆字之应")
                .startsWith("若见物形，可知字体")
                .contains("石逢皮则破").contains("右为拆字之应。");
    }

    private static int[] parseRaw(String raw) {
        String[] p = raw.split("\\|");
        return new int[]{Integer.parseInt(p[0].trim()), Integer.parseInt(p[1].trim()), Integer.parseInt(p[2].trim())};
    }
}
