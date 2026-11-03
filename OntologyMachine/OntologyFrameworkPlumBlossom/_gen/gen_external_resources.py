#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
生成梅花易数「外层输入」所需的类路径资源表（第 28 轮·引入外部资源）。

产出（写入 src/main/resources/plumblossom/）：
  1) unihan-strokes.tsv  —— 字符 -> 笔画数（Unicode Han Database kTotalStrokes）
  2) pingshui-tone.tsv   —— 字符 -> 平水韵声调（1平 2上 3去 4入）

外部数据源（公开）：
  - Unihan: https://www.unicode.org/Public/UCD/latest/ucd/Unihan.zip
  - 平水韵: https://github.com/charlesix59/chinese_word_rhyme (data/Pingshui_Rhyme.json, MIT)

多音字定序：入(4) > 平(1) > 上(2) > 去(3)。
用法：python _gen/gen_external_resources.py <Unihan_IRGSources.txt> <Pingshui_Rhyme.json>
"""
import json
import sys
import os

OUT = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources', 'plumblossom')


def build_strokes(irg_path):
    m = {}
    with open(irg_path, encoding='utf-8') as f:
        for line in f:
            if 'kTotalStrokes' not in line:
                continue
            p = line.rstrip('\n').split('\t')
            if len(p) < 3 or not p[0].startswith('U+'):
                continue
            m[chr(int(p[0][2:], 16))] = int(p[2].split()[0])
    return m


def build_tones(json_path):
    d = json.load(open(json_path, encoding='utf-8'))

    def tone_of(bu):
        if '入声' in bu:
            return 4
        if '上平' in bu or '下平' in bu:
            return 1
        if '上声' in bu:
            return 2
        if '去声' in bu:
            return 3
        return 0

    rank = {4: 0, 1: 1, 2: 2, 3: 3}
    tm = {}
    for bu, groups in d.items():
        t = tone_of(bu)
        for _, chars in groups.items():
            for c in chars:
                if not isinstance(c, str) or len(c) < 1:
                    continue
                old = tm.get(c)
                if old is None or rank[t] < rank[old]:
                    tm[c] = t
    return tm


def main():
    irg, ps = sys.argv[1], sys.argv[2]
    os.makedirs(OUT, exist_ok=True)
    strokes = build_strokes(irg)
    with open(os.path.join(OUT, 'unihan-strokes.tsv'), 'w', encoding='utf-8', newline='\n') as f:
        f.write('# Unihan kTotalStrokes (Unicode Han Database, unicode.org UCD) - char<TAB>strokes\n')
        for c in sorted(strokes, key=ord):
            f.write('%s\t%d\n' % (c, strokes[c]))
    tones = build_tones(ps)
    with open(os.path.join(OUT, 'pingshui-tone.tsv'), 'w', encoding='utf-8', newline='\n') as f:
        f.write('# 平水韵声调 (上平/下平=平 上声=上 去声=去 入声=入；多音字定序：入>平>上>去) - char<TAB>tone(1平2上3去4入)\n')
        for c in sorted(tones, key=ord):
            f.write('%s\t%d\n' % (c, tones[c]))
    print('strokes=%d tones=%d' % (len(strokes), len(tones)))


if __name__ == '__main__':
    main()
