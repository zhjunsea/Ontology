# -*- coding: utf-8 -*-
"""提取 tcm-core.owl 中全部 Panju_* 判据的 ⊑ 目标（八纲/病性）与合取条件。"""
import io, os, re

BASE = r'D:\work\Ontology\TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox\ontology'
CORE = os.path.join(BASE, 'tcm-core.owl')


def block(txt, nm):
    lines = txt.split('\n')
    for i, l in enumerate(lines):
        if re.search(r'<owl:Class rdf:about="#%s">' % nm, l):
            depth = 0
            for j in range(i, len(lines)):
                s = lines[j]
                for m in re.finditer(r'<owl:Class\b[^>]*?(/?)>', s):
                    if m.group(1) != '/':
                        depth += 1
                depth -= s.count('</owl:Class>')
                if depth == 0:
                    return '\n'.join(lines[i:j + 1])
    return None


def main():
    txt = io.open(CORE, 'r', encoding='utf-8', errors='replace').read()
    names = re.findall(r'<owl:Class rdf:about="#(Panju_\w+)">', txt)
    seen = []
    for nm in names:
        if nm in seen:
            continue
        seen.append(nm)
    for nm in seen:
        b = block(txt, nm)
        if not b:
            continue
        subs = re.findall(r'<rdfs:subClassOf rdf:resource="#(\w+)"/>', b)
        lab = re.search(r'<rdfs:label xml:lang="zh">(.*?)</rdfs:label>', b)
        rests = re.findall(r'<owl:onProperty rdf:resource="#(\w+)"/>\s*<owl:someValuesFrom rdf:resource="#(\w+)"/>', b)
        union = 'unionOf' in b
        print('%-12s subs=%-42s %s%s' % (
            nm, subs, 'UNION ' if union else '',
            ['%s.%s' % r for r in rests]))
        if lab:
            print('             label=%s' % lab.group(1))


if __name__ == '__main__':
    main()
