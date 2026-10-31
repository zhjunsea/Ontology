import math

top = 9800.0
dev = 500.0
first_off = 120.0
last_min = 850.0
last_max = 950.0
b_lo, b_hi, step = 40.0, 50.0, 0.1


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
    return dict(nodes=nodes, last=last, residual=residual,
                feasible=feasible, overflow=overflow,
                panel=len(nodes) - 1, t=t, a=a, b=b, denom=denom)


def ecc_half(y, bw_mm, height_mm):
    center = bw_mm + (top - bw_mm) * (y / height_mm)
    return center / 2.0 - dev


def width_at(y, bw_mm, height_mm):
    return bw_mm + (top - bw_mm) * (y / height_mm)


def crossing(nodes, bw_mm, height_mm):
    res = []
    for k in range(len(nodes) - 1):
        hk, hk1 = nodes[k], nodes[k + 1]
        e1 = ecc_half(hk, bw_mm, height_mm)
        e2 = ecc_half(hk1, bw_mm, height_mm)
        denom = e1 + e2
        s = 0.5 if denom <= 1e-9 else e1 / denom
        res.append(hk + s * (hk1 - hk))
    return res


def r(v, d):
    f = 10 ** d
    return round(v * f) / f


H = 115253.0
BW = 25.0
BETA = 47.7

bw_mm = BW * 1000.0
r0 = solve(H, bw_mm, BETA)
print("=== 单点核验：H=%.0f  bw=%.0fm  beta=%.1f ===" % (H, BW, BETA))
if r0 is None:
    print("  该组合在算法下不成立（a<=0 或 denom<=0）")
else:
    print("  t=tan(beta)=%.6f  a=bw/2-dev=%.3f  b=%.8f  denom=%.6f" %
          (r0['t'], r0['a'], r0['b'], r0['denom']))
    print("  panelCount(节间数)=%d" % r0['panel'])
    print("  nodes(偏心线交点高度, mm)=", [r(v, 3) for v in r0['nodes']])
    print("  last(high_truss)=%.3f  residual=H-last=%.3f  feasible=%s overflow=%s" %
          (r0['last'], r0['residual'], r0['feasible'], r0['overflow']))
    cross = crossing(r0['nodes'], bw_mm, H)
    print("  crossingHeights(交叉点高度, mm)=", [r(v, 3) for v in cross])
    print("  layerCount(层数)=%d" % len(cross))
    print("  --- 各层横撑/横隔长度(mm) ---")
    struts, diaphs = [], []
    for y in cross:
        side = width_at(y, bw_mm, H)
        s_len = side - 2.0 * dev
        struts.append(r(s_len, 3))
        diaphs.append(r(s_len * math.sqrt(2), 3))
    print("  截面边长 widthAt(y)=", [r(width_at(y, bw_mm, H), 3) for y in cross])
    print("  strutLengths=", struts)
    print("  diaphragmLengths=", diaphs)

print()
print("=== 算法真实搜索：H=%.0f  bw=25 全角度(50→40, 步0.1) ===" % H)
any_feasible = False
for i in range(101):
    beta = round(b_hi - i * step, 1)
    res = solve(H, bw_mm, beta)
    if res and res['feasible']:
        print("  可行: beta=%.1f  panel=%d  last=%.3f  residual=%.3f" %
              (beta, res['panel'], r(res['last'], 3), r(res['residual'], 3)))
        any_feasible = True
if not any_feasible:
    print("  bw=25 在 H=%.0f 下，全角度搜索【无可行解】" % H)

# 顺带看 47.7 附近residual
print()
print("=== 47.7 附近 residual 扫描 ===")
for i in range(101):
    beta = round(b_hi - i * step, 1)
    res = solve(H, bw_mm, beta)
    if res and abs(beta - 47.7) < 1.0:
        print("  beta=%.1f residual=%.3f  panel=%d feasible=%s" %
              (beta, r(res['residual'], 3), res['panel'], res['feasible']))
