# -*- coding: utf-8 -*-
"""节点坐标核验：老程序（客户脚本）vs 新程序（本体求解器逐节点）。
算例：底面边长 bw=25m、beta=47.7°，塔高约 116733mm。
所有坐标均为「未旋转方阵系」(a=+,+/b=-,+/c=-,-/d=+,-)，与 Java GeometryModel 同系。"""
import importlib.util
import math
import numpy as np

OLD_SCRIPT = (r"D:\work\Ontology\OntologyMachine\OntologyFrameworkLatticeTower\design"
              r"\新铸件偏心_45度_optimized_加横撑横隔_204m_增加格构顶端节点(建总需求).py")

# ---- 本体常量（latticetower-all.owl）----
TOP_MM = 9800.0
DEV_MM = 500.0
FIRST_OFF = 120.0
LAST_MIN, LAST_MAX = 850.0, 950.0


def load_old():
    spec = importlib.util.spec_from_file_location("old_script", OLD_SCRIPT)
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


def run_old():
    m = load_old()
    dis_bott, dis_top, high_truss, H_0, deviation, beta = 25000, 9604.88, 116733, 744.817, 500, 47.7
    pb1 = [dis_bott * 0.5, dis_bott * 0.5, 0]
    pt1 = [dis_top * 0.5, dis_top * 0.5, high_truss]
    alpha = m.cal_angle([dis_top * 0.5, dis_bott * 0.5, 0], pb1, pt1)
    gama = m.cal_angle([0, 0, 0], pb1, pt1)
    L = m.cal_length(pb1, pt1)
    g = m.LatticeTowerGenerator(H_0, alpha, gama, dis_bott, L, pb1, pt1, high_truss, deviation, beta, beta)
    dd = g.calculate_column_segments()
    g.generate_node_coordinates(dd)
    meta = dict(alpha=math.degrees(alpha), gama=math.degrees(gama), L=L, n_per_col=g.n_nodes_per_col,
                dis_bott=dis_bott, dis_top=dis_top, high_truss=high_truss, H_0=H_0, deviation=deviation, beta=beta)
    return g.nodes, g.node_map, meta


# ==================== 新程序（Java 逻辑移植） ====================

def compute_feasible(height, bw_min, bw_max, last_max=LAST_MAX):
    out = []
    for bw in range(int(bw_min), int(bw_max) + 1):
        bwMm = bw * 1000.0
        for k in range(0, 101):            # beta = 50 - k*0.1
            beta = 50.0 - k * 0.1
            if beta < 40.0 - 1e-9:
                break
            t = math.tan(math.radians(beta))
            a = bwMm / 2.0 - DEV_MM
            b = (TOP_MM - bwMm) / (2.0 * height)
            disc = 1.0 - b * b * t * t
            if disc <= 1e-12:
                continue
            eff = t / math.sqrt(disc)
            denom = 1.0 - b * eff
            if denom <= 1e-9 or a <= 0:
                continue
            firstVert = FIRST_OFF / math.sqrt(1.0 + b * b) + DEV_MM * eff / (1.0 + b * eff)
            nodes = [firstVert]
            h = firstVert
            nodeCeil = height + last_max
            for _ in range(200000):
                hNext = (h * (1.0 + b * eff) + 2.0 * a * eff) / denom
                if math.isnan(hNext) or hNext <= h or hNext > nodeCeil:
                    break
                nodes.append(hNext)
                h = hNext
            if len(nodes) < 2:
                continue
            last = nodes[-1]
            residual = last - height
            if LAST_MIN - 1e-6 <= residual <= last_max + 1e-6:
                out.append(dict(bottomWidthM=bw, bottomWidthMm=bwMm, betaDeg=round(beta, 1),
                                panelCount=len(nodes) - 1, nodeHeights=nodes,
                                lastOffsetMm=residual, highTrussMm=last + DEV_MM * eff / denom,
                                _b=b, _eff=eff))
    return out


def width_at(y, bw, top, H):
    return bw + (top - bw) * (y / H)


def build_new(bwMm, heightMm, ecc, highTrussMm, topMm=TOP_MM, devMm=DEV_MM):
    k = len(ecc)
    n = k + 2
    pb1 = [bwMm / 2.0, bwMm / 2.0, 0.0]
    topAtHi = width_at(highTrussMm, bwMm, topMm, heightMm)
    pt1 = [topAtHi / 2.0, topAtHi / 2.0, highTrussMm]

    def sub(p, q):
        return [p[0] - q[0], p[1] - q[1], p[2] - q[2]]

    def dot(u, v):
        return u[0] * v[0] + u[1] * v[1] + u[2] * v[2]

    def cross(u, v):
        return [u[1] * v[2] - u[2] * v[1], u[2] * v[0] - u[0] * v[2], u[0] * v[1] - u[1] * v[0]]

    def nrm(u):
        return math.sqrt(dot(u, u))

    def angle(p1, p2, p3):
        v1, v2 = sub(p1, p2), sub(p3, p2)
        return math.acos(max(-1.0, min(1.0, dot(v1, v2) / (nrm(v1) * nrm(v2)))))

    alpha = angle([topMm / 2.0, bwMm / 2.0, 0.0], pb1, pt1)
    delta = devMm * math.tan(math.pi / 2.0 - alpha)
    mz = [0.0] + [e + delta for e in ecc] + [highTrussMm]

    nodes, nmap = {}, {}
    cur = 1
    cols = ["a", "b", "c", "d"]
    signs = {"a": (1, 1), "b": (-1, 1), "c": (-1, -1), "d": (1, -1)}
    for c in cols:
        sx, sy = signs[c]
        for i in range(n):
            nodes[cur] = [width_at(mz[i], bwMm, topMm, heightMm) / 2.0 * sx,
                          width_at(mz[i], bwMm, topMm, heightMm) / 2.0 * sy, mz[i]]
            nmap[f"col_{c}{i}"] = cur
            cur += 1
    dxOff = devMm / math.cos(math.pi / 2.0 - alpha)
    dzOff = devMm * math.tan(math.pi / 2.0 - alpha)
    for c in cols:
        sx, sy = signs[c]
        for i in range(1, n - 1):
            b = nodes[nmap[f"col_{c}{i}"]]
            nodes[cur] = [b[0], b[1] - sy * dxOff, b[2] - dzOff]
            nmap[f"col_{c}_dev_y{i}"] = cur
            cur += 1
            nodes[cur] = [b[0] - sx * dxOff, b[1], b[2] - dzOff]
            nmap[f"col_{c}_dev_x{i}"] = cur
            cur += 1
    quad = [("I", "a", "b", "x"), ("II", "b", "c", "y"), ("III", "c", "d", "x"), ("IV", "d", "a", "y")]
    for qn, c1, c2, dd in quad:
        for i in range(1, n - 2):
            names = [f"col_{c1}_dev_{dd}{i}", f"col_{c2}_dev_{dd}{i}",
                     f"col_{c2}_dev_{dd}{i+1}", f"col_{c1}_dev_{dd}{i+1}"]
            if all(x in nmap for x in names):
                A, B, C, D = (nodes[nmap[names[0]]], nodes[nmap[names[1]]],
                              nodes[nmap[names[2]]], nodes[nmap[names[3]]])
                A, C2, B2, D2 = A, C, B, D
                AB, CD, AC = sub(B2, A), sub(D2, C2), sub(C2, A)
                cp = cross(AB, CD)
                if nrm(cp) == 0:
                    mid = [(A[t] + B2[t] + C2[t] + D2[t]) / 4.0 for t in range(3)]
                else:
                    tt = dot(cross(AC, CD), cp) / dot(cp, cp)
                    mid = [A[t] + tt * AB[t] for t in range(3)]
                nodes[cur] = mid
                nmap[f"X_{qn}{i}"] = cur
                cur += 1
    tz = sum(nodes[nmap[f"col_{c}{n-1}"]][2] for c in cols) / 4.0
    tx = sum(nodes[nmap[f"col_{c}{n-1}"]][0] for c in cols) / 4.0
    ty = sum(nodes[nmap[f"col_{c}{n-1}"]][1] for c in cols) / 4.0
    nodes[cur] = [tx, ty, tz]
    nmap["top_center"] = cur
    cur += 1
    meta = dict(alpha=math.degrees(alpha), delta=delta, n=n, k=k, dxOff=dxOff, dzOff=dzOff, mz=mz)
    return nodes, nmap, meta


if __name__ == "__main__":
    onodes, onmap, ometa = run_old()
    print("=== 老程序 meta ===")
    for k, v in ometa.items():
        print(f"  {k} = {v}")
    print(f"  节点总数 = {len(onodes)}")

    print("\n=== 新程序求解（bw=25, beta=47.7, height=116733）===")
    sols = compute_feasible(116733, 25, 25)
    sol = next((s for s in sols if abs(s["betaDeg"] - 47.7) < 1e-6), None)
    print(f"  bw=25 总可行解数 = {len(sols)}；beta 列表 = {[s['betaDeg'] for s in sols]}")
    if sol is None:
        print("  >>> beta=47.7 不在可行解中！")
        if sols:
            sol = sols[0]
            print(f"  >>> 取最接近解 beta={sol['betaDeg']}")
    if sol:
        print(f"  panelCount={sol['panelCount']} highTrussMm={sol['highTrussMm']:.3f} lastOffset={sol['lastOffsetMm']:.3f}")
        ecc = sol["nodeHeights"]
        print(f"  ecc(nodeHeights) = {[round(x,3) for x in ecc]}")
        nnodes, nnmap, nmeta = build_new(sol["bottomWidthMm"], 116733, ecc, sol["highTrussMm"])
        print(f"\n=== 新程序 meta ===")
        for k, v in nmeta.items():
            print(f"  {k} = {v}")

        print("\n=== 逐节点对比（老 vs 新，同名节点）===")
        common = sorted(set(onmap) & set(nnmap), key=lambda s: (len(s), s))
        maxdiff = 0.0
        for name in common:
            o = onodes[onmap[name]]
            nv = nnodes[nnmap[name]]
            d = max(abs(o[t] - nv[t]) for t in range(3))
            maxdiff = max(maxdiff, d)
            if d > 1e-6:
                print(f"  {name:18s} OLD({o[0]:11.3f},{o[1]:11.3f},{o[2]:11.3f})  "
                      f"NEW({nv[0]:11.3f},{nv[1]:11.3f},{nv[2]:11.3f})  maxΔ={d:9.3f}")
        print(f"\n  同名节点数 = {len(common)} / 老{len(onmap)} 新{len(nnmap)}；最大分量差 = {maxdiff:.3f} mm")
        only_old = set(onmap) - set(nnmap)
        only_new = set(nnmap) - set(onmap)
        print(f"  仅老程序有 = {sorted(only_old)}")
        print(f"  仅新程序有 = {sorted(only_new)}")
