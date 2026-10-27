import re, sys

path = sys.argv[1]
s = open(path, encoding="utf-8").read()

ABS = re.compile(r'^[a-zA-Z][a-zA-Z0-9+.\-]*:')
count = {"about": 0, "resource": 0}


def fix(m):
    attr, val = m.group(1), m.group(2)
    if val.startswith("#") or ABS.match(val):
        return m.group(0)
    count[attr.replace("rdf:", "")] += 1
    return f'{attr}="#{val}"'


s2 = re.sub(r'(rdf:about)="([^"]*)"', fix, s)
s2 = re.sub(r'(rdf:resource)="([^"]*)"', fix, s2)

open(path, "w", encoding="utf-8", newline="").write(s2)
print("prefixed rdf:about =", count["about"], " rdf:resource =", count["resource"])
