import math

top = 9800.0
dev = 500.0
firstOff = 120.0
height = 116733.0
bw = 25000.0

b = (top - bw) / (2.0 * height)


def solve(beta_deg):
    t = math.tan(math.radians(beta_deg))
    disc = 1.0 - b * b * t * t
    if disc <= 1e-12:
        return None
    eff = t / math.sqrt(disc)
    denom = 1.0 - b * eff
    if denom <= 1e-9:
        return None
    z120 = firstOff / math.sqrt(1.0 + b * b)
    dev1 = z120 + dev * eff / (1.0 + b * eff)
    h = dev1
    nodeCeil = height + 950.0
    nodes = [dev1]
    for _ in range(200000):
        hn = (h * (1.0 + b * eff) + 2.0 * (bw / 2.0 - dev) * eff) / denom
        if math.isnan(hn) or hn <= h or hn > nodeCeil:
            break
        nodes.append(hn)
        h = hn
    last = nodes[-1]
    highTruss = last + dev * eff / denom
    return dev1, highTruss, last - height


def delta_of(highTruss):
    topAtHi = bw + (top - bw) * (highTruss / height)
    pb1 = (bw / 2.0, bw / 2.0, 0.0)
    pt1 = (topAtHi / 2.0, topAtHi / 2.0, highTruss)
    p1 = (top / 2.0, bw / 2.0, 0.0)
    v1 = (p1[0] - pb1[0], p1[1] - pb1[1], p1[2] - pb1[2])
    v2 = (pt1[0] - pb1[0], pt1[1] - pb1[1], pt1[2] - pb1[2])
    dot = v1[0] * v2[0] + v1[1] * v2[1] + v1[2] * v2[2]
    n1 = math.sqrt(sum(c * c for c in v1))
    n2 = math.sqrt(sum(c * c for c in v2))
    alpha = math.acos(max(-1.0, min(1.0, dot / (n1 * n2))))
    return dev * math.tan(math.pi / 2.0 - alpha), math.degrees(alpha)


print("old script: H_0=744.817 (dis_top=9604.88, beta=47.7)")
print("new top=9800, height=116733, bw=25m:")
for beta in (47.9, 47.7, 44.1, 40.8):
    r = solve(beta)
    if r is None:
        print(f"  beta={beta}: 无解")
        continue
    dev1, highTruss, residual = r
    d, alpha = delta_of(highTruss)
    h0 = dev1 + d
    print(f"  beta={beta}: dev1={dev1:.3f} highTruss={highTruss:.3f} residual={residual:.3f} alpha={alpha:.4f} delta={d:.3f} H_0={h0:.3f}")
