import math

TOP = 9800.0
DEV = 500.0
FIRST = 120.0
LAST_MIN, LAST_MAX = 850.0, 950.0
H = 115253.0


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
    return len(nodes) - 1, last, H - last


print("== H=115253, bw=25 : 全角度扫描 (50->40, step 0.1) ==")
rows = []
k = 0
while 50.0 - k * 0.1 >= 40.0 - 1e-9:
    beta = round(50.0 - k * 0.1, 1)
    k += 1
    r = run(25, beta)
    if r is None:
        continue
    p, last, res = r
    rows.append((beta, p, last, res))

feas = [x for x in rows if LAST_MIN <= x[3] <= LAST_MAX]
print(f"可行角度数 = {len(feas)}")

# 按节间数分组，展示各节间数下的余量区间
from collections import OrderedDict
grp = OrderedDict()
for beta, p, last, res in rows:
    grp.setdefault(p, []).append((beta, res))
print("\n节间数 | 角度范围 | 余量residual范围(mm)")
for p in sorted(grp):
    arr = grp[p]
    betas = [x[0] for x in arr]
    ress = [x[1] for x in arr]
    print(f"  {p}   | {min(betas):.1f}~{max(betas):.1f} | {min(ress):.1f} ~ {max(ress):.1f}")

# 窗口边界两侧最近的角度
below = sorted([x for x in rows if x[3] < LAST_MIN], key=lambda z: -z[3])[:3]
above = sorted([x for x in rows if x[3] > LAST_MAX], key=lambda z: z[3])[:3]
print("\n残余<850 中最接近窗口的3个:")
for beta, p, last, res in below:
    print(f"  β={beta:.1f} p={p} last={last:.1f} res={res:.1f}")
print("残余>950 中最接近窗口的3个:")
for beta, p, last, res in above:
    print(f"  β={beta:.1f} p={p} last={last:.1f} res={res:.1f}")

print("\n关键：β=47.7 / 43.1 / 42.0 明细")
for beta in (47.7, 43.1, 42.0):
    r = run(25, beta)
    if r:
        p, last, res = r
        print(f"  β={beta}: p={p} last={last:.3f} residual={res:.3f}")

print("\n== 对照：邻近底宽 (同样 H=115253) 的可行角度 ==")
for bw in (24, 25, 26, 27, 28, 29):
    k = 0
    ok = []
    while 50.0 - k * 0.1 >= 40.0 - 1e-9:
        beta = round(50.0 - k * 0.1, 1)
        k += 1
        r = run(bw, beta)
        if r and LAST_MIN <= r[2] <= LAST_MAX:
            ok.append((beta, r[0], round(r[2], 1)))
    print(f"  bw={bw}: {ok if ok else '无解'}")
