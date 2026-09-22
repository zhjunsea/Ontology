# -*- coding: utf-8 -*-
"""
panju_sim.py —— 八纲/六经 离线模拟器（不启动应用）

用途：把「症状/脉/舌 → 判据 → 八纲 → 六经」这条链在纯 Python 里复现，
      从而在秒级内验证「新增/修改判据」对全测试套件的影响，
      避免每次改本体都要重启应用（约 200s+）。

不做什么：不替代 Openllet。它只复现本体的**合取判据 + 白名单 + 传递闭包**语义。
          最终仍须由真实推理机复核。

用法：
    python panju_sim.py                      # 跑全量，报失败聚类
    python panju_sim.py --case 小柴胡汤证      # 看单个用例的推导明细
    python panju_sim.py --cand D8=Yang:Wanglaihanre+Xiongxiekuman
                                             # 试算候选判据（不改本体）
"""
import re, os, sys, json, argparse
import xml.etree.ElementTree as ET
from collections import defaultdict

ROOT = r'D:\work\Ontology\TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox'
ONT = os.environ.get('PANJU_ONT') or os.path.join(ROOT, 'ontology')
TEST = os.path.join(ROOT, 'OntologyMachine', 'OntologyFramework', 'src', 'test', 'java', 'com', 'ocean', 'ontologyframework')

BAGANG = ['Biao', 'Li', 'Banbiaobanli', 'Yang', 'Yin']
SIX = ['Taiyangbing', 'Yangmingbing', 'Shaoyangbing', 'Taiyinbing', 'Shaoyinbing', 'Jueyinbing']
# 六经 ≡ 病位 ⊓ 病性
CHANNEL_OF = {
    ('Biao', 'Yang'): 'Taiyangbing',
    ('Li', 'Yang'): 'Yangmingbing',
    ('Banbiaobanli', 'Yang'): 'Shaoyangbing',
    ('Li', 'Yin'): 'Taiyinbing',
    ('Biao', 'Yin'): 'Shaoyinbing',
    ('Banbiaobanli', 'Yin'): 'Jueyinbing',
}
MUTEX = {'Shaoyangbing': 'Jueyinbing'}   # LIUJING_MUTEX_PAIRS：半表半里阴覆盖阳

ANCHOR_SYMS = {
    'Taiyangbing': ['Ehan'], 'Yangmingbing': ['Kouke'],
    'Shaoyangbing': ['Wanglaihanre', 'Xiongxiekuman'],
    'Taiyinbing': ['Fuman'], 'Shaoyinbing': ['Danyumei'],
    'Jueyinbing': ['Xiaoke', 'Shouzuleng'],
}
ANCHOR_PULSES = {
    'Taiyangbing': ['Fumai'], 'Yangmingbing': ['Hongmai'],
    'Shaoyangbing': ['Xianmai'], 'Taiyinbing': ['Ruomai'],
    'Shaoyinbing': ['Chenweimai'], 'Jueyinbing': ['Weiximai'],
}
LIUJING_OF_HEBING = {
    'Taiyangyangminghebing': ['Taiyangbing', 'Yangmingbing'],
    'Taiyangshaoyanghebing': ['Taiyangbing', 'Shaoyangbing'],
    'Yangmingshaoyanghebing': ['Yangmingbing', 'Shaoyangbing'],
    'TaiyinYangmingHebing': ['Taiyinbing', 'Yangmingbing'],
    'ShaoyangTaiyinHebing': ['Shaoyangbing', 'Taiyinbing'],
    'TaiyangYangmingHebing': ['Taiyangbing', 'Yangmingbing'],
    'TaiyangTaiyinHebing': ['Taiyangbing', 'Taiyinbing'],
    'Sanyanghebing': ['Taiyangbing', 'Yangmingbing', 'Shaoyangbing'],
}
LIUJING_OF_MISC = {
    'Shibing': 'Taiyangbing', 'Xiongbibing': 'Taiyinbing', 'Feizhangbing': 'Taiyangbing',
    'Shuiqibing': 'Taiyangbing', 'Bentunbing': 'Taiyangbing', 'Xuebibing': 'Taiyangbing',
    'Jingbing': 'Taiyangbing', 'Jingjibing': 'Taiyinbing', 'Taiyangzhongye': 'Taiyangbing',
    'Xulaobing': 'Shaoyinbing', 'Nuebing': 'Shaoyangbing', 'Tanyinbing': 'Taiyinbing',
    'Shuixiebing': 'Taiyinbing', 'Outuoyuexialibing': 'Taiyinbing', 'Furenzabing': 'Taiyinbing',
    'Furenchanhoubing': 'Taiyinbing', 'Chanhoubing': 'Taiyinbing', 'Renshengbing': 'Taiyinbing',
    'Jinchuangbing': 'Taiyinbing', 'Zhuanjinbing': 'Taiyinbing', 'Feiweibing': 'Taiyinbing',
    'Ganzhuobing': 'Taiyinbing', 'Feiweifeiyongkesoushangqi': 'Taiyinbing',
    'Feiyongbing': 'Taiyinbing', 'Kesoushangqibing': 'Taiyinbing', 'Hanshanbing': 'Shaoyinbing',
    'Huangdanbing': 'Yangmingbing', 'Changyongbing': 'Yangmingbing', 'Tunvxiaxuebing': 'Yangmingbing',
    'Xiaxuebing': 'Taiyinbing', 'Yinyangdu': 'Jueyinbing', 'Huhuobing': 'Jueyinbing',
    'Zhongfengbing': 'Jueyinbing', 'Yinhushanbing': 'Jueyinbing', 'Huichongbing': 'Jueyinbing',
    'Chuangyongchangyongjinyinbing': 'Jueyinbing', 'Baihebing': 'Shaoyangbing',
    'Lijiebing': 'Jueyinbing', 'Fumanbing': 'Yangmingbing', 'Chahoulaofubing': 'Yangmingbing',
}


# ---------------------------------------------------------------- 本体解析
NS = {'owl': 'http://www.w3.org/2002/07/owl#',
      'rdf': 'http://www.w3.org/1999/02/22-rdf-syntax-ns#',
      'rdfs': 'http://www.w3.org/2000/01/rdf-schema#'}
RDF_RES = '{http://www.w3.org/1999/02/22-rdf-syntax-ns#}resource'


def _node(el):
    """把 owl:Restriction / owl:Class 元素转成布尔表达式节点。

    支持任意嵌套（如 A8：交集里嵌并集），这是正则做不到的。
    节点形态：('restr', prop, frag) / ('and', [..]) / ('or', [..])
    """
    if el is None:
        return None
    tag = el.tag
    if tag.endswith('}Restriction'):
        p = el.find('owl:onProperty', NS)
        v = el.find('owl:someValuesFrom', NS)
        if p is None or v is None:
            return None
        return ('restr', p.get(RDF_RES).lstrip('#'), v.get(RDF_RES).lstrip('#'))
    inter = el.find('owl:intersectionOf', NS)
    if inter is not None:
        return ('and', [x for x in (_node(c) for c in list(inter)) if x])
    uni = el.find('owl:unionOf', NS)
    if uni is not None:
        return ('or', [x for x in (_node(c) for c in list(uni)) if x])
    return None


def _eval(node, have):
    if not node:
        return False
    t = node[0]
    if t == 'restr':
        return node[2] in have.get(node[1], set())
    if t == 'and':
        return all(_eval(c, have) for c in node[1])
    if t == 'or':
        return any(_eval(c, have) for c in node[1])
    return False


def load_ontology():
    """返回 (subclass: frag->set(supers), panju: list(dict), bagang_of: frag->set)"""
    subclass = defaultdict(set)
    panju = []
    bagang_of = defaultdict(set)

    files = []
    for dp, dn, fn in os.walk(ONT):
        for f in fn:
            if f.endswith('.owl'):
                files.append(os.path.join(dp, f))

    for p in files:
        try:
            root = ET.parse(p).getroot()
        except Exception as e:
            print(f'  [warn] 解析失败 {os.path.basename(p)}: {e}')
            continue
        for el in root.iter('{http://www.w3.org/2002/07/owl#}Class'):
            frag = el.get('{http://www.w3.org/1999/02/22-rdf-syntax-ns#}about')
            if not frag:
                continue
            frag = frag.lstrip('#')
            if not re.fullmatch(r'[A-Za-z0-9_]+', frag):
                continue
            for sup in el.findall('rdfs:subClassOf', NS):
                r = sup.get(RDF_RES)
                if not r:
                    continue
                r = r.lstrip('#')
                if re.fullmatch(r'[A-Za-z0-9_]+', r):
                    subclass[frag].add(r)
                    if r in BAGANG:
                        bagang_of[frag].add(r)
            if frag.startswith('Panju_'):
                eq = el.find('owl:equivalentClass', NS)
                cls = eq.find('owl:Class', NS) if eq is not None else None
                panju.append({'id': frag,
                              'targets': set(bagang_of.get(frag, ())),
                              'expr': _node(cls)})

    # 传递闭包：frag -> 全部祖先（含八纲）
    changed = True
    while changed:
        changed = False
        for f in list(subclass):
            for s in list(subclass[f]):
                for s2 in subclass.get(s, ()):
                    if s2 not in subclass[f]:
                        subclass[f].add(s2); changed = True

    # 病性/病位 传递：Re/Shi -> Yang ; Han/Xu -> Yin
    for f in list(bagang_of):
        if 'Re' in bagang_of[f] or 'Shi' in bagang_of[f]:
            bagang_of[f].add('Yang')
        if 'Han' in bagang_of[f] or 'Xu' in bagang_of[f]:
            bagang_of[f].add('Yin')

    return subclass, panju, bagang_of


# ---------------------------------------------------------------- 用例解析
def _split_top(call):
    parts, d, cur, instr = [], 0, '', False
    for ch in call:
        if ch == '"':
            instr = not instr
        if not instr:
            if ch in '([{':
                d += 1
            elif ch in ')]}':
                d -= 1
            elif ch == ',' and d == 0:
                parts.append(cur.strip()); cur = ''; continue
        cur += ch
    parts.append(cur.strip())
    return parts


def _unq(s):
    s = s.strip()
    return s[1:-1] if len(s) >= 2 and s[0] == '"' and s[-1] == '"' else s


def _strs(s):
    """取出实参里的字符串字面量并拼接。

    测试里常见 `"A;B" + "C;D"` 跨行拼接与行内 `// 注释`，
    直接 _unq 会把代码当 fragment，故统一按字面量拼接。
    """
    s = re.sub(r'//[^\n]*', '', s)
    return ''.join(re.findall(r'"([^"]*)"', s))


def load_cases():
    cases = []
    for dp, dn, fn in os.walk(TEST):
        for f in fn:
            if not f.endswith('.java'):
                continue
            s = open(os.path.join(dp, f), encoding='utf-8').read()
            for m in re.finditer(r'assertFangzheng\s*\(', s):
                i = m.end(); depth = 1; j = i
                while j < len(s) and depth:
                    if s[j] == '(':
                        depth += 1
                    elif s[j] == ')':
                        depth -= 1
                    j += 1
                parts = _split_top(s[i:j - 1])
                if len(parts) < 6:
                    continue
                cases.append({
                    'file': f[:-5],
                    'name': _unq(parts[0]),
                    'lj': _unq(parts[1]),
                    'syms': [x for x in _strs(parts[4]).split(';') if x],
                    'pulses': [x for x in _strs(parts[5]).split(';') if x],
                    'tongues': [x for x in _strs(parts[6]).split(';') if x] if len(parts) > 6 else [],
                })
    return cases


def resolve_anchor(lj):
    if lj in SIX:
        return [lj]
    if lj in LIUJING_OF_HEBING:
        return LIUJING_OF_HEBING[lj]
    if ';' in lj:
        return [x for x in lj.split(';') if x in SIX]
    if lj in LIUJING_OF_MISC:
        return [LIUJING_OF_MISC[lj]]
    out = [c for c in SIX if c.replace('bing', '') in lj]
    return out


# ---------------------------------------------------------------- 推理模拟
def close_findings(frags, subclass):
    """发现类 + 其全部祖先（含八纲）"""
    out = set(frags)
    for f in frags:
        out |= subclass.get(f, set())
    return out


def derive(case, subclass, panju, bagang_of, extra_panju=()):
    """返回 (八纲集合, 六经列表, 明细)"""
    syms = set(case['syms']); pulses = set(case['pulses']); tongues = set(case['tongues'])
    for ch in resolve_anchor(case['lj']):
        syms |= set(ANCHOR_SYMS.get(ch, []))
        pulses |= set(ANCHOR_PULSES.get(ch, []))

    have = {'you_zhengzhuang': close_findings(syms, subclass),
            'you_maixiang': close_findings(pulses, subclass),
            'you_shexiang': close_findings(tongues, subclass)}

    bagang = set()
    detail = []
    for f in syms | pulses | tongues:
        bagang |= bagang_of.get(f, set())
        # 传递闭包里的八纲也要收（如 Ruomai ⊑ Xu ⊑ Yin、Fuhuamai ⊑ Re ⊑ Yang）
        bagang |= {b for b in subclass.get(f, set()) if b in BAGANG}

    for pj in list(panju) + list(extra_panju):
        if _eval(pj['expr'], have):
            bagang |= pj['targets']
            detail.append(pj['id'])

    locs = [b for b in BAGANG[:3] if b in bagang]
    nats = [b for b in BAGANG[3:] if b in bagang]
    chans = sorted({CHANNEL_OF[(l, n)] for l in locs for n in nats if (l, n) in CHANNEL_OF})
    # 半表半里互斥消解
    for yang, yin in MUTEX.items():
        if yang in chans and yin in chans:
            chans.remove(yang)
    return bagang, chans, detail


def judge(case, chans):
    """复现 assertBasicResult 的判定"""
    exp = resolve_anchor(case['lj'])
    if not exp:
        return None, 'no-expected'
    if len(chans) > 1:
        return (exp[0] in chans), '合病'
    return (chans == [exp[0]]), '单经'


# ---------------------------------------------------------------- 主流程
def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--case', help='只看某个用例名（子串匹配）')
    ap.add_argument('--cand', action='append', default=[],
                    help='候选判据，形如 D8=Yang:Wanglaihanre+Xiongxiekuman')
    ap.add_argument('--json', help='把结果写到该 json 文件')
    args = ap.parse_args()

    subclass, panju, bagang_of = load_ontology()
    cases = load_cases()

    # 候选判据语法：  ID=Target:z:症状+m:脉象+s:舌象   （无前缀默认 z:）
    #   例： D8=Yang:z:Ehan+m:Fumai
    #   支持括号内并集： B8=Li:z:Fuman+(m:Ruomai|m:Weimai)
    PROP = {'z': 'you_zhengzhuang', 'm': 'you_maixiang', 's': 'you_shexiang'}

    def _tok(tok):
        tok = tok.strip()
        if len(tok) > 2 and tok[1] == ':' and tok[0] in PROP:
            return ('restr', PROP[tok[0]], tok[2:])
        return ('restr', 'you_zhengzhuang', tok)

    def _split_top(s, sep):
        parts, d, cur = [], 0, ''
        for ch in s:
            if ch == '(':
                d += 1
            elif ch == ')':
                d -= 1
            if ch == sep and d == 0:
                parts.append(cur); cur = ''; continue
            cur += ch
        parts.append(cur)
        return parts

    def _expr(s):
        """'+' = and（顶层）；'(a|b)' = or。"""
        s = s.strip()
        if s.startswith('(') and s.endswith(')'):
            return ('or', [_expr(x) for x in _split_top(s[1:-1], '|')])
        return _tok(s)

    extra = []
    for c in args.cand:
        head, rhs = c.split('=', 1)
        tgt, conj = rhs.split(':', 1)
        items = [_expr(t) for t in _split_top(conj, '+')]
        extra.append({'id': head + '(候选)', 'targets': set(tgt.split('+')),
                      'expr': ('and', items)})

    print(f'判据数={len(panju)}  用例数={len(cases)}  候选判据={len(extra)}')
    print(f'白名单覆盖发现数={len(bagang_of)}')

    rows, fails, undecided = [], [], []
    for cs in cases:
        bagang, chans, detail = derive(cs, subclass, panju, bagang_of, extra)
        ok, kind = judge(cs, chans)
        row = {'file': cs['file'], 'name': cs['name'], 'lj': cs['lj'], 'exp': resolve_anchor(cs['lj']),
               'bagang': sorted(bagang), 'chans': chans, 'panju': detail, 'ok': ok, 'kind': kind}
        rows.append(row)
        if ok is False:
            fails.append(row)
        elif ok is None:
            undecided.append(row)

    if args.case:
        for r in rows:
            if args.case in r['name']:
                print('\n--- ' + r['name'] + ' [' + r['file'] + '] ---')
                print('  期望六经 =', r['exp'], ' (lj=' + r['lj'] + ')')
                print('  推出八纲 =', r['bagang'])
                print('  命中判据 =', r['panju'])
                print('  推出六经 =', r['chans'], '=>', 'PASS' if r['ok'] else 'FAIL')
        return

    npass = len(rows) - len(fails) - len(undecided)
    print(f'\n通过 {npass} / {len(rows)}   失败 {len(fails)}   无法判定 {len(undecided)}')
    if undecided:
        print('  （无法判定 = 测试 lj 无法映射到六经，多为杂病或解析噪声）')
        for r in undecided[:12]:
            print(f'     {r["name"]:<24} lj={r["lj"]}')
    by_exp = defaultdict(list)
    for r in fails:
        by_exp[tuple(r['exp'])].append(r)
    for exp, rs in sorted(by_exp.items(), key=lambda kv: -len(kv[1])):
        print(f'\n### 期望 {exp} —— {len(rs)} 例')
        for r in rs[:12]:
            print(f'   {r["name"]:<24} 八纲={r["bagang"]} 六经={r["chans"]}')
        if len(rs) > 12:
            print(f'   ... 另 {len(rs)-12} 例')

    if args.json:
        json.dump(rows, open(args.json, 'w', encoding='utf-8'), ensure_ascii=False, indent=1)
        print('\n已写 ' + args.json)


if __name__ == '__main__':
    main()
