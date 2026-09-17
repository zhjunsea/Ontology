import os, re, glob

root = r"D:\work\Ontology\TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox\ontology"
files = glob.glob(os.path.join(root, "**", "*.owl"), recursive=True)

# 收集实例及其 label / 类型
inst = {}   # name -> (label, type, file)
for f in files:
    txt = open(f, encoding="utf-8").read()
    rel = os.path.relpath(f, root)
    # 逐个 individual 块
    for m in re.finditer(r'<owl:NamedIndividual rdf:about="#([A-Za-z0-9_]+)_instance">(.*?)</owl:NamedIndividual>', txt, re.S):
        name = m.group(1)
        body = m.group(2)
        lbl = re.search(r'<rdfs:label xml:lang="zh">([^<]*)</rdfs:label>', body)
        typ = re.search(r'<rdf:type rdf:resource="#([^"]+)"', body)
        inst[name] = (lbl.group(1) if lbl else "", typ.group(1) if typ else "", rel)

keywords = """痛 寒 热 烦 渴 饮 利 血 脓 疮 咽 语 言 声 眩 重 冒 满 急 气 腰 背 少腹
小便 四肢 手足 身 面 目 咳 呕 吐 虚 劳 精 涩 数 细 微 弦 沉 浮 弱 濡""".split()

print("总实例:", len(inst))
for kw in keywords:
    hits = [(n, l, t) for n, (l, t, fl) in inst.items() if kw in l]
    if not hits: continue
    print(f"\n===== 含「{kw}」的实例 ({len(hits)}) =====")
    for n, l, t in sorted(hits, key=lambda x: x[1]):
        print(f"  {n:42s} {l}   [{t}]")
