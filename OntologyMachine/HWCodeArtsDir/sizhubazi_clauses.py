# -*- coding: utf-8 -*-
"""条文库抽取：G3《三命通会》卷八·日时断（720）；G8《穷通宝鉴》调候（十天干逐月）。

数据来源（★铁律 8/9/10，逐条可回原文定位、绝不编造）：
  - G3：design/四书全文整理/三命通会.txt  卷八/卷九「六X日Y时断」
  - G8：design/四书全文整理/穷通宝鉴.txt  （《造化元钥》徐乐吾评注）「X月Y五行」小节
OCR 讹字按上下文就地还原（见 _DAYHOUR_HEADER_FIX），源本真缺者建【待考】占位。
"""
import re, os

GAN = '甲乙丙丁戊己庚辛壬癸'
ZHI = '子丑寅卯辰巳午未申酉戌亥'
_HERE = os.path.dirname(os.path.abspath(__file__))
_TXT = os.path.normpath(os.path.join(_HERE, '..', 'OntologyFrameworkSiZhuBaZi',
                                     'design', '四书全文整理'))

# 五鼠遁：日干 -> 子时天干序号
_HSTEM0 = {'甲': 0, '己': 0, '乙': 2, '庚': 2, '丙': 4, '辛': 4,
           '丁': 6, '壬': 6, '戊': 8, '癸': 8}
_TOBE = '【待考】源本未载，待校'


def _read(name):
    with open(os.path.join(_TXT, name), encoding='utf-8') as f:
        return f.read()


def _clean_smth(s):
    s = re.sub(r'三\s*命\s*通\s*会', '', s)
    s = re.sub(r'·\s*[0-9 ]+·', '', s)
    return s


# 卷八起始锚点
_SMTH_START = 175000

# 日时断：按标题（六X日Y时断）就地 OCR 还原（仅作用于该块）
_DAYHOUR_HEADER_FIX = {
    ('乙', '丁亥'): [],
    ('己', '己巳'): [('己亥日乙丑时', '己亥日己巳时')],
    ('辛', '壬辰'): [('辛巳日在辰时', '辛巳日壬辰时')],
    ('戊', '丙辰'): [('戊申时丙辰时', '戊申日丙辰时')],
    ('丙', '庚寅'): [('两子日庚寅时', '丙子日庚寅时')],
    ('丁', '庚子'): [('丁卯庚子时', '丁卯日庚子时')],
    ('己', '壬申'): [('已卯日壬申时', '己卯日壬申时')],
    ('戊', '甲寅'): [('戊戌日申寅时', '戊戌日甲寅时')],
    ('丙', '己丑'): [('丙申日巳丑时', '丙申日己丑时')],
    ('己', '甲子'): [('己巳日庚子时', '己巳日甲子时')],
    ('辛', '己丑'): [('辛已日己丑时', '辛巳日己丑时')],
    ('辛', '戊子'): [('辛已日戊子时', '辛巳日戊子时')],
    ('辛', '甲午'): [('辛卯目甲午时', '辛卯日甲午时')],
    ('甲', '己巳'): [('甲午月己巳时', '甲午日己巳时')],
    ('癸', '戊午'): [('癸已日戊午时', '癸巳日戊午时')],
    ('己', '癸酉'): [('己丑日癸亥时', '己丑日癸酉时')],
}

_HOUR_PIL = None  # (day_pillar, hour_branch) -> hour_pillar
_HDR = re.compile(r'○六([甲乙丙丁戊己庚辛壬癸])日([甲乙丙丁戊己庚辛壬癸][子丑寅卯辰巳午未申酉戌亥])时断')
_ENTRY = re.compile(r'([甲乙丙丁戊己庚辛壬癸])([子丑寅卯辰巳午未申酉戌亥])日'
                    r'([甲乙丙丁戊己庚辛壬癸])([子丑寅卯辰巳午未申酉戌亥])时')


def day_hour_clauses():
    """返回 720 条：{clause_id, day_pillar, hour_pillar, day_stem, day_branch,
    hour_branch, clause_text, source_note}。"""
    s = _clean_smth(_read('三命通会.txt')[_SMTH_START:])
    s = re.sub(r'([甲乙丙丁戊己庚辛壬癸])末日', r'\1未日', s)  # OCR 末->未
    hpos = [m.start() for m in _HDR.finditer(s)]
    hpos.append(len(s))
    heads = [(m.group(1), m.group(2)) for m in _HDR.finditer(s)]

    out = {}
    for i, (dstem, hpill) in enumerate(heads):
        block = s[hpos[i]:hpos[i + 1]]
        for a, b in _DAYHOUR_HEADER_FIX.get((dstem, hpill), []):
            block = block.replace(a, b)
        ms = list(_ENTRY.finditer(block))
        for j, m in enumerate(ms):
            seg = block[m.start(): ms[j + 1].start() if j + 1 < len(ms) else len(block)]
            seg = re.sub(r'\s+', '', seg).strip()
            dp = dstem + m.group(2)
            hp = m.group(3) + m.group(4)
            if (dp, hp) not in out:
                out[(dp, hp)] = seg

    cyc = [GAN[k % 10] + ZHI[k % 12] for k in range(60)]
    rows = []
    for d in cyc:
        ds = d[0]
        for hb_i, hb in enumerate(ZHI):
            hs = GAN[(_HSTEM0[ds] + hb_i) % 10]
            hp = hs + hb
            txt = out.get((d, hp), _TOBE)
            rows.append({
                'clause_id': 'DHC_%s_%s' % (d, hp),
                'day_pillar': d, 'hour_pillar': hp,
                'day_stem': ds, 'day_branch': d[1], 'hour_branch': hb,
                'clause_text': txt,
                'source_note': '三命通会卷八·六%s日%s时断' % (ds, hb),
            })
    return rows


# ---------------- G8 调候 ----------------
_MONTH_NUM = {'正月': [1], '二月': [2], '三月': [3], '四月': [4], '五月': [5], '六月': [6],
              '七月': [7], '八月': [8], '九月': [9], '十月': [10], '十一月': [11], '十二月': [12],
              '冬月': [11], '腊月': [12],
              '正二月': [1, 2], '五六月': [5, 6], '七八月': [7, 8], '九十月': [9, 10],
              '十一十二月': [11, 12],
              '三春': [1, 2, 3], '三夏': [4, 5, 6], '三秋': [7, 8, 9], '三冬': [10, 11, 12]}
_MNAME = ['正月', '二月', '三月', '四月', '五月', '六月',
          '七月', '八月', '九月', '十月', '十一月', '十二月']
_G8_HDR = re.compile(r'(三春|三夏|三秋|三冬|[正二三四五六七八九十]{1,3}月)'
                     r'([甲乙丙丁戊己庚辛壬癸])([木火土金水])')
_USE = re.compile(r'(?:专用|专取|宜用|须用|先取|次取|先|次|用|取|以|得)'
                  r'([甲乙丙丁戊己庚辛壬癸]{1,3})')


def _use_stems(txt):
    """从章节首句提炼调候用神干（照录原文，最多取前 3）；无则空。"""
    head = txt.split('。')[0][:220]
    got = []
    for m in _USE.finditer(head):
        for ch in m.group(1):
            if ch in GAN and ch not in got:
                got.append(ch)
    return got


def tiaohou_clauses():
    """返回 120 条：{clause_id, day_stem, month_branch, month_order,
    primary_use, secondary_use, clause_text, source_note}。"""
    t = _read('穷通宝鉴.txt')
    hdrs = list(_G8_HDR.finditer(t))
    # 两步：先仅用「单月」小节；缺失者再用「合月/总论」小节。
    # 同一标题多次出现时，取“到下一标题的文本段最长者”（正文小节 vs 目录条目）。
    def collect(allow_single=True, allow_combined=False):
        acc = {}
        for i, m in enumerate(hdrs):
            label, stem = m.group(1), m.group(2)
            if label not in _MONTH_NUM:
                continue
            single = len(_MONTH_NUM[label]) == 1
            if single and not allow_single:
                continue
            if not single and not allow_combined:
                continue
            end = hdrs[i + 1].start() if i + 1 < len(hdrs) else len(t)
            seg = re.sub(r'\s+', ' ', t[m.end():end]).strip()
            if len(seg) < 40:
                continue
            for mn in _MONTH_NUM[label]:
                if (stem, mn) not in acc or len(seg) > len(acc[(stem, mn)][1]):
                    acc[(stem, mn)] = (seg, _use_stems(seg))
        return acc

    best = collect(True, False)
    combined = collect(False, True)
    for k, v in combined.items():
        best.setdefault(k, v)

    rows = []
    for stem in GAN:
        for mn in range(1, 13):
            hb = ZHI[(mn + 1) % 12]  # 正月建寅
            rec = best.get((stem, mn))
            if rec is None:
                uses, seg = [], ''
            else:
                seg, uses = rec
            first = (seg.split('。')[0][:180].lstrip('，、') + '。') if seg else _TOBE
            first = re.sub(r'\s+', '', first)
            prim = uses[0] if uses else '【待考】'
            sec = '/'.join(uses[1:3]) if len(uses) > 1 else ''
            rows.append({
                'clause_id': 'THC_%s_%s' % (stem, hb),
                'day_stem': stem, 'month_branch': hb, 'month_order': mn,
                'primary_use': prim, 'secondary_use': sec,
                'clause_text': first,
                'source_note': '穷通宝鉴（造化元钥）·%s%s' % (_MNAME[mn - 1], stem),
            })
    return rows


if __name__ == '__main__':
    d = day_hour_clauses()
    print('G3 rows:', len(d), '待考:', sum(1 for r in d if r['clause_text'].startswith('【待考】')))
    for r in d:
        if r['clause_text'].startswith('【待考】'):
            print('  gap', r['clause_id'])
    th = tiaohou_clauses()
    nv = sum(1 for r in th if r['primary_use'] == '【待考】')
    print('G8 rows:', len(th), '无据（待考）:', nv)
    for r in th[:6]:
        print(' ', r['clause_id'], r['primary_use'], r['secondary_use'], '|', r['clause_text'][:60])
