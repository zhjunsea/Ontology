# -*- coding: utf-8 -*-
"""
v15.4 -> v15.5 本体修正脚本
业务决策：删除线夹及相关属性
  - 类 :电缆线夹（CableClamp）
  - 对象属性 :线夹映射到爬梯支撑（clampMappedToLadderSupport）
  - 对象属性 :夹持电缆（clampsCable）
  - 数据属性 :线夹位置（clampPosition）
  - 数据属性 :是否隔开电缆线夹（isCableClampSeparated，域 :区域）
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


# 1) 删除对象属性 :线夹映射到爬梯支撑
rep(
    """    <!-- 电缆线夹固定关系 -->

    <owl:ObjectProperty rdf:about="#clampMappedToLadderSupport">
        <rdfs:domain rdf:resource="#CableClamp"/>
        <rdfs:range rdf:resource="#LadderSupport"/>
        <rdfs:label xml:lang="zh">线夹映射到爬梯支撑</rdfs:label>
        <rdfs:comment xml:lang="zh">电缆线夹与爬梯支撑的弦长映射关系（v11.0 新增）</rdfs:comment>
    </owl:ObjectProperty>

    <!-- 安全锚点固定关系 -->

    <!-- ============================================================ -->""",
    """    <!-- ============================================================ -->""",
    "1 删除 :线夹映射到爬梯支撑",
)

# 2) 删除对象属性 :夹持电缆
rep(
    """    <owl:ObjectProperty rdf:about="#clampsCable">
        <rdfs:domain rdf:resource="#CableClamp"/>
        <rdfs:range rdf:resource="#Cable"/>
        <rdfs:label xml:lang="zh">夹持电缆</rdfs:label>
        <rdfs:comment xml:lang="zh">电缆线夹夹持电缆（v13.0 新增）</rdfs:comment>
    </owl:ObjectProperty>

    <!-- ============================================================ -->""",
    """    <!-- ============================================================ -->""",
    "2 删除 :夹持电缆",
)

# 3) 删除数据属性 :线夹位置
rep(
    """    <!-- 电缆线夹（亦称电缆夹板） -->
    <owl:DatatypeProperty rdf:about="#clampPosition">
        <rdf:type rdf:resource="http://www.w3.org/2002/07/owl#FunctionalProperty"/>
        <rdfs:domain rdf:resource="#CableClamp"/>
        <rdfs:range rdf:resource="http://www.w3.org/2001/XMLSchema#decimal"/>
        <rdfs:label xml:lang="zh">线夹位置</rdfs:label>
        <rdfs:comment xml:lang="zh">电缆线夹安装高度（距底部），单位 mm（v11.0 新增）</rdfs:comment>
    </owl:DatatypeProperty>

    <!-- 安全锚点 -->""",
    """    <!-- 安全锚点 -->""",
    "3 删除 :线夹位置",
)

# 4) 删除数据属性 :是否隔开电缆线夹
rep(
    """    <!-- 区域：电缆线夹是否隔开 -->
    <owl:DatatypeProperty rdf:about="#isCableClampSeparated">
        <rdf:type rdf:resource="http://www.w3.org/2002/07/owl#FunctionalProperty"/>
        <rdfs:domain rdf:resource="#Region"/>
        <rdfs:range rdf:resource="http://www.w3.org/2001/XMLSchema#boolean"/>
        <rdfs:label xml:lang="zh">是否隔开电缆线夹</rdfs:label>
        <rdfs:comment xml:lang="zh">该区域电缆线夹是否隔开布置。**待业务确认**：原注释「国内一般隔开，国外不隔开」缺乏依据，v15.0 依 P0-3 标记为待业务确认</rdfs:comment>
    </owl:DatatypeProperty>

    <!-- ============================================================ -->""",
    """    <!-- ============================================================ -->""",
    "4 删除 :是否隔开电缆线夹",
)

# 5) 删除类 :电缆线夹
rep(
    """    <!-- 附件子类：电缆线夹（v11.0 新增，亦称电缆夹板） -->
    <owl:Class rdf:about="#CableClamp">
        <rdfs:subClassOf rdf:resource="#Accessory"/>
        <rdfs:label xml:lang="zh">电缆线夹</rdfs:label>
        <rdfs:comment xml:lang="zh">电缆线夹（亦称电缆夹板），沿高度分布夹持/固定电缆，固定在塔筒上（v11.0 新增）</rdfs:comment>
    </owl:Class>

    <owl:Class rdf:about="#CableBracket">""",
    """    <owl:Class rdf:about="#CableBracket">""",
    "5 删除类 :电缆线夹",
)

# 6) 不相交组移除 :电缆线夹
rep(
    """        <rdfs:comment xml:lang="zh">附件子类不相交（v15.0：:螺柱/:线槽 移出；v15.1：:扶持 移出）</rdfs:comment>
        <owl:members rdf:parseType="Collection">
            <owl:Class rdf:about="#LadderSupport"/>
            <owl:Class rdf:about="#CableBracket"/>
            <owl:Class rdf:about="#CableClamp"/>
            <owl:Class rdf:about="#SafetyAnchor"/>
        </owl:members>""",
    """        <rdfs:comment xml:lang="zh">附件子类不相交（v15.0：:螺柱/:线槽 移出；v15.1：:扶持 移出；v15.5：:电缆线夹 删除）</rdfs:comment>
        <owl:members rdf:parseType="Collection">
            <owl:Class rdf:about="#LadderSupport"/>
            <owl:Class rdf:about="#CableBracket"/>
            <owl:Class rdf:about="#SafetyAnchor"/>
        </owl:members>""",
    "6 不相交组移除 :电缆线夹",
)

# 7) :区域 注释更新
rep(
    """        <rdfs:comment xml:lang="zh">全球区域/国家，作为分类值以个体形式存在，便于推理"同区域"（v11.0 补充"是否隔开电缆线夹"）</rdfs:comment>""",
    """        <rdfs:comment xml:lang="zh">全球区域/国家，作为分类值以个体形式存在，便于推理"同区域"（v11.0 曾补充"是否隔开电缆线夹"，该属性已于 v15.5 随 :电缆线夹 删除）</rdfs:comment>""",
    "7 :区域 注释更新",
)

# 8) :电缆 注释更新
rep(
    """        <rdfs:comment xml:lang="zh">塔架内电缆（动力电缆/控制电缆），由线槽承载、电缆线夹夹持（v13.0 新增，补齐附件的服务对象）</rdfs:comment>""",
    """        <rdfs:comment xml:lang="zh">塔架内电缆（动力电缆/控制电缆），由线槽承载（v13.0 新增，补齐附件的服务对象；v15.5：删除 :电缆线夹 后不再由线夹夹持）</rdfs:comment>""",
    "8 :电缆 注释更新",
)

# 9) 版本信息
rep(
    """固定/安装/连接/映射类关系不建逆属性（对称属性 :相邻于 自带逆关系）。v15.4 依业务决策修正：""",
    """固定/安装/连接/映射类关系不建逆属性（对称属性 :相邻于 自带逆关系）。v15.5 依业务决策修正：**删除线夹及相关属性**——删除类 :电缆线夹（CableClamp），及对象属性 :线夹映射到爬梯支撑 / :夹持电缆、数据属性 :线夹位置 / :是否隔开电缆线夹（域 :区域）；不相交组「附件子类」由 4 类减为 3 类（:爬梯支撑 / :电缆托架 / :安全锚点）。v15.4 依业务决策修正：""",
    "9-1 总注释追加 v15.5",
)

rep(
    """        <owl:versionInfo>v15.4</owl:versionInfo>""",
    """        <owl:versionInfo>v15.5</owl:versionInfo>""",
    "9-2 versionInfo -> v15.5",
)

with io.open(P, "w", encoding="utf-8") as f:
    f.write(s)

print("\n共 %d 项修改完成。" % len(hits))
