# -*- coding: utf-8 -*-
import re, os
GAN = '甲乙丙丁戊己庚辛壬癸'
SRC = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..',
                   'OntologyFrameworkSiZhuBaZi', 'design', '四书全文整理', '穷通宝鉴.txt')
t = open(SRC, encoding='utf-8').read()
MONTHS = ['正月', '二月', '三月', '四月', '五月', '六月', '七月', '八月',
          '九月', '十月', '十一月', '十二月']
# find last occurrence of each single-month header per stem
pat = re.compile(r'([正二三四五六七八九十]{1,2}月(?:甲|乙|丙|丁|戊|己|庚|辛|壬|癸)[木火土金水])')
for stem in GAN:
    row = []
    for mi, mn in enumerate(MONTHS, 1):
        h = mn + stem
        pos = -1
        for m in re.finditer(re.escape(h), t):
            pos = m.start()
        # ensure it's a header (followed by 木火土金水)
        hit = None
        for m in re.finditer(re.escape(h) + r'[木火土金水]', t):
            hit = m.start()
        row.append((mn, hit))
    print(stem, [(mn, p) for mn, p in row if p is not None])

# also list combined headers present
comb = sorted(set(re.findall(r'[正二三四五六七八九十]{1,2}月(?:甲|乙|丙|丁|戊|己|庚|辛|壬|癸)[木火土金水]', t)))
print('all header variants:', comb)
