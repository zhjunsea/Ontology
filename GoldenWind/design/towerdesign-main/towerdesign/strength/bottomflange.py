import openpyxl
from steeltowerdesign.Flange.topFlangeCheck import RED_fill
from steeltowerdesign.Flange.topFlangeFatigue import topFlangeFatigueCheck, markov_xlsx2dict
from steeltowerdesign.Flange.topFlangeFatigue import one_load_condition_damage
from steeltowerdesign.JupyterNotebook.project_item import elevation
from steeltowerdesign.TowerBM.KeyParameters import get_gamma_M_buckle_fatigue
from steeltowerdesign.tools.towerGeo2dict import tower_geo_2_dict
def extract_load_stress_data(file_path, sheet_name):
    workbook = openpyxl.load_workbook(file_path, data_only=True)
    worksheet = workbook[sheet_name]
    load_stress_json = {"inner_fillet": {},"outer_fillet": {},"inner_weld": {},"outer_weld": {}}
    for i in range(2, worksheet.max_row + 1):
        if worksheet.cell(i, 1).value is not None:
            position = worksheet.cell(i, 1).value
            value_My_list, S13_Luv_1_list, S13_Luv_2_list, S13_Lee_1_list, S13_Lee_2_list = ([] for _ in range(5))
            for j in range(i, worksheet.max_row + 1):
                if worksheet.cell(j, 2).value is not None:
                    value_My_list.append(worksheet.cell(j, 2).value)
                    S13_Luv_1_list.append(worksheet.cell(j, 3).value)
                    S13_Luv_2_list.append(worksheet.cell(j, 4).value)
                    S13_Lee_1_list.append(worksheet.cell(j, 5).value)
                    S13_Lee_2_list.append(worksheet.cell(j, 6).value)
                else:
                    break
            load_stress_json[position]['My'] = value_My_list
            load_stress_json[position]['S13_Luv_1'] = S13_Luv_1_list
            load_stress_json[position]['S13_Luv_2'] = S13_Luv_2_list
            load_stress_json[position]['S13_Lee_1'] = S13_Lee_1_list
            load_stress_json[position]['S13_Lee_2'] = S13_Lee_2_list
    return load_stress_json

def bottomFlangeResult2workbook(resultWorkBook, markov_xlsx, flange_geo_dict, loadstress, gamma_M_fatigue,DC_TFL_neck,DC_TFL_fillet, scf):
    worksheet = resultWorkBook['BotttomFlange']
    worksheet.append(["Ultimate Strength Results of Bottom Flange"])
    worksheet.merge_cells('A1:F1')
    worksheet.append(["location", "Stress Curve", "Load case", "Maximum Stress", "Design Stress", "SRF", "Dominant Curve"])
    worksheet.append(["————————————————————————————————————"])
    cell_number = worksheet.max_row
    worksheet.merge_cells('A' + str(cell_number) + ':G' + str(cell_number))
    worksheet.append(["Fatigue Results of Top Flange"])
    worksheet.merge_cells('A' + str(cell_number + 1) + ':G' + str(cell_number + 1))
    worksheet.append(["location", "Stress Curve", "DC", "△σD", "Damage", "Load case", "scf"])
    # 计算疲劳
    loc_markov_My_dict = (markov_xlsx2dict(markov_xlsx))['My']
    loc_markov_Mx_dict = (markov_xlsx2dict(markov_xlsx))['Mx']
    top_flange_fatigue_My = bottomFlangeFatigueCheck(loadstress, flange_geo_dict, loc_markov_My_dict,
                                                  gamma_M_fatigue=gamma_M_fatigue,
                                                  DC_TFL_neck=DC_TFL_neck, DC_TFL_fillet=DC_TFL_fillet, scf=scf)
    top_flange_fatigue_Mx = bottomFlangeFatigueCheck(loadstress, flange_geo_dict, loc_markov_Mx_dict,
                                                  gamma_M_fatigue=gamma_M_fatigue,
                                                  DC_TFL_neck=DC_TFL_neck, DC_TFL_fillet=DC_TFL_fillet, scf=scf)
    for location in top_flange_fatigue_My:
        DC = top_flange_fatigue_My[location]['DC']
        deltaSigmaD = top_flange_fatigue_My[location]['deltaSigmaD']
        for curve in top_flange_fatigue_My[location]['damage']:
            damage_My = top_flange_fatigue_My[location]['damage'][curve]
            damage_Mx = top_flange_fatigue_Mx[location]['damage'][curve]
            load_case_name = "My" if damage_My >= damage_Mx else "Mx"
            if location == "inner_fillet" or location == "outer_fillet":
                scf_cal = 1
            elif location == "inner_weld" or location == "outer_weld":
                scf_cal = scf
            worksheet.append([location, curve, DC, deltaSigmaD, max(damage_My, damage_Mx), load_case_name, scf_cal])
            if max(damage_My, damage_Mx) > 1:
                max_row = worksheet.max_row
                worksheet.cell(max_row, 5).fill = RED_fill
    return resultWorkBook


def bottomFlangeFatigueCheck(loadstress, flange_geo_dict, loc_markov_My_dict, gamma_M_fatigue, DC_TFL_neck, DC_TFL_fillet, scf):
    damage_dict = {}
    for location in loadstress:
        damage_dict.update({location: {}})
        # 01 获得DC
        DC_shell = DC_TFL_neck
        if location == "inner_fillet" or location == "outer_fillet":
            DC_shell = DC_TFL_fillet
            deltaSigmaC = DC_shell
            scf_cal = 1
        elif location == "inner_weld" or location == "outer_weld":
            DC_shell = DC_TFL_neck
            thickness_loc = flange_geo_dict['s']#法兰脖子的厚度
            ks = 1
            if thickness_loc >= 0.025:
                ks = (0.025 / thickness_loc) ** 0.2
            deltaSigmaC = DC_shell * ks
            scf_cal = scf
        deltaSigmaD = deltaSigmaC / gamma_M_fatigue * 0.4 ** (1 / 3)
        damage_dict[location].update({'deltaSigmaD': deltaSigmaD, 'DC': DC_shell, 'damage': {}})
        # 02 载荷及应力
        stress_curve_My_list = loadstress[location]['My']
        stress_name_list = list(loadstress[location].keys())
        stress_name_list.remove('My')
        for stress_name in stress_name_list:
            stress_list = loadstress[location][stress_name]
            damage = one_load_condition_damage(loc_markov_My_dict, stress_curve_My_list, stress_list, deltaSigmaD, scf_cal)
            damage_dict[location]['damage'].update({stress_name: damage})
    return damage_dict

def bottomFlangeResult2Excel(result_excel, tower_excel_name, markov_xlsx, loadstress, gamma_M_fatigue, DC_TFL_neck,DC_TFL_fillet, scf):
    FlangeGeo_dict_all = tower_geo_2_dict(tower_excel_name)['Flange']
    flange_geo_dict = next(iter(FlangeGeo_dict_all.items()))[1]#底法兰尺寸信息
    workbook = openpyxl.Workbook()
    worksheet = workbook.active
    worksheet.title = "BotttomFlange"
    workbook = bottomFlangeResult2workbook(workbook, markov_xlsx,flange_geo_dict,loadstress,gamma_M_fatigue, DC_TFL_neck,DC_TFL_fillet, scf)
    workbook.save(result_excel)

def midFlangeResult2Excel(result_excel, tower_excel_name, markov_xlsx, loadstress, gamma_M_fatigue, DC_TFL_neck,DC_TFL_fillet, scf, elevation_fl):
    FlangeGeo_dict_all = tower_geo_2_dict(tower_excel_name)['Flange']
    flange_geo_dict = FlangeGeo_dict_all[elevation_fl]
    workbook = openpyxl.Workbook()
    worksheet = workbook.active
    worksheet.title = "BotttomFlange"
    workbook = bottomFlangeResult2workbook(workbook, markov_xlsx,flange_geo_dict,loadstress,gamma_M_fatigue, DC_TFL_neck,DC_TFL_fillet, scf)
    workbook.save(result_excel)

if __name__ == '__main__':
    # import os
    # root_path = r'D:\塔架计算平台\12.底法兰疲劳计算有误'
    # # loadstressfile = r'D:\towerdesignrelated\flasktest\问题\load_stress_flange.xlsx'
    # # tower_excel_name = r'D:\towerdesignrelated\flasktest\问题\10482117_TowerGeoInput_120m5段标准分段-GW182-7.XMW_4450-513.73t-New.xlsx'
    # # markov_xlsx = r'D:\towerdesignrelated\flasktest\问题\Markov_T_000k650.xlsx'
    # loadstressfile = os.path.join(root_path, 'loadstressExcel.xlsx')
    # tower_excel_name = os.path.join(root_path, 'TowerGeoInput.xlsx')
    # markov_xlsx = os.path.join(root_path, 'Markov_T_000k650.xlsx')
    # loadstress = extract_load_stress_data(loadstressfile, 'Sheet1')
    # result_excel = r'D:\塔架计算平台\12.底法兰疲劳计算有误\result.xlsx'
    # # print(loadstress)
    # # print(loadstress['outer_weld']['S13_Lee_2'])
    # towerGeo_dict = tower_geo_2_dict(tower_excel_name)['Flange']
    # flange_geo = next(iter(towerGeo_dict.items()))[1]
    # print(flange_geo)
    # print("*****************")
    # design_standard= "IEC61400-1 Ed4"
    # DC_TFL_neck= 112
    # DC_TFL_fillet= 160
    # scf = 1.0
    # gamma_M_dict = get_gamma_M_buckle_fatigue(design_standard)
    # gamma_M_fatigue = gamma_M_dict['gamma_M_fatigue']
    # loadstress = extract_load_stress_data(loadstressfile, 'Sheet1')
    # try:
    #     # 尝试调用函数
    #     result = bottomFlangeResult2Excel(result_excel, tower_excel_name, markov_xlsx, loadstress, gamma_M_fatigue,
    #                                 DC_TFL_neck, DC_TFL_fillet, scf)
    # except SystemExit:
    #     print("错误！Markov矩阵中的最大载荷或最小载荷超出应力曲线范围.")

    import os
    root_path = r'D:\towerdesignrelated\flasktest\00.开发\05.中间法兰疲劳损伤计算'
    # loadstressfile = r'D:\towerdesignrelated\flasktest\问题\load_stress_flange.xlsx'
    # tower_excel_name = r'D:\towerdesignrelated\flasktest\问题\10482117_TowerGeoInput_120m5段标准分段-GW182-7.XMW_4450-513.73t-New.xlsx'
    # markov_xlsx = r'D:\towerdesignrelated\flasktest\问题\Markov_T_000k650.xlsx'
    loadstressfile = os.path.join(root_path, 'loadstressExcel.xlsx')
    tower_excel_name = os.path.join(root_path, 'TowerGeoInput.xlsx')
    markov_xlsx = os.path.join(root_path, 'Markov_T_041k050.xlsx')
    loadstress = extract_load_stress_data(loadstressfile, 'Sheet1')
    result_excel = r'D:\towerdesignrelated\flasktest\00.开发\05.中间法兰疲劳损伤计算\result.xlsx'
    # print(loadstress)
    # print(loadstress['outer_weld']['S13_Lee_2'])
    towerGeo_dict = tower_geo_2_dict(tower_excel_name)['Flange']
    flange_geo = next(iter(towerGeo_dict.items()))[1]
    print(flange_geo)
    print("*****************")
    design_standard= "IEC61400-1 Ed4"
    DC_TFL_neck= 112
    DC_TFL_fillet= 160
    scf = 1.0
    gamma_M_dict = get_gamma_M_buckle_fatigue(design_standard)
    gamma_M_fatigue = gamma_M_dict['gamma_M_fatigue']
    loadstress = extract_load_stress_data(loadstressfile, 'Sheet1')
    elevation_fl = 102.94
    try:
        # 尝试调用函数
        result = midFlangeResult2Excel(result_excel, tower_excel_name, markov_xlsx, loadstress, gamma_M_fatigue,
                                    DC_TFL_neck, DC_TFL_fillet, scf, elevation_fl)
    except SystemExit:
        print("错误！Markov矩阵中的最大载荷或最小载荷超出应力曲线范围.")
    except KeyError as e:
        print(f"错误！缺少必要的数据键: {e}")
