# -*- coding: utf-8 -*-
"""探测：客户 CAD 量出 744.559 是否可由「精度（小数取整）」解释。

扫描「中间每步保留 n 位小数」×「四舍五入 / 截断」×「最终展示位数」，
看是否有任一取整口径能命中/逼近 744.559。
"""
import math
from decimal import Decimal, ROUND_HALF_UP, ROUND_DOWN

BW, TOP, H, BETA, DEV, FIRST_OFF = 25000.0, 9800.0, 115253.0, 47.7, 500.0, 120.0
CAD = 744.559

def q(x, nd, mode):
    if x is None or (isinstance(x, float) and math.isnan(x)):
        return x
    d = Decimal(1).scaleb(-nd)  # 10^-nd
    return float(Decimal(str(x)).quantize(d, rounding=(ROUND_HALF_UP if mode == 'round' else ROUND_DOWN)))

def chain(n, mode):
    a = q((BW - TOP) / 2.0, n, mode)
    proj = q(math.sqrt(2.0) * a, n, mode)
    line = q(math.sqrt(proj * proj + H * H), n, mode)
    ca = q(a / line, n, mode)
    al = q(math.degrees(math.acos(ca)), n, mode)
    b = q((TOP - BW) / (2.0 * H), n, mode)
    t = q(math.tan(math.radians(BETA)), n, mode)
    disc = q(1.0 - b * b * t * t, n, mode)
    eff = q(t / math.sqrt(disc), n, mode)
    z1 = q(FIRST_OFF / math.sqrt(1.0 + b * b), n, mode)
    delta = q(DEV * eff / (1.0 + b * eff), n, mode)
    dev1 = q(z1 + delta, n, mode)
    tc = q(math.tan(math.pi / 2.0 - math.radians(al)), n, mode)
    dm = q(DEV * tc, n, mode)
    return q(dev1 + dm, n, mode), (a, proj, line, ca, al, b, t, disc, eff, z1, delta, dev1, tc, dm)

print("=== 全精度基准 ===")
# exact
a = (BW - TOP) / 2.0
ll = math.hypot(math.sqrt(2.0) * a, H)
af = math.acos(a / ll)
bf = (TOP - BW) / (2.0 * H)
tf = math.tan(math.radians(BETA))
efff = tf / math.sqrt(1 - bf * bf * tf * tf)
z1f = FIRST_OFF / math.sqrt(1 + bf * bf)
dltf = DEV * efff / (1 + bf * efff)
dev1f = z1f + dltf
dmf = DEV * math.tan(math.pi / 2.0 - af)
h0f = dev1f + dmf
print(f"  全精度(不取整) H_0 = {h0f:.6f}")
print(f"  客户 CAD          = {CAD:.3f}   差 = {h0f - CAD:+.3f} mm\n")

print("=== 扫描：中间每步 n 位小数 ===")
print(f"{'n':>2} {'四舍五入':>12} {'与CAD差':>9}   {'截断':>12} {'与CAD差':>9}")
for n in range(1, 7):
    hr, _ = chain(n, 'round')
    ht, _ = chain(n, 'trunc')
    print(f"{n:>2} {hr:>12.3f} {hr-CAD:>+9.3f}   {ht:>12.3f} {ht-CAD:>+9.3f}")

print("\n=== 参考：既有各口径 ===")
for tag, v in [("全精度", h0f), ("3位/四舍五入", 746.958), ("3位/截断", 745.180),
               ("2位截断+末3位", 732.910), ("2位四舍五入+末3位", 756.470)]:
    print(f"  {tag:>16} = {v:>10.3f}   与CAD差 {v-CAD:+.3f}")

print(f"\n结论：CAD 744.559 与程序全精度 {h0f:.3f} 差 {h0f-CAD:.3f} mm；"
      f"小数取整在 3 位/2 位下可动 0.001~{abs(756.470-732.910):.1f}mm 量级，")
print("      但无任一纯取整口径命中 744.559 ⇒ 不像『精度（取整）』问题。")
