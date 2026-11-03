# -*- coding: utf-8 -*-
import sys, io, re, glob, os
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')

BASE = r"D:/work/Ontology/OntologyMachine/OntologyFrameworkSiZhuBaZi/design/六书全文整理"

def read(fp):
    with open(fp, encoding='utf-8') as f:
        return f.read().splitlines()

NUM = re.compile(r'^[一二三四五六七八九十百]+[、\.]')

def dump(name, fn, keep):
    lines = read(os.path.join(BASE, fn))
    out = []
    for i, ln in enumerate(lines):
        s = ln.strip()
        if not s or s == '未知':
            continue
        if keep(s):
            out.append((i+1, s))
    print("### %s : %d headings ###" % (name, len(out)))
    for ln, s in out:
        print("%d\t%s" % (ln, s))
    print()

# 滴天髓：编号篇 + 顶层论
dump("滴天髓", "滴天髓.txt", lambda s: NUM.match(s) or s in ("通神论", "六亲论"))
# 子平真诠：编号篇
dump("子平真诠", "子平真诠.txt", lambda s: NUM.match(s))
# 穷通宝鉴：小节标题形如 N、XXX木/火/土/金/水；卷目
dump("穷通宝鉴", "穷通宝鉴.txt", lambda s: bool(re.match(r'^\d+、.*[木火土金水]', s)) or s.startswith("造化元钥之"))
