# -*- coding: utf-8 -*-
import re, os
ont_root = r'D:\work\Ontology\TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox\ontology'
fp = os.path.join(ont_root, 'fangzheng', 'shaoyang_yangming.owl')
t = open(fp, encoding='utf-8').read()

cls = 'Houpoushengjiangbanxiagancaorenshentangzheng'
m = re.search(r'<owl:Class rdf:about="#' + re.escape(cls) + r'">', t)
print('定义标签匹配:', m)
print('字面存在:', ('<owl:Class rdf:about="#' + cls + '">') in t)

# 手动跑块提取
start = m.start()
i = start
depth = 0
tag_re = re.compile(r'<owl:Class\b[^>]*?(/?)>|</owl:Class>')
steps = 0
while i < len(t) and steps < 50:
    tm = tag_re.search(t, i)
    if not tm:
        print('无更多标签，失败')
        break
    steps += 1
    print(f'  step{steps}: depth={depth} tag={tm.group(0)[:60]!r}')
    if tm.group(0) == '</owl:Class>':
        depth -= 1
        i = tm.end()
        if depth == 0:
            print('  块结束于', i)
            break
    else:
        if tm.group(1) == '/':
            i = tm.end()
            continue
        depth += 1
        i = tm.end()
