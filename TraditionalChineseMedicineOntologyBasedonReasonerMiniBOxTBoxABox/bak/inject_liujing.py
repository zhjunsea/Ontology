#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
inject_liujing.py

功能：
  将每个方证类中的 belongsToLiujing 六经类，注入到其 owl:equivalentClass
  的 owl:intersectionOf 中，使 Openllet 等推理机可以基于六经参与推理。

用法：
  pip install lxml
  python inject_liujing.py

输出：
  ./modified/*.owl
"""

import os
import glob
from lxml import etree

RDF = "http://www.w3.org/1999/02/22-rdf-syntax-ns#"
OWL = "http://www.w3.org/2002/07/owl#"
JF  = "http://www.tcm-classics.org/jingfang#"


def tag(ns, name):
    return f"{{{ns}}}{name}"


def get_resource(elem):
    return elem.get(tag(RDF, "resource"))


def make_liujing_class(ref):
    """生成 <owl:Class rdf:about="#Taiyangbing"/>"""
    el = etree.Element(tag(OWL, "Class"))
    el.set(tag(RDF, "about"), ref)
    return el


def make_liujing_item(refs):
    """
    单个六经：直接返回 <owl:Class rdf:about="#Taiyangbing"/>
    多个六经：用 <owl:Class><owl:unionOf ...> 包成一个项，再放入 intersectionOf。
    """
    if len(refs) == 1:
        return make_liujing_class(refs[0])

    wrapper = etree.Element(tag(OWL, "Class"))
    union = etree.SubElement(wrapper, tag(OWL, "unionOf"))
    union.set(tag(RDF, "parseType"), "Collection")
    for ref in refs:
        union.append(make_liujing_class(ref))
    return wrapper


def inject_into_intersection(inter, liujing_refs):
    """把六经项插入已有的 owl:intersectionOf 最前面。"""
    if inter.get(tag(RDF, "parseType")) != "Collection":
        inter.set(tag(RDF, "parseType"), "Collection")

    existing_refs = set()
    for child in inter:
        for c in child.iter(tag(OWL, "Class")):
            about = c.get(tag(RDF, "about"))
            if about:
                existing_refs.add(about)

    missing = [r for r in liujing_refs if r not in existing_refs]
    if not missing:
        return

    item = make_liujing_item(missing)
    inter.insert(0, item)


def process_class(class_elem):
    """处理单个 owl:Class。返回是否发生修改。"""
    liujing_refs = []

    for bel in class_elem.findall(tag(JF, "belongsToLiujing")):
        ref = get_resource(bel)
        if ref and ref not in liujing_refs:
            liujing_refs.append(ref)

    if not liujing_refs:
        return False

    eq = class_elem.find(tag(OWL, "equivalentClass"))
    if eq is None:
        # 没有等价类定义时不自动创建，避免生成只有六经的空定义。
        return False

    eq_class = eq.find(tag(OWL, "Class"))
    if eq_class is None:
        eq_class = etree.SubElement(eq, tag(OWL, "Class"))

    inter = eq_class.find(tag(OWL, "intersectionOf"))
    union = eq_class.find(tag(OWL, "unionOf"))

    if inter is not None:
        # 原本已经是 intersectionOf：直接把六经插到最前面。
        inject_into_intersection(inter, liujing_refs)
        return True

    elif union is not None:
        # 原本是 unionOf：改成
        # (六经) AND (原 unionOf 表达式)
        eq_class.remove(union)

        new_inter = etree.Element(tag(OWL, "intersectionOf"))
        new_inter.set(tag(RDF, "parseType"), "Collection")
        new_inter.append(make_liujing_item(liujing_refs))

        wrapper = etree.Element(tag(OWL, "Class"))
        wrapper.append(union)
        new_inter.append(wrapper)

        eq_class.append(new_inter)
        return True

    else:
        # 其他情况：把原有子元素整体包进新的 intersectionOf，并在最前面加入六经。
        children = list(eq_class)
        if not children:
            return False

        new_inter = etree.Element(tag(OWL, "intersectionOf"))
        new_inter.set(tag(RDF, "parseType"), "Collection")
        new_inter.append(make_liujing_item(liujing_refs))

        for ch in children:
            eq_class.remove(ch)
            new_inter.append(ch)

        eq_class.append(new_inter)
        return True


def process_file(path, out_dir):
    parser = etree.XMLParser(remove_blank_text=False, recover=True)
    tree = etree.parse(path, parser)
    root = tree.getroot()

    changed = False
    for class_elem in root.iter(tag(OWL, "Class")):
        if process_class(class_elem):
            changed = True

    os.makedirs(out_dir, exist_ok=True)
    out_path = os.path.join(out_dir, os.path.basename(path))
    tree.write(out_path, xml_declaration=True, encoding="UTF-8", pretty_print=True)
    print(f"{path} -> {out_path}  changed={changed}")


def main():
    out_dir = "modified"
    files = sorted(glob.glob("*.owl"))
    for path in files:
        process_file(path, out_dir)


if __name__ == "__main__":
    main()