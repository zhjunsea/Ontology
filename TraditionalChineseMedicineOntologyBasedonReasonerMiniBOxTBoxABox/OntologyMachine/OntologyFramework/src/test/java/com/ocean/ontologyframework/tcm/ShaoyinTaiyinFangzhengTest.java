package com.ocean.ontologyframework.tcm;

import io.camunda.zeebe.client.api.response.ProcessInstanceResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static com.ocean.ontologyframework.tcm.JingfangTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("【少阴·太阴】理中/四逆/真武/附子/麻黄附子/少阴杂方")
class ShaoyinTaiyinFangzhengTest extends AbstractJingfangDiagnosisTest {

    // ============ 麻黄附子类 (33~35) ============

    @Test @Order(33) @DisplayName("麻黄附子细辛汤证诊断")
    void shouldDiagnoseMahuangFuziXixinTangPattern() {
        Map<String, Object> variables = Map.of(
                "symptomIris", List.of(
                        NS + "Fare_instance", NS + "Ehan_instance",
                        NS + "Wuhan_instance", NS + "Danyumei_instance"),
                "pulseIris", List.of(NS + "Chenmai_instance", NS + "Weiximai_instance"),
                "tongueIris", List.of(), "fuzhengIris", List.of());
        ProcessInstanceResult result = startProcessAndGetResult(variables);
        printResult("麻黄附子细辛汤证", result);
        assertBasicResult(result, "Shaoyinbing",
                "Mahuangfuzixixintangzheng", NS + "Mahuangfuzixixintang");
        assertBagang(result, List.of("表证"), List.of("虚证"), List.of("阴证"));
    }

    @Test @Order(34) @DisplayName("麻黄附子甘草汤证")
    void t_mahuangfuzigancaotang() { assertFangzheng("麻黄附子甘草汤证", "Shaoyinbing",
            "Mahuangfuzigancaotangzheng", "Mahuangfuzigancaotang",
            "Ehan;Danyumei", "Chenmai"); }

    @Test @Order(35) @DisplayName("麻黄附子汤证")
    void t_mahuangfuzitang() { assertFangzheng("麻黄附子汤证", "Shaoyinbing",
            "Mahuangfuzitangzheng", "Mahuangfuzitang",
            "Shuizhong;Xiaobianbuli;Wuhan", "Chenxiaomai"); }

    // ============ 理中 / 四逆 / 真武 / 附子 (191~206) ============

    @Test @Order(191) @DisplayName("理中汤证诊断")
    void shouldDiagnoseLizhongTangPattern() {
        Map<String, Object> variables = Map.of(
                "symptomIris", List.of(
                        NS + "Fuman_instance", NS + "Outu_instance",
                        NS + "Shibuxia_instance", NS + "Xiali_instance",
                        NS + "Shifuzitong_instance", NS + "Buke_instance"),
                "pulseIris", List.of(NS + "Chenruomai_instance"),
                "tongueIris", List.of(), "fuzhengIris", List.of());
        ProcessInstanceResult result = startProcessAndGetResult(variables);
        printResult("理中汤证", result);
        assertBasicResult(result, "Taiyinbing", "Lizhongtangzheng", NS + "Lizhongtang");
        assertBagang(result, List.of("里证"), List.of("虚证"), List.of("阴证"));
    }

    @Test @Order(192) @DisplayName("四逆汤证诊断（太阴少阴合病）")
    @SuppressWarnings("unchecked")
    void shouldDiagnoseSiniTangPattern() {
        Map<String, Object> variables = Map.of(
                "symptomIris", List.of(
                        NS + "Fuman_instance", NS + "Outu_instance",
                        NS + "Shibuxia_instance", NS + "Xiali_instance",
                        NS + "Xialiqinggu_instance", NS + "Shouzuleng_instance",
                        NS + "Danyumei_instance"),
                "pulseIris", List.of(NS + "Chenweimai_instance"),
                "tongueIris", List.of(), "fuzhengIris", List.of());
        ProcessInstanceResult result = startProcessAndGetResult(variables);
        printResult("四逆汤证", result);
        Map<String, Object> vars = result.getVariablesAsMap();
        assertThat((List<String>) vars.get("liujingTypes"))
                .containsExactlyInAnyOrder("Taiyinbing", "Shaoyinbing");
        assertThat(vars.get("isCombinedChannel")).isEqualTo(true);
        assertThat(vars.get("sixChannel")).isEqualTo("太阴少阴合病");
        assertThat(vars.get("combinedDiseaseMark")).isEqualTo("太阴少阴合病");
        assertThat(vars.get("fangzheng")).isEqualTo("Sinitangzheng");
        assertThat(vars.get("finalFormula")).isEqualTo(NS + "Sinitang");
        assertBagang(result, List.of("表证", "里证"), List.of("虚证"), List.of("阴证"));
    }

    @Test @Order(193) @DisplayName("通脉四逆汤证")
    void t_tongmaisinitang() { assertFangzheng("通脉四逆汤证", "Shaoyinbing",
            "Tongmaisinitangzheng", "Tongmaisinitang",
            "Xialiqinggu;Shouzujueni;Mianchi;Shenbuehan", "Weiyujuemai"); }

    @Test @Order(194) @DisplayName("通脉四逆加猪胆汁汤证")
    void t_tongmaisinijiazhudanzhitang() { assertFangzheng("通脉四逆加猪胆汁汤证", "Shaoyinbing",
            "Tongmaisinijiazhudanzhitangzheng", "Tongmaisinijiazhudanzhitang",
            "Hanchu;Sizhijuji;Jueni", "Weimai"); }

    @Test @Order(195) @DisplayName("四逆加人参汤证")
    void t_sinijiarenshentang() { assertFangzheng("四逆加人参汤证", "Shaoyinbing",
            "Sinijiarenshentangzheng", "Sinijiarenshentang",
            "Ehan;Xiali;Danyumei", "Weimai"); }

    @Test @Order(196) @DisplayName("茯苓四逆汤证")
    void t_fulingsinitang() { assertFangzheng("茯苓四逆汤证", "Taiyangbing",
            "Fulingsinitangzheng", "Fulingsinitang", "Fanzao;Sini", "Weimai"); }

    @Test @Order(197) @DisplayName("白通汤证")
    void t_baitongtang() { assertFangzheng("白通汤证", "Shaoyinbing",
            "Baitongtangzheng", "Baitongtang",
            "Xialiqinggu;Mianchi;Ehan", "Weimai"); }

    @Test @Order(198) @DisplayName("白通加猪胆汁汤证")
    void t_baitongjiazhudanzhitang() { assertFangzheng("白通加猪胆汁汤证", "Shaoyinbing",
            "Baitongjiazhudanzhitangzheng", "Baitongjiazhudanzhitang",
            "Xiali;Jueni;Ganou;Fan", "Weimai"); }

    @Test @Order(199) @DisplayName("干姜附子汤证")
    void t_ganjiangfuzitang() { assertFangzheng("干姜附子汤证", "Taiyangbing",
            "Ganjiangfuzitangzheng", "Ganjiangfuzitang",
            "Fanzao;Budemian", "Chenweimai"); }

    @Test @Order(200) @DisplayName("真武汤证诊断")
    void shouldDiagnoseZhenwuTangPattern() {
        Map<String, Object> variables = Map.of(
                "symptomIris", List.of(
                        NS + "Ehan_instance", NS + "Danyumei_instance",
                        NS + "Xinxiajidong_instance", NS + "Touxuan_instance",
                        NS + "Shenshundong_instance", NS + "Futong_instance",
                        NS + "Xiaobianbuli_instance", NS + "Sizhichenzhongtengtong_instance",
                        NS + "Xiali_instance", NS + "Shouzuleng_instance"),
                "pulseIris", List.of(NS + "Weiximai_instance"),
                "tongueIris", List.of(), "fuzhengIris", List.of());
        ProcessInstanceResult result = startProcessAndGetResult(variables);
        printResult("真武汤证", result);
        assertBasicResult(result, "Shaoyinbing", "Zhenwutangzheng", NS + "Zhenwutang");
        assertBagang(result, List.of("表证"), List.of("虚证"), List.of("阴证"));
    }

    @Test @Order(201) @DisplayName("附子汤证")
    void t_fuzitang() { assertFangzheng("附子汤证", "Shaoyinbing",
            "Fuzitangzheng", "Fuzitang",
            "Beiehan;Shentong;Shouzuhan;Gujietong", "Chenweimai"); }

    // ============ 少阴杂方 + 附子/乌头 (209~222) ============

    @Test @Order(209) @DisplayName("桃花汤证")
    void t_taohuatang() { assertFangzheng("桃花汤证", "Shaoyinbing",
            "Taohuatangzheng", "Taohuatang",
            "Xialibiannongxue;Futong;Xiaobianbuli", "Chenweimai"); }

    @Test @Order(210) @DisplayName("黄连阿胶汤证")
    void t_huanglianejiaotang() { assertFangzheng("黄连阿胶汤证", "Shaoyinbing",
            "Huanglianejiaotangzheng", "Huanglianejiaotang",
            "Xinzhongfan;Budewo", "Xishumai"); }

    @Test @Order(211) @DisplayName("猪肤汤证")
    void t_zhufutang() { assertFangzheng("猪肤汤证", "Shaoyinbing",
            "Zhufutangzheng", "Zhufutang",
            "Xiali;Yantong;Xiongman;Xinfan", "Xishumai"); }

    @Test @Order(212) @DisplayName("甘草汤证")
    void t_gancaotang() { assertFangzheng("甘草汤证", "Shaoyinbing",
            "Gancaotangzheng", "Gancaotang", "Yantong", "Fumai"); }

    @Test @Order(213) @DisplayName("桔梗汤证")
    void t_jiegengtang() { assertFangzheng("桔梗汤证", "Shaoyinbing",
            "Jiegengtangzheng", "Jiegengtang",
            "Yantong;Kesou;Tunong", "Fumai"); }

    @Test @Order(214) @DisplayName("半夏散及汤证")
    void t_banxiasanjitang() { assertFangzheng("半夏散及汤证", "Shaoyinbing",
            "Banxiasanjitangzheng", "Banxiasanjitang", "Yantong", "Fumai"); }

    @Test @Order(215) @DisplayName("苦酒汤证")
    void t_kujiutang() { assertFangzheng("苦酒汤证", "Shaoyinbing",
            "Kujiutangzheng", "Kujiutang",
            "Yanzhongshengchuang;Bunengyanyu", "Fumai"); }

    @Test @Order(216) @DisplayName("附子汤证（少阴）")
    void t_fuzitang_shaoyin() { assertFangzheng("附子汤证", "Shaoyinbing",
            "Fuzitangzheng", "Fuzitang",
            "Beiehan;Shentong;Shouzuhan;Gujietong", "Chenweimai"); }

    @Test @Order(217) @DisplayName("干姜附子汤证（阳明中暍）")
    void t_ganjiangfuzitang_zhongye() { assertFangzheng("干姜附子汤证", "Taiyangbing",
            "Ganjiangfuzitangzheng", "Ganjiangfuzitang",
            "Fanzao;Budemian", "Chenweimai"); }

    @Test @Order(221) @DisplayName("乌头赤石脂丸证")
    void t_wutouchishizhiwan() { assertFangzheng("乌头赤石脂丸证", "Xiongbibing",
            "Wutouchishizhiwanzheng", "Wutouchishizhiwan",
            "Xintongchebei;Beitongchexin", "Chenjinmai"); }

    @Test @Order(249) @DisplayName("茯苓泽泻汤证")
    void t_fulingzexietang() { assertFangzheng("茯苓泽泻汤证", "Outuoyuexialibing",
            "Fulingzexietangzheng", "Fulingzexietang",
            "Outu;Kouke;Yuyinshui", "Fumai"); }

    // ============ 从太阳方证迁移过来的桂枝类方证 (260~262) ============

    @Test @Order(260) @DisplayName("桂枝加龙骨牡蛎汤证（太阴）")
    void t_guizhijialonggumulitang_taiyin() { assertFangzheng("桂枝加龙骨牡蛎汤证", "Taiyinbing",
            "Guizhijialonggumulitangzheng", "Guizhijialonggumulitang",
            "Shijingjia;Shaofuxianji;Yintouhan;Muxuan;Faluo", "Jixukouchimai"); }

    @Test @Order(261) @DisplayName("桂枝生姜枳实汤证（太阴）")
    void t_guizhishengjiangzhishitang_taiyin() { assertFangzheng("桂枝生姜枳实汤证", "Taiyinbing",
            "Guizhishengjiangzhishitangzheng", "Guizhishengjiangzhishitang",
            "Xinzhongpi;Qini;Xinxuantong", "Chenxianmai"); }

    // ============ 从太阳方证迁移过来的方证 (263) ============

    @Test @Order(263) @DisplayName("越婢加术汤证")
    void t_yuebijiazhutang() { assertFangzheng("越婢加术汤证", "Taiyinbing",
            "Yuebijiazhutangzheng", "Yuebijiazhutang",
            "Yishenmianmuhuangzhong;Xiaobianbuli", "Chenmai"); }

    @Test @Order(583) @DisplayName("赤石脂禹余粮汤证")
    void t_chishizhiyuyuliangtang() { assertFangzheng("赤石脂禹余粮汤证", "Taiyangbing",
            "Chishizhiyuyuliangtangzheng", "Chishizhiyuyuliangtang",
            "Xinxiapiying;Xialibuzhi;Fanzhibuyu", "Chenximai"); }

    // ============ 其他伤寒方 (592~604) ============

    @Test @Order(592) @DisplayName("茵陈五苓散证")
    void t_yinchenwulingsan() { assertFangzheng("茵陈五苓散证", "Huangdanbing",
            "Yinchenwulingsanzheng", "Yinchenwulingsan",
            "Huangdan;Xiaobianbuli", "Fumai"); }

    @Test @Order(902) @DisplayName("太少两感检测")
    @SuppressWarnings("unchecked")
    void shouldDetectTaiShaoLiangGan() {
        Map<String, Object> variables = Map.of(
                "symptomIris", List.of(
                        NS + "Fare_instance", NS + "Ehan_instance",
                        NS + "Wuhan_instance", NS + "Danyumei_instance"),
                "pulseIris", List.of(
                        NS + "Fumai_instance", NS + "Weiximai_instance",
                        NS + "Chenmai_instance"),
                "tongueIris", List.of(), "fuzhengIris", List.of());
        ProcessInstanceResult result = startProcessAndGetResult(variables);
        printResult("太少两感（麻黄附子细辛汤证）", result);

        Map<String, Object> vars = result.getVariablesAsMap();
        assertThat(vars.get("isCombinedChannel")).isEqualTo(true);
        assertThat((List<String>) vars.get("liujingTypes"))
                .containsExactlyInAnyOrder("Taiyangbing", "Shaoyinbing");
        assertThat(vars.get("sixChannel")).isEqualTo("太少两感");
        assertThat(vars.get("combinedDiseaseMark")).isEqualTo("太少两感");
        assertThat(vars.get("fangzheng")).isEqualTo("Mahuangfuzixixintangzheng");
        assertThat(vars.get("finalFormula")).isEqualTo(NS + "Mahuangfuzixixintang");
    }

    @Test @Order(8) @DisplayName("桂枝加芍药汤证")
    void t_guizhijiashaoyaotang() { assertFangzheng("桂枝加芍药汤证", "Taiyinbing",
            "Guizhijiashaoyaotangzheng", "Guizhijiashaoyaotang",
            "Fuman;Shitong", "Ruanmai"); }

    @Test @Order(9) @DisplayName("桂枝加大黄汤证")
    void t_guizhijiadahuangtang() { assertFangzheng("桂枝加大黄汤证", "Taiyinbing",
            "Guizhijiadahuangtangzheng", "Guizhijiadahuangtang",
            "Futong;Juan", "Ruanmai"); }

    @Test @Order(43) @DisplayName("射干麻黄汤证")
    void t_sheganmahuangtang() { assertFangzheng("射干麻黄汤证", "Taiyinbing",
            "Sheganmahuangtangzheng", "Sheganmahuangtang",
            "Keershangqi;Houzhongshuijisheng", "Fumai"); }

    @Test @Order(47) @DisplayName("越婢加半夏汤证")
    void t_yuebijiabanxiatang() { assertFangzheng("越婢加半夏汤证", "Taiyinbing",
            "Yuebijiabanxiatangzheng", "Yuebijiabanxiatang",
            "Kesou;Chuan;Fudamai;Murutuo", "Fumai"); }

    // ============ 苓桂类 · 太阳 (241/242/244/248) ============

    @Test @Order(241) @DisplayName("茯苓桂枝甘草大枣汤证")
    void t_fulingguizhigancaodazaotang() { assertFangzheng("茯苓桂枝甘草大枣汤证", "Taiyangbing",
            "Fulingguizhigancaodazaotangzheng", "Fulingguizhigancaodazaotang",
            "Qixiaji;Yuzuobentun", "Chenmai"); }

    @Test @Order(244) @DisplayName("五苓散证")
    void t_wulingsan() { assertFangzheng("五苓散证", "TaiyangYangmingHebing",
            "Wulingsanzheng", "Wulingsan",
            "Kouke;Xiaobianbuli;Shuiruzetu", "Fumai"); }

    @Test @Order(248) @DisplayName("茯苓甘草汤证")
    void t_fulinggancaotang() { assertFangzheng("茯苓甘草汤证", "Taiyangbing",
            "Fulinggancaotangzheng", "Fulinggancaotang",
            "Buke;Xinxiajidong;Shouzuleng", "Fumai"); }@Test @Order(243) @DisplayName("苓桂术甘汤证")
    void t_lingguizhugantang() { assertFangzheng("苓桂术甘汤证", "Tanyinbing",
            "Lingguizhugantangzheng", "Lingguizhugantang",
            "Xinxianiman;Qishangchongxiong;Qizetouxuan", "Chenjinmai"); }

    @Test @Order(254) @DisplayName("苓甘五味姜辛汤证")
    void t_lingganwuweijiangxintang() { assertFangzheng("苓甘五味姜辛汤证", "Tanyinbing",
            "Lingganwuweijiangxintangzheng", "Lingganwuweijiangxintang",
            "Keman", "Chenmai"); }

    @Test @Order(255) @DisplayName("苓甘五味姜辛夏汤证")
    void t_lingganwuweijiangxinxiatang() { assertFangzheng("苓甘五味姜辛夏汤证", "Tanyinbing",
            "Lingganwuweijiangxinxiatangzheng", "Lingganwuweijiangxinxiatang",
            "Keman;Ou;Mao", "Chenmai"); }

    @Test @Order(256) @DisplayName("苓甘五味加姜辛半夏杏仁汤证")
    void t_lingganwuweijiajiangxinbanxiaxingrentang() { assertFangzheng(
            "苓甘五味加姜辛半夏杏仁汤证", "Tanyinbing",
            "Lingganwuweijiajiangxinbanxiaxingrentangzheng",
            "Lingganwuweijiajiangxinbanxiaxingrentang",
            "Xingzhong", "Chenmai"); }

    @Test @Order(257) @DisplayName("苓甘五味加姜辛半杏大黄汤证")
    void t_lingganwuweijiajiangxinbanxingdahuangtang() { assertFangzheng(
            "苓甘五味加姜辛半杏大黄汤证", "Tanyinbing",
            "Lingganwuweijiajiangxinbanxingdahuangtangzheng",
            "Lingganwuweijiajiangxinbanxingdahuangtang",
            "Mianreruzui", "Chenmai"); }

    @Test @Order(258) @DisplayName("桂苓五味甘草汤证")
    void t_guilingwuweigancaotang() { assertFangzheng("桂苓五味甘草汤证", "Tanyinbing",
            "Guilingwuweigancaotangzheng", "Guilingwuweigancaotang",
            "Duotuokouzao;Shouzujueni;Qicongxiaofushangchongxiongyan;" +
                    "Xiaobiannan;Shifumao", "Chenmai"); }

    // ============ 黄芪类 (401~404) ============

    @Test @Order(401) @DisplayName("黄芪桂枝五物汤证")
    void t_huangqiguizhiwuwutang() { assertFangzheng("黄芪桂枝五物汤证", "Xuebibing",
            "Huangqiguizhiwuwutangzheng", "Huangqiguizhiwuwutang",
            "Shentiburen;Rufengbizhuang", "Weisemai"); }

    @Test @Order(402) @DisplayName("黄芪建中汤证")
    void t_huangqijianzhongtang() { assertFangzheng("黄芪建中汤证", "Xulaobing",
            "Huangqijianzhongtangzheng", "Huangqijianzhongtang",
            "Xulaoliji;Fuzhongjiaotong;Miansewuhua", ""); }

    @Test @Order(442) @DisplayName("栝楼瞿麦丸十八反警告检测")
    @SuppressWarnings("unchecked")
    void shouldWarnOnGualouQumaiWanAntagonism() {
        Map<String, Object> variables = Map.of(
                "symptomIris", List.of(
                        NS + "Xiaobianbuli_instance", NS + "Kouke_instance"),
                "pulseIris", List.of(NS + "Chenmai_instance"),
                "tongueIris", List.of(), "fuzhengIris", List.of());
        ProcessInstanceResult result = startProcessAndGetResult(variables);
        printResult("栝楼瞿麦丸（十八反：瓜蒌根反附子）", result);
        Map<String, Object> vars = result.getVariablesAsMap();
        assertThat(vars.get("fangzheng")).isEqualTo("Gualouqumaiwanzheng");
        assertThat(vars.get("finalFormula")).isEqualTo(NS + "Gualouqumaiwan");
        List<String> warnings = (List<String>) vars.get("warnings");
        assertThat(warnings).as("应包含十八反警告").isNotNull().isNotEmpty();
        assertThat(warnings).anySatisfy(w -> assertThat(w)
                .contains("十八反").contains("瓜蒌").contains("附子"));
    }

    @Test @Order(545) @DisplayName("内补当归建中汤证")
    void t_neibudangguijianzhongtang() { assertFangzheng("内补当归建中汤证", "Furenchanhoubing",
            "Neibudangguijianzhongtangzheng", "Neibudangguijianzhongtang",
            "Fuzhongcitong;Xixishaoqi;Shaofujimotong;Yinyaobeitong;Bunengyinshi", ""); }

    @Test @Order(705) @DisplayName("小建中汤证")
    void t_xiaojianzhongtang() { assertFangzheng("小建中汤证", "Taiyinbing",
            "Xiaojianzhongtangzheng", "Xiaojianzhongtang",
            "Fuzhongjiaotong;Xinji;Fan", "Xiansemai"); }

    @Test @Order(706) @DisplayName("大建中汤证")
    void t_dajianzhongtang() { assertFangzheng("大建中汤证", "Taiyinbing",
            "Dajianzhongtangzheng", "Dajianzhongtang",
            "Xinxiongdahantong;Oubunengyinshi;Fuzhonghan", "Chenxianmai"); }

    @Test @Order(708) @DisplayName("甘草干姜汤证")
    void t_gancaoganjiangtang() { assertFangzheng("甘草干姜汤证", "Feiweibing",
            "Gancaoganjiangtangzheng", "Gancaoganjiangtang",
            "Feiweituxianmo;Yiniao;Xiaobianshu;Buke", "Xumai"); }

    @Test @Order(709) @DisplayName("甘草干姜茯苓白术汤证")
    void t_gancaoganjiangfulingbaizhutang() { assertFangzheng(
            "甘草干姜茯苓白术汤证", "Shenzhuobing",
            "Gancaoganjiangfulingbaizhutangzheng",
            "Gancaoganjiangfulingbaizhutang",
            "Shenzhuo;Yaozhongleng;Ruzuoshuizhong;Fuzhongrudaiwuqianqian", "Chenmai"); }

    @Test @Order(738) @DisplayName("大建中汤证（复测）")
    void t_dajianzhongtang_retest() { assertFangzheng("大建中汤证", "Taiyinbing",
            "Dajianzhongtangzheng", "Dajianzhongtang",
            "Xinxiongdahantong;Oubunengyinshi;Fuzhonghan", "Chenxianmai"); }

    @Test @Order(746) @DisplayName("术附汤证")
    void t_shufutang() { assertFangzheng("术附汤证", "Zhongfengbing",
            "Zhufutangzheng", "Zhufutang", "Touzhongxuan;Kujizhiyuandi", ""); }
}