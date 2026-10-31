# -*- coding: utf-8 -*-
"""受控实验：分离新老程序节点坐标差异的来源。
变量：(i) 顶宽 dis_top/topMm = 9604.88 vs 9800；(ii) 节点高度生成算法（老=塔柱分段迭代 / 新=ecc 解析递推）。"""
import importlib.util
import math
import numpy as np

OLD_SCRIPT = (r"D:\work\Ontology\OntologyMachine\OntologyFrameworkLatticeTower\design"
              r"\新铸件偏心_45度_optimized_加横撑横隔_204m_增加格构顶端节点(建总需求).py")


def load_old():
    spec = importlib.util.spec_from_file_location("old_script", OLD_SCRIPT)
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


def run_old(dis_bott, dis_top, high_truss, H_0, deviation, beta):
    m = load_old()
    pb1 = [dis_bott * 0.5, dis_bott * 0.5, 0]
    pt1 = [dis_top * 0.5, dis_top * 0.5, high_truss]
    alpha = m.cal_angle([dis_top * 0.5, dis_bott * 0.5, 0], pb1, pt1)
    gama = m.cal_angle([0, 0, 0], pb1, pt1)
    L = m.cal_length(pb1, pt1)
    g = m.LatticeTowerGenerator(H_0, alpha, gama, dis_bott, L, pb1, pt1, high_truss, deviation, beta, beta)
    dd = g.calculate_column_segments()
    g.generate_node_coordinates(dd)
    main_z, dev_z = [], []
    i = 0
    while f"col_a{i}" in g.node_map:
        main_z.append(g.nodes[g.node_map[f"col_a{i}"]][2]); i += 1
    i = 1
    while f"col_a_dev_y{i}" in g.node_map:
        dev_z.append(g.nodes[g.node_map[f"col_a_dev_y{i}"]][2]); i += 1
    return dict(alpha=math.degrees(alpha), gama=math.degrees(gama), L=L, n=g.n_nodes_per_col,
                main_z=main_z, dev_z=dev_z)


def diffs(seq):
    return [round(seq[i + 1] - seq[i], 3) for i in range(len(seq) - 1)]


if __name__ == "__main__":
    dis_bott, deviation, beta = 25000, 500, 47.7

    print("########## 老程序：原样（dis_top=9604.88, high_truss=116733, H_0=744.817）##########")
    o1 = run_old(dis_bott, 9604.88, 116733, 744.817, deviation, beta)
    print(f"  alpha={o1['alpha']:.4f} gama={o1['gama']:.4f} L={o1['L']:.3f} n={o1['n']}")
    print(f"  主节点 z   = {[round(x,3) for x in o1['main_z']]}")
    print(f"  主节点高差 = {diffs(o1['main_z'])}")
    print(f"  偏心节点 z = {[round(x,3) for x in o1['dev_z']]}")

    print("\n########## 老程序：仅顶宽改 9800（对齐新程序 topMm），其余不变 ##########")
    try:
        o2 = run_old(dis_bott, 9800.0, 116733, 744.817, deviation, beta)
        print(f"  alpha={o2['alpha']:.4f} gama={o2['gama']:.4f} L={o2['L']:.3f} n={o2['n']}")
        print(f"  主节点 z   = {[round(x,3) for x in o2['main_z']]}")
        print(f"  主节点高差 = {diffs(o2['main_z'])}")
        print(f"  偏心节点 z = {[round(x,3) for x in o2['dev_z']]}")
    except Exception as e:
        print(f"  >>> 老程序在 dis_top=9800 时自身崩溃：{type(e).__name__}: {e}")
        print("  >>> 说明老程序首段迭代对该顶宽不收敛（脚本鲁棒性缺陷），无法直接对齐顶宽复算。")
        o2 = None

    print("\n########## 新程序：topMm=9800, height=116733, 强制 beta=47.7（绕过可行性）##########")
    TOP_MM, DEV_MM, FIRST_OFF, height, bwMm = 9800.0, 500.0, 120.0, 116733.0, 25000.0
    t = math.tan(math.radians(beta))
    a = bwMm / 2.0 - DEV_MM
    b = (TOP_MM - bwMm) / (2.0 * height)
    disc = 1.0 - b * b * t * t
    eff = t / math.sqrt(disc)
    denom = 1.0 - b * eff
    firstVert = FIRST_OFF / math.sqrt(1.0 + b * b) + DEV_MM * eff / (1.0 + b * eff)
    ecc = [firstVert]
    h = firstVert
    for _ in range(50):
        hNext = (h * (1.0 + b * eff) + 2.0 * a * eff) / denom
        if hNext <= h or hNext > height + 950:
            break
        ecc.append(hNext); h = hNext
    print(f"  b={b:.6f} eff={eff:.6f} denom={denom:.6f} firstVert={firstVert:.3f}")
    print(f"  ecc(偏心节点 z，含起点) = {[round(x,3) for x in ecc]}")
    print(f"  ecc 高差                = {diffs(ecc)}")

    pb1 = [bwMm/2, bwMm/2, 0]; pt1 = [TOP_MM/2, TOP_MM/2, height]
    v1 = np.array([TOP_MM/2, bwMm/2, 0.0]) - np.array(pb1)
    v2 = np.array(pt1) - np.array(pb1)
    alpha = math.acos(np.dot(v1, v2)/(np.linalg.norm(v1)*np.linalg.norm(v2)))
    dlt = DEV_MM * math.tan(math.pi/2 - alpha)
    main_z = [0.0] + [e + dlt for e in ecc]
    print(f"\n  新程序 alpha={math.degrees(alpha):.4f} delta={dlt:.4f}")
    print(f"  新程序主节点 z   = {[round(x,3) for x in main_z]}")
    print(f"  新程序主节点高差 = {diffs(main_z)}")

    print("\n########## 关键对照：偏心节点相邻高差（老原样 vs 新强制47.7）##########")
    print(f"  老 偏心节点高差 = {diffs(o1['dev_z'])}")
    print(f"  新 偏心节点高差 = {diffs(ecc)}")
