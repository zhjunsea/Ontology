package com.ocean.ontologyframework.tcm;

import io.camunda.zeebe.client.api.response.ProcessInstanceResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static com.ocean.ontologyframework.tcm.JingfangTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("【独立】陷胸/栀子/瓜蒂/十枣/其他伤寒杂方")
class DuliFangzhengTest extends AbstractJingfangDiagnosisTest {

    // ============ 陷胸汤类 (301~303) ============

    

    @Test @Order(582) @DisplayName("十枣汤证")
    void t_shizaotang() { assertFangzheng("十枣汤证", "Taiyangbing",
            "Shizaotangzheng", "Shizaotang",
            "Xinxiapiying;Xietong;Ganou;Duanqi", "Chenxianmai"); }

    @Test @Order(584) @DisplayName("瓜蒂散证")
    void t_guadisan() { assertFangzheng("瓜蒂散证", "Taiyangbing",
            "Guadisanzheng", "Guadisan",
            "Xiongzhongpiying;Qishangchongxiongyan;Budexi", "Weimai"); }

    @Test @Order(589) @DisplayName("蜜煎导方证")
    void t_mijiandaofang() { assertFangzheng("蜜煎导方证", "Yangmingbing",
            "Mijiandaofangzheng", "Mijiandaofang",
            "Dabianying;Xiaobianzili;Kousheganzao", ""); }

    @Test @Order(590) @DisplayName("猪胆汁方证")
    void t_zhudanzhifang() { assertFangzheng("猪胆汁方证", "Yangmingbing",
            "Zhudanzhifangzheng", "Zhudanzhifang",
            "Dabianying;Xiaobianzili;Fare", ""); }

    @Test @Order(593) @DisplayName("一物瓜蒂汤证")
    void t_yiwuguaditang() { assertFangzheng("一物瓜蒂汤证", "Taiyangzhongye",
            "Yiwuguaditangzheng", "Yiwuguaditang",
            "Shenre;Shentong;Shenzhong;Ehan", "Weimai"); }

    @Test @Order(594) @DisplayName("猪膏发煎证")
    void t_zhugaofajian() { assertFangzheng("猪膏发煎证", "Huangdanbing",
            "Zhugaofajianzheng", "Zhugaofajian", "Zhuhuang;Huangdan", "Fumai"); }

    @Test @Order(595) @DisplayName("硝石矾石散证")
    void t_xiaoshifanshisan() { assertFangzheng("硝石矾石散证", "Huangdanbing",
            "Xiaoshifanshisanzheng", "Xiaoshifanshisan",
            "Nvlaodan;Bangguangji;Shaofuman;Shenhuang;Eshanghei;Zuxiare", "Chenximai"); }

    @Test @Order(599) @DisplayName("橘皮竹茹汤证")
    void t_jupizhurutang() { assertFangzheng("橘皮竹茹汤证", "Outuoyuexialibing",
            "Jupizhurutangzheng", "Jupizhurutang", "Hui;Shaoqi", "Xumai"); }

    @Test @Order(600) @DisplayName("橘皮汤证")
    void t_jupitang() { assertFangzheng("橘皮汤证", "Outuoyuexialibing",
            "Jupitangzheng", "Jupitang",
            "Ganou;Hui;Shouzujueni", "Xianmai"); }

    @Test @Order(601) @DisplayName("橘枳姜汤证")
    void t_juzhijiangtang() { assertFangzheng("橘枳姜汤证", "Xiongbibing",
            "Juzhijiangtangzheng", "Juzhijiangtang",
            "Xiongzhongqisai;Duanqi;Xinzhongpiqi", "Chenximai"); }

    @Test @Order(602) @DisplayName("文蛤汤证")
    void t_wengetang() { assertFangzheng("文蛤汤证", "Outuoyuexialibing",
            "Wengetangzheng", "Wengetang",
            "Outu;Kouke;Yuyin;Toutong", "Jinmai"); }

    @Test @Order(603) @DisplayName("紫参汤证")
    void t_zishentang() { assertFangzheng("紫参汤证", "Outuoyuexialibing",
            "Zishentangzheng", "Zishentang", "Xiali;Feitong", "Chenmai"); }

    @Test @Order(604) @DisplayName("诃梨勒散证")
    void t_helilesan() { assertFangzheng("诃梨勒散证", "Outuoyuexialibing",
            "Helilesanzheng", "Helilesan", "Xialiqi", "Chenmai"); }

    // ============ 其他伤寒方 (607~614) ============

    @Test @Order(607) @DisplayName("升麻鳖甲汤证")
    void t_shengmabiejiatang() { assertFangzheng("升麻鳖甲汤证", "Yinyangdu",
            "Shengmabiejiatangzheng", "Shengmabiejiatang",
            "Mianchibanbanrujinwen;Yantong;Tunongxue", "Fumai"); }

    @Test @Order(608) @DisplayName("升麻鳖甲去雄黄蜀椒汤证")
    void t_shengmabiejiaquxionghuangshujiaotang() { assertFangzheng(
            "升麻鳖甲去雄黄蜀椒汤证", "Yinyangdu",
            "Shengmabiejiaquxionghuangshujiaotangzheng",
            "Shengmabiejiaquxionghuangshujiaotang",
            "Mianseqing;Shentong", ""); }

    @Test @Order(609) @DisplayName("苦参汤证（狐惑）")
    void t_kushentang() { assertFangzheng("苦参汤证（狐惑）", "Huhuobing",
            "Kushentangzheng", "Kushentang", "Yangan;Yinzhongshichuang", ""); }

    @Test @Order(610) @DisplayName("雄黄熏方证")
    void t_xionghuangxunfang() { assertFangzheng("雄黄熏方证", "Huhuobing",
            "Xionghuangxunfangzheng", "Xionghuangxunfang", "Yinzhongshichuang", ""); }

    @Test @Order(611) @DisplayName("鳖甲煎丸证")
    void t_biejiajianwan() { assertFangzheng("鳖甲煎丸证", "Nuebing",
            "Biejiajianwanzheng", "Biejiajianwan",
            "Nvemu;Xiexiazhengjia", "Xianmai"); }

    @Test @Order(612) @DisplayName("蜀漆散证")
    void t_shuqisan() { assertFangzheng("蜀漆散证", "Nuebing",
            "Shuqisanzheng", "Shuqisan", "Runvezhuang;Shenduohan;Fahan", "Xianmai"); }

    @Test @Order(613) @DisplayName("牡蛎汤证")
    void t_mulitang() { assertFangzheng("牡蛎汤证", "Nuebing",
            "Mulitangzheng", "Mulitang", "Fahan;Duohanchu", ""); }

    @Test @Order(614) @DisplayName("牡蛎泽泻散证")
    void t_mulizexiesan() { assertFangzheng("牡蛎泽泻散证", "Yangmingbing",
            "Mulizexiesanzheng", "Mulizexiesan",
            "Yaoyixiayoushuiqi;Xiaobianbuli", "Chenmai"); }

    // ============ 从少阳阳明方证迁移过来的方证 (615) ============

    @Test @Order(615) @DisplayName("大黄甘遂汤证")
    void t_dahuanggansuitang() { assertFangzheng("大黄甘遂汤证", "TaiyinYangmingHebing",
            "Dahuanggansuitangzheng", "Dahuanggansuitang",
            "Furenshaofumanrudunzhuang;Xiaobiannan;Buke;Shenghouzhe", "Chenxianmai"); }

    @Test @Order(79) @DisplayName("四逆散证")
    void t_sinisanzheng_test() { assertFangzheng("四逆散证", "Shaoyangbing",
            "Sinisanzheng", "Sinisan",
            "Shouzuleng;Wanglaihanre;Xiongxiekuman;Kouku;Futong;Xiali", "Xianmai"); }

    @Test @Order(137) @DisplayName("己椒苈黄丸证")
    void t_jijiaolihuangwan() { assertFangzheng("己椒苈黄丸证", "Taiyinbing",
            "Jijiaolihuangwanzheng", "Jijiaolihuangwan",
            "Fuman;Kousheganzao;Changmingruzoushui", "Chenxianmai"); }

    @Test @Order(202) @DisplayName("附子粳米汤十八反警告检测")
    @SuppressWarnings("unchecked")
    void shouldWarnOnFuziJingmiTangAntagonism() {
        Map<String, Object> variables = Map.of(
                "symptomIris", List.of(
                        NS + "Futong_instance", NS + "Xiongxiekuman_instance",
                        NS + "Outu_instance", NS + "Fuzhongleiming_instance"),
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

    @Test @Order(205) @DisplayName("薏苡附子散证")
    void t_yiyifuzisan() { assertFangzheng("薏苡附子散证", "Xiongbibing",
            "Yiyifuzisanzheng", "Yiyifuzisan", "Xiongbihuanji", "Chenchimai"); }

    // ============ 苓桂/茯苓 · 少阴系 (245/249/251/259) ============

    @Test @Order(245) @DisplayName("猪苓汤证")
    void t_zhulingtang() { assertFangzheng("猪苓汤证", "Yangmingbing",
            "Zhulingtangzheng", "Zhulingtang",
            "Fare;Kouke;Xiaobianbuli", "Fumai"); }

    @Test @Order(251) @DisplayName("防己茯苓汤证")
    void t_fangjifulingtang() { assertFangzheng("防己茯苓汤证", "Shuiqibing",
            "Fangjifulingtangzheng", "Fangjifulingtang",
            "Pishui;Sizhizhong;Sizhinieniedong", "Fumai"); }

    @Test @Order(246) @DisplayName("猪苓散证")
    void t_zhulingsan() { assertFangzheng("猪苓散证", "Outuoyuexialibing",
            "Zhulingsanzheng", "Zhulingsan",
            "Ou;Tu;Xiongman;Housishui", "Fumai"); }

    @Test @Order(247) @DisplayName("泽泻汤证")
    void t_zexietang() { assertFangzheng("泽泻汤证", "Tanyinbing",
            "Zexietangzheng", "Zexietang",
            "Xinxiayouzhiyin;Kumaoxuan", "Chenxianmai"); }

    @Test @Order(250) @DisplayName("茯苓杏仁甘草汤证")
    void t_fulingxingrengancaotang() { assertFangzheng("茯苓杏仁甘草汤证", "Xiongbibing",
            "Fulingxingrengancaotangzheng", "Fulingxingrengancaotang",
            "Xiongzhongqisai;Duanqi;Xinxiayouzhiyin", "Chenximai"); }

    @Test @Order(252) @DisplayName("木防己汤证")
    void t_mufangjitang() { assertFangzheng("木防己汤证", "Tanyinbing",
            "Mufangjitangzheng", "Mufangjitang",
            "Gejianzhiyin;Chuan;Xiongman;Xinxiapijian;Mianselihei", "Chenjinmai"); }

    @Test @Order(253) @DisplayName("木防己去石膏加茯苓芒硝汤证")
    void t_Mufangjiqushigaojiafulingmangxiaotangzheng() { assertFangzheng(
            "木防己去石膏加茯苓芒硝汤证", "Tanyinbing",
            "Mufangjiqushigaojiafulingmangxiaotangzheng",
            "Mufangjiqushigaojiafulingmangxiaotang",
            "Gejianzhiyin;Xinxiapijian", "Chenjinmai"); }

    @Test @Order(367) @DisplayName("甘遂半夏汤十八反警告检测")
    @SuppressWarnings("unchecked")
    void shouldWarnOnGansuiBanxiaTangAntagonism() {
        Map<String, Object> variables = Map.of(
                "symptomIris", List.of(
                        NS + "Xinxiapi_instance", NS + "Xiali_instance",
                        NS + "Touxuan_instance"),
                "pulseIris", List.of(NS + "Chenxianmai_instance"),
                "tongueIris", List.of(), "fuzhengIris", List.of());
        ProcessInstanceResult result = startProcessAndGetResult(variables);
        printResult("甘遂半夏汤（十八反：甘遂反甘草）", result);
        Map<String, Object> vars = result.getVariablesAsMap();
        assertThat(vars.get("fangzheng")).isEqualTo("Gansuibanxiatangzheng");
        assertThat(vars.get("finalFormula")).isEqualTo(NS + "Gansuibanxiatang");
        List<String> warnings = (List<String>) vars.get("warnings");
        assertThat(warnings).as("应包含十八反警告").isNotNull().isNotEmpty();
        assertThat(warnings).anySatisfy(w -> assertThat(w)
                .contains("十八反").contains("甘遂").contains("甘草"));
    }

    @Test @Order(404) @DisplayName("防己黄芪汤证")
    void t_fangjihuangqitang() { assertFangzheng("防己黄芪汤证", "Shibing",
            "Fangjihuangqitangzheng", "Fangjihuangqitang",
            "Shenzhong;Hanchu;Efeng", "Fumai"); }

    @Test @Order(443) @DisplayName("瓜蒌牡蛎散证")
    void t_gualoumulisan() { assertFangzheng("瓜蒌牡蛎散证", "Baihebing",
            "Gualoumulisanzheng", "Gualoumulisan", "Kouke;Kouku;Xiaobianchi", "Weishumai"); }

    @Test @Order(702) @DisplayName("薯蓣丸证")
    void t_shuyuwan() { assertFangzheng("薯蓣丸证", "Xulaobing",
            "Shuyuwanzheng", "Shuyuwan", "Xulao;Fengqibaiji", "Xumai"); }

    @Test @Order(704) @DisplayName("天雄散证")
    void t_tianxiongsan() { assertFangzheng("天雄散证", "Xulaobing",
            "Tianxiongsanzheng", "Tianxiongsan",
            "Yijing;Yaozhongleng;Yaotong", ""); }

    @Test @Order(713) @DisplayName("蛇床子散证")
    void t_shechuangzisan() { assertFangzheng("蛇床子散证", "Furenzabing",
            "Shechuangzisanzheng", "Shechuangzisan", "Furenyinhan", "Chenximai"); }

    @Test @Order(714) @DisplayName("狼牙汤证")
    void t_langyatantang() { assertFangzheng("狼牙汤证", "Furenzabing",
            "Langyatangzheng", "Langyatang",
            "ShaoyinMaiHuashu;Yinzhongjishengchuang;Yinzhongshichuanglanzhe", "ShaoyinMaiHuashu"); }

    @Test @Order(728) @DisplayName("蜘蛛散证")
    void t_zhizhusan() { assertFangzheng("蜘蛛散证", "Yinhushanbing",
            "Zhizhusanzheng", "Zhizhusan",
            "Yinhushan;Pianyouxiaoda;Shishishangxia", "Xianmai"); }

    @Test @Order(730) @DisplayName("奔豚汤证")
    void t_bentuntang() { assertFangzheng("奔豚汤证", "Bentunbing",
            "Bentuntangzheng", "Bentuntang",
            "Qishangchongxiong;Futong;Wanglaihanre", "Xianmai"); }

    @Test @Order(731) @DisplayName("旋覆花汤证")
    void t_xuanfuhuatang() { assertFangzheng("旋覆花汤证", "Ganzhuobing",
            "Xuanfuhuatangzheng", "Xuanfuhuatang",
            "Ganzhe;Changyudaoqixiongshang;Danyuyinre", "Xianmai"); }

    @Test @Order(732) @DisplayName("皂荚丸证")
    void t_zaojiawan() { assertFangzheng("皂荚丸证", "Feiweifeiyongkesoushangqi",
            "Zaojiawanzheng", "Zaojiawan", "Keni;Shishituzhuo", ""); }

    @Test @Order(733) @DisplayName("千金苇茎汤证")
    void t_qianjinweijingtang() { assertFangzheng("千金苇茎汤证", "Feiyongbing",
            "Qianjinweijingtangzheng", "Qianjinweijingtang",
            "Weire;Fanman;Xiongzhongjiacuo;Feiyong;Kesou", ""); }

    @Test @Order(734) @DisplayName("葶苈大枣泻肺汤证")
    void t_tinglidazaoxiefeitang() { assertFangzheng("葶苈大枣泻肺汤证", "Feiyongbing",
            "Tinglidazaoxiefeitangzheng", "Tinglidazaoxiefeitang",
            "Feiyong;Chuan;Budewo;Xiongman", "Xumai"); }

    @Test @Order(735) @DisplayName("麦门冬汤证")
    void t_maimendongtang() { assertFangzheng("麦门冬汤证", "Kesoushangqibing",
            "Maimendongtangzheng", "Maimendongtang",
            "Huonishangqi;Yanhoubuli", "Xumai"); }

    @Test @Order(736) @DisplayName("泽漆汤证")
    void t_zeqitang() { assertFangzheng("泽漆汤证", "Kesoushangqibing",
            "Zeqitangzheng", "Zeqitang", "Kesou;Chuan", "Chenmai"); }

    @Test @Order(744) @DisplayName("续命汤证")
    void t_xumingtang() { assertFangzheng("续命汤证", "Zhongfengbing",
            "Xumingtangzheng", "Xumingtang",
            "Shentibunengzishouchi;Koujin;Budeyu", ""); }
}