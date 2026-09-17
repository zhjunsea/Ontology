package com.ocean.ontologyframework.tcm;

import org.junit.jupiter.api.Test;

/**
 * 厥阴方证测试
 * 测试当归四逆汤类、乌头汤类、吴茱萸汤类、白头翁汤类等厥阴系统方证
 */
public class JueyinFangzhengTest extends JingfangTestSupport {

    // ==================== 当归四逆汤类 ====================

    @Test
    public void t_mahuangshengmatang() {
        assertFangzheng("麻黄升麻汤证", "Mahuangshengmatangzheng",
            "Yanhoubuli;Tunongxue;Xialibuzhi;Shouzujueni",
            "Chenchimai",
            "Jueyinbing");
    }

    @Test
    public void t_wuzhuyutang() {
        assertFangzheng("吴茱萸汤证", "Wuzhuyutangzheng",
            "Shiguyuou;Tuli;Shouzunileng;Fanzaoyusi;Ganou;Tuxianmo;Toutong",
            "Chenxianmai",
            "Jueyinbing");
    }

    @Test
    public void t_dangguisinitang() {
        assertFangzheng("当归四逆汤证", "Dangguisinitangzheng",
            "Shouzuleng",
            "Ximai;Weimai",
            "Jueyinbing");
    }

    @Test
    public void t_dangguisnijiawuzhuyushengjiangtang() {
        assertFangzheng("当归四逆加吴茱萸生姜汤证", "Dangguisinijiawuzhuyushengjiangtangzheng",
            "Shouzuleng;Neiyoujiuhan;Ou;Futong",
            "Ximai;Weimai",
            "Jueyinbing");
    }

    // ==================== 白头翁汤类 ====================

    @Test
    public void t_baitouwengtang() {
        assertFangzheng("白头翁汤证", "Baitouwengtangzheng",
            "Xiali;Lijihouchong;Xialinongxue;Kouke",
            "Xuanshumai",
            "Jueyinbing");
    }

    // ==================== 乌头汤类 ====================

    @Test
    public void t_wutoutang() {
        assertFangzheng("乌头汤证", "Wutoutangzheng",
            "Guanjietengtong;Bukequshen",
            "Chenjinmai",
            "Jueyinbing");
    }

    @Test
    public void t_dawutoujian() {
        assertFangzheng("大乌头煎证", "Dawutoujianzheng",
            "Raoqihanshan;Raoqitong;Ruofazebaihanchu;Shouzuleng",
            "Chenjinmai",
            "Hanshanbing");
    }

    @Test
    public void t_chiwan() {
        assertFangzheng("赤丸证", "Chiwanzheng",
            "Hanqijueni",
            "Chenximai",
            "Hanshanbing");
    }

    @Test
    public void t_dangguishengjiangyangroutang() {
        assertFangzheng("当归生姜羊肉汤证", "Dangguishengjiangyangroutangzheng",
            "Hanshanfutong;Xietong;Liji",
            "Jinxianmai",
            "Hanshanbing");
    }
}
