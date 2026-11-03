# -*- coding: utf-8 -*-
import re, os
SRC = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..',
                   'OntologyFrameworkSiZhuBaZi', 'design', '六书全文整理', '三命通会.txt')
t = open(SRC, encoding='utf-8').read()
s = re.sub(r'三\s*命\s*通\s*会', '', t[175000:])
s = re.sub(r'·\s*[0-9 ]+·', '', s)
hpat = r'○六([甲乙丙丁戊己庚辛壬癸])日([甲乙丙丁戊己庚辛壬癸][子丑寅卯辰巳午未申酉戌亥])时断'
hpos = [m.start() for m in re.finditer(hpat, s)]
hpos.append(len(s))
heads = [(m.group(1), m.group(2)) for m in re.finditer(hpat, s)]
for d, hp in [('戊', '甲寅'), ('辛', '己丑'), ('辛', '戊子'), ('乙', '丁亥'), ('己', '庚午')]:
    for i, (a, b) in enumerate(heads):
        if a == d and b == hp:
            blk = re.sub(r'\s+', ' ', s[hpos[i]:hpos[i + 1]])
            print('======', d, hp)
            print(blk[:560])
            print()
