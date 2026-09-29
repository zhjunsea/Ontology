# -*- coding: utf-8 -*-
"""v15.8：分类值类改为物理对象的字符串数据属性 + 删除电缆托架规格类。

用户最新决策：
  - :附件连接方式 作为 :附件 的数据属性
  - :附件类型     作为 :附件 的数据属性（AskUserQuestion 确认）
  - :灯类型       作为 :灯   的数据属性（AskUserQuestion 确认）
  - :电缆托架规格 类删除，:托架长度/:托架右弦长/:托架左弦长 直挂 :电缆托架

改动（本体）：
  A. 删除对象属性：:有连接方式/:连接方式属于、:有灯类型/:灯类型属于、
                   :选用附件类型/:附件类型适用于、:有电缆托架规格/:托架规格属于
  B. 删除数据属性：:连接方式名称、:灯类型名称
     新建数据属性（xsd:string + FunctionalProperty）：
       :附件连接方式（域 :附件）、:附件类型（域 :附件）、:灯类型（域 :灯）
  C. 删除类：:电缆托架规格、:附件连接方式、:附件类型、:灯类型
  D. :托架长度/:托架右弦长/:托架左弦长 域由 :电缆托架规格 改挂 :电缆托架
  E. 分类值不相交组移出上述 4 类（仅剩 :机型/:区域）
  F. 删除 2 个分类值个体（粘贴式/焊接式）
  G. 版本号 v15.7 → v15.8 + 头部注释追加

范围：ontology/TowerMidSection.owl（BPMN/UI 无相关引用）
"""
import io
import os
import sys

ROOT = r"D:\work\Ontology\GoldenWind"
owl_p = os.path.join(ROOT, "ontology", "TowerMidSection.owl")


def load(p):
    with io.open(p, "r", encoding="utf-8") as f:
        return f.read()


def save(p, s):
    with io.open(p, "w", encoding="utf-8", newline="") as f:
        f.write(s)


def rep(s, old, new, tag):
    n = s.count(old)
    if n != 1:
        print("[FAIL] %s : 命中 %d 次（应为 1）" % (tag, n))
        sys.exit(1)
    print("[OK] %s" % tag)
    return s.replace(old, new)


def dele(s, old, tag):
    return rep(s, old, "", tag)


s = load(owl_p)

# ============================================================
# A. 删除对象属性（连接方式 / 灯类型 / 附件类型 / 托架规格）
# ============================================================

# A1：:有连接方式 + :连接方式属于（含尾部空行）
s = dele(
    s,
    """    <owl:ObjectProperty rdf:about="#hasConnectionType">
        <rdfs:domain>
            <owl:Class>
                <owl:unionOf rdf:parseType="Collection">
                    <owl:Class rdf:about="#LadderSupport"/>
                    <owl:Class rdf:about="#CableBracket"/>
                </owl:unionOf>
            </owl:Class>
        </rdfs:domain>
        <rdfs:range rdf:resource="#AccessoryConnectionType"/>
        <owl:inverseOf rdf:resource="#connectionTypeBelongsToAccessory"/>
        <rdfs:label xml:lang="zh">有连接方式</rdfs:label>
        <rdfs:comment xml:lang="zh">附件的安装方式。主要用于 :爬梯支撑 与 :电缆托架——二者如何安装在塔筒上（粘贴式 / 焊接式），不同方式选用附件不同（定制化设计流程步骤2匹配维度、步骤8替换附件类型依据，v12.0 新增）</rdfs:comment>
    </owl:ObjectProperty>

    <owl:ObjectProperty rdf:about="#connectionTypeBelongsToAccessory">
        <rdfs:label xml:lang="zh">连接方式属于</rdfs:label>
        <rdfs:comment xml:lang="zh">有连接方式 的逆属性（v12.0 新增）</rdfs:comment>
    </owl:ObjectProperty>

    <owl:ObjectProperty rdf:about="#hasLightType">
        <rdfs:domain rdf:resource="#Light"/>
        <rdfs:range rdf:resource="#LightType"/>
        <owl:inverseOf rdf:resource="#lightTypeBelongsToLight"/>
        <rdfs:label xml:lang="zh">有灯类型</rdfs:label>
        <rdfs:comment xml:lang="zh">灯的类型，由「机型 + 区域」确定（定制化设计流程 5.4，v12.0 新增）</rdfs:comment>
    </owl:ObjectProperty>

    <owl:ObjectProperty rdf:about="#lightTypeBelongsToLight">
        <rdfs:label xml:lang="zh">灯类型属于</rdfs:label>
        <rdfs:comment xml:lang="zh">有灯类型 的逆属性（v12.0 新增）</rdfs:comment>
    </owl:ObjectProperty>

""",
    "A1 删除 :有连接方式/:连接方式属于/:有灯类型/:灯类型属于",
)

# A2：:选用附件类型 + :附件类型适用于
s = dele(
    s,
    """    <owl:ObjectProperty rdf:about="#selectsAccessoryType">
        <rdfs:domain rdf:resource="#AccessoryConnectionType"/>
        <rdfs:range rdf:resource="#AccessoryType"/>
        <owl:inverseOf rdf:resource="#accessoryTypeUsedByConnectionType"/>
        <rdfs:label xml:lang="zh">选用附件类型</rdfs:label>
        <rdfs:comment xml:lang="zh">该连接方式所选用的附件类型（粘贴式与焊接式选用附件不同，对应步骤8.1「替换附件类型」，v12.0 新增）</rdfs:comment>
    </owl:ObjectProperty>

    <owl:ObjectProperty rdf:about="#accessoryTypeUsedByConnectionType">
        <rdfs:label xml:lang="zh">附件类型适用于</rdfs:label>
        <rdfs:comment xml:lang="zh">选用附件类型 的逆属性（v12.0 新增）</rdfs:comment>
    </owl:ObjectProperty>

""",
    "A2 删除 :选用附件类型/:附件类型适用于",
)

# A3：:有电缆托架规格 + :托架规格属于（含前置注释行）
s = dele(
    s,
    """    <!-- 机型有电缆托架规格（承载 V12/V15/V17/V19 的托架长度与左右弦长） -->
    <owl:ObjectProperty rdf:about="#hasBracketSpec">
        <rdfs:domain rdf:resource="#Model"/>
        <rdfs:range rdf:resource="#CableBracketSpec"/>
        <owl:inverseOf rdf:resource="#bracketSpecBelongsToModel"/>
        <rdfs:label xml:lang="zh">有电缆托架规格</rdfs:label>
        <rdfs:comment xml:lang="zh">机型对应的电缆托架规格（托架长度、左右安装弦长，v11.0 新增）</rdfs:comment>
    </owl:ObjectProperty>

    <owl:ObjectProperty rdf:about="#bracketSpecBelongsToModel">
        <rdfs:label xml:lang="zh">托架规格属于</rdfs:label>
        <rdfs:comment xml:lang="zh">有电缆托架规格 的逆属性</rdfs:comment>
    </owl:ObjectProperty>

""",
    "A3 删除 :有电缆托架规格/:托架规格属于",
)

# A4：更新「五之二」章节注释头（连接方式/灯类型 已改为数据属性）
s = rep(
    s,
    """    <!-- 五之二、对象属性 —— 连接方式 / 灯类型 / 平齐 / 同侧（v12.0 新增）-->
    <!-- 依据定制化设计流程：步骤2匹配维度、5.1平齐、5.4灯类型、7.1同侧   -->""",
    """    <!-- 五之二、对象属性 —— 平齐 / 同侧（v12.0 新增）                  -->
    <!-- 依据定制化设计流程：5.1平齐、7.1同侧                            -->
    <!-- （v15.8：连接方式 / 灯类型 改为数据属性，见数据属性章节）       -->""",
    "A4 更新「五之二」章节注释头",
)

# ============================================================
# B. 数据属性：删除 :连接方式名称/:灯类型名称，新建 3 条字符串数据属性
# ============================================================
s = rep(
    s,
    """    <owl:DatatypeProperty rdf:about="#connectionTypeName">
        <rdf:type rdf:resource="http://www.w3.org/2002/07/owl#FunctionalProperty"/>
        <rdfs:domain rdf:resource="#AccessoryConnectionType"/>
        <rdfs:range rdf:resource="http://www.w3.org/2001/XMLSchema#string"/>
        <rdfs:label xml:lang="zh">连接方式名称</rdfs:label>
        <rdfs:comment xml:lang="zh">附件连接方式的名称（身份键），取值：粘贴式 / 焊接式（v12.0 新增）</rdfs:comment>
    </owl:DatatypeProperty>

    <owl:DatatypeProperty rdf:about="#lightTypeName">
        <rdf:type rdf:resource="http://www.w3.org/2002/07/owl#FunctionalProperty"/>
        <rdfs:domain rdf:resource="#LightType"/>
        <rdfs:range rdf:resource="http://www.w3.org/2001/XMLSchema#string"/>
        <rdfs:label xml:lang="zh">灯类型名称</rdfs:label>
        <rdfs:comment xml:lang="zh">灯类型的名称（身份键），由机型+区域确定（v12.0 新增）</rdfs:comment>
    </owl:DatatypeProperty>

""",
    """    <!-- v15.8：附件连接方式 / 附件类型 由分类值类改为 :附件 的字符串数据属性 -->
    <owl:DatatypeProperty rdf:about="#accessoryConnectionType">
        <rdf:type rdf:resource="http://www.w3.org/2002/07/owl#FunctionalProperty"/>
        <rdfs:domain rdf:resource="#Accessory"/>
        <rdfs:range rdf:resource="http://www.w3.org/2001/XMLSchema#string"/>
        <rdfs:label xml:lang="zh">附件连接方式</rdfs:label>
        <rdfs:comment xml:lang="zh">附件的安装方式——附件如何安装在塔筒上，取值：粘贴式 / 焊接式。不同方式选用附件不同（定制化设计流程步骤2匹配维度、步骤8替换附件类型依据）。v15.8 依业务决策由分类值类 :附件连接方式（AccessoryConnectionType）改为 :附件 的字符串数据属性</rdfs:comment>
    </owl:DatatypeProperty>

    <owl:DatatypeProperty rdf:about="#accessoryType">
        <rdf:type rdf:resource="http://www.w3.org/2002/07/owl#FunctionalProperty"/>
        <rdfs:domain rdf:resource="#Accessory"/>
        <rdfs:range rdf:resource="http://www.w3.org/2001/XMLSchema#string"/>
        <rdfs:label xml:lang="zh">附件类型</rdfs:label>
        <rdfs:comment xml:lang="zh">爬梯支撑 / 电缆托架所选用附件的类型，由附件连接方式决定（粘贴式与焊接式选用附件不同）。定制化设计流程步骤8.1「替换附件类型」。v15.8 依业务决策由分类值类 :附件类型（AccessoryType）改为 :附件 的字符串数据属性</rdfs:comment>
    </owl:DatatypeProperty>

    <!-- v15.8：灯类型 由分类值类改为 :灯 的字符串数据属性 -->
    <owl:DatatypeProperty rdf:about="#lightType">
        <rdf:type rdf:resource="http://www.w3.org/2002/07/owl#FunctionalProperty"/>
        <rdfs:domain rdf:resource="#Light"/>
        <rdfs:range rdf:resource="http://www.w3.org/2001/XMLSchema#string"/>
        <rdfs:label xml:lang="zh">灯类型</rdfs:label>
        <rdfs:comment xml:lang="zh">照明灯的类型，由「机型 + 区域」确定（定制化设计流程 5.4）。v15.8 依业务决策由分类值类 :灯类型（LightType）改为 :灯 的字符串数据属性</rdfs:comment>
    </owl:DatatypeProperty>

""",
    "B 删除 :连接方式名称/:灯类型名称，新建 :附件连接方式/:附件类型/:灯类型",
)

# ============================================================
# C. 删除类：:电缆托架规格 / :附件连接方式 / :附件类型 / :灯类型
# ============================================================
s = dele(
    s,
    """    <!-- 顶层类：电缆托架规格（v11.0 新增，承载机型对应的托架参数） -->
    <owl:Class rdf:about="#CableBracketSpec">
        <rdfs:label xml:lang="zh">电缆托架规格</rdfs:label>
        <rdfs:comment xml:lang="zh">电缆托架规格参数集（托架长度、左右安装弦长），随机型变化，作为分类值以个体形式存在（v11.0 新增；v15.0 依 P1-3 补身份键 owl:hasKey）</rdfs:comment>
        <owl:hasKey rdf:parseType="Collection">
            <owl:DatatypeProperty rdf:about="#bracketLength"/>
            <owl:DatatypeProperty rdf:about="#bracketRightChord"/>
            <owl:DatatypeProperty rdf:about="#bracketLeftChord"/>
        </owl:hasKey>
    </owl:Class>

    <!-- 顶层类：附件连接方式（v12.0 新增，定制化设计流程步骤2匹配维度 + 步骤8取数依据） -->
    <owl:Class rdf:about="#AccessoryConnectionType">
        <rdfs:label xml:lang="zh">附件连接方式</rdfs:label>
        <rdfs:comment xml:lang="zh">爬梯支撑与电缆托架**安装在塔筒上的方式**，共 2 种：粘贴式、焊接式。两种方式的**选用附件不同**（对应步骤8「替换附件类型」）。作为分类值以个体形式存在（含身份键 :连接方式名称）。定制化设计流程步骤2「从后往前匹配」的第3环（v12.0 新增）</rdfs:comment>
        <owl:hasKey rdf:parseType="Collection">
            <owl:DatatypeProperty rdf:about="#connectionTypeName"/>
        </owl:hasKey>
    </owl:Class>

    <!-- 顶层类：灯类型（v12.0 新增，由机型+区域确定） -->
    <owl:Class rdf:about="#LightType">
        <rdfs:label xml:lang="zh">灯类型</rdfs:label>
        <rdfs:comment xml:lang="zh">照明灯的类型，作为分类值以个体形式存在（含身份键 :灯类型名称）。定制化设计流程 5.4：灯的类型由「机型 + 区域」确定（v12.0 新增）</rdfs:comment>
        <owl:hasKey rdf:parseType="Collection">
            <owl:DatatypeProperty rdf:about="#lightTypeName"/>
        </owl:hasKey>
    </owl:Class>

    <!-- 顶层类：附件类型（v12.0 新增，由附件连接方式决定，对应步骤8.1「替换附件类型」） -->
    <owl:Class rdf:about="#AccessoryType">
        <rdfs:label xml:lang="zh">附件类型</rdfs:label>
        <rdfs:comment xml:lang="zh">爬梯支撑 / 电缆托架所选用附件的类型，由附件连接方式决定（粘贴式与焊接式选用附件不同）。作为分类值以个体形式存在。定制化设计流程步骤8.1「替换附件类型」（v12.0 新增；v15.6 删除 :附件类型名称，身份键随之移除）</rdfs:comment>
    </owl:Class>

""",
    "C 删除类 :电缆托架规格/:附件连接方式/:附件类型/:灯类型",
)

# ============================================================
# D. :托架长度/:托架右弦长/:托架左弦长 域改挂 :电缆托架
# ============================================================
s = rep(
    s,
    """    <!-- 电缆托架规格（机型参数） -->
    <owl:DatatypeProperty rdf:about="#bracketLength">
        <rdf:type rdf:resource="http://www.w3.org/2002/07/owl#FunctionalProperty"/>
        <rdfs:domain rdf:resource="#CableBracketSpec"/>
        <rdfs:range rdf:resource="http://www.w3.org/2001/XMLSchema#integer"/>
        <rdfs:label xml:lang="zh">托架长度</rdfs:label>
        <rdfs:comment xml:lang="zh">电缆托架长度，单位 mm（随机型变化，v11.0 新增）</rdfs:comment>
    </owl:DatatypeProperty>""",
    """    <!-- 电缆托架尺寸（v15.8：原 :电缆托架规格 类删除，尺寸直挂 :电缆托架） -->
    <owl:DatatypeProperty rdf:about="#bracketLength">
        <rdf:type rdf:resource="http://www.w3.org/2002/07/owl#FunctionalProperty"/>
        <rdfs:domain rdf:resource="#CableBracket"/>
        <rdfs:range rdf:resource="http://www.w3.org/2001/XMLSchema#integer"/>
        <rdfs:label xml:lang="zh">托架长度</rdfs:label>
        <rdfs:comment xml:lang="zh">电缆托架长度，单位 mm（随机型变化，v11.0 新增；v15.8 依业务决策由 :电缆托架规格 类改挂 :电缆托架）</rdfs:comment>
    </owl:DatatypeProperty>""",
    "D1 :托架长度 域 → :电缆托架",
)

s = rep(
    s,
    """    <owl:DatatypeProperty rdf:about="#bracketRightChord">
        <rdf:type rdf:resource="http://www.w3.org/2002/07/owl#FunctionalProperty"/>
        <rdfs:domain rdf:resource="#CableBracketSpec"/>
        <rdfs:range rdf:resource="http://www.w3.org/2001/XMLSchema#integer"/>
        <rdfs:label xml:lang="zh">托架右弦长</rdfs:label>
        <rdfs:comment xml:lang="zh">右侧电缆托架安装弦长，单位 mm（v11.0 新增）</rdfs:comment>
    </owl:DatatypeProperty>""",
    """    <owl:DatatypeProperty rdf:about="#bracketRightChord">
        <rdf:type rdf:resource="http://www.w3.org/2002/07/owl#FunctionalProperty"/>
        <rdfs:domain rdf:resource="#CableBracket"/>
        <rdfs:range rdf:resource="http://www.w3.org/2001/XMLSchema#integer"/>
        <rdfs:label xml:lang="zh">托架右弦长</rdfs:label>
        <rdfs:comment xml:lang="zh">右侧电缆托架安装弦长，单位 mm（v11.0 新增；v15.8 由 :电缆托架规格 类改挂 :电缆托架）</rdfs:comment>
    </owl:DatatypeProperty>""",
    "D2 :托架右弦长 域 → :电缆托架",
)

s = rep(
    s,
    """    <owl:DatatypeProperty rdf:about="#bracketLeftChord">
        <rdf:type rdf:resource="http://www.w3.org/2002/07/owl#FunctionalProperty"/>
        <rdfs:domain rdf:resource="#CableBracketSpec"/>
        <rdfs:range rdf:resource="http://www.w3.org/2001/XMLSchema#integer"/>
        <rdfs:label xml:lang="zh">托架左弦长</rdfs:label>
        <rdfs:comment xml:lang="zh">左侧电缆托架安装弦长，单位 mm（v11.0 新增）</rdfs:comment>
    </owl:DatatypeProperty>""",
    """    <owl:DatatypeProperty rdf:about="#bracketLeftChord">
        <rdf:type rdf:resource="http://www.w3.org/2002/07/owl#FunctionalProperty"/>
        <rdfs:domain rdf:resource="#CableBracket"/>
        <rdfs:range rdf:resource="http://www.w3.org/2001/XMLSchema#integer"/>
        <rdfs:label xml:lang="zh">托架左弦长</rdfs:label>
        <rdfs:comment xml:lang="zh">左侧电缆托架安装弦长，单位 mm（v11.0 新增；v15.8 由 :电缆托架规格 类改挂 :电缆托架）</rdfs:comment>
    </owl:DatatypeProperty>""",
    "D3 :托架左弦长 域 → :电缆托架",
)

# ============================================================
# E. 分类值不相交组移出 4 类
# ============================================================
s = rep(
    s,
    """        <rdfs:comment xml:lang="zh">分类值类互不相交（v13.0 新增分组，与物理对象类分开声明；v15.6 :材料 删除故移出本组）</rdfs:comment>
        <owl:members rdf:parseType="Collection">
            <owl:Class rdf:about="#Model"/>
            <owl:Class rdf:about="#Region"/>
            <owl:Class rdf:about="#CableBracketSpec"/>
            <owl:Class rdf:about="#AccessoryConnectionType"/>
            <owl:Class rdf:about="#LightType"/>
            <owl:Class rdf:about="#AccessoryType"/>
        </owl:members>""",
    """        <rdfs:comment xml:lang="zh">分类值类互不相交（v13.0 新增分组，与物理对象类分开声明；v15.6 :材料 删除故移出本组；v15.8 :电缆托架规格/:附件连接方式/:附件类型/:灯类型 改为属性或删除故移出本组）</rdfs:comment>
        <owl:members rdf:parseType="Collection">
            <owl:Class rdf:about="#Model"/>
            <owl:Class rdf:about="#Region"/>
        </owl:members>""",
    "E 分类值不相交组移出 4 类",
)

# ============================================================
# F. 删除 2 个分类值个体（粘贴式/焊接式）
# ============================================================
s = dele(
    s,
    """    <!-- ============================================================ -->
    <!-- 十、分类值个体（v12.0 新增）                                  -->
    <!-- 附件连接方式：爬梯支撑/电缆托架安装在塔筒上的方式，共 2 种     -->
    <!-- ============================================================ -->

    <owl:NamedIndividual rdf:about="#ConnectionType_Adhesive">
        <rdf:type rdf:resource="#AccessoryConnectionType"/>
        <rdfs:label xml:lang="zh">粘贴式</rdfs:label>
        <connectionTypeName>粘贴式</connectionTypeName>
    </owl:NamedIndividual>

    <owl:NamedIndividual rdf:about="#ConnectionType_Welded">
        <rdf:type rdf:resource="#AccessoryConnectionType"/>
        <rdfs:label xml:lang="zh">焊接式</rdfs:label>
        <connectionTypeName>焊接式</connectionTypeName>
    </owl:NamedIndividual>

""",
    "F 删除 2 个分类值个体",
)

# ============================================================
# G. 版本号 + 头部注释
# ============================================================
s = rep(
    s,
    "<owl:versionInfo>v15.7</owl:versionInfo>",
    "<owl:versionInfo>v15.8</owl:versionInfo>",
    "G1 版本号 v15.7 → v15.8",
)

s = rep(
    s,
    "（注：design/塔架中段设计流程.md 步骤 8.1 标题仍沿用流程原文「替换配件类型」，未改。）</rdfs:comment>",
    "（注：design/塔架中段设计流程.md 步骤 8.1 标题仍沿用流程原文「替换配件类型」，未改。）v15.8 依业务决策修正：**分类值类改为物理对象的字符串数据属性**——删除类 :附件连接方式（AccessoryConnectionType）、:附件类型（AccessoryType）、:灯类型（LightType）及其身份键数据属性（:连接方式名称/:灯类型名称）与 2 个分类值个体（粘贴式/焊接式），改设 :附件 的数据属性 :附件连接方式 / :附件类型 与 :灯 的数据属性 :灯类型（均 xsd:string + FunctionalProperty）；删除对应对象属性 :有连接方式/:连接方式属于、:有灯类型/:灯类型属于、:选用附件类型/:附件类型适用于。**删除类 :电缆托架规格（CableBracketSpec）**及其对象属性 :有电缆托架规格/:托架规格属于，:托架长度/:托架右弦长/:托架左弦长 域由 :电缆托架规格 改挂 :电缆托架。分类值不相交组由 6 类减为 2 类（:机型/:区域）。</rdfs:comment>",
    "G2 头部注释追加 v15.8",
)

save(owl_p, s)
print("\n=== 本体 v15.8 写入完成 ===")
