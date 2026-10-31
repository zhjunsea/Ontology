# -*- coding: utf-8 -*-
"""忠实复现客户脚本模型：直接调用客户脚本 LatticeTowerGenerator（★16 诊断脚本）。
在客户塔参数（bw=25、dis_top=9604.88、high_truss=116733）下扫 beta，取末偏心节点。
比较对象：H9800 = 9800 面高度（客户收分线反算）；residual = ● - H9800。
"""
import importlib.util
import math

CS = r"D:\work\Ontology\OntologyMachine\OntologyFrameworkLatticeTower\design\新铸件偏心_45度_optimized_加横撑横隔_204m_增加格构顶端节点(建总需求).py"
spec = importlib.util.spec_from_file_location("cs", CS)
cs = importlib.util.module_from_spec(spec)
spec.loader.exec_module(cs)

# 客户脚本 __main__ 的塔参数（beta 另扫）
dis_bott = 25000.0
dis_top = 9604.88
high_truss = 116733.0
H_0 = 744.817
deviation = 500.0

point_bott_1 = [dis_bott * 0.5, dis_bott * 0.5, 0.0]
point_top_1 = [dis_top * 0.5, dis_top * 0.5, high_truss]
alpha = cs.cal_angle([dis_top * 0.5, dis_bott * 0.5, 0], point_bott_1, point_top_1)
gama = cs.cal_angle([0, 0, 0], point_bott_1, point_top_1)
L = cs.cal_length(point_bott_1, point_top_1)

# 9800 面高度：width(z)=dis_bott+(dis_top-dis_bott)*z/high_truss 令 =9800 反算
H9800 = high_truss * (dis_bott - 9800.0) / (dis_bott - dis_top)

print("塔参数: alpha=%.4f gama=%.4f L=%.3f  H9800=%.2f  high_truss=%.1f" %
      (math.degrees(alpha), math.degrees(gama), L, H9800, high_truss))
print("窗口[850,980] 对应 ● 高度区间 = [%.2f, %.2f]" % (H9800 + 850, H9800 + 980))
print()


def last_dev_z(beta):
    g = cs.LatticeTowerGenerator(H_0, alpha, gama, dis_bott, L, point_bott_1, point_top_1,
                                 high_truss, deviation, beta, beta)
    dd = g.calculate_column_segments()
    g.generate_node_coordinates(dd)
    zs = []
    for i in range(1, g.n_nodes_per_col - 1):
        nm = f"col_a_dev_y{i}"
        if nm in g.node_map:
            zs.append(g.nodes[g.node_map[nm]][2])
    return zs, g.n_nodes_per_col


# 单点：beta=47.7
zs, n = last_dev_z(47.7)
print("=== bw=25 beta=47.7 ===")
print("  末偏心节点 ● = %.3f  => residual = ● - H9800 = %.3f  窗口[850,980]内? %s"
      % (zs[-1], zs[-1] - H9800, 850 - 1e-6 <= zs[-1] - H9800 <= 980 + 1e-6))
print("  全部偏心节点 z =", [round(z, 2) for z in zs])
print()

# 扫 beta 40~50
print("=== 客户模型 beta 扫描（bw=25） ===")
feas = []
for k in range(101):
    beta = round(50.0 - k * 0.1, 1)
    try:
        zs, n = last_dev_z(beta)
    except Exception as e:
        continue
    res = zs[-1] - H9800
    ok = 850 - 1e-6 <= res <= 980 + 1e-6
    if ok:
        feas.append((beta, res, zs[-1], n))
    if abs(beta - 47.7) < 1.5 or ok:
        print("  beta=%5.1f  ●=%10.2f  residual=%8.2f  %s" %
              (beta, zs[-1], res, "可行" if ok else ""))
print()
print("可行角度: " + (", ".join("%.1f(res=%.1f)" % (b, r) for b, r, _, _ in feas) or "无"))
