import math

TOP = 9800.0
DEV = 500.0
OFF = 120.0
LAST_MIN, LAST_MAX = 850.0, 950.0


def solve(bw, beta, H, shift=True):
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
        return dict(first=first, beta=beta)
    return None


def scan(H):
    feas, no, total = [], [], 0
    for bw in range(15, 31):
        sols = []
        beta = 50.0
        while beta >= 40.0 - 1e-9:
            r = solve(bw * 1000.0, round(beta, 1), H)
            if r:
                sols.append(r)
            beta -= 0.1
        total += len(sols)
        (feas if sols else no).append(bw)
    return feas, no, total


for H in [100000.0, 115253.0]:
    f, n, total = scan(H)
    print(f"H={H:>9.0f} | 可行解总数={total:>3} | 有解底宽={f} | 无解底宽={n}")
