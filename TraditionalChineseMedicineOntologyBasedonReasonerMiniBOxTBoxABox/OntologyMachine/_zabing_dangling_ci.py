import os, re, glob, difflib, collections

root = r"D:\work\Ontology\TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox\ontology"
files = glob.glob(os.path.join(root, "**", "*.owl"), recursive=True)

defined = set()
refs = collections.defaultdict(set)
lower_map = collections.defaultdict(set)
labels = {}
for f in files:
    txt = open(f, encoding="utf-8").read()
    rel = os.path.relpath(f, root)
    for m in re.finditer(r'<owl:Class rdf:about="#([^"]+)"[^>]*>\s*<rdfs:label xml:lang="zh">([^<]*)</rdfs:label>', txt):
        labels[m.group(1)] = m.group(2)
    for m in re.finditer(r'rdf:(?:about|ID)="#?([^"]+)"', txt):
        defined.add(m.group(1))
    for m in re.finditer(r'rdf:resource="#([^"]+)"', txt):
        refs[m.group(1)].add(rel)

for n in defined:
    lower_map[n.lower()].add(n)

dangling = sorted(n for n in refs if n not in defined)

print("=== 悬空引用（含大小写变体检测）===\n")
for n in dangling:
    lc = lower_map.get(n.lower())
    if lc:
        print(f"{n:34s} -> 大小写变体: {sorted(lc)}")
    else:
        close = difflib.get_close_matches(n, sorted(defined), n=3, cutoff=0.75)
        print(f"{n:34s} -> 无精确/大小写匹配; 近似: {close}")
