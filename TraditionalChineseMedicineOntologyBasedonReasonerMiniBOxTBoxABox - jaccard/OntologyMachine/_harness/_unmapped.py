# -*- coding: utf-8 -*-
"""列出「无法映射到六经」的用例（测试数据问题），以及解析噪声。"""
import os, sys, io
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import panju_sim as S

buf = io.StringIO(); old = sys.stdout; sys.stdout = buf
try:
    cases = S.load_cases()
    print(f'总解析用例 = {len(cases)}')
    unmapped = [c for c in cases if not S.resolve_anchor(c['lj'])]
    print(f'无法映射六经 = {len(unmapped)}')
    for c in unmapped:
        print(f"  {c['file']:<32} name={c['name']!r:<24} lj={c['lj']!r}")
    # 解析噪声：name 像形参
    noise = [c for c in cases if c['name'] in ('String name', 'name')]
    print(f'\n疑似「方法声明」误匹配 = {len(noise)}')
    for c in noise:
        print(f"  {c['file']:<32} name={c['name']!r} lj={c['lj']!r}")
finally:
    sys.stdout = old
open(os.path.join(HERE, 'r_unmapped.txt'), 'w', encoding='utf-8').write(buf.getvalue())
print('WROTE')
