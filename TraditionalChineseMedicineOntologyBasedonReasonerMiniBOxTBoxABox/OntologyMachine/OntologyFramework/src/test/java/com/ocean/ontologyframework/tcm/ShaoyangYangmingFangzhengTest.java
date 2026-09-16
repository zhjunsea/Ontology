package com.ocean.ontologyframework.tcm;

import io.camunda.zeebe.client.api.response.ProcessInstanceResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.ocean.ontologyframework.tcm.JingfangTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("【少阳·阳明】柴胡/白虎/承气/泻心/栀子豉类方证")
class ShaoyangYangmingFangzhengTest extends AbstractJingfangDiagnosisTest {

    // ============ 柴胡汤类 (71~79) ============

    @Test @Order(71) @DisplayName("小柴胡汤证诊断")
    void shouldDiagnoseXiaoChaihuTangPattern() {
        Map<String, Object> variables = Map.of(
                "symptomIris", List.of(
                        NS + "Wanglaihanre_instance", NS + "Xiongxiekuman_instance",
                        NS + "Momo_instance", NS + "Buyushi_instance",
                        NS + "Xinfan_instance", NS + "Xiou_instance",
                        NS + "Kouku_instance"),
                "pulseIris", List.of(NS + "Xianmai_instance"),
                "tongueIris", List.of(), "fuzhengIris", List.of());
        ProcessInstanceResult result = startProcessAndGetResult(variables);
        printResult("小柴胡汤证", result);
        assertBasicResult(result, "Shaoyangbing", "Xiaochaihutangzheng", NS + "Xiaochaihutang");
        assertBagang(result, List.of("半表半里"), null, List.of("阳证"));
    }

    @Test @Order(72) @DisplayName("大柴胡汤证诊断（少阳阳明合病）")
    @SuppressWarnings("unchecked")
    void shouldDiagnoseDaChaihuTangPattern() {
        Map<String, Object> variables = Map.of(
                "symptomIris", List.of(
                        NS + "Wanglaihanre_instance", NS + "Xiongxiekuman_instance",
                        NS + "Xinxiaji_instance", NS + "Outubuzhi_instance",
                        NS + "Yuyuweifan_instance", NS + "Dabianying_instance",
                        NS + "Kouku_instance", NS + "Chaore_instance",
                        NS + "Zhanyu_instance", NS + "Fuman_instance",
                        NS + "Futong_instance"),
                "pulseIris", List.of(NS + "Xianmai_instance"),
                "tongueIris", List.of(),
                "fuzhengIris", List.of(NS + "Xinxiaanzhimantong_instance"));
        ProcessInstanceResult result = startProcessAndGetResult(variables);
        printResult("大柴胡汤证", result);
        Map<String, Object> vars = result.getVariablesAsMap();
        assertThat((List<String>) vars.get("liujingTypes"))
                .containsExactlyInAnyOrder("Shaoyangbing", "Yangmingbing");
        assertThat(vars.get("isCombinedChannel")).isEqualTo(true);
        assertThat(vars.get("sixChannel")).isEqualTo("少阳阳明合病");
        assertThat(vars.get("combinedDiseaseMark")).isEqualTo("少阳阳明合病");
        assertThat(vars.get("sixChannelCn")).isEqualTo("少阳阳明合病");
        assertThat(vars.get("fangzheng")).isEqualTo("Dachaihutangzheng");
        assertThat(vars.get("finalFormula")).isEqualTo(NS + "Dachaihutang");
        assertBagang(result, List.of("里证", "半表半里"), List.of("实证"), List.of("阳证"));
    }

    @Test @Order(73) @DisplayName("柴胡加芒硝汤证")
    void t_chaihujiamangxiaotang() { assertFangzheng("柴胡加芒硝汤证", "ShaoyangYangmingHebing",
            "Chaihujiamangxiaotangzheng", "Chaihujiamangxiaotang",
            "Wanglaihanre;Xiongxiekuman;Kouku;Ou;Chaore;Xiali", "Xianmai"); }

    @Test @Order(74) @DisplayName("柴胡加龙骨牡蛎汤证")
    void t_chaihujialonggumulitang() { assertFangzheng("柴胡加龙骨牡蛎汤证", "ShaoyangYangmingHebing",
            "Chaihujialonggumulitangzheng", "Chaihujialonggumulitang",
            "Wanglaihanre;Xiongxiekuman;Kouku;Xiongman;Fanzao;Xiaobianbuli;Zhanyu;Chaore;Dabianying", "Xianmai"); }

    @Test @Order(75) @DisplayName("柴胡桂枝汤证")
    void t_chaihuguizhitang() { assertFangzheng("柴胡桂枝汤证", "TaiyangShaoyangHebing",
            "Chaihuguizhitangzheng", "Chaihuguizhitang",
            "Fare;Ehan;Wanglaihanre;Xiongxiekuman;Kouku;Gujietengfan;Xinxiazhijie;Weiou", "Fumai;Xianmai"); }

    @Test @Order(76) @DisplayName("柴胡桂枝干姜汤证诊断（少阳太阴合病）")
    @SuppressWarnings("unchecked")
    void shouldDiagnoseChaihuGuizhiGanjiangTangPattern_Taiyin() {
        Map<String, Object> variables = Map.of(
                "symptomIris", List.of(
                        NS + "Wanglaihanre_instance", NS + "Xiongxiekuman_instance",
                        NS + "Xiaobianbuli_instance", NS + "Kouke_instance",
                        NS + "Buou_instance", NS + "Dantouhanchu_instance",
                        NS + "Xinfan_instance", NS + "Fuman_instance",
                        NS + "Buke_instance", NS + "Kouku_instance"),
                "pulseIris", List.of(NS + "Xianmai_instance", NS + "Ruomai_instance"),
                "tongueIris", List.of(), "fuzhengIris", List.of());
        ProcessInstanceResult result = startProcessAndGetResult(variables);
        printResult("柴胡桂枝干姜汤证（少阳太阴合病）", result);
        Map<String, Object> vars = result.getVariablesAsMap();
        assertThat((List<String>) vars.get("liujingTypes"))
                .containsExactlyInAnyOrder("Shaoyangbing", "Taiyinbing");
        assertThat(vars.get("isCombinedChannel")).isEqualTo(true);
        assertThat(vars.get("sixChannel")).isEqualTo("少阳太阴合病");
        assertThat(vars.get("combinedDiseaseMark")).isEqualTo("少阳太阴合病");
        assertThat(vars.get("fangzheng")).isEqualTo("Chaihuguizhiganjiangtangzheng");
        assertThat(vars.get("finalFormula")).isEqualTo(NS + "Chaihuguizhiganjiangtang");
    }

    @Test @Order(77) @DisplayName("柴胡去半夏加栝蒌汤证")
    void t_chaihuqubanxiajiagualoutang() { assertFangzheng("柴胡去半夏加栝蒌汤证", "Shaoyangbing",
            "Chaihuqubanxiajiagualoutangzheng", "Chaihuqubanxiajiagualoutang",
            "Kouke;Wanglaihanre;Runvezhuang", ""); }

    @Test @Order(78) @DisplayName("柴胡白虎汤证（三阳合病）")
    void t_chaihubaihutang() { assertFangzheng("柴胡白虎汤证（三阳合病）", "Sanyanghebing",
            "Chaihubaihutangzheng", "Chaihubaihutang",
            "Fare;Ehan;Wuhan;Danrebuhan;Kouke;Dahan;Wanglaihanre;Xiongxiekuman;" +
                    "Kouku;Fuman;Shenzhong;Nanyizhuance;Kouburen;Miangou;Zhanyu;" +
                    "Yiniao;Danyumei;Muhezehan",
            "Fumai;Hongdamai;Xianmai"); }

    @Test @Order(79) @DisplayName("四逆散证")
    void t_sinisanzheng_test() { assertFangzheng("四逆散证", "Shaoyangbing",
            "Sinisanzheng", "Sinisan",
            "Shouzuleng;Wanglaihanre;Xiongxiekuman;Kouku;Futong;Xieli", "Xianmai"); }

    // ============ 白虎汤类 (101~105) ============

    @Test @Order(101) @DisplayName("白虎汤证诊断")
    void shouldDiagnoseBaihuTangPattern() {
        Map<String, Object> variables = Map.of(
                "symptomIris", List.of(
                        NS + "Danrebuhan_instance", NS + "Kouke_instance",
                        NS + "Dare_instance", NS + "Dake_instance", NS + "Dahan_instance"),
                "pulseIris", List.of(NS + "Hongmai_instance"),
                "tongueIris", List.of(), "fuzhengIris", List.of());
        ProcessInstanceResult result = startProcessAndGetResult(variables);
        printResult("白虎汤证", result);
        assertBasicResult(result, "Yangmingbing", "Baihutangzheng", NS + "Baihutang");
        assertBagang(result, List.of("里证"), null, List.of("阳证"));
    }

    @Test @Order(102) @DisplayName("白虎加人参汤证")
    void t_baihujiarenshentang() { assertFangzheng("白虎加人参汤证", "Yangmingbing",
            "Baihujiarenshentangzheng", "Baihujiarenshentang",
            "Dare;Dake;Dahan;Kousheganzao", "Hongmai", "", ""); }

    @Test @Order(103) @DisplayName("白虎加桂枝汤证")
    void t_baihujiaguizhitang() { assertFangzheng("白虎加桂枝汤证", "Yangmingbing",
            "Baihujiaguizhitangzheng", "Baihujiaguizhitang",
            "Danrebuhan;Gujietengfan;Dare;Dake;Dahan", "Pingmai"); }

    @Test @Order(104) @DisplayName("白虎汤证诊断（三阳合病）")
    @SuppressWarnings("unchecked")
    void shouldDiagnoseBaihuTangPattern_SanYang() {
        Map<String, Object> variables = Map.of(
                "symptomIris", List.of(
                        NS + "Dare_instance", NS + "Dahan_instance", NS + "Dake_instance",
                        NS + "Kouke_instance", NS + "Efeng_instance",
                        NS + "Wanglaihanre_instance", NS + "Xiongxiekuman_instance"),
                "pulseIris", List.of(NS + "Fumai_instance", NS + "Hongmai_instance"),
                "tongueIris", List.of(), "fuzhengIris", List.of());
        ProcessInstanceResult result = startProcessAndGetResult(variables);
        printResult("白虎汤证（三阳合病）", result);
        Map<String, Object> vars = result.getVariablesAsMap();
        assertThat((List<String>) vars.get("liujingTypes"))
                .containsExactlyInAnyOrder("Taiyangbing", "Yangmingbing", "Shaoyangbing");
        assertThat(vars.get("isCombinedChannel")).isEqualTo(true);
        assertThat(vars.get("sixChannel")).isEqualTo("三阳合病");
        assertThat(vars.get("combinedDiseaseMark")).isEqualTo("三阳合病");
        assertThat(vars.get("fangzheng")).isEqualTo("Baihutangzheng");
        assertThat(vars.get("finalFormula")).isEqualTo(NS + "Baihutang");
    }

    @Test @Order(105) @DisplayName("竹叶石膏汤证")
    void t_zhuyeshigaotang() { assertFangzheng("竹叶石膏汤证", "Yangmingbing",
            "Zhuyeshigaotangzheng", "Zhuyeshigaotang",
            "Xulei;Shaoqi;Yutu;Dare;Kouke", "Xushumai", "Shehongshaotai", ""); }

    // ============ 承气汤类 (121~139) ============

    // 修改点（本轮）：Fuman、Futong、Juan 从 symptomIris 回滚到 fuzhengIris，
    // 避免 Fuman 触发太阴病分支，导致八纲多出「阴证」。
    @Test @Order(121) @DisplayName("大承气汤证诊断")
    void shouldDiagnoseDaChengqiTangPattern() {
        Map<String, Object> variables = Map.of(
                "symptomIris", List.of(
                        NS + "Danrebuhan_instance", NS + "Kouke_instance",
                        NS + "Chaore_instance", NS + "Zaoshi_instance",
                        NS + "Zhanwang_instance"),
                "pulseIris", List.of(NS + "Chenshimai_instance"),
                "tongueIris", List.of(
                        NS + "YellowCoating_instance", NS + "DryCoating_instance",
                        NS + "TongueWithThorns_instance"),
                "fuzhengIris", List.of(
                        NS + "Fuman_instance", NS + "Futong_instance", NS + "Juan_instance"));
        ProcessInstanceResult result = startProcessAndGetResult(variables);
        printResult("大承气汤证", result);
        assertBasicResult(result, "Yangmingbing", "Dachengqitangzheng", NS + "Dachengqitang");
        assertBagang(result, List.of("里证"), List.of("实证"), List.of("阳证"));
    }

    @Test @Order(122) @DisplayName("小承气汤证")
    void t_xiaochengqitang() { assertFangzheng("小承气汤证", "Yangmingbing",
            "Xiaochengqitangzheng", "Xiaochengqitang",
            "Fuman;Chaore;Zhanyu", "Huamai"); }

    @Test @Order(123) @DisplayName("调胃承气汤证")
    void t_tiaoweichengqitang() { assertFangzheng("调胃承气汤证", "Yangmingbing",
            "Tiaoweichengqitangzheng", "Tiaoweichengqitang",
            "Zhengzhengfare;Fuzhangman;Xinfan", "Chenshimai"); }

    @Test @Order(124) @DisplayName("桃核承气汤证")
    void t_taohechengqitang() { assertFangzheng("桃核承气汤证", "Taiyangbing",
            "Taohechengqitangzheng", "Taohechengqitang",
            "Shaofujijie;Rukuang;Xiaobianzili", "Chensemai"); }

    @Test @Order(125) @DisplayName("麻子仁丸证")
    void t_mazirenwan() { assertFangzheng("麻子仁丸证", "Yangmingbing",
            "Mazirenwanzheng", "Mazirenwan", "Dabianying;Xiaobianshu", "Fusemai"); }

    @Test @Order(126) @DisplayName("大黄甘草汤证")
    void t_dahuanggancaotang() { assertFangzheng("大黄甘草汤证", "Yangmingbing",
            "Dahuanggancaotangzheng", "Dahuanggancaotang",
            "Shiyijitu;Dare;Kouke;Dabianying", "Xianmai"); }

    @Test @Order(127) @DisplayName("大黄牡丹汤证")
    void t_dahuangmudantang() { assertFangzheng("大黄牡丹汤证", "Changyongbing",
            "Dahuangmudantangzheng", "Dahuangmudantang",
            "Changyong;Shaofuzhongpi;Anzhijitongrulin;Xiaobianzidiao;Shishifare;" +
                    "Zihanchu;Fuehan;Maichijin", "Chijinmai"); }

    @Test @Order(128) @DisplayName("大黄硝石汤证")
    void t_dahuangxiaoshitang() { assertFangzheng("大黄硝石汤证", "Yangmingbing",
            "Dahuangxiaoshitangzheng", "Dahuangxiaoshitang",
            "Huangdan;Fuman;Xiaobianbuli;Xiaobianchi;Zihan", "Chenshimai"); }

    @Test @Order(129) @DisplayName("大黄附子汤证")
    void t_dahuangfuzitang() { assertFangzheng("大黄附子汤证", "Taiyinbing",
            "Dahuangfuzitangzheng", "Dahuangfuzitang",
            "Xiexiapiantong;Fare;Fuman;Xiali;Buke", "Jinxianmai"); }

    @Test @Order(130) @DisplayName("大黄蛰虫丸证")
    void t_dahuangzhechongwan() { assertFangzheng("大黄蛰虫丸证", "Xulaobing",
            "Dahuangzhechongwanzheng", "Dahuangzhechongwan",
            "Wulaoxuji;Fuman;Bunengyinshi;Jifujiacuo;Liangmuanhei", "Chensemai"); }

    @Test @Order(131) @DisplayName("大黄甘遂汤证")
    void t_dahuanggansuitang() { assertFangzheng("大黄甘遂汤证", "Furenzabing",
            "Dahuanggansuitangzheng", "Dahuanggansuitang",
            "Furenshaofumanrudunzhuang;Xiaobiannan;Buke", "Chenxianmai"); }

    @Test @Order(132) @DisplayName("厚朴大黄汤证")
    void t_houpodahuangtang() { assertFangzheng("厚朴大黄汤证", "Yangmingbing",
            "Houpodahuangtangzheng", "Houpodahuangtang",
            "Zhiyinxiongman;Dare;Kouke;Dabianying", "Chenshimai"); }

    @Test @Order(133) @DisplayName("厚朴七物汤证")
    void t_houpoqiwutang() { assertFangzheng("厚朴七物汤证", "TaiyangYangmingHebing",
            "Houpoqiwutangzheng", "Houpoqiwutang",
            "Fuman;Fare;Yinshirugu;Ehan;Wuhan;Dabianying;Chaore", "Fushumai"); }

    @Test @Order(134) @DisplayName("厚朴三物汤证")
    void t_houpousanwutang() { assertFangzheng("厚朴三物汤证", "Yangmingbing",
            "Houposanwutangzheng", "Houposanwutang",
            "Futong;Budabian;Dare;Kouke;Chaore;Zhanyu", "Chenshimai"); }

    @Test @Order(135) @DisplayName("厚朴生姜半夏甘草人参汤证")
    void t_houpoushengjiangbanxiagancaorenshentang() { assertFangzheng(
            "厚朴生姜半夏甘草人参汤证", "Taiyinbing",
            "Houposhengjiangbanxiagancaorenshentangzheng",
            "Houposhengjiangbanxiagancaorenshentang",
            "Fuzhangman;AnzhiButong", "Fumai"); }

    @Test @Order(136) @DisplayName("下瘀血汤证")
    void t_xiayuxuetang() { assertFangzheng("下瘀血汤证", "Taiyinbing",
            "Xiayuxuetangzheng", "Xiayuxuetang",
            "Futong;Shaofujijie;Citong", "Chenxianmai"); }

    @Test @Order(137) @DisplayName("己椒苈黄丸证")
    void t_jijiaolihuangwan() { assertFangzheng("己椒苈黄丸证", "Taiyinbing",
            "Jijiaolihuangwanzheng", "Jijiaolihuangwan",
            "Fuman;Kousheganzao;Changjianyoushuiqi", "Chenxianmai"); }

    @Test @Order(138) @DisplayName("枳实芍药散证")
    void t_zhishishaoyaosan() { assertFangzheng("枳实芍药散证", "Taiyinbing",
            "Zhishishaoyaosanzheng", "Zhishishaoyaosan",
            "Chanhoufutong;Fanman;Budewo;Fuman;Xiali;Buke", "Xianmai"); }

    @Test @Order(139) @DisplayName("枳实栀子豉汤证")
    void t_zhishizhizichitang() { assertFangzheng("枳实栀子豉汤证", "Chahoulaofubing",
            "Zhishizhizichitangzheng", "Zhishizhizichitang",
            "Dabingchaihou;Laofu", "Fumai"); }

    // ============ 泻心汤类 (161~167, 170) ============

    @Test @Order(161) @DisplayName("半夏泻心汤证")
    void t_banxiaxiexintang() { assertFangzheng("半夏泻心汤证", "Taiyinbing",
            "Banxiaxiexintangzheng", "Banxiaxiexintang",
            "Xinxiapi;Ou;Xiali;Fuman;Buke;Shibuxia", "Xianmai"); }

    @Test @Order(162) @DisplayName("生姜泻心汤证")
    void t_shengjiangxiexintang() { assertFangzheng("生姜泻心汤证", "Taiyinbing",
            "Shengjiangxiexintangzheng", "Shengjiangxiexintang",
            "Xinxiapiying;Gantaishichou;Leiming;Xiali;Fuman;Buke;Shibuxia", "Xianmai"); }

    @Test @Order(163) @DisplayName("甘草泻心汤证（伤寒）")
    void t_gancaoxiexintang() { assertFangzheng("甘草泻心汤证（伤寒）", "Taiyinbing",
            "Gancaoxiexintangzheng", "Gancaoxiexintang",
            "Xialibuzhi;Xinfan;Fuman;Buke;Shibuxia", "Xianmai"); }

    @Test @Order(164) @DisplayName("甘草泻心汤证（狐惑）")
    void t_gancaoxiexintang_huhuo() { assertFangzheng("甘草泻心汤证（狐惑）", "Taiyinbing",
            "Gancaoxiexintangzheng_huhuo", "Gancaoxiexintang",
            "Zhuangrushanghan;Momoyumian;Mubudebi;Woqibuan;Buyuyinshi;Ewenshichou;" +
                    "Mianmuzhachizhazaizhabai;Fuman;Buke;Shibuxia", "Xianmai"); }

    @Test @Order(165) @DisplayName("大黄黄连泻心汤证")
    void t_dahuanghuanglianxiexintang() { assertFangzheng("大黄黄连泻心汤证", "Yangmingbing",
            "Dahuanghuanglianxiexintangzheng", "Dahuanghuanglianxiexintang",
            "Xinxiapi;Anzhiru;Dare;Kouke;Chaore;Dabianying", "Fumai"); }

    @Test @Order(166) @DisplayName("附子泻心汤证")
    void t_fuzixiexintang() { assertFangzheng("附子泻心汤证", "Yangmingbing",
            "Fuzixiexintangzheng", "Fuzixiexintang",
            "Xinxiapi;Ehan;Hanchu;Dare;Kouke;Chaore;Dabianying", "Fumai"); }

    @Test @Order(167) @DisplayName("泻心汤证")
    void t_xiexintang() { assertFangzheng("泻心汤证", "Yangmingbing",
            "Xiexintangzheng", "Xiexintang",
            "Tuxue;Nvxue;Xinqibuzu", "Hongmai"); }

    @Test @Order(170) @DisplayName("半夏泻心汤证（生姜泻心汤衍化）")
    void t_shengjiangxiexintang_alias() { assertFangzheng("生姜泻心汤证", "Taiyinbing",
            "Shengjiangxiexintangzheng", "Shengjiangxiexintang",
            "Xinxiapiying;Gantaishichou;Leiming;Xiali;Fuman;Buke;Shibuxia", "Xianmai"); }

    // ============ 栀子豉类 (321~327) ============

    @Test @Order(321) @DisplayName("栀子豉汤证")
    void t_zhizichitang() { assertFangzheng("栀子豉汤证", "Yangmingbing",
            "Zhizichitangzheng", "Zhizichitang",
            "Xufan;Budemian;Xinzhongaonao", "Fumai"); }

    @Test @Order(322) @DisplayName("栀子甘草豉汤证")
    void t_zhizigancaochitang() { assertFangzheng("栀子甘草豉汤证", "Yangmingbing",
            "Zhizigancaochitangzheng", "Zhizigancaochitang",
            "Xinzhongaonao;Shaoqi;Xufan;Budemian;Dare;Kouke", "Fumai"); }

    @Test @Order(323) @DisplayName("栀子生姜豉汤证")
    void t_zhizishengjiangchitang() { assertFangzheng("栀子生姜豉汤证", "Yangmingbing",
            "Zhizishengjiangchitangzheng", "Zhizishengjiangchitang",
            "Xinzhongaonao;Ou;Xufan;Budemian;Dare;Kouke", "Fumai"); }

    @Test @Order(324) @DisplayName("栀子厚朴汤证")
    void t_zhizihoupotang() { assertFangzheng("栀子厚朴汤证", "Yangmingbing",
            "Zhizihoupotangzheng", "Zhizihoupotang",
            "Xinfan;Fuman;Woqibuan", "Fumai"); }

    @Test @Order(325) @DisplayName("栀子干姜汤证")
    void t_zhiziganjiangtang() { assertFangzheng("栀子干姜汤证", "Yangmingbing",
            "Zhiziganjiangtangzheng", "Zhiziganjiangtang",
            "Shenrebuqu;Weifan;Dare;Kouke;Chaore;Dabianying", "Fushumai"); }

    @Test @Order(326) @DisplayName("栀子柏皮汤证")
    void t_zhizibaipitang() { assertFangzheng("栀子柏皮汤证", "Yangmingbing",
            "Zhizibaipitangzheng", "Zhizibaipitang", "Shenhuang;Fare", "Fumai"); }

    @Test @Order(327) @DisplayName("栀子大黄汤证")
    void t_zhizidahuangtang() { assertFangzheng("栀子大黄汤证", "Huangdanbing",
            "Zhizidahuangtangzheng", "Zhizidahuangtang",
            "Jiuhuangdan;Xinzhongaonao;Futong", "Xianshumai"); }

    // ============ 其他（阳明/合病类） 591 ============

    @Test @Order(591) @DisplayName("茵陈蒿汤证")
    void t_yinchenhaotang() { assertFangzheng("茵陈蒿汤证", "Yangmingbing",
            "Yinchenhaotangzheng", "Yinchenhaotang",
            "Shenhuang;Xiaobianbuli;Fuman;Kouke", "Chenshimai"); }
}