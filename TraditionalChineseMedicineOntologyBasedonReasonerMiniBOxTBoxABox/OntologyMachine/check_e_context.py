# -*- coding: utf-8 -*-
import re

# 检查 E 的上下文
java_fp = r'D:\work\Ontology\TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox\OntologyMachine\OntologyFramework\src\test\java\com\ocean\ontologyframework\OntologyFrameworkTCMTests.java'
print("===== E 在 java 中的上下文 =====")
with open(java_fp, 'r', encoding='utf-8') as f:
    for i, l in enumerate(f.readlines(), 1):
        if re.search(r'\bE\b', l):
            print(f'{i}: {l.rstrip()}')

zz_fp = r'D:\work\Ontology\TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox\ontology\tcm-zhengzhuang.owl'
print("\n===== E 在症状本体中的上下文 =====")
with open(zz_fp, 'r', encoding='utf-8') as f:
    for i, l in enumerate(f.readlines(), 1):
        if re.search(r'#E["<]', l):
            print(f'{i}: {l.rstrip()}')

print("\n===== 腹中急痛 / 腹中㽲痛 定义 =====")
with open(zz_fp, 'r', encoding='utf-8') as f:
    for i, l in enumerate(f.readlines(), 1):
        if 'Fuzhongjijitong' in l or 'Fuzhongjitong' in l:
            print(f'{i}: {l.rstrip()}')

# 检查 E 在 duli.owl 上下文
duli_fp = r'D:\work\Ontology\TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox\ontology\fangzheng\duli.owl'
print("\n===== E 在 duli.owl 中的上下文 =====")
with open(duli_fp, 'r', encoding='utf-8') as f:
    for i, l in enumerate(f.readlines(), 1):
        if re.search(r'#E["<]', l):
            print(f'{i}: {l.rstrip()}')
