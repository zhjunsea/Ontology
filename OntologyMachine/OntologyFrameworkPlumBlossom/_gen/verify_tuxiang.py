#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""第十三者：图象补录（TU_XIANG_BU_LU）↔ 别本快照 逐条逐字核。

底本诸篇为整页图象（div.kindle-cn-image）、无文字层；第 46 轮遵用户明令
「图像页不用 OCR，可联网找到相关内容」，据别本回补其文字。
本器将 Java `putTx(id, name, desc, text, src)` 之补录文与
`_gen/external_sources/<id>.txt`（网页所采原本之快照）逐条比勘，确保
「文有所本、非自撰」，且图号、条数一一相符。

用法：python _gen/verify_tuxiang.py
退出码：0=全部通过；1=存在偏差。
"""
import io
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
JAVA = os.path.join(ROOT, "src/main/java/com/ocean/ontologyframework/plumblossom/PlumBlossomOntologyJobWorker.java")
SNAP = os.path.join(ROOT, "_gen/external_sources")


def norm(s: str) -> str:
    s = s.replace("&quot;", '"')
    s = s.replace("\u3000", "").replace(" ", "")
    s = re.sub(r"[\r\n\t]", "", s)
    return s


def java_puttx(path: str):
    src = io.open(path, encoding="utf-8").read()
    pat = re.compile(
        r'putTx\(\s*"((?:[^"\\]|\\.)*)"\s*,\s*"((?:[^"\\]|\\.)*)"\s*,\s*'
        r'"((?:[^"\\]|\\.)*)"\s*,\s*"((?:[^"\\]|\\.)*)"\s*,\s*"((?:[^"\\]|\\.)*)"\s*\)',
        re.S)
    out = {}
    for m in pat.finditer(src):
        sid = m.group(1)
        text = m.group(4).replace('\\"', '"').replace("\\\\", "\\")
        out[sid] = (m.group(2), m.group(3), text, m.group(5))
    return out


def snapshot(sid: str):
    p = os.path.join(SNAP, sid + ".txt")
    if not os.path.exists(p):
        return None
    lines = io.open(p, encoding="utf-8").read().split("\n")
    try:
        i = lines.index("# ---- 补录正文 ----")
    except ValueError:
        return None
    return "".join(lines[i + 1:]).strip()


def main() -> int:
    jmap = java_puttx(JAVA)
    snap_ids = sorted(f[:-4] for f in os.listdir(SNAP) if f.endswith(".txt"))
    bad = 0
    print(f"[INFO] Java putTx 图号 {len(jmap)} 个；别本快照 {len(snap_ids)} 个")
    only_java = set(jmap) - set(snap_ids)
    only_snap = set(snap_ids) - set(jmap)
    for sid in sorted(only_java):
        print(f"  !! 仅 Java 有：{sid}")
        bad += 1
    for sid in sorted(only_snap):
        print(f"  !! 仅快照有：{sid}")
        bad += 1
    for sid in sorted(set(jmap) & set(snap_ids)):
        _, _, text, _ = jmap[sid]
        snap = snapshot(sid) or ""
        if norm(text) != norm(snap):
            bad += 1
            print(f"  !! 补录不符：{sid}")
            print(f"       Java: {text[:70]}")
            print(f"       快照: {snap[:70]}")
        else:
            print(f"[ OK ] {sid} ({len(text)}字) 与别本快照逐字相符")
    print("RESULT:", "ALL PASS（图象补录 Java≡别本快照，逐条逐字相等）" if bad == 0 else f"HAS DIFF（{bad}）")
    return 0 if bad == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
