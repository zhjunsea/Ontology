import xlrd
import os
import math
import shutil#高级文件操作的包，这里主要用于文件的删除操作
import openpyxl

def getsheet(path):#path代表文件的路径地址
    book = xlrd.open_workbook(path)
    return book

def read(path,n):#path代表文件的路径地址，n代表excel表的第n+1个表单
    book = xlrd.open_workbook(path)
    sheets = book.sheet_by_index(n)#n代表第几个表单
    return sheets

def dir_exists(target_path): # 判断文件目录是否存在，
    if os.path.exists(target_path):
        #print('文件已存在')
        shutil.rmtree(target_path)#删除目录，包括目录里的所有文件夹与文件
        os.makedirs(target_path)  # 创建数据存放目录
    else:
        #print('不存在')
        os.makedirs(target_path)  # 创建数据存放目录

def get_filename(geo_path):#得到excel文件的名字，不包括扩展名
    filename = os.path.splitext(geo_path.split('/')[-1])[0]
    return filename


def dirsMake(target_path, n):
    for i in range(n - 1):
        os.makedirs(os.path.join(target_path, f"第{i + 1}段"))
    os.makedirs(os.path.join(target_path, "顶段"))
    os.makedirs(os.path.join(target_path, "连接法兰"))  # 创建连接法兰的文件夹


class Creo_tool:#继承类IntialPara初始化的值
    def __init__(self, towerGeoExcel, lay_path, target_path):
        self.towerGeoExcel = towerGeoExcel
        self.target_path = target_path
        self.Description = self.get_sheets(towerGeoExcel, 'Description')  # 塔架说明
        self.TowerGeo = self.get_sheets(towerGeoExcel, 'TowerGeo')#塔筒主体信息
        self.FlangeGeo = self.get_sheets(towerGeoExcel, 'Flange')#塔筒法兰信息
        self.DoorGeo = self.get_sheets(towerGeoExcel,'Door')#门洞的信息
        self.flange_qty = self.flSecHqty()['flange_qty']
        self.section_qty = self.flSecHqty()['section_qty']
        self.vFlqty = self.vFlqty()
        self.stan_layGeo = None
        if lay_path != None:
            self.lay_path = lay_path
            self.stan_layGeo = read(lay_path,1)  # 布局表中间段布局信息


    def vFlqty(self):  # 判断是否为分片塔及分片段的数量
        workbook = openpyxl.load_workbook(self.towerGeoExcel)
        # 检查工作表名称是否在工作簿的工作表名称列表中
        if 'V-Flange' in workbook.sheetnames:
            V_FlangeSheet = workbook['V-Flange']
            # 获得分片塔段数
            number_v_flange = 0
            for i in range(3, V_FlangeSheet.max_row + 1):
                if isinstance(V_FlangeSheet.cell(i, 1).value, (str, int, float)):
                    number_v_flange += 1
            return number_v_flange
        else:
            return 0


    def get_sheets(self,path, sheet_name):  # path代表文件的路径地址，sheet_name代表excel表的名称
        try:
            book = xlrd.open_workbook(path)
            sheets = book.sheet_by_name(sheet_name)  # 使用工作表名称获取表单
            return sheets
        except xlrd.XLRDError as e:
            print(f"Error: {e}")
            return None  # 或者返回一个默认值，例如空列表或自定义错误信息



    def flSecHqty(self):  # 判断法兰的个数，存储法兰的标高值,以及判断塔筒的段数,0:法兰数量2：法兰标高，3：塔架段数
        flange_qty = 0
        flange_height = []  # 存储法兰的标高值
        for i in range(0, self.FlangeGeo.nrows):  # 判断有几个法兰，从而判断有多少个筒节
            if isinstance(self.FlangeGeo.cell_value(i, 0), float):
                flange_qty = flange_qty + 1
                flange_height.append(round(self.FlangeGeo.cell_value(i, 0),4))
        section_qty = flange_qty - 1
        return {'flange_qty': flange_qty, 'flange_height': flange_height, 'section_qty': section_qty}

    def errotest(self):
        pos = []
        towerm = []
        for i in range(1, self.TowerGeo.nrows):
            if round(self.TowerGeo.cell_value(i, 1),4) in self.flSecHqty()[1]:
                pos.append(i)
        pos.append(self.TowerGeo.nrows-1)
        return pos

    def flangePos(self):  # 判断法兰在塔筒信息中的行数，上法兰
        pos = []
        for i in range(1, self.TowerGeo.nrows):
            if round(self.TowerGeo.cell_value(i, 1), 4) in self.flSecHqty()['flange_height']:
                pos.append(i)
        pos.append(self.TowerGeo.nrows-1)
        return pos

    def flangePosAll(self):#判断法兰在塔筒信息中的行数，所有法兰
        pos = []
        for i in range(1, self.TowerGeo.nrows):
            if round(self.TowerGeo.cell_value(i, 1), 4) in self.flSecHqty()['flange_height'] or round(self.TowerGeo.cell_value(i, 3), 4) in self.flSecHqty()['flange_height']:
                pos.append(i)
        return pos

    #得到每段的塔架中径值
    def get_mid_dim(self):
        flpos_list = self.flangePosAll()
        mid_dim_list= []
        #增加每段的上法兰行数进去
        for i in flpos_list:
            mid_dim_list.append(round(self.TowerGeo.cell_value(i, 2) - self.TowerGeo.cell_value(i, 5), 2))
        return mid_dim_list


    def mgeoRead(self,n):  # n代表第n+1段，读入塔筒的直径，壁厚，筒节高度信息
        t = []  # 壁厚
        h = []  # 筒节高
        Db = [] # 筒节下外径
        Dt = []  # 筒节上外径
        pos = self.flangePos()
        if n == self.flSecHqty()['section_qty'] - 1:  # 顶段,包括上下法兰的高
            for j in range(pos[n],pos[n+1]-1):
                t.append(round(self.TowerGeo.cell_value(j, 5),3))
                h.append(round((self.TowerGeo.cell_value(j, 3) - self.TowerGeo.cell_value(j, 1)) * 1000, 1))
                Db.append(round(self.TowerGeo.cell_value(j, 2),3))
                Dt.append(round(self.TowerGeo.cell_value(j, 4),3))  # 外径
            t.append(round(self.TowerGeo.cell_value(pos[n+1]-1, 5),3))
            h.append(round((self.TowerGeo.cell_value(pos[n + 1], 3) - self.TowerGeo.cell_value(pos[n + 1] - 1, 1)) * 1000,1))  # 加上顶法兰的高
            Db.append(round(self.TowerGeo.cell_value(pos[n + 1] - 1, 2), 3))
            Dt.append(round(self.TowerGeo.cell_value(pos[n + 1], 4), 3))
            return h, t,Db, Dt
        else:  # 下段和中间段，包括上下法兰
            for j in range(pos[n], pos[n+1]):
                t.append(self.TowerGeo.cell_value(j, 5))
                h.append(round((self.TowerGeo.cell_value(j, 3) - self.TowerGeo.cell_value(j, 1)) * 1000, 1))
                Db.append(round(self.TowerGeo.cell_value(j, 2),3))
                Dt.append(round(self.TowerGeo.cell_value(j, 4),3))  # 外径
            return h, t, Db,Dt

    def flgeoRead(self):#读取法兰的信息
        m = []#标高
        da = []#外径
        di = []#内径
        dm = []#分度圆直径
        tfl = []#法兰厚度
        s = []#颈厚
        h = []#颈高
        dhole = []#螺栓孔直径
        n = []#螺栓数
        r = []#圆角
        Da_outer = []#T型法兰外径
        Dm_outer = []#T型法兰外圈分度圆直径
        for i in range(self.flSecHqty()[0]):
            m.append(self.FlangeGeo.cell_value(i+2, 0))
            da.append(self.FlangeGeo.cell_value(i + 2, 1))
            di.append(self.FlangeGeo.cell_value(i + 2, 2))
            dm.append(self.FlangeGeo.cell_value(i + 2, 3))
            tfl.append(self.FlangeGeo.cell_value(i + 2, 4))
            s.append(self.FlangeGeo.cell_value(i + 2, 5))
            h.append(self.FlangeGeo.cell_value(i + 2, 6))
            dhole.append(self.FlangeGeo.cell_value(i + 2, 8))
            n.append(self.FlangeGeo.cell_value(i + 2, 9))
            r.append(self.FlangeGeo.cell_value(i + 2, 10))
            Da_outer.append(self.FlangeGeo.cell_value(i + 2, 13))
            Dm_outer.append(self.FlangeGeo.cell_value(i + 2, 14))
        return m,da,di,dm,tfl,s,h,dhole,n,r,Da_outer,Dm_outer


    def shellArea(self,n):#注意计算的是中面
        widthlist = []
        lengthlist = []
        for h,D,d,t in zip(self.mgeoRead(n)[0],self.mgeoRead(n)[2],self.mgeoRead(n)[3],self.mgeoRead(n)[1]):
            D = D-t
            d = d-t
            if D == d:
                width = h
                length = math.pi * D
                widthlist.append(width)
                lengthlist.append(length)
            else:
                y = math.sqrt(((D-d)/2)**2 + h**2)#中间变量
                angle = (D - d) / y * math.pi  # 弧度值,扇形的上端中心角
                r = y * d/(D-d)#展开后小扇形长
                R = y * D/(D-d)#展开后大扇形长
                x = r * math.sqrt((1 + math.cos(angle))/2)#小直角三角形高
                width = R - x #钢板宽度
                length = 2 * R * math.sqrt(r ** 2 - x ** 2) / r  # 钢板长度
                widthlist.append(width)
                lengthlist.append(length)
        return widthlist,lengthlist

    def secNLength(self):  # 计算每段的长度,列表，包含从下段到顶段,包括法兰
        section_l = []
        for i in range(0, self.flSecHqty()['section_qty']):  # 计算每段的长度
            sum = 0
            for j in self.mgeoRead(i)[0]:
                sum = sum + j
            section_l.append(round(sum, 1))
        return section_l

    def towerLength(self):
        return sum(self.secNLength())

    def shellLength(self):  # 计算每段的长度,列表，包含从下段到顶段,不包含上下法兰
        section_l = []
        for i in range(0, self.flSecHqty()[2]):  # 计算每段的长度
            sum = 0
            for j in self.mgeoRead(i)[0][1:-1]:
                sum = sum + j
            section_l.append(round(sum,1))
        return section_l

    def secNHeight(self,n):# 筒节高度累计相加，用来判断不同的位置所在外径,n代表第n+1段,就是不断的往上加,包括上下法兰
        h_x = self.mgeoRead(n)[0]#每一段筒节高，包括法兰
        h_s = []  # 记录标高值
        sum = 0  # 中间数
        for i in range(0, len(h_x)):
            sum = h_x[i] + sum
            h_s.append(sum)
        return h_s

    def towerHeight(self):#筒段长度累计相加
        h_x = self.secNLength()  # 每一段的高度
        h_s = []  # 记录标高值
        sum = 0  # 中间数
        for i in range(0, len(h_x)):
            sum = h_x[i] + sum
            h_s.append(sum)
        return h_s

    def diCal(self, h, n):  # 判断内径，h代表所要判断的高度,距离下法兰底面，n代表第n+1段
        h_1 = self.secNHeight(n)
        h_m = h - self.mgeoRead(n)[0][0]  # h减去筒段底法兰的高,第一个0代表筒节高度，第二个0代表第0个，也就是底法兰高
        D = self.mgeoRead(n)[2][1]# 筒段最下端外径,不包括法兰
        d = self.mgeoRead(n)[3][-2]# 筒段最上端外径，不包括法兰
        L = self.secNLength()[n] - self.mgeoRead(n)[0][0] - self.mgeoRead(n)[0][-1]  # 锥段总长,减去上下法兰的高度
        Di = d + (D - d) * (L - h_m) / L #h处的外径
        for i in range(0, len(h_1)):
            if h <= h_1[i]:
                di = Di - 2 * self.mgeoRead(n)[1][i]
                return round(di, 1)

    # 得到中对齐塔架的内径，h代表所要判断的高度距离法兰底段,距离下法兰底面，n代表第n+1段
    def get_di(self, n, h):
        #n=0底段
        h = self.secNLength()[n] - h - self.mgeoRead(n)[0][0]  # 平台距离下筒体的距离
        D_top = self.mgeoRead(n)[3][-2] - self.mgeoRead(n)[1][-2]  # 钢筒体最上端中径，不包括法兰
        D_bottom = self.mgeoRead(n)[2][1] - self.mgeoRead(n)[1][1]  # 钢筒体最下端中径，不包括法兰
        H = self.secNLength()[n] - self.mgeoRead(n)[0][0] - self.mgeoRead(n)[0][-1]
        d_mid = self.calculate_diameter_at_height(D_top, D_bottom, H, h)
        return self.calculate_diameter_at_height(D_top, D_bottom, H, h)

    #得到锥段某一处的直径
    def calculate_diameter_at_height(self, D_top, D_bottom, H, h):
        D_h = D_bottom + ((D_top - D_bottom) * (h / H))
        return D_h


    #


    def thicknessCal(self,h, n):  # 判断所在处的壁厚
        h_1 = self.secNHeight(n)
        h_m = h - self.mgeoRead(n)[0][0]  # h减去筒段底法兰的高,第一个0代表筒节高度，第二个0代表第0个，也就是底法兰高
        D = self.mgeoRead(n)[2][1]# 筒段最下端外径,不包括法兰
        d = self.mgeoRead(n)[3][-2]# 筒段最上端外径，不包括法兰
        L = self.secNLength()[n] - self.mgeoRead(n)[0][0] - self.mgeoRead(n)[0][-1]  # 锥段总长,减去上下法兰的高度
        Di = d + (D - d) * (L - h_m) / L #h处的外径
        for i in range(0, len(h_1)):
            if h <= h_1[i]:
                di = Di - 2 * self.mgeoRead(n)[1][i]
                return round(self.mgeoRead(n)[1][i]+0.001,1)

    def flangeIn(self,n):
        flange_geo = []
        if n == 0:#底法兰
            for i in range(15):
                flange_geo.append(self.FlangeGeo.cell_value(n+2,i))
        elif n == self.flSecHqty()[2]:  # 顶法兰
            for i in range(13):
                flange_geo.append(self.FlangeGeo.cell_value(n+2,i))
        else:#其他法兰
            for i in range(13):
                flange_geo.append(self.FlangeGeo.cell_value(n+2,i))
        return flange_geo

    def flangeIn2(self):
        flange_geo = []
        for i in range(15):
            flange_geo.append(self.FlangeGeo.cell_value(0,i))
        return flange_geo


    def torlFlange(self):  # 判断底法兰是不是L型法兰，也就是是不是基础环的基础
        if isinstance(self.FlangeGeo.cell_value(2, 13), float):#如果为真就是T型法兰
            return True

    def doorRein(self):#判断门框是不是加强板门框
        if isinstance(self.DoorGeo.cell_value(2, 12), float):
            return True

    #门洞的类型
    def doortype(self):#判断门框是不是加强板门框
        if isinstance(self.DoorGeo.cell_value(2, 12), float):
            return 1
        else:
            return 0

    def topFlangeJudge(self):#判断顶法兰
        if self.FlangeGeo.cell_value(self.flSecHqty()[0]+1,4) == 340:#3MWS法兰,判断法兰厚度
            return 4506
        elif self.FlangeGeo.cell_value(self.flSecHqty()[0]+1,4) == 290:#6MW法兰
            return 7831
        elif self.FlangeGeo.cell_value(self.flSecHqty()[0]+1,9) == 112:
            if self.FlangeGeo.cell_value(self.flSecHqty()[0]+1,6) == 115:#2.X判断法兰脖子高
                return 2409
            else:#2.5/3MW
                return 2343
        else:
            if self.FlangeGeo.cell_value(self.flSecHqty()[0]+1,6) == 115:#2.0新，判断法兰脖子高
                return 2358
            else:#2MW旧
                return 2364

    def seriesJudge(self):#判断顶法兰
        if self.FlangeGeo.cell_value(self.flSecHqty()[0]+1,4) == 340:#3MWS法兰,判断法兰厚度
            return "3MWS"
        elif self.FlangeGeo.cell_value(self.flSecHqty()[0]+1,4) == 290:#6MW法兰
            return "6MW"
        elif self.FlangeGeo.cell_value(self.flSecHqty()[0]+1,9) == 112:
            if self.FlangeGeo.cell_value(self.flSecHqty()[0]+1,6) == 115:#2.X判断法兰脖子高
                return "2.X"
            else:#2.5/3MW
                return "2.5"
        else:
            if self.FlangeGeo.cell_value(self.flSecHqty()[0]+1,6) == 115:#2.0新，判断法兰脖子高
                return "2MWN"
            else:#2MW旧
                return "2MWO"

    def powerJudge(self):#判断塔架数据表里给的功率来判断机型
        va = self.Description.cell_value(1, 2)#功率那里的值
        va = va.strip()#去掉前后的空格
        if va == "2.5":
            return "2.5"
        elif va == "2.2" or va == "2.3":
            return "2.X"
        elif va == "2.8":
            return "2.8"
        else:
            return "Error"

    def heightJudge(self):
        height = 0
        for i in range(self.flSecHqty()['section_qty']):
            height = self.secNHeight(i)[-1] + height
        hh = height/1000
        return hh

    def ladderLay(self, n):  # 梯子布局用
        if n == self.flSecHqty()[2] - 1:  # 顶段
            ladder_length = self.top_layGeo.cell_value(23, 1)  # 爬梯长
            ladder_location = self.top_layGeo.cell_value(6, 1)  # 爬梯定位位置，凸出为正，凹为负
        elif n == 0:#下段
            ladder_length = self.down_layGeo.cell_value(4, 1)# 爬梯长
            ladder_location = self.down_layGeo.cell_value(3, 1) # 爬梯定位位置，凸出为正，凹为负
        else:  # 其他段
            ladder_length = self.stan_layGeo.cell_value(6, n)# 爬梯长
            ladder_location = self.stan_layGeo.cell_value(3, n) # 爬梯定位位置，凸出为正，凹为负
        h2 = self.shellLength()[n]  # 锥段高
        D = self.TowerGeo.cell_value(self.flangePos()[n] + 1, 2)  # 锥段下直径
        d = self.TowerGeo.cell_value(self.flangePos()[n+1] - 2, 4)  # 锥段上直径
        l2 = math.sqrt(((D - d) / 2) ** 2 + h2 ** 2)  # 锥段的母线长，不包含上下法兰
        sina = h2 / l2  # sin(a)，锥段角度
        l = self.secNLength()[n] / sina  # 塔筒段总母线长
        l_ladder_location = ladder_location / sina  # 爬梯距顶平台距离所占的母线长
        h_ladder2bottom = -round((l + l_ladder_location - ladder_length) * sina, 0)  # 爬梯端面距下法兰端面的距离，正为突出，负数为凹进去
        return h_ladder2bottom,l-l_ladder_location#算出的爬梯下端漏出，第n段母线长

    def lasupport2w(self,h,n):#支撑距焊缝的距离
        for i in self.secNHeight(n):
            h2w = h - i#距离焊缝的距离
            if h2w >= 125:
                h_result = h
                continue
            elif h2w <= -125:
                h_result = h
                continue
            else:
                h = h - 280
                h_result = h
                continue
        return h_result

    def laSupportLay(self, n):                                                        # 第n段塔筒
        ladderSupport = []
        ladderSupport1 = self.secNLength()[n] + self.stan_layGeo.cell_value(3, n) - self.stan_layGeo.cell_value(6, n) + 3*280+140
        ladderSupportN = ladderSupport1
        for i in range(18):
            ladderSupportX = self.lasupport2w(ladderSupportN,n)
            ladderSupportN = ladderSupportX + 7* 280
            ladderSupport.append(ladderSupportX)
        return ladderSupport

    def lampLay(self,h,n):                         #灯的自动布局
        for i in self.secNHeight(n):
            h_w = h - i                             #灯的坐标距焊缝的距离
            if h_w >= 365:
                h_result = h
                continue
            elif h_w <= -365:
                h_result = h
                continue
            elif h_w <= 135 and h_w >= -135:
                h_result = h
                continue
            elif (h_w - 250)> 0 :
                h = h + (365-h_w)+10#加10个余量
                h_result = h
                continue
            elif (h_w - 135) >= 0:
                h = h -(h_w - 135)-10
                h_result = h
                continue
            elif (h_w + 250) < 0:
                h = h + (-365-h_w)-10
                h_result = h
                continue
            elif (h_w + 135) <= 0:
                h = h -(h_w+135) +10
                h_result = h
                continue
        return h_result

    def num_to_char(self,num):
        """数字转中文"""
        num = str(num)
        new_str = ""
        num_dict = {"0": "零", "1": "一", "2": "二", "3": "三", "4": "四", "5": "五", "6": "六", "7": "七", "8": "八", "9": "九"}
        listnum = list(num)
        shu = []
        for i in listnum:
            shu.append(num_dict[i])
        new_str = "".join(shu)
        return new_str

    def num_to_rome(self,num):
        """数字转罗马字母"""
        num = str(num)
        new_str = ""
        num_dict = {"1": "Ⅰ", "2": "Ⅱ", "3": "Ⅲ", "4": "Ⅳ", "5": "Ⅴ", "6": "Ⅵ", "7": "Ⅶ", "8": "Ⅷ", "9": "Ⅸ"}
        listnum = list(num)
        shu = []
        for i in listnum:
            shu.append(num_dict[i])
        new_str = "".join(shu)
        return new_str

    #读取TAD表总的物料号
    def tydhRead(self,file,qty):
        tydh_sum_x = []
        for i in range(qty):
            tydh_sheet = read(file, i)
            tydh_sec = []
            for j in range(1, 7):
                tydh_sec.append(tydh_sheet.cell_value(j, 0))
            tydh_sum_x.append(tydh_sec)
        return (tydh_sum_x)

    # 图样代号按需归类
    def tydhClassify(self):
        tydh_sum = self.tydhRead(self.design_file,self.section_qty)
        tydh_sum_cls_x = []
        for sec_i in range(self.section_qty):
            tydh_weld = tydh_sum[sec_i][1]  # 焊合的图样代号
            tydh_cylinder = tydh_sum[sec_i][2]  # 筒体的图样代号
            tydh_fl_u = tydh_sum[sec_i][3]  # 连接上法兰的图样代号
            tydh_fl_d = tydh_sum[sec_i - 1][3]  # 连接下法兰的图样代号
            tydh_jqb = "Error"  # 加强板的图样代号
            if sec_i == 0:
                # 底段
                tydh_fl_d = tydh_sum[0][4]  # 连接下法兰的图样代号
                tydh_fl_u = tydh_sum[sec_i][5]  # 连接上法兰的图样代号
                tydh_jqb = tydh_sum[0][3]  # 加强板的图样代号
            elif sec_i == self.section_qty - 1:
                tydh_fl_u = "60.07.01783"  # 顶法兰
            elif sec_i == 1:  # 如果是第二段
                tydh_fl_d = tydh_sum[0][5]  # 连接下法兰的图样代号
            else:
                pass
            tydh_sec_cls = [tydh_weld, tydh_cylinder, tydh_fl_d, tydh_fl_u, tydh_jqb]  # 每段的图样代号,焊合，筒体，下法兰，上法兰
            tydh_sum_cls_x.append(tydh_sec_cls)
        return tydh_sum_cls_x


    def towername_e(self):
        return str(self.section_qty) + " Sections " + str(self.hh) + "m HH tower"
    #得到塔架总图、招标图名称
    def towername(self,section_qty,hh):
        qty = self.num_to_char(section_qty)
        return str(qty) + "段" + str(hh) + "米塔架"

    def getscale(self, hh):
        if int(hh) <= 100:
            return 100
        elif int(hh) <= 125:
            return 120
        else:
            return 140

    #得到附件总成重量
    def getAccWeight(self,hh,seri,qty):
        if int(hh) == 140 and seri == "2.X" and qty == 6:
            return 11000
        else:
            return 1000000

    def get_weld_positions(self, n): #筒节焊缝位置
        #提取每个筒节的高度
        segment_h = self.mgeoRead(n)[0]

        # 计算累计筒节高度（焊缝位置）
        weld_positions = []
        cumulative_height = 0  # 累计高度初始值

        for h in segment_h:
            cumulative_height += int(h)
            weld_positions.append(cumulative_height)

        return weld_positions

    def support(self,n,SEC_H_TOTAL,H_PLATFORM): #扶持高度
        weld_heights = self.get_weld_positions(n)
        safety_distance = 200
        H_SUPPORT= SEC_H_TOTAL / 2 - H_PLATFORM
        if any((num - safety_distance) <= H_SUPPORT <= (num + safety_distance) for num in weld_heights):
                H_SUPPORT += safety_distance
        H_SUPPORT_rev = int(H_SUPPORT)
        return H_SUPPORT_rev

    def ls_heights(self, n, H_platform, alpha): #爬梯支撑和电缆线夹高度
        weld_heights = self.get_weld_positions(n) # 获取焊缝位置

        h_values = [] # 初始化爬梯支撑高度列表
        h_prev = 980 * math.cos(alpha)
        h_values.append(round(h_prev, 4))


        threshold = weld_heights[-1] - (H_platform + 840)# 平台下840mm停止
        safety_distance = 200

        increment_options = [1960, 1680, 1400]
        while True:
                h_next = h_prev + increment_options[0]# 尝试基础增量1960
                if h_next > threshold:# 限制与顶平台距离
                    break

                weld_heights = self.get_weld_positions(n) # 检查焊缝冲突并调整
                if any((num - safety_distance) <= h_next <= (num + safety_distance) for num in weld_heights):
                    h_next = h_prev + increment_options[1] # 第一次调整

                    if any((num - safety_distance) <= h_next <= (num + safety_distance) for num in weld_heights):
                        h_next = h_prev + increment_options[2] # 第二次调整

                increment = h_next - h_prev
                increment_rev = round(increment)
                h_values.append(increment_rev)
                h_prev = h_next

        if h_values:
            last_height = sum(h_values)  # 计算最后一个高度值（累加所有增量）
            distance_to_top = weld_heights[-1] - H_platform - last_height # 计算与顶面的距离（weld_heights[-1] - H_platform）

            if distance_to_top > 2240:# 如果距离超过2240且不超过阈值，则尝试添加一组1400
                potential_new_height = last_height + 1400
                if potential_new_height <= weld_heights[-1] - H_platform:
                    # 检查新高度是否与焊缝干涉
                    if not any((num - safety_distance) <= potential_new_height <= (num + safety_distance) for num in
                               weld_heights):
                        h_values.append(1400)  # 添加1400增量

        T_or_F = self.stan_layGeo.cell_value(6, n)

        #电缆线夹高度(国内)
        cs_values = []
        if T_or_F == 1:
            cs_values.append(h_values[0])
            # 从索引1开始，每两个元素相加
            i = 1
            while i < len(h_values) - 1:
                cs = h_values[i] + h_values[i + 1]
                cs_values.append(cs)
                i += 2  # 每次增加2，跳过已处理的元素

        # 电缆线夹高度(国外)
        else:
            for i in range(0,len(h_values)):
                cs = h_values[i]
                cs_values.append(cs)

        #调整结果格式
        ls_rev = '\n'.join([
            f'H{i + 1}_L= {val} /*第{i + 1}组爬梯支撑安装高度(距离上一组安装高度)'
            for i, val in enumerate(h_values)
        ])

        ls_exist = {f'H{i + 1}_L_Exist': 'yes /*存在' for i in range(len(h_values))}  #爬梯支撑存在的赋值yes
        for i in range(1, 20):  #爬梯支撑不存在的赋值no
            key = f'H{i + 1}_L_Exist'
            if key not in ls_exist:
                ls_exist[key] = 'no /*不存在'
        ls_exist_rev = "\n".join([f"{key} = {value}" for key, value in ls_exist.items()])

        cs_rev = '\n'.join([
            f'H{i + 1}_CABLE= {val} /*第{i + 1}组电缆线夹安装高度(距离上一组安装高度)'
            for i, val in enumerate(cs_values)
        ])

        cs_exist = {f'H{i + 1}_CABLE_Exist': 'yes /*存在'   for i in range(len(cs_values))} #电缆线夹存在的赋值yes
        for i in range(1, 20):  #电缆线夹不存在的赋值no
            key = f'H{i + 1}_CABLE_Exist'
            if key not in cs_exist:
                cs_exist[key] = 'no /*不存在'
        cs_exist_rev = "\n".join([f"{key} = {value}" for key, value in cs_exist.items()])

        return ls_rev,cs_rev,ls_exist_rev,cs_exist_rev

class Excel_tool(Creo_tool):
    def shellEx(self,worksheet):#塔筒下料
        section_qty = self.flSecHqty()['section_qty']  # 塔段数量
        count = 0
        for i in range(0, section_qty):
            for j in list(range(1, len(self.mgeoRead(i)[0]) - 1)):
                worksheet.write(count + 2, 0, label='S' + str(i + 1) + '-' + str(j))  # 筒节标号
                worksheet.write(count + 2, 1, label=self.mgeoRead(i)[2][j])  # 筒节下段直径
                worksheet.write(count + 2, 2, label=self.mgeoRead(i)[3][j])  # 筒节上端直径
                worksheet.write(count + 2, 3, label=self.mgeoRead(i)[0][j])  # 筒节高度
                worksheet.write(count + 2, 4, label=self.mgeoRead(i)[1][j])  # 筒节壁厚
                worksheet.write(count + 2, 5, label=self.shellArea(i)[0][j])  # 钢板宽度
                worksheet.write(count + 2, 6, label=self.shellArea(i)[1][j])  # 钢板长度
                count = count + 1
    # def sheetNew(self):#建立取号的excel表
    #     section_qty = self.flSecHqty()[2]  # 塔段数量
    #     workbook = xlwt.Workbook(encoding='utf-8')
    #     for i in range(section_qty):
    #         if i == section_qty - 1:
    #             worksheet = workbook.add_sheet('顶段')
    #         else:
    #             worksheet = workbook.add_sheet('第' + str(i + 1) + '段')  # 创建一个worksheet
    #     workbook.save(self.target_path + '\\取号表.xls')


class Weight_tool(Creo_tool):
    # def towerGeoWeight(self , n):
    #     shellNweight = []#每段筒节的重量不包括上下法兰
    #     for i in range(1,len(self.mgeoRead(n)[1])-1):
    #         p = 7850
    #         R = self.mgeoRead(n)[2][i]/1000/2# 筒段最下端外径,单位m
    #         r = self.mgeoRead(n)[3][i]/1000/2# 筒段最上端外径，单位m
    #         h = self.mgeoRead(n)[0][i]/1000  # 每段塔筒节的高度，单位m
    #         t = self.mgeoRead(n)[1][i]/1000 #每段塔筒节的厚度，单位m
    #         volume = math.pi * h * t * (r + R - t)  # 塔筒节的体积，单位m
    #         TowerG_weight = volume * p  # 塔筒节的重量
    #         shellNweight.append(TowerG_weight)
    #     shellSumweight = round(sum(shellNweight),1)#各个筒节相加的总重量
    #     return shellSumweight


    def towerGeoWeight(self , n):
        shellNweight = []#每段筒节的重量不包括上下法兰
        p = 7850
        ll = len(self.mgeoRead(n)[1])-1
        Rx = self.mgeoRead(n)[2]
        rx = self.mgeoRead(n)[3]
        hx = self.mgeoRead(n)[0]
        tx = self.mgeoRead(n)[1]
        for i in range(1,ll):
            R = Rx[i] / 1000/2# 筒段最下端外径,单位m
            r = rx[i] /1000/2# 筒段最上端外径，单位m
            h = hx[i] /1000  # 每段塔筒节的高度，单位m
            t = tx[i] /1000 #每段塔筒节的厚度，单位m
            volume = math.pi * h * t * (r + R - t)  # 塔筒节的体积，单位m
            TowerG_weight = volume * p  # 塔筒节的重量
            shellNweight.append(TowerG_weight)
        shellSumweight = round(sum(shellNweight),1)#各个筒节相加的总重量
        return shellSumweight

    def towerGeoWeight2(self):#筒节的重量
        cylinderWeight = []
        for n in range(self.flSecHqty()[0] - 1):
            shellNweight = []#每段筒节的重量不包括上下法兰
            p = 7850
            ll = len(self.mgeoRead(n)[1])-1
            Rx = self.mgeoRead(n)[2]
            rx = self.mgeoRead(n)[3]
            hx = self.mgeoRead(n)[0]
            tx = self.mgeoRead(n)[1]
            for i in range(1,ll):
                R = Rx[i] / 1000/2# 筒段最下端外径,单位m
                r = rx[i] /1000/2# 筒段最上端外径，单位m
                h = hx[i] /1000  # 每段塔筒节的高度，单位m
                t = tx[i] /1000 #每段塔筒节的厚度，单位m
                volume = math.pi * h * t * (r + R - t)  # 塔筒节的体积，单位m
                TowerG_weight = volume * p  # 塔筒节的重量
                shellNweight.append(TowerG_weight)
            shellSumweight = round(sum(shellNweight),1)#各个筒节相加的总重量
            cylinderWeight.append(shellSumweight)
        return cylinderWeight



    def towertest(self , n):
        shellNweight = []#每段筒节的重量不包括上下法兰
        for i in range(1,len(self.mgeoRead(n)[1])-1):
            p = 7850
            R = self.mgeoRead(n)[2][i]/1000/2# 筒段最下端外径,单位m
            r = self.mgeoRead(n)[3][i]/1000/2# 筒段最上端外径，单位m
            h = self.mgeoRead(n)[0][i]/1000  # 每段塔筒节的高度，单位m
            t = self.mgeoRead(n)[1][i]/1000 #每段塔筒节的厚度，单位m
            volume = math.pi * h * t * (r + R - t)  # 塔筒节的体积，单位m
            TowerG_weight = volume * p  # 塔筒节的重量
            shellNweight.append(h)
        shellSumweight = round(sum(shellNweight),1)#各个筒节相加的总重量
        return shellNweight

    # def flangeWeight(self):#法兰的重量
    #     flWeight = []
    #     for i in range(self.flSecHqty()[0]-1):
    #         R = self.flgeoRead()[1][i] / 2 / 1000  # 法兰的外径，半径，对于L型法兰是外径，对于T型法兰不是
    #         r = self.flgeoRead()[2][i] / 2 / 1000  # 法兰的内径，半径
    #         tf1 = self.flgeoRead()[4][i] / 1000  # 法兰的厚度，可以当成一个圆柱的高度
    #         s = self.flgeoRead()[5][i] / 1000  # 颈厚
    #         h = self.flgeoRead()[6][i] / 1000  # 颈高
    #         r_b = self.flgeoRead()[7][i] / 2 / 1000  # 螺栓孔的半径
    #         n = self.flgeoRead()[8][i]  # 螺栓的个数
    #         r_f = self.flgeoRead()[9][i] / 1000  # 圆角
    #         if self.torlFlange() and i == 0:
    #             r_k = R
    #             r_m = r_k - s / 2  # T型法兰特有的一个尺寸，现在的计算方法不一定准确，middle尺寸
    #             R = (r_m - r) * 2 + r  # 这个是T型法兰的外径
    #             Rrin = r_m - s - r_f  # 一个中间变量,计算圆角的时候用
    #             Rrout = r_m + r_f
    #             V1 = math.pi * (r_m ** 2 - (r_m - s) ** 2) * (h - r_f)  # 法兰颈的体积，不包括圆角的高度的那部分
    #             V2 = math.pi * (R ** 2 - r ** 2) * tf1  # 不包括颈那部分的体积
    #             Vrin = math.pi * r_f * Rrin ** 2 + math.pi * r_f ** 3 + math.pi * Rrin * r_f ** 2 * math.pi / 2 - math.pi * r_f ** 3 / 3
    #             Vbrout = math.pi * r_f * Rrout ** 2 + math.pi * r_f ** 3 - math.pi * Rrout * r_f ** 2 * math.pi / 2 - math.pi * r_f ** 3 / 3
    #             Vbolt = n * math.pi * tf1 * r_b ** 2  # 螺栓的体积
    #             Flange_volume = V1 + V2 - Vbolt + Vbrout - Vrin  # 法兰的体积
    #             T_F_weight = round(Flange_volume * 7850,1)
    #             flWeight.append(T_F_weight)
    #         else:#L型法兰
    #             RR = R - s - r_f  # 一个中间变量,计算圆角的时候用
    #             VR = math.pi * r_f * RR ** 2 + math.pi * r_f ** 3 + math.pi * RR * r_f ** 2 * math.pi / 2 - math.pi * r_f ** 3 / 3
    #             V_F = math.pi * (R - s) ** 2 * r_f - VR  # 法兰的体积，L型法兰
    #             Cylinder_volume = math.pi * tf1 * (R ** 2 - r ** 2)  # 外圆柱体的体积
    #             Bolt_volume = n * math.pi * tf1 * r_b ** 2  # 螺栓的体积
    #             Neck_volume = math.pi * h * s * (2 * R - s)  # 法兰颈的体积
    #             Flange_volume = Cylinder_volume - Bolt_volume + Neck_volume + V_F  # 法兰的体积
    #             L_F_weight = round(Flange_volume * 7850,1)
    #             flWeight.append(L_F_weight)
    #     flWeight.append(self.topFlangeJudge())
    #     return flWeight

    def flangeWeight(self):#法兰的重量
        flWeight = []
        R_list = self.flgeoRead()[1]
        r_list = self.flgeoRead()[2]
        tfl_list = self.flgeoRead()[4]
        s_list = self.flgeoRead()[5]
        h_list = self.flgeoRead()[6]
        r_b_list = self.flgeoRead()[7]
        n_list = self.flgeoRead()[8]
        r_f_list = self.flgeoRead()[9]
        for i in range(self.flSecHqty()[0]-1):
            R = R_list[i] / 2 / 1000  # 法兰的外径，半径，对于L型法兰是外径，对于T型法兰不是
            r = r_list[i] / 2 / 1000  # 法兰的内径，半径
            tf1 = tfl_list[i] / 1000  # 法兰的厚度，可以当成一个圆柱的高度
            s = s_list[i] / 1000  # 颈厚
            h = h_list[i] / 1000  # 颈高
            r_b = r_b_list[i] / 2 / 1000  # 螺栓孔的半径
            n = n_list[i]  # 螺栓的个数
            r_f = r_f_list[i] / 1000  # 圆角
            if self.torlFlange() and i == 0:
                r_k = R
                r_m = R - s / 2  # T型法兰特有的一个尺寸，现在的计算方法不一定准确，middle尺寸
                R = (r_m - r) * 2 + r  # 这个是T型法兰的外径
                Rrin = r_m - s - r_f  # 一个中间变量,计算圆角的时候用
                Rrout = r_m + r_f
                V1 = math.pi * (r_m ** 2 - (r_m - s) ** 2) * (h - r_f)  # 法兰颈的体积，不包括圆角的高度的那部分
                #V1 = math.pi * (R ** 2 - (R - s) ** 2) * (h - r_f)   # 法兰颈的体积，不包括圆角的高度的那部分
                V2 = math.pi * (R ** 2 - r ** 2) * tf1  # 不包括颈那部分的体积，法兰厚大圆盘的体积
                Vrin = math.pi * r_f * Rrin ** 2 + math.pi * r_f ** 3 + math.pi * Rrin * r_f ** 2 * math.pi / 2 - math.pi * r_f ** 3 / 3
                Vbrout = math.pi * r_f * Rrout ** 2 + math.pi * r_f ** 3 - math.pi * Rrout * r_f ** 2 * math.pi / 2 - math.pi * r_f ** 3 / 3
                Vbolt = n * math.pi * tf1 * r_b ** 2  # 螺栓的体积
                Flange_volume = V1 + V2  + Vbrout - Vrin - Vbolt  # 法兰的体积
                T_F_weight = round(Flange_volume * 7850,1)
                flWeight.append(T_F_weight)
            else:#L型法兰
                RR = R - s - r_f  # 一个中间变量,计算圆角的时候用
                VR = math.pi * r_f * RR ** 2 + math.pi * r_f ** 3 + math.pi * RR * r_f ** 2 * math.pi / 2 - math.pi * r_f ** 3 / 3
                V_F = math.pi * (R - s) ** 2 * r_f - VR  # 法兰圆角的体积，L型法兰
                Cylinder_volume = math.pi * tf1 * (R ** 2 - r ** 2)  # 法兰圆盘体积
                Bolt_volume = n * math.pi * tf1 * r_b ** 2  # 螺栓的体积
                Neck_volume = math.pi * h * s * (2 * R - s)  # 法兰颈的体积,面积乘中间的周长
                Flange_volume = Cylinder_volume - Bolt_volume + Neck_volume + V_F  # 法兰的体积
                L_F_weight = round(Flange_volume * 7850,1)
                flWeight.append(L_F_weight)
        flWeight.append(self.topFlangeJudge())
        return flWeight

    def secNWeight(self):
        secNwe = []
        for i in range(self.flSecHqty()[0]-1):
            secW = self.flangeWeight()[i] + self.towerGeoWeight(i) + self.flangeWeight()[i+1]
            secNwe.append(secW)
        return secNwe

class SuggestCode(Creo_tool):#物料号推荐系统

    def choose1(self,diff,dinner,plat_code, plat_di,plat_name):#筛选出最小值:x1,x2不同平台对应不同的值
        plat_sum = []#平台的所有相关数据
        for i,j,k in zip(plat_code, plat_di,plat_name):
            k2 = i,j,k
            plat_sum.append(k2)
        filter1 = list(filter(lambda x:(x[1]-dinner) <= diff and (x[1]-dinner) >= -diff,plat_sum))#
        absValue = [abs(x[1]-dinner) for x in filter1]#内径与第一次筛选的值的相减绝对值
        if absValue:#如果有符合要求的数据
            positionMin = absValue.index(min(absValue))  # 最小的绝对值在相减绝对值中的位置
            matchPlant = filter1[positionMin]  # 最匹配的平台结果
            return matchPlant
        else:#如果没有符合要求的数据
            return ['','','','']

    def choose2(self,diff,y1,y2,dinner,plat_code,plat_di,plat_y1,plat_y2):
        plat_sum = []#平台的所有相关数据
        for i,j,k,l in zip(plat_code, plat_di,plat_y1,plat_y2):
            k2 = i,j,k,l
            plat_sum.append(k2)
        filter1 = list(filter(lambda x:x[2] == y1  and x[3] == y2 and (x[1]-dinner) <= diff and (x[1]-dinner) >= -diff,plat_sum))#得到L_CENTER2LEFT，a相匹配，内径小于6大于-6的平台,第一次筛选
        absValue = [abs(x[1]-dinner) for x in filter1]#内径与第一次筛选的值的相减绝对值
        if absValue:#如果有符合要求的数据
            positionMin = absValue.index(min(absValue))  # 最小的绝对值在相减绝对值中的位置
            matchPlant = filter1[positionMin]  # 最匹配的平台结果
            return matchPlant
        else:#如果没有符合要求的数据
            return ['','','','']

    def ladderSuggest(self,n):#爬梯物料号推荐
        generatrix = self.ladderLay(n)[1]#第n段的母线长
        ladderLength = list(filter(None,self.ladderAndCable.col_values(5,start_rowx=2)))#去掉列表中的空字符，爬梯库中的爬梯长度值
        absValue = [abs(x-generatrix) for x in ladderLength]#爬梯与第n段母线长相减的绝对值
        positionMin = absValue.index(min(absValue))#最小值在绝对值中的位置
        positionRow = positionMin + 2
        return self.ladderAndCable.cell_value(positionRow,4),self.ladderAndCable.cell_value(positionRow,6)

    def cableSuggest(self,n):#判断线槽,不要绝对值，线槽要比塔筒短，线槽物料号推荐系统
        generatrix = self.ladderLay(n)[1]  # 第n段的母线长
        cableLength = list(filter(None,self.ladderAndCable.col_values(2,start_rowx=2)))#去掉列表中的空字符,线槽库中的线槽长度值
        minusValue = [x-generatrix for x in cableLength]#线槽长度与第n段母线长相减，不是绝对值，因为线槽要小于母线长
        positionMin = minusValue.index(min(minusValue))#最小值的位置
        positionRow = positionMin + 2#在excel表格中的位置
        return self.ladderAndCable.cell_value(positionRow,1),self.ladderAndCable.cell_value(positionRow,3)

    def platSugget(self,n):#N段平台物料号推荐系统
        if n == 0:
            lx = self.down_layGeo.cell_value(28,1)#下段LX的值
            ly = self.down_layGeo.cell_value(29,1)#下段LY的值
            di = self.diCal(self.secNLength()[n]-self.down_layGeo.cell_value(6,1),n)#平台所在处的内径值
        else:
            lx = self.stan_layGeo.cell_value(9,n)#中间段的lx值
            ly = self.stan_layGeo.cell_value(10,n)#中间段平台的ly值
            di = self.diCal(self.secNLength()[n]-self.stan_layGeo.cell_value(2,n),n)#平台所在处的内径值
        plat_code = list(filter(None, self.platN.col_values(6 * n + 1, start_rowx=2)))  # 去掉列表中的空字符
        plat_di = list(filter(lambda x:isinstance(x,float), self.platN.col_values(6 * n + 2,start_rowx=2)))  # 去掉列表中的空字符
        plat_lx = list(filter(lambda x:isinstance(x,float), self.platN.col_values(6 * n + 3,start_rowx=2)))  # 去掉列表中的空字符
        plat_ly = list(filter(lambda x:isinstance(x,float), self.platN.col_values(6 * n + 4,start_rowx=2)))  # 去掉列表中的空字符
        #print(di)
        return self.choose2(6,lx,ly,di,plat_code,plat_di,plat_lx,plat_ly)

    def initialPlatSugget(self,n):#升降机起始平台物料号推荐系统
        lx = self.down_layGeo.cell_value(30,1)#下段LX的值
        ly = self.down_layGeo.cell_value(31,1)#下段LY的值
        di = self.diCal(self.down_layGeo.cell_value(8,1),n)#平台所在处的内径值
        if self.towerLength() >= 115000:#如果是柔塔,大于115米
            plat_code = list(filter(None, self.initialAndDeflection.col_values(7, start_rowx=2)))  # 去掉列表中的空字符
            plat_di = list(filter(lambda x: isinstance(x, float),
                                  self.initialAndDeflection.col_values(8, start_rowx=2)))  # 去掉列表中的空字符
            plat_lx = list(filter(lambda x: isinstance(x, float),
                                  self.initialAndDeflection.col_values(9, start_rowx=2)))  # 去掉列表中的空字符
            plat_ly = list(filter(lambda x: isinstance(x, float),
                                  self.initialAndDeflection.col_values(10, start_rowx=2)))  # 去掉列表中的空字符
        else:
            plat_code = list(filter(None, self.initialAndDeflection.col_values(1, start_rowx=2)))  # 去掉列表中的空字符
            plat_di = list(filter(lambda x:isinstance(x,float), self.initialAndDeflection.col_values(2,start_rowx=2)))  # 去掉列表中的空字符
            plat_lx = list(filter(lambda x:isinstance(x,float), self.initialAndDeflection.col_values(3,start_rowx=2)))  # 去掉列表中的空字符
            plat_ly = list(filter(lambda x:isinstance(x,float), self.initialAndDeflection.col_values(4,start_rowx=2)))  # 去掉列表中的空字符
        #print(di)
        #print(self.towerLength())
        return self.choose2(6, lx, ly, di, plat_code,plat_di,plat_lx,plat_ly)

    def deflectionPlatSugget(self,n):#马鞍平台物料号推荐系统
        L_CENTER2LEFT = self.top_layGeo.cell_value(24,1)#升降机偏移位置
        a = self.top_layGeo.cell_value(25,1)#旋转角度
        di = self.diCal(self.secNLength()[n]-self.top_layGeo.cell_value(3,1),n)#平台所在处的内径值
        plat_code = list(filter(None, self.initialAndDeflection.col_values(13, start_rowx=2)))  # 去掉列表中的空字符
        plat_di = list(filter(lambda x: isinstance(x, float),
                              self.initialAndDeflection.col_values(14, start_rowx=2)))  # 去掉列表中的空字符
        plat_L_CENTER2LEFT = list(filter(lambda x: isinstance(x, float),
                              self.initialAndDeflection.col_values(15, start_rowx=2)))  # 去掉列表中的空字符
        plat_a= list(filter(lambda x: isinstance(x, float),
                              self.initialAndDeflection.col_values(16, start_rowx=2)))  # 去掉列表中的空字符
        #print(di)
        return self.choose2(6, L_CENTER2LEFT, a, di, plat_code,plat_di,plat_L_CENTER2LEFT,plat_a)

    def topPlatSugget(self,n):#顶段顶平台物料号推荐系统
        di = self.diCal(self.secNLength()[n]-self.top_layGeo.cell_value(2,1),n)#平台所在处的内径值
        plat_code = list(filter(None, self.initialAndDeflection.col_values(19, start_rowx=2)))  # 去掉列表中的空字符
        plat_di = list(filter(lambda x: isinstance(x, float),
                              self.initialAndDeflection.col_values(20, start_rowx=2)))  # 去掉列表中的空字符
        plat_name = list(filter(None, self.initialAndDeflection.col_values(21, start_rowx=2)))  # 去掉列表中的空字符
        #print(di)
        return self.choose1(6, di, plat_code, plat_di,plat_name)

    def rollerSugget(self,n):#马鞍托架
        height = self.secNLength()[n] - self.top_layGeo.cell_value(3, 1) + self.top_layGeo.cell_value(4, 1)#马鞍托架高度
        di = self.diCal(height,n)#平台所在处的内径值
        plat_code = list(filter(None, self.rollerAndBeam.col_values(1, start_rowx=2)))  # 去掉列表中的空字符
        plat_di = list(filter(lambda x: isinstance(x, float),
                              self.rollerAndBeam.col_values(2, start_rowx=2)))  # 去掉列表中的空字符
        plat_name= list(filter(None,self.rollerAndBeam.col_values(3, start_rowx=2)))  # 去掉列表中的空字符
        #print(di)
        return self.choose1(6, di, plat_code, plat_di,plat_name)

    def cableProSugget1(self,n):#电缆护套梁一物料号推荐系统
        height = self.secNLength()[n] - self.top_layGeo.cell_value(3,1)+self.top_layGeo.cell_value(16,1)#电缆护套梁一
        di = self.diCal(height,n)#平台所在处的内径值
        plat_code = list(filter(None, self.rollerAndBeam.col_values(6, start_rowx=2)))  # 去掉列表中的空字符
        plat_di = list(filter(lambda x: isinstance(x, float),
                              self.rollerAndBeam.col_values(7, start_rowx=2)))  # 去掉列表中的空字符
        plat_name= list(filter(None,self.rollerAndBeam.col_values(8, start_rowx=2)))  # 去掉列表中的空字符
        #print(di)
        return self.choose1(4, di, plat_code, plat_di,plat_name)

    def cableProSugget2(self,n):#马鞍平台物料号推荐系统
        height = self.secNLength()[n]-self.top_layGeo.cell_value(3,1)+self.top_layGeo.cell_value(16,1)+self.top_layGeo.cell_value(17,1)#高度
        di = self.diCal(height,n)#平台所在处的内径值
        plat_code = list(filter(None, self.rollerAndBeam.col_values(11, start_rowx=2)))  # 去掉列表中的空字符
        plat_di = list(filter(lambda x: isinstance(x, float),
                              self.rollerAndBeam.col_values(12, start_rowx=2)))  # 去掉列表中的空字符
        plat_name= list(filter(None,self.rollerAndBeam.col_values(13, start_rowx=2)))  # 去掉列表中的空字符
        #print(di)
        return self.choose1(4, di,plat_code, plat_di,plat_name)


    def cableBridgeSugget(self,n):#动力电缆桥架物料号推荐系统
        di = self.diCal(self.down_layGeo.cell_value(10, 1),n)#平台所在处的内径值
        plat_code = list(filter(None, self.rollerAndBeam.col_values(16, start_rowx=2)))  # 去掉列表中的空字符
        plat_di = list(filter(lambda x: isinstance(x, float),
                              self.rollerAndBeam.col_values(17, start_rowx=2)))  # 去掉列表中的空字符
        plat_name= list(filter(None,self.rollerAndBeam.col_values(18, start_rowx=2)))  # 去掉列表中的空字符
        #print(di)
        return self.choose1(6,di,plat_code, plat_di,plat_name)

    #附件总成物料号推荐
    def choose3(self,diff,y1,y2,dinner,plat_code,plat_di,plat_y1,plat_y2,weight):#范围，项目lx，ly，内径，物料号，
        plat_sum = []#平台的所有相关数据
        for i,j,k,l,m in zip(plat_code, plat_di, plat_y1, plat_y2, weight):
            k2 = i,j,k,l,m
            plat_sum.append(k2)
        filter1 = list(filter(lambda x:x[2] == y1  and x[3] == y2 and (x[1]-dinner) <= diff and (x[1]-dinner) >= -diff,plat_sum))#得到L_CENTER2LEFT，a相匹配，内径小于6大于-6的平台,第一次筛选
        if filter1:
            return filter1[0]
        else:
            return ['', '', '', '']

    def choose4(self, *args):  # 范围，项目lx，ly，内径，物料号，
        diff = args[0]
        y1 = args[1]
        y2 = args[2]
        dinner = args[3]
        plat_code = args[4]
        plat_di = args[5]
        plat_y1 = args[6]
        plat_y2 = args[7]
        weight = args[8]

        plat_sum = []  # 平台的所有相关数据
        for i, j, k, l, m in zip(plat_code, plat_di, plat_y1, plat_y2, weight):
            k2 = i, j, k, l, m
            plat_sum.append(k2)
        filter1 = list(
            filter(lambda x: x[2] == y1 and x[3] == y2 and (x[1] - dinner) <= diff and (x[1] - dinner) >= -diff,
                   plat_sum))  # 得到L_CENTER2LEFT，a相匹配，内径小于6大于-6的平台,第一次筛选
        if filter1:
            return filter1[0]
        else:
            return ['', '', '', '']
    #中间段有阻尼的筛选函数
    def choose5(self, *args):  #
        diff = args[0]
        y1 = args[1]
        y2 = args[2]
        dinner = args[3]
        plat_code = args[4]
        plat_di = args[5]
        plat_y1 = args[6]
        plat_y2 = args[7]
        weight = args[8]
        lay_tmd_qty = args[9]
        acc_tmd_qty =args[10]

        plat_sum = []  # 平台的所有相关数据
        for i, j, k, l, m, n in zip(plat_code, plat_di, plat_y1, plat_y2, weight, acc_tmd_qty):
            k2 = i, j, k, l, m, n
            plat_sum.append(k2)
        filter1 = list(
            filter(lambda x: x[2] == y1 and x[3] == y2 and (x[1] - dinner) <= diff and (x[1] - dinner) >= -diff and x[5] == lay_tmd_qty,
                   plat_sum))  # 得到L_CENTER2LEFT，a相匹配，内径小于6大于-6的平台,第一次筛选
        if filter1:
            return filter1[0]
        else:
            return ['', '', '', '', '']

    #附件总成推荐
    def accSugget(self,n):
        #布局表中的数据
        section_qty = self.flSecHqty()[2]  # 段数
        hh = self.heightJudge()  # 机型标准高度
        seri = self.seriesJudge()  # 机型
        #powerofdes = self.Description.cell_value(1, 2)#塔架说明里给的功率
        powerofdes = self.powerJudge()

        acc_seri = seri + "-" + powerofdes #塔架附件类型
        acc_data_path = "D:/Program Files/OneKeyTower/acc_database"  #附件总成信息根目录
        path = acc_data_path + "/" + acc_seri + "/" + str(section_qty) + "/" + str(
            hh) + ".xlsx"  # 项目的附件总成数据库文件
        #path = acc_data_path + "/" + str(seri) + "/" + str(section_qty) + "/" + str(hh) + ".xlsx"  # 项目的附件总成数据库

        if powerofdes == "2.X":
            diff = 6
        else:
            diff = 15

        sheet = read(path,n)
        if n == 0:#第一段
            lay_top_lx = self.down_layGeo.cell_value(28,1)#下段顶平台LX的值
            lay_top_ly = self.down_layGeo.cell_value(29,1)#下段顶平台LY的值
            lay_top_di = self.diCal(self.secNLength()[n]-self.down_layGeo.cell_value(6,1),n)#平台所在处的内径值
            ###
            lay_lift_lx = self.down_layGeo.cell_value(30,1)#下段升降平台LX的值
            lay_lift_ly = self.down_layGeo.cell_value(31,1)#下段升降平台LY的值
            lay_lift_di = self.diCal(self.down_layGeo.cell_value(8,1),n)#升降平台所在处的内径值
        elif n == section_qty -1:#顶段
            lay_top_plat_di = self.diCal(self.secNLength()[n] - self.top_layGeo.cell_value(2, 1), n)  # 平台所在处的内径值
            lay_top_plat_lx = 0
            lay_top_plat_ly = 0
            ##
            lay_terminal_plat_lx= self.top_layGeo.cell_value(24, 1)  # 升降机偏移位置
            lay_terminal_plat_l2 = self.top_layGeo.cell_value(25, 1)  # 旋转角度
            lay_terminal_plat_di = self.diCal(self.secNLength()[n]-self.top_layGeo.cell_value(3,1),n)#平台所在处的内径值
            ##
            lay_liftbeam_di = self.diCal(self.secNLength()[n] - self.top_layGeo.cell_value(3, 1) + self.top_layGeo.cell_value(5, 1), n)  # 升降机吊梁所在处的内径值
            lay_liftbeam_lx = 0
            lay_liftbeam_ly = 0
            ##
            height1 = self.secNLength()[n] - self.top_layGeo.cell_value(3, 1) + self.top_layGeo.cell_value(16,
                                                                                                          1)  # 电缆护套梁一
            lay_cable1_di = self.diCal(height1, n)  # 平台所在处的内径值
            lay_cable1_lx = 0
            lay_cable1_ly = 0
            ##
            height2 = self.secNLength()[n] - self.top_layGeo.cell_value(3, 1) + self.top_layGeo.cell_value(16,
                                                                                                          1) + self.top_layGeo.cell_value(17, 1)  #电缆护套梁二高度
            lay_cable2_di = self.diCal(height2, n)  # 平台所在处的内径值
            lay_cable2_lx = 0
            lay_cable2_ly = 0
        else:#中间段，布局表中的数量
            lay_top_lx = self.stan_layGeo.cell_value(9,n)#中间段顶平台的lx值
            lay_top_ly = self.stan_layGeo.cell_value(10,n)#中间段顶平台平台的ly值
            lay_top_di = self.diCal(self.secNLength()[n]-self.stan_layGeo.cell_value(2,n),n)#平台所在处的内径值
            try:
                lay_tmd_qty = self.stan_layGeo.cell_value(11,n)#中间段阻尼器的数量
            except IndexError:
                lay_tmd_qty = 0


        lift_plat_code,lift_plat_di,lift_plat_lx,lift_plat_ly = [],[],[],[]
        top_plat_code, top_plat_di, top_plat_lx, top_plat_ly = [], [], [], []
        acc_weight = []
        #存储附件总成数据库中的附加总成信息
        if n == 0:#第一段
            for i in range(3):
                try:#如果数据库中此处有值，即为数据库中的值
                    acc_lift_plat_code_s = sheet.cell_value(i * 9 + 2, 1)#升降平台
                    acc_lift_di_code_s = sheet.cell_value(i * 9 + 2, 6)  #升降平台所在处塔筒内径
                    acc_lift_lx_code_s = sheet.cell_value(i * 9 + 2, 7)  #升降机起始平台lx
                    acc_lift_ly_code_s = sheet.cell_value(i * 9 + 2, 8)  #升降机起始平台ly
                except :#如果没值，则和项目的一致，此处为了配合没有升降机起始平台的项目
                    acc_lift_plat_code_s = sheet.cell_value(i * 9 + 1, 1)#附件总成的图号
                    acc_lift_di_code_s = lay_lift_di  #升降平台所在处塔筒内径
                    acc_lift_lx_code_s = lay_lift_lx #升降机起始平台lx
                    acc_lift_ly_code_s = lay_lift_ly  #升降机起始平台ly
                if acc_lift_plat_code_s == "" and acc_lift_di_code_s == "" and acc_lift_lx_code_s == "" and acc_lift_ly_code_s == "":#如果是空字符，同样赋值
                    acc_lift_plat_code_s = sheet.cell_value(i * 9 + 1, 1)#附件总成的图号
                    acc_lift_di_code_s = lay_lift_di  #升降平台所在处塔筒内径
                    acc_lift_lx_code_s = lay_lift_lx #升降机起始平台lx
                    acc_lift_ly_code_s = lay_lift_ly  #升降机起始平台ly
                lift_plat_code.append(acc_lift_plat_code_s)  # 升降平台
                lift_plat_di.append(acc_lift_di_code_s)  # 升降平台所在处塔筒内径
                lift_plat_lx.append(acc_lift_lx_code_s)  # 升降机起始平台lx
                lift_plat_ly.append(acc_lift_ly_code_s)  # 升降机起始平台ly
                #顶平台
                top_plat_code.append(sheet.cell_value(i * 9 + 1, 1))#顶平台所对应的附件总成总图号
                top_plat_di.append(sheet.cell_value(i * 9 + 1, 6))
                top_plat_lx.append(sheet.cell_value(i * 9 + 1, 7))
                top_plat_ly.append(sheet.cell_value(i * 9 + 1, 8))
                acc_weight.append(sheet.cell_value(i * 9 + 1, 3))
            # liftcode = self.choose3(15, lay_lift_lx, lay_lift_ly, lay_lift_di, lift_plat_code, lift_plat_di,
            #                         lift_plat_lx, lift_plat_ly,acc_weight)  # 起始平台对应总成物料号

            liftcode = self.choose4(diff, lay_lift_lx, lay_lift_ly, lay_lift_di, lift_plat_code, lift_plat_di,
                                    lift_plat_lx, lift_plat_ly,acc_weight)  # 起始平台对应总成物料号

            # topcode = self.choose3(15, lay_top_lx, lay_top_ly, lay_top_di, top_plat_code, top_plat_di, top_plat_lx,
            #                        top_plat_ly,acc_weight)  # 顶平台对应物料号

            topcode = self.choose4(diff, lay_top_lx, lay_top_ly, lay_top_di, top_plat_code, top_plat_di, top_plat_lx,
                                   top_plat_ly,acc_weight)  # 顶平台对应物料号
            if liftcode[0] == topcode[0]:#如果起始平台和顶平台对应的物料号相同
                return liftcode
            else:
                return ("","","","","")
        elif n ==  section_qty -1:#顶段
            terminal_plat_code ,terminal_plat_di, terminal_plat_lx, terminal_plat_ly= [], [], [], []
            liftbeam_code, liftbeam_di,liftbeam_lx,liftbeam_ly = [], [], [], []
            cable1_code, cable1_di, cable1_lx, cable1_ly = [], [], [], []
            cable2_code, cable2_di, cable2_lx, cable2_ly = [], [], [], []
            for i in range(3):
                top_plat_code.append(sheet.cell_value(i * 9 + 1, 1))#顶平台
                top_plat_di.append(sheet.cell_value(i * 9 + 1, 6))
                top_plat_lx.append(sheet.cell_value(i * 9 + 1, 7))
                top_plat_ly.append(sheet.cell_value(i * 9 + 1, 8))

                terminal_plat_code.append(sheet.cell_value(i * 9 + 2, 1))#马鞍平台
                terminal_plat_di.append(sheet.cell_value(i * 9 + 2, 6))
                terminal_plat_lx.append(sheet.cell_value(i * 9 + 2, 7))
                terminal_plat_ly.append(sheet.cell_value(i * 9 + 2, 8))

                liftbeam_code.append(sheet.cell_value(i * 9 + 3, 1))#升降机吊梁
                liftbeam_di.append(sheet.cell_value(i * 9 + 3, 6))
                liftbeam_lx.append(sheet.cell_value(i * 9 + 3, 7))
                liftbeam_ly.append(sheet.cell_value(i * 9 + 3, 8))

                cable1_code.append(sheet.cell_value(i * 9 + 4, 1))#电缆护套梁一
                cable1_di.append(sheet.cell_value(i * 9 + 4, 6))
                cable1_lx.append(sheet.cell_value(i * 9 + 4, 7))
                cable1_ly.append(sheet.cell_value(i * 9 + 4, 8))

                cable2_code.append(sheet.cell_value(i * 9 + 5, 1))#电缆护套梁二
                cable2_di.append(sheet.cell_value(i * 9 + 5, 6))
                cable2_lx.append(sheet.cell_value(i * 9 + 5, 7))
                cable2_ly.append(sheet.cell_value(i * 9 + 5, 8))
                acc_weight.append(sheet.cell_value(i * 9 + 1, 3))
            # result_terminal_plat = self.choose3(15, lay_terminal_plat_lx, lay_terminal_plat_l2, lay_terminal_plat_di, top_plat_code, terminal_plat_di, terminal_plat_lx,
            #                             terminal_plat_ly,acc_weight)  # 马鞍平台筛选
            # result_top_plat= self.choose3(15, lay_top_plat_lx, lay_top_plat_ly, lay_top_plat_di, top_plat_code, top_plat_di, top_plat_lx,
            #                             top_plat_ly,acc_weight) # 顶平台筛选
            # result_beamlift= self.choose3(6, lay_liftbeam_lx, lay_liftbeam_ly, lay_liftbeam_di, liftbeam_code, liftbeam_di, liftbeam_lx,
            #                             liftbeam_ly,acc_weight)  # 升降机吊梁筛选
            # result_cable1= self.choose3(6, lay_cable1_lx, lay_cable1_ly, lay_cable1_di, cable1_code, cable1_di, cable1_lx,
            #                             cable1_ly,acc_weight)  # 升降机吊梁筛选
            # result_cable2= self.choose3(6, lay_cable2_lx, lay_cable2_ly, lay_cable2_di, cable2_code, cable2_di, cable2_lx,
            #                             cable2_ly,acc_weight)  # 升降机吊梁筛选

            result_terminal_plat = self.choose4(diff, lay_terminal_plat_lx, lay_terminal_plat_l2, lay_terminal_plat_di, top_plat_code, terminal_plat_di, terminal_plat_lx,
                                        terminal_plat_ly,acc_weight)  # 马鞍平台筛选
            result_top_plat= self.choose4(diff, lay_top_plat_lx, lay_top_plat_ly, lay_top_plat_di, top_plat_code, top_plat_di, top_plat_lx,
                                        top_plat_ly,acc_weight) # 顶平台筛选
            result_beamlift= self.choose4(6, lay_liftbeam_lx, lay_liftbeam_ly, lay_liftbeam_di, liftbeam_code, liftbeam_di, liftbeam_lx,
                                        liftbeam_ly,acc_weight)  # 升降机吊梁筛选
            result_cable1= self.choose4(6, lay_cable1_lx, lay_cable1_ly, lay_cable1_di, cable1_code, cable1_di, cable1_lx,
                                        cable1_ly,acc_weight)  # 电缆护套梁1
            result_cable2= self.choose4(6, lay_cable2_lx, lay_cable2_ly, lay_cable2_di, cable2_code, cable2_di, cable2_lx,
                                        cable2_ly,acc_weight)  # 电缆护套梁2



            if result_terminal_plat[0] == result_top_plat[0] and result_terminal_plat[0] == result_beamlift[0] and result_terminal_plat[0] == result_cable1[0] and result_terminal_plat[0] == result_cable2[0] :
                return result_terminal_plat
            else:
                return ("","","","","")


        else:#中间段
            acc_tmd_qty = []
            for i in range(3):
                top_plat_code.append(sheet.cell_value(i * 9 + 1, 1))  # 顶平台
                top_plat_di.append(sheet.cell_value(i * 9 + 1, 6))
                top_plat_lx.append(sheet.cell_value(i * 9 + 1, 7))
                top_plat_ly.append(sheet.cell_value(i * 9 + 1, 8))
                acc_weight.append(sheet.cell_value(i * 9 + 1, 3))
                acc_tmd_qty.append(sheet.cell_value(i * 9 + 1, 9))#数据库中阻尼器的数量

            # topcode = self.choose3(15, lay_top_lx, lay_top_ly, lay_top_di, top_plat_code, top_plat_di, top_plat_lx,
            #                            top_plat_ly,acc_weight)  # 顶平台对应物料号

            topcode = self.choose5(diff, lay_top_lx, lay_top_ly, lay_top_di, top_plat_code, top_plat_di, top_plat_lx,
                                       top_plat_ly,acc_weight, lay_tmd_qty, acc_tmd_qty)  # 顶平台对应物料号

            if topcode:
                return topcode
            else:
                return ("","","","","","")


def read_excel_to_dict(filename):
    workbook = openpyxl.load_workbook(filename)
    sheets_data = {}

    for sheetname in workbook.sheetnames:
        sheet = workbook[sheetname]
        rows_data = {}
        # 获取列名
        # 从第二行开始迭代
        for row in sheet.iter_rows(min_row=2, max_row=4, values_only=True):
            dicccc = {}
            dicccc.update({'seri': row[0]})#机型
            dicccc.update({'d_mid_bottom': row[2]})  # 塔架下中径
            dicccc.update({'d_mid_top': row[3]})  # 塔架上中径
            dicccc.update({'doortype': row[4]})  # 门框形式
            dicccc.update({'liftype': row[5]})  # 升降机形式
            dicccc.update({'di_mid': row[6]})  # 平台对应设计内径
        sheets_data[sheetname] = dicccc


    return sheets_data


def project_math(filename):
    # 你的变量
    a = 14240
    b = "V17"
    c = 4450
    # 加载工作簿
    wb = openpyxl.load_workbook(filename)

    # 根据a的值选择工作表，这里我们将a转换为字符串去匹配工作表名称
    sheet_name = str(a)
    if sheet_name in wb.sheetnames:
        sheet = wb[sheet_name]
    else:
        print(f'没有找到名为 {sheet_name} 的工作表。')
        exit()

    # 假设我们不确定b和c对应的列，我们需要遍历每一行
    # 初始化一个空字典来存储匹配的行数据
    matched_row_dict = {}

    for row in sheet.iter_rows(min_row=2, values_only=True):  # 假设第一行是标题行，从第二行开始遍历
        # 假定列b和列c是整数，指示列的索引(从1开始), 因此我们需要-1来访问对应的列值
        print(row[0])
        if row[0] == b and row[2] == c:
            # 匹配到了这一行，将行转换为字典形式，这里我们简单地将列索引作为键
            matched_row_dict = {'Column ' + str(b): row[0], 'Column ' + str(c): row[1]}
            break  # 假设只有一行会匹配，找到后即可退出循环

    if matched_row_dict:
        print('找到匹配行:', matched_row_dict)
    else:
        print('没有找到匹配的行。')




if __name__ == '__main__':
    rootdir = r"D:\oneclicktower\testfolder"
    towerGeoExcel = os.path.join(rootdir, "ALL Accessory.xlsx")
    geo_path = r'D:\Creo_Parametric_Design\Creo参数化设计_中间段\这只是个测试\TowerGeoInput_10522020_HH105m.xlsx'
    lay_path = r'D:\Creo_Parametric_Design\Creo参数化设计_中间段\这只是个测试\项目布局表.xlsx'
    target_path = r'E:\towerdesign\测试'
    tool_initial = Creo_tool(geo_path,lay_path,target_path)
    #print(read_excel_to_dict(towerGeoExcel))

