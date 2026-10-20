# -*- coding: utf-8 -*-
"""第 12 步（闸门⑪）· 将 plumblossom-abox.owl 的「六十四卦名录」由典型（乾坤 2 卦）
扩至全集 64 卦（每卦含上下卦 + 6 爻个体 + 卦辞/爻辞引用），并同步重命名。

命名口径（邵康节 2026-10-20 确认）：
  名录 core:Hexagram_01..64；爻 core:Hexagram_<nn>_Line<pos>；
  卦辞 yijing:Dictum_Hex_01..64；爻辞 yijing:LineDictum_<nn>_<pos>。
"""
import os
from pb_hexagram_data import HEXAGRAMS, line_types

BASE = r"D:\work\Ontology\OntologyMachine\OntologyFrameworkPlumBlossom\ontology"
ABOX = os.path.join(BASE, "plumblossom-abox.owl")

TRIG_IRI = {t: "core:%sTrigram" % t for t in
            ["Qian", "Dui", "Li", "Zhen", "Xun", "Kan", "Gen", "Kun"]}

with open(ABOX, encoding="utf-8") as f:
    text = f.read()

# 更新 ontology 头 label/comment
old_label = "梅花易数 ABox（本体侧保留：八卦 / 方位 / 五行生克 / 旺衰 / 卦气性情 / 类象 / 四不占 / 吉凶等级与事项类别 / 一撮金等级 / 十应三要 / 乾坤爻与反对 等结构性、推理所需个体）"
new_label = "梅花易数 ABox（本体侧保留：八卦 / 方位 / 五行生克 / 旺衰 / 卦气性情 / 类象 / 四不占 / 吉凶等级与事项类别 / 一撮金等级 / 十应三要 / 六十四卦名录（含 384 爻）与反对 等结构性、推理所需个体）"
assert old_label in text
text = text.replace(old_label, new_label)

old_comment = ("第 9 步拆分产出；第 11 步按闸门⑩裁决将「历法（干支/六十甲子/纳音/长生）」与「周易文本（六十四卦名/卦辞/爻辞）」"
               "共 116 个体迁出，入 MySQL（plumblossomdb，经 OBDA plumBlossom.obda 虚拟访问），DB 为唯一真源；"
               "本文件仅保留 129 个结构性、推理所需个体。依赖 TBox 中的类与属性，故显式 imports tbox。")
new_comment = ("第 9 步拆分产出；第 11 步按闸门⑩裁决将「历法（干支/六十甲子/纳音/长生）」与「周易文本（六十四卦名/卦辞/爻辞）」"
               "共 116 个体迁出，入 MySQL（plumblossomdb，经 OBDA plumBlossom.obda 虚拟访问），DB 为唯一真源。"
               "第 12 步按闸门⑪将六十四卦名录由代表性（乾坤）扩至全集 64（名录 core:Hexagram_01..64，每卦含上下卦 + 六爻个体，"
               "并经 yijing:hasHexagramDictum/hasLineDictum 引用卦辞/爻辞；卦辞/爻辞文本经 OBDA 从 DB 取）。依赖 TBox 中的类与属性，故显式 imports tbox。")
assert old_comment in text
text = text.replace(old_comment, new_comment)

# 定位「乾坤爻/名录」区起点（其后全部内容由本脚本重建）
marker = '    <owl:NamedIndividual rdf:about="core:QianLine1">'
idx = text.index(marker)
end = text.rindex("</rdf:RDF>")
prefix = text[:idx]

BLOCK = []
BLOCK.append("    <!-- ==================== 六十四卦名录（全集） + 反对 ==================== -->")
for (seq, name, up, low, _guaci, _lines) in HEXAGRAMS:
    nn = "%02d" % seq
    for (pos, bit) in line_types(seq):
        kind = "YangLine" if bit == 1 else "YinLine"
        yy = "阳" if bit == 1 else "阴"
        BLOCK.append('    <owl:NamedIndividual rdf:about="core:Hexagram_%s_Line%d">' % (nn, pos))
        BLOCK.append('        <rdf:type rdf:resource="core:%s"/>' % kind)
        BLOCK.append('        <rdfs:label xml:lang="zh">%s第%d爻</rdfs:label>' % (name, pos))
        BLOCK.append('        <core:linePosition rdf:datatype="xsd:integer">%d</core:linePosition>' % pos)
        BLOCK.append('        <core:yinYang xml:lang="zh">%s</core:yinYang>' % yy)
        BLOCK.append('    </owl:NamedIndividual>')
        BLOCK.append("")
    BLOCK.append('    <owl:NamedIndividual rdf:about="core:Hexagram_%s">' % nn)
    BLOCK.append('        <rdf:type rdf:resource="core:SixBitHexagram"/>')
    BLOCK.append('        <rdfs:label xml:lang="zh">%s</rdfs:label>' % name)
    BLOCK.append('        <rdfs:comment xml:lang="zh">六十四卦名录（全集·卦序%d）</rdfs:comment>' % seq)
    BLOCK.append('        <core:hexagramName xml:lang="zh">%s</core:hexagramName>' % name)
    BLOCK.append('        <core:hasUpperTrigram rdf:resource="%s"/>' % TRIG_IRI[up])
    BLOCK.append('        <core:hasLowerTrigram rdf:resource="%s"/>' % TRIG_IRI[low])
    BLOCK.append('        <yijing:hasHexagramDictum rdf:resource="yijing:Dictum_Hex_%s"/>' % nn)
    for pos in range(1, 7):
        BLOCK.append('        <core:hasLine rdf:resource="core:Hexagram_%s_Line%d"/>' % (nn, pos))
        BLOCK.append('        <yijing:hasLineDictum rdf:resource="yijing:LineDictum_%s_%d"/>' % (nn, pos))
    BLOCK.append('    </owl:NamedIndividual>')
    BLOCK.append("")

# 反对（乾/坤）：仅乾坤，其余诸卦反对性情粒度待确认（《本体核心设计》§9-8）
BLOCK.append('    <owl:NamedIndividual rdf:about="yijing:Opposite_QianKun">')
BLOCK.append('        <rdf:type rdf:resource="yijing:OppositeNature"/>')
BLOCK.append('        <rdfs:label xml:lang="zh">乾刚坤柔</rdfs:label>')
BLOCK.append('        <yijing:oppositeNatureText xml:lang="zh">乾刚坤柔</yijing:oppositeNatureText>')
BLOCK.append('    </owl:NamedIndividual>')
BLOCK.append("")
BLOCK.append('    <owl:NamedIndividual rdf:about="core:Hexagram_01">')
BLOCK.append('        <yijing:oppositeOf rdf:resource="core:Hexagram_02"/>')
BLOCK.append('        <yijing:oppositeNatureText xml:lang="zh">乾刚坤柔</yijing:oppositeNatureText>')
BLOCK.append('    </owl:NamedIndividual>')
BLOCK.append("")
BLOCK.append('    <owl:NamedIndividual rdf:about="core:Hexagram_02">')
BLOCK.append('        <yijing:oppositeOf rdf:resource="core:Hexagram_01"/>')
BLOCK.append('        <yijing:oppositeNatureText xml:lang="zh">乾刚坤柔</yijing:oppositeNatureText>')
BLOCK.append('    </owl:NamedIndividual>')
BLOCK.append("")

new_text = prefix + "\n".join(BLOCK) + "\n</rdf:RDF>\n"
with open(ABOX, "w", encoding="utf-8", newline="\n") as f:
    f.write(new_text)

print("WROTE", ABOX)
print("hexagrams=64 lines=384")
