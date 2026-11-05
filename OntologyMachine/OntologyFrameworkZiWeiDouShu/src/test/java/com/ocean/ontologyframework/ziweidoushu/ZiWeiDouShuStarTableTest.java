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
    @DisplayName("诸格论·12 格全覆盖补测（卷一 c2 L510-544）：艺术/孤克/寿夭/淫荡/破相/富足/贫贱/盗贼/驳杂")
    void longevityAllCategoriesTest() throws Exception {
        // 艺术：寅安命逢贪狼
        Map<Integer, List<String>> art = new HashMap<>();
        art.put(2, List.of("贪狼"));
        assertThat(judgeLongevity(2, -1, art)).as("寅逢贪狼→艺术").anyMatch(s -> s.contains("艺术"));

        // 孤克：命在子，夫妻(戌10)巨门(陷)+擎羊
        Map<Integer, List<String>> gu = new HashMap<>();
        gu.put(10, List.of("巨门", "擎羊"));
        assertThat(judgeLongevity(0, -1, gu)).as("夫妻宫落陷加杀→孤克").anyMatch(s -> s.contains("孤克"));

        // 寿夭：身宫七杀
        Map<Integer, List<String>> shou = new HashMap<>();
        shou.put(6, List.of("七杀"));
        assertThat(judgeLongevity(0, 6, shou)).as("七杀临身→寿夭").anyMatch(s -> s.contains("寿夭"));

        // 淫荡：贪狼入庙（丑）
        Map<Integer, List<String>> yin = new HashMap<>();
        yin.put(1, List.of("贪狼"));
        assertThat(judgeLongevity(1, -1, yin)).as("贪狼入庙定为娼").anyMatch(s -> s.contains("淫荡"));

        // 破相：疾厄(未7)擎羊
        Map<Integer, List<String>> po = new HashMap<>();
        po.put(7, List.of("擎羊"));
        assertThat(judgeLongevity(0, -1, po)).as("疾厄擎羊→破相").anyMatch(s -> s.contains("破相"));

        // 富足：太阴入庙（子）
        Map<Integer, List<String>> fu = new HashMap<>();
        fu.put(0, List.of("太阴"));
        assertThat(judgeLongevity(0, -1, fu)).as("太阴入庙无破耗→富足").anyMatch(s -> s.contains("富足"));

        // 贫贱：三方火忌羊陀 + 武曲
        Map<Integer, List<String>> pin = new HashMap<>();
        pin.put(0, List.of("武曲"));
        pin.put(4, List.of("擎羊"));
        assertThat(judgeLongevity(0, -1, pin)).as("火忌羊陀+武曲→贫贱").anyMatch(s -> s.contains("贫贱"));

        // 盗贼：命破军 + 三方七杀
        Map<Integer, List<String>> dao = new HashMap<>();
        dao.put(0, List.of("破军"));
        dao.put(4, List.of("七杀"));
        assertThat(judgeLongevity(0, -1, dao)).as("命破军七杀照→盗贼").anyMatch(s -> s.contains("盗贼"));

        // 驳杂：吉曜(左辅)+凶曜(擎羊)
        Map<Integer, List<String>> bo = new HashMap<>();
        bo.put(0, List.of("左辅"));
        bo.put(4, List.of("擎羊"));
        assertThat(judgeLongevity(0, -1, bo)).as("吉凶交杂→一身驳杂").anyMatch(s -> s.contains("一身驳杂"));
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

    @Test
    @DisplayName("卷三·术者验时法：定小儿生时诀（顶门/面向天三组）＋论人生时要审的确（子亥难定/罗经）")
    void birthTimeVerifyTest() throws Exception {
        Method m = ZiWeiDouShuOntologyJobWorker.class.getDeclaredMethod("birthTimeVerifyText", int.class);
        m.setAccessible(true);

        // 子午卯酉组：单顶门、面向天
        String zi = (String) m.invoke(null, 0);   // 子
        assertThat(zi).as("子属子午卯酉组").contains("子午卯酉").contains("面向天");
        String wu = (String) m.invoke(null, 6);   // 午
        assertThat(wu).as("午属子午卯酉组").contains("子午卯酉").contains("面向天");
        // 子时属「子亥二时最难定」→ 推详上午刻/下午刻 + 罗经
        assertThat(zi).as("子时最难定").contains("子亥二时最难定").contains("上午刻").contains("罗经");

        // 寅申巳亥组：单顶偏右、侧身眠
        String yin = (String) m.invoke(null, 2);   // 寅
        assertThat(yin).as("寅属寅申巳亥组").contains("寅申巳亥").contains("侧身眠");
        String hai = (String) m.invoke(null, 11);  // 亥
        assertThat(hai).as("亥属寅申巳亥组且子亥难定").contains("寅申巳亥").contains("子亥二时最难定");

        // 辰戌丑未组：双顶、脸伏地
        String chen = (String) m.invoke(null, 4);  // 辰
        assertThat(chen).as("辰属辰戌丑未组").contains("辰戌丑未").contains("脸伏地").contains("双顶");
        // 非子亥时仍提示阴雨罗经谨慎
        assertThat(chen).as("非子亥仍戒罗经").contains("罗经").doesNotContain("子亥二时最难定");
        // 原文出处标注
        assertThat(zi).as("标注定小儿生时诀出处").contains("c4 L62-65").contains("c4 L68-69");
    }

    @Test
    @DisplayName("卷一·諸星問答論 37 问落地完整性（十四主星化气 + 辅煞杂曜所主 + 化禄权科忌 + 二总论）")
    void zhuXingWenDaCoverageTest() throws Exception {
        java.lang.reflect.Field fMain = ZiWeiDouShuOntologyJobWorker.class.getDeclaredField("STAR_CHARACTER");
        fMain.setAccessible(true);
        String[][] main = (String[][]) fMain.get(null);
        assertThat(main.length).as("十四主星化气").isEqualTo(14);

        java.lang.reflect.Field fAux = ZiWeiDouShuOntologyJobWorker.class.getDeclaredField("STAR_QA_CHARACTER");
        fAux.setAccessible(true);
        String[][] aux = (String[][]) fAux.get(null);
        assertThat(aux.length).as("辅/煞/杂曜所主条数").isEqualTo(20);
        java.util.Set<String> stars = new java.util.HashSet<>();
        for (String[] r : aux) { stars.add(r[0]); }
        // 覆盖昌曲/辅弼/魁钺/禄存/天马/羊陀/火铃/空劫/伤使/刑姚/哭虚
        for (String s : new String[]{"文昌", "文曲", "左辅", "右弼", "天魁", "天钺", "禄存", "天马",
                "擎羊", "陀罗", "火星", "铃星", "天空", "地劫", "天伤", "天使", "天刑", "天姚", "天哭", "天虚"}) {
            assertThat(stars).as("已覆盖 " + s).contains(s);
        }

        java.lang.reflect.Field fHua = ZiWeiDouShuOntologyJobWorker.class.getDeclaredField("SIHUA_QA");
        fHua.setAccessible(true);
        String[] hua = (String[]) fHua.get(null);
        assertThat(hua.length).as("化禄/权/科/忌 所主四条").isEqualTo(4);
        assertThat(hua[3]).as("化忌为多管之神").contains("多管之神");
    }

    @Test
    @DisplayName("卷一·斗數骨髓賦 夹拱格（魁钺/日月/昌曲/空劫/羊陀 分坐命宫左右两夹）")
    void jiaGongJudgeTest() throws Exception {
        Method jia = ZiWeiDouShuOntologyJobWorker.class
                .getDeclaredMethod("judgeJiaGong", int.class, Map.class);
        jia.setAccessible(true);
        // 命宫=午(6)：左夹(5巳)、右夹(7未)
        Map<Integer, List<String>> kuiYue = new HashMap<>();
        kuiYue.put(5, new java.util.ArrayList<>(List.of("天魁")));
        kuiYue.put(7, new java.util.ArrayList<>(List.of("天钺")));
        assertThat((String) jia.invoke(null, 6, kuiYue)).as("夹魁钺").contains("夹魁钺");

        Map<Integer, List<String>> riYue = new HashMap<>();
        riYue.put(5, new java.util.ArrayList<>(List.of("太阳")));
        riYue.put(7, new java.util.ArrayList<>(List.of("太阴")));
        assertThat((String) jia.invoke(null, 6, riYue)).as("夹日月").contains("夹日月");

        Map<Integer, List<String>> changQu = new HashMap<>();
        changQu.put(5, new java.util.ArrayList<>(List.of("文昌")));
        changQu.put(7, new java.util.ArrayList<>(List.of("文曲")));
        assertThat((String) jia.invoke(null, 6, changQu)).as("夹昌曲").contains("夹昌曲");

        Map<Integer, List<String>> kongJie = new HashMap<>();
        kongJie.put(5, new java.util.ArrayList<>(List.of("地空")));
        kongJie.put(7, new java.util.ArrayList<>(List.of("地劫")));
        assertThat((String) jia.invoke(null, 6, kongJie)).as("夹空劫→主贫贱").contains("夹空劫").contains("贫贱");

        Map<Integer, List<String>> yangTuo = new HashMap<>();
        yangTuo.put(5, new java.util.ArrayList<>(List.of("擎羊")));
        yangTuo.put(7, new java.util.ArrayList<>(List.of("陀罗")));
        assertThat((String) jia.invoke(null, 6, yangTuo)).as("夹羊陀→主乞丐").contains("夹羊陀").contains("乞丐");

        // 两夹宫同一天平星（同在一侧）不成夹：两侧均为普通吉星
        Map<Integer, List<String>> none = new HashMap<>();
        none.put(5, new java.util.ArrayList<>(List.of("天府")));
        none.put(7, new java.util.ArrayList<>(List.of("天同")));
        assertThat((String) jia.invoke(null, 6, none)).as("无对星分坐→不成夹格").contains("不成夹格");

        assertThat((String) jia.invoke(null, -1, none)).as("命宫未定→不判").contains("命宫未定");
    }

    @Test
    @DisplayName("卷一·女命骨髓賦 逐句客观判定（府相/贪狼/无正曜/太阳旺地/命宫未定）")
    void femaleBoneMarrowTest() throws Exception {
        Method fm = ZiWeiDouShuOntologyJobWorker.class
                .getDeclaredMethod("judgeFemaleBoneMarrow", int.class, List.class, Map.class, String.class);
        fm.setAccessible(true);
        // 府相之星：命宫(子0)=天府、三方(辰4)=天相
        Map<Integer, List<String>> at1 = new HashMap<>();
        at1.put(0, new java.util.ArrayList<>(List.of("天府")));
        at1.put(4, new java.util.ArrayList<>(List.of("天相")));
        assertThat((String) fm.invoke(null, 0, new java.util.ArrayList<>(List.of("天府")), at1, "命宫=子;夫妻=午;子女=卯;福德=酉;"))
                .as("府相之星→子贵夫贤").contains("府相之星");

        // 贪狼坐命→淫佚
        Map<Integer, List<String>> at2 = new HashMap<>();
        at2.put(0, new java.util.ArrayList<>(List.of("贪狼")));
        assertThat((String) fm.invoke(null, 0, new java.util.ArrayList<>(List.of("贪狼")), at2, "命宫=子;"))
                .as("贪狼→内狠多淫佚").contains("贪狼→内狠多淫佚");

        // 太阳寅到午（太阳在辰=4）→遇吉终是福
        Map<Integer, List<String>> at3 = new HashMap<>();
        at3.put(4, new java.util.ArrayList<>(List.of("太阳")));
        assertThat((String) fm.invoke(null, 0, new java.util.ArrayList<>(List.of()), at3, "命宫=子;"))
                .as("太阳旺地→福").contains("太阳寅到午");

        // 本宫无正曜→生离克害
        Map<Integer, List<String>> at4 = new HashMap<>();
        assertThat((String) fm.invoke(null, 0, new java.util.ArrayList<>(), at4, "命宫=子;"))
                .as("无正曜→生离克害").contains("本宫无正曜");

        // 命宫未定→不判
        assertThat((String) fm.invoke(null, -1, new java.util.ArrayList<>(), new HashMap<>(), ""))
                .as("命宫未定→不判").contains("命宫未定");
    }

    @Test
    @DisplayName("卷一·太微賦等五赋具名格二次收敛（魁钺同行/禄文拱命/日月夹财/刑囚夹印/财居财位/耗居禄位/辅弼单守/杀临身命）")
    void taiweiMingGeExtendedTest() throws Exception {
        Method me = ZiWeiDouShuOntologyJobWorker.class
                .getDeclaredMethod("judgeTaiweiMingGeExtended", int.class, Map.class, java.util.Set.class, java.util.Set.class, String.class);
        me.setAccessible(true);
        java.util.Set<String> mm = new java.util.HashSet<>(List.of("紫微"));
        java.util.List<String> res;

        // 魁钺同行：天魁天钺同宫(子0)
        Map<Integer, List<String>> at1 = new HashMap<>();
        at1.put(0, new java.util.ArrayList<>(List.of("天魁", "天钺")));
        res = (java.util.List<String>) me.invoke(null, 0, at1, mm, new java.util.HashSet<>(List.of("紫微", "天魁", "天钺")), "命宫=子;");
        assertThat(String.join("|", res)).as("魁钺同行").contains("魁钺同行");

        // 禄文拱命：命宫文昌 + 三方(辰4)禄存
        Map<Integer, List<String>> at2 = new HashMap<>();
        at2.put(0, new java.util.ArrayList<>(List.of("紫微", "文昌")));
        at2.put(4, new java.util.ArrayList<>(List.of("禄存")));
        res = (java.util.List<String>) me.invoke(null, 0, at2, mm, new java.util.HashSet<>(List.of("紫微", "文昌")), "命宫=子;");
        assertThat(String.join("|", res)).as("禄文拱命").contains("禄文拱命");

        // 日月夹财：财帛(午6)左右夹太阳/太阴
        Map<Integer, List<String>> at3 = new HashMap<>();
        at3.put(5, new java.util.ArrayList<>(List.of("太阳")));
        at3.put(7, new java.util.ArrayList<>(List.of("太阴")));
        res = (java.util.List<String>) me.invoke(null, 0, at3, mm, new java.util.HashSet<>(List.of("紫微")), "命宫=子;财帛=午;");
        assertThat(String.join("|", res)).as("日月夹财").contains("日月夹财");

        // 刑囚夹印：擎羊/廉贞夹天相(子0左右亥11/丑1)
        Map<Integer, List<String>> at4 = new HashMap<>();
        at4.put(0, new java.util.ArrayList<>(List.of("天相")));
        at4.put(11, new java.util.ArrayList<>(List.of("擎羊")));
        at4.put(1, new java.util.ArrayList<>(List.of("廉贞")));
        res = (java.util.List<String>) me.invoke(null, 0, at4, mm, new java.util.HashSet<>(List.of("紫微")), "命宫=子;");
        assertThat(String.join("|", res)).as("刑囚夹印").contains("刑囚夹印");

        // 财居财位：财帛(午6)守武曲
        Map<Integer, List<String>> at5 = new HashMap<>();
        at5.put(6, new java.util.ArrayList<>(List.of("武曲")));
        res = (java.util.List<String>) me.invoke(null, 0, at5, mm, new java.util.HashSet<>(List.of("紫微")), "命宫=子;财帛=午;");
        assertThat(String.join("|", res)).as("财居财位").contains("财居财位");

        // 耗居禄位：破军与禄存同宫(子0)
        Map<Integer, List<String>> at6 = new HashMap<>();
        at6.put(0, new java.util.ArrayList<>(List.of("破军", "禄存")));
        res = (java.util.List<String>) me.invoke(null, 0, at6, mm, new java.util.HashSet<>(List.of("紫微")), "命宫=子;");
        assertThat(String.join("|", res)).as("耗居禄位").contains("耗居禄位");

        // 辅弼单守命宫：命宫无主星、见左辅
        Map<Integer, List<String>> at7 = new HashMap<>();
        at7.put(0, new java.util.ArrayList<>(List.of("左辅")));
        res = (java.util.List<String>) me.invoke(null, 0, at7, new java.util.HashSet<>(), new java.util.HashSet<>(List.of("左辅")), "命宫=子;");
        assertThat(String.join("|", res)).as("辅弼单守").contains("辅弼单守");

        // 杀临身命加恶杀：七杀守命 + 擎羊
        Map<Integer, List<String>> at8 = new HashMap<>();
        at8.put(0, new java.util.ArrayList<>(List.of("七杀", "擎羊")));
        res = (java.util.List<String>) me.invoke(null, 0, at8, new java.util.HashSet<>(List.of("七杀")), new java.util.HashSet<>(List.of("七杀", "擎羊")), "命宫=子;");
        assertThat(String.join("|", res)).as("杀临身命加恶杀").contains("杀临身命加恶杀");
    }

    @Test
    @DisplayName("卷二·十二宫各星吉凶诀 交叉校验（palace_classic 12 宫全覆盖 + palaceStarQuote 回溯原文行号）")
    void palaceClassicCoverageTest() throws Exception {
        Method load = ZiWeiDouShuOntologyJobWorker.class.getDeclaredMethod("loadPalaceClassic");
        load.setAccessible(true);
        java.util.Map<String, java.util.List<String[]>> m =
                (java.util.Map<String, java.util.List<String[]>>) load.invoke(null);
        assertThat(m.size()).as("十二宫齐备").isEqualTo(12);
        // 各宫内容行计数（与 c3 章节区间严格一致；11 章节标题行按设计排除）
        java.util.Map<String, Integer> expect = new java.util.HashMap<>();
        expect.put("命宫", 518); expect.put("兄弟", 22); expect.put("妻妾", 23); expect.put("子女", 32);
        expect.put("财帛", 29); expect.put("疾厄", 23); expect.put("迁移", 24); expect.put("奴仆", 24);
        expect.put("官禄", 37); expect.put("田宅", 23); expect.put("福德", 27); expect.put("父母", 31);
        int total = 0;
        for (java.util.Map.Entry<String, Integer> e : expect.entrySet()) {
            int got = m.getOrDefault(e.getKey(), java.util.List.of()).size();
            assertThat(got).as("宫[" + e.getKey() + "]内容行数").isEqualTo(e.getValue());
            total += got;
        }
        assertThat(total).as("卷二十二宫内容行合计").isEqualTo(813);

        // palaceStarQuote：回溯原文首句 + 行号
        Method quote = ZiWeiDouShuOntologyJobWorker.class
                .getDeclaredMethod("palaceStarQuote", String.class, String.class);
        quote.setAccessible(true);
        String zi = (String) quote.invoke(null, "命宫", "紫微");
        assertThat(zi).as("命宫·紫微回溯").contains("紫微土").contains("c3 L459");
        String ji = (String) quote.invoke(null, "命宫", "天机");
        assertThat(ji).as("命宫·天机回溯").contains("天机属木");
        String lz = (String) quote.invoke(null, "兄弟", "廉贞");
        assertThat(lz).as("兄弟·廉贞回溯").isNotEmpty().contains("廉贞");
        assertThat((String) quote.invoke(null, "命宫", "不存在之星")).as("无记载返回空").isEmpty();
    }

    @SuppressWarnings("unchecked")
    private static List<String> judgeAlertTypes(List<String> mingStars, List<String> jiEStars,
                                                boolean evilPatternHit, boolean liangFallen) throws Exception {
        Method m = ZiWeiDouShuOntologyJobWorker.class.getDeclaredMethod(
                "judgeAlertTypes", List.class, List.class, boolean.class, boolean.class);
        m.setAccessible(true);
        return (List<String>) m.invoke(null, mingStars, jiEStars, evilPatternHit, liangFallen);
    }

    @Test
    @DisplayName("C-1 告警三类客观凶判据（R18 大凶/R19 寿元关/R20 重大疾病）· 废弃裸阈值 shaCount>=3")
    void alertCategoriesTest() throws Exception {
        // R18 大凶：凶格引动（羊陀迭并/七杀重逢）
        assertThat(judgeAlertTypes(List.of(), List.of(), true, false)).as("凶格引动→大凶").contains("大凶");
        // R18 大凶：命宫双煞同宫（羊陀/火铃/空劫同宫为凶格）
        assertThat(judgeAlertTypes(List.of("擎羊", "陀罗"), List.of(), false, false)).as("命宫羊陀同宫→大凶").contains("大凶");
        // R19 寿元关：天梁（寿星）守命且见煞
        assertThat(judgeAlertTypes(List.of("天梁", "擎羊"), List.of(), false, false)).as("天梁见煞→寿元关").contains("寿元关");
        // R19 寿元关：天梁落陷
        assertThat(judgeAlertTypes(List.of("天梁"), List.of(), false, true)).as("天梁落陷→寿元关").contains("寿元关");
        // R20 重大疾病：疾厄宫逢擎羊
        assertThat(judgeAlertTypes(List.of(), List.of("擎羊"), false, false)).as("疾厄羊→重大疾病").contains("重大疾病");
        // R20 重大疾病：疾厄宫空劫同宫
        assertThat(judgeAlertTypes(List.of(), List.of("地劫", "天空"), false, false)).as("疾厄空劫同宫→重大疾病").contains("重大疾病");
        // 无凶判据 → 不触发
        assertThat(judgeAlertTypes(List.of("紫微", "天机"), List.of("天同"), false, false)).as("无凶判据→不触发").isEmpty();
        // 命宫散煞三颗但不同宫、无凶格/无疾厄/无天梁 → 不因裸计数（旧 shaCount>=3）触发
        assertThat(judgeAlertTypes(List.of("擎羊", "火星", "地劫"), List.of(), false, false)).as("散煞未凑双煞→不触发").isEmpty();
        // 多类同时命中
        List<String> multi = judgeAlertTypes(List.of("天梁", "擎羊", "陀罗"), List.of("火星", "铃星"), true, false);
        assertThat(multi).as("多类同时命中").contains("大凶", "寿元关", "重大疾病");
    }

    private static String extractAlertTypeFromReading(String reading) throws Exception {
        Method m = ZiWeiDouShuOntologyJobWorker.class.getDeclaredMethod("extractAlertTypeFromReading", String.class);
        m.setAccessible(true);
        return (String) m.invoke(null, reading);
    }

    @Test
    @DisplayName("D-2 从 reading 解析告警类别（BPMN 未映射 alertType 时 sendAlert 的运行期兜底）")
    void extractAlertTypeFromReadingTest() throws Exception {
        String line = "九、告警判据（三类客观凶判据）：命宫煞=1，命宫双煞同宫=false，疾厄煞=2，凶格引动=false；命中=重大疾病（触发外发）";
        assertThat(extractAlertTypeFromReading(line)).as("单类").isEqualTo("重大疾病");
        String multi = "…；命中=大凶/寿元关/重大疾病（触发外发）\n十、";
        assertThat(extractAlertTypeFromReading(multi)).as("多类").isEqualTo("大凶/寿元关/重大疾病");
        assertThat(extractAlertTypeFromReading("…；命中=无（不触发）")).as("无→空").isEmpty();
        assertThat(extractAlertTypeFromReading("无该字段")).as("无字段→空").isEmpty();
        assertThat(extractAlertTypeFromReading(null)).as("null→空").isEmpty();
    }

    private static int trailingInt(String s) throws Exception {
        Method m = ZiWeiDouShuOntologyJobWorker.class.getDeclaredMethod("trailingInt", String.class);
        m.setAccessible(true);
        return (int) m.invoke(null, s);
    }

    @Test
    @DisplayName("十二神定序：rule_id 尾部编号按**数值**解析（DR_Shen_9=9 而非字符串 09 错序）")
    void trailingIntNumericOrderTest() throws Exception {
        assertThat(trailingInt("DR_Shen_01")).as("零填充").isEqualTo(1);
        assertThat(trailingInt("DR_Shen_12")).as("两位").isEqualTo(12);
        assertThat(trailingInt("DR_Shen_9")).as("非填充个位").isEqualTo(9);
        assertThat(trailingInt("DR_Shen_10")).as("非填充十位").isEqualTo(10);
        // 关键：数值序 9 < 10（字符串序 "09" > "10" 会颠倒——正是本次加固要消除的隐患）
        assertThat(trailingInt("DR_Shen_9")).as("数值序 9<10").isLessThan(trailingInt("DR_Shen_10"));
        assertThat(trailingInt("NoDigitsHere")).as("无数字→-1").isEqualTo(-1);
        assertThat(trailingInt(null)).as("null→-1").isEqualTo(-1);
    }

    private static void setObdaPath(Object worker, String path) throws Exception {
        Field f = ZiWeiDouShuOntologyJobWorker.class.getDeclaredField("obdaPath");
        f.setAccessible(true);
        f.set(worker, path);
    }

    private static void invokeTableCoverage(Object worker) throws Exception {
        Method m = ZiWeiDouShuOntologyJobWorker.class.getDeclaredMethod("verifyObdaTableCoverage");
        m.setAccessible(true);
        m.invoke(worker);
    }

    @Test
    @DisplayName("DDL↔OBDA 表覆盖自检：全覆盖放行、缺映射 fail-fast 抛 IllegalStateException")
    void obdaTableCoverageFailFastTest() throws Exception {
        java.nio.file.Path dir = java.nio.file.Files.createTempDirectory("zwcov");
        java.nio.file.Path ddl = dir.resolve("createdb_tables.ddl");
        java.nio.file.Path obda = dir.resolve("x.obda");
        try {
            java.nio.file.Files.writeString(ddl,
                    "CREATE TABLE `t1` (id int);\nCREATE TABLE `t2` (id int);\n");

            // 例1：DDL 两表均有映射 → 放行（不抛）
            java.nio.file.Files.writeString(obda,
                    "mappingId m1\ntarget <a> a <b> .\nsource SELECT id FROM t1\n"
                            + "mappingId m2\ntarget <c> a <d> .\nsource SELECT id FROM t2\n");
            ZiWeiDouShuOntologyJobWorker w1 = new ZiWeiDouShuOntologyJobWorker();
            setObdaPath(w1, obda.toString());
            invokeTableCoverage(w1);   // 若抛异常则本行失败

            // 例2：缺 t2 映射 → fail-fast
            java.nio.file.Files.writeString(obda,
                    "mappingId m1\ntarget <a> a <b> .\nsource SELECT id FROM t1\n");
            ZiWeiDouShuOntologyJobWorker w2 = new ZiWeiDouShuOntologyJobWorker();
            setObdaPath(w2, obda.toString());
            Throwable t = org.assertj.core.api.Assertions.catchThrowable(() -> invokeTableCoverage(w2));
            assertThat(t).as("缺映射应抛异常").isNotNull();
            Throwable cause = (t instanceof java.lang.reflect.InvocationTargetException) ? t.getCause() : t;
            assertThat(cause).as("应为 IllegalStateException").isInstanceOf(IllegalStateException.class);
            assertThat(cause.getMessage()).as("消息含缺表名 t2").contains("t2");
        } finally {
            java.nio.file.Files.deleteIfExists(obda);
            java.nio.file.Files.deleteIfExists(ddl);
            java.nio.file.Files.deleteIfExists(dir);
        }
    }

    @Test
    @DisplayName("煞星集合单一真源 + 天空缺陷回归（SI_SHA/LIU_SHA 口径一致，天空计入六煞、地空不误用）")
    @SuppressWarnings("unchecked")
    void shaStarSetSingleSourceTest() throws Exception {
        Set<String> si = (Set<String>) field("SI_SHA");
        Set<String> liu = (Set<String>) field("LIU_SHA");
        Set<String> yunSha = (Set<String>) field("YUNXIAN_SHA_STARS");
        String[] shaArr = (String[]) field("SHA_STARS");

        assertThat(si).as("四煞=羊陀火铃").containsExactlyInAnyOrder("擎羊", "陀罗", "火星", "铃星");
        assertThat(liu).as("六煞=四煞+天空+地劫").containsExactlyInAnyOrder("擎羊", "陀罗", "火星", "铃星", "天空", "地劫");
        assertThat(liu).as("六煞应含天空").contains("天空");
        assertThat(liu).as("六煞不应误用『地空』（本引擎不落此星）").doesNotContain("地空");
        assertThat(yunSha).as("运限凶星与六煞同源（同一集合）").isSameAs(liu);
        assertThat(shaArr).as("SHA_STARS 与四煞同源").containsExactlyInAnyOrder("擎羊", "陀罗", "火星", "铃星");

        // 回归：大限宫仅见「天空」→ 现应计为见煞（修复前用「地空」永不命中而漏判）
        Method dx = ZiWeiDouShuOntologyJobWorker.class
                .getDeclaredMethod("judgeDaXianShiNian", int.class, Map.class, int.class);
        dx.setAccessible(true);
        Map<Integer, List<String>> onlySky = new HashMap<>();
        onlySky.put(4, new java.util.ArrayList<>(List.of("天空")));
        assertThat((String) dx.invoke(null, 4, onlySky, 0))
                .as("大限宫见『天空』应判为见煞（成败不一）").contains("成败不一");
    }

    @Test
    @DisplayName("B-4 同垣大表下沉保真：DDL 有 tongyuan_rule、data.sql 258 行且 quote 与内置 STAR_TONGYUAN 逐行等值、OBDA 映射齐备")
    void tongyuanSinkArtifactsTest() throws Exception {
        // 1) 内置表仍为 258 行（兜底源，未改动）
        String[][] builtin = (String[][]) field("STAR_TONGYUAN");
        assertThat(builtin.length).as("内置 STAR_TONGYUAN 应仍为 258 行").isEqualTo(258);

        // 2) DDL 已建表
        String ddl = java.nio.file.Files.readString(
                java.nio.file.Path.of("ontology", "database", "createdb_tables.ddl"));
        assertThat(ddl).as("DDL 应含 tongyuan_rule 建表").contains("CREATE TABLE `tongyuan_rule`");

        // 3) data.sql：258 行 INSERT，且第 5 列 quote 与内置逐行**等值同序**（下沉 fidelity）
        List<String> lines = java.nio.file.Files.readAllLines(
                java.nio.file.Path.of("ontology", "database", "data.sql"));
        List<String> tyLines = new java.util.ArrayList<>();
        for (String l : lines) {
            String t = l.trim();
            if (t.startsWith("('TY_")) tyLines.add(t);
        }
        assertThat(tyLines).as("data.sql 应有 258 条 tongyuan_rule INSERT 值行").hasSize(258);
        for (int i = 0; i < tyLines.size(); i++) {
            // 值元组：('TY_xxxx','subject','branch','acc','stem','quote','source')
            String[] parts = tyLines.get(i).split("','");
            assertThat(parts.length).as("第 %d 行应为 7 列值元组", i + 1).isEqualTo(7);
            String dbQuote = parts[5];
            assertThat(dbQuote)
                    .as("第 %d 行 quote 应与内置 STAR_TONGYUAN[%d][4] 等值同序", i + 1, i)
                    .isEqualTo(builtin[i][4]);
            String dbSubject = parts[1];
            assertThat(dbSubject)
                    .as("第 %d 行 subject 应等值", i + 1)
                    .isEqualTo(builtin[i][0]);
        }

        // 4) OBDA 已加映射（mappingId + FROM 源表）
        String obda = java.nio.file.Files.readString(
                java.nio.file.Path.of("ontology", "database", "ziweidoushu.obda"));
        assertThat(obda).as("OBDA 应含 tongyuan_rule 映射").contains("mappingId    tongyuan_rule");
        assertThat(obda).as("OBDA 映射应 FROM tongyuan_rule").containsPattern("FROM\\s+tongyuan_rule");
    }

    @Test
    @DisplayName("B-4 第二批断语/星情大表下沉保真：DDL 建表、data.sql 行数与内容与内置逐行等值、OBDA 映射齐备")
    void verseSinkArtifactsTest() throws Exception {
        // {表名, 内置字段, 期望行数, 是否末列合并（顿号展开）}
        Object[][] spec = {
                {"star_character", "STAR_CHARACTER", 14, false},
                {"star_xingxing", "STAR_XINGXING", 25, false},
                {"star_qa_character", "STAR_QA_CHARACTER", 20, false},
                {"verify_star_traits", "VERIFY_STAR_TRAITS", 14, true},
                {"verify_bureau_traits", "VERIFY_BUREAU_TRAITS", 2, true},
                {"taisui_xiaoxian", "TAISUI_XIAOXIAN", 12, false},
                {"palace_zhi_ge", "PALACE_ZHI_GE", 12, false},
                {"palace_zhi_po", "PALACE_ZHI_PO", 12, false},
                {"palace_fugui", "PALACE_FUGUI", 12, false},
                {"palace_pinjian", "PALACE_PINJIAN", 10, false},
                {"idle_verse", "IDLE_VERSE", 9, false},
                {"idle_star", "IDLE_STAR", 8, true},
                {"brightness_variant", "BRIGHTNESS_VARIANTS", 2, false},
                {"limit_taboo", "LIMIT_TABOO", 12, false},
                {"sihua_qa", "SIHUA_QA", 4, false},
                {"star_block_all", "STAR_BLOCK_ALL", 36, false},
        };
        String ddl = java.nio.file.Files.readString(
                java.nio.file.Path.of("ontology", "database", "createdb_tables.ddl"));
        String obda = java.nio.file.Files.readString(
                java.nio.file.Path.of("ontology", "database", "ziweidoushu.obda"));
        List<String> lines = java.nio.file.Files.readAllLines(
                java.nio.file.Path.of("ontology", "database", "data.sql"));

        for (Object[] sp : spec) {
            String table = (String) sp[0];
            String fieldName = (String) sp[1];
            int expRows = (Integer) sp[2];
            boolean mergeTail = (Boolean) sp[3];

            assertThat(ddl).as("DDL 应含 %s 建表", table).contains("CREATE TABLE `" + table + "`");
            assertThat(obda).as("OBDA 应含 %s 映射", table).contains("mappingId    " + table);
            assertThat(obda).as("OBDA 映射应 FROM %s", table).containsPattern("FROM\\s+`?" + table + "`?");

            // 提取该表 INSERT 值行（单行一元组；行首形如 (0,'...','...')）
            List<String> vals = new java.util.ArrayList<>();
            boolean inBlock = false;
            for (String l : lines) {
                String t = l.trim();
                if (t.startsWith("INSERT INTO `" + table + "`")) {
                    inBlock = true;
                    continue;
                }
                if (inBlock) {
                    if (t.isEmpty()) continue;
                    vals.add(t);
                    if (t.endsWith(";")) break;
                }
            }
            assertThat(vals).as("%s 应有 %d 条值行", table, expRows).hasSize(expRows);

            Object builtinObj = field(fieldName);
            String[][] builtin;
            if (builtinObj instanceof String[][]) {
                builtin = (String[][]) builtinObj;
            } else {
                String[] flat = (String[]) builtinObj;   // LIMIT_TABOO 为 String[]
                builtin = new String[flat.length][1];
                for (int i = 0; i < flat.length; i++) builtin[i][0] = flat[i];
            }
            assertThat(builtin.length).as("%s 内置行数", fieldName).isEqualTo(expRows);

            for (int i = 0; i < expRows; i++) {
                String body = vals.get(i).trim();
                while (body.endsWith(";") || body.endsWith(",") || body.endsWith(")")) {
                    body = body.substring(0, body.length() - 1);
                }
                if (body.startsWith("(")) body = body.substring(1);   // 去外层 ( 及尾部 ),  / );  / )',
                int fc = body.indexOf(',');
                String seq = body.substring(0, fc);
                String rest = body.substring(fc + 1);
                rest = rest.substring(1, rest.length() - 1);           // 去首尾单引号
                String[] cols = rest.split("','");
                assertThat(seq).as("%s 第 %d 行 seq 应连续", table, i).isEqualTo(String.valueOf(i));
                if (mergeTail) {
                    assertThat(cols.length).as("%s 第 %d 行应 2 列（末列合并）", table, i).isEqualTo(2);
                    assertThat(cols[0]).as("%s[%d] 首列应与内置等值", table, i).isEqualTo(builtin[i][0]);
                    String joined = String.join("、",
                            java.util.Arrays.copyOfRange(builtin[i], 1, builtin[i].length));
                    assertThat(cols[1]).as("%s[%d] 合并尾列应与内置等值", table, i).isEqualTo(joined);
                } else {
                    assertThat(cols.length).as("%s 第 %d 行列数应与内置一致", table, i).isEqualTo(builtin[i].length);
                    for (int k = 0; k < cols.length; k++) {
                        assertThat(cols[k]).as("%s[%d][%d] 应与内置等值", table, i, k).isEqualTo(builtin[i][k]);
                    }
                }
            }
        }
    }

    /** B-2 语料入库保真：classic_corpus 的 DDL/OBDA/data.sql 与资源 palace_classic.tsv 逐行等值。 */
    @org.junit.jupiter.api.Test
    void classicCorpusArtifactsTest() throws Exception {
        String ddl = java.nio.file.Files.readString(java.nio.file.Path.of("ontology", "database", "createdb_tables.ddl"));
        String obda = java.nio.file.Files.readString(java.nio.file.Path.of("ontology", "database", "ziweidoushu.obda"));
        java.util.List<String> tsv = java.nio.file.Files.readAllLines(
                java.nio.file.Path.of("src", "main", "resources", "ziweidoushu", "palace_classic.tsv"));
        java.util.List<String> sql = java.nio.file.Files.readAllLines(
                java.nio.file.Path.of("ontology", "database", "data.sql"));

        assertThat(ddl).as("DDL 应含 classic_corpus 建表").contains("CREATE TABLE `classic_corpus`");
        assertThat(obda).as("OBDA 应含 classic_corpus 映射").contains("mappingId    classic_corpus");
        assertThat(obda).as("OBDA 映射应 FROM classic_corpus").containsPattern("FROM\\s+`?classic_corpus`?");

        java.util.List<String> vals = new java.util.ArrayList<>();
        boolean inBlock = false;
        for (String l : sql) {
            String t = l.trim();
            if (t.startsWith("INSERT INTO `classic_corpus`")) { inBlock = true; continue; }
            if (inBlock) {
                if (t.isEmpty()) continue;
                vals.add(t);
                if (t.endsWith(";")) break;
            }
        }
        java.util.List<String[]> rows = new java.util.ArrayList<>();
        for (String l : tsv) {
            if (l.isBlank()) continue;
            rows.add(l.split("\t", 3));
        }
        assertThat(vals).as("classic_corpus 值行数应=TSV 行数").hasSize(rows.size());
        for (int i = 0; i < rows.size(); i++) {
            String[] r = rows.get(i);   // palace, line_no, quote
            String body = vals.get(i).trim();
            while (body.endsWith(";") || body.endsWith(",") || body.endsWith(")")) {
                body = body.substring(0, body.length() - 1);
            }
            if (body.startsWith("(")) body = body.substring(1);
            int c1 = body.indexOf(",'");
            String seq = body.substring(0, c1);
            String rest = body.substring(c1 + 2);
            int pEnd = rest.indexOf("',");
            String palace = rest.substring(0, pEnd);
            String rest2 = rest.substring(pEnd + 2);
            int lnEnd = rest2.indexOf(",'");
            String lineNo = rest2.substring(0, lnEnd);
            String quote = rest2.substring(lnEnd + 2);
            if (quote.endsWith("'")) quote = quote.substring(0, quote.length() - 1);
            assertThat(seq).as("classic_corpus 第 %d 行 seq", i).isEqualTo(String.valueOf(i));
            assertThat(palace).as("classic_corpus[%d] palace", i).isEqualTo(r[0]);
            assertThat(lineNo).as("classic_corpus[%d] line_no", i).isEqualTo(r[1]);
            assertThat(quote).as("classic_corpus[%d] quote", i).isEqualTo(r[2]);
        }
    }

    /** Q 类「格局/断语原文」登记保真：pattern_verse 的 DDL/OBDA 与 data.sql 27 条（verse_key 唯一、display 非空、seq 连续）。 */
    @org.junit.jupiter.api.Test
    void patternVerseArtifactsTest() throws Exception {
        String ddl = java.nio.file.Files.readString(java.nio.file.Path.of("ontology", "database", "createdb_tables.ddl"));
        String obda = java.nio.file.Files.readString(java.nio.file.Path.of("ontology", "database", "ziweidoushu.obda"));
        java.util.List<String> sql = java.nio.file.Files.readAllLines(
                java.nio.file.Path.of("ontology", "database", "data.sql"));

        assertThat(ddl).as("DDL 应含 pattern_verse 建表").contains("CREATE TABLE `pattern_verse`");
        assertThat(obda).as("OBDA 应含 pattern_verse 映射").contains("mappingId    pattern_verse");
        assertThat(obda).as("OBDA 映射应 FROM pattern_verse").containsPattern("FROM\\s+`?pattern_verse`?");

        java.util.List<String> vals = new java.util.ArrayList<>();
        boolean inBlock = false;
        for (String l : sql) {
            String t = l.trim();
            if (t.startsWith("INSERT INTO `pattern_verse`")) { inBlock = true; continue; }
            if (inBlock) {
                if (t.isEmpty()) continue;
                vals.add(t);
                if (t.endsWith(";")) break;
            }
        }
        assertThat(vals).as("pattern_verse 值行数应为 27").hasSize(27);
        java.util.Set<String> keys = new java.util.HashSet<>();
        for (int i = 0; i < vals.size(); i++) {
            String body = vals.get(i).trim();
            while (body.endsWith(";") || body.endsWith(",") || body.endsWith(")")) {
                body = body.substring(0, body.length() - 1);
            }
            if (body.startsWith("(")) body = body.substring(1);
            int c1 = body.indexOf(",'");
            String seq = body.substring(0, c1);
            String rest = body.substring(c1 + 2);
            int kEnd = rest.indexOf("',");
            String key = rest.substring(0, kEnd);
            String rest2 = rest.substring(kEnd + 2);
            int mEnd = rest2.indexOf("',");
            String method = rest2.substring(0, mEnd);
            String rest3 = rest2.substring(mEnd + 2);
            int catEnd = rest3.indexOf("',");
            String category = rest3.substring(0, catEnd);
            String rest4 = rest3.substring(catEnd + 2);
            int dEnd = rest4.indexOf("','");
            String display = rest4.substring(0, dEnd);
            assertThat(seq).as("pattern_verse[%d] seq 连续", i).isEqualTo(String.valueOf(i));
            assertThat(key).as("pattern_verse[%d] verse_key 非空且唯一", i).isNotEmpty();
            assertThat(keys.add(key)).as("pattern_verse[%d] verse_key 应唯一(%s)", i, key).isTrue();
            assertThat(method).as("pattern_verse[%d] method_tag 非空", i).isNotEmpty();
            assertThat(category).as("pattern_verse[%d] category 非空", i).isNotEmpty();
            assertThat(display).as("pattern_verse[%d] display_text 非空", i).isNotEmpty();
        }
    }
}
