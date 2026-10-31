# -*- coding: utf-8 -*-
"""校验第 9 步拆分：三文件可解析、imports 正确、TBox∘ABox 并集与原单文件语义等价。"""
import io
from rdflib import Graph, RDF, RDFS, OWL, URIRef

ONT = r"D:/work/Ontology/OntologyMachine/OntologyFrameworkSiZhuBaZi/ontology"
SRC = ONT + "/sizhubazi-all.owl.presplit.tmp"
# 原文件备份路径
BAK = r"D:/work/Ontology/OntologyMachine/HWCodeArtsDir/sizhubazi-all.owl.presplit.bak"

def load(p):
    g = Graph()
    g.parse(p, format="xml")
    return g

orig = load(BAK)
tbox = load(ONT + "/sizhubazi-tbox.owl")
abox = load(ONT + "/sizhubazi-abox.owl")
allf = load(ONT + "/sizhubazi-all.owl")

print("orig triples:", len(orig))
print("tbox triples:", len(tbox))
print("abox triples:", len(abox))
print("all  triples:", len(allf))

# imports 检查
def imports_of(g):
    return set(str(o) for o in g.objects(None, OWL.imports))
print("abox imports:", imports_of(abox))
print("all  imports:", imports_of(allf))
print("tbox imports:", imports_of(tbox))

# ABox imports 是否被注释掉（all 不应直接 imports abox）
assert "http://example.org/sizhubazi/tbox" in imports_of(allf)
assert "http://example.org/sizhubazi/abox" not in imports_of(allf)
assert "http://example.org/sizhubazi/tbox" in imports_of(abox)

# TBox 中不应含个体（ClassAssertion 断言为类的实例）
from rdflib import BNode
abox_types = set()
for s, o in abox.subject_objects(RDF.type):
    abox_types.add((s, o))
tbox_types = set()
for s, o in tbox.subject_objects(RDF.type):
    tbox_types.add((s, o))

# 本体声明
print("tbox ontology:", list(tbox.subjects(RDF.type, OWL.Ontology)))
print("abox ontology:", list(abox.subjects(RDF.type, OWL.Ontology)))

# 组合 tbox+abox，比较除 owl:Ontology/imports 相关外与原图是否一致
def norm(g):
    s = set()
    for t in g:
        # 跳过 ontology 头（含 imports / label / comment 于 ontology 节点）
        if t[0] and (t[0], RDF.type, OWL.Ontology) in g:
            continue
        if t[1] == OWL.imports:
            continue
        s.add(t)
    return s

comb = set(tbox) | set(abox)
o = norm(orig)
c = norm(comb)
print("orig(non-head) triples:", len(o))
print("comb(non-head) triples:", len(c))
only_orig = o - c
only_comb = c - o
print("only in orig:", len(only_orig))
print("only in comb:", len(only_comb))
for t in list(only_orig)[:10]:
    print("  O:", t)
for t in list(only_comb)[:10]:
    print("  C:", t)
print("EQUIVALENT" if (len(only_orig) == 0 and len(only_comb) == 0) else "MISMATCH")
