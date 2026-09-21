# -*- coding: utf-8 -*-
"""对比 SymptomCatalog 硬编码 aliases 与 SKOS 词表，检出冲突/回归。"""
import re, io, json

ROOT = r"D:\work\Ontology\TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox"
CAT = ROOT + r"\OntologyMachine\OntologyFramework\src\main\java\com\ocean\ontologyframework\tcm\app\SymptomCatalog.java"
SKOS = ROOT + r"\ontology\tcm-zhengzhuang_skos.ttl"
OUT = ROOT + r"\OntologyMachine\_harness\_alias_diff.txt"

# --- 1. 硬编码 aliases ---
src = io.open(CAT, encoding="utf-8").read()
raw = dict(re.findall(r'raw\.put\("([^"]+)",\s*"([^"]+)"\)', src))

# --- 2. SKOS surface -> prefLabel ---
t = io.open(SKOS, encoding="utf-8").read()
blocks = re.findall(r"^zzskos:(\w+) a skos:Concept ;(.*?)(?=^zzskos:\w+ a skos:Concept ;|\Z)", t, re.S | re.M)
skos = {}
for name, b in blocks:
    m = re.search(r'skos:prefLabel\s+"([^"]+)"@zh', b)
    if not m:
        continue
    pref = m.group(1)
    skos.setdefault(pref, pref)          # prefLabel 自身也是表面形式
    for prop in ("altLabel", "hiddenLabel"):
        mm = re.search(r"skos:%s((?:\s*\"[^\"]+\"@zh\s*,?)+)\s*;" % prop, b)
        if not mm:
            continue
        for lab in re.findall(r'"([^"]+)"@zh', mm.group(1)):
            skos.setdefault(lab, pref)

L = []
L.append("硬编码 aliases: %d" % len(raw))
L.append("SKOS 表面形式(alt+hid): %d" % len(skos))
L.append("")
conflict = [(k, v, skos[k]) for k, v in raw.items() if k in skos and skos[k] != v]
L.append("== 冲突（硬编码 vs SKOS 目标不同）: %d ==" % len(conflict))
for k, a, b in sorted(conflict):
    L.append("  %-14s 硬编码→%-8s  SKOS→%s" % (k, a, b))
L.append("")
only_raw = sorted(k for k in raw if k not in skos)
L.append("== 仅硬编码有（SKOS 缺）: %d ==" % len(only_raw))
for k in only_raw:
    L.append("  %-14s → %s" % (k, raw[k]))
L.append("")
same = sorted(k for k in raw if k in skos and skos[k] == raw[k])
L.append("== 一致: %d ==" % len(same))
L.append("  " + "、".join(same))

io.open(OUT, "w", encoding="utf-8").write("\n".join(L) + "\n")
print("done")
