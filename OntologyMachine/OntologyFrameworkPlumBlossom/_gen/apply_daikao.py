# -*- coding: utf-8 -*-
"""插入第二十五轮卷之二/三待考落地代码块 + OPPOSITE_NATURE 补「随」。幂等。"""
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
JW = os.path.join(ROOT, 'src/main/java/com/ocean/ontologyframework/plumblossom/PlumBlossomOntologyJobWorker.java')
BLK = open(os.path.join(ROOT, '_gen/daikao_block.java'), encoding='utf-8').read()

s = open(JW, encoding='utf-8').read()

# (1) 插入待考代码块（置于 shiDaYingNote 之后、DONG_XUAN_GE javadoc 之前）
anchor = '    /**\n     * 卷之二·观物洞玄歌（B55'
if '〔①〕卷之三·占卜十应诀·日应' in s:
    print('block: already applied')
else:
    i = s.index(anchor)
    s = s[:i] + BLK + s[i:]
    print('block: inserted')

# (2) OPPOSITE_NATURE 补 随
anchor2 = '        OPPOSITE_NATURE.put("巽为风", "内藏");'
if 'OPPOSITE_NATURE.put("泽雷随"' in s:
    print('opposite: already applied')
else:
    add = anchor2 + '\n        OPPOSITE_NATURE.put("泽雷随", "前·偷安（原文『随前坎后偷安矣』，句式漫漶，照录备考）");'
    s = s.replace(anchor2, add)
    print('opposite: inserted')

open(JW, 'w', encoding='utf-8').write(s)
