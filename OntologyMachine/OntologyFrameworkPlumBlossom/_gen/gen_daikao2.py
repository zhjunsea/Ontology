# -*- coding: utf-8 -*-
"""第二十五轮「待考全部落地」续：卷之四/卷之五 余篇穷尽归位。
   (A) 五赋全录（替换 round-24 之首段）；
   (B) 卷四余篇（齐景至理论/字画经验/字体诗诀/四季水笔/画有阴阳/笔法筌蹄/奴婢/阴人/隔手/笔画长短/笔画犯煞存目）；
   (C) 卷五余篇（七言作用歌/比例歌/西江月/易理玄微/断扇占/买香占/古人相字/断富贵贫贱要诀/五行相生地支/
       天干地支所属五行/论八卦性情/八卦取象/六十甲子纳音）。
"""
import re, json, os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
L = open(os.path.join(ROOT, 'design', '_epub_dump.txt'), encoding='utf-8').read().split('\n')


def seg(a, b):
    out = []
    for i in range(a, b + 1):
        t = re.sub(r'\[\d+\]', '', L[i - 1]).strip()
        if not t or re.fullmatch(r'[①-⑳]', t):
            continue
        out.append(t)
    return ''.join(out)


def seg_title(a, b):
    """同 seg，但含标题行。"""
    return seg(a, b)

# (A) 五赋全录（替换）
REPLACE = [
    ("赋文", "指迷赋", "卷之四·字画指迷", 4428, 4430),
    ("赋文", "玄黄叙", "卷之四·字画指迷", 4532, 4532),
    ("赋文", "玄黄歌", "卷之四·字画指迷", 4536, 4574),
    ("赋文", "花押赋", "卷之四·字画指迷", 4578, 4586),
    ("赋文", "探玄赋", "卷之四·字画指迷", 4590, 4604),
]

# (B) 卷四余篇
JUAN4 = [
    ("齐景至理论", "卷之四·字画指迷", 4608, 4618),
    ("字画经验",   "卷之四·字画指迷", 4622, 4670),
    ("字体诗诀",   "卷之四·字画指迷", 4674, 4814),
    ("四季水笔",   "卷之四·字画指迷", 4816, 4826),
    ("画有阴阳",   "卷之四·字画指迷", 4828, 4830),
    ("笔法筌蹄",   "卷之四·字画指迷", 4884, 4886),
    ("奴婢",       "卷之四·字画指迷", 4938, 4942),
    ("阴人",       "卷之四·字画指迷", 4944, 4948),
    ("隔手",       "卷之四·字画指迷", 4950, 4954),
    ("笔画长短",   "卷之四·字画指迷", 5020, 5026),
]

# (C) 卷五余篇（按标题行切片）
JUAN5 = [
    ("七言作用歌",       "卷之五·拆字杂编", 6653, 6888),
    ("比例歌",           "卷之五·拆字杂编", 6889, 6946),
    ("西江月",           "卷之五·拆字杂编", 6947, 6950),
    ("易理玄微",         "卷之五·拆字杂编", 6951, 6954),
    ("断扇占",           "卷之五·拆字杂编", 6955, 6964),
    ("买香占",           "卷之五·拆字杂编", 6965, 6988),
    ("古人相字",         "卷之五·拆字杂编", 6989, 7013),
    ("断富贵贫贱要诀",   "卷之五·拆字杂编", 7015, 7035),
    ("五行相生地支",     "卷之五·拆字杂编", 7043, 7074),
    ("天干地支所属五行", "卷之五·拆字杂编", 7075, 7100),
    ("论八卦性情",       "卷之五·拆字杂编", 7101, 7132),
    ("八卦取象",         "卷之五·拆字杂编", 7133, 7162),
    ("六十甲子纳音",     "卷之五·拆字杂编", 7169, 7199),
]

STUBS5 = [
    ("五行四时旺相休囚表", "卷之五·拆字杂编", "（本版正文为图象，文字阙如；存目。）"),
]

# 笔画犯煞：原著有目而无诀（漫漶）——存目照录标签
BIFA_FAN_SHA_LABELS = ["凤鳞", "断伏", "活法", "用煞", "连图带", "曰隔伏", "丁欹伏", "冲伏", "悬针", "流金",
                       "活金", "乙伏曲", "曲伏", "口死金", "活火", "死火", "腾蛇", "死土", "蛇土", "力隔伏"]

replace_rows = [(c, k, seg(a, b), s) for c, k, s, a, b in REPLACE]
juan4_rows = [(c, "总诀", seg(a, b), s) for c, s, a, b in JUAN4]
juan5_rows = [(c, "总诀", seg(a, b), s) for c, s, a, b in JUAN5]
bifa = ("笔画犯煞——本版存目（诸式多为图象，诀文漫漶）：" + "、".join(BIFA_FAN_SHA_LABELS)
        + "。（共二十式；原刻有目无诀，照目存录，不臆补。）")
extra_rows = juan4_rows + [("笔画犯煞", "总诀", bifa, "卷之四·字画指迷")] + juan5_rows

# 名称：卷四余篇续 XiangZi_37_xx；卷五余篇续 XiangZi_51_xx？（避与已有 18 类序号冲突）
# 采用独立段：卷四项 XiangZi_40_xx 起；笔画犯煞 XiangZi_41_01；卷五项 XiangZi_50_xx 起。
out_rows = []
ci4 = 40
for c, s, a, b in JUAN4:
    out_rows.append(("XiangZi_%02d_01" % ci4, c, "总诀", seg(a, b), s))
    ci4 += 1
out_rows.append(("XiangZi_50_01", "笔画犯煞", "总诀", bifa, "卷之四·字画指迷"))
ci5 = 51
for c, s, a, b in JUAN5:
    out_rows.append(("XiangZi_%02d_01" % ci5, c, "总诀", seg(a, b), s))
    ci5 += 1
for c, s, txt in STUBS5:
    out_rows.append(("XiangZi_%02d_01" % ci5, c, "总诀", txt, s))
    ci5 += 1

json.dump({"replace": replace_rows, "append": out_rows,
           "bifa": bifa},
          open(os.path.join(ROOT, '_gen', 'daikao2.json'), 'w', encoding='utf-8'),
          ensure_ascii=False, indent=1)
print("replace:", len(replace_rows), "append:", len(out_rows))
for n, c, k, txt, s in out_rows:
    print(n, c, len(txt))
