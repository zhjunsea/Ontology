import math

top = 9800.0
dev = 500.0
first_off = 120.0
last_min, last_max = 850.0, 980.0
step = 0.1


def solve(height_mm, bw_mm, beta):
    t = math.tan(math.radians(beta))
    a = bw_mm / 2.0 - dev
    b = (top - bw_mm) / (2.0 * height_mm)
    denom = 1.0 - b * t
    if denom <= 1e-9 or a <= 0:
        return None
    nodes = [first_off]
    h = first_off
    overflow = False
    for _ in range(200000):
        h_next = (h * (1.0 + b * t) + 2.0 * a * t) / denom
        if math.isnan(h_next) or h_next <= h or h_next > height_mm:
            overflow = True
            break
        nodes.append(h_next)
        h = h_next
    if len(nodes) < 2:
        return None
    last = nodes[-1]
    residual = height_mm - last
    feasible = (last_min - 1e-6) <= residual <= (last_max + 1e-6)
    return dict(last=last, residual=residual, feasible=feasible,
                overflow=overflow, panel=len(nodes) - 1)


def scan(H, BW):
    bw_mm = BW * 1000.0
    print("=== H=%.0f  bw=%.0fm  基线[%.0f,%.0f] ===" % (H, BW, last_min, last_max))
    r = solve(H, bw_mm, 47.7)
    print("beta=47.7 -> residual=%.3f  panel=%d  feasible=%s overflow=%s" %
          (r['residual'], r['panel'], r['feasible'], r['overflow']))
    feas = []
    for i in range(101):
        beta = round(50.0 - i * step, 1)
        res = solve(H, bw_mm, beta)
        if res and res['feasible']:
            feas.append((beta, res['panel'], res['residual']))
    print("可行角度:", [("b=%.1f,p=%d,res=%.1f" % f) for f in feas] or "无")


for H in (115253.0, 100000.0):
    scan(H, 25.0)
    print()
