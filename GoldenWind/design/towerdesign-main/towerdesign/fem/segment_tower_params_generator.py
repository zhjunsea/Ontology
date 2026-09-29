"""
V3.0版本：
1、修改程序架构，处理成标准函数，便于计算平台集成
V2.0版本:
1、修改端部夹块结构，在内侧增加两个距离为非加密螺栓距离的螺栓孔位，便于安装夹块临时固定用工艺螺栓；
2、考虑锥段影响下的纵法兰长度计算；
待补充：疲劳单位载荷6、7工况生成
"""
import openpyxl
import csv
import os
import math
from pulp import LpProblem, LpVariable, LpMinimize, value, LpStatus,PULP_CBC_CMD
from pulp import LpInteger


class Seg_tower_cal_files_gen:
    '''生成分片塔ansys计算输入文件，包含：1、结构参数生成函数para_gen 2、极限载荷生成函数load_gen'''

    def __init__(self, input_tower_geo, input_ult_load,para_store_path ,load_store_path  ):
        self.input_tower_geo=input_tower_geo
        self.input_ult_load=input_ult_load
        self.para_store_path = para_store_path
        self.load_store_path = load_store_path
    def para_gen(self):
        #导入塔架主体数据，筒节、法兰及纵法兰sheet
        tower_gem_bk = openpyxl.load_workbook(self.input_tower_geo, data_only=True)
        shell_gem_sht = tower_gem_bk['TowerGeo']
        shells_max_row = function_actual_max_row(shell_gem_sht)
        shells_cell = shell_gem_sht[(shell_gem_sht.cell(row=2, column=2)).coordinate:(
            shell_gem_sht.cell(row=shells_max_row, column=6)).coordinate]

        flan_gem_sht = tower_gem_bk['Flange']
        flan_max_row = function_actual_max_row(flan_gem_sht)
        flan_cell = flan_gem_sht[(shell_gem_sht.cell(row=3, column=1)).coordinate:(
            shell_gem_sht.cell(row=shells_max_row, column=16)).coordinate]

        v_fla_gem_sht = tower_gem_bk['V-Flange']
        v_flan_max_row = v_fla_gem_sht.max_row
        v_fla_cell = v_fla_gem_sht[(shell_gem_sht.cell(row=3, column=1)).coordinate:(
            shell_gem_sht.cell(row=v_flan_max_row, column=9)).coordinate]

        # 生成所有分片塔段相应结构参数表
        for num_sec in range(0, v_flan_max_row - 2, 1):
            # 筒体几何sheet中查找,塔段顶部标高所在行数
            for j in range(shells_max_row - 1):
                if shells_cell[j][0].value != None:
                    if round(float(shells_cell[j][0].value), 3) == round(float(flan_cell[num_sec + 1][0].value), 3):
                        order_top = j
            # 筒体几何sheet中查找,塔段底部标高所在行数
            for j in range(shells_max_row - 1):
                if shells_cell[j][0].value != None:
                    if round(float(shells_cell[j][0].value), 3) == round(float(flan_cell[num_sec][0].value), 3):
                        order_bot = j
            ###定义几何csv，得到第一行及第一列顺序序号
            geo_csv = []
            for i in range(21):
                geo_csv.append([])
                for j in range(8):
                    geo_csv[i].append(0)
            for i in range(8):
                geo_csv[0][i] = i
            for j in range(21):
                geo_csv[j][0] = j

            ###纵法兰螺栓排布线性优化
            # 塔段设计参数输入
            height_section = (
                        shells_cell[order_top - 1][0].value - shells_cell[order_bot + 1][0].value)  # 剔除上下法兰后，塔段总高度
            radius_bot = 0.5 * 0.001 * (
                        shells_cell[order_bot + 1][1].value - shells_cell[order_bot + 1][4].value)  # 下法兰焊缝处，筒体半径（中径）
            radius_top = 0.5 * 0.001 * (
                        shells_cell[order_top - 1][1].value - shells_cell[order_top - 1][4].value)  # 上法兰焊缝处，筒体半径（中径）
            if radius_bot - radius_top == 0:
                radius_vfla_bot_end = round(1000 * radius_bot)  # 纵法兰下端部（剔除圆弧段）筒体中径，单位mm
                radius_vfla_top_end = round(1000 * radius_bot)  # 纵法兰上端部（剔除圆弧段）筒体中径，单位mm
            else:
                radius_vfla_bot_end = round(1000 * (radius_bot - 0.353 / height_section * (radius_bot - radius_top)))
                radius_vfla_top_end = round(1000 * (radius_top + 0.353 / height_section * (radius_bot - radius_top)))
            # print(radius_vfla_bot_end, radius_vfla_top_end)
            len_v_fla_total = round(1000 * ((height_section ** 2 + (radius_bot - radius_top) ** 2) ** 0.5 * (
                        height_section - 0.3) / height_section - 2 * 0.203))  # 纵法兰总长度（剔除圆弧段）=塔段总长-上下法兰高度-2*353
            space_end_bolts = 60  # 端部加密螺栓间距（受限于拉铆钉施工空间，d24拉铆钉对应间距60mm，d20拉铆钉对应间距50mm）
            space_middle_bolts = v_fla_cell[num_sec][3].value  # 中部非加密螺栓间距
            thick_v_fla = 25  # 纵法兰厚度
            dia_bolt_hole = v_fla_cell[num_sec][5].value  # 螺栓孔直径
            # len_block=85*12         #夹块长度，取值为非加密螺栓间距整数倍，考虑重量要求不宜大于1200mm
            min_num_mid_blocks = int(800 / space_middle_bolts)  # 夹块最小长度800mm，相应螺栓孔个数
            max_num_mid_blocks = int(1500 / space_middle_bolts)  # 夹块最大长度1500mm，相应螺栓孔个数

            bolt_array_para_list = []
            for i in range(min_num_mid_blocks, max_num_mid_blocks):
                bolt_array_para_list.append([])
                len_block = i * space_middle_bolts
                # 创建一个目标函数为最小化的线性规划问题
                model = LpProblem("纵法兰螺栓排布线性规划", LpMinimize)
                # 添加决策变量
                num_bolt_end = LpVariable('num_bolt_end', lowBound=0, cat=LpInteger)  # 端部加密螺栓个数
                num_blocks = LpVariable('num_blocks', lowBound=0, cat=LpInteger)  # 中部非加密段夹块个数
                z = LpVariable('z', lowBound=0, cat=LpInteger)  # 中部非加密段夹块包含的螺栓个数
                bolt_end_distance = LpVariable('bolt_end_distance', lowBound=0, cat='Continuous')  # 端部螺栓孔边距
                len_end_and_middle = LpVariable('len_end_and_middle', lowBound=0, cat='Continuous')  # 加密与非加密螺栓距离
                # 添加目标函数
                model += bolt_end_distance, "Z"
                # 添加约束条件
                model += 2 * bolt_end_distance + 2 * space_end_bolts * (
                            num_bolt_end - 1) + 2 * len_end_and_middle + len_block * num_blocks - space_middle_bolts + 4 * space_middle_bolts <= len_v_fla_total, "constraint 1"  # 各段长度加和等于纵法兰总长度，约束条件不能设置等于关系，为此设置两个约束条件。
                model += 2 * bolt_end_distance + 2 * space_end_bolts * (
                            num_bolt_end - 1) + 2 * len_end_and_middle + len_block * num_blocks - space_middle_bolts + 4 * space_middle_bolts >= len_v_fla_total, "constraint 2"
                # model += bolt_end_distance>=1.2*dia_bolt_hole, "constraint 3"#端部螺栓孔边距要求，根据欧标要求，>=1.2d0,<=4t+40
                model += bolt_end_distance >= 27, "constraint 3"
                model += bolt_end_distance <= 4 * thick_v_fla + 40, "constraint 4"
                model += bolt_end_distance <= space_end_bolts, "constraint 5"  # 端部螺栓孔边距要求，考虑工程上加密需求，<=端部加密螺栓间距
                model += len_end_and_middle >= space_end_bolts, "constraint 6"  # 加密与非加密螺栓组间距
                model += len_end_and_middle <= space_middle_bolts, "constraint 7"  # 加密与非加密螺栓组间距，最大间距不得大于非加密螺栓间距
                model += space_end_bolts * (
                        num_bolt_end - 1) <= 1500, "constraint 8"  # 加密段总长，最大值1200，如果规划遇到问题，可以适当增长长度，一般不建议大于1500mm
                model += space_end_bolts * (num_bolt_end - 1) >= 1000, "constraint 9"  # 加密段总长，最小值1000，一般不小于1000mm，
                # 求解问题
                model.solve(PULP_CBC_CMD(msg=False))  # 关闭Pulp的输出
                # model.solve(PULP_CBC_CMD())
                for variable in model.variables():
                    bolt_array_para_list[i - min_num_mid_blocks].append(variable.varValue)

            """线性规划中给出了夹块个数非整数的方案，将非整数方案剔除，
            最终方案中4个变量分别为：端部螺栓孔边距bolt_end_distance、加密与非加密螺栓间距len_end_and_middle、
            中部夹块个数num_blocks、端部螺栓孔个数num_bolt_end"""

            # 初始化结果列表
            filtered_bolt_list = []
            optimal_bolt_list = []
            for item in bolt_array_para_list:
                # 缓存模运算结果
                mod_check_2 = item[2] % 1 == 0
                mod_check_3 = item[3] % 1 == 0
                # 如果满足条件，添加到中间列表
                if mod_check_2 and mod_check_3:
                    filtered_bolt_list.append(item)

            # 筛选子列表中第一个元素最小的子列表，如果第一个元素有多个子列表，再次判断，从其中找到子列表第3个元素数值最小的子列表
            min_first_element = min(filtered_bolt_list, key=lambda x: x[0])
            min_first_elements = [item for item in filtered_bolt_list if item[0] == min_first_element[0]]
            optimal_bolt_list = min(min_first_elements, key=lambda x: x[2])
            # print("Optimal bolt list:", optimal_bolt_list)

            ##第1列标高数据
            geo_csv[1][1] = 0
            geo_csv[2][1] = flan_cell[num_sec][4].value  # （2，1）下法兰厚度
            for row1 in range(3, order_top - order_bot + 2):
                geo_csv[row1][1] = 1000 * (shells_cell[row1 - 2 + order_bot][0].value - shells_cell[order_bot][0].value)
            geo_csv[order_top - order_bot + 2][1] = flan_cell[num_sec + 1][6].value + \
                                                    geo_csv[order_top - order_bot + 1][1]  # （order_top + 2，1）上法兰焊缝所在标高
            geo_csv[order_top - order_bot + 3][1] = flan_cell[num_sec + 1][4].value + \
                                                    geo_csv[order_top - order_bot + 2][1]  # （order_top + 3，1）上法兰上端面所在标高

            ##第2列中径数据及第3列壁厚数据
            # 底法兰的3个中径数据
            for row in range(1, 4):
                geo_csv[row][2] = flan_cell[num_sec][1].value - flan_cell[num_sec][5].value
                geo_csv[row][3] = flan_cell[num_sec][5].value
            geo_csv[3][3] = shells_cell[order_bot + 1][4].value
            # 中间筒体下标高的中径数据
            for row in range(4, order_top - order_bot + 2):
                geo_csv[row][2] = shells_cell[row - 2 + order_bot][1].value - shells_cell[row - 2 + order_bot][4].value
                geo_csv[row][3] = shells_cell[row - 2 + order_bot][4].value
            for row in range(order_top - order_bot + 2, order_top - order_bot + 4):
                geo_csv[row][2] = flan_cell[num_sec + 1][1].value - flan_cell[num_sec + 1][5].value
                geo_csv[row][3] = flan_cell[num_sec + 1][5].value

            ##第4列塔段长度数据
            geo_csv[2][4] = geo_csv[order_top - order_bot + 3][1]
            if flan_cell[num_sec][13].value is None:  # 判断下法兰为T，或者L型
                geo_csv[3][4] = 0  # 下法兰为L型
            else:
                geo_csv[3][4] = 1  # 下法兰为T型
            geo_csv[4][4] = 0

            ##第5列下法兰及上法兰内径
            geo_csv[1][5] = flan_cell[num_sec][2].value
            geo_csv[2][5] = flan_cell[num_sec + 1][2].value

            ##第6列下法兰及上法兰厚度
            geo_csv[1][6] = flan_cell[num_sec][4].value
            geo_csv[2][6] = flan_cell[num_sec + 1][4].value

            ##第7列塔架门洞数据-仅用于纵法兰螺栓排布参数
            geo_csv[1][7] = v_fla_cell[num_sec][1].value  # 纵法兰厚度
            geo_csv[2][7] = v_fla_cell[num_sec][3].value  # 非加密螺栓间距
            geo_csv[3][7] = v_fla_cell[num_sec][5].value  # 螺栓孔直径
            geo_csv[4][7] = space_end_bolts  # 端部加密螺栓孔间距
            geo_csv[5][7] = optimal_bolt_list[0]  # 端部螺栓孔边距bolt_end_distance
            geo_csv[6][7] = optimal_bolt_list[1]  # 加密与非加密螺栓间距len_end_and_middle
            geo_csv[7][7] = optimal_bolt_list[2]  # 中部夹块个数num_blocks
            geo_csv[8][7] = optimal_bolt_list[3]  # 端部螺栓孔个数num_bolt_end
            geo_csv[9][7] = len_v_fla_total  # 纵法兰总长度
            geo_csv[10][7] = (len_v_fla_total - 2 * geo_csv[5][7] - 2 * geo_csv[4][7] * (geo_csv[8][7] - 1) - 2 *
                              geo_csv[6][7] + geo_csv[2][7] - 4 * geo_csv[2][7]) / geo_csv[7][7]  # 中部夹块长度
            geo_csv[11][7] = radius_vfla_bot_end  # 纵法兰下端部（剔除圆弧段）筒体中径（半径），单位mm
            geo_csv[12][7] = radius_vfla_top_end  # 纵法兰上端部（剔除圆弧段）筒体中径（半径），单位mm

            ##第1、2、3列终止符
            for col in range(1, 4):
                geo_csv[order_top - order_bot + 4][col] = -1

            ##在当前工作目录下创建csv格式塔架强度计算用主体数据
            name_csv = 'design_tower_para_sec' + str(num_sec + 1) + '.csv'
            para_file_path = os.path.join(self.para_store_path, name_csv)
            print(para_file_path)
            with open(para_file_path, 'w', newline='', encoding='utf-8') as outfile_geo:
                new_para_writer = csv.writer(outfile_geo)
                for row in range(0, 21, 1):
                    new_para_writer.writerow(geo_csv[row])

    def load_gen(self):     #生成分片塔各分段对应极限载荷表（txt格式）
        # 导入塔架各截面极限载荷
        ult_bk = openpyxl.load_workbook(self.input_ult_load)
        ult_sheet = ult_bk['tower_section']
        ult_maxrow = ult_sheet.max_row
        ult_cells = ult_sheet[
                    (ult_sheet.cell(row=1, column=1)).coordinate:(
                        ult_sheet.cell(row=ult_maxrow, column=11)).coordinate]

        # 导入塔架主体数据，筒节、法兰及纵法兰sheet
        tower_gem_bk = openpyxl.load_workbook(self.input_tower_geo, data_only=True)
        shell_gem_sht = tower_gem_bk['TowerGeo']
        shells_max_row = function_actual_max_row(shell_gem_sht)
        shells_cell = shell_gem_sht[(shell_gem_sht.cell(row=2, column=2)).coordinate:(
            shell_gem_sht.cell(row=shells_max_row, column=6)).coordinate]

        flan_gem_sht = tower_gem_bk['Flange']
        # flan_max_row = function_actual_max_row(flan_gem_sht)
        flan_cell = flan_gem_sht[(shell_gem_sht.cell(row=3, column=1)).coordinate:(
            shell_gem_sht.cell(row=shells_max_row, column=16)).coordinate]

        v_fla_gem_sht = tower_gem_bk['V-Flange']
        v_flan_max_row = v_fla_gem_sht.max_row
        # v_fla_cell = v_fla_gem_sht[(shell_gem_sht.cell(row=3, column=1)).coordinate:(shell_gem_sht.cell(row=v_flan_max_row, column=9)).coordinate]

        ##定义载荷csv,创建二维列表,获取表格第一行及第一列顺序序号
        load = []
        for i in range(17):
            load.append([])
            for j in range(9):
                load[i].append(0)
        for i in range(9):
            load[0][i] = i
        for j in range(17):
            load[j][0] = j

        ## 极限载荷表中中所有下标高数据循环，写入列表ult_level_list
        ult_level_list = []
        for row in range(0, shells_max_row - 1):
            str_ult_level = ult_cells[18 * row][0].value
            new_str_ult_level = str_ult_level.replace('tower_section-----', "")
            ult_level_list.append(float(new_str_ult_level))

        for num_sec in range(0, v_flan_max_row - 2, 1):
            # 筒体几何sheet中查找,塔段顶部标高所在行数
            for j in range(shells_max_row - 1):
                if shells_cell[j][0].value != None:
                    if round(float(shells_cell[j][0].value), 3) == round(float(flan_cell[num_sec + 1][0].value), 3):
                        order_top = j
            # 筒体几何sheet中查找,塔段底部标高所在行数
            for j in range(shells_max_row - 1):
                if shells_cell[j][0].value != None:
                    if round(float(shells_cell[j][0].value), 3) == round(float(flan_cell[num_sec][0].value), 3):
                        order_bot = j
            # 确定塔段下标高后，读取该标高相应的极限载荷，赋值给二维列表load
            min_level_shell = float(round(shells_cell[order_bot][0].value, 3))
            if min_level_shell in ult_level_list:
                index = ult_level_list.index(min_level_shell)
                for i in range(1, 17):
                    for j in range(1, 9):
                        load[i][j] = float(ult_cells[18 * index + 1 + i][j + 1].value)

                load_opera = []
                load_opera.append(load[0])  # 表头
                load_opera.append(load[1])  # Mx_max工况
                load_opera.append(load[5])  # Mxy_max工况
                load_opera.append(load[7])  # Mz_max工况
                load_opera.append(load[8])  # Mx_min工况
                load_opera.append(load[13])  # Fxy_max工况

                for i in range(len(load_opera)):  # 为load_opera修改序号
                    load_opera[i][0] = i
                # print(load_opera)
                max_abs_Mz = max(abs(row[4]) for row in load_opera if len(row) > 4)
                round_max_abs_Mz=Rounding_envelope(max_abs_Mz)
                # print(max_abs_Mz,round_max_abs_Mz)
                load_case_6=[6,0,0,0,-1*round_max_abs_Mz,0,0,0,0]          #6、7工况，计算Mz疲劳载荷下螺栓疲劳，Mz单位载荷数值
                load_case_7 = [7, 0, 0, 0,  round_max_abs_Mz, 0, 0, 0, 0]
                load_opera.append(load_case_6)
                load_opera.append(load_case_7)

                name_ult_txt = 'ultimate_load_tower_bottom_sec' + str(num_sec + 1) + '.txt'
                load_file_path = os.path.join(self.load_store_path, name_ult_txt)
                with open(load_file_path, 'w', encoding='utf-8') as f:
                    for i in load_opera:
                        if not i:  # 处理空子列表的情况
                            f.write('\n')
                            continue
                        # 使用 join 方法避免多余的制表符，并确保每行末尾有一个换行符
                        f.write('\t'.join(str(x) for x in i) + '\n')
            else:
                # '''比对极限载荷标高与塔架筒节标高是否一致，不一致时输出标高位置并终止程序'''
                print('极限载荷与塔架筒体数据的塔底标高不匹配，请检查')

def function_actual_max_row(sheet_name):###excel 指定sheet_name表中，返回含有有效数据的最大行数
    max_row=sheet_name.max_row
    actual_max_row = 0
    for row in sheet_name.iter_rows(min_row=1, max_row=max_row, values_only=True):
        if any(cell is not None for cell in row):
            actual_max_row += 1
    return actual_max_row

def Rounding_envelope(n):  #载荷包络并次高位数向大数取整
    if n == 0:
        return 1
    else:
        n = abs(n)  # 忽略负号
        if isinstance(n, float):
            n = math.ceil(n)  # 如果是浮点数，取大值取整
        num_dig=len(str(int(n)))   #数字位数
        n=int(math.ceil(n/10**(num_dig-2)))*10**(num_dig-2)
        return n


if __name__ == '__main__':
    input_ult_load = r'D:\塔架计算平台\21.分片塔有限元工具\GWH170P7200TG140_D0BGW83.GWH182P7500TG140_XXXXBSi90.2_chile_20240823_G1_m10_ultimate_loads_tower_section_1724650212649_safeFactor.xlsx'
    input_tower_geo = r'D:\塔架计算平台\21.分片塔有限元工具\xxxx_towergeoinput_140m_6段分片塔_中径5950mm_gw171-6.25mw_外置_锚栓-主体578.8t-20240318.xlsx'
    para_store_path = r'D:\塔架计算平台\21.分片塔有限元工具'
    load_store_path = r'D:\塔架计算平台\21.分片塔有限元工具'
    test_para_gen=Seg_tower_cal_files_gen(input_tower_geo,input_ult_load,para_store_path,load_store_path).para_gen()


