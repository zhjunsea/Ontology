import math

H = 115253.0     # 9800 顶面高度 (mm)
TOP = 9800.0
DEV = 500.0      # 偏心线到同侧塔柱中心线的水平距离
OFF = 120.0      # 首杆垂线约束 (mm)


def calc(bw, beta_deg):
    b = (TOP - bw) / (2.0 * H)                 # 半宽随高度的斜率 dx_c/dz
    t = math.tan(math.radians(beta_deg))
    eff = t / math.sqrt(1.0 - b * b * t * t)    # 斜腹杆立面斜率 dz/dx
    z120 = OFF / math.sqrt(1.0 + b * b)         # 120 约束点 P120 的竖直高度（塔柱中心线上）
    d_naive = DEV * eff                          # 只取 500*eff（不含两斜线修正）
    d_plus = DEV * eff / (1.0 + b * eff)         # 几何严格：内向上 → 1+b*eff
    d_minus = DEV * eff / (1.0 - b * eff)        # 几何严格：外向上 → 1-b*eff
    return dict(b=b, eff=eff, z120=z120,
                h0_naive=z120 + d_naive,
                h0_plus=z120 + d_plus,
                h0_minus=z120 + d_minus,
                d_naive=d_naive, d_plus=d_plus, d_minus=d_minus)


print(f"H={H}  TOP={TOP}  DEV={DEV}  OFF={OFF}")
print(f"{'bw':>4} {'beta':>6} {'b':>9} {'eff':>8} {'z120':>9} "
      f"{'d_naive':>9} {'H0_naive':>10} {'d_plus':>9} {'H0_plus':>10} {'d_minus':>9} {'H0_minus':>10}")
for bw, beta in [(25, 40.5), (25, 47.7), (16, 48.0), (16, 45.0), (30, 45.0), (21, 45.0)]:
    r = calc(bw * 1000.0, beta)
    print(f"{bw:>4} {beta:>6} {r['b']:>9.5f} {r['eff']:>8.4f} {r['z120']:>9.3f} "
          f"{r['d_naive']:>9.3f} {r['h0_naive']:>10.3f} {r['d_plus']:>9.3f} {r['h0_plus']:>10.3f} "
          f"{r['d_minus']:>9.3f} {r['h0_minus']:>10.3f}")

print()
print("参考: 现程序 bw=25 输出 H_0_mm = 152.639")
print("参考: 客户脚本 H_0 = 744.817 (bw=25000, beta=47.7)")
