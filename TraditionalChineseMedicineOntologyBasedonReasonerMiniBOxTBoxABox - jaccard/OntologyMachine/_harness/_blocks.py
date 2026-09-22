#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""从 app_run_v4.log 中抽取指定方证相关的「阶段2」打分块，用于分析方证误选。

用法: python _blocks.py <fragment> [<fragment> ...]
"""
import sys, io, re, os

LOG = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), 'app_run_v4.log')


def main():
    targets = sys.argv[1:]
    if not targets:
        print(__doc__)
        return
    with io.open(LOG, 'r', encoding='utf-8', errors='replace') as f:
        lines = f.readlines()

    # 找到所有「方证完成: X」行的索引
    done = [(i, l) for i, l in enumerate(lines) if '方证完成:' in l]
    for t in targets:
        print('=' * 100)
        print(f'### {t}')
        hits = 0
        for idx, (i, l) in enumerate(done):
            if f'方证完成: {t} ' not in l:
                continue
            # 该块起点：向上找最近的 [阶段1] 患者症状 行
            start = i
            for j in range(i, max(0, i - 40), -1):
                if '[阶段1] 患者症状=' in lines[j]:
                    start = j
                    break
            # 该块终点：i+2（含打分行）
            end = min(len(lines), i + 2)
            # 只保留含「打分」的块
            block = lines[start:end]
            if not any('方证打分' in b for b in block):
                continue
            hits += 1
            if hits > 3:
                break
            for b in block:
                # 精简时间戳
                b = re.sub(r'^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.\d+ ', '', b)
                b = b.replace('INFO  c.o.o.TCMOntologyJobWorker - ', '')
                b = b.replace('WARN  c.o.o.TCMOntologyJobWorker - ', 'WARN ')
                b = b.replace('c.o.o.MiniReasoningContextManager - ', '')
                sys.stdout.write(b)
            print('-' * 60)
        if hits == 0:
            print('(未找到)')


if __name__ == '__main__':
    main()
