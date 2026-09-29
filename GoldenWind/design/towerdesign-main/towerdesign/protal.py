import comtypes.client
import os
import win32com.client
# wincad = win32com.client.Dispatch("ZWCAD.Application.2021")
# doc = wincad.ActiveDocument
# doc.Utility.Prompt("Hello! Autocad from pywin32com.\n")

# acad = comtypes.client.GetActiveObject('ZWCAD.Application', dynamic=True)
# doc = acad.ActiveDocument
# print(doc)
# doc.Utility.Prompt("abddddddddddddc")
# template_dir = os.environ.get('TemplatePath') + "\\"  # 获取系统变量，获得模板的根目录
# print(template_dir)

# doc = acad.documents.add("")  # 以选定的CAD模板新建dwg文档
# doc.SendCommand("tower")
# print(acad)
#


from pyzwcad import ZwCAD, APoint
#acad = ZwCAD()
# acad.prompt("Hello, Autocad from Python\n")
# print(acad.doc.Name)

#from win32com.client import Dispatch
#import win32com
#import time
import os
import sys
#import comtypes
import comtypes.client
import tool
from tool import Excel_tool
from tool import Weight_tool
from tool import SuggestCode
target_path = r"D:\oneclicktower\testfolder"

section_qty = flSecHqty()[2]  # 段数
self.hh = self.heightJudge()  # 机型标准高度
self.seri = self.seriesJudge()  # 机型
self.cad_path = self.target_path + "\\" + "CAD Drawings"  # 所要创建的目标文件夹,必须要有，不然会删除所有文件夹里的值
tool.dir_exists(self.cad_path)  # 判断文件夹是否已经存在,删除文件夹内的所有文件
self.zt_tydh = self.target_path[-11:]  # 总图的图样代号
# 预先调用CAD程序
acad = comtypes.client.GetActiveObject('AutoCAD.Application', dynamic=True)
template_dir = os.environ.get('TemplatePath') + "\\"  # 获取系统变量，获得模板的根目录
print("塔架段数：" + str(self.section_qty))
scale = self.getscale()  # 图纸比例
# 模板名称
template_name = "0000_GW_A0" + "_" + str(scale) + "_Tower_titleoff" + ".dwt"  # 焊合CAD模板文件的名称
template_single = template_dir + template_name
doc = acad.documents.add(template_single)  # 以选定的CAD模板新建dwg文档
to_cad_excel_name = "(" + self.geo_path.replace('/', '\\') + ")"  # 以命令形式输入到CAD，注意这里有括号,前后加括号，并且斜杠变为反斜杠

# 定义塔筒焊合主体的位置
y_loacation = self.towerHeight()  # 塔筒段的累积长度
y_loacation.insert(0, 0)
fjzc_mass = self.getAccWeight(self.hh, self.seri, self.section_qty)  # 附件总成的重量

# 塔架总图绘制
doc.SendCommand('oneclickbid_start\r%d\r%s\r%d\r%d\r' % (scale, to_cad_excel_name, 7000, fjzc_mass))
# 塔架焊合标题栏填充
tydh_tower = self.zt_tydh
towerbt = self.towername()  # 标题
# #标题栏插入
doc.SendCommand(
    'tower_title_insert\r%s\r%d\r%s\r' % (tydh_tower, int(self.hh), towerbt))
zt_drawing_name = self.zt_tydh
filepath = self.cad_path + "\\" + zt_drawing_name + ".dwg"
doc.saveas(filepath)
# #关闭drawing
# #doc.close()