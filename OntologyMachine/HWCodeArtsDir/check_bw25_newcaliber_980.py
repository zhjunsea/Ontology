# -*- coding: utf-8 -*-
"""新口径 + 当前窗口[850,980]：bw=25 全角度判定（★16 诊断脚本）。"""
import math

TOP = 9800.0
DEV = 500.0
FIRST = 120.0
LAST_MIN, LAST_MAX = 850.0, 980.0
BETA_HI, BETA_LO, STEP = 50.0, 40.0, 0.1


def run(bw_m, beta, H):
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
    ceil = H + LAST_MAX
    for _ in range(200000):
        hn = (h * (1.0 + b * eff) + 2.0 * a * eff) / denom
        if math.isnan(hn) or hn <= h or hn > ceil:
            break
        nodes.append(hn)
        h = hn
    last = nodes[-1]
    residual = last - H
    return dict(p=len(nodes) - 1, last=last, residual=residual,
                feasible=(LAST_MIN - 1e-6) <= residual <= (LAST_MAX + 1e-6),
                zstar=last + DEV * eff / denom)


def report(H):
    print("=== H=%.0f  bw=25  new-caliber window[%.0f,%.0f] ===" % (H, LAST_MIN, LAST_MAX))
    r = run(25, 47.7, H)
    print("  beta=47.7: ●=%.3f  residual=%.3f  feasible=%s" %
          (r['last'], r['residual'], r['feasible']))
    # show the jump over the window
    feas = []
    for i in range(101):
        beta = round(BETA_HI - i * STEP, 1)
        rr = run(25, beta, H)
        if rr and rr['feasible']:
            feas.append((beta, rr))
    print("  bw=25 可行角度: " +
          (", ".join("b=%.1f(res=%.1f,●=%.1f)" % (b, x['residual'], x['last']) for b, x in feas)
           or "无"))
    print()


report(115253.0)
report(100000.0)
