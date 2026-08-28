-- ============================================================
-- yaowu.sql 完整版
-- 包含：herb主表（88味）、herb_bagang（八纲映射）、
--       herb_antagonistic（十八反）、herb_fearing（十九畏）
-- ============================================================

USE tcmdb;

-- ============================================================
-- 1. 药物主表（88味，含䗪虫）
-- ============================================================
INSERT INTO herb (uri, label, original_taste, original_nature, shennong_category, earliest_source, guilin_dosage_unit, processing_method, shennong_original_text, bielu_original_text, huxishu_herb_note, clinical_key_points, modern_application) VALUES
('http://www.tcm-classics.org/yaowu#GuiZhi','桂枝','辛','温','上品','ShennongBencaoJing','两','去皮','牡桂，味辛温。主上气咳逆，结气喉痹，吐吸，利关节，补中益气。久服通神，轻身不老。生山谷。',NULL,'桂枝为解肌发表、调和营卫第一药。非独发汗，更能降冲逆、通阳气、化水饮。凡见汗出恶风、气上冲、水饮内停者皆可用之。','汗出恶风、气上冲、水饮。','感冒、流感、自汗盗汗、心悸、水肿、关节痛、荨麻疹。'),
('http://www.tcm-classics.org/yaowu#ShaoYao','芍药','苦','平','中品','ShennongBencaoJing','两','无','芍药，味苦平。主邪气腹痛，除血痹，破坚积寒热疝瘕，止痛，利小便，益气。生川谷。',NULL,'芍药养血柔肝、缓急止痛、敛阴和营。桂枝汤中与桂枝配伍调和营卫，芍药甘草汤中缓急止痛，真武汤中利小便。汉时不分赤白，后世方有区分。','腹痛、挛急、月经不调、小便不利。','腹痛、痛经、肌肉痉挛、泌尿系感染。'),
('http://www.tcm-classics.org/yaowu#GanCao','甘草','甘','平','上品','ShennongBencaoJing','两','炙','甘草，味甘平。主五脏六腑寒热邪气，坚筋骨，长肌肉倍力，金疮肿，解毒。久服轻身延年。生山谷。',NULL,'甘草补中益气、缓急止痛、调和诸药、解毒。经方中使用频率最高之药。炙用偏补中，生用偏清热解毒。芍药甘草汤中缓急止痛，四逆汤中配姜附回阳。','气虚、腹痛挛急、中毒。','胃溃疡、腹痛、药物中毒、心律失常。'),
('http://www.tcm-classics.org/yaowu#ShengJiang','生姜','辛','微温','未载','MingYiBieLu','两','切',NULL,'生姜，味辛微温。主伤寒头痛鼻塞，咳逆上气，止呕吐。生蜀郡。','生姜散寒解表、温中止呕、化饮。桂枝汤中助桂枝解表，小半夏汤中化饮止呕。走而不守，偏于散水气。','恶寒、呕吐、水饮。','感冒、恶心呕吐、晕车、胃炎。'),
('http://www.tcm-classics.org/yaowu#DaZao','大枣','甘','平','上品','ShennongBencaoJing','枚','擘','大枣，味甘平。主心腹邪气，安中养脾，助十二经，平胃气，通九窍，补少气少津液，身中不足，大惊四肢重，和百药。久服轻身长年。生河东。',NULL,'大枣补中益气、养血安神、缓和药性。桂枝汤、小柴胡汤等方中广泛使用。擘开裂以利煎出有效成分。','脾虚、血虚、心神不安。','贫血、失眠、慢性胃炎。'),
('http://www.tcm-classics.org/yaowu#GeGen','葛根','甘','平','中品','ShennongBencaoJing','两','无','葛根，味甘平。主消渴，身大热，呕吐，诸痹，起阴气，解诸毒。生川谷。',NULL,'葛根解肌退热、生津舒筋。专治项背强几几。有汗用桂枝加葛根汤，无汗用葛根汤。又能升阳止泻，治协热下利。','项背强几几、口渴、下利。','感冒、颈椎病、腹泻、糖尿病口渴。'),
('http://www.tcm-classics.org/yaowu#MaHuang','麻黄','苦','温','中品','ShennongBencaoJing','两','去节','麻黄，味苦温。主中风伤寒头痛，温疟，发表出汗，去邪热气，止咳逆上气，除寒热，破癥坚积聚。生山谷。',NULL,'麻黄为发汗峻药，专治表实无汗。兼能宣肺平喘、利水消肿。有汗禁用。胡老强调麻黄配石膏则清宣肺热，配附子则温经解表，配白术则祛湿，配薏苡仁则清利湿热。','无汗、喘、水肿。','感冒、哮喘、支气管炎、肾炎水肿、风湿性关节炎。'),
('http://www.tcm-classics.org/yaowu#XingRen','杏仁','苦','温','下品','ShennongBencaoJing','个','去皮尖','杏核仁，味甘苦温。主咳逆上气雷鸣，喉痹下气，产乳金疮，奔豚。生川谷。',NULL,'杏仁降肺气、止咳平喘。麻黄汤、麻杏石甘汤之要药。配麻黄宣降并用，配石膏清宣肺热。去皮尖减其小毒。','咳喘、胸闷。','支气管炎、哮喘、咳嗽。'),
('http://www.tcm-classics.org/yaowu#FuZi','附子','辛','温','下品','ShennongBencaoJing','枚','炮去皮破八片','附子，味辛温。主风寒咳逆邪气，温中，金疮，破癥坚积聚血瘕，寒湿踒躄拘挛膝痛，不能行步。生山谷。',NULL,'附子为回阳救逆第一药。四逆汤之主药。温肾阳、暖脾土、散寒湿、通经脉。生用回阳力峻，炮用温补力缓。胡老谓附子配干姜为回阳铁对，配白术为祛湿铁对。','四肢厥逆、下利清谷、脉沉微、畏寒。','休克、心衰、肾衰、风湿痛、慢性腹泻。'),
('http://www.tcm-classics.org/yaowu#BaiZhu','白术','苦','温','上品','ShennongBencaoJing','两','无','术，味苦温。主风寒湿痹死肌，痉疸，止汗除热，消食。作煎饵久服轻身延年不饥。生山谷。',NULL,'白术健脾燥湿、利水止汗。苓桂术甘汤、理中汤、真武汤、当归芍药散之要药。《本经》谓"主风寒湿痹""止汗"，正合脾虚湿盛之治。汉时不分苍白术。','脾虚湿盛、水肿、自汗、泄泻。','慢性胃炎、肠炎、水肿、自汗。'),
('http://www.tcm-classics.org/yaowu#FuLing','茯苓','甘','平','上品','ShennongBencaoJing','两','无','茯苓，味甘平。主胸胁逆气，忧恚惊邪恐悸，心下结痛，寒热烦满咳逆，口焦舌干，利小便。久服安魂养神，不饥延年。生山谷。',NULL,'茯苓利水渗湿、健脾宁心。苓桂术甘汤、五苓散、真武汤之要药。《本经》谓"利小便""主惊悸"，正合水饮证之治。','小便不利、水肿、心悸、眩晕。','水肿、泌尿系感染、失眠、心悸。'),
('http://www.tcm-classics.org/yaowu#MuDanPi','牡丹皮','辛','寒','中品','ShennongBencaoJing','两','去心','牡丹，味辛寒。主寒热中风瘈疭惊痫邪气，除癥坚瘀血留舍肠胃，安五脏，疗痈疮。生山谷。',NULL,'牡丹皮清热凉血、活血散瘀。桂枝茯苓丸之要药。治瘀血癥积、血热妄行。','瘀血、出血、发热。','子宫肌瘤、盆腔炎、过敏性紫癜。'),
('http://www.tcm-classics.org/yaowu#TaoRen','桃仁','苦','平','下品','ShennongBencaoJing','个','去皮尖熬','桃核仁，味苦平。主瘀血血闭瘕邪，杀小虫。生川谷。',NULL,'桃仁活血化瘀、润肠通便。桃核承气汤、抵当汤、桂枝茯苓丸之要药。《本经》谓"主瘀血血闭"，正合蓄血证之治。','瘀血、少腹急结、便秘。','闭经、痛经、子宫肌瘤、脑梗塞。'),
('http://www.tcm-classics.org/yaowu#LongGu','龙骨','甘','平','上品','ShennongBencaoJing','两','无','龙骨，味甘平。主心腹鬼疰精物老魅，咳逆泄痢脓血，女子漏下，癥瘕坚结，小儿热气惊痫。生山谷。',NULL,'龙骨镇惊安神、收敛固涩。桂枝加龙骨牡蛎汤、柴胡加龙骨牡蛎汤之要药。治心悸失眠、遗精滑泄、惊狂。','心悸、失眠、遗精、惊狂。','神经衰弱、遗精、盗汗、癫痫。'),
('http://www.tcm-classics.org/yaowu#MuLi','牡蛎','咸','平','中品','ShennongBencaoJing','两','熬','牡蛎，味咸平。主伤寒寒热，温疟洒洒，惊恚怒气，除拘缓鼠瘘，女子带下赤白。久服强骨节，杀邪鬼延年。生东海。',NULL,'牡蛎潜阳安神、软坚散结、收敛固涩。桂枝加龙骨牡蛎汤、柴胡加龙骨牡蛎汤、柴胡桂枝干姜汤之要药。治心悸失眠、瘰疬痰核、遗精盗汗。','心悸、失眠、瘰疬、遗精。','甲亢、淋巴结肿大、神经衰弱、盗汗。'),
('http://www.tcm-classics.org/yaowu#HuangQi','黄芪','甘','微温','上品','ShennongBencaoJing','两','无','黄芪，味甘微温。主痈疽久败疮，排脓止痛，大风癞疾，五痔鼠瘘，补虚小儿百病。生山谷。',NULL,'黄芪补气固表、利水消肿、托毒排脓。黄芪建中汤之要药。治虚劳里急、自汗、水肿、疮疡不溃。','气虚自汗、水肿、疮疡。','慢性肾炎、糖尿病、免疫力低下、疮疡。'),
('http://www.tcm-classics.org/yaowu#YiTang','饴糖','甘','温','未载','MingYiBieLu','升','内汤中微火消解',NULL,'饴糖，味甘温。主补虚乏，止渴去血。生稻米。','饴糖补中缓急、润肺止咳。小建中汤、大建中汤、黄芪建中汤之要药。甘温建中，为建中汤类方之灵魂。','里急腹痛、虚劳。','胃溃疡、十二指肠溃疡、慢性胃炎。'),
('http://www.tcm-classics.org/yaowu#ShuJiao','蜀椒','辛','温','下品','ShennongBencaoJing','合','炒去汗','秦椒，味辛温。主风邪气，温中除寒痹，坚齿发，明目。久服轻身好颜色，耐老增年通神。生川谷。',NULL,'蜀椒温中散寒、杀虫止痛。大建中汤、乌梅丸之要药。治心胸大寒痛、蛔厥。炒去汗减其燥烈之性。','腹冷痛、蛔厥、呕吐。','胃肠痉挛、胆道蛔虫症、痛经。'),
('http://www.tcm-classics.org/yaowu#GanJiang','干姜','辛','温','中品','ShennongBencaoJing','两','无','干姜，味辛温。主胸满咳逆上气，温中止血，出汗，逐风湿痹，肠澼下痢。生者尤良。久服去臭气，通神明。生川谷。',NULL,'干姜温中散寒、回阳通脉。四逆汤中配附子回阳救逆，理中汤中温中散寒，泻心汤类方中配黄连辛开苦降。生姜走表散水，干姜守中温里。','腹冷痛、下利清谷、呕吐、咳喘痰稀。','慢性胃炎、肠炎、支气管炎、休克。'),
('http://www.tcm-classics.org/yaowu#RenShen','人参','甘','微寒','上品','ShennongBencaoJing','两','无','人参，味甘微寒。主补五脏，安精神，定魂魄，止惊悸，除邪气，明目开心益智。久服轻身延年。生山谷。',NULL,'人参大补元气、补脾益肺、生津安神。四逆加人参汤、理中汤、小柴胡汤、白虎加人参汤之要药。《本经》谓"补五脏，安精神"，正合虚证之治。汉时所用人参为上党参，非今之东北人参。','气虚、津伤、心悸、倦怠。','休克、心衰、慢性疲劳、糖尿病。'),
('http://www.tcm-classics.org/yaowu#BanXia','半夏','辛','平','下品','ShennongBencaoJing','升','洗','半夏，味辛平。主伤寒寒热，心下坚，下气，喉咽肿痛，头眩胸胀，咳逆肠鸣，止汗。生川谷。',NULL,'半夏燥湿化痰、降逆止呕、消痞散结。小柴胡汤、泻心汤类方、小青龙汤之要药。《本经》谓"主心下坚，下气"，正合痞证、呕逆之治。洗去滑涎减其毒性。','呕逆、痞满、咳喘痰多、眩晕。','慢性胃炎、支气管炎、梅尼埃病、失眠。'),
('http://www.tcm-classics.org/yaowu#HouPo','厚朴','苦','温','中品','ShennongBencaoJing','斤','炙去皮','厚朴，味苦温。主中风伤寒头痛，寒热惊悸，气血痹，死肌，去三虫。生山谷。',NULL,'厚朴行气消胀、燥湿除满。承气汤类方、厚朴七物汤、栀子厚朴汤之要药。治腹满胀痛。炙去皮减其燥性。','腹满胀痛、便秘。','胃肠功能紊乱、肠梗阻、消化不良。'),
('http://www.tcm-classics.org/yaowu#ZhiShi','枳实','苦','寒','中品','ShennongBencaoJing','枚','炙','枳实，味苦寒。主大风在皮肤中如麻豆苦痒，除寒热结，止痢长肌肉，利五脏，益气轻身。生川泽。',NULL,'枳实破气消积、化痰散痞。承气汤类方、四逆散、枳实薤白桂枝汤之要药。治痞满、便秘、胸痹气结。','痞满、便秘、胸痹气结。','消化不良、胃下垂、冠心病。'),
('http://www.tcm-classics.org/yaowu#DaHuang','大黄','苦','寒','下品','ShennongBencaoJing','两','酒洗','大黄，味苦寒。主下瘀血，血闭寒热，破癥瘕积聚，留饮宿食，荡涤肠胃，推陈致新，通利水谷道，调中化食，安和五脏。生山谷。',NULL,'大黄为攻下第一药。承气汤类方之主药。《本经》谓"荡涤肠胃，推陈致新"，正合阳明腑实之治。又活血化瘀，治蓄血证。酒洗增其活血之力。','便秘、腹满痛、谵语、瘀血。','便秘、肠梗阻、胰腺炎、阑尾炎、闭经。'),
('http://www.tcm-classics.org/yaowu#MangXiao','芒硝','咸','寒','上品','ShennongBencaoJing','合','内汤中微沸','朴硝，味苦寒。主百病，除寒热邪气，逐六腑积聚，结固留癖，能化七十二种石。炼饵服之，轻身神仙。生山谷。',NULL,'芒硝咸寒软坚润燥。大承气汤中配大黄攻下燥屎。调胃承气汤中和胃泄热。不入煎，溶化服用。','大便燥结、腹满痛。','便秘、肠梗阻、胆囊炎。'),
('http://www.tcm-classics.org/yaowu#XiXin','细辛','辛','温','上品','ShennongBencaoJing','两','无','细辛，味辛温。主咳逆上气，头痛脑动，百节拘挛，风湿痹痛，死肌。久服明目利九窍。生山谷。',NULL,'细辛温经散寒、化饮止痛。小青龙汤、麻附辛汤之要药。治寒饮咳喘、头痛、痹痛。胡老谓细辛配干姜五味子为化饮铁三角。','寒饮咳喘、头痛、痹痛。','慢性支气管炎、哮喘、鼻炎、头痛、风湿痛。'),
('http://www.tcm-classics.org/yaowu#WuWeiZi','五味子','酸','温','上品','ShennongBencaoJing','升','无','五味子，味酸温。主益气，咳逆上气，劳伤羸瘦，补不足，强阴，益男子精。生山谷。',NULL,'五味子敛肺止咳、生津敛汗、涩精止泻。小青龙汤中配细辛干姜，散收并用防耗散太过。又治久咳虚喘、自汗盗汗、遗精滑泄。','久咳虚喘、自汗盗汗、遗精。','慢性支气管炎、哮喘、神经衰弱、腹泻。'),
('http://www.tcm-classics.org/yaowu#ChaiHu','柴胡','苦','平','上品','ShennongBencaoJing','两','无','柴胡，味苦平。主心腹肠胃中结气，饮食积聚，寒热邪气，推陈致新。久服轻身明目益精。生山谷。',NULL,'柴胡为少阳枢机之要药。《本经》谓"主心腹肠胃中结气，寒热邪气"，正合少阳半表半里之病机。非后世所谓"升阳举陷"之品。凡往来寒热、胸胁苦满、默默不欲食者必用。','往来寒热、胸胁苦满。','感冒、疟疾、肝炎、胆囊炎、月经不调、抑郁症。'),
('http://www.tcm-classics.org/yaowu#HuangQin','黄芩','苦','平','中品','ShennongBencaoJing','两','无','黄芩，味苦平。主诸热黄疸，肠澼泄痢，逐水，下血闭，恶疮疽蚀火疡。生川谷。',NULL,'黄芩清少阳、阳明之热。小柴胡汤中与柴胡配伍清半表半里之热。又治湿热下利、黄疸、吐血衄血。','口苦、下利、黄疸、出血。','感冒、肝炎、肠炎、高血压、习惯性流产。'),
('http://www.tcm-classics.org/yaowu#HuangLian','黄连','苦','寒','上品','ShennongBencaoJing','两','无','黄连，味苦寒。主热气目痛眦伤泣出，明目，肠澼腹痛下痢，妇人阴中肿痛。久服令人不忘。生川谷。',NULL,'黄连清心胃湿热。泻心汤类方之主药。治痞证、下利脓血、心烦不眠、目痛。胡老谓黄连配干姜为辛开苦降之核心药对。','心烦、痞满、下利脓血、目痛。','胃炎、肠炎、失眠、口腔溃疡、结膜炎。'),
('http://www.tcm-classics.org/yaowu#GuaLouGen','瓜蒌根','苦','寒','中品','ShennongBencaoJing','两','无','栝楼根，味苦寒。主消渴身热，烦满大热，补虚安中，续绝伤。生川谷。',NULL,'瓜蒌根即天花粉。清热生津、消肿排脓。柴胡桂枝干姜汤中配柴胡黄芩治少阳兼水饮内结之渴。','口渴、痈肿。','糖尿病、乳腺炎、疮疡。'),
('http://www.tcm-classics.org/yaowu#QianDan','铅丹','辛','微寒','下品','ShennongBencaoJing','两','无','铅丹，味辛微寒。主吐逆胃反，惊痫癫疾，除热下气。炼化还成九光。生蜀郡。',NULL,'铅丹重镇安神、坠痰截疟。柴胡加龙骨牡蛎汤中用以镇惊安神。有毒，现代多以磁石、生铁落代之。','惊狂、癫痫。','癫痫、精神分裂症（现代已少用）。'),
('http://www.tcm-classics.org/yaowu#ShiGao','石膏','辛','微寒','中品','ShennongBencaoJing','斤','碎绵裹','石膏，味辛微寒。主中风寒热，心下逆气惊喘，口干舌焦不能息，腹中坚痛，除邪鬼，产乳金疮。生山谷。',NULL,'石膏清阳明气分大热。白虎汤之主药。辛寒而非苦寒，故能透热外出。治大热大汗大渴脉洪大。配麻黄则清宣肺热治喘。','大热、大汗、大渴、脉洪大。','高热、肺炎、牙龈炎、湿疹。'),
('http://www.tcm-classics.org/yaowu#ZhiMu','知母','苦','寒','中品','ShennongBencaoJing','两','无','知母，味苦寒。主消渴热中，除邪气，肢体浮肿，下水，补不足益气。生川谷。',NULL,'知母清热滋阴润燥。白虎汤中配石膏清气分热，桂枝芍药知母汤中配桂枝附子治风湿化热伤阴。','口渴、烦躁、肢体肿痛。','糖尿病、甲亢、风湿性关节炎、更年期综合征。'),
('http://www.tcm-classics.org/yaowu#JingMi','粳米','甘','平','未载','MingYiBieLu','合','煮米熟汤成',NULL,'粳米，味甘平。主益气止烦止泄。生江南。','粳米养胃和中、益气生津。白虎汤、竹叶石膏汤、麦门冬汤中用以护胃气、生津液。','胃气虚弱、津伤口渴。','热病后期调养、慢性胃炎。'),
('http://www.tcm-classics.org/yaowu#ZhuYe','竹叶','甘','寒','中品','ShennongBencaoJing','把','无','竹叶，味甘寒。主咳逆上气，溢筋急，恶疡杀小虫。生泽中。',NULL,'竹叶清热除烦、生津利尿。竹叶石膏汤之主药。治热病后期余热未清、虚羸少气。','虚羸少气、心烦口渴。','热病恢复期、口腔溃疡。'),
('http://www.tcm-classics.org/yaowu#MaiMenDong','麦门冬','甘','平','上品','ShennongBencaoJing','升','去心','麦门冬，味甘平。主心腹结气，伤中伤饱，胃络脉绝，羸瘦短气。久服轻身不老不饥。生川谷。',NULL,'麦门冬滋养肺胃之阴、清心除烦。麦门冬汤之主药。治咳逆上气咽喉不利。竹叶石膏汤中配石膏人参治热病后期气阴两伤。','干咳、咽干、心烦。','慢性支气管炎、萎缩性胃炎、糖尿病。'),
('http://www.tcm-classics.org/yaowu#ZhiZi','栀子','苦','寒','中品','ShennongBencaoJing','个','擘','栀子，味苦寒。主五内邪气，胃中热气面赤，酒皰皶鼻，白癞赤癞疮疡。生川谷。',NULL,'栀子清宣胸膈郁热。栀子豉汤之主药。治虚烦不得眠、心中懊憹。又能清利三焦湿热，治黄疸。','虚烦、懊憹、黄疸。','失眠、焦虑、肝炎、胆囊炎。'),
('http://www.tcm-classics.org/yaowu#XiangChi','香豉','苦','寒','未载','MingYiBieLu','合','绵裹',NULL,'豉，味苦寒。主伤寒头痛寒热，瘴气恶毒，烦躁满闷，虚劳喘吸，两脚疼冷。生江东。','香豉即淡豆豉。解表除烦、宣发郁热。栀子豉汤之要药。治虚烦不得眠、心中懊憹。','虚烦、懊憹、表证。','感冒、失眠、焦虑。'),
('http://www.tcm-classics.org/yaowu#ZhuLing','猪苓','甘','平','中品','ShennongBencaoJing','铢','去皮','猪苓，味甘平。主痎疟，解毒蛊疰不祥，利水道。久服轻身耐老。生山谷。',NULL,'猪苓利水渗湿力强于茯苓。五苓散之要药。专治水湿内停、小便不利。','小便不利、水肿、泄泻。','水肿、泌尿系感染、腹泻。'),
('http://www.tcm-classics.org/yaowu#ZeXie','泽泻','甘','寒','上品','ShennongBencaoJing','两','无','泽泻，味甘寒。主风寒湿痹，乳难消水，养五脏益气力，肥健。久服耳目聪明，不饥延年轻身。生池泽。',NULL,'泽泻利水渗湿、泄热。五苓散、当归芍药散之要药。利水之力较强，兼能泄肾与膀胱之热。','小便不利、水肿、泄泻。','水肿、高脂血症、泌尿系感染。'),
('http://www.tcm-classics.org/yaowu#CongBai','葱白','辛','温','未载','MingYiBieLu','茎','无',NULL,'葱白，味辛温。主伤寒寒热，中风面目浮肿，能出汗。生随州。','葱白通阳散寒、宣通上下。白通汤之主药。治少阴下利、面赤、脉微。通阳之力迅捷。','下利、面赤、脉微。','休克早期、感冒鼻塞。'),
('http://www.tcm-classics.org/yaowu#RenNiao','人尿','咸','寒','未载','MingYiBieLu','合','和令相得',NULL,'人尿，味咸寒。主寒热头痛，温气。童男者尤良。','人尿咸寒滋阴降火、引阳入阴。白通加猪胆汁汤中与猪胆汁同用为反佐。现代已罕用。','阴盛格阳、服药即吐。','现代已罕用。'),
('http://www.tcm-classics.org/yaowu#ZhuDanZhi','猪胆汁','苦','寒','未载','MingYiBieLu','合','和令相得',NULL,'猪胆，味苦寒。主伤寒热渴，黄疸，消渴，杀蛊毒。生猪胆中。','猪胆汁苦寒清热、润燥通便。白通加猪胆汁汤中用作反佐，引阳药入阴，防格拒。','阴盛格阳、服药即吐。','休克、严重腹泻（反佐用法）。'),
('http://www.tcm-classics.org/yaowu#HuangLianEJiaoTang_JiZiHuang','鸡子黄','甘','平','未载','MingYiBieLu','枚','小冷内搅令相得',NULL,'鸡子黄，味甘平。主除热，止烦懑，安五脏，益气。生鸡卵中。','鸡子黄滋阴润燥、养血安神。黄连阿胶汤中用以滋肾阴、交通心肾。不入煎，待汤小冷后搅入。','心烦不眠、阴虚。','失眠、神经衰弱。'),
('http://www.tcm-classics.org/yaowu#EJiao','阿胶','甘','平','上品','ShennongBencaoJing','两','烊化','阿胶，味甘平。主心腹内崩，劳极洒洒如疟状，腰腹痛，四肢酸疼，女子下血安胎。久服轻身益气。生东平郡。',NULL,'阿胶滋阴补血、止血安胎。黄连阿胶汤、胶艾汤、温经汤之要药。治阴虚失眠、崩漏、胎动不安。','出血、阴虚失眠、胎动不安。','贫血、功能性子宫出血、先兆流产、失眠。'),
('http://www.tcm-classics.org/yaowu#WuMei','乌梅','酸','平','中品','ShennongBencaoJing','枚','苦酒渍一宿去核蒸','梅实，味酸平。主下气除热烦满，安心，肢体痛，偏枯不仁，死肌，去青黑痣恶疾。生川谷。',NULL,'乌梅敛肺涩肠、安蛔生津。乌梅丸之主药。治蛔厥、久利。酸苦辛甘并用，为厥阴寒热错杂证之要药。','蛔厥、久利、消渴。','胆道蛔虫症、慢性结肠炎、糖尿病。'),
('http://www.tcm-classics.org/yaowu#DangGui','当归','甘','温','中品','ShennongBencaoJing','两','无','当归，味甘温。主咳逆上气，温疟寒热洗洗在皮肤中，妇人漏下绝子，诸恶疮疡金疮。煮饮之。生川谷。',NULL,'当归补血活血、调经止痛。当归四逆汤、温经汤、胶艾汤之要药。治血虚寒凝、月经不调、崩漏。','血虚、月经不调、腹痛。','贫血、痛经、闭经、产后调理。'),
('http://www.tcm-classics.org/yaowu#TongCao','通草','甘','平','中品','ShennongBencaoJing','两','无','通草，味辛平。主去恶虫，除脾胃寒热，通利九窍血脉关节，令人不忘。生山谷。',NULL,'通草通利血脉、下乳利尿。当归四逆汤中用以通利血脉。汉时通草即今之木通，非今之通草。','血脉不通、乳汁不下。','雷诺氏病、产后缺乳。'),
('http://www.tcm-classics.org/yaowu#WuZhuYu','吴茱萸','辛','温','中品','ShennongBencaoJing','升','洗','吴茱萸，味辛温。主温中下气，止痛，咳逆寒热，除湿血痹，逐风邪，开腠理。生山谷。',NULL,'吴茱萸温肝暖胃、降逆止呕。吴茱萸汤之主药。治食谷欲呕、头痛吐涎沫、手足逆冷。又入乌梅丸治蛔厥。','食谷欲呕、头痛吐涎沫、手足逆冷。','偏头痛、慢性胃炎、妊娠呕吐、痛经。'),
('http://www.tcm-classics.org/yaowu#BaiTouWeng','白头翁','苦','温','未载','MingYiBieLu','两','无',NULL,'白头翁，味苦温，有毒。主温疟狂易寒热，癥瘕积聚，瘿气，逐血止腹痛，疗金疮。生田野。','白头翁清肝凉血解毒止痢。白头翁汤之主药。专治厥阴湿热下利、下利脓血里急后重。','下利脓血、里急后重。','细菌性痢疾、阿米巴痢疾、溃疡性结肠炎。'),
('http://www.tcm-classics.org/yaowu#HuangBai','黄柏','苦','寒','上品','ShennongBencaoJing','两','无','檗木，味苦寒。主五脏肠胃中结热，黄疸，肠痔，止泄痢，女子漏下赤白，阴阳蚀疮。生山谷。',NULL,'黄柏清下焦湿热。白头翁汤、乌梅丸之要药。治湿热下利、带下、阴蚀、黄疸。','下利脓血、带下、阴蚀。','痢疾、阴道炎、尿路感染、湿疹。'),
('http://www.tcm-classics.org/yaowu#QinPi','秦皮','苦','寒','中品','ShennongBencaoJing','两','无','秦皮，味苦涩寒。主风寒湿痹，洗洗寒气，除热，目中青翳白膜。久服头不白，轻身。生川谷。',NULL,'秦皮清肝明目、涩肠止痢。白头翁汤之辅药。助白头翁清肝凉血止痢。','下利脓血、目赤翳障。','痢疾、结膜炎、角膜炎。'),
('http://www.tcm-classics.org/yaowu#XuanFuHua','旋覆花','咸','温','下品','ShennongBencaoJing','两','无','旋覆花，味咸温。主结气胁下满，惊悸，除水，去五脏间寒热，补中下气。生川谷。',NULL,'旋覆花降气化痰、止呕噫。旋覆代赭汤之主药。治心下痞硬、噫气不除。《本经》谓"主结气胁下满"，正合此证。','噫气不除、心下痞硬。','胃食管反流、慢性胃炎、呃逆。'),
('http://www.tcm-classics.org/yaowu#DaiZheShi','代赭石','苦','寒','下品','ShennongBencaoJing','两','无','代赭石，味苦寒。主鬼疰贼风蛊毒，杀精物恶鬼，腹中毒邪气，女子赤沃漏下。生山谷。',NULL,'代赭石重镇降逆、平肝潜阳。旋覆代赭汤中配旋覆花降逆止噫。又治肝阳上亢之眩晕。','噫气、呕吐、眩晕。','胃食管反流、高血压、眩晕。'),
('http://www.tcm-classics.org/yaowu#GuaLouShi','瓜蒌实','苦','寒','中品','ShennongBencaoJing','枚','捣','栝楼，味苦寒。主消渴身热，烦满大热，补虚安中，续绝伤。生川谷。',NULL,'瓜蒌实宽胸散结、化痰通便。瓜蒌薤白白酒汤之主药。治胸痹心痛彻背。','胸痹心痛、痰热结胸。','冠心病、心绞痛、乳腺增生。'),
('http://www.tcm-classics.org/yaowu#XieBai','薤白','辛苦','温','未载','MingYiBieLu','斤','无',NULL,'薤，味辛苦温。主金疮疮败，轻身不饥耐老。生鲁山。','薤白通阳散结、行气导滞。瓜蒌薤白白酒汤之要药。治胸痹心痛。胡老谓薤白辛温通阳，专散胸中阴寒痰浊。','胸痹心痛、脘腹胀满。','冠心病、心绞痛、胃肠功能紊乱。'),
('http://www.tcm-classics.org/yaowu#BaiJiu','白酒','甘辛','温','未载','MingYiBieLu','升','同煮',NULL,'酒，味苦甘辛温。主行药势，杀百邪恶毒气。生米麦。','白酒通阳散结、行气活血。瓜蒌薤白白酒汤中用以通胸阳、散痰浊。','胸痹心痛。','冠心病、心绞痛。'),
('http://www.tcm-classics.org/yaowu#FangFeng','防风','甘','温','上品','ShennongBencaoJing','两','无','防风，味甘温。主大风头眩痛，恶风风邪，目盲无所见，风行周身，骨节疼痹，烦满。久服轻身。生川泽。',NULL,'防风祛风胜湿、解痉止痛。桂枝芍药知母汤之要药。治风湿痹痛、头眩、恶风。','风湿痹痛、头眩、恶风。','风湿性关节炎、感冒、头痛、荨麻疹。'),
('http://www.tcm-classics.org/yaowu#ChuanWu','川乌','辛','温','下品','ShennongBencaoJing','枚','㕮咀蜜煎','乌头，味辛温。主中风恶风洗洗出汗，除寒湿痹，咳逆上气，破积聚寒热。生山谷。',NULL,'川乌温经散寒、除湿止痛力峻。乌头汤之主药。治寒湿历节剧痛不可屈伸。有毒，须蜜煎减毒。','关节剧痛不可屈伸。','类风湿关节炎、坐骨神经痛。'),
('http://www.tcm-classics.org/yaowu#Mi','蜜','甘','平','上品','ShennongBencaoJing','升','和丸/煎','石蜜，味甘平。主心腹邪气，诸惊痫痉，安五脏诸不足，益气补中，止痛解毒，除众病，和百药。久服强志轻身不饥不老。生山谷。',NULL,'蜂蜜补中润燥、缓急解毒、和药为丸。乌梅丸、大建中汤、乌头汤中用以缓急、解毒、和药。','腹痛、燥咳、药毒。','便秘、咳嗽、药物炮制。'),
('http://www.tcm-classics.org/yaowu#ChuanXiong','川芎','辛','温','中品','ShennongBencaoJing','两','无','芎藭，味辛温。主中风入脑头痛，寒痹筋挛缓急，金疮，妇人血闭无子。生川谷。',NULL,'川芎活血行气、祛风止痛。当归芍药散、温经汤、胶艾汤之要药。治头痛、月经不调、风湿痹痛。','头痛、月经不调、痹痛。','偏头痛、痛经、冠心病、风湿痛。'),
('http://www.tcm-classics.org/yaowu#AiYe','艾叶','苦','温','未载','MingYiBieLu','两','无',NULL,'艾叶，味苦微温。主灸百病，可作煎，止下痢吐血，下部䘌疮，妇人漏血。生田野。','艾叶温经止血、散寒止痛。胶艾汤之要药。治崩漏下血、胎动不安、宫冷不孕。','崩漏、胎动不安、宫冷。','功能性子宫出血、先兆流产、痛经。'),
('http://www.tcm-classics.org/yaowu#DiHuang','干地黄','甘','寒','上品','ShennongBencaoJing','两','无','干地黄，味甘寒。主折跌绝筋，伤中逐血痹，填骨髓长肌肉，作汤除寒热积聚，除痹。生者尤良。久服轻身不老。生川谷。',NULL,'干地黄滋阴养血、凉血止血。黄土汤、胶艾汤之要药。经方所用多为干地黄，非后世熟地。治血虚、出血、阴虚。','血虚、出血、阴虚发热。','贫血、功能性子宫出血、糖尿病。'),
('http://www.tcm-classics.org/yaowu#SuanZaoRen','酸枣仁','酸','平','上品','ShennongBencaoJing','升','无','酸枣，味酸平。主心腹寒热，邪结气聚，四肢酸疼湿痹。久服安五脏轻身延年。生川泽。',NULL,'酸枣仁养心安神、敛汗。酸枣仁汤之主药。治虚劳虚烦不得眠。胡老谓酸枣仁养血安神，配知母清虚热，配川芎调肝血。','虚烦不眠、心悸盗汗。','失眠、焦虑、神经衰弱。'),
('http://www.tcm-classics.org/yaowu#ZaoXinHuangTu','灶心黄土','辛','温','未载','MingYiBieLu','斤','无',NULL,'灶心黄土，味辛温。主妇人崩中吐血，及尿血金疮出血。生灶中。','灶心黄土即伏龙肝。温中止血、涩肠止泻。黄土汤之主药。治脾阳虚寒便血、先便后血色暗淡。','便血色暗、崩漏、呕吐。','消化道出血、功能性子宫出血、妊娠呕吐。'),
('http://www.tcm-classics.org/yaowu#ChiXiaoDou','赤小豆','甘','平','中品','ShennongBencaoJing','升','浸令芽出曝干','赤小豆，味甘平。主下水排痈肿脓血。生太山。',NULL,'赤小豆利水消肿、排脓解毒。赤小豆当归散之主药。治湿热便血、痈肿。','便血、痈肿、水肿。','痔疮出血、脓肿、肾炎水肿。'),
('http://www.tcm-classics.org/yaowu#JiangShui','浆水','酸甘','凉','未载','MingYiBieLu','合','和散服',NULL,'浆水，味酸甘凉。主调中引气，宣和强力，通关开胃，止霍乱泄痢。生粟米。','浆水即发酵米汤。酸甘清凉、调中和胃。赤小豆当归散中用以和散服，助药力下行。','便血、湿热。','作为服药介质。'),
('http://www.tcm-classics.org/yaowu#BaiHe','百合','甘','平','中品','ShennongBencaoJing','枚','擘渍一宿','百合，味甘平。主邪气腹胀心痛，利大小便，补中益气。生川谷。',NULL,'百合滋养心肺、清热安神。百合地黄汤之主药。治百合病神志恍惚、口苦小便赤。','神志恍惚、口苦、小便赤。','神经官能症、失眠、更年期综合征。'),
('http://www.tcm-classics.org/yaowu#ShengDiHuangZhi','生地黄汁','甘','寒','上品','ShennongBencaoJing','升','绞汁','干地黄，味甘寒。主折跌绝筋，伤中逐血痹，填骨髓长肌肉，作汤除寒热积聚，除痹。生者尤良。久服轻身不老。生川谷。',NULL,'生地黄汁滋阴清热凉血力胜于干地黄。百合地黄汤中用以滋心肺之阴、清血分之热。','阴虚发热、神志恍惚。','神经官能症、糖尿病。'),
('http://www.tcm-classics.org/yaowu#QuanShui','泉水','甘','平','未载','MingYiBieLu','升','煎药',NULL,'泉水，味甘平。主消渴，止烦热，利小便。生山中。','泉水甘平清热、利小便。百合地黄汤中用以煎百合，取其清冽之性助药力。','消渴、烦热。','作为煎药用水。'),
('http://www.tcm-classics.org/yaowu#SheGan','射干','苦','平','下品','ShennongBencaoJing','枚','无','射干，味苦平。主咳逆上气，喉痹咽痛不得消息，散结气腹中邪逆，食饮大热。生川谷。',NULL,'射干清热解毒、消痰利咽。射干麻黄汤之主药。专治喉中水鸡声之寒饮郁肺咳喘。','喉中水鸡声、咽痛。','哮喘、咽喉炎、扁桃体炎。'),
('http://www.tcm-classics.org/yaowu#ZiWan','紫菀','苦','温','中品','ShennongBencaoJing','两','无','紫菀，味苦温。主咳逆上气，胸中寒热结气，去蛊毒痿躄，安五脏。生山谷。',NULL,'紫菀润肺下气、化痰止咳。射干麻黄汤之辅药。治寒饮咳喘。','咳喘痰多。','慢性支气管炎、哮喘。'),
('http://www.tcm-classics.org/yaowu#KuanDongHua','款冬花','辛','温','中品','ShennongBencaoJing','两','无','款冬花，味辛温。主咳逆上气善喘，喉痹，诸惊痫寒热邪气。生山谷。',NULL,'款冬花润肺下气、止咳化痰。射干麻黄汤之辅药。治寒饮咳喘。','咳喘痰多。','慢性支气管炎、哮喘。'),
('http://www.tcm-classics.org/yaowu#XiaoMai','小麦','甘','微寒','未载','MingYiBieLu','升','先煮熟去滓',NULL,'小麦，味甘微寒。主除热止烦渴，利小便，养肝气。生泰山。','小麦养心安神、除烦止渴。厚朴麻黄汤中先煮取汁用以养心护胃。','心烦、口渴。','失眠、烦躁。'),
('http://www.tcm-classics.org/yaowu#ZeQi','泽漆','辛苦','微寒','未载','MingYiBieLu','斤','煮取汁',NULL,'泽漆，味辛苦微寒。主皮肤热，大腹水气，四肢面目浮肿，丈夫阴气不足。生太山。','泽漆逐水消肿、止咳平喘。泽漆汤之主药。治水饮内停咳喘、身肿脉沉。','咳喘、身肿、脉沉。','慢性支气管炎、胸腔积液、肾炎水肿。'),
('http://www.tcm-classics.org/yaowu#ZiCan','紫参','苦','平','中品','ShennongBencaoJing','两','无','紫参，味苦辛寒。主心腹积聚，寒热邪气，通九窍，利大小便。生山谷。',NULL,'紫参活血通经、清热解毒。泽漆汤之辅药。历代考证品种不一，或为拳参、丹参之类。','积聚、咳喘。','慢性支气管炎、肿瘤（品种存疑）。'),
('http://www.tcm-classics.org/yaowu#BaiQian','白前','甘','微温','未载','MingYiBieLu','两','无',NULL,'白前，味甘微温。主胸胁逆气，咳嗽上气。生江州。','白前降气化痰、止咳平喘。泽漆汤之辅药。治水饮咳喘。','咳喘痰多。','慢性支气管炎、哮喘。'),
('http://www.tcm-classics.org/yaowu#DongLiuShui','东流水','甘','平','未载','MingYiBieLu','斗','煮泽漆',NULL,'东流水，味甘平。主荡涤邪秽，通达气血。生东方。','东流水取其流通之性。泽漆汤中用以煮泽漆，助其逐水通阳之力。','水饮内停。','作为煎药用水。'),
('http://www.tcm-classics.org/yaowu#YiYiRen','薏苡仁','甘','微寒','未载','MingYiBieLu','两','无',NULL,'薏苡仁，味甘微寒。主筋急拘挛不可屈伸，风湿痹，下气。久服轻身益气。生真定。','薏苡仁健脾渗湿、清热排脓、舒筋缓急。麻杏苡甘汤、薏苡附子败酱散之要药。治风湿痹痛、肠痈、水肿。','风湿痹痛、肠痈、水肿。','风湿性关节炎、阑尾炎、扁平疣。'),
('http://www.tcm-classics.org/yaowu#BaiJiangCao','败酱草','苦','平','未载','MingYiBieLu','两','无',NULL,'败酱，味苦平。主暴热火疮赤气，疥瘙疽痔马鞍热气。生江夏。','败酱草清热解毒、排脓破瘀。薏苡附子败酱散之要药。治肠痈脓已成。','肠痈、腹痛、身甲错。','阑尾炎、盆腔炎、肝脓肿。'),
('http://www.tcm-classics.org/yaowu#ShuiZhi','水蛭','咸','平','下品','ShennongBencaoJing','个','熬','水蛭，味咸平。主逐恶血瘀血月闭，破血瘕积聚无子，利水道。生池泽。',NULL,'水蛭破血逐瘀。抵当汤之要药。治蓄血重证、发狂少腹硬满。为虫类破血峻药。','蓄血发狂、少腹硬满、闭经。','血栓性疾病、闭经、子宫肌瘤。'),
('http://www.tcm-classics.org/yaowu#MengChong','虻虫','苦','微寒','下品','ShennongBencaoJing','个','去翅足熬','虻虫，味苦微寒。主逐瘀血，破下血积坚痞癥瘕寒热，通利血脉及九窍。生川谷。',NULL,'虻虫破血逐瘀。抵当汤中与水蛭配伍，为破血逐瘀最峻之药对。治蓄血重证。','蓄血发狂、少腹硬满。','血栓性疾病、闭经、肿瘤。'),
('http://www.tcm-classics.org/yaowu#KuJiu','苦酒','酸','温','未载','MingYiBieLu','升','渍乌梅',NULL,'醋，味酸温。主消痈肿，散水气，杀邪毒。生酒糟。','苦酒即醋。收敛、散瘀、解毒。乌梅丸中用以渍乌梅，增其酸收安蛔之力。','蛔厥、瘀血。','作为辅料增强药效。'),
('http://www.tcm-classics.org/yaowu#QingJiu','清酒','甘辛','温','未载','MingYiBieLu','升','和水煮',NULL,'酒，味苦甘辛温。主行药势，杀百邪恶毒气。生米麦。','清酒通血脉、行药势。炙甘草汤、当归四逆加吴茱萸生姜汤、胶艾汤中用以助药力通行。','血脉不通、药力不达。','作为药引增强药效。'),
('http://www.tcm-classics.org/yaowu#BaiYin','白饮','甘','平','未载','MingYiBieLu','合','和散服',NULL,'白饮，即米汤。味甘平。主养胃气，助药力。生稻米。','白饮即米汤。养胃气、助药力。五苓散、四逆散中用以和散服。','胃气虚弱。','作为服药介质。'),
('http://www.tcm-classics.org/yaowu#WenFen','温粉','甘','平','未载','MingYiBieLu','适量','外用扑身',NULL,'温粉，即炒米粉。味甘平。主止汗，护腠理。生稻米。','温粉即炒米粉。外用扑身止汗。大青龙汤过汗后用以止汗护表。','过汗不止。','外用止汗。'),
('http://www.tcm-classics.org/yaowu#Yan','盐','咸','寒','未载','MingYiBieLu','分','外用摩',NULL,'盐，味咸温。主杀鬼蛊邪疰毒，伤寒寒热，吐胸中痰癖，止心腹卒痛，坚肌骨。生河东。','盐外用引药入络、温经散寒。头风摩散中与附子配伍外摩治头风头痛。','头风头痛（外用）。','外用辅助治疗。'),
('http://www.tcm-classics.org/yaowu#RouGui','肉桂','辛','温','上品','ShennongBencaoJing','两','去皮','菌桂，味辛温。主百病，养精神，和颜色，为诸药先聘通使。久服轻身不老，面生光华媚好。生山谷。',NULL,'肉桂温肾助阳、引火归元、通血脉。经方中多用桂枝，肉桂用于肾气丸等补肾方。胡老谓肉桂偏于温里补肾，桂枝偏于解表通阳。','腰膝冷痛、小便不利、虚喘。','慢性肾炎、糖尿病、阳痿、痛经。'),
('http://www.tcm-classics.org/yaowu#LongDanCao','龙胆草','苦','寒','上品','ShennongBencaoJing','两','无','龙胆，味苦涩寒。主骨间寒热，惊痫邪气，续绝伤，定五脏，杀蛊毒。生山谷。',NULL,'龙胆草清肝胆实火、下焦湿热。经方中虽少用，但后世龙胆泻肝汤即宗此意。胡老治肝胆湿热证时常参考。','胁痛、口苦、目赤、阴肿。','肝炎、胆囊炎、带状疱疹、外阴炎。'),
('http://www.tcm-classics.org/yaowu#ChenPi','陈皮','辛苦','温','未载','MingYiBieLu','两','无',NULL,'橘皮，味辛苦温。主下气止呕咳，治气冲胸中，吐逆霍乱，止泻，去寸白。生南山。','陈皮理气健脾、燥湿化痰。经方中少用，但后世二陈汤即宗此意。胡老治痰湿气滞证时偶参用。','脘腹胀满、呕逆、痰多。','消化不良、慢性支气管炎。'),
('http://www.tcm-classics.org/yaowu#BinLang','槟榔','辛苦','温','未载','MingYiBieLu','枚','无',NULL,'槟榔，味辛苦温。主消谷逐水，除痰癖，杀三虫伏尸，疗寸白。生南海。','槟榔杀虫消积、行气利水。经方中少用，但后世驱虫方多用之。胡老治虫积证时偶参用。','虫积、腹胀、水肿。','肠道寄生虫病、消化不良。'),
('http://www.tcm-classics.org/yaowu#XiongHuang','雄黄','辛苦','温','中品','ShennongBencaoJing','两','研末','雄黄，味苦辛温。主寒热鼠瘘恶疮疽痔死肌，杀精物恶鬼邪气百虫毒。生山谷。',NULL,'雄黄解毒杀虫、燥湿祛痰。经方中外用为主。有毒，内服慎用。','疮疡、虫蛇咬伤。','皮肤病、蛇虫咬伤（外用）。'),

-- ===== 䗪虫（下瘀血汤核心药物） =====
('http://www.tcm-classics.org/yaowu#ZheChong','䗪虫','咸','寒','下品','ShennongBencaoJing','枚','熬','䗪虫，味咸寒。主心腹寒热洗洗，血积癥瘕，破坚，下血闭，生子大良。生川泽。',NULL,'䗪虫破血逐瘀、消癥散结。下瘀血汤之主药。治干血着脐下、产妇腹痛、癥瘕积聚。','干血、瘀块、癥瘕。','肝硬化、子宫肌瘤、闭经、产后腹痛。'),

-- ===== 十八反、十九畏涉及的缺失药物（补充完整信息） =====
('http://www.tcm-classics.org/yaowu#BeiMu','贝母','辛','平','中品','ShennongBencaoJing','两','无','贝母，味辛平。主伤寒烦热，淋沥邪气，疝瘕，喉痹，乳难，金疮风痉。生晋地。',NULL,'贝母清热化痰、散结消肿。后世分川贝、浙贝。川贝偏润肺止咳，浙贝偏清火散结。十八反中与乌头类相反。','咳嗽痰多、瘰疬、乳痈。','支气管炎、淋巴结结核、乳腺炎。'),
('http://www.tcm-classics.org/yaowu#BaiLian','白蔹','苦','平','下品','ShennongBencaoJing','两','无','白蔹，味苦平。主痈肿疽疮，散结气，止痛除热，目中赤，小儿惊痫温疟，阴下肿痛。生山谷。',NULL,'白蔹清热解毒、敛疮生肌。外用治疮疡肿毒，内服治湿热痢疾。十八反中与乌头类相反。','疮疡肿毒、烫伤、湿热痢。','皮肤感染、烧伤、肠炎。'),
('http://www.tcm-classics.org/yaowu#BaiJi','白及','苦','平','下品','ShennongBencaoJing','两','无','白及，味苦平。主痈肿恶疮败疽，伤阴死肌，胃中邪气，贼风鬼击，痱缓不收。生川谷。',NULL,'白及收敛止血、消肿生肌。为肺胃出血要药。又治手足皲裂、烫伤。十八反中与乌头类相反。','咯血、吐血、外伤出血、疮疡。','肺结核咯血、胃溃疡出血、烧烫伤。'),
('http://www.tcm-classics.org/yaowu#GanSui','甘遂','苦','寒','下品','ShennongBencaoJing','两','醋煮','甘遂，味苦寒。主大腹疝瘕，腹满，面目浮肿，留饮宿食，破癥坚积聚，利水谷道。生中山。',NULL,'甘遂泻水逐饮力峻。十枣汤、大陷胸汤之要药。治悬饮、水肿胀满。有毒，醋制减毒。十八反中与甘草相反。','胸腹积水、水肿胀满、二便不通。','胸腔积液、肝硬化腹水、肠梗阻。'),
('http://www.tcm-classics.org/yaowu#DaJi','大戟','苦','寒','下品','ShennongBencaoJing','两','醋煮','大戟，味苦寒。主蛊毒十二水，腹满急痛，积聚，中风皮肤疼痛，吐逆。生常山。',NULL,'大戟泻水逐饮、消肿散结。十枣汤之要药。治水肿胀满、痈肿疮毒。有毒，醋制减毒。十八反中与甘草相反。','水肿、腹水、痈肿。','肝硬化腹水、肾炎水肿、淋巴结结核。'),
('http://www.tcm-classics.org/yaowu#HaiZao','海藻','咸','寒','下品','ShennongBencaoJing','两','洗去咸','海藻，味苦寒。主瘿瘤气，颈下核，破散结气，痈肿癥瘕坚气，腹中上下鸣，下十二水肿。生东海。',NULL,'海藻消痰软坚、利水消肿。海藻玉壶汤之要药。治瘿瘤瘰疬、睾丸肿痛。十八反中与甘草相反，但历代亦有同用治瘿瘤者。','瘿瘤、瘰疬、水肿、睾丸肿痛。','甲状腺肿大、淋巴结结核、睾丸鞘膜积液。'),
('http://www.tcm-classics.org/yaowu#YuanHua','芫花','辛','温','下品','ShennongBencaoJing','两','醋煮','芫花，味辛温。主咳逆上气，喉鸣喘，咽肿，短气，蛊毒鬼疰，疝瘕痈肿，杀虫鱼。生淮源。',NULL,'芫花泻水逐饮、祛痰止咳。十枣汤之要药。治寒饮咳喘、水肿胀满。有毒，醋制减毒。十八反中与甘草相反。','寒饮咳喘、水肿、胸胁积水。','慢性支气管炎、胸腔积液、肝硬化腹水。'),
('http://www.tcm-classics.org/yaowu#LiLu','藜芦','苦','寒','下品','ShennongBencaoJing','两','无','藜芦，味苦寒。主蛊毒，咳逆，泄痢肠澼，头疡疥瘙，恶疮，杀诸虫毒，去死肌。生太山。',NULL,'藜芦涌吐风痰、杀虫疗疮。毒性剧烈，内服极慎。十八反中反人参、沙参、丹参、玄参、苦参、细辛、芍药七味。','中风痰壅、癫痫、疥癣。','现代极少内服，偶外用治疥癣。'),
('http://www.tcm-classics.org/yaowu#ShaShen','沙参','苦','微寒','上品','ShennongBencaoJing','两','无','沙参，味苦微寒。主血积惊气，除寒热，补中益肺气。久服利人。生河内。',NULL,'沙参养阴清肺、益胃生津。后世分南沙参、北沙参。南沙参兼能祛痰，北沙参养阴力胜。十八反中与藜芦相反。','肺燥干咳、阴虚劳嗽、津伤口渴。','慢性支气管炎、肺结核、糖尿病口渴。'),
('http://www.tcm-classics.org/yaowu#DanShen','丹参','苦','微寒','上品','ShennongBencaoJing','两','无','丹参，味苦微寒。主心腹邪气，肠鸣幽幽如走水，寒热积聚，破癥除瘕，止烦满益气。生桐柏。',NULL,'丹参活血调经、祛瘀止痛、凉血消痈、安神。后世谓"一味丹参功同四物"。十八反中与藜芦相反。','月经不调、胸痹心痛、癥瘕、心烦不眠。','冠心病、痛经、肝脾肿大、失眠。'),
('http://www.tcm-classics.org/yaowu#XuanShen','玄参','苦','微寒','中品','ShennongBencaoJing','两','无','玄参，味苦微寒。主腹中寒热积聚，女子产乳余疾，补肾气，令人目明。生川谷。',NULL,'玄参清热凉血、滋阴解毒。增液汤之要药。治温病热入营血、津伤便秘、咽喉肿痛。十八反中与藜芦相反。','热病伤阴、咽痛、瘰疬、便秘。','上呼吸道感染、扁桃体炎、糖尿病。'),
('http://www.tcm-classics.org/yaowu#KuShen','苦参','苦','寒','中品','ShennongBencaoJing','两','无','苦参，味苦寒。主心腹结气，癥瘕积聚，黄疸，溺有余沥，逐水，除痈肿，补中明目止泪。生汝南。',NULL,'苦参清热燥湿、杀虫利尿。治湿热泻痢、黄疸、带下、皮肤瘙痒。十八反中与藜芦相反。','湿热泻痢、黄疸、带下、湿疹。','细菌性痢疾、肝炎、阴道炎、湿疹。'),
('http://www.tcm-classics.org/yaowu#LiuHuang','硫黄','酸','温','下品','ShennongBencaoJing','两','研末','石硫黄，味酸温。主妇人阴蚀，疽痔恶血，坚筋骨，除头秃。能化金银铜铁奇物。生山谷。',NULL,'硫黄外用解毒杀虫疗疮，内服补火助阳通便。半硫丸治虚冷便秘。有毒。十九畏中与芒硝相畏。','阳痿、虚冷便秘、疥癣、湿疹。','慢性便秘、皮肤病（外用）。'),
('http://www.tcm-classics.org/yaowu#PiShuang','砒霜','辛苦','大热','下品','ShennongBencaoJing','分','研末','砒石，味辛苦大热。主寒热风痰，哮喘咳嗽，截疟，蚀疮去腐。有大毒。生信州。',NULL,'砒霜劫痰平喘、蚀疮去腐。毒性极烈，内服微量。十九畏中与水银相畏。现代已罕用。','哮喘、疟疾、痔疮、瘰疬。','急性早幼粒细胞白血病（三氧化二砷制剂）。'),
('http://www.tcm-classics.org/yaowu#LangDu','狼毒','辛苦','平','下品','ShennongBencaoJing','两','醋煮','狼毒，味辛苦平。主咳逆上气，破积聚饮食，寒热水气，恶疮鼠瘘疽蚀，鬼精蛊毒，杀飞鸟走兽。生山谷。',NULL,'狼毒逐水祛痰、破积杀虫。毒性峻烈，内服极慎。十九畏中与密陀僧相畏。','水肿腹胀、痰积、疥癣。','现代极少内服，偶外用治皮肤病。'),
('http://www.tcm-classics.org/yaowu#MiTuoSeng','密陀僧','咸辛','平','下品','MingYiBieLu','两','研末','密陀僧，味咸辛平。主久痢五痔，金疮，面上瘢䵟，面膏药用之。生波斯国。',NULL,'密陀僧燥湿杀虫、收敛防腐。多为外用。十九畏中与狼毒相畏。','痔疮、湿疹、疮疡不敛。','皮肤溃疡、湿疹（外用）。'),
('http://www.tcm-classics.org/yaowu#BaDou','巴豆','辛','热','下品','ShennongBencaoJing','枚','去皮心熬黑研如脂','巴豆，味辛温。主伤寒温疟寒热，破癥瘕结聚坚积，留饮痰癖，大腹水肿，荡练五脏六腑，开通闭塞，利水谷道，去恶肉。生巴郡。',NULL,'巴豆峻下冷积、逐水退肿、祛痰利咽。三物备急丸之要药。毒性峻烈，去油制霜减毒。十九畏中与牵牛子相畏。','寒积便秘、腹水、喉痹痰阻。','肠梗阻、肝硬化腹水、白喉（现代慎用）。'),
('http://www.tcm-classics.org/yaowu#QianNiuZi','牵牛子','苦','寒','下品','MingYiBieLu','两','炒','牵牛子，味苦寒。主下气，疗脚满水肿，除风毒，利小便。生田野。',NULL,'牵牛子泻下逐水、去积杀虫。治水肿胀满、二便不通、虫积腹痛。有毒。十九畏中与巴豆相畏。','水肿、腹水、便秘、虫积。','肝硬化腹水、肾炎水肿、肠道寄生虫。'),
('http://www.tcm-classics.org/yaowu#DingXiang','丁香','辛','温','未载','MingYiBieLu','枚','无','鸡舌香，味辛温。主温脾胃，降逆气，杀鬼蛊毒，去恶肉，疗齿痛。生交趾。',NULL,'丁香温中降逆、补肾助阳。治胃寒呕逆、脘腹冷痛、肾虚阳痿。十九畏中与郁金相畏。','胃寒呕吐、腹痛、阳痿。','慢性胃炎、呃逆、牙痛。'),
('http://www.tcm-classics.org/yaowu#YuJin','郁金','辛苦','寒','未载','MingYiBieLu','两','无','郁金，味辛苦寒。主血积，下气，生肌，止血，破恶血，淋沥尿血，金疮。生蜀郡。',NULL,'郁金活血止痛、行气解郁、清心凉血、利胆退黄。治胸胁刺痛、热病神昏、黄疸。十九畏中与丁香相畏。','胸胁痛、痛经、黄疸、神昏。','肝胆疾病、抑郁症、月经失调。'),
('http://www.tcm-classics.org/yaowu#SanLeng','三棱','苦','平','未载','MingYiBieLu','两','醋浸','三棱，味苦平。主老癖癥瘕结块，产后恶露不尽，瘀血腹痛，通经堕胎。生荆襄。',NULL,'三棱破血行气、消积止痛。治癥瘕积聚、瘀血经闭、食积胀痛。十九畏中与芒硝相畏。','癥瘕、闭经、食积、腹痛。','子宫肌瘤、肝脾肿大、消化不良。'),
('http://www.tcm-classics.org/yaowu#XiJiao','犀角','苦咸','寒','中品','ShennongBencaoJing','两','镑片锉末','犀角，味苦咸寒。主百毒蛊疰，邪鬼障气，杀钩吻鸩羽蛇毒，除邪，不迷惑魇寐。生永昌。',NULL,'犀角清心凉血、解毒定惊。犀角地黄汤之主药。治热入血分、神昏谵语、斑疹吐衄。十九畏中与川乌、草乌相畏。现已禁用，以水牛角代之。','高热神昏、斑疹、吐衄。','重症感染、DIC（用水牛角替代）。'),
('http://www.tcm-classics.org/yaowu#WuLingZhi','五灵脂','甘','温','未载','MingYiBieLu','两','酒研飞炼','五灵脂，味甘温。主心腹冷气，诸疮疡，杀虫，疗心腹积气，儿枕痛，产后瘀血。生北地。',NULL,'五灵脂活血止痛、化瘀止血。失笑散之要药。治胸胁脘腹刺痛、痛经、产后瘀痛。十九畏中与人参相畏。','瘀血疼痛、痛经、产后腹痛。','冠心病、胃痛、痛经、子宫内膜异位症。'),
('http://www.tcm-classics.org/yaowu#ChiShiZhi','赤石脂','甘涩','温','上品','ShennongBencaoJing','两','研末','赤石脂，味甘涩温。主黄疸泄痢，肠澼脓血，阴蚀下血赤白，邪气痈肿疽痔恶疮，头疡疥瘙。生济南。',NULL,'赤石脂涩肠止泻、收敛止血、生肌敛疮。桃花汤之要药。治久泻久痢、便血脱肛。十九畏中与肉桂相畏。','久泻久痢、便血、脱肛、疮疡不敛。','慢性结肠炎、溃疡性结肠炎、痔疮。'),
('http://www.tcm-classics.org/yaowu#ShuiYin','水银','辛','寒','中品','ShennongBencaoJing','两','研末','水银，味辛寒。主疥瘘痂疡白秃，杀皮肤虱，堕胎绝子，傅男子阴令无子。生符陵。',NULL,'水银外用杀虫攻毒。毒性极烈，内服禁。十九畏中与砒霜相畏。现代仅外用。','疥癣、梅毒、恶疮。','皮肤病（外用，现代已少用）。'),
('http://www.tcm-classics.org/yaowu#CaoWu','草乌','辛苦','热','下品','ShennongBencaoJing','枚','炮去皮脐','草乌头，味辛苦热。主中风恶风洗洗出汗，除寒湿痹，咳逆上气，破积聚寒热。生山谷。',NULL,'草乌祛风除湿、温经散寒止痛。力较川乌更峻。治风寒湿痹、关节剧痛。有毒，须炮制。十八反中反半夏、瓜蒌、贝母、白蔹、白及；十九畏中畏犀角。','寒湿痹痛、关节剧痛、跌打损伤。','类风湿关节炎、坐骨神经痛、麻醉止痛。');

-- ============================================================
-- 2. 药物-八纲关联（herb_bagang）
-- ============================================================
INSERT INTO herb_bagang (herb_uri, bagang_uri) VALUES
-- 桂枝
('http://www.tcm-classics.org/yaowu#GuiZhi','http://www.tcm-classics.org/bagang#Biao'),
('http://www.tcm-classics.org/yaowu#GuiZhi','http://www.tcm-classics.org/bagang#Re'),
-- 芍药
('http://www.tcm-classics.org/yaowu#ShaoYao','http://www.tcm-classics.org/bagang#Li'),
('http://www.tcm-classics.org/yaowu#ShaoYao','http://www.tcm-classics.org/bagang#Xu'),
-- 甘草
('http://www.tcm-classics.org/yaowu#GanCao','http://www.tcm-classics.org/bagang#Li'),
('http://www.tcm-classics.org/yaowu#GanCao','http://www.tcm-classics.org/bagang#Xu'),
-- 生姜
('http://www.tcm-classics.org/yaowu#ShengJiang','http://www.tcm-classics.org/bagang#Biao'),
('http://www.tcm-classics.org/yaowu#ShengJiang','http://www.tcm-classics.org/bagang#Han'),
-- 大枣
('http://www.tcm-classics.org/yaowu#DaZao','http://www.tcm-classics.org/bagang#Li'),
('http://www.tcm-classics.org/yaowu#DaZao','http://www.tcm-classics.org/bagang#Xu'),
-- 葛根
('http://www.tcm-classics.org/yaowu#GeGen','http://www.tcm-classics.org/bagang#Biao'),
('http://www.tcm-classics.org/yaowu#GeGen','http://www.tcm-classics.org/bagang#Re'),
-- 麻黄
('http://www.tcm-classics.org/yaowu#MaHuang','http://www.tcm-classics.org/bagang#Biao'),
('http://www.tcm-classics.org/yaowu#MaHuang','http://www.tcm-classics.org/bagang#Shi'),
-- 杏仁
('http://www.tcm-classics.org/yaowu#XingRen','http://www.tcm-classics.org/bagang#Li'),
-- 附子
('http://www.tcm-classics.org/yaowu#FuZi','http://www.tcm-classics.org/bagang#Li'),
('http://www.tcm-classics.org/yaowu#FuZi','http://www.tcm-classics.org/bagang#Han'),
('http://www.tcm-classics.org/yaowu#FuZi','http://www.tcm-classics.org/bagang#Xu'),
-- 白术
('http://www.tcm-classics.org/yaowu#BaiZhu','http://www.tcm-classics.org/bagang#Li'),
('http://www.tcm-classics.org/yaowu#BaiZhu','http://www.tcm-classics.org/bagang#Xu'),
-- 茯苓
('http://www.tcm-classics.org/yaowu#FuLing','http://www.tcm-classics.org/bagang#Li'),
('http://www.tcm-classics.org/yaowu#FuLing','http://www.tcm-classics.org/bagang#Shi'),
-- 牡丹皮
('http://www.tcm-classics.org/yaowu#MuDanPi','http://www.tcm-classics.org/bagang#Li'),
('http://www.tcm-classics.org/yaowu#MuDanPi','http://www.tcm-classics.org/bagang#Re'),
-- 桃仁
('http://www.tcm-classics.org/yaowu#TaoRen','http://www.tcm-classics.org/bagang#Li'),
('http://www.tcm-classics.org/yaowu#TaoRen','http://www.tcm-classics.org/bagang#Shi'),
-- 龙骨
('http://www.tcm-classics.org/yaowu#LongGu','http://www.tcm-classics.org/bagang#Li'),
-- 牡蛎
('http://www.tcm-classics.org/yaowu#MuLi','http://www.tcm-classics.org/bagang#Li'),
-- 黄芪
('http://www.tcm-classics.org/yaowu#HuangQi','http://www.tcm-classics.org/bagang#Li'),
('http://www.tcm-classics.org/yaowu#HuangQi','http://www.tcm-classics.org/bagang#Xu'),
-- 饴糖
('http://www.tcm-classics.org/yaowu#YiTang','http://www.tcm-classics.org/bagang#Li'),
('http://www.tcm-classics.org/yaowu#YiTang','http://www.tcm-classics.org/bagang#Xu'),
-- 蜀椒
('http://www.tcm-classics.org/yaowu#ShuJiao','http://www.tcm-classics.org/bagang#Li'),
('http://www.tcm-classics.org/yaowu#ShuJiao','http://www.tcm-classics.org/bagang#Han'),
-- 干姜
('http://www.tcm-classics.org/yaowu#GanJiang','http://www.tcm-classics.org/bagang#Li'),
('http://www.tcm-classics.org/yaowu#GanJiang','http://www.tcm-classics.org/bagang#Han'),
-- 人参
('http://www.tcm-classics.org/yaowu#RenShen','http://www.tcm-classics.org/bagang#Li'),
('http://www.tcm-classics.org/yaowu#RenShen','http://www.tcm-classics.org/bagang#Xu'),
-- 半夏
('http://www.tcm-classics.org/yaowu#BanXia','http://www.tcm-classics.org/bagang#Li'),
('http://www.tcm-classics.org/yaowu#BanXia','http://www.tcm-classics.org/bagang#Shi'),
-- 厚朴
('http://www.tcm-classics.org/yaowu#HouPo','http://www.tcm-classics.org/bagang#Li'),
('http://www.tcm-classics.org/yaowu#HouPo','http://www.tcm-classics.org/bagang#Shi'),
-- 枳实
('http://www.tcm-classics.org/yaowu#ZhiShi','http://www.tcm-classics.org/bagang#Li'),
('http://www.tcm-classics.org/yaowu#ZhiShi','http://www.tcm-classics.org/bagang#Shi'),
-- 大黄
('http://www.tcm-classics.org/yaowu#DaHuang','http://www.tcm-classics.org/bagang#Li'),
('http://www.tcm-classics.org/yaowu#DaHuang','http://www.tcm-classics.org/bagang#Re'),
('http://www.tcm-classics.org/yaowu#DaHuang','http://www.tcm-classics.org/bagang#Shi'),
-- 芒硝
('http://www.tcm-classics.org/yaowu#MangXiao','http://www.tcm-classics.org/bagang#Li'),
('http://www.tcm-classics.org/yaowu#MangXiao','http://www.tcm-classics.org/bagang#Re'),
-- 细辛
('http://www.tcm-classics.org/yaowu#XiXin','http://www.tcm-classics.org/bagang#Li'),
('http://www.tcm-classics.org/yaowu#XiXin','http://www.tcm-classics.org/bagang#Han'),
-- 五味子
('http://www.tcm-classics.org/yaowu#WuWeiZi','http://www.tcm-classics.org/bagang#Li'),
('http://www.tcm-classics.org/yaowu#WuWeiZi','http://www.tcm-classics.org/bagang#Xu'),
-- 柴胡
('http://www.tcm-classics.org/yaowu#ChaiHu','http://www.tcm-classics.org/bagang#BanBiaoBanLi'),
-- 黄芩
('http://www.tcm-classics.org/yaowu#HuangQin','http://www.tcm-classics.org/bagang#Li'),
('http://www.tcm-classics.org/yaowu#HuangQin','http://www.tcm-classics.org/bagang#Re'),
-- 黄连
('http://www.tcm-classics.org/yaowu#HuangLian','http://www.tcm-classics.org/bagang#Li'),
('http://www.tcm-classics.org/yaowu#HuangLian','http://www.tcm-classics.org/bagang#Re'),
-- 瓜蒌根
('http://www.tcm-classics.org/yaowu#GuaLouGen','http://www.tcm-classics.org/bagang#Li'),
('http://www.tcm-classics.org/yaowu#GuaLouGen','http://www.tcm-classics.org/bagang#Re'),
-- 铅丹
('http://www.tcm-classics.org/yaowu#QianDan','http://www.tcm-classics.org/bagang#Li'),
-- 石膏
('http://www.tcm-classics.org/yaowu#ShiGao','http://www.tcm-classics.org/bagang#Li'),
('http://www.tcm-classics.org/yaowu#ShiGao','http://www.tcm-classics.org/bagang#Re'),
-- 知母
('http://www.tcm-classics.org/yaowu#ZhiMu','http://www.tcm-classics.org/bagang#Li'),
('http://www.tcm-classics.org/yaowu#ZhiMu','http://www.tcm-classics.org/bagang#Re'),
-- 粳米
('http://www.tcm-classics.org/yaowu#JingMi','http://www.tcm-classics.org/bagang#Li'),
('http://www.tcm-classics.org/yaowu#JingMi','http://www.tcm-classics.org/bagang#Xu'),
-- 竹叶
('http://www.tcm-classics.org/yaowu#ZhuYe','http://www.tcm-classics.org/bagang#Li'),
('http://www.tcm-classics.org/yaowu#ZhuYe','http://www.tcm-classics.org/bagang#Re'),
-- 麦门冬
('http://www.tcm-classics.org/yaowu#MaiMenDong','http://www.tcm-classics.org/bagang#Li'),
('http://www.tcm-classics.org/yaowu#MaiMenDong','http://www.tcm-classics.org/bagang#Xu'),
-- 栀子
('http://www.tcm-classics.org/yaowu#ZhiZi','http://www.tcm-classics.org/bagang#Li'),
('http://www.tcm-classics.org/yaowu#ZhiZi','http://www.tcm-classics.org/bagang#Re'),
-- 香豉
('http://www.tcm-classics.org/yaowu#XiangChi','http://www.tcm-classics.org/bagang#Biao'),
('http://www.tcm-classics.org/yaowu#XiangChi','http://www.tcm-classics.org/bagang#Re'),
-- 猪苓
('http://www.tcm-classics.org/yaowu#ZhuLing','http://www.tcm-classics.org/bagang#Li'),
('http://www.tcm-classics.org/yaowu#ZhuLing','http://www.tcm-classics.org/bagang#Shi'),
-- 泽泻
('http://www.tcm-classics.org/yaowu#ZeXie','http://www.tcm-classics.org/bagang#Li'),
('http://www.tcm-classics.org/yaowu#ZeXie','http://www.tcm-classics.org/bagang#Shi'),
-- 葱白
('http://www.tcm-classics.org/yaowu#CongBai','http://www.tcm-classics.org/bagang#Li'),
('http://www.tcm-classics.org/yaowu#CongBai','http://www.tcm-classics.org/bagang#Han'),
-- 人尿
('http://www.tcm-classics.org/yaowu#RenNiao','http://www.tcm-classics.org/bagang#Li'),
('http://www.tcm-classics.org/yaowu#RenNiao','http://www.tcm-classics.org/bagang#Re'),
-- 猪胆汁
('http://www.tcm-classics.org/yaowu#ZhuDanZhi','http://www.tcm-classics.org/bagang#Li'),
('http://www.tcm-classics.org/yaowu#ZhuDanZhi','http://www.tcm-classics.org/bagang#Re'),
-- 鸡子黄
('http://www.tcm-classics.org/yaowu#HuangLianEJiaoTang_JiZiHuang','http://www.tcm-classics.org/bagang#Li'),
('http://www.tcm-classics.org/yaowu#HuangLianEJiaoTang_JiZiHuang','http://www.tcm-classics.org/bagang#Xu'),
-- 阿胶
('http://www.tcm-classics.org/yaowu#EJiao','http://www.tcm-classics.org/bagang#Li'),
('http://www.tcm-classics.org/yaowu#EJiao','http://www.tcm-classics.org/bagang#Xu'),
-- 乌梅
('http://www.tcm-classics.org/yaowu#WuMei','http://www.tcm-classics.org/bagang#Li'),
-- 当归
('http://www.tcm-classics.org/yaowu#DangGui','http://www.tcm-classics.org/bagang#Li'),
('http://www.tcm-classics.org/yaowu#DangGui','http://www.tcm-classics.org/bagang#Xu'),
-- 通草
('http://www.tcm-classics.org/yaowu#TongCao','http://www.tcm-classics.org/bagang#Li'),
-- 吴茱萸
('http://www.tcm-classics.org/yaowu#WuZhuYu','http://www.tcm-classics.org/bagang#Li'),
('http://www.tcm-classics.org/yaowu#WuZhuYu','http://www.tcm-classics.org/bagang#Han'),
-- 白头翁
('http://www.tcm-classics.org/yaowu#BaiTouWeng','http://www.tcm-classics.org/bagang#Li'),
('http://www.tcm-classics.org/yaowu#BaiTouWeng','http://www.tcm-classics.org/bagang#Re'),
-- 黄柏
('http://www.tcm-classics.org/yaowu#HuangBai','http://www.tcm-classics.org/bagang#Li'),
('http://www.tcm-classics.org/yaowu#HuangBai','http://www.tcm-classics.org/bagang#Re'),
-- 秦皮
('http://www.tcm-classics.org/yaowu#QinPi','http://www.tcm-classics.org/bagang#Li'),
('http://www.tcm-classics.org/yaowu#QinPi','http://www.tcm-classics.org/bagang#Re'),
-- 旋覆花
('http://www.tcm-classics.org/yaowu#XuanFuHua','http://www.tcm-classics.org/bagang#Li'),
-- 代赭石
('http://www.tcm-classics.org/yaowu#DaiZheShi','http://www.tcm-classics.org/bagang#Li'),
('http://www.tcm-classics.org/yaowu#DaiZheShi','http://www.tcm-classics.org/bagang#Re'),
-- 瓜蒌实
('http://www.tcm-classics.org/yaowu#GuaLouShi','http://www.tcm-classics.org/bagang#Li'),
('http://www.tcm-classics.org/yaowu#GuaLouShi','http://www.tcm-classics.org/bagang#Re'),
-- 薤白
('http://www.tcm-classics.org/yaowu#XieBai','http://www.tcm-classics.org/bagang#Li'),
('http://www.tcm-classics.org/yaowu#XieBai','http://www.tcm-classics.org/bagang#Han'),
-- 白酒
('http://www.tcm-classics.org/yaowu#BaiJiu','http://www.tcm-classics.org/bagang#Li'),
-- 防风
('http://www.tcm-classics.org/yaowu#FangFeng','http://www.tcm-classics.org/bagang#Biao'),
-- 川乌
('http://www.tcm-classics.org/yaowu#ChuanWu','http://www.tcm-classics.org/bagang#Li'),
('http://www.tcm-classics.org/yaowu#ChuanWu','http://www.tcm-classics.org/bagang#Han'),
-- 蜜
('http://www.tcm-classics.org/yaowu#Mi','http://www.tcm-classics.org/bagang#Li'),
('http://www.tcm-classics.org/yaowu#Mi','http://www.tcm-classics.org/bagang#Xu'),
-- 川芎
('http://www.tcm-classics.org/yaowu#ChuanXiong','http://www.tcm-classics.org/bagang#Li'),
-- 艾叶
('http://www.tcm-classics.org/yaowu#AiYe','http://www.tcm-classics.org/bagang#Li'),
('http://www.tcm-classics.org/yaowu#AiYe','http://www.tcm-classics.org/bagang#Han'),
-- 干地黄
('http://www.tcm-classics.org/yaowu#DiHuang','http://www.tcm-classics.org/bagang#Li'),
('http://www.tcm-classics.org/yaowu#DiHuang','http://www.tcm-classics.org/bagang#Xu'),
-- 酸枣仁
('http://www.tcm-classics.org/yaowu#SuanZaoRen','http://www.tcm-classics.org/bagang#Li'),
('http://www.tcm-classics.org/yaowu#SuanZaoRen','http://www.tcm-classics.org/bagang#Xu'),
-- 灶心黄土
('http://www.tcm-classics.org/yaowu#ZaoXinHuangTu','http://www.tcm-classics.org/bagang#Li'),
('http://www.tcm-classics.org/yaowu#ZaoXinHuangTu','http://www.tcm-classics.org/bagang#Han'),
-- 赤小豆
('http://www.tcm-classics.org/yaowu#ChiXiaoDou','http://www.tcm-classics.org/bagang#Li'),
('http://www.tcm-classics.org/yaowu#ChiXiaoDou','http://www.tcm-classics.org/bagang#Shi'),
-- 浆水
('http://www.tcm-classics.org/yaowu#JiangShui','http://www.tcm-classics.org/bagang#Li'),
-- 百合
('http://www.tcm-classics.org/yaowu#BaiHe','http://www.tcm-classics.org/bagang#Li'),
('http://www.tcm-classics.org/yaowu#BaiHe','http://www.tcm-classics.org/bagang#Xu'),
-- 生地黄汁
('http://www.tcm-classics.org/yaowu#ShengDiHuangZhi','http://www.tcm-classics.org/bagang#Li'),
('http://www.tcm-classics.org/yaowu#ShengDiHuangZhi','http://www.tcm-classics.org/bagang#Xu'),
-- 泉水
('http://www.tcm-classics.org/yaowu#QuanShui','http://www.tcm-classics.org/bagang#Li'),
-- 射干
('http://www.tcm-classics.org/yaowu#SheGan','http://www.tcm-classics.org/bagang#Li'),
('http://www.tcm-classics.org/yaowu#SheGan','http://www.tcm-classics.org/bagang#Re'),
-- 紫菀
('http://www.tcm-classics.org/yaowu#ZiWan','http://www.tcm-classics.org/bagang#Li'),
-- 款冬花
('http://www.tcm-classics.org/yaowu#KuanDongHua','http://www.tcm-classics.org/bagang#Li'),
-- 小麦
('http://www.tcm-classics.org/yaowu#XiaoMai','http://www.tcm-classics.org/bagang#Li'),
-- 泽漆
('http://www.tcm-classics.org/yaowu#ZeQi','http://www.tcm-classics.org/bagang#Li'),
('http://www.tcm-classics.org/yaowu#ZeQi','http://www.tcm-classics.org/bagang#Shi'),
-- 紫参
('http://www.tcm-classics.org/yaowu#ZiCan','http://www.tcm-classics.org/bagang#Li'),
-- 白前
('http://www.tcm-classics.org/yaowu#BaiQian','http://www.tcm-classics.org/bagang#Li'),
-- 东流水
('http://www.tcm-classics.org/yaowu#DongLiuShui','http://www.tcm-classics.org/bagang#Li'),
-- 薏苡仁
('http://www.tcm-classics.org/yaowu#YiYiRen','http://www.tcm-classics.org/bagang#Li'),
('http://www.tcm-classics.org/yaowu#YiYiRen','http://www.tcm-classics.org/bagang#Shi'),
-- 败酱草
('http://www.tcm-classics.org/yaowu#BaiJiangCao','http://www.tcm-classics.org/bagang#Li'),
('http://www.tcm-classics.org/yaowu#BaiJiangCao','http://www.tcm-classics.org/bagang#Re'),
-- 水蛭
('http://www.tcm-classics.org/yaowu#ShuiZhi','http://www.tcm-classics.org/bagang#Li'),
('http://www.tcm-classics.org/yaowu#ShuiZhi','http://www.tcm-classics.org/bagang#Shi'),
-- 虻虫
('http://www.tcm-classics.org/yaowu#MengChong','http://www.tcm-classics.org/bagang#Li'),
('http://www.tcm-classics.org/yaowu#MengChong','http://www.tcm-classics.org/bagang#Shi'),
-- 苦酒
('http://www.tcm-classics.org/yaowu#KuJiu','http://www.tcm-classics.org/bagang#Li'),
-- 清酒
('http://www.tcm-classics.org/yaowu#QingJiu','http://www.tcm-classics.org/bagang#Li'),
-- 白饮
('http://www.tcm-classics.org/yaowu#BaiYin','http://www.tcm-classics.org/bagang#Li'),
-- 温粉
('http://www.tcm-classics.org/yaowu#WenFen','http://www.tcm-classics.org/bagang#Biao'),
-- 盐
('http://www.tcm-classics.org/yaowu#Yan','http://www.tcm-classics.org/bagang#Li'),
-- 肉桂
('http://www.tcm-classics.org/yaowu#RouGui','http://www.tcm-classics.org/bagang#Li'),
('http://www.tcm-classics.org/yaowu#RouGui','http://www.tcm-classics.org/bagang#Han'),
-- 龙胆草
('http://www.tcm-classics.org/yaowu#LongDanCao','http://www.tcm-classics.org/bagang#Li'),
('http://www.tcm-classics.org/yaowu#LongDanCao','http://www.tcm-classics.org/bagang#Re'),
-- 陈皮
('http://www.tcm-classics.org/yaowu#ChenPi','http://www.tcm-classics.org/bagang#Li'),
-- 槟榔
('http://www.tcm-classics.org/yaowu#BinLang','http://www.tcm-classics.org/bagang#Li'),
-- 雄黄
('http://www.tcm-classics.org/yaowu#XiongHuang','http://www.tcm-classics.org/bagang#Li'),
-- 䗪虫
('http://www.tcm-classics.org/yaowu#ZheChong','http://www.tcm-classics.org/bagang#Li'),
('http://www.tcm-classics.org/yaowu#ZheChong','http://www.tcm-classics.org/bagang#Shi');

-- ============================================================
-- 3. 十八反（herb_antagonistic）
-- 乌头（附子、川乌、草乌）反半夏、瓜蒌、贝母、白蔹、白及
-- ============================================================
INSERT INTO herb_antagonistic (herb_uri, antagonist_uri) VALUES
('http://www.tcm-classics.org/yaowu#FuZi', 'http://www.tcm-classics.org/yaowu#BanXia'),
('http://www.tcm-classics.org/yaowu#FuZi', 'http://www.tcm-classics.org/yaowu#GuaLouShi'),
('http://www.tcm-classics.org/yaowu#FuZi', 'http://www.tcm-classics.org/yaowu#GuaLouGen'),
('http://www.tcm-classics.org/yaowu#FuZi', 'http://www.tcm-classics.org/yaowu#BeiMu'),
('http://www.tcm-classics.org/yaowu#FuZi', 'http://www.tcm-classics.org/yaowu#BaiLian'),
('http://www.tcm-classics.org/yaowu#FuZi', 'http://www.tcm-classics.org/yaowu#BaiJi'),
('http://www.tcm-classics.org/yaowu#ChuanWu', 'http://www.tcm-classics.org/yaowu#BanXia'),
('http://www.tcm-classics.org/yaowu#ChuanWu', 'http://www.tcm-classics.org/yaowu#GuaLouShi'),
('http://www.tcm-classics.org/yaowu#ChuanWu', 'http://www.tcm-classics.org/yaowu#GuaLouGen'),
('http://www.tcm-classics.org/yaowu#ChuanWu', 'http://www.tcm-classics.org/yaowu#BeiMu'),
('http://www.tcm-classics.org/yaowu#ChuanWu', 'http://www.tcm-classics.org/yaowu#BaiLian'),
('http://www.tcm-classics.org/yaowu#ChuanWu', 'http://www.tcm-classics.org/yaowu#BaiJi'),
('http://www.tcm-classics.org/yaowu#CaoWu', 'http://www.tcm-classics.org/yaowu#BanXia'),
('http://www.tcm-classics.org/yaowu#CaoWu', 'http://www.tcm-classics.org/yaowu#GuaLouShi'),
('http://www.tcm-classics.org/yaowu#CaoWu', 'http://www.tcm-classics.org/yaowu#GuaLouGen'),
('http://www.tcm-classics.org/yaowu#CaoWu', 'http://www.tcm-classics.org/yaowu#BeiMu'),
('http://www.tcm-classics.org/yaowu#CaoWu', 'http://www.tcm-classics.org/yaowu#BaiLian'),
('http://www.tcm-classics.org/yaowu#CaoWu', 'http://www.tcm-classics.org/yaowu#BaiJi');

-- 甘草反甘遂、大戟、海藻、芫花
INSERT INTO herb_antagonistic (herb_uri, antagonist_uri) VALUES
('http://www.tcm-classics.org/yaowu#GanCao', 'http://www.tcm-classics.org/yaowu#GanSui'),
('http://www.tcm-classics.org/yaowu#GanCao', 'http://www.tcm-classics.org/yaowu#DaJi'),
('http://www.tcm-classics.org/yaowu#GanCao', 'http://www.tcm-classics.org/yaowu#HaiZao'),
('http://www.tcm-classics.org/yaowu#GanCao', 'http://www.tcm-classics.org/yaowu#YuanHua');

-- 藜芦反人参、沙参、丹参、玄参、苦参、细辛、芍药
INSERT INTO herb_antagonistic (herb_uri, antagonist_uri) VALUES
('http://www.tcm-classics.org/yaowu#LiLu', 'http://www.tcm-classics.org/yaowu#RenShen'),
('http://www.tcm-classics.org/yaowu#LiLu', 'http://www.tcm-classics.org/yaowu#ShaShen'),
('http://www.tcm-classics.org/yaowu#LiLu', 'http://www.tcm-classics.org/yaowu#DanShen'),
('http://www.tcm-classics.org/yaowu#LiLu', 'http://www.tcm-classics.org/yaowu#XuanShen'),
('http://www.tcm-classics.org/yaowu#LiLu', 'http://www.tcm-classics.org/yaowu#KuShen'),
('http://www.tcm-classics.org/yaowu#LiLu', 'http://www.tcm-classics.org/yaowu#XiXin'),
('http://www.tcm-classics.org/yaowu#LiLu', 'http://www.tcm-classics.org/yaowu#ShaoYao');

-- ============================================================
-- 4. 十九畏（herb_fearing）
-- ============================================================
INSERT INTO herb_fearing (herb_uri, feared_uri) VALUES
('http://www.tcm-classics.org/yaowu#LiuHuang', 'http://www.tcm-classics.org/yaowu#MangXiao'),
('http://www.tcm-classics.org/yaowu#ShuiYin', 'http://www.tcm-classics.org/yaowu#PiShuang'),
('http://www.tcm-classics.org/yaowu#LangDu', 'http://www.tcm-classics.org/yaowu#MiTuoSeng'),
('http://www.tcm-classics.org/yaowu#BaDou', 'http://www.tcm-classics.org/yaowu#QianNiuZi'),
('http://www.tcm-classics.org/yaowu#DingXiang', 'http://www.tcm-classics.org/yaowu#YuJin'),
('http://www.tcm-classics.org/yaowu#MangXiao', 'http://www.tcm-classics.org/yaowu#SanLeng'),
('http://www.tcm-classics.org/yaowu#ChuanWu', 'http://www.tcm-classics.org/yaowu#XiJiao'),
('http://www.tcm-classics.org/yaowu#CaoWu', 'http://www.tcm-classics.org/yaowu#XiJiao'),
('http://www.tcm-classics.org/yaowu#RenShen', 'http://www.tcm-classics.org/yaowu#WuLingZhi'),
('http://www.tcm-classics.org/yaowu#RouGui', 'http://www.tcm-classics.org/yaowu#ChiShiZhi');