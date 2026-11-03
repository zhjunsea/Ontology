# -*- coding: utf-8 -*-
import io, sys, os
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')
BASE = r"D:/work/Ontology/OntologyMachine/OntologyFrameworkSiZhuBaZi/design/四书全文整理"
for fn in ["渊海子平.txt","三命通会.txt","子平真诠.txt","滴天髓.txt","神峰通考.txt","穷通宝鉴.txt"]:
    p = os.path.join(BASE, fn)
    t = open(p, encoding='utf-8').read()
    print(f"{fn}: len={len(t)} \\n={t.count(chr(10))} split('\\n')={len(t.split(chr(10)))} "
          f"splitlines={len(t.splitlines())} | \\f={t.count(chr(12))} \\v={t.count(chr(11))} "
          f"\\x1c={t.count(chr(28))} \\u2028={t.count(chr(8232))} \\r={t.count(chr(13))}")
