package com.ocean.ontologyframework.tcm;

import io.camunda.zeebe.client.api.response.ProcessInstanceResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static com.ocean.ontologyframework.tcm.JingfangTestSupport.*;

@DisplayName("【太阳】桂枝/麻黄/葛根/苓桂类方证")
class TaiyangFangzhengTest extends AbstractJingfangDiagnosisTest {

    // ============ 桂枝汤类 (1~24) ============

    @Test @Order(1) @DisplayName("桂枝汤证诊断")
    void shouldDiagnoseGuizhiTangPattern() {
        Map<String, Object> variables = Map.of(
                "symptomIris", List.of(
                        NS + "Fare_instance", NS + "Efeng_instance", NS + "Hanchu_instance"),
                "pulseIris", List.of(NS + "Fumai_instance", NS + "Huanmai_instance"),
                "tongueIris", List.of(), "fuzhengIris", List.of());
        ProcessInstanceResult result = startProcessAndGetResult(variables);
        printResult("桂枝汤证", result);
        assertBasicResult(result, "Taiyangbing", "Guizhitangzheng", NS + "Guizhitang");
        assertBagang(result, List.of("表证"), null, List.of("阳证"));
    }

    @Test @Order(2) @DisplayName("桂枝加葛根汤证")
    void t_guizhijiagegentang() { assertFangzheng("桂枝加葛根汤证", "Taiyangbing",
            "Guizhijiagegentangzheng", "Guizhijiagegentang",
            "Xiangbeiqiangjiji;Hanchu;Efeng", "Fumai;Huanmai"); }

    @Test @Order(3) @DisplayName("桂枝加厚朴杏子汤证")
    void t_guizhijiahoupoxingrentang() { assertFangzheng("桂枝加厚朴杏子汤证", "Taiyangbing",
            "Guizhijiahoupoxingzitangzheng", "Guizhijiahoupoxingzitang",
            "Chuan;Hanchu;Efeng", "Fumai;Huanmai"); }

    @Test @Order(4) @DisplayName("桂枝加附子汤证")
    void t_guizhijiafuzitang() { assertFangzheng("桂枝加附子汤证", "Taiyangbing",
            "Guizhijiafuzitangzheng", "Guizhijiafuzitang",
            "Hanloubuzhi;Efeng;Xiaobiannan;Sizhiweiji", "Fumai;Xumai"); }

    @Test @Order(5) @DisplayName("桂枝去芍药汤证诊断")
    void shouldDiagnoseGuizhiQuShaoyaoTangPattern() {
        Map<String, Object> variables = Map.of(
                "symptomIris", List.of(
                        NS + "Xiongman_instance", NS + "Efeng_instance",
                        NS + "Fare_instance", NS + "Hanchu_instance"),
                "pulseIris", List.of(NS + "Cumai_instance"),
                "tongueIris", List.of(), "fuzhengIris", List.of());
        ProcessInstanceResult result = startProcessAndGetResult(variables);
        printResult("桂枝去芍药汤证", result);
        assertBasicResult(result, "Taiyangbing",
                "Guizhiqushaoyaotangzheng", NS + "Guizhiqushaoyaotang");
        assertBagang(result, List.of("表证"), null, List.of("阳证"));
    }

    @Test @Order(6) @DisplayName("桂枝去芍药加附子汤证")
    void t_guizhiqushaoyaojiafuzitang() { assertFangzheng("桂枝去芍药加附子汤证", "Taiyangbing",
            "Guizhiqushaoyaojiafuzitangzheng", "Guizhiqushaoyaojiafuzitang",
            "Xiongman;Ehan", ""); }

    @Test @Order(7) @DisplayName("桂枝新加汤证")
    void t_guizhixinjiatang() { assertFangzheng("桂枝新加汤证", "Taiyangbing",
            "Guizhixinjiatangzheng", "Guizhixinjiatang",
            "Shentengfan;Ehan", "Chenchimai"); }

    @Test @Order(10) @DisplayName("桂枝加桂汤证")
    void t_guizhijiaguitang() { assertFangzheng("桂枝加桂汤证", "Bentunbing",
            "Guizhijiaguitangzheng", "Guizhijiaguitang",
            "Qicongshaofushangchongxin", "Chenchimai"); }

    @Test @Order(12) @DisplayName("桂枝加黄芪汤证")
    void t_guizhijiahuangqitang() { assertFangzheng("桂枝加黄芪汤证", "Shuiqibing",
            "Guizhijiahuangqitangzheng", "Guizhijiahuangqitang",
            "Huanghan;Liangjingzileng;Shiyihanchu;Muchangdaohanchu", "Chenmai"); }

    @Test @Order(13) @DisplayName("桂枝甘草汤证")
    void t_guizhigancaotang() { assertFangzheng("桂枝甘草汤证", "Taiyangbing",
            "Guizhigancaotangzheng", "Guizhigancaotang",
            "Xinxiajidong;Yudean", "Fumai"); }

    @Test @Order(14) @DisplayName("桂枝甘草龙骨牡蛎汤证")
    void t_guizhigancaolonggumulitang() { assertFangzheng("桂枝甘草龙骨牡蛎汤证", "Taiyangbing",
            "Guizhigancaolonggumulitangzheng", "Guizhigancaolonggumulitang",
            "Fanzao", "Fumai"); }

    @Test @Order(15) @DisplayName("桂枝去芍药加蜀漆牡蛎龙骨救逆汤证")
    void t_guizhiqushaoyaojiashuqimulilonggujiunitang() { assertFangzheng(
            "桂枝去芍药加蜀漆牡蛎龙骨救逆汤证", "Taiyangbing",
            "Guizhiqushaoyaojiashuqimulilonggujiunitangzheng",
            "Guizhiqushaoyaojiashuqimulilonggujiunitang",
            "Shanghanmaifu;Jingkuang;Woqibuan", "Fumai"); }

    @Test @Order(16) @DisplayName("桂枝人参汤证")
    void t_guizhirenshentang() { assertFangzheng("桂枝人参汤证", "Taiyangbing;Taiyinbing",
            "Guizhirenshentangzheng", "Guizhirenshentang",
            "Xinxiapiying;Xialibuzhi;Fare;Ehan", "Fumai;Xumai"); }

    @Test @Order(24) @DisplayName("桂枝去桂加茯苓白术汤证")
    void t_guizhiquguijiafulingbaizhutang() { assertFangzheng("桂枝去桂加茯苓白术汤证", "Taiyangbing",
            "Guizhiquguijiafulingbaizhutangzheng", "Guizhiquguijiafulingbaizhutang",
            "Toutong;Fare;Wuhan;Xinxiaman;Xiaobianbuli", "Fumai"); }

    // ============ 麻黄汤类 (31~50，33-36 已挪至他类) ============

    @Test @Order(31) @DisplayName("麻黄汤证诊断")
    void shouldDiagnoseMahuangTangPattern() {
        Map<String, Object> variables = Map.of(
                "symptomIris", List.of(
                        NS + "Ehan_instance", NS + "Fare_instance",
                        NS + "Wuhan_instance", NS + "Shentong_instance"),
                "pulseIris", List.of(NS + "Fumai_instance", NS + "Jinmai_instance"),
                "tongueIris", List.of(NS + "ThinCoating_instance"),
                "fuzhengIris", List.of());
        ProcessInstanceResult result = startProcessAndGetResult(variables);
        printResult("麻黄汤证", result);
        assertBasicResult(result, "Taiyangbing", "Mahuangtangzheng", NS + "Mahuangtang");
        assertBagang(result, List.of("表证"), List.of("实证"), List.of("阳证"));
    }

    @Test @Order(32) @DisplayName("麻黄加术汤证")
    void t_mahuangjiazhutang() {
        Map<String, Object> variables = Map.of(
                "symptomIris", List.of(
                        NS + "Ehan_instance", NS + "Fare_instance", NS + "Wuhan_instance",
                        NS + "Shenzhong_instance", NS + "Shentengfan_instance"),
                "pulseIris", List.of(NS + "Fumai_instance", NS + "Jinmai_instance"),
                "tongueIris", List.of(NS + "ThinCoating_instance"),
                "fuzhengIris", List.of());
        ProcessInstanceResult result = startProcessAndGetResult(variables);
        printResult("麻黄加术汤证", result);
        assertBasicResult(result, "Taiyangbing",
                "Mahuangjiazhutangzheng", NS + "Mahuangjiazhutang");
        assertBagang(result, List.of("表证"), List.of("实证"), List.of("阳证"));
    }

    @Test @Order(37) @DisplayName("麻杏石甘汤证")
    void t_maxingganshitang() {
        assertFangzheng("麻杏石甘汤证", "Yangmingbing",
                "Maxingshigantangzheng", "Maxingshigantang",
                "Hanchu;Chuan;Fare;Kouke", "Fumai;Shumai");
    }

    @Test @Order(38) @DisplayName("麻杏薏甘汤证")
    void t_maxingyigantang() { assertFangzheng("麻杏薏甘汤证", "Taiyangbing",
            "Maxingyigantangzheng", "Maxingyigantang",
            "Yishenjinteng;Fare;Ribusuoju;Wuhan", "Fumai"); }

    @Test @Order(39) @DisplayName("麻黄连翘赤小豆汤证")
    void t_mahuanglianqiaochixiaodoutang() {
        assertFangzheng("麻黄连翘赤小豆汤证", "Taiyangbing;Yangmingbing",
                "Mahuanglianqiaochixiaodoutangzheng", "Mahuanglianqiaochixiaodoutang",
                "Shenhuang;Fare;Ehan", "Fumai");
    }

    @Test @Order(40) @DisplayName("大青龙汤证")
    void t_daqinglongtang() { assertFangzheng("大青龙汤证", "Taiyangbing",
            "Daqinglongtangzheng", "Daqinglongtang",
            "Fare;Ehan;Shentong;Wuhan;Fanzao", "Fumai;Jinmai"); }

    @Test @Order(41) @DisplayName("小青龙汤证")
    void t_xiaoqinglongtang() { assertFangzheng("小青龙汤证", "Taiyangbing",
            "Xiaoqinglongtangzheng", "Xiaoqinglongtang",
            "Fare;Ehan;Wuhan;Kesou;Chuan;Ganou;Tanduoqingxi", "Fumai;Jinmai"); }

    @Test @Order(42) @DisplayName("小青龙加石膏汤证")
    void t_xiaoqinglongjiashigaotang() { assertFangzheng("小青龙加石膏汤证", "Feizhangbing",
            "Xiaoqinglongjiashigaotangzheng", "Xiaoqinglongjiashigaotang",
            "Kesou;Chuan;Fanzao", "Fumai"); }

    @Test @Order(45) @DisplayName("越婢汤证")
    void t_yuebitang() { assertFangzheng("越婢汤证", "Shuiqibing",
            "Yuebitangzheng", "Yuebitang",
            "Fengshuiefeng;Yishenxizhong;Buke", "Fumai"); }

    @Test @Order(48) @DisplayName("甘草麻黄汤证")
    void t_gancaomahuangtang() { assertFangzheng("甘草麻黄汤证", "Shuiqibing",
            "Gancaomahuangtangzheng", "Gancaomahuangtang", "Lishui", "Chenmai"); }

    @Test @Order(49) @DisplayName("杏子汤证")
    void t_xingzitang() { assertFangzheng("杏子汤证", "Shuiqibing",
            "Xingzitangzheng", "Xingzitang", "Shuizhiweibing", "Fumai"); }

    // ============ 葛根汤类 (61~63) ============

    @Test @Order(61) @DisplayName("葛根汤证")
    void t_gegentang() { assertFangzheng("葛根汤证", "Taiyangyangminghebing",
            "Gegentangzheng", "Gegentang",
            "Xiangqiang;Wuhan;Efeng", "Fumai;Jinmai"); }

    @Test @Order(62) @DisplayName("葛根加半夏汤证")
    void t_gegenjiabanxiatang() { assertFangzheng("葛根加半夏汤证", "Taiyangyangminghebing",
            "Gegenjiabanxiatangzheng", "Gegenjiabanxiatang",
            "Xiangqiang;Wuhan;Outu", "Fumai;Jinmai"); }

    @Test @Order(63) @DisplayName("葛根芩连汤证")
    void t_gegenqinliantang() { assertFangzheng("葛根芩连汤证", "Taiyangyangminghebing",
            "Gegenhuangqinhuangliantangzheng", "Gegenhuangqinhuangliantang",
            "Xiali;Shenre;Chuan;Hanchu", "Cumai"); }

    @Test @Order(242) @DisplayName("茯苓桂枝白术甘草汤证")
    void t_fulingguizhibaizhugancaotang() { assertFangzheng("茯苓桂枝白术甘草汤证", "Taiyangbing",
            "Fulingguizhibaizhugancaotangzheng", "Lingguizhugantang",
            "Xinxianiman;Qishangchongxiong;Qizetouxuan", "Chenjinmai"); }@Test @Order(301) @DisplayName("大陷胸汤证")
    void t_daxianxiongtang() { assertFangzheng("大陷胸汤证", "Taiyangbing",
            "Daxianxiongtangzheng", "Daxianxiongtang",
            "Xinxiatong", "Chenjinmai", "", "Anzhishiying"); }

    @Test @Order(302) @DisplayName("大陷胸丸证")
    void t_daxianxiongwan() { assertFangzheng("大陷胸丸证", "Taiyangbing",
            "Daxianxiongwanzheng", "Daxianxiongwan",
            "Jiexiong;Xiangqiang", "Chenjinmai"); }

    @Test @Order(303) @DisplayName("小陷胸汤证")
    void t_xiaoxianxiongtang() { assertFangzheng("小陷胸汤证", "Taiyangbing",
            "Xiaoxianxiongtangzheng", "Xiaoxianxiongtang",
            "Xinxiapi", "Fuhuamai", "", "Antong"); }

    @Test @Order(585) @DisplayName("三物白散证")
    void t_sanwubaisan() { assertFangzheng("三物白散证", "Taiyangbing",
            "Sanwubaisanzheng", "Sanwubaisan",
            "Hanshijiexiong;Wurezheng", "Chenjinmai"); }

    @Test @Order(903) @DisplayName("麻黄汤证诊断，主证不全返回TOP1匹配的症状")
    void shouldDiagnoseMahuangTangPatternWithouthEnoughSym() {
        Map<String, Object> variables = Map.of(
                "symptomIris", List.of(
                        NS + "Fare_instance", NS + "Ehan_instance", NS + "Wuhan_instance"),
                "pulseIris", List.of(NS + "Fumai_instance", NS + "Jinmai_instance"),
                "tongueIris", List.of(), "fuzhengIris", List.of());
        ProcessInstanceResult result = startProcessAndGetResult(variables);
        printResult("麻黄汤证", result);
        assertBasicResult(result, "Taiyangbing", "Mahuangtangzheng", NS + "Mahuangtang");
        assertBagang(result, List.of("表证"), List.of("实证"), List.of("阳证"));
    }

    @Test @Order(203) @DisplayName("白术附子汤证")
    void t_baizhufuzitang() { assertFangzheng("白术附子汤证", "Taiyangbing;Taiyinbing",
            "Baizhufuzitangzheng", "Baizhufuzitang",
            "Shentengfan;Dabianying;Xiaobianzili", "Fuxusemai"); }

    @Test @Order(204) @DisplayName("甘草附子汤证")
    void t_gancaofuzitang() { assertFangzheng("甘草附子汤证", "Taiyangbing;Shaoyinbing",
            "Gancaofuzitangzheng", "Gancaofuzitang",
            "Gujietengfan;Chetongbudequshen;Hanchu;Duanqi;Xiaobianbuli;Efeng", "Fumai"); }

    @Test @Order(262) @DisplayName("桂枝附子汤证（太阴）")
    void t_guizhifuzitang_taiyin() { assertFangzheng("桂枝附子汤证", "Taiyangbing",
            "Guizhifuzitangzheng", "Guizhifuzitang",
            "Shentengfan;Nanyizhuance", "Fuxusemai"); }

    @Test @Order(403) @DisplayName("黄芪芍药桂枝苦酒汤证")
    void t_huangqishaoyaoguizhikujiutang() { assertFangzheng("黄芪芍药桂枝苦酒汤证", "Shuiqibing",
            "Huangqishaoyaoguizhikujiutangzheng", "Huangqishaoyaoguizhikujiutang",
            "Huanghan;Shentizhong;Fare;Hanchu;Kouke;Hanzhanyi;Sezhenghuangrubaizhi", "Chenmai"); }

    // ============ 栝楼/薤白类 (441~447) ============

    @Test @Order(441) @DisplayName("栝蒌桂枝汤证")
    void t_gualouguizhitang() { assertFangzheng("栝蒌桂枝汤证", "Jingbing",
            "Gualouguizhitangzheng", "Gualouguizhitang",
            "Shentiqiangjiji;Fare;Hanchu;Efeng", "Chenchimai"); }

    @Test @Order(720) @DisplayName("竹叶汤证")
    void t_zhuyetang() { assertFangzheng("竹叶汤证", "Taiyangbing",
            "Zhuyetangzheng", "Zhuyetang",
            "Chanhouzhongfeng;Fare;Mianchi;Chuan;Toutong", "Fumai"); }
}