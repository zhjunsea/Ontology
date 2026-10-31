# -*- coding: utf-8 -*-
"""算法等价性判定：以「斜腹杆真实三维倾角」为判据。
新程序声称 beta 是含 y 向收分的三维真实夹角（eff）；老程序用平面 alpha。
若某套程序自洽，则其输出的斜腹杆实际 atan(dz/水平距离) 应≈ beta(47.7°)。
另附节间竖直高差与理论值对比。"""
import math
import numpy as np
import verify_node_coords as V

DEV = 500.0
TOP = 9800.0


def diag_angles(nodes, nmap, quad_sign):
    """返回该象限所有「斜腹杆对角线」的真实三维倾角(度)。"""
    res = []
    for i in range(1, 10):
        for (c1, c2) in [("a", "b")]:
            pass
    # 直接按象限定义
    quads = [("I", "a", "b", "x"), ("II", "b", "c", "y"), ("III", "c", "d", "x"), ("IV", "d", "a", "y")]
    for qn, c1, c2, dd in quads:
        i = 1
        while f"X_{qn}{i}" in nmap:
            p1 = np.array(nodes[nmap[f"col_{c1}_dev_{dd}{i}"]])
            p2 = np.array(nodes[nmap[f"col_{c2}_dev_{dd}{i+1}"]])
            p3 = np.array(nodes[nmap[f"col_{c2}_dev_{dd}{i}"]])
            p4 = np.array(nodes[nmap[f"col_{c1}_dev_{dd}{i+1}"]])
            for a, b in [(p1, p2), (p3, p4)]:
                dz = abs(b[2] - a[2])
                dh = math.hypot(b[0] - a[0], b[1] - a[1])
                dx = abs(b[0] - a[0])
                res.append((math.degrees(math.atan2(dz, dh)),      # 真实三维角
                            math.degrees(math.atan2(dz, dx)),      # x-z 投影角
                            dz, dh))
            i += 1
    return res


def summarize(label, rows):
    ang3 = np.array([r[0] for r in rows])
    angx = np.array([r[1] for r in rows])
    print(f"  {label}: 条数={len(rows)}")
    print(f"    真实三维角: 均值={ang3.mean():.4f}°  min={ang3.min():.4f}°  max={ang3.max():.4f}°  std={ang3.std():.4f}°")
    print(f"    x-z 投影角 : 均值={angx.mean():.4f}°  min={angx.min():.4f}°  max={angx.max():.4f}°  std={angx.std():.4f}°")
    return ang3, angx


if __name__ == "__main__":
    # ---- 老程序（原样）----
    onodes, onmap, ometa = V.run_old()
    o_rows = diag_angles(onodes, onmap, None)
    print("########## 老程序（dis_top=9604.88, beta=47.7 作为平面角）##########")
    o3, ox = summarize("斜腹杆", o_rows)
    print(f"  期望 beta = 47.7°：三维角偏差 {o3.mean()-47.7:+.4f}°；投影角偏差 {ox.mean()-47.7:+.4f}°")

    # ---- 新程序（强制 beta=47.7）----
    beta, height, bw = 47.7, 116733.0, 25000.0
    t = math.tan(math.radians(beta))
    a = bw / 2.0 - DEV
    b = (TOP - bw) / (2.0 * height)
    eff = t / math.sqrt(1.0 - b * b * t * t)
    denom = 1.0 - b * eff
    firstV = 120.0 / math.sqrt(1.0 + b * b) + DEV * eff / (1.0 + b * eff)
    ecc = [firstV]
    h = firstV
    for _ in range(50):
        hn = (h * (1.0 + b * eff) + 2.0 * a * eff) / denom
        if hn <= h or hn > height + 950:
            break
        ecc.append(hn); h = hn
    highTruss = ecc[-1] + DEV * eff / denom
    nnodes, nnmap, nmeta = V.build_new(bw, height, ecc, highTruss)
    n_rows = diag_angles(nnodes, nnmap, None)
    print("\n########## 新程序（topMm=9800, 强制 beta=47.7, eff 三维角）##########")
    n3, nx = summarize("斜腹杆", n_rows)
    print(f"  期望 beta = 47.7°：三维角偏差 {n3.mean()-47.7:+.4f}°；投影角偏差 {nx.mean()-47.7:+.4f}°")

    print("\n########## 结论 ##########")
    print(f"  老程序：三维角={o3.mean():.4f}° (偏差 {o3.mean()-47.7:+.4f}°) | 投影角={ox.mean():.4f}° (偏差 {ox.mean()-47.7:+.4f}°)")
    print(f"  新程序：三维角={n3.mean():.4f}° (偏差 {n3.mean()-47.7:+.4f}°) | 投影角={nx.mean():.4f}° (偏差 {nx.mean()-47.7:+.4f}°)")
    print(f"  → 三维角最接近 beta 者：{'老程序' if abs(o3.mean()-47.7)<abs(n3.mean()-47.7) else '新程序'}")
    print(f"  → 投影角最接近 beta 者：{'老程序' if abs(ox.mean()-47.7)<abs(nx.mean()-47.7) else '新程序'}")
