# -*- coding: utf-8 -*-
import sys, io, re, os
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')
BASE = r"D:/work/Ontology/OntologyMachine/OntologyFrameworkSiZhuBaZi/design/六书全文整理"
def read(fn):
    with open(os.path.join(BASE, fn), encoding='utf-8') as f:
        return f.read().splitlines()

# 三命通会：卷标题 + ○标题 + 短篇名
print("########## 三命通会 ##########")
lines = read("三命通会.txt")
for i, ln in enumerate(lines):
    s = ln.strip()
    if not s or s == '未知':
        continue
    if re.match(r'^卷[一二三四五六七八九十]', s) or s.startswith('○'):
        print("%d\t%s" % (i+1, s[:60]))
    elif len(s) <= 14 and re.match(r'^(论|释|定|总论|天干|地支|人元|四时|旺相|遁|年|胎|坐|大运|小运|太岁|十干|支元)', s) and not s.endswith('。'):
        print("%d\t%s" % (i+1, s[:60]))

print()
print("########## 神峰通考 ##########")
lines = read("神峰通考.txt")
for i, ln in enumerate(lines):
    s = ln.strip()
    if not s or s == '未知':
        continue
    if len(s) <= 20 and re.search(r'(说|类|论|诀|赋|歌|篇|式|法|格)$', s) and not s.endswith('。'):
        print("%d\t%s" % (i+1, s[:60]))
