import os, re, glob

root = r"D:\work\Ontology\TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox\ontology"
files = glob.glob(os.path.join(root, "**", "*.owl"), recursive=True)

texts = {}
for f in files:
    texts[f] = open(f, encoding="utf-8").read()

want = """Yantong Fumai Chenweimai Weimai Shouzuleng Xialiqinggu Xiali Mianchi Lizhi Wangxue
Fanzao Budemian Ehan Danyumei Fuman Shouzujueni Jueni Ganou Hanchu Miansechi Buehan
Fuzhonghan Xinxiongdahantong Oubunengyinshi Bunengyinshi Feiweituxianmo Yiniao
Xiaobianshu Buke Shenzhuo Yaozhongleng Ruzuoshuizhong""".split()

class_start = re.compile(r'<owl:Class rdf:about="#([^"]+)"')
for f in files:
    txt = texts[f]
    rel = os.path.relpath(f, root)
    marks = [(m.start(), m.group(1)) for m in class_start.finditer(txt)]
    for i, (pos, name) in enumerate(marks):
        if name not in want:
            continue
        end = marks[i+1][0] if i+1 < len(marks) else len(txt)
        body = txt[pos:end]
        subs = re.findall(r'<rdfs:subClassOf rdf:resource="#([^"]+)"', body)
        eq = re.findall(r'owl:equivalentClass', body)
        lbl = re.search(r'<rdfs:label xml:lang="zh">([^<]*)</rdfs:label>', body)
        print(f"[{rel}] {name} ({lbl.group(1) if lbl else ''}) subClassOf={subs} eq={len(eq)}")
