# -*- coding: utf-8 -*-
"""按 (bw,H,top,dev,beta,firstOff) 参数化重推客户脚本的正弦分段模型，
并与客户脚本实测节点序列对照（验收锚点 bw=25/beta=47.7 → ●≈116220.16）。"""
import math

bw = 25000.0
H = 115253.51
top = 9800.0
dev = 500.0
firstOff = 120.0
beta = 47.7

# 客户脚本实测序列（dev 节点 z）
CUST = [711.92, 25157.37, 46305.84, 64602.0, 80430.54, 94124.27, 105971.11, 116220.16]

dx = (bw - top) / 2.0
den = math.sqrt(2.0 * dx * dx + H * H)   # 柱中心线长度（按 9800 面方向）
sinphi = H / den                          # 柱中心线与水平面夹角 phi
cosg = math.sqrt(2.0) * dx / den          # angle gama (at point_bott_1)
sing = math.sqrt(1.0 - cosg * cosg)
alpha = math.acos(dx / den)               # 与 -x 轴夹角
b = (top - bw) / (2.0 * H)

print("alpha=%.4f  phi=%.4f  gama=%.4f  b=%.6f" %
      (math.degrees(alpha), math.degrees(math.asin(sinphi)), math.degrees(math.acos(cosg)), b))


def seq(dev1):
    z1 = firstOff / math.sqrt(1.0 + b * b)
    eff = math.tan(math.radians(beta)) / math.sqrt(1.0 - b * b * math.tan(math.radians(beta)) ** 2)
    Delta = dev * eff / (1.0 + b * eff)
    # anchor variants
    dev1_auto = z1 + Delta
    e1 = dev1 if dev1 is not None else dev1_auto
    delta_m = dev * math.tan(math.pi / 2.0 - alpha)
    H0 = e1 + delta_m
    L0 = H0 / sing
    disx0 = (bw - 2.0 * dev / math.sin(alpha)) - 2.0 * (L0 - dev / math.tan(alpha)) * math.cos(alpha)
    r = 1.0 - 2.0 * math.cos(alpha) * math.sin(math.radians(beta)) / math.sin(alpha + math.radians(beta))
    g1 = disx0 * math.sin(math.radians(beta)) * sinphi / math.sin(alpha + math.radians(beta))
    out = [e1]
    k = 1
    while len(out) < 40:
        e = e1 + g1 * (1.0 - r ** k) / (1.0 - r)
        out.append(e)
        if e > H + 980.0:
            break
        k += 1
    return out, dict(z1=z1, eff=eff, Delta=Delta, dev1=dev1_auto, delta_m=delta_m, H0=H0,
                     L0=L0, disx0=disx0, r=r, g1=g1)


for label, dev1 in [("客户实测锚 dev1=711.92", 711.92), ("程序式锚 z1+Δ", None)]:
    s, d = seq(dev1)
    print("\n=== %s ===" % label)
    print("  z1=%.2f eff=%.5f Delta=%.2f dev1_auto=%.2f H0=%.2f L0=%.2f disx0=%.2f r=%.6f g1=%.2f"
          % (d['z1'], d['eff'], d['Delta'], d['dev1'], d['H0'], d['L0'], d['disx0'], d['r'], d['g1']))
    print("  生成序列:", [round(x, 2) for x in s[:8]])
    print("  客户序列:", CUST)
    # 与客户序列对齐比较（取前 8）
    if len(s) >= 8:
        diff = [round(s[i] - CUST[i], 2) for i in range(8)]
        print("  逐点差:", diff)
