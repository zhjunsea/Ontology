#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""提取方证类的等价定义（合取约束），用于分析方证误选。

用法:
  python _fz.py <fragment> [<fragment> ...]
  python _fz.py --all <file.owl>     # 列出某文件所有方证及其约束数
"""
import sys, os, re
import xml.etree.ElementTree as ET

BASE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
FZDIR = os.path.join(os.path.dirname(BASE), 'ontology', 'fangzheng')

NS = {
    'owl': 'http://www.w3.org/2002/07/owl#',
    'rdf': 'http://www.w3.org/1999/02/22-rdf-syntax-ns#',
    'rdfs': 'http://www.w3.org/2000/01/rdf-schema#',
}
OWL = NS['owl']
RDF = NS['rdf']
RDF_ABOUT = '{' + NS['rdf'] + '}about'
RDF_RESOURCE = '{' + NS['rdf'] + '}resource'

PROP_MAP = {
    'you_zhengzhuang': '症',
    'you_maixiang': '脉',
    'you_shexiang': '舌',
    'you_fuzheng': '腹',
}


def frag(uri):
    if '#' in uri:
        return uri.rsplit('#', 1)[1]
    return uri.rstrip('/').rsplit('/', 1)[-1]


def parse_file(path):
    """返回 {fragment: {'label':..., 'constraints':[(prop, filler), ...], 'sub':[...], 'eq':bool}}"""
    tree = ET.parse(path)
    root = tree.getroot()
    out = {}
    for cls in root.findall('owl:Class', NS):
        about = cls.get(RDF_ABOUT)
        if about is None:
            continue
        f = frag(about)
        rec = {'label': None, 'constraints': [], 'sub': [], 'eq': False}
        for lab in cls.findall('rdfs:label', NS):
            if lab.text:
                rec['label'] = lab.text
                break
        # subClassOf
        for sub in cls.findall('rdfs:subClassOf', NS):
            r = sub.get(RDF_RESOURCE)
            if r:
                rec['sub'].append(frag(r))
        # equivalentClass -> intersectionOf -> Restriction
        for eq in cls.findall('owl:equivalentClass', NS):
            inner = eq.find('owl:Class', NS)
            if inner is None:
                continue
            inter = inner.find('owl:intersectionOf', NS)
            if inter is None:
                continue
            rec['eq'] = True
            for restr in inter.findall('owl:Restriction', NS):
                onp = restr.find('owl:onProperty', NS)
                some = restr.find('owl:someValuesFrom', NS)
                if onp is None or some is None:
                    continue
                p = frag(onp.get(RDF_RESOURCE, ''))
                v = frag(some.get(RDF_RESOURCE, ''))
                rec['constraints'].append((p, v))
        out[f] = rec
    return out


def load_all():
    allrec = {}
    for fn in os.listdir(FZDIR):
        if not fn.endswith('.owl') or '.bak' in fn:
            continue
        try:
            recs = parse_file(os.path.join(FZDIR, fn))
        except Exception as e:
            print(f'!! {fn}: {e}', file=sys.stderr)
            continue
        for k, v in recs.items():
            v['file'] = fn
            allrec[k] = v
    return allrec


def show(allrec, name):
    r = allrec.get(name)
    if not r:
        print(f'--- {name}: NOT FOUND ---')
        return
    print(f'=== {name}  [{r.get("label")}]  ({r.get("file")})  eq={r["eq"]}  n={len(r["constraints"])}')
    print(f'    sub: {r["sub"]}')
    for p, v in r['constraints']:
        print(f'      {PROP_MAP.get(p, p)}: {v}')


def main():
    if len(sys.argv) < 2:
        print(__doc__)
        return
    allrec = load_all()
    if sys.argv[1] == '--all':
        fn = sys.argv[2]
        recs = parse_file(os.path.join(FZDIR, fn))
        for k, v in sorted(recs.items()):
            if v['eq']:
                print(f'{k:55s} n={len(v["constraints"]):2d}  {v.get("label")}')
        return
    for name in sys.argv[1:]:
        show(allrec, name)


if __name__ == '__main__':
    main()
