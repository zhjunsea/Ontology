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

    

    // ============ 半夏类 (361~368) ============

    @Test @Order(361) @DisplayName("小半夏汤证")
    void t_xiaobanxiatang() { assertFangzheng("小半夏汤证", "Tanyinbing",
            "Xiaobanxiatangzheng", "Xiaobanxiatang",
            "Oujiabenke;Fanbuke;Xinxiayouzhiyin", "Xianmai"); }

    @Test @Order(362) @DisplayName("小半夏加茯苓汤证")
    void t_xiaobanxiajiafulingtang() { assertFangzheng("小半夏加茯苓汤证", "Tanyinbing",
            "Xiaobanxiajiafulingtangzheng", "Xiaobanxiajiafulingtang",
            "Outu;Xinxiapi;Gejianyoushui", "Xianmai"); }

    @Test @Order(363) @DisplayName("大半夏汤证")
    void t_dabanxiatang() { assertFangzheng("大半夏汤证", "Outuoyuexialibing",
            "Dabanxiatangzheng", "Dabanxiatang", "Chaoshimutu", ""); }

    @Test @Order(364) @DisplayName("半夏厚朴汤证")
    void t_banxiahoupotang() { assertFangzheng("半夏厚朴汤证", "Furenzabing",
            "Banxiahoupotangzheng", "Banxiahoupotang",
            "Yanzhongruyouzhiluan", "Xianmai"); }

    @Test @Order(365) @DisplayName("半夏干姜散证")
    void t_banxiaganjiangsan() { assertFangzheng("半夏干姜散证", "Outuoyuexialibing",
            "Banxiaganjiangsanzheng", "Banxiaganjiangsan",
            "Ganou;Tuxianmo", "Xianmai"); }

    @Test @Order(366) @DisplayName("干姜人参半夏丸证")
    void t_ganjiangrenshenbanxiawan() { assertFangzheng("干姜人参半夏丸证", "Renshengbing",
            "Ganjiangrenshenbanxiawanzheng", "Ganjiangrenshenbanxiawan",
            "Renshenoutubuzhi", "Xumai"); }

    @Test @Order(368) @DisplayName("生姜半夏汤证")
    void t_shengjiangbanxiatang() { assertFangzheng("生姜半夏汤证", "Outuoyuexialibing",
            "Shengjiangbanxiatangzheng", "Shengjiangbanxiatang",
            "Xiongzhongkui", "Xianmai"); }

    @Test @Order(444) @DisplayName("栝蒌薤白白酒汤证")
    void t_gualouxiebaibaijiutang() { assertFangzheng("栝蒌薤白白酒汤证", "Xiongbibing",
            "Gualouxiebaibaijiutangzheng", "Gualouxiebaibaijiutang",
            "Xiongbi;Chuan;Duanqi", "Chenchimai"); }

    @Test @Order(445) @DisplayName("栝蒌薤白半夏汤证")
    void t_gualouxiebaibanxiatang() { assertFangzheng("栝蒌薤白半夏汤证", "Xiongbibing",
            "Gualouxiebaibanxiatangzheng", "Gualouxiebaibanxiantang",
            "Xiongbi;Budewo;Xintongchebei", "Chenjinmai"); }

    @Test @Order(446) @DisplayName("枳实薤白桂枝汤证")
    void t_zhishixiebaiguizhitang() { assertFangzheng("枳实薤白桂枝汤证", "Xiongbibing",
            "Zhishixiebaiguizhitangzheng", "Zhishixiebaiguizhitang",
            "Xiongbi;Xinzhongpi;Xiongman;Xiexianiqiangxin", "Chenjinmai"); }

    @Test @Order(447) @DisplayName("枳术汤证")
    void t_zhishutang() { assertFangzheng("枳术汤证", "Taiyinbing",
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
            "Kouku;Xiaobianchi;Xinfan", "Weishumai"); }

    @Test @Order(503) @DisplayName("滑石代赭汤证")
    void t_huashidaizhetang() { assertFangzheng("滑石代赭汤证", "Baihebing",
            "Huashidaizhetangzheng", "Huashidaizhetang",
            "Kouku;Xiaobianchi;Xiaobianbuli", "Weishumai"); }

    @Test @Order(504) @DisplayName("百合鸡子汤证")
    void t_baihejizitang() { assertFangzheng("百合鸡子汤证", "Baihebing",
            "Baihejizitangzheng", "Baihejizitang",
            "Kouku;Xiaobianchi;Xiongzhongfan", "Weishumai"); }

    @Test @Order(505) @DisplayName("百合洗方证")
    void t_baihexifang() { assertFangzheng("百合洗方证", "Baihebing",
            "Baihexifangzheng", "Baihexifang", "Kouke", "Weishumai"); }

    @Test @Order(506) @DisplayName("百合滑石散证")
    void t_baihehuashisan() { assertFangzheng("百合滑石散证", "Baihebing",
            "Baihehuashisanzheng", "Baihehuashisan", "Fare;Kouku;Xiaobianchi", "Weishumai"); }

    // ============ 当归类 (541~545) ============

    @Test @Order(541) @DisplayName("当归芍药散证")
    void t_dangguishaoyaosan() { assertFangzheng("当归芍药散证", "Renshengbing",
            "Dangguishaoyaosanzheng", "Dangguishaoyaosan",
            "Renshen;Fuzhongjiaotong", "Xianmai"); }

    @Test @Order(542) @DisplayName("当归散证")
    void t_dangguisan() { assertFangzheng("当归散证", "Renshengbing",
            "Dangguisanzheng", "Dangguisan",
            "Renshen;Xiaobianhuang", "Xumai"); }

    @Test @Order(543) @DisplayName("当归贝母苦参丸证")
    void t_dangguibeimukushenwan() { assertFangzheng("当归贝母苦参丸证", "Renshengbing",
            "Dangguibeimukushenwanzheng", "Dangguibeimukushenwan",
            "Xiaobiannan;Yinshirugu", "Xumai"); }

    // ============ 金匮杂病 (701~746) ============

    @Test @Order(701) @DisplayName("酸枣仁汤证")
    void t_suanzaorentang() { assertFangzheng("酸枣仁汤证", "Xulaobing",
            "Suanzaorentangzheng", "Suanzaorentang",
            "Xufan;Budemian", "Xianximai"); }

    @Test @Order(703) @DisplayName("肾气丸证")
    void t_shenqiwan() { assertFangzheng("肾气丸证", "Xulaobing",
            "Shenqiwanzheng", "Shenqiwan",
            "Yaotong;Shaofujuji;Xiaobianbuli", "Chenruomai"); }

    @Test @Order(707) @DisplayName("炙甘草汤证")
    void t_zhigancaotang() { assertFangzheng("炙甘草汤证", "Taiyinbing",
            "Zhigancaotangzheng", "Zhigancaotang", "Xinji", "Jiedaimai"); }

    @Test @Order(710) @DisplayName("甘麦大枣汤证")
    void t_ganmaidazaotang() { assertFangzheng("甘麦大枣汤证", "Furenzabing",
            "Ganmaidazaotangzheng", "Ganmaidazaotang",
            "Furenzangzao;Xibeishangyuku;Xiangrushenlingsuozuo;Shuqianshen", "Xumai"); }

    @Test @Order(711) @DisplayName("温经汤证")
    void t_wenjingtang() { assertFangzheng("温经汤证", "Jueyinbing",
            "Wenjingtangzheng", "Wenjingtang",
            "Mujifare;Shaofuliji;Fuman;Shouzhangfanre;Chunkouganzao", "Xumai"); }

    @Test @Order(712) @DisplayName("土瓜根散证")
    void t_tuguagensan() { assertFangzheng("土瓜根散证", "Furenzabing",
            "Tuguagensanzheng", "Tuguagensan",
            "Daixia;Jingshuibuli;Shaofumantong", "Chenxianmai"); }

    @Test @Order(715) @DisplayName("矾石丸证")
    void t_fanshiwan() { assertFangzheng("矾石丸证", "Furenzabing",
            "Fanshiwanzheng", "Fanshiwan",
            "Jingshuibuli;Xiabaiwu", ""); }

    @Test @Order(716) @DisplayName("红蓝花酒证")
    void t_honglanhuajiu() { assertFangzheng("红蓝花酒证", "Furenzabing",
            "Honglanhuajiuzheng", "Honglanhuajiu", "Futong;Citong", ""); }

    @Test @Order(717) @DisplayName("胶艾汤证")
    void t_jiaoaitang() { assertFangzheng("胶艾汤证", "Renshengbing",
            "Jiaoaitangzheng", "Jiaoaitang",
            "Louxia;Banchanhouxiaxuebuduan;Renshenxiaxue", "Xumai"); }

    @Test @Order(718) @DisplayName("当归芍药散证（复测）")
    void t_dangguishaoyaosan_retest() { assertFangzheng("当归芍药散证", "Renshengbing",
            "Dangguishaoyaosanzheng", "Dangguishaoyaosan",
            "Renshen;Fuzhongjiaotong", "Xianmai"); }

    @Test @Order(719) @DisplayName("白术散证")
    void t_baizhusan() { assertFangzheng("白术散证", "Renshengbing",
            "Baizhusanzheng", "Baizhusan", "Renshen;Dabiantang", "Xumai"); }

    @Test @Order(721) @DisplayName("竹皮大丸证")
    void t_zhupidawan() { assertFangzheng("竹皮大丸证", "Chanhoubing",
            "Zhupidawanzheng", "Zhupidawan",
            "Fanluan;Ouni", "Xumai"); }

    @Test @Order(722) @DisplayName("三物黄芩汤证")
    void t_sanwuhuangqintang() { assertFangzheng("三物黄芩汤证", "Yangmingbing",
            "Sanwuhuangqintangzheng", "Sanwuhuangqintang",
            "Toutong;Sizhikufanre", ""); }

    @Test @Order(723) @DisplayName("王不留行散证")
    void t_wangbuliuxingsan() { assertFangzheng("王不留行散证", "Jinchuangbing",
            "Wangbuliuxingsanzheng", "Wangbuliuxingsan", "Jinchuang", "Fumai"); }

    @Test @Order(724) @DisplayName("排脓散证")
    void t_painongsan() { assertFangzheng("排脓散证", "Jinchuangbing",
            "Painongsanzheng", "Painongsan", "Jinchuang;Futong", "Fumai"); }

    @Test @Order(725) @DisplayName("排脓汤证")
    void t_painongtang() { assertFangzheng("排脓汤证", "Jinchuangbing",
            "Painongtangzheng", "Painongtang", "Jinchuang;Yantong", "Fumai"); }

    @Test @Order(726) @DisplayName("黄连粉证")
    void t_huanglianfen() { assertFangzheng("黄连粉证", "Yangmingbing",
            "Huanglianfenzheng", "Huanglianfen", "Jinyinchuang", ""); }

    @Test @Order(727) @DisplayName("鸡屎白散证")
    void t_jishibaisan() { assertFangzheng("鸡屎白散证", "Yangmingbing",
            "Jishibaisanzheng", "Jishibaisan",
            "Zhuanjin;Renbijiaozhi", "Weixianmai"); }

    @Test @Order(729) @DisplayName("甘草粉蜜汤证")
    void t_gancaofenmitang() { assertFangzheng("甘草粉蜜汤证", "Yangmingbing",
            "Gancaofenmitangzheng", "Gancaofenmitang",
            "Huichong;Tuxian;Xintongfazuoyoushi", "Xianmai"); }

    @Test @Order(737) @DisplayName("人参汤证（胸痹）")
    void t_renshentang() { assertFangzheng("人参汤证（胸痹）", "Xiongbibing",
            "Renshentangzheng", "Lizhongtang",
            "Xiongbi;Xinzhongpiqi;Xiongman;Xiexianiqiangxin", "Chenximai"); }

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
    void t_toufengmosan() { assertFangzheng("头风摩散证", "Taiyangbing",
            "Toufengmosanzheng", "Toufengmosan", "Toutong;Efeng", ""); }

    @Test @Order(743) @DisplayName("矾石汤证")
    void t_fanshitang() { assertFangzheng("矾石汤证", "Yangmingbing",
            "Fanshitangzheng", "Fanshitang", "Jiaoqichongxin", ""); }

    @Test @Order(745) @DisplayName("三黄汤证")
    void t_sanhuangtang() { assertFangzheng("三黄汤证", "Zhongfengbing",
            "Sanhuangtangzheng", "Sanhuangtang",
            "Shouzujuji;Baijietengtong", ""); }

    @Test @Order(747) @DisplayName("桂枝茯苓丸证")
    void t_guizhifulingwan() { assertFangzheng("桂枝茯苓丸证", "Taiyangbing",
            "Guizhifulingwanzheng", "Guizhifulingwan",
            "Furensuyouzhengbing;Jingduanweijisanyue;Louxia;Taidongzaiqishang",
            "Chenxianmai"); }

    // ============ 从少阳阳明方证迁移过来的方证 (748) ============

    @Test @Order(748) @DisplayName("厚朴生姜半夏甘草人参汤证")
    void t_houpoushengjiangbanxiagancaorenshentang() { assertFangzheng(
            "厚朴生姜半夏甘草人参汤证", "Taiyinbing",
            "Houposhengjiangbanxiagancaorenshentangzheng",
            "Houposhengjiangbanxiagancaorenshentang",
            "Fuman;Anzhibutong", "Fumai"); }

    // ============ 其他伤寒方 (581~590) ============

    @Test @Order(581) @DisplayName("旋覆代赭汤证")
    void t_xuanfudaizhetang() { assertFangzheng("旋覆代赭汤证", "Taiyinbing",
            "Xuanfudaizhetangzheng", "Xuanfudaizhetang",
            "Xinxiapiying;Yiqi", "Xianmai"); }

    @Test @Order(586) @DisplayName("芍药甘草汤证")
    void t_shaoyaogancaotang() { assertFangzheng("芍药甘草汤证", "Taiyangbing",
            "Shaoyaogancaotangzheng", "Shaoyaogancaotang", "Jiaoluanji", "Xianmai"); }

    @Test @Order(587) @DisplayName("芍药甘草附子汤证")
    void t_shaoyaogancaofuzitang() { assertFangzheng("芍药甘草附子汤证", "Taiyangbing",
            "Shaoyaogancaofuzitangzheng", "Shaoyaogancaofuzitang",
            "Jiaoluanji;Ehan;Hanchu", "Weimai"); }

    @Test @Order(596) @DisplayName("柏叶汤证")
    void t_baiyetang() { assertFangzheng("柏叶汤证", "Taiyinbing",
            "Baiyetangzheng", "Baiyetang", "Tuxuebuzhi", "Xumai"); }

    @Test @Order(597) @DisplayName("黄土汤证")
    void t_huangtutang() { assertFangzheng("黄土汤证", "Xiaxuebing",
            "Huangtutangzheng", "Huangtutang",
            "Xiaxue;Bianxue;Mianseweihuang;Xianbianhoubianxue", "Ximai"); }

    @Test @Order(598) @DisplayName("赤小豆当归散证")
    void t_chixiaodoudangguisan() { assertFangzheng("赤小豆当归散证", "Xiaxuebing",
            "Chixiaodoudangguisanzheng", "Chixiaodoudangguisan",
            "Xiaxue;Bianxue;Xianxuehoubian", "Ximai"); }

    @Test @Order(130) @DisplayName("大黄蛰虫丸证")
    void t_dahuangzhechongwan() { assertFangzheng("大黄蛰虫丸证", "Xulaobing",
            "Dahuangzhechongwanzheng", "Dahuangzhechongwan",
            "Wulaoxuji;Fuman;Bunengyinshi;Jifujiacuo;Liangmuanhei", "Chensemai"); }

    @Test @Order(136) @DisplayName("下瘀血汤证")
    void t_xiayuxuetang() { assertFangzheng("下瘀血汤证", "Yangmingbing",
            "Xiayuxuetangzheng", "Xiayuxuetang",
            "Futong;Shaofujijie;Citong", "Chenxianmai"); }

    @Test @Order(138) @DisplayName("枳实芍药散证")
    void t_zhishishaoyaosan() { assertFangzheng("枳实芍药散证", "Taiyinbing",
            "Zhishishaoyaosanzheng", "Zhishishaoyaosan",
            "Chanhoufutong;Fanman;Budewo;Fuman;Xiali;Buke", "Xianmai"); }

    @Test @Order(167) @DisplayName("泻心汤证")
    void t_xiexintang() { assertFangzheng("泻心汤证", "Yangmingbing",
            "Xiexintangzheng", "Xiexintang",
            "Tuxue;Nvxue;Xinqibuzu", "Hongmai"); }

    @Test @Order(206) @DisplayName("薏苡附子败酱散证")
    void t_yiyifuzibaijiangsan() { assertFangzheng("薏苡附子败酱散证", "Changyongbing",
            "Yiyifuzibaijiangsanzheng", "Yiyifuzibaijiangsan",
            "Changyong;Jifujiacuo;Fupiji;Anzhiruruzhongzhuang;Wujiju;Shenwure", "Shumai"); }

    @Test @Order(259) @DisplayName("葵子茯苓散证")
    void t_kuizifulingsan() { assertFangzheng("葵子茯苓散证", "Renshengbing",
            "Kuizifulingsanzheng", "Kuizifulingsan",
            "Renshenyoushuiqi;Shenzhong;Xiaobianbuli;Sasaehan;Qizetouxuan", "Fumai"); }

    @Test @Order(44) @DisplayName("厚朴麻黄汤证")
    void t_houpoumahuangtang() { assertFangzheng("厚朴麻黄汤证", "Taiyangbing",
            "Houpomahuangtangzheng", "Houpomahuangtang",
            "Kesou;Chuan;Budewo", "Fumai"); }

    @Test @Order(50) @DisplayName("半夏麻黄丸证")
    void t_banxiamahuangwan() { assertFangzheng("半夏麻黄丸证", "Taiyinbing",
            "Banxiamahuangwanzheng", "Banxiamahuangwan", "Xinxiajidong", "Xianmai"); }
}