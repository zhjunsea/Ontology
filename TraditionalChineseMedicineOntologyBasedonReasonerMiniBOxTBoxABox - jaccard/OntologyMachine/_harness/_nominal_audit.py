#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
审计方证本体中 you_chufang 名义量（owl:hasValue）的分布形态。

用「深度感知」的方式切分 <owl:Class rdf:about="#X"> ... </owl:Class>，
正确处理 ≡ 定义内部的匿名 <owl:Class>。

对每个命名类块，找出所有含 you_chufang + owl:hasValue 的 <owl:Restriction>，
判断它出现在 ≡（equivalentClass 内）还是 ⊑（subClassOf 内），记录 filler。
"""
import re
from pathlib import Path

ONT = Path(r"D:/work/Ontology/TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox/ontology")

CLASS_OPEN = re.compile(r'<owl:Class rdf:about="#([A-Za-z0-9_]+)">')
CLASS_ANY_OPEN = re.compile(r'<owl:Class(?:\s+rdf:about="#[A-Za-z0-9_]+")?\s*>')
CLASS_CLOSE = re.compile(r'</owl:Class>')
RESTR = re.compile(
    r'<owl:Restriction>\s*'
    r'<owl:onProperty rdf:resource="#you_chufang"/>\s*'
    r'<owl:hasValue rdf:resource="#([A-Za-z0-9_]+)"/>\s*'
    r'</owl:Restriction>',
    re.S,
)
EQ_BLOCK = re.compile(r'<owl:equivalentClass>.*?</owl:equivalentClass>', re.S)
SUB_BLOCK = re.compile(r'<rdfs:subClassOf>.*?</rdfs:subClassOf>', re.S)


def named_class_blocks(text):
    """深度感知地返回 [(iri, start, end)]，仅命名类（rdf:about）。"""
    out = []
    i = 0
    n = len(text)
    while True:
        m = CLASS_ANY_OPEN.search(text, i)
        if not m:
            break
        depth = 1
        j = m.end()
        while depth > 0:
            no = CLASS_ANY_OPEN.search(text, j)
            nc = CLASS_CLOSE.search(text, j)
            if nc is None:
                j = n
                break
            if no is not None and no.start() < nc.start():
                depth += 1
                j = no.end()
            else:
                depth -= 1
                j = nc.end()
        iri_m = CLASS_OPEN.match(text, m.start())
        if iri_m:
            out.append((iri_m.group(1), m.start(), j))
        i = m.end()
    return out


def main():
    files = sorted([p for p in list(ONT.glob('fangzheng/*.owl')) + list(ONT.glob('tcm-*.owl'))
                    if '.bak' not in p.name and '_bak-' not in str(p)])
    agg = {'EQ': 0, 'SUB': 0, 'ONLY_EQ': 0, 'ONLY_SUB': 0, 'BOTH': 0}
    classes_with_nominal = 0
    fillers = set()
    for f in files:
        text = f.read_text(encoding='utf-8')
        n_eq = n_sub = only_eq = only_sub = both = 0
        for iri, s, e in named_class_blocks(text):
            blk = text[s:e]
            eq_spans = [(m.start(), m.end()) for m in EQ_BLOCK.finditer(blk)]
            sub_spans = [(m.start(), m.end()) for m in SUB_BLOCK.finditer(blk)]

            def in_spans(pos, spans):
                return any(a <= pos < b for a, b in spans)

            has_eq = has_sub = False
            for m in RESTR.finditer(blk):
                fillers.add(m.group(1))
                if in_spans(m.start(), eq_spans):
                    has_eq = True
                elif in_spans(m.start(), sub_spans):
                    has_sub = True
                else:
                    print(f"  !! {f.name} {iri}: 名义量不在 ≡/⊑ 内")
            if has_eq and has_sub:
                both += 1
            elif has_eq:
                only_eq += 1
            elif has_sub:
                only_sub += 1
            if has_eq or has_sub:
                classes_with_nominal += 1
            n_eq += 1 if has_eq else 0
            n_sub += 1 if has_sub else 0
        if n_eq or n_sub:
            print(f"{f.name:28s} 类中名义量: ≡内={n_eq:3d}  ⊑内={n_sub:3d}  (仅≡={only_eq} 仅⊑={only_sub} 两者={both})")
        agg['EQ'] += n_eq
        agg['SUB'] += n_sub
        agg['BOTH'] += both
        agg['ONLY_EQ'] += only_eq
        agg['ONLY_SUB'] += only_sub

    print()
    print(f"汇总: 含名义量的类={classes_with_nominal}  ≡出现={agg['EQ']}  ⊑出现={agg['SUB']}")
    print(f"      仅≡={agg['ONLY_EQ']}  仅⊑={agg['ONLY_SUB']}  两者都有={agg['BOTH']}")
    print(f"      不同方剂个体数={len(fillers)}")


if __name__ == '__main__':
    main()
