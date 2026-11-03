#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""第四十三轮：抽取卷之二「心易占卜玄机／占卜总诀／占卜论理诀／先天后天论」正文
   与「三要灵应篇·拆字之应」段（照录），并生成 Java 常量片段。"""
import io, re
raw = io.open('design/_epub_dump.txt', encoding='utf-8').read()
raw = raw.replace('\r','')

def clean(s):
    # 去页标 [n]、去全部空白
    s = re.sub(r'\[\d+\]', '', s)
    s = re.sub(r'\s+', '', s)
    return s

def between(a, b):
    i = raw.find(a); j = raw.find(b, i+len(a))
    assert i>=0 and j>i, (a,b,i,j)
    return clean(raw[i+len(a):j])

chunks = {
 'XIN_YI':  between('心易占卜玄机','占卜总诀'),
 'ZONG_JUE':between('占卜总诀','占卜论理诀'),
 'LUN_LI':  between('占卜论理诀','先天后天论'),
 'XIAN_HOU':between('先天后天论','卦断遗论'),
 'CHAI_ZI': between('右乃杂见观物之应','复指物名'),
}
for k,v in chunks.items():
    print(f"=== {k}  len={len(v)} ===")
    print(v)
    print()
