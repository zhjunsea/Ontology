import os, re, glob, difflib

root = r"D:\work\Ontology\TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox\ontology"
files = glob.glob(os.path.join(root, "**", "*.owl"), recursive=True)

instances = {}
classes = {}
for f in files:
    txt = open(f, encoding="utf-8").read()
    rel = os.path.relpath(f, root)
    for m in re.finditer(r'rdf:about="#([^"]+)"', txt):
        n = m.group(1)
        if n.endswith("_instance"):
            instances.setdefault(n[:-9], set()).add(rel)
        else:
            classes.setdefault(n, set()).add(rel)
    for m in re.finditer(r'rdf:ID="([^"]+)"', txt):
        n = m.group(1)
        if n.endswith("_instance"):
            instances.setdefault(n[:-9], set()).add(rel)
        else:
            classes.setdefault(n, set()).add(rel)

missing = """Shitong Murutuo Shenbuehan Sizhijuji Sini Fan Shouzuhan Xialibiannongxue
Xinzhongfan Xishumai Yanzhongshengchuang Bunengyanyu Yuyinshui Shijingjia Fanzhibuyu
Keman Mao Mianreruzui Qicongxiaofushangchongxiongyan Shifumao Weisemai Xulaoliji
Fuzhongcitong Xixishaoqi Shaofujimotong Yinyaobeitong Oubunengyinshi Shenzhuo
Ruzuoshuizhong Touzhongxuan Kujizhiyuandi""".split()

allnames = sorted(set(instances) | set(classes))
print("=== 实例总数:", len(instances), " 类总数:", len(classes), "===\n")
for name in missing:
    has_inst = name in instances
    has_cls = name in classes
    tag = "OK-inst" if has_inst else ("ONLY-CLASS" if has_cls else "MISSING")
    close = difflib.get_close_matches(name, allnames, n=4, cutoff=0.6)
    print(f"{name:35s} {tag:10s} inst_in={sorted(instances.get(name,[]))} cls_in={sorted(classes.get(name,[]))}")
    if not has_inst:
        print(f"    相近候选: {close}")
