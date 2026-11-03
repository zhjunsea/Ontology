# -*- coding: utf-8 -*-
"""典籍正文逐篇抽取（第 26 步）——滴天髓 / 穷通宝鉴 / 神峰通考 / 三命通会 / 渊海子平。

★ 数据源：design/四书全文整理/*.txt（客户归档文本，唯一真源）；本脚本**运行时从原文抽取**，
   照录正文（不编造、不截断），清洗页眉/页码/目录噪声行。
输出：classic_texts() -> [(text_id, book_name, chapter, text, source_note)]
"""
import os
import re

BASE = os.path.normpath(os.path.join(
    os.path.dirname(os.path.abspath(__file__)), '..',
    'OntologyFrameworkSiZhuBaZi', 'design', '四书全文整理'))


def _lines(fn):
    """按 '\\n' 切行（与 wc/grep/编辑器一致，行号可核验）；去除行内残留 '\\r'。"""
    txt = open(os.path.join(BASE, fn), encoding='utf-8').read()
    return [ln.replace('\r', '') for ln in txt.split('\n')]


def _is_noise(s):
    """页眉/页码/空白噪声行。"""
    if not s:
        return True
    if s == '未知':
        return True
    t = s.replace(' ', '').replace('\u3000', '')
    if re.fullmatch(r'[·\-—\s]*\d+[·\-—\s]*', s):
        return True
    if re.fullmatch(r'\d+\s*\|\s*\d+', s):
        return True
    if re.fullmatch(r'三命通会', t):
        return True
    if re.fullmatch(r'[一二三四五六七八九十]{1,3}', t) and len(t) <= 3 and s == t:
        return False
    return False


def _join_block(lines, lo, hi, extra_skip=()):
    """把 [lo,hi) 行合并为一行正文（去噪声行，直接拼接）。"""
    parts = []
    for ln in lines[lo:hi]:
        s = ln.strip()
        if _is_noise(s):
            continue
        if any(rx.search(s) for rx in extra_skip):
            continue
        parts.append(s)
    return ''.join(parts)


# ---------------------------------------------------------------------------
# 滴天髓：通神论(34篇)+六亲论(29篇)
# ---------------------------------------------------------------------------
def _dts():
    lines = _lines('滴天髓.txt')
    page = re.compile(r'^\s*\d+\s*\|\s*\d+\s*$')
    part_re = re.compile(r'^(通神论|六亲论)$')
    title_re = re.compile(r'^[一二三四五六七八九十]+、\S+$')
    start = next(i for i, ln in enumerate(lines) if i > 86 and ln.strip() == '通神论')
    marks = []  # (idx, part, title)
    part = ''
    for i in range(start, len(lines)):
        s = lines[i].strip()
        if not s or page.match(s) or _is_noise(s):
            continue
        if part_re.match(s):
            part = s
            continue
        if title_re.match(s) and len(s) <= 12:
            marks.append((i, part, s))
    out = []
    for k, (i, pt, ti) in enumerate(marks):
        hi = marks[k + 1][0] if k + 1 < len(marks) else len(lines)
        txt = _join_block(lines, i + 1, hi, extra_skip=[part_re])
        chapter = ('%s·%s' % (pt, ti)) if pt else ti
        out.append(('CT_DTS_%02d' % (k + 1), '滴天髓', chapter, txt, '%s·L%d' % (chapter, i + 1)))
    return out


# ---------------------------------------------------------------------------
# 穷通宝鉴（造化元钥评注）：先总论（五行总论/论木…），后逐干逐月
# ---------------------------------------------------------------------------
def _qt():
    lines = _lines('穷通宝鉴.txt')
    start = next(i for i, ln in enumerate(lines) if i > 90 and ln.strip() == '韦序')
    marks = []
    for i in range(start, len(lines)):
        s = lines[i].strip()
        if not s or _is_noise(s):
            continue
        # 标题：短、不以句读结尾，且后一行是较长正文行
        if len(s) <= 14 and not re.search(r'[。，,；;！？]', s) and not re.match(r'^\d', s):
            nxt = ''
            for j in range(i + 1, min(i + 12, len(lines))):
                if lines[j].strip() and not _is_noise(lines[j].strip()):
                    nxt = lines[j].strip()
                    break
            if len(nxt) > 20:
                marks.append((i, s))
    out = []
    for k, (i, ti) in enumerate(marks):
        hi = marks[k + 1][0] if k + 1 < len(marks) else len(lines)
        txt = _join_block(lines, i + 1, hi)
        out.append(('CT_QT_%02d' % (k + 1), '穷通宝鉴', ti, txt, '穷通宝鉴·%s·L%d' % (ti, i + 1)))
    return out


# ---------------------------------------------------------------------------
# 神峰通考
# ---------------------------------------------------------------------------
def _sf():
    lines = _lines('神峰通考.txt')
    tail = re.compile(r'(叙|说|类|论|诀|赋|歌|篇|式|法|格|辨|图|序|括)$')
    marks = []
    for i, ln in enumerate(lines):
        s = ln.strip()
        if not s or _is_noise(s):
            continue
        if len(s) <= 20 and tail.search(s) and not s.endswith('。') and not re.match(r'^\d', s):
            marks.append((i, s))
    out = []
    for k, (i, ti) in enumerate(marks):
        hi = marks[k + 1][0] if k + 1 < len(marks) else len(lines)
        txt = _join_block(lines, i + 1, hi)
        out.append(('CT_SF_%02d' % (k + 1), '神峰通考', ti, txt, '神峰通考·%s·L%d' % (ti, i + 1)))
    return out


# ---------------------------------------------------------------------------
# 三命通会：卷 × ○节
# ---------------------------------------------------------------------------
def _smth():
    lines = _lines('三命通会.txt')
    juan_re = re.compile(r'^卷[一二三四五六七八九十]+$')
    jie_re = re.compile(r'^○')
    marks = []  # (idx, juan, jie)
    juan = ''
    for i, ln in enumerate(lines):
        s = ln.strip()
        if not s or _is_noise(s):
            continue
        if juan_re.match(s):
            juan = s
            continue
        if jie_re.match(s):
            marks.append((i, juan, s.lstrip('○').strip()))
    out = []
    for k, (i, ju, ji) in enumerate(marks):
        hi = marks[k + 1][0] if k + 1 < len(marks) else len(lines)
        txt = _join_block(lines, i + 1, hi, extra_skip=[juan_re])
        chapter = ('%s·%s' % (ju, ji)) if ju else ji
        out.append(('CT_SMTH_%03d' % (k + 1), '三命通会', chapter, txt, '%s·L%d' % (chapter, i + 1)))
    return out


# ---------------------------------------------------------------------------
# 渊海子平：以「未知」标记分隔篇题
# ---------------------------------------------------------------------------
def _yh():
    lines = _lines('渊海子平.txt')
    n = len(lines)
    marks = []
    i = 0
    while i < n:
        if lines[i].strip() == '未知':
            j = i + 1
            while j < n and (not lines[j].strip() or lines[j].strip() == '未知'):
                j += 1
            if j < n:
                marks.append((j, lines[j].strip()))
            i = j
        else:
            i += 1
    out = []
    for k, (i, ti) in enumerate(marks):
        hi = marks[k + 1][0] if k + 1 < len(marks) else n
        txt = _join_block(lines, i + 1, hi)
        out.append(('CT_YH_%03d' % (k + 1), '渊海子平', ti, txt, '渊海子平·%s·L%d' % (ti, i + 1)))
    return out


def classic_texts():
    data = _dts() + _qt() + _sf() + _smth() + _yh()
    return [t for t in data if len(t[3]) >= 10]


if __name__ == '__main__':
    import sys
    import io
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')
    data = classic_texts()
    by = {}
    for t in data:
        by.setdefault(t[1], []).append(t)
    for bk, rows in by.items():
        total = sum(len(r[3]) for r in rows)
        empty = sum(1 for r in rows if len(r[3]) < 10)
        print('%-8s 篇=%3d  总字=%6d  空篇=%d' % (bk, len(rows), total, empty))
        for r in rows[:6]:
            print('    %s | %s | %d字 | %s' % (r[0], r[2], len(r[3]), r[3][:40]))
        if len(rows) > 6:
            print('    ...')
            for r in rows[-3:]:
                print('    %s | %s | %d字 | %s' % (r[0], r[2], len(r[3]), r[3][:40]))
    print('TOTAL 行=%d' % len(data))
