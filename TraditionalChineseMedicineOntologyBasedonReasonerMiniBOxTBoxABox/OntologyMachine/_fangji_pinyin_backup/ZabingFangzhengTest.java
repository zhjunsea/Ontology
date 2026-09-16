package com.ocean.ontologyframework.tcm;

import io.camunda.zeebe.client.api.response.ProcessInstanceResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static com.ocean.ontologyframework.tcm.JingfangTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("【杂病】苓桂/半夏/黄芪/栝楼薤白/百合/当归/金匮杂病")
class ZabingFangzhengTest extends AbstractJingfangDiagnosisTest {

    // ============ 苓桂/茯苓类 · 杂病 (243/246/247/250/252~258) ============

    @Test @Order(243) @DisplayName("苓桂术甘汤证")
    void t_lingguizhugantang() { assertFangzheng("苓桂术甘汤证", "Tanyinbing",
            "Lingguizhugantangzheng", "Lingguizhugantang",
            "Xinxianiman;Qishangchongxiong;Qizetouxuan", "Chenjinmai"); }

    @Test @Order(246) @DisplayName("猪苓散证")
    void t_zhulingsan() { assertFangzheng("猪苓散证", "Outuoyuexialibing",
            "Zhulingsanzheng", "Zhulingsan",
            "Outuerbingzaigeshang;Housishui", "Fumai"); }

    @Test @Order(247) @DisplayName("泽泻汤证")
    void t_zexietang() { assertFangzheng("泽泻汤证", "Tanyinbing",
            "Zexietangzheng", "Zexietang",
            "Xinxiayouzhiyin;Kumaoxuan", "Chenxianmai"); }

    @Test @Order(250) @DisplayName("茯苓杏仁甘草汤证")
    void t_fulingxingrengancaotang() { assertFangzheng("茯苓杏仁甘草汤证", "Xiongbibing",
            "Fulingxingrengancaotangzheng", "Fulingxingrengancaotang",
            "Xiongzhongqisai;Duanqi", "Chenximai"); }

    @Test @Order(252) @DisplayName("木防己汤证")
    void t_mufangjitang() { assertFangzheng("木防己汤证", "Tanyinbing",
            "Mufangjitangzheng", "Mufangjitang",
            "Gejianzhiyin;Chuanman;XinxiaPijian;MianseLihei", "Chenjinmai"); }

    @Test @Order(253) @DisplayName("木防己去石膏加茯苓芒硝汤证")
    void t_Mufangjiqushigaojiafulingmangxiaotangzheng() { assertFangzheng(
            "木防己去石膏加茯苓芒硝汤证", "Tanyinbing",
            "Mufangjiqushigaojiafulingmangxiaotangzheng",
            "Mufangjiqushigaojiafulingmangxiaotang",
            "Gejianzhiyin;XinxiaPijian", "Chenjinmai"); }

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

    // ============ 半夏类 (361~368) ============

    @Test @Order(361) @DisplayName("小半夏汤证")
    void t_xiaobanxiatang() { assertFangzheng("小半夏汤证", "Tanyinbing",
            "Xiaobanxiatangzheng", "Xiaobanxiatang",
            "Oujiabenke;Fanbuke;Xinxiayouzhiyin", "Xianmai"); }

    @Test @Order(362) @DisplayName("小半夏加茯苓汤证")
    void t_xiaobanxiajiafulingtang() { assertFangzheng("小半夏加茯苓汤证", "Tanyinbing",
            "Xiaobanxiajiafulingtangzheng", "Xiaobanxiajiafulingtang",
            "Zuoutu;Xinxiapi;Gejianyoushui;Xuanji", "Xianmai"); }

    @Test @Order(363) @DisplayName("大半夏汤证")
    void t_dabanxiatang() { assertFangzheng("大半夏汤证", "Outuoyuexialibing",
            "Dabanxiatangzheng", "Dabanxiatang", "Outu", ""); }

    @Test @Order(364) @DisplayName("半夏厚朴汤证")
    void t_banxiahoupotang() { assertFangzheng("半夏厚朴汤证", "Furenzabing",
            "Banxiahoupotangzheng", "Banxiahoupotang",
            "Furenyanzhongruyouzhilian", "Xianmai"); }

    @Test @Order(365) @DisplayName("半夏干姜散证")
    void t_banxiaganjiangsan() { assertFangzheng("半夏干姜散证", "Outuoyuexialibing",
            "Banxiaganjiangsanzheng", "Banxiaganjiangsan",
            "Ganou;Tuxianmo", "Xianmai"); }

    @Test @Order(366) @DisplayName("干姜人参半夏丸证")
    void t_ganjiangrenshenbanxiawan() { assertFangzheng("干姜人参半夏丸证", "Renshengbing",
            "Ganjiangrenshenbanxiawanzheng", "Ganjiangrenshenbanxiawan",
            "Renshenoutubuzhi", "Xumai"); }

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

    @Test @Order(368) @DisplayName("生姜半夏汤证")
    void t_shengjiangbanxiatang() { assertFangzheng("生姜半夏汤证", "Outuoyuexialibing",
            "Shengjiangbanxiatangzheng", "Shengjiangbanxiatang",
            "Xiongzhongkuikui", "Xianmai"); }

    // ============ 黄芪类 (401~404) ============

    @Test @Order(401) @DisplayName("黄芪桂枝五物汤证")
    void t_huangqiguizhiwuwutang() { assertFangzheng("黄芪桂枝五物汤证", "Xuebibing",
            "Huangqiguizhiwuwutangzheng", "Huangqiguizhiwuwutang",
            "Shentiburen;Rufengbizhuang", "Weisemai"); }

    @Test @Order(402) @DisplayName("黄芪建中汤证")
    void t_huangqijianzhongtang() { assertFangzheng("黄芪建中汤证", "Xulaobing",
            "Huangqijianzhongtangzheng", "Huangqijianzhongtang",
            "Xulaoliji;Fuzhongjiaotong;Miansewuhua", ""); }

    @Test @Order(403) @DisplayName("黄芪芍药桂枝苦酒汤证")
    void t_huangqishaoyaoguizhikujiutang() { assertFangzheng("黄芪芍药桂枝苦酒汤证", "Shuiqibing",
            "Huangqishaoyaoguizhikujiutangzheng", "Huangqishaoyaoguizhikujiutang",
            "Huanghan;Shentizhong;Farehanchuerke;Hanzhanyi;Sezhenghuangrubaizhi", "Chenmai"); }

    @Test @Order(404) @DisplayName("防己黄芪汤证")
    void t_fangjihuangqitang() { assertFangzheng("防己黄芪汤证", "Shibing",
            "Fangjihuangqitangzheng", "Fangjihuangqitang",
            "Shenzhong;Hanchu;Efeng", "Fumai"); }

    // ============ 栝楼/薤白类 (441~447) ============

    @Test @Order(441) @DisplayName("栝蒌桂枝汤证")
    void t_gualouguizhitang() { assertFangzheng("栝蒌桂枝汤证", "Jingbing",
            "Gualouguizhitangzheng", "Gualouguizhitang",
            "Shentiqiangjiji;Fare;Hanchu;Efeng", "Chenchimai"); }

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

    @Test @Order(443) @DisplayName("瓜蒌牡蛎散证")
    void t_gualoumulisan() { assertFangzheng("瓜蒌牡蛎散证", "Baihebing",
            "Gualoumulisanzheng", "Gualoumulisan", "Kouke", "Weishumai"); }

    @Test @Order(444) @DisplayName("栝蒌薤白白酒汤证")
    void t_gualouxiebaibaijiutang() { assertFangzheng("栝蒌薤白白酒汤证", "Xiongbibing",
            "Gualouxiebaibaijiutangzheng", "Gualouxiebaibaijiutang",
            "Xiongbi;Chuanxi;Ketuo;Xiongbeitong;Duanqi", "Chenchimai"); }

    @Test @Order(445) @DisplayName("栝蒌薤白半夏汤证")
    void t_gualouxiebaibanxiatang() { assertFangzheng("栝蒌薤白半夏汤证", "Xiongbibing",
            "Gualouxiebaibanxiatangzheng", "Gualouxiebaibanxiatang",
            "Xiongbibudewo;Xintongchebei", "Chenjinmai"); }

    @Test @Order(446) @DisplayName("枳实薤白桂枝汤证")
    void t_zhishixiebaiguizhitang() { assertFangzheng("枳实薤白桂枝汤证", "Xiongbibing",
            "Zhishixiebaiguizhitangzheng", "Zhishixiebaiguizhitang",
            "Xiongbixinzhongpi;Xiongman;Xiexianiqiangxin", "Chenjinmai"); }

    @Test @Order(447) @DisplayName("枳术汤证")
    void t_zhishutang() { assertFangzheng("枳术汤证", "Shuiqibing",
            "Zhishutangzheng", "Zhishutang",
            "Xinxiajian;Darupan;Bianruxuanpan", "Chenximai"); }

    // ============ 百合类 (501~506) ============

    @Test @Order(501) @DisplayName("百合地黄汤证")
    void t_baihedihuangtang() { assertFangzheng("百合地黄汤证", "Baihebing",
            "Baihedihuangtangzheng", "Baihedihuangtang",
            "Kouku;Xiaobianchi", "Weishumai"); }

    @Test @Order(502) @DisplayName("百合知母汤证")
    void t_baihezhimutang() { assertFangzheng("百合知母汤证", "Baihebing",
            "Baihezhimutangzheng", "Baihezhimutang",
            "Kouku;Xiaobianchi;Xinzhongfan", "Weishumai"); }

    @Test @Order(503) @DisplayName("滑石代赭汤证")
    void t_huashidaizhetang() { assertFangzheng("滑石代赭汤证", "Baihebing",
            "Huashidaizhetangzheng", "Huashidaizhetang",
            "Kouku;Xiaobianchi;Xiaobianbuli;Xinzhongfan", "Weishumai"); }

    @Test @Order(504) @DisplayName("百合鸡子汤证")
    void t_baihejizitang() { assertFangzheng("百合鸡子汤证", "Baihebing",
            "Baihejizitangzheng", "Baihejizitang",
            "Kouku;Xiaobianchi;Xinzhongfan", "Weishumai"); }

    @Test @Order(505) @DisplayName("百合洗方证")
    void t_baihexifang() { assertFangzheng("百合洗方证", "Baihebing",
            "Baihexifangzheng", "Baihexifang", "Kouke", "Weishumai"); }

    @Test @Order(506) @DisplayName("百合滑石散证")
    void t_baihehuashisan() { assertFangzheng("百合滑石散证", "Baihebing",
            "Baihehuashisanzheng", "Baihehuashisan", "Fare", "Weishumai"); }

    // ============ 当归类 (541~545) ============

    @Test @Order(541) @DisplayName("当归芍药散证")
    void t_dangguishaoyaosan() { assertFangzheng("当归芍药散证", "Renshengbing",
            "Dangguishaoyaosanzheng", "Dangguishaoyaosan",
            "Furenhuaishen;Fuzhongjiaotong", "Xianmai"); }

    @Test @Order(542) @DisplayName("当归散证")
    void t_dangguisan() { assertFangzheng("当归散证", "Renshengbing",
            "Dangguisanzheng", "Dangguisan",
            "Renshen;Yichangfu", "Xumai"); }

    @Test @Order(543) @DisplayName("当归贝母苦参丸证")
    void t_dangguibeimukushenwan() { assertFangzheng("当归贝母苦参丸证", "Renshengbing",
            "Dangguibeimukushenwanzheng", "Dangguibeimukushenwan",
            "Renshenxiaobiannan;Yinshirugu", "Xumai"); }

    @Test @Order(544) @DisplayName("当归生姜羊肉汤证")
    void t_dangguishengjiangyangroutang() { assertFangzheng("当归生姜羊肉汤证", "Hanshanbing",
            "Dangguishengjiangyangroutangzheng", "Dangguishengjiangyangroutang",
            "Hanshanfutong;Xietongliji", "Xianjimai"); }

    @Test @Order(545) @DisplayName("内补当归建中汤证")
    void t_neibudangguijianzhongtang() { assertFangzheng("内补当归建中汤证", "Furenchanhoubing",
            "Neibudangguijianzhongtangzheng", "Neibudangguijianzhongtang",
            "Fuzhongcitong;Xixishaoqi;Shaofujimotong;Yinyaobeitong;Bunengyinshi", ""); }

    // ============ 金匮杂病 (701~746) ============

    @Test @Order(701) @DisplayName("酸枣仁汤证")
    void t_suanzaorentang() { assertFangzheng("酸枣仁汤证", "Xulaobing",
            "Suanzaorentangzheng", "Suanzaorentang",
            "Xulaoxufanbudemian", "Xianximai"); }

    @Test @Order(702) @DisplayName("薯蓣丸证")
    void t_shuyuwan() { assertFangzheng("薯蓣丸证", "Xulaobing",
            "Shuyuwanzheng", "Shuyuwan", "Xulao;Fengqibaiji", "Xuruomai"); }

    @Test @Order(703) @DisplayName("肾气丸证")
    void t_shenqiwan() { assertFangzheng("肾气丸证", "Xulaobing",
            "Shenqiwanzheng", "Shenqiwan",
            "Xulaoyaotong;Shaofujuji;Xiaobianbuli", "Chenruomai"); }

    @Test @Order(704) @DisplayName("天雄散证")
    void t_tianxiongsan() { assertFangzheng("天雄散证", "Xulaobing",
            "Tianxiongsanzheng", "Tianxiongsan",
            "Shijing;Yaoxilengtong", ""); }

    @Test @Order(705) @DisplayName("小建中汤证")
    void t_xiaojianzhongtang() { assertFangzheng("小建中汤证", "Taiyinbing",
            "Xiaojianzhongtangzheng", "Xiaojianzhongtang",
            "Fuzhongjiaotong;Xinji;Fan", "Xiansemai"); }

    @Test @Order(706) @DisplayName("大建中汤证")
    void t_dajianzhongtang() { assertFangzheng("大建中汤证", "Taiyinbing",
            "Dajianzhongtangzheng", "Dajianzhongtang",
            "Xinxiongdahantong;Oubunengyinshi;Fuzhonghan", "Chenxianmai"); }

    @Test @Order(707) @DisplayName("炙甘草汤证")
    void t_zhigancaotang() { assertFangzheng("炙甘草汤证", "Taiyinbing",
            "Zhigancaotangzheng", "Zhigancaotang", "Xinji", "Jiedaimai"); }

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

    @Test @Order(710) @DisplayName("甘麦大枣汤证")
    void t_ganmaidazaotang() { assertFangzheng("甘麦大枣汤证", "Furenzabing",
            "Ganmaidazaotangzheng", "Ganmaidazaotang",
            "Furenzangzao;Xibeishangyuku;Xiangrushenlingsuozuo;Shuqianshen", "Xumai"); }

    @Test @Order(711) @DisplayName("温经汤证")
    void t_wenjingtang() { assertFangzheng("温经汤证", "Furenzabing",
            "Wenjingtangzheng", "Wenjingtang",
            "Furennianwushisuo;Bingxialishushiribuzhi;Mujifare;Shaofuliji;" +
                    "Fuman;Shouzhangfanre;Chunkouganzao", "Xumai"); }

    @Test @Order(712) @DisplayName("土瓜根散证")
    void t_tuguagensan() { assertFangzheng("土瓜根散证", "Furenzabing",
            "Tuguagensanzheng", "Tuguagensan",
            "Daixia;Jingshuibuli;Shaofumantong;Jingyiyuezaijian", "Chenxianmai"); }

    @Test @Order(713) @DisplayName("蛇床子散证")
    void t_shechuangzisan() { assertFangzheng("蛇床子散证", "Furenzabing",
            "Shechuangzisanzheng", "Shechuangzisan", "Furenyinhan", "Chenximai"); }

    @Test @Order(714) @DisplayName("狼牙汤证")
    void t_langyatantang() { assertFangzheng("狼牙汤证", "Furenzabing",
            "Langyatangzheng", "Langyatang",
            "Shaoyinmaihuaershu;Yinzhongjishengchuang;Yinzhongshichuanglanzhe", "Huashumai"); }

    @Test @Order(715) @DisplayName("矾石丸证")
    void t_fanshiwan() { assertFangzheng("矾石丸证", "Furenzabing",
            "Fanshiwanzheng", "Fanshiwan",
            "Jingshuibibuli;Zangjianpibuzhi;Xiabaiwu", ""); }

    @Test @Order(716) @DisplayName("红蓝花酒证")
    void t_honglanhuajiu() { assertFangzheng("红蓝花酒证", "Furenzabing",
            "Honglanhuajiuzheng", "Honglanhuajiu", "Fuzhongxueqicitong", ""); }

    @Test @Order(717) @DisplayName("胶艾汤证")
    void t_jiaoaitang() { assertFangzheng("胶艾汤证", "Renshengbing",
            "Jiaoaitangzheng", "Jiaoaitang",
            "Furenlouxia;Banchanhouxiaxuebuduan;Renshenxiaxue", "Xumai"); }

    @Test @Order(718) @DisplayName("当归芍药散证（复测）")
    void t_dangguishaoyaosan_retest() { assertFangzheng("当归芍药散证", "Renshengbing",
            "Dangguishaoyaosanzheng", "Dangguishaoyaosan",
            "Furenhuaishen;Fuzhongjiaotong", "Xianmai"); }

    @Test @Order(719) @DisplayName("白术散证")
    void t_baizhusan() { assertFangzheng("白术散证", "Renshengbing",
            "Baizhusanzheng", "Baizhusan", "Renshenyangtai", "Xumai"); }

    @Test @Order(720) @DisplayName("竹叶汤证")
    void t_zhuyetang() { assertFangzheng("竹叶汤证", "Chanhoubing",
            "Zhuyetangzheng", "Zhuyetang",
            "Chanhouzhongfeng;Fare;Mianzhengchi;Chuan;Toutong", "Fumai"); }

    @Test @Order(721) @DisplayName("竹皮大丸证")
    void t_zhupidawan() { assertFangzheng("竹皮大丸证", "Chanhoubing",
            "Zhupidawanzheng", "Zhupidawan",
            "Furenruzhongxu;Fanluannouni", "Xumai"); }

    @Test @Order(722) @DisplayName("三物黄芩汤证")
    void t_sanwuhuangqintang() { assertFangzheng("三物黄芩汤证", "Furenchanhoubing",
            "Sanwuhuangqintangzheng", "Sanwuhuangqintang",
            "Toutong;Sizhikufanre;Butong", ""); }

    @Test @Order(723) @DisplayName("王不留行散证")
    void t_wangbuliuxingsan() { assertFangzheng("王不留行散证", "Jinchuangbing",
            "Wangbuliuxingsanzheng", "Wangbuliuxingsan", "Jinchuang", "Fumai"); }

    @Test @Order(724) @DisplayName("排脓散证")
    void t_painongsan() { assertFangzheng("排脓散证", "Jinchuangbing",
            "Painongsanzheng", "Painongsan", "Jinchuang", "Fumai"); }

    @Test @Order(725) @DisplayName("排脓汤证")
    void t_painongtang() { assertFangzheng("排脓汤证", "Jinchuangbing",
            "Painongtangzheng", "Painongtang", "Jinchuang", "Fumai"); }

    @Test @Order(726) @DisplayName("黄连粉证")
    void t_huanglianfen() { assertFangzheng("黄连粉证", "Chuangyongchangyongjinyinbing",
            "Huanglianfenzheng", "Huanglianfen", "Jinyinchung", ""); }

    @Test @Order(727) @DisplayName("鸡屎白散证")
    void t_jishibaisan() { assertFangzheng("鸡屎白散证", "Zhuanjinbing",
            "Jishibaisanzheng", "Jishibaisan",
            "Zhuanjin;Renbijiaozhi;Maishangxiuxing", "Weixianmai"); }

    @Test @Order(728) @DisplayName("蜘蛛散证")
    void t_zhizhusan() { assertFangzheng("蜘蛛散证", "Yinhushanbing",
            "Zhizhusanzheng", "Zhizhusan",
            "Yinhushan;Pianyouxiaoda;Shishishangxia", "Xianmai"); }

    @Test @Order(729) @DisplayName("甘草粉蜜汤证")
    void t_gancaofenmitang() { assertFangzheng("甘草粉蜜汤证", "Huichongbing",
            "Gancaofenmitangzheng", "Gancaofenmitang",
            "Huichong;Tuxian;Xintongfazuoyoushi", "Xianmai"); }

    @Test @Order(730) @DisplayName("奔豚汤证")
    void t_bentuntang() { assertFangzheng("奔豚汤证", "Bentunbing",
            "Bentuntangzheng", "Bentuntang",
            "Bentunqishangchongxiong;Futong;Wanglaihanre", "Xianmai"); }

    @Test @Order(731) @DisplayName("旋覆花汤证")
    void t_xuanfuhuatang() { assertFangzheng("旋覆花汤证", "Ganzhuobing",
            "Xuanfuhuatangzheng", "Xuanfuhuatang",
            "Ganzhe;Changyudaoqixiongshang;Danyuyinre", "Xianmai"); }

    @Test @Order(732) @DisplayName("皂荚丸证")
    void t_zaojiawan() { assertFangzheng("皂荚丸证", "Feiweifeiyongkesoushangqi",
            "Zaojiawanzheng", "Zaojiawan", "Kenishangqi;Shishituzhuo", ""); }

    @Test @Order(733) @DisplayName("千金苇茎汤证")
    void t_qianjinweijingtang() { assertFangzheng("千金苇茎汤证", "Feiyongbing",
            "Qianjinweijingtangzheng", "Qianjinweijingtang",
            "Keyouweire;Fanman;Xiongzhongjiacuo", ""); }

    @Test @Order(734) @DisplayName("葶苈大枣泻肺汤证")
    void t_tinglidazaoxiefeitang() { assertFangzheng("葶苈大枣泻肺汤证", "Feiyongbing",
            "Tinglidazaoxiefeitangzheng", "Tinglidazaoxiefeitang",
            "Feiyong;Chuanbudewo;Xiongmanzhang", "Xumai"); }

    @Test @Order(735) @DisplayName("麦门冬汤证")
    void t_maimendongtang() { assertFangzheng("麦门冬汤证", "Kesoushangqibing",
            "Maimendongtangzheng", "Maimendongtang",
            "Huonishangqi;Yanhoubuli", "Xumai"); }

    @Test @Order(736) @DisplayName("泽漆汤证")
    void t_zeqitang() { assertFangzheng("泽漆汤证", "Kesoushangqibing",
            "Zeqitangzheng", "Zeqitang", "Kesou;Chuan", "Chenmai"); }

    @Test @Order(737) @DisplayName("人参汤证（胸痹）")
    void t_renshentang() { assertFangzheng("人参汤证（胸痹）", "Xiongbibing",
            "Renshentangzheng", "Renshentang",
            "Xiongbi;Xinzhongpiqi;Xiongman;Xiexianiqiangxin", "Chenximai"); }

    @Test @Order(738) @DisplayName("大建中汤证（复测）")
    void t_dajianzhongtang_retest() { assertFangzheng("大建中汤证", "Taiyinbing",
            "Dajianzhongtangzheng", "Dajianzhongtang",
            "Xinxiongdahantong;Oubunengyinshi;Fuzhonghan", "Chenxianmai"); }

    @Test @Order(739) @DisplayName("侯氏黑散证")
    void t_houshiheisan() { assertFangzheng("侯氏黑散证", "Zhongfengbing",
            "Houshiheisanzheng", "Houshiheisan",
            "Dafeng;Sizhifanzhong;Xinzhongehanbuzu", "Fumai"); }

    @Test @Order(740) @DisplayName("风引汤证")
    void t_fengyintang() { assertFangzheng("风引汤证", "Zhongfengbing",
            "Fengyintangzheng", "Fengyintang", "Churetanxian", "Shumai"); }

    @Test @Order(741) @DisplayName("防己地黄汤证")
    void t_fangjidihuangtang() { assertFangzheng("防己地黄汤证", "Zhongfengbing",
            "Fangjidihuangtangzheng", "Fangjidihuangtang",
            "Rukuangzhuang;Wangxing;Duyubuxiu;Wuhanre", "Fumai"); }

    @Test @Order(742) @DisplayName("头风摩散证")
    void t_toufengmosan() { assertFangzheng("头风摩散证", "Zhongfengbing",
            "Toufengmosanzheng", "Toufengmosan", "Toufeng", ""); }

    @Test @Order(743) @DisplayName("矾石汤证")
    void t_fanshitang() { assertFangzheng("矾石汤证", "Zhongfengbing",
            "Fanshitangzheng", "Fanshitang", "Jiaoqichongxin", ""); }

    @Test @Order(744) @DisplayName("续命汤证")
    void t_xumingtang() { assertFangzheng("续命汤证", "Zhongfengbing",
            "Xumingtangzheng", "Xumingtang",
            "Shentibunengzishouchi;Koujinbunengyan", ""); }

    @Test @Order(745) @DisplayName("三黄汤证")
    void t_sanhuangtang() { assertFangzheng("三黄汤证", "Zhongfengbing",
            "Sanhuangtangzheng", "Sanhuangtang",
            "Shouzujuji;Baijietengtong", ""); }

    @Test @Order(746) @DisplayName("术附汤证")
    void t_shufutang() { assertFangzheng("术附汤证", "Zhongfengbing",
            "Zhufutangzheng", "Zhufutang", "Touzhongxuan;Kujizhiyuandi", ""); }

    @Test @Order(747) @DisplayName("桂枝茯苓丸证")
    void t_guizhifulingwan() { assertFangzheng("桂枝茯苓丸证", "Renshengbing",
            "Guizhifulingwanzheng", "Guizhifulingwan",
            "Furensuyouzhengbing;Jingduanweijisanyue;LouxiaBuzhi;Taidongzaiqishang",
            "Chenxianmai"); }
}