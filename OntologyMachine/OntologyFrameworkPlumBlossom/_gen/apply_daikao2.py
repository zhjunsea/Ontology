# -*- coding: utf-8 -*-
"""应用 gen_daikao2：五赋全录（替换）＋ 卷四/五余篇（追加）。幂等。"""
import os, re, json

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
JW = os.path.join(ROOT, 'src/main/java/com/ocean/ontologyframework/plumblossom/PlumBlossomOntologyJobWorker.java')
SQL = os.path.join(ROOT, 'ontology/database/data.sql')
D = json.load(open(os.path.join(ROOT, '_gen', 'daikao2.json'), encoding='utf-8'))

def esc(t):
    return t.replace("\\", "\\\\").replace('"', '\\"')

# ---------- data.sql ----------
s = open(SQL, encoding='utf-8').read()
for cat, key, txt, src in D['replace']:
    pat = re.compile(r"('XiangZi_\d+_\d+', '赋文', '" + re.escape(key) + r"', )'[^']*'")
    assert pat.search(s), 'missing row ' + key
    s = pat.sub(lambda m: m.group(1) + "'" + txt.replace("'", "''") + "'", s, count=1)

new_rows = []
for name, cat, key, txt, src in D['append']:
    new_rows.append("('%s', '%s', '%s', '%s', '%s', '%s')" % (
        name, cat, key, txt.replace("'", "''"), src.replace("'", "''"),
        ("相字·%s·%s" % (cat, key)).replace("'", "''")))
if 'XiangZi_40_01' in s:
    print('SQL append: already')
else:
    end = s.rindex(';')  # xiangzi INSERT 末尾分号（文件末尾）
    s = s[:end] + ',\n' + ',\n'.join(new_rows) + s[end:]
open(SQL, 'w', encoding='utf-8').write(s)
print('SQL done; rows=', len(new_rows))

# ---------- Java ----------
j = open(JW, encoding='utf-8').read()
for cat, key, txt, src in D['replace']:
    pat = re.compile(r'putXz\("赋文", "' + re.escape(key) + r'", "[^"]*"\);')
    assert pat.search(j), 'missing putXz ' + key
    j = pat.sub(lambda m: 'putXz("赋文", "%s", "%s");' % (key, esc(txt)), j, count=1)

if 'putXz("七言作用歌"' in j:
    print('Java append: already')
else:
    lines = ['        putXz("%s", "%s", "%s");' % (cat, key, esc(txt))
             for name, cat, key, txt, src in D['append']]
    anchor = j.rindex('putXz("赋文", "探玄赋"')
    eol = j.index('\n', anchor)
    j = j[:eol] + '\n' + '\n'.join(lines) + j[eol:]
open(JW, 'w', encoding='utf-8').write(j)
print('Java done; lines=', len(lines))
