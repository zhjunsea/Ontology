#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
把方证本体中的 you_chufang 名义量公理改写为注解断言。

背景（医理依据）
----------------
原写法有二：
  (a) 方证 ⊑ ∃you_chufang.{方剂}          —— 独立的 subClassOf 名义量
  (b) 方证 ≡ 六经 ⊓ ∃症状… ⊓ ∃you_chufang.{方剂}   —— 名义量混进等价定义（仅 duli 5 例）

医理问题：
  · 方证的定义应由「六经病位 + 特征症状群」给出（方证相应：证由症候界定）。
    把「处方」写进 ≡ 定义，等于用治疗反定义证候，逻辑上循环；
    更严重的是 (b) 使患者永远无法被推为该方证——患者就诊时并无处方断言。
  · (a) 把 262 个方剂个体拖进 TBox，Openllet 对 SROIQ 名义量做分类时
    与 366 条 ≡ 定义相互作用，CLASS_HIERARCHY 由 15s 膨胀到 385s（实测）。

改法：
  删除 (a)(b) 中的 you_chufang 名义量，改为在方证类上写注解
      <chufang rdf:resource="#方剂"/>
  处方映射内容一字不改，只是从「逻辑约束」降为「元数据」——这正是
  「有是证用是方」的恰当本体表示（治疗对应关系，非定义性公理）。

用法：
  python _nominal_to_anno.py            # 试运行（只报告，不写盘）
  python _nominal_to_anno.py --apply    # 实际写盘
"""
import re
import sys
from pathlib import Path

ONT = Path(r"D:/work/Ontology/TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox/ontology")
APPLY = '--apply' in sys.argv

CLASS_ANY_OPEN = re.compile(r'<owl:Class(?:\s+rdf:about="#[A-Za-z0-9_]+")?\s*>')
CLASS_OPEN = re.compile(r'<owl:Class rdf:about="#([A-Za-z0-9_]+)">')
CLASS_CLOSE = re.compile(r'</owl:Class>')
RESTR = re.compile(
    r'<owl:Restriction>\s*'
    r'<owl:onProperty rdf:resource="#you_chufang"/>\s*'
    r'<owl:hasValue rdf:resource="#([A-Za-z0-9_]+)"/>\s*'
    r'</owl:Restriction>',
    re.S,
)
SUB_BLOCK = re.compile(r'[ \t]*<rdfs:subClassOf>\s*<owl:Restriction>\s*'
                       r'<owl:onProperty rdf:resource="#you_chufang"/>\s*'
                       r'<owl:hasValue rdf:resource="#[A-Za-z0-9_]+"/>\s*'
                       r'</owl:Restriction>\s*</rdfs:subClassOf>[ \t]*\r?\n', re.S)
LABEL_LINE = re.compile(r'[ \t]*<rdfs:label[^>]*>[^<]*</rdfs:label>[ \t]*\r?\n')


def named_class_blocks(text):
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


def transform_block(blk, iri, report):
    """返回改写后的块。"""
    fillers = sorted({m.group(1) for m in RESTR.finditer(blk)})
    if not fillers:
        return blk, 0, 0
    if len(fillers) > 1:
        report.append(f"  !! {iri}: 多个方剂 {fillers}，跳过")
        return blk, 0, 0
    filler = fillers[0]

    # 1) 删除独立的 ⊑ 名义量公理
    n_sub = len(SUB_BLOCK.findall(blk))
    blk2 = SUB_BLOCK.sub('', blk)

    # 2) 删除 ≡ 定义内的名义量操作数（整行删除）
    eq_lines = 0
    out_lines = []
    for line in blk2.split('\n'):
        if RESTR.search(line) and '<rdfs:subClassOf>' not in line:
            eq_lines += 1
            continue
        out_lines.append(line)
    blk2 = '\n'.join(out_lines)

    # 3) 插入注解，置于 rdfs:label 之后
    anno = f'        <chufang rdf:resource="#{filler}"/>\n'
    m = LABEL_LINE.search(blk2)
    if m:
        blk2 = blk2[:m.end()] + anno + blk2[m.end():]
    else:
        mo = CLASS_OPEN.match(blk2)
        blk2 = blk2[:mo.end()] + '\n' + anno + blk2[mo.end():]

    return blk2, n_sub, eq_lines


def main():
    files = sorted([p for p in list(ONT.glob('fangzheng/*.owl')) + list(ONT.glob('tcm-*.owl'))
                    if '.bak' not in p.name and '_bak-' not in str(p)])
    grand_sub = grand_eq = grand_cls = 0
    report = []
    for f in files:
        text = f.read_text(encoding='utf-8')
        blocks = named_class_blocks(text)
        if not blocks:
            continue
        # 从后往前替换，避免偏移错乱
        new_text = text
        n_sub = n_eq = n_cls = 0
        for iri, s, e in reversed(blocks):
            blk = new_text[s:e]
            nb, a, b = transform_block(blk, iri, report)
            if a or b:
                new_text = new_text[:s] + nb + new_text[e:]
                n_sub += a
                n_eq += b
                n_cls += 1
        if n_cls:
            print(f"{f.name:28s} 改写类={n_cls:3d}  删⊑名义量={n_sub:3d}  删≡操作数={n_eq:3d}")
            grand_sub += n_sub
            grand_eq += n_eq
            grand_cls += n_cls
            if APPLY:
                f.write_text(new_text, encoding='utf-8')

    for r in report:
        print(r)
    print()
    print(f"合计: 改写类={grand_cls}  删⊑名义量={grand_sub}  删≡操作数={grand_eq}")
    print("已写盘" if APPLY else "试运行（未写盘）")


if __name__ == '__main__':
    main()
