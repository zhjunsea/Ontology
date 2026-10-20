# -*- coding: utf-8 -*-
"""第 12 步（闸门⑪）· 生成全集 data.sql。

范围（邵康节 2026-10-20 确认）：历法全集（天干 10 / 地支 12 / 纳音 30 / 六十甲子 60 / 长生 4）
+ 周易文本全集（卦序 64 / 卦辞 64 / 爻辞 384）+ 运行态占断样本 6。
名称口径（邵康节 2026-10-20 确认）：卦序 Seq_01..64；卦辞 Dictum_Hex_01..64；爻辞 LineDictum_01_1..64_6。
"""
import os
from pb_hexagram_data import HEXAGRAMS

BASE = r"D:\work\Ontology\OntologyMachine\OntologyFrameworkPlumBlossom\ontology\database"
OUT = os.path.join(BASE, "data.sql")

STEM_CHAR = "甲乙丙丁戊己庚辛壬癸"
STEM_PY = ["Jia", "Yi", "Bing", "Ding", "Wu", "Ji", "Geng", "Xin", "Ren", "Gui"]
BRANCH_CHAR = "子丑寅卯辰巳午未申酉戌亥"
BRANCH_PY = ["Zi", "Chou", "Yin", "Mao", "Chen", "Si", "Wu", "Wei", "Shen", "You", "Xu", "Hai"]

NAYIN = [
    ("HaiZhongJin", "海中金", "Jin"),
    ("LuZhongHuo", "炉中火", "Huo"),
    ("DaLinMu", "大林木", "Mu"),
    ("LuPangTu", "路旁土", "Tu"),
    ("JianFengJin", "剑锋金", "Jin"),
    ("ShanTouHuo", "山头火", "Huo"),
    ("JianXiaShui", "涧下水", "Shui"),
    ("ChengTouTu", "城头土", "Tu"),
    ("BaiLaJin", "白蜡金", "Jin"),
    ("YangLiuMu", "杨柳木", "Mu"),
    ("QuanZhongShui", "泉中水", "Shui"),
    ("WuShangTu", "屋上土", "Tu"),
    ("PiLiHuo", "霹雳火", "Huo"),
    ("SongBaiMu", "松柏木", "Mu"),
    ("ChangLiuShui", "长流水", "Shui"),
    ("ShaZhongJin", "沙中金", "Jin"),
    ("ShanXiaHuo", "山下火", "Huo"),
    ("PingDiMu", "平地木", "Mu"),
    ("BiShangTu", "壁上土", "Tu"),
    ("JinBoJin", "金箔金", "Jin"),
    ("FuDengHuo", "覆灯火", "Huo"),
    ("TianHeShui", "天河水", "Shui"),
    ("DaYiTu", "大驿土", "Tu"),
    ("ChaiChuanJin", "钗钏金", "Jin"),
    ("SangZheMu", "桑柘木", "Mu"),
    ("DaXiShui", "大溪水", "Shui"),
    ("ShaZhongTu", "沙中土", "Tu"),
    ("TianShangHuo", "天上火", "Huo"),
    ("ShiLiuMu", "石榴木", "Mu"),
    ("DaHaiShui", "大海水", "Shui"),
]
assert len(NAYIN) == 30

STEMS = [("Stem_" + STEM_PY[i], STEM_CHAR[i], ["Mu", "Mu", "Huo", "Huo", "Tu", "Tu", "Jin", "Jin", "Shui", "Shui"][i], STEM_CHAR[i]) for i in range(10)]
BRANCHES = [
    ("Branch_Zi", "子", "鼠", 1, "Shui"),
    ("Branch_Chou", "丑", "牛", 2, "Tu"),
    ("Branch_Yin", "寅", "虎", 3, "Mu"),
    ("Branch_Mao", "卯", "兔", 4, "Mu"),
    ("Branch_Chen", "辰", "龙", 5, "Tu"),
    ("Branch_Si", "巳", "蛇", 6, "Huo"),
    ("Branch_Wu", "午", "马", 7, "Huo"),
    ("Branch_Wei", "未", "羊", 8, "Tu"),
    ("Branch_Shen", "申", "猴", 9, "Jin"),
    ("Branch_You", "酉", "鸡", 10, "Jin"),
    ("Branch_Xu", "戌", "狗", 11, "Tu"),
    ("Branch_Hai", "亥", "猪", 12, "Shui"),
]
LIFE = [
    ("Life_Huo", "火生于寅"),
    ("Life_Jin", "金生于巳"),
    ("Life_Mu", "木生在亥"),
    ("Life_ShuiTu", "水土长生居申"),
]
PB = [
    ("PB_QianBodyUse_Bihe",    "先天", "YearMonthDayHourMethod", "Cat_TianShi", "Ji",      0, 1, "近", "体用比和", "乾为天卦体用比和，百事顺遂，所谋可成。"),
    ("PB_DuiYongShengTi",      "先天", "YearMonthDayHourMethod", "Cat_QiuCai",  "DaJi",    1, 1, "近", "用生体",   "兑为泽卦用生体，进益之喜，财帛有得。"),
    ("PB_ZhenTiKeYong",        "先天", "YearMonthDayHourMethod", "Cat_ChuXing", "Ji",      0, 1, "中", "体克用",   "震为雷卦体克用，诸事小吉，出行顺利。"),
    ("PB_LiTiShengYong",       "先天", "YearMonthDayHourMethod", "Cat_WuShe",   "Ping",    0, 1, "中", "体生用",   "离为火卦体生用，有耗失之患，宜守不宜进。"),
    ("PB_KanYongKeTi",         "先天", "YearMonthDayHourMethod", "Cat_JiBing",  "DaXiong", 1, 1, "远", "用克体",   "坎为水卦用克体，诸事凶险，病讼当防。"),
    ("PB_YiCuoJin_ShangShang", "先天", "YicuoJinMethod",         "Cat_JiaoYi",  "DaJi",    1, 0, "近", "一撮金上上", "一撮金得上上之数，交关大利。"),
]


def sq(x):
    return "'" + str(x).replace("\\", "\\\\").replace("'", "''") + "'"


s = []
s.append("-- 清空旧数据（便于本文件重复执行）")
for t in ["divination_result", "yijing_line_dictum", "yijing_hexagram_dictum", "yijing_hexagram_sequence",
          "calendar_sexagenary_cycle", "calendar_nayin", "calendar_life_stage",
          "calendar_earthly_branch", "calendar_heavenly_stem"]:
    s.append("DELETE FROM %s;" % t)
s.append("")
s.append("-- ============================================================================")
s.append("-- 1. 十天干（10） / 十二地支（12） / 纳音（30） / 六十甲子（60） / 五行长生（4）")
s.append("--    历法全集，第 12 步（闸门⑪）扩充；数据来源：原著卷之一·十天干/十二地支；")
s.append("--    卷之五·天干地支所属五行 / 六十甲子纳音歌 / 五行相生地支。")
s.append("-- ============================================================================")

s.append("INSERT INTO calendar_heavenly_stem (name, stem_name, five_phase, label) VALUES")
s.append(",\n".join("(%s, %s, %s, %s)" % (sq(n), sq(c), sq(p), sq(c)) for (n, c, p, _l) in STEMS) + ";")
s.append("")
s.append("INSERT INTO calendar_earthly_branch (name, branch_name, branch_animal, zhi_order, five_phase, label) VALUES")
s.append(",\n".join("(%s, %s, %s, %d, %s, %s)" % (sq(n), sq(c), sq(a), o, sq(p), sq(c)) for (n, c, a, o, p) in BRANCHES) + ";")
s.append("")
s.append("INSERT INTO calendar_nayin (name, nayin_name, five_phase, label) VALUES")
s.append(",\n".join("(%s, %s, %s, %s)" % (sq("NaYin_" + k), sq(ch), sq(ph), sq(ch)) for (k, ch, ph) in NAYIN) + ";")
s.append("")
s.append("INSERT INTO calendar_sexagenary_cycle (name, ganzhi, nayin_name, label) VALUES")
rows = []
for i in range(60):
    gz = STEM_CHAR[i % 10] + BRANCH_CHAR[i % 12]
    nm = "Cycle_" + STEM_PY[i % 10] + BRANCH_PY[i % 12]
    ny = "NaYin_" + NAYIN[i // 2][0]
    rows.append("(%s, %s, %s, %s)" % (sq(nm), sq(gz), sq(ny), sq(gz)))
s.append(",\n".join(rows) + ";")
s.append("")
s.append("INSERT INTO calendar_life_stage (name, life_stage_text, label) VALUES")
s.append(",\n".join("(%s, %s, %s)" % (sq(n), sq(t), sq(t)) for (n, t) in LIFE) + ";")
s.append("")

s.append("-- ============================================================================")
s.append("-- 2. 周易文本：六十四卦序（64） / 卦辞（64） / 爻辞（384）")
s.append("--    全集，第 12 步（闸门⑪）扩充；卦辞/爻辞为《周易》经传（外部经典引入，")
s.append("--    原著未全录，见《本体核心设计》§9-6）；卦名/卦序出自卷之五·六十四卦次第歌。")
s.append("-- ============================================================================")
s.append("INSERT INTO yijing_hexagram_sequence (name, sequence_no, hexagram_name, label) VALUES")
s.append(",\n".join("(%s, %d, %s, %s)" % (sq("Seq_%02d" % sq_no), sq_no, sq(name), sq("%d.%s" % (sq_no, name)))
                    for (sq_no, name, _u, _l, _g, _ls) in HEXAGRAMS) + ";")
s.append("")
s.append("INSERT INTO yijing_hexagram_dictum (name, dictum_text, label) VALUES")
s.append(",\n".join("(%s, %s, %s)" % (sq("Dictum_Hex_%02d" % sq_no), sq(g), sq(name + "卦辞"))
                    for (sq_no, name, _u, _l, g, _ls) in HEXAGRAMS) + ";")
s.append("")
s.append("INSERT INTO yijing_line_dictum (name, position, dictum_text, label) VALUES")
rows = []
for (sq_no, name, _u, _l, _g, lines) in HEXAGRAMS:
    for p, txt in enumerate(lines, start=1):
        rows.append("(%s, %d, %s, %s)" % (sq("LineDictum_%02d_%d" % (sq_no, p)), p, sq(txt), sq("%s爻辞%d" % (name, p))))
s.append(",\n".join(rows) + ";")
s.append("")

s.append("-- ============================================================================")
s.append("-- 3. 运行态占断结果（典型样本；jud:PB_*）")
s.append("--    method 存 div:DivinationMethod 子类本地名（本体中起卦法建模为类，非个体）：")
s.append("--      年月日时起例 → YearMonthDayHourMethod；一撮金法 → YicuoJinMethod（均属「先天·以数起卦」体系）。")
s.append("-- ============================================================================")
s.append("INSERT INTO divination_result (pb_name, divination_system, method, subject_category, auspiciousness, is_major_result, uses_zhouyi_dictum, response_period, process_flag, description, created_at) VALUES")
s.append(",\n".join("(%s, %s, %s, %s, %s, %d, %d, %s, %s, %s, %s)"
                    % (sq(a), sq(b), sq(c), sq(d), sq(e), f_, g_, sq(h), sq(i_), sq(j), sq("2026-10-20"))
                    for (a, b, c, d, e, f_, g_, h, i_, j) in PB) + ";")
s.append("")

with open(OUT, "w", encoding="utf-8", newline="\n") as f:
    f.write("\n".join(s) + "\n")

print("WROTE", OUT)
print("stems=%d branches=%d nayin=%d cycles=%d life=%d seq=%d dictum=%d line=%d pb=%d"
      % (len(STEMS), len(BRANCHES), len(NAYIN), 60, len(LIFE), len(HEXAGRAMS), len(HEXAGRAMS),
         sum(len(h[5]) for h in HEXAGRAMS), len(PB)))
