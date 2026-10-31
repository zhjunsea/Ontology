# -*- coding: utf-8 -*-
"""H_0 计算（格构塔）：每一步结果保留 3 位小数——直接舍弃第 3 位之后（截断、不四舍五入）。

输入：底面 bw=25000、顶面 top=9800、高度 H=115253、斜撑角 beta=47.7°、dev=500、firstOff=120
口径：H_0 = dev1 + Δₘ（客户调研.txt 第11/13行，塔柱中心线端点）
截断定义：朝零方向丢弃第 3 位小数之后的位（ROUND_DOWN / math.trunc）。
"""
import math
from decimal import Decimal, ROUND_DOWN

def t3(x):
    return float(Decimal(str(x)).quantize(Decimal("0.001"), rounding=ROUND_DOWN))

BW, TOP, H, BETA, DEV, FIRST_OFF = 25000.0, 9800.0, 115253.0, 47.7, 500.0, 120.0

dx_half = t3((BW - TOP) / 2.0)
proj = t3(math.sqrt(2.0) * dx_half)
line_len = t3(math.sqrt(proj * proj + H * H))
cos_alpha = t3(dx_half / line_len)
alpha = t3(math.degrees(math.acos(cos_alpha)))
b = t3((TOP - BW) / (2.0 * H))

t = t3(math.tan(math.radians(BETA)))
disc = t3(1.0 - b * b * t * t)
eff = t3(t / math.sqrt(disc))

z1 = t3(FIRST_OFF / math.sqrt(1.0 + b * b))
delta = t3(DEV * eff / (1.0 + b * eff))
dev1 = t3(z1 + delta)

tan_comp = t3(math.tan(math.pi / 2.0 - math.radians(alpha)))
delta_m = t3(DEV * tan_comp)

h0 = t3(dev1 + delta_m)

print("=== 每步保留 3 位小数（截断、不四舍五入）===")
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
    ("[6] H_0=dev1+delta_m             ", h0),
]:
    print(f"  {k} = {v:.3f}")

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
print("\n全精度(不截断) H_0 = %.6f" % (dev1_f + dm_f))
print("逐步截断       H_0 = %.3f" % h0)
