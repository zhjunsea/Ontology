# -*- coding: utf-8 -*-
import io, os, glob
import xml.etree.ElementTree as ET

BASE = r"D:\work\Ontology\TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox\ontology"
RDF = "{http://www.w3.org/1999/02/22-rdf-syntax-ns#}"
OWL = "{http://www.w3.org/2002/07/owl#}"
RDFS = "{http://www.w3.org/2000/01/rdf-schema#}"
NS = "{http://www.tcm-classics.org/jingfang#}"

targets = ["Fuzijingmitangzheng","Zhulingsanzheng","Gansuibanxiatangzheng",
           "Banxiaxiexintangzheng","Gualouqumaiwanzheng","Neibudangguijianzhongtangzheng",
           "Mahuangtangzheng","Xiayuxuetangzheng","Baihexifangzheng","Painongtangzheng",
           "Lizhongtangzheng","Xiaochaihutangzheng","Gancaotangzheng","Wangbuliuxingsanzheng",
           "Shengjiangxiexintangzheng","Gancaoxiexintangzheng"]

files = glob.glob(os.path.join(BASE,"fangzheng","*.owl"))
out = io.open(r"D:\work\Ontology\TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox\OntologyMachine\_harness\_full.txt","w",encoding="utf-8")

def txt(el): return (el.text or "").strip()

for f in files:
    if ".bak" in f: continue
    try: root = ET.parse(f).getroot()
    except Exception as e: continue
    for cls in root.findall(OWL+"Class"):
        about = cls.get(RDF+"about","")
        if not about: continue
        name = about.split("#")[-1]
        if name not in targets: continue
        out.write("="*80+"\n")
        out.write("FILE %s  CLASS %s\n" % (os.path.basename(f), name))
        out.write("="*80+"\n")
        # 直接子元素（跳过 equivalentClass 内部）
        for ch in cls:
            t = ch.tag
            if t == OWL+"equivalentClass":
                out.write("  equivalentClass: <see below>\n"); continue
            if t == RDFS+"label": out.write("  label: %s\n" % txt(ch)); continue
            if t == RDFS+"comment": out.write("  comment: %s\n" % txt(ch)); continue
            if t == RDFS+"subClassOf":
                r = ch.get(RDF+"resource")
                out.write("  subClassOf: %s\n" % (r.split("#")[-1] if r else "<anon>")); continue
            if t.startswith(NS):
                r = ch.get(RDF+"resource")
                out.write("  %s: %s\n" % (t.split("}")[-1], r.split("#")[-1] if r else txt(ch))); continue
            out.write("  %s: %s\n" % (t.split("}")[-1], txt(ch)))
        # 打印 equivalentClass 内部结构
        for eq in cls.findall(OWL+"equivalentClass"):
            out.write("  --- equivalentClass 结构 ---\n")
            def dump(el, ind):
                tag = el.tag
                if tag == OWL+"Class":
                    a = el.get(RDF+"about")
                    if a: out.write("%s%s\n" % (ind, a.split("#")[-1])); return
                    for c in el: dump(c, ind)
                    return
                if tag == OWL+"Restriction":
                    p = el.find(OWL+"onProperty"); sv = el.find(OWL+"someValuesFrom")
                    if sv is not None:
                        out.write("%s%s some %s\n" % (ind, p.get(RDF+"resource","?").split("#")[-1], sv.get(RDF+"resource","?").split("#")[-1]))
                    return
                if tag in (OWL+"intersectionOf", OWL+"unionOf"):
                    out.write("%s%s:\n" % (ind, tag.split("}")[-1]))
                    for c in el: dump(c, ind+"  ")
                    return
                out.write("%s?%s\n" % (ind, tag))
            for c in eq: dump(c, "    ")
out.close()
print("ok")
