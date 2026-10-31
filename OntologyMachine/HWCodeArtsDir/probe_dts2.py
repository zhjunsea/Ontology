# -*- coding: utf-8 -*-
import re, os
GAN = '甲乙丙丁戊己庚辛壬癸'
ZHI = '子丑寅卯辰巳午未申酉戌亥'
SRC = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..',
                   'OntologyFrameworkSiZhuBaZi', 'design', '四书全文整理', '三命通会.txt')

t = open(SRC, encoding='utf-8').read()
s = re.sub(r'三\s*命\s*通\s*会', '', t[175000:])
s = re.sub(r'·\s*[0-9 ]+·', '', s)
# OCR: 末->未 in "X末日Y时" context
s = re.sub(r'([甲乙丙丁戊己庚辛壬癸])末日', r'\1未日', s)

hpat = r'○六([甲乙丙丁戊己庚辛壬癸])日([甲乙丙丁戊己庚辛壬癸][子丑寅卯辰巳午未申酉戌亥])时断'
hpos = [m.start() for m in re.finditer(hpat, s)]
hpos.append(len(s))
heads = [(m.group(1), m.group(2)) for m in re.finditer(hpat, s)]

eprea = re.compile(r'([甲乙丙丁戊己庚辛壬癸])([子丑寅卯辰巳午未申酉戌亥])日([甲乙丙丁戊己庚辛壬癸])([子丑寅卯辰巳午未申酉戌亥])时')

result = {}   # (day_pillar, hour_pillar) -> text
for i, (dstem, hpill) in enumerate(heads):
    block = s[hpos[i]:hpos[i + 1]]
    ms = list(eprea.finditer(block))
    for j, m in enumerate(ms):
        seg = block[m.start(): ms[j + 1].start() if j + 1 < len(ms) else len(block)]
        seg = re.sub(r'\s+', ' ', seg).strip()
        dp = dstem + m.group(2)          # enforce heading day-stem (OCR fix)
        hp = m.group(3) + m.group(4)
        result[(dp, hp)] = seg

print('extracted entries:', len(result))
# expected 720
cyc = [GAN[i % 10] + ZHI[i % 12] for i in range(60)]
allpairs = [(d, GAN[k % 10] + ZHI[k % 12]) for d in cyc for k in range(60)
            if True]
# hour pillars must match day stem? no. Just enumerate 60 day x 12 hour-branch -> need hour pillar = any of 5? Actually hour pillar stem determined by day stem.
# Build valid hour pillars per day pillar via 五鼠遁
start = {'甲': '甲', '己': '甲', '乙': '丙', '庚': '丙', '丙': '戊', '辛': '戊',
         '丁': '庚', '壬': '庚', '戊': '壬', '癸': '壬'}
hstem0 = {'甲': 0, '乙': 2, '丙': 4, '丁': 6, '戊': 8, '己': 0, '庚': 2, '辛': 4, '壬': 6, '癸': 8}
valid = set()
for d in cyc:
    ds = d[0]
    for hb_i, hb in enumerate(ZHI):
        hs = GAN[(hstem0[ds] + hb_i) % 10]
        valid.add((d, hs + hb))
print('valid expected pairs:', len(valid))
missing = [p for p in valid if p not in result]
print('missing:', len(missing), missing[:30])
