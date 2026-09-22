#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""按「患者症状签名」定位 app_run_v4.log 中的方证打分块（纯 ASCII 锚点，规避编码问题）。

用法:
  python _case.py <Sym1;Sym2;...> [<Sym1;Sym2;...> ...]

每个参数是一组症状 fragment（分号分隔，可含脉象）。脚本会找到症状集合完全一致的
患者块，打印其 [阶段2] 打分（realize 命中 / 仅候选）与最终方证。
"""
import sys, io, os, re

BASE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
LOG = os.path.join(BASE, 'app_run_v4.log')

INST_RE = re.compile(r'\[([A-Za-z0-9_, ]*_instance[A-Za-z0-9_, ]*)\]')
SCORE_RE = re.compile(r'#(\d+)\s+(\w+)\(hits=(\d+)/(\d+), jaccard=([\d.]+), prio=(\d+)\)(.*)')
PAT_RE = re.compile(r'Patient_(\d+)')


def load():
    with io.open(LOG, 'r', encoding='utf-8', errors='replace') as f:
        return f.readlines()


def signature(lines, upto):
    """向上找最近的症状映射行，返回症状 fragment 集合。"""
    for j in range(upto, max(0, upto - 60), -1):
        m = INST_RE.search(lines[j])
        if m:
            items = [x.strip() for x in m.group(1).split(',') if x.strip()]
            if items:
                return set(x.replace('_instance', '') for x in items)
    return None


def main():
    if len(sys.argv) < 2:
        print(__doc__)
        return
    lines = load()
    # 所有 STAGE_FZ realize 行
    fz_idx = [i for i, l in enumerate(lines) if 'STAGE_FZ' in l and 'Patient_' in l]
    # 所有 方证完成 行（ASCII 锚：候选Top）
    done_idx = [i for i, l in enumerate(lines) if re.search(r'Top\d+=\[', l)]

    for arg in sys.argv[1:]:
        want = set(x.strip() for x in arg.split(';') if x.strip())
        print('=' * 100)
        print('### 目标症状: %s' % sorted(want))
        found = 0
        for i in fz_idx:
            sig = signature(lines, i)
            if sig is None or sig != want:
                continue
            found += 1
            if found > 2:
                break
            # 打印从症状映射行到该块结束
            start = i
            for j in range(i, max(0, i - 60), -1):
                if INST_RE.search(lines[j]):
                    start = j
                    break
            end = min(len(lines), i + 30)
            for k in range(start, end):
                l = lines[k]
                if SCORE_RE.search(l) or 'STAGE_FZ' in l or INST_RE.search(l) \
                        or re.search(r'Top\d+=\[', l) or 'realize' in l.lower():
                    l = re.sub(r'^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.\d+ ', '', l)
                    l = re.sub(r'\[[^\]]*thread[^\]]*\]\s*', '', l)
                    l = l.replace('INFO  c.o.o.TCMOntologyJobWorker - ', '')
                    l = l.replace('WARN  c.o.o.TCMOntologyJobWorker - ', 'WARN ')
                    l = l.replace('c.o.o.MiniReasoningContextManager - ', '')
                    l = re.sub(r'\[MiniContext:[^\]]*\] ', '', l)
                    sys.stdout.write(l)
            print('-' * 60)
        if found == 0:
            print('(未找到匹配症状签名)')


if __name__ == '__main__':
    main()
