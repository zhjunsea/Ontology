import math

TOP = 9800.0
DEV = 500.0
FIRST = 120.0
LAST_MIN = 850.0
LAST_MAX = 950.0
B_LO, B_HI, STEP = 40.0, 50.0, 0.1


def solve(H, bw_min, bw_max, new_model=True):
    res = []
    for bw in range(bw_min, bw_max + 1):
        bwmm = bw * 1000.0
        k = 0
        while B_HI - k * STEP >= B_LO - 1e-9:
            beta = B_HI - k * STEP
            k += 1
            t = math.tan(math.radians(beta))
            a = bwmm / 2.0 - DEV
            b = (TOP - bwmm) / (2.0 * H)
            if new_model:
                disc = 1.0 - b * b * t * t
                if disc <= 1e-12:
                    continue
                T = t / math.sqrt(disc)
                first_vert = FIRST / math.sqrt(1.0 + b * b)
            else:
                T = t
                first_vert = FIRST
            denom = 1.0 - T * b
            if denom <= 1e-9 or a <= 0:
                continue
            nodes = [first_vert]
            h = first_vert
            overflow = False
            for _ in range(200000):
                hn = (h * (1.0 + T * b) + 2.0 * a * T) / denom
                if math.isnan(hn) or hn <= h or hn > H:
                    overflow = True
                    break
                nodes.append(hn)
                h = hn
            if len(nodes) < 2:
                continue
            last = nodes[-1]
            residual = H - last
            if LAST_MIN - 1e-6 <= residual <= LAST_MAX + 1e-6:
                res.append((bw, round(beta, 1), len(nodes) - 1, round(last, 3), round(residual, 3)))
    return res


for H in (100000, 115253):
    for mode in (False, True):
        r = solve(H, 15, 30, new_model=mode)
        widths = sorted({x[0] for x in r})
        tag = "NEW(cos/T)" if mode else "OLD"
        print(f"H={H} {tag}: solutions={len(r)} widths={widths}")
        for x in r:
            print("   ", x)
    print("-" * 60)
