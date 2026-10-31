# -*- coding: utf-8 -*-
import sys, re, io

PUNCT = set('，。、；：？！「」（）()《》〈〉‘’“”,.;:?![]<>~—…·　 \t')

def short_lines(path, lo=2, hi=16):
    out = []
    with io.open(path, encoding='utf-8') as f:
        for i, line in enumerate(f, 1):
            s = line.strip()
            if not s:
                continue
            if lo <= len(s) <= hi and not (set(s) & PUNCT):
                out.append((i, s))
    return out

if __name__ == '__main__':
    for p in sys.argv[1:]:
        print('===== %s =====' % p)
        for i, s in short_lines(p):
            print('%5d  %s' % (i, s))
