# -*- coding: utf-8 -*-
"""
v15.3 -> v15.4 本体修正脚本
业务决策：扶持删除 :扶持水平角度
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


# 1) 删除 :扶持水平角度 数据属性块
rep(
    """    <owl:DatatypeProperty rdf:about="#supportHorizontalAngle">
        <rdf:type rdf:resource="http://www.w3.org/2002/07/owl#FunctionalProperty"/>
        <rdfs:domain rdf:resource="#Support"/>
        <rdfs:range rdf:resource="http://www.w3.org/2001/XMLSchema#decimal"/>
        <rdfs:label xml:lang="zh">扶持水平角度</rdfs:label>
        <rdfs:comment xml:lang="zh">扶持周向水平位置，单位 °，按公式计算（定制化设计流程 9.1「水平位置根据公式计算」，v12.0 新增）</rdfs:comment>
    </owl:DatatypeProperty>

    <owl:DatatypeProperty rdf:about="#supportToWeldDistance">""",
    """    <owl:DatatypeProperty rdf:about="#supportToWeldDistance">""",
    "1 删除 :扶持水平角度",
)

# 2) :扶持 类注释更新
rep(
    """        <rdfs:comment xml:lang="zh">钢绳导向升降机的配套扶持件，固定在塔筒上。业务确认（v15.1）：扶持**不算附件**（其属性 :扶持位置 / :扶持水平角度 与附件不同），由 :附件 子类改挂 :构件 子类；不再继承 :附件到焊缝距离 / :附件中心间距，仅保留焊缝距离约束（单建 :扶持到焊缝距离 &gt; 100），不参与附件中心间距排布</rdfs:comment>""",
    """        <rdfs:comment xml:lang="zh">钢绳导向升降机的配套扶持件，固定在塔筒上。业务确认（v15.1）：扶持**不算附件**（其属性 :扶持位置 与附件不同），由 :附件 子类改挂 :构件 子类；不再继承 :附件到焊缝距离 / :附件中心间距，仅保留焊缝距离约束（单建 :扶持到焊缝距离 &gt; 100），不参与附件中心间距排布。v15.4：删除 :扶持水平角度</rdfs:comment>""",
    "2 :扶持 类注释更新",
)

# 3) 版本信息
rep(
    """固定/安装/连接/映射类关系不建逆属性（对称属性 :相邻于 自带逆关系）。v15.3 依业务决策修正：""",
    """固定/安装/连接/映射类关系不建逆属性（对称属性 :相邻于 自带逆关系）。v15.4 依业务决策修正：**扶持删除 :扶持水平角度**（扶持现仅保留 :扶持位置 与 :扶持到焊缝距离 两条数据属性）。v15.3 依业务决策修正：""",
    "3-1 总注释追加 v15.4",
)

rep(
    """        <owl:versionInfo>v15.3</owl:versionInfo>""",
    """        <owl:versionInfo>v15.4</owl:versionInfo>""",
    "3-2 versionInfo -> v15.4",
)

with io.open(P, "w", encoding="utf-8") as f:
    f.write(s)

print("\n共 %d 项修改完成。" % len(hits))
