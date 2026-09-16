# -*- coding: utf-8 -*-
import subprocess, sys
sys.path.insert(0, '.')
from _fangji81_data import FANGJI81
MYSQL = r"C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe"
iris = "','".join(f[0] for f in FANGJI81)
q = ("SELECT f.iri, COUNT(fy.yaowu_id) FROM fangji f "
     "LEFT JOIN fangji_yaowu fy ON fy.fangji_id=f.id "
     "WHERE f.iri IN ('%s') GROUP BY f.id;" % iris)
p = subprocess.run([MYSQL, '--default-character-set=utf8mb4', '-uroot', '-pzj780704', 'jingfangdb', '-e', q],
                   stdout=subprocess.PIPE, stderr=subprocess.PIPE)
lines = p.stdout.decode('utf-8', 'replace').strip().split('\n')[1:]
db = {}
for ln in lines:
    parts = ln.split('\t')
    if len(parts) == 2:
        db[parts[0]] = int(parts[1])
bad = []
for iri, zh, meds, jl, jf in FANGJI81:
    exp = len(meds)
    got = db.get(iri, -1)
    if exp != got:
        bad.append((iri, zh, exp, got))
print('不匹配方剂数:', len(bad))
for b in bad:
    print('  ', b)
print('数据库返回方剂数:', len(db))
