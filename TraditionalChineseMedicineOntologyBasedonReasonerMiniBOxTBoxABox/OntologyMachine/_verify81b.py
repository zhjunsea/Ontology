# -*- coding: utf-8 -*-
import io, re
s = io.open('../ontology/database/fangji-yaowu.sql', encoding='utf-8').read()
blk = s.split('-- ---------- 新增方剂（81首） ----------')[1].split(';')[0]
print('新增方剂条数:', len(re.findall(r"\('[A-Za-z]+','", blk)))
print('含 Mijiandaofang:', 'Mijiandaofang' in s)
print('含 Sanhuangtang:', 'Sanhuangtang' in s)
# 检查是否有重复方剂 IRI
allf = re.findall(r"INSERT INTO fangji \(iri, label\) VALUES\s*(.*?);", s, re.S)
iris = re.findall(r"\('([A-Za-z]+)','", allf[0]) + re.findall(r"\('([A-Za-z]+)','", allf[1])
print('方剂 IRI 总数:', len(iris), '唯一:', len(set(iris)))
dup = [x for x in set(iris) if iris.count(x) > 1]
print('重复方剂 IRI:', dup)
