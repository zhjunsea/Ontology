# -*- coding: utf-8 -*-
"""统计 possibleSymptom(或然症) 的覆盖与频次分布，判断能否用固定权重。"""
import glob, re, os
from collections import Counter, defaultdict
import xml.etree.ElementTree as ET

NS = {
    'rdf': 'http://www.w3.org/1999/02/22-rdf-syntax-ns#',
    'owl': 'http://www.w3.org/2002/07/owl#',
    'rdfs': 'http://www.w3.org/2000/01/rdf-schema#',
}
RDF = NS['rdf']

base = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..', 'ontology', 'fangzheng')

cls_total = set()
cls_with_def = set()
cls_with_ps = set()
ps_counter = Counter()          # 症状 -> 作为或然症出现次数
ps_per_cls = {}                 # 方证 -> [症状...]

# 主证(equivalentClass 里 you_zhengzhuang 限制) 的症状频次，用于对比
main_counter = Counter()

def local(tag):
    return tag.split('}')[-1]

for f in sorted(glob.glob(os.path.join(base, '*.owl'))):
    if '.bak' in f:
        continue
    try:
        tree = ET.parse(f)
    except Exception as e:
        print('PARSE FAIL', os.path.basename(f), e)
        continue
    root = tree.getroot()
    for cls in root.findall('owl:Class', NS):
        about = cls.get('{%s}about' % RDF) or cls.get('{%s}ID' % RDF)
        if not about:
            continue
        name = about.split('#')[-1]
        cls_total.add(name)
        # equivalentClass
        eq = cls.find('owl:equivalentClass', NS)
        if eq is not None:
            cls_with_def.add(name)
            # 收集 you_zhengzhuang 的 someValuesFrom
            for r in eq.iter():
                if local(r.tag) == 'Restriction':
                    onp = r.find('owl:onProperty', NS)
                    svf = r.find('owl:someValuesFrom', NS)
                    if onp is not None and svf is not None:
                        p = onp.get('{%s}resource' % RDF, '')
                        if p.endswith('#you_zhengzhuang'):
                            v = svf.get('{%s}resource' % RDF)
                            if v:
                                main_counter[v.split('#')[-1]] += 1
        # possibleSymptom（默认命名空间，需按 local-name 匹配）
        pss = []
        for ps in list(cls):
            if local(ps.tag) == 'possibleSymptom':
                v = ps.get('{%s}resource' % RDF)
                if v:
                    pss.append(v.split('#')[-1])
        if pss:
            cls_with_ps.add(name)
            ps_per_cls[name] = pss
            for s in pss:
                ps_counter[s] += 1

print('方证具名类总数        =', len(cls_total))
print('含 equivalentClass    =', len(cls_with_def))
print('含 possibleSymptom    =', len(cls_with_ps), '(%.1f%%)' % (100.0*len(cls_with_ps)/max(1,len(cls_total))))
print('possibleSymptom 条数  =', sum(ps_counter.values()))
print('不同或然症症状种类    =', len(ps_counter))
print('平均每方证或然症数    = %.2f' % (sum(len(v) for v in ps_per_cls.values())/max(1,len(ps_per_cls))))
print()
print('=== 或然症频次 Top20（出现次数越多 = 信息量越低）===')
for s, c in ps_counter.most_common(20):
    print('  %-24s %3d 个方证' % (s, c))
print()
print('=== 只出现 1 次的或然症（高信息量）数量 ===')
once = [s for s, c in ps_counter.items() if c == 1]
print('  数量 =', len(once), ' 样例:', ', '.join(once[:12]))
print()
print('=== 对比：主证症状频次 Top15 ===')
for s, c in main_counter.most_common(15):
    print('  %-24s %3d 个方证' % (s, c))
print()
print('=== 本例相关症状的或然症频次 ===')
for s in ['Kouke', 'Kesou', 'Xinfan', 'Xiou', 'Wanglaihanre', 'Xiongxiekuman', 'Xianmai']:
    print('  %-16s 或然症出现 %d 次 / 主证出现 %d 次' % (s, ps_counter.get(s, 0), main_counter.get(s, 0)))
