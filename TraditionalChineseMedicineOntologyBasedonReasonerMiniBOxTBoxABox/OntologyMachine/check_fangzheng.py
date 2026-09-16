import os
import re
from collections import defaultdict

directory = r"D:\work\Ontology\TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox\ontology\fangzheng"
pattern = r'rdf:about="(#\w+zheng)"'

fangzheng_files = defaultdict(list)

for filename in os.listdir(directory):
    if filename.endswith('.owl'):
        filepath = os.path.join(directory, filename)
        with open(filepath, 'r', encoding='utf-8') as f:
            content = f.read()
            matches = re.findall(pattern, content)
            for match in matches:
                fangzheng_files[match].append(filename)

print("=" * 80)
print("方证重复验证结果")
print("=" * 80)
print()

# 统计每个文件的方证数量
file_counts = defaultdict(int)
for fangzheng, files in fangzheng_files.items():
    for file in files:
        file_counts[file] += 1

print("【一、各文件方证统计】")
print("-" * 80)
for file, count in sorted(file_counts.items()):
    print(f"{file}: {count} 个方证")
print(f"\n总计: {sum(file_counts.values())} 个方证出现")
print(f"独立方证数: {len(fangzheng_files)} 个")
print()

# 查找重复的方证
duplicates = {k: v for k, v in fangzheng_files.items() if len(v) > 1}

print("【二、重复方证检查】")
print("-" * 80)
if duplicates:
    print(f"发现 {len(duplicates)} 个重复方证:")
    print()
    for fangzheng, files in sorted(duplicates.items()):
        print(f"  ★ {fangzheng}")
        print(f"    出现文件: {', '.join(files)}")
        print()
else:
    print("无重复方证")
