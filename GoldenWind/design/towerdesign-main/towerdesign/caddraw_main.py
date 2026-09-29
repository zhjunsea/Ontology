#from win32com.client import Dispatch
#import win32com
#import time
import os
import sys
if sys.platform == 'win32':
    import comtypes.client
else:
    class MockComtypesClient:
        def CreateObject(self, *args, **kwargs):
            raise NotImplementedError("COM technology is not supported on this platform.")
    comtypes = MockComtypesClient()
from towerdesign.tools.tool import Excel_tool
from towerdesign.tools.tool import Weight_tool
from towerdesign.tools.tool import SuggestCode
from towerdesign.tools.tool import dir_exists
#time_start = time.time()
#time_end = time.time()
# print("totally cost:",time_end - time_start)

class CadDraw(Excel_tool,Weight_tool,SuggestCode):
    # 法兰图纸生成
    def flout(self,acad, template_dir, fl_i):
        fl_template_name = "0000_GW_A3" + "_" + str(30) + "_Flange_titleoff" + ".dwt"  # 法兰CAD模板文件的名称
        #print("CAD模板文件名：" + str(fl_template_name))
        template_single = template_dir + fl_template_name  # 模板文件路径
        doc = acad.documents.add(template_single)  # 以选定的CAD模板新建dwg文档
        to_cad_excel_name = "(" + self.geo_path.replace('/', '\\') + ")"  # 以命令形式输入到CAD，注意这里有括号,前后加括号，并且斜杠变为反斜杠
        flscale = 30  # 图纸比例
        # CAD法兰绘制命令输出
        doc.SendCommand(
            'single_fl_insert\r%d\r%s\r%d\r' % (flscale, to_cad_excel_name, fl_i))
        # 标题栏填充
        tydh_sum_cls = self.tydhClassify()
        if fl_i == 0:
            # 塔架底法兰
            tydh_fl = tydh_sum_cls[fl_i][2]  # 连接法兰i的图样代号
        else:
            tydh_fl = tydh_sum_cls[fl_i - 1][3]  # 连接法兰i的图样代号
        #print("连接法兰" + str(fl_i) + "图号：" + str(tydh_fl))

        #print (tydh_fl)
        doc.SendCommand(
            'fl_title_insert\r%s\r%s\r' % (tydh_fl, fl_i))

        if fl_i == 0:
            fl_drawing_name = tydh_fl + "-Bottom flange"
        else:
            fl_drawing_name = tydh_fl + "-Connection flange " + self.num_to_rome(fl_i) + ".dwg"

        filepath = self.cad_path + "\\" + fl_drawing_name
        doc.saveas(filepath)
        #关闭drawing
        doc.close()

    def weldout(self,acad, template_dir, sec_i):
        # 预先调用CAD程序
        if sec_i == 0:
            scale = 60  # 图纸比例
            template_name = "0000_GW_A1" + "_" + str(60) + "_welded_titleoff_bottom" + ".dwt"  # 焊合CAD模板文件的名称
        else:
            scale = 40  # 图纸比例
            template_name = "0000_GW_A1" + "_" + str(40) + "_welded_titleoff" + ".dwt"  # 焊合CAD模板文件的名称
        #print("CAD模板文件名：" + str(template_name))
        template_single = template_dir + template_name
        doc = acad.documents.add(template_single)  # 以选定的CAD模板新建dwg文档
        to_cad_excel_name = "(" + self.geo_path.replace('/', '\\') + ")"  # 以命令形式输入到CAD，注意这里有括号,前后加括号，并且斜杠变为反斜杠

        # 定义塔筒焊合主体的位置
        y_loacation = self.towerHeight()  # 塔筒段的累积长度
        y_loacation.insert(0, 0)
        # 塔架焊合图形绘制
        doc.SendCommand(
            'welded_insert\r%d\r%s\r%d\r%d\r' % (scale, to_cad_excel_name, 0 - y_loacation[sec_i] + 4000, sec_i))
        # 塔架焊合标题栏填充
        tydh_sum_cls = self.tydhClassify()
        tydh_weld = tydh_sum_cls[sec_i][0]  # 焊合总成图号
        tydh_cylinder = tydh_sum_cls[sec_i][1]  # 筒体图号
        tydh_fl_d = tydh_sum_cls[sec_i][2]  # 焊合下法兰图号
        tydh_fl_u = tydh_sum_cls[sec_i][3]  # 焊合上法兰图号
        tydh_jqb = tydh_sum_cls[sec_i][-1]  # 加强板图号
        # 明细表插入
        doc.SendCommand(
            'weld_mxb_insert\r%s\r%s\r%s\r%s\r%s\r' % (sec_i, tydh_fl_d, tydh_cylinder, tydh_fl_u, tydh_jqb))
        # 标题栏插入
        doc.SendCommand(
            'weld_title_insert\r%s\r%s\r' % (tydh_weld, sec_i))
        if sec_i == self.section_qty - 1:
            weld_drawing_name = tydh_weld + "-Top Section Welded.dwg"
        else:
            weld_drawing_name = tydh_weld + "-Section " + self.num_to_rome(sec_i + 1) + " Welded.dwg"
        #filepath = self.cad_path + "\\" + "Section " + str(sec_i) + " Welded.dwg"
        filepath = self.cad_path + "\\" + weld_drawing_name
        doc.saveas(filepath)
        #关闭drawing
        doc.close()

    def towerout(self,acad, template_dir):
        scale = self.getscale(self.hh)  # 图纸比例
        #模板名称
        template_name = "0000_GW_A0" + "_" + str(scale) + "_Tower_titleoff" + ".dwt"  # 焊合CAD模板文件的名称
        #print (template_name)
        #print("CAD模板文件名：" + str(template_name))
        template_single = template_dir + template_name
        doc = acad.documents.add(template_single)  # 以选定的CAD模板新建dwg文档
        to_cad_excel_name = "(" + self.geo_path.replace('/', '\\') + ")"  # 以命令形式输入到CAD，注意这里有括号,前后加括号，并且斜杠变为反斜杠
        to_cad_design_file = "(" + self.design_file.replace('/', '\\') + ")"  # 以命令形式输入到CAD，注意这里有括号,前后加括号，并且斜杠变为反斜杠
        #print("塔架总图比例为：" + str(scale))
        # 定义塔筒焊合主体的位置
        y_loacation = self.towerHeight()  # 塔筒段的累积长度
        y_loacation.insert(0, 0)
        # 塔架总图绘制
        doc.SendCommand('tower_single\r%d\r%s\r%d\r%s\r%s\r' % (scale, to_cad_excel_name, 7000, to_cad_design_file, self.powerofdes))
        # 塔架焊合标题栏填充
        tydh_tower = self.zt_tydh#总图的图样代号
        towerbt = self.towername(self.section_qty, self.hh)#标题
        #print(towerbt)
        # #标题栏插入
        doc.SendCommand(
            'tower_title_insert\r%s\r%d\r%s\r' % (tydh_tower, int(self.hh), towerbt))
        zt_drawing_name = self.zt_tydh
        filepath = self.cad_path + "\\"+ zt_drawing_name + "-" + str(self.section_qty) + " Sections " + str(self.hh) + "m HH Tower" + ".dwg"
        doc.saveas(filepath)
        #关闭drawing
        #doc.close()
    def cad_draw_start(self):
        self.section_qty = self.flSecHqty()[2]  # 段数
        self.hh = self.heightJudge()  # 机型标准高度
        self.seri = self.seriesJudge()  # 机型
        self.powerofdes = self.powerJudge()#塔架说明里给的功率
        #print(self.powerofdes)
        self.acc_seri = self.seri + "-" + self.powerofdes#塔架附件类型
        self.acc_data_path = "D:/Program Files/OneKeyTower/acc_database"  #附件总成信息根目录
        self.acc_data_file = self.acc_data_path + "/" + self.acc_seri + "/" + str(self.section_qty) + "/" + str(
            self.hh) + ".xlsx"  # 项目的附件总成数据库文件
        self.design_file = self.target_path + "/" + "TAD" + "/" + "TAD Table.xls"
        self.cad_path = self.target_path + "\\" + "CAD Drawings" #所要创建的目标文件夹,必须要有，不然会删除所有文件夹里的值
        dir_exists(self.cad_path)#判断文件夹是否已经存在
        self.zt_tydh = self.target_path[-11:]#总图的图样代号
        # 预先调用CAD程序
        acad = comtypes.client.GetActiveObject('AutoCAD.Application', dynamic=True)
        template_dir = os.environ.get('TemplatePath') + "\\"  # 获取系统变量，获得模板的根目录
        #print("塔架段数：" + str(self.section_qty))
        #print(self.target_path)

        # 生成图纸
        for i in range(0,self.section_qty):
            self.flout(acad, template_dir, i)
            self.weldout(acad, template_dir, i)
        self.towerout(acad, template_dir)
        #生成焊合
        # self.weldout(acad, template_dir ,0)
        # self.flout(acad, template_dir, 0)





