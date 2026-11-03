package com.ocean.ontologyframework.ziweidoushu;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 紫微斗数 · 内置安星/庙陷/神煞表口径自检（不需 MySQL / Ontop / Camunda）。
 *
 * <p>锁定本轮《全書》符合性审查结论：卷二·安紫微定位棋盘图 / 安南北斗諸星訣（紫府系偏移）/
 * 星辰庙陷 / 定十二宫星辰落闲 / 安飞天三杀诀。表值经反射读取生产类的既有内置表与查表方法。
 */
@DisplayName("紫微斗数 · 内置表口径自检（卷二·安星/庙陷/神煞）")
class ZiWeiDouShuStarTableTest {

    private static Object field(String name) throws Exception {
        Field f = ZiWeiDouShuOntologyJobWorker.class.getDeclaredField(name);
        f.setAccessible(true);
        return f.get(null);
    }

    private static String brightnessOf(String star, int branchIdx) throws Exception {
        Method m = ZiWeiDouShuOntologyJobWorker.class
                .getDeclaredMethod("brightnessOf", String.class, int.class);
        m.setAccessible(true);
        return (String) m.invoke(null, star, branchIdx);
    }

    private static boolean isIdleStar(String star, int branchIdx) throws Exception {
        Method m = ZiWeiDouShuOntologyJobWorker.class
                .getDeclaredMethod("isIdleStar", String.class, int.class);
        m.setAccessible(true);
        return (Boolean) m.invoke(null, star, branchIdx);
    }

    private static boolean inIdleTable(String star) throws Exception {
        Method m = ZiWeiDouShuOntologyJobWorker.class.getDeclaredMethod("inIdleTable", String.class);
        m.setAccessible(true);
        return (Boolean) m.invoke(null, star);
    }

    private static String[] idleVerseOf(String star) throws Exception {
        Method m = ZiWeiDouShuOntologyJobWorker.class.getDeclaredMethod("idleVerseOf", String.class);
        m.setAccessible(true);
        return (String[]) m.invoke(null, star);
    }

    private static int[] feiTianSanSha(int yearBranch) throws Exception {
        Method m = ZiWeiDouShuOntologyJobWorker.class.getDeclaredMethod("feiTianSanSha", int.class);
        m.setAccessible(true);
        return (int[]) m.invoke(null, yearBranch);
    }

    private static int longChi(int yearBranch) throws Exception {
        Method m = ZiWeiDouShuOntologyJobWorker.class.getDeclaredMethod("longChi", int.class);
        m.setAccessible(true);
        return (Integer) m.invoke(null, yearBranch);
    }

    private static int fengGe(int yearBranch) throws Exception {
        Method m = ZiWeiDouShuOntologyJobWorker.class.getDeclaredMethod("fengGe", int.class);
        m.setAccessible(true);
        return (Integer) m.invoke(null, yearBranch);
    }

    private static boolean isQiShaChaoDou(int mingIdx) throws Exception {
        Method m = ZiWeiDouShuOntologyJobWorker.class.getDeclaredMethod("isQiShaChaoDou", int.class);
        m.setAccessible(true);
        return (Boolean) m.invoke(null, mingIdx);
    }

    @SuppressWarnings("unchecked")
    private static String judgePalaceZhiGrid(int mingIdx, Map<Integer, List<String>> at, int yearGan) throws Exception {
        Method m = ZiWeiDouShuOntologyJobWorker.class
                .getDeclaredMethod("judgePalaceZhiGrid", int.class, Map.class, int.class);
        m.setAccessible(true);
        return (String) m.invoke(null, mingIdx, at, yearGan);
    }

    @Test
    @DisplayName("紫微定位表：五局初一取值（水二丑/木三辰/金四亥/土五午/火六酉）——卷二·安紫微定位棋盘图")
    void ziweiLayoutTable() throws Exception {
        int[][] zt = (int[][]) field("ZIWEI_TABLE");
        assertThat(zt[0][0]).as("水二局初一→丑").isEqualTo(1);
        assertThat(zt[1][0]).as("木三局初一→辰").isEqualTo(4);
        assertThat(zt[2][0]).as("金四局初一→亥").isEqualTo(11);
        assertThat(zt[3][0]).as("土五局初一→午").isEqualTo(6);
        assertThat(zt[4][0]).as("火六局初一→酉（卷二例：火局正月初一酉宫起紫微）").isEqualTo(9);
    }

    @Test
    @DisplayName("紫府系偏移：紫微系 {0,-1,-3,-4,-5,-8} / 天府系 {0,1,2,3,4,5,6,10}——卷二·安南北斗諸星訣")
    void seriesOffsets() throws Exception {
        int[] zi = (int[]) field("ZIWEI_SERIES_OFF");
        assertThat(zi).containsExactly(0, -1, -3, -4, -5, -8);
        int[] fu = (int[]) field("TIANFU_SERIES_OFF");
        assertThat(fu).containsExactly(0, 1, 2, 3, 4, 5, 6, 10);
    }

    @Test
    @DisplayName("紫微↔天府寅申对称：天府系七杀(6)空三而破军(10)（申子辰三合杀破狼）")
    void pojunOffset() throws Exception {
        int[] fu = (int[]) field("TIANFU_SERIES_OFF");
        assertThat(fu[6]).as("七杀").isEqualTo(6);
        assertThat(fu[7]).as("破军=七杀+4（空三）").isEqualTo(10);
    }

    @Test
    @DisplayName("星辰庙陷表（卷二·星辰庙陷）：紫微@午=庙、太阳@子=陷、太阴@亥=庙、七杀@子=旺、文曲@申=得地、铃星@卯=利益")
    void brightnessTable() throws Exception {
        assertThat(brightnessOf("紫微", 6)).as("紫微@午").isEqualTo("庙");
        assertThat(brightnessOf("太阳", 0)).as("太阳@子").isEqualTo("陷");
        assertThat(brightnessOf("太阴", 11)).as("太阴@亥").isEqualTo("庙");
        assertThat(brightnessOf("七杀", 0)).as("七杀@子").isEqualTo("旺");
        assertThat(brightnessOf("文曲", 8)).as("文曲@申").isEqualTo("得地");
        assertThat(brightnessOf("铃星", 3)).as("铃星@卯").isEqualTo("利益");
        assertThat(brightnessOf("天府", 5)).as("天府@巳").isEqualTo("得地");
    }

    @Test
    @DisplayName("庙陷表外之星（辅佐/杂曜）返回「平和」兜底")
    void brightnessFallback() throws Exception {
        assertThat(brightnessOf("左辅", 0)).isEqualTo("平和");
        assertThat(brightnessOf("天魁", 5)).isEqualTo("平和");
        assertThat(brightnessOf("禄存", 1)).as("禄存不入丑（结构上不可能，兜底平和）").isEqualTo("平和");
    }

    @Test
    @DisplayName("星辰落闲（卷二·定十二宫星辰落闲）：紫微子辰亥 / 破军巳申 / 武曲申 / 天机巳")
    void idleStars() throws Exception {
        assertThat(isIdleStar("紫微", 0)).as("紫微@子").isTrue();
        assertThat(isIdleStar("紫微", 1)).as("紫微@丑（非闲）").isFalse();
        assertThat(isIdleStar("破军", 5)).as("破军@巳").isTrue();
        assertThat(isIdleStar("武曲", 8)).as("武曲@申").isTrue();
        assertThat(isIdleStar("天机", 5)).as("天机@巳").isTrue();
        assertThat(isIdleStar("贪狼", 2)).as("贪狼@寅").isTrue();
    }

    @Test
    @DisplayName("落闲断语交叉复核（卷二·定十二宫星辰落闲 × 十二宫各星吉凶诀）：规范表＝8星；表内星引断语、表外星举例式")
    void idleVerseCrossCheck() throws Exception {
        // 规范表恰 8 星（紫微/贪狼/天相/七杀/天梁/天机/破军/武曲），天同/廉贞/擎羊/火星/铃星 不在表内
        assertThat(inIdleTable("武曲")).isTrue();
        assertThat(inIdleTable("天机")).isTrue();
        assertThat(inIdleTable("天同")).as("天同不在定闲表（断语泛论）").isFalse();
        assertThat(inIdleTable("廉贞")).as("廉贞不在定闲表").isFalse();
        assertThat(inIdleTable("擎羊")).as("擎羊不在定闲表").isFalse();
        // 表内星断语：天机 L494、天梁 L712、七杀 L733
        assertThat(idleVerseOf("天机")).as("天机落闲断语").isNotNull();
        assertThat(idleVerseOf("天机")[1]).isEqualTo("494");
        assertThat(idleVerseOf("天机")[2]).contains("天机化忌落闲宫");
        assertThat(idleVerseOf("天梁")[1]).isEqualTo("712");
        assertThat(idleVerseOf("七杀")[1]).isEqualTo("733");
        // 表外星断语（举例式）：天同 L560、廉贞 L585/586、擎羊 L850、火星/铃星 L889
        assertThat(idleVerseOf("天同")[2]).contains("天同守命落闲宫");
        assertThat(idleVerseOf("擎羊")[2]).contains("擎羊一曜落闲宫");
        assertThat(idleVerseOf("火星")[1]).isEqualTo("889");
        assertThat(idleVerseOf("铃星")[1]).isEqualTo("889");
        // 无断语之星 → null（不臆造）
        assertThat(idleVerseOf("紫微")).as("紫微无落闲断语").isNull();
    }

    @Test
    @DisplayName("飞天神煞（卷二·安飞天三杀诀）：寅午戌→亥卯未、申子辰→巳酉丑、亥卯未→申子辰、巳酉丑→寅午戌")
    void feiTianSanShaMap() throws Exception {
        assertThat(feiTianSanSha(2)).as("寅年").containsExactly(11, 3, 7);
        assertThat(feiTianSanSha(0)).as("子年").containsExactly(5, 9, 1);
        assertThat(feiTianSanSha(11)).as("亥年").containsExactly(8, 0, 4);
        assertThat(feiTianSanSha(5)).as("巳年").containsExactly(2, 6, 10);
    }

    @Test
    @DisplayName("四化（卷二·安禄权科忌四星变化诀）：甲廉破武阳 / 壬梁紫府武（壬化科＝天府，非通行「左辅化科」异说）")
    void sihuaTable() throws Exception {
        String[][] sh = (String[][]) field("SIHUA");
        assertThat(sh[0]).as("甲").containsExactly("廉贞", "破军", "武曲", "太阳");
        assertThat(sh[8]).as("壬").containsExactly("天梁", "紫微", "天府", "武曲");
        assertThat(sh[9]).as("癸").containsExactly("破军", "巨门", "太阴", "贪狼");
        assertThat(sh[7]).as("辛").containsExactly("巨门", "太阳", "文曲", "文昌");
    }

    @Test
    @DisplayName("火铃（卷二·安火铃二星诀，按年支三合）：寅午戌→火丑铃卯 / 申子辰→火寅 / 亥卯未→火酉")
    void huoLingMap() throws Exception {
        int[] huo = (int[]) field("HUOXING");
        int[] ling = (int[]) field("LINGXING");
        assertThat(huo[2]).as("寅年火@丑").isEqualTo(1);
        assertThat(ling[2]).as("寅年铃@卯").isEqualTo(3);
        assertThat(huo[0]).as("子年火@寅").isEqualTo(2);
        assertThat(ling[0]).as("子年铃@戌").isEqualTo(10);
        assertThat(huo[3]).as("卯年火@酉").isEqualTo(9);
    }

    @Test
    @DisplayName("龙池凤阁起例（卷二·安龙池凤阁诀「龙池子顺辰，凤阁子戌逆」）：子年→龙池辰/凤阁戌；午年→龙池戌/凤阁辰")
    void dragonPhoenix() throws Exception {
        assertThat(longChi(0)).as("子年 龙池@辰").isEqualTo(4);
        assertThat(fengGe(0)).as("子年 凤阁@戌").isEqualTo(10);
        assertThat(longChi(6)).as("午年 龙池@戌").isEqualTo(10);
        assertThat(fengGe(6)).as("午年 凤阁@辰").isEqualTo(4);
        assertThat(longChi(5)).as("巳年 龙池@酉").isEqualTo(9);
        assertThat(fengGe(5)).as("巳年 凤阁@巳").isEqualTo(5);
    }

    @Test
    @DisplayName("七杀朝斗（卷三·谈星要论「七杀寅申子午一生爵禄荣昌，为七杀朝斗格」）：寅申子午为格，余宫非")
    void qiShaChaoDou() throws Exception {
        assertThat(isQiShaChaoDou(0)).as("子").isTrue();
        assertThat(isQiShaChaoDou(6)).as("午").isTrue();
        assertThat(isQiShaChaoDou(2)).as("寅").isTrue();
        assertThat(isQiShaChaoDou(8)).as("申").isTrue();
        assertThat(isQiShaChaoDou(1)).as("丑（非格）").isFalse();
        assertThat(isQiShaChaoDou(3)).as("卯（非格）").isFalse();
        assertThat(isQiShaChaoDou(5)).as("巳（非格）").isFalse();
        assertThat(isQiShaChaoDou(11)).as("亥（非格）").isFalse();
    }

    @SuppressWarnings("unchecked")
    private static List<String> judgeTenGrades(Set<String> mingSet, Set<String> mingMain, Set<String> sanFang)
            throws Exception {
        Method m = ZiWeiDouShuOntologyJobWorker.class
                .getDeclaredMethod("judgeTenGrades", Set.class, Set.class, Set.class);
        m.setAccessible(true);
        return (List<String>) m.invoke(null, mingSet, mingMain, sanFang);
    }

    private static Set<String> setOf(String... s) {
        return new HashSet<>(List.of(s));
    }

    @Test
    @DisplayName("定富贵贫贱十等论（卷一 c2 L545-555）：天同天梁坐命→福寿；昌曲辅弼拱→聪明；机梁杀破逢空劫→僧道")
    void tenGrades() throws Exception {
        Set<String> mt = setOf("天同", "天梁");
        assertThat(judgeTenGrades(mt, mt, mt)).as("天同天梁坐命庙旺").contains("福寿（天同天梁坐命庙旺）");

        Set<String> smart = setOf("文昌", "文曲", "左辅");
        assertThat(judgeTenGrades(setOf(), setOf(), smart)).as("昌曲辅弼三合拱照").contains("聪明（昌曲辅弼三合拱照）");

        Set<String> seng = setOf("天机", "七杀", "地劫");
        assertThat(judgeTenGrades(setOf(), setOf(), seng)).as("机梁杀破逢空劫").contains("僧道（机梁杀破逢空劫）");

        Set<String> xing = setOf("擎羊", "武曲");
        assertThat(judgeTenGrades(setOf(), setOf(), xing)).as("煞曜武破带杀").contains("刑名（杀曜武破带杀凑合）");

        assertThat(judgeTenGrades(setOf(), setOf(), setOf())).as("无星曜命中 → 十等为空").isEmpty();
    }

    @Test
    @DisplayName("太岁小限庙陷遇十二宫吉凶（卷三 c4 L103-172）：子支含「七杀破军…癸庚己生人发福」，共 12 支三段")
    void taisuiXiaoxianTable() throws Exception {
        String[][] t = (String[][]) field("TAISUI_XIAOXIAN");
        assertThat(t.length).as("12 支").isEqualTo(12);
        assertThat(t[0].length).as("子支三段").isEqualTo(3);
        assertThat(t[0][0]).as("子·入庙化吉").contains("七杀破军在子宫守岁限癸庚己生人发福");
        assertThat(t[0][2]).as("子·所值吉凶星").contains("人财两美事事遂心");
        assertThat(t[6][0]).as("午·入庙化吉").contains("紫微太阳武曲天同天梁廉贞七杀破军");
        assertThat(t[11][1]).as("亥·不入庙化凶").contains("廉贞丙壬癸生人不宜");
    }

    @Test
    @DisplayName("大小限过十二支所忌（卷三 c4 L92-101）：子命忌寅申；申生人忌火铃；酉生人忌羊陀卯宫")
    void limitTabooTable() throws Exception {
        String[] t = (String[]) field("LIMIT_TABOO");
        assertThat(t).as("12 支").hasSize(12);
        assertThat(t[0]).as("子").contains("切忌寅申岁限");
        assertThat(t[8]).as("申").contains("火铃二星");
        assertThat(t[9]).as("酉").contains("忌行卯宫限");
        assertThat(t[7]).as("未").contains("擎羊在四墓宫");
    }

    @Test
    @DisplayName("十二宫诸星得地合格诀/失陷破格诀（卷一 c2 L556-578）：子安命贪阴+机梁拱+生干庚→得地合格(生干合)；卯安命擎羊陷为失陷破格")
    void palaceZhiGrid() throws Exception {
        Map<Integer, List<String>> geZi = new HashMap<>();
        geZi.put(0, List.of("贪狼", "太阴"));
        geZi.put(4, List.of("天机"));
        geZi.put(8, List.of("天梁"));
        assertThat(judgePalaceZhiGrid(0, geZi, 6)).as("子安命：贪狼杀阴星，机梁相拱，庚辛乙癸生人美（庚=6）")
                .contains("得地合格·子安命").contains("生干").contains("相合");

        Map<Integer, List<String>> geMao = new HashMap<>();
        geMao.put(3, List.of("天机", "巨门", "武曲"));
        assertThat(judgePalaceZhiGrid(3, geMao, 7)).as("卯安命：机巨武曲逢，辛乙生人福气隆（辛=7）")
                .contains("得地合格·卯安命").contains("相合");

        // 生年干不合（子安命甲=0，非庚辛乙癸）→ 仍成星象格但标注生干不合·格减等
        assertThat(judgePalaceZhiGrid(0, geZi, 0)).as("子安命+甲（不合庚辛乙癸）→ 生干不合·格减等")
                .contains("得地合格·子安命").contains("不合").contains("格减等");

        Map<Integer, List<String>> poMao = new HashMap<>();
        poMao.put(3, List.of("擎羊"));
        assertThat(judgePalaceZhiGrid(3, poMao, 0)).as("卯安命：太阴擎羊逢（擎羊@卯落陷）").contains("失陷破格·卯安命");

        Map<Integer, List<String>> ziNo = new HashMap<>();
        ziNo.put(0, List.of("贪狼", "太阴"));
        assertThat(judgePalaceZhiGrid(0, ziNo, 0)).as("子安命无天机天梁拱 → 非合格（未命中）")
                .doesNotContain("得地合格·子安命");
    }

    @Test
    @DisplayName("得地合格诀生年干条件（卷一 c2）：寅行原文「申巳庚」含非天干讹字→标存疑·取文不臆断（不以生干否决）")
    void palaceZhiGridGanSuspect() throws Exception {
        Map<Integer, List<String>> geYin = new HashMap<>();
        geYin.put(2, List.of("巨门", "太阳"));
        geYin.put(6, List.of("天梁"));
        String r = judgePalaceZhiGrid(2, geYin, 0);
        assertThat(r).as("寅安命：巨日丰隆，天梁百事通（生干列为「申、巳、庚」含讹字）")
                .contains("得地合格·寅安命").contains("存疑");
    }

    @Test
    @DisplayName("十二宫诸星得地富贵论/失陷贫贱论（卷一 c2 L579-596）：午安命紫微→得地富贵；午安命太阴(不得地)→失陷贫贱；戌安命天府→得地富贵（戌支字形修复）")
    void palaceFuguiPinjian() throws Exception {
        Map<Integer, List<String>> fgWu = new HashMap<>();
        fgWu.put(6, List.of("紫微"));
        assertThat(judgePalaceZhiGrid(6, fgWu, 0)).as("午安命紫微→得地富贵·午").contains("得地富贵·午安命");

        Map<Integer, List<String>> pjWu = new HashMap<>();
        pjWu.put(6, List.of("太阴"));
        assertThat(judgePalaceZhiGrid(6, pjWu, 0)).as("午安命太阴(不得地)→失陷贫贱·午").contains("失陷贫贱·午安命");

        Map<Integer, List<String>> fgXu = new HashMap<>();
        fgXu.put(10, List.of("天府"));
        assertThat(judgePalaceZhiGrid(10, fgXu, 0)).as("戌安命天府→得地富贵·戌（戌/戍字形修复）").contains("得地富贵·戌安命");
    }

    @SuppressWarnings("unchecked")
    private static String judgeTongYuan(int mingIdx, Map<Integer, List<String>> at, int yearGan, String skeleton)
            throws Exception {
        Method m = ZiWeiDouShuOntologyJobWorker.class
                .getDeclaredMethod("judgeTongYuan", int.class, Map.class, int.class, String.class);
        m.setAccessible(true);
        return (String) m.invoke(null, mingIdx, at, yearGan, skeleton);
    }

    @Test
    @DisplayName("卷三·诸星同垣各司所宜（c4 L178-477）：紫微居午+生年甲→「紫微居午无刑忌甲丁己命至公卿」命中；生年丙不命中")
    void tongYuanTable() throws Exception {
        Map<Integer, List<String>> at = new HashMap<>();
        at.put(6, List.of("紫微"));
        String r = judgeTongYuan(6, at, 0, "");
        assertThat(r).as("紫微@午+甲").contains("紫微居午无刑忌甲丁己命至公卿");
        assertThat(r).as("含举例式取文段").contains("举例式·待考取文");
        String r2 = judgeTongYuan(6, at, 2, "");
        assertThat(r2).as("紫微@午+丙（非甲丁己）").doesNotContain("紫微居午无刑忌甲丁己命至公卿");
    }

    @Test
    @DisplayName("卷三·诸星同垣补录（c4 L262-346）：日月条/贪狼条/巨门条 8 条举例式取文已登记")
    void tongYuanSupplementRows() throws Exception {
        Map<Integer, List<String>> at = new HashMap<>();
        at.put(6, List.of("太阳"));   // 日月条
        at.put(0, List.of("贪狼"));   // 贪狼条
        at.put(4, List.of("巨门"));   // 巨门条
        String r = judgeTongYuan(6, at, 0, "");
        assertThat(r).as("日月命身居丑未…").contains("日月命身居丑未三方无吉反为凶");
        assertThat(r).as("日月守命不如照合并明").contains("日月守命不如照合并明");
        assertThat(r).as("阴阳左右合为佳").contains("阴阳左右合为佳");
        assertThat(r).as("日月更须贪杀会…").contains("日月更须贪杀会男多奸盗女多淫");
        assertThat(r).as("日月疾厄命宫空腰陀目瞽").contains("日月疾厄命宫空腰陀目瞽");
        assertThat(r).as("贪武四生四墓宫…").contains("贪武四生四墓宫破军忌杀百工通");
        assertThat(r).as("巨日命宫寅位食禄驰名").contains("巨日命宫寅位食禄驰名");
        assertThat(r).as("巨日命立申宫亦妙").contains("巨日命立申宫亦妙");
    }

    private static String judgeMingGeGrade(List<String> hit, Set<String> mingMain, Set<String> sanFang,
            Map<Integer, List<String>> at, int mingIdx, String fourTransform) throws Exception {
        Method m = ZiWeiDouShuOntologyJobWorker.class.getDeclaredMethod(
                "judgeMingGeGrade", List.class, Set.class, Set.class, Map.class, int.class, String.class);
        m.setAccessible(true);
        return (String) m.invoke(null, hit, mingMain, sanFang, at, mingIdx, fourTransform);
    }

    @Test
    @DisplayName("论人命入格/看命总纲（卷三 c4 L22-36）：入格+庙旺+科权禄→上上之命；太阳陷+羊刃→下格之命；无主星无吉→平常命")
    void mingGeGrade() throws Exception {
        Map<Integer, List<String>> wu = new HashMap<>();
        wu.put(6, List.of("紫微"));
        assertThat(judgeMingGeGrade(List.of("紫府朝垣（贵局）"), setOf("紫微"), setOf("紫微"), wu, 6, "紫微化权"))
                .as("午命紫微庙+化权+入格").contains("上上之命");

        Map<Integer, List<String>> zi = new HashMap<>();
        zi.put(0, List.of("太阳"));
        assertThat(judgeMingGeGrade(List.of(), setOf("太阳"), setOf("太阳", "擎羊"), zi, 0, ""))
                .as("子命太阳陷+羊刃").contains("下格之命");

        assertThat(judgeMingGeGrade(List.of(), setOf(), setOf(), new HashMap<>(), 0, ""))
                .as("无主星无吉").contains("平常命");
    }

    private static String judgeGeXingShu(List<String> hit) throws Exception {
        Method m = ZiWeiDouShuOntologyJobWorker.class.getDeclaredMethod("judgeGeXingShu", List.class);
        m.setAccessible(true);
        return (String) m.invoke(null, hit);
    }

    @Test
    @DisplayName("论格星数高下（卷三 c4 L37-54）：贵局→星得上格（第一~第三区间）；贫贱局→星得下格（第七~第九）；数维标待考")
    void geXingShu() throws Exception {
        assertThat(judgeGeXingShu(List.of("紫府朝垣（贵局·卷一）")))
                .as("贵局→上格").contains("星得上格").contains("第一~第三");
        assertThat(judgeGeXingShu(List.of("一生孤贫（贫贱局·卷一）")))
                .as("贫贱局→下格").contains("星得下格").contains("第七~第九");
        assertThat(judgeGeXingShu(List.of())).as("无格→中格").contains("星得中格");
        assertThat(judgeGeXingShu(List.of("紫府朝垣（贵局）"))).as("数维待考标注").contains("待考");
    }

    private static String judgeAnMingJiXiong(int hourBranch, int mingIdx) throws Exception {
        Method m = ZiWeiDouShuOntologyJobWorker.class.getDeclaredMethod("judgeAnMingJiXiong", int.class, int.class);
        m.setAccessible(true);
        return (String) m.invoke(null, hourBranch, mingIdx);
    }

    @Test
    @DisplayName("论人生时安命吉凶（卷三 c4 L66-67）：六阳时安命六阳宫吉；阳时安命阴宫则少遂")
    void anMingJiXiong() throws Exception {
        assertThat(judgeAnMingJiXiong(2, 2)).as("寅时(阳)安命寅(阳)→吉").contains("吉（命安同组六宫）");
        assertThat(judgeAnMingJiXiong(2, 1)).as("寅时(阳)安命丑(阴)→少遂").contains("少遂");
        assertThat(judgeAnMingJiXiong(1, 1)).as("丑时(阴)安命丑(阴)→吉").contains("吉（命安同组六宫）");
    }

    private static String judgeKeQin(int yearBranch, int hourBranch) throws Exception {
        Method m = ZiWeiDouShuOntologyJobWorker.class.getDeclaredMethod("judgeKeQin", int.class, int.class);
        m.setAccessible(true);
        return (String) m.invoke(null, yearBranch, hourBranch);
    }

    private static String palaceStarRelation(String star, int branchIdx) throws Exception {
        Method m = ZiWeiDouShuOntologyJobWorker.class
                .getDeclaredMethod("palaceStarRelation", String.class, int.class);
        m.setAccessible(true);
        return (String) m.invoke(null, star, branchIdx);
    }

    @SuppressWarnings("unchecked")
    private static List<String> judgeLongevity(int mingIdx, int shenIdx, Map<Integer, List<String>> at)
            throws Exception {
        Method m = ZiWeiDouShuOntologyJobWorker.class
                .getDeclaredMethod("judgeLongevity", int.class, int.class, Map.class);
        m.setAccessible(true);
        return (List<String>) m.invoke(null, mingIdx, shenIdx, at);
    }

    @Test
    @DisplayName("星辰生克制化·星曜落宫（卷二 c3 L137-140）：廉贞火在寅木乡→宫生星；武曲金在午火→宫克星受制（金入火乡）；火星在子水→受制（火入水乡）")
    void palaceStarRelationPhase() throws Exception {
        assertThat(palaceStarRelation("廉贞", 2)).as("廉贞(火)在寅(木)→宫生星").contains("宫生星（得生）");
        assertThat(palaceStarRelation("武曲", 6)).as("武曲(金)在午(火)→受制：金入火乡")
                .contains("宫克星（受制").contains("金入火乡");
        assertThat(palaceStarRelation("火星", 0)).as("火星(火)在子(水)→受制：火入水乡")
                .contains("宫克星（受制").contains("火入水乡");
        assertThat(palaceStarRelation("破军", 5)).as("破军(水)在巳(火)→？").isNotNull();
        assertThat(palaceStarRelation("天机", 7)).as("天机(木)在未(土)→星克宫").contains("星克宫");
    }

    @Test
    @DisplayName("诸格论·12 格（卷一 c2 L510-544）：紫微居卯遇劫空→僧道；贪月同杀会机梁→商贾；命宫太阳(陷)+火星→残疾；曲守命三方会昌→聪明")
    void longevityTypes() throws Exception {
        Map<Integer, List<String>> seng = new HashMap<>();
        seng.put(3, List.of("紫微", "地劫"));
        assertThat(judgeLongevity(3, -1, seng)).as("极居卯酉遇劫空").contains("僧道（极居卯酉遇劫空 / 命坐空乡 / 机杀破梁会太阳帝座——男僧道女尼姑）");

        Map<Integer, List<String>> shang = new HashMap<>();
        shang.put(0, List.of("贪狼"));
        shang.put(4, List.of("太阴", "天机"));
        shang.put(8, List.of("天同"));
        shang.put(6, List.of("七杀"));
        assertThat(judgeLongevity(0, -1, shang)).as("贪月同杀会机梁").anyMatch(s -> s.contains("商贾"));

        Map<Integer, List<String>> can = new HashMap<>();
        can.put(0, List.of("太阳", "火星"));
        assertThat(judgeLongevity(0, -1, can)).as("命宫落陷加羊陀火铃").anyMatch(s -> s.contains("残疾"));

        Map<Integer, List<String>> cong = new HashMap<>();
        cong.put(0, List.of("文曲"));
        cong.put(4, List.of("文昌"));
        assertThat(judgeLongevity(0, -1, cong)).as("文曲天相破军，三方昌曲会").anyMatch(s -> s.contains("聪明"));

        assertThat(judgeLongevity(0, -1, new HashMap<>())).as("空盘不命中").isEmpty();
    }

    @Test
    @DisplayName("论小儿克亲（卷三 c4 L70-71）：寅年辰时最毒；巳年亥时先克父；辰年子时先克母")
    void keQin() throws Exception {
        assertThat(judgeKeQin(2, 4)).as("寅年辰时").contains("最毒");
        assertThat(judgeKeQin(5, 11)).as("巳年亥时").contains("先克父");
        assertThat(judgeKeQin(4, 0)).as("辰年子时").contains("先克母");
    }

    // ---- ★外部通行法·非《全書》原文（《全書》语料 0 命中）诸杂曜安星诀表自检 ----

    private static int sixHePartner(int idx) throws Exception {
        Method m = ZiWeiDouShuOntologyJobWorker.class.getDeclaredMethod("sixHePartner", int.class);
        m.setAccessible(true);
        return (Integer) m.invoke(null, idx);
    }

    @SuppressWarnings("unchecked")
    private static boolean huaInPalace(int idx, Map<Integer, List<String>> at, String ft, String type)
            throws Exception {
        Method m = ZiWeiDouShuOntologyJobWorker.class
                .getDeclaredMethod("huaInPalace", int.class, Map.class, String.class, String.class);
        m.setAccessible(true);
        return (Boolean) m.invoke(null, idx, at, ft, type);
    }

    @SuppressWarnings("unchecked")
    private static boolean palaceHasLu(int idx, Map<Integer, List<String>> at, String ft) throws Exception {
        Method m = ZiWeiDouShuOntologyJobWorker.class
                .getDeclaredMethod("palaceHasLu", int.class, Map.class, String.class);
        m.setAccessible(true);
        return (Boolean) m.invoke(null, idx, at, ft);
    }

    @Test
    @DisplayName("诸杂曜安星诀表〔外部通行法·非《全書》原文〕：天官甲未/庚亥；天福甲酉；天厨丙子/癸亥；天巫巳申寅亥；孤辰子→寅/辰→巳；华盖子→辰；咸池子→酉")
    void miscStarExternalTables() throws Exception {
        int[] guan = (int[]) field("TIAN_GUAN_BR");
        int[] fu = (int[]) field("TIAN_FU_BR");
        int[] chu = (int[]) field("TIAN_CHU_BR");
        int[] wu = (int[]) field("TIAN_WU_BR");
        int[] guChen = (int[]) field("GU_CHEN_BR");
        int[] guaSu = (int[]) field("GUA_SU_BR");
        int[] huaGai = (int[]) field("HUA_GAI_BR");
        int[] xianChi = (int[]) field("XIAN_CHI_BR");

        assertThat(guan[0]).as("天官 甲→未").isEqualTo(7);
        assertThat(guan[6]).as("天官 庚→亥").isEqualTo(11);
        assertThat(fu[0]).as("天福 甲→酉").isEqualTo(9);
        assertThat(fu[1]).as("天福 乙→申").isEqualTo(8);
        assertThat(chu[2]).as("天厨 丙→子").isEqualTo(0);
        assertThat(chu[9]).as("天厨 癸→亥").isEqualTo(11);
        assertThat(wu).as("天巫 正五九巳/二六十申/三七十一寅/四八十二亥").containsExactly(5, 8, 2, 11);
        assertThat(guChen[0]).as("孤辰 子年→寅").isEqualTo(2);
        assertThat(guChen[4]).as("孤辰 辰年→巳").isEqualTo(5);
        assertThat(guaSu[0]).as("寡宿 子年→戌").isEqualTo(10);
        assertThat(guaSu[4]).as("寡宿 辰年→丑").isEqualTo(1);
        assertThat(huaGai[0]).as("华盖 子年→辰").isEqualTo(4);
        assertThat(huaGai[5]).as("华盖 巳年→丑").isEqualTo(1);
        assertThat(xianChi[0]).as("咸池 子年→酉").isEqualTo(9);
        assertThat(xianChi[6]).as("咸池 午年→卯").isEqualTo(3);
    }

    private static String brightnessVariantNote(String star, int branchIdx) throws Exception {
        Method m = ZiWeiDouShuOntologyJobWorker.class
                .getDeclaredMethod("brightnessVariantNote", String.class, int.class);
        m.setAccessible(true);
        return (String) m.invoke(null, star, branchIdx);
    }

    @Test
    @DisplayName("卷二·星辰庙陷 vs 卷三·诸星庙陷汇总 异文登记（⑲异说并陈）：天机@辰、文曲@寅 相左；余宫无")
    void brightnessVariants() throws Exception {
        assertThat(brightnessVariantNote("天机", 4)).as("天机@辰：卷二利益/卷三旺").contains("利益").contains("旺");
        assertThat(brightnessVariantNote("文曲", 2)).as("文曲@寅：卷二平和/卷三陷").contains("平和").contains("陷");
        assertThat(brightnessVariantNote("天机", 0)).as("天机@子（无相左）").isNull();
        assertThat(brightnessVariantNote("紫微", 4)).as("紫微@辰（卷三未列，非矛盾）").isNull();
    }

    @Test
    @DisplayName("宫支六合（暗合）：子丑/寅亥/卯戌/辰酉/巳申/午未 —— 用于「明禄暗禄/科明暗禄」")
    void sixHePairing() throws Exception {
        assertThat(sixHePartner(0)).as("子↔丑").isEqualTo(1);
        assertThat(sixHePartner(2)).as("寅↔亥").isEqualTo(11);
        assertThat(sixHePartner(3)).as("卯↔戌").isEqualTo(10);
        assertThat(sixHePartner(4)).as("辰↔酉").isEqualTo(9);
        assertThat(sixHePartner(5)).as("巳↔申").isEqualTo(8);
        assertThat(sixHePartner(6)).as("午↔未").isEqualTo(7);
    }

    @Test
    @DisplayName("四化落宫/坐禄判定（用于定贵局明禄暗禄·科明暗禄）")
    void huaAndLuHelpers() throws Exception {
        Map<Integer, List<String>> at = new HashMap<>();
        at.put(0, List.of("紫微"));
        at.put(7, List.of("禄存"));
        assertThat(huaInPalace(0, at, "紫微化科", "化科")).as("紫微化科在子").isTrue();
        assertThat(huaInPalace(1, at, "紫微化科", "化科")).as("子以外无化科").isFalse();
        assertThat(palaceHasLu(7, at, "紫微化科")).as("未宫坐禄存→有禄").isTrue();
        assertThat(palaceHasLu(0, at, "紫微化科")).as("子宫无禄").isFalse();
        Map<Integer, List<String>> at2 = new HashMap<>();
        at2.put(0, List.of("武曲"));
        assertThat(palaceHasLu(0, at2, "武曲化禄")).as("武曲化禄→有禄").isTrue();
        assertThat(sixHePartner(0)).as("子宫暗合丑").isEqualTo(1);
    }

    @SuppressWarnings("unchecked")
    private static int yunxianFortuneSign(Map<Integer, List<String>> at, int idx, String ft) throws Exception {
        Method m = ZiWeiDouShuOntologyJobWorker.class
                .getDeclaredMethod("yunxianFortuneSign", Map.class, int.class, String.class);
        m.setAccessible(true);
        return (Integer) m.invoke(null, at, idx, ft);
    }

    private static boolean isBuShuWuYi(int[] signs) throws Exception {
        Method m = ZiWeiDouShuOntologyJobWorker.class.getDeclaredMethod("isBuShuWuYi", int[].class);
        m.setAccessible(true);
        return (Boolean) m.invoke(null, (Object) signs);
    }

    private static boolean isShuiShangJiaXing(int[] signs) throws Exception {
        Method m = ZiWeiDouShuOntologyJobWorker.class.getDeclaredMethod("isShuiShangJiaXing", int[].class);
        m.setAccessible(true);
        return (Boolean) m.invoke(null, (Object) signs);
    }

    @Test
    @DisplayName("运限吉凶符号：三方四正吉星(+)/凶星(-)/主星庙旺(+)/落陷(-)/四化(禄权科 +、忌 -) → 净差符号")
    void yunxianFortuneSigns() throws Exception {
        Map<Integer, List<String>> lu = new HashMap<>();
        lu.put(0, List.of("禄存"));
        assertThat(yunxianFortuneSign(lu, 0, null)).as("子宫坐禄存→吉").isEqualTo(1);
        assertThat(yunxianFortuneSign(lu, 6, null)).as("午宫对宫（三方含子）→吉").isEqualTo(1);
        assertThat(yunxianFortuneSign(lu, 3, null)).as("卯宫三方不含子→平").isEqualTo(0);

        Map<Integer, List<String>> sha = new HashMap<>();
        sha.put(0, List.of("擎羊"));
        assertThat(yunxianFortuneSign(sha, 0, null)).as("子宫坐擎羊→凶").isEqualTo(-1);

        Map<Integer, List<String>> miao = new HashMap<>();
        miao.put(1, List.of("紫微"));
        assertThat(yunxianFortuneSign(miao, 1, null)).as("紫微@丑庙→吉").isEqualTo(1);
        Map<Integer, List<String>> xian = new HashMap<>();
        xian.put(1, List.of("天机"));
        assertThat(yunxianFortuneSign(xian, 1, null)).as("天机@丑陷→凶").isEqualTo(-1);

        Map<Integer, List<String>> hua = new HashMap<>();
        hua.put(0, List.of("武曲"));
        assertThat(yunxianFortuneSign(hua, 0, "武曲化禄")).as("武曲化禄守宫→增吉（庙旺亦+）").isEqualTo(1);
        assertThat(yunxianFortuneSign(miao, 1, "紫微化忌")).as("紫微化忌（庙旺吉与化忌凶相抵）→平").isEqualTo(0);
    }

    @Test
    @DisplayName("步数无依（卷一·定杂局「前限接后限连绵不分」）：大限序列吉凶同号、全序无转折→成")
    void buShuWuYi() throws Exception {
        assertThat(isBuShuWuYi(new int[]{1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1})).as("十二限全吉→成").isTrue();
        assertThat(isBuShuWuYi(new int[]{-1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1})).as("十二限全凶→成").isTrue();
        assertThat(isBuShuWuYi(new int[]{1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, -1})).as("有一限异号（有转折）→不成").isFalse();
        assertThat(isBuShuWuYi(new int[]{1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0})).as("含中性断点→不成").isFalse();
    }

    @Test
    @DisplayName("水上驾星（卷一·定杂局「一年好一年不好」）：流年序列吉凶逐年严格交替→成")
    void shuiShangJiaXing() throws Exception {
        assertThat(isShuiShangJiaXing(new int[]{1, -1, 1, -1, 1, -1})).as("吉凶交替(吉始)→成").isTrue();
        assertThat(isShuiShangJiaXing(new int[]{-1, 1, -1, 1})).as("凶吉交替(凶始)→成").isTrue();
        assertThat(isShuiShangJiaXing(new int[]{1, 1, -1, 1})).as("相邻同年→不成").isFalse();
        assertThat(isShuiShangJiaXing(new int[]{1, -1, 0, 1})).as("含中性→不成").isFalse();
        assertThat(isShuiShangJiaXing(new int[]{1, -1, 1})).as("不足4年→不成").isFalse();
    }

    private static int palaceStemIndex(int branch, int yearGan) throws Exception {
        Method m = ZiWeiDouShuOntologyJobWorker.class.getDeclaredMethod("palaceStemIndex", int.class, int.class);
        m.setAccessible(true);
        return (Integer) m.invoke(null, branch, yearGan);
    }

    private static int daXianPalace(int age, int startAge, int startPalace, boolean forward) throws Exception {
        Method m = ZiWeiDouShuOntologyJobWorker.class
                .getDeclaredMethod("daXianPalace", int.class, int.class, int.class, boolean.class);
        m.setAccessible(true);
        return (Integer) m.invoke(null, age, startAge, startPalace, forward);
    }

    private static int xiaoXianPalace(int age, int birthBranch, boolean male) throws Exception {
        Method m = ZiWeiDouShuOntologyJobWorker.class
                .getDeclaredMethod("xiaoXianPalace", int.class, int.class, boolean.class);
        m.setAccessible(true);
        return (Integer) m.invoke(null, age, birthBranch, male);
    }

    @SuppressWarnings("unchecked")
    private static boolean isZhuLuoSanXian(int idx, Map<Integer, List<String>> at) throws Exception {
        Method m = ZiWeiDouShuOntologyJobWorker.class.getDeclaredMethod("isZhuLuoSanXian", int.class, Map.class);
        m.setAccessible(true);
        return (Boolean) m.invoke(null, idx, at);
    }

    @SuppressWarnings("unchecked")
    private static boolean hasJuMenOrSha(int idx, Map<Integer, List<String>> at) throws Exception {
        Method m = ZiWeiDouShuOntologyJobWorker.class.getDeclaredMethod("hasJuMenOrSha", int.class, Map.class);
        m.setAccessible(true);
        return (Boolean) m.invoke(null, idx, at);
    }

    @SuppressWarnings("unchecked")
    private static int annualFortuneSign(Map<Integer, List<String>> at, int fb, int fg) throws Exception {
        Method m = ZiWeiDouShuOntologyJobWorker.class
                .getDeclaredMethod("annualFortuneSign", Map.class, int.class, int.class);
        m.setAccessible(true);
        return (Integer) m.invoke(null, at, fb, fg);
    }

    @Test
    @DisplayName("运限重算：宫干五虎遁 / 大限宫（依虚龄）/ 小限宫（安小限诀男顺女逆）")
    void yunxianRecalcHelpers() throws Exception {
        assertThat(palaceStemIndex(2, 0)).as("甲年寅宫=丙寅").isEqualTo(2);
        assertThat(palaceStemIndex(3, 0)).as("甲年卯宫=丁卯").isEqualTo(3);
        assertThat(palaceStemIndex(0, 0)).as("甲年子宫=丙子").isEqualTo(2);
        assertThat(daXianPalace(1, 2, 5, true)).as("虚龄1<起限2→未起限").isEqualTo(-1);
        assertThat(daXianPalace(2, 2, 5, true)).as("首限=起宫").isEqualTo(5);
        assertThat(daXianPalace(12, 2, 5, true)).as("虚龄12→第2限(顺+1)").isEqualTo(6);
        assertThat(daXianPalace(12, 2, 5, false)).as("虚龄12→第2限(逆-1)").isEqualTo(4);
        assertThat(xiaoXianPalace(1, 0, true)).as("子年1岁（申子辰起戌）").isEqualTo(10);
        assertThat(xiaoXianPalace(2, 0, true)).as("子年2岁男顺→亥").isEqualTo(11);
        assertThat(xiaoXianPalace(2, 0, false)).as("子年2岁女逆→酉").isEqualTo(9);
    }

    @Test
    @DisplayName("竹萝三限（卷二·定男女竹萝三限）：三方四正七杀破军俱见→成；加巨暗/凶星→作三方四正定议")
    void zhuLuoSanXian() throws Exception {
        Map<Integer, List<String>> at = new HashMap<>();
        at.put(4, List.of("七杀"));
        at.put(8, List.of("破军"));
        assertThat(isZhuLuoSanXian(0, at)).as("子三方四正(辰申)见七杀破军→成").isTrue();
        Map<Integer, List<String>> at2 = new HashMap<>();
        at2.put(4, List.of("七杀"));
        assertThat(isZhuLuoSanXian(0, at2)).as("仅七杀无破军→不成").isFalse();
        Map<Integer, List<String>> at3 = new HashMap<>();
        at3.put(4, List.of("七杀", "破军", "巨门"));
        assertThat(hasJuMenOrSha(0, at3)).as("三方四正见巨门→加重").isTrue();
        assertThat(hasJuMenOrSha(3, at3)).as("卯宫三方四正不含辰→否").isFalse();
        Map<Integer, List<String>> at4 = new HashMap<>();
        at4.put(6, List.of("擎羊"));
        assertThat(hasJuMenOrSha(0, at4)).as("三方四正见煞→加重").isTrue();
    }

    @Test
    @DisplayName("运限重算·流年吉凶符号：含流禄(+)/流羊流陀(-)与流年四化（禄权科 +、忌 -）")
    void annualFortuneSigns() throws Exception {
        Map<Integer, List<String>> empty = new HashMap<>();
        assertThat(annualFortuneSign(empty, 2, 0)).as("甲年流年命宫在寅（含流禄于寅）→吉").isEqualTo(1);
        Map<Integer, List<String>> sha = new HashMap<>();
        sha.put(0, List.of("擎羊"));
        assertThat(annualFortuneSign(sha, 0, 0)).as("子宫流年命宫见擎羊(凶)→凶").isEqualTo(-1);
        Map<Integer, List<String>> ji = new HashMap<>();
        ji.put(0, List.of("太阳"));
        assertThat(annualFortuneSign(ji, 0, 0)).as("甲年太阳化忌坐子宫(陷)→凶").isEqualTo(-1);
    }

    private static int flowMonthPalace(int douJunIdx, int month) throws Exception {
        Method m = ZiWeiDouShuOntologyJobWorker.class.getDeclaredMethod("flowMonthPalace", int.class, int.class);
        m.setAccessible(true);
        return (Integer) m.invoke(null, douJunIdx, month);
    }

    private static int flowDayPalace(int flowMonthPalace, int day) throws Exception {
        Method m = ZiWeiDouShuOntologyJobWorker.class.getDeclaredMethod("flowDayPalace", int.class, int.class);
        m.setAccessible(true);
        return (Integer) m.invoke(null, flowMonthPalace, day);
    }

    @Test
    @DisplayName("流月/流日粒度（㉓）：流月命宫＝斗君起正月逐月顺行；流日〔外部通行法〕＝流月命宫起初一逐日顺行")
    void flowMonthDay() throws Exception {
        assertThat(flowMonthPalace(0, 1)).as("斗君在子，正月→子").isEqualTo(0);
        assertThat(flowMonthPalace(0, 12)).as("斗君在子，十二月→亥").isEqualTo(11);
        assertThat(flowMonthPalace(11, 2)).as("斗君在亥，二月→子（顺行过宫）").isEqualTo(0);
        assertThat(flowDayPalace(0, 1)).as("流月命宫在子，初一→子").isEqualTo(0);
        assertThat(flowDayPalace(0, 31)).as("流月命宫在子，三十→午").isEqualTo(6);
        assertThat(flowDayPalace(11, 2)).as("流月命宫在亥，初二→子").isEqualTo(0);
    }

    @SuppressWarnings("unchecked")
    private static List<String> borrowMainStars(Map<Integer, List<String>> at, int idx) throws Exception {
        Method m = ZiWeiDouShuOntologyJobWorker.class.getDeclaredMethod("borrowMainStars", Map.class, int.class);
        m.setAccessible(true);
        return (List<String>) m.invoke(null, at, idx);
    }

    @SuppressWarnings("unchecked")
    private static String borrowedBrightness(Map<Integer, List<String>> at, int idx) throws Exception {
        Method m = ZiWeiDouShuOntologyJobWorker.class.getDeclaredMethod("borrowedBrightness", Map.class, int.class);
        m.setAccessible(true);
        return (String) m.invoke(null, at, idx);
    }

    @SuppressWarnings("unchecked")
    private static String overlayStack(int mingIdx, int dxIdx, int flowYearIdx, int flowMonthIdx, int flowDayIdx,
            Map<Integer, List<String>> at) throws Exception {
        Method m = ZiWeiDouShuOntologyJobWorker.class.getDeclaredMethod("overlayStack",
                int.class, int.class, int.class, int.class, int.class, Map.class);
        m.setAccessible(true);
        return (String) m.invoke(null, mingIdx, dxIdx, flowYearIdx, flowMonthIdx, flowDayIdx, at);
    }

    @Test
    @DisplayName("空宫借星〔外部通行法·非《全書》原文〕（I·62）：借对宫十四主星、辅煞杂曜不借、庙陷随对宫原地")
    void emptyPalaceBorrow() throws Exception {
        Map<Integer, List<String>> at = new HashMap<>();
        // 子宫空；对宫午宫有 紫微(主星) + 左辅(辅) + 擎羊(煞)
        at.put(6, List.of("紫微", "左辅", "擎羊"));
        assertThat(borrowMainStars(at, 0)).as("只借十四主星（辅煞不借）").containsExactly("紫微");
        assertThat(borrowedBrightness(at, 0)).as("借光·庙陷随对宫午宫原地").contains("紫微@午庙");
        Map<Integer, List<String>> empty = new HashMap<>();
        assertThat(borrowedBrightness(empty, 0)).as("对宫亦空→标注").contains("对宫亦空");
    }

    @Test
    @DisplayName("四盘叠合〔外部通行法·非《全書》原文〕（I·51）：本命为体、大限/流年/流月/流日为用逐层叠入")
    void overlayStacking() throws Exception {
        Map<Integer, List<String>> at = new HashMap<>();
        at.put(0, List.of("紫微"));
        at.put(4, List.of("武曲"));
        String s = overlayStack(0, 4, 8, -1, -1, at);
        assertThat(s).as("本命体@子").contains("本命体@子[紫微]");
        assertThat(s).as("大限用@辰").contains("大限用@辰[武曲]");
        assertThat(s).as("流年用@申").contains("流年用@申");
        assertThat(s).as("未给月/日→不出现流月/流日").doesNotContain("流月用").doesNotContain("流日用");
        String s2 = overlayStack(0, -1, 8, 9, 10, at);
        assertThat(s2).as("未起限→标注").contains("大限用（未起限）");
        assertThat(s2).as("含流月/流日逐层叠入").contains("流月用@").contains("流日用@");
    }

    private static int lifePalaceBranch(int month, int hourBranch) throws Exception {
        Method m = ZiWeiDouShuOntologyJobWorker.class.getDeclaredMethod("lifePalaceBranch", int.class, int.class);
        m.setAccessible(true);
        return (Integer) m.invoke(null, month, hourBranch);
    }

    private static int bodyPalaceBranch(int month, int hourBranch) throws Exception {
        Method m = ZiWeiDouShuOntologyJobWorker.class.getDeclaredMethod("bodyPalaceBranch", int.class, int.class);
        m.setAccessible(true);
        return (Integer) m.invoke(null, month, hourBranch);
    }

    private static int hourCandidatesFirst(int hourBranch) throws Exception {
        Method m = ZiWeiDouShuOntologyJobWorker.class.getDeclaredMethod("hourCandidates", int.class);
        m.setAccessible(true);
        return ((int[]) m.invoke(null, hourBranch))[0];
    }

    @SuppressWarnings("unchecked")
    private static int[] hourCandidates(int hourBranch) throws Exception {
        Method m = ZiWeiDouShuOntologyJobWorker.class.getDeclaredMethod("hourCandidates", int.class);
        m.setAccessible(true);
        return (int[]) m.invoke(null, hourBranch);
    }

    @SuppressWarnings("unchecked")
    private static int[] parseCandidateHours(String s, int def) throws Exception {
        Method m = ZiWeiDouShuOntologyJobWorker.class.getDeclaredMethod("parseCandidateHours", String.class, int.class);
        m.setAccessible(true);
        return (int[]) m.invoke(null, s, def);
    }

    @SuppressWarnings("unchecked")
    private static List<String> candidateMingStars(int month, int day, int hourBranch, int yearGan) throws Exception {
        Method m = ZiWeiDouShuOntologyJobWorker.class
                .getDeclaredMethod("candidateMingStars", int.class, int.class, int.class, int.class);
        m.setAccessible(true);
        return (List<String>) m.invoke(null, month, day, hourBranch, yearGan);
    }

    @SuppressWarnings("unchecked")
    private static Set<String> verifyTraitUniverse() throws Exception {
        Method m = ZiWeiDouShuOntologyJobWorker.class.getDeclaredMethod("verifyTraitUniverse");
        m.setAccessible(true);
        return (Set<String>) m.invoke(null);
    }

    @SuppressWarnings("unchecked")
    private static Set<String> extractStatedTraits(String text, Set<String> universe) throws Exception {
        Method m = ZiWeiDouShuOntologyJobWorker.class
                .getDeclaredMethod("extractStatedTraits", String.class, Set.class);
        m.setAccessible(true);
        return (Set<String>) m.invoke(null, text, universe);
    }

    @SuppressWarnings("unchecked")
    private static int verifyCandidateScore(int month, int day, int hourBranch, int yearGan,
            Set<String> stated, StringBuilder detail) throws Exception {
        Method m = ZiWeiDouShuOntologyJobWorker.class.getDeclaredMethod("verifyCandidateScore",
                int.class, int.class, int.class, int.class, Set.class, StringBuilder.class);
        m.setAccessible(true);
        return (Integer) m.invoke(null, month, day, hourBranch, yearGan, stated, detail);
    }

    @Test
    @DisplayName("验盘（定盘）候补与命宫起例〔外部通行法·非《全書》原文〕：命宫寅起正月逆至生时；候补＝原时±1")
    void verifyChartCandidates() throws Exception {
        assertThat(lifePalaceBranch(2, 2)).as("二月寅时→命宫在丑").isEqualTo(1);
        assertThat(bodyPalaceBranch(2, 2)).as("二月寅时→身宫在巳").isEqualTo(5);
        assertThat(hourCandidates(0)).as("子时候补＝亥/子/丑").containsExactly(11, 0, 1);
        assertThat(parseCandidateHours("卯,辰,巳", 0)).as("指定候补").containsExactly(3, 4, 5);
        assertThat(parseCandidateHours("", 5)).as("缺省→原时±1").containsExactly(4, 5, 6);
    }

    @Test
    @DisplayName("验盘（定盘）多维加权〔外部通行法·非《全書》原文〕：命宫主星(w3)+时系星(w2)+五行局(w1)")
    void verifyChartScoring() throws Exception {
        // 甲年(0) 二月 初一 寅时：命宫在丑、水二局 → 命宫主星 紫微+破军
        assertThat(candidateMingStars(2, 1, 2, 0)).as("命宫主星").containsExactly("紫微", "破军");
        Set<String> u = verifyTraitUniverse();
        Set<String> stated = extractStatedTraits("命主自述：忠厚、背厚、皮肤白", u);
        assertThat(stated).as("抽取命中特征").contains("忠厚", "背厚", "皮肤白");
        StringBuilder d = new StringBuilder();
        int sc = verifyCandidateScore(2, 1, 2, 0, stated, d);
        assertThat(sc).as("紫微忠厚(3)+破军背厚(3)+水二局皮肤白(1)=7").isEqualTo(7);
        assertThat(d.toString()).contains("紫微+忠厚(3)").contains("破军+背厚(3)").contains("水二局+皮肤白(1)");
        assertThat(verifyCandidateScore(2, 1, 2, 0, new HashSet<>(), new StringBuilder()))
                .as("无命中特征→0 分").isEqualTo(0);
    }

    private static String traitKeyword() throws Exception {
        Method m = ZiWeiDouShuOntologyJobWorker.class.getDeclaredMethod("verifyStarTraits", String.class);
        m.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<String> list = (List<String>) m.invoke(null, "七杀");
        return String.join("、", list);
    }

    @Test
    @DisplayName("验盘特征词表：命宫主星→性情/相貌（七杀→果断/背厚/眉宽）")
    void verifyStarTraitTable() throws Exception {
        assertThat(traitKeyword()).contains("果断").contains("背厚").contains("眉宽");
        assertThat(hourCandidatesFirst(0)).as("首个候补＝原时-1").isEqualTo(11);
    }

    @Test
    @DisplayName("卷一·形性賦（c2 L63-95）：STAR_XINGXING 落地——破军背重眉宽/廉贞性暴好争/武曲心性果决/贪狼长耸")
    void starXingXingTable() throws Exception {
        String[][] t = (String[][]) field("STAR_XINGXING");
        assertThat(t.length).as("形性賦星曜条数").isGreaterThanOrEqualTo(20);
        java.util.Map<String, String> m = new java.util.HashMap<>();
        for (String[] row : t) {
            m.put(row[0], row[1]);
        }
        assertThat(m.get("破军")).as("破军不仁·背重眉宽").contains("背重").contains("眉宽");
        assertThat(m.get("廉贞")).as("廉贞眉宽口阔·性暴好忿好争").contains("性暴").contains("口阔");
        assertThat(m.get("武曲")).as("武曲心性果决").contains("心性果决");
        assertThat(m.get("贪狼")).as("贪狼入庙长耸").contains("长耸");
        assertThat(m.get("天机")).as("天机不长不短之姿·情怀好善").contains("不长不短").contains("好善");
    }

    private static String xianPinHouFu(int[] signs) throws Exception {
        Method m = ZiWeiDouShuOntologyJobWorker.class.getDeclaredMethod("judgeXianPinHouFu", int[].class);
        m.setAccessible(true);
        return (String) m.invoke(null, (Object) signs);
    }

    @Test
    @DisplayName("卷三·论命先贫后富（c4 L72-74）：前限凶后限吉→先贫后富；前吉后凶→先富后贫；平稳→不分")
    void xianPinHouFuJudge() throws Exception {
        assertThat(xianPinHouFu(new int[]{-1, -1, -1, -1, -1, -1, 1, 1, 1, 1, 1, 1}))
                .as("初限偏凶、后限转吉").contains("先贫后富");
        assertThat(xianPinHouFu(new int[]{1, 1, 1, 1, 1, 1, -1, -1, -1, -1, -1, -1}))
                .as("初限偏吉、后限转凶").contains("先富后贫");
        assertThat(xianPinHouFu(new int[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0}))
                .as("无转折").contains("不分先后");
    }

    @SuppressWarnings("unchecked")
    private static String twoLimitsTaiSui(Map<Integer, List<String>> at, int dx, int xx, int ts,
                                          int dxSign, int xxSign, int tsSign) throws Exception {
        Method m = ZiWeiDouShuOntologyJobWorker.class.getDeclaredMethod("judgeTwoLimitsTaiSui",
                Map.class, int.class, int.class, int.class, int.class, int.class, int.class);
        m.setAccessible(true);
        return (String) m.invoke(null, at, dx, xx, ts, dxSign, xxSign, tsSign);
    }

    @Test
    @DisplayName("卷三·论二限太岁吉凶（c4 L77-78）：太岁冲大限/太岁冲羊陀七杀/大小二限相逢/岁限俱凶")
    void twoLimitsTaiSuiJudge() throws Exception {
        Map<Integer, List<String>> at = new HashMap<>();
        at.put(0, List.of("擎羊"));   // 太岁宫(子)见擎羊
        // 大限在午(6, -1)，小限在寅(3, +1)，太岁在子(0, -1) → 太岁冲大限(午) + 太岁见羊
        String r = twoLimitsTaiSui(at, 6, 3, 0, -1, 1, -1);
        assertThat(r).as("太岁冲大限").contains("太岁冲大限");
        assertThat(r).as("太岁冲羊陀七杀").contains("太岁冲羊陀七杀");

        // 大小二限相逢（同寅宫）+ 俱吉
        assertThat(twoLimitsTaiSui(new HashMap<>(), 3, 3, 9, 1, 1, 0))
                .as("大小二限相逢").contains("大限与小限相逢").contains("吉");

        // 岁限俱凶（大限/小限/太岁皆非吉）
        assertThat(twoLimitsTaiSui(new HashMap<>(), 5, 7, 9, -1, -1, -1))
                .as("岁限俱凶则凶").contains("岁限俱凶");
    }

    @SuppressWarnings("unchecked")
    private static List<String> taiweiMingGe(int mingIdx, Set<String> mingMain, Set<String> mingSet) throws Exception {
        Method m = ZiWeiDouShuOntologyJobWorker.class.getDeclaredMethod("judgeTaiweiMingGe",
                int.class, Set.class, Set.class);
        m.setAccessible(true);
        return (List<String>) m.invoke(null, mingIdx, mingMain, mingSet);
    }

    @Test
    @DisplayName("卷一·太微賦/彀率具名格：日丽中天(太阳居午)/水澄桂萼(太阴居子)/众水朝东(文耗居寅卯)/金舆捧栉(紫微坐命库)")
    void taiweiMingGeJudge() throws Exception {
        // 子=0,寅=2,卯=3,辰=4,午=6
        assertThat(String.join("、", taiweiMingGe(6, Set.of("太阳"), Set.of("太阳"))))
                .as("太阳居午守命").contains("日丽中天");
        assertThat(String.join("、", taiweiMingGe(0, Set.of("太阴"), Set.of("太阴"))))
                .as("太阴居子守命").contains("水澄桂萼");
        assertThat(String.join("、", taiweiMingGe(2, Set.of("破军"), Set.of("破军", "文曲"))))
                .as("文耗居寅卯（破军+文曲守命于寅）").contains("众水朝东");
        assertThat(String.join("、", taiweiMingGe(4, Set.of("紫微"), Set.of("紫微"))))
                .as("紫微坐命于库（辰）").contains("金舆捧栉");
        // 反例：太阴居午不命中日丽中天；破军居辰不命中众水朝东
        assertThat(taiweiMingGe(6, Set.of("太阴"), Set.of("太阴"))).as("太阴居午不命中").noneMatch(s -> s.contains("日丽中天"));
        assertThat(taiweiMingGe(4, Set.of("破军"), Set.of("破军", "文曲"))).as("破军居辰不命中").noneMatch(s -> s.contains("众水朝东"));
        // 举例式取文登记非空
        Method p = ZiWeiDouShuOntologyJobWorker.class.getDeclaredMethod("taiweiPendingText");
        p.setAccessible(true);
        assertThat((String) p.invoke(null)).as("举例式取文登记").contains("举例式·取文").contains("善蔭朝綱");
    }

    private static String taisuiXiaoxianGanHit(int birthGan, int flowBranch) throws Exception {
        Method m = ZiWeiDouShuOntologyJobWorker.class.getDeclaredMethod("taisuiXiaoxianGanHit", int.class, int.class);
        m.setAccessible(true);
        return (String) m.invoke(null, birthGan, flowBranch);
    }

    @Test
    @DisplayName("卷三·论太岁小限(十二宫吉凶)：按本命生干择入庙化吉/不入庙化凶段；干支之干（丙午/甲子）不取")
    void taisuiXiaoxianGanHitJudge() throws Exception {
        // 子(0)宫：吉段{癸庚己丙丁}·凶段{丙戊}（甲=0,丙=2,戊=4,己=5,庚=6,癸=9）
        assertThat(taisuiXiaoxianGanHit(5, 0)).as("子·己→入庙化吉段").contains("入庙化吉");
        assertThat(taisuiXiaoxianGanHit(4, 0)).as("子·戊（仅见凶段）→不入庙化凶段").contains("不入庙化凶");
        assertThat(taisuiXiaoxianGanHit(2, 0)).as("子·丙（吉段天同丙丁/凶段丙戊两见）→吉凶并参").contains("两段皆见");
        assertThat(taisuiXiaoxianGanHit(0, 0)).as("子·甲→两段未列").contains("两段未列");
        // 午(6)宫凶段「贪狼在丙午壬癸生人破财…」：丙为干支「丙午」之干→不取，仅{壬癸}
        assertThat(taisuiXiaoxianGanHit(2, 6)).as("午·丙（丙午之干不取）→两段未列").contains("两段未列");
        assertThat(taisuiXiaoxianGanHit(8, 6)).as("午·壬→不入庙化凶段").contains("不入庙化凶");
    }

    @Test
    @DisplayName("卷二·十二宫各星吉凶诀原文语料落地：12 宫齐全、命宫/兄弟取原文首句并回原行号(c3 Lxxx)")
    void palaceClassicLanding() {
        Map<String, List<String[]>> all = ZiWeiDouShuOntologyJobWorker.loadPalaceClassic();
        assertThat(all.keySet()).as("十二宫齐全").containsExactlyInAnyOrder(
                "命宫", "兄弟", "妻妾", "子女", "财帛", "疾厄", "迁移", "奴仆", "官禄", "田宅", "福德", "父母");
        assertThat(all.get("命宫")).as("命宫原文行数").hasSizeGreaterThan(400);
        int total = all.values().stream().mapToInt(List::size).sum();
        assertThat(total).as("十二宫原文合计行数").isGreaterThan(800);

        // 取某宫某主星原文首句（附原行号）
        String mingZiwei = ZiWeiDouShuOntologyJobWorker.palaceStarQuote("命宫", "紫微");
        assertThat(mingZiwei).as("命宫·紫微 原文首句").contains("紫微土").contains("卷二·命宫").contains("c3 L");
        String xiongZiwei = ZiWeiDouShuOntologyJobWorker.palaceStarQuote("兄弟", "紫微");
        assertThat(xiongZiwei).as("兄弟·紫微 原文首句").contains("紫微有倚靠");
        String fuTianfu = ZiWeiDouShuOntologyJobWorker.palaceStarQuote("父母", "天府");
        assertThat(fuTianfu).as("父母·天府 原文首句应回原行号").contains("c3 L");
        // 无该星记载 → 空串（不臆造）
        assertThat(ZiWeiDouShuOntologyJobWorker.palaceStarQuote("命宫", "不存在之星"))
                .as("无记载返回空串").isEmpty();
    }

    @Test
    @DisplayName("卷二·命宫『宫位×生年干→财官格/贵格/凶』可客观判定：命中回原文句(附c3行号)、未命中空串不臆造")
    void mingGongGeJudge() {
        // 紫微在子宫（ZHI 子=0）：丁(3)年生 → 「贵格」吉（原文 L463「子宫喜丁己庚生人贵格」）
        String ziweiZiDing = ZiWeiDouShuOntologyJobWorker.judgeMingGongGe("紫微", 0, 3);
        assertThat(ziweiZiDing).as("紫微子·丁年→贵格吉")
                .contains("紫微").contains("子宫").contains("丁年生人")
                .contains("贵格").contains("〔吉〕").contains("c3 L463");
        // 紫微在子宫：壬(8)年生 → 「不耐久」凶（原文 L463「壬癸生人不耐久」）
        String ziweiZiRen = ZiWeiDouShuOntologyJobWorker.judgeMingGongGe("紫微", 0, 8);
        assertThat(ziweiZiRen).as("紫微子·壬年→不耐久凶")
                .contains("壬年生人").contains("不耐久").contains("〔凶〕").contains("c3 L463");
        // 紫微在子宫：甲(0)年生 → 原文仅列丁己庚/壬癸，甲未列 → 未命中，空串（不臆造）
        assertThat(ZiWeiDouShuOntologyJobWorker.judgeMingGongGe("紫微", 0, 0))
                .as("紫微子·甲年无对应原文→空串").isEmpty();
        // 天机在子宫（子午宫）：丁(3)年生 → 「财官格」吉（原文 L486「子午宫入庙，丁己癸甲庚壬生人财官格」）
        assertThat(ZiWeiDouShuOntologyJobWorker.judgeMingGongGe("天机", 0, 3))
                .as("天机子·丁年→财官格吉")
                .contains("天机").contains("财官格").contains("〔吉〕").contains("c3 L486");
        // 紫微在午宫（午宫入庙喜甲丁己）：甲(0)年生 → 「财官格」吉（原文 L464，庙旺描述不阻断判定）
        assertThat(ZiWeiDouShuOntologyJobWorker.judgeMingGongGe("紫微", 6, 0))
                .as("紫微午·甲年→财官格吉（含入庙描述仍可判）")
                .contains("财官格").contains("〔吉〕").contains("c3 L464");
        // 太阴在午宫（午宫陷未申宫利益，丁庚甲入生人财官格）：甲(0)年生 → 财官格吉（原文 L622，含 OCR「入」仍可判）
        assertThat(ZiWeiDouShuOntologyJobWorker.judgeMingGongGe("太阴", 6, 0))
                .as("太阴午·甲年→财官格吉（容 OCR 入字）")
                .contains("财官格").contains("〔吉〕");
        // 巨门在子宫 · 庚年生：原文「命坐子卯合局」与「六庚六丁生人辰戌安命却不富贵」分属两句，
        //   不得跨句误配（否则会把「子」错配给「六庚」）→ 应为空串（不臆造）
        assertThat(ZiWeiDouShuOntologyJobWorker.judgeMingGongGe("巨门", 0, 6))
                .as("巨门子·庚年 跨句不误配→空串").isEmpty();
        // 不存在的星 → 空串
        assertThat(ZiWeiDouShuOntologyJobWorker.judgeMingGongGe("不存在之星", 0, 3))
                .as("无该星块→空串").isEmpty();
    }

    @Test
    @DisplayName("重大事件逐流年回推（subjectTraits 结构化事件表）：年份+事件→事项宫流年定位+流年四化〔外部通行法·释读〕")
    void majorEventBacktraceTest() throws Exception {
        // 固定盘（仅需若干主星/禄存以产出符号）：命宫子紫微、午天府禄存、辰太阳文昌、亥太阴、卯天机
        Map<Integer, List<String>> at = new HashMap<>();
        at.put(0, List.of("紫微"));
        at.put(6, List.of("天府", "禄存"));
        at.put(4, List.of("太阳", "文昌"));
        at.put(11, List.of("太阴"));
        at.put(3, List.of("天机"));

        // 两项结构化事件（分隔符切块，避免相邻年份窗口交叉误归类）：2010 结婚→夫妻宫；2015 丧父→父母宫
        String r = ZiWeiDouShuOntologyJobWorker.majorEventBacktrace("2010年结婚；2015年丧父", at);
        assertThat(r).as("应产出非空").isNotNull();
        assertThat(r).contains("共 2 项");
        assertThat(r).contains("事项宫＝妻妾");   // PALACE_NAME[2] 夫妻
        assertThat(r).contains("事项宫＝父母");   // PALACE_NAME[11] 父母
        assertThat(r).contains("流年四化");
        assertThat(r).contains("2010（");          // 干支年
        assertThat(r).contains("2015（");
        assertThat(r).contains("外部通行法");       // 来源标注
        assertThat(r).contains("原述片段");

        // 无年份 / null / 空串 → null（提示文案由调用方补）
        assertThat(ZiWeiDouShuOntologyJobWorker.majorEventBacktrace("结婚 丧父", at))
                .as("无年份→null").isNull();
        assertThat(ZiWeiDouShuOntologyJobWorker.majorEventBacktrace(null, at)).isNull();
        assertThat(ZiWeiDouShuOntologyJobWorker.majorEventBacktrace("", at)).isNull();

        // 事件→事项宫+期望吉凶 映射（反射校验）
        Method ep = ZiWeiDouShuOntologyJobWorker.class.getDeclaredMethod("eventPalaceExpectation", String.class);
        ep.setAccessible(true);
        assertThat((int[]) ep.invoke(null, "2010年结婚")).containsExactly(2, 1);   // 夫妻·吉期望
        assertThat((int[]) ep.invoke(null, "2015年丧父")).containsExactly(11, -1); // 父母·凶期望
        assertThat((int[]) ep.invoke(null, "破财负债")).containsExactly(4, -1);    // 财帛·凶期望
        assertThat((int[]) ep.invoke(null, "2018年升职")).containsExactly(8, 1);   // 官禄·吉期望
        assertThat((Object) ep.invoke(null, "无关描述文字")).as("未识别→null").isNull();
    }

    @Test
    @DisplayName("煞凑条件客观化（羊陀火铃冲会／巨宿羊陀）：短语解析 + 实际盘布尔判定 + 七杀命宫条件式由待考转客观")
    void shaCouBoolJudgeTest() throws Exception {
        Method det = ZiWeiDouShuOntologyJobWorker.class.getDeclaredMethod("detectShaCou", String.class);
        det.setAccessible(true);
        assertThat(det.invoke(null, "无关之语")).as("无煞凑→null").isNull();

        Object scA = det.invoke(null, "辰宫安命六庚生人吉利羊陀火铃冲会又在陷地残疾下局");
        assertThat(scA).as("应识别羊陀火铃冲会").isNotNull();
        Field fStars = scA.getClass().getDeclaredField("stars");
        fStars.setAccessible(true);
        Field fScope = scA.getClass().getDeclaredField("scope");
        fScope.setAccessible(true);
        assertThat((List<String>) fStars.get(scA)).as("羊陀火铃→擎羊/陀罗/火星/铃星")
                .containsExactly("擎羊", "陀罗", "火星", "铃星");
        assertThat(fScope.get(scA)).isEqualTo("三方四正");

        Object scB = det.invoke(null, "七杀居命落闲宫，巨宿羊陀更照冲，若不伤肢必损骨");
        assertThat((List<String>) fStars.get(scB)).as("巨宿羊陀→巨门/擎羊/陀罗")
                .containsExactlyInAnyOrder("巨门", "擎羊", "陀罗");

        // 实际盘布尔：辰(4) 三方四正＝辰(4)/申(8)/子(0)/戌(10)
        Method satM = ZiWeiDouShuOntologyJobWorker.class
                .getDeclaredMethod("shaCouSatisfied", scA.getClass(), int.class, Map.class);
        satM.setAccessible(true);
        Map<Integer, List<String>> at = new HashMap<>();
        at.put(4, new java.util.ArrayList<>(List.of("七杀", "铃星")));
        at.put(8, new java.util.ArrayList<>(List.of("擎羊")));
        at.put(0, new java.util.ArrayList<>(List.of("陀罗")));
        at.put(10, new java.util.ArrayList<>(List.of("火星")));
        assertThat((Boolean) satM.invoke(null, scA, 4, at)).as("四煞俱在三方四正→满足(是)").isTrue();
        at.get(0).remove("陀罗");
        assertThat((Boolean) satM.invoke(null, scA, 4, at)).as("缺陀罗→不满足(否)").isFalse();

        // judgeMingGongGe 四参重载：七杀@辰·庚年，据盘布尔化（不再待考）
        Map<Integer, List<String>> full = new HashMap<>();
        full.put(4, new java.util.ArrayList<>(List.of("七杀", "铃星")));
        full.put(8, new java.util.ArrayList<>(List.of("擎羊")));
        full.put(0, new java.util.ArrayList<>(List.of("陀罗")));
        full.put(10, new java.util.ArrayList<>(List.of("火星")));
        Method mg = ZiWeiDouShuOntologyJobWorker.class
                .getDeclaredMethod("judgeMingGongGe", String.class, int.class, int.class, Map.class);
        mg.setAccessible(true);
        String withSha = (String) mg.invoke(null, "七杀", 4, 6, full);  // 辰(4)·庚(6)，L722
        assertThat(withSha).as("走煞凑布尔分支且煞凑满足").contains("煞凑·布尔").contains("满足(是)");
        String noSha = (String) mg.invoke(null, "七杀", 4, 6, new HashMap<>());
        assertThat(noSha).as("无煞凑→不满足(否)").contains("煞凑·布尔").contains("不满足(否)");
        // 三参旧口径（at==null）仍「条件式·待考」，保持不臆造
        assertThat(ZiWeiDouShuOntologyJobWorker.judgeMingGongGe("七杀", 4, 6))
                .as("无盘面→条件式·待考").contains("条件式·待考");
        // 七杀@戌(10)·壬(8)：L721「不喜壬癸午戌生人」→客观判凶
        String buXi = (String) mg.invoke(null, "七杀", 10, 8, new HashMap<>());
        assertThat(buXi).as("不喜→凶").contains("不喜本人").contains("凶");
    }

    @Test
    @DisplayName("命宫三方四正吉曜会照布尔判定：昌曲/辅弼/魁钺/禄马/科权禄五组 + 会吉会煞计数 + 无凶杀综合〔释读·非新增断法〕")
    void sanFangJiYaoJudgeTest() throws Exception {
        // 命宫子(0)：三方四正＝子(0)/辰(4)/申(8)/戌(6)
        Map<Integer, List<String>> at = new HashMap<>();
        at.put(0, new java.util.ArrayList<>(List.of("文昌", "天魁")));
        at.put(4, new java.util.ArrayList<>(List.of("文曲", "左辅")));
        at.put(8, new java.util.ArrayList<>(List.of("右弼", "天钺")));
        at.put(6, new java.util.ArrayList<>(List.of("禄存", "天马", "太阳")));
        String r = ZiWeiDouShuOntologyJobWorker.judgeSanFangJiYao(0, at, "太阳化禄");
        assertThat(r).as("五组吉曜齐备且科权禄拱").contains("昌曲会命=是").contains("辅弼拱命=是")
                .contains("魁钺坐拱=是").contains("禄马交驰=是").contains("科权禄拱=是");
        assertThat(r).as("会吉计数=7、会煞=0、无凶杀=是").contains("会吉=7").contains("会煞=0")
                .contains("三方无凶杀=是");
        assertThat(r).as("综合→会吉（三方无凶杀）").contains("综合=会吉（三方无凶杀）");

        // 加入擎羊于三方四正 → 会煞=1，吉多于煞→吉煞交参；三方有凶杀=否
        at.get(4).add("擎羊");
        String rSha = ZiWeiDouShuOntologyJobWorker.judgeSanFangJiYao(0, at, "太阳化禄");
        assertThat(rSha).as("会煞=1").contains("会煞=1");
        assertThat(rSha).as("三方有凶杀→否").contains("三方无凶杀=否");
        assertThat(rSha).as("吉煞交参").contains("综合=吉煞交参");

        // 空盘：无吉无煞→中和
        String empty = ZiWeiDouShuOntologyJobWorker.judgeSanFangJiYao(0, new HashMap<>(), null);
        assertThat(empty).as("空盘→中和").contains("会吉=0").contains("会煞=0")
                .contains("三方无凶杀=是").contains("综合=中和（无吉无煞）");

        // 命宫未定 → 不判
        assertThat(ZiWeiDouShuOntologyJobWorker.judgeSanFangJiYao(-1, at, null))
                .as("命宫未定→不判").contains("命宫未定，不判");
    }

    @Test
    @DisplayName("同垣 OR 并列条件客观复核（加吉/加煞/昌曲/左右/魁钺/禄马/羊陀/火铃）：任一命中即成立，按锚宫三方四正复核")
    void tongYuanOrNoteTest() throws Exception {
        Method m = ZiWeiDouShuOntologyJobWorker.class
                .getDeclaredMethod("tongYuanOrNote", String.class, int.class, Map.class);
        m.setAccessible(true);
        // 锚子(0)：三方四正＝子(0)/辰(4)/申(8)/戌(6)；文昌@辰(4)、擎羊@申(8)
        Map<Integer, List<String>> at = new HashMap<>();
        at.put(4, new java.util.ArrayList<>(List.of("文昌")));
        at.put(8, new java.util.ArrayList<>(List.of("擎羊")));

        String ji = (String) m.invoke(null, "天梁加吉坐迁移臣商高贾", 0, at);
        assertThat(ji).as("加吉任一命中（文昌在四方）").contains("加吉=是(文昌)").contains("锚子宫三方四正");

        String sha = (String) m.invoke(null, "贪狼加煞", 0, at);
        assertThat(sha).as("加煞任一命中（擎羊在四方）").contains("加煞=是(擎羊)");

        String both = (String) m.invoke(null, "加吉加煞", 0, at);
        assertThat(both).as("多组并存").contains("加吉=是").contains("加煞=是");

        String changQu = (String) m.invoke(null, "日月夹命夹财加吉曜", 0, at);
        assertThat(changQu).as("加吉（含吉曜）命中").contains("加吉=");

        // 无 OR 关键词 → null（不臆断）
        assertThat((Object) m.invoke(null, "日月守命不如照合并明", 0, at)).isNull();
        assertThat((Object) m.invoke(null, "日巳月酉丑宫命步蟾宫", 0, at)).isNull();
        // 否定守卫：「无吉曜」不得误判为「加吉」（避免语义反转）
        assertThat((Object) m.invoke(null, "贪狼会杀无吉曜屠宰之人", 0, at))
                .as("「无吉曜」为否定，不应触发加吉").isNull();

        // 集成：含加吉待考行移入「OR条件客观复核」段（原文仍在输出中）
        Map<Integer, List<String>> t = new HashMap<>();
        t.put(0, List.of("天梁"));
        String r = judgeTongYuan(0, t, 0, "");
        assertThat(r).as("含 OR 客观复核段").contains("OR条件客观复核");
        assertThat(r).as("天梁加吉条仍在输出且附 OR 复核")
                .contains("天梁加吉坐迁移臣商高贾").contains("OR客观·加吉=");
    }

    @Test
    @DisplayName("同垣断语「自含条件」客观判定：命无正曜/禄存入庙/禄存守财宅/天机加恶杀/太阳化忌/贪狼入庙/武曲迁移/文昌三方/辅弼命迁/府相朝垣")
    void tongYuanObjectiveTest() throws Exception {
        Method m = ZiWeiDouShuOntologyJobWorker.class
                .getDeclaredMethod("tongYuanObjective", String.class, int.class, Map.class, int.class, String.class);
        m.setAccessible(true);

        // 1 命无正曜
        assertThat((String) m.invoke(null, "命无正曜二姓延生", 0, new HashMap<>(), 0, ""))
                .as("命宫空→是").contains("客观·是（命宫无十四主星");
        Map<Integer, List<String>> zi = new HashMap<>();
        zi.put(0, List.of("紫微"));
        assertThat((String) m.invoke(null, "命无正曜二姓延生", 0, zi, 0, "")).as("命宫有主星→否").contains("客观·否");

        // 2 禄存十二宫中皆入庙
        Map<Integer, List<String>> lu = new HashMap<>();
        lu.put(3, List.of("禄存"));
        assertThat((String) m.invoke(null, "禄存十二宫中皆入庙", 0, lu, 0, "")).as("禄存在盘→是").contains("客观·是");

        // 3 禄存守于财宅（财帛=卯(3)/田宅=午(6)）
        assertThat((String) m.invoke(null, "禄存守于财宅积玉堆金", 0, lu, 0, "财帛=卯;田宅=午;"))
                .as("禄存在财帛→是").contains("客观·是（禄存守财帛/田宅");
        Map<Integer, List<String>> luZi = new HashMap<>();
        luZi.put(0, List.of("禄存"));
        assertThat((String) m.invoke(null, "禄存守于财宅积玉堆金", 0, luZi, 0, "财帛=卯;田宅=午;"))
                .as("禄存不在财宅→否").contains("客观·否");

        // 4 天机加恶杀同宫
        Map<Integer, List<String>> jiSha = new HashMap<>();
        jiSha.put(0, new java.util.ArrayList<>(List.of("天机", "擎羊")));
        assertThat((String) m.invoke(null, "天机加恶杀同宫狗偷鼠窃", 0, jiSha, 0, ""))
                .as("天机同宫见煞→是").contains("客观·是（天机同宫见羊陀火铃");
        Map<Integer, List<String>> jiOnly = new HashMap<>();
        jiOnly.put(0, new java.util.ArrayList<>(List.of("天机")));
        assertThat((String) m.invoke(null, "天机加恶杀同宫狗偷鼠窃", 0, jiOnly, 0, ""))
                .as("天机无煞→否").contains("客观·否");

        // 5 太阳化忌（甲干忌=太阳；庚干忌=天同）
        assertThat((String) m.invoke(null, "太阳化忌是非日有目还伤", 0, new HashMap<>(), 0, ""))
                .as("甲年太阳化忌→是").contains("客观·是（生年太阳化忌");
        assertThat((String) m.invoke(null, "太阳化忌是非日有目还伤", 0, new HashMap<>(), 6, ""))
                .as("庚年太阳不化忌→否").contains("客观·否");

        // 6 贪狼入庙（丑=庙；子=旺）
        Map<Integer, List<String>> tl = new HashMap<>();
        tl.put(1, List.of("贪狼"));
        assertThat((String) m.invoke(null, "贪狼入庙寿元长", 0, tl, 0, "")).as("贪狼@丑庙→是").contains("客观·是（贪狼居庙");
        Map<Integer, List<String>> tl2 = new HashMap<>();
        tl2.put(0, List.of("贪狼"));
        assertThat((String) m.invoke(null, "贪狼入庙寿元长", 0, tl2, 0, "")).as("贪狼@子旺→否").contains("客观·否");

        // 7 武曲迁移（迁移=午(6)）
        Map<Integer, List<String>> wq = new HashMap<>();
        wq.put(6, List.of("武曲"));
        assertThat((String) m.invoke(null, "武曲迁移巨商高贾", 0, wq, 0, "迁移=午;"))
                .as("武曲在迁移→是").contains("客观·是（武曲在迁移宫");

        // 8 文科拱照（文昌在命宫三方四正；命子(0)三方含辰(4)）
        Map<Integer, List<String>> wc = new HashMap<>();
        wc.put(4, List.of("文昌"));
        assertThat((String) m.invoke(null, "文科拱照贾谊年少登科（论三方）", 0, wc, 0, ""))
                .as("文昌在三方→是").contains("客观·是（文昌在命宫三方四正");

        // 9 右弼左辅终身福厚（在命宫迁移）
        Map<Integer, List<String>> fb = new HashMap<>();
        fb.put(0, List.of("左辅"));
        assertThat((String) m.invoke(null, "右弼左辅终身福厚（在命宫迁移）", 0, fb, 0, "迁移=午;"))
                .as("左辅在命宫→是").contains("客观·是（左辅/右弼在命宫或迁移宫");

        // 10 府相朝垣（天府/天相俱在三方）
        Map<Integer, List<String>> fx = new HashMap<>();
        fx.put(4, List.of("天府"));
        fx.put(8, List.of("天相"));
        assertThat((String) m.invoke(null, "府相朝垣千锺食禄", 0, fx, 0, ""))
                .as("府相俱在三方→是").contains("客观·是（天府天相俱在命宫三方四正");

        // 无自含条件 → null（保持待考）
        assertThat((Object) m.invoke(null, "天梁守照吉相逢平生福寿", 0, new HashMap<>(), 0, "")).isNull();
    }

    @Test
    @DisplayName("同垣批次②（外部通行法/释读）：巨日寅位/七杀临身/禄存厚重/生处劫空/女命太阳/身命俱吉/纳音墓库 等客观判定")
    void tongYuanObjectiveFullTest() throws Exception {
        Method m = ZiWeiDouShuOntologyJobWorker.class.getDeclaredMethod("tongYuanObjectiveFull",
                String.class, int.class, Map.class, int.class, String.class, Boolean.class, String.class);
        m.setAccessible(true);

        // A5 巨日命宫寅位（寅=2）
        Map<Integer, List<String>> yinJu = new HashMap<>();
        yinJu.put(2, new java.util.ArrayList<>(List.of("巨门", "太阳")));
        assertThat((String) m.invoke(null, "巨日命宫寅位食禄驰名〔举例式·待考〕", 2, yinJu, 0, "五行局=水二局;", true, ""))
                .as("巨日同寅且命立寅→是").contains("客观·是（巨门太阳同在寅且命立寅");

        // A7 七杀临身终是夭（七杀在命）
        Map<Integer, List<String>> sha = new HashMap<>();
        sha.put(0, new java.util.ArrayList<>(List.of("七杀")));
        assertThat((String) m.invoke(null, "七杀临身终是夭", 0, sha, 0, "身宫=午;", true, ""))
                .as("七杀临命→是").contains("客观·是（七杀临命或身宫");

        // A9 禄存厚重多衣禄
        Map<Integer, List<String>> luMing = new HashMap<>();
        luMing.put(0, new java.util.ArrayList<>(List.of("禄存")));
        assertThat((String) m.invoke(null, "禄存厚重多衣禄", 0, luMing, 0, "", true, ""))
                .as("禄存在命→是").contains("客观·是（禄存守命宫");
        Map<Integer, List<String>> luOther = new HashMap<>();
        luOther.put(4, new java.util.ArrayList<>(List.of("禄存")));
        assertThat((String) m.invoke(null, "禄存厚重多衣禄", 0, luOther, 0, "", true, ""))
                .as("禄存不在命→否").contains("客观·否");

        // A11 生处劫空
        Map<Integer, List<String>> kong = new HashMap<>();
        kong.put(0, new java.util.ArrayList<>(List.of("地劫")));
        assertThat((String) m.invoke(null, "生处劫空犹如半天折翅", 0, kong, 0, "", true, ""))
                .as("命见空劫→是").contains("客观·是（命宫见地劫/天空");

        // A14 命有吉星官杀重（命三方：吉+煞≥2）
        Map<Integer, List<String>> jisha = new HashMap<>();
        jisha.put(0, new java.util.ArrayList<>(List.of("文昌")));
        jisha.put(4, new java.util.ArrayList<>(List.of("擎羊")));
        jisha.put(8, new java.util.ArrayList<>(List.of("陀罗")));
        assertThat((String) m.invoke(null, "命有吉星官杀重纵有财官也辛苦", 0, jisha, 0, "", true, ""))
                .as("有吉有煞重→是").contains("客观·是（命三方有吉星而煞重");

        // B1 女命端正太阳星（太阳@午旺，命立午，女命）
        Map<Integer, List<String>> sunWu = new HashMap<>();
        sunWu.put(6, new java.util.ArrayList<>(List.of("太阳")));
        assertThat((String) m.invoke(null, "女命端正太阳星早配贤夫信可凭（陷平常）", 6, sunWu, 0, "", false, ""))
                .as("女命太阳庙旺守命→外部通行法·是").contains("外部通行法·是");
        assertThat((String) m.invoke(null, "女命端正太阳星早配贤夫信可凭（陷平常）", 6, sunWu, 0, "", true, ""))
                .as("男命→否").contains("外部通行法·否");

        // C1 身命俱吉（命/身三方皆吉）
        Map<Integer, List<String>> juJi = new HashMap<>();
        juJi.put(0, new java.util.ArrayList<>(List.of("文昌")));    // 命三方见吉
        juJi.put(6, new java.util.ArrayList<>(List.of("左辅")));    // 身(午)三方见吉
        assertThat((String) m.invoke(null, "身命俱吉富贵双全", 0, juJi, 0, "身宫=午;", true, ""))
                .as("命身皆吉→释读·是").contains("释读·是");

        // D1 纳音墓库（水二局，辰=4 有禄存）
        Map<Integer, List<String>> chenLu = new HashMap<>();
        chenLu.put(4, new java.util.ArrayList<>(List.of("禄存")));
        assertThat((String) m.invoke(null, "纳音墓库看何宫（水生人墓辰遇财官或禄存尤妙）", 0, chenLu, 0, "五行局=水二局;", true, ""))
                .as("水命墓辰得禄存→外部通行法·是").contains("外部通行法·是");

        // 无客观判据 → null（保持待考，不臆造）
        assertThat((Object) m.invoke(null, "破军一曜性难明", 0, new HashMap<>(), 0, "", true, ""))
                .as("纯性质句→null").isNull();

        // A15 双禄守命（命宫禄存+武曲化禄）
        Map<Integer, List<String>> sl = new HashMap<>();
        sl.put(0, new java.util.ArrayList<>(List.of("禄存", "武曲")));
        assertThat((String) m.invoke(null, "双禄守命吕后专权", 0, sl, 0, "", true, "武曲化禄"))
                .as("命宫双禄→是").contains("客观·是（命宫同见禄存与化禄");

        // A16 双禄重返（命三方禄存+化禄）
        Map<Integer, List<String>> sl2 = new HashMap<>();
        sl2.put(0, new java.util.ArrayList<>(List.of("禄存")));
        sl2.put(4, new java.util.ArrayList<>(List.of("武曲")));
        assertThat((String) m.invoke(null, "双禄重返终身富贵", 0, sl2, 0, "", true, "武曲化禄"))
                .as("命三方双禄→是").contains("客观·是（命三方同会禄存与化禄");

        // A17 日月命身居丑未三方无吉（命居丑、三方无吉）
        assertThat((String) m.invoke(null, "日月命身居丑未三方无吉反为凶（子午辰戌身命更佳）〔举例式·待考〕",
                1, new HashMap<>(), 0, "身宫=午;", true, ""))
                .as("命居丑三方无吉→释读·是").contains("释读·是（命/身居丑未且命三方无吉");
    }

    @Test
    @DisplayName("卷三·谈星要论其余断诀（羊陀迭并/七杀重逢/流年太岁/大限十年祸福/小儿命/男女命同异）")
    void juanSanRemainingTest() throws Exception {
        // 羊陀迭并：本命擎羊@午(6)、命立午(6)；流年干甲→禄存@寅(2)，流羊@卯(3)、流陀@丑(1)（不冲午）
        // 换流年干庚→禄存@申(8)？ LUCUN[庚=6]=8 → 流羊@酉(9)、流陀@申(8)，仍不冲午。
        // 直接构造：本命擎羊@午，取流年干使流陀或流羊@午或其冲子(0)。LUCUN 表中无 4(辰)/10(戌)? 取流羊=午→LUCUN=4(戊)？ LUCUN[4]=5(巳) → 流羊=6(午)✓
        Method dieBing = ZiWeiDouShuOntologyJobWorker.class
                .getDeclaredMethod("judgeYangTuoDieBing", int.class, Map.class, int.class);
        dieBing.setAccessible(true);
        Map<Integer, List<String>> yangWu = new HashMap<>();
        yangWu.put(6, new java.util.ArrayList<>(List.of("擎羊")));   // 本命擎羊@午，命立午
        // LUCUN[4]=5 → 流羊=6(午) 命中 → 迭并=是
        assertThat((Boolean) dieBing.invoke(null, 6, yangWu, 4)).as("本命羊@午遇流羊@午→迭并").isTrue();
        // 流年干甲(0) LUCUN=2 → 流羊=3、流陀=1，不冲午/子 → 否
        assertThat((Boolean) dieBing.invoke(null, 6, yangWu, 0)).as("流羊陀不冲命→否").isFalse();
        // 本命擎羊不在命/迁 → 否（擎羊@寅(2)，命立午(6)，迁移=子(0)，均非）
        Map<Integer, List<String>> yangYin = new HashMap<>();
        yangYin.put(2, new java.util.ArrayList<>(List.of("擎羊")));
        assertThat((Boolean) dieBing.invoke(null, 6, yangYin, 4)).as("原羊不在命/迁→否").isFalse();

        // 七杀重逢：本命七杀居命宫三方（命立子0，七杀@申8=三合），流年流羊/陀冲申或寅
        Method qiSha = ZiWeiDouShuOntologyJobWorker.class
                .getDeclaredMethod("judgeQiShaReFeng", int.class, Map.class, int.class);
        qiSha.setAccessible(true);
        Map<Integer, List<String>> qs = new HashMap<>();
        qs.put(8, new java.util.ArrayList<>(List.of("七杀")));       // 七杀@申，命立子(0)→申为三合
        // 取流年干使流羊/陀=申(8)或寅(2)。LUCUN[6(庚)]=8→流羊=9、流陀=7 不中；LUCUN[0(甲)]=2→流羊=3、流陀=1 不中
        // 取 LUCUN=7(未)? 无。取流陀=申(8)→LUCUN=9(癸)? LUCUN[9]=0→流陀=11，流羊=1。取流羊=寅(2)→LUCUN=1(乙)? LUCUN[1]=3→流羊=4。不中。
        // 直接令冲宫：取流年干 4(戊) LUCUN=5→流羊=6、流陀=4，仍不中申/寅。改用七杀@命宫本身以简化：命立申(8)、七杀@申(8)；流羊=8 需 LUCUN=7(未)? 无。
        // 为稳健：七杀居命宫(命立子0→改命立申8, 七杀@申8)，取 LUCUN[6]=8→流陀=7,流羊=9（不中）；取能命中者 LUCUN=9(1)? 仍无。
        // 用对宫冲照判据：七杀@申(8)，其对宫寅(2)；取流年干 0(甲) LUCUN=2→流羊=3 否。取 LUCUN 使流羊/陀=寅(2)：LUCUN=1或3 → 乙(3)LUCUN[1]=3→流陀=2(寅)✓
        Map<Integer, List<String>> qsMing = new HashMap<>();
        qsMing.put(8, new java.util.ArrayList<>(List.of("七杀")));   // 命立申，七杀守命
        assertThat((Boolean) qiSha.invoke(null, 8, qsMing, 1)).as("七杀守命遇流陀冲寅→重逢").isTrue();
        assertThat((Boolean) qiSha.invoke(null, 8, qsMing, 0)).as("流羊陀不冲→否").isFalse();
        // 七杀不在命三方 → 否（七杀@巳(5)，命立子(0)，巳不在子之三合/对宫）
        Map<Integer, List<String>> qsOut = new HashMap<>();
        qsOut.put(5, new java.util.ArrayList<>(List.of("七杀")));
        assertThat((Boolean) qiSha.invoke(null, 0, qsOut, 1)).as("七杀不在命三方→否").isFalse();

        // 流年太岁吉凶星杀：太岁@命宫 + 三方见吉
        Method taiSui = ZiWeiDouShuOntologyJobWorker.class
                .getDeclaredMethod("judgeLiuNianTaiSui", int.class, int.class, Map.class);
        taiSui.setAccessible(true);
        Map<Integer, List<String>> tsJi = new HashMap<>();
        tsJi.put(0, new java.util.ArrayList<>(List.of("文昌")));
        String tsOnMing = (String) taiSui.invoke(null, 0, 0, tsJi);
        assertThat(tsOnMing).as("太岁临命宫").contains("正临命宫");
        assertThat(tsOnMing).as("太岁宫三方含吉凶计数").contains("太岁宫三方");

        // 大限十年祸福：大限宫无煞→十年安静；有煞且陷→官灾之虞
        Method dx = ZiWeiDouShuOntologyJobWorker.class
                .getDeclaredMethod("judgeDaXianShiNian", int.class, Map.class, int.class);
        dx.setAccessible(true);
        Map<Integer, List<String>> dxClean = new HashMap<>();
        dxClean.put(4, new java.util.ArrayList<>(List.of("天府")));  // 辰宫天府，无煞
        assertThat((String) dx.invoke(null, 4, dxClean, 0)).as("无煞→十年安静").contains("十年安静");
        Map<Integer, List<String>> dxSha = new HashMap<>();
        dxSha.put(4, new java.util.ArrayList<>(List.of("擎羊", "陀罗")));
        assertThat((String) dx.invoke(null, 4, dxSha, 0)).as("见煞→成败不一").contains("成败不一");
        assertThat((String) dx.invoke(null, -1, dxClean, 0)).as("未起限→不判").contains("尚未起限");

        // 论小儿命：命坐庙旺无煞→灾少易养；命坐恶杀→灾多难养
        Method xiaoEr = ZiWeiDouShuOntologyJobWorker.class
                .getDeclaredMethod("judgeXiaoErMing", int.class, Map.class);
        xiaoEr.setAccessible(true);
        Map<Integer, List<String>> xrMiao = new HashMap<>();
        xrMiao.put(6, new java.util.ArrayList<>(List.of("太阳")));   // 太阳@午为旺
        assertThat((String) xiaoEr.invoke(null, 6, xrMiao)).as("命坐庙旺无煞→易养").contains("灾少易养");
        Map<Integer, List<String>> xrSha = new HashMap<>();
        xrSha.put(6, new java.util.ArrayList<>(List.of("擎羊")));
        assertThat((String) xiaoEr.invoke(null, 6, xrSha)).as("命坐恶杀→难养").contains("灾多难养");

        // 论男女命同异：女命身命见贪狼/七杀/擎羊→不美
        Method nanNv = ZiWeiDouShuOntologyJobWorker.class
                .getDeclaredMethod("judgeNanNvTongYi", int.class, List.class, Map.class, String.class);
        nanNv.setAccessible(true);
        assertThat((String) nanNv.invoke(null, 0, new java.util.ArrayList<>(List.of("贪狼")), new HashMap<>(), "命宫=子;"))
                .as("女命见贪狼→不美").contains("不美");
        assertThat((String) nanNv.invoke(null, 0, new java.util.ArrayList<>(List.of("天府")), new HashMap<>(), "命宫=子;"))
                .as("女命未见忌星→无非美").contains("无「不美」之忌");
    }
}
