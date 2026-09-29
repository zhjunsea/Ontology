# -*- coding: utf-8 -*-
"""
v15.2 -> v15.3 本体修正脚本
业务决策：
  A. 灯属性修正
     1) 一个焊接灯恰好有 2 个螺柱（:安装在螺柱 qualifiedCardinality = 2）
     2) 两个螺柱之间距离 500mm（:灯上螺柱间距 归属由 :照明系统 移至 :焊接灯）
     3) 灯的上下两个螺柱距离焊缝的距离都要 > 100mm（:灯螺柱到焊缝距离 明确，约束仍在 :焊接灯）
     4) 删除 :下灯位置 / :上灯位置
  B. 爬梯属性精简：只保留 :梯子高度
     删除 :踏级间距 / :踏面宽度 / :爬梯净宽 / :爬梯与塔壁净距
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
    rep(old, "", tag)


# ============================================================
# A-1 删除 :下灯位置 / :上灯位置
# ============================================================
dele(
    """    <!-- 灯位置 -->
    <owl:DatatypeProperty rdf:about="#lowerLightPosition">
        <rdf:type rdf:resource="http://www.w3.org/2002/07/owl#FunctionalProperty"/>
        <rdfs:domain rdf:resource="#Light"/>
        <rdfs:range rdf:resource="http://www.w3.org/2001/XMLSchema#integer"/>
        <rdfs:label xml:lang="zh">下灯位置</rdfs:label>
        <rdfs:comment xml:lang="zh">下灯距下法兰下端面的距离，单位 mm（v11.0 新增）</rdfs:comment>
    </owl:DatatypeProperty>

    <owl:DatatypeProperty rdf:about="#upperLightPosition">
        <rdf:type rdf:resource="http://www.w3.org/2002/07/owl#FunctionalProperty"/>
        <rdfs:domain rdf:resource="#Light"/>
        <rdfs:range rdf:resource="http://www.w3.org/2001/XMLSchema#integer"/>
        <rdfs:label xml:lang="zh">上灯位置</rdfs:label>
        <rdfs:comment xml:lang="zh">上灯距平台的距离，单位 mm（v11.0 新增）</rdfs:comment>
    </owl:DatatypeProperty>

    <!-- 螺柱安装角度 -->""",
    "A-1 删除 :下灯位置 / :上灯位置",
)

# ============================================================
# A-2 :灯上螺柱间距 归属由 :照明系统 移至 :焊接灯
# ============================================================
rep(
    """    <owl:DatatypeProperty rdf:about="#lightStudSpacing">
        <rdf:type rdf:resource="http://www.w3.org/2002/07/owl#FunctionalProperty"/>
        <rdfs:domain rdf:resource="#LightingSystem"/>
        <rdfs:range rdf:resource="http://www.w3.org/2001/XMLSchema#integer"/>
        <rdfs:label xml:lang="zh">灯上螺柱间距</rdfs:label>
        <rdfs:comment xml:lang="zh">同一个灯上两个螺柱之间的上下间距，单位 mm，固定值 500（业务确认：一个灯上有两个螺柱，上下排列，间距 500mm；v15.0 依 P1-5 由「螺柱间距」改名）</rdfs:comment>
    </owl:DatatypeProperty>""",
    """    <owl:DatatypeProperty rdf:about="#lightStudSpacing">
        <rdf:type rdf:resource="http://www.w3.org/2002/07/owl#FunctionalProperty"/>
        <rdfs:domain rdf:resource="#WeldedLight"/>
        <rdfs:range rdf:resource="http://www.w3.org/2001/XMLSchema#integer"/>
        <rdfs:label xml:lang="zh">灯上螺柱间距</rdfs:label>
        <rdfs:comment xml:lang="zh">同一个焊接灯上两个螺柱之间的上下间距，单位 mm，固定值 500（业务确认：一个焊接灯上有两个螺柱，上下排列，间距 500mm；v15.0 依 P1-5 由「螺柱间距」改名；v15.3 归属由 :照明系统 移至 :焊接灯）</rdfs:comment>
    </owl:DatatypeProperty>""",
    "A-2 :灯上螺柱间距 归属改 :焊接灯",
)

# ============================================================
# A-3 :灯螺柱到焊缝距离 注释明确（上下两个螺柱各自 > 100）
# ============================================================
rep(
    """        <rdfs:label xml:lang="zh">灯螺柱到焊缝距离</rdfs:label>
        <rdfs:comment xml:lang="zh">焊接灯的螺柱到焊缝上下距离，单位 mm，&gt; 100（线槽灯不适用）</rdfs:comment>""",
    """        <rdfs:label xml:lang="zh">灯螺柱到焊缝距离</rdfs:label>
        <rdfs:comment xml:lang="zh">焊接灯的螺柱到焊缝距离，单位 mm，&gt; 100（业务确认 v15.3：灯的上下两个螺柱各自到焊缝的距离均须 &gt; 100mm；线槽灯无螺柱，不适用）</rdfs:comment>""",
    "A-3 :灯螺柱到焊缝距离 注释明确",
)

# ============================================================
# A-4 从 :照明系统 移除 :灯上螺柱间距 约束
# ============================================================
rep(
    """        <!-- 灯上螺柱间距 = 500mm（同一灯上两螺柱上下间距；v15.0 依 P1-5 随属性改名） -->
        <rdfs:subClassOf>
            <owl:Restriction>
                <owl:onProperty rdf:resource="#lightStudSpacing"/>
                <owl:hasValue rdf:datatype="http://www.w3.org/2001/XMLSchema#integer">500</owl:hasValue>
            </owl:Restriction>
        </rdfs:subClassOf>
    </owl:Class>

    <!-- 顶层类：灯（v10.0 由 照明系统 子类改为独立顶层类） -->""",
    """    </owl:Class>

    <!-- 顶层类：灯（v10.0 由 照明系统 子类改为独立顶层类） -->""",
    "A-4 :照明系统 移除 :灯上螺柱间距 约束",
)

# ============================================================
# A-5 :焊接灯 类：加 2 个螺柱基数 + 灯上螺柱间距 =500 + 焊缝距离 >100
# ============================================================
rep(
    """    <owl:Class rdf:about="#WeldedLight">
        <rdfs:subClassOf rdf:resource="#Light"/>
        <rdfs:label xml:lang="zh">焊接灯</rdfs:label>
        <rdfs:comment xml:lang="zh">焊接在筒壁上的灯，通过螺柱固定在塔筒上</rdfs:comment>
        <!-- 灯螺柱到焊缝距离 > 100mm -->
        <rdfs:subClassOf>
            <owl:Restriction>
                <owl:onProperty rdf:resource="#lightStudToWeldDistance"/>
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
    </owl:Class>""",
    """    <owl:Class rdf:about="#WeldedLight">
        <rdfs:subClassOf rdf:resource="#Light"/>
        <rdfs:label xml:lang="zh">焊接灯</rdfs:label>
        <rdfs:comment xml:lang="zh">焊接在筒壁上的灯，通过螺柱固定在塔筒上。业务确认（v15.3）：一个焊接灯有且仅有 2 个螺柱（上下排列），两螺柱间距固定 500mm，且上下两个螺柱各自到焊缝的距离均 &gt; 100mm</rdfs:comment>
        <!-- 恰好 2 个螺柱（v15.3 新增：一个焊接灯有 2 个螺柱，上下排列） -->
        <rdfs:subClassOf>
            <owl:Restriction>
                <owl:onProperty rdf:resource="#mountedOnStud"/>
                <owl:qualifiedCardinality rdf:datatype="http://www.w3.org/2001/XMLSchema#nonNegativeInteger">2</owl:qualifiedCardinality>
                <owl:onClass rdf:resource="#Stud"/>
            </owl:Restriction>
        </rdfs:subClassOf>
        <!-- 灯上螺柱间距 = 500mm（同一焊接灯上两螺柱上下间距；v15.3 由 :照明系统 移至 :焊接灯） -->
        <rdfs:subClassOf>
            <owl:Restriction>
                <owl:onProperty rdf:resource="#lightStudSpacing"/>
                <owl:hasValue rdf:datatype="http://www.w3.org/2001/XMLSchema#integer">500</owl:hasValue>
            </owl:Restriction>
        </rdfs:subClassOf>
        <!-- 灯螺柱到焊缝距离 > 100mm（上下两个螺柱各自到焊缝距离均 > 100） -->
        <rdfs:subClassOf>
            <owl:Restriction>
                <owl:onProperty rdf:resource="#lightStudToWeldDistance"/>
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
    </owl:Class>""",
    "A-5 :焊接灯 加 2 螺柱基数 + 间距 500 + 焊缝距离 >100",
)

# ============================================================
# A-6 :安装在螺柱 注释补充 2 个螺柱
# ============================================================
rep(
    """        <rdfs:comment xml:lang="zh">焊接灯安装在螺柱上（替代原 :有螺柱，方向修正，v10.0）</rdfs:comment>""",
    """        <rdfs:comment xml:lang="zh">焊接灯安装在螺柱上（替代原 :有螺柱，方向修正，v10.0）。v15.3：一个焊接灯恰好对应 2 个螺柱（上下排列）</rdfs:comment>""",
    "A-6 :安装在螺柱 注释补充",
)

# ============================================================
# A-7 :螺柱 类注释补充
# ============================================================
rep(
    """        <rdfs:comment xml:lang="zh">灯安装螺柱，焊接在塔筒筒壁上，灯安装其上。业务确认（P0-2）：螺柱不属于「附件」，v15.0 由 :附件 子类改挂 :构件 子类</rdfs:comment>""",
    """        <rdfs:comment xml:lang="zh">灯安装螺柱，焊接在塔筒筒壁上，灯安装其上。业务确认（P0-2）：螺柱不属于「附件」，v15.0 由 :附件 子类改挂 :构件 子类；v15.3：一个焊接灯恰好对应 2 个螺柱（上下排列，间距 500mm）</rdfs:comment>""",
    "A-7 :螺柱 类注释补充",
)

# ============================================================
# B-1 爬梯属性精简：删除 :踏级间距 / :踏面宽度 / :爬梯净宽 / :爬梯与塔壁净距
# ============================================================
rep(
    """    <owl:DatatypeProperty rdf:about="#rungSpacing">
        <rdfs:domain rdf:resource="#Ladder"/>
        <rdfs:range rdf:resource="http://www.w3.org/2001/XMLSchema#integer"/>
        <rdfs:label xml:lang="zh">踏级间距</rdfs:label>
        <rdfs:comment xml:lang="zh">踏级间距，单位 mm（v15.0 依 P2 移除无依据的 250~300 约束，待业务确认）</rdfs:comment>
    </owl:DatatypeProperty>

    <owl:DatatypeProperty rdf:about="#treadWidth">
        <rdfs:domain rdf:resource="#Ladder"/>
        <rdfs:range rdf:resource="http://www.w3.org/2001/XMLSchema#integer"/>
        <rdfs:label xml:lang="zh">踏面宽度</rdfs:label>
        <rdfs:comment xml:lang="zh">踏面宽度，单位 mm（v15.0 依 P2 移除无依据的 ≥80 约束，待业务确认）</rdfs:comment>
    </owl:DatatypeProperty>

    <owl:DatatypeProperty rdf:about="#ladderClearWidth">
        <rdfs:domain rdf:resource="#Ladder"/>
        <rdfs:range rdf:resource="http://www.w3.org/2001/XMLSchema#integer"/>
        <rdfs:label xml:lang="zh">爬梯净宽</rdfs:label>
        <rdfs:comment xml:lang="zh">爬梯净宽，单位 mm（v15.0 依 P2 移除无依据的 ≥340 约束，待业务确认）</rdfs:comment>
    </owl:DatatypeProperty>

    <owl:DatatypeProperty rdf:about="#ladderToWallDistance">
        <rdfs:domain rdf:resource="#Ladder"/>
        <rdfs:range rdf:resource="http://www.w3.org/2001/XMLSchema#integer"/>
        <rdfs:label xml:lang="zh">爬梯与塔壁净距</rdfs:label>
        <rdfs:comment xml:lang="zh">爬梯与塔壁净距，单位 mm（v15.0 依 P2 移除无依据的 ≥150 约束，待业务确认）</rdfs:comment>
    </owl:DatatypeProperty>

    <owl:DatatypeProperty rdf:about="#ladderHeight">""",
    """    <owl:DatatypeProperty rdf:about="#ladderHeight">""",
    "B-1 删除爬梯 4 条属性（只保留 :梯子高度）",
)

# ============================================================
# C 版本信息
# ============================================================
rep(
    """固定/安装/连接/映射类关系不建逆属性（对称属性 :相邻于 自带逆关系）。</rdfs:comment>""",
    """固定/安装/连接/映射类关系不建逆属性（对称属性 :相邻于 自带逆关系）。v15.3 依业务决策修正：**(A) 灯属性**——:焊接灯 恰好有 2 个螺柱（:安装在螺柱 qualifiedCardinality = 2）；:灯上螺柱间距 归属由 :照明系统 移至 :焊接灯（=500，同一焊接灯上两螺柱上下间距）；:灯螺柱到焊缝距离 明确为灯的上下两个螺柱各自到焊缝距离均 &gt; 100；删除 :下灯位置 / :上灯位置（v11.0 遗留，与现流程不符）。**(B) 爬梯属性精简**——:爬梯 只保留 :梯子高度，删除 :踏级间距 / :踏面宽度 / :爬梯净宽 / :爬梯与塔壁净距（其数值约束已于 v15.0 依 P2 因缺乏依据移除）。</rdfs:comment>""",
    "C-1 总注释追加 v15.3",
)

rep(
    """        <owl:versionInfo>v15.2</owl:versionInfo>""",
    """        <owl:versionInfo>v15.3</owl:versionInfo>""",
    "C-2 versionInfo -> v15.3",
)

with io.open(P, "w", encoding="utf-8") as f:
    f.write(s)

print("\n共 %d 项修改完成。" % len(hits))
