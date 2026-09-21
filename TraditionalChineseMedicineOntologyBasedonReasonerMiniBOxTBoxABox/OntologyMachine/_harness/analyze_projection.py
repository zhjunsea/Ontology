# -*- coding: utf-8 -*-
"""分析 tcm-zhengzhuang.owl 投影层：重复类声明 + 单症状 ⊑ 八纲"""
import re, sys, io, collections
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')

PATH = r"D:\work\Ontology\TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox\ontology\tcm-zhengzhuang.owl"
txt = open(PATH, encoding='utf-8').read()

# 找投影层起点
idx = txt.find('投影层')
print("投影层标记偏移:", idx)
if idx > 0:
    print("上下文:", repr(txt[idx-200:idx+120]))

# 提取所有 owl:Class 块
blocks = []
for m in re.finditer(r'<owl:Class rdf:about="#([^"]+)">(.*?)</owl:Class>', txt, re.S):
    blocks.append((m.group(1), m.group(2), m.start(), m.end()))
print("\n总 Class 块数:", len(blocks))

# 统计同名重复
names = collections.Counter(b[0] for b in blocks)
dups = {k: v for k, v in names.items() if v > 1}
print("重复声明的类数:", len(dups))
print("重复类名:", sorted(dups.keys()))

# 对每个重复类，打印两次的父类边
print("\n=== 重复类明细（原块 vs 投影块）===")
by_name = collections.defaultdict(list)
for n, body, s, e in blocks:
    by_name[n].append((s, body))
for n in sorted(dups.keys()):
    occ = by_name[n]
    print(f"\n[{n}] 出现 {len(occ)} 次")
    for i, (s, body) in enumerate(occ):
        subs = re.findall(r'<rdfs:subClassOf[^>]*rdf:resource="#([^"]+)"', body)
        label = re.search(r'<rdfs:label[^>]*>([^<]*)</rdfs:label>', body)
        cmt = re.search(r'<rdfs:comment[^>]*>([^<]*)</rdfs:comment>', body)
        print(f"  #{i+1} @off{s}: subClassOf={subs} label={label.group(1) if label else None} comment={(cmt.group(1)[:40] if cmt else None)}")

# 单症状 ⊑ 八纲 扫描
BAGANG = {'Biao','Li','Han','Re','Xu','Shi','Yin','Yang'}
print("\n=== 单症状 ⊑ 八纲 边 ===")
for n, body, s, e in blocks:
    subs = re.findall(r'<rdfs:subClassOf[^>]*rdf:resource="#([^"]+)"', body)
    hit = [x for x in subs if x in BAGANG]
    if hit:
        cmt = re.search(r'<rdfs:comment[^>]*>([^<]*)</rdfs:comment>', body)
        print(f"  {n} ⊑ {hit}   comment={(cmt.group(1)[:60] if cmt else None)}")
