# -*- coding: utf-8 -*-
"""_ablate.py —— 对候选判据做消融，找最小必要集。
直接复用 panju_sim 的解析与推理函数。
"""
import os, sys, io, itertools
from collections import defaultdict

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import panju_sim as S

Z = 'you_zhengzhuang'; M = 'you_maixiang'; T = 'you_shexiang'

# 候选判据： id -> (目标八纲集合, [(prop, frag), ...])
CANDS = {
    'A8':  ({'Biao'},          [(Z, 'Danyumei'), (M, 'Weiximai')]),
    'A9':  ({'Biao'},          [(Z, 'Danyumei'), (M, 'Chenweimai')]),
    'B7':  ({'Li'},            [(Z, 'Fuman')]),
    'B8':  ({'Li'},            [(Z, 'Kouke')]),
    'C6':  ({'Banbiaobanli'},  [(Z, 'Wanglaihanre'), (Z, 'Xiongxiekuman')]),
    'C7':  ({'Banbiaobanli'},  [(Z, 'Shouzuleng'), (Z, 'Xiali')]),
    'C8':  ({'Banbiaobanli'},  [(Z, 'Shouzuleng')]),
    'C9':  ({'Banbiaobanli'},  [(Z, 'Xiaoke')]),
    'D8':  ({'Yang'},          [(Z, 'Ehan'), (M, 'Fumai')]),
    'D9':  ({'Yang'},          [(Z, 'Wanglaihanre'), (Z, 'Xiongxiekuman')]),
    'D10': ({'Yang'},          [(M, 'Hongmai')]),
}

# 并集型判据（alts）：任一分支持有即命中
ALTS = {
    # 少阴提纲 281 条：脉微细 → 微细脉；临床常见沉微脉
    'A8u': ({'Biao'}, [[(Z, 'Danyumei'), (M, 'Weiximai')],
                       [(Z, 'Danyumei'), (M, 'Chenweimai')]]),
}

# 一条判据挂两个八纲（少阳：半表半里 + 阳）
MULTI = {
    'C6y': ({'Banbiaobanli', 'Yang'}, [(Z, 'Wanglaihanre'), (Z, 'Xiongxiekuman')]),
}


def _and(items):
    return ('and', [('restr', p, f) for p, f in items])


def as_extra(ids):
    out = []
    for i in ids:
        if i in ALTS:
            tgt, alts = ALTS[i]
            expr = ('or', [_and(b) for b in alts])
        elif i in MULTI:
            tgt, conj = MULTI[i]
            expr = _and(conj)
        else:
            tgt, conj = CANDS[i]
            expr = _and(conj)
        out.append({'id': i + '(候选)', 'targets': set(tgt), 'expr': expr})
    return out


def run(ids, subclass, panju, bagang_of, cases):
    extra = as_extra(ids)
    ok = 0
    fails = []
    for cs in cases:
        _, chans, _ = S.derive(cs, subclass, panju, bagang_of, extra)
        good, kind = S.judge(cs, chans)
        if good:
            ok += 1
        else:
            fails.append((cs, chans))
    return ok, fails


def main():
    subclass, panju, bagang_of = S.load_ontology()
    cases = S.load_cases()
    n = len(cases)

    full = list(CANDS.keys())
    base_ok, _ = run([], subclass, panju, bagang_of, cases)
    full_ok, _ = run(full, subclass, panju, bagang_of, cases)
    print(f'用例数={n}  基线={base_ok}  全量{len(full)}条={full_ok}')

    print('\n--- 留一消融（去掉某条后的通过数；若明显低于全量则该条必要）---')
    for i in full:
        ok, _ = run([x for x in full if x != i], subclass, panju, bagang_of, cases)
        print(f'  去掉 {i:<4} -> {ok:>4}   (Δ={ok - full_ok:+d})')

    print('\n--- 贪心前向选择（从空集开始，每轮加增益最大者）---')
    chosen = []
    cur = base_ok
    remaining = list(full)
    while remaining:
        best, best_ok = None, -1
        for i in remaining:
            ok, _ = run(chosen + [i], subclass, panju, bagang_of, cases)
            if ok > best_ok:
                best, best_ok = i, ok
        if best_ok <= cur:
            break
        chosen.append(best)
        remaining.remove(best)
        print(f'  + {best:<4} -> {best_ok:>4}   (Δ={best_ok - cur:+d})')
        cur = best_ok
    print(f'  贪心最小集 = {chosen}  通过 {cur}/{n}')

    print('\n--- 剩余失败明细（贪心集）---')
    _, fails = run(chosen, subclass, panju, bagang_of, cases)
    by = defaultdict(list)
    for cs, chans in fails:
        by[tuple(S.resolve_anchor(cs['lj']))].append((cs['name'], chans))
    for exp, rs in sorted(by.items(), key=lambda kv: -len(kv[1])):
        print(f'  期望 {exp} —— {len(rs)} 例')
        for nm, ch in rs[:6]:
            print(f'      {nm:<22} 推出={ch}')

    # ---- 落地形态验证：4 条白名单 + 3 条判据 ----
    # 白名单在模拟器里等价于「单症状合取判据」
    FINAL = ['B7', 'B8', 'C8', 'D10', 'A8u', 'C6y', 'D8']
    ok, fails = run(FINAL, subclass, panju, bagang_of, cases)
    print(f'\n--- 落地形态（白名单4 + 判据3）: {FINAL} ---')
    print(f'  通过 {ok} / {len(cases)}')
    by = defaultdict(list)
    for cs, chans in fails:
        by[tuple(S.resolve_anchor(cs['lj']))].append((cs['name'], chans))
    for exp, rs in sorted(by.items(), key=lambda kv: -len(kv[1])):
        print(f'  期望 {exp} —— {len(rs)} 例')
        for nm, ch in rs[:8]:
            print(f'      {nm:<22} 推出={ch}')


if __name__ == '__main__':
    buf = io.StringIO(); old = sys.stdout; sys.stdout = buf
    try:
        main()
    finally:
        sys.stdout = old
    open(os.path.join(HERE, 'r_ablate.txt'), 'w', encoding='utf-8').write(buf.getvalue())
    print('WROTE r_ablate.txt')
