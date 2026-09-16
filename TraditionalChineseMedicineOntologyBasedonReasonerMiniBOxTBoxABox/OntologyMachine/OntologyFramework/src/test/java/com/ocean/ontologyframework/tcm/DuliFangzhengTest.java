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

    @Test @Order(301) @DisplayName("大陷胸汤证")
    void t_daxianxiongtang() { assertFangzheng("大陷胸汤证", "Taiyangbing",
            "Daxianxiongtangzheng", "Daxianxiongtang",
            "Xinxiatong;Anzhishiying", "Chenjinmai"); }

    @Test @Order(302) @DisplayName("大陷胸丸证")
    void t_daxianxiongwan() { assertFangzheng("大陷胸丸证", "Taiyangbing",
            "Daxianxiongwanzheng", "Daxianxiongwan",
            "Jiexiong;Xiangqiang;Ruroujingzhuang", "Chenjinmai"); }

    @Test @Order(303) @DisplayName("小陷胸汤证")
    void t_xiaoxianxiongtang() { assertFangzheng("小陷胸汤证", "Taiyangbing",
            "Xiaoxianxiongtangzheng", "Xiaoxianxiongtang",
            "Zhengzaixinxia;Anzhizhetong", "Fuhuamai"); }

    // ============ 其他伤寒方 (581~590) ============

    @Test @Order(581) @DisplayName("旋覆代赭汤证")
    void t_xuanfudaizhetang() { assertFangzheng("旋覆代赭汤证", "Taiyangbing",
            "Xuanfudaizhetangzheng", "Xuanfudaizhetang",
            "Xinxiapiying;Aqibuchu", "Xianmai"); }

    @Test @Order(582) @DisplayName("十枣汤证")
    void t_shizaotang() { assertFangzheng("十枣汤证", "Taiyangbing",
            "Shizaotangzheng", "Shizaotang",
            "Xinxiapiyingman;Yinxiexiatong;Ganou;Duanqi", "Chenxianmai"); }

    @Test @Order(583) @DisplayName("赤石脂禹余粮汤证")
    void t_chishizhiyuyuliangtang() { assertFangzheng("赤石脂禹余粮汤证", "Taiyangbing",
            "Chishizhiyuyuliangtangzheng", "Chishizhiyuyuliangtang",
            "Xinxiapiying;Xialibuzhi;Fanzhibuyu", "Chenximai"); }

    @Test @Order(584) @DisplayName("瓜蒂散证")
    void t_guadisan() { assertFangzheng("瓜蒂散证", "Taiyangbing",
            "Guadisanzheng", "Guadisan",
            "Xiongzhongpiying;Qishangchonghouyan;Budexi", "Weifumai"); }

    @Test @Order(585) @DisplayName("三物白散证")
    void t_sanwubaisan() { assertFangzheng("三物白散证", "Taiyangbing",
            "Sanwubaisanzheng", "Sanwubaisan",
            "Hanshijiexiong;Wurezheng", "Chenjinmai"); }

    @Test @Order(586) @DisplayName("芍药甘草汤证")
    void t_shaoyaogancaotang() { assertFangzheng("芍药甘草汤证", "Taiyangbing",
            "Shaoyaogancaotangzheng", "Shaoyaogancaotang", "Jiaoluanji", "Xianmai"); }

    @Test @Order(587) @DisplayName("芍药甘草附子汤证")
    void t_shaoyaogancaofuzitang() { assertFangzheng("芍药甘草附子汤证", "Taiyangbing",
            "Shaoyaogancaofuzitangzheng", "Shaoyaogancaofuzitang",
            "Ehan;Hanchu", "Weimai"); }

    @Test @Order(588) @DisplayName("栀子豉汤证（阳明）")
    void t_zhizichitang_yangming() { assertFangzheng("栀子豉汤证", "Yangmingbing",
            "Zhizichitangzheng", "Zhizichitang",
            "Xinzhongaonao;Fanrebudemian;Shaoqi", "Fumai"); }

    @Test @Order(589) @DisplayName("蜜煎导方证")
    void t_mijiandaofang() { assertFangzheng("蜜煎导方证", "Yangmingbing",
            "Mijiandaofangzheng", "Mijiandaofang",
            "Dabianying;Xiaobianzili", ""); }

    @Test @Order(590) @DisplayName("猪胆汁方证")
    void t_zhudanzhifang() { assertFangzheng("猪胆汁方证", "Yangmingbing",
            "Zhudanzhifangzheng", "Zhudanzhifang",
            "Dabianying;Xiaobianzili", ""); }

    // ============ 其他伤寒方 (592~604) ============

    @Test @Order(592) @DisplayName("茵陈五苓散证")
    void t_yinchenwulingsan() { assertFangzheng("茵陈五苓散证", "Huangdanbing",
            "Yinchenwulingsanzheng", "Yinchenwulingsan",
            "Huangdan;Xiaobianbuli", "Fumai"); }

    @Test @Order(593) @DisplayName("一物瓜蒂汤证")
    void t_yiwuguaditang() { assertFangzheng("一物瓜蒂汤证", "Taiyangzhongye",
            "Yiwuguaditangzheng", "Yiwuguaditang",
            "Shenretengzhong;Ehan", "Weimai"); }

    @Test @Order(594) @DisplayName("猪膏发煎证")
    void t_zhugaofajian() { assertFangzheng("猪膏发煎证", "Huangdanbing",
            "Zhugaofajianzheng", "Zhugaofajian", "Zhuhuang", "Fumai"); }

    @Test @Order(595) @DisplayName("硝石矾石散证")
    void t_xiaoshifanshisan() { assertFangzheng("硝石矾石散证", "Huangdanbing",
            "Xiaoshifanshisanzheng", "Xiaoshifanshisan",
            "Nvlaodan;Pangguangji;Shaofuman;Shenjinhuang;Eshanghei;Zuxiare", "Chenximai"); }

    @Test @Order(596) @DisplayName("柏叶汤证")
    void t_baiyetang() { assertFangzheng("柏叶汤证", "Tunvxiaxuebing",
            "Baiyetangzheng", "Baiyetang", "Tuxuebuzhi", "Xumai"); }

    @Test @Order(597) @DisplayName("黄土汤证")
    void t_huangtutang() { assertFangzheng("黄土汤证", "Xiaxuebing",
            "Huangtutangzheng", "Huangtutang",
            "Xiaxue;Xianbianhoubianxue;Mianseweihuang", "Ximai"); }

    @Test @Order(598) @DisplayName("赤小豆当归散证")
    void t_chixiaodoudangguisan() { assertFangzheng("赤小豆当归散证", "Xiaxuebing",
            "Chixiaodoudangguisanzheng", "Chixiaodoudangguisan",
            "Xiaxue;Xianxuehoubian", "Ximai"); }

    @Test @Order(599) @DisplayName("橘皮竹茹汤证")
    void t_jupizhurutang() { assertFangzheng("橘皮竹茹汤证", "Outuoyuexialibing",
            "Jupizhurutangzheng", "Jupizhurutang", "Yueni", "Xumai"); }

    @Test @Order(600) @DisplayName("橘皮汤证")
    void t_jupitang() { assertFangzheng("橘皮汤证", "Outuoyuexialibing",
            "Jupitangzheng", "Jupitang",
            "Ganou;Yueni;Shouzujue", "Xianmai"); }

    @Test @Order(601) @DisplayName("橘枳姜汤证")
    void t_juzhijiangtang() { assertFangzheng("橘枳姜汤证", "Xiongbibing",
            "Juzhijiangtangzheng", "Juzhijiangtang",
            "Xiongzhongqisai;Duanqi", "Chenximai"); }

    @Test @Order(602) @DisplayName("文蛤汤证")
    void t_wengetang() { assertFangzheng("文蛤汤证", "Outuoyuexialibing",
            "Wengetangzheng", "Wengetang",
            "Outu;Kouke;Yinshui;Toutong", "Jinmai"); }

    @Test @Order(603) @DisplayName("紫参汤证")
    void t_zishentang() { assertFangzheng("紫参汤证", "Outuoyuexialibing",
            "Zishentangzheng", "Zishentang", "Xiali;Feitong", "Chenmai"); }

    @Test @Order(604) @DisplayName("诃梨勒散证")
    void t_helilesan() { assertFangzheng("诃梨勒散证", "Outuoyuexialibing",
            "Helilesanzheng", "Helilesan", "Qili", "Chenmai"); }

    // ============ 其他伤寒方 (607~614) ============

    @Test @Order(607) @DisplayName("升麻鳖甲汤证")
    void t_shengmabiejiatang() { assertFangzheng("升麻鳖甲汤证", "Yinyangdu",
            "Shengmabiejiatangzheng", "Shengmabiejiatang",
            "Mianchibanzhang;Yanhoutong;Tunongxue", "Fumai"); }

    @Test @Order(608) @DisplayName("升麻鳖甲去雄黄蜀椒汤证")
    void t_shengmabiejiaquxionghuangshujiaotang() { assertFangzheng(
            "升麻鳖甲去雄黄蜀椒汤证", "Yinyangdu",
            "Shengmabiejiaquxionghuangshujiaotangzheng",
            "Shengmabiejiaquxionghuangshujiaotang",
            "Mianmuqing;Shentongrupiang", ""); }

    @Test @Order(609) @DisplayName("苦参汤证（狐惑）")
    void t_kushentang() { assertFangzheng("苦参汤证（狐惑）", "Huhuobing",
            "Kushentangzheng", "Kushentang", "Yangan;Yinzhongshichuang", ""); }

    @Test @Order(610) @DisplayName("雄黄熏方证")
    void t_xionghuangxunfang() { assertFangzheng("雄黄熏方证", "Huhuobing",
            "Xionghuangxunfangzheng", "Xionghuangxunfang", "Yinzhongshichuang", ""); }

    @Test @Order(611) @DisplayName("鳖甲煎丸证")
    void t_biejiajianwan() { assertFangzheng("鳖甲煎丸证", "Nuebing",
            "Biejiajianwanzheng", "Biejiajianwan",
            "Nuem;Xiexiazhengjia", "Xianmai"); }

    @Test @Order(612) @DisplayName("蜀漆散证")
    void t_shuqisan() { assertFangzheng("蜀漆散证", "Nuebing",
            "Shuqisanzheng", "Shuqisan", "Duohan;Fahan", "Xianmai"); }

    @Test @Order(613) @DisplayName("牡蛎汤证")
    void t_mulitang() { assertFangzheng("牡蛎汤证", "Nuebing",
            "Mulitangzheng", "Mulitang", "Fahan;Duohan", ""); }

    @Test @Order(614) @DisplayName("牡蛎泽泻散证")
    void t_mulizexiesan() { assertFangzheng("牡蛎泽泻散证", "Chanhoubing",
            "Mulizexiesanzheng", "Mulizexiesan",
            "Yaoyixiashuiqi;Xiaobianbuli", "Chenmai"); }
}