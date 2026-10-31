# -*- coding: utf-8 -*-
"""第6步：为 sizhubazi-all.owl 生成 SWRL 规则块（R1/R5/R6/R19/R21/R22）并替换 <!-- P6 --> 占位符。
其余 R2-R4、R7-R18、R20、R23、R24 依《本体核心设计》§5 标注「由程序实现」或查表/待考，以注释留痕。"""
import io, sys

PATH = r"D:\work\Ontology\OntologyMachine\OntologyFrameworkSiZhuBaZi\ontology\sizhubazi-all.owl"
SWRL = "http://www.w3.org/2003/11/swrl#"
RDFNS = "http://www.w3.org/1999/02/22-rdf-syntax-ns#"
XSD = "http://www.w3.org/2001/XMLSchema#"
NIL = RDFNS + "nil"

def desc(inner):
    return "<rdf:Description>" + inner + "</rdf:Description>"

def class_atom(cls, v):
    return desc('<rdf:type rdf:resource="%sClassAtom"/><swrl:classPredicate rdf:resource="%s"/><swrl:argument1 rdf:resource="%s"/>' % (SWRL, cls, v))

def iprop_atom(p, a1, a2):
    return desc('<rdf:type rdf:resource="%sIndividualPropertyAtom"/><swrl:propertyPredicate rdf:resource="%s"/><swrl:argument1 rdf:resource="%s"/><swrl:argument2 rdf:resource="%s"/>' % (SWRL, p, a1, a2))

def dprop_atom_res(p, a1, a2res):
    return desc('<rdf:type rdf:resource="%sDatavaluedPropertyAtom"/><swrl:propertyPredicate rdf:resource="%s"/><swrl:argument1 rdf:resource="%s"/><swrl:argument2 rdf:resource="%s"/>' % (SWRL, p, a1, a2res))

def dprop_atom_lit(p, a1, value, lang=None, dt=None):
    if lang:
        lit = '<swrl:argument2 xml:lang="%s">%s</swrl:argument2>' % (lang, value)
    else:
        lit = '<swrl:argument2 rdf:datatype="%s">%s</swrl:argument2>' % (dt, value)
    return desc('<rdf:type rdf:resource="%sDatavaluedPropertyAtom"/><swrl:propertyPredicate rdf:resource="%s"/><swrl:argument1 rdf:resource="%s"/>%s' % (SWRL, p, a1, lit))

def atom_list(atoms):
    """atoms: list of xml strings -> AtomList chain (innermost rest -> rdf:nil)"""
    node = None
    for a in reversed(atoms):
        if node is None:
            node = ('<rdf:Description><rdf:type rdf:resource="%sAtomList"/>'
                    '<rdf:first>%s</rdf:first>'
                    '<rdf:rest rdf:resource="%s"/></rdf:Description>') % (SWRL, a, NIL)
        else:
            node = ('<rdf:Description><rdf:type rdf:resource="%sAtomList"/>'
                    '<rdf:first>%s</rdf:first>'
                    '<rdf:rest>%s</rdf:rest></rdf:Description>') % (SWRL, a, node)
    return node

def imp(body_atoms, head_atoms, label):
    b = atom_list(body_atoms)
    h = atom_list(head_atoms)
    # atom_list produced nested <rdf:rest>...</rdf:rest>; last rest is <rdf:rest rdf:resource="nil"/>
    return ('    <!-- %s -->\n'
            '    <rdf:Description>\n'
            '        <rdf:type rdf:resource="%sImp"/>\n'
            '        <swrl:body>%s</swrl:body>\n'
            '        <swrl:head>%s</swrl:head>\n'
            '    </rdf:Description>\n') % (label, SWRL, b, h)

def var(v):
    return '    <rdf:Description rdf:about="%s"><rdf:type rdf:resource="%sVariable"/></rdf:Description>\n' % (v, SWRL)

# ---- variables ----
vars_xml = ""
for v in ["rules/r1_s","rules/r1_p",
          "rules/r5_f","rules/r5_i","rules/r5_gf",
          "rules/r6_f","rules/r6_i","rules/r6_gf",
          "rules/r19_f","rules/r21_f","rules/r21_d","rules/r22_f","rules/r22_a"]:
    vars_xml += var(v)

BOOL = XSD + "boolean"

rules_xml = ""
# R1 干支→五行传导
rules_xml += imp(
    [class_atom("core/HeavenlyStem","rules/r1_s"),
     iprop_atom("core/stemFivePhase","rules/r1_s","rules/r1_p")],
    [iprop_atom("core/hasFivePhase","rules/r1_s","rules/r1_p")],
    "R1 干支→五行传导：HeavenlyStem(?s) ∧ stemFivePhase(?s,?p) → hasFivePhase(?s,?p)（三命通会卷一·论五行生成）")
# R5 起运顺逆（阳男顺行）
rules_xml += imp(
    [class_atom("chart/FourPillars","rules/r5_f"),
     iprop_atom("chart/hasBirthInput","rules/r5_f","rules/r5_i"),
     dprop_atom_lit("chart/gender","rules/r5_i","男",lang="zh"),
     dprop_atom_lit("chart/yearStemYinYang","rules/r5_f","阳",lang="zh"),
     iprop_atom("chart/hasGreatFortune","rules/r5_f","rules/r5_gf")],
    [dprop_atom_lit("chart/direction","rules/r5_gf","顺",lang="zh")],
    "R5 起运顺逆（阳男顺行）（渊海子平卷之一·论起大运法）")
# R6 起运顺逆（阴男逆行）
rules_xml += imp(
    [class_atom("chart/FourPillars","rules/r6_f"),
     iprop_atom("chart/hasBirthInput","rules/r6_f","rules/r6_i"),
     dprop_atom_lit("chart/gender","rules/r6_i","男",lang="zh"),
     dprop_atom_lit("chart/yearStemYinYang","rules/r6_f","阴",lang="zh"),
     iprop_atom("chart/hasGreatFortune","rules/r6_f","rules/r6_gf")],
    [dprop_atom_lit("chart/direction","rules/r6_gf","逆",lang="zh")],
    "R6 起运顺逆（阴男逆行）（渊海子平卷之一·论起大运法）")
# R19 告警·大凶（简化判据）
rules_xml += imp(
    [class_atom("chart/FourPillars","rules/r19_f"),
     dprop_atom_lit("pattern/isBroken","rules/r19_f","true",dt=BOOL),
     dprop_atom_lit("yongshen/noYongShen","rules/r19_f","true",dt=BOOL)],
    [iprop_atom("rules/hasAlertFlag","rules/r19_f","rules/AlertDaXiong")],
    "R19 告警·大凶：格局破 ∧ 无用神 → hasAlertFlag(大凶)（顶层设计 I·46；客户裁定 ㉒；综合判据由程序编排）")
# R21 告警·重大疾病
rules_xml += imp(
    [class_atom("chart/FourPillars","rules/r21_f"),
     iprop_atom("reading/hasDisease","rules/r21_f","rules/r21_d"),
     dprop_atom_lit("reading/diseaseSevere","rules/r21_d","true",dt=BOOL)],
    [iprop_atom("rules/hasAlertFlag","rules/r21_f","rules/AlertJiBing")],
    "R21 告警·重大疾病：有重疾 → hasAlertFlag(重大疾病)（三命通会卷九·论疾病先知五脏六腑所属干支；客户裁定 ㉒）")
# R22 告警·命中即外发
rules_xml += imp(
    [class_atom("chart/FourPillars","rules/r22_f"),
     iprop_atom("rules/hasAlertFlag","rules/r22_f","rules/r22_a")],
    [dprop_atom_lit("rules/alertTriggered","rules/r22_a","true",dt=BOOL)],
    "R22 告警·命中即外发：hasAlertFlag(?a) → alertTriggered(?a,true)（流程节点 A；供 SwrlRuleTriggerListener 检测 → RabbitMQ）")

comment = """
    <!-- ============================================================ -->
    <!-- SWRL 规则（§5）· 已序列化 R1/R5/R6/R19/R21/R22（良构、谓词均在 §2/§3 定义） -->
    <!-- 其余规则依《本体核心设计》§5 标注「由程序实现 / 查表 / 待考」，不在 SWRL 中序列化，留痕如下： -->
    <!--   R2/R3  五行生克传递闭包  → 由 core:generates/overcomes 的 owl:TransitiveProperty 承载（不写空头规则） -->
    <!--   R4     柱→干支赋值        → 由程序/OBDA 提供配对（内置表） -->
    <!--   R7     十神判定          → 由程序实现（10×10 查表，避免百条冗余） -->
    <!--   R8     官杀混杂          → 由程序编排（含否定/比较，非 SWRL 合适场景） -->
    <!--   R9/R12 旺衰/层次综合      → 由程序计算（多因子权重，客户裁定 ⑰ 不设数值权重） -->
    <!--   R10/R11 格局成败/用神主取 → 分类与多法冲突编排由程序；主取落 hasYongShen，余挂 SchoolVariant -->
    <!--   R13/R14 空亡/神煞起例     → 查表型，由程序/OBDA（MySQL） -->
    <!--   R15    合冲刑穿成立        → 由程序判定（含进交退伏等条件） -->
    <!--   R16/R17/R18 岁运/运化气/运限 → 细化【待考】，由程序 -->
    <!--   R20    寿元关            → 综合判据由程序编排 -->
    <!--   R23    合婚互补正理        → 由程序（《神峰通考》正理；节点 M1） -->
    <!--   R24    出处绑定          → 由程序在生成期绑定（非推理） -->
    <!-- ============================================================ -->
"""

block = "    <!-- 辅助数据属性：岁干阴阳（供 R5/R6 起运顺逆；§5 R5/R6 所引谓词） -->\n" \
        '    <owl:DatatypeProperty rdf:about="chart/yearStemYinYang"><rdfs:domain rdf:resource="chart/FourPillars"/><rdfs:range rdf:resource="http://www.w3.org/2001/XMLSchema#string"/><rdfs:label xml:lang="zh">年干阴阳（阳/阴）</rdfs:label></owl:DatatypeProperty>\n' \
        + comment + "\n" + vars_xml + "\n" + rules_xml

with io.open(PATH, "r", encoding="utf-8") as f:
    content = f.read()
assert "<!-- P6 -->" in content, "P6 placeholder not found"
content = content.replace("    <!-- P6 -->", block)
with io.open(PATH, "w", encoding="utf-8") as f:
    f.write(content)
print("SWRL block inserted. total chars:", len(content))
