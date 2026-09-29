import os
import shutil
import zipfile

def extractzip(file_zip):
    # 打开指定的zip文件
    with zipfile.ZipFile(file_zip, 'r') as extracting:
        # 提取zip文件到与zip文件同目录下的一个新文件夹，文件夹名与zip文件名相同但不包含'.zip'后缀
        extracting.extractall(os.path.join(os.path.dirname(file_zip), os.path.basename(file_zip)[:-4]))
def zip_target_folder(target_path, output_path ,output_filename):
    """
    将指定的文件夹打包成ZIP文件

    :param target_path: 要打包的文件夹路径
    :param output_filename: 输出的ZIP文件名（不带.zip扩展名）
    :return: 生成的ZIP文件的完整路径
    """
    # 确保输出文件名不带.zip扩展名
    if output_filename.endswith('.zip'):
        output_filename = output_filename[:-4]

    # 使用os.path.join确保路径正确
    output_full_path = os.path.join(output_path, output_filename)

    # 使用shutil.make_archive创建ZIP文件
    try:
        shutil.make_archive(output_full_path, 'zip', target_path)
        return f"{output_full_path}.zip"  # 返回生成的ZIP文件的完整路径
    except Exception as e:
        return None  # 返回None表示打包失败
