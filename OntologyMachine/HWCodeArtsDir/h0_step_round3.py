# -*- coding: utf-8 -*-
"""H_0 计算（格构塔）：每一步结果保留 3 位小数、四舍五入。

输入（客户锚点）：
  底面边长 bw = 25000 mm   顶面边长 top = 9800 mm
  高度 H = 115253 mm       斜撑与水平面锐角 beta = 47.7°
  水平横杆 dev = 500 mm    首杆垂距 firstOff = 120 mm
口径：H_0 = dev1 + Δₘ （客户调研.txt 第11/13行；rigid brace 另一端点在塔柱中心线上）
"""
import math
from decimal import Decimal, ROUND_HALF_UP

def r3(x):
    return float(Decimal(str(x)).quantize(Decimal("0.001"), rounding=ROUND_HALF_UP))

BW, TOP, H, BETA, DEV, FIRST_OFF = 25000.0, 9800.0, 115253.0, 47.7, 500.0, 120.0

rows = []

# [0] 收分几何
dx_half = r3((BW - TOP) / 2.0)
proj = r3(math.sqrt(2.0) * dx_half)
line_len = r3(math.sqrt(proj * proj + H * H))
cos_alpha = r3(dx_half / line_len)
alpha = r3(math.degrees(math.acos(cos_alpha)))
b = r3((TOP - BW) / (2.0 * H))

# [1] 有效斜率
t = r3(math.tan(math.radians(BETA)))
disc = r3(1.0 - b * b * t * t)
eff = r3(t / math.sqrt(disc))

# [2] 120mm → z1
z1 = r3(FIRST_OFF / math.sqrt(1.0 + b * b))

# [3] P120 → dev1 沿杆升高 Δ
delta = r3(DEV * eff / (1.0 + b * eff))

# [4] 首偏心节点
dev1 = r3(z1 + delta)

# [5] 偏 → 中心线 Δₘ
tan_comp = r3(math.tan(math.pi / 2.0 - math.radians(alpha)))
delta_m = r3(DEV * tan_comp)

# [6] H_0
h0 = r3(dev1 + delta_m)

print("=== 每步保留 3 位小数、四舍五入 ===")
for k, v in [
    ("[0] dx_half  = (bw-top)/2      ", dx_half),
    ("[0] sqrt2*dx_half             ", proj),
    ("[0] line_len = sqrt(proj^2+H^2)", line_len),
    ("[0] cos(alpha)=dx_half/line_len", cos_alpha),
    ("[0] alpha (deg)               ", alpha),
    ("[0] b=(top-bw)/(2H)           ", b),
    ("[1] t=tan(beta)               ", t),
    ("[1] 1-b^2 t^2                 ", disc),
    ("[1] eff=t/sqrt(1-b^2t^2)      ", eff),
    ("[2] z1=120/sqrt(1+b^2)        ", z1),
    ("[3] delta=dev*eff/(1+b*eff)   ", delta),
    ("[4] dev1=z1+delta             ", dev1),
    ("[5] tan(pi/2-alpha)           ", tan_comp),
    ("[5] delta_m=dev*tan(pi/2-a)   ", delta_m),
    ("[6] H_0=dev1+delta_m          ", h0),
]:
    print(f"  {k} = {v:.3f}")

# 全精度对照
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
h0_f = dev1_f + dm_f
print("\n全精度(不取整) H_0 = %.6f" % h0_f)
print("逐步取整    H_0 = %.3f" % h0)
