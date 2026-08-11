USE tcmdb;

-- ============================================================
-- 第一步：安全补全 formula 父表记录
-- 使用 INSERT IGNORE 避免主键冲突，已存在的自动跳过
-- ============================================================
INSERT IGNORE INTO formula (uri, label, category_uri) VALUES
('http://www.tcm-classics.org/fangji#GuiZhiTang', '桂枝汤', 'http://www.tcm-classics.org/fangji_category#ShangHan'),
('http://www.tcm-classics.org/fangji#GuiZhiJiaGeGenTang', '桂枝加葛根汤', 'http://www.tcm-classics.org/fangji_category#ShangHan'),
('http://www.tcm-classics.org/fangji#GuiZhiJiaHouPoXingRenTang', '桂枝加厚朴杏子汤', 'http://www.tcm-classics.org/fangji_category#ShangHan'),
('http://www.tcm-classics.org/fangji#GuiZhiJiaFuZiTang', '桂枝加附子汤', 'http://www.tcm-classics.org/fangji_category#ShangHan'),
('http://www.tcm-classics.org/fangji#GuiZhiQuShaoYaoTang', '桂枝去芍药汤', 'http://www.tcm-classics.org/fangji_category#ShangHan'),
('http://www.tcm-classics.org/fangji#GuiZhiQuShaoYaoJiaFuZiTang', '桂枝去芍药加附子汤', 'http://www.tcm-classics.org/fangji_category#ShangHan'),
('http://www.tcm-classics.org/fangji#GuiZhiXinJiaTang', '桂枝新加汤', 'http://www.tcm-classics.org/fangji_category#ShangHan'),
('http://www.tcm-classics.org/fangji#GuiZhiGanCaoTang', '桂枝甘草汤', 'http://www.tcm-classics.org/fangji_category#ShangHan'),
('http://www.tcm-classics.org/fangji#FuLingGuiZhiBaiZhuGanCaoTang', '茯苓桂枝白术甘草汤', 'http://www.tcm-classics.org/fangji_category#ShangHan'),
('http://www.tcm-classics.org/fangji#ShaoYaoGanCaoTang', '芍药甘草汤', 'http://www.tcm-classics.org/fangji_category#ShangHan'),
('http://www.tcm-classics.org/fangji#ShaoYaoGanCaoFuZiTang', '芍药甘草附子汤', 'http://www.tcm-classics.org/fangji_category#ShangHan'),
('http://www.tcm-classics.org/fangji#GuiZhiFuZiTang', '桂枝附子汤', 'http://www.tcm-classics.org/fangji_category#ShangHan'),
('http://www.tcm-classics.org/fangji#BaiZhuFuZiTang', '白术附子汤', 'http://www.tcm-classics.org/fangji_category#ShangHan'),
('http://www.tcm-classics.org/fangji#GanCaoFuZiTang', '甘草附子汤', 'http://www.tcm-classics.org/fangji_category#ShangHan'),
('http://www.tcm-classics.org/fangji#GuiZhiJiaLongGuMuLiTang', '桂枝加龙骨牡蛎汤', 'http://www.tcm-classics.org/fangji_category#JinKui'),
('http://www.tcm-classics.org/fangji#HuangQiJianZhongTang', '黄芪建中汤', 'http://www.tcm-classics.org/fangji_category#JinKui'),
('http://www.tcm-classics.org/fangji#XiaoJianZhongTang', '小建中汤', 'http://www.tcm-classics.org/fangji_category#ShangHan'),
('http://www.tcm-classics.org/fangji#DaJianZhongTang', '大建中汤', 'http://www.tcm-classics.org/fangji_category#JinKui'),
('http://www.tcm-classics.org/fangji#MaHuangTang', '麻黄汤', 'http://www.tcm-classics.org/fangji_category#ShangHan'),
('http://www.tcm-classics.org/fangji#DaQingLongTang', '大青龙汤', 'http://www.tcm-classics.org/fangji_category#ShangHan'),
('http://www.tcm-classics.org/fangji#XiaoQingLongTang', '小青龙汤', 'http://www.tcm-classics.org/fangji_category#ShangHan'),
('http://www.tcm-classics.org/fangji#MaHuangXingRenGanCaoShiGaoTang', '麻杏石甘汤', 'http://www.tcm-classics.org/fangji_category#ShangHan'),
('http://www.tcm-classics.org/fangji#MaHuangFuZiXiXinTang', '麻黄附子细辛汤', 'http://www.tcm-classics.org/fangji_category#ShangHan'),
('http://www.tcm-classics.org/fangji#MaHuangFuZiGanCaoTang', '麻黄附子甘草汤', 'http://www.tcm-classics.org/fangji_category#ShangHan'),
('http://www.tcm-classics.org/fangji#MaHuangJiaZhuTang', '麻黄加术汤', 'http://www.tcm-classics.org/fangji_category#JinKui'),
('http://www.tcm-classics.org/fangji#MaHuangYiYiGanCaoTang', '麻黄薏苡甘草汤', 'http://www.tcm-classics.org/fangji_category#JinKui'),
('http://www.tcm-classics.org/fangji#GeGenTang', '葛根汤', 'http://www.tcm-classics.org/fangji_category#ShangHan'),
('http://www.tcm-classics.org/fangji#GeGenJiaBanXiaTang', '葛根加半夏汤', 'http://www.tcm-classics.org/fangji_category#ShangHan'),
('http://www.tcm-classics.org/fangji#GeGenHuangQinHuangLianTang', '葛根黄芩黄连汤', 'http://www.tcm-classics.org/fangji_category#ShangHan'),
('http://www.tcm-classics.org/fangji#XiaoChaiHuTang', '小柴胡汤', 'http://www.tcm-classics.org/fangji_category#ShangHan'),
('http://www.tcm-classics.org/fangji#DaChaiHuTang', '大柴胡汤', 'http://www.tcm-classics.org/fangji_category#ShangHan'),
('http://www.tcm-classics.org/fangji#ChaiHuGuiZhiTang', '柴胡桂枝汤', 'http://www.tcm-classics.org/fangji_category#ShangHan'),
('http://www.tcm-classics.org/fangji#ChaiHuJiaMangXiaoTang', '柴胡加芒硝汤', 'http://www.tcm-classics.org/fangji_category#ShangHan'),
('http://www.tcm-classics.org/fangji#ChaiHuGuiZhiGanJiangTang', '柴胡桂枝干姜汤', 'http://www.tcm-classics.org/fangji_category#ShangHan'),
('http://www.tcm-classics.org/fangji#ChaiHuJiaLongGuMuLiTang', '柴胡加龙骨牡蛎汤', 'http://www.tcm-classics.org/fangji_category#ShangHan'),
('http://www.tcm-classics.org/fangji#SiNiSan', '四逆散', 'http://www.tcm-classics.org/fangji_category#ShangHan'),
('http://www.tcm-classics.org/fangji#BaiHuTang', '白虎汤', 'http://www.tcm-classics.org/fangji_category#ShangHan'),
('http://www.tcm-classics.org/fangji#BaiHuJiaRenShenTang', '白虎加人参汤', 'http://www.tcm-classics.org/fangji_category#ShangHan'),
('http://www.tcm-classics.org/fangji#ZhuYeShiGaoTang', '竹叶石膏汤', 'http://www.tcm-classics.org/fangji_category#ShangHan'),
('http://www.tcm-classics.org/fangji#DaChengQiTang', '大承气汤', 'http://www.tcm-classics.org/fangji_category#ShangHan'),
('http://www.tcm-classics.org/fangji#XiaoChengQiTang', '小承气汤', 'http://www.tcm-classics.org/fangji_category#ShangHan'),
('http://www.tcm-classics.org/fangji#TiaoWeiChengQiTang', '调胃承气汤', 'http://www.tcm-classics.org/fangji_category#ShangHan'),
('http://www.tcm-classics.org/fangji#TaoHeChengQiTang', '桃核承气汤', 'http://www.tcm-classics.org/fangji_category#ShangHan'),
('http://www.tcm-classics.org/fangji#DiDangTang', '抵当汤', 'http://www.tcm-classics.org/fangji_category#ShangHan'),
('http://www.tcm-classics.org/fangji#LiZhongTang', '理中汤', 'http://www.tcm-classics.org/fangji_category#ShangHan'),
('http://www.tcm-classics.org/fangji#FuZiLiZhongTang', '附子理中汤', 'http://www.tcm-classics.org/fangji_category#ShangHan'),
('http://www.tcm-classics.org/fangji#WuZhuYuTang', '吴茱萸汤', 'http://www.tcm-classics.org/fangji_category#ShangHan'),
('http://www.tcm-classics.org/fangji#SiNiTang', '四逆汤', 'http://www.tcm-classics.org/fangji_category#ShangHan'),
('http://www.tcm-classics.org/fangji#TongMaiSiNiTang', '通脉四逆汤', 'http://www.tcm-classics.org/fangji_category#ShangHan'),
('http://www.tcm-classics.org/fangji#BaiTongTang', '白通汤', 'http://www.tcm-classics.org/fangji_category#ShangHan'),
('http://www.tcm-classics.org/fangji#BaiTongJiaZhuDanZhiTang', '白通加猪胆汁汤', 'http://www.tcm-classics.org/fangji_category#ShangHan'),
('http://www.tcm-classics.org/fangji#ZhenWuTang', '真武汤', 'http://www.tcm-classics.org/fangji_category#ShangHan'),
('http://www.tcm-classics.org/fangji#HuangLianEJiaoTang', '黄连阿胶汤', 'http://www.tcm-classics.org/fangji_category#ShangHan'),
('http://www.tcm-classics.org/fangji#WuMeiWan', '乌梅丸', 'http://www.tcm-classics.org/fangji_category#ShangHan'),
('http://www.tcm-classics.org/fangji#DangGuiSiNiTang', '当归四逆汤', 'http://www.tcm-classics.org/fangji_category#ShangHan'),
('http://www.tcm-classics.org/fangji#DangGuiSiNiJiaWuZhuYuShengJiangTang', '当归四逆加吴茱萸生姜汤', 'http://www.tcm-classics.org/fangji_category#ShangHan'),
('http://www.tcm-classics.org/fangji#BaiTouWengTang', '白头翁汤', 'http://www.tcm-classics.org/fangji_category#ShangHan'),
('http://www.tcm-classics.org/fangji#GanJiangHuangQinHuangLianRenShenTang', '干姜黄芩黄连人参汤', 'http://www.tcm-classics.org/fangji_category#ShangHan'),
('http://www.tcm-classics.org/fangji#BanXiaXieXinTang', '半夏泻心汤', 'http://www.tcm-classics.org/fangji_category#ShangHan'),
('http://www.tcm-classics.org/fangji#ShengJiangXieXinTang', '生姜泻心汤', 'http://www.tcm-classics.org/fangji_category#ShangHan'),
('http://www.tcm-classics.org/fangji#GanCaoXieXinTang', '甘草泻心汤', 'http://www.tcm-classics.org/fangji_category#ShangHan'),
('http://www.tcm-classics.org/fangji#DaHuangHuangLianXieXinTang', '大黄黄连泻心汤', 'http://www.tcm-classics.org/fangji_category#ShangHan'),
('http://www.tcm-classics.org/fangji#FuZiXieXinTang', '附子泻心汤', 'http://www.tcm-classics.org/fangji_category#ShangHan'),
('http://www.tcm-classics.org/fangji#WuLingSan', '五苓散', 'http://www.tcm-classics.org/fangji_category#ShangHan'),
('http://www.tcm-classics.org/fangji#FuLingGanCaoTang', '茯苓甘草汤', 'http://www.tcm-classics.org/fangji_category#ShangHan'),
('http://www.tcm-classics.org/fangji#XuanFuDaiZheTang', '旋覆代赭汤', 'http://www.tcm-classics.org/fangji_category#ShangHan'),
('http://www.tcm-classics.org/fangji#HouPoShengJiangBanXiaGanCaoRenShenTang', '厚朴生姜半夏甘草人参汤', 'http://www.tcm-classics.org/fangji_category#ShangHan'),
('http://www.tcm-classics.org/fangji#ZhiZiChiTang', '栀子豉汤', 'http://www.tcm-classics.org/fangji_category#ShangHan'),
('http://www.tcm-classics.org/fangji#ZhiZiGanCaoTang', '栀子甘草汤', 'http://www.tcm-classics.org/fangji_category#ShangHan'),
('http://www.tcm-classics.org/fangji#ZhiZiShengJiangTang', '栀子生姜汤', 'http://www.tcm-classics.org/fangji_category#ShangHan'),
('http://www.tcm-classics.org/fangji#ZhiZiHouPoTang', '栀子厚朴汤', 'http://www.tcm-classics.org/fangji_category#ShangHan'),
('http://www.tcm-classics.org/fangji#GuaLouXieBaiBaiJiuTang', '瓜蒌薤白白酒汤', 'http://www.tcm-classics.org/fangji_category#JinKui'),
('http://www.tcm-classics.org/fangji#GuaLouXieBaiBanXiaTang', '瓜蒌薤白半夏汤', 'http://www.tcm-classics.org/fangji_category#JinKui'),
('http://www.tcm-classics.org/fangji#ZhiShiXieBaiGuiZhiTang', '枳实薤白桂枝汤', 'http://www.tcm-classics.org/fangji_category#JinKui'),
('http://www.tcm-classics.org/fangji#HouPoQiWuTang', '厚朴七物汤', 'http://www.tcm-classics.org/fangji_category#JinKui'),
('http://www.tcm-classics.org/fangji#GuiZhiShaoYaoZhiMuTang', '桂枝芍药知母汤', 'http://www.tcm-classics.org/fangji_category#JinKui'),
('http://www.tcm-classics.org/fangji#WuTouTang', '乌头汤', 'http://www.tcm-classics.org/fangji_category#JinKui'),
('http://www.tcm-classics.org/fangji#GuiZhiFuLingWan', '桂枝茯苓丸', 'http://www.tcm-classics.org/fangji_category#JinKui'),
('http://www.tcm-classics.org/fangji#WenJingTang', '温经汤', 'http://www.tcm-classics.org/fangji_category#JinKui'),
('http://www.tcm-classics.org/fangji#JiaoAiTang', '胶艾汤', 'http://www.tcm-classics.org/fangji_category#JinKui'),
('http://www.tcm-classics.org/fangji#DangGuiShaoYaoSan', '当归芍药散', 'http://www.tcm-classics.org/fangji_category#JinKui'),
('http://www.tcm-classics.org/fangji#BaiHeDiHuangTang', '百合地黄汤', 'http://www.tcm-classics.org/fangji_category#JinKui'),
('http://www.tcm-classics.org/fangji#SuanZaoRenTang', '酸枣仁汤', 'http://www.tcm-classics.org/fangji_category#JinKui'),
('http://www.tcm-classics.org/fangji#HuangTuTang', '黄土汤', 'http://www.tcm-classics.org/fangji_category#JinKui'),
('http://www.tcm-classics.org/fangji#ChiXiaoDouDangGuiSan', '赤小豆当归散', 'http://www.tcm-classics.org/fangji_category#JinKui'),
('http://www.tcm-classics.org/fangji#MaiMenDongTang', '麦门冬汤', 'http://www.tcm-classics.org/fangji_category#JinKui'),
('http://www.tcm-classics.org/fangji#SheGanMaHuangTang', '射干麻黄汤', 'http://www.tcm-classics.org/fangji_category#JinKui'),
('http://www.tcm-classics.org/fangji#HouPoMaHuangTang', '厚朴麻黄汤', 'http://www.tcm-classics.org/fangji_category#JinKui'),
('http://www.tcm-classics.org/fangji#ZeQiTang', '泽漆汤', 'http://www.tcm-classics.org/fangji_category#JinKui'),
('http://www.tcm-classics.org/fangji#TianWuTouChiShiMiSan', '头风摩散', 'http://www.tcm-classics.org/fangji_category#JinKui');

-- ============================================================
-- 第二步：插入 formula_herb 子表数据
-- ★ 已修正：鸡子黄URI从 JiZiHuang 改为 HuangLianEJiaoTang_JiZiHuang
-- ============================================================
INSERT INTO formula_herb (formula_uri, herb_uri, dosage_text, processing_in_formula, sort_order) VALUES
-- ===== 桂枝汤类（18方）=====
-- 桂枝汤
('http://www.tcm-classics.org/fangji#GuiZhiTang','http://www.tcm-classics.org/yaowu#GuiZhi','三两','去皮',1),
('http://www.tcm-classics.org/fangji#GuiZhiTang','http://www.tcm-classics.org/yaowu#ShaoYao','三两',NULL,2),
('http://www.tcm-classics.org/fangji#GuiZhiTang','http://www.tcm-classics.org/yaowu#GanCao','二两','炙',3),
('http://www.tcm-classics.org/fangji#GuiZhiTang','http://www.tcm-classics.org/yaowu#ShengJiang','三两','切',4),
('http://www.tcm-classics.org/fangji#GuiZhiTang','http://www.tcm-classics.org/yaowu#DaZao','十二枚','擘',5),

-- 桂枝加葛根汤
('http://www.tcm-classics.org/fangji#GuiZhiJiaGeGenTang','http://www.tcm-classics.org/yaowu#GeGen','四两',NULL,1),
('http://www.tcm-classics.org/fangji#GuiZhiJiaGeGenTang','http://www.tcm-classics.org/yaowu#GuiZhi','三两','去皮',2),
('http://www.tcm-classics.org/fangji#GuiZhiJiaGeGenTang','http://www.tcm-classics.org/yaowu#ShaoYao','三两',NULL,3),
('http://www.tcm-classics.org/fangji#GuiZhiJiaGeGenTang','http://www.tcm-classics.org/yaowu#GanCao','二两','炙',4),
('http://www.tcm-classics.org/fangji#GuiZhiJiaGeGenTang','http://www.tcm-classics.org/yaowu#ShengJiang','三两','切',5),
('http://www.tcm-classics.org/fangji#GuiZhiJiaGeGenTang','http://www.tcm-classics.org/yaowu#DaZao','十二枚','擘',6),

-- 桂枝加厚朴杏子汤
('http://www.tcm-classics.org/fangji#GuiZhiJiaHouPoXingRenTang','http://www.tcm-classics.org/yaowu#GuiZhi','三两','去皮',1),
('http://www.tcm-classics.org/fangji#GuiZhiJiaHouPoXingRenTang','http://www.tcm-classics.org/yaowu#ShaoYao','三两',NULL,2),
('http://www.tcm-classics.org/fangji#GuiZhiJiaHouPoXingRenTang','http://www.tcm-classics.org/yaowu#GanCao','二两','炙',3),
('http://www.tcm-classics.org/fangji#GuiZhiJiaHouPoXingRenTang','http://www.tcm-classics.org/yaowu#ShengJiang','三两','切',4),
('http://www.tcm-classics.org/fangji#GuiZhiJiaHouPoXingRenTang','http://www.tcm-classics.org/yaowu#DaZao','十二枚','擘',5),
('http://www.tcm-classics.org/fangji#GuiZhiJiaHouPoXingRenTang','http://www.tcm-classics.org/yaowu#HouPo','二两','炙去皮',6),
('http://www.tcm-classics.org/fangji#GuiZhiJiaHouPoXingRenTang','http://www.tcm-classics.org/yaowu#XingRen','五十个','去皮尖',7),

-- 桂枝加附子汤
('http://www.tcm-classics.org/fangji#GuiZhiJiaFuZiTang','http://www.tcm-classics.org/yaowu#GuiZhi','三两','去皮',1),
('http://www.tcm-classics.org/fangji#GuiZhiJiaFuZiTang','http://www.tcm-classics.org/yaowu#ShaoYao','三两',NULL,2),
('http://www.tcm-classics.org/fangji#GuiZhiJiaFuZiTang','http://www.tcm-classics.org/yaowu#GanCao','三两','炙',3),
('http://www.tcm-classics.org/fangji#GuiZhiJiaFuZiTang','http://www.tcm-classics.org/yaowu#ShengJiang','三两','切',4),
('http://www.tcm-classics.org/fangji#GuiZhiJiaFuZiTang','http://www.tcm-classics.org/yaowu#DaZao','十二枚','擘',5),
('http://www.tcm-classics.org/fangji#GuiZhiJiaFuZiTang','http://www.tcm-classics.org/yaowu#FuZi','一枚','炮去皮破八片',6),

-- 桂枝去芍药汤
('http://www.tcm-classics.org/fangji#GuiZhiQuShaoYaoTang','http://www.tcm-classics.org/yaowu#GuiZhi','三两','去皮',1),
('http://www.tcm-classics.org/fangji#GuiZhiQuShaoYaoTang','http://www.tcm-classics.org/yaowu#GanCao','二两','炙',2),
('http://www.tcm-classics.org/fangji#GuiZhiQuShaoYaoTang','http://www.tcm-classics.org/yaowu#ShengJiang','三两','切',3),
('http://www.tcm-classics.org/fangji#GuiZhiQuShaoYaoTang','http://www.tcm-classics.org/yaowu#DaZao','十二枚','擘',4),

-- 桂枝去芍药加附子汤
('http://www.tcm-classics.org/fangji#GuiZhiQuShaoYaoJiaFuZiTang','http://www.tcm-classics.org/yaowu#GuiZhi','三两','去皮',1),
('http://www.tcm-classics.org/fangji#GuiZhiQuShaoYaoJiaFuZiTang','http://www.tcm-classics.org/yaowu#GanCao','二两','炙',2),
('http://www.tcm-classics.org/fangji#GuiZhiQuShaoYaoJiaFuZiTang','http://www.tcm-classics.org/yaowu#ShengJiang','三两','切',3),
('http://www.tcm-classics.org/fangji#GuiZhiQuShaoYaoJiaFuZiTang','http://www.tcm-classics.org/yaowu#DaZao','十二枚','擘',4),
('http://www.tcm-classics.org/fangji#GuiZhiQuShaoYaoJiaFuZiTang','http://www.tcm-classics.org/yaowu#FuZi','一枚','炮去皮破八片',5),

-- 桂枝新加汤
('http://www.tcm-classics.org/fangji#GuiZhiXinJiaTang','http://www.tcm-classics.org/yaowu#GuiZhi','三两','去皮',1),
('http://www.tcm-classics.org/fangji#GuiZhiXinJiaTang','http://www.tcm-classics.org/yaowu#ShaoYao','四两',NULL,2),
('http://www.tcm-classics.org/fangji#GuiZhiXinJiaTang','http://www.tcm-classics.org/yaowu#GanCao','二两','炙',3),
('http://www.tcm-classics.org/fangji#GuiZhiXinJiaTang','http://www.tcm-classics.org/yaowu#RenShen','三两',NULL,4),
('http://www.tcm-classics.org/fangji#GuiZhiXinJiaTang','http://www.tcm-classics.org/yaowu#DaZao','十二枚','擘',5),
('http://www.tcm-classics.org/fangji#GuiZhiXinJiaTang','http://www.tcm-classics.org/yaowu#ShengJiang','四两','切',6),

-- 桂枝甘草汤
('http://www.tcm-classics.org/fangji#GuiZhiGanCaoTang','http://www.tcm-classics.org/yaowu#GuiZhi','四两','去皮',1),
('http://www.tcm-classics.org/fangji#GuiZhiGanCaoTang','http://www.tcm-classics.org/yaowu#GanCao','二两','炙',2),

-- 茯苓桂枝白术甘草汤
('http://www.tcm-classics.org/fangji#FuLingGuiZhiBaiZhuGanCaoTang','http://www.tcm-classics.org/yaowu#FuLing','四两',NULL,1),
('http://www.tcm-classics.org/fangji#FuLingGuiZhiBaiZhuGanCaoTang','http://www.tcm-classics.org/yaowu#GuiZhi','三两','去皮',2),
('http://www.tcm-classics.org/fangji#FuLingGuiZhiBaiZhuGanCaoTang','http://www.tcm-classics.org/yaowu#BaiZhu','二两',NULL,3),
('http://www.tcm-classics.org/fangji#FuLingGuiZhiBaiZhuGanCaoTang','http://www.tcm-classics.org/yaowu#GanCao','二两','炙',4),

-- 芍药甘草汤
('http://www.tcm-classics.org/fangji#ShaoYaoGanCaoTang','http://www.tcm-classics.org/yaowu#ShaoYao','四两',NULL,1),
('http://www.tcm-classics.org/fangji#ShaoYaoGanCaoTang','http://www.tcm-classics.org/yaowu#GanCao','四两','炙',2),

-- 芍药甘草附子汤
('http://www.tcm-classics.org/fangji#ShaoYaoGanCaoFuZiTang','http://www.tcm-classics.org/yaowu#ShaoYao','三两',NULL,1),
('http://www.tcm-classics.org/fangji#ShaoYaoGanCaoFuZiTang','http://www.tcm-classics.org/yaowu#GanCao','三两','炙',2),
('http://www.tcm-classics.org/fangji#ShaoYaoGanCaoFuZiTang','http://www.tcm-classics.org/yaowu#FuZi','一枚','炮去皮破八片',3),

-- 桂枝附子汤
('http://www.tcm-classics.org/fangji#GuiZhiFuZiTang','http://www.tcm-classics.org/yaowu#GuiZhi','四两','去皮',1),
('http://www.tcm-classics.org/fangji#GuiZhiFuZiTang','http://www.tcm-classics.org/yaowu#FuZi','三枚','炮去皮破八片',2),
('http://www.tcm-classics.org/fangji#GuiZhiFuZiTang','http://www.tcm-classics.org/yaowu#ShengJiang','三两','切',3),
('http://www.tcm-classics.org/fangji#GuiZhiFuZiTang','http://www.tcm-classics.org/yaowu#DaZao','十二枚','擘',4),
('http://www.tcm-classics.org/fangji#GuiZhiFuZiTang','http://www.tcm-classics.org/yaowu#GanCao','二两','炙',5),

-- 白术附子汤
('http://www.tcm-classics.org/fangji#BaiZhuFuZiTang','http://www.tcm-classics.org/yaowu#FuZi','三枚','炮去皮破八片',1),
('http://www.tcm-classics.org/fangji#BaiZhuFuZiTang','http://www.tcm-classics.org/yaowu#BaiZhu','四两',NULL,2),
('http://www.tcm-classics.org/fangji#BaiZhuFuZiTang','http://www.tcm-classics.org/yaowu#ShengJiang','三两','切',3),
('http://www.tcm-classics.org/fangji#BaiZhuFuZiTang','http://www.tcm-classics.org/yaowu#DaZao','十二枚','擘',4),
('http://www.tcm-classics.org/fangji#BaiZhuFuZiTang','http://www.tcm-classics.org/yaowu#GanCao','二两','炙',5),

-- 甘草附子汤
('http://www.tcm-classics.org/fangji#GanCaoFuZiTang','http://www.tcm-classics.org/yaowu#GanCao','二两','炙',1),
('http://www.tcm-classics.org/fangji#GanCaoFuZiTang','http://www.tcm-classics.org/yaowu#FuZi','二枚','炮去皮破八片',2),
('http://www.tcm-classics.org/fangji#GanCaoFuZiTang','http://www.tcm-classics.org/yaowu#BaiZhu','二两',NULL,3),
('http://www.tcm-classics.org/fangji#GanCaoFuZiTang','http://www.tcm-classics.org/yaowu#GuiZhi','四两','去皮',4),

-- 桂枝加龙骨牡蛎汤
('http://www.tcm-classics.org/fangji#GuiZhiJiaLongGuMuLiTang','http://www.tcm-classics.org/yaowu#GuiZhi','三两','去皮',1),
('http://www.tcm-classics.org/fangji#GuiZhiJiaLongGuMuLiTang','http://www.tcm-classics.org/yaowu#ShaoYao','三两',NULL,2),
('http://www.tcm-classics.org/fangji#GuiZhiJiaLongGuMuLiTang','http://www.tcm-classics.org/yaowu#ShengJiang','三两','切',3),
('http://www.tcm-classics.org/fangji#GuiZhiJiaLongGuMuLiTang','http://www.tcm-classics.org/yaowu#GanCao','二两','炙',4),
('http://www.tcm-classics.org/fangji#GuiZhiJiaLongGuMuLiTang','http://www.tcm-classics.org/yaowu#DaZao','十二枚','擘',5),
('http://www.tcm-classics.org/fangji#GuiZhiJiaLongGuMuLiTang','http://www.tcm-classics.org/yaowu#LongGu','三两',NULL,6),
('http://www.tcm-classics.org/fangji#GuiZhiJiaLongGuMuLiTang','http://www.tcm-classics.org/yaowu#MuLi','三两','熬',7),

-- 黄芪建中汤
('http://www.tcm-classics.org/fangji#HuangQiJianZhongTang','http://www.tcm-classics.org/yaowu#GuiZhi','三两','去皮',1),
('http://www.tcm-classics.org/fangji#HuangQiJianZhongTang','http://www.tcm-classics.org/yaowu#GanCao','三两','炙',2),
('http://www.tcm-classics.org/fangji#HuangQiJianZhongTang','http://www.tcm-classics.org/yaowu#ShaoYao','六两',NULL,3),
('http://www.tcm-classics.org/fangji#HuangQiJianZhongTang','http://www.tcm-classics.org/yaowu#ShengJiang','三两','切',4),
('http://www.tcm-classics.org/fangji#HuangQiJianZhongTang','http://www.tcm-classics.org/yaowu#DaZao','十二枚','擘',5),
('http://www.tcm-classics.org/fangji#HuangQiJianZhongTang','http://www.tcm-classics.org/yaowu#YiTang','一升',NULL,6),
('http://www.tcm-classics.org/fangji#HuangQiJianZhongTang','http://www.tcm-classics.org/yaowu#HuangQi','一两半',NULL,7),

-- 小建中汤
('http://www.tcm-classics.org/fangji#XiaoJianZhongTang','http://www.tcm-classics.org/yaowu#GuiZhi','三两','去皮',1),
('http://www.tcm-classics.org/fangji#XiaoJianZhongTang','http://www.tcm-classics.org/yaowu#GanCao','二两','炙',2),
('http://www.tcm-classics.org/fangji#XiaoJianZhongTang','http://www.tcm-classics.org/yaowu#ShaoYao','六两',NULL,3),
('http://www.tcm-classics.org/fangji#XiaoJianZhongTang','http://www.tcm-classics.org/yaowu#ShengJiang','三两','切',4),
('http://www.tcm-classics.org/fangji#XiaoJianZhongTang','http://www.tcm-classics.org/yaowu#DaZao','十二枚','擘',5),
('http://www.tcm-classics.org/fangji#XiaoJianZhongTang','http://www.tcm-classics.org/yaowu#YiTang','一升',NULL,6),

-- 大建中汤
('http://www.tcm-classics.org/fangji#DaJianZhongTang','http://www.tcm-classics.org/yaowu#ShuJiao','二合','炒去汗',1),
('http://www.tcm-classics.org/fangji#DaJianZhongTang','http://www.tcm-classics.org/yaowu#GanJiang','四两',NULL,2),
('http://www.tcm-classics.org/fangji#DaJianZhongTang','http://www.tcm-classics.org/yaowu#RenShen','二两',NULL,3),
('http://www.tcm-classics.org/fangji#DaJianZhongTang','http://www.tcm-classics.org/yaowu#YiTang','一升',NULL,4),

-- ===== 麻黄汤类（8方）=====
-- 麻黄汤
('http://www.tcm-classics.org/fangji#MaHuangTang','http://www.tcm-classics.org/yaowu#MaHuang','三两','去节',1),
('http://www.tcm-classics.org/fangji#MaHuangTang','http://www.tcm-classics.org/yaowu#GuiZhi','二两','去皮',2),
('http://www.tcm-classics.org/fangji#MaHuangTang','http://www.tcm-classics.org/yaowu#GanCao','一两','炙',3),
('http://www.tcm-classics.org/fangji#MaHuangTang','http://www.tcm-classics.org/yaowu#XingRen','七十个','去皮尖',4),

-- 大青龙汤
('http://www.tcm-classics.org/fangji#DaQingLongTang','http://www.tcm-classics.org/yaowu#MaHuang','六两','去节',1),
('http://www.tcm-classics.org/fangji#DaQingLongTang','http://www.tcm-classics.org/yaowu#GuiZhi','二两','去皮',2),
('http://www.tcm-classics.org/fangji#DaQingLongTang','http://www.tcm-classics.org/yaowu#GanCao','二两','炙',3),
('http://www.tcm-classics.org/fangji#DaQingLongTang','http://www.tcm-classics.org/yaowu#XingRen','四十枚','去皮尖',4),
('http://www.tcm-classics.org/fangji#DaQingLongTang','http://www.tcm-classics.org/yaowu#ShengJiang','三两','切',5),
('http://www.tcm-classics.org/fangji#DaQingLongTang','http://www.tcm-classics.org/yaowu#DaZao','十枚','擘',6),
('http://www.tcm-classics.org/fangji#DaQingLongTang','http://www.tcm-classics.org/yaowu#ShiGao','如鸡子大','碎',7),

-- 小青龙汤
('http://www.tcm-classics.org/fangji#XiaoQingLongTang','http://www.tcm-classics.org/yaowu#MaHuang','三两','去节',1),
('http://www.tcm-classics.org/fangji#XiaoQingLongTang','http://www.tcm-classics.org/yaowu#ShaoYao','三两',NULL,2),
('http://www.tcm-classics.org/fangji#XiaoQingLongTang','http://www.tcm-classics.org/yaowu#XiXin','三两',NULL,3),
('http://www.tcm-classics.org/fangji#XiaoQingLongTang','http://www.tcm-classics.org/yaowu#GanJiang','三两',NULL,4),
('http://www.tcm-classics.org/fangji#XiaoQingLongTang','http://www.tcm-classics.org/yaowu#GanCao','三两','炙',5),
('http://www.tcm-classics.org/fangji#XiaoQingLongTang','http://www.tcm-classics.org/yaowu#GuiZhi','三两','去皮',6),
('http://www.tcm-classics.org/fangji#XiaoQingLongTang','http://www.tcm-classics.org/yaowu#WuWeiZi','半升',NULL,7),
('http://www.tcm-classics.org/fangji#XiaoQingLongTang','http://www.tcm-classics.org/yaowu#BanXia','半升','洗',8),

-- 麻杏石甘汤
('http://www.tcm-classics.org/fangji#MaHuangXingRenGanCaoShiGaoTang','http://www.tcm-classics.org/yaowu#MaHuang','四两','去节',1),
('http://www.tcm-classics.org/fangji#MaHuangXingRenGanCaoShiGaoTang','http://www.tcm-classics.org/yaowu#XingRen','五十个','去皮尖',2),
('http://www.tcm-classics.org/fangji#MaHuangXingRenGanCaoShiGaoTang','http://www.tcm-classics.org/yaowu#GanCao','二两','炙',3),
('http://www.tcm-classics.org/fangji#MaHuangXingRenGanCaoShiGaoTang','http://www.tcm-classics.org/yaowu#ShiGao','半斤','碎绵裹',4),

-- 麻黄附子细辛汤
('http://www.tcm-classics.org/fangji#MaHuangFuZiXiXinTang','http://www.tcm-classics.org/yaowu#MaHuang','二两','去节',1),
('http://www.tcm-classics.org/fangji#MaHuangFuZiXiXinTang','http://www.tcm-classics.org/yaowu#XiXin','二两',NULL,2),
('http://www.tcm-classics.org/fangji#MaHuangFuZiXiXinTang','http://www.tcm-classics.org/yaowu#FuZi','一枚','炮去皮破八片',3),

-- 麻黄附子甘草汤
('http://www.tcm-classics.org/fangji#MaHuangFuZiGanCaoTang','http://www.tcm-classics.org/yaowu#MaHuang','二两','去节',1),
('http://www.tcm-classics.org/fangji#MaHuangFuZiGanCaoTang','http://www.tcm-classics.org/yaowu#GanCao','二两','炙',2),
('http://www.tcm-classics.org/fangji#MaHuangFuZiGanCaoTang','http://www.tcm-classics.org/yaowu#FuZi','一枚','炮去皮破八片',3),

-- 麻黄加术汤
('http://www.tcm-classics.org/fangji#MaHuangJiaZhuTang','http://www.tcm-classics.org/yaowu#MaHuang','三两','去节',1),
('http://www.tcm-classics.org/fangji#MaHuangJiaZhuTang','http://www.tcm-classics.org/yaowu#GuiZhi','二两','去皮',2),
('http://www.tcm-classics.org/fangji#MaHuangJiaZhuTang','http://www.tcm-classics.org/yaowu#GanCao','一两','炙',3),
('http://www.tcm-classics.org/fangji#MaHuangJiaZhuTang','http://www.tcm-classics.org/yaowu#XingRen','七十个','去皮尖',4),
('http://www.tcm-classics.org/fangji#MaHuangJiaZhuTang','http://www.tcm-classics.org/yaowu#BaiZhu','四两',NULL,5),

-- 麻黄薏苡甘草汤
('http://www.tcm-classics.org/fangji#MaHuangYiYiGanCaoTang','http://www.tcm-classics.org/yaowu#MaHuang','半两','去节汤泡',1),
('http://www.tcm-classics.org/fangji#MaHuangYiYiGanCaoTang','http://www.tcm-classics.org/yaowu#GanCao','一两','炙',2),
('http://www.tcm-classics.org/fangji#MaHuangYiYiGanCaoTang','http://www.tcm-classics.org/yaowu#YiYiRen','半两',NULL,3),
('http://www.tcm-classics.org/fangji#MaHuangYiYiGanCaoTang','http://www.tcm-classics.org/yaowu#XingRen','十个','去皮尖炒',4),

-- ===== 葛根汤类（3方）=====
-- 葛根汤
('http://www.tcm-classics.org/fangji#GeGenTang','http://www.tcm-classics.org/yaowu#GeGen','四两',NULL,1),
('http://www.tcm-classics.org/fangji#GeGenTang','http://www.tcm-classics.org/yaowu#MaHuang','三两','去节',2),
('http://www.tcm-classics.org/fangji#GeGenTang','http://www.tcm-classics.org/yaowu#GuiZhi','二两','去皮',3),
('http://www.tcm-classics.org/fangji#GeGenTang','http://www.tcm-classics.org/yaowu#ShengJiang','三两','切',4),
('http://www.tcm-classics.org/fangji#GeGenTang','http://www.tcm-classics.org/yaowu#GanCao','二两','炙',5),
('http://www.tcm-classics.org/fangji#GeGenTang','http://www.tcm-classics.org/yaowu#ShaoYao','二两',NULL,6),
('http://www.tcm-classics.org/fangji#GeGenTang','http://www.tcm-classics.org/yaowu#DaZao','十二枚','擘',7),

-- 葛根加半夏汤
('http://www.tcm-classics.org/fangji#GeGenJiaBanXiaTang','http://www.tcm-classics.org/yaowu#GeGen','四两',NULL,1),
('http://www.tcm-classics.org/fangji#GeGenJiaBanXiaTang','http://www.tcm-classics.org/yaowu#MaHuang','三两','去节',2),
('http://www.tcm-classics.org/fangji#GeGenJiaBanXiaTang','http://www.tcm-classics.org/yaowu#GuiZhi','二两','去皮',3),
('http://www.tcm-classics.org/fangji#GeGenJiaBanXiaTang','http://www.tcm-classics.org/yaowu#ShengJiang','三两','切',4),
('http://www.tcm-classics.org/fangji#GeGenJiaBanXiaTang','http://www.tcm-classics.org/yaowu#GanCao','二两','炙',5),
('http://www.tcm-classics.org/fangji#GeGenJiaBanXiaTang','http://www.tcm-classics.org/yaowu#ShaoYao','二两',NULL,6),
('http://www.tcm-classics.org/fangji#GeGenJiaBanXiaTang','http://www.tcm-classics.org/yaowu#DaZao','十二枚','擘',7),
('http://www.tcm-classics.org/fangji#GeGenJiaBanXiaTang','http://www.tcm-classics.org/yaowu#BanXia','半升','洗',8),

-- 葛根黄芩黄连汤
('http://www.tcm-classics.org/fangji#GeGenHuangQinHuangLianTang','http://www.tcm-classics.org/yaowu#GeGen','半斤',NULL,1),
('http://www.tcm-classics.org/fangji#GeGenHuangQinHuangLianTang','http://www.tcm-classics.org/yaowu#GanCao','二两','炙',2),
('http://www.tcm-classics.org/fangji#GeGenHuangQinHuangLianTang','http://www.tcm-classics.org/yaowu#HuangQin','三两',NULL,3),
('http://www.tcm-classics.org/fangji#GeGenHuangQinHuangLianTang','http://www.tcm-classics.org/yaowu#HuangLian','三两',NULL,4),

-- ===== 柴胡汤类（7方）=====
-- 小柴胡汤
('http://www.tcm-classics.org/fangji#XiaoChaiHuTang','http://www.tcm-classics.org/yaowu#ChaiHu','半斤',NULL,1),
('http://www.tcm-classics.org/fangji#XiaoChaiHuTang','http://www.tcm-classics.org/yaowu#HuangQin','三两',NULL,2),
('http://www.tcm-classics.org/fangji#XiaoChaiHuTang','http://www.tcm-classics.org/yaowu#RenShen','三两',NULL,3),
('http://www.tcm-classics.org/fangji#XiaoChaiHuTang','http://www.tcm-classics.org/yaowu#BanXia','半升','洗',4),
('http://www.tcm-classics.org/fangji#XiaoChaiHuTang','http://www.tcm-classics.org/yaowu#GanCao','三两','炙',5),
('http://www.tcm-classics.org/fangji#XiaoChaiHuTang','http://www.tcm-classics.org/yaowu#ShengJiang','三两','切',6),
('http://www.tcm-classics.org/fangji#XiaoChaiHuTang','http://www.tcm-classics.org/yaowu#DaZao','十二枚','擘',7),

-- 大柴胡汤
('http://www.tcm-classics.org/fangji#DaChaiHuTang','http://www.tcm-classics.org/yaowu#ChaiHu','半斤',NULL,1),
('http://www.tcm-classics.org/fangji#DaChaiHuTang','http://www.tcm-classics.org/yaowu#HuangQin','三两',NULL,2),
('http://www.tcm-classics.org/fangji#DaChaiHuTang','http://www.tcm-classics.org/yaowu#ShaoYao','三两',NULL,3),
('http://www.tcm-classics.org/fangji#DaChaiHuTang','http://www.tcm-classics.org/yaowu#BanXia','半升','洗',4),
('http://www.tcm-classics.org/fangji#DaChaiHuTang','http://www.tcm-classics.org/yaowu#ShengJiang','五两','切',5),
('http://www.tcm-classics.org/fangji#DaChaiHuTang','http://www.tcm-classics.org/yaowu#ZhiShi','四枚','炙',6),
('http://www.tcm-classics.org/fangji#DaChaiHuTang','http://www.tcm-classics.org/yaowu#DaZao','十二枚','擘',7),
('http://www.tcm-classics.org/fangji#DaChaiHuTang','http://www.tcm-classics.org/yaowu#DaHuang','二两',NULL,8),

-- 柴胡桂枝汤
('http://www.tcm-classics.org/fangji#ChaiHuGuiZhiTang','http://www.tcm-classics.org/yaowu#GuiZhi','一两半','去皮',1),
('http://www.tcm-classics.org/fangji#ChaiHuGuiZhiTang','http://www.tcm-classics.org/yaowu#ShaoYao','一两半',NULL,2),
('http://www.tcm-classics.org/fangji#ChaiHuGuiZhiTang','http://www.tcm-classics.org/yaowu#HuangQin','一两半',NULL,3),
('http://www.tcm-classics.org/fangji#ChaiHuGuiZhiTang','http://www.tcm-classics.org/yaowu#RenShen','一两半',NULL,4),
('http://www.tcm-classics.org/fangji#ChaiHuGuiZhiTang','http://www.tcm-classics.org/yaowu#GanCao','一两','炙',5),
('http://www.tcm-classics.org/fangji#ChaiHuGuiZhiTang','http://www.tcm-classics.org/yaowu#BanXia','二合半','洗',6),
('http://www.tcm-classics.org/fangji#ChaiHuGuiZhiTang','http://www.tcm-classics.org/yaowu#DaZao','六枚','擘',7),
('http://www.tcm-classics.org/fangji#ChaiHuGuiZhiTang','http://www.tcm-classics.org/yaowu#ShengJiang','一两半','切',8),
('http://www.tcm-classics.org/fangji#ChaiHuGuiZhiTang','http://www.tcm-classics.org/yaowu#ChaiHu','四两',NULL,9),

-- 柴胡加芒硝汤
('http://www.tcm-classics.org/fangji#ChaiHuJiaMangXiaoTang','http://www.tcm-classics.org/yaowu#ChaiHu','二两十六铢',NULL,1),
('http://www.tcm-classics.org/fangji#ChaiHuJiaMangXiaoTang','http://www.tcm-classics.org/yaowu#HuangQin','一两',NULL,2),
('http://www.tcm-classics.org/fangji#ChaiHuJiaMangXiaoTang','http://www.tcm-classics.org/yaowu#RenShen','一两',NULL,3),
('http://www.tcm-classics.org/fangji#ChaiHuJiaMangXiaoTang','http://www.tcm-classics.org/yaowu#GanCao','一两','炙',4),
('http://www.tcm-classics.org/fangji#ChaiHuJiaMangXiaoTang','http://www.tcm-classics.org/yaowu#ShengJiang','一两','切',5),
('http://www.tcm-classics.org/fangji#ChaiHuJiaMangXiaoTang','http://www.tcm-classics.org/yaowu#BanXia','二十铢','洗',6),
('http://www.tcm-classics.org/fangji#ChaiHuJiaMangXiaoTang','http://www.tcm-classics.org/yaowu#DaZao','四枚','擘',7),
('http://www.tcm-classics.org/fangji#ChaiHuJiaMangXiaoTang','http://www.tcm-classics.org/yaowu#MangXiao','二两',NULL,8),

-- 柴胡桂枝干姜汤
('http://www.tcm-classics.org/fangji#ChaiHuGuiZhiGanJiangTang','http://www.tcm-classics.org/yaowu#ChaiHu','半斤',NULL,1),
('http://www.tcm-classics.org/fangji#ChaiHuGuiZhiGanJiangTang','http://www.tcm-classics.org/yaowu#GuiZhi','三两','去皮',2),
('http://www.tcm-classics.org/fangji#ChaiHuGuiZhiGanJiangTang','http://www.tcm-classics.org/yaowu#GanJiang','二两',NULL,3),
('http://www.tcm-classics.org/fangji#ChaiHuGuiZhiGanJiangTang','http://www.tcm-classics.org/yaowu#GuaLouGen','四两',NULL,4),
('http://www.tcm-classics.org/fangji#ChaiHuGuiZhiGanJiangTang','http://www.tcm-classics.org/yaowu#HuangQin','三两',NULL,5),
('http://www.tcm-classics.org/fangji#ChaiHuGuiZhiGanJiangTang','http://www.tcm-classics.org/yaowu#MuLi','二两','熬',6),
('http://www.tcm-classics.org/fangji#ChaiHuGuiZhiGanJiangTang','http://www.tcm-classics.org/yaowu#GanCao','二两','炙',7),

-- 柴胡加龙骨牡蛎汤
('http://www.tcm-classics.org/fangji#ChaiHuJiaLongGuMuLiTang','http://www.tcm-classics.org/yaowu#ChaiHu','四两',NULL,1),
('http://www.tcm-classics.org/fangji#ChaiHuJiaLongGuMuLiTang','http://www.tcm-classics.org/yaowu#LongGu','一两半',NULL,2),
('http://www.tcm-classics.org/fangji#ChaiHuJiaLongGuMuLiTang','http://www.tcm-classics.org/yaowu#HuangQin','一两半',NULL,3),
('http://www.tcm-classics.org/fangji#ChaiHuJiaLongGuMuLiTang','http://www.tcm-classics.org/yaowu#ShengJiang','一两半','切',4),
('http://www.tcm-classics.org/fangji#ChaiHuJiaLongGuMuLiTang','http://www.tcm-classics.org/yaowu#RenShen','一两半',NULL,5),
('http://www.tcm-classics.org/fangji#ChaiHuJiaLongGuMuLiTang','http://www.tcm-classics.org/yaowu#GuiZhi','一两半','去皮',6),
('http://www.tcm-classics.org/fangji#ChaiHuJiaLongGuMuLiTang','http://www.tcm-classics.org/yaowu#FuLing','一两半',NULL,7),
('http://www.tcm-classics.org/fangji#ChaiHuJiaLongGuMuLiTang','http://www.tcm-classics.org/yaowu#BanXia','二合半','洗',8),
('http://www.tcm-classics.org/fangji#ChaiHuJiaLongGuMuLiTang','http://www.tcm-classics.org/yaowu#DaHuang','二两',NULL,9),
('http://www.tcm-classics.org/fangji#ChaiHuJiaLongGuMuLiTang','http://www.tcm-classics.org/yaowu#MuLi','一两半','熬',10),
('http://www.tcm-classics.org/fangji#ChaiHuJiaLongGuMuLiTang','http://www.tcm-classics.org/yaowu#DaZao','六枚','擘',11),
('http://www.tcm-classics.org/fangji#ChaiHuJiaLongGuMuLiTang','http://www.tcm-classics.org/yaowu#QianDan','一两半',NULL,12),

-- 四逆散
('http://www.tcm-classics.org/fangji#SiNiSan','http://www.tcm-classics.org/yaowu#GanCao','十分','炙',1),
('http://www.tcm-classics.org/fangji#SiNiSan','http://www.tcm-classics.org/yaowu#ZhiShi','十分','破水渍炙干',2),
('http://www.tcm-classics.org/fangji#SiNiSan','http://www.tcm-classics.org/yaowu#ChaiHu','十分',NULL,3),
('http://www.tcm-classics.org/fangji#SiNiSan','http://www.tcm-classics.org/yaowu#ShaoYao','十分',NULL,4),

-- ===== 白虎汤类（3方）=====
-- 白虎汤
('http://www.tcm-classics.org/fangji#BaiHuTang','http://www.tcm-classics.org/yaowu#ZhiMu','六两',NULL,1),
('http://www.tcm-classics.org/fangji#BaiHuTang','http://www.tcm-classics.org/yaowu#ShiGao','一斤','碎',2),
('http://www.tcm-classics.org/fangji#BaiHuTang','http://www.tcm-classics.org/yaowu#GanCao','二两','炙',3),
('http://www.tcm-classics.org/fangji#BaiHuTang','http://www.tcm-classics.org/yaowu#JingMi','六合',NULL,4),

-- 白虎加人参汤
('http://www.tcm-classics.org/fangji#BaiHuJiaRenShenTang','http://www.tcm-classics.org/yaowu#ZhiMu','六两',NULL,1),
('http://www.tcm-classics.org/fangji#BaiHuJiaRenShenTang','http://www.tcm-classics.org/yaowu#ShiGao','一斤','碎',2),
('http://www.tcm-classics.org/fangji#BaiHuJiaRenShenTang','http://www.tcm-classics.org/yaowu#GanCao','二两','炙',3),
('http://www.tcm-classics.org/fangji#BaiHuJiaRenShenTang','http://www.tcm-classics.org/yaowu#JingMi','六合',NULL,4),
('http://www.tcm-classics.org/fangji#BaiHuJiaRenShenTang','http://www.tcm-classics.org/yaowu#RenShen','三两',NULL,5),

-- 竹叶石膏汤
('http://www.tcm-classics.org/fangji#ZhuYeShiGaoTang','http://www.tcm-classics.org/yaowu#ZhuYe','二把',NULL,1),
('http://www.tcm-classics.org/fangji#ZhuYeShiGaoTang','http://www.tcm-classics.org/yaowu#ShiGao','一斤',NULL,2),
('http://www.tcm-classics.org/fangji#ZhuYeShiGaoTang','http://www.tcm-classics.org/yaowu#BanXia','半升','洗',3),
('http://www.tcm-classics.org/fangji#ZhuYeShiGaoTang','http://www.tcm-classics.org/yaowu#MaiMenDong','一升','去心',4),
('http://www.tcm-classics.org/fangji#ZhuYeShiGaoTang','http://www.tcm-classics.org/yaowu#RenShen','二两',NULL,5),
('http://www.tcm-classics.org/fangji#ZhuYeShiGaoTang','http://www.tcm-classics.org/yaowu#GanCao','二两','炙',6),
('http://www.tcm-classics.org/fangji#ZhuYeShiGaoTang','http://www.tcm-classics.org/yaowu#JingMi','半升',NULL,7),

-- ===== 承气汤类（5方）=====
-- 大承气汤
('http://www.tcm-classics.org/fangji#DaChengQiTang','http://www.tcm-classics.org/yaowu#DaHuang','四两','酒洗',1),
('http://www.tcm-classics.org/fangji#DaChengQiTang','http://www.tcm-classics.org/yaowu#HouPo','半斤','炙去皮',2),
('http://www.tcm-classics.org/fangji#DaChengQiTang','http://www.tcm-classics.org/yaowu#ZhiShi','五枚','炙',3),
('http://www.tcm-classics.org/fangji#DaChengQiTang','http://www.tcm-classics.org/yaowu#MangXiao','三合',NULL,4),

-- 小承气汤
('http://www.tcm-classics.org/fangji#XiaoChengQiTang','http://www.tcm-classics.org/yaowu#DaHuang','四两','酒洗',1),
('http://www.tcm-classics.org/fangji#XiaoChengQiTang','http://www.tcm-classics.org/yaowu#HouPo','二两','炙去皮',2),
('http://www.tcm-classics.org/fangji#XiaoChengQiTang','http://www.tcm-classics.org/yaowu#ZhiShi','三枚','大者炙',3),

-- 调胃承气汤
('http://www.tcm-classics.org/fangji#TiaoWeiChengQiTang','http://www.tcm-classics.org/yaowu#DaHuang','四两','清酒洗',1),
('http://www.tcm-classics.org/fangji#TiaoWeiChengQiTang','http://www.tcm-classics.org/yaowu#GanCao','二两','炙',2),
('http://www.tcm-classics.org/fangji#TiaoWeiChengQiTang','http://www.tcm-classics.org/yaowu#MangXiao','半升',NULL,3),

-- 桃核承气汤
('http://www.tcm-classics.org/fangji#TaoHeChengQiTang','http://www.tcm-classics.org/yaowu#TaoRen','五十个','去皮尖',1),
('http://www.tcm-classics.org/fangji#TaoHeChengQiTang','http://www.tcm-classics.org/yaowu#DaHuang','四两',NULL,2),
('http://www.tcm-classics.org/fangji#TaoHeChengQiTang','http://www.tcm-classics.org/yaowu#GuiZhi','二两','去皮',3),
('http://www.tcm-classics.org/fangji#TaoHeChengQiTang','http://www.tcm-classics.org/yaowu#GanCao','二两','炙',4),
('http://www.tcm-classics.org/fangji#TaoHeChengQiTang','http://www.tcm-classics.org/yaowu#MangXiao','二两',NULL,5),

-- 抵当汤
('http://www.tcm-classics.org/fangji#DiDangTang','http://www.tcm-classics.org/yaowu#ShuiZhi','三十个','熬',1),
('http://www.tcm-classics.org/fangji#DiDangTang','http://www.tcm-classics.org/yaowu#MengChong','三十个','去翅足熬',2),
('http://www.tcm-classics.org/fangji#DiDangTang','http://www.tcm-classics.org/yaowu#TaoRen','二十个','去皮尖',3),
('http://www.tcm-classics.org/fangji#DiDangTang','http://www.tcm-classics.org/yaowu#DaHuang','三两','酒洗',4),

-- ===== 理中汤类（3方）=====
-- 理中汤
('http://www.tcm-classics.org/fangji#LiZhongTang','http://www.tcm-classics.org/yaowu#RenShen','三两',NULL,1),
('http://www.tcm-classics.org/fangji#LiZhongTang','http://www.tcm-classics.org/yaowu#GanJiang','三两',NULL,2),
('http://www.tcm-classics.org/fangji#LiZhongTang','http://www.tcm-classics.org/yaowu#GanCao','三两','炙',3),
('http://www.tcm-classics.org/fangji#LiZhongTang','http://www.tcm-classics.org/yaowu#BaiZhu','三两',NULL,4),

-- 附子理中汤
('http://www.tcm-classics.org/fangji#FuZiLiZhongTang','http://www.tcm-classics.org/yaowu#RenShen','三两',NULL,1),
('http://www.tcm-classics.org/fangji#FuZiLiZhongTang','http://www.tcm-classics.org/yaowu#GanJiang','四两半',NULL,2),
('http://www.tcm-classics.org/fangji#FuZiLiZhongTang','http://www.tcm-classics.org/yaowu#GanCao','三两','炙',3),
('http://www.tcm-classics.org/fangji#FuZiLiZhongTang','http://www.tcm-classics.org/yaowu#BaiZhu','三两',NULL,4),
('http://www.tcm-classics.org/fangji#FuZiLiZhongTang','http://www.tcm-classics.org/yaowu#FuZi','一枚','炮去皮破八片',5),

-- 吴茱萸汤
('http://www.tcm-classics.org/fangji#WuZhuYuTang','http://www.tcm-classics.org/yaowu#WuZhuYu','一升','洗',1),
('http://www.tcm-classics.org/fangji#WuZhuYuTang','http://www.tcm-classics.org/yaowu#RenShen','三两',NULL,2),
('http://www.tcm-classics.org/fangji#WuZhuYuTang','http://www.tcm-classics.org/yaowu#ShengJiang','六两','切',3),
('http://www.tcm-classics.org/fangji#WuZhuYuTang','http://www.tcm-classics.org/yaowu#DaZao','十二枚','擘',4),

-- ===== 四逆汤类（6方）=====
-- 四逆汤
('http://www.tcm-classics.org/fangji#SiNiTang','http://www.tcm-classics.org/yaowu#GanCao','二两','炙',1),
('http://www.tcm-classics.org/fangji#SiNiTang','http://www.tcm-classics.org/yaowu#GanJiang','一两半',NULL,2),
('http://www.tcm-classics.org/fangji#SiNiTang','http://www.tcm-classics.org/yaowu#FuZi','一枚','生用去皮破八片',3),

-- 通脉四逆汤
('http://www.tcm-classics.org/fangji#TongMaiSiNiTang','http://www.tcm-classics.org/yaowu#GanCao','二两','炙',1),
('http://www.tcm-classics.org/fangji#TongMaiSiNiTang','http://www.tcm-classics.org/yaowu#FuZi','大者一枚','生用去皮破八片',2),
('http://www.tcm-classics.org/fangji#TongMaiSiNiTang','http://www.tcm-classics.org/yaowu#GanJiang','三两',NULL,3),

-- 白通汤
('http://www.tcm-classics.org/fangji#BaiTongTang','http://www.tcm-classics.org/yaowu#CongBai','四茎',NULL,1),
('http://www.tcm-classics.org/fangji#BaiTongTang','http://www.tcm-classics.org/yaowu#GanJiang','一两',NULL,2),
('http://www.tcm-classics.org/fangji#BaiTongTang','http://www.tcm-classics.org/yaowu#FuZi','一枚','生用去皮破八片',3),

-- 白通加猪胆汁汤
('http://www.tcm-classics.org/fangji#BaiTongJiaZhuDanZhiTang','http://www.tcm-classics.org/yaowu#CongBai','四茎',NULL,1),
('http://www.tcm-classics.org/fangji#BaiTongJiaZhuDanZhiTang','http://www.tcm-classics.org/yaowu#GanJiang','一两',NULL,2),
('http://www.tcm-classics.org/fangji#BaiTongJiaZhuDanZhiTang','http://www.tcm-classics.org/yaowu#FuZi','一枚','生用去皮破八片',3),
('http://www.tcm-classics.org/fangji#BaiTongJiaZhuDanZhiTang','http://www.tcm-classics.org/yaowu#RenNiao','五合',NULL,4),
('http://www.tcm-classics.org/fangji#BaiTongJiaZhuDanZhiTang','http://www.tcm-classics.org/yaowu#ZhuDanZhi','一合',NULL,5),

-- 真武汤
('http://www.tcm-classics.org/fangji#ZhenWuTang','http://www.tcm-classics.org/yaowu#FuLing','三两',NULL,1),
('http://www.tcm-classics.org/fangji#ZhenWuTang','http://www.tcm-classics.org/yaowu#ShaoYao','三两',NULL,2),
('http://www.tcm-classics.org/fangji#ZhenWuTang','http://www.tcm-classics.org/yaowu#BaiZhu','二两',NULL,3),
('http://www.tcm-classics.org/fangji#ZhenWuTang','http://www.tcm-classics.org/yaowu#ShengJiang','三两','切',4),
('http://www.tcm-classics.org/fangji#ZhenWuTang','http://www.tcm-classics.org/yaowu#FuZi','一枚','炮去皮破八片',5),

-- 黄连阿胶汤
('http://www.tcm-classics.org/fangji#HuangLianEJiaoTang','http://www.tcm-classics.org/yaowu#HuangLian','四两',NULL,1),
('http://www.tcm-classics.org/fangji#HuangLianEJiaoTang','http://www.tcm-classics.org/yaowu#HuangQin','二两',NULL,2),
('http://www.tcm-classics.org/fangji#HuangLianEJiaoTang','http://www.tcm-classics.org/yaowu#ShaoYao','二两',NULL,3),
('http://www.tcm-classics.org/fangji#HuangLianEJiaoTang','http://www.tcm-classics.org/yaowu#HuangLianEJiaoTang_JiZiHuang','二枚',NULL,4),
('http://www.tcm-classics.org/fangji#HuangLianEJiaoTang','http://www.tcm-classics.org/yaowu#EJiao','三两',NULL,5),

-- ===== 厥阴病篇（5方）=====
-- 乌梅丸
('http://www.tcm-classics.org/fangji#WuMeiWan','http://www.tcm-classics.org/yaowu#WuMei','三百枚',NULL,1),
('http://www.tcm-classics.org/fangji#WuMeiWan','http://www.tcm-classics.org/yaowu#XiXin','六两',NULL,2),
('http://www.tcm-classics.org/fangji#WuMeiWan','http://www.tcm-classics.org/yaowu#GanJiang','十两',NULL,3),
('http://www.tcm-classics.org/fangji#WuMeiWan','http://www.tcm-classics.org/yaowu#HuangLian','十六两',NULL,4),
('http://www.tcm-classics.org/fangji#WuMeiWan','http://www.tcm-classics.org/yaowu#DangGui','四两',NULL,5),
('http://www.tcm-classics.org/fangji#WuMeiWan','http://www.tcm-classics.org/yaowu#FuZi','六两','炮去皮',6),
('http://www.tcm-classics.org/fangji#WuMeiWan','http://www.tcm-classics.org/yaowu#ShuJiao','四两','炒去汗',7),
('http://www.tcm-classics.org/fangji#WuMeiWan','http://www.tcm-classics.org/yaowu#GuiZhi','六两','去皮',8),
('http://www.tcm-classics.org/fangji#WuMeiWan','http://www.tcm-classics.org/yaowu#RenShen','六两',NULL,9),
('http://www.tcm-classics.org/fangji#WuMeiWan','http://www.tcm-classics.org/yaowu#HuangBai','六两',NULL,10),

-- 当归四逆汤
('http://www.tcm-classics.org/fangji#DangGuiSiNiTang','http://www.tcm-classics.org/yaowu#DangGui','三两',NULL,1),
('http://www.tcm-classics.org/fangji#DangGuiSiNiTang','http://www.tcm-classics.org/yaowu#GuiZhi','三两','去皮',2),
('http://www.tcm-classics.org/fangji#DangGuiSiNiTang','http://www.tcm-classics.org/yaowu#ShaoYao','三两',NULL,3),
('http://www.tcm-classics.org/fangji#DangGuiSiNiTang','http://www.tcm-classics.org/yaowu#XiXin','三两',NULL,4),
('http://www.tcm-classics.org/fangji#DangGuiSiNiTang','http://www.tcm-classics.org/yaowu#GanCao','二两','炙',5),
('http://www.tcm-classics.org/fangji#DangGuiSiNiTang','http://www.tcm-classics.org/yaowu#TongCao','二两',NULL,6),
('http://www.tcm-classics.org/fangji#DangGuiSiNiTang','http://www.tcm-classics.org/yaowu#DaZao','二十五枚','擘',7),

-- 当归四逆加吴茱萸生姜汤
('http://www.tcm-classics.org/fangji#DangGuiSiNiJiaWuZhuYuShengJiangTang','http://www.tcm-classics.org/yaowu#DangGui','三两',NULL,1),
('http://www.tcm-classics.org/fangji#DangGuiSiNiJiaWuZhuYuShengJiangTang','http://www.tcm-classics.org/yaowu#GuiZhi','三两','去皮',2),
('http://www.tcm-classics.org/fangji#DangGuiSiNiJiaWuZhuYuShengJiangTang','http://www.tcm-classics.org/yaowu#ShaoYao','三两',NULL,3),
('http://www.tcm-classics.org/fangji#DangGuiSiNiJiaWuZhuYuShengJiangTang','http://www.tcm-classics.org/yaowu#XiXin','三两',NULL,4),
('http://www.tcm-classics.org/fangji#DangGuiSiNiJiaWuZhuYuShengJiangTang','http://www.tcm-classics.org/yaowu#GanCao','二两','炙',5),
('http://www.tcm-classics.org/fangji#DangGuiSiNiJiaWuZhuYuShengJiangTang','http://www.tcm-classics.org/yaowu#TongCao','二两',NULL,6),
('http://www.tcm-classics.org/fangji#DangGuiSiNiJiaWuZhuYuShengJiangTang','http://www.tcm-classics.org/yaowu#DaZao','二十五枚','擘',7),
('http://www.tcm-classics.org/fangji#DangGuiSiNiJiaWuZhuYuShengJiangTang','http://www.tcm-classics.org/yaowu#WuZhuYu','二升',NULL,8),
('http://www.tcm-classics.org/fangji#DangGuiSiNiJiaWuZhuYuShengJiangTang','http://www.tcm-classics.org/yaowu#ShengJiang','半斤','切',9),

-- 白头翁汤
('http://www.tcm-classics.org/fangji#BaiTouWengTang','http://www.tcm-classics.org/yaowu#BaiTouWeng','二两',NULL,1),
('http://www.tcm-classics.org/fangji#BaiTouWengTang','http://www.tcm-classics.org/yaowu#HuangBai','三两',NULL,2),
('http://www.tcm-classics.org/fangji#BaiTouWengTang','http://www.tcm-classics.org/yaowu#HuangLian','三两',NULL,3),
('http://www.tcm-classics.org/fangji#BaiTouWengTang','http://www.tcm-classics.org/yaowu#QinPi','三两',NULL,4),

-- 干姜黄芩黄连人参汤
('http://www.tcm-classics.org/fangji#GanJiangHuangQinHuangLianRenShenTang','http://www.tcm-classics.org/yaowu#GanJiang','三两',NULL,1),
('http://www.tcm-classics.org/fangji#GanJiangHuangQinHuangLianRenShenTang','http://www.tcm-classics.org/yaowu#HuangQin','三两',NULL,2),
('http://www.tcm-classics.org/fangji#GanJiangHuangQinHuangLianRenShenTang','http://www.tcm-classics.org/yaowu#HuangLian','三两',NULL,3),
('http://www.tcm-classics.org/fangji#GanJiangHuangQinHuangLianRenShenTang','http://www.tcm-classics.org/yaowu#RenShen','三两',NULL,4),

-- ===== 泻心汤类（5方）=====
-- 半夏泻心汤
('http://www.tcm-classics.org/fangji#BanXiaXieXinTang','http://www.tcm-classics.org/yaowu#BanXia','半升','洗',1),
('http://www.tcm-classics.org/fangji#BanXiaXieXinTang','http://www.tcm-classics.org/yaowu#HuangQin','三两',NULL,2),
('http://www.tcm-classics.org/fangji#BanXiaXieXinTang','http://www.tcm-classics.org/yaowu#GanJiang','三两',NULL,3),
('http://www.tcm-classics.org/fangji#BanXiaXieXinTang','http://www.tcm-classics.org/yaowu#RenShen','三两',NULL,4),
('http://www.tcm-classics.org/fangji#BanXiaXieXinTang','http://www.tcm-classics.org/yaowu#GanCao','三两','炙',5),
('http://www.tcm-classics.org/fangji#BanXiaXieXinTang','http://www.tcm-classics.org/yaowu#HuangLian','一两',NULL,6),
('http://www.tcm-classics.org/fangji#BanXiaXieXinTang','http://www.tcm-classics.org/yaowu#DaZao','十二枚','擘',7),

-- 生姜泻心汤
('http://www.tcm-classics.org/fangji#ShengJiangXieXinTang','http://www.tcm-classics.org/yaowu#ShengJiang','四两','切',1),
('http://www.tcm-classics.org/fangji#ShengJiangXieXinTang','http://www.tcm-classics.org/yaowu#GanCao','三两','炙',2),
('http://www.tcm-classics.org/fangji#ShengJiangXieXinTang','http://www.tcm-classics.org/yaowu#RenShen','三两',NULL,3),
('http://www.tcm-classics.org/fangji#ShengJiangXieXinTang','http://www.tcm-classics.org/yaowu#GanJiang','一两',NULL,4),
('http://www.tcm-classics.org/fangji#ShengJiangXieXinTang','http://www.tcm-classics.org/yaowu#HuangQin','三两',NULL,5),
('http://www.tcm-classics.org/fangji#ShengJiangXieXinTang','http://www.tcm-classics.org/yaowu#BanXia','半升','洗',6),
('http://www.tcm-classics.org/fangji#ShengJiangXieXinTang','http://www.tcm-classics.org/yaowu#HuangLian','一两',NULL,7),
('http://www.tcm-classics.org/fangji#ShengJiangXieXinTang','http://www.tcm-classics.org/yaowu#DaZao','十二枚','擘',8),

-- 甘草泻心汤
('http://www.tcm-classics.org/fangji#GanCaoXieXinTang','http://www.tcm-classics.org/yaowu#GanCao','四两','炙',1),
('http://www.tcm-classics.org/fangji#GanCaoXieXinTang','http://www.tcm-classics.org/yaowu#HuangQin','三两',NULL,2),
('http://www.tcm-classics.org/fangji#GanCaoXieXinTang','http://www.tcm-classics.org/yaowu#GanJiang','三两',NULL,3),
('http://www.tcm-classics.org/fangji#GanCaoXieXinTang','http://www.tcm-classics.org/yaowu#BanXia','半升','洗',4),
('http://www.tcm-classics.org/fangji#GanCaoXieXinTang','http://www.tcm-classics.org/yaowu#HuangLian','一两',NULL,5),
('http://www.tcm-classics.org/fangji#GanCaoXieXinTang','http://www.tcm-classics.org/yaowu#DaZao','十二枚','擘',6),
('http://www.tcm-classics.org/fangji#GanCaoXieXinTang','http://www.tcm-classics.org/yaowu#RenShen','三两',NULL,7),

-- 大黄黄连泻心汤
('http://www.tcm-classics.org/fangji#DaHuangHuangLianXieXinTang','http://www.tcm-classics.org/yaowu#DaHuang','二两',NULL,1),
('http://www.tcm-classics.org/fangji#DaHuangHuangLianXieXinTang','http://www.tcm-classics.org/yaowu#HuangLian','一两',NULL,2),

-- 附子泻心汤
('http://www.tcm-classics.org/fangji#FuZiXieXinTang','http://www.tcm-classics.org/yaowu#DaHuang','二两',NULL,1),
('http://www.tcm-classics.org/fangji#FuZiXieXinTang','http://www.tcm-classics.org/yaowu#HuangLian','一两',NULL,2),
('http://www.tcm-classics.org/fangji#FuZiXieXinTang','http://www.tcm-classics.org/yaowu#HuangQin','一两',NULL,3),
('http://www.tcm-classics.org/fangji#FuZiXieXinTang','http://www.tcm-classics.org/yaowu#FuZi','一枚','炮去皮破别煮取汁',4),

-- ===== 蓄水蓄血及其他太阳变证（8方）=====
-- 五苓散
('http://www.tcm-classics.org/fangji#WuLingSan','http://www.tcm-classics.org/yaowu#ZhuLing','十八铢','去皮',1),
('http://www.tcm-classics.org/fangji#WuLingSan','http://www.tcm-classics.org/yaowu#ZeXie','一两六铢',NULL,2),
('http://www.tcm-classics.org/fangji#WuLingSan','http://www.tcm-classics.org/yaowu#BaiZhu','十八铢',NULL,3),
('http://www.tcm-classics.org/fangji#WuLingSan','http://www.tcm-classics.org/yaowu#FuLing','十八铢',NULL,4),
('http://www.tcm-classics.org/fangji#WuLingSan','http://www.tcm-classics.org/yaowu#GuiZhi','半两','去皮',5),

-- 茯苓甘草汤
('http://www.tcm-classics.org/fangji#FuLingGanCaoTang','http://www.tcm-classics.org/yaowu#FuLing','二两',NULL,1),
('http://www.tcm-classics.org/fangji#FuLingGanCaoTang','http://www.tcm-classics.org/yaowu#GuiZhi','二两','去皮',2),
('http://www.tcm-classics.org/fangji#FuLingGanCaoTang','http://www.tcm-classics.org/yaowu#GanCao','一两','炙',3),
('http://www.tcm-classics.org/fangji#FuLingGanCaoTang','http://www.tcm-classics.org/yaowu#ShengJiang','三两','切',4),

-- 旋覆代赭汤
('http://www.tcm-classics.org/fangji#XuanFuDaiZheTang','http://www.tcm-classics.org/yaowu#XuanFuHua','三两',NULL,1),
('http://www.tcm-classics.org/fangji#XuanFuDaiZheTang','http://www.tcm-classics.org/yaowu#RenShen','二两',NULL,2),
('http://www.tcm-classics.org/fangji#XuanFuDaiZheTang','http://www.tcm-classics.org/yaowu#ShengJiang','五两','切',3),
('http://www.tcm-classics.org/fangji#XuanFuDaiZheTang','http://www.tcm-classics.org/yaowu#DaiZheShi','一两',NULL,4),
('http://www.tcm-classics.org/fangji#XuanFuDaiZheTang','http://www.tcm-classics.org/yaowu#GanCao','三两','炙',5),
('http://www.tcm-classics.org/fangji#XuanFuDaiZheTang','http://www.tcm-classics.org/yaowu#BanXia','半升','洗',6),
('http://www.tcm-classics.org/fangji#XuanFuDaiZheTang','http://www.tcm-classics.org/yaowu#DaZao','十二枚','擘',7),

-- 厚朴生姜半夏甘草人参汤
('http://www.tcm-classics.org/fangji#HouPoShengJiangBanXiaGanCaoRenShenTang','http://www.tcm-classics.org/yaowu#HouPo','半斤','炙去皮',1),
('http://www.tcm-classics.org/fangji#HouPoShengJiangBanXiaGanCaoRenShenTang','http://www.tcm-classics.org/yaowu#ShengJiang','半斤','切',2),
('http://www.tcm-classics.org/fangji#HouPoShengJiangBanXiaGanCaoRenShenTang','http://www.tcm-classics.org/yaowu#BanXia','半升','洗',3),
('http://www.tcm-classics.org/fangji#HouPoShengJiangBanXiaGanCaoRenShenTang','http://www.tcm-classics.org/yaowu#GanCao','二两','炙',4),
('http://www.tcm-classics.org/fangji#HouPoShengJiangBanXiaGanCaoRenShenTang','http://www.tcm-classics.org/yaowu#RenShen','一两',NULL,5),

-- 栀子豉汤
('http://www.tcm-classics.org/fangji#ZhiZiChiTang','http://www.tcm-classics.org/yaowu#ZhiZi','十四个','擘',1),
('http://www.tcm-classics.org/fangji#ZhiZiChiTang','http://www.tcm-classics.org/yaowu#XiangChi','四合','绵裹',2),

-- 栀子甘草汤
('http://www.tcm-classics.org/fangji#ZhiZiGanCaoTang','http://www.tcm-classics.org/yaowu#ZhiZi','十四个','擘',1),
('http://www.tcm-classics.org/fangji#ZhiZiGanCaoTang','http://www.tcm-classics.org/yaowu#XiangChi','四合','绵裹',2),
('http://www.tcm-classics.org/fangji#ZhiZiGanCaoTang','http://www.tcm-classics.org/yaowu#GanCao','二两','炙',3),

-- 栀子生姜汤
('http://www.tcm-classics.org/fangji#ZhiZiShengJiangTang','http://www.tcm-classics.org/yaowu#ZhiZi','十四个','擘',1),
('http://www.tcm-classics.org/fangji#ZhiZiShengJiangTang','http://www.tcm-classics.org/yaowu#XiangChi','四合','绵裹',2),
('http://www.tcm-classics.org/fangji#ZhiZiShengJiangTang','http://www.tcm-classics.org/yaowu#ShengJiang','五两','切',3),

-- 栀子厚朴汤
('http://www.tcm-classics.org/fangji#ZhiZiHouPoTang','http://www.tcm-classics.org/yaowu#ZhiZi','十四个','擘',1),
('http://www.tcm-classics.org/fangji#ZhiZiHouPoTang','http://www.tcm-classics.org/yaowu#HouPo','四两','炙去皮',2),
('http://www.tcm-classics.org/fangji#ZhiZiHouPoTang','http://www.tcm-classics.org/yaowu#ZhiShi','四枚','水浸炙令黄',3),

-- ===== 金匮杂病方（20方）=====
-- 瓜蒌薤白白酒汤
('http://www.tcm-classics.org/fangji#GuaLouXieBaiBaiJiuTang','http://www.tcm-classics.org/yaowu#GuaLouShi','一枚','捣',1),
('http://www.tcm-classics.org/fangji#GuaLouXieBaiBaiJiuTang','http://www.tcm-classics.org/yaowu#XieBai','半斤',NULL,2),
('http://www.tcm-classics.org/fangji#GuaLouXieBaiBaiJiuTang','http://www.tcm-classics.org/yaowu#BaiJiu','七升',NULL,3),

-- 瓜蒌薤白半夏汤
('http://www.tcm-classics.org/fangji#GuaLouXieBaiBanXiaTang','http://www.tcm-classics.org/yaowu#GuaLouShi','一枚','捣',1),
('http://www.tcm-classics.org/fangji#GuaLouXieBaiBanXiaTang','http://www.tcm-classics.org/yaowu#XieBai','三两',NULL,2),
('http://www.tcm-classics.org/fangji#GuaLouXieBaiBanXiaTang','http://www.tcm-classics.org/yaowu#BanXia','半升',NULL,3),
('http://www.tcm-classics.org/fangji#GuaLouXieBaiBanXiaTang','http://www.tcm-classics.org/yaowu#BaiJiu','一斗',NULL,4),

-- 枳实薤白桂枝汤
('http://www.tcm-classics.org/fangji#ZhiShiXieBaiGuiZhiTang','http://www.tcm-classics.org/yaowu#ZhiShi','四枚',NULL,1),
('http://www.tcm-classics.org/fangji#ZhiShiXieBaiGuiZhiTang','http://www.tcm-classics.org/yaowu#HouPo','四两',NULL,2),
('http://www.tcm-classics.org/fangji#ZhiShiXieBaiGuiZhiTang','http://www.tcm-classics.org/yaowu#XieBai','半斤',NULL,3),
('http://www.tcm-classics.org/fangji#ZhiShiXieBaiGuiZhiTang','http://www.tcm-classics.org/yaowu#GuiZhi','一两',NULL,4),
('http://www.tcm-classics.org/fangji#ZhiShiXieBaiGuiZhiTang','http://www.tcm-classics.org/yaowu#GuaLouShi','一枚','捣',5),

-- 厚朴七物汤
('http://www.tcm-classics.org/fangji#HouPoQiWuTang','http://www.tcm-classics.org/yaowu#HouPo','半斤',NULL,1),
('http://www.tcm-classics.org/fangji#HouPoQiWuTang','http://www.tcm-classics.org/yaowu#GanCao','三两',NULL,2),
('http://www.tcm-classics.org/fangji#HouPoQiWuTang','http://www.tcm-classics.org/yaowu#DaHuang','三两',NULL,3),
('http://www.tcm-classics.org/fangji#HouPoQiWuTang','http://www.tcm-classics.org/yaowu#DaZao','十枚',NULL,4),
('http://www.tcm-classics.org/fangji#HouPoQiWuTang','http://www.tcm-classics.org/yaowu#ZhiShi','五枚',NULL,5),
('http://www.tcm-classics.org/fangji#HouPoQiWuTang','http://www.tcm-classics.org/yaowu#GuiZhi','二两',NULL,6),
('http://www.tcm-classics.org/fangji#HouPoQiWuTang','http://www.tcm-classics.org/yaowu#ShengJiang','五两',NULL,7),

-- 桂枝芍药知母汤
('http://www.tcm-classics.org/fangji#GuiZhiShaoYaoZhiMuTang','http://www.tcm-classics.org/yaowu#GuiZhi','四两',NULL,1),
('http://www.tcm-classics.org/fangji#GuiZhiShaoYaoZhiMuTang','http://www.tcm-classics.org/yaowu#ShaoYao','三两',NULL,2),
('http://www.tcm-classics.org/fangji#GuiZhiShaoYaoZhiMuTang','http://www.tcm-classics.org/yaowu#GanCao','二两',NULL,3),
('http://www.tcm-classics.org/fangji#GuiZhiShaoYaoZhiMuTang','http://www.tcm-classics.org/yaowu#MaHuang','二两',NULL,4),
('http://www.tcm-classics.org/fangji#GuiZhiShaoYaoZhiMuTang','http://www.tcm-classics.org/yaowu#ShengJiang','五两',NULL,5),
('http://www.tcm-classics.org/fangji#GuiZhiShaoYaoZhiMuTang','http://www.tcm-classics.org/yaowu#BaiZhu','五两',NULL,6),
('http://www.tcm-classics.org/fangji#GuiZhiShaoYaoZhiMuTang','http://www.tcm-classics.org/yaowu#ZhiMu','四两',NULL,7),
('http://www.tcm-classics.org/fangji#GuiZhiShaoYaoZhiMuTang','http://www.tcm-classics.org/yaowu#FangFeng','四两',NULL,8),
('http://www.tcm-classics.org/fangji#GuiZhiShaoYaoZhiMuTang','http://www.tcm-classics.org/yaowu#FuZi','二枚','炮',9),

-- 乌头汤
('http://www.tcm-classics.org/fangji#WuTouTang','http://www.tcm-classics.org/yaowu#ChuanWu','五枚','㕮咀蜜煎',1),
('http://www.tcm-classics.org/fangji#WuTouTang','http://www.tcm-classics.org/yaowu#MaHuang','三两',NULL,2),
('http://www.tcm-classics.org/fangji#WuTouTang','http://www.tcm-classics.org/yaowu#ShaoYao','三两',NULL,3),
('http://www.tcm-classics.org/fangji#WuTouTang','http://www.tcm-classics.org/yaowu#HuangQi','三两',NULL,4),
('http://www.tcm-classics.org/fangji#WuTouTang','http://www.tcm-classics.org/yaowu#GanCao','三两','炙',5),

-- 桂枝茯苓丸
('http://www.tcm-classics.org/fangji#GuiZhiFuLingWan','http://www.tcm-classics.org/yaowu#GuiZhi','等分',NULL,1),
('http://www.tcm-classics.org/fangji#GuiZhiFuLingWan','http://www.tcm-classics.org/yaowu#FuLing','等分',NULL,2),
('http://www.tcm-classics.org/fangji#GuiZhiFuLingWan','http://www.tcm-classics.org/yaowu#MuDanPi','等分','去心',3),
('http://www.tcm-classics.org/fangji#GuiZhiFuLingWan','http://www.tcm-classics.org/yaowu#TaoRen','等分','去皮尖熬',4),
('http://www.tcm-classics.org/fangji#GuiZhiFuLingWan','http://www.tcm-classics.org/yaowu#ShaoYao','等分',NULL,5),

-- 温经汤
('http://www.tcm-classics.org/fangji#WenJingTang','http://www.tcm-classics.org/yaowu#WuZhuYu','三两',NULL,1),
('http://www.tcm-classics.org/fangji#WenJingTang','http://www.tcm-classics.org/yaowu#DangGui','二两',NULL,2),
('http://www.tcm-classics.org/fangji#WenJingTang','http://www.tcm-classics.org/yaowu#ChuanXiong','二两',NULL,3),
('http://www.tcm-classics.org/fangji#WenJingTang','http://www.tcm-classics.org/yaowu#ShaoYao','二两',NULL,4),
('http://www.tcm-classics.org/fangji#WenJingTang','http://www.tcm-classics.org/yaowu#RenShen','二两',NULL,5),
('http://www.tcm-classics.org/fangji#WenJingTang','http://www.tcm-classics.org/yaowu#GuiZhi','二两',NULL,6),
('http://www.tcm-classics.org/fangji#WenJingTang','http://www.tcm-classics.org/yaowu#EJiao','二两',NULL,7),
('http://www.tcm-classics.org/fangji#WenJingTang','http://www.tcm-classics.org/yaowu#MuDanPi','二两','去心',8),
('http://www.tcm-classics.org/fangji#WenJingTang','http://www.tcm-classics.org/yaowu#ShengJiang','二两',NULL,9),
('http://www.tcm-classics.org/fangji#WenJingTang','http://www.tcm-classics.org/yaowu#GanCao','二两',NULL,10),
('http://www.tcm-classics.org/fangji#WenJingTang','http://www.tcm-classics.org/yaowu#BanXia','半升',NULL,11),
('http://www.tcm-classics.org/fangji#WenJingTang','http://www.tcm-classics.org/yaowu#MaiMenDong','一升','去心',12),

-- 胶艾汤
('http://www.tcm-classics.org/fangji#JiaoAiTang','http://www.tcm-classics.org/yaowu#ChuanXiong','二两',NULL,1),
('http://www.tcm-classics.org/fangji#JiaoAiTang','http://www.tcm-classics.org/yaowu#EJiao','二两',NULL,2),
('http://www.tcm-classics.org/fangji#JiaoAiTang','http://www.tcm-classics.org/yaowu#GanCao','二两',NULL,3),
('http://www.tcm-classics.org/fangji#JiaoAiTang','http://www.tcm-classics.org/yaowu#AiYe','三两',NULL,4),
('http://www.tcm-classics.org/fangji#JiaoAiTang','http://www.tcm-classics.org/yaowu#DangGui','三两',NULL,5),
('http://www.tcm-classics.org/fangji#JiaoAiTang','http://www.tcm-classics.org/yaowu#ShaoYao','四两',NULL,6),
('http://www.tcm-classics.org/fangji#JiaoAiTang','http://www.tcm-classics.org/yaowu#DiHuang','六两',NULL,7),

-- 当归芍药散
('http://www.tcm-classics.org/fangji#DangGuiShaoYaoSan','http://www.tcm-classics.org/yaowu#DangGui','三两',NULL,1),
('http://www.tcm-classics.org/fangji#DangGuiShaoYaoSan','http://www.tcm-classics.org/yaowu#ShaoYao','一斤',NULL,2),
('http://www.tcm-classics.org/fangji#DangGuiShaoYaoSan','http://www.tcm-classics.org/yaowu#ChuanXiong','半斤',NULL,3),
('http://www.tcm-classics.org/fangji#DangGuiShaoYaoSan','http://www.tcm-classics.org/yaowu#FuLing','四两',NULL,4),
('http://www.tcm-classics.org/fangji#DangGuiShaoYaoSan','http://www.tcm-classics.org/yaowu#BaiZhu','四两',NULL,5),
('http://www.tcm-classics.org/fangji#DangGuiShaoYaoSan','http://www.tcm-classics.org/yaowu#ZeXie','半斤',NULL,6),

-- 百合地黄汤
('http://www.tcm-classics.org/fangji#BaiHeDiHuangTang','http://www.tcm-classics.org/yaowu#BaiHe','七枚','擘',1),
('http://www.tcm-classics.org/fangji#BaiHeDiHuangTang','http://www.tcm-classics.org/yaowu#ShengDiHuangZhi','一升',NULL,2),

-- 酸枣仁汤
('http://www.tcm-classics.org/fangji#SuanZaoRenTang','http://www.tcm-classics.org/yaowu#SuanZaoRen','二升',NULL,1),
('http://www.tcm-classics.org/fangji#SuanZaoRenTang','http://www.tcm-classics.org/yaowu#GanCao','一两',NULL,2),
('http://www.tcm-classics.org/fangji#SuanZaoRenTang','http://www.tcm-classics.org/yaowu#ZhiMu','二两',NULL,3),
('http://www.tcm-classics.org/fangji#SuanZaoRenTang','http://www.tcm-classics.org/yaowu#FuLing','二两',NULL,4),
('http://www.tcm-classics.org/fangji#SuanZaoRenTang','http://www.tcm-classics.org/yaowu#ChuanXiong','二两',NULL,5),

-- 黄土汤
('http://www.tcm-classics.org/fangji#HuangTuTang','http://www.tcm-classics.org/yaowu#GanCao','三两',NULL,1),
('http://www.tcm-classics.org/fangji#HuangTuTang','http://www.tcm-classics.org/yaowu#DiHuang','三两',NULL,2),
('http://www.tcm-classics.org/fangji#HuangTuTang','http://www.tcm-classics.org/yaowu#BaiZhu','三两',NULL,3),
('http://www.tcm-classics.org/fangji#HuangTuTang','http://www.tcm-classics.org/yaowu#FuZi','三两','炮',4),
('http://www.tcm-classics.org/fangji#HuangTuTang','http://www.tcm-classics.org/yaowu#EJiao','三两',NULL,5),
('http://www.tcm-classics.org/fangji#HuangTuTang','http://www.tcm-classics.org/yaowu#HuangQin','三两',NULL,6),
('http://www.tcm-classics.org/fangji#HuangTuTang','http://www.tcm-classics.org/yaowu#ZaoXinHuangTu','半斤',NULL,7),

-- 赤小豆当归散
('http://www.tcm-classics.org/fangji#ChiXiaoDouDangGuiSan','http://www.tcm-classics.org/yaowu#ChiXiaoDou','三升','浸令芽出曝干',1),
('http://www.tcm-classics.org/fangji#ChiXiaoDouDangGuiSan','http://www.tcm-classics.org/yaowu#DangGui','三两',NULL,2),

-- 麦门冬汤
('http://www.tcm-classics.org/fangji#MaiMenDongTang','http://www.tcm-classics.org/yaowu#MaiMenDong','七升',NULL,1),
('http://www.tcm-classics.org/fangji#MaiMenDongTang','http://www.tcm-classics.org/yaowu#BanXia','一升',NULL,2),
('http://www.tcm-classics.org/fangji#MaiMenDongTang','http://www.tcm-classics.org/yaowu#RenShen','二两',NULL,3),
('http://www.tcm-classics.org/fangji#MaiMenDongTang','http://www.tcm-classics.org/yaowu#GanCao','二两',NULL,4),
('http://www.tcm-classics.org/fangji#MaiMenDongTang','http://www.tcm-classics.org/yaowu#JingMi','三合',NULL,5),
('http://www.tcm-classics.org/fangji#MaiMenDongTang','http://www.tcm-classics.org/yaowu#DaZao','十二枚',NULL,6),

-- 射干麻黄汤
('http://www.tcm-classics.org/fangji#SheGanMaHuangTang','http://www.tcm-classics.org/yaowu#SheGan','十三枚',NULL,1),
('http://www.tcm-classics.org/fangji#SheGanMaHuangTang','http://www.tcm-classics.org/yaowu#MaHuang','四两',NULL,2),
('http://www.tcm-classics.org/fangji#SheGanMaHuangTang','http://www.tcm-classics.org/yaowu#ShengJiang','四两',NULL,3),
('http://www.tcm-classics.org/fangji#SheGanMaHuangTang','http://www.tcm-classics.org/yaowu#XiXin','三两',NULL,4),
('http://www.tcm-classics.org/fangji#SheGanMaHuangTang','http://www.tcm-classics.org/yaowu#ZiWan','三两',NULL,5),
('http://www.tcm-classics.org/fangji#SheGanMaHuangTang','http://www.tcm-classics.org/yaowu#KuanDongHua','三两',NULL,6),
('http://www.tcm-classics.org/fangji#SheGanMaHuangTang','http://www.tcm-classics.org/yaowu#WuWeiZi','半升',NULL,7),
('http://www.tcm-classics.org/fangji#SheGanMaHuangTang','http://www.tcm-classics.org/yaowu#DaZao','七枚',NULL,8),
('http://www.tcm-classics.org/fangji#SheGanMaHuangTang','http://www.tcm-classics.org/yaowu#BanXia','八枚','洗',9),

-- 厚朴麻黄汤
('http://www.tcm-classics.org/fangji#HouPoMaHuangTang','http://www.tcm-classics.org/yaowu#HouPo','五两',NULL,1),
('http://www.tcm-classics.org/fangji#HouPoMaHuangTang','http://www.tcm-classics.org/yaowu#MaHuang','四两',NULL,2),
('http://www.tcm-classics.org/fangji#HouPoMaHuangTang','http://www.tcm-classics.org/yaowu#ShiGao','如鸡子大',NULL,3),
('http://www.tcm-classics.org/fangji#HouPoMaHuangTang','http://www.tcm-classics.org/yaowu#XingRen','半升',NULL,4),
('http://www.tcm-classics.org/fangji#HouPoMaHuangTang','http://www.tcm-classics.org/yaowu#BanXia','半升',NULL,5),
('http://www.tcm-classics.org/fangji#HouPoMaHuangTang','http://www.tcm-classics.org/yaowu#GanJiang','二两',NULL,6),
('http://www.tcm-classics.org/fangji#HouPoMaHuangTang','http://www.tcm-classics.org/yaowu#XiXin','二两',NULL,7),
('http://www.tcm-classics.org/fangji#HouPoMaHuangTang','http://www.tcm-classics.org/yaowu#XiaoMai','一升',NULL,8),
('http://www.tcm-classics.org/fangji#HouPoMaHuangTang','http://www.tcm-classics.org/yaowu#WuWeiZi','半升',NULL,9),

-- 泽漆汤
('http://www.tcm-classics.org/fangji#ZeQiTang','http://www.tcm-classics.org/yaowu#BanXia','半升',NULL,1),
('http://www.tcm-classics.org/fangji#ZeQiTang','http://www.tcm-classics.org/yaowu#ZiCan','五两',NULL,2),
('http://www.tcm-classics.org/fangji#ZeQiTang','http://www.tcm-classics.org/yaowu#ShengJiang','五两',NULL,3),
('http://www.tcm-classics.org/fangji#ZeQiTang','http://www.tcm-classics.org/yaowu#BaiQian','五两',NULL,4),
('http://www.tcm-classics.org/fangji#ZeQiTang','http://www.tcm-classics.org/yaowu#GanCao','三两',NULL,5),
('http://www.tcm-classics.org/fangji#ZeQiTang','http://www.tcm-classics.org/yaowu#HuangQin','三两',NULL,6),
('http://www.tcm-classics.org/fangji#ZeQiTang','http://www.tcm-classics.org/yaowu#RenShen','三两',NULL,7),
('http://www.tcm-classics.org/fangji#ZeQiTang','http://www.tcm-classics.org/yaowu#GuiZhi','三两',NULL,8),
('http://www.tcm-classics.org/fangji#ZeQiTang','http://www.tcm-classics.org/yaowu#ZeQi','三斤',NULL,9),

-- 头风摩散
('http://www.tcm-classics.org/fangji#TianWuTouChiShiMiSan','http://www.tcm-classics.org/yaowu#FuZi','一枚','炮',1),
('http://www.tcm-classics.org/fangji#TianWuTouChiShiMiSan','http://www.tcm-classics.org/yaowu#Yan','等分',NULL,2);