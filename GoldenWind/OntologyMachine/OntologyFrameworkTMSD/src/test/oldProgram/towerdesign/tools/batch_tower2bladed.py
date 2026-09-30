#批量转换blaed塔架
import os
import zipfile
from steeltowerdesign.tools.towerGeo2Bladed import TowerGeo2BladedModel
from towerdesign.tools.file_operations import zip_target_folder,extractzip
#解压文件

from pathlib import Path

#遍历文件
import os

def get_file_paths(directory):
    # 初始化一个空列表，用于存储文件路径
    file_paths = []
    # 遍历指定目录下的所有文件
    for root, dirs, files in os.walk(directory):
        for file in files:
            # 检查文件名是否以'TowerGeoInput'开头并且以'.xlsx'结尾
            if file.startswith('TowerGeoInput') and file.endswith('.xlsx') and 'bladedModel' not in file:
                # 获取文件的完整路径
                file_path = os.path.join(root, file)
                # 将文件路径添加到列表中
                file_paths.append(file_path)
    # 返回文件路径列表
    return file_paths


#调用，把列表里的塔架文件批量转换成blaeded格式

def tower2bladed_batch(file_list, inputs_params):
    """
    批量处理TowerGeo文件并转换为Bladed模型。

    参数:
    file_list (list): 包含TowerGeo文件路径的列表。
    inputs_params (dict): 传递给TowerGeo2BladedModel的参数字典。

    返回:
    failed_files (list): 处理失败的文件路径列表。
    """
    failed_files = []

    for file in file_list:
        try:
            TowerGeo2BladedModel(file, **inputs_params)
        except Exception as e:
            failed_files.append(file)

    return failed_files




if __name__ == '__main__':
    tower_zip = r'D:\towerdesignrelated\flasktest\00.开发\02.批量转bladed\blade-第一组.zip'
    extractzip(tower_zip)
    extrac_file_path = os.path.dirname(tower_zip)

    file_list = get_file_paths(extrac_file_path)
    foundation_stiffness_dict = {"translational_stiffness": 0,
                                 "rotational_stiffness": 0,
                                 "foundation_mass": 0,
                                 "Foundation_inertia": 0
                                 }
    params = {
        'foundation_stiffness_dict': foundation_stiffness_dict,
        'concrete_included_if_true': True,
        'concrete_plate': True
    }
    failed_files = tower2bladed_batch(file_list, params)

    if failed_files:
        print(f"以下文件处理失败: {failed_files}")
    else:
        print("所有文件处理成功。")
    filtered_file_list = [Path(file).parent.name for file in failed_files]
    print(filtered_file_list)
    base_name, ext = os.path.splitext(os.path.basename(tower_zip))
    output_file_name = base_name + '_bladedModel'
    target_path = os.path.join(extrac_file_path, base_name)  # 所要创建的目标文件夹
    zip_path = zip_target_folder(target_path, extrac_file_path, output_file_name)
    print("down")