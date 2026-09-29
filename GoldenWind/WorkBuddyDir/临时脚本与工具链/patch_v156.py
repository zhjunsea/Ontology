# -*- coding: utf-8 -*-
"""
v15.5 -> v15.6 本体修正脚本
业务决策（合并两轮指令）：
  A. :与…平齐 改名 :平齐（表示主语与宾语等高）
  B. 删除类 :材料 及其属性；改为每个主要结构件的 :材质 数据属性（并集域）
  C. 删除类 :照明系统 及其组成属性；:有灯 改挂 :塔架中段；照明布置属性改挂 :塔架中段
  D. 扁平化：删除中间类 :塔架段，:塔架中段 直接作为 :塔架 子类
  E. 删除 :疲劳等级
  F. 删除 :螺柱安装角度
  G. 删除 :配件类型名称（:配件类型 身份键随之移除）
  H. 删除类 :安全锚点 及其属性
  I. :支撑宽度 改名 :宽度，并集域扩至 :电缆托架
"""
import io, sys

P = r"D:\work\Ontology\GoldenWind\ontology\TowerMidSection.owl"
with io.open(P, "r", encoding="utf-8") as f:
    s = f.read()

hits = []


def rep(old, new, tag):
    global s
    n = s.count(old)
    if n != 1:
        print("[FAIL] %s : 命中 %d 次（应为 1）" % (tag, n))
        sys.exit(1)
    s = s.replace(old, new)
    hits.append(tag)
    print("[OK] %s" % tag)


def dele(old, tag):
    global s
    n = s.count(old)
    if n != 1:
        print("[FAIL] %s : 命中 %d 次（应为 1）" % (tag, n))
        sys.exit(1)
    s = s.replace(old, "")
    hits.append(tag)
    print("[OK] %s" % tag)


# 新名称冲突检查
for nm in ("#width\"", "#material\""):
    if nm in s:
        print("[FAIL] 新名称已存在：%s" % nm)
        sys.exit(1)

# ============================================================
# 0) 版本号 / 总注释
# ============================================================
rep("<owl:versionInfo>v15.5</owl:versionInfo>",
    "<owl:versionInfo>v15.6</owl:versionInfo>",
    "0-1 版本号 v15.5 -> v15.6")

rep(":与…平齐 ↔ :平齐于", ":平齐 ↔ :平齐于", "0-2 总注释逆属性引用改名")

rep("（其数值约束已于 v15.0 依 P2 因缺乏依据移除）。</rdfs:comment>",
    "（其数值约束已于 v15.0 依 P2 因缺乏依据移除）。"
    "v15.6 依业务决策修正：**A** :与…平齐 改名 :平齐（表示主语与宾语等高）；"
    "**B** 删除类 :材料 及其属性（:有材料/:材料属于/:板材等级/:材料密度），"
    "改为每个主要结构件（:筒节/:法兰/:平台/:爬梯/:爬梯支撑/:电缆托架/:线槽/:扶持）的 :材质 数据属性（并集域）；"
    "**C** 删除类 :照明系统 及其组成属性（:有照明系统/:照明系统属于），:有灯 改挂 :塔架中段；"
    "照明布置属性（:第一灯安装高度/:灯与灯最大间距/:灯与灯最小间距）改挂 :塔架中段，约束公理随之迁移；"
    "**D** 扁平化：删除中间类 :塔架段，:塔架中段 直接作为 :塔架 子类（:有塔架段→:有塔架中段、"
    ":法兰连接塔架段→:法兰连接塔架中段、:相邻于 域/值域改 :塔架中段）；"
    "**E** 删除 :疲劳等级；**F** 删除 :螺柱安装角度；**G** 删除 :配件类型名称（:配件类型 身份键随之移除）；"
    "**H** 删除类 :安全锚点 及其属性（:有安全锚点/:锚点安装高度），附件子类不相交组由 3 类减为 2 类；"
    "**I** :支撑宽度 改名 :宽度，并集域扩至 :电缆托架。</rdfs:comment>",
    "0-3 总注释追加 v15.6")

# ============================================================
# D) 扁平化：:有塔架段 -> :有塔架中段
# ============================================================
rep(
    """    <owl:ObjectProperty rdf:about="#hasTowerSection">
        <rdfs:domain rdf:resource="#Tower"/>
        <rdfs:range rdf:resource="#TowerSection"/>
        <owl:inverseOf rdf:resource="#sectionBelongsToTower"/>
        <rdfs:label xml:lang="zh">有塔架段</rdfs:label>
        <rdfs:comment xml:lang="zh">塔架由塔架段组成（本项目仅涉及塔架中段）。v14.0 保留为抽象父类</rdfs:comment>
    </owl:ObjectProperty>""",
    """    <owl:ObjectProperty rdf:about="#hasTowerMidSection">
        <rdfs:domain rdf:resource="#Tower"/>
        <rdfs:range rdf:resource="#TowerMidSection"/>
        <owl:inverseOf rdf:resource="#midSectionBelongsToTower"/>
        <rdfs:label xml:lang="zh">有塔架中段</rdfs:label>
        <rdfs:comment xml:lang="zh">塔架由塔架中段组成（本项目仅涉及塔架中段）。v15.6 随 :塔架段 扁平化删除，由 :有塔架段 改名并收窄值域至 :塔架中段</rdfs:comment>
    </owl:ObjectProperty>""",
    "D-1 :有塔架段 -> :有塔架中段")

rep(
    """    <owl:ObjectProperty rdf:about="#sectionBelongsToTower">
        <rdfs:label xml:lang="zh">塔架段属于</rdfs:label>
        <rdfs:comment xml:lang="zh">有塔架段 的逆属性</rdfs:comment>
    </owl:ObjectProperty>""",
    """    <owl:ObjectProperty rdf:about="#midSectionBelongsToTower">
        <rdfs:label xml:lang="zh">塔架中段属于</rdfs:label>
        <rdfs:comment xml:lang="zh">有塔架中段 的逆属性（v15.6 随 :塔架段 扁平化删除改名）</rdfs:comment>
    </owl:ObjectProperty>""",
    "D-2 :塔架段属于 -> :塔架中段属于")

# ============================================================
# C) 删除 :有照明系统 / :照明系统属于；:有灯 改挂
# ============================================================
dele(
    """    <owl:ObjectProperty rdf:about="#hasLightingSystem">
        <rdfs:domain rdf:resource="#TowerMidSection"/>
        <rdfs:range rdf:resource="#LightingSystem"/>
        <owl:inverseOf rdf:resource="#lightingSystemBelongsToTowerMidSection"/>
        <rdfs:label xml:lang="zh">有照明系统</rdfs:label>
        <rdfs:comment xml:lang="zh">塔架中段包含照明系统</rdfs:comment>
    </owl:ObjectProperty>

    <owl:ObjectProperty rdf:about="#lightingSystemBelongsToTowerMidSection">
        <rdfs:label xml:lang="zh">照明系统属于</rdfs:label>
        <rdfs:comment xml:lang="zh">有照明系统 的逆属性</rdfs:comment>
    </owl:ObjectProperty>

""",
    "C-1 删除 :有照明系统 / :照明系统属于")

rep(
    """    <owl:ObjectProperty rdf:about="#hasLight">
        <rdfs:domain rdf:resource="#LightingSystem"/>
        <rdfs:range rdf:resource="#Light"/>
        <owl:inverseOf rdf:resource="#lightBelongsToLightingSystem"/>
        <rdfs:label xml:lang="zh">有灯</rdfs:label>
        <rdfs:comment xml:lang="zh">照明系统由灯组成</rdfs:comment>
    </owl:ObjectProperty>

    <owl:ObjectProperty rdf:about="#lightBelongsToLightingSystem">
        <rdfs:label xml:lang="zh">灯属于</rdfs:label>
        <rdfs:comment xml:lang="zh">有灯 的逆属性</rdfs:comment>
    </owl:ObjectProperty>""",
    """    <owl:ObjectProperty rdf:about="#hasLight">
        <rdfs:domain rdf:resource="#TowerMidSection"/>
        <rdfs:range rdf:resource="#Light"/>
        <owl:inverseOf rdf:resource="#lightBelongsToTowerMidSection"/>
        <rdfs:label xml:lang="zh">有灯</rdfs:label>
        <rdfs:comment xml:lang="zh">塔架中段包含灯（v15.6 随 :照明系统 删除，域由 :照明系统 改挂 :塔架中段）</rdfs:comment>
    </owl:ObjectProperty>

    <owl:ObjectProperty rdf:about="#lightBelongsToTowerMidSection">
        <rdfs:label xml:lang="zh">灯属于</rdfs:label>
        <rdfs:comment xml:lang="zh">有灯 的逆属性（v15.6 随 :照明系统 删除改名）</rdfs:comment>
    </owl:ObjectProperty>""",
    "C-2 :有灯 改挂 :塔架中段 + 逆属性改名")

# ============================================================
# A) :与…平齐 -> :平齐
# ============================================================
rep(
    """        <rdfs:label xml:lang="zh">与…平齐</rdfs:label>
        <rdfs:comment xml:lang="zh">爬梯支撑与电缆托架平齐（定制化设计流程 5.1：这两个附件是平齐的，即 :支撑位置 = :托架位置，v12.0 新增）</rdfs:comment>
    </owl:ObjectProperty>

    <owl:ObjectProperty rdf:about="#flushWithInverse">
        <rdfs:label xml:lang="zh">平齐于</rdfs:label>
        <rdfs:comment xml:lang="zh">与…平齐 的逆属性（v12.0 新增）</rdfs:comment>""",
    """        <rdfs:label xml:lang="zh">平齐</rdfs:label>
        <rdfs:comment xml:lang="zh">表示主语与宾语等高（爬梯支撑与电缆托架等高，即 :支撑位置 = :托架位置；定制化设计流程 5.1，v12.0 新增；v15.6 由 :与…平齐 改名 :平齐）</rdfs:comment>
    </owl:ObjectProperty>

    <owl:ObjectProperty rdf:about="#flushWithInverse">
        <rdfs:label xml:lang="zh">平齐于</rdfs:label>
        <rdfs:comment xml:lang="zh">平齐 的逆属性（v12.0 新增；v15.6 随正向属性改名）</rdfs:comment>""",
    "A-1 :与…平齐 -> :平齐")

# ============================================================
# B) 删除 :有材料 / :材料属于
# ============================================================
dele(
    """    <!-- 筒节有材料 -->
    <owl:ObjectProperty rdf:about="#hasMaterial">
        <rdfs:domain rdf:resource="#TubeSection"/>
        <rdfs:range rdf:resource="#Material"/>
        <owl:inverseOf rdf:resource="#materialBelongsToTubeSection"/>
        <rdfs:label xml:lang="zh">有材料</rdfs:label>
        <rdfs:comment xml:lang="zh">筒节由某种材料制成（承载板材等级与密度，v11.0 新增）</rdfs:comment>
    </owl:ObjectProperty>

    <owl:ObjectProperty rdf:about="#materialBelongsToTubeSection">
        <rdfs:label xml:lang="zh">材料属于</rdfs:label>
        <rdfs:comment xml:lang="zh">有材料 的逆属性</rdfs:comment>
    </owl:ObjectProperty>

""",
    "B-1 删除 :有材料 / :材料属于")

# ============================================================
# D) :法兰连接塔架段 -> :法兰连接塔架中段
# ============================================================
rep(
    """    <owl:ObjectProperty rdf:about="#flangeConnectsTowerSection">
        <rdfs:domain rdf:resource="#Flange"/>
        <rdfs:range rdf:resource="#TowerSection"/>
        <rdfs:label xml:lang="zh">法兰连接塔架段</rdfs:label>
        <rdfs:comment xml:lang="zh">法兰连接上下相邻的塔架段（v13.0 新增，补齐法兰-段关系）</rdfs:comment>
    </owl:ObjectProperty>""",
    """    <owl:ObjectProperty rdf:about="#flangeConnectsTowerMidSection">
        <rdfs:domain rdf:resource="#Flange"/>
        <rdfs:range rdf:resource="#TowerMidSection"/>
        <rdfs:label xml:lang="zh">法兰连接塔架中段</rdfs:label>
        <rdfs:comment xml:lang="zh">法兰连接上下相邻的塔架中段（v13.0 新增；v15.6 随 :塔架段 扁平化删除，值域改为 :塔架中段）</rdfs:comment>
    </owl:ObjectProperty>""",
    "D-3 :法兰连接塔架段 -> :法兰连接塔架中段")

# ============================================================
# H) 删除 :有安全锚点
# ============================================================
dele(
    """    <owl:ObjectProperty rdf:about="#hasSafetyAnchor">
        <rdfs:domain rdf:resource="#Ladder"/>
        <rdfs:range rdf:resource="#SafetyAnchor"/>
        <rdfs:label xml:lang="zh">有安全锚点</rdfs:label>
        <rdfs:comment xml:lang="zh">爬梯配套的安全锚点（v13.0 新增，补齐锚点-爬梯关系）</rdfs:comment>
    </owl:ObjectProperty>

""",
    "H-1 删除 :有安全锚点")

# ============================================================
# D) :相邻于 域/值域 -> :塔架中段
# ============================================================
rep(
    """        <rdfs:domain rdf:resource="#TowerSection"/>
        <rdfs:range rdf:resource="#TowerSection"/>
        <rdfs:label xml:lang="zh">相邻于</rdfs:label>
        <rdfs:comment xml:lang="zh">塔架段上下相邻（对称属性，自带逆关系，v13.0 新增）</rdfs:comment>""",
    """        <rdfs:domain rdf:resource="#TowerMidSection"/>
        <rdfs:range rdf:resource="#TowerMidSection"/>
        <rdfs:label xml:lang="zh">相邻于</rdfs:label>
        <rdfs:comment xml:lang="zh">塔架中段上下相邻（对称属性，自带逆关系，v13.0 新增；v15.6 域/值域由 :塔架段 改为 :塔架中段）</rdfs:comment>""",
    "D-4 :相邻于 域/值域 -> :塔架中段")

# ============================================================
# 固定在塔筒 注释更新（安全锚点已删除）
# ============================================================
rep("domain 统一为 :附件 ∪ :构件 ∪ :平台（v15.0 加入 :构件）",
    "domain 统一为 :附件 ∪ :构件 ∪ :平台（v15.0 加入 :构件；v15.6 安全锚点已删除）",
    "X-1 :固定在塔筒 注释更新")

# ============================================================
# C) 照明布置属性 域 -> :塔架中段
# ============================================================
rep(
    """    <!-- 照明系统 -->
    <owl:DatatypeProperty rdf:about="#firstLightHeight">
        <rdfs:domain rdf:resource="#LightingSystem"/>
        <rdfs:range rdf:resource="http://www.w3.org/2001/XMLSchema#integer"/>
        <rdfs:label xml:lang="zh">第一灯安装高度</rdfs:label>
        <rdfs:comment xml:lang="zh">第一个灯安装高度（上方空间），单位 mm，允许范围 2600~3000</rdfs:comment>
    </owl:DatatypeProperty>

    <owl:DatatypeProperty rdf:about="#lightToLightMaxSpacing">
        <rdfs:domain rdf:resource="#LightingSystem"/>
        <rdfs:range rdf:resource="http://www.w3.org/2001/XMLSchema#integer"/>
        <rdfs:label xml:lang="zh">灯与灯最大间距</rdfs:label>
        <rdfs:comment xml:lang="zh">相邻两灯之间的最大间距，单位 mm，最大 10000（依定制化设计流程 5.5；v15.0 依 P1-5 由「灯上螺柱最大间距」改名，原名称与语义不符）</rdfs:comment>
    </owl:DatatypeProperty>

    <owl:DatatypeProperty rdf:about="#lightToLightMinSpacing">
        <rdfs:domain rdf:resource="#LightingSystem"/>
        <rdfs:range rdf:resource="http://www.w3.org/2001/XMLSchema#integer"/>
        <rdfs:label xml:lang="zh">灯与灯最小间距</rdfs:label>
        <rdfs:comment xml:lang="zh">相邻两灯之间的最小间距，单位 mm，最小 5000（依定制化设计流程 5.5；v15.0 依 P1-5 由「灯上螺柱最小间距」改名，原名称与语义不符）</rdfs:comment>
    </owl:DatatypeProperty>""",
    """    <!-- 照明布置（v15.6 由 :照明系统 改挂 :塔架中段） -->
    <owl:DatatypeProperty rdf:about="#firstLightHeight">
        <rdfs:domain rdf:resource="#TowerMidSection"/>
        <rdfs:range rdf:resource="http://www.w3.org/2001/XMLSchema#integer"/>
        <rdfs:label xml:lang="zh">第一灯安装高度</rdfs:label>
        <rdfs:comment xml:lang="zh">第一个灯安装高度（上方空间），单位 mm，允许范围 2600~3000（v15.6 域由 :照明系统 改挂 :塔架中段）</rdfs:comment>
    </owl:DatatypeProperty>

    <owl:DatatypeProperty rdf:about="#lightToLightMaxSpacing">
        <rdfs:domain rdf:resource="#TowerMidSection"/>
        <rdfs:range rdf:resource="http://www.w3.org/2001/XMLSchema#integer"/>
        <rdfs:label xml:lang="zh">灯与灯最大间距</rdfs:label>
        <rdfs:comment xml:lang="zh">相邻两灯之间的最大间距，单位 mm，最大 10000（依定制化设计流程 5.5；v15.0 依 P1-5 由「灯上螺柱最大间距」改名，原名称与语义不符；v15.6 域由 :照明系统 改挂 :塔架中段）</rdfs:comment>
    </owl:DatatypeProperty>

    <owl:DatatypeProperty rdf:about="#lightToLightMinSpacing">
        <rdfs:domain rdf:resource="#TowerMidSection"/>
        <rdfs:range rdf:resource="http://www.w3.org/2001/XMLSchema#integer"/>
        <rdfs:label xml:lang="zh">灯与灯最小间距</rdfs:label>
        <rdfs:comment xml:lang="zh">相邻两灯之间的最小间距，单位 mm，最小 5000（依定制化设计流程 5.5；v15.0 依 P1-5 由「灯上螺柱最小间距」改名，原名称与语义不符；v15.6 域由 :照明系统 改挂 :塔架中段）</rdfs:comment>
    </owl:DatatypeProperty>""",
    "C-3 照明布置属性 域 -> :塔架中段")

# ============================================================
# G) 删除 :配件类型名称
# ============================================================
dele(
    """    <owl:DatatypeProperty rdf:about="#fittingTypeName">
        <rdf:type rdf:resource="http://www.w3.org/2002/07/owl#FunctionalProperty"/>
        <rdfs:domain rdf:resource="#FittingType"/>
        <rdfs:range rdf:resource="http://www.w3.org/2001/XMLSchema#string"/>
        <rdfs:label xml:lang="zh">配件类型名称</rdfs:label>
        <rdfs:comment xml:lang="zh">配件类型的名称（身份键），具体取值待业务补充（v12.0 新增）</rdfs:comment>
    </owl:DatatypeProperty>

""",
    "G-1 删除 :配件类型名称")

# ============================================================
# H) 删除 :锚点安装高度
# ============================================================
dele(
    """    <!-- 安全锚点 -->
    <owl:DatatypeProperty rdf:about="#anchorInstallationHeight">
        <rdf:type rdf:resource="http://www.w3.org/2002/07/owl#FunctionalProperty"/>
        <rdfs:domain rdf:resource="#SafetyAnchor"/>
        <rdfs:range rdf:resource="http://www.w3.org/2001/XMLSchema#integer"/>
        <rdfs:label xml:lang="zh">锚点安装高度</rdfs:label>
        <rdfs:comment xml:lang="zh">爬梯安全锚点安装高度，单位 mm（v11.0 新增）</rdfs:comment>
    </owl:DatatypeProperty>

""",
    "H-2 删除 :锚点安装高度")

# ============================================================
# B) 删除 :板材等级 / :材料密度，新增 :材质
# ============================================================
rep(
    """    <!-- 材料 -->
    <owl:DatatypeProperty rdf:about="#plateGrade">
        <rdf:type rdf:resource="http://www.w3.org/2002/07/owl#FunctionalProperty"/>
        <rdfs:domain rdf:resource="#Material"/>
        <rdfs:range rdf:resource="http://www.w3.org/2001/XMLSchema#string"/>
        <rdfs:label xml:lang="zh">板材等级</rdfs:label>
        <rdfs:comment xml:lang="zh">认证参考板材等级（如 Q355C）。v13.0 升为 :材料 的身份键（owl:hasKey）</rdfs:comment>
    </owl:DatatypeProperty>

    <owl:DatatypeProperty rdf:about="#materialDensity">
        <rdf:type rdf:resource="http://www.w3.org/2002/07/owl#FunctionalProperty"/>
        <rdfs:domain rdf:resource="#Material"/>
        <rdfs:range rdf:resource="http://www.w3.org/2001/XMLSchema#decimal"/>
        <rdfs:label xml:lang="zh">材料密度</rdfs:label>
        <rdfs:comment xml:lang="zh">材料密度，单位 kg/m³（钢材取 7850，v11.0 新增）</rdfs:comment>
    </owl:DatatypeProperty>""",
    """    <!-- 材质（v15.6：删除 :材料 类，改为每个主要结构件的材质属性，并集域） -->
    <owl:DatatypeProperty rdf:about="#material">
        <rdf:type rdf:resource="http://www.w3.org/2002/07/owl#FunctionalProperty"/>
        <rdfs:domain>
            <owl:Class>
                <owl:unionOf rdf:parseType="Collection">
                    <owl:Class rdf:about="#TubeSection"/>
                    <owl:Class rdf:about="#Flange"/>
                    <owl:Class rdf:about="#Platform"/>
                    <owl:Class rdf:about="#Ladder"/>
                    <owl:Class rdf:about="#LadderSupport"/>
                    <owl:Class rdf:about="#CableBracket"/>
                    <owl:Class rdf:about="#CableTray"/>
                    <owl:Class rdf:about="#Support"/>
                </owl:unionOf>
            </owl:Class>
        </rdfs:domain>
        <rdfs:range rdf:resource="http://www.w3.org/2001/XMLSchema#string"/>
        <rdfs:label xml:lang="zh">材质</rdfs:label>
        <rdfs:comment xml:lang="zh">对象所用材质（如 Q355C）。v15.6 依业务决策：删除 :材料 类及其属性（:有材料/:材料属于/:板材等级/:材料密度），改为每个主要结构件（:筒节/:法兰/:平台/:爬梯/:爬梯支撑/:电缆托架/:线槽/:扶持）直接承载的 :材质 数据属性</rdfs:comment>
    </owl:DatatypeProperty>""",
    "B-2 删除 :板材等级/:材料密度，新增 :材质")

# ============================================================
# I) :支撑宽度 -> :宽度（并集域）
# ============================================================
rep(
    """    <owl:DatatypeProperty rdf:about="#ladderSupportWidth">
        <rdf:type rdf:resource="http://www.w3.org/2002/07/owl#FunctionalProperty"/>
        <rdfs:domain rdf:resource="#LadderSupport"/>
        <rdfs:range rdf:resource="http://www.w3.org/2001/XMLSchema#integer"/>
        <rdfs:label xml:lang="zh">支撑宽度</rdfs:label>
        <rdfs:comment xml:lang="zh">爬梯支撑宽度，单位 mm（v11.0 新增）</rdfs:comment>
    </owl:DatatypeProperty>""",
    """    <owl:DatatypeProperty rdf:about="#width">
        <rdf:type rdf:resource="http://www.w3.org/2002/07/owl#FunctionalProperty"/>
        <rdfs:domain>
            <owl:Class>
                <owl:unionOf rdf:parseType="Collection">
                    <owl:Class rdf:about="#LadderSupport"/>
                    <owl:Class rdf:about="#CableBracket"/>
                </owl:unionOf>
            </owl:Class>
        </rdfs:domain>
        <rdfs:range rdf:resource="http://www.w3.org/2001/XMLSchema#integer"/>
        <rdfs:label xml:lang="zh">宽度</rdfs:label>
        <rdfs:comment xml:lang="zh">爬梯支撑 / 电缆托架的宽度，单位 mm（v11.0 新增为 :支撑宽度；v15.6 依业务决策改名 :宽度 并将值域扩至 :电缆托架）</rdfs:comment>
    </owl:DatatypeProperty>""",
    "I-1 :支撑宽度 -> :宽度（并集域）")

# ============================================================
# F) 删除 :螺柱安装角度
# ============================================================
dele(
    """

    <owl:DatatypeProperty rdf:about="#studInstallationAngle">
        <rdf:type rdf:resource="http://www.w3.org/2002/07/owl#FunctionalProperty"/>
        <rdfs:domain rdf:resource="#Stud"/>
        <rdfs:range rdf:resource="http://www.w3.org/2001/XMLSchema#decimal"/>
        <rdfs:label xml:lang="zh">螺柱安装角度</rdfs:label>
        <rdfs:comment xml:lang="zh">防雷螺柱安装角度（逆时针为正），单位 °（v11.0 新增）</rdfs:comment>
    </owl:DatatypeProperty>

""",
    "F-1 删除 :螺柱安装角度")

# ============================================================
# E) 删除 :疲劳等级
# ============================================================
dele(
    """    <!-- 环焊缝疲劳等级 -->
    <owl:DatatypeProperty rdf:about="#fatigueDetailCategory">
        <rdfs:domain rdf:resource="#WeldSeam"/>
        <rdfs:range rdf:resource="http://www.w3.org/2001/XMLSchema#string"/>
        <rdfs:label xml:lang="zh">疲劳等级</rdfs:label>
        <rdfs:comment xml:lang="zh">焊缝疲劳 Detail category 值（v11.0 新增）</rdfs:comment>
    </owl:DatatypeProperty>

""",
    "E-1 删除 :疲劳等级")

rep("<rdfs:comment xml:lang=\"zh\">塔筒环焊缝，连接相邻筒节（v11.0 补充疲劳等级）</rdfs:comment>",
    "<rdfs:comment xml:lang=\"zh\">塔筒环焊缝，连接相邻筒节（v15.6 删除 :疲劳等级）</rdfs:comment>",
    "E-2 :环焊缝 注释更新")

# ============================================================
# D) 塔架 注释 / 头注
# ============================================================
rep("    <!-- 顶层类：塔架（抽象父类，v15.0 确立为 :塔架段 的父类） -->",
    "    <!-- 顶层类：塔架（抽象父类，v15.6 :塔架中段 直接继承） -->",
    "D-5 塔架 头注更新")

rep("        <rdfs:comment xml:lang=\"zh\">塔架，抽象父类。塔架「由」塔架段组成（对象属性 :有塔架段），且塔架段「是一种」塔架（继承关系 :塔架段 ⊂ :塔架）。本项目仅涉及塔架中段。v15.0 依专家审视 P0-1 确立 :塔架段 为 :塔架 子类</rdfs:comment>",
    "        <rdfs:comment xml:lang=\"zh\">塔架，抽象父类。塔架「由」塔架中段组成（对象属性 :有塔架中段），且塔架中段「是一种」塔架（继承关系 :塔架中段 ⊂ :塔架）。本项目仅涉及塔架中段。v15.6 扁平化：删除中间类 :塔架段，:塔架中段 直接作为 :塔架 子类</rdfs:comment>",
    "D-6 塔架 注释更新")

# ============================================================
# D) 删除 :塔架段 类
# ============================================================
dele(
    """    <!-- 塔架段（:塔架 的子类，v15.0 依 P0-1 调整） -->
    <owl:Class rdf:about="#TowerSection">
        <rdfs:subClassOf rdf:resource="#Tower"/>
        <rdfs:label xml:lang="zh">塔架段</rdfs:label>
        <rdfs:comment xml:lang="zh">塔架分段。塔架段「是一种」塔架（rdfs:subClassOf :塔架），同时塔架「由」塔架段组成（对象属性 :有塔架段）。v15.0 依专家审视 P0-1 由独立顶层类改为 :塔架 子类</rdfs:comment>
    </owl:Class>

""",
    "D-7 删除 :塔架段 类")

rep("""    <owl:Class rdf:about="#TowerMidSection">
        <rdfs:subClassOf rdf:resource="#TowerSection"/>""",
    """    <owl:Class rdf:about="#TowerMidSection">
        <rdfs:subClassOf rdf:resource="#Tower"/>""",
    "D-8 :塔架中段 直接继承 :塔架")

# ============================================================
# C) :塔架中段 上 照明系统基数约束 -> 三条照明布置约束
# ============================================================
rep(
    """        <!-- 完整性校验：有且仅有 1 个照明系统 -->
        <rdfs:subClassOf>
            <owl:Restriction>
                <owl:onProperty rdf:resource="#hasLightingSystem"/>
                <owl:qualifiedCardinality rdf:datatype="http://www.w3.org/2001/XMLSchema#nonNegativeInteger">1</owl:qualifiedCardinality>
                <owl:onClass rdf:resource="#LightingSystem"/>
            </owl:Restriction>
        </rdfs:subClassOf>""",
    """        <!-- 照明布置约束（v15.6 由 :照明系统 迁移至 :塔架中段） -->
        <!-- 第一灯安装高度 2600~3000mm -->
        <rdfs:subClassOf>
            <owl:Restriction>
                <owl:onProperty rdf:resource="#firstLightHeight"/>
                <owl:allValuesFrom>
                    <rdfs:Datatype>
                        <owl:onDatatype rdf:resource="http://www.w3.org/2001/XMLSchema#integer"/>
                        <owl:withRestrictions rdf:parseType="Collection">
                            <rdf:Description>
                                <xsd:minInclusive rdf:datatype="http://www.w3.org/2001/XMLSchema#integer">2600</xsd:minInclusive>
                            </rdf:Description>
                            <rdf:Description>
                                <xsd:maxInclusive rdf:datatype="http://www.w3.org/2001/XMLSchema#integer">3000</xsd:maxInclusive>
                            </rdf:Description>
                        </owl:withRestrictions>
                    </rdfs:Datatype>
                </owl:allValuesFrom>
            </owl:Restriction>
        </rdfs:subClassOf>
        <!-- 灯与灯最小间距 ≥ 5000mm -->
        <rdfs:subClassOf>
            <owl:Restriction>
                <owl:onProperty rdf:resource="#lightToLightMinSpacing"/>
                <owl:allValuesFrom>
                    <rdfs:Datatype>
                        <owl:onDatatype rdf:resource="http://www.w3.org/2001/XMLSchema#integer"/>
                        <owl:withRestrictions rdf:parseType="Collection">
                            <rdf:Description>
                                <xsd:minInclusive rdf:datatype="http://www.w3.org/2001/XMLSchema#integer">5000</xsd:minInclusive>
                            </rdf:Description>
                        </owl:withRestrictions>
                    </rdfs:Datatype>
                </owl:allValuesFrom>
            </owl:Restriction>
        </rdfs:subClassOf>
        <!-- 灯与灯最大间距 ≤ 10000mm -->
        <rdfs:subClassOf>
            <owl:Restriction>
                <owl:onProperty rdf:resource="#lightToLightMaxSpacing"/>
                <owl:allValuesFrom>
                    <rdfs:Datatype>
                        <owl:onDatatype rdf:resource="http://www.w3.org/2001/XMLSchema#integer"/>
                        <owl:withRestrictions rdf:parseType="Collection">
                            <rdf:Description>
                                <xsd:maxInclusive rdf:datatype="http://www.w3.org/2001/XMLSchema#integer">10000</xsd:maxInclusive>
                            </rdf:Description>
                        </owl:withRestrictions>
                    </rdfs:Datatype>
                </owl:allValuesFrom>
            </owl:Restriction>
        </rdfs:subClassOf>""",
    "C-4 :塔架中段 照明约束迁移")

# ============================================================
# C) 删除 :照明系统 类
# ============================================================
dele(
    """    <!-- 顶层类：照明系统（含约束公理） -->
    <owl:Class rdf:about="#LightingSystem">
        <rdfs:label xml:lang="zh">照明系统</rdfs:label>
        <rdfs:comment xml:lang="zh">塔内照明系统，由灯组成</rdfs:comment>
        <!-- 第一灯安装高度 2600~3000mm -->
        <rdfs:subClassOf>
            <owl:Restriction>
                <owl:onProperty rdf:resource="#firstLightHeight"/>
                <owl:allValuesFrom>
                    <rdfs:Datatype>
                        <owl:onDatatype rdf:resource="http://www.w3.org/2001/XMLSchema#integer"/>
                        <owl:withRestrictions rdf:parseType="Collection">
                            <rdf:Description>
                                <xsd:minInclusive rdf:datatype="http://www.w3.org/2001/XMLSchema#integer">2600</xsd:minInclusive>
                            </rdf:Description>
                            <rdf:Description>
                                <xsd:maxInclusive rdf:datatype="http://www.w3.org/2001/XMLSchema#integer">3000</xsd:maxInclusive>
                            </rdf:Description>
                        </owl:withRestrictions>
                    </rdfs:Datatype>
                </owl:allValuesFrom>
            </owl:Restriction>
        </rdfs:subClassOf>
        <!-- 灯与灯最小间距 ≥ 5000mm（v15.0 依 P1-5 随属性改名） -->
        <rdfs:subClassOf>
            <owl:Restriction>
                <owl:onProperty rdf:resource="#lightToLightMinSpacing"/>
                <owl:allValuesFrom>
                    <rdfs:Datatype>
                        <owl:onDatatype rdf:resource="http://www.w3.org/2001/XMLSchema#integer"/>
                        <owl:withRestrictions rdf:parseType="Collection">
                            <rdf:Description>
                                <xsd:minInclusive rdf:datatype="http://www.w3.org/2001/XMLSchema#integer">5000</xsd:minInclusive>
                            </rdf:Description>
                        </owl:withRestrictions>
                    </rdfs:Datatype>
                </owl:allValuesFrom>
            </owl:Restriction>
        </rdfs:subClassOf>
        <!-- 灯与灯最大间距 ≤ 10000mm（v15.0 依 P1-5 随属性改名） -->
        <rdfs:subClassOf>
            <owl:Restriction>
                <owl:onProperty rdf:resource="#lightToLightMaxSpacing"/>
                <owl:allValuesFrom>
                    <rdfs:Datatype>
                        <owl:onDatatype rdf:resource="http://www.w3.org/2001/XMLSchema#integer"/>
                        <owl:withRestrictions rdf:parseType="Collection">
                            <rdf:Description>
                                <xsd:maxInclusive rdf:datatype="http://www.w3.org/2001/XMLSchema#integer">10000</xsd:maxInclusive>
                            </rdf:Description>
                        </owl:withRestrictions>
                    </rdfs:Datatype>
                </owl:allValuesFrom>
            </owl:Restriction>
        </rdfs:subClassOf>
    </owl:Class>

""",
    "C-5 删除 :照明系统 类")

# ============================================================
# H) 删除 :安全锚点 类
# ============================================================
dele(
    """    <!-- 顶层类：安全锚点（v11.0 新增） -->
    <owl:Class rdf:about="#SafetyAnchor">
        <rdfs:subClassOf rdf:resource="#Accessory"/>
        <rdfs:label xml:lang="zh">安全锚点</rdfs:label>
        <rdfs:comment xml:lang="zh">爬梯安全锚点，供作业人员挂接安全带，固定在塔筒上。**计入**塔架附件排布清单（v13.0 由独立顶层类改为 :附件 子类；业务确认计入清单）</rdfs:comment>
    </owl:Class>

""",
    "H-3 删除 :安全锚点 类")

# ============================================================
# B) 删除 :材料 类
# ============================================================
dele(
    """    <!-- 顶层类：材料（v11.0 新增） -->
    <owl:Class rdf:about="#Material">
        <rdfs:label xml:lang="zh">材料</rdfs:label>
        <rdfs:comment xml:lang="zh">塔筒筒节用材料，作为**分类值**以个体形式存在（含身份键 :板材等级），承载板材等级与密度。v13.0 由「物理对象」改为「分类值」，与 :机型/:区域/:电缆托架规格 等处理方式统一</rdfs:comment>
        <owl:hasKey rdf:parseType="Collection">
            <owl:DatatypeProperty rdf:about="#plateGrade"/>
        </owl:hasKey>
    </owl:Class>

""",
    "B-3 删除 :材料 类")

# ============================================================
# G) :配件类型 去身份键 + 注释更新
# ============================================================
rep(
    """        <rdfs:comment xml:lang="zh">爬梯支撑 / 电缆托架所选用配件的类型，由附件连接方式决定（粘贴式与焊接式选用配件不同）。作为分类值以个体形式存在（含身份键 :配件类型名称）。定制化设计流程步骤8.1「替换配件类型」（v12.0 新增）</rdfs:comment>
        <owl:hasKey rdf:parseType="Collection">
            <owl:DatatypeProperty rdf:about="#fittingTypeName"/>
        </owl:hasKey>""",
    """        <rdfs:comment xml:lang="zh">爬梯支撑 / 电缆托架所选用配件的类型，由附件连接方式决定（粘贴式与焊接式选用配件不同）。作为分类值以个体形式存在。定制化设计流程步骤8.1「替换配件类型」（v12.0 新增；v15.6 删除 :配件类型名称，身份键随之移除）</rdfs:comment>""",
    "G-2 :配件类型 去身份键")

# ============================================================
# 法兰 注释更新
# ============================================================
rep("塔筒连接法兰，位于塔筒段之间，连接上下相邻塔架段（中段每段有上下两个连接法兰，v11.0 新增）",
    "塔筒连接法兰，位于塔筒段之间，连接上下相邻塔架中段（中段每段有上下两个连接法兰，v11.0 新增；v15.6 随 :塔架段 扁平化删除，措辞改为塔架中段）",
    "X-2 法兰 注释更新")

# ============================================================
# 爬梯支撑 注释更新
# ============================================================
rep("爬梯支撑结构，支撑爬梯并固定在塔筒上（v11.0 补充支撑长度/宽度）",
    "爬梯支撑结构，支撑爬梯并固定在塔筒上（v11.0 补充支撑长度/宽度；v15.6 :支撑宽度 改名 :宽度 并与 :电缆托架 共用）",
    "X-3 爬梯支撑 注释更新")

# ============================================================
# 不相交组：移出 :照明系统 / :材料 / :安全锚点
# ============================================================
dele("            <owl:Class rdf:about=\"#LightingSystem\"/>\n", "J-1 不相交组移出 :照明系统")
dele("            <owl:Class rdf:about=\"#Material\"/>\n", "J-2 不相交组移出 :材料")
dele("            <owl:Class rdf:about=\"#SafetyAnchor\"/>\n", "J-3 不相交组移出 :安全锚点")

rep("顶层物理对象类互不相交（v15.0：:塔架段 已改为 :塔架 子类故移出本组；新增 :构件）",
    "顶层物理对象类互不相交（v15.6：:照明系统 删除故移出本组）",
    "J-4 顶层不相交组注释更新")

rep("分类值类互不相交（v13.0 新增分组，与物理对象类分开声明）",
    "分类值类互不相交（v13.0 新增分组，与物理对象类分开声明；v15.6 :材料 删除故移出本组）",
    "J-5 分类值不相交组注释更新")

rep("附件子类不相交（v15.0：:螺柱/:线槽 移出；v15.1：:扶持 移出；v15.5：:电缆线夹 删除）",
    "附件子类不相交（v15.0：:螺柱/:线槽 移出；v15.1：:扶持 移出；v15.5：:电缆线夹 删除；v15.6：:安全锚点 删除）",
    "J-6 附件子类不相交组注释更新")

# ============================================================
with io.open(P, "w", encoding="utf-8") as f:
    f.write(s)

print("\n=== 共 %d 处修改完成 ===" % len(hits))
