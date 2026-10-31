# -*- coding: utf-8 -*-
import subprocess, io, sys
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')
M = r"D:/work/Ontology/mysql-26.7.0-winx64/bin/mysql.exe"
DB = "sizhubazidb"

def q(sql):
    p = subprocess.run([M, "-uroot", "--default-character-set=utf8mb4", DB, "-N", "-e", sql],
                       capture_output=True)
    return p.stdout.decode("utf-8", "replace")

print("== technique_catalog (tech_name | category) ==")
print(q("select concat(tech_name,' | ',category) from technique_catalog;"))
print("== theory_clause (subject | clause_group) ==")
print(q("select concat(subject,' | ',clause_group) from theory_clause;"))
print("== auspice_clause (subject | clause_group) ==")
print(q("select concat(subject,' | ',clause_group) from auspice_clause;"))
print("== dts_clause (subject | clause_group) ==")
print(q("select concat(subject,' | ',clause_group) from dts_clause;"))
print("== pattern_catalog (pattern_name | pattern_class) sample ==")
print(q("select concat(pattern_name,' | ',pattern_class) from pattern_catalog limit 40;"))
print("== ziping_clause subject ==")
print(q("select subject from ziping_clause;"))
print("== classic_text distinct book/chapter sample ==")
print(q("select concat(book_name,' | ',chapter) from classic_text limit 30;"))
print("== fu_clause subject|group sample ==")
print(q("select concat(subject,' | ',clause_group) from fu_clause limit 40;"))
