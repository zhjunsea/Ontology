import re
import os

dir_path = r'D:\work\Ontology\TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox\ontology\fangzheng'
files = ['taiyang.owl', 'shaoyang_yangming.owl', 'shaoyin_taiyin.owl', 'jueyin.owl', 'zabing.owl', 'duli.owl', 'hebing.owl', 'jianjia.owl', 'fanggen.owl', 'rules.owl']

all_fangzheng = {}
file_counts = {}

for file in files:
    file_path = os.path.join(dir_path, file)
    with open(file_path, 'r', encoding='utf-8') as f:
        content = f.read()
    matches = re.findall(r'rdf:about="#(\w+zheng)"', content)
    file_counts[file] = len(matches)
    for m in matches:
        if m not in all_fangzheng:
            all_fangzheng[m] = []
        all_fangzheng[m].append(file)

print('=== 各文件方证数量 ===')
for file, count in file_counts.items():
    print(f'{file}: {count}个')

print()
print('=== 重复方证 ===')
duplicates = {k: v for k, v in all_fangzheng.items() if len(v) > 1}
if duplicates:
    for fz, files in duplicates.items():
        print(f'{fz}: {files}')
else:
    print('无重复方证')
