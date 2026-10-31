# -*- coding: utf-8 -*-
import sys, io, subprocess, os
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')
MYSQL = r"D:/work/Ontology/mysql-26.7.0-winx64/bin/mysql.exe"
DB = "sizhubazidb"
OUT = r"D:/work/Ontology/OntologyMachine/HWCodeArtsDir/_r4_db_haystack.txt"

def run(sql):
    p = subprocess.run([MYSQL, "-uroot", DB, "-N", "-e", sql],
                       capture_output=True)
    return p.stdout.decode("utf-8", "replace")

tables = run("show tables;").split()
hay = []
for t in tables:
    cols = run("show columns from `%s`;" % t)
    names = []
    for ln in cols.splitlines():
        parts = ln.split("\t")
        if parts:
            names.append(parts[0])
    textcols = [c for c in names if c not in ("name",) or True]
    sel = ",".join("`%s`" % c for c in textcols)
    rows = run("select %s from `%s`;" % (sel, t))
    for r in rows.splitlines():
        for cell in r.split("\t"):
            cell = cell.strip()
            if cell:
                hay.append(cell)
    print("dumped", t, "cols", len(textcols))

with open(OUT, "w", encoding="utf-8") as f:
    f.write("\n".join(hay))
print("TOTAL cells:", len(hay), "->", OUT)
