# -*- coding: utf-8 -*-
"""
按 BPMN 流程裁剪塔架中段本体（v13.0 -> v14.0）
原则：仅删除与中段设计流程无关的「整机（风机/机头/偏航轴承）」与「其他塔架段（底段/顶段）」，
      保留抽象父类 :塔架 / :塔架段；连带删除端点已失效的对象属性。
"""
import re, io, sys

PATH = r"D:\work\Ontology\GoldenWind\ontology\TowerMidSection.owl"

s = io.open(PATH, encoding="utf-8").read()
orig = s


def rep(old, new, cnt=1):
    global s
    n = s.count(old)
    if n != cnt:
        raise SystemExit("[rep] matched %d (expect %d) for:\n%s" % (n, cnt, old[:120]))
    s = s.replace(old, new)


def del_block(tag, name):
    global s
    pat = re.compile(
        r'[ \t]*<' + tag + r' rdf:about="#' + re.escape(name) + r'">.*?</' + tag + r'>\n\n?',
        re.S)
    s, n = pat.subn('', s)
    if n != 1:
        raise SystemExit("[del_block] %s #%s matched %d" % (tag, name, n))


# ---------------------------------------------------------------- 1. 删对象属性
for n in ["hasTowerHead", "headBelongsToWindTurbine",
          "hasTower", "towerBelongsToWindTurbine",
          "supportsTowerHead", "towerHeadSupportedBy",
          "connectsTowerHead", "yawBearingAttachedToTower",
          "hasTopFlange"]:
    del_block("owl:ObjectProperty", n)

# ---------------------------------------------------------------- 2. 删类
for n in ["WindTurbine", "WindTurbineHead", "YawBearing",
          "TowerBottomSection", "TowerTopSection"]:
    del_block("owl:Class", n)

# ---------------------------------------------------------------- 3. 删孤立的注释头
rep("""    <!-- 顶层类：风机 -->
""", "")
rep("""    <!-- 顶层类：风机机头（独立顶层类） -->
""", "")
rep("""    <!-- 顶层类：偏航轴承（v10.0 新增） -->
""", "")
rep("""    <!-- ============================================================ -->
    <!-- 三、对象属性 —— 偏航轴承连接关系（v10.0 新增）                 -->
    <!-- 依据 GB/T 29717-2013：偏航轴承外圈固定在塔架顶端上法兰，      -->
    <!-- 内圈连接机舱底盘，支撑整个机舱并允许绕垂直轴旋转              -->
    <!-- ============================================================ -->

""", "")

# ---------------------------------------------------------------- 4. hasModel / hasRegion 改挂
rep("""    <owl:ObjectProperty rdf:about="#hasModel">
        <rdfs:domain rdf:resource="#WindTurbine"/>
        <rdfs:range rdf:resource="#Model"/>
        <owl:inverseOf rdf:resource="#modelBelongsToWindTurbine"/>
        <rdfs:label xml:lang="zh">有机型</rdfs:label>
        <rdfs:comment xml:lang="zh">风机（项目级）对应的机型（分类值，用对象属性指向个体以便推理同机型）。v13.0 由 :风机机头 上提至 :风机，与 :有区域 同层级</rdfs:comment>
    </owl:ObjectProperty>

    <owl:ObjectProperty rdf:about="#modelBelongsToWindTurbine">
        <rdfs:label xml:lang="zh">机型属于</rdfs:label>
        <rdfs:comment xml:lang="zh">有机型 的逆属性（v13.0 随 domain 上提改名）</rdfs:comment>
    </owl:ObjectProperty>
""",
"""    <owl:ObjectProperty rdf:about="#hasModel">
        <rdfs:domain rdf:resource="#TowerMidSection"/>
        <rdfs:range rdf:resource="#Model"/>
        <owl:inverseOf rdf:resource="#modelBelongsToTowerMidSection"/>
        <rdfs:label xml:lang="zh">有机型</rdfs:label>
        <rdfs:comment xml:lang="zh">塔架中段（项目级）对应的机型（分类值，用对象属性指向个体以便推理同机型）。v14.0 随 :风机 类删除，由 :风机 改挂至 :塔架中段</rdfs:comment>
    </owl:ObjectProperty>

    <owl:ObjectProperty rdf:about="#modelBelongsToTowerMidSection">
        <rdfs:label xml:lang="zh">机型属于</rdfs:label>
        <rdfs:comment xml:lang="zh">有机型 的逆属性（v14.0 随 domain 改挂改名）</rdfs:comment>
    </owl:ObjectProperty>
""")

rep("""    <owl:ObjectProperty rdf:about="#hasRegion">
        <rdfs:domain rdf:resource="#WindTurbine"/>
        <rdfs:range rdf:resource="#Region"/>
        <owl:inverseOf rdf:resource="#regionBelongsToWindTurbine"/>
        <rdfs:label xml:lang="zh">有区域</rdfs:label>
        <rdfs:comment xml:lang="zh">风机（项目级）适用的全球区域/国家（分类值，用对象属性指向个体以便推理同区域）。v13.0 由 :塔架中段 上提至 :风机（区域为项目级输入）</rdfs:comment>
    </owl:ObjectProperty>

    <owl:ObjectProperty rdf:about="#regionBelongsToWindTurbine">
        <rdfs:label xml:lang="zh">区域属于</rdfs:label>
        <rdfs:comment xml:lang="zh">有区域 的逆属性（v13.0 随 domain 上提改名）</rdfs:comment>
    </owl:ObjectProperty>
""",
"""    <owl:ObjectProperty rdf:about="#hasRegion">
        <rdfs:domain rdf:resource="#TowerMidSection"/>
        <rdfs:range rdf:resource="#Region"/>
        <owl:inverseOf rdf:resource="#regionBelongsToTowerMidSection"/>
        <rdfs:label xml:lang="zh">有区域</rdfs:label>
        <rdfs:comment xml:lang="zh">塔架中段（项目级）适用的全球区域/国家（分类值，用对象属性指向个体以便推理同区域）。v14.0 随 :风机 类删除，由 :风机 改挂至 :塔架中段</rdfs:comment>
    </owl:ObjectProperty>

    <owl:ObjectProperty rdf:about="#regionBelongsToTowerMidSection">
        <rdfs:label xml:lang="zh">区域属于</rdfs:label>
        <rdfs:comment xml:lang="zh">有区域 的逆属性（v14.0 随 domain 改挂改名）</rdfs:comment>
    </owl:ObjectProperty>
""")

# ---------------------------------------------------------------- 5. 保留类的注释更新
rep("""        <rdfs:comment xml:lang="zh">塔架由塔架段组成（塔架底段/塔架中段/塔架顶段）</rdfs:comment>""",
    """        <rdfs:comment xml:lang="zh">塔架由塔架段组成（本项目仅涉及塔架中段）。v14.0 保留为抽象父类</rdfs:comment>""")

rep("""        <rdfs:comment xml:lang="zh">风机塔架，独立顶层类。由塔架段组成，通过偏航轴承支撑风机机头</rdfs:comment>""",
    """        <rdfs:comment xml:lang="zh">塔架，独立顶层类。由塔架段组成（本项目仅涉及塔架中段）。v14.0 保留为抽象父类</rdfs:comment>""")

rep("""    <!-- 顶层类：塔架（v10.0 由 风机 子类改为独立顶层类） -->""",
    """    <!-- 顶层类：塔架（抽象父类，v14.0 保留） -->""")
rep("""    <!-- 顶层类：塔架段（v10.0 由 塔架 子类改为独立顶层类） -->""",
    """    <!-- 顶层类：塔架段（抽象父类，v14.0 保留） -->""")

# ---------------------------------------------------------------- 6. 不相交声明
rep("""        <rdfs:comment xml:lang="zh">顶层物理对象类互不相交（v13.0：移除 :安全锚点（已归入 :附件）、:材料（改为分类值），新增 :电缆）</rdfs:comment>
        <owl:members rdf:parseType="Collection">
            <owl:Class rdf:about="#WindTurbine"/>
            <owl:Class rdf:about="#WindTurbineHead"/>
            <owl:Class rdf:about="#YawBearing"/>
            <owl:Class rdf:about="#Tower"/>""",
"""        <rdfs:comment xml:lang="zh">顶层物理对象类互不相交（v14.0：移除 :风机/:风机机头/:偏航轴承）</rdfs:comment>
        <owl:members rdf:parseType="Collection">
            <owl:Class rdf:about="#Tower"/>""")

rep("""    <owl:AllDisjointClasses>
        <rdfs:comment xml:lang="zh">塔架段子类不相交</rdfs:comment>
        <owl:members rdf:parseType="Collection">
            <owl:Class rdf:about="#TowerBottomSection"/>
            <owl:Class rdf:about="#TowerMidSection"/>
            <owl:Class rdf:about="#TowerTopSection"/>
        </owl:members>
    </owl:AllDisjointClasses>

""", "")

# ---------------------------------------------------------------- 7. 版本与总注释
rep("""        <owl:versionInfo>v13.0</owl:versionInfo>""",
    """        <owl:versionInfo>v14.0</owl:versionInfo>""")

rep("""**逆属性约定**：仅组成关系（:有X）建逆属性，固定/安装/连接/位置/映射类关系不建逆属性（对称属性 :相邻于 自带逆关系）。</rdfs:comment>""",
    """v14.0 依 BPMN 设计流程裁剪：删除与中段设计流程无关的 :风机、:风机机头、:偏航轴承、:塔架底段、:塔架顶段 五类，及端点失效的对象属性（:有机头/:机头属于、:有塔架/:塔架属于、:支撑机头/:机头被支撑、:连接机头、:固定在塔架、:有顶法兰）；:有机型/:有区域 改挂至 :塔架中段（逆属性随之改名）；保留抽象父类 :塔架 与 :塔架段。**逆属性约定**：仅组成关系（:有X）建逆属性，固定/安装/连接/位置/映射类关系不建逆属性（对称属性 :相邻于 自带逆关系）。</rdfs:comment>""")

# ---------------------------------------------------------------- 8. 收尾：压缩多余空行
s = re.sub(r'\n{3,}', '\n\n', s)

if s == orig:
    raise SystemExit("no change!")

io.open(PATH, "w", encoding="utf-8", newline="\n").write(s)
print("OK, written. size %d -> %d" % (len(orig), len(s)))
