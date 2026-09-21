# -*- coding: utf-8 -*-
"""量化分析：方证 equivalentClass 里有多少把「六经」写成了充要条件项。"""
import io
import os
import re

ROOT = r"D:\work\Ontology\TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox\ontology"
LIUJING = ["Taiyangbing", "Yangmingbing", "Shaoyangbing", "Taiyinbing",
           "Shaoyinbing", "Jueyinbing", "Hebing"]
HEBING = ["TaiyangYangmingHebing", "TaiyangShaoyangHebing", "TaiyangTaiyinHebing",
          "TaiyangShaoyinHebing", "TaiyangJueyinHebing", "YangmingShaoyangHebing",
          "YangmingTaiyinHebing", "YangmingShaoyinHebing", "YangmingJueyinHebing",
          "ShaoyangTaiyinHebing", "ShaoyangShaoyinHebing", "ShaoyangJueyinHebing",
          "TaiyinShaoyinHebing", "TaiyinJueyinHebing", "ShaoyinJueyinHebing",
          "SanYangHebing", "SanYinHebing"]
ALL_LJ = set(LIUJING) | set(HEBING)


def read(p):
    with io.open(p, "r", encoding="utf-8", errors="replace") as f:
        return f.read()


def main():
    fz_dir = os.path.join(ROOT, "fangzheng")
    files = [os.path.join(fz_dir, n) for n in os.listdir(fz_dir)
             if n.endswith(".owl") and ".bak" not in n]
    files.append(os.path.join(ROOT, "tcm-core.owl"))

    total = 0
    with_lj = 0
    only_lj_missing = 0          # 除六经外全部满足（即只差六经）
    detail = []

    for p in files:
        doc = read(p)
        # 逐个 <owl:Class rdf:about="#X"> ... </owl:Class> 块（非贪婪，够用）
        for m in re.finditer(r'<owl:Class rdf:about="#([A-Za-z0-9_]+)">(.*?)</owl:Class>',
                             doc, re.S):
            frag, body = m.group(1), m.group(2)
            if "equivalentClass" not in body:
                continue
            if not re.search(r'<rdfs:subClassOf rdf:resource="#Fangzheng"/>', body):
                continue                      # 只统计方证
            total += 1
            eq = body[body.index("equivalentClass"):]
            lj_hits = sorted({x for x in ALL_LJ if ('#' + x) in eq})
            if lj_hits:
                with_lj += 1
                detail.append((frag, lj_hits))

    print("方证总数（含 equivalentClass 且 ⊑ Fangzheng）:", total)
    print("其中 equivalentClass 含六经项:", with_lj,
          "（%.0f%%）" % (100.0 * with_lj / max(total, 1)))
    print("不含六经项:", total - with_lj)
    print()
    print("样例（前 12 个含六经项的方证）:")
    for frag, lj in detail[:12]:
        print("   %-52s %s" % (frag, ",".join(lj)))


if __name__ == "__main__":
    main()
