# -*- coding: utf-8 -*-
"""
patch_v15.py —— 塔架中段本体 v14.0 -> v15.0
依用户逐条决策执行（P0-1 ~ P2）。
用法: python patch_v15.py
"""
import io, os, sys

OWL = os.path.join(os.path.dirname(os.path.abspath(__file__)),
                   "..", "..", "ontology", "TowerMidSection.owl")
OWL = os.path.normpath(OWL)

with io.open(OWL, "r", encoding="utf-8") as f:
    s = f.read()

orig_len = len(s)
log = []


def rep(old, new, cnt=1, tag=""):
    global s
    n = s.count(old)
    if n != cnt:
        raise SystemExit("[FAIL] %s: 期望 %d 处，实际 %d 处\n---\n%s\n---" % (tag, cnt, n, old[:300]))
    s = s.replace(old, new)
    log.append("OK  %-8s (%d 处)" % (tag, cnt))


def dele(old, tag=""):
    rep(old, "", 1, tag)


# ================================================================
# P0-4 + 版本号：本体总注释与 versionInfo
# ================================================================
rep(
    "保留抽象父类 :塔架 与 :塔架段。**逆属性约定**：仅组成关系（:有X）建逆属性，固定/安装/连接/位置/映射类关系不建逆属性（对称属性 :相邻于 自带逆关系）。",
    "保留抽象父类 :塔架 与 :塔架段。"
    "v15.0 依专家审视报告与业务决策修正：**P0-1** :塔架段 改为 :塔架 子类（rdfs:subClassOf），并从顶层物理对象不相交组移出；"
    "**P0-2** 新增顶层类 :构件，:螺柱/:线槽 由 :附件 子类改挂 :构件 子类（业务确认：附件不包括螺柱，也不包括线槽）；"
    "**P0-3** :是否隔开电缆线夹 标记为待业务确认；"
    "**P1-1** 删除 :直径，几何由 :外径底部/:外径顶部（段级）+ :筒节内径（筒节级）承载；"
    "**P1-2** :环焊缝 连接筒节 由 ≥2 改为精确 =2；"
    "**P1-3** :电缆托架规格 补身份键 owl:hasKey；"
    "**P1-4** 补 3 条有依据约束（:第一附件到底部=980、:最后电缆托架到平台=200、:倒数第二附件到平台 1400~1960）；"
    "**P1-5** :螺柱间距 改名 :灯上螺柱间距，:灯上螺柱最大/最小间距 改名 :灯与灯最大/最小间距；"
    "**P1-6** :标高下/:标高上 注释改为「塔架中段」；"
    "**P1-7** :扶持位置 以流程 9.1 为准 =（中段筒高 − 1250）÷ 2；"
    "**P2** 删除 8 条无依据约束公理（平台宽度/集中荷载/栏杆高度/踢脚板高度、踏级间距/踏面宽度/爬梯净宽/爬梯与塔壁净距）。"
    "**逆属性约定**：仅「组成关系（:有X）」与「双向定位/平齐关系（:平台到筒顶 ↔ :筒顶定位平台、:与…平齐 ↔ :平齐于）」建逆属性；"
    "固定/安装/连接/映射类关系不建逆属性（对称属性 :相邻于 自带逆关系）。",
    1, "P0-4/注释")
rep("<owl:versionInfo>v14.0</owl:versionInfo>",
    "<owl:versionInfo>v15.0</owl:versionInfo>", 1, "版本号")

# ================================================================
# P0-2：新增 :有构件 / :构件属于 对象属性（紧随 :有附件 之后）
# ================================================================
rep(
    """    <owl:ObjectProperty rdf:about="#accessoryBelongsToTowerMidSection">
        <rdfs:label xml:lang="zh">附件属于</rdfs:label>
        <rdfs:comment xml:lang="zh">有附件 的逆属性</rdfs:comment>
    </owl:ObjectProperty>
""",
    """    <owl:ObjectProperty rdf:about="#accessoryBelongsToTowerMidSection">
        <rdfs:label xml:lang="zh">附件属于</rdfs:label>
        <rdfs:comment xml:lang="zh">有附件 的逆属性</rdfs:comment>
    </owl:ObjectProperty>

    <!-- v15.0 新增（P0-2）：构件（附件之外、同样固定在塔筒上的零件，如螺柱、线槽） -->
    <owl:ObjectProperty rdf:about="#hasComponent">
        <rdfs:domain rdf:resource="#TowerMidSection"/>
        <rdfs:range rdf:resource="#Component"/>
        <owl:inverseOf rdf:resource="#componentBelongsToTowerMidSection"/>
        <rdfs:label xml:lang="zh">有构件</rdfs:label>
        <rdfs:comment xml:lang="zh">塔架中段包含构件（螺柱、线槽等，v15.0 依 P0-2 新增，与 :有附件 并列）</rdfs:comment>
    </owl:ObjectProperty>

    <owl:ObjectProperty rdf:about="#componentBelongsToTowerMidSection">
        <rdfs:label xml:lang="zh">构件属于</rdfs:label>
        <rdfs:comment xml:lang="zh">有构件 的逆属性（v15.0 新增）</rdfs:comment>
    </owl:ObjectProperty>
""", 1, "P0-2/属性")

# ================================================================
# P0-2：:固定在塔筒 domain 并集加入 :构件
# ================================================================
rep(
    """                <owl:unionOf rdf:parseType="Collection">
                    <owl:Class rdf:about="#Accessory"/>
                    <owl:Class rdf:about="#Platform"/>
                </owl:unionOf>""",
    """                <owl:unionOf rdf:parseType="Collection">
                    <owl:Class rdf:about="#Accessory"/>
                    <owl:Class rdf:about="#Component"/>
                    <owl:Class rdf:about="#Platform"/>
                </owl:unionOf>""", 1, "P0-2/固定域")
rep("domain 统一为 :附件 ∪ :平台",
    "domain 统一为 :附件 ∪ :构件 ∪ :平台（v15.0 加入 :构件）", 1, "P0-2/固定域注释")

# ================================================================
# P1-1：删除 :直径 数据属性
# ================================================================
dele(
    """    <owl:DatatypeProperty rdf:about="#diameter">
        <rdf:type rdf:resource="http://www.w3.org/2002/07/owl#FunctionalProperty"/>
        <rdfs:domain rdf:resource="#TowerTube"/>
        <rdfs:range rdf:resource="http://www.w3.org/2001/XMLSchema#integer"/>
        <rdfs:label xml:lang="zh">直径</rdfs:label>
        <rdfs:comment xml:lang="zh">塔筒直径，单位 mm</rdfs:comment>
    </owl:DatatypeProperty>

""", "P1-1/删直径")

# ================================================================
# P1-5：灯相关数据属性改名（URI + label + comment）
# ================================================================
rep(
    """    <owl:DatatypeProperty rdf:about="#lightStudMaxSpacing">
        <rdfs:domain rdf:resource="#LightingSystem"/>
        <rdfs:range rdf:resource="http://www.w3.org/2001/XMLSchema#integer"/>
        <rdfs:label xml:lang="zh">灯上螺柱最大间距</rdfs:label>
        <rdfs:comment xml:lang="zh">每个灯的上螺柱最大间距，单位 mm，最大 10000</rdfs:comment>
    </owl:DatatypeProperty>""",
    """    <owl:DatatypeProperty rdf:about="#lightToLightMaxSpacing">
        <rdfs:domain rdf:resource="#LightingSystem"/>
        <rdfs:range rdf:resource="http://www.w3.org/2001/XMLSchema#integer"/>
        <rdfs:label xml:lang="zh">灯与灯最大间距</rdfs:label>
        <rdfs:comment xml:lang="zh">相邻两灯之间的最大间距，单位 mm，最大 10000（依定制化设计流程 5.5；v15.0 依 P1-5 由「灯上螺柱最大间距」改名，原名称与语义不符）</rdfs:comment>
    </owl:DatatypeProperty>""", 1, "P1-5/max")

rep(
    """    <owl:DatatypeProperty rdf:about="#lightStudMinSpacing">
        <rdfs:domain rdf:resource="#LightingSystem"/>
        <rdfs:range rdf:resource="http://www.w3.org/2001/XMLSchema#integer"/>
        <rdfs:label xml:lang="zh">灯上螺柱最小间距</rdfs:label>
        <rdfs:comment xml:lang="zh">每个灯的上螺柱最小间距，单位 mm，最小 5000</rdfs:comment>
    </owl:DatatypeProperty>""",
    """    <owl:DatatypeProperty rdf:about="#lightToLightMinSpacing">
        <rdfs:domain rdf:resource="#LightingSystem"/>
        <rdfs:range rdf:resource="http://www.w3.org/2001/XMLSchema#integer"/>
        <rdfs:label xml:lang="zh">灯与灯最小间距</rdfs:label>
        <rdfs:comment xml:lang="zh">相邻两灯之间的最小间距，单位 mm，最小 5000（依定制化设计流程 5.5；v15.0 依 P1-5 由「灯上螺柱最小间距」改名，原名称与语义不符）</rdfs:comment>
    </owl:DatatypeProperty>""", 1, "P1-5/min")

rep(
    """    <owl:DatatypeProperty rdf:about="#studSpacing">
        <rdf:type rdf:resource="http://www.w3.org/2002/07/owl#FunctionalProperty"/>
        <rdfs:domain rdf:resource="#LightingSystem"/>
        <rdfs:range rdf:resource="http://www.w3.org/2001/XMLSchema#integer"/>
        <rdfs:label xml:lang="zh">螺柱间距</rdfs:label>
        <rdfs:comment xml:lang="zh">螺柱间距，单位 mm，固定值 500</rdfs:comment>
    </owl:DatatypeProperty>""",
    """    <owl:DatatypeProperty rdf:about="#lightStudSpacing">
        <rdf:type rdf:resource="http://www.w3.org/2002/07/owl#FunctionalProperty"/>
        <rdfs:domain rdf:resource="#LightingSystem"/>
        <rdfs:range rdf:resource="http://www.w3.org/2001/XMLSchema#integer"/>
        <rdfs:label xml:lang="zh">灯上螺柱间距</rdfs:label>
        <rdfs:comment xml:lang="zh">同一个灯上两个螺柱之间的上下间距，单位 mm，固定值 500（业务确认：一个灯上有两个螺柱，上下排列，间距 500mm；v15.0 依 P1-5 由「螺柱间距」改名）</rdfs:comment>
    </owl:DatatypeProperty>""", 1, "P1-5/stud")

# ================================================================
# P1-6：:标高下 / :标高上 注释改为「塔架中段」
# ================================================================
rep("塔架底部标高，单位 m（场地标高，不与构件尺寸比较）",
    "塔架中段底部标高，单位 m（场地标高，不与构件尺寸比较）（v15.0 依 P1-6 明确为塔架中段）", 1, "P1-6/下")
rep("塔架顶部标高，单位 m（场地标高，不与构件尺寸比较）",
    "塔架中段顶部标高，单位 m（场地标高，不与构件尺寸比较）（v15.0 依 P1-6 明确为塔架中段）", 1, "P1-6/上")

# ================================================================
# P1-7：:扶持位置 注释明确以流程 9.1 为准
# ================================================================
rep("扶持竖向位置，单位 mm，计算式 (中段筒高 − 1250) ÷ 2（计算需外置，结果写回）",
    "扶持竖向位置，单位 mm，计算式 (中段筒高 − 1250) ÷ 2，依定制化设计流程 9.1（计算需外置，结果写回）（v15.0 依 P1-7 明确以流程 9.1 为准）",
    1, "P1-7/扶持位置")

# ================================================================
# P0-3：:是否隔开电缆线夹 标记待业务确认
# ================================================================
rep("该区域电缆线夹是否隔开布置（国内一般隔开，国外不隔开，v11.0 新增）",
    "该区域电缆线夹是否隔开布置。**待业务确认**：原注释「国内一般隔开，国外不隔开」缺乏依据，v15.0 依 P0-3 标记为待业务确认",
    1, "P0-3/隔开线夹")

# ================================================================
# P2：8 条无依据约束公理 —— 删除 Restriction 块 + 修正属性注释
# ================================================================
# -- 平台 4 条 --
dele(
    """        <!-- 平台宽度 ≥ 650mm -->
        <rdfs:subClassOf>
            <owl:Restriction>
                <owl:onProperty rdf:resource="#platformWidth"/>
                <owl:allValuesFrom>
                    <rdfs:Datatype>
                        <owl:onDatatype rdf:resource="http://www.w3.org/2001/XMLSchema#integer"/>
                        <owl:withRestrictions rdf:parseType="Collection">
                            <rdf:Description>
                                <xsd:minInclusive rdf:datatype="http://www.w3.org/2001/XMLSchema#integer">650</xsd:minInclusive>
                            </rdf:Description>
                        </owl:withRestrictions>
                    </rdfs:Datatype>
                </owl:allValuesFrom>
            </owl:Restriction>
        </rdfs:subClassOf>
        <!-- 平台集中荷载 ≥ 1500N -->
        <rdfs:subClassOf>
            <owl:Restriction>
                <owl:onProperty rdf:resource="#platformLoad"/>
                <owl:allValuesFrom>
                    <rdfs:Datatype>
                        <owl:onDatatype rdf:resource="http://www.w3.org/2001/XMLSchema#integer"/>
                        <owl:withRestrictions rdf:parseType="Collection">
                            <rdf:Description>
                                <xsd:minInclusive rdf:datatype="http://www.w3.org/2001/XMLSchema#integer">1500</xsd:minInclusive>
                            </rdf:Description>
                        </owl:withRestrictions>
                    </rdfs:Datatype>
                </owl:allValuesFrom>
            </owl:Restriction>
        </rdfs:subClassOf>
        <!-- 栏杆高度 900~1100mm -->
        <rdfs:subClassOf>
            <owl:Restriction>
                <owl:onProperty rdf:resource="#railingHeight"/>
                <owl:allValuesFrom>
                    <rdfs:Datatype>
                        <owl:onDatatype rdf:resource="http://www.w3.org/2001/XMLSchema#integer"/>
                        <owl:withRestrictions rdf:parseType="Collection">
                            <rdf:Description>
                                <xsd:minInclusive rdf:datatype="http://www.w3.org/2001/XMLSchema#integer">900</xsd:minInclusive>
                            </rdf:Description>
                            <rdf:Description>
                                <xsd:maxInclusive rdf:datatype="http://www.w3.org/2001/XMLSchema#integer">1100</xsd:maxInclusive>
                            </rdf:Description>
                        </owl:withRestrictions>
                    </rdfs:Datatype>
                </owl:allValuesFrom>
            </owl:Restriction>
        </rdfs:subClassOf>
        <!-- 踢脚板高度 ≥ 150mm -->
        <rdfs:subClassOf>
            <owl:Restriction>
                <owl:onProperty rdf:resource="#toeboardHeight"/>
                <owl:allValuesFrom>
                    <rdfs:Datatype>
                        <owl:onDatatype rdf:resource="http://www.w3.org/2001/XMLSchema#integer"/>
                        <owl:withRestrictions rdf:parseType="Collection">
                            <rdf:Description>
                                <xsd:minInclusive rdf:datatype="http://www.w3.org/2001/XMLSchema#integer">150</xsd:minInclusive>
                            </rdf:Description>
                        </owl:withRestrictions>
                    </rdfs:Datatype>
                </owl:allValuesFrom>
            </owl:Restriction>
        </rdfs:subClassOf>
""", "P2/平台4条")

# -- 爬梯 4 条 --
dele(
    """        <!-- 踏级间距 250~300mm -->
        <rdfs:subClassOf>
            <owl:Restriction>
                <owl:onProperty rdf:resource="#rungSpacing"/>
                <owl:allValuesFrom>
                    <rdfs:Datatype>
                        <owl:onDatatype rdf:resource="http://www.w3.org/2001/XMLSchema#integer"/>
                        <owl:withRestrictions rdf:parseType="Collection">
                            <rdf:Description>
                                <xsd:minInclusive rdf:datatype="http://www.w3.org/2001/XMLSchema#integer">250</xsd:minInclusive>
                            </rdf:Description>
                            <rdf:Description>
                                <xsd:maxInclusive rdf:datatype="http://www.w3.org/2001/XMLSchema#integer">300</xsd:maxInclusive>
                            </rdf:Description>
                        </owl:withRestrictions>
                    </rdfs:Datatype>
                </owl:allValuesFrom>
            </owl:Restriction>
        </rdfs:subClassOf>
        <!-- 踏面宽度 ≥ 80mm -->
        <rdfs:subClassOf>
            <owl:Restriction>
                <owl:onProperty rdf:resource="#treadWidth"/>
                <owl:allValuesFrom>
                    <rdfs:Datatype>
                        <owl:onDatatype rdf:resource="http://www.w3.org/2001/XMLSchema#integer"/>
                        <owl:withRestrictions rdf:parseType="Collection">
                            <rdf:Description>
                                <xsd:minInclusive rdf:datatype="http://www.w3.org/2001/XMLSchema#integer">80</xsd:minInclusive>
                            </rdf:Description>
                        </owl:withRestrictions>
                    </rdfs:Datatype>
                </owl:allValuesFrom>
            </owl:Restriction>
        </rdfs:subClassOf>
        <!-- 爬梯净宽 ≥ 340mm -->
        <rdfs:subClassOf>
            <owl:Restriction>
                <owl:onProperty rdf:resource="#ladderClearWidth"/>
                <owl:allValuesFrom>
                    <rdfs:Datatype>
                        <owl:onDatatype rdf:resource="http://www.w3.org/2001/XMLSchema#integer"/>
                        <owl:withRestrictions rdf:parseType="Collection">
                            <rdf:Description>
                                <xsd:minInclusive rdf:datatype="http://www.w3.org/2001/XMLSchema#integer">340</xsd:minInclusive>
                            </rdf:Description>
                        </owl:withRestrictions>
                    </rdfs:Datatype>
                </owl:allValuesFrom>
            </owl:Restriction>
        </rdfs:subClassOf>
        <!-- 爬梯与塔壁净距 ≥ 150mm -->
        <rdfs:subClassOf>
            <owl:Restriction>
                <owl:onProperty rdf:resource="#ladderToWallDistance"/>
                <owl:allValuesFrom>
                    <rdfs:Datatype>
                        <owl:onDatatype rdf:resource="http://www.w3.org/2001/XMLSchema#integer"/>
                        <owl:withRestrictions rdf:parseType="Collection">
                            <rdf:Description>
                                <xsd:minInclusive rdf:datatype="http://www.w3.org/2001/XMLSchema#integer">150</xsd:minInclusive>
                            </rdf:Description>
                        </owl:withRestrictions>
                    </rdfs:Datatype>
                </owl:allValuesFrom>
            </owl:Restriction>
        </rdfs:subClassOf>
""", "P2/爬梯4条")

# -- 属性注释去约束 --
rep("平台宽度，单位 mm，≥ 650（约束见 owl:Restriction）",
    "平台宽度，单位 mm（v15.0 依 P2 移除无依据的 ≥650 约束，待业务确认）", 1, "P2/注释1")
rep("平台集中荷载，单位 N，≥ 1500（约束见 owl:Restriction）",
    "平台集中荷载，单位 N（v15.0 依 P2 移除无依据的 ≥1500 约束，待业务确认）", 1, "P2/注释2")
rep("栏杆高度，单位 mm，允许范围 900~1100（单一实际值 + 范围约束，故不拆分）",
    "栏杆高度，单位 mm（v15.0 依 P2 移除无依据的 900~1100 约束，待业务确认）", 1, "P2/注释3")
rep("踢脚板高度，单位 mm，≥ 150（约束见 owl:Restriction）",
    "踢脚板高度，单位 mm（v15.0 依 P2 移除无依据的 ≥150 约束，待业务确认）", 1, "P2/注释4")
rep("踏级间距，单位 mm，允许范围 250~300（约束见 owl:Restriction）",
    "踏级间距，单位 mm（v15.0 依 P2 移除无依据的 250~300 约束，待业务确认）", 1, "P2/注释5")
rep("踏面宽度，单位 mm，≥ 80",
    "踏面宽度，单位 mm（v15.0 依 P2 移除无依据的 ≥80 约束，待业务确认）", 1, "P2/注释6")
rep("爬梯净宽，单位 mm，≥ 340",
    "爬梯净宽，单位 mm（v15.0 依 P2 移除无依据的 ≥340 约束，待业务确认）", 1, "P2/注释7")
rep("爬梯与塔壁净距，单位 mm，≥ 150",
    "爬梯与塔壁净距，单位 mm（v15.0 依 P2 移除无依据的 ≥150 约束，待业务确认）", 1, "P2/注释8")

# ================================================================
# 类定义区
# ================================================================
# P0-1：:塔架 / :塔架段
rep(
    """    <!-- 顶层类：塔架（抽象父类，v14.0 保留） -->
    <owl:Class rdf:about="#Tower">
        <rdfs:label xml:lang="zh">塔架</rdfs:label>
        <rdfs:comment xml:lang="zh">塔架，独立顶层类。由塔架段组成（本项目仅涉及塔架中段）。v14.0 保留为抽象父类</rdfs:comment>
    </owl:Class>

    <!-- 顶层类：塔架段（抽象父类，v14.0 保留） -->
    <owl:Class rdf:about="#TowerSection">
        <rdfs:label xml:lang="zh">塔架段</rdfs:label>
        <rdfs:comment xml:lang="zh">塔架分段，塔架由塔架段组成（组成关系，非继承关系）</rdfs:comment>
    </owl:Class>""",
    """    <!-- 顶层类：塔架（抽象父类，v15.0 确立为 :塔架段 的父类） -->
    <owl:Class rdf:about="#Tower">
        <rdfs:label xml:lang="zh">塔架</rdfs:label>
        <rdfs:comment xml:lang="zh">塔架，抽象父类。塔架「由」塔架段组成（对象属性 :有塔架段），且塔架段「是一种」塔架（继承关系 :塔架段 ⊂ :塔架）。本项目仅涉及塔架中段。v15.0 依专家审视 P0-1 确立 :塔架段 为 :塔架 子类</rdfs:comment>
    </owl:Class>

    <!-- 塔架段（:塔架 的子类，v15.0 依 P0-1 调整） -->
    <owl:Class rdf:about="#TowerSection">
        <rdfs:subClassOf rdf:resource="#Tower"/>
        <rdfs:label xml:lang="zh">塔架段</rdfs:label>
        <rdfs:comment xml:lang="zh">塔架分段。塔架段「是一种」塔架（rdfs:subClassOf :塔架），同时塔架「由」塔架段组成（对象属性 :有塔架段）。v15.0 依专家审视 P0-1 由独立顶层类改为 :塔架 子类</rdfs:comment>
    </owl:Class>""", 1, "P0-1/塔架段")

# P1-2：环焊缝 连接筒节 精确基数 2
rep("<!-- 完整性校验：环焊缝连接 2 个筒节（v13.0） -->",
    "<!-- 完整性校验：环焊缝恰好连接 2 个筒节（v15.0 依 P1-2 由 ≥2 改为 =2） -->", 1, "P1-2/注释")
rep("""                <owl:minQualifiedCardinality rdf:datatype="http://www.w3.org/2001/XMLSchema#nonNegativeInteger">2</owl:minQualifiedCardinality>
                <owl:onClass rdf:resource="#TubeSection"/>""",
    """                <owl:qualifiedCardinality rdf:datatype="http://www.w3.org/2001/XMLSchema#nonNegativeInteger">2</owl:qualifiedCardinality>
                <owl:onClass rdf:resource="#TubeSection"/>""", 1, "P1-2/基数")

# P1-4：:附件 补 2 条约束
rep(
    """        <!-- 中间附件中心间距 6×280 ~ 7×280（1680~1960mm），依定制化设计流程 5.2 收窄（v12.0） -->
        <rdfs:subClassOf>
            <owl:Restriction>
                <owl:onProperty rdf:resource="#accessoryCenterSpacing"/>
                <owl:allValuesFrom>
                    <rdfs:Datatype>
                        <owl:onDatatype rdf:resource="http://www.w3.org/2001/XMLSchema#integer"/>
                        <owl:withRestrictions rdf:parseType="Collection">
                            <rdf:Description>
                                <xsd:minInclusive rdf:datatype="http://www.w3.org/2001/XMLSchema#integer">1680</xsd:minInclusive>
                            </rdf:Description>
                            <rdf:Description>
                                <xsd:maxInclusive rdf:datatype="http://www.w3.org/2001/XMLSchema#integer">1960</xsd:maxInclusive>
                            </rdf:Description>
                        </owl:withRestrictions>
                    </rdfs:Datatype>
                </owl:allValuesFrom>
            </owl:Restriction>
        </rdfs:subClassOf>
    </owl:Class>""",
    """        <!-- 中间附件中心间距 6×280 ~ 7×280（1680~1960mm），依定制化设计流程 5.2 收窄（v12.0） -->
        <rdfs:subClassOf>
            <owl:Restriction>
                <owl:onProperty rdf:resource="#accessoryCenterSpacing"/>
                <owl:allValuesFrom>
                    <rdfs:Datatype>
                        <owl:onDatatype rdf:resource="http://www.w3.org/2001/XMLSchema#integer"/>
                        <owl:withRestrictions rdf:parseType="Collection">
                            <rdf:Description>
                                <xsd:minInclusive rdf:datatype="http://www.w3.org/2001/XMLSchema#integer">1680</xsd:minInclusive>
                            </rdf:Description>
                            <rdf:Description>
                                <xsd:maxInclusive rdf:datatype="http://www.w3.org/2001/XMLSchema#integer">1960</xsd:maxInclusive>
                            </rdf:Description>
                        </owl:withRestrictions>
                    </rdfs:Datatype>
                </owl:allValuesFrom>
            </owl:Restriction>
        </rdfs:subClassOf>
        <!-- 第一附件到底部 = 980mm（依定制化设计流程 5.3，v15.0 依 P1-4 补） -->
        <rdfs:subClassOf>
            <owl:Restriction>
                <owl:onProperty rdf:resource="#firstAccessoryToBottom"/>
                <owl:hasValue rdf:datatype="http://www.w3.org/2001/XMLSchema#integer">980</owl:hasValue>
            </owl:Restriction>
        </rdfs:subClassOf>
        <!-- 倒数第二附件到平台 5×280 ~ 7×280（1400~1960mm）（依定制化设计流程 5.3，v15.0 依 P1-4 补） -->
        <rdfs:subClassOf>
            <owl:Restriction>
                <owl:onProperty rdf:resource="#secondLastToPlatform"/>
                <owl:allValuesFrom>
                    <rdfs:Datatype>
                        <owl:onDatatype rdf:resource="http://www.w3.org/2001/XMLSchema#integer"/>
                        <owl:withRestrictions rdf:parseType="Collection">
                            <rdf:Description>
                                <xsd:minInclusive rdf:datatype="http://www.w3.org/2001/XMLSchema#integer">1400</xsd:minInclusive>
                            </rdf:Description>
                            <rdf:Description>
                                <xsd:maxInclusive rdf:datatype="http://www.w3.org/2001/XMLSchema#integer">1960</xsd:maxInclusive>
                            </rdf:Description>
                        </owl:withRestrictions>
                    </rdfs:Datatype>
                </owl:allValuesFrom>
            </owl:Restriction>
        </rdfs:subClassOf>
    </owl:Class>""", 1, "P1-4/附件约束")

# P0-2：:线槽 / :螺柱 改挂 :构件
rep(
    """    <owl:Class rdf:about="#CableTray">
        <rdfs:subClassOf rdf:resource="#Accessory"/>
        <rdfs:label xml:lang="zh">线槽</rdfs:label>
        <rdfs:comment xml:lang="zh">电缆线槽，固定在塔筒上</rdfs:comment>
    </owl:Class>

    <owl:Class rdf:about="#Stud">
        <rdfs:subClassOf rdf:resource="#Accessory"/>
        <rdfs:label xml:lang="zh">螺柱</rdfs:label>
        <rdfs:comment xml:lang="zh">灯安装螺柱，焊接在塔筒筒壁上，灯安装其上</rdfs:comment>
    </owl:Class>""",
    """    <owl:Class rdf:about="#CableTray">
        <rdfs:subClassOf rdf:resource="#Component"/>
        <rdfs:label xml:lang="zh">线槽</rdfs:label>
        <rdfs:comment xml:lang="zh">电缆线槽，固定在塔筒上。业务确认（P0-2）：线槽不属于「附件」，v15.0 由 :附件 子类改挂 :构件 子类</rdfs:comment>
    </owl:Class>

    <owl:Class rdf:about="#Stud">
        <rdfs:subClassOf rdf:resource="#Component"/>
        <rdfs:label xml:lang="zh">螺柱</rdfs:label>
        <rdfs:comment xml:lang="zh">灯安装螺柱，焊接在塔筒筒壁上，灯安装其上。业务确认（P0-2）：螺柱不属于「附件」，v15.0 由 :附件 子类改挂 :构件 子类</rdfs:comment>
    </owl:Class>""", 1, "P0-2/改挂")

# P1-4：:电缆托架 补 1 条约束
rep(
    """    <owl:Class rdf:about="#CableBracket">
        <rdfs:subClassOf rdf:resource="#Accessory"/>
        <rdfs:label xml:lang="zh">电缆托架</rdfs:label>
        <rdfs:comment xml:lang="zh">电缆支撑托架，固定在塔筒上</rdfs:comment>
    </owl:Class>""",
    """    <owl:Class rdf:about="#CableBracket">
        <rdfs:subClassOf rdf:resource="#Accessory"/>
        <rdfs:label xml:lang="zh">电缆托架</rdfs:label>
        <rdfs:comment xml:lang="zh">电缆支撑托架，固定在塔筒上</rdfs:comment>
        <!-- 最后电缆托架到平台 = 200mm（依定制化设计流程 5.3，v15.0 依 P1-4 补） -->
        <rdfs:subClassOf>
            <owl:Restriction>
                <owl:onProperty rdf:resource="#lastBracketToPlatform"/>
                <owl:hasValue rdf:datatype="http://www.w3.org/2001/XMLSchema#integer">200</owl:hasValue>
            </owl:Restriction>
        </rdfs:subClassOf>
    </owl:Class>""", 1, "P1-4/托架约束")

# P0-2：新增 :构件 顶层类（插在 :机型 之前）
rep(
    """    <!-- 顶层类：机型（v10.0 新增，含身份键） -->""",
    """    <!-- 顶层类：构件（v15.0 新增，依 P0-2：附件不含螺柱与线槽） -->
    <owl:Class rdf:about="#Component">
        <rdfs:label xml:lang="zh">构件</rdfs:label>
        <rdfs:comment xml:lang="zh">塔内构件：同样固定在塔筒上、但不属于「附件」的零件。业务确认（P0-2）：附件不包括螺柱，也不包括线槽，故将 :螺柱、:线槽 由 :附件 子类改挂 :构件 子类（v15.0 新增）</rdfs:comment>
    </owl:Class>

    <!-- 顶层类：机型（v10.0 新增，含身份键） -->""", 1, "P0-2/构件类")

# P1-3：:电缆托架规格 补身份键
rep(
    """    <owl:Class rdf:about="#CableBracketSpec">
        <rdfs:label xml:lang="zh">电缆托架规格</rdfs:label>
        <rdfs:comment xml:lang="zh">电缆托架规格参数集（托架长度、左右安装弦长），随机型变化，作为分类值以个体形式存在（v11.0 新增）</rdfs:comment>
    </owl:Class>""",
    """    <owl:Class rdf:about="#CableBracketSpec">
        <rdfs:label xml:lang="zh">电缆托架规格</rdfs:label>
        <rdfs:comment xml:lang="zh">电缆托架规格参数集（托架长度、左右安装弦长），随机型变化，作为分类值以个体形式存在（v11.0 新增；v15.0 依 P1-3 补身份键 owl:hasKey）</rdfs:comment>
        <owl:hasKey rdf:parseType="Collection">
            <owl:DatatypeProperty rdf:about="#bracketLength"/>
            <owl:DatatypeProperty rdf:about="#bracketRightChord"/>
            <owl:DatatypeProperty rdf:about="#bracketLeftChord"/>
        </owl:hasKey>
    </owl:Class>""", 1, "P1-3/托架规格键")

# ================================================================
# 不相交类声明
# ================================================================
rep(
    """    <owl:AllDisjointClasses>
        <rdfs:comment xml:lang="zh">顶层物理对象类互不相交（v14.0：移除 :风机/:风机机头/:偏航轴承）</rdfs:comment>
        <owl:members rdf:parseType="Collection">
            <owl:Class rdf:about="#Tower"/>
            <owl:Class rdf:about="#TowerSection"/>
            <owl:Class rdf:about="#TowerTube"/>
            <owl:Class rdf:about="#TubeSection"/>
            <owl:Class rdf:about="#WeldSeam"/>
            <owl:Class rdf:about="#Platform"/>
            <owl:Class rdf:about="#Ladder"/>
            <owl:Class rdf:about="#Elevator"/>
            <owl:Class rdf:about="#LightingSystem"/>
            <owl:Class rdf:about="#Light"/>
            <owl:Class rdf:about="#Accessory"/>
            <owl:Class rdf:about="#Flange"/>
            <owl:Class rdf:about="#Cable"/>
        </owl:members>
    </owl:AllDisjointClasses>""",
    """    <owl:AllDisjointClasses>
        <rdfs:comment xml:lang="zh">顶层物理对象类互不相交（v15.0：:塔架段 已改为 :塔架 子类故移出本组；新增 :构件）</rdfs:comment>
        <owl:members rdf:parseType="Collection">
            <owl:Class rdf:about="#Tower"/>
            <owl:Class rdf:about="#TowerTube"/>
            <owl:Class rdf:about="#TubeSection"/>
            <owl:Class rdf:about="#WeldSeam"/>
            <owl:Class rdf:about="#Platform"/>
            <owl:Class rdf:about="#Ladder"/>
            <owl:Class rdf:about="#Elevator"/>
            <owl:Class rdf:about="#LightingSystem"/>
            <owl:Class rdf:about="#Light"/>
            <owl:Class rdf:about="#Accessory"/>
            <owl:Class rdf:about="#Component"/>
            <owl:Class rdf:about="#Flange"/>
            <owl:Class rdf:about="#Cable"/>
        </owl:members>
    </owl:AllDisjointClasses>""", 1, "不相交/顶层")

rep(
    """    <owl:AllDisjointClasses>
        <rdfs:comment xml:lang="zh">附件子类不相交（v13.0：新增 :安全锚点）</rdfs:comment>
        <owl:members rdf:parseType="Collection">
            <owl:Class rdf:about="#LadderSupport"/>
            <owl:Class rdf:about="#CableBracket"/>
            <owl:Class rdf:about="#CableTray"/>
            <owl:Class rdf:about="#Stud"/>
            <owl:Class rdf:about="#Support"/>
            <owl:Class rdf:about="#CableClamp"/>
            <owl:Class rdf:about="#SafetyAnchor"/>
        </owl:members>
    </owl:AllDisjointClasses>""",
    """    <owl:AllDisjointClasses>
        <rdfs:comment xml:lang="zh">附件子类不相交（v15.0：:螺柱/:线槽 已改挂 :构件，移出本组）</rdfs:comment>
        <owl:members rdf:parseType="Collection">
            <owl:Class rdf:about="#LadderSupport"/>
            <owl:Class rdf:about="#CableBracket"/>
            <owl:Class rdf:about="#Support"/>
            <owl:Class rdf:about="#CableClamp"/>
            <owl:Class rdf:about="#SafetyAnchor"/>
        </owl:members>
    </owl:AllDisjointClasses>

    <owl:AllDisjointClasses>
        <rdfs:comment xml:lang="zh">构件子类不相交（v15.0 新增）</rdfs:comment>
        <owl:members rdf:parseType="Collection">
            <owl:Class rdf:about="#Stud"/>
            <owl:Class rdf:about="#CableTray"/>
        </owl:members>
    </owl:AllDisjointClasses>""", 1, "不相交/附件+构件")

# ================================================================
with io.open(OWL, "w", encoding="utf-8") as f:
    f.write(s)

print("\n".join(log))
print("---")
print("原始 %d 字节 -> 现在 %d 字节 (Δ %+d)" % (orig_len, len(s), len(s) - orig_len))
print("DONE")
