# -*- coding: utf-8 -*-
"""第 12 步（闸门⑪）· 将「八卦万物类占」全表（8 卦 × 27 栏）注入 plumblossom-abox.owl。

命名口径（邵康节 2026-10-20 确认）：sym:Symbol_<卦>_<维度>_<序号>；
新增缺失维度个体 sym:Dim_<维度>（天时/地理/人物/时序/动物/静物/屋舍/婚姻 已存在，另增 19 个）。
替换原 8 个代表性类象（sym:Symbol_<卦>），其取象语义已由 core:trigramSymbol 承载。
出处：原著卷之一·八卦万物类占（HWCodeArtsDir/pb_symbol_data.json）。
"""
import json
import os

HERE = os.path.dirname(os.path.abspath(__file__))
BASE = r"D:\work\Ontology\OntologyMachine\OntologyFrameworkPlumBlossom\ontology"
ABOX = os.path.join(BASE, "plumblossom-abox.owl")

with open(os.path.join(HERE, "pb_symbol_data.json"), encoding="utf-8") as f:
    DATA = json.load(f)

EXISTING_DIM = {"TianShi", "DiLi", "RenWu", "ShiXu", "DongWu", "JingWu", "WuShe", "HunYin"}

with open(ABOX, encoding="utf-8") as f:
    text = f.read()


def dim_block(loc, zh):
    return ('    <owl:NamedIndividual rdf:about="sym:Dim_%s">\n'
            '        <rdf:type rdf:resource="sym:SymbolDimension"/>\n'
            '        <rdfs:label xml:lang="zh">%s</rdfs:label>\n'
            '        <rdfs:comment xml:lang="zh">%s</rdfs:comment>\n'
            '    </owl:NamedIndividual>\n') % (loc, zh, zh)


# ---- 1) 在 sym:Dim_HunYin 之后插入新增维度个体 ----
mark = 'rdf:about="sym:Dim_HunYin"'
p = text.index(mark)
close = text.index("</owl:NamedIndividual>", p) + len("</owl:NamedIndividual>")
new_dims = []
seen = set()
for tri in DATA:
    for d in DATA[tri]:
        if d["loc"] not in EXISTING_DIM and d["loc"] not in seen:
            seen.add(d["loc"])
            new_dims.append(dim_block(d["loc"], d["zh"]))
assert len(seen) == 19, sorted(seen)
text = text[:close] + "\n" + "\n".join(new_dims) + text[close:]

# ---- 2) 用全集类象替换原 8 个代表性类象 ----
start = text.index('    <owl:NamedIndividual rdf:about="sym:Symbol_Qian">')
end = text.index("<!-- ==================== 六十四卦名录（全集）")
blocks = []
for tri in DATA:
    for d in DATA[tri]:
        for i, item in enumerate(d["items"], start=1):
            blocks.append('    <owl:NamedIndividual rdf:about="sym:Symbol_%s_%s_%02d">\n'
                          '        <rdf:type rdf:resource="sym:ImageSymbol"/>\n'
                          '        <rdfs:label xml:lang="zh">%s</rdfs:label>\n'
                          '        <sym:symbolText xml:lang="zh">%s</sym:symbolText>\n'
                          '        <sym:belongsToDimension rdf:resource="sym:Dim_%s"/>\n'
                          '    </owl:NamedIndividual>\n' % (tri, d["loc"], i, item, item, d["loc"]))
text = text[:start] + "\n".join(blocks) + "\n" + text[end:]

with open(ABOX, "w", encoding="utf-8", newline="\n") as f:
    f.write(text)

n_items = sum(len(d["items"]) for tri in DATA for d in DATA[tri])
print("WROTE", ABOX)
print("new dims=19 symbols=%d" % n_items)
