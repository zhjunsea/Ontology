#from collections import OrderedDict
#from pyexcel_xls import save_data
#from pyexcel_xls import get_data
from suds.client import Client
#import pyexcel_xls
from towerdesign.tools.tool import Creo_tool

class PlmCode(Creo_tool):
    def plmcode_main(self):
        OA = "33905"
        Path = "2.XMW\\基础、塔架 Foundation and tower\\产品图纸 Product drawings\\60.00.01408 GW140-S-90米塔架（五段）锚栓（华润广西贺州平桂大平80MW风电场项目一期）\\\\"
        header = {
            'User-Agent': 'User-Agent: Mozilla/4.0 (compatible; MSIE 6.0; MS Web Services Client Protocol 2.0.50727.8937)'
        }
        client = Client('http://10.1.29.225:8080/AdapterService?wsdl')
        client.set_options(soapheaders=[header, ])
        section_qty = self.flSecHqty()[2]  # 段数
        node_sum = {0:"CEA3EEFE810",1:"CEA3EEFE811",2:"CEA3EEFE812",3:"CEA3EEFE813",4:"CEA3EEFE814"}
        #塔架下段焊合，塔架中下段焊合，塔架中段焊合，塔架中上段焊合，塔架上段焊合
        bookcode =[]
        sheetcode = []#每个sheet里的总code量

        for i in range(section_qty):
            if i == 0:#第一段
                sheetcode = []  # 每个sheet里的总code量
                node = node_sum[0]#图号类型
                sheetcode.append(self.plmgetcode(client, Path, OA, "第"+str(i+1)+"段塔筒焊合", "Section"+str(i+1)+"Welded", node))#第i+1段塔筒焊合
                sheetcode.append(self.plmgetcode(client, Path, OA, "筒体", "Cylinder", node))  # 第i+1段塔筒筒体
                sheetcode.append(self.plmgetcode(client, Path, OA, "加强板", "Reinforcing plate", node))  # 加强板
                sheetcode.append(self.plmgetcode(client, Path, OA, "塔架底法兰", "Bottom flange", node))  # 塔架底法兰
                sheetcode.append(self.plmgetcode(client, Path, OA, "连接法兰" + str(i + 1) , "Connection flange" + str(i+1), node))  # 连接法兰
                bookcode.append(sheetcode)

            elif i == 1:#第二段
                sheetcode = []  # 每个sheet里的总code量
                node = node_sum[1]#图号类型
                sheetcode.append(self.plmgetcode(client, Path, OA, "第"+str(i+1)+"段塔筒焊合", "Section"+str(i+1)+"Welded", node))#第i+1段塔筒焊合
                sheetcode.append(self.plmgetcode(client, Path, OA, "筒体", "Cylinder", node))  # 第i+1段塔筒筒体
                sheetcode.append(self.plmgetcode(client, Path, OA, "连接法兰" + str(i + 1) , "Connection flange" + str(i+1), node))  # 第i+1段塔筒焊合
                bookcode.append(sheetcode)
            elif i == section_qty - 2:#顶段往下一段
                sheetcode = []  # 每个sheet里的总code量
                node = node_sum[3]#图号类型
                sheetcode.append(self.plmgetcode(client, Path, OA, "第"+str(i+1)+"段塔筒焊合", "Section"+str(i+1)+"Welded", node))#第i+1段塔筒焊合
                sheetcode.append(self.plmgetcode(client, Path, OA, "筒体", "Cylinder", node))  # 第i+1段塔筒筒体
                sheetcode.append(self.plmgetcode(client, Path, OA, "连接法兰" + str(i + 1) , "Connection flange" + str(i+1), node))  # 第i+1段塔筒焊合
                bookcode.append(sheetcode)
            elif i == section_qty - 1:#顶段
                sheetcode = []  # 每个sheet里的总code量
                node = node_sum[4]
                sheetcode.append(self.plmgetcode(client, Path, OA, "顶段塔筒焊合", "Top Section Welded", node))#顶段塔筒焊合
                sheetcode.append(self.plmgetcode(client, Path, OA, "筒体", "Cylinder", node))  # 第i+1段塔筒筒体
                bookcode.append(sheetcode)
            else :#其他所有的中间段
                sheetcode = []  # 每个sheet里的总code量
                node = node_sum[2]#图号类型
                sheetcode.append(self.plmgetcode(client, Path, OA, "第"+str(i+1)+"段塔筒焊合", "Section"+str(i+1)+"Welded", node))#第i+1段塔筒焊合
                sheetcode.append(self.plmgetcode(client, Path, OA, "筒体", "Cylinder", node))  # 第i+1段塔筒筒体
                sheetcode.append(self.plmgetcode(client, Path, OA, "连接法兰" + str(i + 1) , "Connection flange" + str(i+1), node))  # 第i+1段塔筒焊合
                bookcode.append(sheetcode)
        return bookcode



    def plmgetcode(self,client,code_path,OA,nameFou_Ch,nameFou_En,node_FOU):
        folder = code_path# 定义图纸在PLM文件夹路径
        source = 'buy'
        defaultUnit = 'ea'
        MaterialType = '0类'
        GW_Transfer2ERP = 'TRUE'
        ISSequenceControlMaterial = 'N'
        genericType = 'standard'
        endItem = '否'
        GW_ZWCL = ''
        GW_YWCL = ''
        # nameFou_Ch = "四段90米塔架"    # 基础图纸中文名
        # nameFou_En = "4 Sections 90m HH tower"    # 基础图纸英文名
        # node_FOU = 'JC7016'      #基础环基础图纸分类号
        creatpartPara_Fou = r'&lt;ROOT&gt;&lt;BASE&gt;&lt;name&gt;' + nameFou_Ch + '&lt;/name&gt;&lt;GW_YWMC&gt;' + nameFou_En + '&lt;/GW_YWMC&gt;' \
                            r'&lt;source&gt;' + source + '&lt;/source&gt;&lt;defaultUnit&gt;' + defaultUnit + '&lt;/defaultUnit&gt;' \
                            r'&lt;folder&gt;' + folder + '&lt;/folder&gt;&lt;TC_MaterialType&gt;' + MaterialType + '&lt;/TC_MaterialType&gt;' \
                            r'&lt;GW_Transfer2ERP&gt;' + GW_Transfer2ERP + '&lt;/GW_Transfer2ERP&gt;' \
                            r'&lt;ISSequenceControlMaterial&gt;' + ISSequenceControlMaterial + '&lt;/ISSequenceControlMaterial&gt;' \
                            r'&lt;genericType&gt;' + genericType + '&lt;/genericType&gt;' \
                            r'&lt;endItem&gt;' + endItem + '&lt;/endItem&gt;' \
                            r'&lt;GW_ZWCL&gt;' + GW_ZWCL + '&lt;/GW_ZWCL&gt;' \
                            r'&lt;GW_YWCL&gt;' + GW_YWCL + '&lt;/GW_YWCL&gt;' \
                            r'&lt;/BASE&gt;&lt;CLASSIFICATION&gt;' \
                            r'&lt;node&gt;' + node_FOU + '&lt;/node&gt;&lt;/CLASSIFICATION&gt;&lt;/ROOT&gt;'
        code_x= client.service.createPart(creatpartPara_Fou, OA)  # 申请基础环基础图号
        return code_x
        # print(client.service.getClassficationXML("36368"))
        # print(Fou_Num)

    def style(self,color):
        pattern = xlwt.Pattern()
        pattern.pattern = xlwt.Pattern.SOLID_PATTERN
        pattern.pattern_fore_colour = color
        style1 = xlwt.XFStyle()
        style1.pattern = pattern#名称颜色
        return style1


# test111 = PlmCode()
# test111.plmcode_main()


