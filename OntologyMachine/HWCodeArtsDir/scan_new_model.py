# -*- coding: utf-8 -*-
"""新口径（斜面正弦分段）逐底宽×角度扫描，复算测试基线（H=100000 / H=115253，bw 15~30）。"""
import math

TOP = 9800.0
DEV = 500.0
FIRST = 120.0
LMIN, LMAX = 850.0, 980.0
STEP_RELAX = 5.0
BLO, BHI, BSTEP = 40.0, 50.0, 0.1


def nodes_for(H, bwMm, beta):
    dx = (bwMm - TOP) / 2.0
    den = math.sqrt(2.0 * dx * dx + H * H)
    alpha = math.acos(dx / den)
    sinphi = H / den
    cosg = math.sqrt(2.0) * dx / den
    sing = math.sqrt(max(0.0, 1.0 - cosg * cosg))
    b = (TOP - bwMm) / (2.0 * H)
    bt = math.radians(beta)
    t = math.tan(bt)
    disc = 1.0 - b * b * t * t
    if disc <= 1e-12:
        return None
    eff = t / math.sqrt(disc)
    z1 = FIRST / math.sqrt(1.0 + b * b)
    Delta = DEV * eff / (1.0 + b * eff)
    dev1 = z1 + Delta
    deltaM = DEV * math.tan(math.pi / 2.0 - alpha)
    H0 = dev1 + deltaM
    L0 = H0 / sing
    disx0 = (bwMm - 2.0 * DEV / math.sin(alpha)) - 2.0 * (L0 - DEV / math.tan(alpha)) * math.cos(alpha)
    r = 1.0 - 2.0 * math.cos(alpha) * math.sin(bt) / math.sin(alpha + bt)
    g1 = disx0 * math.sin(bt) * sinphi / math.sin(alpha + bt)
    if not (0.0 < r < 1.0) or g1 <= 0:
        return None
    return dev1, g1, r, eff, b


def solve(H, bwMm, lastMax):
    dx = (bwMm - TOP) / 2.0
    den = math.sqrt(2.0 * dx * dx + H * H)
    alpha = math.acos(dx / den)
    sinphi = H / den
    b = (TOP - bwMm) / (2.0 * H)
    best = None
    beta = BHI
    n = int(round((BHI - BLO) / BSTEP))
    for k in range(n + 1):
        beta = BHI - k * BSTEP
        res = nodes_for(H, bwMm, beta)
        if res is None:
            continue
        dev1, g1, r, eff, bb = res
        ceil = H + lastMax
        e = dev1
        gap = g1
        last = e
        for _ in range(200000):
            en = e + gap
            if not (en > e) or en > ceil:
                break
            last = en
            e = en
            gap *= r
        residual = last - H
        if LMIN - 1e-6 <= residual <= lastMax + 1e-6:
            sol = dict(beta=round(beta, 1), last=round(last, 3), residual=round(residual, 3))
            if best is None or sol["beta"] > best["beta"]:
                best = sol
    return best


def run(H):
    base_ok, relaxed = [], []
    for bw in range(15, 31):
        s = solve(H, bw * 1000.0, LMAX)
        if s:
            base_ok.append((bw, s["beta"], s["residual"]))
        else:
            lm = LMAX
            steps = 0
            sr = None
            while lm < 5000:
                lm += STEP_RELAX
                steps += 1
                sr = solve(H, bw * 1000.0, lm)
                if sr:
                    break
            relaxed.append((bw, lm, steps, sr))
    print("H=%d  基准有解底宽数=%d  无解底宽（触发放宽）=%s" %
          (H, len(base_ok), [bw for bw, _, _ in [] ] or [r[0] for r in relaxed]))
    print("  基准有解:", [(bw, b, res) for bw, b, res in base_ok])
    print("  放宽:", [(bw, int(lm), st, (sr or {}).get('residual')) for bw, lm, st, sr in relaxed])
    print("  solutionCount=%d（含放宽，每个底宽必有解）  relaxedNoSolutionCount=%d" %
          (len(base_ok) + len(relaxed), len(relaxed)))
    print()
    return base_ok, relaxed


run(115253)
run(100000)

# 锚点 & P-2 关注底宽
print("=== 锚点 bw=25 ===")
for H in (115253, 115253.51):
    print("  H=%s  base解:" % H, solve(H, 25000.0, LMAX))
