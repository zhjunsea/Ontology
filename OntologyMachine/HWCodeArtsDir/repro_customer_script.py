"""复现客户脚本的节点/单元编号与结构，并与本体求解器几何口径对照。

目的：锁定「坐标生成 + 编号」方案（★16：诊断脚本放 HWCodeArtsDir）。
不修改任何客户资产。
"""
import importlib.util
import math
import numpy as np

CS = r"D:\work\Ontology\OntologyMachine\OntologyFrameworkLatticeTower\design\新铸件偏心_45度_optimized_加横撑横隔_204m_增加格构顶端节点(建总需求).py"

spec = importlib.util.spec_from_file_location("cs", CS)
cs = importlib.util.module_from_spec(spec)
spec.loader.exec_module(cs)

# ---- 样例参数（同客户脚本 __main__）----
dis_bott = 25000
dis_top = 9604.88
high_truss = 116733
H_0 = 744.817
deviation = 500
beta1 = beta2 = 47.7

point_bott_1 = [dis_bott * 0.5, dis_bott * 0.5, 0]
point_top_1 = [dis_top * 0.5, dis_top * 0.5, high_truss]
alpha = cs.cal_angle([dis_top * 0.5, dis_bott * 0.5, 0], point_bott_1, point_top_1)
gama = cs.cal_angle([0, 0, 0], point_bott_1, point_top_1)
L = cs.cal_length(point_bott_1, point_top_1)
print(f"alpha={math.degrees(alpha):.4f} gama={math.degrees(gama):.4f} L={L:.4f}")

g = cs.LatticeTowerGenerator(H_0, alpha, gama, dis_bott, L, point_bott_1, point_top_1,
                             high_truss, deviation, beta1, beta2)
dd = g.calculate_column_segments()
print("data_dict:", {k: round(v, 4) for k, v in dd.items()})
print("n_nodes_per_col:", g.n_nodes_per_col)
g.generate_node_coordinates(dd)
g.generate_elements()

print("总节点数:", len(g.nodes), " 总单元数:", len(g.elements))

# 按名称归类打印关键 z
def z(name):
    return round(g.nodes[g.node_map[name]][2], 4)

print("\n--- 立柱 a 主节点 z ---")
for i in range(g.n_nodes_per_col):
    print(f"  col_a{i} id={g.node_map['col_a'+str(i)]} z={z('col_a'+str(i))}")
print("\n--- 立柱 a 偏心节点 z (dev_y / dev_x) ---")
for i in range(1, g.n_nodes_per_col - 1):
    print(f"  i={i} dev_y id={g.node_map['col_a_dev_y'+str(i)]} z={z('col_a_dev_y'+str(i))} | "
          f"dev_x id={g.node_map['col_a_dev_x'+str(i)]} z={z('col_a_dev_x'+str(i))}")
print("\n--- 交叉点 X_I z ---")
for i in range(1, g.n_nodes_per_col - 2):
    nm = f"X_I{i}"
    if nm in g.node_map:
        print(f"  i={i} id={g.node_map[nm]} z={z(nm)}")
print("\n--- top_center ---")
print("  id=", g.node_map["top_center"], "z=", z("top_center"), "coord=", g.nodes[g.node_map["top_center"]])
