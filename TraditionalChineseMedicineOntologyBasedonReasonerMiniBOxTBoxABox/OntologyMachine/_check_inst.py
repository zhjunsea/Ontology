# -*- coding: utf-8 -*-
import re

# 1. 症状类定义
zz = open('../ontology/tcm-zhengzhuang.owl', encoding='utf-8').read()
zz_classes = set(re.findall(r'<owl:Class rdf:about="#([^"]+)"', zz))
# 2. 症状实例
zab = open('../ontology/tcm-zhengzhuang-abox.owl', encoding='utf-8').read()
zz_inst = set(re.findall(r'rdf:about="#([^"]+)_instance"', zab))
# 3. 脉象类
mx = open('../ontology/tcm-maixiang.owl', encoding='utf-8').read()
mx_classes = set(re.findall(r'<owl:Class rdf:about="#([^"]+)"', mx))
# 4. 脉象实例
mxab = open('../ontology/tcm-maixiang-abox.owl', encoding='utf-8').read()
mx_inst = set(re.findall(r'rdf:about="#([^"]+)_instance"', mxab))

print('症状类:', len(zz_classes), ' 症状实例:', len(zz_inst))
print('脉象类:', len(mx_classes), ' 脉象实例:', len(mx_inst))

# 检查方证定义引用的悬空症状
fz_files = ['taiyang','shaoyang_yangming','shaoyin_taiyin','jueyin','zabing','duli','hebing','jianjia','fanggen','rules']
print('\n===== 方证本体中悬空症状引用（类不存在）=====')
all_missing = {}
for f in fz_files:
    try:
        text = open(f'../ontology/fangzheng/{f}.owl', encoding='utf-8').read()
    except FileNotFoundError:
        continue
    refs = set(re.findall(r'#you_zhengzhuang"/>\s*<owl:someValuesFrom rdf:resource="#([^"]+)"', text))
    refs |= set(re.findall(r'#you_maixiang"/>\s*<owl:someValuesFrom rdf:resource="#([^"]+)"', text))
    for r in refs:
        if r not in zz_classes and r not in mx_classes:
            all_missing.setdefault(r, []).append(f)
for k, v in sorted(all_missing.items()):
    print(f'  {k}: {v}')

# 检查测试用例症状实例是否存在
print('\n===== 关键测试症状实例检查 =====')
for s in ['Wulaoxuji','Bunengyinshi','Jifujiacuo','Liangmuanhei','Fuman',
          'Chanhoufutong','Fanman','Budewo','Shaofujijie','Citong',
          'Weiou','XinqiBuzu','Tuxue','Nvxue','Shouzuleng','Futong']:
    print(f'  {s}: 类={s in zz_classes} 实例={s in zz_inst}')
print('\n脉象: Chenshimai 类=', 'Chenshimai' in mx_classes, ' 实例=', 'Chenshimai' in mx_inst)
print('脉象: Xianmai 类=', 'Xianmai' in mx_classes, ' 实例=', 'Xianmai' in mx_inst)
