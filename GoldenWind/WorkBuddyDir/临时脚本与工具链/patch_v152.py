# -*- coding: utf-8 -*-
"""patch_v152.py —— 塔架中段本体 v15.1 -> v15.2
业务决策：:平台 的数据属性只保留 :平台到筒顶距离 与 :平台所在处内径，
         删除 :平台宽度 / :平台集中荷载 / :栏杆高度 / :踢脚板高度。
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
    log.append("OK  %-14s (%d)" % (tag, cnt))

# 1) 删除 4 条平台数据属性
rep("""    <owl:DatatypeProperty rdf:about="#platformWidth">
        <rdfs:domain rdf:resource="#Platform"/>
        <rdfs:range rdf:resource="http://www.w3.org/2001/XMLSchema#integer"/>
        <rdfs:label xml:lang="zh">平台宽度</rdfs:label>
        <rdfs:comment xml:lang="zh">平台宽度，单位 mm（v15.0 依 P2 移除无依据的 ≥650 约束，待业务确认）</rdfs:comment>
    </owl:DatatypeProperty>

    <owl:DatatypeProperty rdf:about="#platformLoad">
        <rdfs:domain rdf:resource="#Platform"/>
        <rdfs:range rdf:resource="http://www.w3.org/2001/XMLSchema#integer"/>
        <rdfs:label xml:lang="zh">平台集中荷载</rdfs:label>
        <rdfs:comment xml:lang="zh">平台集中荷载，单位 N（v15.0 依 P2 移除无依据的 ≥1500 约束，待业务确认）</rdfs:comment>
    </owl:DatatypeProperty>

    <owl:DatatypeProperty rdf:about="#railingHeight">
        <rdfs:domain rdf:resource="#Platform"/>
        <rdfs:range rdf:resource="http://www.w3.org/2001/XMLSchema#integer"/>
        <rdfs:label xml:lang="zh">栏杆高度</rdfs:label>
        <rdfs:comment xml:lang="zh">栏杆高度，单位 mm（v15.0 依 P2 移除无依据的 900~1100 约束，待业务确认）</rdfs:comment>
    </owl:DatatypeProperty>

    <owl:DatatypeProperty rdf:about="#toeboardHeight">
        <rdfs:domain rdf:resource="#Platform"/>
        <rdfs:range rdf:resource="http://www.w3.org/2001/XMLSchema#integer"/>
        <rdfs:label xml:lang="zh">踢脚板高度</rdfs:label>
        <rdfs:comment xml:lang="zh">踢脚板高度，单位 mm（v15.0 依 P2 移除无依据的 ≥150 约束，待业务确认）</rdfs:comment>
    </owl:DatatypeProperty>

""", "", 1, "删平台4属性")

# 2) 平台到筒顶距离 注释补充（说明平台仅保留两条数据属性）
rep("<rdfs:comment xml:lang=\"zh\">归属 :平台 的数据属性，表示平台放置在距中段塔筒顶端的距离，单位 mm，固定值 1250（定位关系由对象属性 :平台到筒顶 表达，约束见 owl:Restriction）（v11.1 明确归属与语义）</rdfs:comment>",
    "<rdfs:comment xml:lang=\"zh\">归属 :平台 的数据属性，表示平台放置在距中段塔筒顶端的距离，单位 mm，固定值 1250（定位关系由对象属性 :平台到筒顶 表达，约束见 owl:Restriction）（v11.1 明确归属与语义；v15.2 起 :平台 仅保留本属性与 :平台所在处内径 两条数据属性）</rdfs:comment>",
    1, "平台到筒顶注释")

# 3) 版本号 + 总注释
rep("<owl:versionInfo>v15.1</owl:versionInfo>", "<owl:versionInfo>v15.2</owl:versionInfo>", 1, "版本号")
rep("**逆属性约定**：仅「组成关系（:有X）」与「双向定位/平齐关系（:平台到筒顶 ↔ :筒顶定位平台、:与…平齐 ↔ :平齐于）」建逆属性；固定/安装/连接/映射类关系不建逆属性（对称属性 :相邻于 自带逆关系）。",
    "v15.2 依业务决策精简：**:平台 的数据属性只保留 :平台到筒顶距离 与 :平台所在处内径**，删除 :平台宽度 / :平台集中荷载 / :栏杆高度 / :踢脚板高度（其数值约束已于 v15.0 依 P2 因缺乏依据移除）。"
    "**逆属性约定**：仅「组成关系（:有X）」与「双向定位/平齐关系（:平台到筒顶 ↔ :筒顶定位平台、:与…平齐 ↔ :平齐于）」建逆属性；固定/安装/连接/映射类关系不建逆属性（对称属性 :相邻于 自带逆关系）。",
    1, "注释")

with io.open(OWL, "w", encoding="utf-8") as f:
    f.write(s)

print("\n".join(log))
print("---")
print("字符数 %d -> %d (Δ %+d)" % (orig, len(s), len(s) - orig))
print("DONE")
