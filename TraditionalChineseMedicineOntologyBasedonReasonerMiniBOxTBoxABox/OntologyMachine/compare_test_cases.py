import re
import os

# 读取本体文件中的方证列表
def extract_fangzheng_from_owl(owl_file):
    with open(owl_file, 'r', encoding='utf-8') as f:
        content = f.read()
    # 提取rdf:about中的方证名称
    pattern = r'rdf:about="#(\w+zheng)"'
    matches = re.findall(pattern, content)
    # 也提取包含zheng的类（即使没有zheng后缀）
    pattern2 = r'rdf:about="#(\w+)(?<!zheng)"'
    matches2 = re.findall(pattern2, content)
    # 过滤出包含zheng的类
    matches2 = [m for m in matches2 if 'zheng' in m.lower()]
    return set(matches + matches2)

# 读取测试类中的fangzheng标识
def extract_fangzheng_from_test(test_file):
    with open(test_file, 'r', encoding='utf-8') as f:
        content = f.read()
    # 提取assertFangzheng的第三个参数
    pattern = r'assertFangzheng\("[^"]+",\s*"[^"]+",\s*"(\w+zheng)"'
    matches = re.findall(pattern, content)
    return set(matches)

# 本体文件路径
owl_dir = r"D:\work\Ontology\TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox\ontology\fangzheng"
test_dir = r"D:\work\Ontology\TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox\OntologyMachine\OntologyFramework\src\test\java\com\ocean\ontologyframework\tcm"

# 本体文件与测试类的映射
mapping = {
    "taiyang.owl": ("TaiyangFangzhengTest.java", "TaiyangFangzheng"),
    "shaoyang_yangming.owl": ("ShaoyangYangmingFangzhengTest.java", "ShaoyangYangmingFangzheng"),
    "shaoyin_taiyin.owl": ("ShaoyinTaiyinFangzhengTest.java", "ShaoyinTaiyinFangzheng"),
    "jueyin.owl": ("JueyinFangzhengTest.java", "JueyinFangzheng"),
    "zabing.owl": ("ZabingFangzhengTest.java", "ZabingFangzheng"),
    "duli.owl": ("DuliFangzhengTest.java", "DuliFangzheng"),
    "hebing.owl": ("HebingFangzhengTest.java", "HebingFangzheng"),
}

# 构建方证到本体的映射
fangzheng_to_owl = {}
for owl_file in mapping.keys():
    owl_path = os.path.join(owl_dir, owl_file)
    fangzhengs = extract_fangzheng_from_owl(owl_path)
    for fz in fangzhengs:
        fangzheng_to_owl[fz] = owl_file

print("=" * 80)
print("测试案例与本体方证对比报告（增强版）")
print("=" * 80)

all_migrations = []

for owl_file, (test_file, test_name) in mapping.items():
    owl_path = os.path.join(owl_dir, owl_file)
    test_path = os.path.join(test_dir, test_file)
    
    owl_fangzheng = extract_fangzheng_from_owl(owl_path)
    test_fangzheng = extract_fangzheng_from_test(test_path)
    
    print(f"\n【{test_name}】")
    print(f"  本体文件: {owl_file} ({len(owl_fangzheng)} 个方证)")
    print(f"  测试类: {test_file} ({len(test_fangzheng)} 个测试案例)")
    
    # 检查测试案例是否在本体中
    not_in_owl = test_fangzheng - owl_fangzheng
    if not_in_owl:
        print(f"  ❌ 测试案例不在对应本体中 ({len(not_in_owl)} 个):")
        for fz in sorted(not_in_owl):
            # 找出这个方证在哪个本体中
            target_owl = fangzheng_to_owl.get(fz, "未找到")
            if target_owl != "未找到" and target_owl != owl_file:
                target_test = mapping[target_owl][1]
                print(f"     - {fz} → 应迁移到 {target_test} ({target_owl})")
                all_migrations.append((test_name, target_test, fz, target_owl))
            else:
                print(f"     - {fz} → {target_owl}")
    else:
        print(f"  ✓ 所有测试案例都在对应本体中")

print("\n" + "=" * 80)
print("迁移方案汇总")
print("=" * 80)

# 按源测试类分组
migrations_by_source = {}
for source_test, target_test, fz, target_owl in all_migrations:
    if source_test not in migrations_by_source:
        migrations_by_source[source_test] = []
    migrations_by_source[source_test].append((fz, target_test, target_owl))

for source_test, migrations in migrations_by_source.items():
    print(f"\n【{source_test}】需要迁移 {len(migrations)} 个测试案例:")
    for fz, target_test, target_owl in migrations:
        print(f"  - {fz} → {target_test} ({target_owl})")

print("\n" + "=" * 80)
print(f"总计: {len(all_migrations)} 个测试案例需要迁移")
print("=" * 80)
