import re, sys, io

p = r"D:\work\Ontology\TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox\ontology\tcm-zhengzhuang_skos.ttl"
out = io.StringIO()
t = open(p, encoding="utf-8").read()

blocks = re.findall(r"^zzskos:(\w+) a skos:Concept ;(.*?)(?=^zzskos:\w+ a skos:Concept ;|\Z)", t, re.S | re.M)
out.write("concepts: %d\n" % len(blocks))

want = ["睡不着", "拉肚子", "手脚冰凉", "两边肋骨下面胀痛", "肋骨下胀痛", "反酸", "想吐", "拉不消化的东西", "想拉又拉不出"]
for name, b in blocks:
    m = re.search(r'skos:prefLabel\s+"([^"]+)"@zh', b)
    pref = m.group(1) if m else None
    for w in want:
        if '"%s"@zh' % w in b:
            out.write("%-12s -> %s\n" % (w, pref))

open(r"D:\work\Ontology\TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox\OntologyMachine\_harness\_check_skos_map.txt", "w", encoding="utf-8").write(out.getvalue())

