# -*- coding: utf-8 -*-
import re, os, shutil

base = r'D:\work\Ontology\TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox'
ont_root = os.path.join(base, 'ontology')
bak_dir = os.path.join(base, 'OntologyMachine', '_dup_delete_backup')
os.makedirs(bak_dir, exist_ok=True)

def find_class_block(t, cls):
    m = re.search(r'<owl:Class rdf:about="#' + re.escape(cls) + r'">', t)
    if not m:
        return None
    start = m.start()
    i = start
    depth = 0
    tag_re = re.compile(r'<owl:Class\b[^>]*?(/?)>|</owl:Class>')
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
    else:
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

to_delete = [
    ('shaoyang_yangming.owl', 'Dahuanggansuitangzheng'),
    ('shaoyang_yangming.owl', 'Houpoqiwutangzheng'),
    ('taiyang.owl', 'Yuebijiazhutangzheng'),
    ('shaoyang_yangming.owl', 'Houpoushengjiangbanxiagancaorenshentangzheng'),
]

# 按文件分组，从后往前删（避免偏移）
from collections import defaultdict
by_file = defaultdict(list)
for fn, cls in to_delete:
    by_file[fn].append(cls)

for fn, clss in by_file.items():
    fp = os.path.join(ont_root, 'fangzheng', fn)
    shutil.copy2(fp, os.path.join(bak_dir, fn))
    t = open(fp, encoding='utf-8').read()
    blocks = []
    for cls in clss:
        r = find_class_block(t, cls)
        if not r:
            print(f'❌ {fn} #{cls} 未找到，跳过')
            continue
        blocks.append((r[2], r[1], cls))
    blocks.sort(reverse=True)  # 从后往前
    for cstart, end, cls in blocks:
        # 额外吞掉块后的空白行
        tail = end
        while tail < len(t) and t[tail] in ' \t\r\n':
            tail += 1
        t = t[:cstart] + t[tail:]
        print(f'  已删除 {fn} #{cls}  [{cstart},{tail})')
    open(fp, 'w', encoding='utf-8', newline='').write(t)

print('\n完成，备份于', bak_dir)
