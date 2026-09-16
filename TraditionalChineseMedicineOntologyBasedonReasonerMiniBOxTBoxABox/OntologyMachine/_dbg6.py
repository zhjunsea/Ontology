# -*- coding: utf-8 -*-
import re, os
ont_root = r'D:\work\Ontology\TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox\ontology'

def show(fp, label):
    t = open(fp, encoding='utf-8').read()
    print('#' * 90)
    print(label)
    print('#' * 90)
    for m in re.finditer(r'<owl:Class rdf:about="#([^"]*[Hh]oupo[^"]*)">', t):
        cls = m.group(1)
        start = m.start()
        # 找块结束
        i = start; depth = 0
        tag_re = re.compile(r'<owl:Class\b[^>]*?(/?)>|</owl:Class>')
        while i < len(t):
            tm = tag_re.search(t, i)
            if not tm: break
            if tm.group(0) == '</owl:Class>':
                depth -= 1; i = tm.end()
                if depth == 0: break
            else:
                if tm.group(1) == '/': i = tm.end(); continue
                depth += 1; i = tm.end()
        print(f'\n--- #{cls}  [{start},{i}) ---')
        print(t[start:i])

show(os.path.join(ont_root, 'fangzheng', 'shaoyang_yangming.owl'), '当前 shaoyang_yangming.owl')
show(os.path.join(ont_root, '..', 'OntologyMachine', '_fangji_pinyin_backup', 'shaoyang_yangming.owl'), 'A类修正前备份 _fangji_pinyin_backup')
