# -*- coding: utf-8 -*-
"""生成 ziweidoushu-terminology.ttl（SKOS 词汇表 · 第13步）
规范概念来源：12 子本体的 owl:Class / owl:ObjectProperty / owl:DatatypeProperty 的 rdfs:label；
星曜术语（个体，含别名）来源：ziweidoushu-abox.owl；
同义词/别名逐条标注《紫微斗數全書》篇章或本应用设计文档出处。
"""
import glob, os, re
from rdflib import Graph, RDF, RDFS, OWL

BASE = r"D:/work/Ontology/OntologyMachine/OntologyFrameworkZiWeiDouShu/ontology"
DOMAINS = ["core","calendar","chart","star","layout","state","palace",
           "pattern","fortune","reading","diagnosis","rules"]
NS = "http://example.org/ziweidoushu/%s#"

def zh_label(g, s):
    for l in g.objects(s, RDFS.label):
        if l.language == "zh":
            return str(l)
    for l in g.objects(s, RDFS.label):
        return str(l)
    return None

def split_iri(iri):
    iri = str(iri)
    for d in DOMAINS:
        p = NS % d
        if iri.startswith(p):
            return d, iri[len(p):]
    return None, None

def humanize(name, title=False):
    # camelCase -> words; underscore -> space
    parts = re.split(r'_', name)
    words = []
    for part in parts:
        sub = re.findall(r'[A-Z]+(?=[A-Z][a-z])|[A-Z]?[a-z]+|[A-Z]+|\d+', part)
        words.extend(sub if sub else [part])
    txt = " ".join(words)
    if title:
        return txt
    return txt.lower() if name[:1].islower() or name.startswith(("has","is")) else txt

# ---- 首选标签消歧（本体中不同实体同 zh 名，SKOS 内需一名一概念）----
DISAMBIG = {
    "chart:hasBureau": "有宫之五行局",
    "calendar:hasLeapMonth": "有闰月归属",
    "chart:hasPalaceStem": "有宫干（五鼠遁）",
    "layout:placementPalace": "落宫（宫位名）",
    "star:Deity_JiangJun": "将军（流年十二神）",
    "star:Deity_SanSha_JiangJun": "将军（飞天三杀）",
    "star:Deity_ZouShu": "奏书（流年十二神）",
    "star:Deity_SanSha_ZouShu": "奏书（飞天三杀）",
    "star:Deity_TaiSuiGuanFu": "官府（流年十二神）",
    "star:Deity_GuanFu": "官府（四飞星）",
}

# ---- 逐条同义词/别名（含出处）----
ALIAS = {
    "star:ZiWei": [("帝座","《紫微斗數全書》卷一·諸星問答論")],
    "star:TanLang": [("桃花","《紫微斗數全書》卷一·諸星問答論")],
    "star:JuMen": [("暗","《紫微斗數全書》卷一·諸星問答論")],
    "star:TianXiang": [("印","《紫微斗數全書》卷一·諸星問答論")],
    "star:TianLiang": [("荫","《紫微斗數全書》卷一·諸星問答論")],
    "star:QingYang": [("刑","《紫微斗數全書》卷一·諸星問答論")],
    "star:TuoLuo": [("忌","《紫微斗數全書》卷一·諸星問答論")],
    "star:PoJun": [("耗","《紫微斗數全書》卷一·諸星問答論")],
    "chart:Palace_JiaoYou": [("仆役宫","《紫微斗數全書》卷二·安十二宫"),("奴仆宫","《紫微斗數全書》卷二·安十二宫")],
    "star:FourteenMainStar": [("十四正曜","《紫微斗數全書》卷一·諸星問答論")],
    "star:Star": [("星辰","《紫微斗數全書》卷一·諸星問答論")],
    "core:SexagenaryCycle": [("六十花甲子","《紫微斗數全書》卷二·六十花甲子納音歌"),("花甲子","《紫微斗數全書》卷二·六十花甲子納音歌")],
    "core:NaYin": [("纳音五行","《紫微斗數全書》卷二·六十花甲子納音歌")],
    "core:FivePhaseBureau": [("五行局","《紫微斗數全書》卷二·安五行局诀")],
    "state:Brightness": [("庙旺利陷","《紫微斗數全書》卷二·星辰庙陷表")],
    "palace:SanFangSiZheng": [("三合四正","本应用设计文档·宫位体系域（S4）")],
    "pattern:StarPattern": [("諸格","《紫微斗數全書》卷一·諸格論")],
    "rules:AlertFlag": [("告警标志","本应用设计文档·告警标志域（S5）")],
}

# ---- 收集概念 ----
files = sorted(glob.glob(os.path.join(BASE, "ziweidoushu-*.owl")))
files = [f for f in files if not f.endswith(("ziweidoushu-all.owl","ziweidoushu-tbox.owl","ziweidoushu-abox.owl"))]

concepts = {}   # key "dom:Name" -> dict(local, dom, kind, zh, parent, broader_key)
for f in files:
    g = Graph(); g.parse(f, format="xml")
    for kind, tag in ((OWL.Class,"Class"),(OWL.ObjectProperty,"ObjectProperty"),(OWL.DatatypeProperty,"DatatypeProperty")):
        for s in g.subjects(RDF.type, kind):
            d, name = split_iri(s)
            if d is None: continue
            zh = zh_label(g, s)
            if zh is None: continue
            parent = None
            if tag == "Class":
                for p in g.objects(s, RDFS.subClassOf):
                    pd, pn = split_iri(p)
                    if pd: parent = "%s:%s" % (pd, pn); break
            else:
                for p in g.objects(s, RDFS.domain):
                    pd, pn = split_iri(p)
                    if pd: parent = "%s:%s" % (pd, pn); break
            key = "%s:%s" % (d, name)
            concepts[key] = dict(local=name, dom=d, kind=tag, zh=zh, parent=parent)

# ---- 星曜个体（含别名）----
gab = Graph(); gab.parse(os.path.join(BASE, "ziweidoushu-abox.owl"), format="xml")
star_ind = {}
SSTAR = NS % "star"
for s in gab.subjects(RDF.type, None):
    d, name = split_iri(s)
    if d != "star": continue
    # only individuals typed as star classes (or having starName/deityName)
    names = list(gab.objects(s, None))
    star_name = None
    for p in gab.predicates(s, None):
        pl = str(p)
        if pl.endswith("starName") or pl.endswith("deityName"):
            v = list(gab.objects(s, p))
            if v: star_name = str(v[0])
    if star_name is None: continue
    if name in concepts: continue
    # parent class concept
    parent = None
    for t in gab.objects(s, RDF.type):
        pd, pn = split_iri(t)
        if pd: parent = "%s:%s" % (pd, pn); break
    alias = []
    for v in gab.objects(s, None):
        pass
    for p in gab.predicates(s, None):
        if str(p).endswith("starAlias"):
            for v in gab.objects(s, p):
                if str(v).strip(): alias.append(str(v))
    star_ind["star:"+name] = dict(local=name, dom="star", kind="Individual",
                                  zh=star_name, parent=parent, alias=alias)

# ---- 概念 IRI 去重 ----
all_keys = list(concepts.keys()) + list(star_ind.keys())
local_count = {}
for k in all_keys:
    loc = concepts.get(k, star_ind.get(k))["local"]
    local_count[loc] = local_count.get(loc, 0) + 1
def term_iri(key):
    rec = concepts.get(key) or star_ind.get(key)
    loc = rec["local"]
    if local_count[loc] > 1:
        return "%s_%s" % (rec["dom"], loc)
    return loc

# ---- 输出 ----
out = []
out.append("@prefix skos: <http://www.w3.org/2004/02/skos/core#> .")
out.append("@prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .")
out.append("@prefix dct:  <http://purl.org/dc/terms/> .")
out.append("@prefix owl:  <http://www.w3.org/2002/07/owl#> .")
out.append("@prefix term: <http://example.org/ziweidoushu/term/> .")
for d in DOMAINS:
    out.append("@prefix %-10s <http://example.org/ziweidoushu/%s#> ." % (d+":", d))
out.append("")
out.append("<http://example.org/ziweidoushu/term>")
out.append("    a owl:Ontology ;")
out.append("    a skos:ConceptScheme ;")
out.append("    skos:prefLabel \"紫微斗数业务术语体系\"@zh ;")
out.append("    skos:prefLabel \"ZiWeiDouShu (Purple Star Astrology) Business Terminology\"@en ;")
out.append("    dct:created \"2026-10-31\"^^<http://www.w3.org/2001/XMLSchema#date> ;")
out.append("    dct:description \"紫微斗数领域核心业务术语的标准化词汇表。规范概念（skos:Concept）全部来源于本体的 owl:Class / owl:ObjectProperty / owl:DatatypeProperty 的 rdfs:label；星曜术语来源于 ABox 星曜个体（star:*）及其 star:starAlias。同义词/别名逐条标注《紫微斗數全書》篇章或本应用设计文档出处，无出处者标【待考】。用于统一 OWL 本体及其他系统中的词义，实现同名同义、同义同名，供 OntologyLabelMatcher / SkosSynonymReader 做名称归一（对齐顶层设计 S4、Example §8）。\"@zh .")
out.append("")

def emit(key, rec):
    iri = term_iri(key)
    zh = DISAMBIG.get(key, rec["zh"])
    L = []
    L.append("term:%s" % iri)
    L.append("    a skos:Concept ;")
    L.append("    skos:prefLabel \"%s\"@zh ;" % zh)
    en = humanize(rec["local"], title=(rec["kind"] == "Class"))
    if iri != rec["local"]:  # 同名冲突项：英文标签补域后缀以保唯一
        en = "%s (%s)" % (en, rec["dom"])
    L.append("    skos:prefLabel \"%s\"@en ;" % en)
    # altLabel（去重：有出处的优先，裸别名补注来源）
    sourced = ALIAS.get(key, [])
    sourced_txt = {a for a, _ in sourced}
    seen_txt = set(sourced_txt)
    for a in rec.get("alias", []):
        if a in seen_txt:
            continue
        seen_txt.add(a)
        L.append("    skos:altLabel \"%s\"@zh ;   # 源: star:starAlias（《紫微斗數全書》卷一·諸星問答論·化气异名）" % a)
    for a, src in sourced:
        L.append("    skos:altLabel \"%s\"@zh ;   # 源: %s" % (a, src))
    # broader
    if rec.get("parent") and rec["parent"] in concepts:
        L.append("    skos:broader term:%s ;" % term_iri(rec["parent"]))
    # exactMatch
    pref = key.split(":")[0]
    L.append("    skos:exactMatch %s:%s ." % (pref, rec["local"]))
    return "\n".join(L)

# 1. 类
cls = [(k,v) for k,v in concepts.items() if v["kind"]=="Class"]
out.append("# ========================")
out.append("# 类（业务概念）（%d）" % len(cls))
out.append("# ========================")
for d in DOMAINS:
    items = sorted([k for k,v in cls if v["dom"]==d], key=lambda x: concepts[x]["local"])
    if not items: continue
    out.append("")
    out.append("# ---- %s ----" % d)
    for k in items:
        out.append(emit(k, concepts[k]))
        out.append("")

# 2. 对象属性
obj = [(k,v) for k,v in concepts.items() if v["kind"]=="ObjectProperty"]
out.append("# ========================")
out.append("# 对象属性（业务关系）（%d）" % len(obj))
out.append("# ========================")
for d in DOMAINS:
    items = sorted([k for k,v in obj if v["dom"]==d], key=lambda x: concepts[x]["local"])
    if not items: continue
    out.append("")
    out.append("# ---- %s ----" % d)
    for k in items:
        out.append(emit(k, concepts[k]))
        out.append("")

# 3. 数据属性
dat = [(k,v) for k,v in concepts.items() if v["kind"]=="DatatypeProperty"]
out.append("# ========================")
out.append("# 数据属性（业务字段）（%d）" % len(dat))
out.append("# ========================")
for d in DOMAINS:
    items = sorted([k for k,v in dat if v["dom"]==d], key=lambda x: concepts[x]["local"])
    if not items: continue
    out.append("")
    out.append("# ---- %s ----" % d)
    for k in items:
        out.append(emit(k, concepts[k]))
        out.append("")

# 4. 星曜术语（个体，含别名）
out.append("# ========================")
out.append("# 星曜术语（个体，含别名）（%d）" % len(star_ind))
out.append("# ========================")
for k in sorted(star_ind.keys(), key=lambda x: (star_ind[x]["parent"] or "", star_ind[x]["local"])):
    out.append(emit(k, star_ind[k]))
    out.append("")

txt = "\n".join(out).rstrip() + "\n"
with open(os.path.join(BASE, "ziweidoushu-terminology.ttl"), "w", encoding="utf-8") as fh:
    fh.write(txt)
# ---- 输出 owlIRI → termIRI 映射（供 OWL 侧注入 skos:exactMatch）----
TERM = "http://example.org/ziweidoushu/term/"
with open(os.path.join(r"D:/work/Ontology/OntologyMachine/HWCodeArtsDir", "_zwds_term_map.tsv"), "w", encoding="utf-8") as fh:
    for key, rec in concepts.items():
        owl_iri = (NS % rec["dom"]) + rec["local"]
        fh.write("%s\t%s%s\n" % (owl_iri, TERM, term_iri(key)))
    for key, rec in star_ind.items():
        owl_iri = (NS % "star") + rec["local"]
        fh.write("%s\t%s%s\n" % (owl_iri, TERM, term_iri(key)))
with open(os.path.join(r"D:/work/Ontology/OntologyMachine/HWCodeArtsDir", "_zwds_term_map_ind.tsv"), "w", encoding="utf-8") as fh:
    for key, rec in star_ind.items():
        owl_iri = (NS % "star") + rec["local"]
        fh.write("%s\t%s%s\n" % (owl_iri, TERM, term_iri(key)))

print("classes=%d objprops=%d dataprops=%d starIndividuals=%d total=%d" % (
    len(cls), len(obj), len(dat), len(star_ind), len(all_keys)))
print("collisions:", {k:v for k,v in local_count.items() if v>1})
