#把计算好的值写入到excel中
#塔架数据表
import os.path

#把中间的工况复制到塔架数据表中
import pandas as pd
from openpyxl import load_workbook
from towerdesign.tools.tool import Creo_tool
import sys



def copy_loadcase2_towergeo(source_file,target_file):
    # 工作表名称
    source_sheet_name = 'Buckling'
    target_sheet_name = 'TowerGeo'

    # 读取源文件中的数据
    source_df = pd.read_excel(source_file, sheet_name=source_sheet_name, skiprows=0)

    # 需要复制的列索引
    column_index = 12  # 第13列（索引从0开始）

    # 提取列数据
    source_data = source_df.iloc[:, column_index]

    # 加载目标文件中的工作表
    book = load_workbook(target_file)
    writer = pd.ExcelWriter(target_file, engine='openpyxl', mode='a')
    writer.book = book

    # 获取目标工作表
    target_sheet = book[target_sheet_name]

    # 更新目标工作表中的特定列数据
    for idx, value in enumerate(source_data, start=1):  # start=1以适应跳过的第0行
        target_sheet.cell(row=idx, column=14, value=value)  # 写入到第14列

    # 保存修改
    book.save(target_file)
    book.close()
#计算结果表

def real_max_row(worksheet):
    for row in worksheet.iter_rows(min_row=1, max_row=worksheet.max_row, min_col=2, max_col=3):
        for cell in row:
            if cell.value is None:
                return row[0].row - 1
    return worksheet.max_row

def find_interval(a, b):
    #a=[]
    #b=value
    for i in range(len(a) - 1):
        if a[i] <= b < a[i + 1]:  # 检查b是否在a[i]和a[i+1]之间
            return i + 1
        elif b == a[-1]:#顶段顶法兰处
            return len(a)-1
    return None  # 如果b不在任何区间内，返回None




def convert_loadcase(m):
    if m == 1:
        return 'MaxMx'
    elif m == 3:
        return 'MaxMy'
    elif m == 4:
        return 'MinMy'
    elif m == 5:
        return 'MaxMxy'
    elif m == 9:
        return 'MaxFx'
    elif m == 10:
        return 'MinFx'
    elif m == 13:
        return 'MaxFxy'
    else:
        return None



def get_fem_result(fem_worksheet):
    result_dict = {}
    for i in range(2, 76 + 1):#76是固定的，可以改一下
        tjth = fem_worksheet.cell(i, 1).value#图样代号：60.00.02699-1
        if tjth:#如果不是None
            # 分割字符串
            th_part = tjth.split("-")[0]#图号
            d_part = tjth.split("-")[1]#段数
            mvalue = fem_worksheet.cell(i, 3).value

            mvalue = convert_loadcase(mvalue)

            rRcr = fem_worksheet.cell(i, 4).value
            rRpl = fem_worksheet.cell(i, 5).value
            # 检查并添加键值对
            if th_part not in result_dict:
                result_dict[th_part] = {}

            if d_part not in result_dict[th_part]:
                result_dict[th_part][d_part] = {}

            result_dict[th_part][d_part][mvalue] = {'rRcr': rRcr, 'rRpl': rRpl}
        else:
            pass
    return result_dict




def fill_fem_result(tower_worksheet, result_dict, th, maxrow, flangePos_list):
    #遍历塔架数据表
    for i in range(2, maxrow+1):
        #loc_pos = tower_worksheet.cell(i, 1).value
        loc_pos = i - 1

        #print(loc_pos)
        ds = find_interval(flangePos_list, loc_pos)#所在的段数
        try:
            get_rrcr = result_dict[th][str(ds)][tower_worksheet.cell(i, 14).value]['rRcr']
            get_rRpl = result_dict[th][str(ds)][tower_worksheet.cell(i, 14).value]['rRpl']

        except KeyError:
            get_rrcr = 1
            get_rRpl = 1
        #有些值没有，就赋值为1
        if get_rrcr is None:
            get_rrcr = 1
        if get_rRpl is None:
            get_rRpl = 1
        # if th == "10438168":
        #     print(ds)
        #     print([tower_worksheet.cell(i, 14).value])
        #     print(result_dict[th][str(ds)])
        #     print(get_rrcr)
        #     print(get_rRpl)
        tower_worksheet.cell(i, 15).value = round(get_rrcr,4)
        tower_worksheet.cell(i, 16).value = round(get_rRpl,4)
        #设置单元格显示小数点后4位
        tower_worksheet.cell(i, 15).number_format = '0.0000'
        tower_worksheet.cell(i, 16).number_format = '0.0000'

        # print(ds)
        # print(get_rrcr)
    #增加表头
    tower_worksheet.cell(1, 15).value = 'rRcr'
    tower_worksheet.cell(1, 16).value = 'rRpl'

def get_excel_files_by_folder(root_dir):
    result = []
    for root, dirs, files in os.walk(root_dir):
        # Create a list to store Excel file paths for each folder
        excel_files = []
        for file in files:
            if file.endswith('.xls') or file.endswith('.xlsx'):
                excel_files.append(os.path.join(root, file))
        # If there are exactly 2 Excel files in the current folder, add that list to the result
        if len(excel_files) >= 2:
            result.append({
                "folder_path": root,
                "excel_paths": excel_files
            })

    return result

if __name__ == '__main__':
    # 文件路径，项目的文件，塔架数据表，载荷
    root_dir = r'D:\塔架计算平台\34.新屈曲计算方法\除10个塔型外的其他数据\项目'
    excel_files_grouped_by_folders = get_excel_files_by_folder(root_dir)
    print(excel_files_grouped_by_folders)
    for group in excel_files_grouped_by_folders:
        print(f"Folder Path: {group['folder_path']}")
        for excel_path in group['excel_paths']:
            if 'TowerGeoInput' in excel_path and 'calculateResult' not in excel_path:
                towerGeoExcelName = excel_path
            elif excel_path.endswith('calculateResult-25.xlsx'):
                write2ExcelName = excel_path
        print(towerGeoExcelName)
        print(write2ExcelName)
        rootdir = group['folder_path']


        #图号
        th = os.path.basename(rootdir)
        # th = '60.00.02699'


        #有限元计算出来的两个参数的汇总值
        fem_file = r'D:\塔架计算平台\34.新屈曲计算方法\除10个塔型外的其他数据\2017非线性屈曲塔型统计.xlsx'
        #复制工况
        copy_loadcase2_towergeo(write2ExcelName,towerGeoExcelName)

        #实例化工具
        pp = Creo_tool(towerGeoExcelName)
        flangePos_list = pp.flangePos()#法兰位置
        if th == '60.00.03895':
            print(flangePos_list)
            print(pp.flSecHqty())

        workbook = load_workbook(towerGeoExcelName, data_only=True)
        tower_worksheet = workbook['TowerGeo']
        maxrow = real_max_row(tower_worksheet)
        print(maxrow)

        #处理fem结果
        fem_worksheet = load_workbook(fem_file, data_only=True)['除10个塔型外的其他数据']
        fem_maxrow = real_max_row(fem_worksheet)
        result_dict = get_fem_result(fem_worksheet)
        if th == '60.00.03895':
            print(result_dict)
            print(result_dict['60.00.03895']['1'])
            print(th)
        fill_fem_result(tower_worksheet, result_dict, th, maxrow, flangePos_list)
        #增加表头
        workbook.save(towerGeoExcelName)


    #有限元计算结果表
