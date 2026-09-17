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

# 解析测试文件
tf = r"D:\work\Ontology\TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox\OntologyMachine\OntologyFramework\src\test\java\com\ocean\ontologyframework\tcm\ZabingFangzhengTest.java"
src = open(tf, encoding="utf-8").read()

# 每个 assertFangzheng 调用：提取方法名 + 参数字符串
calls = []
for m in re.finditer(r'void (t_\w+)\(\)\s*\{\s*assertFangzheng\((.*?)\);\s*\}', src, re.S):
    calls.append((m.group(1), m.group(2)))

def split_args(s):
    args, cur, depth, instr = [], "", 0, False
    i = 0
    while i < len(s):
        c = s[i]
        if c == '"' and (i == 0 or s[i-1] != '\\'):
            instr = not instr
            cur += c
        elif instr:
            cur += c
        elif c == '(':
            depth += 1; cur += c
        elif c == ')':
            depth -= 1; cur += c
        elif c == ',' and depth == 0:
            args.append(cur.strip()); cur = ""
        else:
            cur += c
        i += 1
    if cur.strip(): args.append(cur.strip())
    return args

print("=== 测试用例中所有症状/脉象名存在性检查 ===\n")
missing_all = []
for name, body in calls:
    args = split_args(body)
    if len(args) < 6: continue
    syms = args[4].strip('"')
    pulses = args[5].strip('"')
    for field, val in (("syms", syms), ("pulses", pulses)):
        for n in val.split(";"):
            n = n.strip()
            if not n: continue
            if n not in instances and n not in classes:
                missing_all.append((name, field, n))

seen = set()
for name, field, n in missing_all:
    key = (field, n)
    if key in seen: continue
    seen.add(key)
    close = difflib.get_close_matches(n, allnames, n=4, cutoff=0.62)
    cand = "; ".join(f"{c}({labels.get(c,'')})" for c in close)
    print(f"[{field}] {n:32s} <- {name}\n      候选: {cand}")

print(f"\n合计缺失(去重): {len(seen)}")
