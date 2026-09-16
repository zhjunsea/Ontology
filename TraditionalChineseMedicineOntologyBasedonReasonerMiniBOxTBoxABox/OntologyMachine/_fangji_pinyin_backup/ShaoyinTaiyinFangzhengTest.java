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

    @Test @Order(202) @DisplayName("附子粳米汤十八反警告检测")
    @SuppressWarnings("unchecked")
    void shouldWarnOnFuziJingmiTangAntagonism() {
        Map<String, Object> variables = Map.of(
                "symptomIris", List.of(
                        NS + "Futong_instance", NS + "Xiongxiekuman_instance",
                        NS + "Outu_instance"),
                "pulseIris", List.of(NS + "Chenweimai_instance"),
                "tongueIris", List.of(), "fuzhengIris", List.of());
        ProcessInstanceResult result = startProcessAndGetResult(variables);
        printResult("附子粳米汤（十八反：附子反半夏）", result);
        Map<String, Object> vars = result.getVariablesAsMap();
        assertThat(vars.get("fangzheng")).isEqualTo("Fuzijingmitangzheng");
        assertThat(vars.get("finalFormula")).isEqualTo(NS + "Fuzijingmitang");
        List<String> warnings = (List<String>) vars.get("warnings");
        assertThat(warnings).as("应包含十八反警告").isNotNull().isNotEmpty();
        assertThat(warnings).anySatisfy(w -> assertThat(w)
                .contains("十八反").contains("附子").contains("半夏"));
    }

    @Test @Order(203) @DisplayName("白术附子汤证")
    void t_baizhufuzitang() { assertFangzheng("白术附子汤证", "Shibing",
            "Baizhufuzitangzheng", "Baizhufuzitang",
            "Shentitengfan;Dabianjian;Xiaobianzili", "Fuxusemai"); }

    @Test @Order(204) @DisplayName("甘草附子汤证")
    void t_gancaofuzitang() { assertFangzheng("甘草附子汤证", "Shibing",
            "Gancaofuzitangzheng", "Gancaofuzitang",
            "Gujietengfan;Chetongbudequshen;Hanchu;Duanqi;Xiaobianbuli;Efeng", "Fumai"); }

    @Test @Order(205) @DisplayName("薏苡附子散证")
    void t_yiyifuzisan() { assertFangzheng("薏苡附子散证", "Xiongbibing",
            "Yiyifuzisanzheng", "Yiyifuzisan", "Xiongbihuanji", "Chenchimai"); }

    @Test @Order(206) @DisplayName("薏苡附子败酱散证")
    void t_yiyifuzibaijiangsan() { assertFangzheng("薏苡附子败酱散证", "Changyongbing",
            "Yiyifuzibaijiangsanzheng", "Yiyifuzibaijiangsan",
            "Changyong;Shenjiacuo;Fupiji;Anzhiruruzhongzhuang;Wujiju;Shenwure;Maishu", "Shumai"); }

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

    @Test @Order(218) @DisplayName("乌头汤证")
    void t_wutoutang() { assertFangzheng("乌头汤证", "Lijiebing",
            "Wutoutangzheng", "Wutoutang",
            "Guanjietengtong;Bukequshen", "Chenjinmai"); }

    @Test @Order(219) @DisplayName("大乌头煎证")
    void t_dawutoujian() { assertFangzheng("大乌头煎证", "Hanshanbing",
            "Dawutoujianzheng", "Dawutoujian",
            "Hanshanraoqitong;Ruofazebaihanchu;Shouzujueleng", "Chenjinmai"); }

    @Test @Order(220) @DisplayName("乌头桂枝汤证")
    void t_wutouguizhitang() { assertFangzheng("乌头桂枝汤证", "Hanshanbing",
            "Wutouguizhitangzheng", "Wutouguizhitang",
            "Hanshanfutong;Nishen;Shouzuburen;Shentengtong", "Chenjinmai"); }

    @Test @Order(221) @DisplayName("乌头赤石脂丸证")
    void t_wutouchishizhiwan() { assertFangzheng("乌头赤石脂丸证", "Xiongbibing",
            "Wutouchishizhiwanzheng", "Wutouchishizhiwan",
            "Xintongchebei;Beitongchexin", "Chenjinmai"); }

    @Test @Order(222) @DisplayName("赤丸证")
    void t_chiwan() { assertFangzheng("赤丸证", "Hanshanbing",
            "Chiwanzheng", "Chiwan", "Hanqijueni", "Chenximai"); }

    // ============ 苓桂/茯苓 · 少阴系 (245/249/251/259) ============

    @Test @Order(245) @DisplayName("猪苓汤证")
    void t_zhulingtang() { assertFangzheng("猪苓汤证", "Yangmingbing",
            "Zhulingtangzheng", "Zhulingtang",
            "Fare;Kouke;Xiaobianbuli", "Fumai"); }

    @Test @Order(249) @DisplayName("茯苓泽泻汤证")
    void t_fulingzexietang() { assertFangzheng("茯苓泽泻汤证", "Outuoyuexialibing",
            "Fulingzexietangzheng", "Fulingzexietang",
            "Outu;Kouke;Yuyinshui", "Fumai"); }

    @Test @Order(251) @DisplayName("防己茯苓汤证")
    void t_fangjifulingtang() { assertFangzheng("防己茯苓汤证", "Shuiqibing",
            "Fangjifulingtangzheng", "Fangjifulingtang",
            "Pishui;Sizhizhong;Sizhinieniedong", "Fumai"); }

    @Test @Order(259) @DisplayName("葵子茯苓散证")
    void t_kuizifulingsan() { assertFangzheng("葵子茯苓散证", "Renshengbing",
            "Kuizifulingsanzheng", "Kuizifulingsan",
            "Renshenyoushuiqi;Shenzhong;Xiaobianbuli;Sasaehan;Qizetouxuan", "Fumai"); }

    // ============ 从太阳方证迁移过来的桂枝类方证 (260~262) ============

    @Test @Order(260) @DisplayName("桂枝加龙骨牡蛎汤证（太阴）")
    void t_guizhijialonggumulitang_taiyin() { assertFangzheng("桂枝加龙骨牡蛎汤证", "Taiyinbing",
            "Guizhijialonggumulitangzheng", "Guizhijialonggumulitang",
            "Shijingjia;Shaofuxianji;Yintouhan;Muxuan;Faluo", "Jixukouchimai"); }

    @Test @Order(261) @DisplayName("桂枝生姜枳实汤证（太阴）")
    void t_guizhishengjiangzhishitang_taiyin() { assertFangzheng("桂枝生姜枳实汤证", "Taiyinbing",
            "Guizhishengjiangzhishitangzheng", "Guizhishengjiangzhishitang",
            "Xinzhongpi;Qini;Xinxuantong", "Chenxianmai"); }

    @Test @Order(262) @DisplayName("桂枝附子汤证（太阴）")
    void t_guizhifuzitang_taiyin() { assertFangzheng("桂枝附子汤证", "Taiyinbing",
            "Guizhifuzitangzheng", "Guizhifuzitang",
            "Shentengfan;Nanyizhuance", "Fuxusemai"); }
}