# -*- coding: utf-8 -*-
import subprocess
MYSQL = r"C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe"
q = ("SELECT id, iri, label FROM fangji WHERE iri LIKE '%Gansui%' OR iri LIKE '%banxia%';")
p = subprocess.run([MYSQL, '--default-character-set=utf8mb4', '-uroot', '-pzj780704', 'jingfangdb', '-e', q],
                   stdout=subprocess.PIPE, stderr=subprocess.PIPE)
print(p.stdout.decode('utf-8', 'replace'))
print('ERR:', p.stderr.decode('utf-8', 'replace'))
