# -*- coding: utf-8 -*-
import re, os
GAN = '甲乙丙丁戊己庚辛壬癸'
ZHI = '子丑寅卯辰巳午未申酉戌亥'
SRC = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..',
                   'OntologyFrameworkSiZhuBaZi', 'design', '四书全文整理', '三命通会.txt')

t = open(SRC, encoding='utf-8').read()
s = re.sub(r'三\s*命\s*通\s*会', '', t[175000:])
s = re.sub(r'·\s*[0-9 ]+·', '', s)

CORR = [
    ('辛巳日在辰时', '辛巳日壬辰时'),
    ('两子日庚寅时', '丙子日庚寅时'),
    ('丁卯庚子时', '丁卯日庚子时'),
    ('已卯日壬申时', '己卯日壬申时'),
    ('丙申日巳丑时', '丙申日己丑时'),
    ('己巳日庚子时', '己巳日甲子时'),
    ('己亥日乙丑时', '己亥日己巳时'),
    ('辛卯目甲午时', '辛卯日甲午时'),
    ('甲午月己巳时', '甲午日己巳时'),
    ('癸已日戊午时', '癸巳日戊午时'),
    ('己丑日癸亥时', '己丑日癸酉时'),
    ('戊申时丙辰时', '戊申日丙辰时'),
]
for a, b in CORR:
    s = s.replace(a, b)
s = re.sub(r'([甲乙丙丁戊己庚辛壬癸])末日', r'\1未日', s)

hpat = r'○六([甲乙丙丁戊己庚辛壬癸])日([甲乙丙丁戊己庚辛壬癸][子丑寅卯辰巳午未申酉戌亥])时断'
hpos = [m.start() for m in re.finditer(hpat, s)]
hpos.append(len(s))
heads = [(m.group(1), m.group(2)) for m in re.finditer(hpat, s)]
eprea = re.compile(r'([甲乙丙丁戊己庚辛壬癸])([子丑寅卯辰巳午未申酉戌亥])日([甲乙丙丁戊己庚辛壬癸])([子丑寅卯辰巳午未申酉戌亥])时')

result = {}
for i, (dstem, hpill) in enumerate(heads):
    block = s[hpos[i]:hpos[i + 1]]
    ms = list(eprea.finditer(block))
    for j, m in enumerate(ms):
        seg = block[m.start(): ms[j + 1].start() if j + 1 < len(ms) else len(block)]
        seg = re.sub(r'\s+', ' ', seg).strip()
        dp = dstem + m.group(2)
        hp = m.group(3) + m.group(4)
        if (dp, hp) not in result:
            result[(dp, hp)] = seg

hstem0 = {'甲': 0, '乙': 2, '丙': 4, '丁': 6, '戊': 8, '己': 0, '庚': 2, '辛': 4, '壬': 6, '癸': 8}
cyc = [GAN[k % 10] + ZHI[k % 12] for k in range(60)]
valid = set()
for d in cyc:
    ds = d[0]
    for hb_i, hb in enumerate(ZHI):
        hs = GAN[(hstem0[ds] + hb_i) % 10]
        valid.add((d, hs + hb))
print('extracted:', len(result))
miss = sorted(p for p in valid if p not in result)
print('missing:', len(miss), miss)
