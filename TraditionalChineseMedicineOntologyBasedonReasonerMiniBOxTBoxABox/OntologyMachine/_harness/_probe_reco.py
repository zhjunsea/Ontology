# -*- coding: utf-8 -*-
"""批量诊断探针：观察 八纲/六经/方证 各阶段的实际产出与失败模式。"""
import json
import sys
import urllib.request

API = "http://127.0.0.1:9081/api/diagnosis"


def run(text):
    req = urllib.request.Request(
        API,
        data=json.dumps({"text": text}).encode("utf-8"),
        headers={"Content-Type": "application/json"},
    )
    j = json.loads(urllib.request.urlopen(req, timeout=180).read().decode("utf-8"))
    d = j.get("diagnosis") or {}
    m = j.get("mapping") or {}

    print("=" * 78)
    print("输入:", text)
    print("  映射:", " | ".join(
        "%s(%.2f)" % (x.get("label"), x.get("confidence") or 0) for x in (m.get("detail") or [])))
    if m.get("unmatched"):
        print("  未匹配:", m.get("unmatched"))
    bg = d.get("bagang") or {}
    print("  八纲:", json.dumps(bg, ensure_ascii=False))
    print("  六经:", json.dumps(d.get("liujing"), ensure_ascii=False))
    print("  outcome:", d.get("outcome"), " | 方证:", d.get("fangzhengCn"),
          " | 方剂:", d.get("finalFormulaCn"))
    names = d.get("candidateFangzhengsCn") or []
    scores = d.get("candidateScores") or []
    for i, n in enumerate(names[:10]):
        print("    #%d %s   %s" % (i + 1, n, scores[i] if i < len(scores) else ""))
    pa = d.get("pathA") or {}
    print("  路A 追问数:", len(pa.get("questions") or []))
    print()


if __name__ == "__main__":
    cases = sys.argv[1:] or [
        "发烧怕冷，两边肋骨下面胀痛，还老想吐",
        "咳嗽，口渴，心烦",
        "拉肚子，肚子胀，手脚冰凉",
        "往来寒热，胸胁苦满，默默不欲饮食，心烦喜呕",
        "发热，汗出，恶风，脉缓",
        "下利脓血，里急后重，热利",
    ]
    for c in cases:
        try:
            run(c)
        except Exception as e:
            print("ERR", c, repr(e))
