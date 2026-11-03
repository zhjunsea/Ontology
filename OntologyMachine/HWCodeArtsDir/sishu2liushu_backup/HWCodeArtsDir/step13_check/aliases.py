# -*- coding: utf-8 -*-
# SKOS 同义词/别名映射：key = "<ns>:<LocalName>"（完整本体 IRI 的简写），
# value = [(altLabel, 来源), ...]。来源为四书具体卷篇，或本应用设计文档/业务约定；
# 查无出处者标【待考】。仅收录有据可依者，避免凭空造词（★10）。

ALIASES = {
    # ===== core =====
    "core:HeavenlyStem": [("十干", "渊海子平卷之一·论天干地支")],
    "core:EarthlyBranch": [("十二支", "渊海子平卷之一·论天干地支")],
    "core:SexagenaryCycle": [("六十花甲子", "渊海子平卷之一·论六十花甲子纳音并注解"),
                             ("花甲子", "三命通会卷一·论纳音取象")],
    "core:NaYin": [("纳音五行", "三命通会卷一·论纳音取象")],
    "core:HiddenStem": [("人元", "三命通会卷一·论人元司事"),
                        ("支藏人元", "渊海子平卷之一·又地支藏遁歌")],
    "core:LifeStage": [("长生", "三命通会卷二·论长生"),
                       ("十二长生", "三命通会卷二·论长生")],
    "core:FivePhase": [("五行之气", "三命通会卷一·论五行生克")],
    "core:YinYang": [("阴阳二气", "三命通会卷一·论阴阳")],
    "core:overcomes": [("相克", "三命通会卷一·论五行生克")],
    "core:generates": [("相生", "三命通会卷一·论五行生克")],

    # ===== calendar =====
    "calendar:MonthOrder": [("月令", "三命通会卷二·论遁月时"),
                            ("月建", "三命通会卷二·论遁月时")],
    "calendar:SolarTerm": [("节气", "渊海子平卷之一·论节候歌")],

    # ===== chart =====
    "chart:FourPillars": [("四柱", "渊海子平卷之一·论日为主"),
                          ("八字", "渊海子平卷之一·论日为主")],

    # ===== relations =====
    "relations:Clash": [("冲", "三命通会卷二·论冲"),
                        ("六冲", "三命通会卷二·论冲")],
    "relations:Harm": [("六害", "三命通会卷二·论六害"),
                       ("相害", "三命通会卷二·论六害"),
                       ("穿", "三命通会卷二·论六害")],
    "relations:Punishment": [("刑", "三命通会卷二·论三刑")],
    "relations:SixCombination": [("地支六合", "三命通会卷二·论六合")],
    "relations:TripleCombination": [("三合", "三命通会卷二·论三合"),
                                    ("半合", "三命通会卷二·论三合"),
                                    ("三合局", "三命通会卷二·论三合")],
    "relations:StemCombination": [("五合", "三命通会卷二·论天干合"),
                                  ("天干相合", "三命通会卷二·论天干合")],
    "relations:TombRelation": [("墓库", "三命通会卷二·论墓库"),
                               ("入墓", "三命通会卷二·论墓库")],
    "relations:Bondage": [("合绊", "本应用设计文档·干支作用域"),
                          ("绊", "本应用设计文档·干支作用域")],
    "relations:HiddenRelation": [("暗合", "三命通会卷二·论暗合"),
                                 ("遥冲", "三命通会卷二·论遥冲")],

    # ===== tengod =====
    "tengod:PianGuan": [("七杀", "渊海子平卷之一·论七杀"),
                        ("七煞", "三命通会卷二·论七煞")],
    "tengod:PianYin": [("枭神", "三命通会卷二·论枭神"),
                       ("倒食", "渊海子平卷之一·论倒食")],
    "tengod:JieCai": [("败财", "三命通会卷二·论劫财")],
    "tengod:OfficialKillMix": [("官煞混杂", "三命通会卷二·论官煞混杂")],
    "tengod:FoodHurtsKill": [("食伤制煞", "子平真诠·论食伤制煞")],


    # ===== deity =====
    "deity:Deity": [("神杀", "三命通会卷二·诸神煞篇")],
    "deity:Void": [("旬空", "三命通会卷二·论空亡"),
                   ("空", "三命通会卷二·论空亡")],
    "deity:LuMa": [("禄马", "三命通会卷二·论禄马")],
    "deity:AuspiciousDeity": [("吉神", "三命通会卷二·诸神煞篇")],
    "deity:InauspiciousDeity": [("凶神", "三命通会卷二·诸神煞篇"),
                                ("凶煞", "三命通会卷二·诸神煞篇")],

    # ===== pattern =====
    "pattern:Pattern": [("格", "子平真诠·论格局")],
    "pattern:RegularPattern": [("正格", "子平真诠·论正格"),
                               ("八格", "子平真诠·论正格")],
    "pattern:SpecialPattern": [("外格", "子平真诠·论外格"),
                               ("杂格", "子平真诠·论杂格")],
    "pattern:MixedQiPattern": [("杂气", "子平真诠·论杂气")],
    "pattern:PatternQuality": [("成败", "子平真诠·论格局成败")],

    # ===== yongshen =====
    "yongshen:YongShen": [("喜用神", "子平真诠·论用神")],
    "yongshen:Tiaohou": [("调候", "《穷通宝鉴》（《造化元钥》·徐乐吾评注）·五行总论及各干逐月取用")],
    "yongshen:Vitality": [("旺弱", "三命通会卷二·论旺衰"),
                          ("强弱", "三命通会卷二·论旺衰")],
    "yongshen:Level": [("层次", "本应用设计文档·用神旺衰域")],
    "yongshen:FuGe": [("富", "三命通会卷二·论富贵贫贱")],
    "yongshen:GuiGe": [("贵", "三命通会卷二·论富贵贫贱")],
    "yongshen:Purity": [("清浊", "子平真诠·论清浊")],
    "yongshen:FlowRelation": [("源流", "三命通会卷二·论源流"),
                              ("通关", "三命通会卷二·论通关")],

    # ===== fortune =====
    "fortune:AnnualFortune": [("太岁", "三命通会卷二·论太岁")],
    "fortune:SubAnnualFortune": [("流月", "三命通会卷二·论流月"),
                                 ("流日", "三命通会卷二·论流日")],
    "fortune:YunHuaQi": [("化气", "三命通会卷二·论化气")],
    "fortune:LifeStageLimit": [("运限", "三命通会卷二·论运限")],
    "fortune:SpecialFortunePattern": [("岁运格局", "本应用设计文档·岁运域")],

    # ===== reading =====
    "reading:ReadingConclusion": [("命书", "本应用设计文档·断语域（综合结论）")],
    "reading:MarriageMatch": [("合婚", "《神峰通考》·合婚（已归档）")],
    "reading:LadyFortune": [("女命", "三命通会卷三·论女命"),
                            ("坤造", "三命通会卷三·论女命")],
    "reading:ChildFortune": [("小儿关煞", "三命通会卷三·论小儿关煞")],
    "reading:CategoryFortune": [("分类断", "本应用设计文档·断语域")],
    "reading:KinshipFortune": [("六亲", "渊海子平卷之一·论六亲")],
    "reading:Temperament": [("性情", "三命通会卷二·论性情")],
    "reading:Admonition": [("修身", "本应用设计文档·断语域（劝诫修身）")],
    "reading:ReadingItem": [("断语", "本应用设计文档·断语域")],
    "reading:CaseRecord": [("命例", "本应用设计文档·断语域（隐私存档 S6）")],

    # ===== diagnosis =====
    "diagnosis:SourceNote": [("出处", "本应用设计文档·出处标注域 S3")],
    "diagnosis:ToBeVerified": [("待考", "公共铁律·无出处即标待考（★8/10）")],
    "diagnosis:SchoolVariant": [("流派", "本应用设计文档·出处标注域 S3")],

    # ===== rules =====
    "rules:AlertFlag": [("告警", "本应用设计文档·告警标志域 S5")],
    "rules:alertType": [("告警类型", "本应用设计文档·告警标志域 S5")],

    # ===== 第 15 步（六典籍覆盖补全）新增 =====
    "reading:DayHourClause": [("日时断条文", "三命通会卷八·六X日Y时断")],
    "yongshen:TiaohouClause": [("调候逐月", "穷通宝鉴·各干逐月取用")],
    "yongshen:BingYao": [("病药", "神峰通考·病药说"),
                          ("四病四药", "神峰通考·雕枯旺弱四病说/损益生长四药说")],
    "pattern:AssimilationPattern": [("从化", "滴天髓·从象/化象/假从/假化")],
    "pattern:SequencePattern": [("顺逆", "滴天髓·顺局/反局/战局/合局")],
    "pattern:SovereignPattern": [("君臣", "滴天髓·君象/臣象/母象/子象")],
    "fortune:ZhenYuan": [("贞元", "滴天髓·贞元")],
}
