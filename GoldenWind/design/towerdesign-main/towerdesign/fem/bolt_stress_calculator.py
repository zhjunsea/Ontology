#处理有限元计算出来的螺栓数据
import numpy as np
import pandas as pd

def compute_moment_projection(My, Mz, angles_rad):
    """计算弯矩在不同角度上的投影"""
    return My * np.cos(angles_rad) + Mz * np.sin(angles_rad)

def compute_stress(N, Mn, A, I, r):
    """计算螺栓应力"""
    axial_stress = N / A
    bending_stress = (Mn * r) / I
    total_stress = axial_stress + bending_stress
    return axial_stress, bending_stress, total_stress

def process_bolt_data(input_file, output_file, bolt_diameter):
    """
    处理螺栓数据并计算应力

    参数:
        input_file (str): 输入文件路径
        output_file (str): 输出文件路径
        bolt_diameter (int): 螺栓应力截面积直径
    """
    # 螺栓参数
    d = bolt_diameter
    A = np.pi * d**2 / 4    # 截面积 (mm²)
    I = np.pi * d**4 / 64   # 惯性矩 (mm⁴)
    r = d / 2               # 半径 (mm)

    # 圆周8个点
    angles_deg = np.arange(0, 360, 45)
    angles_rad = np.radians(angles_deg)

    # 读取数据
    try:
        data = pd.read_csv(input_file, sep=r'\s+', header=None, engine='python')
    except FileNotFoundError:
        print(f"❌ 错误：找不到文件 '{input_file}'，请检查路径！")
        return None

    # 提取数据
    load_ids = data.iloc[1:, 0].values
    bolt_ids = data.iloc[0, :].values
    data_values = data.iloc[1:, 1:].values.astype(float)

    n_load_cases = data_values.shape[0]
    n_cols = data_values.shape[1]

    # 按螺栓处理（计算应力）
    results = {}
    col_idx = 0

    while col_idx < n_cols:
        bolt_id = int(bolt_ids[col_idx + 1])
        if bolt_id == 0:
            col_idx += 1
            continue
        if col_idx + 3 > n_cols:
            break

        My_col = data_values[:, col_idx]
        Mz_col = data_values[:, col_idx + 1]
        N_col = data_values[:, col_idx + 2]

        stress_table = []

        for i in range(n_load_cases):
            N, My, Mz = N_col[i], My_col[i], Mz_col[i]
            Mn = compute_moment_projection(My, Mz, angles_rad)
            sigma_axial, sigma_bend, sigma_total = compute_stress(N, Mn, A, I, r)

            row = [load_ids[i], N, My, Mz, sigma_axial]
            row += np.round(sigma_bend, 2).tolist()
            row += np.round(sigma_total, 2).tolist()
            stress_table.append(row)

        columns = ['Load_ID(KN.m)', 'N(N)', 'My(N.mm)', 'Mz(N.mm)', 'Axial_Stress(Mpa)']
        columns += [f'Bend_Stress_{int(a)}°(Mpa)' for a in angles_deg]
        columns += [f'Total_Stress_{int(a)}°(Mpa)' for a in angles_deg]

        results[bolt_id] = pd.DataFrame(stress_table, columns=columns)
        col_idx += 3


    # 保存到 Excel
    with pd.ExcelWriter(output_file, engine='openpyxl') as writer:
        # 按数值大小排序螺栓ID
        sorted_bolt_ids = sorted(results.keys(), key=lambda x: int(''.join(filter(str.isdigit, str(x)))))
        for bolt_id in sorted_bolt_ids:
            results[bolt_id].to_excel(writer, sheet_name=f'Bolt_{bolt_id}', index=False)

    return results



if __name__ == '__main__':
    # 配置参数
    input_file = r'D:\00.分片塔\03.分片环法兰建模\13.普通整法兰计算20251013 - 奥地利项目第2段算例-平台版本 - 螺栓后处理\txt处理\Down_flage_M30_point1_my.txt'
    output_file = r'D:\00.分片塔\03.分片环法兰建模\13.普通整法兰计算20251013 - 奥地利项目第2段算例-平台版本 - 螺栓后处理\txt处理\output_analysistest.xlsx'
    bolt_diameter = 30
    # 执行处理
    results = process_bolt_data(input_file, output_file, bolt_diameter)