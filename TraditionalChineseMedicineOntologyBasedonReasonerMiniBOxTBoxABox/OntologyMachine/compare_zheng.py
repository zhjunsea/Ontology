import re
import os

# Extract zheng names from original file
original_file = r"D:\work\Ontology\TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox\ontology\extracted\tcm-fangzheng-jianjia.owl"

with open(original_file, 'r', encoding='utf-8') as f:
    content = f.read()

# Find all zheng names
pattern = r'rdf:about="#(\w+zheng)"'
original_zhengs = set(re.findall(pattern, content))

print(f"原始文件中的方证总数: {len(original_zhengs)}")

# Extract zheng names from fangzheng directory
fangzheng_dir = r"D:\work\Ontology\TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox\ontology\fangzheng"
fangzheng_zhengs = set()

for filename in os.listdir(fangzheng_dir):
    if filename.endswith('.owl'):
        filepath = os.path.join(fangzheng_dir, filename)
        with open(filepath, 'r', encoding='utf-8') as f:
            content = f.read()
        zhengs = re.findall(pattern, content)
        fangzheng_zhengs.update(zhengs)
        print(f"  {filename}: {len(zhengs)} 个方证")

print(f"\nfangzheng目录中的方证总数（去重后）: {len(fangzheng_zhengs)}")

# Find missing zhengs
missing_zhengs = original_zhengs - fangzheng_zhengs

print(f"\n缺失的方证数量: {len(missing_zhengs)}")
if missing_zhengs:
    print("缺失的方证列表:")
    for zheng in sorted(missing_zhengs):
        print(f"  - {zheng}")
else:
    print("fangzheng目录完全包含了原始文件中的所有方证！")
