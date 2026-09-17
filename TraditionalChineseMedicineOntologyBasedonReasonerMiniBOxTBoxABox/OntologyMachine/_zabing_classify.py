import os, re, glob

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

# 日志 A 类缺失名（测试用例中不存在的）
missing = """Chensemai Shenjiacuo Maishu Zuoutu Gejianyoushui Xuanji
Furenyanzhongruyouzhilian Xiongzhongkuikui Chuanxi Xiongbeitong Xiongbibudewo
Xiongbixinzhongpi Darupan Bianruxuanpan Weishumai Xinzhongfan Furenhuaishen
Yichangfu Renshenxiaobiannan Aqibuchu Xianbianhoubianxue Xianxuehoubian
Furennianwushisuo Bingxialishushiribuzhi Shaofumantong Jingshuibibuli Zangjianpibuzhi
Fuzhongxueqicitong Furenlouxia Renshenyangtai Furenruzhongxu Fanluannouni Butong
Jinchuang Jinyinchung Renbijiaozhi Maishangxiuxing Huichong Xintongfazuoyoushi
Xinzhongpiqi Dafeng Churetanxian Rukuangzhuang Toufeng LouxiaBuzhi Fuzhangman
Weixianmai""".split()

cls_only, both, neither = [], [], []
for n in sorted(set(missing)):
    has_i = n in instances
    has_c = n in classes
    if has_i: both.append(n)
    elif has_c: cls_only.append((n, sorted(classes[n]), labels.get(n, '')))
    else: neither.append(n)

print(f"=== 有实例（测试应通过，可能日志旧）({len(both)}) ===")
print(", ".join(both))
print(f"\n=== 有类无实例（需补 abox 实例）({len(cls_only)}) ===")
for n, fs, lab in cls_only:
    print(f"  {n:28s} {lab:12s} {fs}")
print(f"\n=== 完全无定义（需新建类+实例）({len(neither)}) ===")
for n in neither:
    print(f"  {n}")
