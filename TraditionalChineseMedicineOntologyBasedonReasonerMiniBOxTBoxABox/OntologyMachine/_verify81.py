# -*- coding: utf-8 -*-
import io, re
# SQL 校验
s = io.open('../ontology/database/fangji-yaowu.sql', encoding='utf-8').read()
print('SQL 新增药物 INSERT:', s.count("('Wenge','文蛤')"))
print('SQL 新增方剂条数:', len(re.findall(r"\('([A-Za-z]+)','[^']+'\)", s.split('INSERT INTO fangji (iri, label) VALUES')[1].split(';')[0])))
print('SQL 方剂-药物关系总数:', s.count('INSERT INTO fangji_yaowu'))
print('SQL 以 </rdf 结尾?', s.rstrip().endswith('-- ========== 完成 =========='))

# 方剂 OWL 校验
s2 = io.open('../ontology/tcm-fangji-abox.owl', encoding='utf-8').read()
ids = re.findall(r'<owl:NamedIndividual rdf:about="#([^"]+)"', s2)
print('方剂 OWL 个体总数:', len(ids), '(应=261)')
print('方剂 OWL 重复 IRI:', len(ids) - len(set(ids)))
print('方剂 OWL 结尾:', s2.rstrip()[-20:])

# 药物 OWL 校验
s3 = io.open('../ontology/tcm-yaowu-abox.owl', encoding='utf-8').read()
ids3 = re.findall(r'<owl:NamedIndividual rdf:about="#([^"]+)"', s3)
print('药物 OWL 个体总数:', len(ids3))
print('药物 OWL 结尾:', s3.rstrip()[-20:])
