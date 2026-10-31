# -*- coding: utf-8 -*-
"""复核 2：在多口径下求『使首杆120mm成立』的 H_0，与 744.559 / 744.817 / 程序746.749 对比。
另验证程序闭式本身是否精确满足 120mm（自洽性）。只读。"""
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
    return g

def N(g,name): return np.array(g.nodes[g.node_map[name]],dtype=float)

def defs(H0):
    g = build(H0)
    a0=N(g,'col_a0'); aN=N(g,'col_a'+str(g.n_nodes_per_col-1)); dirA=aN-a0
    A=N(g,'col_a_dev_x1'); B=N(g,'col_b_dev_x2'); d=B-A
    # 底面交线 (面a-b ∩ z=0): 过 a0, 方向 b0-a0
    bl_pt, bl_dir = a0, N(g,'col_b0')-a0; u=bl_dir/np.linalg.norm(bl_dir)
    def perp_to_line(p):
        v=p-bl_pt; return np.linalg.norm(v-np.dot(v,u)*u)
    # 最近点
    def closest(p0,d0,p1,d1):
        n=np.cross(d0,d1)
        if np.linalg.norm(n)<1e-12:
            t=np.dot(p1-p0,d0)/np.dot(d0,d0); return p0+t*d0
        M=np.array([[np.dot(d0,d0),-np.dot(d0,d1)],[np.dot(d0,d1),-np.dot(d1,d1)]])
        b=np.array([np.dot(p1-p0,d0),np.dot(p1-p0,d1)]); s,t=np.linalg.solve(M,b)
        return p0+s*d0
    Qc=closest(a0,dirA,A,d)     # 中心线上最近点
    Pc=closest(A,d,a0,dirA)     # 斜杆线上最近点
    return dict(H0=H0, dev1=A[2],
                A=perp_to_line(A),            # 起点到交线垂距
                Qc=perp_to_line(Qc),          # 中心线最近点到交线垂距
                Pc=perp_to_line(Pc),          # 斜杆最近点到交线垂距
                zQc=Qc[2], gap=np.linalg.norm(Pc-Qc))

def solve_for(target, key):
    lo,hi=700.0,800.0
    for _ in range(80):
        mid=(lo+hi)/2
        if defs(mid)[key]<target: lo=mid
        else: hi=mid
    return (lo+hi)/2

print("== 客户几何：各口径使“=120”的 H_0 ==")
for key in ['A','Qc','Pc','zQc']:
    print(f"  {key:>4}: H0 = {solve_for(120.0,key):.4f}   (744.559? {'是' if abs(solve_for(120.0,key)-744.559)<0.02 else '否'})")
print("\n== 实测量（H0=744.559 / 744.817 / 746.749）==")
for h0 in [744.559,744.817,746.749]:
    m=defs(h0)
    print(f"  H0={h0:.3f}: d120(A)={m['A']:8.4f} d120(Qc)={m['Qc']:8.4f} dev1={m['dev1']:.4f}")

print("\n== 程序闭式自洽性：H_0=746.749 时 P120 垂距 ==")
BW,TOP,H,beta,dv,off=25000.0,9800.0,115253.0,47.7,500.0,120.0
b=(TOP-BW)/(2*H); z1=off/math.sqrt(1+b*b)
print(f"  b={b:.8f}  z1={z1:.6f}  z1*sqrt(1+b^2)={z1*math.sqrt(1+b*b):.6f} (=120?)")
print(f"  客户/程序 implied firstOff for H0=744.559: {(744.559 - dv*math.tan(math.pi/2-math.acos(((BW-TOP)/2)/math.hypot(math.sqrt(2)*(BW-TOP)/2,H))) - dv*(math.tan(math.radians(beta))/math.sqrt(1-b*b*math.tan(math.radians(beta))**2))/(1+b*(math.tan(math.radians(beta))/math.sqrt(1-b*b*math.tan(math.radians(beta))**2))))*math.sqrt(1+b*b):.4f} mm")
