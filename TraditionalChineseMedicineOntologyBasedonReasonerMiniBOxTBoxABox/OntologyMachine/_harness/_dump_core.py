# -*- coding: utf-8 -*-
import io, os
import xml.etree.ElementTree as ET

P = r"D:\work\Ontology\TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox\ontology\tcm-core.owl"
RDF = "{http://www.w3.org/1999/02/22-rdf-syntax-ns#}"
OWL = "{http://www.w3.org/2002/07/owl#}"
RDFS = "{http://www.w3.org/2000/01/rdf-schema#}"

tree = ET.parse(P); root = tree.getroot()

def dump_expr(el, indent, buf):
    tag = el.tag
    if tag == OWL+"Class":
        about = el.get(RDF+"about")
        if about:
            buf.append("%s%s" % (indent, about.split("#")[-1])); return
        for ch in el: dump_expr(ch, indent, buf)
        return
    if tag == OWL+"Restriction":
        prop = el.find(OWL+"onProperty"); sv = el.find(OWL+"someValuesFrom")
        if sv is not None:
            buf.append("%s%s some %s" % (indent, prop.get(RDF+"resource","?").split("#")[-1], sv.get(RDF+"resource","?").split("#")[-1]))
        return
    if tag in (OWL+"intersectionOf", OWL+"unionOf"):
        buf.append("%s%s:" % (indent, tag.split("}")[-1]))
        for ch in el: dump_expr(ch, indent+"  ", buf)
        return
    buf.append("%s?%s" % (indent, tag))

out = io.open(r"D:\work\Ontology\TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox\OntologyMachine\_harness\_core.txt","w",encoding="utf-8")
for cls in root.findall(OWL+"Class"):
    about = cls.get(RDF+"about","")
    if not about: continue
    name = about.split("#")[-1]
    if not (name.startswith("Panju") or name.startswith("Bagang") or
            name.endswith("bing") or name in ("Li","Biao","Han","Re","Xu","Shi","Yin","Yang","Banbiaobanli")):
        continue
    buf = ["="*70, "CLASS %s" % name]
    for lbl in cls.findall(RDFS+"label"):
        buf.append("  label: %s" % lbl.text)
    for eq in cls.findall(OWL+"equivalentClass"):
        buf.append("  equivalentClass:")
        for ch in eq: dump_expr(ch, "    ", buf)
    for sub in cls.findall(RDFS+"subClassOf"):
        r = sub.get(RDF+"resource")
        if r: buf.append("  subClassOf: %s" % r.split("#")[-1])
    out.write("\n".join(buf)+"\n")
out.close()
print("ok")
