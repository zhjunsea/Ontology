"""原型：用本体求解器几何推算全部节点 + 按样例编号/单元顺序生成。

几何驱动 = 本体求解器（新口径）：
  height(=9800面高 H)、top=9800、bw、beta、dev=500；
  偏心节点高度 E = 求解器 nodeHeights；末杆 ● = E[-1]、highTruss = z*。
编号/结构 = 参照 02_...xlsx 样例 + 客户脚本生成顺序。

★16：诊断/原型脚本放 HWCodeArtsDir，不改任何客户资产。
"""
import math
import numpy as np

DEV = 500.0
TOPW = 9800.0
LASTMIN, LASTMAX = 850.0, 950.0
BETA_HI, BETA_LO, BETA_STEP = 50.0, 40.0, 0.1


def compute_solutions(H, bw_lo, bw_hi):
    out = []
    for bw in range(bw_lo, bw_hi + 1):
        bwmm = bw * 1000.0
        for i in range(int(round((BETA_HI - BETA_LO) / BETA_STEP)) + 1):
            beta = BETA_HI - i * BETA_STEP
            t = math.tan(math.radians(beta))
            a = bwmm / 2.0 - DEV
            b = (TOPW - bwmm) / (2.0 * H)
            disc = 1.0 - b * b * t * t
            if disc <= 1e-12:
                continue
            eff = t / math.sqrt(disc)
            den = 1.0 - b * eff
            if den <= 1e-9 or a <= 0:
                continue
            first = 120.0 / math.sqrt(1.0 + b * b)
            nodes = [first]
            h = first
            ceil = H + LASTMAX
            for _ in range(200000):
                hn = (h * (1.0 + b * eff) + 2.0 * a * eff) / den
                if math.isnan(hn) or hn <= h or hn > ceil:
                    break
                nodes.append(hn)
                h = hn
            if len(nodes) < 2:
                continue
            last = nodes[-1]
            residual = last - H
            if residual < LASTMIN - 1e-6 or residual > LASTMAX + 1e-6:
                continue
            cr = crossing_heights(nodes, bwmm, H)
            out.append(dict(bottomWidthM=bw, bottomWidthMm=bwmm, betaDeg=round(beta, 1),
                            nodeHeights=nodes, crossingHeights=cr,
                            panelCount=len(nodes) - 1,
                            highTrussMm=last + DEV * eff / den))
    return out


def crossing_heights(nodes, bwmm, H):
    res = []
    for k in range(len(nodes) - 1):
        hk, hk1 = nodes[k], nodes[k + 1]
        e1 = ecc_half(hk, bwmm, H)
        e2 = ecc_half(hk1, bwmm, H)
        d = e1 + e2
        s = 0.5 if d <= 1e-9 else e1 / d
        res.append(hk + s * (hk1 - hk))
    return res


def width_at(z, bwmm, H):
    return bwmm + (TOPW - bwmm) * (z / H)


def ecc_half(z, bwmm, H):
    return width_at(z, bwmm, H) / 2.0 - DEV


def cal_angle(p1, p2, p3):
    v1 = np.array(p1) - np.array(p2)
    v2 = np.array(p3) - np.array(p2)
    c = np.dot(v1, v2) / (np.linalg.norm(v1) * np.linalg.norm(v2))
    return math.acos(max(-1.0, min(1.0, c)))


def midpoint(p1, p2, p3, p4):
    A, B, C, D = map(np.array, [p1, p3, p2, p4])
    AB, CD, AC = B - A, D - C, C - A
    cp = np.cross(AB, CD)
    if np.linalg.norm(cp) == 0:
        return ((A + B + C + D) / 4).tolist()
    t = np.dot(np.cross(AC, CD), cp) / np.dot(cp, cp)
    return (A + t * AB).tolist()


def generate(sol, H):
    bwmm = sol["bottomWidthMm"]
    E = sol["nodeHeights"]
    hi = sol["highTrussMm"]
    k = len(E)
    n = k + 2
    pb1 = np.array([bwmm / 2, bwmm / 2, 0.0])
    topw_at_hi = width_at(hi, bwmm, H)
    pt1 = np.array([topw_at_hi / 2, topw_at_hi / 2, hi])
    alpha = cal_angle([TOPW / 2, bwmm / 2, 0.0], pb1, pt1)
    delta = DEV * math.tan(math.pi / 2 - alpha)

    def half(z):
        return width_at(z, bwmm, H) / 2.0

    nodes, node_map, elements = {}, {}, []
    cur = 1
    signs = {"a": (1, 1), "b": (-1, 1), "c": (-1, -1), "d": (1, -1)}
    mz = [0.0] + [E[i] + delta for i in range(k)] + [hi]
    assert len(mz) == n, (len(mz), n)
    for cn in ["a", "b", "c", "d"]:
        sx, sy = signs[cn]
        for i in range(n):
            nodes[cur] = [half(mz[i]) * sx, half(mz[i]) * sy, mz[i]]
            node_map[f"col_{cn}{i}"] = cur
            cur += 1
    for cn in ["a", "b", "c", "d"]:
        sx, sy = signs[cn]
        for i in range(1, n - 1):
            xb, yb, zb = nodes[node_map[f"col_{cn}{i}"]]
            nodes[cur] = [xb, yb - sy * DEV / math.cos(math.pi / 2 - alpha),
                          zb - DEV * math.tan(math.pi / 2 - alpha)]
            node_map[f"col_{cn}_dev_y{i}"] = cur
            cur += 1
            nodes[cur] = [xb - sx * DEV / math.cos(math.pi / 2 - alpha), yb,
                          zb - DEV * math.tan(math.pi / 2 - alpha)]
            node_map[f"col_{cn}_dev_x{i}"] = cur
            cur += 1
    quad = [("I", "a", "b", "x"), ("II", "b", "c", "y"), ("III", "c", "d", "x"), ("IV", "d", "a", "y")]
    for q, c1, c2, dd in quad:
        for i in range(1, n - 2):
            nm = [f"col_{c1}_dev_{dd}{i}", f"col_{c2}_dev_{dd}{i}",
                  f"col_{c2}_dev_{dd}{i+1}", f"col_{c1}_dev_{dd}{i+1}"]
            if all(x in node_map for x in nm):
                nodes[cur] = midpoint(nodes[node_map[nm[0]]], nodes[node_map[nm[1]]],
                                      nodes[node_map[nm[2]]], nodes[node_map[nm[3]]])
                node_map[f"X_{q}{i}"] = cur
                cur += 1
    top_nodes = [f"col_{c}{n-1}" for c in "abcd"]
    xs = [nodes[node_map[x]][0] for x in top_nodes]
    ys = [nodes[node_map[x]][1] for x in top_nodes]
    zs = [nodes[node_map[x]][2] for x in top_nodes]
    nodes[cur] = [sum(xs) / 4, sum(ys) / 4, sum(zs) / 4]
    node_map["top_center"] = cur
    cur += 1

    eid = 1
    for cn in "abcd":
        for i in range(n - 1):
            elements.append([eid, node_map[f"col_{cn}{i}"], node_map[f"col_{cn}{i+1}"], "950x7.5", "塔柱"]); eid += 1
    for cn in "abcd":
        for i in range(1, n - 1):
            bn = node_map[f"col_{cn}{i}"]
            for dd in ["y", "x"]:
                elements.append([eid, bn, node_map[f"col_{cn}_dev_{dd}{i}"], "1000x30", "刚性连接杆"]); eid += 1
    layer_mid = {}
    for i in range(2, n - 1):
        pairs = [(f"col_a_dev_x{i}", f"col_b_dev_x{i}"), (f"col_b_dev_y{i}", f"col_c_dev_y{i}"),
                 (f"col_c_dev_x{i}", f"col_d_dev_x{i}"), (f"col_d_dev_y{i}", f"col_a_dev_y{i}")]
        mids = []
        for n1, n2 in pairs:
            if n1 in node_map and n2 in node_map:
                mid = ((np.array(nodes[node_map[n1]]) + np.array(nodes[node_map[n2]])) / 2.0).tolist()
                nodes[cur] = mid
                nmn = f"hc_mid_{i}_{n1.split('_')[1]}_{n2.split('_')[1]}"
                node_map[nmn] = cur
                mid_node_id = cur
                mids.append(nmn)
                cur += 1
                elements.append([eid, node_map[n1], mid_node_id, "200x7", "横撑"]); eid += 1
                elements.append([eid, mid_node_id, node_map[n2], "200x7", "横撑"]); eid += 1
        if len(mids) == 4:
            layer_mid[i] = mids
    for i in range(1, n - 1):
        if i in layer_mid:
            m = layer_mid[i]
            for a, b in [(m[0], m[1]), (m[1], m[2]), (m[2], m[3]), (m[3], m[0])]:
                if a in node_map and b in node_map:
                    elements.append([eid, node_map[a], node_map[b], "150x4", "横隔"]); eid += 1
    brace_sec = ["300x10"] * 10 + ["520x12.5"] * 7
    for q, c1, c2, dd in quad:
        j = 0
        for i in range(1, n - 2):
            ctr = node_map[f"X_{q}{i}"]
            sec = brace_sec[min(j, len(brace_sec) - 1)]
            for nm in [f"col_{c1}_dev_{dd}{i}", f"col_{c1}_dev_{dd}{i+1}",
                       f"col_{c2}_dev_{dd}{i}", f"col_{c2}_dev_{dd}{i+1}"]:
                if nm in node_map:
                    elements.append([eid, ctr, node_map[nm], sec, "斜撑"]); eid += 1
            j += 1
    for cn in "abcd":
        nm = f"col_{cn}{n-1}"
        if nm in node_map:
            elements.append([eid, node_map[nm], node_map["top_center"], "1300x10", "刚性杆"]); eid += 1
    return nodes, elements, dict(alpha=math.degrees(alpha), delta=delta, n=n, topw_at_hi=topw_at_hi)


if __name__ == "__main__":
    for H in (100000.0,):
        sols = compute_solutions(H, 15, 30)
        print(f"H={H}: 可行解 {len(sols)} 条, 底宽集合 {sorted({s['bottomWidthM'] for s in sols})}")
        sol = sols[0]
        nodes, elements, meta = generate(sol, H)
        print("样本解:", {k: (round(v, 3) if isinstance(v, float) else v) for k, v in sol.items() if k != 'nodeHeights' and k != 'crossingHeights'})
        print("  nodeHeights:", [round(x, 2) for x in sol['nodeHeights']])
        print("  crossingHeights:", [round(x, 2) for x in sol['crossingHeights']])
        print("  meta:", {k: (round(v, 4) if isinstance(v, float) else v) for k, v in meta.items()})
        print(f"  节点数={len(nodes)} 单元数={len(elements)}  (客户脚本样例:161/300)")
        import collections
        print("  单元类别:", dict(collections.Counter(e[4] for e in elements)))
