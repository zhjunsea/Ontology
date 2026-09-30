#把底法兰有限元计算计算出来焊缝及倒角的应力曲线txt文档转换成格式化的excel文件
import numpy as np
import pandas as pd
import os

def split(df):
    df_categories = df.iloc[:, 0:1]
    df_outer = df.iloc[:, :5]
    new_order = [0, 1, 3, 2, 4]
    df_outer = df_outer.iloc[:, new_order]

    df_inner = df.iloc[:, 5:10]
    df_new_inner = pd.concat([df_categories, df_inner], axis=1)
    df_new_inner = df_new_inner.iloc[:, new_order]

    # 创建空集并赋值
    n_rows, n_cols = df_outer.shape
    columns = df_outer.columns
    index = df_outer.index

    # 先外
    empty_array_1 = np.empty((n_rows, n_cols))
    empty_array_1[:] = df_outer.to_numpy()
    df_processed_outer = pd.DataFrame(empty_array_1, index=index, columns=columns)

    # 后内
    empty_array_2 = np.empty((n_rows, n_cols))
    empty_array_2[:] = df_new_inner.to_numpy()
    df_processed_inner = pd.DataFrame(empty_array_2, index=index, columns=columns)


    return [df_processed_inner, df_processed_outer]


def txt_to_excel(txt_fillet, txt_weld, excel_path):
    # 读取TXT文件
    df_fillet = pd.read_csv(txt_fillet, delim_whitespace=True, header=None)
    df_weld = pd.read_csv(txt_weld, delim_whitespace=True, header=None)
    [df_fillet_inner, df_fillet_outer] = split(df_fillet)

    fillet_header_outer = pd.DataFrame({'Row Type': ['outer_fillet']}, index=[0])
    df_fillet_outer_with_header = pd.concat([fillet_header_outer, df_fillet_outer], axis=1)
    fillet_header_inner = pd.DataFrame({'Row Type': ['inner_fillet']}, index=[0])
    df_fillet_inner_with_header = pd.concat([fillet_header_inner, df_fillet_inner], axis=1)

    [df_weld_inner, df_weld_outer] = split(df_weld)
    weld_header_outer = pd.DataFrame({'Row Type': ['outer_weld']}, index=[0])
    df_weld_outer_with_header = pd.concat([weld_header_outer, df_weld_outer], axis=1)
    weld_header_inner = pd.DataFrame({'Row Type': ['inner_weld']}, index=[0])
    df_weld_inner_with_header = pd.concat([weld_header_inner, df_weld_inner], axis=1)

    # 创建空行
    empty_row = pd.DataFrame(np.nan, index=[0], columns=df_fillet_inner_with_header.columns)

    # 结合
    df_combined = pd.concat([
        df_fillet_inner_with_header,
        empty_row,
        df_fillet_outer_with_header,
        empty_row,
        df_weld_inner_with_header,
        empty_row,
        df_weld_outer_with_header
    ], axis=0)


    fixed_horizontal_headers = ['', 'My', 'S13_Luv_1', 'S13_Luv_2', 'S13_Lee_1', 'S13_Lee_2']
    df_combined.columns = fixed_horizontal_headers

    if os.path.exists(excel_path):
        try:
            os.remove(excel_path)
            print(f"旧的数据表 '{excel_path}' 已被覆盖.")
        except Exception as e:
            print(f"覆盖旧的数据表的时候产生错误 {e}")

    df_combined.to_excel(excel_path, index=False)

def txt_to_excel_four_files(inner_fillet, outer_fillet, inner_weld, outer_weld, excel_path):
    """
    读取四个TXT文件并生成格式化的Excel文件

    参数:
    inner_fillet: 内倒角应力数据文件路径
    outer_fillet: 外倒角应力数据文件路径
    inner_weld: 内焊缝应力数据文件路径
    outer_weld: 外焊缝应力数据文件路径
    excel_path: 输出Excel文件路径
    """
    # 读取TXT文件
    df_inner_fillet = pd.read_csv(inner_fillet, delim_whitespace=True, header=None)
    df_outer_fillet = pd.read_csv(outer_fillet, delim_whitespace=True, header=None)
    df_inner_weld = pd.read_csv(inner_weld, delim_whitespace=True, header=None)
    df_outer_weld = pd.read_csv(outer_weld, delim_whitespace=True, header=None)

    # 交换第3列和第4列 (索引2和3)，保持其他列不变
    def swap_columns_safely(df):
        if df.shape[1] >= 4:  # 确保至少有4列才能交换
            # 创建列索引列表
            cols = list(range(df.shape[1]))
            # 交换第3列和第4列（索引2和3）
            cols[2], cols[3] = cols[3], cols[2]
            return df.iloc[:, cols]
        return df  # 列数不足时不交换

    df_inner_fillet = swap_columns_safely(df_inner_fillet)
    df_outer_fillet = swap_columns_safely(df_outer_fillet)
    df_inner_weld = swap_columns_safely(df_inner_weld)
    df_outer_weld = swap_columns_safely(df_outer_weld)

    # 为每个数据添加标识
    inner_fillet_header = pd.DataFrame({'Row Type': ['inner_fillet']}, index=[0])
    df_inner_fillet_with_header = pd.concat([inner_fillet_header, df_inner_fillet], axis=1)

    outer_fillet_header = pd.DataFrame({'Row Type': ['outer_fillet']}, index=[0])
    df_outer_fillet_with_header = pd.concat([outer_fillet_header, df_outer_fillet], axis=1)

    inner_weld_header = pd.DataFrame({'Row Type': ['inner_weld']}, index=[0])
    df_inner_weld_with_header = pd.concat([inner_weld_header, df_inner_weld], axis=1)

    outer_weld_header = pd.DataFrame({'Row Type': ['outer_weld']}, index=[0])
    df_outer_weld_with_header = pd.concat([outer_weld_header, df_outer_weld], axis=1)

    # 创建空行
    empty_row = pd.DataFrame(np.nan, index=[0], columns=df_inner_fillet_with_header.columns)

    # 结合所有数据
    df_combined = pd.concat([
        df_inner_fillet_with_header,
        empty_row,
        df_outer_fillet_with_header,
        empty_row,
        df_inner_weld_with_header,
        empty_row,
        df_outer_weld_with_header
    ], axis=0)

    # 设置列标题 - 根据实际列数动态生成列名
    num_columns = len(df_combined.columns)
    if num_columns == 6:
        fixed_horizontal_headers = ['', 'My', 'S13_Luv_1', 'S13_Luv_2', 'S13_Lee_1', 'S13_Lee_2']
    else:
        # 如果列数不匹配，动态生成列名
        fixed_horizontal_headers = ['Row Type'] + [f'Column_{i}' for i in range(1, num_columns)]
        # 确保列表长度匹配
        fixed_horizontal_headers = fixed_horizontal_headers[:num_columns]

    df_combined.columns = fixed_horizontal_headers

    # 如果文件已存在则删除
    if os.path.exists(excel_path):
        try:
            os.remove(excel_path)
            print(f"旧的数据表 '{excel_path}' 已被覆盖.")
        except Exception as e:
            print(f"覆盖旧的数据表的时候产生错误 {e}")

    # 保存为Excel
    df_combined.to_excel(excel_path, index=False)







if __name__ == "__main__":
    # 输入
    # txt_fillet = r"D:\塔架计算平台\10.txt应力曲线转excel\stress_out_fillet.txt"
    # txt_weld = r"D:\塔架计算平台\10.txt应力曲线转excel\stress_weld.txt"
    # excel_file = r"D:\塔架计算平台\10.txt应力曲线转excel\load_stress_flange.xlsx"
    # txt_to_excel(txt_fillet, txt_weld, excel_file)

    # 处理四个独立文件
    inner_fillet = r"D:\towerdesignrelated\flasktest\00.开发\04.中间法兰应力曲线txt转excel\low_fillet_stress.txt"
    outer_fillet = r"D:\towerdesignrelated\flasktest\00.开发\04.中间法兰应力曲线txt转excel\low_fillet_stress.txt"
    inner_weld = r"D:\towerdesignrelated\flasktest\00.开发\04.中间法兰应力曲线txt转excel\low_weld_in_stress.txt"
    outer_weld = r"D:\towerdesignrelated\flasktest\00.开发\04.中间法兰应力曲线txt转excel\low_weld_out_stress.txt"
    excel_file_four = r"D:\towerdesignrelated\flasktest\00.开发\04.中间法兰应力曲线txt转excel\abc.xlsx"

    txt_to_excel_four_files(inner_fillet, outer_fillet, inner_weld, outer_weld, excel_file_four)
