# -*- coding: utf-8 -*-
import re, os

ont_root = r'D:\work\Ontology\TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox\ontology'

def find_class_block(t, cls):
    """稳健提取 <owl:Class rdf:about="#cls"> ... </owl:Class> 块（正确处理自闭合标签）。
    返回 (block_start, block_end_exclusive, include_comment_start)"""
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
            if tm.group(1) == '/':   # 自闭合，不增加深度
                i = tm.end()
                continue
            depth += 1
            i = tm.end()
    else:
        return None
    # 向前吸收紧邻注释（仅当注释结束与 start 之间只有空白）
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

if __name__ == '__main__':
    to_delete = [
        ('shaoyang_yangming.owl', 'Dahuanggansuitangzheng'),
        ('shaoyang_yangming.owl', 'Houpoqiwutangzheng'),
        ('taiyang.owl', 'Yuebijiazhutangzheng'),
        ('shaoyang_yangming.owl', 'Houpoushengjiangbanxiagancaorenshentangzheng'),
    ]
    for fn, cls in to_delete:
        fp = os.path.join(ont_root, 'fangzheng', fn)
        t = open(fp, encoding='utf-8').read()
        r = find_class_block(t, cls)
        if not r:
            print(f'❌ {fn} #{cls}: 未找到')
            continue
        start, end, cstart = r
        print('=' * 90)
        print(f'{fn}  #{cls}  类块[{start},{end}) 含注释[{cstart},{end})')
        print('--- 头部 ---')
        print(t[cstart:min(cstart+200, end)])
        print('--- 尾部 ---')
        print(t[max(cstart, end-160):end])
