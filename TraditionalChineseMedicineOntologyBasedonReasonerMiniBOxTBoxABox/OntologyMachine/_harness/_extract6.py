# -*- coding: utf-8 -*-
import re
raw = open("_suite6.clean.log", "rb").read()
enc = "gb18030"
s = raw.decode(enc, errors="replace")
blocks = re.split(r'\n===== ', s)
want = ["半夏泻心汤证", "生姜泻心汤证", "甘草泻心汤证", "柴胡白虎汤证", "白虎汤证", "四逆散证", "猪苓汤证"]
out = ["enc=%s blocks=%d" % (enc, len(blocks))]
for b in blocks:
    title = b.split("\n", 1)[0].strip()
    if any(title.startswith(w) for w in want):
        out.append("===== " + title)
        for L in b.split("\n"):
            if L.startswith(("八纲：", "六经：", "方证：", "候选方证打分：", "[失败]", "[通过]")):
                out.append("   " + L[:600])
        out.append("")
open("_harness/_extract6.out", "w", encoding="utf-8").write("\n".join(out))
