import re
import os

# Original file path
original_file = r"D:\work\Ontology\TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox\ontology\extracted\tcm-fangzheng-jianjia.owl"

# Fangzheng directory
fangzheng_dir = r"D:\work\Ontology\TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox\ontology\fangzheng"

# Pattern to match zheng names
pattern = r'rdf:about="(#\w+zheng)"'

# Extract from original file
original_zhengs = set()
with open(original_file, 'r', encoding='utf-8') as f:
    content = f.read()
    matches = re.findall(pattern, content)
    original_zhengs = set(matches)

print(f"原始文件中的方证总数: {len(original_zhengs)}")

# Extract from all owl files in fangzheng directory
fangzheng_zhengs = set()
for filename in os.listdir(fangzheng_dir):
    if filename.endswith('.owl'):
        filepath = os.path.join(fangzheng_dir, filename)
        with open(filepath, 'r', encoding='utf-8') as f:
            content = f.read()
            matches = re.findall(pattern, content)
            fangzheng_zhengs.update(matches)

print(f"fangzheng目录中的方证总数: {len(fangzheng_zhengs)}")

# Find missing zhengs
missing_zhengs = original_zhengs - fangzheng_zhengs
print(f"\n缺失的方证数量: {len(missing_zhengs)}")
print("缺失的方证列表:")
for zheng in sorted(missing_zhengs):
    print(f"  - {zheng}")

# Check if fangzheng contains all
if len(missing_zhengs) == 0:
    print("\n✓ 验证结果: fangzheng目录完全包含了原始文件中的所有方证")
else:
    print(f"\n✗ 验证结果: fangzheng目录缺少 {len(missing_zhengs)} 个方证")
