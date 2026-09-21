# -*- coding: utf-8 -*-
"""解析 tcm-core.owl 的 37 个八纲判据 Panju_*：label / comment / 合取-析取结构。
用途：① 为「判据缺口分析」提供结构；② 校验 union/intersection 语义。
"""
import os, re
import xml.etree.ElementTree as ET

RDF = '{http://www.w3.org/1999/02/22-rdf-syntax-ns#}'
OWL = '{http://www.w3.org/2002/07/owl#}'

def local(t):
    return t.split('}')[-1]

def frag(u):
    return u.split('#')[-1] if u else '?'

def parse_node(el):
    """返回结构树：('AND',[...]) / ('OR',[...]) / ('RESTR', prop, filler) / ('CLS', name)"""
    lt = local(el.tag)
    if lt == 'Restriction':
        onp = svf = None
        for ch in el:
            if local(ch.tag) == 'onProperty':
                onp = ch.get(RDF + 'resource')
            elif local(ch.tag) == 'someValuesFrom':
                svf = ch.get(RDF + 'resource')
        return ('RESTR', frag(onp), frag(svf))
    if lt == 'Class':
        about = el.get(RDF + 'about')
        if about:
            return ('CLS', frag(about))
        for ch in el:
            lc = local(ch.tag)
            if lc == 'intersectionOf':
                return ('AND', [parse_node(c) for c in ch if local(c.tag) in ('Class', 'Restriction')])
            if lc == 'unionOf':
                return ('OR', [parse_node(c) for c in ch if local(c.tag) in ('Class', 'Restriction')])
        return ('CLS', '?')
    return ('CLS', '?')

def render(node, depth=0):
    k = node[0]
    if k == 'RESTR':
        return node[2]
    if k == 'CLS':
        return node[1]
    op = ' ⊓ ' if k == 'AND' else ' ∪ '
    parts = [render(c, depth + 1) for c in node[1]]
    inner = op.join(parts)
    return '(' + inner + ')' if depth > 0 else inner

def count_required(node):
    """按正确语义计数：AND → 子项数之和；OR → 1（任一满足即可）；RESTR/CLS → 1"""
    k = node[0]
    if k in ('RESTR', 'CLS'):
        return 1
    if k == 'OR':
        return 1
    return sum(count_required(c) for c in node[1])

def collect_leaves(node, out=None):
    if out is None:
        out = []
    k = node[0]
    if k == 'RESTR':
        out.append(node[2])
    elif k == 'CLS':
        out.append(node[1])
    else:
        for c in node[1]:
            collect_leaves(c, out)
    return out

base = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..', 'ontology')
tree = ET.parse(os.path.join(base, 'tcm-core.owl'))
root = tree.getroot()

rows = []
for cls in root.findall('owl:Class', {'owl': 'http://www.w3.org/2002/07/owl#'}):
    about = cls.get(RDF + 'about')
    if not about:
        continue
    name = frag(about)
    if not name.startswith('Panju_'):
        continue
    label = ''
    comment = ''
    for ch in cls:
        if local(ch.tag) == 'label':
            label = (ch.text or '').strip()
        elif local(ch.tag) == 'comment':
            comment = (ch.text or '').strip()
    # 判据归属的八纲（subClassOf）
    sup = []
    for ch in cls:
        if local(ch.tag) == 'subClassOf':
            r = ch.get(RDF + 'resource')
            if r:
                sup.append(frag(r))
    eq = None
    for ch in cls:
        if local(ch.tag) == 'equivalentClass':
            for c in ch:
                eq = parse_node(c)
    rows.append((name, label, sup, eq, comment))

def key(n):
    m = re.match(r'Panju_([A-Z])(\d+)', n)
    return (m.group(1), int(m.group(2))) if m else ('Z', 0)

rows.sort(key=lambda r: key(r[0]))

print('判据总数 =', len(rows))
print()
for name, label, sup, eq, comment in rows:
    struct = render(eq) if eq else '(无 ≡)'
    req = count_required(eq) if eq else 0
    leaves = collect_leaves(eq) if eq else []
    print('%-10s %s' % (name, label))
    print('    ⊑ %s   |  required(正确语义)=%d  叶子数=%d' % (', '.join(sup), req, len(leaves)))
    print('    ≡ %s' % struct)
    if comment:
        c = comment.replace('\n', ' ')
        print('    依据: %s' % (c[:150] + ('…' if len(c) > 150 else '')))
    print()
