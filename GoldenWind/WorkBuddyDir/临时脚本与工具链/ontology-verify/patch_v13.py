# -*- coding: utf-8 -*-
"""v12.0 -> v13.0 结构化改造（依专家审视建议报告）"""
import re, shutil, sys

P = r"D:\work\Ontology\GoldenWind\ontology\TowerMidSection.owl"
shutil.copyfile(P, P + ".bak2")
s = open(P, encoding='utf-8').read()
orig_len = len(s)

def rep(old, new, label):
    global s
    if old not in s:
        raise SystemExit("NOT FOUND: " + label)
    if s.count(old) != 1:
        raise SystemExit("NOT UNIQUE(%d): %s" % (s.count(old), label))
    s = s.replace(old, new, 1)

def del_block(kind, name):
    global s
    pat = re.compile(r'\n    <owl:' + kind + r' rdf:about="#' + re.escape(name) + r'">.*?\n    </owl:' + kind + r'>', re.S)
    s2, n = pat.subn('', s)
    if n != 1:
        raise SystemExit("DEL FAIL %s %s -> %d" % (kind, name, n))
    s = s2

# ============================================================
# 1. 删除冗余对象属性
# ============================================================
for n in ["bracketAttachedToTowerTube", "platformAttachedToTowerTube", "studAttachedToTowerTube",
          "trayAttachedToTowerTube", "supportAttachedToTowerTube", "clampAttachedToTowerTube",
          "anchorAttachedToTowerTube", "sameSideLadderSupport", "sameSideCableBracket"]:
    del_block("ObjectProperty", n)

# 2. 删除冗余数据属性
del_block("DatatypeProperty", "supportToWeldDistance")

# 3. 删除 :Support 上的冗余约束
s = re.sub(r'\n        <!-- 扶持到焊缝距离 > 100mm -->.*?\n        </rdfs:subClassOf>', '', s, count=1, flags=re.S)
if "supportToWeldDistance" in s:
    raise SystemExit("supportToWeldDistance 残留")

# ============================================================
# 4. 合并「固定在塔筒」：domain 改为 unionOf(:附件, :平台)
# ============================================================
rep(
'''    <owl:ObjectProperty rdf:about="#attachedToTowerTube">
        <rdfs:domain rdf:resource="#LadderSupport"/>
        <rdfs:range rdf:resource="#TowerTube"/>
        <rdfs:label xml:lang="zh">固定在塔筒</rdfs:label>
        <rdfs:comment xml:lang="zh">爬梯支撑固定在塔筒上</rdfs:comment>
    </owl:ObjectProperty>''',
'''    <owl:ObjectProperty rdf:about="#attachedToTowerTube">
        <rdfs:domain>
            <owl:Class>
                <owl:unionOf rdf:parseType="Collection">
                    <owl:Class rdf:about="#Accessory"/>
                    <owl:Class rdf:about="#Platform"/>
                </owl:unionOf>
            </owl:Class>
        </rdfs:domain>
        <rdfs:range rdf:resource="#TowerTube"/>
        <rdfs:label xml:lang="zh">固定在塔筒</rdfs:label>
        <rdfs:comment xml:lang="zh">零件固定在塔筒上。v13.0 合并原 8 个同义属性（爬梯支撑/电缆托架/平台/螺柱/线槽/扶持/电缆线夹/安全锚点各建一个），domain 统一为 :附件 ∪ :平台</rdfs:comment>
    </owl:ObjectProperty>''',
"attachedToTowerTube merge")

# ============================================================
# 5. :有连接方式 domain 收窄
# ============================================================
rep(
'''    <owl:ObjectProperty rdf:about="#hasConnectionType">
        <rdfs:domain rdf:resource="#Accessory"/>
        <rdfs:range rdf:resource="#AccessoryConnectionType"/>''',
'''    <owl:ObjectProperty rdf:about="#hasConnectionType">
        <rdfs:domain>
            <owl:Class>
                <owl:unionOf rdf:parseType="Collection">
                    <owl:Class rdf:about="#LadderSupport"/>
                    <owl:Class rdf:about="#CableBracket"/>
                </owl:unionOf>
            </owl:Class>
        </rdfs:domain>
        <rdfs:range rdf:resource="#AccessoryConnectionType"/>''',
"hasConnectionType domain")

# ============================================================
# 6. :固定在塔架 值域 -> :法兰
# ============================================================
rep(
'''    <owl:ObjectProperty rdf:about="#yawBearingAttachedToTower">
        <rdfs:domain rdf:resource="#YawBearing"/>
        <rdfs:range rdf:resource="#TowerTopSection"/>
        <rdfs:label xml:lang="zh">固定在塔架</rdfs:label>
        <rdfs:comment xml:lang="zh">偏航轴承外圈固定在塔架顶端上法兰；塔架顶法兰位于 :搭建顶段，故值域由 :塔架 收窄至 :搭建顶段（v11.2 确认）</rdfs:comment>
    </owl:ObjectProperty>''',
'''    <owl:ObjectProperty rdf:about="#yawBearingAttachedToTower">
        <rdfs:domain rdf:resource="#YawBearing"/>
        <rdfs:range rdf:resource="#Flange"/>
        <rdfs:label xml:lang="zh">固定在塔架</rdfs:label>
        <rdfs:comment xml:lang="zh">偏航轴承外圈固定在塔架顶法兰（:法兰 个体）上。v13.0 修正原「值域=:搭建顶段」的层级跳跃：轴承固定在法兰零件上，法兰再归属于塔架顶段（见 :有顶法兰）</rdfs:comment>
    </owl:ObjectProperty>''',
"yawBearingAttachedToTower range")

# ============================================================
# 7. :有机型 / :有区域 上提至 :风机（项目级）
# ============================================================
rep(
'''    <owl:ObjectProperty rdf:about="#hasModel">
        <rdfs:domain rdf:resource="#WindTurbineHead"/>
        <rdfs:range rdf:resource="#Model"/>
        <owl:inverseOf rdf:resource="#modelBelongsToWindTurbineHead"/>
        <rdfs:label xml:lang="zh">有机型</rdfs:label>
        <rdfs:comment xml:lang="zh">风机机头对应的机型（分类值，用对象属性指向个体以便推理同机型）</rdfs:comment>
    </owl:ObjectProperty>''',
'''    <owl:ObjectProperty rdf:about="#hasModel">
        <rdfs:domain rdf:resource="#WindTurbine"/>
        <rdfs:range rdf:resource="#Model"/>
        <owl:inverseOf rdf:resource="#modelBelongsToWindTurbine"/>
        <rdfs:label xml:lang="zh">有机型</rdfs:label>
        <rdfs:comment xml:lang="zh">风机（项目级）对应的机型（分类值，用对象属性指向个体以便推理同机型）。v13.0 由 :风机机头 上提至 :风机，与 :有区域 同层级</rdfs:comment>
    </owl:ObjectProperty>''',
"hasModel domain")

rep(
'''    <owl:ObjectProperty rdf:about="#modelBelongsToWindTurbineHead">
        <rdfs:label xml:lang="zh">机型属于</rdfs:label>
        <rdfs:comment xml:lang="zh">有机型 的逆属性</rdfs:comment>
    </owl:ObjectProperty>''',
'''    <owl:ObjectProperty rdf:about="#modelBelongsToWindTurbine">
        <rdfs:label xml:lang="zh">机型属于</rdfs:label>
        <rdfs:comment xml:lang="zh">有机型 的逆属性（v13.0 随 domain 上提改名）</rdfs:comment>
    </owl:ObjectProperty>''',
"modelBelongsToWindTurbine rename")

rep(
'''    <owl:ObjectProperty rdf:about="#hasRegion">
        <rdfs:domain rdf:resource="#TowerMidSection"/>
        <rdfs:range rdf:resource="#Region"/>
        <owl:inverseOf rdf:resource="#regionBelongsToTowerMidSection"/>
        <rdfs:label xml:lang="zh">有区域</rdfs:label>
        <rdfs:comment xml:lang="zh">塔架中段适用的全球区域/国家（分类值，用对象属性指向个体以便推理同区域）</rdfs:comment>
    </owl:ObjectProperty>''',
'''    <owl:ObjectProperty rdf:about="#hasRegion">
        <rdfs:domain rdf:resource="#WindTurbine"/>
        <rdfs:range rdf:resource="#Region"/>
        <owl:inverseOf rdf:resource="#regionBelongsToWindTurbine"/>
        <rdfs:label xml:lang="zh">有区域</rdfs:label>
        <rdfs:comment xml:lang="zh">风机（项目级）适用的全球区域/国家（分类值，用对象属性指向个体以便推理同区域）。v13.0 由 :塔架中段 上提至 :风机（区域为项目级输入）</rdfs:comment>
    </owl:ObjectProperty>''',
"hasRegion domain")

rep(
'''    <owl:ObjectProperty rdf:about="#regionBelongsToTowerMidSection">
        <rdfs:label xml:lang="zh">区域属于</rdfs:label>
        <rdfs:comment xml:lang="zh">有区域 的逆属性</rdfs:comment>
    </owl:ObjectProperty>''',
'''    <owl:ObjectProperty rdf:about="#regionBelongsToWindTurbine">
        <rdfs:label xml:lang="zh">区域属于</rdfs:label>
        <rdfs:comment xml:lang="zh">有区域 的逆属性（v13.0 随 domain 上提改名）</rdfs:comment>
    </owl:ObjectProperty>''',
"regionBelongsToWindTurbine rename")

# ============================================================
# 8. :与…平齐 增加 Functional + InverseFunctional
# ============================================================
rep(
'''    <owl:ObjectProperty rdf:about="#flushWith">
        <rdfs:domain rdf:resource="#LadderSupport"/>''',
'''    <owl:ObjectProperty rdf:about="#flushWith">
        <rdf:type rdf:resource="http://www.w3.org/2002/07/owl#FunctionalProperty"/>
        <rdf:type rdf:resource="http://www.w3.org/2002/07/owl#InverseFunctionalProperty"/>
        <rdfs:domain rdf:resource="#LadderSupport"/>''',
"flushWith functional")

# ============================================================
# 9. 新增对象属性（v13.0）
# ============================================================
NEW_OBJ = '''    <!-- ============================================================ -->
    <!-- 六之三、对象属性 —— v13.0 新增（专家审视修正）                 -->
    <!-- ============================================================ -->

    <owl:ObjectProperty rdf:about="#hasTopFlange">
        <rdfs:domain rdf:resource="#TowerTopSection"/>
        <rdfs:range rdf:resource="#Flange"/>
        <rdfs:label xml:lang="zh">有顶法兰</rdfs:label>
        <rdfs:comment xml:lang="zh">塔架顶段顶部的连接法兰（塔架顶法兰），偏航轴承外圈固定于此（v13.0 新增，修正 :固定在塔架 的层级跳跃）</rdfs:comment>
    </owl:ObjectProperty>

    <owl:ObjectProperty rdf:about="#flangeConnectsTowerSection">
        <rdfs:domain rdf:resource="#Flange"/>
        <rdfs:range rdf:resource="#TowerSection"/>
        <rdfs:label xml:lang="zh">法兰连接塔架段</rdfs:label>
        <rdfs:comment xml:lang="zh">法兰连接上下相邻的塔架段（v13.0 新增，补齐法兰-段关系）</rdfs:comment>
    </owl:ObjectProperty>

    <owl:ObjectProperty rdf:about="#supportsCableTray">
        <rdfs:domain rdf:resource="#CableBracket"/>
        <rdfs:range rdf:resource="#CableTray"/>
        <rdfs:label xml:lang="zh">支撑线槽</rdfs:label>
        <rdfs:comment xml:lang="zh">电缆托架支撑线槽（v13.0 新增）</rdfs:comment>
    </owl:ObjectProperty>

    <owl:ObjectProperty rdf:about="#passesThroughPlatform">
        <rdfs:domain rdf:resource="#Ladder"/>
        <rdfs:range rdf:resource="#Platform"/>
        <rdfs:label xml:lang="zh">穿过平台</rdfs:label>
        <rdfs:comment xml:lang="zh">爬梯穿过平台开口（v13.0 新增）</rdfs:comment>
    </owl:ObjectProperty>

    <owl:ObjectProperty rdf:about="#hasSafetyAnchor">
        <rdfs:domain rdf:resource="#Ladder"/>
        <rdfs:range rdf:resource="#SafetyAnchor"/>
        <rdfs:label xml:lang="zh">有安全锚点</rdfs:label>
        <rdfs:comment xml:lang="zh">爬梯配套的安全锚点（v13.0 新增，补齐锚点-爬梯关系）</rdfs:comment>
    </owl:ObjectProperty>

    <owl:ObjectProperty rdf:about="#docksAtPlatform">
        <rdfs:domain rdf:resource="#Elevator"/>
        <rdfs:range rdf:resource="#Platform"/>
        <rdfs:label xml:lang="zh">停靠平台</rdfs:label>
        <rdfs:comment xml:lang="zh">升降机在平台层停靠（v13.0 新增）</rdfs:comment>
    </owl:ObjectProperty>

    <owl:ObjectProperty rdf:about="#adjacentTo">
        <rdf:type rdf:resource="http://www.w3.org/2002/07/owl#SymmetricProperty"/>
        <rdfs:domain rdf:resource="#TowerSection"/>
        <rdfs:range rdf:resource="#TowerSection"/>
        <rdfs:label xml:lang="zh">相邻于</rdfs:label>
        <rdfs:comment xml:lang="zh">塔架段上下相邻（对称属性，自带逆关系，v13.0 新增）</rdfs:comment>
    </owl:ObjectProperty>

    <owl:ObjectProperty rdf:about="#carriesCable">
        <rdfs:domain rdf:resource="#CableTray"/>
        <rdfs:range rdf:resource="#Cable"/>
        <rdfs:label xml:lang="zh">承载电缆</rdfs:label>
        <rdfs:comment xml:lang="zh">线槽承载电缆（v13.0 新增）</rdfs:comment>
    </owl:ObjectProperty>

    <owl:ObjectProperty rdf:about="#clampsCable">
        <rdfs:domain rdf:resource="#CableClamp"/>
        <rdfs:range rdf:resource="#Cable"/>
        <rdfs:label xml:lang="zh">夹持电缆</rdfs:label>
        <rdfs:comment xml:lang="zh">电缆线夹夹持电缆（v13.0 新增）</rdfs:comment>
    </owl:ObjectProperty>

'''
ANCHOR_OBJ = '''    <!-- ============================================================ -->
    <!-- 七、数据属性                                                  -->'''
rep(ANCHOR_OBJ, NEW_OBJ + ANCHOR_OBJ, "insert new object properties")

# ============================================================
# 10. :壁厚 -> :平均壁厚
# ============================================================
rep(
'''    <owl:DatatypeProperty rdf:about="#wallThickness">
        <rdfs:domain rdf:resource="#TowerTube"/>
        <rdfs:range rdf:resource="http://www.w3.org/2001/XMLSchema#decimal"/>
        <rdfs:label xml:lang="zh">壁厚</rdfs:label>
        <rdfs:comment xml:lang="zh">塔筒壁厚，单位 mm</rdfs:comment>
    </owl:DatatypeProperty>''',
'''    <owl:DatatypeProperty rdf:about="#averageWallThickness">
        <rdfs:domain rdf:resource="#TowerTube"/>
        <rdfs:range rdf:resource="http://www.w3.org/2001/XMLSchema#decimal"/>
        <rdfs:label xml:lang="zh">平均壁厚</rdfs:label>
        <rdfs:comment xml:lang="zh">塔筒平均壁厚，单位 mm。**派生值**（由 :筒节壁厚 汇总），非权威值；权威值以筒节级 :筒节壁厚 为准（v13.0 由 :壁厚 改名并标明派生）</rdfs:comment>
    </owl:DatatypeProperty>''',
"wallThickness rename")

# ============================================================
# 11. :板材等级 升为身份键（Functional）
# ============================================================
rep(
'''    <owl:DatatypeProperty rdf:about="#plateGrade">
        <rdfs:domain rdf:resource="#Material"/>
        <rdfs:range rdf:resource="http://www.w3.org/2001/XMLSchema#string"/>
        <rdfs:label xml:lang="zh">板材等级</rdfs:label>
        <rdfs:comment xml:lang="zh">认证参考板材等级（如 Q355C，v11.0 新增）</rdfs:comment>
    </owl:DatatypeProperty>''',
'''    <owl:DatatypeProperty rdf:about="#plateGrade">
        <rdf:type rdf:resource="http://www.w3.org/2002/07/owl#FunctionalProperty"/>
        <rdfs:domain rdf:resource="#Material"/>
        <rdfs:range rdf:resource="http://www.w3.org/2001/XMLSchema#string"/>
        <rdfs:label xml:lang="zh">板材等级</rdfs:label>
        <rdfs:comment xml:lang="zh">认证参考板材等级（如 Q355C）。v13.0 升为 :材料 的身份键（owl:hasKey）</rdfs:comment>
    </owl:DatatypeProperty>''',
"plateGrade functional")

# ============================================================
# 12. 类：:安全锚点 归入 :附件
# ============================================================
rep(
'''    <owl:Class rdf:about="#SafetyAnchor">
        <rdfs:label xml:lang="zh">安全锚点</rdfs:label>
        <rdfs:comment xml:lang="zh">爬梯安全锚点，供作业人员挂接安全带，固定在塔筒上（v11.0 新增）</rdfs:comment>
    </owl:Class>''',
'''    <owl:Class rdf:about="#SafetyAnchor">
        <rdfs:subClassOf rdf:resource="#Accessory"/>
        <rdfs:label xml:lang="zh">安全锚点</rdfs:label>
        <rdfs:comment xml:lang="zh">爬梯安全锚点，供作业人员挂接安全带，固定在塔筒上。**计入**塔架附件排布清单（v13.0 由独立顶层类改为 :附件 子类；业务确认计入清单）</rdfs:comment>
    </owl:Class>''',
"SafetyAnchor subclass")

# ============================================================
# 13. 类：:材料 改为分类值（hasKey）
# ============================================================
rep(
'''    <owl:Class rdf:about="#Material">
        <rdfs:label xml:lang="zh">材料</rdfs:label>
        <rdfs:comment xml:lang="zh">塔筒筒节用材料，承载板材等级与密度（v11.0 新增）</rdfs:comment>
    </owl:Class>''',
'''    <owl:Class rdf:about="#Material">
        <rdfs:label xml:lang="zh">材料</rdfs:label>
        <rdfs:comment xml:lang="zh">塔筒筒节用材料，作为**分类值**以个体形式存在（含身份键 :板材等级），承载板材等级与密度。v13.0 由「物理对象」改为「分类值」，与 :机型/:区域/:电缆托架规格 等处理方式统一</rdfs:comment>
        <owl:hasKey rdf:parseType="Collection">
            <owl:DatatypeProperty rdf:about="#plateGrade"/>
        </owl:hasKey>
    </owl:Class>''',
"Material hasKey")

# ============================================================
# 14. 类：新增 :电缆
# ============================================================
NEW_CABLE = '''    <!-- 顶层类：电缆（v13.0 新增） -->
    <owl:Class rdf:about="#Cable">
        <rdfs:label xml:lang="zh">电缆</rdfs:label>
        <rdfs:comment xml:lang="zh">塔架内电缆（动力电缆/控制电缆），由线槽承载、电缆线夹夹持（v13.0 新增，补齐附件的服务对象）</rdfs:comment>
    </owl:Class>

'''
ANCHOR_DISJOINT = '''    <!-- ============================================================ -->
    <!-- 九、不相交类声明                                              -->'''
rep(ANCHOR_DISJOINT, NEW_CABLE + ANCHOR_DISJOINT, "insert Cable class")

# ============================================================
# 15. 类：:塔架中段 增加 :有附件 min 1
# ============================================================
rep(
'''                <owl:onClass rdf:resource="#LightingSystem"/>
            </owl:Restriction>
        </rdfs:subClassOf>
    </owl:Class>''',
'''                <owl:onClass rdf:resource="#LightingSystem"/>
            </owl:Restriction>
        </rdfs:subClassOf>
        <!-- 完整性校验：至少有 1 个附件（v13.0） -->
        <rdfs:subClassOf>
            <owl:Restriction>
                <owl:onProperty rdf:resource="#hasAccessory"/>
                <owl:minQualifiedCardinality rdf:datatype="http://www.w3.org/2001/XMLSchema#nonNegativeInteger">1</owl:minQualifiedCardinality>
                <owl:onClass rdf:resource="#Accessory"/>
            </owl:Restriction>
        </rdfs:subClassOf>
    </owl:Class>''',
"TowerMidSection hasAccessory")

# ============================================================
# 16. 类：升降机子类基数约束
# ============================================================
rep(
'''    <owl:Class rdf:about="#RopeGuidedElevator">
        <rdfs:subClassOf rdf:resource="#Elevator"/>
        <rdfs:label xml:lang="zh">钢绳导向升降机</rdfs:label>
        <rdfs:comment xml:lang="zh">钢绳导向升降机，通过扶持固定在塔筒上</rdfs:comment>
    </owl:Class>''',
'''    <owl:Class rdf:about="#RopeGuidedElevator">
        <rdfs:subClassOf rdf:resource="#Elevator"/>
        <rdfs:label xml:lang="zh">钢绳导向升降机</rdfs:label>
        <rdfs:comment xml:lang="zh">钢绳导向升降机，通过扶持固定在塔筒上</rdfs:comment>
        <!-- 完整性校验：钢绳导向升降机至少有 1 个扶持（v13.0） -->
        <rdfs:subClassOf>
            <owl:Restriction>
                <owl:onProperty rdf:resource="#hasSupport"/>
                <owl:minQualifiedCardinality rdf:datatype="http://www.w3.org/2001/XMLSchema#nonNegativeInteger">1</owl:minQualifiedCardinality>
                <owl:onClass rdf:resource="#Support"/>
            </owl:Restriction>
        </rdfs:subClassOf>
    </owl:Class>''',
"RopeGuidedElevator minSupport")

rep(
'''    <owl:Class rdf:about="#LadderGuidedElevator">
        <rdfs:subClassOf rdf:resource="#Elevator"/>
        <rdfs:label xml:lang="zh">爬梯导向升降机</rdfs:label>
        <rdfs:comment xml:lang="zh">爬梯导向升降机，以塔筒内壁爬梯为导向</rdfs:comment>
    </owl:Class>''',
'''    <owl:Class rdf:about="#LadderGuidedElevator">
        <rdfs:subClassOf rdf:resource="#Elevator"/>
        <rdfs:label xml:lang="zh">爬梯导向升降机</rdfs:label>
        <rdfs:comment xml:lang="zh">爬梯导向升降机，以塔筒内壁爬梯为导向</rdfs:comment>
        <!-- 爬梯导向升降机不需要扶持（v13.0 显式声明） -->
        <rdfs:subClassOf>
            <owl:Restriction>
                <owl:onProperty rdf:resource="#hasSupport"/>
                <owl:maxQualifiedCardinality rdf:datatype="http://www.w3.org/2001/XMLSchema#nonNegativeInteger">0</owl:maxQualifiedCardinality>
                <owl:onClass rdf:resource="#Support"/>
            </owl:Restriction>
        </rdfs:subClassOf>
    </owl:Class>''',
"LadderGuidedElevator noSupport")

# ============================================================
# 17. 类：:环焊缝 连接 2 个筒节
# ============================================================
rep(
'''    <owl:Class rdf:about="#WeldSeam">
        <rdfs:label xml:lang="zh">环焊缝</rdfs:label>
        <rdfs:comment xml:lang="zh">塔筒环焊缝，连接相邻筒节（v11.0 补充疲劳等级）</rdfs:comment>
    </owl:Class>''',
'''    <owl:Class rdf:about="#WeldSeam">
        <rdfs:label xml:lang="zh">环焊缝</rdfs:label>
        <rdfs:comment xml:lang="zh">塔筒环焊缝，连接相邻筒节（v11.0 补充疲劳等级）</rdfs:comment>
        <!-- 完整性校验：环焊缝连接 2 个筒节（v13.0） -->
        <rdfs:subClassOf>
            <owl:Restriction>
                <owl:onProperty rdf:resource="#connectsTubeSection"/>
                <owl:minQualifiedCardinality rdf:datatype="http://www.w3.org/2001/XMLSchema#nonNegativeInteger">2</owl:minQualifiedCardinality>
                <owl:onClass rdf:resource="#TubeSection"/>
            </owl:Restriction>
        </rdfs:subClassOf>
    </owl:Class>''',
"WeldSeam minConnects")

# ============================================================
# 18. 约束：平台到筒顶距离 = 1250 用 hasValue
# ============================================================
rep(
'''        <!-- 平台到筒顶距离 = 1250mm -->
        <rdfs:subClassOf>
            <owl:Restriction>
                <owl:onProperty rdf:resource="#platformToTopDistance"/>
                <owl:allValuesFrom>
                    <rdfs:Datatype>
                        <owl:onDatatype rdf:resource="http://www.w3.org/2001/XMLSchema#integer"/>
                        <owl:withRestrictions rdf:parseType="Collection">
                            <rdf:Description>
                                <xsd:minInclusive rdf:datatype="http://www.w3.org/2001/XMLSchema#integer">1250</xsd:minInclusive>
                            </rdf:Description>
                            <rdf:Description>
                                <xsd:maxInclusive rdf:datatype="http://www.w3.org/2001/XMLSchema#integer">1250</xsd:maxInclusive>
                            </rdf:Description>
                        </owl:withRestrictions>
                    </rdfs:Datatype>
                </owl:allValuesFrom>
            </owl:Restriction>
        </rdfs:subClassOf>''',
'''        <!-- 平台到筒顶距离 = 1250mm（v13.0 改用 owl:hasValue） -->
        <rdfs:subClassOf>
            <owl:Restriction>
                <owl:onProperty rdf:resource="#platformToTopDistance"/>
                <owl:hasValue rdf:datatype="http://www.w3.org/2001/XMLSchema#integer">1250</owl:hasValue>
            </owl:Restriction>
        </rdfs:subClassOf>''',
"platformToTopDistance hasValue")

# ============================================================
# 19. 约束：螺柱间距 = 500 用 hasValue
# ============================================================
rep(
'''        <!-- 螺柱间距 = 500mm -->
        <rdfs:subClassOf>
            <owl:Restriction>
                <owl:onProperty rdf:resource="#studSpacing"/>
                <owl:allValuesFrom>
                    <rdfs:Datatype>
                        <owl:onDatatype rdf:resource="http://www.w3.org/2001/XMLSchema#integer"/>
                        <owl:withRestrictions rdf:parseType="Collection">
                            <rdf:Description>
                                <xsd:minInclusive rdf:datatype="http://www.w3.org/2001/XMLSchema#integer">500</xsd:minInclusive>
                            </rdf:Description>
                            <rdf:Description>
                                <xsd:maxInclusive rdf:datatype="http://www.w3.org/2001/XMLSchema#integer">500</xsd:maxInclusive>
                            </rdf:Description>
                        </owl:withRestrictions>
                    </rdfs:Datatype>
                </owl:allValuesFrom>
            </owl:Restriction>
        </rdfs:subClassOf>''',
'''        <!-- 螺柱间距 = 500mm（v13.0 改用 owl:hasValue） -->
        <rdfs:subClassOf>
            <owl:Restriction>
                <owl:onProperty rdf:resource="#studSpacing"/>
                <owl:hasValue rdf:datatype="http://www.w3.org/2001/XMLSchema#integer">500</owl:hasValue>
            </owl:Restriction>
        </rdfs:subClassOf>''',
"studSpacing hasValue")

# ============================================================
# 20. 不相交类重构
# ============================================================
rep(
'''    <owl:AllDisjointClasses>
        <rdfs:comment xml:lang="zh">顶层物理对象类互不相交</rdfs:comment>
        <owl:members rdf:parseType="Collection">
            <owl:Class rdf:about="#WindTurbine"/>
            <owl:Class rdf:about="#WindTurbineHead"/>
            <owl:Class rdf:about="#YawBearing"/>
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
            <owl:Class rdf:about="#Model"/>
            <owl:Class rdf:about="#Region"/>
            <owl:Class rdf:about="#Flange"/>
            <owl:Class rdf:about="#SafetyAnchor"/>
            <owl:Class rdf:about="#Material"/>
            <owl:Class rdf:about="#CableBracketSpec"/>
            <owl:Class rdf:about="#AccessoryConnectionType"/>
            <owl:Class rdf:about="#LightType"/>
            <owl:Class rdf:about="#FittingType"/>
        </owl:members>
    </owl:AllDisjointClasses>''',
'''    <owl:AllDisjointClasses>
        <rdfs:comment xml:lang="zh">顶层物理对象类互不相交（v13.0：移除 :安全锚点（已归入 :附件）、:材料（改为分类值），新增 :电缆）</rdfs:comment>
        <owl:members rdf:parseType="Collection">
            <owl:Class rdf:about="#WindTurbine"/>
            <owl:Class rdf:about="#WindTurbineHead"/>
            <owl:Class rdf:about="#YawBearing"/>
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
    </owl:AllDisjointClasses>

    <owl:AllDisjointClasses>
        <rdfs:comment xml:lang="zh">分类值类互不相交（v13.0 新增分组，与物理对象类分开声明）</rdfs:comment>
        <owl:members rdf:parseType="Collection">
            <owl:Class rdf:about="#Model"/>
            <owl:Class rdf:about="#Region"/>
            <owl:Class rdf:about="#Material"/>
            <owl:Class rdf:about="#CableBracketSpec"/>
            <owl:Class rdf:about="#AccessoryConnectionType"/>
            <owl:Class rdf:about="#LightType"/>
            <owl:Class rdf:about="#FittingType"/>
        </owl:members>
    </owl:AllDisjointClasses>''',
"disjoint physical + classification")

rep(
'''    <owl:AllDisjointClasses>
        <rdfs:comment xml:lang="zh">附件子类不相交</rdfs:comment>
        <owl:members rdf:parseType="Collection">
            <owl:Class rdf:about="#LadderSupport"/>
            <owl:Class rdf:about="#CableBracket"/>
            <owl:Class rdf:about="#CableTray"/>
            <owl:Class rdf:about="#Stud"/>
            <owl:Class rdf:about="#Support"/>
            <owl:Class rdf:about="#CableClamp"/>
        </owl:members>
    </owl:AllDisjointClasses>''',
'''    <owl:AllDisjointClasses>
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
    </owl:AllDisjointClasses>''',
"accessory disjoint + SafetyAnchor")

# ============================================================
# 21. 头部注释 + 版本号
# ============================================================
s = re.sub(r'<rdfs:comment xml:lang="zh">基于塔架中段设计流程构建。.*?</rdfs:comment>',
'''<rdfs:comment xml:lang="zh">基于塔架中段设计流程构建。设计原则：真实存在的物理对象定义为类，其余定义为属性；属性承载实例值，约束用 owl:Restriction 表达。设计步骤放到 BPMN 中。面向 Openllet 推理。v11.0 依老工具（towerdesign）补充法兰、电缆线夹、安全锚点、材料、电缆托架规格五类。v11.1 明确 :梯子高度 归属 :爬梯、:平台到筒顶距离 归属 :平台。v11.2 确认塔架顶法兰位于 :塔架顶段。v12.0 依《定制化设计》流程修正：:附件中心间距 收窄至 1680~1960；新增 :附件连接方式、:灯类型 等。v13.0 依专家审视报告修正：合并 8 个同义「固定在塔筒」属性为 1 个；删除 :同侧爬梯支撑、:扶持到焊缝距离 等冗余；:固定在塔架 值域改为 :法兰 并新增 :有顶法兰；:安全锚点 归入 :附件；:材料 改为分类值；:有机型/:有区域 上提至 :风机（项目级）；新增 :电缆 类及法兰/线槽/平台/锚点/升降机/相邻段等关系；增强基数约束。**逆属性约定**：仅组成关系（:有X）建逆属性，固定/安装/连接/位置/映射类关系不建逆属性（对称属性 :相邻于 自带逆关系）。</rdfs:comment>''',
s, count=1, flags=re.S)

rep('<owl:versionInfo>v12.0</owl:versionInfo>',
    '<owl:versionInfo>v13.0</owl:versionInfo>', "versionInfo")

# ============================================================
# 22. 全局：搭建顶段 -> 塔架顶段
# ============================================================
n = s.count("搭建顶段")
s = s.replace("搭建顶段", "塔架顶段")
print("搭建顶段 -> 塔架顶段 :", n, "处")

open(P, 'w', encoding='utf-8').write(s)
print("OK  len %d -> %d" % (orig_len, len(s)))
