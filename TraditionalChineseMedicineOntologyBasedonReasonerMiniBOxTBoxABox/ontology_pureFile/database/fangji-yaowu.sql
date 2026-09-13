-- =============================================
-- 经方数据库 MySQL 脚本
-- 生成依据：药物模块 ABox 与方剂模块 ABox
-- =============================================

CREATE DATABASE IF NOT EXISTS jingfangdb DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE jingfangdb;

-- ---------- 建表 ----------
CREATE TABLE IF NOT EXISTS yaowu (
    id INT AUTO_INCREMENT PRIMARY KEY,
    iri VARCHAR(255) NOT NULL UNIQUE,
    label VARCHAR(255) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS fangji (
    id INT AUTO_INCREMENT PRIMARY KEY,
    iri VARCHAR(255) NOT NULL UNIQUE,
    label VARCHAR(255) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS fangji_yaowu (
    fangji_id INT NOT NULL,
    yaowu_id INT NOT NULL,
    PRIMARY KEY (fangji_id, yaowu_id),
    FOREIGN KEY (fangji_id) REFERENCES fangji(id) ON DELETE CASCADE,
    FOREIGN KEY (yaowu_id) REFERENCES yaowu(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS shibafan (
    yaowu_id INT NOT NULL,
    related_yaowu_id INT NOT NULL,
    PRIMARY KEY (yaowu_id, related_yaowu_id),
    FOREIGN KEY (yaowu_id) REFERENCES yaowu(id) ON DELETE CASCADE,
    FOREIGN KEY (related_yaowu_id) REFERENCES yaowu(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS shijiuwei (
    yaowu_id INT NOT NULL,
    related_yaowu_id INT NOT NULL,
    PRIMARY KEY (yaowu_id, related_yaowu_id),
    FOREIGN KEY (yaowu_id) REFERENCES yaowu(id) ON DELETE CASCADE,
    FOREIGN KEY (related_yaowu_id) REFERENCES yaowu(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------- 插入药物（去重后共125种） ----------
INSERT INTO yaowu (iri, label) VALUES
('Guizhi','桂枝'),('Shaoyao','芍药'),('Gancao','甘草'),('Shengjiang','生姜'),('Dazao','大枣'),
('Mahuang','麻黄'),('Xingren','杏仁'),('Shigao','石膏'),('Zhimu','知母'),('Jingmi','粳米'),
('Dahuang','大黄'),('Mangxiao','芒硝'),('Houpo','厚朴'),('Zhishi','枳实'),('Chaihu','柴胡'),
('Huangqin','黄芩'),('Renshen','人参'),('Banxia','半夏'),('Ganjiang','干姜'),('Baizhu','白术'),
('Fuzi','附子'),('Xixin','细辛'),('Fuling','茯苓'),('Wumei','乌梅'),('Huanglian','黄连'),
('Danggui','当归'),('Shujiao','蜀椒'),('Huangbai','黄柏'),('Gualougen','瓜蒌根'),('Muli','牡蛎'),
('Danshen','丹参'),('Taoren','桃仁'),('Xiangfu','香附'),('Yujin','郁金'),('Huashi','滑石'),
('Shanzha','山楂'),('Shenqu','神曲'),('Mudanpi','牡丹皮'),('Wuweizi','五味子'),('Zhuru','竹茹'),
('Chenpi','陈皮'),('Gegen','葛根'),('Longgu','龙骨'),('Yitang','饴糖'),('Huangqi','黄芪'),
('Zhizi','栀子'),('Xiangchi','香豉'),('Zhuye','竹叶'),('Maimendong','麦门冬'),('Xuanfuhua','旋覆花'),
('Daizheshi','代赭石'),('Zhuling','猪苓'),('Zexie','泽泻'),('Gualoushi','瓜蒌实'),('Xiebai','薤白'),
('Baijiu','白酒'),('Chuanwu','川乌'),('Fangfeng','防风'),('Chuanxiong','川芎'),('Ejiao','阿胶'),
('Aiye','艾叶'),('Dihuang','干地黄'),('Baihe','百合'),('Shengdihuangzhi','生地黄汁'),('Suanzaoren','酸枣仁'),
('Chixiaodou','赤小豆'),('Shegan','射干'),('Ziwan','紫菀'),('Kuandonghua','款冬花'),('Xiaomai','小麦'),
('Zican','紫参'),('Baiqian','白前'),('Zeqi','泽漆'),('Yan','盐'),('Zhechong','䗪虫'),
('Renniao','人尿'),('Zhudanzhi','猪胆汁'),('Congbai','葱白'),('Shuizhi','水蛭'),('Mengchong','虻虫'),
('Qiandan','铅丹'),('Wuzhuyu','吴茱萸'),('Tongcao','通草'),('Baitouweng','白头翁'),('Qinpi','秦皮'),
('Jizihuang','鸡子黄'),('Zaoxintu','灶心黄土'),('Yiyiren','薏苡仁'),('Mi','蜜'),('Jiangshui','浆水'),
('Quanshui','泉水'),('Dongliushui','东流水'),('Baijiangcao','败酱草'),('Kujiu','苦酒'),('Qingjiu','清酒'),
('Baiyin','白饮'),('Wenfen','温粉'),('Rougui','肉桂'),('Longdancao','龙胆草'),('Binlang','槟榔'),
('Xionghuang','雄黄'),('Beimu','贝母'),('Bailian','白蔹'),('Baiji','白及'),('Gansui','甘遂'),
('Daji','大戟'),('Haizao','海藻'),('Yuanhua','芫花'),('Lilu','藜芦'),('Shashen','沙参'),
('Xuanshen','玄参'),('Kushen','苦参'),('Liuhuang','硫黄'),('Pishuang','砒霜'),('Langdu','狼毒'),
('Mituoseng','密陀僧'),('Badou','巴豆'),('Qianniuzi','牵牛子'),('Dingxiang','丁香'),('Sanleng','三棱'),
('Xijiao','犀角'),('Wulingzhi','五灵脂'),('Chishizhi','赤石脂'),('Shuiyin','水银'),('Caowu','草乌'),
('Shanyao','山药'),('Qumai','瞿麦');

-- ---------- 插入方剂（共112首） ----------
INSERT INTO fangji (iri, label) VALUES
('GuizhiTang','桂枝汤'),('MahuangTang','麻黄汤'),('BaihuTang','白虎汤'),('DaChengqiTang','大承气汤'),
('XiaoChaihuTang','小柴胡汤'),('DaChaihuTang','大柴胡汤'),('LizhongTang','理中汤'),('SiniTang','四逆汤'),
('MahuangFuziXixinTang','麻黄附子细辛汤'),('ZhenwuTang','真武汤'),('WumeiWan','乌梅丸'),
('ChaihuGuizhiGanjiangTang','柴胡桂枝干姜汤'),('ChaihuBaihuTang','柴胡白虎汤'),('GuiZhiFuLingWan','桂枝茯苓丸'),
('XiaoQingLongTang','小青龙汤'),('SiNiSan','四逆散'),('WenDanTang','温胆汤'),('BanXiaXieXinTang','半夏泻心汤'),
('GuizhiJiaGegenTang','桂枝加葛根汤'),('GuizhiJiaHoupoXingrenTang','桂枝加厚朴杏子汤'),
('GuizhiJiaFuziTang','桂枝加附子汤'),('GuizhiQushaoyaoTang','桂枝去芍药汤'),
('GuizhiQushaoyaoJiaFuziTang','桂枝去芍药加附子汤'),('GuizhiXinjiaTang','桂枝新加汤'),
('GuizhiGancaoTang','桂枝甘草汤'),('FulingGuizhiBaizhuGancaoTang','茯苓桂枝白术甘草汤'),
('ShaoyaoGancaoTang','芍药甘草汤'),('ShaoyaoGancaoFuziTang','芍药甘草附子汤'),('GuizhiFuziTang','桂枝附子汤'),
('BaizhuFuziTang','白术附子汤'),('GancaoFuziTang','甘草附子汤'),('GuizhiJiaLongguMuliTang','桂枝加龙骨牡蛎汤'),
('HuangqiJianzhongTang','黄芪建中汤'),('XiaojianzhongTang','小建中汤'),('DajianzhongTang','大建中汤'),
('DaqinglongTang','大青龙汤'),('MaxingshiganTang','麻杏石甘汤'),('MahuangFuziGancaoTang','麻黄附子甘草汤'),
('MahuangJiazhuTang','麻黄加术汤'),('MahuangYiyiGancaoTang','麻黄薏苡甘草汤'),('GegenTang','葛根汤'),
('GegenJiaBanxiaTang','葛根加半夏汤'),('GegenHuangqinHuanglianTang','葛根黄芩黄连汤'),
('ChaihuGuizhiTang','柴胡桂枝汤'),('ChaihuJiaMangxiaoTang','柴胡加芒硝汤'),
('ChaihuJiaLongguMuliTang','柴胡加龙骨牡蛎汤'),('BaihuJiaRenshenTang','白虎加人参汤'),
('ZhuyeshigaoTang','竹叶石膏汤'),('XiaochengqiTang','小承气汤'),('TiaoweichengqiTang','调胃承气汤'),
('TaohechengqiTang','桃核承气汤'),('DidangTang','抵当汤'),('FuzilizhongTang','附子理中汤'),
('WuzhuyuTang','吴茱萸汤'),('TongmaisiniTang','通脉四逆汤'),('BaitongTang','白通汤'),
('BaitongJiazhudanzhiTang','白通加猪胆汁汤'),('HuanglianEjiaoTang','黄连阿胶汤'),
('DangguisiniTang','当归四逆汤'),('DangguisiniJiawuzhuyushengjiangTang','当归四逆加吴茱萸生姜汤'),
('BaitouwengTang','白头翁汤'),('GanjianghuangqinhuanglianrenshenTang','干姜黄芩黄连人参汤'),
('ShengjiangxiexinTang','生姜泻心汤'),('GancaoxiexinTang','甘草泻心汤'),
('DahuanghuanglianxiexinTang','大黄黄连泻心汤'),('FuzixiexinTang','附子泻心汤'),
('Wulingsan','五苓散'),('FulinggancaoTang','茯苓甘草汤'),('XuanfudaizheTang','旋覆代赭汤'),
('HouposhengjiangbanxiagancaorenshenTang','厚朴生姜半夏甘草人参汤'),('GuizhirenshenTang','桂枝人参汤'),
('ZhizichiTang','栀子豉汤'),('ZhizigancaoTang','栀子甘草汤'),('ZhizishengjiangTang','栀子生姜汤'),
('ZhizihoupoTang','栀子厚朴汤'),('GualouxiebaibaijiuTang','瓜蒌薤白白酒汤'),
('GualouxiebaibanxiaTang','瓜蒌薤白半夏汤'),('ZhishixiebaiguizhiTang','枳实薤白桂枝汤'),
('HoupoqiwuTang','厚朴七物汤'),('GuizhishaoyaozhimuTang','桂枝芍药知母汤'),('WutouTang','乌头汤'),
('WenjingTang','温经汤'),('JiaoaiTang','胶艾汤'),('Dangguishaoyaosan','当归芍药散'),
('BaihedihuangTang','百合地黄汤'),('SuanzaorenTang','酸枣仁汤'),('HuangtuTang','黄土汤'),
('Chixiaodoudangguisan','赤小豆当归散'),('MaimendongTang','麦门冬汤'),('SheganmahuangTang','射干麻黄汤'),
('HoupomahuangTang','厚朴麻黄汤'),('ZeqiTang','泽漆汤'),('Tianwutouchishimisan','头风摩散'),
('XiayuxueTang','下瘀血汤'),('HouposanwuTang','厚朴三物汤'),('XijiaodihuangTang','犀角地黄汤'),
('GansuiBanxiaTang','甘遂半夏汤'),('FuziJingmiTang','附子粳米汤'),('GualouQumaiWan','栝楼瞿麦丸'),
('AngongniuhuangWan','安宫牛黄丸'),('ZixueDan','紫雪丹'),('ZhibaoDan','至宝丹');

-- ---------- 插入方剂-药物组成关系（完整） ----------
-- 为节省篇幅，以下使用一个辅助存储过程思路，但MySQL不支持一次插入多行子查询，因此逐条列出。
-- 每个关系一行，使用“INSERT INTO ... SELECT ... WHERE”模式。

-- 桂枝汤
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='GuizhiTang' AND y.iri='Guizhi';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='GuizhiTang' AND y.iri='Shaoyao';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='GuizhiTang' AND y.iri='Gancao';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='GuizhiTang' AND y.iri='Shengjiang';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='GuizhiTang' AND y.iri='Dazao';
-- 麻黄汤
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='MahuangTang' AND y.iri='Mahuang';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='MahuangTang' AND y.iri='Guizhi';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='MahuangTang' AND y.iri='Xingren';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='MahuangTang' AND y.iri='Gancao';
-- 白虎汤
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='BaihuTang' AND y.iri='Shigao';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='BaihuTang' AND y.iri='Zhimu';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='BaihuTang' AND y.iri='Gancao';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='BaihuTang' AND y.iri='Jingmi';
-- 大承气汤
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='DaChengqiTang' AND y.iri='Dahuang';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='DaChengqiTang' AND y.iri='Mangxiao';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='DaChengqiTang' AND y.iri='Houpo';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='DaChengqiTang' AND y.iri='Zhishi';
-- 小柴胡汤
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='XiaoChaihuTang' AND y.iri='Chaihu';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='XiaoChaihuTang' AND y.iri='Huangqin';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='XiaoChaihuTang' AND y.iri='Renshen';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='XiaoChaihuTang' AND y.iri='Banxia';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='XiaoChaihuTang' AND y.iri='Gancao';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='XiaoChaihuTang' AND y.iri='Shengjiang';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='XiaoChaihuTang' AND y.iri='Dazao';
-- 大柴胡汤
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='DaChaihuTang' AND y.iri='Chaihu';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='DaChaihuTang' AND y.iri='Huangqin';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='DaChaihuTang' AND y.iri='Shaoyao';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='DaChaihuTang' AND y.iri='Banxia';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='DaChaihuTang' AND y.iri='Shengjiang';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='DaChaihuTang' AND y.iri='Zhishi';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='DaChaihuTang' AND y.iri='Dazao';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='DaChaihuTang' AND y.iri='Dahuang';
-- 理中汤
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='LizhongTang' AND y.iri='Renshen';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='LizhongTang' AND y.iri='Ganjiang';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='LizhongTang' AND y.iri='Baizhu';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='LizhongTang' AND y.iri='Gancao';
-- 四逆汤
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='SiniTang' AND y.iri='Fuzi';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='SiniTang' AND y.iri='Ganjiang';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='SiniTang' AND y.iri='Gancao';
-- 麻黄附子细辛汤
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='MahuangFuziXixinTang' AND y.iri='Mahuang';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='MahuangFuziXixinTang' AND y.iri='Fuzi';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='MahuangFuziXixinTang' AND y.iri='Xixin';
-- 真武汤
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='ZhenwuTang' AND y.iri='Fuzi';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='ZhenwuTang' AND y.iri='Baizhu';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='ZhenwuTang' AND y.iri='Fuling';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='ZhenwuTang' AND y.iri='Shengjiang';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='ZhenwuTang' AND y.iri='Shaoyao';
-- 乌梅丸
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='WumeiWan' AND y.iri='Wumei';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='WumeiWan' AND y.iri='Xixin';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='WumeiWan' AND y.iri='Ganjiang';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='WumeiWan' AND y.iri='Huanglian';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='WumeiWan' AND y.iri='Danggui';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='WumeiWan' AND y.iri='Fuzi';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='WumeiWan' AND y.iri='Shujiao';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='WumeiWan' AND y.iri='Guizhi';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='WumeiWan' AND y.iri='Renshen';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='WumeiWan' AND y.iri='Huangbai';
-- 柴胡桂枝干姜汤
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='ChaihuGuizhiGanjiangTang' AND y.iri='Chaihu';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='ChaihuGuizhiGanjiangTang' AND y.iri='Guizhi';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='ChaihuGuizhiGanjiangTang' AND y.iri='Ganjiang';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='ChaihuGuizhiGanjiangTang' AND y.iri='Gualougen';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='ChaihuGuizhiGanjiangTang' AND y.iri='Huangqin';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='ChaihuGuizhiGanjiangTang' AND y.iri='Muli';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='ChaihuGuizhiGanjiangTang' AND y.iri='Gancao';
-- 柴胡白虎汤
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='ChaihuBaihuTang' AND y.iri='Chaihu';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='ChaihuBaihuTang' AND y.iri='Huangqin';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='ChaihuBaihuTang' AND y.iri='Renshen';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='ChaihuBaihuTang' AND y.iri='Banxia';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='ChaihuBaihuTang' AND y.iri='Gancao';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='ChaihuBaihuTang' AND y.iri='Shengjiang';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='ChaihuBaihuTang' AND y.iri='Dazao';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='ChaihuBaihuTang' AND y.iri='Shigao';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='ChaihuBaihuTang' AND y.iri='Zhimu';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='ChaihuBaihuTang' AND y.iri='Jingmi';
-- 桂枝茯苓丸
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='GuiZhiFuLingWan' AND y.iri='Guizhi';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='GuiZhiFuLingWan' AND y.iri='Fuling';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='GuiZhiFuLingWan' AND y.iri='Mudanpi';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='GuiZhiFuLingWan' AND y.iri='Taoren';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='GuiZhiFuLingWan' AND y.iri='Shaoyao';
-- 小青龙汤
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='XiaoQingLongTang' AND y.iri='Mahuang';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='XiaoQingLongTang' AND y.iri='Shaoyao';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='XiaoQingLongTang' AND y.iri='Xixin';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='XiaoQingLongTang' AND y.iri='Ganjiang';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='XiaoQingLongTang' AND y.iri='Gancao';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='XiaoQingLongTang' AND y.iri='Guizhi';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='XiaoQingLongTang' AND y.iri='Wuweizi';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='XiaoQingLongTang' AND y.iri='Banxia';
-- 四逆散
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='SiNiSan' AND y.iri='Gancao';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='SiNiSan' AND y.iri='Zhishi';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='SiNiSan' AND y.iri='Chaihu';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='SiNiSan' AND y.iri='Shaoyao';
-- 温胆汤
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='WenDanTang' AND y.iri='Banxia';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='WenDanTang' AND y.iri='Zhuru';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='WenDanTang' AND y.iri='Zhishi';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='WenDanTang' AND y.iri='Chenpi';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='WenDanTang' AND y.iri='Gancao';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='WenDanTang' AND y.iri='Fuling';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='WenDanTang' AND y.iri='Shengjiang';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='WenDanTang' AND y.iri='Dazao';
-- 半夏泻心汤
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='BanXiaXieXinTang' AND y.iri='Banxia';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='BanXiaXieXinTang' AND y.iri='Huangqin';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='BanXiaXieXinTang' AND y.iri='Ganjiang';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='BanXiaXieXinTang' AND y.iri='Renshen';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='BanXiaXieXinTang' AND y.iri='Huanglian';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='BanXiaXieXinTang' AND y.iri='Dazao';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='BanXiaXieXinTang' AND y.iri='Gancao';
-- 桂枝加葛根汤
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='GuizhiJiaGegenTang' AND y.iri='Gegen';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='GuizhiJiaGegenTang' AND y.iri='Guizhi';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='GuizhiJiaGegenTang' AND y.iri='Shaoyao';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='GuizhiJiaGegenTang' AND y.iri='Gancao';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='GuizhiJiaGegenTang' AND y.iri='Shengjiang';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='GuizhiJiaGegenTang' AND y.iri='Dazao';
-- 桂枝加厚朴杏子汤
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='GuizhiJiaHoupoXingrenTang' AND y.iri='Guizhi';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='GuizhiJiaHoupoXingrenTang' AND y.iri='Shaoyao';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='GuizhiJiaHoupoXingrenTang' AND y.iri='Gancao';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='GuizhiJiaHoupoXingrenTang' AND y.iri='Shengjiang';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='GuizhiJiaHoupoXingrenTang' AND y.iri='Dazao';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='GuizhiJiaHoupoXingrenTang' AND y.iri='Houpo';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='GuizhiJiaHoupoXingrenTang' AND y.iri='Xingren';
-- 桂枝加附子汤
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='GuizhiJiaFuziTang' AND y.iri='Guizhi';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='GuizhiJiaFuziTang' AND y.iri='Shaoyao';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='GuizhiJiaFuziTang' AND y.iri='Gancao';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='GuizhiJiaFuziTang' AND y.iri='Shengjiang';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='GuizhiJiaFuziTang' AND y.iri='Dazao';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='GuizhiJiaFuziTang' AND y.iri='Fuzi';
-- 桂枝去芍药汤
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='GuizhiQushaoyaoTang' AND y.iri='Guizhi';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='GuizhiQushaoyaoTang' AND y.iri='Gancao';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='GuizhiQushaoyaoTang' AND y.iri='Shengjiang';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='GuizhiQushaoyaoTang' AND y.iri='Dazao';
-- 桂枝去芍药加附子汤
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='GuizhiQushaoyaoJiaFuziTang' AND y.iri='Guizhi';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='GuizhiQushaoyaoJiaFuziTang' AND y.iri='Gancao';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='GuizhiQushaoyaoJiaFuziTang' AND y.iri='Shengjiang';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='GuizhiQushaoyaoJiaFuziTang' AND y.iri='Dazao';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='GuizhiQushaoyaoJiaFuziTang' AND y.iri='Fuzi';
-- 桂枝新加汤
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='GuizhiXinjiaTang' AND y.iri='Guizhi';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='GuizhiXinjiaTang' AND y.iri='Shaoyao';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='GuizhiXinjiaTang' AND y.iri='Gancao';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='GuizhiXinjiaTang' AND y.iri='Renshen';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='GuizhiXinjiaTang' AND y.iri='Dazao';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='GuizhiXinjiaTang' AND y.iri='Shengjiang';
-- 桂枝甘草汤
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='GuizhiGancaoTang' AND y.iri='Guizhi';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='GuizhiGancaoTang' AND y.iri='Gancao';
-- 茯苓桂枝白术甘草汤
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='FulingGuizhiBaizhuGancaoTang' AND y.iri='Fuling';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='FulingGuizhiBaizhuGancaoTang' AND y.iri='Guizhi';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='FulingGuizhiBaizhuGancaoTang' AND y.iri='Baizhu';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='FulingGuizhiBaizhuGancaoTang' AND y.iri='Gancao';
-- 芍药甘草汤
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='ShaoyaoGancaoTang' AND y.iri='Shaoyao';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='ShaoyaoGancaoTang' AND y.iri='Gancao';
-- 芍药甘草附子汤
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='ShaoyaoGancaoFuziTang' AND y.iri='Shaoyao';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='ShaoyaoGancaoFuziTang' AND y.iri='Gancao';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='ShaoyaoGancaoFuziTang' AND y.iri='Fuzi';
-- 桂枝附子汤
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='GuizhiFuziTang' AND y.iri='Guizhi';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='GuizhiFuziTang' AND y.iri='Fuzi';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='GuizhiFuziTang' AND y.iri='Shengjiang';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='GuizhiFuziTang' AND y.iri='Dazao';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='GuizhiFuziTang' AND y.iri='Gancao';
-- 白术附子汤
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='BaizhuFuziTang' AND y.iri='Fuzi';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='BaizhuFuziTang' AND y.iri='Baizhu';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='BaizhuFuziTang' AND y.iri='Shengjiang';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='BaizhuFuziTang' AND y.iri='Dazao';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='BaizhuFuziTang' AND y.iri='Gancao';
-- 甘草附子汤
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='GancaoFuziTang' AND y.iri='Gancao';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='GancaoFuziTang' AND y.iri='Fuzi';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='GancaoFuziTang' AND y.iri='Baizhu';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='GancaoFuziTang' AND y.iri='Guizhi';
-- 桂枝加龙骨牡蛎汤
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='GuizhiJiaLongguMuliTang' AND y.iri='Guizhi';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='GuizhiJiaLongguMuliTang' AND y.iri='Shaoyao';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='GuizhiJiaLongguMuliTang' AND y.iri='Shengjiang';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='GuizhiJiaLongguMuliTang' AND y.iri='Gancao';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='GuizhiJiaLongguMuliTang' AND y.iri='Dazao';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='GuizhiJiaLongguMuliTang' AND y.iri='Longgu';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='GuizhiJiaLongguMuliTang' AND y.iri='Muli';
-- 黄芪建中汤
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='HuangqiJianzhongTang' AND y.iri='Guizhi';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='HuangqiJianzhongTang' AND y.iri='Gancao';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='HuangqiJianzhongTang' AND y.iri='Shaoyao';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='HuangqiJianzhongTang' AND y.iri='Shengjiang';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='HuangqiJianzhongTang' AND y.iri='Dazao';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='HuangqiJianzhongTang' AND y.iri='Yitang';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='HuangqiJianzhongTang' AND y.iri='Huangqi';
-- 小建中汤
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='XiaojianzhongTang' AND y.iri='Guizhi';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='XiaojianzhongTang' AND y.iri='Gancao';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='XiaojianzhongTang' AND y.iri='Shaoyao';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='XiaojianzhongTang' AND y.iri='Shengjiang';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='XiaojianzhongTang' AND y.iri='Dazao';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='XiaojianzhongTang' AND y.iri='Yitang';
-- 大建中汤
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='DajianzhongTang' AND y.iri='Shujiao';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='DajianzhongTang' AND y.iri='Ganjiang';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='DajianzhongTang' AND y.iri='Renshen';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='DajianzhongTang' AND y.iri='Yitang';
-- 大青龙汤
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='DaqinglongTang' AND y.iri='Mahuang';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='DaqinglongTang' AND y.iri='Guizhi';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='DaqinglongTang' AND y.iri='Gancao';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='DaqinglongTang' AND y.iri='Xingren';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='DaqinglongTang' AND y.iri='Shengjiang';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='DaqinglongTang' AND y.iri='Dazao';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='DaqinglongTang' AND y.iri='Shigao';
-- 麻杏石甘汤
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='MaxingshiganTang' AND y.iri='Mahuang';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='MaxingshiganTang' AND y.iri='Xingren';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='MaxingshiganTang' AND y.iri='Gancao';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='MaxingshiganTang' AND y.iri='Shigao';
-- 麻黄附子甘草汤
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='MahuangFuziGancaoTang' AND y.iri='Mahuang';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='MahuangFuziGancaoTang' AND y.iri='Gancao';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='MahuangFuziGancaoTang' AND y.iri='Fuzi';
-- 麻黄加术汤
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='MahuangJiazhuTang' AND y.iri='Mahuang';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='MahuangJiazhuTang' AND y.iri='Guizhi';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='MahuangJiazhuTang' AND y.iri='Gancao';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='MahuangJiazhuTang' AND y.iri='Xingren';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='MahuangJiazhuTang' AND y.iri='Baizhu';
-- 麻黄薏苡甘草汤
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='MahuangYiyiGancaoTang' AND y.iri='Mahuang';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='MahuangYiyiGancaoTang' AND y.iri='Gancao';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='MahuangYiyiGancaoTang' AND y.iri='Yiyiren';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='MahuangYiyiGancaoTang' AND y.iri='Xingren';
-- 葛根汤
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='GegenTang' AND y.iri='Gegen';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='GegenTang' AND y.iri='Mahuang';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='GegenTang' AND y.iri='Guizhi';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='GegenTang' AND y.iri='Shengjiang';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='GegenTang' AND y.iri='Gancao';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='GegenTang' AND y.iri='Shaoyao';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='GegenTang' AND y.iri='Dazao';
-- 葛根加半夏汤
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='GegenJiaBanxiaTang' AND y.iri='Gegen';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='GegenJiaBanxiaTang' AND y.iri='Mahuang';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='GegenJiaBanxiaTang' AND y.iri='Guizhi';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='GegenJiaBanxiaTang' AND y.iri='Shengjiang';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='GegenJiaBanxiaTang' AND y.iri='Gancao';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='GegenJiaBanxiaTang' AND y.iri='Shaoyao';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='GegenJiaBanxiaTang' AND y.iri='Dazao';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='GegenJiaBanxiaTang' AND y.iri='Banxia';
-- 葛根黄芩黄连汤
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='GegenHuangqinHuanglianTang' AND y.iri='Gegen';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='GegenHuangqinHuanglianTang' AND y.iri='Gancao';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='GegenHuangqinHuanglianTang' AND y.iri='Huangqin';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='GegenHuangqinHuanglianTang' AND y.iri='Huanglian';
-- 柴胡桂枝汤
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='ChaihuGuizhiTang' AND y.iri='Guizhi';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='ChaihuGuizhiTang' AND y.iri='Shaoyao';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='ChaihuGuizhiTang' AND y.iri='Huangqin';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='ChaihuGuizhiTang' AND y.iri='Renshen';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='ChaihuGuizhiTang' AND y.iri='Gancao';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='ChaihuGuizhiTang' AND y.iri='Banxia';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='ChaihuGuizhiTang' AND y.iri='Dazao';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='ChaihuGuizhiTang' AND y.iri='Shengjiang';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='ChaihuGuizhiTang' AND y.iri='Chaihu';
-- 柴胡加芒硝汤
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='ChaihuJiaMangxiaoTang' AND y.iri='Chaihu';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='ChaihuJiaMangxiaoTang' AND y.iri='Huangqin';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='ChaihuJiaMangxiaoTang' AND y.iri='Renshen';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='ChaihuJiaMangxiaoTang' AND y.iri='Gancao';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='ChaihuJiaMangxiaoTang' AND y.iri='Shengjiang';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='ChaihuJiaMangxiaoTang' AND y.iri='Banxia';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='ChaihuJiaMangxiaoTang' AND y.iri='Dazao';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='ChaihuJiaMangxiaoTang' AND y.iri='Mangxiao';
-- 柴胡加龙骨牡蛎汤
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='ChaihuJiaLongguMuliTang' AND y.iri='Chaihu';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='ChaihuJiaLongguMuliTang' AND y.iri='Longgu';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='ChaihuJiaLongguMuliTang' AND y.iri='Huangqin';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='ChaihuJiaLongguMuliTang' AND y.iri='Shengjiang';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='ChaihuJiaLongguMuliTang' AND y.iri='Renshen';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='ChaihuJiaLongguMuliTang' AND y.iri='Guizhi';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='ChaihuJiaLongguMuliTang' AND y.iri='Fuling';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='ChaihuJiaLongguMuliTang' AND y.iri='Banxia';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='ChaihuJiaLongguMuliTang' AND y.iri='Dahuang';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='ChaihuJiaLongguMuliTang' AND y.iri='Muli';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='ChaihuJiaLongguMuliTang' AND y.iri='Dazao';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='ChaihuJiaLongguMuliTang' AND y.iri='Qiandan';
-- 白虎加人参汤
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='BaihuJiaRenshenTang' AND y.iri='Zhimu';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='BaihuJiaRenshenTang' AND y.iri='Shigao';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='BaihuJiaRenshenTang' AND y.iri='Gancao';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='BaihuJiaRenshenTang' AND y.iri='Jingmi';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='BaihuJiaRenshenTang' AND y.iri='Renshen';
-- 竹叶石膏汤
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='ZhuyeshigaoTang' AND y.iri='Zhuye';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='ZhuyeshigaoTang' AND y.iri='Shigao';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='ZhuyeshigaoTang' AND y.iri='Banxia';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='ZhuyeshigaoTang' AND y.iri='Maimendong';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='ZhuyeshigaoTang' AND y.iri='Renshen';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='ZhuyeshigaoTang' AND y.iri='Gancao';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='ZhuyeshigaoTang' AND y.iri='Jingmi';
-- 小承气汤
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='XiaochengqiTang' AND y.iri='Dahuang';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='XiaochengqiTang' AND y.iri='Houpo';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='XiaochengqiTang' AND y.iri='Zhishi';
-- 调胃承气汤
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='TiaoweichengqiTang' AND y.iri='Dahuang';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='TiaoweichengqiTang' AND y.iri='Gancao';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='TiaoweichengqiTang' AND y.iri='Mangxiao';
-- 桃核承气汤
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='TaohechengqiTang' AND y.iri='Taoren';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='TaohechengqiTang' AND y.iri='Dahuang';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='TaohechengqiTang' AND y.iri='Guizhi';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='TaohechengqiTang' AND y.iri='Gancao';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='TaohechengqiTang' AND y.iri='Mangxiao';
-- 抵当汤
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='DidangTang' AND y.iri='Shuizhi';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='DidangTang' AND y.iri='Mengchong';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='DidangTang' AND y.iri='Taoren';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='DidangTang' AND y.iri='Dahuang';
-- 附子理中汤
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='FuzilizhongTang' AND y.iri='Renshen';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='FuzilizhongTang' AND y.iri='Ganjiang';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='FuzilizhongTang' AND y.iri='Gancao';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='FuzilizhongTang' AND y.iri='Baizhu';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='FuzilizhongTang' AND y.iri='Fuzi';
-- 吴茱萸汤
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='WuzhuyuTang' AND y.iri='Wuzhuyu';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='WuzhuyuTang' AND y.iri='Renshen';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='WuzhuyuTang' AND y.iri='Shengjiang';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='WuzhuyuTang' AND y.iri='Dazao';
-- 通脉四逆汤
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='TongmaisiniTang' AND y.iri='Gancao';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='TongmaisiniTang' AND y.iri='Fuzi';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='TongmaisiniTang' AND y.iri='Ganjiang';
-- 白通汤
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='BaitongTang' AND y.iri='Congbai';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='BaitongTang' AND y.iri='Ganjiang';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='BaitongTang' AND y.iri='Fuzi';
-- 白通加猪胆汁汤
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='BaitongJiazhudanzhiTang' AND y.iri='Congbai';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='BaitongJiazhudanzhiTang' AND y.iri='Ganjiang';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='BaitongJiazhudanzhiTang' AND y.iri='Fuzi';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='BaitongJiazhudanzhiTang' AND y.iri='Renniao';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='BaitongJiazhudanzhiTang' AND y.iri='Zhudanzhi';
-- 黄连阿胶汤
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='HuanglianEjiaoTang' AND y.iri='Huanglian';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='HuanglianEjiaoTang' AND y.iri='Huangqin';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='HuanglianEjiaoTang' AND y.iri='Shaoyao';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='HuanglianEjiaoTang' AND y.iri='Jizihuang';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='HuanglianEjiaoTang' AND y.iri='Ejiao';
-- 当归四逆汤
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='DangguisiniTang' AND y.iri='Danggui';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='DangguisiniTang' AND y.iri='Guizhi';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='DangguisiniTang' AND y.iri='Shaoyao';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='DangguisiniTang' AND y.iri='Xixin';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='DangguisiniTang' AND y.iri='Gancao';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='DangguisiniTang' AND y.iri='Tongcao';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='DangguisiniTang' AND y.iri='Dazao';
-- 当归四逆加吴茱萸生姜汤
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='DangguisiniJiawuzhuyushengjiangTang' AND y.iri='Danggui';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='DangguisiniJiawuzhuyushengjiangTang' AND y.iri='Guizhi';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='DangguisiniJiawuzhuyushengjiangTang' AND y.iri='Shaoyao';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='DangguisiniJiawuzhuyushengjiangTang' AND y.iri='Xixin';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='DangguisiniJiawuzhuyushengjiangTang' AND y.iri='Gancao';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='DangguisiniJiawuzhuyushengjiangTang' AND y.iri='Tongcao';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='DangguisiniJiawuzhuyushengjiangTang' AND y.iri='Dazao';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='DangguisiniJiawuzhuyushengjiangTang' AND y.iri='Wuzhuyu';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='DangguisiniJiawuzhuyushengjiangTang' AND y.iri='Shengjiang';
-- 白头翁汤
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='BaitouwengTang' AND y.iri='Baitouweng';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='BaitouwengTang' AND y.iri='Huangbai';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='BaitouwengTang' AND y.iri='Huanglian';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='BaitouwengTang' AND y.iri='Qinpi';
-- 干姜黄芩黄连人参汤
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='GanjianghuangqinhuanglianrenshenTang' AND y.iri='Ganjiang';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='GanjianghuangqinhuanglianrenshenTang' AND y.iri='Huangqin';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='GanjianghuangqinhuanglianrenshenTang' AND y.iri='Huanglian';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='GanjianghuangqinhuanglianrenshenTang' AND y.iri='Renshen';
-- 生姜泻心汤
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='ShengjiangxiexinTang' AND y.iri='Shengjiang';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='ShengjiangxiexinTang' AND y.iri='Gancao';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='ShengjiangxiexinTang' AND y.iri='Renshen';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='ShengjiangxiexinTang' AND y.iri='Ganjiang';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='ShengjiangxiexinTang' AND y.iri='Huangqin';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='ShengjiangxiexinTang' AND y.iri='Banxia';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='ShengjiangxiexinTang' AND y.iri='Huanglian';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='ShengjiangxiexinTang' AND y.iri='Dazao';
-- 甘草泻心汤
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='GancaoxiexinTang' AND y.iri='Gancao';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='GancaoxiexinTang' AND y.iri='Huangqin';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='GancaoxiexinTang' AND y.iri='Ganjiang';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='GancaoxiexinTang' AND y.iri='Banxia';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='GancaoxiexinTang' AND y.iri='Huanglian';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='GancaoxiexinTang' AND y.iri='Dazao';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='GancaoxiexinTang' AND y.iri='Renshen';
-- 大黄黄连泻心汤
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='DahuanghuanglianxiexinTang' AND y.iri='Dahuang';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='DahuanghuanglianxiexinTang' AND y.iri='Huanglian';
-- 附子泻心汤
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='FuzixiexinTang' AND y.iri='Dahuang';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='FuzixiexinTang' AND y.iri='Huanglian';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='FuzixiexinTang' AND y.iri='Huangqin';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='FuzixiexinTang' AND y.iri='Fuzi';
-- 五苓散
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Wulingsan' AND y.iri='Zhuling';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Wulingsan' AND y.iri='Zexie';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Wulingsan' AND y.iri='Baizhu';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Wulingsan' AND y.iri='Fuling';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Wulingsan' AND y.iri='Guizhi';
-- 茯苓甘草汤
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='FulinggancaoTang' AND y.iri='Fuling';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='FulinggancaoTang' AND y.iri='Guizhi';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='FulinggancaoTang' AND y.iri='Gancao';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='FulinggancaoTang' AND y.iri='Shengjiang';
-- 旋覆代赭汤
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='XuanfudaizheTang' AND y.iri='Xuanfuhua';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='XuanfudaizheTang' AND y.iri='Renshen';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='XuanfudaizheTang' AND y.iri='Shengjiang';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='XuanfudaizheTang' AND y.iri='Daizheshi';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='XuanfudaizheTang' AND y.iri='Gancao';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='XuanfudaizheTang' AND y.iri='Banxia';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='XuanfudaizheTang' AND y.iri='Dazao';
-- 厚朴生姜半夏甘草人参汤
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='HouposhengjiangbanxiagancaorenshenTang' AND y.iri='Houpo';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='HouposhengjiangbanxiagancaorenshenTang' AND y.iri='Shengjiang';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='HouposhengjiangbanxiagancaorenshenTang' AND y.iri='Banxia';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='HouposhengjiangbanxiagancaorenshenTang' AND y.iri='Gancao';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='HouposhengjiangbanxiagancaorenshenTang' AND y.iri='Renshen';
-- 桂枝人参汤
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='GuizhirenshenTang' AND y.iri='Guizhi';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='GuizhirenshenTang' AND y.iri='Gancao';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='GuizhirenshenTang' AND y.iri='Baizhu';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='GuizhirenshenTang' AND y.iri='Renshen';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='GuizhirenshenTang' AND y.iri='Ganjiang';
-- 栀子豉汤
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='ZhizichiTang' AND y.iri='Zhizi';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='ZhizichiTang' AND y.iri='Xiangchi';
-- 栀子甘草汤
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='ZhizigancaoTang' AND y.iri='Zhizi';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='ZhizigancaoTang' AND y.iri='Xiangchi';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='ZhizigancaoTang' AND y.iri='Gancao';
-- 栀子生姜汤
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='ZhizishengjiangTang' AND y.iri='Zhizi';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='ZhizishengjiangTang' AND y.iri='Xiangchi';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='ZhizishengjiangTang' AND y.iri='Shengjiang';
-- 栀子厚朴汤
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='ZhizihoupoTang' AND y.iri='Zhizi';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='ZhizihoupoTang' AND y.iri='Houpo';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='ZhizihoupoTang' AND y.iri='Zhishi';
-- 瓜蒌薤白白酒汤
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='GualouxiebaibaijiuTang' AND y.iri='Gualoushi';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='GualouxiebaibaijiuTang' AND y.iri='Xiebai';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='GualouxiebaibaijiuTang' AND y.iri='Baijiu';
-- 瓜蒌薤白半夏汤
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='GualouxiebaibanxiaTang' AND y.iri='Gualoushi';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='GualouxiebaibanxiaTang' AND y.iri='Xiebai';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='GualouxiebaibanxiaTang' AND y.iri='Banxia';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='GualouxiebaibanxiaTang' AND y.iri='Baijiu';
-- 枳实薤白桂枝汤
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='ZhishixiebaiguizhiTang' AND y.iri='Zhishi';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='ZhishixiebaiguizhiTang' AND y.iri='Houpo';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='ZhishixiebaiguizhiTang' AND y.iri='Xiebai';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='ZhishixiebaiguizhiTang' AND y.iri='Guizhi';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='ZhishixiebaiguizhiTang' AND y.iri='Gualoushi';
-- 厚朴七物汤
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='HoupoqiwuTang' AND y.iri='Houpo';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='HoupoqiwuTang' AND y.iri='Gancao';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='HoupoqiwuTang' AND y.iri='Dahuang';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='HoupoqiwuTang' AND y.iri='Dazao';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='HoupoqiwuTang' AND y.iri='Zhishi';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='HoupoqiwuTang' AND y.iri='Guizhi';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='HoupoqiwuTang' AND y.iri='Shengjiang';
-- 桂枝芍药知母汤
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='GuizhishaoyaozhimuTang' AND y.iri='Guizhi';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='GuizhishaoyaozhimuTang' AND y.iri='Shaoyao';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='GuizhishaoyaozhimuTang' AND y.iri='Gancao';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='GuizhishaoyaozhimuTang' AND y.iri='Mahuang';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='GuizhishaoyaozhimuTang' AND y.iri='Shengjiang';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='GuizhishaoyaozhimuTang' AND y.iri='Baizhu';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='GuizhishaoyaozhimuTang' AND y.iri='Zhimu';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='GuizhishaoyaozhimuTang' AND y.iri='Fangfeng';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='GuizhishaoyaozhimuTang' AND y.iri='Fuzi';
-- 乌头汤
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='WutouTang' AND y.iri='Chuanwu';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='WutouTang' AND y.iri='Mahuang';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='WutouTang' AND y.iri='Shaoyao';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='WutouTang' AND y.iri='Huangqi';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='WutouTang' AND y.iri='Gancao';
-- 温经汤
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='WenjingTang' AND y.iri='Wuzhuyu';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='WenjingTang' AND y.iri='Danggui';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='WenjingTang' AND y.iri='Chuanxiong';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='WenjingTang' AND y.iri='Shaoyao';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='WenjingTang' AND y.iri='Renshen';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='WenjingTang' AND y.iri='Guizhi';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='WenjingTang' AND y.iri='Ejiao';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='WenjingTang' AND y.iri='Mudanpi';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='WenjingTang' AND y.iri='Shengjiang';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='WenjingTang' AND y.iri='Gancao';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='WenjingTang' AND y.iri='Banxia';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='WenjingTang' AND y.iri='Maimendong';
-- 胶艾汤
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='JiaoaiTang' AND y.iri='Chuanxiong';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='JiaoaiTang' AND y.iri='Ejiao';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='JiaoaiTang' AND y.iri='Gancao';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='JiaoaiTang' AND y.iri='Aiye';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='JiaoaiTang' AND y.iri='Danggui';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='JiaoaiTang' AND y.iri='Shaoyao';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='JiaoaiTang' AND y.iri='Dihuang';
-- 当归芍药散
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Dangguishaoyaosan' AND y.iri='Danggui';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Dangguishaoyaosan' AND y.iri='Shaoyao';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Dangguishaoyaosan' AND y.iri='Chuanxiong';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Dangguishaoyaosan' AND y.iri='Fuling';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Dangguishaoyaosan' AND y.iri='Baizhu';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Dangguishaoyaosan' AND y.iri='Zexie';
-- 百合地黄汤
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='BaihedihuangTang' AND y.iri='Baihe';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='BaihedihuangTang' AND y.iri='Shengdihuangzhi';
-- 酸枣仁汤
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='SuanzaorenTang' AND y.iri='Suanzaoren';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='SuanzaorenTang' AND y.iri='Gancao';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='SuanzaorenTang' AND y.iri='Zhimu';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='SuanzaorenTang' AND y.iri='Fuling';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='SuanzaorenTang' AND y.iri='Chuanxiong';
-- 黄土汤
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='HuangtuTang' AND y.iri='Gancao';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='HuangtuTang' AND y.iri='Dihuang';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='HuangtuTang' AND y.iri='Baizhu';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='HuangtuTang' AND y.iri='Fuzi';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='HuangtuTang' AND y.iri='Ejiao';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='HuangtuTang' AND y.iri='Huangqin';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='HuangtuTang' AND y.iri='Zaoxintu';
-- 赤小豆当归散
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Chixiaodoudangguisan' AND y.iri='Chixiaodou';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Chixiaodoudangguisan' AND y.iri='Danggui';
-- 麦门冬汤
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='MaimendongTang' AND y.iri='Maimendong';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='MaimendongTang' AND y.iri='Banxia';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='MaimendongTang' AND y.iri='Renshen';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='MaimendongTang' AND y.iri='Gancao';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='MaimendongTang' AND y.iri='Jingmi';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='MaimendongTang' AND y.iri='Dazao';
-- 射干麻黄汤
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='SheganmahuangTang' AND y.iri='Shegan';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='SheganmahuangTang' AND y.iri='Mahuang';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='SheganmahuangTang' AND y.iri='Shengjiang';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='SheganmahuangTang' AND y.iri='Xixin';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='SheganmahuangTang' AND y.iri='Ziwan';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='SheganmahuangTang' AND y.iri='Kuandonghua';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='SheganmahuangTang' AND y.iri='Wuweizi';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='SheganmahuangTang' AND y.iri='Dazao';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='SheganmahuangTang' AND y.iri='Banxia';
-- 厚朴麻黄汤
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='HoupomahuangTang' AND y.iri='Houpo';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='HoupomahuangTang' AND y.iri='Mahuang';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='HoupomahuangTang' AND y.iri='Shigao';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='HoupomahuangTang' AND y.iri='Xingren';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='HoupomahuangTang' AND y.iri='Banxia';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='HoupomahuangTang' AND y.iri='Ganjiang';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='HoupomahuangTang' AND y.iri='Xixin';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='HoupomahuangTang' AND y.iri='Xiaomai';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='HoupomahuangTang' AND y.iri='Wuweizi';
-- 泽漆汤
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='ZeqiTang' AND y.iri='Banxia';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='ZeqiTang' AND y.iri='Zican';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='ZeqiTang' AND y.iri='Shengjiang';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='ZeqiTang' AND y.iri='Baiqian';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='ZeqiTang' AND y.iri='Gancao';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='ZeqiTang' AND y.iri='Huangqin';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='ZeqiTang' AND y.iri='Renshen';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='ZeqiTang' AND y.iri='Guizhi';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='ZeqiTang' AND y.iri='Zeqi';
-- 头风摩散
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Tianwutouchishimisan' AND y.iri='Fuzi';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Tianwutouchishimisan' AND y.iri='Yan';
-- 下瘀血汤
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='XiayuxueTang' AND y.iri='Dahuang';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='XiayuxueTang' AND y.iri='Taoren';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='XiayuxueTang' AND y.iri='Zhechong';
-- 厚朴三物汤
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='HouposanwuTang' AND y.iri='Houpo';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='HouposanwuTang' AND y.iri='Zhishi';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='HouposanwuTang' AND y.iri='Dahuang';
-- 犀角地黄汤
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='XijiaodihuangTang' AND y.iri='Dihuang';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='XijiaodihuangTang' AND y.iri='Shaoyao';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='XijiaodihuangTang' AND y.iri='Mudanpi';
-- 甘遂半夏汤
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='GansuiBanxiaTang' AND y.iri='Gansui';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='GansuiBanxiaTang' AND y.iri='Banxia';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='GansuiBanxiaTang' AND y.iri='Shaoyao';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='GansuiBanxiaTang' AND y.iri='Gancao';
-- 附子粳米汤
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='FuziJingmiTang' AND y.iri='Fuzi';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='FuziJingmiTang' AND y.iri='Banxia';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='FuziJingmiTang' AND y.iri='Jingmi';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='FuziJingmiTang' AND y.iri='Gancao';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='FuziJingmiTang' AND y.iri='Dazao';
-- 栝楼瞿麦丸
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='GualouQumaiWan' AND y.iri='Gualougen';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='GualouQumaiWan' AND y.iri='Fuzi';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='GualouQumaiWan' AND y.iri='Fuling';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='GualouQumaiWan' AND y.iri='Shanyao';
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='GualouQumaiWan' AND y.iri='Qumai';

-- ---------- 插入十八反关系 ----------
-- 附子反半夏、瓜蒌实、瓜蒌根、贝母、白蔹、白及
INSERT INTO shibafan (yaowu_id, related_yaowu_id) SELECT y1.id, y2.id FROM yaowu y1, yaowu y2 WHERE y1.iri='Fuzi' AND y2.iri='Banxia';
INSERT INTO shibafan (yaowu_id, related_yaowu_id) SELECT y1.id, y2.id FROM yaowu y1, yaowu y2 WHERE y1.iri='Fuzi' AND y2.iri='Gualoushi';
INSERT INTO shibafan (yaowu_id, related_yaowu_id) SELECT y1.id, y2.id FROM yaowu y1, yaowu y2 WHERE y1.iri='Fuzi' AND y2.iri='Gualougen';
INSERT INTO shibafan (yaowu_id, related_yaowu_id) SELECT y1.id, y2.id FROM yaowu y1, yaowu y2 WHERE y1.iri='Fuzi' AND y2.iri='Beimu';
INSERT INTO shibafan (yaowu_id, related_yaowu_id) SELECT y1.id, y2.id FROM yaowu y1, yaowu y2 WHERE y1.iri='Fuzi' AND y2.iri='Bailian';
INSERT INTO shibafan (yaowu_id, related_yaowu_id) SELECT y1.id, y2.id FROM yaowu y1, yaowu y2 WHERE y1.iri='Fuzi' AND y2.iri='Baiji';
-- 川乌反半夏、瓜蒌实、瓜蒌根、贝母、白蔹、白及
INSERT INTO shibafan (yaowu_id, related_yaowu_id) SELECT y1.id, y2.id FROM yaowu y1, yaowu y2 WHERE y1.iri='Chuanwu' AND y2.iri='Banxia';
INSERT INTO shibafan (yaowu_id, related_yaowu_id) SELECT y1.id, y2.id FROM yaowu y1, yaowu y2 WHERE y1.iri='Chuanwu' AND y2.iri='Gualoushi';
INSERT INTO shibafan (yaowu_id, related_yaowu_id) SELECT y1.id, y2.id FROM yaowu y1, yaowu y2 WHERE y1.iri='Chuanwu' AND y2.iri='Gualougen';
INSERT INTO shibafan (yaowu_id, related_yaowu_id) SELECT y1.id, y2.id FROM yaowu y1, yaowu y2 WHERE y1.iri='Chuanwu' AND y2.iri='Beimu';
INSERT INTO shibafan (yaowu_id, related_yaowu_id) SELECT y1.id, y2.id FROM yaowu y1, yaowu y2 WHERE y1.iri='Chuanwu' AND y2.iri='Bailian';
INSERT INTO shibafan (yaowu_id, related_yaowu_id) SELECT y1.id, y2.id FROM yaowu y1, yaowu y2 WHERE y1.iri='Chuanwu' AND y2.iri='Baiji';
-- 草乌反半夏、瓜蒌实、瓜蒌根、贝母、白蔹、白及
INSERT INTO shibafan (yaowu_id, related_yaowu_id) SELECT y1.id, y2.id FROM yaowu y1, yaowu y2 WHERE y1.iri='Caowu' AND y2.iri='Banxia';
INSERT INTO shibafan (yaowu_id, related_yaowu_id) SELECT y1.id, y2.id FROM yaowu y1, yaowu y2 WHERE y1.iri='Caowu' AND y2.iri='Gualoushi';
INSERT INTO shibafan (yaowu_id, related_yaowu_id) SELECT y1.id, y2.id FROM yaowu y1, yaowu y2 WHERE y1.iri='Caowu' AND y2.iri='Gualougen';
INSERT INTO shibafan (yaowu_id, related_yaowu_id) SELECT y1.id, y2.id FROM yaowu y1, yaowu y2 WHERE y1.iri='Caowu' AND y2.iri='Beimu';
INSERT INTO shibafan (yaowu_id, related_yaowu_id) SELECT y1.id, y2.id FROM yaowu y1, yaowu y2 WHERE y1.iri='Caowu' AND y2.iri='Bailian';
INSERT INTO shibafan (yaowu_id, related_yaowu_id) SELECT y1.id, y2.id FROM yaowu y1, yaowu y2 WHERE y1.iri='Caowu' AND y2.iri='Baiji';
-- 甘草反甘遂、大戟、海藻、芫花
INSERT INTO shibafan (yaowu_id, related_yaowu_id) SELECT y1.id, y2.id FROM yaowu y1, yaowu y2 WHERE y1.iri='Gancao' AND y2.iri='Gansui';
INSERT INTO shibafan (yaowu_id, related_yaowu_id) SELECT y1.id, y2.id FROM yaowu y1, yaowu y2 WHERE y1.iri='Gancao' AND y2.iri='Daji';
INSERT INTO shibafan (yaowu_id, related_yaowu_id) SELECT y1.id, y2.id FROM yaowu y1, yaowu y2 WHERE y1.iri='Gancao' AND y2.iri='Haizao';
INSERT INTO shibafan (yaowu_id, related_yaowu_id) SELECT y1.id, y2.id FROM yaowu y1, yaowu y2 WHERE y1.iri='Gancao' AND y2.iri='Yuanhua';
-- 藜芦反人参、沙参、丹参、玄参、苦参、细辛、芍药
INSERT INTO shibafan (yaowu_id, related_yaowu_id) SELECT y1.id, y2.id FROM yaowu y1, yaowu y2 WHERE y1.iri='Lilu' AND y2.iri='Renshen';
INSERT INTO shibafan (yaowu_id, related_yaowu_id) SELECT y1.id, y2.id FROM yaowu y1, yaowu y2 WHERE y1.iri='Lilu' AND y2.iri='Shashen';
INSERT INTO shibafan (yaowu_id, related_yaowu_id) SELECT y1.id, y2.id FROM yaowu y1, yaowu y2 WHERE y1.iri='Lilu' AND y2.iri='Danshen';
INSERT INTO shibafan (yaowu_id, related_yaowu_id) SELECT y1.id, y2.id FROM yaowu y1, yaowu y2 WHERE y1.iri='Lilu' AND y2.iri='Xuanshen';
INSERT INTO shibafan (yaowu_id, related_yaowu_id) SELECT y1.id, y2.id FROM yaowu y1, yaowu y2 WHERE y1.iri='Lilu' AND y2.iri='Kushen';
INSERT INTO shibafan (yaowu_id, related_yaowu_id) SELECT y1.id, y2.id FROM yaowu y1, yaowu y2 WHERE y1.iri='Lilu' AND y2.iri='Xixin';
INSERT INTO shibafan (yaowu_id, related_yaowu_id) SELECT y1.id, y2.id FROM yaowu y1, yaowu y2 WHERE y1.iri='Lilu' AND y2.iri='Shaoyao';

-- ---------- 插入十九畏关系 ----------
-- 硫黄畏芒硝
INSERT INTO shijiuwei (yaowu_id, related_yaowu_id) SELECT y1.id, y2.id FROM yaowu y1, yaowu y2 WHERE y1.iri='Liuhuang' AND y2.iri='Mangxiao';
-- 水银畏砒霜
INSERT INTO shijiuwei (yaowu_id, related_yaowu_id) SELECT y1.id, y2.id FROM yaowu y1, yaowu y2 WHERE y1.iri='Shuiyin' AND y2.iri='Pishuang';
-- 狼毒畏密陀僧
INSERT INTO shijiuwei (yaowu_id, related_yaowu_id) SELECT y1.id, y2.id FROM yaowu y1, yaowu y2 WHERE y1.iri='Langdu' AND y2.iri='Mituoseng';
-- 巴豆畏牵牛子
INSERT INTO shijiuwei (yaowu_id, related_yaowu_id) SELECT y1.id, y2.id FROM yaowu y1, yaowu y2 WHERE y1.iri='Badou' AND y2.iri='Qianniuzi';
-- 丁香畏郁金
INSERT INTO shijiuwei (yaowu_id, related_yaowu_id) SELECT y1.id, y2.id FROM yaowu y1, yaowu y2 WHERE y1.iri='Dingxiang' AND y2.iri='Yujin';
-- 芒硝畏三棱
INSERT INTO shijiuwei (yaowu_id, related_yaowu_id) SELECT y1.id, y2.id FROM yaowu y1, yaowu y2 WHERE y1.iri='Mangxiao' AND y2.iri='Sanleng';
-- 川乌畏犀角
INSERT INTO shijiuwei (yaowu_id, related_yaowu_id) SELECT y1.id, y2.id FROM yaowu y1, yaowu y2 WHERE y1.iri='Chuanwu' AND y2.iri='Xijiao';
-- 草乌畏犀角
INSERT INTO shijiuwei (yaowu_id, related_yaowu_id) SELECT y1.id, y2.id FROM yaowu y1, yaowu y2 WHERE y1.iri='Caowu' AND y2.iri='Xijiao';
-- 人参畏五灵脂
INSERT INTO shijiuwei (yaowu_id, related_yaowu_id) SELECT y1.id, y2.id FROM yaowu y1, yaowu y2 WHERE y1.iri='Renshen' AND y2.iri='Wulingzhi';
-- 肉桂畏赤石脂
INSERT INTO shijiuwei (yaowu_id, related_yaowu_id) SELECT y1.id, y2.id FROM yaowu y1, yaowu y2 WHERE y1.iri='Rougui' AND y2.iri='Chishizhi';

-- ========== 完成 ==========