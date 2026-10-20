# -*- coding: utf-8 -*-
"""
第 10 步：将 plumblossom-tbox.owl（单文件 TBox）按业务域拆为九个子本体
（core/div/yijing/rel/sym/ome/jud/cal/rules），plumblossom-tbox.owl 改为聚合入口。
纯文本顶层元素级拆分；按元素 rdf:about 的命名空间前缀归域；保留原 serialization。
"""
import re
from pathlib import Path

ONT = Path(r"D:/work/Ontology/OntologyMachine/OntologyFrameworkPlumBlossom/ontology")
SRC = ONT / "plumblossom-tbox.owl"
lines = SRC.read_text(encoding="utf-8").splitlines(keepends=True)
n = len(lines)

# ---- 命名空间块（第 2 行到含 xmlns:rules 的那一行）----
i_ns_end = next(i for i, l in enumerate(lines) if "xmlns:rules=" in l)
ns_block = lines[1:i_ns_end + 1]

# ---- 内容区：Ontology 元素之后 → </rdf:RDF> 之前 ----
i_onto_end = next(i for i, l in enumerate(lines) if l.strip() == "</owl:Ontology>")
i_end = max(i for i, l in enumerate(lines) if l.strip() == "</rdf:RDF>")
content = lines[i_onto_end + 1:i_end]

# ---- 顶层元素起点（恰好 4 空格缩进）----
start_re = re.compile(r'^ {4}<(owl:Class|owl:ObjectProperty|owl:DatatypeProperty|rdf:Description)\b')
starts = [i for i, l in enumerate(content) if start_re.match(l)]
assert starts, "no top-level elements found"

# ---- 每个块的域名（按首行 rdf:about 前缀）----
about_re = re.compile(r'rdf:about="([a-zA-Z0-9]+):')
DOMAINS = {
    "core":   ("plumblossom-core.owl",       "http://example.org/plumblossom/core",       "核心：卦象类体系（八卦/方位/爻/重卦）"),
    "div":    ("plumblossom-divination.owl", "http://example.org/plumblossom/divination", "起卦：起卦法与输入、四不占戒律"),
    "yijing": ("plumblossom-yijing.owl",     "http://example.org/plumblossom/yijing",     "周易经传：卦辞/爻辞/卦序/取象/反对性情"),
    "rel":    ("plumblossom-relations.owl",  "http://example.org/plumblossom/relations",  "关系：五行/生克/体用/旺衰/性情/阴阳次序"),
    "sym":    ("plumblossom-symbols.owl",    "http://example.org/plumblossom/symbols",    "类象：万物类象"),
    "ome":    ("plumblossom-omens.owl",      "http://example.org/plumblossom/omens",      "外应：三要/十应"),
    "jud":    ("plumblossom-judgement.owl",  "http://example.org/plumblossom/judgement",  "断验：事项类别/吉凶/应期/修身/一撮金"),
    "cal":    ("plumblossom-calendars.owl",  "http://example.org/plumblossom/calendars",  "历法：干支/六十甲子/纳音/长生"),
    "rules":  ("plumblossom-rules.owl",      "http://example.org/plumblossom/rules",      "SWRL 规则 R1~R14"),
}

blocks = []
for k, s in enumerate(starts):
    e = starts[k + 1] if k + 1 < len(starts) else len(content)
    m = about_re.search(content[s])
    dom = m.group(1) if m else "rules"  # 顶层 rdf:Description 无 rdf:about 时归 rules
    assert dom in DOMAINS, f"unknown domain prefix at content line {s}: {content[s].strip()[:80]}"
    blocks.append((s, e, dom))

# ---- 按域汇总（含块间注释/空白，归后随块）----
dom_text = {d: [] for d in DOMAINS}
prev = 0
for (s, e, d) in blocks:
    if prev < s:
        dom_text[d].append("".join(content[prev:s]))
    dom_text[d].append("".join(content[s:e]))
    prev = e
tail = "".join(content[prev:]).strip("\n")
if tail:
    dom_text[blocks[-1][2]].append("\n" + tail + "\n")

# 依《本体顶层设计》§4.2 的 import 拓扑（单向、无环）
IMPORTS = {
    "core":  [],
    "div":   ["core"],
    "yijing": ["core"],
    "sym":   ["core"],
    "rel":   ["core", "sym"],
    "ome":   ["core", "sym"],
    "jud":   ["rel", "sym", "ome", "yijing"],
    "cal":   [],
    "rules": ["rel", "sym", "jud"],
}

def ns_for(iri):
    out = list(ns_block)
    out[0] = f'<rdf:RDF xmlns="{iri}"\n'
    out[1] = f'         xml:base="{iri}"\n'
    return "".join(out)

xml_decl = '<?xml version="1.0" encoding="UTF-8"?>\n'
end = '</rdf:RDF>\n'

for dom, (fname, iri, desc) in DOMAINS.items():
    imp = "".join(
        f'        <owl:imports rdf:resource="{DOMAINS[d][1]}"/>\n'
        for d in IMPORTS.get(dom, [])
    )
    onto = (
        f'    <owl:Ontology rdf:about="{iri}">\n'
        f'        <rdfs:label xml:lang="zh">梅花易数 TBox 子本体 · {dom}</rdfs:label>\n'
        f'        <rdfs:comment xml:lang="zh">第 10 步产出：TBox 按业务域拆分（{desc}）。命名空间保持 http://example.org/plumblossom/{dom} 不变；由 plumblossom-tbox.owl 聚合入口统一 imports。</rdfs:comment>\n'
        f'{imp}'
        f'    </owl:Ontology>\n'
    )
    body = "".join(dom_text[dom])
    if not body.strip():
        body = "\n"
    (ONT / fname).write_text(xml_decl + ns_for(iri) + "\n" + onto + body + "\n" + end, encoding="utf-8")
    print(f"{fname}: {body.count(chr(10))} 行")

# ---- 重写 tbox.owl 为聚合入口 ----
imports = "\n".join(
    f'        <owl:imports rdf:resource="{DOMAINS[d][1]}"/>'
    for d in ["core", "div", "yijing", "rel", "sym", "ome", "jud", "cal", "rules"]
)
tbox = (
    xml_decl + ns_for("http://example.org/plumblossom/tbox") + "\n"
    + '    <owl:Ontology rdf:about="http://example.org/plumblossom/tbox">\n'
    + imports + "\n"
    + '        <rdfs:label xml:lang="zh">梅花易数 TBox 聚合入口（显式 imports 九个子本体）</rdfs:label>\n'
    + '        <rdfs:comment xml:lang="zh">第 10 步产出：TBox 聚合入口，按业务域显式导入 core/div/yijing/rel/sym/ome/jud/cal/rules 九个子本体；语义与原单文件 TBox 等价，imports 无环。</rdfs:comment>\n'
    + '    </owl:Ontology>\n\n' + end
)
(ONT / "plumblossom-tbox.owl").write_text(tbox, encoding="utf-8")
print("plumblossom-tbox.owl -> 聚合入口")
print("done")
