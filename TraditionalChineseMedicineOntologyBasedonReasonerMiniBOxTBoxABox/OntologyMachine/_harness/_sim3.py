# -*- coding: utf-8 -*-
"""_sim3.py —— 在 panju_sim2 基础上，把 MUTEX 与 阴阳策略 一起做网格对比。
用法: python _sim3.py [--suite]
"""
import sys, os, argparse
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import panju_sim2 as S

ap = argparse.ArgumentParser()
ap.add_argument('--suite', action='store_true')
args = ap.parse_args()

subclass, panju, bagang_of = S.load_ontology()
cases = S.load_cases(args.suite)
print(f'判据数={len(panju)}  用例数={len(cases)}  suite={args.suite}')

def run(mutex, yy):
    S.MUTEX = mutex
    fails, undec, npass = [], [], 0
    for cs in cases:
        bagang, chans, detail = S.derive(cs, subclass, panju, bagang_of, yy)
        ok, kind = S.judge(cs, chans)
        if ok is False:
            fails.append((cs['file'], cs['name'], cs['lj'], S.resolve_anchor(cs['lj']), sorted(bagang), chans))
        elif ok is None:
            undec.append((cs['file'], cs['name'], cs['lj']))
        else:
            npass += 1
    return npass, fails, undec

for mutex_name, mutex in (('MUTEX有', {'Shaoyangbing': 'Jueyinbing'}), ('MUTEX无', {})):
    for yy in ('none', 'hanre', 'hanre_xushi', 'panju_first', 'panju_hanre'):
        npass, fails, undec = run(mutex, yy)
        print(f'\n=== {mutex_name}  yy={yy}: 通过 {npass}/{len(cases)}  失败 {len(fails)}  无法判定 {len(undec)} ===')
        if fails:
            for f in fails[:25]:
                print(f'   FAIL {f[1]:<26} 期望={f[3]} 八纲={f[4]} 六经={f[5]}')
            if len(fails) > 25:
                print(f'   ... 另 {len(fails)-25} 例')
