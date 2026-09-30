import os
import zipfile
from steeltowerdesign.Design.TowerUpdate import tower_update_main
from steeltowerdesign.tools.read_allMydata import all_flangeRange_markov_dict
from steeltowerdesign.TowerBM.TowerShellFatigue import getMarkovDict
from steeltowerdesign.StrengthCheck.CalcResult import CalcResult
import shutil
import openpyxl
from towerdesign.tools.file_operations import extractzip

def get_file_paths(directory):
    for filename in os.listdir(directory):
        if os.path.isfile(os.path.join(directory,filename)): # 检查是否为文件
            return os.path.join(directory,filename) # 返回文件路径列表

#得到载荷文件夹
def get_fold_paths(directory):
    folder_list = []
    # iterate over all the directories direct under the parent folder
    for foldername in os.listdir(directory):
        if os.path.isdir(os.path.join(directory, foldername)):
            folder_list.append(os.path.join(directory, foldername))
    return folder_list

#把塔架主体复制到每一个文件夹
def copyfiles(source_file, destination_folders):
    for folder in destination_folders:
        shutil.copy2(source_file, folder)  # copies source_file into folder

def zipfolder(foldername, target_dir):
    zipobj = zipfile.ZipFile(foldername + '.zip', 'w', zipfile.ZIP_DEFLATED)
    rootlen = len(target_dir) + 1
    for base, dirs, files in os.walk(target_dir):
        for file in files:
            fn = os.path.join(base, file)
            zipobj.write(fn, fn[rootlen:])
def getinputpara(file):
    ws = openpyxl.load_workbook(file, data_only=True)['Sheet1']
    QualityClass = ws.cell(2, 3).value
    top_flange_name = ws.cell(2, 4).value
    update_flange = ws.cell(2, 5).value
    shell_material = ws.cell(2, 6).value
    flange_material = ws.cell(2, 7).value
    is_update_internals = ws.cell(2, 8).value
    is_using_anchor_bolt = ws.cell(2, 9).value
    is_consider_towergeo_special_inputs = ws.cell(2, 10).value
    SCF = ws.cell(2, 11).value
    C1 = ws.cell(2, 12).value
    DC_shell = ws.cell(2, 13).value
    DC_accessory = ws.cell(2, 14).value
    buckling_value_limit = ws.cell(2, 15).value
    need_intermediate_result = ws.cell(2, 16).value
    damage_value_limit = ws.cell(2, 17).value
    is_cone_cylinder_transform = ws.cell(2, 18).value
    is_buckling_2017 = ws.cell(2, 19).value

#getinputpara(r"D:\塔架计算平台\14.批量定制化\塔架定制模板.xlsx")

def towerupdate_batch(file_zip,paratemplatefile):
    #解压文件
    extractzip(file_zip)
    direc = os.path.join(os.path.dirname(file_zip), os.path.basename(file_zip)[:-4])
    towerGeoExcel = get_file_paths(direc)
    load_list = get_fold_paths(direc)
    #复制模板主体数据表至每个载荷文件夹
    copyfiles(towerGeoExcel, load_list)
    #读取参数
    ws = openpyxl.load_workbook(paratemplatefile, data_only=True)['Sheet1']
    QualityClass = ws.cell(2, 3).value
    top_flange_name = ws.cell(2, 4).value
    update_flange = ws.cell(2, 5).value
    shell_material = ws.cell(2, 6).value
    flange_material = ws.cell(2, 7).value
    is_update_internals = ws.cell(2, 8).value
    is_using_anchor_bolt = ws.cell(2, 9).value
    is_consider_towergeo_special_inputs = ws.cell(2, 10).value
    SCF = ws.cell(2, 11).value
    C1 = ws.cell(2, 12).value
    DC_shell = ws.cell(2, 13).value
    DC_accessory = ws.cell(2, 14).value
    buckling_value_limit = ws.cell(2, 15).value
    need_intermediate_results = ws.cell(2, 16).value
    damage_value_limit = ws.cell(2, 17).value
    is_cone_cylinder_transform = ws.cell(2, 18).value
    is_buckling_2017 = ws.cell(2, 19).value
    prioritize_L_flange = ws.cell(2, 20).value
    special_g_M_fatigue_shell = ws.cell(2, 21).value
    directAnalysisMethod = ws.cell(2, 22).value
    directAnalysisMethodSF = ws.cell(2, 23).value
    # print(QualityClass,top_flange_name,update_flange,shell_material,flange_material,is_update_internals,is_using_anchor_bolt,is_consider_towergeo_special_inputs,
    #       SCF,C1,DC_shell,DC_accessory,buckling_value_limit,need_intermediate_result,damage_value_limit,is_cone_cylinder_transform,is_buckling_2017)
    num = 1
    for folder in load_list:
        print(f"第{num}个塔架开始定制！")
        print(os.path.basename(folder))
        for filename in os.listdir(folder):
            if os.path.isfile(os.path.join(folder, filename)):  # 检查是否为文件
                if os.path.join(folder, filename)[-4:] == "xlsx":
                    if filename[:13] == "TowerGeoInput":
                        towerGeoExcel = os.path.join(folder, filename)
                    else:
                        loadDocx = os.path.join(folder, filename)
                elif os.path.join(folder, filename)[-4:] == ".zip":
                    fatigue_file = os.path.join(folder, filename)
                    extractzip(fatigue_file)
                    fatigueLoadDir = os.path.join(os.path.dirname(fatigue_file), os.path.basename(fatigue_file)[:-4])
                else:
                    print("载荷文件夹中有多余文件！")
        nosf_loadDocx = None
        updateInputs = {'towerGeoExcel': towerGeoExcel, 'loadDocx': loadDocx, 'fatigueLoadDir': fatigueLoadDir,
                        'nosf_loadDocx': nosf_loadDocx, "QualityClass": QualityClass,
                        'top_flange_name': top_flange_name, 'update_flange': update_flange,
                        'flange_material': flange_material, 'shell_material': shell_material,
                        'is_update_internals': is_update_internals, 'is_using_anchor_bolt': is_using_anchor_bolt,
                        'is_consider_towergeo_special_inputs': is_consider_towergeo_special_inputs,
                        'is_door_fatigue_check': True, 'SCF': SCF, 'C1': C1,
                        'DC_shell': DC_shell, 'DC_accessory': DC_accessory,
                        'buckling_value_limit': buckling_value_limit, "DC_TFL_neck": 112, "DC_TFL_fillet": 140,
                        'damage_value_limit': damage_value_limit,
                        'static_safety_value': 1.03, 'need_intermediate_results': need_intermediate_results, 'min_thickness': 0.012,
                        'is_cone_cylinder_transform': is_cone_cylinder_transform, 'temperature': -30, 'mz_uncertainty_amplify': 1,
                        'is_buckling_2017': is_buckling_2017, 'prioritize_L_flange': prioritize_L_flange, 'special_g_M_fatigue_shell': special_g_M_fatigue_shell,
                        'directAnalysisMethod': directAnalysisMethod, 'directAnalysisMethodSF':directAnalysisMethodSF
                        }
        design_standard = "IEC61400-1 Ed4"
        tower_update_main(updateInputs, design_standard)
        num += 1
    #压缩结果文件包
    zipfolder(os.path.join(os.path.dirname(file_zip), os.path.basename(file_zip)[:-4]+"-result"), os.path.join(os.path.dirname(file_zip), os.path.basename(file_zip)[:-4]))
    print("批量定制塔架完成！")
def towercal_batch(file_zip,inputsFiles,inputsParams):
    #解压文件
    extractzip(file_zip)
    direc = os.path.join(os.path.dirname(file_zip), os.path.basename(file_zip)[:-4])
    load_list = get_fold_paths(direc)
    #计算
    num = 1
    error_tower = []
    for folder in load_list:
        try:
            print(f"第{num}个塔架开始校核！")
            print(os.path.basename(folder))
            for filename in os.listdir(folder):
                if os.path.isfile(os.path.join(folder, filename)):  # 检查是否为文件
                    if os.path.join(folder, filename)[-4:] == "xlsx":
                        if filename[:13] == "TowerGeoInput":
                            towerGeoExcel = os.path.join(folder, filename)
                        else:
                            loadDocx = os.path.join(folder, filename)
                    elif os.path.join(folder, filename)[-4:] == ".zip":
                        fatigue_file = os.path.join(folder, filename)
                        extractzip(fatigue_file)
                        fatigueLoadDir = os.path.join(os.path.dirname(fatigue_file), os.path.basename(fatigue_file)[:-4])
                    else:
                        print("载荷文件夹中有多余文件！")
            nosf_load_file_name = None
            flange_worksheet = openpyxl.load_workbook(towerGeoExcel, data_only=True)['Flange']
            flange_loc_list = []
            for i in range(3, flange_worksheet.max_row + 1):
                if flange_worksheet.cell(i, 1).value is not None:
                    flange_loc_list += [flange_worksheet.cell(i, 1).value]
            markovMatrix_dict = all_flangeRange_markov_dict(fatigueLoadDir, flange_loc_list)
            fatigueLoadDict = getMarkovDict(towerGeoExcel, fatigueLoadDir)
            write2ExcelName = towerGeoExcel[:-5] + '-calculateResult-25.xlsx'
            inputsFiles['towerGeoExcelName'] = towerGeoExcel
            inputsFiles['load_file_name'] = loadDocx
            inputsFiles['nosf_load_file_name'] = nosf_load_file_name
            inputsParams['write2ExcelName'] = write2ExcelName
            inputsParams['markovMatrix_dict'] = markovMatrix_dict
            inputsParams['fatigueLoadDict'] = fatigueLoadDict
            inputsParams['markovDir'] = fatigueLoadDir
            CalcResult(**inputsFiles,**inputsParams)
        except Exception as e:
            error_tower.append(os.path.basename(folder))
        num += 1
    #压缩结果文件包
    zipfolder(os.path.join(os.path.dirname(file_zip), os.path.basename(file_zip)[:-4]+"-result"), os.path.join(os.path.dirname(file_zip), os.path.basename(file_zip)[:-4]))
    print("批量校核塔架完成！")
    print(error_tower)
    return error_tower


if __name__ == '__main__':
    file_zip = r"D:\塔架计算平台\00.基础\14.批量塔架\校核\new-cal.zip"
    paratemplatefile = r"D:\塔架计算平台\00.基础\14.批量塔架\校核\塔架校核模板.xlsx"
    #towerupdate_batch(file_zip, paratemplatefile)

    jsonData = {'algoVer_display': 'v2.9.9', 'oa_display': 33905, 'contentTitle': '钢塔/强度校核', 'prj-name': '',
     'gamma_M_buckle': 1.2, 'gamma_M_fatigue_shell': 1.25, 'gamma_M_fatigue_accessory': 1.25,
     'gamma_M_fatigue_door': 1.1, 'gamma_M_fatigue_flange': 1.25, 'design_standard': 'IEC61400-1 Ed4',
     'is_buckling_2017': 'EN 1993-1-6-2017', 'bolt_standard': 'VDI2203', 'SCF': 1, 'C1': None, 'DC_shell': 90,
     'DC_accessory': 90, 'shell_material': 'Q355', 'flange_material': 'Q355', 'DC_TFL_neck': 112, 'DC_TFL_fillet': 140,
     'CheckContent': 'All', 'top_flange_name': None, 'temperature': -30, 'QualityClass': 'A',
     'is_update_vertical_flange': False, 'is_cone_cylinder_transform': True, 'is_using_anchor_bolt': True,
     'is_consider_towergeo_special_inputs': True, 'need_intermediate_results': False}

    design_standard = jsonData['design_standard']
    is_buckling_2017 = jsonData.get('is_buckling_2017') == 'EN 1993-1-6-2017'
    DC_shell = jsonData['DC_shell']
    DC_accessory = jsonData['DC_accessory']
    QualityClass = jsonData['QualityClass']
    top_flange_name = None
    SCF = jsonData['SCF']
    C1 = jsonData['C1']
    temperature = jsonData['temperature']
    flange_material = jsonData['flange_material']
    shell_material = jsonData['shell_material']
    is_using_anchor_bolt = jsonData['is_using_anchor_bolt']
    is_consider_towergeo_special_inputs = jsonData['is_consider_towergeo_special_inputs']
    need_intermediate_results = jsonData['need_intermediate_results']
    DC_TFL_neck = jsonData['DC_TFL_neck']
    DC_TFL_fillet = jsonData['DC_TFL_fillet']
    is_cone_cylinder_transform = jsonData['is_cone_cylinder_transform']
    is_update_vertical_flange = jsonData['is_update_vertical_flange']
    directAnalysisMethod = jsonData.get('directAnalysisMethod', False)
    directAnalysisMethodSF = jsonData.get('directAnalysisMethodSF', 1.0)
    special_g_M_fatigue_shell = jsonData.get('special_g_M_fatigue_shell', False)
    # 解压疲劳文件并返回markov文件夹地址,以及疲劳载荷
    markov_path = None
    markovMatrix_dict = None
    fatigueLoadDict = None
    # 材料安全系数，这里没有用前端传递过来的值，还是用的根据标准判断出来的，以兼容旧版本
    gamma_M_dict = {'gamma_M_buckle': 1.1, 'gamma_M_fatigue': 1.25}
    # 全扇区校核
    inputsFiles = {
        'towerGeoExcelName': None,
        'load_file_name': None,
        'nosf_load_file_name': None}
    inputsParams = {
        'write2ExcelName': None,
        'gamma_M_dict': gamma_M_dict,  # 示例值，请根据实际情况调整
        'DC_shell': DC_shell,
        'DC_accessory': DC_accessory,  # 示例值，请根据实际情况调整
        'QualityClass': QualityClass,  # 假设为默认值
        'top_flange_name': top_flange_name,  # 根据需要设置，默认为None
        'SCF': SCF,  # 安全系数，示例值，请根据实际情况调整
        'markovMatrix_dict': markovMatrix_dict,  # 如果有Markov矩阵字典则提供，否则为None
        'markovDir': markov_path,  # 如果有Markov文件夹路径则提供，否则为None
        'C1': C1,  # 示例值，请根据实际情况调整
        'fatigueLoadDict': fatigueLoadDict,  # 如果有疲劳载荷字典则提供，否则为None
        'temperature': temperature,  # 默认温度，示例值，请根据实际情况调整
        'flange_material': flange_material,
        'shell_material': shell_material,
        'is_using_anchor_bolt': is_using_anchor_bolt,
        'is_consider_towergeo_special_inputs': is_consider_towergeo_special_inputs,
        'need_intermediate_results': need_intermediate_results,
        'DC_TFL_neck': DC_TFL_neck,  # 示例值，请根据实际情况调整
        'DC_TFL_fillet': DC_TFL_fillet,  # 示例值，请根据实际情况调整
        'is_cone_cylinder_transform': is_cone_cylinder_transform,
        'is_update_vertical_flange': is_update_vertical_flange,
        'is_buckling_2017': is_buckling_2017,  # 假定不采用2017年抗屈曲标准
        'directAnalysisMethod': directAnalysisMethod,
        'directAnalysisMethodSF': directAnalysisMethodSF,  # 直接分析方法的安全系数，默认值为1.0
        'special_g_M_fatigue_shell': special_g_M_fatigue_shell  # 对于特定的外壳，考虑不同的疲劳安全系数
    }
    towercal_batch(file_zip, inputsFiles,inputsParams)



