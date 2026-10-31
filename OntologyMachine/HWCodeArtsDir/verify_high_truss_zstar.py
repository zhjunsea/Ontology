# -*- coding: utf-8 -*-
"""核验：high_truss 由 last 改为 z*=last+dev*eff/(1-b*eff) 的影响（★16：诊断脚本，不入模块目录）。

判据 residual=H-last∈[850,950] 与 H 输入均不变（用户 2026-10-28 裁定），
故可行域（哪些 bw/beta 可行）应完全不变，仅 high_truss 输出值变化。
"""
import math

TOP = 9800.0          # 顶面宽度 mm
DEV = 500.0           # 偏心量 mm
FIRST = 120.0         # 首杆偏移 mm
LAST_MIN, LAST_MAX = 850.0, 950.0
H = 115253.0          # 样例塔高（顶面 9800 平面高度）
BW_LO, BW_HI = 15, 30
BETA_LO, BETA_HI, STEP = 40.0, 50.0, 0.1


def run(bw_m, beta):
    bwmm = bw_m * 1000.0
    t = math.tan(math.radians(beta))
    a = bwmm / 2.0 - DEV
    b = (TOP - bwmm) / (2.0 * H)
    disc = 1.0 - b * b * t * t
    if disc <= 1e-12:
        return None
    eff = t / math.sqrt(disc)
    denom = 1.0 - b * eff
    if denom <= 1e-9 or a <= 0:
        return None
    first = FIRST / math.sqrt(1.0 + b * b)
    nodes = [first]
    h = first
    for _ in range(200000):
        hn = (h * (1.0 + b * eff) + 2.0 * a * eff) / denom
        if math.isnan(hn) or hn <= h or hn > H:
            break
        nodes.append(hn)
        h = hn
    last = nodes[-1]
    residual = H - last
    zstar = last + DEV * eff / (1.0 - b * eff)   # 交塔柱中心线（顶段延长线）
    return dict(p=len(nodes) - 1, last=last, residual=residual,
                zstar=zstar, dz=zstar - last, eff=eff, b=b, a=a)


def scan():
    sols = []
    for bw in range(BW_LO, BW_HI + 1):
        k = 0
        while BETA_HI - k * STEP >= BETA_LO - 1e-9:
            beta = round(BETA_HI - k * STEP, 1)
            k += 1
            r = run(bw, beta)
            if r is None:
                continue
            if LAST_MIN - 1e-6 <= r['residual'] <= LAST_MAX + 1e-6:
                sols.append((bw, beta, r))
    return sols


sols = scan()
print(f"H={H}  可行解数(判据 residual=H-last∈[{LAST_MIN},{LAST_MAX}] 不变) = {len(sols)}")
print()
print(f"{'bw':>3} {'beta':>5} {'p':>3} {'last(旧h_t)':>12} {'residual':>9} {'dz':>8} {'z*(新h_t)':>11} {'z*<H':>5}")
for bw, beta, r in sols:
    flag = "OK" if r['zstar'] < H else ">H!"
    print(f"{bw:>3} {beta:>5.1f} {r['p']:>3} {r['last']:>12.3f} {r['residual']:>9.3f} {r['dz']:>8.3f} {r['zstar']:>11.3f} {flag:>5}")

print()
if sols:
    dzs = [r['dz'] for _, _, r in sols]
    worst = max(sols, key=lambda x: x[2]['zstar'])
    print(f"dz(=z*-last) 范围 : {min(dzs):.3f} ~ {max(dzs):.3f} mm")
    print(f"z* 最大值        : {worst[2]['zstar']:.3f} @ bw={worst[0]} beta={worst[1]}")
    print(f"H                : {H}")
    print(f"是否所有解 z*<H  : {'是' if worst[2]['zstar'] < H else '否（存在交点越过顶面，需复核语义）'}")
    print(f"旧 high_truss 取值范围: {min(r['last'] for _,_,r in sols):.3f} ~ {max(r['last'] for _,_,r in sols):.3f}")
    print(f"新 high_truss 取值范围: {min(r['zstar'] for _,_,r in sols):.3f} ~ {max(r['zstar'] for _,_,r in sols):.3f}")

print()
print("== 抽样明细 ==")
for bw, beta in [(25, 47.7), (25, 43.1), (25, 42.0), (29, 45.0), (15, 45.0)]:
    r = run(bw, beta)
    if r:
        feas = LAST_MIN <= r['residual'] <= LAST_MAX
        print(f"bw={bw:>2} beta={beta:>5.1f}: p={r['p']:>2} last={r['last']:.3f} "
              f"residual={r['residual']:.3f} z*={r['zstar']:.3f} dz={r['dz']:.3f} "
              f"eff={r['eff']:.6f} b={r['b']:.6f} a={r['a']:.3f} 可行={feas}")
