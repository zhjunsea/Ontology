# -*- coding: utf-8 -*-
"""
紫微斗数（ZiWeiDouShu）第 12 步（闸门⑪）：把 ontology/database/data.sql 的可枚举表
由「典型/代表性」补全至《紫微斗數全書》全集（DB 侧）。

范围（不新增表、不改 DDL 结构）：
  - sexagenary_cycle  6  -> 60   （六十甲子全量；卷二·六十花甲子納音歌）
  - nayin             6  -> 30   （纳音全量；卷二·六十花甲子納音歌）
  - star_layout       2  -> 150  （紫微定位表 5局×30日；卷二·安紫微定位棋盘图，值取本工程已校订 ZIWEI_TABLE）
  - brightness_map    0  -> 252  （星辰庙陷 星×地支宫 全表；卷二·星辰庙陷「庙/旺/得地/利益/平和/不得地/落陷」）
  - deity_rule        10 -> 42   （流禄10 + 流羊10 + 流陀10 + 博士十二神12；卷二·安禄存/安擎羊陀罗/安十二宫太岁杀禄诀）
不变：ganzhi_stem 10 / ganzhi_branch 12 / bureau_stage 17 / sihua_map 40 / palace_stem_map 5 /
      true_solar_time 1 / leap_month 1 / case_record 0 / alert_flag 0（运行态）。

数据来源标注逐条落 source_note / 注释；无明确出处者标【待考】。
输出：直接覆盖 ../OntologyMachine/OntologyFrameworkZiWeiDouShu/ontology/database/data.sql
"""
import io, os

OUT = os.path.join(os.path.dirname(__file__), "..", "OntologyFrameworkZiWeiDouShu", "ontology", "database", "data.sql")

# ---- 十天干 / 十二地支 ----
STEMS = [("甲", "Jia"), ("乙", "Yi"), ("丙", "Bing"), ("丁", "Ding"), ("戊", "Wu"),
         ("己", "Ji"), ("庚", "Geng"), ("辛", "Xin"), ("壬", "Ren"), ("癸", "Gui")]
BRANCHES = [("子", "Zi"), ("丑", "Chou"), ("寅", "Yin"), ("卯", "Mao"), ("辰", "Chen"), ("巳", "Si"),
            ("午", "Wu"), ("未", "Wei"), ("申", "Shen"), ("酉", "You"), ("戌", "Xu"), ("亥", "Hai")]
ZHI = [b[0] for b in BRANCHES]

STEM_YINYANG = ["Yang", "Yin"] * 5
STEM_PHASE = ["Mu", "Mu", "Huo", "Huo", "Tu", "Tu", "Jin", "Jin", "Shui", "Shui"]
BRANCH_ANIMAL = ["鼠", "牛", "虎", "兔", "龙", "蛇", "马", "羊", "猴", "鸡", "狗", "猪"]
BRANCH_PHASE = ["Shui", "Tu", "Mu", "Mu", "Tu", "Huo", "Huo", "Tu", "Jin", "Jin", "Tu", "Shui"]

# ---- 三十纳音（卷二·六十花甲子納音歌）----
NAYIN = [("海中金", "HaiZhongJin"), ("炉中火", "LuZhongHuo"), ("大林木", "DaLinMu"), ("路旁土", "LuPangTu"),
         ("剑锋金", "JianFengJin"), ("山头火", "ShanTouHuo"), ("涧下水", "JianXiaShui"), ("城头土", "ChengTouTu"),
         ("白蜡金", "BaiLaJin"), ("杨柳木", "YangLiuMu"), ("泉中水", "QuanZhongShui"), ("屋上土", "WuShangTu"),
         ("霹雳火", "PiLiHuo"), ("松柏木", "SongBaiMu"), ("长流水", "ChangLiuShui"), ("沙中金", "ShaZhongJin"),
         ("山下火", "ShanXiaHuo"), ("平地木", "PingDiMu"), ("壁上土", "BiShangTu"), ("金箔金", "JinBoJin"),
         ("覆灯火", "FuDengHuo"), ("天河水", "TianHeShui"), ("大驿土", "DaYiTu"), ("钗钏金", "ChaiChuanJin"),
         ("桑拓木", "SangTuoMu"), ("大溪水", "DaXiShui"), ("沙中土", "ShaZhongTu"), ("天上火", "TianShangHuo"),
         ("石榴木", "ShiLiuMu"), ("大海水", "DaHaiShui")]

# ---- 紫微定位表（5局×30日 → 地支索引 子=0；卷二·安紫微定位棋盘图；取本工程已校订内置表）----
ZIWEI_BUREAU = [2, 3, 4, 5, 6]
BUREAU_NAME = {2: "水二局", 3: "木三局", 4: "金四局", 5: "土五局", 6: "火六局"}
ZIWEI_TABLE = [
    [1, 2, 2, 3, 3, 4, 4, 5, 5, 6, 6, 7, 7, 8, 8, 9, 9, 10, 10, 11, 11, 0, 0, 1, 1, 2, 2, 3, 3, 4],
    [4, 1, 2, 5, 2, 3, 6, 3, 4, 7, 4, 5, 8, 5, 6, 9, 6, 7, 10, 7, 8, 11, 8, 9, 0, 9, 10, 1, 10, 11],
    [11, 4, 1, 2, 0, 5, 2, 3, 1, 6, 3, 4, 2, 7, 4, 5, 3, 8, 5, 6, 4, 9, 6, 7, 5, 10, 7, 8, 6, 11],
    [6, 11, 4, 1, 2, 7, 0, 5, 2, 3, 8, 1, 6, 3, 4, 9, 2, 7, 4, 5, 10, 3, 8, 5, 6, 11, 4, 9, 6, 7],
    [9, 6, 11, 4, 1, 2, 10, 7, 0, 5, 2, 3, 11, 8, 1, 6, 3, 4, 0, 9, 2, 7, 4, 5, 1, 10, 3, 8, 5, 6]]

# ---- 流禄（依年干；卷二·安禄存星诀）----
LUCUN = [2, 3, 5, 6, 5, 6, 8, 9, 11, 0]
DEITY12 = ["博士", "力士", "青龙", "小耗", "将军", "奏书", "蜚廉", "喜神", "病符", "大耗", "伏兵", "官府"]

# ---- 星辰庙陷（卷二·星辰庙陷：宫 → {等第: 星缩写}）----
CH2PINYIN = {
    "紫": "ZiWei", "机": "TianJi", "日": "TaiYang", "武": "WuQu", "同": "TianTong", "廉": "LianZhen",
    "府": "TianFu", "月": "TaiYin", "贪": "TanLang", "巨": "JuMen", "相": "TianXiang", "梁": "TianLiang",
    "杀": "QiSha", "破": "PoJun", "昌": "WenChang", "曲": "WenQu", "禄": "LuCun", "羊": "QingYang",
    "陀": "TuoLuo", "火": "HuoXing", "铃": "LingXing"}
BRIGHT = {
    "子": {"庙": "机府月相梁破禄", "旺": "武同贪巨杀", "得地": "昌曲", "利益": "", "平和": "紫廉", "不得地": "", "陷": "日羊火铃"},
    "丑": {"庙": "紫武府月贪相杀昌曲羊陀", "旺": "梁破", "得地": "火铃", "利益": "廉", "平和": "", "不得地": "日同巨", "陷": "机"},
    "寅": {"庙": "廉府巨相梁杀禄火铃", "旺": "紫日月", "得地": "机武破", "利益": "同", "平和": "贪曲", "不得地": "", "陷": "昌陀"},
    "卯": {"庙": "日巨梁禄", "旺": "紫机杀曲", "得地": "府", "利益": "武贪昌火铃", "平和": "同廉", "不得地": "", "陷": "月相破羊"},
    "辰": {"庙": "武府贪梁杀羊陀", "旺": "日破", "得地": "紫相昌曲", "利益": "机廉", "平和": "同", "不得地": "", "陷": "月巨火铃"},
    "巳": {"庙": "同昌曲禄", "旺": "紫日巨", "得地": "府相火铃", "利益": "", "平和": "机武杀破", "不得地": "", "陷": "廉月贪梁陀"},
    "午": {"庙": "紫机相梁破禄火铃", "旺": "日武府贪巨杀", "得地": "", "利益": "", "平和": "廉", "不得地": "月", "陷": "同昌曲羊"},
    "未": {"庙": "紫武府贪杀羊陀", "旺": "梁破曲", "得地": "日相", "利益": "廉昌火铃", "平和": "", "不得地": "同月巨", "陷": "机"},
    "申": {"庙": "廉巨相杀禄", "旺": "紫同", "得地": "机日武府破昌曲", "利益": "月", "平和": "贪", "不得地": "", "陷": "梁陀火铃"},
    "酉": {"庙": "巨昌曲禄", "旺": "紫机府月杀", "得地": "梁火铃", "利益": "武贪", "平和": "日同廉", "不得地": "", "陷": "相破羊"},
    "戌": {"庙": "武府贪梁杀羊陀火铃", "旺": "月破", "得地": "紫相", "利益": "机廉", "平和": "同", "不得地": "日", "陷": "巨昌曲"},
    "亥": {"庙": "同月禄", "旺": "紫巨曲", "得地": "府相", "利益": "昌火铃", "平和": "机武杀破", "不得地": "", "陷": "日廉贪梁陀"}}


def q(s):
    return "'" + str(s).replace("'", "''") + "'"


out = []
w = out.append

w("-- ============================================================================")
w("-- 数据：紫微斗数（ZiWeiDouShu）第 12 步（闸门⑪）ABox 扩至场景全集（DB 侧）")
w("--   * 幂等：先清空再插入（DELETE），配合 createdb_tables.ddl 的 DROP/CREATE 可重复执行。")
w("--   * 迁移边界（闸门⑩裁决 2026-10-31·陈希夷）：查表型历法个体自 ziweidoushu-abox.owl 迁出，")
w("--     本文件为 DB 唯一真源；静态术语（阴阳/五行/星曜名录/化气/四化类型/十二宫义/告警标志）仍留 ABox。")
w("--   * 数据规模（闸门⑪裁决 2026-10-31·陈希夷）：沿用现表逐张补全至全集——")
w("--     六十甲子 60 / 纳音 30 / 紫微定位表 150 / 星辰庙陷 252 / 流曜起例 42；其余不变。")
w("--   * 数据来源：卷二·六十花甲子納音歌、安紫微定位棋盘图、安禄存星诀、安擎羊陀罗二星诀、")
w("--     安十二宫太岁杀禄诀、星辰庙陷表；逐条附 source_note；无明确出处者标【待考】。")
w("--   * 依据：《本体核心设计》§8「规划表」+「全集规模（第 12 步）」；《应用架构设计》§9（★铁律 13/14）。")
w("-- ============================================================================")
w("")
w("USE `ziweidoushudb`;")
w("")
w("SET FOREIGN_KEY_CHECKS = 0;")
for t in ["alert_flag", "case_record", "deity_rule", "palace_stem_map", "sihua_map", "brightness_map",
          "star_layout", "leap_month", "true_solar_time", "bureau_stage", "nayin", "sexagenary_cycle",
          "ganzhi_branch", "ganzhi_stem"]:
    w("DELETE FROM `%s`;" % t)
w("SET FOREIGN_KEY_CHECKS = 1;")
w("")
w("-- ---------------------------------------------------------------------------")
w("-- 1) 十天干（10 条，全量；卷二·星辰屬性）")
w("-- ---------------------------------------------------------------------------")
w("INSERT INTO `ganzhi_stem` (`name`,`stem_name`,`stem_yinyang`,`yinyang`,`five_phase`,`stem_order`,`label`) VALUES")
rows = []
for i, (ch, py) in enumerate(STEMS):
    rows.append("(%s,%s,%s,%s,%s,%d,%s)" % (q("Stem_" + py), q(ch), q("阳" if i % 2 == 0 else "阴"),
                                            q(STEM_YINYANG[i]), q(STEM_PHASE[i]), i + 1, q(ch)))
w(",\n".join(rows) + ";")
w("")
w("-- ---------------------------------------------------------------------------")
w("-- 2) 十二地支（12 条，全量；卷二·安十二宮例）")
w("-- ---------------------------------------------------------------------------")
w("INSERT INTO `ganzhi_branch` (`name`,`branch_name`,`branch_animal`,`branch_order`,`five_phase`,`label`) VALUES")
rows = []
for i, (ch, py) in enumerate(BRANCHES):
    rows.append("(%s,%s,%s,%d,%s,%s)" % (q("Branch_" + py), q(ch), q(BRANCH_ANIMAL[i]), i + 1, q(BRANCH_PHASE[i]), q(ch)))
w(",\n".join(rows) + ";")
w("")
w("-- ---------------------------------------------------------------------------")
w("-- 3) 六十甲子（60 条，全量；卷二·六十花甲子納音歌）")
w("-- ---------------------------------------------------------------------------")
w("INSERT INTO `sexagenary_cycle` (`name`,`ganzhi`,`stem_local`,`branch_local`,`nayin_local`,`label`) VALUES")
rows = []
for i in range(60):
    si, bi = i % 10, i % 12
    gz = STEMS[si][0] + BRANCHES[bi][0]
    nm = STEMS[si][1] + BRANCHES[bi][1]
    nay = "NaYin_" + NAYIN[i // 2][1]
    rows.append("(%s,%s,%s,%s,%s,%s)" % (q(nm), q(gz), q("Stem_" + STEMS[si][1]), q("Branch_" + BRANCHES[bi][1]), q(nay), q(gz)))
w(",\n".join(rows) + ";")
w("")
w("-- ---------------------------------------------------------------------------")
w("-- 4) 纳音（30 条，全量；卷二·六十花甲子納音歌）")
w("-- ---------------------------------------------------------------------------")
w("INSERT INTO `nayin` (`name`,`nayin_name`,`label`) VALUES")
rows = []
for name, py in NAYIN:
    rows.append("(%s,%s,%s)" % (q("NaYin_" + py), q(name), q(name)))
w(",\n".join(rows) + ";")
w("")
w("-- ---------------------------------------------------------------------------")
w("-- 5) 五行局（5）+ 十二长生（12）（卷二·起五行寅例/十二长生）")
w("-- ---------------------------------------------------------------------------")
w("INSERT INTO `bureau_stage` (`name`,`kind`,`item_name`,`item_number`,`start_branch`,`label`) VALUES")
rows = []
for n, nm, num, sb in [(2, "水二局", 2, "申"), (3, "木三局", 3, "亥"), (4, "金四局", 4, "巳"), (5, "土五局", 5, "申"), (6, "火六局", 6, "寅")]:
    rows.append("(%s,'bureau',%s,%d,%s,%s)" % (q("Ju_" + {2: "Shui2", 3: "Mu3", 4: "Jin4", 5: "Tu5", 6: "Huo6"}[n]), q(nm), num, q(sb), q(nm)))
for i, nm in enumerate(["长生", "沐浴", "冠带", "临官", "帝旺", "衰", "病", "死", "墓", "绝", "胎", "养"]):
    rows.append("(%s,'stage',%s,%d,'',%s)" % (q("Stage_" + ["ChangSheng", "MuYu", "GuanDai", "LinGuan", "DiWang", "Shuai", "Bing", "Si", "Mu", "Jue", "Tai", "Yang"][i]), q(nm), i + 1, q(nm)))
w(",\n".join(rows) + ";")
w("")
w("-- ---------------------------------------------------------------------------")
w("-- 6) 真太阳时校正参数（1 条；I·61 验盘·真太阳时校正；非枚举，随命例，保留代表样）")
w("-- ---------------------------------------------------------------------------")
w("INSERT INTO `true_solar_time` (`name`,`longitude`,`label`) VALUES")
w("('TrueSolar_Demo01','116.4','真太阳时校正示例（东经 116.4°）');")
w("")
w("-- ---------------------------------------------------------------------------")
w("-- 7) 闰月归属（1 条；卷二·安身命例：闰月作下月论）")
w("-- ---------------------------------------------------------------------------")
w("INSERT INTO `leap_month` (`name`,`leap_month_rule`,`label`) VALUES")
w("('LeapMonth_ZhengYue','按下月','闰月归属示例（按下月）');")
w("")
w("-- ---------------------------------------------------------------------------")
w("-- 8) 紫微定位表（150 条 = 5 局 × 30 日，全量；卷二·安紫微定位棋盘图）")
w("-- ---------------------------------------------------------------------------")
w("INSERT INTO `star_layout` (`rule_id`,`rule_kind`,`bureau_name`,`lunar_day`,`star_local`,`to_branch`,`source_note`) VALUES")
rows = []
for ri, num in enumerate(ZIWEI_BUREAU):
    for d in range(1, 31):
        idx = ZIWEI_TABLE[ri][d - 1]
        rows.append("(%s,'紫微定位',%s,%d,'ZiWei',%s,'卷二·安紫微定位棋盘图')" % (q("ZLP_Ju%d_D%02d" % (num, d)), q(BUREAU_NAME[num]), d, q(ZHI[idx])))
w(",\n".join(rows) + ";")
w("")
w("-- ---------------------------------------------------------------------------")
w("-- 9) 星辰庙陷表（252 条 = 21 星 × 12 地支宫，全量；卷二·星辰庙陷）")
w("--     等第：庙/旺/得地/利益/平和/不得地/陷；palace_local 以地支（Branch_*）为键。")
w("-- ---------------------------------------------------------------------------")
w("INSERT INTO `brightness_map` (`map_id`,`star_local`,`palace_local`,`brightness_name`,`source_note`) VALUES")
rows = []
for bch, _bpy in BRANCHES:
    levels = BRIGHT[bch]
    for lvl, chars in levels.items():
        for c in chars:
            star = CH2PINYIN[c]
            rows.append("(%s,%s,%s,%s,'卷二·星辰庙陷')" % (q("BM_%s_%s" % (star, BRANCHES[[b[0] for b in BRANCHES].index(bch)][1])), q(star), q("Branch_" + BRANCHES[[b[0] for b in BRANCHES].index(bch)][1]), q(lvl)))
w(",\n".join(rows) + ";")
w("")
w("-- ---------------------------------------------------------------------------")
w("-- 10) 四化表（40 条 = 10 干 × 4 化，全量；卷一·諸星問答論·四化 / 卷二·安禄权科忌四星变化诀）")
w("--      【庚干四化有异说·待考】")
w("-- ---------------------------------------------------------------------------")
SIHUA = {"甲": ["LianZhen", "PoJun", "WuQu", "TaiYang"], "乙": ["TianJi", "TianLiang", "ZiWei", "TaiYin"],
         "丙": ["TianTong", "TianJi", "WenChang", "LianZhen"], "丁": ["TaiYin", "TianTong", "TianJi", "JuMen"],
         "戊": ["TanLang", "TaiYin", "YouBi", "TianJi"], "己": ["WuQu", "TanLang", "TianLiang", "WenQu"],
         "庚": ["TaiYang", "WuQu", "TaiYin", "TianTong"], "辛": ["JuMen", "TaiYang", "WenQu", "WenChang"],
         "壬": ["TianLiang", "ZiWei", "TianFu", "WuQu"], "癸": ["PoJun", "JuMen", "TaiYin", "TanLang"]}
w("INSERT INTO `sihua_map` (`map_id`,`year_stem`,`transformation_type`,`star_local`,`source_note`) VALUES")
rows = []
TYPE = ["化禄", "化权", "化科", "化忌"]
TP = ["Lu", "Quan", "Ke", "Ji"]
for stem, _py in STEMS:
    note = "卷一·諸星問答論·四化【庚干四化有异说·待考】" if stem == "庚" else ("卷二·安禄权科忌四星变化诀【壬化科「左辅化科」异说·待考】" if stem == "壬" else "卷一·諸星問答論·四化")
    for k in range(4):
        rows.append("(%s,%s,%s,%s,%s)" % (q("SH_%s_%s" % ({"甲": "Jia", "乙": "Yi", "丙": "Bing", "丁": "Ding", "戊": "Wu", "己": "Ji", "庚": "Geng", "辛": "Xin", "壬": "Ren", "癸": "Gui"}[stem], TP[k])), q(stem), q(TYPE[k]), q(SIHUA[stem][k]), q(note)))
w(",\n".join(rows) + ";")
w("")
w("-- ---------------------------------------------------------------------------")
w("-- 11) 宫干五鼠遁（5 条；卷二·推算宫干）【待考·中】")
w("-- ---------------------------------------------------------------------------")
w("INSERT INTO `palace_stem_map` (`map_id`,`year_stem_group`,`start_stem`,`source_note`) VALUES")
w("('PSM_JiaJi','甲己','丙','卷二·推算宫干·五鼠遁【待考·中】'),")
w("('PSM_YiGeng','乙庚','戊','卷二·推算宫干·五鼠遁【待考·中】'),")
w("('PSM_BingXin','丙辛','庚','卷二·推算宫干·五鼠遁【待考·中】'),")
w("('PSM_DingRen','丁壬','壬','卷二·推算宫干·五鼠遁【待考·中】'),")
w("('PSM_WuGui','戊癸','甲','卷二·推算宫干·五鼠遁【待考·中】');")
w("")
w("-- ---------------------------------------------------------------------------")
w("-- 12) 流曜起例（42 条 = 流禄10 + 流羊10 + 流陀10 + 博士十二神12；卷二 起例诸条）")
w("-- ---------------------------------------------------------------------------")
w("INSERT INTO `deity_rule` (`rule_id`,`rule_kind`,`base`,`deity_name`,`to_branch`,`source_note`) VALUES")
rows = []
for i, (stem, _py) in enumerate(STEMS):
    rows.append("(%s,'流禄',%s,'流禄',%s,'卷二·安禄存星诀')" % (q("DR_Lu_" + STEMS[i][1]), q("年干" + stem), q(ZHI[LUCUN[i]])))
for i, (stem, _py) in enumerate(STEMS):
    rows.append("(%s,'流羊',%s,'流羊',%s,'卷二·安擎羊陀罗二星诀（禄前一位）')" % (q("DR_Yang_" + STEMS[i][1]), q("年干" + stem), q(ZHI[(LUCUN[i] + 1) % 12])))
for i, (stem, _py) in enumerate(STEMS):
    rows.append("(%s,'流陀',%s,'流陀',%s,'卷二·安擎羊陀罗二星诀（禄后一位）')" % (q("DR_Tuo_" + STEMS[i][1]), q("年干" + stem), q(ZHI[(LUCUN[i] - 1) % 12])))
for k, nm in enumerate(DEITY12):
    rows.append("(%s,'十二神','随禄存起','%s','','卷二·安十二宫太岁杀禄诀')" % (q("DR_Shen_%02d" % (k + 1)), nm))
w(",\n".join(rows) + ";")
w("")
w("-- ---------------------------------------------------------------------------")
w("-- 13) 运行态命例记录（运行态；全集不含历史数据，留空，运行时由 persistAndReturn 追加）")
w("-- ---------------------------------------------------------------------------")
w("")
w("-- ---------------------------------------------------------------------------")
w("-- 14) 运行态告警标志（运行态；全集不含历史数据，留空，运行时由流程按需追加）")
w("-- ---------------------------------------------------------------------------")

with io.open(OUT, "w", encoding="utf-8", newline="\n") as f:
    f.write("\n".join(out) + "\n")

print("written:", os.path.abspath(OUT))
print("sexagenary=%d nayin=%d star_layout=%d brightness=%d deity=%d" % (
    60, 30, 150, sum(len(c) for b in BRIGHT.values() for c in b.values()), 10 * 3 + 12))
