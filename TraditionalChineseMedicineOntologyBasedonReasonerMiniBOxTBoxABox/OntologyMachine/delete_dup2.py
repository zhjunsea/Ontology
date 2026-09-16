# -*- coding: utf-8 -*-
import re, os, shutil

base = r'D:\work\Ontology\TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox'
ont_root = os.path.join(base, 'ontology')
bak_dir = os.path.join(base, 'OntologyMachine', '_dup_delete_backup')
fp = os.path.join(ont_root, 'fangzheng', 'shaoyang_yangming.owl')
cls = 'Houposhengjiangbanxiagancaorenshentangzheng'


def find_class_block(t, cls):
    m = re.search(r'<owl:Class rdf:about="#' + re.escape(cls) + r'">', t)
    if not m:
        return None
    start = m.start()
    i = start
    depth = 0
    tag_re = re.compile(r'<owl:Class\b[^>]*?(/?)>|</owl:Class>')
    end = None
    while i < len(t):
        tm = tag_re.search(t, i)
        if not tm:
            return None
        if tm.group(0) == '</owl:Class>':
            depth -= 1
            i = tm.end()
            if depth == 0:
                end = i
                break
        else:
            if tm.group(1) == '/':
                i = tm.end()
                continue
            depth += 1
            i = tm.end()
    if end is None:
        return None
    cstart = start
    pre = t[:start]
    j = len(pre)
    while j > 0 and pre[j-1] in ' \t\r\n':
        j -= 1
    if pre[:j].endswith('-->'):
        k = pre.rfind('<!--', 0, j)
        if k != -1:
            cstart = k
    return (start, end, cstart)


shutil.copy2(fp, os.path.join(bak_dir, 'shaoyang_yangming_before_dup2.owl'))
t = open(fp, encoding='utf-8').read()
r = find_class_block(t, cls)
if not r:
    print('未找到', cls)
else:
    start, end, cstart = r
    tail = end
    while tail < len(t) and t[tail] in ' \t\r\n':
        tail += 1
    print('删除范围:', cstart, tail)
    print('预览:', t[cstart:cstart+100].replace('\n', ' '))
    t = t[:cstart] + t[tail:]
    open(fp, 'w', encoding='utf-8', newline='').write(t)
    print('已删除')
