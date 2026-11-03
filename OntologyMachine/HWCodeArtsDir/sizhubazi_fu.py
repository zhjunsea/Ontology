# -*- coding: utf-8 -*-
"""
第 18 步（第三轮 R3 内容层补录）：为渊海子平卷三~卷七 + 神峰通考附录的成篇「赋 / 歌 / 诗诀 / 独步 / 诀 / 论 / 篇」
建条文库并全文落库（fu_clause，LONGTEXT 不截断）。

口径同第 15/17 步（day_hour_clause / tiaohou_clause / auspice_clause / theory_clause / dts_clause）：
  大条文库经 MySQL + Ontop OBDA 外置，本体只建类与出处（★铁律 13/14：DB 为唯一真源）。

经 OBDA 暴露为 reading:CategoryFortune（reading:readingCategory 记「赋/歌/诗诀/…」区分）。

★8/9/10 禁编造：clause_text 一律**照录**典籍正文（由源文本按「未知」边界/篇目行号机械抽取），
source_note 逐条指到篇名与整理本行号。每条：(clause_id, clause_group, subject, clause_text, source_note)。

来源：
  渊海子平 —— design/六书全文整理/渊海子平.txt（篇目边界＝行内「未知」标记的下一行）
  神峰通考 —— design/六书全文整理/神峰通考.txt（篇目行号见 _SF_TARGETS）
"""
import os
import re

_BASE = os.path.normpath(os.path.join(
    os.path.dirname(os.path.abspath(__file__)), '..',
    'OntologyFrameworkSiZhuBaZi', 'design', '六书全文整理'))
_YH = os.path.join(_BASE, '渊海子平.txt')
_SF = os.path.join(_BASE, '神峰通考.txt')
_SMTH = os.path.join(_BASE, '三命通会.txt')


def _read(fn):
    with open(fn, encoding='utf-8') as f:
        return f.read().split('\n')


def _clean(t):
    return re.sub(r'[（(].*?[)）]', '', t).strip()


def _group(title):
    c = _clean(title)
    ov = {'崖泉摘锦': '赋', '巫咸撮要': '论', '女命大义': '论', '渊源集说': '论',
          '一行禅师天元赋注解': '赋', '渊源集说(神峰)': '论'}
    if title in ov:
        return ov[title]
    for suf, g in (('诗诀', '诗诀'), ('独步', '独步'), ('赋', '赋'),
                   ('歌', '歌'), ('诀', '诀'), ('论', '论'), ('篇', '篇'),
                   ('说', '说'), ('图', '图')):
        if c.endswith(suf):
            return g
    return '文'


# ---------------------------------------------------------------------------
# 渊海子平：篇目边界 = 行内「未知」下一行；卷次由卷首行判定
# ---------------------------------------------------------------------------
_YH_VOL = [('卷之一', 6), ('卷之二', 595), ('卷之三', 1316), ('卷之四', 1822),
           ('卷之五', 2192), ('卷之六', 2969), ('卷之七', 4334)]
_YH_TARGETS = [
    # 卷三（赋/歌/论）
    '六亲捷要歌', '阴命赋', '女命总断歌', '寸金搜髓论(搜髓歌)', '心镜歌(心镜五七赋)',
    '妖祥赋', '络绎赋', '相心赋', '玄机赋', '幽微赋', '五行元理消息赋',
    # 卷四（赋/论/诀/独步 + 十二月候诗诀）
    '金玉赋', '碧渊赋(元妙论)', '造微论', '人鉴论', '爱憎赋', '万金赋(明津先生骨髓歌)',
    '掣要捷驰玄妙诀(此集诸家口诀)', '渊源集说', '子平百章歌', '四言独步', '身弱论',
    '弃命从杀论', '五言独步',
    '正月建寅候诗诀', '二月建卯候诗诀', '三月建辰候诗诀', '四月建巳候诗诀', '五月建午候诗诀',
    '六月建未候诗诀', '七月建申候诗诀', '八月建酉候诗诀', '九月建戌候诗诀', '十月建亥候诗诀',
    '十一月建子候诗诀', '十二月建丑候诗诀',
    # 卷五（各格/运限诗诀 + 赋）
    '正官诗诀', '偏官诗诀(七杀同)', '印绶诗诀', '正财诗诀', '偏财诗诀', '食神诗诀', '伤官诗诀',
    '羊刃诗诀(劫财)', '刑合诗诀', '日贵诗诀', '金神诗诀', '日德诗诀', '魁罡诗诀', '时墓诗诀',
    '杂气财官诗诀', '子遥巳诗诀', '丑遥巳格诗诀', '壬骑龙背诗诀', '井栏叉格诗诀', '归禄格诗诀',
    '六阴朝阳诗诀', '拱禄拱贵诗诀', '六甲趋干诗诀', '六壬趋艮诗诀', '勾陈得位诗诀', '玄武当权诗诀',
    '润下诗诀', '从革诗诀', '稼穑诗诀', '曲直格诗诀', '炎上格诗诀', '福德格诗诀', '弃命从财诗诀',
    '弃命从杀诗诀', '杀重有救诗诀', '天元一气诗诀', '化气诗诀', '天元一字诗诀', '刑冲诗诀',
    '克妻诗诀', '克子诗诀', '运晦诗诀', '运通诗诀', '带疾诗诀', '寿元诗诀', '飘荡诗诀', '女命诗诀',
    '长生诗诀', '沐浴诗诀', '冠带诗诀', '临官诗诀', '帝旺诗诀', '衰病死诗诀', '墓库诗诀', '胞胎诗诀',
    '胎养诗诀',
    '五行生克赋', '珞琭子消息赋', '论八字撮要法', '会命要说',
    # 卷六（赋/诀/篇/论）
    '玉井奥诀', '气象篇', '看命口诀', '巫咸撮要', '元理赋', '明通赋', '六神篇', '金声玉振赋',
    '一行禅师天元赋注解',
    # 卷七（赋/篇/论）
    '崖泉摘锦(崖泉男女命赋)', '渭泾篇', '女命大义', '总歌', '讲命捷径赋', '金鼎神秘赋',
    # 卷三~七 散体短论/说/图（第三轮复核追加；客户裁定 2026-10-30「彻底无遗漏」）
    '六亲总论', '男命六亲取要图', '女命六亲取要图', '论父', '论母', '论妻妾', '论兄弟姐妹',
    '论子息', '论妇人总诀', '女命富贵贫贱篇', '女命贵格', '女命贱格', '滚浪桃花',
    '论小儿', '论小儿关杀例', '论性情', '论疾病', '论大运', '论太岁吉凶', '论运化气',
    '化气十段锦', '神趣八法有类属从化返照鬼伏', '论格局生死引用', '论征太岁', '杂论口诀',
    '群兴论', '论兴亡', '宝法第一', '宝法第二', '论命细法', '伤官说',
    '十干体象', '十二支体象',
    '病药说', '雕枯旺弱四病说类', '损益生长四药说类', '动静说', '盖头说',
    # 卷一/卷二 零星成篇条目（客户裁定 2026-10-30「一并纳入·彻底零遗漏」）
    '论五行所生之始', '天降干支图', '掌诀', '五行发用定例', '月律分野之图',
    '天干五阳通变', '天干五阴通变', '又论五行生旺衰绝吉凶', '论五行墓库财印',
    '二至阴阳相生理', '子平举要歌', '详解定真论', '继善篇',
]


def _yh_vol(line):
    v = '卷之一'
    for nm, ln in _YH_VOL:
        if line >= ln:
            v = nm
    return v


def _build_yh():
    lines = _read(_YH)
    bnd = []
    for i, l in enumerate(lines):
        if l.strip() == '未知' and i + 1 < len(lines):
            bnd.append((i + 2, lines[i + 1].strip()))
    bnd.append((len(lines) + 1, '__EOF__'))
    pos = {t: ln for (ln, t) in bnd}
    out = []
    for k, title in enumerate(_YH_TARGETS, 1):
        if title not in pos:
            raise SystemExit('渊海子平缺篇目: ' + title)
        ln = pos[title]
        nxt = len(lines) + 1
        for (b, _t) in bnd:
            if b > ln:
                nxt = b
                break
        seg = [x.strip() for x in lines[ln:nxt - 1]]
        seg = [x for x in seg if x and x != '未知']
        txt = ' '.join(seg)  # 单行存库（与 data.sql 一行一记录惯例一致；仅空白归一，不丢字符）
        if not txt:
            # 图类无正文（原书为图示），按客户裁定以名录/文字形式收录
            txt = '【图录】%s（原书为图，整理本正文无对应文字；以名录收录）' % _clean(title)
        cid = 'FU_YH_%03d' % k
        src = '渊海子平%s·%s（整理本第%d行）' % (_yh_vol(ln), _clean(title), ln)
        out.append((cid, _group(title), title, txt, src))
    return out


# ---------------------------------------------------------------------------
# 神峰通考（附录）：篇目行号（grep 核定，1-based）
# ---------------------------------------------------------------------------
_SF_TARGETS = [
    ('子平泛论', 1090), ('十干从化定诀', 1096), ('五阴歌', 1141), ('天元一字歌', 1145),
    ('运晦歌', 1149), ('运通歌', 1153), ('刑克歌', 1157), ('刑妻歌', 1161), ('克子歌', 1166),
    ('带疾歌', 1168), ('寿元歌', 1170), ('飘荡歌', 1172), ('女命歌', 1174), ('看命捷歌', 1199),
    ('论诸格有救', 1203),
    ('万尚书琼玑三盘赋', 1213), ('崖泉男命赋', 1226), ('崖泉女命赋', 1232), ('喜忌篇', 1257),
    ('继善篇', 1287), ('六神篇', 1356), ('气象篇', 1364), ('渭泾论', 1395), ('定真篇', 1432),
    ('五行元理消息赋', 1442), ('五行生克赋', 1488), ('一行禅师天元赋', 1507), ('捷驰千里马赋', 1540),
    ('络绎赋', 1545), ('玄机赋', 1552), ('憎爱赋', 1554), ('万金赋', 1558), ('相心赋', 1563),
    ('仙机赋', 1567), ('金玉赋', 1571), ('人鉴论', 1573), ('渊源集说', 1577), ('妖祥赋', 1579),
    ('幽微天干赋', 1586), ('人元消息赋', 1599), ('地支赋', 1610),
]


def _build_sf():
    lines = _read(_SF)
    out = []
    for k, (title, ln) in enumerate(_SF_TARGETS, 1):
        nxt = len(lines) + 1
        for (_t, b) in _SF_TARGETS:
            if b > ln:
                nxt = b
                break
        seg = [x.strip() for x in lines[ln - 1:nxt - 1]]
        seg = [x for x in seg if x]
        txt = ' '.join(seg)  # 单行存库（与 data.sql 一行一记录惯例一致；仅空白归一，不丢字符）
        cid = 'FU_SF_%03d' % k
        src = '神峰通考·%s（整理本第%d行）' % (_clean(title), ln)
        out.append((cid, '论' if title == '渊源集说' else _group(title), title, txt, src))
    return out


# 神峰通考前段（<1090，无「未知」标记）：显式行区间 (title, start, end)，1-based 含端点
_SF_PRE = [
    ('伤官十论', 213, 214),
    ('五星论', 549, 557),
]


def _build_sf_pre():
    lines = _read(_SF)
    out = []
    for k, (title, s, e) in enumerate(_SF_PRE, len(_SF_TARGETS) + 1):
        seg = [x.strip() for x in lines[s - 1:e]]
        seg = [x for x in seg if x]
        txt = ' '.join(seg)  # 单行存库（与 data.sql 一行一记录惯例一致；仅空白归一，不丢字符）
        cid = 'FU_SF_%03d' % k
        src = '神峰通考·%s（整理本第%d行）' % (_clean(title), s)
        out.append((cid, _group(title), title, txt, src))
    return out


# ---------------------------------------------------------------------------
# 第 20 步（第五轮全量扫描 P5/P8 补录）：三命通会·附论墓运（论）＋神峰通考二图（图录）
#   来源：三命通会.txt 第 10481~10486 行（卷六）；神峰通考.txt 第 714~985 / 986~1032 行
#   图类原书为图，整理本以文字碎片存，此处【图录】照录不编造（★8/9/10）。
# ---------------------------------------------------------------------------
_FU_EXTRA = [
    # (clause_id, clause_group, title, file, start, end, vol_label)  1-based 含端点
    ('FU_SMTH_MuYun', '论', '附论墓运', _SMTH, 10481, 10486, '三命通会卷六'),
    ('FU_SF_YangShunYinNiTu', '图', '阳顺阴逆生旺死绝图', _SF, 714, 985, '神峰通考'),
    ('FU_SF_ZhiZaoTu', '图', '地支造化图', _SF, 986, 1032, '神峰通考'),
]


def _build_extra():
    out = []
    for (cid, grp, title, fn, s, e, vol) in _FU_EXTRA:
        lines = _read(fn)
        seg = [x.strip() for x in lines[s - 1:e]]
        seg = [x for x in seg if x]
        txt = ' '.join(seg)
        if grp == '图':
            txt = '【图录】原书为图、整理本文字碎片照录：' + txt
        src = '%s·%s（整理本第%d行）' % (vol, _clean(title), s)
        out.append((cid, grp, title, txt, src))
    return out


FU_CLAUSES = _build_yh() + _build_sf() + _build_sf_pre() + _build_extra()


if __name__ == '__main__':
    import sys
    import io
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')
    tot = sum(len(c[3]) for c in FU_CLAUSES)
    print('FU_CLAUSES =', len(FU_CLAUSES), ' total chars =', tot)
    grp = {}
    for c in FU_CLAUSES:
        grp[c[1]] = grp.get(c[1], 0) + 1
    print('groups:', grp)
    for c in FU_CLAUSES:
        print('%s  [%s]  %-22s  len=%d' % (c[0], c[1], c[2], len(c[3])))
