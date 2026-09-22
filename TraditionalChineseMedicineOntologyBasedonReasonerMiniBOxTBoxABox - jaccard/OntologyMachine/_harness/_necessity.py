# -*- coding: utf-8 -*-
"""逐条移除测试：把当前本体复制成快照，删掉某一条改动，看通过数损失。
用于回答「这条改动真的必要吗」。"""
import os, re, io, sys, shutil, subprocess

ROOT = r'D:\work\Ontology\TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox'
ONT = os.path.join(ROOT, 'ontology')
HARNESS = os.path.join(ROOT, 'OntologyMachine', '_harness')
TMPROOT = os.path.join(HARNESS, '_variants')
PY = r'C:\Users\ocean\.workbuddy\binaries\python\versions\3.13.12\python.exe'

# 每条改动 = (名称, 文件, 用于定位并删除的正则/字符串)
CHANGES = [
    ('Fuman->Li',        'tcm-zhengzhuang.owl', '<rdfs:subClassOf rdf:resource="#Li"/>', 'Fuman'),
    ('Kouke->Li',        'tcm-zhengzhuang.owl', '<rdfs:subClassOf rdf:resource="#Li"/>', 'Kouke'),
    ('Shouzuleng->Ban',  'tcm-zhengzhuang.owl', '<rdfs:subClassOf rdf:resource="#Banbiaobanli"/>', 'Shouzuleng'),
    ('Hongmai->Yang',    'tcm-maixiang.owl',    '<rdfs:subClassOf rdf:resource="#Yang"/>', 'Hongmai'),
    ('Panju_A8',         'tcm-core.owl',        None, 'Panju_A8'),
    ('Panju_C6',         'tcm-core.owl',        None, 'Panju_C6'),
    ('Panju_D8',         'tcm-core.owl',        None, 'Panju_D8'),
]


def build_snapshot(dst, drop=None):
    if os.path.exists(dst):
        shutil.rmtree(dst)
    os.makedirs(dst)
    for dp, dn, fn in os.walk(ONT):
        rel = os.path.relpath(dp, ONT)
        d = os.path.join(dst, rel) if rel != '.' else dst
        os.makedirs(d, exist_ok=True)
        for f in fn:
            if f.endswith('.owl'):
                shutil.copy2(os.path.join(dp, f), os.path.join(d, f))
    if drop:
        name, fname, _, key = drop
        p = os.path.join(dst, fname)
        s = open(p, encoding='utf-8').read()
        if key.startswith('Panju_'):
            # 删除整个 <owl:Class rdf:about="#Panju_X"> ... </owl:Class> 块
            s2 = re.sub(r'\s*<owl:Class rdf:about="#' + key + r'">.*?</owl:Class>\s*(?=<owl:Class rdf:about=)',
                        '\n    ', s, count=1, flags=re.S)
        else:
            # 删除该具名类块内的某条 subClassOf
            m = re.search(r'(<owl:Class rdf:about="#' + key + r'">)(.*?)(</owl:Class>)', s, re.S)
            body = m.group(2)
            # 只删第一条匹配（该块内）
            nb = body.replace('<rdfs:subClassOf rdf:resource="#Li"/>', '', 1) \
                if '#Li' in drop[2] else \
                body.replace('<rdfs:subClassOf rdf:resource="#Banbiaobanli"/>', '', 1) \
                if '#Banbiaobanli' in drop[2] else \
                body.replace('<rdfs:subClassOf rdf:resource="#Yang"/>', '', 1)
            s2 = s[:m.start(2)] + nb + s[m.end(2):]
        assert s2 != s, f'未改动 {key}'
        open(p, 'w', encoding='utf-8').write(s2)


def run(ont_dir):
    env = dict(os.environ, PANJU_ONT=ont_dir, PYTHONIOENCODING='utf-8')
    r = subprocess.run([PY, os.path.join(HARNESS, '_run.py'),
                        os.path.join(HARNESS, '_tmp_nec.txt')],
                       env=env, capture_output=True, text=True, encoding='utf-8')
    out = open(os.path.join(HARNESS, '_tmp_nec.txt'), encoding='utf-8').read()
    m = re.search(r'通过 (\d+) / (\d+)\s+失败 (\d+)\s+无法判定 (\d+)', out)
    pj = re.search(r'判据数=(\d+)', out)
    return (int(m.group(1)), int(m.group(2)), int(m.group(3)), int(m.group(4)), int(pj.group(1)))


buf = io.StringIO(); old = sys.stdout; sys.stdout = buf
try:
    base_dir = os.path.join(TMPROOT, 'base')
    build_snapshot(base_dir)
    b = run(base_dir)
    print(f'基线（当前本体）: 通过 {b[0]}/{b[1]}  失败 {b[2]}  无法判定 {b[3]}  判据数 {b[4]}')
    print()
    print('--- 逐条移除 ---')
    print(f'{"移除项":<18}{"通过":>6}{"失败":>6}{"无法判定":>8}{"判据数":>7}{"Δ通过":>8}')
    for ch in CHANGES:
        d = os.path.join(TMPROOT, ch[0].replace('->', '_').replace('/', '_'))
        try:
            build_snapshot(d, drop=ch)
        except AssertionError as e:
            print(f'{ch[0]:<18}  跳过（{e}）')
            continue
        r = run(d)
        print(f'{ch[0]:<18}{r[0]:>6}{r[2]:>6}{r[3]:>8}{r[4]:>7}{r[0]-b[0]:>+8}')
finally:
    sys.stdout = old
open(os.path.join(HARNESS, 'r_necessity.txt'), 'w', encoding='utf-8').write(buf.getvalue())
print('WROTE')
