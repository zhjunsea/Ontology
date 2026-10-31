# -*- coding: utf-8 -*-
"""向 12 子本体与 abox 注入 skos:exactMatch → term（Example 方向；第13步）"""
import re, os, shutil

BASE = r"D:/work/Ontology/OntologyMachine/OntologyFrameworkZiWeiDouShu/ontology"
MAP = r"D:/work/Ontology/OntologyMachine/HWCodeArtsDir/_zwds_term_map.tsv"
MAP_IND = r"D:/work/Ontology/OntologyMachine/HWCodeArtsDir/_zwds_term_map_ind.tsv"
DNS = "http://example.org/ziweidoushu/%s#"
DOMAINS = ["core","calendar","chart","star","layout","state","palace",
           "pattern","fortune","reading","diagnosis","rules"]

def load_map(path):
    m = {}
    with open(path, encoding="utf-8") as fh:
        for ln in fh:
            ln = ln.rstrip("\n")
            if not ln: continue
            o, t = ln.split("\t")
            m[o] = t
    return m

def add_em(line, owl_iri, term_iri):
    em = '<skos:exactMatch rdf:resource="%s"/>' % term_iri
    if em in line or ('rdf:about="%s"' % owl_iri) not in line:
        return line
    m = re.search(r'(<([A-Za-z0-9_:]+)([^>]*rdf:about="%s"[^>]*)>)(.*?)(</\2>)' % re.escape(owl_iri), line)
    if m:
        return line.replace(m.group(0), m.group(1) + m.group(4) + em + m.group(5), 1)
    m = re.search(r'<([A-Za-z0-9_:]+)([^>]*rdf:about="%s"[^>]*)/>' % re.escape(owl_iri), line)
    if m:
        return line.replace(m.group(0), '<%s%s>%s</%s>' % (m.group(1), m.group(2), em, m.group(1)), 1)
    return line

def process(path, mapping, is_abox=False, backup_suffix=".bak13"):
    with open(path, encoding="utf-8") as fh:
        lines = fh.readlines()
    hits = 0
    out = []
    for ln in lines:
        new = ln
        for owl_iri, term_iri in mapping.items():
            if 'rdf:about="%s"' % owl_iri in new:
                n2 = add_em(new, owl_iri, term_iri)
                if n2 != new:
                    hits += 1
                    new = n2
        out.append(new)
    shutil.copyfile(path, path + backup_suffix)
    with open(path, "w", encoding="utf-8") as fh:
        fh.writelines(out)
    return hits

main_map = load_map(MAP)
ind_map = load_map(MAP_IND)

total = 0
for dom in DOMAINS:
    fname = "ziweidoushu-%s.owl" % dom
    path = os.path.join(BASE, fname)
    sub = {k: v for k, v in main_map.items() if k.startswith(DNS % dom)}
    if not sub:
        continue
    h = process(path, sub)
    total += h
    print("%-24s +%d" % (fname, h))

h = process(os.path.join(BASE, "ziweidoushu-abox.owl"), ind_map)
total += h
print("%-24s +%d (star individuals)" % ("ziweidoushu-abox.owl", h))
print("TOTAL exactMatch injected:", total)
