# -*- coding: utf-8 -*-
"""校验塔架中段本体 v15.0"""
import io, sys, re
from rdflib import Graph, Namespace, RDF, RDFS, OWL, URIRef
from rdflib.collection import Collection

P = r"D:\work\Ontology\GoldenWind\ontology\TowerMidSection.owl"
NS = "http://goldwind.com/ontology/tower-mid-section#"
EX = Namespace(NS)

g = Graph()
g.parse(P, format="xml")
print("三元组总数 :", len(g))

def local(u):
    s = str(u)
    return s.split("#")[-1] if "#" in s else s

classes = sorted({local(c) for c in g.subjects(RDF.type, OWL.Class) if isinstance(c, URIRef)})
objprops = sorted({local(p) for p in g.subjects(RDF.type, OWL.ObjectProperty)})
dataprops = sorted({local(p) for p in g.subjects(RDF.type, OWL.DatatypeProperty)})
inds = sorted({local(i) for i in g.subjects(RDF.type, OWL.NamedIndividual)})
print("具名类     :", len(classes))
print("对象属性   :", len(objprops))
print("数据属性   :", len(dataprops))
print("个体       :", len(inds))

# 1) 悬空引用
defined = set()
for s_, p_, o_ in g:
    if isinstance(s_, URIRef) and str(s_).startswith(NS):
        defined.add(str(s_))
dangling = set()
for s_, p_, o_ in g:
    if isinstance(o_, URIRef) and str(o_).startswith(NS) and str(o_) not in defined:
        dangling.add(str(o_))
print("悬空引用   :", sorted(local(d) for d in dangling) or "无")

# 2) Restriction 合法性
invalid = 0
for r in g.subjects(RDF.type, OWL.Restriction):
    onp = list(g.objects(r, OWL.onProperty))
    has_card = any(g.objects(r, x) for x in
                   (OWL.cardinality, OWL.minCardinality, OWL.maxCardinality,
                    OWL.qualifiedCardinality, OWL.minQualifiedCardinality, OWL.maxQualifiedCardinality))
    has_range = any(g.objects(r, x) for x in (OWL.allValuesFrom, OWL.someValuesFrom, OWL.hasValue))
    if not onp:
        invalid += 1; continue
    if list(g.objects(r, OWL.onDataRange)):
        if not any(g.objects(r, x) for x in
                   (OWL.qualifiedCardinality, OWL.minQualifiedCardinality, OWL.maxQualifiedCardinality)):
            invalid += 1
    if not (has_card or has_range):
        invalid += 1
print("Restriction:", len(list(g.subjects(RDF.type, OWL.Restriction))), " invalid =", invalid)

# 3) inverseOf 配对（本体约定：仅正向声明，故未配对属预期）
inv = list(g.subject_objects(OWL.inverseOf))
bad = [(local(a), local(b)) for a, b in inv if (b, a) not in inv]
print("inverseOf  :", len(inv), " 仅正向声明(预期) =", len(bad))

# 4) 不相交类 + 父子同组检查
print("AllDisjointClasses:", len(list(g.subjects(RDF.type, OWL.AllDisjointClasses))))
sub_of = {}
for c in g.subjects(RDFS.subClassOf, None):
    o = list(g.objects(c, RDFS.subClassOf))
    for x in o:
        if isinstance(x, URIRef):
            sub_of.setdefault(str(c), set()).add(str(x))

def ancestors(u, seen=None):
    seen = seen or set()
    for a in sub_of.get(str(u), ()):
        if a not in seen:
            seen.add(a)
            ancestors(a, seen)
    return seen

for d in g.subjects(RDF.type, OWL.AllDisjointClasses):
    m = list(g.objects(d, OWL.members))
    if m:
        col = [str(x) for x in Collection(g, m[0])]
        names = [local(x) for x in col]
        print("   -", names)
        # 组内不得同时出现父类与其子类
        for x in col:
            for y in col:
                if x != y and y in ancestors(x):
                    print("     [!!] 父子同组:", local(x), "⊂", local(y))

# 5) 关键结构断言
def cls(u): return URIRef(NS + u)
print("\n--- 关键断言 ---")
print("TowerMidSection ⊂ Tower :", (cls("TowerMidSection"), RDFS.subClassOf, cls("Tower")) in g)
print("TowerSection 已删除   :", not list(g.predicate_objects(cls("TowerSection"))))
print("Stud ⊂ Component     :", (cls("Stud"), RDFS.subClassOf, cls("Component")) in g)
print("CableTray ⊂ Component:", (cls("CableTray"), RDFS.subClassOf, cls("Component")) in g)
print("Stud ⊂ Accessory     :", (cls("Stud"), RDFS.subClassOf, cls("Accessory")) in g)
print("Support ⊂ Component  :", (cls("Support"), RDFS.subClassOf, cls("Component")) in g)
print("Support ⊂ Accessory  :", (cls("Support"), RDFS.subClassOf, cls("Accessory")) in g)
print(":扶持到焊缝距离 存在  :", bool(list(g.predicate_objects(cls("supportToWeldDistance")))))
print(":直径 已删除          :", (cls("diameter"), None, None) not in g and
      not list(g.predicate_objects(cls("diameter"))))
print(":灯上螺柱间距 存在    :", bool(list(g.predicate_objects(cls("lightStudSpacing")))))
print(":灯与灯最大间距 存在  :", bool(list(g.predicate_objects(cls("lightToLightMaxSpacing")))))
print(":灯与灯最小间距 存在  :", bool(list(g.predicate_objects(cls("lightToLightMinSpacing")))))
print(":螺柱间距 已删除      :", not list(g.predicate_objects(cls("studSpacing"))))
print(":电缆托架规格 hasKey 已移除(v15.8):", not list(g.objects(cls("CableBracketSpec"), OWL.hasKey)))
print(":有构件 存在          :", bool(list(g.predicate_objects(cls("hasComponent")))))

# 6) v15.3 灯属性 / 爬梯属性断言
print("\n--- v15.3 断言 ---")
print(":下灯位置 已删除      :", not list(g.predicate_objects(cls("lowerLightPosition"))))
print(":上灯位置 已删除      :", not list(g.predicate_objects(cls("upperLightPosition"))))
print(":踏级间距 已删除      :", not list(g.predicate_objects(cls("rungSpacing"))))
print(":踏面宽度 已删除      :", not list(g.predicate_objects(cls("treadWidth"))))
print(":爬梯净宽 已删除      :", not list(g.predicate_objects(cls("ladderClearWidth"))))
print(":爬梯与塔壁净距 已删除:", not list(g.predicate_objects(cls("ladderToWallDistance"))))
print(":梯子高度 存在        :", bool(list(g.predicate_objects(cls("ladderHeight")))))
print(":灯上螺柱间距 域=焊接灯:", (cls("lightStudSpacing"), RDFS.domain, cls("WeldedLight")) in g)
# :焊接灯 恰好 2 个螺柱
wl_card = None
for r in g.subjects(RDFS.subClassOf, cls("WeldedLight")):
    pass
for r in g.objects(cls("WeldedLight"), RDFS.subClassOf):
    if (r, OWL.onProperty, cls("mountedOnStud")) in g:
        wl_card = list(g.objects(r, OWL.qualifiedCardinality))
        wl_onclass = list(g.objects(r, OWL.onClass))
        print(":焊接灯 :安装在螺柱 基数:", [str(x) for x in wl_card],
              "onClass:", [local(x) for x in wl_onclass])
print(":焊接灯 恰好 2 螺柱    :", wl_card is not None and str(wl_card[0]) == "2")

# 7) v15.4 断言
print("\n--- v15.4 断言 ---")
print(":扶持水平角度 已删除  :", not list(g.predicate_objects(cls("supportHorizontalAngle"))))
print(":扶持位置 存在        :", bool(list(g.predicate_objects(cls("supportPosition")))))
print(":扶持到焊缝距离 存在  :", bool(list(g.predicate_objects(cls("supportToWeldDistance")))))

# 8) v15.5 断言（删除线夹及相关属性）
print("\n--- v15.5 断言 ---")
print(":电缆线夹 类已删除    :", not list(g.predicate_objects(cls("CableClamp"))))
print(":线夹位置 已删除      :", not list(g.predicate_objects(cls("clampPosition"))))
print(":夹持电缆 已删除      :", not list(g.predicate_objects(cls("clampsCable"))))
print(":线夹映射到爬梯支撑 已删除:", not list(g.predicate_objects(cls("clampMappedToLadderSupport"))))
print(":是否隔开电缆线夹 已删除:", not list(g.predicate_objects(cls("isCableClampSeparated"))))

print("\n--- v15.6 断言 ---")
print(":平齐 存在            :", bool(list(g.predicate_objects(cls("flushWith")))))
print(":与…平齐 标签已改     :", (cls("flushWith"), RDFS.label, None) in g and
      any(str(o) == "平齐" for o in g.objects(cls("flushWith"), RDFS.label)))
print(":材料 类已删除        :", not list(g.predicate_objects(cls("Material"))))
print(":有材料 已删除        :", not list(g.predicate_objects(cls("hasMaterial"))))
print(":材料属于 已删除      :", not list(g.predicate_objects(cls("materialBelongsToTubeSection"))))
print(":板材等级 已删除      :", not list(g.predicate_objects(cls("plateGrade"))))
print(":材料密度 已删除      :", not list(g.predicate_objects(cls("materialDensity"))))
print(":材质 存在            :", bool(list(g.predicate_objects(cls("material")))))
print(":照明系统 类已删除    :", not list(g.predicate_objects(cls("LightingSystem"))))
print(":有照明系统 已删除    :", not list(g.predicate_objects(cls("hasLightingSystem"))))
print(":照明系统属于 已删除  :", not list(g.predicate_objects(cls("lightingSystemBelongsToTowerMidSection"))))
print(":有灯 域=塔架中段     :", (cls("hasLight"), RDFS.domain, cls("TowerMidSection")) in g)
print(":灯属于 已改名        :", bool(list(g.predicate_objects(cls("lightBelongsToTowerMidSection")))) and
      not list(g.predicate_objects(cls("lightBelongsToLightingSystem"))))
print(":第一灯安装高度 域=中段:", (cls("firstLightHeight"), RDFS.domain, cls("TowerMidSection")) in g)
print(":灯与灯最大间距 域=中段:", (cls("lightToLightMaxSpacing"), RDFS.domain, cls("TowerMidSection")) in g)
print(":灯与灯最小间距 域=中段:", (cls("lightToLightMinSpacing"), RDFS.domain, cls("TowerMidSection")) in g)
print(":有塔架中段 存在      :", bool(list(g.predicate_objects(cls("hasTowerMidSection")))))
print(":有塔架段 已删除      :", not list(g.predicate_objects(cls("hasTowerSection"))))
print(":塔架段属于 已删除    :", not list(g.predicate_objects(cls("sectionBelongsToTower"))))
print(":法兰连接塔架中段 存在:", bool(list(g.predicate_objects(cls("flangeConnectsTowerMidSection")))))
print(":法兰连接塔架段 已删除:", not list(g.predicate_objects(cls("flangeConnectsTowerSection"))))
print(":相邻于 域=塔架中段   :", (cls("adjacentTo"), RDFS.domain, cls("TowerMidSection")) in g)
print(":疲劳等级 已删除      :", not list(g.predicate_objects(cls("fatigueDetailCategory"))))
print(":螺柱安装角度 已删除  :", not list(g.predicate_objects(cls("studInstallationAngle"))))
print(":配件类型名称 已删除  :", not list(g.predicate_objects(cls("accessoryTypeName"))))
print(":配件类型 无身份键    :", not list(g.objects(cls("AccessoryType"), OWL.hasKey)))
print(":安全锚点 类已删除    :", not list(g.predicate_objects(cls("SafetyAnchor"))))
print(":有安全锚点 已删除    :", not list(g.predicate_objects(cls("hasSafetyAnchor"))))
print(":锚点安装高度 已删除  :", not list(g.predicate_objects(cls("anchorInstallationHeight"))))
print(":宽度 存在            :", bool(list(g.predicate_objects(cls("width")))))
print(":支撑宽度 已删除      :", not list(g.predicate_objects(cls("ladderSupportWidth"))))

print("\n--- v15.7 断言（术语统一：配件→附件）---")
print(":配件类型 已删除      :", not list(g.predicate_objects(cls("FittingType"))))
print(":选用配件类型 已删除  :", not list(g.predicate_objects(cls("selectsFittingType"))))
print(":配件类型适用于 已删除:", not list(g.predicate_objects(cls("fittingTypeUsedByConnectionType"))))
_owl_txt = open(r"D:\work\Ontology\GoldenWind\ontology\TowerMidSection.owl", encoding="utf-8").read()
import re as _re
_bad_ids = [m.group(1) for m in _re.finditer(r'rdf:about="#([^"]+)"', _owl_txt)
            if "配件" in m.group(1) or "Fitting" in m.group(1) or "fitting" in m.group(1)]
print("标识符无「配件/Fitting」     :", not _bad_ids, _bad_ids)

print("\n--- v15.8 断言（分类值类→数据属性 + 删电缆托架规格类）---")
print(":附件连接方式 类已删除    :", not list(g.predicate_objects(cls("AccessoryConnectionType"))))
print(":附件类型 类已删除        :", not list(g.predicate_objects(cls("AccessoryType"))))
print(":灯类型 类已删除          :", not list(g.predicate_objects(cls("LightType"))))
print(":电缆托架规格 类已删除    :", not list(g.predicate_objects(cls("CableBracketSpec"))))
print(":有连接方式 已删除        :", not list(g.predicate_objects(cls("hasConnectionType"))))
print(":连接方式属于 已删除      :", not list(g.predicate_objects(cls("connectionTypeBelongsToAccessory"))))
print(":有灯类型 已删除          :", not list(g.predicate_objects(cls("hasLightType"))))
print(":灯类型属于 已删除        :", not list(g.predicate_objects(cls("lightTypeBelongsToLight"))))
print(":选用附件类型 已删除      :", not list(g.predicate_objects(cls("selectsAccessoryType"))))
print(":附件类型适用于 已删除    :", not list(g.predicate_objects(cls("accessoryTypeUsedByConnectionType"))))
print(":有电缆托架规格 已删除    :", not list(g.predicate_objects(cls("hasBracketSpec"))))
print(":托架规格属于 已删除      :", not list(g.predicate_objects(cls("bracketSpecBelongsToModel"))))
print(":连接方式名称 已删除      :", not list(g.predicate_objects(cls("connectionTypeName"))))
print(":灯类型名称 已删除        :", not list(g.predicate_objects(cls("lightTypeName"))))
print(":附件连接方式 数据属性存在:", bool(list(g.predicate_objects(cls("accessoryConnectionType")))))
print(":附件连接方式 域=附件     :", (cls("accessoryConnectionType"), RDFS.domain, cls("Accessory")) in g)
print(":附件类型 数据属性存在    :", bool(list(g.predicate_objects(cls("accessoryType")))))
print(":附件类型 域=附件         :", (cls("accessoryType"), RDFS.domain, cls("Accessory")) in g)
print(":灯类型 数据属性存在      :", bool(list(g.predicate_objects(cls("lightType")))))
print(":灯类型 域=灯             :", (cls("lightType"), RDFS.domain, cls("Light")) in g)
print(":托架长度 域=电缆托架     :", (cls("bracketLength"), RDFS.domain, cls("CableBracket")) in g)
print(":托架右弦长 域=电缆托架   :", (cls("bracketRightChord"), RDFS.domain, cls("CableBracket")) in g)
print(":托架左弦长 域=电缆托架   :", (cls("bracketLeftChord"), RDFS.domain, cls("CableBracket")) in g)
print("个体数=0                  :", len(inds) == 0)
print("hasKey 组数=3             :", len(list(g.subjects(OWL.hasKey, None))) == 3)
print("分类值不相交组=2 类       :", any(
    set(Collection(g, list(g.objects(adc, OWL.members))[0])) == {cls("Model"), cls("Region")}
    for adc in g.subjects(RDF.type, OWL.AllDisjointClasses)
    if list(g.objects(adc, OWL.members))))
print("「配件」仅存于变更说明    :", _owl_txt.count("配件") <= 6)

print("\n类清单:", ", ".join(classes))
