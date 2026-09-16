# -*- coding: utf-8 -*-
import io

# ---------- 1. SQL ----------
p = '../ontology/database/fangji-yaowu.sql'
s = io.open(p, encoding='utf-8').read()
append = io.open('_b81_sql_append.sql', encoding='utf-8').read()
marker = '-- ---------- 插入十八反关系 ----------'
assert marker in s, 'SQL 锚点未找到'
assert 'B 类补建' not in s, 'SQL 已包含补建内容，勿重复'
s = s.replace(marker, append.strip() + '\n\n' + marker, 1)
io.open(p, 'w', encoding='utf-8').write(s)
print('SQL 已更新')

# ---------- 2. 方剂 OWL ----------
p2 = '../ontology/tcm-fangji-abox.owl'
s2 = io.open(p2, encoding='utf-8').read()
a2 = io.open('_b81_owl_append.owl', encoding='utf-8').read()
assert 'B 类补建方剂' not in s2, '方剂 OWL 已包含补建内容'
assert s2.rstrip().endswith('</rdf:RDF>')
s2 = s2.rstrip()[:-len('</rdf:RDF>')].rstrip() + '\n' + a2 + '\n</rdf:RDF>\n'
io.open(p2, 'w', encoding='utf-8').write(s2)
print('方剂 OWL 已更新')

# ---------- 3. 药物 OWL ----------
p3 = '../ontology/tcm-yaowu-abox.owl'
s3 = io.open(p3, encoding='utf-8').read()
a3 = io.open('_b81_yaowu_append.owl', encoding='utf-8').read()
assert 'B 类补建新增药物' not in s3, '药物 OWL 已包含补建内容'
assert s3.rstrip().endswith('</rdf:RDF>')
s3 = s3.rstrip()[:-len('</rdf:RDF>')].rstrip() + '\n' + a3 + '\n</rdf:RDF>\n'
io.open(p3, 'w', encoding='utf-8').write(s3)
print('药物 OWL 已更新')
