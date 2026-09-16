
-- =============================================
-- B 类补建：81 首缺失方剂（依《伤寒论》《金匮要略》原文）
-- 含 23 味新增药物；柴胡白虎汤、杏子汤按后世公认组成补建并注明来源
-- =============================================

-- ---------- 新增药物（23味） ----------
INSERT INTO yaowu (iri, label) VALUES
('Wenge','文蛤'),('Helile','诃梨勒'),('Puhui','蒲灰'),('Luanfa','乱发'),('Baiyu','白鱼'),('Rongyan','戎盐'),('Zhizhu','蜘蛛'),('Shechuangzi','蛇床子'),('Zhugao','猪膏'),('Yunmu','云母'),('Yangrou','羊肉'),('Tuguagen','土瓜根'),('Kuizi','葵子'),('Baiwei','白薇'),('Wangbuliuxing','王不留行'),('Shuoduoixiye','蒴藋细叶'),('Sangdongnangenbaipi','桑东南根白皮'),('Jishibai','鸡屎白'),('Qianfen','铅粉'),('Juhua','菊花'),('Hanshuishi','寒水石'),('Baishizhi','白石脂'),('Zishiying','紫石英');

-- ---------- 新增方剂（81首） ----------
INSERT INTO fangji (iri, label) VALUES
('Mijiandaofang','蜜煎导方'),('Zhudanzhifang','猪胆汁方'),('Wenkesan','文蛤散'),('Zhulingsan','猪苓散'),('Zaojiawan','皂荚丸'),('Qianjinweijingtang','千金苇茎汤'),('Jupitang','橘皮汤'),('Zishentang','紫参汤'),('Helilesan','诃梨勒散'),('Yiwuguaditang','一物瓜蒂汤'),('Xumingtang','续命汤'),('Mulitang','牡蛎汤'),('Tianxiongsan','天雄散'),('Puhuisan','蒲灰散'),('Huashibaiyusan','滑石白鱼散'),('Fulingrongyantang','茯苓戎盐汤'),('Zhizhusan','蜘蛛散'),('Shechuangzisan','蛇床子散'),('Langyatang','狼牙汤'),('Zhugaofajian','猪膏发煎'),('Xiaoshifanshisan','硝石矾石散'),('Wengetang','文蛤汤'),('Shengmabiejiaquxionghuangshujiaotang','升麻鳖甲去雄黄蜀椒汤'),('Kushentang','苦参汤'),('Xionghuangxunfang','雄黄熏方'),('Shuqisan','蜀漆散'),('Dahuanggansuitang','大黄甘遂汤'),('Guizhieryuebiyitang','桂枝二越婢一汤'),('Dangguishengjiangyangroutang','当归生姜羊肉汤'),('Chaihuqubanxiajiagualoutang','柴胡去半夏加栝楼汤'),('Chaihubaihutang','柴胡白虎汤'),('Houpoudahuangtang','厚朴大黄汤'),('Fulingsinitang','茯苓四逆汤'),('Ganjiangfuzitang','干姜附子汤'),('Fulingzexietang','茯苓泽泻汤'),('Lingganwuweijiangxinxiatang','苓甘五味姜辛夏汤'),('Lingganwuweijiajiangxinbanxiaxingrentang','苓甘五味加姜辛半夏杏仁汤'),('Lingganwuweijiajiangxinbanxingdahuangtang','苓甘五味加姜辛半杏大黄汤'),('Guizhijiashaoyaotang','桂枝加芍药汤'),('Huangqijianzhongtang','黄芪建中汤'),('Neibudangguijianzhongtang','内补当归建中汤'),('Mahuangfuzigancaotang','麻黄附子甘草汤'),('Mahuangfuzitang','麻黄附子汤'),('Guizhiqushaoyaojiashuqimulilonggujiunitang','桂枝去芍药加蜀漆牡蛎龙骨救逆汤'),('Guizhiquguijiafulingbaizhutang','桂枝去桂加茯苓白术汤'),('Guizhigancaotang','桂枝甘草汤'),('Guizhigancaolonggumulitang','桂枝甘草龙骨牡蛎汤'),('Didangwan','抵当丸'),('Xingzitang','杏子汤'),('Shengjiangbanxiatang','生姜半夏汤'),('Banxiaganjiangsan','半夏干姜散'),('Banxiamahuangwan','半夏麻黄丸'),('Dabanxiatang','大半夏汤'),('Dangguishaoyaosan','当归芍药散'),('Zhishishaoyaosan','枳实芍药散'),('Jiaoaitang','胶艾汤'),('Tuguagensan','土瓜根散'),('Dangguisan','当归散'),('Shaoyaogancaotang','芍药甘草汤'),('Shaoyaogancaofuzitang','芍药甘草附子汤'),('Fanshiwan','矾石丸'),('Honglanhuajiu','红蓝花酒'),('Kuizifulingsan','葵子茯苓散'),('Baizhusan','白术散'),('Zhupidawan','竹皮大丸'),('Baihexifang','百合洗方'),('Yiyifuzibaijiangsan','薏苡附子败酱散'),('Sanwuhuangqintang','三物黄芩汤'),('Fanshitang','矾石汤'),('Zhishutang','枳术汤'),('Ganmaidazaotang','甘麦大枣汤'),('Wangbuliuxingsan','王不留行散'),('Painongsan','排脓散'),('Painongtang','排脓汤'),('Huanglianfen','黄连粉'),('Jishibaisan','鸡屎白散'),('Gancaofenmitang','甘草粉蜜汤'),('Houshiheisan','侯氏黑散'),('Fengyintang','风引汤'),('Fangjidihuangtang','防己地黄汤'),('Sanhuangtang','三黄汤');

-- ---------- 新增方剂-药物关系 ----------
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Mijiandaofang' AND y.iri IN ('Mi');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Zhudanzhifang' AND y.iri IN ('Zhudanzhi');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Wenkesan' AND y.iri IN ('Wenge');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Zhulingsan' AND y.iri IN ('Zhuling','Fuling','Baizhu');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Zaojiawan' AND y.iri IN ('Zaojia');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Qianjinweijingtang' AND y.iri IN ('Weijing','Yiyiren','Guaban','Taoren');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Jupitang' AND y.iri IN ('Chenpi','Shengjiang');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Zishentang' AND y.iri IN ('Zican','Gancao');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Helilesan' AND y.iri IN ('Helile');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Yiwuguaditang' AND y.iri IN ('Guadi');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Xumingtang' AND y.iri IN ('Mahuang','Guizhi','Danggui','Renshen','Shigao','Ganjiang','Gancao','Chuanxiong','Xingren');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Mulitang' AND y.iri IN ('Muli','Mahuang','Gancao','Shuqi');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Tianxiongsan' AND y.iri IN ('Tianxiong','Baizhu','Guizhi','Longgu');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Puhuisan' AND y.iri IN ('Puhui','Huashi');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Huashibaiyusan' AND y.iri IN ('Huashi','Luanfa','Baiyu');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Fulingrongyantang' AND y.iri IN ('Fuling','Baizhu','Rongyan');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Zhizhusan' AND y.iri IN ('Zhizhu','Guizhi');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Shechuangzisan' AND y.iri IN ('Shechuangzi');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Langyatang' AND y.iri IN ('Shenglangya');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Zhugaofajian' AND y.iri IN ('Zhugao','Luanfa');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Xiaoshifanshisan' AND y.iri IN ('Xiaoshi','Fanshi');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Wengetang' AND y.iri IN ('Wenge','Mahuang','Gancao','Shengjiang','Shigao','Xingren','Dazao');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Shengmabiejiaquxionghuangshujiaotang' AND y.iri IN ('Shengma','Danggui','Gancao','Biejia');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Kushentang' AND y.iri IN ('Kushen');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Xionghuangxunfang' AND y.iri IN ('Xionghuang');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Shuqisan' AND y.iri IN ('Shuqi','Yunmu','Longgu');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Dahuanggansuitang' AND y.iri IN ('Dahuang','Gansui','Ejiao');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Guizhieryuebiyitang' AND y.iri IN ('Guizhi','Shaoyao','Mahuang','Gancao','Dazao','Shengjiang','Shigao');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Dangguishengjiangyangroutang' AND y.iri IN ('Danggui','Shengjiang','Yangrou');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Chaihuqubanxiajiagualoutang' AND y.iri IN ('Chaihu','Renshen','Huangqin','Gancao','Gualougen','Shengjiang','Dazao');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Chaihubaihutang' AND y.iri IN ('Chaihu','Huangqin','Banxia','Renshen','Gancao','Shengjiang','Dazao','Shigao','Zhimu');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Houpoudahuangtang' AND y.iri IN ('Houpo','Dahuang','Zhishi');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Fulingsinitang' AND y.iri IN ('Fuling','Renshen','Fuzi','Gancao','Ganjiang');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Ganjiangfuzitang' AND y.iri IN ('Ganjiang','Fuzi');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Fulingzexietang' AND y.iri IN ('Fuling','Zexie','Gancao','Guizhi','Baizhu','Shengjiang');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Lingganwuweijiangxinxiatang' AND y.iri IN ('Fuling','Gancao','Wuweizi','Ganjiang','Xixin','Banxia');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Lingganwuweijiajiangxinbanxiaxingrentang' AND y.iri IN ('Fuling','Gancao','Wuweizi','Ganjiang','Xixin','Banxia','Xingren');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Lingganwuweijiajiangxinbanxingdahuangtang' AND y.iri IN ('Fuling','Gancao','Wuweizi','Ganjiang','Xixin','Banxia','Xingren','Dahuang');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Guizhijiashaoyaotang' AND y.iri IN ('Guizhi','Shaoyao','Gancao','Shengjiang','Dazao');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Huangqijianzhongtang' AND y.iri IN ('Huangqi','Guizhi','Shaoyao','Gancao','Shengjiang','Dazao','Yitang');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Neibudangguijianzhongtang' AND y.iri IN ('Danggui','Guizhi','Shaoyao','Gancao','Shengjiang','Dazao','Yitang');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Mahuangfuzigancaotang' AND y.iri IN ('Mahuang','Fuzi','Gancao');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Mahuangfuzitang' AND y.iri IN ('Mahuang','Fuzi','Gancao');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Guizhiqushaoyaojiashuqimulilonggujiunitang' AND y.iri IN ('Guizhi','Gancao','Shengjiang','Dazao','Shuqi','Muli','Longgu');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Guizhiquguijiafulingbaizhutang' AND y.iri IN ('Shaoyao','Gancao','Shengjiang','Dazao','Fuling','Baizhu');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Guizhigancaotang' AND y.iri IN ('Guizhi','Gancao');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Guizhigancaolonggumulitang' AND y.iri IN ('Guizhi','Gancao','Muli','Longgu');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Didangwan' AND y.iri IN ('Shuizhi','Mengchong','Taoren','Dahuang');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Xingzitang' AND y.iri IN ('Mahuang','Xingren','Gancao');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Shengjiangbanxiatang' AND y.iri IN ('Banxia','Shengjiang');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Banxiaganjiangsan' AND y.iri IN ('Banxia','Ganjiang');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Banxiamahuangwan' AND y.iri IN ('Banxia','Mahuang');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Dabanxiatang' AND y.iri IN ('Banxia','Renshen','Mi');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Dangguishaoyaosan' AND y.iri IN ('Danggui','Shaoyao','Fuling','Baizhu','Zexie','Chuanxiong');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Zhishishaoyaosan' AND y.iri IN ('Zhishi','Shaoyao');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Jiaoaitang' AND y.iri IN ('Chuanxiong','Ejiao','Gancao','Aiye','Danggui','Shaoyao','Dihuang');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Tuguagensan' AND y.iri IN ('Tuguagen','Shaoyao','Guizhi','Zhechong');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Dangguisan' AND y.iri IN ('Danggui','Huangqin','Shaoyao','Chuanxiong','Baizhu');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Shaoyaogancaotang' AND y.iri IN ('Shaoyao','Gancao');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Shaoyaogancaofuzitang' AND y.iri IN ('Shaoyao','Gancao','Fuzi');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Fanshiwan' AND y.iri IN ('Fanshi','Xingren');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Honglanhuajiu' AND y.iri IN ('Honglanhua','Qingjiu');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Kuizifulingsan' AND y.iri IN ('Kuizi','Fuling');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Baizhusan' AND y.iri IN ('Baizhu','Chuanxiong','Shujiao','Muli');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Zhupidawan' AND y.iri IN ('Zhuru','Shigao','Guizhi','Gancao','Baiwei');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Baihexifang' AND y.iri IN ('Baihe');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Yiyifuzibaijiangsan' AND y.iri IN ('Yiyiren','Fuzi','Baijiangcao');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Sanwuhuangqintang' AND y.iri IN ('Huangqin','Kushen','Dihuang');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Fanshitang' AND y.iri IN ('Fanshi');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Zhishutang' AND y.iri IN ('Zhishi','Baizhu');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Ganmaidazaotang' AND y.iri IN ('Gancao','Xiaomai','Dazao');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Wangbuliuxingsan' AND y.iri IN ('Wangbuliuxing','Shuoduoixiye','Sangdongnangenbaipi','Gancao','Shujiao','Huangqin','Ganjiang','Shaoyao','Houpo');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Painongsan' AND y.iri IN ('Zhishi','Shaoyao','Jiegeng');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Painongtang' AND y.iri IN ('Gancao','Jiegeng','Shengjiang','Dazao');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Huanglianfen' AND y.iri IN ('Huanglian');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Jishibaisan' AND y.iri IN ('Jishibai');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Gancaofenmitang' AND y.iri IN ('Gancao','Qianfen','Mi');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Houshiheisan' AND y.iri IN ('Juhua','Baizhu','Xixin','Fuling','Muli','Jiegeng','Fangfeng','Renshen','Fanshi','Huangqin','Danggui','Ganjiang','Chuanxiong','Guizhi');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Fengyintang' AND y.iri IN ('Dahuang','Ganjiang','Longgu','Guizhi','Gancao','Muli','Hanshuishi','Huashi','Chishizhi','Baishizhi','Zishiying','Shigao');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Fangjidihuangtang' AND y.iri IN ('Mufangji','Guizhi','Fangfeng','Gancao','Shengdihuangzhi');
INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='Sanhuangtang' AND y.iri IN ('Mahuang','Duhuo','Xixin','Huangqi','Huangqin');

-- ========== B 类补建完成 ==========
