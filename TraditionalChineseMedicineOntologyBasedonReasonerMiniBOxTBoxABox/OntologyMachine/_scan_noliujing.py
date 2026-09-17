import os, re, glob

root = r"D:\work\Ontology\TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox\ontology"
files = glob.glob(os.path.join(root, "**", "*.owl"), recursive=True)

texts = {}
for f in files:
    texts[f] = open(f, encoding="utf-8").read()

class_start = re.compile(r'<owl:Class rdf:about="#([^"]+)"')
missing = []
for f in files:
    txt = texts[f]
    rel = os.path.relpath(f, root)
    marks = [(m.start(), m.group(1)) for m in class_start.finditer(txt)]
    for i, (pos, name) in enumerate(marks):
        end = marks[i+1][0] if i+1 < len(marks) else len(txt)
        body = txt[pos:end]
        if 'rdfs:subClassOf rdf:resource="#Fangzheng"' not in body:
            continue
        if 'belongsToLiujing' in body:
            continue
        # 是否等价类里含六经命名类
        ljs = re.findall(r'<owl:Class rdf:about="#(Taiyangbing|Yangmingbing|Shaoyangbing|Taiyinbing|Shaoyinbing|Jueyinbing)"', body)
        lbl = re.search(r'<rdfs:label xml:lang="zh">([^<]*)</rdfs:label>', body)
        missing.append((rel, name, lbl.group(1) if lbl else '', sorted(set(ljs))))

print("缺 belongsToLiujing 的 Fangzheng 子类:", len(missing))
for rel, name, lbl, ljs in missing:
    print(f"  [{rel}] {name:45s} {lbl:12s} 等价类六经={ljs}")
