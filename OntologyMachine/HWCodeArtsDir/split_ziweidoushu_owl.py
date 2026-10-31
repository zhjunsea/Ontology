# -*- coding: utf-8 -*-
"""
第 9 步：紫微斗数 TBox/ABox 分离（单文件 -> all/tbox/abox 三文件）。
- 输入：OntologyFrameworkZiWeiDouShu/ontology/ziweidoushu-all.owl（单文件，含节1-5 TBox + 节6 ABox）
- 输出：ziweidoushu-tbox.owl / ziweidoushu-abox.owl / ziweidoushu-all.owl(聚合入口) / catalog-v001.xml
- 口径照兄弟应用 PlumBlossom / SiZhuBaZi（xmlns+xml:base, all imports tbox, abox imports 注释掉）。
- 属性值保持完整绝对 IRI（原文本原样搬运），避免本平台 RDF/XML 解析器 CURIE 失配。
"""
import re, os, shutil, sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)                      # OntologyMachine
ONT = os.path.join(ROOT, "OntologyFrameworkZiWeiDouShu", "ontology")
SRC = os.path.join(ONT, "ziweidoushu-all.owl")
BAK = os.path.join(HERE, "_ziweidoushu-all.owl.bak")

text = open(SRC, encoding="utf-8").read()

marker1 = "<!-- ============================ 1. 类体系"
marker6 = "<!-- ============================ 6. 典型 ABox"
if marker1 not in text or marker6 not in text:
    sys.exit("!! 未找到分节标记，文件可能已拆分，终止。")

# 备份原单文件
shutil.copyfile(SRC, BAK)

i1 = text.index(marker1)
i6 = text.index(marker6)
iend = text.rindex("</rdf:RDF>")
tbox_body = text[i1:i6].rstrip() + "\n"
abox_body = text[i6:iend].rstrip() + "\n"

DOMAINS = ["core", "calendar", "chart", "star", "layout", "state",
           "palace", "pattern", "fortune", "reading", "diagnosis", "rules"]


def ns_lines():
    lines = [
        '     xmlns:owl="http://www.w3.org/2002/07/owl#"',
        '     xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#"',
        '     xmlns:rdfs="http://www.w3.org/2000/01/rdf-schema#"',
        '     xmlns:xsd="http://www.w3.org/2001/XMLSchema#"',
        '     xmlns:swrl="http://www.w3.org/2003/11/swrl#"',
        '     xmlns:swrlb="http://www.w3.org/2003/11/swrlb#"',
        '     xmlns:skos="http://www.w3.org/2004/02/skos/core#"',
    ]
    for d in DOMAINS:
        lines.append('     xmlns:%s="http://example.org/ziweidoushu/%s#"' % (d, d))
    return "\n".join(lines)


# ---------------- tbox ----------------
tbox_onto = (
    '    <owl:Ontology rdf:about="http://example.org/ziweidoushu/tbox">\n'
    '        <rdfs:label xml:lang="zh">紫微斗数 TBox（类体系 / 对象属性 / 数据属性 / owl:Restriction 约束 / SWRL R1~R25）</rdfs:label>\n'
    '        <rdfs:comment xml:lang="zh">第 9 步产出：TBox/ABox 分离。本文件承载全部术语（TBox，节 1–5）；典型 ABox 见 ziweidoushu-abox.owl。'
    'ABox 依铁律 11 不进主入口闭包（all 的 abox imports 注释掉），由应用侧 / Ontop OBDA 独立供给。'
    '据《紫微斗数应用-本体核心设计》v1.1（闸门③通过）。依据《紫微斗數全書》（[宋]陈抟（陈希夷）撰）。'
    '★两类「化」严格分列：化气·静态 state:StarCharacter ／ 四化·动态 layout:Transformation。</rdfs:comment>\n'
    '    </owl:Ontology>'
)
tbox = ('<?xml version="1.0"?>\n'
        '<rdf:RDF xmlns="http://example.org/ziweidoushu/tbox"\n'
        '     xml:base="http://example.org/ziweidoushu/tbox"\n'
        + ns_lines() + '>\n\n'
        + tbox_onto + '\n\n'
        + tbox_body + '\n'
        '</rdf:RDF>\n')

# ---------------- abox ----------------
objprops = list(dict.fromkeys(re.findall(r'<owl:ObjectProperty rdf:about="([^"]+)"', tbox_body)))
decl = "\n".join('    <owl:ObjectProperty rdf:about="%s"/>' % p for p in objprops)
abox_onto = (
    '    <owl:Ontology rdf:about="http://example.org/ziweidoushu/abox">\n'
    '        <owl:imports rdf:resource="http://example.org/ziweidoushu/tbox"/>\n'
    '        <rdfs:label xml:lang="zh">紫微斗数 ABox（典型个体：历法枚举 / 典型命例 Demo / 四化 / 告警 / 出处绑定等）</rdfs:label>\n'
    '        <rdfs:comment xml:lang="zh">第 9 步产出（TBox/ABox 分离）。依赖 TBox 的类与属性，故显式 imports tbox。'
    '第 11 步按闸门⑩裁决将查表型历法个体迁出至 MySQL（经 OBDA 虚拟访问）；本文件为「本体侧保留」的典型 ABox。'
    '顶部对象属性声明用于确保断言被解析为对象属性断言而非注解。依据《紫微斗數全書》（[宋]陈抟（陈希夷）撰）。</rdfs:comment>\n'
    '    </owl:Ontology>'
)
abox = ('<?xml version="1.0"?>\n'
        '<rdf:RDF xmlns="http://example.org/ziweidoushu/abox"\n'
        '     xml:base="http://example.org/ziweidoushu/abox"\n'
        + ns_lines() + '>\n\n'
        + abox_onto + '\n\n'
        + '    <!-- 对象属性声明（使下方断言被解析为对象属性断言；否则 ABox 对推理机不生效） -->\n'
        + decl + '\n\n'
        + abox_body + '\n'
        '</rdf:RDF>\n')

# ---------------- all（聚合入口） ----------------
all_onto = (
    '    <owl:Ontology rdf:about="http://example.org/ziweidoushu/all">\n'
    '        <owl:imports rdf:resource="http://example.org/ziweidoushu/tbox"/>\n'
    '        <!-- abox\n'
    '             第 9 步：ABox 的 owl:imports 按铁律 11 注释掉——ABox 不进全局推理器（避免拖累分类），\n'
    '             由应用侧经 ontology.abox-path 独立加载 / 第 11 步起经 Ontop OBDA 虚拟访问。\n'
    '             参考 Example/ontology/pizza-all.owl 的分离形态。\n'
    '        <owl:imports rdf:resource="http://example.org/ziweidoushu/abox"/>\n'
    '        -->\n'
    '        <rdfs:label xml:lang="zh">紫微斗数本体总入口（TBox 聚合入口；ABox 由应用侧独立加载）</rdfs:label>\n'
    '        <rdfs:comment xml:lang="zh">第 9 步产出：聚合入口，显式导入 TBox。ABox 的 imports 依铁律 11 注释掉，由应用工程经 ontology.abox-path 指向 ziweidoushu-abox.owl 独立加载。加载此文件即导入全部 TBox。</rdfs:comment>\n'
    '    </owl:Ontology>'
)
allowl = ('<?xml version="1.0"?>\n'
          '<rdf:RDF xmlns="http://example.org/ziweidoushu/all"\n'
          '     xml:base="http://example.org/ziweidoushu/all"\n'
          + ns_lines() + '>\n\n'
          + all_onto + '\n\n'
          '</rdf:RDF>\n')

# ---------------- catalog ----------------
cat = ['<?xml version="1.0" encoding="UTF-8" standalone="no"?>',
       '<catalog prefer="public" xmlns="urn:oasis:names:tc:entity:xmlns:xml:catalog">',
       '    <uri id="User Entered Import Resolution" name="http://example.org/ziweidoushu/all" uri="ziweidoushu-all.owl"/>',
       '    <uri id="User Entered Import Resolution" name="http://example.org/ziweidoushu/tbox" uri="ziweidoushu-tbox.owl"/>',
       '    <uri id="User Entered Import Resolution" name="http://example.org/ziweidoushu/abox" uri="ziweidoushu-abox.owl"/>']
for d in DOMAINS:
    cat.append('    <uri id="User Entered Import Resolution" name="http://example.org/ziweidoushu/%s" uri="ziweidoushu-tbox.owl"/>' % d)
cat.append('    <group id="Folder Repository, directory=, recursive=true, Auto-Update=true, version=2" prefer="public">')
cat.append('        <uri id="Automatically generated entry, Timestamp=1793282066818" name="duplicate:http://example.org/ziweidoushu/" uri="ziweidoushu-all.owl"/>')
cat.append('        <uri id="Automatically generated entry, Timestamp=1793282066818" name="duplicate:http://example.org/ziweidoushu/" uri="ziweidoushu-tbox.owl"/>')
cat.append('        <uri id="Automatically generated entry, Timestamp=1793282066818" name="duplicate:http://example.org/ziweidoushu/" uri="ziweidoushu-abox.owl"/>')
cat.append('    </group>')
cat.append('</catalog>')
catalog = "\n".join(cat) + "\n"

open(os.path.join(ONT, "ziweidoushu-tbox.owl"), "w", encoding="utf-8").write(tbox)
open(os.path.join(ONT, "ziweidoushu-abox.owl"), "w", encoding="utf-8").write(abox)
open(SRC, "w", encoding="utf-8").write(allowl)
open(os.path.join(ONT, "catalog-v001.xml"), "w", encoding="utf-8").write(catalog)

print("OK  tbox=%d bytes  abox=%d bytes  all=%d bytes" % (len(tbox), len(abox), len(allowl)))
print("object properties declared in abox: %d" % len(objprops))
print("backup: %s" % BAK)
