#选取内附件
from towerdesign.tools.tool import Creo_tool
import os
def select_accessory(towerGeoExcel):
    dir_path = os.path.dirname(towerGeoExcel)#塔架数据表所在的目录
    file_name = os.path.splitext(os.path.basename(towerGeoExcel))[0] #塔架数据表的名称，不带扩展名.xlsx
    print(file_name)
    #一些工具的初始化
    tool_initial = Creo_tool(towerGeoExcel)
    section_qty = tool_initial.flSecHqty()['section_qty']  # 段数
    print(section_qty)
    hh = tool_initial.heightJudge()  # 得到塔架的高度
    print(hh)
    print("heght:",tool_initial.flSecHqty()['flange_height'])
    #每段的长度
    secNLength_list = tool_initial.secNLength()
    print('每段的长度：',secNLength_list)
    #每段的下直径
    flangePos_list = tool_initial.flangePos()
    print(flangePos_list)

    print("aaaaaaaaaaaaaaaaaaaaaaaaaaa")

    print(tool_initial.flangePosAll())
    print(tool_initial.get_mid_dim())
    print("加强板门洞：",tool_initial.doortype())




    print(tool_initial.calculate_diameter_at_height(0,10,10,1))

    print(tool_initial.mgeoRead(5))
    #第一段平台所在处的内径判断
    n  = 5
    a = 1265#平台距离上法兰的距离
    h = secNLength_list[0] - a - tool_initial.mgeoRead(n)[0][0] #平台距离下筒体的距离
    h = 23625
    D_top = tool_initial.mgeoRead(n)[3][-2] - tool_initial.mgeoRead(n)[1][-2]# 钢筒体最上端中径，不包括法兰
    print("D_top：",D_top)
    D_bottom = tool_initial.mgeoRead(n)[2][1] - tool_initial.mgeoRead(n)[1][1]  # 钢筒体最下端中径，不包括法兰
    print('D_bottom:', D_bottom)
    H = secNLength_list[n] - tool_initial.mgeoRead(n)[0][0] - tool_initial.mgeoRead(n)[0][-1]
    print()
    print(tool_initial.calculate_diameter_at_height(D_top, D_bottom, H, h))
    print(tool_initial.get_di(5,1265))
    print("fdddddddddddddddddddddddddddddddddd")
    #机型
    seri =  "v12"
    #升降机形式
    lift_type= 0
    #平台所在处内径
    h_plate = 1250

    project_data = {}
    #段数section_qty
    for i in range(section_qty):
        dicccc = {}
        #dictt = {{'seclength':tool_initial.secNLength()[i]},{'d_mid_bottom': tool_initial.get_mid_dim()[i]},{'d_mid_top': tool_initial.get_mid_dim()[i * 2 + 1]}}
        dicccc.update({'seclength':tool_initial.secNLength()[i]})
        dicccc.update({'seri': seri})
        dicccc.update({'d_mid_bottom': tool_initial.get_mid_dim()[i]})
        dicccc.update({'d_mid_top': tool_initial.get_mid_dim()[i * 2 + 1]})
        dicccc.update({'doortype': tool_initial.doortype()})
        dicccc.update({'liftype': lift_type})
        dicccc.update({'di_mid': round(tool_initial.get_di(n, h_plate), 2)})
        project_data[f'section_{i}'] = dicccc
    print(project_data)














if __name__ == '__main__':
    rootdir = r"D:\oneclicktower\testfolder"
    towerGeoExcel = os.path.join(rootdir, "HH130m_6段_中径4.95m.xlsx")
    select_accessory(towerGeoExcel)