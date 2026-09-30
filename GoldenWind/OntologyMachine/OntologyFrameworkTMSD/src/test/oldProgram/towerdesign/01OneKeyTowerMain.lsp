(prompt "\nTower&Foundation Tech Dept. \n")
(prompt "\n《OneClickTower V6.1.5.4》20240506\n")

;;;;;;;;;;;;;;;软件入口,CAD内的制图;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defun c:tower(/ inputstr)   ;用来在CAD界面中进行手动输入命令，生成塔架图
  (setq inputstr "tower")
  (verify inputstr);license验证并选择入口
)
;END c:tower();;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;;;;;;;;;;;;;;软件入口,CAD内的制图;;曹学敏新增;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defun c:tower_assembly(/ inputstr)   ;用来在CAD界面中进行手动输入命令，生成塔架总图*******（带附件）+目前仅针对V12*******
  (setq inputstr "tower_assembly")
  (verify inputstr);license验证并选择入口
)
;;;;;;;;;;;;;;;;;函数结束;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defun c:one_key_tower(/ inputstr)   ;用于批量生成塔架图，+ 明细表
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  (setq inputstr "one_key_tower")
  (verify inputstr);license验证并选择入口
)
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;;;;;;;;;;; ;用于在外部程序中，生成单独塔架图，无明细表,判断注册
;;;;;;;;;;;;;;;;;;;
(defun c:tower_single(/ inputstr)  
  (setq inputstr "tower_single")
  (verify inputstr);license验证并选择入口
)
;;;;;;;;函数结束;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;;;;;;;;;;; ;用于在外部程序中，生成塔架招标图
;;;;;;;;;;;;;;;;;;;
(defun c:oneclickbid_start(/ inputstr)  
  (setq inputstr "oneclickbid_start")
  (verify inputstr);license验证并选择入口
)
;;;;;;;;函数结束;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;About verify
;;;;;;验证程序,和分发入口
(defun verify(mainprog / passwordfile ReadPassword LocalPassword InnerPassword)
	(setq passwordfile (open"D:\\Program Files (x86)\\Autodesk\\AutoLisp\\TowerLicense.lc""r"))
	(if (= passwordfile nil)
		(progn
			(alert "未发现注册文件，请注册软件！")
			(print "未发现注册文件，请注册软件！")
			(exit)
		)
		(setq ReadPassword (read-line passwordfile))
	)
	(close passwordfile)
	(SysTimeToIntTime)
	(setq LocalPassword (XD:MD5 (vl-string->list sysTime)))
	(if (= ReadPassword LocalPassword)
		(progn
			(cond
				((= mainprog "tower") (towerdraw))
				((= mainprog "tower_assembly") (towerdraw_assembly))
				((= mainprog "one_key_tower") (towerdraw2))
				((= mainprog "tower_single") (towerdraw_single))
				((= mainprog "oneclickbid_start") (oneclickbid_main))
			)
		)
		(progn
			(InternetTime)
			(setq InnerPassword (XD:MD5(vl-string->list InterTime)))
			(if (= ReadPassword InnerPassword)
				(progn
					(cond
						((= mainprog "tower") (towerdraw))
						((= mainprog "tower_assembly") (towerdraw_assembly))
						((= mainprog "one_key_tower") (towerdraw2))
						((= mainprog "tower_single") (towerdraw_single))
						((= mainprog "oneclickbid_start") (oneclickbid_main))
					)
				)
				(progn
					(alert "软件注册已失效，请注册软件！")
					(print "软件注册已失效，请注册软件！")
				)
			)
		)
	)
	(princ);清除nil
)
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;;;;;手工出图;;;;;;
(defun towerdraw( / )
  (vl-load-com)
  (setvar "cmdecho" 0);加载扩展的AutoLISP函数
  (setvar "osmode" 0);不扑捉任何类型
  (setq mscale (GetScale));获取标题栏比例
  ;(setq mscale 140)
  ;(print mscale)
  (setvar "ltscale" (/ mscale 2));改变线型比例因子大小
  ;(print (/ mscale 2))
  (setq Towerdat_file (GetTowerdat_file));选择塔架数据表
  (defaultfile Towerdat_file);把上次打开的excel路径写入到默认路径中
  ;原点
  ;(setq pa (getpoint "\n选择塔底中心点："))
  ;(print "选择点为")
  ;(princ pa)
  (cond
    ((= mscale 100)
      (setq pa '(13269 8908 0));说明的插入原点
    )
    ((= mscale 120)
      (setq pa '(15923 10690 0));说明的插入原点
    )
    ((= mscale 140)
      (setq pa '(18576 12472 0));说明的插入原点
      (setq Pt_Tr '(33000 59000 0))
    )
    (t
      (setq pa '(52000 28000 0));说明的插入原点
    )
  );cond

  
  ;是否弹窗
  (setq is_alert T)
  (print "程序将以弹窗形式进行数据错误提示！")

  ;初始化，必须先做的
  (initializedata)
  ;数据预判断
  (setq logsystem T);log日志系统开
  ;(alert "a")
  (prejudge)
  ;(alert "b")
  ;图形绘制
  (maindraw)
  
)
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


;;;;;;;;;;;;;;;;;;应用与在外部程序中，单独生成塔架图;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defun towerdraw_single()
  (setq onekeydetail T)
  (vl-load-com)
  (setvar "cmdecho" 0)
  (setvar "osmode" 0);不扑捉任何类型
  (command "zoom" "a")
  (command "regen")
  ;比例确定
  (setq mscale (atoi(getstring "\n输入比例：")))
  (setvar "ltscale" (/ mscale 2))
  (setq TowerExcel_file (getstring "\n输入塔架几何数据表："))
  (setq Towerdat_file (substr TowerExcel_file 2 (- (strlen TowerExcel_file) 2)))
  ;得到 retTower、retFlange、retDoor,retEmbedded
  (GetTowerGeoData Towerdat_file)
  ;原点确定
  (setq pa (list (* 150 mscale) (atoi (getstring "\n输入纵坐标：")) 0.0))
  ;物料号表
  
  (setq Towernum_file (getstring "\n输入塔架物料号数据表："))
  (setq Towernum_file (substr Towernum_file 2 (- (strlen Towernum_file) 2)))
  (setq power (getstring "\n输入主体功率：") );主机的功率
  
  ;是否弹窗
  (setq is_alert nil)  ;是否弹出警告
  ;初始化，必须先做的
  (initializedata)
  ;得到设计辅助表的内容
  (GetAidedDesignData Towernum_file)
  
  ;数据预判断
  (setq logsystem T);log日志系统开
  (prejudge)
  
  ;图形绘制
  (maindraw)
)
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;;;;;;;;;;;;;;;;;应用与在外部程序中，一键招标图;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defun oneclickbid_main(/ pa_x pa_y)
  (setq oneclickbid_auto nil)
  (vl-load-com)
  (setvar "cmdecho" 0)
  (setvar "osmode" 0);不扑捉任何类型
  (command "zoom" "a")
  (command "regen")
  ;比例确定
  (setq mscale (atoi(getstring "\n输入比例：")))
  (setvar "ltscale" (/ mscale 2))
  (setq TowerExcel_file (getstring "\n输入塔架几何数据表："))
  (setq Towerdat_file (substr TowerExcel_file 2 (- (strlen TowerExcel_file) 2))) 
  ;得到 retTower、retFlange、retDoor,retEmbedded
  (GetTowerGeoData Towerdat_file)
  ;原点确定
  (setq pa_x (atoi (getstring "\n输入横坐标：")) )
  (setq pa_y (atoi (getstring "\n输入纵坐标：")) )
  (setq pa (list pa_x pa_y 0.0))

  
  ;是否弹窗
  (setq is_alert nil)  ;是否弹出警告
  ;初始化，必须先做的
  (initializedata)
  ;数据预判断
  (setq logsystem T);log日志系统开
  (prejudge)
  
  ;图形绘制
  (maindraw)
)
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;;;;;;;;;;;;;;;;;;;;;;;;应用于批量生成塔架图,程序界面用;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defun towerdraw2() 
  (vl-load-com)
  (setvar "cmdecho" 0)
  (setvar "osmode" 0);不扑捉任何类型
  (command "zoom" "a")
  (command "regen")
  ;比例确定
  (setq mscale (atoi (getstring "\n输入比例：")));atoi字符串转换成整数
  (setvar "ltscale" (/ mscale 2))
  (setq TowerExcel_file (getstring "\n输入塔架几何数据表："))
  (setq Towerdat_file (substr TowerExcel_file 2 (- (strlen TowerExcel_file) 2)))
  ;retTower 塔架数据
  (GetTowerGeoData Towerdat_file) 
  ;原点确定
  (setq pa (list (* 150 mscale) (atoi (getstring "\n输入纵坐标：")) 0.0))
  ;是否弹窗
  (setq is_alert nil)
  ;初始化，必须先做的
  (initializedata)
  ;数据预判断
  (prejudge)
  ;图形绘制
  (maindraw)
)
;;;;;;;;;函数结束;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;




;;;;;;手工出总图;;;;;;曹学敏新增
(defun towerdraw_assembly( / )
	(setq towerdraw_detail T)
	(vl-load-com)
	(setvar "cmdecho" 0);加载扩展的AutoLISP函数，初始化Autolisp的active环境
	(setvar "osmode" 0);不扑捉任何类型
	(setq mscale (GetScale));获取标题栏比例
	(setvar "ltscale" (/ mscale 2));改变线型比例因子大小
	(setq Towerdat_file (GetTowerdat_file));选择塔架数据表
	(setq Towerasm_file "D:\\Program Files (x86)\\Autodesk\\AutoLisp\\Accessory.xlsx");选择塔架附件设计表
	(setq Towerasm_file_1 "D:\\Program Files (x86)\\Autodesk\\AutoLisp\\Accessory_1.xlsx");选择塔架附件设计表
	;(setq Towerasm_file (GetTowerasm_file));选择塔架附件设计表
	(defaultfile Towerdat_file);把上次打开的excel路径写入到默认路径中
  ;原点
	;(setq pa (getpoint "\n选择塔底中心点："))
	;(print "选择点为")
	;(princ pa)
	(cond
		((= mscale 100)
		  (setq pa '(13269 8908 0));说明的插入原点
		)
		((= mscale 120)
		  (setq pa '(15923 10690 0));说明的插入原点
		)
		((= mscale 140)
		  (setq pa '(18576 12472 0));说明的插入原点
		  (setq Pt_Tr '(33000 59000 0))
		)
		(t
		  (setq pa '(52000 28000 0));说明的插入原点
		)
	);cond

  
	;是否弹窗
	(setq is_alert T)
	(print "程序将以弹窗形式进行数据错误提示！")

	;初始化，必须先做的
	(initializedata)
	;数据预判断
	(setq logsystem T);log日志系统开
	;(alert "a")
	(prejudge)
	(if  (= (value retDoor 0 18) "2600") ;
		(GetTowerDesignData Towerasm_file_1);得到塔架附件设计表的内容
		(GetTowerDesignData Towerasm_file);得到塔架附件设计表的内容
	);if
	(asm_judge);附件是否可借用的判断
	;图形绘制
	(maindraw)
  
)
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;



;;;;;;新总图出图;;;;;;
(defun C:tower_detail(/ Pt_Tr)
  (vl-load-com)
  (setvar "cmdecho" 0)
  (setvar "osmode" 0);不扑捉任何类型
  (setq mscale (GetScale));获取标题栏比例
  ;(setq mscale 100)
  (setvar "ltscale" (/ mscale 2));改变线型比例因子大小
  (setq Towerdat_file (GetTowerdat_file));选择塔架数据表,数据表的全路径
  (print Towerdat_file)
  (defaultfile Towerdat_file);把上次打开的excel路径写入到默认路径中

  (cond
    ((= mscale 100)
      (setq pa '(13269 8908 0));说明的插入原点
    )
    ((= mscale 120)
      (setq pa '(15923 10690 0));说明的插入原点
    )
    ((= mscale 140)
      (setq pa '(18576 12472 0));说明的插入原点
      (setq Pt_Tr '(33000 59000 0))
    )
    (t
      (setq pa '(52000 28000 0));说明的插入原点
    )
  );cond
  
  ;(print "选择点为")
  ;(princ pa)

  ;是否弹窗
  (setq is_alert T)
  (print "程序将以弹窗形式进行数据错误提示！")

  ;初始化，必须先做的
  (initializedata)
  ;数据预判断
  (setq logsystem T);log日志系统开

  ;(prejudge)
 
  ;图形绘制
  (setq detail T)
  (maindraw)
  ;插入技术条件
  ;(setq topfltype "2.xMW_TopFlange")
  ;(TechReq_insert Pt_Tr En_2.x_TechReq_detail  2_En_2.x_TechReq_detail 3.5 "en" topfltype)
  ;(setq Pt_Tr2 (polar Pt_Tr 0 25800 ))
  ;(TechReq_insert Pt_Tr2 1_2.x_TechReq_detail  2_2.x_TechReq_detail 5.0 "ch" topfltype)
)
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


;;;;;;总图出图;;;;;;
(defun towerdraw3(/ )
  (vl-load-com)
  (setvar "cmdecho" 0)
  (setvar "osmode" 0);不扑捉任何类型
  ;(setq mscale (GetScale));获取标题栏比例
  (setq mscale 100);获取标题栏比例
  (setvar "ltscale" (/ mscale 2));改变线型比例因子大小
  (setq Towerdat_file (GetTowerdat_file));选择塔架数据表
  ;选择塔架设计辅助表
  (setq Towernum_file (GetTowerdat_file2))
  (defaultfile Towerdat_file);把上次打开的excel路径写入到默认路径中
  (setq pa (getpoint "\n选择塔底中心点："))
  (princ pa)
  ;是否弹窗
  (setq is_alert T)
  ;初始化，必须先做的
  (initializedata)
  ;数据预判断
  ;(prejudge)
  ;得到设计辅助表的内容
  (GetAidedDesignData Towernum_file)
  ;(print retAidlist)
  ;(print (nth 0 retAidlist))
  (setq zongtu T)
  ;图形绘制
  (maindraw)
)
;;;;End towerdraw3;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defun maindraw (/ i k hh p_top towreoript bid_xhkeyptlist);
	(setvar "dimzin" 8) ;消零，舍弃无效的尾零
	;初始化，初始化一些全局变量
	
	;;;;;;对主体材料进行判断分类
	(setq i 0)
	(setq collect_num 0)
	(while (< i towernum)
		(if (and (/= (value retTower i 5) "")(/= (value retTower i 5) nil));(and (/= (value retTower i 9) "") (/= (value retTower i 9) nil)));如果不为空
			(progn
				(if (=(value retTower i 5) "Q420");(=(atof (value retTower i 9)) "Q420"))
					(setq collect_num (+ collect_num 1))
				)
			)
		)
		(setq i (+ i 1))
	)
	(if (/= collect_num 0)
		(progn
			(if (= collect_num towernum)
				(setq materile_style 1);0指Q355，1指Q420，2指Q355和420
				(setq materile_style 2);0指Q355，1指Q420，2指Q355和420
			)
		)
		(setq materile_style 0);0指Q355，1指Q420，2指Q355和420
	)
	;1、主体绘制
	(if midornot   ;;;中对齐和外对齐的主体绘制
		(progn
			(Body_insert_ali)
			(keypt)
			(Body_insert_ali_w)
		)
		(Body_insert)
	)

	;2、绘制法兰放大图
	;所有法兰的重量
	(setq FlangeMassList (AllFlangeMass))
	
	(Flange_insert)
	;3、绘制门洞
	(if (ConcreOrNot)
		(progn
			(setq MassOfDoor 0.0)
			(setq MassOfTowerDoorHole 0.0)
		);prong
		(Door_insert)
	);if
	;4、基础环绘制
	(Embedded_insert)
	;5、混塔-混段绘制
	(Concrete_insert)
	;爬梯插入
	(setq towreoript (nth 0 sthptlist))
	(setq stairsname (stairsjudge));梯子名称
	(Entrance_stairs_insert towreoript stairsname);插入的原点
	;求每个筒段的重量
	(setq SectionMassList (secmass));
	;(print SectionMassList)
  
	;求塔架门外梯重量
	(setq MassOfStairs (Entrance_stairs_mass stairsname))
	;求混塔门外梯的重量
	(setq MassOfConcreteStairs (hybridTower_asm_mass))
	;插入附件（包括基础）
	(cond   
		(onekeydetail ;如果是一键总图
			(progn 
			   ;主体上的序号标注
			   (setq xhkeyptlist (zt_xhKeypt));一键总图拉序号的点,主体上
			   (zt_acc_block_ins);一键总图的附件块插入
			   (xh_insert xhkeyptlist);主体图上序号标注
			   ;法兰放大图螺栓的序号插入
			   (setq xh_bolt_list (bolt_judge));得到螺栓的序号
			   (setq bolt_zb_list bolt_zb_list);得到螺栓的标注坐标
			   (zt_boltnutwasher_xh_lead bolt_zb_list xh_bolt_list);法兰放大图螺栓序号标注
			   ;插入明细表
			   (BomList3 Towerdat_file SectionMassList FlangeMassList MassOfStairs MassOfDoor MassOfTowerDoorHole);一键详图
			   ;插入技术条件
			   (zt_tech_insert)
			);end progn
		)
		(towerdraw_detail ;如果是一键手工出总图;;;曹学敏新增
			(progn 
			   ;主体上的序号标注
			   (setq xhkeyptlist (yjzt_xhKeypt));总图主体上拉序号的点
			   (yjzt_asm_block_ins);总图附件块插入
			   (yjztxh_insert xhkeyptlist);总图主体上序号标注
			   ; ;法兰放大图螺栓的序号插入
			   (setq xh_bolt_list (yjbolt_judge));得到螺栓的序号
			   (setq bolt_zb_list bolt_zb_list);得到螺栓的标注坐标
			   (yjzt_boltnutwasher_xh_lead bolt_zb_list xh_bolt_list);法兰放大图螺栓序号标注
			   ;插入明细表
			   (setq Sum_FLange section_qty) 

			   (BomList1 Towerdat_file SectionMassList FlangeMassList MassOfStairs Sum_FLange MassOfDoor MassOfTowerDoorHole)
				;一键总图

			   ;插入技术条件
			   (if midornot
					(progn  (tech_insert_mid) (DiamNoticeInsert) )
					(tech_insert)
			   );end if
			);end progn
		)
		(oneclickbid_auto ;一键招标图，带界面
			(progn 
			   (accessories_insert) ;招标图的附件块插入
			   (setq Sum_FLange section_qty) ;招标图明细表生成，生成txt
			   (BomList4 Towerdat_file SectionMassList FlangeMassList MassOfStairs MassOfDoor MassOfTowerDoorHole) ;插入明细表
			   (setq bid_xhkeyptlist (xhKeypt));招标图拉序号的点,主体上
			   (xh_insert bid_xhkeyptlist);主体图上序号标注
			   ;插入技术条件
			   (if midornot
					(progn  (tech_insert_mid) (NoticeInsert) )
					(tech_insert)
			   );end if
			);end progn
		)
		(detail
			(progn 
			   (accessories_insert) ;招标图的附件块插入
			   (setq Sum_FLange section_qty) ;招标图明细表生成，生成txt
			   (BomList_detail Towerdat_file SectionMassList FlangeMassList MassOfStairs Sum_FLange MassOfDoor MassOfTowerDoorHole) ;插入明细表
			   ;插入技术条件
			   (if midornot  
					(progn  (tech_insert_mid) (NoticeInsert) )
					(tech_insert)
			   );end if
			);end progn
		)
		(t  ;一键招标图-定制化用的
			(progn 
				(if (and (or(= topfltype "V12_TopFlange")(= topfltype "V12_TopFlange_250")) (= TheDoorType "ConcDoor"))
					(progn  
						(Hybrid_accessories_insert) ;招标图的附件块插入-V12混塔   
						(setq bid_xhkeyptlist (HybridTower_xhKeypt));标图拉序号的点,主体上-V12混塔招
						(hybrid_xh_insert bid_xhkeyptlist);主体图上序号标注
					);progn 
					(progn 
						(accessories_insert) ;招标图的附件块插入
						(setq bid_xhkeyptlist (xhKeypt));招标图拉序号的点,主体上
						(xh_insert bid_xhkeyptlist);主体图上序号标注
					);progn
				);end if 
				;明细表生成-txt格式
				(setq Sum_FLange section_qty) 
				(BomList Towerdat_file SectionMassList FlangeMassList MassOfStairs MassOfConcreteStairs Sum_FLange MassOfDoor MassOfTowerDoorHole) 
				;插入技术条件
				(if midornot
					(progn  (tech_insert_mid) (NoticeInsert) (DiamNoticeInsert) )
					(tech_insert)
				);end if
			);end progn
		)
	);end cond
      
  ; (if onekeydetail
    ; (progn;如果是一键总图
      ; ; 主体上的序号标注
      ; (setq xhkeyptlist (zt_xhKeypt));一键总图拉序号的点,主体上
      ; (zt_acc_block_ins);一键总图的附件块插入
      ; (xh_insert xhkeyptlist);主体图上序号标注
      ; ; 法兰放大图螺栓的序号插入
      ; (setq xh_bolt_list (bolt_judge));得到螺栓的序号
      ; (setq bolt_zb_list bolt_zb_list);得到螺栓的标注坐标
      ; (zt_boltnutwasher_xh_lead bolt_zb_list xh_bolt_list);法兰放大图螺栓序号标注
      ; ; 插入明细表
      ; (BomList3 Towerdat_file SectionMassList FlangeMassList MassOfStairs MassOfDoor MassOfTowerDoorHole);一键详图
      ; ; 插入技术条件
      ; (zt_tech_insert)
    ; );end progn
    ; (progn;招标图
      ; ;招标图的附件块插入
      ; (accessories_insert)
      ; ;招标图明细表生成，生成txt
      ; (setq Sum_FLange section_qty)
      ; (if oneclickbid_auto
        ; (progn;一键招标图，带界面
	  ; (BomList4 Towerdat_file SectionMassList FlangeMassList MassOfStairs MassOfDoor MassOfTowerDoorHole)
	  ; (setq bid_xhkeyptlist (xhKeypt));招标图拉序号的点,主体上
	  ; (xh_insert bid_xhkeyptlist);主体图上序号标注
	; );end progn
	; (progn
          ; (if detail
            ; (BomList_detail Towerdat_file SectionMassList FlangeMassList MassOfStairs Sum_FLange MassOfDoor MassOfTowerDoorHole)
            ; (BomList Towerdat_file SectionMassList FlangeMassList MassOfStairs Sum_FLange MassOfDoor MassOfTowerDoorHole)
          ; );end if
	; );end progn
      ; );end if

      
      ; ;插入技术条件
      ; (if midornot
	    ; (progn  (tech_insert_mid) (NoticeInsert) )
	    ; (tech_insert)
      ; );end if
    ; );END PROGN
  ; );End if
  ;;;;;;;;;;

  ;明细表
  ;(if zongtu
  ;  (BomList2 Towerdat_file SectionMassList FlangeMassList MassOfStairs Sum_FLange MassOfDoor MassOfTowerDoorHole)
  ;  (BomList Towerdat_file SectionMassList FlangeMassList MassOfStairs Sum_FLange MassOfDoor MassOfTowerDoorHole)
  ;)
  ;(if onekeydetail
  ;  (BomList3 Towerdat_file SectionMassList FlangeMassList MassOfStairs Sum_FLange MassOfDoor MassOfTowerDoorHole);一键详图
  ;  (BomList Towerdat_file SectionMassList FlangeMassList MassOfStairs Sum_FLange MassOfDoor MassOfTowerDoorHole)
  ;)

  
  ;图纸绘制完成提示
  (if midornot (zdqzthjfh_insert) )
  (Endalert)
  (release_cache)
);end maindraw
;;;;;;

;释放全局变量缓存
(defun release_cache()
  (setq sthptlist nil);标高中心点列表,全局变量
  (setq sthptllist nil);标高左点列表,全局变量
  (setq sthptrlist nil);标高右点列表,全局变量
  (setq shellthicklist nil);筒节壁厚列表,全局变量
  (setq sthptllist_upper nil);全局变量
  (setq sthptrlist_upper nil);全局变量
       
)

;;;;总图中法兰放大图螺栓螺母的标注
(defun zt_boltnutwasher_xh_lead(zb xh_bolt_list / jj pt_fl bolt_code nut_code washer_code)
  (setq jj 0)
  (while (< jj (- section_qty 1))
    (setq pt_fl (nth jj zb))
    (setq bolt_code (nth (+ (* jj 3) 0) xh_bolt_list))
    (setq nut_code (nth (+ (* jj 3) 1) xh_bolt_list))
    (setq washer_code (nth (+ (* jj 3) 2) xh_bolt_list))
    (bolt_xhlead bolt_code nut_code washer_code pt_fl 3000 (/ pi 3))
    (setq jj (+ jj 1))
  )
);End zt_boltnutwasher_xh_lead


;;;;;;中对齐主体焊接符号插入
(defun zdqzthjfh_insert(/ npt1)
	(cond
		((= mscale 100)
		  (setq npt1 '(52000 28000 0));说明的插入原点
		  ;(command "insert" "zdqzthjfh" "S" 1 npt1 "")
		)
		((= mscale 120)
		  (setq npt1 '(63500 30000 0));说明的插入原点
		  ;(command "insert" "zdqzthjfh" "S" 1 npt1 "")
		)
		((= mscale 140)
		  (setq npt1 '(98900 160000 0));说明的插入原点
		  ;(command "insert" "zdqzthjfh" "S" 2 npt1 "")
		)
		(t
		  (setq npt1 '(52000 28000 0));说明的插入原点
		  ;(command "insert" "zdqzthjfh" "S" 1 npt1 "")
		)
	);cond

  ;插入的位置
	(cond
		((or (= flange_qty 2) (= flange_qty 3))
		  (setq npt1 (nth 8 enkeyptlist))
		)
		((or (= flange_qty 4) (= flange_qty 5) (= flange_qty 6))
		  (setq npt1 (nth 8 enkeyptlist))
		)
		((or (= flange_qty 7) (= flange_qty 8) (= flange_qty 9))
		  (setq npt1 (nth 11 enkeyptlist))
		)
		(t
		 (setq npt1 (nth 12 enkeyptlist))
		)
	);cond
  (if towerdraw_detail (setq npt1 (polar npt1 pi (* 18000 (/ mscale 140.0) ))))

  ;原位置
  ;(if (= mscale 100)
  ;  (progn
  ;    (setq npt1 '(52000 28000 0));说明的插入原点
  ;  );end progn
  ;  (if (= mscale 120)
  ;    (progn
  ;	(setq npt1 '(63500 30000 0));说明的插入原点
  ;    );end progn
  ;    (progn
  ;	(setq npt1 '(75000 32000 0));说明的插入原点cf
  ;    );end progn
  ;  );end if
  ;);end if
  (command "insert" "zdqzthjfh" "S" (/ mscale 100) npt1 "")
);end zdqzthjfh_insert

;;;;;;技术条件
(defun tech_insert(/ npt1 oript oripten oriptznq oriptznqen  towerheight)
	(if (= mscale 100)
		(progn
			(setq npt1 '(45200 10200 0));说明的插入原点
			(setq oripten '(80600  57530));英文技术条件插入的原点
			(setq oript '(80600  34500));英文技术条件插入的原点
		)
		(if (= mscale 120)
			(progn
				(setq npt1 '(57800 10200 0));说明的插入原点
				(setq oripten '(96000 43000))
				(setq oript (polar oripten (* pi 1.5) 30700 ));中文技术条件的位置
			);progn
			(progn
				(setq npt1 '(70200 10200 0));说明的插入原点
				(setq oripten '( 108000 80000))
				(setq oript (polar oripten (* pi 1.5) 30700 ));中文技术条件的位置
			);progn
		);if
	);if
	(setq towerheight (- (cadr (bnth -1 sthptlist)) (cadr (nth 0 sthptlist)) ) )
	;(setq oript (polar oripten (* pi 1.5) 30700 ));中文技术条件的位置

	(if (or (= topfltype "2MW_TopFlange") (= topfltype "2.xMW_TopFlange") (= topfltype "2MW-20_TopFlange") (= topfltype "21_TopFlange") )
		(progn	;如果是2.X系列
			(if (> towerheight 125000);如果高于125米
				(progn;插入阻尼器技术要求
					(setq oriptznq (polar oript pi 194));阻尼器的插入位置
					(setq oriptznqen (polar oripten 0 1465))
					(if (= topfltype "21_TopFlange")
						(progn	;21号阻尼器
							(command "insert" "21TRZNQ" "S" 1 oriptznq "")
							(command "insert" "21TRZNQEN" "S" 1 oriptznqen "")
						);end progn
						(progn
							(command "insert" "2STRZNQ" "S" 1 oriptznq "")
							(command "insert" "2STRZNQEN" "S" 1 oriptznqen "")
						);end progn
					);end if
				);end progn
				(progn
					(command "insert" "2STR" "S" 1 oript "");插入中文技术条件
					(command "insert" "2STRE" "S" 1 oripten "");插入英文技术条件
				);END PROGN
			);end if
			(insert_notice npt1);插入说明
		);end progn
		(progn;如果是其他机型
			(if (> towerheight 125000)
				(progn;插入阻尼器技术要求
					(setq oriptznq (polar oript pi 3000));阻尼器的插入位置
					(setq oriptznqen (polar oripten pi 2000))
					(command "insert" "3STRZNQ" "S" 1 oriptznq "")
					(command "insert" "3STRZNQEN" "S" 1 oriptznqen "")
				);end progn
				(progn
					(command "insert" "3STR" "S" 1 oript "");
					(command "insert" "3STRE" "S" 1 oripten "")
				);progn
			);end if
			(insert_notice npt1);插入说明
		);end progn
	);end if
	(print "技术条件绘制成功！")
)
;;;;END tech_insert


(defun NoticeInsert(/ oript)
    (cond
    ((= mscale 100)
      (setq oript '(9600 3500 0));
    )
    ((= mscale 120)
      (setq oript '(11700 5580 0));
    )
    ((= mscale 140)
      (setq oript '(13540 5950 0));
    )
    (t
      (setq oript '(2000 3000 0));
    )
  );cond
  (MtextInsert oript 7 NoticeofBid) 

  (print "说明绘制成功！")
)
;defun

(defun DiamNoticeInsert(/ oript)
    (cond
     ((= mscale 100)
      (setq oript '(29600 3500 0));
    )
    ((= mscale 120)
      (setq oript '(31700 5580 0));
    )
    ((= mscale 140)
      (setq oript '(33540 5950 0));
    )
    (t
      (setq oript '(22000 3000 0));
    )
  );cond
  (MtextInsert oript 7 NoticeofDia_1) 
  (MtextInsert oript 5 NoticeofDia_2) 

  (print "说明绘制成功！")
);defun

;;;;;;中间对齐技术条件
(defun tech_insert_mid( / npt1 oript oripten oriptznq oriptznqen  towerheight)
	(cond
		((= mscale 100)
		  (setq oript '(22100 36000 0));
		  (setq oripten (polar oript 0 20400 ))
		)
		((= mscale 120)
		  (setq oript '(26100 45600 0));说明的插入原点
		  (setq oripten (polar oript 0 25900 ))
		)
		((= mscale 140)
		  (setq oript '(29600 45600 0));
		  (setq oripten (polar oript 0 29800 ))
		)
		(t
		  (setq pa '(52000 25000 0));说明的插入原点
		)
	);cond

	(setq towerheight (- (cadr (bnth -1 sthptlist)) (cadr (nth 0 sthptlist)) ) )
	;根据机型插入不同的技术条件
  
	;插入技术条件标题
	(MtextInsert oript 7.0 TechReqTitle) 
	(MtextInsert oripten 5.0 En_TechReqTitle) 
	(cond
	  ;2S&21#技术条件
		((or (= topfltype "2MW_TopFlange") (= topfltype "2.xMW_TopFlange") (= topfltype "2MW-20_TopFlange") (= topfltype "21_TopFlange") )
		  (if (> towerheight 125000);如果高于125米
		(progn;插入阻尼器技术要求
			  (if (= topfltype "21_TopFlange")
			(progn;21号阻尼器
			  (TechReq_insert oript 1_2.x_TechReq_detail  znq_2_21_TechReq_detail 5.0 "ch" topfltype)
			  (TechReq_insert oripten En_2.x_TechReq_detail  znq_2_En_21_TechReq_detail 3.5 "en" topfltype)
			);end progn
			(progn;
			  ;(print oript)
			  (TechReq_insert oript 1_2.x_TechReq_detail  znq_2_2.x_TechReq_detail 5.0 "ch" topfltype)
			  (TechReq_insert oripten En_2.x_TechReq_detail  znq_2_En_2.x_TechReq_detail 3.5 "en" topfltype)
			);end progn
		  );end if
		);end progn
			(progn
		  (TechReq_insert oript 1_2.x_TechReq_detail  2_2.x_TechReq_detail 5.0 "ch" topfltype)
		  (TechReq_insert oripten En_2.x_TechReq_detail  2_En_2.x_TechReq_detail 3.5 "en" topfltype)
			);end progn
		  );end if
		)
		
		;V12分片塔技术条件
		((and (or (= topfltype "5S_TopFlange") (= topfltype "5X_TopFlange") (= topfltype "V12_TopFlange")(= topfltype "V12_TopFlange_250")) (= TheTowerSliceType "SliceTower"))
		  (if (> towerheight 125000);如果高于125米
		(progn;插入阻尼器技术要求
		  (TechReq_insert oript 1_V12_Slice_TechReq_detail  znq_2_V12_Slice_TechReq_detail 5.0 "ch" topfltype)
		  (TechReq_insert oripten En_V12_Slice_TechReq_detail  znq_2_En_V12_Slice_TechReq_detail 3.5 "en" topfltype)
		);end progn
			(progn
		  (TechReq_insert oript 1_V12_Slice_TechReq_detail  2_V12_Slice_TechReq_detail 5.0 "ch" topfltype)
		  (TechReq_insert oripten En_V12_Slice_TechReq_detail  2_En_V12_Slice_TechReq_detail 3.5 "en" topfltype)
			);end progn
		  );end if
		)
		
		;5S,5H,V12技术条件
		((and(or (= topfltype "5S_TopFlange") (= topfltype "5X_TopFlange") (= topfltype "V12_TopFlange")(= topfltype "V12_TopFlange_250")) (= TheTowerSliceType "NomalTower"))
		  (if (> towerheight 125000);如果高于125米
		(progn;插入阻尼器技术要求
		  (TechReq_insert oript 1_5S_TechReq_detail  znq_2_5S_TechReq_detail 5.0 "ch" topfltype)
		  (TechReq_insert oripten En_5S_TechReq_detail  znq_2_En_5S_TechReq_detail 3.5 "en" topfltype)
		);end progn
			(progn
		  (TechReq_insert oript 1_5S_TechReq_detail  2_5S_TechReq_detail 5.0 "ch" topfltype)
		  (TechReq_insert oripten En_5S_TechReq_detail  2_En_5S_TechReq_detail 3.5 "en" topfltype)
			);end progn
		  );end if
		)
		
		(t
		  (if (> towerheight 125000)
		(progn;插入阻尼器技术要求,柔塔
		  (TechReq_insert oript 1_3S_TechReq_detail  znq_2_3S_TechReq_detail 5.0 "ch" topfltype)
		  (TechReq_insert oripten En_3S_TechReq_detail  znq_2_En_3S_TechReq_detail 3.5 "en" topfltype)
		);end progn
			(progn
		  (TechReq_insert oript 1_3S_TechReq_detail  2_3S_TechReq_detail 5.0 "ch" topfltype)
		  (TechReq_insert oripten En_3S_TechReq_detail  2_En_3S_TechReq_detail 3.5 "en" topfltype)
		);end progn
		  );end if
		)
	);end cond
	; (cond
	  ; ;2S&21#技术条件
		; ((or (= topfltype "2MW_TopFlange") (= topfltype "2.xMW_TopFlange") (= topfltype "2MW-20_TopFlange") (= topfltype "21_TopFlange") )
		  ; (if (> towerheight 125000);如果高于125米
		; (progn;插入阻尼器技术要求
			  ; (if (= topfltype "21_TopFlange")
			; (progn;21号阻尼器
			  ; (TechReq_insert oript 1_2.x_TechReq_detail  znq_2_21_TechReq_detail 5.0 "ch" topfltype)
			  ; (TechReq_insert oripten En_2.x_TechReq_detail  znq_2_En_21_TechReq_detail 3.5 "en" topfltype)
			; );end progn
			; (progn;
			  ; ;(print oript)
			  ; (TechReq_insert oript 1_2.x_TechReq_detail  znq_2_2.x_TechReq_detail 5.0 "ch" topfltype)
			  ; (TechReq_insert oripten En_2.x_TechReq_detail  znq_2_En_2.x_TechReq_detail 3.5 "en" topfltype)
			; );end progn
		  ; );end if
		; );end progn
			; (progn
		  ; (TechReq_insert oript 1_2.x_TechReq_detail  2_2.x_TechReq_detail 5.0 "ch" topfltype)
		  ; (TechReq_insert oripten En_2.x_TechReq_detail  2_En_2.x_TechReq_detail 3.5 "en" topfltype)
			; );end progn
		  ; );end if
		; )
		
		; ;V12分片塔技术条件
		; ((and (or (= topfltype "5S_TopFlange") (= topfltype "5X_TopFlange") (= topfltype "V12_TopFlange")(= topfltype "V12_TopFlange_250")) (= TheTowerSliceType "SliceTower"))
		  ; (if (> towerheight 125000);如果高于125米
		; (progn;插入阻尼器技术要求
		  ; (TechReq_insert oript 1_V12_Slice_TechReq_detail  znq_2_V12_Slice_TechReq_detail 5.0 "ch" topfltype)
		  ; (TechReq_insert oripten En_V12_Slice_TechReq_detail  znq_2_En_V12_Slice_TechReq_detail 3.5 "en" topfltype)
		; );end progn
			; (progn
		  ; (TechReq_insert oript 1_V12_Slice_TechReq_detail  2_V12_Slice_TechReq_detail 5.0 "ch" topfltype)
		  ; (TechReq_insert oripten En_V12_Slice_TechReq_detail  2_En_V12_Slice_TechReq_detail 3.5 "en" topfltype)
			; );end progn
		  ; );end if
		; )
		
		; ;5S,5H,V12技术条件
		; ((and(or (= topfltype "5S_TopFlange") (= topfltype "5X_TopFlange") (= topfltype "V12_TopFlange")(= topfltype "V12_TopFlange_250")) (= TheTowerSliceType "NomalTower"))
		  ; (if (> towerheight 125000);如果高于125米
		; (progn;插入阻尼器技术要求
		  ; (TechReq_insert oript 1_5S_TechReq_detail  znq_2_5S_TechReq_detail 5.0 "ch" topfltype)
		  ; (TechReq_insert oripten En_5S_TechReq_detail  znq_2_En_5S_TechReq_detail 3.5 "en" topfltype)
		; );end progn
			; (progn
		  ; (TechReq_insert oript 1_5S_TechReq_detail  2_5S_TechReq_detail 5.0 "ch" topfltype)
		  ; (TechReq_insert oripten En_5S_TechReq_detail  2_En_5S_TechReq_detail 3.5 "en" topfltype)
			; );end progn
		  ; );end if
		; )
		
		; (t
		  ; (if (> towerheight 125000)
		; (progn;插入阻尼器技术要求,柔塔
		  ; (TechReq_insert oript 1_3S_TechReq_detail  znq_2_3S_TechReq_detail 5.0 "ch" topfltype)
		  ; (TechReq_insert oripten En_3S_TechReq_detail  znq_2_En_3S_TechReq_detail 3.5 "en" topfltype)
		; );end progn
			; (progn
		  ; (TechReq_insert oript 1_3S_TechReq_detail  2_3S_TechReq_detail 5.0 "ch" topfltype)
		  ; (TechReq_insert oripten En_3S_TechReq_detail  2_En_3S_TechReq_detail 3.5 "en" topfltype)
		; );end progn
		  ; );end if
		; )
	; );end cond

  
  ;(if (or (= topfltype "2MW_TopFlange") (= topfltype "2.xMW_TopFlange") (= topfltype "2MW-20_TopFlange") (= topfltype "21_TopFlange") (= topfltype "5S_TopFlange"))
  ;  (progn;如果是2.X系列,21#
  ;    (if (> towerheight 125000);如果高于125米
;	(progn;插入阻尼器技术要求
 ;         (if (= topfltype "21_TopFlange")
;	    (progn;21号阻尼器
;	      (TechReq_insert oript 1_2.x_TechReq_detail  znq_2_21_TechReq_detail 5.0 "ch" topfltype)
;	      (TechReq_insert oripten En_2.x_TechReq_detail  znq_2_En_21_TechReq_detail 3.5 "en" topfltype)
;	    );end progn
;	    (progn;
;	      ;(print oript)
;	      (TechReq_insert oript 1_2.x_TechReq_detail  znq_2_2.x_TechReq_detail 5.0 "ch" topfltype)
;	      (TechReq_insert oripten En_2.x_TechReq_detail  znq_2_En_2.x_TechReq_detail 3.5 "en" topfltype)
;	    );end progn
;	  );end if
;	);end progn
 ;       (progn
;	  (TechReq_insert oript 1_2.x_TechReq_detail  2_2.x_TechReq_detail 5.0 "ch" topfltype)
;	  (TechReq_insert oripten En_2.x_TechReq_detail  2_En_2.x_TechReq_detail 3.5 "en" topfltype)
 ;       );end progn
  ;    );end if
;
 ;   );end progn
  ;  (progn
   ;   (if (> towerheight 125000)
;	(progn;插入阻尼器技术要求,柔塔
;	  (TechReq_insert oript 1_3S_TechReq_detail  znq_2_3S_TechReq_detail 5.0 "ch" topfltype)
;	  (TechReq_insert oripten En_3S_TechReq_detail  znq_2_En_3S_TechReq_detail 3.5 "en" topfltype)
;	);end progn
 ;       (progn
;	  (TechReq_insert oript 1_3S_TechReq_detail  2_3S_TechReq_detail 5.0 "ch" topfltype)
;	  (TechReq_insert oripten En_3S_TechReq_detail  2_En_3S_TechReq_detail 3.5 "en" topfltype)
;	);end progn
 ;     );end if
  ;    ;(insert_notice npt1 );插入说明
   ; );end progn
 ; )  
  (print "技术条件绘制成功！")
)
;;;;END tech_insert


;1_2.x_TechReq_detail
;2_2.x_TechReq_detail
;znq_2_2.x_TechReq_detail

;znq_2_21_TechReq_detail

;En_2.x_TechReq_detail
;2_En_2.x_TechReq_detail
;znq_2_En_2.x_TechReq_detail

;znq_2_En_21_TechReq_detail

;1_3S_TechReq_detail
;2_3S_TechReq_detail
;znq_2_3S_TechReq_detail

;En_3S_TechReq_detail
;2_En_3S_TechReq_detail
;znq_2_En_3S_TechReq_detail

;21and2.xtable
;EN21and2.xtable
;3Stable
;EN3Stable

;;;;;;总图技术条件
(defun zt_tech_insert(/ npt1 oript oripten oriptznq oriptznqen  towerheight)
  (if (= mscale 100)
    (progn
      (setq npt1 '(45200 10200 0));说明的插入原点
      (setq oripten '(79700  67500));英文技术条件插入的原点
    )
    (if (= mscale 120)
      (progn
	(setq npt1 '(57800 10200 0));说明的插入原点
        (setq oripten '(96000 43000))
      )
      (progn
	(setq npt1 '(70200 10200 0));说明的插入原点
        (setq oripten '( 108000 110000))
      )
    )
  )
  
  (setq towerheight (- (cadr (bnth -1 sthptlist)) (cadr (nth 0 sthptlist)) ) )
  (setq oript (polar oripten (* pi 1.5) 34700 ));技术条件的位置

  (if (or (= topfltype "2MW_TopFlange") (= topfltype "2.xMW_TopFlange") (= topfltype "2MW-20_TopFlange"))
    (progn;如果是2.X系列
      (command "insert" "2STR" "S" 1 oript "");插入中文技术条件
      (command "insert" "2STRE" "S" 1 oripten "");插入英文技术条件
      ;(insert_notice npt1 );插入说明
      (if (> towerheight 125000);如果高于125米
	(progn;插入阻尼器技术要求
	  (setq oriptznq (polar oript pi 194));阻尼器的插入位置
	  (setq oriptznqen (polar oripten 0 1465))
	  (command "insert" "2STRZNQ" "S" 1 oriptznq "")
	  (command "insert" "2STRZNQEN" "S" 1 oriptznqen "")
	)
      )      
    )
    (progn
      (command "insert" "3STR" "S" 1 oript "");
      (command "insert" "3STRE" "S" 1 oripten "")
      ;(insert_notice npt1 );插入说明
      (if (> towerheight 125000)
	(progn;插入阻尼器技术要求
	  (setq oriptznq (polar oript pi 3000));阻尼器的插入位置
	  (setq oriptznqen (polar oripten pi 2000))
	  (command "insert" "3STRZNQ" "S" 1 oriptznq "")
	  (command "insert" "3STRZNQEN" "S" 1 oriptznqen "")
	)
      )
    )
  )  
  (print "技术条件绘制成功！")
)
;;;;END tech_insert


(defun insert_notice (oript / actualdate deadline deadlinestr deadlineyear)
  (setq oript2 (polar oript 0 6300))
  (setq oript2 (polar oript2 (/ pi 2) 1000)) 
  ;(setq actualdate (getvar "cdate"));当下时间
  ;(setq deadline (+ actualdate 199));两个月的时间
  
  ;(setq deadlinestr (rtos deadline 2 0));连个月后的日期

  ;(setq dyear (substr deadlinestr 1 4));年份
  ;(setq dmonth (substr deadlinestr 5 6));月份
  ;(setq dmonth (substr dmonth 1 2));月份
  ;(setq dday (substr deadlinestr 7 8));日
  ;(if (or (= topfltype "2MW_TopFlange") (= topfltype "2.xMW_TopFlange") (= topfltype "2MW-20_TopFlange"))
  ;  (setq dnotice (strcat "此图仅供招标，不得用于生产，如需用于备料请申请确认，有效期至" dyear "年" dmonth "月" dday "日。"));2.XMW系列
  ;  (progn
  ;    (setq oript2 (polar oript2 pi 2500)) 
  ;    (setq dnotice "此图仅供招标，不得用于生产，如需用于备料请申请确认。")
  ;  )
  ;)
  (setq oript2 (polar oript2 pi 2500)) 
  (setq dnotice "此图仅供招标，经技术确认后方可备料，不得用于生产。")
  (setq dnotice2 "说明")
  (setq oript (vlax-3d-point oript))
  (setq oript2 (vlax-3d-point oript2))
  (setq insertn (vla-AddText myms dnotice oript 600 ) );插入有效期限
  (setq insertn2 (vla-AddText myms dnotice2 oript2 600 ) );插入有效期限
  (vla-put-layer insertn "6文字层")
  (vla-put-layer insertn2 "6文字层")
  ;(vlax-dump-object insertn t)

)


;;;一些全局变量函数的初始化;;;;;;;;;;;;;;;;;
(defun initializedata( / boltdat_file)
  ;主体表格中的数据初始化
  (print "开始读取塔架主体信息表格！")
  (GetTowerGeoData Towerdat_file)  ;得到 retTower、retFlange、retDoor,retEmbedded
  (print "成功读取塔架主体信息表格！")
  ;塔架对齐形式
  (midjudge)
  ;(+ 1 "");打断
  ;螺栓数据初始化
  (setq boltdat_file "D:\\Program Files (x86)\\Autodesk\\AutoLisp\\Data.xlsx")
  (GetExcelData boltdat_file);得到retBoltDraw，retBolt，retNut，retWasher
  (print "成功读取紧固件信息表格！")
  ;图纸初始化，visul lisp代码使用
  (setq myacad (vlax-get-acad-object))
  (setq mydoc (vla-get-ActiveDocument myacad))
  (setq myms (vla-get-ModelSpace mydoc));my models
  ;函数中使用的全局变量初始化
  (flSecHqty);执行这个函数从而得到法兰的数量,这个第1执行
  (print (strcat "此项目段数：" (rtos section_qty 2 0)))
  (print (strcat "此项目法兰数："  (rtos flange_qty 2 0)))
  (flangepos);执行这个函数从而得到法兰在主体中的位置，这个第2执行
  ;顶法兰类型，确定机型
  (setq topfltype (topfljudge))
  (print (strcat "此项目顶法兰类型：" topfltype))
  (setq bflange_hole (atoi (rtos (value retFlange 0 8))));塔架底法兰螺栓孔直径
  ;机型的功率
  (if (and (/= (value retDescription 0 1) nil) (/= (value retDescription 0 1) ""))
	    (setq Power (value retDescription 0 1))
		(setq Power (atof(getstring "请输入功率(MW)：")))  
   );end if
  (if midornot
    (progn
      (keypt_ali);中对齐项目
    )
    (keypt);外对齐项目
  );end if
  ;(keypt);关键点坐标获取,必须运行，获取一些全局变量
  (print "关键点坐标获取成功！")
  ;(setq enkeyptlist (EnlargeKeypt));法兰放大图的插入点
  (setq enkeyptlist (FlKeypt));法兰放大视图的插入点，新
  ;门洞类型
  (setq TheDoorType (DoorType))
  (print (strcat "此项目门洞类型：" TheDoorType))
  ;V12混塔是否防洪
  (if (and (or(= topfltype "V12_TopFlange")(= topfltype "V12_TopFlange_250")) (= TheDoorType "ConcDoor"))
    (progn (setq AntiFloodType (flood_protection_judge)) ;是否防洪-Yes/No
           (print (strcat "此项目是否有防洪需求：" AntiFloodType))
	);progn
   );end if
  ;塔架分片类型
  (setq TheTowerSliceType (SliceTowerOrNot))
  (print (strcat "此项目塔架分片类型：" TheTowerSliceType))
  (GetrootDir Towerdat_file);获得excel所在的根目录
  (print "Excel表格根目录获取成功！")
  (AllTextOfTechReq)
  (print "数据初始化成功！")
)
;;;;函数结束;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;






;;;;;;程序结束提醒函数
(defun Endalert(/ pt1 pt2)
	  (setq pt1 '(0 -2000 0))
	  (setq pt1 '(0 -2000 0))
	  (setq pt2 '(80600 125000))
	  (command "zoom" "w" pt1 pt2)
	  (command "regen")
	  ;(prompt "\n 恭喜，图纸和明细txt顺利生成！ \n")
	  (prompt "\n恭喜，图纸和明细表生成！\n注意：图纸和明细表仅供参考，请务必仔细检查Excel数据表和图纸!")
	  (if is_alert
		(alert "恭喜，图纸和明细表生成！\n注意：\n图纸和明细表仅供参考，请务必仔细检查Excel数据表和图纸!")
	  )
	  (prin1)
	  ;转移焦点
)
;;;;;;;;End Endalert;;;;;


;;;招标图插入基础
(defun accessories_insert(/ scalefactor oript oript2 oript3)
  (setq oript (nth 0 sthptlist))
  (if (and (or (= topfltype "2.xMW_TopFlange") (= topfltype "2MW-20_TopFlange") (= topfltype "2MW_TopFlange")) (=(value retFlange 0 11) "T") (/= (value retdoor 0 12) 5675))
    (command "insert" "2MW_base" "S" 1 oript "")
  );2.0基础
  ;升降机插入
  (setq scalefactor 1.5)
  ;(setq oript2 (MiddlePoint (nth 0 sthptlist) (nth (nth 1 pos) sthptlist)))

  (cond
    ((= topfltype "3MW_S_New_TopFlange")
      (setq oript2 (polar pa (/ pi 2) (+ (DoorHeight) 1600)))
      (setq oript2 (polar oript2 pi (+ 530 (* 500 scalefactor))))
    )
    (t
      (setq oript2 (polar pa (/ pi 2) (+ (DoorHeight) 3300)))
      (setq oript2 (polar oript2 pi (- (* 500 scalefactor) 250)))
    )
  );cond

  
  ;(setq oript2 (polar oript2 pi 500))
  (command "insert" "lift" "S" scalefactor oript2 "");插入升降机块
  ;(print (nth (- section_qty 1) pos))
  ;附件插入
  (setq oript3 (bnth -1 sthptlist))
  (setq oript3 (polar oript3 (* pi 1.5) 8100))
  (setq oript3 (polar oript3 pi 800))
  (command "insert" "Deflection_Roller" "S" 1 oript3 "");插入顶部附件块
)
;;;;;End accessories_insert;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;;招标图插入基础--V12混塔
(defun Hybrid_accessories_insert(/ scalefactor oript oript2 oript3)
  (setq oript (nth 0 sthptlist))
  (if (and (or (= topfltype "2.xMW_TopFlange") (= topfltype "2MW-20_TopFlange") (= topfltype "2MW_TopFlange")) (=(value retFlange 0 11) "T") (/= (value retdoor 0 12) 5675))
    (command "insert" "2MW_base" "S" 1 oript "")
  );2.0基础
  ; ;升降机插入
  ; (setq scalefactor 1.5)
  ; ;(setq oript2 (MiddlePoint (nth 0 sthptlist) (nth (nth 1 pos) sthptlist)))

  ; (cond
    ; ((= topfltype "3MW_S_New_TopFlange")
      ; (setq oript2 (polar pa (/ pi 2) (+ (DoorHeight) 1600)))
      ; (setq oript2 (polar oript2 pi (+ 530 (* 500 scalefactor))))
    ; )
    ; (t
      ; (setq oript2 (polar pa (/ pi 2) (+ (DoorHeight) 3300)))
      ; (setq oript2 (polar oript2 pi (- (* 500 scalefactor) 250)))
    ; )
  ; );cond

  
  ; ;(setq oript2 (polar oript2 pi 500))
  ; (command "insert" "lift" "S" scalefactor oript2 "");插入升降机块
  ;(print (nth (- section_qty 1) pos))
  ;附件插入
  (setq oript3 (bnth -1 sthptlist))
  (setq oript3 (polar oript3 (* pi 1.5) 8100))
  (setq oript3 (polar oript3 pi 800))
  (command "insert" "Deflection_Roller" "S" 1 oript3 "");插入顶部附件块
)
;;;;;End accessories_insert;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;;总图附件块插入
(defun zt_acc_block_ins(/ oript oript_edge oript2 i pt)
  (setq oript (nth 0 sthptlist))
  (if (and (or (= topfltype "2.xMW_TopFlange") (= topfltype "2MW-20_TopFlange") (= topfltype "2MW_TopFlange")) (=(value retFlange 0 11) "T") (/= (value retdoor 0 12) 5675))
    (command "insert" "2MW_base" "S" 1 oript "")
  );2.0基础
  ;围边插入
  (if (/= power "2.5")
    (progn;如果机型不是2.x-2.5，插入围边
      (setq oript_edge  (nth 2 sthptrlist) )
      (command "insert" "edge" "S" 1 oript_edge "");插入平台护边
    )
  )
  ;(setq oript_edge  (nth 2 sthptrlist) )
  ;(command "insert" "edge" "S" 1 oript_edge "");插入平台护边
  ;升降机插入
  (setq oript2 (MiddlePoint (nth 0 sthptlist) (nth (nth 1 pos) sthptlist)))
  (setq oript2 (polar oript2 pi 500))
  (command "insert" "lift" "S" 1.5 oript2 "");插入升降机块
  ;(print (nth (- section_qty 1) pos))
  ;代表附件总成的块插入
  (setq i 0)
  (while (< i section_qty)
    (if (= i 0);第一段
      (setq pt (MiddlePoint (nth (- (nth 1 pos) 3) sthptlist) (nth (nth 1 pos) sthptlist)));第一段
      (setq pt (nth (- (nth (+ i 1) pos) 3) sthptlist) )
    )
    (command "insert" "acc_tower" "S" 1 pt "");插入代表附件总成的块
    (setq i (+ i 1))
  )
  ;附件插入
  ;(setq oript3 (bnth -1 sthptlist))
  ;(setq oript3 (polar oript3 (* pi 1.5) 8100))
  ;(setq oript3 (polar oript3 pi 800))
  ;(command "insert" "Deflection_Roller" "S" 1 oript3 "");插入顶部附件块
)
;;;;;End accessories_insert;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;;;;;;序号标注，主体上的序号插入;;;;;;;;
(defun xh_insert(xhkeyptlist / i)
  (setq i 0)
  (while (< i (-(length xhkeyptlist) 1))
    (xhlead (+ i 1) (nth i xhkeyptlist) 7000.0 (/ pi 12))
    (setq i (+ i 1))
  )
  (xhlead1 (+ i 1) (nth i xhkeyptlist) 8500.0 (/ pi 8))
)
;;;;End xh_insert;;;;;;;

;;;;;;;序号标注，主体上的序号插入---V12混塔;;;;;;;;
(defun hybrid_xh_insert(xhkeyptlist / i)
  (setq i 0)
  (while (< i (-(length xhkeyptlist) 3))
    (xhlead (+ i 1) (nth i xhkeyptlist) 7000.0 (/ pi 12))
    (setq i (+ i 1))
  )
  (while (< i (length xhkeyptlist))
    (xhlead1 (+ i 1) (nth i xhkeyptlist) 8500.0 (/ pi 8))
	(setq i (+ i 1))
  )
)
;;;;End xh_insert;;;;;;;

;;;;;;;;;;;;;;;;外对齐主体相关绘制;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defun Body_insert(/ i k)
	;;;;塔架主体中心线绘制
	(MiddleLine (nth 0 sthptlist) (bnth -1 sthptlist) 100);底法兰中心下点，顶法兰上中心点
	;;;;塔架主体绘制
	;塔架标高插入
	;(elevation (value retTower 0 0));塔架标高
	(setq eleName (elevation (value retTower 0 0)))
	(command "insert" eleName "S" mscale (nth 0 sthptllist) "");插入标高尺寸,放大比例，插入原点
	;(command "-scalelistedit" "R" "Y" "e")
	;主体绘制
	(setq i 0);开始的值
	(setq k section_qty);段数
	;(setq k 0)
	(while (< i k )
		(secdraw i)
		(setq i (+ i 1))
	)
 
	;塔架主体总高度标注
	(ldimv (nth 0 sthptllist) (bnth -1 sthptllist) -5000);塔架总高度
	;主体上法兰序号的绘制
	(enlargesymbol)
	(print "主体绘制成功！")
)
;;;;;;;;Body_insert函数结束;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;*********************************法兰所有放大视图生成**************************************************;;;;;;;;;;;;;;;;;;
(defun Flange_insert(/ Sum_flange flange_index Dout_fl Din_fl Dpcd_fl tfl_fl tn_fl L_fl boltType d_fl num_hole R_fl tempflange_type
		     flange_type BoltClass H_fl Moment tn_top tn_down pt_fl MassOfFlange scale_fl bottomflname)
    (setq Sum_flange (- flange_qty 1));吴航
    (setq flange_index Sum_flange);循环初值,法兰数量，不包括顶法兰
	;(setq flange_index 1)
    (while (and (>= flange_index 0) (> (value retFlange flange_index 2) 0))
		;数据初始化
		(setq Dout_fl (value retFlange flange_index 1));法兰外径
		(setq Din_fl (value retFlange flange_index 2));法兰内径
		(setq Dpcd_fl (value retFlange flange_index 3));螺栓分度圆直径
		(setq tfl_fl (value retFlange flange_index 4));法兰厚度
		(setq tn_fl (value retFlange flange_index 5));法兰颈厚
		(setq L_fl (value retFlange flange_index 6));法兰颈高
		(setq L_fl (neck_h_fl_judge L_fl tn_fl));法兰颈高，过滤一下数据
		;(print L_fl)
		(setq boltType (value retFlange flange_index 7));螺栓公称直径
		(setq d_fl (value retFlange flange_index 8));螺栓孔直径
		(setq num_hole (value retFlange flange_index 9));螺栓数
		(setq R_fl (value retFlange flange_index 10));圆角
		(if (or (= R_fl nil) (= R_fl " "));如果圆角单元格没有内容则为10
			(setq R_fl 10)
		)
		(setq tempflange_type (value retFlange flange_index 11));法兰类型
		(setq flange_type (FlangeTypeJudge tempflange_type ii));判断法兰的类型
		(setq BoltClass (value retFlange flange_index 12));螺栓等级
		(setq H_fl (+ L_fl tfl_fl));法兰高度
		(setq Moment (value retFlange flange_index 15));螺栓预紧力
		;;;;确定法兰上下的筒节壁厚
		(setq tn_top (value retTower (+ (nth flange_index pos ) 1) 4))
		(if (= flange_index 0)
			(setq tn_down 0)
			(setq tn_down (value retTower (- (nth flange_index pos ) 2) 4))
		)
		;法兰放大视图绘制
		(setq pt_fl (nth (- section_qty flange_index) enkeyptlist));放大视图的插入原点
		(setq MassOfFlange (nth flange_index FlangeMassList) )
		(if (= flange_index section_qty)
			(progn;如果是顶法兰，顶法兰执行该程序,判断顶法兰类型
				(setq scale_fl 20);法兰放大系数
				;(setq topflname (topfljudge));判断顶法兰类型
				;(topflinsert pt_fl topflname scale_fl H_fl)
				(topflinsert pt_fl topfltype scale_fl H_fl)
			)
			(if (and (> d_fl 79.0) (= flange_index 0))
				(progn;混凝土法兰
					(setq bottomflname (bottomfljudge));判断底法兰类型	    
					;(otherfl_draw pt_fl bottomflname flange_index scale_fl);如果是螺栓孔大于79的底法兰
					(otherfl_draw pt_fl bottomflname flange_index scale_fl flange_type H_fl Dout_fl Din_fl Dpcd_fl tn_fl d_fl L_fl R_fl num_hole sum_flange boltType BoltClass tn_top tn_down Neck_TopFlange Moment MassOfFlange)
	    		);其他法兰
				(if midornot;
					(flange_draw_mid flange_type pt_fl H_fl Dout_fl Din_fl Dpcd_fl tn_fl d_fl L_fl R_fl num_hole flange_index sum_flange boltType BoltClass tn_top tn_down Neck_TopFlange Moment MassOfFlange);中对齐
					(flange_draw     flange_type pt_fl H_fl Dout_fl Din_fl Dpcd_fl tn_fl d_fl L_fl R_fl num_hole flange_index sum_flange boltType BoltClass tn_top tn_down Neck_TopFlange Moment MassOfFlange) 
				);end if
				;(flange_draw flange_type pt_fl H_fl Dout_fl Din_fl Dpcd_fl tn_fl d_fl L_fl R_fl num_hole flange_index sum_flange boltType BoltClass tn_top tn_down Neck_TopFlange Moment MassOfFlange)
			);end if
		)
		(setq flange_index (1- flange_index))
	);while的右括号
	(print "法兰放大视图绘制成功！")
);********************************法兰所有放大视图生成结束***********************************************


;;;;;;;;;;;;;;;;;;;;;;;;;插入其他类型的底法兰;;;;;;;;;;;
;(defun otherfl_draw(pt_flange bottomflname flange_index scale_fl / pt_middle1);其他的特殊法兰绘制
(defun otherfl_draw( pt_flange bottomflname flange_index scale_fl flange_type H_fl Dout_fl Din_fl Dpcd_fl tn_fl d_fl L_fl R_fl num_hole sum_flange boltType BoltClass tn_top tn_down Neck_TopFlange Moment MassOfFlange
		    / pt_middle1);其他的特殊法兰绘制
	(setq pt_middle1 (polar pt_flange 0 (* 400 (/ mscale scale_fl) 2 )))
	(setq pt_middle1 (polar pt_middle1 (/ pi 2) (* 200 (/ mscale scale_fl) 8 )))
	(cond
		((= bottomflname "ConcreteFlange_120_4500")
		  (FlangeZoomTitle pt_middle1 (- section_qty flange_index) (/ mscale scale_fl)) 
		  (command "insert" bottomflname "S" 1 pt_flange "")
		)
		((= bottomflname "ConcreteFlange_140_4500")
		  (FlangeZoomTitle pt_middle1 (- section_qty flange_index) (/ mscale scale_fl)) 
		  (command "insert" bottomflname "S" 1 pt_flange "")
		)
		((= bottomflname "ConcreteFlange_140_4500_35")
		  (FlangeZoomTitle pt_middle1 (- section_qty flange_index) (/ mscale scale_fl)) 
		  (command "insert" bottomflname "S" 1 pt_flange "")
		)
		((= bottomflname "ConcreteFlange_140_4300")
		  (FlangeZoomTitle pt_middle1 (- section_qty flange_index) (/ mscale scale_fl))   
		  (command "insert" bottomflname "S" 1 pt_flange "")
		)
		((= bottomflname "ConcreteFlange_125")
		  (ConcreteFlDraw flange_type pt_flange H_fl Dout_fl Din_fl Dpcd_fl tn_fl d_fl L_fl R_fl num_hole flange_index sum_flange boltType BoltClass tn_top tn_down Neck_TopFlange Moment MassOfFlange)
		)
		(t
		  (print "底法兰信息有误！")
		)
	);cond
)
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


;;;;;;;;法兰绘制;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defun flange_draw(flange_type pt_flange relH relDout relDin relDpcd reltn reld_hole relL relR num_hole flange_index
	       sum_flange boltType BoltClass reltn_top reltn_down Neck_TopFlange Moment MassOfFlange
		   / scale_fl H Dout Din Dpcd tn d L R middle_line Width Width_pcd_in 
		   flpt00 flpt01 flpt02 flpt04 flpt06 flpt33 flpt31 flpt21 flpt20 flpt16 flpt12 flpt03 flpt05 flpt14 flpt13
		   flpt15 flpt11 flpt10  flpt30 flpt32 flpt51 flpt50 flpt40 flpt-10
		   mi_flpt33 mi_flpt32 mi_flpt30 mi_flpt31 mi_flpt11 mi_flpt51 mi_flpt50 mi_flpt10 mi_flpt12 mi_flpt13
		   mi_flpt14 mi_flpt15 mi_flpt16 mi_flpt21 mi_flpt20 mi_flpt40
		   bottomflname );L型法兰绘制
    ;flange_type 法兰类型
    ;pt_flange法兰绘制初始点
    ;flange_index 法兰序号
    ;sum_flange 法兰总数
    ;reld_hole 螺栓孔直径
    (setq scale_fl 20.0);法兰放大系数
    (setq H (* relH scale_fl));H 法兰高度
    (setq Dout (* relDout scale_fl));Dout 法兰外径
    (setq Din (* relDin scale_fl));Din 法兰内径
    (setq Dpcd (* relDpcd scale_fl));Dpcd 分度圆直径
    (setq tn (* reltn scale_fl));tn 颈厚
    (setq d (* reld_hole scale_fl));d 螺栓孔直径
    (setq L (* relL scale_fl));L颈高
    (setq R (* relR scale_fl));R 圆角
    ;法兰相邻筒节的壁厚确定
    (if (and (/= reltn_top nil) (/= reltn_top 0) (/= reltn_top ""));reltn_top：壁厚
        (setq tn_top (* reltn_top scale_fl))
        (setq tn_top tn)
    )
    (if (and (/= reltn_down nil) (/= reltn_down 0) (/= reltn_down ""))
        (setq tn_down (* reltn_down scale_fl))
        (setq tn_down tn)
    )
    (setq middle_line (* 30 scale_fl))
    (setq Width (/ (- Dout Din) 2))	;;;     法兰宽度
    (setq Width_pcd_in (/ (- Dpcd Din) 2)) ;螺栓孔中心到法兰内径的距离
    ;关键点;;;;
    (setq flpt01 pt_flange)
    (setq flpt02 (polar flpt01 0 (/ Width 2)))
    (setq flpt04 (polar flpt02 0 Width_pcd_in)) 
    (setq flpt06 (polar flpt02 0 Width)) 
    (setq flpt33 (polar flpt06 (/ pi 2) H))  
    (setq flpt31 (polar flpt33 pi tn))
    (setq flpt21 (polar flpt31 (* pi 1.5) (- L R)))
    (setq flpt20 (polar flpt21 pi R))
    (setq flpt16 (polar flpt20 (* pi 1.5) R)) 
    (setq flpt12 (polar flpt02 (/ pi 2) (- H L)))
    (setq flpt03 (polar flpt04 pi (/ d 2)))
    (setq flpt05 (polar flpt04 0 (/ d 2)))
    (setq flpt14 (polar flpt04 (/ pi 2) (- H L)))
    (setq flpt13 (polar flpt14 pi (/ d 2)))
    (setq flpt15 (polar flpt14 0 (/ d 2)))
    (setq flpt11 (polar flpt01 (/ pi 2) (- H L)))
    (setq flpt10 (polar flpt11 pi (/ H 3)))
    (setq flpt00 (polar flpt01 pi (/ H 2)))
    (setq flpt30 (polar flpt11 (/ pi 2) L))
    (setq flpt32 (polar flpt33 pi tn_top))
    (setq flpt51 (polar flpt33 (* pi 0.5) L))
    (setq flpt50 (polar flpt51 pi tn_top))
    (setq flpt40 (polar flpt14 (/ pi 2) middle_line));螺栓中心线上点
    (setq flpt-10 (polar flpt04 (* pi 1.5) middle_line));螺栓中心线下点
  
    ;;;;;对称点
    (setq mi_flpt33 (miFlange flpt33 H))
    (setq mi_flpt32 (polar mi_flpt33 pi tn_down))
    (setq mi_flpt30 (miFlange flpt30 H))
    (setq mi_flpt31 (miFlange flpt31 H))
    (setq mi_flpt11 (miFlange flpt11 (- H L)))
    (setq mi_flpt51 (miFlange flpt51 (+ H L)))
    (setq mi_flpt50 (polar mi_flpt51 pi tn_down))
    (setq mi_flpt10  (miFlange flpt10 (- H L)))			
    (setq mi_flpt12 (miFlange flpt12 (- H L)))			
    (setq mi_flpt13 (miFlange flpt13 (- H L)))			
    (setq mi_flpt14 (miFlange flpt14 (- H L)))
    (setq mi_flpt15 (miFlange flpt15 (- H L)))			
    (setq mi_flpt16 (miFlange flpt16 (- H L)))			
    (setq mi_flpt21 (miFlange flpt21 (+ (- H L) R)))
    (setq mi_flpt20 (miFlange flpt20 (+ (- H L) R)))
    (setq mi_flpt40 (polar mi_flpt14 (* pi 1.5) middle_line))

    ;生成螺栓
    (if (<= boltType 72)
        (bolt boltType (/ (- H L) scale_fl) )
    )
  
    ;连线成法兰
    (command "layer" "M" "1轮廓实线层" "")
    ;绘制L和T型法兰的公共部分
    (command "line" flpt30 flpt33 "")
    (command "line" flpt10 flpt16 "")			
    (command "line" flpt31 flpt21 "")			
    (command "arc" flpt16 "c" flpt20 flpt21);倒角
    (command "line" flpt12 flpt02 "")			
    (command "line" flpt03 flpt13 "")
    (command "line" flpt15 flpt05 "")
    (command "line" flpt33 flpt51 "")
    (command "line" flpt32 flpt50 "")
    (if (= flange_type "L");
		(progn;如果是L型法兰
			(if (and (/= (value retEmbedded 0 0) "") (/= (value retEmbedded 0 0) nil) (= flange_index 0))
				(progn ;如果基础那里有数,并且是底法兰
					(setq mi_flpt31 (polar mi_flpt33 pi (* (value retEmbedded 2 0) scale_fl)))
					(setq mi_flpt21 (polar mi_flpt31 (/ pi 2) (- L R) ))
					(setq mi_flpt20 (polar mi_flpt21 pi R))
					(setq mi_flpt16 (polar mi_flpt20 (/ pi 2) R));;;;;
					(setq mi_flpt51 (polar mi_flpt33 (* pi 1.5) L))
					(setq mi_flpt51 (polar mi_flpt51 0 (/  (- (* (value retEmbedded 4 0) scale_fl) Dout ) 2 )))
					(setq mi_flpt50 (polar mi_flpt51 pi (* (value retEmbedded 5 0) scale_fl)))
					(setq mi_pt_h11 (polar mi_flpt51 (/ pi 2) L))
					(setq mi_pt_t11 (polar mi_pt_h11 pi (* (value retEmbedded 5 0) scale_fl) ))
					(setq ts (* (value retEmbedded 2 0) scale_fl));基础环上法兰脖子厚
				)
				(setq ts tn);若不是底法兰，或基础环数据那里为空
			)
			;(setq repline2_mi (vla-mirror repline2 (vlax-3d-point rept1 ) (vlax-3d-point rept5)))
			;绘制L法兰特有的部分
			(command "line" flpt00 flpt03 "");
			(command "line" flpt05 flpt06 "");不再穿过螺栓
			(command "line" flpt33 flpt06 "");右侧壁直线
			;绘制对称法兰
			;视图名绘制,点，序号，比例
			(FlangeZoomTitle (polar flpt40 (* pi 0.5) (* 25 mScale)) (- sum_flange flange_index) (/ mscale scale_fl))
			(command "insert" boltName "S" scale_fl mi_flpt14 "")			
			(if (= flange_index 0) 
				(progn
					(if (or (= (value retEmbedded 0 0) "") (= (value retEmbedded 0 0) nil)) 
						(progn;如果底法兰并且基础环法兰那里没有数据
							(command "layer" "M" "4虚线层" "")
							(command "line" mi_flpt30 mi_flpt33 "")
							(command "line" mi_flpt33 mi_flpt51 "")
							(command "line" mi_flpt31 mi_flpt50 "")
						)
						(progn;如果是底法兰但是有数据
							(command "layer" "M" "1轮廓实线层" "")		
							(command "line" mi_flpt30 mi_pt_h11 "")
							(command "line" mi_pt_h11 mi_flpt51 "")
							(command "line" mi_pt_t11 mi_flpt50 "") 
						)
					)
				)
				(progn;如果不是底法兰
					(command "line" mi_flpt30 mi_flpt33 "")
					(command "line" mi_flpt33 mi_flpt51 "")
					(command "line" mi_flpt32 mi_flpt50 "")
				)
			)
			(command "line" mi_flpt10  mi_flpt16 "")			
			(command "line" mi_flpt31 mi_flpt21 "")			
			(command "arc" mi_flpt21 "c" mi_flpt20 mi_flpt16)
			(command "line" mi_flpt12 flpt02 "")
			(command "line" flpt03 mi_flpt13 "")
			(command "line" mi_flpt15 flpt05 "")
			(command "line" mi_flpt33 flpt06 "")
			(Middleline flpt40 mi_flpt40 (* 20 scale_fl));螺栓中心的线
			(command "layer" "M" "2细线层" "")
			(command "spline" flpt51 flpt50 flpt30 flpt10 flpt00  mi_flpt10  mi_flpt30 mi_flpt50 mi_flpt51 "" "" "");绘制剖切的弧线
			;直径标注
			(if (and (/= (value retEmbedded 4 0 ) nil) (/= (value retEmbedded 4 0 ) ""))
				(if (and (/= relDout (value retEmbedded 4 0 )) (= flange_index 0));如果底法兰外径与基础环筒体外径不相等
				;;;标注筒体直径
					(progn
						;(Dimflange (polar mi_flpt51 pi (* (value retEmbedded 4 0) scale_fl)) mi_flpt51 (polar mi_flpt30 (* pi 1.5) (* 200 scale_fl)) scale_fl 0 reld_hole);基础环筒体标注
						(DimflD (polar mi_flpt51 pi (* (value retEmbedded 4 0) scale_fl)) mi_flpt51  (/ 1 scale_fl) 0 reld_hole (polar mi_flpt30 (* pi 1.5) (* 200 scale_fl)));基础环筒体标注
					)
				)
			)
			(DimflD (polar mi_flpt33 pi Dout) mi_flpt33  (/ 1 scale_fl) 0 reld_hole (polar mi_flpt30 (* pi 1.5) (* 150 scale_fl)));法兰外径标注
			(DimflD (polar mi_flpt14 pi Dpcd) mi_flpt14  (/ 1 scale_fl) num_hole reld_hole (polar mi_flpt30 (* pi 1.5) (* 100 scale_fl)));分度圆直径标注
			(DimflD (polar mi_flpt12 pi Din) mi_flpt12  (/ 1 scale_fl) 0 reld_hole (polar mi_flpt30 (* pi 1.5) (* 50 scale_fl)));内径标注
			;法兰厚度和高度标注
			(dimFlange_thick2 flpt12 flpt02 (/ 1 scale_fl ) 0 (- (/ (- H L) 2)) );上法兰度厚度标注
			(ldimv2 flpt33 flpt06 (/ 1 scale_fl ) 0 2500);上法兰高度标注
			(dimFlange_thick2 mi_flpt12 flpt02 (/ 1 scale_fl ) 0 (- (/ (- H L) 2)) );对称的下法兰度厚度标注
			(ldimv2 mi_flpt33 flpt06 (/ 1 scale_fl ) 0 2500);下法兰高度标注
			;法兰脖子和连接筒体厚度标注
			(command "zoom" "w" mi_flpt51 mi_flpt31)
			(command "regen")
			;(scaleDim flpt21 (polar flpt21 0 tn) scale_fl 0);上法兰脖子厚度标注
		
			(dimh_flange flpt21 (polar flpt21 0 tn) (/ 1.0 scale_fl) (- 0 (* 10 scale_fl) 100) 200 0);上法兰脖子厚度标注
	        (scaleDim flpt50 (polar flpt50 0 tn_top) scale_fl 1);上法兰上筒体厚度标注
			;(scaleDim mi_flpt50 mi_flpt51 scale_fl 2) ;基础环筒体厚度标注
			(dimh_flange mi_flpt50 mi_flpt51 (/ 1.0 scale_fl) (- 0 (* 130 (/ mscale scale_fl))) 200 0);基础环筒体厚度标注
			(scaleDim mi_flpt21 (polar mi_flpt21 0 ts) scale_fl 0);下法兰脖子厚度标注
			;法兰质量标注
			(flmasslead FlangeIndex Flange_type flpt06 MassOfFlange (- relH relL));法兰重量标注
			;;打剖面线,对角
			(bhatch_pt flpt12 flpt03 scale_fl 0)
			(bhatch_pt flpt15 flpt06 scale_fl 0)
			(bhatch_pt flpt50 flpt33 scale_fl 90) 
			(bhatch_pt flpt02 mi_flpt13 scale_fl 90)
			(bhatch_pt flpt05 mi_flpt33 scale_fl 90)
			(bhatch_pt mi_flpt31 mi_flpt51 scale_fl 0)
		);L型法兰绘制结束	    
		(progn;如果是T型法兰
			(setq Da_outer (value retFlange flange_index 13));T型法兰外径
			(setq Dm_outer (value retFlange flange_index 14));T型法兰外分度圆
			(tfldadmouter T_D T_D relDout reltn relDpcd relDin);得到T型法兰的外径和外分度圆
			(setq T_D (* Da_outer scale_fl))
			(setq T_D_m (* Dm_outer scale_fl))
			(setq tflpt22 (polar flpt21 0 tn))
			(setq tflpt23 (polar tflpt22 0 R))
			(setq tflpt17 (polar flpt16 0 (+ tn (* R 2))))
			(setq tflpt111 (polar flpt12 0 (/ (- T_D Din)2)))
			(setq tflpt19 (polar tflpt111 pi (/ (- T_D T_D_m) 2)))
			(setq tflpt110 (polar tflpt19 0 (/ d 2)))
			(setq tflpt18 (polar tflpt19 pi (/ d 2)))
			(setq tflpt010 (polar tflpt111 (* 1.5 pi) (- H L)))
			(setq tflpt08 (polar tflpt19 (* 1.5 pi) (- H L)))
			(setq tflpt09 (polar tflpt08 0 (/ d 2)))
			(setq tflpt07 (polar tflpt08 pi (/ d 2)))
			(setq DTout T_D)
			(setq DTpcd T_D_m)
			(setq tflpt41 (polar tflpt19 (/ pi 2) middle_line))
			(setq tflpt-11 (polar tflpt08 (* pi 1.5) middle_line))
			;绘制公共直线
			(command "line" tflpt17 tflpt111 "");右侧上端面横线
			(command "line" tflpt18 tflpt07 "");上法兰右侧螺栓孔左线
			(command "line" tflpt110 tflpt09 "");上法兰右侧螺栓孔右线
			(command "line" flpt33 tflpt22 "");;T上法兰右侧颈右侧竖线
			(command "line" tflpt111 tflpt010 "");T上法兰右侧最右竖线
			(command "arc" tflpt22 "c" tflpt23 tflpt17);T上法兰右侧圆角的弧
			(if (= flange_index 0);判断是否是底法兰
				(progn;如果是底法兰
					(anchor_bolt boltType (/ (- H L) scale_fl));锚栓块绘制
					(command "line" flpt00 tflpt010 "");下端面直线
					;;;绘制样条曲线
					(command "layer" "M" "2细线层" "")
					(command "spline" flpt51 flpt50 flpt30 flpt10 flpt00 "" "" "")
					(command "insert" boltName "S" scale_fl flpt14 "")
					(command "insert" boltName "S" scale_fl tflpt19 "")
					(Middleline flpt-10 flpt40 (* 20 scale_fl))
					(Middleline tflpt-11 tflpt41 (* 20 scale_fl))
					;名称
					(FlangeZoomTitle (polar flpt40 (* pi 0.5) (* 25 mScale)) (- sum_flange flange_index) (/ mscale scale_fl))
					;标注
					;直径标注
					;(Dimflange (polar flpt06 pi Dout) tflpt22 (polar flpt01 (* pi 1.5) (* 150 scale_fl)) scale_fl 0 reld_hole) ;外径
					;(Dimflange (polar flpt04 pi Dpcd) flpt04 (polar flpt01 (* pi 1.5) (* 100 scale_fl)) scale_fl (/ num_hole 2) reld_hole) ;内分度圆
					;(Dimflange (polar flpt02 pi Din) flpt02 (polar flpt01 (* pi 1.5) (* 50 scale_fl)) scale_fl 0 reld_hole) ;内径标注
					;(Dimflange (polar tflpt010 pi DTout) tflpt010 (polar flpt01 (* pi 1.5) (* 250 scale_fl)) scale_fl 0 reld_hole) ;最外径
					;(Dimflange (polar tflpt08 pi DTpcd) tflpt08 (polar flpt01 (* pi 1.5) (* 200 scale_fl)) scale_fl (/ num_hole 2) reld_hole) ;外分度圆
					(DimflD (polar tflpt22 pi Dout) tflpt22  (/ 1 scale_fl) 0 reld_hole (polar flpt01 (* pi 1.5) (* 150 scale_fl)));外径
					(DimflD (polar flpt04 pi Dpcd) flpt04  (/ 1 scale_fl) (/ num_hole 2) reld_hole (polar flpt01 (* pi 1.5) (* 100 scale_fl)));内分度圆
					(DimflD (polar flpt02 pi Din) flpt02  (/ 1 scale_fl) 0 reld_hole (polar flpt01 (* pi 1.5) (* 50 scale_fl))) ;内径标注
					(DimflD (polar tflpt010 pi DTout) tflpt010  (/ 1 scale_fl) 0 reld_hole (polar flpt01 (* pi 1.5) (* 250 scale_fl)));最外径
					(DimflD (polar tflpt08 pi DTpcd) tflpt08  (/ 1 scale_fl) (/ num_hole 2) reld_hole (polar flpt01 (* pi 1.5) (* 200 scale_fl))) ;外分度圆
					;法兰厚度标注
					;(dimFlange_thick flpt12 flpt02 "L" scale_fl);法兰厚度标注
					(dimFlange_thick2 flpt12 flpt02 (/ 1 scale_fl ) 0 (- (/ (- H L) 2)) )
					;(dimFlange_thick flpt33 tflpt010 "R" scale_fl);法兰高度标注
					(dimFlange_h flpt33 tflpt010 scale_fl 2500);法兰高度标注
					;法兰脖子标注
					;(scaleDim flpt21 (polar flpt21 0 tn) scale_fl 0);法兰脖子厚度标注
					(dimh_flange flpt21 (polar flpt21 0 tn) (/ 1.0 scale_fl) 0 200 0);法兰脖子厚度标注
					(scaleDim flpt50 (polar flpt50 0 tn_top) scale_fl 1);法兰高度标注
					;法兰质量标注
					(flmasslead flange_index Flange_type tflpt010 MassOfFlange (- relH relL));法兰重量标注
					;标注力矩
					(AnchorBolt BoltType BoltClass tflpt19 Moment)
					;打剖面线
					(bhatch_pt tflpt110 tflpt010 scale_fl 0)
					(bhatch_pt flpt12 flpt03 scale_fl 0)
					(bhatch_pt flpt15 flpt06 scale_fl 0)
					(bhatch_pt flpt50 flpt33 scale_fl 90)
				);如果是底法兰的if右括号
				(progn;如果不是底法兰
					(bolt boltType (- relH relL) );螺栓绘制
					;;;;镜像点,T型连接法兰特有的
					(setq mi_tflpt19 (miFlange tflpt19 (- H L)));T型连接法兰特有的
					(setq mi_tflpt17 (miFlange tflpt17 (- H L)));T型连接法兰特有的
					(setq mi_tflpt18 (miFlange tflpt18 (- H L)));T型连接法兰特有的
					(setq mi_tflpt110 (miFlange tflpt110 (- H L)));T型连接法兰特有的
					(setq mi_tflpt111 (miFlange tflpt111 (- H L)));T型连接法兰特有的
					(setq mi_tflpt23 (polar mi_tflpt17 (* 1.5 pi) R));T型连接法兰特有的
					(setq mi_tflpt22 (polar mi_tflpt23 pi R ));T型连接法兰特有的
					(setq mi_tflpt41 (polar mi_tflpt19 (* 1.5 pi) middle_line));
					;绘制线
					(command "line" mi_flpt10  mi_flpt16 "");下法兰左侧上面横线
					(command "line" mi_flpt31 mi_flpt21 "");下法兰颈左侧竖线
					(command "arc" mi_flpt21 "c" mi_flpt20 mi_flpt16);下法兰左侧圆角弧线
					(command "line" mi_flpt12 flpt02 "");下法兰左侧最左竖线
					(command "line" mi_flpt13 flpt03 "");下法兰左侧螺栓孔左侧竖线
					(command "line" mi_flpt15 flpt05 "");下法兰左侧螺栓孔右侧竖线
					(command "line" mi_flpt30 mi_flpt33 "");下法兰下筒节横线
					(command "line" mi_flpt33 mi_flpt51 "");下法兰右侧下筒节右侧竖线
					;(command "line" mi_flpt31 mi_flpt50 "");下法兰左侧下筒节左侧竖线
					(command "line" mi_flpt50 (polar mi_flpt50 (/ pi 2) L) "");下法兰左侧下筒节左侧竖线
					(command "line" mi_flpt33 mi_tflpt22 "");;下法兰颈右侧竖线
					(command "line" mi_tflpt17 mi_tflpt111 "");下法兰右侧上面横线
					(command "line" mi_tflpt18 tflpt07 "");下法兰右螺栓孔左线	
					(command "line" mi_tflpt110 tflpt09 "");下法兰右螺栓孔右线
					(command "arc" mi_tflpt17 "c" mi_tflpt23 mi_tflpt22);下法兰右圆角弧线
					(command "line" mi_tflpt111 tflpt010 "");下法兰最右侧竖线	
					(command "line" flpt00 flpt03 "");上法兰下底面左线
					(command "line" flpt05 tflpt07 "");上法兰下底面中线
					(command "line" tflpt09 tflpt010 "");上法兰下底面右线
					;;;绘制样条曲线
					(command "layer" "M" "2细线层" "")
					(command "spline" flpt51 flpt50 flpt30 flpt10 flpt00 mi_flpt10 mi_flpt30 mi_flpt50 mi_flpt51 "" "" "");剖切线
					(command "insert" boltName "S" scale_fl mi_flpt14 "");插入左螺栓
					(command "insert" boltName "S" scale_fl mi_tflpt19 "");插入右螺栓
					(Middleline flpt40 mi_flpt40 (* 20 scale_fl))
					(Middleline tflpt41 mi_tflpt41 (* 20 scale_fl))
					(FlangeZoomTitle (polar flpt40 (* pi 0.5) (* 25 mScale)) (- sum_flange flange_index) (/ mscale scale_fl))
						
					;标注
					;直径标注
					;(Dimflange (polar mi_tflpt111 pi DTout) mi_tflpt111 (polar flpt01 (* pi 1.5) (* 450 scale_fl)) scale_fl 0 reld_hole) ;最外径
					;(Dimflange (polar mi_tflpt19 pi DTpcd) mi_tflpt19 (polar flpt01 (* pi 1.5) (* 400 scale_fl)) scale_fl (/ num_hole 2) reld_hole) ;外分度圆
					;(Dimflange (polar mi_flpt33 pi Dout) mi_flpt33 (polar flpt01 (* pi 1.5) (* 350 scale_fl)) scale_fl 0 reld_hole) ;外径
					;(Dimflange (polar mi_flpt14 pi Dpcd) mi_flpt14 (polar flpt01 (* pi 1.5) (* 300 scale_fl)) scale_fl (/ num_hole 2) reld_hole) ;内分度圆
					;(Dimflange (polar mi_flpt12 pi Din) mi_flpt12 (polar flpt01 (* pi 1.5) (* 250 scale_fl)) scale_fl 0 reld_hole) ;内径标注

					(DimflD (polar mi_tflpt111 pi DTout) mi_tflpt111  (/ 1 scale_fl) 0 reld_hole (polar flpt01 (* pi 1.5) (* 450 scale_fl)));最外径
					(DimflD (polar mi_tflpt19 pi DTpcd) mi_tflpt19  (/ 1 scale_fl) (/ num_hole 2) reld_hole (polar flpt01 (* pi 1.5) (* 400 scale_fl)));外分度圆
					(DimflD (polar mi_flpt33 pi Dout) mi_flpt33  (/ 1 scale_fl) 0 reld_hole (polar flpt01 (* pi 1.5) (* 350 scale_fl)));外径
					(DimflD (polar mi_flpt14 pi Dpcd) mi_flpt14  (/ 1 scale_fl) (/ num_hole 2) reld_hole (polar flpt01 (* pi 1.5) (* 300 scale_fl)));内分度圆
					(DimflD (polar mi_flpt12 pi Din) mi_flpt12  (/ 1 scale_fl) 0 reld_hole (polar flpt01 (* pi 1.5) (* 250 scale_fl)));内径标注
	    
					;法兰厚度标注
					;(dimFlange_thick flpt12 flpt02 "L" scale_fl);上法兰法兰厚度标注
					;(dimFlange_thick flpt33 tflpt010 "R" scale_fl);上法兰高度标注
				
					;(dimFlange_thick mi_flpt12 flpt02 "L" scale_fl);下法兰法兰厚度标注
					;(dimFlange_thick mi_flpt33 tflpt010 "R" scale_fl);下法兰高度标注
					(dimFlange_thick2 flpt12 flpt02 (/ 1 scale_fl ) 0 (- (/ (- H L) 2)) );上法兰法兰厚度标注
					(dimFlange_h flpt33 tflpt010 scale_fl 2500);上法兰高度标注
					(dimFlange_thick2 mi_flpt12 flpt02 (/ 1 scale_fl ) 0 (- (/ (- H L) 2)) );下法兰法兰厚度标注
					(dimFlange_h mi_flpt33 tflpt010 scale_fl 2500);下法兰高度标注

	    
					;法兰脖子标注
					(scaleDim flpt21 (polar flpt21 0 tn) scale_fl 0);法兰脖子厚度标注
					(scaleDim flpt50 (polar flpt50 0 tn_top) scale_fl 1);上法兰连接处筒节厚度标注
					(scaleDim mi_flpt21 (polar mi_flpt21 0 tn) scale_fl 0);下法兰脖子厚度标注
					;(scaleDim mi_flpt50 (polar mi_flpt50 0 tn_top) scale_fl 2);下法兰连接筒节厚度标注
					(scaleDim mi_flpt50 (polar mi_flpt50 0 tn_down) scale_fl 2);下法兰连接筒节厚度标注
	    
					;法兰重量标注
					(flmasslead flange_index Flange_type tflpt010 MassOfFlange (- relH relL));法兰重量标注
					;打剖面线
					(bhatch_pt tflpt110 tflpt010 scale_fl 0);上法兰最右
					(bhatch_pt flpt12 flpt03 scale_fl 0);上法兰最左
					(bhatch_pt flpt15 flpt06 scale_fl 0);上法兰中间
					(bhatch_pt flpt50 flpt33 scale_fl 90);上法兰连接筒节
					;下法兰剖面线
					(bhatch_pt mi_tflpt110 tflpt010 scale_fl 90);下法兰最右
					(bhatch_pt mi_flpt12 flpt03 scale_fl 90);下法兰最左
					(bhatch_pt flpt05 mi_tflpt18 scale_fl 90);中间法兰
					(bhatch_pt mi_flpt50 mi_flpt33 scale_fl 0);下法兰连接筒节       
				);如果不是底法兰执行的右括号
			);if的右括号
		);T 型法兰绘制结束
    );判断法兰类型右括号
	(setq bolt_zb_list (append bolt_zb_list (list flpt40) ))
);法兰绘制函数终结括号

;;;;判断T型法兰的最外径和分度圆是否有值，若没有计算出来,返回的是实际值
(defun  tfldadmouter (Da_outer Dm_outer relDout reltn relDpcd relDin)
  ;(setq Da_outer (value retFlange flange_index 13));T型法兰外径
  ;(setq Dm_outer (value retFlange flange_index 14));T型法兰外分度圆
  (if (or (= Da_outer nil) (= Da_outer "") (= Dm_outer nil) (= Dm_outer ""));如果这两个值有空的时，用两倍算出来，为了兼容旧表
    (progn
      (setq Da_outer (+ relDout (* (- (/ (- relDout relDin) 2) reltn) 2)))
      (setq Dm_outer (- Da_outer (* (/ (- relDpcd relDin) 2) 2)))
    )
    (progn
      (setq Da_outer  Da_outer)
      (setq Dm_outer  Dm_outer)
    )
  )
);;;;函数结束




;;;;;;;;;;;;;;;;;;;;;顶法兰插入;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defun topflinsert (pt_flange TopFlangeName scale_fl relH / )
  ;法兰名称序号标注


  (cond
    ((and (= mscale 100) (= topfltype "21_TopFlange") )
      (FlangeZoomTitle (polar (polar pt_flange 0 (* 30 mscale)) (/ pi 2) (* (/ relH 3) 60))      0 (/ mscale scale_fl)
      )
    )
    ((and (= mscale 100) (or (= topfltype "5S_TopFlange") (= topfltype "5X_TopFlange")))
      (FlangeZoomTitle (polar (polar pt_flange 0 (* 30 mscale)) (/ pi 2) (* (/ relH 3) 60))      0 (/ mscale scale_fl)
      )
    )
    (t
      (FlangeZoomTitle (polar (polar pt_flange 0 (* 30 mscale)) (/ pi 2) (* (/ relH 3) 80)) 0 (/ mscale scale_fl))
    )
  );cond
  
  ;(FlangeZoomTitle (polar (polar pt_flange 0 (* 30 mscale)) (/ pi 2) (* (/ relH 3) 80)) 0 (/ mscale scale_fl))
  
  (if (/= TopFlangeName "other_TopFlange")
    (command "insert" TopFlangeName "S" 1 pt_flange "")
    (print "顶法兰数据有误")
  );if的右括号
)
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;;;;;;;;;;;;;;;;**************混塔-混段插入********************;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defun Concrete_insert ();插入原点
  (if (> bflange_hole 79)
    (command "insert" "Concrete_Block" "S" 1 (nth 0 sthptlist) "")
  )
)
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;;;;;;;;;;;;;;;;**************外爬梯插入********************;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defun Entrance_stairs_insert(fl_pa entrance_stairs_name );插入原点
  (if (/= entrance_stairs_name "NoStairs")
    (command "insert" entrance_stairs_name "S" 1 fl_pa "")
    (progn
      (print "程序中或现有产品中，无对应的Stair规格！")
    )
  )
)
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;



;;;;*************************门洞生成并插入*******************************************
(defun Door_insert( / BP_pa doorscale doorH D Thick h1 b1 h2 bst hst t1 t2 material repAngle 
		   DoorfrontPt DoorleftPT oript1 oript2 oript3 repdrfrtoript repDrViewPt
		   repoript)
	(setq BP_pa (nth 0 sthptlist));定义原点
	;(setq doorscale 50.0);局部视图的放大比例,也就是1：doorscale,1:40,实际的放大比例是图纸比例除以40
	;插入的位置
	(cond
		((or (= flange_qty 2) (= flange_qty 3))
			(setq DoorfrontPt (nth 3 enkeyptlist))
			(setq repdrfrtoript DoorfrontPt)
		)
		((or (= flange_qty 4) (= flange_qty 5) (= flange_qty 6))
			(setq DoorfrontPt (nth 6 enkeyptlist))
			(setq repdrfrtoript DoorfrontPt)
		)
		((or (= flange_qty 7) (= flange_qty 8) (= flange_qty 9))
			(setq DoorfrontPt (nth 9 enkeyptlist))
			(setq repdrfrtoript DoorfrontPt)
		)
		(t
			(setq DoorfrontPt (nth 12 enkeyptlist))
			(setq repdrfrtoript DoorfrontPt)
		)
	)
	;放大系数
	(setq doorscale (doorscalejudge));放大比例
	(if (= (ReplateDoor)  nil);如果为普通门框;普通门框不为空时
		(progn ;普通门框
			(setq doorH (value retDoor 0 0));普通门框的高度
			(setq D (value retDoor 0 1);塔筒外径
					Thick (value retDoor 0 2);塔筒壁厚
					h1 (value retDoor 0 3);门框总高度
					b1 (value retDoor 0 4);门框宽度
					h2 (value retDoor 0 5);直边长度
					bst (value retDoor 0 6);门框厚度
					hst (value retDoor 0 7);门款宽度
			)
			;如果是普通门框再放大两倍
			(setq doorscale (/ doorscale 2));放大比例
				;(setq doorscale 20)
				;;门洞挖去的重量,全局变量,bom表用
			(setq MassOfTowerDoorHole (GeneralDoorMass D Thick h1 b1 h2 bst hst))
				;;;;普通门框的重量,全局变量,bom表用
			(setq MassOfDoor (GeneralDoorFrameMass h1 b1 h2 bst hst))
				;主体上的门框绘制
			(generaldoorfront (polar BP_pa (/ pi 2) doorH) 1 MassOfDoor doorH D Thick h1 b1 h2 bst hst nil);塔架门洞生成,插入原点，放大倍数1倍
				;主体上门框高度标注
			(setq doorpt1 (nth 0 sthptrlist));主体中门洞标注的下点
			(cond
				((= topfltype "3MW_S_New_TopFlange")
					(command "dimlinear" doorpt1 (polar BP_pa (/ pi 2) doorH) "v" (polar BP_pa 0 6900));主体上门洞高度标注
				)
				(t
					(command "dimlinear" doorpt1 (polar BP_pa (/ pi 2) doorH) "v" (polar BP_pa 0 3300));主体上门洞高度标注
				)
			)
			(setq DoorfrontPt (polar DoorfrontPt 0 6000))
			(setq DoorfrontPt (polar DoorfrontPt (* pi 1.5) 7000 ))
			;转移焦点
			(command "zoom" "w" DoorfrontPt (polar DoorfrontPt (/ pi 4) 10000))
			(command "regen")
			;(setq DoorfrontPt (polar (polar BP_pa (/ pi 2) 5000) 0 15000))
			(generaldoorfront DoorfrontPt (/ mscale doorscale) MassOfDoor doorH D Thick h1 b1 h2 bst hst T)
			;门框侧面放大视图
			(setq DoorleftPT (polar (polar DoorfrontPt (* pi 1.5) (* (/ h1 2) (/ mscale doorscale))) 0 3000))
			(generaldoorleft DoorleftPT (/ mscale doorscale) doorH D Thick h1 b1 h2 bst hst T)
			(print "门洞视图绘制成功！")
		);加强板门框
		(progn 
			(if (= (ReplateDoor)  T)
				(progn ;加强门框
					(if (< (value retTower 1 2) 2.5)
						(progn
							(setq doorH (value retDoor 0 12));加强门框的高度
							(setq D (value retDoor 0 13);塔筒壁直径
									t1 (value retDoor 0 14);塔筒壁厚度t1
										hh1 (value retDoor 0 15); 补强板高度
									t2 (value retDoor 0 16);补强板厚度t2
										h2 (value retDoor 0 17);直边长度H_V
									h1 (value retDoor 0 18);门洞高度H2
									b1 (value retDoor 0 19);门洞宽度w
									material (value retDoor 0 20);门洞材料
									repAngle (value retDoor 0 21);加强板跨度-开洞的角度
							)
							(if (or (= repAngle nil) (= repAngle ""))
								(progn 
									(if (= TheTowerSliceType "SliceTower")
										(setq angle_plate (* 5(/ pi 36))) ;分片塔门洞开口-50°/2
										(setq angle_plate (/ pi 6))  ;常规塔门洞开口-60°/2
									)
								)
									(setq angle_plate (* (/ repAngle 2 180) pi))  ;根据数据表确定开口-angle_plate°/2 
							);end if 
							;;;加强板门洞挖去的重量,全局变量,bom表用
							(setq MassOfTowerDoorHole (RepDoorHoleMass_1 D t1 hh1 t2 h2 h1 b1))
							;;;加强板重量,全局变量,bom表用
							(setq MassOfDoor (Repdoormass_1 D t1 hh1 t2 h2 h1 b1))
							;主体中的加强板绘制
							(setq oript1 (polar BP_pa (/ pi 2) doorH));主体中加强板的绘制原点
							(replatedoorfront_1 oript1 1 MassOfDoor doorH D t1 hh1 t2 h2 h1 b1 nil);中心点，放大比例
								;加强板厚度标注
							(setq oript2 (polar oript1 (/ pi 3) 1200))
							(setq oript3 (polar oript2 (/ pi 2) 200))
							(if (/=  materilal_style 2)
								(Thick_drw t2 material oript2 oript3 3000.0 (/ pi 6));thick:壁厚,下坐标，上坐标
								(Thick_drw_1 t2 material oript2 oript3 3000.0 (/ pi 6));thick:壁厚,下坐标，上坐标
							)					
							(setq repdrfrtoript (polar repdrfrtoript (* pi 1.5) 7000 ))
							(setq repdrfrtoript (polar repdrfrtoript (* pi 0) 3100 ))
								;转移焦点
							(command "zoom" "w" repdrfrtoript (polar repdrfrtoript (/ pi 4) 10000))
							(command "regen")
						  
							;(setq ang1 (/ pi 3));偏转的角度
							;(setq repdrfrtoript (polar BP_pa ang1 (/ 20000 (cos ang1))));中心原点
							(replatedoorfront_1 repdrfrtoript (/ mscale doorscale) MassOfDoor doorH D t1 hh1 t2 h2 h1 b1 T);加强板放大视图
							;加强板放大俯视图绘制
							(setq repdrtopoript (polar repdrfrtoript (* pi 1.5) (* 7000 (/ mscale doorscale))));7000=塔筒外半径+门框高度+一定的间隙
							(replatedoortop repdrtopoript (/ mscale doorscale) doorH D t1 hh1 t2 h2 h1 b1 nil);加强板放大俯视图
							;加强板放大视图的名称绘制
							(setq repDrViewPt (polar repdrfrtoript (* pi 0.5) (* 5000 (/ mscale doorscale))))
							(repdrfrtnamelead repDrViewPt doorscale);即塔架门洞1：x
							;A-A标题绘制
							(setq section_text_A_A (polar repdrtopoript (/ pi 2)  (* (+ (/ D 2) 500) (/ mscale doorscale))))
							(setq text_height (* 5 mscale))
							(repdrtopnamelead section_text_A_A text_height);A-A标题
							;加强板壁厚放大视图绘制
							(setq doorSec_angle_pt1 (polar repdrtopoript startang_plate_left (* (/ D 2) (/ mscale doorscale))));放大视图符号原点
							(if (and (/= (value retEmbedded 0 0) "") (/= (value retEmbedded 0 0) nil))
								(setq door_zoom_index (+ 2 section_qty))
								(setq door_zoom_index (+ 1 section_qty))
							)
							(FlangeZoom doorSec_angle_pt1 door_zoom_index mscale nil T );doorSec_angle_pt2放大视图圆圈的原点;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
							(setq repoript (polar repdrtopoript (* pi 1.5)  (+ (* (/ D 2) (/ mscale doorscale)) 9500)));加强板放大视图圆圈的中心点
							(repdraw t1 t2 mscale 10.0 repoript door_zoom_index);加强板放大视图绘制,door_zoom_index:序号
							(print "门洞视图绘制成功！")
						)
						(progn
							(setq doorH (value retDoor 0 12));加强门框的高度
							(setq D (value retDoor 0 13);塔筒壁直径
									t1 (value retDoor 0 14);塔筒壁厚度t1
										hh1 (value retDoor 0 15); 补强板高度
									t2 (value retDoor 0 16);补强板厚度t2
										h2 (value retDoor 0 17);直边长度H_V
									h1 (value retDoor 0 18);门洞高度H2
									b1 (value retDoor 0 19);门洞宽度w
									material (value retDoor 0 20);门洞材料
									repAngle (value retDoor 0 21);加强板跨度-开洞的角度
							)
							(if (or (= repAngle nil) (= repAngle ""))
								(progn 
									(if (= TheTowerSliceType "SliceTower")
										(setq angle_plate (* 5(/ pi 36))) ;分片塔门洞开口-50°/2
										(setq angle_plate (/ pi 6))  ;常规塔门洞开口-60°/2
									)
								)
									(setq angle_plate (* (/ repAngle 2 180) pi))  ;根据数据表确定开口-angle_plate°/2 
							);end if 
							;;;加强板门洞挖去的重量,全局变量,bom表用
							(setq MassOfTowerDoorHole (RepDoorHoleMass D t1 hh1 t2 h2 h1 b1))
							;;;加强板重量,全局变量,bom表用
							(setq MassOfDoor (Repdoormass D t1 hh1 t2 h2 h1 b1))
							;主体中的加强板绘制
							(setq oript1 (polar BP_pa (/ pi 2) doorH));主体中加强板的绘制原点
							(replatedoorfront oript1 1 MassOfDoor doorH D t1 hh1 t2 h2 h1 b1 nil);中心点，放大比例
								;加强板厚度标注
							(setq oript2 (polar oript1 (/ pi 3) 1200))
							(setq oript3 (polar oript2 (/ pi 2) 200))
							(if (/=  materilal_style 2)
								(Thick_drw t2 material oript2 oript3 3000.0 (/ pi 6));thick:壁厚,下坐标，上坐标
								(Thick_drw_1 t2 material oript2 oript3 3000.0 (/ pi 6));thick:壁厚,下坐标，上坐标
							)					
							(setq repdrfrtoript (polar repdrfrtoript (* pi 1.5) 7000 ))
							(setq repdrfrtoript (polar repdrfrtoript (* pi 0) 3100 ))
								;转移焦点
							(command "zoom" "w" repdrfrtoript (polar repdrfrtoript (/ pi 4) 10000))
							(command "regen")
						  
							;(setq ang1 (/ pi 3));偏转的角度
							;(setq repdrfrtoript (polar BP_pa ang1 (/ 20000 (cos ang1))));中心原点
							(replatedoorfront repdrfrtoript (/ mscale doorscale) MassOfDoor doorH D t1 hh1 t2 h2 h1 b1 T);加强板放大视图
							;加强板放大俯视图绘制
							(setq repdrtopoript (polar repdrfrtoript (* pi 1.5) (* 7000 (/ mscale doorscale))));7000=塔筒外半径+门框高度+一定的间隙
							(replatedoortop repdrtopoript (/ mscale doorscale) doorH D t1 hh1 t2 h2 h1 b1 nil);加强板放大俯视图
							;加强板放大视图的名称绘制
							(setq repDrViewPt (polar repdrfrtoript (* pi 0.5) (* 5000 (/ mscale doorscale))))
							(repdrfrtnamelead repDrViewPt doorscale);即塔架门洞1：x
							;A-A标题绘制
							(setq section_text_A_A (polar repdrtopoript (/ pi 2)  (* (+ (/ D 2) 500) (/ mscale doorscale))))
							(setq text_height (* 5 mscale))
							(repdrtopnamelead section_text_A_A text_height);A-A标题
							;加强板壁厚放大视图绘制
							(setq doorSec_angle_pt1 (polar repdrtopoript startang_plate_left (* (/ D 2) (/ mscale doorscale))));放大视图符号原点
							(if (and (/= (value retEmbedded 0 0) "") (/= (value retEmbedded 0 0) nil))
								(setq door_zoom_index (+ 2 section_qty))
								(setq door_zoom_index (+ 1 section_qty))
							)
							(FlangeZoom doorSec_angle_pt1 door_zoom_index mscale nil T );doorSec_angle_pt2放大视图圆圈的原点;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
							(setq repoript (polar repdrtopoript (* pi 1.5)  (+ (* (/ D 2) (/ mscale doorscale)) 9500)));加强板放大视图圆圈的中心点
							(repdraw t1 t2 mscale 10.0 repoript door_zoom_index);加强板放大视图绘制,door_zoom_index:序号
							(print "门洞视图绘制成功！")
						)
					)
					
				)
				(progn
					(setq MassOfDoor 0.0)
					(setq MassOfTowerDoorHole 0.0)
				)
			)
      
		)
	)
)
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;加强板门框俯视图AA标注
(defun repdrtopnamelead(section_text_A_A text_height)
  (setq section_text_A_A_end (polar section_text_A_A 0 15))
	            (entmake (list '(0 . "TEXT")
		                   '(100 . "AcDbEntity")
		                   '(67 . 0)
		                   '(410 . "Model")
		                   '(8 . "6文字层")
		                   '(100 . "AcDbText")
		                   (cons 10 section_text_A_A)
		                   (cons 40 text_height)
		                   '(1 . "A-A")
		                   '(50 . 0.0)
		                   '(41 . 0.67)
		                   '(51 . 0.0)
		                   '(7 . "PC_TEXTSTYLE")
		                   '(71 . 0)
		                   '(72 . 1)
		                   (cons 11 section_text_A_A_end)
		                   '(210 0.0 0.0 1.0)
		                   '(100 . "AcDbText")
		                   '(73 . 0)))
)
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;;;;;;;;加强板放大视图名字标注函数；；；；；
(defun repdrfrtnamelead(pt1 doorscale / strength_Door_R strength_Door_L strength_Door_ViewNamePt strength_Door_ViewScalePt stairsname stairsname_en)
  (setq strength_Door_R (polar pt1 0 (* 60 mscale))
	strength_Door_L (polar pt1 pi (* 60 mscale))
	strength_Door_ViewNamePt (polar pt1 (/ pi 2) (* 1.5 mscale))
	strength_Door_ViewNamePt_en (polar pt1 (* pi 1.5) (* 6.5 mscale))
	strength_Door_ViewScalePt (polar strength_Door_ViewNamePt_en (* pi 1.5) (* 6.5 mscale)))
  (if 
    (or(= topfltype "V12_TopFlange")(= topfltype "V12_TopFlange_250"))
	(setq stairsname "入口梯模块")
	(setq stairsname "外爬梯总成")
  );end if
  (command "layer" "M" "6文字层" "")
  (command "text" "C" strength_Door_ViewNamePt (* 5 mscale) 0 (strcat "塔筒门洞（去除" stairsname "）正视图"))
  (command "text" "C" strength_Door_ViewNamePt_en (* 5 mscale) 0 "Front view of tower door(Entrance stair assembly excluded)")
  (command "text" "C" strength_Door_ViewScalePt (* 5 mscale) 0 (strcat "1:"(rtos doorscale)))
  (command "layer" "M" "2细线层" "")
  (command "line" strength_Door_L strength_Door_R "")
)
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


;;;;****************************塔架门洞生成;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defun generaldoorfront (locationpt scale geframemass doorH D Thick h1 b1 h2 bst hst isdim /
			 door_up_pt dimscale DoorPoint en obj H Line_R Line_L ViewNamePt ViewNamePt_en ViewScalePt TitlePt);普通门框
  (setq H doorH);门框位置
  (if (or (= H nil) (= H " "))
    (setq H 0);当为加强板门框是为0
  )
  (setq D (* D scale);塔筒外径
	Thick (* Thick scale);塔筒壁厚
    	h1 (* h1 scale);门框总高度
	b1 (* b1 scale);门框宽度
	h2 (* h2 scale);直边长度
	bst (* bst scale);门框厚度
	hst (* hst scale);门框侧视图宽度
  )
  (setq DoorPoint locationpt
	DP_up (polar DoorPoint (* pi 0.5) (/ h2 2))
	DP_down (polar DoorPoint (* pi 1.5) (/ h2 2))
	halfLAxis (/ (- h1 h2) 2);椭圆的长半轴长
	halfSAxis (/ b1 2);椭圆短半轴长
	ratio (/ halfSAxis halfLAxis)
	halfLAxis2 (- halfLAxis bst);减去门框宽度，门框内径椭圆长半轴长
	halfSAxis2 (- halfSAxis bst)
	ratio2 (/ halfSAxis2 halfLAxis2)
	pt1 (polar DoorPoint 0 (/ (- b1 (* bst 2)) 2))
	pt2 (polar pt1 0 bst)
	pt3 (polar pt1 (/ pi 2) (/ h2 2))
	pt5 (polar pt3 (* pi 1.5) h2)
	pt4 (polar pt2 (/ pi 2) (/ h2 2))
	pt6 (polar pt4 (* pi 1.5)h2)
	pt7 (polar DoorPoint pi (/(- b1 (* bst 2)) 2))
	pt8 (polar pt7 pi bst)
	pt9 (polar pt7 (/ pi 2) (/ h2 2))
	pt11 (polar pt9 (* pi 1.5) h2)
	pt10 (polar pt8 (/ pi 2) (/ h2 2))
	pt12 (polar pt10 (* pi 1.5) h2)
  )
  ;;;;
  (entmake (list '(0 . "ELLIPSE")
		 '(100 . "AcDbEntity")
		 '(100 . "AcDbEllipse")
		 (cons 10 DP_up)
		 (cons 11 (list 0 halfLAxis 0))
		 (cons 40 ratio)
		 (cons 41 (sk_el_ang->Par(ang->rad -90) ratio))
		 (cons 42 (sk_el_ang->Par(ang->rad 90) ratio))
		 (cons 8 "1轮廓实线层")
	   )
  )
  (entmake (list '(0 . "line") (cons 10 pt3) (cons 11 pt5)(cons 8 "1轮廓实线层")))
  (entmake (list '(0 . "line") (cons 10 pt4) (cons 11 pt6)(cons 8 "1轮廓实线层")))
  (entmake (list '(0 . "line") (cons 10 pt9) (cons 11 pt11)(cons 8 "1轮廓实线层")))
  (entmake (list '(0 . "line") (cons 10 pt10) (cons 11 pt12)(cons 8 "1轮廓实线层")))
  (entmake (list '(0 . "ELLIPSE")
		 '(100 . "AcDbEntity")
		 '(100 . "AcDbEllipse")
		 (cons 10 DP_down)
		 (cons 11 (list 0 halfLAxis 0))
		 (cons 40 ratio)
		 (cons 41 (sk_el_ang->Par(ang->rad 90) ratio))
		 (cons 42 (sk_el_ang->Par(ang->rad 270) ratio))
		 (cons 8"1轮廓实线层")
	   )
  )
  (entmake (list '(0 . "ELLIPSE")
		 '(100 . "AcDbEntity")
		 '(100 . "AcDbEllipse")
		 (cons 10 DP_up)
		 (cons 11 (list 0 halfLAxis2 0))
		 (cons 40 ratio2)
		 (cons 41 (sk_el_ang->Par(ang->rad -90) ratio2))
		 (cons 42 (sk_el_ang->Par(ang->rad 90) ratio2))
		 (cons 8"1轮廓实线层")
		 )
	   )
  (entmake (list '(0 . "ELLIPSE")
		 '(100 . "AcDbEntity")
		 '(100 . "AcDbEllipse")
		 (cons 10 DP_down)
		 (cons 11 (list 0 halfLAxis2 0))
		 (cons 40 ratio2)
		 (cons 41 (sk_el_ang->Par(ang->rad 90) ratio2))
		 (cons 42 (sk_el_ang->Par(ang->rad 270) ratio2))
		 (cons 8"1轮廓实线层")
		 )
	   )
  (entmake (list '(0 . "line") (cons 10 (polar pt10 pi bst)) (cons 11 (polar pt4 0 bst))(cons 8 "3中心线层")))
  (entmake (list '(0 . "line") (cons 10 (polar pt12 pi bst)) (cons 11 (polar pt6 0 bst))(cons 8 "3中心线层")))
  (entmake (list '(0 . "line") (cons 10 (polar pt8 pi bst)) (cons 11 (polar pt2 0 bst))(cons 8 "3中心线层")))
  (entmake (list '(0 . "line") (cons 10 (polar DP_up (/ pi 2) (+ halfLAxis bst)))
		 (cons 11 (polar DP_down (* pi 1.5) (+ halfLAxis bst)))(cons 8"3中心线层")))
  (if (= isdim T);如果标注
    (progn
      (setq
	door_up_pt (polar DP_up (* pi 0.5) halfLAxis)
	door_down_pt (polar DP_down (* pi 1.5) halfLAxis)
      	Et1(ptOnEllipse DoorPoint halfLAxis halfSAxis 2 (/ h2 2));焊接符号的8个点
	Et2(ptOnEllipse DoorPoint halfLAxis halfSAxis -2(/ h2 2))	
	Et3(ptOnEllipse DoorPoint halfLAxis2 halfSAxis2 2(/ h2 2))
	Et4(ptOnEllipse DoorPoint halfLAxis2 halfSAxis2 -2(/ h2 2))
	Et5(ptOnEllipse DoorPoint halfLAxis2 halfSAxis2 178 (/ h2 -2))
	Et6(ptOnEllipse DoorPoint halfLAxis2 halfSAxis2 182(/ h2 -2))
	Et7(ptOnEllipse DoorPoint halfLAxis halfSAxis 178(/ h2 -2))
	Et8(ptOnEllipse DoorPoint halfLAxis halfSAxis 182(/ h2 -2))
      )
      (entmake (list '(0 . "HATCH")'(100 . "AcDbEntity")'(67 . 0)'(410 . "Model")
		 (cons 8 "5剖面线层")'(100 . "AcDbHatch")'(10 0.0 0.0 0.0) '(210 0.0 0.0 1.0)
		 '(2 . "SOLID")'(70 . 1) '(71 . 0)'(91 . 1)'(92 . 3)'(72 . 0) '(73 . 1)
		 '(93 . 4) (cons 10 Et1) (cons 10 Et2) (cons 10 Et3) (cons 10 Et4)
		 '(97 . 0) '(75 . 0) '(76 . 1) '(98 . 0) )
	   )
      (entmake (list '(0 . "HATCH")'(100 . "AcDbEntity")'(67 . 0)'(410 . "Model")
		 (cons 8 "5剖面线层")'(100 . "AcDbHatch")'(10 0.0 0.0 0.0) '(210 0.0 0.0 1.0)
		 '(2 . "SOLID")'(70 . 1) '(71 . 0)'(91 . 1)'(92 . 3)'(72 . 0) '(73 . 1)
		 '(93 . 4) (cons 10 Et5) (cons 10 Et6) (cons 10 Et7) (cons 10 Et8)
		 '(97 . 0) '(75 . 0) '(76 . 1) '(98 . 0) )
	   )
      ;(setvar "dimlfac" (/ 1.0 scale))
      ;门框总高度
      ;(command "dimlinear" door_up_pt door_down_pt "v" (polar door_up_pt pi (* 0.8 h2)))
      (setq dimscale (/ 1.0 scale));放大反比例
      (ldimv2 door_up_pt door_down_pt dimscale 0 (- (* 1.1 b1)))
      ;门框直边长度
      ;(command "dimlinear" pt10 pt12 "v" (polar pt8 pi (* 0.25 h2)))
      (ldimv2 pt10 pt12 dimscale 0 (- (/ b1 3.2)))
      ;门框半直边长度
      ;(command "dimlinear" pt10 pt8 "v" (polar pt8 pi (* 0.15 h2)))
      (ldimv2 pt10 pt8 dimscale 0 (- (/ b1 8)))
      ;门框宽度
      (dimh pt12 pt6 dimscale (- (* 0.9 h2)) 0 0)
      ;(command "dimlinear" pt12 pt6 (polar door_down_pt (* pi 1.5) (* 0.25 h2)))generaldoorfront
      ;门框厚度
      (dimh pt1 pt2 dimscale (* 0.5 halfSAxis) (* 0.5 halfSAxis) 0)
      ;(command "dimlinear" pt1 pt2 (polar pt2 (/ pi 4) (* 0.5 halfSAxis)))
      ;(setvar "dimlfac" 1.0)
      ;焊接符号标注
      (command "insert" "DoorWeld" "S" 1 (polar DoorPoint (/ pi 2) (/ h1 2)) "")
      ;普通门框标注
      (generaldoorfrontlead DoorPoint halfLAxis halfSAxis geframemass)


      (setq TitlePt (polar door_up_pt (/ pi 2)  (* 40 mscale)) )
      
  (setq Line_R (polar TitlePt 0 (* 60 mscale)))
  (setq Line_L (polar TitlePt pi (* 60 mscale)))
  (command "layer" "M" "7标注层" "")
  (command "line" Line_L line_R "")
  (setq ViewNamePt (polar TitlePt (/ pi 2) (* 1.5 mscale)))
  (setq ViewNamePt_en (polar TitlePt (* pi 1.5) (* 6.5 mscale)))
  (setq ViewScalePt (polar ViewNamePt_en (* pi 1.5) (* 6.5 mscale)))
  (command "layer" "M" "6文字层" "")
  (command "text" "C" ViewNamePt (* 5 mscale) 0 "门框(去除入口梯子总成)正视图")
  (command "text" "C" ViewNamePt_en (* 5 mscale) 0 "Front view of tower door(Entrance stair assembly excluded)")
  (command "text" "C" ViewScalePt (* 5 mscale) 0 (strcat "1:"(rtos (/ mscale scale))))
  (command "layer" "M" "1轮廓实现层" "")
    )
  )
);;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;;;****************************塔架门洞生成;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defun generaldoorleft (locationpt scale doorH D Thick h1 b1 h2 bst hst isdim / en obj H);普通门框左视图
  (setq H doorH);门框位置
  (if (or (= H nil) (= H " "))
    (setq H 0);当为加强板门框是为0
  )
  (setq D (* D scale);塔筒外径
	Thick (* Thick scale);塔筒壁厚
    	h1 (* h1 scale);门框总高度
	b1 (* b1 scale);门框宽度
	h2 (* h2 scale);直边长度
	bst (* bst scale);门框厚度
	hst (* hst scale);门框宽度
  )
  (setq door_down_pt locationpt
	PRt1 (polar door_down_pt 0 b1);侧视图离门框中心的距离
	PRt2 (polar PRt1 0 hst)
	PRt3 (polar PRt1 (/ pi 2) h1)
	PRt4 (polar PRt3 0 hst)
	PRt7 (polar PRt1 (/ pi 2) bst)
	PRt8 (polar PRt7 0 hst)
	PRt5 (polar PRt3 (* pi 1.5) bst)
	PRt6 (polar PRt4 (* pi 1.5) bst)
	DoorMiddleP1 (MiddlePoint PRt3 PRt1)
	DoorMiddleP2 (MiddlePoint PRt4 PRt2)
  )
  (entmake (list '(0 . "line") (cons 10 PRt3) (cons 11 PRt4)(cons 8"1轮廓实线层")))
  (entmake (list '(0 . "line") (cons 10 PRt3) (cons 11 PRt1)(cons 8"1轮廓实线层")))
  (entmake (list '(0 . "line") (cons 10 PRt4) (cons 11 PRt2)(cons 8"1轮廓实线层")))
  (entmake (list '(0 . "line") (cons 10 PRt1) (cons 11 PRt2)(cons 8"1轮廓实线层")))
  (entmake (list '(0 . "line") (cons 10 PRt5) (cons 11 PRt6)(cons 8"1轮廓实线层")))
  (entmake (list '(0 . "line") (cons 10 PRt7) (cons 11 PRt8)(cons 8"1轮廓实线层")))
  (entmake (list '(0 . "line") (cons 10 (polar DoorMiddleP1 pi bst)) (cons 11 (polar DoorMiddleP2 0 bst))(cons 8 "3中心线层")))
  (entmake (list '(0 . "HATCH")
		 '(100 . "AcDbEntity")
		 '(67 . 0)
		 '(410 . "Model")
		 (cons 8 "5剖面线层")
		 '(100 . "AcDbHatch")
		 '(10 0.0 0.0 0.0)
		 '(210 0.0 0.0 1.0)
		 '(2 . "ANSI31")
		 '(70 . 0)
		 '(71 . 0)
		 '(91 . 1)
		 '(92 . 3)
		 '(72 . 0)
		 '(73 . 1)
		 '(93 . 4)
		 (cons 10 PRt1)
		 (cons 10 PRt2)
		 (cons 10 PRt8)
		 (cons 10 PRt7)
		 '(97 . 0)
		 '(75 . 0)
		 '(76 . 1)
		 '(52 . 0.0)
		 ;'(41 . 5.0)
		 '(41 . 50.0)
		 '(77 . 0)
		 '(78 . 1)
		 '(53 . 0.785398)
		 '(43 . 0.0)
		 '(44 . 0.0)
		 '(45 . -11.2253)
		 '(46 . 11.2253)
		 '(79 . 0)
		 '(98 . 0)
		 
		 )
	   )
  (entmake (list '(0 . "HATCH")
		 '(100 . "AcDbEntity")
		 '(67 . 0)
		 '(410 . "Model")
		 (cons 8 "5剖面线层")
		 '(100 . "AcDbHatch")
		 '(10 0.0 0.0 0.0)
		 '(210 0.0 0.0 1.0)
		 '(2 . "ANSI31")
		 '(70 . 0)
		 '(71 . 0)
		 '(91 . 1)
		 '(92 . 3)
		 '(72 . 0)
		 '(73 . 1)
		 '(93 . 4)
		 (cons 10 PRt5)
		 (cons 10 PRt6)
		 (cons 10 PRt4)
		 (cons 10 PRt3)
		 '(97 . 0)
		 '(75 . 0)
		 '(76 . 1)
		 '(52 . 0.0)
		 ;'(41 . 5.0)
		 '(41 . 50.0)
		 '(77 . 0)
		 '(78 . 1)
		 '(53 . 0.785398)
		 '(43 . 0.0)
		 '(44 . 0.0)
		 '(45 . -11.2253)
		 '(46 . 11.2253)
		 '(79 . 0)
		 '(98 . 0)
		 
		 )
	   )
  (if (= isdim T);如果标注
    (progn
      (setvar "dimlfac" (/ 1.0 scale))
      (command "dimlinear" PRt3 PRt4 (polar (MiddlePoint PRt3 PRt4) (* pi 0.5) (* 0.5 halfSAxis)));左视图门框宽度度
      (setvar "dimlfac" 1.0)
    )
  )

)
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;;;;;;;;;;;;;;;;;;;;;;*****加强门框正视图生成 圆角加强板;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defun replatedoorfront( locationpt scale repmass doorH D t1 hh1 t2 h2 h1 b1 isdim /
			H dimscale R pt1 pt2 pt3 pt4 pt5 pt6 pt6_c pt7 pt8 pt9 pt10 pt11
			pt12 pt12_c pt13 pt14 pt15 pt16 pt16_c pt8_2 pt7_2 pt1_2 pt2_2 repdc112_pt )
  (setq H (* doorH scale));门框位置
  (setq	D (* D scale)	;塔筒壁直径
	t1 (* t1 scale)	;塔筒壁厚度
    	hh1 (* hh1 scale) ; 补强板高度
	t2 (* t2 scale)	;补强板厚度
        h2 (* h2 scale)	;直边长度H_V
	h1 (* h1 scale)	;门洞高度H2
	b1 (* b1 scale)) ;门洞宽度w
  (setq R (* 200 scale))
  (setq	DoorPoint locationpt
	DP_up (polar DoorPoint (* pi 0.5) (/ h2 2))
	DP_down (polar DoorPoint (* pi 1.5) (/ h2 2))
	halfLAxis (/ (- h1 h2) 2);椭圆长半轴
	halfSAxis (/ b1 2);椭圆短半轴
	ratio (/ halfSAxis halfLAxis)
	pt1 (polar DoorPoint 0 (/ b1 2))
	pt3 (polar pt1 (/ pi 2) (/ h2 2))
	pt5 (polar pt3 (* pi 1.5) h2)
	pt7 (polar DoorPoint pi (/ b1 2))
	pt9 (polar pt7 (/ pi 2) (/ h2 2))
	pt11 (polar pt9 (* pi 1.5) h2)	
	width_plate (* D (sin angle_plate));加强板展开后的宽度
	plate_up_pt (polar DoorPoint (* pi 0.5) (/ hh1 2))
	pt15 (polar plate_up_pt pi (- (/ width_plate 2) R))
	pt15_c (polar pt15 (* pi 1.5) R)
	pt16 (polar plate_up_pt 0 (-(/ width_plate 2) R))
	pt16_c (polar pt16 (* pi 1.5) R)
	
	door_up_pt (polar DP_up (* pi 0.5) halfLAxis)
	door_down_pt (polar DP_down (* pi 1.5) halfLAxis)
	pt8 (polar DoorPoint pi (/ width_plate 2))
	pt10 (polar pt8 (* pi 0.5) (- (/ hh1 2) R))
	pt12 (polar pt8 (* pi 1.5) (- (/ hh1 2) R))
	pt12_c (polar pt12 0 R)
	pt2 (polar DoorPoint 0 (/ width_plate 2))
	pt4 (polar pt2 (* pi 0.5) (- (/ hh1 2) R))
	pt6 (polar pt2 (* pi 1.5) (- (/ hh1 2) R))
	pt6_c (polar pt6 pi R)
	
	plate_down_pt (polar DoorPoint (* pi 1.5) (/ hh1 2))
	pt13 (polar plate_down_pt pi (- (/ width_plate 2) R))
	pt14 (polar plate_down_pt 0 (- (/ width_plate 2) R))
  )
  (entmake (list '(0 . "ELLIPSE")
		 '(100 . "AcDbEntity")
		 '(100 . "AcDbEllipse")
		 (cons 10 DP_up)
		 (cons 11 (list 0 halfLAxis 0))
		 (cons 40 ratio)
		 (cons 41 (sk_el_ang->Par(ang->rad -90) ratio))
		 (cons 42 (sk_el_ang->Par(ang->rad 90) ratio))
		 (cons 8 "1轮廓实线层")
		 )
	   )
  (entmake (list '(0 . "line") (cons 10 pt3) (cons 11 pt5) (cons 8 "1轮廓实线层")))
  (entmake (list '(0 . "line") (cons 10 pt9) (cons 11 pt11) (cons 8 "1轮廓实线层")))
  ;(if (or (= topfltype "21_TopFlange") (= topfltype "5S_TopFlange"));21#与5s类型塔架加强门板不是对称的
  (if (= topfltype "##_TopFlange");2021年底取消了21#及5S的加强板自拼接焊缝
    (progn
      (setq pt8_2 (polar pt8 (/ pi 2) (* 140 scale)))
      (setq pt7_2 (polar pt7 (/ pi 2) (* 140 scale)))
      (setq pt1_2 (polar pt1 (/ pi 2) (* 140 scale)))
      (setq pt2_2 (polar pt2 (/ pi 2) (* 140 scale)))
      ;(entmake (list '(0 . "line") (cons 10 pt7_2) (cons 11 pt8_2) (cons 8 "1轮廓实线层")))
      ;(entmake (list '(0 . "line") (cons 10 pt1_2) (cons 11 pt2_2) (cons 8 "1轮廓实线层")))
    )
    (progn
      ;(entmake (list '(0 . "line") (cons 10 pt7) (cons 11 pt8) (cons 8 "1轮廓实线层")))
      ;(entmake (list '(0 . "line") (cons 10 pt1) (cons 11 pt2) (cons 8 "1轮廓实线层")))
    )
  );end if
  ;(entmake (list '(0 . "line") (cons 10 pt7) (cons 11 pt8) (cons 8 "1轮廓实线层")))
  ;(entmake (list '(0 . "line") (cons 10 pt1) (cons 11 pt2) (cons 8 "1轮廓实线层")))
  (entmake (list '(0 . "ELLIPSE")
		 '(100 . "AcDbEntity")
		 '(100 . "AcDbEllipse")
		 (cons 10 DP_down)
		 (cons 11 (list 0 halfLAxis 0))
		 (cons 40 ratio)
		 (cons 41 (sk_el_ang->Par(ang->rad 90) ratio))
		 (cons 42 (sk_el_ang->Par(ang->rad 270) ratio))
		 (cons 8 "1轮廓实线层")
		 )
	   )
  (entmake (list '(0 . "line") (cons 10 pt10) (cons 11 pt12)(cons 8 "1轮廓实线层")))
  (entmake (list '(0 . "line") (cons 10 pt13) (cons 11 pt14)(cons 8 "1轮廓实线层")))
  (entmake (list '(0 . "line") (cons 10 pt6) (cons 11 pt4)(cons 8 "1轮廓实线层")))
  (entmake (list '(0 . "line") (cons 10 pt16) (cons 11 pt15)(cons 8 "1轮廓实线层")))
  (entmake (list '(0 . "ARC") (cons 10 pt15_c) (cons 40 R)(cons 50 (/ pi 2))(cons 51 pi)(cons 8 "1轮廓实线层")))
  (entmake (list '(0 . "ARC") (cons 10 pt16_c) (cons 40 R)(cons 50 0)(cons 51 (/ pi 2))(cons 8 "1轮廓实线层")))
  (entmake (list '(0 . "ARC") (cons 10 pt12_c) (cons 40 R)(cons 50 pi)(cons 51 (* pi 1.5))(cons 8 "1轮廓实线层")))
  (entmake (list '(0 . "ARC") (cons 10 pt6_c) (cons 40 R)(cons 50 (* pi 1.5))(cons 51 0)(cons 8 "1轮廓实线层")))
  
  (entmake (list '(0 . "line") (cons 10 (polar pt7 pi 50)) (cons 11 (polar pt1 0 50))(cons 8 "3中心线层")))
  (entmake (list '(0 . "line") (cons 10 (polar DP_up (/ pi 2) (+ halfLAxis 50))) (cons 11 (polar DP_down (* pi 1.5) (+ halfLAxis 50)))(cons 8 "3中心线层")))
  (if (= isdim T);如果标注,即放大视图
    (progn
      (setq total_plate_h (+ H (/ hh1 2)));加强板最上面的高度
      (setq jj 0)
      ;L型法兰、T型法兰公共参数
      (setq pt_btm (polar DoorPoint (* pi 1.5) H)
	    neck_pt_T (polar pt_btm (* pi 0.5) (* (* (- (value retTower 0 2) (value retTower 0 0)) 1000) scale))
	    ptd7 (polar neck_pt_T pi (/ D 2))
	    ptd8 (polar neck_pt_T 0 (/ D 2))
      )
      (if (= (value retFlange 0 11) "T")
        (progn       ;T型法兰执行
          (setq T_D (* (value retFlange 0 13) scale))
          (if (or (= T_D nil) (= T_D ""))
	      (setq T_D (* (- (* 2 (- (value retFlange 0 1) (value retFlange 0 5))) (value retFlange 0 2)) scale))
          )
          (setq pt_T1 (polar pt_btm (* pi 0.5) (* (value retFlange 0 4) scale))
	        ptd1 (polar pt_btm pi (/ T_D 2))
                ptd2 (polar pt_btm 0 (/ T_D 2))
                ptd3 (polar pt_T1 pi (/ T_D 2))
                ptd4 (polar pt_T1 0 (/ T_D 2))
	        ptd5 (polar pt_T1 pi (/ D 2))
	        ptd6 (polar pt_T1 0 (/ D 2))
	  )
          (entmake (list '(0 . "line") (cons 10 ptd1) (cons 11 ptd2)(cons 8 "1轮廓实线层")))
          (entmake (list '(0 . "line") (cons 10 ptd3) (cons 11 ptd4)(cons 8 "1轮廓实线层")))
          (entmake (list '(0 . "line") (cons 10 ptd1) (cons 11 ptd3)(cons 8 "1轮廓实线层")))
          (entmake (list '(0 . "line") (cons 10 ptd2) (cons 11 ptd4)(cons 8 "1轮廓实线层")))
          (entmake (list '(0 . "line") (cons 10 ptd5) (cons 11 ptd7)(cons 8 "1轮廓实线层")))
          (entmake (list '(0 . "line") (cons 10 ptd6) (cons 11 ptd8)(cons 8 "1轮廓实线层")))
        )
        (progn	;L型法兰执行    
          (setq ptd1 (polar pt_btm pi (/ D 2))
                ptd2 (polar pt_btm 0 (/ D 2))    
          )
          (entmake (list '(0 . "line") (cons 10 ptd1) (cons 11 ptd2)(cons 8 "1轮廓实线层")))      
          (entmake (list '(0 . "line") (cons 10 ptd7) (cons 11 ptd8)(cons 8 "1轮廓实线层")))
          (entmake (list '(0 . "line") (cons 10 ptd1) (cons 11 ptd7)(cons 8 "1轮廓实线层")))
          (entmake (list '(0 . "line") (cons 10 ptd2) (cons 11 ptd8) (cons 8 "1轮廓实线层")))
        )
      )
      (setq jj 1
	    before_ptd_L ptd7
	    before_ptd_R ptd8)
      (while (< (* (* (- (value retTower jj 2) (value retTower 0 0)) 1000) scale) total_plate_h)
        (setq weld_height (* (* (- (value retTower jj 2) (value retTower 0 0)) 1000) scale)
	      pt_T (polar pt_btm (* pi 0.5) weld_height)
	      ptd_L (polar pt_T pi (/ (* (value retTower jj 1) scale) 2))
	      ptd_R (polar pt_T 0 (/ (* (value retTower jj 1) scale) 2))
        )
        (entmake (list '(0 . "line") (cons 10 before_ptd_L) (cons 11 ptd_L) (cons 8 "1轮廓实线层")))
        (entmake (list '(0 . "line") (cons 10 before_ptd_R) (cons 11 ptd_R) (cons 8 "1轮廓实线层")))
        (if (> weld_height (- H (/ hh1 2))) ;判断焊缝是否跨越加强版
          (progn
            (setq pt_T_L (polar pt_T pi (/ width_plate 2))
	          pt_T_R (polar pt_T 0 (/ width_plate 2))
	    )  
	    (entmake (list '(0 . "line") (cons 10 ptd_L) (cons 11 pt_T_L) (cons 8 "1轮廓实线层")))
	    (entmake (list '(0 . "line") (cons 10 ptd_R) (cons 11 pt_T_R)(cons 8 "1轮廓实线层")))	    	    
          )
          (progn;如果加强板在第一个焊缝上，
            (entmake (list '(0 . "line") (cons 10 ptd_L) (cons 11 ptd_R) (cons 8 "1轮廓实线层")))
          )
        )
        (setq jj (+ jj 1)
	      before_ptd_L ptd_L
	      before_ptd_R ptd_R)
      );循环完，但是没取到加强版上面，所以在后面再加一节塔筒段
      (setq upper_ptd_T1 (polar pt_btm (* pi 0.5) (* (* (- (value retTower jj 2) (value retTower 0 0)) 1000) scale))
            upper_ptd_L1 (polar upper_ptd_T1 pi (/ (* (value retTower jj 1) scale) 2))
	    upper_ptd_R1 (polar upper_ptd_T1 0 (/ (* (value retTower jj 1) scale) 2))
	    door_sec_height (* (* (- (value retTower jj 2) (value retTower 0 0)) 1000) scale)
      )
      (entmake (list '(0 . "line") (cons 10 before_ptd_L) (cons 11 upper_ptd_L1)(cons 8 "1轮廓实线层")))
      (entmake (list '(0 . "line") (cons 10 before_ptd_R) (cons 11 upper_ptd_R1)(cons 8 "1轮廓实线层")))
      (entmake (list '(0 . "line") (cons 10 upper_ptd_L1) (cons 11 upper_ptd_R1)(cons 8 "1轮廓实线层")))
      ;标注
      (command "layer" "M" "7标注层" "")
      (setq dimscale (/ 1.0 scale));放大反比例
      ;圆角标注
      (dimr pt16_c R (/ pi 20) dimscale 6400)
      ;筒节直径标注
      ;(dimd upper_ptd_L1 upper_ptd_R1 dimscale 900.0 0 0 );筒节直径标注
      (ldimv2 pt3 pt5 dimscale 0 1100);门洞直边长,pt1 pt2 dimscale disv dish

      ;(ldimv2 door_up_pt door_down_pt dimscale 0 3800);门洞长度,pt1 pt2 dimscale disv dish

      (dim_2 door_up_pt door_down_pt dimscale 0 (* 5300 (/ mscale 100.0)) 0 "" "2" "-0.5" "");门洞长度

      (ldimv2 pt13 pt15 dimscale 600 -900);加强板长度
      (setvar "dimlfac" (/ 1.0 scale))
      (command "dimlinear" ptd1 pt7 "v" (polar ptd1 pi 1200));放大视图中的门洞位置高度
      
      (if (or (= topfltype "21_TopFlange") (= topfltype "5S_TopFlange"));21#与5s类型塔架加强门板不是对称的
	(progn
          (command "dimlinear" pt8 pt8_2 "v" (polar ptd1 pi 900));加强门板拼接焊缝的位置标注
	  (setq repdc112_pt (polar DoorPoint (/ pi 2) (* 140 scale)));dc112的原点
	  ;(command "insert" "repdc112" "S" 1 repdc112_pt "");;插入dc112符号
	);end progn
	(progn
	  ;(command "insert" "repdc112" "S" 1 DoorPoint "");;插入dc112符号
	);end progn
      );end if
      
      ;门洞宽度
      ;(dimh pt9 pt3 dimscale 1440 0 0)
      (dim_2 pt9 pt3 dimscale (* 3780 (/ mscale 100.0)) (* 0 (/ mscale 100.0)) 0 "" "2" "-0.5" "");门洞宽度
      
      (setvar "dimlfac" 1.0)
      (command "insert" "sectionA" "S" 1 DoorPoint "");插入A向视图符号
      ;加强板半椭圆符号标注
      (repellipselead DoorPoint halfLAxis halfSAxis)
      ;加强板质量标注
      (setq masspt (polar pt16_c pi (/ width_plate 4)));标注符号的原点
      (repmasslead masspt repmass)
	  
    ) 
  );if的右括号

)
;;;;;;;;;;;;;;;;;;;;;直角加强板门洞;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defun replatedoorfront_1( locationpt scale repmass doorH D t1 hh1 t2 h2 h1 b1 isdim /
			H dimscale R pt1 pt2 pt3 pt4 pt5 pt6 pt6_c pt7 pt8 pt9 pt10 pt11
			pt12 pt12_c pt13 pt14 pt15 pt16 pt16_c pt8_2 pt7_2 pt1_2 pt2_2 repdc112_pt )
	(setq 	H (* doorH scale));门框位置
	(setq 	D (* D scale)	;塔筒壁直径
			t1 (* t1 scale)	;塔筒壁厚度
			hh1 (* hh1 scale) ; 补强板高度
			t2 (* t2 scale)	;补强板厚度
			h2 (* h2 scale)	;直边长度H_V
			h1 (* h1 scale)	;门洞高度H2
			b1 (* b1 scale) ;门洞宽度
	) ;门洞宽度w
	(setq R (* 0 scale))
	(setq	DoorPoint locationpt
			DP_up (polar DoorPoint (* pi 0.5) (/ h2 2))
			DP_down (polar DoorPoint (* pi 1.5) (/ h2 2))
			halfLAxis (/ (- h1 h2) 2);椭圆长半轴
			halfSAxis (/ b1 2);椭圆短半轴
			ratio (/ halfSAxis halfLAxis)
			pt1 (polar DoorPoint 0 (/ b1 2))
			pt3 (polar pt1 (/ pi 2) (/ h2 2))
			pt5 (polar pt3 (* pi 1.5) h2)
			pt7 (polar DoorPoint pi (/ b1 2))
			pt9 (polar pt7 (/ pi 2) (/ h2 2))
			pt11 (polar pt9 (* pi 1.5) h2)	
			width_plate (* D (sin angle_plate));加强板展开后的宽度
			plate_up_pt (polar DoorPoint (* pi 0.5) (/ hh1 2))
			pt15 (polar plate_up_pt pi (- (/ width_plate 2) R))
			pt15_c (polar pt15 (* pi 1.5) R)
			pt16 (polar plate_up_pt 0 (-(/ width_plate 2) R))
			pt16_c (polar pt16 (* pi 1.5) R)
			
			door_up_pt (polar DP_up (* pi 0.5) halfLAxis)
			door_down_pt (polar DP_down (* pi 1.5) halfLAxis)
			pt8 (polar DoorPoint pi (/ width_plate 2))
			pt10 (polar pt8 (* pi 0.5) (- (/ hh1 2) R))
			pt12 (polar pt8 (* pi 1.5) (- (/ hh1 2) R))
			pt12_c (polar pt12 0 R)
			pt2 (polar DoorPoint 0 (/ width_plate 2))
			pt4 (polar pt2 (* pi 0.5) (- (/ hh1 2) R))
			pt6 (polar pt2 (* pi 1.5) (- (/ hh1 2) R))
			pt6_c (polar pt6 pi R)
			
			plate_down_pt (polar DoorPoint (* pi 1.5) (/ hh1 2))
			pt13 (polar plate_down_pt pi (- (/ width_plate 2) R))
			pt14 (polar plate_down_pt 0 (- (/ width_plate 2) R))
	)
	(entmake (list '(0 . "ELLIPSE")
				'(100 . "AcDbEntity")
				'(100 . "AcDbEllipse")
				(cons 10 DP_up)
				(cons 11 (list 0 halfLAxis 0))
				(cons 40 ratio)
				(cons 41 (sk_el_ang->Par(ang->rad -90) ratio))
				(cons 42 (sk_el_ang->Par(ang->rad 90) ratio))
				(cons 8 "1轮廓实线层"))
	)
	(entmake (list '(0 . "line") (cons 10 pt3) (cons 11 pt5) (cons 8 "1轮廓实线层")))
	(entmake (list '(0 . "line") (cons 10 pt9) (cons 11 pt11) (cons 8 "1轮廓实线层")))
	;(if (or (= topfltype "21_TopFlange") (= topfltype "5S_TopFlange"));21#与5s类型塔架加强门板不是对称的
	(if (= topfltype "##_TopFlange");2021年底取消了21#及5S的加强板自拼接焊缝
		(progn
			(setq pt8_2 (polar pt8 (/ pi 2) (* 140 scale)))
			(setq pt7_2 (polar pt7 (/ pi 2) (* 140 scale)))
			(setq pt1_2 (polar pt1 (/ pi 2) (* 140 scale)))
			(setq pt2_2 (polar pt2 (/ pi 2) (* 140 scale)))
			;(entmake (list '(0 . "line") (cons 10 pt7_2) (cons 11 pt8_2) (cons 8 "1轮廓实线层")))
			;(entmake (list '(0 . "line") (cons 10 pt1_2) (cons 11 pt2_2) (cons 8 "1轮廓实线层")))
		)
		(progn
			;(entmake (list '(0 . "line") (cons 10 pt7) (cons 11 pt8) (cons 8 "1轮廓实线层")))
			;(entmake (list '(0 . "line") (cons 10 pt1) (cons 11 pt2) (cons 8 "1轮廓实线层")))
		)
	)
	;(entmake (list '(0 . "line") (cons 10 pt7) (cons 11 pt8) (cons 8 "1轮廓实线层")))
	;(entmake (list '(0 . "line") (cons 10 pt1) (cons 11 pt2) (cons 8 "1轮廓实线层")))
	(entmake (list '(0 . "ELLIPSE")
		 '(100 . "AcDbEntity")
		 '(100 . "AcDbEllipse")
		 (cons 10 DP_down)
		 (cons 11 (list 0 halfLAxis 0))
		 (cons 40 ratio)
		 (cons 41 (sk_el_ang->Par(ang->rad 90) ratio))
		 (cons 42 (sk_el_ang->Par(ang->rad 270) ratio))
		 (cons 8 "1轮廓实线层"))
	)
	(entmake (list '(0 . "line") (cons 10 pt10) (cons 11 pt12)(cons 8 "1轮廓实线层")))
	(entmake (list '(0 . "line") (cons 10 pt13) (cons 11 pt14)(cons 8 "1轮廓实线层")))
	(entmake (list '(0 . "line") (cons 10 pt6) (cons 11 pt4)(cons 8 "1轮廓实线层")))
	(entmake (list '(0 . "line") (cons 10 pt16) (cons 11 pt15)(cons 8 "1轮廓实线层")))
	(entmake (list '(0 . "ARC") (cons 10 pt15_c) (cons 40 R)(cons 50 (/ pi 2))(cons 51 pi)(cons 8 "1轮廓实线层")))
	(entmake (list '(0 . "ARC") (cons 10 pt16_c) (cons 40 R)(cons 50 0)(cons 51 (/ pi 2))(cons 8 "1轮廓实线层")))
	(entmake (list '(0 . "ARC") (cons 10 pt12_c) (cons 40 R)(cons 50 pi)(cons 51 (* pi 1.5))(cons 8 "1轮廓实线层")))
	(entmake (list '(0 . "ARC") (cons 10 pt6_c) (cons 40 R)(cons 50 (* pi 1.5))(cons 51 0)(cons 8 "1轮廓实线层")))
  
	(entmake (list '(0 . "line") (cons 10 (polar pt7 pi 50)) (cons 11 (polar pt1 0 50))(cons 8 "3中心线层")))
	(entmake (list '(0 . "line") (cons 10 (polar DP_up (/ pi 2) (+ halfLAxis 50))) (cons 11 (polar DP_down (* pi 1.5) (+ halfLAxis 50)))(cons 8 "3中心线层")))
	(if (= isdim T);如果标注,即放大视图
		(progn
			(setq total_plate_h (+ H (/ hh1 2)));加强板最上面的高度
			(setq jj 0)
			;L型法兰、T型法兰公共参数
			(setq pt_btm (polar DoorPoint (* pi 1.5) H)
				neck_pt_T (polar pt_btm (* pi 0.5) (* (* (- (value retTower 0 2) (value retTower 0 0)) 1000) scale))
				ptd7 (polar neck_pt_T pi (/ D 2))
				ptd8 (polar neck_pt_T 0 (/ D 2))
			)
			(if (= (value retFlange 0 11) "T")
				(progn       ;T型法兰执行
					(setq T_D (* (value retFlange 0 13) scale))
					(if (or (= T_D nil) (= T_D ""))
						(setq T_D (* (- (* 2 (- (value retFlange 0 1) (value retFlange 0 5))) (value retFlange 0 2)) scale))
					)
					(setq pt_T1 (polar pt_btm (* pi 0.5) (* (value retFlange 0 4) scale))
						ptd1 (polar pt_btm pi (/ T_D 2))
							ptd2 (polar pt_btm 0 (/ T_D 2))
							ptd3 (polar pt_T1 pi (/ T_D 2))
							ptd4 (polar pt_T1 0 (/ T_D 2))
						ptd5 (polar pt_T1 pi (/ D 2))
						ptd6 (polar pt_T1 0 (/ D 2))
					)
					(entmake (list '(0 . "line") (cons 10 ptd1) (cons 11 ptd2)(cons 8 "1轮廓实线层")))
					(entmake (list '(0 . "line") (cons 10 ptd3) (cons 11 ptd4)(cons 8 "1轮廓实线层")))
					(entmake (list '(0 . "line") (cons 10 ptd1) (cons 11 ptd3)(cons 8 "1轮廓实线层")))
					(entmake (list '(0 . "line") (cons 10 ptd2) (cons 11 ptd4)(cons 8 "1轮廓实线层")))
					(entmake (list '(0 . "line") (cons 10 ptd5) (cons 11 ptd7)(cons 8 "1轮廓实线层")))
					(entmake (list '(0 . "line") (cons 10 ptd6) (cons 11 ptd8)(cons 8 "1轮廓实线层")))
				)
				(progn	;L型法兰执行    
					(setq ptd1 (polar pt_btm pi (/ D 2))
						ptd2 (polar pt_btm 0 (/ D 2))    
					)
					(entmake (list '(0 . "line") (cons 10 ptd1) (cons 11 ptd2)(cons 8 "1轮廓实线层")))      
					(entmake (list '(0 . "line") (cons 10 ptd7) (cons 11 ptd8)(cons 8 "1轮廓实线层")))
					(entmake (list '(0 . "line") (cons 10 ptd1) (cons 11 ptd7)(cons 8 "1轮廓实线层")))
					(entmake (list '(0 . "line") (cons 10 ptd2) (cons 11 ptd8) (cons 8 "1轮廓实线层")))
				)
			)
			(setq jj 1
				before_ptd_L ptd7
				before_ptd_R ptd8
			)
			(while (< (* (* (- (value retTower jj 2) (value retTower 0 0)) 1000) scale) total_plate_h)
				(setq weld_height (* (* (- (value retTower jj 2) (value retTower 0 0)) 1000) scale)
					pt_T (polar pt_btm (* pi 0.5) weld_height)
					ptd_L (polar pt_T pi (/ (* (value retTower jj 1) scale) 2))
					ptd_R (polar pt_T 0 (/ (* (value retTower jj 1) scale) 2))
				)
				(entmake (list '(0 . "line") (cons 10 before_ptd_L) (cons 11 ptd_L) (cons 8 "1轮廓实线层")))
				(entmake (list '(0 . "line") (cons 10 before_ptd_R) (cons 11 ptd_R) (cons 8 "1轮廓实线层")))
				(if (> weld_height (- H (/ hh1 2))) ;判断焊缝是否跨越加强版
					(progn
						(setq pt_T_L (polar pt_T pi (/ width_plate 2))
							pt_T_R (polar pt_T 0 (/ width_plate 2))
						)  
						(entmake (list '(0 . "line") (cons 10 ptd_L) (cons 11 pt_T_L) (cons 8 "1轮廓实线层")))
						(entmake (list '(0 . "line") (cons 10 ptd_R) (cons 11 pt_T_R)(cons 8 "1轮廓实线层")))	    	    
					)
					(progn ;如果加强板在第一个焊缝上，
						(entmake (list '(0 . "line") (cons 10 ptd_L) (cons 11 ptd_R) (cons 8 "1轮廓实线层")))
					)
				)
				(setq jj (+ jj 1)
					before_ptd_L ptd_L
					before_ptd_R ptd_R
				)
			);循环完，但是没取到加强版上面，所以在后面再加一节塔筒段
			(setq upper_ptd_T1 (polar pt_btm (* pi 0.5) (* (* (- (value retTower jj 2) (value retTower 0 0)) 1000) scale))
				upper_ptd_L1 (polar upper_ptd_T1 pi (/ (* (value retTower jj 1) scale) 2))
				upper_ptd_R1 (polar upper_ptd_T1 0 (/ (* (value retTower jj 1) scale) 2))
				door_sec_height (* (* (- (value retTower jj 2) (value retTower 0 0)) 1000) scale)
			)
			(entmake (list '(0 . "line") (cons 10 before_ptd_L) (cons 11 upper_ptd_L1)(cons 8 "1轮廓实线层")))
			(entmake (list '(0 . "line") (cons 10 before_ptd_R) (cons 11 upper_ptd_R1)(cons 8 "1轮廓实线层")))
			(entmake (list '(0 . "line") (cons 10 upper_ptd_L1) (cons 11 upper_ptd_R1)(cons 8 "1轮廓实线层")))
			;标注
			(command "layer" "M" "7标注层" "")
			(setq dimscale (/ 1.0 scale));放大反比例
			;圆角标注
			;(dimr pt16_c R (/ pi 20) dimscale 6400)
			;筒节直径标注
			;(dimd upper_ptd_L1 upper_ptd_R1 dimscale 900.0 0 0 );筒节直径标注
			(ldimv2 pt3 pt5 dimscale 0 1100);门洞直边长,pt1 pt2 dimscale disv dish

			;(ldimv2 door_up_pt door_down_pt dimscale 0 3800);门洞长度,pt1 pt2 dimscale disv dish

			(dim_2 door_up_pt door_down_pt dimscale 0 (* 5300 (/ mscale 100.0)) 0 "" "2" "-0.5" "");门洞长度

			(ldimv2 pt13 pt15 dimscale 600 -900);加强板长度
			(setvar "dimlfac" (/ 1.0 scale))
			(command "dimlinear" ptd1 pt7 "v" (polar ptd1 pi 1200));放大视图中的门洞位置高度
		  
			(if (or (= topfltype "21_TopFlange") (= topfltype "5S_TopFlange"));21#与5s类型塔架加强门板不是对称的
				(progn
					(command "dimlinear" pt8 pt8_2 "v" (polar ptd1 pi 900));加强门板拼接焊缝的位置标注
					(setq repdc112_pt (polar DoorPoint (/ pi 2) (* 140 scale)));dc112的原点
					;(command "insert" "repdc112" "S" 1 repdc112_pt "");;插入dc112符号
				)
				(progn
				  ;(command "insert" "repdc112" "S" 1 DoorPoint "");;插入dc112符号
				)
			);end if
				  
			;门洞宽度
			;(dimh pt9 pt3 dimscale 1440 0 0)
			(dim_2 pt9 pt3 dimscale (* 3780 (/ mscale 100.0)) (* 0 (/ mscale 100.0)) 0 "" "2" "-0.5" "");门洞宽度
				  
			(setvar "dimlfac" 1.0)
			(command "insert" "sectionA" "S" 1 DoorPoint "");插入A向视图符号
			;加强板半椭圆符号标注
			(repellipselead DoorPoint halfLAxis halfSAxis)
			;加强板质量标注
			(setq masspt (polar pt16_c pi (/ width_plate 4)));标注符号的原点
			(repmasslead masspt repmass) 
		) 
	)
)
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


;;;;;;;;;;;;;;;;;;;;;;;*****加强门框俯视图生成;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defun replatedoortop(locationpt scale doorH D t1 hh1 t2 h2 h1 b1 isdim / H revit_scale)
  (setq H doorH);门框位置
  (if (or (= H nil) (= H " "))
    (setq H 0)
  )
  (setq	D (* D scale)	;塔筒壁直径
	t1 (* t1 scale)	;塔筒壁厚度
    	hh1 (* hh1 scale) ; 补强板高度
	t2 (* t2 scale)	;补强板厚度
        h2 (* h2 scale)	;直边长度H_V
	h1 (* h1 scale)	;门洞高度H2
	b1 (* b1 scale);门洞宽度w
  ) 
  (setq startang_plate_left  (-(* 1.5 pi )  angle_plate)  ) ;加强版俯视图左侧起始角度
  (setq startang_plate_right (+(* 1.5 pi )  angle_plate)  ) ;加强版俯视图右侧终止角度
  (setq secPoint locationpt
	fpt1 (polar secPoint startang_plate_left   (/ (- D (* 2 t1)) 2))
	fpt2 (polar secPoint startang_plate_left   (/ D 2))
	fpt3 (polar secPoint startang_plate_right  (/ (- D (* 2 t1)) 2))
	fpt4 (polar secPoint startang_plate_right  (/ D 2))
	
	angle1_frame_thick (- (/ pi 2) (atan 0.25) (* angle_plate 2))
	angle2_frame_thick (- (/ pi 2) (atan 0.25) angle_plate)
	L (* (/ (- t2 t1) 2) (sqrt 17))
	
	angle_L1 (+ startang_plate_left  (angle_b (+ (* angle_plate 2) angle1_frame_thick) L (/ (- D (+ t2 t1))2.0)))
  	angle_R1 (- startang_plate_right (angle_b (+ (* angle_plate 2) angle1_frame_thick) L (/(- D (+ t2 t1))2.0)))
  	angle_L2 (+ startang_plate_left  (angle_b (- (/ startang_plate_right 2) angle2_frame_thick) L (/(+ D (- t2 t1))2.0)))
 	angle_R2 (- startang_plate_right (angle_b (- (/ startang_plate_right 2) angle2_frame_thick) L (/(+ D (- t2 t1))2.0)))
	fpt11(polar secPoint angle_L1 (/ (- D (+ t2 t1)) 2.0))
	fpt12(polar secPoint angle_R1 (/ (- D (+ t2 t1)) 2.0))
	fpt13(polar secPoint angle_L2 (/ (+ D (- t2 t1)) 2.0))
	fpt14(polar secPoint angle_R2 (/ (+ D (- t2 t1)) 2.0))
	door_trig_inner_h(sqrt (- (* (/(- D (+ t2 t1))2.0)(/(- D (+ t2 t1))2.0)) (* (/ b1 2.0)(/ b1 2.0))))
	door_trig_outer_h(sqrt (- (* (/(+ D (- t2 t1))2.0)(/(+ D (- t2 t1))2.0)) (* (/ b1 2.0)(/ b1 2.0))))
	angle_trig_half1(atan (/ b1 2 door_trig_inner_h))
	angle_trig_half2(atan (/ b1 2 door_trig_outer_h))
	angle_frame_inner_L(- (* pi 1.5) angle_trig_half1)
	angle_frame_inner_R(+ (* pi 1.5) angle_trig_half1)
	angle_frame_outer_L(- (* pi 1.5) angle_trig_half2)
	angle_frame_outer_R(+ (* pi 1.5) angle_trig_half2)
	door_pt1(polar secPoint angle_frame_outer_L (/(+ D (- t2 t1))2.0))
	door_pt2(polar secPoint angle_frame_outer_R (/(+ D (- t2 t1))2.0))
	door_pt3(polar secPoint angle_frame_inner_L (/(- D (+ t2 t1))2.0))
	door_pt4(polar secPoint angle_frame_inner_R (/(- D (+ t2 t1))2.0))
  )
  (entmake (list '(0 . "ARC") (cons 10 secPoint) (cons 40 (/ D 2))(cons 50 startang_plate_right)(cons 51 startang_plate_left)(cons 8 "1轮廓实线层")))
  (entmake (list '(0 . "ARC") (cons 10 secPoint) (cons 40 (/ (- D (* 2 t1)) 2))(cons 50 startang_plate_right)(cons 51 startang_plate_left)(cons 8 "1轮廓实线层")))
  (entmake (list '(0 . "line") (cons 10 fpt1) (cons 11 fpt2)(cons 8 "1轮廓实线层")))
  (entmake (list '(0 . "line") (cons 10 fpt3) (cons 11 fpt4)(cons 8 "1轮廓实线层")))
  (entmake (list '(0 . "line") (cons 10 fpt1) (cons 11 fpt11)(cons 8 "1轮廓实线层")))
  (entmake (list '(0 . "line") (cons 10 fpt2) (cons 11 fpt13)(cons 8 "1轮廓实线层")))
  (entmake (list '(0 . "line") (cons 10 fpt12) (cons 11 fpt3)(cons 8 "1轮廓实线层")))
  (entmake (list '(0 . "line") (cons 10 fpt14) (cons 11 fpt4)(cons 8 "1轮廓实线层")))

  (entmake (list '(0 . "line") (cons 10 door_pt1) (cons 11 door_pt3)(cons 8 "1轮廓实线层")))
  (entmake (list '(0 . "line") (cons 10 door_pt2) (cons 11 door_pt4)(cons 8 "1轮廓实线层")))
  
  (entmake (list '(0 . "ARC") (cons 10 secPoint) (cons 40 (/ (- D (+ t2 t1 )) 2.0))(cons 50 angle_L1)(cons 51 angle_R1)(cons 8 "1轮廓实线层")))
  (entmake (list '(0 . "ARC") (cons 10 secPoint) (cons 40 (/ (+ D (- t2 t1 )) 2.0))(cons 50 angle_L2)(cons 51 angle_R2)(cons 8 "1轮廓实线层")))
  (entmake (list '(0 . "line") (cons 10 (polar secPoint 0 (+(/ D 2)100))) (cons 11 (polar secPoint pi (+(/ D 2)100)))(cons 8 "3中心线层")))
  (entmake (list '(0 . "line") (cons 10 (polar secPoint (/ pi 2) (+(/ D 2)100))) (cons 11 (polar secPoint (* pi 1.5) (+(/ D 2)100)))(cons 8 "3中心线层")))
  (bhatch_pt fpt13 door_pt1 (* scale 2) 0)
  (bhatch_pt fpt14 door_pt2 (* scale 2) 0)
  (setvar "dimlfac" (/ 1.0 scale))
  (command "dimlinear" door_pt1 door_pt2 (polar door_pt1 (* pi 1.5) 1200));A向放大视图中的门洞宽度
  (setvar "dimlfac" 1.0)
  (setq dim1 (vla-AddDimAngular myms (vlax-3d-point secPoint) (vlax-3d-point fpt1) (vlax-3d-point fpt3) (vlax-3d-point (polar secPoint (* pi 1.5) (+ (/ D 2) 3000)))));门洞角度标注
  (setq mfpt1 (MiddlePoint fpt13 door_pt1)
	    mfpt2 (MiddlePoint fpt14 door_pt2))
  (setq revit_scale (/ scale 2 ))
  (Rivet_insert secPoint revit_scale);插入短尾铆钉
  
  
  (if (= isdim T);如果标注,即放大视图
    (progn
      (alert "ok")
    )
  );if的右括号

)
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;;;;;;;;;;;;;;;;**************门洞俯视图短尾铆钉及其放大图插入********************;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defun Rivet_insert(pt_insert revit_sca);插入原点
  (if (= TheTowerSliceType "SliceTower")
       (command "insert" "revit" "S" revit_sca pt_insert "");插入铆钉块
  );end if
);end defun
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;




;;;;;;;;;;tool文件;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;*****获取图纸比例函数******;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defun GetScale (/ sourceblkobj blockname xscale mscale)
  (setq sourceblkobj (entsel "\n请选择标题栏块,获取比例:"))
  (if (null sourceblkobj)
    (exit)
  )
  (setq sourceblkobj (vlax-ename->vla-object (car sourceblkobj)))
  (if (/= "AcDbBlockReference" (vla-get-objectname sourceblkobj))
    (progn
      (princ "\n***********错误！你选择的不是标题栏块，请重新运行程序，选择图块***************\n")
      (exit)
    )
  )
  (setq blockname (vla-get-name sourceblkobj)
	xscale (vla-get-xscalefactor sourceblkobj)
  )
  (setq mscale xscale)
);defun函数Getscale的右括号
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;;;;;;;;;;默认路径写入;;;;;;;;
(defun defaultfile(tower_path / file)
	(if (/= tower_path nil)
		(progn
			(setq file (open"D:\\Program Files (x86)\\Autodesk\\AutoLisp\\dir.ini" "w"))
			(write-line tower_path file)
			(close file)
		)
	)
)
;;;;函数结束



;;;;;;;获得法兰的数量，段数;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defun flSecHqty(/ i k)
	(setq flange_qty 0)
	(setq i 0);循环变量
	(setq k (length retflange));总行数
	(setq flangeHHlist '());全局变量，
	(while (< i k)
		(if (and (/= (value retFlange i 0) "") (/= (value retFlange i 0) nil));如果不为空
			(progn 
				(setq flange_qty (+ flange_qty 1))
				(setq flangeHHlist (cons (rtos (value retFlange i 0)) flangeHHlist))
			)
		)
		(setq i (+ i 1))
	)
	(setq flangeHHlist (reverse flangeHHlist))
	(setq section_qty (- flange_qty 1))
)
;;;;;;;;;;;;;;函数结束;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;;;法兰在tower表中的位置;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defun flangepos(/ i staheight k x);上法兰
	(setq i 0)
	(setq towernum 0);tower表的行数
	(setq staheight '());standard height
	(setq k (length retTower))
	(setq pos '());法兰在tower表中的位置
	(while (< i k)
		(if (and (/= (value retTower i 0) "") (/= (value retTower i 0) nil));如果不为空
			(progn
				(setq towernum (+ towernum 1))
				(setq staheight (cons (rtos (value retTower i 0)) staheight));
				(foreach x flangeHHlist
					(if (= x (rtos (value retTower i 0)))
						(progn
							(setq pos (cons i pos))
						)
					)
				) 
			)
		)
		(setq i (+ i 1))
	)
	(setq staheight (reverse staheight))
	(setq pos (cons (- (length staheight) 1) pos));把顶法兰的位置加进去
	(setq pos (reverse pos))
)
;;;;;函数结束;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defun bnth (x listx / value)  ;得到列表倒数的值,x是负数
  (setq n (length listx));得到列表的长度
  (setq value (nth (+ n x) listx))
)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defun extract_str(str)   ; 去除斜杠
  (nth 1 (del_str_2_list str "\""));返回list中的第一个数
  )
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defun del_str_2_list (str del / pos lst) ;函数中的局部变量pos，lst
    (while
        (setq pos (vl-string-search del str));返回del在str中的位置
        (setq lst (cons (substr str 1 pos) lst)
              str (substr str (+ 1 pos (strlen del)))
        )
    )
    (reverse (cons str lst))
)

(defun extract_str2 (str)   ; 去除斜杠
  (nth 1 (del_str_2_list str "\\"));返回list中的第一个数
  )

;;;;;;确定法兰的颈高
(defun neck_h_fl_judge (L_fl tn_fl );法兰颈高判断,l_fl:法兰颈高，tn_fl:此法兰颈厚
	(if (or (= L_fl nil) (= L_fl " ") (= L_fl 0));如果颈高为空
		(progn;如果颈高为空或为0
			(if (<= tn_fl 36.0)
				(setq L_fl 40)
				(progn
					(setq L_fl (+ (* 0.885 tn_fl) 10));+圆角，这给定死了为10
					(if (> (/ L_fl 10) (atoi (rtos (/ L_fl 10) 2 0)))
						(setq L_fl (+ (* (atoi (rtos (/ L_fl 10) 2 0)) 10) 5))
						(setq L_fl (* (atoi (rtos (/ L_fl 10) 2 0)) 10))
					)
				)
			)
		)
		(progn;如果不为空
			(setq L_fl L_fl)
		)
	) 
)
;;;;;;End neck_h_fl_judge

;;;;;;;;;;;;;;;;;;;;;;;;;;*******判断excel表中法兰类型;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defun FlangeTypeJudge (tempflange_type ii / i_flange_type flange_type)
	(if (/= tempflange_type nil)
		(progn
			(if (= (strlen tempflange_type) 1)
				(setq flange_type tempflange_type)
				(progn
					(if (= (strlen tempflange_type) 0)
						(progn
							(if is_alert
								(alert "①Excel表中没法兰类型或表格模板有误，请检查！")
							)
							(print "①Excel表中没法兰类型或表格模板有误，请检查！")
							(exit)
						)
						(progn
							(setq i_flange_type 1)
							(while (<= i_flange_type (strlen tempflange_type))
								(if (/= (substr tempflange_type i_flange_type 1) " ")
									(setq flange_type (substr tempflange_type i_flange_type 1))
								)
								(setq i_flange_type (+ 1 i_flange_type))
							)
						)
					)
				)
			)
			(if (and (/= flange_type "L") (/= flange_type "T") (/= ii flange_qty))
				(progn
					(if is_alert
						(alert "②Excel表中没法兰类型有误或表格模板有误，请检查！")
					)
					(print "②Excel表中没法兰类型有误或表格模板有误，请检查！")	  
					(exit)
				)
			)
		)
		(setq flangeType "L");如果为nil，直接就为L型法兰
	)
	(setq flangeType flange_type)
)
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;;;;;;;;;;;;;;;;;;;;;;******************法兰对称点求取;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defun miFlange(fp1 dist_half)
  (polar fp1 (* pi 1.5) (* 2 dist_half))
  )
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;




;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defun ptOnEllipse(oript halfLAxis halfSAxis theta halfh2)
  (setq oript_x (car oript))
  (setq oript_y (cadr oript))
  (setq ang (ang->rad theta)
	x (* halfSAxis (sin ang))
	y (+ (* halfLAxis (cos ang)) halfh2)
  )
  (list (+ oript_x x) (+ oript_y y) 0)
  )
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;;;;;;判断法兰是不是T型法兰;;;;;;
(defun TorLflange (i / fltype);i:第n个法兰，从0开始，0是底法兰
  (if (and (and (/= (value retFlange i 13) "") (/= (value retFlange i 13) nil)) (and (/= (value retFlange i 14) "") (/= (value retFlange i 14) nil)));如果不为空，为T型法兰
    (setq fltype T)
  )
);;;;;函数结束

;;;;;;;;判断是否是加强板门框;;;;;;;;;;;
(defun ReplateDoor (/ DoorTypeR)
  (if (or (= (value retDoor 0 12) "") (= (value retDoor 0 12) nil));如果加强板为空
    (progn
      (if (and (/= (value retDoor 0 0) "") (/= (value retDoor 0 0) nil));普通门框不为空
        (setq DoorTypeR nil);普通门框
	(setq DoorTypeR "none");无门框数据
      )
    );progn
    (setq DoorTypeR T)
  );if
)
;;;;函数结束;;;;;;;;;;












;;;;;;;;;;;;;;;;;;;;;;;;;End tool文件;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;





;法兰放大图插入点
(defun FLKeyPt(/ i pt0 pt1 pt2 pt3 pt4 pt5 pt6 pt7 pt8 pt9 pt10 pt11 pt12 ptlist)
	(if (= mscale 100.0)
		(setq pt0 '(80600 116900));图框右上角的顶点
		(if (= mscale 120.0)
			  (setq pt0 '(96720 140280));图框右上角的顶点
			  (setq pt0 '(112840 163660));图框右上角的顶点,140的比例
		);end if
	);end if
	(cond
		((and (= mscale 100) (= topfltype "21_TopFlange") )
			(setq pt1 (polar pt0 pi (* 56400 (/ mscale 100.0) ) ));第一个点，原点向左偏移
			(setq pt1 (polar pt1 (* pi 1.5) (* 13000 (/ mscale 100.0) )));第一个点，向下偏移
		)
		((and (= mscale 100) (= topfltype "5S_TopFlange") )
			(setq pt1 (polar pt0 pi (* 54400 (/ mscale 100.0) ) ));第一个点，原点向左偏移
			(setq pt1 (polar pt1 (* pi 1.5) (* 13000 (/ mscale 100.0) )));第一个点，向下偏移
		)
		((and (= mscale 100) (= topfltype "5X_TopFlange") )
			(setq pt1 (polar pt0 pi (* 54400 (/ mscale 100.0) ) ));第一个点，原点向左偏移
			(setq pt1 (polar pt1 (* pi 1.5) (* 13000 (/ mscale 100.0) )));第一个点，向下偏移
		)
		(t
			(setq pt1 (polar pt0 pi (* 56400 (/ mscale 100.0) ) ));第一个点，原点向左偏移
			(setq pt1 (polar pt1 (* pi 1.5) (* 13000(/ mscale 100.0) )));第一个点，向下偏移
		)
	)
  ;(setq pt1 (polar pt0 pi (* 56400 (/ mscale 100.0) ) ));第一个点，原点向左偏移
  ;(setq pt1 (polar pt1 (* pi 1.5) (* 10000(/ mscale 100.0) )));第一个点，向下偏移
  (setq pt2 (polar pt1 0 (* 18000 (/ mscale 100.0)) ));第二个点，向右偏移
  (setq pt3 (polar pt2 0 (* 18000 (/ mscale 100.0)) ));第三个点，向右偏移
  
  ;(setq pt4 (polar pt1 (* pi 1.5) (* 14000 (/ mscale 100.0)) ));第四个点，pt1点向下偏移

  (cond
    ((and (= mscale 100) (= topfltype "5X_TopFlange") )
      (setq pt4 (polar pt1 (* pi 1.5) (* 20000 (/ mscale 100.0)) ));第四个点，pt1点向下偏移
    )
    (t
      (setq pt4 (polar pt1 (* pi 1.5) (* 14000 (/ mscale 100.0)) ));第四个点，pt1点向下偏移
    )
  )
  
  
  (setq pt5 (polar pt4 0 (* 18000 (/ mscale 100.0)) ));第五个点，向右偏移
  (setq pt6 (polar pt5 0 (* 18000 (/ mscale 100.0)) ));第六个点，向右偏移

  (setq pt7 (polar pt4 (* pi 1.5) (* 14000 (/ mscale 100.0)) ));第七个点，第三行，最左
  (setq pt8 (polar pt7 0 (* 18000 (/ mscale 100.0)) ));第8个点，向右偏移
  (setq pt9 (polar pt8 0 (* 18000 (/ mscale 100.0)) ));第9个点，向右偏移

  (setq pt10 (polar pt7 (* pi 1.5) (* 14000 (/ mscale 100.0)) ));第10个点，第四行，最左
  (setq pt11 (polar pt10 0 (* 18000 (/ mscale 100.0)) ));第11个点，向右偏移
  (setq pt12 (polar pt11 0 (* 18000 (/ mscale 100.0)) ));第12个点，向右偏移

  (setq pt13 (polar pt10 (* pi 1.5) (* 14000 (/ mscale 100.0)) ));第13个点，第五行，最左
  
  (setq ptlist (list pt1 pt2 pt3 pt4 pt5 pt6 pt7 pt8 pt9 pt10 pt1 pt12 pt13))
);;;;;End 

;法兰基础环放大视图的插入点
(defun EnlargeKeypt(/ pt1 pt2 pt3 pt4 pt5 pt6 pt7 pt8 pt9 pt10 ptlist)
  ;(setq enkeyptlist '())
      (setq dish 18000)
      (setq disv 16000)
      (setq pt0 (bnth -1 sthptlist));塔架主体最上点
      (setq pt0 (polar pt0 (* pi 1.5) 4000))
      (setq pt1 (polar pt0 0 dish ))
      (setq pt2 (polar pt1 0 dish))
      (setq pt3 (polar pt1 (* pi 1.5) disv))
      (setq pt4 (polar pt3 0 dish))
      (setq pt5 (polar pt3 (* pi 1.5) disv))
      (setq pt6 (polar pt5 0 dish))
      (setq pt7 (polar pt5 (* pi 1.5) disv))
      (setq pt8 (polar pt7 0 dish))
      (setq pt9 (polar pt7 (* pi 1.5) disv))
      (setq pt10 (polar pt9 0 dish))
      (setq ptlist (list pt1 pt2 pt3 pt4 pt5 pt6 pt7 pt8 pt9 pt10))
  (if (and (= onekeydetail T) (= mscale 100.0) )
    (progn
      (setq pt1 (polar pt1 pi 5000 ))
      (setq pt2 (polar pt2 pi 6000))
      (setq pt3 (polar pt3 pi 5000))
      (setq pt4 (polar pt4 pi 6000))
      (setq pt5 (polar pt5 pi 5000))
      (setq pt6 (polar pt6 pi 6000))
      
    )
  );End if
  (setq ptlist (list pt1 pt2 pt3 pt4 pt5 pt6 pt7 pt8 pt9 pt10))
);;;;;End EnlargeKeypt;;;;;;;

;;;;;;;序号的插入点,普通招标图的插入点
(defun xhKeypt(/ i pt pt0 pt1 pt2 ptlist)
  (setq ptlist '())
  (setq pt0 (nth 1 sthptlist));法兰最下点
  (setq pt1 (polar pt0 (/ pi 2) 200))
  (setq pt1 (polar pt1 0 400));爬梯

  
  (setq pt2 (MiddlePoint (nth 0 sthptlist) (nth (nth 1 pos) sthptlist)));升降机
  (setq pt2 (polar pt2 (/ pi 2) 300))
  (setq ptlist (list pt1 pt2))
  
  (setq i 0)
  (while (< i section_qty)
    (if (= i 0)
      (setq pt (MiddlePoint (nth (- (nth 1 pos) 3) sthptlist) (nth (nth 1 pos) sthptlist)));第一段主体的点
      (setq pt (nth (- (nth (+ i 1) pos) 5) sthptlist) )
    )
    (setq ptlist (append ptlist (list pt)))
    (setq i (+ i 1))
  )
  ;附件的坐标
  ;(setq pt3 (nth (- (nth section_qty pos) 2) sthptlist) )
  

  (setq oript3 (bnth -1 sthptlist))
  (setq oript3 (polar oript3 (* pi 1.5) 8100))
  (setq pt3 (polar oript3 pi 800))


  (setq ptlist (append ptlist (list pt3)))
  (setq ptlist ptlist)
)
;;;;;End xheKeypt;;;;;;;

;;;;;;;序号的插入点,普通招标图-V12混塔的插入点
(defun HybridTower_xhKeypt(/ i pt pt0 pt1 pt2 pt3 pt4 pt5 ptlist)
  (setq ptlist '())
  (setq pt0 (nth 1 sthptlist));法兰最下点
  (setq pt1 (polar pt0 (* pi 1.5) 33860))
  (setq pt1 (polar pt1 0 847));外爬梯
  (setq pt2 (polar pt0 (* pi 1.5) 33400))
  (setq pt2 (polar pt2 pi 1097));升降机
  (if (or (= AntiFloodType "yes")(= AntiFloodType "Yes"))
      (setq ptlist (append ptlist (list pt1) (list pt2)))
	  (setq ptlist (append ptlist (list pt2)))
  );end if 
  (setq i 0)
  (while (< i section_qty)
    (if (= i 0)
      (setq pt (MiddlePoint (nth (- (nth 1 pos) 3) sthptlist) (nth (nth 1 pos) sthptlist)));第一段主体的点
      (setq pt (nth (- (nth (+ i 1) pos) 5) sthptlist) )
    )
    (setq ptlist (append ptlist (list pt)))
    (setq i (+ i 1))
  )
  ;钢段附件的坐标
  (setq pt3 (bnth -1 sthptlist))
  (setq pt3 (polar pt3 (* pi 1.5) 8100))
  (setq pt3 (polar pt3 pi 800))
  (setq ptlist (append ptlist (list pt3)))
  
  ;混段中间平台的坐标
  (setq pt4 (polar pt0 (* pi 1.5) 15177))
  (setq pt4 (polar pt4 pi 800))
  
  (setq ptlist (append ptlist (list pt4)))
  
  ;混段附件的坐标
  (setq pt5 (polar pt0 (* pi 1.5) 22730))
  (setq pt5 (polar pt5 pi 800))
  
  (setq ptlist (append ptlist (list pt5)))
  (setq ptlist ptlist)
)
;;;;;End xheKeypt;;;;;;;

;;;;;;;一键详图的序号的插入点;;;;;;;;;
(defun zt_xhKeypt(/ ptlist pt0 pt1 pt1_p pt2 i pt_weld pt)
  (setq ptlist '())
  (setq pt0 (nth 1 sthptlist));法兰最下点
  (setq pt1 (polar pt0 (/ pi 2) 200))
  (setq pt1 (polar pt1 0 400));爬梯
  
  (setq ptlist (append ptlist (list pt1)))
  
  (if (/= power "2.5")
    (progn;如果机型不是2.x-2.5，假如底平台围边的坐标
      (setq pt1_p (nth 2 sthptrlist));底平台平台围边
      (setq pt1_p (polar pt1_p (* pi (/ 4.0 3) ) 300))
      (setq ptlist (append ptlist (list pt1_p)))
    )
  )
  
  ;(setq pt1_p (nth 2 sthptrlist));底平台平台围边
  ;(setq pt1_p (polar pt1_p (* pi (/ 4.0 3) ) 300))
  
  (setq pt2 (MiddlePoint (nth 0 sthptlist) (nth (nth 1 pos) sthptlist)));升降机
  (setq pt2 (polar pt2 (/ pi 2) 1000))
  ;(setq ptlist (list pt1 pt1_p pt2))
  (setq ptlist (append ptlist (list pt2)))
  
  
  (setq i 0)
  (while (< i section_qty)
    (if (= i 0)
      (progn
	(setq pt_weld (MiddlePoint (nth (- (nth 1 pos) 3) sthptlist) (nth (- (nth 1 pos) 4) sthptlist)));第一段焊合
        (setq pt (MiddlePoint (nth (- (nth 1 pos) 3) sthptlist) (nth (nth 1 pos) sthptlist)));第一段附件总成的点
      )
      (progn
	(setq pt_weld (nth (- (nth (+ i 1) pos) 5) sthptlist) )
        (setq pt (nth (- (nth (+ i 1) pos) 3) sthptlist) )
      )
    )
    (setq ptlist (append ptlist (list pt_weld pt)))
    (setq i (+ i 1))
  )
  ;附件的坐标
  ;(setq pt3 (nth (- (nth section_qty pos) 2) sthptlist) )
  ;(setq ptlist (append ptlist (list pt3)))
  (setq ptlist ptlist)
)
;;;;;End zt_xheKeypt;;;;;;;


;主体法兰放大符号绘制;;;;;;;;;;;;;;;;;;;;;;;;;
(defun enlargesymbol(/ i k cspt);主体上法兰的序号绘制
  (setq i 0)
  (setq k flange_qty)
  (while (< i k)
    (setq cspt (nth (nth i pos) sthptrlist) );放大圈的坐标值
    (if (= i 0);如果是底法兰
      (progn;底法兰标注位置不同
        (FlangeZoom cspt (- (- flange_qty 1) i) mscale T nil)
      )
      (progn
        (FlangeZoom cspt (- (- flange_qty 1) i) mscale nil nil)
      )
    )
    (setq i (+ i 1))
  )
)
;;;;;;;;;函数结束;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;;主体上的关键点获取;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defun keypt(/ flstart j k sthlist h_upper_list sthpt_upper_list h
		 h_upper pt_x_upper pt_x shellradius shellradius_upper shellthick 
		 sthptl sthptr sthptl_upper sthptr_upper  )
  (setq flstart (car pos));开始的位置
  (setq j  flstart);塔筒段下法兰在主体表中开始的位置
  (setq k  (bnth -1 pos));塔筒段上法兰在主体表中结束的位置
  
  ;新建存储值的一些列表
  (setq sthlist '())
  (setq sthptlist '());标高中心点列表
  (setq sthptllist '());标高左点列表
  (setq sthptrlist '());标高右点列表
  (setq shellthicklist '());筒节壁厚列表
  (setq shellMaterialList '());筒节材料列表
  
  (setq pa_concrete '(16815 90220 0))
  ;得到关键点的坐标

  (while (<= j k)
   (if (> bflange_hole 79)
	(progn
     (setq h (* (- (value retTower j 0) (value retTower 0 0)) 1000));得到标高值（相对底法兰高度）并转换成mm
     (setq sthlist (cons h sthmidlist));把标高加到列表中
     (setq pt_x (polar pa_concrete (/ pi 2) h));相对选择点向上加标高
     (setq sthptlist (cons pt_x sthptlist))
	);progn 
	(progn
     (setq h (* (value retTower j 0) 1000));得到标高值（相对底法兰高度）并转换成mm
     (setq sthlist (cons h sthmidlist));把标高加到列表中
     (setq pt_x (polar pa (/ pi 2) h));相对选择点向上加标高
     (setq sthptlist (cons pt_x sthptlist))
	);progn 
   );end if
     ;;;;;外半径的判断
    
   
     (if  (and (= j flstart) (TorLflange j)) ;最底法兰且为T型法兰时
       (progn
         (setq shellradius (/ (value retFlange 0 13) 2));底法兰的最外半径
       )
       (progn
	   (setq shellradius (/ (value retTower j 1) 2));标高处的外半径
       )
     )
    
     ;;;;;;;;;;;
     (setq sthptl (polar pt_x pi shellradius));横线最左点
     (setq sthptr (polar pt_x 0 shellradius));横线右点  
     (setq sthptllist (cons sthptl sthptllist));把左坐标填到列表中
     (setq sthptrlist (cons sthptr sthptrlist));把右坐标填到列表中
     (setq shellthicklist (cons (value retTower j 4) shellthicklist));把壁厚填到列表中
	 (setq shellMaterialList (cons (value retTower j 5) shellMaterialList));把主体材料填到列表中
     (setq j (+ j 1))
  )
  ;把最顶点的加进来

 (if (> bflange_hole 79)
 (progn 
  (setq h (* (- (value retTower k 2) (value retTower 0 0)) 1000));塔架主体的顶点标高，并转换成mm
  (setq sthlist (cons h sthmidlist));把标高加到列表中
  (setq pt_x (polar pa_concrete (/ pi 2) h));相对选择点向上加标高
  (setq sthptlist (cons pt_x sthptlist))
  );progn
 (progn 
  (setq h (* (value retTower k 2) 1000));塔架主体的顶点标高-相对底法兰的高度，并转换成mm
  (setq sthlist (cons h sthmidlist));把标高加到列表中
  (setq pt_x (polar pa (/ pi 2) h));相对选择点向上加标高
  (setq sthptlist (cons pt_x sthptlist))
  );progn
 );end if
  (setq shellradius (/ (value retTower k 3) 2));标高处的外半径
  (setq sthptl (polar pt_x pi shellradius));横线最左点
  (setq sthptr (polar pt_x 0 shellradius));横线右点  
  (setq sthptllist (cons sthptl sthptllist));把左坐标填到列表中
  (setq sthptrlist (cons sthptr sthptrlist));把右坐标填到列表中
  ;;;;;;列表翻转，得到从下往上的顺序
  (setq sthlist (reverse sthlist))
  (setq sthptlist (reverse sthptlist))
  (setq sthptllist (reverse sthptllist))
  (setq sthptrlist (reverse sthptrlist))
  (setq shellthicklist (reverse shellthicklist))
  (setq shellMaterialList (reverse shellMaterialList))
)
;;;;;;;keypt函数结束;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


;;;;塔架主体绘制函数
(defun secdraw(xsec / flstart j k h pt_x shellradius shellptl shellptr
		m n shellptlx shellptrx shellptlxu shellptrxu shellptlx_x
		shellptlxu_x thick material hh pt_x shellradius shellptl);底段绘制
  ;下法兰绘制
  (bflangedraw (nth (nth xsec pos) sthptlist ) xsec);底法兰绘制
  ;绘制主体的横线，包括上下法兰的最上和最下的横线
  (setq m (+ (nth xsec pos) 2));初始值
  (setq n (nth (+ xsec 1) pos));此段的结束位置
  (while (< m (- n 1));画筒节的横线
    (setq shellptlx (nth m sthptllist))
    (setq shellptrx (nth m sthptrlist))
    (addline shellptlx shellptrx)
    (setq m (+ m 1))
  )
  ;绘制主体的左右侧竖线
  (setq m (+ (nth xsec pos) 1));从1开始，不画底法兰的直线参数
  (while (< m (- n 1))
    (setq shellptlx (nth m sthptllist));左下点
    (setq shellptrx (nth m sthptrlist));右下点
    (setq shellptlxu (nth (+ m 1) sthptllist));左上点
    (setq shellptrxu (nth (+ m 1) sthptrlist));右上点
    (setq shellptlx_x (car shellptlx));shellptlx的横坐标
    (setq shellptlxu_x (car shellptlxu));shellptlxu的横坐标
    (addline shellptlx shellptlxu);绘制左直线
    (addline shellptrx shellptrxu);绘制右直线
    ;筒节高度标注
    (ldimv shellptlx shellptlxu -1300);,坐标1，坐标2，标注距离，往左标注
    ;筒节直径标注
    (if (/= (rtos shellptlx_x) (rtos shellptlxu_x));若筒节上下的横坐标不同，是锥段，标注尺寸
      (if (/= m (- n 2))
        (dimd shellptlxu shellptrxu 1.0 -1000 -800 60);不标注最上筒节的尺寸
      ) 
    )
    ;壁厚标注
    (setq thick (nth m shellthicklist));得到壁厚
	(setq material (nth m shellMaterialList));得到塔筒主体材料
	(setq collect_num 0)
	(while (< i towernum)
		(if (and (/= (value retTower i 5) "")(/= (value retTower i 5) nil));(and (/= (value retTower i 9) "") (/= (value retTower i 9) nil)));如果不为空
			(progn
				(if (=(value retTower i 5) "Q420");(=(atof (value retTower i 9)) "Q420"))
					(setq collect_num (+ collect_num 1))
				)
			)
		)
		(setq i (+ i 1))
	)
	(if (/= collect_num 0)
		(progn
			(if (= collect_num towernum)
				(setq materile_style 1);0指Q355，1指Q420，2指Q355和420
				(setq materile_style 2);0指Q355，1指Q420，2指Q355和420
			)
		)
		(setq materile_style 0);0指Q355，1指Q420，2指Q355和420
	)
	(if (/=  materile_style 2)
		(Thick_drw thick material shellptrx shellptrxu 2150.0 (/ pi 6));thick:壁厚,下坐标，上坐标
		(Thick_drw_1 thick material shellptrx shellptrxu 2150.0 (/ pi 6));thick:壁厚,下坐标，上坐标
	)
    ;(Thick_drw thick material shellptrx shellptrxu 2150.0 (/ pi 6));thick:壁厚,下坐标，上坐标
    (command "layer" "M" "1轮廓实线层" "");为了改正标注壁厚时的线型
    (setq m (+ m 1))
  )
  ;此段的顶法兰绘制
  (uflangedraw (nth (- (nth (+ xsec 1) pos) 1) sthptlist ) (+ xsec 1))
  ;塔段总高度标注
  (if (= xsec (- section_qty 1))
    ;顶段的尺寸标注
    (ldimv (nth (nth xsec pos) sthptllist) (bnth -1 sthptllist) -3300)
    ;其他段尺寸标注
    (ldimv (nth (nth xsec pos) sthptllist) (nth (nth (+ xsec 1) pos) sthptllist) -3300)
  ) 
)
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


;Some tools
;增强属性块插入
(defun zq_block_insert (zb Index daihao mingcheng name shuliang beizhu notes danzhong zongzhong mingcheng_sf name_sf /
			liftblock attlist aa0 aa1 aa2 aa3 aa4 aa5 aa6 aa7 aa8 aa9 aa10 a11 aa12);坐标，序号，代号，名称，name，数量，备注,notes
  (setq liftblock (vla-InsertBlock myms (vlax-3d-point zb) "pc_mxb_block" mscale mscale 1 0 ) )
  (setq attlist (vlax-safearray->list (vlax-variant-value (vla-getattributes liftblock))))  
  (setq aa0 (vla-put-textstring (nth 0 attlist) ""));版本
  (setq aa1 (vla-put-textstring (nth 1 attlist) notes));notes
  (setq aa2 (vla-put-textstring (nth 2 attlist) ""));material
  (setq aa3 (nth 3 attlist))
  (vla-put-textstring aa3 name);name
  (setq aa4 (vla-put-textstring (nth 4 attlist) ""));drawing number
  (setq aa5 (vla-put-textstring (nth 5 attlist) zongzhong));总重
  (setq aa6 (vla-put-textstring (nth 6 attlist) danzhong));单重
  (setq aa7 (vla-put-textstring (nth 7 attlist) beizhu));备注
  (setq aa8 (vla-put-textstring (nth 8 attlist) shuliang));数量
  (setq aa9 (nth 9 attlist))
  (vla-put-textstring aa9 mingcheng);名称
  (setq aa10 (vla-put-textstring (nth 10 attlist) Index));序号
  (setq a11 (nth 11 attlist))
  (vla-put-textstring a11 daihao);代号
  (setq aa12 (vla-put-textstring (nth 12 attlist) ""));材料
  (vla-put-ScaleFactor aa9 mingcheng_sf);宽度因子
  (vla-put-ScaleFactor aa3 name_sf);宽度因子
  ;(vlax-dump-object aa9 t)

)
;;;End defun


;;;空物料号返回值
(defun tydhnil(v / r)
  (if (= v nil)
    (setq r "60.")
    (setq r v)
  );End if
);End tydhnil
;;;空重量返回值
(defun weightnil(v / r)
  (if (= v nil)
    (setq r 0)
    (setq r v)
  );End if
);End tydhnil

;;;;;列表中插入
(defun list_insert (yuanshi n vz /  aaa bbb test val jieguo)
  ;(setq yuanshi '(1 2 3 4 5));原始表
  (setq aaa yuanshi);复制原始表,循环处理会改变,所以复制数据
  (setq bbb yuanshi);复制原始表,循环处理会改变,所以复制数据
  ;前段处理
  (repeat n ;循环
    (setq test (cons (car aaa) test));制作一个储存逆向数据的表
    (setq aaa (cdr aaa))
  )
  (setq test (reverse test));逆转表
  ;后端处理
  (repeat n ;循环
    (setq bbb (cdr bbb))
  )
  ;合并
  (setq val (list vz))
  (setq jieguo (append test val bbb))
)
;;;;;;;End list_insert

;;;;bolt_judge;;;得到法兰螺栓的序号;;;;;
(defun bolt_judge (/ i xh_bolt_list xh_bolt_type_1 xh_nut_type_1 xh_washer_type_1 xh_boltnutwasher_list
		   bolt_d_1 tfl_1 bolt_d_2 tfl_2 xh_bolt_type_2 xh_nut_type_2 xh_washer_type_2
		   xh_bolt_type_2_kh xh_nut_type_2_kh xh_washer_type_2_kh)
  (setq xh_bolt_list '())
  ;(setq xh_bolt_type_1 (+ 3 (* 2 section_qty) 1));最顶法兰的螺栓序号
  ;(setq xh_nut_type_1 (+ 3 (* 2 section_qty) 2));最顶法兰的螺母序号
  ;(setq xh_washer_type_1 (+ 3 (* 2 section_qty) 3));最顶法兰的垫片序号

  (setq xh_bolt_type_1 (+ (length xhkeyptlist) 1));最顶法兰的螺栓序号
  (setq xh_nut_type_1 (+ xh_bolt_type_1 1));最顶法兰的螺母序号
  (setq xh_washer_type_1 (+ xh_nut_type_1 1));最顶法兰的垫片序号

  
  (setq xh_boltnutwasher_list (list xh_bolt_type_1 xh_nut_type_1 xh_washer_type_1))
  (setq xh_bolt_list (append xh_boltnutwasher_list))
  (setq i (- section_qty 1))
  (while (> i 1)

    (setq bolt_d_1  (value retFlange i 7));法兰表中的顶连接法兰螺栓直径
    (setq tfl_1  (value retFlange i 4));法兰表中顶连接法兰的厚度
    
    (setq bolt_d_2  (value retFlange (- i 1) 7));法兰表中螺栓直径
    (setq tfl_2  (value retFlange (- i 1) 4));法兰表厚度

    (if (> xh_nut_type_1 xh_bolt_type_1);if1
      (progn
        (if (= bolt_d_2 bolt_d_1)
          (progn
	    (if  (= tfl_2 tfl_1)
              (progn;如果螺栓和上一个螺栓相同，序号相同
                (setq xh_bolt_type_2 xh_bolt_type_1 )
	        (setq xh_nut_type_2 xh_nut_type_1 )
	        (setq xh_washer_type_2 xh_washer_type_1 )
	        ;;;相等的话加上括号
	        (setq xh_bolt_type_2_kh (strcat "(" (rtos xh_bolt_type_2) ")"))
                (setq xh_nut_type_2_kh (strcat "(" (rtos xh_nut_type_2) ")"))
	        (setq xh_washer_type_2_kh (strcat "(" (rtos xh_washer_type_2) ")"))
	        (setq xh_boltnutwasher_list (list xh_bolt_type_2_kh xh_nut_type_2_kh xh_washer_type_2_kh))
	        ;;;;重置
	        (setq xh_bolt_type_1 xh_bolt_type_2 );
	        (setq xh_nut_type_1 xh_nut_type_2 );
	        (setq xh_washer_type_1 xh_washer_type_2 );
	      )
	      (progn;如果螺母等相同，螺栓长度不同
                (setq xh_bolt_type_2 (+ xh_bolt_type_1 3) );
	        (setq xh_nut_type_2 xh_nut_type_1 )
	        (setq xh_washer_type_2 xh_washer_type_1 )
	        (setq xh_nut_type_2_kh (strcat "(" (rtos xh_nut_type_2) ")"))
                (setq xh_washer_type_2_kh (strcat "(" (rtos xh_washer_type_2) ")"))
	        (setq xh_boltnutwasher_list (list xh_bolt_type_2 xh_nut_type_2_kh xh_washer_type_2_kh))
	        ;;;;重置
	        (setq xh_bolt_type_1 xh_bolt_type_2 );
	        (setq xh_nut_type_1 xh_nut_type_2 );
	        (setq xh_washer_type_1 xh_washer_type_2 );
	      )
	    );End if
          )
          (progn;如果都不相同
	    (if (= i (- section_qty 1))
	      (progn;如果是第一次循环,螺栓都相同
                (setq xh_bolt_type_2 (+ xh_bolt_type_1 3) );
	        (setq xh_nut_type_2 (+ xh_nut_type_1 3) );
	        (setq xh_washer_type_2 (+ xh_washer_type_1 3) );
	        (setq xh_boltnutwasher_list (list xh_bolt_type_2 xh_nut_type_2 xh_washer_type_2))
	        ;;;;重置
	        (setq xh_bolt_type_1 xh_bolt_type_2 );
	        (setq xh_nut_type_1 xh_nut_type_2 );
	        (setq xh_washer_type_1 xh_washer_type_2 );
	      )
	      (progn
	        (setq xh_bolt_type_2 (+ xh_bolt_type_1 3) );
	        (setq xh_nut_type_2 (+ xh_nut_type_1 3) );
	        (setq xh_washer_type_2 (+ xh_washer_type_1 3) );
	        (setq xh_boltnutwasher_list (list xh_bolt_type_2 xh_nut_type_2 xh_washer_type_2))
	        ;;;;重置
	        (setq xh_bolt_type_1 xh_bolt_type_2 );
	        (setq xh_nut_type_1 xh_nut_type_2 );
	        (setq xh_washer_type_1 xh_washer_type_2 );
	      )
	    );End if
          );End progn
        );eND IF
      );progn
      (progn;如果上一个螺母与上上一个螺母相等，判断这一个和上一个
        (if (= bolt_d_2 bolt_d_1)
          (progn
	    (if  (= tfl_2 tfl_1)
              (progn;如果螺栓和上一个螺栓相同，序号相同
                (setq xh_bolt_type_2 xh_bolt_type_1 )
	        (setq xh_nut_type_2 xh_nut_type_1 )
	        (setq xh_washer_type_2 xh_washer_type_1 )
	        ;;;相等的话加上括号
	        (setq xh_bolt_type_2_kh (strcat "(" (rtos xh_bolt_type_2) ")"))
                (setq xh_nut_type_2_kh (strcat "(" (rtos xh_nut_type_2) ")"))
	        (setq xh_washer_type_2_kh (strcat "(" (rtos xh_washer_type_2) ")"))
	        (setq xh_boltnutwasher_list (list xh_bolt_type_2_kh xh_nut_type_2_kh xh_washer_type_2_kh))
	        ;;;;重置
	        (setq xh_bolt_type_1 xh_bolt_type_2 );
	        (setq xh_nut_type_1 xh_nut_type_2 );
	        (setq xh_washer_type_1 xh_washer_type_2 );
	      )
	      (progn;如果螺母等相同，螺栓长度不同
                (setq xh_bolt_type_2 (+ xh_bolt_type_1 1) );
	        (setq xh_nut_type_2 xh_nut_type_1 )
	        (setq xh_washer_type_2 xh_washer_type_1 )
	        (setq xh_nut_type_2_kh (strcat "(" (rtos xh_nut_type_2) ")"))
                (setq xh_washer_type_2_kh (strcat "(" (rtos xh_washer_type_2) ")"))
	        (setq xh_boltnutwasher_list (list xh_bolt_type_2 xh_nut_type_2_kh xh_washer_type_2_kh))
	        ;;;;重置
	        (setq xh_bolt_type_1 xh_bolt_type_2 );
	        (setq xh_nut_type_1 xh_nut_type_2 );
	        (setq xh_washer_type_1 xh_washer_type_2 );
	      )
	    );End if
          )
          (progn;如果都不相同
	    ;(if (= i (- section_qty 1))
	    ;  (progn;如果是第一次循环,螺栓都相同
            ;    (setq xh_bolt_type_2 (+ xh_bolt_type_1 3) );
	    ;    (setq xh_nut_type_2 (+ xh_nut_type_1 3) );
	    ;    (setq xh_washer_type_2 (+ xh_washer_type_1 3) );
	    ;    (setq xh_boltnutwasher_list (list xh_bolt_type_2 xh_nut_type_2 xh_washer_type_2))
	    ;    ;;;;重置
	    ;    (setq xh_bolt_type_1 xh_bolt_type_2 );
	    ;    (setq xh_nut_type_1 xh_nut_type_2 );
	    ;    (setq xh_washer_type_1 xh_washer_type_2 );
	    ;  )
	    ;  (progn
	        (setq xh_bolt_type_2 (+ xh_bolt_type_1 1) );
	        (setq xh_nut_type_2 (+ xh_bolt_type_2 1) );
	        (setq xh_washer_type_2 (+ xh_nut_type_2 1) );
	        (setq xh_boltnutwasher_list (list xh_bolt_type_2 xh_nut_type_2 xh_washer_type_2))
	        ;;;;重置
	        (setq xh_bolt_type_1 xh_bolt_type_2 );
	        (setq xh_nut_type_1 xh_nut_type_2 );
	        (setq xh_washer_type_1 xh_washer_type_2 );
	    ;  )
	    ;);End if
          );End progn
        );eND IF

      );End progn
    );End if1
  
    (setq xh_bolt_list (append xh_bolt_list xh_boltnutwasher_list))
    ;重置到上一个
    
    (setq i (- i 1))
  );eND WHILE
  (setq xh_bolt_list xh_bolt_list)
  
);;;;End bolt_judge

;;;直线绘制函数;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defun addline (pt1 pt2 /)
  (command "line" pt1 pt2 "")
)
;;;addline函数结束;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;;;;;;;;;;;;;;;;;;;;;;;;*****绘制中心线函数*******;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defun MiddleLine(p1 p2 dist);下点，上点，多出的距离
    (command "layer" "M" "3中心线层" "")
    (command "line" (list (car p1) (- (cadr p1) dist))     (list(car p2) (+ (cadr p2) dist)) "");
    (command "layer" "M" "1轮廓实线层" "")
)
;;;;End MiddleLine;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;;;门洞正面放大比例确定
(defun doorscalejudge(/ doorscale)
  (if (= mscale 140)
    (setq doorscale 70.0)
    (if (= mscale 120)
      (setq doorscale 60.0)
      (setq doorscale 50.0)
    )
  )
)
;;;;;;;;;;;;;;;;;;;;;;;;


;;;;;;;;;;;;;;;;;;;;;;;;;;;一斜边和一直角边求这个角度的弧度值;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defun asin(a c / b angle1)
  (setq b (expt (- (expt c 2) (expt a 2)) 0.5))
  (setq angle1 (atan a b))
)
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


;;;;;;;;;;;;****************已知三点，求过三点的圆半径;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defun Radius3P(p1 pc p2 / a b c pc_temp h1 S Radius)                                     ;;;;
  (setq a(distance p1 pc)                                                                 ;;;;
	b(distance p1 p2)                                                                 ;;;;
	c(distance pc p2)                                                                 ;;;;
	pc_temp(MiddlePoint p1 p2)                                                        ;;;;
	h1(distance pc pc_temp)                                                           ;;;;
	S(/(* b h1) 2)                                                                    ;;;;
	Radius(/(*(* a b)c)(* 4 S))                                                       ;;;;
	)                                                                                 ;;;;
  )                                                                                       ;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


;;;;;;;;;;;;;*************求p1 p2中点函数;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defun MiddlePoint(p1 p2 / Middle_px Middle_py)                                           ;;;;
  (setq Middle_px (/ (+ (car p1) (car p2)) 2))                                            ;;;;
  (setq Middle_py (/ (+ (cadr p1) (cadr p2)) 2))                                          ;;;;
  (list Middle_px Middle_py 0.0)                                                          ;;;;
  )                                                                                       ;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


;获得excel的目录,并且得到bom的文本前缀;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defun GetDir(excel_file)
  (substr excel_file 1(-(strlen excel_file)4))
)
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;获得excel的根目录目录;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defun GetrootDir(excel_file / str3 reg  matchcollect sdir )
  ;(setq str3 "\\\\rdfs.goldwind.com.cn\\vdi_user_workspace_v3\\userdata\\33905\\1月\\一键招标图测试\\2.X加强板锚栓四段90米\\TowerGeoInput_2.XMW_HH90m_(4段，锚栓式，160t，门洞加强，131-2.2，SW64，IEC S，丰华能源湖北随州曾都君子山99MW风电场项目).xlsx")
  ;(setq str3 "D:\\Personal\\Desktop\\TowerGeoInput_2.XMW_HH140m_(5段，锚栓式，332.2t，门洞加强，140-2.5，Sinoma68.6B，IEC S，河北建投巨鹿50MW项目_C).xlsx")
  (setq str3 excel_file)
  ;用@替换\\
  (while (vl-string-search "\\" str3)
    (setq str3 (vl-string-subst "@" "\\" str3))
  )
  ;(print str3)
  ;正则表达式建立及属性设置
  (setq reg (vlax-create-object "vbscript.regexp")) ;创建正则表达式
  (vlax-put-property reg 'global -1) ;是否匹配全部 （-1是 ，0 不是）
  (vlax-put-property reg 'Multiline -1);是否多行匹配 （-1是 ，0 不是）
  (vlax-put-property reg 'IgnoreCase -1);是否忽略大小写 （-1是 ，0 不是）
  (vlax-put-property reg 'pattern  (strcat  "(.+?)" "@"));
  ;(setq dirslist '())
  ;(setq newdir "")
  (setq rootdir "")
  (if (vlax-invoke-method reg 'test str3)
    (progn
      (setq matchcollect (vlax-invoke-method reg 'Execute str3))
      
      (vlax-for match_item matchcollect
	(progn
	  (setq sdir (eval (vlax-get-property match_item 'value)));提取出的每一个符合条件的值
	  (setq sdir (substr sdir 1 (- (strlen sdir) 1)));减去最后一个符号
	  ;(princ (strcat "\n"  "邮箱地址：" sdir))
          ;(setq dirslist (append dirslist (list sdir)))
	  ;(setq newdir (strcat newdir sdir))
	  (setq rootdir (strcat rootdir sdir "\\"))
	)
      )
      ;(print rootdir)
      ;(print dirslist)
      ;(print newdir)
    )
  );End if
  (while (vl-string-search "@" rootdir)
    (setq rootdir (vl-string-subst "\\" "@" rootdir))
  )
  ;(print rootdir)

  (vlax-release-object reg);释放内存
  (prin1)
)
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;;;;;******角度转弧度;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defun ang->rad(ang)
  (* pi (/ ang 180.0))
  )
;;当角度值大于90-270时，需要减掉180
;(angtos (- (atan (/  (tan (ang->b 271)) 0.6)) pi))
;tan(StartAngle) = ratio * tan(StratParam)
;(atan (* 0.6 (tan (/ pi 6.0))))
;;椭圆真实角度转参数
;;(椭圆起始角在组码中是起始参数)
;;(sk_el_ang->Par 真实弧度 长短轴比例值-组码40值)
;;所谓真实角度指的是起点或终点与长轴方向的夹角
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defun sk_el_ang->Par(ang ratio)
  (if(and (> ang (* pi 0.5))(<= ang (* pi 1.5)))
    (- (atan (/  (tan ang) ratio)) pi)
    (atan (/  (tan ang) ratio))
    )
)
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;;;;;****tan函数;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defun tan (ang)
  (/ (sin ang) (cos ang))
)
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


;;;;;;;;;;;;;;;;;**********已知∠A，b,c,求∠B;;;;;;;;;;;;;;;;;;;;;;;;;
(defun angle_b(angle_a b c)
  (setq a (sqrt (+ (* b b)(* c c)(* -2 b c (cos angle_a)))))
  (setq sinb (*(/ b a)(sin angle_a)))
  (setq cosb (/(+ (* a a)(* c c)(* b (- b)))(* 2 a c)))
  (atan (/ sinb cosb))
 )
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


;;;;;;;;;;;已知标高，求塔筒内径;;;;曹学敏新增
(defun calInnDia (asmLoc / i asmLocCyc asmlocUp asmLocDown asmDiaUp asmDiaDown asmDia)  
  (setq i 0)
  (setq asmLocCyc (value retTower 0 0)) ;标高循环初始值-即底法兰标高
  
  (while (> asmLoc asmLocCyc)  ;直到附件标高<循环标高，此时循环标高为筒段上标高
     (setq i (+ i 1))
	 (setq asmLocCyc (value retTower i 0))
  );end while
  
  (setq asmlocUp asmLocCyc)  ;筒段上标高
  ;(print asmlocUp)
  (setq asmLocDown (value retTower (- i 1) 0)) ;筒段下标高
  ;(print asmLocDown)
  (setq asmDiaUp (- (value retTower (- i 1) 3) (* 2 (value retTower (- i 1) 4))))   ;筒段上内径
  ;(print asmDiaUp)
  (setq asmDiaDown (- (value retTower (- i 1) 1) (* 2 (value retTower (- i 1) 4))))   ;筒段下内径
  ;(print asmDiaDown)
  (setq asmDia (+ asmDiaUp (/ (* (- asmDiaDown asmDiaUp) (- asmLoc asmlocUp)) (- asmLocDown asmlocUp))))    ;附加标高对应的塔筒内径
  ;(print asmDia)
);;;end defun





;;;;;;;;;;;;;;;;;;;;;;;;;;;;;license文件;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;获取系统时间;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defun SysTimeToIntTime()
  (setq numsystemTime(rtos(getvar"cdate"))
	year(substr numsystemTime 1 4)
	numMonth(substr numsystemTime 5 2))
  (setq MonthArray(list"Jan" "Feb" "Mar" "Apr" "May" "Jun" "Jul" "Aug" "Sep" "Oct" "Nov" "Dec"))
  (setq month(nth (- (atoi numMonth) 1)MonthArray))
  (setq sysTime(strcat Month " " year))
)
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defun XD:MD5 ( lst / a b c d f g h i k l r w x y )
  
  ;; k[n] = floor(abs(sin(n+1)))*2^32 ; n:1-64
  (setq k
	 (mapcar '(lambda ( x ) (md5:int->bits x 32))
		 '(
		   3614090360 3905402710 0606105819 3250441966 4118548399 1200080426 2821735955 4249261313
		   1770035416 2336552879 4294925233 2304563134 1804603682 4254626195 2792965006 1236535329
		   4129170786 3225465664 0643717713 3921069994 3593408605 0038016083 3634488961 3889429448
		   0568446438 3275163606 4107603335 1163531501 2850285829 4243563512 1735328473 2368359562
		   4294588738 2272392833 1839030562 4259657740 2763975236 1272893353 4139469664 3200236656
		   0681279174 3936430074 3572445317 0076029189 3654602809 3873151461 0530742520 3299628645
		   4096336452 1126891415 2878612391 4237533241 1700485571 2399980690 4293915773 2240044497
		   1873313359 4264355552 2734768916 1309151649 4149444226 3174756917 0718787259 3951481745
		   )
		 )
	)
  
  ;; bit-shift values
  (setq r
	 '(
	   07 12 17 22  07 12 17 22  07 12 17 22  07 12 17 22
	   05 09 14 20  05 09 14 20  05 09 14 20  05 09 14 20
	   04 11 16 23  04 11 16 23  04 11 16 23  04 11 16 23
	   06 10 15 21  06 10 15 21  06 10 15 21  06 10 15 21
	   )
	)
  
  ;; Initial hash values: forward/backward count in little-endian hex
  (setq h
	 (mapcar '(lambda ( x ) (md5:int->bits x 32))
		 '(
		   1732584193 ; 0x67452301 = 01234567
		   4023233417 ; 0xefcdab89 = 89abcdef
		   2562383102 ; 0x98badcfe = fedcda98
		   0271733878 ; 0x10325476 = 76543210
		   )
		 )
	)
  
  ;; Pre-processing:
  ;; Append 0x80 to list of byte values
  ;; Append 0x00 until list has length of 448 bits (mod 512)
  ;; Append length of string in bytes (little-endian) mod(2^64)
  (setq l (cons 128 (reverse lst)))
  (repeat (rem (+ 64 (- 56 (rem (length l) 64))) 64) (setq l (cons 0 l)))
  (setq l (append (reverse l) (md5:bits->bytes (md5:int->bits (* 8 (length lst)) 64))))
  
  ;; Process list in 512-bit (64-byte) chunks
  (repeat (/ (length l) 64)
    
    ;; Construct list of 16 32-bit (4-byte) words
    (repeat 16
      (setq w (cons (md5:bytes->bits (mapcar '+ l '(0 0 0 0))) w)
	    l (cddddr l)
	    )
      )
    (setq w (reverse w))
    
    ;; Initialise variables to hash values
    ;; Lists of bit values are used as AutoLISP does not support 32-bit unsigned integers
    (mapcar 'set '(a b c d) h)
    
    ;; Main MD5 algorithm:
    (setq i 0)
    (repeat 64
      (cond
	(   (< i 16)
	 (setq f (mapcar 'logior (mapcar 'logand b c) (mapcar 'logand (mapcar '(lambda ( a ) (+ 2 (~ a))) b) d))
	       g i
	       )
	 )
	(   (< i 32)
	 (setq f (mapcar 'logior (mapcar 'logand d b) (mapcar 'logand (mapcar '(lambda ( a ) (+ 2 (~ a))) d) c))
	       g (rem (1+ (* 5 i)) 16)
	       )
	 )
	(   (< i 48)
	 (setq f (mapcar '(lambda ( a b c ) (boole 6 a b c)) b c d)
	       g (rem (+ 5 (* 3 i)) 16)
	       )
	 )
	(   (setq f (mapcar '(lambda ( a b ) (boole 6 a b)) c (mapcar 'logior b (mapcar '(lambda ( a ) (+ 2 (~ a))) d)))
		  g (rem (* 7 i) 16)
		  )
	 )
	)
      (mapcar 'set '(d c a b i)
	      (list c b d
		    (md5:uint32_+ b
		      (md5:leftrotate
			(md5:uint32_+
			  (md5:uint32_+
			    (md5:uint32_+ a f)
			    (nth i k)
			    )
			  (nth g w)
			  )
			(nth i r)
			)
		      )
		    (1+ i)
		    )
	      )
      )
    
    ;; Update hash values for this chunk
    (setq h (mapcar 'md5:uint32_+ h (list a b c d))
	  w nil
	  )
    )
  
  ;; Convert the 4 32-bit integer values to 128-bit hash string of 32 hex digits
  (apply 'strcat
	 (mapcar 'md5:byte->hex
		 (apply 'append (mapcar 'md5:bits->bytes h))
		 )
	 )
  )
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defun md5:int->bits ( n b / l x )
  (repeat b (setq l (cons 0 l)))
  (foreach x (vl-string->list (rtos n 2 0))
    (setq x (- x 48)
	  l (mapcar '(lambda ( a ) (setq a (+ (* a 10) x) x (/ a 2)) (rem a 2)) l)
	  )
    )
  (reverse l) ;; output is big-endian
  )
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defun md5:bits->int ( l ) ;; input is big-endian
  (   (lambda ( f ) (f (reverse l)))
    (lambda ( l ) (if l (+ (* 2.0 (f (cdr l))) (car l)) 0))
    )
  )
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defun md5:bits->bytes ( l / b r ) ;; input is big-endian
  (repeat (/ (length l) 8)
    (repeat 8
      (setq b (cons (car l) b)
	    l (cdr l)
	    )
      )
    (setq r (cons (fix (+ 1e-8 (md5:bits->int (reverse b)))) r)
	  b nil
	  )
    )
  r ;; output is little-endian
  )
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defun md5:bytes->bits ( l ) ;; input is little-endian
  (apply 'append (mapcar '(lambda ( b ) (md5:int->bits b 8)) (reverse l))) ;; output is big-endian
  )
(defun md5:int->char ( n )
  (chr (+ n (if (< n 10) 48 87)))
  )
(defun md5:byte->hex ( x )
  (strcat (md5:int->char (/ x 16)) (md5:int->char (rem x 16)))
  )
(defun md5:leftrotate ( l x )
  (repeat x (setq l (append (cdr l) (list (car l)))))
  )
(defun md5:uint32_+ ( bl1 bl2 / r ) ;; input is big-endian
  (setq r 0)
  (reverse
    (mapcar
      '(lambda ( a b c / x )
	 (setq x (boole 6 (boole 6 a b) r)
	       r (boole 7 (boole 1 a b) (boole 1 a r) (boole 1 b r))
	       )
	 x
	 )
      (append (reverse bl1) (md5:uint32_0))
      (append (reverse bl2) (md5:uint32_0))
      (md5:uint32_0)
      )
    ) ;; output is big-endian
  )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defun md5:uint32_0 ( / l )
  (repeat 32 (setq l (cons 0 l)))
  (eval (list 'defun 'md5:uint32_0 nil (list 'quote l)))
  (md5:uint32_0)
  )
(princ)
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defun InternetTime (/ ie-obj)
  (setq ie-obj (vlax-get-or-create-object "Msxml2.xmlhttp"))
  (vlax-invoke-method ie-obj 'open  "get" "https://www.baidu.com/" 0)
  (vlax-invoke-method ie-obj 'setRequestHeader  "If-Modified-Since" "q")
  (vlax-invoke-method ie-obj "Send")
  (if (= (vlax-get-property ie-obj "readyState" ) 4)
    (progn
      (setq s (vlax-invoke-method ie-obj 'getResponseHeader "Date"))
      (setq InterTime(substr s 9 8))
      )
    (alert "网络验证失败!")
    )
  ;(and s (print s))
  (vlax-release-object ie-obj)
  (princ)
  )
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;End license;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;Include dimensions 202107014


(setq aaaa '(0 0 0))
(setq bbbb '(0 5000 0))


(defun c:qwe()
  (setq mscale 100)
  (setq myacad (vlax-get-acad-object))
  (setq mydoc (vla-get-ActiveDocument myacad))
  (setq myms (vla-get-ModelSpace mydoc));my models
  ;(dim_2 aaaa bbbb 1.0 0 -800 0 "" "2" "-0.5" "%%C")
  (setq center '(0 0 0))
  (setq ChordPoint '(5000 5000 0))
  (setq LeaderLength 5000)
  ;(setq dim1 (vla-AddDimRadial myms (vlax-3d-point center) (vlax-3d-point pt2) 1000 ) )
  (dimRadialR center pt2 LeaderLength 1)
  
)

(defun dimRadialR(Center ChordPoint LeaderLength dimscale / dim1)
  (setq dim1 (vla-AddDimRadial myms (vlax-3d-point Center) (vlax-3d-point ChordPoint) LeaderLength ) )
  ;(vlax-dump-object dim1 t)
  (vla-put-LinearScaleFactor dim1 dimscale)
  (vla-put-TextPosition  dim1  (vlax-3d-point Center))
  ;(vla-put-DimLineSuppress dim1 1);尺寸线（开/关）
  ;(vla-put-ForceLineInside   dim1 0);尺寸线强制
)


;;;;;;;;;;;;;;;;;;;;;;********************法兰厚度高度标注;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defun dimFlange_thick2(pt1 pt2 dimscale disv dish  / p3x p3y p_dim  dim1)
  (setq p3x (+ (car (MiddlePoint pt1 pt2)) dish))
  (setq p3y (+ (cadr pt1) disv ))
  (setq p_dim (list p3x p3y ))
  (setq dim1 (vla-adddimaligned myms (vlax-3d-point pt1) (vlax-3d-point pt2) (vlax-3d-point p_dim) ) )
  (vla-put-ToleranceDisplay dim1 2)
  (vla-put-ToleranceUpperLimit dim1 2.0)
  (vla-put-ToleranceLowerLimit dim1 0)
  (vla-put-LinearScaleFactor dim1 dimscale);改变加强板标注的尺寸比例
);
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;;;竖直尺寸标注，带公差,使用中
(defun dim_2 (pt1 pt2 dimscale disv dish ang text upperlimit lowerlimit suffix
	      / p3x p3y p_dim dim1 handle_value);dim diamatric 直径标注,点1，点2，放大比例，竖直位置，水平左右位置，旋转角度,后缀，上公差，下公差,前缀
  (setq p3x (+ (car (MiddlePoint pt1 pt2)) dish))
  (setq p3y (+ (cadr (MiddlePoint pt1 pt2)) disv ))
  (setq p_dim (list p3x p3y ))
  (setq dim1 (vla-adddimaligned myms (vlax-3d-point pt1) (vlax-3d-point pt2) (vlax-3d-point p_dim) ) )
  (vla-put-LinearScaleFactor dim1 dimscale);改变标注的尺寸比例

  (if (/= suffix "");如果前缀为空就不标注
    (vla-put-TextPrefix dim1 suffix);标注后面加文字
  )
  ;(vla-put-TextOverride dim1 "%%C<>");把直径符号加入到标注尺寸最前面

  (vla-put-PrimaryUnitsPrecision dim1 0);显示1位小数
  
  ;(setq text "中径")
  ;(setq text (strcat "(" text ")"))
  (if (/= text "");如果后缀为空就不标注
    (vla-put-TextSuffix dim1 text);标注后面加文字
  )

  (if (/= upperlimit "");如果后缀为空就不标注
    (progn
      (vla-put-ToleranceDisplay dim1 2)
      (vla-put-TolerancePrecision dim1 1)
      (vla-put-ToleranceSuppressTrailingZeros dim1 1)
      (vla-put-ToleranceUpperLimit dim1 upperlimit)
      (vla-put-ToleranceLowerLimit dim1 lowerlimit)
    );end progn
  );end if
  
  ;(vla-put-TextPrefix dim1 "4X");前面加文字

  (vla-put-TextPosition dim1 (vlax-3d-point p_dim))
  ;角度偏转
  (setq handle_value (vla-get-Handle dim1))
  (command "dimedit" "o"  (handent handle_value) "" ang)
  ;(vlax-dump-object dim1 t)
)
;;;;;



;;;;;;;;;;;;;;;;;;********直径单边标注;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defun DimflD(pt_L pt_R dimscale num_hole d_hole p_dim / dim1 text)

  (if (> num_hole 0)
    (setq text (strcat "(" (rtos num_hole) "×%%C"  (rtos d_hole) "EQS)"))
    (setq text "")
  )
  (setq dim1 (vla-adddimaligned myms (vlax-3d-point pt_L) (vlax-3d-point pt_R) (vlax-3d-point p_dim) ) )
  (vla-put-LinearScaleFactor dim1 dimscale);改变标注的尺寸比例
  (vla-put-TextPosition dim1 (vlax-3d-point p_dim));文字的位置
  ;(vlax-dump-object dim1 t)
  (vla-put-ExtLine1Suppress dim1 1);关闭尺寸扩展线
  (vla-put-DimLine1Suppress  dim1 1);关闭尺寸线1
  (vla-put-TextPrefix dim1 "%%C<>");
  (vla-put-TextSuffix dim1 text);标注后面加文字
  (vla-put-PrimaryUnitsPrecision dim1 1);显示1位小数
  (vla-put-SuppressTrailingZeros dim1 1);消去后续零
)
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


;;;;;;;;;;;;;;;;;;********中对齐直径单边标注;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defun DimflD_mid(pt_L pt_R dimscale num_hole d_hole p_dim ang / dim1 text handle_value)

  (if (> num_hole 0)
    (setq text (strcat "(" (rtos num_hole 2 0) "×%%C"  (rtos d_hole) "EQS)"))
    (setq text "")
  )
  (setq dim1 (vla-adddimaligned myms (vlax-3d-point pt_L) (vlax-3d-point pt_R) (vlax-3d-point p_dim) ) )
  (vla-put-LinearScaleFactor dim1 dimscale);改变标注的尺寸比例
  (vla-put-TextPosition dim1 (vlax-3d-point p_dim));文字的位置
  ;(vlax-dump-object dim1 t)
  (vla-put-ExtLine1Suppress dim1 1);关闭尺寸扩展线
  (vla-put-DimLine1Suppress  dim1 1);关闭尺寸线1
  (vla-put-TextPrefix dim1 "%%C<>");
  (vla-put-TextSuffix dim1 text);标注后面加文字
  (vla-put-PrimaryUnitsPrecision dim1 1);显示1位小数
  (vla-put-SuppressTrailingZeros dim1 1);消去后续零
   ;角度偏转
  (setq handle_value (vla-get-Handle dim1))
  (command "dimedit" "o"  (handent handle_value) "" ang)
)
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;;;中对齐主体直径标注，带后缀
(defun dimd_mid (pt1 pt2 dimscale disv dish ang text / p3x p3y p_dim dim1 handle_value);dim diamatric 直径标注,点1，点2，竖直位置，水平左右位置，旋转角度
  ;(setq p3x (+ (car pa) dish))
  ;(setq p3y (+ (cadr pt1) disv ))
  (setq p3x (+ (car (MiddlePoint pt1 pt2)) dish))
  (setq p3y (+ (cadr pt1) disv ))
  (setq p_dim (list p3x p3y ))
  (setq dim1 (vla-adddimaligned myms (vlax-3d-point pt1) (vlax-3d-point pt2) (vlax-3d-point p_dim) ) )
  (vla-put-LinearScaleFactor dim1 dimscale);改变加强板标注的尺寸比例
  (vla-put-TextOverride dim1 "%%C<>");把直径符号加入到标注尺寸最前面

  (vla-put-PrimaryUnitsPrecision dim1 1);显示1位小数
  (vla-put-SuppressTrailingZeros dim1 1);消去后续零
  
  ;(vlax-dump-object dim1 t)
  ;(setq text "中径")
  ;(setq text (strcat "(" text ")"))
  (vla-put-TextSuffix dim1 text);标注后面加文字
  ;(vla-put-TextSuffix dim1 "(中径)");标注后面加文字
  
  ;(vla-put-TextPrefix dim1 "4X");前面加文字

  
  (vla-put-TextPosition dim1 (vlax-3d-point p_dim))

  (setq handle_value (vla-get-Handle dim1))
  (command "dimedit" "o"  (handent handle_value) "" ang)
  
)
;;;;;

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;dimension文件;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;高度标注,主要用于标注主体，各个筒节的高度
(defun ldimv (pt1 pt2 dis / towerdiameter p3x p3y p_dim);left dim vertical,左方向竖直标注,点1，点2，距离
  (setq towerdiameter (value retTower 1 1));塔筒底段外径，标注尺寸用
  (if (> bflange_hole 79)
  (setq p3x (+ (car pa_concrete) (- (/ towerdiameter 2)) dis)) ;竖直标注x方向，car：返回x坐标值
  (setq p3x (+ (car pa) (- (/ towerdiameter 2)) dis)) ;竖直标注x方向，car：返回x坐标值
  ;(setq p3x (+ (car pa) dis)) ;竖直标注x方向，car：返回x坐标值
  );end if
  (setq p3y ( + (cadr pt1) (/ (- (cadr pt2) (cadr pt1)) 2)) )
  (setq p_dim (list p3x p3y))
  (command "dimlinear"  pt1 pt2 "v" p_dim)
)

;;;竖直标注，主要用在了门洞那块
(defun ldimv2 (pt1 pt2 dimscale disv dish / p3x p3y p_dim);left dim vertical,左方向竖直标注,点1，点2，左右距离，上下距离
  (setq p3x (+ (car (MiddlePoint pt1 pt2)) dish) ) ;竖直标注x方向，car：返回x坐标值
  (setq p3y ( + (cadr (MiddlePoint pt1 pt2)) disv) )
  ;(setq p3y (+ (cadr pt1) disv) )
  (setq p_dim (list p3x p3y))
  ;(command "dimlinear"  pt1 pt2 "v" p_dim)
  (setq dim1 (vla-adddimaligned myms (vlax-3d-point pt1) (vlax-3d-point pt2) (vlax-3d-point p_dim) ) )
  (vla-put-TextPosition dim1 (vlax-3d-point p_dim))
  ;(vlax-dump-object dim1 t)
  ;(setq dimscale (/ 40.0 mscale ) );局部放大视图比例,dimscale:dim scale,实际标注尺寸
  (vla-put-LinearScaleFactor dim1 dimscale);改变标注的尺寸比例
)

;连接法兰竖直厚度标注
(defun cfldimv (pt1 pt2 dis / p3x p3y p_dim);left dim vertical,左方向竖直标注
  (setq towerdiameter (value retTower 1 1));塔筒底段外径，标注尺寸用
  (if (> bflange_hole 79)
  (setq p3x (+ (car pa_concrete) (- (/ towerdiameter 2)) dis)) ;竖直标注x方向，car：返回x坐标值
  (setq p3x (+ (car pa) (- (/ towerdiameter 2)) dis)) ;竖直标注x方向，car：返回x坐标值
  ;(setq p3x (+ (car pa) dis)) ;竖直标注x方向，car：返回x坐标值
  );end if
  (setq p3y (* -1 ( + (cadr pt1) (/ (- (cadr pt2) (cadr pt1)) 2))) )
  (setq p_dim (list p3x p3y))
  (command "dimlinear"  pt2 pt1 "v" "t" "\(<>\)" p_dim)
)

;;;;主体直径标注
(defun dimd (pt1 pt2 dimscale disv dish ang / p3x p3y p_dim dim1 handle_value);dim diamatric 直径标注,点1，点2，竖直位置，水平左右位置，旋转角度
  ;(setq p3x (+ (car pa) dish))
  ;(setq p3y (+ (cadr pt1) disv ))
  (setq p3x (+ (car (MiddlePoint pt1 pt2)) dish))
  (setq p3y (+ (cadr pt1) disv ))
  (setq p_dim (list p3x p3y ))
  (setq dim1 (vla-adddimaligned myms (vlax-3d-point pt1) (vlax-3d-point pt2) (vlax-3d-point p_dim) ) )
  (vla-put-LinearScaleFactor dim1 dimscale);改变加强板标注的尺寸比例
  (vla-put-TextOverride dim1 "%%C<>");把直径符号加入到标注尺寸最前面

  (vla-put-PrimaryUnitsPrecision dim1 1);显示1位小数
  ;(vlax-dump-object dim1 t)
  
  (vla-put-TextPosition dim1 (vlax-3d-point p_dim))

  (setq handle_value (vla-get-Handle dim1))
  (command "dimedit" "o"  (handent handle_value) "" ang)
  
)
;;;;;

;;;;水平尺寸标注
(defun dimh (pt1 pt2 dimscale disv dish ang / p3x p3y p_dim dim1 handle_value);点1，点2，竖直位置，水平左右位置，旋转角度
  (setq p3x (+ (car (MiddlePoint pt1 pt2)) dish))
  (setq p3y (+ (cadr pt1) disv ))
  (setq p_dim (list p3x p3y ))
  (setq dim1 (vla-adddimaligned myms (vlax-3d-point pt1) (vlax-3d-point pt2) (vlax-3d-point p_dim) ) )
  (vla-put-LinearScaleFactor dim1 dimscale);改变加强板标注的尺寸比例
  ;(vla-put-TextOverride dim1 "%%C<>");把直径符号加入到标注尺寸最前面
  (vla-put-TextPosition dim1 (vlax-3d-point p_dim))
  ;(vlax-dump-object dim1 t)
  ;(vla-put-PrimaryUnitsPrecision dim1 1);显示1位有效小数
  (setq handle_value (vla-get-Handle dim1))
  (command "dimedit" "o"  (handent handle_value) "" ang)
)
;;;;;

;;;;水平尺寸标注,法兰脖子厚度水平尺寸标注
(defun dimh_flange (pt1 pt2 dimscale disv dish ang / p3x p3y p_dim dim1 handle_value);点1，点2，竖直位置，水平左右位置，旋转角度
  (setq p3x (+ (car pt2) dish))
  (setq p3y (+ (cadr pt1) disv ))
  (setq p_dim (list p3x p3y ))
  (setq dim1 (vla-adddimaligned myms (vlax-3d-point pt1) (vlax-3d-point pt2) (vlax-3d-point p_dim) ) )
  (vla-put-LinearScaleFactor dim1 dimscale);改变加强板标注的尺寸比例
  (vla-put-TextPosition dim1 (vlax-3d-point p_dim))
  ;(vlax-dump-object dim1 t)
  (vla-put-PrimaryUnitsPrecision dim1 1);显示1位有效小数
  (vla-put-SuppressTrailingZeros dim1 1);消去后续零
  (setq handle_value (vla-get-Handle dim1))
  (command "dimedit" "o"  (handent handle_value) "" ang)
)
;;;;;


;圆弧标注
(defun dimr(center R ang dimscale leng / dim1)
  (setq dim1 (vla-AddDimRadial myms (vlax-3d-point center) (vlax-3d-point (polar center ang R)) leng ) )
  (vla-put-LinearScaleFactor dim1 dimscale)
  (vla-put-TextSuffix dim1 "(展开尺寸/Expanded dimention)");标注后面加文字
  (vla-put-TextPrefix dim1 "4XR");把
)
;;;;函数结束

;;;;;;;;;;;;;;;;;;;;;;********************法兰厚度高度标注;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defun dimFlange_thick(pt1 pt2 location scale)
  (setvar "DIMTOL" 3)
  (setvar "DIMTM" 0);下公差
  (setvar "DIMTP" 2);上公差
  (setvar "dimlfac" (/ 1.0 scale)) ;尺寸比例
  (if (= location "L")
    (command "Dimlinear" pt1 pt2 (polar (MiddlePoint pt1 pt2) pi (/ (distance pt1 pt2) 2)));法兰厚度标注
    (progn
      (setvar "DIMTOL" 0)
      (command "Dimlinear" pt1 pt2 (polar (MiddlePoint pt1 pt2) 0 2500));法兰高的标注
    )
  )
  (setvar "DIMTOL" 0)
  (setvar "DIMTP" 0)
  (setvar "dimlfac" 1)
);(dimFlange_thick pt_h pt_out "R" scale_fl)
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


;;;;;;;;;;;;;;;;;;;;;;********************法兰高度标注;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defun dimFlange_h(pt1 pt2 scale dist)
  (setvar "dimlfac" (/ 1.0 scale)) ;尺寸比例
  (command "Dimlinear" pt1 pt2 (polar (MiddlePoint pt1 pt2) 0 dist));法兰高的标注
  (setvar "dimlfac" 1)
)
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;;;;;;;;;;;;;;;;;;;;;********************法兰厚度高度标注;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defun dimFlange_thick2(pt1 pt2 dimscale disv dish  / p3x p3y p_dim  dim1)
  (setq p3x (+ (car (MiddlePoint pt1 pt2)) dish))
  (setq p3y (+ (cadr pt1) disv ))
  (setq p_dim (list p3x p3y ))
  (setq dim1 (vla-adddimaligned myms (vlax-3d-point pt1) (vlax-3d-point pt2) (vlax-3d-point p_dim) ) )
  (vla-put-ToleranceDisplay dim1 2)
  (vla-put-ToleranceUpperLimit dim1 2.0)
  (vla-put-ToleranceLowerLimit dim1 0)
  (vla-put-LinearScaleFactor dim1 dimscale);改变加强板标注的尺寸比例
);
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;;;;;;;;;;;;;;;;;********直径单边标注;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defun Dimflange(pt_L pt_R pt_text scale num_hole d_hole)
    (command "layer" "M" "7标注层" "")
    (setvar "dimjust" 2) ;尺寸放在第二条尺寸线上
    (setvar "dimsd1" 1) ;关闭第一条尺寸线
    (setvar "dimse1" 1) ;关闭尺寸界限1
    (setvar "dimupt" 1) ;尺寸放在指定位置pt_text
    (setvar "dimlfac" (/ 1.0 scale)) ;尺寸比例
    (if (> num_hole 0)
      (setq text (strcat "%%C<>(" (rtos num_hole) "×%%C" (rtos d_hole) "EQS)"))
      (setq text "%%C<>")
    )
    (command "dimlinear" pt_L pt_R "t" text pt_text)
    (setvar "dimjust" 0)
    (setvar "dimsd1" 0)
    (setvar "dimse1" 0)
    (setvar "dimupt" 0)
    (setvar "dimlfac" 1)
)
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;




;;;;;;;;;;;;;;;;;;;;;;;;;;;******************带比例标注,精度为1位小数;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defun scaleDim(pt1 pt2 scale isup)
  ;0为标注位置不动
  ;1为标注位置在上
  ;2为标注位置在下
  (setvar "dimlfac" (/ 1.0 scale)) ;尺寸比例
  (setvar "dimdec" 1);设置标注精度
  (cond ((= isup 0) (command "Dimlinear" pt1 pt2 (polar pt2 0 (distance pt1 pt2))))
	((= isup 1) (command "Dimlinear" pt1 pt2 (polar (polar pt2 (* pi 0.5) (distance pt1 pt2)) 0 (distance pt1 pt2))))
	((= isup 2) (command "Dimlinear" pt1 pt2 (polar (polar pt2 (* pi 1.5) (distance pt1 pt2)) 0 (distance pt1 pt2))))
  )
  (setvar "dimlfac" 1)
  (setvar "dimdec" 0)
)
;;;;;;(scaleDim flpt21 (polar flpt21 0 tn) scale_fl 0);;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;End dimension;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;lead文件;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;;;;;;;;;;;;;;;;;;;;法兰重量标注函数;;;;
(defun flmasslead(FlangeIndex Flange_type pt xFlangeMass tfl / pt2 pt3 pt4 pt5 text dubs leader dubs2 leader2 dubs3 leader3);法兰序号，法兰类型，插入点，法兰重量，法兰厚
  (setq pt (append pt '(0)));
  (if (and (= FlangeIndex 0) (= Flange_type "T"))
    (progn;第一个法兰执行
      (setvar "cmdecho" 0)
      (command "layer" "M" "7标注层" "")
      (setvar "cmleaderstyle" "GW3")
      (setq pt2 (polar pt (* pi 0.67) (* tfl 15)))
      (setq pt3 (polar pt2 (* pi 0.25) (* 40 mscale)))
      (setq pt4 (polar pt (* pi 0.67) (* tfl 10)))
      (setq pt5 (polar pt4 (* pi 1.75) (* 40 mscale)))
      (setq text (strcat "{" "\\C3;""法兰重量："(rtos xFlangeMass 2 1)"Kg""\\P""FlangeWeight""}"))
       (setq dubs3 (vlax-make-safearray vlax-vbDouble '(0 . 5)));
      (vlax-safearray-fill dubs3 (append pt4 pt5))
      (setq dubs3 (vlax-make-variant dubs3))
      (setq leader3 (vla-AddMLeader myms dubs3 10 ));多重引线
      (vla-put-ArrowheadType leader3 0);引线头的样式
      (vla-put-textstring leader3 text);
      ;(vlax-put-property leader3 'TextHeight (* mscale 4));更改文字大小
      (vla-put-ScaleFactor leader3 (* mscale 1));更改标注后的比例
      ;(command "mleader" pt4 pt5 text)
      (princ)

      (setvar "cmleaderstyle" "GW")
    )
    (progn;其余的法兰执行
      (setvar "cmdecho" 0)
      (command "layer" "M" "7标注层" "")
      (setvar "cmleaderstyle" "GW3")
      (setq pt2 (polar pt (* pi 0.67) (* tfl 15)))
      (setq pt3 (polar pt2 (* pi 0.25) (* 40 mscale)))
      (setq pt4 (polar pt (* pi 1.33) (* tfl 15)))
      (setq pt5 (polar pt4 (* pi 1.75) (* 40 mscale)))

      (setq text (strcat "{" "\\C3;""法兰重量："(rtos xFlangeMass 2 1)"Kg""\\P""FlangeWeight""}"))
            (setq dubs (vlax-make-safearray vlax-vbDouble '(0 . 5)));
      (vlax-safearray-fill dubs (append pt2 pt3))
      (setq dubs (vlax-make-variant dubs))
      (setq leader (vla-AddMLeader myms dubs 10 ));多重引线
      (vla-put-ArrowheadType leader 0);引线头的样式
      (vla-put-textstring leader text);
      ;(vlax-put-property leader 'TextHeight (* mscale 4));更改文字大小
      (vla-put-ScaleFactor leader (* mscale 1));更改标注后的比例
      ;(vlax-dump-object leader t)

      (setq dubs2 (vlax-make-safearray vlax-vbDouble '(0 . 5)));
      (vlax-safearray-fill dubs2 (append pt4 pt5))
      (setq dubs (vlax-make-variant dubs2))
      (setq leader2 (vla-AddMLeader myms dubs2 10 ));多重引线
      (vla-put-ArrowheadType leader2 0);引线头的样式
      (vla-put-textstring leader2 text);
      ;(vlax-put-property leader2 'TextHeight (* mscale 4));更改文字大小
      (vla-put-ScaleFactor leader2 (* mscale 1));更改标注后的比例
      ;(command "mleader" pt2 pt3 text)
      ;(command "mleader" pt4 pt5 text)
      (princ)

      (setvar "cmleaderstyle" "GW")
    )
  )
)
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;;;;;;;;;;;;;;;;;;;;加强板重量标注函数;;;;
(defun repmasslead(pt0 xMass / pt1 text 2dubs leader2);法兰序号，法兰类型，插入点，法兰重量，法兰厚
      (setvar "cmdecho" 0)
      (command "layer" "M" "7标注层" "")
      (setvar "cmleaderstyle" "GW3")
      (setq pt0 (append pt0 '(0)))
      (setq pt1 (polar pt0 (/ pi 4) 5000))
      (setq text (strcat "{" "\\C3;""加强板重量："(rtos xMass 2 0)"Kg""\\P""Reinforcing plates Weight""}"))
    (setq 2dubs (vlax-make-safearray vlax-vbDouble '(0 . 5)));
  (vlax-safearray-fill 2dubs (append pt0 pt1))
  (setq 2dubs (vlax-make-variant 2dubs))
  (setq leader2 (vla-AddMLeader myms 2dubs 0 ));多重引线
  (vla-put-ArrowheadType leader2 0);引线头的样式
  (vla-put-textstring leader2 text);
  ;(vlax-put-property leader2 'TextHeight (* mscale 4));更改文字大小
  (vla-put-ScaleFactor leader2 (* mscale 1));更改标注后的比例

      ;(command "mleader" pt0 pt1 text)
      (princ)
      (setvar "cmleaderstyle" "GW")
)
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;标注力矩函数***;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defun AnchorBolt(AnchorBoltType AnchorBoltClass MomentPt Moment / retbolt i 2dubs leader2)
  ;当仅采用下面内容进行计算时，函数参数无Moment
  ;(setq boltdat_file "D:\\Program Files (x86)\\Autodesk\\AutoLisp\\Data.xlsx")
  ;(setq retbolt (GetCellValueAsList boltdat_file "Bolt_Data" "C2:V17")) ;螺栓数据
  (setq MomentPt (append MomentPt'(0)))
  (setq retbolt retBoltDraw)
  (setq i 0 )
  (while (<= i 14)
    (if (= (value retbolt i 0) AnchorBoltType)
      (progn
	(setq BoltStressArea (value retbolt i 1))
	(if (/= AnchorBoltClass nil)
	  (setq Moment2 (* 0.7 BoltStressArea (fix AnchorBoltClass) (- AnchorBoltClass (fix AnchorBoltClass)) 0.1))
	)
      )
    )
    (setq i (1+ i))
  )
  (setvar "cmdecho" 0)
  (command "layer" "M" "7标注层" "")
  (setvar "cmleaderstyle" "GW3")
  (setq pt2 (polar MomentPt (* pi 0.5) (* 10 mscale)))
  (setq pt3 (polar pt2 (* pi 0.25) (* 30 mscale)))

  (if (= AnchorBoltClass nil)
    (setq text (strcat "{" "\\C1;""Excel中无锚栓等级，请检查！""}"))
;;;    (setq text (strcat "{" "\\C3;""基础锚栓+螺母+垫片（基础施工提供）""\\P""\\W0.45;""Pretightening force of M"(rtos AnchorBoltType)" anchor bolt is "
;;;		       (rtos Moment 2 1)"kN""("(rtos AnchorBoltClass 2 1)")""\\P""\\W0.67;""M"(rtos AnchorBoltType)"锚栓（"(rtos AnchorBoltClass 2 1)
;;;		       "级）预紧力:"(rtos Moment 2 1)"kN""}"))
    (progn
      (if (= Moment nil)
	(progn
	  (setq text (strcat "{" "\\C3;""基础锚栓+螺母+垫片""\\P""\\W0.45;""Pretightening force of M" (rtos AnchorBoltType) "-" (rtos AnchorBoltClass 2 1)
			     " anchor bolt is "
		       "\\C1;Error \\C3; kN" "\\P""\\W0.67;""M"(rtos AnchorBoltType)"锚栓（"(rtos AnchorBoltClass 2 1)
		       "级）预紧力:""\\C1;Error \\C3;""kN""}"))
	  (print (strcat "Excel表中没填写预紧力，程序计算预紧力为："(rtos Moment2 2 1)"KN"))
	  (print (strcat "Excel表中没填写预紧力，程序计算预紧力为："(rtos Moment2 2 1)"KN"))
	)
    	(setq text (strcat "{" "\\C3;""基础锚栓+螺母+垫片""\\P""\\W0.45;""Pretightening force of M"(rtos AnchorBoltType) "-" (rtos AnchorBoltClass 2 1)
			   " anchor bolt is "
		       (rtos Moment 2 1)"kN" "\\P""\\W0.67;""M"(rtos AnchorBoltType)"锚栓（"(rtos AnchorBoltClass 2 1)
		       "级）预紧力:"(rtos Moment 2 1)"kN""}"))
      )
    )
  )
  (princ)
    (setq 2dubs (vlax-make-safearray vlax-vbDouble '(0 . 5)));
  (vlax-safearray-fill 2dubs (append pt2 pt3))
  (setq 2dubs (vlax-make-variant 2dubs))
  (setq leader2 (vla-AddMLeader myms 2dubs 0 ));多重引线
  (vla-put-ArrowheadType leader2 0);引线头的样式
  (vla-put-textstring leader2 text);
  ;(vlax-put-property leader2 'TextHeight (* mscale 4));更改文字大小
  (vla-put-ScaleFactor leader2 (* mscale 1));更改标注后的比例
  ;(command "mleader" pt2 pt3 text)

  (setvar "cmleaderstyle" "GW")
)
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;;;;;;;;;加强板放大图椭圆符号标注
(defun repellipselead(pt0 halfLAxis halfSAxis / ShapeRefPt ShapePt1 pt3 text 2dubs leader2)
  (setq ShapeRefPt (ptOnEllipse pt0 halfLAxis  halfSAxis -45 (/ h2 2))
	ShapePt1 (polar ShapeRefPt (* pi 0.67) (* 7.5 halfSAxis)))
  (setq pt3 (polar ShapePt1 pi (* 5 halfSAxis)))
  (setvar "cmleaderstyle" "GW3")
  ;(setvar "mleaderscale" (/ mscale 2))
  ;(setvar "mleaderscale" mscale)
  (command "layer" "M" "7标注层""")
  (setq text (strcat "{" "\\C3;""半椭圆" "\\P""Semi-Ellipse""}"))
  
  (setq 2dubs (vlax-make-safearray vlax-vbDouble '(0 . 8)));
  (vlax-safearray-fill 2dubs (append ShapeRefPt ShapePt1 pt3))
  (setq 2dubs (vlax-make-variant 2dubs))
  (setq leader2 (vla-AddMLeader myms 2dubs 0 ));多重引线
  (vla-put-ArrowheadType leader2 0);引线头的样式
  (vla-put-textstring leader2 text);
  ;(vlax-put-property leader2 'TextHeight (* mscale 4));更改文字大小
  (vla-put-ScaleFactor leader2 (* mscale 1));更改标注后的比例

  ;(command "mleader" ShapeRefPt ShapePt1 text)

  (setvar "dimlfac" 1.0)

  ;(setvar "mleaderscale" 1.0)
  (command "layer" "M" "1轮廓实线层""")
  (setvar "cmleaderstyle" "GW")
)
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


;;;基础环放大视图上质量的标注;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defun EmbeddedLead(pt1 pt2 mass_base shellmass / pt3 pt5 text1 text2)
  (setvar "cmdecho" 0)
  (command "layer" "M" "7标注层" "")
  (setvar "cmleaderstyle" "GW3")
  (setq pt3 (polar pt1 (* pi 0.25) (* 40 mscale)))
  (setq pt5 (polar pt2 (* pi 0.25) (* 40 mscale)))
  (setvar "mleaderscale" mscale)
  (setq text1 (strcat "{" "\\C3;""法兰重量："(rtos mass_base 2 0)"Kg""\\P""FlangeWeight""}"))
  (setq text2 (strcat "{" "\\C3;""底座环重量："(rtos shellmass 2 0)"Kg""\\P""CylinderRing Weight""}"))
  (command "mleader" pt1 pt3 text1)
  (command "mleader" pt2 pt5 text2)
  (princ)
  (setvar "mleaderscale" 1)
  (setvar "cmleaderstyle" "GW")
)
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


;;;;;;;;;;;;;;;;;;;;普通门框的标注
;门框重量，门框椭圆
(defun generaldoorfrontlead (DoorZoomLocationPt halfLAxis halfSAxis MassDoorFrame /
			     pt1 ShapeRefPt1 ShapeRefPt2 pt2 pt3 text 2dubs leader2);普通门框的中心点，长半轴，短半轴，门框重量
  (setvar "cmdecho" 0)
  (command "layer" "M" "7标注层" "")
  (setvar "cmleaderstyle" "GW3")
  (setq pt1 (ptOnEllipse DoorZoomLocationPt halfLAxis halfSAxis -45 (/ h2 2)))
  (setq ShapeRefPt1 (ptOnEllipse DoorZoomLocationPt halfLAxis  halfSAxis 45  (/ h2 2))
	ShapeRefPt2 (polar ShapeRefPt1 (* pi 0.25) (* 1 halfSAxis))
  )
  (setq pt2 (polar pt1 (* pi 0.75)  (* 2 halfSAxis) ))
  (setq pt3 (polar pt2 (* pi 1)  (* 2 halfSAxis) ))
  (setq text (strcat "{" "\\C3;""门框重量："(rtos MassDoorFrame 2 0)"Kg""\\P""DoorFrameWeight""}"))
    (setq 2dubs (vlax-make-safearray vlax-vbDouble '(0 . 8)));6
  (vlax-safearray-fill 2dubs (append pt1 pt2 pt3))
  (setq 2dubs (vlax-make-variant 2dubs))
  (setq leader2 (vla-AddMLeader myms 2dubs 0 ));多重引线
  (vla-put-ArrowheadType leader2 0);引线头的样式
  (vla-put-textstring leader2 text);
  (vla-put-ScaleFactor leader2 (* mscale 1));更改标注后的比例
  ;(vlax-put-property leader2 'TextHeight (* mscale 4));更改文字大小
  ;(vlax-dump-object leader2 t)
  ;(command "mleader" pt1 pt2 text)
  (princ)
  ;(setq DoorFramViewPt (polar (polar door_up_pzt (/ pi 2)  halfSAxis) 0  halfSAxis))
  (if (> (/ halfLAxis halfSAxis) 1)
    (progn
      (setq dubs (vlax-make-safearray vlax-vbDouble '(0 . 5)));6
      (vlax-safearray-fill dubs (append ShapeRefPt1 ShapeRefPt2))
      (setq dubs (vlax-make-variant dubs))
      (setq leader (vla-AddMLeader myms dubs 0 ));多重引线
      (vla-put-ArrowheadType leader 0);引线头的样式
      (vla-put-textstring leader "{\\C3;半椭圆}");
      (vla-put-ScaleFactor leader (* mscale 1));更改标注后的比例
      ;(vlax-put-property leader 'TextHeight (* mscale 4));更改文字大小
      ;(command "mleader" ShapeRefPt1 ShapeRefPt2 "{\\C3;半椭圆}")
    )
  )
  (princ)

  (setvar "cmleaderstyle" "GW")
  (command "layer" "M" "6文字层" "")
  ;(command "text" "C" DoorFramViewPt (* 5 mscale) 0 "门框外形")
  (command "layer" "M" "1轮廓实线层" "")
)
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;;;;;;;******壁厚标注函数;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;; 
(defun Thick_drw (thick materials pt1 pt2 dist ang / dp pt3 pt4x ll pt4y pt4 text 2dubs)
 (setvar "cmdecho" 0)
    (command "layer" "M" "7标注层" "")
    (setq dp (/ (distance pt1 pt2) 2))
    (setq pt3 (polar pt1 (* pi 0.75) dp));起始点,135度
  
    (setq pt4x (+ (car pt1) dist));终点横坐标
    (setq ll (abs (- pt4x (car pt3))));引出线的x坐标长度
    (setq pt4y (+ (cadr pt3) (* ll (tan ang) )));终点竖坐标
    (setq pt4 (list pt4x pt4y))
	;筒体材料不写入壁厚标注
	(setq text (strcat "{" "\\C3;" "t"(rtos thick)"}"))
	
	;筒体材料写入壁厚标注
	; (if (or (= materials nil) (= materials ""))
	    ; (setq text (strcat "{" "\\C3;" "t"(rtos thick)"}"))
		; (setq text (strcat "{" "\\C3;" "t"(strcat (rtos thick) "  " materials)"}"))
	; )
	
	    ; (progn
		   ; (print 111)
	       ; (if is_alert (alert "请明确筒体材料"))
	       ; (print "请明确筒体材料")
	       ; (setq materials "1")
	    ; );end progn
	; );end if 
		; ;(setq text (strcat "{" "\\C3;" "t"(rtos thick)"}"))
    ; (print 222)
	  (setq 2dubs (vlax-make-safearray vlax-vbDouble '(0 . 5)));一组的数组
  (vlax-safearray-fill 2dubs (append pt3 pt4))
  (setq 2dubs (vlax-make-variant 2dubs))
  (setq leader2 (vla-AddMLeader myms 2dubs 0 ));多重引线
  (vla-put-ArrowheadType leader2 3);引线头的样式3是实心圆点
  (vla-put-textstring leader2 (strcat "{" "\\C3;" "t"(rtos thick)"}"));
  ;(vlax-put-property leader2 'TextHeight (* mscale 4));更改文字大小
  (vla-put-ScaleFactor leader2 (* mscale 1));更改标注后的比例
  ;(vlax-put-property leader2 'ScaleFactor mscale);更改比例，达到的效果与更改文字大小相同
  ;(print (vlax-get-property  leader2 "TextHeight"))
  ;(vlax-dump-object leader2 t)
    ;(command "mleader" pt3 pt4 text)
    (princ)


 )
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


;;;;;;;;******壁厚标注函数;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;; 
(defun Thick_drw_1 (thick materials pt1 pt2 dist ang / dp pt3 pt4x ll pt4y pt4 text 2dubs)
 (setvar "cmdecho" 0)
    (command "layer" "M" "7标注层" "")
    (setq dp (/ (distance pt1 pt2) 2))
    (setq pt3 (polar pt1 (* pi 0.75) dp));起始点,135度
  
    (setq pt4x (+ (car pt1) dist));终点横坐标
    (setq ll (abs (- pt4x (car pt3))));引出线的x坐标长度
    (setq pt4y (+ (cadr pt3) (* ll (tan ang) )));终点竖坐标
    (setq pt4 (list pt4x pt4y))
	;筒体材料不写入壁厚标注
	(setq text (strcat "{" "\\C3;" "t"(rtos thick)"}"))
	
	;筒体材料写入壁厚标注
	; (if (or (= materials nil) (= materials ""))
	    ; (setq text (strcat "{" "\\C3;" "t"(rtos thick)"}"))
		; (setq text (strcat "{" "\\C3;" "t"(strcat (rtos thick) "  " materials)"}"))
	; )
	
	    ; (progn
		   ; (print 111)
	       ; (if is_alert (alert "请明确筒体材料"))
	       ; (print "请明确筒体材料")
	       ; (setq materials "1")
	    ; );end progn
	; );end if 
		; ;(setq text (strcat "{" "\\C3;" "t"(rtos thick)"}"))
    ; (print 222)
	  (setq 2dubs (vlax-make-safearray vlax-vbDouble '(0 . 5)));一组的数组
  (vlax-safearray-fill 2dubs (append pt3 pt4))
  (setq 2dubs (vlax-make-variant 2dubs))
  (setq leader2 (vla-AddMLeader myms 2dubs 0 ));多重引线
  (vla-put-ArrowheadType leader2 3);引线头的样式3是实心圆点
   (if (or (= materials nil) (= materials ""))
		(progn 
			(vla-put-textstring leader2 (strcat "{" "\\C3;" "t"(rtos thick)"}"))	
		)
		(vla-put-textstring leader2 (strcat "{" "\\C3;" "t"(strcat (rtos thick) "(" materials ")")"}"))	
  )
 ; (vla-put-textstring leader2 (strcat "{" "\\C3;" "t"(rtos thick)"}"));
  ;(vlax-put-property leader2 'TextHeight (* mscale 4));更改文字大小
  (vla-put-ScaleFactor leader2 (* mscale 1));更改标注后的比例
  ;(vlax-put-property leader2 'ScaleFactor mscale);更改比例，达到的效果与更改文字大小相同
  ;(print (vlax-get-property  leader2 "TextHeight"))
  ;(vlax-dump-object leader2 t)
    ;(command "mleader" pt3 pt4 text)
    (princ)


 )
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;;;;;;;******序号标注函数-向右侧拉序号;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;; 
(defun xhlead (num pt1 dist ang / pt3 pt4x ll pt4y pt4 text 2dubs leader2)
    (setvar "cmdecho" 0)
    (command "layer" "M" "7标注层" "")
    ;(setq pt3 (polar pt1 (* pi 0.75) pt1));起始点
  
    (setq pt4x (+ (car pt1) dist));终点横坐标
    (setq ll (abs (- pt4x (car pt1))));引出线的x坐标长度
    (setq pt4y (+ (cadr pt1) (* ll (tan ang) )));终点竖坐标
    (setq pt4 (list pt4x pt4y))
    ;(setvar "mleaderscale" mscale)
    (setq text (strcat "{" "\\C3;" (rtos num)"}"))
        (setq 2dubs (vlax-make-safearray vlax-vbDouble '(0 . 5)));6
    (vlax-safearray-fill 2dubs (append pt1 pt4))
    (setq 2dubs (vlax-make-variant 2dubs))
    (setq leader2 (vla-AddMLeader myms 2dubs 0 ));多重引线
    (vla-put-ArrowheadType leader2 8);引线头的样式，指示原点2
    (vla-put-textstring leader2 text);
    (vla-put-ScaleFactor leader2 (* mscale 1));更改标注后的比例
    ;(vlax-put-property leader2 'TextHeight (* mscale 4));更改文字大小
    ;(command "mleader" pt1 pt4 text)
    (princ)


 )
;;;;;;;;;;;;;;;;;;;;;;;;End xhlead;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;;;;;;;******序号标注函数-向左侧拉序号;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;; 
(defun xhlead1 (num pt1 dist ang / pt3 pt4x ll pt4y pt4 pt5 text 2dubs leader2)
    (setvar "cmdecho" 0)
    (command "layer" "M" "7标注层" "")
    ;(setq pt3 (polar pt1 (* pi 0.75) pt1));起始点
  
    (setq pt4x (- (car pt1) dist));终点横坐标
    (setq ll (abs (- pt4x (car pt1))));引出线的x坐标长度
    (setq pt4y (- (cadr pt1) (* ll (tan ang) )));终点竖坐标
    (setq pt4 (list pt4x pt4y 0))
    (setq pt5 (polar pt4 pi (* mscale 4)))
    ;(setvar "mleaderscale" mscale)
    (setq text (strcat "{" "\\C3;" (rtos num)"}"))

        (setq 2dubs (vlax-make-safearray vlax-vbDouble '(0 . 8)));6
    (vlax-safearray-fill 2dubs (append pt1 pt4 pt5))
    (setq 2dubs (vlax-make-variant 2dubs))
    (setq leader2 (vla-AddMLeader myms 2dubs 0 ));多重引线
    (vla-put-ArrowheadType leader2 8);引线头的样式
    (vla-put-textstring leader2 text);
    (vla-put-ScaleFactor leader2 (* mscale 1));更改标注后的比例
    ;(vlax-put-property leader2 'TextHeight (* mscale 4));更改文字大小

    ;(command "mleader" pt1 pt4 text)
    (princ)


 )
;;;;;;;;;;;;;;;;;;;;;;;;End xhlead1;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


;;;;;;;;******螺栓序号标注函数-向右侧拉序号;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;; 
(defun bolt_xhlead (num1 num2 num3 pt1 dist ang / pt2x ll pt2y pt2  text1 text2 text3 pt3 pt4 2dubs leader2
		    pt5 pt6 3dubs leader3)
  (setvar "cmdecho" 0)
  (command "layer" "M" "7标注层" "")  
  (setq pt2x (+ (car pt1) dist));终点横坐标
  (setq ll (abs (- pt2x (car pt1))));引出线的x坐标长度
  (setq pt2y (+ (cadr pt1) (* ll (tan ang) )));终点竖坐标
  (setq pt2 (list pt2x pt2y 0))
  ;(setvar "mleaderscale" mscale)
  (if (= (type num1) 'INT)
    (setq text1 (strcat "{" "\\C3;" (rtos num1)"}"))
    (setq text1 (strcat "{" "\\C3;"  num1 "}"))
  )
  (if (= (type num2) 'INT)
    (setq text2 (strcat "{" "\\C3;" (rtos num2)"}"))
    (setq text2 (strcat "{" "\\C3;"  num2 "}"))
  )
  (if (= (type num3) 'INT)
    (setq text3 (strcat "{" "\\C3;" (rtos num3)"}"))
    (setq text3 (strcat "{" "\\C3;"  num3 "}"))
  )
  ;(setq text1 (strcat "{" "\\C3;" (rtos num1)"}"))
  ;(setq text2 (strcat "{" "\\C3;" (rtos num2)"}"))
  ;(setq text3 (strcat "{" "\\C3;" (rtos num3)"}"))
  
  (command "mleader" pt1 pt2 text1)
  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  (setq pt3 (polar pt2 (* pi 1.5) 900))
  (setq pt4 (polar pt3 0 100))
  (setq 2dubs (vlax-make-safearray vlax-vbDouble '(0 . 5)));6个数为一组的数组
  (vlax-safearray-fill 2dubs (append pt2 pt3))
  (setq 2dubs (vlax-make-variant 2dubs))
  (setq leader2 (vla-AddMLeader myms 2dubs 0 ));多重引线
  (vla-put-ScaleFactor leader2 mscale);全局比例
  (vla-put-ArrowheadType leader2 19);引线头的样式，19是无箭头
  (vla-put-textstring leader2 text2);
  ;(vlax-dump-object leader2 t)

  (setq pt5 (polar pt3 (* pi 1.5) 900))
  (setq pt6 (polar pt5 0 100))
  (setq 3dubs (vlax-make-safearray vlax-vbDouble '(0 . 5)));6个数为一组的数组
  (vlax-safearray-fill 3dubs (append pt3 pt5))
  (setq 3dubs (vlax-make-variant 3dubs))

  (setq leader3 (vla-AddMLeader myms 3dubs 0 ));多重引线
  (vla-put-ScaleFactor leader3 mscale);全局比例
  (vla-put-ArrowheadType leader3 19);引线头的样式，19是无箭头
  (vla-put-textstring leader3 text3);
  
  (princ)


 )
;;;;;;;;;;;;;;;;;;;;;;;;End bolt_xhlead;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;;;;;;;******螺栓序号标注函数-向左侧拉序号;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;; 
(defun bolt_xhlead_l (num1 num2 num3 pt1 dist ang / pt2x ll pt2y pt2 dubs leader text1 text2 text3 pt3 pt4 2dubs leader2
		    pt5 pt6 3dubs leader3)
  (setvar "cmdecho" 0)
  (command "layer" "M" "7标注层" "")
  (setq pt1 (append pt1 '(0)))  
  (setq pt2x (+ (car pt1) dist));终点横坐标
  (setq ll (abs (- pt2x (car pt1))));引出线的x坐标长度
  (setq pt2y (+ (cadr pt1) (* ll (tan ang) )));终点竖坐标
  (setq pt2 (list pt2x pt2y 0))
  ;(setvar "mleaderscale" mscale)
  (if (= (type num1) 'INT)
    (setq text1 (strcat "{" "\\C3;" (rtos num1)"}"))
    (setq text1 (strcat "{" "\\C3;"  num1 "}"))
  )
  (if (= (type num2) 'INT)
    (setq text2 (strcat "{" "\\C3;" (rtos num2)"}"))
    (setq text2 (strcat "{" "\\C3;"  num2 "}"))
  )
  (if (= (type num3) 'INT)
    (setq text3 (strcat "{" "\\C3;" (rtos num3)"}"))
    (setq text3 (strcat "{" "\\C3;"  num3 "}"))
  )
  ;(setq text1 (strcat "{" "\\C3;" (rtos num1)"}"))
  ;(setq text2 (strcat "{" "\\C3;" (rtos num2)"}"))
  ;(setq text3 (strcat "{" "\\C3;" (rtos num3)"}"))
  
  (setq dubs (vlax-make-safearray vlax-vbDouble '(0 . 5)));
  (vlax-safearray-fill dubs (append pt1 pt2))
  (setq dubs (vlax-make-variant dubs))
  (setq leader (vla-AddMLeader myms dubs 0 ));多重引线
  (vla-put-ArrowheadType leader 8);引线头的样式
  (vla-put-textstring leader text1);
  (vla-put-ScaleFactor leader (* mscale 1));更改标注后的比例

  ;(command "mleader" pt1 pt2 text1)
  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  (setq pt3 (polar pt2 (* pi 1.5) 900))
  (setq pt4 (polar pt3 0 100))
  (setq 2dubs (vlax-make-safearray vlax-vbDouble '(0 . 5)));6个数为一组的数组
  (vlax-safearray-fill 2dubs (append pt2 pt3))
  (setq 2dubs (vlax-make-variant 2dubs))
  (setq leader2 (vla-AddMLeader myms 2dubs 0));多重引线
  ;(vla-put-ScaleFactor leader2 mscale);全局比例
  (vla-put-ArrowheadType leader2 19);引线头的样式，19是无箭头
  (vla-put-textstring leader2 text2);
  (vla-put-ScaleFactor leader2 (* mscale 1));更改标注后的比例
  
  ;(command "mirror" pt1 pt2 "y")
  
  ;(vlax-dump-object BlockConnectionType t)

  (setq pt5 (polar pt3 (* pi 1.5) 900))
  (setq pt6 (polar pt5 0 100))
  (setq 3dubs (vlax-make-safearray vlax-vbDouble '(0 . 5)));6个数为一组的数组
  (vlax-safearray-fill 3dubs (append pt3 pt5))
  (setq 3dubs (vlax-make-variant 3dubs))

  (setq leader3 (vla-AddMLeader myms 3dubs 0));多重引线
  (vla-put-ArrowheadType leader3 19);引线头的样式，19是无箭头
  (vla-put-textstring leader3 text3);
  (vla-put-ScaleFactor leader3 (* mscale 1));更改标注后的比例
  
  (princ)


 )
 ;;;;;;;;;;;;;;;;;;;;;;;;End bolt_xhlead_l;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;;;;;;;;;;;;;*********法兰放大视图符号函数********;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;主体上圈带符号
(defun FlangeZoom (pt FlangeIndex mscale isFirstFlange isreinforceplate / textData pt2 pt3 pt4 text 2dubs leader2);
    ;(setq pt (append pt '(0)))
    (setq textData (list "Ⅰ""Ⅱ""Ⅲ""Ⅳ""Ⅴ""Ⅵ""Ⅶ""Ⅷ""Ⅸ""Ⅹ""Ⅺ""Ⅻ"))
    (command "layer" "M" "8符号标注层" "")
    (command "circle" pt (* 5 mscale))
    (setvar "cmdecho" 0)
    (command "layer" "M" "7标注层" "")
    (setvar "cmleaderstyle" "GW2")
    (if isFirstFlange
      (progn;如果是底法兰
            ;(setq pt2 (polar pt (* pi 0.16) (* 5 mscale)))
            ;(setq pt3 (polar pt2 (* pi 0.16) (* 20 mscale)))
	      (setq pt2 (polar pt (- (* pi 0.16)) (* 5 mscale)))
            (setq pt3 (polar pt2 (- (* pi 0.16)) (* 20 mscale)))
      )
      (progn
        (setq pt2 (polar pt (* pi 0.16) (* 5 mscale)))
        (setq pt3 (polar pt2 (* pi 0.16) (* 20 mscale)))
      )
    )
    (setq 2dubs (vlax-make-safearray vlax-vbDouble '(0 . 5)));
    (vlax-safearray-fill 2dubs (append pt2 pt3))
    (if isreinforceplate
      (progn
          (setq pt (append pt '(0)))
          (setq pt2 (polar pt (* pi -0.67) (* 5 mscale)))
          (setq pt3 (polar pt2 (* pi -0.8) (* 20 mscale)))
          (setq pt4 (polar pt3 pi (* mscale 3)))
          (setq 2dubs (vlax-make-safearray vlax-vbDouble '(0 . 8)));
          (vlax-safearray-fill 2dubs (append pt2 pt3 pt4))
      )
    )
    (if (< FlangeIndex 0)
        (setq text (strcat "{" "\\C1;""Error""}"))
        (setq text (strcat "{" "\\C3;"(nth FlangeIndex textData)"}"))
    )
  (setq 2dubs (vlax-make-variant 2dubs))
  (setq leader2 (vla-AddMLeader myms 2dubs 0 ));多重引线
  (vla-put-ArrowheadType leader2 19);引线头的样式
  (vla-put-textstring leader2 text);
  (vlax-put-property leader2 'TextHeight (* mscale 4));更改文字大小
    (princ)
    (setvar "cmleaderstyle" "GW")
);defun函数FlangeZoom右括号
;;;;;;;;;;;;;;;;;;;;End drawing;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;;;;;;;;;;;;*********法兰放大视图注释+比例标注;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defun FlangeZoomTitle(TitlePt FlangeIndex scale / textData Line_R Line_L ViewNamePt ViewScalePt);原点，序号
  (setq textData (list "Ⅰ""Ⅱ""Ⅲ""Ⅳ""Ⅴ""Ⅵ""Ⅶ""Ⅷ""Ⅸ""Ⅹ""Ⅺ""Ⅻ"))
  (setq Line_R (polar TitlePt 0 (* 4 mscale)))
  (setq Line_L (polar TitlePt pi (* 4 mscale)))
  (command "layer" "M" "7标注层" "")
  (command "line" Line_L line_R "")
  (setq ViewNamePt (polar TitlePt (/ pi 2) (* 1.5 mscale)))
  (setq ViewScalePt (polar TitlePt (* pi 1.5) (* 6.5 mscale)))
  (command "layer" "M" "6文字层" "")
  (command "text" "C" ViewNamePt (* 5 mscale) 0 (nth FlangeIndex textData))
  ;(command "text" "C" ViewScalePt (* 5 mscale) 0 (strcat "1:"(rtos (/ mscale 20))))
  (command "text" "C" ViewScalePt (* 5 mscale) 0 (strcat "1:"(rtos scale)))
  (command "layer" "M" "1轮廓实现层" "")
)
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;;;;;;;;;;;;;;;;;;;;****塔架标高标注函数****;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defun elevation(eleValue / pt pt1 pt_temp pt2 pt3 text textLength pt4 pt5 eleName)
    (setvar 'dimzin 8) ;消零，舍弃无效的尾零
    (setq pt  '(0 0 0);单引号‘的意思是禁止求值,列表（0 0 0 ）
 	  pt1 (polar pt pi 90);返回距pt点，pi角度，距离90的点的位置
	  pt1 (polar pt pi 80)
	  pt_temp (polar pt1 (* pi 0.5) 2.5)
	  pt2 (polar pt_temp pi 2.5)
	  pt3 (polar pt_temp 0 2.5)
	  text (strcat (rtos eleValue 2 2)"m");rtos：实数转化成字符串，strcat：合并字符串
	  textLength (strlen text);字符串构成的字符数量
	  pt4 (polar pt3 0 (* 2 textLength))
	  pt5 (polar pt1 pi 2.5))
    (setq eleName (strcat "elevation" (rtos eleValue)))
    (entmake (list '(0 . "BLOCK") (cons 2 eleName) '(70 . 0) (cons 10 pt)));将新元素添加到列表,（cons  新元素 列表);list:将所有元素合并为一列表
    (entmake (list '(0 . "line") (cons 10 pt5) (cons 11 pt)(cons 8"7标注层")))
    (entmake (list '(0 . "line") (cons 10 pt1) (cons 11 pt2)(cons 8"标注")))
    (entmake (list '(0 . "line") (cons 10 pt1) (cons 11 pt3)(cons 8"标注")))
    (entmake (list '(0 . "line") (cons 10 pt2) (cons 11 pt4)(cons 8"标注")))
    (entmake (list '(0 . "text") (cons 1 text) (cons 10 pt3) (cons 40 3.5)(cons 8"6文字层")(cons 7"PC_TEXTSTYLE")))
    (entmake '((0 . "endblk")))
    (setq eleName (strcat "elevation" (rtos eleValue)))
);defun函数elevatione的右括号
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;;;;;;;;*************打剖面线函数;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defun bhatch_pt(pt1 pt2 scale angle_bhatch / middle_pt)
  ;;pt1 pt2为闭合空间斜角两点
  ;;scale 比例
  ;;angle_bhatch 剖面线角度
  (command "zoom" "w" pt1 pt2)
  (command "regen")
  (command "zoom" "w" (polar pt1 (* 0.75 pi) (* 50 scale)) (polar pt2 (* -0.25 pi) (* 50 scale)))
  (setq scale (* 2.5 scale))
  (command "layer" "M" "5剖面线层" "")
  (setq middle_pt (MiddlePoint pt1 pt2))
  (setvar "HPGAPTOL" 0 ) ;容差
  (command "bhatch" "p" "ansi31" scale angle_bhatch middle_pt "")
  (princ)
)
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;JUDGE
;excel表数据相关判断
(defun prejudge (/ logname logdir)
  (if logsystem;如果日志系统为真
    (progn
      (setq logName "logdata");明细表的名称
      (setq logdir (strcat rootdir logName ".dat"))
      ;(print logdir)
      ;(setq preBomName (GetDir TowerExcelFile));明细表的名称
      (setq logText (open logdir "w"))
      (write-line "图纸仅供参考，请务必仔细检查Excel数据表和图纸！" logText);把BomTitle写到BomListTxt
      
    )
  )
  (sthjudge);上下标高判断
  (evenjudge);法兰螺栓奇偶数判断
  (neckjudge);法兰表中的壁厚与主体表中的壁厚是否相同判断
  (if (not midornot)
    (dijudge);相邻筒节外径是否相等判断
  )
 ;(dijudge);相邻筒节外径是否相等判断

  (boltholejudge);螺栓孔直径与螺栓孔直径是否差3判断

  (embedthickjudge)
  
  (embedneckjudge);基础环顶法兰脖子厚度与底法兰脖子厚度是否相同判断
  (anchorMomentjudge);T型底法兰是否给预紧力判断
  (replateDjudge);门框处外径与主体表中外径是否相同判断
  (replatethickjudge);加强板厚度判断
  
  (flneckjudge);法兰脖子高度判断
  (print "查看")
  (tflsymmetry);T型法兰对称性判断
  (flheight);法兰表中的法兰高度与主体表中的高度判断
  (FlangeCheck section_qty);法兰空间判断
  (fldjudge);法兰空表中外径与主体表中外径判断
  (topflheighttoweld);法兰表中顶法兰标高与主体表中顶法兰标高判断
  
  (close logText)
  (print "数据判断完成！")
)
;;;;;;函数结束

;;;;;;;;;;;附件能否借用判断;;;;;;曹学敏新增
(defun asm_judge(/ towerHeight towerSection miDdia i j k p q asmLoc InnDia_H InnDiaDif_H ErrorHeight );
  (setq towerheight (- (value retFlange section_qty 0) (value retFlange 0 0)));塔架总高
  (setq towerSection section_qty) ;塔筒段数
  (setq midDia (atoi (rtos (-(value retTower 0 1) (value retTower 0 4)))))  ;塔筒中径

  (cond    ;根据体型判断与塔架附件设计表sheet列表中的哪个sheet中的数据做对比
      (  (and(> towerheight 95) (<= towerheight 100) (= towerSection 4) (= midDia 4450))  (setq i 0)   )
	  (  (and(> towerheight 95) (<= towerheight 100) (= towerSection 4) (= midDia 4950))  (setq i 1)   )
	  (  (and(> towerheight 95) (<= towerheight 100) (= towerSection 5) (= midDia 4450))  (setq i 2)   )
	  (  (and(> towerheight 95) (<= towerheight 100) (= towerSection 5) (= midDia 4950))  (setq i 3)   )
	  (  (and(> towerheight 100) (<= towerheight 105) (= towerSection 5) (= midDia 4450))  (setq i 4)   )
	  (  (and(> towerheight 100) (<= towerheight 105) (= towerSection 5) (= midDia 4950))  (setq i 5)   )
	  (  (and(> towerheight 105) (<= towerheight 110) (= towerSection 5) (= midDia 4450))  (setq i 6)   )
	  (  (and(> towerheight 105) (<= towerheight 110) (= towerSection 5) (= midDia 4950))  (setq i 7)   )
	  (  (and(> towerheight 110) (<= towerheight 115) (= towerSection 5) (= midDia 4450))  (setq i 8)   )
	  (  (and(> towerheight 110) (<= towerheight 115) (= towerSection 5) (= midDia 4950))  (setq i 9)   )
	  (  (and(> towerheight 115) (<= towerheight 120) (= towerSection 5) (= midDia 4450))  (setq i 10)   )
	  (  (and(> towerheight 115) (<= towerheight 120) (= towerSection 5) (= midDia 4950))  (setq i 11)   )
	  (  (and(> towerheight 115) (<= towerheight 120) (= towerSection 6) (= midDia 4450))  (setq i 12)   )
	  (  (and(> towerheight 115) (<= towerheight 120) (= towerSection 6) (= midDia 4950))  (setq i 13)   )
	  (  (and(> towerheight 125) (<= towerheight 130) (= towerSection 6) (= midDia 4950))  (setq i 14)   )
	  (  (and(> towerheight 125) (<= towerheight 130) (= towerSection 5) (= midDia 5950))  (setq i 15)   )
	  (  (and(> towerheight 135) (<= towerheight 140) (= towerSection 6) (= midDia 5950))  (setq i 16)   )
	  (  (and(> towerheight 140) (<= towerheight 145) (= towerSection 6) (= midDia 5950))  (setq i 17)   )
  );end cond

  (if (= i nil)
    (progn
      (alert "无此种体型塔架，程序退出！")
      (exit)
    )
  )
  (setq retAsm (nth i retTowerDesignlist))  ;获取体型对应的塔架设计表参数

;(print retAsm)
  (setq j 0 );j-塔筒段

  (while (<= j (- towerSection 1)) ;直到顶段
      (setq k 5 p 6 q 7) ;k-附件表中附件位置列、P-附件表中中径列、q-附件表中调节范围列	  
      (while (<= k 17)  ;直到顶平台对比
	    
;(print asmLoc)
;(print (type asmLoc))
		(if 
		   (and (/= (value retAsm j k) nil) (/= (value retAsm j k) ""))
		   (progn 
	          (setq asmLoc (- (value retFlange (+ j 1) 0) (/ (value retAsm j k) 1000)));附件表中标高值=该段法兰标高-距离上法兰的距离
			  (setq InnDia_H (calInnDia asmLoc))  ;;;标高对应的塔筒内径
;(print InnDia_H)
	          (setq InnDiaDif_H (- InnDia_H (value retAsm j p)))  ;;;求设计表附件直径与塔筒主体位置处直径差值
;(print InnDiaDif_H)
	          (if (> (abs InnDiaDif_H) (value retAsm j q))  ;;;直径差值在设计范围外，则提示+输出+写入
		         (progn  
		           (setq ErrorAsm T)
				   (setq ErrorHeight (rtos asmLoc))
			       (if is_alert (alert (strcat "标高：" ErrorHeight "m处，直径不满足附件借用要求，请检查！" )))
			       (print (strcat "标高：" ErrorHeight "m处，直径不满足附件借用要求，请检查！" ))
			       ;;;;;;;;;(write-line (strcat "标高：" ErrorHeight "m处，直径不满足附件借用要求，请检查！" ) logText)
		         );end progn
	          );end if
			);end progn
	    );end if
		(setq k (+ k 3))
;(print k)
		(setq p (+ p 3))
;(print p)
		(setq q (+ q 3))
;(print q)
	  );end while
	  
	  (print (strcat (value retAsm j 0) "附件是否可借用判断完毕"))
	  (setq j (+ j 1))
		
  );end while 

);;;;;;;end defun


;;;;;;T型底法兰内外螺栓孔对称判断;;;;;
(defun tflsymmetry(/ i k dm da dmout s a1 a2 ErrorHeight )
  (setq i 0)
  (setq k flange_qty)
  (while (< i k)
    (if (TorLflange i);如果是T型法兰
      (progn
        (setq dm (value retFlange i 3));T型法兰内分度圆
        (setq da (value retFlange i 1));T法兰外径
        (setq dmout (value retFlange i 14));T型法兰外分度圆
        (setq s (value retFlange i 5));法兰颈厚

        (setq a1 (- da (* 2 s) dm));内分度圆距离法兰内壁距离
        (setq a2 (- dmout da));外分度圆距离法兰外壁距离
        (setq ErrorHeight (rtos (value retFlange i 0)))

        (setq a1 (rtos a1 2 2))
	(setq a2 (rtos a2 2 2))
	;(print (type a1))
	;(print (type a2))
	;(print a1)
	;(print a2)

	;(print (- a1 a2))
	;(print dmout)
	
        (if (/= a1 a2)
	  (progn
            (if (= is_alert T)
	      (alert (strcat "标高：" ErrorHeight "m处,T型法兰内外分度圆不对称，请检查Excel！"))
            )
	    	      	;(print a1)
	        ;(print a2)
            (print (strcat "标高：" ErrorHeight "m处,T型法兰内外分度圆不对称，请检查Excel！"))
          );end progn
	);if的右括号
      );end progn
    );if右括号
    (setq i (+ i 1))
  );end while
);end tflsysmetry


;上下标高判断标高判断;;;;;;;;;;;;;;;;;
(defun sthjudge(/ j ErrorHeight);,standard height judge,判断主体表中标高是否相同
    (setq j 0)
    (while (and (/= (value retTower j 2) "") (/= (value retTower j 2) nil));判断标高的值是否为空，如果不等于空
        (if (and (> j 0) (/= (rtos (value retTower j 0)) (rtos (value retTower (- j 1) 2))))  ;若上下标高值不同;判断上下标高值是否相等
            (progn
				(setq ErrorHeight (rtos (value retTower j 0)))
				(if (= is_alert T)
					(alert (strcat "标高：" ErrorHeight "m处有误,上下标高不同，请检查Excel！"))
				)
       	        (print (strcat "标高：" ErrorHeight "m处有误,请检查Excel！"))
      	        (print (strcat (rtos (value retTower j 0)) "与" (rtos (value retTower (- j 1) 2)) "不相等" ))
				(write-line (strcat "标高：" ErrorHeight "m处有误,上下标高不同，请检查Excel！") logText)
            )
        );;;if的右括号，这个if用来判断上下标高是否相同
        (setq j (+ j 1))
    )
)
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;螺栓孔判断;;;;;;;;;;;;;;;;;判断螺栓孔直径与螺栓直径是否填反
(defun boltholejudge(/ i ErrorHeight bolt_d);,standard height judge,判断主体表中标高是否相同
	(setq i 0)
	(setq k flange_qty)  
	(while (< i k)
		(setq ErrorHeight (rtos (value retFlange i 0)))

		;混塔底法兰
		(if (and (= i 0) (= (value retFlange 0 7) "预应力索"))
			(setq bolt_d (- (value retFlange 0 8) 3));螺栓公称直径
			(setq bolt_d (value retFlange i 7))
		)
    
		(if (/= i 0)
			(if (> (value retFlange i 7) 64)
				(progn
					(if (and (/= (rtos (- (value retFlange i 8) 6)) (rtos (value retFlange i 7))))
						(progn	    
							(if (= is_alert T)
								(alert (strcat "标高：" ErrorHeight "m处,螺栓孔直径与螺栓直径错误，请检查Excel！"))
							)
							(print (strcat "标高：" ErrorHeight "m处,螺栓孔直径与螺栓直径错误，请检查Excel！"))
							(write-line (strcat "标高：" ErrorHeight "m处,螺栓孔直径与螺栓直径错误，请检查Excel！") logText)
						);End progn
					)
				)	
				(if (and (/= (rtos (- (value retFlange i 8) 3)) (rtos (value retFlange i 7))))
					(progn	    
						(if (= is_alert T)
							(alert (strcat "标高：" ErrorHeight "m处,螺栓孔直径与螺栓直径错误，请检查Excel！"))
						)
						(print (strcat "标高：" ErrorHeight "m处,螺栓孔直径与螺栓直径错误，请检查Excel！"))
						(write-line (strcat "标高：" ErrorHeight "m处,螺栓孔直径与螺栓直径错误，请检查Excel！") logText)
					);End progn		
				);End if	
			)
				
		)
		(if (and (= i 0) (and (/= (rtos (- (value retFlange i 8) 3)) (rtos bolt_d)) (/= (rtos (- (value retFlange i 8) 6)) (rtos bolt_d)) ) )
			(progn	    
				(if (= is_alert T)
					(alert (strcat "标高：" ErrorHeight "m处,螺栓孔直径与螺栓直径错误，请检查Excel！"))
				)
				(print (strcat "标高：" ErrorHeight "m处,螺栓孔直径与螺栓直径错误，请检查Excel！"))
				(write-line (strcat "标高：" ErrorHeight "m处,螺栓孔直径与螺栓直径错误，请检查Excel！") logText)
			);End progn
		);End if
		
		
    
		(setq i (+ i 1))
	)
)
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;



;;;;;;;;;;法兰螺栓孔奇偶数判断;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defun evenjudge (/ i k ErrorHeight)
  (setq i 0)
  (setq k flange_qty)
  (while (< i k)
    (if (and (/= (value retFlange i 9) "") (/= (value retFlange i 9) nil));如果螺栓数里面有内容
      (progn
	(if (TorLflange i);如果是T型法兰
	  (progn
            (if (/= (rem (value retFlange i 9) 4) 0);如果不是4的整数倍
	      (progn 
	        (setq ErrorHeight (rtos (value retFlange i 0)))
		(if (= is_alert T)
	          (alert (strcat "标高：" ErrorHeight "m处,法兰螺栓孔数不是4的倍数，请检查Excel！"))
		)
		(print (strcat "标高：" ErrorHeight "m处,法兰螺栓孔数不是4的倍数，请检查Excel！"))
		
		(write-line (strcat "标高：" ErrorHeight "m处,法兰螺栓孔数不是4的倍数，请检查Excel！") logText)
	      )
	    )
	  )
	  (progn
            (if (/= (rem (value retFlange i 9) 2) 0)
	      (progn
		(setq ErrorHeight (rtos (value retFlange i 0)))
		(if (= is_alert T)
	          (alert (strcat "标高：" ErrorHeight "m处,法兰螺栓孔数不是2的倍数，请检查Excel！"))
		)
		(print (strcat "标高：" ErrorHeight "m处,法兰螺栓孔数不是2的倍数，请检查Excel！"))
		(write-line (strcat "标高：" ErrorHeight "m处,法兰螺栓孔数不是2的倍数，请检查Excel！") logText)
	      )
	    )
	  )
	)
      )
    )
    (setq i (+ i 1))
  )
)
;;;;;;;;;;函数技术;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;;;;;;;判断法兰表中的颈厚与主体表中的颈厚是否相同;;;;;;
(defun neckjudge(/ i k ErrorHeight)
  (setq i 0)
  (setq k flange_qty)
  (while (< i k)
    (if ( /= (rtos (value retFlange i 5)) (rtos (value retTower (nth i pos) 4)) )
      (progn
	(setq ErrorHeight (rtos (value retFlange i 0)))
	(if (= is_alert T)
	  (alert (strcat "标高：" ErrorHeight "m处,法兰颈厚与主体中颈厚不同，请检查Excel！"))
	)
	(print (strcat "标高：" ErrorHeight "m处,法兰颈厚与主体中颈厚不同，请检查Excel！"))
	(write-line (strcat "标高：" ErrorHeight "m处,法兰颈厚与主体中颈厚不同，请检查Excel！") logText)
      )
    )
    (setq i (+ i 1))
  )
)
;;;;;;;函数结束;;;;;;;;;;;;


;(setq k (length retTower))

;;;;;;;;判断法兰表中的顶法兰标高与主体表中的顶法兰标高是否相同;;;;;;
(defun topflheighttoweld(/ i k ErrorHeight y a1 a2 count x)
  (setq k (- flange_qty 1))
  (setq a1 (rtos (value retFlange k 0)));法兰表中的顶法兰的标高
  ;(print (rtos (value retFlange k 0)))
  ;(print retTower)
  (setq count 0)
  (foreach x retTower
    (setq y (vlax-variant-value (nth 0 x)))
    (if (/= y nil)
      (setq count (+ count 1))
    ) 
  );end foreach
  ;(print count)
  ;(print (value rettower (- count 1) 2))
  (setq a2 (rtos (value rettower (- count 1) 2)) )
  ;(print a1)
  ;(print a2)
  (if ( /= a1 a2)
      (progn
  	(setq ErrorHeight (rtos (value retFlange k 0)))
  	(if (= is_alert T)
  	  (alert (strcat "标高：" ErrorHeight "m处,法兰表中顶法兰标高与主体表中不同，请检查Excel！"))
  	)
  	(print (strcat "标高：" ErrorHeight "m处,法兰表中顶法兰标高与主体表中不同，请检查Excel！"))
  	(write-line (strcat "标高：" ErrorHeight "m处,法兰表中顶法兰标高与主体表中不同，请检查Excel！") logText)
      )
  );end if 
);end defun
;;;;;;;函数结束;;;;;;;;;;;;



;;;;;;;;判断法兰表中的外径与主体表中的外径是否相同;;;;;;
(defun fldjudge(/ i k ErrorHeight)
  (setq i 0)
  (setq k flange_qty)
  (while (< i k)
    (if ( /= (rtos (value retFlange i 1)) (rtos (value retTower (nth i pos) 1)) )
      (progn
	(setq ErrorHeight (rtos (value retFlange i 0)))
	(if (= is_alert T)
	  (alert (strcat "标高：" ErrorHeight "m处,法兰外径与主体中外径不同，请检查Excel！"))
	);end if
	(print (strcat "标高：" ErrorHeight "m处,法兰外径与主体中外径不同，请检查Excel！"))
	(write-line (strcat "标高：" ErrorHeight "m处,法兰外径与主体中外径不同，请检查Excel！") logText)
      );end progn
    );end if
    (setq i (+ i 1))
    ;(print (value retFlange i 1))
    ;(print (value retTower (nth i pos) 1))
  );end while
);end fldjudge
;;;;;;;函数结束;;;;;;;;;;;;

;;;;;;;;;相邻筒节的外径是否相等;;;;;;;;;;;;;;;;;;;;;;;;;;
(defun dijudge(/ i ErrorHeight)
  (setq i 0)
  (while (< i (- towernum 1));直到倒数第二行
    (progn
      (if (/=  (rtos (value retTower i 3)) (rtos (value retTower (+ i 1) 1)))
	(progn
	  (setq ErrorHeight (rtos (value retTower i 0)))
	  (if (= is_alert T)
	    (alert (strcat "标高：" ErrorHeight "m处,上下外径不同，请检查Excel！"))
	  )
	  (print (strcat "标高：" ErrorHeight "m处,上下外径不同，请检查Excel！"))
	  (write-line (strcat "标高：" ErrorHeight "m处,上下外径不同，请检查Excel！") logText)
	)
      )
    )
    (setq i (+ i 1))
  )
)
;;;;;;;;;;函数结束;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;;;;;颈高判断
(defun flneckjudge (/ ii i ErrorHeight L_fl tn_fl L_fl_temp);法兰颈高判断,l_fl:法兰颈高，tn_fl:此法兰颈厚
	(setq i 0)
	; (setq ii (- flange_qty 1))
	(while (< i (- section_qty 1))
		(setq tn_fl (value retFlange i 5));颈厚
		(setq L_fl (value retFlange i 6));颈高     
		(setq ErrorHeight (rtos (value retFlange i 0)))
		(if (= (value retFlange i 11)"T");判断是否是T法兰
			(progn
				(if(< tn_fl 25)
					(if (/= L_fl 35);如果不为35，就错了
						(progn
							(if is_alert
								;(alert (strcat "标高：" ErrorHeight "m处,颈高有误，请检查Excel表格！" "程序将自动定义此处标高为40mm进行绘制！"))
								(alert (strcat "标高：" ErrorHeight "m处,颈高有误，请检查Excel表格！" ))
							)
							(print (strcat "标高：" ErrorHeight "m处,颈高有误，请检查Excel表格！"))
							(write-line (strcat "标高：" ErrorHeight "m处,颈高有误，请检查Excel表格！") logText)
						)
					)
				);end if
				(if(and(>= tn_fl 25)(<= tn_fl 35))
					(if (/= L_fl 40);如果不为40，就错了
					(progn
						(if is_alert
							;(alert (strcat "标高：" ErrorHeight "m处,颈高有误，请检查Excel表格！" "程序将自动定义此处标高为40mm进行绘制！"))
							(alert (strcat "标高：" ErrorHeight "m处,颈高有误，请检查Excel表格！" ))
						)
						(print (strcat "标高：" ErrorHeight "m处,颈高有误，请检查Excel表格！"))
						(write-line (strcat "标高：" ErrorHeight "m处,颈高有误，请检查Excel表格！") logText)
					)
					)
				);end if
				(if(and(> tn_fl 35)(<= tn_fl 44))
					(if (/= L_fl 45);如果不为45，就错了
					(progn
						(if is_alert
							;(alert (strcat "标高：" ErrorHeight "m处,颈高有误，请检查Excel表格！" "程序将自动定义此处标高为40mm进行绘制！"))
							(alert (strcat "标高：" ErrorHeight "m处,颈高有误，请检查Excel表格！" ))
						)
						(print (strcat "标高：" ErrorHeight "m处,颈高有误，请检查Excel表格！"))
						(write-line (strcat "标高：" ErrorHeight "m处,颈高有误，请检查Excel表格！") logText)
					)
					)
				);end if
				(if(and(> tn_fl 44)(<= tn_fl 54))
					(if (/= L_fl 50);如果不为50，就错了
					(progn
						(if is_alert
							;(alert (strcat "标高：" ErrorHeight "m处,颈高有误，请检查Excel表格！" "程序将自动定义此处标高为40mm进行绘制！"))
							(alert (strcat "标高：" ErrorHeight "m处,颈高有误，请检查Excel表格！" ))
						)
						(print (strcat "标高：" ErrorHeight "m处,颈高有误，请检查Excel表格！"))
						(write-line (strcat "标高：" ErrorHeight "m处,颈高有误，请检查Excel表格！") logText)
					)
					)
				);end if
				(if(and(> tn_fl 54)(<= tn_fl 64))
					(if (/= L_fl 55);如果不为55，就错了
					(progn
						(if is_alert
							;(alert (strcat "标高：" ErrorHeight "m处,颈高有误，请检查Excel表格！" "程序将自动定义此处标高为40mm进行绘制！"))
							(alert (strcat "标高：" ErrorHeight "m处,颈高有误，请检查Excel表格！" ))
						)
						(print (strcat "标高：" ErrorHeight "m处,颈高有误，请检查Excel表格！"))
						(write-line (strcat "标高：" ErrorHeight "m处,颈高有误，请检查Excel表格！") logText)
					)
					)
				);end if
				(if(and(> tn_fl 64)(<= tn_fl 74))
					(if (/= L_fl 60);如果不为60，就错了
					(progn
						(if is_alert
							;(alert (strcat "标高：" ErrorHeight "m处,颈高有误，请检查Excel表格！" "程序将自动定义此处标高为40mm进行绘制！"))
							(alert (strcat "标高：" ErrorHeight "m处,颈高有误，请检查Excel表格！" ))
						)
						(print (strcat "标高：" ErrorHeight "m处,颈高有误，请检查Excel表格！"))
						(write-line (strcat "标高：" ErrorHeight "m处,颈高有误，请检查Excel表格！") logText)
					)
					)
				);end if
				(if (> tn_fl 74)
					(if (/= L_fl 65);如果不为65，就错了
					(progn
						(if is_alert
							;(alert (strcat "标高：" ErrorHeight "m处,颈高有误，请检查Excel表格！" "程序将自动定义此处标高为40mm进行绘制！"))
							(alert (strcat "标高：" ErrorHeight "m处,颈高有误，请检查Excel表格！" ))
						)
						(print (strcat "标高：" ErrorHeight "m处,颈高有误，请检查Excel表格！"))
						(write-line (strcat "标高：" ErrorHeight "m处,颈高有误，请检查Excel表格！") logText)
					)
					)
				);end if
				; (progn
				; (setq L_fl_temp (+ (* 0.885 tn_fl) 10));+圆角，这给定死了为10
				; (if (> (/ L_fl_temp 10) (atoi (rtos (/ L_fl_temp 10) 2 0)))
					; (setq L_fl_temp (+ (* (atoi (rtos (/ L_fl_temp 10) 2 0)) 10) 5))
					; (setq L_fl_temp (* (atoi (rtos (/ L_fl_temp 10) 2 0)) 10))
				; );end if
				; (if (/= L_fl  L_fl_temp);
					; (progn
						; (if is_alert
							; (alert (strcat "标高：" ErrorHeight "m处,颈高有误，请检查Excel表格！" ))
							; ;(alert (strcat "标高：" ErrorHeight "m处,颈高有误，请检查Excel表格！" "程序将自动定义此处标高为" (rtos L_fl_temp) "进行绘制！"))
						; )
						; (print (strcat "标高：" ErrorHeight "m处,颈高有误，请检查Excel表格！"))
						; (write-line (strcat "标高：" ErrorHeight "m处,颈高有误，请检查Excel表格！") logText)
					; )
				; );end if
				; );end progn
			);end cond
			(progn  
				(if(< tn_fl 25)
					(if (/= L_fl 30);如果不为30，就错了
						(progn
								(if is_alert
									;(alert (strcat "标高：" ErrorHeight "m处,颈高有误，请检查Excel表格！" "程序将自动定义此处标高为40mm进行绘制！"))
									(alert (strcat "标高：" ErrorHeight "m处,颈高有误，请检查Excel表格！" ))
								)
								(print (strcat "标高：" ErrorHeight "m处,颈高有误，请检查Excel表格！"))
								(write-line (strcat "标高：" ErrorHeight "m处,颈高有误，请检查Excel表格！") logText)
								
							)
							
						)
				);end if
				(if(and(>= tn_fl 25)(<= tn_fl 35))
					(if (/= L_fl 35);如果不为35，就错了
							(progn
								(if is_alert
									;(alert (strcat "标高：" ErrorHeight "m处,颈高有误，请检查Excel表格！" "程序将自动定义此处标高为40mm进行绘制！"))
									(alert (strcat "标高：" ErrorHeight "m处,颈高有误，请检查Excel表格！" ))
								)
								(print (strcat "标高：" ErrorHeight "m处,颈高有误，请检查Excel表格！"))
								(write-line (strcat "标高：" ErrorHeight "m处,颈高有误，请检查Excel表格！") logText)
							)
						)
				);end if
				(if(and(> tn_fl 35)(<= tn_fl 44))
						(if (/= L_fl 40);如果不为40，就错了
							(progn
								(if is_alert
									;(alert (strcat "标高：" ErrorHeight "m处,颈高有误，请检查Excel表格！" "程序将自动定义此处标高为40mm进行绘制！"))
									(alert (strcat "标高：" ErrorHeight "m处,颈高有误，请检查Excel表格！" ))
								)
								(print (strcat "标高：" ErrorHeight "m处,颈高有误，请检查Excel表格！"))
								(write-line (strcat "标高：" ErrorHeight "m处,颈高有误，请检查Excel表格！") logText)
							)
						)
				);end if
					(if(and(> tn_fl 44)(<= tn_fl 54))
						(if (/= L_fl 45);如果不为45，就错了
							(progn
								(if is_alert
								;(alert (strcat "标高：" ErrorHeight "m处,颈高有误，请检查Excel表格！" "程序将自动定义此处标高为40mm进行绘制！"))
								(alert (strcat "标高：" ErrorHeight "m处,颈高有误，请检查Excel表格！" ))
								)
								(print (strcat "标高：" ErrorHeight "m处,颈高有误，请检查Excel表格！"))
								(write-line (strcat "标高：" ErrorHeight "m处,颈高有误，请检查Excel表格！") logText)
							)
						)
					);end if
					(if(and(> tn_fl 55)(<= tn_fl 64))
						(if (/= L_fl 50);如果不为50，就错了
							(progn
								(if is_alert
									;(alert (strcat "标高：" ErrorHeight "m处,颈高有误，请检查Excel表格！" "程序将自动定义此处标高为40mm进行绘制！"))
									(alert (strcat "标高：" ErrorHeight "m处,颈高有误，请检查Excel表格！" ))
								)
								(print (strcat "标高：" ErrorHeight "m处,颈高有误，请检查Excel表格！"))
								(write-line (strcat "标高：" ErrorHeight "m处,颈高有误，请检查Excel表格！") logText)
							)
						)
					);end if
					(if(and(> tn_fl 64)(<= tn_fl 74))
						(if (/= L_fl 55);如果不为55，就错了
							(progn
								(if is_alert
									;(alert (strcat "标高：" ErrorHeight "m处,颈高有误，请检查Excel表格！" "程序将自动定义此处标高为40mm进行绘制！"))
									(alert (strcat "标高：" ErrorHeight "m处,颈高有误，请检查Excel表格！" ))
								)
								(print (strcat "标高：" ErrorHeight "m处,颈高有误，请检查Excel表格！"))
								(write-line (strcat "标高：" ErrorHeight "m处,颈高有误，请检查Excel表格！") logText)
							)
						)
					);end if
					(if (> tn_fl 74)
						(if (/= L_fl 60);如果不为60，就错了
							(progn
								(if is_alert
									;(alert (strcat "标高：" ErrorHeight "m处,颈高有误，请检查Excel表格！" "程序将自动定义此处标高为40mm进行绘制！"))
									(alert (strcat "标高：" ErrorHeight "m处,颈高有误，请检查Excel表格！" ))
								)
								(print (strcat "标高：" ErrorHeight "m处,颈高有误，请检查Excel表格！"))
								(write-line (strcat "标高：" ErrorHeight "m处,颈高有误，请检查Excel表格！") logText)
							)
						)
					);end if
					; (progn
					; (setq L_fl_temp (+ (* 0.885 tn_fl) 10));+圆角，这给定死了为10
					; (if (> (/ L_fl_temp 10) (atoi (rtos (/ L_fl_temp 10) 2 0)))
						; (setq L_fl_temp (+ (* (atoi (rtos (/ L_fl_temp 10) 2 0)) 10) 5))
						; (setq L_fl_temp (* (atoi (rtos (/ L_fl_temp 10) 2 0)) 10))
					; );end if
					; (if (/= L_fl  L_fl_temp);
						; (progn
							; (if is_alert
								; (alert (strcat "标高：" ErrorHeight "m处,颈高有误，请检查Excel表格！" ))
								; ;(alert (strcat "标高：" ErrorHeight "m处,颈高有误，请检查Excel表格！" "程序将自动定义此处标高为" (rtos L_fl_temp) "进行绘制！"))
							; )
							; (print (strcat "标高：" ErrorHeight "m处,颈高有误，请检查Excel表格！"))
							; (write-line (strcat "标高：" ErrorHeight "m处,颈高有误，请检查Excel表格！") logText)
						; )
					; );end if
					; );end progn
				)	
				
			
		)	;endif
		(setq i (+ i 1))
	);end while
)
; (defun flneckjudge (/ i ErrorHeight L_fl tn_fl L_fl_temp);法兰颈高判断,l_fl:法兰颈高，tn_fl:此法兰颈厚
	; (setq i 0)
	; (while (< i section_qty)
		; (setq tn_fl (value retFlange i 5));颈厚
		; (setq L_fl (value retFlange i 6));颈高     
		; (setq ErrorHeight (rtos (value retFlange i 0)))
		; (if(<= tn_fl 36)) ;如果颈厚小于36时
			; (if (<= tn_fl 36);如果颈厚小于36时
			; (if (/= L_fl 40.0);如果不为40，就错了
				; (progn
					; (if is_alert
						; ;(alert (strcat "标高：" ErrorHeight "m处,颈高有误，请检查Excel表格！" "程序将自动定义此处标高为40mm进行绘制！"))
						; (alert (strcat "标高：" ErrorHeight "m处,颈高有误，请检查Excel表格！" ))
					; )
					; (print (strcat "标高：" ErrorHeight "m处,颈高有误，请检查Excel表格！"))
					; (write-line (strcat "标高：" ErrorHeight "m处,颈高有误，请检查Excel表格！") logText)
				; )
			; );end if
			; (progn
				; (setq L_fl_temp (+ (* 0.885 tn_fl) 10));+圆角，这给定死了为10
				; (if (> (/ L_fl_temp 10) (atoi (rtos (/ L_fl_temp 10) 2 0)))
					; (setq L_fl_temp (+ (* (atoi (rtos (/ L_fl_temp 10) 2 0)) 10) 5))
					; (setq L_fl_temp (* (atoi (rtos (/ L_fl_temp 10) 2 0)) 10))
				; );end if
				; (if (/= L_fl  L_fl_temp);
					; (progn
						; (if is_alert
							; (alert (strcat "标高：" ErrorHeight "m处,颈高有误，请检查Excel表格！" ))
							; ;(alert (strcat "标高：" ErrorHeight "m处,颈高有误，请检查Excel表格！" "程序将自动定义此处标高为" (rtos L_fl_temp) "进行绘制！"))
						; )
						; (print (strcat "标高：" ErrorHeight "m处,颈高有误，请检查Excel表格！"))
						; (write-line (strcat "标高：" ErrorHeight "m处,颈高有误，请检查Excel表格！") logText)
					; )
				; );end if
			; );end progn
		; );end if
		; (setq i (+ i 1))
	; );end while
; )
; ;;;;;;End ;;;;;;;;;;;;;;;;;;;;;;;;;

;;;;法兰高度与主体中高度判断
(defun flheight(/ i flh flh2 ErrorHeight)
  (setq i 0)
  (while (< i section_qty)
    (setq flh (+ (value retFlange i 4) (value retFlange i 6)));法兰表中的颈高
    (setq flh2 (* (- (value retTower (nth i pos) 2) (value retTower (nth i pos) 0)) 1000));主体表中的颈高
    (if (/=  (rtos flh) (rtos flh2))
	(progn
	  (setq ErrorHeight (rtos (value retFlange i 0)))
	  (if (= is_alert T)
	    (alert (strcat "标高：" ErrorHeight "m处,法兰表中法兰高度与主体表中法兰高度不同，请检查Excel！"))
	  )
	  (print (strcat "标高：" ErrorHeight "m处,法兰表中法兰高度与主体表中法兰高度不同，请检查Excel！"))
	  (write-line (strcat "标高：" ErrorHeight "m处,法兰表中法兰高度与主体表中法兰高度不同，请检查Excel！") logText)
	)
      )
    (setq i (+ i 1))
  )
)
;;;;;;;;End;;;;;;;;;;;;;;;;;;


;;;;;;;基础环数据判;;;;;;;;;;;;;;;;;;;
(defun embedneckjudge(/ reltn)
  (if (and (/= (value retEmbedded 2 0) "") (/= (value retEmbedded 2 0) nil));如果法兰颈厚不为空
    (progn
      (setq reltn (value retFlange 0 5));底法兰颈厚
      (if (/= (rtos reltn) (rtos (value retEmbedded 2 0)));判断底法兰颈与基础环上法兰颈厚
        (progn
	  (if (= is_alert T)
            (alert "基础环顶法兰脖子与塔架底法兰脖子厚度不一致，请检查Excel表格！")
	  )
          (print "基础环顶法兰脖子与塔架底法兰脖子厚度不一致，请检查Excel表格！")
	  (write-line "基础环顶法兰脖子与塔架底法兰脖子厚度不一致，请检查Excel表格！" logText)
        )
      )
    )	   
  )     
)
;;;;;函数结束;;;;;;;;;

;;;;;;;基础环底法兰厚度判断数据判;;;;;;;;;;;;;;;;;;;
(defun embedthickjudge(/ reltn)
  (if (and (/= (value retEmbedded 1 0) "") (/= (value retEmbedded 1 0) nil));如果法兰厚度不为空
    (progn
      (setq reltn (value retFlange 0 4));底法兰厚度
      
      (if (/= (rtos reltn) (rtos (value retEmbedded 0 0)));判断底法兰厚与基础环上法兰厚度
        (progn
	  (if (= is_alert T)
            (alert "基础环顶法兰厚度与塔架底法兰厚度不一致，请检查Excel表格！")
	  )
          (print "基础环顶法兰厚度与塔架底法兰厚度不一致，请检查Excel表格！")
	  (write-line "基础环顶法兰厚度与塔架底法兰厚度不一致，请检查Excel表格！" logText)
        )
      )
    )	   
  )     
)
;;;;;函数结束;;;;;;;;;


;;;;;;;锚栓有没有预紧力判断;;;;;;;
(defun anchorMomentjudge (/ Moment)
  (if (TorLflange 0);如果是T型法兰
    (progn
      (setq Moment (value retFlange 0 15))
      (if (or (= Moment nil) (= Moment "") )
	(progn
	  (if is_alert
	       (alert "Excel表中没填写预紧力，请检查Excel表格！")
	  )
	  (print "Excel表中没填写预紧力，请检查Excel表格！")
	)
      )
    )
  )
)
;;;;;;函数结束;;;;;;;;;;;;;;;;;;;;;;;


;;;;;;加强板门框塔筒外径判断;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defun replateDjudge ()
  (if (/= TheDoorType "ConcDoor")
  
   (if (= (ReplateDoor) T);如果是加强板门框
    (progn
      (if (/=  (rtos (value retDoor 0 13)) (rtos (value retTower 2 1)));
        (progn
	  (if is_alert
	    (alert "加强板门框处塔筒外径与主体表外径不一致，请检查Excel表格！")
	  )
	  (print "加强板门框处塔筒外径与主体表外径不一致，请检查Excel表格！")
        )
      )
    );progn右括号
    (progn
      (if (= (ReplateDoor) nil);如果是普通门框
        (progn
          (if (/=  (rtos (value retDoor 0 1)) (rtos (value retTower 2 1)));
            (progn
	      (if is_alert
	        (alert "普通门框处塔筒外径与主体表外径不一致，请检查Excel表格！")
	      )
	      (print "普通门框处塔筒外径与主体表外径不一致，请检查Excel表格！")
            )
          );if右括号
	);progn括号
	(progn
	  (if is_alert
            (alert "门框中无数据，请检查")
	  )
	  (print "门框中无数据，请检查")
	)
      );if的右括号
    );progn右括号
   );if右括号

  );if
)
;;;;;;;;;函数结束;;;;;;;;;;;;;

;;;;;;加强板门洞的壁厚判断
(defun replatethickjudge ()
  (if (= (ReplateDoor) T);如果是加强板门框
    (progn
      (if (<= (value retDoor 0 14) 30.0);如果塔筒壁厚小于等于30
        (progn
	  (if (/= (- (value retDoor 0 16) 60) 0);如果减60不等于0
	    (progn
	      (if is_alert
	        (alert "加强板厚度有问题，请检查Excel表格！")
	      )
	      (print "加强板厚度有问题，请检查Excel表格！")
	    )
	  )
        )
	(progn
	  (if (<= (value retDoor 0 14) 40.0)
            (progn
	      (if (/= (- (value retDoor 0 16) 70) 0)
	        (progn
	          (if is_alert
	            (alert "加强板厚度有问题，请检查Excel表格！")
	          )
	          (print "加强板厚度有问题，请检查Excel表格！")
	        )
	      );if右括号
	    )
	    (progn
	      (if (<= (value retDoor 0 14) 50.0)
                (progn
	          (if (/= (- (value retDoor 0 16) 80) 0)
	            (progn
	              (if is_alert
	                (alert "加强板厚度有问题，请检查Excel表格！")
	              )
	              (print "加强板厚度有问题，请检查Excel表格！")
	            )
	          );if右括号
	        )
	      );if右括号
	    )
	  );if右括号
	)
      )
    );progn右括号
  )
)
;;;;;;;;;;;;;;;;函数结束

;---------------------------------------法兰螺栓空间判断------------------------------------------
(defun FlangeCheck(Sum_flange / retbolt i FlangeCheckList FlangeGeoError da di dm s boltType dhole n flangeType b a
		   dk min_dw min_ck j sleeveDimMax nutDimMax tensioned_k tensioned_D washerDim
		   c dw ck min_dk min_c real_tight_space theory_tight_space)
;(defun FlangeCheck(retFlange retbolt Sum_flange / )
	(setq retbolt retBoltDraw)
	(setq i 0)
	(setq FlangeCheckList (list 0))
	(setq FlangeGeoError 0)
	(while (< i Sum_flange)
		(setq da (value retFlange i 1)
			di (value retFlange i 2)
			dm (value retFlange i 3)
			s (value retFlange i 5)
			boltType (value retFlange i 7)
			dhole(value retFlange i 8)
			n (value retFlange i 9)
			flangeType (value retFlange i 11)
			b (/ (- da s dm) 2)
			a (/(- dm di) 2)
			dk (- b (/ s 2))
			min_dw 10
			min_ck 3
		)
		(setq j 0)
		(while (<= j 15)
			(if (= (value retbolt j 0) boltType)
				(progn
					(setq sleeveDimMax (value retbolt j 10)
							nutDimMax(value retbolt j 9)
							tensioned_k(value retbolt j 16)
							tensioned_D(value retbolt j 17)
							washerDim(value retbolt j 6)
					)
					(if (= flangeType "L")
						(setq c(* dm (sin (/ pi n)))
							dw(- dk (/ sleeveDimMax 2))
							ck(- c (/ sleeveDimMax 2) (/ nutDimMax 2))
							min_dk (+ (/ sleeveDimMax 2) min_dw)
							min_c(+ (/ nutDimMax 2) (/ sleeveDimMax 2) min_ck)
						)
						(setq c(* dm (sin (/ pi (/ n 2))))
							dw(- dk (- tensioned_k (/ tensioned_D 2)))
							ck(- c (/ tensioned_D 2) (/ washerDim 2))
							min_dk(+(- tensioned_k (/ tensioned_D 2))min_dw)
							min_c(+ (/ washerDim 2) (/ tensioned_D 2) min_ck)
						)
					)
					(setq real_tight_space (list (+ i 1) "real"(list "dk" dk)(list "dw" dw)(list "c" c)(list "ck" ck)))
					(setq theory_tight_space (list(+ i 1) "theory"(list "dk" min_dk)(list "dw" min_dw)(list "c" min_c)(list "ck" min_ck)))
					(setq FlangeCheckList (append FlangeCheckList (list (list real_tight_space theory_tight_space))))
					(if (or (< (atoi(rtos dk))(atoi(rtos min_dk)))
						(< (atoi(rtos dw))(atoi(rtos min_dw)))
						(< (atoi(rtos c))(atoi(rtos min_c)))
						(< (atoi(rtos ck))(atoi(rtos min_ck))))
						(progn
							(if is_alert
								(alert (strcat "第" (rtos (+ i 1) 2 0)"个法兰空间不满足，需重新校核！\n"
								   "dk=" (rtos dk 2 2) ",min_dk="(rtos min_dk 2 2) "\n"
								   "dw=" (rtos dw 2 2) ",min_dw="(rtos min_dw 2 2) "\n"
								   "c="(rtos c 2 2) ",min_c="(rtos min_c 2 2) "\n"
								   "ck="(rtos ck 2 2) ",min_ck="(rtos min_ck 2 2))
								)
							)
							(print (strcat "第" (rtos (+ i 1) 2 0)"个法兰空间不满足，需重新校核！"
								   "dk=" (rtos dk 2 2) ",min_dk="(rtos min_dk 2 2) 
								   ";dw=" (rtos dw 2 2) ",min_dw="(rtos min_dw 2 2) 
								   ";c="(rtos c 2 2) ",min_c="(rtos min_c 2 2) 
								   ";ck="(rtos ck 2 2) ",min_ck="(rtos min_ck 2 2))
							)
							(write-line (strcat "第" (rtos (+ i 1) 2 0)"个法兰空间不满足，需重新校核！"
								   "dk=" (rtos dk) ",min_dk="(rtos min_dk) 
								   ";dw=" (rtos dw) ",min_dw="(rtos min_dw) 
								   ";c="(rtos c) ",min_c="(rtos min_c) 
								   ";ck="(rtos ck) ",min_ck="(rtos min_ck)) logText)

							(setq FlangeGeoError 1)
						)
						(print (strcat "法兰" (rtos (+ i 1) 2 0)"空间满足:"
							"dk=" (rtos dk 2 2) ",min_dk="(rtos min_dk 2 2) 
							";dw=" (rtos dw 2 2) ",min_dw="(rtos min_dw 2 2) 
							";c="(rtos c 2 2) ",min_c="(rtos min_c 2 2) 
							";ck="(rtos ck 2 2) ",min_ck="(rtos min_ck 2 2))
						)
					)
				)
			)
			(setq j (+ j 1))
		)
		(setq i (+ i 1))
    )
	(if (and(> Sum_flange 0)(= FlangeGeoError 1))
		(progn
			(if is_alert      
				(alert "法兰螺栓空间有误，请检查！\n
					数据已输出，请参考！")
			)
			(print "法兰螺栓空间有误，请检查！")
		)
	)
	(if (and (> Sum_flange 0) (= FlangeGeoError 0))
		(progn
			; (if is_alert      
			;   (alert "法兰螺栓空间已校核，满足要求！")
			;)
			(print "法兰螺栓空间已校核，满足要求！")
		)
	)
)
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;End preproccess;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;;;;;;;;;;;prejudge 数据判断;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;中对齐判断
(defun midjudge(/ i)
	(setq i 0)
	(while (< i 10)
		(if (/= (rtos (value retTower i 4)) (rtos (value retTower (+ i 1) 4)));如果壁厚不相等
			(if (/= (rtos  (value retTower i 3)) (rtos (value retTower (+ i 1) 3)));如果外径不相等
				(progn
					(if (= (rtos (- (value retTower i 3) (value retTower i 4))) (rtos (- (value retTower (+ i 1) 1) (value retTower (+ i 1) 4))));判断中径是否相等
						(progn;如果中径相等
							(setq midornot T)
							(print "筒节对齐方式：中对齐！")
							(setq i 1000)
						)
						(progn
							(print "筒节对齐方式：外对齐！")
							(setq i 1000)
						)
					);end if	
				);end progn
				(progn	;如果外径相等
					(print "筒节对齐方式：外对齐！")
					(setq i 1000)
				)
			);End if
		);end if
		(setq i (+ i 1))
	);end while
	
);END MIDJUDGE


;;;;;;;;;;;;;;;;;;;;;;;;;底法兰类型法兰判断;;;;;;;;;;;
(defun bottomfljudge(/ ii relDout reld_hole reld_thick BottomFlangeNmae);其他的特殊法兰绘制
	(setq ii 0)
	(setq relDout (atoi (rtos (value retFlange ii 1))));法兰外径
	(setq reld_hole (atoi (rtos (value retFlange ii 8))));螺栓孔直径
	(setq reld_thick (atoi (rtos (value retFlange ii 5))))
	(if (> reld_hole 79)	
		(cond
			((and (= reld_hole 120) (= relDout 4500))
				(setq BottomFlangeNmae "ConcreteFlange_120_4500")
			)
			((and (= reld_hole 140) (= relDout 4485) (= reld_thick 35))
				(setq BottomFlangeNmae "ConcreteFlange_140_4500_35")
			)
			((and (= reld_hole 140) (= relDout 4502) (= reld_thick 52))
				(setq BottomFlangeNmae "ConcreteFlange_140_4500")
			)
			((and (= reld_hole 140) (= relDout 4300))
				(setq BottomFlangeNmae "ConcreteFlange_140_4300")
			)
			((and (= reld_hole 125))
				(setq BottomFlangeNmae "ConcreteFlange_125")
			)
			(t
				(setq BottomFlangeNmae "UnknownBottomFl")
			)
			);cond
		(setq BottomFlangeNmae "GeneralBottomFl")
  );if
)
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;



;;;;;;;;;;;;;;;;;**************梯子类型判断********************;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defun stairsjudge(/ Hg Hr Wr Wr1 entrance_stairs_name ii num_hole reld_hole reltn Neck_TopFlange TopFlangeName TopFlange_da );Topfl_style
	(setq Hg (value retdoor 0 0));普通门框的高度
	(setq Wr1 (value retdoor 0 4));普通门框的宽度
	(setq Hr (value retdoor 0 12));加强板门框的高度
	(setq Wr (value retdoor 0 19));加强板门框的宽度，用于判断V12与5H的梯子类型
	(setq TopFlangeName topfltype)
	   
	(cond
		( (or (= Hg 5675) (= Hr  5675))
			(setq entrance_stairs_name "2.5MW_Stairs")
		);;2.5入口梯
		( (or (=  Hg 2885) (= Hr  2885))
			(setq entrance_stairs_name "3MW_S_stair_2885")
		);3s-2885入口梯
		( (or (= Hg 5970)(= Hr  5970))
			(setq entrance_stairs_name "3MW_S_stair_5970")
		);3s-5970入口梯
		( (or (= Hg 2778)(= Hr 2778))
			(setq entrance_stairs_name "2MW_Stairs_2778")
		);2MW-2778入口梯
		( (or (= Hg 3030)(= Hr 3030))
			(setq entrance_stairs_name "21_Stair")
		);21#入口梯
		( (and (or (= Wr 820) (= Wr1 1000))(or (= Hg 3130)(= Hr 3130))(= TopFlangeName "5S_TopFlange"))
			(setq entrance_stairs_name "5H_stair_3130")
		);5H入口梯V11
		( (and (or (= Wr 820) (= Wr1 1000))(or (= Hg 3130)(= Hr 3130))(or (= TopFlangeName "V12_TopFlange")(= TopFlangeName "V12_TopFlange_250" )))
			(setq entrance_stairs_name "V12_stair_3130")
		);5H入口梯V11
		( (and (or (= Wr 740)(= Wr1 970))(or (= Hg 3130)(= Hr 3130))(or (= TopFlangeName "V12_TopFlange")(= TopFlangeName "V12_TopFlange_250" )))
			(setq entrance_stairs_name "V12_stair_3130")
		);V12入口梯
		( (and (or (= Wr 970)(= Wr1 970))(or (= Hg 3290)(= Hr 3290))(or (= TopFlangeName "V15_TopFlange") (= TopFlangeName "V15_TopFlange_250"))); (or (= Topfl_style "V15_TopFlange") (= Topfl_style "V15_TopFlange_250")))
			(setq entrance_stairs_name "V15_stair_3130")
		);V15 入口梯
		( t
			(setq entrance_stairs_name "NoStairs")
		)
	);cond
 	(print entrance_stairs_name)
)
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


;;;;;;;;;;;;;;;;;;;;;顶法兰类型判断;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defun topfljudge (/ ii  TopFlangeName TopFlange_da TopFlange_d1 TopFlange_dm
				TopFlange_tfl TopFlange_s TopFlange_h TopFlange_dhold TopFlange_n Neck_TopFlange);顶法兰判断并插入
  (setq ii section_qty)
  (setq TopFlange_da (atoi (rtos (value retFlange ii 1))));法兰外径
  (setq TopFlange_d1 (atoi (rtos (value retFlange ii 2))));法兰内径
  (setq TopFlange_dm (atoi (rtos (value retFlange ii 3))));螺栓分度圆直径
  (setq TopFlange_tfl (atoi (rtos (value retFlange ii 4))));法兰外径
  (setq TopFlange_s (atoi (rtos (value retFlange ii 5))));法兰颈厚
  (setq TopFlange_h (atoi (rtos (value retFlange ii 6))));法兰颈高
  (setq TopFlange_dhold (atoi (rtos (value retFlange ii 8))));螺栓孔直径
  (setq TopFlange_n (atoi (rtos (value retFlange ii 9))));螺栓数量
  (cond
    ((and  (= TopFlange_da 3276) (= TopFlange_d1 2980) (= TopFlange_dm 3107) (= TopFlange_tfl 200) (= TopFlange_s 28) (= TopFlange_h 70) (= TopFlange_dhold 33) (= TopFlange_n 96))
      (setq TopFlangeName "2MW_TopFlange")
    );2MW顶法兰
    ((and  (= TopFlange_da 3276) (= TopFlange_d1 2980) (= TopFlange_dm 3107) (= TopFlange_tfl 200) (= TopFlange_s 28) (= TopFlange_h 70) (= TopFlange_dhold 33) (= TopFlange_n 112))
      (setq TopFlangeName "2.5MW_TopFlange")
    );2.5/3MW顶法兰
    ((and  (= TopFlange_da 3316) (= TopFlange_d1 2980) (= TopFlange_dm 3107) (= TopFlange_tfl 340) (= TopFlange_s 38) (= TopFlange_h 155) (= TopFlange_dhold 39) (= TopFlange_n 112))
      (setq TopFlangeName "3MW_S_TopFlange")
    );3MWS顶法兰
    ((and  (= TopFlange_da 3316) (= TopFlange_d1 2980) (= TopFlange_dm 3107) (= TopFlange_tfl 380) (= TopFlange_s 38) (= TopFlange_h 155) (= TopFlange_dhold 39) (= TopFlange_n 112))
      (setq TopFlangeName "21_TopFlange")
    );21#工程顶法兰
    ((and  (= TopFlange_da 3860) (= TopFlange_d1 3523) (= TopFlange_dm 3650) (= TopFlange_tfl 360) (= TopFlange_s 38) (= TopFlange_h 115) (= TopFlange_dhold 39) (= TopFlange_n 112))
      (setq TopFlangeName "5S_TopFlange")
    );5S顶法兰，V11顶法兰
    ((and  (= TopFlange_da 3316) (= TopFlange_d1 2980) (= TopFlange_dm 3107) (= TopFlange_tfl 240) (= TopFlange_s 30) (= TopFlange_h 155) (= TopFlange_dhold 39) (= TopFlange_n 112))
      (setq TopFlangeName "3MW_S_New_TopFlange")
    );3MWS新顶法兰
    ((and  (= TopFlange_da 4980) (= TopFlange_d1 4560) (= TopFlange_dm 4794) (= TopFlange_tfl 290) (= TopFlange_s 42) (= TopFlange_h 115) (= TopFlange_dhold 45) (= TopFlange_n 156))
      (setq TopFlangeName "6MW_TopFlange")
    );6MWS顶法兰
    ((and  (= TopFlange_da 4980) (= TopFlange_d1 4560) (= TopFlange_dm 4794) (= TopFlange_tfl 250) (= TopFlange_s 36) (= TopFlange_h 115) (= TopFlange_dhold 45) (= TopFlange_n 156))
      (setq TopFlangeName "6MW_N_TopFlange")
    );6MWS_N 顶法兰
    ((and  (= TopFlange_da 3276) (= TopFlange_d1 2980) (= TopFlange_dm 3107) (= TopFlange_tfl 200) (= TopFlange_s 28) (= TopFlange_h 115) (= TopFlange_dhold 33) (= TopFlange_n 112))
      (setq TopFlangeName "2.xMW_TopFlange")
    );2.XMWS顶法兰
    ((and  (= TopFlange_da 3276) (= TopFlange_d1 2980) (= TopFlange_dm 3107) (= TopFlange_tfl 200) (= TopFlange_s 20) (= TopFlange_h 115) (= TopFlange_dhold 33) (= TopFlange_n 96))
      (setq TopFlangeName "2MW-20_TopFlange")
    );2MWS-20顶法兰
    ((and  (= TopFlange_da 2591) (= TopFlange_d1 2372) (= TopFlange_dm 2460) (= TopFlange_tfl 190) (= TopFlange_s 22) (= TopFlange_h 40) (= TopFlange_dhold 33) (= TopFlange_n 76))
      (setq TopFlangeName "1.5MW-22_TopFlange")
    );1.5MW-22顶法兰
    ((and  (= TopFlange_da 2591) (= TopFlange_d1 2374) (= TopFlange_dm 2460) (= TopFlange_tfl 100) (= TopFlange_s 21) (= TopFlange_h 40) (= TopFlange_dhold 33) (= TopFlange_n 76))
      (setq TopFlangeName "1SMW_TopFlange")
    );1SMW顶法兰
    ((and  (= TopFlange_da 2178) (= TopFlange_d1 1998) (= TopFlange_dm 2068) (= TopFlange_tfl 100) (= TopFlange_s 18) (= TopFlange_h 50) (= TopFlange_dhold 23) (= TopFlange_n 40))
      (setq TopFlangeName "750T_TopFlange")
    );750T顶法兰
    ((and  (= TopFlange_da 3860) (= TopFlange_d1 3523) (= TopFlange_dm 3650) (= TopFlange_tfl 420) (= TopFlange_s 42) (= TopFlange_h 145) (= TopFlange_dhold 39) (= TopFlange_n 112))
      (setq TopFlangeName "5X_TopFlange")
    );5X顶法兰
	((and  (= TopFlange_da 3860) (= TopFlange_d1 3523) (= TopFlange_dm 3650) (= TopFlange_tfl 300) (= TopFlange_s 38) (= TopFlange_h 115) (= TopFlange_dhold 39) (= TopFlange_n 112))
		(setq TopFlangeName "V12_TopFlange")
	);v12顶法兰
	((and  (= TopFlange_da 3860) (= TopFlange_d1 3523) (= TopFlange_dm 3650) (= TopFlange_tfl 250) (= TopFlange_s 38) (= TopFlange_h 115) (= TopFlange_dhold 39) (= TopFlange_n 112))
		(setq TopFlangeName "V12_TopFlange_250")
	);v12顶法兰250减重
	((and  (= TopFlange_da 3884) (= TopFlange_d1 3523) (= TopFlange_dm 3650) (= TopFlange_tfl 330) (= TopFlange_s 50) (= TopFlange_h 115) (= TopFlange_dhold 39) (= TopFlange_n 112))
		(setq TopFlangeName "V15_TopFlange")
	);v15--330顶法兰
	((and  (= TopFlange_da 3884) (= TopFlange_d1 3523) (= TopFlange_dm 3650) (= TopFlange_tfl 250) (= TopFlange_s 50) (= TopFlange_h 115) (= TopFlange_dhold 39) (= TopFlange_n 112))
		(setq TopFlangeName "V15_TopFlange_250")
	);v15-250顶法兰
	((and  (= TopFlange_da 4140) (= TopFlange_d1 3779) (= TopFlange_dm 3906) (= TopFlange_tfl 400) (= TopFlange_s 50) (= TopFlange_h 140) (= TopFlange_dhold 39) (= TopFlange_n 112))
		(setq TopFlangeName "V17_TopFlange_400")
	);v17-400顶法兰
    
  )
  (if (= TopFlangeName nil)
      (setq TopFlangeName "other_TopFlange")
      (setq TopFlangeName TopFlangeName)
  )
)
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


;;;;;;;;;;;基础环判断
(defun EmbeddedOrNot(/ Embedded)
  (if (and (/= (value retEmbedded 0 0) "") (/= (value retEmbedded 0 0) nil) (= (value retFlange 0 11) "L"))
    (setq Embedded T)
  )
)
;;;;;;;;;;;;;;;;;;;

;;;;;;;;;;;;;;;;;;;;;V12是否是分片塔判断;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defun SliceTowerOrNot (/ TowerType )
	(if(>= (atoi (rtos (-(value retTower 0 1) (value retTower 0 4)))) 5950.0) 
		(setq TowerType "SliceTower");如果第一段筒节中径>=5950，则为分片塔slicetower
		(setq TowerType "NomalTower");如果第一段筒节中径<5950，则为常规塔nomaltower
	);;;;;if
	;(print (type (-(value retTower 0 1) (value retTower 0 4)) ))
	;(print (type 5950.0));type 输出数据类型
	;(print TowerType) 
);;;;;defun


;;;;;;;;;;;短尾铆钉及套环数量判断
(defun Rivet_judge(/ rivet_num towerHeight);局部变量/竖向、塔架高度
  (if (or(= topfltype "V12_TopFlange")(= topfltype "V12_TopFlange_250"))
    (progn ;V12数量判断并填入，其他机型不填
	   (setq towerheight (- (value retFlange section_qty 0) (value retFlange 0 0)))
	   (cond 
         (
	       (and(> towerheight 125) (<= towerheight 130) (= section_qty 5))
           (setq rivet_num 1900)
	     )
	     (
	       (and(> towerheight 135) (<= towerheight 140) (= section_qty 6))
           (setq rivet_num 2300)
	     )
	     (
	       (and(> towerheight 140) (<= towerheight 145) (= section_qty 6))
           (setq rivet_num 2500)
	     )
	     ( t
           (setq rivet_num 0)
         )
        );end cond
	);end progn
	(setq rivet_num 0)
  );end if
);;;;end defun

;;;;;;;;;;;V12混塔是否防洪的判断
(defun flood_protection_judge(/ flood_protection)
  (if (and (or(= topfltype "V12_TopFlange")(= topfltype "V12_TopFlange_250")) (= TheDoorType "ConcDoor"))
	   (setq flood_protection (getstring "\n请输入是否有防洪需求(Yes/No)："))
  );end if
 );end defun

;;;;;;;;;;;V12混塔附件重量判断
(defun hybridTower_asm_mass( / MassList towerHeight steel_asm_mass intermediate_plate_mass concrete_asm_mass stair_mass)
  (if (and (or(= topfltype "V12_TopFlange")(= topfltype "V12_TopFlange_250")) (= TheDoorType "ConcDoor"))
    (progn 
	   (setq MassList '())
	   (setq towerheight (- (value retFlange section_qty 0) 0.4))
	   (cond 
         (
	       (and(> towerheight 135) (<= towerheight 140) (or (= AntiFloodType "no")(= AntiFloodType "No")))
           (setq steel_asm_mass 2300
		         intermediate_plate_mass 3920
		         concrete_asm_mass 7000
				 stair_mass 0
		         MassList (append MassList (list steel_asm_mass) (list intermediate_plate_mass) (list concrete_asm_mass) (list stair_mass))
		   );setq
	     )
	     (
	       (and(> towerheight 135) (<= towerheight 140) (or (= AntiFloodType "yes")(= AntiFloodType "Yes")))
           (setq steel_asm_mass 2300
		         intermediate_plate_mass 3920
		         concrete_asm_mass 9500
				 stair_mass 3130
		         MassList (append MassList (list steel_asm_mass) (list intermediate_plate_mass) (list concrete_asm_mass) (list stair_mass))
		   );setq
	     )
	     (
	       (and(> towerheight 155) (<= towerheight 160) (or (= AntiFloodType "no")(= AntiFloodType "No")))
            (setq steel_asm_mass 3700
		         intermediate_plate_mass 5972
		         concrete_asm_mass 7000
				 stair_mass 0
		         MassList (append MassList (list steel_asm_mass) (list intermediate_plate_mass) (list concrete_asm_mass) (list stair_mass))
		   );setq
	     )
		 (
	       (and(> towerheight 155) (<= towerheight 160) (or (= AntiFloodType "yes")(= AntiFloodType "Yes")))
            (setq steel_asm_mass 3700
		         intermediate_plate_mass 5972
		         concrete_asm_mass 9500
				 stair_mass 3130
		         MassList (append MassList (list steel_asm_mass) (list intermediate_plate_mass) (list concrete_asm_mass) (list stair_mass))
		   );setq
	     )
		 (
	       (and(> towerheight 160) (<= towerheight 166) (or (= AntiFloodType "no")(= AntiFloodType "No")))
            (setq steel_asm_mass 3800
		         intermediate_plate_mass 3920
		         concrete_asm_mass 7000
				 stair_mass 0
		         MassList (append MassList (list steel_asm_mass) (list intermediate_plate_mass) (list concrete_asm_mass) (list stair_mass))
		   );setq
	     )
		 (
	       (and(> towerheight 160) (<= towerheight 166) (or (= AntiFloodType "yes")(= AntiFloodType "Yes")))
            (setq steel_asm_mass 3800
		         intermediate_plate_mass 3920
		         concrete_asm_mass 9500
				 stair_mass 3130
		         MassList (append MassList (list steel_asm_mass) (list intermediate_plate_mass) (list concrete_asm_mass) (list stair_mass))
		   );setq
	     )
		 (
	       (and(> towerheight 180) (<= towerheight 185) (or (= AntiFloodType "no")(= AntiFloodType "No")))
           (setq steel_asm_mass 4000
		         intermediate_plate_mass 3920
		         concrete_asm_mass 18000
				 stair_mass 0
		         MassList (append MassList (list steel_asm_mass) (list intermediate_plate_mass) (list concrete_asm_mass) (list stair_mass))
		   );setq
	     )
	     (
	       (and(> towerheight 180) (<= towerheight 185) (or (= AntiFloodType "yes")(= AntiFloodType "Yes")))
           (setq steel_asm_mass 4000
		         intermediate_plate_mass 3920
		         concrete_asm_mass 19900
				 stair_mass 3180
		         MassList (append MassList (list steel_asm_mass) (list intermediate_plate_mass) (list concrete_asm_mass) (list stair_mass))
		   );setq
	     )
	     ( t
           (setq MassList '(0 0 0 0))
         )
        );end cond
		(setq MassList MassList)
	);end progn
	(setq MassList '(0 0 0 0))
  );end if
);;;;end defun


;CAL
;;;;;;;;;;;;;;;;;;;;;;;;mass文件;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;;各筒段质量计算
(defun secmass(/ i k SectionMass SectionMassList h_m h j jend BtmDim UpDim thickness ConeMass SectionMass );求筒节重量
  (setq i 0)
  (setq k section_qty)
  (setq SectionMass 0);质量初值
  ;(setq SectionMassList (list 0 0))
  (setq SectionMassList '())
  (while (< i k);遍历筒节
    (setq j (+ (nth i pos) 1));起始筒节往上加1即为第一个筒节
    (setq jend (nth (+ i 1) pos));起始筒节
    (while (< j (- jend 1))
      (setq h_m (- (value retTower j 2) (value retTower j 0)));筒节高度
      (setq h (* h_m 1000));转换成mm
      (setq BtmDim (value retTower j 1);筒节下外直径
            UpDim (value retTower j 3);筒节上外直径
            thickness (value retTower j 4));筒节厚度
      (setq ConeMass (TowerWeight BtmDim UpDim h thickness));求塔段质量
      (setq SectionMass (+ SectionMass ConeMass));塔段质量累加
      (setq j (+ j 1))
    )
    (setq SectionMassList (append SectionMassList (list SectionMass)))
    (setq SectionMass 0.0)
    (setq i (+ i 1))
  )
  (setq SectionMassList SectionMassList)
)
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;



;*********************************所有法兰的质量**************************************************;;;;;;;;;;;;;;;;;;
(defun AllFlangeMass(/ Sum_flange ii FlangeMassList Dout_fl Din_fl Dpcd_fl tfl_fl tn_fl L_fl d_fl num_hole R_fl flange_type H_fl
		     topflname bottomflname nFlangeMass)
    (setq Sum_flange (- flange_qty 1));吴航
    (setq ii 0);循环初值
    (setq FlangeMassList '())
  
    (while (and (<= ii section_qty) (> (value retFlange ii 2) 0))      
      ;数据初始化
		(setq Dout_fl (value retFlange ii 1));法兰外径
		(setq Din_fl (value retFlange ii 2));法兰内径
		(setq Dpcd_fl (value retFlange ii 3));螺栓分度圆直径
		(setq tfl_fl (value retFlange ii 4));法兰厚度
		(setq tn_fl (value retFlange ii 5));法兰颈厚
		(setq L_fl (value retFlange ii 6));法兰颈高
		(setq L_fl (neck_h_fl_judge L_fl tn_fl));法兰颈高，过滤一下数据
		(setq d_fl (value retFlange ii 8));螺栓孔直径
		(setq num_hole (value retFlange ii 9));螺栓数
		(setq R_fl (value retFlange ii 10));圆角
		(setq DTout (value retFlange ii 13));T型法兰外径
		(if (or (= R_fl nil) (= R_fl " "));如果圆角单元格没有内容则为10
			(setq R_fl 10)
		)
		(setq flange_type (value retFlange ii 11));法兰类型
		(setq flange_type (FlangeTypeJudge flange_type ii));判断法兰的类型
		(setq H_fl (+ L_fl tfl_fl));法兰高度
      
		;法兰重量存入列表中
		(if (= ii section_qty)
			(progn;顶法兰时
				(setq nFlangeMass (topflmass topfltype));顶法兰重量
			)
			(progn
				(if (= ii 0);如果是底法兰
					(progn
						(setq bottomflname (bottomfljudge));判断底法兰类型
						;(print bottomflname)
						(setq nFlangeMass (bottomflmass bottomflname flange_type Dout_fl Din_fl tfl_fl L_fl d_fl num_hole tn_fl R_fl DTout))
					)
					(setq nFlangeMass (FlangeMass flange_type Dout_fl Din_fl tfl_fl L_fl d_fl num_hole tn_fl R_fl DTout))
				)
			);progn
		);if
		(setq FlangeMassList (append FlangeMassList (list nFlangeMass)))
		(setq ii (+ ii 1))
	);while的右括号
	(setq FlangeMassList FlangeMassList)
);********************************所有法兰质量计算结束***********************************************

;;;;;;;;;;;;;;;;;;;;;************单个法兰重量计算;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defun FlangeMass(Flange_type Da Di tfl h dhole num s r DTout / PAI RR V1 V2 VR V3 V4 V5 V6 R0 R01 R1 R2 R3 R4 Vbolt V RRIN RRout Dout VRIN VROUT xFlangeMass);V4 V5 V6 R1 R2 R3 R4 ;外径，内径，法兰厚，颈高，孔直径，数量，颈厚，圆角，外径
	(setq PAI 3.14159263534)
	(if (= Flange_type "L")
		(progn
			(setq RR (-(/ Da 2.0) s r))
			(setq V1 (* PAI (- (expt (/ Da 2.0) 2) (expt (-(/ Da 2.0)s) 2)) h))
			(setq V2 (* PAI (- (expt (/ Da 2.0) 2) (expt (/ Di 2.0) 2)) tfl))
			(setq VR (+ (* PAI r (expt RR 2)) (* PAI r r r) (* PAI RR r r PAI 0.5) (* PAI r r r (/ 1 -3.0))))
			(setq V3 (-(* PAI (-(/ Da 2) s)(-(/ Da 2) s) r) VR))
			(setq Vbolt (* num PAI (expt (/ dhole 2.0)2) tfl))
			
				(if(<= s 15)
					(progn 
						(setq R0 (* 0.5(- Da 6)))
						(setq R01 (* 0.5(- Da(* s 2))))
						(setq V4 (* 0.5 PAI (* 0.7 (- s 3))(- (* R0 R0)(* R01 R01))))
					)
					
				);end if
				(if(and(> s 15)(<= s 40))
					(progn
						(setq R1 (* 0.5 Da))
						(setq R2 (+ (- R1 (* 0.3333 s)) 1.5))
						(setq R3 (- R2 3))
						(setq R4 (* 0.5(- Da(* s 2))))
						(setq V5 (* 0.5 PAI (* 0.7 (- (* s 0.6667) 1.5))(- (* R3 R3)(* R4 R4))))
						(setq V6 (* 0.5 PAI (* 0.7 (- (* s 0.3333) 1.5))(- (* R1 R1)(* R2 R2))))
						(setq V4 (+ v5 v6))
					)
				);end if			
				(if (> s 40)
					(progn
						(setq R1 (* 0.5 Da))
						(setq R2 (+ (- R1 (* 0.5 s)) 1.5))
						(setq R3 (- R2 3))
						(setq R4 (* 0.5(- Da(* s 2))))
						(setq V5 (* 0.5 PAI (- (* s 0.6667) 1.5)(- (* R3 R3)(* R4 R4))))
						(setq V6 (* 0.5 PAI (- (* s 0.3333) 1.5)(- (* R1 R1)(* R2 R2))))
						(setq V4 (+ v5 v6))
					)
				);end if
				
		
			(setq V (-(+ V1 V2 V3) Vbolt V4));V4
			(setq xFlangeMass (/ (* V 7850)1000000000.0))
		);减重计算
		(progn
			(setq RRIN (-(/ Da 2.0)s r))
			(setq RRout (+ (/ Da 2.0) r))
			(if (or(= DTout "")(= DTout nil))
				(setq Dout (+ Da (- Da (* s 2.0) Di)))
				(setq Dout DTout)
			)
			(setq V1 (* PAI (- (expt (/ Da 2.0) 2) (expt (-(/ Da 2.0)s) 2)) h))
			(setq V2 (* PAI (-(expt (/ Dout 2)2)(expt (/ Di 2)2))tfl))
			(setq VRIN (* PAI r r (/ (-(+ (* 12 RRIN)(* r 2))(* 3 PAI RRIN)) 6.0)))
			(setq VROUT (/(* PAI r r (+(-(* 2 r) (* 12 RROUT))(* 3 PAI RROUT)))-6.0))
			(setq Vbolt (* num PAI (/ dhole 2.0)(/ dhole 2.0) tfl))
				
				(if(<= s 15)
					(progn 
						(setq R0 (* 0.5(- Da 6)))
						(setq R01 (* 0.5(- Da(* s 2))))
						(setq V4 (* 0.5 PAI (* 0.7 (- s 3))(- (* R0 R0)(* R01 R01))))
					)
				);end if
				(if(and(> s 15)(<= s 40))
					(progn
						(setq R1 (* 0.5 Da))
						(setq R2 (+ (- R1 (* 0.3333 s)) 1.5))
						(setq R3 (- R2 3))
						(setq R4 (* 0.5(- Da(* s 2))))
						(setq V5 (* 0.5 PAI (* 0.7 (- (* s 0.6667) 1.5))(- (* R3 R3)(* R4 R4))))
						(setq V6 (* 0.5 PAI (* 0.7 (- (* s 0.3333) 1.5))(- (* R1 R1)(* R2 R2))))
						(setq V4 (+ v5 v6))
					)
				);end if			
				(if (> s 40)
					(progn
						(setq R1 (* 0.5 Da))
						(setq R2 (+ (- R1 (* 0.5 s)) 1.5))
						(setq R3 (- R2 3))
						(setq R4 (* 0.5(- Da(* s 2))))
						(setq V5 (* 0.5 PAI (- (* s 0.6667) 1.5)(- (* R3 R3)(* R4 R4))))
						(setq V6 (* 0.5 PAI (- (* s 0.3333) 1.5)(- (* R1 R1)(* R2 R2))))
						(setq V4 (+ v5 v6))
					)
				);end if
			
			(setq V (-(+ V1 V2 VROUT VRIN) Vbolt V4));V4
			(setq xFlangeMass (/ (* V 7850)1000000000.0))
		);减重计算
		
	)
)
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;单个法兰重量计算结束;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;;;;;;;;;;;******************塔架质量计算************************;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defun TowerWeight(BtmDim UpDim h thickness / RBtm Rup Costheta DetaR inRBtm inRUp Vbig Vsmall VCone)
  (setq RBtm(/ BtmDim 2)
	RUp(/ UpDim 2)
	Costheta (/ h (expt (+ (expt (/ (- BtmDim UpDim) 2) 2) (expt h 2)) 0.5))
	DetaR (/ thickness Costheta)
	inRBtm (- RBtm DetaR)
	inRUp (- RUp DetaR)
	Vbig (/(* pi h (+ (expt RBtm 2) (expt RUp 2) (* RBtm RUp))) 3)
	Vsmall (/(* pi h (+(expt inRBtm 2)(expt inRUp 2)(* inRBtm inRUp))) 3)
	VCone(- Vbig Vsmall))
  (/ (* VCone 7850) 1000000000.0);重量
)
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


;;;;基础环上法兰质量;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defun embeddedflmass(Da Di s r h tfl num dhole / PAI RR V1 V2 VR V3 Vbolt V mass_Flange)
  (setq PAI 3.14159263534)
  (setq RR (-(/ Da 2.0) s r)
        V1 (* PAI (- (expt (/ Da 2.0) 2) (expt (-(/ Da 2.0)s) 2)) h)
        V2 (* PAI (- (expt (/ Da 2.0) 2) (expt (/ Di 2.0) 2)) tfl)
        VR (+ (* PAI r (expt RR 2)) (* PAI r r r) (* PAI RR r r PAI 0.5) (* PAI r r r (/ 1 -3.0)))
        V3 (-(* PAI (-(/ da 2) s)(-(/ da 2) s) r) VR)
        Vbolt (* num PAI (expt (/ dhole 2.0)2) tfl)
        V (-(+ V1 V2 V3) Vbolt)
        mass_Flange (/ (* V 7850) 1000000000.0)
  )
)
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;;;基础环上椭圆孔的重量;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defun embeddedellipsemass(n_cy a_cy b_cy ts_e / V_ellipse mass_ellipse)
  (setq V_ellipse  (* n_cy pi a_cy b_cy ts_e)
	mass_ellipse  (/ (* V_ellipse  7850) 1000000000.0)
  );椭圆的重量
)
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;;;;基础环筒体重量;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defun embeddedshellmass(da_e h_total tf hf tf_base ts_e / V1_cylinder V2_cylinder V_cylinder mass_cylinder)
  (setq V1_cylinder (* pi (/ da_e 2)(/ da_e 2) (- h_total tf hf tf_base))
	V2_cylinder (* pi (/ (- da_e (* 2 ts_e)) 2)(/ (- da_e (* 2 ts_e)) 2) (- h_total tf hf tf_base))
	V_cylinder (- V1_cylinder V2_cylinder)
	mass_cylinder (/ (* V_cylinder  7850)1000000000.0)
  );圆壳的重量
)

;;;基础环底拼接法兰重量;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defun embeddedbaseflmass(da_base di_base tf_base / V_base mass_base V_bolthole )
  (setq V_bolthole (* 3 (/ (* pi (* 39 39)) 4)  tf_base))
  ;(setq V_base (* pi (/ (- (* da_base da_base) (* di_base di_base)) 4) tf_base))
  (setq V_base (- (* pi (/ (- (* da_base da_base) (* di_base di_base)) 4) tf_base) V_bolthole))
  (setq mass_base (/ (* V_base  7850) 1000000000.0));底座的重量
)
;;;;;;;;End


;;;;****************************加强板门板重量;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defun Repdoormass(DD1 t1 hh1 t2 h_v hh2 ww1 / R D_out D_in l_out_redoor l_in_redoor
			small_rec_v a_redoor b_out_redoor b_in_redoor reellipse_v small_door_v m_replate_door
			ww ll S l_4R ll_vertical ll_out_horizontal ll_in_horizontal l_big_r
			l_small_r l_big l_small cham_v sum_replate_v sum_replate_m l_out_re l_in_re)
  ;DD1 (value retDoor 0 13);塔筒壁直径
  ;t1 (value retDoor 0 14)	;塔筒壁厚度t1
  ;hh1 (value retDoor 0 15) ; 补强板高度H1
  ;t2 (value retDoor 0 16)	;补强板厚度t2
  ;h_v (value retDoor 0 17);直边长度H_V
  ;hh2 (value retDoor 0 18);门洞高度H2
  ;ww1 (value retDoor 0 19)) ;门洞宽度W
  (setq R 200);四个角的圆角
  (setq D_out (- (+ DD1 t2) t1))
  (setq D_in (- (- DD1 t2) t1))
  (setq l_out_redoor (* D_out (asin (/ ww1 2) (/ D_out 2))) );加强板门洞的外弧长
  (setq l_in_redoor (* D_in (asin (/ ww1 2) (/ D_in 2))))
  (setq small_rec_v (/ (* h_v (+ l_out_redoor l_in_redoor) t2) 2))
  (setq a_redoor (/ (- hh2 h_v) 2))
  (setq b_out_redoor (/ l_out_redoor 2))
  (setq b_in_redoor (/ l_in_redoor 2))
  (setq reellipse_v (* pi a_redoor (/ (+ b_out_redoor b_in_redoor) 2) t2))
  (setq small_door_v (+ small_rec_v reellipse_v));加强板门洞的体积
  (setq m_replate_door (/ (* small_door_v 7850) 1000000000));加强板小门洞的质量
  (setq l_out_re (* (* DD1 pi) (/ angle_plate pi)));加强板门洞的外弧长
  (setq l_in_re (* (* (- DD1 (* t1 2)) pi) (/ angle_plate pi)));加强板门洞的内弧长
  (setq raw_replate_v (/ (* hh1 t2 (+ l_out_re l_in_re)) 2))
  (setq R_replate_v (* t2 (- 1 (/ pi 4)) (expt R 2)))
  (setq ww (/ (- t2 t1) 2))
  (setq ll (* 2 (- t2 t1)))
  (setq S (* ww ll))
  (setq l_4R (- R (* 4 ww)))
  (setq ll_vertical (- hh1 R))
  (setq ll_out_horizontal (* (- (/ pi 3) (* 4 (asin (/ R 2) (/ D_out 2)))) (/ D_out 2)))
  (setq ll_in_horizontal (* (- (/ pi 3) (* 4 (asin (/ R 2) (/ D_in 2)))) (/ D_in 2)))
  (setq l_big_r (* (/ pi 2) R))
  (setq l_small_r (* (/ pi 2) l_4R))
  (setq l_big (+ (* 2 ll_vertical) (* 4 l_big_r) (* 2 ll_out_horizontal)))
  (setq l_small (+ (* 2 ll_vertical) (* 4 l_small_r) (* 2 ll_in_horizontal)))
  (setq cham_v (/ (* S (+ l_big l_small)) 2))
  (setq sum_replate_v (- raw_replate_v (* 4 R_replate_v) cham_v small_door_v))
  (setq sum_replate_m (/ (* sum_replate_v 7850) 1000000000))
)
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;加强板门板重量;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defun Repdoormass_1(DD1 t1 hh1 t2 h_v hh2 ww1 / R D_out D_in l_out_redoor l_in_redoor
			small_rec_v a_redoor b_out_redoor b_in_redoor reellipse_v small_door_v m_replate_door
			ww ll S l_4R ll_vertical ll_out_horizontal ll_in_horizontal l_big_r
			l_small_r l_big l_small cham_v sum_replate_v sum_replate_m l_out_re l_in_re)
	;DD1 (value retDoor 0 13);塔筒壁直径
	;t1 (value retDoor 0 14)	;塔筒壁厚度t1
	;hh1 (value retDoor 0 15) ; 补强板高度H1
	;t2 (value retDoor 0 16)	;补强板厚度t2
	;h_v (value retDoor 0 17);直边长度H_V
	;hh2 (value retDoor 0 18);门洞高度H2
	;ww1 (value retDoor 0 19)) ;门洞宽度W
	(setq R 0);四个角的圆角
	(setq D_out (- (+ DD1 t2) t1))
	(setq D_in (- (- DD1 t2) t1))
	(setq l_out_redoor (* D_out (asin (/ ww1 2) (/ D_out 2))) );加强板门洞的外弧长
	(setq l_in_redoor (* D_in (asin (/ ww1 2) (/ D_in 2))))
	(setq small_rec_v (/ (* h_v (+ l_out_redoor l_in_redoor) t2) 2))
	(setq a_redoor (/ (- hh2 h_v) 2))
	(setq b_out_redoor (/ l_out_redoor 2))
	(setq b_in_redoor (/ l_in_redoor 2))
	(setq reellipse_v (* pi a_redoor (/ (+ b_out_redoor b_in_redoor) 2) t2))
	(setq small_door_v (+ small_rec_v reellipse_v));加强板门洞的体积
	(setq m_replate_door (/ (* small_door_v 7850) 1000000000));加强板小门洞的质量
	(setq l_out_re (* (* DD1 pi) (/ angle_plate pi)));加强板门洞的外弧长
	(setq l_in_re (* (* (- DD1 (* t1 2)) pi) (/ angle_plate pi)));加强板门洞的内弧长
	(setq raw_replate_v (/ (* hh1 t2 (+ l_out_re l_in_re)) 2))
	(setq R_replate_v (* t2 (- 1 (/ pi 4)) (expt R 2)))
	(setq ww (/ (- t2 t1) 2))
	(setq ll (* 2 (- t2 t1)))
	(setq S (* ww ll))
	(setq l_4R (- R (* 4 ww)))
	(setq ll_vertical (- hh1 R))
	(setq ll_out_horizontal (* (- (/ pi 3) (* 4 (asin (/ R 2) (/ D_out 2)))) (/ D_out 2)))
	(setq ll_in_horizontal (* (- (/ pi 3) (* 4 (asin (/ R 2) (/ D_in 2)))) (/ D_in 2)))
	(setq l_big_r (* (/ pi 2) R))
	(setq l_small_r (* (/ pi 2) l_4R))
	(setq l_big (+ (* 2 ll_vertical) (* 4 l_big_r) (* 2 ll_out_horizontal)))
	(setq l_small (+ (* 2 ll_vertical) (* 4 l_small_r) (* 2 ll_in_horizontal)))
	(setq cham_v (/ (* S (+ l_big l_small)) 2))
	(setq sum_replate_v (- raw_replate_v (* 4 R_replate_v) cham_v small_door_v))
	(setq sum_replate_m (/ (* sum_replate_v 7850) 1000000000))
)
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


;;;;****************************普通门框重量;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defun GeneralDoorFrameMass-backup (D Thick h1 b1 h2 bst hst / h2_v ellipse_frame_v door_frame_v door_frame_m)
  ;(setq D (value retDoor 0 1);塔筒外径
	;Thick (value retDoor 0 2);塔筒壁厚
    	;h1 (value retDoor 0 3);高度
	;b1 (value retDoor 0 4);宽度
	;h2 (value retDoor 0 5);直边长度
	;bst (value retDoor 0 6);门框厚度
	;hst (value retDoor 0 7));门框厚度
  (setq h2_v (* bst h2 hst))
  (setq ellipse_frame_v (* hst pi (- (* b1 (/ (- h1 h2) 4)) (* (- (/ b1 2) bst) (- (/ (- h1 h2) 2) bst)))))
  (setq door_frame_v (+ (* 2 h2_v) ellipse_frame_v))
  (setq dorr_frame_m (/ (* door_frame_v 7850) 1000000000) )
)
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;******************门框质量****************************/曹旭东算法，相当于creo中的偏移命令
(defun GeneralDoorFrameMass( h1 b1 h2 bst hst / halfLAxis halfSAxis middle_ell_a middle_ell_b temp_h middle_ell_length all_length DoorFrameVolume MassDoorFrame)
  (setq halfLAxis (/ (- h1 h2) 2));椭圆的长半轴长
  (setq halfSAxis (/ b1 2));椭圆短半轴长
  (setq middle_ell_a (- halfLAxis (/ bst 2))
	middle_ell_b (- halfSAxis (/ bst 2))
	temp_h (/ (* (- middle_ell_a middle_ell_b) (- middle_ell_a middle_ell_b))
		 (* (+ middle_ell_a middle_ell_b) (+ middle_ell_a middle_ell_b)))
	middle_ell_length (* pi (+ middle_ell_a middle_ell_b)
			    (+ 1 (/ (* 3 temp_h)
				   (+ 10 (sqrt (- 4 (* 3 temp_h)))))))
	all_length (+ middle_ell_length (* 2 h2))

	DoorFrameVolume (* all_length hst bst)
	MassDoorFrame (/ (* DoorFrameVolume 7850) 1000000000.0)
  )
)



;;;;****************************加强板门洞重量;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defun RepDoorHoleMass( DD1 t1 hh1 t2 h_v hh2 ww1 / R l_out_re l_in_re r_v re_v re_door_m )
        ;DD1 (value retDoor 0 13);塔筒壁直径
	;t1 (value retDoor 0 14)	;塔筒壁厚度t1
    	;hh1 (value retDoor 0 15) ; 补强板高度H1
	;t2 (value retDoor 0 16)	;补强板厚度t2
        ;h_v (value retDoor 0 17);直边长度H_V
	;hh2 (value retDoor 0 18);门洞高度H2
	;ww1 (value retDoor 0 19)) ;门洞宽度W
  (setq R 200);四个角的圆角
  (setq l_out_re (* (* DD1 pi) (/ angle_plate pi)));加强板门洞的外弧长
  (setq l_in_re (* (* (- DD1 (* t1 2)) pi) (/ angle_plate pi)));加强板门洞的内部弧长
  (setq r_v (* t1 (- 1 (/ pi 4)) (expt R 2)));单个圆角的体积
  (setq re_v (- (/ (* hh1 t1 (+ l_out_re l_in_re)) 2) (* 4 r_v)));加强板门洞的体积
  (setq re_door_m (/ (* re_v 7850) 1000000000));加强板门洞的重量
)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;加强板门洞重量--加强板不含圆角;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defun RepDoorHoleMass_1( DD1 t1 hh1 t2 h_v hh2 ww1 / R l_out_re l_in_re r_v re_v re_door_m )
        ;DD1 (value retDoor 0 13);塔筒壁直径
	;t1 (value retDoor 0 14)	;塔筒壁厚度t1
    	;hh1 (value retDoor 0 15) ; 补强板高度H1
	;t2 (value retDoor 0 16)	;补强板厚度t2
        ;h_v (value retDoor 0 17);直边长度H_V
	;hh2 (value retDoor 0 18);门洞高度H2
	;ww1 (value retDoor 0 19)) ;门洞宽度W
	(setq R 0);四个角的圆角
	(setq l_out_re (* (* DD1 pi) (/ angle_plate pi)));加强板门洞的外弧长
	(setq l_in_re (* (* (- DD1 (* t1 2)) pi) (/ angle_plate pi)));加强板门洞的内部弧长
	(setq r_v (* t1 (- 1 (/ pi 4)) (expt R 2)));单个圆角的体积
	(setq re_v (- (/ (* hh1 t1 (+ l_out_re l_in_re)) 2) (* 4 r_v)));加强板门洞的体积
	(setq re_door_m (/ (* re_v 7850) 1000000000));加强板门洞的重量
)
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;



;;;;****************************普通门洞重量;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defun GeneralDoorMass(D Thick h1 b1 h2 bst hst / 
		     l_out_door l_in_door rec_v a ellipse_v door_m)
  ;(setq D (value retDoor 0 1);塔筒外径
	;Thick (value retDoor 0 2);塔筒壁厚
    	;h1 (value retDoor 0 3);高度
	;b1 (value retDoor 0 4);宽度
	;h2 (value retDoor 0 5);直边长度
	;bst (value retDoor 0 6);门框厚度
	;hst (value retDoor 0 7));门框宽度
  (setq l_out_door (* D (asin (/ b1 2) (/ D 2))))
  (setq l_in_door (* (- D (* 2 Thick)) (asin (/ b1 2)  (/ (- D (* 2 Thick)) 2)) ))
  (setq rec_v (/ (* h2 (+ l_out_door l_in_door) Thick) 2))
  (setq a (/ (- h1 h2) 2))
  (setq ellipse_v (/ (* pi a Thick (+ (/ l_out_door 2) (/ l_in_door 2))) 2))
  (setq door_m  ( / (* (+ rec_v  ellipse_v) 7850) 1000000000)  )
)
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;;;;;;;;;;;;;;;;**************外爬梯重量确定********************;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defun Entrance_stairs_mass(entrance_stairs_name / stair_mass)
	(cond
		( (= entrance_entrance_stairs_name_name "2.5MW_Stairs")
			(setq stair_mass 2523)
		);
		( (= entrance_stairs_name "3MW_S_stair_2885")
			(setq stair_mass 890)
		);
		( (= entrance_stairs_name "3MW_S_stair_5970")
			(setq stair_mass 2350)
		);
		( (= entrance_stairs_name "2MW_Stairs_2778")
			(setq stair_mass 596)
		);
		( (= entrance_stairs_name "21_Stair")
			(setq stair_mass 822)
		);
		( (= entrance_stairs_name "5H_stair_3130")
			(setq stair_mass 824)
		);5H，V11
		( (= entrance_stairs_name "V12_stair_3130")
			(setq stair_mass 502)
		);V12
		( (= entrance_stairs_name "V15_stair_3130")
			(setq stair_mass 584)
		);V15
		( t
			(setq stair_mass 0)
		)
	);cond 
)
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


;;;;;;;;;;;;;;;;;;;;;顶法兰重量;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defun topflmass (TopFlangeName / FlangeMass1);顶法兰判断并插入
  (cond
    ( (= TopFlangeName "2MW_TopFlange")
      (setq FlangeMass1 2365.0)
    );2MW顶法兰
    ( (= TopFlangeName "2.5MW_TopFlange")
      (setq FlangeMass1 2344.0)
    );2.5/3MW顶法兰
    ( (= TopFlangeName "3MW_S_TopFlange")
      (setq FlangeMass1 4508.0)
    );3MWS顶法兰
    
    ( (= TopFlangeName "3MW_S_New_TopFlange")
      (setq FlangeMass1 3209.0)
    );3MWS新顶法兰

    ( (= TopFlangeName "21_TopFlange")
      (setq FlangeMass1 4987.0)
    );21#工程顶法兰

    ( (= TopFlangeName "5S_TopFlange")
      (setq FlangeMass1 5465.0)
    );5S工程顶法兰

    ( (= TopFlangeName "6MW_TopFlange")
      (setq FlangeMass1 7837.9)
    );6MWS顶法兰
    ( (= TopFlangeName "2.xMW_TopFlange")
      (setq FlangeMass1 2410.0)
    );2.XMWS顶法兰
    ( (= TopFlangeName "2MW-20_TopFlange") 
      (setq FlangeMass1 2358.0)
    );2MWS-20顶法兰
    ( (= TopFlangeName "other_TopFlange") 
      (setq FlangeMass1 0)
    );其他法兰

   ( (= TopFlangeName "1.5MW-22_TopFlange")
      (setq FlangeMass1 1235.0)
    );1.5MW-22顶法兰
   ( (= TopFlangeName "1SMW_TopFlange")
      (setq FlangeMass1 648.3)
    );1SMW顶法兰

   ( (= TopFlangeName "750T_TopFlange")
      (setq FlangeMass1 495.0)
    );750T顶法兰
   ( (= TopFlangeName "5X_TopFlange")
      (setq FlangeMass1 6484.0)
    );5X顶法兰
   ( (= TopFlangeName "V12_TopFlange")
      (setq FlangeMass1 4608.0)
   );V12-300顶法兰
    ( (= TopFlangeName "V12_TopFlange_250")
      (setq FlangeMass1 3893.0)
   );V12-250顶法兰
   
   ( (= TopFlangeName "V15_TopFlange")
      (setq FlangeMass1 5508.0)
   );V15-330 顶法兰
   ( (= TopFlangeName "V15_TopFlange_250")
      (setq FlangeMass1 4273.0)
   );V15-250顶法兰
   ( (= TopFlangeName "V17_TopFlange_400")
      (setq FlangeMass1 7198.0)
   );V17_TopFlange_400顶法兰
  );cond
)
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


;;;;;;;;;;;;;;;;;**************外爬梯重量确定********************;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defun Entrance_stairs_mass(entrance_stairs_name / stair_mass)
	(cond
		( (= entrance_entrance_stairs_name_name "2.5MW_Stairs")
			(setq stair_mass 2523)
		);
		( (= entrance_stairs_name "3MW_S_stair_2885")
			(setq stair_mass 890)
		);
		( (= entrance_stairs_name "3MW_S_stair_5970")
			(setq stair_mass 2350)
		);
		( (= entrance_stairs_name "2MW_Stairs_2778")
			(setq stair_mass 596)
		);
		( (= entrance_stairs_name "21_Stair")
			(setq stair_mass 818)
		);
		( (= entrance_stairs_name "5H_stair_3130")
			(setq stair_mass 824)
		);5H
		( (= entrance_stairs_name "V12_stair_3130")
			(setq stair_mass 502)
		);V12
		( (= entrance_stairs_name "V15_stair_3130")
			(setq stair_mass 584)
		);V15
		( t
			(setq stair_mass 0)
		)
	);cond 
)

;;;;;;;;;;;竖向法兰重量确定（仅针对分片塔）
(defun Vertical_flange_mass(/ vertical_fla_mass towerHeight);局部变量/竖向法兰重量、塔架高度
  (if (or(= topfltype "V12_TopFlange")(= topfltype "V12_TopFlange"))
    (progn ;V12重量判断并填入，其他机型不填重量
      (setq towerheight (- (value retFlange section_qty 0) (value retFlange 0 0)))  ;section_qty-塔筒段数
	  (cond 
        (
	      (and(> towerheight 125) (<= towerheight 130) (= section_qty 5))
          (setq vertical_fla_mass 10250)
	    )
	    (
	      (and(> towerheight 135) (<= towerheight 140) (= section_qty 6))
          (setq vertical_fla_mass 12200)
	    )
	    (
	      (and(> towerheight 140) (<= towerheight 145) (= section_qty 6))
          (setq vertical_fla_mass 13200)
	    )
	    ( t
          (setq vertical_fla_mass 0)
        )
      );end cond
	);end progn
    (setq vertical_fla_mass 0)
  );end if 
);;;;defun


;;;;;;;;;;;钢附件重量确定
(defun Accessory_mass_steel(/ accessoryMass towerHeight bottomDiameter);局部变量/附件重量、塔架高度、底部直径
  (if (or(= topfltype "V12_TopFlange")(= topfltype "V12_TopFlange"))
    (progn ;V12附件重量判断并填入，其他机型不填重量
	  (setq towerheight (- (value retFlange section_qty 0) (value retFlange 0 0)))
	  (setq bottomDiameter (atoi (rtos (-(value retTower 0 1) (value retTower 0 4)))))
      (cond 
	    (
   		 (and (> towerheight 95) (<= towerheight 100) (= section_qty 4) (= bottomDiameter 4450.0) )
		 (setq accessoryMass 8400)
		)
		(
   		 (and (> towerheight 95) (<= towerheight 100) (= section_qty 4) (= bottomDiameter 4950.0) )
		 (setq accessoryMass 9400)
		)
		(
   		 (and (> towerheight 95) (<= towerheight 100) (= section_qty 5) (= bottomDiameter 4450.0) )
		 (setq accessoryMass 9200)
		)
		(
   		 (and (> towerheight 95) (<= towerheight 100) (= section_qty 5) (= bottomDiameter 4950.0) )
		 (setq accessoryMass 10200)
		)
		(
   		 (and (> towerheight 100) (<= towerheight 105) (= section_qty 5) (= bottomDiameter 4450.0) )
		 (setq accessoryMass 9300)
		)
		(
   		 (and (> towerheight 100) (<= towerheight 105) (= section_qty 5) (= bottomDiameter 4950.0) )
		 (setq accessoryMass 10300)
		)
		(
   		 (and (> towerheight 105) (<= towerheight 110) (= section_qty 5) (= bottomDiameter 4450.0) )
		 (setq accessoryMass 9400)
		)
		(
   		 (and (> towerheight 105) (<= towerheight 110) (= section_qty 5) (= bottomDiameter 4950.0) )
		 (setq accessoryMass 10400)
		)
		(
   		 (and (> towerheight 110) (<= towerheight 115) (= section_qty 5) (= bottomDiameter 4450.0) )
		 (setq accessoryMass 9600)
		)
		(
   		 (and (> towerheight 110) (<= towerheight 115) (= section_qty 5) (= bottomDiameter 4950.0) )
		 (setq accessoryMass 10600)
		)
		(
   		 (and (> towerheight 115) (<= towerheight 120) (= section_qty 5) (= bottomDiameter 4450.0) )
		 (setq accessoryMass 9800)
		)
		(
   		 (and (> towerheight 115) (<= towerheight 120) (= section_qty 5) (= bottomDiameter 4950.0) )
		 (setq accessoryMass 10800)
		)
		(
   		 (and (> towerheight 125) (<= towerheight 130) (= section_qty 6) (= bottomDiameter 4950.0) )
		 (setq accessoryMass 12000)
		)
		(
   		 (and (> towerheight 125) (<= towerheight 130) (= section_qty 5) (= bottomDiameter 5950.0) )
		 (setq accessoryMass 11100)
		)
		(
   		 (and (> towerheight 135) (<= towerheight 140) (= section_qty 5) (= bottomDiameter 5950.0) )
		 (setq accessoryMass 11500)
		)
		(
   		 (and (> towerheight 140) (<= towerheight 145) (= section_qty 5) (= bottomDiameter 5950.0) )
		 (setq accessoryMass 11800)
		)
		( t
         (setq accessoryMass 0)
        )
	  );end cond
	);end progn
	(setq accessoryMass 0)
  );end if 
);;;;defun

;;;;;;;;;;;铝合金附件重量确定
(defun Accessory_mass_Al(/ accessoryMass towerHeight bottomDiameter);局部变量/附件重量、塔架高度、底部直径
  (if (or(= topfltype "V12_TopFlange")(= topfltype "V12_TopFlange"))
    (progn ;V12附件重量判断并填入，其他机型不填重量
	  (setq towerheight (- (value retFlange section_qty 0) (value retFlange 0 0)))
	  (setq bottomDiameter (atoi (rtos (- (value retTower 0 1) (value retTower 0 4)))))
      (cond 
		(
   		 (and (> towerheight 125) (<= towerheight 130) (= section_qty 5) (= bottomDiameter 5950.0) )
		 (setq accessoryMass 1400)
		)
		(
   		 (and (> towerheight 135) (<= towerheight 140) (= section_qty 6) (= bottomDiameter 5950.0) )
		 (setq accessoryMass 2000)
		)
		(
   		 (and (> towerheight 140) (<= towerheight 145) (= section_qty 6) (= bottomDiameter 5950.0) )
		 (setq accessoryMass 2000)
		)
		( t
         (setq accessoryMass 0)
        )
	  );end cond
	);end progn
	(setq accessoryMass 0)
  );end if 
);;;;defun


;flange
;;;;;;;;;;上连接法兰;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defun uflangedraw (pax i / flptori flptub flptdabl flptdabr flptdaul flptdaur flptoutbl flptoutbr flptoutul flptoutur neckh_fl);upper flange draw 连接法兰绘制
  (setq flptori pax);底法兰的下最下中心点
  ;(setq flptnb (polar flptori (/ pi 2) (value retFlange i 4)));底法兰法兰厚的中心下点
  ;;;法兰颈高判断
  (if (= i (- flange_qty 1));如果是最顶法兰
    (progn
      (if (or (= (value retFlange i 6) nil) (= (value retFlange i 6) " ") (= (value retFlange i 6) 0));如果颈高为空
        (setq neckh_fl (* (- (value retTower (- (nth i pos) 1) 2) (value retTower (- (nth i pos) 1) 0)) 1000));主体表中得到颈高
	(setq neckh_fl (value retFlange i 6))
      );end if
    );end progn
    ;如果不是顶法兰
    (setq neckh_fl (neck_h_fl_judge (value retFlange i 6) (value retFlange i 5)));法兰的颈高,过滤一下
  );end if
  (if (TorLflange i);如果此法兰是T型法兰
    (progn;如果是
      (setq flptdabl (polar flptori pi (/ (value retFlange i 1) 2)));法兰左下点
      (setq flptdabr (polar flptori 0 (/ (value retFlange i 1) 2)));法兰右下点
      
      (setq flptdaul (polar flptdabl (/ pi 2)  neckh_fl));
      (setq flptdaur (polar flptdabr (/ pi 2)  neckh_fl));

      (setq flptub (polar flptori (/ pi 2) neckh_fl));底法兰颈厚的中心下点

      (setq flptoutbl (polar flptub pi  (/ (value retFlange i 13) 2)));
      (setq flptoutbr (polar flptub 0  (/ (value retFlange i 13) 2)));    
      (setq flptoutul (polar flptoutbl (/ pi 2)  (value retFlange i 4)));
      (setq flptoutur (polar flptoutbr (/ pi 2)  (value retFlange i 4)));
      (addline flptoutbl flptoutbr );法兰厚的下横线
      (addline  flptoutul flptoutur );法兰厚上横线
      ;(addline  flptdaul flptdaur );最上横线
      (addline  flptoutbl flptoutul );法兰厚左线
      (addline  flptoutbr flptoutur );法兰厚右线
      (addline  flptdabl flptdaul );法兰壁的左线
      (addline  flptdabr flptdaur );法兰壁的右线
      (addline  flptdabl flptdabr );法兰壁下线
      ;竖直尺寸标注
      (cfldimv flptdabl flptoutul -2350)
      (dimd flptoutul flptoutur 1.0 -1000 -800 60);直径标注,标注线倾斜
    );end progn
    (progn;如果不是T型法兰
      (setq flptoutbl (polar flptori pi (/ (value retFlange i 1) 2)));底法兰左下点
      (setq flptoutbr (polar flptori 0 (/ (value retFlange i 1) 2)));底法兰右下点
      (setq flptdaul (polar flptoutbl (/ pi 2)  (+ (value retFlange i 4) neckh_fl)));底法兰颈左上点
      (setq flptdaur (polar flptoutbr (/ pi 2)  (+ (value retFlange i 4) neckh_fl)));底法兰颈右上点
      (if (= i section_qty)
        (progn;如果是顶法兰,标注的尺寸在最上
	  (if (or (= topfltype "3MW_S_TopFlange") (= topfltype "3MW_S_New_TopFlange"))
            (progn;如果是3S法兰
              (setq flpt3sl (polar flptoutbl (/ pi 2)  (- (+ (value retFlange i 4) neckh_fl) 80)));3s法兰的中间点
              (setq flpt3sr (polar flptoutbr (/ pi 2)  (- (+ (value retFlange i 4) neckh_fl) 80)));底法兰的中间点
	      (addline flpt3sl flpt3sr );3s法兰中间横线
	      
	      (setq flptdaul (polar flptdaul 0  20));3s法兰颈左上点
              (setq flptdaur (polar flptdaur pi  20));3s法兰颈右上点
              (addline flpt3sl flptdaul );3s法兰左斜线
	      (addline flpt3sr flptdaur );3s法兰右斜线
	      (addline flpt3sl flptoutbl );3s法兰左直线
	      (addline flpt3sr flptoutbr );3s法兰右直线	      

	      (if (not midornot)
	        (progn;外对齐塔架
                  (dimd flpt3sl flpt3sr 1.0 500 0 0);上直径标注
	        );end progn
	        (progn;中对齐塔架
	          (dimd_mid flpt3sl flpt3sr 1.0 500 0 0 "(法兰外径/FLG OD)");中间普通连接法兰直径标注
	        );end progn
              );end if 是否中对齐塔架
	      
	    );end progn
	    (progn;如果不是3s顶法兰
              (addline flptoutbl flptdaul );左线
              (addline flptoutbr flptdaur );右线
	      	    
	      (if (not midornot)
	        (progn;外对齐塔架
                  (dimd flptdaul flptdaur 1.0 500 0 0);上直径标注
	        );end progn
	        (progn;中对齐塔架
	          (dimd_mid flptdaul flptdaur 1.0 500 0 0 "(法兰外径/FLG OD)");中间普通连接法兰直径标注
	        );end progn
              );end if 是否中对齐塔架
	      
	    );end progn
          );end if
	  ;(dimd flptoutbl flptoutbr 1.0 -1000 -800 60);直径标注
	);end progn
	(progn;不是顶法兰
	  (addline flptoutbl flptdaul );左线
          (addline flptoutbr flptdaur );右线
	  
          ;(dimd flptdaul flptdaur 1.0 -1000 -800 60);中间普通连接法兰直径标注

	  (if (not midornot)
	    (progn;外对齐塔架
              (dimd flptdaul flptdaur 1.0 -1000 -800 60);中间普通连接法兰直径标注
	    );end progn
	    (progn;中对齐塔架
	      (dimd_mid flptdaul flptdaur 1.0 -1000 -100 60 "(法兰外径/FLG OD)");中间普通连接法兰直径标注
	    );end progn
          );end if 是否中对齐塔架
	  
	);end progn
      );end if
      (addline flptoutbl flptoutbr );最下横线
      (addline flptdaul flptdaur );最上横线
      (cfldimv flptoutbl flptdaul -2350);竖直尺寸标注
    );progn
  );end if
);end uflangedraw

;;;;;;;;;;;;;;;;;;;;;;;;;插入其他类型的底法兰;;;;;;;;;;;
(defun bottomflmass(BottomFlangeName flange_type Dout_fl Din_fl tfl_fl L_fl d_fl num_hole tn_fl R_fl DTout / FlangeMass1)
    (cond
		((= BottomFlangeName "ConcreteFlange_120_4500")
			(setq FlangeMass1 6790.0)
		)
		((= BottomFlangeName "ConcreteFlange_140_4500")
			(setq FlangeMass1 6683.0)
		)
		((= BottomFlangeName "ConcreteFlange_140_4500_35")
			(setq FlangeMass1 6529.4)
		)
		((= BottomFlangeName "ConcreteFlange_140_4300")
			(setq FlangeMass1 6222.0)
		)
		((= BottomFlangeName "GeneralBottomFl")
			(setq FlangeMass1 (FlangeMass flange_type Dout_fl Din_fl tfl_fl L_fl d_fl num_hole tn_fl R_fl DTout))
		)
		((= BottomFlangeName "ConcreteFlange_125")
			(setq FlangeMass1 6804.0)
		)
		(t
			(progn
				(if is_alert
					(alert "转接法兰重量为0，请修正对应焊合重量！")
				);end if 
				(setq FlangeMass1 0.0)
			);progn
		)
    );End cond
)
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;筒段底法兰绘制;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defun bflangedraw(pax i / flptori flptnb flptoutbl flptoutbr flptoutul flptoutur flptdabl flptdabr flptdaul flptdaur neckh_fl);bottom法兰绘制
  (setq flptori pax);底法兰的下最下中心点
  (setq flptnb (polar flptori (/ pi 2) (value retFlange i 4)));底法兰法兰厚的中心下点
  (setq neckh_fl (neck_h_fl_judge (value retFlange i 6) (value retFlange i 5)));法兰的颈高,过滤一下
  (if (TorLflange i);如果是T型法兰
    (progn;如果是T型法兰
      (setq flptoutbl (polar flptori pi (/ (value retFlange i 13) 2)));底法兰左下点
      (setq flptoutbr (polar flptori 0 (/ (value retFlange i 13) 2)));底法兰右下点
      (setq flptoutul (polar flptoutbl (/ pi 2)  (value retFlange i 4)));底法兰法兰厚左上点
      (setq flptoutur (polar flptoutbr (/ pi 2)  (value retFlange i 4)));底法兰法兰厚右上点
      (setq flptdabl (polar flptnb pi  (/ (value retFlange i 1) 2)));底法兰颈左下点
      (setq flptdabr (polar flptnb 0  (/ (value retFlange i 1) 2)));底法兰颈右下点
      (setq flptdaul (polar flptdabl (/ pi 2)  neckh_fl));底法兰颈左上点
      (setq flptdaur (polar flptdabr (/ pi 2)  neckh_fl));底法兰颈右上点
      ;;;绘制法兰轮廓
      (addline flptoutbl flptoutbr )
      (addline flptoutul flptoutur )
      (addline flptdaul flptdaur )
      (addline flptoutbl flptoutul )
      (addline flptoutbr flptoutur )
      (addline flptdabl flptdaul )
      (addline flptdabr flptdaur )
    );end progn
    (progn;如果是L型法兰
      (setq flptoutbl (polar flptori pi (/ (value retFlange i 1) 2)));底法兰左下点
      (setq flptoutbr (polar flptori 0 (/ (value retFlange i 1) 2)));底法兰右下点
      (setq flptdaul (polar flptoutbl (/ pi 2)  (+ neckh_fl (value retFlange i 4))));底法兰颈左上点
      (setq flptdaur (polar flptoutbr (/ pi 2)  (+ neckh_fl (value retFlange i 4))));底法兰颈右上点
      (addline flptoutbl flptoutbr )
      (addline flptdaul flptdaur )
      (addline flptoutbl flptdaul )
      (addline flptoutbr flptdaur )
    );end progn
  );end if
  (if (= i 0)
    (progn;如果是底法兰
      (ldimv flptoutbl flptdaul -2350);法兰高度标注
      ;(dimd flptoutbl flptoutbr 1.0 -1600 0 0);法兰下端面直径标注
      
      (if (not midornot)
	(progn;外对齐塔架
	  (dimd flptdaul flptdaur 1.0 250 -1150 0);法兰上端面直径标注
	  (dimd flptoutbl flptoutbr 1.0 -1600 0 0);法兰下端面直径标注
	);end progn
	(progn;中对齐塔架
	  (dimd_mid flptoutbl flptoutbr 1.0 -1600 0 0 "(法兰外径/FLG OD)");法兰下端面直径标注,
	);end progn
      );end if 是否中对齐塔架
    );end progn
    (progn
      (ldimv flptoutbl flptdaul -2500);法兰高度标注
    );end progn
  );end if
);;;;;;;函数结束



;BOLT
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;boltblock文件;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;******螺栓绘制函数;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defun bolt(boltType Height_1_flange / i rebolt); / boltdat_file i boltPitch WasherMaxThick NutMaxDim BoltHeadThick BoltHeadMinDim boltLength 3pboltLength pt pt1 pt2;pt3 pt4 pt5 pt6 pt7 pt8 pt9 pt91 pt10 pta ptb ptc ptd)
    (setvar "dimzin" 8);无小数
    ;(setq boltdat_file "D:\\Program Files (x86)\\Autodesk\\AutoLisp\\Data.xlsx")
    ;(setq retbolt (GetCellValueAsList boltdat_file "Bolt_Data" "C2:V17")) ;螺栓数据
    (setq retbolt retBoltDraw)
    (setq i 0)
    (while (<= i 15)
        (if (= (value retbolt i 0) boltType)
            (progn
	        (setq boltPitch (value retbolt i 2);螺距
	        WasherMaxThick (value retbolt i 4);垫片最大厚度
	        WasherThick (value retbolt i 5);垫片公称厚度
	        WasherDim (value retbolt i 6);垫片直径
	        NutThickness (value retbolt i 8);螺母最大厚度
	        NutMaxDim (value retbolt i 9);螺母最大外径
	        NutMinDim (* NutMaxDim (/ (expt 3 0.5)2));假定螺母的小径
	        BoltHeadThick (value retbolt i 14);螺栓头厚度
	        BoltHeadDim (value retbolt i 15);螺栓头直径
	        BoltHeadMinDim (* BoltHeadDim (/ (expt 3 0.5)2));假定螺栓头的小径
	        boltLength (+(+(+(* Height_1_flange 2) (* WasherMaxThick 2))NutThickness)(* 4 boltPitch));螺栓长度
	        3pboltLength (+(+(+(* Height_1_flange 2) (* WasherMaxThick 2))NutThickness)(* 3 boltPitch))
	        pt  '(0 0 0)
	        pt1 (polar (polar pt pi (/ boltType 2)) (* pi 1.5) WasherThick)
	        pt2 (polar pt1 0 boltType)
	        pte (polar pt (* pi 1.5) WasherThick)
	        ptd (polar pte (/ pi 2) boltLength)
	        pt3 (polar ptd pi (/ boltType 2))
	        pt4 (polar ptd 0 (/ boltType 2))
	        pta (polar pt (/ pi 2) (* Height_1_flange 2))
	        ptb (polar pta (/ pi 2) WasherThick)
	        ptc (polar ptb (/ pi 2) NutThickness)
	        pt5 (polar pta pi (/ WasherDim 2))
	        pt6 (polar pta 0 (/ WasherDim 2))
	        pt7 (polar ptb pi (/ WasherDim 2))
	        pt8 (polar ptb 0 (/ WasherDim 2))
	        ;螺母
	        pt9 (polar ptb pi (/ NutMaxDim 2))
	        pt91 (polar pt9 (* pi 0.5) (/ NutThickness 10))
	        pt10 (polar ptb 0 (/ NutMaxDim 2))
	        pt101 (polar pt10 (* pi 0.5) (/ NutThickness 10))
	        pt11 (polar ptc pi (/ NutMaxDim 2))
	        pt111 (polar pt11 (* pi 1.5) (/ NutThickness 10))
	        pt12 (polar ptc 0 (/ NutMaxDim 2))
	        pt121 (polar pt12 (* pi 1.5) (/ NutThickness 10))
	        pt23 (polar ptc pi(/ NutMaxDim 4))
	        pt231 (polar pt23 (* pi 1.5) (/ NutThickness 10))
	        pt24 (polar ptc 0(/ NutMaxDim 4))
	        pt241 (polar pt24 (* pi 1.5) (/ NutThickness 10))
	        R_nut (Radius3P pt231 ptc pt241)
	        ptCenter1 (polar ptc (* pi 1.5) R_nut)
	        sAng1 (angle ptCenter1 pt241)
	        eAng1 (angle ptCenter1 pt231)
	      
	        pt25 (polar ptb pi(/ NutMaxDim 4))
	        pt251 (polar pt25 (* pi 0.5) (/ NutThickness 10))
	        pt26 (polar ptb 0(/ NutMaxDim 4))
	        pt261 (polar pt26 (* pi 0.5) (/ NutThickness 10))
	        ptCenter2 (polar ptb (* pi 0.5) R_nut)
	        sAng2 (angle ptCenter2 pt251)
	        eAng2 (angle ptCenter2 pt261)
	      
	      
	        ptnm1 (polar ptc pi (/ NutMinDim 2))
	        ptnm2 (polar ptc 0 (/ NutMinDim 2))
	        ptnm3 (polar ptb pi (/ NutMinDim 2))
	        ptnm4 (polar ptb 0 (/ NutMinDim 2))
	        ptm1 (MiddlePoint ptnm1 pt23)
	        ptm2 (MiddlePoint pt24 ptnm2)
	        ptm3 (MiddlePoint ptnm3 pt25)
	        ptm4 (MiddlePoint pt26 ptnm4)
	        ;下垫片
	      
	        pt15 (polar pt pi (/ WasherDim 2))
	        pt16 (polar pt 0 (/ WasherDim 2))
	        pt13 (polar pte pi (/ WasherDim 2))
	        pt14 (polar pte 0 (/ WasherDim 2))
	      
	        pt19 (polar ptc pi (/ boltType 2))
	        pt20 (polar ptc 0 (/ boltType 2))
	        pt17 (polar pta pi (/ boltType 2))
	        pt18 (polar pta 0 (/ boltType 2))
	        pt21 (polar pt pi (/ boltType 2))
	        pt22 (polar pt 0 (/ boltType 2))
	        ;螺栓头
	        ptn1 (polar pte pi (/ BoltHeadDim 2))
	        ptn2 (polar pte 0 (/ BoltHeadDim 2))
	        ptn5 (polar pte pi (/ BoltHeadDim 4))
	        ptn6 (polar pte 0 (/ BoltHeadDim 4))
	        ptn7 (polar ptn5 (* pi 1.5) (* BoltHeadThick 0.9))
	        ptn8 (polar ptn6 (* pi 1.5) (* BoltHeadThick 0.9))
	        ptn9 (polar ptn1 (* pi 1.5) (* BoltHeadThick 0.9))
	        ptn10 (polar ptn2 (* pi 1.5) (* BoltHeadThick 0.9))
	        ptf (polar pte (* pi 1.5) BoltHeadThick)
	        ptn13 (polar ptf pi (/ BoltHeadMinDim 2))
	        ptn14 (polar ptf 0 (/ BoltHeadMinDim 2))
	        ptn15 (polar ptn5 (* pi 1.5) BoltHeadThick)
	        ptn16 (polar ptn6 (* pi 1.5) BoltHeadThick)
	        ptn11 (MiddlePoint ptn13 ptn15)
	        ptn12 (MiddlePoint ptn14 ptn16)
	        R_bolt (Radius3P ptn7 ptf ptn8)
	        ptCenter3 (polar ptf (* pi 0.5) R_bolt)
	        sAng3 (angle ptCenter3 ptn7)
	        eAng3 (angle ptCenter3 ptn8)
	        ;螺栓名字
	        boltName (strcat "bolt M" (rtos boltType) "×" (rtos 3pboltLength) "(3p)-" (rtos boltLength) "(4p)"))
	        (entmake (list '(0 . "BLOCK") (cons 2 boltName) '(70 . 0) (cons 10 pt)))
	        (entmake (list '(0 . "line") (cons 10 pt1) (cons 11 pt2)(cons 8"1轮廓实线层")))
	        (entmake (list '(0 . "line") (cons 10 pt21) (cons 11 pt17)(cons 8"1轮廓实线层")))
	        (entmake (list '(0 . "line") (cons 10 pt19) (cons 11 pt3)(cons 8"1轮廓实线层")))
	        (entmake (list '(0 . "line") (cons 10 pt22) (cons 11 pt18)(cons 8"1轮廓实线层")))
	        (entmake (list '(0 . "line") (cons 10 pt20) (cons 11 pt4)(cons 8"1轮廓实线层")))
	        (entmake (list '(0 . "line") (cons 10 pt3) (cons 11 pt4)(cons 8"1轮廓实线层")))
	        (entmake (list '(0 . "line") (cons 10 pt5) (cons 11 pt6)(cons 8"1轮廓实线层")))
	        (entmake (list '(0 . "line") (cons 10 pt7) (cons 11 pt8)(cons 8"1轮廓实线层")))
	
	        (entmake (list '(0 . "line") (cons 10 pt5) (cons 11 pt7)(cons 8"1轮廓实线层")))
	        (entmake (list '(0 . "line") (cons 10 pt6) (cons 11 pt8)(cons 8"1轮廓实线层")))
	        ;螺母
	        (entmake (list '(0 . "line") (cons 10 ptnm1) (cons 11 ptnm2)(cons 8"1轮廓实线层")))
	        (entmake (list '(0 . "line") (cons 10 pt91) (cons 11 pt111)(cons 8"1轮廓实线层")))
	        (entmake (list '(0 . "line") (cons 10 pt101) (cons 11 pt121)(cons 8"1轮廓实线层")))
	        (entmake (list '(0 . "line") (cons 10 pt231) (cons 11 pt251)(cons 8"1轮廓实线层")))
	        (entmake (list '(0 . "line") (cons 10 pt241) (cons 11 pt261)(cons 8"1轮廓实线层")))
	        (entmake (list '(0 . "line") (cons 10 pt111) (cons 11 ptnm1)(cons 8"1轮廓实线层")))
	        (entmake (list '(0 . "line") (cons 10 pt91) (cons 11 ptnm3)(cons 8"1轮廓实线层")))
	        (entmake (list '(0 . "line") (cons 10 pt121) (cons 11 ptnm2)(cons 8"1轮廓实线层")))
	        (entmake (list '(0 . "line") (cons 10 pt101) (cons 11 ptnm4)(cons 8"1轮廓实线层")))
	        (entmake (list '(0 . "ARC") (cons 10 ptCenter1) (cons 40 R_nut)(cons 50 sAng1)(cons 51 eAng1)(cons 8"1轮廓实线层")))
	        (entmake (list '(0 . "ARC") (cons 10 ptCenter2) (cons 40 R_nut)(cons 50 sAng2)(cons 51 eAng2)(cons 8"1轮廓实线层")))
	        (entmake (list '(0 . "line") (cons 10 ptm1) (cons 11 pt231)(cons 8"1轮廓实线层")))
	        (entmake (list '(0 . "line") (cons 10 ptm2) (cons 11 pt241)(cons 8"1轮廓实线层")))
	        (entmake (list '(0 . "line") (cons 10 ptm3) (cons 11 pt251)(cons 8"1轮廓实线层")))
	        (entmake (list '(0 . "line") (cons 10 ptm4) (cons 11 pt261)(cons 8"1轮廓实线层")))
	
	        ;下垫片
	        (entmake (list '(0 . "line") (cons 10 pt15) (cons 11 pt16)(cons 8"1轮廓实线层")))
	        (entmake (list '(0 . "line") (cons 10 pt13) (cons 11 pt14)(cons 8"1轮廓实线层")))
	        (entmake (list '(0 . "line") (cons 10 pt13) (cons 11 pt15)(cons 8"1轮廓实线层")))
	        (entmake (list '(0 . "line") (cons 10 pt16) (cons 11 pt14)(cons 8"1轮廓实线层")))
	
	        ;螺栓头
	        (entmake (list '(0 . "line") (cons 10 ptn1) (cons 11 ptn2)(cons 8"1轮廓实线层")))
	        (entmake (list '(0 . "line") (cons 10 ptn1) (cons 11 ptn9)(cons 8"1轮廓实线层")))
	        (entmake (list '(0 . "line") (cons 10 ptn2) (cons 11 ptn10)(cons 8"1轮廓实线层")))
	        (entmake (list '(0 . "line") (cons 10 ptn5) (cons 11 ptn7)(cons 8"1轮廓实线层")))
	        (entmake (list '(0 . "line") (cons 10 ptn6) (cons 11 ptn8)(cons 8"1轮廓实线层")))
	        (entmake (list '(0 . "line") (cons 10 ptn9) (cons 11 ptn13)(cons 8"1轮廓实线层")))
	        (entmake (list '(0 . "line") (cons 10 ptn7) (cons 11 ptn11)(cons 8"1轮廓实线层")))
	        (entmake (list '(0 . "line") (cons 10 ptn8) (cons 11 ptn12)(cons 8"1轮廓实线层")))
	        (entmake (list '(0 . "line") (cons 10 ptn10) (cons 11 ptn14)(cons 8"1轮廓实线层")))
	        (entmake (list '(0 . "line") (cons 10 ptn13) (cons 11 ptn14)(cons 8"1轮廓实线层")))
	        (entmake (list '(0 . "ARC") (cons 10 ptCenter3) (cons 40 R_bolt)(cons 50 sAng3)(cons 51 eAng3)(cons 8"1轮廓实线层")))
	
	        (entmake (list '(0 . "line") (cons 10 (polar ptf (* pi 1.5)5)) (cons 11 (polar ptd (* pi 0.5)5)) (cons 8"3中心线层")))
	        (entmake '((0 . "endblk")))
	    )
        );if结束
        (setq i (1+ i))
    ) ;while的右括号
)
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;;;;;;;;;;;;;;;;;;;;;;******************锚栓绘制;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defun anchor_bolt (boltType Height_1_flange / i retbolt)
  (setvar "dimzin" 8);无小数
  ; (setq boltdat_file "D:\\Program Files (x86)\\Autodesk\\AutoLisp\\Data.xlsx")
  ; (setq retbolt (GetCellValueAsList boltdat_file "Bolt_Data" "C2:V17")) ;螺栓数据
  (setq i 0)
  (setq retbolt retBoltDraw)
  ;;;boltType 42
  ;;;Height_1_flange 100
  (while (<= i 14)
    (if (= (value retbolt i 0) boltType)
      (progn
	;(print (value retbolt i 0))
	(setq boltPitch (value retbolt i 2);螺距
	      WasherMaxThick (value retbolt i 4);垫片最大厚度
	      WasherDim (value retbolt i 6);垫片直径
	      NutThickness (value retbolt i 8);螺母最大厚度
	      NutMaxDim (value retbolt i 9);螺母最大外径
	      NutMinDim(* NutMaxDim (/ (expt 3 0.5)2));假定螺母的小径
	      
	      boltLength(+(+(+(* Height_1_flange 2) WasherMaxThick)NutThickness)(* 4 boltPitch));螺栓长度
	      
	      pt  '(0 0 0)
	      
	      ptd(polar pt (/ pi 2) (* boltLength 1.1));加长
	      pt3 (polar ptd pi (/ boltType 2))
	      pt4 (polar ptd 0 (/ boltType 2))
	      pta(polar pt (/ pi 2) (* Height_1_flange 2))
	      ptb(polar pta (/ pi 2) WasherMaxThick)
	      ptc(polar ptb (/ pi 2) NutThickness)
	      pt5(polar pta pi (/ WasherDim 2))
	      pt6(polar pta 0 (/ WasherDim 2))
	      pt7(polar ptb pi (/ WasherDim 2))
	      pt8(polar ptb 0 (/ WasherDim 2))
	      
	      pt21(polar pt pi (/ boltType 2))
	      pt22(polar pt 0 (/ boltType 2))
	      Cpt1 (polar (MiddlePoint pt21 pt) (/ pi 2) (/ bolttype 4 (expt 3 0.5)))
	      Cpt2 (polar (MiddlePoint pt22 pt) (* pi 1.5) (/ bolttype 4 (expt 3 0.5)))
	      Cpt3 (polar (MiddlePoint pt22 pt) (/ pi 2) (/ bolttype 4 (expt 3 0.5)))
	      sAng3(angle Cpt1 pt21)
	      eAng3(angle Cpt1 pt)
	      sAng4(angle Cpt3 pt)
	      eAng4(angle Cpt3 pt22)
	      sAng5(angle Cpt2 pt22)
	      eAng5(angle Cpt2 pt)
	      ;螺母
	      pt9(polar ptb pi (/ NutMaxDim 2))
	      pt91(polar pt9 (* pi 0.5) (/ NutThickness 10))
	      pt10(polar ptb 0 (/ NutMaxDim 2))
	      pt101(polar pt10 (* pi 0.5) (/ NutThickness 10))
	      pt11(polar ptc pi (/ NutMaxDim 2))
	      pt111(polar pt11 (* pi 1.5) (/ NutThickness 10))
	      pt12(polar ptc 0 (/ NutMaxDim 2))
	      pt121(polar pt12 (* pi 1.5) (/ NutThickness 10))
	      pt23(polar ptc pi(/ NutMaxDim 4))
	      pt231(polar pt23 (* pi 1.5) (/ NutThickness 10))
	      pt24(polar ptc 0(/ NutMaxDim 4))
	      pt241(polar pt24 (* pi 1.5) (/ NutThickness 10))
	      R_nut(Radius3P pt231 ptc pt241)
	      ptCenter1(polar ptc (* pi 1.5) R_nut)
	      sAng1(angle ptCenter1 pt241)
	      eAng1(angle ptCenter1 pt231)
	      
	      pt25(polar ptb pi(/ NutMaxDim 4))
	      pt251(polar pt25 (* pi 0.5) (/ NutThickness 10))
	      pt26(polar ptb 0(/ NutMaxDim 4))
	      pt261(polar pt26 (* pi 0.5) (/ NutThickness 10))
	      ptCenter2(polar ptb (* pi 0.5) R_nut)
	      sAng2(angle ptCenter2 pt251)
	      eAng2(angle ptCenter2 pt261)
	      
	      
	      ptnm1(polar ptc pi (/ NutMinDim 2))
	      ptnm2(polar ptc 0 (/ NutMinDim 2))
	      ptnm3(polar ptb pi (/ NutMinDim 2))
	      ptnm4(polar ptb 0 (/ NutMinDim 2))
	      ptm1(MiddlePoint ptnm1 pt23)
	      ptm2(MiddlePoint pt24 ptnm2)
	      ptm3(MiddlePoint ptnm3 pt25)
	      ptm4(MiddlePoint pt26 ptnm4)
	      
	      pt19(polar ptc pi (/ boltType 2))
	      pt20(polar ptc 0 (/ boltType 2))
	      pt17(polar pta pi (/ boltType 2))
	      pt18(polar pta 0 (/ boltType 2))
	      ;螺栓名字
	      boltName (strcat "AnchorBolt M"(rtos boltType))
	      )
	(entmake (list '(0 . "BLOCK")
		       (cons 2 boltName)
		       '(70 . 0)
		       (cons 10 pta)
		       )
		 )
	(entmake (list '(0 . "ARC") (cons 10 Cpt1) (cons 40 (*(/ 2 (expt 3 0.5))(/ boltType 4)))(cons 50 sAng3)(cons 51 eAng3)(cons 8"4虚线层")))
	(entmake (list '(0 . "ARC") (cons 10 Cpt3) (cons 40 (*(/ 2 (expt 3 0.5))(/ boltType 4)))(cons 50 sAng4)(cons 51 eAng4)(cons 8"4虚线层")))
	(entmake (list '(0 . "ARC") (cons 10 Cpt2) (cons 40 (*(/ 2 (expt 3 0.5))(/ boltType 4)))(cons 50 sAng5)(cons 51 eAng5)(cons 8"4虚线层")))
	
	(entmake (list '(0 . "line") (cons 10 pt21) (cons 11 pt17)(cons 8"4虚线层")))
	(entmake (list '(0 . "line") (cons 10 pt19) (cons 11 pt3)(cons 8"4虚线层")))
	(entmake (list '(0 . "line") (cons 10 pt22) (cons 11 pt18)(cons 8"4虚线层")))
	(entmake (list '(0 . "line") (cons 10 pt20) (cons 11 pt4)(cons 8"4虚线层")))
	(entmake (list '(0 . "line") (cons 10 pt3) (cons 11 pt4)(cons 8"4虚线层")))
	(entmake (list '(0 . "line") (cons 10 pt5) (cons 11 pt6)(cons 8"4虚线层")))
	(entmake (list '(0 . "line") (cons 10 pt7) (cons 11 pt8)(cons 8"4虚线层")))
	
	(entmake (list '(0 . "line") (cons 10 pt5) (cons 11 pt7)(cons 8"4虚线层")))
	(entmake (list '(0 . "line") (cons 10 pt6) (cons 11 pt8)(cons 8"4虚线层")))
	;螺母
	(entmake (list '(0 . "line") (cons 10 ptnm1) (cons 11 ptnm2)(cons 8"4虚线层")))
	(entmake (list '(0 . "line") (cons 10 pt91) (cons 11 pt111)(cons 8"4虚线层")))
	(entmake (list '(0 . "line") (cons 10 pt101) (cons 11 pt121)(cons 8"4虚线层")))
	(entmake (list '(0 . "line") (cons 10 pt231) (cons 11 pt251)(cons 8"4虚线层")))
	(entmake (list '(0 . "line") (cons 10 pt241) (cons 11 pt261)(cons 8"4虚线层")))
	(entmake (list '(0 . "line") (cons 10 pt111) (cons 11 ptnm1)(cons 8"4虚线层")))
	(entmake (list '(0 . "line") (cons 10 pt91) (cons 11 ptnm3)(cons 8"4虚线层")))
	(entmake (list '(0 . "line") (cons 10 pt121) (cons 11 ptnm2)(cons 8"4虚线层")))
	(entmake (list '(0 . "line") (cons 10 pt101) (cons 11 ptnm4)(cons 8"4虚线层")))
	(entmake (list '(0 . "ARC") (cons 10 ptCenter1) (cons 40 R_nut)(cons 50 sAng1)(cons 51 eAng1)(cons 8"4虚线层")))
	(entmake (list '(0 . "ARC") (cons 10 ptCenter2) (cons 40 R_nut)(cons 50 sAng2)(cons 51 eAng2)(cons 8"4虚线层")))
	(entmake (list '(0 . "line") (cons 10 ptm1) (cons 11 pt231)(cons 8"4虚线层")))
	(entmake (list '(0 . "line") (cons 10 ptm2) (cons 11 pt241)(cons 8"4虚线层")))
	(entmake (list '(0 . "line") (cons 10 ptm3) (cons 11 pt251)(cons 8"4虚线层")))
	(entmake (list '(0 . "line") (cons 10 ptm4) (cons 11 pt261)(cons 8"4虚线层")))
	
	(entmake (list '(0 . "line") (cons 10 (polar pt (* pi 1.5)5)) (cons 11 (polar ptd (* pi 0.5)5)) (cons 8"3中心线层")))
	(entmake '((0 . "endblk")))
	)
      );if结束
    (setq i (1+ i))
    )
  
)
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;End boltblock;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;boltplm文件2,用于出详图，向上按10元整螺栓的长度;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;******************************************统计螺栓、垫圈、螺母数量*********************************/
(defun SetBoltNum2(retFlange Sum_FLange / i j ii jj iNut iWasher FlangeType)
  ;用于统计螺栓数量
	(setq FastenerListList(list (list 0 0 0 0))
		i (- Sum_Flange 1)
		)
	(while(>= i 0)
		(setq Height_1_flange(value retFlange i 4);法兰厚度
			num_hole (value retFlange i 9);螺栓孔数
			boltType(value retFlange i 7)
			d_hole(value retFlange i 8);螺栓孔直径
			boltclass(value retFlange i 12)
			tempflange_type (value retFlange i 11)
		)
		(if(= boltclass nil)
			(progn
				(if is_alert
					(alert "螺栓无等级或Excel模板有误！\n
						此处设置为10.9，请检查表格！")
				)
				(print "螺栓无等级或Excel模板有误！\n
				此处设置为10.9，请检查表格！")
				(setq boltclass 10.9)
			)
		)
		(setq FlangeType (FlangeTypeJudge tempflange_type i))
		(cond
			((and(= i 0)(= FlangeType "T")) (setq Sum_Flange(- Sum_flange 1)))
			((and(= i 0)(> d_hole 79)) (setq Sum_Flange(- Sum_flange 1)))
		)
      
		(if(< d_hole 79)
			(progn

			;计算螺栓长度
	
				(setq j 0)
				(while (<= j 15)
					(if (= (value retBoltDraw j 0) boltType)
						(progn
							(setq boltPitch (value retBoltDraw j 2);螺距
								WasherMaxThick (value retBoltDraw j 4);垫片最大厚度
								WasherDim (value retBoltDraw j 6);垫片直径
								NutThickness (value retBoltDraw j 8);螺母最大厚度
								4pboltLength(+(+(+(* Height_1_flange 2)(* 2 WasherMaxThick))NutThickness)(* 4 boltPitch));螺栓长度
								3pboltLength(+(+(+(* Height_1_flange 2)(* 2 WasherMaxThick))NutThickness)(* 3 boltPitch))
							)
							(if (> (/ 3pboltLength 10) (atoi(rtos (/ 3pboltLength 10) 2 0)))
								(setq boltLength (+ (* (atoi (rtos (/ 3pboltLength 10) 2 0)) 10) 10))
								(setq boltLength (* (atoi (rtos (/ 3pboltLength 10) 2 0)) 10))
							)
							(setq tempBoltType(strcat "M"(rtos boltType)"×"(rtos boltLength)"-"(rtos boltclass)))
						)
					)
		  
					(setq j(+ j 1))
				)
		
				(setq tempNutType(strcat "M"(rtos boltType)"-"(rtos(- boltclass 1)2 0)))
				(setq tempWasherType(strcat(rtos boltType)"-300HV"))
				(if(/= i 0)
					(progn
						(setq FastenerList(list(list tempBoltType num_hole tempNutType tempWasherType)))
						(setq FastenerListList(append FastenerListList FastenerList))
					)
					(if(/= FlangeType "T")
						(progn
							(setq FastenerList(list(list tempBoltType num_hole tempNutType tempWasherType)))
							(setq FastenerListList(append FastenerListList FastenerList))
						)
					)
				)
			)      
		)
		(setq i (- i 1))
	);END  while 
	;统计螺栓规格
	;设置初值
	(setq BoltLengthTypeList(list(list 0 0)))
	(setq WasherTypeList(list(list 0 0)))
	(setq NutTypeList(list(list 0 0)))
	;jj——列表序号FastenerListList
	(setq jj 1)
	(while (<= jj sum_flange)
		(setq BoltLengthType(nth 0(nth jj FastenerListList))
			BoltLengthNum(nth 1(nth jj FastenerListList))
			NutNum(nth 1(nth jj FastenerListList))
			NutType(nth 2(nth jj FastenerListList))
			WasherType(nth 3(nth jj FastenerListList))
			WasherNum(*(nth 1(nth jj FastenerListList))2)
		)
		;将列表中的螺栓每项与后面所有的项对比
		(setq ii jj)
		(setq iback jj)
		(setq statMark 1);设置统计标识，如果标识为1，则向下统计
		(while(> iback 0)
			(if(= BoltLengthType (nth 0(nth (- iback 1) FastenerListList)))
				(setq statMark 0);设置标识，如果标识为0，则不再向下统计
			)
			(setq iback(- iback 1))
		)
		(if (= statMark 1);如果标识为1，说明在这之前没有相同的螺栓，则进行向下统计
			(progn
				(while(< ii (-(length FastenerListList)1))
					(if(= BoltLengthType (nth 0(nth (+ ii 1) FastenerListList)))
						(setq BoltLengthNum(+ BoltLengthNum (nth 1(nth (+ ii 1) FastenerListList))))
					)
					(setq ii (+ ii 1))
				)
				(setq BoltLengthTypeList(append BoltLengthTypeList (list(list BoltlengthType BoltLengthNum))))
			)
		)
    
		;将列表中的螺母每项与后面所有的项对比
		(setq iNut jj)
		(setq iback jj)
		(setq statMark 1);设置统计标识，如果标识为1，则向下统计
		(while(> iback 0)
			(if(= NutType (nth 2(nth (- iback 1) FastenerListList)))
				(setq statMark 0);设置标识，如果标识为0，则不再向下统计
			)
			(setq iback(- iback 1))
		)
		(if (= statMark 1);如果标识为1，说明在这之前没有相同的螺母，则进行向下统计
			(progn
				(while(< iNut (-(length FastenerListList)1))
					(if(= NutType (nth 2(nth (+ iNut 1) FastenerListList)))
						(setq NutNum(+ NutNum (nth 1(nth (+ iNut 1) FastenerListList))))
					)
					(setq iNut (+ iNut 1))
				)
				(setq NutTypeList(append NutTypeList (list(list NutType NutNum))))
			)
		)
    
		;将列表中的垫圈每项与后面所有的项对比
		(setq iWasher jj)
		(setq iback jj)
		(setq statMark 1);设置统计标识，如果标识为1，则向下统计
		(while(> iback 0)
			(if(= WasherType (nth 3(nth (- iback 1) FastenerListList)))
				(setq statMark 0);设置标识，如果标识为0，则不再向下统计
			)
			(setq iback(- iback 1))
		)
		(if (= statMark 1);如果标识为1，说明在这之前没有相同的垫圈，则进行向下统计
			(progn
				(while(< iWasher (-(length FastenerListList)1))
					(if(= WasherType (nth 3(nth (+ iWasher 1) FastenerListList)))
						(setq WasherNum(+ WasherNum (*(nth 1(nth (+ iWasher 1) FastenerListList))2)))
					)
					(setq iWasher (+ iWasher 1))
				)
				(setq WasherTypeList(append WasherTypeList(list(list WasherType WasherNum))))
			)
		)
		(setq jj (+ jj 1))
	)
)
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;boltplm文件;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;******************************************统计螺栓、垫圈、螺母数量*********************************/
(defun SetBoltNum(retFlange Sum_FLange / i j ii jj iNut iWasher FlangeType)
  ;用于统计螺栓数量
  (setq FastenerListList(list (list 0 0 0 0))
	i (- Sum_Flange 1)
	)
  (while(>= i 0)
    (setq Height_1_flange(value retFlange i 4);法兰厚度
	  num_hole (value retFlange i 9);螺栓孔数
	  boltType(value retFlange i 7)
	  d_hole(value retFlange i 8);螺栓孔直径
	  boltclass(value retFlange i 12)
	  tempflange_type (value retFlange i 11)
	  )
    (if(= boltclass nil)
      (progn
	(if is_alert
	   (alert "螺栓无等级或Excel模板有误！\n
		   此处设置为10.9，请检查表格！")
	  )
	(print "螺栓无等级或Excel模板有误！\n
		此处设置为10.9，请检查表格！")
	(setq boltclass 10.9)
	)
      )
    (setq FlangeType(FlangeTypeJudge tempflange_type i))
    (cond((and(= i 0)(= FlangeType "T"))
	  (setq Sum_Flange(- Sum_flange 1)))
	 ((and(= i 0)(> d_hole 79))
	  (setq Sum_Flange(- Sum_flange 1)))
	 )
      
    (if(< d_hole 79)
      (progn

	;计算螺栓长度
	
	(setq j 0)
	(while (<= j 15)
	  (if (= (value retBoltDraw j 0) boltType)
	    (progn
	      (setq boltPitch (value retBoltDraw j 2);螺距
		    WasherMaxThick (value retBoltDraw j 4);垫片最大厚度
		    WasherDim (value retBoltDraw j 6);垫片直径
		    NutThickness (value retBoltDraw j 8);螺母最大厚度
		    4pboltLength(+(+(+(* Height_1_flange 2)(* 2 WasherMaxThick))NutThickness)(* 4 boltPitch));螺栓长度
		    3pboltLength(+(+(+(* Height_1_flange 2)(* 2 WasherMaxThick))NutThickness)(* 3 boltPitch))
		    )
	      (if (> (/ 3pboltLength 10) (atoi(rtos (/ 3pboltLength 10) 2 0)))
		(setq boltLength (+ (* (atoi (rtos (/ 3pboltLength 10) 2 0)) 10) 5))
		(setq boltLength (* (atoi (rtos (/ 3pboltLength 10) 2 0)) 10))
		)
	      (setq tempBoltType(strcat "M"(rtos boltType)"×"(rtos boltLength)"-"(rtos boltclass)))
	      )
	    )
	  
	  (setq j(+ j 1))
	  )
	
	(setq tempNutType(strcat "M"(rtos boltType)"-"(rtos(- boltclass 1)2 0)))
	(setq tempWasherType(strcat(rtos boltType)"-300HV"))
	(if(/= i 0)
	  (progn
	    (setq FastenerList(list(list tempBoltType num_hole tempNutType tempWasherType)))
	    (setq FastenerListList(append FastenerListList FastenerList))
	    )
	  (if(/= FlangeType "T")
	    (progn
	      (setq FastenerList(list(list tempBoltType num_hole tempNutType tempWasherType)))
	      (setq FastenerListList(append FastenerListList FastenerList))
	      )
	    )
	  )
	)      
      )
    (setq i (- i 1))
    )
  ;统计螺栓规格
  ;设置初值
  (setq BoltLengthTypeList(list(list 0 0)))
  (setq WasherTypeList(list(list 0 0)))
  (setq NutTypeList(list(list 0 0)))
  ;jj——列表序号FastenerListList
  (setq jj 1)
  (while (<= jj sum_flange)
    (setq BoltLengthType(nth 0(nth jj FastenerListList))
	  BoltLengthNum(nth 1(nth jj FastenerListList))
	  NutNum(nth 1(nth jj FastenerListList))
	  NutType(nth 2(nth jj FastenerListList))
	  WasherType(nth 3(nth jj FastenerListList))
	  WasherNum(*(nth 1(nth jj FastenerListList))2)
	  )
    ;将列表中的螺栓每项与后面所有的项对比
    (setq ii jj)
    (setq iback jj)
    (setq statMark 1);设置统计标识，如果标识为1，则向下统计
    (while(> iback 0)
      (if(= BoltLengthType (nth 0(nth (- iback 1) FastenerListList)))
	(setq statMark 0);设置标识，如果标识为0，则不再向下统计
	)
      (setq iback(- iback 1))
      )
    (if (= statMark 1);如果标识为1，说明在这之前没有相同的螺栓，则进行向下统计
      (progn
	(while(< ii (-(length FastenerListList)1))
	  (if(= BoltLengthType (nth 0(nth (+ ii 1) FastenerListList)))
	    (setq BoltLengthNum(+ BoltLengthNum (nth 1(nth (+ ii 1) FastenerListList))))
	    )
	  (setq ii (+ ii 1))
	  )
	(setq BoltLengthTypeList(append BoltLengthTypeList (list(list BoltlengthType BoltLengthNum))))
	)
      )
    
    ;将列表中的螺母每项与后面所有的项对比
    (setq iNut jj)
    (setq iback jj)
    (setq statMark 1);设置统计标识，如果标识为1，则向下统计
    (while(> iback 0)
      (if(= NutType (nth 2(nth (- iback 1) FastenerListList)))
	(setq statMark 0);设置标识，如果标识为0，则不再向下统计
	)
      (setq iback(- iback 1))
      )
    (if (= statMark 1);如果标识为1，说明在这之前没有相同的螺母，则进行向下统计
      (progn
	(while(< iNut (-(length FastenerListList)1))
	  (if(= NutType (nth 2(nth (+ iNut 1) FastenerListList)))
	    (setq NutNum(+ NutNum (nth 1(nth (+ iNut 1) FastenerListList))))
	    )
	  (setq iNut (+ iNut 1))
	  )
	(setq NutTypeList(append NutTypeList (list(list NutType NutNum))))
	)
      )
    
    ;将列表中的垫圈每项与后面所有的项对比
    (setq iWasher jj)
    (setq iback jj)
    (setq statMark 1);设置统计标识，如果标识为1，则向下统计
    (while(> iback 0)
      (if(= WasherType (nth 3(nth (- iback 1) FastenerListList)))
	(setq statMark 0);设置标识，如果标识为0，则不再向下统计
	)
      (setq iback(- iback 1))
      )
    (if (= statMark 1);如果标识为1，说明在这之前没有相同的垫圈，则进行向下统计
      (progn
	(while(< iWasher (-(length FastenerListList)1))
	  (if(= WasherType (nth 3(nth (+ iWasher 1) FastenerListList)))
	    (setq WasherNum(+ WasherNum (*(nth 1(nth (+ iWasher 1) FastenerListList))2)))
	    )
	  (setq iWasher (+ iWasher 1))
	  )
	(setq WasherTypeList(append WasherTypeList(list(list WasherType WasherNum))))
	)
      )
    (setq jj (+ jj 1))
    )
)
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;此处查询螺栓PLM号;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defun search_bolt_PLM(boltLengthType / i)
  ;(setq boltdat_file "D:\\Program Files (x86)\\Autodesk\\AutoLisp\\Data.xlsx")
  ;(setq boltPLM_data (GetCellValueAsList boltdat_file "Bolt" "E2:I400")) ;螺栓数据
  (setq boltPLM_data retPlmBolt)
  (setq i 0)
  (setq boltPLM "           ")
  (while (and(<= i 400)(/=(value boltPLM_data i 3)nil))
    (if (= (strcat(value boltPLM_data i 3) "×" (rtos(value boltPLM_data i 4)) "-10.9") boltLengthType)
      (setq boltPLM (value boltPLM_data i 0))
      )
    (setq i (+ i 1))
    (setq boltPLM boltPLM)
  )
);;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;此处查询螺母PLM号;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defun search_nut_PLM(nutType / i)
  ;(setq nutdat_file "D:\\Program Files (x86)\\Autodesk\\AutoLisp\\Data.xlsx")
  ;(setq nutPLM_data (GetCellValueAsList nutdat_file "Nut" "E2:H25") ;螺母数据
  (setq nutPLM_data retPlmNut)
  (setq i 0)
  (setq nutPLM "           ")
  (while (and(<= i 25)(/=(value nutPLM_data i 3)nil))
    (if (= (strcat(value nutPLM_data i 3) "-10") nutType)
      (setq nutPLM (value nutPLM_data i 0))
      )
    (setq i (+ i 1))
    (setq nutPLM nutPLM)
  )
 )
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;;;;;;;;;;;;;;;;;;;;;;;;;此处查询垫片PLM号;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defun search_washer_PLM(washerType / i)
  ;(setq washerdat_file "D:\\Program Files (x86)\\Autodesk\\AutoLisp\\Data.xlsx")
  ;(setq washerPLM_data (GetCellValueAsList washerdat_file "Washer" "E2:H20") ;垫圈数据
  (setq washerPLM_data retPlmWasher)
  (setq i 0)
  (setq washerPLM "           ")
  (while (and(<= i 20)(/=(value washerPLM_data i 3)nil))
    (if (= (strcat(rtos(value washerPLM_data i 3)) "-300HV") washerType)
      (setq washerPLM (value washerPLM_data i 0))
      )
    (setq i (+ i 1))
    (setq washerPLM washerPLM)
  )
)
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;END boltplm;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


;Embedded
;********************************判断并插入基础环*****************************
(defun Embedded_insert(/ oript btm_da Di num dhole tf hf ts da_e ts_e n_cy a_cy b_cy h_cy da_base di_base tf_base h_total
				 r MassOfEmbeddedbase MassOfEmbeddedShell Embedded_scale)
  (if (= (EmbeddedOrNot) T)
    (progn;基础环数据不为空，且底法兰为L型法兰时
      (setq btm_da (value retFlange 0 1);底法兰外径
	    Di (value retFlange 0 2);底法兰内径
	    num (value retFlange 0 9);螺栓数
	    dhole(value retFlange 0 8);螺栓孔直径
    	    tf (value retEmbedded 0 0);基础环顶法兰厚度
	    hf (value retEmbedded 1 0);基础环顶法兰颈高
    	    ts(value retEmbedded 2 0);基础环顶法兰颈厚
	    da_e (value retEmbedded 4 0);基础环筒体外径
	    ts_e (value retEmbedded 5 0);基础环筒体壁厚
	    n_cy(value retEmbedded 7 0);椭圆孔个数
	    a_cy(value retEmbedded 8 0);椭圆孔长半轴
	    b_cy(value retEmbedded 9 0);椭圆孔短半轴
	    h_cy(value retEmbedded 10 0);椭圆孔中心至顶法兰距离
	    da_base (value retEmbedded 12 0);基础环底法兰外径
	    di_base (value retEmbedded 13 0);基础环底法兰内径
	    tf_base (value retEmbedded 14 0);基础环底法兰厚度
	    h_total (value retEmbedded 16 0);基础环总高度
      )
      (setq r 10)
      (setq BP_pa (nth 0 sthptlist));定义原点
      (setq oript BP_pa)
      ;;生成基础环上的椭圆孔
      (Embedded_ellipse a_cy b_cy)
      ;主体图上的基础环绘制
      (Embedded_steel_can_front oript btm_da tf hf da_e n_cy h_cy da_base tf_base h_total)
      ;基础环上法兰重量
      (setq MassOfEmbeddedFl (embeddedflmass btm_da Di ts r hf tf num dhole))
      ;基础环椭圆孔重量
      (setq MassOfEmbeddedEllipse (embeddedellipsemass n_cy a_cy b_cy ts_e))
      ;基础环筒体重量
      (setq MassOfEmbeddedShell (embeddedshellmass da_e h_total tf hf tf_base ts_e))
      ;基础环底拼接法兰重量
      (setq MassOfEmbeddedbase (embeddedbaseflmass da_base di_base tf_base))
      ;基础环的重量
      ;(setq mass_of_embedded (mass_embedded btm_da Di ts r hf tf num dhole))   ; 计算重量用
      (setq mass_of_embedded (- (+ MassOfEmbeddedFl MassOfEmbeddedShell MassOfEmbeddedbase) MassOfEmbeddedEllipse))
      (setq sum_flange (- flange_qty 1))
      ;主体基础环放大符号标注
      (FlangeZoom (polar (polar Bp_pa (* pi 1.5) (- h_total tf_base))0 (/ da_e 2)) (+ sum_flange 1) mscale T nil)
      (setq Embedded_scale 5.0);基础环放大比例
      ;基础环放大视图绘制

      (if (= (rem flange_qty 2) 0);如果是偶数
        (setq pt_embed (nth (+ section_qty 2) enkeyptlist))
	(setq pt_embed (nth (+ section_qty 2) enkeyptlist))
      )
      
      (Embedded_steel_can_detail pt_embed (/ mscale Embedded_scale) da_e ts_e da_base di_base tf_base MassOfEmbeddedbase (- MassOfEmbeddedShell MassOfEmbeddedEllipse))
    )
  )
)
;********************************判断并插入基础环结束*************************


;;;;基础环上的椭圆孔块生成;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defun Embedded_ellipse (a_cy b_cy / cylinderPoint halfLAxis halfSAxis ratio cylindername)
  (setq cylinderPoint '(0 0 0)
	halfLAxis a_cy  
	halfSAxis b_cy
	ratio (/ halfSAxis halfLAxis)
	cylindername "cylinder"
  )
  (entmake (list '(0 . "BLOCK")
		 (cons 2 cylindername)
		 '(70 . 0)
		 (cons 10 cylinderPoint)
	   )
  )
  (entmake (list '(0 . "ELLIPSE")
		 '(100 . "AcDbEntity")
		 '(100 . "AcDbEllipse")
		 (cons 10 cylinderPoint)
		 (cons 11 (list 0 halfLAxis 0))
		 (cons 40 ratio)
		 (cons 41 (sk_el_ang->Par(ang->rad -90) ratio))
		 (cons 42 (sk_el_ang->Par(ang->rad 90) ratio))
		 (cons 8 "1轮廓实线层")
	   )
  )
  (entmake (list '(0 . "ELLIPSE")
		 '(100 . "AcDbEntity")
		 '(100 . "AcDbEllipse")
		 (cons 10 cylinderPoint)
		 (cons 11 (list 0 halfLAxis 0))
		 (cons 40 ratio)
		 (cons 41 (sk_el_ang->Par(ang->rad 90) ratio))
		 (cons 42 (sk_el_ang->Par(ang->rad 270) ratio))
		 (cons 8"1轮廓实线层")
	   )
  )
  (entmake '((0 . "endblk")))   ; 第一个椭圆块生成完成

);;;;函数结束;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;;;;;;;;;;;;;;;;;;;;基础环正面绘制;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defun Embedded_steel_can_front (oript btm_da tf hf da_e n_cy h_cy da_base tf_base h_total
				 / Em_point r i)
  (setq	r 10
	Em_point oript
	mpt1 (polar Em_point (* pi -0.5) (+ tf hf))
	mpt2 (polar Em_point (* pi -0.5) h_cy)
	mpt4 (polar Em_point (* pi -0.5) h_total)
	mpt3 (polar mpt4 (* pi 0.5) tf_base)
	pt1 (polar Em_point pi (/ btm_da 2))
	pt2 (polar Em_point 0 (/ btm_da 2))
	pt3 (polar mpt1 pi (/ btm_da 2))
	pt4 (polar mpt1 0 (/ btm_da 2))
	pt5 (polar mpt1 pi (/ da_e 2))
	pt6 (polar mpt1 0 (/ da_e 2))
	pt7 (polar mpt3 pi (/ da_e 2))
	pt8 (polar mpt3 0 (/ da_e 2))
	pt9 (polar mpt3 pi (/ da_base 2))
	pt10 (polar mpt3 0 (/ da_base 2))
	pt11 (polar mpt4 pi (/ da_base 2))
	pt12 (polar mpt4 0 (/ da_base 2))
	ptn (polar mpt2 pi (+ (/ da_e 2) 100))
	ptm (polar mpt2 0 (+ (/ da_e 2) 100))
	;cylinderPoint '(0 0 0)
	cylindername "cylinder"
  )
  (entmake (list '(0 . "line") (cons 10 pt1) (cons 11 pt2)(cons 8"1轮廓实线层")))
  (entmake (list '(0 . "line") (cons 10 pt1) (cons 11 pt3)(cons 8"1轮廓实线层")))
  (entmake (list '(0 . "line") (cons 10 pt2) (cons 11 pt4)(cons 8"1轮廓实线层")))
  (entmake (list '(0 . "line") (cons 10 pt3) (cons 11 pt4)(cons 8"1轮廓实线层")))
  (entmake (list '(0 . "line") (cons 10 pt5) (cons 11 pt6)(cons 8"1轮廓实线层")));基础环筒体上直线
  (entmake (list '(0 . "line") (cons 10 pt5) (cons 11 pt7)(cons 8"1轮廓实线层")));基础环筒体左竖线
  (entmake (list '(0 . "line") (cons 10 pt6) (cons 11 pt8)(cons 8"1轮廓实线层")));基础环筒体右竖线
  (entmake (list '(0 . "line") (cons 10 pt9) (cons 11 pt10)(cons 8"1轮廓实线层")));基础环底法兰上横线
  (entmake (list '(0 . "line") (cons 10 pt9) (cons 11 pt11)(cons 8"1轮廓实线层")));基础环底法兰左竖线
  (entmake (list '(0 . "line") (cons 10 pt10) (cons 11 pt12)(cons 8"1轮廓实线层")));基础环底法兰右竖线
  (entmake (list '(0 . "line") (cons 10 pt11) (cons 11 pt12)(cons 8"1轮廓实线层")));基础环底法兰下横线
  (entmake (list '(0 . "line") (cons 10 ptn) (cons 11 ptm) (cons 8"3中心线层")));椭圆所在所在位置的横线
  (entmake (list '(0 . "line") (cons 10 Em_point) (cons 11 (polar mpt4 (* pi 1.5) 100))(cons 8"3中心线层")));中心线
  
  (entmake (list '(0 . "Insert")
		 (cons 2 cylindername)
		 (cons 10 mpt2)
	   )
  )
  (setq i 1)
  (while (< i (/ n_cy 4))
    (entmake (list '(0 . "Insert")
		 (cons 2 cylindername)
		 (cons 10 (polar mpt2 0 (* (/ da_e 2)(cos (* (-(/ n_cy 4)i)(/ (* 2 pi)n_cy))))))
		 (cons 41 (- 1 (/ i (/ n_cy 4))))
	     )
    )
    (entmake (list '(0 . "Insert")
		 (cons 2 cylindername)
		 (cons 10 (polar mpt2 pi (* (/ da_e 2)(cos (* (-(/ n_cy 4)i)(/ (* 2 pi)n_cy))))))
		 (cons 41 (- 1 (/ i (/ n_cy 4))))
	     )
    )
    (setq i (+ i 1))
  )
  ;基础环底法兰直径
  (dimd pt11 pt12 1 -1000 0 0)
  ;基础环筒壁直径
  (dimd pt7 pt8 1 600 -800 0)
  ;基础环总高
  (ldimv pt1 pt11 -3300)
  ;椭圆孔中心位置
  (ldimv pt1 ptn -800)
);;;函数结束




;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;基础环放大视图
(defun Embedded_steel_can_detail(oript scale da_e ts_e da_base di_base tf_base mass_base shellmass /
				 zoomEmbeddedPoint width_embedded dist_outer ept1 ept2
				 ept3 ept4 ept5 ept6 ept7 ept8 ept10 ept9 lst spt1 spt2 spt3 spt4 spt5
				 mpt1 mpt2)
  ;放大视图块
  (setq zoomEmbeddedPoint oript)
  (setq tf_base (* tf_base scale)
        ts_e (* ts_e scale)
  )
  (setq width_embedded (* (/ (- da_base di_base) 2) scale);基础环底法兰宽
	dist_outer (* (/ (- da_base da_e) 2) scale);基础环底法兰外面至基础环筒体外面距离	
        ept1 zoomEmbeddedPoint
	ept2 (polar ept1 0 (* (* mscale 2) scale))
	ept3 (polar ept2 0 width_embedded)
	ept4 (polar ept1 (* pi 1.5) tf_base)
	ept5 (polar ept2 (* pi 1.5) tf_base)
	ept6 (polar ept3 (* pi 1.5) tf_base)
	ept7 (polar ept3 pi dist_outer)
	ept8 (polar ept7 pi ts_e)
	
	ept10 (polar ept7 (/ pi 2) (* (* mscale 2) scale))
	ept9 (polar ept8 (/ pi 2) (* (* mscale 2) scale))
	lst (list ept10 ept9 ept1 ept4)
	 
        spt1(polar ept8 0 (/ ts_e 2))
	spt2(polar ept8 pi (/ ts_e 2))
	spt3(polar ept7 0 (/ ts_e 2))
	spt4(polar ept8 (* pi 0.5) (/ ts_e 2))
	spt5(polar ept7 (* pi 0.5) (/ ts_e 2))
	mpt1 (MiddlePoint spt4 ept8)
	mpt2 (MiddlePoint spt5 ept7)
  )
  (entmake (list '(0 . "line") (cons 10 ept1) (cons 11 ept3)(cons 8"1轮廓实线层")))
  (entmake (list '(0 . "line") (cons 10 ept4) (cons 11 ept6)(cons 8"1轮廓实线层")))
  (entmake (list '(0 . "line") (cons 10 ept2) (cons 11 ept5)(cons 8"1轮廓实线层")))
  (entmake (list '(0 . "line") (cons 10 ept3) (cons 11 ept6)(cons 8"1轮廓实线层")))
  (entmake (list '(0 . "line") (cons 10 spt4) (cons 11 ept9)(cons 8"1轮廓实线层")))
  (entmake (list '(0 . "line") (cons 10 spt5) (cons 11 ept10)(cons 8"1轮廓实线层")))
  (entmake (list '(0 . "line") (cons 10 spt1) (cons 11 spt4)(cons 8"1轮廓实线层")))
  (entmake (list '(0 . "line") (cons 10 spt2) (cons 11 spt4)(cons 8"1轮廓实线层")))
  (entmake (list '(0 . "line") (cons 10 spt1) (cons 11 spt5)(cons 8"1轮廓实线层")))
  (entmake (list '(0 . "line") (cons 10 spt5) (cons 11 spt3)(cons 8"1轮廓实线层")))
  (entmake
	(append
		(list
		'(0 . "SPLINE")
		'(100 . "AcDbEntity")
		'(100 . "AcDbSpline")
		'(70 . 40)
		'(71 . 3)
		(cons 74 (length lst))
		'(44 . 1.0e-005)
		(cons 8"2细线层")
		)
	(mapcar '(lambda (x) (cons 11 x)) lst)
	)
  )
  (entmake (list '(0 . "HATCH")
		 '(100 . "AcDbEntity")
		 '(67 . 0)
		 '(410 . "Model")
		 (cons 8 "5剖面线层")
		 '(100 . "AcDbHatch")
		 '(10 0.0 0.0 0.0)
		 '(210 0.0 0.0 1.0)
		 '(2 . "ANSI31")
		 '(70 . 0)
		 '(71 . 0)
		 '(91 . 1)
		 '(92 . 3)
		 '(72 . 0)
		 '(73 . 1)
		 '(93 . 4)
		 (cons 10 ept2)
		 (cons 10 ept3)
		 (cons 10 ept6)
		 (cons 10 ept5)
		 '(97 . 0)
		 '(75 . 0)
		 '(76 . 1)
		 '(52 . 0.0)
		 '(41 . 100.0);比例
		 '(77 . 0)
		 '(78 . 1)
		 '(53 . 0.785398)
		 '(43 . 0.0)
		 '(44 . 0.0)
		 '(45 . -11.2253)
		 '(46 . 11.2253)
		 '(79 . 0)
		 '(98 . 0)
		 
		 )
	   )
  (entmake (list '(0 . "HATCH")
		 '(100 . "AcDbEntity")
		 '(67 . 0)
		 '(410 . "Model")
		 (cons 8 "5剖面线层")
		 '(100 . "AcDbHatch")
		 '(10 0.0 0.0 0.0)
		 '(210 0.0 0.0 1.0)
		 '(2 . "ANSI31")
		 '(70 . 0)
		 '(71 . 0)
		 '(91 . 1)
		 '(92 . 3)
		 '(72 . 0)
		 '(73 . 1)
		 '(93 . 4)
		 (cons 10 ept9)
		 (cons 10 ept10)
		 (cons 10 ept7)
		 (cons 10 ept8)
		 '(97 . 0)
		 '(75 . 0)
		 '(76 . 1)
		 '(52 . 1.5708)
		 '(41 . 100.0)
		 '(77 . 0)
		 '(78 . 1)
		 '(53 . 0.785398)
		 '(43 . 0.0)
		 '(44 . 0.0)
		 '(45 . -11.2253)
		 '(46 . 11.2253)
		 '(79 . 0)
		 '(98 . 0)
		 
		 )
	   )
  (entmake (list '(0 . "HATCH") 
	         '(100 . "AcDbEntity")
		 '(67 . 0)
		 '(410 . "Model")
		 (cons 8 "5剖面线层")
		 '(100 . "AcDbHatch")
		 '(10 0.0 0.0 0.0)
		 '(210 0.0 0.0 1.0)
		 '(2 . "SOLID")
		 '(70 . 1)
		 '(71 . 0)
		 '(91 . 2)
		 '(92 . 7)
		 '(72 . 0)
		 '(73 . 1)
		 '(93 . 3)
		 (cons 10 spt4)
		 (cons 10 spt2)
		 (cons 10 spt1)
		 '(97 . 0)
		 '(92 . 7)
		 '(72 . 0)
		 '(73 . 1)
		 '(93 . 3)
		 (cons 10 spt5)
		 (cons 10 spt1)
		 (cons 10 spt3)
		 '(97 . 0)
		 '(75 . 1)
		 '(76 . 1)
		 '(47 . 17.7311)
		 '(98 . 2)
		 (cons 10 mpt1)
		 (cons 10 mpt2)
		 '(450 . 0)
		 '(451 . 0)
		 '(460 . 0.0)
		 '(461 . 0.0)
		 '(452 . 0)
		 '(462 . 1.0)
		 '(453 . 2)
		 '(463 . 0.0)
		 '(63 . 5)
		 '(421 . 255)
		 '(463 . 1.0) '(63 . 2) '(421 . 16776960) '(470 . "LINEAR")))
	 ;(Dimflange (polar ept5 pi (* di_base scale)) ept5 (polar ept4 (* pi 1.5) 1200) scale 0 hole);底法兰最内径
	 ;(Dimflange (polar ept6 pi (* da_base scale)) ept6 (polar ept4 (* pi 1.5) 2400) scale 0 hole);底法兰最外径
	 ;(Dimflange (polar ept10 pi (* da_e scale)) ept10 (polar ept4 (* pi 0.5) 7700) scale 0 hole);基础环筒体直径

         (DimflD (polar ept5 pi (* di_base scale)) ept5  (/ 1 scale) 0 0 (polar ept4 (* pi 1.5) 1200));底法兰最内径
         (DimflD (polar ept6 pi (* da_base scale)) ept6  (/ 1 scale) 0 0 (polar ept4 (* pi 1.5) 2400));底法兰最外径
         (DimflD (polar ept10 pi (* da_e scale)) ept10  (/ 1 scale) 0 0 (polar ept4 (* pi 0.5) 7600));基础环筒体直径

  
	 ;(dimFlange_thick ept3 ept6 "R" scale) ;基础环底法兰底法兰厚度
         (ldimv2 ept3 ept6 (/ 1 scale ) 0 2500);基础环底法兰底法兰厚度
         (scaleDim ept9 ept10 scale 1) ;标注壁厚
	 ;(FlangeZoomTitle (polar ept2 (* pi 0.5) (* 65 mScale)) (+ sum_flange 1))  ;基础环放大视图符号
  
         (FlangeZoomTitle (polar ept2 (* pi 0.5) (* 65 mScale)) (+ sum_flange 1) (/ mscale scale))  ;基础环放大视图符号
         ;筒体及底法兰重量的标注
         (setq base_ept (polar ept6 (* pi 0.67) tf_base))
         (setq cylinder_ept (polar ept7 (* pi 0.55) tf_base ))
         (EmbeddedLead base_ept cylinder_ept mass_base shellmass )
);;;;;函数结束


;*********************************总图明细表文本导出***********************/
(defun BomList_detail(TowerExcelFile SectionMassList FlangeMassList stair_mass Sum_FLange MassDoorFrame MassOfTowerDoorHole
	       / i stairname staircode stairname_e Liftname  Liftname_e Liftcode preBomName BomTitle
                 SectionIndex SectionIndexEN Anticorrosion AnticorrosionEN preBoltName preBoltNameEN preNutName preNutNameEN preWasherName preWasherNameEN
                 sufSectionName sufWeldName BomListTxt all_tower_mass Index embedded_mass_str embeddedBom all_tower_mass
                 stair_mass_str stair_mass_str all_tower_mass StairBom
                 LiftBom seccount i WeldName  WeldNameEN section_mass SectionNameEN SectionName TowerSectionMass SectionName WeldBom secIndex SectionBom
                 EIABom AttachBom LugrightBom LugleftBom PLUGBom LadderBom BushesBom RubberBom BoltM12Bom
                 GeneraloneBom MiddletopBom GeneralflBom flange_type BoltprotectBom ExternalplatBom FireBom
		 nn)

	(cond
		( (= topfltype "2MW_TopFlange")
		);2MW顶法兰
		( (= topfltype "2.5MW_TopFlange")
		);2.5/3MW顶法兰
		( (= topfltype "3MW_S_TopFlange")
		);3MWS顶法兰
    
		( (= topfltype "3MW_S_New_TopFlange")
			(setq stairname "\"塔架入口梯子总成\"")
			(setq staircode "\"60.08.04646\"")
			(setq stairname_e "\"Entrance stair\"")
			(setq Liftname "\"升降机模块-钢丝绳导向型\"")
			(setq Liftname_e "\"Lift module-Wire rope guide type\"")
			(setq Liftcode "\"60.10.74335\"")
		);3MWS新顶法兰

		( (= topfltype "21_TopFlange");21#工程顶法兰
			(setq stairname "\"入口梯模块\"")
			(setq staircode "\"60.10.72524\"")
			(setq stairname_e "\"Entrance stair module\"")
			(setq Liftname "\"升降机模块-钢丝绳导向型\"")
			(setq Liftname_e "\"Lift module-Wire rope guide type\"")
			(setq Liftcode "\"60.10.74335\"")
		);

		( (= topfltype "5S_TopFlange");5S工程顶法兰
		);

		( (= topfltype "6MW_TopFlange");6MWS顶法兰
		);

    
		( (= topfltype "2.xMW_TopFlange");2.XMWS顶法兰
			(setq stairname "\"塔架入口梯子总成\"")
			(setq staircode "\"60.08.04580\"")
			(setq stairname_e "\"Entrance stair assembly\"")
			(setq Liftname "\"钢丝绳导向正面右侧开门塔筒升降机\"")
			(setq Liftname_e "\"Wire guided lift with righted door\"")
			(setq Liftcode "\"60.10.04003\"")
		);
    
		( (= topfltype "2MW-20_TopFlange") 
		);2MWS-20顶法兰
		( (= topfltype "other_TopFlange") 
		);其他法兰

		( (= topfltype "1.5MW-22_TopFlange")
		);1.5MW-22顶法兰
    
		( (= topfltype "1SMW_TopFlange")
		);1SMW顶法兰

		( (= topfltype "750T_TopFlange")
		);750T顶法兰
    
	);cond

  
	(setq preBomName (GetDir TowerExcelFile));明细表的名称
	;明细表表头写入
	(setq BomTitle (strcat "\"*\"" "	"
			 "\"序号\"" "	"
			 "\"是否出图\"" "	"
			 "\"代号\"" "	"
			 "\"版本\"" "	"
			 "\"名称\"" "	"
			 "\"数量\"" "	"
			 "\"材料\"" "	"
			 "\"单重\"" "	"
			 "\"总重\"" "	"
			 "\"备注\"" "	"
			 "\"DRAWINGNUMBER\"" "	"
			 "\"NAME\"" "	"
			 "\"MATERIAL\"" "	"
			 "\"NOTES\"" "	"
			 "\"零件类型\"" "	"
			 "\"显示状态\"" "	")
		SectionIndex (list "\"第一" "\"第二" "\"第三" "\"第四" "\"第五" "\"第六" "\"第七" "\"第八" "\"第九" "\"第十" "\"第十一" "\"第十二")
		SectionIndexEN (list "Ⅰ " "Ⅱ " "Ⅲ " "Ⅳ " "Ⅴ " "Ⅵ " "Ⅶ " "Ⅷ " "Ⅸ " "Ⅹ " "Ⅺ " "Ⅻ "))
	(setq Anticorrosion "\"达克罗\""
		AnticorrosionEN "\"Dacromet\""
		preBoltName "\"螺栓GB/T5782-"
		preBoltNameEN "\"Bolt ISO4014-"
		preNutName "\"螺母GB/T6170-"
		preNutNameEN "\"Nut ISO4032-"
		preWasherName "\"垫圈EN14399-6-"
		preWasherNameEN "\"Washer EN14399-6-")
	(setq sufSectionName "段塔筒附件总成\"")
	(setq sufWeldName "段塔筒焊合\"")
  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
	(setq BomListTxt (open (strcat preBomName "txt") "w"))
	(write-line BomTitle BomListTxt);把BomTitle写到BomListTxt

	;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
	;基础环/塔架底座  
	(setq all_tower_mass 0.0)  ;设置初始塔架重量为0
	(setq count 0);计数
	;(setq embedded_is_exit 0)
	(if (= (EmbeddedOrNot) T);如果是基础环
		(progn
			(setq count (+ count 1))
			(setq Index (strcat "\"" (rtos count) "\""));把两个字符串合并成一个字符并返回，返回 \"1\" ；rots四舍五入，
			(setq embedded_mass_str (strcat "\"" (rtos mass_of_embedded 2 0) "\""));返回重量
			(setq embeddedBom (strcat Index "	"
				Index "	"
				"\"出图\"" "	"
				"\"           \"" "	"
				"\"           \"" "	"
				"\"塔架底座总成\"" "	"
				"\"1\"" "	"
				"\"           \"" "	"
				embedded_mass_str "	"
				embedded_mass_str "	"
				"\"           \"" "	"
				"\"           \"" "	"
				"\"Tower embedded can assembly\"" "	"
				"\"           \"" "	"
				"\"           \"" "	"
				"\"自制零件\"" "	"
				"\"显示\"" "	"))
			(write-line embeddedBom BomListTxt)
			(print "基础环写入明细表成功！")
			(setq all_tower_mass (+ all_tower_mass mass_of_embedded))  ;累加重量
		)
	);基础环if的右括号，END基础环

  
  ;外爬梯bom;;;;;;;;
	(setq count (+ count 1))
	(setq Index (strcat "\"" (rtos count) "\""));基础环的序号，如果有基础环，count为1，如果没有count为0
	(if (= stair_mass 0)
		(setq stair_mass_str (strcat "\"请填写重量！\""))
		(setq stair_mass_str (strcat "\"" (rtos stair_mass) "\""))
	)


  
	(setq all_tower_mass (+ all_tower_mass stair_mass))  ;累加重量

	(setq StairBom (strcat Index "	"
			Index "	"
			"\"出图\"" "	"
			staircode "	";梯子物料号
			"\"           \"" "	"
			stairname "	"
			"\"1\"" "	"
			"\"           \"" "	"
			stair_mass_str "	"
			stair_mass_str "	"
			"\"           \"" "	"
			"\"           \"" "	"
			stairname_e "	"
			"\"           \"" "	"
			"\"           \"" "	"
			"\"自制零件\"" "	"
			"\"显示\"" "	"))
	(write-line StairBom BomListTxt)
	(print "外爬梯写入明细表成功！")

  
  ;升降机bom;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
	(setq count (+ count 1))
	(setq Index (strcat "\"" (rtos count) "\""))
	(setq LiftBom (strcat Index "	";*
			Index "	";序号
			"\"出图\"" "	";是否出图
			Liftcode "	";代号
			"\"           \"" "	";版本
			Liftname "	";名称
			"\"1\"" "	";数量
			"\"           \"" "	";材料
			"\"\"" "	";单重
			"\"\"" "	";总重
			"\"采购\"" "	";备注
			"\"           \"" "	";DRAWINGNUMBER
			Liftname_e "	";NAME
			"\"           \"" "	";MATERIAL
			"\"Purchasing\"" "	";NOTES
			"\"自制零件\"" "	";零件类型
			"\"显示\"" "	"));显示状态

	(write-line LiftBom BomListTxt)
	(print "升降机写入明细表成功！")
  
  ;塔架段bom
  ;SectionIndex (list "\"第一" "\"第二" "\"第三" "\"第四" "\"第五" "\"第六" "\"第七" "\"第八" "\"第九" "\"第十" "\"第十一" "\"第十二")
  ;sufWeldName "段塔筒焊合\""
  ;(setq sufSectionName "段塔筒附件总成\"")
  ;SectionIndexEN (list "Ⅰ\"" "Ⅱ\"" "Ⅲ\"" "Ⅳ\"" "Ⅴ\"" "Ⅵ\"" "Ⅶ\"" "Ⅷ\"" "Ⅸ\"" "Ⅹ\"" "Ⅺ\"" "Ⅻ\"")
	(setq i 0)
	(setq seccount 0)
	(while (< i (length SectionMassList) );SectinMassList
		(setq WeldName (strcat (nth i SectionIndex) sufWeldName);把表SectionIndex的第i个元素与sufWeldName合并
		WeldNameEN (strcat "\"Section " (nth i SectionIndexEN) "Welded\"")
		section_mass (+ (nth i SectionMassList) (nth i FlangeMassList) (nth (+ i 1) FlangeMassList))
		Index (strcat "\"" (rtos (+ i (+ count 1) seccount)) "\"")
		)
		(if (= i 0);如果是第一段
			(setq section_mass (+ section_mass MassDoorFrame (- MassOfTowerDoorHole) ))
		)
   
		(setq SectionNameEN (strcat "\"Section " (nth i SectionIndexEN) "Accessories\""))
		(setq SectionName (strcat (nth i SectionIndex) sufSectionName))
		(setq TowerSectionMass (strcat "\"" (rtos section_mass 2 0) "\""))
		(setq all_tower_mass (+ all_tower_mass section_mass))  ;累加重量
		(if (= i (- section_qty 1));如果是顶段
			(progn
				(setq WeldName "\"顶段塔筒焊合\""
					WeldNameEN "\"Top Section Welded\"")0
				(setq SectionName "\"顶段塔筒附件总成\""
					SectionNameEN "\"Top Section Accessories\"")
			)
		)

		(setq WeldBom (strcat Index "	"
			     Index "	"
			     "\"出图\"" "	"
			     "\"           \"" "	"
			     "\"           \"" "	"
			     WeldName "	"
			     "\"1\"" "	"
			     "\"           \"" "	"
			     TowerSectionMass "	"
			     TowerSectionMass "	"
			     "\"本图\"" "	"
			     "\"           \"" "	"
			     WeldNameEN "	"
			     "\"           \"" "	"
			     "\"In drawing\"" "	"
			     "\"自制零件\"" "	"
			     "\"显示\"" "	"))

		(write-line WeldBom BomListTxt)
		(setq secIndex (strcat "\"" (rtos (+ i (+ count 1)  seccount)) "\""))

		(setq SectionBom (strcat secIndex "	"
			     secIndex "	"
			     "\"出图\"" "	"
			     "\"           \"" "	"
			     "\"           \"" "	"
			     SectionName "	"
			     "\"1\"" "	"
			     "\"           \"" "	"
			     "\"\"" "	"
			     "\"\"" "	"
			     "\"\"" "	"
			     "\"           \"" "	"
			     SectionNameEN "	"
			     "\"           \"" "	"
			     "\"\"" "	"
			     "\"自制零件\"" "	"
			     "\"显示\"" "	"))
		(write-line SectionBom BomListTxt)
		(setq seccount (+ seccount 1))
		(setq i (+ 1 i))
		;(print i)
	)
	(print "塔筒段写入明细表成功！")





  ;电气附件总成bom;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
	(setq count (+ count 1))
	(setq Index (strcat "\"" (rtos count) "\""))
	(setq EIABom (strcat Index "	";*
			Index "	";序号
			"\"出图\"" "	";是否出图
			"\"           \"" "	";代号
			"\"           \"" "	";版本
			"\"塔筒电气安装总成\"" "	";名称
			"\"1\"" "	";数量
			"\"           \"" "	";材料
			"\"\"" "	";单重
			"\"\"" "	";总重
			"\"\"" "	";备注
			"\"           \"" "	";DRAWINGNUMBER
			"\"EIA of tower\"" "	";NAME
			"\"           \"" "	";MATERIAL
			"\"\"" "	";NOTES
			"\"自制零件\"" "	";零件类型
			"\"显示\"" "	"));显示状态
	(write-line EIABom BomListTxt)
	(print "电气总成写入明细表成功！")
	;每种机型特有的附件
	(cond
		( (= topfltype "21_TopFlange");21#工程顶法兰
			;阻尼器bom;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
			;对塔架净高度>125m的塔架，采用阻尼器
			(if (> (- (value retFlange section_qty 0) (value retFlange 0 0)) 125)
				(progn
				; 冷却液
					(setq count (+ count 1))
					(setq Index (strcat "\""(rtos count)"\""))
					(setq AttachBom (strcat Index "	";*
					   Index "	";序号
					   "\"出图\"" "	";是否出图
					   "\"60.10.71375\"" "	";代号
					   "\"           \"" "	";版本
					   "\"金风模块化液体阻尼器4X2\"" "	";名称
					   "\"4\"" "	";数量
					   "\"           \"" "	";材料
					   "\"\"" "	";单重
					   "\"\"" "	";总重
					   "\"采购\"" "	";备注
					   "\"           \"" "	";DRAWINGNUMBER
					   "\"GW MTLD4X2\"" "	";NAME
					   "\"           \"" "	";MATERIAL
					   "\"Purchasing\"" "	";NOTES
					   "\"自制零件\"" "	";零件类型
					   "\"显示\"" "	"));显示状态
					(write-line AttachBom BomListTxt)
						; 液体阻尼器
				);End progn
			);End if
		  ;梯子支耳(右)bom
			(setq count (+ count 1))
			(setq Index (strcat "\"" (rtos count) "\""))
			(setq LugrightBom (strcat Index "	";*
				Index "	";序号
				"\"出图\"" "	";是否出图
				"\"60.03.01002\"" "	";代号
				"\"           \"" "	";版本
				"\"2.5MW入口梯子支耳（右）\"" "	";名称
				"\"1\"" "	";数量
				"\"           \"" "	";材料
				"\"1.79\"" "	";单重
				"\"1.79\"" "	";总重
				"\"           \"" "	";备注
				"\"           \"" "	";DRAWINGNUMBER
				"\"Entrance stairs lug(Right)\"" "	";NAME
				"\"           \"" "	";MATERIAL
				"\"           \"" "	";NOTES
				"\"自制零件\"" "	";零件类型
				"\"显示\"" "	"));显示状态
			(write-line LugrightBom BomListTxt)
			;梯子支耳(左)bom
			(setq count (+ count 1))
			(setq Index (strcat "\"" (rtos count) "\""))
			(setq LugleftBom (strcat Index "	";*
				Index "	";序号
				"\"出图\"" "	";是否出图
				"\"60.03.01021\"" "	";代号
				"\"           \"" "	";版本
				"\"2.5MW入口梯子支耳（左）\"" "	";名称
				"\"1\"" "	";数量
				"\"           \"" "	";材料
				"\"1.79\"" "	";单重
				"\"1.79\"" "	";总重
				"\"           \"" "	";备注
				"\"           \"" "	";DRAWINGNUMBER
				"\"Entrance stairs lug(Left)\"" "	";NAME
				"\"           \"" "	";MATERIAL
				"\"           \"" "	";NOTES
				"\"自制零件\"" "	";零件类型
				"\"显示\"" "	"));显示状态
			(write-line LugleftBom BomListTxt)
		 
		);END "21_TopFlange"
    
		( (= topfltype "2.xMW_TopFlange");2.XMWS顶法兰
		;堵头bom
			(setq count (+ count 1))
			(setq Index (strcat "\"" (rtos count) "\""))
			(setq PLUGBom (strcat Index "	";*
			Index "	";序号
			"\"出图\"" "	";是否出图
			"\"60.10.74275\"" "	";代号
			"\"           \"" "	";版本
			"\"堵头\"" "	";名称
			"\"18\"" "	";数量
			"\"ASA\"" "	";材料
			"\"\"" "	";单重
			"\"\"" "	";总重
			"\"           \"" "	";备注
			"\"           \"" "	";DRAWINGNUMBER
			"\"Plug\"" "	";NAME
			"\"ASA\"" "	";MATERIAL
			"\"           \"" "	";NOTES
			"\"自制零件\"" "	";零件类型
			"\"显示\"" "	"));显示状态
			(write-line PLUGBom BomListTxt)
			;阻尼器bom;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
			;对塔架净高度>125m的塔架，采用阻尼器，明细表中增加两行
			(if (> (- (value retFlange section_qty 0) (value retFlange 0 0)) 125)
				(progn
				; 冷却液
					(setq count (+ count 1))
					(setq Index (strcat "\""(rtos count)"\""))
					(setq AttachBom (strcat Index "	";*
			       Index "	";序号
			       "\"出图\"" "	";是否出图
			       "\"6.0200.1761\"" "	";代号
			       "\"           \"" "	";版本
			       "\"塔架液体阻尼器阻尼液（塑料桶装）\"" "	";名称
			       "\"27\"" "	";数量
			       "\"           \"" "	";材料
			       "\"\"" "	";单重
			       "\"\"" "	";总重
			       "\"采购\"" "	";备注
			       "\"           \"" "	";DRAWINGNUMBER
			       "\"Special liquid for TLD\"" "	";NAME
			       "\"           \"" "	";MATERIAL
			       "\"Purchasing\"" "	";NOTES
			       "\"自制零件\"" "	";零件类型
			       "\"显示\"" "	"));显示状态
					(write-line AttachBom BomListTxt)
					; 液体阻尼器
					(setq count (+ count 1))
					(setq Index(strcat "\""(rtos count)"\""))
					(setq AttachBom (strcat Index "	";*
			       Index "	";序号
			       "\"出图\"" "	";是否出图
			       "\"C60.11.02651\"" "	";代号
			       "\"           \"" "	";版本
			       "\"塔架中段钢制液体阻尼器\"" "	";名称
			       "\"5\"" "	";数量
			       "\"           \"" "	";材料
			       "\"\"" "	";单重
			       "\"\"" "	";总重
			       "\"采购\"" "	";备注
			       "\"           \"" "	";DRAWINGNUMBER
			       "\"Mid-tower liquid damper(Steel)\"" "	";NAME
			       "\"           \"" "	";MATERIAL
			       "\"Purchasing\"" "	";NOTES
			       "\"自制零件\"" "	";零件类型
			       "\"显示\"" "	");显示状态
					)
					(write-line AttachBom BomListTxt)
				);End progn
			);End if
			;堵头bom
			(setq count (+ count 1))
			(setq Index (strcat "\"" (rtos count) "\""))
			(setq LadderBom (strcat Index "	";*
			Index "	";序号
			"\"出图\"" "	";是否出图
			"\"60.11.00985\"" "	";代号
			"\"           \"" "	";版本
			"\"入口梯子耳板焊合\"" "	";名称
			"\"2\"" "	";数量
			"\"           \"" "	";材料
			"\"\"" "	";单重
			"\"\"" "	";总重
			"\"           \"" "	";备注
			"\"           \"" "	";DRAWINGNUMBER
			"\"Entrance ladder lug welded\"" "	";NAME
			"\"           \"" "	";MATERIAL
			"\"           \"" "	";NOTES
			"\"自制零件\"" "	";零件类型
			"\"显示\"" "	"));显示状态
			(write-line LadderBom BomListTxt)
			;内螺纹圆柱bom
			(setq count (+ count 1))
			(setq Index (strcat "\"" (rtos count) "\""))
			(setq BushesBom (strcat Index "	";*
			Index "	";序号
			"\"出图\"" "	";是否出图
			"\"60.10.71539\"" "	";代号
			"\"           \"" "	";版本
			"\"内螺纹圆柱φ30x35 M12\"" "	";名称
			"\"8\"" "	";数量
			"\"圆钢φ30/Q355C/D/NE\"" "	";材料
			"\"\"" "	";单重
			"\"\"" "	";总重
			"\"           \"" "	";备注
			"\"           \"" "	";DRAWINGNUMBER
			"\"Threaded bushes φ30x35 M12\"" "	";NAME
			"\"Steel rod φ30/Q355/C/D/NE\"" "	";MATERIAL
			"\"           \"" "	";NOTES
			"\"自制零件\"" "	";零件类型
			"\"显示\"" "	"));显示状态
			(write-line BushesBom BomListTxt)
			;橡胶垫片bom
			(setq count (+ count 1))
			(setq Index (strcat "\"" (rtos count) "\""))
			(setq RubberBom (strcat Index "	";*
			Index "	";序号
			"\"出图\"" "	";是否出图
			"\"60.10.73485\"" "	";代号
			"\"           \"" "	";版本
			"\"橡胶垫片\"" "	";名称
			"\"8\"" "	";数量
			"\"EPDM\"" "	";材料
			"\"\"" "	";单重
			"\"\"" "	";总重
			"\"           \"" "	";备注
			"\"           \"" "	";DRAWINGNUMBER
			"\"Rubber washer\"" "	";NAME
			"\"EPDM\"" "	";MATERIAL
			"\"           \"" "	";NOTES
			"\"自制零件\"" "	";零件类型
			"\"显示\"" "	"));显示状态
			(write-line RubberBom BomListTxt)
			;小螺栓bom
			(setq count (+ count 1))
			(setq Index (strcat "\"" (rtos count) "\""))
			(setq BoltM12Bom (strcat Index "	";*
			Index "	";序号
			"\"出图\"" "	";是否出图
			"\"4.0100.0580\"" "	";代号
			"\"           \"" "	";版本
			"\"螺栓ISO 4017-M12x35-8.8\"" "	";名称
			"\"8\"" "	";数量
			"\"\"" "	";材料
			"\"\"" "	";单重
			"\"\"" "	";总重
			"\"热浸镀锌\"" "	";备注
			"\"           \"" "	";DRAWINGNUMBER
			"\"Bolt ISO 4017-M12x35-8.8\"" "	";NAME
			"\"\"" "	";MATERIAL
			"\"Hot-dip Zinc galvanized\"" "	";NOTES
			"\"自制零件\"" "	";零件类型
			"\"显示\"" "	"));显示状态
			(write-line BoltM12Bom BomListTxt)
       
		);END 2.XMWS顶法兰
    
		( (= TopFlangeName "other_TopFlange") 
		);其他法兰
    
	);cond


  
  ;螺栓，螺母，垫片bom;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  (setq ibolt count) ;第一个螺栓的序号
  (SetBoltNum2 retFlange section_qty);统计螺栓、螺母、垫圈数量
  ;(print BoltLengthTypeList)
  ;(print NutTypeList)
  ;(print WasherTypeList)
  (setq BoltLengthTypeList (cdr BoltLengthTypeList));去掉第一个元素
  (setq NutTypeList (cdr NutTypeList))
  (setq WasherTypeList (cdr WasherTypeList))
  ;(print BoltLengthTypeList)
  ;(print NutTypeList)
  ;(print WasherTypeList)
  (setq count (+ count (length BoltLengthTypeList) (length NutTypeList) (length WasherTypeList)));螺栓，螺母，垫片总的数量
  ;处理螺栓螺母垫片表,给公用螺栓的加空列表
  (setq nn 1)
  (while (< nn (length BoltLengthTypeList))
    (setq bolt_m_1 (substr (nth 0 (nth nn BoltLengthTypeList)) 1 3 ))
    (if (= (nth nn NutTypeList) nil);如果为空
      (progn
        (setq NutTypeList (list_insert NutTypeList nn '("" "")))
        (setq WasherTypeList (list_insert WasherTypeList nn '("" "")))
      )
      (progn
        (setq nut_m_1  (substr (nth 0 (nth nn NutTypeList)) 1 3 ))
        (if (/= bolt_m_1 nut_m_1)
          (progn
            (setq NutTypeList (list_insert NutTypeList nn '("" "")))
            (setq WasherTypeList (list_insert WasherTypeList nn '("" "")))
          );End progn
        );End if
      );End progn
    );End if
    (setq nn (+ nn 1))
  )
  ;(print BoltLengthTypeList)
  ;(print NutTypeList)
  ;(print WasherTypeList)

  (setq k 0)
  (while(< k (length BoltLengthTypeList))
    (setq bolt_m_1  (substr (nth 0 (nth k BoltLengthTypeList)) 1 3 ))
    (if (= (nth k NutTypeList) nil)
      (setq nut_m_1 (substr (nth 0 (nth k BoltLengthTypeList)) 1 3 ))
      (setq nut_m_1  (substr (nth 0 (nth k NutTypeList)) 1 3 ))
    );end if
    
    ;(print bolt_m_1 )
    ;(print nut_m_1 )

    (if (= bolt_m_1 nut_m_1 )
      (progn;如果列表中螺栓和垫片匹配
        (setq Index (rtos ibolt)
	  BoltIndex  k
	  BoltLengthTypeName (strcat preBoltName (nth 0 (nth BoltIndex BoltLengthTypeList)))
	  BoltLengthTypeNameEN (strcat preBoltNameEN (nth 0 (nth BoltIndex BoltLengthTypeList)))
	  BoltLengthTypeNum (rtos (nth 1 (nth BoltIndex BoltLengthTypeList)) 2 0)
	  BoltPLM (search_bolt_PLM (nth 0 (nth BoltIndex BoltLengthTypeList))))
    
        ;螺栓明细表
        (setq bolt_zb (list (* 806 mscale) (+ (* 50 mscale) (* (atoi Index) 13 mscale)) 0))
	;螺栓明细表
        (setq Index (strcat "\""(rtos ibolt)"\"")
	  BoltIndex k
	  BoltLengthTypeName(strcat preBoltName(nth 0(nth BoltIndex BoltLengthTypeList))"\"")
	  BoltLengthTypeNameEN(strcat preBoltNameEN(nth 0(nth BoltIndex BoltLengthTypeList))"\"")
	  BoltLengthTypeNum(strcat "\""(rtos(nth 1(nth BoltIndex BoltLengthTypeList))2 0)"\"")
	  BoltPLM(strcat "\""(search_bolt_PLM (nth 0(nth BoltIndex BoltLengthTypeList)))"\"")
	  BoltLengthTypeBom(strcat Index "	";*
				   Index "	";序号
				   "\"出图\"" "	";是否出图
				   BoltPLM "	";代号
				   "\"           \"" "	";版本
				   BoltLengthTypeName "	";名称
				   BoltLengthTypeNum "	";数量
				   "\"           \"" "	";材料
				   "\"\"" "	";单重
				   "\"\"" "	";总重
				   Anticorrosion "	";备注
				   "\"           \"" "	";DRAWINGNUMBER
				   BoltLengthTypeNameEN "	";NAME
				   "\"           \"" "	";MATERIAL
				   AnticorrosionEN "	";NOTES
				   "\"自制零件\"" "	";零件类型
				   "\"显示\"" "	");显示状态
	)
        (write-line BoltLengthTypeBom BomListTxt)
        ;插入块的方式插入明细表
	;(zq_block_insert bolt_zb Index BoltPLM BoltLengthTypeName BoltLengthTypeNameEN BoltLengthTypeNum Anticorrosion AnticorrosionEN "" "" 0.44 0.62)
	
        ;螺母
        (setq iNut (+ ibolt 1))
        (setq EndNoBolt k)
        (setq Index (rtos iNut)
	  NutIndex k
	  NutTypeName(strcat preNutName (nth 0 (nth NutIndex NutTypeList)))
	  NutTypeNameEN(strcat preNutNameEN (nth 0 (nth NutIndex NutTypeList)) )
	  NutPLM (search_nut_PLM (nth 0 (nth NutIndex NutTypeList)) )
	  NutTypeNum (rtos (nth 1 (nth NutIndex NutTypeList)) 2 0))
        ;螺母明细表
        (setq nut_zb (list (* 806 mscale) (+ (* 50 mscale) (* (atoi Index) 13 mscale)) 0))

        (setq Index(strcat "\""(rtos iNut)"\"")
	  NutIndex k
	  NutTypeName(strcat preNutName(nth 0(nth NutIndex NutTypeList))"\"")
	  NutTypeNameEN(strcat preNutNameEN(nth 0(nth NutIndex NutTypeList))"\"")
	  NutPLM(strcat "\""(search_nut_PLM (nth 0(nth NutIndex NutTypeList)))"\"")
	  NutTypeNum(strcat "\""(rtos(nth 1(nth NutIndex NutTypeList))2 0)"\"")
	  NutTypeBom(strcat Index "	";*
			    Index "	";序号
			    "\"出图\"" "	";是否出图
			    NutPLM "	";代号
			    "\"           \"" "	";版本
			    NutTypeName "	";名称
			    NutTypeNum "	";数量
			    "\"           \"" "	";材料
			    "\"\"" "	";单重
			    "\"\"" "	";总重
			    Anticorrosion "	";备注
			    "\"           \"" "	";DRAWINGNUMBER
			    NutTypeNameEN "	";NAME
			    "\"           \"" "	";MATERIAL
			    AnticorrosionEN "	";NOTES
			    "\"自制零件\"" "	";零件类型
			    "\"显示\"" "	");显示状态
	)
        (write-line NutTypeBom BomListTxt)

	

        ;(zq_block_insert nut_zb Index NutPLM NutTypeName NutTypeNameEN NutTypeNum Anticorrosion AnticorrosionEN "" "" 0.44 0.89)
	
        ;垫圈
        (setq iWasher (+ iNut 1))
        (setq EndNoNut k)
        (setq Index (rtos iWasher)
	  WasherIndex k
	  WasherTypeName (strcat preWasherName (nth 0 (nth WasherIndex WasherTypeList)))
	  WasherTypeNameEN (strcat preWasherNameEN (nth 0 (nth WasherIndex WasherTypeList)))
	  WasherPLM (search_washer_PLM (nth 0(nth WasherIndex WasherTypeList)))
	  WasherTypeNum (rtos(nth 1 (nth WasherIndex WasherTypeList)) 2 0))
        (setq washer_zb (list (* 806 mscale) (+ (* 50 mscale) (* (atoi Index) 13 mscale)) 0))


        (setq Index(strcat "\""(rtos iWasher)"\"")
	  WasherIndex k
	  WasherTypeName(strcat preWasherName(nth 0(nth WasherIndex WasherTypeList))"\"")
	  WasherTypeNameEN(strcat preWasherNameEN(nth 0(nth WasherIndex WasherTypeList))"\"")
	  WasherPLM(strcat "\""(search_washer_PLM (nth 0(nth WasherIndex WasherTypeList)))"\"")
	  WasherTypeNum(strcat "\""(rtos(nth 1(nth WasherIndex WasherTypeList))2 0)"\"")
	  WasherTypeBom(strcat Index "	";*
			       Index "	";序号
			       "\"出图\"" "	";是否出图
			       WasherPLM "	";代号
			       "\"           \"" "	";版本
			       WasherTypeName "	";名称
			       WasherTypeNum "	";数量
			       "\"           \"" "	";材料
			       "\"\"" "	";单重
			       "\"\"" "	";总重
			       Anticorrosion "	";备注
			       "\"           \"" "	";DRAWINGNUMBER
			       WasherTypeNameEN "	";NAME
			       "\"           \"" "	";MATERIAL
			       AnticorrosionEN "	";NOTES
			       "\"自制零件\"" "	";零件类型
			       "\"显示\"" "	");显示状态
        )
        (write-line WasherTypeBom BomListTxt)
	

        ;(zq_block_insert washer_zb Index WasherPLM WasherTypeName WasherTypeNameEN WasherTypeNum Anticorrosion AnticorrosionEN "" "" 0.44 0.62)
	
        (setq ibolt (+ 3 ibolt))
      );End progn
      (progn;如果列表中螺栓和垫片不匹配
        (setq Index (rtos ibolt)
	  BoltIndex  k
	  BoltLengthTypeName (strcat preBoltName (nth 0 (nth BoltIndex BoltLengthTypeList)))
	  BoltLengthTypeNameEN (strcat preBoltNameEN (nth 0 (nth BoltIndex BoltLengthTypeList)))
	  BoltLengthTypeNum (rtos (nth 1 (nth BoltIndex BoltLengthTypeList)) 2 0)
	  BoltPLM (search_bolt_PLM (nth 0 (nth BoltIndex BoltLengthTypeList))))
        ;螺栓明细表
        (setq bolt_zb (list (* 806 mscale) (+ (* 50 mscale) (* (atoi Index) 13 mscale)) 0))
	;(zq_block_insert bolt_zb Index BoltPLM BoltLengthTypeName BoltLengthTypeNameEN BoltLengthTypeNum Anticorrosion AnticorrosionEN "" "" 0.44 0.62)

	;螺栓明细表;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
        (setq Index (strcat "\""(rtos ibolt)"\"")
	  BoltIndex k
	  BoltLengthTypeName(strcat preBoltName(nth 0(nth BoltIndex BoltLengthTypeList))"\"")
	  BoltLengthTypeNameEN(strcat preBoltNameEN(nth 0(nth BoltIndex BoltLengthTypeList))"\"")
	  BoltLengthTypeNum(strcat "\""(rtos(nth 1(nth BoltIndex BoltLengthTypeList))2 0)"\"")
	  BoltPLM(strcat "\""(search_bolt_PLM (nth 0(nth BoltIndex BoltLengthTypeList)))"\"")
	  BoltLengthTypeBom(strcat Index "	";*
				   Index "	";序号
				   "\"出图\"" "	";是否出图
				   BoltPLM "	";代号
				   "\"           \"" "	";版本
				   BoltLengthTypeName "	";名称
				   BoltLengthTypeNum "	";数量
				   "\"           \"" "	";材料
				   "\"\"" "	";单重
				   "\"\"" "	";总重
				   Anticorrosion "	";备注
				   "\"           \"" "	";DRAWINGNUMBER
				   BoltLengthTypeNameEN "	";NAME
				   "\"           \"" "	";MATERIAL
				   AnticorrosionEN "	";NOTES
				   "\"自制零件\"" "	";零件类型
				   "\"显示\"" "	");显示状态
	)
        (write-line BoltLengthTypeBom BomListTxt)
        ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
	
        (setq ibolt (+ 1 ibolt))
      );End progn
    );End if
    (setq k (+ k 1))
  );End while

  (print "螺栓螺母垫片写入明细表成功！")

  
  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  (cond
    ( (= topfltype "21_TopFlange");21#工程顶法兰
       ;第一段塔筒焊合通用图bom
      (setq count (+ count 1))
      (setq Index (strcat "\"" (rtos count) "\""))
     (print Index)
     (print count)
     
     
      (setq GeneraloneBom (strcat Index "	";*
			          Index "	";序号
                                  "\"出图\"" "	";是否出图
				  "\"           \"" "	";代号
				  "\"           \"" "	";版本
				  "\"第一段塔筒焊合通用图\"" "	";名称
				  "\"1\"" "	";数量
				  "\"\"" "	";材料
				  "\"\"" "	";单重
				  "\"\"" "	";总重
				  "\"           \"" "	";备注
				  "\"           \"" "	";DRAWINGNUMBER
				  "\"General drawing of Section Ⅰ Welded\"" "	";NAME
				  "\"\"" "	";MATERIAL
				  "\"           \"" "	";NOTES
				  "\"自制零件\"" "	";零件类型
				  "\"显示\"" "	")
      );显示状态
     
      (write-line GeneraloneBom BomListTxt)

   
      ;中间段和顶段焊合通用图bom
      (setq count (+ count 1))
      (setq Index (strcat "\"" (rtos count) "\""))
      (setq MiddletopBom (strcat Index "	";*
			Index "	";序号
			"\"出图\"" "	";是否出图
			"\"60.11.00985\"" "	";代号
			"\"           \"" "	";版本
			"\"中间段和顶段焊合通用图\"" "	";名称
			"\"1\"" "	";数量
			"\"           \"" "	";材料
			"\"\"" "	";单重
			"\"\"" "	";总重
			"\"           \"" "	";备注
			"\"           \"" "	";DRAWINGNUMBER
			"\"Middle and Top section welded\"" "	";NAME
			"\"           \"" "	";MATERIAL
			"\"           \"" "	";NOTES
			"\"自制零件\"" "	";零件类型
			"\"显示\"" "	"));显示状态
      (write-line MiddletopBom BomListTxt)
      ;法兰通用图bom

     
     
      (setq count (+ count 1))
      (setq Index (strcat "\"" (rtos count) "\""))
      (setq GeneralflBom (strcat Index "	";*
			Index "	";序号
			"\"出图\"" "	";是否出图
			"\"60.10.74180\"" "	";代号
			"\"           \"" "	";版本
			"\"法兰通用图\"" "	";名称
			"\"1\"" "	";数量
			"\"           \"" "	";材料
			"\"\"" "	";单重
			"\"\"" "	";总重
			"\"           \"" "	";备注
			"\"           \"" "	";DRAWINGNUMBER
			"\"General drawing of Flange\"" "	";NAME
			"\"           \"" "	";MATERIAL
			"\"           \"" "	";NOTES
			"\"自制零件\"" "	";零件类型
			"\"显示\"" "	"));显示状态
      (write-line GeneralflBom BomListTxt)
     
      (setq flange_type (value retFlange 1 11));法兰类型
     
      (setq flange_type (FlangeTypeJudge flange_type 1));判断法兰的类型
     
      (if (= flange_type "T")
        (progn
          ;第一段塔筒焊合通用图bom
          (setq count (+ count 1))
          (setq Index (strcat "\"" (rtos count) "\""))
          (setq BoltprotectBom (strcat Index "   ";*
			Index "	";序号
			"\"出图\"" "	";是否出图
			"\"           \"" "	";代号
			"\"           \"" "	";版本
			"\"螺栓保护套\"" "	";名称
			"\"1\"" "	" ;数量
			"\"          \"" " " ;材料
			"\"          \"" " " ;单重
			"\"\"" "	";总重
			"\"           \"" "	";备注
			"\"           \"" "	";DRAWINGNUMBER
			"\"Bolt protector\"" "	";NAME
			"\"\"" "	";MATERIAL
			"\"           \"" "	";NOTES
			"\"自制零件\"" "	";零件类型
			"\"显示\"" "	")) ;显示状态
      (write-line BoltprotectBom BomListTxt)
      
      ;中间段和顶段焊合通用图bom
      (setq count (+ count 1))
      (setq Index (strcat "\"" (rtos count) "\""))
      (setq ExternalplatBom (strcat Index "	";*
			Index "	";序号
			"\"出图\"" "	";是否出图
			"\"\"" "	";代号
			"\"           \"" "	";版本
			"\"外平台\"" "	";名称
			"\"1\"" "	";数量
			"\"           \"" "	";材料
			"\"\"" "	";单重
			"\"\"" "	";总重
			"\"           \"" "	";备注
			"\"           \"" "	";DRAWINGNUMBER
			"\"External platform\"" "	";NAME
			"\"           \"" "	";MATERIAL
			"\"           \"" "	";NOTES
			"\"自制零件\"" "	";零件类型
			"\"显示\"" "	"));显示状态
      (write-line ExternalplatBom BomListTxt)
	  
        );end progn
      );end if
     
     
    );END "21_TopFlange"
    
    ( (= topfltype "2.xMW_TopFlange");2.XMWS顶法兰
      ;第一段塔筒焊合通用图bom
      (setq count (+ count 1))
      (setq Index (strcat "\"" (rtos count) "\""))
      (setq GeneraloneBom (strcat Index "	";*
			Index "	";序号
			"\"出图\"" "	";是否出图
			"\"60.10.73614\"" "	";代号
			"\"           \"" "	";版本
			"\"第一段塔筒焊合通用图\"" "	";名称
			"\"1\"" "	";数量
			"\"           \"" "	";材料
			"\"\"" "	";单重
			"\"\"" "	";总重
			"\"           \"" "	";备注
			"\"           \"" "	";DRAWINGNUMBER
			"\"General drawing of Section Ⅰ Welded\"" "	";NAME
			"\"\"" "	";MATERIAL
			"\"           \"" "	";NOTES
			"\"自制零件\"" "	";零件类型
			"\"显示\"" "	"));显示状态
      (write-line GeneraloneBom BomListTxt)
      (print "第一段塔筒焊合通用图明细表成功！")
      ;中间段和顶段焊合通用图bom
      (setq count (+ count 1))
      (setq Index (strcat "\"" (rtos count) "\""))
      (setq MiddletopBom (strcat Index "	";*
			Index "	";序号
			"\"出图\"" "	";是否出图
			"\"60.10.73612\"" "	";代号
			"\"           \"" "	";版本
			"\"中间段和顶段焊合通用图\"" "	";名称
			"\"1\"" "	";数量
			"\"           \"" "	";材料
			"\"\"" "	";单重
			"\"\"" "	";总重
			"\"           \"" "	";备注
			"\"           \"" "	";DRAWINGNUMBER
			"\"Middle and Top section welded\"" "	";NAME
			"\"           \"" "	";MATERIAL
			"\"           \"" "	";NOTES
			"\"自制零件\"" "	";零件类型
			"\"显示\"" "	"));显示状态
      (write-line MiddletopBom BomListTxt)
      (print "中间段和顶段塔筒焊合通用图明细表成功！")
      ;法兰通用图bom
      (setq count (+ count 1))
      (setq Index (strcat "\"" (rtos count) "\""))
      (setq GeneralflBom (strcat Index "	";*
			Index "	";序号
			"\"出图\"" "	";是否出图
			"\"60.10.73613\"" "	";代号
			"\"           \"" "	";版本
			"\"法兰通用图\"" "	";名称
			"\"1\"" "	";数量
			"\"           \"" "	";材料
			"\"\"" "	";单重
			"\"\"" "	";总重
			"\"           \"" "	";备注
			"\"           \"" "	";DRAWINGNUMBER
			"\"General drawing of Flange\"" "	";NAME
			"\"           \"" "	";MATERIAL
			"\"           \"" "	";NOTES
			"\"自制零件\"" "	";零件类型
			"\"显示\"" "	"));显示状态
      (write-line GeneralflBom BomListTxt)
      (print "法兰通用图明细表成功！")
      ;灭火器支架bom
      (setq count (+ count 1))
      (setq Index (strcat "\"" (rtos count) "\""))
      (setq FireBom (strcat Index "	";*
			Index "	";序号
			"\"出图\"" "	";是否出图
			"\"60.10.67021\"" "	";代号
			"\"           \"" "	";版本
			"\"平台灭火器支架安装方案\"" "	";名称
			"\"1\"" "	";数量
			"\"\"" "	";材料
			"\"\"" "	";单重
			"\"\"" "	";总重
			"\"           \"" "	";备注
			"\"           \"" "	";DRAWINGNUMBER
			"\"Installation plan of fire extinguisher\"" "	";NAME
			"\"           \"" "	";MATERIAL
			"\"           \"" "	";NOTES
			"\"自制零件\"" "	";零件类型
			"\"显示\"" "	"));显示状态
      (write-line FireBom BomListTxt)
      (print "灭火器支架明细表成功！")
       
    );END 2.XMWS顶法兰
    
    ( (= TopFlangeName "other_TopFlange") 
    );其他法兰
    
  );cond


  
  (close BomListTxt)
  (print "明细表成功生成！")
  (prin1)
)
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;boomlist文件;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;*********************************明细表文本导出***********************/
(defun BomList(TowerExcelFile SectionMassList FlangeMassList stair_mass ConcreteStairsMass Sum_FLange MassDoorFrame MassOfTowerDoorHole
	       / i preBomName BomTitle BomListTxt SectionName BoltLengthTypeBom NutTypeBom WasherTypeBom FastenerListList 
		   towerheight NameList NameList_en AccessoryName AccessoryName_en AccessoryMass AccessoryMass_steel AccessoryMass_Al revit_num vertical_fla_mass)
	(setq Bom_insert_point (list (* 806 mscale) (* 50 mscale) 0))	  ;名细表插入点
	;(if (/= is_alert T)
      ;(command "insert" "one_key_mxb_title" "S" mscale Bom_insert_point 0 "") ;插入明细表表头
	;)
	(setq preBomName (GetDir TowerExcelFile));明细表的名称
	;明细表表头写入
	(setq BomTitle (strcat "\"*\"" "	"
			 "\"序号\"" "	"
			 "\"是否出图\"" "	"
			 "\"代号\"" "	"
			 "\"版本\"" "	"
			 "\"名称\"" "	"
			 "\"数量\"" "	"
			 "\"材料\"" "	"
			 "\"单重\"" "	"
			 "\"总重\"" "	"
			 "\"备注\"" "	"
			 "\"DRAWINGNUMBER\"" "	"
			 "\"NAME\"" "	"
			 "\"MATERIAL\"" "	"
			 "\"NOTES\"" "	"
			 "\"零件类型\"" "	"
			 "\"显示状态\"" "	")
		SectionIndex (list "\"第一" "\"第二" "\"第三" "\"第四" "\"第五" "\"第六" "\"第七" "\"第八" "\"第九" "\"第十" "\"第十一" "\"第十二")
		SectionIndexEN (list "Ⅰ\"" "Ⅱ\"" "Ⅲ\"" "Ⅳ\"" "Ⅴ\"" "Ⅵ\"" "Ⅶ\"" "Ⅷ\"" "Ⅸ\"" "Ⅹ\"" "Ⅺ\"" "Ⅻ\""))
	(setq Anticorrosion "\"达克罗\""
		AnticorrosionEN "\"Dacromet\""
		preBoltName "\"螺栓GB/T5782-"
		preBoltNameEN "\"Bolt ISO4014-"
		preBoltName_m72 "\"螺栓DAST 021-"
		preBoltNameEN_m72 "\"Bolt DAST 021-"
		preNutName "\"螺母GB/T6170-"
		preNutNameEN "\"Nut ISO4032-"
		preNutName_m72 "\"螺母DAST 021-"
		preNutNameEN_m72 "\"Nut DAST 021-"
		preWasherName "\"垫圈EN14399-6-"
		preWasherNameEN "\"Washer EN14399-6-")
	;(if zongtu;判断是不是出总成图
    ;(setq sufSectionName "段塔筒附件总成\"")
    ;(setq sufSectionName "段塔筒焊合\"")
	;)
	(setq sufSectionName "段塔筒焊合\"")
		;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  
	(setq BomListTxt (open (strcat preBomName "txt") "w"))
	(write-line BomTitle BomListTxt);把BomTitle写到BomListTxt
	(setq all_tower_mass 0.0)  ;设置初始塔架重量为0
	;基础环/塔架底座  
	(setq embedded_is_exit 0)
	(if (= (EmbeddedOrNot) T);如果是基础环
		(progn
			(setq embedded_is_exit 1)
			(setq Index (strcat "\"" (rtos embedded_is_exit) "\""));把两个字符串合并成一个字符并返回，返回 \"1\" ；rots四舍五入，
			(setq embedded_mass_str (strcat "\"" (rtos mass_of_embedded 2 0) "\""));返回重量
			(setq embeddedBom (strcat Index "	"
				Index "	"
				"\"出图\"" "	"
				"\"           \"" "	"
				"\"           \"" "	"
				"\"塔架底座\"" "	"
				"\"1\"" "	"
				"\"           \"" "	"
				embedded_mass_str "	"
				embedded_mass_str "	"
				"\"           \"" "	"
				"\"           \"" "	"
				"\"Embedded Steel Can\"" "	"
				"\"           \"" "	"
				"\"           \"" "	"
				"\"自制零件\"" "	"
				"\"显示\"" "	"))
			(write-line embeddedBom BomListTxt)
      
			;(if (/= is_alert T)
			;  (command "insert" "tower_mxb" "S" mscale (polar Bom_insert_point (/ pi 2) (* 13 mscale)) 0
			;      "" "" (rtos embedded_is_exit) "塔架底座" "1" " " (rtos mass_of_embedded 2 1) (rtos mass_of_embedded 2 1) "" "Embedded Steel Can" " " " " "") ;插入基础环明细表
			;)
			(setq all_tower_mass (+ all_tower_mass mass_of_embedded))  ;累加重量
		)
	);基础环if的右括号，END基础环
	;外爬梯bom;;;;;;;;
	(if (/= TheDoorType "ConcDoor")
		(progn ;非混塔
			(setq xIndex (+ embedded_is_exit 1));基础环的序号，如果有基础环，embedded_is_exit为1，如果没有embedded_is_exit为0,非混塔有外爬梯，序号+1
			(setq Index (strcat "\"" (rtos xIndex) "\""))
			(if (= stair_mass 0)
				(setq stair_mass_str (strcat "\"请填写重量！\""))
				(setq stair_mass_str (strcat "\"" (rtos stair_mass) "\""))
			)
			(setq all_tower_mass (+ all_tower_mass stair_mass))  ;累加重量
			(setq StairBom(strcat Index "	"
				Index "	"
				"\"出图\"" "	"
				"\"           \"" "	"
				"\"           \"" "	"
				"\"外爬梯总成\"" "	"
				"\"1\"" "	"
				"\"           \"" "	"
				stair_mass_str "	"
				stair_mass_str "	"
				"\"           \"" "	"
				"\"           \"" "	"
				"\"Entrance stair assembly\"" "	"
				"\"           \"" "	"
				"\"           \"" "	"
				"\"自制零件\"" "	"
				"\"显示\"" "	"))
			(write-line StairBom BomListTxt)
		);progn
		(progn ;混塔
			(if (= AntiFloodType "Yes")
				(progn 
					(setq xIndex (+ embedded_is_exit 1));混塔有防洪需求，有外爬梯，序号+1
					(setq Index (strcat "\"" (rtos xIndex) "\""))
					(setq stair_mass_str (strcat "\"" (rtos (nth 3 ConcreteStairsMass)) "\""))
					(setq all_tower_mass (+ all_tower_mass (nth 3 ConcreteStairsMass)))  ;累加重量
					(setq StairBom(strcat Index "	"
						Index "	"
						"\"出图\"" "	"
						"\"           \"" "	"
						"\"           \"" "	"
						"\"外爬梯总成\"" "	"
						"\"1\"" "	"
						"\"           \"" "	"
						stair_mass_str "	"
						stair_mass_str "	"
						"\"           \"" "	"
						"\"           \"" "	"
						"\"Entrance stair assembly\"" "	"
						"\"           \"" "	"
						"\"           \"" "	"
						"\"自制零件\"" "	"
						"\"显示\"" "	"))
					(write-line StairBom BomListTxt)
				);progn
				(setq xIndex (+ embedded_is_exit 0));混塔无防洪需求，无外爬梯，序号+0
			);end if
		);progn
	);end if 
	;(if (/= is_alert T)
		;(command "insert" "tower_mxb" "S" mscale (polar Bom_insert_point(/ pi 2) (* 13 mscale (+ embedded_is_exit 1))) 0
	      ;"" "" (rtos (+ embedded_is_exit 1)) "外爬梯总成" "1" " " (rtos stair_mass) (rtos stair_mass) "" "Entrance stair assembly" " " " " "")
		  ;(command "insert" "pc_mxb_block" "S" mscale (polar Bom_insert_point(/ pi 2) (* 13 mscale (+ embedded_is_exit 1))) 0
		; "" "" "" "Entrance stair assembly" "" (rtos stair_mass) (rtos stair_mass) "" "1" "塔架入口梯子总成" Index ""
		;"")
	;)
  
  
  ;升降机bom;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
	(setq xIndex (+ xIndex 1));混塔无防洪需求，无外爬梯，序号+0
	(setq Index (strcat "\"" (rtos xIndex) "\""))
	(if (or (= topfltype "2.xMW_TopFlange") (= topfltype "2MW_TopFlange"))
		(setq StairBom (strcat Index "	";*
			Index "	";序号
			"\"出图\"" "	";是否出图
			"\"           \"" "	";代号
			"\"           \"" "	";版本
			"\"钢丝绳导向正面右侧开门塔筒升降机\"" "	";名称
			"\"1\"" "	";数量
			"\"           \"" "	";材料
			"\"\"" "	";单重
			"\"\"" "	";总重
			"\"采购\"" "	";备注
			"\"           \"" "	";DRAWINGNUMBER
			"\"Wire guided lift with righted door\"" "	";NAME
			"\"           \"" "	";MATERIAL
			"\"Purchasing\"" "	";NOTES
			"\"自制零件\"" "	";零件类型
			"\"显示\"" "	"));显示状态
		(setq StairBom (strcat Index "	";*
			Index "	";序号
			"\"出图\"" "	";是否出图
			"\"           \"" "	";代号
			"\"           \"" "	";版本
			"\"升降机\"" "	";名称
			"\"1\"" "	";数量
			"\"           \"" "	";材料
			"\"\"" "	";单重
			"\"\"" "	";总重
			"\"采购\"" "	";备注
			"\"           \"" "	";DRAWINGNUMBER
			"\"Lift\"" "	";NAME
			"\"           \"" "	";MATERIAL
			"\"Purchasing\"" "	";NOTES
			"\"自制零件\"" "	";零件类型
			"\"显示\"" "	"));显示状态
	);end if;;;;;
	(write-line StairBom BomListTxt)  ;升降机bom;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  
  
	;(if (/= is_alert T)
	;  (command "insert" "tower_mxb" "S" mscale (polar Bom_insert_point(/ pi 2) (* 13 mscale (+ embedded_is_exit 2))) 0
	;      "" "" (rtos (+ embedded_is_exit 2)) "升降机" "1" "采购" "" "" "" "Lift" " " "Purchasing" "")
	;)
  
    
	;塔架段bom
	(setq i 0)
	(while (< i (length SectionMassList) );SectinMassList

		(setq SectionName (strcat (nth i SectionIndex) sufSectionName);把表SectionIndex的第i个元素与sufSectionName合并
		;SectionIndex (list "\"第一" "\"第二" "\"第三" "\"第四" "\"第五" "\"第六" "\"第七" "\"第八" "\"第九" "\"第十" "\"第十一" "\"第十二")
		;sufSectionName "段塔筒焊合\""
		;SectionIndexEN (list "Ⅰ\"" "Ⅱ\"" "Ⅲ\"" "Ⅳ\"" "Ⅴ\"" "Ⅵ\"" "Ⅶ\"" "Ⅷ\"" "Ⅸ\"" "Ⅹ\"" "Ⅺ\"" "Ⅻ\"")
		SectionNameEN (strcat "\"Tower Section "(nth i SectionIndexEN))
		section_mass (+ (nth i SectionMassList) (nth i FlangeMassList) (nth (+ i 1) FlangeMassList))
		;TowerSectionMass (strcat "\"" (rtos section_mass 2 1) "\"")
		;i (+ 1 i)
		xIndex (+ xIndex 1)
		Index (strcat "\""(rtos xIndex)"\"")
		)
    
		(if (= i 0);如果是第一段
			(setq section_mass (+ section_mass MassDoorFrame (- MassOfTowerDoorHole) ))
		)
    
		(setq TowerSectionMass (strcat "\"" (rtos section_mass 2 0) "\""))
		(setq all_tower_mass (+ all_tower_mass section_mass))  ;累加重量
		(if (= i (- section_qty 1));如果是顶段
			(progn
				(setq SectionName "\"顶段塔筒焊合\""
					SectionNameEN "\"Top Section\"")
			)
		)
		(setq  SectionBom(strcat Index "	"
			     Index "	"
			     "\"出图\"" "	"
			     "\"           \"" "	"
			     "\"           \"" "	"
			     SectionName "	"
			     "\"1\"" "	"
			     "\"           \"" "	"
			     TowerSectionMass "	"
			     TowerSectionMass "	"
			     "\"           \"" "	"
			     "\"           \"" "	"
			     SectionNameEN "	"
			     "\"           \"" "	"
			     "\"           \"" "	"
			     "\"自制零件\"" "	"
			     "\"显示\"" "	"))
		(write-line SectionBom BomListTxt)
		(setq i (+ 1 i))
		;(if (/= is_alert T)
		; (command "insert" "tower_mxb" "S" mscale (polar Bom_insert_point(/ pi 2) (* 13 mscale (+ i (+ embedded_is_exit 2)))) 0
		;      "" "" (rtos (+ i (+ embedded_is_exit 2))) (extract_str SectionName) "1" " " (extract_str TowerSectionMass)
		;    (extract_str TowerSectionMass) "" (extract_str SectionNameEN) " " " " "")   ;插入明细表
		;)
		;(print i)
	)
  
	;附件bom;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
	; (setq accessoryIndex (+ section_qty embedded_is_exit 3));塔筒段数+基础环+外爬梯+升降机+1
	; (setq xIndex (+ section_qty embedded_is_exit 3));螺栓序号用
	; (setq Index (strcat "\"" (rtos accessoryIndex) "\""))
	(if (or(= topfltype "V12_TopFlange")(= topfltype "V12_TopFlange_250"))
		(progn ;V12附件重量自动识别
			(if (= TheDoorType "ConcDoor") 
				(progn ;如果是V12的混塔，添加钢段附件重量、中间平台重量及混段附件重量
					(setq i 0)
					(setq towerheight (- (value retFlange section_qty 0) 0.4))
					(setq NameList '("钢段附件总成" "混段中间平台" "混段附件总成" ))
					(setq NameList_en '("Steel tower acccessory assembly" "concrete tower intermediate platforms" "concrete tower acccessory assembly" ))
		  		  
					(while (< i 3)
						(setq xIndex (+ xIndex 1))
						(setq Index(strcat "\""(rtos xIndex)"\""))
						(setq AccessoryName(strcat "\""(nth i NameList)"\""))
						(setq AccessoryName_en (strcat "\""(nth i NameList_en)"\""))
						(setq AccessoryMass(strcat "\""(rtos (nth i ConcreteStairsMass))"\""))
						(setq AttachBom(strcat Index "	"
							 Index "	"
							 "\"出图\"" "	"
							 "\"           \"" "	"
							 "\"           \"" "	"
							 AccessoryName "	"
							 "\"1\"" "	"
							 "\"           \"" "	"
							 AccessoryMass "	"
							 AccessoryMass "	"
							 "\"参考重量\"" "	"
							 "\"           \"" "	"
							 AccessoryName_en "	"
							 "\"           \"" "	"
							 "\"           \"" "	"
							 "\"自制零件\"" "	"
							 "\"显示\"" "	"))
						(write-line AttachBom BomListTxt)
						(setq i (+ 1 i) )
					);end while
				);end progn
				(progn ;如果是V12的钢塔，自动添加附件总成重量
					(setq xIndex (+ xIndex 1))
					(setq Index(strcat "\""(rtos xIndex)"\""))
					(setq AccessoryMass_steel(strcat "\""(rtos (Accessory_mass_steel))"\""))
					(setq AttachBom(strcat Index "	"
						 Index "	"
						 "\"出图\"" "	"
						 "\"           \"" "	"
						 "\"           \"" "	"
						 "\"附件总成\"" "	"
						 "\"1\"" "	"
						 "\"           \"" "	"
						 AccessoryMass_steel "	"
						 AccessoryMass_steel "	"
						 "\"参考重量\"" "	"
						 "\"           \"" "	"
						 "\"Accessory assembly\"" "	"
						 "\"           \"" "	"
						 "\"           \"" "	"
						 "\"自制零件\"" "	"
						 "\"显示\"" "	"))
					(write-line AttachBom BomListTxt)
				);end progn
			);end if
		);progn
		(progn ;其他机型附件重量不填写
			(setq xIndex (+ xIndex 1))
			(setq Index(strcat "\""(rtos xIndex)"\""))
			(setq AttachBom(strcat Index "	"
				 Index "	"
				 "\"出图\"" "	"
				 "\"           \"" "	"
				 "\"           \"" "	"
				 "\"附件总成\"" "	"
				 "\"1\"" "	"
				 "\"           \"" "	"
				 "\"           \"" "	"
				 "\"           \"" "	"
				 "\"参考重量\"" "	"
				 "\"           \"" "	"
				 "\"Accessory assembly\"" "	"
				 "\"           \"" "	"
				 "\"           \"" "	"
				 "\"自制零件\"" "	"
				 "\"显示\"" "	"))
			(write-line AttachBom BomListTxt)
		);end progn
	);end if
  
	;(if (/= is_alert T)
	;   (command "insert" "tower_mxb" "S" mscale (polar Bom_insert_point(/ pi 2) (* 13 mscale (+ i embedded_is_exit 3))) 0
	;      "" "" (rtos (+ i (+ embedded_is_exit 3))) "附件总成" "1" "参考重量" "" "" "" "Accessory assembly" " " " " "")   ;插入明细表
	;)
 
 ;阻尼器bom;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  ;对塔架净高度>125m的钢塔架，采用阻尼器，明细表中增加相应行
	(if (and (/= TheDoorType "ConcDoor")(> (- (value retFlange section_qty 0) (value retFlange 0 0)) 125))
		(cond
			( (= topfltype "21_TopFlange");21#阻尼器
				(progn 
					(setq xIndex (+ xIndex 1))
					(setq Index(strcat "\""(rtos xIndex)"\""))
					(setq AttachBom(strcat Index "	";*
					   Index "	";序号
					   "\"出图\"" "	";是否出图
					   "\"           \"" "	";代号
					   "\"           \"" "	";版本
					   "\"阻尼器及辅材模块-4X2液体阻尼器\"" "	";名称
					   "\"4\"" "	";数量
					   "\"           \"" "	";材料
					   "\"\"" "	";单重
					   "\"\"" "	";总重
					   "\"采购\"" "	";备注
					   "\"           \"" "	";DRAWINGNUMBER
					   "\"GW MTLD4X2\"" "	";NAME
					   "\"           \"" "	";MATERIAL
					   "\"Purchasing\"" "	";NOTES
					   "\"自制零件\"" "	";零件类型
					   "\"显示\"" "	"));显示状态
					(write-line AttachBom BomListTxt)
				);end progn))
			)
	  ( (or (= topfltype "5S_TopFlange") (= topfltype "5X_TopFlange") (= topfltype "V12_TopFlange")(= topfltype "V12_TopFlange_250"));V12\5S\5H阻尼器
	    (progn 
             (setq xIndex (+ xIndex 1))
	         (setq Index(strcat "\""(rtos xIndex)"\""))
	         (setq AttachBom(strcat Index "	";*
			       Index "	";序号
			       "\"出图\"" "	";是否出图
			       "\"60.10.85430\"" "	";代号
			       "\"           \"" "	";版本
			       "\"阻尼器及辅材模块-4X2液体阻尼器(150mm)\"" "	";名称
			       "\"1\"" "	";数量
			       "\"           \"" "	";材料
			       "\"\"" "	";单重
			       "\"\"" "	";总重
			       "\"采购\"" "	";备注
			       "\"           \"" "	";DRAWINGNUMBER
			       "\"GW HMTLD\"" "	";NAME
			       "\"           \"" "	";MATERIAL
			       "\"Purchasing\"" "	";NOTES
			       "\"自制零件\"" "	";零件类型
			       "\"显示\"" "	"));显示状态
	         (write-line AttachBom BomListTxt)
	    );end progn
      );
	  (  t
        (progn 
		    ; 冷却液
             (setq xIndex (+ xIndex 1))
	         (setq Index(strcat "\""(rtos xIndex)"\""))
	         (setq AttachBom(strcat Index "	";*
			       Index "	";序号
			       "\"出图\"" "	";是否出图
			       "\"           \"" "	";代号
			       "\"           \"" "	";版本
			       "\"塔架液体阻尼器阻尼液（塑料桶装）\"" "	";名称
			       "\"50\"" "	";数量
			       "\"           \"" "	";材料
			       "\"\"" "	";单重
			       "\"\"" "	";总重
			       "\"采购\"" "	";备注
			       "\"           \"" "	";DRAWINGNUMBER
			       "\"Coolant XEG-Ⅱ 20L/Barrel\"" "	";NAME
			       "\"           \"" "	";MATERIAL
			       "\"Purchasing\"" "	";NOTES
			       "\"自制零件\"" "	";零件类型
			       "\"显示\"" "	"));显示状态
	         (write-line AttachBom BomListTxt)
	        ; 液体阻尼器
             (setq xIndex (+ xIndex 1))
	         (setq Index(strcat "\""(rtos xIndex)"\""))
	         (setq AttachBom(strcat Index "	";*
		 	       Index "	";序号
			       "\"出图\"" "	";是否出图
			       "\"           \"" "	";代号
			       "\"           \"" "	";版本
			       "\"液体阻尼器\"" "	";名称
			       "\"10\"" "	";数量
			       "\"           \"" "	";材料
			       "\"\"" "	";单重
			       "\"\"" "	";总重
			       "\"采购\"" "	";备注
			       "\"           \"" "	";DRAWINGNUMBER
			       "\"TLD\"" "	";NAME
			       "\"           \"" "	";MATERIAL
			       "\"Purchasing\"" "	";NOTES
			       "\"自制零件\"" "	";零件类型
			       "\"显示\"" "	");显示状态
	         )
	         (write-line AttachBom BomListTxt)
	    );end progn
	  );end t   
    );end cond
   );end if 
   ;对塔架净高度为160m的混塔架，采用阻尼器，明细表中增加相应行
  (setq towerheight (- (value retFlange section_qty 0) 0.4))
  (if (and (= TheDoorType "ConcDoor") (> towerheight 155) (<= towerheight 160))
      (progn
			(setq xIndex (+ xIndex 1))
	        (setq Index(strcat "\""(rtos xIndex)"\""))
	        (setq AttachBom(strcat Index "	";*
			       Index "	";序号
			       "\"出图\"" "	";是否出图
			       "\"60.10.71375\"" "	";代号
			       "\"           \"" "	";版本
			       "\"阻尼器及辅材模块-4X2液体阻尼器\"" "	";名称
			       "\"2\"" "	";数量
			       "\"           \"" "	";材料
			       "\"\"" "	";单重
			       "\"\"" "	";总重
			       "\"采购\"" "	";备注
			       "\"           \"" "	";DRAWINGNUMBER
			       "\"GW HMTLD\"" "	";NAME
			       "\"           \"" "	";MATERIAL
			       "\"Purchasing\"" "	";NOTES
			       "\"自制零件\"" "	";零件类型
			       "\"显示\"" "	"));显示状态
	          (write-line AttachBom BomListTxt)
	   );end progn
  );end if 
  
  ;V12分片塔增加bom-（铝合金内附件+纵法兰+短尾铆钉及套环）;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  (if (and (or(= topfltype "V12_TopFlange")(= topfltype "V12_TopFlange_250")) (= TheTowerSliceType "SliceTower"))
    (progn 
	;铝合金附件
	  (setq xIndex (+ xIndex 1))
	  (setq Index(strcat "\""(rtos xIndex)"\""))
	  (setq AccessoryMass_Al(strcat "\""(rtos (Accessory_mass_Al))"\""))
	  (setq AttachBom(strcat Index "	";*
		 	       Index "	";序号
			       "\"出图\"" "	";是否出图
			       "\"           \"" "	";代号
			       "\"           \"" "	";版本
			       "\"铝合金附件总成\"" "	";名称
			       "\"1\"" "	";数量
			       "\"           \"" "	";材料
			       AccessoryMass_Al "	";单重
			       AccessoryMass_Al "	";总重
			       "\"参考重量\"" "	";备注
			       "\"           \"" "	";DRAWINGNUMBER
			       "\"Aluminum alloy accessory assembly\"" "	";NAME
			       "\"           \"" "	";MATERIAL
			       "\"           \"" "	";NOTES
			       "\"自制零件\"" "	";零件类型
			       "\"显示\"" "	");显示状态
      )
	  (write-line AttachBom BomListTxt)
	;纵法兰
	  (setq xIndex (+ xIndex 1))
	  (setq Index(strcat "\""(rtos xIndex)"\""))
	  (setq vertical_fla_mass(strcat "\""(rtos (Vertical_flange_mass))"\""))
	  (setq AttachBom(strcat Index "	";*
		 	       Index "	";序号
			       "\"出图\"" "	";是否出图
			       "\"           \"" "	";代号
			       "\"           \"" "	";版本
			       "\"竖向法兰\"" "	";名称
			       "\"1\"" "	";数量
			       "\"           \"" "	";材料
			       vertical_fla_mass "	";单重
			       vertical_fla_mass "	";总重
			       "\"\"" "	";备注
			       "\"           \"" "	";DRAWINGNUMBER
			       "\"Vertiacl outer flange\"" "	";NAME
			       "\"           \"" "	";MATERIAL
			       "\"           \"" "	";NOTES
			       "\"自制零件\"" "	";零件类型
			       "\"显示\"" "	");显示状态
      )
	  (write-line AttachBom BomListTxt)
	;短尾铆钉及套环
	  (setq xIndex (+ xIndex 1))
	  (setq Index(strcat "\""(rtos xIndex)"\""))
	  (setq revit_num(strcat "\""(rtos (Rivet_judge))"\""))
	  (setq AttachBom(strcat Index "	";*
		 	       Index "	";序号
			       "\"出图\"" "	";是否出图
			       "\"60.10.86216\"" "	";代号
			       "\"           \"" "	";版本
			       "\"短尾铆钉及套环\"" "	";名称
			       revit_num "	";数量
			       "\"           \"" "	";材料
			       "\"\"" "	";单重
			       "\"\"" "	";总重
			       "\"型号和厂家见技术要求\"" "	";备注
			       "\"           \"" "	";DRAWINGNUMBER
			       "\"Rivet\"" "	";NAME
			       "\"           \"" "	";MATERIAL
			       "\"           \"" "	";NOTES
			       "\"自制零件\"" "	";零件类型
			       "\"显示\"" "	");显示状态
      ) 
	  (write-line AttachBom BomListTxt)
	);end progn
  );end if
  
    
  
  
 ;  
  ; 添加标题栏
  (setq drawscale (strcat "1:" (rtos mscale)))
  (setq str_all_mass (rtos all_tower_mass 2 1))
  ;(if (/= is_alert T)
  ;  (command "insert" "金风科技标题栏" "S" mscale (list (* 806 mscale) 0 0) 0 "" "" "" "" "" "" "" "" "" "" "" "" "60.00.xxxxx" " " "" "" "" str_all_mass drawscale "" "" "" "" "" "")
  ;)
  ;螺栓，螺母，垫片bom;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  (setq ibolt (+ xIndex 1))
  ;如果有阻尼器，在前面中已判断加过两行，此处仅+1行
  ;(print i)
  ;(SetBoltNum retFlange Sum_flange)  ;统计螺栓、螺母、垫圈数量
  (SetBoltNum2 retFlange Sum_FLange)
  ;;;  (print (strcat"所有螺栓规格"(rtos(length FastenerListList))))
  ;;;  (print FastenerListList)
  ;;;  (print (strcat"螺栓长度："(rtos(length BoltLengthTypeList))))
  ;;;  (print BoltLengthTypeList)
  ;;;  (print (strcat"螺母规格："(rtos(length NutTypeList))))
  ;;;  (print NutTypeList)
  ;;;  (print (strcat"垫圈规格"(rtos(length washerTypeList))))
  ;;;  (print washerTypeList)
  (setq NumSection ibolt)
  (while(< ibolt (+ NumSection (- (length BoltLengthTypeList) 1)))
	(setq BoltIndex(- ibolt (- NumSection 1)))
  	(setq M72bolt_judge (nth 0 (nth BoltIndex BoltLengthTypeList)))
	(setq M72bolt_V (substr M72bolt_judge 2 2))
	(if (= (distof M72bolt_V) 72.0);以上三行用于判断是否为M72螺栓组，以引入对应的标准preBoltName_m72
		(progn
		(setq Index (strcat "\""(rtos ibolt)"\"")
		  BoltLengthTypeName(strcat preBoltName_m72(nth 0(nth BoltIndex BoltLengthTypeList))"\"")
		  BoltLengthTypeNameEN(strcat preBoltNameEN_m72(nth 0(nth BoltIndex BoltLengthTypeList))"\"")
		  BoltLengthTypeNum(strcat "\""(rtos(nth 1(nth BoltIndex BoltLengthTypeList))2 0)"\"")
		  BoltPLM(strcat "\""(search_bolt_PLM (nth 0(nth BoltIndex BoltLengthTypeList)))"\"")
		  BoltLengthTypeBom(strcat Index "	";*
					   Index "	";序号
					   "\"出图\"" "	";是否出图
					   BoltPLM "	";代号
					   "\"           \"" "	";版本
					   BoltLengthTypeName "	";名称
					   BoltLengthTypeNum "	";数量
					   "\"           \"" "	";材料
					   "\"\"" "	";单重
					   "\"\"" "	";总重
					   Anticorrosion "	";备注
					   "\"           \"" "	";DRAWINGNUMBER
					   BoltLengthTypeNameEN "	";NAME
					   "\"           \"" "	";MATERIAL
					   AnticorrosionEN "	";NOTES
					   "\"自制零件\"" "	";零件类型
					   "\"显示\"" "	");显示状态
		  ibolt (+ 1 ibolt)
		)
		)
		(progn
		(setq Index (strcat "\""(rtos ibolt)"\"")
		  BoltLengthTypeName(strcat preBoltName(nth 0(nth BoltIndex BoltLengthTypeList))"\"")
		  BoltLengthTypeNameEN(strcat preBoltNameEN(nth 0(nth BoltIndex BoltLengthTypeList))"\"")
		  BoltLengthTypeNum(strcat "\""(rtos(nth 1(nth BoltIndex BoltLengthTypeList))2 0)"\"")
		  BoltPLM(strcat "\""(search_bolt_PLM (nth 0(nth BoltIndex BoltLengthTypeList)))"\"")
		  BoltLengthTypeBom(strcat Index "	";*
					   Index "	";序号
					   "\"出图\"" "	";是否出图
					   BoltPLM "	";代号
					   "\"           \"" "	";版本
					   BoltLengthTypeName "	";名称
					   BoltLengthTypeNum "	";数量
					   "\"           \"" "	";材料
					   "\"\"" "	";单重
					   "\"\"" "	";总重
					   Anticorrosion "	";备注
					   "\"           \"" "	";DRAWINGNUMBER
					   BoltLengthTypeNameEN "	";NAME
					   "\"           \"" "	";MATERIAL
					   AnticorrosionEN "	";NOTES
					   "\"自制零件\"" "	";零件类型
					   "\"显示\"" "	");显示状态
		  ibolt (+ 1 ibolt)
		)
		)
	)
    (write-line BoltLengthTypeBom BomListTxt)
	)
  ;螺母
  (setq iNut ibolt)
  (setq EndNoBolt iNut)
  ;(print NutTypeList)

  
  (while(< iNut (+ EndNoBolt (-(length NutTypeList)1)))
	(setq NutIndex(- iNut (- EndNoBolt 1)))
	(setq M72nut_judge (nth 0 (nth NutIndex NutTypeList)))
	(setq M72nut_V (substr M72nut_judge 2 2))

	(if (= (distof M72nut_V) 72.0);用于判断是否为M72螺栓组，以引入对应的标准
		(progn
		
			(setq Index(strcat "\""(rtos iNut)"\"")
			NutTypeName(strcat preNutName_m72(nth 0(nth NutIndex NutTypeList))"\"")
			NutTypeNameEN(strcat preNutNameEN_m72(nth 0(nth NutIndex NutTypeList))"\"")
			NutPLM(strcat "\""(search_nut_PLM (nth 0(nth NutIndex NutTypeList)))"\"")
			NutTypeNum(strcat "\""(rtos(nth 1(nth NutIndex NutTypeList))2 0)"\"")
			NutTypeBom(strcat Index "	";*
						Index "	";序号
						"\"出图\"" "	";是否出图
						NutPLM "	";代号
						"\"           \"" "	";版本
						NutTypeName "	";名称
						NutTypeNum "	";数量
						"\"           \"" "	";材料
						"\"\"" "	";单重
						"\"\"" "	";总重
						Anticorrosion "	";备注
						"\"           \"" "	";DRAWINGNUMBER
						NutTypeNameEN "	";NAME
						"\"           \"" "	";MATERIAL
						AnticorrosionEN "	";NOTES
						"\"自制零件\"" "	";零件类型
						"\"显示\"" "	");显示状态
			iNut (+ 1 iNut))
		)
		(progn 
			(setq Index(strcat "\""(rtos iNut)"\"")
			NutTypeName(strcat preNutName(nth 0(nth NutIndex NutTypeList))"\"")
			NutTypeNameEN(strcat preNutNameEN(nth 0(nth NutIndex NutTypeList))"\"")
			NutPLM(strcat "\""(search_nut_PLM (nth 0(nth NutIndex NutTypeList)))"\"")
			NutTypeNum(strcat "\""(rtos(nth 1(nth NutIndex NutTypeList))2 0)"\"")
			NutTypeBom(strcat Index "	";*
						Index "	";序号
						"\"出图\"" "	";是否出图
						NutPLM "	";代号
						"\"           \"" "	";版本
						NutTypeName "	";名称
						NutTypeNum "	";数量
						"\"           \"" "	";材料
						"\"\"" "	";单重
						"\"\"" "	";总重
						Anticorrosion "	";备注
						"\"           \"" "	";DRAWINGNUMBER
						NutTypeNameEN "	";NAME
						"\"           \"" "	";MATERIAL
						AnticorrosionEN "	";NOTES
						"\"自制零件\"" "	";零件类型
						"\"显示\"" "	");显示状态
			iNut (+ 1 iNut))
		)
	
	)
    (write-line NutTypeBom BomListTxt)
  )
  ;垫圈
  (setq iWasher iNut)
  (setq EndNoNut iWasher)
  (while(< iWasher (+ EndNoNut (-(length WasherTypeList)1)))
    (setq Index(strcat "\""(rtos iWasher)"\"")
	  WasherIndex(- iWasher (- EndNoNut 1))
	  WasherTypeName(strcat preWasherName(nth 0(nth WasherIndex WasherTypeList))"\"")
	  WasherTypeNameEN(strcat preWasherNameEN(nth 0(nth WasherIndex WasherTypeList))"\"")
	  WasherPLM(strcat "\""(search_washer_PLM (nth 0(nth WasherIndex WasherTypeList)))"\"")
	  WasherTypeNum(strcat "\""(rtos(nth 1(nth WasherIndex WasherTypeList))2 0)"\"")
	  WasherTypeBom(strcat Index "	";*
			       Index "	";序号
			       "\"出图\"" "	";是否出图
			       WasherPLM "	";代号
			       "\"           \"" "	";版本
			       WasherTypeName "	";名称
			       WasherTypeNum "	";数量
			       "\"           \"" "	";材料
			       "\"\"" "	";单重
			       "\"\"" "	";总重
			       Anticorrosion "	";备注
			       "\"           \"" "	";DRAWINGNUMBER
			       WasherTypeNameEN "	";NAME
			       "\"           \"" "	";MATERIAL
			       AnticorrosionEN "	";NOTES
			       "\"自制零件\"" "	";零件类型
			       "\"显示\"" "	");显示状态
	  iWasher (+ 1 iWasher))
    (write-line WasherTypeBom BomListTxt)
  )
  ;顶法兰螺栓bom;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  (setq xIndex (+ iWasher 1));
  (setq Index (strcat "\"" (rtos iWasher) "\""))
   (cond 
		((= topfltype "V12_TopFlange")
			(progn
				(setq Topbolt_Bom (strcat Index "	";*
					Index "	";序号
					"\"出图\"" "	";是否出图
					"\"50.15.00644\"" "	";代号
					"\"           \"" "	";版本
					"\"偏航塔架连接模块\"" "	";名称
					"\"1\"" "	";数量
					"\"           \"" "	";材料
					"\"\"" "	";单重
					"\"\"" "	";总重
					"\"   \"" "	";备注
					"\"           \"" "	";DRAWINGNUMBER
					"\"Yaw and tower top connection\"" "	";NAME
					"\"           \"" "	";MATERIAL
					"\"    \"" "	";NOTES
					"\"自制零件\"" "	";零件类型
					"\"显示\"" "	"));显示状态)
				(write-line Topbolt_Bom BomListTxt)
			)
		)
		((= topfltype "V12_TopFlange_250")
			(progn
				(setq Topbolt_Bom (strcat Index "	";*
					Index "	";序号
					"\"出图\"" "	";是否出图
					"\"50.15.00828\"" "	";代号
					"\"           \"" "	";版本
					"\"偏航塔架连接模块\"" "	";名称
					"\"1\"" "	";数量
					"\"           \"" "	";材料
					"\"\"" "	";单重
					"\"\"" "	";总重
					"\"   \"" "	";备注
					"\"           \"" "	";DRAWINGNUMBER
					"\"Yaw and tower top connection\"" "	";NAME
					"\"           \"" "	";MATERIAL
					"\"    \"" "	";NOTES
					"\"自制零件\"" "	";零件类型
					"\"显示\"" "	"));显示状态)
				(write-line Topbolt_Bom BomListTxt)
			)
		)
		((= topfltype "V15_TopFlange")
			(progn
				(setq Topbolt_Bom (strcat Index "	";*
					Index "	";序号
					"\"出图\"" "	";是否出图
					"\"50.15.00875\"" "	";代号
					"\"           \"" "	";版本
					"\"偏航塔架连接模块\"" "	";名称
					"\"1\"" "	";数量
					"\"           \"" "	";材料
					"\"\"" "	";单重
					"\"\"" "	";总重
					"\"   \"" "	";备注
					"\"           \"" "	";DRAWINGNUMBER
					"\"Yaw and tower top connection\"" "	";NAME
					"\"           \"" "	";MATERIAL
					"\"    \"" "	";NOTES
					"\"自制零件\"" "	";零件类型
					"\"显示\"" "	"));显示状态)
				(write-line Topbolt_Bom BomListTxt)
			)
		)
		((= topfltype "V15_TopFlange_250")
			(progn
				(setq Topbolt_Bom (strcat Index "	";*
					Index "	";序号
					"\"出图\"" "	";是否出图
					"\"50.15.00874\"" "	";代号
					"\"           \"" "	";版本
					"\"偏航塔架连接模块\"" "	";名称
					"\"1\"" "	";数量
					"\"           \"" "	";材料
					"\"\"" "	";单重
					"\"\"" "	";总重
					"\"   \"" "	";备注
					"\"           \"" "	";DRAWINGNUMBER
					"\"Yaw and tower top connection\"" "	";NAME
					"\"           \"" "	";MATERIAL
					"\"    \"" "	";NOTES
					"\"自制零件\"" "	";零件类型
					"\"显示\"" "	"));显示状态)
				(write-line Topbolt_Bom BomListTxt)
			)
		)
		((= topfltype "V17_TopFlange_400")
			(progn
				(setq Topbolt_Bom (strcat Index "	";*
					Index "	";序号
					"\"出图\"" "	";是否出图
					"\"50.15.00934\"" "	";代号
					"\"           \"" "	";版本
					"\"偏航塔架连接模块\"" "	";名称
					"\"1\"" "	";数量
					"\"           \"" "	";材料
					"\"\"" "	";单重
					"\"\"" "	";总重
					"\"   \"" "	";备注
					"\"           \"" "	";DRAWINGNUMBER
					"\"Yaw and tower top connection\"" "	";NAME
					"\"           \"" "	";MATERIAL
					"\"    \"" "	";NOTES
					"\"自制零件\"" "	";零件类型
					"\"显示\"" "	"));显示状态)
				(write-line Topbolt_Bom BomListTxt)
			)
		)
   )
  (close BomListTxt)
  (print "明细表成功生成！")
  (prin1)
)
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;end boomlist;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;boomlist1文件-V12明细表文本导出;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;*********************************明细表文本导出***********************/
(defun BomList1(TowerExcelFile SectionMassList FlangeMassList stair_mass Sum_FLange MassDoorFrame MassOfTowerDoorHole
	       / i j preBomName BomTitle BomListTxt SectionName BoltLengthTypeBom NutTypeBom WasherTypeBom FastenerListList 
		   AsmDrawingNum AsmName AsmNameEn AsmMass PlugMass revit_num)
  (setq Bom_insert_point (list (* 806 mscale) (* 50 mscale) 0))	  ;明细表插入点
  ;(if (/= ErrorAsm T)
   ;   (command "insert" "one_key_mxb_title" "S" mscale Bom_insert_point 0 "") ;插入明细表表头
  ;);end if
  (setq preBomName (GetDir TowerExcelFile));明细表的名称
  ;明细表表头写入
  (setq BomTitle (strcat "\"*\"" "	"
			 "\"序号\"" "	"
			 "\"是否出图\"" "	"
			 "\"代号\"" "	"
			 "\"版本\"" "	"
			 "\"名称\"" "	"
			 "\"数量\"" "	"
			 "\"材料\"" "	"
			 "\"单重\"" "	"
			 "\"总重\"" "	"
			 "\"备注\"" "	"
			 "\"DRAWINGNUMBER\"" "	"
			 "\"NAME\"" "	"
			 "\"MATERIAL\"" "	"
			 "\"NOTES\"" "	"
			 "\"零件类型\"" "	"
			 "\"显示状态\"" "	")
	SectionIndex (list "\"第一" "\"第二" "\"第三" "\"第四" "\"第五" "\"第六" "\"第七" "\"第八" "\"第九" "\"第十" "\"第十一" "\"第十二")
	SectionIndexEN (list "Ⅰ" "Ⅱ" "Ⅲ" "Ⅳ" "Ⅴ" "Ⅵ" "Ⅶ" "Ⅷ" "Ⅸ" "Ⅹ" "Ⅺ" "Ⅻ"))
  (setq Anticorrosion "\"达克罗\""
	AnticorrosionEN "\"Dacromet\""
	preBoltName "\"螺栓GB/T5782-"
	preBoltNameEN "\"Bolt ISO4014-"
	preNutName "\"螺母GB/T6170-"
	preNutNameEN "\"Nut ISO4032-"
	preWasherName "\"垫圈EN14399-6-"
	preWasherNameEN "\"Washer EN14399-6-")
 ;(setq sufSectionName "段塔筒附件总成\"")
  (setq sufSectionName "段塔筒主体模块\"")  
  (setq BomListTxt (open (strcat preBomName "txt") "w"))
  (write-line BomTitle BomListTxt);把BomTitle写到BomListTxt
  (setq all_tower_mass 0.0)  ;设置初始塔架重量为0
  ;基础环/塔架底座  
  (setq embedded_is_exit 0)
  (if (= (EmbeddedOrNot) T);如果是基础环
    (progn
      (setq embedded_is_exit 1)
      (setq Index (strcat "\"" (rtos embedded_is_exit) "\""));把两个字符串合并成一个字符并返回，返回 \"1\" ；rots四舍五入，
      (setq embedded_mass_str (strcat "\"" (rtos mass_of_embedded 2 0) "\""));返回重量
      (setq embeddedBom (strcat Index "	"
				Index "	"
				"\"出图\"" "	"
				"\"           \"" "	"
				"\"           \"" "	"
				"\"塔架底座\"" "	"
				"\"1\"" "	"
				"\"           \"" "	"
				embedded_mass_str "	"
				embedded_mass_str "	"
				"\"           \"" "	"
				"\"           \"" "	"
				"\"Embedded Steel Can\"" "	"
				"\"           \"" "	"
				"\"           \"" "	"
				"\"自制零件\"" "	"
				"\"显示\"" "	"))
      (write-line embeddedBom BomListTxt)
      
      ;(if (/= ErrorAsm T)
         ; (command "insert" "tower_mxb" "S" mscale (polar Bom_insert_point (/ pi 2) (* 13 mscale)) 0
	      ;         "" "" (rtos embedded_is_exit) "塔架底座" "1" " " (rtos mass_of_embedded 2 0) (rtos mass_of_embedded 2 0) "" "Embedded Steel Can" " " " " "") ;插入基础环明细表
     ; );end if
      (setq all_tower_mass (+ all_tower_mass mass_of_embedded))  ;累加重量
    )
  );基础环if的右括号，END基础环
  
  ;外爬梯bom;;;;;;;;
  (setq Index (strcat "\"" (rtos (+ embedded_is_exit 1)) "\""));基础环的序号，如果有基础环，embedded_is_exit为1，如果没有embedded_is_exit为0
  (if (= stair_mass 0)
    (setq stair_mass_str (strcat "\"请填写重量！\""))
    (setq stair_mass_str (strcat "\"" (rtos stair_mass) "\""))
  )
  (setq all_tower_mass (+ all_tower_mass stair_mass))  ;累加重量
  (setq StairBom(strcat Index "	"
			Index "	"
			"\"出图\"" "	"
			"\"60.08.05372\"" "	"
			"\"           \"" "	"
			"\"入口梯模块\"" "	"
			"\"1\"" "	"
			"\"           \"" "	"
			stair_mass_str "	"
			stair_mass_str "	"
			"\"           \"" "	"
			"\"           \"" "	"
			"\"Entrance stair assembly\"" "	"
			"\"           \"" "	"
			"\"           \"" "	"
			"\"自制零件\"" "	"
			"\"显示\"" "	"))
  (write-line StairBom BomListTxt)
  ;(if (/= ErrorAsm T)
      ;(command "insert" "tower_mxb" "S" mscale (polar Bom_insert_point(/ pi 2) (* 13 mscale (+ embedded_is_exit 1))) 0
	     ; "" "" (rtos (+ embedded_is_exit 1)) "入口梯模块" "1" " " (rtos stair_mass) (rtos stair_mass) "" "Entrance stair assembly" " " " " "")
		  
 ; )
  
  
  ;升降机bom;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  (setq Index (strcat "\"" (rtos (+ embedded_is_exit 2)) "\""))
  (if (or (= topfltype "2.xMW_TopFlange") (= topfltype "2MW_TopFlange"))
    (setq StairBom (strcat Index "	";*
			Index "	";序号
			"\"出图\"" "	";是否出图
			"\"           \"" "	";代号
			"\"           \"" "	";版本
			"\"钢丝绳导向正面右侧开门塔筒升降机\"" "	";名称
			"\"1\"" "	";数量
			"\"           \"" "	";材料
			"\"\"" "	";单重
			"\"\"" "	";总重
			"\"采购\"" "	";备注
			"\"           \"" "	";DRAWINGNUMBER
			"\"Wire guided lift with righted door\"" "	";NAME
			"\"           \"" "	";MATERIAL
			"\"Purchasing\"" "	";NOTES
			"\"自制零件\"" "	";零件类型
			"\"显示\"" "	"));显示状态
      (setq StairBom (strcat Index "	";*
			Index "	";序号
			"\"出图\"" "	";是否出图
			"\"60.10.74335\"" "	";代号
			"\"           \"" "	";版本
			"\"升降机模块-钢丝绳导向型\"" "	";名称
			"\"1\"" "	";数量
			"\"           \"" "	";材料
			"\"\"" "	";单重
			"\"\"" "	";总重
			"\"采购\"" "	";备注
			"\"           \"" "	";DRAWINGNUMBER
			"\"Lift module-Wire rope guide type\"" "	";NAME
			"\"           \"" "	";MATERIAL
			"\"Purchasing\"" "	";NOTES
			"\"自制零件\"" "	";零件类型
			"\"显示\"" "	"));显示状态
  );end if;;;;;
  (write-line StairBom BomListTxt)
  ;(if (/= ErrorAsm T)
     ;(command "insert" "tower_mxb" "S" mscale (polar Bom_insert_point(/ pi 2) (* 13 mscale (+ embedded_is_exit 2))) 0
	     ; "" "" (rtos (+ embedded_is_exit 2)) "升降机" "1" "采购" "" "" "" "Lift" " " "Purchasing" "")
  ;)

  ;塔架段及附件bom
  (setq i 0);用于塔架段的序号累加（因为塔架段后为附件段，所以增量为2）
  (setq j 0);塔筒段数的循环变量（增量为1）
  (while (< j (length SectionMassList) );SectinMassList
  ;1-塔架段bom
    (setq SectionName (strcat (nth j SectionIndex) sufSectionName);把表SectionIndex的第i个元素与sufSectionName合并
	  ;SectionIndex (list "\"第一" "\"第二" "\"第三" "\"第四" "\"第五" "\"第六" "\"第七" "\"第八" "\"第九" "\"第十" "\"第十一" "\"第十二")
	  ;sufSectionName "段塔筒焊合\""
	  ;SectionIndexEN (list "Ⅰ\"" "Ⅱ\"" "Ⅲ\"" "Ⅳ\"" "Ⅴ\"" "Ⅵ\"" "Ⅶ\"" "Ⅷ\"" "Ⅸ\"" "Ⅹ\"" "Ⅺ\"" "Ⅻ\""))
	  SectionNameEN (strcat "\"Tower Section "(nth j SectionIndexEN) " body module\"")
	  section_mass (+ (nth j SectionMassList) (nth j FlangeMassList) (nth (+ j 1) FlangeMassList))
	  ;TowerSectionMass (strcat "\"" (rtos section_mass 2 1) "\"")
	  ;j (+ 1 j)
	  Index (strcat "\""(rtos (+ i (+ embedded_is_exit 3)))"\"")
    )
    
    (if (= j 0) (setq section_mass (+ section_mass MassDoorFrame (- MassOfTowerDoorHole) )));如果是第一段
    
    (setq TowerSectionMass (strcat "\"" (rtos section_mass 2 0) "\""))
    (setq all_tower_mass (+ all_tower_mass section_mass))  ;累加重量
    (if (= j (- section_qty 1));如果是顶段
	  (setq SectionName "\"顶段塔筒主体模块\""
	        SectionNameEN "\"Top Section body module\"")
    );end if
    (setq  SectionBom(strcat Index "	"
			     Index "	"
			     "\"出图\"" "	"
			     "\"60.        \"" "	"
			     "\"           \"" "	"
			     SectionName "	"
			     "\"1\"" "	"
			     "\"           \"" "	"
			     TowerSectionMass "	"
			     TowerSectionMass "	"
			     "\"本图       \"" "	"
			     "\"           \"" "	"
			     SectionNameEN "	"
			     "\"           \"" "	"
			     "\"In drawing \"" "	"
			     "\"自制零件\"" "	"
			     "\"显示\"" "	"))
    (write-line SectionBom BomListTxt)
     
	;2-附件bom
	(setq accessoryIndex (+ i embedded_is_exit 4));塔筒段数+基础环+外爬梯+升降机+1
    (setq xIndex (+ section_qty section_qty embedded_is_exit 3));螺栓序号用
    (setq Index (strcat "\"" (rtos accessoryIndex) "\""));附件序号
	(if (= (value retAsm j 2) nil)
		(progn
			(alert "附件库中无此附件，程序退出！")
			(exit)
		)
	)
	  (setq AsmDrawingNum (strcat "\""(value retAsm j 1)"\""));附件图号-从附件设计表中提取
	  (setq AsmName (strcat "\""(value retAsm j 2)"\""));附件中文名-从附件设计表中提取
	  (setq AsmNameEn (strcat "\""(value retAsm j 3)"\""));附件英文名-从附件设计表中提取
	  (setq AsmMass (strcat "\""(rtos (value retAsm j 4))"\""));附件重量-从附件设计表中提取
      (setq AttachBom(strcat Index "	"
		   	 Index "	"
			 "\"出图\"" "	"
			 AsmDrawingNum "	"
			 "\"           \"" "	"
			 AsmName "	"
			 "\"1\"" "	"
			 "\"           \"" "	"
			 AsmMass "	"
			 AsmMass "	"
			 "\"           \"" "	"
			 "\"           \"" "	"
			 AsmNameEn "	"
			 "\"           \"" "	"
			 "\"           \"" "	"
			 "\"自制零件\"" "	"
			 "\"显示\"" "	"))
            
      (write-line AttachBom BomListTxt)
	
    (setq i (+ 2 i))
	(setq j (+ 1 j))
    ;(if (/= ErrorAsm T)
      ;(command "insert" "tower_mxb" "S" mscale (polar Bom_insert_point(/ pi 2) (* 13 mscale (+ i (+ embedded_is_exit 2)))) 0
	   ;   "" "" (rtos (+ i (+ embedded_is_exit 2))) (extract_str SectionName) "1" " " (extract_str TowerSectionMass)
	   ;  (extract_str TowerSectionMass) "" (extract_str SectionNameEN) " " " " "")   ;插入明细表
   ; )
  );end while
  ;附件bom;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  ; (setq i 0);用于附件的序号累加（因为塔架段后为附件段，所以增量为2）
  ; (setq j 0);塔筒段数的循环变量（增量为1）
  ; (while (< j section_qty );SectinMassList
      ; (setq accessoryIndex (+ i embedded_is_exit 4));塔筒段数+基础环+外爬梯+升降机+1
      ; (setq xIndex (+ section_qty section_qty embedded_is_exit 3));螺栓序号用
      ; (setq Index (strcat "\"" (rtos accessoryIndex) "\""));附件序号
	  ; (setq AsmDrawingNum (strcat "\""(value retAsm j 1)"\""));附件图号-从附件设计表中提取
	  ; (setq AsmName (strcat "\""(value retAsm j 2)"\""));附件中文名-从附件设计表中提取
	  ; (setq AsmNameEn (strcat "\""(value retAsm j 3)"\""));附件英文名-从附件设计表中提取
	  ; (setq AsmMass (strcat "\""(rtos (value retAsm j 4))"\""));附件重量-从附件设计表中提取
      ; (setq AttachBom(strcat Index "	"
		   	 ; Index "	"
			 ; "\"出图\"" "	"
			 ; AsmDrawingNum "	"
			 ; "\"           \"" "	"
			 ; AsmName "	"
			 ; "\"1\"" "	"
			 ; "\"           \"" "	"
			 ; AsmMass "	"
			 ; AsmMass "	"
			 ; "\"           \"" "	"
			 ; "\"           \"" "	"
			 ; AsmNameEn "	"
			 ; "\"           \"" "	"
			 ; "\"           \"" "	"
			 ; "\"自制零件\"" "	"
			 ; "\"显示\"" "	"))
            
      ; (write-line AttachBom BomListTxt)
      ; (setq i (+ 2 i))
	  ; (setq j (+ 1 j))

	 
  ; ; (if (/= is_alert T)
    ; ; (command "insert" "tower_mxb" "S" mscale (polar Bom_insert_point(/ pi 2) (* 13 mscale (+ i embedded_is_exit 3))) 0
	     ; ; "" "" (rtos (+ i (+ embedded_is_exit 3))) "附件总成" "1" "参考重量" "" "" "" "Accessory assembly" " " " " "")   ;插入明细表
  ; ; )
  ; );end while
  
  ;电缆固定夹bom;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  (setq xIndex (+ accessoryIndex 1))
	  (setq Index(strcat "\""(rtos xIndex)"\""))
	  (if (and (/= (value retDescription 0 1) nil) (/= (value retDescription 0 1) ""))
	    (setq Power (value retDescription 0 1))
		(setq Power (atof(getstring "请输入功率(MW)：")))  
	   );end if
	  (cond 
	       ((< Power 6.0)
	       (setq AttachBom(strcat Index "	";*       
				   Index "	"
			       "\"出图\"" "	"
			       "\"60.13.02819\"" "	"
			       "\"           \"" "	"
			       "\"电缆固定夹3×185\"" "	"
			       "\"1\"" "	"
			       "\"           \"" "	"
			       "\"\"" "	"
			       "\"\"" "	"
			       "\"00089398\"" "	"
			       "\"           \"" "	"
			       "\"Cable Clamp3×185\"" "	"
			       "\"           \"" "	"
			       "\"           \"" "	"
			       "\"自制零件\"" "	"
			       "\"显示\"" "	")))
		  ((and (>= Power 6.0) (< Power 7.5))
		  (setq AttachBom(strcat Index "	";*       
				   Index "	"
			       "\"出图\"" "	"
			       "\"60.13.02821\"" "	"
			       "\"           \"" "	"
			       "\"电缆固定夹3×240\"" "	"
			       "\"1\"" "	"
			       "\"           \"" "	"
			       "\"\"" "	"
			       "\"\"" "	"
			       "\"00089405\"" "	"
			       "\"           \"" "	"
			       "\"Cable Clamp3×240\"" "	"
			       "\"           \"" "	"
			       "\"           \"" "	"
			       "\"自制零件\"" "	"
			       "\"显示\"" "	")))
	      (t
		  (setq AttachBom(strcat Index "	";*       
				   Index "	"
			       "\"出图\"" "	"
			       "\"           \"" "	"
			       "\"           \"" "	"
			       "\"电缆固定夹\"" "	"
			       "\"1\"" "	"
			       "\"           \"" "	"
			       "\"\"" "	"
			       "\"\"" "	"
			       "\"           \"" "	"
			       "\"           \"" "	"
			       "\"Cable Clamp\"" "	"
			       "\"           \"" "	"
			       "\"           \"" "	"
			       "\"自制零件\"" "	"
			       "\"显示\"" "	")))
		  
	  );end cond
	  (write-line AttachBom BomListTxt)
	  	 
  
  ;中间段和顶段通用图bom;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  (setq xIndex (+ xIndex 1))
	  (setq Index(strcat "\""(rtos xIndex)"\""))
	  ;堵头重量计算
	  (cond 
	    ( (= section_qty 4) (setq PlugMass (* 0.04 6)) )
		( (= section_qty 5) (setq PlugMass (* 0.04 12)) )
		( (= section_qty 6) (setq PlugMass (* 0.04 18)) )
		( (= section_qty 7) (setq PlugMass (* 0.04 18)) )
	  );end cond
	  (setq PlugMass (strcat "\""(rtos PlugMass)"\""));附件重量-从附件设计表中提取
	  (setq AttachBom(strcat Index "	";*
		 	       Index "	";序号
			       "\"出图\"" "	";是否出图
			       "\"60.01.03875\"" "	";代号
			       "\"           \"" "	";版本
			       "\"中间段和顶段塔架焊合通用图\"" "	";名称
			       "\"1\"" "	";数量
			       "\"\"" "	";材料
			       PlugMass "	";单重
			       PlugMass "	";总重
			       "\"           \"" "	";备注
			       "\"           \"" "	";DRAWINGNUMBER
			       "\"Middle and Top section welded\"" "	";NAME
			       "\"           \"" "	";MATERIAL
			       "\"           \"" "	";NOTES
			       "\"自制零件\"" "	";零件类型
			       "\"显示\"" "	");显示状态
      ) 
	  (write-line AttachBom BomListTxt)
	  
  
  ;法兰通用图bom;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  (setq xIndex (+ xIndex 1))
	  (setq Index(strcat "\""(rtos xIndex)"\""))
	  (setq AttachBom(strcat Index "	";*
		 	       Index "	";序号
			       "\"出图\"" "	";是否出图
			       "\"60.01.04141\"" "	";代号
			       "\"           \"" "	";版本
			       "\"法兰通用图\"" "	";名称
			       "\"1\"" "	";数量
			       "\"           \"" "	";材料
			       "\"\"" "	";单重
			       "\"\"" "	";总重
			       "\"           \"" "	";备注
			       "\"           \"" "	";DRAWINGNUMBER
			       "\"General drawing of flange\"" "	";NAME
			       "\"           \"" "	";MATERIAL
			       "\"           \"" "	";NOTES
			       "\"自制零件\"" "	";零件类型
			       "\"显示\"" "	");显示状态
      ) 
	  (write-line AttachBom BomListTxt)
  
  ;第一段塔筒焊合通用图bom-分片塔没有;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  (if (= TheTowerSliceType "NomalTower")
    (progn 
      (setq xIndex (+ xIndex 1))
	  (setq Index(strcat "\""(rtos xIndex)"\""))
	  (setq AttachBom(strcat Index "	";*
		 	       Index "	";序号
			       "\"出图\"" "	";是否出图
			       "\"60.01.03873\"" "	";代号
			       "\"           \"" "	";版本
			       "\"底段焊合通用图\"" "	";名称
			       "\"1          \"" "	";数量
			       "\"           \"" "	";材料
			       "\"1.76\"" "	";单重
			       "\"1.76\"" "	";总重
			       "\"           \"" "	";备注
			       "\"           \"" "	";DRAWINGNUMBER
			       "\"Bottom Section Welded\"" "	";NAME
			       "\"           \"" "	";MATERIAL
			       "\"           \"" "	";NOTES
			       "\"自制零件\"" "	";零件类型
			       "\"显示\"" "	"));显示状态
       
	  (write-line AttachBom BomListTxt)
	 
	)
   );end if 
  

  
  ; 添加标题栏
  (setq drawscale (strcat "1:" (rtos mscale)))
  (setq str_all_mass (rtos all_tower_mass 2 1))
  ;(if (/= is_alert T)
  ;  (command "insert" "金风科技标题栏" "S" mscale (list (* 806 mscale) 0 0) 0 "" "" "" "" "" "" "" "" "" "" "" "" "60.00.xxxxx" " " "" "" "" str_all_mass drawscale "" "" "" "" "" "")
  ;)
  
  
    ;螺栓，螺母，垫片bom;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  (setq count (+ xIndex 1))
  (setq ibolt count) ;第一个螺栓的序号
  (SetBoltNum2 retFlange section_qty);统计螺栓、螺母、垫圈数量
  ;(print BoltLengthTypeList)
  ;(print NutTypeList)
  ;(print WasherTypeList)
  (setq BoltLengthTypeList (cdr BoltLengthTypeList));去掉第一个元素
  (setq NutTypeList (cdr NutTypeList))
  (setq WasherTypeList (cdr WasherTypeList))
  ;(print BoltLengthTypeList)
  ;(print NutTypeList)
  ;(print WasherTypeList)
  (setq count (+ count (length BoltLengthTypeList) (length NutTypeList) (length WasherTypeList)));螺栓，螺母，垫片总的数量
  ;处理螺栓螺母垫片表,给公用螺栓的加空列表
  (setq nn 1)
  (while (< nn (length BoltLengthTypeList))
    (setq bolt_m_1 (substr (nth 0 (nth nn BoltLengthTypeList)) 1 3 ))
    (if (= (nth nn NutTypeList) nil);如果为空
      (progn
        (setq NutTypeList (list_insert NutTypeList nn '("" "")))
        (setq WasherTypeList (list_insert WasherTypeList nn '("" "")))
      )
      (progn
        (setq nut_m_1  (substr (nth 0 (nth nn NutTypeList)) 1 3 ))
        (if (/= bolt_m_1 nut_m_1)
          (progn
            (setq NutTypeList (list_insert NutTypeList nn '("" "")))
            (setq WasherTypeList (list_insert WasherTypeList nn '("" "")))
          );End progn
        );End if
      );End progn
    );End if
    (setq nn (+ nn 1))
  )
  ;(print BoltLengthTypeList)
  ;(print NutTypeList)
  ;(print WasherTypeList)

  (setq k 0)
  (while(< k (length BoltLengthTypeList))
    (setq bolt_m_1  (substr (nth 0 (nth k BoltLengthTypeList)) 1 3 ))
    (if (= (nth k NutTypeList) nil)
      (setq nut_m_1 (substr (nth 0 (nth k BoltLengthTypeList)) 1 3 ))
      (setq nut_m_1  (substr (nth 0 (nth k NutTypeList)) 1 3 ))
    );end if
    
    ;(print bolt_m_1 )
    ;(print nut_m_1 )

    (if (= bolt_m_1 nut_m_1 )
      (progn;如果列表中螺栓和垫片匹配
        (setq Index (rtos ibolt)
	  BoltIndex  k
	  BoltLengthTypeName (strcat preBoltName (nth 0 (nth BoltIndex BoltLengthTypeList)))
	  BoltLengthTypeNameEN (strcat preBoltNameEN (nth 0 (nth BoltIndex BoltLengthTypeList)))
	  BoltLengthTypeNum (rtos (nth 1 (nth BoltIndex BoltLengthTypeList)) 2 0)
	  BoltPLM (search_bolt_PLM (nth 0 (nth BoltIndex BoltLengthTypeList))))
    
        ;螺栓明细表
        (setq bolt_zb (list (* 806 mscale) (+ (* 50 mscale) (* (atoi Index) 13 mscale)) 0))
	;螺栓明细表
        (setq Index (strcat "\""(rtos ibolt)"\"")
	  BoltIndex k
	  BoltLengthTypeName(strcat preBoltName(nth 0(nth BoltIndex BoltLengthTypeList))"\"")
	  BoltLengthTypeNameEN(strcat preBoltNameEN(nth 0(nth BoltIndex BoltLengthTypeList))"\"")
	  BoltLengthTypeNum(strcat "\""(rtos(nth 1(nth BoltIndex BoltLengthTypeList))2 0)"\"")
	  BoltPLM(strcat "\""(search_bolt_PLM (nth 0(nth BoltIndex BoltLengthTypeList)))"\"")
	  BoltLengthTypeBom(strcat Index "	";*
				   Index "	";序号
				   "\"出图\"" "	";是否出图
				   BoltPLM "	";代号
				   "\"           \"" "	";版本
				   BoltLengthTypeName "	";名称
				   BoltLengthTypeNum "	";数量
				   "\"           \"" "	";材料
				   "\"\"" "	";单重
				   "\"\"" "	";总重
				   Anticorrosion "	";备注
				   "\"           \"" "	";DRAWINGNUMBER
				   BoltLengthTypeNameEN "	";NAME
				   "\"           \"" "	";MATERIAL
				   AnticorrosionEN "	";NOTES
				   "\"自制零件\"" "	";零件类型
				   "\"显示\"" "	");显示状态
	)
        (write-line BoltLengthTypeBom BomListTxt)
        ;插入块的方式插入明细表
	;(zq_block_insert bolt_zb Index BoltPLM BoltLengthTypeName BoltLengthTypeNameEN BoltLengthTypeNum Anticorrosion AnticorrosionEN "" "" 0.44 0.62)
	
        ;螺母
        (setq iNut (+ ibolt 1))
        (setq EndNoBolt k)
        (setq Index (rtos iNut)
	  NutIndex k
	  NutTypeName(strcat preNutName (nth 0 (nth NutIndex NutTypeList)))
	  NutTypeNameEN(strcat preNutNameEN (nth 0 (nth NutIndex NutTypeList)) )
	  NutPLM (search_nut_PLM (nth 0 (nth NutIndex NutTypeList)) )
	  NutTypeNum (rtos (nth 1 (nth NutIndex NutTypeList)) 2 0))
        ;螺母明细表
        (setq nut_zb (list (* 806 mscale) (+ (* 50 mscale) (* (atoi Index) 13 mscale)) 0))

        (setq Index(strcat "\""(rtos iNut)"\"")
	  NutIndex k
	  NutTypeName(strcat preNutName(nth 0(nth NutIndex NutTypeList))"\"")
	  NutTypeNameEN(strcat preNutNameEN(nth 0(nth NutIndex NutTypeList))"\"")
	  NutPLM(strcat "\""(search_nut_PLM (nth 0(nth NutIndex NutTypeList)))"\"")
	  NutTypeNum(strcat "\""(rtos(nth 1(nth NutIndex NutTypeList))2 0)"\"")
	  NutTypeBom(strcat Index "	";*
			    Index "	";序号
			    "\"出图\"" "	";是否出图
			    NutPLM "	";代号
			    "\"           \"" "	";版本
			    NutTypeName "	";名称
			    NutTypeNum "	";数量
			    "\"           \"" "	";材料
			    "\"\"" "	";单重
			    "\"\"" "	";总重
			    Anticorrosion "	";备注
			    "\"           \"" "	";DRAWINGNUMBER
			    NutTypeNameEN "	";NAME
			    "\"           \"" "	";MATERIAL
			    AnticorrosionEN "	";NOTES
			    "\"自制零件\"" "	";零件类型
			    "\"显示\"" "	");显示状态
	)
        (write-line NutTypeBom BomListTxt)

	

        ;(zq_block_insert nut_zb Index NutPLM NutTypeName NutTypeNameEN NutTypeNum Anticorrosion AnticorrosionEN "" "" 0.44 0.89)
	
        ;垫圈
        (setq iWasher (+ iNut 1))
        (setq EndNoNut k)
        (setq Index (rtos iWasher)
	  WasherIndex k
	  WasherTypeName (strcat preWasherName (nth 0 (nth WasherIndex WasherTypeList)))
	  WasherTypeNameEN (strcat preWasherNameEN (nth 0 (nth WasherIndex WasherTypeList)))
	  WasherPLM (search_washer_PLM (nth 0(nth WasherIndex WasherTypeList)))
	  WasherTypeNum (rtos(nth 1 (nth WasherIndex WasherTypeList)) 2 0))
        (setq washer_zb (list (* 806 mscale) (+ (* 50 mscale) (* (atoi Index) 13 mscale)) 0))


        (setq Index(strcat "\""(rtos iWasher)"\"")
	  WasherIndex k
	  WasherTypeName(strcat preWasherName(nth 0(nth WasherIndex WasherTypeList))"\"")
	  WasherTypeNameEN(strcat preWasherNameEN(nth 0(nth WasherIndex WasherTypeList))"\"")
	  WasherPLM(strcat "\""(search_washer_PLM (nth 0(nth WasherIndex WasherTypeList)))"\"")
	  WasherTypeNum(strcat "\""(rtos(nth 1(nth WasherIndex WasherTypeList))2 0)"\"")
	  WasherTypeBom(strcat Index "	";*
			       Index "	";序号
			       "\"出图\"" "	";是否出图
			       WasherPLM "	";代号
			       "\"           \"" "	";版本
			       WasherTypeName "	";名称
			       WasherTypeNum "	";数量
			       "\"           \"" "	";材料
			       "\"\"" "	";单重
			       "\"\"" "	";总重
			       Anticorrosion "	";备注
			       "\"           \"" "	";DRAWINGNUMBER
			       WasherTypeNameEN "	";NAME
			       "\"           \"" "	";MATERIAL
			       AnticorrosionEN "	";NOTES
			       "\"自制零件\"" "	";零件类型
			       "\"显示\"" "	");显示状态
        )
        (write-line WasherTypeBom BomListTxt)
	

        ;(zq_block_insert washer_zb Index WasherPLM WasherTypeName WasherTypeNameEN WasherTypeNum Anticorrosion AnticorrosionEN "" "" 0.44 0.62)
	
        (setq ibolt (+ 3 ibolt))
      );End progn
      (progn;如果列表中螺栓和垫片不匹配
        (setq Index (rtos ibolt)
	  BoltIndex  k
	  BoltLengthTypeName (strcat preBoltName (nth 0 (nth BoltIndex BoltLengthTypeList)))
	  BoltLengthTypeNameEN (strcat preBoltNameEN (nth 0 (nth BoltIndex BoltLengthTypeList)))
	  BoltLengthTypeNum (rtos (nth 1 (nth BoltIndex BoltLengthTypeList)) 2 0)
	  BoltPLM (search_bolt_PLM (nth 0 (nth BoltIndex BoltLengthTypeList))))
        ;螺栓明细表
        (setq bolt_zb (list (* 806 mscale) (+ (* 50 mscale) (* (atoi Index) 13 mscale)) 0))
	;(zq_block_insert bolt_zb Index BoltPLM BoltLengthTypeName BoltLengthTypeNameEN BoltLengthTypeNum Anticorrosion AnticorrosionEN "" "" 0.44 0.62)

	;螺栓明细表;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
        (setq Index (strcat "\""(rtos ibolt)"\"")
	  BoltIndex k
	  BoltLengthTypeName(strcat preBoltName(nth 0(nth BoltIndex BoltLengthTypeList))"\"")
	  BoltLengthTypeNameEN(strcat preBoltNameEN(nth 0(nth BoltIndex BoltLengthTypeList))"\"")
	  BoltLengthTypeNum(strcat "\""(rtos(nth 1(nth BoltIndex BoltLengthTypeList))2 0)"\"")
	  BoltPLM(strcat "\""(search_bolt_PLM (nth 0(nth BoltIndex BoltLengthTypeList)))"\"")
	  BoltLengthTypeBom(strcat Index "	";*
				   Index "	";序号
				   "\"出图\"" "	";是否出图
				   BoltPLM "	";代号
				   "\"           \"" "	";版本
				   BoltLengthTypeName "	";名称
				   BoltLengthTypeNum "	";数量
				   "\"           \"" "	";材料
				   "\"\"" "	";单重
				   "\"\"" "	";总重
				   Anticorrosion "	";备注
				   "\"           \"" "	";DRAWINGNUMBER
				   BoltLengthTypeNameEN "	";NAME
				   "\"           \"" "	";MATERIAL
				   AnticorrosionEN "	";NOTES
				   "\"自制零件\"" "	";零件类型
				   "\"显示\"" "	");显示状态
	)
        (write-line BoltLengthTypeBom BomListTxt)
        ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
	
        (setq ibolt (+ 1 ibolt))
      );End progn
    );End if
    (setq k (+ k 1))
  );End while

;顶法兰螺栓bom;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  (setq xIndex (+ ibolt 1));
  (setq Index (strcat "\"" (rtos ibolt) "\""))
   (cond 
		((= topfltype "V12_TopFlange")
			(progn
				(setq Topbolt_Bom (strcat Index "	";*
					Index "	";序号
					"\"出图\"" "	";是否出图
					"\"50.15.00644\"" "	";代号
					"\"           \"" "	";版本
					"\"偏航塔架连接模块\"" "	";名称
					"\"1\"" "	";数量
					"\"           \"" "	";材料
					"\"\"" "	";单重
					"\"\"" "	";总重
					"\"   \"" "	";备注
					"\"           \"" "	";DRAWINGNUMBER
					"\"Yaw and tower top connection\"" "	";NAME
					"\"           \"" "	";MATERIAL
					"\"    \"" "	";NOTES
					"\"自制零件\"" "	";零件类型
					"\"显示\"" "	"));显示状态)
				(write-line Topbolt_Bom BomListTxt)
			)
		)
		((= topfltype "V12_TopFlange_250")
			(progn
				(setq Topbolt_Bom (strcat Index "	";*
					Index "	";序号
					"\"出图\"" "	";是否出图
					"\"50.15.0828\"" "	";代号
					"\"           \"" "	";版本
					"\"偏航塔架连接模块\"" "	";名称
					"\"1\"" "	";数量
					"\"           \"" "	";材料
					"\"\"" "	";单重
					"\"\"" "	";总重
					"\"   \"" "	";备注
					"\"           \"" "	";DRAWINGNUMBER
					"\"Yaw and tower top connection\"" "	";NAME
					"\"           \"" "	";MATERIAL
					"\"    \"" "	";NOTES
					"\"自制零件\"" "	";零件类型
					"\"显示\"" "	"));显示状态)
				(write-line Topbolt_Bom BomListTxt)
			)
		)
		((= topfltype "V15_TopFlange")
			(progn
				(setq Topbolt_Bom (strcat Index "	";*
					Index "	";序号
					"\"出图\"" "	";是否出图
					"\"50.15.00875\"" "	";代号
					"\"           \"" "	";版本
					"\"偏航塔架连接模块\"" "	";名称
					"\"1\"" "	";数量
					"\"           \"" "	";材料
					"\"\"" "	";单重
					"\"\"" "	";总重
					"\"   \"" "	";备注
					"\"           \"" "	";DRAWINGNUMBER
					"\"Yaw and tower top connection\"" "	";NAME
					"\"           \"" "	";MATERIAL
					"\"    \"" "	";NOTES
					"\"自制零件\"" "	";零件类型
					"\"显示\"" "	"));显示状态)
				(write-line Topbolt_Bom BomListTxt)
			)
		)
		((= topfltype "V15_TopFlange_250")
			(progn
				(setq Topbolt_Bom (strcat Index "	";*
					Index "	";序号
					"\"出图\"" "	";是否出图
					"\"50.15.00874\"" "	";代号
					"\"           \"" "	";版本
					"\"偏航塔架连接模块\"" "	";名称
					"\"1\"" "	";数量
					"\"           \"" "	";材料
					"\"\"" "	";单重
					"\"\"" "	";总重
					"\"   \"" "	";备注
					"\"           \"" "	";DRAWINGNUMBER
					"\"Yaw and tower top connection\"" "	";NAME
					"\"           \"" "	";MATERIAL
					"\"    \"" "	";NOTES
					"\"自制零件\"" "	";零件类型
					"\"显示\"" "	"));显示状态)
				(write-line Topbolt_Bom BomListTxt)
			)
		)
		((= topfltype "V17_TopFlange_400")
			(progn
				(setq Topbolt_Bom (strcat Index "	";*
					Index "	";序号
					"\"出图\"" "	";是否出图
					"\"50.15.00934\"" "	";代号
					"\"           \"" "	";版本
					"\"偏航塔架连接模块\"" "	";名称
					"\"1\"" "	";数量
					"\"           \"" "	";材料
					"\"\"" "	";单重
					"\"\"" "	";总重
					"\"   \"" "	";备注
					"\"           \"" "	";DRAWINGNUMBER
					"\"Yaw and tower top connection\"" "	";NAME
					"\"           \"" "	";MATERIAL
					"\"    \"" "	";NOTES
					"\"自制零件\"" "	";零件类型
					"\"显示\"" "	"));显示状态)
				(write-line Topbolt_Bom BomListTxt)
			)
		)
   )
  (print "螺栓螺母垫片写入明细表成功！")
  
  
  ; ;螺栓，螺母，垫片bom;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  ; (setq ibolt (+ xIndex 1))  ;如果有阻尼器，在前面中已判断加过两行，此处仅+1行
  ; ;(print i)
  ; ;(SetBoltNum retFlange Sum_flange)  ;统计螺栓、螺母、垫圈数量
  ; (SetBoltNum2 retFlange Sum_FLange)
  ; ;;;  (print (strcat"所有螺栓规格"(rtos(length FastenerListList))))
  ; ;;;  (print FastenerListList)
  ; ;;;  (print (strcat"螺栓长度："(rtos(length BoltLengthTypeList))))
  ; ;;;  (print BoltLengthTypeList)
  ; ;;;  (print (strcat"螺母规格："(rtos(length NutTypeList))))
  ; ;;;  (print NutTypeList)
  ; ;;;  (print (strcat"垫圈规格"(rtos(length washerTypeList))))
  ; ;;;  (print washerTypeList)
  ; (setq NumSection ibolt)
  ; (while(< ibolt (+ NumSection (- (length BoltLengthTypeList) 1)))
    ; (setq Index (strcat "\""(rtos ibolt)"\"")
	  ; BoltIndex(- ibolt (- NumSection 1))
	  ; BoltLengthTypeName(strcat preBoltName(nth 0(nth BoltIndex BoltLengthTypeList))"\"")
	  ; BoltLengthTypeNameEN(strcat preBoltNameEN(nth 0(nth BoltIndex BoltLengthTypeList))"\"")
	  ; BoltLengthTypeNum(strcat "\""(rtos(nth 1(nth BoltIndex BoltLengthTypeList))2 0)"\"")
	  ; BoltPLM(strcat "\""(search_bolt_PLM (nth 0(nth BoltIndex BoltLengthTypeList)))"\"")
	  ; BoltLengthTypeBom(strcat Index "	";*
				   ; Index "	";序号
				   ; "\"出图\"" "	";是否出图
				   ; BoltPLM "	";代号
				   ; "\"           \"" "	";版本
				   ; BoltLengthTypeName "	";名称
				   ; BoltLengthTypeNum "	";数量
				   ; "\"           \"" "	";材料
				   ; "\"\"" "	";单重
				   ; "\"\"" "	";总重
				   ; Anticorrosion "	";备注
				   ; "\"           \"" "	";DRAWINGNUMBER
				   ; BoltLengthTypeNameEN "	";NAME
				   ; "\"           \"" "	";MATERIAL
				   ; AnticorrosionEN "	";NOTES
				   ; "\"自制零件\"" "	";零件类型
				   ; "\"显示\"" "	");显示状态
	  ; ibolt (+ 1 ibolt))
    ; (write-line BoltLengthTypeBom BomListTxt)
    ; )
  ; ;螺母
  ; (setq iNut ibolt)
  ; (setq EndNoBolt iNut)
  ; (while(< iNut (+ EndNoBolt (-(length NutTypeList)1)))
    ; (setq Index(strcat "\""(rtos iNut)"\"")
	  ; NutIndex(- iNut (- EndNoBolt 1))
	  ; NutTypeName(strcat preNutName(nth 0(nth NutIndex NutTypeList))"\"")
	  ; NutTypeNameEN(strcat preNutNameEN(nth 0(nth NutIndex NutTypeList))"\"")
	  ; NutPLM(strcat "\""(search_nut_PLM (nth 0(nth NutIndex NutTypeList)))"\"")
	  ; NutTypeNum(strcat "\""(rtos(nth 1(nth NutIndex NutTypeList))2 0)"\"")
	  ; NutTypeBom(strcat Index "	";*
			    ; Index "	";序号
			    ; "\"出图\"" "	";是否出图
			    ; NutPLM "	";代号
			    ; "\"           \"" "	";版本
			    ; NutTypeName "	";名称
			    ; NutTypeNum "	";数量
			    ; "\"           \"" "	";材料
			    ; "\"\"" "	";单重
			    ; "\"\"" "	";总重
			    ; Anticorrosion "	";备注
			    ; "\"           \"" "	";DRAWINGNUMBER
			    ; NutTypeNameEN "	";NAME
			    ; "\"           \"" "	";MATERIAL
			    ; AnticorrosionEN "	";NOTES
			    ; "\"自制零件\"" "	";零件类型
			    ; "\"显示\"" "	");显示状态
	  ; iNut (+ 1 iNut))
    ; (write-line NutTypeBom BomListTxt)
  ; )
  ; ;垫圈
  ; (setq iWasher iNut)
  ; (setq EndNoNut iWasher)
  ; (while(< iWasher (+ EndNoNut (-(length WasherTypeList)1)))
    ; (setq Index(strcat "\""(rtos iWasher)"\"")
	  ; WasherIndex(- iWasher (- EndNoNut 1))
	  ; WasherTypeName(strcat preWasherName(nth 0(nth WasherIndex WasherTypeList))"\"")
	  ; WasherTypeNameEN(strcat preWasherNameEN(nth 0(nth WasherIndex WasherTypeList))"\"")
	  ; WasherPLM(strcat "\""(search_washer_PLM (nth 0(nth WasherIndex WasherTypeList)))"\"")
	  ; WasherTypeNum(strcat "\""(rtos(nth 1(nth WasherIndex WasherTypeList))2 0)"\"")
	  ; WasherTypeBom(strcat Index "	";*
			       ; Index "	";序号
			       ; "\"出图\"" "	";是否出图
			       ; WasherPLM "	";代号
			       ; "\"           \"" "	";版本
			       ; WasherTypeName "	";名称
			       ; WasherTypeNum "	";数量
			       ; "\"           \"" "	";材料
			       ; "\"\"" "	";单重
			       ; "\"\"" "	";总重
			       ; Anticorrosion "	";备注
			       ; "\"           \"" "	";DRAWINGNUMBER
			       ; WasherTypeNameEN "	";NAME
			       ; "\"           \"" "	";MATERIAL
			       ; AnticorrosionEN "	";NOTES
			       ; "\"自制零件\"" "	";零件类型
			       ; "\"显示\"" "	");显示状态
	  ; iWasher (+ 1 iWasher))
    ; (write-line WasherTypeBom BomListTxt)
  ; )
  
  ;根据情况判断是否增加的附件bom
  ;V12分片塔增加bom-（短尾铆钉及套环&传感器安装板）;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  (if (and (or(= topfltype "V12_TopFlange")(= topfltype "V12_TopFlange_250")) (= TheTowerSliceType "SliceTower"))
    (progn 
	;1-短尾铆钉及套环bom
	  (setq count (+ count 1))
	  (setq Index(strcat "\""(rtos count)"\""))
	  (setq revit_num(strcat "\""(rtos (Rivet_judge))"\""))
	  (setq AttachBom(strcat Index "	";*
		 	       Index "	";序号
			       "\"出图\"" "	";是否出图
			       "\"60.10.86216\"" "	";代号
			       "\"           \"" "	";版本
			       "\"短尾铆钉及套环\"" "	";名称
			       revit_num "	";数量
			       "\"           \"" "	";材料
			       "\"\"" "	";单重
			       "\"\"" "	";总重
			       "\"型号和厂家见技术要求\"" "	";备注
			       "\"           \"" "	";DRAWINGNUMBER
			       "\"Rivet\"" "	";NAME
			       "\"           \"" "	";MATERIAL
			       "\"           \"" "	";NOTES
			       "\"自制零件\"" "	";零件类型
			       "\"显示\"" "	");显示状态
      ) 
	  (write-line AttachBom BomListTxt)
	 
	;2-传感器安装板bom
	  (setq count (+ count 1))
	  (setq Index(strcat "\""(rtos count)"\""))
	  (setq AttachBom(strcat Index "	";*
		 	       Index "	";序号
			       "\"出图\"" "	";是否出图
			       "\"60.13.05172\"" "	";代号
			       "\"           \"" "	";版本
			       "\"传感器安装板\"" "	";名称
			       "\"1          \"" "	";数量
			       "\"Q355/C/D/E \"" "	";材料
			       "\"0.75\"" "	";单重
			       "\"0.75\"" "	";总重
			       "\"           \"" "	";备注
			       "\"           \"" "	";DRAWINGNUMBER
			       "\"Support\"" "	";NAME
			       "\"S355J0/J2/NL\"" "	";MATERIAL
			       "\"           \"" "	";NOTES
			       "\"自制零件\"" "	";零件类型
			       "\"显示\"" "	");显示状态
      ) 
	  (write-line AttachBom BomListTxt)  
	
	);end progn
  );end if
  
  ;阻尼器bom;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  ;对塔架净高度>125m的塔架，采用阻尼器，明细表中增加两行
  (if (> (- (value retFlange section_qty 0) (value retFlange 0 0)) 125) 
    (cond
      ( (= topfltype "21_TopFlange");21#阻尼器
	    (progn 
             (setq count (+ count 1))
	         (setq Index(strcat "\""(rtos count)"\""))
			 (setq AttachBom(strcat Index "	";*
			       Index "	";序号
			       "\"出图\"" "	";是否出图
			       "\"           \"" "	";代号
			       "\"           \"" "	";版本
			       "\"阻尼器及辅材模块-4×2液体阻尼器\"" "	";名称
			       "\"4\"" "	";数量
			       "\"           \"" "	";材料
			       "\"\"" "	";单重
			       "\"\"" "	";总重
			       "\"采购\"" "	";备注
			       "\"           \"" "	";DRAWINGNUMBER
			       "\"GW MTLD4×2\"" "	";NAME
			       "\"           \"" "	";MATERIAL
			       "\"Purchasing\"" "	";NOTES
			       "\"自制零件\"" "	";零件类型
			       "\"显示\"" "	"));显示状态
	         (write-line AttachBom BomListTxt)
	    );end progn))
      )
	  ( (or (= topfltype "5S_TopFlange") (= topfltype "5X_TopFlange") (= topfltype "V12_TopFlange")(= topfltype "V12_TopFlange_250"));V12\5S\5H阻尼器
	    (progn 
             (setq count (+ count 1))
	         (setq Index(strcat "\""(rtos count)"\""))
	         (setq AttachBom(strcat Index "	";*
			       Index "	";序号
			       "\"出图\"" "	";是否出图
			       "\"60.10.85430\"" "	";代号
			       "\"           \"" "	";版本
			       "\"阻尼器及辅材模块-4×2液体阻尼器(150mm)\"" "	";名称
			       "\"1\"" "	";数量
			       "\"           \"" "	";材料
			       "\"\"" "	";单重
			       "\"\"" "	";总重
			       "\"采购\"" "	";备注
			       "\"           \"" "	";DRAWINGNUMBER
			       "\"GW HMTLD\"" "	";NAME
			       "\"           \"" "	";MATERIAL
			       "\"Purchasing\"" "	";NOTES
			       "\"自制零件\"" "	";零件类型
			       "\"显示\"" "	"));显示状态
	         (write-line AttachBom BomListTxt)
	    );end progn
      );
	  (  t
        (progn 
		    ; 冷却液
             (setq count (+ count 1))
	         (setq Index(strcat "\""(rtos count)"\""))
	         (setq AttachBom(strcat Index "	";*
			       Index "	";序号
			       "\"出图\"" "	";是否出图
			       "\"           \"" "	";代号
			       "\"           \"" "	";版本
			       "\"塔架液体阻尼器阻尼液（塑料桶装）\"" "	";名称
			       "\"50\"" "	";数量
			       "\"           \"" "	";材料
			       "\"\"" "	";单重
			       "\"\"" "	";总重
			       "\"采购\"" "	";备注
			       "\"           \"" "	";DRAWINGNUMBER
			       "\"Coolant XEG-Ⅱ 20L/Barrel\"" "	";NAME
			       "\"           \"" "	";MATERIAL
			       "\"Purchasing\"" "	";NOTES
			       "\"自制零件\"" "	";零件类型
			       "\"显示\"" "	"));显示状态
	         (write-line AttachBom BomListTxt)
	        ; 液体阻尼器
             (setq count (+ count 1))
	         (setq Index(strcat "\""(rtos count)"\""))
	         (setq AttachBom(strcat Index "	";*
		 	       Index "	";序号
			       "\"出图\"" "	";是否出图
			       "\"           \"" "	";代号
			       "\"           \"" "	";版本
			       "\"液体阻尼器\"" "	";名称
			       "\"10\"" "	";数量
			       "\"           \"" "	";材料
			       "\"\"" "	";单重
			       "\"\"" "	";总重
			       "\"采购\"" "	";备注
			       "\"           \"" "	";DRAWINGNUMBER
			       "\"TLD\"" "	";NAME
			       "\"           \"" "	";MATERIAL
			       "\"Purchasing\"" "	";NOTES
			       "\"自制零件\"" "	";零件类型
			       "\"显示\"" "	");显示状态
	         )
	         (write-line AttachBom BomListTxt)
	    );end progn
	  );end t   
    );end cond
  );end if 
  
  (close BomListTxt)
  (print "明细表成功生成！")
  (prin1)
)
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;end boomlist1;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;*********************************2.x总图明细表文本导出***********************/
(defun BomList2(TowerExcelFile SectionMassList FlangeMassList stair_mass Sum_FLange MassDoorFrame MassOfTowerDoorHole
	       / i preBomName BomTitle BomListTxt SectionName BoltLengthTypeBom NutTypeBom WasherTypeBom FastenerListList staircode count weldcode)
;(defun BomList(TowerExcelFile SectionMassList FlangeMassList stair_mass MassDoorFrame MassOfTowerDoorHole
	       ;/ preBomName BomTitle BomListTxt SectionName BoltLengthTypeBom NutTypeBom WasherTypeBom FastenerListList)
  (setq Bom_insert_point (list (* 806 mscale) (* 50 mscale) 0))	  ;明细表插入点
  (if (/= is_alert T)
    (command "insert" "one_key_mxb_title" "S" mscale Bom_insert_point 0 "") ;插入明细表表头
  )
  (setq preBomName (GetDir TowerExcelFile));明细表的名称
  ;明细表表头写入
  (setq BomTitle (strcat "\"*\"" "	"
			 "\"序号\"" "	"
			 "\"是否出图\"" "	"
			 "\"代号\"" "	"
			 "\"版本\"" "	"
			 "\"名称\"" "	"
			 "\"数量\"" "	"
			 "\"材料\"" "	"
			 "\"单重\"" "	"
			 "\"总重\"" "	"
			 "\"备注\"" "	"
			 "\"DRAWINGNUMBER\"" "	"
			 "\"NAME\"" "	"
			 "\"MATERIAL\"" "	"
			 "\"NOTES\"" "	"
			 "\"零件类型\"" "	"
			 "\"显示状态\"" "	")
	SectionIndex (list "\"第一" "\"第二" "\"第三" "\"第四" "\"第五" "\"第六" "\"第七" "\"第八" "\"第九" "\"第十" "\"第十一" "\"第十二")
	SectionIndexEN (list "Ⅰ " "Ⅱ " "Ⅲ " "Ⅳ " "Ⅴ " "Ⅵ " "Ⅶ " "Ⅷ " "Ⅸ " "Ⅹ " "Ⅺ " "Ⅻ "))
  (setq Anticorrosion "\"达克罗\""
	AnticorrosionEN "\"Dacromet\""
	preBoltName "\"螺栓GB/T5782-"
	preBoltNameEN "\"Bolt ISO4014-"
	preNutName "\"螺母GB/T6170-"
	preNutNameEN "\"Nut ISO4032-"
	preWasherName "\"垫圈EN14399-6-"
	preWasherNameEN "\"Washer EN14399-6-")
  ;(if zongtu;判断是不是出总成图
    ;(setq sufSectionName "段塔筒附件总成\"")
    ;(setq sufSectionName "段塔筒焊合\"")
  ;)
  (setq sufSectionName "段塔筒附件总成\"")
  (setq sufWeldName "段塔筒焊合\"")
  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  
  (setq BomListTxt (open (strcat preBomName "txt") "w"))
  (write-line BomTitle BomListTxt);把BomTitle写到BomListTxt
  (setq all_tower_mass 0.0)  ;设置初始塔架重量为0
  (setq count 0);计数
  ;基础环/塔架底座  
  ;(setq embedded_is_exit 0)
  (if (= (EmbeddedOrNot) T);如果是基础环
    (progn
      (setq count 1)
      (setq Index (strcat "\"" (rtos count) "\""));把两个字符串合并成一个字符并返回，返回 \"1\" ；rots四舍五入，
      (setq embedded_mass_str (strcat "\"" (rtos mass_of_embedded 2 0) "\""));返回重量
      (setq embeddedBom (strcat Index "	"
				Index "	"
				"\"出图\"" "	"
				"\"           \"" "	"
				"\"           \"" "	"
				"\"塔架底座总成\"" "	"
				"\"1\"" "	"
				"\"           \"" "	"
				embedded_mass_str "	"
				embedded_mass_str "	"
				"\"           \"" "	"
				"\"           \"" "	"
				"\"Tower embedded can assembly\"" "	"
				"\"           \"" "	"
				"\"           \"" "	"
				"\"自制零件\"" "	"
				"\"显示\"" "	"))
      (write-line embeddedBom BomListTxt)
      (print "基础环写入明细表成功！")
      (setq all_tower_mass (+ all_tower_mass mass_of_embedded))  ;累加重量
    )
  );基础环if的右括号，END基础环
  ;外爬梯bom;;;;;;;;
  (setq Index (strcat "\"" (rtos (+ count 1)) "\""));基础环的序号，如果有基础环，count为1，如果没有count为0
  (if (= stair_mass 0)
    (setq stair_mass_str (strcat "\"请填写重量！\""))
    (setq stair_mass_str (strcat "\"" (rtos stair_mass) "\""))
  )
  (setq all_tower_mass (+ all_tower_mass stair_mass))  ;累加重量
  (setq staircode "\"60.08.02697\"")
  (setq StairBom(strcat Index "	"
			Index "	"
			"\"出图\"" "	"
			staircode "	";梯子物料号
			"\"           \"" "	"
			"\"塔架入口梯子总成\"" "	"
			"\"1\"" "	"
			"\"           \"" "	"
			stair_mass_str "	"
			stair_mass_str "	"
			"\"           \"" "	"
			"\"           \"" "	"
			"\"Entrance stair assembly\"" "	"
			"\"           \"" "	"
			"\"           \"" "	"
			"\"自制零件\"" "	"
			"\"显示\"" "	"))
  (write-line StairBom BomListTxt)
  (print "外爬梯写入明细表成功！")
  ;底平台围边bom;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  (setq Index (strcat "\"" (rtos (+ count 2)) "\""))
  (setq EdgeBom (strcat Index "	";*
			Index "	";序号
			"\"出图\"" "	";是否出图
			"\"        \"" "	";代号
			"\"           \"" "	";版本
			"\"底平台围边\"" "	";名称
			"\"1\"" "	";数量
			"\"           \"" "	";材料
			"\"\"" "	";单重
			"\"\"" "	";总重
			"\"        \"" "	";备注
			"\"           \"" "	";DRAWINGNUMBER
			"\"Bottom platform surrounding edge\"" "	";NAME
			"\"           \"" "	";MATERIAL
			"\"        \"" "	";NOTES
			"\"自制零件\"" "	";零件类型
			"\"显示\"" "	"));显示状态
  (write-line EdgeBom BomListTxt)
  (print "底平台围边写入明细表成功！")
  ;升降机bom;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  (setq Index (strcat "\"" (rtos (+ count 3)) "\""))
  (if (or (= topfltype "2.xMW_TopFlange") (= topfltype "2MW_TopFlange"))
    (setq StairBom (strcat Index "	";*
			Index "	";序号
			"\"出图\"" "	";是否出图
			"\"60.10.04003\"" "	";代号
			"\"           \"" "	";版本
			"\"钢丝绳导向正面右侧开门塔筒升降机\"" "	";名称
			"\"1\"" "	";数量
			"\"           \"" "	";材料
			"\"\"" "	";单重
			"\"\"" "	";总重
			"\"采购\"" "	";备注
			"\"           \"" "	";DRAWINGNUMBER
			"\"Wire guided lift with righted door\"" "	";NAME
			"\"           \"" "	";MATERIAL
			"\"Purchasing\"" "	";NOTES
			"\"自制零件\"" "	";零件类型
			"\"显示\"" "	"));显示状态
    (setq StairBom (strcat Index "	";*
			Index "	";序号
			"\"出图\"" "	";是否出图
			"\"           \"" "	";代号
			"\"           \"" "	";版本
			"\"升降机\"" "	";名称
			"\"1\"" "	";数量
			"\"           \"" "	";材料
			"\"\"" "	";单重
			"\"\"" "	";总重
			"\"采购\"" "	";备注
			"\"           \"" "	";DRAWINGNUMBER
			"\"Lift\"" "	";NAME
			"\"           \"" "	";MATERIAL
			"\"Purchasing\"" "	";NOTES
			"\"自制零件\"" "	";零件类型
			"\"显示\"" "	"));显示状态
  );end if;;;;;
  (write-line StairBom BomListTxt)
  (print "升降机写入明细表成功！")
  
  ;塔架段bom
  ;SectionIndex (list "\"第一" "\"第二" "\"第三" "\"第四" "\"第五" "\"第六" "\"第七" "\"第八" "\"第九" "\"第十" "\"第十一" "\"第十二")
  ;sufWeldName "段塔筒焊合\""
  ;(setq sufSectionName "段塔筒附件总成\"")
  ;SectionIndexEN (list "Ⅰ\"" "Ⅱ\"" "Ⅲ\"" "Ⅳ\"" "Ⅴ\"" "Ⅵ\"" "Ⅶ\"" "Ⅷ\"" "Ⅸ\"" "Ⅹ\"" "Ⅺ\"" "Ⅻ\"")
  (setq i 0)
  (setq seccount 0)
  (while (< i (length SectionMassList) );SectinMassList
    (setq WeldName (strcat (nth i SectionIndex) sufWeldName);把表SectionIndex的第i个元素与sufWeldName合并
	  WeldNameEN (strcat "\"Section " (nth i SectionIndexEN) "Welded\"")
	  section_mass (+ (nth i SectionMassList) (nth i FlangeMassList) (nth (+ i 1) FlangeMassList))
	  Index (strcat "\"" (rtos (+ i (+ count 4) seccount)) "\"")
    )
    (if (= i 0);如果是第一段
      (setq section_mass (+ section_mass MassDoorFrame (- MassOfTowerDoorHole) ))
    )
   
    (setq SectionNameEN (strcat "\"Section " (nth i SectionIndexEN) "Accessories\""))
    (setq SectionName (strcat (nth i SectionIndex) sufSectionName))
    (setq TowerSectionMass (strcat "\"" (rtos section_mass 2 0) "\""))
    (setq all_tower_mass (+ all_tower_mass section_mass))  ;累加重量
    (if (= i (- section_qty 1));如果是顶段
      (progn
	(setq WeldName "\"顶段塔筒焊合\""
	      WeldNameEN "\"Top Section Welded\"")
	(setq SectionName "\"顶段塔筒附件总成\""
	      SectionNameEN "\"Top Section Accessories\"")
      )
    )
    (setq weldcode (strcat "\"" (value (nth i retAidlist) 1 0) "\""));焊合物料号
    ;(print weldcode)
    (setq WeldBom(strcat Index "	"
			     Index "	"
			     "\"出图\"" "	"
			     weldcode "	"
			     "\"           \"" "	"
			     WeldName "	"
			     "\"1\"" "	"
			     "\"           \"" "	"
			     TowerSectionMass "	"
			     TowerSectionMass "	"
			     "\"           \"" "	"
			     "\"           \"" "	"
			     WeldNameEN "	"
			     "\"           \"" "	"
			     "\"           \"" "	"
			     "\"自制零件\"" "	"
			     "\"显示\"" "	"))

    (write-line WeldBom BomListTxt)
    (setq secIndex (strcat "\"" (rtos (+ i (+ count 5)  seccount)) "\""))
    (setq sectioncode (strcat "\"" (value (nth i retAidlist) 0 0) "\""));塔筒附件总成物料号
    ;(print sectioncode)
    (setq  SectionBom(strcat secIndex "	"
			     secIndex "	"
			     "\"出图\"" "	"
			     sectioncode "	"
			     "\"           \"" "	"
			     SectionName "	"
			     "\"1\"" "	"
			     "\"           \"" "	"
			     TowerSectionMass "	"
			     TowerSectionMass "	"
			     "\"           \"" "	"
			     "\"           \"" "	"
			     SectionNameEN "	"
			     "\"           \"" "	"
			     "\"           \"" "	"
			     "\"自制零件\"" "	"
			     "\"显示\"" "	"))
    (write-line SectionBom BomListTxt)
    (setq seccount (+ seccount 1))
    (setq i (+ 1 i))
    ;(print i)
  )
  (print "塔筒段写入明细表成功！")

  ;螺栓，螺母，垫片bom;;;;;;;;;;;;
  (setq xIndex (+ count 3 (* 2 section_qty)))
  (setq ibolt (+ xIndex 1))  ;如果有阻尼器，在前面中已判断加过两行，此处仅+1行
  ;(print i)
  ;(SetBoltNum retFlange Sum_flange)  ;统计螺栓、螺母、垫圈数量
  (SetBoltNum2 retFlange Sum_FLange)
  ;;;  (print (strcat"所有螺栓规格"(rtos(length FastenerListList))))
  ;;;  (print FastenerListList)
  ;;;  (print (strcat"螺栓长度："(rtos(length BoltLengthTypeList))))
  ;;;  (print BoltLengthTypeList)
  ;;;  (print (strcat"螺母规格："(rtos(length NutTypeList))))
  ;;;  (print NutTypeList)
  ;;;  (print (strcat"垫圈规格"(rtos(length washerTypeList))))
  ;;;  (print washerTypeList)
  (setq NumSection ibolt)
  
  (while(< ibolt (+ NumSection (-(length BoltLengthTypeList)1)))
    (setq Index (strcat "\""(rtos ibolt)"\"")
	  BoltIndex(- ibolt (- NumSection 1))
	  BoltLengthTypeName(strcat preBoltName(nth 0(nth BoltIndex BoltLengthTypeList))"\"")
	  BoltLengthTypeNameEN(strcat preBoltNameEN(nth 0(nth BoltIndex BoltLengthTypeList))"\"")
	  BoltLengthTypeNum(strcat "\""(rtos(nth 1(nth BoltIndex BoltLengthTypeList))2 0)"\"")
	  BoltPLM(strcat "\""(search_bolt_PLM (nth 0(nth BoltIndex BoltLengthTypeList)))"\"")
	  BoltLengthTypeBom(strcat Index "	";*
				   Index "	";序号
				   "\"出图\"" "	";是否出图
				   BoltPLM "	";代号
				   "\"           \"" "	";版本
				   BoltLengthTypeName "	";名称
				   BoltLengthTypeNum "	";数量
				   "\"           \"" "	";材料
				   "\"\"" "	";单重
				   "\"\"" "	";总重
				   Anticorrosion "	";备注
				   "\"           \"" "	";DRAWINGNUMBER
				   BoltLengthTypeNameEN "	";NAME
				   "\"           \"" "	";MATERIAL
				   AnticorrosionEN "	";NOTES
				   "\"自制零件\"" "	";零件类型
				   "\"显示\"" "	");显示状态
	  ibolt (+ 1 ibolt))
    (write-line BoltLengthTypeBom BomListTxt)
    )
  ;螺母
  (setq iNut ibolt)
  (setq EndNoBolt iNut)
  (while(< iNut (+ EndNoBolt (-(length NutTypeList)1)))
    (setq Index(strcat "\""(rtos iNut)"\"")
	  NutIndex(- iNut (- EndNoBolt 1))
	  NutTypeName(strcat preNutName(nth 0(nth NutIndex NutTypeList))"\"")
	  NutTypeNameEN(strcat preNutNameEN(nth 0(nth NutIndex NutTypeList))"\"")
	  NutPLM(strcat "\""(search_nut_PLM (nth 0(nth NutIndex NutTypeList)))"\"")
	  NutTypeNum(strcat "\""(rtos(nth 1(nth NutIndex NutTypeList))2 0)"\"")
	  NutTypeBom(strcat Index "	";*
			    Index "	";序号
			    "\"出图\"" "	";是否出图
			    NutPLM "	";代号
			    "\"           \"" "	";版本
			    NutTypeName "	";名称
			    NutTypeNum "	";数量
			    "\"           \"" "	";材料
			    "\"\"" "	";单重
			    "\"\"" "	";总重
			    Anticorrosion "	";备注
			    "\"           \"" "	";DRAWINGNUMBER
			    NutTypeNameEN "	";NAME
			    "\"           \"" "	";MATERIAL
			    AnticorrosionEN "	";NOTES
			    "\"自制零件\"" "	";零件类型
			    "\"显示\"" "	");显示状态
	  iNut (+ 1 iNut))
    (write-line NutTypeBom BomListTxt)
  )
  ;垫圈
  (setq iWasher iNut)
  (setq EndNoNut iWasher)
  (while(< iWasher (+ EndNoNut (-(length WasherTypeList)1)))
    (setq Index(strcat "\""(rtos iWasher)"\"")
	  WasherIndex(- iWasher (- EndNoNut 1))
	  WasherTypeName(strcat preWasherName(nth 0(nth WasherIndex WasherTypeList))"\"")
	  WasherTypeNameEN(strcat preWasherNameEN(nth 0(nth WasherIndex WasherTypeList))"\"")
	  WasherPLM(strcat "\""(search_washer_PLM (nth 0(nth WasherIndex WasherTypeList)))"\"")
	  WasherTypeNum(strcat "\""(rtos(nth 1(nth WasherIndex WasherTypeList))2 0)"\"")
	  WasherTypeBom(strcat Index "	";*
			       Index "	";序号
			       "\"出图\"" "	";是否出图
			       WasherPLM "	";代号
			       "\"           \"" "	";版本
			       WasherTypeName "	";名称
			       WasherTypeNum "	";数量
			       "\"           \"" "	";材料
			       "\"\"" "	";单重
			       "\"\"" "	";总重
			       Anticorrosion "	";备注
			       "\"           \"" "	";DRAWINGNUMBER
			       WasherTypeNameEN "	";NAME
			       "\"           \"" "	";MATERIAL
			       AnticorrosionEN "	";NOTES
			       "\"自制零件\"" "	";零件类型
			       "\"显示\"" "	");显示状态
	  iWasher (+ 1 iWasher))
    (write-line WasherTypeBom BomListTxt)
  )
  (print "螺栓螺母垫片写入明细表成功！")
  
  ;阻尼器bom;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  ;对塔架净高度>125m的塔架，采用阻尼器，明细表中增加两行
  (if (> (- (value retFlange section_qty 0) (value retFlange 0 0)) 125)
    (progn
	; 冷却液
        (setq xIndex (+ iWasher 1))
	(setq Index(strcat "\""(rtos xIndex)"\""))
	(setq AttachBom(strcat Index "	";*
			       Index "	";序号
			       "\"出图\"" "	";是否出图
			       "\"           \"" "	";代号
			       "\"           \"" "	";版本
			       "\"塔架液体阻尼器阻尼液（塑料桶装）\"" "	";名称
			       "\"50\"" "	";数量
			       "\"           \"" "	";材料
			       "\"\"" "	";单重
			       "\"\"" "	";总重
			       "\"采购\"" "	";备注
			       "\"           \"" "	";DRAWINGNUMBER
			       "\"Coolant XEG-Ⅱ 20L/Barrel\"" "	";NAME
			       "\"           \"" "	";MATERIAL
			       "\"Purchasing\"" "	";NOTES
			       "\"自制零件\"" "	";零件类型
			       "\"显示\"" "	"));显示状态
	(write-line AttachBom BomListTxt)
        (if (/= is_alert T)
          (command "insert" "tower_mxb" "S" mscale (polar Bom_insert_point(/ pi 2) (* 13 mscale xIndex)) 0
	      "" "" (rtos xIndex) "塔架液体阻尼器阻尼液（塑料桶装）" "30" "采购" "" "" "" "Coolant XEG-Ⅱ 20L/Barrelt" " " "Purchasing" "")

        )
	; 液体阻尼器
        (setq xIndex (+ xIndex 1))
	(setq Index(strcat "\""(rtos xIndex)"\""))
	(setq AttachBom(strcat Index "	";*
			       Index "	";序号
			       "\"出图\"" "	";是否出图
			       "\"           \"" "	";代号
			       "\"           \"" "	";版本
			       "\"液体阻尼器\"" "	";名称
			       "\"10\"" "	";数量
			       "\"           \"" "	";材料
			       "\"\"" "	";单重
			       "\"\"" "	";总重
			       "\"采购\"" "	";备注
			       "\"           \"" "	";DRAWINGNUMBER
			       "\"TLD\"" "	";NAME
			       "\"           \"" "	";MATERIAL
			       "\"Purchasing\"" "	";NOTES
			       "\"自制零件\"" "	";零件类型
			       "\"显示\"" "	");显示状态
	 )
	(write-line AttachBom BomListTxt)
    )
  )


  
  (close BomListTxt)
  (print "明细表成功生成！")
  (prin1)
)
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;end boomlist2;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


;*********************************2.x总图明细表文本导出***********************/
(defun BomList3(TowerExcelFile SectionMassList FlangeMassList stair_mass  MassDoorFrame MassOfTowerDoorHole
	       / i preBomName BomTitle BomListTxt SectionName BoltLengthTypeBom NutTypeBom WasherTypeBom FastenerListList staircode count weldcode
		nn k TowerAccMass)
  (setq Bom_insert_point (list (* 806 mscale) (* 50 mscale) 0))	  ;明细表插入点
  (command "insert" "PC_MXBTITLERECORD" "S" mscale Bom_insert_point "" "") ;插入明细表表头
  ;明细表表头写入
  (setq SectionIndex (list "第一" "第二" "第三" "第四" "第五" "第六" "第七" "第八" "第九" "第十" "第十一" "第十二")
	SectionIndexEN (list "Ⅰ " "Ⅱ " "Ⅲ " "Ⅳ " "Ⅴ " "Ⅵ " "Ⅶ " "Ⅷ " "Ⅸ " "Ⅹ " "Ⅺ " "Ⅻ "))
  (setq Anticorrosion "达克罗"
	AnticorrosionEN "Dacromet"
	preBoltName "螺栓GB/T5782-"
	preBoltNameEN "Bolt ISO4014-"
	preNutName "螺母GB/T6170-"
	preNutNameEN "Nut ISO4032-"
	preWasherName "垫圈EN14399-6-"
	preWasherNameEN "Washer EN14399-6-")
  (setq sufSectionName "段塔筒附件总成")
  (setq sufWeldName "段塔筒焊合")
  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  (setq all_tower_mass 0.0)  ;设置初始塔架重量为0
  (setq count 0);计数
  ;基础环/塔架底座  
  ;(setq embedded_is_exit 0)
  (if (= (EmbeddedOrNot) T);如果是基础环
    (progn
      (setq count 1)
      (setq Index  (rtos count) );把两个字符串合并成一个字符并返回，返回 \"1\" ；rots四舍五入，
      (setq embedded_mass_str  (rtos mass_of_embedded 2 0) );返回重量
      (setq all_tower_mass (+ all_tower_mass mass_of_embedded))  ;累加重量
      ;基础环底座bom;;;;;;;;
      (setq Index  (rtos count) );基础环的序号，如果有基础环，count为1，如果没有count为0
      (setq all_tower_mass (+ all_tower_mass stair_mass))  ;累加重量
      (setq embedded_zb (list (* 806 mscale) (+ (* 50 mscale) (* (atoi Index) 13 mscale)) 0))
      (command "insert" "pc_mxb_block" "S" mscale embedded_zb ""
	   "" "" "" "Tower embedded can assembly" "" embedded_mass_str embedded_mass_str "" "1" "塔架底座总成" Index ""
	   "" "" )
    )
  );基础环if的右括号，END基础环
  
  ;外爬梯bom;;;;;;;;
  (setq count (+ count 1))
  (setq Index  (rtos count) );基础环的序号，如果有基础环，count为1，如果没有count为0
  (setq all_tower_mass (+ all_tower_mass stair_mass))  ;累加重量
  (setq staircode "60.08.02697")
  ;块名称，S，比例，坐标，旋转角度,
  ;版本，NOTES，MATERIAL，NAME，DRAWINGNUMBER，总重，单重，备注，数量，名称，序号，代号
  ;材料
  ;明细表1
  (setq stair_zb (list (* 806 mscale) (+ (* 50 mscale) (* (atoi Index) 13 mscale)) 0))
  
  (command "insert" "pc_mxb_block" "S" mscale stair_zb ""
    "" "" "" "Entrance stair assembly" "" stair_mass stair_mass "" "1" "塔架入口梯子总成" Index staircode
    "")
  
  ;底平台围边bom
  (if (/= power "2.5")
    (progn
      (setq count (+ count 1))
      (setq Index  (rtos count) )
      (setq edge_zb (list (* 806 mscale) (+ (* 50 mscale) (* (atoi Index) 13 mscale)) 0))
      (setq edge_mass 74)
      (setq all_tower_mass (+ all_tower_mass edge_mass))  ;累加重量
      ;(command "insert" "pc_mxb_block_edge" "S" mscale edge_zb ""
      ;	   "" "" "" "Bottom platform surrounding edge" "" edge_mass edge_mass "" "1" "底平台围边" Index "60.10.50364"
      ;	   "" )
      (zq_block_insert edge_zb Index "60.10.50364" "底平台围边" "Bottom platform surrounding edge" "1" "" "" "74" "74" 0.67 0.53)
    )
  )
  ;升降机bom;;;;;
  (setq count (+ count 1))
  (setq Index (rtos count))
  (setq lift_zb (list (* 806 mscale) (+ (* 50 mscale) (* (atoi Index) 13 mscale)) 0))
  (if (or (= topfltype "2.xMW_TopFlange") (= topfltype "2MW_TopFlange"))
    ;(command "insert" "pc_mxb_block" "S" mscale lift_zb ""
    ;	   "" "Purchasing" "" "Wire guided lift with righted door" "" "" "" "采购" "1" "钢丝绳导向正面右侧开门塔筒升降机" Index "60.10.04003"
    ;  "" )
    (zq_block_insert lift_zb Index "60.10.04003" "钢丝绳导向正面右侧开门塔筒升降机" "Wire guided lift with righted door" "1" "采购" "Purchasing" "" "" 0.44 0.44)
    (command "insert" "pc_mxb_block" "S" mscale lift_zb ""
	   "" "Purchasing" "" "lift" "" "" "" "采购" "1" "塔筒升降机" Index ""
	   "" )
   
  );end if;;;;;

  
  ;塔架段bom
  (setq i 0)
  (setq count (+ count 1))
  (while (< i (length SectionMassList) );SectinMassList
    (setq Index (rtos (+ i count)))
    (setq WeldName (strcat (nth i SectionIndex) sufWeldName);把表SectionIndex的第i个元素与sufWeldName合并
	  WeldNameEN (strcat "Section " (nth i SectionIndexEN) "Welded")
	  weld_mass (+ (atof (rtos (nth i SectionMassList) 2 1)) (atof (rtos (nth i FlangeMassList) 2 1)) (atof (rtos (nth (+ i 1) FlangeMassList) 2 1)))
    )

    
    (if (= i 0);如果是第一段,重量
      (progn
        (setq weld_mass (+ weld_mass (atof (rtos MassDoorFrame 2 1)) (- (atof (rtos MassOfTowerDoorHole 2 1))) 1.8) )
      )
    );End if
    (setq weld_mass_str (rtos weld_mass 2 0))
    (setq weld_mass (atoi weld_mass_str))
    (setq all_tower_mass (+ all_tower_mass weld_mass))  ;累加重量

    
    (setq SectionNameEN (strcat "Section " (nth i SectionIndexEN) "Accessories"));附件总成英文名
    (setq SectionName (strcat (nth i SectionIndex) sufSectionName));附件总成中文名
    
    (if (= i (- section_qty 1));如果是顶段
      (progn
	(setq WeldName "顶段塔筒焊合"
	      WeldNameEN "Top Section Welded")
	(setq SectionName "顶段塔筒附件总成"
	      SectionNameEN "Top Section Accessories")
      )
    );END IF

    
    (setq weldcode (value (nth i retAidlist) 1 0));焊合物料号
    (setq weldcode (tydhnil weldcode));如果物料号为空返回60.
    
   
    (setq weld_zb (list (* 806 mscale) (+ (* 50 mscale) (* (atoi Index) 13 mscale)) 0))
    ;焊合明细表插入
    (command "insert" "pc_mxb_block" "S" mscale weld_zb ""
	   "" "" "" WeldNameEN "" weld_mass_str weld_mass_str "" "1" WeldName Index weldcode
	   "" )
    ;附件总成
    (setq secIndex  (rtos (+ 1 count i )))

    (setq sectioncode (value (nth i retAidlist) 0 0));塔筒附件总成物料号
    (setq sectioncode (tydhnil sectioncode));如果物料号为空返回60.
    (setq TowerAccMass (value (nth i retAidlist) 0 2));附件总成重量
    (setq TowerAccMass (weightnil TowerAccMass));如果重量为空返回0
    (setq TowerAccMass_str (rtos TowerAccMass));附件总成重量
    (setq all_tower_mass (+ all_tower_mass TowerAccMass))  ;累加重量
    (setq tower_ac_zb (list (* 806 mscale) (+ (* 50 mscale) (* (atoi secIndex) 13 mscale)) 0))
    
    (command "insert" "pc_mxb_block" "S" mscale tower_ac_zb ""
	   "" "" "" SectionNameEN "" TowerAccMass_str TowerAccMass_str "" "1" SectionName secIndex sectioncode
	   "" )

    (setq count (+ count 1))
    (setq i (+ 1 i))
  
  )
  (setq count (+ count i))
  (print "塔筒段写入明细表成功！") 
  
  ;螺栓，螺母，垫片bom;;;;;;;;;;;;
  ;(setq count (+ count 1))
  ;(setq xIndex count)
  ;(setq ibolt (+ xIndex 0)) ;第一个螺栓的序号
  (setq ibolt count) ;第一个螺栓的序号
  ;(SetBoltNum retFlange section_qty);统计螺栓、螺母、垫圈数量
  (SetBoltNum2 retFlange section_qty);统计螺栓、螺母、垫圈数量
  
  ;(print BoltLengthTypeList)
  ;(print NutTypeList)
  ;(print WasherTypeList)
  
  (setq BoltLengthTypeList (cdr BoltLengthTypeList));去掉第一个元素
  (setq NutTypeList (cdr NutTypeList))
  (setq WasherTypeList (cdr WasherTypeList))
  ;(print BoltLengthTypeList)
  ;(print NutTypeList)
  ;(print WasherTypeList)
  ;(setq znq_Index (+ (length BoltLengthTypeList) (length NutTypeList) (length WasherTypeList)));螺栓，螺母，垫片总的数量
  (setq count (+ count (length BoltLengthTypeList) (length NutTypeList) (length WasherTypeList)));螺栓，螺母，垫片总的数量



  ;处理螺栓螺母垫片表,给公用螺栓的加空列表
  (setq nn 1)
  (while (< nn (length BoltLengthTypeList))
    (setq bolt_m_1 (substr (nth 0 (nth nn BoltLengthTypeList)) 1 3 ))
    (if (= (nth nn NutTypeList) nil);如果为空
      (progn
        (setq NutTypeList (list_insert NutTypeList nn '("" "")))
        (setq WasherTypeList (list_insert WasherTypeList nn '("" "")))
      )
      (progn
        (setq nut_m_1  (substr (nth 0 (nth nn NutTypeList)) 1 3 ))
        (if (/= bolt_m_1 nut_m_1)
          (progn
            (setq NutTypeList (list_insert NutTypeList nn '("" "")))
            (setq WasherTypeList (list_insert WasherTypeList nn '("" "")))
          );End progn
        );End if
      );End progn
    );End if
    (setq nn (+ nn 1))
  )
  ;(print BoltLengthTypeList)
  ;(print NutTypeList)
  ;(print WasherTypeList)

  
  (setq k 0)
  (while(< k (length BoltLengthTypeList))
    (setq bolt_m_1  (substr (nth 0 (nth k BoltLengthTypeList)) 1 3 ))
    (if (= (nth k NutTypeList) nil)
      (setq nut_m_1 (substr (nth 0 (nth k BoltLengthTypeList)) 1 3 ))
      (setq nut_m_1  (substr (nth 0 (nth k NutTypeList)) 1 3 ))
    )
    
    ;(setq bolt_m_2 (substr (nth 0 (nth (+ k 1) BoltLengthTypeList)) 1 3 ))    
    ;(print bolt_m_1 )
    ;(print nut_m_1 )

    (if (= bolt_m_1 nut_m_1 )
      (progn;如果列表中螺栓和垫片匹配
        (setq Index (rtos ibolt)
	  BoltIndex  k
	  BoltLengthTypeName (strcat preBoltName (nth 0 (nth BoltIndex BoltLengthTypeList)))
	  BoltLengthTypeNameEN (strcat preBoltNameEN (nth 0 (nth BoltIndex BoltLengthTypeList)))
	  BoltLengthTypeNum (rtos (nth 1 (nth BoltIndex BoltLengthTypeList)) 2 0)
	  BoltPLM (search_bolt_PLM (nth 0 (nth BoltIndex BoltLengthTypeList))))
    
        ;螺栓明细表
        (setq bolt_zb (list (* 806 mscale) (+ (* 50 mscale) (* (atoi Index) 13 mscale)) 0))
        ;(command "insert" "pc_mxb_block2" "S" mscale bolt_zb ""
	;   "" AnticorrosionEN "" BoltLengthTypeNameEN "" "" "" Anticorrosion BoltLengthTypeNum BoltLengthTypeName Index BoltPLM
	;   "" )

	(zq_block_insert bolt_zb Index BoltPLM BoltLengthTypeName BoltLengthTypeNameEN BoltLengthTypeNum Anticorrosion AnticorrosionEN "" "" 0.44 0.62)
	
        ;螺母
        (setq iNut (+ ibolt 1))
        (setq EndNoBolt k)
        (setq Index (rtos iNut)
	  NutIndex k
	  NutTypeName(strcat preNutName (nth 0 (nth NutIndex NutTypeList)))
	  NutTypeNameEN(strcat preNutNameEN (nth 0 (nth NutIndex NutTypeList)) )
	  NutPLM (search_nut_PLM (nth 0 (nth NutIndex NutTypeList)) )
	  NutTypeNum (rtos (nth 1 (nth NutIndex NutTypeList)) 2 0))
        ;螺母明细表
        (setq nut_zb (list (* 806 mscale) (+ (* 50 mscale) (* (atoi Index) 13 mscale)) 0))
        ;(command "insert" "pc_mxb_block2" "S" mscale nut_zb ""
	;   "" AnticorrosionEN "" NutTypeNameEN "" "" "" Anticorrosion NutTypeNum NutTypeName Index NutPLM
	;   "" )

        (zq_block_insert nut_zb Index NutPLM NutTypeName NutTypeNameEN NutTypeNum Anticorrosion AnticorrosionEN "" "" 0.44 0.89)
	
        ;垫圈
        (setq iWasher (+ iNut 1))
        (setq EndNoNut k)
        (setq Index (rtos iWasher)
	  WasherIndex k
	  WasherTypeName (strcat preWasherName (nth 0 (nth WasherIndex WasherTypeList)))
	  WasherTypeNameEN (strcat preWasherNameEN (nth 0 (nth WasherIndex WasherTypeList)))
	  WasherPLM (search_washer_PLM (nth 0(nth WasherIndex WasherTypeList)))
	  WasherTypeNum (rtos(nth 1 (nth WasherIndex WasherTypeList)) 2 0))
        ;(setq iWasher (+ 1 iWasher))
        (setq washer_zb (list (* 806 mscale) (+ (* 50 mscale) (* (atoi Index) 13 mscale)) 0))
        ;(command "insert" "pc_mxb_block2" "S" mscale washer_zb ""
	;   "" AnticorrosionEN "" WasherTypeNameEN "" "" "" Anticorrosion WasherTypeNum WasherTypeName Index WasherPLM
	;   ""  )
        (zq_block_insert washer_zb Index WasherPLM WasherTypeName WasherTypeNameEN WasherTypeNum Anticorrosion AnticorrosionEN "" "" 0.44 0.62)
	
        (setq ibolt (+ 3 ibolt))
      );End progn
      (progn;如果列表中螺栓和垫片不匹配
        (setq Index (rtos ibolt)
	  BoltIndex  k
	  BoltLengthTypeName (strcat preBoltName (nth 0 (nth BoltIndex BoltLengthTypeList)))
	  BoltLengthTypeNameEN (strcat preBoltNameEN (nth 0 (nth BoltIndex BoltLengthTypeList)))
	  BoltLengthTypeNum (rtos (nth 1 (nth BoltIndex BoltLengthTypeList)) 2 0)
	  BoltPLM (search_bolt_PLM (nth 0 (nth BoltIndex BoltLengthTypeList))))
    
        ;螺栓明细表
        (setq bolt_zb (list (* 806 mscale) (+ (* 50 mscale) (* (atoi Index) 13 mscale)) 0))
        ;(command "insert" "pc_mxb_block2" "S" mscale bolt_zb ""
	;   "" AnticorrosionEN "" BoltLengthTypeNameEN "" "" "" Anticorrosion BoltLengthTypeNum BoltLengthTypeName Index BoltPLM
	;   "" )


	(zq_block_insert bolt_zb Index BoltPLM BoltLengthTypeName BoltLengthTypeNameEN BoltLengthTypeNum Anticorrosion AnticorrosionEN "" "" 0.44 0.62)
	
        (setq ibolt (+ 1 ibolt))
      );End progn
    );End if
    (setq k (+ k 1))
  );End while

  (print "螺栓螺母垫片写入明细表成功！")
  
  ;阻尼器bom;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  ;对塔架净高度>125m的塔架，采用阻尼器，明细表中增加两行

  (if (> (- (value retFlange section_qty 0) (value retFlange 0 0)) 125)
    (progn
	; 冷却液
        ;(setq xIndex (+ xIndex znq_Index))
	;(setq Index (rtos xIndex))
        (setq Index (rtos count))
        (setq Attach_zb (list (* 806 mscale) (+ (* 50 mscale) (* (atoi Index) 13 mscale)) 0))
        ;(command "insert" "pc_mxb_block2" "S" mscale Attach_zb ""
	;   "" "Purchasing" "" "Special liquid for Tower liquid damper" "" "" "" "采购" "33" "塔架液体阻尼器阻尼液（塑料桶装）" Index "6.0200.1761"
	;   "" )

        (zq_block_insert Attach_zb Index "6.0200.1761" "塔架液体阻尼器阻尼液（塑料桶装）" "Special liquid for Tower liquid damper" "50" "采购" "Purchasing" "" "" 0.44 0.44)
	; 液体阻尼器
        ;(setq xIndex (+ xIndex 1))
	;(setq Index (rtos xIndex))
        (setq count (+ count 1))
        (setq Index (rtos count))

        (setq Attach_zb (list (* 806 mscale) (+ (* 50 mscale) (* (atoi Index) 13 mscale)) 0))
        (command "insert" "pc_mxb_block" "S" mscale Attach_zb ""
	   "" "Purchasing" "" "TLD" "" "" "" "采购" "10" "液体阻尼器" Index "60.01.00937"
	   "" )

        
    )
  );End if
  (print "明细表成功插入！")
  (prin1)
)
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;end BomList3;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


;*********************************auto一键招标图明细表，针对有界面的程序***********************/
(defun BomList4(TowerExcelFile SectionMassList FlangeMassList stair_mass  MassDoorFrame MassOfTowerDoorHole
	       / i preBomName BomTitle BomListTxt SectionName BoltLengthTypeBom NutTypeBom WasherTypeBom FastenerListList staircode count weldcode
		nn k TowerAccMass)
  (setq Bom_insert_point (list (* 806 mscale) (* 50 mscale) 0))	  ;明细表插入点
  (command "insert" "PC_MXBTITLERECORD" "S" mscale Bom_insert_point "" "") ;插入明细表表头
  ;明细表表头写入
  (setq SectionIndex (list "第一" "第二" "第三" "第四" "第五" "第六" "第七" "第八" "第九" "第十" "第十一" "第十二")
	SectionIndexEN (list "Ⅰ " "Ⅱ " "Ⅲ " "Ⅳ " "Ⅴ " "Ⅵ " "Ⅶ " "Ⅷ " "Ⅸ " "Ⅹ " "Ⅺ " "Ⅻ "))
  (setq Anticorrosion "达克罗"
	AnticorrosionEN "Dacromet"
	preBoltName "螺栓GB/T5782-"
	preBoltNameEN "Bolt ISO4014-"
	preNutName "螺母GB/T6170-"
	preNutNameEN "Nut ISO4032-"
	preWasherName "垫圈EN14399-6-"
	preWasherNameEN "Washer EN14399-6-")
  (setq sufSectionName "段塔筒附件总成")
  (setq sufWeldName "段塔筒焊合")
  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  (setq all_tower_mass 0.0)  ;设置初始塔架重量为0
  (setq count 0);计数
  ;基础环/塔架底座  
  ;(setq embedded_is_exit 0)
  (if (= (EmbeddedOrNot) T);如果是基础环
    (progn
      (setq count (+ count 1))
      (setq Index  (rtos count) );把两个字符串合并成一个字符并返回，返回 \"1\" ；rots四舍五入，
      (setq embedded_mass_str  (rtos mass_of_embedded 2 0) );返回重量
      (setq all_tower_mass (+ all_tower_mass mass_of_embedded))  ;累加重量
      ;基础环底座bom;;;;;;;;
      (setq Index  (rtos (+ count 1)) );基础环的序号，如果有基础环，count为1，如果没有count为0
      (setq all_tower_mass (+ all_tower_mass stair_mass))  ;累加重量
      (setq embedded_zb (list (* 806 mscale) (+ (* 50 mscale) (* (atoi Index) 13 mscale)) 0))
      (command "insert" "pc_mxb_block" "S" mscale embedded_zb ""
	   "" "" "" "Tower embedded can assembly" "" embedded_mass_str embedded_mass_str "" "1" "塔架底座总成" Index ""
	   "" "" )
    )
  );基础环if的右括号，END基础环
  
  ;外爬梯bom;;;;;;;;
  (setq count (+ count 1))
  (setq Index  (rtos count) );基础环的序号，如果有基础环，count为1，如果没有count为0
  (setq all_tower_mass (+ all_tower_mass stair_mass))  ;累加重量
  ;(setq staircode "60.08.02697")
  (setq staircode "")
  ;块名称，S，比例，坐标，旋转角度,
  ;版本，NOTES，MATERIAL，NAME，DRAWINGNUMBER，总重，单重，备注，数量，名称，序号，代号
  ;材料
  ;明细表1
  (setq stair_zb (list (* 806 mscale) (+ (* 50 mscale) (* (atoi Index) 13 mscale)) 0));坐标
  
  ;(command "insert" "pc_mxb_block" "S" mscale stair_zb ""
  ;  "" "" "" "Entrance stair assembly" "" stair_mass stair_mass "" "1" "塔架入口梯子总成" Index staircode
  ;  "")
  (zq_block_insert stair_zb Index staircode "塔架入口梯子总成" "Entrance stair assembly" "1" "" "" stair_mass stair_mass 0.67 0.53)
  
  ;底平台围边bom
  ;(setq count (+ count 1))
  ;(setq Index  (rtos count) )
  ;(setq edge_zb (list (* 806 mscale) (+ (* 50 mscale) (* (atoi Index) 13 mscale)) 0))
  ;(setq edge_mass 74)
  ;(setq all_tower_mass (+ all_tower_mass edge_mass))  ;累加重量
  ;(command "insert" "pc_mxb_block_edge" "S" mscale edge_zb ""
  ;	   "" "" "" "Bottom platform surrounding edge" "" edge_mass edge_mass "" "1" "底平台围边" Index "60.10.50364"
  ;	   "" )
  ;升降机bom;;;;;
  (setq count (+ count 1))
  (setq Index (rtos count))
  (setq lift_zb (list (* 806 mscale) (+ (* 50 mscale) (* (atoi Index) 13 mscale)) 0))
  (if (or (= topfltype "2.xMW_TopFlange") (= topfltype "2MW_TopFlange"))
    (command "insert" "pc_mxb_block2" "S" mscale lift_zb ""
	   "" "Purchasing" "" "Wire guided lift with righted door" "" "" "" "采购" "1" "钢丝绳导向正面右侧开门塔筒升降机" Index ""
	   "" )
    (command "insert" "pc_mxb_block" "S" mscale lift_zb ""
	   "" "Purchasing" "" "lift" "" "" "" "采购" "1" "塔筒升降机" Index ""
	   "" )
  );end if;;;;;
  ;塔架段bom
  (setq count (+ count 1))
  (setq i 0)
  ;(setq seccount 0)
  (while (< i (length SectionMassList) );SectinMassList
    (setq WeldName (strcat (nth i SectionIndex) sufWeldName);把表SectionIndex的第i个元素与sufWeldName合并
	  WeldNameEN (strcat "Section " (nth i SectionIndexEN) "Welded")
	  weld_mass (+ (nth i SectionMassList) (nth i FlangeMassList) (nth (+ i 1) FlangeMassList))
    )
    (setq Index (rtos (+ i count)))
    (if (= i 0);如果是第一段
      (setq weld_mass (+ weld_mass MassDoorFrame (- MassOfTowerDoorHole) ))
    )
    (setq weld_mass_str (rtos weld_mass 2 0))
    (setq weld_mass (atoi weld_mass_str))
    (setq all_tower_mass (+ all_tower_mass weld_mass))  ;累加重量

    
    (if (= i (- section_qty 1));如果是顶段
      (progn
	(setq WeldName "顶段塔筒焊合"
	      WeldNameEN "Top Section Welded")
	(setq SectionName "顶段塔筒附件总成"
	      SectionNameEN "Top Section Accessories")
      )
    )
    (setq weldcode "");焊合物料号
    ;(setq seccount (+ seccount 1))
    ;;;塔筒明细表插入
    (setq weld_zb (list (* 806 mscale) (+ (* 50 mscale) (* (atoi Index) 13 mscale)) 0))
    ;焊合
    (command "insert" "pc_mxb_block" "S" mscale weld_zb ""
	   "" "" "" WeldNameEN "" weld_mass_str weld_mass_str "" "1" WeldName Index weldcode
	   "" )
    (setq i (+ 1 i))
  )
  (print "塔筒段明细表插入成功！")
  ;;;附件总成插入
  (setq count (+ count section_qty))
 
  (setq Index  (rtos count) );附件总成的序号
  ;(setq fjzc_mass 10000.0);附件总成重量
  (setq all_tower_mass (+ all_tower_mass fjzc_mass))  ;累加重量
  (setq fjzc_mass_str (rtos fjzc_mass))
  (setq Index count)

  (setq fjzc_zb (list (* 806 mscale) (+ (* 50 mscale) (*  Index 13 mscale)) 0))


  ;焊合
  (command "insert" "pc_mxb_block" "S" mscale fjzc_zb ""
	   "" "" "" "Accessory assembly" "" fjzc_mass_str fjzc_mass_str "" "1" "附件总成" Index ""
	   "" )
  (print "附件总成明细表插入成功！")
  ;阻尼器bom;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  ;对塔架净高度>125m的塔架，采用阻尼器，明细表中增加两行
  (if (> (- (value retFlange section_qty 0) (value retFlange 0 0)) 125)
    (progn
      (if (= topfltype "21_TopFlange") ;如果是21号顶法兰
	(progn
          ; 冷却液
          (setq count (+ count 1))
          (setq Index (rtos count))
          (setq Attach_zb (list (* 806 mscale) (+ (* 50 mscale) (* (atoi Index) 13 mscale)) 0))
          (command "insert" "pc_mxb_block2" "S" mscale Attach_zb ""
	   "" "Purchasing" "" "" "" "" "" "采购" "50" "金风模块化液体阻尼器4X2" Index ""
	   "" )
	);end progn
	(progn
	  ; 冷却液
          (setq count (+ count 1))
          (setq Index (rtos count))
          (setq Attach_zb (list (* 806 mscale) (+ (* 50 mscale) (* (atoi Index) 13 mscale)) 0))
          (command "insert" "pc_mxb_block2" "S" mscale Attach_zb ""
	   "" "Purchasing" "" "" "" "" "" "采购" "50" "塔架液体阻尼器阻尼液（塑料桶装）" Index ""
	   "" )
	  ; 液体阻尼器
          (setq count (+ count 1))
          (setq Index (rtos count))
          (setq Attach_zb (list (* 806 mscale) (+ (* 50 mscale) (* (atoi Index) 13 mscale)) 0))
          (command "insert" "pc_mxb_block" "S" mscale Attach_zb ""
	   "" "Purchasing" "" "TLD" "" "" "" "采购" "10" "液体阻尼器" Index ""
	   "" )
	);end progn
      );end if	
    );end progn
  );End if
  (print "阻尼器插入成功")
  (print "明细表成功插入！")
  (prin1)
)
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;end BomList4;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;About excel
;;;;;;;;;;得到选择的塔架数据表;;;;;;;;;;;;;;
(defun GetTowerdat_file(/ file dir Towerdat_file) 
  (setq file (open "D:\\Program Files (x86)\\Autodesk\\AutoLisp\\dir.ini" "r"))
  (setq dir (read-line file))
  (if (= dir nil)
    (setq dir "C:\\")
    (close file)
  )
  (setq Towerdat_file (getfiled "塔架数据" dir "xlsx" 2));有2吗？，excel文件路径，“塔架数据是起的一个名字”，dir是路径，xlsx是寻找这种后缀名的文件
)
;;;;;;;;;函数结束;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;;;;;;;;;得到选择的塔架附件设计表;;;;;;;;;;;;;;曹学敏新增
; (defun GetTowerasm_file(/ file dir Towerdat_file) 
  ; (setq file (open "D:\\Program Files (x86)\\Autodesk\\AutoLisp\\dir.ini" "r"))   
  ; (setq dir (read-line file))
  ; (close file)
  ; (if (= dir nil)
    ; (setq dir "E:\\")
  ; )
  ; (setq Towerdat_file (getfiled "塔架附件设计表" dir "xlsx" 2));excel文件路径，“塔架数据是起的一个名字”，dir是路径，xlsx是寻找这种后缀名的文件，2表示禁用“type it”    
; )
;;;;;;;;;函数结束;;;;;;;;;;;;;;;;;;;;;;;;;;;;


;;;;;;;;;;得到选择的塔架数据表;;;;;;;;;;;;;;
(defun GetTowerdat_file2(/ file dir Towerdat_file) 
  (setq file (open "D:\\Program Files (x86)\\Autodesk\\AutoLisp\\dir.ini" "r"))
  (setq dir (read-line file))
  (close file)
  (if (= dir nil)
    (setq dir "E:\\")
  )
  (setq Towerdat_file (getfiled "塔架辅助设计表" dir "xls" 2));有2吗？，excel文件路径，“塔架数据是起的一个名字”，dir是路径，xlsx是寻找这种后缀名的文件
)
;;;;;;;;;函数结束;;;;;;;;;;;;;;;;;;;;;;;;;;;;


;;;;;;;;;;;;;;;;;;;;;;;;;;*******获取excel表中的数据函数*******;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defun GetTowerGeoData(excelFile / xl wbs wb shs sh_description sh_tower sh_flange sh_door rg_description rg_tower rg_flange rg_door vvv_description vvv_tower vvv_flange vvv_door vvv_embedded)
    (vl-load-com)
    (setq xl (vlax-get-or-create-object "Excel.Application")) ;创建excel程序对象
    (setq wbs (vlax-get-property xl "WorkBooks")) ;获取excel程序对象的工作簿集合对象
    (setq wb (vlax-invoke-method wbs "open" excelFile)) ;用工作簿集合对象打开指定的excel文件
    (setq shs (vlax-get-property wb "Sheets"));获取刚才打开工作簿的工作表集合
  
    (setq sh_description (vlax-get-property shs "Item" "Description"));获取指定的description工作表
	(setq sh_tower (vlax-get-property shs "Item" "TowerGeo"));获取指定的tower工作表
    (setq sh_flange (vlax-get-property shs "Item" "Flange"));获取指定的flange工作表
    (setq sh_door (vlax-get-property shs "Item" "Door"));获取指定的door工作表
	
    (setq rg_description (vlax-get-property sh_description "Range" "B2:M2"));用指定的字符串创建工作表范围对象
	(setq rg_tower (vlax-get-property sh_tower "Range" "B2:K100"));用指定的字符串创建工作表范围对象
    (setq rg_flange (vlax-get-property sh_flange "Range" "A3:P15"));用指定的字符串创建工作表范围对象
    (setq rg_door (vlax-get-property sh_door "Range" "A3:V3"));用指定的字符串创建工作表范围对象
    (setq rg_embedded (vlax-get-property sh_door "Range" "C6:C22"));基础环
  
    (setq vvv_description (vlax-get-property rg_description 'Value));获取范围对象的值
	(setq vvv_tower (vlax-get-property rg_tower 'Value));获取范围对象的值
    (setq vvv_flange (vlax-get-property rg_flange 'Value));获取范围对象的值
    (setq vvv_door (vlax-get-property rg_door 'Value));获取范围对象的值
    (setq vvv_embedded (vlax-get-property rg_embedded 'Value));获取范围对象的值
  
    (setq retDescription (vlax-safearray->list (vlax-variant-value vvv_description))) ;转换为list
	(setq retTower (vlax-safearray->list (vlax-variant-value vvv_tower))) ;转换为list
    (setq retFlange (vlax-safearray->list (vlax-variant-value vvv_flange))) ;转换为list
    (setq retDoor (vlax-safearray->list (vlax-variant-value vvv_door))) ;转换为list
    (setq retEmbedded (vlax-safearray->list (vlax-variant-value vvv_embedded))) ;转换为list

    (vlax-invoke-method (vlax-get-property xl "ActiveWorkbook")'Close :vlax-false)  
    (vlax-invoke-method xl "Quit");退出excel对象
    (vlax-release-object xl);释放excel对象
);defun函数GetTowerGeoData的右括号
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;;;;;;;;;;;;;;;;;;;;;;;;;*******获取塔架附件设计表中的数据函数*******曹学敏新增;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defun GetTowerDesignData(excelFile / xl wbs wb shs SheetNameList i sheetname sh_tower rg_tower vvv_tower)
    (vl-load-com)
    (setq xl (vlax-get-or-create-object "Excel.Application")) ;创建excel程序对象
    (setq wbs (vlax-get-property xl "WorkBooks")) ;获取excel程序对象的工作簿集合对象
    (setq wb (vlax-invoke-method wbs "open" excelFile)) ;用工作簿集合对象打开指定的excel文件
    (setq shs (vlax-get-property wb "Sheets"));获取刚才打开工作簿的工作表集合

	(setq SheetNameList '("100_4_4450" "100_4_4950" "100_5_4450" "100_5_4950" "105_5_4450" "105_5_4950" "110_5_4450" "110_5_4950" "115_5_4450" "115_5_4950" 
	                      "120_5_4450" "120_5_4950" "120_6_4450" "120_6_4950" "130_6_4950" "130_5_5950" "140_6_5950" "145_6_5950") )
	(setq i 0)
	(setq retTowerDesignlist '())
	(while (<= i 18);遍历SheetNameList中的元素，用于建立所有体型对应的数据
         (setq sheetname (nth i SheetNameList))
		 (setq sh_tower (vlax-get-property shs "Item" sheetname));获取指定的tower工作表
		 (setq rg_tower (vlax-get-property sh_tower "Range" "A2:U7"));用指定的字符串创建工作表范围对象
		 (setq vvv_tower (vlax-get-property rg_tower 'Value));获取范围对象的值
		 (setq retTowerDesignlist (append retTowerDesignlist (list (vlax-safearray->list (vlax-variant-value vvv_tower))))) ;转换为list
	     (setq i (+ i 1))
	);end while
    (vlax-invoke-method (vlax-get-property xl "ActiveWorkbook")'Close :vlax-false)  
    (vlax-invoke-method xl "Quit");退出excel对象
    (vlax-release-object xl);释放excel对象
);defun函数GetTowerDesignData的右括号
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;;;;;;;;;;;;;;;;;;;;;;;;;*******获取塔架附件设计表中的数据函数******新增;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defun GetTowerDesignData_1(excelFile / xl wbs wb shs SheetNameList i sheetname sh_tower rg_tower vvv_tower)
    (vl-load-com)
    (setq xl (vlax-get-or-create-object "Excel.Application")) ;创建excel程序对象
    (setq wbs (vlax-get-property xl "WorkBooks")) ;获取excel程序对象的工作簿集合对象
    (setq wb (vlax-invoke-method wbs "open" excelFile)) ;用工作簿集合对象打开指定的excel文件
    (setq shs (vlax-get-property wb "Sheets"));获取刚才打开工作簿的工作表集合

	(setq SheetNameList '("100_4_4450" "100_4_4950" "100_5_4450" "100_5_4950" "105_5_4450" "105_5_4950" "110_5_4450" "110_5_4950" "115_5_4450" "115_5_4950" 
	                      "120_5_4450" "120_5_4950" "120_6_4450" "120_6_4950" "130_6_4950" "130_5_5950" "140_6_5950" "145_6_5950") )
	(setq i 0)
	(setq retTowerDesignlist '())
	(while (<= i 18);遍历SheetNameList中的元素，用于建立所有体型对应的数据
         (setq sheetname (nth i SheetNameList))
		 (setq sh_tower (vlax-get-property shs "Item" sheetname));获取指定的tower工作表
		 (setq rg_tower (vlax-get-property sh_tower "Range" "A2:U7"));用指定的字符串创建工作表范围对象
		 (setq vvv_tower (vlax-get-property rg_tower 'Value));获取范围对象的值
		 (setq retTowerDesignlist (append retTowerDesignlist (list (vlax-safearray->list (vlax-variant-value vvv_tower))))) ;转换为list
	     (setq i (+ i 1))
	);end while
    (vlax-invoke-method (vlax-get-property xl "ActiveWorkbook")'Close :vlax-false)  
    (vlax-invoke-method xl "Quit");退出excel对象
    (vlax-release-object xl);释放excel对象
);defun函数GetTowerDesignData的右括号
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;;;;;;;;;;;;;;;;;;;;;;;;;*******获取辅助设计表中的数据函数*******;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defun GetAidedDesignData(excelFile / xl wbs wb shs i sheetname sh_tower rg_tower vvv_tower)
    (setq xl (vlax-get-or-create-object "Excel.Application")) ;创建excel程序对象
    (setq wbs (vlax-get-property xl "WorkBooks")) ;获取excel程序对象的工作簿集合对象
    (setq wb (vlax-invoke-method wbs "open" excelFile)) ;用工作簿集合对象打开指定的excel文件
    (setq shs (vlax-get-property wb "Sheets"));获取刚才打开工作簿的工作表集合
    (setq i 1)
    ;(setq retname (list "retAid1" "retAid2" "retAid3" "retAid4" "retAid5" "retAid6" "retAid7" "retAid8"))
    (setq retAidlist '())
    (while (<= i section_qty)
      (if (= i section_qty)
        (setq sheetname  (strcat "顶"  "段"));sheet的名字
	(setq sheetname  (strcat "第" (rtos i 2 0) "段"));sheet的名字
      )
      (setq sh_tower (vlax-get-property shs "Item" sheetname));获取指定的tower工作表
      (setq rg_tower (vlax-get-property sh_tower "Range" "A2:C7"));用指定的字符串创建工作表范围对象
      (setq vvv_tower (vlax-get-property rg_tower 'Value));获取范围对象的值
      ;得到附件总成的重量
      ;(setq rg_acc (vlax-get-property sh_tower "Range" "C2:C7"));用指定的字符串创建工作表范围对象
      ;(setq v_acc_weight (vlax-get-property rg_acc 'Value));获取范围对象的值

      
      (setq retAidlist (append retAidlist (list (vlax-safearray->list (vlax-variant-value vvv_tower)))))
      ;(setq retAcclist (append retAcclist (list (vlax-safearray->list (vlax-variant-value v_acc_weight)))))
      ;(print x)
      (setq i (+ i 1))
  
      
    )
    (vlax-invoke-method (vlax-get-property xl "ActiveWorkbook")'Close :vlax-false)  
    (vlax-invoke-method xl "Quit");退出excel对象
    (vlax-release-object xl);释放excel对象
  
);defun函数GetAidedDesignData的右括号
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;***************************************读取Excel表*****************************************查询螺栓表用
(defun GetCellValueAsList(excelFile sheetName RangeStr / xl wbs wb shs sh rg  vvv nms nm ttt ret)
  ;(vl-load-com)
  (setq xl (vlax-get-or-create-object "Excel.Application")) ;创建excel程序对象
  (setq wbs (vlax-get-property xl "WorkBooks")) ;获取excel程序对象的工作簿集合对象
  (setq wb (vlax-invoke-method wbs "open" excelFile)) ;用工作簿集合对象打开指定的excel文件
  (setq shs (vlax-get-property wb "Sheets"));获取刚才打开工作簿的工作表集合
  (setq sh (vlax-get-property shs "Item" sheetName));获取指定的工作表
  (setq rg (vlax-get-property sh "Range" RangeStr));用指定的字符串创建工作表范围对象
  (setq vvv (vlax-get-property rg 'Value));获取范围对象的值
  (setq ttt (vlax-safearray->list (vlax-variant-value vvv))) ;转换为list
;;;  (vlax-invoke-method wb "Close" );关闭工作簿
  (vlax-invoke-method (vlax-get-property xl "ActiveWorkbook")'Close :vlax-false)
  (vlax-invoke-method xl "Quit");推出excel对象
  (vlax-release-object xl);释放excel对象
  (setq ret ttt)
)
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;;;;;;;;;;;;;;;;;;;;;;;;;*******获取数据表格中的函数*******;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defun GetExcelData(excelFile / xl wbs wb shs sh_Bolt_Data sh_Bolt sh_Nut sh_Washer rg_BoltDraw rg_Bolt rg_Nut rg_Washer vvv_Bolt_Data vvv_Bolt vvv_Nut vvv_Washer r)
    ;(vl-load-com)
    (setq xl (vlax-get-or-create-object "Excel.Application")) ;创建excel程序对象
    (setq wbs (vlax-get-property xl "WorkBooks")) ;获取excel程序对象的工作簿集合对象
    (setq wb (vlax-invoke-method wbs "open" excelFile)) ;用工作簿集合对象打开指定的excel文件
    (setq shs (vlax-get-property wb "Sheets"));获取刚才打开工作簿的工作表集合
  
    (setq sh_BoltDraw (vlax-get-property shs "Item" "Bolt_Data"));获取指定的Bolt_Data工作表
    (setq sh_Bolt (vlax-get-property shs "Item" "Bolt"));获取指定的Bolt工作表
    (setq sh_Nut (vlax-get-property shs "Item" "Nut"));获取指定的Nut工作表
    (setq sh_Washer (vlax-get-property shs "Item" "Washer"))
  
    (setq rg_BoltDraw (vlax-get-property sh_BoltDraw "Range" "C2:V17"));用指定的字符串创建工作表范围对象
    (setq rg_Bolt (vlax-get-property sh_Bolt "Range" "E2:I400"));用指定的字符串创建工作表范围对象
    (setq rg_Nut (vlax-get-property sh_Nut "Range" "E2:H25"));用指定的字符串创建工作表范围对象
    (setq rg_Washer (vlax-get-property sh_Washer "Range"  "E2:H20"))
  
  
    (setq vvv_BoltDraw (vlax-get-property rg_BoltDraw 'Value));获取范围对象的值
    (setq vvv_Bolt (vlax-get-property rg_Bolt 'Value));获取范围对象的值
    (setq vvv_Nut (vlax-get-property rg_Nut 'Value));获取范围对象的值
    (setq vvv_Washer (vlax-get-property rg_Washer 'Value))
  
    (setq retBoltDraw (vlax-safearray->list (vlax-variant-value vvv_BoltDraw))) ;转换为list
    (setq retPlmBolt (vlax-safearray->list (vlax-variant-value vvv_Bolt))) ;转换为list
    (setq retPlmNut (vlax-safearray->list (vlax-variant-value vvv_Nut))) ;转换为list 
    (setq retPlmWasher (vlax-safearray->list (vlax-variant-value vvv_Washer))) ;转换为list

    (vlax-invoke-method (vlax-get-property xl "ActiveWorkbook")'Close :vlax-false)  
    (vlax-invoke-method xl "Quit");退出excel对象
    (vlax-release-object xl);释放excel对象
);defun函数GetexcelData的右括号
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;




;;;;;;;;;;;;;;;;;;;;;;;;;*****得到excle表中单元格内容函数*******;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defun value(datasheet i j / data);返回sheet中第i行j列的值
    (setq data (vlax-variant-value(nth j (nth i datasheet))))
);defun函数value的右括号
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


;;20191202

(defun c:as()
  (cflange 4300  4151 4000 17.5 40 45 126.0 10 130  )
)


;;;;;;普通法兰函数;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defun cflange (Dout_fl Dpcd_fl Din_fl T_FL L_fl d_fl num_hole R_fl H_fl  / cr circle1 circle2 circle3 circle4 circle5 dr1 i c1 c2 c3 c4 line1 line2 flpt00 scale_fl H_flange L_flange T_flange R_flange Dout_flange Din_flange
		                                                                  Dpcd_flange d_flange flpt01 flpt02 flpt03 flpt04 flpt05 flpt06 flpt33 flpt31 flpt21 flpt20 flpt16 flpt15 flpt14 flpt13 flpt12 flpt11 flpt10 flpt40
		                                                                  flpt50  flpt5001 flpt5002 flpt30 flpt313 flpt3133 flpt1621 flpt0304 flpt03041 flpt03042 flpt03043 flpt03044 flpt03045 flpt03046 flpt03047 flpt03048
		                                                                  flpt03049 line3 line4 line5 line6 line7 line8 line9 line10 line11 line12 line13 line14 line15 line16 line17 line18 line19 line20 arc1 dr1 dr2 dr3 dr4
		                                                                  dr5 dr6 dr7 dr8 dr9 line21 dim_scale dim_disv dim_dish dim_ang dim2)
  (setq myacad (vlax-get-acad-object))
  (setq mydoc (vla-get-ActiveDocument myacad))
  (setq myms (vla-get-ModelSpace mydoc));my models
  (setq cr '(3800 4700 0 ));指定圆心
 ; (setq Dout_fl 4300);指定法兰外径
  (setq circle1 (vla-addcircle myms (vlax-3d-point cr) (/ Dout_fl 2.0))) ;画出法兰外圆
 ; (setq Dpcd_fl 4134);指定法兰分度圆直径
  (command "layer" "M" "4虚线层" "")
  (setq circle2 (vla-addcircle myms (vlax-3d-point cr) (/ Dpcd_fl 2.0)));画出法兰分度圆
  (vla-put-LinetypeScale circle2 0.2);调整分度圆线型的比例
  
  
  ;(setq Din_fl 3960);指定法兰内圆直径
  (command "layer" "M" "1轮廓实线层" "")
  (setq circle3 (vla-addcircle myms (vlax-3d-point cr) (/ Din_fl 2.0)));画出法兰内圆
  ;(setq T_fl 25.7)
  (setq circle4 (vla-addcircle myms (vlax-3d-point cr) (/ (- Dout_fl (* 2 T_fl)) 2.0)));画出法兰壁厚所在圆
  (setq dr1 (polar cr 0  (/ Dpcd_fl 2)));指定圆心
  ;(setq d_fl 45);指定螺栓孔直径 
  (setq circle5 (vla-addcircle myms (vlax-3d-point dr1) (/ d_fl 2.0)));画出螺栓孔
  ;(setq num_hole 144.0);指定螺栓孔数量
  (setq i 1);指定循环起始数据
  (while (<= i num_hole)
    (setq drn (polar cr (* (* pi 2)(/ i num_hole )) (/ Dpcd_fl 2.0)));指定螺栓孔圆心
    (setq circlen (vla-addcircle myms (vlax-3d-point drn) (/ d_fl 2.0)));循环画出螺栓孔
    (setq i (+ 1 i)) 
  )
  (setq c1 (polar cr 0 (+ 100 (/ Dout_fl 2.0))));指定中心线右端点
  (setq c2 (polar cr (/ pi 2) (+ 100 (/ Dout_fl 2.0))));指定中心线上端点
  (setq c3 (polar cr pi (+ 100 (/ Dout_fl 2.0))));指定中心线左端点
  (setq c4 (polar cr (* 1.5  pi) (+ 100 (/ Dout_fl 2.0))));指定中心线右端点
  (command "layer" "M" "3中心线层" "")
  (setq line1 (vla-addline myms (vlax-3d-point c1) (vlax-3d-point c3)));画出中心线水平线
  (setq line2 (vla-addline myms (vlax-3d-point c2) (vlax-3d-point c4)));画出中心线竖直线
  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;法兰放大图;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  (setq flpt00 (polar cr (/ pi 10)  4500));指定法兰剖视图起点
  (setq scale_fl 5);
 ; (setq H_fl 140)
  ;(setq L_fl 40)
  ;(setq T_fl 25.7)
  ;(setq R_fl 10)
  (setq H_flange (* scale_fl H_fl));比例系数放大
  (setq L_flange (* scale_fl L_fl));比例系数放大L_fl放大后为 L_flange
  (setq T_flange (* scale_fl T_fl))
  (setq R_flange (* scale_fl R_fl))
  (setq Dout_flange (* scale_fl Dout_fl))
  (setq Din_flange (* scale_fl Din_fl))
  (setq Dpcd_flange (* scale_fl Dpcd_fl))
  (setq d_flange (* scale_fl d_fl))
  (setq flpt01 (polar flpt00 0  (/ H_flange 6)));指定法兰剖视图关键点
  (setq flpt02 (polar flpt01 0  (/ (- Dout_flange Din_flange) 6)));指定法兰剖视图关键点	
  (setq flpt03 (polar flpt02 0  (/ (- Dpcd_flange (+ Din_flange d_flange)) 2.0)));指定法兰剖视图关键点
  (setq flpt04 (polar flpt03 0  (/ d_flange 2.0)));指定法兰剖视图关键点
  (setq flpt05 (polar flpt04 0  (/ d_flange 2.0)));指定法兰剖视图关键点
  (setq flpt06 (polar flpt05 0  (/ (- Dout_flange (+ Dpcd_flange d_flange)) 2.0)));指定法兰剖视图关键点
  (setq flpt33 (polar flpt06 (/ pi 2) H_flange));指定法兰剖视图关键点
  (setq flpt31 (polar flpt33  pi T_flange));指定法兰剖视图关键点
  (setq flpt21 (polar flpt31 (* 1.5 pi) (- L_flange R_flange )));指定法兰剖视图关键点
  (setq flpt20 (polar flpt21 pi R_flange));指定法兰剖视图关键点
  (setq flpt16 (polar flpt20 (* 1.5 pi) R_flange));指定法兰剖视图关键点
  (setq flpt15 (polar flpt05 (* 0.5 pi) (- H_flange L_flange)));指定法兰剖视图关键点
  (setq flpt14 (polar flpt15  pi (/ d_flange 2.0)));指定法兰剖视图关键点
  (setq flpt13 (polar flpt14  pi (/ d_flange 2.0)));指定法兰剖视图关键点
  (setq flpt12 (polar flpt02 (* 0.5 pi) (- H_flange L_flange)));指定法兰剖视图关键点
  (setq flpt11 (polar flpt01 (* 0.5 pi) (- H_flange L_flange)));指定法兰剖视图关键点
  (setq flpt10 (polar flpt11  pi (/ H_flange 8)));指定法兰剖视图关键点
  (setq flpt40 (polar flpt14  (* 0.5 pi) 40 ));指定法兰剖视图关键点
  (setq flpt50 (polar flpt04  (* 1.5 pi) 40 ));指定法兰剖视图关键点
  (setq flpt5001 (polar flpt05  (* 1.5 pi) 600 ));指定法兰剖视图关键点
  (setq flpt5002 (polar flpt5001  0 300 ));指定法兰剖视图关键点
  (setq flpt30 (polar flpt11  (* 0.5 pi) L_flange ));指定法兰剖视图关键点
  (setq flpt313 (polar flpt31  (* 0.5 pi) 250));指定法兰剖视图关键点
  (setq flpt3133 (polar flpt313  0 250));指定法兰剖视图关键点
  (setq flpt1621 (polar flpt20  (-(* 0.25 pi)) R_flange ));指定法兰圆角标注键点
  (setq flpt0304 (polar flpt03  (-(* 0.5 pi)) 1000 ));指定法兰圆角标注键点
  (setq flpt03041 (polar flpt0304  (- pi) 800));指定技术要求注键点
  (setq flpt03042 '(3600 1800 0 ));公司图章插入点
  (setq flpt03043 '(1400 1800 0 ));法兰P向视图插入点
  (setq flpt03044 '(11180 8150 0 ));法兰粗糙度插入点
  (setq flpt03045 (polar flpt13  (/ pi 2) 800 ));A-A视图及比例插入点
  (setq flpt03046 (polar flpt13  (- pi) (/ (- Dpcd_flange (+ d_flange Din_flange)) 4.0)));平行度插入点
  (setq flpt03047 (polar flpt05 0 (/ (- Dout_flange (+ d_flange Dpcd_flange)) 4.0 )));平面度插入点
  (setq flpt03048 (polar cr (* pi (/ 122.0 180.0)) (/ Din_fl 2.0)));标注点内径定位点2
  (setq flpt03049 (polar cr  (* (/ 15.0 180.0) pi) (/ Dout_fl 2.0)));标注点a基准
  (command "layer" "M" "1轮廓实线层" "")
  (setq line3 (vla-addline myms (vlax-3d-point flpt00) (vlax-3d-point flpt01)));画出法兰放大图连线
  (setq line4 (vla-addline myms (vlax-3d-point flpt01) (vlax-3d-point flpt02)));画出法兰放大图连线
  (setq line5 (vla-addline myms (vlax-3d-point flpt02) (vlax-3d-point flpt03)));画出法兰放大图连线
  (setq line6 (vla-addline myms (vlax-3d-point flpt03) (vlax-3d-point flpt04)));画出法兰放大图连线
  (setq line7 (vla-addline myms (vlax-3d-point flpt04) (vlax-3d-point flpt05)));画出法兰放大图连线
  (setq line8 (vla-addline myms (vlax-3d-point flpt05) (vlax-3d-point flpt06)));画出法兰放大图连线
  (setq line9 (vla-addline myms (vlax-3d-point flpt06) (vlax-3d-point flpt33)));画出法兰放大图连线
  (setq line10 (vla-addline myms (vlax-3d-point flpt33) (vlax-3d-point flpt31)));画出法兰放大图连线
  (setq line11 (vla-addline myms (vlax-3d-point flpt31) (vlax-3d-point flpt21)));画出法兰放大图连线
  (setq arc1 (vla-addarc myms (vlax-3d-point flpt20) R_flange (-(/ pi 2)) 0));画出法兰放大图连线
  (setq line12 (vla-addline myms (vlax-3d-point flpt16) (vlax-3d-point flpt15)));画出法兰放大图连线
  (setq line13 (vla-addline myms (vlax-3d-point flpt15) (vlax-3d-point flpt13)));画出法兰放大图连线
  (setq line14 (vla-addline myms (vlax-3d-point flpt13) (vlax-3d-point flpt12)));画出法兰放大图连线
  (setq line15 (vla-addline myms (vlax-3d-point flpt12) (vlax-3d-point flpt10)));画出法兰放大图连线
  (setq line16 (vla-addline myms (vlax-3d-point flpt12) (vlax-3d-point flpt02)));画出法兰放大图连线
  (setq line17 (vla-addline myms (vlax-3d-point flpt13) (vlax-3d-point flpt03)));画出法兰放大图连线
  (setq line18 (vla-addline myms (vlax-3d-point flpt15) (vlax-3d-point flpt05)));画出法兰放大图连线
  (command "layer" "M" "3中心线层" "")
  (setq line19 (vla-addline myms (vlax-3d-point flpt40) (vlax-3d-point flpt50)));画出法兰放大图连线
  (vla-put-LinetypeScale line19 0.4);调整法兰放大图中心线线型的比例
  
  (command "layer" "M" "1轮廓实线层" "")
  (setq line20 (vla-addline myms (vlax-3d-point flpt31) (vlax-3d-point flpt30)));画出法兰放大图连线
  (command "layer" "M" "5剖面线层" "")
  (command "spline" flpt30 flpt10 flpt00  "" "" "")
  (rephatch myms (list line8 line9 line10 line11 arc1 line12 line18) 30 0);法兰右半部分剖面线的绘制
  (rephatch myms (list line5 line17 line14 line16) 30 0);法兰右半部分剖面线的绘制
  (setq dr3 (polar cr 0  (/ Dout_fl 2.0)));标注点外径定位基点
  (setq dr2 (polar cr 0  (/ Din_fl 2.0)));标注点内径定位基点
  (setq dr1 (polar cr 0  (/ 2.0 (- Dpcd_fl Din_fl))));标注点分度圆定位基点
  (setq dr4 (polar cr (/ pi 6)  (/ Din_fl 2.0)));标注点内径定位点1
  (setq dr5 (polar cr (* pi (/ 7 6.0)) (/ Din_fl 2.0)));标注点内径定位点2
  (setq dr6 (polar cr (/ pi 3)  (/ Dout_fl 2.0)));标注点外径定位点1
  (setq dr7 (polar cr (- (* (/ 2 3.0) pi))  (/ Dout_fl 2.0)));标注点外径定位点2
  (setq dr8 (polar cr (* pi (/ 5 6.0))  (/ Dpcd_fl 2.0)));标注点分度圆定位点1
  (setq dr9 (polar cr (- (* pi (/ 1 6.0))) (/ Dpcd_fl 2.0)));标注点分度圆定位点2
  
  ;(setq line21 (vla-addline myms (vlax-3d-point dr4) (vlax-3d-point dr5)));画出法兰放大图连线
  
  (setq dim_scale 5);指定尺寸的放大比例
  (setq dim_disv (/ Din_fl 2.0));
  (setq dim_dish (/ Din_fl 2.0));
  (setq dim_ang  (/ pi 6));
  ;;;;;;;标注尺寸;;;;;;;;;;;;;;;;;;;;;;;;
  (dimFlange_thick2 flpt12 flpt02 (/ 1.0 scale_fl) 0  (- (* 4 H_fl)));法兰厚度标注
  (ldimv2 flpt33 flpt06 (/ 1.0 scale_fl ) 0 (* 4 H_fl));法兰高度标注
  
  (dimr_fl flpt1621 50  (* 0.75 pi) (/ 1.0 scale_fl) 100);圆角标注

  (scaleDim_ct flpt31 flpt33 scale_fl flpt3133);标注法兰脖子厚度
  (dimhole flpt03 flpt05 scale_fl  flpt5002 num_hole);标出圆孔直径及个数
  
  (dimdiafla dr4 dr5 500 1 0 3.0);标出法兰内径
  
  (dimdiafla dr6 dr7 500 1 2.0 0);标出法兰外径

  (dimdiafla dr9 dr8 500 4 0 0);标注主视图的分度圆直径
  
  ;(setq dim2 (vla-AddDimDiametric myms (vlax-3d-point dr9) (vlax-3d-point dr8) 500) );标注主视图的分度圆直径

  
  ; (setq dim4 (vla-AddDimDiametric myms (vlax-3d-point dr4) (vlax-3d-point dr5) 500 ) );标注主视图的内径
  ;(vla-put-ToleranceDisplay dim4 2)
  ;(vla-put-ToleranceUpperLimit dim4 0)
  ;(vla-put-ToleranceLowerLimit dim4 2.0)
  ;(vlax-dump-object dim1 t)
  ;(setq dim2 (vla-AddDimDiametric myms (vlax-3d-point dr9) (vlax-3d-point dr8) 500) );标注主视图的分度圆直径
  ;(setq dim3 (vla-AddDimDiametric myms (vlax-3d-point dr6) (vlax-3d-point dr7) 500 ) );标注主视图的外径直径
  ;(vla-put-ToleranceDisplay dim3 2)
  ; (vla-put-ToleranceUpperLimit dim3 2.0)
  ;(vla-put-ToleranceLowerLimit dim3 0)
  ; (vla-put-Arrowhead1block dim3 "open");改变箭头样式反向
  ;(vla-put-Arrowhead2block dim3 "open");改变箭头样式反向
  ;(vlax-dump-object dim3 t);;;查看属性
  ;(setq dim4 (vla-AddDimAligned myms (vlax-3d-point flpt03) (vlax-3d-point flpt05) (vlax-3d-point flpt5002)));标注圆孔直径和孔数
  ;;(vlax-dump-object dim4 t);;;查看属性
  ;(vla-put-linearscalefactor dim4 (/ 1.0 scale_fl));改变比例
  ;(setq dim5 (vla-AddDimAligned myms (vlax-3d-point flpt31) (vlax-3d-point flpt33) (vlax-3d-point flpt3133)));标注法兰脖子厚度
  ;(vlax-dump-object dim5 t);;;查看属性
  ;(vla-put-linearscalefactor dim5 (/ 1.0 scale_fl));改变比例0
  ;(vla-put-AltSuppressTrailingZeros dim5 1.0);改变小数点位数
  ;(setq dimr1 (vla-AddDimRadial myms (vlax-3d-point flpt20) (vlax-3d-point flpt16) 100 ) );法兰圆角标注
  ;(setq text1 (strcat (rtos num_hole 2 0 ) "*%%c" ));
  ;(vla-put-TextPrefix dim4 text1);加入前缀
  ;(vla-put-Textsuffix dim4 "EQS");加入后缀
  (command "insert" "2and3commonflange" "S" 1 flpt03041 "");插入法兰技术要求
  (command "insert" "company" "S" 1 flpt03042 "");插入公司图章要求
  (command "insert" "pview" "S" 1 flpt03043 "");插入P视图要求
  (command "insert" "cucaodu" "S" 1 flpt03044 "");插入粗糙度要求
  (command "insert" "aaview" "S" 1 flpt03045 "");插入A-A视图要求
  (command "insert" "pingxingdu" "S" 1 flpt03046 "");插入平行度要求
  
  (command "insert" "pingmiandu" "S" 1 flpt03047 "");插入平面度要求
  (command "insert" "aapao2" "S" 1 dr3 "");插入平面度要求
  
  ;(command "insert" "bjizhun1" "S" 1 flpt01 "");插入B基准要求
  (command "insert" "bjizhun1" "S" 1 flpt02 "");插入B基准要求
  
  (command "insert" "pjiantou2" "S" 1 flpt03048 "");插入p箭头要求
  
  ;(command "insert" "ajizhun3" "S" 1 flpt03049 "");插入a基准要求

  (command "insert" "ajizhun1" "S" 1 "R" 60.0 dr6 "");插入a基准要求
  
  (setq flpt5002 (polar flpt5002 0 200))
  (command "insert" "weizhidu" "S" 1 flpt5002 "");插入位置度要求
)


;;;;;;;;;;;;;法兰剖面线绘制函数;;;;;;;;;;;;;;;;;;
(defun rephatch (xmodels loop hatchscale hatchangle / hatch1 );加强板放大视图剖面线绘制,
  (setq hatch1 (vla-addhatch myms "0" "ANSI31" :vlax-True));绘制剖面线,ANSI31类型的剖面线
  (vlax-invoke hatch1 'AppendOuterLoop loop);右侧加强板\ 的剖面线
  (vla-put-PatternScale hatch1 hatchscale);剖面线的比例
  (vla-put-Layer hatch1 "5剖面线层");剖面线的图层
  (vla-put-PatternAngle hatch1 hatchangle);剖面线的角度
)


;;;;;;圆弧标注
(defun dimr_fl(center R ang dimscale leng / dim1)
  ;(setq dim1 (vla-AddDimDiametric myms (vlax-3d-point center) (vlax-3d-point (polar center ang R)) leng ) )
  (setq dim1 (vla-AddDimRadial myms (vlax-3d-point center) (vlax-3d-point (polar center ang R)) leng ) )
  
  (vla-put-LinearScaleFactor dim1 dimscale)
  ;(vla-put-TextSuffix dim1 "(展开尺寸)");标注后面加文字
  ;(vla-put-TextPrefix dim1 "4X");把
)



;;;;;;;;;;;;;;;;;;;;;;********************法兰厚度标注;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defun scaleDim_ct(pt1 pt2 scale pt3)
  (setvar "dimlfac" (/ 1.0 scale)) ;尺寸比例
  (setvar "dimdec" 1);设置标注精度
  (command "Dimlinear" pt1 pt2 pt3)
  (setvar "dimlfac" 1)
  (setvar "dimdec" 0)
)



;;;;;;;;;;;;;;;;;;;;;;********************标注圆孔直径和孔数;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defun dimhole (pt1 pt2 scale pt3 num_hole);点1，点2，比例，标注位置点，孔数量
  (setq dim1 (vla-AddDimAligned myms (vlax-3d-point pt1) (vlax-3d-point pt2) (vlax-3d-point pt3)));标注圆孔直径和孔数
  ;;(vlax-dump-object dim4 t);;;查看属性
  (vla-put-linearscalefactor dim1 (/ 1.0 scale));改变比例
  ;(setq dim5 (vla-AddDimAligned myms (vlax-3d-point flpt31) (vlax-3d-point flpt33) (vlax-3d-point flpt3133)));标注法兰脖子厚度
  ;(vlax-dump-object dim5 t);;;查看属性
  ;(vla-put-linearscalefactor dim5 (/ 1.0 scale_fl));改变比例0
  ;(vla-put-AltSuppressTrailingZeros dim5 1.0);改变小数点位数
  ;(setq dimr1 (vla-AddDimRadial myms (vlax-3d-point flpt20) (vlax-3d-point flpt16) 100 ) );法兰圆角标注
  (setq text1 (strcat (rtos num_hole 2 0 ) "*%%c" ));
  (vla-put-TextPrefix dim1 text1);加入前缀
  (vla-put-Textsuffix dim1 "EQS");加入后缀  
)




;;;;;;;;;;;;;;;;;;;;;;********************标注主视图法兰直径;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defun dimdiafla (pt1 pt2 leng sw tol1 tol2 / dim2);点1，点2，leng，是否带公差()，上公差，下公差
  (setq dim2 (vla-AddDimDiametric myms (vlax-3d-point pt1) (vlax-3d-point pt2) leng));标注主视图的分度圆直径
  (if (= sw 1);判断是否带公差,sw=1:带公差，sw=4:基本尺寸，带方框，sw=0:无
    (progn;如果带公差
      (vla-put-ToleranceDisplay dim2 2)
      (vla-put-ToleranceUpperLimit dim2 tol1)
      (vla-put-ToleranceLowerLimit dim2 tol2)
    )
    (progn
      (vla-put-ToleranceDisplay dim2 sw)
    )
    
  );End if

  (vla-put-TextPosition dim2 (vlax-3d-point '(4870.596 5318.1089 0) ) )
  (vla-put-TextMovement dim2 2)
  ;(vlax-dump-object dim2 t)
)


;Some functions about judge the program,
;门洞类型判断
(defun DoorType(/ DoorTypeR)
	(if (or (= (value retDoor 0 13) "") (= (value retDoor 0 13) nil));
		(progn
			(if (or (= (value retDoor 0 1) "") (= (value retDoor 0 1) nil));
				(setq DoorTypeR "ConcDoor");混塔门洞
				(setq DoorTypeR "odinDoor");普通门洞
			);if
		);progn
		(setq DoorTypeR "RepDoor");加强板门洞
  );if
);defun1

;门洞的高度
(defun DoorHeight(/ doorx Hg)
	(setq doorx (DoorType))
	(cond
		( (= doorx "odinDoor")
		(setq Hg (value retdoor 0 0));普通门框的高度
		)
		( (= doorx "RepDoor")
		(setq Hg (value retdoor 0 12));普通门框的高度
		)
		(t
		(setq Hg (cadr pa))
		)
	);cond
);defun

;About concrete tower
(defun ConcreOrNot(/)
	(if (or (= (value retDoor 0 1) "") (= (value retDoor 0 1) nil));
		(progn
			(if (or (= (value retDoor 0 13) "") (= (value retDoor 0 13) nil));
				(setq DoorTypeR T);
			)
		);progn
		(setq DoorTypeR nil)
	);if
)

(defun C:bcd()
  (setq mscale 100)
    (setq myacad (vlax-get-acad-object))
  (setq mydoc (vla-get-ActiveDocument myacad))
  (setq myms (vla-get-ModelSpace mydoc));my models
  ;(ConcreteFlDraw flange_type pt_flange relH relDout relDin relDpcd reltn reld_hole relL relR num_hole flange_index sum_flange boltType BoltClass reltn_top reltn_down Neck_TopFlange Moment MassOfFlange)
  (ConcreteFlDraw "T" '        (0 0 0)   220  4502    3820   4146    52    125       80   30   48       0             5         48       "10.9"    52        52 36 36 300)
  ;(ConcreteFlDraw "T" '(0 0 0) 190 4515 4040 4245 65 54 70 10 94 0 5 48 10.9 65 65 36 36 300.0)
  ;bolttype螺栓公称直径
		   
)

;;;;;;;;法兰放大图绘制混塔底法兰;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defun ConcreteFlDraw(flange_type pt_flange relH relDout relDin relDpcd reltn reld_hole relL relR num_hole flange_index
	       sum_flange boltType BoltClass reltn_top reltn_down Neck_TopFlange Moment MassOfFlange
		   / scale_fl H Dout Din Dpcd tn d L R middle_line Width Width_pcd_in 
		   flpt00 flpt01 flpt02 flpt04 flpt06 flpt33 flpt31 flpt21 flpt20 flpt16 flpt12 flpt03 flpt05 flpt14 flpt13
		   flpt15 flpt11 flpt10  flpt30 flpt32 flpt51 flpt50 flpt40 flpt-10
		   mi_flpt33 mi_flpt32 mi_flpt30 mi_flpt31 mi_flpt11 mi_flpt51 mi_flpt50 mi_flpt10 mi_flpt12 mi_flpt13
		   mi_flpt14 mi_flpt15 mi_flpt16 mi_flpt21 mi_flpt20 mi_flpt40
		   bottomflname );L型法兰绘制
    ;flange_type 法兰类型
    ;pt_flange法兰绘制初始点
    ;flange_index 法兰序号
    ;sum_flange 法兰总数
    ;reld_hole 螺栓孔直径

    (setq Flange_type "T");混塔法兰强制改成"T"型法兰
    (setq scale_fl 20.0);法兰放大系数
    (setq H (* relH scale_fl));H 法兰高度
    (setq Dout (* relDout scale_fl));Dout 法兰外径
    (setq Din (* relDin scale_fl));Din 法兰内径
    (setq Dpcd (* relDpcd scale_fl));Dpcd 分度圆直径
    (setq tn (* reltn scale_fl));tn 颈厚
    (setq d (* reld_hole scale_fl));d 螺栓孔直径
    (setq L (* relL scale_fl));L颈高
    (setq R (* relR scale_fl));R 圆角
    ;法兰相邻筒节的壁厚确定
    (if (and (/= reltn_top nil) (/= reltn_top 0) (/= reltn_top ""));reltn_top：壁厚
        (setq tn_top (* reltn_top scale_fl))
        (setq tn_top tn)
    )
    (if (and (/= reltn_down nil) (/= reltn_down 0) (/= reltn_down ""))
        (setq tn_down (* reltn_down scale_fl))
        (setq tn_down tn)
    )
    (setq middle_line (* 30 scale_fl))
    (setq Width (/ (- Dout Din) 2))	;;;     法兰宽度
    (setq Width_pcd_in (/ (- Dpcd Din) 2)) ;螺栓孔中心到法兰内径的距离
    ;关键点;;;;
    (setq flpt01 pt_flange)
    (setq flpt02 (polar flpt01 0 (/ Width 2)))
    (setq flpt04 (polar flpt02 0 Width_pcd_in)) 
    (setq flpt06 (polar flpt02 0 Width)) 
    (setq flpt33 (polar flpt06 (/ pi 2) H))  
    (setq flpt31 (polar flpt33 pi tn))
    (setq flpt21 (polar flpt31 (* pi 1.5) (- L R)))
    (setq flpt20 (polar flpt21 pi R))
    (setq flpt16 (polar flpt20 (* pi 1.5) R)) 
    (setq flpt12 (polar flpt02 (/ pi 2) (- H L)))
    (setq flpt03 (polar flpt04 pi (/ d 2)))
    (setq flpt05 (polar flpt04 0 (/ d 2)))
    (setq flpt14 (polar flpt04 (/ pi 2) (- H L)))
    (setq flpt13 (polar flpt14 pi (/ d 2)))
    (setq flpt15 (polar flpt14 0 (/ d 2)))
    (setq flpt11 (polar flpt01 (/ pi 2) (- H L)))
    (setq flpt10 (polar flpt11 pi (/ H 3)))
    (setq flpt00 (polar flpt01 pi (/ H 2)))
    (setq flpt30 (polar flpt11 (/ pi 2) L))
    

    (setq flpt40 (polar flpt14 (/ pi 2) middle_line));螺栓中心线上点
    (setq flpt-10 (polar flpt04 (* pi 1.5) middle_line));螺栓中心线下点
    ;法兰中对齐的关键点

    (if (< tn_top tn)
      (progn
        (setq flpt325 (polar flpt33 pi (/ (- tn tn_top) 2)))
	(setq flpt32 (polar flpt325 pi tn_top))
      )
      (progn
        (setq flpt325 (polar flpt33 0 (/ (- tn_top tn) 2)))
	(setq flpt32 (polar flpt325 pi tn_top))
      )
    );end if
    (setq flpt51 (polar flpt325 (* pi 0.5) L))
    (setq flpt50 (polar flpt51 pi tn_top))
  
    ;;;;;对称点
    (setq mi_flpt33 (miFlange flpt33 H))
    
    (setq mi_flpt30 (miFlange flpt30 H))
    (setq mi_flpt31 (miFlange flpt31 H))
    (setq mi_flpt11 (miFlange flpt11 (- H L)))

    (setq mi_flpt10  (miFlange flpt10 (- H L)))			
    (setq mi_flpt12 (miFlange flpt12 (- H L)))			
    (setq mi_flpt13 (miFlange flpt13 (- H L)))			
    (setq mi_flpt14 (miFlange flpt14 (- H L)))
    (setq mi_flpt15 (miFlange flpt15 (- H L)))			
    (setq mi_flpt16 (miFlange flpt16 (- H L)))			
    (setq mi_flpt21 (miFlange flpt21 (+ (- H L) R)))
    (setq mi_flpt20 (miFlange flpt20 (+ (- H L) R)))
    (setq mi_flpt40 (polar mi_flpt14 (* pi 1.5) middle_line))
    ;中对齐
    (if (> tn_down tn)
      (progn
        (setq mi_flpt34 (polar mi_flpt33 0 (/ (- tn_down tn) 2)))
	(setq mi_flpt315 (polar mi_flpt34 pi tn_down))
      )
      (progn
        (setq mi_flpt34 (polar mi_flpt33 pi (/ (- tn tn_down) 2)))
	(setq mi_flpt315 (polar mi_flpt34 pi tn_down))
      )
    );end if
    (setq mi_flpt32 (polar mi_flpt34 pi tn_down))
    (setq mi_flpt51 (polar mi_flpt34 (* pi 1.5) L))
    (setq mi_flpt50 (polar mi_flpt51 pi tn_down))
  
    ;连线成法兰
    (command "layer" "M" "1轮廓实线层" "")
    ;绘制L和T型法兰的公共部分

  

    (command "line" flpt30 flpt33 "")

    (command "line" flpt10 flpt16 "")

    (command "line" flpt31 flpt21 "")

  
    (command "arc" flpt16 "c" flpt20 flpt21);倒角

    (command "line" flpt12 flpt02 "")

    (command "line" flpt03 flpt13 "")
    (command "line" flpt15 flpt05 "")
    (command "line" flpt325 flpt51 "")
    (command "line" flpt32 flpt50 "")
  
   

        (setq Da_outer 4740);T型法兰外径
        (setq Dm_outer 4655);T型法兰外分度圆
	(tfldadmouter T_D T_D relDout reltn relDpcd relDin);得到T型法兰的外径和外分度圆
        (setq T_D (* Da_outer scale_fl))
        (setq T_D_m (* Dm_outer scale_fl))
	(setq tflpt22 (polar flpt21 0 tn))
	(setq tflpt23 (polar tflpt22 0 R))
	(setq tflpt17 (polar flpt16 0 (+ tn (* R 2))))
	(setq tflpt111 (polar flpt12 0 (/ (- T_D Din)2)))
	(setq tflpt19 (polar tflpt111 pi (/ (- T_D T_D_m) 2)))
	(setq tflpt110 (polar tflpt19 0 (/ d 2)))
	(setq tflpt18 (polar tflpt19 pi (/ d 2)))
	(setq tflpt010 (polar tflpt111 (* 1.5 pi) (- H L)))
	(setq tflpt08 (polar tflpt19 (* 1.5 pi) (- H L)))
	(setq tflpt09 (polar tflpt08 0 (/ d 2)))
	(setq tflpt07 (polar tflpt08 pi (/ d 2)))
	(setq DTout T_D)
	(setq DTpcd T_D_m)
	(setq tflpt41 (polar tflpt19 (/ pi 2) middle_line))
	(setq tflpt-11 (polar tflpt08 (* pi 1.5) middle_line))
        ;绘制公共直线
        (command "line" tflpt17 tflpt111 "");右侧上端面横线
	;(command "line" tflpt18 tflpt07 "");上法兰右侧螺栓孔左线
	;(command "line" tflpt110 tflpt09 "");上法兰右侧螺栓孔右线
        (command "line" flpt33 tflpt22 "");;T上法兰右侧颈右侧竖线
	(command "line" tflpt111 tflpt010 "");T上法兰右侧最右竖线
	(command "arc" tflpt22 "c" tflpt23 tflpt17);T上法兰右侧圆角的弧

	    (command "line" flpt00 tflpt010 "");下端面直线
	    ;;;绘制样条曲线
	    (command "layer" "M" "2细线层" "")
	    (command "spline" flpt51 flpt50 flpt30 flpt10 flpt00 "" "" "")

	    (Middleline flpt-10 flpt40 (* 20 scale_fl));左侧中心红色
	    ;(Middleline tflpt-11 tflpt41 (* 20 scale_fl));
	    ;名称
	    
	    ;(FlangeZoomTitle (polar flpt40 (* pi 0.5) (* 25 mScale)) (- sum_flange flange_index) (/ mscale scale_fl))


	    ;标注
	    ;直径标注
 
	    (DimflD_mid (polar tflpt22 pi Dout) tflpt22  (/ 1 scale_fl) 0 reld_hole (polar flpt01 (* pi 1.5) (* 150 scale_fl)) 0 );外径 
	    (DimflD_mid (polar flpt04 pi Dpcd) flpt04  (/ 1 scale_fl) num_hole reld_hole (polar flpt01 (* pi 1.5) (* 100 scale_fl)) 0 );内分度圆
  
	    (DimflD_mid (polar flpt02 pi Din) flpt02  (/ 1 scale_fl) 0 reld_hole (polar flpt01 (* pi 1.5) (* 50 scale_fl)) 0 ) ;内径标注
	    (DimflD_mid (polar tflpt010 pi DTout) tflpt010  (/ 1 scale_fl) 0 reld_hole (polar flpt01 (* pi 1.5) (* 200 scale_fl)) 0 );最外径
  
	    ;(DimflD_mid (polar tflpt08 pi DTpcd) tflpt08  (/ 1 scale_fl) (/ num_hole 2) reld_hole (polar flpt01 (* pi 1.5) (* 200 scale_fl)) 0 ) ;外分度圆
	    ;法兰厚度标注
	    (dimFlange_thick2 flpt12 flpt02 (/ 1 scale_fl ) 0 (- (/ (- H L) 2)) )
             (dimFlange_h flpt33 tflpt010 scale_fl 2500);法兰高度标注
	    ;法兰脖子标注
            (dimh_flange flpt21 (polar flpt21 0 tn) (/ 1.0 scale_fl) 0 1500 0);法兰脖子厚度标注
	    
	    (scaleDim flpt50 (polar flpt50 0 tn_top) scale_fl 1);法兰高度标注
	    ;法兰质量标注

	    (flmasslead flange_index Flange_type tflpt010 MassOfFlange (- relH relL));法兰重量标注

	    ;打剖面线
	    (bhatch_pt tflpt110 tflpt010 scale_fl 0)
	    (bhatch_pt flpt12 flpt03 scale_fl 0)
	    (bhatch_pt flpt15 flpt06 scale_fl 0)
	    (bhatch_pt flpt50 flpt33 scale_fl 90)
  ;圆角标注
  (setq flpt1-20 (polar flpt20 (* pi 1.75) R))
  (dimRadialR flpt20 flpt1-20 200 (/ 1 scale_fl ))
  (setq flpt1-21 (polar tflpt23 (* pi 1.25) R))
  (dimRadialR tflpt23 flpt1-21 200 (/ 1 scale_fl ))

     
  (setq bolt_zb_list (append bolt_zb_list (list flpt40) ))
);法兰绘制函数终结括号

;;;;;;;;;;;;;;;;中对齐主体相关绘制;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defun Body_insert_ali(/ i k)
  ;;;;塔架主体中心线绘制
  (MiddleLine (nth 0 sthptlist) (bnth -1 sthptlist) 100);底法兰中心下点，顶法兰上中心点
  ;;;;塔架主体绘制
  ;塔架标高插入
  ;(elevation (value retTower 0 0));塔架标高
  (setq eleName (elevation (value retTower 0 0)))
  (command "insert" eleName "S" mscale (nth 0 sthptllist) "");插入标高尺寸,放大比例，插入原点
  ;(command "-scalelistedit" "R" "Y" "e")
  ;主体绘制
  (setq i 0);开始的值
  (setq k section_qty);段数
  ;(setq k 0)
  (while (< i k )
    (secdraw_mid i)
    (setq i (+ i 1))
  )
  ;塔架主体总高度标注
  (ldimv (nth 0 sthptllist) (bnth -1 sthptllist) -5000);塔架总高度

  ;主体上法兰序号的绘制
  (enlargesymbol)
  (print "主体绘制成功！")
)
;;;;;;;;Body_insert函数结束;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;;;;;;;;;;;;;;;中对齐塔架外轮廓线绘制;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defun Body_insert_ali_w(/ i k)
  ;主体绘制
  (setq i 0);开始的值
  (setq k section_qty);段数
  (while (< i k )
    (secdraw_mid_w i);中对齐塔架的外轮廓线
    (setq i (+ i 1))
  )
);end
;;;;;;;;Body_insert函数结束;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


;;;主体上的关键点获取;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defun keypt_ali(/ flstart j k sthlist h_upper_list sthpt_upper_list h
		 h_upper pt_x_upper pt_x shellradius shellradius_upper shellthick shellmaterial
		 sthptl sthptr sthptl_upper sthptr_upper  )
  (setq flstart (car pos));开始的位置
  (setq j  flstart);塔筒段下法兰在主体表中开始的位置
  (setq k  (bnth -1 pos));塔筒段上法兰在主体表中结束的位置
  ;新建存储值的一些列表
  (setq sthlist '())
  (setq sthptlist '());标高中心点列表,全局变量
  (setq sthptllist '());标高左点列表,全局变量
  (setq sthptrlist '());标高右点列表,全局变量
  (setq shellthicklist '());筒节壁厚列表,全局变量
  (setq shellMaterialList '());筒节材料列表,全局变量
  (setq sthptllist_upper '());全局变量
  (setq sthptrlist_upper '());全局变量

  (setq h_upper_list '())
  (setq sthpt_upper_list '())
  (setq pa_concrete '(16815 90220 0))
  ;得到关键点的坐标

  (while (<= j k)
     (if (> bflange_hole 79)
	(progn
     (setq h (* (- (value retTower j 0) (value retTower 0 0)) 1000));得到标高值（相对底法兰高度）并转换成mm
     (setq sthlist (cons h sthmidlist));把标高加到列表中
     (setq pt_x (polar pa_concrete (/ pi 2) h));相对选择点向上加标高
     (setq sthptlist (cons pt_x sthptlist))
	 (setq h_upper (* (- (value retTower j 2) (value retTower 0 0)) 1000) );每个筒节的上相对标高-相对底法兰的高度
     (setq pt_x_upper (polar pa_concrete (/ pi 2) h_upper));相对选择点向上加标高
     (setq h_upper_list (cons h_upper sthmidlist));把标高加到列表中  
     (setq sthpt_upper_list (cons pt_x_upper  sthpt_upper_list))
	);progn
	(progn
     (setq h (* (value retTower j 0) 1000));得到标高值并转换成mm
     (setq sthlist (cons h sthmidlist));把标高加到列表中
     (setq pt_x (polar pa (/ pi 2) h));相对选择点向上加标高
     (setq sthptlist (cons pt_x sthptlist))
     (setq h_upper (* (value retTower j 2) 1000));每个筒节的上标高
     (setq pt_x_upper (polar pa (/ pi 2) h_upper));相对选择点向上加标高
     (setq h_upper_list (cons h_upper sthmidlist));把标高加到列表中   
     (setq sthpt_upper_list (cons pt_x_upper  sthpt_upper_list))
    );progn
	 );end if
     ;;;;;外半径的判断
     (setq shellradius  (value retTower j 1));标高处的外半径
     (setq shellradius_upper (value retTower j 3) );上标高处的外半径
    
     ;;;;;;;;;;;
     (setq shellthick (value retTower j 4));壁厚
	 (setq shellmaterial (value retTower j 5));主体材料
     (setq sthptl (polar pt_x pi (/ (- shellradius shellthick) 2)));横线最左点
     (setq sthptr (polar pt_x 0 (/ (- shellradius shellthick) 2)));横线右点
     (setq sthptllist (cons sthptl sthptllist));把左坐标填到列表中
     (setq sthptrlist (cons sthptr sthptrlist));把右坐标填到列表中


     (setq sthptl_upper (polar pt_x_upper pi (/ (- shellradius_upper shellthick) 2)));横线最左点上
     (setq sthptr_upper (polar pt_x_upper 0 (/ (- shellradius_upper shellthick) 2 )));横线右点上  
     (setq sthptllist_upper (cons sthptl_upper sthptllist_upper));把左坐标填到列表中上
     (setq sthptrlist_upper (cons sthptr_upper sthptrlist_upper));把右坐标填到列表中上

    ;(print shellradius)
    ;(print shellradius_upper)
    
     (setq j (+ j 1))

    (setq shellthicklist (cons shellthick shellthicklist));把壁厚填到列表中
	(setq shellMaterialList (cons shellmaterial shellMaterialList));把主体材料填到列表中
  )
  ;把最顶点的加进来
  (if (> bflange_hole 79)
  (progn 
  (setq h (* (- (value retTower k 2) (value retTower 0 0)) 1000));塔架主体的顶点标高-相对底法兰的高度，并转换成mm
  (setq sthlist (cons h sthmidlist));把标高加到列表中
  (setq pt_x (polar pa_concrete (/ pi 2) h));相对选择点向上加标高
  (setq sthptlist (cons pt_x sthptlist))
  );progn
  (progn
  (setq h (* (value retTower k 2) 1000));塔架主体的顶点标高，并转换成mm
  (setq sthlist (cons h sthmidlist));把标高加到列表中
  (setq pt_x (polar pa (/ pi 2) h));相对选择点向上加标高
  (setq sthptlist (cons pt_x sthptlist))
  );progn
  );end if
  (setq shellradius (/ (value retTower k 3) 2.0));标高处的外半径
  (setq sthptl (polar pt_x pi shellradius));横线最左点
  (setq sthptr (polar pt_x 0 shellradius));横线右点  
  (setq sthptllist (cons sthptl sthptllist));把左坐标填到列表中
  (setq sthptrlist (cons sthptr sthptrlist));把右坐标填到列表中

  (setq shellradius_upper (/ (value retTower k 3) 2.0));
  (setq sthptl_upper (polar pt_x pi shellradius_upper));
  (setq sthptr_upper (polar pt_x 0 shellradius_upper)); 
  (setq sthptllist_upper (cons sthptl_upper sthptllist_upper));
  (setq sthptrlist_upper (cons sthptr_upper sthptrlist_upper));
  
  ;;;;;;列表翻转，得到从下往上的顺序
  (setq sthlist (reverse sthlist))
  (setq sthptlist (reverse sthptlist))
  (setq sthptllist (reverse sthptllist))
  (setq sthptrlist (reverse sthptrlist))
  (setq shellthicklist (reverse shellthicklist))
  (setq shellMaterialList (reverse shellMaterialList))
  
  (setq sthptllist_upper (reverse sthptllist_upper))
  (setq sthptrlist_upper (reverse sthptrlist_upper))


)
;;;;;;;keypt_ali函数结束;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


;;;;塔架主体绘制函数
(defun secdraw_mid(xsec / flstart j k h pt_x shellradius shellptl shellptr
		m n shellptlx shellptrx shellptlxu shellptrxu shellptlx_x
		shellptlxu_x thick material hh pt_x shellradius shellptl);底段绘制


  ;下法兰绘制
  (if (or (/= xsec 0) (and (= xsec 0) (<= bflange_hole 70)))  ;底法兰螺栓孔直径>70
      (bflangedraw (nth (nth xsec pos) sthptlist ) xsec);底法兰绘制
  );end if
  ;绘制主体的横线，包括上下法兰的最上和最下的横线
  (setq m (+ (nth xsec pos) 2));初始值
  (setq n (nth (+ xsec 1) pos));此段的结束位置
  (while (< m (- n 1));画筒节的横线
    (setq shellptlx (nth m sthptllist))  
    (setq shellptrx (nth m sthptrlist))
    ;(addline shellptlx shellptrx)
    (setq m (+ m 1))
  )
  
  ;绘制主体的左右侧竖线
  (setq m (+ (nth xsec pos) 1));从1开始，不画底法兰的直线参数
  (while (< m (- n 1))

    (setq shell_thick (nth m shellthicklist));筒节的壁厚
    (setq shell_thick_upper (nth (+ m 1) shellthicklist))
    
    (setq shellptlx (nth m sthptllist));左下点
    (setq shellptrx (nth m sthptrlist));右下点

    (setq shellptlxu (nth m sthptllist_upper));左上点
    (setq shellptrxu (nth m sthptrlist_upper));右上点

    (setq shellptlx_x (car shellptlx));shellptlx的横坐标
    (setq shellptlxu_x (car shellptlxu));shellptlxu的横坐标

    (adddashline shellptlx shellptlxu);绘制左虚直线
    (adddashline shellptrx shellptrxu);绘制右虚直线

    ;(addline shellptlx shellptlxu)
    ;(addline shellptrx shellptrxu)
    
    ;筒节高度标注
    (ldimv shellptlx shellptlxu -1300);,坐标1，坐标2，标注距离，往左标注
    ;每段的中径标注
    (if (= (rtos shellptlx_x) (rtos shellptlxu_x));若筒节上下的横坐标相同，直段
      (if (= m (- n 3))
        (dimd_mid shellptlxu shellptrxu 1.0 -900 -100 60 "(中径/MD)");
      );end if
    );end if
    ;筒节直径标注
    (if (/= (rtos shellptlx_x) (rtos shellptlxu_x));若筒节上下的横坐标不同，是锥段，标注尺寸
      (progn
	    (if (/= m (- n 1))
            (dimd_mid shellptlxu shellptrxu 1.0 -900 -100 60 "(中径/MD)");不标注最上筒节的尺寸
        )
        (if (= m (+ (nth xsec pos) 1))
            (dimd_mid shellptlx shellptrx 1.0 80 0 300 "(中径/MD)");标注最下筒节下端直径的尺寸
        )
	    );end progn	  
    )
    ;壁厚标注
    (setq thick (nth m shellthicklist));得到壁厚
	(setq material (nth m shellMaterialList));得到塔筒主体材料
	  
	(if (/=  materile_style 2)
		(Thick_drw thick material shellptrx shellptrxu 1200.0 (/ pi 6));thick:壁厚,下坐标，上坐标
		(Thick_drw_1 thick material shellptrx shellptrxu 1200.0 (/ pi 6));thick:壁厚,下坐标，上坐标
	)
    ; (cond
      ; ((and (= topfltype "3MW_S_New_TopFlange") (= m 1)) 
        ; (Thick_drw thick material shellptrx shellptrxu 2850.0 (/ pi 5));thick:壁厚,下坐标，上坐标
      ; )
      ; ((and (= topfltype "3MW_S_New_TopFlange") (= m 2)) 
        ; (Thick_drw thick material shellptrx shellptrxu 2150.0 (/ pi 4.5));thick:壁厚,下坐标，上坐标
      ; )
      ; ; ((or(= materile_style 0)(= materile_style 1))
        ; ; (Thick_drw thick material shellptrx shellptrxu 1200.0 (/ pi 6));thick:壁厚,下坐标，上坐标
      ; ; )
	   ; (t
        ; (Thick_drw thick material shellptrx shellptrxu 1200.0 (/ pi 6));thick:壁厚,下坐标，上坐标
      ; )
    ; );cond
    ;(Thick_drw thick shellptrx shellptrxu 2150.0 (/ pi 6));thick:壁厚,下坐标，上坐标

    (command "layer" "M" "1轮廓实线层" "");为了改正标注壁厚时的线型
    (setq m (+ m 1))
  )
  ;此段的顶法兰绘制
  (uflangedraw (nth (- (nth (+ xsec 1) pos) 1) sthptlist ) (+ xsec 1))
  ;塔段总高度标注
  (if (= xsec (- section_qty 1))
    ;顶段的尺寸标注
    (ldimv (nth (nth xsec pos) sthptllist) (bnth -1 sthptllist) -3300)
    ;其他段尺寸标注
    (ldimv (nth (nth xsec pos) sthptllist) (nth (nth (+ xsec 1) pos) sthptllist) -3300)
  )

)
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;




;;;;中对齐塔架主体实线轮廓绘制
(defun secdraw_mid_w(xsec / flstart j k h pt_x shellradius shellptl shellptr
		m n shellptlx shellptrx shellptlxu shellptrxu shellptlx_x
		shellptlxu_x thick hh pt_x shellradius shellptl);底段绘制
  (if (> bflange_hole 79)
    (setq m (+ (nth xsec pos) 1));初始值
    (setq m (+ (nth xsec pos) 2));初始值
  );end if
  (setq n (nth (+ xsec 1) pos));此段的结束位置
  (while (< m (- n 1));画筒节的横线
    (setq shellptlx (nth m sthptllist))
    (setq shellptrx (nth m sthptrlist))
    (command "layer" "M" "1轮廓实线层" "")
    (addline shellptlx shellptrx)
    (setq m (+ m 1))
  )
  ;绘制主体的左右侧竖线
  (setq m (+ (nth xsec pos) 1));从1开始，不画底法兰的直线参数
  (while (< m (- n 1))
    (setq shellptlx (nth m sthptllist));左下点
    (setq shellptrx (nth m sthptrlist));右下点
    (setq shellptlxu (nth (+ m 1) sthptllist));左上点
    (setq shellptrxu (nth (+ m 1) sthptrlist));右上点
    (setq shellptlx_x (car shellptlx));shellptlx的横坐标
    (setq shellptlxu_x (car shellptlxu));shellptlxu的横坐标
    (command "layer" "M" "1轮廓实线层" "");为了改正标注壁厚时的线型
    (addline shellptlx shellptlxu);绘制左直线
    (addline shellptrx shellptrxu);绘制右直线
    (setq m (+ m 1))
  );end while
);end secdraw_mid_w
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


;;;虚直线绘制函数;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defun adddashline (pt1 pt2 / line1)
  (setq line1 (vla-addline myms (vlax-3d-point pt1) (vlax-3d-point pt2) ) )
  ;(vlax-dump-object line1 t)
  
  (vla-put-layer line1 "4虚线层")
  (vla-put-linetypescale line1 0.2)
  (vla-update line1)
  
  
  ;(vlax-dump-object line1 t)

)
;;;adddashline函数结束;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;;;;;;;法兰放大图绘制;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defun flange_draw_mid(flange_type pt_flange relH relDout relDin relDpcd reltn reld_hole relL relR num_hole flange_index
	       sum_flange boltType BoltClass reltn_top reltn_down Neck_TopFlange Moment MassOfFlange
		   / scale_fl H Dout Din Dpcd tn d L R middle_line Width Width_pcd_in 
		   flpt00 flpt01 flpt02 flpt04 flpt06 flpt33 flpt31 flpt21 flpt20 flpt16 flpt12 flpt03 flpt05 flpt14 flpt13
		   flpt15 flpt11 flpt10  flpt30 flpt32 flpt51 flpt50 flpt40 flpt-10
		   mi_flpt33 mi_flpt32 mi_flpt30 mi_flpt31 mi_flpt11 mi_flpt51 mi_flpt50 mi_flpt10 mi_flpt12 mi_flpt13
		   mi_flpt14 mi_flpt15 mi_flpt16 mi_flpt21 mi_flpt20 mi_flpt40
		   bottomflname );L型法兰绘制
    ;flange_type 法兰类型
    ;pt_flange法兰绘制初始点
    ;flange_index 法兰序号
    ;sum_flange 法兰总数
    ;reld_hole 螺栓孔直径
    (setq scale_fl 20.0);法兰放大系数
    (setq H (* relH scale_fl));H 法兰高度
    (setq Dout (* relDout scale_fl));Dout 法兰外径
    (setq Din (* relDin scale_fl));Din 法兰内径
    (setq Dpcd (* relDpcd scale_fl));Dpcd 分度圆直径
    (setq tn (* reltn scale_fl));tn 颈厚
    (setq d (* reld_hole scale_fl));d 螺栓孔直径
    (setq L (* relL scale_fl));L颈高
    (setq R (* relR scale_fl));R 圆角
    ;法兰相邻筒节的壁厚确定
    (if (and (/= reltn_top nil) (/= reltn_top 0) (/= reltn_top ""));reltn_top：壁厚
        (setq tn_top (* reltn_top scale_fl))
        (setq tn_top tn)
    )
    (if (and (/= reltn_down nil) (/= reltn_down 0) (/= reltn_down ""))
        (setq tn_down (* reltn_down scale_fl))
        (setq tn_down tn)
    )
    (setq middle_line (* 30 scale_fl))
    (setq Width (/ (- Dout Din) 2))	;;;     法兰宽度
    (setq Width_pcd_in (/ (- Dpcd Din) 2)) ;螺栓孔中心到法兰内径的距离
    ;关键点;;;;
    (setq flpt01 pt_flange)
    (setq flpt02 (polar flpt01 0 (/ Width 2)))
    (setq flpt04 (polar flpt02 0 Width_pcd_in)) 
    (setq flpt06 (polar flpt02 0 Width)) 
    (setq flpt33 (polar flpt06 (/ pi 2) H))  
    (setq flpt31 (polar flpt33 pi tn))
    (setq flpt21 (polar flpt31 (* pi 1.5) (- L R)))
    (setq flpt20 (polar flpt21 pi R))
    (setq flpt16 (polar flpt20 (* pi 1.5) R)) 
    (setq flpt12 (polar flpt02 (/ pi 2) (- H L)))
    (setq flpt03 (polar flpt04 pi (/ d 2)))
    (setq flpt05 (polar flpt04 0 (/ d 2)))
    (setq flpt14 (polar flpt04 (/ pi 2) (- H L)))
    (setq flpt13 (polar flpt14 pi (/ d 2)))
    (setq flpt15 (polar flpt14 0 (/ d 2)))
    (setq flpt11 (polar flpt01 (/ pi 2) (- H L)))
    (setq flpt10 (polar flpt11 pi (/ H 3)))
    (setq flpt00 (polar flpt01 pi (/ H 2)))
    (setq flpt30 (polar flpt11 (/ pi 2) L))
    

    (setq flpt40 (polar flpt14 (/ pi 2) middle_line));螺栓中心线上点
    (setq flpt-10 (polar flpt04 (* pi 1.5) middle_line));螺栓中心线下点
    ;法兰中对齐的关键点

    (if (< tn_top tn)
      (progn
        (setq flpt325 (polar flpt33 pi (/ (- tn tn_top) 2)))
	(setq flpt32 (polar flpt325 pi tn_top))
      )
      (progn
        (setq flpt325 (polar flpt33 0 (/ (- tn_top tn) 2)))
	(setq flpt32 (polar flpt325 pi tn_top))
      )
    );end if
    (setq flpt51 (polar flpt325 (* pi 0.5) L))
    (setq flpt50 (polar flpt51 pi tn_top))
  
    ;;;;;对称点
    (setq mi_flpt33 (miFlange flpt33 H))
    
    (setq mi_flpt30 (miFlange flpt30 H))
    (setq mi_flpt31 (miFlange flpt31 H))
    (setq mi_flpt11 (miFlange flpt11 (- H L)))

    (setq mi_flpt10  (miFlange flpt10 (- H L)))			
    (setq mi_flpt12 (miFlange flpt12 (- H L)))			
    (setq mi_flpt13 (miFlange flpt13 (- H L)))			
    (setq mi_flpt14 (miFlange flpt14 (- H L)))
    (setq mi_flpt15 (miFlange flpt15 (- H L)))			
    (setq mi_flpt16 (miFlange flpt16 (- H L)))			
    (setq mi_flpt21 (miFlange flpt21 (+ (- H L) R)))
    (setq mi_flpt20 (miFlange flpt20 (+ (- H L) R)))
    (setq mi_flpt40 (polar mi_flpt14 (* pi 1.5) middle_line))
    ;中对齐
    (if (> tn_down tn)
      (progn
        (setq mi_flpt34 (polar mi_flpt33 0 (/ (- tn_down tn) 2)))
	(setq mi_flpt315 (polar mi_flpt34 pi tn_down))
      )
      (progn
        (setq mi_flpt34 (polar mi_flpt33 pi (/ (- tn tn_down) 2)))
	(setq mi_flpt315 (polar mi_flpt34 pi tn_down))
      )
    );end if
    (setq mi_flpt32 (polar mi_flpt34 pi tn_down))
    (setq mi_flpt51 (polar mi_flpt34 (* pi 1.5) L))
    (setq mi_flpt50 (polar mi_flpt51 pi tn_down))
  
    ;生成螺栓
    (if (<= boltType 72)
        (bolt boltType (/ (- H L) scale_fl) )
    )
  
    ;连线成法兰
    (command "layer" "M" "1轮廓实线层" "")
    ;绘制L和T型法兰的公共部分
	(if (> tn_top tn)
    (command "line" flpt30 flpt325 "")
	(command "line" flpt30 flpt33 "")
	);end if 
    (command "line" flpt10 flpt16 "")			
    (command "line" flpt31 flpt21 "")			
    (command "arc" flpt16 "c" flpt20 flpt21);倒角
    (command "line" flpt12 flpt02 "")			
    (command "line" flpt03 flpt13 "")
    (command "line" flpt15 flpt05 "")
    (command "line" flpt325 flpt51 "")
    (command "line" flpt32 flpt50 "")
    (if (= flange_type "L");
      (progn;如果是L型法兰
        (if (and (/= (value retEmbedded 0 0) "") (/= (value retEmbedded 0 0) nil) (= flange_index 0))
	  (progn ;如果基础那里有数,并且是底法兰
            (setq mi_flpt31 (polar mi_flpt33 pi (* (value retEmbedded 2 0) scale_fl)))
	    (setq mi_flpt21 (polar mi_flpt31 (/ pi 2) (- L R) ))
            (setq mi_flpt20 (polar mi_flpt21 pi R))
	    (setq mi_flpt16 (polar mi_flpt20 (/ pi 2) R));;;;;
	    (setq mi_flpt51 (polar mi_flpt33 (* pi 1.5) L))
	    (setq mi_flpt51 (polar mi_flpt51 0 (/  (- (* (value retEmbedded 4 0) scale_fl) Dout ) 2 )))
	    (setq mi_flpt50 (polar mi_flpt51 pi (* (value retEmbedded 5 0) scale_fl)))
            (setq mi_pt_h11 (polar mi_flpt51 (/ pi 2) L))
	    (setq mi_pt_t11 (polar mi_pt_h11 pi (* (value retEmbedded 5 0) scale_fl) ))
	    (setq ts (* (value retEmbedded 2 0) scale_fl));基础环上法兰脖子厚
	  )
	  (setq ts tn);若不是底法兰，或基础环数据那里为空
	)
        ;(setq repline2_mi (vla-mirror repline2 (vlax-3d-point rept1 ) (vlax-3d-point rept5)))
	;绘制L法兰特有的部分
	(command "line" flpt00 flpt03 "");
	(command "line" flpt05 flpt06 "");不再穿过螺栓
	(command "line" flpt33 flpt06 "");右侧壁直线
	;绘制对称法兰
        ;视图名绘制,点，序号，比例
        (FlangeZoomTitle (polar flpt40 (* pi 0.5) (* 25 mScale)) (- sum_flange flange_index) (/ mscale scale_fl))
	(command "insert" boltName "S" scale_fl mi_flpt14 "")			
        (if (= flange_index 0) 
          (progn
            (if (or (= (value retEmbedded 0 0) "") (= (value retEmbedded 0 0) nil)) 
              (progn;如果底法兰并且基础环法兰那里没有数据
	        (command "layer" "M" "4虚线层" "")
		(command "line" mi_flpt30 mi_flpt33 "")
		(command "line" mi_flpt33 mi_flpt51 "")
		(command "line" mi_flpt31 mi_flpt50 "")
              )
	      (progn;如果是底法兰但是有数据
                (command "layer" "M" "1轮廓实线层" "")		
		(command "line" mi_flpt30 mi_pt_h11 "")
		(command "line" mi_pt_h11 mi_flpt51 "")
		(command "line" mi_pt_t11 mi_flpt50 "") 
              )
	    )
          )
	  (progn;如果不是底法兰
	     (if (> tn_down tn)
		   (command "line" mi_flpt30 mi_flpt34 "")
		   (command "line" mi_flpt30 mi_flpt33 "")
		 );end if
            
            (command "line" mi_flpt34 mi_flpt51 "")
	    (command "line" mi_flpt32 mi_flpt50 "")
	  )
	)
        (command "line" mi_flpt10  mi_flpt16 "")			
        (command "line" mi_flpt31 mi_flpt21 "")			
        (command "arc" mi_flpt21 "c" mi_flpt20 mi_flpt16)
        (command "line" mi_flpt12 flpt02 "")
        (command "line" flpt03 mi_flpt13 "")
        (command "line" mi_flpt15 flpt05 "")
        (command "line" mi_flpt33 flpt06 "")
	
	;(Middleline flpt40 mi_flpt40 (* 20 scale_fl));螺栓中心的线

	(command "layer" "M" "2细线层" "")
	(command "spline" flpt51 flpt50 flpt30 flpt10 flpt00  mi_flpt10  mi_flpt30 mi_flpt50 mi_flpt51 "" "" "");绘制剖切的弧线
		;;打剖面线,对角
        (bhatch_pt flpt12 flpt03 scale_fl 0)
	(bhatch_pt flpt15 flpt06 scale_fl 0)
	(bhatch_pt flpt50 flpt33 scale_fl 90) 
	(bhatch_pt flpt02 mi_flpt13 scale_fl 90)
	(bhatch_pt flpt05 mi_flpt33 scale_fl 90)
	(bhatch_pt mi_flpt315 mi_flpt51 scale_fl 0)
	;直径标注
	(if (and (/= (value retEmbedded 4 0 ) nil) (/= (value retEmbedded 4 0 ) ""))
	  (if (and (/= relDout (value retEmbedded 4 0 )) (= flange_index 0));如果底法兰外径与基础环筒体外径不相等
            ;;;标注筒体直径
	    (progn
	      (DimflD_mid (polar mi_flpt51 pi (* (value retEmbedded 4 0) scale_fl)) mi_flpt51  (/ 1 scale_fl) 0 reld_hole (polar mi_flpt30 (* pi 1.5) (* 200 scale_fl)) 0 );基础环筒体标注
	    )
	  )
	)
	(DimflD_mid (polar mi_flpt33 pi Dout) mi_flpt33  (/ 1 scale_fl) 0 reld_hole (polar mi_flpt30 (* pi 1.5) (* 150 scale_fl)) -67.5 );法兰外径标注
	(DimflD_mid (polar mi_flpt14 pi Dpcd) mi_flpt14  (/ 1 scale_fl) num_hole reld_hole (polar mi_flpt30 (* pi 1.5) (* 100 scale_fl)) 0 );分度圆直径标注
	(DimflD_mid (polar mi_flpt12 pi Din) mi_flpt12  (/ 1 scale_fl) 0 reld_hole (polar mi_flpt30 (* pi 1.5) (* 50 scale_fl)) 0 );内径标注
	;法兰厚度和高度标注
	(dimFlange_thick2 flpt12 flpt02 (/ 1 scale_fl ) 0 (- (/ (- H L) 2)) );上法兰度厚度标注
	(ldimv2 flpt33 flpt06 (/ 1 scale_fl ) 0 2500);上法兰高度标注
	(dimFlange_thick2 mi_flpt12 flpt02 (/ 1 scale_fl ) 0 (- (/ (- H L) 2)) );对称的下法兰度厚度标注
	(ldimv2 mi_flpt33 flpt06 (/ 1 scale_fl ) 0 2500);下法兰高度标注
	;法兰脖子和连接筒体厚度标注
        (command "zoom" "w" mi_flpt51 mi_flpt31)
        (command "regen")

	(dimh_flange flpt21 (polar flpt21 0 tn) (/ 1.0 scale_fl) (- 0 (* 10 scale_fl) 100) 200 0);上法兰脖子厚度标注
	
        (scaleDim flpt50 (polar flpt50 0 tn_top) scale_fl 1);上法兰上筒体厚度标注
	
        (dimh_flange mi_flpt50 mi_flpt51 (/ 1.0 scale_fl) (- 0 (* 130 (/ mscale scale_fl))) 200 0);基础环筒体厚度标注
	
        (scaleDim mi_flpt21 (polar mi_flpt21 0 ts) scale_fl 0);下法兰脖子厚度标注
	;法兰质量标注
	(flmasslead FlangeIndex Flange_type flpt06 MassOfFlange (- relH relL));法兰重量标注

      );L型法兰绘制结束	    
      (progn;如果是T型法兰
        (setq Da_outer (value retFlange flange_index 13));T型法兰外径
        (setq Dm_outer (value retFlange flange_index 14));T型法兰外分度圆
	(tfldadmouter T_D T_D relDout reltn relDpcd relDin);得到T型法兰的外径和外分度圆
        (setq T_D (* Da_outer scale_fl))
        (setq T_D_m (* Dm_outer scale_fl))
	(setq tflpt22 (polar flpt21 0 tn))
	(setq tflpt23 (polar tflpt22 0 R))
	(setq tflpt17 (polar flpt16 0 (+ tn (* R 2))))
	(setq tflpt111 (polar flpt12 0 (/ (- T_D Din)2)))
	(setq tflpt19 (polar tflpt111 pi (/ (- T_D T_D_m) 2)))
	(setq tflpt110 (polar tflpt19 0 (/ d 2)))
	(setq tflpt18 (polar tflpt19 pi (/ d 2)))
	(setq tflpt010 (polar tflpt111 (* 1.5 pi) (- H L)))
	(setq tflpt08 (polar tflpt19 (* 1.5 pi) (- H L)))
	(setq tflpt09 (polar tflpt08 0 (/ d 2)))
	(setq tflpt07 (polar tflpt08 pi (/ d 2)))
	(setq DTout T_D)
	(setq DTpcd T_D_m)
	(setq tflpt41 (polar tflpt19 (/ pi 2) middle_line))
	(setq tflpt-11 (polar tflpt08 (* pi 1.5) middle_line))
        ;绘制公共直线
        (command "line" tflpt17 tflpt111 "");右侧上端面横线
	(command "line" tflpt18 tflpt07 "");上法兰右侧螺栓孔左线
	(command "line" tflpt110 tflpt09 "");上法兰右侧螺栓孔右线
        (command "line" flpt33 tflpt22 "");;T上法兰右侧颈右侧竖线
	(command "line" tflpt111 tflpt010 "");T上法兰右侧最右竖线
	(command "arc" tflpt22 "c" tflpt23 tflpt17);T上法兰右侧圆角的弧
	(if (= flange_index 0);判断是否是底法兰
	  (progn;如果是底法兰
	    (anchor_bolt boltType (/ (- H L) scale_fl));锚栓块绘制
	    (command "line" flpt00 tflpt010 "");下端面直线
	    ;;;绘制样条曲线
	    (command "layer" "M" "2细线层" "")
	    (command "spline" flpt51 flpt50 flpt30 flpt10 flpt00 "" "" "")
	    (command "insert" boltName "S" scale_fl flpt14 "")
	    (command "insert" boltName "S" scale_fl tflpt19 "")
	    ;(Middleline flpt-10 flpt40 (* 20 scale_fl))
	    ;(Middleline tflpt-11 tflpt41 (* 20 scale_fl))
	    ;名称
	    (FlangeZoomTitle (polar flpt40 (* pi 0.5) (* 25 mScale)) (- sum_flange flange_index) (/ mscale scale_fl))
	    ;标注
	    ;直径标注
	    (DimflD_mid (polar tflpt22 pi Dout) tflpt22  (/ 1 scale_fl) 0 reld_hole (polar flpt01 (* pi 1.5) (* 150 scale_fl)) 0 );外径
	    (DimflD_mid (polar flpt04 pi Dpcd) flpt04  (/ 1 scale_fl) (/ num_hole 2) reld_hole (polar flpt01 (* pi 1.5) (* 100 scale_fl)) 0 );内分度圆
	    (DimflD_mid (polar flpt02 pi Din) flpt02  (/ 1 scale_fl) 0 reld_hole (polar flpt01 (* pi 1.5) (* 50 scale_fl)) 0 ) ;内径标注
	    (DimflD_mid (polar tflpt010 pi DTout) tflpt010  (/ 1 scale_fl) 0 reld_hole (polar flpt01 (* pi 1.5) (* 250 scale_fl)) 0 );最外径
	    (DimflD_mid (polar tflpt08 pi DTpcd) tflpt08  (/ 1 scale_fl) (/ num_hole 2) reld_hole (polar flpt01 (* pi 1.5) (* 200 scale_fl)) 0 ) ;外分度圆
	    ;法兰厚度标注
	    (dimFlange_thick2 flpt12 flpt02 (/ 1 scale_fl ) 0 (- (/ (- H L) 2)) )
             (dimFlange_h flpt33 tflpt010 scale_fl 2500);法兰高度标注
	    ;法兰脖子标注
            (dimh_flange flpt21 (polar flpt21 0 tn) (/ 1.0 scale_fl) 0 200 0);法兰脖子厚度标注
	    
	    (scaleDim flpt50 (polar flpt50 0 tn_top) scale_fl 1);法兰高度标注
	    ;法兰质量标注
	    (flmasslead flange_index Flange_type tflpt010 MassOfFlange (- relH relL));法兰重量标注
	    ;标注力矩
	    (AnchorBolt BoltType BoltClass tflpt19 Moment)
	    ;打剖面线
	    (bhatch_pt tflpt110 tflpt010 scale_fl 0)
	    (bhatch_pt flpt12 flpt03 scale_fl 0)
	    (bhatch_pt flpt15 flpt06 scale_fl 0)
	    (bhatch_pt flpt50 flpt33 scale_fl 90)
          );如果是底法兰的if右括号
          (progn;如果不是底法兰
	    (bolt boltType (- relH relL) );螺栓绘制
            ;;;;镜像点,T型连接法兰特有的
	    (setq mi_tflpt19 (miFlange tflpt19 (- H L)));T型连接法兰特有的
            (setq mi_tflpt17 (miFlange tflpt17 (- H L)));T型连接法兰特有的
	    (setq mi_tflpt18 (miFlange tflpt18 (- H L)));T型连接法兰特有的
	    (setq mi_tflpt110 (miFlange tflpt110 (- H L)));T型连接法兰特有的
	    (setq mi_tflpt111 (miFlange tflpt111 (- H L)));T型连接法兰特有的
	    (setq mi_tflpt23 (polar mi_tflpt17 (* 1.5 pi) R));T型连接法兰特有的
            (setq mi_tflpt22 (polar mi_tflpt23 pi R ));T型连接法兰特有的
            (setq mi_tflpt41 (polar mi_tflpt19 (* 1.5 pi) middle_line));
            ;绘制线
	    (command "line" mi_flpt10  mi_flpt16 "");下法兰左侧上面横线
            (command "line" mi_flpt31 mi_flpt21 "");下法兰颈左侧竖线
            (command "arc" mi_flpt21 "c" mi_flpt20 mi_flpt16);下法兰左侧圆角弧线
	    (command "line" mi_flpt12 flpt02 "");下法兰左侧最左竖线
	    (command "line" mi_flpt13 flpt03 "");下法兰左侧螺栓孔左侧竖线
	    (command "line" mi_flpt15 flpt05 "");下法兰左侧螺栓孔右侧竖线
	    (command "line" mi_flpt30 mi_flpt34 "");下法兰下筒节横线
            (command "line" mi_flpt34 mi_flpt51 "");下法兰右侧下筒节右侧竖线
            
	    (command "line" mi_flpt50 (polar mi_flpt50 (/ pi 2) L) "");下法兰左侧下筒节左侧竖线
	    (command "line" mi_flpt33 mi_tflpt22 "");;下法兰颈右侧竖线
            (command "line" mi_tflpt17 mi_tflpt111 "");下法兰右侧上面横线
            (command "line" mi_tflpt18 tflpt07 "");下法兰右螺栓孔左线	
	    (command "line" mi_tflpt110 tflpt09 "");下法兰右螺栓孔右线
	    (command "arc" mi_tflpt17 "c" mi_tflpt23 mi_tflpt22);下法兰右圆角弧线
	    (command "line" mi_tflpt111 tflpt010 "");下法兰最右侧竖线	
	    (command "line" flpt00 flpt03 "");上法兰下底面左线
	    (command "line" flpt05 tflpt07 "");上法兰下底面中线
            (command "line" tflpt09 tflpt010 "");上法兰下底面右线
	    ;;;绘制样条曲线
	    (command "layer" "M" "2细线层" "")
	    (command "spline" flpt51 flpt50 flpt30 flpt10 flpt00 mi_flpt10 mi_flpt30 mi_flpt50 mi_flpt51 "" "" "");剖切线
	    (command "insert" boltName "S" scale_fl mi_flpt14 "");插入左螺栓
	    (command "insert" boltName "S" scale_fl mi_tflpt19 "");插入右螺栓
	    ;(Middleline flpt40 mi_flpt40 (* 20 scale_fl))
	    ;(Middleline tflpt41 mi_tflpt41 (* 20 scale_fl))
            (FlangeZoomTitle (polar flpt40 (* pi 0.5) (* 25 mScale)) (- sum_flange flange_index) (/ mscale scale_fl))
		    
	    ;标注
	    ;直径标注
        (DimflD_mid (polar mi_tflpt111 pi DTout) mi_tflpt111  (/ 1 scale_fl) 0 reld_hole (polar flpt01 (* pi 1.5) (* 450 scale_fl)) 0);最外径
	    (DimflD_mid (polar mi_tflpt19 pi DTpcd) mi_tflpt19  (/ 1 scale_fl) (/ num_hole 2) reld_hole (polar flpt01 (* pi 1.5) (* 400 scale_fl)) 0);外分度圆
	    (DimflD_mid (polar mi_flpt33 pi Dout) mi_flpt33  (/ 1 scale_fl) 0 reld_hole (polar flpt01 (* pi 1.5) (* 350 scale_fl)) 0);外径
	    (DimflD_mid (polar mi_flpt14 pi Dpcd) mi_flpt14  (/ 1 scale_fl) (/ num_hole 2) reld_hole (polar flpt01 (* pi 1.5) (* 300 scale_fl)) 0);内分度圆
	    (DimflD_mid (polar mi_flpt12 pi Din) mi_flpt12  (/ 1 scale_fl) 0 reld_hole (polar flpt01 (* pi 1.5) (* 250 scale_fl)) 0);内径标注
	    
	    ;法兰厚度标注

            (dimFlange_thick2 flpt12 flpt02 (/ 1 scale_fl ) 0 (- (/ (- H L) 2)) );上法兰法兰厚度标注
	    (dimFlange_h flpt33 tflpt010 scale_fl 2500);上法兰高度标注
	    (dimFlange_thick2 mi_flpt12 flpt02 (/ 1 scale_fl ) 0 (- (/ (- H L) 2)) );下法兰法兰厚度标注
	    (dimFlange_h mi_flpt33 tflpt010 scale_fl 2500);下法兰高度标注

	    
	    ;法兰脖子标注
	    (scaleDim flpt21 (polar flpt21 0 tn) scale_fl 0);法兰脖子厚度标注
	    (scaleDim flpt50 (polar flpt50 0 tn_top) scale_fl 1);上法兰连接处筒节厚度标注
            (scaleDim mi_flpt21 (polar mi_flpt21 0 tn) scale_fl 0);下法兰脖子厚度标注
	    (scaleDim mi_flpt50 (polar mi_flpt50 0 tn_down) scale_fl 2);下法兰连接筒节厚度标注
	    
            ;法兰重量标注
	    (flmasslead flange_index Flange_type tflpt010 MassOfFlange (- relH relL));法兰重量标注
            ;打剖面线
            (bhatch_pt tflpt110 tflpt010 scale_fl 0);上法兰最右
	    (bhatch_pt flpt12 flpt03 scale_fl 0);上法兰最左
	    (bhatch_pt flpt15 flpt06 scale_fl 0);上法兰中间
	    (bhatch_pt flpt50 flpt33 scale_fl 90);上法兰连接筒节
            ;下法兰剖面线
            (bhatch_pt tflpt010 mi_tflpt110  scale_fl 90);下法兰最右
	    (bhatch_pt flpt03 mi_flpt12  scale_fl 90);下法兰最左
	    (bhatch_pt flpt05 mi_tflpt18 scale_fl 90);中间法兰
            (bhatch_pt mi_flpt50 mi_flpt33 scale_fl 0);下法兰连接筒节       
	  );如果不是底法兰执行的右括号
	);if的右括号
      );T 型法兰绘制结束
    );判断法兰类型右括号
  (setq bolt_zb_list (append bolt_zb_list (list flpt40) ))
);法兰绘制函数终结括号


;;;;;;;;;;;;;;;;;;应用与在外部程序中，生成焊合图;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defun c:welded_insert(/ i pa_y fst_oript_y fst_oript) 
  (vl-load-com)
  (setvar "cmdecho" 0)
  (setvar "osmode" 0);不扑捉任何类型
  (command "zoom" "a")
  (command "regen")
  ;比例确定
  (setq mscale (atoi (getstring "\n输入比例：")))
  (setvar "ltscale" (/ mscale 2))
  (setq TowerExcel_file (getstring "\n输入塔架几何数据表："))
  (setq Towerdat_file (substr TowerExcel_file 2 (- (strlen TowerExcel_file) 2)))
  ;得到 retTower、retFlange、retDoor,retEmbedded
  (GetTowerGeoData Towerdat_file)
  
  (setq pa_y (atoi (getstring "\n输入纵坐标：")))
  (setq i (atoi (getstring "\n输入第n段：")))
  
  (setq pa_x (* 150 mscale))
  (if (= i 0)
    (progn;如果是第一段
      (setq pa_y (+ pa_y 4000))
      (setq fst_oript_y (- pa_y 5000))
      (setq fst_oript (list pa_x fst_oript_y 0));主体下的俯视图的原点
    )
  )
  ;(setq pa (list (* 150 mscale) (atoi (getstring "\n输入纵坐标：")) 0.0))
  ;原点确定
  (setq pa (list pa_x pa_y 0.0))
  ;初始化，必须先做的
  (initializedata)
  (setvar "dimzin" 8);无小数
  (weldeddraw i)

  (setq FlangeMassList (AllFlangeMass));重量初始化
  
  (Welded_Fl_fd_insert i);焊合法兰放大视图绘制
  
  
  (setq SectionMassList (secmass));塔架主体重量初始化
  (print "焊合图绘制成功!")
  (if (= i 0)
    (progn
      (section1_other_insert fst_oript)
      ;(setq pt_zoom '(48360 34440 0));第一段图框的右上点
    )
    ;(setq pt_zoom '(22360 32840 0));其他段图框的右上点
  )
  ;视图刷新调整
  ;(command "zoom" "w" '(0 0 0) pt_zoom)
  ;(command "zoom" "A" '(0 0 0) pt_zoom)
  (if (= i (- section_qty 1))
    (progn
      (command "insert" "weld_t_techreq" "S" 1 '(3000 600 0) "");如果是顶段    
    )
    (progn
      (if (= i 0)
        (command "insert" "walltechrequ" "S" 1 '(14000 1500 0 ) "");插入塔架中英文技术要求,第一弹
	(command "insert" "weld_techreq" "S" 1 '(3000 600 0) "");中间段
      );End if
    );End progn
  );End if
);End welded_insert

;;;;;焊合第一段的其他附件的绘制
(defun section1_other_insert(pt0 / ii H num_hole_tfl num_hole_fl)
 
  ;数据初始化
  (setq ii 0) 
  (setq D (value retFlange ii 1));法兰外径
  (setq Din_fl (value retFlange ii 2));法兰内径
  (setq Dpcd_fl (value retFlange ii 3));螺栓分度圆直径
  (setq H (value retDoor 0 12));加强门框的高度

  (setq Dout_tfl (value retFlange ii 13));T型法兰最外径
  (setq Dpcd_tfl (value retFlange ii 14));T型法兰外分度圆直径
 
  (setq t1 (value retDoor 0 14));塔筒壁厚度t1
  (setq hh1 (value retDoor 0 15)); 补强板高度
  (setq t2 (value retDoor 0 16));补强板厚度t2
  (setq h2 (value retDoor 0 17));直边长度H_V
  (setq h1 (value retDoor 0 18));门洞高度H2
  (setq b1 (value retDoor 0 19));门洞宽度w

  (setq R 200.0)
     
  (setq tfl_fl (value retFlange ii 4));法兰厚度
  (setq tn_fl (value retFlange ii 5));法兰颈厚
  (setq L_fl (value retFlange ii 6));法兰颈高
  (setq L_fl (neck_h_fl_judge L_fl tn_fl));法兰颈高，过滤一下数据

  (setq d_tfl (value retFlange ii 8));螺栓孔直径
  (setq num_hole_tfl (/ (value retFlange ii 9) 2));螺栓数
  (setq d_fl (value retFlange (+ ii 1) 8));上螺栓孔直径
  (setq num_hole_fl (value retFlange ii 9) );螺栓数
    
  (setq H_flange (+ L_fl tfl_fl));法兰高度

  (setq cylinder_H1 (* (- (value retTower 1 2) (value retTower 1 0)) 1000))
  (setq cylinder_H2 (* (- (value retTower 2 2) (value retTower 2 0)) 1000))
  
  (sectiononeweld pt0 Dout_tfl Din_fl Dpcd_fl Dpcd_tfl d_tfl num_hole_tfl d_fl num_hole_fl t1 t2 H D hh1 h2 h1 b1 R H_flange cylinder_H1 cylinder_H2)
)
;;;;;;End section1_other_insert


;;;;;;;;;;;;;;;;;;;;法兰图纸生成;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defun c:single_fl_insert(/ pa) 
  (vl-load-com)
  (setvar "cmdecho" 0)
  (setvar "osmode" 0);不扑捉任何类型
  (command "zoom" "a")
  (command "regen")
  ;比例确定

  (setq mscale (atoi (getstring "\n输入比例：")))
  (setvar "ltscale" (/ mscale 2))
  (setq TowerExcel_file (getstring "\n输入塔架几何数据表："))
  (setq Towerdat_file (substr TowerExcel_file 2 (- (strlen TowerExcel_file) 2)))
  ;得到 retTower、retFlange、retDoor,retEmbedded
  (GetTowerGeoData Towerdat_file)
  ;原点确定
  (setq pa '(0 0 0 ))
  
  ;初始化，必须先做的
  (initializedata)
  ;数据预判断
  ;(setq logsystem T);log日志系统开
  ;(prejudge)
  (setq i (atoi (getstring "\n输入第n段：")))
  (setvar "dimzin" 8);无小数
  (setq FlangeMassList (AllFlangeMass));重量初始化
  
  (weldsingledraw i);焊合法兰放大视图绘制
)
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;*********************************法兰图纸信息获取**************************************************;;;;;;;;;;;;;;;;;;
(defun weldsingledraw(nsec / bOruFL Sum_flange ii Dout_fl Din_fl Dpcd_fl tfl_fl tn_fl L_fl boltType d_fl num_hole R_fl tempflange_type
		     flange_type BoltClass H_fl Moment tn_top tn_down pt_fl MassOfFlange scale_fl bottomflname)
    (setq Sum_flange (+ nsec 1));吴航
  
    (setq ii nsec);循环初值,法兰数量，不包括顶法兰
      ;数据初始化
      (setq Dout_fl (value retFlange ii 1));法兰外径
      (setq Din_fl (value retFlange ii 2));法兰内径
      (setq Dpcd_fl (value retFlange ii 3));螺栓分度圆直径
      (setq tfl_fl (value retFlange ii 4));法兰厚度
      (setq tn_fl (value retFlange ii 5));法兰颈厚
      (setq L_fl (value retFlange ii 6));法兰颈高
      (setq L_fl (neck_h_fl_judge L_fl tn_fl));法兰颈高，过滤一下数据
      ;(print L_fl)
      (setq boltType (value retFlange ii 7));螺栓公称直径
      (setq d_fl (value retFlange ii 8));螺栓孔直径
      (setq num_hole (value retFlange ii 9));螺栓数
      (setq R_fl (value retFlange ii 10));圆角
      (if (or (= R_fl nil) (= R_fl " "));如果圆角单元格没有内容则为10
        (setq R_fl 10)
      )
      (setq tempflange_type (value retFlange ii 11));法兰类型

      (setq flange_type (FlangeTypeJudge tempflange_type ii));判断法兰的类型
      
      (setq BoltClass (value retFlange ii 12));螺栓等级
      (setq H_fl (+ L_fl tfl_fl));法兰高度
      (setq Moment (value retFlange ii 15));螺栓预紧力
      ;;;;确定法兰上下的筒节壁厚
      (setq tn_top (value retTower (+ (nth ii pos ) 1) 4))
      
      (if (= ii 0)
        (setq tn_down 0)
	(setq tn_down (value retTower (- (nth ii pos ) 2) 4))
      )
      
      (setq MassOfFlange (nth ii FlangeMassList) )
      (if (= flange_type "T")
	(progn
	  (setq Dout_tfl (value retFlange ii 13))
	  (setq Dpcd_out_tfl (value retFlange ii 14))
	  

	  (tflange Dout_tfl Dpcd_fl     Dpcd_out_tfl Din_fl  Dout_fl tn_fl  L_fl  H_fl  d_fl  num_hole R_fl)
	  ;(tflange Dout_tfl Dpcd_in_tfl Dpcd_out_tfl Din_tfl Da_tfl  Tn_tFL L_tfl H_tfl d_tfl num_hole_tfl R_tfl)
	  ;(tflange 4686.0    4090.0       4437.0      3840.0 4300.0   37.0   45.0 160.0  54.0 184.0        10.0 )
	)
	(cflange Dout_fl Dpcd_fl Din_fl tn_fl L_fl d_fl num_hole R_fl H_fl)
      )
      ;(cflange Dout_fl Dpcd_fl Din_fl tn_fl L_fl d_fl num_hole R_fl H_fl)
  (print "法兰图绘制成功！")
);********************************法兰所有放大视图生成结束***********************************************

;;;;焊合图纸塔架主体绘制函数
(defun weldeddraw(xsec / flstart j k h pt_x shellradius shellptl shellptr
		m n shellptlx shellptrx shellptlxu shellptrxu shellptlx_x
		shellptlxu_x thick hh pt_x shellradius shellptl);底段绘制
  ;下法兰绘制
  (bfldraw_welded (nth (nth xsec pos) sthptlist ) xsec);底法兰绘制
  ;绘制主体的横线，包括上下法兰的最上和最下的横线
  
  (setq m (+ (nth xsec pos) 2));初始值
  (setq n (nth (+ xsec 1) pos));此段的结束位置
  (MiddleLine (nth (nth xsec pos) sthptlist) (nth n sthptlist) 100);底法兰中心下点，顶法兰上中心点
  (if (= xsec 0)
    (xhlead 3 (polar (polar (nth (+ (nth xsec pos) 3) sthptlist) 0 800) (/ pi 2) 100) 4000 (/ pi 6));第一段筒体序号
    (xhlead 2 (polar (polar (nth (+ (nth xsec pos) 3) sthptlist) 0 800) (/ pi 2) 100) 4000 (/ pi 6));筒体序号
  )
  ;(xhlead 3 (polar (nth (+ (nth xsec pos) 3) sthptlist) 0 800) 4000 (/ pi 6));筒体序号
  (command "layer" "M" "1轮廓实线层" "");线型
  (while (< m (- n 1));画筒节的横线
    (setq shellptlx (nth m sthptllist))
    (setq shellptrx (nth m sthptrlist))
    (addline shellptlx shellptrx)
    (setq m (+ m 1))
  )
  ;绘制主体的左右侧竖线
  (setq m (+ (nth xsec pos) 1));从1开始，不画底法兰的直线参数
  (while (< m (- n 1))
    (setq shellptlx (nth m sthptllist));左下点
    (setq shellptrx (nth m sthptrlist));右下点
    (setq shellptlxu (nth (+ m 1) sthptllist));左上点
    (setq shellptrxu (nth (+ m 1) sthptrlist));右上点
    (setq shellptlx_x (car shellptlx));shellptlx的横坐标
    (setq shellptlxu_x (car shellptlxu));shellptlxu的横坐标
    (addline shellptlx shellptlxu);绘制左直线
    (addline shellptrx shellptrxu);绘制右直线
    ;筒节高度标注
    (ldimv shellptlx shellptlxu -1300);,坐标1，坐标2，标注距离，往左标注
    ;筒节直径标注
    (if (/= (rtos shellptlx_x) (rtos shellptlxu_x));若筒节上下的横坐标不同，是锥段，标注尺寸
      (progn 
        ;(if (/= m (- n 2))
        (dimd shellptlxu shellptrxu 1.0 -1000 -800 60);不标注最上筒节的尺寸
        ;)
        (if (= m (+ (nth xsec pos) 1));锥段最下筒节的直径
	  (dimd shellptlx shellptrx 1.0 300 -800 0)
        )
      )
    )
    ;壁厚标注
    (setq thick (nth m shellthicklist));得到壁厚
    (Thick_drw thick shellptrx shellptrxu 1500.0 (/ pi 6));thick:壁厚,下坐标，上坐标
    (command "layer" "M" "1轮廓实线层" "");为了改正标注壁厚时的线型
    (setq m (+ m 1))
  )
  ;此段的顶法兰绘制
  
  (ufldraw_welded (nth (- (nth (+ xsec 1) pos) 1) sthptlist ) (+ xsec 1))
  
  ;塔段总高度标注
  (if (= xsec (- section_qty 1))
    ;顶段的尺寸标注
    (ldimv (nth (nth xsec pos) sthptllist) (bnth -1 sthptllist) -2700)
    ;其他段尺寸标注
    (ldimv (nth (nth xsec pos) sthptllist) (nth (nth (+ xsec 1) pos) sthptllist) -2700)
  )
  (command "insert" "weldbjizhun1" "S" 1 (polar (nth (nth xsec pos) sthptllist) pi 1300) "")
  (if (= xsec 0)
    (command "insert" "weldbxwgc_1" "S" 1 (polar (nth (nth xsec pos) sthptllist) pi 1600) "");如果是第一段
    (command "insert" "weldbxwgc" "S" 1 (polar (nth (nth xsec pos) sthptllist) pi 1600) "")
  )
  ;(command "insert" "weldbxwgc" "S" 1 (polar (nth (nth xsec pos) sthptllist) pi 1600) "")
  (if (= xsec 0)
    (command "insert" "zhuti_door" "S" 1 (nth (nth xsec pos) sthptlist ) "");如果是第一段插入门洞
  )
  
)
;;;;;;;End weldeddraw;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;*********************************焊合图中的法兰放大视图生成**************************************************;;;;;;;;;;;;;;;;;;
(defun Welded_Fl_fd_insert(nsec / pt_fl  bOruFL Sum_flange ii Dout_fl Din_fl Dpcd_fl tfl_fl tn_fl L_fl d_fl num_hole R_fl tempflange_type
		     flange_type H_fl Moment tn_top tn_down scale_fl bottomflname)
    (setq Sum_flange (+ nsec 1));吴航
    (setq ii nsec);循环初值,法兰数量，不包括顶法兰
    (while (and (<= ii Sum_flange) (> (value retFlange ii 2) 0))
      ;数据初始化
      (setq Dout_fl (value retFlange ii 1));法兰外径
      (setq Din_fl (value retFlange ii 2));法兰内径
      (setq Dpcd_fl (value retFlange ii 3));螺栓分度圆直径
      (setq tfl_fl (value retFlange ii 4));法兰厚度
      (setq tn_fl (value retFlange ii 5));法兰颈厚
      (setq L_fl (value retFlange ii 6));法兰颈高
      (setq L_fl (neck_h_fl_judge L_fl tn_fl));法兰颈高，过滤一下数据
      ;(setq boltType (value retFlange ii 7));螺栓公称直径
      (setq d_fl (value retFlange ii 8));螺栓孔直径
      (setq num_hole (value retFlange ii 9));螺栓数
      (setq R_fl (value retFlange ii 10));圆角
      (if (or (= R_fl nil) (= R_fl " "));如果圆角单元格没有内容则为10
        (setq R_fl 10)
      )
      (setq tempflange_type (value retFlange ii 11));法兰类型
      (setq flange_type (FlangeTypeJudge tempflange_type ii));判断法兰的类型
      ;(setq BoltClass (value retFlange ii 12));螺栓等级
      (setq H_fl (+ L_fl tfl_fl));法兰高度
      ;(setq Moment (value retFlange ii 15));螺栓预紧力
      ;;;;确定法兰上下的筒节壁厚
      (setq tn_top (value retTower (+ (nth ii pos ) 1) 4))
      
      (if (= ii 0)
        (setq tn_down 0)
	(setq tn_down (value retTower (- (nth ii pos ) 2) 4))
      )
      ;法兰放大视图绘制      
      ;(setq MassOfFlange (nth ii FlangeMassList) )
      
      (if (= ii section_qty)
        (progn;如果是顶法兰，顶法兰执行该程序,判断顶法兰类型
	  (setq scale_fl 10);法兰放大系数
	  (setq pt_fl '(14000 29600))
	  (weldtopflinsert pt_fl topfltype scale_fl H_fl)
        )
	(progn
	  (if (= ii nsec)
	    (progn;如果是下法兰
	      ;(setq pt_fl '(19000 29000))
	      (if (= nsec 0)
		(progn
		  (setq pt_fl '(35000 29000));如果是第一段
		  (setq scale_fl 6.0)
		)
		(progn
		  (setq pt_fl '(19000 29000))
		  (setq scale_fl 4.0)
		)
              )
	     
              ;(Weldbfl_draw scale_fl flange_type pt_fl H_fl Dout_fl Din_fl Dpcd_fl tn_fl d_fl L_fl R_fl num_hole (+ ii 1) sum_flange tn_top tn_down Neck_TopFlange)
	      (Weldbfl_draw scale_fl flange_type pt_fl H_fl Dout_fl Din_fl Dpcd_fl tn_fl d_fl L_fl R_fl num_hole ii sum_flange tn_top tn_down Neck_TopFlange)
            )
	    (progn;如果是上法兰
	      (if (= nsec 0)
		(progn
		  (setq pt_fl '(30000 29600));如果是第一段
		  (setq scale_fl 6.0)
		)
		(progn
		  (setq pt_fl '(14000 29600))
		  (setq scale_fl 4.0)
		)
              )
	      ;(setq pt_fl '(14000 29600))
	      ;(Weldufl_draw scale_fl flange_type pt_fl H_fl Dout_fl Din_fl Dpcd_fl tn_fl d_fl L_fl R_fl num_hole (- ii 1) sum_flange tn_top tn_down Neck_TopFlange)
	      
	      (Weldufl_draw scale_fl flange_type pt_fl H_fl Dout_fl Din_fl Dpcd_fl tn_fl d_fl L_fl R_fl num_hole ii sum_flange tn_top tn_down Neck_TopFlange)
            )
          );End if
	)
      );End if
      (setq ii (+ 1 ii))
  );while的右括号
  (print "法兰放大视图绘制成功！")
);********End Welded_Fl_fd_insert***********************************************

;;;;;筒段底法兰绘制;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defun bfldraw_welded(pax i / flptori flptnb flptoutbl flptoutbr flptoutul flptoutur flptdabl flptdabr flptdaul flptdaur neckh_fl);bottom法兰绘制
  (setq flptori pax);底法兰的下最下中心点
  (setq flptnb (polar flptori (/ pi 2) (value retFlange i 4)));底法兰法兰厚的中心下点
  (setq neckh_fl (neck_h_fl_judge (value retFlange i 6) (value retFlange i 5)));法兰的颈高,过滤一下
  (if (TorLflange i);如果是T型法兰
    (progn;如果是T型法兰
      (setq flptoutbl (polar flptori pi (/ (value retFlange i 13) 2)));底法兰左下点
      (setq flptoutbr (polar flptori 0 (/ (value retFlange i 13) 2)));底法兰右下点
      (setq flptoutul (polar flptoutbl (/ pi 2)  (value retFlange i 4)));底法兰法兰厚左上点
      (setq flptoutur (polar flptoutbr (/ pi 2)  (value retFlange i 4)));底法兰法兰厚右上点
      (setq flptdabl (polar flptnb pi  (/ (value retFlange i 1) 2)));底法兰颈左下点
      (setq flptdabr (polar flptnb 0  (/ (value retFlange i 1) 2)));底法兰颈右下点
      (setq flptdaul (polar flptdabl (/ pi 2)  neckh_fl));底法兰颈左上点
      (setq flptdaur (polar flptdabr (/ pi 2)  neckh_fl));底法兰颈右上点
      ;;;绘制法兰轮廓
      (addline flptoutbl flptoutbr )
      (addline flptoutul flptoutur )
      (addline flptdaul flptdaur )
      (addline flptoutbl flptoutul )
      (addline flptoutbr flptoutur )
      (addline flptdabl flptdaul )
      (addline flptdabr flptdaur )
    )
    (progn;如果是L型法兰
      (setq flptoutbl (polar flptori pi (/ (value retFlange i 1) 2)));底法兰左下点
      (setq flptoutbr (polar flptori 0 (/ (value retFlange i 1) 2)));底法兰右下点
      (setq flptdaul (polar flptoutbl (/ pi 2)  (+ neckh_fl (value retFlange i 4))));底法兰颈左上点
      (setq flptdaur (polar flptoutbr (/ pi 2)  (+ neckh_fl (value retFlange i 4))));底法兰颈右上点
      (addline flptoutbl flptoutbr )
      (addline flptdaul flptdaur )
      (addline flptoutbl flptdaul )
      (addline flptoutbr flptdaur )
    )
  )
  (if (= i 0)
    (progn;如果是底法兰
      (ldimv flptoutbl flptdaul -2050);法兰高度标注
      (dimd flptoutbl flptoutbr 1.0 -1600 0 0);法兰下端面直径标注
      (dimd flptdaul flptdaur 1.0 250 -1150 0);法兰上端面直径标注
      (command "insert" "weldajizhun" "S" 1 (polar flptoutbr (* pi 1.5) 1600) "");A基准符号标注
    )
    (progn
      (dimd flptoutbl flptoutbr 1.0 -1600 0 0);法兰下端面直径标注
      (command "insert" "weldajizhun" "S" 1 (polar flptoutbr (* pi 1.5) 1600) "");A基准符号标注
      
      (ldimv flptoutbl flptdaul -2000);法兰高度标注
    )
  )
  ;(FlangeZoom flptoutbr 0 (* mscale 3) T nil);放大视图圆圈
  (command "insert" "weld_fd_xh_1" "S" 1 flptoutbr "")

  (xhlead 1 (polar flptnb 0 800) 4000 (/ pi 6));下法兰序号
);;;;;;;End bfldraw_welded函数结束

;;;;;;;;;;上连接法兰;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defun ufldraw_welded (pax i / flptori flptub flptdabl flptdabr flptdaul flptdaur flptoutbl flptoutbr flptoutul flptoutur neckh_fl);upper flange draw 连接法兰绘制
	(setq flptori pax);底法兰的下最下中心点
  ;(setq flptnb (polar flptori (/ pi 2) (value retFlange i 4)));底法兰法兰厚的中心下点
  ;;;法兰颈高判断
  
	(if (= i (- flange_qty 1));如果是最顶法兰
		(progn
			(if (or (= (value retFlange i 6) nil) (= (value retFlange i 6) " ") (= (value retFlange i 6) 0));如果颈高为空
				(setq neckh_fl (* (- (value retTower (- (nth i pos) 1) 2) (value retTower (- (nth i pos) 1) 0)) 1000));主体表中得到颈高
				(setq neckh_fl (value retFlange i 6))
			)
		)
    ;如果不是顶法兰
		(setq neckh_fl (neck_h_fl_judge (value retFlange i 6) (value retFlange i 5)));法兰的颈高,过滤一下
	)
  
	(if (TorLflange i);如果此法兰是T型法兰
		(progn;如果是
			(setq flptdabl (polar flptori pi (/ (value retFlange i 1) 2)));法兰左下点
			(setq flptdabr (polar flptori 0 (/ (value retFlange i 1) 2)));法兰右下点
            (setq flptdaul (polar flptdabl (/ pi 2)  neckh_fl));
			(setq flptdaur (polar flptdabr (/ pi 2)  neckh_fl));
			(setq flptub (polar flptori (/ pi 2) neckh_fl));底法兰颈厚的中心下点
			(setq flptoutbl (polar flptub pi  (/ (value retFlange i 13) 2)));
			(setq flptoutbr (polar flptub 0  (/ (value retFlange i 13) 2)));    
			(setq flptoutul (polar flptoutbl (/ pi 2)  (value retFlange i 4)));
			(setq flptoutur (polar flptoutbr (/ pi 2)  (value retFlange i 4)));
			(addline flptoutbl flptoutbr );法兰厚的下横线
			(addline  flptoutul flptoutur );法兰厚上横线
			;(addline  flptdaul flptdaur );最上横线
			(addline  flptoutbl flptoutul );法兰厚左线
			(addline  flptoutbr flptoutur );法兰厚右线
			(addline  flptdabl flptdaul );法兰壁的左线
			(addline  flptdabr flptdaur );法兰壁的右线
			(addline  flptdabl flptdabr );法兰壁下线
			;竖直尺寸标注
			(cfldimv flptdabl flptoutul -2050)
			(dimd flptoutul flptoutur 1.0 -1000 -800 60);直径标注,标注线倾斜
		)
		(progn;如果不是T型法兰
			(setq flptoutbl (polar flptori pi (/ (value retFlange i 1) 2)));底法兰左下点
			(setq flptoutbr (polar flptori 0 (/ (value retFlange i 1) 2)));底法兰右下点
			(setq flptdaul (polar flptoutbl (/ pi 2)  (+ (value retFlange i 4) neckh_fl)));底法兰颈左上点
			(setq flptdaur (polar flptoutbr (/ pi 2)  (+ (value retFlange i 4) neckh_fl)));底法兰颈右上点      
			(if (= i section_qty)
				(progn;如果是顶法兰,标注的尺寸在最上
					(if (or (= topfltype "3MW_S_TopFlange") (= topfltype "3MW_S_New_TopFlange"))
						(progn;如果是3S法兰
							(setq flpt3sl (polar flptoutbl (/ pi 2)  (- (+ (value retFlange i 4) neckh_fl) 80)));3s法兰的中间点
							(setq flpt3sr (polar flptoutbr (/ pi 2)  (- (+ (value retFlange i 4) neckh_fl) 80)));底法兰的中间点
							(addline flpt3sl flpt3sr );3s法兰中间横线
							(setq flptdaul (polar flptdaul 0  20));3s法兰颈左上点
							(setq flptdaur (polar flptdaur pi  20));3s法兰颈右上点
							(addline flpt3sl flptdaul );3s法兰左斜线
							(addline flpt3sr flptdaur );3s法兰右斜线
							(addline flpt3sl flptoutbl );3s法兰左直线
							(addline flpt3sr flptoutbr );3s法兰右直线
							(dimd flpt3sl flpt3sr 1.0 500 0 0);上直径标注
	      				)
						(progn;如果不是3s顶法兰
							(addline flptoutbl flptdaul );左线
							(addline flptoutbr flptdaur );右线
							(dimd flptdaul flptdaur 1.0 600 0 0);上直径标注
							(command "insert" "weldcjizhun" "S" 1 (polar flptdaur (/ pi 2) 600) "");A基准符号标注
	      
						)
					)
						;(dimd flptdaul flptdaur 1.0 500 0 0);上直径标注
					(dimd flptoutbl flptoutbr 1.0 -1000 -800 60);直径标注
					(command "insert" "weldtxwgc" "S" 1 (polar flptdaul pi 1300) "");形位公差
				);End progn
				(progn;如果不是顶段顶法兰
	  
					(addline flptoutbl flptdaul );左线
					(addline flptoutbr flptdaur );右线
					;(dimd flptdaul flptdaur 1.0 -1000 -800 60);直径标注
					(dimd flptdaul flptdaur 1.0 1100 0 0);直径标注
					(command "insert" "weldcjizhun" "S" 1 (polar flptdaur (/ pi 2) 1100) "");A基准符号标注
					(command "insert" "welduxwgc" "S" 1 (polar flptdaul pi 1300) "");形位公差
					(command "insert" "welduxwgc" "S" 1 (polar flptdaul pi 1300) "");形位公差
				)
			)
			(addline flptoutbl flptoutbr );最下横线
			(addline flptdaul flptdaur );最上横线
			;(addline flptoutbl flptdaul );左线
			;(addline flptoutbr flptdaur );右线
			(cfldimv flptoutbl flptdaul -2050);竖直尺寸标注
				;(command "insert" "welduxwgc" "S" 1 (polar flptdaul pi 1300) "");形位公差
      
		)
	)
	;(FlangeZoom flptoutbr 1 (* mscale 2.5) T nil)
	(command "insert" "weld_fd_xh_2" "S" 1 flptoutbr "")
	(print i)
	(print (type i))
	(if (= i 1)
		(xhlead 4 (polar (polar flptori 0 800) (/ pi 2) 70) 4000 (/ pi 6));上法兰序号
		(xhlead 3 (polar (polar flptori 0 800) (/ pi 2) 70) 4000 (/ pi 6));上法兰序号
	)
	;(xhlead 4 (polar flptori 0 800) 4000 (/ pi 6));上法兰序号
)
;;;;;;;;END ufldraw_welded;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;;;;;;;放大下法兰绘制;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defun Weldbfl_draw(scale_fl flange_type pt_flange relH relDout relDin relDpcd reltn reld_hole relL relR num_hole flange_index
	       sum_flange reltn_top reltn_down Neck_TopFlange
		   / H Dout Din Dpcd tn d L R middle_line Width Width_pcd_in 
		   flpt00 flpt01 flpt02 flpt04 flpt06 flpt33 flpt31 flpt21 flpt20 flpt16 flpt12 flpt03 flpt05 flpt14 flpt13
		   flpt15 flpt11 flpt10  flpt30 flpt32 flpt51 flpt50 flpt40 flpt-10
		   mi_flpt33 mi_flpt32 mi_flpt30 mi_flpt31 mi_flpt11 mi_flpt51 mi_flpt50 mi_flpt10 mi_flpt12 mi_flpt13
		   mi_flpt14 mi_flpt15 mi_flpt16 mi_flpt21 mi_flpt20 mi_flpt40
		   bottomflname );L型法兰绘制
    ;flange_type 法兰类型
    ;pt_flange法兰绘制初始点
    ;flange_index 法兰序号
    ;sum_flange 法兰总数
    ;reld_hole 螺栓孔直径
    ;(setq scale_fl 4.0);法兰放大系数
    (setq H (* relH scale_fl));H 法兰高度
    (setq Dout (* relDout scale_fl));Dout 法兰外径
    (setq Din (* relDin scale_fl));Din 法兰内径
    (setq Dpcd (* relDpcd scale_fl));Dpcd 分度圆直径
    (setq tn (* reltn scale_fl));tn 颈厚
    (setq d (* reld_hole scale_fl));d 螺栓孔直径
    (setq L (* relL scale_fl));L颈高
    (setq R (* relR scale_fl));R 圆角
    ;法兰相邻筒节的壁厚确定
    (if (and (/= reltn_top nil) (/= reltn_top 0) (/= reltn_top ""));reltn_top：壁厚
        (setq tn_top (* reltn_top scale_fl))
        (setq tn_top tn)
    )
    (if (and (/= reltn_down nil) (/= reltn_down 0) (/= reltn_down ""))
        (setq tn_down (* reltn_down scale_fl))
        (setq tn_down tn)
    )
    (setq middle_line (* 30 scale_fl))
    ;(setq middle_line (* 0.5 scale_fl))
    (setq Width (/ (- Dout Din) 2))	;;;     法兰宽度
    (setq Width_pcd_in (/ (- Dpcd Din) 2)) ;螺栓孔中心到法兰内径的距离
    ;关键点;;;;
    (setq flpt01 pt_flange)
    (setq flpt02 (polar flpt01 0 (/ Width 2)))
    (setq flpt04 (polar flpt02 0 Width_pcd_in)) 
    (setq flpt06 (polar flpt02 0 Width)) 
    (setq flpt33 (polar flpt06 (/ pi 2) H))  
    (setq flpt31 (polar flpt33 pi tn))
    (setq flpt21 (polar flpt31 (* pi 1.5) (- L R)))
    (setq flpt20 (polar flpt21 pi R))
    (setq flpt16 (polar flpt20 (* pi 1.5) R)) 
    (setq flpt12 (polar flpt02 (/ pi 2) (- H L)))
    (setq flpt03 (polar flpt04 pi (/ d 2)))
    (setq flpt05 (polar flpt04 0 (/ d 2)))
    (setq flpt14 (polar flpt04 (/ pi 2) (- H L)))
    (setq flpt13 (polar flpt14 pi (/ d 2)))
    (setq flpt15 (polar flpt14 0 (/ d 2)))
    (setq flpt11 (polar flpt01 (/ pi 2) (- H L)))
    (setq flpt10 (polar flpt11 pi (/ H 3)))
    (setq flpt00 (polar flpt01 pi (/ H 2)))
    (setq flpt30 (polar flpt11 (/ pi 2) L))
    (setq flpt32 (polar flpt33 pi tn_top))
    (setq flpt51 (polar flpt33 (* pi 0.5) L))
    (setq flpt50 (polar flpt51 pi tn_top))
    (setq flpt40 (polar flpt14 (/ pi 2) middle_line));螺栓中心线上点
    (setq flpt-10 (polar flpt04 (* pi 1.5) middle_line));螺栓中心线下点
  
    ;;;;;对称点
    (setq mi_flpt33 (miFlange flpt33 H))
    (setq mi_flpt32 (polar mi_flpt33 pi tn_down))
    (setq mi_flpt30 (miFlange flpt30 H))
    (setq mi_flpt31 (miFlange flpt31 H))
    (setq mi_flpt11 (miFlange flpt11 (- H L)))
    (setq mi_flpt51 (miFlange flpt51 (+ H L)))
    (setq mi_flpt50 (polar mi_flpt51 pi tn_down))
    (setq mi_flpt10  (miFlange flpt10 (- H L)))			
    (setq mi_flpt12 (miFlange flpt12 (- H L)))			
    (setq mi_flpt13 (miFlange flpt13 (- H L)))			
    (setq mi_flpt14 (miFlange flpt14 (- H L)))
    (setq mi_flpt15 (miFlange flpt15 (- H L)))			
    (setq mi_flpt16 (miFlange flpt16 (- H L)))			
    (setq mi_flpt21 (miFlange flpt21 (+ (- H L) R)))
    (setq mi_flpt20 (miFlange flpt20 (+ (- H L) R)))
    (setq mi_flpt40 (polar mi_flpt14 (* pi 1.5) middle_line))

  
    ;连线成法兰
    (command "layer" "M" "1轮廓实线层" "")
    ;绘制L和T型法兰的公共部分
    (command "line" flpt30 flpt33 "")
    (command "line" flpt10 flpt16 "")			
    (command "line" flpt31 flpt21 "")			
    (command "arc" flpt16 "c" flpt20 flpt21);倒角
    (command "line" flpt12 flpt02 "")			
    (command "line" flpt03 flpt13 "")
    (command "line" flpt15 flpt05 "")
    (command "line" flpt33 flpt51 "")
    (command "line" flpt32 flpt50 "")
    (if (= flange_type "L");
      (progn;如果是L型法兰
        (if (and (/= (value retEmbedded 0 0) "") (/= (value retEmbedded 0 0) nil) (= flange_index 0))
	  (progn ;如果基础那里有数,并且是底法兰
            (setq mi_flpt31 (polar mi_flpt33 pi (* (value retEmbedded 2 0) scale_fl)))
	    (setq mi_flpt21 (polar mi_flpt31 (/ pi 2) (- L R) ))
            (setq mi_flpt20 (polar mi_flpt21 pi R))
	    (setq mi_flpt16 (polar mi_flpt20 (/ pi 2) R));;;;;
	    (setq mi_flpt51 (polar mi_flpt33 (* pi 1.5) L))
	    (setq mi_flpt51 (polar mi_flpt51 0 (/  (- (* (value retEmbedded 4 0) scale_fl) Dout ) 2 )))
	    (setq mi_flpt50 (polar mi_flpt51 pi (* (value retEmbedded 5 0) scale_fl)))
            (setq mi_pt_h11 (polar mi_flpt51 (/ pi 2) L))
	    (setq mi_pt_t11 (polar mi_pt_h11 pi (* (value retEmbedded 5 0) scale_fl) ))
	    (setq ts (* (value retEmbedded 2 0) scale_fl));基础环上法兰脖子厚
	  )
	  (setq ts tn);若不是底法兰，或基础环数据那里为空
	)
        ;(setq repline2_mi (vla-mirror repline2 (vlax-3d-point rept1 ) (vlax-3d-point rept5)))
	;绘制L法兰特有的部分
	;(command "line" flpt00 flpt03 "");
	;(command "line" flpt05 flpt06 "");不再穿过螺栓
	(command "line" flpt00 flpt06 "");
	(command "line" flpt33 flpt06 "");右侧壁直线
        
	
	;绘制对称法兰
        ;视图名绘制,点，序号，比例

	(FlangeZoomTitle (polar flpt40 (* pi 0.5) (* 25 mScale)) 0 (/ mscale scale_fl))
	
	(Middleline flpt40 flpt-10 (* 20 scale_fl));螺栓中心的线

	(command "layer" "M" "2细线层" "")
	(command "spline" flpt51 flpt50 flpt30 flpt10 flpt00  "" "" "");绘制剖切的弧线
	;法兰厚度和高度标注
	;上法兰厚度标注
	(dimFlange_thick2 flpt12 flpt02 (/ 1 scale_fl ) 0 (- (/ (- H L) 2)) );法兰厚度
	(ldimv2 flpt33 flpt06 (/ 1 scale_fl ) 0 800);法兰高度
	;法兰脖子和连接筒体厚度标注
        (command "zoom" "w" mi_flpt51 mi_flpt31)
        (command "regen")

        ;(dimh flpt21 (polar flpt21 0 tn) (/ 1.000 scale_fl) -150 300 0 );上法兰脖子厚度标注
	
	(scaleDim2 flpt21 (polar flpt21 0 tn) scale_fl 2);上法兰脖子厚度标注
	
	(scaleDim flpt50 (polar flpt50 0 tn_top) scale_fl 1);上法兰上筒体厚度标注

	;;打剖面线,对角
        (bhatch_pt flpt12 flpt03 scale_fl 0)
	(bhatch_pt flpt15 flpt06 scale_fl 0)
	(bhatch_pt flpt50 flpt33 scale_fl 90)

        ;;;插入焊接符号
	(command "insert" "bfl_hjfh" "S" 1 flpt33 "")
	
      );L型法兰绘制结束	    
      (progn;如果是T型法兰
	
        (setq Da_outer (value retFlange flange_index 13));T型法兰外径
        (setq Dm_outer (value retFlange flange_index 14));T型法兰外分度圆
	(tfldadmouter T_D T_D relDout reltn relDpcd relDin);得到T型法兰的外径和外分度圆
	
        (setq T_D (* Da_outer scale_fl))

        (setq T_D_m (* Dm_outer scale_fl))
	
	(setq tflpt22 (polar flpt21 0 tn))
	
	(setq tflpt23 (polar tflpt22 0 R))
	(setq tflpt17 (polar flpt16 0 (+ tn (* R 2))))
	(setq tflpt111 (polar flpt12 0 (/ (- T_D Din)2)))
	
	(setq tflpt19 (polar tflpt111 pi (/ (- T_D T_D_m) 2)))
	(setq tflpt110 (polar tflpt19 0 (/ d 2)))
	(setq tflpt18 (polar tflpt19 pi (/ d 2)))
	(setq tflpt010 (polar tflpt111 (* 1.5 pi) (- H L)))
	(setq tflpt08 (polar tflpt19 (* 1.5 pi) (- H L)))
	(setq tflpt09 (polar tflpt08 0 (/ d 2)))
	(setq tflpt07 (polar tflpt08 pi (/ d 2)))
	
	(setq DTout T_D)
	(setq DTpcd T_D_m)
	(setq tflpt41 (polar tflpt19 (/ pi 2) middle_line))
	(setq tflpt-11 (polar tflpt08 (* pi 1.5) middle_line))
        ;绘制公共直线
	
        (command "line" tflpt17 tflpt111 "");右侧上端面横线
	(command "line" tflpt18 tflpt07 "");上法兰右侧螺栓孔左线
	(command "line" tflpt110 tflpt09 "");上法兰右侧螺栓孔右线
        (command "line" flpt33 tflpt22 "");;T上法兰右侧颈右侧竖线
	(command "line" tflpt111 tflpt010 "");T上法兰右侧最右竖线
	(command "arc" tflpt22 "c" tflpt23 tflpt17);T上法兰右侧圆角的弧
	(if (= flange_index 0);判断是否是底法兰
	  (progn;如果是底法兰
	  
	    (command "line" flpt00 tflpt010 "");下端面直线
	    ;;;绘制样条曲线
	    (command "layer" "M" "2细线层" "")
	    (command "spline" flpt51 flpt50 flpt30 flpt10 flpt00 "" "" "")
	    ;螺栓孔中心线
	    (Middleline flpt-10 flpt40 -80)
	    (Middleline tflpt-11 tflpt41 -80)
	    ;名称

	    (FlangeZoomTitle (polar flpt40 (* pi 0.5) (* 25 mScale)) 0 (/ mscale scale_fl))
	    ;标注
	    ;法兰厚度标注
	    (dimFlange_thick2 flpt12 flpt02 (/ 1 scale_fl ) 0 (- (/ (- H L) 2)) )
	    ;法兰高度标注
            (dimFlange_h flpt33 tflpt010 scale_fl -3000);法兰高度标注
	    ;法兰脖子标注
	    (scaleDim flpt21 (polar flpt21 0 tn) scale_fl 0);法兰脖子厚度标注
	    (scaleDim flpt50 (polar flpt50 0 tn_top) scale_fl 1);法兰高度标注
	    ;打剖面线
	    (bhatch_pt tflpt110 tflpt010 scale_fl 0)
	    (bhatch_pt flpt12 flpt03 scale_fl 0)
	    (bhatch_pt flpt15 flpt06 scale_fl 0)
	    (bhatch_pt flpt50 flpt33 scale_fl 90)
          );如果是底法兰的if右括号
          (progn;如果不是底法兰

            ;;;;镜像点,T型连接法兰特有的
	    (setq mi_tflpt19 (miFlange tflpt19 (- H L)));T型连接法兰特有的
            (setq mi_tflpt17 (miFlange tflpt17 (- H L)));T型连接法兰特有的
	    (setq mi_tflpt18 (miFlange tflpt18 (- H L)));T型连接法兰特有的
	    (setq mi_tflpt110 (miFlange tflpt110 (- H L)));T型连接法兰特有的
	    (setq mi_tflpt111 (miFlange tflpt111 (- H L)));T型连接法兰特有的
	    (setq mi_tflpt23 (polar mi_tflpt17 (* 1.5 pi) R));T型连接法兰特有的
            (setq mi_tflpt22 (polar mi_tflpt23 pi R ));T型连接法兰特有的
            (setq mi_tflpt41 (polar mi_tflpt19 (* 1.5 pi) middle_line));
            ;绘制线
	    ;(command "line" flpt00 flpt03 "");上法兰下底面左线
	    ;(command "line" flpt05 tflpt07 "");上法兰下底面中线
            ;(command "line" tflpt09 tflpt010 "");上法兰下底面右线
	    (command "line" flpt00 tflpt010 "");上法兰下底面左线
	    ;;;绘制样条曲线
	    (command "layer" "M" "2细线层" "")
	    (command "spline" flpt51 flpt50 flpt30 flpt10 flpt00 "" "" "");剖切线
            (Middleline flpt40 flpt-10 80)
	    ;(Middleline flpt40 mi_flpt40 (* 20 scale_fl))
	    ;(Middleline tflpt41 mi_tflpt41 (* 20 scale_fl))
	    (Middleline tflpt41 tflpt-11 80)
  
	    (FlangeZoomTitle (polar flpt40 (* pi 0.5) (* 25 mScale)) 0 (/ mscale scale_fl))	    
	    ;标注
	    
	    ;法兰厚度标注

            (dimFlange_thick2 flpt12 flpt02 (/ 1 scale_fl ) 0 (- (/ (- H L) 2)) );上法兰法兰厚度标注
	    (dimFlange_h flpt33 tflpt010 scale_fl -2500);上法兰高度标注

	    
	    ;法兰脖子标注
	    (scaleDim flpt21 (polar flpt21 0 tn) scale_fl 0);法兰脖子厚度标注
	    (scaleDim flpt50 (polar flpt50 0 tn_top) scale_fl 1);上法兰连接处筒节厚度标注

            ;打剖面线
            (bhatch_pt tflpt110 tflpt010 scale_fl 0);上法兰最右
	    (bhatch_pt flpt12 flpt03 scale_fl 0);上法兰最左
	    (bhatch_pt flpt15 flpt06 scale_fl 0);上法兰中间
	    (bhatch_pt flpt50 flpt33 scale_fl 90);上法兰连接筒节
	  );如果不是底法兰执行的右括号
	);if的右括号
	;;;插入焊接符号
	(command "insert" "bfl_hjfh" "S" 1 flpt33 "")
      );T 型法兰绘制结束
    );判断法兰类型右括号
);法兰绘制函数终结括号
;;;;;;;;;;;;;;;;;END WELDBFL_DRAW;;;;;;;;;;;;;;;;;;;;;



;;;;;;;;焊合图中上放大法兰绘制;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defun Weldufl_draw(scale_fl flange_type pt_flange relH relDout relDin relDpcd reltn reld_hole relL relR num_hole flange_index
	       sum_flange reltn_top reltn_down Neck_TopFlange
		   / H Dout Din Dpcd tn d L R middle_line Width Width_pcd_in 
		   flpt00 flpt01 flpt02 flpt04 flpt06 flpt33 flpt31 flpt21 flpt20 flpt16 flpt12 flpt03 flpt05 flpt14 flpt13
		   flpt15 flpt11 flpt10  flpt30 flpt32 flpt51 flpt50 flpt40 flpt-10
		   mi_flpt33 mi_flpt32 mi_flpt30 mi_flpt31 mi_flpt11 mi_flpt51 mi_flpt50 mi_flpt10 mi_flpt12 mi_flpt13
		   mi_flpt14 mi_flpt15 mi_flpt16 mi_flpt21 mi_flpt20 mi_flpt40
		   bottomflname );L型法兰绘制
    ;flange_type 法兰类型
    ;pt_flange法兰绘制初始点
    ;flange_index 法兰序号
    ;sum_flange 法兰总数
    ;reld_hole 螺栓孔直径
    ;(setq scale_fl 4.0);法兰放大系数
    (setq H (* relH scale_fl));H 法兰高度
    (setq Dout (* relDout scale_fl));Dout 法兰外径
    (setq Din (* relDin scale_fl));Din 法兰内径
    (setq Dpcd (* relDpcd scale_fl));Dpcd 分度圆直径
    (setq tn (* reltn scale_fl));tn 颈厚
    (setq d (* reld_hole scale_fl));d 螺栓孔直径
    (setq L (* relL scale_fl));L颈高
    (setq R (* relR scale_fl));R 圆角
    ;法兰相邻筒节的壁厚确定
    (if (and (/= reltn_top nil) (/= reltn_top 0) (/= reltn_top ""));reltn_top：壁厚
        (setq tn_top (* reltn_top scale_fl))
        (setq tn_top tn)
    )
    (if (and (/= reltn_down nil) (/= reltn_down 0) (/= reltn_down ""))
        (setq tn_down (* reltn_down scale_fl))
        (setq tn_down tn)
    )
    ;(setq middle_line (* 30 scale_fl))
    (setq middle_line (* 30 scale_fl))
    (setq Width (/ (- Dout Din) 2))	;;;     法兰宽度
    (setq Width_pcd_in (/ (- Dpcd Din) 2)) ;螺栓孔中心到法兰内径的距离
    ;关键点;;;;
    (setq flpt01 pt_flange)
    (setq flpt02 (polar flpt01 0 (/ Width 2)))
    (setq flpt04 (polar flpt02 0 Width_pcd_in)) 
    (setq flpt06 (polar flpt02 0 Width)) 
    (setq flpt33 (polar flpt06 (/ pi 2) H))  
    (setq flpt31 (polar flpt33 pi tn))
    (setq flpt21 (polar flpt31 (* pi 1.5) (- L R)))
    (setq flpt20 (polar flpt21 pi R))
    (setq flpt16 (polar flpt20 (* pi 1.5) R)) 
    (setq flpt12 (polar flpt02 (/ pi 2) (- H L)))
    (setq flpt03 (polar flpt04 pi (/ d 2)))
    (setq flpt05 (polar flpt04 0 (/ d 2)))
    (setq flpt14 (polar flpt04 (/ pi 2) (- H L)))
    (setq flpt13 (polar flpt14 pi (/ d 2)))
    (setq flpt15 (polar flpt14 0 (/ d 2)))
    (setq flpt11 (polar flpt01 (/ pi 2) (- H L)))
    (setq flpt10 (polar flpt11 pi (/ H 3)))
    (setq flpt00 (polar flpt01 pi (/ H 2)))
    (setq flpt30 (polar flpt11 (/ pi 2) L))
    (setq flpt32 (polar flpt33 pi tn_top))
    (setq flpt51 (polar flpt33 (* pi 0.5) L))
    (setq flpt50 (polar flpt51 pi tn_top))
    (setq flpt40 (polar flpt14 (/ pi 2) middle_line));螺栓中心线上点
    (setq flpt-10 (polar flpt04 (* pi 1.5) middle_line));螺栓中心线下点
  
    ;;;;;对称点
    (setq mi_flpt33 (miFlange flpt33 H))
    (setq mi_flpt32 (polar mi_flpt33 pi tn_down))
    (setq mi_flpt30 (miFlange flpt30 H))
    (setq mi_flpt31 (miFlange flpt31 H))
    (setq mi_flpt11 (miFlange flpt11 (- H L)))
    (setq mi_flpt51 (miFlange flpt51 (+ H L)))
    (setq mi_flpt50 (polar mi_flpt51 pi tn_down))
    (setq mi_flpt10  (miFlange flpt10 (- H L)))			
    (setq mi_flpt12 (miFlange flpt12 (- H L)))			
    (setq mi_flpt13 (miFlange flpt13 (- H L)))			
    (setq mi_flpt14 (miFlange flpt14 (- H L)))
    (setq mi_flpt15 (miFlange flpt15 (- H L)))			
    (setq mi_flpt16 (miFlange flpt16 (- H L)))			
    (setq mi_flpt21 (miFlange flpt21 (+ (- H L) R)))
    (setq mi_flpt20 (miFlange flpt20 (+ (- H L) R)))
    (setq mi_flpt40 (polar mi_flpt14 (* pi 1.5) middle_line))

  
    ;连线成法兰
    (command "layer" "M" "1轮廓实线层" "")
    (if (= flange_type "L");
      (progn;如果是L型法兰
        (if (and (/= (value retEmbedded 0 0) "") (/= (value retEmbedded 0 0) nil) (= flange_index 0))
	  (progn ;如果基础那里有数,并且是底法兰
            (setq mi_flpt31 (polar mi_flpt33 pi (* (value retEmbedded 2 0) scale_fl)))
	    (setq mi_flpt21 (polar mi_flpt31 (/ pi 2) (- L R) ))
            (setq mi_flpt20 (polar mi_flpt21 pi R))
	    (setq mi_flpt16 (polar mi_flpt20 (/ pi 2) R));;;;;
	    (setq mi_flpt51 (polar mi_flpt33 (* pi 1.5) L))
	    (setq mi_flpt51 (polar mi_flpt51 0 (/  (- (* (value retEmbedded 4 0) scale_fl) Dout ) 2 )))
	    (setq mi_flpt50 (polar mi_flpt51 pi (* (value retEmbedded 5 0) scale_fl)))
            (setq mi_pt_h11 (polar mi_flpt51 (/ pi 2) L))
	    (setq mi_pt_t11 (polar mi_pt_h11 pi (* (value retEmbedded 5 0) scale_fl) ))
	    (setq ts (* (value retEmbedded 2 0) scale_fl));基础环上法兰脖子厚
	  )
	  (setq ts tn);若不是底法兰，或基础环数据那里为空
	)
        ;(setq repline2_mi (vla-mirror repline2 (vlax-3d-point rept1 ) (vlax-3d-point rept5)))
	;绘制L法兰特有的部分
	;(command "line" flpt00 flpt03 "");
	;(command "line" flpt05 flpt06 "");不再穿过螺栓
        (command "line" flpt00 flpt06 "");法兰上面线
	
	;绘制对称法兰
        ;视图名绘制,点，序号，比例
	(FlangeZoomTitle (polar flpt-10 (* pi 0.5) (* 25 mScale)) 1 (/ mscale scale_fl))
	
	(command "line" mi_flpt30 mi_flpt33 "")
        (command "line" mi_flpt33 mi_flpt51 "")
	(command "line" mi_flpt32 mi_flpt50 "")
	
        (command "line" mi_flpt10  mi_flpt16 "")			
        (command "line" mi_flpt31 mi_flpt21 "")			
        (command "arc" mi_flpt21 "c" mi_flpt20 mi_flpt16)
        (command "line" mi_flpt12 flpt02 "")
        (command "line" flpt03 mi_flpt13 "")
        (command "line" mi_flpt15 flpt05 "")
        (command "line" mi_flpt33 flpt06 "")
	(setq flpt1.54 (polar flpt04 (/ pi 2) (* 30 scale_fl)));螺栓孔中心线的上点
	(Middleline flpt1.54 mi_flpt40 (* 20 scale_fl));螺栓中心的线
	(command "layer" "M" "2细线层" "")
	;(command "spline" flpt51 flpt50 flpt30 flpt10 flpt00  mi_flpt10  mi_flpt30 mi_flpt50 mi_flpt51 "" "" "");绘制剖切的弧线
	(command "spline" flpt00 mi_flpt10  mi_flpt30 mi_flpt50 mi_flpt51 "" "" "");绘制剖切的弧线
	;法兰厚度和高度标注
	;上法兰厚度标注
	;(dimFlange_thick2 flpt12 flpt02 (/ 1 scale_fl ) 0 (- (/ (- H L) 2)) )
        ;(dimFlange_thick flpt33 flpt06 "R" scale_fl);上法兰高度标注
	;(ldimv2 flpt33 flpt06 (/ 1 scale_fl ) 0 2500)
	;对称的下法兰度厚度标注
	(dimFlange_thick2 mi_flpt12 flpt02 (/ 1 scale_fl ) 0 (- (/ (- H L) 2)) )
        ;下法兰高度标注
	;(ldimv2 mi_flpt33 flpt06 (/ 1 scale_fl ) 0 800)
	(ldimv2 mi_flpt33 flpt06 (/ 1 scale_fl ) 0 (* mscale 20))
	
	;法兰脖子和连接筒体厚度标注
        (command "zoom" "w" mi_flpt51 mi_flpt31)
        (command "regen")
	;(scaleDim flpt21 (polar flpt21 0 tn) scale_fl 0);上法兰脖子厚度标注
	;(scaleDim flpt50 (polar flpt50 0 tn_top) scale_fl 1);上法兰上筒体厚度标注
	(scaleDim mi_flpt50 mi_flpt51 scale_fl 2) ;基础环筒体厚度标注
        (scaleDim mi_flpt21 (polar mi_flpt21 0 ts) scale_fl 0);下法兰脖子厚度标注

	;;打剖面线,对角
        ;(bhatch_pt flpt12 flpt03 scale_fl 0)
	;(bhatch_pt flpt15 flpt06 scale_fl 0)
	;(bhatch_pt flpt50 flpt33 scale_fl 90) 
	(bhatch_pt flpt02 mi_flpt13 scale_fl 90)
	(bhatch_pt flpt05 mi_flpt33 scale_fl 90)
	(bhatch_pt mi_flpt31 mi_flpt51 scale_fl 0)
	;;;插入焊接符号
	(command "insert" "ufl_hjfh" "S" 1 mi_flpt33 "")
	
      );L型法兰绘制结束	    
      (progn;如果是T型法兰
        (setq Da_outer (value retFlange flange_index 13));T型法兰外径
        (setq Dm_outer (value retFlange flange_index 14));T型法兰外分度圆
	(tfldadmouter T_D T_D relDout reltn relDpcd relDin);得到T型法兰的外径和外分度圆
        (setq T_D (* Da_outer scale_fl))
        (setq T_D_m (* Dm_outer scale_fl))
	(setq tflpt22 (polar flpt21 0 tn))
	(setq tflpt23 (polar tflpt22 0 R))
	(setq tflpt17 (polar flpt16 0 (+ tn (* R 2))))
	(setq tflpt111 (polar flpt12 0 (/ (- T_D Din)2)))
	(setq tflpt19 (polar tflpt111 pi (/ (- T_D T_D_m) 2)))
	(setq tflpt110 (polar tflpt19 0 (/ d 2)))
	(setq tflpt18 (polar tflpt19 pi (/ d 2)))
	(setq tflpt010 (polar tflpt111 (* 1.5 pi) (- H L)))
	(setq tflpt08 (polar tflpt19 (* 1.5 pi) (- H L)))
	(setq tflpt09 (polar tflpt08 0 (/ d 2)))
	(setq tflpt07 (polar tflpt08 pi (/ d 2)))
	(setq DTout T_D)
	(setq DTpcd T_D_m)
	(setq tflpt41 (polar tflpt19 (/ pi 2) middle_line))
	(setq tflpt-11 (polar tflpt08 (* pi 1.5) middle_line))
        ;绘制公共直线
        ;(command "line" tflpt17 tflpt111 "");右侧上端面横线
	;(command "line" tflpt18 tflpt07 "");上法兰右侧螺栓孔左线
	;(command "line" tflpt110 tflpt09 "");上法兰右侧螺栓孔右线
        ;(command "line" flpt33 tflpt22 "");;T上法兰右侧颈右侧竖线
	;(command "line" tflpt111 tflpt010 "");T上法兰右侧最右竖线
	;(command "arc" tflpt22 "c" tflpt23 tflpt17);T上法兰右侧圆角的弧
	(if (= flange_index 0);判断是否是底法兰
	  (progn;如果是底法兰
	    (command "line" flpt00 tflpt010 "");下端面直线
	    ;;;绘制样条曲线
	    (command "layer" "M" "2细线层" "")
	    (command "spline" flpt51 flpt50 flpt30 flpt10 flpt00 "" "" "")
	    ;(command "insert" boltName "S" scale_fl flpt14 "")
	    ;(command "insert" boltName "S" scale_fl tflpt19 "")
	    (Middleline flpt-10 flpt40 (* 20 scale_fl));螺栓孔中心线
	    (Middleline tflpt-11 tflpt41 (* 20 scale_fl));螺栓孔中心线
	    ;名称
	    (FlangeZoomTitle (polar flpt40 (* pi 0.5) (* 25 mScale)) 1 (/ mscale scale_fl))
	    ;标注
	    ;法兰厚度标注
	    ;法兰厚度标注
	    (dimFlange_thick2 flpt12 flpt02 (/ 1 scale_fl ) 0 (- (/ (- H L) 2)) )
	    ;法兰高度标注
             (dimFlange_h flpt33 tflpt010 scale_fl 2500);法兰高度标注
	    ;法兰脖子标注
	    (scaleDim flpt21 (polar flpt21 0 tn) scale_fl 0);法兰脖子厚度标注
	    (scaleDim flpt50 (polar flpt50 0 tn_top) scale_fl 1);法兰高度标注
	    ;打剖面线
	    (bhatch_pt tflpt110 tflpt010 scale_fl 0)
	    (bhatch_pt flpt12 flpt03 scale_fl 0)
	    (bhatch_pt flpt15 flpt06 scale_fl 0)
	    (bhatch_pt flpt50 flpt33 scale_fl 90)
          );如果是底法兰的if右括号
          (progn;如果不是底法兰

            ;;;;镜像点,T型连接法兰特有的
	    (setq mi_tflpt19 (miFlange tflpt19 (- H L)));T型连接法兰特有的
            (setq mi_tflpt17 (miFlange tflpt17 (- H L)));T型连接法兰特有的
	    (setq mi_tflpt18 (miFlange tflpt18 (- H L)));T型连接法兰特有的
	    (setq mi_tflpt110 (miFlange tflpt110 (- H L)));T型连接法兰特有的
	    (setq mi_tflpt111 (miFlange tflpt111 (- H L)));T型连接法兰特有的
	    (setq mi_tflpt23 (polar mi_tflpt17 (* 1.5 pi) R));T型连接法兰特有的
            (setq mi_tflpt22 (polar mi_tflpt23 pi R ));T型连接法兰特有的
            (setq mi_tflpt41 (polar mi_tflpt19 (* 1.5 pi) middle_line));
            ;绘制线
	    (command "line" mi_flpt10  mi_flpt16 "");下法兰左侧上面横线
            (command "line" mi_flpt31 mi_flpt21 "");下法兰颈左侧竖线
            (command "arc" mi_flpt21 "c" mi_flpt20 mi_flpt16);下法兰左侧圆角弧线
	    (command "line" mi_flpt12 flpt02 "");下法兰左侧最左竖线
	    (command "line" mi_flpt13 flpt03 "");下法兰左侧螺栓孔左侧竖线
	    (command "line" mi_flpt15 flpt05 "");下法兰左侧螺栓孔右侧竖线
	    (command "line" mi_flpt30 mi_flpt33 "");下法兰下筒节横线
            (command "line" mi_flpt33 mi_flpt51 "");下法兰右侧下筒节右侧竖线
	    (command "line" mi_flpt50 (polar mi_flpt50 (/ pi 2) L) "");下法兰左侧下筒节左侧竖线
	    (command "line" mi_flpt33 mi_tflpt22 "");;下法兰颈右侧竖线
            (command "line" mi_tflpt17 mi_tflpt111 "");下法兰右侧上面横线
            (command "line" mi_tflpt18 tflpt07 "");下法兰右螺栓孔左线	
	    (command "line" mi_tflpt110 tflpt09 "");下法兰右螺栓孔右线
	    (command "arc" mi_tflpt17 "c" mi_tflpt23 mi_tflpt22);下法兰右圆角弧线
	    (command "line" mi_tflpt111 tflpt010 "");下法兰最右侧竖线
	    
	    ;(command "line" flpt00 flpt03 "");上法兰下底面左线
	    ;(command "line" flpt05 tflpt07 "");上法兰下底面中线
            ;(command "line" tflpt09 tflpt010 "");上法兰下底面右线

	    (command "line" flpt00 tflpt010 "")
	    
	    ;;;绘制样条曲线
	    (command "layer" "M" "2细线层" "")
	    ;(command "spline" flpt51 flpt50 flpt30 flpt10 flpt00 mi_flpt10 mi_flpt30 mi_flpt50 mi_flpt51 "" "" "");剖切线
            (command "spline" flpt00 mi_flpt10 mi_flpt30 mi_flpt50 mi_flpt51 "" "" "");剖切线

	    (setq flpt1.54 (polar flpt04 (/ pi 2) (* 30 scale_fl)));螺栓孔中心线的上点
	    (Middleline flpt1.54 mi_flpt40 (* 20 scale_fl))
	    (setq tflpt1.58 (polar tflpt08 (/ pi 2) (* 30 scale_fl)));螺栓孔中心线的上点
	    (Middleline tflpt1.58 mi_tflpt41 (* 20 scale_fl))

	    (FlangeZoomTitle (polar flpt40 (* pi 0.5) (* 25 mScale)) 1 (/ mscale scale_fl))
		    
	    ;标注
	    
	    ;法兰厚度标注

            ;(dimFlange_thick2 flpt12 flpt02 (/ 1 scale_fl ) 0 (- (/ (- H L) 2)) );上法兰法兰厚度标注
	    ;(dimFlange_h flpt33 tflpt010 scale_fl 2500);上法兰高度标注
	    (dimFlange_thick2 mi_flpt12 flpt02 (/ 1 scale_fl ) 0 (- (/ (- H L) 2)) );下法兰法兰厚度标注
	    (dimFlange_h mi_flpt33 tflpt010 scale_fl -2500);下法兰高度标注

	    
	    ;法兰脖子标注
            (dimh mi_flpt21 (polar mi_flpt21 0 tn) (/ 1.0 scale_fl) -150 300 0 );下法兰脖子厚度标注
            ;(scaleDim mi_flpt21 (polar mi_flpt21 0 tn) scale_fl 0);下法兰脖子厚度标注

	    (scaleDim mi_flpt50 (polar mi_flpt50 0 tn_down) scale_fl 2);下法兰连接筒节厚度标注
	    

            ;打剖面线
            ;(bhatch_pt tflpt110 tflpt010 scale_fl 0);上法兰最右
	    ;(bhatch_pt flpt12 flpt03 scale_fl 0);上法兰最左
	    ;(bhatch_pt flpt15 flpt06 scale_fl 0);上法兰中间
	    ;(bhatch_pt flpt50 flpt33 scale_fl 90);上法兰连接筒节
            ;下法兰剖面线
            (bhatch_pt mi_tflpt110 tflpt010 scale_fl 90);下法兰最右
	    (bhatch_pt mi_flpt12 flpt03 scale_fl 90);下法兰最左
	    (bhatch_pt flpt05 mi_tflpt18 scale_fl 90);中间法兰
            (bhatch_pt mi_flpt50 mi_flpt33 scale_fl 0);下法兰连接筒节       
	  );如果不是底法兰执行的右括号
	);if的右括号
      );T 型法兰绘制结束
    );判断法兰类型右括号
);法兰绘制函数终结括号


;;;;;;;;;;;;;;;;;;;;;顶法兰插入;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defun weldtopflinsert (pt_flange TopFlangeName scale_fl relH / )
  ;法兰名称序号标注

  (FlangeZoomTitle (polar (polar pt_flange 0 (* 20 mscale)) (/ pi 2) (* 50 mscale)) 0 scale_fl)
  
  (if (/= TopFlangeName "other_TopFlange")
    (progn
      (if (= TopFlangeName "2.xMW_TopFlange")
        (command "insert" "weld_2.xMW_TopFlange" "S" 1 pt_flange"")
      )
      (if (= TopFlangeName "3MW_S_New_TopFlange")
        (command "insert" "weld_3MW_S_New_TopFlange" "S" 1 pt_flange"")
      )
    )
    ;(command "insert" TopFlangeName "S" (/ 1 5.0) pt_flange"")
    (print "顶法兰数据有误")
  );if的右括号
)
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


;;;;;;;;;;;;;;;;;;;;;;;;;;;******************焊合;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defun scaleDim2(pt1 pt2 scale isup)
  ;0为标注位置不动
  ;1为标注位置在上
  ;2为标注位置在下
  (setvar "dimlfac" (/ 1.0 scale)) ;尺寸比例
  (setvar "dimdec" 1);设置标注精度
  (cond ((= isup 0) (command "Dimlinear" pt1 pt2 (polar pt2 0 (distance pt1 pt2))))
	((= isup 1) (command "Dimlinear" pt1 pt2 (polar (polar pt2 (* pi 0.5) (distance pt1 pt2)) 0 (distance pt1 pt2))))
	((= isup 2) (command "Dimlinear" pt1 pt2 (polar (polar pt2 (* pi 1.5) (* (distance pt1 pt2) 2)) 0 (distance pt1 pt2))))
  )
  (setvar "dimlfac" 1)
  (setvar "dimdec" 0)
)
;;;;;;(scaleDim flpt21 (polar flpt21 0 tn) scale_fl 0);;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;End dimension;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


;;;;;;;;;多点转换函数;;;;;;
(defun 3dPointListToVariant (lst)
  (vlax-make-variant
    (vlax-safearray-fill
      (vlax-make-safearray vlax-VbDouble (cons 0 (1- (* 3 (length lst)))))
      (apply 'append lst)
    )
  )
)

(defun romanum (index / numlist num);
  (setq numlist (list "Ⅰ" "Ⅱ" "Ⅲ" "Ⅳ" "Ⅴ" "Ⅵ" "Ⅶ" "Ⅷ" "Ⅸ" "Ⅹ" "Ⅺ" "Ⅻ"))
  (setq num (nth index numlist))

);defun函数FlangeZoom右括号


;;;;;;;;;;加强板尺寸标注函数;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defun repdim (xmodels line1 line2 dimrotangle dimlocation repscale
	      / dpt1 dpt2 dpt_loca dim1 dimscale);xmodels:vla-get-modelspace,dpt1:dim point 1,dpt_local:dim point loaction标注值离标注线位置，标注的旋转角度

  (setq dpt1 (vlax-safearray->list (vlax-variant-value (vla-get-endpoint line1))));dim point upper left,标注上左点,把得到的属性点转换成坐标值
  (setq dpt2 (vlax-safearray->list (vlax-variant-value (vla-get-endpoint line2))))
  (setq dpt_loca (polar (MiddlePoint dpt1 dpt2) dimrotangle dimlocation ));标注尺寸的位置,两个标注点的中点位置再想处置方向位移500,dim location left
  (setq dim1 (vla-adddimaligned myms (vlax-3d-point dpt1) (vlax-3d-point dpt2) (vlax-3d-point dpt_loca)));壁厚标注尺寸，dim left
  (setq dimscale (/ repscale mscale ) );局部放大视图比例,dimscale:dim scale,实际标注尺寸
 
  (vla-put-PrimaryUnitsPrecision dim1 1.0);改变加强板标注主单位的精度
  (vla-put-LinearScaleFactor dim1 dimscale);改变加强板标注的尺寸比例
  
  ;(vlax-dump-object dim1 t)

)

;;;;;;;;;;;;;加强板剖面线绘制函数;;;;;;;;;;;;;;;;;;
(defun rephatch (xmodels loop hatchscale hatchangle / hatch1 );加强板放大视图剖面线绘制,
  (setq hatch1 (vla-addhatch myms "0" "ANSI31" :vlax-True));绘制剖面线,ANSI31类型的剖面线
  (vlax-invoke hatch1 'AppendOuterLoop loop);右侧加强板的剖面线
  (vla-put-PatternScale hatch1 hatchscale);剖面线的比例
  (vla-put-Layer hatch1 "5剖面线层");剖面线的图层
  (vla-put-PatternAngle hatch1 hatchangle);剖面线的角度
)

;;;;;;;;;;;;加强板引线说明绘制函数;;;;;;;;;;;;;;;;
(defun repleader (p1 p2 p3 / pts  led);三个点
  (setq pts (list p1 p2 p3))
  (setq pts (3dPointListToVariant pts));转换成引线可以用的数组点
  (setq led (vla-AddMLeader myms pts 0 ));多重引线
  (vla-put-ArrowheadType led 0);引线头的样式，0是箭头
  (vla-put-ArrowheadSize led 300);箭头的大小
  (vla-put-textstring led "          以不大于1:4的斜度平滑过渡\\PSlop transition with no more than 1:4");前面的空格用来实际中增加空格，标注的文字,\\p是回车的意思
  (vla-put-TextHeight led 300);标注文字的大小
  (vla-put-layer led "7标注层")
  ;(vlax-dump-object led t)
)

;;;;;;;;;;加强板直线绘制函数;;;;;;
(defun replinedraw (xmodels pt1 pt2 / repline);;;;绘制直线
  (setq repline (vla-addline xmodels (vlax-3d-point pt1) (vlax-3d-point pt2)));line1：直线1
  (vla-put-Layer repline "1轮廓实线层")
  (setq repline repline)
)

;;;;;;;;加强板题号标注
(defun repnumdraw (xmodels num oript rep_radius);oript，放大视图圆中心
  (setq numpt (polar oript (/ pi 2) (* rep_radius 2)));局部放大视图点
  (setq numscalept (polar oript (/ pi 2) (* rep_radius 1.6)));比例点左上角定位坐标
  (setq numscalept (polar numscalept pi (/ 350 4)));微调
  ;(setq numscalept (polar numscalept pi 200))
  
  (setq numlinemiddlept (polar oript (/ pi 2) (* rep_radius 1.7)));中间线的中点
  (setq numlineleftpt (polar numlinemiddlept pi (/ rep_radius 4.2)));中间线左点
  (setq numlinerightpt (polar numlinemiddlept 0 (/ rep_radius 3)));中间线右点
  
  (setq numdraw (vla-AddMText xmodels (vlax-3d-point numpt ) rep_radius  num));局部放大视图号
  (setq numdscaledraw (vla-AddMText xmodels (vlax-3d-point numscalept) rep_radius "1:10"));局部放大视图号

  (replinedraw myms numlineleftpt numlinerightpt);中间线绘制
  (vla-put-layer numdraw "6文字层")
  (vla-put-layer numdscaledraw "6文字层")
  (vla-put-Height numdraw 350)
  (vla-put-Height numdscaledraw 350)

)

(defun repdraw (t1 t2 mscale repscale oript num / x y );t1:塔筒壁厚，t2：加强板厚,repscale:加强板局部图的放大倍数，oript_x:加强板放大视图中心圆的x坐标,mscale图纸比例
  ;(vl-load-com)
  (setq myacad (vlax-get-acad-object))
  (setq mydoc (vla-get-ActiveDocument myacad))
  (setq myms (vla-get-ModelSpace mydoc));my models

  (setq t1 (* (/ mscale repscale) t1));壁厚，放大视图,放的倍数是总图放大比例除局部视图放大比例
  (setq t2 (* (/ mscale repscale) t2));加强板厚

  (setq oript_x (car oript))
  (setq oript_y (cadr oript))

  (setq x oript_x);初始圆心的x点
  (setq y oript_y);初始圆心的y点

  (setq rep_radius (* 5 t1));加强板放大视图中，圆圈的半径
  (setq rept3_x (+ x (* (- t2 t1) 2)))
  (setq rept3_y (+ y (/ t2 2)))
  (setq rept1 (list x y 0))
  (setq rept2 (polar rept1  (/ pi 2) (/ t1 2)))
  (setq rept3 (list rept3_x rept3_y))
  (setq rept4_x (+ x (abs (expt (- (expt rep_radius 2) (expt (/ t2 2) 2)) 0.5))))
  (setq rept4_y rept3_y)
  (setq rept4 (list rept4_x rept4_y))
  (setq rept5 (polar rept1  0 rep_radius));镜像中心线的右点
  (setq rept12 (polar rept2 pi (abs (expt (- (expt rep_radius 2) (expt (/ t1 2) 2)) 0.5))))
  
  (setq repline1 (replinedraw myms rept1 rept2));line1：直线1
  (setq repline2 (replinedraw myms rept2 rept3))
  (setq repline3 (replinedraw myms rept3 rept4))
  (setq repline4 (replinedraw myms rept2 rept12))
  
  (setq repline1_mi (vla-mirror repline1 (vlax-3d-point rept1 ) (vlax-3d-point rept5)));line1_mi:line1_mirror,直线的镜像
  (setq repline2_mi (vla-mirror repline2 (vlax-3d-point rept1 ) (vlax-3d-point rept5)))
  (setq repline3_mi (vla-mirror repline3 (vlax-3d-point rept1 ) (vlax-3d-point rept5)))
  (setq repline4_mi (vla-mirror repline4 (vlax-3d-point rept1 ) (vlax-3d-point rept5)))

  (setq angle_r (asin (/ t2 2) rep_radius));右侧的半弧度值
  (setq angle_l (asin (/ t1 2) rep_radius));左侧的半弧度值
  (setq rotate_angle (- (/ pi 6)));旋转的角度

  (setq arc_r (vla-addarc myms (vlax-3d-point rept1 ) rep_radius (- rotate_angle angle_r) (+ angle_r rotate_angle)));绘制右侧的小圆弧，逆时针绘制，圆心，半径，起始和终点弧度值
  (setq arc_l (vla-addarc myms (vlax-3d-point rept1 ) rep_radius (+ (- pi angle_l) rotate_angle) (+ (+ pi angle_l) rotate_angle)));绘制左侧的小圆弧，逆时针绘制
  
  (vla-Rotate repline1 (vlax-3d-point rept1 ) rotate_angle);方向是逆时针去旋转
  (vla-Rotate repline2 (vlax-3d-point rept1 ) rotate_angle)
  (vla-Rotate repline3 (vlax-3d-point rept1 ) rotate_angle)
  (vla-Rotate repline4 (vlax-3d-point rept1 ) rotate_angle)
  (vla-Rotate repline1_mi (vlax-3d-point rept1 ) rotate_angle)
  (vla-Rotate repline2_mi (vlax-3d-point rept1 ) rotate_angle)
  (vla-Rotate repline3_mi (vlax-3d-point rept1 ) rotate_angle)
  (vla-Rotate repline4_mi (vlax-3d-point rept1 ) rotate_angle)

  (setq dim_l (repdim myms repline4 repline4_mi (+ pi rotate_angle) (/ rep_radius 2) repscale));标注壁厚的函数，连个最左线的端点，标注值偏转的角度以及距离
  (setq dim_r (repdim myms repline3 repline3_mi (+ 0 rotate_angle) (/ rep_radius 2) repscale));标注加强板厚
  
  (rephatch myms (list repline1 repline4 repline1_mi repline4_mi arc_l) mscale (/ pi 4));筒壁剖面线的绘制
  (rephatch myms (list repline1 repline2 repline3 repline1_mi repline2_mi repline3_mi arc_r) mscale 0);加强板剖面线的绘制

  (vla-delete arc_r);删除绘制剖面线用的右弧
  (vla-delete arc_l);
  (setq jqb_circle (vla-addcircle myms (vlax-3d-point rept1 ) rep_radius));绘制放大视图的圆
  (vla-put-Layer jqb_circle "8符号标注层")

  ;以不大于1:4的斜度平滑过渡
  (setq arrowstpt (vlax-safearray->list (vlax-variant-value (vla-get-endpoint repline1_mi))) );多重引线箭头的原点arrow start point
  ;(setq arrowenpt (list (- x (* rep_radius 1)) (- y (* rep_radius 0.7)) 0));多重引线的终点,arrow end point
  ;(setq textenpt (list (- x (* rep_radius 5)) (- y (* rep_radius 0.7)) 0));多重引线的横线的最终点,text end point
  ;(repleader arrowstpt arrowenpt textenpt);引线说明文字
  (command "insert" "repslope" "S" 1 arrowstpt "" )
  
  ;焊接符号插入
  (setq weldstpt (vlax-safearray->list (vlax-variant-value (vla-get-endpoint repline1))) );焊接符号插入的原点
  (command "insert" "repweldsymbol" "S" 1 weldstpt "" )

  ;序号和比例绘制  
  ;(setq num (romanum num))
  ;(repnumdraw myms num oript rep_radius)
  (FlangeZoomTitle (polar oript (/ pi 2) 4600) num 10)
  
)


;(defun C:zx ()
  ;(setq dd (getpoint "选原点"))
  ;(princ dd)
  ;(setq ddx (car dd))aa
  ;(setq ddy (cadr dd))
 ; (repdraw 30 60 100.0 10.0 dd 5);
;)





(defun c:test1()

 (sectiononeweld 4666.0  3840.0 4085.0 4437.0 54.0 92.0 67.0 96.0 36.0 70.0 2778.0 4300.0 3420.0 1240.0 2420.0 820.0 200.0 145.0 2333.0 2672.0)
)
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;第一段焊合函数;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defun sectiononeweld (pt_fs Dout_tfl  Din_fl Dpcd_fl Dpcd_tfl  d_tfl  num_hole_tfl  d_fl  num_hole_fl  t1 t2  H  D  hh1 h2 h1 b1  R  H_flange cylinder_H1  cylinder_H2 /  sw1  sw2  sw3   sw4   sw5  sw6  sw7   wcr  circle1  circle2   circle3   circle4  circle5 circle6 wd1  wd2 wd3 wd4 wd5 wd6 wd7 wd8 line1 line2 line3 i
                                                                                                                                                                                    drn circlen scale_RPlate t1_wall t2_Rplate jpt10 jpt11 jpt12 jpt2 jpt1 jpt9 jpt8 jpt0 jpt3 jpt7 jpt13 jpt4 jpt5 jpt6 jpt14 jpt15 jpt16 jpt17 line4 line5 line6
                                                                                                                                                                                    line6 line7 line8 line9 line10 line11 line12 line13  line14 line15 line16 jpt20 jpt22 jpt23 jpt2320 jpt2321 jpt24 jpt26 jpt27 sw8 sw9 DoorPoint DP_up DP_down
                                                                                                                                                                                    halfLAxis  halfSAxis  ratio pt1 pt3 pt5  pt7  pt9 pt11 angle_plate width_plate plate_up_pt pt15 pt15_c pt16 pt16_c door_up_pt door_down_pt pt8 pt10 pt12 
                                                                                                                                                                                    pt12_c pt2 pt4 pt6 pt6_c plate_down_pt pt13 pt14 pt_btm neck_pt_T ptd7 ptd8 ptd_L ptd_R upper_ptd_L1 upper_ptd_R1 upper_ptd_T1 pt_T_L pt_T_R line17 line18
		                                                                                                                                                                    line19 line20 line21 line22 secPoint fpt1 fpt2 fpt3 fpt4 angle1_frame_thick angle2_frame_thick L angle_L1 angle_R1 angle_L2 angle_R2 fpt11 fpt12 fpt13 fpt14
                                                                                                                                                                                    door_trig_inner_h door_trig_outer_h angle_trig_half1 angle_trig_half2 angle_frame_inner_L angle_frame_inner_R angle_frame_outer_L angle_frame_outer_R door_pt1
                                                                                                                                                                                    door_pt2 door_pt3 door_pt4  fpt16 fpt18 fpt15 fpt17 line23 line24 line25 line26 arc1 arc2 arc3 arc4 dimscale sw10 sw11 door_pt5 c1 door_trig_outer_h1 angle_trig_half3
		                                                                                                                                                                    angle_frame_outer_L1  angle_frame_outer_R1 door_pt7  door_pt8 jpt87 jpt88)


 
  (setq myacad (vlax-get-acad-object))
  (setq mydoc (vla-get-ActiveDocument myacad))
  (setq myms (vla-get-ModelSpace mydoc));my models
 
  (setq sw1 '(17000 8300 0 ));筒体壁厚焊接示意图插入点
  (command "insert" "wallwelding" "S" 1 sw1 "");插入筒体壁厚焊接示意图
  
  (setq sw2 '(14000 1500 0 ));塔架中英文技术要求插入点
  ;(command "insert" "walltechrequ" "S" 1 sw2 "");插入塔架中英文技术要求
  
  (setq sw3 '(44000 30000 0 ));加强板展开图插入点
  (command "insert" "platedevelopview" "S" 1 sw3 "");插入加强板展开图
  (setq sw4 '(32400 22000 0 ));门框图插入点
  (command "insert" "doorfdview" "S" 1 sw4 "");插入门框图
  (setq sw5 '(27000 11000 0 ));加强板与筒壁壁厚插入点
  (command "insert" "platewallfd" "S" 1 sw5 "");插入加强板与筒壁壁厚
  (setq sw6 '(26700 6000 0 ));版权插入点
  (command "insert" "Banquan" "S" 1 sw6 "");插入版权
  (setq sw7 '(44000 14500 0 ));连接耳板放大插入点
  (command "insert" "lugweld" "S" 1 sw7 "");插入版权
  
  ;;;;;;插入俯视图，定位法兰与筒体关系
  ;(setq wcr '(7200 4700 0 ));指定圆心
  ;俯视图的圆的圆心
  (setq wcr pt_fs);指定圆心
    
  ;(setq Dout_tfl 4666.0);指定法兰外径
  (command "layer" "M" "1轮廓实线层" "")
  (setq circle1 (vla-addcircle myms (vlax-3d-point wcr) (/ Dout_tfl 2))) ;画出法兰外圆
  ;(setq Dout_wal 4300.0);指定筒体外径
  (setq circle2 (vla-addcircle myms (vlax-3d-point wcr) (/ D 2))) ;画出筒体外圆
  ;(setq t_wal 25.7)
  (setq circle3 (vla-addcircle myms (vlax-3d-point wcr) (/ (- D (* 2 t1)) 2)));画出筒体壁厚所在圆
  ;(setq Din_fl 3840);指定法兰一内圆直径
  (setq circle4 (vla-addcircle myms (vlax-3d-point wcr) (/ Din_fl 2))) ;画出法兰一内圆直径
  ;(setq Dpcd_fl 4085);指定法兰一分度圆直径
  (command "layer" "M" "4虚线层" "")
  (setq circle5 (vla-addcircle myms (vlax-3d-point wcr) (/ Dpcd_fl 2)));画出法兰一分度圆直径
  ;(setq Dpcd_tfl 4437);指定底法兰分度圆直径
  (setq circle6 (vla-addcircle myms (vlax-3d-point wcr) (/ Dpcd_tfl 2)));画出底法兰分度圆直径
  (command "layer" "M" "1轮廓实线层" "")
  ;(setq d_tfl 54.0);指定T法兰螺栓孔直径
  ;(setq num_hole_tfl 92.0);指定T法兰螺栓孔数量
  ;(setq d_fl 67.0);指定连接法兰一螺栓孔直径
  ;(setq num_hole_fl 96.0);指定T法兰螺栓孔数量
  (setq wd1 (polar wcr 0 (+ 200 (/ Dout_tfl 2))));指定中心线右端点
  (setq wd2 (polar wcr (/ pi 2) (+ 200 (/ Dout_tfl 2))));指定中心线上端点
  (setq wd3 (polar wcr pi (+ 200 (/ Dout_tfl 2))));指定中心线左端点
  (setq wd4 (polar wcr (* 1.5  pi) (+ 200 (/ Dout_tfl 2))));指定中心线右端点
  (setq wd5 (polar wcr (* 1.5  pi) (/ Dpcd_tfl 2)));指定T法兰螺栓孔起始点
  (setq wd6 (polar wcr (* (/ 323.0 180.0) pi) (/ Dout_tfl 2)));指定T法兰螺栓孔起始点与53度线
  (setq wd7 (polar wcr (* (/ 143.0 180.0) pi) (/ Dout_tfl 2)));指定53度线终点
  (setq wd8 (polar wcr (* (/ 323.0 180.0) pi) (/ Dpcd_tfl 2)));指定T法兰螺栓孔起始点与53度线
  (command "layer" "M" "3中心线层" "")
  (setq line1 (vla-addline myms (vlax-3d-point wd1) (vlax-3d-point wd3)));画出中心线水平线
  (setq line2 (vla-addline myms (vlax-3d-point wd2) (vlax-3d-point wd4)));画出中心线竖直线
  (setq line3 (vla-addline myms (vlax-3d-point wd7) (vlax-3d-point wd6)));画出中心线竖直线
  (command "layer" "M" "1轮廓实线层" "")
  (setq num_hole_tfl 92.0);指定螺栓孔数量
  (setq i 1);指定循环起始数据
    (while (<= i num_hole_tfl)
      (setq drn (polar wcr (+ (* 1.5 pi) (* (* pi 2)(/ i num_hole_tfl ))) (/ Dpcd_tfl 2)));指定螺栓孔圆心
      (setq circlen (vla-addcircle myms (vlax-3d-point drn) (/ d_tfl 2)));循环画出螺栓孔
      (setq i (+ 1 i)) 
     )
  (setq num_hole_fl 96.0);指定螺栓孔数量
  (setq i 1);指定循环起始数据
    (while (<= i num_hole_fl)
      (setq drn (polar wcr (+ (* (/ 323.0 180.0) pi) (* (* pi 2) (/ i num_hole_fl ))) (/ Dpcd_fl 2)));指定螺栓孔圆心
      (setq circlen (vla-addcircle myms (vlax-3d-point drn) (/ d_fl 2)));循环画出螺栓孔
      (setq i (+ 1 i)) 
     )
;;;;;;加强板与筒体壁厚关系表达;;;;;;;;
  (setq scale_RPlate 12);
  ;(setq t1 34);指定塔筒壁厚度
  ;(setq t2 70);指定加强板厚度
  (setq t1_wall (* t1 scale_RPlate));塔筒壁厚度放大
  (setq t2_Rplate (* t2 scale_RPlate));补强板厚度放大
  (setq jpt10 '(26000 25000 0 ));补强板与筒壁定位第一点
  (setq jpt11 (polar jpt10 0  (/ t1_wall 2.0)));补强板与筒壁定位点	
  (setq jpt12 (polar jpt11 0  (/ t1_wall 2.0)));补强板与筒壁定位点
  (setq jpt2 (polar jpt12 (* pi 1.5)  1000));补强板与筒壁定位点
  (setq jpt1 (polar jpt2  pi  (/ t1_wall 2)));补强板与筒壁定位点
  (setq jpt9 (polar jpt1  pi  (/ t1_wall 2)));补强板与筒壁定位点
  (setq jpt8 (polar jpt9  pi  (/ (- t2_Rplate t1_wall) 2)));补强板与筒壁定位点
  (setq jpt0 (polar jpt2  0 (/ (- t2_Rplate t1_wall) 2)));补强板与筒壁定位点
  (setq jpt3 (polar jpt0  (* pi 1.5) (* (- t2_Rplate t1_wall) 2)));补强板与筒壁定位点
  (setq jpt7 (polar jpt8  (* pi 1.5) (* (- t2_Rplate t1_wall) 2)));补强板与筒壁定位点
  (setq jpt13 (polar jpt1  (* pi 1.5) (* (- t2_Rplate t1_wall) 2)));补强板与筒壁定位点
  (setq jpt4 (polar jpt3  (* pi 1.5) 1600));补强板与筒壁定位点
  (setq jpt5 (polar jpt13  (* pi 1.5) 1700));补强板与筒壁定位点
  (setq jpt6 (polar jpt7 (* pi 1.5) 1750));补强板与筒壁定位点
  (setq jpt14 (polar jpt11 (* pi 0.5) 200));补强板与筒壁定位点
  (setq jpt15 (polar jpt12 (* pi 0.5) 300));补强板与筒壁定位点
  (setq jpt16 (polar jpt5 (* pi 1.5) 200));补强板与筒壁定位点
  (setq jpt17 (polar jpt6 (* pi 1.5) 400));补强板与筒壁定位点
  
  (setq line4 (vla-addline myms (vlax-3d-point jpt10) (vlax-3d-point jpt14)));画出补强板与筒壁放大图连线
  
  (setq line5 (vla-addline myms (vlax-3d-point jpt14) (vlax-3d-point jpt15)));画出补强板与筒壁放大图连线
  (setq line6 (vla-addline myms (vlax-3d-point jpt12) (vlax-3d-point jpt2)));画出补强板与筒壁放大图连线
  (setq line7 (vla-addline myms (vlax-3d-point jpt10) (vlax-3d-point jpt9)));画出补强板与筒壁放大图连线
  (setq line8 (vla-addline myms (vlax-3d-point jpt2) (vlax-3d-point jpt3)));画出补强板与筒壁放大图连线
  (setq line9 (vla-addline myms (vlax-3d-point jpt9) (vlax-3d-point jpt7)));画出补强板与筒壁放大图连线
  (setq line10 (vla-addline myms (vlax-3d-point jpt3) (vlax-3d-point jpt4)));画出补强板与筒壁放大图连线
  (setq line11 (vla-addline myms (vlax-3d-point jpt7) (vlax-3d-point jpt6)));画出补强板与筒壁放大图连线

  (setq line12 (vla-addline myms (vlax-3d-point jpt6) (vlax-3d-point jpt5)));画出补强板与筒壁放大图连线

  (setq line13 (vla-addline myms (vlax-3d-point jpt5) (vlax-3d-point jpt4)));画出补强板与筒壁放大图连线
  
  (setq line14 (vla-addline myms (vlax-3d-point jpt9) (vlax-3d-point jpt1)));画出补强板与筒壁放大图连线
  (setq line15 (vla-addline myms (vlax-3d-point jpt1) (vlax-3d-point jpt2)));画出补强板与筒壁放大图连线
  (setq line16 (vla-addline myms (vlax-3d-point jpt15) (vlax-3d-point jpt12)));画出补强板与筒壁放大图连线

  (setq jptmidu (polar jpt14 (/ pi 2) 100));加强板与筒节放大图中心线上点
  (setq jptmidd (polar jpt5 (* pi 1.5) 100));加强板与筒节放大图中心线下点
  (setq jptmidline (vla-addline myms (vlax-3d-point jptmidu) (vlax-3d-point jptmidd)));画出补强板与筒壁放大图中心线
  (vla-put-Layer jptmidline "3中心线层");剖面线的图层
  
  
  
  ;(command "spline" jpt10 jpt14 jpt15  "" "" "")
  ;(command "spline" jpt4 jpt5 jpt6 "" "" "")
  (rephatch myms (list line14 line7 line4 line5 line16 line6 line15 ) 120 0)
  (rephatch myms (list line14 line15 line8 line10 line13 line12 line11 line9) 120 60)
  ;;;;;;加强板与筒体壁厚尺寸标注(以下定义各点为标注尺寸放置位置)
  (setq jpt20 (polar jpt9 (* pi 0.5) 700));补强板与筒壁定位点
  (setq jpt22 (polar jpt2 (* pi 0.5) 700));补强板与筒壁定位点
  (setq jpt23 (polar jpt22 0 500));补强板与筒壁定位点
  (setq jpt2320 (polar jpt23 (* pi 0.5) 500));补强板与筒壁定位点
  (setq jpt2321 (polar jpt12 0 600));补强板与筒壁定位点
  ;(setq line17 (vla-addline myms (vlax-3d-point jpt20) (vlax-3d-point jpt22)));画出补强板与筒壁放大图连线
  ;(scaleDim jpt10 jpt12 scale_RPlate jpt2321);标注筒壁厚度
  (setq jpt24 (polar jpt7 (* pi 1.5) 500));补强板与筒壁定位点
  (setq jpt26 (polar jpt3 (* pi 1.5) 500));补强板与筒壁定位点
  (setq jpt27 (polar jpt26 0 1000));补强板与筒壁定位点
  ;(scaleDim jpt26 jpt24 scale_RPlate jpt27);标注加强板厚度
  ;(dimh3 jpt26 jpt24 (/ 1.0 12.0) 800 0 0)
 ;;;;;加强板与筒体壁厚关系视图III标注;;;;;;;;
  (setq sw8 (polar jpt11 (* pi 0.5) 2100));补强板与筒壁定位点
  (command "insert" "wpview3" "S" 1 sw8 "");插入筒体壁厚焊接示意图
  (command "insert" "hanjiefuhao" "S" 1 jpt2 "");插入筒体壁厚焊接示意图
  (setq sw9 (polar wcr (* pi 0.5) 5300));补强板与筒壁定位点
  ;(command "insert" "zhutixinxi" "S" 1 sw9 "");插入筒体壁厚焊接示意图

;;;;;;加强板主视图生成;;;;;;;;
  ;(setq H 2778.0);门框位置
  ;(setq D 4300.0);塔筒壁直径
  ;(setq t1 34.0);塔筒壁厚度
  ;(setq hh1 3420.0) ; 补强板高度
  ;(setq t2 70.0);补强板厚度
  ;(setq h2 1240.0);直边长度H_V
  ;(setq h1 2420.0);门洞高度H2
  ;(setq b1 820.0) ;门洞宽度w
  ;(setq R 200.0 )
  ;(setq H_flange 145)
  ; (alert "B")
  (setq DoorPoint '(42244 22638 0 ));定义中心点
  ;(alert "D")
  (setq DP_up (polar DoorPoint (* pi 0.5) (/ h2 2)))
  (setq DP_down (polar DoorPoint (* pi 1.5) (/ h2 2)))
  (setq halfLAxis (/ (- h1 h2) 2));椭圆长半轴
  (setq halfSAxis (/ b1 2));椭圆短半轴
  (setq ratio (/ halfSAxis halfLAxis))
  (setq pt1 (polar DoorPoint 0 (/ b1 2)))
  ;(alert "f")
  (setq pt3 (polar pt1 (/ pi 2) (/ h2 2)))
  (setq pt5 (polar pt3 (* pi 1.5) h2))
  (setq pt7 (polar DoorPoint pi (/ b1 2)))
  (setq pt9 (polar pt7 (/ pi 2) (/ h2 2)))
  (setq pt11 (polar pt9 (* pi 1.5) h2))
  (setq angle_plate (/ pi 6))
  ;(alert "h")
  (setq width_plate (* D (sin angle_plate)));加强板展开后的宽度
   ;(alert "x")
  (setq plate_up_pt (polar DoorPoint (* pi 0.5) (/ hh1 2)))
  (setq pt15 (polar plate_up_pt pi (- (/ width_plate 2) R)))
   (setq pt15_c (polar pt15 (* pi 1.5) R))
  ; (alert "z")
  (setq pt16 (polar plate_up_pt 0 (-(/ width_plate 2) R)))
  (setq pt16_c (polar pt16 (* pi 1.5) R))           
  (setq door_up_pt (polar DP_up (* pi 0.5) halfLAxis))
   ;(alert "y")
  (setq door_down_pt (polar DP_down (* pi 1.5) halfLAxis))
  (setq pt8 (polar DoorPoint pi (/ width_plate 2)))
  (setq pt10 (polar pt8 (* pi 0.5) (- (/ hh1 2) R)))
  (setq pt12 (polar pt8 (* pi 1.5) (- (/ hh1 2) R)))
  ;(alert "C")
  (setq pt12_c (polar pt12 0 R))                                  
  (setq pt2 (polar DoorPoint 0 (/ width_plate 2)))
  (setq pt4 (polar pt2 (* pi 0.5) (- (/ hh1 2) R)))
  (setq pt6 (polar pt2 (* pi 1.5) (- (/ hh1 2) R)))
  (setq pt6_c (polar pt6 pi R))	
  (setq plate_down_pt (polar DoorPoint (* pi 1.5) (/ hh1 2)))
  (setq pt13 (polar plate_down_pt pi (- (/ width_plate 2) R)))
  (setq pt14 (polar plate_down_pt 0 (- (/ width_plate 2) R)))
  ;                           (alert "A")
  ;(setq line17 (vla-addline myms (vlax-3d-point pt9) (vlax-3d-point pt11)));画出补强板门洞处视图
  ;(setq line18 (vla-addline myms (vlax-3d-point pt3) (vlax-3d-point pt5)));画出补强板门洞处视图
  ;(setq line19 (vla-addline myms (vlax-3d-point door_up_pt) (vlax-3d-point door_down_pt)));画出补强板门洞处视图
  ;(setq ellipse1 (vla-addellipse myms (vlax-3d-point Dp_up) (* 2 halfLAxis) ratio));画出补强板门洞处视图
  ;(setq ellipse1 (vla-addellipse myms (vlax-3d-point Dp_up) 300 0.5));画出补强板门洞处视图
(entmake (list '(0 . "ELLIPSE")
		 '(100 . "AcDbEntity")
		 '(100 . "AcDbEllipse")
		 (cons 10 DP_up)
		 (cons 11 (list 0 halfLAxis 0))
		 (cons 40 ratio)
		 (cons 41 (sk_el_ang->Par(ang->rad -90) ratio))
		 (cons 42 (sk_el_ang->Par(ang->rad 90) ratio))
		 (cons 8"1轮廓实线层")
		 )
	   )
   (entmake (list '(0 . "line") (cons 10 pt3) (cons 11 pt5) (cons 8 "1轮廓实线层")))
  (entmake (list '(0 . "line") (cons 10 pt9) (cons 11 pt11) (cons 8 "1轮廓实线层")))
  (entmake (list '(0 . "line") (cons 10 pt7) (cons 11 pt8) (cons 8 "1轮廓实线层")))
  (entmake (list '(0 . "line") (cons 10 pt1) (cons 11 pt2) (cons 8 "1轮廓实线层")))
  (entmake (list '(0 . "ELLIPSE")
		 '(100 . "AcDbEntity")
		 '(100 . "AcDbEllipse")
		 (cons 10 DP_down)
		 (cons 11 (list 0 halfLAxis 0))
		 (cons 40 ratio)
		 (cons 41 (sk_el_ang->Par(ang->rad 90) ratio))
		 (cons 42 (sk_el_ang->Par(ang->rad 270) ratio))
		 (cons 8"1轮廓实线层")
		 )
	   )
  (entmake (list '(0 . "line") (cons 10 pt10) (cons 11 pt12)(cons 8"1轮廓实线层")))
  (entmake (list '(0 . "line") (cons 10 pt13) (cons 11 pt14)(cons 8"1轮廓实线层")))
  (entmake (list '(0 . "line") (cons 10 pt6) (cons 11 pt4)(cons 8"1轮廓实线层")))
  (entmake (list '(0 . "line") (cons 10 pt16) (cons 11 pt15)(cons 8"1轮廓实线层")))
  (entmake (list '(0 . "ARC") (cons 10 pt15_c) (cons 40 R)(cons 50 (/ pi 2))(cons 51 pi)(cons 8"1轮廓实线层")))
  (entmake (list '(0 . "ARC") (cons 10 pt16_c) (cons 40 R)(cons 50 0)(cons 51 (/ pi 2))(cons 8"1轮廓实线层")))
  (entmake (list '(0 . "ARC") (cons 10 pt12_c) (cons 40 R)(cons 50 pi)(cons 51 (* pi 1.5))(cons 8"1轮廓实线层")))
  (entmake (list '(0 . "ARC") (cons 10 pt6_c) (cons 40 R)(cons 50 (* pi 1.5))(cons 51 0)(cons 8"1轮廓实线层")))
  (entmake (list '(0 . "line") (cons 10 (polar pt7 pi 50)) (cons 11 (polar pt1 0 50))(cons 8"3中心线层")))
  (entmake (list '(0 . "line") (cons 10 (polar DP_up (/ pi 2) (+ halfLAxis 50)))
		 (cons 11 (polar DP_down (* pi 1.5) (+ halfLAxis 50)))(cons 8"3中心线层")))
  ;;;;;;画出加强板周围的筒节
  (setq pt_btm (polar DoorPoint (* pi 1.5) H))
  (setq neck_pt_T (polar pt_btm (* pi 0.5) H_flange))
  (setq ptd7 (polar neck_pt_T pi (/ D 2)))
  (setq ptd8 (polar neck_pt_T 0 (/ D 2)))
  ;(setq cylinder_H1 2333)
  ;(setq cylinder_H2 2672)
  (setq ptd_L (polar ptd7 (* pi 0.5) cylinder_H1))
  (setq ptd_R (polar ptd8 (* pi 0.5) cylinder_H1))
  (setq upper_ptd_L1 (polar ptd_L (* pi 0.5) cylinder_H2))
  (setq upper_ptd_R1 (polar ptd_r (* pi 0.5) cylinder_H2))
  (setq upper_ptd_T1 (polar upper_ptd_L1 0 (/ D 2)))
  (setq pt_T_L (polar ptd_L 0 (/ (- D width_plate) 2)))
  (setq pt_T_R (polar ptd_r pi (/ (- D width_plate) 2)))
  (setq line17 (vla-addline myms (vlax-3d-point ptd7) (vlax-3d-point ptd8)));画出补强板周围筒节
  (setq line18 (vla-addline myms (vlax-3d-point ptd7) (vlax-3d-point upper_ptd_L1)));画出补强板周围筒节
  (setq line19 (vla-addline myms (vlax-3d-point upper_ptd_L1) (vlax-3d-point upper_ptd_R1)));画出补强板周围筒节
  (setq line20 (vla-addline myms (vlax-3d-point upper_ptd_R1) (vlax-3d-point ptd8)));画出补强板周围筒节
  (setq line21 (vla-addline myms (vlax-3d-point ptd_L) (vlax-3d-point pt_T_L)));画出补强板周围筒节
  (setq line22 (vla-addline myms (vlax-3d-point ptd_R) (vlax-3d-point pt_T_R)));画出补强板周围筒节
  ;(setq arc1 (vla-addarc myms (vlax-3d-point flpt20) R_flange (-(/ pi 2)) 0));画出法兰放大图连线
  ;;;;;;;;;;;画出补强板俯视图;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  (setq secPoint '(36473 17624 0 ))
  (setq scale 3)
  (setq	D (* D scale)	;塔筒壁直径
	t1 (* t1 scale)	;塔筒壁厚度
    	hh1 (* hh1 scale) ; 补强板高度
	t2 (* t2 scale)	;补强板厚度
        h2 (* h2 scale)	;直边长度H_V
	h1 (* h1 scale)	;门洞高度H2
	b1 (* b1 scale);门洞宽度w
  )
  (setq 
	fpt1 (polar secPoint (* pi (/ 4 3.0)) (/ (- D (* 2 t1)) 2))
	fpt2 (polar secPoint (* pi (/ 4 3.0)) (/ D 2))
	fpt3 (polar secPoint (* pi (/ 5 3.0)) (/ (- D (* 2 t1)) 2))
	fpt4 (polar secPoint (* pi (/ 5 3.0)) (/ D 2))
	
	angle1_frame_thick (- (/ pi 2) (atan 0.25) (/ pi 3))
	angle2_frame_thick (- (/ pi 2) (atan 0.25) (/ pi 6))
	L (* (/ (- t2 t1) 2) (sqrt 17))	
	angle_L1 (+ (* pi (/ 4 3.0)) (angle_b (+ (/ pi 3) angle1_frame_thick) L (/ (- D (+ t2 t1))2.0)))
  	angle_R1 (- (* pi (/ 5 3.0))(angle_b (+ (/ pi 3) angle1_frame_thick) L (/(- D (+ t2 t1))2.0)))
  	angle_L2 (+ (* pi (/ 4 3.0))(angle_b (- (* pi (/ 5 6.0)) angle2_frame_thick) L (/(+ D (- t2 t1))2.0)))
 	angle_R2 (- (* pi (/ 5 3.0))(angle_b (- (* pi (/ 5 6.0)) angle2_frame_thick) L (/(+ D (- t2 t1))2.0)))
	fpt11(polar secPoint angle_L1 (/ (- D (+ t2 t1)) 2.0))
	fpt12(polar secPoint angle_R1 (/ (- D (+ t2 t1)) 2.0))
	fpt13(polar secPoint angle_L2 (/ (+ D (- t2 t1)) 2.0))
	fpt14(polar secPoint angle_R2 (/ (+ D (- t2 t1)) 2.0))
	door_trig_inner_h(sqrt (- (* (/(- D (+ t2 t1))2.0)(/(- D (+ t2 t1))2.0)) (* (/ b1 2.0)(/ b1 2.0))))
	door_trig_outer_h(sqrt (- (* (/(+ D (- t2 t1))2.0)(/(+ D (- t2 t1))2.0)) (* (/ b1 2.0)(/ b1 2.0))))
	angle_trig_half1(atan (/ b1 2 door_trig_inner_h))
	angle_trig_half2(atan (/ b1 2 door_trig_outer_h))
	angle_frame_inner_L(- (* pi 1.5) angle_trig_half1)
	angle_frame_inner_R(+ (* pi 1.5) angle_trig_half1)
	angle_frame_outer_L(- (* pi 1.5) angle_trig_half2)
	angle_frame_outer_R(+ (* pi 1.5) angle_trig_half2)
	door_pt1(polar secPoint angle_frame_outer_L (/(+ D (- t2 t1))2.0))
	door_pt2(polar secPoint angle_frame_outer_R (/(+ D (- t2 t1))2.0))
	door_pt3(polar secPoint angle_frame_inner_L (/(- D (+ t2 t1))2.0))
	door_pt4(polar secPoint angle_frame_inner_R (/(- D (+ t2 t1))2.0))
        fpt16 (polar secPoint (* pi (/ 5 4.0)) (/ D 2))
        fpt18 (polar secPoint (* pi (/ 7 4.0)) (/ D 2))
	fpt15 (polar secPoint (* pi (/ 5 4.0)) (/ (- D (* 2 t1)) 2))
	fpt17 (polar secPoint (* pi (/ 7 4.0)) (/ (- D (* 2 t1)) 2))
  )
  ;(entmake (list '(0 . "ARC") (cons 10 secPoint) (cons 40 (/ D 2))(cons 50 (* pi (/ 5 3.0)))(cons 51 (* pi (/ 4 3.0)))(cons 8"1轮廓实线层")))
  ;(entmake (list '(0 . "ARC") (cons 10 secPoint) (cons 40 (/ (- D (* 2 t1)) 2))(cons 50 (* pi (/ 5 3.0)))(cons 51 (* pi (/ 4 3.0)))(cons 8"1轮廓实线层")))
  ;(entmake (list '(0 . "line") (cons 10 fpt1) (cons 11 fpt2)(cons 8"1轮廓实线层")));
  (entmake (list '(0 . "line") (cons 10 fpt3) (cons 11 fpt4)(cons 8"1轮廓实线层")));
  (setq line23 (vla-addline myms (vlax-3d-point fpt1) (vlax-3d-point fpt2)))
  (setq line24 (vla-addline myms (vlax-3d-point fpt3) (vlax-3d-point fpt4)))
  (entmake (list '(0 . "line") (cons 10 fpt1) (cons 11 fpt11)(cons 8"1轮廓实线层")))
  (entmake (list '(0 . "line") (cons 10 fpt2) (cons 11 fpt13)(cons 8"1轮廓实线层")))
  (entmake (list '(0 . "line") (cons 10 fpt12) (cons 11 fpt3)(cons 8"1轮廓实线层")))
  (entmake (list '(0 . "line") (cons 10 fpt14) (cons 11 fpt4)(cons 8"1轮廓实线层")))
  (entmake (list '(0 . "line") (cons 10 door_pt1) (cons 11 door_pt3)(cons 8"1轮廓实线层")))
  (entmake (list '(0 . "line") (cons 10 door_pt2) (cons 11 door_pt4)(cons 8"1轮廓实线层")))
  (entmake (list '(0 . "ARC") (cons 10 secPoint) (cons 40 (/ (- D (+ t2 t1 )) 2.0))(cons 50 angle_L1)(cons 51 angle_R1)(cons 8"1轮廓实线层")))
  (entmake (list '(0 . "ARC") (cons 10 secPoint) (cons 40 (/ (+ D (- t2 t1 )) 2.0))(cons 50 angle_L2)(cons 51 angle_R2)(cons 8"1轮廓实线层")))
  (entmake (list '(0 . "line") (cons 10 (polar secPoint 0 (+(/ 300 2)100))) (cons 11 (polar secPoint pi (+(/ 300 2)100)))(cons 8 "3中心线层")))
  (entmake (list '(0 . "line") (cons 10 (polar secPoint (/ pi 2) (+(/ 300 2)100))) (cons 11 (polar secPoint (* pi 1.5) (+(/ D 2)100)))(cons 8 "3中心线层")))
  (entmake (list '(0 . "line") (cons 10 secPoint) (cons 11 fpt1)(cons 8"3中心线层")))
  (entmake (list '(0 . "line") (cons 10 secPoint) (cons 11 fpt3)(cons 8"3中心线层")))
  (setq arc1 (vla-addarc myms (vlax-3d-point secPoint) (/ (- D (* 2 t1)) 2) (* pi (/ 5 4.0)) (* pi (/ 4 3.0))))
  (setq arc2 (vla-addarc myms (vlax-3d-point secPoint) (/ D 2) (* pi (/ 5 4.0)) (* pi (/ 4 3.0))))
  (setq arc3 (vla-addarc myms (vlax-3d-point secPoint) (/ (- D (* 2 t1)) 2) (* pi (/ 5 3.0)) (* pi (/ 7 4.0))))
  (setq arc4 (vla-addarc myms (vlax-3d-point secPoint) (/ D 2) (* pi (/ 5 3.0)) (* pi (/ 7 4.0))))
  (setq line25 (vla-addline myms (vlax-3d-point fpt15) (vlax-3d-point fpt16)));画出补强板周围筒节
  (setq line26 (vla-addline myms (vlax-3d-point fpt17) (vlax-3d-point fpt18)));画出补强板周围筒节
  ;(alert "a")
  (bhatch_pt fpt13 door_pt1 (* scale 2) pi)
  ;(alert "b")
  (bhatch_pt fpt14 door_pt2 (* scale 2) pi)
  ;(alert "c")
  (rephatch myms (list line23 line25 arc1 arc2 ) 60 45)
  ;(alert "d")
  (rephatch myms (list line24 line26 arc3 arc4 ) 60 45)
  ;(alert "e")
  (setq dimscale 1);放大反比例
  ;(alert "f")
  ;筒节直径标注
  (dimd upper_ptd_L1 upper_ptd_R1 dimscale 900.0 0 0 );筒节直径标注
  ;(alert "g")
  (ldimv2 pt3 pt5 dimscale 0 2100);门洞直边长,pt1 pt2 dimscale disv dish
  ;(ldimv2 door_up_pt door_down_pt dimscale 0 3800);门洞长度,pt1 pt2 dimscale disv dish
  (dimFlange_thick3 door_up_pt door_down_pt dimscale 0 3500)
  (ldimv2 pt13 pt15 dimscale 600 -900);加强板长度
  (command "dimlinear" ptd7 pt8 "v" (polar ptd7 pi 900));放大视图中的门洞位置高度
  (dimh1 pt9 pt3 dimscale 1440 0 0)
  ;(command "insert" "repdc112" "S" 1 DoorPoint "");插入A向视图符号
  ;(command "insert" "sectionA" "S" 1 DoorPoint "");插入A向视图符号 
  ;(vlax-dump-object dimh1 t);;;用于查找对象属性
  ;加强板半椭圆符号标注
  (setq sw10 '(42161 26933 0 ));插入塔筒门洞开孔插入点
  (command "insert" "Towerdoorhole" "S" 1 sw10 "");插入塔筒门洞开孔注释
  (command "insert" "weldcategory2" "S" 1 DoorPoint "");插入焊缝等级注释
  (command "insert" "BBview" "S" 1 DoorPoint "");插入BB剖注释
  (command "insert" "semiellipse" "S" 1 Door_down_pt "");插入半椭圆注释
  (setq sw11 '(36501 18798 0 ));插入BB视图
  (command "insert" "BBbili" "S" 1 sw11 "");插入半椭圆注释
  (setq dim1 (vla-AddDimAngular myms (vlax-3d-point secPoint) (vlax-3d-point fpt1) (vlax-3d-point fpt3) (vlax-3d-point (polar secPoint (* pi 1.5) (/ D 4.0) ))));门洞角度标注
  (setq dim2 (vla-AddDimAngular myms (vlax-3d-point wcr) (vlax-3d-point wd4) (vlax-3d-point wd6) (vlax-3d-point (polar wcr (* pi 1.7) (/ Din_fl 4.0) ))));法兰角度标注
  ;(alert "a")
  (dimh4 fpt3 fpt1 (* (/ 1.0 3.0) dimscale) 800 0 0)
  ;(alert "b")
  ;(dimr secpoint (/ (- D (+ t2 t1)) 2.0) (* pi (/ 34.0 24.0)) (* (/ 1.0 3.0) dimscale) 300)
  ;(dimr secpoint (/ (+ D (- t2 t1)) 2.0) (* pi (/ 38.0 24.0)) (* (/ 1.0 3.0) dimscale) 300)
  (command "insert" "daojiaobiaozhu" "S" 1 Door_pt3 "");插入半椭圆注释
  (dimh4 door_pt3 door_pt4 (* (/ 1.0 3.0) dimscale) -1300 0 0)
  (setq door_pt5 (polar door_pt4 (* 1.5 pi ) 750))
  (command "insert" "cucaodu" "S" 1 door_pt5 "");插入粗糙度注释
  
   ;插入连接耳板注释及尺寸
  (setq c1 4314.0)
  (setq
   door_trig_outer_h1(sqrt (- (* (/(+ D (- t2 t1))2.0)(/(+ D (- t2 t1))2.0)) (* (/ c1 2.0)(/ c1 2.0))))
   angle_trig_half3(atan (/ c1 2 door_trig_outer_h1))
   angle_frame_outer_L1(- (* pi 1.5) angle_trig_half3)
   angle_frame_outer_R1(+ (* pi 1.5) angle_trig_half3)
   door_pt7(polar secPoint angle_frame_outer_L1 (/(+ D (- t2 t1))2.0))
   door_pt8(polar secPoint angle_frame_outer_R1 (/(+ D (- t2 t1))2.0))

   )
   (command "insert" "leftlianjieerban" "S" 1 door_pt7 "");插入连接耳板注释
   (command "insert" "lianjieerban" "S" 1 door_pt8 "");插入连接耳板注释
   (command "insert" "fangdasign" "S" 1 door_pt8 "");插入连接耳板注释
   ;插入连接耳板序号
   (command "insert" "erban_lead_5" "S" 1 door_pt7 "");插入连接耳板注释
  
   (dimh2 door_pt7 door_pt8 (* (/ 1.0 3.0) dimscale) -2000 0 0)
   ;(dimh3 jpt24 jpt26 (* (/ 1.0 3.0) dimscale) -2000 0 0)
   (dimh3 jpt20 jpt22 (* (/ 1.0 12.0) dimscale) 700 0 0)
   (dimh3 jpt26 jpt24 (* (/ 1.0 12.0) dimscale) -2000 0 0)
   (setq jpt87 (polar jpt8  (* pi 1.5) (- t2_Rplate t1_wall) ));补强板与筒壁定位点
   (setq jpt88 (polar jpt87  pi 500 ));补强板与筒壁定位点
   ;(command "dimlinear" jpt9 jpt7 "v" jpt88);放大视图中的门洞位置高度
   (scaleDim3 jpt9 jpt7 12.0 jpt88 )
   (dimr2 secpoint (/ (- D (+ t2 t1)) 2.0) (* pi (/ 34.0 24.0)) (* (/ 1.0 3.0) dimscale) -500)
   (dimr2 secpoint (/ (+ D (- t2 t1)) 2.0) (* pi (/ 38.0 24.0)) (* (/ 1.0 3.0) dimscale) -500)
  

  )




;;;;;;;;;;;;;加强板与筒壁剖面线绘制函数;;;;;;;;;;;;;;;;;;
(defun rephatch (xmodels loop hatchscale hatchangle / hatch1 );加强板放大视图剖面线绘制,
  (setq hatch1 (vla-addhatch myms "0" "ANSI31" :vlax-True));绘制剖面线,ANSI31类型的剖面线
  (vlax-invoke hatch1 'AppendOuterLoop loop);右侧加强板\ 的剖面线
  (vla-put-PatternScale hatch1 hatchscale);剖面线的比例
  (vla-put-Layer hatch1 "5剖面线层");剖面线的图层
  (vla-put-PatternAngle hatch1 hatchangle);剖面线的角度
)


;;;;;;;;;;;;;;;;;;;;;;********************法兰厚度标注（加强板与筒壁水平尺寸标注）;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defun scaleDim3(pt1 pt2 scale pt3)
  (setvar "dimlfac" (/ 1.0 scale)) ;尺寸比例
  (setvar "dimdec" 0);设置标注精度
  (command "Dimlinear" pt1 pt2 pt3)
  (setvar "dimlfac" 1)
  (setvar "dimdec" 0)
)
;;;;




;;;;;;;;;;;;;;;;;**********已知∠A，b,c,求∠B;;;;;;;;;;;;;;;;;;;;;;;;;
(defun angle_b(angle_a b c)
  (setq a (sqrt (+ (* b b)(* c c)(* -2 b c (cos angle_a)))))
  (setq sinb (*(/ b a)(sin angle_a)))
  (setq cosb (/(+ (* a a)(* c c)(* b (- b)))(* 2 a c)))
  (atan (/ sinb cosb))
 )
;;;;;;;;;*************打剖面线函数;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defun bhatch_pt(pt1 pt2 scale angle_bhatch / middle_pt)
  ;;pt1 pt2为闭合空间斜角两点
  ;;scale 比例
  ;;angle_bhatch 剖面线角度
  (command "zoom" "w" pt1 pt2)
  (command "regen")
  (command "zoom" "w" (polar pt1 (* 0.75 pi) (* 50 scale)) (polar pt2 (* -0.25 pi) (* 50 scale)))
  (setq scale (* 2.5 scale))
  (command "layer" "M" "5剖面线层" "")
  (setq middle_pt (MiddlePoint pt1 pt2))
  (setvar "HPGAPTOL" 0 ) ;容差
  (command "bhatch" "p" "ansi31" scale angle_bhatch middle_pt "")
  (princ)
)
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;尺寸标注系列;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


;;;竖直标注，主要用在了门洞那块
(defun ldimv2 (pt1 pt2 dimscale disv dish / p3x p3y p_dim);left dim vertical,左方向竖直标注,点1，点2，左右距离，上下距离
  (setq p3x (+ (car (MiddlePoint pt1 pt2)) dish) ) ;竖直标注x方向，car：返回x坐标值
  (setq p3y ( + (cadr (MiddlePoint pt1 pt2)) disv) )
  ;(setq p3y (+ (cadr pt1) disv) )
  (setq p_dim (list p3x p3y))
  ;(command "dimlinear"  pt1 pt2 "v" p_dim)
  (setq dim1 (vla-adddimaligned myms (vlax-3d-point pt1) (vlax-3d-point pt2) (vlax-3d-point p_dim) ) )
  (vla-put-TextPosition dim1 (vlax-3d-point p_dim))
  ;(vlax-dump-object dim1 t)
  ;(setq dimscale (/ 40.0 mscale ) );局部放大视图比例,dimscale:dim scale,实际标注尺寸
  (vla-put-LinearScaleFactor dim1 dimscale);改变标注的尺寸比例
)
;;;;;;;;;;;;;;;;;;;;;;;带精度的高度标注
(defun dimFlange_thick3(pt1 pt2 dimscale disv dish  / p3x p3y p_dim  dim1)
  (setq p3x (+ (car (MiddlePoint pt1 pt2)) dish))
  (setq p3y (+ (cadr pt1) disv ))
  (setq p_dim (list p3x p3y ))
  (setq dim1 (vla-adddimaligned myms (vlax-3d-point pt1) (vlax-3d-point pt2) (vlax-3d-point p_dim) ) )
  (vla-put-ToleranceDisplay dim1 2)
  (vla-put-TolerancePrecision dim1 1)
  (vla-put-ToleranceUpperLimit dim1 2.0)
  (vla-put-ToleranceLowerLimit dim1 -0.50)
  (vla-put-LinearScaleFactor dim1 dimscale);改变加强板标注的尺寸比例
  ;(vlax-dump-object dim1 t)用于查找属性
);



;;;;水平尺寸标注带精度
(defun dimh1 (pt1 pt2 dimscale disv dish ang / p3x p3y p_dim dim1 handle_value);dim diamatric 直径标注,点1，点2，竖直位置，水平左右位置，旋转角度
  ;(setq p3x (+ (car pa) dish))
  ;(setq p3y (+ (cadr pt1) disv ))
  (setq p3x (+ (car (MiddlePoint pt1 pt2)) dish))
  (setq p3y (+ (cadr pt1) disv ))
  (setq p_dim (list p3x p3y ))
  (setq dimh1 (vla-adddimaligned myms (vlax-3d-point pt1) (vlax-3d-point pt2) (vlax-3d-point p_dim) ) )
  (vla-put-ToleranceDisplay dimh1 2)
  (vla-put-TolerancePrecision dimh1 1)
  (vla-put-ToleranceUpperLimit dimh1 2.0)
  (vla-put-ToleranceLowerLimit dimh1 -0.5)
  (vla-put-LinearScaleFactor dimh1 dimscale);改变加强板标注的尺寸比例
  ;(vla-put-TextOverride dim1 "%%C<>");把直径符号加入到标注尺寸最前面
  (vla-put-TextPosition dimh1 (vlax-3d-point p_dim))
  ;(vlax-dump-object dim1 t)
  (setq handle_value (vla-get-Handle dimh1))
  (command "dimedit" "o"  (handent handle_value) "" ang)
)
;;;;水平尺寸标注
(defun dimh4 (pt1 pt2 dimscale disv dish ang / p3x p3y p_dim dim1 handle_value);dim diamatric 直径标注,点1，点2，竖直位置，水平左右位置，旋转角度
  ;(setq p3x (+ (car pa) dish))
  ;(setq p3y (+ (cadr pt1) disv ))
  (setq p3x (+ (car (MiddlePoint pt1 pt2)) dish))
  (setq p3y (+ (cadr pt1) disv ))
  (setq p_dim (list p3x p3y ))
  (setq dim1 (vla-adddimaligned myms (vlax-3d-point pt1) (vlax-3d-point pt2) (vlax-3d-point p_dim) ) )
  (vla-put-LinearScaleFactor dim1 dimscale);改变加强板标注的尺寸比例
  (vla-put-TextSuffix dim1 ")");标注后面加文字
  (vla-put-TextPrefix dim1 "(");把
  ;(vla-put-TextOverride dim1 "%%C<>");把直径符号加入到标注尺寸最前面
  (vla-put-TextPosition dim1 (vlax-3d-point p_dim))
  ;(vlax-dump-object dim1 t)
  (setq handle_value (vla-get-Handle dim1))
  (command "dimedit" "o"  (handent handle_value) "" ang)
)
;;;;;
;圆弧标注
(defun dimr2(center R ang dimscale leng / dim1)
  (setq dim1 (vla-AddDimRadial myms (vlax-3d-point center) (vlax-3d-point (polar center ang R)) leng ) )
  (vla-put-LinearScaleFactor dim1 dimscale)
  (vla-put-PrimaryUnitsPrecision dim1 1);显示1位有效小数
)
;;;End dimr2

;;;;水平尺寸标注带精度
(defun dimh2 (pt1 pt2 dimscale disv dish ang / p3x p3y p_dim dim1 handle_value);dim diamatric 直径标注,点1，点2，竖直位置，水平左右位置，旋转角度
  ;(setq p3x (+ (car pa) dish))
  ;(setq p3y (+ (cadr pt1) disv ))
  (setq p3x (+ (car (MiddlePoint pt1 pt2)) dish))
  (setq p3y (+ (cadr pt1) disv ))
  (setq p_dim (list p3x p3y ))
  (setq dimh2 (vla-adddimaligned myms (vlax-3d-point pt1) (vlax-3d-point pt2) (vlax-3d-point p_dim) ) )
  (vla-put-ToleranceDisplay dimh2 2)
  (vla-put-TolerancePrecision dimh2 0)
  (vla-put-ToleranceUpperLimit dimh2 0)
  (vla-put-ToleranceLowerLimit dimh2 2)
  (vla-put-LinearScaleFactor dimh2 dimscale);改变加强板标注的尺寸比例
  ;(vla-put-TextOverride dim1 "%%C<>");把直径符号加入到标注尺寸最前面
  (vla-put-TextPosition dimh2 (vlax-3d-point p_dim))
  ;(vlax-dump-object dim1 t)
  (setq handle_value (vla-get-Handle dimh1))
  (command "dimedit" "o"  (handent handle_value) "" ang)
)

;;;;水平尺寸标注
(defun dimh3 (pt1 pt2 dimscale disv dish ang / p3x p3y p_dim dim1 handle_value);dim diamatric 直径标注,点1，点2，竖直位置，水平左右位置，旋转角度
  ;(setq p3x (+ (car pa) dish))
  ;(setq p3y (+ (cadr pt1) disv ))
  (setq p3x (+ (car (MiddlePoint pt1 pt2)) dish))
  (setq p3y (+ (cadr pt1) disv ))
  (setq p_dim (list p3x p3y ))
  (setq dim1 (vla-adddimaligned myms (vlax-3d-point pt1) (vlax-3d-point pt2) (vlax-3d-point p_dim) ) )
  (vla-put-LinearScaleFactor dim1 dimscale);改变加强板标注的尺寸比例
  ;(vla-put-TextSuffix dim1 ")");标注后面加文字
  ;(vla-put-TextPrefix dim1 "(");把
  ;(vla-put-TextOverride dim1 "%%C<>");把直径符号加入到标注尺寸最前面
  (vla-put-TextPosition dim1 (vlax-3d-point p_dim))
  ;(vlax-dump-object dim1 t)
  (setq handle_value (vla-get-Handle dim1))
  (command "dimedit" "o"  (handent handle_value) "" ang)
)
;;;;函数结束


;图纸初始化，visul lisp代码使用
;1_2.x_TechReq_detail:2MW_TopFlange,2.xMW_TopFlange,2MW-20_TopFlange,21_TopFlange的第一个技术条件
;2_2.x_TechReq_detail:2MW_TopFlange,2.xMW_TopFlange,2MW-20_TopFlange,21_TopFlange的第二个技术条件
;znq_2_2.x_TechReq_detail:阻尼器技术条件
;znq_2_21_TechReq_detail

;En_2.x_TechReq_detail
;2_En_2.x_TechReq_detail
;znq_2_En_2.x_TechReq_detail

;znq_2_En_21_TechReq_detail

;1_3S_TechReq_detail
;2_3S_TechReq_detail
;znq_2_3S_TechReq_detail
;En_3S_TechReq_detail
;2_En_3S_TechReq_detail
;znq_2_En_3S_TechReq_detail

;21and2.xtable
;EN21and2.xtable
;3Stable
;EN3Stable

(defun AllTextOfTechReq(/ temp )

(setq NoticeofBid "     说明
此图仅供招标，不得用于生产，如需用于备料请申请确认"
)
(setq NoticeofDia_1 "             直径标注中缩写的说明"
)
(setq NoticeofDia_2 "  
           
MD-Middle Diameter、FLG OD-Flange Outer Diameter"
)

; 根据项目最低气温选板材的技术要求用
; (if (or (= (value retDescription 0 9) nil) (= (value retDescription 0 9) ""))
    ; (progn
	    ; (if is_alert (alert "请明确项目最低气温"))
	    ; (print "请明确项目最低气温")
	    ; (setq temp "待确认")
	; );end progn
    ; (setq temp (rtos (value retDescription 0 9) 2 0));项目最低气温
; )

(setq TechReqTitle "                  技术要求")
(setq En_TechReqTitle "                  Technical requirements")

;5H,5S,V12
(setq 1_5S_TechReq_detail "      
            
1、塔体制造按照Q/GW202002《风力发电机组 塔架技术规范》最新版执行；
2、塔架防腐按照Q/GW201175《陆上风力发电机组 塔架通用防腐技术规范》最新版执行；
3、塔架法兰按照Q/GW203041《风力发电机组 塔架整锻法兰技术条件》最新版执行；
4、塔架紧固件按照Q/GW203008《风力发电机组通用技术规范 紧固件》最新版执行；
5、根据项目现场结构工作温度（最低气温T），各材料选取原则如下表："
)

(setq 2_5S_TechReq_detail
"6、塔架内铝合金爬梯要求有CE认证并取得金风科技认可；
7、塔筒内升降机的参数应符合当地电网的要求；
8、未注主体筒节环焊缝按照DC90执行；
9、与筒壁、传统门框焊接的附件（参考具体详图），其焊趾处采用超声波冲击工艺处理，
具体要求应符合Q/GW204021《风力发电机组 超声波冲击工艺技术要求》；
10、塔筒壁%%C16扰流条孔用堵头封堵，安装时堵头配合表面涂抹锂基脂；
11、如项目不配置升降机，则取消升降机吊梁及其附属构件与焊点、升降机护栏、升降机
平台过孔增加盖板封堵，对应内附件减重XXXkg。"
)

(setq 3_5S_TechReq_detail
"11、如项目不配置升降机，则取消升降机吊梁及其附属构件与焊点、升降机护栏、升降机
平台过孔增加盖板封堵，对应内附件减重XXXkg。"
)

(setq znq_2_5S_TechReq_detail
"6、塔架内铝合金爬梯要求有CE认证并取得金风科技认可；
7、塔筒内升降机的参数应符合当地电网的要求；
8、未注主体筒节环焊缝按照DC90执行；
9、与筒壁、传统门框焊接的附件（参考具体详图），其焊趾处采用超声波冲击工艺处理，
具体要求应符合Q/GW204021《风力发电机组 超声波冲击工艺技术要求》；
10、塔筒壁%%C16扰流条孔用堵头封堵，安装时堵头配合表面涂抹锂基脂。
11、阻尼器及辅材模块应满足《GW-00CG.0548 阻尼器及辅材模块-液体阻尼器订货技术文件》要求；
12、如项目不配置升降机，则取消升降机吊梁及其附属构件与焊点、升降机护栏、升降机
平台过孔增加盖板封堵，对应内附件减重XXXkg。"
)
(setq znq_3_5S_TechReq_detail
"12、如项目不配置升降机，则取消升降机吊梁及其附属构件与焊点、升降机护栏、升降机
平台过孔增加盖板封堵，各平台需参考免爬器方案图纸进行改造，对应内附件减重XXXkg。"
)
(setq En_5S_TechReq_detail "  
               
1.The requirements of manufacturing should be executed under the lastest version of
\"EN Q/GW 202005 Technical Specifications for Tower of Wind Turbine Generator System(overseas manufacturing)\".
2.The requirements of corrosion protection should be executed under the lastest version of
\"EN Q/GW 201177 Goldwind Onshore Wind Turbine Technical Specifications for Corrosion Protection of Towers 
(For Towers Manufactured Overseas)\".
3.The requirements of flange should be executed under the lastest version of 
 \"EN Q/GW 203050 Goldwind Wind Turbine Technical Specifications for Integrally Forged Tower Flange 
 (Overseas Manufacturing）\".
4.The requirements of fasteners should be executed under the lastest version of
\"EN Q/GW 203008 General technical specifications for wind turbine – fasteners\".
5.Generally, The selection principle of material is as follows:"
)

(setq 2_En_5S_TechReq_detail "Their weights are shown in its formal drawing.
6.All requirements of aluminium alloy ladder in tower must be certificated by CE and  approved by Goldwind.
7.Parameters of the lift should comply with the local power grid requirements.
8.The girth welds of tower cylinder without notes shall be DC90.
9.The welding toes of internals ,connected to tower shell or traditional doorframe should be  treated by 
Ultrasonic Impact Treatement(UIT).More detailed requirments should be conform to the “EN Q/GW 204021
 Technical Requirements on Ultrasonic Impact Treatment for Wind Turbine”.
10.The tower drum wall %%C16 spoiler hole is blocked with plug, and the plug is installed with a 
lithium based grease smear on the surface.
11.If the project is not configured with the lift, cancel the lift beam, lift support, 
lift guardrail, and its accessory components and solder joints, the lift through the platform hole to increase
the cover blocking. the corresponding inner accessories shall be reduced by XXXkg."
)
(setq 3_EN__5S_TechReq_detail
"11.If the project is not configured with the lift, cancel the lift beam, lift support, 
lift guardrail, and its accessory components and solder joints, the lift through the platform hole to increase
the cover blocking. the corresponding inner accessories shall be reduced by XXXkg."
)

(setq znq_2_En_5S_TechReq_detail "Their weights are shown in its formal drawing.
6.All requirements of aluminium alloy ladder in tower must be certificated by CE and  approved by Goldwind.
7.Parameters of the lift should comply with the local power grid requirements.
8.The girth welds of tower cylinder without notes shall be DC90.
9.The welding toes of internals ,connected to tower shell or traditional doorframe should be  treated by 
Ultrasonic Impact Treatement(UIT).More detailed requirments should be conform to the “EN Q/GW 204021
 Technical Requirements on Ultrasonic Impact Treatment for Wind Turbine”.
10.The tower drum wall %%C16 spoiler hole is blocked with plug, and the plug is installed with a 
lithium based grease smear on the surface.
11.The tower damper and auxiliary module should meet the requirements of 
\"GW-00CG-0548 Order technical Specifications for tower liquid damper and auxiliary module\". 
12.If the project is not configured with the lift, cancel the lift beam, lift support, 
lift guardrail, and its accessory components and solder joints, the lift through the platform hole to increase
the cover blocking. the corresponding inner accessories shall be reduced by XXXkg."
)
(setq znq_3_EN_5S_TechReq_detail
"12.If the project is not configured with the lift, cancel the lift beam, lift support, 
lift guardrail, and its accessory components and solder joints, the lift through the platform hole to increase
the cover blocking. the corresponding inner accessories shall be reduced by XXXkg."
)

;V12分片塔
(setq 1_V12_Slice_TechReq_detail "    
             
1、塔体制造按照Q/GW202002《风力发电机组 塔架技术规范》最新版执行；
2、塔架防腐按照Q/GW201175《陆上风力发电机组 塔架通用防腐技术规范》最新版执行；
3、塔架法兰按照Q/GW203041《风力发电机组 塔架整锻法兰技术条件》最新版执行；
4、塔架紧固件按照Q/GW203008《风力发电机组通用技术规范 紧固件》最新版执行；
5、根据项目现场结构工作温度（最低气温T），各材料选取原则如下表："
)

(setq 2_V12_Slice_TechReq_detail
"6、塔架内铝合金爬梯要求有CE认证并取得金风科技认可；
7、塔筒内升降机的参数应符合当地电网的要求；
8、未注主体筒节环焊缝按照DC90执行；
9、与筒壁、传统门框焊接的附件（参考具体详图），其焊趾处采用超声波冲击工艺处理，
具体要求应符合Q/GW204021《风力发电机组 超声波冲击工艺技术要求》；
10、塔筒壁%%C16扰流条孔用堵头封堵，安装时堵头配合表面涂抹锂基脂。
11、短尾铆钉及套环均由塔架厂采购，厂家及型号：眉山中车 MTD-T20-50D,LMDTS-T20GA；
12、塔架附件应在塔筒厂安装，并随塔筒一起发运；
13、短尾铆钉张拉工具由塔筒厂提供，张拉工具供应商于短尾铆钉供应商相同；
14、分片段塔架运输支架由塔架厂家根据塔架分片段数以及项目需求生产， 
运输支架图号：80.08.XXXXX（直段装配5950-5950）80.08.XXXXX（锥段装配5950-XXXX）；
15、分片塔附件采用预装方式，打包方式按《风力发电机组 分片式塔架 分片段打包技术条件》；
16、如项目不配置升降机，则取消升降机吊梁及其附属构件与焊点、升降机护栏、升降机
平台过孔增加盖板封堵，各平台需参考免爬器方案图纸进行改造，对应内附件减重XXXkg。"
)
(setq 3_V12_Slice_TechReq_detail
"16、如项目不配置升降机，则取消升降机吊梁及其附属构件与焊点、升降机护栏、升降机
平台过孔增加盖板封堵，各平台需参考免爬器方案图纸进行改造，对应内附件减重XXXkg。"
)

(setq znq_2_V12_Slice_TechReq_detail
"6、塔架内铝合金爬梯要求有CE认证并取得金风科技认可；
7、塔筒内升降机的参数应符合当地电网的要求；
8、未注主体筒节环焊缝按照DC90执行；
9、与筒壁、传统门框焊接的附件（参考具体详图），其焊趾处采用超声波冲击工艺处理，
具体要求应符合Q/GW204021《风力发电机组 超声波冲击工艺技术要求》；
10、塔筒壁%%C16扰流条孔用堵头封堵，安装时堵头配合表面涂抹锂基脂。
11、短尾铆钉及套环均由塔架厂采购，厂家及型号：眉山中车 MTD-T20-50D,LMDTS-T20GA；
12、塔架附件应在塔筒厂安装，并随塔筒一起发运；
13、短尾铆钉张拉工具由塔筒厂提供，张拉工具供应商于短尾铆钉供应商相同；
14、分片段塔架运输支架由塔架厂家根据塔架分片段数以及项目需求生产， 
运输支架图号：80.08.XXXXX（直段装配5950-5950）80.08.XXXXX（锥段装配5950-XXXX）；
15、分片塔附件采用预装方式，打包方式按《风力发电机组 分片式塔架 分片段打包技术条件》；
16、阻尼器及辅材模块应满足《GW-00CG.0548 阻尼器及辅材模块-液体阻尼器订货技术文件》要求；
17、如项目不配置升降机，则取消升降机吊梁及其附属构件与焊点、升降机护栏、升降机
平台过孔增加盖板封堵，各平台需参考免爬器方案图纸进行改造，对应内附件减重XXXkg。"
)
(setq znq_3_V12_Slice_TechReq_detail
"17、如项目不配置升降机，则取消升降机吊梁及其附属构件与焊点、升降机护栏、升降机
平台过孔增加盖板封堵，各平台需参考免爬器方案图纸进行改造，对应内附件减重XXXkg。"
)
(setq En_V12_Slice_TechReq_detail " 
               
1.The requirements of manufacturing should be executed under the lastest version of
\"EN Q/GW 202005 Technical Specifications for Tower of Wind Turbine Generator System(overseas manufacturing)\".
2.The requirements of corrosion protection should be executed under the lastest version of
\"EN Q/GW 201177 Goldwind Onshore Wind Turbine Technical Specifications for Corrosion Protection of 
Towers (For Towers Manufactured Overseas)\".
3.The requirements of flange should be executed under the lastest version of 
 \"EN Q/GW 203050 Goldwind Wind Turbine Technical Specifications for Integrally Forged 
 Tower Flange (Overseas Manufacturing）\".
4.The requirements of fasteners should be executed under the lastest version of
\"EN Q/GW 203008 General technical specifications for wind turbine – fasteners\".
5.Generally, The selection principle of material is as follows:"
)

(setq 2_En_V12_Slice_TechReq_detail "Their weights are shown in its formal drawing.
6.All requirements of aluminium alloy ladder in tower must be certificated by CE and  approved by Goldwind.
7.Parameters of the lift should comply with the local power grid requirements.
8.The girth welds of tower cylinder without notes shall be DC90.
9.The welding toes of internals ,connected to tower shell or traditional doorframe should be  treated by 
Ultrasonic Impact Treatement(UIT).More detailed requirments should be conform to the “EN Q/GW 204021 
Technical Requirements on Ultrasonic Impact Treatment for Wind Turbine”.
10.The tower drum wall %%C16 spoiler hole is blocked with plug, and the plug is installed with a 
lithium based grease smear on the surface.
11.Bobtail and collars shall be purchased by tower manufacturer. Bobtail manufacturer and model:
 Meishan CRRC MTD-T20-50D, LMDTS-T20GA.
12.Tower accessories shall be install at the tower factory and transported with the tower.
13.Bobtail tensioning tools shall be provided by the tower factory and the tensioning tool supplier 
shall be the same as the bobtail supplier
14. The segmented tower transportation bracket is produced by the tower manufacturer based on the number of 
tower segments and project requirements, Transportation bracket drawing number: 
80.08.XXXXX (straight section assembly 5950-5950) 80.08.XXXXX (cone section assembly 5950-XXXX).
15.The  accessories of the segmented tower need to be assembled in the tower factory，And the 
technical requirements should be under the latest version of“ Goldwind Wind Turbine Packaging 
Technical Specifications for Segmented Tower Section”.
16.If the project is not configured with the lift, cancel the lift beam, lift support, 
lift guardrail, and its accessory components and solder joints, the lift through the platform hole to increase
the cover blocking. the corresponding inner accessories shall be reduced by XXXkg."
)
(setq 3_EN_V12_Slice_TechReq_detail
"16.If the project is not configured with the lift, cancel the lift beam, lift support, 
lift guardrail, and its accessory components and solder joints, the lift through the platform hole to increase
the cover blocking. the corresponding inner accessories shall be reduced by XXXkg."
)

(setq znq_2_En_V12_Slice_TechReq_detail "Their weights are shown in its formal drawing.
6.All requirements of aluminium alloy ladder in tower must be certificated by CE and  approved by Goldwind.
7.Parameters of the lift should comply with the local power grid requirements.
8.The girth welds of tower cylinder without notes shall be DC90.
9.The welding toes of internals ,connected to tower shell or traditional doorframe should be  treated by 
Ultrasonic Impact Treatement(UIT).More detailed requirments should be conform to the “EN Q/GW 204021 
Technical Requirements on Ultrasonic Impact Treatment for Wind Turbine”.
10.The tower drum wall %%C16 spoiler hole is blocked with plug, and the plug is installed with a 
lithium based grease smear on the surface.
11.Bobtail and collars shall be purchased by tower manufacturer. Bobtail manufacturer and model:
 Meishan CRRC MTD-T20-50D, LMDTS-T20GA.
12.Tower accessories shall be install at the tower factory and transported with the tower.
13.Bobtail tensioning tools shall be provided by the tower factory and the tensioning tool supplier 
shall be the same as the bobtail supplier
14. The segmented tower transportation bracket is produced by the tower manufacturer based on the number of 
tower segments and project requirements, Transportation bracket drawing number: 
80.08.XXXXX (straight section assembly 5950-5950) 80.08.XXXXX (cone section assembly 5950-XXXX).
15.The  accessories of the segmented tower need to be assembled in the tower factory，And the 
technical requirements should be under the latest version of“ Goldwind Wind Turbine Packaging 
Technical Specifications for Segmented Tower Section”.
16.The tower damper and auxiliary module should meet the requirements of 
\"GW-00CG-0548 Order technical Specifications for tower liquid damper and auxiliary module\".
17.If the project is not configured with the lift, cancel the lift beam, lift support, 
lift guardrail, and its accessory components and solder joints, the lift through the platform hole to increase
the cover blocking. the corresponding inner accessories shall be reduced by XXXkg."
)
(setq znq_3_EN_V12_Slice_TechReq_detail
"17.If the project is not configured with the lift, cancel the lift beam, lift support, 
lift guardrail, and its accessory components and solder joints, the lift through the platform hole to increase
the cover blocking. the corresponding inner accessories shall be reduced by XXXkg."
)

;21# 2.X

(setq 1_2.x_TechReq_detail "           
       
1、塔体制造按照Q/GW202002《风力发电机组 塔架技术规范》最新版执行；
2、塔架防腐按照Q/GW201175《陆上风力发电机组 塔架通用防腐技术规范》最新版执行；
3、塔架法兰按照Q/GW203041《风力发电机组 塔架整锻法兰技术条件》最新版执行；
4、塔架紧固件按照Q/GW203008《风力发电机组通用技术规范 紧固件》最新版执行；
5、根据项目现场结构工作温度（最低气温T），各材料选取原则如下表："
)

(setq 2_2.x_TechReq_detail
"6、塔架内铝合金爬梯要求有CE认证并取得金风科技认可；
7、塔筒内升降机的参数应符合当地电网的要求；
8、未注主体筒节环焊缝按照DC90执行；
9、与筒壁、传统门框焊接的附件（参考具体详图），其焊趾处采用超声波冲击工艺处理，
具体要求应符合Q/GW204021《风力发电机组 超声波冲击工艺技术要求》；
10、塔筒壁%%C16扰流条孔用堵头封堵，安装时堵头配合表面涂抹锂基脂。"
)

(setq znq_2_2.x_TechReq_detail
"6、塔架内铝合金爬梯要求有CE认证并取得金风科技认可；
7、塔筒内升降机的参数应符合当地电网的要求；
8、未注主体筒节环焊缝按照DC90执行；
9、与筒壁、传统门框焊接的附件（参考具体详图），其焊趾处采用超声波冲击工艺处理，
具体要求应符合Q/GW204021《风力发电机组 超声波冲击工艺技术要求》；
10、塔筒壁%%C16扰流条孔用堵头封堵，安装时堵头配合表面涂抹锂基脂。
16、阻尼器及辅材模块应满足《GW-00CG.0548 阻尼器及辅材模块-液体阻尼器订货技术文件》要求。"
)

(setq znq_2_21_TechReq_detail
"6、塔架内铝合金爬梯要求有CE认证并取得金风科技认可；
7、塔筒内升降机的参数应符合当地电网的要求；
8、未注主体筒节环焊缝按照DC90执行；
9、与筒壁、传统门框焊接的附件（参考具体详图），其焊趾处采用超声波冲击工艺处理，
具体要求应符合Q/GW204021《风力发电机组 超声波冲击工艺技术要求》；
10、塔筒壁%%C16扰流条孔用堵头封堵，安装时堵头配合表面涂抹锂基脂。
16、阻尼器及辅材模块应满足《GW-00CG.0548 阻尼器及辅材模块-液体阻尼器订货技术文件》要求。"
)

(setq En_2.x_TechReq_detail "             
    
1.The requirements of manufacturing should be executed under the lastest version of
\"EN Q/GW 202005 Technical Specifications for Tower of Wind Turbine Generator System(overseas manufacturing)\".
2.The requirements of corrosion protection should be executed under the lastest version of
\"EN Q/GW 201177 Goldwind Onshore Wind Turbine Technical Specifications for Corrosion Protection of 
Towers (For Towers Manufactured Overseas)\".
3.The requirements of flange should be executed under the lastest version of 
 \"EN Q/GW 203050 Goldwind Wind Turbine Technical Specifications for Integrally Forged 
 Tower Flange (Overseas Manufacturing）\".
4.The requirements of fasteners should be executed under the lastest version of
\"EN Q/GW 203008 General technical specifications for wind turbine – fasteners\".
5.Generally, The selection principle of material is as follows:"
)

(setq 2_En_2.x_TechReq_detail "Their weights are shown in its formal drawing.
6.All requirements of aluminium alloy ladder in tower must be certificated by CE and  approved by Goldwind.
7.Parameters of the lift should comply with the local power grid requirements.
8.The girth welds of tower cylinder without notes shall be DC90.
9.The welding toes of internals ,connected to tower shell or traditional doorframe should be  treated by 
Ultrasonic Impact Treatement(UIT).More detailed requirments should be conform to the “EN Q/GW 204021
 Technical Requirements on Ultrasonic Impact Treatment for Wind Turbine”.
10.The tower drum wall %%C16 spoiler hole is blocked with plug, and the plug is installed with a 
lithium based grease smear on the surface."
)

(setq znq_2_En_2.x_TechReq_detail "Their weights are shown in its formal drawing.
6.All requirements of aluminium alloy ladder in tower must be certificated by CE and  approved by Goldwind.
7.Parameters of the lift should comply with the local power grid requirements.
8.The girth welds of tower cylinder without notes shall be DC90.
9.The welding toes of internals ,connected to tower shell or traditional doorframe should be  treated by 
Ultrasonic Impact Treatement(UIT).More detailed requirments should be conform to the “EN Q/GW 204021
 Technical Requirements on Ultrasonic Impact Treatment for Wind Turbine”.
10.The tower drum wall %%C16 spoiler hole is blocked with plug, and the plug is installed with a 
lithium based grease smear on the surface.
11.The tower damper and auxiliary module should meet the requirements of 
\"GW-00CG-0548 Order technical Specifications for tower liquid damper and auxiliary module\". "
)

(setq znq_2_En_21_TechReq_detail "Their weights are shown in its formal drawing.
6.All requirements of aluminium alloy ladder in tower must be certificated by CE and  approved by Goldwind.
7.Parameters of the lift should comply with the local power grid requirements.
8.The girth welds of tower cylinder without notes shall be DC90.
9.The welding toes of internals ,connected to tower shell or traditional doorframe should be  treated by 
Ultrasonic Impact Treatement(UIT).More detailed requirments should be conform to the “EN Q/GW 204021
 Technical Requirements on Ultrasonic Impact Treatment for Wind Turbine”.
10.The tower drum wall %%C16 spoiler hole is blocked with plug, and the plug is installed with a 
lithium based grease smear on the surface.
11.The tower damper and auxiliary module should meet the requirements of 
\"GW-00CG-0548 Order technical Specifications for tower liquid damper and auxiliary module\". "
)


;3s
(setq 1_3S_TechReq_detail "           
       
1、塔体制造按照《风力发电机组 塔架技术条件》最新版执行；
2、塔架防腐按照《陆上风力发电机组 塔架通用防腐技术规范》最新版执行；
3、塔架法兰按照《风力发电机组 塔架整锻法兰技术条件》最新版执行；
4、塔架紧固件按照《风力发电机组通用技术规范 紧固件》最新版执行；
5、根据项目现场结构工作温度（最低气温T），各材料选取原则如下表："
)

(setq 2_3S_TechReq_detail
"6、塔架内铝合金爬梯要求有CE认证并取得金风科技认可；
7、塔筒内升降机的参数应符合当地电网的要求；
8、未注主体筒节环焊缝按照DC90执行；
9、与筒壁、传统门框焊接的附件（参考具体详图），其焊趾处采用超声波冲击工艺处理，
具体要求应符合Q/GW204021《风力发电机组 超声波冲击工艺技术要求》；
10、塔筒壁%%C16扰流条孔用堵头封堵，安装时堵头配合表面涂抹锂基脂。"
)

(setq znq_2_3S_TechReq_detail
"6、塔架内铝合金爬梯要求有CE认证并取得金风科技认可；
7、塔筒内升降机的参数应符合当地电网的要求；
8、未注主体筒节环焊缝按照DC90执行；
9、与筒壁、传统门框焊接的附件（参考具体详图），其焊趾处采用超声波冲击工艺处理，
具体要求应符合Q/GW204021《风力发电机组 超声波冲击工艺技术要求》；
10、塔筒壁%%C16扰流条孔用堵头封堵，安装时堵头配合表面涂抹锂基脂；
16、阻尼器及辅材模块应满足《GW-00CG.0548 阻尼器及辅材模块-液体阻尼器订货技术文件》要求。"
)

(setq En_3S_TechReq_detail "            
     
1.The requirements of manufacturing should be executed under the lastest version of
\"EN Q/GW 202005 Technical Specifications for Tower of Wind Turbine Generator System(overseas manufacturing)\".
2.The requirements of corrosion protection should be executed under the lastest version of
\"EN Q/GW 201177 Goldwind Onshore Wind Turbine Technical Specifications for Corrosion Protection of 
Towers (For Towers Manufactured Overseas)\".
3.The requirements of flange should be executed under the lastest version of 
 \"EN Q/GW 203050 Goldwind Wind Turbine Technical Specifications for Integrally Forged 
 Tower Flange (Overseas Manufacturing）\".
4.The requirements of fasteners should be executed under the lastest version of
\"EN Q/GW 203008 General technical specifications for wind turbine – fasteners\".
5.Generally, The selection principle of material is as follows:"
)

(setq 2_En_3S_TechReq_detail
"6.All requirements of aluminium alloy ladder in tower must be certificated by CE and  approved by Goldwind.
7.Parameters of the lift should comply with the local power grid requirements.
8.The girth welds of tower cylinder without notes shall be DC90.
9.The welding toes of internals ,connected to tower shell or traditional doorframe should be  treated by 
Ultrasonic Impact Treatement(UIT).More detailed requirments should be conform to the “EN Q/GW 204021
 Technical Requirements on Ultrasonic Impact Treatment for Wind Turbine”.
10.The tower drum wall %%C16 spoiler hole is blocked with plug, and the plug is installed with a 
lithium based grease smear on the surface.
11.The tower damper and auxiliary module should meet the requirements of 
\"GW-00CG-0548 Order technical Specifications for tower liquid damper and auxiliary module\". "
)

(setq znq_2_En_3S_TechReq_detail
"2. Junctions with different wall thickness must be manufactured smoothly and the slope transition is no more than 1:4.
3. All requirements of aluminium alloy ladder in tower must be Certificated by authoritative third party recognized by Goldwind.
4. Generally, material of accessories is S235JR, its weight show in its formal drawing.
5. L type flanges and T type flanges must be forged as one piece.
6. Notch Detail category according to EN 1993-1-9. The design of connection not indicated in the drawing has to satisfy notch detail category
90 or better.The weld preparation has to be done according to this notch detail category. Welding top flange to shell has to satisfy notch
detail category 112,the welding & the undercut have to be ground flat to the surface of the shell, according to EN 22553 Table 3.
7. The drawing shows in millimeter in length and kilogram in weight.
8. The Lift as well as its appendix is supplied by Lift manufactuer. parameters of the lift should comply with the local power grid requirements.
9. The requirements should be executed under the latest version of \"Technical Specifications for Tower of Wind Turbine Generator System
(Overseas manufacturing)\".
10.The welding toes of internals ,connected to tower shell or traditional doorframe should be treated by Ultrasonic Impact Treatement(UIT).
More detailed requirments should be conform to the EN Q/GW 204021 Technical Requirements on Ultrasonic Impact Treatment for Wind Turbine.
11.The tower welded abutting shall be adopted center alignment.
12.The girth welds of tower cylinder without notes shall be DC90.
13.The tower liquid damper should meet the requirements of \"Technical Specifications for tower-mid damper working fluid\".
14.The thick plate welding shall comply with 3.5.5.1 of Q/GW202005-2022 \"Goldwind Wind Turbine Technical Specifications for
Tower (Overseas Manufacturing)\".
15.The girth weld grinding of tower cylinder（sections） should be executed under the lastest version of
\"Goldwind Wind Turbine supplement Technical Specifications for Tower surplus height（excess weld metal）of weld\".
16. For Q390 and above high strength steel, the steel plant shall provide complete weldability data, guiding welding process,
Hot working and heat treatment process parameters, welding joint performance data of corresponding steel.
17. The performance of tower body material steel plate shall at least comply requirement of similar products of qualified suppliers of 
Goldwind steel plate, and the performance shall meet or exceed the common performance."
)

);defun

;MtextInsert

(defun MtextInsert(pt1 text_height text / TRMtext pt_return)
  (setq TRMtext (vla-AddMText myms (vlax-3d-point pt1) 50000 text ) )
  (vla-put-Height TRMtext (* mscale text_height));文字高度，文字大小，中文5，英文3.5
  (vla-put-Layer TRMtext "6文字层")
  (vla-GetBoundingBox TRMtext 'pt_return 'pty);得到左下角和右上角的值
  (setq pt_return (vlax-safearray->list  pt_return))
);defun

;;;;;;材料表说明;;;加强板、门框、板材Q355/Q420说明；；；；

(defun Reinforcing_plates_Q355 (pt1 text_height / row_max col_max TextTable i j pt_return);插入表格,2.x和21号工程,中文（加强板材料表）
  (setq row_max 7
	col_max 7
  )

  (setq TextTable (vla-AddTable myms (vlax-3d-point pt1) row_max col_max 1000 4000 ) );(InsertionPoint, NumRows, NumColumns, RowHeight, ColWidth) 
  ;标题
  (vla-setText TextTable 0 0 "材料选取原则")
  ;第一行表格合并,材料
  (vla-MergeCells TextTable 1 2 0 0);合并表格(minRow, maxRow, minCol, maxCol)
  (vla-MergeCells TextTable 1 2 6 6)
  (vla-MergeCells TextTable 1 1 1 5)
  (vla-MergeCells TextTable 2 2 1 2)
  (vla-MergeCells TextTable 2 2 3 4)
  (vla-setText TextTable 1 0 "材料")
  (vla-setText TextTable 1 1 "温度")
  (vla-setText TextTable 1 6 "备注")
  (vla-setText TextTable 2 1 "T＞-20°C")
  (vla-setText TextTable 2 3 "-40°C＜T≤-20°C")
  (vla-setText TextTable 2 5 "T≤-40°C")
  ;第3、4行表格合并,筒体(含基础环)
  (vla-MergeCells TextTable 3 4 0 0);合并表格(minRow, maxRow, minCol, maxCol)
  ;(vla-MergeCells TextTable 3 4 3 3)
  (vla-MergeCells TextTable 3 4 5 5)
  (vla-MergeCells TextTable 3 4 6 6)
  (vla-setText TextTable 3 0 "筒体")
  (vla-setText TextTable 3 1 "板厚＜40mm")
  (vla-setText TextTable 3 2 "板厚≥40mm")
  (vla-setText TextTable 4 1 "Q355C")
  (vla-setText TextTable 4 2 "Q355D")
  (vla-setText TextTable 3 3 "板厚＜35mm")
  (vla-setText TextTable 3 4 "板厚≥35mm")
  (vla-setText TextTable 4 3 "Q355D")
  (vla-setText TextTable 4 4 "Q355ND")
  (vla-setText TextTable 3 5 "Q355NE")
  (vla-setText TextTable 3 6 "ND、NE交货状\n态为正火轧制")
  ;第5行表格合并,锻造法兰
  (vla-MergeCells TextTable 5 5 1 5);合并表格(minRow, maxRow, minCol, maxCol)
  (vla-setText TextTable 5 0 "锻造法兰")
  (vla-setText TextTable 5 1 "Q355NEZ35")
  (vla-setText TextTable 5 6 "正火/(正火+回火)")
  ;第6行表格合并,加强板
  (vla-MergeCells TextTable 6 6 1 5);合并表格(minRow, maxRow, minCol, maxCol)
  (vla-setText TextTable 6 0 "加强板")
  (vla-setText TextTable 6 1 "Q355NE")
  (vla-setText TextTable 6 6 "NE交货状态为\n正火或正火轧制")
  ; ;第7行表格合并,锻造法兰
  ; (vla-MergeCells TextTable 7 7 1 4);合并表格(minRow, maxRow, minCol, maxCol)
  ; (vla-setText TextTable 7 0 "拼接法兰（如果有）")
  ; (vla-setText TextTable 7 1 "Q355NEZ35")
  ; (vla-setText TextTable 7 2 "Q355NEZ35")
  ; (vla-setText TextTable 7 5 "正火")
  ;(vla-put-Height TRMtext (* mscale 5.0));文字高度，文字大小，中文5，英文3.5
  (vla-put-Layer TextTable "6文字层")
  ;调整表格内文字的对齐方式，文字大小
  (setq i 0
	j 0)
  (while (< i row_max)
    (while (< j col_max) 
      (vla-setcellalignment TextTable i j 5);(row, col, cellAlignment)
      (vla-setcelltextheight TextTable i j (* mscale text_height))
      (setq j (+ j 1))
    );end while
    (setq j 0)
    (setq i (+ i 1))
  );end while
  (vla-SetColumnWidth TextTable (- col_max 1) (* 29 mscale));Sets the column width for the column at the specified column index in the table.(col, Width)
  (vla-SetColumnWidth TextTable (- col_max 2) (* 25 mscale))
  (vla-SetColumnWidth TextTable (- col_max 3) (* 25 mscale))
  (vla-SetColumnWidth TextTable (- col_max 4) (* 25 mscale))
  (vla-SetColumnWidth TextTable (- col_max 5) (* 25 mscale))
  (vla-SetColumnWidth TextTable (- col_max 6) (* 25 mscale))
  (vla-SetColumnWidth TextTable (- col_max 7) (* 26 mscale))
  ;(vlax-dump-object TextTable 2)
  (vla-GetBoundingBox TextTable 'pt_return 'pty);得到左下角和右上角的值
  (setq pt_return (vlax-safearray->list  pt_return))
);defun

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defun EN_Reinforcing_plates_Q355(pt1 textheight / row_max col_max TextTable i j pt_return);插入英语表格,2.x和21号；英文（加强板材料表）
  (setq row_max 6
	col_max 3
  )
  (setq TextTable (vla-AddTable myms (vlax-3d-point pt1) row_max col_max 1000 5000 ) );(InsertionPoint, NumRows, NumColumns, RowHeight, ColWidth) 
  ;标题
  (vla-setText TextTable 0 0 "Principles for material selection")
  ;第一行表格合并,材料
  (vla-setText TextTable 1 0 "Component")
  (vla-setText TextTable 1 1 "Material (a)")
  (vla-setText TextTable 1 2 "Delivery status")
  ;第3行表格合并,筒体(含基础环)
  (vla-setText TextTable 2 0 "Tower section ")
  (vla-setText TextTable 2 1 "S355J0, S355J2, S355NL, Q355C, Q355D, Q355ND and Q355NE (b)")
  (vla-setText TextTable 2 2 "Normalizing, normalizing rolling, hot rolling")
  ;第4行表格合并,锻造法兰
  (vla-setText TextTable 3 0 "Forged flange")
  (vla-setText TextTable 3 1 "S355NLZ35 and Q355NEZ35")
  (vla-setText TextTable 3 2 "Normalizing/(Normalizing and tempering)")
  ;第5行表格合并,Reinforcing plates
  (vla-setText TextTable 4 0 "Reinforcing plates")
  (vla-setText TextTable 4 1 "S355NL and Q355NE")
  (vla-setText TextTable 4 2 "Normalizing rolling,Normalizing")
  ; ;第6行表格合并,Spliced flange
  ; (vla-setText TextTable 5 0 "Spliced flange")
  ; (vla-setText TextTable 5 1 "S355NLZ35 and Q355NEZ35")
  ; (vla-setText TextTable 5 2 "Normalizing")
  ;第7行表格合并
  (vla-MergeCells TextTable 5 5 0 2);合并表格(minRow, maxRow, minCol, maxCol)
  (vla-setText TextTable 5 0 "(a):Select material according to applicable documents on materials;
  (b):The delivery status of Q355ND and Q355NE is normalizing rolling.")
  (vla-put-Layer TextTable "6文字层")
  ;调整表格内文字的对齐方式，文字大小
  (setq i 0
	j 0)
  (while (< i row_max)
    (while (< j col_max) 
      (vla-setcellalignment TextTable i j 5);(row, col, cellAlignment)
      (vla-setcelltextheight TextTable i j (* mscale textheight))
      (setq j (+ j 1))
    );end while
    (setq j 0)
    (setq i (+ i 1))
  );end while
  (vla-SetColumnWidth TextTable (- col_max 1) (* 57 mscale));Sets the column width for the column at the specified column index in the table.(col, Width)

  (vla-SetColumnWidth TextTable (- col_max 2) (* 57 mscale))
  (vla-SetColumnWidth TextTable 0 (* 57 mscale))
  ;(vlax-dump-object TextTable 2)
  (vla-GetBoundingBox TextTable 'pt_return 'pty);得到左下角和右上角的值
  (setq pt_return (vlax-safearray->list  pt_return))
);defun

(defun Door_frame_Q355 (pt1 text_height / row_max col_max TextTable i j pt_return);插入表格,2.x和21号工程,中文（门框材料表）
  (setq row_max 7
	col_max 7
  )

  (setq TextTable (vla-AddTable myms (vlax-3d-point pt1) row_max col_max 1000 4000 ) );(InsertionPoint, NumRows, NumColumns, RowHeight, ColWidth) 
  ;标题
  (vla-setText TextTable 0 0 "材料选取原则")
  ;第一行表格合并,材料
  (vla-MergeCells TextTable 1 2 0 0);合并表格(minRow, maxRow, minCol, maxCol)
  (vla-MergeCells TextTable 1 2 6 6)
  (vla-MergeCells TextTable 1 1 1 5)
  (vla-MergeCells TextTable 2 2 1 2)
  (vla-MergeCells TextTable 2 2 3 4)
  (vla-setText TextTable 1 0 "材料")
  (vla-setText TextTable 1 1 "温度")
  (vla-setText TextTable 1 6 "备注")
  (vla-setText TextTable 2 1 "T＞-20°C")
  (vla-setText TextTable 2 3 "-40°C＜T≤-20°C")
  (vla-setText TextTable 2 5 "T≤-40°C")
  ;第3、4行表格合并,筒体(含基础环)
  (vla-MergeCells TextTable 3 4 0 0);合并表格(minRow, maxRow, minCol, maxCol)
  ;(vla-MergeCells TextTable 3 4 3 3)
  (vla-MergeCells TextTable 3 4 5 5)
  (vla-MergeCells TextTable 3 4 6 6)
  (vla-setText TextTable 3 0 "筒体")
  (vla-setText TextTable 3 1 "板厚＜40mm")
  (vla-setText TextTable 3 2 "板厚≥40mm")
  (vla-setText TextTable 4 1 "Q355C")
  (vla-setText TextTable 4 2 "Q355D")
  (vla-setText TextTable 3 3 "板厚＜35mm")
  (vla-setText TextTable 3 4 "板厚≥35mm")
  (vla-setText TextTable 4 3 "Q355D")
  (vla-setText TextTable 4 4 "Q355ND")
  (vla-setText TextTable 3 5 "Q355NE")
  (vla-setText TextTable 3 6 "ND、NE交货状\n态为正火轧制")
  ;第5行表格合并,锻造法兰
  (vla-MergeCells TextTable 5 5 1 5);合并表格(minRow, maxRow, minCol, maxCol)
  (vla-setText TextTable 5 0 "锻造法兰")
  (vla-setText TextTable 5 1 "Q355NEZ35")
  (vla-setText TextTable 5 6 "正火/(正火+回火)")
  ;第6行表格合并,加强板
  (vla-MergeCells TextTable 6 6 1 5);合并表格(minRow, maxRow, minCol, maxCol)
  (vla-setText TextTable 6 0 "门框")
  (vla-setText TextTable 6 1 "Q355NEZ35")
  (vla-setText TextTable 6 6 "正火")
  ; ;第7行表格合并,锻造法兰
  ; (vla-MergeCells TextTable 7 7 1 4);合并表格(minRow, maxRow, minCol, maxCol)
  ; (vla-setText TextTable 7 0 "拼接法兰（如果有）")
  ; (vla-setText TextTable 7 1 "Q355NEZ35")
  ; (vla-setText TextTable 7 2 "Q355NEZ35")
  ; (vla-setText TextTable 7 5 "正火")
  ;(vla-put-Height TRMtext (* mscale 5.0));文字高度，文字大小，中文5，英文3.5
  (vla-put-Layer TextTable "6文字层")
  ;调整表格内文字的对齐方式，文字大小
  (setq i 0
	j 0)
  (while (< i row_max)
    (while (< j col_max) 
      (vla-setcellalignment TextTable i j 5);(row, col, cellAlignment)
      (vla-setcelltextheight TextTable i j (* mscale text_height))
      (setq j (+ j 1))
    );end while
    (setq j 0)
    (setq i (+ i 1))
  );end while
  (vla-SetColumnWidth TextTable (- col_max 1) (* 29 mscale));Sets the column width for the column at the specified column index in the table.(col, Width)
  (vla-SetColumnWidth TextTable (- col_max 2) (* 25 mscale))
  (vla-SetColumnWidth TextTable (- col_max 3) (* 25 mscale))
  (vla-SetColumnWidth TextTable (- col_max 4) (* 25 mscale))
  (vla-SetColumnWidth TextTable (- col_max 5) (* 25 mscale))
  (vla-SetColumnWidth TextTable (- col_max 6) (* 25 mscale))
  (vla-SetColumnWidth TextTable (- col_max 7) (* 26 mscale))
  ;(vlax-dump-object TextTable 2)
  (vla-GetBoundingBox TextTable 'pt_return 'pty);得到左下角和右上角的值
  (setq pt_return (vlax-safearray->list  pt_return))
);defun

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defun EN_Door_frame_Q355(pt1 textheight / row_max col_max TextTable i j pt_return);插入英语表格,2.x和21号；中文（门框材料表）
  (setq row_max 6
	col_max 3
  )
  (setq TextTable (vla-AddTable myms (vlax-3d-point pt1) row_max col_max 1000 5000 ) );(InsertionPoint, NumRows, NumColumns, RowHeight, ColWidth) 
  ;标题
  (vla-setText TextTable 0 0 "Principles for material selection")
  ;第一行表格合并,材料
  (vla-setText TextTable 1 0 "Component")
  (vla-setText TextTable 1 1 "Material (a)")
  (vla-setText TextTable 1 2 "Delivery status")
  ;第3行表格合并,筒体(含基础环)
  (vla-setText TextTable 2 0 "Tower section ")
  (vla-setText TextTable 2 1 "S355J0, S355J2, S355NL, Q355C, Q355D, Q355ND and Q355NE (b)")
  (vla-setText TextTable 2 2 "Normalizing, normalizing rolling, hot rolling")
  ;第4行表格合并,锻造法兰
  (vla-setText TextTable 3 0 "Forged flange")
  (vla-setText TextTable 3 1 "S355NLZ35 and Q355NEZ35")
  (vla-setText TextTable 3 2 "Normalizing/(Normalizing and tempering)")
  ;第5行表格合并,Reinforcing plates
  (vla-setText TextTable 4 0 "Door frame")
  (vla-setText TextTable 4 1 "S355NLZ35 and Q355NEZ35")
  (vla-setText TextTable 4 2 "Normalizing")
  ; ;第6行表格合并,Spliced flange
  ; (vla-setText TextTable 5 0 "Spliced flange")
  ; (vla-setText TextTable 5 1 "S355NLZ35 and Q355NEZ35")
  ; (vla-setText TextTable 5 2 "Normalizing")
  ;第7行表格合并
  (vla-MergeCells TextTable 5 5 0 2);合并表格(minRow, maxRow, minCol, maxCol)
  (vla-setText TextTable 5 0 "(a):Select material according to applicable documents on materials;
  (b):The delivery status of Q355ND and Q355NE is normalizing rolling.")
  (vla-put-Layer TextTable "6文字层")
  ;调整表格内文字的对齐方式，文字大小
  (setq i 0
	j 0)
  (while (< i row_max)
    (while (< j col_max) 
      (vla-setcellalignment TextTable i j 5);(row, col, cellAlignment)
      (vla-setcelltextheight TextTable i j (* mscale textheight))
      (setq j (+ j 1))
    );end while
    (setq j 0)
    (setq i (+ i 1))
  );end while
  (vla-SetColumnWidth TextTable (- col_max 1) (* 57 mscale));Sets the column width for the column at the specified column index in the table.(col, Width)

  (vla-SetColumnWidth TextTable (- col_max 2) (* 57 mscale))
  (vla-SetColumnWidth TextTable 0 (* 57 mscale))
  ;(vlax-dump-object TextTable 2)
  (vla-GetBoundingBox TextTable 'pt_return 'pty);得到左下角和右上角的值
  (setq pt_return (vlax-safearray->list  pt_return))
);defun

(defun Reinforcing_plates_Q420 (pt1 text_height / row_max col_max TextTable i j pt_return);插入表格,2.x和21号工程,中文
  (setq row_max 7
	col_max 7
  )

  (setq TextTable (vla-AddTable myms (vlax-3d-point pt1) row_max col_max 1000 4000 ) );(InsertionPoint, NumRows, NumColumns, RowHeight, ColWidth) 
  ;标题
  (vla-setText TextTable 0 0 "材料选取原则")
  ;第一行表格合并,材料
  (vla-MergeCells TextTable 1 2 0 0);合并表格(minRow, maxRow, minCol, maxCol)
  (vla-MergeCells TextTable 1 2 5 6)
  (vla-MergeCells TextTable 1 1 1 4)
  (vla-MergeCells TextTable 2 2 1 2)
  (vla-MergeCells TextTable 2 2 3 4)
  (vla-setText TextTable 1 0 "材料")
  (vla-setText TextTable 1 1 "温度")
  (vla-setText TextTable 1 5 "备注")
  (vla-setText TextTable 2 1 "T＞-20°C")
  (vla-setText TextTable 2 3 "T≤-20°C")
   ;第3、4行表格合并,筒体(含基础环)
  (vla-MergeCells TextTable 3 4 0 0);合并表格(minRow, maxRow, minCol, maxCol)
  (vla-MergeCells TextTable 3 4 3 4)
 
  (vla-MergeCells TextTable 3 4 5 6)
  (vla-setText TextTable 3 0 "筒体")
  (vla-setText TextTable 3 1 "板厚＜40mm")
  (vla-setText TextTable 3 2 "板厚≥40mm")
  (vla-setText TextTable 4 1 "Q420ND/MD")
   (vla-setText TextTable 4 2 "Q420NE/ME")
  (vla-setText TextTable 3 3 "Q420NE/ME")
  (vla-setText TextTable 3 5 "ND、NE交货状态为正火轧制\nMD、ME交货状态为热机械轧制（TMCP）")
  ;第5行表格合并,锻造法兰
  (vla-MergeCells TextTable 5 5 1 4);合并表格(minRow, maxRow, minCol, maxCol)
    (vla-MergeCells TextTable 5 5 5 6)
  (vla-setText TextTable 5 0 "锻造法兰")
  (vla-setText TextTable 5 1 "Q420NEZ35")
  (vla-setText TextTable 5 5 "正火/(正火+回火)")
  ;第6行表格合并,加强板
  (vla-MergeCells TextTable 6 6 1 4);合并表格(minRow, maxRow, minCol, maxCol)
   (vla-MergeCells TextTable 6 6 5 6)
  (vla-setText TextTable 6 0 "加强板")
  (vla-setText TextTable 6 1 "Q420NE/ME")
  (vla-setText TextTable 6 5 "NE交货状态为正火或正火轧制;ME交货状态为热机械轧制（TMCP）")
  ; ;第7行表格合并,锻造法兰
  ; (vla-MergeCells TextTable 7 7 1 4);合并表格(minRow, maxRow, minCol, maxCol)
  ; (vla-setText TextTable 7 0 "拼接法兰（如果有）")
  ; (vla-setText TextTable 7 1 "Q355NEZ35")
  ; (vla-setText TextTable 7 2 "Q355NEZ35")
  ; (vla-setText TextTable 7 5 "正火")
  ;(vla-put-Height TRMtext (* mscale 5.0));文字高度，文字大小，中文5，英文3.5
  (vla-put-Layer TextTable "6文字层")
  ;调整表格内文字的对齐方式，文字大小
  (setq i 0
	j 0)
  (while (< i row_max)
    (while (< j col_max) 
      (vla-setcellalignment TextTable i j 5);(row, col, cellAlignment)
      (vla-setcelltextheight TextTable i j (* mscale text_height))
      (setq j (+ j 1))
    );end while
    (setq j 0)
    (setq i (+ i 1))
  );end while
  (vla-SetColumnWidth TextTable (- col_max 1) (* 29 mscale));Sets the column width for the column at the specified column index in the table.(col, Width)
  (vla-SetColumnWidth TextTable (- col_max 2) (* 25 mscale))
  (vla-SetColumnWidth TextTable (- col_max 3) (* 25 mscale))
  (vla-SetColumnWidth TextTable (- col_max 4) (* 25 mscale))
  (vla-SetColumnWidth TextTable (- col_max 5) (* 25 mscale))
  (vla-SetColumnWidth TextTable (- col_max 6) (* 25 mscale))
  (vla-SetColumnWidth TextTable (- col_max 7) (* 26 mscale))
  ;(vlax-dump-object TextTable 2)
  (vla-GetBoundingBox TextTable 'pt_return 'pty);得到左下角和右上角的值
  (setq pt_return (vlax-safearray->list  pt_return))
);defun

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defun EN_Reinforcing_plates_Q420(pt1 textheight / row_max col_max TextTable i j pt_return);插入英语表格,2.x和21号
  (setq row_max 6
	col_max 3
  )
  (setq TextTable (vla-AddTable myms (vlax-3d-point pt1) row_max col_max 1000 5000 ) );(InsertionPoint, NumRows, NumColumns, RowHeight, ColWidth) 
  ;标题
  (vla-setText TextTable 0 0 "Principles for material selection")
  ;第一行表格合并,材料
  (vla-setText TextTable 1 0 "Component")
  (vla-setText TextTable 1 1 "Material (a)")
  (vla-setText TextTable 1 2 "Delivery status")
  ;第3行表格合并,筒体(含基础环)
  (vla-setText TextTable 2 0 "Tower section")
  (vla-setText TextTable 2 1 "S420N, S420NL, S420M, S420ML, Q420ND or Q420NE (b),Q420MD or Q420ME(c)")
  (vla-setText TextTable 2 2 "Normalizing normalizing rolling hot rolling  Thermo-Mechanical Control Process (TMCP)")
  ;第4行表格合并,锻造法兰
  (vla-setText TextTable 3 0 "Forged flange")
  (vla-setText TextTable 3 1 "S420NLZ35 and Q420NEZ35")
  (vla-setText TextTable 3 2 "Normalizing/(Normalizing and tempering)")
  ;第5行表格合并,Reinforcing plates
  (vla-setText TextTable 4 0 "Reinforcing plates")
  (vla-setText TextTable 4 1 "S420NL and Q420NE\n S420ML and Q420ME")
  (vla-setText TextTable 4 2 "Normalizing rolling,Normalizing\n Thermo-Mechanical Control Process (TMCP)")
  ; ;第6行表格合并,Spliced flange
  ; (vla-setText TextTable 5 0 "Spliced flange")
  ; (vla-setText TextTable 5 1 "S355NLZ35 and Q355NEZ35")
  ; (vla-setText TextTable 5 2 "Normalizing")
  ;第7行表格合并
  (vla-MergeCells TextTable 5 5 0 2);合并表格(minRow, maxRow, minCol, maxCol)
  (vla-setText TextTable 5 0 "(a):Select material according to applicable documents on materials;\n (b):The delivery status of Q420ND or Q420NE is normalizing rolling.\n (c):The delivery status of Q420MD or Q420ME is Thermo-Mechanical Control Process (TMCP).")
  (vla-put-Layer TextTable "6文字层")
  ;调整表格内文字的对齐方式，文字大小
  (setq i 0
	j 0)
  (while (< i row_max)
    (while (< j col_max) 
      (vla-setcellalignment TextTable i j 5);(row, col, cellAlignment)
      (vla-setcelltextheight TextTable i j (* mscale textheight))
      (setq j (+ j 1))
    );end while
    (setq j 0)
    (setq i (+ i 1))
  );end while
  (vla-SetColumnWidth TextTable (- col_max 1) (* 57 mscale));Sets the column width for the column at the specified column index in the table.(col, Width)

  (vla-SetColumnWidth TextTable (- col_max 2) (* 57 mscale))
  (vla-SetColumnWidth TextTable 0 (* 57 mscale))
  ;(vlax-dump-object TextTable 2)
  (vla-GetBoundingBox TextTable 'pt_return 'pty);得到左下角和右上角的值
  (setq pt_return (vlax-safearray->list  pt_return))
);defun

(defun Door_frame_Q420 (pt1 text_height / row_max col_max TextTable i j pt_return);插入表格,2.x和21号工程,中文
  (setq row_max 7
	col_max 7
  )

  (setq TextTable (vla-AddTable myms (vlax-3d-point pt1) row_max col_max 1000 4000 ) );(InsertionPoint, NumRows, NumColumns, RowHeight, ColWidth) 
  ;标题
  (vla-setText TextTable 0 0 "材料选取原则")
  ;第一行表格合并,材料
  (vla-MergeCells TextTable 1 2 0 0);合并表格(minRow, maxRow, minCol, maxCol)
  (vla-MergeCells TextTable 1 2 5 6)
  (vla-MergeCells TextTable 1 1 1 4)
  (vla-MergeCells TextTable 2 2 1 2)
  (vla-MergeCells TextTable 2 2 3 4)
  (vla-setText TextTable 1 0 "材料")
  (vla-setText TextTable 1 1 "温度")
  (vla-setText TextTable 1 5 "备注")
  (vla-setText TextTable 2 1 "T＞-20°C")
  (vla-setText TextTable 2 3 "T≤-20°C")
   ;第3、4行表格合并,筒体(含基础环)
  (vla-MergeCells TextTable 3 4 0 0);合并表格(minRow, maxRow, minCol, maxCol)
  (vla-MergeCells TextTable 3 4 3 4)
 
  (vla-MergeCells TextTable 3 4 5 6)
  (vla-setText TextTable 3 0 "筒体")
  (vla-setText TextTable 3 1 "板厚＜40mm")
  (vla-setText TextTable 3 2 "板厚≥40mm")
  (vla-setText TextTable 4 1 "Q420ND/MD")
   (vla-setText TextTable 4 2 "Q420NE/ME")
  (vla-setText TextTable 3 3 "Q420NE/ME")
  (vla-setText TextTable 3 5 "ND、NE交货状态为正火轧制\nMD、ME交货状态为热机械轧制（TMCP）")
  ;第5行表格合并,锻造法兰
  (vla-MergeCells TextTable 5 5 1 4);合并表格(minRow, maxRow, minCol, maxCol)
    (vla-MergeCells TextTable 5 5 5 6)
  (vla-setText TextTable 5 0 "锻造法兰")
  (vla-setText TextTable 5 1 "Q420NEZ35")
  (vla-setText TextTable 5 5 "正火/(正火+回火)")
  ;第6行表格合并,加强板
  (vla-MergeCells TextTable 6 6 1 4);合并表格(minRow, maxRow, minCol, maxCol)
   (vla-MergeCells TextTable 6 6 5 6)
  (vla-setText TextTable 6 0 "门框")
  (vla-setText TextTable 6 1 "Q420NEZ35")
  (vla-setText TextTable 6 5 "正火")
  ; ;第7行表格合并,锻造法兰
  ; (vla-MergeCells TextTable 7 7 1 4);合并表格(minRow, maxRow, minCol, maxCol)
  ; (vla-setText TextTable 7 0 "拼接法兰（如果有）")
  ; (vla-setText TextTable 7 1 "Q355NEZ35")
  ; (vla-setText TextTable 7 2 "Q355NEZ35")
  ; (vla-setText TextTable 7 5 "正火")
  ;(vla-put-Height TRMtext (* mscale 5.0));文字高度，文字大小，中文5，英文3.5
  (vla-put-Layer TextTable "6文字层")
  ;调整表格内文字的对齐方式，文字大小
  (setq i 0
	j 0)
  (while (< i row_max)
    (while (< j col_max) 
      (vla-setcellalignment TextTable i j 5);(row, col, cellAlignment)
      (vla-setcelltextheight TextTable i j (* mscale text_height))
      (setq j (+ j 1))
    );end while
    (setq j 0)
    (setq i (+ i 1))
  );end while
  (vla-SetColumnWidth TextTable (- col_max 1) (* 29 mscale));Sets the column width for the column at the specified column index in the table.(col, Width)
  (vla-SetColumnWidth TextTable (- col_max 2) (* 25 mscale))
  (vla-SetColumnWidth TextTable (- col_max 3) (* 25 mscale))
  (vla-SetColumnWidth TextTable (- col_max 4) (* 25 mscale))
  (vla-SetColumnWidth TextTable (- col_max 5) (* 25 mscale))
  (vla-SetColumnWidth TextTable (- col_max 6) (* 25 mscale))
  (vla-SetColumnWidth TextTable (- col_max 7) (* 26 mscale))
  ;(vlax-dump-object TextTable 2)
  (vla-GetBoundingBox TextTable 'pt_return 'pty);得到左下角和右上角的值
  (setq pt_return (vlax-safearray->list  pt_return))
);defun

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defun EN_Door_frame_Q420(pt1 textheight / row_max col_max TextTable i j pt_return);插入英语表格,2.x和21号
  (setq row_max 6
	col_max 3
  )
  (setq TextTable (vla-AddTable myms (vlax-3d-point pt1) row_max col_max 1000 5000 ) );(InsertionPoint, NumRows, NumColumns, RowHeight, ColWidth) 
  ;标题
  (vla-setText TextTable 0 0 "Principles for material selection")
  ;第一行表格合并,材料
  (vla-setText TextTable 1 0 "Component")
  (vla-setText TextTable 1 1 "Material (a)")
  (vla-setText TextTable 1 2 "Delivery status")
  ;第3行表格合并,筒体(含基础环)
  (vla-setText TextTable 2 0 "Tower section")
  (vla-setText TextTable 2 1 "S420N, S420NL, S420M, S420ML, Q420ND or Q420NE (b),Q420MD or Q420ME(c)")
  (vla-setText TextTable 2 2 "Normalizing normalizing rolling hot rolling  Thermo-Mechanical Control Process (TMCP)")
  ;第4行表格合并,锻造法兰
  (vla-setText TextTable 3 0 "Forged flange")
  (vla-setText TextTable 3 1 "S420NLZ35 and Q420NEZ35")
  (vla-setText TextTable 3 2 "Normalizing/(Normalizing and tempering)")
  ;第5行表格合并,Reinforcing plates
  (vla-setText TextTable 4 0 "Door frame")
  (vla-setText TextTable 4 1 "S420NLZ35 and Q420NEZ35")
  (vla-setText TextTable 4 2 "Normalizing")
  ; ;第6行表格合并,Spliced flange
  ; (vla-setText TextTable 5 0 "Spliced flange")
  ; (vla-setText TextTable 5 1 "S355NLZ35 and Q355NEZ35")
  ; (vla-setText TextTable 5 2 "Normalizing")
  ;第7行表格合并
  (vla-MergeCells TextTable 5 5 0 2);合并表格(minRow, maxRow, minCol, maxCol)
  (vla-setText TextTable 5 0 "(a):Select material according to applicable documents on materials;\n (b):The delivery status of Q420ND or Q420NE is normalizing rolling.\n (c):The delivery status of Q420MD or Q420ME is Thermo-Mechanical Control Process (TMCP).")
  (vla-put-Layer TextTable "6文字层")
  ;调整表格内文字的对齐方式，文字大小
  (setq i 0
	j 0)
  (while (< i row_max)
    (while (< j col_max) 
      (vla-setcellalignment TextTable i j 5);(row, col, cellAlignment)
      (vla-setcelltextheight TextTable i j (* mscale textheight))
      (setq j (+ j 1))
    );end while
    (setq j 0)
    (setq i (+ i 1))
  );end while
  (vla-SetColumnWidth TextTable (- col_max 1) (* 57 mscale));Sets the column width for the column at the specified column index in the table.(col, Width)

  (vla-SetColumnWidth TextTable (- col_max 2) (* 57 mscale))
  (vla-SetColumnWidth TextTable 0 (* 57 mscale))
  ;(vlax-dump-object TextTable 2)
  (vla-GetBoundingBox TextTable 'pt_return 'pty);得到左下角和右上角的值
  (setq pt_return (vlax-safearray->list  pt_return))
);defun

(defun Reinforcing_plates_Q355_Q420 (pt1 text_height / row_max col_max TextTable i j pt_return);插入表格,2.x和21号工程,中文
  (setq row_max 7
	col_max 8
  )

  (setq TextTable (vla-AddTable myms (vlax-3d-point pt1) row_max col_max 1800 4000 ) );(InsertionPoint, NumRows, NumColumns, RowHeight, ColWidth) 
  ;标题
  (vla-setText TextTable 0 0 "材料选取原则")
  ;第一行表格合并,材料
  (vla-MergeCells TextTable 1 2 0 0);合并表格(minRow, maxRow, minCol, maxCol)
  (vla-MergeCells TextTable 1 2 6 7)
  (vla-MergeCells TextTable 1 1 1 5)
  (vla-MergeCells TextTable 2 2 1 2)
  (vla-MergeCells TextTable 2 2 3 4)
  (vla-setText TextTable 1 0 "材料")
  (vla-setText TextTable 1 1 "温度") 
  (vla-setText TextTable 1 6 "备注")
  (vla-setText TextTable 2 1 "T＞-20°C")
  (vla-setText TextTable 2 3 "-40°C＜T≤-20°C")
  (vla-setText TextTable 2 5 "T≤-40°C")
  ;第3、4行表格合并,筒体(含基础环)
  (vla-MergeCells TextTable 3 4 0 0);合并表格(minRow, maxRow, minCol, maxCol)
  ;(vla-MergeCells TextTable 3 4 3 3)
  (vla-MergeCells TextTable 3 4 5 5)
  (vla-MergeCells TextTable 3 4 6 7)
  (vla-setText TextTable 3 0 "筒体")
  (vla-setText TextTable 3 1 "板厚＜40mm")
  (vla-setText TextTable 3 2 "板厚≥40mm")
  (vla-setText TextTable 4 1 "Q355C\n Q420ND/MD")
  (vla-setText TextTable 4 2 "Q355D\n Q420NE/ME")
  (vla-setText TextTable 3 3 "板厚＜35mm")
  (vla-setText TextTable 3 4 "板厚≥35mm")
  (vla-setText TextTable 4 3 "Q355D\n Q420NE/ME")
  (vla-setText TextTable 4 4 "Q355ND\n Q420NE/ME")
  (vla-setText TextTable 3 5 "Q355NE\n Q420NE/ME")
  (vla-setText TextTable 3 6 "ND、NE交货状态为正火轧制\nMD、ME交货状态为热机械轧制（TMCP）")
  ;第5行表格合并,锻造法兰
  (vla-MergeCells TextTable 5 5 1 5);合并表格(minRow, maxRow, minCol, maxCol)
  (vla-MergeCells TextTable 5 5 6 7)
  (vla-setText TextTable 5 0 "锻造法兰")
  (vla-setText TextTable 5 1 "Q355NEZ35\n Q420NEZ35")
  (vla-setText TextTable 5 6 "正火/(正火+回火)")
  ;第6行表格合并,加强板
  (vla-MergeCells TextTable 6 6 1 5);合并表格(minRow, maxRow, minCol, maxCol)
  (vla-MergeCells TextTable 6 6 6 7)
  (vla-setText TextTable 6 0 "加强板")
  (vla-setText TextTable 6 1 "Q355NE\n Q420NE/ME")
  (vla-setText TextTable 6 6 "NE交货状态为\n正火或正火轧制\nME交货状态为热机械轧制（TMCP）")
  ; ;第7行表格合并,锻造法兰
  ; (vla-MergeCells TextTable 7 7 1 4);合并表格(minRow, maxRow, minCol, maxCol)
  ; (vla-setText TextTable 7 0 "拼接法兰（如果有）")
  ; (vla-setText TextTable 7 1 "Q355NEZ35")
  ; (vla-setText TextTable 7 2 "Q355NEZ35")
  ; (vla-setText TextTable 7 5 "正火")
  ;(vla-put-Height TRMtext (* mscale 5.0));文字高度，文字大小，中文5，英文3.5
  (vla-put-Layer TextTable "6文字层")
  ;调整表格内文字的对齐方式，文字大小
  (setq i 0
	j 0)
  (while (< i row_max)
    (while (< j col_max) 
      (vla-setcellalignment TextTable i j 5);(row, col, cellAlignment)
      (vla-setcelltextheight TextTable i j (* mscale text_height))
      (setq j (+ j 1))
    );end while
    (setq j 0)
    (setq i (+ i 1))
  );end while
  (vla-SetColumnWidth TextTable (- col_max 1) (* 29 mscale));Sets the column width for the column at the specified column index in the table.(col, Width)
  (vla-SetColumnWidth TextTable (- col_max 2) (* 25 mscale))
  (vla-SetColumnWidth TextTable (- col_max 3) (* 25 mscale))
  (vla-SetColumnWidth TextTable (- col_max 4) (* 25 mscale))
  (vla-SetColumnWidth TextTable (- col_max 5) (* 25 mscale))
  (vla-SetColumnWidth TextTable (- col_max 6) (* 25 mscale))
  (vla-SetColumnWidth TextTable (- col_max 7) (* 26 mscale))
  ;(vlax-dump-object TextTable 2)
  (vla-GetBoundingBox TextTable 'pt_return 'pty);得到左下角和右上角的值
  (setq pt_return (vlax-safearray->list  pt_return))
);defun

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defun EN_Reinforcing_plates_Q355_Q420(pt1 textheight / row_max col_max TextTable i j pt_return);插入英语表格,2.x和21号
  (setq row_max 6
	col_max 3
  )
  (setq TextTable (vla-AddTable myms (vlax-3d-point pt1) row_max col_max 1600 5000 ) );(InsertionPoint, NumRows, NumColumns, RowHeight, ColWidth) 
  ;标题
  (vla-setText TextTable 0 0 "Principles for material selection")
  ;第一行表格合并,材料
  (vla-setText TextTable 1 0 "Component")
  (vla-setText TextTable 1 1 "Material (a)")
  (vla-setText TextTable 1 2 "Delivery status")
  ;第3行表格合并,筒体(含基础环)
  (vla-setText TextTable 2 0 "Tower section")
  (vla-setText TextTable 2 1 "S355J0, S355J2, S355NL, Q355C, Q355D, Q355ND and Q355NE (b)S420N, S420NL, S420M, S420ML, Q420ND or Q420NE (b),Q420MD or Q420ME(c)")
  (vla-setText TextTable 2 2 "Normalizing, normalizing rolling, hot rolling Thermo-Mechanical Control Process (TMCP)")
  ;第4行表格合并,锻造法兰
  (vla-setText TextTable 3 0 "Forged flange")
  (vla-setText TextTable 3 1 "S355NLZ35 Q355NEZ35\n S420NLZ35 and Q420NEZ35")
  (vla-setText TextTable 3 2 "Normalizing Normalizing and tempering")
  ;第5行表格合并,Reinforcing plates
  (vla-setText TextTable 4 0 "Reinforcing plates")
  (vla-setText TextTable 4 1 "S355NL Q355NE\n S420NL Q420NE S420ML Q420ME")
  (vla-setText TextTable 4 2 "Normalizing, normalizing rolling, hot rolling,\n Thermo-Mechanical Control Process (TMCP)")
  ; ;第6行表格合并,Spliced flange
  ; (vla-setText TextTable 5 0 "Spliced flange")
  ; (vla-setText TextTable 5 1 "S355NLZ35 and Q355NEZ35")
  ; (vla-setText TextTable 5 2 "Normalizing")
  ;第7行表格合并
  (vla-MergeCells TextTable 5 5 0 2);合并表格(minRow, maxRow, minCol, maxCol)
  (vla-setText TextTable 5 0 "(a):Select material according to applicable documents on materials;
  (b):The delivery status of Q420ND or Q420NE is normalizing rolling.
(c):The delivery status of Q420MD or Q420ME is Thermo-Mechanical Control Process (TMCP).")
  (vla-put-Layer TextTable "6文字层")
  ;调整表格内文字的对齐方式，文字大小
  (setq i 0
	j 0)
  (while (< i row_max)
    (while (< j col_max) 
      (vla-setcellalignment TextTable i j 5);(row, col, cellAlignment)
      (vla-setcelltextheight TextTable i j (* mscale textheight))
      (setq j (+ j 1))
    );end while
    (setq j 0)
    (setq i (+ i 1))
  );end while
  (vla-SetColumnWidth TextTable (- col_max 1) (* 57 mscale));Sets the column width for the column at the specified column index in the table.(col, Width)

  (vla-SetColumnWidth TextTable (- col_max 2) (* 57 mscale))
  (vla-SetColumnWidth TextTable 0 (* 57 mscale))
  ;(vlax-dump-object TextTable 2)
  (vla-GetBoundingBox TextTable 'pt_return 'pty);得到左下角和右上角的值
  (setq pt_return (vlax-safearray->list  pt_return))
);defun

(defun Door_frame_Q355_Q420 (pt1 text_height / row_max col_max TextTable i j pt_return);插入表格,2.x和21号工程,中文
  (setq row_max 7
	col_max 8
  )

  (setq TextTable (vla-AddTable myms (vlax-3d-point pt1) row_max col_max 1800 4000 ) );(InsertionPoint, NumRows, NumColumns, RowHeight, ColWidth) 
  ;标题
  (vla-setText TextTable 0 0 "材料选取原则")
  ;第一行表格合并,材料
  (vla-MergeCells TextTable 1 2 0 0);合并表格(minRow, maxRow, minCol, maxCol)
  (vla-MergeCells TextTable 1 2 6 7)
  (vla-MergeCells TextTable 1 1 1 5)
  (vla-MergeCells TextTable 2 2 1 2)
  (vla-MergeCells TextTable 2 2 3 4)
  (vla-setText TextTable 1 0 "材料")
  (vla-setText TextTable 1 1 "温度") 
  (vla-setText TextTable 1 6 "备注")
  (vla-setText TextTable 2 1 "T＞-20°C")
  (vla-setText TextTable 2 3 "-40°C＜T≤-20°C")
  (vla-setText TextTable 2 5 "T≤-40°C")
  ;第3、4行表格合并,筒体(含基础环)
  (vla-MergeCells TextTable 3 4 0 0);合并表格(minRow, maxRow, minCol, maxCol)
  ;(vla-MergeCells TextTable 3 4 3 3)
  (vla-MergeCells TextTable 3 4 5 5)
  (vla-MergeCells TextTable 3 4 6 7)
  (vla-setText TextTable 3 0 "筒体")
  (vla-setText TextTable 3 1 "板厚＜40mm")
  (vla-setText TextTable 3 2 "板厚≥40mm")
  (vla-setText TextTable 4 1 "Q355C\n Q420ND/MD")
  (vla-setText TextTable 4 2 "Q355D\n Q420NE/ME")
  (vla-setText TextTable 3 3 "板厚＜35mm")
  (vla-setText TextTable 3 4 "板厚≥35mm")
  (vla-setText TextTable 4 3 "Q355D\n Q420NE/ME")
  (vla-setText TextTable 4 4 "Q355ND\n Q420NE/ME")
  (vla-setText TextTable 3 5 "Q355NE\n Q420NE/ME")
  (vla-setText TextTable 3 6 "ND、NE交货状态为正火轧制\nMD、ME交货状态为热机械轧制（TMCP）")
  ;第5行表格合并,锻造法兰
  (vla-MergeCells TextTable 5 5 1 5);合并表格(minRow, maxRow, minCol, maxCol)
  (vla-MergeCells TextTable 5 5 6 7)
  (vla-setText TextTable 5 0 "锻造法兰")
  (vla-setText TextTable 5 1 "Q355NEZ35\n Q420NEZ35")
  (vla-setText TextTable 5 6 "正火/(正火+回火)")
  ;第6行表格合并,加强板
  (vla-MergeCells TextTable 6 6 1 5);合并表格(minRow, maxRow, minCol, maxCol)
  (vla-MergeCells TextTable 6 6 6 7)
  (vla-setText TextTable 6 0 "门框")
  (vla-setText TextTable 6 1 "Q355NEZ35\n Q420NEZ35")
  (vla-setText TextTable 6 6 "正火")
  ; ;第7行表格合并,锻造法兰
  ; (vla-MergeCells TextTable 7 7 1 4);合并表格(minRow, maxRow, minCol, maxCol)
  ; (vla-setText TextTable 7 0 "拼接法兰（如果有）")
  ; (vla-setText TextTable 7 1 "Q355NEZ35")
  ; (vla-setText TextTable 7 2 "Q355NEZ35")
  ; (vla-setText TextTable 7 5 "正火")
  ;(vla-put-Height TRMtext (* mscale 5.0));文字高度，文字大小，中文5，英文3.5
  (vla-put-Layer TextTable "6文字层")
  ;调整表格内文字的对齐方式，文字大小
  (setq i 0
	j 0)
  (while (< i row_max)
    (while (< j col_max) 
      (vla-setcellalignment TextTable i j 5);(row, col, cellAlignment)
      (vla-setcelltextheight TextTable i j (* mscale text_height))
      (setq j (+ j 1))
    );end while
    (setq j 0)
    (setq i (+ i 1))
  );end while
  (vla-SetColumnWidth TextTable (- col_max 1) (* 29 mscale));Sets the column width for the column at the specified column index in the table.(col, Width)
  (vla-SetColumnWidth TextTable (- col_max 2) (* 25 mscale))
  (vla-SetColumnWidth TextTable (- col_max 3) (* 25 mscale))
  (vla-SetColumnWidth TextTable (- col_max 4) (* 25 mscale))
  (vla-SetColumnWidth TextTable (- col_max 5) (* 25 mscale))
  (vla-SetColumnWidth TextTable (- col_max 6) (* 25 mscale))
  (vla-SetColumnWidth TextTable (- col_max 7) (* 26 mscale))
  ;(vlax-dump-object TextTable 2)
  (vla-GetBoundingBox TextTable 'pt_return 'pty);得到左下角和右上角的值
  (setq pt_return (vlax-safearray->list  pt_return))
);defun

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defun EN_Door_frame_Q355_Q420(pt1 textheight / row_max col_max TextTable i j pt_return);插入英语表格,2.x和21号
  (setq row_max 6
	col_max 3
  )
  (setq TextTable (vla-AddTable myms (vlax-3d-point pt1) row_max col_max 1600 5000 ) );(InsertionPoint, NumRows, NumColumns, RowHeight, ColWidth) 
  ;标题
  (vla-setText TextTable 0 0 "Principles for material selection")
  ;第一行表格合并,材料
  (vla-setText TextTable 1 0 "Component")
  (vla-setText TextTable 1 1 "Material (a)")
  (vla-setText TextTable 1 2 "Delivery status")
  ;第3行表格合并,筒体(含基础环)
  (vla-setText TextTable 2 0 "Tower section")
  (vla-setText TextTable 2 1 "S355J0, S355J2, S355NL, Q355C, Q355D, Q355ND and Q355NE (b)S420N, S420NL, S420M, S420ML, Q420ND or Q420NE (b),Q420MD or Q420ME(c)")
  (vla-setText TextTable 2 2 "Normalizing, normalizing rolling, hot rolling Thermo-Mechanical Control Process (TMCP)")
  ;第4行表格合并,锻造法兰
  (vla-setText TextTable 3 0 "Forged flange")
  (vla-setText TextTable 3 1 "S355NLZ35 Q355NEZ35\n S420NLZ35 and Q420NEZ35")
  (vla-setText TextTable 3 2 "Normalizing Normalizing and tempering")
  ;第5行表格合并,Reinforcing plates
  (vla-setText TextTable 4 0 "Door frame")
  (vla-setText TextTable 4 1 "S355NLZ35 Q355NEZ35\n S420NLZ35 and Q420NEZ35")
  (vla-setText TextTable 4 2 "Normalizing")
  ; ;第6行表格合并,Spliced flange
  ; (vla-setText TextTable 5 0 "Spliced flange")
  ; (vla-setText TextTable 5 1 "S355NLZ35 and Q355NEZ35")
  ; (vla-setText TextTable 5 2 "Normalizing")
  ;第7行表格合并
  (vla-MergeCells TextTable 5 5 0 2);合并表格(minRow, maxRow, minCol, maxCol)
  (vla-setText TextTable 5 0 "(a):Select material according to applicable documents on materials;
  (b):The delivery status of Q420ND or Q420NE is normalizing rolling.
(c):The delivery status of Q420MD or Q420ME is Thermo-Mechanical Control Process (TMCP).")
  (vla-put-Layer TextTable "6文字层")
  ;调整表格内文字的对齐方式，文字大小
  (setq i 0
	j 0)
  (while (< i row_max)
    (while (< j col_max) 
      (vla-setcellalignment TextTable i j 5);(row, col, cellAlignment)
      (vla-setcelltextheight TextTable i j (* mscale textheight))
      (setq j (+ j 1))
    );end while
    (setq j 0)
    (setq i (+ i 1))
  );end while
  (vla-SetColumnWidth TextTable (- col_max 1) (* 57 mscale));Sets the column width for the column at the specified column index in the table.(col, Width)

  (vla-SetColumnWidth TextTable (- col_max 2) (* 57 mscale))
  (vla-SetColumnWidth TextTable 0 (* 57 mscale))
  ;(vlax-dump-object TextTable 2)
  (vla-GetBoundingBox TextTable 'pt_return 'pty);得到左下角和右上角的值
  (setq pt_return (vlax-safearray->list  pt_return))
);defun



;3S;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defun 3Stable (pt1 text_height / row_max col_max TextTable i j pt_return);插入表格,3s,中文
  (setq row_max 7
	col_max 7
  )

  (setq TextTable (vla-AddTable myms (vlax-3d-point pt1) row_max col_max 1000 4000 ) );(InsertionPoint, NumRows, NumColumns, RowHeight, ColWidth) 
  ;标题
  (vla-setText TextTable 0 0 "材料选取原则")
  ;第一行表格合并,材料
  (vla-MergeCells TextTable 1 2 0 0);合并表格(minRow, maxRow, minCol, maxCol)
  (vla-MergeCells TextTable 1 2 6 6)
  (vla-MergeCells TextTable 1 1 1 5)
  (vla-MergeCells TextTable 2 2 1 2)
  (vla-MergeCells TextTable 2 2 3 4)
  (vla-setText TextTable 1 0 "材料")
  (vla-setText TextTable 1 1 "温度")
  (vla-setText TextTable 1 6 "备注")
  (vla-setText TextTable 2 1 "T＞-20°C")
  (vla-setText TextTable 2 3 "-40°C＜T≤-20°C")
  (vla-setText TextTable 2 5 "T≤-40°C")
  ;第3、4行表格合并,筒体(含基础环)
  (vla-MergeCells TextTable 3 4 0 0);合并表格(minRow, maxRow, minCol, maxCol)
  ;(vla-MergeCells TextTable 3 4 3 3)
  (vla-MergeCells TextTable 3 4 5 5)
  (vla-MergeCells TextTable 3 4 6 6)
  (vla-setText TextTable 3 0 "筒体")
  (vla-setText TextTable 3 1 "板厚＜40mm")
  (vla-setText TextTable 3 2 "板厚≥40mm")
  (vla-setText TextTable 4 1 "Q355C")
  (vla-setText TextTable 4 2 "Q355D")
  (vla-setText TextTable 3 3 "板厚＜35mm")
  (vla-setText TextTable 3 4 "板厚≥35mm")
  (vla-setText TextTable 4 3 "Q355D")
  (vla-setText TextTable 4 4 "Q355ND")
  (vla-setText TextTable 3 5 "Q355NE")
  (vla-setText TextTable 3 6 "ND、NE交货状\n态为正火轧制")
  ;第5行表格合并,锻造法兰
  (vla-MergeCells TextTable 5 5 1 5);合并表格(minRow, maxRow, minCol, maxCol)
  (vla-setText TextTable 5 0 "锻造法兰")
  (vla-setText TextTable 5 1 "Q355NEZ35")
  (vla-setText TextTable 5 6 "正火/(正火+回火)")
  ;第6行表格合并,门框
  (vla-MergeCells TextTable 6 6 1 5);合并表格(minRow, maxRow, minCol, maxCol)
  (vla-setText TextTable 6 0 "门框")
  (vla-setText TextTable 6 1 "Q355NEZ35")
  (vla-setText TextTable 6 6 "正火")
  ; ;第7行表格合并,拼接法兰
  ; (vla-MergeCells TextTable 7 7 1 4);合并表格(minRow, maxRow, minCol, maxCol)
  ; (vla-setText TextTable 7 0 "拼接法兰（如果有）")
  ; (vla-setText TextTable 7 1 "Q355NEZ35")
  ; (vla-setText TextTable 7 2 "Q355NEZ35")
  ; (vla-setText TextTable 7 5 "正火")
  ;(vla-put-Height TRMtext (* mscale 5.0));文字高度，文字大小，中文5，英文3.5
  (vla-put-Layer TextTable "6文字层")
  ;调整表格内文字的对齐方式，文字大小
  (setq i 0
	j 0)
  (while (< i row_max)
    (while (< j col_max) 
      (vla-setcellalignment TextTable i j 5);(row, col, cellAlignment)
      (vla-setcelltextheight TextTable i j (* mscale text_height))
      (setq j (+ j 1))
    );end while
    (setq j 0)
    (setq i (+ i 1))
  );end while
  (vla-SetColumnWidth TextTable (- col_max 1) (* 29 mscale));Sets the column width for the column at the specified column index in the table.(col, Width)
  (vla-SetColumnWidth TextTable (- col_max 2) (* 25 mscale))
  (vla-SetColumnWidth TextTable (- col_max 3) (* 25 mscale))
  (vla-SetColumnWidth TextTable (- col_max 4) (* 25 mscale))
  (vla-SetColumnWidth TextTable (- col_max 5) (* 25 mscale))
  (vla-SetColumnWidth TextTable (- col_max 6) (* 25 mscale))
  (vla-SetColumnWidth TextTable (- col_max 7) (* 26 mscale))
  
  ;(vlax-dump-object TextTable 2)
  (vla-GetBoundingBox TextTable 'pt_return 'pty);得到左下角和右上角的值
  (setq pt_return (vlax-safearray->list  pt_return))
);defun

(defun EN3Stable(pt1 textheight / row_max col_max TextTable i j pt_return);插入英语表格,3S
  (setq row_max 6
	col_max 3
  )
  (setq TextTable (vla-AddTable myms (vlax-3d-point pt1) row_max col_max 1000 5000 ) );(InsertionPoint, NumRows, NumColumns, RowHeight, ColWidth) 
  ;标题
  (vla-setText TextTable 0 0 "Principles for material selection")
  ;第一行表格合并,材料
  (vla-setText TextTable 1 0 "Component")
  (vla-setText TextTable 1 1 "Material (a)")
  (vla-setText TextTable 1 2 "Delivery status")
  ;第3行表格合并,筒体(含基础环)
  (vla-setText TextTable 2 0 "Tower section")
  (vla-setText TextTable 2 1 "S355J0, S355J2, S355NL, Q355C, Q355D, Q355ND and Q355NE (b)")
  (vla-setText TextTable 2 2 "Normalizing, normalizing rolling, hot rolling")
  ;第4行表格合并,锻造法兰
  (vla-setText TextTable 3 0 "Forged flange")
  (vla-setText TextTable 3 1 "S355NLZ35 and Q355NEZ35")
  (vla-setText TextTable 3 2 "Normalizing/Normalizing and tempering")
  ;第5行表格合并,door frame
  (vla-setText TextTable 4 0 "Door frame")
  (vla-setText TextTable 4 1 "S355NLZ35 and Q355NEZ35")
  (vla-setText TextTable 4 2 "Normalizing")
  ; ;第5行表格合并,Spliced flange
  ; (vla-setText TextTable 5 0 "Spliced flange")
  ; (vla-setText TextTable 5 1 "S355NLZ35 and Q355NEZ35")
  ; (vla-setText TextTable 5 2 "Normalizing")
  ;第7行表格合并
  (vla-MergeCells TextTable 5 5 0 2);合并表格(minRow, maxRow, minCol, maxCol)
  (vla-setText TextTable 5 0 "(a):Select material according to applicable documents on materials;
  (b):The delivery status of Q355ND and Q355NE is normalizing rolling.")
  (vla-put-Layer TextTable "6文字层")
  ;调整表格内文字的对齐方式，文字大小
  (setq i 0
	j 0)
  (while (< i row_max)
    (while (< j col_max) 
      (vla-setcellalignment TextTable i j 5);(row, col, cellAlignment)
      (vla-setcelltextheight TextTable i j (* mscale textheight))
      (setq j (+ j 1))
    );end while
    (setq j 0)
    (setq i (+ i 1))
  );end while
  (vla-SetColumnWidth TextTable (- col_max 1) (* 57 mscale));Sets the column width for the column at the specified column index in the table.(col, Width)
  (vla-SetColumnWidth TextTable (- col_max 2) (* 57 mscale))
  (vla-SetColumnWidth TextTable 0 8000)
  ;(vlax-dump-object TextTable 2)
  (vla-GetBoundingBox TextTable 'pt_return 'pty);得到左下角和右上角的值
  (setq pt_return (vlax-safearray->list  pt_return))
);defun


;以下为根据最低温度及静强度利用率选板材的选取规则
; (defun 21and2.xtable (pt1 text_height / row_max col_max TextTable i j pt_return);插入表格,2.x和21号工程,中文
  ; (setq row_max 5
	; col_max 6
  ; )

  ; (setq TextTable (vla-AddTable myms (vlax-3d-point pt1) row_max col_max 1000 5000 ) );(InsertionPoint, NumRows, NumColumns, RowHeight, ColWidth) 
  ; ;标题
  ; (vla-setText TextTable 0 0 "材料选取原则")
  ; ;第一行表格合并,材料
  ; ;(vla-MergeCells TextTable 1 2 0 0);合并表格(minRow, maxRow, minCol, maxCol)
  ; ;(vla-MergeCells TextTable 1 2 5 5)
  ; ;(vla-MergeCells TextTable 1 2 1 4)
  ; (vla-MergeCells TextTable 1 1 1 4)
  ; ;(vla-MergeCells TextTable 2 2 1 2)
  ; (vla-setText TextTable 1 0 "分类")
  ; (vla-setText TextTable 1 1 "材料")
  ; (vla-setText TextTable 1 5 "备注")
  ; ;(vla-setText TextTable 2 1 "T＞-20°C")
  ; ;(vla-setText TextTable 2 3 "-40°C＜T≤-20°C")
  ; ;(vla-setText TextTable 2 4 "T≤-40°C")
  ; ;第3、4行表格合并,筒体(含基础环)
  ; ;(vla-MergeCells TextTable 3 4 0 0);合并表格(minRow, maxRow, minCol, maxCol)
  ; (vla-MergeCells TextTable 2 2 1 4)
  ; ;(vla-MergeCells TextTable 3 4 3 3)
  ; ;(vla-MergeCells TextTable 3 4 4 4)
  ; ;(vla-MergeCells TextTable 3 4 5 5)
  ; (vla-setText TextTable 2 0 "筒体")
  ; (vla-setText TextTable 2 1 "图示材料为项目最低气温下要求，按以高代低原则可选择同钢级更高质量等级材料，\n从低到高顺序依次为\nQ355系列：Q355C、Q355NC、Q355D、Q355ND、Q355NE、Q355NF\nQ420系列：Q420ND/MD、Q420NE/ME")
  ; ;(vla-setText TextTable 3 2 "钢板厚度≥40mm")
  ; ;(vla-setText TextTable 4 1 "Q355C")
  ; ;(vla-setText TextTable 4 2 "Q355D")
  ; ;(vla-setText TextTable 3 3 "Q355D")
  ; ;(vla-setText TextTable 3 4 "Q355NE")
  ; (vla-setText TextTable 2 5 "N,交货状态为\n正火轧制\nM,交货状态为\n热机械轧制")
  ; ;第5行表格合并,锻造法兰
  ; (vla-MergeCells TextTable 3 3 1 4);合并表格(minRow, maxRow, minCol, maxCol)
  ; (vla-setText TextTable 3 0 "锻造法兰")
  ; (vla-setText TextTable 3 1 "Q355NEZ35")
  ; (vla-setText TextTable 3 5 "正火+回火/正火")
  ; ;第6行表格合并,加强板
  ; (vla-MergeCells TextTable 4 4 1 4);合并表格(minRow, maxRow, minCol, maxCol)
  ; (vla-setText TextTable 4 0 "加强板")
  ; (vla-setText TextTable 4 1 "Q355NE")
  ; (vla-setText TextTable 4 5 "NE交货状态为\n正火或正火轧制")
  ; ; ;第7行表格合并,锻造法兰
  ; ; (vla-MergeCells TextTable 5 5 1 4);合并表格(minRow, maxRow, minCol, maxCol)
  ; ; (vla-setText TextTable 5 0 "拼接法兰（如果有）")
  ; ; (vla-setText TextTable 5 1 "Q355NEZ35")
  ; ; (vla-setText TextTable 5 2 "Q355NEZ35")
  ; ; (vla-setText TextTable 5 5 "正火")
  ; ;(vla-put-Height TRMtext (* mscale 5.0));文字高度，文字大小，中文5，英文3.5
  ; (vla-put-Layer TextTable "6文字层")
  ; ;调整表格内文字的对齐方式，文字大小
  ; (setq i 0
	; j 0)
  ; (while (< i row_max)
    ; (while (< j col_max) 
      ; (vla-setcellalignment TextTable i j 5);(row, col, cellAlignment)
      ; (vla-setcelltextheight TextTable i j (* mscale text_height))
      ; (setq j (+ j 1))
    ; );end while
    ; (setq j 0)
    ; (setq i (+ i 1))
  ; );end while
  ; (vla-SetColumnWidth TextTable (- col_max 1) (* 29 mscale));Sets the column width for the column at the specified column index in the table.(col, Width)
  ; (vla-SetColumnWidth TextTable (- col_max 2) (* 29 mscale))
  ; (vla-SetColumnWidth TextTable (- col_max 3) (* 36 mscale))
  ; (vla-SetColumnWidth TextTable (- col_max 4) (* 36 mscale))
  ; (vla-SetColumnWidth TextTable (- col_max 5) (* 36 mscale))
  ; (vla-SetColumnWidth TextTable (- col_max 6) (* 36 mscale))
  ; ;(vlax-dump-object TextTable 2)
  ; (vla-GetBoundingBox TextTable 'pt_return 'pty);得到左下角和右上角的值
  ; (setq pt_return (vlax-safearray->list  pt_return))
; );defun

; ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
; (defun EN21and2.xtable(pt1 textheight / row_max col_max TextTable i j pt_return);插入英语表格,2.x和21号
  ; (setq row_max 6
	; col_max 5
  ; )
  ; (setq TextTable (vla-AddTable myms (vlax-3d-point pt1) row_max col_max 1000 5000 ) );(InsertionPoint, NumRows, NumColumns, RowHeight, ColWidth) 
  ; ;标题
  ; (vla-setText TextTable 0 0 "Principles for material selection")
  ; ;第一行表格合并,材料
  ; (vla-MergeCells TextTable 1 1 1 3)
  ; (vla-setText TextTable 1 0 "Component")
  ; (vla-setText TextTable 1 1 "Material (a)")
  ; (vla-setText TextTable 1 4 "Delivery status")
  ; ;第3行表格合并,筒体(含基础环)
  ; (vla-MergeCells TextTable 2 2 1 3)
  ; (vla-setText TextTable 2 0 "Tower section")
  ; (vla-setText TextTable 2 1 "The materials shown in the figure are required under the minimum temperature\nof the project. According to the principle of substituting high for low,
  ; materials of higher quality with the same steel grade can be selected.\nFrom lowest to highest:
  ; For S355: S355J0,S355J2,S355NL,Q355C,Q355NC,Q355D,Q355ND,Q355NE and Q355NF\nFor S420: S420M/ML,S420N/NL,Q420ND/MD and Q420NE/ME")
  ; (vla-setText TextTable 2 4 "N-normalizing rolling,\nM-thermomechanical rolling,\nothers-hot rolling")
  ; ;第4行表格合并,锻造法兰
  ; (vla-MergeCells TextTable 3 3 1 3)
  ; (vla-setText TextTable 3 0 "Forged flange")
  ; (vla-setText TextTable 3 1 "S355NLZ35 and Q355NEZ35")
  ; (vla-setText TextTable 3 4 "Normalizing,\nNormalizing and tempering")
  ; ;第5行表格合并,Reinforcing plates
  ; (vla-MergeCells TextTable 4 4 1 3)
  ; (vla-setText TextTable 4 0 "Reinforcing plates")
  ; (vla-setText TextTable 4 1 "S355NL and Q355NE")
  ; (vla-setText TextTable 4 4 "Normalizing rolling,\nNormalizing") thermomechanical rolling
  ; ;第6行表格合并,Spliced flange
  ; ; (vla-setText TextTable 5 0 "Spliced flange")
  ; ; (vla-setText TextTable 5 1 "S355NLZ35 and Q355NEZ35")
  ; ; (vla-setText TextTable 5 2 "Normalizing")
  ; ;第7行表格合并
  ; (vla-MergeCells TextTable 5 5 0 4);合并表格(minRow, maxRow, minCol, maxCol)
  ; (vla-setText TextTable 5 0 "(a):Select material according to applicable documents on materials."
  ; ;(b):The delivery status of Q355NE is normalizing rolling."
  ; )
  ; (vla-put-Layer TextTable "6文字层")
  ; ;调整表格内文字的对齐方式，文字大小
  ; (setq i 0
	; j 0)
  ; (while (< i row_max)
    ; (while (< j col_max) 
      ; (vla-setcellalignment TextTable i j 5);(row, col, cellAlignment)
      ; (vla-setcelltextheight TextTable i j (* mscale textheight))
      ; (setq j (+ j 1))
    ; );end while
    ; (setq j 0)
    ; (setq i (+ i 1))
  ; );end while
  ; (vla-SetColumnWidth TextTable (- col_max 1) (* 38 mscale));Sets the column width for the column at the specified column index in the table.(col, Width)
  ; (vla-SetColumnWidth TextTable (- col_max 2) (* 37 mscale))
  ; (vla-SetColumnWidth TextTable (- col_max 3) (* 37 mscale))
  ; (vla-SetColumnWidth TextTable (- col_max 4) (* 37 mscale))
  ; (vla-SetColumnWidth TextTable (- col_max 5) (* 28 mscale))
  ; ;(vla-SetColumnWidth TextTable 0 (* 57 mscale))
  ; ;(vlax-dump-object TextTable 2)
  ; (vla-GetBoundingBox TextTable 'pt_return 'pty);得到左下角和右上角的值
  ; (setq pt_return (vlax-safearray->list  pt_return))
; );defun

; ;3S;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
; (defun 3Stable (pt1 text_height / row_max col_max TextTable i j pt_return);插入表格,3s,中文
  ; (setq row_max 5
	; col_max 6
  ; )

  ; (setq TextTable (vla-AddTable myms (vlax-3d-point pt1) row_max col_max 1000 5000 ) );(InsertionPoint, NumRows, NumColumns, RowHeight, ColWidth) 
  ; ;标题
  ; (vla-setText TextTable 0 0 "材料选取原则")
  ; ;第一行表格合并,材料
  ; ;(vla-MergeCells TextTable 1 2 0 0);合并表格(minRow, maxRow, minCol, maxCol)
  ; ;(vla-MergeCells TextTable 1 2 5 5)
  ; ;(vla-MergeCells TextTable 1 1 1 4)
  ; (vla-MergeCells TextTable 1 1 1 4)
  ; (vla-setText TextTable 1 0 "分类")
  ; (vla-setText TextTable 1 1 "材料")
  ; (vla-setText TextTable 1 5 "备注")
  ; ;(vla-setText TextTable 2 1 "T＞-20°C")
  ; ;(vla-setText TextTable 2 3 "-40°C＜T≤-20°C")
  ; ;(vla-setText TextTable 2 4 "T≤-40°C")
  ; ;第3、4行表格合并,筒体(含基础环)
  ; ;(vla-MergeCells TextTable 3 4 0 0);合并表格(minRow, maxRow, minCol, maxCol)
  ; (vla-MergeCells TextTable 2 2 1 4)
  ; ;(vla-MergeCells TextTable 3 4 4 4)
  ; ;(vla-MergeCells TextTable 3 4 5 5)
  ; (vla-setText TextTable 2 0 "筒体")
  ; (vla-setText TextTable 2 1 "图示材料为项目最低气温下要求，按以高代低原则可选择同钢级更高质量等级材料，\n从低到高顺序依次为\nQ355系列：Q355C、Q355NC、Q355D、Q355ND、Q355NE、Q355NF\nQ420系列：Q420ND/MD、Q420NE/ME")
  ; ;(vla-setText TextTable 3 1 "钢板厚度＜40mm")
  ; ;(vla-setText TextTable 3 2 "钢板厚度≥40mm")
  ; ;vla-setText TextTable 4 1 "Q355C")
  ; ;(vla-setText TextTable 4 2 "Q355D")
  ; ;(vla-setText TextTable 3 3 "Q355D")
  ; ;(vla-setText TextTable 3 4 "Q355NE")
  ; (vla-setText TextTable 2 5 "N,交货状态为\n正火轧制\nM,交货状态为\n热机械轧制")
  ; ;第5行表格合并,锻造法兰
  ; (vla-MergeCells TextTable 3 3 1 4);合并表格(minRow, maxRow, minCol, maxCol)
  ; (vla-setText TextTable 3 0 "锻造法兰")
  ; (vla-setText TextTable 3 1 "Q355NEZ35")
  ; (vla-setText TextTable 3 5 "正火+回火/正火")
  ; ;第6行表格合并,门框
  ; (vla-MergeCells TextTable 4 4 1 4);合并表格(minRow, maxRow, minCol, maxCol)
  ; (vla-setText TextTable 4 0 "门框")
  ; (vla-setText TextTable 4 1 "Q355NEZ35")
  ; (vla-setText TextTable 4 5 "正火")
  ; ;第7行表格合并,锻造法兰
  ; ; (vla-MergeCells TextTable 7 7 1 4);合并表格(minRow, maxRow, minCol, maxCol)
  ; ; (vla-setText TextTable 7 0 "拼接法兰（如果有）")
  ; ; (vla-setText TextTable 7 1 "Q355NEZ35")
  ; ; (vla-setText TextTable 7 2 "Q355NEZ35")
  ; ; (vla-setText TextTable 7 5 "正火")
  ; ;(vla-put-Height TRMtext (* mscale 5.0));文字高度，文字大小，中文5，英文3.5
  ; (vla-put-Layer TextTable "6文字层")
  ; ;调整表格内文字的对齐方式，文字大小
  ; (setq i 0
	; j 0)
  ; (while (< i row_max)
    ; (while (< j col_max) 
      ; (vla-setcellalignment TextTable i j 5);(row, col, cellAlignment)
      ; (vla-setcelltextheight TextTable i j (* mscale text_height))
      ; (setq j (+ j 1))
    ; );end while
    ; (setq j 0)
    ; (setq i (+ i 1))
  ; );end while
  ; (vla-SetColumnWidth TextTable (- col_max 1) (* 29 mscale));Sets the column width for the column at the specified column index in the table.(col, Width)
  ; (vla-SetColumnWidth TextTable (- col_max 2) (* 29 mscale))
  ; (vla-SetColumnWidth TextTable (- col_max 3) (* 36 mscale))
  ; (vla-SetColumnWidth TextTable (- col_max 4) (* 36 mscale))
  ; (vla-SetColumnWidth TextTable (- col_max 5) (* 36 mscale))
  ; (vla-SetColumnWidth TextTable (- col_max 6) (* 36 mscale))
  
  ; ;(vlax-dump-object TextTable 2)
  ; (vla-GetBoundingBox TextTable 'pt_return 'pty);得到左下角和右上角的值
  ; (setq pt_return (vlax-safearray->list  pt_return))
; );defun

; (defun EN3Stable(pt1 textheight / row_max col_max TextTable i j pt_return);插入英语表格,3S
  ; (setq row_max 7
	; col_max 3
  ; )
  ; (setq TextTable (vla-AddTable myms (vlax-3d-point pt1) row_max col_max 1000 5000 ) );(InsertionPoint, NumRows, NumColumns, RowHeight, ColWidth) 
  ; ;标题
  ; (vla-setText TextTable 0 0 "Principles for material selection")
  ; ;第一行表格合并,材料
  ; (vla-setText TextTable 1 0 "Component")
  ; (vla-setText TextTable 1 1 "Material (a)")
  ; (vla-setText TextTable 1 2 "Delivery status")
  ; ;第3行表格合并,筒体(含基础环)
  ; (vla-setText TextTable 2 0 "Tower section (including foundation ring)")
  ; (vla-setText TextTable 2 1 "The materials shown in the figure are required under the minimum temperature\nof the project. According to the principle of substituting high for low,
  ; materials of higher quality with the same steel grade can be selected.\nFrom lowest to highest:
  ; For S355: S355J0,S355J2,S355NL,Q355C,Q355NC,Q355D,Q355ND,Q355NE and Q355NF\nFor S420: S420M/ML,S420N/NL,Q420ND/MD and Q420NE/ME")
  ; (vla-setText TextTable 2 2 "N-normalizing rolling,\nM-thermomechanical rolling,\nothers-hot rolling")
  ; ;第4行表格合并,锻造法兰
  ; (vla-setText TextTable 3 0 "Forged flange")
  ; (vla-setText TextTable 3 1 "S355NLZ35 and Q355NEZ35")
  ; (vla-setText TextTable 3 2 "Normalizing/Normalizing and tempering")
  ; ;第5行表格合并,door frame
  ; (vla-setText TextTable 4 0 "Door frame")
  ; (vla-setText TextTable 4 1 "S355NLZ35 and Q355NEZ35")
  ; (vla-setText TextTable 4 2 "Normalizing")
  ; ;第5行表格合并,Spliced flange
  ; (vla-setText TextTable 5 0 "Spliced flange")
  ; (vla-setText TextTable 5 1 "S355NLZ35 and Q355NEZ35")
  ; (vla-setText TextTable 5 2 "Normalizing")
  ; ;第7行表格合并
  ; (vla-MergeCells TextTable 6 6 0 2);合并表格(minRow, maxRow, minCol, maxCol)
  ; (vla-setText TextTable 6 0 "(a):Select material according to applicable documents on materials."
  ; ;(b):The delivery status of Q355NE is normalizing rolling."
  ; )
  ; (vla-put-Layer TextTable "6文字层")
  ; ;调整表格内文字的对齐方式，文字大小
  ; (setq i 0
	; j 0)
  ; (while (< i row_max)
    ; (while (< j col_max) 
      ; (vla-setcellalignment TextTable i j 5);(row, col, cellAlignment)
      ; (vla-setcelltextheight TextTable i j (* mscale textheight))
      ; (setq j (+ j 1))
    ; );end while
    ; (setq j 0)
    ; (setq i (+ i 1))
  ; );end while
  ; (vla-SetColumnWidth TextTable (- col_max 1) (* 57 mscale));Sets the column width for the column at the specified column index in the table.(col, Width)
  ; (vla-SetColumnWidth TextTable (- col_max 2) (* 57 mscale))
  ; (vla-SetColumnWidth TextTable 0 8000)
  ; ;(vlax-dump-object TextTable 2)
  ; (vla-GetBoundingBox TextTable 'pt_return 'pty);得到左下角和右上角的值
  ; (setq pt_return (vlax-safearray->list  pt_return))
; );defun

;插入技术条件
(defun TechReq_insert(pt1 text1 text2 text_height langu fltype / pt2 pt3 pt4);TechReq_insert,langu:语言
  (setq pt2 (MtextInsert pt1 text_height text1));第一段

  (setq pt3 (sel_table pt2 text_height langu fltype) );表格
  (MtextInsert pt3 text_height text2);第二段文字
);defun


(defun sel_table (pt2 text_height langu fltype / i );/ i materiai_style collect_list ncollect_list)
	; (setq i 0)
	; (setq collect_num 0)
	; (while (< i towernum)
		; (if (and (/= (value retTower i 5) "")(/= (value retTower i 5) nil));(and (/= (value retTower i 9) "") (/= (value retTower i 9) nil)));如果不为空
			; (progn
				; (if (=(value retTower i 5) "Q420");(=(atof (value retTower i 9)) "Q420"))
					; (setq collect_num (+ collect_num 1))
				; )
			; )
		; )
		; (setq i (+ i 1))
	; )
	; (if (/= collect_num 0)
		; (progn
			; (if (= collect_num towernum)
				; (setq materile_style 1);0指Q355，1指Q420，2指Q355和420
				; (setq materile_style 2);0指Q355，1指Q420，2指Q355和420
			; )
		; )
		; (setq materile_style 0);0指Q355，1指Q420，2指Q355和420
	; )
	
	(cond
		 ((and (= langu "ch") (= materile_style 0)(= (ReplateDoor) T));(or (= fltype "2.xMW_TopFlange") (= topfltype "21_TopFlange") (= topfltype "5S_TopFlange")(= topfltype "V12_TopFlange")(= topfltype "V12_TopFlange_250")(= topfltype "V15_TopFlange")(= topfltype "V15_TopFlange_250"))
			(Reinforcing_plates_Q355 pt2 text_height)
		 );Q355中文表格（加强板）
		 ((and (= langu "en") (= materile_style 0)(= (ReplateDoor) T))
			(EN_Reinforcing_plates_Q355 pt2 text_height)
		 );Q355英文表格（加强板）
		 ((and (= langu "ch") (= materile_style 0)(= (ReplateDoor) nil))
			(Door_frame_Q355 pt2 text_height)
		 );Q355中文表格（门框）
		 ((and (= langu "en") (= materile_style 0)(= (ReplateDoor) nil))
			(EN_Door_frame_Q355 pt2 text_height)
		 );Q355英文表格（门框）
		((and (= langu "ch") (= materile_style 1)(= (ReplateDoor) T))
			(Reinforcing_plates_Q420 pt2 text_height)
		);Q420中文表格（加强板）
		((and (= langu "en") (= materile_style 1)(= (ReplateDoor) T))
			(EN_Reinforcing_plates_Q420 pt2 text_height)
		);Q420英文表格（加强板）
		((and (= langu "ch") (= materile_style 1)(= (ReplateDoor)  nil))
			(Door_frame_Q420 pt2 text_height)
		);Q420中文表格（门框）
		((and (= langu "en") (= materile_style 1)(= (ReplateDoor)  nil))
			(EN_Door_frame_Q420 pt2 text_height)
		);Q420英文表格（门框）
		((and (= langu "ch") (= materile_style 2)(= (ReplateDoor) T))
			(Reinforcing_plates_Q355_Q420 pt2 text_height)
		);Q355Q420中文表格（加强板）
		((and (= langu "en") (= materile_style 2)(= (ReplateDoor) T))
			(EN_Reinforcing_plates_Q355_Q420 pt2 text_height)
		);Q355Q420英文表格（加强板）
		((and (= langu "ch") (= materile_style 2)(= (ReplateDoor)  nil))
			(Door_frame_Q355_Q420 pt2 text_height)
		);Q355Q420中文表格（门框）
		((and (= langu "en") (= materile_style 2)(= (ReplateDoor)  nil))
			(EN_Door_frame_Q355_Q420 pt2 text_height)
		);Q355Q420英文表格（门框）
		
		((and (= langu "ch")  (= fltype "3MW_S_New_TopFlange") )
			(3Stable pt2 text_height)
		);3S中文
		((and (= langu "en")  (= fltype "3MW_S_New_TopFlange"))
			(EN3Stable pt2 text_height)
		);3S英文
		(t
			(if (= langu "ch")
			(3Stable pt2 text_height)
			(EN3Stable pt2 text_height)
			);if
		);t
    
	);cond
)

;21and2.xtable
;EN21and2.xtable
;3Stable
;EN3Stable

(defun sel_table_cable (pt2 text_height /)

  (cond
    ((and (or(= topfltype "V12_TopFlange")(= topfltype "V12_TopFlange_250")) (< Power 6.0))
      (cable1 pt2 text_height)
    );185线夹表格
    ((and (or(= topfltype "V12_TopFlange")(= topfltype "V12_TopFlange_250")) (>= Power 6.0) (< Power 7.5))
      (cable2 pt2 text_height)
    );240线夹表格
    (t
      (cable3 pt2 text_height)
    );待定线夹表格  
  );cond
);end defun

(defun C:cable_table(/ pt1 textheight row_max col_max TextTable i j pt_return);插入185线夹表格
  (setq row_max 3
	col_max 5
	pt1 '(0 0 0)
	textheight 5
  )
  (setq TextTable (vla-AddTable myms (vlax-3d-point pt1) row_max col_max 2000 5000 ) );(InsertionPoint, NumRows, NumColumns, RowHeight, ColWidth) 
  ;标题
  (vla-setText TextTable 0 0 "电缆固定夹规格表\nClamp specification table")
  ;第一行表格填写,名称
  (vla-setText TextTable 1 0 "序号\Serial number")
  (vla-setText TextTable 1 1 "物料编码\Drawing number")
  (vla-setText TextTable 1 2 "名称\Name")
  (vla-setText TextTable 1 3 "对应电缆固定架图号\Cable clamp NO.")
  (vla-setText TextTable 1 4 "数量\quantity")
  ;第二行表格填写,参数
  (vla-setText TextTable 1 0 "SerialNum")
  (vla-setText TextTable 1 1 "Drawing")
  (vla-setText TextTable 1 2 "名称\Name")
  (vla-setText TextTable 1 3 "对应电缆固定架图号\Cable clamp NO.")
  (vla-setText TextTable 1 4 "数量\quantity")
  ;调整表格内文字的对齐方式，文字大小
  (setq i 0
	j 0)
  (while (< i row_max)
    (while (< j col_max) 
      (vla-setcellalignment TextTable i j 5);(row, col, cellAlignment)
      (vla-setcelltextheight TextTable i j (* mscale textheight))
      (setq j (+ j 1))
    );end while
    (setq j 0)
    (setq i (+ i 1))
  );end while
  ;(vla-SetColumnWidth TextTable (- col_max 1) (* 57 mscale));Sets the column width for the column at the specified column index in the table.(col, Width)
  ;(vla-SetColumnWidth TextTable (- col_max 2) (* 57 mscale))
  ;(vla-SetColumnWidth TextTable 0 (* 57 mscale))
  ;(vlax-dump-object TextTable 2)
  (vla-GetBoundingBox TextTable 'pt_return 'pty);得到左下角和右上角的值
  (setq pt_return (vlax-safearray->list  pt_return))
);defun

;test
(defun C:dfef001(/)
  (setq myacad (vlax-get-acad-object))
  (setq mydoc (vla-get-ActiveDocument myacad))
  (setq myms (vla-get-ModelSpace mydoc));my models
  (setq topfltype "2.xMW_TopFlange")
  (setq mscale 140.0)
  (setq pt1 '(0 0 0))
  (TechReq_insert pt1 2.x_TechReq_detail  2.x_TechReq_detail 5.0 "en" topfltype)
);defun


(defun c:ass()
  (tflange 4686.0 4090.0 4437.0 3840.0 4300.0 37.0 45.0 160.0 54.0 184.0 10.0 )
)


;;;;;;普通法兰函数;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defun tflange (Dout_tfl Dpcd_in_tfl Dpcd_out_tfl Din_tfl Da_tfl Tn_tFL L_tfl H_tfl d_tfl num_hole_tfl R_tfl  / crt circle1 circle2 circle3 circle4 circle5 circle6 circle7 semi_num_hole_tfl dr1 i dr1n dr2n circle1n circle2n c1 c2 c3 c4 line1 line2 tflpt00 scale_tfl H_tflange L_tflange T_tflange R_tflange Dout_tflange Din_tflange
		                                                                  Dpcd_in_tflange Dpcd_out_tflange d_tflange tflpt01 tflpt02 tflpt03 tflpt04 tflpt05 tflpt06 tflpt07 tflpt08  tflpt09 tflpt010 tflpt111 tflpt110 tflpt19 tflpt18 tflpt171 tflpt17 tflpt23 tflpt22 tflpt33 tflpt31
		                                                                  tflpt21  tflpt20 tflpt16 tflpt15 tflpt14 tflpt13 tflpt12 tflpt11 tflpt10 tflpt30 tflpt1722 tflpt1621 tflpt40 tflpt41 tflp42 tflpt43 tflpt1722 tflpt1621 tflpt03042 tflpt03043 tflp03044 
		                                                                  line3 line4 line5 line6 line7 line8 line9 line10 line11 line12 line13 line14 line15 line16 line17 line18 line19 line20 line21 line22 line23  line24 line25 line26 line27 line28 line29 line30 line31
		                                                                  line32  line33  line34  drt9  drt8 drt7 drt6 drt5 drt4 drt3 drt2 drt11 drt10  tflpt0002 tflpt30 tflpt01621 tflpt01722 tflpt313 tflpt3133 tflpt500 tflpt5002 tflpt05 tflpt070  tflpt0709  dim2 dim4 dim5 tflpt03041
		                                                                  tflpt03045  tflpt1312 tflpt110111 tflpt0910  drt12 tflpt03048 tflpt03049 Da_tflange)
  (setq myacad (vlax-get-acad-object))
  (setq mydoc (vla-get-ActiveDocument myacad))
  (setq myms (vla-get-ModelSpace mydoc));my models
  (setq crt '(3300 4700 0 ));指定圆心
  ;(setq Dout_tfl 4612);指定T型法兰最外径
  (setq circle1 (vla-addcircle myms (vlax-3d-point crt) (/ Dout_tfl 2))) ;画出法兰外圆
  ;(setq Dpcd_in_tfl 4132);指定T法兰内分度圆直径
  ;(setq Dpcd_out_tfl 4420);指定T法兰外分度圆直径
  (command "layer" "M" "4虚线层" "")
  (setq circle2 (vla-addcircle myms (vlax-3d-point crt) (/ Dpcd_in_tfl 2)));画出法兰内分度圆
  (vla-put-LinetypeScale circle2 0.2);调整分度圆线型的比例
  (setq circle3 (vla-addcircle myms (vlax-3d-point crt) (/ Dpcd_out_tfl 2)));画出法兰外分度圆
  (vla-put-LinetypeScale circle3 0.2);调整分度圆线型的比例
  ;(setq Din_tfl 3960);指定法兰内圆直径
  ;(setq Da_tfl 4300);指定法兰筒壁对接直径
  (command "layer" "M" "1轮廓实线层" "")
  (setq circle4 (vla-addcircle myms (vlax-3d-point crt) (/ Din_tfl 2)));画出法兰内圆
  
  
  ;(vlax-dump-object circle4 t)
  (setq circle5 (vla-addcircle myms (vlax-3d-point crt) (/ Da_tfl 2)));画出法兰外圆
  (vla-put-LinetypeScale circle5 0.2);调整分度圆线型的比例
  ;(setq Tn_tfl 24.0)
  (setq circle6 (vla-addcircle myms (vlax-3d-point crt) (/ (- Da_tfl (* 2 Tn_tfl)) 2)));画出法兰壁厚所在圆
  (setq dr1 (polar crt 0  (/ Dpcd_in_tfl 2)));指定螺栓圆心
  ;(setq d_tfl 45);指定螺栓孔直径
  ;(alert "B");
  (setq circle7 (vla-addcircle myms (vlax-3d-point dr1) (/ d_tfl 2)));画出螺栓孔
  ;(setq num_hole_tfl 176.0);指定螺栓孔数量
  (setq semi_num_hole_tfl (/ num_hole_tfl 2));指定螺栓孔数量
 ; (alert "A");
  (setq i 1);指定循环起始数据
  (while (<= i semi_num_hole_tfl)
    (setq dr1n (polar crt (* (* pi 2)(/ i semi_num_hole_tfl )) (/ Dpcd_in_tfl 2)));指定螺栓孔内圆心
    (setq dr2n (polar crt (* (* pi 2)(/ i semi_num_hole_tfl )) (/ Dpcd_out_tfl 2)));指定螺栓孔外圆心
    (setq circle1n (vla-addcircle myms (vlax-3d-point dr1n) (/ d_tfl 2)));循环画出内分度圆螺栓孔
    (setq circle2n (vla-addcircle myms (vlax-3d-point dr2n) (/ d_tfl 2)));循环画出外分度圆螺栓孔
   (setq i (+ 1 i)) 
  )
  (setq c1 (polar crt 0 (+ 100 (/ Dout_tfl 2))));指定中心线右端点
  (setq c2 (polar crt (/ pi 2) (+ 100 (/ Dout_tfl 2))));指定中心线上端点
  (setq c3 (polar crt pi (+ 100 (/ Dout_tfl 2))));指定中心线左端点
  (setq c4 (polar crt (* 1.5  pi) (+ 100 (/ Dout_tfl 2))));指定中心线右端点
  (command "layer" "M" "3中心线层" "")
  (setq line1 (vla-addline myms (vlax-3d-point c1) (vlax-3d-point c3)));画出中心线水平线
  (setq line2 (vla-addline myms (vlax-3d-point c2) (vlax-3d-point c4)));画出中心线竖直线
  ;(alert "A")
  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;法兰放大图;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  (setq tflpt00 (polar crt (/ pi 10)  4000));指定法兰剖视图起点
  (setq scale_tfl 6);
  ;(setq H_tfl 140)
  ;(setq L_tfl 40)
  ;(setq Tn_tfl 24.0)
  ;(setq R_tfl 10)
  (setq H_tflange (* scale_tfl H_tfl));比例系数放大
  (setq L_tflange (* scale_tfl L_tfl));比例系数放大L_fl放大后为 L_flange
  (setq T_tflange (* scale_tfl Tn_tfl))
  (setq R_tflange (* scale_tfl R_tfl))
  (setq Dout_tflange (* scale_tfl Dout_tfl))
  (setq Din_tflange (* scale_tfl Din_tfl))
  (setq Da_tflange (* scale_tfl Da_tfl))
  (setq Dpcd_in_tflange (* scale_tfl Dpcd_in_tfl))
  (setq Dpcd_out_tflange (* scale_tfl Dpcd_out_tfl))
  (setq d_tflange (* scale_tfl d_tfl))
  ;(alert "b")
  (setq tflpt01 (polar tflpt00 0  (/ H_tflange 6.0)));指定法兰剖视图关键点
  (setq tflpt02 (polar tflpt01 0  (/ (- Dout_tflange Din_tflange) 6.0)));指定法兰剖视图关键点	
  (setq tflpt03 (polar tflpt02 0  (/ (- Dpcd_in_tflange (+ Din_tflange d_tflange)) 2.0)));指定法兰剖视图关键点
  (setq tflpt04 (polar tflpt03 0  (/ d_tflange 2.0)));指定法兰剖视图关键点
  (setq tflpt05 (polar tflpt04 0  (/ d_tflange 2.0)));指定法兰剖视图关键点
  (setq tflpt06 (polar tflpt05 0  (/ (- Da_tflange (+ Dpcd_in_tflange d_tflange)) 2.0)));指定法兰剖视图关键点
  (setq tflpt07 (polar tflpt06 0  (/ (- (- Dpcd_out_tflange d_tflange)  Da_tflange) 2.0)));指定法兰剖视图关键点
  (setq tflpt08 (polar tflpt07 0  (/ d_tflange 2.0)));指定法兰剖视图关键点
  (setq tflpt09 (polar tflpt08 0  (/ d_tflange 2.0)));指定法兰剖视图关键点
  (setq tflpt010 (polar tflpt09 0  (/ (- Dout_tflange (+ Dpcd_out_tflange d_tflange) ) 2.0)));指定法兰剖视图关键点
  (setq tflpt111 (polar tflpt010 (/ pi 2) (- H_tflange L_tflange)));指定法兰剖视图关键点
  (setq tflpt110 (polar tflpt09  (/ pi 2) (- H_tflange L_tflange)));指定法兰剖视图关键点
  (setq tflpt19 (polar tflpt08 (/ pi 2) (- H_tflange L_tflange)));指定法兰剖视图关键点
  (setq tflpt18 (polar tflpt07 (/ pi 2) (- H_tflange L_tflange)));指定法兰剖视图关键点
  (setq tflpt171 (polar tflpt06 (/ pi 2) (- H_tflange L_tflange)));指定法兰剖视图关键点
  ;(alert "c")
  (setq tflpt17 (polar tflpt171 0 R_tflange));指定法兰剖视图关键点
  (setq tflpt23 (polar tflpt17 (/ pi 2) R_tflange));指定法兰剖视图关键点
  (setq tflpt22 (polar tflpt23  pi  R_tflange));指定法兰剖视图关键点
  (setq tflpt33 (polar tflpt06 (/ pi 2) H_tflange ));指定法兰剖视图关键点
  (setq tflpt31  (polar tflpt33 pi T_tflange));指定法兰剖视图关键点
  (setq tflpt21  (polar tflpt22 (- pi) T_tflange));指定法兰剖视图关键点
  (setq tflpt20  (polar tflpt21 (- pi) R_tflange));指定法兰剖视图关键点
  (setq tflpt16  (polar tflpt20 (- (/ pi 2)) R_tflange));指定法兰剖视图关键点
  (setq tflpt15 (polar tflpt05 (/ pi 2) (- H_tflange L_tflange)));指定法兰剖视图关键点
  (setq tflpt14 (polar tflpt04 (/ pi 2) (- H_tflange L_tflange)));指定法兰剖视图关键点
  (setq tflpt13 (polar tflpt03 (/ pi 2) (- H_tflange L_tflange)));指定法兰剖视图关键点
  (setq tflpt12 (polar tflpt02 (/ pi 2) (- H_tflange L_tflange)));指定法兰剖视图关键点
  (setq tflpt11 (polar tflpt01 (/ pi 2) (- H_tflange L_tflange)));指定法兰剖视图关键点
  (setq tflpt10 (polar tflpt11  pi (/ H_tflange 8.0)));指定法兰剖视图关键点
  (setq tflpt30 (polar tflpt11  (/ pi 2) L_tflange));指定法兰剖视图关键点
  (setq tflpt1722 (polar tflpt23  (-(* 0.25 pi)) R_tflange ));指定法兰圆角标注键点
  (setq tflpt1621 (polar tflpt16  (-(* 0.25 pi)) R_tflange ));指定法兰圆角标注键点
  (setq tflpt40 (polar tflpt14  (/ pi 2) 100));指定法兰剖视图关键点
  (setq tflpt41 (polar tflpt04 (- (/ pi 2))100));指定法兰剖视图关键点
  (setq tflpt42 (polar tflpt19  (/ pi 2) 100));指定法兰剖视图关键点
  (setq tflpt43 (polar tflpt08  (-(/ pi 2)) 100));指定法兰剖视图关键点
   ;(alert "d")
  (setq tflpt1722 (polar tflpt23  (-(* 0.75 pi)) R_tflange ));指定法兰圆角标注键点
  (setq tflpt1621 (polar tflpt20  (-(* 0.25 pi)) R_tflange ));指定法兰圆角标注键点
  (setq tflpt03042 '(3600 1800 0 ));公司图章插入点
  (setq tflpt03043 '(1400 1800 0 ));法兰P向视图插入点
  (setq tflpt03044 '(11180 8150 0 ));法兰粗糙度插入点
  ;(setq tflpt03045 (polar tflpt31  (/ pi 2) 900 ));A-A视图及比例插入点
  ;(setq flpt03046 (polar flpt13  (- pi) (/ (- Dpcd_flange (+ d_flange Din_flange)) 4.0)));平行度插入点
  ;(setq flpt03047 (polar flpt05 0 (/ (- Dout_flange (+ d_flange Dpcd_flange)) 4.0 )));平面度插入点
  (command "layer" "M" "1轮廓实线层" "")
  (setq line3 (vla-addline myms (vlax-3d-point tflpt00) (vlax-3d-point tflpt01)));画出法兰放大图连线
  (setq line4 (vla-addline myms (vlax-3d-point tflpt01) (vlax-3d-point tflpt02)));画出法兰放大图连线
  (setq line5 (vla-addline myms (vlax-3d-point tflpt02) (vlax-3d-point tflpt03)));画出法兰放大图连线
  (setq line6 (vla-addline myms (vlax-3d-point tflpt03) (vlax-3d-point tflpt04)));画出法兰放大图连线
  (setq line7 (vla-addline myms (vlax-3d-point tflpt04) (vlax-3d-point tflpt05)));画出法兰放大图连线
  (setq line8 (vla-addline myms (vlax-3d-point tflpt05) (vlax-3d-point tflpt06)));画出法兰放大图连线
  (setq line9 (vla-addline myms (vlax-3d-point tflpt06) (vlax-3d-point tflpt07)));画出法兰放大图连线
  ; (alert "e")
  (setq line10 (vla-addline myms (vlax-3d-point tflpt07) (vlax-3d-point tflpt08)));画出法兰放大图连线
  (setq line11 (vla-addline myms (vlax-3d-point tflpt08) (vlax-3d-point tflpt09)));画出法兰放大图连线
  (setq line12 (vla-addline myms (vlax-3d-point tflpt09) (vlax-3d-point tflpt010)));画出法兰放大图连线
  (setq line13 (vla-addline myms (vlax-3d-point tflpt010) (vlax-3d-point tflpt111)));画出法兰放大图连线
  (setq line14 (vla-addline myms (vlax-3d-point tflpt111) (vlax-3d-point tflpt110)));画出法兰放大图连线
  (setq line15 (vla-addline myms (vlax-3d-point tflpt110) (vlax-3d-point tflpt19)));画出法兰放大图连线
  (setq line16 (vla-addline myms (vlax-3d-point tflpt19) (vlax-3d-point tflpt18)));画出法兰放大图连线
  (setq line17 (vla-addline myms (vlax-3d-point tflpt18) (vlax-3d-point tflpt17)));画出法兰放大图连线
   ;(alert "f")
  (setq arc1 (vla-addarc myms (vlax-3d-point tflpt23) R_tflange pi (* 1.5 pi)));画出法兰放大图连线
  ;(alert "g")
  (setq line18 (vla-addline myms (vlax-3d-point tflpt22) (vlax-3d-point tflpt33)));画出法兰放大图连线
  (setq line19 (vla-addline myms (vlax-3d-point tflpt33) (vlax-3d-point tflpt31)));画出法兰放大图连线
  (setq line20 (vla-addline myms (vlax-3d-point tflpt31) (vlax-3d-point tflpt21)));画出法兰放大图连线
  (setq arc2 (vla-addarc myms (vlax-3d-point tflpt20) R_tflange (-(/ pi 2)) 0));画出法兰放大图连线
  (setq line21 (vla-addline myms (vlax-3d-point tflpt16) (vlax-3d-point tflpt15)));画出法兰放大图连线
  (setq line22 (vla-addline myms (vlax-3d-point tflpt15) (vlax-3d-point tflpt14)));画出法兰放大图连线
  (setq line23 (vla-addline myms (vlax-3d-point tflpt14) (vlax-3d-point tflpt13)));画出法兰放大图连线
  (setq line24 (vla-addline myms (vlax-3d-point tflpt13) (vlax-3d-point tflpt12)));画出法兰放大图连线
  (setq line25 (vla-addline myms (vlax-3d-point tflpt12) (vlax-3d-point tflpt11)));画出法兰放大图连线
  (setq line26 (vla-addline myms (vlax-3d-point tflpt11) (vlax-3d-point tflpt10)));画出法兰放大图连线
  (command "layer" "M" "3中心线层" "")
  (setq line27 (vla-addline myms (vlax-3d-point tflpt40) (vlax-3d-point tflpt41)));画出法兰放大图连线
  (setq line28 (vla-addline myms (vlax-3d-point tflpt42) (vlax-3d-point tflpt43)));画出法兰放大图连线
  (command "layer" "M" "1轮廓实线层" "")
  (setq line29 (vla-addline myms (vlax-3d-point tflpt110) (vlax-3d-point tflpt09)));画出法兰放大图连线
  (setq line30 (vla-addline myms (vlax-3d-point tflpt18) (vlax-3d-point tflpt07)));画出法兰放大图连线
  (setq line31 (vla-addline myms (vlax-3d-point tflpt15) (vlax-3d-point tflpt05)));画出法兰放大图连线
  (setq line32 (vla-addline myms (vlax-3d-point tflpt13) (vlax-3d-point tflpt03)));画出法兰放大图连线
  (setq line33 (vla-addline myms (vlax-3d-point tflpt12) (vlax-3d-point tflpt02)));画出法兰放大图连线
  (setq line34 (vla-addline myms (vlax-3d-point tflpt31) (vlax-3d-point tflpt30)));画出法兰放大图连线
  (command "layer" "M" "5剖面线层" "")
  (command "spline" tflpt30 tflpt10 tflpt00  "" "" "")
  (rephatch myms (list line12 line13 line14 line29) 30 0);法兰右半部分剖面线的绘制
  (rephatch myms (list line8 line9 line30 line17 arc1 line18 line19 line20 arc2 line21 line31) 30 0);法兰中间部分剖面线的绘制
  (rephatch myms (list line5 line32 line24 line33 ) 30 0);法兰左半部分剖面线的绘制
  (setq drt9 (polar crt (- (* pi (/ 1 6.0))) (/ Din_tfl 2)));标注点内径定位基点
  (setq drt8 (polar crt  (* pi (/ 5 6.0)) (/ Din_tfl 2)));标注点内径定位基点
  (setq drt4 (polar crt  (- (* pi (/ 1 12.0))) (/ Dout_tfl 2)));标注点塔筒璧径定位基点
  (setq drt5 (polar crt  (* pi (/ 11 12.0)) (/ Dout_tfl 2)));标注点塔筒璧径定位基点
  (setq drt10 (polar crt  (* pi (/ 1 6.0)) (/ Da_tfl 2)));标注点法兰外径定位基点
  (setq drt11 (polar crt  (* pi (/ 7 6.0)) (/ Da_tfl 2)));标注点法兰外径定位基点
  (setq drt3 (polar crt  (* pi (/ 1 4.0)) (/ Dpcd_out_tfl 2)));标注点法兰外径定位基点
  (setq drt2 (polar crt  (* pi (/ 5 4.0)) (/ Dpcd_out_tfl 2)));标注点法兰外径定位基点
  (setq drt6 (polar crt (* pi (/ 1 3.0))  (/ Dpcd_in_tfl 2 )));标注点内径定位基点
  (setq drt7 (polar crt (* pi (/ 4 3.0))  (/ Dpcd_in_tfl 2 )));标注点内径定位基点
  ;(setq dr1 (polar cr 0  (/ 2 (- Dpcd_fl Din_fl))));标注点分度圆定位基点
  ;(setq dr4 (polar cr (/ pi 6)  (/ Din_fl 2)));标注点内径定位点1
  ;(setq dr5 (polar cr (* pi (/ 7 6.0)) (/ Din_fl 2)));标注点内径定位点2
  ;(setq dr6 (polar cr (/ pi 3)  (/ Dout_fl 2)));标注点外径定位点1
  ;(setq dr7 (polar cr (- (* (/ 2 3.0) pi))  (/ Dout_fl 2)));标注点外径定位点2
  ;(setq dr8 (polar cr (* pi (/ 5 6.0))  (/ Dpcd_fl 2)));标注点分度圆定位点1
  ;(setq dr9 (polar cr (- (* pi (/ 1 6.0))) (/ Dpcd_fl 2)));标注点分度圆定位点2
  ;(setq line21 (vla-addline myms (vlax-3d-point dr4) (vlax-3d-point dr5)));画出法兰放大图连线
  ;(setq dim_scale 5);指定尺寸的放大比例
  ;(setq dim_disv (/ Din_fl 2));
  ;(setq dim_dish (/ Din_fl 2));
  ;(setq dim_ang  (/ pi 6));
  ;;;;;;;标注尺寸;;;;;;;;;;;;;;;;;;;;;;;;
  (setq tflpt0002 (polar tflpt10 (- (/ pi 2)) (- H_tflange L_tflange)));
  (dimFlange_thick2 tflpt10 tflpt0002 (/ 1.0 scale_tfl) 0  (- (* 4 H_tfl)));法兰厚度标注
   ;(alert "z")
  (ldimv2 tflpt30 tflpt01 (/ 1.0 scale_tfl) 0 (- (* 7 H_tfl)));法兰高度标注
    ;(alert "h")
  (dimr_t tflpt1621 60  (* 0.75 pi) (/ 1.0 scale_tfl) 100);圆角标注
  ;(alert "i")
  (dimr_t tflpt1722 60  (* 0.25 pi) (/ 1.0 scale_tfl) 100);圆角标注
  (setq tflpt313 (polar tflpt33  0 400));指定法兰厚度尺寸放置位置
  (setq tflpt3133 (polar tflpt313  (* 0.5 pi) 400 ));指定法兰厚度尺寸放置位置
  (scaleDim_ct tflpt31 tflpt33 scale_tfl tflpt3133);标注法兰脖子厚度
  (setq tflpt500 (polar tflpt03  0 (- 500) ));指定法兰直径及个数放置位置
  (setq tflpt5002 (polar tflpt500 (-(* 0.5 pi)) 700 ));指定法兰直径及个数放置位置
  (dimhole tflpt05 tflpt03 scale_tfl  tflpt5002 semi_num_hole_tfl);标出圆孔直径及个数
  (setq tflpt070 (polar tflpt09  0 500 ));指定法兰直径及个数放置位置
  (setq tflpt0709 (polar tflpt070  (-(* 0.5 pi)) 700 ));指定法兰直径及个数放置位置
  (dimhole tflpt07 tflpt09 scale_tfl  tflpt0709 semi_num_hole_tfl);标出圆孔直径及个数
  
  ;(dimdiafla_t drt9 drt8 600 0 3.0);标出法兰最内经
  (dimdiafla drt9 drt8 500 1 0 3.0);标出法兰最内经
  ;(dimdiafla_t drt4 drt5 500 2.0 0);标出法兰最外径
  (dimdiafla drt4 drt5 500 1 2.0 0);标出法兰最外径
  
  ;(setq dim2 (vla-AddDimDiametric myms (vlax-3d-point drt6) (vlax-3d-point drt7) 500));标注主视图的分度圆内直径
  (dimdiafla drt6 drt7 500 4 2.0 0);标注主视图的分度圆内直径
  ;(setq dim4 (vla-AddDimDiametric myms (vlax-3d-point drt3) (vlax-3d-point drt2) 500 ));标注分度圆外直径
  (dimdiafla drt3 drt2 500 4 2.0 0);标注分度圆外直径
  ;(setq dim5 (vla-AddDimDiametric myms (vlax-3d-point drt10) (vlax-3d-point drt11) 500 ));标注塔筒壁外直径
  (dimdiafla drt10 drt11 500 0 2.0 0);标注塔筒壁外直径，一般是4300那个值
  
  ;(vla-put-ToleranceDisplay dim4 2)
  ;(vla-put-ToleranceUpperLimit dim4 0)
  ;(vla-put-ToleranceLowerLimit dim4 2.0)
  ;(vlax-dump-object dim1 t)
  ;(setq dim2 (vla-AddDimDiametric myms (vlax-3d-point dr9) (vlax-3d-point dr8) 500) );标注主视图的分度圆直径
  ;(setq dim3 (vla-AddDimDiametric myms (vlax-3d-point dr6) (vlax-3d-point dr7) 500 ) );标注主视图的外径直径
  ;(vla-put-ToleranceDisplay dim3 2)
  ; (vla-put-ToleranceUpperLimit dim3 2.0)
  ;(vla-put-ToleranceLowerLimit dim3 0)
  ; (vla-put-Arrowhead1block dim3 "open");改变箭头样式反向
  ;(vla-put-Arrowhead2block dim3 "open");改变箭头样式反向
  ;(vlax-dump-object dim3 t);;;查看属性
  ;(setq dim4 (vla-AddDimAligned myms (vlax-3d-point flpt03) (vlax-3d-point flpt05) (vlax-3d-point flpt5002)));标注圆孔直径和孔数
  ;;(vlax-dump-object dim4 t);;;查看属性
  ;(vla-put-linearscalefactor dim4 (/ 1.0 scale_fl));改变比例
  ;(setq dim5 (vla-AddDimAligned myms (vlax-3d-point flpt31) (vlax-3d-point flpt33) (vlax-3d-point flpt3133)));标注法兰脖子厚度
  ;(vlax-dump-object dim5 t);;;查看属性
  ;(vla-put-linearscalefactor dim5 (/ 1.0 scale_fl));改变比例0
  ;(vla-put-AltSuppressTrailingZeros dim5 1.0);改变小数点位数
  ;(setq dimr1 (vla-AddDimRadial myms (vlax-3d-point flpt20) (vlax-3d-point flpt16) 100 ) );法兰圆角标注
  ;(setq text1 (strcat (rtos num_hole 2 0 ) "*%%c" ));
  ;(vla-put-TextPrefix dim4 text1);加入前缀
  ;(vla-put-Textsuffix dim4 "EQS");加入后缀
  (setq tflpt03041 (polar tflpt02(- (/ pi 2)) 1200));指定技术要求注键点
  (command "insert" "2and3commonflange" "S" 1 tflpt03041 "");插入法兰技术要求
  (command "insert" "company" "S" 1 tflpt03042 "");插入公司图章要求
  (command "insert" "pview" "S" 1 tflpt03043 "");插入P视图要求
  (command "insert" "cucaodu" "S" 1 tflpt03044 "");插入粗糙度要求
  (setq tflpt03045 (polar tflpt31  (/ pi 2) 900 ));A-A视图及比例插入点
  (command "insert" "aaview1" "S" 1 tflpt03045 "");插入A-A视图要求
  (setq tflpt1312 (polar tflpt13  pi 100));A-A视图及比例插入点
  (command "insert" "pingxingdu" "S" 1 tflpt1312 "");插入平行度要求
  (setq tflpt110111 (polar tflpt110  0  200));A-A视图及比例插入点
  (command "insert" "pingxingdu1" "S" 1 tflpt110111 "");插入平行度要求
  (setq tflpt0910 (polar tflpt09  0  200));A-A视图及比例插入点
  (command "insert" "pingmiandu" "S" 1 tflpt0910 "");插入平面度要求
  (setq drt12 (polar crt 0  (/ Da_tfl 2)));A-A剖视视图及比例插入点
  (command "insert" "aapao2" "S" 1 drt12 "");插入aa剖视图要求
  (command "insert" "bjizhun1" "S" 1 tflpt02 "");插入B基准要求
  (setq tflpt03048 (polar crt (- (* pi (/ 58.0 180.0))) (/ Dout_tfl 2)));标注点内径定位点2
  (command "insert" "pjiantou2" "S" 1 tflpt03048 "");插入p箭头要求
  (setq tflpt03049 (polar crt  (* (/ 15.0 180.0) pi) (/ Dout_tfl 2.0)));标注点a基准
  
  ;(command "insert" "ajizhun3" "S" 1 tflpt03049 "");插入a基准要求
  
  (command "insert" "ajizhun1" "S" 1 "R" 345.0 drt4 "");插入a基准要求
  
  (command "insert" "weizhidu1" "S" 1 tflpt5002 "");插入位置度要求
  
  (command "insert" "weizhidu" "S" 1 tflpt0709 "");插入位置度要求
  
 )




;;;;水平尺寸标注
(defun dimh_t (pt1 pt2 dimscale disv dish ang / p3x p3y p_dim dim1 handle_value);dim diamatric 直径标注,点1，点2，竖直位置，水平左右位置，旋转角度
  (setq p3x (+ (car pa) dish))
  (setq p3y (+ (cadr pt1) disv ))
  (setq p3x (+ (car (MiddlePoint pt1 pt2)) dish))
  (setq p3y (+ (cadr pt1) disv ))
  (setq p_dim (list p3x p3y ))
  (setq dim1 (vla-adddimaligned myms (vlax-3d-point pt1) (vlax-3d-point pt2) (vlax-3d-point p_dim) ) )
  (vla-put-LinearScaleFactor dim1 dimscale);改变加强板标注的尺寸比例
  (vla-put-TextOverride dim1 "%%C<>");把直径符号加入到标注尺寸最前面
  (vla-put-TextPosition dim1 (vlax-3d-point p_dim))
  (vlax-dump-object dim1 t)
  (setq handle_value (vla-get-Handle dim1))
  (command "dimedit" "o"  (handent handle_value) "" ang)
)


(defun dimr_t(center R ang dimscale leng / dim1)
  (setq dim1 (vla-AddDimRadial myms (vlax-3d-point center) (vlax-3d-point (polar center ang R)) leng ) )
  (vla-put-LinearScaleFactor dim1 dimscale)
  ;(vla-put-TextSuffix dim1 "(展开尺寸)");标注后面加文字
  ;(vla-put-TextPrefix dim1 "4X");把
)




;;;;;;;;;;;;;;;;;;;;;;********************标注主视图法兰直径;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defun dimdiafla_t (pt1 pt2 leng tol1 tol2)
  (setq dim2 (vla-AddDimDiametric myms (vlax-3d-point pt1) (vlax-3d-point pt2) leng));标注主视图的分度圆直径
  (vla-put-ToleranceDisplay dim2 2)
  (vla-put-ToleranceUpperLimit dim2 tol1)
  (vla-put-ToleranceLowerLimit dim2 tol2)
)


;总图标题栏插入
(defun c:tower_title_insert(/ i titlename titlename_en)
  ;(setq qty 4)
  (setq tydh_weld (getstring "\n输入图样代号："))
  (setq hh (getstring "\n输入塔架HH："))
  (setq titlename (getstring "\n输入图样中文名称："))
  (setq titlename_en (strcat (rtos section_qty 2 0) " Sections " hh " m HH Tower"))
  
  
  ;(setq titlename (weld_title_name i))
  ;(setq titlename_en (weld_title_name_e i))
  ;(setq titleweight (+ (nth i SectionMassList) (nth i FlangeMassList) (nth (+ i 1) FlangeMassList)))
  ;(setq titleweight (rtos titleweight 2 1))
  ;(setq mscale 100)
 
  (setq titleweight all_tower_mass)
  (setq titleweight_str (rtos all_tower_mass 2 0))
  ;块名称，S，比例，坐标，旋转角度,
  ;批准日期，标准化日期，工艺日期，审核日期，校对日期，批准，标准化，工艺，审核，涉及日期，校对，设计
  ;图样代号，名称,Name，材料，Material，重量，比例
  ;drawingnumber,第几页，共几页，sheet，of，版本
  (print mscale)
  (setq title_zb (title_zb mscale))
  (setq tydh_zb (list 0 (* mscale 1169) 0))
  
  (setq title_bl (strcat "1:" (rtos mscale 2 0)))
  (if (= power "2.X")
    (command "insert" "pc_title_block" "S" mscale title_zb ""
	   "" "" "" "" "" "" "" "" "" "" "" ""
	   tydh_weld titlename titlename_en "126-2.2/131-2.2/121-2.2/131-2.3" "" titleweight_str title_bl
	   "" "" "" "" "" ""
	   "")
    (command "insert" "pc_title_block" "S" mscale title_zb ""
	   "" "" "" "" "" "" "" "" "" "" "" ""
	   tydh_weld titlename titlename_en "121-2.5/130-2.5/136-2.5/140-2.5" "1431/1432/1433/1434" titleweight_str title_bl
	   "" "" "" "" "" ""
	   "")
  );End
  ;(command "insert" "pc_title_block" "S" mscale title_zb ""
  ;	   "" "" "" "" "" "" "" "" "" "" "" ""
  ;	   tydh_weld titlename titlename_en "121-2.5/130-2.5/136-2.5/140-2.5" "1431/1432/1433/1434" titleweight_str title_bl
  ;	   "" "" "" "" "" ""
  ;	   "")
  ;插入左上角的图样代号
  (command "insert" "PC_TYDH_BLOCK" "S" mscale tydh_zb ""
	   tydh_weld
	   )
  ;(weld_mxb_insert i)
);END tower_title_insert

;总图的明细表插入坐标
(defun title_zb(scale / zb)
  (if (= scale 100)
    (setq zb (list 80600 0 0))
    (if (= scale 120)
      (setq zb (list 96720 0 0 ))
      (setq zb (list 112840 0 0))
    )

  )

  
);End title_zb

;总图的右上角图样代号插入坐标
(defun tydh_zb(scale / zb)
  (if (= scale 100)
    (setq zb (list 0 116900 0))
    (if (= scale 120)
      (setq zb (list 0 140280 0 ))
      (setq zb (list 0 163660 0))
    )

  )

  
);End tydh_zb

;法兰标题栏插入
(defun c:fl_title_insert(/ tydh i titlename titlename_en titleweight)
  (setq tydh (getstring "\n输入图样代号："))
  (setq i (atoi (getstring "\n输入连接法兰i：")))
  (setq titlename (mxb_fl_name i))
  (setq titlename_en (mxb_fl_name_e i))
  (setq titleweight (rtos (nth i FlangeMassList) 2 1))
  ;块名称，S，比例，坐标，旋转角度,
  ;批准日期，标准化日期，工艺日期，审核日期，校对日期，批准，标准化，工艺，审核，涉及日期，校对，设计
  ;图样代号，名称,Name，材料，Material，重量，比例
  ;drawingnumber,第几页，共几页，sheet，of，版本
  (command "insert" "pc_title_block" "S" 30 '(11700 0) ""
	   "" "" "" "" "" "" "" "" "" "" "" ""
	   tydh titlename titlename_en "Q355NEZ35" "S355NL-Z35" titleweight "1:30"
	   "" "1" "1" "1" "1" ""
	   "")
  ;插入左上角的图样代号
  (command "insert" "PC_TYDH_BLOCK" "S" "30" '(0 8610) ""
	   tydh
	   "")
)
;;;;;END fl_title_insert

;焊合总成标题栏插入
(defun c:weld_title_insert(/ tydh_weld i titlename titlename_en titleweight bzbl)
  (setq tydh_weld (getstring "\n输入图样代号："))
  (setq i (atoi (getstring "\n输入塔筒焊合i：")))
  (setq titlename (weld_title_name i))
  (setq titlename_en (weld_title_name_e i))
  ;(setq titleweight (+ (nth i SectionMassList) (nth i FlangeMassList) (nth (+ i 1) FlangeMassList)))
  ;(setq titleweight (rtos weld_all_mass 2 1))
  (setq titleweight (rtos weld_all_mass 2 1))
  ;块名称，S，比例，坐标，旋转角度,
  ;批准日期，标准化日期，工艺日期，审核日期，校对日期，批准，标准化，工艺，审核，涉及日期，校对，设计
  ;图样代号，名称,Name，材料，Material，重量，比例
  ;drawingnumber,第几页，共几页，sheet，of，版本
  (if (= i 0)
    (progn
      
      (setq title_zb '(48360 0 0))
      (setq tydh_zb '(0 34440 0))
    )
    (progn
      (setq title_zb '(22360 0))
      (setq tydh_zb '(0 32840 0))
    )
  )
  (setq bzbl (strcat "1:" (rtos mscale 2 0)))
  (command "insert" "pc_title_block" "S" mscale title_zb ""
	   "" "" "" "" "" "" "" "" "" "" "" ""
	   tydh_weld titlename titlename_en "" "" titleweight bzbl
	   "" "1" "1" "1" "1" ""
	   "")
  ;插入左上角的图样代号
  (command "insert" "PC_TYDH_BLOCK" "S" mscale tydh_zb ""
	   tydh_weld
	   "")
  ;(weld_mxb_insert i)
);END weld_title_insert

;焊合总成明细表插入;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defun c:weld_mxb_insert ( / i tydh_fl_d tydh_cylinder tydh_fl_u tydh_jqb bt_zb mxb_fl_d_zb_y mxb_fl_d_zb
			  fl_name_d fl_name_u fl_name_d_e fl_name_u_e mxb_fl_weight_d mxb_fl_weight_u mxb_cylinder_weight
			  mxb_2_zb_y mxb_2_zb mxb_3_zb_y mxb_3_zb count index mxb_jqb_weight mxb_rep_hole_mass )
  (setq i (atoi (getstring "\n输入塔筒焊合i：")))
  (setq tydh_fl_d (getstring "\n输入焊合下法兰图号："))
  (setq tydh_cylinder (getstring "\n输入筒体图号：："))
  (setq tydh_fl_u (getstring "\n输入焊合上法兰图号："))
  (setq tydh_jqb (getstring "\n输入加强板图号："))
  (if (= i 0)
    (progn;底段焊合图框
      (setq tz_zb_x 48360)
    )
    (progn
      (setq tz_zb_x 22360)
    )
  )
  (setq tz_zb_y (* 50 mscale))
  ;明细表标头插入
  (setq bt_zb (list tz_zb_x tz_zb_y 0))
  (print bt_zb)
  (print mscale)
  (command "insert" "PC_MXBTITLERECORD" "S" mscale bt_zb "" "")
  (setq weld_all_mass 0);焊合的总重量
  ;块名称，S，比例，坐标，旋转角度,
  ;版本，NOTES，MATERIAL，NAME，DRAWINGNUMBER，总重，单重，备注，数量，名称，序号，代号
  ;材料
  ;明细表，下法兰
  (setq fl_name_d (mxb_fl_name i) );焊合下法兰中文名
  (setq fl_name_d_e (mxb_fl_name_e i) );焊合下法兰英文名
  (setq mxb_fl_weight_d (nth i FlangeMassList));焊合下法兰的重量
  (setq mxb_fl_weight_d_str (rtos mxb_fl_weight_d 2 1));焊合下法兰字符型重量
  (setq mxb_fl_weight_d (atof mxb_fl_weight_d_str));转换成计算焊合总重量的数值型重量
  (setq weld_all_mass (+ weld_all_mass mxb_fl_weight_d));焊合总重累加
  (setq count 1)
  (setq index count)
  (setq mxb_fl_d_zb_y (+ tz_zb_y mscale (* 13 mscale count)))
  (setq mxb_fl_d_zb (list tz_zb_x mxb_fl_d_zb_y ))
  (print mxb_fl_d_zb)
  (command "insert" "pc_mxb_block" "S" mscale mxb_fl_d_zb ""
	   "" "" "S355NL-Z35" fl_name_d_e "" mxb_fl_weight_d_str mxb_fl_weight_d_str "" "1" fl_name_d index tydh_fl_d
	   "Q355NEZ35" )
  ;加强板
  (if (= i 0)
    (progn;如果是第一段焊合
      (setq count (+ count 1))
      (setq index count)
      (setq mxb_jqb_zb_y (+ tz_zb_y mscale (* 13 mscale count)) );明细表加强板
      (setq mxb_jqb_zb (list tz_zb_x mxb_jqb_zb_y));明细表插入的点
  
      (setq mxb_jqb_weight (nth 1 (jqb_weight)));加强板的重量
      (setq mxb_jqb_weight_str (rtos mxb_jqb_weight 2 1))
      (setq mxb_jqb_weight (atof mxb_jqb_weight_str))
      (setq weld_all_mass (+ weld_all_mass mxb_jqb_weight));焊合总重累加
      
      (command "insert" "pc_mxb_block" "S" mscale mxb_jqb_zb ""
	   "" "In drawing" "S355J0/J2/NL" "Reinforcing plate" "" mxb_jqb_weight_str mxb_jqb_weight_str "本图" "1" "加强板" index tydh_jqb
	   "Q355C/D/NE" )
    )
  );End if
  ;明细表,筒体
  (setq count (+ count 1))
  (setq index count)
  (setq mxb_2_zb_y (+ tz_zb_y mscale (* 13 mscale count)) );明细表2坐标
  (setq mxb_2_zb (list tz_zb_x mxb_2_zb_y))
  (if (= i 0)
    (progn;第一段
      (setq mxb_rep_hole_mass (nth 0 (jqb_weight)));筒体上因开加强板门洞损失的重量
      (setq mxb_rep_hole_mass_str (rtos mxb_rep_hole_mass 2 1))
      (setq mxb_rep_hole_mass (atof mxb_rep_hole_mass_str))
      
      (setq mxb_cylinder_weight (- (atof (rtos (nth i SectionMassList) 2 1)) mxb_rep_hole_mass))
      (setq mxb_cylinder_weight_str (rtos mxb_cylinder_weight 2 1))
      (setq mxb_cylinder_weight (atof mxb_cylinder_weight_str))
    )
    (progn
      (setq mxb_cylinder_weight (nth i SectionMassList));其他段的筒体重量
      (setq mxb_cylinder_weight_str (rtos mxb_cylinder_weight 2 1))
      (setq mxb_cylinder_weight (atof mxb_cylinder_weight_str))
    )
  )
  ;(setq mxb_rep_hole_mass (nth 0 (jqb_weight)));筒体上因开加强板门洞损失的重量
  ;(setq mxb_cylinder_weight (- (nth i SectionMassList) mxb_rep_hole_mass))
  ;(setq mxb_cylinder_weight)
  (setq weld_all_mass (+ weld_all_mass mxb_cylinder_weight));焊合总重累加
  (command "insert" "pc_mxb_block" "S" mscale mxb_2_zb ""
	   "" "In drawing" "S355J0/J2/NL" "Cylinder" "" mxb_cylinder_weight_str mxb_cylinder_weight_str "本图" "1" "筒体" index tydh_cylinder
	   "Q355C/D/NE" )
  ;明细表3,上法兰
  (setq fl_name_u (mxb_fl_name (+ i 1)) );焊合上法兰
  (setq fl_name_u_e (mxb_fl_name_e (+ i 1)) );焊合上法兰英文名
  (setq mxb_fl_weight_u (nth (+ i 1) FlangeMassList));焊合上法兰的重量
  (setq mxb_fl_weight_u_str (rtos mxb_fl_weight_u 2 1));焊合上法兰的字符型四舍五入重量
  (setq mxb_fl_weight_u (atof mxb_fl_weight_u_str))
  (setq weld_all_mass (+ weld_all_mass mxb_fl_weight_u));焊合总重累加
  
  (setq count (+ count 1))
  (setq index count)
  (setq mxb_3_zb_y (+ tz_zb_y mscale (* 13 mscale count)) );明细表3坐标
  (setq mxb_3_zb (list tz_zb_x mxb_3_zb_y))
  (command "insert" "pc_mxb_block" "S" mscale mxb_3_zb ""
	   "" "" "S355NL-Z35" fl_name_u_e "" mxb_fl_weight_u_str mxb_fl_weight_u_str "" "1" fl_name_u index tydh_fl_u
	   "Q355NEZ35" )
  (if (= i 0)
    (progn;如果是第一段焊合
      (setq count (+ count 1))
      (setq index count)
      (setq mxb_eb_zb_y (+ tz_zb_y mscale (* 13 mscale count)) );明细表加强板
      (setq mxb_eb_zb (list tz_zb_x mxb_eb_zb_y))
      (setq weld_all_mass (+ weld_all_mass 1.8));焊合总重累加
      ;(setq mxb_eb_weight 0.88)
      (command "insert" "pc_mxb_block" "S" mscale mxb_eb_zb ""
	   "" "" "" "Entrance ladder lug welded" "" (rtos 1.8) (rtos 0.88) "" "2" "入口梯子耳板焊合" index "60.11.00985"
	   "" )
    )
  );End if
  
)
;END weld_mxb_insert

;焊合的中文名称
(defun weld_title_name (i / name)
  (cond
    ( (= i (- section_qty 1)) 
      (setq name "顶段塔筒焊合")
    );顶法兰
    ( (= i 0)
      (setq name "第一段塔筒焊合")
    );塔架底法兰
    ( (= i 1) 
      (setq name "第二段塔筒焊合")
    );连接法兰一
    ( (= i 2) 
      (setq name "第三段塔筒焊合")
    );连接法兰二
    ( (= i 3) 
      (setq name "第四段塔筒焊合")
    );连接法兰三
    ( (= i 4) 
      (setq name "第五段塔筒焊合")
    );连接法兰四
    ( (= i 5) 
      (setq name "第六段塔筒焊合")
    );连接法兰五
    ( (= i 6) 
      (setq name "第七段塔筒焊合")
    );连接法兰六

    (t
      (setq name "Error!")
    )
  )
);End weld_title_name

;焊合的英文名称
(defun weld_title_name_e(i / name)
  (cond
    ( (= i (- section_qty 1)) 
      (setq name "Top Section Welded")
    );顶法兰
    ( (= i 0)
      (setq name "Section Ⅰ Welded")
    );塔架底法兰
    ( (= i 1) 
      (setq name "Section Ⅱ Welded")
    );连接法兰一
    ( (= i 2) 
      (setq name "Section Ⅲ Welded")
    );连接法兰二
    ( (= i 3) 
      (setq name "Section Ⅳ Welded")
    );连接法兰三
    ( (= i 4) 
      (setq name "Section Ⅴ Welded")
    );连接法兰四
    ( (= i 5) 
      (setq name "Section Ⅵ Welded")
    );连接法兰五
    ( (= i 6) 
      (setq name "Section Ⅶ Welded")
    );连接法兰六

    (t
      (setq titlename_en "Error!")
    )
  )
);End weld_title_name_e

;法兰的中文名称
(defun mxb_fl_name (i / name)
  (cond
    ( (= i (- section_qty 0)) 
      (setq titlename_en "顶法兰")
    );顶法兰
    ( (= i 0)
      (setq name "塔架底法兰")
    );塔架底法兰
    ( (= i 1) 
      (setq name "连接法兰一")
    );连接法兰一
    ( (= i 2) 
      (setq name "连接法兰二")
    );连接法兰二
    ( (= i 3) 
      (setq name "连接法兰三")
    );连接法兰三
    ( (= i 4) 
      (setq name "连接法兰四")
    );连接法兰四
    ( (= i 5) 
      (setq name "连接法兰五")
    );连接法兰五
    ( (= i 6) 
      (setq name "连接法兰六")
    );连接法兰六
    ( (= i 7) 
      (setq name "连接法兰七")
    );连接法兰七
    (t
      (setq name "Error!")
    )
  )
);End mxb_fl_name

;法兰的英文名称
(defun mxb_fl_name_e(i / name)
  (cond
    ( (= i (- section_qty 0)) 
      (setq name "Top flange")
    );顶法兰
    ( (= i 0)
      (setq name "Bottom flange")
    );塔架底法兰
    ( (= i 1) 
      (setq name "Connection flange Ⅰ")
    );连接法兰一
    ( (= i 2) 
      (setq name "Connection flange Ⅱ")
    );连接法兰二
    ( (= i 3) 
      (setq name "Connection flange Ⅲ")
    );连接法兰三
    ( (= i 4) 
      (setq name "Connection flange Ⅳ")
    );连接法兰四
    ( (= i 5) 
      (setq name "Connection flange Ⅴ")
    );连接法兰五
    ( (= i 6) 
      (setq name "Connection flange Ⅵ")
    );连接法兰六
    ( (= i 7) 
      (setq name "Connection flange Ⅶ")
    );连接法兰七
    (t
      (setq name "Error!")
    )
  )
);End mxb_fl_name_e


;;;;*************************门洞生成并插入*******************************************
(defun jqb_weight(/ doorH D t1 hh1 t2 h2 h1 b1 MassOfRePlate MassOfCyReHole jqbHoleWeight)
  (if (= (ReplateDoor)  T)
    (progn;加强门框
      (setq doorH (value retDoor 0 12));加强门框的高度
      (setq D (value retDoor 0 13);塔筒壁直径
	    t1 (value retDoor 0 14);塔筒壁厚度t1
    	    hh1 (value retDoor 0 15); 补强板高度
	    t2 (value retDoor 0 16);补强板厚度t2
            h2 (value retDoor 0 17);直边长度H_V
	    h1 (value retDoor 0 18);门洞高度H2
	    b1 (value retDoor 0 19);门洞宽度w
      )
      (setq MassOfRePlate (Repdoormass D t1 hh1 t2 h2 h1 b1));;;加强板重量,已经掏空了加强板上的小门洞
      (setq MassOfCyReHole (RepDoorHoleMass D t1 hh1 t2 h2 h1 b1));加强板开的洞在筒体上，筒体这一块的重量
    );End progn
    (progn
      (setq MassOfRePlate "Error")
      (setq MassOfCyReHole "Error")
    )
  );End if
  (setq jqbHoleWeight (list MassOfCyReHole MassOfRePlate))
);defun函数的右括号
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


;法兰标题栏插入
(defun c:fl_title_insert(/ tydh i titlename titlename_en titleweight)
  (setq tydh (getstring "\n输入图样代号："))
  (setq i (atoi (getstring "\n输入连接法兰i：")))
  (setq titlename (mxb_fl_name i))
  (setq titlename_en (mxb_fl_name_e i))
  (setq titleweight (rtos (nth i FlangeMassList) 2 1))
  ;块名称，S，比例，坐标，旋转角度,
  ;批准日期，标准化日期，工艺日期，审核日期，校对日期，批准，标准化，工艺，审核，涉及日期，校对，设计
  ;图样代号，名称,Name，材料，Material，重量，比例
  ;drawingnumber,第几页，共几页，sheet，of，版本
  (command "insert" "pc_title_block" "S" 30 '(11700 0) ""
	   "" "" "" "" "" "" "" "" "" "" "" ""
	   tydh titlename titlename_en "Q355NEZ35" "S355NL-Z35" titleweight "1:30"
	   "" "1" "1" "1" "1" ""
	   "")
  ;插入左上角的图样代号
  (command "insert" "PC_TYDH_BLOCK" "S" "30" '(0 8610) ""
	   tydh
	   "")
)
;;;;;END fl_title_insert

;总图标题栏插入
(defun c:tower_title_insert2(/ i titlename titlename_en cailiao chanpindaihao)
  ;(setq qty 4)
  (setq tydh_weld (getstring "\n输入图样代号："))
  (setq hh (getstring "\n输入塔架HH："))
  (setq titlename (getstring "\n输入图样中文名称："))
  (setq titlename_en (strcat (rtos section_qty 2 0) " Sections " hh " m HH Tower")) 
  (setq titleweight all_tower_mass)
  (setq titleweight_str (rtos all_tower_mass 2 0))
  (print mscale)
  (setq title_zb (title_zb mscale))
  (setq tydh_zb (list 0 (* mscale 1169) 0))
  
  (setq title_bl (strcat "1:" (rtos mscale 2 0)))

  (setq cailiao "136-4.8")
  (setq chanpindaihao "")

  (title_zq_block_insert title_zb tydh_weld titlename titlename_en cailiao chanpindaihao titleweight_str title_bl)
  
  ;插入左上角的图样代号
  (command "insert" "PC_TYDH_BLOCK" "S" mscale tydh_zb ""
	   tydh_weld
	   )
);END tower_title_insert

 ;标题栏增强属性块插入
(defun title_zq_block_insert (zb wuliaohao mingcheng emingcheng cailiao chanpindaihao zhongliang bili /
			      titleblock attlist aa12 aa13 aa14 aa15 aa16 aa17 aa18 a00
			      a01 a02 a03 a04 a05 a06 a07 a08 a09 a11 actualdate);坐标，序号，代号，名称，name，数量，备注,notes

  (setq titleblock (vla-InsertBlock myms (vlax-3d-point zb) "pc_title_block" mscale mscale 1 0 ) )
  (setq attlist (vlax-safearray->list (vlax-variant-value (vla-getattributes titleblock))))  



  (setq aa12 (nth 12 attlist))  
  ;(vla-put-textstring aa12 123.1);图样代号
  (vla-put-textstring aa12 wuliaohao);图样代号
  ;(vlax-dump-object aa12 t)
  ;(setq aa12 (vla-put-textstring (nth 12 attlist) wuliaohao));图样代号
  (setq aa13 (vla-put-textstring (nth 13 attlist) mingcheng));中文标题名称
  (setq aa14 (vla-put-textstring (nth 14 attlist) emingcheng));英文标题名称
  (setq aa15 (nth 15 attlist));材料
  (vla-put-textstring aa15 (strcat "        " cailiao));材料
  ;(vla-put-Layer aa15 "3中心线层");
  (vla-put-Alignment aa15 0);对齐方式
  (vla-put-ScaleFactor aa15 0.67);
  (setq aa16 (vla-put-textstring (nth 16 attlist) chanpindaihao));产品代号
  (setq aa17 (vla-put-textstring (nth 17 attlist) zhongliang));重量
  (setq aa18 (vla-put-textstring (nth 18 attlist) bili));比例


  (setq actualdate (getvar "cdate"));当下时间，格式为：20201202.1532106
  (setq actualdate (rtos actualdate 2 0));去除小数点后的

  (setq aa01 (vla-put-textstring (nth 1 attlist) actualdate));标准化后的日期
  (setq aa02 (vla-put-textstring (nth 2 attlist) actualdate));工艺后的日期
  (setq aa03 (vla-put-textstring (nth 3 attlist) actualdate));审核后的日期
  (setq aa04 (vla-put-textstring (nth 4 attlist) actualdate));校对后的日期
  (setq aa05 (vla-put-textstring (nth 5 attlist) "xxx"));批准
  (setq aa06 (vla-put-textstring (nth 6 attlist) "xxx"));标准化
  (setq aa07 (vla-put-textstring (nth 7 attlist) "xxx"));工艺
  (setq aa08 (vla-put-textstring (nth 8 attlist) "xxx"));审核
  (setq aa09 (vla-put-textstring (nth 9 attlist) actualdate));设计后的日期
  (setq aa10 (vla-put-textstring (nth 10 attlist) "xxx"));校对
  (setq aa11 (vla-put-textstring (nth 11 attlist) "xxx"));设计
  (setq aa00 (vla-put-textstring (nth 0 attlist) actualdate));批准后的日期
  
  
  ;(vlax-dump-object aa15 t)


  ;(setq js 0)
  ;(while (< js 50)
  ;  (setq aa3 (vla-put-textstring (nth js attlist) (rtos js)))
  ;  (setq js (+ js 1))
  ;)


)
;;;End defun


; the function is used to save the drawing which is drawed by "tower" function areadly.
; the saving path is the towerdata's path
; author : Cao Xudong
; date: 20180426

(defun c:sd()
  (setq save_dir (GetDir Towerdat_file))
  (command "save" (strcat save_dir "dwg"))
  (princ)
)

(defun GetDir(excel_file)
  (substr excel_file 1(-(strlen excel_file)4))
  )

  



;;;;;;;;手工一键出总图-曹学敏新增

;;;;;;;普通招标图序号的插入点;;;;;;;;
(defun yjzb_xhKeypt(/ i pt pt0 pt1 pt2 ptlist)
  (setq ptlist '())
  (setq pt0 (nth 1 sthptlist));法兰最下点
  (setq pt1 (polar pt0 (/ pi 2) 200))
  (setq pt1 (polar pt1 0 400));爬梯
  (setq ptlist (append ptlist (list pt1)))

  
  (setq pt2 (polar pa (/ pi 2) (+ (DoorHeight) 3300)))
  (setq pt2 (polar pt2 (/ pi 2) 1000))
  (setq pt2 (polar pt2 pi (+ 530 (* 500 1.5))))
  ;(setq ptlist (list pt1 pt1_p pt2))
  (setq ptlist (append ptlist (list pt2)))
  
  (setq i 0)
  (while (< i section_qty)
    (if (= i 0)
      (setq pt (MiddlePoint (nth (- (nth 1 pos) 3) sthptlist) (nth (nth 1 pos) sthptlist)));第一段附件总成的点
      (setq pt (nth (- (nth (+ i 1) pos) 3) sthptlist) )
    )
    (setq ptlist (append ptlist (list pt)))
    (setq i (+ i 1))
  )
  ;附件总成的坐标
  ;(setq pt3 (nth (- (nth section_qty pos) 2) sthptlist) )
  

  (setq oript3 (bnth -1 sthptlist))
  (setq oript3 (polar oript3 (* pi 1.5) 8100))
  (setq pt3 (polar oript3 pi 800))


  (setq ptlist (append ptlist (list pt3)))
  (setq ptlist ptlist)
)
;;;;;End xheKeypt;;;;;;;

;;;;;;;一键手工出总图的序号的插入点;;;;;;;;;
(defun yjzt_xhKeypt(/ ptlist pt0 pt1 pt2 i pt_weld pt_asm pt3 pt4 pt5 pt6)
  (setq ptlist '())
  (setq pt0 (nth 0 sthptlist));法兰最下点
  (setq pt1 (polar pt0 (/ pi 2) 950))
  ;(setq pt1 (polar pt1 0 400));爬梯
  
  (setq ptlist (append ptlist (list pt1)))
  
  ;升降机
  (setq pt2 (polar pa (/ pi 2) (+ (DoorHeight) 3300)))
  (setq pt2 (polar pt2 (/ pi 2) 600))
  ;(setq pt2 (polar pt2 pi (+ 530 (* 500 1.5))))
  ;(setq ptlist (list pt1 pt1_p pt2))
  (setq ptlist (append ptlist (list pt2)))

  ;主体焊合+附件
  (setq i 0)
  (while (< i section_qty)
    (if (= i 0)
      (progn
	    (setq pt_weld (MiddlePoint (nth (- (nth 1 pos) 3) sthptlist) (nth (- (nth 1 pos) 4) sthptlist)));第一段焊合
        (setq pt_asm (nth (- (nth (+ i 1) pos) 3) sthptlist));第一段附件总成的点
		(setq pt_asm (polar pt_asm (/ pi 2) 500))
      )
      (progn
	    (setq pt_weld (MiddlePoint (nth (- (nth (+ i 1) pos) 4) sthptlist) (nth (- (nth (+ i 1) pos) 5) sthptlist)))
        (setq pt_asm (nth (- (nth (+ i 1) pos) 3) sthptlist))
		(setq pt_asm (polar pt_asm (/ pi 2) 800))
      )
    )
    (setq ptlist (append ptlist (list pt_weld pt_asm)))
    (setq i (+ i 1))
  )
  
  ;电缆线夹
  (setq pt3 (MiddlePoint(nth (+ (nth (- section_qty 1) pos) 3) sthptlist) (nth (+ (nth (- section_qty 1) pos) 4) sthptlist)))
  (setq pt3 (polar pt3 pi 1800))
  (setq ptlist (append ptlist (list pt3)))
  
  ;顶段和中间的焊合通用图
  (setq pt4 (MiddlePoint(nth (- (nth (- section_qty 1) pos) 3) sthptlist) (nth (- (nth (- section_qty 1) pos) 4) sthptlist)))
  (setq pt4 (polar pt4 pi 1800))
  (setq ptlist (append ptlist (list pt4)))
  
  ;法兰通用图
  (setq pt5 (nth (nth (- section_qty 2) pos) sthptlist))
  (setq pt5 (polar pt5 pi 1800))
  (setq ptlist (append ptlist (list pt5)))
  
  ;第一段焊合通用图
  (setq pt6 (MiddlePoint (nth (- (nth 1 pos) 3) sthptlist) (nth (- (nth 1 pos) 4) sthptlist)))
  (setq pt6 (polar pt6 pi 1800))
  (setq ptlist (append ptlist (list pt6)))
  
  (setq ptlist ptlist)
)
;;;;;End yjzt_xhKeypt;;;;;;;

;;;一键手工出总图附件块插入
(defun yjzt_asm_block_ins(/ oript oript1 i pt oript2 oript3 radius length1 length2 rug_pt oript4)
  (setq oript (nth 0 sthptlist))
  
  ;升降机插入
  (setq scalefactor 1.5)
  (setq oript1 (polar pa (/ pi 2) (+ (DoorHeight) 3300)))
  ;(setq oript1 (polar oript1 pi (+ 530 (* 500 scalefactor))))
  
  ;(setq oript1 (MiddlePoint (nth 0 sthptlist) (nth (nth 1 pos) sthptlist)))
  ;(setq oript1 (polar oript1 pi 500))
  (command "insert" "lift" "S" 1.5 oript1 "");插入升降机块
  ;(print (nth (- section_qty 1) pos))
  
  ;附件总成插入
  (setq i 0)
  (while (< i section_qty)
    (if (= i 0);第一段
      (progn (setq pt (nth (- (nth 1 pos) 3) sthptlist)) (setq pt (polar pt (/ pi 2) 500)));第一段
      (progn (setq pt (nth (- (nth (+ i 1) pos) 3) sthptlist)) (setq pt (polar pt (/ pi 2) 800)))
    )
    (command "insert" "acc_tower" "S" 1 pt "");插入代表附件总成的块
    (setq i (+ i 1))
  )
  
  ;阻尼器插入
  (if (> (- (value retFlange section_qty 0) (value retFlange 0 0)) 125) 
    (progn 
      (setq oript2 (MiddlePoint(nth (- (nth (- section_qty 2) pos) 1) sthptlist) (nth (- (nth (- section_qty 2) pos) 2) sthptlist)))
      (command "insert" "TLD" "S" 1.5 oript2 "")
	)
    ;(setq oript3 (bnth -1 sthptlist))
    ;(setq oript3 (polar oript3 (* pi 1.5) 8100))
    ;(setq oript3 (polar oript3 pi 800))
    ;(command "insert" "Deflection_Roller" "S" 1 oript3 "");插入顶部附件块
  );end if
  
  ;电缆线夹插入
  (setq oript3 (MiddlePoint(nth (+ (nth (- section_qty 1) pos) 3) sthptlist) (nth (+ (nth (- section_qty 1) pos) 4) sthptlist)))
  (setq oript3 (polar oript3 pi 1800))
  (command "insert" "clamp" "S" 1 oript3 "")
  ; ; V12电缆线夹规格表插入
  ; (if (= topfltype "V12_TopFlange")
	  ; (progn
		 ; (cond
             ; ((= mscale 100)
              ; (setq oript4 '(37580 24000 0))
             ; )
             ; ((= mscale 120)
              ; (setq oript4 '(47080 47200 0))
             ; )
             ; ((= mscale 140)
              ; (setq oript4 '(52680 33600 0))
             ; )
             ; (t
              ; (setq oript4 '(52000 25000 0))
             ; )
          ; );cond
		  ; (print mscale)
		  ; (print oript4)
		  ; (if (and (/= (value retDescription 0 1) nil) (/= (value retDescription 0 1) ""))
	          ; (setq Power (value retDescription 0 1))
		      ; (setq Power (atof(getstring "请输入功率(MW)：")))  
	      ; );end if
		  ; (print Power)
	      ; (if (< Power 6.0)
		     ; (command "insert" "ClampTable_1" "S" 1 oript4 "")
			 ; (command "insert" "ClampTable_2" "S" 1 oript4 "")
		 ; );end if
	  ; );end progn
   ; );end if
  ;V12入口梯支耳及电缆夹规格表插入
  (if (or(= topfltype "V12_TopFlange")(= topfltype "V12_TopFlange_250"))
	  (progn
	    ;加强板放大视图正视图插入入口梯支耳
	    (command "insert" "V12Lug_1" "S" 1 pt_btm "")
		;加强板放大视图俯视图插入入口梯支耳
		(setq radius (+ (- (value retDoor 0 13) (value retDoor 0 14)) (value retDoor 0 16));加强板外径的2倍为俯视图的半径，因为放大比例为2
		      length1 1438  ;三角形短边长
			  length2 (sqrt (- (* radius radius) (* length1 length1))) ;三角形长边长
		      rug_pt (polar repdrtopoript (* pi 1.5) length2)
			  rug_pt (polar rug_pt 0 length1);支耳右侧安装点
		)
		(command "insert" "V12Lug_2" "S" 1 rug_pt "")
		(cond
             ((= mscale 100)
              (setq oript4 '(35010 38780 0))
             )
             ((= mscale 120)
              (setq oript4 '(47080 47200 0))
             )
             ((= mscale 140)
              (setq oript4 '(57180 50100 0))
             )
             (t
              (setq oript4 '(59000 65000 0))
             )
	    )
		  (if (< Power 6.00)
		     (command "insert" "ClampTable_1" "S" 1 oript4 "")
			 (command "insert" "ClampTable_2" "S" 1 oript4 "")
		  );end if
	  );end progn
   );end if
  
  
)
;;;;;End accessories_insert;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;;;;;;一键手工出总图序号标注，阻尼器+电缆夹+通用图插入;;;;;;;;
(defun yjztxh_insert(xhkeyptlist / i)
  (setq i 0)
  (while (< i (- (length xhkeyptlist) 4));循环到电缆线夹之前为止，然后主体左侧开始标注
    (xhlead (+ i 1) (nth i xhkeyptlist) 5500.0 (/ pi 6))
    (setq i (+ i 1))
  )
  (while (< i (- (length xhkeyptlist) 1));循环到第一段焊合通用图之前为止，主体左侧开始标注
    (xhlead1 (+ i 1) (nth i xhkeyptlist) 7500.0 (* pi 0.07))
    (setq i (+ i 1))
  )
  (if (= TheTowerSliceType "NomalTower")   (xhlead1 (length xhkeyptlist) (nth (- (length xhkeyptlist) 1) xhkeyptlist) 7500.0 (* pi 0.07)) ) ;常规塔标注第一段通用图，分片塔不标注
)
;;;;End yjztxh_insert;;;;;;;

;;;;一键手工出总图中法兰放大图螺栓螺母垫圈的标注
(defun yjzt_boltnutwasher_xh_lead(zb xh_bolt_list / jj pt_fl bolt_code nut_code washer_code)
	(setq jj 0)
	(while (< jj (- section_qty 1))
		(setq pt_fl (nth jj zb))
		(setq bolt_code (nth (+ (* jj 3) 0) xh_bolt_list))
		(setq nut_code (nth (+ (* jj 3) 1) xh_bolt_list))
		(setq washer_code (nth (+ (* jj 3) 2) xh_bolt_list))
		(bolt_xhlead_l bolt_code nut_code washer_code pt_fl 3000 (/ pi 3))
		(setq jj (+ jj 1))
  )
)
;End zt_boltnutwasher_xh_lead

;;;;bolt_judge;;;一键手工出总图-得到法兰螺栓的序号;;;;;
(defun yjbolt_judge (/ xh_body_qty i xh_bolt_list xh_bolt_type_1 xh_nut_type_1 xh_washer_type_1 xh_boltnutwasher_list
		   bolt_d_1 tfl_1 bolt_d_2 tfl_2 xh_bolt_type_2 xh_nut_type_2 xh_washer_type_2
		   xh_bolt_type_2_kh xh_nut_type_2_kh xh_washer_type_2_kh)
  (setq xh_bolt_list '())
  ;(setq xh_bolt_type_1 (+ 3 (* 2 section_qty) 1));最顶法兰的螺栓序号
  ;(setq xh_nut_type_1 (+ 3 (* 2 section_qty) 2));最顶法兰的螺母序号
  ;(setq xh_washer_type_1 (+ 3 (* 2 section_qty) 3));最顶法兰的垫片序号
  (if 
     (= TheTowerSliceType "NomalTower")
     (setq xh_body_qty (length xhkeyptlist)) ;常规塔，序号含第一段焊合通用图
	 (setq xh_body_qty (- (length xhkeyptlist) 1))  ;分片塔，序号不含第一段焊合通用图
  );end if
  
  (setq xh_bolt_type_1 (+ xh_body_qty 1));最顶法兰的螺栓序号
  (setq xh_nut_type_1 (+ xh_bolt_type_1 1));最顶法兰的螺母序号
  (setq xh_washer_type_1 (+ xh_nut_type_1 1));最顶法兰的垫片序号

  
  (setq xh_boltnutwasher_list (list xh_bolt_type_1 xh_nut_type_1 xh_washer_type_1))
  (setq xh_bolt_list (append xh_boltnutwasher_list))
  (setq i (- section_qty 1))
  (while (> i 1)

    (setq bolt_d_1  (value retFlange i 7));法兰表中的顶连接法兰螺栓直径
    (setq tfl_1  (value retFlange i 4));法兰表中顶连接法兰的厚度
    
    (setq bolt_d_2  (value retFlange (- i 1) 7));法兰表中螺栓直径
    (setq tfl_2  (value retFlange (- i 1) 4));法兰表厚度

    (if (> xh_nut_type_1 xh_bolt_type_1);if1
      (progn
        (if (= bolt_d_2 bolt_d_1)
          (progn
	    (if  (= tfl_2 tfl_1)
          (progn ;如果螺栓和上一个螺栓相同，序号相同
            (setq xh_bolt_type_2 xh_bolt_type_1 )
	        (setq xh_nut_type_2 xh_nut_type_1 )
	        (setq xh_washer_type_2 xh_washer_type_1 )
	        ;;;相等的话加上括号
	        (setq xh_bolt_type_2_kh (strcat "(" (rtos xh_bolt_type_2) ")"))
            (setq xh_nut_type_2_kh (strcat "(" (rtos xh_nut_type_2) ")"))
	        (setq xh_washer_type_2_kh (strcat "(" (rtos xh_washer_type_2) ")"))
	        (setq xh_boltnutwasher_list (list xh_bolt_type_2_kh xh_nut_type_2_kh xh_washer_type_2_kh))
	        ;;;;重置
	        (setq xh_bolt_type_1 xh_bolt_type_2 );
	        (setq xh_nut_type_1 xh_nut_type_2 );
	        (setq xh_washer_type_1 xh_washer_type_2 );
	      )
	      (progn ;如果螺母等相同，螺栓长度不同
            (setq xh_bolt_type_2 (+ xh_bolt_type_1 3) );
	        (setq xh_nut_type_2 xh_nut_type_1 )
	        (setq xh_washer_type_2 xh_washer_type_1 )
	        (setq xh_nut_type_2_kh (strcat "(" (rtos xh_nut_type_2) ")"))
                (setq xh_washer_type_2_kh (strcat "(" (rtos xh_washer_type_2) ")"))
	        (setq xh_boltnutwasher_list (list xh_bolt_type_2 xh_nut_type_2_kh xh_washer_type_2_kh))
	        ;;;;重置
	        (setq xh_bolt_type_1 xh_bolt_type_2 );
	        (setq xh_nut_type_1 xh_nut_type_2 );
	        (setq xh_washer_type_1 xh_washer_type_2 );
	      )
	    );End if
          )
          (progn ;如果都不相同
	    (if (= i (- section_qty 1))
	      (progn ;如果是第一次循环,螺栓都相同
            (setq xh_bolt_type_2 (+ xh_bolt_type_1 3) );
	        (setq xh_nut_type_2 (+ xh_nut_type_1 3) );
	        (setq xh_washer_type_2 (+ xh_washer_type_1 3) );
	        (setq xh_boltnutwasher_list (list xh_bolt_type_2 xh_nut_type_2 xh_washer_type_2))
	        ;;;;重置
	        (setq xh_bolt_type_1 xh_bolt_type_2 );
	        (setq xh_nut_type_1 xh_nut_type_2 );
	        (setq xh_washer_type_1 xh_washer_type_2 );
	      )
	      (progn
	        (setq xh_bolt_type_2 (+ xh_bolt_type_1 3) );
	        (setq xh_nut_type_2 (+ xh_nut_type_1 3) );
	        (setq xh_washer_type_2 (+ xh_washer_type_1 3) );
	        (setq xh_boltnutwasher_list (list xh_bolt_type_2 xh_nut_type_2 xh_washer_type_2))
	        ;;;;重置
	        (setq xh_bolt_type_1 xh_bolt_type_2 );
	        (setq xh_nut_type_1 xh_nut_type_2 );
	        (setq xh_washer_type_1 xh_washer_type_2 );
	      )
	    );End if
          );End progn
        );eND IF
      );progn
      (progn ;如果上一个螺母与上上一个螺母相等，判断这一个和上一个
        (if (= bolt_d_2 bolt_d_1)
          (progn
	    (if  (= tfl_2 tfl_1)
              (progn ;如果螺栓和上一个螺栓相同，序号相同
            (setq xh_bolt_type_2 xh_bolt_type_1 )
	        (setq xh_nut_type_2 xh_nut_type_1 )
	        (setq xh_washer_type_2 xh_washer_type_1 )
	        ;;;相等的话加上括号
	        (setq xh_bolt_type_2_kh (strcat "(" (rtos xh_bolt_type_2) ")"))
                (setq xh_nut_type_2_kh (strcat "(" (rtos xh_nut_type_2) ")"))
	        (setq xh_washer_type_2_kh (strcat "(" (rtos xh_washer_type_2) ")"))
	        (setq xh_boltnutwasher_list (list xh_bolt_type_2_kh xh_nut_type_2_kh xh_washer_type_2_kh))
	        ;;;;重置
	        (setq xh_bolt_type_1 xh_bolt_type_2 );
	        (setq xh_nut_type_1 xh_nut_type_2 );
	        (setq xh_washer_type_1 xh_washer_type_2 );
	      )
	      (progn ;如果螺母等相同，螺栓长度不同
                (setq xh_bolt_type_2 (+ xh_bolt_type_1 1) );
	        (setq xh_nut_type_2 xh_nut_type_1 )
	        (setq xh_washer_type_2 xh_washer_type_1 )
	        (setq xh_nut_type_2_kh (strcat "(" (rtos xh_nut_type_2) ")"))
                (setq xh_washer_type_2_kh (strcat "(" (rtos xh_washer_type_2) ")"))
	        (setq xh_boltnutwasher_list (list xh_bolt_type_2 xh_nut_type_2_kh xh_washer_type_2_kh))
	        ;;;;重置
	        (setq xh_bolt_type_1 xh_bolt_type_2 );
	        (setq xh_nut_type_1 xh_nut_type_2 );
	        (setq xh_washer_type_1 xh_washer_type_2 );
	      )
	    );End if
          )
          (progn ;如果都不相同
	    ;(if (= i (- section_qty 1))
	    ;  (progn;如果是第一次循环,螺栓都相同
            ;    (setq xh_bolt_type_2 (+ xh_bolt_type_1 3) );
	    ;    (setq xh_nut_type_2 (+ xh_nut_type_1 3) );
	    ;    (setq xh_washer_type_2 (+ xh_washer_type_1 3) );
	    ;    (setq xh_boltnutwasher_list (list xh_bolt_type_2 xh_nut_type_2 xh_washer_type_2))
	    ;    ;;;;重置
	    ;    (setq xh_bolt_type_1 xh_bolt_type_2 );
	    ;    (setq xh_nut_type_1 xh_nut_type_2 );
	    ;    (setq xh_washer_type_1 xh_washer_type_2 );
	    ;  )
	    ;  (progn
	        (setq xh_bolt_type_2 (+ xh_bolt_type_1 1) );
	        (setq xh_nut_type_2 (+ xh_bolt_type_2 1) );
	        (setq xh_washer_type_2 (+ xh_nut_type_2 1) );
	        (setq xh_boltnutwasher_list (list xh_bolt_type_2 xh_nut_type_2 xh_washer_type_2))
	        ;;;;重置
	        (setq xh_bolt_type_1 xh_bolt_type_2 );
	        (setq xh_nut_type_1 xh_nut_type_2 );
	        (setq xh_washer_type_1 xh_washer_type_2 );
	    ;  )
	    ;);End if
          );End progn
        );eND IF

      );End progn
    );End if1
  
    (setq xh_bolt_list (append xh_bolt_list xh_boltnutwasher_list))
    ;重置到上一个
    
    (setq i (- i 1))
  );eND WHILE
  (setq xh_bolt_list xh_bolt_list)
  
)
;;;;End bolt_judge
 
























 







