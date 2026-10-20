# -*- coding: utf-8 -*-
"""
生成梅花易数（PlumBlossom）单文件本体 ontology/plumblossom-all.owl（第 6 步：TBox + 典型 ABox）
依据：design/PlumBlossom应用-本体顶层设计.md (v3) + design/PlumBlossom应用-本体核心设计.md (v3.1)
形态基线：OntologyFrameworkExample/ontology/*.owl（SWRL 采用显式 rdf:List + swrl:AtomList 形式）
"""
import os, io

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)),
                   "..", "OntologyFrameworkPlumBlossom", "ontology", "plumblossom-all.owl")
CAT = os.path.join(os.path.dirname(os.path.abspath(__file__)),
                   "..", "OntologyFrameworkPlumBlossom", "ontology", "catalog-v001.xml")

L = []
def w(s=""):
    L.append(s)

# ---------------- 数据表 ----------------
# (en, label, number, fivePhase, directionEn, directionLabel, nature, natureLabel, role, yinyang, symbol)
TRIGRAMS = [
    ("Qian", "乾", 1, "Jin", "NorthWest", "西北", "Jian", "健", "父", "阳", "乾为天"),
    ("Dui",  "兑", 2, "Jin", "West",      "西",   "Yue",  "说", "少女", "阴", "兑为泽"),
    ("Li",   "离", 3, "Huo", "South",     "南",   "Li",   "丽", "中女", "阴", "离为火"),
    ("Zhen", "震", 4, "Mu",  "East",      "东",   "Qi",   "起", "长男", "阳", "震为雷"),
    ("Xun",  "巽", 5, "Mu",  "SouthEast", "东南", "Ru",   "入", "长女", "阴", "巽为风"),
    ("Kan",  "坎", 6, "Shui","North",     "北",   "Xian", "陷", "中男", "阳", "坎为水"),
    ("Gen",  "艮", 7, "Tu",  "NorthEast", "东北", "Zhi",  "止", "少男", "阳", "艮为山"),
    ("Kun",  "坤", 8, "Tu",  "SouthWest", "西南", "Shun", "顺", "母",   "阴", "坤为地"),
]
DIRECTIONS = [("East","东"),("South","南"),("West","西"),("North","北"),
              ("SouthEast","东南"),("SouthWest","西南"),("NorthEast","东北"),("NorthWest","西北")]
FIVEPHASES = [("Jin","金"),("Mu","木"),("Shui","水"),("Huo","火"),("Tu","土")]
VITALITY = [("Wang","旺"),("Xiang","相"),("Xiu","休"),("Qiu","囚"),("Si","死")]
AUSP = [("DaJi","大吉",5),("Ji","吉",4),("Ping","平",3),("Xiong","凶",2),("DaXiong","大凶",1)]
GRADES = [("ShangShang","上上",6),("ShangZhong","上中",5),("ZhongZhong","中中",4),
          ("ZhongPing","中平",3),("XiaZhong","下中",2),("XiaXia","下下",1)]
TENRESP = [("Zheng","正应"),("Hu","互应"),("Bian","变应"),("Fang","方应"),("Ri","日应"),
           ("Ke","刻应"),("Wai","外应"),("TianShi","天时应"),("DiLi","地理应"),("RenShi","人事应")]
ESSENTIALS = [("Er","耳"),("Mu","目"),("Xin","心")]
PRECEPTS = [("BuCheng","不诚不占"),("WuShi","无事不占"),("BuYi","不义不占"),("ChongGua","重卦不占")]
CATEGORIES = [("TianShi","天时"),("RenShi","人事"),("JiaZhai","家宅"),("WuShe","屋舍"),("HunYin","婚姻"),
              ("ShengChan","生产"),("YinShi","饮食"),("QiuMou","求谋"),("QiuMing","求名"),("QiuCai","求财"),
              ("JiaoYi","交易"),("ChuXing","出行"),("XingRen","行人"),("YeJian","谒见"),("ShiWu","失物"),
              ("JiBing","疾病"),("GuanSong","官讼"),("FenMu","坟墓")]
# (en, label, fivePhase)
STEMS = [("Jia","甲","Mu"),("Yi","乙","Mu"),("Bing","丙","Huo"),("Ding","丁","Huo"),("Wu","戊","Tu"),
         ("Ji","己","Tu"),("Geng","庚","Jin"),("Xin","辛","Jin"),("Ren","壬","Shui"),("Gui","癸","Shui")]
# (en, label, animal, order, fivePhase)
BRANCHES = [("Zi","子","鼠",1,"Shui"),("Chou","丑","牛",2,"Tu"),("Yin","寅","虎",3,"Mu"),
            ("Mao","卯","兔",4,"Mu"),("Chen","辰","龙",5,"Tu"),("Si","巳","蛇",6,"Huo"),
            ("Wu","午","马",7,"Huo"),("Wei","未","羊",8,"Tu"),("Shen","申","猴",9,"Jin"),
            ("You","酉","鸡",10,"Jin"),("Xu","戌","狗",11,"Tu"),("Hai","亥","猪",12,"Shui")]
LIFESTAGE = [("Mu","木生在亥"),("Huo","火生于寅"),("Jin","金生于巳"),("ShuiTu","水土长生居申")]
# 代表性六十甲子 + 纳音（全量留待第 12 步扩全集）
CYCLES = [("JiaZi","甲子","HaiZhongJin"),("BingYin","丙寅","LuZhongHuo"),("WuChen","戊辰","DaLinMu"),
          ("GengWu","庚午","LuPangTu"),("RenShen","壬申","JianFengJin"),("JiaXu","甲戌","ShanTouHuo")]
NAYIN = [("HaiZhongJin","海中金","Jin"),("LuZhongHuo","炉中火","Huo"),("DaLinMu","大林木","Mu"),
         ("LuPangTu","路旁土","Tu"),("JianFengJin","剑锋金","Jin"),("ShanTouHuo","山头火","Huo")]
SYMDIMS = [("TianShi","天时"),("DiLi","地理"),("RenWu","人物"),("ShiXu","时序"),
           ("DongWu","动物"),("JingWu","静物"),("WuShe","屋舍"),("HunYin","婚姻")]

# 六十四卦名录（序 1~64，用于 Seq 个体；此处全列以支撑 R15 名录与卦序）
HEXNAMES = ["乾为天","坤为地","水雷屯","山水蒙","水天需","天水讼","地水师","水地比",
            "风天小畜","天泽履","地天泰","天地否","天火同人","火天大有","地山谦","雷地豫",
            "泽雷随","山风蛊","地泽临","风地观","火雷噬嗑","山火贲","山地剥","地雷复",
            "天雷无妄","山天大畜","山雷颐","泽风大过","坎为水","离为火","泽山咸","雷风恒",
            "天山遁","雷天大壮","火地晋","地火明夷","风火家人","火泽睽","水山蹇","雷水解",
            "山泽损","风雷益","泽天夬","天风姤","泽地萃","地风升","泽水困","水风井",
            "泽火革","火风鼎","震为雷","艮为山","风山渐","雷泽归妹","雷火丰","火山旅",
            "巽为风","兑为泽","风水涣","水泽节","风泽中孚","雷山小过","水火既济","火水未济"]

# 乾、坤两卦之《周易》经文（外部经典引入，非原著收录；见核心设计 §9-6）
QIAN_DICTUM = "元亨利贞。"
QIAN_LINES = ["初九：潜龙勿用。","九二：见龙在田，利见大人。","九三：君子终日乾乾，夕惕若厉，无咎。",
              "九四：或跃在渊，无咎。","九五：飞龙在天，利见大人。","上九：亢龙有悔。"]
KUN_DICTUM = "元亨，利牝马之贞。君子有攸往，先迷后得主利。西南得朋，东北丧朋。安贞吉。"
KUN_LINES = ["初六：履霜，坚冰至。","六二：直方大，不习无不利。","六三：含章可贞。或从王事，无成有终。",
             "六四：括囊，无咎无誉。","六五：黄裳元吉。","上六：龙战于野，其血玄黄。"]

# ---------------- SWRL 构造 ----------------
VARS = set()
def var(n):
    VARS.add(n)
    return "rules:" + n

def atom_class(cls, a):
    return ('<rdf:Description>\n'
            '                                <rdf:type rdf:resource="http://www.w3.org/2003/11/swrl#ClassAtom"/>\n'
            '                                <swrl:classPredicate rdf:resource="%s"/>\n'
            '                                <swrl:argument1 rdf:resource="%s"/>\n'
            '                            </rdf:Description>' % (cls, a))

def atom_prop(p, a1, a2):
    return ('<rdf:Description>\n'
            '                                <rdf:type rdf:resource="http://www.w3.org/2003/11/swrl#IndividualPropertyAtom"/>\n'
            '                                <swrl:propertyPredicate rdf:resource="%s"/>\n'
            '                                <swrl:argument1 rdf:resource="%s"/>\n'
            '                                <swrl:argument2 rdf:resource="%s"/>\n'
            '                            </rdf:Description>' % (p, a1, a2))

def atom_data(p, a1, a2):
    return ('<rdf:Description>\n'
            '                                <rdf:type rdf:resource="http://www.w3.org/2003/11/swrl#DatavaluedPropertyAtom"/>\n'
            '                                <swrl:propertyPredicate rdf:resource="%s"/>\n'
            '                                <swrl:argument1 rdf:resource="%s"/>\n'
            '                                <swrl:argument2 rdf:resource="%s"/>\n'
            '                            </rdf:Description>' % (p, a1, a2))

def _arg(v, last):
    if isinstance(v, str) and v.startswith("rules:"):
        return ('<rdf:Description>\n'
                '                                            <rdf:type rdf:resource="http://www.w3.org/1999/02/22-rdf-syntax-ns#List"/>\n'
                '                                            <rdf:first rdf:resource="%s"/>\n'
                '                                            <rdf:rest %s/>\n'
                '                                        </rdf:Description>' % (v, 'rdf:resource="http://www.w3.org/1999/02/22-rdf-syntax-ns#nil"' if last else ('>\n'+ '' )))
    else:
        # literal (integer or typed string)
        if isinstance(v, int) or (isinstance(v, str) and v.lstrip('-').isdigit()):
            lit = '<rdf:first rdf:datatype="http://www.w3.org/2001/XMLSchema#integer">%s</rdf:first>' % v
        else:
            lit = '<rdf:first rdf:datatype="http://www.w3.org/2001/XMLSchema#string">%s</rdf:first>' % v
        return ('<rdf:Description>\n'
                '                                            <rdf:type rdf:resource="http://www.w3.org/1999/02/22-rdf-syntax-ns#List"/>\n'
                '                                            %s\n'
                '                                            <rdf:rest rdf:resource="http://www.w3.org/1999/02/22-rdf-syntax-ns#nil"/>\n'
                '                                        </rdf:Description>' % lit)

def atom_builtin(fn, args):
    # build nested list of args
    def build(i):
        v = args[i]
        last = (i == len(args) - 1)
        if isinstance(v, str) and (v.startswith("rules:") or v.startswith("res:")):
            first = '<rdf:first rdf:resource="%s"/>' % (v[4:] if v.startswith("res:") else v)
        elif isinstance(v, int) or (isinstance(v, str) and v.lstrip('-').isdigit()):
            first = '<rdf:first rdf:datatype="http://www.w3.org/2001/XMLSchema#integer">%s</rdf:first>' % v
        else:
            first = '<rdf:first rdf:datatype="http://www.w3.org/2001/XMLSchema#string">%s</rdf:first>' % v
        rest = '<rdf:rest rdf:resource="http://www.w3.org/1999/02/22-rdf-syntax-ns#nil"/>' if last else \
               ('<rdf:rest>\n' + build(i+1) + '\n</rdf:rest>')
        return ('<rdf:Description>\n'
                '                                        <rdf:type rdf:resource="http://www.w3.org/1999/02/22-rdf-syntax-ns#List"/>\n'
                '                                        %s\n'
                '                                        %s\n'
                '                                    </rdf:Description>' % (first, rest))
    arglist = build(0)
    return ('<rdf:Description>\n'
            '                                <rdf:type rdf:resource="http://www.w3.org/2003/11/swrl#BuiltinAtom"/>\n'
            '                                <swrl:builtin rdf:resource="http://www.w3.org/2003/11/swrlb#%s"/>\n'
            '                                <swrl:arguments>\n'
            '                                    %s\n'
            '                                </swrl:arguments>\n'
            '                            </rdf:Description>' % (fn, arglist))

def swrl_atomlist(atoms, outer_indent):
    # build rdf:List of atoms
    def build(i):
        a = atoms[i]
        last = (i == len(atoms) - 1)
        rest = ('<rdf:rest rdf:resource="http://www.w3.org/1999/02/22-rdf-syntax-ns#nil"/>' if last
                else '<rdf:rest>\n' + build(i+1) + '\n</rdf:rest>')
        return ('<rdf:Description>\n'
                '            <rdf:type rdf:resource="http://www.w3.org/2003/11/swrl#AtomList"/>\n'
                '            <rdf:first>\n'
                '                %s\n'
                '            </rdf:first>\n'
                '            %s\n'
                '        </rdf:Description>' % (a, rest))
    return build(0)

def rule(name, body_atoms, head_atoms, comment):
    w("    <!-- %s -->" % comment)
    w("    <rdf:Description>")
    w('        <rdf:type rdf:resource="http://www.w3.org/2003/11/swrl#Imp"/>')
    w("        <swrl:body>")
    w("        " + swrl_atomlist(body_atoms, 4))
    w("        </swrl:body>")
    w("        <swrl:head>")
    w("        " + swrl_atomlist(head_atoms, 4))
    w("        </swrl:head>")
    w("    </rdf:Description>")
    w("")

# ---------------- 文档头 ----------------
w('<?xml version="1.0" encoding="UTF-8"?>')
w('<rdf:RDF xmlns="http://example.org/plumblossom/all"')
w('         xml:base="http://example.org/plumblossom/all"')
w('         xmlns:owl="http://www.w3.org/2002/07/owl#"')
w('         xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#"')
w('         xmlns:rdfs="http://www.w3.org/2000/01/rdf-schema#"')
w('         xmlns:xsd="http://www.w3.org/2001/XMLSchema#"')
w('         xmlns:swrl="http://www.w3.org/2003/11/swrl#"')
w('         xmlns:swrlb="http://www.w3.org/2003/11/swrlb#"')
w('         xmlns:core="http://example.org/plumblossom/core/"')
w('         xmlns:div="http://example.org/plumblossom/divination/"')
w('         xmlns:yijing="http://example.org/plumblossom/yijing/"')
w('         xmlns:rel="http://example.org/plumblossom/relations/"')
w('         xmlns:sym="http://example.org/plumblossom/symbols/"')
w('         xmlns:ome="http://example.org/plumblossom/omens/"')
w('         xmlns:jud="http://example.org/plumblossom/judgement/"')
w('         xmlns:cal="http://example.org/plumblossom/calendars/"')
w('         xmlns:rules="http://example.org/plumblossom/rules#">')
w('')
w('    <owl:Ontology rdf:about="http://example.org/plumblossom/all">')
w('        <rdfs:label xml:lang="zh">梅花易数本体总入口（单文件：TBox + 典型 ABox）</rdfs:label>')
w('        <rdfs:comment xml:lang="zh">第 6 步产出。依据《PlumBlossom应用-本体顶层设计》v3 与《PlumBlossom应用-本体核心设计》v3.1。命名空间：core/div/yijing/rel/sym/ome/jud/cal。TBox 全量，ABox 为代表性典型个体（六十四卦全名录、卦辞爻辞全集留待第 12 步扩全集）。</rdfs:comment>')
w('    </owl:Ontology>')
w('')

# ---------------- 类 ----------------
def clazz(iri, label, supers, comment=None):
    w('    <owl:Class rdf:about="%s">' % iri)
    if label:
        w('        <rdfs:label xml:lang="zh">%s</rdfs:label>' % label)
    if comment:
        w('        <rdfs:comment xml:lang="zh">%s</rdfs:comment>' % comment)
    for s in supers:
        w('        <rdfs:subClassOf rdf:resource="%s"/>' % s)
    w('    </owl:Class>')
    w('')

w('    <!-- ==================== 类层级 ==================== -->')
clazz("core:PlumBlossomEntity", "本体事物根", [], "梅花易数本体根类")
clazz("div:DivinationInput", "起卦域", ["core:PlumBlossomEntity"], None)
clazz("div:DivinationMethod", "起卦法类别", ["div:DivinationInput"], "共 17 子类（卷之一十六类 + 附录一撮金）")
clazz("div:NumberBasedMethod", "先天·以数起卦", ["div:DivinationMethod"], None)
clazz("div:ImageBasedMethod", "后天·端法·以象起卦", ["div:DivinationMethod"], None)
for en, lab in [("YearMonthDayHourMethod","年月日时起例"),("ObjectCountMethod","物占数"),
                ("SoundCountMethod","声音占"),("CharacterMethod","字占"),("ZhangChiMethod","丈尺占"),
                ("ChiCunMethod","尺寸物占"),("YicuoJinMethod","一撮金法")]:
    clazz("div:"+en, lab, ["div:NumberBasedMethod"], None)
for en, lab in [("PersonObserveMethod","为人占"),("SelfObserveMethod","自己占"),("MovingThingMethod","占动物"),
                ("StillThingMethod","占静物"),("ThingHexagramMethod","物卦起例"),("WindMethod","风觉占"),
                ("BirdMethod","鸟占"),("HeardSoundMethod","听声音占"),("ShapeMethod","形物占"),("ColorMethod","验色占")]:
    clazz("div:"+en, lab, ["div:ImageBasedMethod"], None)
clazz("div:Precept", "心法戒律·四不占", ["div:DivinationInput"], "四不占戒律（卷之一·观梅占；附录·一撮金）")
clazz("div:InputDatum", "起卦输入数据", ["div:DivinationInput"], None)
clazz("div:DivinationSubject", "占断事项域", ["core:PlumBlossomEntity"], None)
clazz("jud:SubjectCategory", "事项类别", ["div:DivinationSubject"], "18 子类（卷之二十八章占例）")
clazz("core:HexagramDomain", "卦象域", ["core:PlumBlossomEntity"], None)
clazz("core:Trigram", "八卦", ["core:HexagramDomain"], None)
clazz("core:Direction", "八方", ["core:HexagramDomain"], None)
clazz("core:Line", "爻", ["core:HexagramDomain"], None)
clazz("core:YinLine", "阴爻", ["core:Line"], None)
clazz("core:YangLine", "阳爻", ["core:Line"], None)
clazz("core:MovingLine", "动爻", ["core:Line"], "一卦一爻动")
clazz("core:Hexagram", "重卦", ["core:HexagramDomain"], "由上下卦组成")
clazz("core:OriginalHexagram", "本卦", ["core:Hexagram"], None)
clazz("core:MutualHexagram", "互卦", ["core:Hexagram"], None)
clazz("core:ChangedHexagram", "变卦", ["core:Hexagram"], None)
clazz("core:SixBitHexagram", "六十四卦名录", ["core:Hexagram"], "术语型")
clazz("yijing:YijingDomain", "周易经传域", ["core:PlumBlossomEntity"], None)
clazz("yijing:HexagramDictum", "周易卦辞", ["yijing:YijingDomain"], "六十四卦各一")
clazz("yijing:LineDictum", "周易爻辞", ["yijing:YijingDomain"], "三百八十四爻各一")
clazz("yijing:HexagramSequence", "卦序", ["yijing:YijingDomain"], "六十四卦次第 1~64")
clazz("yijing:OppositeNature", "诸卦反对性情", ["yijing:YijingDomain"], "杂卦两两反对之卦义")
clazz("rel:YiliDomain", "易理域", ["core:PlumBlossomEntity"], None)
clazz("rel:FivePhase", "五行", ["rel:YiliDomain"], None)
clazz("rel:ShengKeRelation", "五行生克关系", ["rel:YiliDomain"], None)
clazz("rel:Sheng", "生", ["rel:ShengKeRelation"], None)
clazz("rel:Ke", "克", ["rel:ShengKeRelation"], None)
clazz("rel:BodyUseRelation", "体用关系", ["rel:YiliDomain"], None)
clazz("rel:VitalityState", "卦气旺衰", ["rel:YiliDomain"], None)
clazz("rel:TrigramNature", "八卦性情", ["rel:YiliDomain"], None)
clazz("rel:TrigramOrder", "八卦定阴阳次序", ["rel:YiliDomain"], None)
clazz("sym:ImageSymbol", "万物类象", ["rel:YiliDomain"], None)
clazz("sym:SymbolDimension", "类象维度", ["rel:YiliDomain"], "28 分栏维度")
clazz("sym:ModernEntity", "现代事物（映射扩展层）", ["rel:YiliDomain"], "默认关闭")
clazz("ome:OmenDomain", "外应域", ["core:PlumBlossomEntity"], None)
clazz("ome:ThreeEssentials", "三要", ["ome:OmenDomain"], "耳/目/心")
clazz("ome:TenResponses", "十应", ["ome:OmenDomain"], None)
clazz("cal:CalendarDomain", "历法域", ["core:PlumBlossomEntity"], None)
clazz("cal:HeavenlyStem", "十天干", ["cal:CalendarDomain"], None)
clazz("cal:EarthlyBranch", "十二地支", ["cal:CalendarDomain"], None)
clazz("cal:SexagenaryCycle", "六十甲子", ["cal:CalendarDomain"], None)
clazz("cal:NaYin", "纳音", ["cal:CalendarDomain"], None)
clazz("cal:LifeStage", "五行长生", ["cal:CalendarDomain"], None)
clazz("cal:CalendarDatum", "历法数据", ["cal:CalendarDomain"], None)
clazz("jud:JudgementDomain", "断验域", ["core:PlumBlossomEntity"], None)
clazz("jud:Auspiciousness", "吉凶等级", ["jud:JudgementDomain"], "大吉/吉/平/凶/大凶")
clazz("jud:YicuoJinAdmonition", "一撮金爻诀断辞", ["jud:JudgementDomain"], "附录·一撮金")
clazz("jud:YicuoJinGrade", "一撮金吉凶六等", ["jud:JudgementDomain"], "上上…下下")
clazz("jud:ResponsePeriod", "应期", ["jud:JudgementDomain"], None)
clazz("jud:SelfCultivationAdvice", "修身建议", ["jud:JudgementDomain"], None)

# ---------------- 对象属性 ----------------
def oprop(iri, label, dom, rng, inverse=None, functional=False, symmetric=False, transitive=False,
          inverse_of=None):
    w('    <owl:ObjectProperty rdf:about="%s">' % iri)
    if label:
        w('        <rdfs:label xml:lang="zh">%s</rdfs:label>' % label)
    if functional:
        w('        <rdf:type rdf:resource="http://www.w3.org/2002/07/owl#FunctionalProperty"/>')
    if symmetric:
        w('        <rdf:type rdf:resource="http://www.w3.org/2002/07/owl#SymmetricProperty"/>')
    if transitive:
        w('        <rdf:type rdf:resource="http://www.w3.org/2002/07/owl#TransitiveProperty"/>')
    if inverse_of:
        w('        <owl:inverseOf rdf:resource="%s"/>' % inverse_of)
    if dom:
        w('        <rdfs:domain rdf:resource="%s"/>' % dom)
    if rng:
        w('        <rdfs:range rdf:resource="%s"/>' % rng)
    w('    </owl:ObjectProperty>')
    w('')

def iprop(iri, label, dom, rng, functional=False):
    w('    <owl:ObjectProperty rdf:about="%s">' % iri)
    if label:
        w('        <rdfs:label xml:lang="zh">%s</rdfs:label>' % label)
    if functional:
        w('        <rdf:type rdf:resource="http://www.w3.org/2002/07/owl#FunctionalProperty"/>')
    w('        <owl:inverseOf rdf:resource="%s"/>' % iri.replace("/", "/is").replace("#", "#is"))
    if dom:
        w('        <rdfs:domain rdf:resource="%s"/>' % rng)
    if rng:
        w('        <rdfs:range rdf:resource="%s"/>' % dom)
    w('    </owl:ObjectProperty>')
    w('')

w('    <!-- ==================== 对象属性 ==================== -->')
for iri, lab, dom, rng, inv, fn in [
    ("core:hasUpperTrigram","有上卦","core:Hexagram","core:Trigram","core:isUpperTrigramOf",True),
    ("core:hasLowerTrigram","有下卦","core:Hexagram","core:Trigram","core:isLowerTrigramOf",True),
    ("core:hasLine","有爻","core:Hexagram","core:Line","core:isLineOf",False),
    ("core:hasMovingLine","有动爻","core:Hexagram","core:MovingLine","core:isMovingLineOf",True),
    ("core:mutualOf","为…之互卦","core:MutualHexagram","core:OriginalHexagram","core:hasMutualHexagram",True),
    ("core:changedFrom","由…变来","core:ChangedHexagram","core:OriginalHexagram","core:hasChangedHexagram",True),
    ("core:hasDirection","有方位","core:Trigram","core:Direction","core:isDirectionOf",True),
    ("core:hasTrigramNature","有性情","core:Trigram","rel:TrigramNature","core:isTrigramNatureOf",True),
    ("core:hasTrigramOrder","有阴阳次序","core:Trigram","rel:TrigramOrder","core:isTrigramOrderOf",True),
    ("yijing:hasHexagramDictum","有卦辞","core:SixBitHexagram","yijing:HexagramDictum","yijing:isHexagramDictumOf",True),
    ("yijing:hasLineDictum","有爻辞","core:SixBitHexagram","yijing:LineDictum","yijing:isLineDictumOf",False),
    ("rel:hasBodyTrigram","有体卦","rel:BodyUseRelation","core:Trigram","rel:isBodyTrigramOf",True),
    ("rel:hasUseTrigram","有用卦","rel:BodyUseRelation","core:Trigram","rel:isUseTrigramOf",True),
    ("rel:hasFivePhase","有五行","core:Trigram","rel:FivePhase","rel:isFivePhaseOf",True),
    ("rel:generates","生","rel:FivePhase","rel:FivePhase","rel:generatedBy",False),
    ("rel:overcomes","克","rel:FivePhase","rel:FivePhase","rel:overcomeBy",False),
    ("rel:hasVitalityState","有旺衰","core:Trigram","rel:VitalityState","rel:isVitalityStateOf",True),
    ("cal:hasStem","有天干","cal:CalendarDatum","cal:HeavenlyStem","cal:isStemOf",True),
    ("cal:hasBranch","有地支","cal:CalendarDatum","cal:EarthlyBranch","cal:isBranchOf",True),
    ("cal:stemFivePhase","天干五行","cal:HeavenlyStem","rel:FivePhase",None,True),
    ("cal:branchFivePhase","地支五行","cal:EarthlyBranch","rel:FivePhase",None,True),
    ("cal:hasNaYin","有纳音","cal:SexagenaryCycle","cal:NaYin","cal:isNaYinOf",True),
    ("cal:naYinPhase","纳音五行","cal:NaYin","rel:FivePhase",None,True),
    ("cal:datumFivePhase","历法数据五行","cal:CalendarDatum","rel:FivePhase",None,True),
    ("cal:hasLifeStage","有长生","rel:FivePhase","cal:LifeStage",None,True),
    ("sym:hasImageSymbol","有类象","core:Trigram","sym:ImageSymbol","sym:isImageOf",False),
    ("sym:belongsToDimension","属维度","sym:ImageSymbol","sym:SymbolDimension",None,False),
    ("sym:modernMapping","现代映射","sym:ImageSymbol","sym:ModernEntity",None,False),
    ("ome:hasThreeEssential","有三要","ome:OmenDomain","ome:ThreeEssentials",None,False),
    ("ome:hasTenResponse","有十应","ome:OmenDomain","ome:TenResponses",None,False),
    ("jud:belongsToCategory","属事项类别","div:DivinationInput","jud:SubjectCategory",None,False),
    ("jud:hasMethod","所据起卦法","jud:JudgementDomain","div:DivinationMethod",None,True),
    ("jud:hasAuspiciousness","有吉凶等级","jud:JudgementDomain","jud:Auspiciousness","jud:isAuspiciousnessOf",True),
    ("jud:hasYicuoJinAdmonition","有一撮金爻诀","jud:JudgementDomain","jud:YicuoJinAdmonition","jud:admonitionOfHexagram",False),
    ("jud:hasYicuoJinGrade","有爻诀等级","jud:YicuoJinAdmonition","jud:YicuoJinGrade",None,True),
    ("jud:hasResponsePeriod","有应期","jud:JudgementDomain","jud:ResponsePeriod","jud:isResponsePeriodOf",True),
    ("jud:hasAdvice","有修身建议","jud:JudgementDomain","jud:SelfCultivationAdvice",None,False),
]:
    oprop(iri, lab, dom, rng, functional=fn)
    # 逆属性
    if inv:
        oprop(inv, None, rng, dom, functional=False)

# 特殊特性补充：generates/overcomes 传递；oppositeOf 对称
w('    <owl:ObjectProperty rdf:about="yijing:oppositeOf">')
w('        <rdfs:label xml:lang="zh">反对（杂卦两两相对）</rdfs:label>')
w('        <rdf:type rdf:resource="http://www.w3.org/2002/07/owl#SymmetricProperty"/>')
w('        <rdfs:domain rdf:resource="core:SixBitHexagram"/>')
w('        <rdfs:range rdf:resource="core:SixBitHexagram"/>')
w('    </owl:ObjectProperty>')
w('')
# 传递特性：单独声明（因上面 oprop 未置 transitive）
for p in ["rel:generates", "rel:overcomes"]:
    w('    <owl:ObjectProperty rdf:about="%s">' % p)
    w('        <rdf:type rdf:resource="http://www.w3.org/2002/07/owl#TransitiveProperty"/>')
    w('    </owl:ObjectProperty>')
    w('')

# ---------------- 数据属性 ----------------
def dprop(iri, label, dom, typ):
    w('    <owl:DatatypeProperty rdf:about="%s">' % iri)
    if label:
        w('        <rdfs:label xml:lang="zh">%s</rdfs:label>' % label)
    w('        <rdfs:domain rdf:resource="%s"/>' % dom)
    w('        <rdfs:range rdf:resource="%s"/>' % typ)
    w('    </owl:DatatypeProperty>')
    w('')

w('    <!-- ==================== 数据属性 ==================== -->')
for iri, lab, dom, typ in [
    ("core:trigramNumber","先天卦数","core:Trigram","xsd:integer"),
    ("core:linePosition","爻位","core:Line","xsd:integer"),
    ("core:hexagramName","卦名","core:Hexagram","xsd:string"),
    ("core:yinYang","阴/阳","core:Line","xsd:string"),
    ("core:trigramSymbol","卦象符（取象）","core:Trigram","xsd:string"),
    ("core:trigramYinYang","八卦阴阳","core:Trigram","xsd:string"),
    ("core:trigramFamilyRole","八卦长幼男女","core:Trigram","xsd:string"),
    ("yijing:hexagramSequence","卦序","yijing:HexagramSequence","xsd:integer"),
    ("yijing:hexagramDictumText","卦辞文本","yijing:HexagramDictum","xsd:string"),
    ("yijing:lineDictumText","爻辞文本","yijing:LineDictum","xsd:string"),
    ("yijing:lineDictumPosition","爻辞爻位","yijing:LineDictum","xsd:integer"),
    ("yijing:oppositeNatureText","反对性情文本","core:SixBitHexagram","xsd:string"),
    ("div:methodCode","起卦法代码","div:DivinationMethod","xsd:string"),
    ("div:preceptName","戒律名","div:Precept","xsd:string"),
    ("div:strokeCount","笔画数","div:InputDatum","xsd:integer"),
    ("div:rawNumber","起卦原始数","div:InputDatum","xsd:integer"),
    ("rel:shengKeType","生克类型","rel:BodyUseRelation","xsd:integer"),
    ("rel:vitality","旺相休囚死","rel:VitalityState","xsd:string"),
    ("rel:natureText","性情文本","rel:TrigramNature","xsd:string"),
    ("sym:symbolText","类象文本","sym:ImageSymbol","xsd:string"),
    ("ome:omenText","外应文本","ome:TenResponses","xsd:string"),
    ("jud:divinationSystem","先天/后天体系","jud:JudgementDomain","xsd:string"),
    ("jud:usesZhouyiDictum","是否引周易卦爻辞","jud:JudgementDomain","xsd:boolean"),
    ("jud:auspiciousnessLevel","吉凶等级值","jud:Auspiciousness","xsd:integer"),
    ("jud:yicuoJinGradeValue","一撮金六等值","jud:YicuoJinGrade","xsd:integer"),
    ("jud:processDescription","过程描述","jud:JudgementDomain","xsd:string"),
    ("jud:processFlag","过程标记","jud:JudgementDomain","xsd:string"),
    ("jud:isMajorResult","重大结果标记","jud:JudgementDomain","xsd:boolean"),
    ("jud:responseValue","应期数值","jud:ResponsePeriod","xsd:decimal"),
    ("jud:responseUnit","应期单位","jud:ResponsePeriod","xsd:string"),
    ("cal:ganzhiYear","干支纪年","cal:CalendarDatum","xsd:string"),
    ("cal:ganzhiMonth","干支纪月","cal:CalendarDatum","xsd:string"),
    ("cal:ganzhiDay","干支纪日","cal:CalendarDatum","xsd:string"),
    ("cal:ganzhiHour","干支纪时","cal:CalendarDatum","xsd:string"),
    ("cal:season","季节","cal:CalendarDatum","xsd:string"),
    ("cal:stemName","天干名","cal:HeavenlyStem","xsd:string"),
    ("cal:branchName","地支名","cal:EarthlyBranch","xsd:string"),
    ("cal:branchAnimal","生肖","cal:EarthlyBranch","xsd:string"),
    ("cal:zhiOrder","地支序","cal:EarthlyBranch","xsd:integer"),
    ("cal:naYinName","纳音名","cal:NaYin","xsd:string"),
    ("cal:lifeStageText","长生文本","cal:LifeStage","xsd:string"),
]:
    dprop(iri, lab, dom, typ)

# ---------------- 约束 ----------------
def cardinality(cls, prop, n, onclass=None):
    w('    <owl:Class rdf:about="%s">' % cls)
    w('        <rdfs:subClassOf>')
    w('            <owl:Restriction>')
    w('                <owl:onProperty rdf:resource="%s"/>' % prop)
    if onclass:
        w('                <owl:qualifiedCardinality rdf:datatype="xsd:nonNegativeInteger">%d</owl:qualifiedCardinality>' % n)
        w('                <owl:onClass rdf:resource="%s"/>' % onclass)
    else:
        w('                <owl:cardinality rdf:datatype="xsd:nonNegativeInteger">%d</owl:cardinality>' % n)
    w('            </owl:Restriction>')
    w('        </rdfs:subClassOf>')
    w('    </owl:Class>')
    w('')

w('    <!-- ==================== 约束（owl:Restriction） ==================== -->')
cardinality("core:Hexagram","core:hasUpperTrigram",1,"core:Trigram")
cardinality("core:Hexagram","core:hasLowerTrigram",1,"core:Trigram")
cardinality("core:Hexagram","core:hasLine",6,"core:Line")
cardinality("core:OriginalHexagram","core:hasMovingLine",1,"core:MovingLine")
cardinality("core:MutualHexagram","core:mutualOf",1,"core:OriginalHexagram")
cardinality("core:ChangedHexagram","core:changedFrom",1,"core:OriginalHexagram")
cardinality("core:Trigram","core:hasDirection",1,"core:Direction")
cardinality("core:Trigram","core:hasTrigramNature",1,"rel:TrigramNature")
cardinality("core:Trigram","core:hasTrigramOrder",1,"rel:TrigramOrder")
cardinality("core:SixBitHexagram","yijing:hasHexagramDictum",1,"yijing:HexagramDictum")
cardinality("core:SixBitHexagram","yijing:hasLineDictum",6,"yijing:LineDictum")
cardinality("yijing:LineDictum","yijing:lineDictumPosition",1,None)
cardinality("cal:CalendarDatum","cal:hasStem",1,"cal:HeavenlyStem")
cardinality("cal:CalendarDatum","cal:hasBranch",1,"cal:EarthlyBranch")
cardinality("rel:BodyUseRelation","rel:hasBodyTrigram",1,"core:Trigram")
cardinality("rel:BodyUseRelation","rel:hasUseTrigram",1,"core:Trigram")
cardinality("core:Trigram","rel:hasFivePhase",1,"rel:FivePhase")
cardinality("jud:JudgementDomain","jud:hasAuspiciousness",1,"jud:Auspiciousness")
cardinality("jud:YicuoJinAdmonition","jud:hasYicuoJinGrade",1,"jud:YicuoJinGrade")

# ---------------- 典型 ABox ----------------
def ind(iri, types, props, label=None, comment=None):
    w('    <owl:NamedIndividual rdf:about="%s">' % iri)
    for t in types:
        w('        <rdf:type rdf:resource="%s"/>' % t)
    if label:
        w('        <rdfs:label xml:lang="zh">%s</rdfs:label>' % label)
    if comment:
        w('        <rdfs:comment xml:lang="zh">%s</rdfs:comment>' % comment)
    for p, v, kind in props:
        if kind == "iri":
            w('        <%s rdf:resource="%s"/>' % (p, v))
        elif kind == "int":
            w('        <%s rdf:datatype="xsd:integer">%s</%s>' % (p, v, p))
        elif kind == "dec":
            w('        <%s rdf:datatype="xsd:decimal">%s</%s>' % (p, v, p))
        elif kind == "bool":
            w('        <%s rdf:datatype="xsd:boolean">%s</%s>' % (p, v, p))
        else:
            w('        <%s xml:lang="zh">%s</%s>' % (p, v, p))
    w('    </owl:NamedIndividual>')
    w('')

w('    <!-- ==================== 典型 ABox ==================== -->')
# 八卦
for en, lab, num, fp, de, dl, nat, natlab, role, yy, sym in TRIGRAMS:
    ind("core:%sTrigram" % en, ["core:Trigram"], [
        ("core:trigramNumber", num, "int"),
        ("rel:hasFivePhase", "rel:%s" % fp, "iri"),
        ("core:hasDirection", "core:Dir_%s" % de, "iri"),
        ("core:hasTrigramNature", "rel:Nature_%s" % en, "iri"),
        ("core:hasTrigramOrder", "rel:Order_%s" % en, "iri"),
        ("core:trigramSymbol", sym, "str"),
        ("core:trigramYinYang", yy, "str"),
        ("core:trigramFamilyRole", role, "str"),
    ], lab, "%s（先天数%d）" % (sym, num))
# 方位
for en, lab in DIRECTIONS:
    ind("core:Dir_%s" % en, ["core:Direction"], [], lab, "%s（八方）" % lab)
# 五行
for en, lab in FIVEPHASES:
    ind("rel:%s" % en, ["rel:FivePhase"], [("rdfs:comment", lab, "str")], lab)
# 生克
for src, dst in [("Jin","Shui"),("Shui","Mu"),("Mu","Huo"),("Huo","Tu"),("Tu","Jin")]:
    ind("rel:Sheng_%s%s" % (src, dst), ["rel:Sheng"], [("rel:generates", "rel:%s" % dst, "iri")], "%s生%s" % (src, dst))
for src, dst in [("Jin","Mu"),("Mu","Tu"),("Tu","Shui"),("Shui","Huo"),("Huo","Jin")]:
    ind("rel:Ke_%s%s" % (src, dst), ["rel:Ke"], [("rel:overcomes", "rel:%s" % dst, "iri")], "%s克%s" % (src, dst))
# 旺衰
for en, lab in VITALITY:
    ind("rel:Vitality_%s" % en, ["rel:VitalityState"], [("rel:vitality", lab, "str")], lab)
# 八卦性情
for en, lab, num, fp, de, dl, nat, natlab, role, yy, sym in TRIGRAMS:
    ind("rel:Nature_%s" % en, ["rel:TrigramNature"], [("rel:natureText", natlab, "str")], natlab)
# 八卦阴阳次序
for en, lab, num, fp, de, dl, nat, natlab, role, yy, sym in TRIGRAMS:
    ind("rel:Order_%s" % en, ["rel:TrigramOrder"], [("core:trigramYinYang", yy, "str"), ("core:trigramFamilyRole", role, "str")], "%s·%s" % (role, yy))
# 吉凶等级
for en, lab, lvl in AUSP:
    ind("jud:%s" % en, ["jud:Auspiciousness"], [("jud:auspiciousnessLevel", lvl, "int")], lab)
# 一撮金六等
for en, lab, v in GRADES:
    ind("jud:Grade_%s" % en, ["jud:YicuoJinGrade"], [("jud:yicuoJinGradeValue", v, "int")], lab)
# 十应
for en, lab in TENRESP:
    ind("ome:Response_%s" % en, ["ome:TenResponses"], [("ome:omenText", lab, "str")], lab)
# 三要
for en, lab in ESSENTIALS:
    ind("ome:Essential_%s" % en, ["ome:ThreeEssentials"], [("rdfs:comment", lab, "str")], lab)
# 四不占
for en, lab in PRECEPTS:
    ind("div:Precept_%s" % en, ["div:Precept"], [("div:preceptName", lab, "str")], lab)
# 事项类别
for en, lab in CATEGORIES:
    ind("jud:Cat_%s" % en, ["jud:SubjectCategory"], [("rdfs:comment", lab, "str")], lab)
# 天干
for en, lab, fp in STEMS:
    ind("cal:Stem_%s" % en, ["cal:HeavenlyStem"], [("cal:stemName", lab, "str"), ("cal:stemFivePhase", "rel:%s" % fp, "iri")], lab)
# 地支
for en, lab, animal, order, fp in BRANCHES:
    ind("cal:Branch_%s" % en, ["cal:EarthlyBranch"], [
        ("cal:branchName", lab, "str"), ("cal:branchAnimal", animal, "str"),
        ("cal:zhiOrder", order, "int"), ("cal:branchFivePhase", "rel:%s" % fp, "iri")], lab)
# 长生
for en, lab in LIFESTAGE:
    ind("cal:Life_%s" % en, ["cal:LifeStage"], [("cal:lifeStageText", lab, "str")], lab)
# 纳音（代表性）
for en, lab, fp in NAYIN:
    ind("cal:NaYin_%s" % en, ["cal:NaYin"], [("cal:naYinName", lab, "str"), ("cal:naYinPhase", "rel:%s" % fp, "iri")], lab)
# 六十甲子（代表性）
for en, lab, nayin in CYCLES:
    ind("cal:Cycle_%s" % en, ["cal:SexagenaryCycle"], [("cal:hasNaYin", "cal:NaYin_%s" % nayin, "iri"), ("rdfs:comment", lab, "str")], lab)
# 类象维度
for en, lab in SYMDIMS:
    ind("sym:Dim_%s" % en, ["sym:SymbolDimension"], [("rdfs:comment", lab, "str")], lab)
# 类象（每卦一代表象）
for en, lab, num, fp, de, dl, nat, natlab, role, yy, sym in TRIGRAMS:
    ind("sym:Symbol_%s" % en, ["sym:ImageSymbol"], [
        ("sym:symbolText", sym, "str"), ("sym:belongsToDimension", "sym:Dim_DiLi", "iri")],
        sym)
# 六十四卦卦序
for i, name in enumerate(HEXNAMES, start=1):
    ind("yijing:Seq_%02d" % i, ["yijing:HexagramSequence"], [("yijing:hexagramSequence", i, "int"), ("rdfs:label", name, "str")], "%d.%s" % (i, name))
# 乾/坤 八卦爻 + 爻辞 + 卦辞（代表性）
def hexagram_individuals(en, lab, upper, lower, lines, dictum, line_texts, seqno):
    # 六爻
    for i, txt in enumerate(lines, start=1):
        yy = "阳" if "九" in txt[:2] or "初九" in txt else "阴"
        yy = "阳" if ("九" in txt.split("：")[0]) else "阴"
        typ = "core:YangLine" if yy == "阳" else "core:YinLine"
        ind("core:%sLine%d" % (en, i), [typ], [("core:linePosition", i, "int"), ("core:yinYang", yy, "str")], "%s第%d爻" % (lab, i))
    # 爻辞
    for i, txt in enumerate(line_texts, start=1):
        ind("yijing:LineDictum_%s_%d" % (en, i), ["yijing:LineDictum"], [("yijing:lineDictumText", txt, "str"), ("yijing:lineDictumPosition", i, "int")], "%s爻辞%d" % (lab, i))
    # 卦辞
    ind("yijing:Dictum_%s" % en, ["yijing:HexagramDictum"], [("yijing:hexagramDictumText", dictum, "str")], "%s卦辞" % lab)
    # 六爻个体（六十四卦名录）
    props = [("core:hexagramName", lab, "str"),
             ("core:hasUpperTrigram", "core:%sTrigram" % upper, "iri"),
             ("core:hasLowerTrigram", "core:%sTrigram" % lower, "iri"),
             ("yijing:hasHexagramDictum", "yijing:Dictum_%s" % en, "iri")]
    for i in range(1, 7):
        props.append(("core:hasLine", "core:%sLine%d" % (en, i), "iri"))
        props.append(("yijing:hasLineDictum", "yijing:LineDictum_%s_%d" % (en, i), "iri"))
    ind("core:%sHexagram" % en, ["core:SixBitHexagram"], props, lab, "六十四卦名录（代表性）")

hexagram_individuals("Qian", "乾为天", "Qian", "Qian", QIAN_LINES, QIAN_DICTUM, QIAN_LINES, 1)
hexagram_individuals("Kun", "坤为地", "Kun", "Kun", KUN_LINES, KUN_DICTUM, KUN_LINES, 2)
# 反对性情（乾刚坤柔）
ind("yijing:Opposite_QianKun", ["yijing:OppositeNature"], [("yijing:oppositeNatureText", "乾刚坤柔", "str")], "乾刚坤柔")
for en, other in [("Qian", "Kun"), ("Kun", "Qian")]:
    w('    <owl:NamedIndividual rdf:about="core:%sHexagram">' % en)
    w('        <yijing:oppositeOf rdf:resource="core:%sHexagram"/>' % other)
    w('        <yijing:oppositeNatureText xml:lang="zh">乾刚坤柔</yijing:oppositeNatureText>')
    w('    </owl:NamedIndividual>')
    w('')

# ---------------- SWRL 变量声明 ----------------
# 先收集变量（通过构造规则时调用 var）
rules = []
def R(name, body, head, comment):
    rules.append((name, body, head, comment))

# R1 体用判定（动爻在下卦→下卦为用、上卦为体）
R("R1", [
    atom_class("core:OriginalHexagram", var("h")),
    atom_prop("core:hasMovingLine", var("h"), var("m")),
    atom_data("core:linePosition", var("m"), var("p")),
    atom_builtin("lessThanOrEqual", [var("p"), 3]),
    atom_prop("core:hasLowerTrigram", var("h"), var("use")),
    atom_prop("core:hasUpperTrigram", var("h"), var("body")),
], [
    atom_prop("rel:hasUseTrigram", var("h"), var("use")),
    atom_prop("rel:hasBodyTrigram", var("h"), var("body")),
], "R1 体用判定：动爻在 1/2/3 → 下卦为用、上卦为体")
# R2
R("R2", [
    atom_class("core:OriginalHexagram", var("h2")),
    atom_prop("core:hasMovingLine", var("h2"), var("m2")),
    atom_data("core:linePosition", var("m2"), var("p2")),
    atom_builtin("greaterThan", [var("p2"), 3]),
    atom_prop("core:hasUpperTrigram", var("h2"), var("use2")),
    atom_prop("core:hasLowerTrigram", var("h2"), var("body2")),
], [
    atom_prop("rel:hasUseTrigram", var("h2"), var("use2")),
    atom_prop("rel:hasBodyTrigram", var("h2"), var("body2")),
], "R2 体用判定：动爻在 4/5/6 → 上卦为用、下卦为体")
# R3~R7 体用生克五级
R("R3", [
    atom_class("rel:BodyUseRelation", var("r")),
    atom_prop("rel:hasUseTrigram", var("r"), var("u")),
    atom_prop("rel:hasBodyTrigram", var("r"), var("b")),
    atom_prop("rel:hasFivePhase", var("u"), var("pu")),
    atom_prop("rel:hasFivePhase", var("b"), var("pb")),
    atom_prop("rel:generates", var("pu"), var("pb")),
], [atom_data("rel:shengKeType", var("r"), 1)], "R3 用生体=最吉（进益之喜）")
R("R4", [
    atom_class("rel:BodyUseRelation", var("r4")),
    atom_prop("rel:hasUseTrigram", var("r4"), var("u4")),
    atom_prop("rel:hasBodyTrigram", var("r4"), var("b4")),
    atom_prop("rel:hasFivePhase", var("u4"), var("pu4")),
    atom_prop("rel:hasFivePhase", var("b4"), var("pu4")),
], [atom_data("rel:shengKeType", var("r4"), 2)], "R4 体用比和=次吉（百事顺遂）")
R("R5", [
    atom_class("rel:BodyUseRelation", var("r5")),
    atom_prop("rel:hasUseTrigram", var("r5"), var("u5")),
    atom_prop("rel:hasBodyTrigram", var("r5"), var("b5")),
    atom_prop("rel:hasFivePhase", var("u5"), var("pu5")),
    atom_prop("rel:hasFivePhase", var("b5"), var("pb5")),
    atom_prop("rel:overcomes", var("pb5"), var("pu5")),
], [atom_data("rel:shengKeType", var("r5"), 3)], "R5 体克用=小吉（诸事吉）")
R("R6", [
    atom_class("rel:BodyUseRelation", var("r6")),
    atom_prop("rel:hasUseTrigram", var("r6"), var("u6")),
    atom_prop("rel:hasBodyTrigram", var("r6"), var("b6")),
    atom_prop("rel:hasFivePhase", var("u6"), var("pu6")),
    atom_prop("rel:hasFivePhase", var("b6"), var("pb6")),
    atom_prop("rel:generates", var("pb6"), var("pu6")),
], [atom_data("rel:shengKeType", var("r6"), 4)], "R6 体生用=有耗（耗失之患）")
R("R7", [
    atom_class("rel:BodyUseRelation", var("r7")),
    atom_prop("rel:hasUseTrigram", var("r7"), var("u7")),
    atom_prop("rel:hasBodyTrigram", var("r7"), var("b7")),
    atom_prop("rel:hasFivePhase", var("u7"), var("pu7")),
    atom_prop("rel:hasFivePhase", var("b7"), var("pb7")),
    atom_prop("rel:overcomes", var("pu7"), var("pb7")),
], [atom_data("rel:shengKeType", var("r7"), 5)], "R7 用克体=最凶（诸事凶）")
# R8 变卦克体·末后不吉（经本卦：本卦亦被 R1/R2 推为体用关系，携体卦与变卦）
# 说明：原著「变卦五行克体五行」以变卦上卦之五行代表变卦，克体卦五行则末后不吉。
R("R8", [
    atom_class("core:OriginalHexagram", var("h8")),
    atom_prop("rel:hasBodyTrigram", var("h8"), var("b8")),
    atom_prop("rel:hasFivePhase", var("b8"), var("pb8")),
    atom_prop("core:hasChangedHexagram", var("h8"), var("c8")),
    atom_prop("core:hasUpperTrigram", var("c8"), var("cu8")),
    atom_prop("rel:hasFivePhase", var("cu8"), var("pcu8")),
    atom_prop("rel:overcomes", var("pcu8"), var("pb8")),
], [atom_data("jud:processFlag", var("h8"), "末后不吉")], "R8 变卦五行克体五行 → 末后不吉")
# R9 一撮金六等→五级
R("R9", [
    atom_class("jud:JudgementDomain", var("j9")),
    atom_prop("jud:hasYicuoJinAdmonition", var("j9"), var("a9")),
    atom_prop("jud:hasYicuoJinGrade", var("a9"), var("g9")),
    atom_data("jud:yicuoJinGradeValue", var("g9"), var("v9")),
    atom_builtin("equal", [var("v9"), 6]),
], [atom_prop("jud:hasAuspiciousness", var("j9"), "jud:DaJi")], "R9 一撮金：上上(6)→大吉（其余类推）")
# R10 重大吉凶→告警
R("R10", [
    atom_class("jud:JudgementDomain", var("j10")),
    atom_prop("jud:hasAuspiciousness", var("j10"), var("a10")),
    atom_class("jud:Auspiciousness", var("a10")),
    atom_data("jud:auspiciousnessLevel", var("a10"), var("v10")),
    atom_builtin("equal", [var("v10"), 5]),
], [atom_data("jud:isMajorResult", var("j10"), "true")], "R10 大吉→重大结果（大凶同理 level=1）")
# R11 后天体系引周易卦爻辞
R("R11", [
    atom_class("jud:JudgementDomain", var("j11")),
    atom_data("jud:divinationSystem", var("j11"), "后天"),
], [atom_data("jud:usesZhouyiDictum", var("j11"), "true")], "R11 后天体系 → 引周易卦爻辞")
# R12 先天体系不引（一撮金豁免）
R("R12", [
    atom_class("jud:JudgementDomain", var("j12")),
    atom_data("jud:divinationSystem", var("j12"), "先天"),
    atom_prop("jud:hasMethod", var("j12"), var("m12")),
    atom_class("div:DivinationMethod", var("m12")),
    atom_builtin("notEqual", [var("m12"), "res:div:YicuoJinMethod"]),
], [atom_data("jud:usesZhouyiDictum", var("j12"), "false")], "R12 先天体系 → 不引周易爻辞（一撮金除外）")
# R13 干支→五行
R("R13", [
    atom_class("cal:CalendarDatum", var("c13")),
    atom_prop("cal:hasStem", var("c13"), var("s13")),
    atom_prop("cal:stemFivePhase", var("s13"), var("p13")),
], [atom_prop("cal:datumFivePhase", var("c13"), var("p13"))], "R13 干支→五行 传导")
# R14 纳音→五行
R("R14", [
    atom_class("cal:SexagenaryCycle", var("x14")),
    atom_prop("cal:hasNaYin", var("x14"), var("n14")),
    atom_prop("cal:naYinPhase", var("n14"), var("p14")),
], [atom_prop("cal:datumFivePhase", var("x14"), var("p14"))], "R14 纳音→五行")
# R15 六十四卦合成：trigramPairMap 为核心设计 §2 明确的「内置映射表（非本体个体）」，
# 故不以 SWRL 形式编码（否则须引入本不存在的映射个体类）；由查询/规则层以内置表实现，
# 六十四卦名录由 core:SixBitHexagram 个体（ABox）承载。

w('    <!-- ==================== SWRL 规则（R1~R14；R15 见下方注释） ==================== -->')
w('    <!-- 变量声明 -->')
for v in sorted(VARS):
    w('    <rdf:Description rdf:about="rules:%s">' % v)
    w('        <rdf:type rdf:resource="http://www.w3.org/2003/11/swrl#Variable"/>')
    w('    </rdf:Description>')
w('')
for name, body, head, comment in rules:
    rule(name, body, head, comment)

w('</rdf:RDF>')

# ---------------- 写文件 ----------------
os.makedirs(os.path.dirname(OUT), exist_ok=True)
with io.open(OUT, "w", encoding="utf-8", newline="\n") as f:
    f.write("\n".join(L) + "\n")

# catalog
with io.open(CAT, "w", encoding="utf-8", newline="\n") as f:
    f.write('<?xml version="1.0" encoding="UTF-8" standalone="no"?>\n')
    f.write('<catalog prefer="public" xmlns="urn:oasis:names:tc:entity:xmlns:xml:catalog">\n')
    f.write('    <group id="Folder Repository, directory=, recursive=true, Auto-Update=true, version=2" prefer="public">\n')
    f.write('        <uri id="User Entered Import Resolution" name="http://example.org/plumblossom/all" uri="plumblossom-all.owl"/>\n')
    f.write('        <!-- 第 9/10 步拆分后追加各子本体 IRI→文件映射 -->\n')
    f.write('    </group>\n')
    f.write('</catalog>\n')

print("written:", os.path.normpath(OUT), len(L), "lines")
print("written:", os.path.normpath(CAT))
