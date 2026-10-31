# -*- coding: utf-8 -*-
"""精简 MEMORY.md：删除重复/低价值条目行，使其回到 200 行内（保留原行尾）。"""
p = r"C:\Users\ocean\.codeartsdoer\memory\D--work-Ontology-OntologyMachine\MEMORY.md"
with open(p, "r", encoding="utf-8", newline="") as f:
    lines = f.readlines()
drop = {102, 113, 118, 198, 199, 202, 203, 204}
kept = [l for i, l in enumerate(lines, 1) if i not in drop]
with open(p, "w", encoding="utf-8", newline="") as f:
    f.writelines(kept)
print("before", len(lines), "after", len(kept))
