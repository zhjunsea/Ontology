import json, sys, time, urllib.request, urllib.error

APP = "http://localhost:9089"
KEY = "3zkjHUR6H5ylPrwTRVOa82YuhuHzfTJ4eT_0gDF4tzQoodafEGyC9-gfkWu1rzZ_TDc_xaF5FNj0uZeQs68RMg"
LLM = "https://api.modelarts-maas.com/openai/v1/chat/completions"
MODEL = "DeepSeek-V4.1-Flash"
cases_path = r"D:\work\Ontology\OntologyMachine\OntologyFrameworkZiWeiDouShu\src\test\resources\ziweidoushu\hunt_cases.json"
out_json = r"D:\work\Ontology\OntologyMachine\OntologyFrameworkZiWeiDouShu\logs\_llm_inspect.json"
N = int(sys.argv[1]) if len(sys.argv) > 1 else 10

def post(url, obj):
    data = json.dumps(obj).encode("utf-8")
    req = urllib.request.Request(url, data=data, headers={"Content-Type": "application/json"}, method="POST")
    with urllib.request.urlopen(req, timeout=30) as r:
        return json.loads(r.read().decode("utf-8"))

def get(url):
    with urllib.request.urlopen(url, timeout=30) as r:
        return json.loads(r.read().decode("utf-8"))

def run_chart(c, idx):
    body = {
        "birth": c["birth"], "gender": c.get("gender", "男"),
        "subjectType": c.get("subjectType", "成人男"), "depth": "本命", "mode": "直接排盘",
        "calendar": "公历", "birthPlace": "北京 116.4E", "granularity": "年",
    }
    r = post(APP + "/api/ziweidoushu/chart", body)
    key = r.get("instanceKey")
    if key is None:
        return None
    for _ in range(80):
        s = get(APP + "/api/ziweidoushu/chart/" + str(key))
        if str(s.get("status", "")).upper() == "COMPLETED":
            return s
        time.sleep(1.0)
    return s

def ask_llm(result):
    payload = {
        "model": MODEL, "temperature": 0,
        "messages": [{"role": "user", "content":
            "你是《紫微斗數全書》校验师。依据《紫微斗數全書》（卷一卷二卷三）与《新刻纂集紫微斗数捷览》，"
            "判断以下紫微斗数排盘输出是否【合理、有据、无自相矛盾/无编造】。只看是否合乎经典义理与命理常识。\n"
            "命盘：" + str(result.get("chartBase", ""))[:200] + "\n"
            "命格：" + str(result.get("patternGrade", ""))[:120] + "\n"
            "格局断语：" + str(result.get("patternResult", ""))[:700] + "\n"
            "告警：" + str(result.get("alertTriggered")) + "；空宫：" + str(result.get("emptyPalaceResult", ""))[:120] + "\n"
            "仅输出JSON（一行）：{\"verdict\":\"合理|存疑|不合理\",\"basis\":\"依据经典简述\",\"issues\":\"如无留空\"}"
        }]
    }
    data = json.dumps(payload).encode("utf-8")
    req = urllib.request.Request(LLM, data=data, headers={"Content-Type": "application/json", "Authorization": "Bearer " + KEY}, method="POST")
    with urllib.request.urlopen(req, timeout=90) as r:
        j = json.loads(r.read().decode("utf-8"))
    return j["choices"][0]["message"]["content"]

cases = json.load(open(cases_path, "r", encoding="utf-8"))[:N]
report = []
for i, c in enumerate(cases):
    try:
        res = run_chart(c, i)
        if res is None:
            report.append({"id": c.get("id"), "birth": c.get("birth"), "error": "no instanceKey"})
            continue
        verdict = ask_llm(res)
        report.append({"id": c.get("id"), "birth": c.get("birth"), "status": res.get("status"),
                       "patternGrade": str(res.get("patternGrade", ""))[:80],
                       "llm": verdict})
        print("[%d/%d] %s -> %s" % (i + 1, len(cases), c.get("birth"), verdict[:80].replace("\n", " ")))
    except Exception as e:
        report.append({"id": c.get("id"), "birth": c.get("birth"), "error": str(e)})
        print("[%d] %s ERR %s" % (i, c.get("birth"), e))
json.dump(report, open(out_json, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
print("DONE", len(report), "->", out_json)
