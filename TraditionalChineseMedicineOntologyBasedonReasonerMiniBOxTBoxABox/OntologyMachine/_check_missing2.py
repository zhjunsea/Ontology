import os, re, glob, difflib

root = r"D:\work\Ontology\TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox\ontology"
files = glob.glob(os.path.join(root, "**", "*.owl"), recursive=True)

instances = {}
classes = {}
labels = {}
for f in files:
    txt = open(f, encoding="utf-8").read()
    rel = os.path.relpath(f, root)
    for m in re.finditer(r'<owl:Class rdf:about="#([^"]+)"[^>]*>\s*<rdfs:label xml:lang="zh">([^<]*)</rdfs:label>', txt):
        labels[m.group(1)] = m.group(2)
    for m in re.finditer(r'rdf:about="#([^"]+)"', txt):
        n = m.group(1)
        if n.endswith("_instance"):
            instances.setdefault(n[:-9], set()).add(rel)
        else:
            classes.setdefault(n, set()).add(rel)

allnames = sorted(set(instances) | set(classes))
missing = """Shitong Murutuo Shenbuehan Sizhijuji Sini Fan Shouzuhan Xialibiannongxue
Xinzhongfan Xishumai Yanzhongshengchuang Bunengyanyu Yuyinshui Shijingjia Fanzhibuyu
Keman Mao Mianreruzui Qicongxiaofushangchongxiongyan Shifumao Weisemai Xulaoliji
Fuzhongcitong Xixishaoqi Shaofujimotong Yinyaobeitong Oubunengyinshi Shenzhuo
Ruzuoshuizhong Touzhongxuan Kujizhiyuandi""".split()

for name in missing:
    close = difflib.get_close_matches(name, allnames, n=6, cutoff=0.55)
    print(f"### {name}  (inst={name in instances}, cls={name in classes})")
    for c in close:
        kind = "inst" if c in instances else "cls"
        print(f"    {c:38s} [{kind}] {labels.get(c,'')}")
    print()
