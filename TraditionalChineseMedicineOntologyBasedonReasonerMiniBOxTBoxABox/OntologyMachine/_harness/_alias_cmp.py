# -*- coding: utf-8 -*-
"""对比 _alias_before.log 与 _alias_after.log 的逐单元命中情况。"""
import re, io

H = r"D:\work\Ontology\TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox\OntologyMachine\_harness"

def parse(p):
    t = io.open(p, encoding="utf-8", errors="replace").read()
    res = {}
    for m in re.finditer(r"单元「([^」]+)」L1 候选 (\d+) 条:([^\n]*)", t):
        res[m.group(1)] = (int(m.group(2)), m.group(3).strip())
    return res

b = parse(H + r"\_alias_before.log")
a = parse(H + r"\_alias_after.log")

L = []
L.append("单元数: before=%d after=%d" % (len(b), len(a)))
L.append("")
L.append("%-16s %-8s %-8s %s" % ("单元", "before", "after", "after top1"))
L.append("-" * 70)
for k in a:
    nb = b.get(k, (0, ""))[0]
    na, top = a[k]
    flag = "  <== 新增命中" if nb == 0 and na > 0 else ""
    L.append("%-16s %-8d %-8d %s%s" % (k, nb, na, top.split("  ")[0][:40], flag))

io.open(H + r"\_alias_cmp.txt", "w", encoding="utf-8").write("\n".join(L) + "\n")
print("done")
