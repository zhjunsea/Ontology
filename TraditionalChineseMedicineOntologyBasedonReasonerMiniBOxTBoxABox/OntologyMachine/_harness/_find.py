# -*- coding: utf-8 -*-
"""按症状子集定位 app_run_v4.log 中的方证打分块并打印。

用法: python _find.py "Fare;Ehan;Wuhan" "Fumai;Jinmai"
      （第1参数=症状fragment，第2参数=脉象fragment；均分号分隔，须全部命中）
"""
import io, os, re, sys

BASE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
LOG = os.path.join(BASE, 'app_run_v4.log')
INST_RE = re.compile(r'症状=\[([^\]]*)\]\s*脉象=\[([^\]]*)\]')
SCORE_RE = re.compile(r'#(\d+)\s+(\w+)\(hits=(\d+)/(\d+), jaccard=([\d.]+), prio=(\d+)\)(.*)')
DONE_RE = re.compile(r'方证完成:\s*(\w+)')


def clean(s):
    s = re.sub(r'^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.\d+ ', '', s)
    s = re.sub(r'\[[^\]]*thread[^\]]*\]\s*', '', s)
    s = s.replace('INFO  c.o.o.TCMOntologyJobWorker - ', '')
    s = s.replace('WARN  c.o.o.TCMOntologyJobWorker - ', 'WARN ')
    return s.rstrip()


def main():
    want_sym = set(x.strip() for x in sys.argv[1].split(';') if x.strip())
    want_pul = set(x.strip() for x in (sys.argv[2] if len(sys.argv) > 2 else '').split(';') if x.strip())
    lines = io.open(LOG, 'r', encoding='utf-8', errors='replace').readlines()
    hits = []
    for i, l in enumerate(lines):
        m = INST_RE.search(l)
        if not m:
            continue
        syms = set(x.strip().replace('_instance', '') for x in m.group(1).split(',') if x.strip())
        puls = set(x.strip().replace('_instance', '') for x in m.group(2).split(',') if x.strip())
        if want_sym.issubset(syms) and want_pul.issubset(puls):
            hits.append((i, syms, puls))
    print('命中 %d 个块' % len(hits))
    for (i, syms, puls) in hits[:3]:
        print('=' * 100)
        print('line %d  sym=%s pul=%s' % (i, sorted(syms), sorted(puls)))
        for k in range(i, min(len(lines), i + 45)):
            s = clean(lines[k])
            if any(t in s for t in ('症状=', 'STAGE_', 'realize', '#1', '#2', '#3', '#4', '#5',
                                    '#6', '#7', '#8', '#9', '#10', 'Top', '方证完成', '六经',
                                    '八纲', '池', '筛选', '兜底', '物化')):
                print('  ', s[:260])


if __name__ == '__main__':
    main()
