# -*- coding: utf-8 -*-
"""反推客户脚本 H_0=744.817（bw=25000,dis_top=9604.88,high_truss=116733,beta=47.7）。
枚举候选封闭式，寻找给出 dev_1(=H_0-δ)≈711.919 或 H_0≈744.817 的式子。"""
import math

dis_bott = 25000.0
dis_top = 9604.88
high_truss = 116733.0
H0_cust = 744.817
dev = 500.0
off = 120.0
beta = 47.7

pb = (dis_bott / 2, dis_bott / 2, 0.0)
pt = (dis_top / 2, dis_top / 2, high_truss)


def ang(p1, p2, p3):
    v1 = [p1[i] - p2[i] for i in range(3)]
    v2 = [p3[i] - p2[i] for i in range(3)]
    d = sum(v1[i] * v2[i] for i in range(3))
    n1 = math.sqrt(sum(c * c for c in v1))
    n2 = math.sqrt(sum(c * c for c in v2))
    return math.acos(max(-1, min(1, d / (n1 * n2))))


alpha = ang((dis_top / 2, dis_bott / 2, 0), pb, pt)
gama = ang((0, 0, 0), pb, pt)
L = math.dist(pb, pt)
delta = dev * math.tan(math.pi / 2 - alpha)
b = (9800.0 - dis_bott) / (2.0 * 115253.51)     # 9800 面收分斜率
t = math.tan(math.radians(beta))
eff = t / math.sqrt(1 - b * b * t * t)

print("alpha=%.6f  gama=%.6f  L=%.4f  delta=%.4f  b=%.7f  t=%.6f  eff=%.6f"
      % (math.degrees(alpha), math.degrees(gama), L, delta, b, t, eff))
print("目标 dev_1 = H_0 - delta = %.4f   (客户实测 711.92)" % (H0_cust - delta))
print("程序 z1=off/sqrt(1+b^2)=%.4f  z1+dev*eff/(1+b*eff)=%.4f" %
      (off / math.sqrt(1 + b * b), off / math.sqrt(1 + b * b) + dev * eff / (1 + b * eff)))
print()

z1 = off / math.sqrt(1 + b * b)
target_dev1 = H0_cust - delta
target_H0 = H0_cust

cands = {
    "z1_a=off/sqrt(1+b^2)": z1,
    "off*sin(alpha)": off * math.sin(alpha),
    "off*cos(pi/2-alpha)": off * math.cos(math.pi / 2 - alpha),
    "off/sin(gama)": off / math.sin(gama),
    "off*sin(gama)": off * math.sin(gama),
    "off/cos(alpha)": off / math.cos(alpha),
    "off*tan(alpha)": off * math.tan(alpha),
    "off/tan(alpha)": off / math.tan(alpha),
    "off*cos(gama)": off * math.cos(gama),
    "off/sqrt(1+b^2)": off / math.sqrt(1 + b * b),
}
print("== z1 候选 (目标 %.4f) ==" % z1)
for k, v in cands.items():
    print("  %-22s = %10.4f   Δ(dev1)=%+.4f" % (k, v, v - z1))

print()
print("== Δ 候选 (目标 %.4f) ==" % (target_dev1 - z1))
dcands = {
    "dev*eff": dev * eff,
    "dev*eff/(1+b*eff)": dev * eff / (1 + b * eff),
    "dev*eff/(1-b*eff)": dev * eff / (1 - b * eff),
    "dev*t": dev * t,
    "dev*t/(1+b*eff)": dev * t / (1 + b * eff),
    "dev*eff/(1+b*t)": dev * eff / (1 + b * t),
    "dev*eff*(1+b*eff)": dev * eff * (1 + b * eff),
    "dev/sin(gama)": dev / math.sin(gama),
    "dev/sin(alpha)": dev / math.sin(alpha),
    "dev*sin(alpha)/cos(beta)": dev * math.sin(alpha) / math.cos(math.radians(beta)),
    "dev*t*sin(gama)": dev * t * math.sin(gama),
    "dev*t*cos(gama)": dev * t * math.cos(gama),
    "dev/sin(pi/2-alpha)": dev / math.sin(math.pi / 2 - alpha),
}
for k, v in dcands.items():
    print("  %-26s = %10.4f   Δ(diff)=%+.4f" % (k, v, v - (target_dev1 - z1)))

print()
print("== H_0 候选 (目标 %.4f) ==" % target_H0)
hcands = {
    "z1+dev*eff": z1 + dev * eff,
    "z1+dev*eff/(1+b*eff)": z1 + dev * eff / (1 + b * eff),
    "off/sin(alpha)+dev*eff": off / math.sin(alpha) + dev * eff,
    "dev*eff/(1+b*eff)+z1": z1 + dev * eff / (1 + b * eff),
    "off/sqrt(1+b^2)+dev*eff/(1+b*eff)  [程序]": z1 + dev * eff / (1 + b * eff),
}
for k, v in hcands.items():
    print("  %-40s = %10.4f   Δ=%+.4f" % (k, v, v - target_H0))

print()
print("注：客户脚本内部 col_a1 高 z=(L0/L)*high_truss 且 L0=H_0/sin(gama) → 自洽，H_0 为其给定输入。")
L0 = H0_cust / math.sin(gama)
print("  由 H_0 推出的 L0=%.4f → z(col_a1)=%.4f (=H_0? %.4f)" % (L0, L0 / L * high_truss, H0_cust))
print("  由目标 dev_1=%.4f 反推 z1=dev1-Δ? 若Δ=dev*eff/(1+b*eff)=%.4f → z1=%.4f (程序 z1=%.4f)"
      % (target_dev1, dev * eff / (1 + b * eff), target_dev1 - dev * eff / (1 + b * eff), z1))
