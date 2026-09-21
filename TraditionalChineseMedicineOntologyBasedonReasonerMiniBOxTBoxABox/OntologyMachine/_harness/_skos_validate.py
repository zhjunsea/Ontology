# -*- coding: utf-8 -*-
"""校验重新生成的 SKOS：TTL 可解析、概念数、表面形式唯一性。"""
import io, re, sys

ROOT = r"D:\work\Ontology\TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox"
SKOS = ROOT + r"\ontology\tcm-zhengzhuang_skos.ttl"
OUT = ROOT + r"\OntologyMachine\_harness\_skos_validate.txt"

L = []
t = io.open(SKOS, encoding="utf-8").read()
L.append("文件大小: %d bytes, 行数 %d" % (len(t.encode("utf-8")), t.count("\n")))

# 1) rdflib 解析
try:
    import rdflib
    g = rdflib.Graph()
    g.parse(SKOS, format="turtle")
    L.append("rdflib 解析: OK, 三元组 %d" % len(g))
    SKOS_C = rdflib.URIRef("http://www.w3.org/2004/02/skos/core#Concept")
    concepts = set(g.subjects(rdflib.RDF.type, SKOS_C))
    L.append("skos:Concept 数: %d" % len(concepts))
except Exception as e:
    L.append("rdflib 解析失败: %r" % e)

# 2) 表面形式唯一性（pref/alt/hid 全局）
blocks = re.findall(r"^zzskos:(\w+) a skos:Concept ;(.*?)(?=^zzskos:\w+ a skos:Concept ;|\Z)", t, re.S | re.M)
L.append("概念块数: %d" % len(blocks))
pref = {}
surf = {}
for name, b in blocks:
    m = re.search(r'skos:prefLabel\s+"([^"]+)"@zh', b)
    if not m:
        continue
    p = m.group(1)
    pref.setdefault(p, []).append(name)
    surf.setdefault(p, []).append(("pref", name))
    for prop in ("altLabel", "hiddenLabel"):
        mm = re.search(r"skos:%s((?:\s*\"[^\"]+\"@zh\s*,?)+)\s*;" % prop, b)
        if not mm:
            continue
        for lab in re.findall(r'"([^"]+)"@zh', mm.group(1)):
            surf.setdefault(lab, []).append((prop, name))

dupp = {k: v for k, v in pref.items() if len(v) > 1}
L.append("重复 prefLabel: %d 组" % len(dupp))
for k, v in dupp.items():
    L.append("   %s -> %s" % (k, v))

# 跨概念重复（同一表面形式出现在多个概念）
multi = {k: v for k, v in surf.items() if len(set(n for _, n in v)) > 1}
L.append("跨概念重复表面形式: %d" % len(multi))
for k, v in list(multi.items())[:20]:
    L.append("   %s -> %s" % (k, v))

# 3) 关键映射抽查
want = {
    "两边肋骨下面胀痛": "胸胁苦满", "拉不消化的东西": "下利清谷", "想拉又拉不出": "下重",
    "想吐": "欲呕", "低烧": "微热", "高烧": "大热", "小肚子疼": "少腹痛",
    "手脚发凉": "手足冷", "喘不上气": "短气", "打嗝": "哕", "说胡话": "谵语",
}
L.append("")
L.append("关键映射抽查:")
for k, exp in want.items():
    got = None
    for lab, v in surf.items():
        if lab == k:
            got = v[0][1]
            break
    # 概念名 → prefLabel
    gotlab = None
    for name, b in blocks:
        if name == got:
            m = re.search(r'skos:prefLabel\s+"([^"]+)"@zh', b)
            gotlab = m.group(1) if m else None
    ok = "OK " if gotlab == exp else "!! "
    L.append("   %s%-14s -> %s (期望 %s)" % (ok, k, gotlab, exp))

io.open(OUT, "w", encoding="utf-8").write("\n".join(L) + "\n")
print("done")
