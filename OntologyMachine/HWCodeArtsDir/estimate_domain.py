import math

H = 115253.0     # 9800 顶面高度 (mm)
TOP = 9800.0
DEV = 500.0
OFF = 120.0
LAST_MIN, LAST_MAX = 850.0, 950.0


def solve(bw, beta, shift):
    """shift=False: 首节点=z120(旧); shift=True: 首节点=z120+Δ(新, Δ=dev*eff/(1+b*eff))"""
    b = (TOP - bw) / (2.0 * H)
    t = math.tan(math.radians(beta))
    disc = 1.0 - b * b * t * t
    if disc <= 1e-12:
        return None
    eff = t / math.sqrt(disc)
    a = bw / 2.0 - DEV
    denom = 1.0 - b * eff
    if denom <= 1e-9 or a <= 0:
        return None
    z120 = OFF / math.sqrt(1.0 + b * b)
    d = DEV * eff / (1.0 + b * eff) if shift else 0.0
    first = z120 + d
    nodes = [first]
    h = first
    nodeCeil = H + LAST_MAX
    for _ in range(200000):
        hNext = (h * (1.0 + b * eff) + 2.0 * a * eff) / denom
        if math.isnan(hNext) or hNext <= h or hNext > nodeCeil:
            break
        nodes.append(hNext)
        h = hNext
    if len(nodes) < 2:
        return None
    last = nodes[-1]
    res = last - H
    if LAST_MIN - 1e-6 <= res <= LAST_MAX + 1e-6:
        return dict(first=first, n=len(nodes) - 1, last=last, res=res)
    return None


def scan(shift):
    rows = []
    bw = 16000.0
    while bw <= 30000.0 + 0.5:
        sols = []
        beta = 50.0
        while beta >= 40.0 - 1e-9:
            r = solve(bw, round(beta, 1), shift)
            if r:
                sols.append((round(beta, 1), r))
            beta -= 0.1
        rows.append((int(bw / 1000), sols))
        bw += 1000.0
    return rows


for tag, shift in [("旧(首节点=z120)", False), ("新(首节点=z120+Δ)", True)]:
    rows = scan(shift)
    total = sum(len(s) for _, s in rows)
    print(f"===== {tag} ===== 可行解总数={total}  有解底宽数={sum(1 for _, s in rows if s)}")
    for bw, sols in rows:
        if not sols:
            print(f"  bw={bw:>4}m  : 无解")
            continue
        parts = []
        for beta, r in sols:
            parts.append(f"β={beta:>4} n={r['n']:>2} H0={r['first']:>8.2f} last={r['last']:>9.2f} res={r['res']:>7.2f}")
        print(f"  bw={bw:>4}m  :")
        for p in parts:
            print(f"      {p}")
