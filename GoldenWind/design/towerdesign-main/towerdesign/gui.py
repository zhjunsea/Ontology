#这是一个完全的开发版，有第三个阶段的功能
#20181121，添加大数据分析功能
import tkinter
import tkinter.filedialog
import tkinter.messagebox

root = tkinter.Tk()
#root.title('塔架三维设计信息前处理平台v2.0')#标题名称
root.title('OneClickTower v1.0')#标题名称
root.geometry('550x160')#图形框的分辨率
root.iconbitmap("D:\Program Files\OneKeyTower\Gui-python\icos\icon.ico")
def selectPath():#塔架主体信息
    path_ = tkinter.filedialog.askopenfilename()
    geo_path.set(path_)

def selectPath2():#布局信息
    path_ = tkinter.filedialog.askopenfilename()
    lay_path.set(path_)

def selectDir():#目标文件夹
    path_ = tkinter.filedialog.askdirectory()
    target_path.set(path_)


def about():#关于程序的说明
    # geo_path = geo_path_frame.get()#得到geo文本框里的值
    # lay_path = lay_path_frame.get()#得到布局信息文本框里的值
    # target_path = target_path_frame.get()#得到目标文件夹文本框里的值
    # # geo_path = "//rdfs.goldwind.com.cn/vdi_user_workspace_v3/userdata/33905/12月/04.详图/60.00.01590/60.00.01590.xlsx"  # 得到geo文本框里的值
    # # lay_path = "//rdfs.goldwind.com.cn/vdi_user_workspace_v3/userdata/33905/12月/04.详图/60.00.01590/5段90米塔架布局表v2.0.0.xlsx"  # 得到布局信息文本框里的值
    # # target_path = "//rdfs.goldwind.com.cn/vdi_user_workspace_v3/userdata/33905/12月/04.详图/60.00.01590"
    #
    # main.tadfull(geo_path, lay_path, target_path)
    # done()

    tkinter.messagebox.showinfo('关于程序','\n版本：v1.0.0\n'
                                       'RTX:吴航\nwuhang@goldwind.com.cn\n塔架基础技术部')

def done():#完成弹窗
    tkinter.messagebox.showinfo('完成','完成')

def creo_parameter():#参数生成按钮
    geo_path = geo_path_frame.get()#得到geo文本框里的值
    lay_path = lay_path_frame.get()#得到布局信息文本框里的值
    target_path = target_path_frame.get()#得到目标文件夹文本框里的值

    # geo_path = "D:/code/Tower-Design-Plantform/金风2.0MW机组HH90m塔架主体数据(4段，121_2000，SW59.5B，IEC S，基础环，山西吕梁岚县大蛇头一期50MW项目)_太重基础环2.xlsx"#得到geo文本框里的值
    # lay_path = "D:/code/Tower-Design-Plantform/布局表v2.0.0.xlsx"#得到布局信息文本框里的值
    # target_path = "D:/Personal/Desktop"#得到目标文件夹文本框里的值

    main.creoParaEx(geo_path, lay_path, target_path)
    done()

def excelshellcut():#辅助设计按钮
    geo_path = geo_path_frame.get()#得到geo文本框里的值
    lay_path = lay_path_frame.get()#得到布局信息文本框里的值
    target_path = target_path_frame.get()#得到目标文件夹文本框里的值
    # geo_path = "//rdfs.goldwind.com.cn/vdi_user_workspace_v3/userdata/33905/12月/04.详图/60.00.00946/60.00.00946.xlsx"  # 得到geo文本框里的值
    # lay_path = "//rdfs.goldwind.com.cn/vdi_user_workspace_v3/userdata/33905/12月/04.详图/6段140米塔架(2.2)标准布局表v2.0.0.xlsx"  # 得到布局信息文本框里的值
    # target_path = "//rdfs.goldwind.com.cn/vdi_user_workspace_v3/userdata/33905/12月/04.详图/60.00.00946"

    main.towerBasicEx(geo_path, lay_path, target_path)
    done()

def onekeydetail_button():#一键详图按钮
    geo_path = geo_path_frame.get()#得到geo文本框里的值
    lay_path = lay_path_frame.get()#得到布局信息文本框里的值
    target_path = target_path_frame.get()#得到目标文件夹文本框里的值
    # geo_path = "//rdfs.goldwind.com.cn/vdi_user_workspace_v3/userdata/33905/12月/04.详图/60.00.01523/60.00.01523.xlsx"  # 得到geo文本框里的值
    # lay_path = "//rdfs.goldwind.com.cn/vdi_user_workspace_v3/userdata/33905/12月/04.详图/6段140米塔架(2.5B)标准布局表v2.0.0.xlsx"  # 得到布局信息文本框里的值
    # target_path = "//rdfs.goldwind.com.cn/vdi_user_workspace_v3/userdata/33905/12月/04.详图/60.00.01523"
    main.onekeydetail_start(geo_path, lay_path, target_path)
    tkinter.messagebox.showinfo('完成', '塔架详图绘制完成，请审图！')

def oneclickbid_button():#一键详图按钮
    geo_path = geo_path_frame.get()#得到geo文本框里的值
    lay_path = lay_path_frame.get()#得到布局信息文本框里的值
    target_path = target_path_frame.get()#得到目标文件夹文本框里的值
    main.oneclickbid_start(geo_path, lay_path, target_path)
    done()
# def excelNew():#展板计算生成按钮
#     geo_path = geo_path_frame.get()#得到geo文本框里的值
#     lay_path = lay_path_frame.get()#得到布局信息文本框里的值
#     target_path = target_path_frame.get()#得到目标文件夹文本框里的值
#
#     # geo_path = "D:/code/cre/金风2.0MW机组HH90m塔架主体数据(4段，121_2000，SW59.5B，IEC S，基础环，山西吕梁岚县大蛇头一期50MW项目)_太重基础环2.xlsx"#得到geo文本框里的值
#     # lay_path = "D:/code/cre/布局表v2.0.0_Beta.xlsx"#得到布局信息文本框里的值
#     # target_path = "D:/Personal/Desktop"#得到目标文件夹文本框里的值
#
#     main.towerBasicEx(geo_path, lay_path, target_path)
#     done()

tkinter.Label(root, text = '塔架主体信息:').grid(row = 0, column = 0)
tkinter.Label(root, text = '布局信息:').grid(row = 1, column = 0)
tkinter.Label(root, text = '目标文件夹:').grid(row = 2, column = 0)

geo_path = tkinter.StringVar()
lay_path = tkinter.StringVar()
target_path = tkinter.StringVar()

geo_path_frame = tkinter.Entry(root,textvariable = geo_path,width = 60)#文本框显示
geo_path_frame.grid(row = 0, column = 1)
lay_path_frame = tkinter.Entry(root,textvariable = lay_path,width = 60)
lay_path_frame.grid(row = 1, column = 1)
target_path_frame = tkinter.Entry(root,textvariable = target_path,width = 60)
target_path_frame.grid(row = 2, column = 1)

tkinter.Button(root,text = '浏览',command = selectPath).grid(row = 0, column = 2)
tkinter.Button(root,text = '浏览',command = selectPath2).grid(row = 1, column = 2)
tkinter.Button(root,text = '浏览',command = selectDir).grid(row = 2, column = 2)
tkinter.Button(root,text = '参数生成',command = creo_parameter).grid(row = 3,column = 1)
tkinter.Button(root,text = '辅助设计',command = excelshellcut).grid(row = 3,column = 1,sticky = 'w')
#tkinter.Button(root,text = '表格生成',command = excelNew).grid(row = 3,column = 1,sticky = 'e')
tkinter.Button(root,text = '关于',command = about).grid(row = 3,column = 2)
tkinter.Button(root,text = '一键招标',command = oneclickbid_button).grid(row = 4,column = 1)
tkinter.Button(root,text = '一键详图',command = onekeydetail_button).grid(row = 4,column = 1,sticky = 'w')
root.mainloop()