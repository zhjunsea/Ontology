# -*- coding: utf-8 -*-
"""
第 12 步（闸门⑪）：生成 SiZhuBaZi 全集 ABox 数据脚本（表格族入库 MySQL，DB 为唯一真源）。

数据来源：六书整理稿（渊海子平 / 三命通会 / 子平真诠 / 滴天髓），逐族附出处；
          六书无明确体系出处者一律标【待考】（禁止编造，★8/10）。
输出：ontology/database/data.sql（覆盖）。

表结构 = 第 11 步 createdb_tables.ddl（11 张）+ 第 15 步新增 2 张（day_hour_clause / tiaohou_clause）。
命名与既有典型数据保持一致（Stem_Jia / Branch_Zi / JiaZi / NaYin_HaiZhongJin / Term_LiChun / ZiHidden ...）。
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from sizhubazi_clauses import day_hour_clauses, tiaohou_clauses  # noqa: E402
from sizhubazi_catalog import (EXTRA_DEITY, EXTRA_DEITY2, PATTERN_CATALOG,  # noqa: E402
                               LADY_CATALOG, TECHNIQUE_CATALOG)
from sizhubazi_nature import sixty_nature  # noqa: E402
from sizhubazi_reading import (AUSPICE_CLAUSES, THEORY_CLAUSES,  # noqa: E402
                               DTS_CLAUSES)
from sizhubazi_fu import FU_CLAUSES  # noqa: E402
from sizhubazi_ziping import ZIPING_CLAUSES  # noqa: E402
from sizhubazi_classic import classic_texts  # noqa: E402
from sizhubazi_solarterm import all_rows as _solar_term_time_rows  # noqa: E402


def esc(txt, n=500):
    """SQL 单引号转义 + 截断。"""
    return txt.replace('\\', '').replace("'", "''")[:n]

# ---------------------------------------------------------------------------
# 基础数组
# ---------------------------------------------------------------------------
GAN = ['甲', '乙', '丙', '丁', '戊', '己', '庚', '辛', '壬', '癸']
ZHI = ['子', '丑', '寅', '卯', '辰', '巳', '午', '未', '申', '酉', '戌', '亥']
GANPY = ['Jia', 'Yi', 'Bing', 'Ding', 'Wu', 'Ji', 'Geng', 'Xin', 'Ren', 'Gui']
ZHIPY = ['Zi', 'Chou', 'Yin', 'Mao', 'Chen', 'Si', 'Wu', 'Wei', 'Shen', 'You', 'Xu', 'Hai']
ELEM_OF_GAN = ['Mu', 'Mu', 'Huo', 'Huo', 'Tu', 'Tu', 'Jin', 'Jin', 'Shui', 'Shui']
YY_OF_GAN = ['Yang', 'Yin', 'Yang', 'Yin', 'Yang', 'Yin', 'Yang', 'Yin', 'Yang', 'Yin']
ANIMAL = ['鼠', '牛', '虎', '兔', '龙', '蛇', '马', '羊', '猴', '鸡', '狗', '猪']
ZHI_ELEM = ['Shui', 'Tu', 'Mu', 'Mu', 'Tu', 'Huo', 'Huo', 'Tu', 'Jin', 'Jin', 'Tu', 'Shui']

# 五行生克（用于十神推导）
SHENG = {'Mu': 'Huo', 'Huo': 'Tu', 'Tu': 'Jin', 'Jin': 'Shui', 'Shui': 'Mu'}   # 我生者
KE = {'Mu': 'Tu', 'Tu': 'Shui', 'Shui': 'Huo', 'Huo': 'Jin', 'Jin': 'Mu'}      # 我克者

# ---------------------------------------------------------------------------
# 纳音（30）：甲子起，每两柱共用一气｜渊海子平卷之一·论六十花甲子纳音并注解；三命通会卷一·论纳音取象
# ---------------------------------------------------------------------------
NAYIN = [
    ('海中金', 'Jin', 'HaiZhongJin'), ('炉中火', 'Huo', 'LuZhongHuo'),
    ('大林木', 'Mu', 'DaLinMu'), ('路旁土', 'Tu', 'LuPangTu'),
    ('剑锋金', 'Jin', 'JianFengJin'), ('山头火', 'Huo', 'ShanTouHuo'),
    ('涧下水', 'Shui', 'JianXiaShui'), ('城头土', 'Tu', 'ChengTouTu'),
    ('白蜡金', 'Jin', 'BaiLaJin'), ('杨柳木', 'Mu', 'YangLiuMu'),
    ('泉中水', 'Shui', 'QuanZhongShui'), ('屋上土', 'Tu', 'WuShangTu'),
    ('霹雳火', 'Huo', 'PiLiHuo'), ('松柏木', 'Mu', 'SongBaiMu'),
    ('长流水', 'Shui', 'ChangLiuShui'), ('砂中金', 'Jin', 'ShaZhongJin'),
    ('山下火', 'Huo', 'ShanXiaHuo'), ('平地木', 'Mu', 'PingDiMu'),
    ('壁上土', 'Tu', 'BiShangTu'), ('金箔金', 'Jin', 'JinBoJin'),
    ('覆灯火', 'Huo', 'FuDengHuo'), ('天河水', 'Shui', 'TianHeShui'),
    ('大驿土', 'Tu', 'DaYiTu'), ('钗钏金', 'Jin', 'ChaiChuanJin'),
    ('桑柘木', 'Mu', 'SangZheMu'), ('大溪水', 'Shui', 'DaXiShui'),
    ('沙中土', 'Tu', 'ShaZhongTu'), ('天上火', 'Huo', 'TianShangHuo'),
    ('石榴木', 'Mu', 'ShiLiuMu'), ('大海水', 'Shui', 'DaHaiShui'),
]

# ---------------------------------------------------------------------------
# 地支藏干（本气/中气/余气）｜渊海子平卷之一·又地支藏遁歌；三命通会卷一·论人元司事
# ---------------------------------------------------------------------------
ZHI_HIDDEN = {
    0:  [('癸', '本气')],
    1:  [('己', '本气'), ('癸', '中气'), ('辛', '余气')],
    2:  [('甲', '本气'), ('丙', '中气'), ('戊', '余气')],
    3:  [('乙', '本气')],
    4:  [('戊', '本气'), ('乙', '中气'), ('癸', '余气')],
    5:  [('丙', '本气'), ('戊', '中气'), ('庚', '余气')],
    6:  [('丁', '本气'), ('己', '中气')],
    7:  [('己', '本气'), ('丁', '中气'), ('乙', '余气')],
    8:  [('庚', '本气'), ('壬', '中气'), ('戊', '余气')],
    9:  [('辛', '本气')],
    10: [('戊', '本气'), ('辛', '中气'), ('丁', '余气')],
    11: [('壬', '本气'), ('甲', '中气')],
}

# ---------------------------------------------------------------------------
# 二十四节气（序 1..24，立春为首）｜渊海子平卷之一·论节候歌 / 又论节气歌；三命通会卷一·论二十四节气
# ---------------------------------------------------------------------------
TERMS = [
    ('立春', 'LiChun'), ('雨水', 'YuShui'), ('惊蛰', 'JingZhe'), ('春分', 'ChunFen'),
    ('清明', 'QingMing'), ('谷雨', 'GuYu'), ('立夏', 'LiXia'), ('小满', 'XiaoMan'),
    ('芒种', 'MangZhong'), ('夏至', 'XiaZhi'), ('小暑', 'XiaoShu'), ('大暑', 'DaShu'),
    ('立秋', 'LiQiu'), ('处暑', 'ChuShu'), ('白露', 'BaiLu'), ('秋分', 'QiuFen'),
    ('寒露', 'HanLu'), ('霜降', 'ShuangJiang'), ('立冬', 'LiDong'), ('小雪', 'XiaoXue'),
    ('大雪', 'DaXue'), ('冬至', 'DongZhi'), ('小寒', 'XiaoHan'), ('大寒', 'DaHan'),
]

# ---------------------------------------------------------------------------
# 神煞起例（内置查表，非本体个体）｜三命通会卷二（诸神煞篇）；渊海子平卷之一·诸神煞篇
# 字段：rule_id, base_pillar, base_ganzhi, deity_name, deity_alias, branch_hit, category, source_note
# ---------------------------------------------------------------------------
SANHE_GROUPS = [('申子辰', 0), ('寅午戌', 1), ('巳酉丑', 2), ('亥卯未', 3)]
# grp -> 各起例命中支
YIMA = {'申子辰': '寅', '寅午戌': '申', '巳酉丑': '亥', '亥卯未': '巳'}
TAOHUA = {'申子辰': '酉', '寅午戌': '卯', '巳酉丑': '午', '亥卯未': '子'}
HUAGAI = {'申子辰': '辰', '寅午戌': '戌', '巳酉丑': '丑', '亥卯未': '未'}
JIANGXING = {'申子辰': '子', '寅午戌': '午', '巳酉丑': '酉', '亥卯未': '卯'}
JIESHA = {'申子辰': '巳', '寅午戌': '亥', '巳酉丑': '寅', '亥卯未': '申'}
WANGSHEN = {'申子辰': '亥', '寅午戌': '巳', '巳酉丑': '申', '亥卯未': '寅'}

DEITY_RULES = []


def add_rule(rid, base_pillar, base_ganzhi, name, alias, hit, cat, src):
    DEITY_RULES.append((rid, base_pillar, base_ganzhi, name, alias, hit, cat, src))


# 1) 天乙贵人（异名异法并陈）｜三命通会卷二·论天乙贵人
add_rule('DR_TianYi_JiaWuGeng', '日干', '甲戊庚', '天乙贵人', '', '丑未', '贵人吉神', '三命通会卷二·论天乙贵人')
add_rule('DR_TianYi_YiJi', '日干', '乙己', '天乙贵人', '', '子申', '贵人吉神', '三命通会卷二·论天乙贵人')
add_rule('DR_TianYi_BingDing', '日干', '丙丁', '天乙贵人', '', '亥酉', '贵人吉神', '三命通会卷二·论天乙贵人')
add_rule('DR_TianYi_RenGui', '日干', '壬癸', '天乙贵人', '', '卯巳', '贵人吉神', '三命通会卷二·论天乙贵人')
add_rule('DR_TianYi_GengXin', '日干', '庚辛', '天乙贵人', '', '午寅', '贵人吉神', '三命通会卷二·论天乙贵人（庚辛逢虎马）')

# 2) 羊刃｜渊海子平卷之一·诸神煞篇；三命通会卷二·论羊刃
add_rule('DR_YangRen_Jia', '日干', '甲', '羊刃', '阳刃', '卯', '凶煞', '三命通会卷二·论羊刃')
add_rule('DR_YangRen_BingWu', '日干', '丙戊', '羊刃', '阳刃', '午', '凶煞', '三命通会卷二·论羊刃')
add_rule('DR_YangRen_Geng', '日干', '庚', '羊刃', '阳刃', '酉', '凶煞', '三命通会卷二·论羊刃')
add_rule('DR_YangRen_Ren', '日干', '壬', '羊刃', '阳刃', '子', '凶煞', '三命通会卷二·论羊刃')
add_rule('DR_YangRen_Yi', '日干', '乙', '羊刃', '阴刃（异法）', '寅', '凶煞', '渊海子平卷之一·诸神煞篇（阴干刃异法并陈）')
add_rule('DR_YangRen_DingJi', '日干', '丁己', '羊刃', '阴刃（异法）', '巳', '凶煞', '渊海子平卷之一·诸神煞篇')
add_rule('DR_YangRen_Xin', '日干', '辛', '羊刃', '阴刃（异法）', '申', '凶煞', '渊海子平卷之一·诸神煞篇')
add_rule('DR_YangRen_Gui', '日干', '癸', '羊刃', '阴刃（异法）', '亥', '凶煞', '渊海子平卷之一·诸神煞篇')

# 3) 驿马｜三命通会卷二·论驿马
for i, g in enumerate(['申子辰', '寅午戌', '巳酉丑', '亥卯未']):
    add_rule('DR_YiMa_' + ['ShenZiChen', 'YinWuXu', 'SiYouChou', 'HaiMaoWei'][i],
             '年支/日支', g, '驿马', '', YIMA[g], '禄马', '三命通会卷二·论驿马')

# 4) 空亡（旬空，六旬）｜三命通会卷二·论空亡
XUN = [('甲子旬', '戌亥'), ('甲戌旬', '申酉'), ('甲申旬', '午未'), ('甲午旬', '辰巳'), ('甲辰旬', '寅卯'), ('甲寅旬', '子丑')]
for i, (xun, hit) in enumerate(XUN):
    add_rule('DR_Void_' + ['JiaZi', 'JiaXu', 'JiaShen', 'JiaWu', 'JiaChen', 'JiaYin'][i],
             '日柱', xun, '旬空', '空亡', hit, '空亡', '三命通会卷二·论空亡')

# 5) 咸池（桃花）｜三命通会卷二·论咸池
for i, g in enumerate(['申子辰', '寅午戌', '巳酉丑', '亥卯未']):
    add_rule('DR_TaoHua_' + ['ShenZiChen', 'YinWuXu', 'SiYouChou', 'HaiMaoWei'][i],
             '年支/日支', g, '桃花', '咸池', TAOHUA[g], '凶煞', '三命通会卷二·论咸池')

# 6) 华盖｜三命通会卷二·论华盖
for i, g in enumerate(['申子辰', '寅午戌', '巳酉丑', '亥卯未']):
    add_rule('DR_HuaGai_' + ['ShenZiChen', 'YinWuXu', 'SiYouChou', 'HaiMaoWei'][i],
             '年支/日支', g, '华盖', '', HUAGAI[g], '贵人吉神', '三命通会卷二·论华盖')

# 7) 将星｜三命通会卷二·论将星
for i, g in enumerate(['申子辰', '寅午戌', '巳酉丑', '亥卯未']):
    add_rule('DR_JiangXing_' + ['ShenZiChen', 'YinWuXu', 'SiYouChou', 'HaiMaoWei'][i],
             '年支/日支', g, '将星', '', JIANGXING[g], '禄马', '三命通会卷二·论将星')

# 8) 禄神（临官）｜三命通会卷二·论禄
LU = [('甲', '寅'), ('乙', '卯'), ('丙戊', '巳'), ('丁己', '午'), ('庚', '申'), ('辛', '酉'), ('壬', '亥'), ('癸', '子')]
for i, (gg, hit) in enumerate(LU):
    add_rule('DR_Lu_' + ['Jia', 'Yi', 'BingWu', 'DingJi', 'Geng', 'Xin', 'Ren', 'Gui'][i],
             '日干', gg, '禄', '临官', hit, '禄马', '三命通会卷二·论禄')

# 9) 文昌贵人｜三命通会卷二·论文昌
WENCHANG = [('甲', '巳'), ('乙', '午'), ('丙', '申'), ('丁', '酉'), ('戊', '申'), ('己', '酉'),
            ('庚', '亥'), ('辛', '子'), ('壬', '寅'), ('癸', '卯')]
for i, (gg, hit) in enumerate(WENCHANG):
    add_rule('DR_WenChang_' + ['Jia', 'Yi', 'Bing', 'Ding', 'Wu', 'Ji', 'Geng', 'Xin', 'Ren', 'Gui'][i],
             '日干', gg, '文昌贵人', '', hit, '贵人吉神', '三命通会卷二·论文昌')

# 10) 金舆｜三命通会卷二·论金舆
JINYU = [('甲', '辰'), ('乙', '巳'), ('丙', '未'), ('丁', '申'), ('戊', '未'), ('己', '申'),
         ('庚', '戌'), ('辛', '亥'), ('壬', '丑'), ('癸', '寅')]
for i, (gg, hit) in enumerate(JINYU):
    add_rule('DR_JinYu_' + ['Jia', 'Yi', 'Bing', 'Ding', 'Wu', 'Ji', 'Geng', 'Xin', 'Ren', 'Gui'][i],
             '日干', gg, '金舆', '', hit, '贵人吉神', '三命通会卷二·论金舆')

# 11) 天德（按月支逢干）｜三命通会卷二·论天德月德
TIANDE = [('寅月', '丁'), ('卯月', '申'), ('辰月', '壬'), ('巳月', '辛'), ('午月', '亥'), ('未月', '甲'),
          ('申月', '癸'), ('酉月', '寅'), ('戌月', '丙'), ('亥月', '乙'), ('子月', '巳'), ('丑月', '庚')]
for i, (mm, hit) in enumerate(TIANDE):
    add_rule('DR_TianDe_' + ['Yin', 'Mao', 'Chen', 'Si', 'Wu', 'Wei', 'Shen', 'You', 'Xu', 'Hai', 'Zi', 'Chou'][i],
             '月支', mm, '天德', '', hit, '贵人吉神', '三命通会卷二·论天德（命中或为干或为支）')

# 12) 月德（按月支三合逢干）｜三命通会卷二·论天德月德
YUEDE = [('寅午戌月', '丙'), ('申子辰月', '壬'), ('亥卯未月', '甲'), ('巳酉丑月', '庚')]
for i, (mm, hit) in enumerate(YUEDE):
    add_rule('DR_YueDe_' + ['YinWuXu', 'ShenZiChen', 'HaiMaoWei', 'SiYouChou'][i],
             '月支', mm, '月德', '', hit, '贵人吉神', '三命通会卷二·论月德')

# 13) 劫煞｜三命通会卷二·论劫煞
for i, g in enumerate(['申子辰', '寅午戌', '巳酉丑', '亥卯未']):
    add_rule('DR_JieSha_' + ['ShenZiChen', 'YinWuXu', 'SiYouChou', 'HaiMaoWei'][i],
             '年支/日支', g, '劫煞', '', JIESHA[g], '凶煞', '三命通会卷二·论劫煞')

# 14) 亡神｜三命通会卷二·论亡神
for i, g in enumerate(['申子辰', '寅午戌', '巳酉丑', '亥卯未']):
    add_rule('DR_WangShen_' + ['ShenZiChen', 'YinWuXu', 'SiYouChou', 'HaiMaoWei'][i],
             '年支/日支', g, '亡神', '', WANGSHEN[g], '凶煞', '三命通会卷二·论亡神')

# 15) 孤辰寡宿（按年支）｜三命通会卷二·论孤辰寡宿
GU_GUA = [('亥子丑', '寅', '戌'), ('寅卯辰', '巳', '丑'), ('巳午未', '申', '辰'), ('申酉戌', '亥', '未')]
for i, (yy, gu, gua) in enumerate(GU_GUA):
    add_rule('DR_GuChen_' + ['HaiZiChou', 'YinMaoChen', 'SiWuWei', 'ShenYouXu'][i],
             '年支', yy, '孤辰', '', gu, '凶煞', '三命通会卷二·论孤辰寡宿')
    add_rule('DR_GuaSu_' + ['HaiZiChou', 'YinMaoChen', 'SiWuWei', 'ShenYouXu'][i],
             '年支', yy, '寡宿', '', gua, '凶煞', '三命通会卷二·论孤辰寡宿')

# 16) 天赦（按季节）｜渊海子平卷之一·论天赦；三命通会亦载
add_rule('DR_TianShe', '季节', '春/夏/秋/冬', '天赦', '',
         '春戊寅/夏甲午/秋戊申/冬甲子', '贵人吉神', '渊海子平卷之一·论天赦（凡生日干遇之方得用）')

# 17) 四废日（按季节）｜渊海子平卷之一·论四废日
add_rule('DR_SiFeiRi', '季节', '春/夏/秋/冬', '四废日', '',
         '春庚申/夏壬子/秋甲寅/冬丙午', '凶煞', '渊海子平卷之一·论四废日（囚而无用谓之废）')

# 18) 天地转杀（按季节；旺连干为天转、旺连纳音为地转）｜渊海子平卷之一·论天地转杀
add_rule('DR_TianDiZhuanSha', '季节', '春/夏/秋/冬', '天地转杀', '天转/地转',
         '春乙卯辛卯/夏丙午戊午/秋辛酉癸酉/冬壬子丙子', '凶煞',
         '渊海子平卷之一·论天地转杀（旺连干曰天转、旺连纳音曰地转）')

# 19) 天厨贵人（按日干；取食神临禄）｜渊海子平卷之一·论天厨贵人
add_rule('DR_TianChu', '日干', '甲丙/乙丁/戊/己/庚/癸/壬/辛', '天厨贵人', '',
         '甲丙巳（双女）/乙丁午（狮子）/己酉（金牛）/戊申/庚亥（鱼腹）/癸卯（天蝎）/壬寅（人马）/辛子（宝瓶）',
         '贵人吉神', '渊海子平卷之一·论天厨贵人（食神临禄，眉批注甲木生丙、丙禄在巳）')

# 20) 月德合（按月支三合）｜渊海子平卷之一·论月德合
add_rule('DR_YueDeHe', '月支三合', '寅午戌/申子辰/亥卯未/巳酉丑', '月德合', '',
         '辛/丁/己/乙', '贵人吉神', '渊海子平卷之一·论月德合（从寅上起辛己丁乙逐位顺数）')

# 21) 夹禄（按日干；禄前一位与后一位）｜渊海子平卷之一·论夹禄
add_rule('DR_JiaLu', '日干', '甲/乙（余仿此）', '夹禄', '',
         '甲丑卯/乙寅辰（禄前后各一位）', '禄马', '渊海子平卷之一·论夹禄（甲禄在寅，前丑后卯；其他仿此）')

# 22) 垣城（取日上天干长生）｜渊海子平卷之一·论垣城
add_rule('DR_YuanCheng', '日干长生', '日天干长生之支', '垣城', '',
         '如甲辰日长生在亥即亥上（取日干长生）', '禄马',
         '渊海子平卷之一·论垣城（取日上天干长生；垣城主妻宫，合驿马主是非）')

# 23) 帝座（取时下纳音旺处）｜渊海子平卷之一·论帝座
add_rule('DR_DiZuo', '时柱纳音', '纳音五行旺处', '帝座', '',
         '如甲子时纳音属金、金旺于酉即酉上（取时纳音旺处）', '禄马',
         '渊海子平卷之一·论帝座（取时下纳音旺处；帝座主儿女宫）')

# 24) 三元（天地人三元；以日柱为例）｜渊海子平卷之一·论三元
add_rule('DR_SanYuan', '日柱', '甲子（例）', '三元', '天元/地元/人元',
         '甲木为天元/子为地元/子中藏癸水为人元', '概念',
         '渊海子平卷之一·论三元（甲木为天元、子为地元、子中所藏癸水为人元）')

# 25) 第 16 步（H1 神煞补录）：顶层设计 F·25/26/27/28 列名而本体 0 命中者
#     出处：三命通会卷二（论太极贵/论学堂词馆/论德秀/论元辰/论暗金的煞/论灾煞/论六厄/论勾绞/
#           论天罗地网/论十恶大败/日禄归时）；渊海子平卷之一（论起天官贵人/论三奇贵人/论福星贵人/
#           论十干学堂/论拱禄/论交禄/论暗禄/论截路空亡/论四大空亡）。★8/9/10 逐条指到篇章。
for _r in EXTRA_DEITY:
    add_rule(*_r)

# 26) 第 17 步（第二轮 I2 神煞细煞补录）：设计 F·25/26/27 枚举之外、典籍正文另载者
#     出处：三命通会卷三·总论诸神煞（破煞/返本/阴阳/淫欲妨害/孤鸾寡鹊/阴阳差错/桃花红艳）；
#           神峰通考·吉神类（官贵学馆/红鸾/天喜/解神）· 凶神类（隔角/伏吟/返吟/破军/悬针/平头/
#           紫暗星/流霞/冲天/五鬼/红艳/吞焰/三丘五墓/马前神煞·攀鞍年杀月杀/流年星耀/太白星/斧劈星/孤虚神）。★8/9/10。
for _r in EXTRA_DEITY2:
    add_rule(*_r)


# ---------------------------------------------------------------------------
# 十神推导（日干 × 目标干）
# ---------------------------------------------------------------------------
def ten_god(day_i, tgt_i):
    de, te = ELEM_OF_GAN[day_i], ELEM_OF_GAN[tgt_i]
    same = (YY_OF_GAN[day_i] == YY_OF_GAN[tgt_i])
    if te == de:
        return '比肩' if same else '劫财'
    if SHENG[de] == te:
        return '食神' if same else '伤官'
    if KE[de] == te:
        return '偏财' if same else '正财'
    if KE[te] == de:
        return '偏官' if same else '正官'
    if SHENG[te] == de:
        return '偏印' if same else '正印'
    raise ValueError('unreachable')


# ---------------------------------------------------------------------------
# SQL 组装
# ---------------------------------------------------------------------------
L = []
w = L.append

w('-- ============================================================================')
w('-- 数据：全集 ABox 入库（幂等，可重复执行）')
w('--   第 12 步（闸门⑪，数据来源裁定：AI 依六书生成 + 客户校订；落地裁定：表格族入库 DB、结构个体留 abox.owl）。')
w('--   查表型历法（天干/地支/六十甲子/纳音/藏干/节气/月建）全集入库 sizhubazidb；')
w('--   神煞起例 / 十神映射为「内置查表」（非本体个体），亦全集入库。')
w('--   第 15 步（六典籍覆盖补全）新增 day_hour_clause（720）/ tiaohou_clause（120）。')
w('--   第 16 步（六典籍正文级复核 H1~H4 补录）新增/扩充：神煞起例补录 +49（H1）、pattern_catalog 95（H2）、')
w('--     lady_fortune_catalog 17（H3）、technique_catalog 38（H4）、sexagenary_nature 60（H4）；后四者经 OBDA 暴露。')
w('--   出处：渊海子平卷之一（论六十花甲子纳音并注解 / 又地支藏遁歌 / 论节候歌 / 论日为主 / 诸神煞篇）、')
w('--         三命通会卷一（论纳音取象 / 论人元司事 / 论二十四节气）、卷二（论遁月时 / 论驿马 / 论天乙贵人 /')
w('--         论羊刃 / 论咸池 / 论华盖 / 论将星 / 论禄 / 论文昌 / 论金舆 / 论天德月德 / 论劫煞 / 论亡神 / 论孤辰寡宿 / 论空亡）。')
w('--   ★8/10 禁编造：六书无明确体系出处者一律标【待考】（如纳音吉凶三分），留待客户校订。')
w('-- ============================================================================')
w('')
w('-- 清空旧数据（便于本文件重复执行）')
for t in ['ziping_clause', 'fu_clause', 'dts_clause', 'theory_clause', 'auspice_clause',
          'sexagenary_nature', 'technique_catalog', 'lady_fortune_catalog', 'pattern_catalog',
          'tiaohou_clause', 'day_hour_clause', 'alert_flag', 'case_record', 'ten_god_map',
          'deity_rule', 'month_order', 'solar_term_time', 'solar_term', 'hidden_stem', 'nayin', 'sexagenary_cycle',
          'ganzhi_branch', 'ganzhi_stem']:
    w('DELETE FROM `%s`;' % t)
w('')

# 1. 十天干
w('-- ============================================================================')
w('-- 1. 十天干（10）｜core:HeavenlyStem｜出处：渊海子平卷之一·论天干阴阳（甲乙丙丁戊己庚辛壬癸）')
w('-- ============================================================================')
w('INSERT INTO `ganzhi_stem` (name, stem_name, stem_yinyang, yinyang, five_phase, stem_order, label) VALUES')
rows = []
for i in range(10):
    yy = '阳' if YY_OF_GAN[i] == 'Yang' else '阴'
    rows.append("('%s', '%s', '%s', '%s', '%s', %d, '%s')" %
                ('Stem_' + GANPY[i], GAN[i], yy, YY_OF_GAN[i], ELEM_OF_GAN[i], i + 1, GAN[i]))
w(',\n'.join(rows) + ';')
w('')

# 2. 十二地支
w('-- ============================================================================')
w('-- 2. 十二地支（12）｜core:EarthlyBranch｜出处：渊海子平卷之一；三命通会卷二·论地支生肖')
w('-- ============================================================================')
w('INSERT INTO `ganzhi_branch` (name, branch_name, branch_animal, branch_order, five_phase, label) VALUES')
rows = []
for i in range(12):
    rows.append("('%s', '%s', '%s', %d, '%s', '%s')" %
                ('Branch_' + ZHIPY[i], ZHI[i], ANIMAL[i], i + 1, ZHI_ELEM[i], ZHI[i]))
w(',\n'.join(rows) + ';')
w('')

# 3. 六十甲子
w('-- ============================================================================')
w('-- 3. 六十甲子（60）｜core:SexagenaryCycle｜出处：渊海子平卷之一·论六十花甲子纳音并注解')
w('-- ============================================================================')
w('INSERT INTO `sexagenary_cycle` (name, ganzhi, stem_local, branch_local, nayin_local, label) VALUES')
rows = []
for k in range(60):
    s, b = k % 10, k % 12
    gz = GAN[s] + ZHI[b]
    nname = 'NaYin_' + NAYIN[k // 2][2]
    rows.append("('%s', '%s', '%s', '%s', '%s', '%s')" %
                (GANPY[s] + ZHIPY[b], gz, 'Stem_' + GANPY[s], 'Branch_' + ZHIPY[b], nname, gz))
w(',\n'.join(rows) + ';')
w('')

# 4. 纳音
w('-- ============================================================================')
w('-- 4. 纳音（30）｜core:NaYin｜出处：渊海子平卷之一·论六十花甲子纳音并注解；三命通会卷一·论纳音取象')
w('--   note：nayin_nature（吉/平/凶）六书无明确体系出处，统一标【待考】待客户校订（★8/10）。')
w('-- ============================================================================')
w('INSERT INTO `nayin` (name, nayin_name, nayin_nature, five_phase, label) VALUES')
rows = []
for (nm, el, py) in NAYIN:
    rows.append("('NaYin_%s', '%s', '待考', '%s', '%s')" % (py, nm, el, nm))
w(',\n'.join(rows) + ';')
w('')

# 5. 地支藏干
w('-- ============================================================================')
w('-- 5. 地支藏干（28）｜core:HiddenStem｜出处：渊海子平卷之一·又地支藏遁歌；三命通会卷一·论人元司事')
w('--   命名：单藏干支 <Zhi>Hidden；多藏干支 <Zhi>Hidden<Stem>（与第 11 步典型数据一致）')
w('-- ============================================================================')
w('INSERT INTO `hidden_stem` (name, role, branch_local, label) VALUES')
rows = []
for zi in range(12):
    for (stem, qi) in ZHI_HIDDEN[zi]:
        if len(ZHI_HIDDEN[zi]) == 1:
            hid = ZHIPY[zi] + 'Hidden'
        else:
            hid = ZHIPY[zi] + 'Hidden' + GANPY[GAN.index(stem)]
        role = '%s藏%s（人元·%s）' % (ZHI[zi], stem, qi)
        label = '%s藏%s' % (ZHI[zi], stem)
        rows.append("('%s', '%s', '%s', '%s')" % (hid, role, 'Branch_' + ZHIPY[zi], label))
w(',\n'.join(rows) + ';')
w('')

# 6. 二十四节气
w('-- ============================================================================')
w('-- 6. 二十四节气（24）｜calendar:SolarTerm｜出处：渊海子平卷之一·论节候歌 / 又论节气歌')
w('-- ============================================================================')
w('INSERT INTO `solar_term` (name, solar_term_name, term_order, label) VALUES')
rows = []
for i, (nm, py) in enumerate(TERMS):
    rows.append("('Term_%s', '%s', %d, '%s')" % (py, nm, i + 1, nm))
w(',\n'.join(rows) + ';')
w('')

# 6b. 二十四节气·精确交节时刻（1950–2050）
SOLAR_TERM_TIME_ROWS = _solar_term_time_rows()
w('-- ============================================================================')
w('-- 6b. 二十四节气·精确交节时刻｜calendar:SolarTermInstant（1950–2050，共 %d 行）' % len(SOLAR_TERM_TIME_ROWS))
w('--   数据来源：太阳视黄经法（Meeus《Astronomical Algorithms》2nd ed. 第 25 章低精度太阳位置')
w('--     + Espenak & Meeus ΔT 多项式）自算，输出北京时间（UTC+8）；')
w('--   term_idx 与程序 S_TERM 同序（0=小寒 … 23=冬至）；term_local → solar_term.name（如 Term_LiChun）。')
w('--   ★8/10 禁编造：算法与出处如实标注；交节「日」与公开历法一致，「时刻」误差约 ±15 分钟（详见生成脚本）。')
w('-- ============================================================================')
w('INSERT INTO `solar_term_time` (name, term_year, term_idx, term_local, term_name, term_datetime) VALUES')
rows = []
for (yr, idx, nm, py, dts) in SOLAR_TERM_TIME_ROWS:
    rows.append("('TermTime_%d_%s', %d, %d, 'Term_%s', '%s', '%s')" % (yr, py, yr, idx, py, nm, dts))
w(',\n'.join(rows) + ';')
w('')

# 7. 十二月建
w('-- ============================================================================')
w('-- 7. 十二月建（12）｜calendar:MonthOrder｜出处：三命通会卷二·论遁月时（五虎遁，正月建寅）')
w('-- ============================================================================')
w('INSERT INTO `month_order` (name, month_order_name, label) VALUES')
rows = []
# 正月起寅，顺序 寅卯辰巳午未申酉戌亥子丑
order_zhi = ZHI[2:] + ZHI[:2]
for b in order_zhi:
    rows.append("('Month%s', '%s', '%s')" % (ZHIPY[ZHI.index(b)], b, b))
w(',\n'.join(rows) + ';')
w('')

# 8. 神煞起例
w('-- ============================================================================')
w('-- 8. 神煞起例（%d；内置查表，非本体个体）｜三命通会卷二·诸神煞篇；渊海子平卷之一·诸神煞篇' % len(DEITY_RULES))
w('--   异名异法并陈（阴干羊刃/天乙贵人庚辛等）；六书无明确出处者已并入异名或用【待考】标注。')
w('-- ============================================================================')
w('INSERT INTO `deity_rule` (rule_id, base_pillar, base_ganzhi, deity_name, deity_alias, branch_hit, category, source_note) VALUES')
rows = []
for (rid, bp, bg, nm, al, hit, cat, src) in DEITY_RULES:
    rows.append("('%s', '%s', '%s', '%s', '%s', '%s', '%s', '%s')" % (rid, bp, bg, nm, al, hit, cat, src))
w(',\n'.join(rows) + ';')
w('')

# 8b. 日时断条文库（三命通会卷八/卷九·六X日Y时断，720 条）
DHC = day_hour_clauses()
_tb = sum(1 for r in DHC if r['clause_text'].startswith('【待考】'))
w('-- ============================================================================')
w('-- 8b. 日时断条文库（%d；经 OBDA 暴露 reading:DayHourClause）｜三命通会卷八/卷九·六X日Y时断' % len(DHC))
w('--   60 日柱 × 12 时支 = 720；源本真缺 %d 条建【待考】占位（不编造）。' % _tb)
w('-- ============================================================================')
w('INSERT INTO `day_hour_clause` (clause_id, day_pillar, hour_pillar, day_stem, day_branch, hour_branch, clause_text, source_note) VALUES')
rows = []
for r in DHC:
    rows.append("('%s', '%s', '%s', '%s', '%s', '%s', '%s', '%s')" % (
        r['clause_id'], r['day_pillar'], r['hour_pillar'], r['day_stem'], r['day_branch'],
        r['hour_branch'], esc(r['clause_text']), esc(r['source_note'], 120)))
w(',\n'.join(rows) + ';')
w('')

# 8c. 调候逐月条文库（穷通宝鉴·十天干生十二月，120 条）
THC = tiaohou_clauses()
_nv = sum(1 for r in THC if r['primary_use'] == '【待考】')
w('-- ============================================================================')
w('-- 8c. 调候逐月条文库（%d；经 OBDA 暴露 yongshen:TiaohouClause）｜穷通宝鉴（造化元钥）·各干逐月取用' % len(THC))
w('--   十天干 × 十二月 = 120；原文明示者照录，首句无据者 %d 条标【待考】（不编造）。' % _nv)
w('-- ============================================================================')
w('INSERT INTO `tiaohou_clause` (clause_id, day_stem, month_branch, month_order, primary_use, secondary_use, clause_text, source_note) VALUES')
rows = []
for r in THC:
    rows.append("('%s', '%s', '%s', %d, '%s', '%s', '%s', '%s')" % (
        r['clause_id'], r['day_stem'], r['month_branch'], r['month_order'],
        esc(r['primary_use'], 16), esc(r['secondary_use'], 32), esc(r['clause_text']), esc(r['source_note'], 120)))
w(',\n'.join(rows) + ';')
w('')

# 9. 十神映射
w('-- ============================================================================')
w('-- 9. 十神映射（100）｜内置查表，非本体个体｜出处：渊海子平卷之一·论日为主（日干生克同类，同异性分正偏）')
w('-- ============================================================================')
w('INSERT INTO `ten_god_map` (map_id, day_stem, target_stem, ten_god, source_note) VALUES')
rows = []
for d in range(10):
    for t in range(10):
        rows.append("('TGM_%s_%s', '%s', '%s', '%s', '%s')" %
                    (GANPY[d], GANPY[t], GAN[d], GAN[t], ten_god(d, t), '渊海子平卷之一·论日为主'))
w(',\n'.join(rows) + ';')
w('')

# 9b. 具名格局名录（H2，pattern_catalog；经 OBDA 暴露为 pattern:Pattern）
w('-- ============================================================================')
w('-- 9b. 具名格局名录（%d；经 OBDA 暴露为 pattern:Pattern）' % len(PATTERN_CATALOG))
w('--   出处：三命通会卷五/卷六、神峰通考、渊海子平卷二（★8/9/10 逐条指到篇章）。' % ())
w('-- ============================================================================')
w('INSERT INTO `pattern_catalog` (pattern_id, pattern_name, pattern_class, source_note, meaning, label) VALUES')
rows = []
for (pid, nm, pcls, src, mean) in PATTERN_CATALOG:
    rows.append("('%s', '%s', '%s', '%s', '%s', '%s')" % (pid, nm, pcls, esc(src, 120), esc(mean), nm))
w(',\n'.join(rows) + ';')
w('')

# 9c. 女命细分名录（H3，lady_fortune_catalog；经 OBDA 暴露为 reading:LadyFortune）
w('-- ============================================================================')
w('-- 9c. 女命细分名录（%d；经 OBDA 暴露为 reading:LadyFortune）｜三命通会卷七·论女命；渊海子平卷三' % len(LADY_CATALOG))
w('-- ============================================================================')
w('INSERT INTO `lady_fortune_catalog` (lady_id, lady_name, category, source_note, meaning) VALUES')
rows = []
for (lid, nm, cat, src, mean) in LADY_CATALOG:
    rows.append("('%s', '%s', '%s', '%s', '%s')" % (lid, nm, cat, esc(src, 120), esc(mean)))
w(',\n'.join(rows) + ';')
w('')

# 9d. 技法/篇章名录（H4，technique_catalog；经 OBDA 暴露为 reading:ReadingItem）
w('-- ============================================================================')
w('-- 9d. 技法/篇章名录（%d；经 OBDA 暴露为 reading:ReadingItem）' % len(TECHNIQUE_CATALOG))
w('--   出处：渊海子平卷三/卷四、神峰通考技法篇、滴天髓·通神论（★8/9/10）。' % ())
w('-- ============================================================================')
w('INSERT INTO `technique_catalog` (tech_id, tech_name, category, source_note, meaning) VALUES')
rows = []
for (tid, nm, cat, src, mean) in TECHNIQUE_CATALOG:
    rows.append("('%s', '%s', '%s', '%s', '%s')" % (tid, nm, cat, esc(src, 120), esc(mean)))
w(',\n'.join(rows) + ';')
w('')

# 9e. 六十甲子性质吉凶（H4，sexagenary_nature；经 OBDA 追加至 core:SexagenaryCycle）
NATURE = sixty_nature()
w('-- ============================================================================')
w('-- 9e. 六十甲子性质吉凶（%d；经 OBDA 追加至 core:SexagenaryCycle）｜三命通会卷一·释六十甲子性质吉凶' % len(NATURE))
w('--   异名异法并陈；源本 OCR 讹字（辛卯木/丁酉火/己未火）已就地还原（★8/9/10）。' % ())
w('-- ============================================================================')
w('INSERT INTO `sexagenary_nature` (name, ganzhi, nature_wuxing, nature_text) VALUES')
rows = []
for k, r in enumerate(NATURE):
    nm_ = GANPY[k % 10] + ZHIPY[k % 12]
    rows.append("('%s', '%s', '%s', '%s')" % (nm_, r['ganzhi'], r['wuxing'], esc(r['text'], 400)))
w(',\n'.join(rows) + ';')
w('')

# 9f. 断语条文库·十干坐支/十二月支/五行时地/孕生男女（I3，auspice_clause；经 OBDA 暴露为 G1/G2 四类）
w('-- ============================================================================')
w('-- 9f. 断语条文库·十干坐支/十二月支/五行时地/孕生男女（%d；第 17 步 I3）' % len(AUSPICE_CLAUSES))
w('--   经 OBDA 暴露为 reading:StemBranchAuspice / MonthBranchDayStemAuspice / TimePlaceFortune / ProgenyPrediction。')
w('--   出处：三命通会卷四（论十干坐支兼得月时 / 论十二月支得日干 / 论五行时地分野）、卷七（定妇人孕生男女）。★8/9/10。')
w('-- ============================================================================')
w('INSERT INTO `auspice_clause` (clause_id, clause_group, subject, clause_text, source_note) VALUES')
rows = []
for (cid, grp, subj, txt, src) in AUSPICE_CLAUSES:
    rows.append("('%s', '%s', '%s', '%s', '%s')" % (cid, grp, esc(subj, 64), esc(txt, 1024), esc(src, 128)))
w(',\n'.join(rows) + ';')
w('')

# 9g. 断语条文库·病药/动静/盖头/六亲诸说（I3，theory_clause；经 OBDA 暴露为 G5/G7）
w('-- ============================================================================')
w('-- 9g. 断语条文库·病药说/动静说/盖头说/六亲说（%d；第 17 步 I3）' % len(THEORY_CLAUSES))
w('--   经 OBDA 暴露为 yongshen:BingYao / reading:DynamicStaticTheory / HeadCoveringTheory / KinshipTheory。')
w('--   出处：渊海子平卷之六（病药说/雕枯旺弱四病说/损益生长四药说/动静说/盖头说）、神峰通考（同及六亲说）。★8/9/10。')
w('-- ============================================================================')
w('INSERT INTO `theory_clause` (clause_id, clause_group, subject, clause_text, source_note) VALUES')
rows = []
for (cid, grp, subj, txt, src) in THEORY_CLAUSES:
    rows.append("('%s', '%s', '%s', '%s', '%s')" % (cid, grp, esc(subj, 64), esc(txt, 1024), esc(src, 128)))
w(',\n'.join(rows) + ';')
w('')

# 9h. 断语条文库·滴天髓通神论/六亲论诸篇（I3，dts_clause；经 OBDA 暴露为 G6 诸类）
w('-- ============================================================================')
w('-- 9h. 断语条文库·滴天髓通神论/六亲论诸篇（%d；第 17 步 I3）' % len(DTS_CLAUSES))
w('--   经 OBDA 暴露为 pattern:ImagePattern/DirectionPattern/ZhenDuiPattern/KanLiPattern/AssimilationPattern/')
w('--     SequencePattern/SovereignPattern、yongshen:IdleGod、reading:IlluminatingChapter、fortune:ZhenYuan。')
w('--   出处：滴天髓·通神论/六亲论（形象/方局/震兑/坎离/何知章/闲神/从象化象/顺反战合/君臣母子/贞元）。★8/9/10。')
w('-- ============================================================================')
w('INSERT INTO `dts_clause` (clause_id, clause_group, subject, clause_text, source_note) VALUES')
rows = []
for (cid, grp, subj, txt, src) in DTS_CLAUSES:
    rows.append("('%s', '%s', '%s', '%s', '%s')" % (cid, grp, esc(subj, 64), esc(txt, 1024), esc(src, 128)))
w(',\n'.join(rows) + ';')
w('')

# 9i. 赋/歌/诗诀/独步/诀/论/篇/说/图 条文库（第三轮 R3，fu_clause；经 OBDA 暴露为 reading:CategoryFortune）
w('-- ============================================================================')
w('-- 9i. 赋/歌/诗诀/独步/诀/论/篇/说/图 条文库（%d；第三轮 R3；经 OBDA 暴露为 reading:CategoryFortune）' % len(FU_CLAUSES))
w('--   全文照录（LONGTEXT 不截断）；出处：渊海子平卷一~卷七、神峰通考附录前段。★8/9/10。')
w('-- ============================================================================')
w('INSERT INTO `fu_clause` (clause_id, clause_group, subject, clause_text, source_note) VALUES')
rows = []
for (cid, grp, subj, txt, src) in FU_CLAUSES:
    rows.append("('%s', '%s', '%s', '%s', '%s')" % (cid, esc(grp, 16), esc(subj, 64), esc(txt, 100000), esc(src, 128)))
w(',\n'.join(rows) + ';')
w('')

# 9j. 子平真诠 47 篇正文断语库（第 23 步·正文级复核补录；ziping_clause；经 OBDA 暴露为 reading:ReadingItem）
w('-- ============================================================================')
w('-- 9j. 子平真诠 47 篇正文断语库（%d；第 23 步·正文级复核补录；经 OBDA 暴露为 reading:ReadingItem）' % len(ZIPING_CLAUSES))
w('--   缘起：正文级复核发现《子平真诠》47 篇正文整体未落库（其余五书均有专表承载）；')
w('--   客户裁定「新增子平真诠专表，逐篇录入 47 篇正文 + 出处行号」。')
w('--   clause_text 用 LONGTEXT 全文照录（不截断，最长约 1514 字）；出处：子平真诠·沈孝瞻原文。★8/9/10。')
w('-- ============================================================================')
w('INSERT INTO `ziping_clause` (clause_id, clause_group, subject, clause_text, source_note) VALUES')
rows = []
for (cid, grp, subj, txt, src) in ZIPING_CLAUSES:
    rows.append("('%s', '%s', '%s', '%s', '%s')" % (cid, esc(grp, 16), esc(subj, 64), esc(txt, 100000), esc(src, 128)))
w(',\n'.join(rows) + ';')
w('')

# 9k. 典籍正文全文库（第 26 步·第九轮正文内容级复核补齐；classic_text；经 OBDA 暴露为 reading:ReadingItem）
CLASSIC = classic_texts()
w('-- ============================================================================')
w('-- 9k. 典籍正文全文库（%d；第 26 步·第九轮正文内容级复核补齐；经 OBDA 暴露为 reading:ReadingItem）' % len(CLASSIC))
w('--   缘起：第九轮「正文内容级」复核（逐句比对全库）发现多书散文体正文大量未入本体；')
w('--   客户裁定「逐篇全文入库（六书不得有遗漏）」。滴天髓/穷通宝鉴/神峰通考/三命通会/渊海子平 逐篇正文，全文照录（不截断）。')
w('--   出处：design/六书全文整理/*.txt（客户归档文本，唯一真源）。★8/9/10。')
w('-- ============================================================================')
w('INSERT INTO `classic_text` (text_id, book_name, chapter, clause_text, source_note) VALUES')
rows = []
for (tid, bk, ch, txt, src) in CLASSIC:
    rows.append("('%s', '%s', '%s', '%s', '%s')" % (tid, esc(bk, 32), esc(ch, 128), esc(txt, 1000000), esc(src, 128)))
w(',\n'.join(rows) + ';')
w('')

# 10/11. 运行态
w('-- ============================================================================')
w('-- 10. 运行态命例记录｜case_record')
w('--     运行态数据（由 persistAndReturn 经 OBDA addComponent 落库），全集不含历史命例，留空。')
w('-- ============================================================================')
w('')
w('-- ============================================================================')
w('-- 11. 运行态告警标志｜alert_flag')
w('--     运行态数据（由流程按需落库），全集不含历史告警，留空。')
w('-- ============================================================================')
w('')

out = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..',
                   'OntologyFrameworkSiZhuBaZi', 'ontology', 'database', 'data.sql')
out = os.path.normpath(out)
with open(out, 'w', encoding='utf-8', newline='\n') as f:
    f.write('\n'.join(L))

print('written:', out)
print('counts: stem=%d branch=%d cycle=%d nayin=%d hidden=%d term=%d termtime=%d month=%d deity=%d dayhour=%d tiaohou=%d tengod=%d pattern=%d lady=%d tech=%d nature=%d auspice=%d theory=%d dts=%d fu=%d ziping=%d classic=%d' % (
    10, 12, 60, len(NAYIN), sum(len(v) for v in ZHI_HIDDEN.values()), len(TERMS), len(SOLAR_TERM_TIME_ROWS), 12,
    len(DEITY_RULES), len(DHC), len(THC), 100,
    len(PATTERN_CATALOG), len(LADY_CATALOG), len(TECHNIQUE_CATALOG), len(NATURE),
    len(AUSPICE_CLAUSES), len(THEORY_CLAUSES), len(DTS_CLAUSES), len(FU_CLAUSES), len(ZIPING_CLAUSES), len(CLASSIC)))
