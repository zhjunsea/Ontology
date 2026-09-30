from towerdesign.drawing import infoformat
from towerdesign.tools.tool import Creo_tool
from towerdesign.tools.tool import Excel_tool
import xlwt
import math
from towerdesign.tools.tool import Weight_tool, SuggestCode
from towerdesign.plm import PlmCode

# 极限法兰厚度固定值
LIMIT_FLANGE_THICKNESS = 215

class Creo_para(Creo_tool):
    def __init__(self, geo_path, lay_path, target_path, limit_flange_thickness=False):
        super().__init__(geo_path, lay_path, target_path)
        self.limit_flange_thickness = limit_flange_thickness

        if limit_flange_thickness:
            self._orig_flange_cell = self.FlangeGeo.cell_value
            def cell_value(r, c):
                if c == 4:
                    return self.get_tfl(r)
                if c == 2:
                    return self.get_di(r)
                return self._orig_flange_cell(r, c)
            self.FlangeGeo.cell_value = cell_value

    def get_tfl(self, row_index):
        """获取指定行的 TFL 值，极限模式下中间法兰统一返回 215mm
        底法兰(Flange第3行,即xlrd row 2)和顶法兰(Flange最后一行数据)始终不替换"""
        original_tfl = self._orig_flange_cell(row_index, 4)
        # 底法兰(xlrd row 2)和顶法兰(最后一行数据行)不做更改
        first_data_row = 2
        last_data_row = first_data_row + self.flange_qty - 1
        if row_index == first_data_row or row_index == last_data_row:
            return original_tfl
        if self.limit_flange_thickness:
            return LIMIT_FLANGE_THICKNESS
        return original_tfl

    def get_di(self, row_index):
        """获取指定行的 DI 值，极限模式下中间法兰内径替换为 DA - S×2 - 600
        底法兰(row 2)和顶法兰(最后一行数据)始终不替换"""
        original_di = self._orig_flange_cell(row_index, 2)
        first_data_row = 2
        last_data_row = first_data_row + self.flange_qty - 1
        if row_index == first_data_row or row_index == last_data_row:
            return original_di
        if self.limit_flange_thickness:
            da = self._orig_flange_cell(row_index, 1)
            s = self._orig_flange_cell(row_index, 5)
            return round(da - s * 2 - 600, 1)
        return original_di

    def get_top_delta(self, n):
        """获取第n段因顶部法兰厚度增加，最上面筒节需要扣减的高度"""
        if not self.limit_flange_thickness:
            return 0
        top_flange_row = n + 3  # 第n段顶部法兰行号
        last_data_row = 2 + self.flange_qty - 1
        if top_flange_row == last_data_row:  # 顶法兰不改
            return 0
        original_tfl = self._orig_flange_cell(top_flange_row, 4)
        return max(round(LIMIT_FLANGE_THICKNESS - original_tfl, 1), 0)

    def get_bottom_delta(self, n):
        """获取第n段因底部法兰厚度增加，最下面筒节需要扣减的高度"""
        if not self.limit_flange_thickness:
            return 0
        bottom_flange_row = n + 2  # 第n段底部法兰行号
        first_data_row = 2
        if bottom_flange_row == first_data_row:  # 底法兰不改
            return 0
        original_tfl = self._orig_flange_cell(bottom_flange_row, 4)
        return max(round(LIMIT_FLANGE_THICKNESS - original_tfl, 1), 0)

    def towerInfoW(self,file,n):#写入塔筒主体信息，焊合信息，写入到file文件里
    #标准段的上下端直径为cyD_top和cyD_bottom，而顶段的为cy_d_top,cy_d_bottom.
        section_length = self.secNLength()#筒段长
        section_qty = self.flSecHqty()['section_qty']#筒段数量
        f_pos = self.flangePos()
        H_B = round((self.TowerGeo.cell_value(f_pos[n],3)-self.TowerGeo.cell_value(f_pos[n],1))*1000,3) #下法兰高
        H_T = round((self.TowerGeo.cell_value(f_pos[n+1],3)-self.TowerGeo.cell_value(f_pos[n+1],1))*1000,3) #上法兰高
        file.write(infoformat.weight_info)
        file.write( "DELTA=0.02/*缝隙高度\n")
        file.write( "SEC_H_TOTAL=" + str(section_length[n]) + "/*筒段总高\n")
        file.write( "DA_TOP=" + str(round(self.FlangeGeo.cell_value(n + 3, 1),0))+ "/*上法兰外径\n")
        file.write( "TFL_TOP=" + str(round(self.FlangeGeo.cell_value(n + 3, 4),0)) + "/* 上法兰厚\n")
        file.write( "TFL_BOTTOM=" + str(round(self.FlangeGeo.cell_value(n + 2, 4),0)) + "/* 下法兰厚\n")
        file.write( "H_BOTTOM=" + str(round(H_B-self.FlangeGeo.cell_value(n + 2, 4),1)) + "/* 下法兰脖子高度\n")
        if n == section_qty - 1:#顶段
            file.write("H_TOP=" + str(round((self.TowerGeo.cell_value(self.TowerGeo.nrows-2,3)-self.TowerGeo.cell_value(self.TowerGeo.nrows-2,1))*1000,1)) + "/* 上法兰脖子高度\n")
        elif n == 0:#底段
            file.write("H_TOP=" + str(round(H_T - self.FlangeGeo.cell_value(n + 3, 4), 0)) + "/* 上法兰脖子高度\n")
        else:#中间段
            file.write("H_TOP=" + str( round(H_T-self.FlangeGeo.cell_value(n + 3, 4),0) )+ "/* 上法兰脖子高度\n")




        if n == 0:#底段
            if isinstance(self.DoorGeo.cell_value(2, 12), float):  # 判断有没有加强板信息
                file.write("/***************加强板开洞信息*************/\n")
                file.write("H=" + str(round(self.DoorGeo.cell_value(2, 12), 0)) + "/*门框位置\n")
                file.write("$α=360-53/*门框角度，与X轴正方向，逆时针\n")
                file.write("α_frame=" + "60" + "/*门框加强板对应圆心角\n")
                file.write("H1_FRAME=" + str(round(self.DoorGeo.cell_value(2, 15), 0)) + "/*补强板高度\n")
                file.write("H2_FRAME=" + "200" + "/*补强板展开倒圆角\n")
            else:
                file.write("/***************普通门洞信息*************/\n")
                file.write("H_FRAME=" + str(round(self.DoorGeo.cell_value(n + 2, 0), 0)) + "/*门框位置\n")
                file.write("α_frame=" + "360-53" + "/*门框角度，与X轴，逆时针\n")
                file.write("H1_FRAME=" + str(round(self.DoorGeo.cell_value(n + 2, 3), 0)) + "/*门框开洞高度\n")
                file.write("H2_FRAME=" + str(round(self.DoorGeo.cell_value(n + 2, 5), 0)) + "/*门洞直边长度\n")
                file.write("B1_FRAME=" + str(round(self.DoorGeo.cell_value(n + 2, 4), 0)) + "/*门洞宽度\n")


        file.write("/*********主体参数************\n")
        k = 1#代表第n个筒节，写入塔筒壁厚信息
        for i in range(f_pos[n] + 1, f_pos[n + 1] - 1):  # 写入塔筒壁厚信息
            file.write("cy" + str(k) + "_t=" + str( round(self.TowerGeo.cell_value(i,5),1))+ "/*筒节" + str(k) + "壁厚\n")
            k = k + 1
        for i in range(0 ,20 - (f_pos[n + 1] - f_pos[n] - 2) ):#一共要有20段，补充没有数据的段
            file.write("cy" + str(k) + "_t=" + str(0) + "/*筒节" + str(k) + "壁厚\n")
            k = k + 1

        k = 1#写入塔筒高度信息
        top_delta = self.get_top_delta(n)
        bottom_delta = self.get_bottom_delta(n)
        real_course_count = f_pos[n + 1] - f_pos[n] - 2
        for i in range(f_pos[n] + 1, f_pos[n + 1] - 1):
            cy_h = round((self.TowerGeo.cell_value(i, 3) - self.TowerGeo.cell_value(i, 1)) * 1000, 0)
            if k == 1 and bottom_delta > 0:
                cy_h = cy_h - bottom_delta
            elif k == real_course_count and top_delta > 0:
                cy_h = cy_h - top_delta
            file.write("cy" + str(k) + "_h=" + str(cy_h) + "/*筒节" + str(k) + "节高\n")
            k = k + 1
        for i in range(0 ,20 - (f_pos[n + 1] - f_pos[n] - 2) ):#一共要有20段，补充没有数据的段
            file.write("cy" + str(k) + "_h=" + str(1) + "/*筒节" + str(k) + "节高\n")
            k = k + 1

        if n == section_qty - 1:#写入塔筒上下直径信息,顶段
            k = 1
            for i in range(f_pos[n] + 1, f_pos[n + 1] - 1):
                file.write("cy" + str(k) + "_d_bottom=" +str( round(self.TowerGeo.cell_value(i, 2)+0.00001,1) )+ "/*筒节" + str(k) + "下端直径\n")
                k = k + 1
            for i in range(0 ,20 - (f_pos[n + 1] - f_pos[n] - 2) ):#一共要有20段，补充没有数据的段
                file.write("cy" + str(k) + "_d_bottom=" + str(4300) + "/*筒节" + str(k) + "下端直径\n")
                k = k + 1
            k = 1
            for i in range(f_pos[n] + 1, f_pos[n + 1] - 1):
                file.write("cy" + str(k) + "_d_top=" +str( round(self.TowerGeo.cell_value(i, 4)+0.00001,1) )+ "/*筒节" + str(k) + "上端直径\n")
                k = k + 1
            for i in range(0 ,20 - (f_pos[n + 1] - f_pos[n] - 2) ):#一共要有20段，补充没有数据的段
                file.write("cy" + str(k) + "_d_top=" +str( self.TowerGeo.cell_value(f_pos[n+1]-2,4) )+ "/*筒节" + str(k) + "上端直径\n")
                k = k + 1

        elif n == 0:#下段的
            k = 1
            for i in range(f_pos[n] + 1, f_pos[n + 1] - 1):
                file.write(
                    "cy" + str(k) + "_d_bottom=" +str(round(self.TowerGeo.cell_value(i, 2) + 0.01, 1) )+ "/*筒节" + str(k) + "下端直径\n")
                k = k + 1
            for i in range(0, 20 - (f_pos[n + 1] - f_pos[n] - 2)):  # 一共要有20段，补充没有数据的段
                file.write("cy" + str(k) + "_d_bottom=" + str(4300) + "/*筒节" + str(k) + "下端直径\n")
                k = k + 1
            k = 1
            for i in range(f_pos[n] + 1, f_pos[n + 1] - 1):
                file.write("cy" + str(k) + "_d_top=" + str(round(self.TowerGeo.cell_value(i, 4) + 0.01, 1)) + "/*筒节" + str(k) + "上端直径\n")
                k = k + 1
            for i in range(0, 20 - (f_pos[n + 1] - f_pos[n] - 2)):  # 一共要有20段，补充没有数据的段
                file.write("cy" + str(k) + "_d_top=" + str(self.TowerGeo.cell_value(f_pos[n + 1] - 2, 4)) + "/*筒节" + str(k) + "上端直径\n")
                k = k + 1

        else:#中间段的
            k = 1
            for i in range(f_pos[n] + 1, f_pos[n + 1] - 1):#写入筒节下端直径信息
                file.write("cy" + str(k) + "_D_bottom=" + str(round(self.TowerGeo.cell_value(i, 2)+0.01,1)) + "/*筒节" + str(k) + "下端直径\n")
                k = k + 1
            for i in range(0 ,20 - (f_pos[n + 1] - f_pos[n] - 2) ):#一共要有20段，补充没有数据的段
                file.write("cy" + str(k) + "_D_bottom=" + str(4300) + "/*筒节" + str(k) + "下端直径\n")
                k = k + 1
            k = 1#写入筒节上端直径信息
            for i in range(f_pos[n] + 1, f_pos[n + 1] - 1):
                file.write("cy" + str(k) + "_D_top=" + str(round(self.TowerGeo.cell_value(i, 4)+0.01,1)) + "/*筒节" + str(k) + "上端直径\n")
                k = k + 1
            for i in range(0 ,20 - (f_pos[n + 1] - f_pos[n] - 2) ):#一共要有20段，补充没有数据的段
                file.write("cy" + str(k) + "_D_top=" + str(self.TowerGeo.cell_value(f_pos[n+1]-2,4)) + "/*筒节" + str(k) + "上端直径\n")
                k = k + 1
            if self.stan_layGeo != None:
                H_platform = self.stan_layGeo.cell_value(2, n)
                file.write(self.ls_heights(n, H_platform, 0)[2] + "\n\n")
                file.write(self.ls_heights(n, H_platform, 0)[3] + "\n")
            else:
                # 可选：添加无布局信息时的处理（如日志提示）
                print(f"未获取到布局信息，跳过第{n}段的平台高度写入")
        file.write(infoformat.drive_size)

    def plateSkelW(self,file, n): #第一段筒体骨架
        section_length = self.secNLength()
        file.write("/**设置显示模型中文名称项**/\nPART_NAME=PTC_COMMON_NAME\n\n")
        file.write("/**第一段主体参数值**/\n")
        file.write("SEC_H_TOTAL=" + str(section_length[n]) + "/*筒段总高\n"
        + "DA_TOP=" + str(round(self.FlangeGeo.cell_value(n + 3, 1), 0)) + "/*上法兰外直径\n"
        + "DA_BOTTOM=" + str(round(self.FlangeGeo.cell_value(n + 2, 1), 0)) + "/*底法兰外直径\n"
        + "DI_TOP=" + str(round(self.FlangeGeo.cell_value(n + 3, 2), 0)) + "/*上法兰内径\n"
        + "DI_BOTTOM=" + str(round(self.FlangeGeo.cell_value(n + 2, 2), 0)) + "/*底法兰内径\n"
        + "TFL_TOP=" + str(round(self.FlangeGeo.cell_value(n + 3, 4), 1)) + "/*上法兰厚度\n"
        + "TFL_BOTTOM=" + str(round(self.FlangeGeo.cell_value(n + 2, 4))) + "/*底法兰厚度\n")
        file.write("/*************门框信息****************/\n")
        file.write("L_FRAME="+str(round(self.DoorGeo.cell_value(2,8),0))+"/*门框外漏\n"
        + "BST_FRAME=" + str(round(self.DoorGeo.cell_value(2, 6), 0)) + "/*门框厚度\n"
        + "HST_FRAME=" + str(round(self.DoorGeo.cell_value(2, 7), 0)) + "/*门框宽度\n"
        + "T1=" + str(round(self.DoorGeo.cell_value(2, 2), 0)) + "/*门框处筒壁厚度\n"
        + "DA_FRAME=" + str(round(self.DoorGeo.cell_value(2, 1), 0)) + "/*门框处塔架外径\n"
        + "H_FRAME=" + str(round(self.DoorGeo.cell_value(2, 0), 1)) + "/*门框位置\n"
        + "α_frame=360-53" + "/*门框角度，与X轴，逆时针\n"
        + "B1_FRAME=" + str(round(self.DoorGeo.cell_value(2, 4), 0)) + "/*门洞宽度\n"
        + "H2_FRAME=" + str(round(self.DoorGeo.cell_value(2, 5), 0)) + "/*门洞直边长度\n"
        + "H1_FRAME=" + str(round(self.DoorGeo.cell_value(2, 3), 1)) + "/*门框开洞高度\n")
        file.write("B_DIA=HST_FRAME-30 /*门框横隔板长度\n")
        file.write("H_DIAPHRAGM=" + str(1938) + "/*门框横隔板高度\n"
        + "d128=" + str(360) + "/*门销轴焊合高度\n")
        file.write("/***************入口梯耳板安装信息****************/\n")
        file.write("H_LUG="+ str(self.down_layGeo.cell_value(13,1)) + "/*入口梯耳板高度\n")
        file.write("DA_LUG=DA_TOP/*入口梯耳板处筒壁外径\n")

    def replateSkelW(self,file, n):#加强板筒体骨架
        section_length = self.secNLength()
        file.write("/**设置显示模型中文名称项**/\nPART_NAME=PTC_COMMON_NAME\n\n")
        file.write("/**第一段主体参数值**/\n")
        file.write("SEC_H_TOTAL=" + str(section_length[n]) + "/*筒段总高\n"
        + "DA_TOP=" + str(round(self.FlangeGeo.cell_value(n + 3, 1), 0)) + "/*上法兰外直径\n"
        + "DA_BOTTOM=" + str(round(self.FlangeGeo.cell_value(n + 2, 1), 0)) + "/*底法兰外直径\n")
        file.write("/*************门框加强板信息****************/\n")
        file.write("d110="+str(360-53)+"/*门框补强板角度\n"
        + "H=" + str(round(self.DoorGeo.cell_value(2, 12), 0)) + "/*门框补强板高度\n"
        + "D=" + str(round(self.DoorGeo.cell_value(2, 13), 0)) + "/*门框筒外直径\n"
        + "t1=" + str(round(self.DoorGeo.cell_value(2, 14), 0)) + "/*门框处筒壁厚度\n"
        + "t2=" + str(round(self.DoorGeo.cell_value(2, 16), 0)) + "/*门框补强板厚度\n"
        + "补强板外端距离塔筒外径=" + "(t2-t1)/2\n"
        + "L=" + str(30) + "/*门框伸出补强板距离\n")
        file.write("/***************入口梯耳板安装信息****************/\n")
        file.write("H_LUG="+ str(self.down_layGeo.cell_value(13,1)) + "/*入口梯耳板高度\n")
        file.write("DA_LUG=DA_TOP+(t2-t1)/*入口梯耳板处筒壁外径\n")


    def assemblySkelW(self,file, n):#下段总成骨架信息
        section_length = self.secNLength()
        section_qty = self.flSecHqty()[2]
        f_pos = self.flangePos()
        file.write("/**设置显示模型中文名称项**/\nPART_NAME=PTC_COMMON_NAME\n\n")
        file.write("/**主体信息**/\n")
        file.write("SEC_H_TOTAL=" + str(section_length[n]) + "/*第一段塔筒总高\n")
        file.write("$H_BOTTOM2CONCRETE=" + str(round(self.down_layGeo.cell_value(2,1),0)) + "/*混凝土面相对底法兰下端的距离\n")
        file.write("$α=-53 " + "/*门框角度，与X轴正方向，逆时针为正\n")
        file.write("/*********上法兰参数*********/\n")
        file.write("DA_TOP=" + str(round(self.TowerGeo.cell_value(f_pos[n+1],2),0)) + "/*上法兰外径（由于第一段一般是直段，所有截面处的外径用DA_TOP代替）\n")
        file.write("DI_TOP=" + str(round(self.FlangeGeo.cell_value(3,2),0)) + "/*上法兰内径 \n")
        file.write("TFL_TOP=" + str(round(self.FlangeGeo.cell_value(3, 4), 0)) + "/*上法兰厚\n")
        file.write("S_TOP=" + str(round(self.FlangeGeo.cell_value(3, 5), 0)) + "/*上法兰颈厚\n")
        file.write("/*********下法兰参数*********/\n")
        file.write("DA_BOTTOM=" + str(round(self.TowerGeo.cell_value(f_pos[n],2),0)) + "/*下法兰外径\n")
        file.write("DI_BOTTOM=" + str(round(self.FlangeGeo.cell_value(2,2),0)) + "/*下法兰内径 \n")
        file.write("TFL_BOTTOM=" + str(round(self.FlangeGeo.cell_value(2, 4), 0)) + "/*下法兰厚\n")
        file.write("S_BOTTOM=" + str(round(self.FlangeGeo.cell_value(2, 5), 0)) + "/*下法兰颈厚\n\n")
        file.write("/*************平台位置*********************/\n")
        file.write("H_PLAT2FL=" + str(round(self.down_layGeo.cell_value(6,1),0)) + "/*顶平台距离上法兰上端面\n")
        file.write("H_PLAT_INITIAL=" + str(round(self.down_layGeo.cell_value(8,1),0)) + "/*升降机起始平台绝对高度，距下法兰下端面\n")
        file.write("H_PLAT_CONTROL=" + str(round(self.down_layGeo.cell_value(11, 1), 0)) + "/*底平台绝对高度，距下法兰下端面\n")
        file.write("H_SURROUNDING_EDGE=" + str(round(self.down_layGeo.cell_value(12, 1), 0)) + "/*平台围边距离底平台距离\n\n")
        file.write("/*************直爬梯位置信息*********************/\n")
        file.write("$H_LADDER_TOP=" + str(round(self.down_layGeo.cell_value(3, 1), 0)) + "/*爬梯上端面距上法兰上端面\n")
        file.write("L_LADDER=" + str(round(self.down_layGeo.cell_value(4, 1), 0)) + "/*爬梯长度\n")
        file.write("$H_LADDER_FIXED_SUPPORT=-L_LADDER+140 " + "/*爬梯最下端支撑坐标系距爬梯安装坐标系距离\n")
        file.write("L_LADDER_VERTICAL=L_LADDER\n")
        file.write("DI_LADDER_UP=" + str(round(self.TowerGeo.cell_value(f_pos[n+1],2)-2*self.FlangeGeo.cell_value(3, 5),1)) + "/*爬梯顶端处内径\n")
        file.write("DI_LADDER_DOWN=" + str(round(self.TowerGeo.cell_value(f_pos[n+1],2)-2*self.FlangeGeo.cell_value(3, 5),1)) + "/*爬梯底端处内径\n")
        file.write("N_LADDER=" + "L_LADDER/280/*爬梯横支撑阵列数\n")
        file.write("/************电缆夹板位置信息************************/\n")
        file.write("H_CLAMP2TOP=" + str(round(self.down_layGeo.cell_value(7,1), 1))+ "/*电缆夹板距离上法兰上端面距离\n")
        file.write("DI_CLAMP_2MW=" + str(self.diCal(section_length[n] - self.down_layGeo.cell_value(7,1),n))+ "/*顶法兰上电缆夹板所在处内径")
        file.write("\n/*************电缆线槽（从顶法兰下来）位置信息******************/")
        file.write("\nL_TRAY=" + str(round(self.down_layGeo.cell_value(5,1), 1))+ "/*电缆线槽（从顶端下来的长线槽）长度")
        file.write("\nL_TRAY_VERTICAL=" + "L_TRAY   /*长线槽的长度，和上面重合了，用来确定长线槽最下端的位置")
        file.write("\n/************2MW电缆线槽（位于电缆托架上）关系***************/")
        file.write("\nDI_TRAY_2MW_UP=" + str(round(self.TowerGeo.cell_value(f_pos[n+1],2)-2*self.FlangeGeo.cell_value(3, 5),1))+ "/*长线槽最上端所在处塔筒内径")
        file.write("\nDI_TRAY_2MW_DOWN=" + str(round(self.TowerGeo.cell_value(f_pos[n+1],2)-2*self.FlangeGeo.cell_value(3, 5),1)) + "/*长线槽最下端所在处塔筒内径")
        file.write("\n/************电缆线槽I定位（4米4线槽）***************************************/")
        file.write("\nL_TRAY_I_VERTICAL=" + str(round(self.down_layGeo.cell_value(21,1), 1))+ "/*电缆线槽一的长度")
        file.write("\nH_TRAY_I_DOWN=" + str(round(self.down_layGeo.cell_value(22,1), 1))+ "/*电缆线槽最下端相对底平台距离")
        file.write("\nH_TRAY_I_UP=" + "H_TRAY_I_DOWN+L_TRAY_I_VERTICAL /*线槽I最上端距底平台距离")
        file.write("\nDI_TRAY_I_UP=" + str(self.diCal(self.down_layGeo.cell_value(11, 1)+self.down_layGeo.cell_value(22,1)+self.down_layGeo.cell_value(21,1), n))+ "/*线槽I最上端所在处内径")
        file.write("\nDI_TRAY_I_DOWN=" + str(self.diCal(self.down_layGeo.cell_value(11, 1)+self.down_layGeo.cell_value(22,1),n))+ "/*电缆线槽I最下端所处塔筒内径")
        file.write("\n$H_1200_TRAY_I=" + "-(L_TRAY_I_VERTICAL-1200)  /*电缆线槽I下的1米2线槽的安装位置，距离电缆线槽I顶端的距离")
        file.write("\nH_FIX_I=" + str(round(self.down_layGeo.cell_value(23,1), 1))+ "/*电缆线槽I最下段的固定装置至底平台距离")
        file.write("\nDI_FIX_I=" + str(self.diCal(self.down_layGeo.cell_value(11, 1)+self.down_layGeo.cell_value(23,1),n))+ "/*电缆线槽I最下段的固定装置所在塔筒处内径")
        file.write("\n/************电缆线槽II（固定装置固定）****/")
        file.write("\nL_TRAY_II_VERTICAL=" + str(round(self.down_layGeo.cell_value(24,1), 1))+ "/*电缆线槽II的长度")
        file.write("\nH_TRAY_II_DOWN=" + str(round(self.down_layGeo.cell_value(25,1), 1))+"/*电缆线槽最下端相对底平台距离")
        file.write("\nH_TRAY_II_UP=" + "H_TRAY_II_DOWN+L_TRAY_II_VERTICAL/*线槽II最上端距底平台距离")
        file.write("\nDI_TRAY_II_UP=" + str(self.diCal(self.down_layGeo.cell_value(11, 1)+self.down_layGeo.cell_value(25,1)+self.down_layGeo.cell_value(24,1), n))+ "/*线槽II最上端所在处内径")
        file.write("\nDI_TRAY_II_DOWN=" + str(self.diCal(self.down_layGeo.cell_value(11, 1)+self.down_layGeo.cell_value(25,1),n))+ "/*电缆线槽II最下端所处塔筒内径")
        file.write("\n$H_1200_TRAY_II=" + "-(L_TRAY_II_VERTICAL-1200)  /*电缆线槽II下的1米2线槽的安装位置，距离电缆线槽II顶端的距离")
        file.write("\nH_FIX_II=" + str(round(self.down_layGeo.cell_value(26,1), 1)))
        file.write("\nDI_FIX_II=" + str(self.diCal(section_length[n] - self.down_layGeo.cell_value(11, 1)+self.down_layGeo.cell_value(26,1),n)) )
        file.write("\n/**********灯（顶平台下）***************************************/")
        H_LIGHT2FL=self.lampLay(section_length[n]-self.down_layGeo.cell_value(16,1),n)#上灯距下法兰下端面的距离，调整焊缝后的
        H_LIGHT2PLATFORM = section_length[n] - H_LIGHT2FL#调整后上灯至平台的距离
        file.write("\nH_LIGHT2PLATFORM=" + str(round(H_LIGHT2PLATFORM, 1))+ "/* 至上法兰上端面")#中间段是至平台
        file.write("\nDI_LIGHT2PLATFORM=" + str(self.diCal(H_LIGHT2FL,n))+ "/*上灯处塔筒内径")
        file.write("\n/**********灯（底平台下）***************************************/")
        H_LIGHT2CONTROL_DOWN = self.lampLay(self.down_layGeo.cell_value(17,1),n)#调整焊缝后的
        file.write("\nH_LIGHT2CONTROL_DOWN=" + str(round(H_LIGHT2CONTROL_DOWN, 1))+ "/*至下法兰下端面距离")
        file.write("\nDI_LIGHT_CONTROL_DOWN=" + str(self.diCal(H_LIGHT2CONTROL_DOWN,n))+ "/*所在处塔筒内径")
        file.write("\n/**********灯（底平台上高灯）***************************************/")
        H_LIGHT_CONTROL_UP_H = self.lampLay(self.down_layGeo.cell_value(18,1), n)
        file.write("\nH_LIGHT_CONTROL_UP_H=" + str(round(H_LIGHT_CONTROL_UP_H, 1))+ "/*至下法兰下端面距离")
        file.write("\nDI_LIGHT_CONTROL_HIGH=" + str(self.diCal(H_LIGHT_CONTROL_UP_H,n))+ "/*所在处塔筒内径")
        file.write("\n/**********灯（底平台上低灯）***************************************/")
        H_LIGHT_CONTROL_UP_L = self.lampLay(self.down_layGeo.cell_value(19,1), n)
        file.write("\nH_LIGHT_CONTROL_UP_L=" + str(round(H_LIGHT_CONTROL_UP_L, 1))+ "/*至下法兰下端面距离")
        file.write("\nDI_LIGHT_CONTROL_LOW=" + str(self.diCal(H_LIGHT_CONTROL_UP_L,n))+ "/*所在处塔筒内径")
        file.write("\n/**********灯（升降机平台上）***************************************/")
        H_5LIGHT2FL = self.lampLay(self.down_layGeo.cell_value(8, 1)+self.down_layGeo.cell_value(20,1),n)
        H_LIGHT2INITIAL= H_5LIGHT2FL - self.down_layGeo.cell_value(8, 1)
        file.write("\nH_LIGHT2INITIAL=" + str(round(H_LIGHT2INITIAL, 1))+ "/*至升降机起始平台距离")
        file.write("\nDI_LIGHT_INITIAL=" + str(self.diCal(H_5LIGHT2FL,n))+ "/*所在处塔筒内径")
        file.write("\n/****底平台上方灭火器支架************************/")
        file.write("\nH_FE2CONTROL=" + str(round(self.down_layGeo.cell_value(14,1), 1))+ "/*至底平台距离")
        file.write("\nDI_FE_CONTROL=" + str(self.diCal(self.down_layGeo.cell_value(11, 1)+self.down_layGeo.cell_value(14,1),n))+ "/*所在处塔筒内径")
        file.write("\nANGLE_FE_CONTROL_I="+"360-18/*相对塔筒门的角度")
        file.write("\n/************衣帽挂钩安装位置信息***************************************************/")
        file.write("\nH_HOOK2CONTROL=" + str(round(self.down_layGeo.cell_value(15,1), 1))+ "/*至底平台距离")
        file.write("\nDI_HOOK=" + str(self.diCal(self.down_layGeo.cell_value(11, 1)+self.down_layGeo.cell_value(15,1),n))+ "/*衣帽挂钩处所在塔筒内径")
        file.write("\nANGLE_HOOK_I=" + "23/*第一个衣帽挂钩相对门方向位置，顺时针角度")
        file.write("\nANGLE_HOOK_II=" + "15/*第二个衣帽挂钩相对第一个衣帽挂钩的位置")
        file.write("\nD448=" + "360+$α /*门的方向，相对于x负轴，逆时针转")
        file.write("\n/***********吊耳位置信息***************************************************/")
        file.write("\nH_CONVERTER_BEAM=" + str(round(self.down_layGeo.cell_value(27,1), 1))+ "/*至底法兰下端面距离")
        file.write("\nDI_CONVERTER_BEAM=" + str(self.diCal(self.down_layGeo.cell_value(27, 1),n))+ "/*所在处塔筒内径")
        file.write("\nL_BEAM2CENTER=" + "725" )
        file.write("\n吊梁对称轴与x轴夹角=" + "38")
        file.write("\n/***********电梯漏电保护装置位置信息***************************************************/")
        file.write("\nH_SUPPORT2INITIAL=" + str(round(self.down_layGeo.cell_value(9,1), 1))+ "/*距离升降起始平台的位置")
        file.write("\nDI_SUPPORT_INITIAL=" + str(self.diCal(self.down_layGeo.cell_value(9, 1)+self.down_layGeo.cell_value(9,1),n)) + " /*电梯漏电保护支架所在处塔筒内径")
        file.write("\nANGLE_SUPPORT_INITIAL=" + "40/*电梯漏电保护装置相对y轴的偏转角度")
        file.write("\n/***********动力电缆桥架及变流、主控柜桥架位置信息**************")
        file.write("\nH_BRIDGE_POWER=" + str(round(self.down_layGeo.cell_value(10,1), 1))+ "/*至下法兰端面距离")
        file.write("\nD300=" + "2400" +"/*变流柜桥架相对底平台高度")
        file.write("\n$D298=" + "-1233/*变流柜桥架X轴方向位移")
        file.write("\nD299=" + "171/*变流柜桥架Y轴方向位移")
        file.write("\nD294=" + "2230/*主控柜桥架相对底平台高度")
        file.write("\n$D292=" + "-812.5 /*主控柜桥架X轴方向位移")
        file.write("\n$D293=" + "-943 /*主控柜桥架Y轴方向位移")
        file.write("\n/************塔架门安装位置信息***************************************************/")
        file.write("\nDA_DOOR=" + "DA_TOP /*门处所在的外径")
        file.write("\nANGLE_DOOR=" + "360+$α/*门的位置，相对y负轴逆时针")
        if isinstance(self.DoorGeo.cell_value(2, 12), float):  # 加强板
            file.write("\nH_DOOR=" + str(round(self.DoorGeo.cell_value(2, 12), 1)) + "/*门位置，至底法兰底面的距离")
            file.write("\n门框外漏=" + str((180-self.DoorGeo.cell_value(2,16))/2) )#小门框外漏的长度
            file.write("\nT1_REINFORCING=" + str(round(self.DoorGeo.cell_value(2,14), 1))+ "/**塔筒壁厚**")
            file.write("\nT2_REINFORCING=" + str(round(self.DoorGeo.cell_value(2, 16), 1)) + "/**补强板厚度**")
            file.write("\nd524=" + "DA_DOOR/2+(T2_REINFORCING -T1_REINFORCING)/2  /*补强板外径，半径/")
            file.write("\nd525=" + "d524-T2_REINFORCING    /*补强板内径,半径/")
        else:
            file.write("\nH_DOOR=" + str(round(self.DoorGeo.cell_value(2, 0), 1)) + "/*门位置，至底法兰底面的距离")
            file.write("\n门框外漏=" + str(round(self.DoorGeo.cell_value(2,8), 1)) )
            file.write("\nT1_REINFORCING=" + str(round(self.DoorGeo.cell_value(2,2), 1))+ "/**塔筒壁厚**")
        file.write("\n/*****************防雷螺柱定位关系***************")
        file.write("\nDI_BOTTOM_BUSH=" + "DI_BOTTOM  /*下法兰内径")
        file.write("\nDI_TOP_BUSH=" + "DI_TOP  /*上法兰")
        file.write(infoformat.down_skel_info)

    def flWrite(self,file,n):#连接法兰信息
        H_TOTAL = (self.TowerGeo.cell_value(self.flangePos()[n],3)-self.TowerGeo.cell_value(self.flangePos()[n],1))*1000#法兰高
        file.write(infoformat.weight_info)
        file.write( "/*****连接法兰" + str(n) + "参数****\n")
        file.write("DA=" + str(round(self.FlangeGeo.cell_value(n + 2, 1),1)) + "/*法兰外径\n")
        file.write("DI=" + str(round(self.FlangeGeo.cell_value(n + 2, 2),0)) + "/*法兰内径\n")
        file.write("DM=" + str(round(self.FlangeGeo.cell_value(n + 2, 3),0)) + "/*螺栓分度圆直径\n")
        file.write("TFL=" + str(round(self.FlangeGeo.cell_value(n + 2, 4),0)) + "/*法兰厚度\n")
        file.write("S=" + str(round(self.FlangeGeo.cell_value(n + 2, 5),1)) + "/*法兰颈厚\n")
        file.write("H_TOTAL=" + str(round(H_TOTAL,0)) + "/*法兰高\n")
        file.write("DHOLE=" + str(round(self.FlangeGeo.cell_value(n + 2, 8),0)) + "/*螺栓孔直径\n")
        file.write("N=" + str(round(self.FlangeGeo.cell_value(n + 2, 9),0)) + "/*螺栓数\n")

    def tflWrite(self,file,n):#底法兰信息写入
        H_TOTAL = (self.TowerGeo.cell_value(self.flangePos()[n],3)-self.TowerGeo.cell_value(self.flangePos()[n],1))*1000#法兰高
        file.write(infoformat.weight_info)
        file.write( "/*****塔架底法兰参数****\n")
        file.write("DA=" + str(round(self.FlangeGeo.cell_value(n + 2, 1),1)) + "/*T型法兰外径（筒壁外径）\n")
        file.write("DI=" + str(round(self.FlangeGeo.cell_value(n + 2, 2),0)) + "/*T型法兰内径\n")
        file.write("DM=" + str(round(self.FlangeGeo.cell_value(n + 2, 3),0)) + "/*螺栓分度圆直径\n")
        file.write("TFL=" + str(round(self.FlangeGeo.cell_value(n + 2, 4),0)) + "/*法兰厚度\n")
        file.write("S=" + str(round(self.FlangeGeo.cell_value(n + 2, 5),1)) + "/*法兰颈厚\n")
        file.write("H_TOTAL=" + str(round(H_TOTAL,0)) + "/*法兰高\n")
        file.write("DHOLE=" + str(round(self.FlangeGeo.cell_value(n + 2, 8),0)) + "/*T型法兰内侧螺栓孔直径\n")
        file.write("N_INNER=" + str(round(self.FlangeGeo.cell_value(n + 2, 9)/2,0)) + "/*T型法兰内侧螺栓数\n")
        file.write("Da_outer=" + str(round(self.FlangeGeo.cell_value(n + 2, 13),1)) + "/*T型法兰外径\n")
        file.write("Dm_outer=" + str(round(self.FlangeGeo.cell_value(n + 2, 14),0)) + "/*T型法兰外圈分度圆\n")
        file.write("dhole_outer=" + str(round(self.FlangeGeo.cell_value(n + 2, 8),0)) + "/*T型法兰外侧螺栓孔直径\n")
        file.write("N_OUTER=" + str(round(self.FlangeGeo.cell_value(n + 2, 9)/2,0)) + "/*T型法兰外侧螺栓数\n")
    #分片法兰信息
    def vflangeinfo(self,file):
        file.write(infoformat.flange_start)
        file.write(infoformat.flange_end)

    #分片段信息
    def vflangetowerinfo(self,file, n):
        file.write("/*********分片塔分缝参数************/\n")
        file.write("/**中径/\n")
        file.write("DA_MID_TOP=" + str(round(self.FlangeGeo.cell_value(n + 2, 1) - self.FlangeGeo.cell_value(n + 2, 5), 1)) + "/*下中径\n")
        file.write("DA_MID_BOTTOM=" + str(round(self.FlangeGeo.cell_value(n + 3, 1) - self.FlangeGeo.cell_value(n + 3, 5) , 1)) + "/*上中径\n")
        file.write(infoformat.dmwz)

    def midSkelW(self,file,n):#中间段的骨架信息
        section_length = self.secNLength()
        file.write(infoformat.skel_name)
        file.write("/*---------------------| 筒段 |------------------------------*/\n")
        file.write("SEC_H_total=" + str(section_length[n]) + "/*筒段总高\n\n")
        file.write("/*---------------------| 上法兰 |------------------------------*/\n")
        file.write("DA_TOP=" + str(round(self.FlangeGeo.cell_value(n + 3, 1), 1)) + "/*上法兰外径\n")
        file.write("DI_TOP=" + str(round(self.FlangeGeo.cell_value(n + 3, 2), 1)) + "/*上法兰内径\n")
        file.write("TFL_TOP=" + str(round(self.FlangeGeo.cell_value(n + 3, 4), 0)) + "/*上法兰厚\n")
        file.write("S_TOP=" + str(round(self.FlangeGeo.cell_value(n + 3, 5), 1)) + "/*上法兰颈厚\n\n")
        file.write("/*---------------------| 下法兰 |------------------------------*/\n")
        file.write("DA_BOTTOM=" + str(round(self.FlangeGeo.cell_value(n + 2, 1), 1)) + "/*下法兰外径\n")
        file.write("DI_BOTTOM=" + str(round(self.FlangeGeo.cell_value(n + 2, 2), 1)) + "/*下法兰内径\n")
        file.write("TFL_BOTTOM=" + str(round(self.FlangeGeo.cell_value(n + 2, 4), 0)) + "/*下法兰厚\n")
        file.write("S_BOTTOM=" + str(round(self.FlangeGeo.cell_value(n + 2, 5), 1)) + "/*下法兰颈厚\n\n")
        file.write("/*---------------------| 平台 |------------------------------*/\n")
        H_platform = self.stan_layGeo.cell_value(2, n)
        file.write("H_platform=" + str(round(self.stan_layGeo.cell_value(2, n), 0)) + "/*平台距离顶法兰距离\n\n")
        file.write("/*---------------------| 爬梯 |------------------------------*/\n")
        file.write("$H_LADDER_TOP= 0 /*爬梯位置\n")
        file.write("L_LADDER=" + str(section_length[n]) + "/*爬梯长度\n")
        file.write("L_LADDER_I =" + str(round(self.stan_layGeo.cell_value(3, n), 0)) + "/*爬梯支撑长度\n")
        file.write("W_LADDER_I =" + str(round(self.stan_layGeo.cell_value(4, n), 0)) + "/*爬梯支撑宽度\n")
        file.write("Alpha = atan((DA_BOTTOM - DA_TOP) / 2 / SEC_H_TOTAL)\n")
        file.write("H_L_LADDER = L_LADDER * cos(Alpha)\n")
        file.write("b = H_LIGHT2FL + 300 /*B截面高度\n")
        file.write("Ladder_up_circle = DA_TOP - 2 * S_TOP\n")
        file.write("Ladder_bottom_circle = DA_BOTTOM - 2 * S_BOTTOM\n\n")
        file.write("/*---------------------| 电缆线槽 |------------------------------*/\n")
        file.write("L_C=" + str(section_length[n]) + "/*电缆线槽长度\n\n")
        file.write("/*---------------------| 扶持 |------------------------------*/\n")
        file.write("H_SUPPORT=" + str(round(self.stan_layGeo.cell_value(12, n), 0)) + "/*扶持高度\n\n")
        file.write("/*---------------------| 中间段爬梯支撑 |------------------------------*/\n")
        DA_BOTTOM = round(float(self.FlangeGeo.cell_value(n + 2, 1)), 1)
        DA_TOP = round(float(self.FlangeGeo.cell_value(n + 3, 1)), 1)
        SEC_H_TOTAL = section_length[n]
        Alpha = math.atan((DA_BOTTOM - DA_TOP) / 2 / SEC_H_TOTAL)
        file.write(self.ls_heights(n, H_platform, Alpha)[0] + "\n\n")
        file.write("/*---------------------| 电缆线夹 |------------------------------*/\n")
        file.write(self.ls_heights(n, H_platform, Alpha)[1] + "\n")
        file.write("Cable_top_h = 1000/*最后一组电缆夹板相对于顶法兰上端面\n")
        Model = self.stan_layGeo.cell_value(5, n)
        windturbine_models = {
            "V12": {"L_CABLE": 420, "L1_CABLE_I": 800, "L2_CABLE_I": 800},
            "V15": {"L_CABLE": 420, "L1_CABLE_I": 800, "L2_CABLE_I": 800},
            "V17": {"L_CABLE": 800, "L1_CABLE_I": 650, "L2_CABLE_I": 650},
            "V19": {"L_CABLE": 560, "L1_CABLE_I": 650, "L2_CABLE_I": 650},
        }
        params = windturbine_models[Model]
        file.write(f"L_CABLE={params['L_CABLE']}/*电缆托架长度\n")
        file.write(f"L1_CABLE_I={params['L1_CABLE_I']}/*右侧电缆托架安装弦长\n")
        file.write(f"L2_CABLE_I={params['L2_CABLE_I']}/*左侧电缆托架安装弦长\n")
        file.write("di_cable_top=" + str(self.diCal(section_length[n] - 1000, n)) + "/*平台上方电缆夹板位置处塔筒内径\n")
        file.write("di_cable_down=" + str(self.diCal(980, n)) + "/*下方第一个电缆夹板位置处塔筒内径\n\n")
        file.write("/*---------------------| 照明灯 |------------------------------*/\n")
        file.write("H_LIGHT2FL=" + str(round(self.stan_layGeo.cell_value(7, n), 0)) + "/*下灯位置\n")
        file.write("LIGHT_BOTTOM_circle=" + str(self.diCal(self.stan_layGeo.cell_value(7, n), n)) + "/*下灯位置处塔筒内径\n")
        file.write("H_LIGHT2PLATFORM=" + str(round(self.stan_layGeo.cell_value(8, n), 0)) + "/*上灯位置\n")
        file.write("LIGHT_TOP_circle=" + str(self.diCal(section_length[n] - self.stan_layGeo.cell_value(8, n), n)) + "/*上灯位置处塔筒内径\n\n")
        file.write("/*---------------------| 爬梯安全锚点 |------------------------------*/\n")
        file.write("H_AP=" + str(round(self.stan_layGeo.cell_value(9, n), 0)) + "/*爬梯安全锚点安装高度\n\n")
        file.write("/*---------------------| B和C 二维视图所需信息 |------------------------------*/\n")
        file.write("DA_B=" + str(self.diCal(section_length[n] - 825, n)) + "/*B_B视图截面所在外径\n")
        file.write("DA_C=" + str(self.diCal(self.stan_layGeo.cell_value(12, n) + 400, n)) + "/*C_C视图截面所在外径\n")
        file.write("/*---------------------| 防雷螺柱定位 |------------------------------*/\n")
        file.write("B_B_A=" + str(round(self.stan_layGeo.cell_value(10, n), 0)) + "/*下端防雷螺柱安装角度\n")
        file.write("B_T_A=" + str(round(self.stan_layGeo.cell_value(11, n), 0)) + "/*上端防雷螺柱安装角度\n")
        file.write(infoformat.skel_comp)


def topSkelW(self,file,n):#顶段骨架
        file.write("SEC_H_total=" + str(section_length[n]) + "/*筒段总高\n")
        file.write("H_platform=" + str(round(self.stan_layGeo.cell_value(2,n),0)) + "/*平台位置\n")
        file.write("$H_LADDER_TOP=" + str(round(self.stan_layGeo.cell_value(3,n),0)) + "/*爬梯位置\n\n")
        file.write("L_LADDER=" + str(round(self.stan_layGeo.cell_value(6,n),0)) + "/*爬梯长度\n")
        file.write("L_C=" + str(round(self.stan_layGeo.cell_value(7,n),0)) + "/*电缆线槽长度\n\n")
        H_LIGHT2FL=self.lampLay(self.stan_layGeo.cell_value(4,n),n)#避开焊缝后的灯的位置，至下法兰下端面距离
        file.write("H_LIGHT2FL=" + str(round(H_LIGHT2FL,0)) + "/*下灯位置\n")#
        file.write("LIGHT_BOTTOM_circle=" + str(self.diCal(H_LIGHT2FL,n)) + "/*下灯位置处塔筒内径\n\n")
        H_2LIGHT2FL=self.lampLay(section_length[n]-self.stan_layGeo.cell_value(2,n)-self.stan_layGeo.cell_value(5,n),n)#上灯距下法兰下端面的距离，调整焊缝后的
        H_LIGHT2PLATFORM = section_length[n] - H_2LIGHT2FL - self.stan_layGeo.cell_value(2,n)#调整后上灯至平台的距离
        file.write("H_LIGHT2PLATFORM=" + str(round(H_LIGHT2PLATFORM,0)) + "/*上灯位置\n")
        file.write("LIGHT_TOP_circle=" + str(self.diCal(H_2LIGHT2FL, n)) + "/*上灯位置处塔筒内径\n\n")
        H_MIDLIGHT2FL = self.lampLay((H_2LIGHT2FL - H_LIGHT2FL) / 2 + H_LIGHT2FL , n)  # 中间灯具下法兰下端面的距离,避开焊缝后
        file.write('H_MIDLIGHT2FL=' + str(round(H_MIDLIGHT2FL,0)) + "/*中间灯位置\n")
        file.write('MIDLIGHT_DI='+ str(self.diCal(H_MIDLIGHT2FL,n)) + "/*中间灯处塔筒内径\n\n")
        file.write("L_CABLE=" + str(round(self.stan_layGeo.cell_value(8,n),0)) + "/*电缆托架长度L\n")
        file.write("di_cable_top=" + str(self.diCal(section_length[n] - 1000, n)) + "/*平台上方电缆夹板位置处塔筒内径\n")
        file.write("di_cable_bottom=" + str(self.diCal(980, n)) + "/*下方第一个电缆夹板位置处塔筒内径\n")
        file.write("b=H_LIGHT2FL+300/* B截面高度\n\n")
        file.write("SUPPORT2FL="+str(round(self.secNLength()[n] / 2 - 1200, 0))+'/*扶持位置，至下法兰下端面\n\n')
        file.write("/*********上法兰参数******\n")
        file.write("DA_TOP=" + str(round(self.FlangeGeo.cell_value(n + 3, 1),0)) + "/*上法兰外径\n")
        file.write("DI_TOP=" + str(round(self.FlangeGeo.cell_value(n + 3, 2),0)) + "/*上法兰内径\n")
        file.write("TFL_TOP=" + str(round(self.FlangeGeo.cell_value(n + 3, 4),0)) + "/*上法兰厚\n")
        file.write("S_TOP=" + str(round(self.FlangeGeo.cell_value(n + 3, 5),1)) + "/*上法兰颈厚\n")
        file.write("/*********下法兰参数******\n")
        file.write("DA_BOTTOM=" + str(round(self.FlangeGeo.cell_value(n + 2, 1),0)) + "/*下法兰外径\n")
        file.write("DI_BOTTOM=" + str(round(self.FlangeGeo.cell_value(n + 2, 2),0)) + "/*下法兰内径\n")
        file.write("S_BOTTOM=" + str(round(self.FlangeGeo.cell_value(n + 2, 5),1)) + "/*下法兰颈厚\n")
        file.write("TFL_BOTTOM=" + str(round(self.FlangeGeo.cell_value(n + 2, 4),0)) + "/*下法兰厚\n")
        file.write(infoformat.skel_comp)
        file.write('/*爬梯自动布局信息\n')
        file.write('D689=L_CABLE/*爬梯自动布局时，电缆托架的长度\n')
        file.write('D691=DA_BOTTOM-2*S_BOTTOM/*爬梯自动布局时，最下爬梯所在处内径\n')
        laSupport = self.laSupportLay(n)
        for num in range(18):
            file.write('LADDERSUPPORT'+ str(num+1) + '=' + str(laSupport[num]) + '\n')
        file.write('/*电缆夹板位置\n')
        file.write('d790 = DA_BOTTOM-2*S_BOTTOM/*爬梯自动布局时，最下电缆夹板所在处内径\n')
        file.write('d792 = 200/*L_CABLE/*爬梯自动布局时，电缆托架的长度\n')
        for num2 in range(18):
            file.write('$clamp'+ str(num2+1)+ '=-LADDERSUPPORT'+ str(num2+1) + '\n')
def topSkelW(self,file,n):#顶段骨架
    section_length = self.secNLength()
    H_PLAT_TOP= round(self.top_layGeo.cell_value(2,1),0) #顶平台位置
    H_PLAT_ROLLER= round(self.top_layGeo.cell_value(3,1),0)#马鞍平台位置
    H_ROLLER= round(self.top_layGeo.cell_value(4,1),0) #鞍托架位置\
    H_BEAM_LIFT= round(self.top_layGeo.cell_value(5,1),0) #升降机吊梁位置
    H_LIGHT2TP_UP = round(self.top_layGeo.cell_value(7,1),0)#灯位置_顶平台上\n
    H_LIGHT2TP_DOWN = round(self.top_layGeo.cell_value(8, 1), 0)#灯位置_顶平台下
    H_LIGHT2RP_UP =  round(self.top_layGeo.cell_value(9,1),0) #灯位置_马鞍平台上
    H_LIGHT2RP_DOWN = round(self.top_layGeo.cell_value(10,1),0) #灯位置_马鞍平台下
    H_LIGHT2FL = round(self.top_layGeo.cell_value(11,1),0)#下法兰处灯位置
    H_GL = round(self.top_layGeo.cell_value(15,1),0) #防雷接地耳板位置)
    H_TRAY_I = round(self.top_layGeo.cell_value(12,1),0) #电缆线槽一位置
    H_CLAMP_RP_I = round(self.top_layGeo.cell_value(14,1),0) #电缆夹板一位置_马鞍平台上方
    file.write(infoformat.skel_name)
    file.write("SEC_H_total=" + str(section_length[n]) + "/*筒段总高\n")
    file.write("H_PLAT_TOP=" + str(round(self.top_layGeo.cell_value(2,1),0)) + "/*顶平台位置\n")
    file.write("H_PLAT_ROLLER=" + str(round(self.top_layGeo.cell_value(3,1),0)) + "/*马鞍平台位置\n\n")
    file.write("H_ROLLER=" + str(round(self.top_layGeo.cell_value(4,1),0)) + "/*马鞍托架位置\n")
    file.write("H_BEAM_LIFT=" + str(round(self.top_layGeo.cell_value(5,1),0)) + "/*升降机吊梁位置\n\n")
    file.write("/*********灯位置参数********************************\n")
    file.write("H_LIGHT2TP_UP=" + str(round(self.top_layGeo.cell_value(7,1),0)) + "/*灯位置_顶平台上\n")
    #灯横过来后的内径信息：
    DI_LIGHT2TP_UP = self.diCal(section_length[n] - H_PLAT_TOP + H_LIGHT2TP_UP, n)#竖着时候的塔筒内径
    DI_LIGHT2TP_UP_H = round(math.sqrt((DI_LIGHT2TP_UP/2)**2-250**2)*2,1)#灯横向时候的内径
    file.write("DI_LIGHT2TP_UP=" + str(DI_LIGHT2TP_UP_H) + "/*灯内径_顶平台上\n\n")

    file.write("H_LIGHT2TP_DOWN=" + str(round(self.top_layGeo.cell_value(8,1),0)) + "/*灯位置_顶平台下\n")
    file.write("DI_LIGHT2TP_DOWN=" + str(self.diCal(section_length[n] - H_PLAT_TOP - H_LIGHT2TP_DOWN, n)) + "/*灯内径_顶平台下\n\n")
    file.write("H_LIGHT2RP_UP=" + str(round(self.top_layGeo.cell_value(9,1),0)) + "/*灯位置_马鞍平台上\n")
    file.write("DI_LIGHT2RP_UP=" + str(self.diCal(section_length[n] - H_PLAT_ROLLER + H_LIGHT2RP_UP, n)) + "/*灯内径_马鞍平台上\n\n")
    file.write("H_LIGHT2RP_DOWN=" + str(round(self.top_layGeo.cell_value(10,1),0)) + "/*灯位置_马鞍平台下\n")
    file.write("DI_LIGHT2RP_DOWN=" + str(self.diCal(section_length[n] - H_PLAT_ROLLER - H_LIGHT2RP_DOWN, n)) + "/*灯内径_马鞍平台下\n\n")
    file.write("H_LIGHT2FL=" + str(round(self.top_layGeo.cell_value(11,1),0)) + "/*下法兰处灯位置\n")
    file.write("DI_LIGHT2FL=" + str(self.diCal(H_LIGHT2FL, n)) + "/*下法兰处灯位置处塔筒内径\n")
    file.write("/***********************************************\n")
    file.write("H_GL=" + str(round(self.top_layGeo.cell_value(15,1),0)) + "/*防雷接地耳板位置\n")
    file.write("DI_GL=" + str(self.diCal(section_length[n] - H_PLAT_ROLLER + H_ROLLER - H_GL,n)) + "/*防雷接地耳板内径\n\n")
    file.write("H_TRAY_I=" + str(round(self.top_layGeo.cell_value(12,1),0)) + "/*电缆线槽一位置\n")
    file.write("DI_TRAY_I=" + str(self.diCal(section_length[n] - H_PLAT_ROLLER + H_TRAY_I, n)) + "/*电缆线槽一内径\n\n")
    file.write("H_TRAY_II=" + str(round(self.top_layGeo.cell_value(13,1),0)) + "/*电缆线槽二位置\n")
    file.write("DI_TRAY_II=" + str(self.diCal(section_length[n] - self.top_layGeo.cell_value(13,1), n)) + "/*电缆线槽二内径\n\n")
    file.write("H_CLAMP_RP_I=" + str(round(self.top_layGeo.cell_value(14,1),0)) + "/*电缆夹板一位置_马鞍平台上方\n")
    file.write("DI_CLAMP_RP_I=" + str(self.diCal(section_length[n] - H_PLAT_ROLLER + H_CLAMP_RP_I,n)) + "/*电缆夹板一内径_马鞍平台上方\n\n")
    file.write("DI_CLAMP=" + str(self.diCal(980,n)) + "/*底段电缆夹板内径\n")
    file.write("L_CABLE=" + str(round(round(self.top_layGeo.cell_value(19,1),0),0)) + "/*电缆托架长度L\n")
    file.write("H_BEAM_I=" + str(round(self.top_layGeo.cell_value(16,1),0)) + "/*电缆护套梁一位置（马鞍上方）\n")
    file.write("H_BEAM_II_I=" + str(round(self.top_layGeo.cell_value(17,1),0)) + "/*电缆护套梁二_1位置\n")
    file.write("H_BEAM_II_II=" + str(round(self.top_layGeo.cell_value(18,1),0)) + "/*电缆护套梁二_2位置\n\n")
    file.write("H_RING=" + str(round(self.top_layGeo.cell_value(20,1),0)) + "/*电缆隔环位置_常规\n")
    file.write("H_RING_ROLLER=" + str(round(self.top_layGeo.cell_value(21,1),0)) + "/*电缆隔环位置_马鞍之下\n\n")
    file.write("$H_LADDER_TOP=" + str(round(self.top_layGeo.cell_value(6,1),0)) + "/*爬梯与相邻段接口位置\n")
    file.write("L_LADDER=" + str(round(self.top_layGeo.cell_value(23,1),0)) + "/*爬梯长度 \n\n")
    file.write("DI_FIX_I=" + str(self.diCal(section_length[n]-1000,n)) + "/*上方第一个电缆线槽固定装置 \n")
    file.write("L_C_I=" + str(16200) + "/*电缆线槽I长度\n")
    file.write("L_C_II=" + str(7800) + "/*电缆线槽II长度\n")
    file.write("/*********顶法兰参数******\n")
    file.write("DA_TOP=" + str(round(self.FlangeGeo.cell_value(n + 3, 1),0)) + "/*上法兰外径\n")
    file.write("DI_TOP=" + str(round(self.FlangeGeo.cell_value(n + 3, 2),0)) + "/*上法兰内径\n")
    file.write("TFL_TOP=" + str(round(self.FlangeGeo.cell_value(n + 3, 4),0)) + "/*上法兰厚\n")
    file.write("S_TOP=" + str(round(self.FlangeGeo.cell_value(n + 3, 5),1)) + "/*上法兰颈厚\n")
    file.write("/*********下法兰参数******\n")
    file.write("DA_BOTTOM=" + str(round(self.FlangeGeo.cell_value(n + 2, 1),0)) + "/*下法兰外径\n")
    file.write("DI_BOTTOM=" + str(round(self.FlangeGeo.cell_value(n + 2, 2),0)) + "/*下法兰内径\n")
    file.write("S_BOTTOM=" + str(round(self.FlangeGeo.cell_value(n + 2, 5),1)) + "/*下法兰颈厚\n")
    file.write("TFL_BOTTOM=" + str(round(self.FlangeGeo.cell_value(n + 2, 4),0)) + "/*下法兰厚\n")
    file.write(infoformat.t_skel_comp)


def headdesiG(self,file):#写入开头信息
    file.write("/*******************设计指南**********************/\n")
    file.write(" 塔架总段数：" + str(self.flSecHqty()[2]))

def middesiG(self,file,n):#标准段
    section_length = self.secNLength()
    file.write("\n\n/**********第"+str(n+1)+"段塔筒*****************************\n")
    file.write(" 第"+ str(n+1) + "段塔筒总高度：" + str(section_length[n]))
    file.write("\n 第"+str(n+1)+"段塔筒顶平台所在处的内径："+str(self.diCal(section_length[n]-round(self.stan_layGeo.cell_value(2,n),0),n)))
    file.write("\n 第" +str(n+1)+"段塔筒扶持所在高度：" + str(round(section_length[n]/2-1200,0))+"  #如果需要扶持，此高度为距下法兰下端面的高度，此高度扶持在上下平台的中间")
    file.write("\n 第" +str(n+1)+"段扶持所在处内径："+str(self.diCal(section_length[n]/2-1200,n)))

def topdesiG(self,file,n):
    section_length = self.secNLength()
    file.write("\n\n/**********顶段塔筒*****************************\n")#顶段设计信息
    file.write(" 顶段塔筒总高度：" + str(section_length[n]))
    file.write("\n 顶段塔筒顶平台所在处的内径："+str(self.diCal(section_length[n]-round(self.top_layGeo.cell_value(2,1),0),n)))
    file.write("\n 顶段塔筒马鞍平台所在处内径：" + str(self.diCal(section_length[n]-round(self.top_layGeo.cell_value(3,1),0),n)))
    file.write("\n 顶段塔筒马鞍托架所在处内径：" + str(self.diCal(section_length[n]-self.top_layGeo.cell_value(3,1)+self.top_layGeo.cell_value(4,1),n)))
    file.write("\n 顶段电缆护套梁一所在处内径：" + str(self.diCal(section_length[n]-self.top_layGeo.cell_value(3,1)+self.top_layGeo.cell_value(16,1),n)))
    file.write("\n 顶段电缆护套梁二（下）所在处内径："+str(self.diCal(section_length[n]-self.top_layGeo.cell_value(3,1)+self.top_layGeo.cell_value(16,1)+self.top_layGeo.cell_value(17,1),n)))
    file.write("\n 升降机吊梁所在处内径：" + str(self.diCal(section_length[n]-self.top_layGeo.cell_value(3,1)+self.top_layGeo.cell_value(5,1),n)))
    #file.write("\n 爬梯距下法兰端面距离：" + str(self.diCal(section_qty,section_length,f_pos,TowerGeo,top_layGeo,n)) + "  #凸出为正，凹为负")
    #print(tool.lay_ladder(TowerGeo.cell_value()))

def bottomDesiG(self,file, n):#底段信息
    section_length = self.secNLength()
    file.write("\n\n/**********下段塔筒*****************************\n")
    file.write(" 下段塔筒总高度：" + str(section_length[n]))
    def headdesiG(self,file):#写入开头信息
        file.write("/*******************设计指南**********************/\n")
        file.write(" 塔架总段数：" + str(self.flSecHqty()[2]))

    def middesiG(self,file,n):#标准段
        section_length = self.secNLength()
        file.write("\n\n/**********第"+str(n+1)+"段塔筒*****************************\n")
        file.write(" 第"+ str(n+1) + "段塔筒总高度：" + str(section_length[n]))
        file.write("\n 第"+str(n+1)+"段塔筒顶平台所在处的内径："+str(self.diCal(section_length[n]-round(self.stan_layGeo.cell_value(2,n),0),n)))
        file.write("\n 第" +str(n+1)+"段塔筒扶持所在高度：" + str(round(section_length[n]/2-1200,0))+"  #如果需要扶持，此高度为距下法兰下端面的高度，此高度扶持在上下平台的中间")
        file.write("\n 第" +str(n+1)+"段扶持所在处内径："+str(self.diCal(section_length[n]/2-1200,n)))

    def topdesiG(self,file,n):
        section_length = self.secNLength()
        file.write("\n\n/**********顶段塔筒*****************************\n")#顶段设计信息
        file.write(" 顶段塔筒总高度：" + str(section_length[n]))
        file.write("\n 顶段塔筒顶平台所在处的内径："+str(self.diCal(section_length[n]-round(self.top_layGeo.cell_value(2,1),0),n)))
        file.write("\n 顶段塔筒马鞍平台所在处内径：" + str(self.diCal(section_length[n]-round(self.top_layGeo.cell_value(3,1),0),n)))
        file.write("\n 顶段塔筒马鞍托架所在处内径：" + str(self.diCal(section_length[n]-self.top_layGeo.cell_value(3,1)+self.top_layGeo.cell_value(4,1),n)))
        file.write("\n 顶段电缆护套梁一所在处内径：" + str(self.diCal(section_length[n]-self.top_layGeo.cell_value(3,1)+self.top_layGeo.cell_value(16,1),n)))
        file.write("\n 顶段电缆护套梁二（下）所在处内径："+str(self.diCal(section_length[n]-self.top_layGeo.cell_value(3,1)+self.top_layGeo.cell_value(16,1)+self.top_layGeo.cell_value(17,1),n)))
        file.write("\n 升降机吊梁所在处内径：" + str(self.diCal(section_length[n]-self.top_layGeo.cell_value(3,1)+self.top_layGeo.cell_value(5,1),n)))
        #file.write("\n 爬梯距下法兰端面距离：" + str(self.diCal(section_qty,section_length,f_pos,TowerGeo,top_layGeo,n)) + "  #凸出为正，凹为负")
        #print(tool.lay_ladder(TowerGeo.cell_value()))

    def bottomDesiG(self,file, n):#底段信息
        section_length = self.secNLength()
        file.write("\n\n/**********下段塔筒*****************************\n")
        file.write(" 下段塔筒总高度：" + str(section_length[n]))

class Excel_para(Excel_tool,Weight_tool,SuggestCode,PlmCode):
    def shellCut(self):
        section_qty = self.flSecHqty()['section_qty']  # 塔段数量
        workbook = xlwt.Workbook(encoding='utf-8')  # 创建txt文件写入几何信息
        worksheet = workbook.add_sheet('塔筒节下料表')  # 创建一个worksheet
        worksheet.write(0, 0, label='塔架筒节下料计算表')  # 写入excel# 参数对应 行, 列, 值
        worksheet.write(1, 0, label='筒节')
        worksheet.write(1, 1, label='下端直径')
        worksheet.write(1, 2, label='上端直径')
        worksheet.write(1, 3, label='筒节高度')
        worksheet.write(1, 4, label='筒节壁厚')
        worksheet.write(1, 5, label='钢板宽度')
        worksheet.write(1, 6, label='钢板长度')
        self.shellEx(worksheet)
        workbook.save(self.target_path + '\\塔筒筒节展开尺寸.xls')  # 保存

    def style(self,color):
        pattern = xlwt.Pattern()
        pattern.pattern = xlwt.Pattern.SOLID_PATTERN
        pattern.pattern_fore_colour = color
        style1 = xlwt.XFStyle()
        style1.pattern = pattern#名称颜色
        return style1

    def numberTake(self):  # 取号excel表
        section_length = self.secNLength()
        p = 0  # excel表中每一行的定位
        workbook = xlwt.Workbook()
        suggestcode = '推荐物料号：'


        # pattern = xlwt.Pattern()
        # pattern.pattern = xlwt.Pattern.SOLID_PATTERN
        # pattern.pattern_fore_colour = 57
        # style1 = xlwt.XFStyle()
        # style1.pattern = pattern#名称颜色
        style1 = self.style(57)#标题名称
        style2 = self.style(43)#计算出来的数据
        style3 = self.style(2)#推荐的数据
        style4 = self.style(71)  # 布局表中的数据
        for i in range(self.flSecHqty()[2]):
            if i == 0:  # 第一段
                #xsheet = w_numSheet.get_sheet(i)
                xsheet = workbook.add_sheet('第' + str(i + 1) + '段')
                # 调整列的宽度
                xsheet.col(0).width = 256*23
                xsheet.col(1).width = 256*23
                xsheet.col(2).width = 256*8
                xsheet.col(3).width = 256*20
                xsheet.col(4).width = 256 * 14
                xsheet.col(5).width = 256 * 20
                xsheet.col(6).width = 256 * 18
                xsheet.col(7).width = 256 * 21
                xsheet.col(8).width = 256 * 8
                xsheet.write(0, 0, '主体与法兰', style1)
                xsheet.write(0,1,'',style1)
                xsheet.write(1, 0, self.accSugget(i)[0])#附件总成物料号推荐

                xsheet.write(1, 1, '第' + str(i + 1) + '段塔筒（附件）总成', style1)
                xsheet.write(1, 2, self.accSugget(i)[-1])  # 附件总成重量
                xsheet.write(1, 3, '第' + str(i + 1) + '段塔筒总成高度：', style1)
                xsheet.write(1, 4, section_length[i],style2)  # 第一段总高度

                xsheet.write(2, 1, '第' + str(i + 1) + '段塔筒焊合', style1)
                xsheet.write(4, 1, '加强板', style1)
                if self.doorRein():
                    xsheet.write(4, 3, '此处塔筒壁厚t1：', style1)
                    xsheet.write(4, 4, self.DoorGeo.cell_value(2, 14),style2)
                    xsheet.write(4, 5, '加强板厚t2：', style1)
                    xsheet.write(4, 6, self.DoorGeo.cell_value(2, 16),style2)
                xsheet.write(5, 1, '塔架底法兰', style1)
                xsheet.write(5, 3, '重量(kg)：', style1)
                xsheet.write(5, 4, self.flangeWeight()[0],style2)  # 底法兰重量
                xsheet.write(6, 1, '连接法兰' + str(i + 1), style1)
                xsheet.write(6, 3, '重量(kg)：', style1)
                xsheet.write(6, 4, self.flangeWeight()[i + 1],style2)  # 法兰重量
                xsheet.write(3, 1, '筒体', style1)
                xsheet.write(3, 3, '筒体重量(kg)：', style1)
                xsheet.write(3, 4, self.towerGeoWeight(i),style2)  # 筒体重量
                xsheet.write(9, 0, '第' + str(i + 1) + '段平台', style1)
                xsheet.write(9, 1, '至上法兰上端面距离：', style1)
                xsheet.write(9, 2, self.down_layGeo.cell_value(6, 1),style4)  # 顶平台所在位置
                xsheet.write(9, 3, '所在处塔筒内径：', style1)
                xsheet.write(9, 4, self.diCal(self.secNLength()[i] - self.down_layGeo.cell_value(6, 1), i),style2)  # 顶平台所在处内径
                xsheet.write(17, 0, '升降机起始平台', style1)
                xsheet.write(17, 1, '至下法兰下端面距离：', style1)
                xsheet.write(17, 2, self.down_layGeo.cell_value(8, 1),style4)  # 升降机起始平台所在位置
                xsheet.write(17, 3, '所在处塔筒内径：', style1)
                xsheet.write(17, 4, self.diCal(self.down_layGeo.cell_value(8, 1), i),style2)  # 升降机起始平台所在处内径
                xsheet.write(25, 0, '电缆桥架', style1)
                xsheet.write(25, 1, '至下法兰下端面距离：', style1)
                xsheet.write(25, 2, self.down_layGeo.cell_value(10, 1), style4)  # 动力电缆桥架所在位置
                xsheet.write(35, 0, '门框', style1)
                xsheet.write(40, 0, '底平台围边', style1)
                xsheet.write(40, 1, '所在处塔筒内径', style1)

                if self.doorRein():
                    h_surrounding = self.down_layGeo.cell_value(12, 1) + self.down_layGeo.cell_value(11, 1)  # 顶平台围边高度
                    R_surrounding = self.diCal(h_surrounding, i)  # 围边所在处内径
                    xsheet.write(40, 2, R_surrounding, style2)  # 顶平台围边所在处内径
                    xsheet.write(40, 3, '围边半径R:', style1)
                    xsheet.write(40, 4, (R_surrounding - 30) / 2, style2)  #
                    RD_surrounding = (R_surrounding - (
                        self.DoorGeo.cell_value(2, 16) - self.DoorGeo.cell_value(2, 14)) - 30) / 2  # 围边门框所在处内径
                    xsheet.write(40, 5, '围边门框处半径R：', style1)
                    xsheet.write(40, 6, RD_surrounding, style2)
                    xsheet.write(40, 7, '门框横隔板后长度LL：', style1)
                    # 塔筒外径/2+(补强板厚度-塔筒壁厚)/2+（180-补强板厚度）/2-1831-180-15
                    L_surrounding = (self.DoorGeo.cell_value(2, 13) / 2 + (
                        self.DoorGeo.cell_value(2, 16) - self.DoorGeo.cell_value(2, 14)) / 2 + (
                                         180 - self.DoorGeo.cell_value(2, 16)) / 2 - 1831 - 180 - 15)
                    xsheet.write(40, 8, L_surrounding, style2)

                # h_surrounding = self.down_layGeo.cell_value(12, 1) + self.down_layGeo.cell_value(11, 1)  # 顶平台围边高度
                # R_surrounding = self.diCal(h_surrounding, i)  # 围边所在处内径
                # xsheet.write(40, 2, R_surrounding, style2)  # 顶平台围边所在处内径
                # xsheet.write(40, 3, '围边半径R:', style1)
                # xsheet.write(40, 4, (R_surrounding - 30) / 2,style2)#
                # RD_surrounding = (R_surrounding - (
                # self.DoorGeo.cell_value(2, 16) - self.DoorGeo.cell_value(2, 14)) - 30) / 2  # 围边门框所在处内径
                # xsheet.write(40, 5, '围边门框处半径R：', style1)
                # xsheet.write(40, 6, RD_surrounding, style2)
                # xsheet.write(40, 7, '门框横隔板后长度LL：', style1)
                # #塔筒外径/2+(补强板厚度-塔筒壁厚)/2+（180-补强板厚度）/2-1831-180-15
                # L_surrounding = (self.DoorGeo.cell_value(2, 13) / 2 + (
                # self.DoorGeo.cell_value(2, 16) - self.DoorGeo.cell_value(2, 14)) / 2 + (
                #                  180 - self.DoorGeo.cell_value(2, 16)) / 2 - 1831 - 180 - 15)
                # xsheet.write(40, 8, L_surrounding, style2)

                xsheet.write(44, 0, '直爬梯与大线槽', style1)
                xsheet.write(44, 1, '直爬梯长：', style1)
                xsheet.write(44, 2, self.down_layGeo.cell_value(4, 1), style1)
                xsheet.write(44, 3, '直爬梯上端位置：', style1)
                xsheet.write(44, 4, self.down_layGeo.cell_value(3, 1), style4)
                xsheet.write(44, 5, '直爬梯下段位置：', style1)
                xsheet.write(44, 6, self.ladderLay(i)[0], style2)  # 爬梯下段位置，是距离混凝土面的，梯子的混凝土面是120
                xsheet.write(46, 0, '大线槽', style1)
                xsheet.write(46, 1, '大线槽长：', style1)
                xsheet.write(46, 2, section_length[i] - self.down_layGeo.cell_value(10,
                                                                                    1) - 2 * 200, style2)  # 大线槽需要的长度，用筒段高-桥架高-两个线槽长
                xsheet.write(48, 0, '梯子固定支座', style1)
                xsheet.write(48,1,'',style1)
                xsheet.write(53, 0, '第一段塔筒总成重量(kg)：', style1)
                xsheet.write(54, 0, '第一段塔筒焊合重量(kg)：', style1)
                xsheet.write(54, 1, self.secNWeight()[i], style2)
                xsheet.write(55, 0, '附件重量(kg)：', style1)
                xsheet.write(10, 1, '第' + str(i + 1) + '段平台', style1)
                xsheet.write(11, 1, '平台面板', style1)
                xsheet.write(12, 1, 'H梁L=', style1)
                xsheet.write(13, 1, '横梁连接板=', style1)
                xsheet.write(14, 1, '横梁连接板', style1)
                xsheet.write(18, 1, '升降机起始平台', style1)
                xsheet.write(19, 1, '平台面板', style1)
                xsheet.write(20, 1, 'H梁L=', style1)
                xsheet.write(21, 1, '横梁连接板=', style1)
                xsheet.write(22, 1, '横梁连接板=', style1)
                xsheet.write(26, 1, '动力电缆桥架总成', style1)
                xsheet.write(27, 1, '电缆桥架固定梁L=', style1)
                xsheet.write(28, 1, '电缆桥架固定梁L=', style1)
                xsheet.write(29, 1, '横梁连接板=', style1)
                xsheet.write(30, 1, '横梁连接板=', style1)
                xsheet.write(31, 1, '横梁连接板=', style1)
                xsheet.write(32, 1, '横梁连接板=', style1)
                xsheet.write(35, 1, '宽：', style1)
                xsheet.write(36, 1, '门框T=，t=', style1)
                xsheet.write(37, 1, '门框竖隔板', style1)
                xsheet.write(41, 1, '底平台围边 ，，', style1)
                xsheet.write(42, 1, '围边', style1)
                xsheet.write(48, 3, 'L1:', style1)
                # L2:塔筒段长+爬梯位置-混凝图面高-爬梯长+140
                L2 = section_length[i] + self.down_layGeo.cell_value(3, 1) + self.down_layGeo.cell_value(2,
                                                                                                         1) - self.down_layGeo.cell_value(
                    4, 1) + 140
                xsheet.write(48, 4, L2 + 150, style2)
                xsheet.write(48, 5, 'L2:', style1)
                xsheet.write(48, 6, L2, style2)
                xsheet.write(49, 1, '梯架固定支座总成L1=,L2=', style1)
                xsheet.write(50, 1, '梯子固定支座', style1)
                xsheet.write(10, 3, suggestcode, style1)  # 推荐物料号
                xsheet.write(10, 4, self.platSugget(i)[0], style3)  # 推荐的物料号，顶平台
                xsheet.write(10, 5, self.platSugget(i)[1], style3)  # 对应的内径
                xsheet.write(10, 6, self.platSugget(i)[2], style3)  # 对应的lx
                xsheet.write(10, 7, self.platSugget(i)[3], style3)  # 对应的ly
                xsheet.write(18, 3, suggestcode, style1)  # 推荐物料号
                xsheet.write(18, 4, self.initialPlatSugget(i)[0], style3)  # 对应的物料号，升降机起始平台
                xsheet.write(18, 5, self.initialPlatSugget(i)[1], style3)  # 对应的物料号
                xsheet.write(18, 6, self.initialPlatSugget(i)[2], style3)  # 对应的物料号
                xsheet.write(18, 7, self.initialPlatSugget(i)[3], style3)  # 对应的物料号
                xsheet.write(25, 3, '所在处塔筒内径：', style1)
                xsheet.write(25, 4, self.diCal(self.down_layGeo.cell_value(10, 1), i), style2)  # 电缆桥架所在处内径
                xsheet.write(26, 3, suggestcode, style1)  # 推荐物料号
                xsheet.write(26, 4, self.cableBridgeSugget(i)[0], style3)  # 推荐的物料号
                xsheet.write(26, 5, self.cableBridgeSugget(i)[1] ,style3)  # 对应的内径
                xsheet.write(26, 6, self.cableBridgeSugget(i)[2], style3)  # 对应的名称
            elif i == self.flSecHqty()[2] - 1:  # 顶段
                xsheet = workbook.add_sheet('顶段')
                xsheet.col(0).width = 256*23
                xsheet.col(1).width = 256*23
                xsheet.col(2).width = 256*8
                xsheet.col(3).width = 256*20
                xsheet.col(4).width = 256 * 14
                xsheet.col(5).width = 256 * 20
                xsheet.col(6).width = 256 * 18
                xsheet.col(7).width = 256 * 21
                xsheet.col(8).width = 256 * 8
                xsheet.write(0, 0, '主体与法兰',style1)
                xsheet.write(0, 1,'',style1)
                xsheet.write(1, 3, '顶段塔筒总成高度：',style1)
                xsheet.write(1, 4, section_length[i],style2)  # 顶段总高度
                xsheet.write(7, 0, '顶平台',style1)
                xsheet.write(16, 0, '马鞍平台',style1)
                xsheet.write(24, 0, '马鞍托架',style1)
                xsheet.write(30, 0, '电缆护套梁一',style1)
                xsheet.write(38, 0, '电缆护套梁二',style1)
                xsheet.write(46, 0, '升降机吊梁',style1)
                xsheet.write(54, 0, '直爬梯与大线槽',style1)
                xsheet.write(59, 0, '顶段塔筒总成重量(kg)：',style1)
                xsheet.write(60, 0, '顶段塔筒焊合重量(kg)：',style1)
                xsheet.write(60, 1, self.secNWeight()[i],style2)
                xsheet.write(61, 0, '顶段附件重量(kg)：',style1)
                xsheet.write(1, 0, self.accSugget(i)[0])  # 附件总成物料号推荐
                xsheet.write(1, 2, self.accSugget(i)[-1])  # 附件总成重量
                xsheet.write(1, 1, '顶段塔筒（附件）总成',style1)
                xsheet.write(2, 1, '顶段塔筒焊合',style1)
                xsheet.write(3, 1, '筒体',style1)
                xsheet.write(3, 3, '筒体重量(kg)：',style1)
                xsheet.write(3, 4, self.towerGeoWeight(i),style2)  # 筒体重量
                # xsheet.write(4, 1, '连接法兰' + str(i + 1))
                xsheet.write(7, 1, '至上法兰上端面距离：',style1)
                xsheet.write(7, 2, self.top_layGeo.cell_value(2, 1),style4)  # 顶平台所在位置
                xsheet.write(8, 1, '顶段顶平台',style1)
                xsheet.write(9, 1, '平台面板',style1)
                xsheet.write(10, 1, 'H梁',style1)
                xsheet.write(11, 1, 'H梁L=',style1)
                xsheet.write(12, 1, '横梁连接板=',style1)
                xsheet.write(13, 1, '横梁连接板=',style1)
                xsheet.write(16, 1, '至上法兰上端面距离：',style1)
                xsheet.write(16, 2, self.top_layGeo.cell_value(3, 1),style4)  # 马鞍平台所在位置
                xsheet.write(17, 1, '马鞍平台',style1)
                xsheet.write(18, 1, '平台面板',style1)
                xsheet.write(19, 1, 'H梁L=',style1)
                xsheet.write(20, 1, '横梁连接板=',style1)
                xsheet.write(21, 1, '横梁连接板=',style1)
                xsheet.write(24, 1, '至马鞍平台距离：',style1)
                xsheet.write(24, 2, self.top_layGeo.cell_value(4, 1),style4)  # 马鞍托架所在位置
                xsheet.write(25, 1, '马鞍托架=',style1)
                xsheet.write(26, 1, '托架侧板一',style1)
                xsheet.write(27, 1, '托架侧板二',style1)
                xsheet.write(30, 1, '至马鞍平台距离：',style1)
                xsheet.write(30, 2, self.top_layGeo.cell_value(16, 1),style4)  # 电缆护套梁一所在位置
                xsheet.write(31, 1, '电缆护套梁一L= = ',style1)
                xsheet.write(32, 1, 'H梁',style1)
                xsheet.write(33, 1, '角部连接装置',style1)
                xsheet.write(34, 1, '连接板',style1)
                xsheet.write(35, 1, '支撑',style1)
                xsheet.write(38, 1, '至电缆护套梁一距离：',style1)
                xsheet.write(38, 2, self.top_layGeo.cell_value(17, 1),style4)  # 电缆护套梁二所在位置
                xsheet.write(39, 1, '电缆护套梁二L= = ',style1)
                xsheet.write(40, 1, 'H钢梁',style1)
                xsheet.write(41, 1, '角部连接装置',style1)
                xsheet.write(42, 1, '连接板',style1)
                xsheet.write(43, 1, '支撑',style1)
                xsheet.write(46, 1, '至马鞍平台距离： ',style1)
                xsheet.write(46, 2, self.top_layGeo.cell_value(5, 1),style4)  # 升降机吊梁所在位置
                xsheet.write(47, 1, '升降机吊梁总成L= ，R=',style1)
                xsheet.write(48, 1, '连接板',style1)
                xsheet.write(49, 1, '筋板',style1)
                xsheet.write(50, 1, '热轧槽钢',style1)
                xsheet.write(51, 1, '吊板',style1)
                xsheet.write(54, 1, '直爬梯长：',style1)
                xsheet.write(54, 2, self.top_layGeo.cell_value(23, 1),style4)
                xsheet.write(54, 3, '直爬梯上端位置：',style1)
                xsheet.write(54, 4, self.top_layGeo.cell_value(6, 1),style4)
                xsheet.write(54, 5, '直爬梯下段位置：',style1)
                xsheet.write(54, 6, self.ladderLay(i)[0],style2)
                xsheet.write(56, 1, '大线槽长：',style1)
                xsheet.write(56, 2,
                             self.secNLength()[i] - self.top_layGeo.cell_value(3, 1) + self.top_layGeo.cell_value(12,
                                                                                                                  1),style4)
                xsheet.write(8, 3, suggestcode,style1)  # 推荐物料号
                xsheet.write(7, 3, '所在处塔筒内径：',style1)
                xsheet.write(7, 4,
                             self.diCal(section_length[i] - round(self.top_layGeo.cell_value(2, 1), 0), i),style2)  # 顶平台所在处内径
                xsheet.write(8, 4, self.topPlatSugget(i)[0],style3)  # 推荐的物料号
                xsheet.write(8, 5, self.topPlatSugget(i)[1],style3)  # 对应的内径
                xsheet.write(8, 6, self.topPlatSugget(i)[2],style3)  # 对应名称
                xsheet.write(16, 3, '所在处塔筒内径：',style1)
                xsheet.write(16, 4, self.diCal(section_length[i] - round(self.top_layGeo.cell_value(3, 1), 0), i),style2)
                xsheet.write(17, 3, suggestcode,style1)  # 推荐物料号
                xsheet.write(17, 4, self.deflectionPlatSugget(i)[0],style3)  # 推荐的物料号，马鞍平台
                xsheet.write(17, 5, self.deflectionPlatSugget(i)[1],style3)  # 对应的内径
                xsheet.write(17, 6, self.deflectionPlatSugget(i)[2],style3)  # 对应的l_center2left
                xsheet.write(17, 7, self.deflectionPlatSugget(i)[3],style3)  # 对应旋转角度
                xsheet.write(24, 3, '所在处塔筒内径：',style1)
                xsheet.write(24, 4, self.diCal(
                    section_length[i] - self.top_layGeo.cell_value(3, 1) + self.top_layGeo.cell_value(4, 1), i),style2)  # 马鞍托架
                xsheet.write(25, 3, suggestcode,style1)  # 推荐物料号
                xsheet.write(25, 4, self.rollerSugget(i)[0],style3)  # 推荐的物料号，马鞍托架
                xsheet.write(25, 5, self.rollerSugget(i)[1],style3)  # 对应的内径
                xsheet.write(25, 6, self.rollerSugget(i)[2],style3)  # 对应的名称
                xsheet.write(30, 3, '所在处塔筒内径：',style1)
                xsheet.write(30, 4, self.diCal(
                    section_length[i] - self.top_layGeo.cell_value(3, 1) + self.top_layGeo.cell_value(16, 1),
                    i),style2)  # 电缆护套梁一
                xsheet.write(31, 3, suggestcode,style1)  # 推荐物料号
                xsheet.write(31, 4, self.cableProSugget1(i)[0],style3)  # 推荐的物料号，电缆护套梁一
                xsheet.write(31, 5, self.cableProSugget1(i)[1],style3)  # 对应的内径
                xsheet.write(31, 6, self.cableProSugget1(i)[2],style3)  # 对应名称
                xsheet.write(38, 3, '所在处塔筒内径：',style1)  # 电缆护套梁二
                xsheet.write(38, 4, self.diCal(
                    section_length[i] - self.top_layGeo.cell_value(3, 1) + self.top_layGeo.cell_value(16,
                                                                                                      1) + self.top_layGeo.cell_value(
                        17, 1), i),style2)
                xsheet.write(39, 3, suggestcode,style1)  # 推荐物料号
                xsheet.write(39, 4, self.cableProSugget2(i)[0],style3)  # 推荐的物料号，电缆护套梁二
                xsheet.write(39, 5, self.cableProSugget2(i)[1],style3)  # 对应的内径
                xsheet.write(39, 6, self.cableProSugget2(i)[2],style3)  # 对应名称
                xsheet.write(46, 3, '所在处塔筒内径：',style1)
                xsheet.write(46, 4, self.diCal(
                    section_length[i] - self.top_layGeo.cell_value(3, 1) + self.top_layGeo.cell_value(5, 1), i),style2)
            else:  # 中间段
                xsheet = workbook.add_sheet('第' + str(i + 1) + '段')
                xsheet.col(0).width = 256*23
                xsheet.col(1).width = 256*23
                xsheet.col(2).width = 256*8
                xsheet.col(3).width = 256*20
                xsheet.col(4).width = 256 * 14
                xsheet.col(5).width = 256 * 20
                xsheet.col(6).width = 256 * 18
                xsheet.col(7).width = 256 * 21
                xsheet.col(8).width = 256 * 8

                xsheet.write(0, 0, '主体与法兰',style1)
                xsheet.write(0,1,'',style1)
                xsheet.write(1, 0, self.accSugget(i)[0])  # 附件总成物料号推荐
                xsheet.write(1, 2, self.accSugget(i)[4])  # 附件总成重量
                xsheet.write(1, 1, '第' + str(i + 1) + '段塔筒（附件）总成',style1)
                xsheet.write(1, 3, '第' + str(i + 1) + '段塔筒总成高度：',style1)
                xsheet.write(1, 4, section_length[i],style2)  # 中间段总高度

                xsheet.write(7, 0, '第' + str(i + 1) + '段平台',style1)
                xsheet.write(15, 0, '第' + str(i + 1) + '段扶持',style1)
                xsheet.write(15, 1, '', style1)
                xsheet.write(23, 0, '直爬梯与大线槽',style1)
                xsheet.write(23, 1, '直爬梯长：',style1)
                xsheet.write(23, 2, self.stan_layGeo.cell_value(6, i),style4)
                xsheet.write(23, 3, '直爬梯上端位置：',style1)
                xsheet.write(23, 4, self.stan_layGeo.cell_value(3, i),style2)
                xsheet.write(23, 5, '直爬梯下段位置：',style1)
                xsheet.write(23, 6, self.ladderLay(i)[0],style2)  # 爬梯下段位置
                xsheet.write(28, 0, '第' + str(i + 1) + '段塔筒总成质量(kg)：',style1)
                xsheet.write(29, 0, '第' + str(i + 1) + '段塔筒焊合质量(kg)：',style1)
                xsheet.write(29, 1, self.secNWeight()[i],style2)
                xsheet.write(30, 0, '第' + str(i + 1) + '段塔筒附件质量(kg)：',style1)

                xsheet.write(2, 1, '第' + str(i + 1) + '段塔筒焊合',style1)

                xsheet.write(3, 1, '筒体',style1)
                xsheet.write(3, 3, '筒体重量(kg)：',style1)
                xsheet.write(3, 4, self.towerGeoWeight(i),style2)  # 筒体重量

                xsheet.write(4, 1, '连接法兰' + str(i + 1),style1)
                xsheet.write(4, 3, '重量(kg)：',style1)
                xsheet.write(4, 4, self.flangeWeight()[i + 1],style2)  # 法兰重量

                xsheet.write(7, 1, '距上法兰上端面：',style1)
                xsheet.write(7, 2, self.stan_layGeo.cell_value(2, i),style4)  # 顶平台所在位置
                xsheet.write(8, 1, '第' + str(i + 1) + '段平台',style1)
                xsheet.write(9, 1, '平台面板',style1)
                xsheet.write(10, 1, 'H梁L=',style1)
                xsheet.write(11, 1, '横梁连接板=',style1)
                xsheet.write(12, 1, '横梁连接板=',style1)
                xsheet.write(15, 3, '距下法兰下端面：',style1)
                xsheet.write(15, 4, str(round(self.secNLength()[i] / 2 - 1200, 0)),style2)
                xsheet.write(16, 1, str(i + 1) + '段扶持总成',style1)
                xsheet.write(17, 1, '底座',style1)
                xsheet.write(18, 1, '扶持L=',style1)
                xsheet.write(19, 1, '支撑板',style1)
                xsheet.write(20, 1, '筋板',style1)
                xsheet.write(7, 3, '所在处塔筒内径：',style1)
                xsheet.write(7, 4,
                             str(self.diCal(self.secNLength()[i] - self.stan_layGeo.cell_value(2, i), i)),style2)  # 顶平台所在处内径
                xsheet.write(8, 3, suggestcode,style1)  # 推荐物料号
                xsheet.write(8, 4, self.platSugget(i)[0],style3)  # 推荐的物料号，平台
                xsheet.write(8, 5, self.platSugget(i)[1],style3)  # 对应的内径
                xsheet.write(8, 6, self.platSugget(i)[2],style3)  # 对应的lx
                xsheet.write(8, 7, self.platSugget(i)[3],style3)  # 对应的ly
                xsheet.write(15, 5, '所在处塔筒内径：',style1)
                xsheet.write(15, 6, str(self.diCal(self.secNLength()[i] / 2 - 1200, i)),style2)  # 扶持所在处内径
        #w_numSheet.save(self.target_path + '\\TAD Table.xls')
        workbook.save(self.target_path + '\\TAD Table.xls')

    def numberTake_plm(self):  # 取号excel表，自动生成plm图号
        section_length = self.secNLength()
        p = 0  # excel表中每一行的定位
        workbook = xlwt.Workbook()
        suggestcode = '推荐物料号：'

        codeofplm = self.plmcode_main()
        print(codeofplm)

        # pattern = xlwt.Pattern()
        # pattern.pattern = xlwt.Pattern.SOLID_PATTERN
        # pattern.pattern_fore_colour = 57
        # style1 = xlwt.XFStyle()
        # style1.pattern = pattern#名称颜色
        style1 = self.style(57)#标题名称
        style2 = self.style(43)#计算出来的数据
        style3 = self.style(2)#推荐的数据
        style4 = self.style(71)  # 布局表中的数据
        for i in range(self.flSecHqty()[2]):
            if i == 0:  # 第一段
                #xsheet = w_numSheet.get_sheet(i)
                xsheet = workbook.add_sheet('第' + str(i + 1) + '段')
                # 调整列的宽度
                xsheet.col(0).width = 256*23
                xsheet.col(1).width = 256*23
                xsheet.col(2).width = 256*8
                xsheet.col(3).width = 256*20
                xsheet.col(4).width = 256 * 14
                xsheet.col(5).width = 256 * 20
                xsheet.col(6).width = 256 * 18
                xsheet.col(7).width = 256 * 21
                xsheet.col(8).width = 256 * 8
                xsheet.write(0, 0, '主体与法兰', style1)
                xsheet.write(0,1,'',style1)
                xsheet.write(1, 0, self.accSugget(i)[0])#附件总成物料号推荐
                xsheet.write(2, 0, codeofplm[0][0])  # 第一段塔筒焊合plm取号
                xsheet.write(3, 0, codeofplm[0][1])  # 第一段筒体plm取的号
                xsheet.write(4, 0, codeofplm[0][2])  # 第一段加强板plm取的号
                xsheet.write(5, 0, codeofplm[0][3])  # 第一段塔架底法兰plm取的号
                xsheet.write(6, 0, codeofplm[0][4])  # 第一段连接法兰一plm取的号

                xsheet.write(1, 1, '第' + str(i + 1) + '段塔筒总成', style1)
                xsheet.write(1, 2, self.accSugget(i)[-1])  # 附件总成重量
                xsheet.write(1, 3, '第' + str(i + 1) + '段塔筒总成高度：', style1)
                xsheet.write(1, 4, section_length[i],style2)  # 第一段总高度

                xsheet.write(2, 1, '第' + str(i + 1) + '段塔筒焊合', style1)
                xsheet.write(4, 1, '加强板', style1)
                if self.doorRein():
                    xsheet.write(4, 3, '此处塔筒壁厚t1：', style1)
                    xsheet.write(4, 4, self.DoorGeo.cell_value(2, 14),style2)
                    xsheet.write(4, 5, '加强板厚t2：', style1)
                    xsheet.write(4, 6, self.DoorGeo.cell_value(2, 16),style2)
                xsheet.write(5, 1, '塔架底法兰', style1)
                xsheet.write(5, 3, '重量(kg)：', style1)
                xsheet.write(5, 4, self.flangeWeight()[0],style2)  # 底法兰重量
                xsheet.write(6, 1, '连接法兰' + str(i + 1), style1)
                xsheet.write(6, 3, '重量(kg)：', style1)
                xsheet.write(6, 4, self.flangeWeight()[i + 1],style2)  # 法兰重量
                xsheet.write(3, 1, '筒体', style1)
                xsheet.write(3, 3, '筒体重量(kg)：', style1)
                xsheet.write(3, 4, self.towerGeoWeight(i),style2)  # 筒体重量
                xsheet.write(9, 0, '第' + str(i + 1) + '段平台', style1)
                xsheet.write(9, 1, '至上法兰上端面距离：', style1)
                xsheet.write(9, 2, self.down_layGeo.cell_value(6, 1),style4)  # 顶平台所在位置
                xsheet.write(9, 3, '所在处塔筒内径：', style1)
                xsheet.write(9, 4, self.diCal(self.secNLength()[i] - self.down_layGeo.cell_value(6, 1), i),style2)  # 顶平台所在处内径
                xsheet.write(17, 0, '升降机起始平台', style1)
                xsheet.write(17, 1, '至下法兰下端面距离：', style1)
                xsheet.write(17, 2, self.down_layGeo.cell_value(8, 1),style4)  # 升降机起始平台所在位置
                xsheet.write(17, 3, '所在处塔筒内径：', style1)
                xsheet.write(17, 4, self.diCal(self.down_layGeo.cell_value(8, 1), i),style2)  # 升降机起始平台所在处内径
                xsheet.write(25, 0, '电缆桥架', style1)
                xsheet.write(25, 1, '至下法兰下端面距离：', style1)
                xsheet.write(25, 2, self.down_layGeo.cell_value(10, 1), style4)  # 动力电缆桥架所在位置
                xsheet.write(35, 0, '门框', style1)
                xsheet.write(40, 0, '底平台围边', style1)
                xsheet.write(40, 1, '所在处塔筒内径', style1)

                if self.doorRein():
                    h_surrounding = self.down_layGeo.cell_value(12, 1) + self.down_layGeo.cell_value(11, 1)  # 顶平台围边高度
                    R_surrounding = self.diCal(h_surrounding, i)  # 围边所在处内径
                    xsheet.write(40, 2, R_surrounding, style2)  # 顶平台围边所在处内径
                    xsheet.write(40, 3, '围边半径R:', style1)
                    xsheet.write(40, 4, (R_surrounding - 30) / 2, style2)  #
                    RD_surrounding = (R_surrounding - (
                        self.DoorGeo.cell_value(2, 16) - self.DoorGeo.cell_value(2, 14)) - 30) / 2  # 围边门框所在处内径
                    xsheet.write(40, 5, '围边门框处半径R：', style1)
                    xsheet.write(40, 6, RD_surrounding, style2)
                    xsheet.write(40, 7, '门框横隔板后长度LL：', style1)
                    # 塔筒外径/2+(补强板厚度-塔筒壁厚)/2+（180-补强板厚度）/2-1831-180-15
                    L_surrounding = (self.DoorGeo.cell_value(2, 13) / 2 + (
                        self.DoorGeo.cell_value(2, 16) - self.DoorGeo.cell_value(2, 14)) / 2 + (
                                         180 - self.DoorGeo.cell_value(2, 16)) / 2 - 1831 - 180 - 15)
                    xsheet.write(40, 8, L_surrounding, style2)

                xsheet.write(44, 0, '直爬梯与大线槽', style1)
                xsheet.write(44, 1, '直爬梯长：', style1)
                xsheet.write(44, 2, self.down_layGeo.cell_value(4, 1), style1)
                xsheet.write(44, 3, '直爬梯上端位置：', style1)
                xsheet.write(44, 4, self.down_layGeo.cell_value(3, 1), style4)
                xsheet.write(44, 5, '直爬梯下段位置：', style1)
                xsheet.write(44, 6, self.ladderLay(i)[0], style2)  # 爬梯下段位置，是距离混凝土面的，梯子的混凝土面是120
                xsheet.write(46, 0, '大线槽', style1)
                xsheet.write(46, 1, '大线槽长：', style1)
                xsheet.write(46, 2, section_length[i] - self.down_layGeo.cell_value(10,
                                                                                    1) - 2 * 200, style2)  # 大线槽需要的长度，用筒段高-桥架高-两个线槽长
                xsheet.write(48, 0, '梯子固定支座', style1)
                xsheet.write(48,1,'',style1)
                xsheet.write(53, 0, '第一段塔筒总成重量(kg)：', style1)
                xsheet.write(54, 0, '第一段塔筒焊合重量(kg)：', style1)
                xsheet.write(54, 1, self.secNWeight()[i], style2)
                xsheet.write(55, 0, '附件重量(kg)：', style1)
                xsheet.write(10, 1, '第' + str(i + 1) + '段平台', style1)
                xsheet.write(11, 1, '平台面板', style1)
                xsheet.write(12, 1, 'H梁L=', style1)
                xsheet.write(13, 1, '横梁连接板=', style1)
                xsheet.write(14, 1, '横梁连接板', style1)
                xsheet.write(18, 1, '升降机起始平台', style1)
                xsheet.write(19, 1, '平台面板', style1)
                xsheet.write(20, 1, 'H梁L=', style1)
                xsheet.write(21, 1, '横梁连接板=', style1)
                xsheet.write(22, 1, '横梁连接板=', style1)
                xsheet.write(26, 1, '动力电缆桥架总成', style1)
                xsheet.write(27, 1, '电缆桥架固定梁L=', style1)
                xsheet.write(28, 1, '电缆桥架固定梁L=', style1)
                xsheet.write(29, 1, '横梁连接板=', style1)
                xsheet.write(30, 1, '横梁连接板=', style1)
                xsheet.write(31, 1, '横梁连接板=', style1)
                xsheet.write(32, 1, '横梁连接板=', style1)
                xsheet.write(35, 1, '宽：', style1)
                xsheet.write(36, 1, '门框T=，t=', style1)
                xsheet.write(37, 1, '门框竖隔板', style1)
                xsheet.write(41, 1, '底平台围边 ，，', style1)
                xsheet.write(42, 1, '围边', style1)
                xsheet.write(48, 3, 'L1:', style1)
                # L2:塔筒段长+爬梯位置-混凝图面高-爬梯长+140
                L2 = section_length[i] + self.down_layGeo.cell_value(3, 1) + self.down_layGeo.cell_value(2,
                                                                                                         1) - self.down_layGeo.cell_value(
                    4, 1) + 140
                xsheet.write(48, 4, L2 + 150, style2)
                xsheet.write(48, 5, 'L2:', style1)
                xsheet.write(48, 6, L2, style2)
                xsheet.write(49, 1, '梯架固定支座总成L1=,L2=', style1)
                xsheet.write(50, 1, '梯子固定支座', style1)
                xsheet.write(10, 3, suggestcode, style1)  # 推荐物料号
                xsheet.write(10, 4, self.platSugget(i)[0], style3)  # 推荐的物料号，顶平台
                xsheet.write(10, 5, self.platSugget(i)[1], style3)  # 对应的内径
                xsheet.write(10, 6, self.platSugget(i)[2], style3)  # 对应的lx
                xsheet.write(10, 7, self.platSugget(i)[3], style3)  # 对应的ly
                xsheet.write(18, 3, suggestcode, style1)  # 推荐物料号
                xsheet.write(18, 4, self.initialPlatSugget(i)[0], style3)  # 对应的物料号，升降机起始平台
                xsheet.write(18, 5, self.initialPlatSugget(i)[1], style3)  # 对应的物料号
                xsheet.write(18, 6, self.initialPlatSugget(i)[2], style3)  # 对应的物料号
                xsheet.write(18, 7, self.initialPlatSugget(i)[3], style3)  # 对应的物料号
                xsheet.write(25, 3, '所在处塔筒内径：', style1)
                xsheet.write(25, 4, self.diCal(self.down_layGeo.cell_value(10, 1), i), style2)  # 电缆桥架所在处内径
                xsheet.write(26, 3, suggestcode, style1)  # 推荐物料号
                xsheet.write(26, 4, self.cableBridgeSugget(i)[0], style3)  # 推荐的物料号
                xsheet.write(26, 5, self.cableBridgeSugget(i)[1] ,style3)  # 对应的内径
                xsheet.write(26, 6, self.cableBridgeSugget(i)[2], style3)  # 对应的名称
            elif i == self.flSecHqty()[2] - 1:  # 顶段
                xsheet = workbook.add_sheet('顶段')
                xsheet.col(0).width = 256*23
                xsheet.col(1).width = 256*23
                xsheet.col(2).width = 256*8
                xsheet.col(3).width = 256*20
                xsheet.col(4).width = 256 * 14
                xsheet.col(5).width = 256 * 20
                xsheet.col(6).width = 256 * 18
                xsheet.col(7).width = 256 * 21
                xsheet.col(8).width = 256 * 8
                xsheet.write(0, 0, '主体与法兰',style1)
                xsheet.write(0, 1,'',style1)
                xsheet.write(1, 3, '顶段塔筒总成高度：',style1)
                xsheet.write(1, 4, section_length[i],style2)  # 顶段总高度
                xsheet.write(7, 0, '顶平台',style1)
                xsheet.write(16, 0, '马鞍平台',style1)
                xsheet.write(24, 0, '马鞍托架',style1)
                xsheet.write(30, 0, '电缆护套梁一',style1)
                xsheet.write(38, 0, '电缆护套梁二',style1)
                xsheet.write(46, 0, '升降机吊梁',style1)
                xsheet.write(54, 0, '直爬梯与大线槽',style1)
                xsheet.write(59, 0, '顶段塔筒总成重量(kg)：',style1)
                xsheet.write(60, 0, '顶段塔筒焊合重量(kg)：',style1)
                xsheet.write(60, 1, self.secNWeight()[i],style2)
                xsheet.write(61, 0, '顶段附件重量(kg)：',style1)
                xsheet.write(1, 0, self.accSugget(i)[0])  # 附件总成物料号推荐
                xsheet.write(2, 0, codeofplm[-1][0])  # 顶段塔筒焊合plm取号
                xsheet.write(3, 0, codeofplm[-1][1])  # 顶段筒体plm取的号


                xsheet.write(1, 2, self.accSugget(i)[-1])  # 附件总成重量
                xsheet.write(1, 1, '顶段塔筒总成',style1)
                xsheet.write(2, 1, '顶段塔筒焊合',style1)
                xsheet.write(3, 1, '筒体',style1)
                xsheet.write(3, 3, '筒体重量(kg)：',style1)
                xsheet.write(3, 4, self.towerGeoWeight(i),style2)  # 筒体重量
                # xsheet.write(4, 1, '连接法兰' + str(i + 1))
                xsheet.write(7, 1, '至上法兰上端面距离：',style1)
                xsheet.write(7, 2, self.top_layGeo.cell_value(2, 1),style4)  # 顶平台所在位置
                xsheet.write(8, 1, '顶段顶平台',style1)
                xsheet.write(9, 1, '平台面板',style1)
                xsheet.write(10, 1, 'H梁',style1)
                xsheet.write(11, 1, 'H梁L=',style1)
                xsheet.write(12, 1, '横梁连接板=',style1)
                xsheet.write(13, 1, '横梁连接板=',style1)
                xsheet.write(16, 1, '至上法兰上端面距离：',style1)
                xsheet.write(16, 2, self.top_layGeo.cell_value(3, 1),style4)  # 马鞍平台所在位置
                xsheet.write(17, 1, '马鞍平台',style1)
                xsheet.write(18, 1, '平台面板',style1)
                xsheet.write(19, 1, 'H梁L=',style1)
                xsheet.write(20, 1, '横梁连接板=',style1)
                xsheet.write(21, 1, '横梁连接板=',style1)
                xsheet.write(24, 1, '至马鞍平台距离：',style1)
                xsheet.write(24, 2, self.top_layGeo.cell_value(4, 1),style4)  # 马鞍托架所在位置
                xsheet.write(25, 1, '马鞍托架=',style1)
                xsheet.write(26, 1, '托架侧板一',style1)
                xsheet.write(27, 1, '托架侧板二',style1)
                xsheet.write(30, 1, '至马鞍平台距离：',style1)
                xsheet.write(30, 2, self.top_layGeo.cell_value(16, 1),style4)  # 电缆护套梁一所在位置
                xsheet.write(31, 1, '电缆护套梁一L= = ',style1)
                xsheet.write(32, 1, 'H梁',style1)
                xsheet.write(33, 1, '角部连接装置',style1)
                xsheet.write(34, 1, '连接板',style1)
                xsheet.write(35, 1, '支撑',style1)
                xsheet.write(38, 1, '至电缆护套梁一距离：',style1)
                xsheet.write(38, 2, self.top_layGeo.cell_value(17, 1),style4)  # 电缆护套梁二所在位置
                xsheet.write(39, 1, '电缆护套梁二L= = ',style1)
                xsheet.write(40, 1, 'H钢梁',style1)
                xsheet.write(41, 1, '角部连接装置',style1)
                xsheet.write(42, 1, '连接板',style1)
                xsheet.write(43, 1, '支撑',style1)
                xsheet.write(46, 1, '至马鞍平台距离： ',style1)
                xsheet.write(46, 2, self.top_layGeo.cell_value(5, 1),style4)  # 升降机吊梁所在位置
                xsheet.write(47, 1, '升降机吊梁总成L= ，R=',style1)
                xsheet.write(48, 1, '连接板',style1)
                xsheet.write(49, 1, '筋板',style1)
                xsheet.write(50, 1, '热轧槽钢',style1)
                xsheet.write(51, 1, '吊板',style1)
                xsheet.write(54, 1, '直爬梯长：',style1)
                xsheet.write(54, 2, self.top_layGeo.cell_value(23, 1),style4)
                xsheet.write(54, 3, '直爬梯上端位置：',style1)
                xsheet.write(54, 4, self.top_layGeo.cell_value(6, 1),style4)
                xsheet.write(54, 5, '直爬梯下段位置：',style1)
                xsheet.write(54, 6, self.ladderLay(i)[0],style2)
                xsheet.write(56, 1, '大线槽长：',style1)
                xsheet.write(56, 2,
                             self.secNLength()[i] - self.top_layGeo.cell_value(3, 1) + self.top_layGeo.cell_value(12,
                                                                                                                  1),style4)
                xsheet.write(8, 3, suggestcode,style1)  # 推荐物料号
                xsheet.write(7, 3, '所在处塔筒内径：',style1)
                xsheet.write(7, 4,
                             self.diCal(section_length[i] - round(self.top_layGeo.cell_value(2, 1), 0), i),style2)  # 顶平台所在处内径
                xsheet.write(8, 4, self.topPlatSugget(i)[0],style3)  # 推荐的物料号
                xsheet.write(8, 5, self.topPlatSugget(i)[1],style3)  # 对应的内径
                xsheet.write(8, 6, self.topPlatSugget(i)[2],style3)  # 对应名称
                xsheet.write(16, 3, '所在处塔筒内径：',style1)
                xsheet.write(16, 4, self.diCal(section_length[i] - round(self.top_layGeo.cell_value(3, 1), 0), i),style2)
                xsheet.write(17, 3, suggestcode,style1)  # 推荐物料号
                xsheet.write(17, 4, self.deflectionPlatSugget(i)[0],style3)  # 推荐的物料号，马鞍平台
                xsheet.write(17, 5, self.deflectionPlatSugget(i)[1],style3)  # 对应的内径
                xsheet.write(17, 6, self.deflectionPlatSugget(i)[2],style3)  # 对应的l_center2left
                xsheet.write(17, 7, self.deflectionPlatSugget(i)[3],style3)  # 对应旋转角度
                xsheet.write(24, 3, '所在处塔筒内径：',style1)
                xsheet.write(24, 4, self.diCal(
                    section_length[i] - self.top_layGeo.cell_value(3, 1) + self.top_layGeo.cell_value(4, 1), i),style2)  # 马鞍托架
                xsheet.write(25, 3, suggestcode,style1)  # 推荐物料号
                xsheet.write(25, 4, self.rollerSugget(i)[0],style3)  # 推荐的物料号，马鞍托架
                xsheet.write(25, 5, self.rollerSugget(i)[1],style3)  # 对应的内径
                xsheet.write(25, 6, self.rollerSugget(i)[2],style3)  # 对应的名称
                xsheet.write(30, 3, '所在处塔筒内径：',style1)
                xsheet.write(30, 4, self.diCal(
                    section_length[i] - self.top_layGeo.cell_value(3, 1) + self.top_layGeo.cell_value(16, 1),
                    i),style2)  # 电缆护套梁一
                xsheet.write(31, 3, suggestcode,style1)  # 推荐物料号
                xsheet.write(31, 4, self.cableProSugget1(i)[0],style3)  # 推荐的物料号，电缆护套梁一
                xsheet.write(31, 5, self.cableProSugget1(i)[1],style3)  # 对应的内径
                xsheet.write(31, 6, self.cableProSugget1(i)[2],style3)  # 对应名称
                xsheet.write(38, 3, '所在处塔筒内径：',style1)  # 电缆护套梁二
                xsheet.write(38, 4, self.diCal(
                    section_length[i] - self.top_layGeo.cell_value(3, 1) + self.top_layGeo.cell_value(16,
                                                                                                      1) + self.top_layGeo.cell_value(
                        17, 1), i),style2)
                xsheet.write(39, 3, suggestcode,style1)  # 推荐物料号
                xsheet.write(39, 4, self.cableProSugget2(i)[0],style3)  # 推荐的物料号，电缆护套梁二
                xsheet.write(39, 5, self.cableProSugget2(i)[1],style3)  # 对应的内径
                xsheet.write(39, 6, self.cableProSugget2(i)[2],style3)  # 对应名称
                xsheet.write(46, 3, '所在处塔筒内径：',style1)
                xsheet.write(46, 4, self.diCal(
                    section_length[i] - self.top_layGeo.cell_value(3, 1) + self.top_layGeo.cell_value(5, 1), i),style2)
            else:  # 中间段
                xsheet = workbook.add_sheet('第' + str(i + 1) + '段')
                xsheet.col(0).width = 256*23
                xsheet.col(1).width = 256*23
                xsheet.col(2).width = 256*8
                xsheet.col(3).width = 256*20
                xsheet.col(4).width = 256 * 14
                xsheet.col(5).width = 256 * 20
                xsheet.col(6).width = 256 * 18
                xsheet.col(7).width = 256 * 21
                xsheet.col(8).width = 256 * 8

                xsheet.write(0, 0, '主体与法兰',style1)
                xsheet.write(0,1,'',style1)
                xsheet.write(1, 0, self.accSugget(i)[0])  # 附件总成物料号推荐
                xsheet.write(2, 0, codeofplm[i][0])  # 第i+1段塔筒焊合plm取号
                xsheet.write(3, 0, codeofplm[i][1])  # 第i+1段筒体plm取的号
                xsheet.write(4, 0, codeofplm[i][2])  # 第i+1段连接法兰


                xsheet.write(1, 2, self.accSugget(i)[4])  # 附件总成重量
                xsheet.write(1, 1, '第' + str(i + 1) + '段塔筒总成',style1)
                xsheet.write(1, 3, '第' + str(i + 1) + '段塔筒总成高度：',style1)
                xsheet.write(1, 4, section_length[i],style2)  # 中间段总高度

                xsheet.write(7, 0, '第' + str(i + 1) + '段平台',style1)
                xsheet.write(15, 0, '第' + str(i + 1) + '段扶持',style1)
                xsheet.write(15, 1, '', style1)
                xsheet.write(23, 0, '直爬梯与大线槽',style1)
                xsheet.write(23, 1, '直爬梯长：',style1)
                xsheet.write(23, 2, self.stan_layGeo.cell_value(6, i),style4)
                xsheet.write(23, 3, '直爬梯上端位置：',style1)
                xsheet.write(23, 4, self.stan_layGeo.cell_value(3, i),style2)
                xsheet.write(23, 5, '直爬梯下段位置：',style1)
                xsheet.write(23, 6, self.ladderLay(i)[0],style2)  # 爬梯下段位置
                xsheet.write(28, 0, '第' + str(i + 1) + '段塔筒总成质量(kg)：',style1)
                xsheet.write(29, 0, '第' + str(i + 1) + '段塔筒焊合质量(kg)：',style1)
                xsheet.write(29, 1, self.secNWeight()[i],style2)
                xsheet.write(30, 0, '第' + str(i + 1) + '段塔筒附件质量(kg)：',style1)

                xsheet.write(2, 1, '第' + str(i + 1) + '段塔筒焊合',style1)

                xsheet.write(3, 1, '筒体',style1)
                xsheet.write(3, 3, '筒体重量(kg)：',style1)
                xsheet.write(3, 4, self.towerGeoWeight(i),style2)  # 筒体重量

                xsheet.write(4, 1, '连接法兰' + str(i + 1),style1)
                xsheet.write(4, 3, '重量(kg)：',style1)
                xsheet.write(4, 4, self.flangeWeight()[i + 1],style2)  # 法兰重量

                xsheet.write(7, 1, '距上法兰上端面：',style1)
                xsheet.write(7, 2, self.stan_layGeo.cell_value(2, i),style4)  # 顶平台所在位置
                xsheet.write(8, 1, '第' + str(i + 1) + '段平台',style1)
                xsheet.write(9, 1, '平台面板',style1)
                xsheet.write(10, 1, 'H梁L=',style1)
                xsheet.write(11, 1, '横梁连接板=',style1)
                xsheet.write(12, 1, '横梁连接板=',style1)
                xsheet.write(15, 3, '距下法兰下端面：',style1)
                xsheet.write(15, 4, str(round(self.secNLength()[i] / 2 - 1200, 0)),style2)
                xsheet.write(16, 1, str(i + 1) + '段扶持总成',style1)
                xsheet.write(17, 1, '底座',style1)
                xsheet.write(18, 1, '扶持L=',style1)
                xsheet.write(19, 1, '支撑板',style1)
                xsheet.write(20, 1, '筋板',style1)
                xsheet.write(7, 3, '所在处塔筒内径：',style1)
                xsheet.write(7, 4,
                             str(self.diCal(self.secNLength()[i] - self.stan_layGeo.cell_value(2, i), i)),style2)  # 顶平台所在处内径
                xsheet.write(8, 3, suggestcode,style1)  # 推荐物料号
                xsheet.write(8, 4, self.platSugget(i)[0],style3)  # 推荐的物料号，平台
                xsheet.write(8, 5, self.platSugget(i)[1],style3)  # 对应的内径
                xsheet.write(8, 6, self.platSugget(i)[2],style3)  # 对应的lx
                xsheet.write(8, 7, self.platSugget(i)[3],style3)  # 对应的ly
                xsheet.write(15, 5, '所在处塔筒内径：',style1)
                xsheet.write(15, 6, str(self.diCal(self.secNLength()[i] / 2 - 1200, i)),style2)  # 扶持所在处内径
        #w_numSheet.save(self.target_path + '\\TAD Table.xls')
        workbook.save(self.target_path + '\\TAD Table.xls')