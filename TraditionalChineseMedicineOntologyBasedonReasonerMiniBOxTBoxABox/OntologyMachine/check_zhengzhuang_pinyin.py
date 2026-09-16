import re
from pypinyin import pinyin, Style

path = r'D:\work\Ontology\TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox\ontology\tcm-zhengzhuang.owl'
with open(path, 'r', encoding='utf-8') as f:
    content = f.read()

pattern = re.compile(r'<owl:Class rdf:about="#([^"]+)">(.*?)</owl:Class>', re.S)
classes = []
for m in pattern.finditer(content):
    cls = m.group(1)
    body = m.group(2)
    lm = re.search(r'<rdfs:label xml:lang="zh">([^<]+)</rdfs:label>', body)
    label = lm.group(1) if lm else None
    classes.append((cls, label))

print(f'总类数: {len(classes)}')

def expected_pinyin(label):
    # 全拼拼接，仅首字母大写，其余全小写
    parts = []
    for ch in label:
        if re.match(r'[\u4e00-\u9fff]', ch):
            py = pinyin(ch, style=Style.NORMAL, errors='ignore')
            if py and py[0]:
                parts.append(py[0][0])
    s = ''.join(parts)
    if not s:
        return ''
    return s[0].upper() + s[1:].lower()

mismatches = []
for cls, label in classes:
    if not label:
        mismatches.append((cls, None, 'NO_LABEL'))
        continue
    exp = expected_pinyin(label)
    if cls != exp:
        mismatches.append((cls, label, exp))

print(f'不匹配数: {len(mismatches)}')
print()
for cls, label, exp in mismatches:
    print(f'{cls}\t{label}\t期望:{exp}')
