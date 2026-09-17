import os, re, glob

root = r"D:\work\Ontology\TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox\ontology"
files = glob.glob(os.path.join(root, "**", "*.owl"), recursive=True)

defined = set()
for f in files:
    txt = open(f, encoding="utf-8").read()
    for m in re.finditer(r'rdf:(?:about|ID)="#?([^"]+)"', txt):
        defined.add(m.group(1))

fz_fillers = {}
for f in files:
    txt = open(f, encoding="utf-8").read()
    marks = [(m.start(), m.group(1)) for m in re.finditer(r'^    <owl:Class rdf:about="#([^"]+)"', txt, re.M)]
    for i, (pos, name) in enumerate(marks):
        end = marks[i+1][0] if i+1 < len(marks) else len(txt)
        body = txt[pos:end]
        if 'owl:equivalentClass' not in body:
            continue
        eq = body[body.find('owl:equivalentClass'):]
        fills = re.findall(r'<owl:onProperty rdf:resource="#(you_\w+)"/>\s*<owl:someValuesFrom rdf:resource="#([^"]+)"', eq, re.S)
        if fills:
            fz_fillers[name] = fills

tf = r"D:\work\Ontology\TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox\OntologyMachine\OntologyFramework\src\test\java\com\ocean\ontologyframework\tcm\ZabingFangzhengTest.java"
src = open(tf, encoding="utf-8").read()

def split_args(s):
    args, cur, depth, instr = [], "", 0, False
    i = 0
    while i < len(s):
        c = s[i]
        if c == '"' and (i == 0 or s[i-1] != '\\'):
            instr = not instr; cur += c
        elif instr: cur += c
        elif c == '(': depth += 1; cur += c
        elif c == ')': depth -= 1; cur += c
        elif c == ',' and depth == 0: args.append(cur.strip()); cur = ""
        else: cur += c
        i += 1
    if cur.strip(): args.append(cur.strip())
    return args

print("=== 测试症状 vs 本体方证 filler 对比（仅列有差异/缺失）===\n")
for m in re.finditer(r'void (t_\w+)\(\)\s*\{\s*assertFangzheng\((.*?)\);\s*\}', src, re.S):
    tname, body = m.group(1), m.group(2)
    args = split_args(body)
    if len(args) < 6: continue
    fz = args[2].strip().strip('"')
    syms = [x.strip() for x in args[4].strip('"').split(";") if x.strip()]
    pulses = [x.strip() for x in args[5].strip('"').split(";") if x.strip()]
    fills = fz_fillers.get(fz, [])
    fill_syms = [v for k, v in fills if k == "you_zhengzhuang"]
    fill_mais = [v for k, v in fills if k == "you_maixiang"]
    if not fills:
        print(f"### {tname}  fz={fz}  [本体无等价类 filler]")
        continue
    only_test = [s for s in syms if s not in fill_syms]
    only_onto = [s for s in fill_syms if s not in syms]
    dang_syms = [s for s in fill_syms if s not in defined]
    dang_mais = [s for s in fill_mais if s not in defined]
    if only_test or only_onto or dang_syms or dang_mais:
        print(f"### {tname}  fz={fz}")
        print(f"    测试症状 : {';'.join(syms)}")
        print(f"    本体filler: {';'.join(fill_syms)}")
        if fill_mais: print(f"    本体脉象 : {';'.join(fill_mais)}  | 测试脉象: {';'.join(pulses)}")
        if only_test: print(f"    ⚠ 仅测试有: {only_test}")
        if only_onto: print(f"    ⚠ 仅本体有: {only_onto}")
        if dang_syms: print(f"    ✖ 本体filler悬空: {dang_syms}")
        if dang_mais: print(f"    ✖ 本体脉象悬空: {dang_mais}")
        print()
