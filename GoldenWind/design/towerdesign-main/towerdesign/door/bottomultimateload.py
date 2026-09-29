import os.path

import openpyxl

def read_excel_range_and_write_to_txt(excel_file, start_row, start_col, end_row, end_col):
    try:
        txt_file = os.path.join(os.path.dirname(excel_file), 'ultimate_load_tower_base.txt')
        # 打开 Excel 文件
        workbook = openpyxl.load_workbook(excel_file, data_only=True)
        sheet = workbook.active  # 假设使用活动工作表
        # 打开或创建文本文件
        with open(txt_file, 'w', encoding='utf-8') as txt:
            # 写入表头
            header = [f"{i}" for i in range(end_col - start_col + 2)]
            txt.write("\t".join(header) + "\n")

            # 写入数据
            for row_num in range(start_row, end_row + 1):
                row_data = []
                for col_num in range(start_col, end_col + 1):
                    cell_value = sheet.cell(row=row_num, column=col_num).value
                    row_data.append(str(cell_value) if cell_value is not None else "")
                txt.write(f"{row_num - start_row + 1}\t" + "\t".join(row_data) + "\n")
        return txt_file
    except Exception as e:
        return None

if __name__ == "__main__":
    # 示例调用
    excel_file = r'D:\towerdesignrelated\flasktest\00.开发\01.生成塔底载荷txt\3ultimate_loads_tower_sf_section.xlsx'  # 替换为你的 Excel 文件路径
    start_row = 3
    start_col = 3  # 列 C 对应的列号
    end_row = 18
    end_col = 10   # 列 J 对应的列号

    read_excel_range_and_write_to_txt(excel_file, start_row, start_col, end_row, end_col)
