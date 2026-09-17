import os, re, glob, collections

root = r"D:\work\Ontology\TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox\ontology"
files = glob.glob(os.path.join(root, "**", "*.owl"), recursive=True)

defined = set()
refs = collections.defaultdict(set)
about_re = re.compile(r'rdf:about="#([^"]+)"')
id_re = re.compile(r'rdf:ID="([^"]+)"')
resource_re = re.compile(r'rdf:resource="#([^"]+)"')

texts = {}
for f in files:
    txt = open(f, encoding="utf-8").read()
    texts[f] = txt
    for m in about_re.finditer(txt):
        defined.add(m.group(1))
    for m in id_re.finditer(txt):
        defined.add(m.group(1))
    for m in resource_re.finditer(txt):
        refs[m.group(1)].add(f)

dangling = set(n for n in refs if n not in defined)

# split each file into owl:Class blocks
class_start = re.compile(r'<owl:Class rdf:about="#([^"]+)"')
for f in files:
    txt = texts[f]
    marks = [(m.start(), m.group(1)) for m in class_start.finditer(txt)]
    for i, (pos, name) in enumerate(marks):
        end = marks[i+1][0] if i+1 < len(marks) else len(txt)
        body = txt[pos:end]
        hits = sorted(set(re.findall(r'rdf:resource="#([^"]+)"', body)) & dangling)
        if hits:
            print(os.path.relpath(f, root), "|", name, "| dangling:", ",".join(hits))
