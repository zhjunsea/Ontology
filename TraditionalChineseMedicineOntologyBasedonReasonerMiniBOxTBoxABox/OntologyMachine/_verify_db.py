# -*- coding: utf-8 -*-
import subprocess
MYSQL = r"C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe"
q = ("SELECT 'yaowu' t, COUNT(*) c FROM yaowu "
     "UNION ALL SELECT 'fangji', COUNT(*) FROM fangji "
     "UNION ALL SELECT 'fangji_yaowu', COUNT(*) FROM fangji_yaowu;")
p = subprocess.run([MYSQL, '--default-character-set=utf8mb4', '-uroot', '-pzj780704', 'jingfangdb', '-e', q],
                   stdout=subprocess.PIPE, stderr=subprocess.PIPE)
print(p.stdout.decode('utf-8', 'replace'))
print('ERR:', p.stderr.decode('utf-8', 'replace'))

# 抽查新增方剂
q2 = ("SELECT f.iri, f.label, COUNT(fy.yaowu_id) FROM fangji f "
      "LEFT JOIN fangji_yaowu fy ON fy.fangji_id=f.id "
      "WHERE f.iri IN ('Wenkesan','Dahuanggansuitang','Houshiheisan','Fengyintang','Sanhuangtang','Ganmaidazaotang') "
      "GROUP BY f.id;")
p2 = subprocess.run([MYSQL, '--default-character-set=utf8mb4', '-uroot', '-pzj780704', 'jingfangdb', '-e', q2],
                    stdout=subprocess.PIPE, stderr=subprocess.PIPE)
print(p2.stdout.decode('utf-8', 'replace'))
