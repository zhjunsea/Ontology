#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""输出 ABox 各类个体的属性集合（含样例值），供设计 MySQL 列。"""
import os
from rdflib import Graph, RDF, RDFS, OWL, URIRef

BASE = r"D:\work\Ontology\OntologyMachine\OntologyFrameworkPlumBlossom\ontology"

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

g = Graph()
g.parse(os.path.join(BASE, "plumblossom-abox.owl"), format="xml")

TARGET = {"cal:HeavenlyStem", "cal:EarthlyBranch", "cal:SexagenaryCycle", "cal:NaYin",
          "cal:LifeStage", "yijing:HexagramSequence", "yijing:HexagramDictum", "yijing:LineDictum"}

by_type = {}
for ind in g.subjects(RDF.type, OWL.NamedIndividual):
    for t in g.objects(ind, RDF.type):
        if t == OWL.NamedIndividual:
            continue
        by_type.setdefault(short(t), []).append(ind)

for t in sorted(by_type):
    if t not in TARGET:
        continue
    props = {}
    for ind in by_type[t]:
        for p, o in g.predicate_objects(ind):
            if p in (RDF.type,):
                continue
            key = short(p)
            props.setdefault(key, []).append(f"{short(ind)}={o}")
    print(f"### {t}  (n={len(by_type[t])})")
    for k in sorted(props):
        samples = props[k]
        print(f"  {k:<22} x{len(samples):<3} eg: {samples[0]}")
    print()
    print(f"  >> 样例个体: {short(by_type[t][0])}")
    for p, o in g.predicate_objects(by_type[t][0]):
        print(f"       {short(p):<22} {o}")
    print()
