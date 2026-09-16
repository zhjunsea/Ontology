# -*- coding: utf-8 -*-
import re
import os
from collections import defaultdict

pattern_class = r'<owl:Class rdf:about="#(\w+)\">'
pattern_label = r'<rdfs:label xml:lang="zh">([^<]+)</rdfs:label>'

files = ['taiyang.owl', 'shaoyang_yangming.owl', 'shaoyin_taiyin.owl', 'jueyin.owl', 'zabing.owl', 'duli.owl', 'hebing.owl', 'jianjia.owl', 'fanggen.owl', 'rules.owl']
base_path = r'D:/work/Ontology/TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox/ontology/fangzheng'

all_fangzheng = defaultdict(list)
fangzheng_names = defaultdict(list)
fangzheng_labels = {}  # name -> label

for fname in files:
    fpath = os.path.join(base_path, fname)
    with open(fpath, 'r', encoding='utf-8') as f:
        content = f.read()
    
    # 提取所有owl:Class
    classes = re.findall(pattern_class, content)
    labels = re.findall(pattern_label, content)
    
    # 匹配每个方证的label
    for cls in classes:
        if cls.endswith('zheng') or cls.endswith('Zheng'):
            all_fangzheng[fname].append(cls)
            fangzheng_names[cls].append(fname)
            # 尝试找到对应的label - 需要更精确的匹配

print('一、各文件方证总数统计')
print('='*60)
total = 0
for fname in files:
    count = len(all_fangzheng[fname])
    total += count
    print(f'{fname}: {count} 个方证')
print(f'总计: {total} 个方证')
print()

print('二、重复方证列表（名称完全一致）')
print('='*60)
duplicates = {k: v for k, v in fangzheng_names.items() if len(v) > 1}
if duplicates:
    for name, files_list in duplicates.items():
        print(f'{name}: 出现在 {files_list}')
else:
    print('未发现重复的方证名称')
print()

# 输出每个文件的方证列表
print('三、各文件方证详细列表')
print('='*60)
for fname in files:
    print(f'\n【{fname}】 ({len(all_fangzheng[fname])} 个方证)')
    for i, cls in enumerate(all_fangzheng[fname], 1):
        print(f'  {i}. {cls}')
