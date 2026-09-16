import re
from pypinyin import pinyin, Style

def chinese_to_pinyin(text):
    """将中文转换为拼音，返回首字母大写其余小写的字符串"""
    result = []
    for char in text:
        if '\u4e00' <= char <= '\u9fff':
            py = pinyin(char, style=Style.NORMAL)[0][0]
            result.append(py)
        else:
            result.append(char)
    pinyin_str = ''.join(result)
    return pinyin_str[0].upper() + pinyin_str[1:].lower() if pinyin_str else ''

# 读取文件
file_path = r"D:\work\Ontology\TraditionalChineseMedicineOntologyBasedonReasonerTBoxABox\ontology\tcm-fangzheng-jianjia.owl"
with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

# 方法1：匹配带label的owl:Class定义块
# 使用更宽松的正则：从 <owl:Class rdf:about="#..."> 到 </owl:Class>
pattern = r'<owl:Class rdf:about="#(\w+)">(.*?)</owl:Class>'
matches = re.findall(pattern, content, re.DOTALL)

print("=" * 80)
print("方证名称拼音一致性验证报告")
print(f"文件: tcm-fangzheng-jianjia.owl")
print("=" * 80)

errors = []
warnings = []
checked = 0
skipped = []

for class_name, class_body in matches:
    # 提取中文label
    label_match = re.search(r'<rdfs:label xml:lang="zh">(.*?)</rdfs:label>', class_body)
    if not label_match:
        skipped.append(class_name)
        continue
    
    cn_label = label_match.group(1)
    checked += 1
    
    # 去除可能的后缀如 _Taiyang
    base_class_name = class_name
    if '_' in class_name:
        base_class_name = class_name.split('_')[0]
    
    # 判断是否为方证类（label以"证"结尾）
    if not cn_label.endswith('证'):
        continue
    
    # 完整label的拼音 = OWL类名
    expected_pinyin = chinese_to_pinyin(cn_label)
    
    if expected_pinyin != base_class_name:
        errors.append({
            'class': class_name,
            'base': base_class_name,
            'label': cn_label,
            'expected': expected_pinyin,
        })
        print(f"  ✗ {class_name}")
        print(f"    label: {cn_label}")
        print(f"    期望: {expected_pinyin}")
        print(f"    实际: {base_class_name}")
    else:
        print(f"  ✓ {class_name}")

print()
print("=" * 80)
print(f"总计: 检查了 {checked} 个带label的类，{len(errors)} 个类名与拼音不匹配")
print("=" * 80)

if errors:
    print()
    print("不匹配列表（需修正）:")
    for e in errors:
        print(f"  rdf:about=\"#{e['class']}\" → 应改为 \"#{e['expected']}\"")
        print(f"    label: {e['label']}")

if skipped:
    print(f"\n无label的类 ({len(skipped)} 个): {skipped[:5]}...")
