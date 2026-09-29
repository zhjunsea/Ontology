import os.path

from towerdesign.tools import tool


from towerdesign.drawing import infoW
from towerdesign import preproccess
from towerdesign import caddraw_main
from towerdesign import oneclickbid_main
from towerdesign.tools.file_operations import zip_target_folder
import shutil


def generate_creo_parameters(geo_path,lay_path,target_path):
    base_name, ext = os.path.splitext(os.path.basename(geo_path))
    directory = os.path.dirname(geo_path)
    target_path = os.path.join(directory, base_name)  # 所要创建的目标文件夹
    tool.dir_exists(target_path)  # 判断文件夹是否已经存在

    for flange_mode_path, limit_flange_thickness in [
        (os.path.join(target_path, "项目法兰尺寸_骨架关系式"), False),
        (os.path.join(target_path, "极限法兰尺寸_骨架关系式"), True),
    ]:
        if lay_path is None or lay_path == '':
            print("未传入布局文件，使用默认空逻辑处理")
            Creotool = tool.Creo_tool(geo_path, None, flange_mode_path)  # 类的实例化，工具类
            section_qty = Creotool.section_qty
            vflange_qty = Creotool.vFlqty
            CreoWrite = infoW.Creo_para(geo_path, None, flange_mode_path, limit_flange_thickness=limit_flange_thickness)

            tool.dirsMake(flange_mode_path, section_qty)  # 创建所有的文件夹

            for i in range(0, section_qty):  # 创建txt文件写入几何信息
                if i == 0:  # 下段
                    f_path = os.path.join(flange_mode_path, "连接法兰")
                    if i < vflange_qty:  # 如果是分片塔段法兰
                        t_fl_path = os.path.join(f_path, "底法兰关系式_分片法兰.txt")
                        with open(t_fl_path, 'w', encoding='gbk') as t_fl: # 使用gbk文字输出格式避免乱码
                            if Creotool.torlFlange():
                                CreoWrite.tflWrite(t_fl, i)
                            else:
                                CreoWrite.flWrite(t_fl, i)
                            CreoWrite.vflangeinfo(t_fl)
                    else:
                        t_fl_path = os.path.join(f_path, "底法兰关系式.txt")
                        with open(t_fl_path, 'w', encoding='gbk') as t_fl:
                            if Creotool.torlFlange():
                                CreoWrite.tflWrite(t_fl, i)
                            else:
                                CreoWrite.flWrite(t_fl, i)

                    n_path = os.path.join(flange_mode_path, f"第{i + 1}段")  # 下一级目录路径
                    write_section_file(n_path, i, vflange_qty, CreoWrite)

                elif i == section_qty - 1:  # 顶段
                    n_path = os.path.join(flange_mode_path, "顶段")  # 下一级目录路径

                    section_Geo_path = os.path.join(n_path, "顶段筒体信息关系式.txt")
                    with open(section_Geo_path, 'w', encoding='gbk') as section_Geo:
                        CreoWrite.towerInfoW(section_Geo, i)

                    write_flange_file(f_path, i, vflange_qty, CreoWrite)

                else:  # 中间段
                    n_path = os.path.join(flange_mode_path, f"第{i + 1}段")  # 下一级目录路径
                    write_section_file(n_path, i, vflange_qty, CreoWrite)

                    write_flange_file(f_path, i, vflange_qty, CreoWrite)

        else:
            Creotool = tool.Creo_tool(geo_path, lay_path, flange_mode_path)  # 类的实例化，工具类
            section_qty = Creotool.section_qty
            vflange_qty = Creotool.vFlqty
            CreoWrite = infoW.Creo_para(geo_path,lay_path,flange_mode_path, limit_flange_thickness=limit_flange_thickness)

            tool.dirsMake(flange_mode_path, section_qty)  # 创建所有的文件夹

            for i in range(0, section_qty):  # 创建txt文件写入几何信息
                if i == 0:  # 下段
                    f_path = os.path.join(flange_mode_path, "连接法兰")
                    if i < vflange_qty:  # 如果是分片塔段法兰
                        t_fl_path = os.path.join(f_path, "底法兰关系式_分片法兰.txt")
                        with open(t_fl_path, 'w', encoding='gbk') as t_fl:
                            if Creotool.torlFlange():
                                CreoWrite.tflWrite(t_fl, i)
                            else:
                                CreoWrite.flWrite(t_fl, i)
                            CreoWrite.vflangeinfo(t_fl)
                    else:
                        t_fl_path = os.path.join(f_path, "底法兰关系式.txt")
                        with open(t_fl_path, 'w', encoding='gbk') as t_fl:
                            if Creotool.torlFlange():
                                CreoWrite.tflWrite(t_fl, i)
                            else:
                                CreoWrite.flWrite(t_fl, i)

                    n_path = os.path.join(flange_mode_path, f"第{i + 1}段")  # 下一级目录路径
                    write_section_file(n_path, i, vflange_qty, CreoWrite)

                elif i == section_qty - 1:  # 顶段
                    n_path = os.path.join(flange_mode_path, "顶段")  # 下一级目录路径

                    section_Geo_path = os.path.join(n_path, "顶段筒体信息关系式.txt")
                    with open(section_Geo_path, 'w', encoding='gbk') as section_Geo:
                        CreoWrite.towerInfoW(section_Geo, i)

                    write_flange_file(f_path, i, vflange_qty, CreoWrite)

                else:  # 中间段
                    n_path = os.path.join(flange_mode_path, f"第{i + 1}段")  # 下一级目录路径
                    skel_path = os.path.join(n_path, "第" + str(i + 1) + "段附件信息关系式.txt")
                    with open(skel_path, 'w', encoding='gbk') as flskel:
                        CreoWrite.midSkelW(flskel, i)
                    flskel.close()
                    write_section_file(n_path, i, vflange_qty, CreoWrite)

                    write_flange_file(f_path, i, vflange_qty, CreoWrite)

    zip_path = zip_target_folder(target_path, directory, base_name)
    return zip_path




def write_section_file(n_path, i, vflange_qty, CreoWrite):
    """
    写入段落文件。

    :param n_path: 文件保存的路径
    :param i: 当前段的索引
    :param vflange_qty: 分片法兰的数量
    :param CreoWrite: 包含 towerInfoW 和 vflangetowerinfo 方法的对象
    """
    if i < vflange_qty:  # 如果是分片塔段
        section_Geo_path = os.path.join(n_path, f"第{i + 1}段筒体信息关系式_分片段.txt")
        with open(section_Geo_path, 'w', encoding='gbk') as section_Geo:
            CreoWrite.towerInfoW(section_Geo, i)
            CreoWrite.vflangetowerinfo(section_Geo, i)
    else:
        section_Geo_path = os.path.join(n_path, f"第{i + 1}段筒体信息关系式.txt")
        with open(section_Geo_path, 'w', encoding='gbk') as section_Geo:
            CreoWrite.towerInfoW(section_Geo, i)



def write_flange_file(f_path, i, vflange_qty, CreoWrite):
    """
    写入法兰文件。

    :param f_path: 文件保存的路径
    :param i: 当前法兰的索引
    :param vflange_qty: 分片法兰的数量
    :param CreoWrite: 包含 flWrite 和 vflangeinfo 方法的对象
    """
    if i < vflange_qty:  # 如果是分片塔段法兰
        fl_path = os.path.join(f_path, f"连接法兰{i}关系式_分片法兰.txt")
        with open(fl_path, 'w', encoding='gbk') as fl:
            CreoWrite.flWrite(fl, i)
            CreoWrite.vflangeinfo(fl)
    else:
        fl_path = os.path.join(f_path, f"连接法兰{i}关系式.txt")
        with open(fl_path, 'w', encoding='gbk') as fl:
            CreoWrite.flWrite(fl, i)
    # 再产生一个分片法兰参数关系式
    if i == vflange_qty:  # 相等的时候也存在一个分片法兰
        fl_path = os.path.join(f_path, f"连接法兰{i}关系式_分片法兰.txt")
        with open(fl_path, 'w') as fl:
            CreoWrite.flWrite(fl, i)
            CreoWrite.vflangeinfo(fl)

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




def towerBasicEx(geo_path,lay_path,target_path):#辅助设计
    target_path = target_path + "\\" + "TAD" #所要创建的目标文件夹,必须要有，不然会删除所有文件夹里的值,Tower Aided Design
    tool.dir_exists(target_path)#判断文件夹是否已经存在
    ExcelWrite = infoW.Excel_para(geo_path,lay_path,target_path)
    ExcelWrite.shellCut()
    ExcelWrite.numberTake()

#一键详图函数
def onekeydetail_start(geo_path,lay_path,target_path):
    #target_path = target_path + "\\" + "辅助表格" #所要创建的目标文件夹,必须要有，不然会删除所有文件夹里的值
    #Creotool = tool.Creo_tool(Creotool)  # 类的实例化，工具类
    #判断是不是标准分段形式
    Datapre_Initialize = preproccess.DataPre(geo_path, lay_path, target_path)
    Datapre_Initialize.std_section_judge()
    #一键详图
    CadDraw_Initialize = caddraw_main.CadDraw(geo_path, lay_path, target_path)
    CadDraw_Initialize.cad_draw_start()


#一键招標圖函数
def oneclickbid_start(geo_path,lay_path,target_path):
    #target_path = target_path + "\\" + "辅助表格" #所要创建的目标文件夹,必须要有，不然会删除所有文件夹里的值
    #tool.dir_exists(target_path)#判断文件夹是否已经存在
    #Creotool = tool.Creo_tool(Creotool)  # 类的实例化，工具类
    #判断是不是标准分段形式
    Datapre_Initialize = preproccess.DataPre(geo_path, lay_path, target_path)
    Datapre_Initialize.std_section_judge()
    #一键招标图
    BidDraw_Initialize = oneclickbid_main.BidDraw(geo_path, lay_path, target_path)
    BidDraw_Initialize.bid_draw_start()

#到自动取号的辅助设计表
def tadfull(geo_path,lay_path,target_path):#辅助设计
    target_path = target_path + "\\" + "TAD" #所要创建的目标文件夹,必须要有，不然会删除所有文件夹里的值,Tower Aided Design
    tool.dir_exists(target_path)#判断文件夹是否已经存在
    ExcelWrite = infoW.Excel_para(geo_path,lay_path,target_path)
    ExcelWrite.numberTake_plm()

#展板计算功能
def expboard(geo_path,lay_path,target_path):
    infoW.Excel_para(geo_path,lay_path,target_path).shellCut()

if __name__ == '__main__':
    geo_path = r'D:\塔架计算平台\28.creo参数关系式\TowerGeoInput_10522020_HH105m.xlsx'
    lay_path = r'D:\塔架计算平台\28.creo参数关系式\项目布局表.xlsx'
    target_path = r'D:\塔架计算平台\28.creo参数关系式'
    #generate_creo_parameters(geo_path)
    # generate_creo_parameters(geo_path,lay_path,target_path)
    # print('end')
    expboard(geo_path,lay_path,target_path)
