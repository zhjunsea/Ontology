# -*- coding: utf-8 -*-
import io, os

names = ['Weiou', 'XinqiBuzu', 'Shaofumanrudunzhuang', 'Xiaobianweinan']
roots = ['../ontology', '../OntologyMachine/OntologyFramework/src']
files = []
for r in roots:
    for dp, dn, fn in os.walk(r):
        if '_backup' in dp or '__pycache__' in dp:
            continue
        for f in fn:
            if f.endswith(('.owl', '.java', '.sql', '.obda')):
                files.append(os.path.join(dp, f))

for nm in names:
    print('==== %s ====' % nm)
    for fp in files:
        s = io.open(fp, encoding='utf-8', errors='ignore').read()
        c = s.count(nm)
        if c:
            print('  %s x%d' % (fp, c))
