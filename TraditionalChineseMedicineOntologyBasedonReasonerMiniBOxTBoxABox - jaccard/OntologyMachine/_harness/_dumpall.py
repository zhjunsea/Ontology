#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""导出 app_run_v4.log 中每个方证块的摘要：症状签名 | realize数 | Top1 | Top3。

用法: python _dumpall.py > _allblocks.txt
"""
import io, os, re, sys

BASE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
LOG = os.path.join(BASE, 'app_run_v4.log')
INST_RE = re.compile(r'\[([A-Za-z0-9_, ]*_instance[A-Za-z0-9_, ]*)\]')
SCORE_RE = re.compile(r'#(\d+)\s+(\w+)\(hits=(\d+)/(\d+), jaccard=([\d.]+), prio=(\d+)\)(.*)')
DONE_RE = re.compile(r':\s*(\w+)\s+\S*=(\d+)\s+Top\d+=\[([^\]]*)\]')


def main():
    lines = io.open(LOG, 'r', encoding='utf-8', errors='replace').readlines()
    fz_idx = [i for i, l in enumerate(lines) if 'STAGE_FZ' in l and 'Patient_' in l]
    out = []
    for i in fz_idx:
        # signature
        sig = None
        for j in range(i, max(0, i - 60), -1):
            m = INST_RE.search(lines[j])
            if m:
                items = [x.strip().replace('_instance', '') for x in m.group(1).split(',') if x.strip()]
                if items:
                    sig = items
                    break
        # realize count line
        rc = None
        for j in range(i, min(len(lines), i + 5)):
            if 'realize' in lines[j] and ('无匹配' in lines[j] or '命中' in lines[j]):
                mm = re.search(r'(\d+)\s*个', lines[j])
                rc = 0 if '无匹配' in lines[j] else (mm.group(1) if mm else '?')
                break
        # scores
        scores = []
        for j in range(i, min(len(lines), i + 40)):
            m = SCORE_RE.search(lines[j])
            if m:
                scores.append('%s(%s/%s,j=%s,p%s)%s' % (
                    m.group(2), m.group(3), m.group(4), m.group(5), m.group(6),
                    'R' if 'realize' in m.group(7) else ''))
            if DONE_RE.search(lines[j]):
                break
        top1 = None
        for j in range(i, min(len(lines), i + 40)):
            m = DONE_RE.search(lines[j])
            if m:
                top1 = m.group(1)
                break
        out.append('%-70s | rc=%s | T1=%-38s | %s' % (
            ';'.join(sorted(sig)) if sig else '?', rc, top1, ' '.join(scores[:4])))
    sys.stdout.write('\n'.join(out) + '\n')


if __name__ == '__main__':
    main()
