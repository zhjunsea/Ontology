# -*- coding: utf-8 -*-
import re, io, sys
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')
s = open(r'D:/work/Ontology/OntologyMachine/OntologyFrameworkSiZhuBaZi/ontology/database/createdb_tables.ddl', encoding='utf-8').read()
for m in re.finditer(r'CREATE\s+TABLE[^(]*?`(\w+)`\s*\((.*?)\)\s*;', s, re.S | re.I):
    name = m.group(1); body = m.group(2)
    cols = []
    for l in body.splitlines():
        l = l.strip()
        if not l or l.upper().startswith(('PRIMARY', 'KEY', 'UNIQUE', 'INDEX', 'CONSTRAINT')):
            continue
        cols.append(re.split(r'\s+', l)[0])
    print('%-24s %s' % (name, ' '.join(cols)))
