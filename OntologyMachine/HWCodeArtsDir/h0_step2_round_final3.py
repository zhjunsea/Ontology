# -*- coding: utf-8 -*-
"""H_0 计算（格构塔）：中间每步保留 2 位小数（四舍五入）；最终结果保留 3 位小数。

输入：底面 bw=25000、顶面 top=9800、高度 H=115253、斜撑角 beta=47.7°、dev=500、firstOff=120
口径：H_0 = dev1 + Δₘ（客户调研.txt 第11/13行，塔柱中心线端点）
取整：四舍五入（ROUND_HALF_UP）——中间 t2 到 2 位；最终 t3 到 3 位。
"""
import math
from decimal import Decimal, ROUND_HALF_UP

def t2(x):
    return float(Decimal(str(x)).quantize(Decimal("0.01"), rounding=ROUND_HALF_UP))

def t3(x):
    return float(Decimal(str(x)).quantize(Decimal("0.001"), rounding=ROUND_HALF_UP))

BW, TOP, H, BETA, DEV, FIRST_OFF = 25000.0, 9800.0, 115253.0, 47.7, 500.0, 120.0

dx_half = t2((BW - TOP) / 2.0)
proj = t2(math.sqrt(2.0) * dx_half)
line_len = t2(math.sqrt(proj * proj + H * H))
cos_alpha = t2(dx_half / line_len)
alpha = t2(math.degrees(math.acos(cos_alpha)))
b = t2((TOP - BW) / (2.0 * H))

t = t2(math.tan(math.radians(BETA)))
disc = t2(1.0 - b * b * t * t)
eff = t2(t / math.sqrt(disc))

z1 = t2(FIRST_OFF / math.sqrt(1.0 + b * b))
delta = t2(DEV * eff / (1.0 + b * eff))
dev1 = t2(z1 + delta)

tan_comp = t2(math.tan(math.pi / 2.0 - math.radians(alpha)))
delta_m = t2(DEV * tan_comp)

h0_raw = dev1 + delta_m
h0 = t3(h0_raw)

print("=== 中间每步 2 位小数（四舍五入）；最终 3 位小数 ===")
for k, v in [
    ("[0] dx_half  = (bw-top)/2        ", dx_half),
    ("[0] sqrt2*dx_half                ", proj),
    ("[0] line_len = sqrt(proj^2+H^2)  ", line_len),
    ("[0] cos(alpha)=dx_half/line_len  ", cos_alpha),
    ("[0] alpha (deg)                  ", alpha),
    ("[0] b=(top-bw)/(2H)              ", b),
    ("[1] t=tan(beta)                  ", t),
    ("[1] 1-b^2 t^2                    ", disc),
    ("[1] eff=t/sqrt(1-b^2t^2)         ", eff),
    ("[2] z1=120/sqrt(1+b^2)           ", z1),
    ("[3] delta=dev*eff/(1+b*eff)      ", delta),
    ("[4] dev1=z1+delta                ", dev1),
    ("[5] tan(pi/2-alpha)              ", tan_comp),
    ("[5] delta_m=dev*tan(pi/2-a)      ", delta_m),
]:
    print(f"  {k} = {v:.2f}")
print(f"  [6] dev1+delta_m (raw)          = {h0_raw}")
print(f"  [6] H_0 (最终 3 位四舍五入)      = {h0:.3f}")

d1 = (BW - TOP) / 2.0
ll = math.hypot(math.sqrt(2.0) * d1, H)
a_f = math.acos(d1 / ll)
b_f = (TOP - BW) / (2.0 * H)
tf = math.tan(math.radians(BETA))
eff_f = tf / math.sqrt(1 - b_f * b_f * tf * tf)
z1_f = FIRST_OFF / math.sqrt(1 + b_f * b_f)
dlt_f = DEV * eff_f / (1 + b_f * eff_f)
dev1_f = z1_f + dlt_f
dm_f = DEV * math.tan(math.pi / 2.0 - a_f)
print("\n对照：全精度(不取整) H_0 = %.6f" % (dev1_f + dm_f))
print("      上轮 中间2位逐字截断+最终3位 H_0 = 732.910")
print("      本轮 中间2位四舍五入+最终3位 H_0 = %.3f" % h0)
