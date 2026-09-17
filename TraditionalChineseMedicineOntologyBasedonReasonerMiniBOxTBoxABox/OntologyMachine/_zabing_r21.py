# -*- coding: utf-8 -*-
import re, glob, os

root = r"D:\work\Ontology\TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox\ontology"
files = glob.glob(os.path.join(root, "**", "*.owl"), recursive=True)

all_classes = set()
for f in files:
    t = open(f, encoding="utf-8", errors="replace").read()
    for m in re.finditer(r'<owl:Class rdf:about="#([^"]+)"', t):
        all_classes.add(m.group(1))

targets = [
    "Dahuangzhechongwanzheng","Yiyifuzibaijiangsanzheng","Zhishutangzheng",
    "Baihezhimutangzheng","Baihejizitangzheng","Dangguisanzheng",
    "Xuanfudaizhetangzheng","Shaoyaogancaotangzheng","Baiyetangzheng",
    "Huangtutangzheng","Chixiaodoudangguisanzheng","Wenjingtangzheng",
    "Sanwuhuangqintangzheng","Wangbuliuxingsanzheng","Painongtangzheng",
    "Huanglianfenzheng","Jishibaisanzheng","Gancaofenmitangzheng",
    "Toufengmosanzheng","Guizhifulingwanzheng",
]

def find_class(name):
    for f in files:
        t = open(f, encoding="utf-8", errors="replace").read()
        m = re.search(r'<owl:Class rdf:about="#'+re.escape(name)+r'">(.*?)</owl:Class>', t, re.S)
        if m:
            return os.path.basename(f), m.group(1)
    return None, None

for name in targets:
    f, body = find_class(name)
    if body is None:
        print(f"{name}: NOT FOUND")
        continue
    lj = re.findall(r'<belongsToLiujing rdf:resource="#([^"]+)"', body)
    liujing_in_eq = re.findall(r'<owl:Class rdf:about="#((?:Taiyang|Shaoyang|Yangming|Taiyin|Shaoyin|Jueyin)bing)"', body)
    fills = re.findall(r'<owl:onProperty rdf:resource="#(you_\w+)"/>\s*<owl:someValuesFrom rdf:resource="#([^"]+)"', body, re.S)
    dangling = [v for k, v in fills if v not in all_classes]
    print(f"{name} [{f}]")
    print(f"    belongsToLiujing={lj}  eq六经={liujing_in_eq}")
    print(f"    filler={fills}")
    if dangling:
        print(f"    !! 悬空: {dangling}")
