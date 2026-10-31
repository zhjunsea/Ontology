# -*- coding: utf-8 -*-
"""验证新程序递推关系：逐段提取斜腹杆端点 Δx/Δy/Δz，
对照 eff=tanβ/√(1-b²tan²β) 与 tanβ，判断「三维角=β」是否被精确构造。"""
import math
import numpy as np
import verify_node_coords as V

DEV, TOP, BETA, HEIGHT, BW = 500.0, 9800.0, 47.7, 116733.0, 25000.0

t = math.tan(math.radians(BETA))
a = BW / 2.0 - DEV
b = (TOP - BW) / (2.0 * HEIGHT)
eff = t / math.sqrt(1.0 - b * b * t * t)
denom = 1.0 - b * eff
firstV = 120.0 / math.sqrt(1.0 + b * b) + DEV * eff / (1.0 + b * eff)
ecc = [firstV]
h = firstV
for _ in range(50):
    hn = (h * (1.0 + b * eff) + 2.0 * a * eff) / denom
    if hn <= h or hn > HEIGHT + 950:
        break
    ecc.append(hn); h = hn
highTruss = ecc[-1] + DEV * eff / denom
nnodes, nnmap, _ = V.build_new(BW, HEIGHT, ecc, highTruss)

print(f"beta={BETA}°  tanβ={t:.6f}  eff={eff:.6f}  b={b:.6f}  denom={denom:.6f}")
print(f"1/sqrt(1+b^2)={1/math.sqrt(1+b*b):.6f}\n")

print("逐段（象限 I，dev_x 对角线 a{i}->b{i+1}）：")
print(f"{'i':>2} {'dx':>12} {'dy':>10} {'dz':>12} {'dz/dx':>10} {'dz/hyp':>10} {'3D角度':>9}")
ratios_dx, ratios_hyp = [], []
i = 1
while f"X_I{i}" in nnmap:
    A = np.array(nnodes[nnmap[f"col_a_dev_x{i}"]])
    B = np.array(nnodes[nnmap[f"col_b_dev_x{i+1}"]])
    dx, dy, dz = abs(B[0]-A[0]), abs(B[1]-A[1]), abs(B[2]-A[2])
    rdx = dz/dx
    rhyp = dz/math.hypot(dx, dy)
    ratios_dx.append(rdx); ratios_hyp.append(rhyp)
    print(f"{i:>2} {dx:>12.3f} {dy:>10.3f} {dz:>12.3f} {rdx:>10.6f} {rhyp:>10.6f} {math.degrees(math.atan2(dz, math.hypot(dx,dy))):>9.5f}")
    i += 1

print(f"\n  dz/dx  均值 = {np.mean(ratios_dx):.6f}   （eff={eff:.6f}，tanβ={t:.6f}）")
print(f"  dz/hyp 均值 = {np.mean(ratios_hyp):.6f}  → 3D角均值 = {math.degrees(math.atan(np.mean(ratios_hyp))):.5f}°（beta={BETA}°）")
print(f"  结论：dz/hyp ∝ tan(3D角)，若≈tanβ 则斜腹杆真实三维角=beta。")
