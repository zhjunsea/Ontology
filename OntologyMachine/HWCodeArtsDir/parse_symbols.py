# -*- coding: utf-8 -*-
"""从原著提取文本解析「八卦万物类占」全表 → 结构化类象数据（第 12 步）。

产出 HWCodeArtsDir/pb_symbol_data.json，供生成 ABox 类象个体使用。
出处：原著卷之一·八卦万物类占（orig_00006.txt 第 240~547 行）。
"""
import json
import re
import os

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(HERE, "orig_00006.txt")
OUT = os.path.join(HERE, "pb_symbol_data.json")

TRI = {"乾": "Qian", "兑": "Dui", "离": "Li", "震": "Zhen",
       "巽": "Xun", "坎": "Kan", "艮": "Gen", "坤": "Kun"}

# 栏目 → (本地名, 中文名)
DIM = {
    "天时": ("TianShi", "天时"), "地理": ("DiLi", "地理"), "人物": ("RenWu", "人物"),
    "人事": ("RenShi", "人事"), "身体": ("ShenTi", "身体"), "时序": ("ShiXu", "时序"),
    "动物": ("DongWu", "动物"), "静物": ("JingWu", "静物"), "屋舍": ("WuShe", "屋舍"),
    "家宅": ("JiaZhai", "家宅"), "饮食": ("YinShi", "饮食"), "婚姻": ("HunYin", "婚姻"),
    "生产": ("ShengChan", "生产"), "求名": ("QiuMing", "求名"), "谋望": ("MouWang", "谋望"),
    "交易": ("JiaoYi", "交易"), "求利": ("QiuLi", "求利"), "出行": ("ChuXing", "出行"),
    "谒见": ("YeJian", "谒见"), "竭见": ("YeJian", "谒见"), "疾病": ("JiBing", "疾病"),
    "官讼": ("GuanSong", "官讼"), "坟墓": ("FenMu", "坟墓"), "方道": ("FangDao", "方道"),
    "五色": ("WuSe", "五色"), "姓字": ("XingZi", "姓字"), "数目": ("ShuMu", "数目"),
    "五味": ("WuWei", "五味"),
}

with open(SRC, encoding="utf-8") as f:
    lines = f.read().split("\n")

sec = lines[239:547]  # 「八卦万物类占」标题行 .. 五味行
s = "".join(sec)
s = re.sub(r"\[\d+\]", "", s)        # 半角脚注标记
s = s.replace("［黑］", "黑")        # 坎·五色 内嵌全角括号

parts = re.split(r"([乾坤震巽坎离艮兑])　卦", s)
# parts[0]=前导, 之后成对 [trigram, body]
result = {}
for i in range(1, len(parts), 2):
    ch, body = parts[i], parts[i + 1]
    dims = {}
    for chunk in body.split("［")[1:]:
        if "］：" not in chunk:
            continue
        label, content = chunk.split("］：", 1)
        label = label.strip()
        if label not in DIM:
            print("WARN unknown dim:", label)
            continue
        loc, zh = DIM[label]
        items = [x.strip().strip("。").strip() for x in content.split("、")]
        items = [x for x in items if x]
        dims.setdefault((loc, zh), [])
        dims[(loc, zh)].extend(items)
    result[TRI[ch]] = [{"loc": k[0], "zh": k[1], "items": v} for k, v in dims.items()]

total = sum(len(d["items"]) for tri in result.values() for d in tri)
print("trigrams:", len(result))
for k, v in result.items():
    print(k, "dims=", len(v), "items=", sum(len(d["items"]) for d in v))
print("TOTAL items:", total)

with open(OUT, "w", encoding="utf-8") as f:
    json.dump(result, f, ensure_ascii=False, indent=1)
print("WROTE", OUT)
