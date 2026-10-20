import re, sys

path = r"D:\work\Ontology\OntologyMachine\OntologyFrameworkPlumBlossom\ontology\plumblossom-all.owl"
s = open(path, encoding="utf-8").read()


def short(x):
    x = re.sub(r"http://example.org/plumblossom/([a-z]+)/", r"\1:", x)
    x = re.sub(r"http://www.w3.org/2003/11/swrlb#", "swrlb:", x)
    return x


idx = [m.start() for m in re.finditer(r"swrl#Imp", s)]
print("imps", len(idx))
blocks = []
for k, st in enumerate(idx):
    a = s.rfind("<rdf:Description", 0, st)
    en = idx[k + 1] if k + 1 < len(idx) else len(s)
    blocks.append(s[a:en])

for n, b in enumerate(blocks, 1):
    body = b.split("<swrl:head>")[0]
    head = b.split("<swrl:head>")[-1]
    def dump(seg):
        preds = re.findall(r"swrl:(classPredicate|propertyPredicate|builtin) rdf:resource=\"([^\"]+)\"", seg)
        args = re.findall(r"swrl:argument(\d) rdf:resource=\"([^\"]+)\"", seg)
        lits = re.findall(r"<swrl:argument2 rdf:datatype=\"[^\"]+\">([^<]+)</swrl:argument2>", seg)
        return preds, args, lits
    bp, ba, bl = dump(body)
    hp, ha, hl = dump(head)
    print("==== R%d ====" % n)
    print("  BODY preds:", [short(p[1]) for p in bp], "lits:", bl)
    print("  BODY args :", [(a, short(v)) for a, v in ba])
    print("  HEAD preds:", [short(p[1]) for p in hp], "lits:", hl)
    print("  HEAD args :", [(a, short(v)) for a, v in ha])
