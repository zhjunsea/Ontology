# -*- coding: utf-8 -*-
"""复核：客户『修正到准确的120mm → 744.559』是否自洽 / 我们程序 746.749 是否有问题。

方法：用客户脚本 3D 几何，扫描 H_0，计算首根斜腹杆反向延长线 ∩ 柱a中心线(P120)
到『面a-b ∩ 塔底平面』交线的垂距，找使垂距=120 的 H_0。只读，不改客户资产。
"""
import importlib.util, math
import numpy as np

CS = r"D:\work\Ontology\OntologyMachine\OntologyFrameworkLatticeTower\design\新铸件偏心_45度_optimized_加横撑横隔_204m_增加格构顶端节点(建总需求).py"
spec = importlib.util.spec_from_file_location("cs", CS)
cs = importlib.util.module_from_spec(spec); spec.loader.exec_module(cs)

DIS_BOTT, DIS_TOP, HIGH_TRUSS, DEV, BETA = 25000.0, 9604.88, 116733.0, 500.0, 47.7

def build(H0):
    pb = [DIS_BOTT*0.5, DIS_BOTT*0.5, 0]; pt = [DIS_TOP*0.5, DIS_TOP*0.5, HIGH_TRUSS]
    alpha = cs.cal_angle([DIS_TOP*0.5, DIS_BOTT*0.5, 0], pb, pt)
    gama  = cs.cal_angle([0,0,0], pb, pt)
    L     = cs.cal_length(pb, pt)
    g = cs.LatticeTowerGenerator(H0, alpha, gama, DIS_BOTT, L, pb, pt, HIGH_TRUSS, DEV, BETA, BETA)
    dd = g.calculate_column_segments(); g.generate_node_coordinates(dd); g.generate_elements()
    return g, alpha, gama, L

def N(g, name): return np.array(g.nodes[g.node_map[name]], dtype=float)

def ll_closest(p0,d0,p1,d1):
    n=np.cross(d0,d1)
    if np.linalg.norm(n)<1e-12:
        t=np.dot(p1-p0,d0)/np.dot(d0,d0); return p0+t*d0
    A=np.array([[np.dot(d0,d0),-np.dot(d0,d1)],[np.dot(d0,d1),-np.dot(d1,d1)]])
    b=np.array([np.dot(p1-p0,d0),np.dot(p1-p0,d1)]); s,t=np.linalg.solve(A,b)
    return p0+s*d0

def measures(H0):
    g, alpha, gama, L = build(H0)
    col_a0 = N(g,'col_a0'); col_a9 = N(g,'col_a'+str(g.n_nodes_per_col-1))
    colA_dir = col_a9 - col_a0
    start = N(g,'col_a_dev_x1'); partner = N(g,'col_b_dev_x2')
    d = partner - start
    Pc = ll_closest(start, d, col_a0, colA_dir)      # brace-line 上最近点
    Qc = ll_closest(col_a0, colA_dir, start, d)      # centerline 上最近点
    gap = np.linalg.norm(Pc-Qc)
    # 用中心线侧最近点 Qc 作 P120
    base_pt = col_a0; base_dir = N(g,'col_b0') - col_a0
    u = base_dir/np.linalg.norm(base_dir)
    v = Qc - base_pt; perp = v - np.dot(v,u)*u
    d120 = np.linalg.norm(perp)
    return dict(H0=H0, dev1=start[2], Qc_z=Qc[2], Pc_z=Pc[2], d120=d120, gap=gap, alpha=math.degrees(alpha))

print("=== 客户脚本实参 (dis_top=9604.88, high_truss=116733) ===")
for h0 in [744.559, 744.817, 746.749]:
    m = measures(h0)
    print(f"H0={h0:.3f}: dev1={m['dev1']:.4f} Qc_z={m['Qc_z']:.4f} d120={m['d120']:.4f} "
          f"gap={m['gap']:.4f} (dev1=H0-Δm? Δm=H0-dev1={m['H0']-m['dev1']:.4f})")

print("\n=== 扫描 H0，求 d120=120 的 H0 ===")
prev=None
for h0 in np.arange(735, 760, 0.25):
    m = measures(h0)
    print(f"H0={h0:7.2f}  dev1={m['dev1']:9.4f}  d120={m['d120']:10.4f}  gap={m['gap']:.4f}")
