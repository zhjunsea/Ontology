import re
import os

# 读取本体文件中的方证列表（支持驼峰格式）
def extract_fangzheng_from_owl_camel(owl_file):
    with open(owl_file, 'r', encoding='utf-8') as f:
        content = f.read()
    # 提取rdf:about中的方证名称（驼峰格式）
    pattern = r'rdf:about="#([A-Z][a-zA-Z]+Zheng)"'
    matches = re.findall(pattern, content)
    return set(matches)

# 驼峰转小写
def camel_to_lower(name):
    result = []
    for c in name:
        if c.isupper():
            result.append(c.lower())
        elif c.islower():
            result.append(c)
    return ''.join(result)

# 检查v2目录中的tcm-tbox.owl
v2_file = r"D:\work\Ontology\TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox\ontology_pureFile\v2\tcm-tbox.owl"

# 提取方证
v2_fangzheng_camel = extract_fangzheng_from_owl_camel(v2_file)
v2_fangzheng = set(camel_to_lower(fz) for fz in v2_fangzheng_camel)

print("=" * 80)
print("v2/tcm-tbox.owl 中的方证")
print("=" * 80)
print(f"总计: {len(v2_fangzheng)} 个方证\n")

# 检查测试案例中的方证
test_fz = ['banxiamahuangwanzheng', 'daqinglongtangzheng', 'fulinggancaotangzheng', 
           'fulingguizhigancaodazaotangzheng', 'gancaomahuangtangzheng',
           'gegenjiabanxiantangzheng', 'gegentangzheng', 'guizhijiadahuangtangzheng',
           'guizhijiashaoyaotangzheng', 'houpomahuangtangzheng',
           'mahuanglianqiaochixiaodoutangzheng', 'maxingshigantangzheng',
           'maxingyigantangzheng', 'sheganmahuangtangzheng', 'wulingsanzheng',
           'xiaoqinglongjiashigaotangzheng', 'xiaoqinglongtangzheng',
           'yuebijiabanxiatangzheng', 'yuebijiazhutangzheng', 'yuebitangzheng']

print("检查测试案例中的方证是否在v2/tcm-tbox.owl中:")
found = []
missing = []
for fz in test_fz:
    if fz in v2_fangzheng:
        found.append(fz)
        print(f"  ✓ {fz}")
    else:
        missing.append(fz)
        print(f"  ✗ {fz} (不在v2中)")

print(f"\n在v2中找到: {len(found)} 个")
print(f"在v2中缺失: {len(missing)} 个")

# 如果缺失，检查是否在当前本体中
if missing:
    print("\n检查缺失的方证是否在当前10个本体文件中:")
    owl_dir = r"D:\work\Ontology\TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox\ontology\fangzheng"
    
    current_fz = set()
    for owl_file in os.listdir(owl_dir):
        if owl_file.endswith('.owl'):
            owl_path = os.path.join(owl_dir, owl_file)
            fz_set = extract_fangzheng_from_owl_camel(owl_path)
            current_fz.update(camel_to_lower(f) for f in fz_set)
    
    for fz in missing:
        if fz in current_fz:
            print(f"  ✓ {fz} (在当前本体中)")
        else:
            print(f"  ✗ {fz} (也不在当前本体中)")
