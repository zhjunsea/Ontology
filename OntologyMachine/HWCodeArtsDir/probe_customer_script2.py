import math
import numpy as np
from probe_customer_script import probe, cal_angle, cal_length  # noqa

def scan(dis_bott, dis_top, h0, h1, step, beta1=47.7):
    sols = []
    h = h0
    while h <= h1:
        r = probe(dis_bott, dis_top, h, beta1_deg=beta1)
        if r and r['solved']:
            sols.append((h, r['n_temp'], r['last_res'], r['tot_l'], r['L']))
        h += step
    return sols

def merge(sols, gap=3000):
    if not sols:
        return []
    out = []
    start = sols[0][0]; end = sols[0][0]; ns = {sols[0][1]}
    for h, n, res, tl, L in sols[1:]:
        if h - end <= gap:
            end = h; ns.add(n)
        else:
            out.append((start, end, sorted(ns)))
            start = h; end = h; ns = {n}
    out.append((start, end, sorted(ns)))
    return out

if __name__ == '__main__':
    print("=== (A) dis_top=9800：h in [114000,121000] step 25mm 有解点 ===")
    s = scan(25000, 9800, 114000, 121000, 25)
    for h, n, res, tl, L in s:
        print(f"  h={h}  n={n}  res={res:.2f}  window_hit")
    print("  有解点数量:", len(s))

    print()
    print("=== (B) dis_top=9800：h in [100000,300000] step 100mm，解区间(窗宽) ===")
    s2 = scan(25000, 9800, 100000, 300000, 100)
    for a, b, ns in merge(s2):
        print(f"  [{a}, {b}]  宽度={b-a}mm  段数={ns}")
    print("  有解点数:", len(s2))

    print()
    print("=== (C) 关键对照 ===")
    for top, h in [(9604.88, 116733), (9800, 116733), (9800, 118000), (9800, 120000)]:
        r = probe(25000, top, h)
        print(f"  dis_top={top}, h={h}: alpha={r['alpha_deg']:.3f} gama={r['gama_deg']:.3f} "
              f"L={r['L']:.2f} n={r['n_temp']} solved={r['solved']} res={r['last_res']:.2f}")

    print()
    print("=== (D) dis_top=9800, h=116733：扫描 beta1 in [40,50] step 0.1 ===")
    found = []
    b = 40.0
    while b <= 50.0001:
        r = probe(25000, 9800, 116733, beta1_deg=b)
        if r and r['solved']:
            found.append((round(b, 1), r['n_temp'], r['last_res']))
        b = round(b + 0.1, 4)
    print("  beta1 有解点:", found)
