# -*- coding: utf-8 -*-
"""dump 指定方证的 equivalentClass 症状集 + belongsToLiujing + prio"""
import re, sys, io, glob, os
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')

ONT = r"D:\work\Ontology\TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox\ontology"
TARGETS = ['Dachaihutangzheng','Xiaochengqitangzheng','Dachengqitangzheng',
           'Baihujiaguizhitangzheng','Baihutangzheng','Baihujiarenshentangzheng',
           'Guizhirenshentangzheng','Chishizhiyuyuliangtangzheng',
           'Chaihubaihutangzheng','Xiaochaihutangzheng']

files = glob.glob(os.path.join(ONT, '**', '*.owl'), recursive=True)
for f in files:
    t = open(f, encoding='utf-8', errors='replace').read()
    for n in TARGETS:
        m = re.search(r'<owl:Class rdf:about="#' + n + r'">(.*?)</owl:Class>\s*(?=<owl:Class|<!--|</rdf:RDF)', t, re.S)
        if not m: continue
        body = m.group(1)
        lj = re.findall(r'<belongsToLiujing rdf:resource="#([^"]+)"', body)
        prio = re.search(r'<clinicalPriority[^>]*>(\d+)<', body)
        syms = re.findall(r'<owl:someValuesFrom rdf:resource="#([^"]+)"', body)
        # 只取 you_zhengzhuang 的
        syms2 = re.findall(r'<owl:onProperty rdf:resource="#you_zhengzhuang"/><owl:someValuesFrom rdf:resource="#([^"]+)"', body)
        bag = re.findall(r'<bagangAttr rdf:resource="#([^"]+)"', body)
        print(f"[{n}] file={os.path.basename(f)} 六经={lj} prio={prio.group(1) if prio else '?'} 八纲={bag}")
        print(f"    症状({len(syms2)}): {syms2}")
