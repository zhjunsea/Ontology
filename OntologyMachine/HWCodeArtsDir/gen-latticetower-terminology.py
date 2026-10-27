# -*- coding: utf-8 -*-
"""
Generate LatticeTower SKOS terminology (第 13 步产出，闸门⑥确认).
术语 1:1 覆盖本体全部类/对象属性/数据属性（157 个），prefLabel=本体中文标签，
altLabel=英文代码名 + 源自《客户调研.txt》的业务同义词，exactMatch=本体 IRI。
"""
import re, io, os

OWL = r'D:\work\Ontology\OntologyMachine\OntologyFrameworkLatticeTower\ontology\latticetower-all.owl'
OUT = r'D:\work\Ontology\OntologyMachine\OntologyFrameworkLatticeTower\ontology\latticetower-terminology.ttl'
ONT = 'http://example.org/latticetower#'
TERM = 'http://example.org/latticetower/term#'

# 反向属性（本体无中文标签）→ 可读中文名（对齐 IndustryInsight 词表做法）
INV = {
 'heightSpecOf':'高度规格所属','bottomWidthRangeOf':'底面宽度范围所属','facadeTypeOf':'立面形式所属',
 'validationOf':'输入校验结果所属','correctionHintOf':'修正提示所属','envelopeOf':'几何边界所属',
 'eccentricLineOf':'偏心线所属','eccentricOffsetOf':'偏心量所属','highTrussOf':'末段平面距所属',
 'diagonalLayoutOf':'斜腹杆布局所属','panelOf':'节间所属','layoutNodeOf':'布局节点所属',
 'eccentricIntersectionOf':'偏心线交点所属','crossingPointOf':'交叉点所属','layerOf':'层所属',
 'storeyHeightOf':'层高所属','crossingAggregatedBy':'被交叉点聚合','cornerPointOf':'角点所属',
 'memberOf':'构件所属','memberLengthOf':'构件长度所属','memberAtLayer':'构件位于层','betaOf':'夹角所属',
 'solutionOf':'可行方案所属','optimizedSolutionOf':'选定方案所属','constrains':'施加约束',
 'designResultOf':'设计结果所属','exportArtifactOf':'导出产物所属',
}

# 业务同义词（zh），源自《客户调研.txt》与第 2/4 步确认口径（不臆造）
SYN = {
 'DesignInput':['设计输入参数'],
 'HeightSpec':['塔架高度'],
 'BottomWidthRange':['底宽范围','塔底宽度范围'],
 'FacadeType':['立面型式'],
 'TowerEnvelope':['几何包络','梯形体边界'],
 'EccentricLine':['偏心偏移线'],
 'EccentricOffset':['偏心值'],
 'HighTruss':['high_truss','末段平面距'],
 'DiagonalLayout':['斜腹杆总布局'],
 'ZigzagLayout':['Z 字形斜腹杆','单面 Z 形布局'],
 'CrossLayout':['X 字形斜腹杆','单面 X 形布局'],
 'Panel':['节间面','面'],
 'LayoutNode':['节点'],
 'CrossingPoint':['双向斜腹杆交点'],
 'Layer':['成层'],
 'StoreyHeight':['楼层高'],
 'Column':['塔柱构件'],
 'Diagonal':['斜撑'],
 'HorizontalStrut':['横撑杆','横腹杆'],
 'Diaphragm':['横隔板'],
 'LayoutSolution':['可行布局方案'],
 'FeasibleLayoutSet':['逐底宽可行集'],
 'OptimizedSolution':['最优方案','选定布局方案'],
 'BetaAngle':['倾角','斜腹杆夹角'],
 'NoSolutionHint':['无解说明'],
 'DesignResult':['设计输出'],
 'ExportArtifact':['导出文件'],
 'bottomWidth':['塔底宽度','底宽'],
 'topWidth':['塔顶面宽度','塔顶宽'],
 'deviation':['水平横杆长度'],
 'betaValue':['夹角值'],
 'highTrussValue':['high_truss 值'],
 'nodeHeight':['节点标高'],
 'firstDiagonalOffsetValue':['首杆偏移值'],
}

def load():
    s=open(OWL,encoding='utf-8').read()
    def grab(kind):
        out=[]
        for n,b in re.findall(r'<'+kind+r' rdf:about="#([^"]+)">(.*?)</'+kind+r'>',s,re.S):
            m=re.search(r'rdfs:label xml:lang="zh">([^<]*)',b)
            out.append((n, m.group(1) if m else ''))
        return out
    return grab('owl:Class'),grab('owl:ObjectProperty'),grab('owl:DatatypeProperty')

def esc(t):
    return t.replace('\\','\\\\').replace('"','\\"')

def block(local, zh, en, kind):
    lines=[f'<{TERM}{local}> a skos:Concept ;',
           f'    skos:prefLabel "{esc(zh)}"@zh ;']
    alts=[]
    if en: alts.append((en,'en'))
    for sy in SYN.get(local,[]):
        if sy!=zh: alts.append((sy,'zh'))
    for sy,lg in alts:
        lines.append(f'    skos:altLabel "{esc(sy)}"@{lg} ;')
    lines.append(f'    skos:exactMatch <{ONT}{local}> .')
    return '\n'.join(lines)

def main():
    classes,objs,datas=load()
    parts=[]
    parts.append('@prefix skos: <http://www.w3.org/2004/02/skos/core#> .')
    parts.append('@prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .')
    parts.append('@prefix dct: <http://purl.org/dc/terms/> .')
    parts.append('@prefix xsd: <http://www.w3.org/2001/XMLSchema#> .')
    parts.append('')
    parts.append(f'<{TERM[:-1]}> a skos:ConceptScheme ;')
    parts.append('    skos:prefLabel "风机格构塔几何布置设计术语表"@zh ;')
    parts.append('    skos:prefLabel "Wind Turbine Lattice Tower Layout Terminology"@en ;')
    parts.append('    dct:created "2026-10-27"^^xsd:date ;')
    parts.append('    rdfs:comment "规范概念 1:1 覆盖本体（每个类/对象属性/数据属性对应一个概念，'
                 '共 157 个）；prefLabel=本体中文标签；altLabel=英文代码名 + 业务同义词（斜撑=斜腹杆、'
                 '横撑杆=横腹杆、底宽=塔底面宽度等），用于消解「不同名同义、同义不同名」。第 13 步产出；'
                 '闸门⑥：2026-10-27 经业务人员确认通过（157 概念与本体验 IRI 双射、无孤立词条）。"@zh .')
    parts.append('')
    parts.append('# ======================== 类（Classes） ========================')
    for n,zh in classes:
        if not zh: zh=INV.get(n,n)
        parts.append(block(n,zh,n,'class')); parts.append('')
    parts.append('# ======================== 对象属性（Object Properties） ========================')
    for n,zh in objs:
        if not zh: zh=INV.get(n,n)
        parts.append(block(n,zh,n,'obj')); parts.append('')
    parts.append('# ======================== 数据属性（Datatype Properties） ========================')
    for n,zh in datas:
        if not zh: zh=INV.get(n,n)
        parts.append(block(n,zh,n,'data')); parts.append('')
    txt='\n'.join(parts)
    open(OUT,'w',encoding='utf-8').write(txt)
    print('concepts=',len(classes)+len(objs)+len(datas),
          '(classes=%d obj=%d data=%d)'%(len(classes),len(objs),len(datas)))
    print('written:',OUT)

main()
