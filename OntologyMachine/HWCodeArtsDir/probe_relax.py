import math

TOP = 9800.0
DEV = 500.0
OFF = 120.0
LAST_MIN = 850.0


def solve(bw, beta, H, last_max):
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
    first = z120 + DEV * eff / (1.0 + b * eff)
    nodes = [first]
    h = first
    nodeCeil = H + last_max
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
    if LAST_MIN - 1e-6 <= res <= last_max + 1e-6:
        return dict(first=first, beta=beta, last=last, res=res)
    return None


def first_feasible(bw, H, base=950.0, cap=1500.0):
    lm = base
    while lm <= cap + 1e-9:
        beta = 50.0
        while beta >= 40.0 - 1e-9:
            r = solve(bw, round(beta, 1), H, lm)
            if r:
                return lm, r
            beta -= 0.1
        lm += 1.0
    return None, None


for bw in [24]:
    for H in [100000.0]:
        lm, r = first_feasible(bw * 1000.0, H)
        if r:
            print(f"H={H:.0f} bw={bw} → 放宽 lastMax={lm:.0f} 首次可解 β={r['beta']} H0={r['first']:.2f} last={r['last']:.2f} res={r['res']:.2f}")
        else:
            print(f"H={H:.0f} bw={bw} → 放宽至 cap 仍无解")
