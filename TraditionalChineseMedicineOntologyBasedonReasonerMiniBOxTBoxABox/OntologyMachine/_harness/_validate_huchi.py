# -*- coding: utf-8 -*-
"""
互斥对安全性校验：
  1. 给定候选互斥对，计算其全部子类闭包；
  2. 扫描所有「有等价定义」的类（方证 / 舌象复合 / 判据类等），
     若某定义同时要求两个互斥填充符（AND 语义），则该类将变为不可满足 → 报冲突。
"""
import re, os, sys, io, collections
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')
ONT = r"D:/work/Ontology/TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox/ontology"

def read(p):
    with open(p, encoding='utf-8') as f: return f.read()

FILES = ["tcm-core.owl","tcm-zhengzhuang.owl","tcm-maixiang.owl","tcm-shexiang.owl",
         "tcm-fuzheng.owl","fangzheng/taiyang.owl","fangzheng/shaoyang_yangming.owl",
         "fangzheng/shaoyin_taiyin.owl","fangzheng/jueyin.owl","fangzheng/zabing.owl",
         "fangzheng/duli.owl","fangzheng/hebing.owl","fangzheng/fanggen.owl",
         "fangzheng/jianjia.owl","fangzheng/rules.owl"]

parents, labels, defs = collections.defaultdict(set), {}, {}
for cf in FILES:
    p = os.path.join(ONT, cf)
    if not os.path.exists(p): continue
    txt = read(p)
    for m in re.finditer(r'<owl:Class rdf:about="#([A-Za-z0-9_]+)">(.*?)</owl:Class>', txt, re.S):
        frag, body = m.group(1), m.group(2)
        for pm in re.finditer(r'<rdfs:subClassOf rdf:resource="#([A-Za-z0-9_]+)"', body):
            parents[frag].add(pm.group(1))
        lm = re.search(r'<rdfs:label xml:lang="zh">([^<]+)</rdfs:label>', body)
        if lm: labels[frag] = lm.group(1)
        if '<owl:equivalentClass>' in body or '<owl:Restriction>' in body:
            defs[frag] = set(re.findall(r'<owl:someValuesFrom rdf:resource="#([A-Za-z0-9_]+)"', body))

def closure(frag):
    seen, stack = set(), [frag]
    while stack:
        c = stack.pop()
        for p in parents.get(c, ()):
            if p not in seen: seen.add(p); stack.append(p)
    return seen

# ---- 候选互斥对 ----
PAIRS = [
    # 症状
    ("Ehan","Buehan"), ("Hanchu","Wuhan"), ("Kouke","Buke"), ("Tu","Butu"),
    ("Ou","Buou"), ("Yuyin","Buyuyin"), ("Dare","Wudare"), ("Xiali","Budabian"),
    # 脉象
    ("Fumai","Chenmai"), ("Shumai","Chimai"), ("Huamai","Semai"), ("Xumai","Shimai"),
    ("Changmai","Duanmai"), ("Damai","Xiaomai"), ("Hongmai","Weimai"), ("Fumai","Fumai_Yin"),
    # 舌色（单值）
    ("PaleRedTongue","PaleWhiteTongue"), ("PaleRedTongue","RedTongue"),
    ("PaleRedTongue","CrimsonTongue"), ("PaleRedTongue","PurpleTongue"),
    ("PaleRedTongue","BlueTongue"), ("PaleWhiteTongue","RedTongue"),
    ("PaleWhiteTongue","CrimsonTongue"), ("PaleWhiteTongue","PurpleTongue"),
    ("PaleWhiteTongue","BlueTongue"), ("RedTongue","CrimsonTongue"),
    ("RedTongue","PurpleTongue"), ("RedTongue","BlueTongue"),
    ("CrimsonTongue","PurpleTongue"), ("CrimsonTongue","BlueTongue"),
    ("PurpleTongue","BlueTongue"),
    # 苔色（单值）
    ("WhiteCoating","YellowCoating"), ("WhiteCoating","GreyCoating"),
    ("WhiteCoating","BlackCoating"), ("YellowCoating","GreyCoating"),
    ("YellowCoating","BlackCoating"), ("GreyCoating","BlackCoating"),
    # 苔质
    ("ThinCoating","ThickCoating"), ("SlipperyCoating","DryCoating"),
    ("TongueNoCoating","ThickCoating"), ("TongueNoCoating","ThinCoating"),
    # 舌面润燥
    ("MoistTongue","DryTongue"), ("SlipperyTongue","DryTongue"),
    ("MoistTongue","RoughTongue"), ("SlipperyTongue","RoughTongue"),
    # 舌形/舌态
    ("SwollenTongue","ThinTongue"), ("StiffTongue","FlaccidTongue"),
    # 腹证
    ("Antong","Anzhibutong"), ("Anzhiru","Anzhishiying"),
]

# 校验 fragment 存在
missing = [f for pr in PAIRS for f in pr if f not in labels]
if missing:
    print("!! 不存在的 fragment:", sorted(set(missing)))

# 展开子类闭包
def expand(f):
    return closure(f) | {f}
pairs_exp = [(expand(a), expand(b), a, b) for a, b in PAIRS]

print(f"互斥对: {len(PAIRS)} 对")
print("\n===== 冲突检查：定义同时要求两个互斥填充符的类 =====")
conflicts = 0
for frag, fillers in sorted(defs.items()):
    for fa, fb, a, b in pairs_exp:
        # 定义中同时出现 a 侧与 b 侧的填充符
        sa = fillers & fa
        sb = fillers & fb
        if sa and sb:
            print(f"  [冲突] {frag}({labels.get(frag,'')}) 需 {sorted(sa)} 与 {sorted(sb)}（互斥 {a}⊥{b}）")
            conflicts += 1
if not conflicts:
    print("  无冲突 ✓")
