import sys

p = r"D:\work\Ontology\TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox\OntologyMachine\app_run_fuzheng.log"
lines = open(p, encoding='utf-8', errors='replace').read().split('\n')

# find score blocks whose top-1 (or any) contains a given fragment AND whose diag is 方证未定
frag = sys.argv[1] if len(sys.argv) > 1 else None
ctx = int(sys.argv[2]) if len(sys.argv) > 2 else 22

for i, ln in enumerate(lines):
    if '方证打分:' in ln and frag and frag in ln:
        lo = max(0, i - ctx)
        hi = min(len(lines), i + ctx)
        print("#" * 90)
        for j in range(lo, hi):
            s = lines[j]
            if any(k in s for k in ['方证打分:', '诊断解释:', '六经 [', 'realize 命中', '池内症状命中',
                                     '阶段2', '八纲', '患者推理', 'realize 无匹配', '未定', '兜底']):
                print(f"{j}: {s[:400]}")
