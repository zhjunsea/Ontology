# -*- coding: utf-8 -*-
"""指令 A 端到端校验：index.html 的追问清单必须只问四诊症状，不得问八纲/六经抽象类。

做法：对若干症状输入调用 POST /api/diagnosis，抽取 diagnosis.pathA.questions[].ask，
逐条扫描是否含八纲/六经抽象词。命中即 FAIL。
"""
import json
import sys
import urllib.request

BASE = "http://localhost:9081"
INPUTS = ["胸胁苦满", "腹满", "口苦", "头痛", "下利", "咳嗽", "心烦"]

# 八纲 / 六经 抽象词（追问中不得出现）
# 注意：只用「带证字/病名的具体抽象词」，不要用「表里/寒热/虚实/阴阳」这类泛词——
# 「往来寒热」「恶寒发热」是四诊症状（《伤寒论》96 条），含「寒热」二字但并非八纲抽象类。
ABSTRACT = [
    "半表半里", "半表", "半里", "阴证", "阳证", "表证", "里证", "寒证", "热证",
    "虚证", "实证",
    "太阳病", "阳明病", "少阳病", "太阴病", "少阴病", "厥阴病",
    "太阳", "阳明", "少阳", "太阴", "少阴", "厥阴",
    "六经", "合病", "并病", "八纲",
]


def post_diagnosis(text):
    req = urllib.request.Request(
        BASE + "/api/diagnosis",
        data=json.dumps({"text": text}, ensure_ascii=False).encode("utf-8"),
        headers={"Content-Type": "application/json; charset=utf-8"},
        method="POST",
    )
    with urllib.request.urlopen(req, timeout=120) as r:
        return json.loads(r.read().decode("utf-8"))


def main():
    total = 0
    hits = []
    for text in INPUTS:
        try:
            resp = post_diagnosis(text)
        except Exception as e:
            print(f"[{text}] 请求失败: {e}")
            continue
        diag = resp.get("diagnosis") or {}
        pathA = diag.get("pathA") or {}
        questions = pathA.get("questions") or []
        asks = [q.get("ask", "") for q in questions]
        total += len(asks)
        bad = [a for a in asks if any(w in a for w in ABSTRACT)]
        for b in bad:
            hits.append((text, b))
        print(f"[{text}] 追问 {len(asks)} 条, 抽象词命中 {len(bad)} 条")
        for a in asks:
            print(f"    - {a}")
    print("=" * 60)
    print(f"合计追问 {total} 条, 抽象词命中 {len(hits)} 条")
    for t, b in hits:
        print(f"  !! [{t}] {b}")
    print("RESULT:", "PASS" if not hits else "FAIL")
    return 0 if not hits else 1


if __name__ == "__main__":
    sys.exit(main())
