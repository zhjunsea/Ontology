# -*- coding: utf-8 -*-
"""提取指定方证类的定义摘要。"""
import io, os, re, sys

BASE = r'D:\work\Ontology\TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox\ontology'
FZ = os.path.join(BASE, 'fangzheng')

def block(txt, nm):
    """按嵌套深度提取 <owl:Class rdf:about="#nm"> ... </owl:Class> 整块。"""
    lines = txt.split('\n')
    for i, l in enumerate(lines):
        if re.search(r'<owl:Class rdf:about="#%s">' % nm, l):
            depth = 0
            for j in range(i, len(lines)):
                s = lines[j]
                # 统计本行内 <owl:Class ...> 开标签（含自闭合不算）
                for m in re.finditer(r'<owl:Class\b[^>]*?(/?)>', s):
                    if m.group(1) != '/':
                        depth += 1
                depth -= s.count('</owl:Class>')
                if depth == 0:
                    return '\n'.join(lines[i:j + 1])
    return None

def main():
    names = sys.argv[1:]
    for fn in sorted(os.listdir(FZ)):
        if not fn.endswith('.owl'):
            continue
        txt = io.open(os.path.join(FZ, fn), 'r', encoding='utf-8', errors='replace').read()
        for nm in names:
            body = block(txt, nm)
            if not body or '<rdfs:label' not in body:
                continue
            lab = re.search(r'<rdfs:label xml:lang="zh">(.*?)</rdfs:label>', body)
            subs = re.findall(r'<rdfs:subClassOf rdf:resource="#(\w+)"/>', body)
            lj = re.findall(r'<belongsToLiujing rdf:resource="#(\w+)"/>', body)
            da = re.search(r'<differentialAxis xml:lang="zh">(.*?)</differentialAxis>', body)
            cp = re.search(r'<clinicalPriority[^>]*>(\d+)</clinicalPriority>', body)
            ba = re.findall(r'<bagangAttr rdf:resource="#(\w+)"/>', body)
            eq = re.search(r'<owl:equivalentClass>(.*?)</owl:equivalentClass>', body, re.S)
            rests = []; classes = []; neg = 0
            if eq:
                e = eq.group(1)
                rests = re.findall(r'<owl:onProperty rdf:resource="#(\w+)"/>\s*<owl:someValuesFrom rdf:resource="#(\w+)"/>', e)
                classes = re.findall(r'<owl:Class rdf:about="#(\w+)"/>', e)
                neg = e.count('complementOf')
            print('%-38s [%s] lj=%s cp=%s ba=%s' % (nm, fn, lj, cp.group(1) if cp else '?', ba))
            print('   label=%s' % (lab.group(1) if lab else '?'))
            print('   axis=%s' % (da.group(1) if da else '?'))
            print('   eqCls=%s rests=%s neg=%d' % (classes, ['%s.%s' % r for r in rests], neg))
            print()

if __name__ == '__main__':
    main()
