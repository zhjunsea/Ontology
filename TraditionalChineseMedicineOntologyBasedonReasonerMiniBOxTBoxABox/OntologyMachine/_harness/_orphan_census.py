# -*- coding: utf-8 -*-
"""普查 tcm-zhengzhuang 的「孤儿类」（TBox 有类、ABox 无个体）与「重复个体」（同 label 多个个体）。

用法: python _orphan_census.py
"""
import io, re, sys, collections
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')

BASE = r"D:/work/Ontology/TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox/ontology"
TBOX = BASE + "/tcm-zhengzhuang.owl"
ABOX = BASE + "/tcm-zhengzhuang-abox.owl"

# 抽象类（八纲/六经等，非本模块症状）
ABSTRACT = {"Zhengzhuang", "Biao", "Li", "Yin", "Yang", "Xu", "Shi", "Re", "Hanxiang",
            "Xiaobian", "ZhengzhuangLei"}

tbox = io.open(TBOX, encoding="utf-8").read()
abox = io.open(ABOX, encoding="utf-8").read()

# ---- TBox 类 ----
CLS = re.compile(r'<owl:Class\s+rdf:about="#([^"]+)"\s*>(.*?)</owl:Class>', re.S)
LAB = re.compile(r'<rdfs:label\s+xml:lang="zh">([^<]*)</rdfs:label>')
classes = {}   # name -> label
for m in CLS.finditer(tbox):
    name, body = m.group(1), m.group(2)
    lm = LAB.search(body)
    classes[name] = lm.group(1).strip() if lm else ""

# ---- ABox 个体 ----
IND = re.compile(r'<owl:NamedIndividual\s+rdf:about="#([^"]+)"\s*>(.*?)</owl:NamedIndividual>', re.S)
individuals = {}   # name -> label
for m in IND.finditer(abox):
    name, body = m.group(1), m.group(2)
    lm = LAB.search(body)
    individuals[name] = lm.group(1).strip() if lm else ""

bases = set(k[:-len("_instance")] for k in individuals if k.endswith("_instance"))

# ---- 孤儿类 ----
orphans = sorted(k for k in classes if k not in bases and k not in ABSTRACT)
print("TBox 类总数:", len(classes))
print("ABox 个体总数:", len(individuals))
print("ABox 基名总数:", len(bases))
print()
print("=== 孤儿类（TBox 有类、ABox 无 #<Base>_instance 个体）: %d ===" % len(orphans))
for k in orphans:
    print("  %-40s %s" % (k, classes[k]))

# ---- 重复 label（多个个体同 label） ----
by_label = collections.defaultdict(list)
for k, v in individuals.items():
    if v:
        by_label[v].append(k)
dups = {k: v for k, v in by_label.items() if len(v) > 1}
print()
print("=== 重复 label（多个个体同 label）: %d ===" % len(dups))
for k, v in sorted(dups.items()):
    print("  %-20s -> %s" % (k, v))

# ---- 个体 fragment 与 rdf:type 类不匹配（typo 检测） ----
print()
print("=== fragment 与 rdf:type 不匹配（疑似 typo） ===")
TYPE = re.compile(r'<rdf:type\s+rdf:resource="#([^"]+)"')
n_mis = 0
for m in IND.finditer(abox):
    name, body = m.group(1), m.group(2)
    tm = TYPE.search(body)
    if not tm:
        continue
    typ = tm.group(1)
    if name.endswith("_instance"):
        base = name[:-len("_instance")]
        if base != typ:
            print("  %-45s rdf:type=#%s" % (name, typ))
            n_mis += 1
print("  合计:", n_mis)
