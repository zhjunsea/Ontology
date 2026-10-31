# -*- coding: utf-8 -*-
"""第 9 步：将 sizhubazi-all.owl（TBox + 典型 ABox 单文件）拆分为
sizhubazi-tbox.owl + sizhubazi-abox.owl，并把 sizhubazi-all.owl 改为聚合入口。
铁律 55：ABox 只搬不删（不改任何三元组）；铁律 11：主入口对 ABox 的 imports 注释掉。
"""
import io, os

ONT = r"D:/work/Ontology/OntologyMachine/OntologyFrameworkSiZhuBaZi/ontology"
SRC = os.path.join(ONT, "sizhubazi-all.owl")

with io.open(SRC, encoding="utf-8") as f:
    lines = f.readlines()  # 0-indexed; lines[i] == 行 i+1


def R(a, b):
    """1-indexed inclusive"""
    return "".join(lines[a - 1:b])


XMLDECL = '<?xml version="1.0" encoding="UTF-8"?>\n'
NS_BLOCK = R(2, 22)  # <rdf:RDF ...> 至最后一个 xmlns

# ---- TBox 内容：29-506（对象属性/数据属性/类/约束）+ 692-693（yearStemYinYang）+ 695-761（SWRL 注释块+变量+规则）----
tbox_content = R(29, 506) + R(692, 693) + "\n" + R(695, 761)

# ---- ABox 内容：507-690（典型 ABox）----
abox_content = R(507, 690)

OWL_TBOX = '''    <owl:Ontology rdf:about="http://example.org/sizhubazi/tbox">
        <rdfs:label xml:lang="zh">四柱命理本体 TBox（术语定义层）</rdfs:label>
        <rdfs:comment xml:lang="zh">第 9 步产出：TBox 独立文件，含对象属性、数据属性、类、owl:Restriction 约束与 SWRL 规则（R1/R5/R6/R19/R21/R22）。由 sizhubazi-abox.owl 显式 imports；第 10 步（闸门⑨）再按业务域细拆为 core/calendar/chart/relations/tengod/deity/pattern/yongshen/fortune/reading/diagnosis/rules 子本体。原单文件 sizhubazi-all.owl 语义等价迁移（铁律 55：只搬不删）。</rdfs:comment>
    </owl:Ontology>
'''

OWL_ABOX = '''    <owl:Ontology rdf:about="http://example.org/sizhubazi/abox">
        <owl:imports rdf:resource="http://example.org/sizhubazi/tbox"/>
        <rdfs:label xml:lang="zh">四柱命理本体 ABox（典型实例层）</rdfs:label>
        <rdfs:comment xml:lang="zh">第 9 步产出：ABox 独立文件，含术语型个体（阴阳/五行/天干/地支/六十甲子/纳音/藏干/长生）、神煞与格局样例、出处标注个体、典型命例。显式 imports TBox；主入口 sizhubazi-all.owl 对其 imports 依铁律 11 注释掉，由应用侧经 ontology.abox-path 独立加载。第 11 步（闸门⑩）查表型数据入库 MySQL，第 12 步（闸门⑪）扩至全集。</rdfs:comment>
    </owl:Ontology>
'''

OWL_ALL = '''    <owl:Ontology rdf:about="http://example.org/sizhubazi/all">
        <owl:imports rdf:resource="http://example.org/sizhubazi/tbox"/>
        <!-- abox：ABox 不参与全局推理器，且运行态数据由 OBDA(MySQL) 提供（sizhubazi.obda 映射至同 IRI 命名空间），
             故依铁律 11 注释掉其 imports，由应用侧经 ontology.abox-path 独立加载。
        <owl:imports rdf:resource="http://example.org/sizhubazi/abox"/>
        -->
        <rdfs:label xml:lang="zh">四柱命理本体总入口（聚合 TBox；ABox 独立加载）</rdfs:label>
        <rdfs:comment xml:lang="zh">第 9 步产出：主入口，显式 imports sizhubazi-tbox.owl；ABox 的 imports 依铁律 11 注释掉，由应用侧经 ontology.abox-path 独立加载。第 10 步后 tbox 改为聚合入口。</rdfs:comment>
    </owl:Ontology>
'''

def build(ontology_block, content):
    return XMLDECL + NS_BLOCK + "\n" + ontology_block + "\n" + content + "\n</rdf:RDF>\n"


with io.open(os.path.join(ONT, "sizhubazi-tbox.owl"), "w", encoding="utf-8") as f:
    f.write(build(OWL_TBOX, tbox_content))
with io.open(os.path.join(ONT, "sizhubazi-abox.owl"), "w", encoding="utf-8") as f:
    f.write(build(OWL_ABOX, abox_content))
with io.open(os.path.join(ONT, "sizhubazi-all.owl"), "w", encoding="utf-8") as f:
    f.write(build(OWL_ALL, ""))

print("tbox lines:", tbox_content.count("\n"))
print("abox lines:", abox_content.count("\n"))
print("done")
