# -*- coding: utf-8 -*-
import re, io
jp = r"D:\work\Ontology\OntologyMachine\OntologyFrameworkZiWeiDouShu\src\main\java\com\ocean\ontologyframework\ziweidoushu\ZiWeiDouShuOntologyJobWorker.java"
sql = r"D:\work\Ontology\OntologyMachine\OntologyFrameworkZiWeiDouShu\ontology\database\data.sql"
java = io.open(jp, encoding="utf-8").read()
# verse("PV_...", ...) call sites
call_keys = re.findall(r'verse\(\s*"(PV_[A-Z0-9_]+)"', java)
print("verse() call keys: count=%d distinct=%d" % (len(call_keys), len(set(call_keys))))
for k in call_keys:
    print("   ", k)

# pattern_verse keys in data.sql
lines = io.open(sql, encoding="utf-8").read().splitlines()
pkeys = []
inp = False
for l in lines:
    if l.startswith("INSERT INTO `pattern_verse`"):
        inp = True; continue
    if inp:
        m = re.match(r"\(\s*\d+\s*,\s*'(PV_[A-Z0-9_]+)'", l.strip())
        if m:
            pkeys.append(m.group(1))
        if l.strip().endswith(";"): break
print("\npattern_verse DB keys: count=%d distinct=%d" % (len(pkeys), len(set(pkeys))))
for k in pkeys:
    print("   ", k)

call_set = set(call_keys)
db_set = set(pkeys)
print("\n=== DIFF ===")
print("in code but NOT in DB (would always use literal fallback):", sorted(call_set - db_set))
print("in DB but NOT used in code:", sorted(db_set - call_set))
