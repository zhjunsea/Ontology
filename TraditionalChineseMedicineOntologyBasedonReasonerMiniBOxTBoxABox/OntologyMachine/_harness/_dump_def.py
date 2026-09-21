# -*- coding: utf-8 -*-
import io, os
import xml.etree.ElementTree as ET

BASE = r"D:\work\Ontology\TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox\ontology\fangzheng"
targets = ["Fuzijingmitangzheng","Zhulingsanzheng","Gansuibanxiatangzheng",
           "Banxiaxiexintangzheng","Gualouqumaiwanzheng","Neibudangguijianzhongtangzheng",
           "Mahuangtangzheng","Xiayuxuetangzheng","Baihexifangzheng","Painongtangzheng"]
files = ["duli.owl","shaoyang_yangming.owl","shaoyin_taiyin.owl","taiyang.owl","zabing.owl"]

RDF = "{http://www.w3.org/1999/02/22-rdf-syntax-ns#}"
OWL = "{http://www.w3.org/2002/07/owl#}"
RDFS = "{http://www.w3.org/2000/01/rdf-schema#}"

out = io.open(r"D:\work\Ontology\TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox\OntologyMachine\_harness\_defs.txt","w",encoding="utf-8")

def dump_restriction(el, indent, buf):
    prop = el.find(OWL+"onProperty")
    sv = el.find(OWL+"someValuesFrom")
    if sv is not None:
        buf.append("%s%s some %s" % (indent, prop.get(RDF+"resource","?").split("#")[-1], sv.get(RDF+"resource","?").split("#")[-1]))
    av = el.find(OWL+"allValuesFrom")
    if av is not None:
        buf.append("%s%s only %s" % (indent, prop.get(RDF+"resource","?").split("#")[-1], av.get(RDF+"resource","?").split("#")[-1]))

def dump_expr(el, indent, buf):
    tag = el.tag
    if tag == OWL+"Class":
        about = el.get(RDF+"about")
        if about:
            buf.append("%sCLASS %s" % (indent, about.split("#")[-1]))
            return
        for ch in el:
            dump_expr(ch, indent, buf)
        return
    if tag == OWL+"Restriction":
        dump_restriction(el, indent, buf)
        return
    if tag in (OWL+"intersectionOf", OWL+"unionOf"):
        buf.append("%s%s:" % (indent, tag.split("}")[-1]))
        for ch in el:
            dump_expr(ch, indent+"  ", buf)
        return
    buf.append("%s?%s" % (indent, tag))

for f in files:
    p = os.path.join(BASE,f)
    if not os.path.exists(p): continue
    tree = ET.parse(p)
    root = tree.getroot()
    for t in targets:
        for cls in root.findall(OWL+"Class"):
            if cls.get(RDF+"about","").endswith("#"+t):
                buf = []
                buf.append("="*70)
                buf.append("FILE %s  CLASS %s" % (f,t))
                for lbl in cls.findall(RDFS+"label"):
                    buf.append("  label: %s" % lbl.text)
                for eq in cls.findall(OWL+"equivalentClass"):
                    buf.append("  equivalentClass:")
                    for ch in eq:
                        dump_expr(ch, "    ", buf)
                for sub in cls.findall(RDFS+"subClassOf"):
                    r = sub.get(RDF+"resource")
                    if r: buf.append("  subClassOf: %s" % r.split("#")[-1])
                for bl in cls.findall("{http://www.tcm-classics.org/jingfang#}belongsToLiujing"):
                    buf.append("  belongsToLiujing: %s" % bl.get(RDF+"resource","").split("#")[-1])
                out.write("\n".join(buf)+"\n")
out.close()
print("ok")
