# -*- coding: utf-8 -*-
import os, re

OLD = r'D:\work\Ontology\GoldenWind\HWCodeArtsDir\old-out\TowerGeoInput_10563080_HH130m_6段_中径4.95m_GWH182-7.5_外置_锚栓式__主体434t-20251103-2025-11-06-11-24-50\项目法兰尺寸_骨架关系式'
NEW = r'D:\work\Ontology\GoldenWind\HWCodeArtsDir\tmsd-new'

def read(p):
    return open(p, encoding='gbk').read().splitlines()

def parse(lines):
    """提取 变量=值 及行后 /*注释"""
    out = {}
    for ln in lines:
        s = ln.strip()
        if not s or s.startswith('/'):
            continue
        m = re.match(r'^([A-Za-z_\$][\w\$]*)\s*=\s*(.*?)\s*(?:/\*(.*))?$', s)
        if not m:
            continue
        name, val, cmt = m.group(1), m.group(2), (m.group(3) or '')
        out[name] = (val.strip(), cmt.strip())
    return out

def numfmt(v):
    try:
        return round(float(v), 4)
    except Exception:
        return v

def cmp_file(old_rel, new_name, title):
    op = os.path.join(OLD, old_rel)
    np = os.path.join(NEW, new_name)
    if not os.path.exists(op) or not os.path.exists(np):
        print('=' * 90); print(title); print('  [缺文件] 老=%s 新=%s' % (os.path.exists(op), os.path.exists(np))); print()
        return
    o = parse(read(op))
    n = parse(read(np))
    print('=' * 90)
    print(title)
    print('=' * 90)
    only_o = [k for k in o if k not in n]
    only_n = [k for k in n if k not in o]
    both = [k for k in o if k in n]
    dif_val, dif_cmt = [], []
    for k in both:
        ov, oc = o[k]; nv, nc = n[k]
        if numfmt(ov) != numfmt(nv):
            dif_val.append((k, ov, nv))
    print('--- 仅老程序有 (%d) ---' % len(only_o))
    for k in only_o:
        print('  %s = %s /*%s' % (k, o[k][0], o[k][1]))
    print('--- 仅TMSD有 (%d) ---' % len(only_n))
    for k in only_n:
        print('  %s = %s /*%s' % (k, n[k][0], n[k][1]))
    print('--- 值不同 (%d) ---' % len(dif_val))
    for k, ov, nv in dif_val:
        print('  %-16s 老=%-12s TMSD=%-12s' % (k, ov, nv))
    print()

import sys
sys.stdout = open(r'D:\work\Ontology\GoldenWind\HWCodeArtsDir\cmp_report2.txt', 'w', encoding='utf-8')
for s in [2, 3, 4, 5]:
    cmp_file(r'第%d段\第%d段筒体信息关系式.txt' % (s, s), '用例0-第%d段-筒体信息关系式.txt' % s,
             '第%d段 筒体信息  老 vs TMSD' % s)
for s in [2, 3, 4, 5]:
    cmp_file(r'第%d段\第%d段附件信息关系式.txt' % (s, s), '用例0-第%d段-附件信息关系式.txt' % s,
             '第%d段 附件信息  老 vs TMSD' % s)
for i in [2, 3, 4, 5]:
    cmp_file(r'连接法兰\连接法兰%d关系式.txt' % i, '用例0-第%d段-连接法兰关系式.txt' % i,
             '连接法兰%d (=TMSD 第%d段上法兰)  老 vs TMSD' % (i, i))
sys.stdout.close()
