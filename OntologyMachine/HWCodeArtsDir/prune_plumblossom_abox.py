#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""从 plumblossom-abox.owl 移除已入库的 116 个个体（历法 38 + 周易文本 78），
保留 129 个结构性/推理所需个体。文本级删除，保留其余格式。"""
import re, os
from rdflib import Graph, RDF, OWL

P = r"D:\work\Ontology\OntologyMachine\OntologyFrameworkPlumBlossom\ontology\plumblossom-abox.owl"

def remove(iri):
    if iri.startswith("cal:"):
        return True
    if iri.startswith("yijing:"):
        loc = iri.split(":", 1)[1]
        return loc.startswith("Seq_") or loc.startswith("Dictum_") or loc.startswith("LineDictum_")
    return False

text = open(P, encoding="utf-8").read()
pat = re.compile(r'\n[ \t]*<owl:NamedIndividual rdf:about="([^"]+)">.*?</owl:NamedIndividual>', re.DOTALL)

removed = []
def repl(m):
    if remove(m.group(1)):
        removed.append(m.group(1))
        return ""
    return m.group(0)

new = pat.sub(repl, text)
print("removed count:", len(removed))
print("sample removed:", removed[:3], "...", removed[-3:])

# 更新本体注释
new = new.replace(
    "<rdfs:label xml:lang=\"zh\">梅花易数 ABox（典型个体：六十四卦名录 / 八卦 / 四不占 / 吉凶等级与事项类别 / 干支等）</rdfs:label>",
    "<rdfs:label xml:lang=\"zh\">梅花易数 ABox（本体侧保留：八卦 / 方位 / 五行生克 / 旺衰 / 卦气性情 / 类象 / 四不占 / 吉凶等级与事项类别 / 一撮金等级 / 十应三要 / 乾坤爻与反对 等结构性、推理所需个体）</rdfs:label>")
new = new.replace(
    "<rdfs:comment xml:lang=\"zh\">第 9 步产出：由原 plumblossom-all.owl 拆分。含 247 个代表性典型个体（六十四卦全名录、卦辞爻辞全集留待第 12 步扩全集）。依赖 TBox 中的类与属性，故显式 imports tbox。</rdfs:comment>",
    "<rdfs:comment xml:lang=\"zh\">第 9 步拆分产出；第 11 步按闸门⑩裁决将「历法（干支/六十甲子/纳音/长生）」与「周易文本（六十四卦名/卦辞/爻辞）」共 116 个体迁出，入 MySQL（plumblossomdb，经 OBDA plumBlossom.obda 虚拟访问），DB 为唯一真源；本文件仅保留 129 个结构性、推理所需个体。依赖 TBox 中的类与属性，故显式 imports tbox。</rdfs:comment>")

open(P, "w", encoding="utf-8", newline="\n").write(new)

g = Graph(); g.parse(P, format="xml")
n = sum(1 for _ in g.subjects(RDF.type, OWL.NamedIndividual))
print("abox individuals now:", n)
