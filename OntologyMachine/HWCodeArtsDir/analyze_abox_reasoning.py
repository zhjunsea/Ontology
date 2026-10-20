#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""分析 plumblossom ABox 个体的「推理参与面」：
   - SWRL 规则体引用的类 / 属性（body atoms）
   - ABox 个体按类型分组
   - 判定每个个体是否参与推理
判定规则：
  参与推理 当且仅当：
    (a) 该个体的某个 rdf:type 类出现在 SWRL 规则的 classPredicate 中；或
    (b) 该个体断言了某个属性（作为主语），该属性出现在 SWRL 规则的 propertyPredicate 中；或
    (c) 该个体被某属性（出现在规则中）指向（作为宾语）。
"""
import os, glob
from rdflib import Graph, RDF, RDFS, OWL, URIRef
from rdflib.namespace import Namespace
from rdflib.collection import Collection

BASE = r"D:\work\Ontology\OntologyMachine\OntologyFrameworkPlumBlossom\ontology"
SWRL = Namespace("http://www.w3.org/2003/11/swrl#")

def short(uri):
    s = str(uri)
    for ns in ("http://example.org/plumblossom/rules#", "http://example.org/plumblossom/core/",
               "http://example.org/plumblossom/divination/", "http://example.org/plumblossom/yijing/",
               "http://example.org/plumblossom/relations/", "http://example.org/plumblossom/symbols/",
               "http://example.org/plumblossom/omens/", "http://example.org/plumblossom/judgement/",
               "http://example.org/plumblossom/calendars/"):
        if s.startswith(ns):
            return s[len(ns):]
    return s

# 1) 解析 SWRL 规则：收集类谓词、属性谓词
g_rules = Graph()
rules_file = os.path.join(BASE, "plumblossom-rules.owl")
g_rules.parse(rules_file, format="xml")

rule_classes, rule_props = set(), set()
for imp in g_rules.subjects(RDF.type, SWRL.Imp):
    body = g_rules.value(imp, SWRL.body)
    atoms = Collection(g_rules, body)
    for atom in atoms:
        cp = g_rules.value(atom, SWRL.classPredicate)
        if cp is not None:
            rule_classes.add(short(cp))
        pp = g_rules.value(atom, SWRL.propertyPredicate)
        if pp is not None:
            rule_props.add(short(pp))
print("SWRL 规则体类谓词:", sorted(rule_classes))
print("SWRL 规则体属性谓词:", sorted(rule_props))
print()

# 2) 解析 ABox：按类型分组
g_abox = Graph()
g_abox.parse(os.path.join(BASE, "plumblossom-abox.owl"), format="xml")

# 收集被规则属性指向的宾语个体
target_individuals = set()
for s, p, o in g_abox:
    if short(p) in rule_props and isinstance(o, URIRef):
        target_individuals.add(short(o))

by_type = {}
for ind in g_abox.subjects(RDF.type, OWL.NamedIndividual):
    types = [short(t) for t in g_abox.objects(ind, RDF.type) if t != OWL.NamedIndividual]
    for t in types:
        by_type.setdefault(t, []).append(short(ind))

# 3) 判定
print(f"{'类型':<24}{'总数':>4}  {'参与推理':>8}  {'不参与':>6}")
participating, non_participating = [], []
for t in sorted(by_type):
    part, nonp = [], []
    for local in by_type[t]:
        iri = None
        # 找到该个体的完整 uri
        for s in g_abox.subjects(RDF.type, OWL.NamedIndividual):
            if short(s) == local:
                iri = s
                break
        participates = (t in rule_classes) or (local in target_individuals)
        if not participates:
            # 检查其断言的属性是否在规则属性集
            for p, o in g_abox.predicate_objects(iri):
                if short(p) in rule_props:
                    participates = True
                    break
        (part if participates else nonp).append(local)
    participating += part
    non_participating += nonp
    print(f"{t:<24}{len(by_type[t]):>4}  {len(part):>8}  {len(nonp):>6}")

print()
print(f"合计个体: {sum(len(v) for v in by_type.values())}")
print(f"参与推理: {len(participating)}")
print(f"不参与推理: {len(non_participating)}")
print()
print("== 不参与推理的个体 ==")
for t in sorted(by_type):
    for local in by_type[t]:
        if local in non_participating:
            print(f"  [{t}] {local}")
