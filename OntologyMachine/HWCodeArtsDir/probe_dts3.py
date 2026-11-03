# -*- coding: utf-8 -*-
import re, os
GAN = '甲乙丙丁戊己庚辛壬癸'
ZHI = '子丑寅卯辰巳午未申酉戌亥'
SRC = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..',
                   'OntologyFrameworkSiZhuBaZi', 'design', '六书全文整理', '三命通会.txt')
t = open(SRC, encoding='utf-8').read()
s = re.sub(r'三\s*命\s*通\s*会', '', t[175000:])
s = re.sub(r'·\s*[0-9 ]+·', '', s)
s = re.sub(r'([甲乙丙丁戊己庚辛壬癸])末日', r'\1未日', s)
hpat = r'○六([甲乙丙丁戊己庚辛壬癸])日([甲乙丙丁戊己庚辛壬癸][子丑寅卯辰巳午未申酉戌亥])时断'
hpos = [m.start() for m in re.finditer(hpat, s)]
hpos.append(len(s))
heads = [(m.group(1), m.group(2)) for m in re.finditer(hpat, s)]

targets = [('乙', '丁亥'), ('己', '己巳'), ('辛', '壬辰'), ('戊', '丙辰'), ('丙', '庚寅'),
           ('丁', '庚子'), ('己', '壬申'), ('戊', '己未'), ('丙', '己丑'), ('己', '甲子'),
           ('己', '庚午'), ('辛', '甲午'), ('甲', '己巳'), ('癸', '戊午'), ('己', '癸酉')]
for d, hp in targets:
    for i, (a, b) in enumerate(heads):
        if a == d and b == hp:
            blk = s[hpos[i]:hpos[i + 1]]
            blk = re.sub(r'\s+', ' ', blk)
            print('======', d, hp)
            print(blk[:520])
            print()
