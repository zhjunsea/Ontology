# -*- coding: utf-8 -*-
import subprocess, io, sys
MYSQL = r"C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe"
sql = io.open('_b81_sql_append.sql', encoding='utf-8').read()
p = subprocess.run([MYSQL, '--default-character-set=utf8mb4', '-uroot', '-pzj780704', 'jingfangdb'],
                   input=sql.encode('utf-8'), stdout=subprocess.PIPE, stderr=subprocess.PIPE)
print('returncode:', p.returncode)
print('stdout:', p.stdout.decode('utf-8', 'replace'))
print('stderr:', p.stderr.decode('utf-8', 'replace'))
