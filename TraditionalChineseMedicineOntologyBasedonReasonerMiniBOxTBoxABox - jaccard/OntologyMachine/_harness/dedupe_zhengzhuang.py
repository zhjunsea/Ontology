# -*- coding: utf-8 -*-
"""去重 tcm-zhengzhuang.owl 投影层重复类声明（RDF 等价：把重复块的 subClassOf 边并入首个块，删除重复块）"""
import re, sys, io, collections, shutil, os
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')

PATH = r"D:\work\Ontology\TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox\ontology\tcm-zhengzhuang.owl"
BAK  = r"D:\work\Ontology\TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox\bak\session-20260919\tcm-zhengzhuang.owl.pre-dedupe"
t = open(PATH, encoding='utf-8').read()

BLK = re.compile(r'<owl:Class rdf:about="#([^"]+)">(.*?)</owl:Class>', re.S)

# 收集所有块
blocks = [(m.group(1), m.group(2), m.start(), m.end()) for m in BLK.finditer(t)]

def edges(body):
    return re.findall(r'<rdfs:subClassOf rdf:resource="#([^"]+)"', body)

# 统计去重前的 (类, 父类) 集合
before_pairs = collections.Counter()
for n, b, s, e in blocks:
    for p in edges(b):
        before_pairs[(n, p)] += 1
before_classes = set(n for n, *_ in blocks)
print("去重前: 类块数=%d, 不同类数=%d, (类,父类)边数=%d" % (len(blocks), len(before_classes), len(before_pairs)))

# 按类名分组
by_name = collections.defaultdict(list)
for n, b, s, e in blocks:
    by_name[n].append((s, e, b))

dups = {k: v for k, v in by_name.items() if len(v) > 1}
print("重复类数:", len(dups))

# 构造新文件：对重复类，把后续块的 subClassOf 边并入首块，删除后续块
# 做法：先算出每个重复类首块的新 body，再按位置重建文本
replacements = []   # (start, end, new_text)
for n, occ in dups.items():
    occ_sorted = sorted(occ, key=lambda x: x[0])
    first_s, first_e, first_b = occ_sorted[0]
    # 收集所有边（去重、保序）
    all_edges = []
    for _, _, b in occ_sorted:
        for p in edges(b):
            if p not in all_edges:
                all_edges.append(p)
    # 重建首块 body：在原有 body 基础上，把缺失的 subClassOf 边插到 </owl:Class> 前
    new_b = first_b
    existing = edges(first_b)
    add = [p for p in all_edges if p not in existing]
    if add:
        ins = ''.join('<rdfs:subClassOf rdf:resource="#%s"/>' % p for p in add)
        new_b = new_b + ins
    replacements.append((first_s, first_e, '<owl:Class rdf:about="#%s">%s</owl:Class>' % (n, new_b)))
    # 删除后续块
    for s, e, b in occ_sorted[1:]:
        replacements.append((s, e, ''))

# 按位置从后往前替换
replacements.sort(key=lambda x: x[0], reverse=True)
out = t
for s, e, txt in replacements:
    out = out[:s] + txt + out[e:]

# 校验
blocks2 = [(m.group(1), m.group(2)) for m in BLK.finditer(out)]
after_pairs = collections.Counter()
for n, b in blocks2:
    for p in edges(b):
        after_pairs[(n, p)] += 1
after_classes = set(n for n, _ in blocks2)
print("去重后: 类块数=%d, 不同类数=%d, (类,父类)边数=%d" % (len(blocks2), len(after_classes), len(after_pairs)))
print("类集合一致:", before_classes == after_classes)
print("边集合一致:", set(before_pairs) == set(after_pairs))
missing = set(before_pairs) - set(after_pairs)
extra = set(after_pairs) - set(before_pairs)
print("缺失边:", missing)
print("多余边:", extra)

if before_classes == after_classes and set(before_pairs) == set(after_pairs):
    shutil.copy2(PATH, BAK)
    open(PATH, 'w', encoding='utf-8').write(out)
    print("\n[OK] 已写回，备份:", BAK)
    print("新文件大小:", len(out), "原:", len(t))
else:
    print("\n[ABORT] 校验不通过，未写回")
