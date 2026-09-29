# -*- coding: utf-8 -*-
"""盘点 TowerMidSection.owl 中的类、对象属性、数据属性及个体。"""
import io
import os
import xml.etree.ElementTree as ET

BASE = r"D:\work\Ontology\GoldenWind"
OWL = os.path.join(BASE, "ontology", "TowerMidSection.owl")

NS = {
    "owl": "http://www.w3.org/2002/07/owl#",
    "rdf": "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
    "rdfs": "http://www.w3.org/2000/01/rdf-schema#",
    "xml": "http://www.w3.org/XML/1998/namespace",
}
RDF_ABOUT = "{http://www.w3.org/1999/02/22-rdf-syntax-ns#}about"
RDF_RES = "{http://www.w3.org/1999/02/22-rdf-syntax-ns#}resource"

tree = ET.parse(OWL)
root = tree.getroot()


def label(e):
    for l in e.findall("rdfs:label", NS):
        if l.get("{http://www.w3.org/XML/1998/namespace}lang") == "zh":
            return (l.text or "").strip()
    return ""


def about(e):
    return (e.get(RDF_ABOUT) or "").lstrip("#")


def sub_of(e):
    out = []
    for s in e.findall("rdfs:subClassOf", NS):
        r = s.get(RDF_RES)
        if r:
            out.append(r.lstrip("#"))
        else:
            inner = s.find("owl:Restriction", NS)
            if inner is not None:
                out.append("<Restriction>")
    return out


def dom_rng(e):
    d = e.find("rdfs:domain", NS)
    rr = e.find("rdfs:range", NS)
    def txt(x):
        if x is None:
            return "-"
        res = x.get(RDF_RES)
        if res:
            return res.lstrip("#")
        if x.find("owl:unionOf", NS) is not None:
            names = [c.get(RDF_ABOUT, "").lstrip("#") for c in x.iter("{http://www.w3.org/2002/07/owl#}Class")]
            return "union(" + ",".join(n for n in names if n) + ")"
        return "<anon>"
    return txt(d), txt(rr)


lines = []
for tag, title in [("owl:Class", "类"), ("owl:ObjectProperty", "对象属性"),
                   ("owl:DatatypeProperty", "数据属性"), ("owl:NamedIndividual", "个体")]:
    els = root.findall(tag, NS)
    lines.append("")
    lines.append("=" * 78)
    lines.append("%s（%d）" % (title, len(els)))
    lines.append("=" * 78)
    for e in els:
        a = about(e)
        lb = label(e)
        if tag == "owl:Class":
            lines.append("  %-44s %s" % (a, lb))
            subs = sub_of(e)
            if subs:
                lines.append("      ⊂ %s" % ", ".join(subs))
        elif tag == "owl:ObjectProperty":
            d, rr = dom_rng(e)
            lines.append("  %-44s %s" % (a, lb))
            lines.append("      domain=%s  range=%s" % (d, rr))
        else:
            d, rr = dom_rng(e)
            lines.append("  %-44s %s" % (a, lb))
            lines.append("      domain=%s  range=%s" % (d, rr))

txt = "\n".join(lines)
out = os.path.join(BASE, "WorkBuddyDir", "临时脚本与工具链", "ontology_inventory.txt")
with io.open(out, "w", encoding="utf-8") as f:
    f.write(txt)
print(txt)
print("\n[written]", out)
