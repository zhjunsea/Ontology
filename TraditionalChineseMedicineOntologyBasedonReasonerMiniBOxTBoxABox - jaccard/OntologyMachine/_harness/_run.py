# -*- coding: utf-8 -*-
"""_run.py —— 以 utf-8 捕获 panju_sim 的输出，规避控制台编码问题。
用法: python _run.py <out.txt> [sim args...]
"""
import sys, io, os, runpy

out_path = sys.argv[1]
sim_args = sys.argv[2:]

here = os.path.dirname(os.path.abspath(__file__))
sim = os.path.join(here, 'panju_sim.py')

buf = io.StringIO()
old = sys.stdout
sys.stdout = buf
sys.argv = [sim] + sim_args
try:
    runpy.run_path(sim, run_name='__main__')
finally:
    sys.stdout = old

with open(out_path, 'w', encoding='utf-8') as f:
    f.write(buf.getvalue())
print('WROTE', out_path, len(buf.getvalue()))
