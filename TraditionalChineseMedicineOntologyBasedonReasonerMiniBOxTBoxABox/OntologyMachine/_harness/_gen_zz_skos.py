# -*- coding: utf-8 -*-
"""
生成 tcm-zhengzhuang_skos.ttl
—— 症状个体配套 SKOS 词表（同义词 / 习惯用法 / 缩写 / 白话）
参照 D:/work/Ontology/TraditionalChineseMedicineOntologyBaseonCode/ontology/zhengzhuangtizheng_skos.ttl 的建模约定。
"""
import json, io, sys, re
sys.path.insert(0, r"D:/work/Ontology/TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox/OntologyMachine/_harness")
from _zz_skos_data import SYN
from _mlfz_skos_data import SYN as MLFZ_SYN

BASE = r"D:/work/Ontology/TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox/ontology"
HARN = r"D:/work/Ontology/TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox/OntologyMachine/_harness"
DUMP = HARN + "/_zz_dump.json"
MLFZ_DUMP = HARN + "/_mlfz_dump.json"
OUT  = BASE + "/tcm-zhengzhuang_skos.ttl"

d = json.load(io.open(DUMP, encoding="utf-8"))
cls, ind = d["cls"], d["ind"]

# 个体 → 基名
bases = []
for k, v in ind.items():
    if k.endswith("_instance"):
        bases.append(k[:-len("_instance")])
baseset = set(bases)
# 保持本体中的出现顺序（ind 是 dict，Python3.7+ 保序）
order = bases

# 抽象类（八纲/六经等，非本模块症状）——不作为 broader
ABSTRACT = {"Zhengzhuang", "Biao", "Li", "Yin", "Yang", "Xu", "Shi", "Re", "Hanxiang",
            "Xiaobian", "ZhengzhuangLei"}

# TBox 有类、ABox 无个体的「孤儿类」（同义词高发区，需补齐 ABox 个体）
orphans = [k for k in cls if k not in baseset and k not in ABSTRACT]

def esc(s):
    return s.replace("\\", "\\\\").replace('"', '\\"')

def ttl_list(items, indent="        "):
    return ",\n".join('%s"%s"@zh' % (indent, esc(x)) for x in items)

# ---------- 近似同义簇（skos:closeMatch） ----------
CLUSTERS = [
    ["Shouzujueni","Shouzujueleng","Shouzunileng","Sizhinileng","Shouzuleng","Jueni","Hanqijueni"],
    ["Shentong","ShenTengtong","Yishenjinteng","Shentengfan"],
    ["Gujietong","Gujietengtong","Gujietengfan","Baijietengtong","Zhijietong","Guanjietengtong","Zhuzhijietengtong","Sizhilijietong"],
    ["Wanglaihanre","HanreWanglai","Xiuzuoyoushi","Runvezhuang","Yirizaifa"],
    ["Chaore","Ripuchaore","Ribusuofare"],
    ["Dabiannan","Dabianying","Dabianmijie","Budabian","Bugengyi","Zaoshi"],
    ["Xiaobianbuli","Xiaobiannan","Xiaobianshu","Xiaobianqing","Xiaobiansebai","Xiaobianhuang","Xiaobianchi","Xiaobianhuangchi"],
    ["Danyumei","Shiwo","Juanwo","Jingshenweimi","Duomianshui"],
    ["Xiali","Reli","Xialiqinggu","Wanguobuhua","Gubuhua","Xialibuzhi","Xialinongxue","Jiuli","Ziliyishen","Xialiqi"],
    ["Xinxiapi","Xinxiapiying","Xinxiapijian","Xinzhongpi"],
    ["Xiongxiekuman","Xiongxiekutong","Xiongxiezhiman","Xietong","Xiexiaman","Xiexiapiying"],
    ["Fuman","Fumantong","FumanJuAn","Shaofuman","Furenshaofuman"],
    ["Hanchu","Zihan","Duohanchu","Dahan"],
    ["Ehan","WeiHan","Weihan","Shenhan","Shenduohan"],
    ["Fare","Dare","Shenre","Weire","Shenzhuore","Fanre","Mianre","Shitoure"],
    ["Kouku","Yangan","Aigan"],
    ["Outu","Ou","Tu","Yuou","Yutu","Ganou","Weiou","Xiou","Ouni"],
    ["Kesou","Ketuo","Keni","Chuan","Shangqi","Duanqi","Shaoqi"],
    ["Shuizhong","Shentizhong","Sizhizhong","Jiaozhong","Yishenxizhong","Xingzhong","Mianfu","Shenweizhong"],
    ["Huangdan","Shenhuang","Zhuhuang","Muhuang","Jiuhuangdan","Nvlaodan"],
    ["Yijing","Mengshijing","Shijingjia"],
    ["Yinyang","Yinzhong","Yinzhongtong","Yinchuang","Yinzhongshichuang","Yinzhongjishengchuang","Yinzhongshichuanglanzhe","Yinchetong","Yinxiashi","Yinchui","Yintouhan","Furenyinhan"],
    ["Zhengkuai","Shaofuzhengkuai","Xiexiazhengjia","Nvemu","Darupan"],
    ["Changyudaoqixiongshang"],
    ["Hanshanraoqitong","Raoqihanshan","Raoqitong","Hanshanfutong"],
    ["Shaofuman","Shaofumantong","Furenshaofuman","Furenshaofumanrudunzhuang"],
    ["Jingduan","Jingshuibuli","Jingyiyuezaijian","Jingshuishilai","Jingshuishiduan","Jingduanweijisanyue"],
    ["Xiaxue","Bianxue","Louxia","Xianbianhoubianxue","Xianxuehoubian","Banchanhouxiaxuebuduan","Renshenxiaxue"],
    ["Tunong","Tunongxue","Tunongrumizhou"],
    ["Yantong","Yanzhongtong"],
    ["Kouzao","Kousheganzao","Chunkouganzao","Kouzhongpipizao","Qianbanchizao","Duotuokouzao"],
    ["Xinfan","Weifan","Yuyuweifan","Xufan","Fanman","Fanluan","Xiongzhongfan"],
    ["Fanzao","Fanzaoyusi","Rukuang","Fakuang","Jingkuang","Rukuangzhuang"],
    ["Zhanyu","Zhengsheng","Duyubuxiu"],
    ["Jiexiong","Hanshijiexiong"],
    ["Tanduo","Tanduoqingxi"],
    ["Feiyong","Feitong"],
    ["Jingbing","Jingxiangqiangji","Beifanzhang","Zukoujin","Xiechi","Wobuzhexi","Dutoudongyao"],
    ["Xulao","Xulaoyaotong","Xulaoxufanbudemian","Wulaoxuji","Xulei","Xuleishaoqi","Zhubuzu","Fengqibaiji"],
    ["Mianchi","Mianre","Mianseweihuang","Mianseqing","Miansebai","Mianselihei","Miansewuhua","Eshanghei"],
    ["Muchi","Muxuan","Muming","Muhuang","Muteng","Murundong","Muchirujiuyan","Muzhongbulele","Jingbuhe","Murutuozhuang","Liangmuanhei","Muqizichu","Mukeshangweiyong","Muxiayouwocan","Mubudebi"],
    ["Toutong","Touxuan","Touzhong","Touxiangqiangtong","Qizetouxuan","Kumaoxuan"],
    ["Xiangqiang","Beiqiang","Xiangbeiqiangjiji","Shentiqiangjiji"],
    ["Erlong","Erqianhouzhong","Biming","Binv","Bigan"],
    ["Kouke","Dake","Buke","Fanbuke","Yuyin","Buyuyin","Xiaoke","Danyuyinre"],
    ["Qishangchong","Qishangchongxiong","Qishangchongxiongyan","Qicongshaofushangchongxin","Qicongshaofushangchongxiongyan","Qishangzhuangxin","Yuzuobentun","Qidongzhe"],
    ["Xinxiaji","Xinxiaman","Xinxianiman","Xinxiazhijie","Xinxiaanzhimantong","Xinxiaodutong","Xinxiakuan","Xinxiajian"],
    ["Xiongman","Xiongzhongzhi","Xiongzhongqisai","Xiongzhongpiying","Xiongzhongyoure","Xiongzhongfan","Xiongzhongjiacuo","Xiongtong","Xiongbi","Xiongbihuanji","Xiongzhongtong","Xiongzhongkui","Xiongxiemanweijie"],
    ["Xintong","Xintongchebei","Beitongchexin","Xinxuantong","Xintongfazuoyoushi","Xinxiongdahantong"],
    ["Futong","Fuzhonghan","Fuzhongjiaotong","Fuzhongleiming","Fuzhongjitong","Shifuzitong","Shitong"],
    ["Changming","Changmingruzoushui","Shuizouchangjianliliyousheng"],
    ["Shaofutong","Shaofuxianji","Shaofujuji","Shaofuliji","Shaofujijie","Shaofujiantong","Shaofuyingman","Shaofuehan","Shaofuzhongpi"],
    ["Tuxian","Tuxianmo","Tuzhuo","Tuxue","Tuxuebuzhi","Tuhui","Shishituzhuo"],
    ["Keershangqi","Huonishangqi","Houzhongshuijisheng"],
    ["Xinji","Xinxiajidong","Qixiaji","Xuanji","Chashouzimaoxin","Yudean"],
    ["Xinzhongaonao"],
    ["Jing","Kong","Shanwang","Xunyimochuang","Tierbuan","Rujianguizhuang","Zhishi","Shisou","Chizong","Wozejing","Yanyujianse","Wangxing","Changmoran","Momo","Momoyumian","Momobuyushiyin"],
    ["Yiyushifubunengshi","Yuwobunengwo","Yuxingbunengxing","Ruhanwuhan","Rurewure","Mianmuzhachizhaheizhabai","Yanhuxuanmao","Zhuangrujuedian"],
    ["Furenzangzao","Xibeishangyuku","Beishang","Xiangrushenlingsuozuo","Shuqianshen"],
    ["Zhuanjin","Jiaoluanji","Sizhiweiji","Shouzujuji","Renbijiaozhi","Bukequshen","Chetongbudequshen"],
    ["Shenrundong","Jintirourun","Sizhinieniedong","Murundong","Zhenzhenshenrunju","Shenweizhenzhenyao","Zhenzhenyupidi"],
    ["Yaotong","Yaozhongleng","Yaoyixiayoushuiqi","Xulaoyaotong","Fuzhongrudaiwuqianqian","Ruzuoshuizhong"],
    ["Jinyinchuang","Jinchuang","Younong","Shenyouchuang"],
    ["Renshen","Renshenxiaxue","Renshenoutubuzhi","Renshenyoushuiqi","Furensuyouzhengbing","Taidong","Taidongzaiqishang","Banchan"],
    ["Chanhoufutong","Chanhouxiali","Chanhouzhongfeng","Elubujin","Shenghouzhe"],
    ["Daixia","Xiabaiwu"],
    ["Yinhushan","Pianyouxiaoda","Shishishangxia"],
    ["Huanghan","Hanzhanyi","Sezhenghuangrubaizhi","Shenseruxunhuang"],
    ["Pishui","Lishui","Shuizhiweibing","Fuzhonganzhimeizhi","Yishenmianmuhuangzhong"],
    ["Zhiyinxiongman","Xinxiayouzhiyin","Gejianzhiyin","Gejianyoushui","Budewo"],
    ["Beihanlengruzhangda","Beihanleng","Beiehan"],
    ["Qini","Shangqi","Budexi"],
    ["Buyushi","Shibuxia","Bunengyinshi","Bunengshiyin","Ewenshichou"],
    ["Nengshi","Yinshirugu","Xiaogushanji","Yinshihuoyoumeishi","Ji","Xinzhongji"],
    ["Tunsuan","Aifu","Yiqi","Ganyishichou","Hui","Ye"],
    ["Sushi","Weizhongbuhe","Weizhongyouxieqi","Xinzhongpiqi","Weili"],
    ["Nvxue","Binv","Wangxue","Niaoxue","Nongxue"],
    ["Jifujiacuo","Liangmuanhei","Citong","Danyushushuibuyuyan","Chunan","Xiongzhongjiacuo"],
    ["Xingtisunfen","Shentiwanglei"],
    ["Wangxue","Lizhi"],
    ["Ganzhe"],
    ["Feiweituxianmo"],
    ["Huichong","Tuhui","Shizetuhui","Xinzhongtengre"],
    ["Koukai","Shejinanyan","Koutuxian","Danbibusui","Maibuchuzhe"],
    ["Jiaozhongrutuo","Xijingtengfan","Jinzhizetongju"],
    ["Shenzhong","Ribusuoju"],
    ["Miangou"],
    ["Laofu","Dabingchaihou"],
    ["Tuli"],
    ["Shengbuchu","Budeyu","Shenghe"],
]

# 校验簇内 fragment 均存在
for c in CLUSTERS:
    for f in c:
        if f not in baseset:
            print("WARN cluster fragment not found:", f, file=sys.stderr)

close = {}
for c in CLUSTERS:
    valid = [f for f in c if f in baseset]
    if len(valid) < 2:
        continue
    for f in valid:
        close.setdefault(f, set()).update(x for x in valid if x != f)

# ---------- broader / narrower ----------
broader, narrower = {}, {}
for b in order:
    sups = [s for s in cls.get(b, {}).get("sup", []) if s in baseset and s not in ABSTRACT]
    if sups:
        broader[b] = sups
        for s in sups:
            narrower.setdefault(s, []).append(b)

# ---------- 顶层概念 ----------
tops = [b for b in order if b not in broader]

# ---------- 表面形式消歧 ----------
# 规则（保证「一个表面形式 → 唯一规范概念」，匹配才确定）：
#   P1 优先级：prefLabel > altLabel > hiddenLabel
#   P2 若某表面形式恰是另一概念的 prefLabel，则从本概念的 alt/hid 中删除
#   P3 同级（alt/hid）跨概念重复时，只保留在本体顺序最靠前的概念上
all_pref = {}
for b in order:
    all_pref.setdefault(ind[b + "_instance"]["label"], []).append(b)
for b in orphans:
    all_pref.setdefault(cls[b]["label"], []).append(b)

dup_pref = {k: v for k, v in all_pref.items() if len(v) > 1}
if dup_pref:
    print("!! 重复 prefLabel（本体存在同名类/个体）:", file=sys.stderr)
    for k, v in dup_pref.items():
        print("   ", k, "->", v, file=sys.stderr)

def resolve(b, lab, alt, hid):
    """按 P2/P3 消歧，返回 (alt, hid)。"""
    alt = [x for x in alt if x not in all_pref or all_pref[x] == [b]]
    hid = [x for x in hid if x not in all_pref or all_pref[x] == [b]]
    return alt, hid

# 先做 P2，再统计同级重复
_tmp = {}
for b in order + orphans:
    lab = ind[b + "_instance"]["label"] if b in baseset else cls[b]["label"]
    s = SYN.get(b, {})
    a = [x for x in s.get("alt", []) if x and x != lab]
    h = [x for x in s.get("hid", []) if x and x != lab and x not in a]
    _tmp[b] = resolve(b, lab, a, h)

_seen_alt, _seen_hid = {}, {}
for b in order + orphans:
    a, h = _tmp[b]
    keep_a = []
    for x in a:
        if x in _seen_alt and _seen_alt[x] != b:
            print("   [alt 冲突] %s 保留于 %s，从 %s 移除" % (x, _seen_alt[x], b), file=sys.stderr)
            continue
        _seen_alt[x] = b; keep_a.append(x)
    _tmp[b] = (keep_a, h)

# P1 跨级：hiddenLabel 不得与任何概念的 altLabel 重复（alt 优先）
for b in order + orphans:
    a, h = _tmp[b]
    own = set(a)
    keep_h = []
    for x in h:
        if x in _seen_alt and x not in own:
            print("   [hid<alt 降级] %s 保留于 alt(%s)，从 hid(%s) 移除" % (x, _seen_alt[x], b), file=sys.stderr)
            continue
        if x in _seen_hid and _seen_hid[x] != b:
            print("   [hid 冲突] %s 保留于 %s，从 %s 移除" % (x, _seen_hid[x], b), file=sys.stderr)
            continue
        _seen_hid[x] = b; keep_h.append(x)
    _tmp[b] = (a, keep_h)

# ---------- 输出 ----------
L = []
L.append("@prefix skos: <http://www.w3.org/2004/02/skos/core#> .")
L.append("@prefix zzskos: <http://www.tcm-classics.org/skos/zhengzhuang#> .")
L.append("@prefix zz: <http://www.tcm-classics.org/jingfang#> .")
L.append("@prefix dct: <http://purl.org/dc/terms/> .")
L.append("@prefix xsd: <http://www.w3.org/2001/XMLSchema#> .")
L.append("@prefix owl: <http://www.w3.org/2002/07/owl#> .")
L.append("@prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .")
L.append("")
L.append("<http://www.tcm-classics.org/skos/zhengzhuang> a owl:Ontology ;")
L.append('    owl:versionInfo "1.0" ;')
L.append('    rdfs:label "症状 SKOS 词表（tcm-zhengzhuang.owl 配套）"@zh ;')
L.append('    rdfs:comment "与 tcm-zhengzhuang.owl / tcm-zhengzhuang-abox.owl 配套。每个 skos:Concept 通过 skos:exactMatch 指向 ABox 个体（#<Base>_instance），通过 zz:classMatch 指向 TBox 类（#<Base>），skos:notation 记录个体 fragment。"@zh .')
L.append("")
L.append("zzskos: a skos:ConceptScheme ;")
L.append('    dct:title "症状SKOS词汇表（经方·原子症状同义映射版）"@zh ;')
L.append('    dct:title "症状·四诊SKOS词汇表（经方·原子症状同义映射版）"@zh ;')
L.append('    dct:description "覆盖 %d 个症状个体（另含脉象/舌象/腹证四诊通道）的同义词、缩写、白话、经方医案常用表述，支持任意入口定位至标准URI。'
         '依据《伤寒论》《金匮要略》《濒湖脉学》及胡希恕学术思想。skos:prefLabel 为本体规范名；skos:altLabel 为经方/文言同义词；'
         'skos:hiddenLabel 为患者白话口语；skos:closeMatch 标注近义/重叠症状；skos:broader/narrower 取自本体子类关系。"@zh ;'
         % len(order))
L.append("    skos:hasTopConcept " + ",\n        ".join("zzskos:" + b for b in tops) + " .")
L.append("")

# 按本体顺序输出概念
for i, b in enumerate(order):
    lab = ind[b + "_instance"]["label"]
    s = SYN.get(b, {})
    definition = s.get("definition") or cls.get(b, {}).get("comment") or ""
    alt2, hid2 = _tmp[b]
    note = s.get("note", "")

    L.append("zzskos:%s a skos:Concept ;" % b)
    L.append("    skos:inScheme <http://www.tcm-classics.org/skos/zhengzhuang> ;")
    L.append('    skos:prefLabel "%s"@zh ;' % esc(lab))
    if definition:
        L.append('    skos:definition "%s"@zh ;' % esc(definition))
    if alt2:
        L.append("    skos:altLabel " + ttl_list(alt2) + " ;")
    if hid2:
        L.append("    skos:hiddenLabel " + ttl_list(hid2) + " ;")
    if note:
        L.append('    skos:note "%s"@zh ;' % esc(note))
    if b in broader:
        L.append("    skos:broader " + ",\n        ".join("zzskos:" + x for x in broader[b]) + " ;")
    if b in narrower:
        L.append("    skos:narrower " + ",\n        ".join("zzskos:" + x for x in narrower[b]) + " ;")
    if b in close:
        L.append("    skos:closeMatch " + ",\n        ".join("zzskos:" + x for x in sorted(close[b])) + " ;")
    L.append('    skos:notation "%s_instance" ;' % b)
    # 双锚点：exactMatch → ABox 个体（匹配引擎 / getSynonymsByOwlIndividual 的落点）；
    #        classMatch → TBox 类（推理机按类做子类 / 一致性推理的落点）。
    L.append("    skos:exactMatch zz:%s_instance ;" % b)
    L.append("    zz:classMatch zz:%s ." % b)
    L.append("")

# ---------- 附加：TBox 有类、ABox 无个体的「孤儿类」 ----------
# 这些类是同义词高发区（寒热往来/大便秘结/小便黄赤…），但 ABox 缺个体，
# 目录（按 ABox 解析）无法命中。此处仍建概念并显式标注缺口，便于后续补齐 ABox。
if orphans:
    L.append("### ========== 附：TBox 有类、ABox 缺个体的症状（待补 ABox） ==========")
    L.append("")
    for b in orphans:
        lab = cls[b]["label"]
        s = SYN.get(b, {})
        definition = s.get("definition") or cls[b].get("comment") or ""
        alt2, hid2 = _tmp[b]
        L.append("zzskos:%s a skos:Concept ;" % b)
        L.append("    skos:inScheme <http://www.tcm-classics.org/skos/zhengzhuang> ;")
        L.append('    skos:prefLabel "%s"@zh ;' % esc(lab))
        if definition:
            L.append('    skos:definition "%s"@zh ;' % esc(definition))
        if alt2:
            L.append("    skos:altLabel " + ttl_list(alt2) + " ;")
        if hid2:
            L.append("    skos:hiddenLabel " + ttl_list(hid2) + " ;")
        sups = [x for x in cls[b].get("sup", []) if x in baseset]
        if sups:
            L.append("    skos:broader " + ",\n        ".join("zzskos:" + x for x in sups) + " ;")
        L.append('    skos:note "ABox 缺个体：tcm-zhengzhuang-abox.owl 中无 #%s_instance，'
                 'SymptomCatalog（按 ABox 解析）无法命中此症状，需补齐个体后方可参与匹配。"@zh ;' % b)
        L.append("    zz:classMatch zz:%s ." % b)
        L.append("")

# ---------- 附加：脉象 / 舌象 / 腹证（四诊其余通道） ----------
# 目的：让「表面形式 → 规范名」的全部映射都以 SKOS 为唯一权威来源，
#       SymptomCatalog / SymptomMappingService 不再保留任何硬编码同义词表。
# 规则化推导（非臆造）：
#   脉象：规范名以「脉」结尾 → 口语「脉X」（弦脉 ↔ 脉弦）
#   舌象：规范名以「苔」结尾且不以「舌」开头 → 口语「舌苔X」（黄苔 ↔ 舌苔黄）
# 其余（舌体、腹证、特例）取自 _mlfz_skos_data.py 的人工校订表。
mlfz = json.load(io.open(MLFZ_DUMP, encoding="utf-8"))

# 已被「症状」通道占用的表面形式 —— 症状优先，四诊其余通道不得抢占
_used = set()
for b in order:
    _used.add(ind[b + "_instance"]["label"])
    _a, _h = _tmp[b]
    _used.update(_a)
    _used.update(_h)

def _derive(cat, label):
    """按规则推导口语形式；返回 (alt, hid)。"""
    alt = []
    if cat == "MAIXIANG" and label.endswith("脉") and len(label) > 1:
        alt.append("脉" + label[:-1])
    elif cat == "SHEXIANG" and label.endswith("苔") and not label.startswith("舌") and len(label) > 1:
        alt.append("舌苔" + label[:-1])
    return alt, []

_mlfz_emitted = 0
_mlfz_dup_pref = []
for cat in ("MAIXIANG", "SHEXIANG", "FUZHENG"):
    items = mlfz.get(cat, [])
    if not items:
        continue
    L.append("### ========== 四诊通道：%s（%d 项） ==========" % (cat, len(items)))
    L.append("")
    for frag, label in items:
        base = frag[:-len("_instance")] if frag.endswith("_instance") else frag
        if not label:
            continue
        if label in _used:
            _mlfz_dup_pref.append((cat, base, label))
        d_alt, d_hid = _derive(cat, label)
        s = MLFZ_SYN.get(base, {})
        alt = list(d_alt) + list(s.get("alt", []))
        hid = list(d_hid) + list(s.get("hid", []))
        # 去重 + 排除规范名自身 + 排除已被占用的表面形式（症状通道优先）
        alt = [x for x in dict.fromkeys(alt) if x and x != label and x not in _used]
        hid = [x for x in dict.fromkeys(hid) if x and x != label and x not in _used and x not in alt]
        _used.add(label)
        _used.update(alt)
        _used.update(hid)

        L.append("zzskos:%s a skos:Concept ;" % base)
        L.append("    skos:inScheme <http://www.tcm-classics.org/skos/zhengzhuang> ;")
        L.append('    skos:prefLabel "%s"@zh ;' % esc(label))
        if alt:
            L.append("    skos:altLabel " + ttl_list(alt) + " ;")
        if hid:
            L.append("    skos:hiddenLabel " + ttl_list(hid) + " ;")
        L.append('    skos:notation "%s" ;' % frag)
        L.append("    skos:exactMatch zz:%s ;" % frag)
        L.append("    zz:classMatch zz:%s ." % base)
        L.append("")
        _mlfz_emitted += 1

if _mlfz_dup_pref:
    print("!! 四诊通道 prefLabel 与症状通道重复（已跳过同义形式）:", file=sys.stderr)
    for cat, base, label in _mlfz_dup_pref:
        print("   ", cat, base, label, file=sys.stderr)

io.open(OUT, "w", encoding="utf-8", newline="\n").write("\n".join(L) + "\n")
print("WROTE", OUT)
print("orphan concepts appended:", len(orphans))
print("concepts:", len(order), "topConcepts:", len(tops))
print("mlfz concepts appended:", _mlfz_emitted)
n_alt = sum(1 for b in order if SYN.get(b, {}).get("alt"))
n_hid = sum(1 for b in order if SYN.get(b, {}).get("hid"))
n_note = sum(1 for b in order if SYN.get(b, {}).get("note"))
n_def = sum(1 for b in order if (SYN.get(b, {}).get("definition") or cls.get(b, {}).get("comment")))
print("with altLabel:", n_alt, "with hiddenLabel:", n_hid, "with definition:", n_def, "with note:", n_note)
print("closeMatch concepts:", len(close), "broader links:", sum(len(v) for v in broader.values()))
