# -*- coding: utf-8 -*-
"""patch_v151.py —— 塔架中段本体 v15.0 -> v15.1
业务决策：:扶持 改挂 :构件（不算附件，因其属性与附件不同）；
         仅保留焊缝距离约束 —— 单建 :扶持到焊缝距离（>100），不参与附件中心间距排布。
"""
import io, os

OWL = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)),
                                    "..", "..", "ontology", "TowerMidSection.owl"))
with io.open(OWL, "r", encoding="utf-8") as f:
    s = f.read()
orig = len(s)
log = []

def rep(old, new, cnt=1, tag=""):
    global s
    n = s.count(old)
    if n != cnt:
        raise SystemExit("[FAIL] %s: 期望 %d 处，实际 %d 处\n---\n%s\n---" % (tag, cnt, n, old[:300]))
    s = s.replace(old, new)
    log.append("OK  %-16s (%d)" % (tag, cnt))

# 1) 本体总注释 + 版本号
rep("**逆属性约定**：仅「组成关系（:有X）」与「双向定位/平齐关系（:平台到筒顶 ↔ :筒顶定位平台、:与…平齐 ↔ :平齐于）」建逆属性；固定/安装/连接/映射类关系不建逆属性（对称属性 :相邻于 自带逆关系）。",
    "v15.1 依业务决策修正：**:扶持 由 :附件 改挂 :构件**（业务确认：扶持不算附件，因其属性与附件不同）；扶持不再继承 :附件到焊缝距离 / :附件中心间距，**仅保留焊缝距离约束**——单建数据属性 :扶持到焊缝距离（>100），不参与附件中心间距排布。"
    "**逆属性约定**：仅「组成关系（:有X）」与「双向定位/平齐关系（:平台到筒顶 ↔ :筒顶定位平台、:与…平齐 ↔ :平齐于）」建逆属性；固定/安装/连接/映射类关系不建逆属性（对称属性 :相邻于 自带逆关系）。",
    1, "注释")
rep("<owl:versionInfo>v15.0</owl:versionInfo>", "<owl:versionInfo>v15.1</owl:versionInfo>", 1, "版本号")

# 2) 新增数据属性 :扶持到焊缝距离（紧随 :扶持水平角度）
rep("""        <rdfs:label xml:lang="zh">扶持水平角度</rdfs:label>
        <rdfs:comment xml:lang="zh">扶持周向水平位置，单位 °，按公式计算（定制化设计流程 9.1「水平位置根据公式计算」，v12.0 新增）</rdfs:comment>
    </owl:DatatypeProperty>
""",
    """        <rdfs:label xml:lang="zh">扶持水平角度</rdfs:label>
        <rdfs:comment xml:lang="zh">扶持周向水平位置，单位 °，按公式计算（定制化设计流程 9.1「水平位置根据公式计算」，v12.0 新增）</rdfs:comment>
    </owl:DatatypeProperty>

    <owl:DatatypeProperty rdf:about="#supportToWeldDistance">
        <rdfs:domain rdf:resource="#Support"/>
        <rdfs:range rdf:resource="http://www.w3.org/2001/XMLSchema#integer"/>
        <rdfs:label xml:lang="zh">扶持到焊缝距离</rdfs:label>
        <rdfs:comment xml:lang="zh">扶持到环焊缝上下距离，单位 mm，&gt; 100（约束见 owl:Restriction）。v15.1 新增：扶持改挂 :构件 后不再继承 :附件到焊缝距离，故单建本属性以保留焊缝距离约束</rdfs:comment>
    </owl:DatatypeProperty>
""", 1, "扶持到焊缝距离")

# 3) :扶持 改挂 :构件 + 注释 + 约束
rep("""    <owl:Class rdf:about="#Support">
        <rdfs:subClassOf rdf:resource="#Accessory"/>
        <rdfs:label xml:lang="zh">扶持</rdfs:label>
        <rdfs:comment xml:lang="zh">钢绳导向升降机的配套扶持件，固定在塔筒上，**计入**塔架附件排布清单，参与附件间距排布（v11.2 确认）</rdfs:comment>
    </owl:Class>""",
    """    <owl:Class rdf:about="#Support">
        <rdfs:subClassOf rdf:resource="#Component"/>
        <rdfs:label xml:lang="zh">扶持</rdfs:label>
        <rdfs:comment xml:lang="zh">钢绳导向升降机的配套扶持件，固定在塔筒上。业务确认（v15.1）：扶持**不算附件**（其属性 :扶持位置 / :扶持水平角度 与附件不同），由 :附件 子类改挂 :构件 子类；不再继承 :附件到焊缝距离 / :附件中心间距，仅保留焊缝距离约束（单建 :扶持到焊缝距离 &gt; 100），不参与附件中心间距排布</rdfs:comment>
        <!-- 扶持到焊缝距离 > 100mm（v15.1 新增，替代原继承自 :附件 的约束） -->
        <rdfs:subClassOf>
            <owl:Restriction>
                <owl:onProperty rdf:resource="#supportToWeldDistance"/>
                <owl:allValuesFrom>
                    <rdfs:Datatype>
                        <owl:onDatatype rdf:resource="http://www.w3.org/2001/XMLSchema#integer"/>
                        <owl:withRestrictions rdf:parseType="Collection">
                            <rdf:Description>
                                <xsd:minExclusive rdf:datatype="http://www.w3.org/2001/XMLSchema#integer">100</xsd:minExclusive>
                            </rdf:Description>
                        </owl:withRestrictions>
                    </rdfs:Datatype>
                </owl:allValuesFrom>
            </owl:Restriction>
        </rdfs:subClassOf>
    </owl:Class>""", 1, "扶持改挂")

# 4) :构件 类注释补充扶持
rep("<rdfs:comment xml:lang=\"zh\">塔内构件：同样固定在塔筒上、但不属于「附件」的零件。业务确认（P0-2）：附件不包括螺柱，也不包括线槽，故将 :螺柱、:线槽 由 :附件 子类改挂 :构件 子类（v15.0 新增）</rdfs:comment>",
    "<rdfs:comment xml:lang=\"zh\">塔内构件：同样固定在塔筒上、但不属于「附件」的零件。业务确认（P0-2）：附件不包括螺柱，也不包括线槽，故将 :螺柱、:线槽 由 :附件 子类改挂 :构件 子类（v15.0 新增）；业务确认（v15.1）：扶持亦不算附件（其属性与附件不同），由 :附件 改挂 :构件</rdfs:comment>",
    1, "构件注释")

# 5) :有构件 注释补充扶持
rep("<rdfs:comment xml:lang=\"zh\">塔架中段包含构件（螺柱、线槽等，v15.0 依 P0-2 新增，与 :有附件 并列）</rdfs:comment>",
    "<rdfs:comment xml:lang=\"zh\">塔架中段包含构件（螺柱、线槽、扶持等，v15.0 依 P0-2 新增，与 :有附件 并列；v15.1 加入扶持）</rdfs:comment>",
    1, "有构件注释")

# 6) 不相交组
rep("""    <owl:AllDisjointClasses>
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
    </owl:AllDisjointClasses>""",
    """    <owl:AllDisjointClasses>
        <rdfs:comment xml:lang="zh">附件子类不相交（v15.0：:螺柱/:线槽 移出；v15.1：:扶持 移出）</rdfs:comment>
        <owl:members rdf:parseType="Collection">
            <owl:Class rdf:about="#LadderSupport"/>
            <owl:Class rdf:about="#CableBracket"/>
            <owl:Class rdf:about="#CableClamp"/>
            <owl:Class rdf:about="#SafetyAnchor"/>
        </owl:members>
    </owl:AllDisjointClasses>

    <owl:AllDisjointClasses>
        <rdfs:comment xml:lang="zh">构件子类不相交（v15.0 新增；v15.1 加入 :扶持）</rdfs:comment>
        <owl:members rdf:parseType="Collection">
            <owl:Class rdf:about="#Stud"/>
            <owl:Class rdf:about="#CableTray"/>
            <owl:Class rdf:about="#Support"/>
        </owl:members>
    </owl:AllDisjointClasses>""", 1, "不相交组")

with io.open(OWL, "w", encoding="utf-8") as f:
    f.write(s)

print("\n".join(log))
print("---")
print("字符数 %d -> %d (Δ %+d)" % (orig, len(s), len(s) - orig))
print("DONE")
