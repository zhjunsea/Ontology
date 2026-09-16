# -*- coding: utf-8 -*-
import subprocess
MYSQL = r"C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe"

sql = (
    "INSERT IGNORE INTO fangji (iri, label) VALUES ('Gansuibanxiatang','甘遂半夏汤');\n"
    "INSERT IGNORE INTO fangji_yaowu (fangji_id, yaowu_id) "
    "SELECT f.id, y.id FROM fangji f, yaowu y "
    "WHERE f.iri='Gansuibanxiatang' AND y.iri IN ('Gansui','Banxia','Shaoyao','Gancao');\n"
    "SELECT f.iri, f.label, COUNT(fy.yaowu_id) AS n FROM fangji f "
    "LEFT JOIN fangji_yaowu fy ON fy.fangji_id=f.id "
    "WHERE f.iri='Gansuibanxiatang' GROUP BY f.id;\n"
    "SELECT COUNT(*) AS fangji_total FROM fangji;\n"
    "SELECT COUNT(*) AS rel_total FROM fangji_yaowu;\n"
)

p = subprocess.run([MYSQL, '--default-character-set=utf8mb4', '-uroot', '-pzj780704', 'jingfangdb'],
                   input=sql.encode('utf-8'), stdout=subprocess.PIPE, stderr=subprocess.PIPE)
print('returncode:', p.returncode)
print(p.stdout.decode('utf-8', 'replace'))
print('ERR:', p.stderr.decode('utf-8', 'replace'))
