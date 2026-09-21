# -*- coding: utf-8 -*-
"""导出 脉象/舌象/腹证 ABox 个体（fragment -> label），供构建 SKOS 使用。"""
import io, re, json

BASE = r"D:/work/Ontology/TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox/ontology"
HARN = r"D:/work/Ontology/TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox/OntologyMachine/_harness"
FILES = {
    "MAIXIANG": "tcm-maixiang-abox.owl",
    "SHEXIANG": "tcm-shexiang-abox.owl",
    "FUZHENG": "tcm-fuzheng-abox.owl",
}
IND = re.compile(r'<owl:NamedIndividual\s+rdf:about="#([^"]+)"\s*>(.*?)</owl:NamedIndividual>', re.S)
LAB = re.compile(r'<rdfs:label\s+xml:lang="zh">([^<]*)</rdfs:label>')

out = {}
lines = []
for cat, fn in FILES.items():
    t = io.open(BASE + "/" + fn, encoding="utf-8").read()
    items = []
    for m in IND.finditer(t):
        name, body = m.group(1), m.group(2)
        lm = LAB.search(body)
        items.append((name, lm.group(1).strip() if lm else ""))
    out[cat] = items
    lines.append("=== %s (%d) ===" % (cat, len(items)))
    for n, l in items:
        lines.append("  %-28s %s" % (n, l))
    lines.append("")

io.open(HARN + "/_mlfz_dump.txt", "w", encoding="utf-8", newline="\n").write("\n".join(lines) + "\n")
json.dump(out, io.open(HARN + "/_mlfz_dump.json", "w", encoding="utf-8"), ensure_ascii=False, indent=1)
print("OK", {k: len(v) for k, v in out.items()})
