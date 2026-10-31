"""从客户脚本 generator 的内部(未旋转)3D节点出发，精确重推 P120 / dev_1 / H_0 及 120mm 垂距。
不修改任何客户资产。"""
import importlib.util, math
import numpy as np

CS = r"D:\work\Ontology\OntologyMachine\OntologyFrameworkLatticeTower\design\新铸件偏心_45度_optimized_加横撑横隔_204m_增加格构顶端节点(建总需求).py"
spec = importlib.util.spec_from_file_location("cs", CS)
cs = importlib.util.module_from_spec(spec); spec.loader.exec_module(cs)

dis_bott, dis_top, high_truss, H_0, deviation = 25000, 9604.88, 116733, 744.817, 500
pb = [dis_bott*0.5, dis_bott*0.5, 0]; pt = [dis_top*0.5, dis_top*0.5, high_truss]
alpha = cs.cal_angle([dis_top*0.5, dis_bott*0.5, 0], pb, pt)
gama  = cs.cal_angle([0,0,0], pb, pt)
L     = cs.cal_length(pb, pt)
g = cs.LatticeTowerGenerator(H_0, alpha, gama, dis_bott, L, pb, pt, high_truss, deviation, 47.7, 47.7)
dd = g.calculate_column_segments(); g.generate_node_coordinates(dd); g.generate_elements()

def N(name): return np.array(g.nodes[g.node_map[name]], dtype=float)

print("alpha=%.4f deg  gama=%.4f deg  L=%.4f" % (math.degrees(alpha), math.degrees(gama), L))
print("1+b^2 slope check b=(dis_top/2-dis_bott/2)/high:", (dis_top/2-dis_bott/2)/high_truss)

col_a0, col_a1, col_a9 = N('col_a0'), N('col_a1'), N('col_a9')
print("col_a0=%s\ncol_a1=%s  z=%.4f\ncol_a9=%s" % (col_a0, col_a1, col_a1[2], col_a9))
dev1 = N('col_a_dev_x1'); dev2 = N('col_a_dev_x2')
bdev1 = N('col_b_dev_x1'); bdev2 = N('col_b_dev_x2')
print("dev_x1(a)=%s z=%.4f" % (dev1, dev1[2]))
print("dev_x2(a)=%s z=%.4f" % (dev2, dev2[2]))
print("dev_x1(b)=%s z=%.4f" % (bdev1, bdev1[2]))
print("dev_x2(b)=%s z=%.4f" % (bdev2, bdev2[2]))
print("X_I1 =", N('X_I1'), N('X_I1')[2])

def ll_closest(p0,d0,p1,d1):
    n=np.cross(d0,d1)
    if np.linalg.norm(n)<1e-12:
        t=np.dot(p1-p0,d0)/np.dot(d0,d0); q=p0+t*d0; return q,q
    A=np.array([[np.dot(d0,d0),-np.dot(d0,d1)],[np.dot(d0,d1),-np.dot(d1,d1)]])
    b=np.array([np.dot(p1-p0,d0),np.dot(p1-p0,d1)]); s,t=np.linalg.solve(A,b)
    return p0+s*d0, p1+t*d1

# 塔柱 a 中心线方向
colA_dir = col_a9 - col_a0
# 底边交线: 面 a-b 与 z=0 的交线 -> 过 col_a0, 方向 col_b0-col_a0
col_b0 = N('col_b0')
base_line_pt, base_line_dir = col_a0, (col_b0 - col_a0)

def report(tag, start, partner):
    d = partner - start
    Pc, Qc = ll_closest(start, d, col_a0, colA_dir)   # Pc on brace-line, Qc on centerline
    dev_to_col = np.linalg.norm(Pc-Qc)                # brace-line 到中心线最近距离
    # 若以 Qc 为 P120 (中心线上点)
    p = Qc
    v = p - base_line_pt
    u = base_line_dir/np.linalg.norm(base_line_dir)
    perp = v - np.dot(v,u)*u
    d120 = np.linalg.norm(perp)
    print("\n== %s ==" % tag)
    print("  brace dir=%s" % np.round(d,3))
    print("  最近点: brace侧=%s  z=%.4f | 中心线侧=%s z=%.4f  间距=%.4f" %
          (np.round(Pc,3), Pc[2], np.round(Qc,3), Qc[2], dev_to_col))
    print("  P120(中心线)到'面∩底面'交线 垂距 = %.4f mm" % d120)
    print("  dev起点 z=%.4f -> P120 z=%.4f : Δ竖直 = %.4f" % (start[2], Qc[2], start[2]-Qc[2]))

report("a_dev_x1 -> b_dev_x2", dev1, bdev2)
report("b_dev_x1 -> a_dev_x2", bdev1, dev2)
report("a_dev_x1 -> b_dev_x1", dev1, bdev1)
report("dev_x2(a) -> b_dev_x1", dev2, bdev1)

print("\n--- dev_1 与 H_0 ---")
print("dev_1 z=%.4f ; col_a1(H_0) z=%.4f ; 差=%.4f" % (dev1[2], col_a1[2], col_a1[2]-dev1[2]))
print("dev*tan(pi/2-alpha)=%.4f" % (deviation*math.tan(math.pi/2-alpha)))
print("dev*tan(pi/2-alpha) 用 cos/sin: %.4f" % (deviation*math.cos(alpha)/math.sin(alpha)))
