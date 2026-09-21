import sys

p = r"D:\work\Ontology\TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox\OntologyMachine\app_run_fuzheng.log"
lines = open(p, encoding='utf-8', errors='replace').read().split('\n')

frag = sys.argv[1]
ctx = int(sys.argv[2]) if len(sys.argv) > 2 else 40
KEYS = ['八纲来源', '八纲完成', '六经来源', '六经 [', 'realize 命中', 'realize 无匹配',
        '方证打分:', '诊断解释:', '池内症状命中', '八纲判据']

for i, ln in enumerate(lines):
    if '方证打分:' in ln and frag in ln:
        lo, hi = max(0, i - ctx), min(len(lines), i + 8)
        print("#" * 90)
        for j in range(lo, hi):
            s = lines[j]
            if any(k in s for k in KEYS):
                print(f"{j}: {s[:340]}")
