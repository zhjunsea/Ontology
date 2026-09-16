# -*- coding: utf-8 -*-
import re, os, glob

mapping = {
    "AnzhijitongRulin": "Anzhijitongrulin",
    "RenshenXiaxue": "Renshenxiaxue",
    "RenshenOutuBuzhi": "Renshenoutubuzhi",
    "RenshenYoushuiqi": "Renshenyoushuiqi",
    "ChanhouFutong": "Chanhoufutong",
    "ChanhouXiali": "Chanhouxiali",
    "HanshiJiexiong": "Hanshijiexiong",
    "Zili'erke": "Zilierke",
    "Beiweiehan": "Beiehan",
    "Ruzhuang": "Runvezhuang",
    "Duohanshui": "Duohanchu",
    "Mianchibanzhang": "Mianchibanbanrujinwen",
    "Biniu": "Binv",
    "Muzhongliaoliao": "Muzhongbulele",
    "Mianseqinghei": "Mianselihei",
    "Mushundong": "Murundong",
    "Mukeshanweiweiyong": "Mukeshangweiyong",
    "Koushebuzhen": "Kousheburen",
    "Yanzhongruyouzhilian": "Yanzhongruyouzhiluan",
    "Shuiruzetutu": "Shuiruzetu",
    "Koutuoxian": "Koutuxian",
    "Yuyannanchuchu": "Yuyannanchu",
    "Kesuo": "Ketuo",
    "Kesangerqi": "Keershangqi",
    "Xinzhongzhi": "Xiongzhongzhi",
    "Xiongzhongjiatuo": "Xiongzhongjiacuo",
    "Xinzhongdahantong": "Xinxiongdahantong",
    "Xiexiaoniqiangxin": "Xiexianiqiangxin",
    "Fuzhongleng": "Fuzhonghan",
    "Leiming": "Fuzhongleiming",
    "Shaofuji": "Shaofujuji",
    "Shaofujili": "Shaofuliji",
    "Xinxianzhimantong": "Xinxiaanzhimantong",
    "Shaofuzhengjia": "Shaofuzhengkuai",
    "Raogitong": "Raoqitong",
    "Fuzhongjitong": "Fuzhongjiaotong",
    "Fuzhongjijitong": "Fuzhongjitong",
    "Pangguangji": "Bangguangji",
    "Xialirishu": "Xialirishushixing",
    "Dabianzhananzayi": "Dabianzhananzhayi",
    "E": "Hui",
    "Gantaishichou": "Ganyishichou",
    "Shiguyuyu": "Shiguyuou",
    "Shouzuraorao": "Shouzuzaorao",
    "Shouzhufanre": "Shouzufanre",
    "Jintiroushun": "Jintirourun",
    "Danbibuxui": "Danbibusui",
    "Shenrushichongxingpizhong": "Shenruchongxingpizhong",
    "Yaoyixiashuiqi": "Yaoyixiayoushuiqi",
    "Tibueran": "Tierbuan",
    "Rujiangguizhuang": "Rujianguizhuang",
    "Zhenzhenshenshunju": "Zhenzhenshenrunju",
    "Mianmuzhachizhaheizhaibai": "Mianmuzhachizhaheizhabai",
    "Wozegjing": "Wozejing",
    "Cukoujin": "Zukoujin",
    "Wobuzhuoxi": "Wobuzhexi",
    "Niaoshitoutong": "Nishitoutong",
    "Sezhenghuangrubai": "Sezhenghuangrubaizhi",
    "Shensheruxunhuang": "Shenseruxunhuang",
    "Nuxue": "Nvxue",
    "Furensuyouzhengjia": "Furensuyouzhengbing",
    "Nuemu": "Nvemu",
    "Ripusuoju": "Ribusuoju",
    "Changyudaqixiongshang": "Changyudaoqixiongshang",
    "Hanshanraogitong": "Hanshanraoqitong",
    "Gexianzhiyin": "Gejianzhiyin",
    "Gexianyoushui": "Gejianyoushui",
}

ont_root = r'D:\work\Ontology\TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox\ontology'
java_root = r'D:\work\Ontology\TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox\OntologyMachine\OntologyFramework\src'

owl_files = glob.glob(os.path.join(ont_root, '**', '*.owl'), recursive=True)
java_files = glob.glob(os.path.join(java_root, '**', '*.java'), recursive=True)

print("===== 旧名残留检查（OWL）=====")
found = False
for fp in owl_files:
    if '_rename_backup' in fp:
        continue
    with open(fp, 'r', encoding='utf-8') as f:
        txt = f.read()
    for old in mapping:
        if re.search(r'#' + re.escape(old) + r'(?=["<>])', txt):
            print(f"  ⚠️ {os.path.relpath(fp, ont_root)}: 仍含 #{old}")
            found = True
if not found:
    print("  ✓ 无残留")

print("\n===== 旧名残留检查（Java，排除E）=====")
found = False
for fp in java_files:
    with open(fp, 'r', encoding='utf-8') as f:
        txt = f.read()
    for old in mapping:
        if old == 'E':
            continue
        if re.search(r'\b' + re.escape(old) + r'\b', txt):
            print(f"  ⚠️ {os.path.relpath(fp, java_root)}: 仍含 {old}")
            found = True
if not found:
    print("  ✓ 无残留")

print("\n===== 新名引用检查 =====")
# 检查新名是否在OWL和Java中一致出现
for old, new in mapping.items():
    owl_hit = []
    for fp in owl_files:
        if '_rename_backup' in fp:
            continue
        with open(fp, 'r', encoding='utf-8') as f:
            if re.search(r'#' + re.escape(new) + r'(?=["<>])', f.read()):
                owl_hit.append(os.path.relpath(fp, ont_root))
    print(f"  {new}: OWL引用 {len(owl_hit)} 处")
