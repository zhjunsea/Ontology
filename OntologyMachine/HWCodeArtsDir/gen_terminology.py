import xml.etree.ElementTree as ET
import os
import sys
import re

NS = "http://example.org/industryinsight#"
TERM = "http://example.org/industryinsight/term#"
RDF = "http://www.w3.org/1999/02/22-rdf-syntax-ns#"
XML = "http://www.w3.org/XML/1998/namespace"

src = sys.argv[1]
out = sys.argv[2]

tree = ET.parse(src)
root = tree.getroot()

WANT = {"Class", "ObjectProperty", "DatatypeProperty"}
ents = {}
for el in root.iter():
    tag = el.tag
    if not isinstance(tag, str):
        continue
    ln = tag.split('}')[-1]
    if ln not in WANT:
        continue
    about = el.get("{" + RDF + "}about")
    if not about:
        continue
    if about.startswith("http"):
        iri = about
    elif about.startswith("#"):
        iri = NS + about[1:]
    else:
        iri = NS + about
    label = None
    for ch in el:
        if not isinstance(ch.tag, str):
            continue
        if ch.tag.split('}')[-1] == "label":
            if ch.get("{" + XML + "}lang") == "zh" and ch.text and ch.text.strip():
                label = ch.text.strip()
    local = iri[len(NS):] if iri.startswith(NS) else iri
    prev = ents.get(iri)
    if prev is None or (label and not prev[1]):
        ents[iri] = (local, label)

items = sorted(ents.values(), key=lambda x: x[0])
missing = [(l, iri) for iri, (l, lab) in ents.items() if not lab]
print("entities(Class/ObjectProperty/DatatypeProperty) =", len(items))
print("missing zh label =", len(missing))
for l, iri in sorted(missing):
    print("   MISSING_ZH_LABEL:", l, iri)

# 同义词/别名（仅收录 TBox 概念的惯用异名；来源=流程设计文档中的环节名，待闸门⑥确认/补充）
# 注：Tool_* 分析工具为本体 ABox 个体（owl:NamedIndividual），非 TBox 概念，本词汇表不收录。
ALIASES = {
    "IndustryInsightConcept": ["本体根类"],
    "InsightScenario": ["洞察场景", "分析场景"],
    "InitialHypothesisTree": ["初始假设树", "假设树"],
    "StrategicControlPoint": ["战略控制点", "控制点"],
    "InsightReport": ["洞察报告", "行业洞察报告"],
    "FiveLooksInsight": ["五看"],
    "IndustryTrendInsight": ["看行业"],
    "MarketCustomerInsight": ["看市场"],
    "CompetitionInsight": ["看竞争"],
    "SelfInsight": ["看自己"],
    "Opportunity": ["机会点"],
    "KeyInsightConversionTable": ["关键洞察转换表"],
    "OnePageStrategy": ["一页纸战略"],
}


# 结构逆属性（owl:inverseOf 自动生成、本体未给 label）：标签由正向属性语义推导，非编造
INVERSE_LABELS = {
    "consensusOf": "共识点所属",
    "controlPointOf": "战略控制点所属",
    "derivedInsight": "衍生的洞察",
    "divergenceOf": "分歧点所属",
    "evidenceFor": "作为证据",
    "findingOf": "工具结论所属",
    "hypothesisOf": "假设所属",
    "includedInReport": "被汇总包含",
    "objectiveOf": "目标所属",
    "opportunityOf": "机会点所属",
    "strategyOf": "策略所属",
    "summaryOf": "总结分析所属",
}


def lang_of(s):
    return "zh" if re.search(r"[\u4e00-\u9fff]", s) else "en"


lines = []
lines.append("@prefix skos: <http://www.w3.org/2004/02/skos/core#> .")
lines.append("@prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .")
lines.append("")
lines.append("<http://example.org/industryinsight/term> a skos:ConceptScheme ;")
lines.append('    skos:prefLabel "行业洞察（五看三定）业务术语表"@zh ;')
lines.append('    rdfs:comment "规范概念来源于本体（每个类/对象属性/数据属性对应一个概念）；同义表述（别名/缩写/惯用异名）以 skos:altLabel 归并，用于消解「不同名同义、同义不同名」。第 13 步产出，闸门⑥确认。"@zh .')
lines.append("")
for local, label in items:
    lbl = label or INVERSE_LABELS.get(local) or local
    aliases = []
    if re.fullmatch(r"[A-Za-z0-9_\-]+", local) and local != lbl:
        aliases.append(local)
    for a in ALIASES.get(local, []):
        if a != lbl and a not in aliases:
            aliases.append(a)
    b = [f"<{TERM}{local}> a skos:Concept ;",
         f'    skos:prefLabel "{lbl}"@zh ;']
    for a in aliases:
        b.append(f'    skos:altLabel "{a}"@{lang_of(a)} ;')
    b.append(f"    skos:exactMatch <{NS}{local}> .")
    lines.append("\n".join(b))
    lines.append("")

os.makedirs(os.path.dirname(out), exist_ok=True)
with open(out, "w", encoding="utf-8") as f:
    f.write("\n".join(lines))
print("written:", out, "concepts:", len(items))
