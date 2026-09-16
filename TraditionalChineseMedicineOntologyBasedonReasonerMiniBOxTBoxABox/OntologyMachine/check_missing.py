import re
import os

# 读取本体文件中的方证列表
def extract_fangzheng_from_owl(owl_file):
    with open(owl_file, 'r', encoding='utf-8') as f:
        content = f.read()
    pattern = r'rdf:about="#(\w+zheng)"'
    matches = re.findall(pattern, content)
    return set(matches)

# 当前fangzheng目录
owl_dir = r"D:\work\Ontology\TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox\ontology\fangzheng"

# 提取当前所有本体文件中的方证
current_fangzheng = set()
for owl_file in os.listdir(owl_dir):
    if owl_file.endswith('.owl'):
        owl_path = os.path.join(owl_dir, owl_file)
        fz = extract_fangzheng_from_owl(owl_path)
        current_fangzheng.update(fz)

# 测试案例中的方证
test_fz = ['banxiamahuangwanzheng', 'daqinglongtangzheng', 'fulinggancaotangzheng', 
           'fulingguizhigancaodazaotangzheng', 'gancaomahuangtangzheng',
           'gegenjiabanxiantangzheng', 'gegentangzheng', 'guizhijiadahuangtangzheng',
           'guizhijiashaoyaotangzheng', 'houpomahuangtangzheng',
           'mahuanglianqiaochixiaodoutangzheng', 'maxingshigantangzheng',
           'maxingyigantangzheng', 'sheganmahuangtangzheng', 'wulingsanzheng',
           'xiaoqinglongjiashigaotangzheng', 'xiaoqinglongtangzheng',
           'yuebijiabanxiatangzheng', 'yuebijiazhutangzheng', 'yuebitangzheng']

print("检查测试案例中的方证是否在当前10个本体文件中:")
for fz in test_fz:
    if fz in current_fangzheng:
        print(f"  ✓ {fz}")
    else:
        print(f"  ✗ {fz} (不存在)")

# 检查是否有类似的方证名称
print("\n\n检查类似的方证名称:")
for fz in test_fz:
    if fz not in current_fangzheng:
        # 查找类似的方证
        similar = [c for c in current_fangzheng if fz[:10] in c or c[:10] in fz[:10]]
        if similar:
            print(f"  {fz} → 可能类似: {similar[:3]}")
