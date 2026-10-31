# -*- coding: utf-8 -*-
"""H_0 = dev_1 + Δₘ 的精确几何推导（只读核验，不改任何客户/程序资产）。

输入（客户锚点）：
  塔底塔柱中心距 bw  = 25000 mm
  塔顶面塔柱中心距 top =  9800 mm
  顶面所在高度     H  = 115253 mm   （用户口语“塔顶 9800”指顶面宽；H 为输入的塔架面高）
  斜腹杆与水平面锐角 β = 47.7°
  偏心水平横杆     dev = 500 mm
  首杆垂距约束  firstOff = 120 mm

推导链（与源码/客户脚本逐行对应，见文末引用）：
  0. 收分几何  →  b, α
  1. 有效斜率  →  eff
  2. 120mm     →  z₁   = firstOff/√(1+b²)
  3. 沿杆升高  →  Δ    = dev·eff/(1+b·eff)
  4. 首偏心节点 →  dev₁ = z₁ + Δ
  5. 偏 → 中心线 →  Δₘ  = dev·tan(π/2−α)
  6.                H_0  = dev₁ + Δₘ
"""
import math

# ---- 输入 ----
BW = 25000.0
TOP = 9800.0
H = 115253.0
BETA_DEG = 47.7
DEV = 500.0
FIRST_OFF = 120.0

# ---- 0. 收分几何（塔柱中心线）----
dx_half = (BW - TOP) / 2.0                      # 半收分量 7600
line_len = math.hypot(math.sqrt(2.0) * dx_half, H)   # 中心线方向长
cos_alpha = dx_half / line_len
alpha = math.acos(cos_alpha)                    # 与 −x 轴夹角（近竖直）
sin_alpha = math.sin(alpha)
b = (TOP - BW) / (2.0 * H)                      # 半径随高度斜率 dx/dz（负=向上收窄）

# ---- 1. eff（斜腹杆真实三维角 → 立面 dz/dx 斜率）----
t = math.tan(math.radians(BETA_DEG))
disc = 1.0 - b * b * t * t
eff = t / math.sqrt(disc)

# ---- 2. 120mm → P120 竖直高度 z1 ----
z1 = FIRST_OFF / math.sqrt(1.0 + b * b)

# ---- 3. P120 → dev1 沿杆竖直升高 Δ ----
delta = DEV * eff / (1.0 + b * eff)

# ---- 4. 首偏心节点 ----
dev1 = z1 + delta

# ---- 5. 偏心节点 → 中心线主节点 竖直分量 Δₘ ----
delta_m = DEV * math.tan(math.pi / 2.0 - alpha)

# ---- 6. H_0 ----
h0 = dev1 + delta_m

print(f"bw={BW:.0f} top={TOP:.0f} H={H:.0f} beta={BETA_DEG} dev={DEV:.0f} firstOff={FIRST_OFF:.0f}")
print(f"[0] dx_half       = {dx_half:.6f}")
print(f"[0] line_len      = {line_len:.6f}")
print(f"[0] cos(alpha)    = {cos_alpha:.10f}")
print(f"[0] alpha         = {math.degrees(alpha):.6f} deg")
print(f"[0] b             = {b:.10f}")
print(f"[1] t=tan(beta)   = {t:.8f}")
print(f"[1] sqrt(1-b^2t^2)= {math.sqrt(disc):.10f}")
print(f"[1] eff           = {eff:.10f}")
print(f"[2] sqrt(1+b^2)   = {math.sqrt(1.0+b*b):.10f}")
print(f"[2] z1            = {z1:.6f}")
print(f"[3] delta         = {delta:.6f}")
print(f"[4] dev1          = {dev1:.6f}")
print(f"[5] tan(pi/2-a)   = {math.tan(math.pi/2.0-alpha):.10f}")
print(f"[5] delta_m       = {delta_m:.6f}")
print(f"[6] H_0           = {h0:.6f}")
print()
print(f"对照：客户脚本 H_0=744.817 (dev1=711.9177, Δₘ=32.899)")
print(f"      程序现值 dev1={dev1:.4f}  H_0={h0:.4f}  Δₘ={delta_m:.4f}")
print(f"      程序−客户 H_0 残差 = {h0-744.817:.4f} mm")
