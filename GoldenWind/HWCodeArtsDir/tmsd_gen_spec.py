# -*- coding: utf-8 -*-
"""从 TmsdOutputWriter.java 抽取 6 个静态文本块，并结合行模板生成本体「输出规范」片段（RDF/XML）。"""
import re, io, os

JAVA = r"D:/work/Ontology/GoldenWind/OntologyMachine/OntologyFrameworkTMSD/src/main/java/com/ocean/ontologyframework/tmsd/TmsdOutputWriter.java"
SCRATCH = r"D:/work/Ontology/GoldenWind/HWCodeArtsDir"
src = io.open(JAVA, encoding="utf-8").read()


def extract_const(name):
    m = re.search(r'String\s+' + name + r'\s*=\s*', src)
    i = m.end()
    out = []
    while i < len(src):
        ch = src[i]
        if ch == '"':
            j = i + 1; buf = []
            while j < len(src):
                c = src[j]
                if c == '\\':
                    buf.append(src[j:j + 2]); j += 2; continue
                if c == '"':
                    break
                buf.append(c); j += 1
            out.append(''.join(buf)); i = j + 1; continue
        if ch == ';':
            break
        i += 1
    s = ''.join(out)
    return s.replace('\\n', '\n').replace('\\t', '\t').replace('\\"', '"').replace('\\\\', '\\')


BLOCKS = {
    "weightInfo": extract_const("WEIGHT_INFO"),
    "skelName": extract_const("SKEL_NAME"),
    "vflangeInfo": extract_const("VFLANGE_INFO"),
    "vflangeTail": extract_const("VFLANGE_TAIL"),
    "skelComp": extract_const("SKEL_COMP"),
    "driveSize": io.open(os.path.join(SCRATCH, "drive_size.txt"), encoding="utf-8").read(),
}

# (key, scope, template)  —— 模板用 {k}/{i} 表索引，{{value}}/{{note}}/{{case}}/{{section}}/{{version}} 表注入
LINES = [
    # ---- 筒体信息：公共头 ----
    ("towerDelta", "towerInfo", "DELTA={{value}}/*缝隙高度\n"),
    ("towerSecH", "towerInfo", "SEC_H_TOTAL={{value}}/*筒段总高\n"),
    ("towerDaTop", "towerInfo", "DA_TOP={{value}}/*上法兰外径\n"),
    ("towerTflTop", "towerInfo", "TFL_TOP={{value}}/* 上法兰厚\n"),
    ("towerTflBottom", "towerInfo", "TFL_BOTTOM={{value}}/* 下法兰厚\n"),
    ("towerHBottom", "towerInfo", "H_BOTTOM={{value}}/* 下法兰脖子高度\n"),
    ("towerHTop", "towerInfo", "H_TOP={{value}}/* 上法兰脖子高度\n"),
    # ---- 门洞（加强板） ----
    ("doorRTitle", "doorReinforced", "/***************加强板开洞信息*************/\n"),
    ("doorRH", "doorReinforced", "H={{value}}/*门框位置\n"),
    ("doorRAlpha", "doorReinforced", "$α=360-53/*门框角度，与X轴正方向，逆时针\n"),
    ("doorRAlphaFrame", "doorReinforced", "α_frame=60/*门框加强板对应圆心角\n"),
    ("doorRH1", "doorReinforced", "H1_FRAME={{value}}/*补强板高度\n"),
    ("doorRH2", "doorReinforced", "H2_FRAME=200/*补强板展开倒圆角\n"),
    # ---- 门洞（普通） ----
    ("doorNTitle", "doorNormal", "/***************普通门洞信息*************/\n"),
    ("doorNHFrame", "doorNormal", "H_FRAME={{value}}/*门框位置\n"),
    ("doorNAlphaFrame", "doorNormal", "α_frame=360-53/*门框角度，与X轴，逆时针\n"),
    ("doorNH1", "doorNormal", "H1_FRAME={{value}}/*门框开洞高度\n"),
    ("doorNH2", "doorNormal", "H2_FRAME={{value}}/*门洞直边长度\n"),
    ("doorNB1", "doorNormal", "B1_FRAME={{value}}/*门洞宽度\n"),
    # ---- 筒体信息：主体 ----
    ("towerBodyTitle", "towerInfo", "/*********主体参数************\n"),
    ("towerCyT", "towerInfo", "cy{k}_t={{value}}/*筒节{k}壁厚\n"),
    ("towerCyH", "towerInfo", "cy{k}_h={{value}}/*筒节{k}节高\n"),
    ("towerCyDBottom", "towerInfo", "cy{k}_d_bottom={{value}}/*筒节{k}下端直径\n"),
    ("towerCyDDBottom", "towerInfo", "cy{k}_D_bottom={{value}}/*筒节{k}下端直径\n"),
    ("towerCyDTop", "towerInfo", "cy{k}_d_top={{value}}/*筒节{k}上端直径\n"),
    ("towerCyDDTop", "towerInfo", "cy{k}_D_top={{value}}/*筒节{k}上端直径\n"),
    ("towerLExist", "towerInfo", "H{i}_L_Exist = {{value}}{{note}}\n"),
    ("towerBlank", "towerInfo", "\n"),
    ("towerCableExist", "towerInfo", "H{i}_CABLE_Exist = {{value}}{{note}}\n"),
    # ---- 附件信息 ----
    ("attHeader1", "attachment", "/*=====================| 塔架中段附件信息 |=====================*/\n"),
    ("attHeader2", "attachment", "/* 用例：{{case}}  第{{section}}段\n"),
    ("attHeader3", "attachment", "/*   本体：TowerMidSection.owl {{version}}（约束已逐条校验通过）\n"),
    ("attSecTitle", "attachment", "/*---------------------| 筒段 |------------------------------*/\n"),
    ("attSecHTotal", "attachment", "SEC_H_total={{value}}/*筒段总高\n\n"),
    ("attUpperTitle", "attachment", "/*---------------------| 上法兰 |------------------------------*/\n"),
    ("attDaTop", "attachment", "DA_TOP={{value}}/*上法兰外径\n"),
    ("attDiTop", "attachment", "DI_TOP={{value}}/*上法兰内径\n"),
    ("attTflTop", "attachment", "TFL_TOP={{value}}/*上法兰厚\n"),
    ("attSTop", "attachment", "S_TOP={{value}}/*上法兰颈厚\n\n"),
    ("attLowerTitle", "attachment", "/*---------------------| 下法兰 |------------------------------*/\n"),
    ("attDaBottom", "attachment", "DA_BOTTOM={{value}}/*下法兰外径\n"),
    ("attDiBottom", "attachment", "DI_BOTTOM={{value}}/*下法兰内径\n"),
    ("attTflBottom", "attachment", "TFL_BOTTOM={{value}}/*下法兰厚\n"),
    ("attSBottom", "attachment", "S_BOTTOM={{value}}/*下法兰颈厚\n\n"),
    ("attPlatformTitle", "attachment", "/*---------------------| 平台 |------------------------------*/\n"),
    ("attHPlatform", "attachment", "H_platform={{value}}/*平台距离顶法兰距离\n\n"),
    ("attLadderTitle", "attachment", "/*---------------------| 爬梯 |------------------------------*/\n"),
    ("attLadderTop", "attachment", "$H_LADDER_TOP= 0 /*爬梯位置\n"),
    ("attLLadder", "attachment", "L_LADDER={{value}}/*爬梯长度\n"),
    ("attLLadderI", "attachment", "L_LADDER_I ={{value}}/*爬梯支撑长度\n"),
    ("attWLadderI", "attachment", "W_LADDER_I ={{value}}/*爬梯支撑宽度\n"),
    ("attAlpha", "attachment", "Alpha = atan((DA_BOTTOM - DA_TOP) / 2 / SEC_H_TOTAL)\n"),
    ("attHLL", "attachment", "H_L_LADDER = L_LADDER * cos(Alpha)\n"),
    ("attB", "attachment", "b = H_LIGHT2FL + 300 /*B截面高度\n"),
    ("attLadderUp", "attachment", "Ladder_up_circle = DA_TOP - 2 * S_TOP\n"),
    ("attLadderBottom", "attachment", "Ladder_bottom_circle = DA_BOTTOM - 2 * S_BOTTOM\n\n"),
    ("attTrayTitle", "attachment", "/*---------------------| 电缆线槽 |------------------------------*/\n"),
    ("attLC", "attachment", "L_C={{value}}/*电缆线槽长度\n\n"),
    ("attSupportTitle", "attachment", "/*---------------------| 扶持 |------------------------------*/\n"),
    ("attHSupport", "attachment", "H_SUPPORT={{value}}/*扶持高度（直读布局表 (12,n)）\n\n"),
    ("attMidSupportTitle", "attachment", "/*---------------------| 中间段爬梯支撑 |------------------------------*/\n"),
    ("attHL", "attachment", "H{i}_L= {{value}} /*第{i}组爬梯支撑安装高度(距离上一组安装高度)\n"),
    ("attBlank", "attachment", "\n"),
    ("attCableTitle", "attachment", "/*---------------------| 电缆线夹 |------------------------------*/\n"),
    ("attHCable", "attachment", "H{i}_CABLE= {{value}} /*第{i}组电缆线夹安装高度(距离上一组安装高度)\n"),
    ("attCableTopH", "attachment", "Cable_top_h = {{value}}/*最后一组电缆夹板相对于顶法兰上端面\n"),
    ("attLCable", "attachment", "L_CABLE={{value}}/*电缆托架长度\n"),
    ("attL1CableI", "attachment", "L1_CABLE_I={{value}}/*右侧电缆托架安装弦长\n"),
    ("attL2CableI", "attachment", "L2_CABLE_I={{value}}/*左侧电缆托架安装弦长\n"),
    ("attDiCableTop", "attachment", "di_cable_top={{value}}/*平台上方电缆夹板位置处塔筒内径\n"),
    ("attDiCableDown", "attachment", "di_cable_down={{value}}/*下方第一个电缆夹板位置处塔筒内径\n\n"),
    ("attLightTitle", "attachment", "/*---------------------| 照明灯 |------------------------------*/\n"),
    ("attHLight2Fl", "attachment", "H_LIGHT2FL={{value}}/*下灯位置\n"),
    ("attLightBottomCircle", "attachment", "LIGHT_BOTTOM_circle={{value}}/*下灯位置处塔筒内径\n"),
    ("attHLight2Platform", "attachment", "H_LIGHT2PLATFORM={{value}}/*上灯位置\n"),
    ("attLightTopCircle", "attachment", "LIGHT_TOP_circle={{value}}/*上灯位置处塔筒内径\n\n"),
    ("attAnchorTitle", "attachment", "/*---------------------| 爬梯安全锚点 |------------------------------*/\n"),
    ("attHAp", "attachment", "H_AP={{value}}/*爬梯安全锚点安装高度（布局表 (9,n)）\n\n"),
    ("attBcTitle", "attachment", "/*---------------------| B和C 二维视图所需信息 |------------------------------*/\n"),
    ("attDaB", "attachment", "DA_B={{value}}/*B_B视图截面所在外径（命名沿用老应用）\n"),
    ("attDaC", "attachment", "DA_C={{value}}/*C_C视图截面所在外径（命名沿用老应用）\n"),
    ("attLightningTitle", "attachment", "/*---------------------| 防雷螺柱定位 |------------------------------*/\n"),
    ("attBBA", "attachment", "B_B_A={{value}}/*下端防雷螺柱安装角度（距爬梯中心线顺时针，其余 120° 均布）\n"),
    ("attBTA", "attachment", "B_T_A={{value}}/*上端防雷螺柱安装角度（距爬梯中心线顺时针，其余 120° 均布）\n"),
    # ---- 底法兰 ----
    ("bfHeader1", "bottomFlange", "/*=====================| 底法兰信息 |=====================*/\n"),
    ("bfHeader2", "bottomFlange", "/* 用例：{{case}}  底法兰\n"),
    ("bfHeader3", "bottomFlange", "/*   本体：TowerMidSection.owl {{version}}\n"),
    ("bfTTitle", "bottomFlangeT", "/*****塔架底法兰参数****\n"),
    ("bfTDa", "bottomFlangeT", "DA={{value}}/*T型法兰外径（筒壁外径）\n"),
    ("bfTDi", "bottomFlangeT", "DI={{value}}/*T型法兰内径\n"),
    ("bfTDm", "bottomFlangeT", "DM={{value}}/*螺栓分度圆直径\n"),
    ("bfTTfl", "bottomFlangeT", "TFL={{value}}/*法兰厚度\n"),
    ("bfTS", "bottomFlangeT", "S={{value}}/*法兰颈厚\n"),
    ("bfTHTotal", "bottomFlangeT", "H_TOTAL={{value}}/*法兰高\n"),
    ("bfTDhole", "bottomFlangeT", "DHOLE={{value}}/*T型法兰内侧螺栓孔直径\n"),
    ("bfTNInner", "bottomFlangeT", "N_INNER={{value}}/*T型法兰内侧螺栓数\n"),
    ("bfTDaOuter", "bottomFlangeT", "Da_outer={{value}}/*T型法兰外径\n"),
    ("bfTDmOuter", "bottomFlangeT", "Dm_outer={{value}}/*T型法兰外圈分度圆\n"),
    ("bfTDholeOuter", "bottomFlangeT", "dhole_outer={{value}}/*T型法兰外侧螺栓孔直径\n"),
    ("bfTNOuter", "bottomFlangeT", "N_OUTER={{value}}/*T型法兰外侧螺栓数\n"),
    ("bfOTitle", "bottomFlange", "/*****连接法兰0参数****\n"),
    ("bfODa", "bottomFlange", "DA={{value}}/*法兰外径\n"),
    ("bfODi", "bottomFlange", "DI={{value}}/*法兰内径\n"),
    ("bfODm", "bottomFlange", "DM={{value}}/*螺栓分度圆直径\n"),
    ("bfOTfl", "bottomFlange", "TFL={{value}}/*法兰厚度\n"),
    ("bfOS", "bottomFlange", "S={{value}}/*法兰颈厚\n"),
    ("bfOHTotal", "bottomFlange", "H_TOTAL={{value}}/*法兰高\n"),
    ("bfODhole", "bottomFlange", "DHOLE={{value}}/*螺栓孔直径\n"),
    ("bfON", "bottomFlange", "N={{value}}/*螺栓数\n"),
    # ---- 连接法兰 ----
    ("cfHeader1", "connectionFlange", "/*=====================| 连接法兰信息 |=====================*/\n"),
    ("cfHeader2", "connectionFlange", "/* 用例：{{case}}  连接法兰{{i}}\n"),
    ("cfHeader3", "connectionFlange", "/*   本体：TowerMidSection.owl {{version}}\n"),
    ("cfTitle", "connectionFlange", "/*****连接法兰{i}参数****\n"),
    ("cfDa", "connectionFlange", "DA={{value}}/*法兰外径\n"),
    ("cfDi", "connectionFlange", "DI={{value}}/*法兰内径\n"),
    ("cfDm", "connectionFlange", "DM={{value}}/*螺栓分度圆直径\n"),
    ("cfTfl", "connectionFlange", "TFL={{value}}/*法兰厚度\n"),
    ("cfS", "connectionFlange", "S={{value}}/*法兰颈厚\n"),
    ("cfHTotal", "connectionFlange", "H_TOTAL={{value}}/*法兰高\n"),
    ("cfDhole", "connectionFlange", "DHOLE={{value}}/*螺栓孔直径\n"),
    ("cfN", "connectionFlange", "N={{value}}/*螺栓数\n"),
    # ---- 分片段筒体信息 ----
    ("vfTitle", "vflange", "/*********分片塔分缝参数************/\n"),
    ("vfMidDia", "vflange", "/**中径/\n"),
    ("vfDaMidTop", "vflange", "DA_MID_TOP={{value}}/*下中径\n"),
    ("vfDaMidBottom", "vflange", "DA_MID_BOTTOM={{value}}/*上中径\n"),
]

def esc(s):
    return (s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            .replace("\n", "&#10;"))

out = []
out.append("    <!-- ============================================================ -->")
out.append("    <!-- 十二、输出规范（v16.10）：骨架关系式 txt 的变量名/注释/行模板   -->")
out.append("    <!-- 由 TmsdOutputWriter 逐行渲染：本层只承载「行呈现」，值由 Java   -->")
out.append("    <!-- 计算后注入 {{value}}；{k}/{i} 为索引占位符。:OutputBlock 承载整块 -->")
out.append("    <!-- 原样文本（老工具 infoformat.py 的照抄块）。                     -->")
out.append("    <!-- ============================================================ -->")
out.append("")
out.append("    <!-- ==== 输出规范：类 ==== -->")
out.append('    <owl:Class rdf:about="#OutputLine">')
out.append('        <rdfs:label xml:lang="zh">输出行模板</rdfs:label>')
out.append('        <rdfs:comment xml:lang="zh">骨架关系式 txt 的一行模板（变量名 + 尾注释 + 空格标点全在此），{{value}} 由 Java 注入、{k}/{i} 为索引。v16.10 新增</rdfs:comment>')
out.append('    </owl:Class>')
out.append('')
out.append('    <owl:Class rdf:about="#OutputBlock">')
out.append('        <rdfs:label xml:lang="zh">输出文本块</rdfs:label>')
out.append('        <rdfs:comment xml:lang="zh">骨架关系式 txt 的整块原样文本（逐行照抄老工具 infoformat.py 的块，含变量名/值/表达式/注释）。v16.10 新增</rdfs:comment>')
out.append('    </owl:Class>')
out.append('')
out.append("    <!-- ==== 输出规范：数据属性 ==== -->")
out.append('    <owl:DatatypeProperty rdf:about="#outputTemplate">')
out.append('        <rdf:type rdf:resource="http://www.w3.org/2002/07/owl#FunctionalProperty"/>')
out.append('        <rdfs:domain rdf:resource="#OutputLine"/>')
out.append('        <rdfs:range rdf:resource="http://www.w3.org/2001/XMLSchema#string"/>')
out.append('        <rdfs:label xml:lang="zh">输出行模板</rdfs:label>')
out.append('        <rdfs:comment xml:lang="zh">行模板字符串，含 {{value}} 与 {k}/{i} 占位符；输出编码的换行以 &amp;#10; 承载。v16.10 新增</rdfs:comment>')
out.append('    </owl:DatatypeProperty>')
out.append('')
out.append('    <owl:DatatypeProperty rdf:about="#outputText">')
out.append('        <rdf:type rdf:resource="http://www.w3.org/2002/07/owl#FunctionalProperty"/>')
out.append('        <rdfs:domain rdf:resource="#OutputBlock"/>')
out.append('        <rdfs:range rdf:resource="http://www.w3.org/2001/XMLSchema#string"/>')
out.append('        <rdfs:label xml:lang="zh">输出块文本</rdfs:label>')
out.append('        <rdfs:comment xml:lang="zh">整块原样文本（含换行，以 &amp;#10; 承载）。v16.10 新增</rdfs:comment>')
out.append('    </owl:DatatypeProperty>')
out.append('')
out.append('    <owl:DatatypeProperty rdf:about="#outputScope">')
out.append('        <rdf:type rdf:resource="http://www.w3.org/2002/07/owl#FunctionalProperty"/>')
out.append('        <rdfs:domain rdf:resource="#OutputLine"/>')
out.append('        <rdfs:range rdf:resource="http://www.w3.org/2001/XMLSchema#string"/>')
out.append('        <rdfs:label xml:lang="zh">输出适用域</rdfs:label>')
out.append('        <rdfs:comment xml:lang="zh">该行模板适用的渲染域（towerInfo/attachment/bottomFlange/connectionFlange/vflange 等）。v16.10 新增</rdfs:comment>')
out.append('    </owl:DatatypeProperty>')
out.append('')
out.append("    <!-- ==== 输出规范：行模板个体 ==== -->")
for key, scope, tpl in LINES:
    out.append('    <owl:NamedIndividual rdf:about="#%s">' % key)
    out.append('        <rdf:type rdf:resource="#OutputLine"/>')
    out.append('        <outputTemplate rdf:datatype="http://www.w3.org/2001/XMLSchema#string">%s</outputTemplate>' % esc(tpl))
    out.append('        <outputScope rdf:datatype="http://www.w3.org/2001/XMLSchema#string">%s</outputScope>' % scope)
    out.append('    </owl:NamedIndividual>')
out.append('')
out.append("    <!-- ==== 输出规范：文本块个体（原样照抄老工具 infoformat.py） ==== -->")
order = ["weightInfo", "skelName", "driveSize", "skelComp", "vflangeInfo", "vflangeTail"]
for key in order:
    out.append('    <owl:NamedIndividual rdf:about="#%s">' % key)
    out.append('        <rdf:type rdf:resource="#OutputBlock"/>')
    out.append('        <outputText rdf:datatype="http://www.w3.org/2001/XMLSchema#string">%s</outputText>' % esc(BLOCKS[key]))
    out.append('    </owl:NamedIndividual>')
out.append('')

frag = "\n".join(out) + "\n"
io.open(os.path.join(SCRATCH, "output_spec_fragment.xml"), "w", encoding="utf-8", newline="").write(frag)
print("lines=%d blocks=%d frag_bytes=%d" % (len(LINES), len(order), len(frag.encode("utf-8"))))
