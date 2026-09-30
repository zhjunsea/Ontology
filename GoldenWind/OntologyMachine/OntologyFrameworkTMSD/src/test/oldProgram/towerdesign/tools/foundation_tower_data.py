#把towergeo数据表及基础数据表转换成有限元计算法兰的输入

import numpy as np
import openpyxl
import os

def process_tower_data(tower_excel_path, foundation_excel_path, output_txt_path,
                       Lengthrg,ls_start,ls_end,ls_range):
    # 创建一个26行7列的零矩阵
    matrix = np.zeros((26, 7), dtype=object)

    # 填充第一行和第一列
    for i in range(26):
        matrix[i][0] = i  # 第一列为行号
    for j in range(7):
        matrix[0][j] = j  # 第一行为列号

    # 打开源Excel文件
    source_wb = openpyxl.load_workbook(tower_excel_path)
    source_ws = source_wb['Flange']

    # 读取A1单元格的数据
    value = source_ws['D3'].value  # 内圈螺栓分度圆直径 mm
    matrix[1][1] = value
    value = source_ws['O3'].value  # 外圈螺栓分度圆直径 mm
    matrix[1][2] = value
    value = source_ws['J3'].value  # 内外圈螺栓总数
    matrix[2][1] = int(value / 2)
    matrix[2][2] = int(value / 2)

    matrix[4][1] = round(360 * 2 / value, 2)# 单个螺栓扇区角度
    matrix[4][2] = round(360 * 2 / value, 2)

    matrix[5][1] = round(360 * 2 / value, 2)
    matrix[5][2] = round(360 * 2 / value, 2)

    value = source_ws['I3'].value  # 螺栓孔径 mmm
    matrix[3][1] = value
    matrix[3][2] = value

    bolt_dim = source_ws['H3'].value
    if bolt_dim == 39:
        matrix[6][1] = 35.25  # 内圈锚栓应力截面对应的直径mm
        matrix[6][2] = 35.25  # 外圈锚栓应力截面对应的直径mm
        matrix[7][1] = 25  # 内圈螺栓头厚度mm
        matrix[7][2] = 25  # 外圈螺栓头厚度mm
        matrix[8][1] = 55.86  # 内圈螺栓头Dw mm
        matrix[8][2] = 55.86  # 外圈螺栓头Dw mm

        matrix[13][1] = 4  # 内圈螺栓螺距 mm
        matrix[13][2] = 4  # 外圈螺栓螺距 mm
        matrix[14][1] = 42  # 内圈垫片内径 mm
        matrix[14][2] = 42  # 外圈垫片内径 mm
        matrix[15][1] = 72  # 内圈垫片外径 mm
        matrix[15][2] = 72  # 外圈垫片外径 mm
        matrix[16][1] = 6  # 内圈垫片厚度 mm
        matrix[16][2] = 6  # 外圈垫片厚度 mm

    elif bolt_dim == 42:
        matrix[6][1] = 37.76
        matrix[6][2] = 37.76
        matrix[7][1] = 26
        matrix[7][2] = 26
        matrix[8][1] = 59.95
        matrix[8][2] = 59.95

        matrix[13][1] = 4.5
        matrix[13][2] = 4.5
        matrix[14][1] = 45
        matrix[14][2] = 45
        matrix[15][1] = 78
        matrix[15][2] = 78
        matrix[16][1] = 8
        matrix[16][2] = 8
    elif bolt_dim == 48:
        matrix[6][1] = 43.26
        matrix[6][2] = 43.26
        matrix[7][1] = 30
        matrix[7][2] = 30
        matrix[8][1] = 69.45
        matrix[8][2] = 69.45

        matrix[13][1] = 5
        matrix[13][2] = 5
        matrix[14][1] = 52
        matrix[14][2] = 52
        matrix[15][1] = 92
        matrix[15][2] = 92
        matrix[16][1] = 8
        matrix[16][2] = 8
    else:
        matrix[6][1] = 0
        matrix[6][2] = 0
        matrix[7][1] = 0
        matrix[7][2] = 0
        matrix[8][1] = 0
        matrix[8][2] = 0

        matrix[13][1] = 0
        matrix[13][2] = 0
        matrix[14][1] = 0
        matrix[14][2] = 0
        matrix[15][1] = 0
        matrix[15][2] = 0
        matrix[16][1] = 0
        matrix[16][2] = 0

    matrix[11][1] = bolt_dim
    matrix[11][2] = bolt_dim
    matrix[18][1] = 0.2
    matrix[18][2] = 0.2
    matrix[19][1] = 0.2
    matrix[19][2] = 0.2
    matrix[20][1] = 0.09
    matrix[20][2] = 0.09
    matrix[22][1] = 210000
    matrix[22][2] = 210000
    matrix[23][1] = 0.3
    matrix[23][2] = 0.3

    preforce = source_ws['P3'].value  # 锚栓预紧力 kN

    matrix[21][1] = preforce
    matrix[21][2] = preforce

    Da_fl_inner = source_ws['C3'].value  # 法兰内径 mm
    matrix[1][6] = Da_fl_inner
    Da_fl_outer = source_ws['N3'].value  # 法兰外径 mm
    matrix[2][6] = Da_fl_outer
    tfl = source_ws['E3'].value  # 法兰厚度 mm
    matrix[3][6] = tfl
    R_fillet = source_ws['K3'].value  # 法兰倒角半径 mm
    matrix[4][6] = R_fillet
    thk_neck = source_ws['F3'].value  # 法兰颈部壁厚 mm
    matrix[5][6] = thk_neck
    Da_tower = source_ws['B3'].value  # 塔筒外径  mm
    matrix[6][6] = Da_tower
    matrix[7][6] = thk_neck  # 筒壁厚度 mm
    matrix[8][6] = 6000  # 假体高度 mm

    # 倒角椭圆轴的长短比
    Ratio_e = source_ws['Q3'].value  # 法兰倒角椭圆长短轴之比  mm
    if Ratio_e is None:
        Ratio_e = 1.0

    matrix[9][6] = Ratio_e

    Ht_neck = source_ws['G3'].value  # 法兰颈高  mm
    matrix[10][6] = Ht_neck

    Lengthrg = float(Lengthrg)
    matrix[11][6] = Lengthrg
    ls_start = int(ls_start)
    matrix[12][6] = ls_start
    ls_end = int(ls_end)
    matrix[13][6] = ls_end
    ls_range = int(ls_range)
    matrix[14][6] = ls_range

    # 打开源Excel文件
    source_wb = openpyxl.load_workbook(foundation_excel_path)

    source_ws = source_wb['Foundation_ammount']

    L_down = source_ws['E15'].value

    matrix[9][1] = L_down * 1000  # 锚栓下漏长度，mm
    matrix[9][2] = L_down * 1000

    L_up = source_ws['C15'].value

    matrix[10][1] = L_up * 1000  # 锚栓上漏长度，mm
    matrix[10][2] = L_up * 1000

    Da_up_plate_in = source_ws['J15'].value
    matrix[1][3] = Da_up_plate_in * 1000  # 上锚板内径，mm

    Da_up_plate_out = source_ws['I15'].value
    matrix[2][3] = Da_up_plate_out * 1000  # 上锚板外径，mm

    thk_up_plate = source_ws['M15'].value
    matrix[3][3] = thk_up_plate * 1000  # 上锚板外径，mm

    Da_down_plate_in = source_ws['L15'].value
    matrix[1][5] = Da_down_plate_in * 1000  # 下锚板内径，mm

    Da_down_plate_out = source_ws['K15'].value
    matrix[2][5] = Da_down_plate_out * 1000  # 下锚板外径，mm

    thk_down_plate = source_ws['N15'].value
    matrix[3][5] = thk_down_plate * 1000  # 下锚板厚度，mm

    Di_grout_layer = source_ws['C41'].value
    matrix[1][4] = Di_grout_layer  # 上灌浆层内径，mm

    DO_grout_layer = source_ws['C40'].value
    matrix[2][4] = round(DO_grout_layer,1)  # 上灌浆层外径，mm

    THK_grout_layer = source_ws['C39'].value
    matrix[3][4] = THK_grout_layer  # 上灌浆层外径，mm

    Da_upper_foundation = source_ws['B6'].value
    matrix[4][4] = Da_upper_foundation * 1000  # 基础顶部外径，mm

    H_upper_foundation = source_ws['F6'].value
    matrix[5][4] = H_upper_foundation * 1000  # 基础上部高度，mm

    H_middle_foundation = source_ws['E6'].value
    matrix[6][4] = H_middle_foundation * 1000  # 基础中部高度，mm

    H_lower_foundation = source_ws['D6'].value
    matrix[7][4] = H_lower_foundation * 1000  # 基础下部高度，mm

    Da_lower_foundation = source_ws['A6'].value
    matrix[8][4] = Da_lower_foundation * 1000  # 基础底部外径，mm

    matrix[9][4] = Da_down_plate_in * 1000  # 下灌浆层内径，mm
    matrix[10][4] = Da_down_plate_out * 1000  # 下灌浆层外径

    matrix[11][4] = 200  # 内置预留高度
    matrix[12][4] = 1000  # 内置建模数值

    matrix[13][4] = (H_upper_foundation + H_middle_foundation + H_lower_foundation) * 1000 - 200  # 上下锚板之间的距离-200,mm

    with open(output_txt_path, 'w') as f:
        for row in matrix:
            # 将每一行的元素转换为字符串并用制表符分隔
            line = '\t'.join(map(str, row))
            f.write(line + '\n')  # 写入文件并换行
#提取塔底标高处的mxy载荷，并向上元整为疲劳计算的载荷
def extract_load_for_fatigue(excel_file, txt_file, row_num=7, col_num=5):
    try:

        # 打开 Excel 文件
        workbook = openpyxl.load_workbook(excel_file, data_only=True)
        sheet = workbook.active  # 假设使用活动工作表
        # 读取指定单元格的值
        mxy_max = sheet.cell(row=row_num, column=col_num).value
        # 尝试将值转换为浮点数
        if isinstance(mxy_max, str):
            try:
                mxy_max = float(mxy_max)
            except ValueError:
                print(f"无法将字符串 '{mxy_max}' 转换为浮点数")
                return None
        # 将值转换为绝对值
        mxy_max_abs = abs(mxy_max)
        #转换成整数
        mxy_max_int = int(mxy_max_abs)
        # 确定整数部分的位数
        k = len(str(mxy_max_int))
        # 计算元整位数，例如三位数元整到百位（100）
        divisor = 10 ** (k - 2)
        # 向上元整
        mxy_max_rounded = (mxy_max_int // divisor + 1) * divisor

        # 打开或创建文本文件
        with open(txt_file, 'w', encoding='utf-8') as txt:
            # 写入表头，txt的第一行
            header = [f"{i}" for i in range(13)]
            txt.write("\t".join(header) + "\n")
            # 生成一个包含13个零的列表
            zero_list = [0] * 13
            # 替换第4个和第5个元素（索引为3和4）
            zero_list[0] = 1
            zero_list[3] = -mxy_max_rounded
            zero_list[4] = mxy_max_rounded
            # 打印生成的列表
            # 将列表中的元素用制表符分隔并写入文件
            txt.write("\t".join(map(str, zero_list)) + "\n")
        return txt_file
    except Exception as e:
        return None



if __name__ == '__main__':
   tower_excel_path = r"D:\towerdesignrelated\flasktest\测试\有限元计算底法兰\Geometry_tower.xlsx"
   foundation_excel_path = r"D:\towerdesignrelated\flasktest\测试\有限元计算底法兰\Foundation_information.xlsx"
   output_txt_path = r"D:\towerdesignrelated\flasktest\测试\有限元计算底法兰\book.inp"

   Lengthrg = 1 #位置偏差（mm)
   ls_start = 1#"极限起始工况
   ls_end = 1 #极限结束工况
   ls_range = 1 #极限工况间隔

   process_tower_data(tower_excel_path, foundation_excel_path, output_txt_path,Lengthrg,ls_start,ls_end,ls_range)

   ultimateloadsExcel =  r"D:\塔架计算平台\11.读取最大极限载荷做为疲劳载荷的范围\ultimate_loads_tower_sf_section.xlsx"

   txt_file = r"D:\塔架计算平台\11.读取最大极限载荷做为疲劳载荷的范围\single_direction_ultimate_load.txt"
   extract_load_for_fatigue(ultimateloadsExcel, txt_file, 7, 5)
