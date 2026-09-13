-- =============================================
-- 经方数据库 MySQL 脚本
-- 依据：tcm-yaowu-abox.owl + tcm-fangji-abox.owl
-- IRI 与本体 ABox 完全一致
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

-- ---------- 插入药物（共175种） ----------
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
('Shanyao','山药'),('Qumai','瞿麦'),('Maziren','麻子仁'),('Dongguazi','冬瓜仁'),('Jiaomu','椒目'),
('Mufangji','木防己'),('Wutou','乌头'),('Zhusha','朱砂'),('Xiaoshi','硝石'),('Shanglu','商陆'),
('Yuyuliang','禹余粮'),('Shufu','鼠妇'),('Wushan','乌扇'),('Shiwei','石韦'),('Ziwei','紫葳'),
('Fengwo','蜂窠'),('Chixiao','赤硝'),('Qianglang','蜣螂'),('Ganqi','干漆'),('Jicao','蛴螬'),
('Dadouhuangjuan','豆黄卷'),('Qu','曲'),('Weirui','葳蕤'),('Tianmendong','天门冬'),('Yinchenhao','茵陈蒿'),
('Jiegeng','桔梗'),('Zhufu','猪肤'),('Baifen','白粉'),('Baiye','柏叶'),('Matongzhi','马通汁'),
('Xinjiang','新绛'),('Lianqiao','连翘'),('Shengzibaipi','生梓白皮'),('Shanzhuyu','山茱萸'),('Guadi','瓜蒂'),
('Ziye','紫苏叶'),('Facu','法醋'),('Shenglangya','生狼牙'),('Tinglizi','葶苈子'),('Shuqi','蜀漆'),
('Fanshi','矾石'),('Duhuo','独活'),('Tianxiong','天雄'),('Zaojia','皂荚'),('Weijing','苇茎'),
('Guaban','瓜瓣'),('Honglanhua','红蓝花'),('Ganligenbaipi','甘李根白皮'),('Biejia','鳖甲'),('Shengma','升麻');

-- ---------- 插入方剂（共180首） ----------
INSERT INTO fangji (iri, label) VALUES
('Guizhitang','桂枝汤'),('Mahuangtang','麻黄汤'),('Gegentang','葛根汤'),('Xiaochaihutang','小柴胡汤'),('Dachengqitang','大承气汤'),
('Sinitang','四逆汤'),('Wulingsan','五苓散'),('Lingguizhugantang','苓桂术甘汤'),('Shenqiwan','肾气丸'),('Wumeiwan','乌梅丸'),
('Wenjingtang','温经汤'),('Xiaoqinglongtang','小青龙汤'),('Huangqiguizhiwuwutang','黄芪桂枝五物汤'),('Xiaochengqitang','小承气汤'),('Tiaoweichengqitang','调胃承气汤'),
('Tongmaisinitang','通脉四逆汤'),('Baitongtang','白通汤'),('Banxiahoupotang','半夏厚朴汤'),('Yuebitang','越婢汤'),('Maimendongtang','麦门冬汤'),
('Tinglidazaoxiefeitang','葶苈大枣泻肺汤'),('Gualouxiebaibaijiutang','瓜蒌薤白白酒汤'),('Mufangjitang','木防己汤'),('Zexietang','泽泻汤'),('Zhizichitang','栀子豉汤'),
('Baihedihuangtang','百合地黄汤'),('Gancaoganjiangtang','甘草干姜汤'),('Fulinggancaotang','茯苓甘草汤'),('Dahuanggancaotang','大黄甘草汤'),('Baitouwengtang','白头翁汤'),
('Huangtutang','黄土汤'),('Chixiaodoudangguisan','赤小豆当归散'),('Xiexintang','泻心汤'),('Baiyetang','柏叶汤'),('Daqinglongtang','大青龙汤'),
('Xiaoqinglongjiashigaotang','小青龙加石膏汤'),('Yuebijiazhuatang','越婢加术汤'),('Zhuyeshigaotang','竹叶石膏汤'),('Huangliantang','黄连汤'),('Zhizihoupotang','栀子厚朴汤'),
('Zhiziganjiangtang','栀子干姜汤'),('Zhizigancaochitang','栀子甘草豉汤'),('Zhizishengjiangchitang','栀子生姜豉汤'),('Zhishizhizichitang','枳实栀子豉汤'),('Dahuanghuanglianxiexintang','大黄黄连泻心汤'),
('Fuzixiexintang','附子泻心汤'),('Shengjiangxiexintang','生姜泻心汤'),('Gancaoxiexintang','甘草泻心汤'),('Dachaihutang','大柴胡汤'),('Chaihuguizhiganjiangtang','柴胡桂枝干姜汤'),
('Chaihuguizhitang','柴胡桂枝汤'),('Chaihujiamangxiaotang','柴胡加芒硝汤'),('Chaihujialonggumulitang','柴胡加龙骨牡蛎汤'),('Huangqintang','黄芩汤'),('Huangqinjiabanxiashengjiangtang','黄芩加半夏生姜汤'),
('Mazirenwan','麻子仁丸'),('Yinchenhaotang','茵陈蒿汤'),('Zhizibaipitang','栀子柏皮汤'),('Dahuangfuzitang','大黄附子汤'),('Houposanwutang','厚朴三物汤'),
('Dahuangmudantang','大黄牡丹汤'),('Fuzitang','附子汤'),('Taohuatang','桃花汤'),('Zhufutang','猪肤汤'),('Gancaotang','甘草汤'),
('Jiegengtang','桔梗汤'),('Dangguisinijiawuzhuyushengjiangtang','当归四逆加吴茱萸生姜汤'),('Mahuangshengmatang','麻黄升麻汤'),('Gualouguizhitang','栝楼桂枝汤'),('Mahuangjiazhuatang','麻黄加术汤'),
('Maxingyigantang','麻杏薏甘汤'),('Fangjihuangqitang','防己黄芪汤'),('Guizhifuzitang','桂枝附子汤'),('Baizhufuzitang','白术附子汤'),('Gancaofuzitang','甘草附子汤'),
('Shengmabiejiatang','升麻鳖甲汤'),('Wutoutang','乌头汤'),('Guizhijialonggumulitang','桂枝加龙骨牡蛎汤'),('Dahuangzhechongwan','大黄蛰虫丸'),('Gualouxiebaibanxiantang','瓜蒌薤白半夏汤'),
('Zhishixiebaiguizhitang','枳实薤白桂枝汤'),('Fulingxingrengancaotang','茯苓杏仁甘草汤'),('Juzhijiangtang','橘枳姜汤'),('Xuanfuhuatang','旋覆花汤'),('Xiaobanxiantang','小半夏汤'),
('Xiaobanxiajiafulingtang','小半夏加茯苓汤'),('Guilingwuweigancaotang','桂苓五味甘草汤'),('Lingganwuweijiangxintang','苓甘五味姜辛汤'),('Mufangjiqushigaojiafulingmangxiaotang','木防己去石膏加茯苓芒硝汤'),('Jijiaolihuangwan','己椒苈黄丸'),
('Zhuyetang','竹叶汤'),('Baitouwengjiagancaojiaotang','白头翁加甘草阿胶汤'),('Ganjiangrenshenbanxiawan','干姜人参半夏丸'),('Dangguibeimukushenwan','当归贝母苦参丸'),('Kujiutang','苦酒汤'),
('Banxiasanjitang','半夏散及汤'),('Baitongjiazhudanzhitang','白通加猪胆汁汤'),('Tongmaisijiazhudanzhitang','通脉四逆加猪胆汁汤'),('Sinijiarenshentang','四逆加人参汤'),('Guizhijiadahuangtang','桂枝加大黄汤'),
('Baihezhimutang','百合知母汤'),('Huashidaizhetang','滑石代赭汤'),('Baihejizitang','百合鸡子汤'),('Gualoumulisan','瓜蒌牡蛎散'),('Baihehuashisan','百合滑石散'),
('Baihujiaguizhitang','白虎加桂枝汤'),('Houpomahuangtang','厚朴麻黄汤'),('Yuebijiabanxiatang','越婢加半夏汤'),('Baihutang','白虎汤'),('Mahuangfuzixixintang','麻黄附子细辛汤'),
('Zhenwutang','真武汤'),('Guizhifulingwan','桂枝茯苓丸'),('Sinisansan','四逆散'),('Wendantang','温胆汤'),('Banxiaxiexintang','半夏泻心汤'),
('Gansuibanxiantang','甘遂半夏汤'),('Fuzijingmitang','附子粳米汤'),('Gualouqumaiwan','栝楼瞿麦丸'),('Taohechengqitang','桃核承气汤'),('Huanglianejiaotang','黄连阿胶汤'),
('Dangguisinitang','当归四逆汤'),('Wuzhuyutang','吴茱萸汤'),('Xiaojianzhongtang','小建中汤'),('Suanzaorentang','酸枣仁汤'),('Houpoqiwutang','厚朴七物汤'),
('Gegenhuangqinhuangliantang','葛根黄芩黄连汤'),('Zeqitang','泽漆汤'),('Maxingshigantang','麻杏石甘汤'),('Zhulingtang','猪苓汤'),('Shizaotang','十枣汤'),
('Didangtang','抵当汤'),('Guadisan','瓜蒂散'),('Ganjianghuangqinhuanglianrenshentang','干姜黄芩黄连人参汤'),('Guizhishaoyaozhimutang','桂枝芍药知母汤'),('Xuanfudaizhetang','旋覆代赭汤'),
('Bentuntang','奔豚汤'),('Guizhijiaguitang','桂枝加桂汤'),('Tianwutouchishimisan','头风摩散'),('Lizhongtang','理中汤'),('Xiayuxuetang','下瘀血汤'),
('Guizhijiagegentang','桂枝加葛根汤'),('Guizhijiahoupoxingrentang','桂枝加厚朴杏子汤'),('Guizhijiafuzitang','桂枝加附子汤'),('Guizhiqushaoyaotang','桂枝去芍药汤'),('Guizhiqushaoyaojiafuzitang','桂枝去芍药加附子汤'),
('Guizhixinjiatang','桂枝新加汤'),('Gegenjiabanxiantang','葛根加半夏汤'),('Mahuanglianqiaochixiaodoutang','麻黄连翘赤小豆汤'),('Guizhiermahuangyitang','桂枝二麻黄一汤'),('Guizhimahuanggebantang','桂枝麻黄各半汤'),
('Daxianxiongtang','大陷胸汤'),('Xiaoxianxiongtang','小陷胸汤'),('Guizhirenshentang','桂枝人参汤'),('Houposhengjiangbanxiagancaorenshentang','厚朴生姜半夏甘草人参汤'),('Dajianzhongtang','大建中汤'),
('Biejiajianwan','鳖甲煎丸'),('Sheganmahuangtang','射干麻黄汤'),('Fangjifulingtang','防己茯苓汤'),('Yinchenwulingsan','茵陈五苓散'),('Jupizhuratang','橘皮竹茹汤'),
('Fulingguizhigancaodazaotang','茯苓桂枝甘草大枣汤'),('Zhigancaotang','炙甘草汤'),('Baihujiarenshentang','白虎加人参汤'),('Mulizexiesan','牡蛎泽泻散'),('Chishizhiyuyuliangtang','赤石脂禹余粮汤'),
('Daxianxiongwan','大陷胸丸'),('Sanwubaisan','三物白散'),('Shuyuwan','薯蓣丸'),('Yiyifuzisan','薏苡附子散'),('Guizhishengjiangzhishitang','桂枝生姜枳实汤'),
('Wutouchishizhiwan','乌头赤石脂丸'),('Chiwan','赤丸'),('Dawutoujian','大乌头煎'),('Wutouguizhitang','乌头桂枝汤'),('Gancaoganjiangfulingbaizhutang','甘草干姜茯苓白术汤'),
('Gancaomahuangtang','甘草麻黄汤'),('Huangqishaoyaoguizhikujiutang','黄芪芍药桂枝苦酒汤'),('Guizhijiahuangqitang','桂枝加黄芪汤'),('Zhizidahuangtang','栀子大黄汤'),('Dahuangxiaoshitang','大黄硝石汤');

-- ---------- 插入方剂-药物关系 ----------
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Guizhitang' AND y.iri IN ('Guizhi','Shaoyao','Gancao','Shengjiang','Dazao');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Mahuangtang' AND y.iri IN ('Mahuang','Guizhi','Xingren','Gancao');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Gegentang' AND y.iri IN ('Gegen','Mahuang','Guizhi','Shengjiang','Gancao','Shaoyao','Dazao');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Xiaochaihutang' AND y.iri IN ('Chaihu','Huangqin','Renshen','Banxia','Gancao','Shengjiang','Dazao');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Dachengqitang' AND y.iri IN ('Dahuang','Houpo','Zhishi','Mangxiao');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Sinitang' AND y.iri IN ('Fuzi','Ganjiang','Gancao');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Wulingsan' AND y.iri IN ('Zhuling','Zexie','Baizhu','Fuling','Guizhi');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Lingguizhugantang' AND y.iri IN ('Fuling','Guizhi','Baizhu','Gancao');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Shenqiwan' AND y.iri IN ('Dihuang','Shanyao','Shanzhuyu','Zexie','Fuling','Mudanpi','Guizhi','Fuzi');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Wumeiwan' AND y.iri IN ('Wumei','Xixin','Ganjiang','Huanglian','Danggui','Fuzi','Shujiao','Guizhi','Renshen','Huangbai');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Wenjingtang' AND y.iri IN ('Wuzhuyu','Danggui','Chuanxiong','Shaoyao','Renshen','Guizhi','Ejiao','Mudanpi','Shengjiang','Gancao','Banxia','Maimendong');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Xiaoqinglongtang' AND y.iri IN ('Mahuang','Shaoyao','Xixin','Ganjiang','Gancao','Guizhi','Wuweizi','Banxia');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Huangqiguizhiwuwutang' AND y.iri IN ('Huangqi','Shaoyao','Guizhi','Shengjiang','Dazao');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Xiaochengqitang' AND y.iri IN ('Dahuang','Houpo','Zhishi');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Tiaoweichengqitang' AND y.iri IN ('Dahuang','Gancao','Mangxiao');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Tongmaisinitang' AND y.iri IN ('Gancao','Fuzi','Ganjiang');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Baitongtang' AND y.iri IN ('Congbai','Ganjiang','Fuzi');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Banxiahoupotang' AND y.iri IN ('Banxia','Houpo','Fuling','Shengjiang','Ziye');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Yuebitang' AND y.iri IN ('Mahuang','Shigao','Shengjiang','Dazao','Gancao');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Maimendongtang' AND y.iri IN ('Maimendong','Banxia','Renshen','Gancao','Jingmi','Dazao');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Tinglidazaoxiefeitang' AND y.iri IN ('Tinglizi','Dazao');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Gualouxiebaibaijiutang' AND y.iri IN ('Gualoushi','Xiebai','Baijiu');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Mufangjitang' AND y.iri IN ('Mufangji','Shigao','Guizhi','Renshen');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Zexietang' AND y.iri IN ('Zexie','Baizhu');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Zhizichitang' AND y.iri IN ('Zhizi','Xiangchi');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Baihedihuangtang' AND y.iri IN ('Baihe','Shengdihuangzhi');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Gancaoganjiangtang' AND y.iri IN ('Gancao','Ganjiang');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Fulinggancaotang' AND y.iri IN ('Fuling','Guizhi','Gancao','Shengjiang');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Dahuanggancaotang' AND y.iri IN ('Dahuang','Gancao');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Baitouwengtang' AND y.iri IN ('Baitouweng','Huangbai','Huanglian','Qinpi');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Huangtutang' AND y.iri IN ('Gancao','Dihuang','Baizhu','Fuzi','Ejiao','Huangqin','Zaoxintu');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Chixiaodoudangguisan' AND y.iri IN ('Chixiaodou','Danggui');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Xiexintang' AND y.iri IN ('Dahuang','Huanglian','Huangqin');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Baiyetang' AND y.iri IN ('Baiye','Ganjiang','Aiye','Matongzhi');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Daqinglongtang' AND y.iri IN ('Mahuang','Guizhi','Gancao','Xingren','Shengjiang','Dazao','Shigao');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Xiaoqinglongjiashigaotang' AND y.iri IN ('Mahuang','Shaoyao','Guizhi','Xixin','Gancao','Ganjiang','Wuweizi','Banxia','Shigao');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Yuebijiazhuatang' AND y.iri IN ('Mahuang','Shigao','Shengjiang','Gancao','Baizhu','Dazao');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Zhuyeshigaotang' AND y.iri IN ('Zhuye','Shigao','Banxia','Maimendong','Renshen','Gancao','Jingmi');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Huangliantang' AND y.iri IN ('Huanglian','Gancao','Ganjiang','Guizhi','Renshen','Banxia','Dazao');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Zhizihoupotang' AND y.iri IN ('Zhizi','Houpo','Zhishi');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Zhiziganjiangtang' AND y.iri IN ('Zhizi','Ganjiang');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Zhizigancaochitang' AND y.iri IN ('Zhizi','Gancao','Xiangchi');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Zhizishengjiangchitang' AND y.iri IN ('Zhizi','Shengjiang','Xiangchi');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Zhishizhizichitang' AND y.iri IN ('Zhishi','Zhizi','Xiangchi');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Dahuanghuanglianxiexintang' AND y.iri IN ('Dahuang','Huanglian');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Fuzixiexintang' AND y.iri IN ('Dahuang','Huanglian','Huangqin','Fuzi');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Shengjiangxiexintang' AND y.iri IN ('Shengjiang','Gancao','Renshen','Ganjiang','Huangqin','Banxia','Huanglian','Dazao');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Gancaoxiexintang' AND y.iri IN ('Gancao','Huangqin','Ganjiang','Banxia','Huanglian','Dazao','Renshen');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Dachaihutang' AND y.iri IN ('Chaihu','Huangqin','Shaoyao','Banxia','Shengjiang','Zhishi','Dazao','Dahuang');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Chaihuguizhiganjiangtang' AND y.iri IN ('Chaihu','Guizhi','Ganjiang','Gualougen','Huangqin','Muli','Gancao');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Chaihuguizhitang' AND y.iri IN ('Guizhi','Shaoyao','Huangqin','Renshen','Gancao','Banxia','Dazao','Shengjiang','Chaihu');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Chaihujiamangxiaotang' AND y.iri IN ('Chaihu','Huangqin','Renshen','Gancao','Shengjiang','Banxia','Dazao','Mangxiao');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Chaihujialonggumulitang' AND y.iri IN ('Chaihu','Longgu','Huangqin','Shengjiang','Renshen','Guizhi','Fuling','Banxia','Dahuang','Muli','Dazao','Qiandan');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Huangqintang' AND y.iri IN ('Huangqin','Shaoyao','Gancao','Dazao');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Huangqinjiabanxiashengjiangtang' AND y.iri IN ('Huangqin','Shaoyao','Gancao','Dazao','Banxia','Shengjiang');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Mazirenwan' AND y.iri IN ('Maziren','Shaoyao','Zhishi','Dahuang','Houpo','Xingren');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Yinchenhaotang' AND y.iri IN ('Yinchenhao','Zhizi','Dahuang');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Zhizibaipitang' AND y.iri IN ('Zhizi','Gancao','Huangbai');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Dahuangfuzitang' AND y.iri IN ('Dahuang','Fuzi','Xixin');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Houposanwutang' AND y.iri IN ('Houpo','Zhishi','Dahuang');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Dahuangmudantang' AND y.iri IN ('Dahuang','Mudanpi','Taoren','Dongguazi','Mangxiao');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Fuzitang' AND y.iri IN ('Fuzi','Fuling','Renshen','Baizhu','Shaoyao');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Taohuatang' AND y.iri IN ('Chishizhi','Ganjiang','Jingmi');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Zhufutang' AND y.iri IN ('Zhufu','Baifen','Mi');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Gancaotang' AND y.iri IN ('Gancao');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Jiegengtang' AND y.iri IN ('Jiegeng','Gancao');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Dangguisinijiawuzhuyushengjiangtang' AND y.iri IN ('Danggui','Guizhi','Shaoyao','Xixin','Gancao','Tongcao','Dazao','Wuzhuyu','Shengjiang');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Mahuangshengmatang' AND y.iri IN ('Mahuang','Shengma','Danggui','Zhimu','Huangqin','Weirui','Shaoyao','Tianmendong','Guizhi','Fuling','Gancao','Shigao','Baizhu','Ganjiang');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Gualouguizhitang' AND y.iri IN ('Gualougen','Guizhi','Shaoyao','Gancao','Shengjiang','Dazao');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Mahuangjiazhuatang' AND y.iri IN ('Mahuang','Guizhi','Gancao','Xingren','Baizhu');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Maxingyigantang' AND y.iri IN ('Mahuang','Xingren','Yiyiren','Gancao');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Fangjihuangqitang' AND y.iri IN ('Mufangji','Huangqi','Gancao','Baizhu');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Guizhifuzitang' AND y.iri IN ('Guizhi','Fuzi','Shengjiang','Dazao','Gancao');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Baizhufuzitang' AND y.iri IN ('Baizhu','Fuzi','Shengjiang','Dazao','Gancao');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Gancaofuzitang' AND y.iri IN ('Gancao','Fuzi','Baizhu','Guizhi');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Shengmabiejiatang' AND y.iri IN ('Shengma','Danggui','Shujiao','Gancao','Biejia','Xionghuang');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Wutoutang' AND y.iri IN ('Chuanwu','Mahuang','Shaoyao','Huangqi','Gancao');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Guizhijialonggumulitang' AND y.iri IN ('Guizhi','Shaoyao','Shengjiang','Gancao','Dazao','Longgu','Muli');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Dahuangzhechongwan' AND y.iri IN ('Dahuang','Huangqin','Gancao','Taoren','Xingren','Shaoyao','Dihuang','Ganqi','Mengchong','Shuizhi','Jicao','Zhechong');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Gualouxiebaibanxiantang' AND y.iri IN ('Gualoushi','Xiebai','Banxia','Baijiu');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Zhishixiebaiguizhitang' AND y.iri IN ('Zhishi','Houpo','Xiebai','Guizhi','Gualoushi');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Fulingxingrengancaotang' AND y.iri IN ('Fuling','Xingren','Gancao');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Juzhijiangtang' AND y.iri IN ('Chenpi','Zhishi','Shengjiang');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Xuanfuhuatang' AND y.iri IN ('Xuanfuhua','Congbai','Xinjiang');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Xiaobanxiantang' AND y.iri IN ('Banxia','Shengjiang');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Xiaobanxiajiafulingtang' AND y.iri IN ('Banxia','Shengjiang','Fuling');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Guilingwuweigancaotang' AND y.iri IN ('Fuling','Guizhi','Gancao','Wuweizi');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Lingganwuweijiangxintang' AND y.iri IN ('Fuling','Gancao','Ganjiang','Xixin','Wuweizi');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Mufangjiqushigaojiafulingmangxiaotang' AND y.iri IN ('Mufangji','Guizhi','Renshen','Fuling','Mangxiao');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Jijiaolihuangwan' AND y.iri IN ('Mufangji','Jiaomu','Tinglizi','Dahuang');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Zhuyetang' AND y.iri IN ('Zhuye','Gegen','Fangfeng','Jiegeng','Guizhi','Renshen','Gancao','Fuzi','Shengjiang','Dazao');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Baitouwengjiagancaojiaotang' AND y.iri IN ('Baitouweng','Gancao','Ejiao','Huangbai','Huanglian','Qinpi');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Ganjiangrenshenbanxiawan' AND y.iri IN ('Ganjiang','Renshen','Banxia');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Dangguibeimukushenwan' AND y.iri IN ('Danggui','Beimu','Kushen');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Kujiutang' AND y.iri IN ('Banxia','Jizihuang','Kujiu');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Banxiasanjitang' AND y.iri IN ('Banxia','Guizhi','Gancao');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Baitongjiazhudanzhitang' AND y.iri IN ('Congbai','Ganjiang','Fuzi','Renniao','Zhudanzhi');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Tongmaisijiazhudanzhitang' AND y.iri IN ('Gancao','Fuzi','Ganjiang','Zhudanzhi');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Sinijiarenshentang' AND y.iri IN ('Gancao','Fuzi','Ganjiang','Renshen');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Guizhijiadahuangtang' AND y.iri IN ('Guizhi','Shaoyao','Gancao','Shengjiang','Dazao','Dahuang');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Baihezhimutang' AND y.iri IN ('Baihe','Zhimu');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Huashidaizhetang' AND y.iri IN ('Baihe','Huashi','Daizheshi');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Baihejizitang' AND y.iri IN ('Baihe','Jizihuang');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Gualoumulisan' AND y.iri IN ('Gualougen','Muli');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Baihehuashisan' AND y.iri IN ('Baihe','Huashi');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Baihujiaguizhitang' AND y.iri IN ('Zhimu','Shigao','Gancao','Jingmi','Guizhi');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Houpomahuangtang' AND y.iri IN ('Houpo','Mahuang','Shigao','Xingren','Banxia','Ganjiang','Xixin','Xiaomai','Wuweizi');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Yuebijiabanxiatang' AND y.iri IN ('Mahuang','Shigao','Shengjiang','Dazao','Gancao','Banxia');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Baihutang' AND y.iri IN ('Zhimu','Shigao','Gancao','Jingmi');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Mahuangfuzixixintang' AND y.iri IN ('Mahuang','Fuzi','Xixin');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Zhenwutang' AND y.iri IN ('Fuling','Shaoyao','Shengjiang','Baizhu','Fuzi');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Guizhifulingwan' AND y.iri IN ('Guizhi','Fuling','Mudanpi','Taoren','Shaoyao');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Sinisansan' AND y.iri IN ('Gancao','Zhishi','Chaihu','Shaoyao');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Wendantang' AND y.iri IN ('Banxia','Zhuru','Zhishi','Chenpi','Gancao','Fuling','Shengjiang','Dazao');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Banxiaxiexintang' AND y.iri IN ('Banxia','Huangqin','Ganjiang','Renshen','Huanglian','Dazao','Gancao');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Gansuibanxiantang' AND y.iri IN ('Gansui','Banxia','Shaoyao','Gancao');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Fuzijingmitang' AND y.iri IN ('Fuzi','Banxia','Gancao','Dazao','Jingmi');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Gualouqumaiwan' AND y.iri IN ('Gualougen','Fuling','Shanyao','Fuzi','Qumai');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Taohechengqitang' AND y.iri IN ('Taoren','Dahuang','Guizhi','Gancao','Mangxiao');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Huanglianejiaotang' AND y.iri IN ('Huanglian','Huangqin','Shaoyao','Jizihuang','Ejiao');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Dangguisinitang' AND y.iri IN ('Danggui','Guizhi','Shaoyao','Xixin','Gancao','Tongcao','Dazao');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Wuzhuyutang' AND y.iri IN ('Wuzhuyu','Renshen','Shengjiang','Dazao');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Xiaojianzhongtang' AND y.iri IN ('Guizhi','Gancao','Dazao','Shaoyao','Shengjiang','Yitang');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Suanzaorentang' AND y.iri IN ('Suanzaoren','Gancao','Zhimu','Fuling','Chuanxiong');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Houpoqiwutang' AND y.iri IN ('Houpo','Gancao','Dahuang','Dazao','Zhishi','Guizhi','Shengjiang');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Gegenhuangqinhuangliantang' AND y.iri IN ('Gegen','Gancao','Huangqin','Huanglian');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Zeqitang' AND y.iri IN ('Banxia','Zican','Zeqi','Shengjiang','Baiqian','Gancao','Huangqin','Renshen','Guizhi');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Maxingshigantang' AND y.iri IN ('Mahuang','Xingren','Gancao','Shigao');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Zhulingtang' AND y.iri IN ('Zhuling','Fuling','Zexie','Ejiao','Huashi');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Shizaotang' AND y.iri IN ('Yuanhua','Gansui','Daji','Dazao');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Didangtang' AND y.iri IN ('Shuizhi','Mengchong','Taoren','Dahuang');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Guadisan' AND y.iri IN ('Guadi','Chixiaodou','Xiangchi');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Ganjianghuangqinhuanglianrenshentang' AND y.iri IN ('Ganjiang','Huangqin','Huanglian','Renshen');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Guizhishaoyaozhimutang' AND y.iri IN ('Guizhi','Shaoyao','Gancao','Mahuang','Shengjiang','Baizhu','Zhimu','Fangfeng','Fuzi');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Xuanfudaizhetang' AND y.iri IN ('Xuanfuhua','Renshen','Shengjiang','Daizheshi','Gancao','Banxia','Dazao');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Bentuntang' AND y.iri IN ('Gancao','Chuanxiong','Danggui','Banxia','Huangqin','Gegen','Shaoyao','Shengjiang','Ganligenbaipi');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Guizhijiaguitang' AND y.iri IN ('Guizhi','Shaoyao','Gancao','Shengjiang','Dazao');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Tianwutouchishimisan' AND y.iri IN ('Fuzi','Yan');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Lizhongtang' AND y.iri IN ('Renshen','Ganjiang','Baizhu','Gancao');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Xiayuxuetang' AND y.iri IN ('Dahuang','Taoren','Zhechong');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Guizhijiagegentang' AND y.iri IN ('Gegen','Guizhi','Shaoyao','Gancao','Shengjiang','Dazao');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Guizhijiahoupoxingrentang' AND y.iri IN ('Guizhi','Shaoyao','Gancao','Shengjiang','Dazao','Houpo','Xingren');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Guizhijiafuzitang' AND y.iri IN ('Guizhi','Shaoyao','Gancao','Shengjiang','Dazao','Fuzi');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Guizhiqushaoyaotang' AND y.iri IN ('Guizhi','Gancao','Shengjiang','Dazao');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Guizhiqushaoyaojiafuzitang' AND y.iri IN ('Guizhi','Gancao','Shengjiang','Dazao','Fuzi');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Guizhixinjiatang' AND y.iri IN ('Guizhi','Shaoyao','Gancao','Renshen','Dazao','Shengjiang');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Gegenjiabanxiantang' AND y.iri IN ('Gegen','Mahuang','Guizhi','Shengjiang','Gancao','Shaoyao','Dazao','Banxia');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Mahuanglianqiaochixiaodoutang' AND y.iri IN ('Mahuang','Lianqiao','Xingren','Chixiaodou','Dazao','Shengjiang','Gancao');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Guizhiermahuangyitang' AND y.iri IN ('Guizhi','Shaoyao','Mahuang','Shengjiang','Xingren','Gancao','Dazao');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Guizhimahuanggebantang' AND y.iri IN ('Guizhi','Shaoyao','Shengjiang','Gancao','Mahuang','Dazao','Xingren');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Daxianxiongtang' AND y.iri IN ('Dahuang','Mangxiao','Gansui');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Xiaoxianxiongtang' AND y.iri IN ('Huanglian','Banxia','Gualoushi');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Guizhirenshentang' AND y.iri IN ('Guizhi','Gancao','Baizhu','Renshen','Ganjiang');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Houposhengjiangbanxiagancaorenshentang' AND y.iri IN ('Houpo','Shengjiang','Banxia','Gancao','Renshen');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Dajianzhongtang' AND y.iri IN ('Shujiao','Ganjiang','Renshen','Yitang');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Biejiajianwan' AND y.iri IN ('Biejia','Wushan','Huangqin','Chaihu','Shufu','Ganjiang','Dahuang','Guizhi','Shiwei','Houpo','Ziwei','Ejiao','Fengwo','Chixiao','Qianglang','Taoren','Mudanpi','Shaoyao','Tinglizi','Banxia','Renshen','Qumai','Zhechong');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Sheganmahuangtang' AND y.iri IN ('Shegan','Mahuang','Shengjiang','Xixin','Ziwan','Kuandonghua','Wuweizi','Dazao','Banxia');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Fangjifulingtang' AND y.iri IN ('Mufangji','Huangqi','Guizhi','Fuling','Gancao');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Yinchenwulingsan' AND y.iri IN ('Yinchenhao','Zhuling','Zexie','Baizhu','Fuling','Guizhi');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Jupizhuratang' AND y.iri IN ('Chenpi','Zhuru','Dazao','Shengjiang','Gancao','Renshen');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Fulingguizhigancaodazaotang' AND y.iri IN ('Fuling','Guizhi','Gancao','Dazao');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Zhigancaotang' AND y.iri IN ('Gancao','Shengjiang','Renshen','Dihuang','Guizhi','Ejiao','Maimendong','Maziren','Dazao');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Baihujiarenshentang' AND y.iri IN ('Zhimu','Shigao','Gancao','Jingmi','Renshen');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Mulizexiesan' AND y.iri IN ('Muli','Zexie','Shuqi','Tinglizi','Shanglu','Haizao','Gualougen');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Chishizhiyuyuliangtang' AND y.iri IN ('Chishizhi','Yuyuliang');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Daxianxiongwan' AND y.iri IN ('Dahuang','Tinglizi','Mangxiao','Xingren','Gansui','Mi');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Sanwubaisan' AND y.iri IN ('Jiegeng','Badou','Beimu');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Shuyuwan' AND y.iri IN ('Shanyao','Danggui','Guizhi','Dihuang','Qu','Dadouhuangjuan','Gancao','Renshen','Ejiao','Chuanxiong','Shaoyao','Baizhu','Maimendong','Xingren','Chaihu','Jiegeng','Fuling','Ganjiang','Baijiangcao','Fangfeng','Dazao');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Yiyifuzisan' AND y.iri IN ('Yiyiren','Fuzi');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Guizhishengjiangzhishitang' AND y.iri IN ('Guizhi','Shengjiang','Zhishi');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Wutouchishizhiwan' AND y.iri IN ('Shujiao','Wutou','Fuzi','Ganjiang','Chishizhi');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Chiwan' AND y.iri IN ('Fuling','Banxia','Wutou','Xixin','Zhusha');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Dawutoujian' AND y.iri IN ('Wutou','Mi');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Wutouguizhitang' AND y.iri IN ('Wutou','Guizhi','Shaoyao','Gancao','Shengjiang','Dazao','Mi');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Gancaoganjiangfulingbaizhutang' AND y.iri IN ('Gancao','Baizhu','Ganjiang','Fuling');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Gancaomahuangtang' AND y.iri IN ('Gancao','Mahuang');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Huangqishaoyaoguizhikujiutang' AND y.iri IN ('Huangqi','Shaoyao','Guizhi','Kujiu');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Guizhijiahuangqitang' AND y.iri IN ('Guizhi','Shaoyao','Gancao','Shengjiang','Dazao','Huangqi');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Zhizidahuangtang' AND y.iri IN ('Zhizi','Dahuang','Zhishi','Xiangchi');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Dahuangxiaoshitang' AND y.iri IN ('Dahuang','Huangbai','Xiaoshi','Zhizi');

-- ---------- 插入十八反关系 ----------
INSERT INTO shibafan (yaowu_id, related_yaowu_id) SELECT y1.id, y2.id FROM yaowu y1, yaowu y2 WHERE y1.iri='Fuzi' AND y2.iri='Banxia';
INSERT INTO shibafan (yaowu_id, related_yaowu_id) SELECT y1.id, y2.id FROM yaowu y1, yaowu y2 WHERE y1.iri='Fuzi' AND y2.iri='Gualoushi';
INSERT INTO shibafan (yaowu_id, related_yaowu_id) SELECT y1.id, y2.id FROM yaowu y1, yaowu y2 WHERE y1.iri='Fuzi' AND y2.iri='Gualougen';
INSERT INTO shibafan (yaowu_id, related_yaowu_id) SELECT y1.id, y2.id FROM yaowu y1, yaowu y2 WHERE y1.iri='Fuzi' AND y2.iri='Beimu';
INSERT INTO shibafan (yaowu_id, related_yaowu_id) SELECT y1.id, y2.id FROM yaowu y1, yaowu y2 WHERE y1.iri='Fuzi' AND y2.iri='Bailian';
INSERT INTO shibafan (yaowu_id, related_yaowu_id) SELECT y1.id, y2.id FROM yaowu y1, yaowu y2 WHERE y1.iri='Fuzi' AND y2.iri='Baiji';
INSERT INTO shibafan (yaowu_id, related_yaowu_id) SELECT y1.id, y2.id FROM yaowu y1, yaowu y2 WHERE y1.iri='Chuanwu' AND y2.iri='Banxia';
INSERT INTO shibafan (yaowu_id, related_yaowu_id) SELECT y1.id, y2.id FROM yaowu y1, yaowu y2 WHERE y1.iri='Chuanwu' AND y2.iri='Gualoushi';
INSERT INTO shibafan (yaowu_id, related_yaowu_id) SELECT y1.id, y2.id FROM yaowu y1, yaowu y2 WHERE y1.iri='Chuanwu' AND y2.iri='Gualougen';
INSERT INTO shibafan (yaowu_id, related_yaowu_id) SELECT y1.id, y2.id FROM yaowu y1, yaowu y2 WHERE y1.iri='Chuanwu' AND y2.iri='Beimu';
INSERT INTO shibafan (yaowu_id, related_yaowu_id) SELECT y1.id, y2.id FROM yaowu y1, yaowu y2 WHERE y1.iri='Chuanwu' AND y2.iri='Bailian';
INSERT INTO shibafan (yaowu_id, related_yaowu_id) SELECT y1.id, y2.id FROM yaowu y1, yaowu y2 WHERE y1.iri='Chuanwu' AND y2.iri='Baiji';
INSERT INTO shibafan (yaowu_id, related_yaowu_id) SELECT y1.id, y2.id FROM yaowu y1, yaowu y2 WHERE y1.iri='Caowu' AND y2.iri='Banxia';
INSERT INTO shibafan (yaowu_id, related_yaowu_id) SELECT y1.id, y2.id FROM yaowu y1, yaowu y2 WHERE y1.iri='Caowu' AND y2.iri='Gualoushi';
INSERT INTO shibafan (yaowu_id, related_yaowu_id) SELECT y1.id, y2.id FROM yaowu y1, yaowu y2 WHERE y1.iri='Caowu' AND y2.iri='Gualougen';
INSERT INTO shibafan (yaowu_id, related_yaowu_id) SELECT y1.id, y2.id FROM yaowu y1, yaowu y2 WHERE y1.iri='Caowu' AND y2.iri='Beimu';
INSERT INTO shibafan (yaowu_id, related_yaowu_id) SELECT y1.id, y2.id FROM yaowu y1, yaowu y2 WHERE y1.iri='Caowu' AND y2.iri='Bailian';
INSERT INTO shibafan (yaowu_id, related_yaowu_id) SELECT y1.id, y2.id FROM yaowu y1, yaowu y2 WHERE y1.iri='Caowu' AND y2.iri='Baiji';
INSERT INTO shibafan (yaowu_id, related_yaowu_id) SELECT y1.id, y2.id FROM yaowu y1, yaowu y2 WHERE y1.iri='Gancao' AND y2.iri='Gansui';
INSERT INTO shibafan (yaowu_id, related_yaowu_id) SELECT y1.id, y2.id FROM yaowu y1, yaowu y2 WHERE y1.iri='Gancao' AND y2.iri='Daji';
INSERT INTO shibafan (yaowu_id, related_yaowu_id) SELECT y1.id, y2.id FROM yaowu y1, yaowu y2 WHERE y1.iri='Gancao' AND y2.iri='Haizao';
INSERT INTO shibafan (yaowu_id, related_yaowu_id) SELECT y1.id, y2.id FROM yaowu y1, yaowu y2 WHERE y1.iri='Gancao' AND y2.iri='Yuanhua';
INSERT INTO shibafan (yaowu_id, related_yaowu_id) SELECT y1.id, y2.id FROM yaowu y1, yaowu y2 WHERE y1.iri='Lilu' AND y2.iri='Renshen';
INSERT INTO shibafan (yaowu_id, related_yaowu_id) SELECT y1.id, y2.id FROM yaowu y1, yaowu y2 WHERE y1.iri='Lilu' AND y2.iri='Shashen';
INSERT INTO shibafan (yaowu_id, related_yaowu_id) SELECT y1.id, y2.id FROM yaowu y1, yaowu y2 WHERE y1.iri='Lilu' AND y2.iri='Danshen';
INSERT INTO shibafan (yaowu_id, related_yaowu_id) SELECT y1.id, y2.id FROM yaowu y1, yaowu y2 WHERE y1.iri='Lilu' AND y2.iri='Xuanshen';
INSERT INTO shibafan (yaowu_id, related_yaowu_id) SELECT y1.id, y2.id FROM yaowu y1, yaowu y2 WHERE y1.iri='Lilu' AND y2.iri='Kushen';
INSERT INTO shibafan (yaowu_id, related_yaowu_id) SELECT y1.id, y2.id FROM yaowu y1, yaowu y2 WHERE y1.iri='Lilu' AND y2.iri='Xixin';
INSERT INTO shibafan (yaowu_id, related_yaowu_id) SELECT y1.id, y2.id FROM yaowu y1, yaowu y2 WHERE y1.iri='Lilu' AND y2.iri='Shaoyao';

-- ---------- 插入十九畏关系 ----------
INSERT INTO shijiuwei (yaowu_id, related_yaowu_id) SELECT y1.id, y2.id FROM yaowu y1, yaowu y2 WHERE y1.iri='Liuhuang' AND y2.iri='Mangxiao';
INSERT INTO shijiuwei (yaowu_id, related_yaowu_id) SELECT y1.id, y2.id FROM yaowu y1, yaowu y2 WHERE y1.iri='Shuiyin' AND y2.iri='Pishuang';
INSERT INTO shijiuwei (yaowu_id, related_yaowu_id) SELECT y1.id, y2.id FROM yaowu y1, yaowu y2 WHERE y1.iri='Langdu' AND y2.iri='Mituoseng';
INSERT INTO shijiuwei (yaowu_id, related_yaowu_id) SELECT y1.id, y2.id FROM yaowu y1, yaowu y2 WHERE y1.iri='Badou' AND y2.iri='Qianniuzi';
INSERT INTO shijiuwei (yaowu_id, related_yaowu_id) SELECT y1.id, y2.id FROM yaowu y1, yaowu y2 WHERE y1.iri='Dingxiang' AND y2.iri='Yujin';
INSERT INTO shijiuwei (yaowu_id, related_yaowu_id) SELECT y1.id, y2.id FROM yaowu y1, yaowu y2 WHERE y1.iri='Mangxiao' AND y2.iri='Sanleng';
INSERT INTO shijiuwei (yaowu_id, related_yaowu_id) SELECT y1.id, y2.id FROM yaowu y1, yaowu y2 WHERE y1.iri='Chuanwu' AND y2.iri='Xijiao';
INSERT INTO shijiuwei (yaowu_id, related_yaowu_id) SELECT y1.id, y2.id FROM yaowu y1, yaowu y2 WHERE y1.iri='Caowu' AND y2.iri='Xijiao';
INSERT INTO shijiuwei (yaowu_id, related_yaowu_id) SELECT y1.id, y2.id FROM yaowu y1, yaowu y2 WHERE y1.iri='Renshen' AND y2.iri='Wulingzhi';
INSERT INTO shijiuwei (yaowu_id, related_yaowu_id) SELECT y1.id, y2.id FROM yaowu y1, yaowu y2 WHERE y1.iri='Rougui' AND y2.iri='Chishizhi';

-- ========== 完成 ==========