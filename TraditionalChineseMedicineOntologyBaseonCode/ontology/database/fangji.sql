USE tcmdb;

-- ============================================================
-- 1. 方剂类别字典（无变化，保留全部）
-- ============================================================
INSERT INTO formula_category (uri, label) VALUES
('http://www.tcm-classics.org/tcm#GuiZhiTangLei',       '桂枝汤类'),
('http://www.tcm-classics.org/tcm#GuiZhiJiaFuZiLei',    '桂枝加附子类'),
('http://www.tcm-classics.org/tcm#GuiZhiJiaShaoYaoLei', '桂枝加芍药类'),
('http://www.tcm-classics.org/tcm#MaHuangTangLei',      '麻黄汤类'),
('http://www.tcm-classics.org/tcm#DaQingLongTangLei',   '大青龙汤类'),
('http://www.tcm-classics.org/tcm#XiaoQingLongTangLei', '小青龙汤类'),
('http://www.tcm-classics.org/tcm#GeGenTangLei',        '葛根汤类'),
('http://www.tcm-classics.org/tcm#ChaiHuTangLei',       '柴胡汤类'),
('http://www.tcm-classics.org/tcm#ChaiHuGuiZhiLei',     '柴胡桂枝类'),
('http://www.tcm-classics.org/tcm#DaChaiHuTangLei',     '大柴胡汤类'),
('http://www.tcm-classics.org/tcm#ZhiZiChiTangLei',     '栀子豉汤类'),
('http://www.tcm-classics.org/tcm#XieXinTangLei',       '泻心汤类'),
('http://www.tcm-classics.org/tcm#BanXiaXieXinLei',     '半夏泻心类'),
('http://www.tcm-classics.org/tcm#ChengQiTangLei',      '承气汤类'),
('http://www.tcm-classics.org/tcm#TaoHeChengQiLei',     '桃核承气类'),
('http://www.tcm-classics.org/tcm#BaiHuTangLei',        '白虎汤类'),
('http://www.tcm-classics.org/tcm#WuLingSanLei',        '五苓散类'),
('http://www.tcm-classics.org/tcm#ZhenWuTangLei',       '真武汤类'),
('http://www.tcm-classics.org/tcm#SiNiTangLei',         '四逆汤类'),
('http://www.tcm-classics.org/tcm#SiNiSanLei',          '四逆散类'),
('http://www.tcm-classics.org/tcm#LiZhongTangLei',      '理中汤类'),
('http://www.tcm-classics.org/tcm#DangGuiShaoYaoLei',   '当归芍药类'),
('http://www.tcm-classics.org/tcm#BieJiaJianWanLei',    '鳖甲煎丸类'),
('http://www.tcm-classics.org/tcm#ShuYuWanLei',         '薯蓣丸类'),
('http://www.tcm-classics.org/tcm#SuanZaoRenTangLei',   '酸枣仁汤类'),
('http://www.tcm-classics.org/tcm#HuangTuTangLei',      '黄土汤类'),
('http://www.tcm-classics.org/tcm#MaiMenDongTangLei',   '麦门冬汤类');

-- ============================================================
-- 2. 方剂主表（完整118首，含所有字段，pattern_uri直接写入）
-- ============================================================
INSERT INTO formula (
    uri, label, clause_number, source_clause, original_dosage,
    category_uri, pattern_uri,
    water_volume, final_yield_volume, single_dose_volume,
    decoction_heat, administration_method,
    dietary_contraindications, post_decoction_note,
    huxishu_note, clinical_key_points
) VALUES
-- ===== 桂枝汤类（18首） =====
('http://www.tcm-classics.org/fangji#GuiZhiTang', '桂枝汤', '第12条', '太阳病，头痛发热，汗出恶风，脉缓者，桂枝汤主之。', '桂枝三两、芍药三两、甘草二两、生姜三两、大枣十二枚', 'http://www.tcm-classics.org/tcm#GuiZhiTangLei', 'http://www.tcm-classics.org/bingzheng#TaiYangZhongFengZheng', '七升', '三升', '一升', '微火', '温服，服已须臾，啜热稀粥一升余，以助药力', '禁生冷、粘滑、肉面、五辛、酒酪', '若一服汗出病瘥，停后服，不必尽剂', '调和营卫，解肌发表。', '汗出恶风、脉缓'),
('http://www.tcm-classics.org/fangji#GuiZhiJiaGeGenTang', '桂枝加葛根汤', '第14条', '太阳病，项背强几几，反汗出恶风者，桂枝加葛根汤主之。', '葛根四两、桂枝三两、芍药三两、甘草二两、生姜三两、大枣十二枚', 'http://www.tcm-classics.org/tcm#GuiZhiTangLei', 'http://www.tcm-classics.org/bingzheng#TaiYangZhongFengZheng', NULL, NULL, NULL, NULL, NULL, NULL, NULL, '解肌发表，升津舒筋。', '项背强几几、汗出恶风'),
('http://www.tcm-classics.org/fangji#GuiZhiJiaHouPoXingRenTang', '桂枝加厚朴杏子汤', '第18条', '喘家作桂枝汤，加厚朴杏子佳。', '桂枝三两、芍药三两、甘草二两、生姜三两、大枣十二枚、厚朴二两、杏仁五十个', 'http://www.tcm-classics.org/tcm#GuiZhiTangLei', 'http://www.tcm-classics.org/bingzheng#TaiYangZhongFengZheng', NULL, NULL, NULL, NULL, NULL, NULL, NULL, '解表降气平喘。', '太阳中风兼喘'),
('http://www.tcm-classics.org/fangji#GuiZhiJiaFuZiTang', '桂枝加附子汤', '第20条', '太阳病，发汗遂漏不止，其人恶风，小便难，四肢微急，难以屈伸者，桂枝加附子汤主之。', '桂枝三两、芍药三两、甘草三两、生姜三两、大枣十二枚、附子一枚', 'http://www.tcm-classics.org/tcm#GuiZhiJiaFuZiLei', 'http://www.tcm-classics.org/bingzheng#TaiYangBingGangZheng', NULL, NULL, NULL, NULL, NULL, NULL, NULL, '扶阳固表。', '汗漏不止、恶风、四肢微急'),
('http://www.tcm-classics.org/fangji#GuiZhiQuShaoYaoTang', '桂枝去芍药汤', '第21条', '太阳病，下之后，脉促胸满者，桂枝去芍药汤主之。', '桂枝三两、甘草二两、生姜三两、大枣十二枚', 'http://www.tcm-classics.org/tcm#GuiZhiTangLei', 'http://www.tcm-classics.org/bingzheng#TaiYangBingGangZheng', NULL, NULL, NULL, NULL, NULL, NULL, NULL, '解肌通阳。', '脉促胸满'),
('http://www.tcm-classics.org/fangji#GuiZhiQuShaoYaoJiaFuZiTang', '桂枝去芍药加附子汤', '第22条', '太阳病，下之后，脉促胸满，微恶寒者，桂枝去芍药加附子汤主之。', '桂枝三两、甘草二两、生姜三两、大枣十二枚、附子一枚', 'http://www.tcm-classics.org/tcm#GuiZhiJiaFuZiLei', 'http://www.tcm-classics.org/bingzheng#TaiYangBingGangZheng', NULL, NULL, NULL, NULL, NULL, NULL, NULL, '解肌通阳、温经复阳。', '脉促胸满、微恶寒'),
('http://www.tcm-classics.org/fangji#GuiZhiXinJiaTang', '桂枝新加汤', '第62条', '发汗后，身疼痛，脉沉迟者，桂枝加芍药生姜各一两人参三两新加汤主之。', '桂枝三两、芍药四两、甘草二两、人参三两、大枣十二枚、生姜四两', 'http://www.tcm-classics.org/tcm#GuiZhiTangLei', 'http://www.tcm-classics.org/bingzheng#TaiYangBingGangZheng', NULL, NULL, NULL, NULL, NULL, NULL, NULL, '调和营卫，益气养血。', '汗后身痛脉沉迟'),
('http://www.tcm-classics.org/fangji#GuiZhiGanCaoTang', '桂枝甘草汤', '第64条', '发汗过多，其人叉手自冒心，心下悸，欲得按者，桂枝甘草汤主之。', '桂枝四两、甘草二两', 'http://www.tcm-classics.org/tcm#GuiZhiTangLei', 'http://www.tcm-classics.org/bingzheng#TaiYangBingGangZheng', NULL, NULL, NULL, NULL, NULL, NULL, NULL, '温通心阳。', '心下悸欲得按'),
('http://www.tcm-classics.org/fangji#FuLingGuiZhiBaiZhuGanCaoTang', '苓桂术甘汤', '金匮·痰饮-16', '心下有痰饮，胸胁支满，目眩，苓桂术甘汤主之。', '茯苓四两、桂枝三两、白术二两、甘草二两', 'http://www.tcm-classics.org/tcm#GuiZhiTangLei', 'http://www.tcm-classics.org/bingzheng#ShuiQiBing_LingGuiZhuGanTang', NULL, NULL, NULL, NULL, NULL, NULL, NULL, '健脾利水，温阳化饮。', '胸胁支满、目眩'),
('http://www.tcm-classics.org/fangji#ShaoYaoGanCaoTang', '芍药甘草汤', '第29条', '脚挛急，屈伸不利者，芍药甘草汤主之。', '芍药四两、甘草四两', 'http://www.tcm-classics.org/tcm#GuiZhiTangLei', 'http://www.tcm-classics.org/bingzheng#TaiYangBingGangZheng', NULL, NULL, NULL, NULL, NULL, NULL, NULL, '柔肝缓急。', '脚挛急'),
('http://www.tcm-classics.org/fangji#ShaoYaoGanCaoFuZiTang', '芍药甘草附子汤', '第68条', '发汗病不解，反恶寒者，虚故也，芍药甘草附子汤主之。', '芍药三两、甘草三两、附子一枚', 'http://www.tcm-classics.org/tcm#GuiZhiJiaFuZiLei', 'http://www.tcm-classics.org/bingzheng#TaiYangBingGangZheng', NULL, NULL, NULL, NULL, NULL, NULL, NULL, '阴阳双补。', '汗后反恶寒'),
('http://www.tcm-classics.org/fangji#GuiZhiFuZiTang', '桂枝附子汤', '第174条', '伤寒八九日，风湿相搏，身体疼烦，不能自转侧，不呕不渴，脉浮虚而涩者，桂枝附子汤主之。', '桂枝四两、附子三枚、生姜三两、大枣十二枚、甘草二两', 'http://www.tcm-classics.org/tcm#GuiZhiJiaFuZiLei', 'http://www.tcm-classics.org/bingzheng#TaiYangBingGangZheng', NULL, NULL, NULL, NULL, NULL, NULL, NULL, '祛风散寒除湿。', '身体疼烦、不能自转侧'),
('http://www.tcm-classics.org/fangji#BaiZhuFuZiTang', '白术附子汤', '第174条', '风湿相搏，身体疼烦，不能自转侧，不呕不渴，脉浮虚而涩者，白术附子汤主之。', '附子三枚、白术四两、生姜三两、大枣十二枚、甘草二两', 'http://www.tcm-classics.org/tcm#GuiZhiJiaFuZiLei', 'http://www.tcm-classics.org/bingzheng#TaiYangBingGangZheng', NULL, NULL, NULL, NULL, NULL, NULL, NULL, '祛风散寒除湿。', '身体疼烦、不能自转侧'),
('http://www.tcm-classics.org/fangji#GanCaoFuZiTang', '甘草附子汤', '第175条', '风湿相搏，骨节疼烦，掣痛不得屈伸，近之则痛剧，汗出短气，小便不利，恶风不欲去衣，或身微肿者，甘草附子汤主之。', '甘草二两、附子二枚、白术二两、桂枝四两', 'http://www.tcm-classics.org/tcm#GuiZhiJiaFuZiLei', 'http://www.tcm-classics.org/bingzheng#TaiYangBingGangZheng', NULL, NULL, NULL, NULL, NULL, NULL, NULL, '温阳散寒除湿。', '骨节掣痛不得屈伸'),
('http://www.tcm-classics.org/fangji#GuiZhiJiaLongGuMuLiTang', '桂枝加龙骨牡蛎汤', '金匮·血痹虚劳-8', '夫失精家，少腹弦急，阴头寒，目眩发落，脉极虚芤迟，男子失精，女子梦交，桂枝加龙骨牡蛎汤主之。', '桂枝三两、芍药三两、生姜三两、甘草二两、大枣十二枚、龙骨三两、牡蛎三两', 'http://www.tcm-classics.org/tcm#GuiZhiTangLei', 'http://www.tcm-classics.org/bingzheng#GuiZhiJiaLongGuMuLiTangZheng', '七升', '三升', NULL, NULL, '温服', NULL, NULL, '调和营卫，潜镇固涩。', '失精、梦交、少腹弦急'),
('http://www.tcm-classics.org/fangji#HuangQiJianZhongTang', '黄芪建中汤', '金匮·血痹虚劳-14', '虚劳里急，诸不足，黄芪建中汤主之。', '桂枝三两、甘草三两、芍药六两、生姜三两、大枣十二枚、饴糖一升、黄芪一两半', 'http://www.tcm-classics.org/tcm#GuiZhiTangLei', 'http://www.tcm-classics.org/bingzheng#HuangQiJianZhongTangZheng', NULL, NULL, NULL, NULL, NULL, NULL, NULL, '补气建中。', '虚劳里急诸不足'),
('http://www.tcm-classics.org/fangji#XiaoJianZhongTang', '小建中汤', '金匮·血痹虚劳-13', '虚劳里急，悸，衄，腹中痛，梦失精，四肢酸疼，手足烦热，咽干口燥，小建中汤主之。', '桂枝三两、甘草二两、芍药六两、生姜三两、大枣十二枚、饴糖一升', 'http://www.tcm-classics.org/tcm#GuiZhiTangLei', 'http://www.tcm-classics.org/bingzheng#XiaoJianZhongTangZheng', NULL, NULL, NULL, NULL, NULL, NULL, NULL, '温中补虚，和里缓急。', '里急腹痛、虚劳'),
('http://www.tcm-classics.org/fangji#DaJianZhongTang', '大建中汤', '金匮·腹满寒疝-14', '心胸中大寒痛，呕不能饮食，腹中寒，上冲皮起出见有头足，上下痛而不可触近，大建中汤主之。', '蜀椒二合、干姜四两、人参二两、饴糖一升', 'http://www.tcm-classics.org/tcm#GuiZhiTangLei', 'http://www.tcm-classics.org/bingzheng#DaJianZhongTangZheng', NULL, NULL, NULL, NULL, NULL, NULL, NULL, '温中散寒，补虚止痛。', '心胸大寒痛、呕不能食'),

-- ===== 麻黄汤类（8首）=====
('http://www.tcm-classics.org/fangji#MaHuangTang', '麻黄汤', '第35条', '太阳病，头痛发热，身疼腰痛，骨节疼痛，恶风，无汗而喘者，麻黄汤主之。', '麻黄三两、桂枝二两、甘草一两、杏仁七十个', 'http://www.tcm-classics.org/tcm#MaHuangTangLei', 'http://www.tcm-classics.org/bingzheng#TaiYangShangHanZheng', NULL, NULL, NULL, NULL, NULL, NULL, NULL, '发汗解表，宣肺平喘。', '无汗而喘、身疼痛'),
('http://www.tcm-classics.org/fangji#DaQingLongTang', '大青龙汤', '第38条', '太阳中风，脉浮紧，发热恶寒，身疼痛，不汗出而烦躁者，大青龙汤主之。', '麻黄六两、桂枝二两、甘草二两、杏仁四十枚、生姜三两、大枣十枚、石膏如鸡子大', 'http://www.tcm-classics.org/tcm#DaQingLongTangLei', 'http://www.tcm-classics.org/bingzheng#DaQingLongTangZheng', NULL, NULL, NULL, NULL, NULL, NULL, NULL, '发汗解表，清热除烦。', '不汗出而烦躁'),
('http://www.tcm-classics.org/fangji#XiaoQingLongTang', '小青龙汤', '第40条', '伤寒表不解，心下有水气，干呕发热而咳，或渴，或利，或噎，或小便不利、少腹满，或喘者，小青龙汤主之。', '麻黄三两、芍药三两、细辛三两、干姜三两、甘草三两、桂枝三两、五味子半升、半夏半升', 'http://www.tcm-classics.org/tcm#XiaoQingLongTangLei', 'http://www.tcm-classics.org/bingzheng#XiaoQingLongTangZheng', NULL, NULL, NULL, NULL, NULL, NULL, NULL, '解表化饮，止咳平喘。', '干呕发热而咳'),
('http://www.tcm-classics.org/fangji#MaHuangXingRenGanCaoShiGaoTang', '麻杏石甘汤', '第63条', '发汗后，不可更行桂枝汤，汗出而喘，无大热者，可与麻黄杏仁甘草石膏汤。', '麻黄四两、杏仁五十个、甘草二两、石膏半斤', 'http://www.tcm-classics.org/tcm#MaHuangTangLei', 'http://www.tcm-classics.org/bingzheng#MaXingShiGanTangZheng', NULL, NULL, NULL, NULL, NULL, NULL, NULL, '清热宣肺平喘。', '汗出而喘'),
('http://www.tcm-classics.org/fangji#MaHuangFuZiXiXinTang', '麻黄附子细辛汤', '第301条', '少阴病，始得之，反发热脉沉者，麻黄附子细辛汤主之。', '麻黄二两、细辛二两、附子一枚', 'http://www.tcm-classics.org/tcm#MaHuangTangLei', 'http://www.tcm-classics.org/bingzheng#MaHuangFuZiXiXinTangZheng', NULL, NULL, NULL, NULL, NULL, NULL, NULL, '温经解表。', '反发热脉沉'),
('http://www.tcm-classics.org/fangji#MaHuangFuZiGanCaoTang', '麻黄附子甘草汤', '第302条', '少阴病，得之二三日，麻黄附子甘草汤微发汗。', '麻黄二两、甘草二两、附子一枚', 'http://www.tcm-classics.org/tcm#MaHuangTangLei', 'http://www.tcm-classics.org/bingzheng#MaHuangFuZiXiXinTangZheng', NULL, NULL, NULL, NULL, NULL, NULL, NULL, '温经微汗。', '少阴病得之二三日'),
('http://www.tcm-classics.org/fangji#MaHuangJiaZhuTang', '麻黄加术汤', '金匮·痉湿暍-20', '湿家身疼烦，可与麻黄加术汤发其汗。', '麻黄三两、桂枝二两、甘草一两、杏仁七十个、白术四两', 'http://www.tcm-classics.org/tcm#MaHuangTangLei', 'http://www.tcm-classics.org/bingzheng#TaiYangBingGangZheng', NULL, NULL, NULL, NULL, NULL, NULL, NULL, '解表散寒祛湿。', '湿家身疼烦'),
('http://www.tcm-classics.org/fangji#MaHuangYiYiGanCaoTang', '麻黄薏苡甘草汤', '金匮·痉湿暍-21', '病者一身尽疼，发热，日晡所剧者，名风湿，麻黄薏苡甘草汤主之。', '麻黄半两、甘草一两、薏苡仁半两、杏仁十个', 'http://www.tcm-classics.org/tcm#MaHuangTangLei', 'http://www.tcm-classics.org/bingzheng#TaiYangBingGangZheng', NULL, NULL, NULL, NULL, NULL, NULL, NULL, '祛风除湿清热。', '一身尽疼发热'),

-- ===== 葛根汤类（3首）=====
('http://www.tcm-classics.org/fangji#GeGenTang', '葛根汤', '第31条', '太阳病，项背强几几，无汗恶风，葛根汤主之。', '葛根四两、麻黄三两、桂枝二两、生姜三两、甘草二两、芍药二两、大枣十二枚', 'http://www.tcm-classics.org/tcm#GeGenTangLei', 'http://www.tcm-classics.org/bingzheng#TaiYangShangHanZheng', NULL, NULL, NULL, NULL, NULL, NULL, NULL, '发汗解表，升津舒筋。', '项背强几几、无汗恶风'),
('http://www.tcm-classics.org/fangji#GeGenJiaBanXiaTang', '葛根加半夏汤', '第33条', '太阳与阳明合病，不下利但呕者，葛根加半夏汤主之。', '葛根四两、麻黄三两、桂枝二两、生姜三两、甘草二两、芍药二两、大枣十二枚、半夏半升', 'http://www.tcm-classics.org/tcm#GeGenTangLei', 'http://www.tcm-classics.org/bingzheng#TaiYangShangHanZheng', NULL, NULL, NULL, NULL, NULL, NULL, NULL, '解表降逆止呕。', '太阳阳明合病而呕'),
('http://www.tcm-classics.org/fangji#GeGenHuangQinHuangLianTang', '葛根黄芩黄连汤', '第34条', '太阳病，桂枝证，医反下之，利遂不止，脉促者，表未解也；喘而汗出者，葛根黄芩黄连汤主之。', '葛根半斤、甘草二两、黄芩三两、黄连三两', 'http://www.tcm-classics.org/tcm#GeGenTangLei', 'http://www.tcm-classics.org/bingzheng#GeGenHuangQinHuangLianTangZheng', NULL, NULL, NULL, NULL, NULL, NULL, NULL, '表里双解，清热止利。', '利遂不止、喘而汗出'),

-- ===== 柴胡汤类（7首）=====
('http://www.tcm-classics.org/fangji#XiaoChaiHuTang', '小柴胡汤', '第96条', '伤寒五六日中风，往来寒热，胸胁苦满，嘿嘿不欲饮食，心烦喜呕，小柴胡汤主之。', '柴胡半斤、黄芩三两、人参三两、半夏半升、甘草三两、生姜三两、大枣十二枚', 'http://www.tcm-classics.org/tcm#ChaiHuTangLei', 'http://www.tcm-classics.org/bingzheng#XiaoChaiHuTangZheng', '一斗二升', '六升', '一升', NULL, '温服，日三服', NULL, NULL, '和解少阳。', '往来寒热、胸胁苦满'),
('http://www.tcm-classics.org/fangji#DaChaiHuTang', '大柴胡汤', '第103条', '呕不止，心下急，郁郁微烦者，为未解也，与大柴胡汤下之则愈。', '柴胡半斤、黄芩三两、芍药三两、半夏半升、生姜五两、枳实四枚、大枣十二枚、大黄二两', 'http://www.tcm-classics.org/tcm#DaChaiHuTangLei', 'http://www.tcm-classics.org/bingzheng#DaChaiHuTangZheng', NULL, NULL, NULL, NULL, NULL, NULL, NULL, '和解少阳，攻下里实。', '呕不止、心下急'),
('http://www.tcm-classics.org/fangji#ChaiHuGuiZhiTang', '柴胡桂枝汤', '第146条', '伤寒六七日，发热微恶寒，支节烦疼，微呕，心下支结，外证未去者，柴胡桂枝汤主之。', '桂枝一两半、芍药一两半、黄芩一两半、人参一两半、甘草一两、半夏二合半、大枣六枚、生姜一两半、柴胡四两', 'http://www.tcm-classics.org/tcm#ChaiHuGuiZhiLei', 'http://www.tcm-classics.org/bingzheng#ChaiHuGuiZhiTangZheng', NULL, NULL, NULL, NULL, NULL, NULL, NULL, '和解少阳，解肌发表。', '发热微恶寒、支节烦疼'),
('http://www.tcm-classics.org/fangji#ChaiHuJiaMangXiaoTang', '柴胡加芒硝汤', '第104条', '伤寒十三日，不解，胸胁满而呕，日晡所发潮热，已而微利，此本柴胡证，下之以不得利，今反利者，知医以丸药下之，此非其治也。潮热者实也，先宜服小柴胡汤以解外，后以柴胡加芒硝汤主之。', '柴胡二两十六铢、黄芩一两、人参一两、甘草一两、生姜一两、半夏二十铢、大枣四枚、芒硝二两', 'http://www.tcm-classics.org/tcm#ChaiHuTangLei', 'http://www.tcm-classics.org/bingzheng#ShaoYangBingGangZheng', NULL, NULL, NULL, NULL, NULL, NULL, NULL, '和解少阳，轻下里热。', '胸胁满而呕、日晡潮热'),
('http://www.tcm-classics.org/fangji#ChaiHuGuiZhiGanJiangTang', '柴胡桂枝干姜汤', '第147条', '伤寒五六日，已发汗而复下之，胸胁满微结，小便不利，渴而不呕，但头汗出，往来寒热，心烦者，此为未解也，柴胡桂枝干姜汤主之。', '柴胡半斤、桂枝三两、干姜二两、瓜蒌根四两、黄芩三两、牡蛎二两、甘草二两', 'http://www.tcm-classics.org/tcm#ChaiHuTangLei', 'http://www.tcm-classics.org/bingzheng#XiaoChaiHuTangZheng', NULL, NULL, NULL, NULL, NULL, NULL, NULL, '和解少阳，温化水饮。', '胸胁满微结、小便不利、渴而不呕'),
('http://www.tcm-classics.org/fangji#ChaiHuJiaLongGuMuLiTang', '柴胡加龙骨牡蛎汤', '第107条', '伤寒八九日，下之，胸满烦惊，小便不利，谵语，一身尽重，不可转侧者，柴胡加龙骨牡蛎汤主之。', '柴胡四两、龙骨一两半、黄芩一两半、生姜一两半、人参一两半、桂枝一两半、茯苓一两半、半夏二合半、大黄二两、牡蛎一两半、大枣六枚、铅丹一两半', 'http://www.tcm-classics.org/tcm#ChaiHuTangLei', 'http://www.tcm-classics.org/bingzheng#ShaoYangBingGangZheng', NULL, NULL, NULL, NULL, NULL, NULL, NULL, '和解少阳，镇惊安神。', '胸满烦惊、小便不利'),
('http://www.tcm-classics.org/fangji#SiNiSan', '四逆散', '第318条', '少阴病，四逆，其人或咳，或悸，或小便不利，或腹中痛，或泄利下重者，四逆散主之。', '甘草十分、枳实十分、柴胡十分、芍药十分', 'http://www.tcm-classics.org/tcm#SiNiSanLei', 'http://www.tcm-classics.org/bingzheng#SiNiSanZheng', NULL, NULL, NULL, NULL, NULL, NULL, NULL, '疏肝理脾，透达郁阳。', '四逆（非寒厥）'),

-- ===== 白虎汤类（3首）=====
('http://www.tcm-classics.org/fangji#BaiHuTang', '白虎汤', '第176条', '伤寒，脉浮滑，此以表有热，里有寒，白虎汤主之。（胡老校：表里俱热）', '知母六两、石膏一斤、甘草二两、粳米六合', 'http://www.tcm-classics.org/tcm#BaiHuTangLei', 'http://www.tcm-classics.org/bingzheng#BaiHuTangZheng', NULL, NULL, NULL, NULL, NULL, NULL, NULL, '清热生津。', '大热、大汗、大渴、脉洪大'),
('http://www.tcm-classics.org/fangji#BaiHuJiaRenShenTang', '白虎加人参汤', '第26条', '服桂枝汤，大汗出后，大烦渴不解，脉洪大者，白虎加人参汤主之。', '知母六两、石膏一斤、甘草二两、粳米六合、人参三两', 'http://www.tcm-classics.org/tcm#BaiHuTangLei', 'http://www.tcm-classics.org/bingzheng#BaiHuTangZheng', NULL, NULL, NULL, NULL, NULL, NULL, NULL, '清热益气生津。', '大烦渴不解、脉洪大'),
('http://www.tcm-classics.org/fangji#ZhuYeShiGaoTang', '竹叶石膏汤', '第397条', '伤寒解后，虚羸少气，气逆欲吐，竹叶石膏汤主之。', '竹叶二把、石膏一斤、半夏半升、麦门冬一升、人参二两、甘草二两、粳米半升', 'http://www.tcm-classics.org/tcm#BaiHuTangLei', 'http://www.tcm-classics.org/bingzheng#ZhuYeShiGaoTangZheng', NULL, NULL, NULL, NULL, NULL, NULL, NULL, '清热益气养阴。', '虚羸少气、气逆欲吐'),

-- ===== 承气汤类（5首）=====
('http://www.tcm-classics.org/fangji#DaChengQiTang', '大承气汤', '第208条', '阳明病，脉迟，虽汗出不恶寒者，其身必重，短气，腹满而喘，有潮热者，此外欲解，可攻里也。手足濈然汗出者，此大便已硬也，大承气汤主之。', '大黄四两、厚朴半斤、枳实五枚、芒硝三合', 'http://www.tcm-classics.org/tcm#ChengQiTangLei', 'http://www.tcm-classics.org/bingzheng#DaChengQiTangZheng', NULL, NULL, NULL, NULL, NULL, NULL, NULL, '峻下热结。', '痞满燥实坚'),
('http://www.tcm-classics.org/fangji#XiaoChengQiTang', '小承气汤', '第213条', '阳明病，其人多汗，以津液外出，胃中燥，大便必硬，硬则谵语，小承气汤主之。', '大黄四两、厚朴二两、枳实三枚', 'http://www.tcm-classics.org/tcm#ChengQiTangLei', 'http://www.tcm-classics.org/bingzheng#XiaoChengQiTangZheng', NULL, NULL, NULL, NULL, NULL, NULL, NULL, '轻下热结。', '大便硬、谵语'),
('http://www.tcm-classics.org/fangji#TiaoWeiChengQiTang', '调胃承气汤', '第207条', '阳明病，不吐不下，心烦者，可与调胃承气汤。', '大黄四两、甘草二两、芒硝半升', 'http://www.tcm-classics.org/tcm#ChengQiTangLei', 'http://www.tcm-classics.org/bingzheng#TiaoWeiChengQiTangZheng', NULL, NULL, NULL, NULL, NULL, NULL, NULL, '缓下热结，调和胃气。', '心烦'),
('http://www.tcm-classics.org/fangji#TaoHeChengQiTang', '桃核承气汤', '第106条', '太阳病不解，热结膀胱，其人如狂，血自下，下者愈。但少腹急结者，乃可攻之，宜桃核承气汤。', '桃仁五十个、大黄四两、桂枝二两、甘草二两、芒硝二两', 'http://www.tcm-classics.org/tcm#TaoHeChengQiLei', 'http://www.tcm-classics.org/bingzheng#TaoHeChengQiTangZheng', NULL, NULL, NULL, NULL, NULL, NULL, NULL, '逐瘀泻热。', '如狂、少腹急结'),
('http://www.tcm-classics.org/fangji#DiDangTang', '抵当汤', '第124条', '太阳病六七日，表证仍在，脉微而沉，反不结胸，其人发狂者，以热在下焦，少腹当硬满，小便自利者，下血乃愈，抵当汤主之。', '水蛭三十个、虻虫三十个、桃仁二十个、大黄三两', 'http://www.tcm-classics.org/tcm#TaoHeChengQiLei', 'http://www.tcm-classics.org/bingzheng#DiDangTangZheng', NULL, NULL, NULL, NULL, NULL, NULL, NULL, '破血逐瘀。', '发狂、少腹硬满、小便自利'),

-- ===== 理中汤类（3首）=====
('http://www.tcm-classics.org/fangji#LiZhongTang', '理中汤', '第386条', '霍乱，头痛发热，身疼痛，热多欲饮水者，五苓散主之；寒多不用水者，理中丸主之。', '人参三两、干姜三两、甘草三两、白术三两', 'http://www.tcm-classics.org/tcm#LiZhongTangLei', 'http://www.tcm-classics.org/bingzheng#LiZhongTangZheng', NULL, NULL, NULL, NULL, NULL, NULL, NULL, '温中散寒，健脾益气。', '寒多不用水'),
('http://www.tcm-classics.org/fangji#FuZiLiZhongTang', '附子理中汤', '无直接条文', '自利不渴者，属太阴，以其脏有寒故也，当温之，宜服四逆辈。（理中汤加附子为附子理中汤）', '人参三两、干姜四两半、甘草三两、白术三两、附子一枚', 'http://www.tcm-classics.org/tcm#LiZhongTangLei', 'http://www.tcm-classics.org/bingzheng#LiZhongTangZheng', NULL, NULL, NULL, NULL, NULL, NULL, NULL, '温阳健脾。', '自利不渴、太阴虚寒'),
('http://www.tcm-classics.org/fangji#WuZhuYuTang', '吴茱萸汤', '第243、309、378条', '食谷欲呕，属阳明也，吴茱萸汤主之。少阴病，吐利，手足逆冷，烦躁欲死者，吴茱萸汤主之。干呕吐涎沫，头痛者，吴茱萸汤主之。', '吴茱萸一升、人参三两、生姜六两、大枣十二枚', 'http://www.tcm-classics.org/tcm#LiZhongTangLei', 'http://www.tcm-classics.org/bingzheng#WuZhuYuTangZheng', NULL, NULL, NULL, NULL, NULL, NULL, NULL, '温中降逆止呕。', '食谷欲呕、头痛吐涎沫'),

-- ===== 四逆汤类（6首）=====
('http://www.tcm-classics.org/fangji#SiNiTang', '四逆汤', '第323条', '少阴病，脉沉者，急温之，宜四逆汤。', '甘草二两、干姜一两半、附子一枚', 'http://www.tcm-classics.org/tcm#SiNiTangLei', 'http://www.tcm-classics.org/bingzheng#SiNiTangZheng_ShaoYin', NULL, NULL, NULL, NULL, NULL, NULL, NULL, '回阳救逆。', '脉沉、四肢厥逆'),
('http://www.tcm-classics.org/fangji#TongMaiSiNiTang', '通脉四逆汤', '第317条', '少阴病，下利清谷，里寒外热，手足厥逆，脉微欲绝，身反不恶寒，其人面色赤，或腹痛，或干呕，或咽痛，或利止脉不出者，通脉四逆汤主之。', '甘草二两、附子大者一枚、干姜三两', 'http://www.tcm-classics.org/tcm#SiNiTangLei', 'http://www.tcm-classics.org/bingzheng#TongMaiSiNiTangZheng', NULL, NULL, NULL, NULL, NULL, NULL, NULL, '回阳通脉。', '里寒外热、脉微欲绝'),
('http://www.tcm-classics.org/fangji#BaiTongTang', '白通汤', '第314条', '少阴病，下利，白通汤主之。', '葱白四茎、干姜一两、附子一枚', 'http://www.tcm-classics.org/tcm#SiNiTangLei', 'http://www.tcm-classics.org/bingzheng#ShaoYinBingGangZheng', NULL, NULL, NULL, NULL, NULL, NULL, NULL, '通阳散寒。', '少阴下利'),
('http://www.tcm-classics.org/fangji#BaiTongJiaZhuDanZhiTang', '白通加猪胆汁汤', '第315条', '少阴病，下利，脉微者，与白通汤。利不止，厥逆无脉，干呕烦者，白通加猪胆汁汤主之。', '葱白四茎、干姜一两、附子一枚、人尿五合、猪胆汁一合', 'http://www.tcm-classics.org/tcm#SiNiTangLei', 'http://www.tcm-classics.org/bingzheng#ShaoYinBingGangZheng', NULL, NULL, NULL, NULL, NULL, NULL, NULL, '通阳散寒，反佐清热。', '下利不止、厥逆无脉'),
('http://www.tcm-classics.org/fangji#ZhenWuTang', '真武汤', '第316条', '少阴病，二三日不已，至四五日，腹痛，小便不利，四肢沉重疼痛，自下利者，此为有水气……真武汤主之。', '茯苓三两、芍药三两、白术二两、生姜三两、附子一枚', 'http://www.tcm-classics.org/tcm#ZhenWuTangLei', 'http://www.tcm-classics.org/bingzheng#ZhenWuTangZheng', NULL, NULL, NULL, NULL, NULL, NULL, NULL, '温阳利水。', '四肢沉重、小便不利、下利'),
('http://www.tcm-classics.org/fangji#HuangLianEJiaoTang', '黄连阿胶汤', '第303条', '少阴病，得之二三日以上，心中烦，不得卧，黄连阿胶汤主之。', '黄连四两、黄芩二两、芍药二两、鸡子黄二枚、阿胶三两', 'http://www.tcm-classics.org/tcm#SiNiTangLei', 'http://www.tcm-classics.org/bingzheng#HuangLianEJiaoTangZheng', NULL, NULL, NULL, NULL, NULL, NULL, NULL, '滋阴降火，交通心肾。', '心中烦、不得卧'),

-- ===== 厥阴病篇（5首）=====
('http://www.tcm-classics.org/fangji#WuMeiWan', '乌梅丸', '第338条', '蛔厥者，其人当吐蛔……蛔厥者，乌梅丸主之。又主久利。', '乌梅三百枚、细辛六两、干姜十两、黄连十六两、当归四两、附子六两、蜀椒四两、桂枝六两、人参六两、黄柏六两', 'http://www.tcm-classics.org/tcm#SiNiTangLei', 'http://www.tcm-classics.org/bingzheng#WuMeiWanZheng', NULL, NULL, NULL, NULL, NULL, NULL, NULL, '寒热并用，安蛔止痛。', '蛔厥、久利'),
('http://www.tcm-classics.org/fangji#DangGuiSiNiTang', '当归四逆汤', '第351条', '手足厥寒，脉细欲绝者，当归四逆汤主之。', '当归三两、桂枝三两、芍药三两、细辛三两、甘草二两、通草二两、大枣二十五枚', 'http://www.tcm-classics.org/tcm#SiNiTangLei', 'http://www.tcm-classics.org/bingzheng#DangGuiSiNiTangZheng', NULL, NULL, NULL, NULL, NULL, NULL, NULL, '养血温经通脉。', '手足厥寒、脉细欲绝'),
('http://www.tcm-classics.org/fangji#DangGuiSiNiJiaWuZhuYuShengJiangTang', '当归四逆加吴茱萸生姜汤', '第352条', '若其人内有久寒者，宜当归四逆加吴茱萸生姜汤。', '当归三两、桂枝三两、芍药三两、细辛三两、甘草二两、通草二两、大枣二十五枚、吴茱萸二升、生姜半斤', 'http://www.tcm-classics.org/tcm#SiNiTangLei', 'http://www.tcm-classics.org/bingzheng#DangGuiSiNiTangZheng', NULL, NULL, NULL, NULL, NULL, NULL, NULL, '养血温经，散寒降逆。', '手足厥寒、内有久寒'),
('http://www.tcm-classics.org/fangji#BaiTouWengTang', '白头翁汤', '第371条', '热利下重者，白头翁汤主之。', '白头翁二两、黄柏三两、黄连三两、秦皮三两', 'http://www.tcm-classics.org/tcm#SiNiTangLei', 'http://www.tcm-classics.org/bingzheng#BaiTouWengTangZheng', NULL, NULL, NULL, NULL, NULL, NULL, NULL, '清热燥湿止痢。', '热利下重'),
('http://www.tcm-classics.org/fangji#GanJiangHuangQinHuangLianRenShenTang', '干姜黄芩黄连人参汤', '第359条', '伤寒本自寒下，医复吐下之，寒格更逆吐下，若食入口即吐，干姜黄芩黄连人参汤主之。', '干姜三两、黄芩三两、黄连三两、人参三两', 'http://www.tcm-classics.org/tcm#SiNiTangLei', 'http://www.tcm-classics.org/bingzheng#GanJiangHuangQinHuangLianRenShenTangZheng', NULL, NULL, NULL, NULL, NULL, NULL, NULL, '辛开苦降，寒热并用。', '食入口即吐'),

-- ===== 泻心汤类（5首）=====
('http://www.tcm-classics.org/fangji#BanXiaXieXinTang', '半夏泻心汤', '第149条', '伤寒五六日，呕而发热者，柴胡汤证具，而以他药下之，柴胡证仍在者，复与柴胡汤。此虽已下之，不为逆，必蒸蒸而振，却发热汗出而解。若心下满而硬痛者，此为结胸也，大陷胸汤主之；但满而不痛者，此为痞，柴胡不中与之，宜半夏泻心汤。', '半夏半升、黄芩三两、干姜三两、人参三两、甘草三两、黄连一两、大枣十二枚', 'http://www.tcm-classics.org/tcm#BanXiaXieXinLei', 'http://www.tcm-classics.org/bingzheng#TaiYangBingGangZheng', NULL, NULL, NULL, NULL, NULL, NULL, NULL, '辛开苦降，和中消痞。', '心下痞满而不痛'),
('http://www.tcm-classics.org/fangji#ShengJiangXieXinTang', '生姜泻心汤', '第157条', '伤寒汗出解之后，胃中不和，心下痞硬，干噫食臭，胁下有水气，腹中雷鸣下利者，生姜泻心汤主之。', '生姜四两、甘草三两、人参三两、干姜一两、黄芩三两、半夏半升、黄连一两、大枣十二枚', 'http://www.tcm-classics.org/tcm#BanXiaXieXinLei', 'http://www.tcm-classics.org/bingzheng#TaiYangBingGangZheng', NULL, NULL, NULL, NULL, NULL, NULL, NULL, '和胃消痞，散水止利。', '心下痞硬、干噫食臭、雷鸣下利'),
('http://www.tcm-classics.org/fangji#GanCaoXieXinTang', '甘草泻心汤', '第158条', '伤寒中风，医反下之，其人下利日数十行，谷不化，腹中雷鸣，心下痞硬而满，干呕心烦不得安。医见心下痞，谓病不尽，复下之，其痞益甚，此非结热，但以胃中虚，客气上逆，故使硬也，甘草泻心汤主之。', '甘草四两、黄芩三两、干姜三两、半夏半升、黄连一两、大枣十二枚、人参三两', 'http://www.tcm-classics.org/tcm#BanXiaXieXinLei', 'http://www.tcm-classics.org/bingzheng#TaiYangBingGangZheng', NULL, NULL, NULL, NULL, NULL, NULL, NULL, '补中消痞。', '心下痞硬而满、下利日数十行'),
('http://www.tcm-classics.org/fangji#DaHuangHuangLianXieXinTang', '大黄黄连泻心汤', '第154条', '心下痞，按之濡，其脉关上浮者，大黄黄连泻心汤主之。', '大黄二两、黄连一两', 'http://www.tcm-classics.org/tcm#XieXinTangLei', 'http://www.tcm-classics.org/bingzheng#TaiYangBingGangZheng', NULL, NULL, NULL, NULL, NULL, NULL, NULL, '清热消痞。', '心下痞、按之濡'),
('http://www.tcm-classics.org/fangji#FuZiXieXinTang', '附子泻心汤', '第155条', '心下痞，而复恶寒汗出者，附子泻心汤主之。', '大黄二两、黄连一两、黄芩一两、附子一枚', 'http://www.tcm-classics.org/tcm#XieXinTangLei', 'http://www.tcm-classics.org/bingzheng#TaiYangBingGangZheng', NULL, NULL, NULL, NULL, NULL, NULL, NULL, '清热消痞，温经回阳。', '心下痞、恶寒汗出'),

-- ===== 蓄水蓄血及其他太阳变证（8首）=====
('http://www.tcm-classics.org/fangji#WuLingSan', '五苓散', '第71条', '若脉浮，小便不利，微热消渴者，五苓散主之。', '猪苓十八铢、泽泻一两六铢、白术十八铢、茯苓十八铢、桂枝半两', 'http://www.tcm-classics.org/tcm#WuLingSanLei', 'http://www.tcm-classics.org/bingzheng#WuLingSanZheng', NULL, NULL, NULL, NULL, NULL, NULL, NULL, '利水渗湿，温阳化气。', '小便不利、口渴'),
('http://www.tcm-classics.org/fangji#FuLingGanCaoTang', '茯苓甘草汤', '第73条', '伤寒，汗出而渴者，五苓散主之；不渴者，茯苓甘草汤主之。', '茯苓二两、桂枝二两、甘草一两、生姜三两', 'http://www.tcm-classics.org/tcm#WuLingSanLei', 'http://www.tcm-classics.org/bingzheng#TaiYangBingGangZheng', NULL, NULL, NULL, NULL, NULL, NULL, NULL, '温中化饮。', '汗出不渴'),
('http://www.tcm-classics.org/fangji#XuanFuDaiZheTang', '旋覆代赭汤', '第161条', '伤寒发汗，若吐若下，解后，心下痞硬，噫气不除者，旋覆代赭汤主之。', '旋覆花三两、人参二两、生姜五两、代赭石一两、甘草三两、半夏半升、大枣十二枚', 'http://www.tcm-classics.org/tcm#XieXinTangLei', 'http://www.tcm-classics.org/bingzheng#TaiYangBingGangZheng', NULL, NULL, NULL, NULL, NULL, NULL, NULL, '降逆化痰，益气和胃。', '心下痞硬、噫气不除'),
('http://www.tcm-classics.org/fangji#HouPoShengJiangBanXiaGanCaoRenShenTang', '厚朴生姜半夏甘草人参汤', '第66条', '发汗后，腹胀满者，厚朴生姜半夏甘草人参汤主之。', '厚朴半斤、生姜半斤、半夏半升、甘草二两、人参一两', 'http://www.tcm-classics.org/tcm#XieXinTangLei', 'http://www.tcm-classics.org/bingzheng#TaiYangBingGangZheng', NULL, NULL, NULL, NULL, NULL, NULL, NULL, '行气消胀，健脾和胃。', '腹胀满'),

-- ===== 栀子豉汤类（4首）=====
('http://www.tcm-classics.org/fangji#ZhiZiChiTang', '栀子豉汤', '第76条', '发汗吐下后，虚烦不得眠，若剧者，必反复颠倒，心中懊憹，栀子豉汤主之。', '栀子十四个、香豉四合', 'http://www.tcm-classics.org/tcm#ZhiZiChiTangLei', 'http://www.tcm-classics.org/bingzheng#YangMingBingGangZheng', NULL, NULL, NULL, NULL, NULL, NULL, NULL, '清宣郁热。', '虚烦不得眠、心中懊憹'),
('http://www.tcm-classics.org/fangji#ZhiZiGanCaoTang', '栀子甘草汤', '第76条', '若少气者，栀子甘草汤主之。', '栀子十四个、香豉四合、甘草二两', 'http://www.tcm-classics.org/tcm#ZhiZiChiTangLei', 'http://www.tcm-classics.org/bingzheng#YangMingBingGangZheng', NULL, NULL, NULL, NULL, NULL, NULL, NULL, '清宣郁热，益气和中。', '虚烦不得眠、少气'),
('http://www.tcm-classics.org/fangji#ZhiZiShengJiangTang', '栀子生姜汤', '第76条', '若呕者，栀子生姜汤主之。', '栀子十四个、香豉四合、生姜五两', 'http://www.tcm-classics.org/tcm#ZhiZiChiTangLei', 'http://www.tcm-classics.org/bingzheng#YangMingBingGangZheng', NULL, NULL, NULL, NULL, NULL, NULL, NULL, '清宣郁热，降逆止呕。', '虚烦不得眠、呕'),
('http://www.tcm-classics.org/fangji#ZhiZiHouPoTang', '栀子厚朴汤', '第79条', '伤寒下后，心烦腹满，卧起不安者，栀子厚朴汤主之。', '栀子十四个、厚朴四两、枳实四枚', 'http://www.tcm-classics.org/tcm#ZhiZiChiTangLei', 'http://www.tcm-classics.org/bingzheng#YangMingBingGangZheng', NULL, NULL, NULL, NULL, NULL, NULL, NULL, '清宣郁热，行气除满。', '心烦腹满、卧起不安'),

-- ===== 金匮杂病方（原有+新增，共29首） =====
-- 原有20首
('http://www.tcm-classics.org/fangji#GuaLouXieBaiBaiJiuTang', '瓜蒌薤白白酒汤', '金匮·胸痹-3', '胸痹之病，喘息咳唾，胸背痛，短气，寸口脉沉而迟，关上小紧数，瓜蒌薤白白酒汤主之。', '瓜蒌实一枚、薤白半斤、白酒七升', 'http://www.tcm-classics.org/tcm#XieXinTangLei', 'http://www.tcm-classics.org/bingzheng#XiongBi_GuaLouXieBaiBaiJiuTang', NULL, NULL, NULL, NULL, NULL, NULL, NULL, '通阳散结，行气化痰。', '胸痹喘息咳唾'),
('http://www.tcm-classics.org/fangji#GuaLouXieBaiBanXiaTang', '瓜蒌薤白半夏汤', '金匮·胸痹-4', '胸痹不得卧，心痛彻背者，瓜蒌薤白半夏汤主之。', '瓜蒌实一枚、薤白三两、半夏半升、白酒一斗', 'http://www.tcm-classics.org/tcm#XieXinTangLei', 'http://www.tcm-classics.org/bingzheng#XiongBi_GuaLouXieBaiBaiJiuTang', NULL, NULL, NULL, NULL, NULL, NULL, NULL, '通阳散结，化痰降逆。', '胸痹不得卧、心痛彻背'),
('http://www.tcm-classics.org/fangji#ZhiShiXieBaiGuiZhiTang', '枳实薤白桂枝汤', '金匮·胸痹-5', '胸痹心中痞，留气结在胸，胸满，胁下逆抢心，枳实薤白桂枝汤主之。', '枳实四枚、厚朴四两、薤白半斤、桂枝一两、瓜蒌实一枚', 'http://www.tcm-classics.org/tcm#XieXinTangLei', 'http://www.tcm-classics.org/bingzheng#XiongBi_GuaLouXieBaiBaiJiuTang', NULL, NULL, NULL, NULL, NULL, NULL, NULL, '通阳散结，行气降逆。', '胸痹心中痞、胁下逆抢心'),
('http://www.tcm-classics.org/fangji#HouPoQiWuTang', '厚朴七物汤', '金匮·腹满-9', '病腹满，发热十日，脉浮而数，饮食如故，厚朴七物汤主之。', '厚朴半斤、甘草三两、大黄三两、大枣十枚、枳实五枚、桂枝二两、生姜五两', 'http://www.tcm-classics.org/tcm#ChengQiTangLei', 'http://www.tcm-classics.org/bingzheng#FuTong_HouPoQiWuTang', NULL, NULL, NULL, NULL, NULL, NULL, NULL, '行气除满，表里双解。', '腹满发热'),
('http://www.tcm-classics.org/fangji#GuiZhiShaoYaoZhiMuTang', '桂枝芍药知母汤', '金匮·中风历节-8', '诸肢节疼痛，身体尪羸，脚肿如脱，头眩短气，温温欲吐，桂枝芍药知母汤主之。', '桂枝四两、芍药三两、甘草二两、麻黄二两、生姜五两、白术五两、知母四两、防风四两、附子二枚', 'http://www.tcm-classics.org/tcm#XieXinTangLei', 'http://www.tcm-classics.org/bingzheng#XueBi_GuiZhiShaoYaoZhiMuTang', NULL, NULL, NULL, NULL, NULL, NULL, NULL, '祛风除湿，温经散寒，清热养阴。', '诸肢节疼痛、身体尪羸'),
('http://www.tcm-classics.org/fangji#WuTouTang', '乌头汤', '金匮·中风历节-10', '病历节，不可屈伸，疼痛，乌头汤主之。', '川乌五枚、麻黄三两、芍药三两、黄芪三两、甘草三两', 'http://www.tcm-classics.org/tcm#XieXinTangLei', 'http://www.tcm-classics.org/bingzheng#WuTouTangZheng', NULL, NULL, NULL, NULL, NULL, NULL, NULL, '温经散寒，除湿止痛。', '历节疼痛不可屈伸'),
('http://www.tcm-classics.org/fangji#GuiZhiFuLingWan', '桂枝茯苓丸', '金匮·妇人妊娠-2', '妇人素有癥病，经断未及三月，而得漏下不止……当下其癥，桂枝茯苓丸主之。', '桂枝、茯苓、牡丹皮、桃仁、芍药各等分', 'http://www.tcm-classics.org/tcm#DangGuiShaoYaoLei', 'http://www.tcm-classics.org/bingzheng#FuRenZhengJia_GuiZhiFuLingWan', NULL, NULL, NULL, NULL, NULL, NULL, NULL, '活血化瘀，缓消癥块。', '癥病、漏下'),
('http://www.tcm-classics.org/fangji#WenJingTang', '温经汤', '金匮·妇人杂病-9', '妇人年五十所，病下利数十日不止，暮即发热，少腹里急，腹满，手掌烦热，唇口干燥……温经汤主之。', '吴茱萸三两、当归二两、川芎二两、芍药二两、人参二两、桂枝二两、阿胶二两、牡丹皮二两、生姜二两、甘草二两、半夏半升、麦门冬一升', 'http://www.tcm-classics.org/tcm#DangGuiShaoYaoLei', 'http://www.tcm-classics.org/bingzheng#WenJingTangZheng', NULL, NULL, NULL, NULL, NULL, NULL, NULL, '温经散寒，养血祛瘀。', '暮即发热、手掌烦热、唇口干燥'),
('http://www.tcm-classics.org/fangji#JiaoAiTang', '胶艾汤', '金匮·妇人妊娠-4', '妇人有漏下者，有半产后因续下血都不绝者，有妊娠下血者，假令妊娠腹中痛，为胞阻，胶艾汤主之。', '川芎二两、阿胶二两、甘草二两、艾叶三两、当归三两、芍药四两、干地黄六两', 'http://www.tcm-classics.org/tcm#DangGuiShaoYaoLei', 'http://www.tcm-classics.org/bingzheng#JiaoAiTangZheng', NULL, NULL, NULL, NULL, NULL, NULL, NULL, '养血止血，安胎调经。', '漏下、妊娠下血'),
('http://www.tcm-classics.org/fangji#DangGuiShaoYaoSan', '当归芍药散', '金匮·妇人妊娠-5', '妇人怀妊，腹中㽲痛，当归芍药散主之。', '当归三两、芍药一斤、川芎半斤、茯苓四两、白术四两、泽泻半斤', 'http://www.tcm-classics.org/tcm#DangGuiShaoYaoLei', 'http://www.tcm-classics.org/bingzheng#DangGuiShaoYaoSanZheng', NULL, NULL, NULL, NULL, NULL, NULL, NULL, '养血调肝，健脾渗湿。', '腹中㽲痛'),
('http://www.tcm-classics.org/fangji#BaiHeDiHuangTang', '百合地黄汤', '金匮·百合狐惑-1', '百合病者……口苦，小便赤……百合地黄汤主之。', '百合七枚、生地黄汁一升', 'http://www.tcm-classics.org/tcm#DangGuiShaoYaoLei', 'http://www.tcm-classics.org/bingzheng#BaiHeBing_BaiHeDiHuangTang', NULL, NULL, NULL, NULL, NULL, NULL, NULL, '养阴清热，宁心安神。', '百合病、口苦小便赤'),
('http://www.tcm-classics.org/fangji#SuanZaoRenTang', '酸枣仁汤', '金匮·血痹虚劳-17', '虚劳虚烦不得眠，酸枣仁汤主之。', '酸枣仁二升、甘草一两、知母二两、茯苓二两、川芎二两', 'http://www.tcm-classics.org/tcm#SuanZaoRenTangLei', 'http://www.tcm-classics.org/bingzheng#SuanZaoRenTangZheng', NULL, NULL, NULL, NULL, NULL, NULL, NULL, '养血安神，清热除烦。', '虚烦不得眠'),
('http://www.tcm-classics.org/fangji#HuangTuTang', '黄土汤', '金匮·惊悸吐衄-15', '下血，先便后血，此远血也，黄土汤主之。', '甘草三两、干地黄三两、白术三两、附子三两、阿胶三两、黄芩三两、灶心黄土半斤', 'http://www.tcm-classics.org/tcm#HuangTuTangLei', 'http://www.tcm-classics.org/bingzheng#HuangTuTangZheng', NULL, NULL, NULL, NULL, NULL, NULL, NULL, '温中止血，健脾涩肠。', '先便后血'),
('http://www.tcm-classics.org/fangji#ChiXiaoDouDangGuiSan', '赤小豆当归散', '金匮·惊悸吐衄-16', '下血，先血后便，此近血也，赤小豆当归散主之。', '赤小豆三升、当归三两', 'http://www.tcm-classics.org/tcm#DangGuiShaoYaoLei', 'http://www.tcm-classics.org/bingzheng#ChiXiaoDouDangGuiSanZheng', NULL, NULL, NULL, NULL, NULL, NULL, NULL, '清热利湿，活血止血。', '先血后便'),
('http://www.tcm-classics.org/fangji#MaiMenDongTang', '麦门冬汤', '金匮·肺痿-10', '火逆上气，咽喉不利，止逆下气者，麦门冬汤主之。', '麦门冬七升、半夏一升、人参二两、甘草二两、粳米三合、大枣十二枚', 'http://www.tcm-classics.org/tcm#MaiMenDongTangLei', 'http://www.tcm-classics.org/bingzheng#MaiMenDongTangZheng', NULL, NULL, NULL, NULL, NULL, NULL, NULL, '滋阴润肺，降逆和胃。', '火逆上气、咽喉不利'),
('http://www.tcm-classics.org/fangji#SheGanMaHuangTang', '射干麻黄汤', '金匮·肺痿-6', '咳而上气，喉中水鸡声，射干麻黄汤主之。', '射干十三枚、麻黄四两、生姜四两、细辛三两、紫菀三两、款冬花三两、五味子半升、大枣七枚、半夏八枚', 'http://www.tcm-classics.org/tcm#MaHuangTangLei', 'http://www.tcm-classics.org/bingzheng#SheGanMaHuangTangZheng', NULL, NULL, NULL, NULL, NULL, NULL, NULL, '散寒化饮，宣肺止咳。', '喉中水鸡声'),
('http://www.tcm-classics.org/fangji#HouPoMaHuangTang', '厚朴麻黄汤', '金匮·肺痿-8', '咳而脉浮者，厚朴麻黄汤主之。', '厚朴五两、麻黄四两、石膏如鸡子大、杏仁半升、半夏半升、干姜二两、细辛二两、小麦一升、五味子半升', 'http://www.tcm-classics.org/tcm#MaHuangTangLei', 'http://www.tcm-classics.org/bingzheng#HouPoMaHuangTangZheng', NULL, NULL, NULL, NULL, NULL, NULL, NULL, '宣肺降逆，清热化饮。', '咳而脉浮'),
('http://www.tcm-classics.org/fangji#ZeQiTang', '泽漆汤', '金匮·肺痿-9', '咳而脉沉者，泽漆汤主之。', '半夏半升、紫参五两、生姜五两、白前五两、甘草三两、黄芩三两、人参三两、桂枝三两、泽漆三斤', 'http://www.tcm-classics.org/tcm#MaHuangTangLei', 'http://www.tcm-classics.org/bingzheng#ZeQiTangZheng', NULL, NULL, NULL, NULL, NULL, NULL, NULL, '逐水通阳，扶正止咳。', '咳而脉沉'),
('http://www.tcm-classics.org/fangji#TianWuTouChiShiMiSan', '头风摩散', '金匮·中风历节·附方', '头风摩散方：大附子一枚（炮），盐等分。', '附子一枚、盐等分', 'http://www.tcm-classics.org/tcm#MaHuangTangLei', 'http://www.tcm-classics.org/bingzheng#TouFengMoSanZheng', NULL, NULL, NULL, NULL, NULL, NULL, NULL, '外治头风。', '头痛'),

-- 新增5首（补充 shexiang.owl 缺失引用）
('http://www.tcm-classics.org/fangji#GuiZhiRenShenTang', '桂枝人参汤', '第163条', '太阳病，外证未除，而数下之，遂协热而利，利下不止，心下痞硬，表里不解者，桂枝人参汤主之。', '桂枝四两、甘草四两、白术三两、人参三两、干姜三两', 'http://www.tcm-classics.org/tcm#GuiZhiTangLei', 'http://www.tcm-classics.org/bingzheng#TaiYangBingGangZheng', NULL, NULL, NULL, NULL, NULL, NULL, NULL, '解表温里。', '协热而利、心下痞硬'),
('http://www.tcm-classics.org/fangji#XiaYuXueTang', '下瘀血汤', '金匮·妇人产后-6', '产妇腹痛，法当以枳实芍药散，假令不愈者，此为腹中有干血着脐下，宜下瘀血汤主之。', '大黄二两、桃仁二十枚、䗪虫二十枚', 'http://www.tcm-classics.org/tcm#TaoHeChengQiLei', 'http://www.tcm-classics.org/bingzheng#XiaYuXueTangZheng', NULL, NULL, NULL, NULL, NULL, NULL, NULL, '破血逐瘀。', '产妇腹痛、干血着脐下'),
('http://www.tcm-classics.org/fangji#HouPoSanWuTang', '厚朴三物汤', '金匮·腹满寒疝-11', '痛而闭者，厚朴三物汤主之。', '厚朴八两、枳实五枚、大黄四两', 'http://www.tcm-classics.org/tcm#ChengQiTangLei', 'http://www.tcm-classics.org/bingzheng#FuTong_HouPoQiWuTang', NULL, NULL, NULL, NULL, NULL, NULL, NULL, '行气通便。', '痛而闭'),
('http://www.tcm-classics.org/fangji#XijiaoDihuangTang', '犀角地黄汤', '温病·非仲景方', '温病血分证，犀角地黄汤主之。', '犀角、生地黄、芍药、牡丹皮', 'http://www.tcm-classics.org/tcm#BaiHuTangLei', 'http://www.tcm-classics.org/bingzheng#XueFeng_ZhanWei', NULL, NULL, NULL, NULL, NULL, NULL, NULL, '清热凉血散瘀。', '血分热盛'),
('http://www.tcm-classics.org/fangji#AngongNiuhuangWan', '安宫牛黄丸', '温病·非仲景方', '温病热闭心包，安宫牛黄丸主之。', '牛黄、犀角、麝香、珍珠等', 'http://www.tcm-classics.org/tcm#BaiHuTangLei', 'http://www.tcm-classics.org/bingzheng#XinBao_ZhanWei', NULL, NULL, NULL, NULL, NULL, NULL, NULL, '清热解毒，镇惊开窍。', '热闭心包'),
('http://www.tcm-classics.org/fangji#ZixueDan', '紫雪丹', '温病·非仲景方', '温病热陷心包，紫雪丹主之。', '石膏、滑石、磁石等', 'http://www.tcm-classics.org/tcm#BaiHuTangLei', 'http://www.tcm-classics.org/bingzheng#XinBao_ZhanWei', NULL, NULL, NULL, NULL, NULL, NULL, NULL, '清热开窍，镇痉安神。', '热陷心包'),
('http://www.tcm-classics.org/fangji#ZhibaoDan', '至宝丹', '温病·非仲景方', '温病痰热内闭心包，至宝丹主之。', '犀角、玳瑁、安息香等', 'http://www.tcm-classics.org/tcm#BaiHuTangLei', 'http://www.tcm-classics.org/bingzheng#XinBao_ZhanWei', NULL, NULL, NULL, NULL, NULL, NULL, NULL, '清热开窍，化痰解毒。', '痰热内闭');

-- ============================================================
-- 3. 方剂-药物组成数据（原有全部 + 新增补充）
--    （原有数据此前已存在于数据库中，此处为了完整性，
--     再次插入全部数据。若主键冲突，可使用 IGNORE）
-- ============================================================
INSERT IGNORE INTO formula_herb (formula_uri, herb_uri, dosage_text, processing_in_formula, sort_order) VALUES
-- ===== 桂枝汤类（18首）=====
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

-- ===== 麻黄汤类（8首）=====
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

-- ===== 葛根汤类（3首）=====
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

-- ===== 柴胡汤类（7首）=====
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

-- ===== 白虎汤类（3首）=====
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

-- ===== 承气汤类（5首）=====
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

-- ===== 理中汤类（3首）=====
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

-- ===== 四逆汤类（6首）=====
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

-- ===== 厥阴病篇（5首）=====
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

-- ===== 泻心汤类（5首）=====
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

-- ===== 蓄水蓄血及其他太阳变证（8首）=====
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

-- ===== 栀子豉汤类（4首）=====
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

-- ===== 金匮杂病方（原有20首 + 新增5首）=====
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
('http://www.tcm-classics.org/fangji#TianWuTouChiShiMiSan','http://www.tcm-classics.org/yaowu#Yan','等分',NULL,2),

-- ===== 新增5首的组成 =====
-- 桂枝人参汤
('http://www.tcm-classics.org/fangji#GuiZhiRenShenTang','http://www.tcm-classics.org/yaowu#GuiZhi','四两','去皮',1),
('http://www.tcm-classics.org/fangji#GuiZhiRenShenTang','http://www.tcm-classics.org/yaowu#GanCao','四两','炙',2),
('http://www.tcm-classics.org/fangji#GuiZhiRenShenTang','http://www.tcm-classics.org/yaowu#BaiZhu','三两',NULL,3),
('http://www.tcm-classics.org/fangji#GuiZhiRenShenTang','http://www.tcm-classics.org/yaowu#RenShen','三两',NULL,4),
('http://www.tcm-classics.org/fangji#GuiZhiRenShenTang','http://www.tcm-classics.org/yaowu#GanJiang','三两',NULL,5),
-- 下瘀血汤
('http://www.tcm-classics.org/fangji#XiaYuXueTang','http://www.tcm-classics.org/yaowu#DaHuang','二两',NULL,1),
('http://www.tcm-classics.org/fangji#XiaYuXueTang','http://www.tcm-classics.org/yaowu#TaoRen','二十枚','去皮尖',2),
('http://www.tcm-classics.org/fangji#XiaYuXueTang','http://www.tcm-classics.org/yaowu#ZheChong','二十枚','熬去足',3),
-- 厚朴三物汤
('http://www.tcm-classics.org/fangji#HouPoSanWuTang','http://www.tcm-classics.org/yaowu#HouPo','八两',NULL,1),
('http://www.tcm-classics.org/fangji#HouPoSanWuTang','http://www.tcm-classics.org/yaowu#ZhiShi','五枚',NULL,2),
('http://www.tcm-classics.org/fangji#HouPoSanWuTang','http://www.tcm-classics.org/yaowu#DaHuang','四两',NULL,3),
-- 犀角地黄汤（占位，仅示意）
('http://www.tcm-classics.org/fangji#XijiaoDihuangTang','http://www.tcm-classics.org/yaowu#DiHuang','一斤',NULL,1),
('http://www.tcm-classics.org/fangji#XijiaoDihuangTang','http://www.tcm-classics.org/yaowu#ShaoYao','三两',NULL,2),
('http://www.tcm-classics.org/fangji#XijiaoDihuangTang','http://www.tcm-classics.org/yaowu#MuDanPi','二两','去心',3);

-- ============================================================
-- 修改二：更新 formula 表中 pattern_uri 指向可处方病证个体
-- 依据：bingzheng.owl 中 patternRole=prescribable 的个体定义
-- 执行前请备份 formula 表
-- ============================================================

USE tcmdb;

-- 一、桂枝汤类（13首）→ 归入 TaiYangZhongFengZheng（太阳中风证）
-- 此类方剂均为太阳表虚证之变体，核心病机为“营卫不和”
UPDATE formula SET pattern_uri = 'http://www.tcm-classics.org/bingzheng#TaiYangZhongFengZheng' WHERE uri = 'http://www.tcm-classics.org/fangji#GuiZhiTang';
UPDATE formula SET pattern_uri = 'http://www.tcm-classics.org/bingzheng#TaiYangZhongFengZheng' WHERE uri = 'http://www.tcm-classics.org/fangji#GuiZhiJiaGeGenTang';
UPDATE formula SET pattern_uri = 'http://www.tcm-classics.org/bingzheng#TaiYangZhongFengZheng' WHERE uri = 'http://www.tcm-classics.org/fangji#GuiZhiJiaHouPoXingRenTang';
UPDATE formula SET pattern_uri = 'http://www.tcm-classics.org/bingzheng#TaiYangZhongFengZheng' WHERE uri = 'http://www.tcm-classics.org/fangji#GuiZhiJiaFuZiTang';
UPDATE formula SET pattern_uri = 'http://www.tcm-classics.org/bingzheng#TaiYangZhongFengZheng' WHERE uri = 'http://www.tcm-classics.org/fangji#GuiZhiQuShaoYaoTang';
UPDATE formula SET pattern_uri = 'http://www.tcm-classics.org/bingzheng#TaiYangZhongFengZheng' WHERE uri = 'http://www.tcm-classics.org/fangji#GuiZhiQuShaoYaoJiaFuZiTang';
UPDATE formula SET pattern_uri = 'http://www.tcm-classics.org/bingzheng#TaiYangZhongFengZheng' WHERE uri = 'http://www.tcm-classics.org/fangji#GuiZhiXinJiaTang';
UPDATE formula SET pattern_uri = 'http://www.tcm-classics.org/bingzheng#TaiYangZhongFengZheng' WHERE uri = 'http://www.tcm-classics.org/fangji#GuiZhiGanCaoTang';
UPDATE formula SET pattern_uri = 'http://www.tcm-classics.org/bingzheng#TaiYangZhongFengZheng' WHERE uri = 'http://www.tcm-classics.org/fangji#ShaoYaoGanCaoTang';
UPDATE formula SET pattern_uri = 'http://www.tcm-classics.org/bingzheng#TaiYangZhongFengZheng' WHERE uri = 'http://www.tcm-classics.org/fangji#ShaoYaoGanCaoFuZiTang';
UPDATE formula SET pattern_uri = 'http://www.tcm-classics.org/bingzheng#TaiYangZhongFengZheng' WHERE uri = 'http://www.tcm-classics.org/fangji#GuiZhiFuZiTang';
UPDATE formula SET pattern_uri = 'http://www.tcm-classics.org/bingzheng#TaiYangZhongFengZheng' WHERE uri = 'http://www.tcm-classics.org/fangji#BaiZhuFuZiTang';
UPDATE formula SET pattern_uri = 'http://www.tcm-classics.org/bingzheng#TaiYangZhongFengZheng' WHERE uri = 'http://www.tcm-classics.org/fangji#GanCaoFuZiTang';

-- 二、桂枝汤类专属方证（4首）→ 指向 bingzheng.owl 中已定义的 prescribable 个体
-- 这些个体在 bingzheng.owl 中有明确的 patternRole=prescribable 声明
UPDATE formula SET pattern_uri = 'http://www.tcm-classics.org/bingzheng#GuiZhiJiaLongGuMuLiTangZheng' WHERE uri = 'http://www.tcm-classics.org/fangji#GuiZhiJiaLongGuMuLiTang';
UPDATE formula SET pattern_uri = 'http://www.tcm-classics.org/bingzheng#HuangQiJianZhongTangZheng' WHERE uri = 'http://www.tcm-classics.org/fangji#HuangQiJianZhongTang';
UPDATE formula SET pattern_uri = 'http://www.tcm-classics.org/bingzheng#XiaoJianZhongTangZheng' WHERE uri = 'http://www.tcm-classics.org/fangji#XiaoJianZhongTang';
UPDATE formula SET pattern_uri = 'http://www.tcm-classics.org/bingzheng#DaJianZhongTangZheng' WHERE uri = 'http://www.tcm-classics.org/fangji#DaJianZhongTang';

-- 三、麻黄汤类（2首）→ 归入 TaiYangShangHanZheng（太阳伤寒证）
-- 均为太阳表实证之变体，核心病机为“寒邪闭表”
UPDATE formula SET pattern_uri = 'http://www.tcm-classics.org/bingzheng#TaiYangShangHanZheng' WHERE uri = 'http://www.tcm-classics.org/fangji#MaHuangJiaZhuTang';
UPDATE formula SET pattern_uri = 'http://www.tcm-classics.org/bingzheng#TaiYangShangHanZheng' WHERE uri = 'http://www.tcm-classics.org/fangji#MaHuangYiYiGanCaoTang';

-- 四、葛根汤类（3首）→ 指向 bingzheng.owl 中已定义的 prescribable 个体
-- 葛根汤主证及合病下利型均指向 GeGenTangZheng
UPDATE formula SET pattern_uri = 'http://www.tcm-classics.org/bingzheng#GeGenTangZheng' WHERE uri = 'http://www.tcm-classics.org/fangji#GeGenTang';
UPDATE formula SET pattern_uri = 'http://www.tcm-classics.org/bingzheng#GeGenTangZheng' WHERE uri = 'http://www.tcm-classics.org/fangji#GeGenJiaBanXiaTang';
-- 葛根芩连汤证为独立可处方个体
UPDATE formula SET pattern_uri = 'http://www.tcm-classics.org/bingzheng#GeGenQinLianTangZheng' WHERE uri = 'http://www.tcm-classics.org/fangji#GeGenHuangQinHuangLianTang';

-- 五、柴胡汤类（3首）→ 归入 XiaoChaiHuTangZheng（小柴胡汤证）
-- 此类无独立 prescribable 个体，但核心病机均为“少阳枢机不利”
UPDATE formula SET pattern_uri = 'http://www.tcm-classics.org/bingzheng#XiaoChaiHuTangZheng' WHERE uri = 'http://www.tcm-classics.org/fangji#ChaiHuJiaMangXiaoTang';
UPDATE formula SET pattern_uri = 'http://www.tcm-classics.org/bingzheng#XiaoChaiHuTangZheng' WHERE uri = 'http://www.tcm-classics.org/fangji#ChaiHuGuiZhiGanJiangTang';
UPDATE formula SET pattern_uri = 'http://www.tcm-classics.org/bingzheng#XiaoChaiHuTangZheng' WHERE uri = 'http://www.tcm-classics.org/fangji#ChaiHuJiaLongGuMuLiTang';

-- 六、四逆汤类（2首）→ 归入 SiNiTangZheng_ShaoYin（少阴四逆汤证）
-- 白通汤、白通加猪胆汁汤无独立 prescribable 个体，核心病机均为“少阴阳虚寒盛”
UPDATE formula SET pattern_uri = 'http://www.tcm-classics.org/bingzheng#SiNiTangZheng_ShaoYin' WHERE uri = 'http://www.tcm-classics.org/fangji#BaiTongTang';
UPDATE formula SET pattern_uri = 'http://www.tcm-classics.org/bingzheng#SiNiTangZheng_ShaoYin' WHERE uri = 'http://www.tcm-classics.org/fangji#BaiTongJiaZhuDanZhiTang';

-- 七、栀子豉汤类（4首）→ 归入 BaiHuTangZheng（白虎汤证）
-- 栀子豉汤类无独立 prescribable 个体，其病机属“阳明热郁胸膈”，
-- 与白虎汤同属阳明经证范畴，故归入 BaiHuTangZheng
UPDATE formula SET pattern_uri = 'http://www.tcm-classics.org/bingzheng#BaiHuTangZheng' WHERE uri = 'http://www.tcm-classics.org/fangji#ZhiZiChiTang';
UPDATE formula SET pattern_uri = 'http://www.tcm-classics.org/bingzheng#BaiHuTangZheng' WHERE uri = 'http://www.tcm-classics.org/fangji#ZhiZiGanCaoTang';
UPDATE formula SET pattern_uri = 'http://www.tcm-classics.org/bingzheng#BaiHuTangZheng' WHERE uri = 'http://www.tcm-classics.org/fangji#ZhiZiShengJiangTang';
UPDATE formula SET pattern_uri = 'http://www.tcm-classics.org/bingzheng#BaiHuTangZheng' WHERE uri = 'http://www.tcm-classics.org/fangji#ZhiZiHouPoTang';

-- 八、泻心汤类（5首）→ 归入 TaiYangZhongFengZheng（太阳中风证）
-- 泻心汤类方证在 bingzheng.owl 中无独立 prescribable 个体，
-- 其病机属“太阳病误治后寒热错杂于心下”，以太阳表虚为基础，故归入此类
UPDATE formula SET pattern_uri = 'http://www.tcm-classics.org/bingzheng#TaiYangZhongFengZheng' WHERE uri = 'http://www.tcm-classics.org/fangji#BanXiaXieXinTang';
UPDATE formula SET pattern_uri = 'http://www.tcm-classics.org/bingzheng#TaiYangZhongFengZheng' WHERE uri = 'http://www.tcm-classics.org/fangji#ShengJiangXieXinTang';
UPDATE formula SET pattern_uri = 'http://www.tcm-classics.org/bingzheng#TaiYangZhongFengZheng' WHERE uri = 'http://www.tcm-classics.org/fangji#GanCaoXieXinTang';
UPDATE formula SET pattern_uri = 'http://www.tcm-classics.org/bingzheng#TaiYangZhongFengZheng' WHERE uri = 'http://www.tcm-classics.org/fangji#DaHuangHuangLianXieXinTang';
UPDATE formula SET pattern_uri = 'http://www.tcm-classics.org/bingzheng#TaiYangZhongFengZheng' WHERE uri = 'http://www.tcm-classics.org/fangji#FuZiXieXinTang';

-- 九、苓桂剂及太阳变证（5首）
-- 苓桂术甘汤：指向 bingzheng.owl 中已定义的 prescribable 个体
UPDATE formula SET pattern_uri = 'http://www.tcm-classics.org/bingzheng#LingGuiZhuGanTangZheng' WHERE uri = 'http://www.tcm-classics.org/fangji#FuLingGuiZhiBaiZhuGanCaoTang';
-- 茯苓甘草汤、旋覆代赭汤、厚朴生姜半夏甘草人参汤、桂枝人参汤
-- 均无独立 prescribable 个体，归入 TaiYangZhongFengZheng（太阳表虚变证）
UPDATE formula SET pattern_uri = 'http://www.tcm-classics.org/bingzheng#TaiYangZhongFengZheng' WHERE uri = 'http://www.tcm-classics.org/fangji#FuLingGanCaoTang';
UPDATE formula SET pattern_uri = 'http://www.tcm-classics.org/bingzheng#TaiYangZhongFengZheng' WHERE uri = 'http://www.tcm-classics.org/fangji#XuanFuDaiZheTang';
UPDATE formula SET pattern_uri = 'http://www.tcm-classics.org/bingzheng#TaiYangZhongFengZheng' WHERE uri = 'http://www.tcm-classics.org/fangji#HouPoShengJiangBanXiaGanCaoRenShenTang';
UPDATE formula SET pattern_uri = 'http://www.tcm-classics.org/bingzheng#TaiYangZhongFengZheng' WHERE uri = 'http://www.tcm-classics.org/fangji#GuiZhiRenShenTang';

-- ============================================================
-- 验证：查询所有仍然指向废弃 gangzheng 的方剂（应返回空）
-- ============================================================
-- SELECT uri, label, pattern_uri FROM formula
-- WHERE pattern_uri IN (
--     'http://www.tcm-classics.org/bingzheng#TaiYangBingGangZheng',
--     'http://www.tcm-classics.org/bingzheng#YangMingBingGangZheng',
--     'http://www.tcm-classics.org/bingzheng#ShaoYangBingGangZheng',
--     'http://www.tcm-classics.org/bingzheng#TaiYinBingGangZheng',
--     'http://www.tcm-classics.org/bingzheng#ShaoYinBingGangZheng',
--     'http://www.tcm-classics.org/bingzheng#JueYinBingGangZheng'
-- );
-- 若执行结果为空，则更新完成。
-- ============================================================