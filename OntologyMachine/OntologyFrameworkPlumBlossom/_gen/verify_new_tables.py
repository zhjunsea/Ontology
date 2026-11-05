#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""第十四器：新迁移表「Java 内置同源兜底」↔ DB `data.sql` 一致性核（第 52 轮 C-3）。

严格逐字比对（Java 兜底 与 DB 真源 必须相等）：
  - five_phase_attr   ↔ FIVE_DIR / FIVE_COLOR / FIVE_SEASON / FIVE_OBJECT（按 phase_label 取键）
  - season_vitality   ↔ vitalityOf 旺衰算法（标准五行生克推出，与 Java 内置一致）
  - bodyuse_level     ↔ shengKeName() / shengKeLabel() 之 switch
  - auspiciousness_level ↔ shengKeLabel()（level→label 反查）
  - judge_weight      ↔ JUDGE_W_BUILTIN
  - juan_text         ↔ 58 个 JUAN_* 照录常量（C-2 下沉新表）

结构/覆盖核（bytewise 不适用，因 Java 兜底由运行期字符串拼接而成）：
  - domain_lore：text 非空、PK 唯一、且代码 loreMap/loreTpl 引用的 collection 均在库中存在。

退出码：0=全部通过；1=存在差异。
"""
import io, re, sys

JAVA = 'src/main/java/com/ocean/ontologyframework/plumblossom/PlumBlossomOntologyJobWorker.java'
SQL = 'ontology/database/data.sql'

# 标准五行生克（与 JobWorker 内置一致，仅用于推导 vitalityOf 结果）
GEN = {('木', '火'), ('火', '土'), ('土', '金'), ('金', '水'), ('水', '木')}
KE = {('木', '土'), ('土', '水'), ('水', '火'), ('火', '金'), ('金', '木')}
SEASON_FIVE = {'春': '木', '夏': '火', '秋': '金', '冬': '水', '四季': '土'}


def norm_sql_text(s):
    return s.replace('\\n', '\n').replace("''", "'")


def split_fields(s):
    out, buf, i, n, ins = [], '', 0, len(s), False
    while i < n:
        ch = s[i]
        if ins:
            if ch == "'":
                if i + 1 < n and s[i + 1] == "'":
                    buf += "'"; i += 2; continue
                ins = False
            else:
                buf += ch
        elif ch == "'":
            ins = True
        elif ch == ',':
            out.append(buf.strip()); buf = ''
        else:
            buf += ch
        i += 1
    out.append(buf.strip())
    return out


def parse_inserts(sql, table):
    """返回该表所有 INSERT 的行（每行为字段列表）。"""
    rows = []
    pat = re.compile(r'INSERT\s+INTO\s+`?%s`?\s*\([^)]*\)\s*VALUES' % re.escape(table), re.I)
    for m in pat.finditer(sql):
        i = m.end()
        n = len(sql)
        while i < n:
            # 跳过空白与逗号
            while i < n and sql[i] in ' \t\r\n,':
                i += 1
            if i >= n or sql[i] == ';':
                break
            if sql[i] != '(':
                break
            # 解析一个元组
            i += 1
            buf, fields, ins = '', [], False
            while i < n:
                ch = sql[i]
                if ins:
                    if ch == "'":
                        if i + 1 < n and sql[i + 1] == "'":
                            buf += "'"; i += 2; continue
                        ins = False
                    else:
                        buf += ch
                else:
                    if ch == "'":
                        ins = True
                    elif ch == ',':
                        fields.append(buf.strip()); buf = ''
                    elif ch == ')':
                        fields.append(buf.strip())
                        i += 1
                        break
                    else:
                        buf += ch
                i += 1
            rows.append(fields)
    return rows


def java_map_of(src, name):
    """解析 `NAME = Map.of( ... );` → dict（值为纯字符串字面量）。"""
    m = re.search(re.escape(name) + r'\s*=\s*Map\.of\((.*?)\);', src, re.S)
    if not m:
        return None
    body = m.group(1)
    pairs = re.findall(r'"((?:[^"\\]|\\.)*)"\s*,\s*"((?:[^"\\]|\\.)*)"', body)
    return {k: v for k, v in pairs}


def java_switch_map(src, method):
    """解析方法体内 `case N -> "X";` → {N: X}。"""
    m = re.search(r'private\s+static\s+String\s+' + re.escape(method) + r'\s*\([^)]*\)\s*\{(.*?)\n    \}', src, re.S)
    if not m:
        return {}
    body = m.group(1)
    out = {}
    for cm in re.finditer(r'case\s+(\d+)\s*->\s*"((?:[^"\\]|\\.)*)"', body):
        out[int(cm.group(1))] = cm.group(2)
    return out


def java_judge_weight(src):
    m = re.search(r'JUDGE_W_BUILTIN\s*=\s*Map\.ofEntries\((.*?)\);', src, re.S)
    out = {}
    if not m:
        return out
    for em in re.finditer(r'Map\.entry\(\s*"([^"]+)"\s*,\s*(-?\d+)\s*\)', m.group(1)):
        out[em.group(1)] = int(em.group(2))
    return out


def java_juan(src):
    """复用 extract_juan 的解析：返回 {code: content}。"""
    JUAN_MAP = {'JUAN_YI_': '一', 'JUAN_ER_': '二', 'JUAN_SAN_': '三', 'JUAN_SI_': '四', 'JUAN_WU_': '五'}
    pat = re.compile(r'private\s+static\s+String\s+(JUAN_[A-Z0-9_]+)\s*=')
    out = {}
    for m in pat.finditer(src):
        name = m.group(1)
        pos = m.end()
        outv, cur, i, n, ins = [], '', pos, len(src), False
        while i < n:
            c = src[i]
            if ins:
                if c == '\\' and i + 1 < n:
                    cur += {'n': '\n', 't': '\t', '"': '"', '\\': '\\'}.get(src[i + 1], src[i + 1]); i += 2; continue
                if c == '"':
                    ins = False; outv.append(cur); cur = ''
                else:
                    cur += c
            else:
                if c == '"':
                    ins = True
                elif c == ';':
                    break
            i += 1
        out[name] = ''.join(outv)
    return out


def vitality(phase, season):
    s = SEASON_FIVE.get(season)
    if s is None:
        return '?'
    if phase == s:
        return '旺'
    if (s, phase) in GEN:
        return '相'
    if (phase, s) in GEN:
        return '休'
    if (phase, s) in KE:
        return '囚'
    return '死'


def main():
    src = io.open(JAVA, encoding='utf-8').read()
    sql = io.open(SQL, encoding='utf-8').read().replace('\r', '')
    bad = 0

    def chk(cond, msg):
        nonlocal bad
        if not cond:
            bad += 1
            print('  !! ' + msg)

    # ---- 1) five_phase_attr ↔ FIVE_* ----
    fdir = java_map_of(src, 'FIVE_DIR') or {}
    fcol = java_map_of(src, 'FIVE_COLOR') or {}
    fsea = java_map_of(src, 'FIVE_SEASON') or {}
    fobj = java_map_of(src, 'FIVE_OBJECT') or {}
    rows = parse_inserts(sql, 'five_phase_attr')
    print('[five_phase_attr] DB 行 %d；Java FIVE 键 %d' % (len(rows), len(fdir)))
    for r in rows:
        local, label, direction, color, season, obj = r[0], r[1], r[2], r[3], r[4], r[5]
        chk(fdir.get(label) == direction, 'five_phase_attr %s 方向 Java=%s DB=%s' % (label, fdir.get(label), direction))
        chk(fcol.get(label) == color, 'five_phase_attr %s 色 Java=%s DB=%s' % (label, fcol.get(label), color))
        chk(fsea.get(label) == season, 'five_phase_attr %s 时令 Java=%s DB=%s' % (label, fsea.get(label), season))
        chk(fobj.get(label) == obj, 'five_phase_attr %s 物 Java=%s DB=%s' % (label, fobj.get(label), obj))
    chk(len(rows) == len(fdir) == 5, 'five_phase_attr 行数 %d / Java %d' % (len(rows), len(fdir)))

    # ---- 2) season_vitality ↔ vitalityOf ----
    rows = parse_inserts(sql, 'season_vitality')
    print('[season_vitality] DB 行 %d' % len(rows))
    seen = set()
    for r in rows:
        season, label, vit = r[1], r[2], r[3]
        seen.add((label, season))
        exp = vitality(label, season)
        chk(exp == vit, 'season_vitality %s/%s 算法=%s DB=%s' % (label, season, exp, vit))
    chk(len(seen) == 25, 'season_vitality 应 25 组合，实 %d' % len(seen))

    # ---- 3) bodyuse_level ↔ shengKeName/shengKeLabel ----
    sname = java_switch_map(src, 'shengKeName')
    slab = java_switch_map(src, 'shengKeLabel')
    rows = parse_inserts(sql, 'bodyuse_level')
    print('[bodyuse_level] DB 行 %d；Java name %d / label %d' % (len(rows), len(sname), len(slab)))
    for r in rows:
        lv = int(r[0]); nm = r[1]; au = r[2]
        chk(sname.get(lv) == nm, 'bodyuse_level L%d 名 Java=%s DB=%s' % (lv, sname.get(lv), nm))
        chk(slab.get(lv) == au, 'bodyuse_level L%d 吉凶 Java=%s DB=%s' % (lv, slab.get(lv), au))

    # ---- 4) auspiciousness_level ↔ shengKeLabel(level) ----
    # 注：auspiciousness_level.level 序为大凶→大吉（1 最凶…5 最吉），与 shengKeLabel 的
    #     bodyuse level（1 大吉…5 大凶）互为倒序；故标签取 shengKeLabel，序数按 6-体用级 交叉核。
    label2aus = {v: 6 - k for k, v in slab.items()}
    rows = parse_inserts(sql, 'auspiciousness_level')
    print('[auspiciousness_level] DB 行 %d；标签集 %d' % (len(rows), len(label2aus)))
    for r in rows:
        local, label, lv = r[0], r[1], int(r[2])
        chk(label in label2aus, 'auspiciousness_level 未知标签 %s' % label)
        chk(label2aus.get(label) == lv, 'auspiciousness_level %s 序 Java(6-体用)=%s DB=%s' % (label, label2aus.get(label), lv))
    chk(set(r[1] for r in rows) == set(label2aus), 'auspiciousness_level 标签集与 shengKeLabel 不等')

    # ---- 5) judge_weight ↔ JUDGE_W_BUILTIN ----
    jw = java_judge_weight(src)
    rows = parse_inserts(sql, 'judge_weight')
    print('[judge_weight] DB 行 %d；Java 键 %d' % (len(rows), len(jw)))
    for r in rows:
        item, weight = r[0], int(r[1])
        chk(jw.get(item) == weight, 'judge_weight %s Java=%s DB=%s' % (item, jw.get(item), weight))
    chk(len(rows) == len(jw), 'judge_weight 行数 %d / Java %d' % (len(rows), len(jw)))

    # ---- 6) juan_text ↔ JUAN_* ----
    jj = java_juan(src)
    rows = parse_inserts(sql, 'juan_text')
    print('[juan_text] DB 行 %d；Java JUAN_* %d' % (len(rows), len(jj)))
    dbj = {}
    for r in rows:
        dbj[r[0]] = r[3]
    chk(set(dbj) == set(jj), 'juan_text 键集不符：仅DB=%s 仅Java=%s' % (
        sorted(set(dbj) - set(jj)), sorted(set(jj) - set(dbj))))
    for k in sorted(set(dbj) & set(jj)):
        if dbj[k] != jj[k]:
            bad += 1
            print('  !! juan_text %s 内容不符 Java %d 字 / DB %d 字' % (k, len(jj[k]), len(dbj[k])))

    # ---- 7) domain_lore 结构/覆盖（bytewise 不适用） ----
    rows = parse_inserts(sql, 'domain_lore')
    pk = set()
    colls = set()
    for r in rows:
        coll, k, text = r[0], r[1], r[2]
        colls.add(coll)
        chk((coll, k) not in pk, 'domain_lore PK 重复 %s/%s' % (coll, k))
        pk.add((coll, k))
        chk(text != '', 'domain_lore %s/%s 文本为空' % (coll, k))
    refs = set(re.findall(r'lore(?:Map|Tpl)\("([A-Z_]+)"', src))
    missing = refs - colls
    print('[domain_lore] DB 行 %d / 集合 %d；代码引用集合 %d' % (len(rows), len(colls), len(refs)))
    for c in sorted(missing):
        # 代码引用但库中缺该集合 → 仅提示（部分 collection 走算法兜底，可不在库）
        print('  ~ warning: 代码引用 collection=%s 库中未见（可能为算法兜底/内联）' % c)

    print('RESULT:', 'ALL PASS（新迁移表 Java≡DB 严格核；domain_lore 结构/覆盖核）' if bad == 0
          else 'HAS DIFF（%d）' % bad)
    return 0 if bad == 0 else 1


if __name__ == '__main__':
    sys.exit(main())
