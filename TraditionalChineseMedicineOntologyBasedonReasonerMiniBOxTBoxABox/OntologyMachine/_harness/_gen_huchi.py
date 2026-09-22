# -*- coding: utf-8 -*-
"""
生成互斥本体 tcm-huchi.owl。

依据（铁律 63：一切改动必须有医理/医书依据，严禁编造；用户要求：必须是一定互斥的症状才建立互斥）：
  互斥 = 同一维度上「不可能同时成立」的两个四诊所见。分三类：
  (1) 逻辑否定对：X 与「不X／无X」，如 恶寒／不恶寒、汗出／无汗、呕／不呕。
      依据《伤寒论》原文对举（如 1 条「恶寒」、182 条「不恶寒」；35 条「无汗」、13 条「汗出」；
      96 条「若胸中烦而不呕」「或不渴」）。
  (2) 脉象对立对：同一脉诊维度之两极。依据《濒湖脉学》：
      浮「举之有余、按之不足」／沉「举之不足、按之有余」（脉位）；
      数「一息五至以上」／迟「一息三至」（脉率）；
      滑「往来流利」／涩「往来艰涩」（脉流利度）；
      虚「按之空虚」／实「按之充实」（脉势）；
      长「过于本位」／短「不及本位」（脉长）；
      大／小（脉形）；洪「来盛去衰」／微「极细而软、按之欲绝」（脉势盛衰）；
      浮／伏「重按推筋着骨始得」（脉位深浅）。
  (3) 单值维度内互斥：舌色（淡红/淡白/红/绛/紫/青）、苔色（白/黄/灰/黑）——舌面只有一个颜色，
      故同维度各类两两互斥；另加 苔之厚薄（薄/厚）、苔之润燥（滑/燥）、舌面润燥（润/滑 与 燥/糙）、
      舌形胖瘦（胖大/瘦薄）、舌态（强硬/痿软）、腹证（按痛/按之不痛、按之濡/按之石硬）。
      依据《中医舌诊》《中医诊断学》及《伤寒论》《金匮要略》腹诊原文。

  已用 _validate_huchi.py 校验：49 对互斥均不会使任何既有类（方证/舌象复合/判据类）变为不可满足。

输出：ontology/tcm-huchi.owl
"""
import os, sys, io
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')
ONT = r"D:/work/Ontology/TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox/ontology"
NS = "http://www.tcm-classics.org/jingfang#"

# (左, 右, 依据)
PAIRS = [
    # ---- (1) 症状：逻辑否定对 ----
    ("Ehan", "Buehan", "恶寒之有无。《伤寒论》1条「恶寒」、182条「不恶寒」"),
    ("Hanchu", "Wuhan", "汗之有无。《伤寒论》13条「汗出」、35条「无汗」"),
    ("Kouke", "Buke", "渴之有无。《伤寒论》71条「渴」、96条「或不渴」"),
    ("Tu", "Butu", "吐之有无。《伤寒论》96条「不吐」"),
    ("Ou", "Buou", "呕之有无。《伤寒论》96条「若胸中烦而不呕」"),
    ("Yuyin", "Buyuyin", "欲饮之有无。《伤寒论》71条「欲饮水」、282条「不欲饮」"),
    ("Dare", "Wudare", "大热之有无。《伤寒论》63条「无大热」"),
    ("Xiali", "Budabian", "大便通利与否。《伤寒论》314条「下利」、181条「不大便」"),
    # ---- (2) 脉象：对立对 ----
    ("Fumai", "Chenmai", "脉位。浮「举之有余、按之不足」／沉「举之不足、按之有余」。《濒湖脉学》"),
    ("Shumai", "Chimai", "脉率。数「一息五至以上」／迟「一息三至」。《濒湖脉学》"),
    ("Huamai", "Semai", "脉流利度。滑「往来流利」／涩「往来艰涩」。《濒湖脉学》"),
    ("Xumai", "Shimai", "脉势。虚「按之空虚」／实「按之充实」。《濒湖脉学》"),
    ("Changmai", "Duanmai", "脉长。长「过于本位」／短「不及本位」。《濒湖脉学》"),
    ("Damai", "Xiaomai", "脉形大小。《濒湖脉学》"),
    ("Hongmai", "Weimai", "脉势盛衰。洪「来盛去衰」／微「极细而软、按之欲绝」。《濒湖脉学》"),
    ("Fumai", "Fumai_Yin", "脉位深浅。浮「举之有余」／伏「重按推筋着骨始得」。《濒湖脉学》"),
    # ---- (3) 舌象：舌色单值（两两互斥）----
    ("PaleRedTongue", "PaleWhiteTongue", "舌色单值。《中医舌诊》舌色分淡红/淡白/红/绛/紫/青"),
    ("PaleRedTongue", "RedTongue", "舌色单值。《中医舌诊》"),
    ("PaleRedTongue", "CrimsonTongue", "舌色单值。《中医舌诊》"),
    ("PaleRedTongue", "PurpleTongue", "舌色单值。《中医舌诊》"),
    ("PaleRedTongue", "BlueTongue", "舌色单值。《中医舌诊》"),
    ("PaleWhiteTongue", "RedTongue", "舌色单值。《中医舌诊》"),
    ("PaleWhiteTongue", "CrimsonTongue", "舌色单值。《中医舌诊》"),
    ("PaleWhiteTongue", "PurpleTongue", "舌色单值。《中医舌诊》"),
    ("PaleWhiteTongue", "BlueTongue", "舌色单值。《中医舌诊》"),
    ("RedTongue", "CrimsonTongue", "舌色单值。《中医舌诊》"),
    ("RedTongue", "PurpleTongue", "舌色单值。《中医舌诊》"),
    ("RedTongue", "BlueTongue", "舌色单值。《中医舌诊》"),
    ("CrimsonTongue", "PurpleTongue", "舌色单值。《中医舌诊》"),
    ("CrimsonTongue", "BlueTongue", "舌色单值。《中医舌诊》"),
    ("PurpleTongue", "BlueTongue", "舌色单值。《中医舌诊》"),
    # ---- 苔色单值（两两互斥）----
    ("WhiteCoating", "YellowCoating", "苔色单值。《中医舌诊》苔色分白/黄/灰/黑"),
    ("WhiteCoating", "GreyCoating", "苔色单值。《中医舌诊》"),
    ("WhiteCoating", "BlackCoating", "苔色单值。《中医舌诊》"),
    ("YellowCoating", "GreyCoating", "苔色单值。《中医舌诊》"),
    ("YellowCoating", "BlackCoating", "苔色单值。《中医舌诊》"),
    ("GreyCoating", "BlackCoating", "苔色单值。《中医舌诊》"),
    # ---- 苔质 ----
    ("ThinCoating", "ThickCoating", "苔之厚薄。《中医舌诊》"),
    ("SlipperyCoating", "DryCoating", "苔之润燥。《中医舌诊》"),
    ("TongueNoCoating", "ThickCoating", "无苔与厚苔不可并见。《中医舌诊》"),
    ("TongueNoCoating", "ThinCoating", "无苔与薄苔不可并见。《中医舌诊》"),
    # ---- 舌面润燥 ----
    ("MoistTongue", "DryTongue", "舌面润燥。《中医舌诊》"),
    ("SlipperyTongue", "DryTongue", "舌面润燥。《中医舌诊》"),
    ("MoistTongue", "RoughTongue", "舌面润燥。《中医舌诊》"),
    ("SlipperyTongue", "RoughTongue", "舌面润燥。《中医舌诊》"),
    # ---- 舌形/舌态 ----
    ("SwollenTongue", "ThinTongue", "舌形胖瘦。《中医舌诊》"),
    ("StiffTongue", "FlaccidTongue", "舌态强硬与痿软不可并见。《中医舌诊》"),
    # ---- 腹证 ----
    ("Antong", "Anzhibutong", "按痛之有无。《伤寒论》《金匮要略》腹诊"),
    ("Anzhiru", "Anzhishiying", "腹力濡软与石硬不可并见。《伤寒论》135条「按之石硬」"),
]

def esc(s):
    return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

# 按左类聚合
by_left = {}
for a, b, why in PAIRS:
    by_left.setdefault(a, []).append((b, why))

blocks = []
for a in sorted(by_left):
    lines = [f'    <owl:Class rdf:about="#{a}">']
    for b, why in by_left[a]:
        lines.append(f'        <owl:disjointWith rdf:resource="#{b}"/>')
        lines.append(f'        <huChiReason xml:lang="zh">{esc(a)} ⊥ {esc(b)}：{esc(why)}</huChiReason>')
    lines.append('    </owl:Class>')
    blocks.append("\n".join(lines))

header = f'''<?xml version="1.0" encoding="UTF-8"?>
<rdf:RDF xmlns="{NS}"
         xml:base="{NS}"
         xmlns:owl="http://www.w3.org/2002/07/owl#"
         xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#"
         xmlns:rdfs="http://www.w3.org/2000/01/rdf-schema#"
         xmlns:xsd="http://www.w3.org/2001/XMLSchema#">

    <owl:Ontology rdf:about="http://www.tcm-classics.org/jingfang/huchi">
        <rdfs:label xml:lang="zh">互斥模块</rdfs:label>
        <rdfs:comment xml:lang="zh">四诊互斥关系。互斥 = 同一维度上不可能同时成立的两个四诊所见，以 owl:disjointWith 表达（OWL 语义：个体同时属于互斥两类则本体不一致，即「四诊参合有矛盾」）。仅收录「一定互斥」者：逻辑否定对（恶寒/不恶寒、汗出/无汗等）、脉象对立对（浮/沉、数/迟等，依《濒湖脉学》）、单值维度内互斥（舌色、苔色等，依《中医舌诊》）。每对均附 huChiReason 医理/医书依据，无一条出于编造。</rdfs:comment>
        <owl:imports rdf:resource="http://www.tcm-classics.org/jingfang/core"/>
        <owl:versionInfo>1.0</owl:versionInfo>
    </owl:Ontology>

    <owl:AnnotationProperty rdf:about="#huChiReason">
        <rdfs:label xml:lang="zh">互斥依据</rdfs:label>
        <rdfs:comment xml:lang="zh">该互斥对所依据的医理/医书原文。不参与推理，供应用层与审计读取。</rdfs:comment>
    </owl:AnnotationProperty>

'''

out = header + "\n".join(blocks) + "\n\n</rdf:RDF>\n"
with open(os.path.join(ONT, "tcm-huchi.owl"), "w", encoding='utf-8') as f:
    f.write(out)
print("已写出 tcm-huchi.owl：", len(PAIRS), "对互斥，", len(by_left), "个左类")
