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
            "Yanhoubuli;Tunongxue;Xialibuzhi;Shouzujueni", "Chenchimai"); }

    @Test @Order(207) @DisplayName("吴茱萸汤证")
    void t_wuzhuyutang() { assertFangzheng("吴茱萸汤证", "Shaoyinbing",
            "Wuzhuyutangzheng", "Wuzhuyutang",
            "Shiguyuou;Tuli;Shouzunileng;Fanzaoyusi;Ganou;Tuxianmo;Toutong", "Chenxianmai"); }

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
            "Shouzuleng;Ximai;Weimai", "Xiruomai"); }

    @Test @Order(224) @DisplayName("当归四逆加吴茱萸生姜汤证")
    void t_dangguisnijiawuzhuyushengjiangtang() { assertFangzheng(
            "当归四逆加吴茱萸生姜汤证", "Jueyinbing",
            "Dangguisnijiawuzhuyushengjiangtangzheng",
            "Dangguisnijiawuzhuyushengjiangtang",
            "Shouzuleng;Neiyoujiuhan;Ou;Futong", "Xiruomai"); }

    @Test @Order(605) @DisplayName("白头翁汤证")
    void t_baitouwengtang() { assertFangzheng("白头翁汤证", "Jueyinbing",
            "Baitouwengtangzheng", "Baitouwengtang",
            "Xiali;Lijihouchong;Xialinongxue;Kouke", "Xianshumai"); }

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

    // ============ 从太阳方证迁移过来的桂枝类方证 (616) ============

    @Test @Order(616) @DisplayName("桂枝芍药知母汤证（厥阴）")
    void t_guizhishaoyaozhimutang_jueyin() { assertFangzheng("桂枝芍药知母汤证", "Jueyinbing",
            "Guizhishaoyaozhimutangzheng", "Guizhishaoyaozhimutang",
            "Zhuzhijietengtong;Shentiwanglei;Jiaozhongrutuo;Touxuan;Duanqi;Wenwenyutu",
            "Chenxianmai"); }

    @Test @Order(218) @DisplayName("乌头汤证")
    void t_wutoutang() { assertFangzheng("乌头汤证", "Lijiebing",
            "Wutoutangzheng", "Wutoutang",
            "Guanjietengtong;Bukequshen", "Chenjinmai"); }

    @Test @Order(219) @DisplayName("大乌头煎证")
    void t_dawutoujian() { assertFangzheng("大乌头煎证", "Hanshanbing",
            "Dawutoujianzheng", "Dawutoujian",
            "Hanshanraoqitong;Ruofazebaihanchu;Shouzuleng", "Chenjinmai"); }

    @Test @Order(222) @DisplayName("赤丸证")
    void t_chiwan() { assertFangzheng("赤丸证", "Hanshanbing",
            "Chiwanzheng", "Chiwan", "Hanqijueni", "Chenximai"); }

    @Test @Order(544) @DisplayName("当归生姜羊肉汤证")
    void t_dangguishengjiangyangroutang() { assertFangzheng("当归生姜羊肉汤证", "Hanshanbing",
            "Dangguishengjiangyangroutangzheng", "Dangguishengjiangyangroutang",
            "Hanshanfutong;Xietong;Liji", "Xianjimai"); }
}