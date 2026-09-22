# -*- coding: utf-8 -*-
"""
生成药证本体 tcm-yaozheng.owl。

依据（铁律 63：一切改动必须有医理/医书依据，严禁编造）：
  药证的唯一经典来源 = 张仲景《伤寒论》《金匮要略》各方「方后注」加减法。
    方后注「若X者加Y」⟹ X 是 Y 的药证（适应证）；
    方后注「若X者去Y」⟹ X 是 Y 的忌药证（禁忌证）；
    方后注「若X者还用Y」（retain）⟹ X 是 Y 的药证。
  此即吉益东洞《药征》、胡希恕「方证是辨证的尖端、药证是方证的基础」之方法论。
  本脚本不新增任何规则，只把 ontology/fangzheng/rules.owl 中已有的 84 条方后注加减规则
  按「药物」重新聚合，转写为 OWL 类定义（等价类 ≡ 条件析取）。

四诊化（铁律 62：追问/输入只限四诊，不得暴露六经/八纲）：
  条件 token 一律落到 you_zhengzhuang / you_maixiang / you_shexiang / you_fuzheng 四个槽位。
  唯一的八纲 token「Han（寒）」展开为其全部四诊子类之析取
  （Fuchimai / Chenchimai / Xianchimai / JixuKouchiMai / PaleWhiteTongue /
    SheXiang_Han_HeiHua / SheXiang_Han_HuiHua）——本体中 Han 仅此 7 个子类，
  其余 #Han 引用皆为 bagangAttr 元数据，故该展开是完备的。

输入：ontology/fangzheng/rules.owl、ontology/database/fangji-yaowu.sql、各通道本体
输出：ontology/tcm-yaozheng.owl
"""
import re, os, sys, io, collections

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')

ONT = r"D:/work/Ontology/TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox/ontology"
NS = "http://www.tcm-classics.org/jingfang#"

def read(p):
    with open(p, encoding='utf-8') as f:
        return f.read()

# ---------------------------------------------------------------- 1. 类层级
CHANNEL_FILES = ["tcm-core.owl", "tcm-zhengzhuang.owl", "tcm-maixiang.owl",
                 "tcm-shexiang.owl", "tcm-fuzheng.owl"]
parents = collections.defaultdict(set)
labels = {}
for cf in CHANNEL_FILES:
    txt = read(os.path.join(ONT, cf))
    for m in re.finditer(r'<owl:Class rdf:about="#([A-Za-z0-9_]+)">(.*?)</owl:Class>', txt, re.S):
        frag, body = m.group(1), m.group(2)
        for pm in re.finditer(r'<rdfs:subClassOf rdf:resource="#([A-Za-z0-9_]+)"', body):
            parents[frag].add(pm.group(1))
        lm = re.search(r'<rdfs:label xml:lang="zh">([^<]+)</rdfs:label>', body)
        if lm:
            labels[frag] = lm.group(1)

def closure(frag):
    seen, stack = set(), [frag]
    while stack:
        c = stack.pop()
        for p in parents.get(c, ()):
            if p not in seen:
                seen.add(p); stack.append(p)
    return seen

CHANNEL_PROP = {"Zhengzhuang": "you_zhengzhuang", "Maixiang": "you_maixiang",
                "Shexiang": "you_shexiang", "Fuzheng": "you_fuzheng"}

def channel_of(frag):
    cl = closure(frag) | {frag}
    for top, prop in CHANNEL_PROP.items():
        if top in cl:
            return prop
    return None

HAN_LEAVES = ["Fuchimai", "Chenchimai", "Xianchimai", "JixuKouchiMai",
              "PaleWhiteTongue", "SheXiang_Han_HeiHua", "SheXiang_Han_HuiHua"]

def token_disjuncts(tok):
    """token → [conj, ...]（析取范式：每个 conj 是 (prop,filler) 的合取列表）。"""
    if tok == "Han":
        return [[(channel_of(lf), lf)] for lf in HAN_LEAVES]
    prop = channel_of(tok)
    return [[(prop, tok)]] if prop else []

# ---------------------------------------------------------------- 2. 药物表
drug_cn = {}
sql = read(os.path.join(ONT, "database/fangji-yaowu.sql"))
for m in re.finditer(r"\('([A-Za-z]+)','([^']+)'\)", sql):
    drug_cn.setdefault(m.group(1), m.group(2))

# ---------------------------------------------------------------- 3. 解析 rules.owl
rules_txt = read(os.path.join(ONT, "fangzheng/rules.owl"))
RULE_BLOCK = re.compile(r'<owl:Class\s+rdf:about="#([^"]+)">(.*?)</owl:Class>', re.S)
RULE_LIT = re.compile(r'<(addHerbRule|removeHerbRule|replaceHerbRule|dosageChangeRule)[^>]*>(.*?)</\1>', re.S)
RULE_SRC = re.compile(r'<ruleSource[^>]*>(.*?)</ruleSource>', re.S)
IF_THEN = re.compile(r'(?is)^\s*IF\s+(.+?)\s+THEN\s+(.+?)\s*$')

def unescape(s):
    return (s.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", '"')
             .replace("&apos;", "'").replace("&amp;", "&"))

def split_top_and(s):
    parts, depth, cur, i = [], 0, [], 0
    while i < len(s):
        c = s[i]
        if c == '(':
            depth += 1; cur.append(c); i += 1; continue
        if c == ')':
            depth -= 1; cur.append(c); i += 1; continue
        at_and = (depth == 0 and s[i:i+3].upper() == "AND"
                  and (i == 0 or s[i-1].isspace())
                  and (i+3 >= len(s) or s[i+3].isspace()))
        if at_and:
            parts.append(''.join(cur).strip()); cur = []; i += 3; continue
        cur.append(c); i += 1
    parts.append(''.join(cur).strip())
    return [p for p in parts if p]

records = []
for bm in RULE_BLOCK.finditer(rules_txt):
    host, body = bm.group(1), bm.group(2)
    lits, starts = [], []
    for lm in RULE_LIT.finditer(body):
        lits.append((lm.group(1), unescape(lm.group(2)).strip())); starts.append(lm.start())
    if not lits:
        continue
    srcs, sstarts = [], []
    for sm in RULE_SRC.finditer(body):
        srcs.append(unescape(sm.group(1)).strip()); sstarts.append(sm.start())
    for i, (kind, raw) in enumerate(lits):
        rs = starts[i]
        nxt = starts[i+1] if i+1 < len(starts) else 10**9
        src = None
        for j, ss in enumerate(sstarts):
            if rs <= ss < nxt:
                src = srcs[j]; break
        flat = re.sub(r'\s+', ' ', raw).strip()
        m = IF_THEN.match(flat)
        if not m:
            print("!! 无法解析:", raw); continue
        condGroups = []
        for atom in split_top_and(m.group(1)):
            a = atom.strip()
            if a.startswith('(') and a.endswith(')'):
                ors = [o.strip() for o in re.split(r'(?i)\s+OR\s+', a[1:-1]) if o.strip()]
                if ors: condGroups.append(ors)
            elif a:
                condGroups.append([a])
        adds, removes, retains = [], [], []
        cur = "add"
        for seg in re.split(r'\s*\+\s*', m.group(2)):
            s = seg.strip()
            if not s: continue
            low = s.lower()
            if low.startswith("remove "): cur, s = "remove", s[7:].strip()
            elif low.startswith("add "): cur, s = "add", s[4:].strip()
            elif low.startswith("retain "): cur, s = "retain", s[7:].strip()
            elif low.startswith("replace "): cur, s = "replace", s[8:].strip()
            lp = s.find('(')
            if lp >= 0:
                herb = s[:lp].strip()
                dose = s[lp+1:s.rfind(')')].strip() if s.rfind(')') > lp else s[lp+1:].strip()
            else:
                herb, dose = s.strip(), None
            herb = herb.split()[0] if herb else ""
            if not herb: continue
            if cur == "remove": removes.append(herb)
            elif cur == "retain": retains.append(herb)
            elif cur == "replace": pass
            else: adds.append((herb, dose))
        if condGroups:
            records.append((host, condGroups, adds, removes, retains, src, flat))

print("解析规则条数:", len(records), " 宿主方证:", len({r[0] for r in records}))

# ---------------------------------------------------------------- 4. 按药物聚合
pos = collections.defaultdict(list)
neg = collections.defaultdict(list)
for host, cg, adds, removes, retains, src, raw in records:
    for herb, _ in adds:
        pos[herb].append((cg, src, host, raw))
    for herb in retains:
        pos[herb].append((cg, src, host, raw))
    for herb in removes:
        neg[herb].append((cg, src, host, raw))

unknown = sorted(set(list(pos) + list(neg)) - set(drug_cn))
if unknown:
    print("!! 药物表中不存在的药物:", unknown)

bad = set()
for d, lst in list(pos.items()) + list(neg.items()):
    for cg, *_ in lst:
        for grp in cg:
            for tok in grp:
                if not token_disjuncts(tok):
                    bad.add(tok)
if bad:
    print("!! 无法映射四诊通道的 token:", sorted(bad))

print("药证（加/留）药物数:", len(pos), " 忌药证（去）药物数:", len(neg))

# 冲突检查：同一药物同一条件既加又去
for d in sorted(set(pos) & set(neg)):
    ps = {tuple(sorted(t for g in cg for t in g)) for cg, *_ in pos[d]}
    ns = {tuple(sorted(t for g in cg for t in g)) for cg, *_ in neg[d]}
    both = ps & ns
    if both:
        print(f"  [注意] {d} 同条件既加又去: {both}")

# ---------------------------------------------------------------- 5. 生成 OWL
def esc(s):
    return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

IND = " " * 16

def render_conj(conj):
    rs = []
    for prop, filler in conj:
        rs.append(f'{IND}<owl:Restriction>\n'
                  f'{IND}    <owl:onProperty rdf:resource="#{prop}"/>\n'
                  f'{IND}    <owl:someValuesFrom rdf:resource="#{filler}"/>\n'
                  f'{IND}</owl:Restriction>')
    if len(rs) == 1:
        return rs[0]
    return (f'{IND}<owl:Class>\n'
            f'{IND}    <owl:intersectionOf rdf:parseType="Collection">\n'
            + "\n".join(rs) + "\n"
            f'{IND}    </owl:intersectionOf>\n'
            f'{IND}</owl:Class>')

def render_or(disjuncts):
    if len(disjuncts) == 1:
        return render_conj(disjuncts[0])
    return (f'{IND}<owl:Class>\n'
            f'{IND}    <owl:unionOf rdf:parseType="Collection">\n'
            + "\n".join(render_conj(c) for c in disjuncts) + "\n"
            f'{IND}    </owl:unionOf>\n'
            f'{IND}</owl:Class>')

def render_rule(cg):
    """condGroups（AND of ORs）→ 一个合取表达式。"""
    parts = []
    for grp in cg:
        disj = []
        for tok in grp:
            disj.extend(token_disjuncts(tok))
        if not disj:
            continue
        parts.append(render_or(disj))
    if not parts:
        return None
    if len(parts) == 1:
        return parts[0]
    return (f'{IND}<owl:Class>\n'
            f'{IND}    <owl:intersectionOf rdf:parseType="Collection">\n'
            + "\n".join(parts) + "\n"
            f'{IND}    </owl:intersectionOf>\n'
            f'{IND}</owl:Class>')

def render_class(frag, cn_label, drug, action, branches):
    parts = [render_rule(cg) for cg, *_ in branches]
    parts = [p for p in parts if p]
    if not parts:
        return None
    if len(parts) == 1:
        eq = f'        <owl:equivalentClass>\n{parts[0]}\n        </owl:equivalentClass>'
    else:
        eq = (f'        <owl:equivalentClass>\n'
              f'            <owl:Class>\n'
              f'                <owl:unionOf rdf:parseType="Collection">\n'
              + "\n".join(parts) + "\n"
              f'                </owl:unionOf>\n'
              f'            </owl:Class>\n'
              f'        </owl:equivalentClass>')
    src_txt = "；".join(dict.fromkeys(s for _, s, _, _ in branches if s))
    hosts = sorted({h for _, _, h, _ in branches})
    return "\n".join([
        f'    <owl:Class rdf:about="#{frag}">',
        f'        <rdfs:subClassOf rdf:resource="#Yaozheng"/>',
        f'        <rdfs:label xml:lang="zh">{cn_label}</rdfs:label>',
        f'        <yaozhengDrug rdf:resource="#{drug}"/>',
        f'        <yaozhengAction>{action}</yaozhengAction>',
        f'        <rdfs:comment xml:lang="zh">来源方证：{"、".join(hosts)}</rdfs:comment>',
        f'        <classicalText xml:lang="zh">{esc(src_txt)}</classicalText>',
        eq,
        f'    </owl:Class>'])

# 桂枝药证：并入原 GuizhiZheng（汗出+恶风+脉浮，依《伤寒论》12条桂枝汤证）
GUIZHI_12 = ([["Hanchu"], ["Efeng"], ["Fumai"]], "《伤寒论》12条：「太阳中风，阳浮而阴弱……啬啬恶寒，淅淅恶风，翕翕发热，鼻鸣干呕者，桂枝汤主之。」汗出、恶风、脉浮为桂枝药证。（原 GuizhiZheng 类并入，避免同一药证重复定义）", "Guizhitangzheng", "IF Hanchu AND Efeng AND Fumai THEN add Guizhi")

blocks = []
for drug in sorted(pos):
    b = render_class(f"Yaozheng_{drug}", f"{drug_cn.get(drug, drug)}药证", drug, "add", pos[drug])
    if b: blocks.append(b)
for drug in sorted(neg):
    b = render_class(f"JiYaozheng_{drug}", f"{drug_cn.get(drug, drug)}忌药证", drug, "remove", neg[drug])
    if b: blocks.append(b)

blocks = [b for b in blocks if 'rdf:about="#Yaozheng_Guizhi"' not in b]
b = render_class("Yaozheng_Guizhi", f"{drug_cn.get('Guizhi','桂枝')}药证", "Guizhi", "add",
                 pos.get("Guizhi", []) + [GUIZHI_12])
blocks.append(b)

header = f'''<?xml version="1.0" encoding="UTF-8"?>
<rdf:RDF xmlns="{NS}"
         xml:base="{NS}"
         xmlns:owl="http://www.w3.org/2002/07/owl#"
         xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#"
         xmlns:rdfs="http://www.w3.org/2000/01/rdf-schema#"
         xmlns:xsd="http://www.w3.org/2001/XMLSchema#">

    <owl:Ontology rdf:about="http://www.tcm-classics.org/jingfang/yaozheng">
        <rdfs:label xml:lang="zh">药证模块</rdfs:label>
        <rdfs:comment xml:lang="zh">药证类定义。药证的唯一经典来源为张仲景《伤寒论》《金匮要略》各方方后注加减法：方后注「若X者加Y」⟹ X 为 Y 之药证；「若X者去Y」⟹ X 为 Y 之忌药证。本模块由 rules.owl 的 84 条方后注加减规则按药物聚合转写而成，每条均附 classicalText 原文出处，无一条出于编造。条件一律以四诊（症状/脉象/舌象/腹证）表达；八纲 token「寒」展开为其四诊子类之析取（铁律 62）。</rdfs:comment>
        <owl:imports rdf:resource="http://www.tcm-classics.org/jingfang/core"/>
        <owl:versionInfo>2.0</owl:versionInfo>
    </owl:Ontology>

    <owl:AnnotationProperty rdf:about="#yaozhengDrug">
        <rdfs:label xml:lang="zh">药证主治药物</rdfs:label>
        <rdfs:comment xml:lang="zh">药证类所对应之药物（IRI 取自 fangji-yaowu.sql / tcm-yaowu-abox.owl）。不参与推理，供应用层读取。</rdfs:comment>
    </owl:AnnotationProperty>

    <owl:AnnotationProperty rdf:about="#yaozhengAction">
        <rdfs:label xml:lang="zh">药证作用</rdfs:label>
        <rdfs:comment xml:lang="zh">add=加药（药证/适应证）；remove=去药（忌药证/禁忌证）。</rdfs:comment>
    </owl:AnnotationProperty>

'''

out = header + "\n".join(blocks) + "\n\n</rdf:RDF>\n"
with open(os.path.join(ONT, "tcm-yaozheng.owl"), "w", encoding='utf-8') as f:
    f.write(out)

print("已写出 tcm-yaozheng.owl：", len(blocks), "个类")
print("  药证类:", sum(1 for b in blocks if 'rdf:about="#Yaozheng_' in b))
print("  忌药证类:", sum(1 for b in blocks if 'rdf:about="#JiYaozheng_' in b))
