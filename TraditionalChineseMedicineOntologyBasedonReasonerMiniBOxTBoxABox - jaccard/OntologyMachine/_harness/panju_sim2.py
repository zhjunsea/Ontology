# -*- coding: utf-8 -*-
"""
panju_sim2.py —— panju_sim.py 的增强版：把「病性层（Re/Shi/Han/Xu）」也纳入模拟，
并支持多种「阴阳裁定」策略，用于秒级验证修复方案。

用法：
    python panju_sim2.py                     # 默认策略 none（现状）
    python panju_sim2.py --yy hanre          # 寒热优先
    python panju_sim2.py --yy hanre_xushi    # 寒热优先，无寒热时用虚实
    python panju_sim2.py --yy panju_first    # 判据(D/E)优先于病性
    python panju_sim2.py --case 百合地黄汤证  # 单例明细
"""
import re, os, sys, json, argparse
import xml.etree.ElementTree as ET
from collections import defaultdict

ROOT = r'D:\work\Ontology\TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox'
ONT = os.environ.get('PANJU_ONT') or os.path.join(ROOT, 'ontology')
TEST = os.path.join(ROOT, 'OntologyMachine', 'OntologyFramework', 'src', 'test', 'java', 'com', 'ocean', 'ontologyframework')

LOC = ['Biao', 'Li', 'Banbiaobanli']
NAT = ['Yang', 'Yin']
BINGXING = ['Re', 'Shi', 'Han', 'Xu']
BAGANG = LOC + NAT
BAGANG_ALL = LOC + BINGXING + NAT
SIX = ['Taiyangbing', 'Yangmingbing', 'Shaoyangbing', 'Taiyinbing', 'Shaoyinbing', 'Jueyinbing']
CHANNEL_OF = {
    ('Biao', 'Yang'): 'Taiyangbing', ('Li', 'Yang'): 'Yangmingbing',
    ('Banbiaobanli', 'Yang'): 'Shaoyangbing', ('Li', 'Yin'): 'Taiyinbing',
    ('Biao', 'Yin'): 'Shaoyinbing', ('Banbiaobanli', 'Yin'): 'Jueyinbing',
}
MUTEX = {'Shaoyangbing': 'Jueyinbing'}

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

NS = {'owl': 'http://www.w3.org/2002/07/owl#',
      'rdf': 'http://www.w3.org/1999/02/22-rdf-syntax-ns#',
      'rdfs': 'http://www.w3.org/2000/01/rdf-schema#'}
RDF_RES = '{http://www.w3.org/1999/02/22-rdf-syntax-ns#}resource'


def _node(el):
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
                    if r in BAGANG_ALL:
                        bagang_of[frag].add(r)
            if frag.startswith('Panju_'):
                eq = el.find('owl:equivalentClass', NS)
                cls = eq.find('owl:Class', NS) if eq is not None else None
                panju.append({'id': frag, 'targets': set(bagang_of.get(frag, ())), 'expr': _node(cls)})

    changed = True
    while changed:
        changed = False
        for f in list(subclass):
            for s in list(subclass[f]):
                for s2 in subclass.get(s, ()):
                    if s2 not in subclass[f]:
                        subclass[f].add(s2); changed = True

    # 病性 → 阴阳（Re/Shi → Yang；Han/Xu → Yin）
    for f in list(bagang_of):
        if 'Re' in bagang_of[f] or 'Shi' in bagang_of[f]:
            bagang_of[f].add('Yang')
        if 'Han' in bagang_of[f] or 'Xu' in bagang_of[f]:
            bagang_of[f].add('Yin')
    return subclass, panju, bagang_of


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
    s = re.sub(r'//[^\n]*', '', s)
    return ''.join(re.findall(r'"([^"]*)"', s))


SUITE_FILES = {
    'HerbRuleEngineTest', 'DuliFangzhengTest', 'HebingFangzhengTest', 'JianjiaFangzhengTest',
    'JueyinFangzhengTest', 'ShaoyangYangmingFangzhengTest', 'ShaoyinTaiyinFangzhengTest',
    'TaiyangFangzhengTest', 'ZabingFangzhengTest',
}


def load_cases(suite_only=False):
    cases = []
    for dp, dn, fn in os.walk(TEST):
        for f in fn:
            if not f.endswith('.java'):
                continue
            if suite_only and f[:-5] not in SUITE_FILES:
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
                    'file': f[:-5], 'name': _unq(parts[0]), 'lj': _unq(parts[1]),
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
    return [c for c in SIX if c.replace('bing', '') in lj]


def close_findings(frags, subclass):
    out = set(frags)
    for f in frags:
        out |= subclass.get(f, set())
    return out


def resolve_yinyang(bagang, detail, mode):
    """按策略把 阴阳 收敛为单一值。返回新的 bagang 集合。"""
    if mode == 'none':
        return bagang
    has = lambda x: x in bagang
    if mode == 'hanre':
        if has('Re'):
            return (bagang - {'Yin'}) | {'Yang'}
        if has('Han'):
            return (bagang - {'Yang'}) | {'Yin'}
        return bagang
    if mode == 'hanre_xushi':
        if has('Re'):
            return (bagang - {'Yin'}) | {'Yang'}
        if has('Han'):
            return (bagang - {'Yang'}) | {'Yin'}
        if has('Shi'):
            return (bagang - {'Yin'}) | {'Yang'}
        if has('Xu'):
            return (bagang - {'Yang'}) | {'Yin'}
        return bagang
    if mode == 'panju_first':
        # 若命中 D*/E* 判据，则以其阴阳为准（阴判据优先）
        d_hit = any(p.startswith('Panju_D') for p in detail)
        e_hit = any(p.startswith('Panju_E') for p in detail)
        if e_hit and not d_hit:
            return (bagang - {'Yang'}) | {'Yin'}
        if d_hit and not e_hit:
            return (bagang - {'Yin'}) | {'Yang'}
        if e_hit and d_hit:
            return (bagang - {'Yang'}) | {'Yin'}   # 阴判据优先
        return bagang
    if mode == 'panju_hanre':
        # 判据优先；无判据时寒热优先
        d_hit = any(p.startswith('Panju_D') for p in detail)
        e_hit = any(p.startswith('Panju_E') for p in detail)
        if e_hit and not d_hit:
            return (bagang - {'Yang'}) | {'Yin'}
        if d_hit and not e_hit:
            return (bagang - {'Yin'}) | {'Yang'}
        if e_hit and d_hit:
            return (bagang - {'Yang'}) | {'Yin'}
        if has('Re'):
            return (bagang - {'Yin'}) | {'Yang'}
        if has('Han'):
            return (bagang - {'Yang'}) | {'Yin'}
        return bagang
    return bagang


def derive(case, subclass, panju, bagang_of, mode='none'):
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
        bagang |= {b for b in subclass.get(f, set()) if b in BAGANG_ALL}
    for pj in panju:
        if _eval(pj['expr'], have):
            bagang |= pj['targets']
            detail.append(pj['id'])
    bagang = resolve_yinyang(bagang, detail, mode)
    locs = [b for b in LOC if b in bagang]
    nats = [b for b in NAT if b in bagang]
    chans = sorted({CHANNEL_OF[(l, n)] for l in locs for n in nats if (l, n) in CHANNEL_OF})
    for yang, yin in MUTEX.items():
        if yang in chans and yin in chans:
            chans.remove(yang)
    return bagang, chans, detail


def judge(case, chans):
    exp = resolve_anchor(case['lj'])
    if not exp:
        return None, 'no-expected'
    if len(chans) > 1:
        return (exp[0] in chans), '合病'
    return (chans == [exp[0]]), '单经'


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--case')
    ap.add_argument('--yy', default='none')
    ap.add_argument('--suite', action='store_true', help='只统计 9 个套件测试类')
    ap.add_argument('--json')
    args = ap.parse_args()
    subclass, panju, bagang_of = load_ontology()
    cases = load_cases(args.suite)
    print(f'判据数={len(panju)}  用例数={len(cases)}  阴阳策略={args.yy}')
    rows, fails, undecided = [], [], []
    for cs in cases:
        bagang, chans, detail = derive(cs, subclass, panju, bagang_of, args.yy)
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
                print('  期望六经 =', r['exp'], '(lj=' + r['lj'] + ')')
                print('  推出八纲 =', r['bagang'])
                print('  命中判据 =', r['panju'])
                print('  推出六经 =', r['chans'], '=>', 'PASS' if r['ok'] else 'FAIL')
        return
    npass = len(rows) - len(fails) - len(undecided)
    print(f'\n通过 {npass} / {len(rows)}   失败 {len(fails)}   无法判定 {len(undecided)}')
    by_exp = defaultdict(list)
    for r in fails:
        by_exp[tuple(r['exp'])].append(r)
    for exp, rs in sorted(by_exp.items(), key=lambda kv: -len(kv[1])):
        print(f'\n### 期望 {exp} —— {len(rs)} 例')
        for r in rs[:15]:
            print(f'   {r["name"]:<24} 八纲={r["bagang"]} 六经={r["chans"]}')
        if len(rs) > 15:
            print(f'   ... 另 {len(rs)-15} 例')
    if args.json:
        json.dump(rows, open(args.json, 'w', encoding='utf-8'), ensure_ascii=False, indent=1)


if __name__ == '__main__':
    main()
