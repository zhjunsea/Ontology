# -*- coding: utf-8 -*-
"""预测新口径：height=9800面高H；结构自动上延；●=<=H+950 的最高偏心节点；
residual=●-H∈[850,950]；high_truss=●+dev*eff/(1-b*eff)。（★16 诊断脚本）"""
import math

TOP = 9800.0
DEV = 500.0
FIRST = 120.0
LAST_MIN, LAST_MAX = 850.0, 950.0
BW_LO, BW_HI = 15, 30
BETA_LO, BETA_HI, STEP = 40.0, 50.0, 0.1


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
    ceil = H + LAST_MAX            # 递归上限：窗口上界
    for _ in range(200000):
        hn = (h * (1.0 + b * eff) + 2.0 * a * eff) / denom
        if math.isnan(hn) or hn <= h or hn > ceil:
            break
        nodes.append(hn)
        h = hn
    last = nodes[-1]
    residual = last - H            # ● 在 H 之上
    zstar = last + DEV * eff / denom
    return dict(p=len(nodes) - 1, last=last, residual=residual,
                zstar=zstar, dz=zstar - last, eff=eff, b=b, a=a)


def scan(H):
    sols = []
    for bw in range(BW_LO, BW_HI + 1):
        k = 0
        while BETA_HI - k * STEP >= BETA_LO - 1e-9:
            beta = round(BETA_HI - k * STEP, 1)
            k += 1
            r = run(bw, beta, H)
            if r is None:
                continue
            if LAST_MIN - 1e-6 <= r['residual'] <= LAST_MAX + 1e-6:
                sols.append((bw, beta, r))
    return sols


def run_old(bw_m, beta, H):
    """旧口径：递归上限=H；residual=H-last（● 在 H 之下）。用于验证复刻忠实性。"""
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
    return dict(p=len(nodes) - 1, residual=H - last, last=last)


def scan_old(H):
    sols = []
    for bw in range(BW_LO, BW_HI + 1):
        k = 0
        while BETA_HI - k * STEP >= BETA_LO - 1e-9:
            beta = round(BETA_HI - k * STEP, 1)
            k += 1
            r = run_old(bw, beta, H)
            if r is None:
                continue
            if LAST_MIN - 1e-6 <= r['residual'] <= LAST_MAX + 1e-6:
                sols.append((bw, beta, r))
    return sols


for H, tag in [(100000.0, "测试用 height=100000"), (115253.0, "客户锚点 H=115253")]:
    olds = scan_old(H)
    obws = sorted(set(s[0] for s in olds))
    print(f"== [旧口径校验] {tag}: 可行解数={len(olds)} 覆盖底宽={obws} bw=24有解={'有' if 24 in obws else '无'}")
    sols = scan(H)
    bws = sorted(set(s[0] for s in sols))
    print(f"== {tag} : 可行解数={len(sols)}  覆盖底宽={bws} ==")
    for bw, beta, r in sols[:8]:
        print(f"  bw={bw:>2} beta={beta:>5.1f} p={r['p']:>3} last={r['last']:.2f} "
              f"residual={r['residual']:.2f} z*={r['zstar']:.2f} dz={r['dz']:.2f}")
    if len(sols) > 8:
        print(f"  ... 共 {len(sols)} 条")
    print(f"  bw=24 是否有解: {'有' if 24 in bws else '无'}")
    print()

# 客户锚点复现核对
H = 115253.0
r = run(25, 47.7, H)
print("客户参数 bw=25 beta=47.7 H=115253:")
if r:
    print(f"  last(●)={r['last']:.2f} residual={r['residual']:.2f} dz={r['dz']:.2f} z*(=high_truss)={r['zstar']:.2f}")
    print(f"  dis_top=width(z*)={25000.0 + (TOP-25000.0)*r['zstar']/H:.2f}  (实测9604.88, high_truss实测116733)")
