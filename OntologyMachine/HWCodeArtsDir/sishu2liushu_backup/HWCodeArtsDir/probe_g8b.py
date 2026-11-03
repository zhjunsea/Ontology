# -*- coding: utf-8 -*-
import re, os
SRC = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..',
                   'OntologyFrameworkSiZhuBaZi', 'design', '四书全文整理', '穷通宝鉴.txt')
t = open(SRC, encoding='utf-8').read()
H = re.compile(r'(三春|三夏|三秋|三冬|[正二三四五六七八九十]{1,3}月)([甲乙丙丁戊己庚辛壬癸])([木火土金水])')
ms = list(H.finditer(t))
seq = []
for i, m in enumerate(ms):
    end = ms[i + 1].start() if i + 1 < len(ms) else len(t)
    seg = re.sub(r'\s+', ' ', t[m.end():end]).strip()
    if len(seg) > 40:
        seq.append((m.start(), m.group(1) + m.group(2), len(seg)))
for s in seq[:80]:
    print(s)
print('total good:', len(seq))
