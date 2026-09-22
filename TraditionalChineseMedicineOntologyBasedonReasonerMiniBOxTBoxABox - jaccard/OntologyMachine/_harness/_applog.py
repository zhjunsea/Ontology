# -*- coding: utf-8 -*-
"""解析 app_run_v4.log，抽取每个病例的：输入症状/脉象 → 八纲 → 六经 → 方证。"""
import re, sys, io, json

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')

LOG = r"D:/work/Ontology/TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox/OntologyMachine/app_run_v4.log"

re_map = re.compile(r'症状映射\[第0轮\]: 已识别 (\d+) 项.*?症状=\[(.*?)\] 脉象=\[(.*?)\] 舌象=\[(.*?)\]')
re_bg = re.compile(r'八纲来源: 患者推理=\[(.*?)\]')
re_bgdone = re.compile(r'八纲完成: \{(.*?)\}')
re_lj = re.compile(r'六经来源: 患者推理=\[(.*?)\]（物化八纲=\[(.*?)\]）')
re_fz = re.compile(r'方证完成: (\S+) 匹配数=(\d+)')
re_score = re.compile(r'方证打分: \[(.*?)\]$')
re_top = re.compile(r'\[阶段2\] Top\d+ = (\d+)')
re_diag = re.compile(r'诊断解释: (.*)')

cases = []
cur = None
with open(LOG, 'r', encoding='utf-8', errors='replace') as f:
    for line in f:
        m = re_map.search(line)
        if m:
            if cur: cases.append(cur)
            cur = {'n': int(m.group(1)),
                   'sym': [x.strip() for x in m.group(2).split(',') if x.strip()],
                   'pulse': [x.strip() for x in m.group(3).split(',') if x.strip()],
                   'tongue': [x.strip() for x in m.group(4).split(',') if x.strip()],
                   'bg': None, 'bgdone': None, 'lj': None, 'fz': None, 'diag': None,
                   'score': None, 'bgtypes': None}
            continue
        if cur is None: continue
        m = re_bg.search(line)
        if m: cur['bg'] = [x.strip() for x in m.group(1).split(',') if x.strip()]; continue
        m = re_bgdone.search(line)
        if m: cur['bgdone'] = m.group(1); continue
        m = re_score.search(line)
        if m: cur['score'] = m.group(1); continue
        m = re_lj.search(line)
        if m: cur['lj'] = [x.strip() for x in m.group(1).split(',') if x.strip()]; continue
        m = re_fz.search(line)
        if m: cur['fz'] = m.group(1); continue
        m = re_diag.search(line)
        if m: cur['diag'] = m.group(1); continue
if cur: cases.append(cur)

print("总病例数 =", len(cases))
print()

# 统计
both_sy_jy = []
both_yin_yang = []
for i, c in enumerate(cases):
    lj = c['lj'] or []
    bg = c['bg'] or []
    if 'Shaoyangbing' in lj and 'Jueyinbing' in lj:
        both_sy_jy.append(i)
    if 'Yin' in bg and 'Yang' in bg:
        both_yin_yang.append(i)

print("=== 六经同时含 少阳+厥阴 的病例 (%d) ===" % len(both_sy_jy))
for i in both_sy_jy:
    c = cases[i]
    print("  #%d 症状=%s 脉=%s | 八纲=%s | 六经=%s | 方证=%s" % (
        i, c['sym'], c['pulse'], c['bg'], c['lj'], c['fz']))
print()
print("=== 八纲同时含 阴+阳 的病例 (%d) ===" % len(both_yin_yang))
for i in both_yin_yang:
    c = cases[i]
    print("  #%d 症状=%s 脉=%s | 八纲=%s | 六经=%s | 方证=%s" % (
        i, c['sym'], c['pulse'], c['bg'], c['lj'], c['fz']))
print()

if len(sys.argv) > 1 and sys.argv[1] == 'dump':
    for i, c in enumerate(cases):
        print("#%d 症状=%s 脉=%s 舌=%s | 八纲=%s | 六经=%s | 方证=%s | %s" % (
            i, c['sym'], c['pulse'], c['tongue'], c['bg'], c['lj'], c['fz'], c['diag']))

if len(sys.argv) > 2 and sys.argv[1] == 'score':
    key = sys.argv[2]
    for i, c in enumerate(cases):
        sig = ",".join(x.replace('_instance', '') for x in c['sym']) + "|" + \
              ",".join(x.replace('_instance', '') for x in c['pulse'])
        if key in sig:
            print("#%d 症状=%s 脉=%s" % (i, c['sym'], c['pulse']))
            print("   八纲=%s 六经=%s 方证=%s" % (c['bg'], c['lj'], c['fz']))
            print("   打分=%s" % c['score'])
            print("   诊断=%s" % c['diag'])
            print()
