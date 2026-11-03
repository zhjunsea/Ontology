# -*- coding: utf-8 -*-
"""把 gen_daikao.py 生成的 18 条残卷条目：
   (1) 追加进 JobWorker 的 XIANGZI_RULE 静态块；
   (2) 追加进 ontology/database/data.sql 的 xiangzi_rule INSERT。
幂等：已插入则跳过。
"""
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
JW = os.path.join(ROOT, 'src/main/java/com/ocean/ontologyframework/plumblossom/PlumBlossomOntologyJobWorker.java')
SQL = os.path.join(ROOT, 'ontology/database/data.sql')
JAVA = open(os.path.join(ROOT, '_gen/out/daikao_java.txt'), encoding='utf-8').read().rstrip('\n')
SQLS = open(os.path.join(ROOT, '_gen/out/daikao_sql.txt'), encoding='utf-8').read().rstrip('\n')

# (1) JobWorker
s = open(JW, encoding='utf-8').read()
anchor = 'putXz("赋文", "探玄赋", '
idx = s.rindex(anchor)
eol = s.index('\n', idx)
if 'putXz("六十四卦次第歌"' in s:
    print('Java: already applied')
else:
    block = '\n' + '\n'.join('        ' + l.strip() for l in JAVA.split('\n'))
    s = s[:eol] + block + s[eol:]
    open(JW, 'w', encoding='utf-8').write(s)
    print('Java: inserted', JAVA.count('\n') + 1, 'lines')

# (2) data.sql
d = open(SQL, encoding='utf-8').read()
anchor2 = "('XiangZi_18_05'"
i2 = d.index(anchor2)
end = d.index(';', i2)
if 'XiangZi_19_01' in d:
    print('SQL: already applied')
else:
    d = d[:end] + ',\n' + SQLS + d[end:]
    open(SQL, 'w', encoding='utf-8').write(d)
    print('SQL: inserted', SQLS.count('\n') + 1, 'rows')
