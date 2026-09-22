# -*- coding: utf-8 -*-
"""落地校验：XML 合法性 / 判据计数 / 白名单 / ABox 个体 / tcm-all.owl 同步。"""
import os, re, io, sys
import xml.etree.ElementTree as ET

ONT = r'D:\work\Ontology\TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox\ontology'
buf = io.StringIO(); old = sys.stdout; sys.stdout = buf
try:
    files = ['tcm-core.owl', 'tcm-zhengzhuang.owl', 'tcm-maixiang.owl']
    print('=== 1. XML well-formed ===')
    for f in files:
        p = os.path.join(ONT, f)
        try:
            ET.parse(p)
            print(f'  OK   {f}')
        except Exception as e:
            print(f'  FAIL {f}: {e}')

    print('\n=== 2. 判据计数（tcm-core.owl）===')
    s = open(os.path.join(ONT, 'tcm-core.owl'), encoding='utf-8').read()
    panju = sorted(set(re.findall(r'<owl:Class rdf:about="#(Panju_[A-E]\d+)"', s)))
    print(f'  判据数 = {len(panju)}')
    print(f'  {panju}')

    print('\n=== 3. 新增判据的目标八纲 ===')
    for pid in ['Panju_A8', 'Panju_C6', 'Panju_D8']:
        m = re.search(r'<owl:Class rdf:about="#' + pid + r'">(.*?)</owl:Class>\s*(?=<owl:Class rdf:about=)',
                      s, re.S)
        body = m.group(1) if m else ''
        tgts = re.findall(r'<rdfs:subClassOf rdf:resource="#(Biao|Li|Banbiaobanli|Yang|Yin)"', body)
        conds = re.findall(r'<owl:onProperty rdf:resource="#(you_[a-z]+)"/>\s*'
                           r'<owl:someValuesFrom rdf:resource="#([A-Za-z0-9_]+)"', body)
        print(f'  {pid}: 目标={tgts}  条件={conds}')

    print('\n=== 4. 白名单（单发现 ⊑ 八纲）===')
    for f, want in [('tcm-zhengzhuang.owl', ['Fuman', 'Kouke', 'Shouzuleng']),
                    ('tcm-maixiang.owl', ['Hongmai'])]:
        t = open(os.path.join(ONT, f), encoding='utf-8').read()
        for cls in want:
            m = re.search(r'<owl:Class rdf:about="#' + cls + r'">(.*?)</owl:Class>', t, re.S)
            body = m.group(1) if m else ''
            tgts = re.findall(r'<rdfs:subClassOf rdf:resource="#(Biao|Li|Banbiaobanli|Yang|Yin)"', body)
            print(f'  {cls:<12} ({f}) -> {tgts}')

    print('\n=== 5. ABox 个体存在性（发现类需有 _instance 个体）===')
    abox_files = ['tcm-zhengzhuang-abox.owl', 'tcm-maixiang-abox.owl']
    for f in abox_files:
        p = os.path.join(ONT, f)
        if not os.path.exists(p):
            print(f'  MISSING {f}')
            continue
        t = open(p, encoding='utf-8').read()
        for cls in ['Fuman', 'Kouke', 'Shouzuleng', 'Hongmai']:
            has = (f'#{cls}_instance' in t) or re.search(r'rdf:about="#' + cls + r'_instance"', t)
            if has:
                print(f'  {cls}_instance  在 {f}')
    # 汇总缺失
    allabox = ''
    for f in abox_files:
        p = os.path.join(ONT, f)
        if os.path.exists(p):
            allabox += open(p, encoding='utf-8').read()
    print('  --- 缺失检查 ---')
    for cls in ['Fuman', 'Kouke', 'Shouzuleng', 'Hongmai']:
        if f'#{cls}_instance' not in allabox:
            print(f'  ⚠ 未找到 {cls}_instance')

    print('\n=== 6. tcm-all.owl 是否含新公理（合并产物同步性）===')
    p = os.path.join(ONT, 'tcm-all.owl')
    if os.path.exists(p):
        t = open(p, encoding='utf-8').read()
        for probe in ['Panju_A8', 'Panju_C6', 'Panju_D8']:
            print(f'  {probe}: {"含" if probe in t else "不含"}')
        for probe in ['#Fuman', '#Kouke']:
            print(f'  {probe}: {"含" if probe in t else "不含"}')
        # 检查 tcm-all 里 Fuman 是否已挂 Li
        m = re.search(r'<owl:Class rdf:about="#Fuman">(.*?)</owl:Class>', t, re.S)
        if m:
            print(f'  tcm-all 里 Fuman 超类 = {re.findall(r"subClassOf rdf:resource=\"#([A-Za-z]+)\"", m.group(1))}')
    else:
        print('  tcm-all.owl 不存在')

    print('\n=== 7. 副本同步检查（同名文件是否有多份）===')
    import glob
    for name in ['tcm-core.owl', 'tcm-zhengzhuang.owl', 'tcm-maixiang.owl']:
        hits = [x for x in glob.glob(os.path.join(ONT, '**', name), recursive=True)]
        print(f'  {name}: {len(hits)} 份 -> {[os.path.relpath(h, ONT) for h in hits]}')
finally:
    sys.stdout = old
open(os.path.join(ONT, '..', 'OntologyMachine', '_harness', 'r_landing.txt'), 'w',
     encoding='utf-8').write(buf.getvalue())
print('WROTE')
