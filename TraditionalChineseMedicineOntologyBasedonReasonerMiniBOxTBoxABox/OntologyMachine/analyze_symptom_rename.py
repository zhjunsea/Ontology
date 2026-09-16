# -*- coding: utf-8 -*-
import re, os, glob

# 66个待修正映射（排除4个医学约定保留项：Jingbing/Momo/Dabingchaihou/Fuzhongjitong）
mapping = {
    # 格式违规 8
    "AnzhijitongRulin": "Anzhijitongrulin",
    "RenshenXiaxue": "Renshenxiaxue",
    "RenshenOutuBuzhi": "Renshenoutubuzhi",
    "RenshenYoushuiqi": "Renshenyoushuiqi",
    "ChanhouFutong": "Chanhoufutong",
    "ChanhouXiali": "Chanhouxiali",
    "HanshiJiexiong": "Hanshijiexiong",
    "Zili'erke": "Zilierke",
    # 拼写错误 58
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

print(f"映射总数: {len(mapping)}")

# 1. 收集症状本体中所有现有类名
zz_path = r'D:\work\Ontology\TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox\ontology\tcm-zhengzhuang.owl'
with open(zz_path, 'r', encoding='utf-8') as f:
    zz = f.read()
existing = set(re.findall(r'<owl:Class rdf:about="#([^"]+)">', zz))
print(f"症状本体现有类数: {len(existing)}")

# 2. 冲突检查：新名是否已被占用（且不是自己旧名）
print("\n===== 冲突检查（新名已存在于症状本体）=====")
conflicts = []
for old, new in mapping.items():
    if new in existing and new != old:
        conflicts.append((old, new))
        print(f"  ⚠️ {old} -> {new} : 目标名已存在!")
if not conflicts:
    print("  无冲突")

# 3. 统计每个旧名在 ontology 目录下的引用
ont_root = r'D:\work\Ontology\TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox\ontology'
owl_files = glob.glob(os.path.join(ont_root, '**', '*.owl'), recursive=True)
print(f"\nontology下owl文件数: {len(owl_files)}")

ref_count = {old: [] for old in mapping}
for fp in owl_files:
    with open(fp, 'r', encoding='utf-8') as f:
        txt = f.read()
    for old in mapping:
        # 精确匹配 #OldName" 或 #OldName< 或 OldName 作为独立标识
        if re.search(r'#' + re.escape(old) + r'(?=["<])', txt):
            ref_count[old].append(os.path.relpath(fp, ont_root))

print("\n===== ontology引用统计 =====")
for old, files in ref_count.items():
    if files:
        print(f"  {old} ({len(files)}): {files}")

# 4. 统计 java 测试引用
java_root = r'D:\work\Ontology\TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox\OntologyMachine\OntologyFramework\src'
java_files = glob.glob(os.path.join(java_root, '**', '*.java'), recursive=True)
print(f"\njava文件数: {len(java_files)}")
java_ref = {old: [] for old in mapping}
for fp in java_files:
    with open(fp, 'r', encoding='utf-8') as f:
        txt = f.read()
    for old in mapping:
        if re.search(r'\b' + re.escape(old) + r'\b', txt):
            java_ref[old].append(os.path.relpath(fp, java_root))

print("\n===== java引用统计 =====")
for old, files in java_ref.items():
    if files:
        print(f"  {old} ({len(files)}): {files}")

# 5. 汇总未在任何地方引用的
print("\n===== 未被引用的旧名（仅本体定义自身）=====")
for old in mapping:
    if not ref_count[old] and not java_ref[old]:
        print(f"  {old}")
