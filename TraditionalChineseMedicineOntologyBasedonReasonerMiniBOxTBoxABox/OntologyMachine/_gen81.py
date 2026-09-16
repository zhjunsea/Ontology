# -*- coding: utf-8 -*-
import io, sys
sys.path.insert(0, '.')
from _fangji81_data import FANGJI81

db = {}
for line in io.open('_db_yaowu.txt', encoding='utf-8').read().strip().split('\n'):
    iri, zh = line.split('\t')
    db[zh] = iri

NEWYAO = [
 ('Wenge','文蛤'),('Helile','诃梨勒'),('Puhui','蒲灰'),('Luanfa','乱发'),('Baiyu','白鱼'),
 ('Rongyan','戎盐'),('Zhizhu','蜘蛛'),('Shechuangzi','蛇床子'),('Zhugao','猪膏'),('Yunmu','云母'),
 ('Yangrou','羊肉'),('Tuguagen','土瓜根'),('Kuizi','葵子'),('Baiwei','白薇'),('Wangbuliuxing','王不留行'),
 ('Shuoduoixiye','蒴藋细叶'),('Sangdongnangenbaipi','桑东南根白皮'),('Jishibai','鸡屎白'),
 ('Qianfen','铅粉'),('Juhua','菊花'),('Hanshuishi','寒水石'),('Baishizhi','白石脂'),('Zishiying','紫石英'),
]
ALIAS = {'生地黄':'Shengdihuangzhi','川椒':'Shujiao','生竹茹':'Zhuru','白蜜':'Mi',
         '生姜汁':'Shengjiang','大猪胆':'Zhudanzhi','食蜜':'Mi','猪胆汁':'Zhudanzhi'}
allmap = dict(db)
for iri, zh in NEWYAO:
    allmap[zh] = iri
allmap.update(ALIAS)

# ---------- SQL ----------
sql = []
sql.append('')
sql.append('-- =============================================')
sql.append('-- B 类补建：81 首缺失方剂（依《伤寒论》《金匮要略》原文）')
sql.append('-- 含 23 味新增药物；柴胡白虎汤、杏子汤按后世公认组成补建并注明来源')
sql.append('-- =============================================')
sql.append('')
sql.append('-- ---------- 新增药物（23味） ----------')
vals = ','.join("('%s','%s')" % (iri, zh) for iri, zh in NEWYAO)
sql.append('INSERT INTO yaowu (iri, label) VALUES')
sql.append(vals + ';')
sql.append('')
sql.append('-- ---------- 新增方剂（81首） ----------')
fvals = ','.join("('%s','%s')" % (f[0], f[1]) for f in FANGJI81)
sql.append('INSERT INTO fangji (iri, label) VALUES')
sql.append(fvals + ';')
sql.append('')
sql.append('-- ---------- 新增方剂-药物关系 ----------')
for iri, zh, meds, jl, jf in FANGJI81:
    iris = [allmap[m] for m in meds]
    inlist = ','.join("'%s'" % x for x in iris)
    sql.append("INSERT INTO fangji_yaowu (fangji_id, yaowu_id) SELECT f.id, y.id FROM fangji f, yaowu y WHERE f.iri='%s' AND y.iri IN (%s);" % (iri, inlist))
sql.append('')
sql.append('-- ========== B 类补建完成 ==========')
io.open('_b81_sql_append.sql', 'w', encoding='utf-8').write('\n'.join(sql) + '\n')
print('SQL 追加块已生成，行数:', len(sql))

# ---------- OWL ----------
owl = []
owl.append('')
owl.append('    <!-- ==================== B 类补建方剂（81首） ==================== -->')
for iri, zh, meds, jl, jf in FANGJI81:
    owl.append('    <owl:NamedIndividual rdf:about="#%s">' % iri)
    owl.append('        <rdf:type rdf:resource="#Fangji"/>')
    owl.append('        <rdfs:label xml:lang="zh">%s</rdfs:label>' % zh)
    for m in meds:
        owl.append('        <you_yaowu rdf:resource="#%s"/>' % allmap[m])
    owl.append('        <you_jiliang xml:lang="zh">%s</you_jiliang>' % jl)
    owl.append('        <you_jianfufa xml:lang="zh">%s</you_jianfufa>' % jf)
    owl.append('    </owl:NamedIndividual>')
    owl.append('')
io.open('_b81_owl_append.owl', 'w', encoding='utf-8').write('\n'.join(owl) + '\n')
print('OWL 追加块已生成，行数:', len(owl))

# ---------- 药物 OWL ----------
yao = []
yao.append('')
yao.append('    <!-- ========== B 类补建新增药物（23味） ========== -->')
for iri, zh in NEWYAO:
    yao.append('    <owl:NamedIndividual rdf:about="#%s">' % iri)
    yao.append('        <rdf:type rdf:resource="#Yaowu"/>')
    yao.append('        <rdfs:label xml:lang="zh">%s</rdfs:label>' % zh)
    yao.append('    </owl:NamedIndividual>')
io.open('_b81_yaowu_append.owl', 'w', encoding='utf-8').write('\n'.join(yao) + '\n')
print('药物 OWL 追加块已生成，行数:', len(yao))
