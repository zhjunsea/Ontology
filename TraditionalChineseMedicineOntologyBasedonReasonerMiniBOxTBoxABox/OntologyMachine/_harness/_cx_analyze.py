# -*- coding: utf-8 -*-
"""
统计方证等价类定义的推理复杂度结构（铁律 45：必须用 ElementTree 递归，正则处理不了嵌套）。
"""
import os
import sys
import xml.etree.ElementTree as ET
from collections import Counter

RDF = "{http://www.w3.org/1999/02/22-rdf-syntax-ns#}"
OWL = "{http://www.w3.org/2002/07/owl#}"

ONT = r"D:\work\Ontology\TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox\ontology"


def local(tag):
    return tag.split("}")[-1]


def walk(el, counter):
    counter[local(el.tag)] += 1
    for c in el:
        walk(c, counter)


def analyze(path):
    tree = ET.parse(path)
    root = tree.getroot()
    rows = []
    for desc in root.iter(OWL + "Class"):
        about = desc.get(RDF + "about")
        if not about:
            continue
        name = about.split("#")[-1]
        for eq in desc.findall(OWL + "equivalentClass"):
            counter = Counter()
            walk(eq, counter)
            rows.append((name, counter))
    return rows


def main():
    files = [
        "fangzheng/duli.owl",
        "fangzheng/taiyang.owl",
        "fangzheng/shaoyin_taiyin.owl",
        "fangzheng/zabing.owl",
        "fangzheng/shaoyang_yangming.owl",
        "fangzheng/jueyin.owl",
        "fangzheng/hebing.owl",
        "fangzheng/jianjia.owl",
        "tcm-core.owl",
        "tcm-maixiang.owl",
        "tcm-shexiang.owl",
    ]
    grand = Counter()
    print("%-28s %6s %6s %6s %6s %6s %6s" % ("FILE", "方证数", "some", "comp", "union", "inter", "max合取"))
    for f in files:
        p = os.path.join(ONT, f.replace("/", os.sep))
        if not os.path.exists(p):
            continue
        try:
            rows = analyze(p)
        except Exception as e:
            print("%-28s PARSE-ERR %s" % (f, e))
            continue
        some = comp = union = inter = 0
        maxconj = 0
        for name, c in rows:
            some += c.get("someValuesFrom", 0)
            comp += c.get("complementOf", 0)
            union += c.get("unionOf", 0)
            inter += c.get("intersectionOf", 0)
            maxconj = max(maxconj, c.get("intersectionOf", 0))
        grand["some"] += some
        grand["comp"] += comp
        grand["union"] += union
        grand["inter"] += inter
        print("%-28s %6d %6d %6d %6d %6d %6d" % (f, len(rows), some, comp, union, inter, maxconj))
    print("-" * 78)
    print("合计 some=%d comp=%d union=%d inter=%d" % (
        grand["some"], grand["comp"], grand["union"], grand["inter"]))


if __name__ == "__main__":
    main()
