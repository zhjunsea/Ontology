import os
import sys
if sys.platform == 'win32':
    import comtypes.client
else:
    class MockComtypesClient:
        def CreateObject(self, *args, **kwargs):
            raise NotImplementedError("COM technology is not supported on this platform.")
    comtypes = MockComtypesClient()
from towerdesign.tools.tool import Creo_tool

def get_draw_origin_point(scale):
    if scale == 100:
        pa_x = 13269
        pa_y = 8908
    elif scale == 120:
        pa_x = 15923
        pa_y = 10690
    elif scale == 140:
        pa_x = 18576
        pa_y = 12472
    else:
        pa_x = 52000
        pa_y = 28000
    return {'x': pa_x, 'y': pa_y}

def biddraw(towerGeoExcel):
    dir_path = os.path.dirname(towerGeoExcel)#塔架数据表所在的目录
    file_name = os.path.splitext(os.path.basename(towerGeoExcel))[0] #塔架数据表的名称，不带扩展名.xlsx
    print(file_name)
    #一些工具的初始化
    tool_initial = Creo_tool(towerGeoExcel)
    section_qty = tool_initial.flSecHqty()[2]  # 段数
    hh = tool_initial.heightJudge()  # 得到塔架的高度
    print(hh)
    # 预先调用CAD程序
    acad = comtypes.client.GetActiveObject('ZWCAD.Application', dynamic=True)
    print("塔架段数：" + str(section_qty))
    scale = tool_initial.getscale(hh)  # 图纸比例
    #原点坐标
    pa = get_draw_origin_point(scale)
    #有图框的名称
    template_dir = os.environ.get('TemplatePath') + "\\"  # 获取系统变量，获得模板的根目录
    template_name = "0000_GW_A0" + "_" + str(scale) + "_招标图_中望" + ".dwt"  # CAD模板文件的名称
    template_single = template_dir + template_name

    doc = acad.documents.add(template_single)  # 以选定的CAD模板新建dwg文档
    #输入到cad命令行的塔架数据表
    to_cad_excel_name = "(" + towerGeoExcel.replace('/', '\\') + ")"  # 以命令形式输入到CAD，注意这里有括号,前后加括号，并且斜杠变为反斜杠
    print(to_cad_excel_name)

    # 塔架总图绘制
    doc.SendCommand('oneclickbid_start\r%d\r%s\r%d\r%d\r' % (scale, to_cad_excel_name, pa['x'], pa['y']))
    filepath = dir_path + "\\" + file_name + ".dwg"
    print(filepath)
    doc.saveas(filepath)
    # #关闭drawing
    doc.close()

if __name__ == '__main__':
    rootdir = r"D:\oneclicktower\testfolder"
    towerGeoExcel = os.path.join(rootdir, "HH130m_6段_中径4.95m.xlsx")
    biddraw(towerGeoExcel)


