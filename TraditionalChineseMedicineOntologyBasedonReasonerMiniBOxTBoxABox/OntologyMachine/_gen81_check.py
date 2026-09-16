# -*- coding: utf-8 -*-
import io, sys, re
sys.path.insert(0, '.')
from _fangji81_data import FANGJI81

# 现有 175 味药：IRI -> 中文
db = {}
for line in io.open('_db_yaowu.txt', encoding='utf-8').read().strip().split('\n'):
    iri, zh = line.split('\t')
    db[zh] = iri

# 23 味新药
NEWYAO = {
 '文蛤':'Wenge','诃梨勒':'Helile','蒲灰':'Puhui','乱发':'Luanfa','白鱼':'Baiyu',
 '戎盐':'Rongyan','蜘蛛':'Zhizhu','蛇床子':'Shechuangzi','猪膏':'Zhugao','云母':'Yunmu',
 '羊肉':'Yangrou','土瓜根':'Tuguagen','葵子':'Kuizi','白薇':'Baiwei','王不留行':'Wangbuliuxing',
 '蒴藋细叶':'Shuoduoixiye','桑东南根白皮':'Sangdongnangenbaipi','鸡屎白':'Jishibai',
 '铅粉':'Qianfen','菊花':'Juhua','寒水石':'Hanshuishi','白石脂':'Baishizhi','紫石英':'Zishiying',
}
# 别名 -> 现有 IRI
ALIAS = {
 '生地黄':'Shengdihuangzhi','川椒':'Shujiao','生竹茹':'Zhuru','白蜜':'Mi',
 '生姜汁':'Shengjiang','大猪胆':'Zhudanzhi','食蜜':'Mi','猪胆汁':'Zhudanzhi',
}

allmap = dict(db)
allmap.update(NEWYAO)
allmap.update(ALIAS)

unmatched = set()
used_new = set()
for iri, zh, meds, jl, jf in FANGJI81:
    for m in meds:
        if m in allmap:
            if m in NEWYAO:
                used_new.add(m)
        else:
            unmatched.add((zh, m))

print('未匹配药物:', len(unmatched))
for zh, m in sorted(unmatched):
    print('  ', zh, '->', m)
print()
print('实际用到的23味新药:', len(used_new), '/23')
for m in NEWYAO:
    if m not in used_new:
        print('  未使用:', m)
print()
print('方剂总数:', len(FANGJI81))
