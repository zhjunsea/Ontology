package com.ocean.ontologyframework.tcm;

import io.camunda.zeebe.client.api.response.ProcessInstanceResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static com.ocean.ontologyframework.tcm.JingfangTestSupport.*;

@DisplayName("【厥阴】乌梅丸/当归四逆/吴茱萸/白头翁/麻黄升麻/黄连汤")
class JueyinFangzhengTest extends AbstractJingfangDiagnosisTest {

    @Test @Order(36) @DisplayName("麻黄升麻汤证")
    void t_mahuangshengmatang() { assertFangzheng("麻黄升麻汤证", "Jueyinbing",
            "Mahuangshengmatangzheng", "Mahuangshengmatang",
            "Yanhoubuli;Tunongxue;Xielibuzhi;Shouzujueni", "Chenchimai"); }

    @Test @Order(168) @DisplayName("黄连汤证")
    void t_huangliantang() { assertFangzheng("黄连汤证", "Jueyinbing",
            "Huangliantangzheng", "Huangliantang",
            "Xiongzhongyoure;Weizhongyouxieqi;Futong;Yuou", "Xianmai"); }

    @Test @Order(169) @DisplayName("干姜黄芩黄连人参汤证")
    void t_ganjianghuangqinhuanglianrenshentang() { assertFangzheng(
            "干姜黄芩黄连人参汤证", "Jueyinbing",
            "Ganjianghuangqinhuanglianrenshentangzheng",
            "Ganjianghuangqinhuanglianrenshentang",
            "Shirukoujitu;Xiali", "Chenweimai"); }

    @Test @Order(207) @DisplayName("吴茱萸汤证")
    void t_wuzhuyutang() { assertFangzheng("吴茱萸汤证", "Shaoyinbing",
            "Wuzhuyutangzheng", "Wuzhuyutang",
            "Shiguyuou;Tuli;Shouzunileng;Fanzaoyusi;Ganoutuxianmo;Toutong", "Chenxianmai"); }

    @Test @Order(208) @DisplayName("乌梅丸证诊断")
    void shouldDiagnoseWumeiWanPattern() {
        Map<String, Object> variables = Map.of(
                "symptomIris", List.of(
                        NS + "Xiaoke_instance", NS + "Qicongshaofushangchongxin_instance",
                        NS + "Xinzhongtengre_instance", NS + "Ji_instance",
                        NS + "Buyushi_instance", NS + "Shizetuhui_instance",
                        NS + "Shouzuleng_instance", NS + "Kouku_instance"),
                "pulseIris", List.of(NS + "Weiximai_instance"),
                "tongueIris", List.of(), "fuzhengIris", List.of());
        ProcessInstanceResult result = startProcessAndGetResult(variables);
        printResult("乌梅丸证", result);
        assertBasicResult(result, "Jueyinbing", "Wumeiwanzheng", NS + "Wumeiwan");
        assertBagang(result, List.of("半表半里"), List.of("虚证"), List.of("阴证"));
    }

    @Test @Order(223) @DisplayName("当归四逆汤证")
    void t_dangguisinitang() { assertFangzheng("当归四逆汤证", "Jueyinbing",
            "Dangguisinitangzheng", "Dangguisinitang",
            "Shouzujuehan;Maixiyujue", "Xiruomai"); }

    @Test @Order(224) @DisplayName("当归四逆加吴茱萸生姜汤证")
    void t_dangguisnijiawuzhuyushengjiangtang() { assertFangzheng(
            "当归四逆加吴茱萸生姜汤证", "Jueyinbing",
            "Dangguisnijiawuzhuyushengjiangtangzheng",
            "Dangguisnijiawuzhuyushengjiangtang",
            "Shouzujuehan;Neiyoujiuhan;Ou;Futong", "Xiruomai"); }

    @Test @Order(605) @DisplayName("白头翁汤证")
    void t_baitouwengtang() { assertFangzheng("白头翁汤证", "Jueyinbing",
            "Baitouwengtangzheng", "Baitouwengtang",
            "Relixiazhong;Xialinongxue;Kouke", "Xianshumai"); }

    @Test @Order(606) @DisplayName("白头翁加甘草阿胶汤证")
    void t_baitouwengjiagancaojiaotang() { assertFangzheng(
            "白头翁加甘草阿胶汤证", "Jueyinbing",
            "Baitouwengjiagancaojiaotangzheng", "Baitouwengjiagancaojiaotang",
            "Xiali;Xufan", "Weimai"); }

    @Test @Order(615) @DisplayName("乌梅丸证（复测）")
    void t_wumeiwan_retest() { assertFangzheng("乌梅丸证", "Jueyinbing",
            "Wumeiwanzheng", "Wumeiwan",
            "Xiaoke;Qicongshaofushangchongxin;Xinzhongtengre;Ji;Buyushi;" +
                    "Shizetuhui;Shouzuleng;Kouku", "Weiximai"); }
}