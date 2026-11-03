# -*- coding: utf-8 -*-
"""H4：解析《三命通会》卷一·释六十甲子性质吉凶（60 条：干支+五行取象+喜+所带神煞）。"""
import os
import re

BASE = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..',
                    'OntologyFrameworkSiZhuBaZi', 'design', '四书全文整理', '三命通会.txt')
BASE = os.path.normpath(BASE)

GAN = '甲乙丙丁戊己庚辛壬癸'
ZHI = '子丑寅卯辰巳午未申酉戌亥'
START = re.compile(r'([' + GAN + r'][' + ZHI + r'])([木火土金水])，')


def _clean_lines():
    with open(BASE, encoding='utf-8') as f:
        lines = f.read().split('\n')   # 仅按 \n 切（与 read 工具一致）
    # 找 451 行（1-based）起
    return lines


# OCR 讹字就地还原（★禁编造：仅还原《三命通会》OCR 本公认讹字，不改原文语义）
#   辛卯木→辛卯未（未/木形近）、丁酉火→丁西火（酉/西形近）、己未火→已未火（己/已形近）
OCR_FIX = [
    ('辛卯未，', '辛卯木，'),
    ('丁西火，', '丁酉火，'),
    ('已未火，', '己未火，'),
]


def sixty_nature():
    lines = _clean_lines()
    # 起始：含“○释六十甲子性质吉凶”的行
    start = None
    for i, ln in enumerate(lines):
        if '释六十甲子性质吉凶' in ln:
            start = i
            break
    # 结束：其后第一处“以上六十甲子”（该篇结语）
    end = len(lines)
    for j in range(start + 1, len(lines)):
        if '以上六十甲子' in lines[j]:
            end = j
            break
    # 合并正文，剔除页码/空白/书名页记
    buf = []
    for ln in lines[start + 1:end]:
        s = ln.strip()
        if not s:
            continue
        if re.fullmatch(r'[·\s0-9]+', s):
            continue
        if s.replace(' ', '') in ('三命通会',):
            continue
        buf.append(s)
    text = ''.join(buf)
    for a, b in OCR_FIX:
        text = text.replace(a, b)
    # 按起点切分
    idxs = [m.start() for m in START.finditer(text)]
    out = []
    for k, pos in enumerate(idxs):
        nxt = idxs[k + 1] if k + 1 < len(idxs) else len(text)
        seg = text[pos:nxt]
        m = START.match(seg)
        gz = m.group(1)
        wu = m.group(2)
        body = seg[m.end():].rstrip('。').strip()
        out.append({'ganzhi': gz, 'wuxing': wu, 'text': body})
    return out


if __name__ == '__main__':
    import sys
    import io
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')
    r = sixty_nature()
    print('count =', len(r))
    for x in r[:8]:
        print(x['ganzhi'], x['wuxing'], '|', x['text'][:60])
    print('...')
    gzs = [x['ganzhi'] for x in r]
    print('unique =', len(set(gzs)))
    # 校验 60 甲子齐全
    allgz = [GAN[i % 10] + ZHI[i % 12] for i in range(60)]
    print('missing =', [g for g in allgz if g not in gzs])
