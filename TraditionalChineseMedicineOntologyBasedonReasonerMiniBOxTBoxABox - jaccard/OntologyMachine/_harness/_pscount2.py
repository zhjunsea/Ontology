import glob, os
import xml.etree.ElementTree as ET

base = r"D:/work/Ontology/TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox/ontology/fangzheng"
RDF = "{http://www.w3.org/1999/02/22-rdf-syntax-ns#}"
OWL = "{http://www.w3.org/2002/07/owl#}"

total = set()
with_ps = {}
with_eq = set()
ps_total = 0

for f in sorted(glob.glob(os.path.join(base, "*.owl"))):
    if ".bak" in f:
        continue
    try:
        root = ET.parse(f).getroot()
    except Exception as e:
        print("PARSE FAIL", os.path.basename(f), e)
        continue
    for cls in root.iter(OWL + "Class"):
        about = cls.get(RDF + "about")
        if not about or not about.startswith("#"):
            continue
        name = about[1:]
        total.add(name)
        ps = [c for c in cls if c.tag.endswith("possibleSymptom")]
        if ps:
            with_ps[name] = len(ps)
            ps_total += len(ps)
        if any(c.tag == OWL + "equivalentClass" for c in cls):
            with_eq.add(name)

print("fangzheng 具名类总数(去重) =", len(total))
print("含 equivalentClass 的类数   =", len(with_eq))
print("含 possibleSymptom 的类数   =", len(with_ps))
print("possibleSymptom 引用总条数  =", ps_total)
print("覆盖率(占具名类)            = %.1f%%" % (100.0 * len(with_ps) / max(1, len(total))))
print()
print("全部含 possibleSymptom 的类:")
for k in sorted(with_ps, key=lambda x: -with_ps[x]):
    print("  %-42s %d 条" % (k, with_ps[k]))
