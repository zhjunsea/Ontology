import re
import os

# 读取本体文件中的方证列表
def extract_fangzheng_from_owl(owl_file, format_type='lowercase'):
    with open(owl_file, 'r', encoding='utf-8') as f:
        content = f.read()
    
    if format_type == 'lowercase':
        # 当前格式: xiaochaihutangzheng (全小写)
        pattern = r'rdf:about="#(\w+zheng)"'
    else:
        # 原始格式: XiaoChaihuTangZheng (驼峰)
        pattern = r'rdf:about="#([A-Z][a-zA-Z]+Zheng)"'
    
    matches = re.findall(pattern, content)
    return set(matches)

# 驼峰转小写
def camel_to_lower(name):
    # XiaoChaihuTangZheng -> xiaochaihutangzheng
    # 直接去掉大写字母，全部转小写
    result = []
    for c in name:
        if c.isupper():
            result.append(c.lower())
        elif c.islower():
            result.append(c)
    return ''.join(result)

# 原始文件路径
original_file = r"D:\work\Ontology\TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox\ontology_pureFile\tcm-fangzheng-jianjia.owl"

# 当前fangzheng目录
owl_dir = r"D:\work\Ontology\TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox\ontology\fangzheng"

# 提取原始文件中的方证（驼峰格式）
original_fangzheng_camel = extract_fangzheng_from_owl(original_file, format_type='camel')
original_fangzheng = set(camel_to_lower(fz) for fz in original_fangzheng_camel)

print("=" * 80)
print("原始文件 tcm-fangzheng-jianjia.owl 中的方证")
print("=" * 80)
print(f"总计: {len(original_fangzheng)} 个方证\n")
print("原始文件方证列表（驼峰 → 小写）:")
for fz in sorted(original_fangzheng)[:20]:
    print(f"  - {fz}")

# 提取当前所有本体文件中的方证
current_fangzheng = set()
for owl_file in os.listdir(owl_dir):
    if owl_file.endswith('.owl'):
        owl_path = os.path.join(owl_dir, owl_file)
        fz = extract_fangzheng_from_owl(owl_path, format_type='lowercase')
        current_fangzheng.update(fz)

print(f"\n当前10个本体文件总计: {len(current_fangzheng)} 个方证")

# 找出缺失的方证
missing = original_fangzheng - current_fangzheng

print("\n" + "=" * 80)
print("缺失的方证（在原始文件中有，但在当前10个本体文件中没有）")
print("=" * 80)
if missing:
    print(f"总计: {len(missing)} 个方证缺失\n")
    for fz in sorted(missing):
        print(f"  - {fz}")
else:
    print("✓ 所有方证都已包含在当前本体文件中")

# 检查哪些方证是当前新增的
new_fz = current_fangzheng - original_fangzheng
if new_fz:
    print("\n" + "=" * 80)
    print("新增的方证（在当前本体中有，但在原始文件中没有）")
    print("=" * 80)
    print(f"总计: {len(new_fz)} 个新增方证\n")
    for fz in sorted(new_fz)[:20]:
        print(f"  - {fz}")
    if len(new_fz) > 20:
        print(f"  ... 还有 {len(new_fz) - 20} 个")
