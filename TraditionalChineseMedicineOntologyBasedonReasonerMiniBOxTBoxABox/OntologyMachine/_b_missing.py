# -*- coding: utf-8 -*-
"""可靠提取：按顶层 owl:Class 块切分，块内找 you_chufang hasValue。"""
import re, os, glob, json

ONT = '../ontology'
FZ_DIR = os.path.join(ONT, 'fangzheng')
FANGJI_ABOX = os.path.join(ONT, 'tcm-fangji-abox.owl')


def read(p):
    with open(p, encoding='utf-8') as f:
        return f.read()


def extract_individuals(text):
    out = {}
    for m in re.finditer(r'<owl:NamedIndividual\s+rdf:about="#([^"]+)"(.*?)</owl:NamedIndividual>',
                         text, re.S):
        iri, body = m.group(1), m.group(2)
        lm = re.search(r'<rdfs:label[^>]*>([^<]+)</rdfs:label>', body)
        out[iri] = lm.group(1).strip() if lm else None
    for m in re.finditer(r'<owl:NamedIndividual\s+rdf:about="#([^"]+)"\s*/>', text):
        out.setdefault(m.group(1), None)
    return out


def top_classes(text):
    """返回 [(cls_name, block_text)]，仅顶层类（行首4空格缩进）。"""
    starts = [(m.start(), m.group(1)) for m in
              re.finditer(r'^    <owl:Class\s+rdf:about="#([^"]+)">', text, re.M)]
    res = []
    for i, (pos, name) in enumerate(starts):
        end = starts[i + 1][0] if i + 1 < len(starts) else len(text)
        res.append((name, text[pos:end]))
    return res


def block_label(block):
    lm = re.search(r'<rdfs:label[^>]*>([^<]+)</rdfs:label>', block)
    return lm.group(1).strip() if lm else None


def block_chufang(block):
    """块内所有 you_chufang 的 hasValue（去重）。"""
    out = []
    for m in re.finditer(r'#you_chufang"/>\s*<owl:hasValue\s+rdf:resource="#([^"]+)"', block, re.S):
        out.append(m.group(1))
    return list(dict.fromkeys(out))


fangji = extract_individuals(read(FANGJI_ABOX))
fangji_by_label = {}
for iri, lab in fangji.items():
    if lab:
        fangji_by_label.setdefault(lab, []).append(iri)

print('方剂个体数:', len(fangji))

rows, total = [], 0
for path in sorted(glob.glob(os.path.join(FZ_DIR, '*.owl'))):
    fname = os.path.basename(path)
    text = read(path)
    for cls, block in top_classes(text):
        lab = block_label(block)
        if lab is None:
            continue  # 家族类等无 label 的跳过
        for chufang in block_chufang(block):
            total += 1
            if chufang in fangji:
                continue
            # 方证名去掉尾部"证"再匹配方剂名
            base = lab[:-1] if lab.endswith('证') else lab
            cand = fangji_by_label.get(base, [])
            rows.append({
                'file': fname, 'fangzheng': cls, 'chufang_iri': chufang,
                'fangzheng_label': lab, 'base_label': base,
                'reuse_candidates': cand
            })

print('方证→方剂引用总数:', total)
print('未按 IRI 命中:', len(rows))

reusable = [r for r in rows if r['reuse_candidates']]
truly = [r for r in rows if not r['reuse_candidates']]
print('\n可复用（中文名命中）:', len(reusable))
for r in reusable:
    print('  ', r['file'], r['fangzheng'], r['chufang_iri'], '->', r['reuse_candidates'])
print('\n真实缺失（中文名未命中）:', len(truly))
for r in truly:
    print('  ', r['file'], r['fangzheng'], r['chufang_iri'], r['fangzheng_label'])

with open('_b_missing.json', 'w', encoding='utf-8') as f:
    json.dump(rows, f, ensure_ascii=False, indent=1)
