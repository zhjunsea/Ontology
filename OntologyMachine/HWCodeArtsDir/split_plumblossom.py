# -*- coding: utf-8 -*-
"""
第 9 步：将单文件 plumblossom-all.owl 拆分为 plumblossom-tbox.owl + plumblossom-abox.owl，
并将 plumblossom-all.owl 改造为聚合入口（imports tbox + abox）。
纯文本区块级拆分，保留原 serialization（含短前缀 IRI 与 SWRL 结构）。
"""
from pathlib import Path

ONT = Path(r"D:/work/Ontology/OntologyMachine/OntologyFrameworkPlumBlossom/ontology")
SRC = ONT / "plumblossom-all.owl"

lines = SRC.read_text(encoding="utf-8").splitlines(keepends=True)
n = len(lines)

def find(marker):
    for i, l in enumerate(lines):
        if marker in l:
            return i  # 0-based
    raise SystemExit(f"marker not found: {marker}")

i_class = find("==================== 类层级")            # TBox 起点（类层级注释）
i_abox  = find("==================== 典型 ABox")         # ABox 起点注释
i_swrl  = find("==================== SWRL 规则（R1~R14")  # SWRL 起点注释
i_end   = None
for i in range(n - 1, -1, -1):
    if lines[i].strip() == "</rdf:RDF>":
        i_end = i
        break
assert i_end is not None

# 区块（0-based 半开区间）
tbox_block = lines[i_class:i_abox]     # 类/对象属性/数据属性/约束
abox_block = lines[i_abox:i_swrl]      # 典型 ABox 个体
swrl_block = lines[i_swrl:i_end]       # SWRL 规则

# 命名空间块 = rdf:RDF 开始标签（第 2 行到含 xmlns:rules 的那一行）
ns_start = 1  # 0-based，第 2 行 ` <rdf:RDF ...`
i_ns_end = find('xmlns:rules=')
ns_block = lines[ns_start:i_ns_end + 1]  # 含结尾 '>' 的那行

def ns_for(iri):
    """按目标命名空间重写默认 xmlns 与 xml:base 两行。"""
    out = list(ns_block)
    out[0] = f'<rdf:RDF xmlns="{iri}"\n'                 # 原第 2 行
    out[1] = f'         xml:base="{iri}"\n'              # 原第 3 行
    return "".join(out)

def onto_all():
    return (
        '    <owl:Ontology rdf:about="http://example.org/plumblossom/all">\n'
        '        <owl:imports rdf:resource="http://example.org/plumblossom/tbox"/>\n'
        '        <!-- abox\n'
        '             第 9 步：ABox 的 owl:imports 按铁律 11 注释掉——ABox 不进全局推理器（避免拖累 CLASS_HIERARCHY 分类），\n'
        '             由应用侧经 ontology.abox-path 独立加载（复用 OntologyService，见 PlumBlossomOntologyJobWorker）。\n'
        '             参考 Example/ontology/pizza-all.owl 的分离形态。\n'
        '        <owl:imports rdf:resource="http://example.org/plumblossom/abox"/>\n'
        '        -->\n'
        '        <rdfs:label xml:lang="zh">梅花易数本体总入口（TBox 聚合入口；ABox 由应用侧独立加载）</rdfs:label>\n'
        '        <rdfs:comment xml:lang="zh">第 9 步产出：聚合入口，显式导入 TBox。ABox 的 imports 依铁律 11 注释掉，由应用工程经 ontology.abox-path 指向 plumblossom-abox.owl 独立加载。加载此文件即可导入全部 TBox 模块。</rdfs:comment>\n'
        '    </owl:Ontology>\n'
    )

def onto_tbox():
    return (
        '    <owl:Ontology rdf:about="http://example.org/plumblossom/tbox">\n'
        '        <rdfs:label xml:lang="zh">梅花易数 TBox（类层级 / 对象属性 / 数据属性 / 约束 / SWRL 规则）</rdfs:label>\n'
        '        <rdfs:comment xml:lang="zh">第 9 步产出：由原 plumblossom-all.owl 拆分。含全量 TBox（90 类、64 对象属性、41 数据属性、Restriction 约束、SWRL 规则 R1~R14）。命名空间 core/div/yijing/rel/sym/ome/jud/cal/rules 保持不变。</rdfs:comment>\n'
        '    </owl:Ontology>\n'
    )

def onto_abox():
    return (
        '    <owl:Ontology rdf:about="http://example.org/plumblossom/abox">\n'
        '        <owl:imports rdf:resource="http://example.org/plumblossom/tbox"/>\n'
        '        <rdfs:label xml:lang="zh">梅花易数 ABox（典型个体：六十四卦名录 / 八卦 / 四不占 / 吉凶等级与事项类别 / 干支等）</rdfs:label>\n'
        '        <rdfs:comment xml:lang="zh">第 9 步产出：由原 plumblossom-all.owl 拆分。含 247 个代表性典型个体（六十四卦全名录、卦辞爻辞全集留待第 12 步扩全集）。依赖 TBox 中的类与属性，故显式 imports tbox。</rdfs:comment>\n'
        '    </owl:Ontology>\n'
    )

xml_decl = '<?xml version="1.0" encoding="UTF-8"?>\n'
end = '</rdf:RDF>\n'

# ---- 写出 tbox ----
tbox = xml_decl + ns_for("http://example.org/plumblossom/tbox") + "\n" + onto_tbox() + "\n" + \
       "".join(tbox_block) + "".join(swrl_block) + "\n" + end
(ONT / "plumblossom-tbox.owl").write_text(tbox, encoding="utf-8")

# ---- 写出 abox ----
abox = xml_decl + ns_for("http://example.org/plumblossom/abox") + "\n" + onto_abox() + "\n" + \
       "".join(abox_block) + "\n" + end
(ONT / "plumblossom-abox.owl").write_text(abox, encoding="utf-8")

# ---- 改造 all 为聚合入口 ----
allowl = xml_decl + ns_for("http://example.org/plumblossom/all") + "\n" + onto_all() + "\n" + end
(ONT / "plumblossom-all.owl").write_text(allowl, encoding="utf-8")

print("tbox lines:", tbox.count("\n"), "| abox lines:", abox.count("\n"), "| all lines:", allowl.count("\n"))
print("done")
