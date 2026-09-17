# -*- coding: utf-8 -*-
import re
p = r"OntologyFramework/target/surefire-reports/com.ocean.ontologyframework.tcm.ZabingFangzhengTest.txt"
t = open(p, encoding="utf-8", errors="replace").read()
# split by test entries
blocks = re.split(r'\n(?=com\.ocean\.ontologyframework\.tcm\.ZabingFangzhengTest\.)', t)
inst_missing = []
mismatch = []
for b in blocks:
    m = re.match(r'com\.ocean\.ontologyframework\.tcm\.ZabingFangzhengTest\.(\w+)', b)
    if not m:
        continue
    name = m.group(1)
    if '实例不存在于本体' in b:
        names = re.search(r'请核对名称: \[([^\]]+)\]', b)
        inst_missing.append((name, names.group(1) if names else '?'))
    else:
        exp = re.search(r'expected: "([^"]+)"', b)
        got = re.search(r'but was: "([^"]+)"', b)
        mismatch.append((name, exp.group(1) if exp else '?', got.group(1) if got else '?'))
print("=== 实例缺失 (%d) ===" % len(inst_missing))
for n, x in inst_missing:
    print(f"  {n}: {x}")
print("\n=== 方证误判 (%d) ===" % len(mismatch))
for n, e, g in mismatch:
    print(f"  {n}: expected {e} but {g}")
print("\nTOTAL", len(inst_missing) + len(mismatch))
