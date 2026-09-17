import os, re, glob, collections

root = r"D:\work\Ontology\TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox\ontology"
files = glob.glob(os.path.join(root, "**", "*.owl"), recursive=True)

defined = collections.defaultdict(set)
refs = collections.defaultdict(set)

about_re = re.compile(r'rdf:about="#([^"]+)"')
id_re = re.compile(r'rdf:ID="([^"]+)"')
resource_re = re.compile(r'rdf:resource="#([^"]+)"')

for f in files:
    txt = open(f, encoding="utf-8").read()
    for m in about_re.finditer(txt):
        defined[m.group(1)].add(f)
    for m in id_re.finditer(txt):
        defined[m.group(1)].add(f)
    for m in resource_re.finditer(txt):
        refs[m.group(1)].add(f)

dangling = {}
for name, fs in refs.items():
    if name not in defined:
        dangling[name] = fs

print("TOTAL FILES:", len(files))
print("DANGLING COUNT:", len(dangling))
for name in sorted(dangling):
    print(name, "->", ";".join(os.path.relpath(p, root) for p in sorted(dangling[name])))
