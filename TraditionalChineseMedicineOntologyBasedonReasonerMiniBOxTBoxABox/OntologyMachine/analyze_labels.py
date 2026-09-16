# -*- coding: utf-8 -*-
import re
import os
from collections import defaultdict

pattern_class = r'<owl:Class rdf:about="#(\w+)\">'
pattern_label = r'<rdfs:label xml:lang="zh">([^<]+)</rdfs:label>'

files = ['taiyang.owl', 'shaoyang_yangming.owl', 'shaoyin_taiyin.owl', 'jueyin.owl', 'zabing.owl', 'duli.owl', 'hebing.owl', 'jianjia.owl', 'fanggen.owl', 'rules.owl']
base_path = r'D:/work/Ontology/TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox/ontology/fangzheng'

# 存储方证名称和label
fangzheng_data = []  # [(filename, name, label)]

for fname in files:
    fpath = os.path.join(base_path, fname)
    with open(fpath, 'r', encoding='utf-8') as f:
        content = f.read()
    
    # 找到所有owl:Class及其位置
    class_matches = list(re.finditer(pattern_class, content))
    label_matches = list(re.finditer(pattern_label, content))
    
    for match in class_matches:
        name = match.group(1)
        if name.endswith('zheng') or name.endswith('Zheng'):
            # 找到对应的label - 在class后面最近的label
            class_pos = match.end()
            corresponding_label = None
            for label_match in label_matches:
                label_pos = label_match.start()
                if label_pos > class_pos:
                    # 检查是否在下一个class之前
                    next_class_pos = len(content)
                    for cm in class_matches:
                        if cm.start() > class_pos:
                            next_class_pos = cm.start()
                            break
                    if label_pos < next_class_pos:
                        corresponding_label = label_match.group(1)
                        break
            
            fangzheng_data.append((fname, name, corresponding_label))

print('四、方证名称与label对照表')
print('='*80)
print(f'{"文件名":<25} {"方证名称":<45} {"Label"}')
print('-'*80)
for fname, name, label in fangzheng_data:
    label_str = label if label else "[未找到]"
    print(f'{fname:<25} {name:<45} {label_str}')
