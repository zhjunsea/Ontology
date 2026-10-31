# -*- coding: utf-8 -*-
"""第 10 步：将 sizhubazi-tbox.owl 按业务域拆为 12 个子本体，
sizhubazi-tbox.owl 改为聚合入口（显式 imports 12 子本体）。
按「声明主体 IRI 的命名空间前缀」归属；SWRL 规则块归 rules；并集与原 TBox 图同构。
xml:base 一律保持 http://example.org/sizhubazi/（内容用相对 IRI 如 core/hasYinYang 解析依赖 base）。
"""
import io, os, re

ONT = r"D:/work/Ontology/OntologyMachine/OntologyFrameworkSiZhuBaZi/ontology"
SRC = os.path.join(ONT, "sizhubazi-tbox.owl")
# 从第 9 步产出的（单文件）TBox 备份重建，保证可重复执行
SRC = r"D:/work/Ontology/OntologyMachine/HWCodeArtsDir/sizhubazi-tbox.owl.presplit.bak"

with io.open(SRC, encoding="utf-8") as f:
    lines = f.readlines()

# 头（XMLDECL + rdf:RDF 到最后一个 xmlns）固定复用：行 1..22
XMLDECL = lines[0]
NS_BLOCK = "".join(lines[1:22])  # 行2..22
# 内容：从 </owl:Ontology> 之后到 </rdf:RDF> 之前
onto_end = next(i for i, l in enumerate(lines) if "</owl:Ontology>" in l)
rdf_end = next(i for i, l in enumerate(lines) if "</rdf:RDF>" in l)
content_lines = lines[onto_end + 1:rdf_end]

TOPOLOGY = {
    "core": [],
    "calendar": ["core"],
    "chart": ["core", "calendar"],
    "relations": ["core"],
    "tengod": ["core", "relations"],
    "deity": ["core", "calendar", "chart"],
    "pattern": ["core", "tengod", "relations"],
    "yongshen": ["core", "pattern", "relations"],
    "fortune": ["core", "chart", "yongshen", "pattern"],
    "reading": ["core", "tengod", "deity", "yongshen", "fortune"],
    "diagnosis": [],
    "rules": ["pattern", "yongshen", "fortune", "reading"],
}
ORDER = ["core", "calendar", "chart", "relations", "tengod", "deity",
         "pattern", "yongshen", "fortune", "reading", "diagnosis", "rules"]

LABEL = {
    "core": "干支基础：阴阳 / 五行 / 天干 / 地支 / 六十甲子 / 纳音 / 藏干 / 长生",
    "calendar": "历法：节气 / 月建 / 真太阳时 / 历法边界",
    "chart": "排盘：四柱 / 大运 / 小运 / 胎元 / 命宫 / 起运",
    "relations": "干支作用：五合化气 / 六合三合 / 冲刑穿害 / 合绊 / 暗合暗拱 / 生克",
    "tengod": "十神与六亲：十神 / 生克制化 / 六亲宫位",
    "deity": "神煞：贵人吉神 / 禄马 / 凶煞 / 空亡",
    "pattern": "格局：八正格 / 杂气 / 外格杂格 / 成败高低",
    "yongshen": "用神旺衰：用神相神忌神 / 调候 / 源流通关 / 旺衰十二宫 / 清浊真假 / 层次",
    "fortune": "岁运：大运流年 / 流月日时 / 应期 / 运限 / 特殊岁运格局 / 运化气 / 综合辩证",
    "reading": "断语：性情 / 疾病 / 六亲 / 女命 / 小儿 / 分类事项 / 劝诫 / 合婚",
    "diagnosis": "出处与流派标注：SourceNote / SchoolVariant / 待考标记",
    "rules": "SWRL 规则（含告警标志推导）",
}

buffers = {k: [] for k in ORDER}
pending = []
for raw in content_lines:
    s = raw.strip()
    if s == "":
        continue
    if s.startswith("<!--"):
        pending.append(raw)
        continue
    if "<owl:Restriction>" in raw:
        # 约束归属「owl:onProperty 所属命名空间」（标准 OWL 模块化）：
        # 保证 onProperty 与文件内声明的属性一致，OWLAPI 方可识别匿名类表达式。
        mp = re.search(r'owl:onProperty rdf:resource="([a-z]+)/', raw)
        owner = mp.group(1) if (mp and mp.group(1) in TOPOLOGY) else "rules"
    else:
        m = re.search(r'rdf:about="([a-z]+)/', raw)
        owner = m.group(1) if (m and m.group(1) in TOPOLOGY) else "rules"
    if pending:
        buffers[owner].extend(pending)
        pending = []
        if not buffers[owner][-1].endswith("\n"):
            buffers[owner][-1] += "\n"
    buffers[owner].append(raw)

print("归属统计：", {k: len(buffers[k]) for k in ORDER})

def ontology_block(prefix, imports):
    imp = "".join('        <owl:imports rdf:resource="http://example.org/sizhubazi/%s"/>\n' % i
                  for i in imports)
    return ('    <owl:Ontology rdf:about="http://example.org/sizhubazi/%s">\n' % prefix
            + imp
            + '        <rdfs:label xml:lang="zh">四柱命理本体 TBox 子本体 · %s（%s）</rdfs:label>\n' % (prefix, LABEL[prefix])
            + '        <rdfs:comment xml:lang="zh">第 10 步产出：TBox 按业务域拆分。命名空间 http://example.org/sizhubazi/%s 不变；由 sizhubazi-tbox.owl 聚合入口统一 imports；依赖方向单向无环。</rdfs:comment>\n' % prefix
            + '    </owl:Ontology>\n')

def build(prefix, imports, content):
    return XMLDECL + NS_BLOCK + "\n" + ontology_block(prefix, imports) + "\n" + "".join(content) + "\n</rdf:RDF>\n"

for p in ORDER:
    with io.open(os.path.join(ONT, "sizhubazi-%s.owl" % p), "w", encoding="utf-8") as f:
        f.write(build(p, TOPOLOGY[p], buffers[p]))

# tbox 聚合入口
tbox_block = ('    <owl:Ontology rdf:about="http://example.org/sizhubazi/tbox">\n'
              + "".join('        <owl:imports rdf:resource="http://example.org/sizhubazi/%s"/>\n' % p for p in ORDER)
              + '        <rdfs:label xml:lang="zh">四柱命理本体 TBox 聚合入口（显式 imports 12 子本体）</rdfs:label>\n'
              + '        <rdfs:comment xml:lang="zh">第 10 步产出：TBox 聚合入口，按业务域显式导入 core/calendar/chart/relations/tengod/deity/pattern/yongshen/fortune/reading/diagnosis/rules 十二个子本体；并集语义与原单文件 TBox 图同构，imports 单向无环。第 13 步术语表（term）并入此入口。</rdfs:comment>\n'
              + '    </owl:Ontology>\n')
with io.open(os.path.join(ONT, "sizhubazi-tbox.owl"), "w", encoding="utf-8") as f:
    f.write(XMLDECL + NS_BLOCK + "\n" + tbox_block + "\n</rdf:RDF>\n")

print("done")
