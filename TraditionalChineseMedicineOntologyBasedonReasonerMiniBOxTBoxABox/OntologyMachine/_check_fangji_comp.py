# -*- coding: utf-8 -*-
import subprocess
MYSQL = r"C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe"
q = ("SELECT f.iri, f.label, COUNT(fy.yaowu_id) AS n FROM fangji f "
     "LEFT JOIN fangji_yaowu fy ON fy.fangji_id=f.id "
     "WHERE f.iri IN ('Gansuibanxiatang','Dahuanggansuitang','Dabanxiatang','Honglanhuajiu','Houpoudahuangtang','Chaihubaihutang','Xiexintang','Xiayuxuetang','Zhishishaoyaosan','Dahuangzhechongwan') "
     "GROUP BY f.id ORDER BY f.iri;")
p = subprocess.run([MYSQL, '--default-character-set=utf8mb4', '-uroot', '-pzj780704', 'jingfangdb', '-e', q],
                   stdout=subprocess.PIPE, stderr=subprocess.PIPE)
print(p.stdout.decode('utf-8', 'replace'))
print('ERR:', p.stderr.decode('utf-8', 'replace'))
