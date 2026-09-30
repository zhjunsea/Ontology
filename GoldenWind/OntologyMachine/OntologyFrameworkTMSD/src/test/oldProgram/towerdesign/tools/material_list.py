# -*- coding: utf-8 -*-
"""
备料清单生成模块
从 calculateResult.xlsx 提取筒节信息和钢材等级，生成备料清单
"""
import openpyxl


# 国标钢材等级 → 欧标转换映射
GB_TO_EN = {
    'Q235B': 'S235JR', 'Q235C': 'S235J0', 'Q235D': 'S235J2',
    'Q355B': 'S355JR',
    'Q355C': 'S355J0', 'Q355D': 'S355J2', 'Q355ND': 'S355NL', 'Q355NE': 'S355NL',
    'Q355E': 'S355K2',
    'Q390C': 'S390J0', 'Q390D': 'S390J2',
    'Q420C': 'S420J0', 'Q420D': 'S420J2',
    'Q460C': 'S460J0', 'Q460D': 'S460J2',
}


def convert_steel_grade(gb_grade):
    """国标钢材等级转欧标
    Args:
        gb_grade: 国标等级，如 'Q355D'
    Returns:
        欧标等级，如 'S355J2'，未匹配则原样返回
    """
    if not gb_grade:
        return ''
    grade = str(gb_grade).strip()
    return GB_TO_EN.get(grade, grade)


def extract_material_list(filepath):
    """从 calculateResult.xlsx 提取备料清单数据

    Args:
        filepath: calculateResult.xlsx 文件路径

    Returns:
        dict: {
            'shell_rows': [  # 筒节数据
                {'name': 'S1-1', 'd_bottom': 5018, 'd_top': 5018,
                 'height': 2660, 'thickness': 68, 'steel_grade': 'S355J2'},
                ...
            ],
            'extra_rows': [  # 法兰/门框/加强环
                {'name': '法兰', 'steel_grade': 'S355NL+Z35'},
                ...
            ]
        }
    """
    wb = openpyxl.load_workbook(filepath, data_only=True)

    # 校验必需的 sheet
    required = ['StaticStrength-nosf', 'Buckling']
    for s in required:
        if s not in wb.sheetnames:
            raise ValueError(f'缺少必需的Sheet: {s}')

    # 1. 从 Buckling 提取每个截面的标高、外径
    buckling_rows = _extract_buckling_data(wb['Buckling'])

    # 2. 从 StaticStrength-nosf 提取每个截面的钢材等级（P列），空白行=法兰
    sections = _extract_sections_with_grades(wb['StaticStrength-nosf'])

    # 3. 构建标高→外径映射（从Buckling）
    od_map = {}
    for r in buckling_rows:
        od_map[round(r['z'], 3)] = r['od']

    # 4. 按法兰分段，组装筒节数据
    shell_rows = _build_shell_rows_v2(sections, od_map)

    # 5. 追加法兰、门框、加强环
    extra_rows = [
        {'name': '法兰', 'steel_grade_cn': 'Q355NEZ35', 'steel_grade_en': 'S355NLZ35'},
        {'name': '门框（若有）', 'steel_grade_cn': 'Q355NEZ35', 'steel_grade_en': 'S355NLZ35'},
        {'name': '加强环（若有）', 'steel_grade_cn': 'Q355NEZ35', 'steel_grade_en': 'S355NLZ35'},
    ]

    return {
        'shell_rows': shell_rows,
        'extra_rows': extra_rows,
    }


def _extract_flange_heights(ws):
    """从 Flange&Bolt sheet 提取法兰标高列表
    第2行是表头，第3行开始是数据，A列是法兰标高(m)
    遇到非数字行（如 'Data of Strength Analysis'）停止
    """
    heights = []
    for row in ws.iter_rows(min_row=3, max_row=ws.max_row, values_only=True):
        val = row[0]
        if val is None or not isinstance(val, (int, float)):
            break
        heights.append(float(val))
    return heights


def _extract_buckling_data(ws):
    """从 Buckling sheet 提取截面数据
    第2行是表头，第3行开始是数据
    A列=标高z(m), B列=壁厚t(mm), C列=外径OD(mm)
    """
    rows = []
    for row in ws.iter_rows(min_row=3, max_row=ws.max_row, values_only=True):
        z = row[0]
        if z is None or not isinstance(z, (int, float)):
            break
        thickness = row[1]
        od = row[2]
        rows.append({
            'z': float(z),
            'thickness': round(float(thickness), 1) if thickness else 0,
            'od': round(float(od), 1) if od else 0,
        })
    return rows


def _extract_sections_with_grades(ws):
    """从 StaticStrength-nosf sheet 提取所有截面，标记哪些是法兰
    第1行是表头，第2行开始是数据
    A列=标高z(m), B列=壁厚t(mm), P列(index 15)=Allowable steel class
    P列空白 = 法兰位置

    Returns:
        list of dict: [{'z': 0.65, 'thickness': 68, 'grade': 'Q355D', 'is_flange': False}, ...]
    """
    sections = []
    for row in ws.iter_rows(min_row=2, max_row=ws.max_row, values_only=True):
        z = row[0]
        if z is None or not isinstance(z, (int, float)):
            break
        thickness = row[1] if len(row) > 1 else None
        grade = row[15] if len(row) > 15 else None
        is_flange = (grade is None or str(grade).strip() == '')
        sections.append({
            'z': float(z),
            'thickness': round(float(thickness), 1) if thickness else 0,
            'grade': str(grade).strip() if grade else None,
            'is_flange': is_flange,
        })
    return sections


def _build_shell_rows_v2(sections, od_map):
    """组装筒节数据（v2：用P列空白判断法兰）
    法兰位置有连续两行空白：第一行是上一段的结束截面，第二行是下一段的起点前
    只在同一段内相邻截面之间生成筒节
    """
    if not sections:
        return []

    # 标记连续法兰行：连续空白中第一行保留给上一段，后续行作为分段点
    # 先找出所有法兰行的索引
    flange_indices = [i for i, sec in enumerate(sections) if sec['is_flange']]

    # 找出连续法兰组，每组中只有最后一行作为真正的分段点，前面的保留给上一段
    split_indices = set()  # 真正的分段点（跳过的行）
    keep_indices = set()   # 法兰组中保留给上一段的行

    i = 0
    while i < len(flange_indices):
        # 找连续法兰组
        group = [flange_indices[i]]
        while i + 1 < len(flange_indices) and flange_indices[i + 1] == flange_indices[i] + 1:
            i += 1
            group.append(flange_indices[i])
        # 组内第一行保留给上一段（作为结束截面），其余行作为分段点跳过
        if len(group) >= 2:
            keep_indices.add(group[0])
            for idx in group[1:]:
                split_indices.add(idx)
        else:
            # 单行法兰（如底法兰），作为分段点
            split_indices.add(group[0])
        i += 1

    # 按分段点切分，keep_indices的行当作普通截面保留
    segments = []
    current_seg = []
    for i, sec in enumerate(sections):
        if i in split_indices:
            if current_seg:
                segments.append(current_seg)
                current_seg = []
        elif i in keep_indices:
            # 法兰组的第一行，保留给上一段作为结束截面
            current_seg.append(sec)
        else:
            current_seg.append(sec)
    if current_seg:
        segments.append(current_seg)

    # 查找外径的辅助函数
    def get_od(z):
        key = round(z, 3)
        if key in od_map:
            return od_map[key]
        min_dist = float('inf')
        nearest_od = 0
        for k, v in od_map.items():
            dist = abs(k - z)
            if dist < min_dist:
                min_dist = dist
                nearest_od = v
        return nearest_od

    shell_rows = []
    for seg_idx, seg_sections in enumerate(segments):
        seg_num = seg_idx + 1
        for i in range(len(seg_sections) - 1):
            curr = seg_sections[i]
            next_sec = seg_sections[i + 1]

            z_bottom = curr['z']
            z_top = next_sec['z']
            height = round((z_top - z_bottom) * 1000)
            thickness = curr['thickness']
            d_bottom = get_od(z_bottom)
            d_top = get_od(z_top)

            gb_grade = curr['grade'] or next_sec['grade'] or ''
            en_grade = convert_steel_grade(gb_grade)

            shell_rows.append({
                'name': f'S{seg_num}-{i + 1}',
                'd_bottom': d_bottom,
                'd_top': d_top,
                'height': height,
                'thickness': thickness,
                'steel_grade_cn': gb_grade,
                'steel_grade_en': en_grade,
            })

    return shell_rows


def _extract_steel_grades(ws):
    """从 StaticStrength-nosf sheet 提取钢材等级
    第1行是表头，第2行开始是数据
    A列=标高z(m), P列(index 15)=Allowable steel class
    返回 {标高: 钢材等级} 的映射
    """
    grade_map = {}
    for row in ws.iter_rows(min_row=2, max_row=ws.max_row, values_only=True):
        z = row[0]
        if z is None or not isinstance(z, (int, float)):
            break
        # P列 = index 15
        grade = row[15] if len(row) > 15 else None
        if grade:
            grade_map[round(float(z), 3)] = str(grade).strip()
    return grade_map


def _find_grade_for_section(z_bottom, z_top, grade_map):
    """查找某个筒节对应的钢材等级
    取筒节范围内最严格（最高等级字母）的钢材等级
    如果范围内没有数据，取最近的
    """
    candidates = []
    for z, grade in grade_map.items():
        if z_bottom - 0.01 <= z <= z_top + 0.01:
            candidates.append(grade)

    if candidates:
        # 取第一个非空的（通常同一筒节内等级一致）
        return candidates[0]

    # 没找到，取最近的
    min_dist = float('inf')
    nearest = ''
    mid = (z_bottom + z_top) / 2
    for z, grade in grade_map.items():
        dist = abs(z - mid)
        if dist < min_dist:
            min_dist = dist
            nearest = grade
    return nearest


def _build_shell_rows(buckling_rows, grade_map, flange_heights):
    """组装筒节数据
    用法兰标高分段，相邻两个截面之间构成一个筒节
    """
    if not buckling_rows:
        return []

    shell_rows = []
    seg_num = 1  # 当前段号
    sec_num = 1  # 段内筒节序号
    flange_idx = 1  # 下一个要检查的法兰（跳过第一个底法兰）

    for i in range(len(buckling_rows) - 1):
        curr = buckling_rows[i]
        next_row = buckling_rows[i + 1]

        z_bottom = curr['z']
        z_top = next_row['z']

        # 检查是否跨过法兰（进入下一段）
        if flange_idx < len(flange_heights):
            fl_h = flange_heights[flange_idx]
            # 如果当前截面标高 >= 法兰标高，说明进入了新段
            if z_bottom >= fl_h - 0.01:
                seg_num += 1
                sec_num = 1
                flange_idx += 1

        # 筒节高度 (mm)
        height = round((z_top - z_bottom) * 1000)

        # 壁厚取当前截面的
        thickness = curr['thickness']

        # 外径
        d_bottom = curr['od']
        d_top = next_row['od']

        # 钢材等级：从 grade_map 查找，国标转欧标
        gb_grade = _find_grade_for_section(z_bottom, z_top, grade_map)
        en_grade = convert_steel_grade(gb_grade)

        shell_rows.append({
            'name': f'S{seg_num}-{sec_num}',
            'd_bottom': d_bottom,
            'd_top': d_top,
            'height': height,
            'thickness': thickness,
            'steel_grade': en_grade,
        })

        sec_num += 1

    return shell_rows


def export_material_list_excel(data, output_path):
    """将备料清单数据导出为 Excel 文件（完整版）

    Args:
        data: extract_material_list 返回的 dict
        output_path: 输出文件路径，如 'D:/output/备料清单.xls'
    """
    import xlwt

    wb = xlwt.Workbook(encoding='utf-8')
    ws = wb.add_sheet('备料清单')

    # 表头样式
    header_style = xlwt.easyxf(
        'font: bold on; '
        'alignment: horiz centre, vert centre; '
        'borders: left thin, right thin, top thin, bottom thin; '
        'pattern: pattern solid, fore_colour light_yellow;'
    )
    # 数据样式
    cell_style = xlwt.easyxf(
        'alignment: horiz centre, vert centre; '
        'borders: left thin, right thin, top thin, bottom thin;'
    )

    headers = ['筒节', '下端直径(mm)', '上端直径(mm)', '筒节高度(mm)', '筒节壁厚(mm)', '钢材牌号(国内)', '钢材牌号(国际)']
    for col, h in enumerate(headers):
        ws.write(0, col, h, header_style)

    row_idx = 1
    for r in data.get('shell_rows', []):
        ws.write(row_idx, 0, r['name'], cell_style)
        ws.write(row_idx, 1, r['d_bottom'], cell_style)
        ws.write(row_idx, 2, r['d_top'], cell_style)
        ws.write(row_idx, 3, r['height'], cell_style)
        ws.write(row_idx, 4, r['thickness'], cell_style)
        ws.write(row_idx, 5, r.get('steel_grade_cn', ''), cell_style)
        ws.write(row_idx, 6, r.get('steel_grade_en', ''), cell_style)
        row_idx += 1

    for r in data.get('extra_rows', []):
        ws.write(row_idx, 0, r['name'], cell_style)
        ws.write(row_idx, 1, '', cell_style)
        ws.write(row_idx, 2, '', cell_style)
        ws.write(row_idx, 3, '', cell_style)
        ws.write(row_idx, 4, '', cell_style)
        ws.write(row_idx, 5, r.get('steel_grade_cn', ''), cell_style)
        ws.write(row_idx, 6, r.get('steel_grade_en', ''), cell_style)
        row_idx += 1

    wb.save(output_path)
    return output_path


def export_material_list_simple_excel(data, output_path):
    """将备料清单数据导出为 Excel 文件（简版：只有筒节、壁厚、钢材等级）

    Args:
        data: extract_material_list 返回的 dict
        output_path: 输出文件路径，如 'D:/output/备料清单_简版.xls'
    """
    import xlwt

    wb = xlwt.Workbook(encoding='utf-8')
    ws = wb.add_sheet('备料清单_简版')

    header_style = xlwt.easyxf(
        'font: bold on; '
        'alignment: horiz centre, vert centre; '
        'borders: left thin, right thin, top thin, bottom thin; '
        'pattern: pattern solid, fore_colour light_yellow;'
    )
    cell_style = xlwt.easyxf(
        'alignment: horiz centre, vert centre; '
        'borders: left thin, right thin, top thin, bottom thin;'
    )

    headers = ['筒节', '筒节壁厚(mm)', '钢材牌号']
    for col, h in enumerate(headers):
        ws.write(0, col, h, header_style)

    row_idx = 1
    for r in data.get('shell_rows', []):
        ws.write(row_idx, 0, r['name'], cell_style)
        ws.write(row_idx, 1, r['thickness'], cell_style)
        ws.write(row_idx, 2, r.get('steel_grade_en', ''), cell_style)
        row_idx += 1

    for r in data.get('extra_rows', []):
        ws.write(row_idx, 0, r['name'], cell_style)
        ws.write(row_idx, 1, '', cell_style)
        ws.write(row_idx, 2, r.get('steel_grade_en', ''), cell_style)
        row_idx += 1

    wb.save(output_path)
    return output_path


def generate_material_list(input_path, output_path=None):
    """一步到位：输入 calculateResult.xlsx 路径，输出备料清单 Excel 路径

    Args:
        input_path: calculateResult.xlsx 文件路径
        output_path: 完整版输出路径，默认在输入文件同目录下生成

    Returns:
        tuple: (完整版路径, 简版路径)
    """
    import os

    dir_name = os.path.dirname(input_path)
    if output_path is None:
        output_path = os.path.join(dir_name, '备料清单.xls')

    simple_path = os.path.splitext(output_path)[0] + '_简版.xls'

    data = extract_material_list(input_path)
    export_material_list_excel(data, output_path)
    export_material_list_simple_excel(data, simple_path)
    print(f'备料清单已保存: {output_path}')
    print(f'备料清单(简版)已保存: {simple_path}')
    return output_path, simple_path


if __name__ == '__main__':
    filepath = r'D:\microflask\beiliaokaifa\TowerGeoInput-calculateResult.xlsx'
    output = generate_material_list(filepath)
