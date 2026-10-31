# -*- coding: utf-8 -*-
"""
第 10 步：ziweidoushu TBox 按业务域拆分为 12 子本体 + tbox.owl 聚合入口。
口径（照 SiZhuBaZi §4.2）：
  - 类声明块 -> 归该类 IRI 所属域
  - 对象/数据属性块 -> 归该属性 IRI 所属域
  - owl:Restriction 约束 -> 按 owl:onProperty 所属域归入对应文件（独立 <owl:Class> 块）
  - SWRL（rdf:Description） -> rules
  - 属性值一律保持完整绝对 IRI（不回退前缀）
并集内容须与原 TBox 图同构（公理集合等价校验）。
"""
import re, os, shutil, sys

HERE = os.path.dirname(os.path.abspath(__file__))
ONT = os.path.abspath(os.path.join(HERE, '..', 'OntologyFrameworkZiWeiDouShu', 'ontology'))
TBOX = os.path.join(ONT, 'ziweidoushu-tbox.owl')
NS = 'http://example.org/ziweidoushu'

DOMAINS = ['core', 'calendar', 'chart', 'star', 'layout', 'state',
           'palace', 'pattern', 'fortune', 'reading', 'diagnosis', 'rules']

IMPORTS = {
    'core': [],
    'calendar': ['core'],
    'chart': ['core', 'calendar'],
    'star': ['core'],
    'layout': ['core', 'calendar', 'chart', 'star'],
    'state': ['core', 'star', 'layout'],
    'palace': ['core', 'chart', 'layout'],
    'pattern': ['core', 'star', 'state', 'palace'],
    'fortune': ['core', 'chart', 'star', 'layout', 'palace', 'pattern'],
    'reading': ['core', 'star', 'state', 'palace', 'pattern', 'fortune'],
    'diagnosis': [],
    'rules': ['state', 'palace', 'pattern', 'fortune', 'reading'],
}

LABELS = {
    'core': '基础历法（阴阳/五行/天干/地支/六十甲子/纳音/五行局/十二长生）',
    'calendar': '历法时辰（真太阳时/闰月/时辰交界/昼夜/历法边界）',
    'chart': '排盘（生辰输入/命盘/十二宫/命身宫/宫干/命主身主）',
    'star': '星曜体系（十四主星南北斗/辅佐/煞忌/杂曜/太岁诸神）',
    'layout': '安星落宫（各星盘/四化落宫[动态]/十二长生落宫）',
    'state': '星曜状态（庙旺利陷/生克制化/宫位强弱/化气[静态]）',
    'palace': '宫位体系（十二宫义/三方四正/夹宫/空宫借星）',
    'pattern': '格局星情（赋论/诸格/命格高低/星曜组合/诸星所主）',
    'fortune': '运限（大限/小限/流年/流曜重算/叠宫/凶格引动/宫干飞化）',
    'reading': '断语（本命分类/女命/小儿/阴骘延寿劝诫）',
    'diagnosis': '出处与流派标注（SourceNote/SchoolVariant/待考/验盘）',
    'rules': 'SWRL 规则（含告警标志推导）',
}

PREFIX_BLOCK = '''     xmlns:owl="http://www.w3.org/2002/07/owl#"
     xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#"
     xmlns:rdfs="http://www.w3.org/2000/01/rdf-schema#"
     xmlns:xsd="http://www.w3.org/2001/XMLSchema#"
     xmlns:swrl="http://www.w3.org/2003/11/swrl#"
     xmlns:swrlb="http://www.w3.org/2003/11/swrlb#"
     xmlns:skos="http://www.w3.org/2004/02/skos/core#"
     xmlns:core="http://example.org/ziweidoushu/core#"
     xmlns:calendar="http://example.org/ziweidoushu/calendar#"
     xmlns:chart="http://example.org/ziweidoushu/chart#"
     xmlns:star="http://example.org/ziweidoushu/star#"
     xmlns:layout="http://example.org/ziweidoushu/layout#"
     xmlns:state="http://example.org/ziweidoushu/state#"
     xmlns:palace="http://example.org/ziweidoushu/palace#"
     xmlns:pattern="http://example.org/ziweidoushu/pattern#"
     xmlns:fortune="http://example.org/ziweidoushu/fortune#"
     xmlns:reading="http://example.org/ziweidoushu/reading#"
     xmlns:diagnosis="http://example.org/ziweidoushu/diagnosis#"
     xmlns:rules="http://example.org/ziweidoushu/rules#">

'''

TAG_RE = re.compile(
    r'<!--.*?-->|<([A-Za-z_][\w:.\-]*)((?:\s+[\w:.\-]+\s*=\s*"[^"]*")*)\s*(/?)>|</([A-Za-z_][\w:.\-]*)>',
    re.S)
RESTR_RE = re.compile(
    r'<rdfs:subClassOf>\s*(<owl:Restriction>.*?</owl:Restriction>)\s*</rdfs:subClassOf>', re.S)
ABOUT_RE = re.compile(r'rdf:about="([^"]*)"')
ONPROP_RE = re.compile(r'owl:onProperty\s+rdf:resource="([^"]*)"')
SEP_COMMENT_RE = re.compile(r'^<!--[\s=#\-]*$')


def norm(x):
    return re.sub(r'\s+', ' ', x).strip()


def domain_of(iri):
    m = re.match(r'http://example\.org/ziweidoushu/([A-Za-z]+)#', iri or '')
    return m.group(1) if m else None


def scan_tokens(text):
    """返回 [(kind, payload)]，kind in {'block','comment'}；block payload=(tag, raw)。"""
    toks = []
    depth = 0
    start = None
    starttag = None
    for m in TAG_RE.finditer(text):
        raw = m.group(0)
        if raw.startswith('<!--'):
            toks.append(('comment', raw))
            continue
        if m.group(4):  # closing
            if depth == 2 and start is not None:
                toks.append(('block', (starttag, text[start:m.end()])))
                start = None
            depth -= 1
            continue
        tag = m.group(1)
        selfclose = bool(m.group(3))
        depth += 1
        if depth == 2:
            start = m.start()
            starttag = tag
        if selfclose:
            if depth == 2 and start is not None:
                toks.append(('block', (starttag, text[start:m.end()])))
                start = None
            depth -= 1
    return toks


def reindent(raw):
    """把块文本规范为顶格 4 空格缩进（原文件已是 4 空格，直接 strip 首尾空白即可）。"""
    return raw.strip('\n')


def extract_axioms(files_texts):
    """从若干文件文本抽取规范化公理集合。"""
    axioms = set()
    for text in files_texts:
        for kind, payload in scan_tokens(text):
            if kind == 'comment':
                continue
            tag, raw = payload
            about = ABOUT_RE.search(raw)
            about = about.group(1) if about else None
            if tag == 'owl:Class':
                body = raw
                restrs = list(RESTR_RE.finditer(raw))
                for r in reversed(restrs):
                    body = body[:r.start()] + body[r.end():]
                inner = re.sub(r'\s+', ' ', body).strip()
                inner = re.sub(r'^<owl:Class\b[^>]*>', '', inner)
                inner = re.sub(r'</owl:Class>$', '', inner).strip()
                if inner:
                    axioms.add(('Class', about, inner))
                for r in restrs:
                    onp = ONPROP_RE.search(r.group(0))
                    axioms.add(('SubClassOf', about, norm(r.group(0))))
                    assert onp, 'restriction without onProperty'
            elif tag == 'owl:ObjectProperty':
                axioms.add(('ObjectProperty', about, norm(raw)))
            elif tag == 'owl:DatatypeProperty':
                axioms.add(('DatatypeProperty', about, norm(raw)))
            elif tag == 'rdf:Description':
                axioms.add(('Description', None, norm(raw)))
            elif tag == 'owl:Ontology':
                continue
            else:
                raise SystemExit('unexpected top-level tag: ' + tag)
    return axioms


def main():
    text = open(TBOX, encoding='utf-8').read()
    toks = scan_tokens(text)

    buckets = {d: [] for d in DOMAINS}
    pending_comments = []
    for kind, payload in toks:
        if kind == 'comment':
            if not SEP_COMMENT_RE.match(payload):
                pending_comments.append(payload.strip())
            continue
        tag, raw = payload
        about = ABOUT_RE.search(raw)
        about = about.group(1) if about else None
        prefix_lines = list(pending_comments)
        pending_comments = []

        if tag == 'owl:Class':
            dom = domain_of(about)
            assert dom in DOMAINS, 'bad class domain: %r (%s)' % (dom, about)
            body = raw
            restrs = list(RESTR_RE.finditer(raw))
            for r in reversed(restrs):
                body = body[:r.start()] + body[r.end():]
            head = reindent(body)
            head = re.sub(r'\n\s*\n+', '\n', head)
            inner_head = re.sub(r'^<owl:Class\b[^>]*>', '', head)
            inner_head = re.sub(r'</owl:Class>$', '', inner_head).strip()
            if inner_head:
                buckets[dom].append((prefix_lines, head))
                prefix_lines = []
            for r in restrs:
                onp = ONPROP_RE.search(r.group(0)).group(1)
                od = domain_of(onp)
                assert od in DOMAINS, 'bad restriction domain: %r (%s)' % (od, onp)
                block = ('    <owl:Class rdf:about="%s"><rdfs:subClassOf>%s</rdfs:subClassOf></owl:Class>'
                         % (about, r.group(1)))
                buckets[od].append((prefix_lines, block))
                prefix_lines = []
        elif tag in ('owl:ObjectProperty', 'owl:DatatypeProperty'):
            dom = domain_of(about)
            assert dom in DOMAINS, 'bad prop domain: %r (%s)' % (dom, about)
            buckets[dom].append((prefix_lines, reindent(raw)))
        elif tag == 'rdf:Description':
            buckets['rules'].append((prefix_lines, reindent(raw)))
        elif tag == 'owl:Ontology':
            continue
        else:
            raise SystemExit('unexpected tag: ' + tag)

    # 写出 12 子本体
    written = []
    for dom in DOMAINS:
        imports = ''.join('        <owl:imports rdf:resource="%s/%s"/>\n' % (NS, d)
                          for d in IMPORTS[dom])
        head = ('<?xml version="1.0"?>\n'
                '<rdf:RDF xmlns="%s/%s"\n'
                '     xml:base="%s/%s"\n'
                '%s'
                '    <owl:Ontology rdf:about="%s/%s">\n'
                '%s'
                '        <rdfs:label xml:lang="zh">紫微斗数本体 TBox 子本体 · %s（%s）</rdfs:label>\n'
                '        <rdfs:comment xml:lang="zh">第 10 步产出：TBox 按业务域拆分。命名空间 %s/%s 不变；由 ziweidoushu-tbox.owl 聚合入口统一 imports；imports 单向无环（§4.2）。依据《紫微斗數全書》（[宋]陈抟（陈希夷）撰）。属性值一律使用完整绝对 IRI。</rdfs:comment>\n'
                '    </owl:Ontology>\n\n'
                % (NS, dom, NS, dom, PREFIX_BLOCK, NS, dom, imports, dom, LABELS[dom], NS, dom))
        parts = [head]
        for comments, block in buckets[dom]:
            for c in comments:
                parts.append('    ' + c)
                parts.append('')
            parts.append(block)
            parts.append('')
        parts.append('</rdf:RDF>\n')
        out = '\n'.join(parts)
        out = re.sub(r'\n{3,}', '\n\n', out)
        path = os.path.join(ONT, 'ziweidoushu-%s.owl' % dom)
        open(path, 'w', encoding='utf-8').write(out)
        written.append(path)
        print('written %-32s blocks=%d' % (os.path.basename(path), len(buckets[dom])))

    # 等价校验：原 TBox 公理集合 == 12 子本体公理集合之并
    s_orig = extract_axioms([text])
    s_new = extract_axioms([open(p, encoding='utf-8').read() for p in written])
    print('\n=== axiom-set check ===')
    print('orig axioms :', len(s_orig))
    print('new  axioms :', len(s_new))
    only_o = s_orig - s_new
    only_n = s_new - s_orig
    print('only in orig:', len(only_o))
    print('only in new :', len(only_n))
    for x in list(only_o)[:5]:
        print('  O:', x)
    for x in list(only_n)[:5]:
        print('  N:', x)
    if s_orig != s_new:
        print('!!! NOT EQUIVALENT — aborting tbox rewrite')
        sys.exit(1)
    print('OK: 12 子本体公理集合与原 TBox 同构')

    # 备份并改写 tbox.owl 为聚合入口
    backup = os.path.join(HERE, '_ziweidoushu-tbox.owl.bak')
    if not os.path.exists(backup):
        shutil.copy2(TBOX, backup)
        print('backup ->', backup)
    imports = ''.join('        <owl:imports rdf:resource="%s/%s"/>\n' % (NS, d) for d in DOMAINS)
    entry = ('<?xml version="1.0"?>\n'
             '<rdf:RDF xmlns="%s/tbox"\n'
             '     xml:base="%s/tbox"\n'
             '%s'
             '    <owl:Ontology rdf:about="%s/tbox">\n'
             '%s'
             '        <rdfs:label xml:lang="zh">紫微斗数本体 TBox 聚合入口（显式 imports 12 子本体）</rdfs:label>\n'
             '        <rdfs:comment xml:lang="zh">第 10 步产出：TBox 聚合入口，按业务域显式导入 core/calendar/chart/star/layout/state/palace/pattern/fortune/reading/diagnosis/rules 十二个子本体；并集语义与原单文件 TBox 图同构，imports 单向无环。第 13 步术语表（term）并入此入口。</rdfs:comment>\n'
             '    </owl:Ontology>\n\n'
             '</rdf:RDF>\n'
             % (NS, NS, PREFIX_BLOCK, NS, imports))
    open(TBOX, 'w', encoding='utf-8').write(entry)
    print('rewritten tbox.owl -> aggregate entry')


if __name__ == '__main__':
    main()
