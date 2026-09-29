weight_info =  """/**设置显示模型中文名称项**/
PART_NAME=PTC_COMMON_NAME

/**设置显示模型重量**/
E_WGH=pro_mp_mass

/*将重量参数值由实数类型转化为字符串类型
if  PRO_MP_MASS<0.1
重量 ="0.1"
endif
if PRO_MP_MASS>=1
重量 =extract(itos(PRO_MP_MASS*100),1,string_length(itos(PRO_MP_MASS*100))-2)+\
"."+extract(itos(PRO_MP_MASS*100),string_length(itos(PRO_MP_MASS*100))-1,2)
endif
if PRO_MP_MASS<1 & PRO_MP_MASS>=0.1
重量 =extract(itos(PRO_MP_MASS*100),1,string_length(itos(PRO_MP_MASS*100))-2)+\
"0."+extract(itos(PRO_MP_MASS*100),string_length(itos(PRO_MP_MASS*100))-1,2)
endif

/*重量小于10时，3位小数
If PRO_MP_MASS<10
E_WGH=ceil(PRO_MP_MASS-0.0004,3)
else
endif

/*重量大于等于10且小于100时，2位小数
If PRO_MP_MASS>=10 & PRO_MP_MASS<100
E_WGH=ceil(PRO_MP_MASS-0.004,2)
else
endif
/*重量大于等于100且小于1000时，1位小数
If PRO_MP_MASS>=100 & PRO_MP_MASS<1000
E_WGH=ceil(PRO_MP_MASS-0.04,1)
else
endif
/*重量大于等于1000时，整数
If PRO_MP_MASS>=1000
E_WGH=ceil(PRO_MP_MASS-0.4)
else
endif
"""

drive_size = """
/***************筒体驱动尺寸***********************************************************/
SEC_H_TOTAL=SEC_H_TOTAL
D_TOP=DA_TOP
H_total_TOP=TFL_TOP+H_TOP
/***************第1节筒体***********************************************************/
H_TOTAL_BOTTOM=TFL_BOTTOM+H_BOTTOM
下法兰总高=H_TOTAL_BOTTOM
CY1_H=CY1_H-DELTA
/***************第2节筒体***********************************************************/
CY2_H=CY2_H-DELTA
/***************第3节筒体***********************************************************/
CY3_H=CY3_H-DELTA
/***************第4节筒体***********************************************************/
CY4_H=CY4_H-DELTA
/***************第5节筒体***********************************************************/
CY5_H=CY5_H-DELTA
/***************第6节筒体***********************************************************/
CY6_H=CY6_H-DELTA
/***************第7节筒体***********************************************************/
CY7_H=CY7_H-DELTA
/***************第8节筒体***********************************************************/
CY8_H=CY8_H-DELTA
/***************第9节筒体***********************************************************/
CY9_H=CY9_H-DELTA
/***************第10节筒体***********************************************************/
CY10_H=CY10_H-DELTA
/***************第11节筒体***********************************************************/
CY11_H=CY11_H-DELTA
/***************第12节筒体***********************************************************/
CY12_H=CY12_H-DELTA
/***************第13节筒体***********************************************************/
CY13_H=CY13_H-DELTA
/***************第14节筒体***********************************************************/
CY14_H=CY14_H-DELTA
/***************第15节筒体***********************************************************/
CY15_H=CY15_H-DELTA
/***************第16节筒体***********************************************************/
CY16_H=CY16_H-DELTA
/***************第17节筒体***********************************************************/
CY17_H=CY17_H-DELTA
/***************第18节筒体***********************************************************/
CY18_H=CY18_H-DELTA
/***************第19节筒体***********************************************************/
CY19_H=CY19_H-DELTA
/***************第20节筒体***********************************************************/
CY20_H=CY20_H-DELTA
/***************每节起始位置***********************************************************/
CY2_H_STAR=CY1_H+H_TOTAL_BOTTOM+DELTA
CY3_H_STAR=CY2_H_STAR+CY2_H+DELTA
CY4_H_STAR=CY3_H_STAR+CY3_H+DELTA
CY5_H_STAR=CY4_H_STAR+CY4_H+DELTA
CY6_H_STAR=CY5_H_STAR+CY5_H+DELTA
CY7_H_STAR=CY6_H_STAR+CY6_H+DELTA
CY8_H_STAR=CY7_H_STAR+CY7_H+DELTA
CY9_H_STAR=CY8_H_STAR+CY8_H+DELTA
CY10_H_STAR=CY9_H_STAR+CY9_H+DELTA
CY11_H_STAR=CY10_H_STAR+CY10_H+DELTA
CY12_H_STAR=CY11_H_STAR+CY11_H+DELTA
CY13_H_STAR=CY12_H_STAR+CY12_H+DELTA
CY14_H_STAR=CY13_H_STAR+CY13_H+DELTA
CY15_H_STAR=CY14_H_STAR+CY14_H+DELTA
CY16_H_STAR=CY15_H_STAR+CY15_H+DELTA
CY17_H_STAR=CY16_H_STAR+CY16_H+DELTA
CY18_H_STAR=CY17_H_STAR+CY17_H+DELTA
CY19_H_STAR=CY18_H_STAR+CY18_H+DELTA
CY20_H_STAR=CY19_H_STAR+CY19_H+DELTA
"""
skel_name = """/*---------------------| 中文名称 |------------------------------*/
PART_NAME=PTC_COMMON_NAME

"""
skel_comp = """Bush_a=120 /*防雷螺柱间距角度

/*下端纵向定位判定
DI_BOTTOM_BUSH=DI_BOTTOM
DI_TOP_BUSH=DI_TOP 
if TFL_BOTTOM > 100
bush_bottom_h = 50
else
bush_bottom_h = TFL_BOTTOM/2
endif

/*上端纵向定位判定
if TFL_TOP > 100
bush_top_h = 50
else
bush_top_h = TFL_TOP/2
endif

/*---------------------| 侧支撑 |------------------------------*/
$H1_S=-980*cos(Alpha)/*爬梯侧支撑距上法兰上端面安装高度

/*---------------------| 电缆线槽定位 |------------------------------*/
cable_up_circle =DA_TOP-2*S_TOP

/*---------------------| 休息踏板 |------------------------------*/
if $H_LADDER_TOP < 0
    $rest1=-(280*10+140)
 else
   $rest1=-(280*11+140)
 endIF

/*---------------------| 过法兰支撑 |------------------------------*/
OFS_BOTTOM_H = 50 /*下方过法兰支撑距离底平面
DI_BOTTOM_OFS=DI_BOTTOM /*下方过法兰支撑所在位置内径
OFS_TOP_H = 50 /*上方过法兰支撑距离底平面
DI_TOP_OFS =DI_TOP /*上方过法兰支撑所在位置内径

"""
t_skel_comp ="""
/**请在下方自定义关系式**/
/********************************
α=atan((DA_BOTTOM-DA_TOP)/2/SEC_H_TOTAL)
H_L_LADDER=L_LADDER*cos(α)
第一个电缆夹板相对于爬梯底段距离=980*cos(α)
/***********电缆托架长度*********************
电缆托架长度l_4=L_CABLE
电缆托架长度l_2=L_CABLE
电缆托架长度l_1=L_CABLE
电缆托架长度l_3=L_CABLE
/***********电缆隔环高度*********************
H_RING_1=H_RING
H_RING_2=H_RING
H_RING_3=H_RING
H_RING_4=H_RING
H_RING_5=H_RING
H_RING_6=H_RING
H_RING_7=H_RING
H_RING_8=H_RING
H_RING_9=H_RING
/**********************************************
/*****************直爬梯定位***************
DI_LADDER_UP=DA_TOP-2*S_TOP
DI_LADDER_DOWN=DA_BOTTOM-2*S_BOTTOM
/**********************************************
/*****************爬梯横支撑***************
/*爬梯横支撑阵列数
爬梯支撑定位点 = L_LADDER/280
/*****************************************
/*****************1.2米电缆线槽定位，适用于电缆线槽一顶部***************
$小线槽定位=1200-H_TRAY_I
/*****************防雷螺柱定位***************
 DI_BOTTOM_BUSH=DI_BOTTOM
/*下端纵向定位
if TFL_BOTTOM > 100
bush_bottom_h = 50
else
bush_bottom_h = TFL_BOTTOM/2
endif
 
 
/*****************上方第一个休息踏板***************
$rest1=-(280*11+140)

"""

down_skel_info = """
/*下端纵向定位
IF TFL_BOTTOM > 100
H_BUSH_BOTTOM = 50
ELSE
H_BUSH_BOTTOM = TFL_BOTTOM/2
ENDIF
/*上端纵向定位
IF TFL_TOP > 100
H_BUSH_TOP = 50
ELSE
H_BUSH_TOP= TFL_TOP/2
ENDIF
"""

flange_start="""
/*上法兰尺寸名称转换为参数名称
DA_BOTTOM=DA_BOTTOM_PARA
DI=DI_PARA
DA_TOP=DA_TOP_PARA
DM=DM_PARA
TFL=TFL_PARA
S=S_PARA
H_TOTAL=H_TOTAL_PARA
DHOLE=DHOLE_PARA
N=N_PARA
\n
"""

flange_end="""
D83=TFL/2    /*连接孔定位高度
D25=(360/N)/2
d90=360/n*2
d241=360/N
d83=TFL/2
"""

dmwz = """
/*****定位面位置*****/
D295=TFL_BOTTOM+H_BOTTOM
D296=SEC_H_TOTAL-TFL_TOP-H_TOP\n
"""