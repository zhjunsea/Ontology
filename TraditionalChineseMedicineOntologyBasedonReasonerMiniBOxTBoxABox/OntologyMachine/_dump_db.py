import subprocess, re

MYSQL = r"C:/Program Files/MySQL/MySQL Server 8.0/bin/mysql.exe"

def q(sql):
    r = subprocess.run([MYSQL, '--default-character-set=utf8mb4', '-uroot', '-pzj780704', 'jingfangdb', '-N', '-B', '-e', sql],
                       capture_output=True, text=True, encoding='utf-8', errors='replace')
    return [l.split('\t') for l in r.stdout.strip().split('\n') if l]

yaowu = q("SELECT iri,label FROM yaowu")
fangji = q("SELECT iri,label FROM fangji")
print('yaowu:', len(yaowu), 'fangji:', len(fangji))
with open('_db_yaowu.txt', 'w', encoding='utf-8') as f:
    for iri, lab in yaowu:
        f.write(f'{iri}\t{lab}\n')
with open('_db_fangji.txt', 'w', encoding='utf-8') as f:
    for iri, lab in fangji:
        f.write(f'{iri}\t{lab}\n')
print('written')
