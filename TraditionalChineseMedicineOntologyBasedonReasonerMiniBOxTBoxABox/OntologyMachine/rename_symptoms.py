# -*- coding: utf-8 -*-
import re, os, glob, shutil

# 68项修正映射（4个医学约定保留：Jingbing/Momo/Dabingchaihou 不动）
# 腹中㽲痛 Fuzhongjitong -> Fuzhongjiaotong ; 腹中急痛 Fuzhongjijitong -> Fuzhongjitong
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
    "Fuzhongjitong": "Fuzhongjiaotong",      # 腹中㽲痛（先处理，腾出名字）
    "Fuzhongjijitong": "Fuzhongjitong",      # 腹中急痛
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

ont_root = r'D:\work\Ontology\TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox\ontology'
java_root = r'D:\work\Ontology\TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox\OntologyMachine\OntologyFramework\src'

owl_files = glob.glob(os.path.join(ont_root, '**', '*.owl'), recursive=True)
java_files = glob.glob(os.path.join(java_root, '**', '*.java'), recursive=True)

# 备份
bak_dir = os.path.join(ont_root, '_rename_backup')
os.makedirs(bak_dir, exist_ok=True)
for fp in owl_files:
    shutil.copy2(fp, os.path.join(bak_dir, os.path.basename(fp)))
print(f"已备份 {len(owl_files)} 个owl文件到 {bak_dir}")

# 处理顺序：先 Fuzhongjitong->Fuzhongjiaotong，再 Fuzhongjijitong->Fuzhongjitong
order = list(mapping.keys())
order.remove("Fuzhongjitong"); order.remove("Fuzhongjijitong")
order = ["Fuzhongjitong", "Fuzhongjijitong"] + order

def replace_owl(txt):
    for old in order:
        new = mapping[old]
        # 精确匹配 #OldName 后跟 " 或 < 或 >
        txt = re.sub(r'#' + re.escape(old) + r'(?=["<>])', '#' + new, txt)
    return txt

def replace_java(txt):
    for old in order:
        if old == 'E':
            continue  # Java中E是规则编号，不替换
        new = mapping[old]
        txt = re.sub(r'\b' + re.escape(old) + r'\b', new, txt)
    return txt

# OWL替换
owl_changed = 0
for fp in owl_files:
    with open(fp, 'r', encoding='utf-8') as f:
        orig = f.read()
    new_txt = replace_owl(orig)
    if new_txt != orig:
        with open(fp, 'w', encoding='utf-8', newline='') as f:
            f.write(new_txt)
        owl_changed += 1
        print(f"  [OWL] {os.path.relpath(fp, ont_root)}")

# Java替换
java_changed = 0
for fp in java_files:
    with open(fp, 'r', encoding='utf-8') as f:
        orig = f.read()
    new_txt = replace_java(orig)
    if new_txt != orig:
        with open(fp, 'w', encoding='utf-8', newline='') as f:
            f.write(new_txt)
        java_changed += 1
        print(f"  [JAVA] {os.path.relpath(fp, java_root)}")

print(f"\n完成：OWL {owl_changed} 个文件，Java {java_changed} 个文件")
