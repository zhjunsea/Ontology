# -*- coding: utf-8 -*-
"""分析 rules.owl 的条件 token 与本体类的对应关系，为药证本体生成做依据核验。"""
import re, os, sys, io
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')

ONT = r"D:/work/Ontology/TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox/ontology"

def read(p):
    with open(p, encoding='utf-8') as f:
        return f.read()

# 1. 收集本体中所有具名类 IRI
class_files = ["tcm-core.owl","tcm-zhengzhuang.owl","tcm-maixiang.owl","tcm-shexiang.owl","tcm-fuzheng.owl","tcm-yaowu.owl"]
classes = set()
for cf in class_files:
    txt = read(os.path.join(ONT, cf))
    for m in re.finditer(r'<owl:Class rdf:about="#([A-Za-z0-9_]+)"', txt):
        classes.add(m.group(1))
print("本体具名类总数:", len(classes))

# 2. 解析 rules.owl 里所有 addHerbRule/removeHerbRule 的 IF 条件
rules_txt = read(os.path.join(ONT, "fangzheng/rules.owl"))
cond_tokens = set()
for m in re.finditer(r'<addHerbRule rdf:parseType="Literal">(.*?)</addHerbRule>', rules_txt, re.S):
    body = m.group(1)
    ifm = re.search(r'IF\s+(.*?)\s+THEN', body, re.S)
    if not ifm:
        continue
    cond = ifm.group(1)
    # 去掉括号，按 AND/OR 切
    cond = cond.replace('(', ' ').replace(')', ' ')
    for tok in re.split(r'\s+(?:AND|OR)\s+', cond):
        tok = tok.strip()
        if tok:
            cond_tokens.add(tok)

print("条件 token 总数:", len(cond_tokens))
missing = sorted(t for t in cond_tokens if t not in classes)
print("不在本体类中的 token:", missing)

# 3. Han 及其四诊子类
core = read(os.path.join(ONT, "tcm-core.owl"))
han_subs = re.findall(r'<owl:Class rdf:about="#([A-Za-z0-9_]+)">\s*<rdfs:subClassOf rdf:resource="#Han"/>', core)
print("Han 的直接子类:", han_subs)
# 也查 subClassOf 里含 Han 的
han_subs2 = []
for m in re.finditer(r'<owl:Class rdf:about="#([A-Za-z0-9_]+)">(.*?)</owl:Class>', core, re.S):
    if 'rdf:resource="#Han"' in m.group(2):
        han_subs2.append(m.group(1))
print("含 subClassOf Han 的类:", sorted(set(han_subs2)))
