# -*- coding: utf-8 -*-
import re, os
SRC = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..',
                   'OntologyFrameworkSiZhuBaZi', 'design', '四书全文整理', '三命通会.txt')
t = open(SRC, encoding='utf-8').read()
s = re.sub(r'三\s*命\s*通\s*会', '', t[175000:])
s = re.sub(r'·\s*[0-9 ]+·', '', s)
pat = r'○六([甲乙丙丁戊己庚辛壬癸])日([甲乙丙丁戊己庚辛壬癸][子丑寅卯辰巳午未申酉戌亥])时断'
pos = [m.start() for m in re.finditer(pat, s)]
pos.append(len(s))
heads = [(m.group(1), m.group(2)) for m in re.finditer(pat, s)]
print('heads', len(heads))


def block(d, hp):
    for i, (a, b) in enumerate(heads):
        if a == d and b == hp:
            return s[pos[i]:pos[i + 1]]
    return ''


for d, hp in [('丁', '甲子'), ('辛', '甲子'), ('辛', '乙丑'), ('戊', '己未'), ('己', '丁卯')]:
    b = block(d, hp)
    print('======', d, hp)
    print(b[:700].replace('\n', ' '))
    print()
