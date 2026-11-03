# -*- coding: utf-8 -*-
"""从 xz.json 生成：DDL 片段、data.sql 片段、OBDA 映射、SKOS 术语、Java 静态表。"""
import json, os, re

BASE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
D = json.load(open(os.path.join(BASE, "_gen", "xz.json"), encoding="utf-8"))

SRC = {
    "六神笔式": "卷之四·六神笔法/六神形式/六神主事",
    "起六神卦诀": "卷之四·起六神卦诀",
    "五行笔式": "卷之四·五行体格式",
    "八卦断": "卷之四·八卦断",
    "六神形式": "卷之五·六神形式",
    "五行全备": "卷之五·五行全备",
    "神煞": "卷之五·拆字杂编",
    "笔画歌诀": "卷之四·字画指迷",
    "玄黄克应歌": "卷之四·玄黄克应歌",
    "玄黄笔法歌": "卷之四·玄黄笔法歌",
    "辨别五行歌": "卷之四·辨别五行歌",
    "辨别六神歌": "卷之四·辨别六神歌",
    "八卦辨": "卷之五·八卦辨",
    "五行歌并式": "卷之四·五行歌并式",
    "相字短诀": "卷之四·字画指迷",
    "分类断法": "卷之四·字画指迷",
    "字例": "卷之四·字画指迷",
    "赋文": "卷之四·字画指迷",
}


def norm(cat, entry):
    if cat == "六神笔式":
        name, bi, zhu, jue = entry
        return name, f"笔式：{bi}。主事：{zhu}。{jue}"
    return entry[0], entry[1]


def esc(s):
    return s.replace("\\", "\\\\").replace("'", "''")


rows = []  # (rid, cat, key, text, src)
for ci, (cat, entries) in enumerate(D.items(), 1):
    for ei, e in enumerate(entries, 1):
        key, text = norm(cat, e)
        rid = f"XiangZi_{ci:02d}_{ei:02d}"
        rows.append((rid, cat, key, text, SRC[cat]))

print("total rows:", len(rows))

# ---------- 1) DDL ----------
ddl = """
-- 10) 相字（卷之四·字画指迷 / 卷之五·拆字杂编）—— 字画/拆字断法条目
--     依《本体顶层设计》§56（节点6 类象来源含「卷之四/卷之五」）与 §112（symbols.owl ← 万物类象…拆字）。
--     与「以数起卦」别系；仅作节点 9 取象与补断（C12：节点 16 不设独立栏）。
CREATE TABLE `xiangzi_rule` (
  `name`        VARCHAR(64) NOT NULL COMMENT '个体本地名（如 XiangZi_01_01）',
  `category`    VARCHAR(32) DEFAULT ''  COMMENT '类目（六神笔式/五行笔式/八卦断/神煞/…/赋文）',
  `rule_key`    VARCHAR(64) DEFAULT ''  COMMENT '条目名（如 青龙/甲/水/乾/贵神/…）',
  `rule_text`   TEXT                    COMMENT '法诀文本（照录原著）',
  `source_ref`  VARCHAR(64) DEFAULT ''  COMMENT '出处（卷之四/卷之五·篇名）',
  `label`       VARCHAR(96) DEFAULT ''  COMMENT '显示名（相字·类目·条目）',
  PRIMARY KEY (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='相字（字画/拆字）断法条目表';
"""

# ---------- 2) data.sql ----------
lines = ["", "-- 6) 相字（卷之四·字画指迷 / 卷之五·拆字杂编）断法条目",
         "INSERT INTO `xiangzi_rule` (`name`, `category`, `rule_key`, `rule_text`, `source_ref`, `label`) VALUES"]
vals = []
for rid, cat, key, text, src in rows:
    label = f"相字·{cat}·{key}" if cat not in ("赋文", "辨别六神歌", "八卦辨", "五行歌并式") else f"相字·{key}"
    vals.append(f"('{rid}', '{esc(cat)}', '{esc(key)}', '{esc(text)}', '{esc(src)}', '{esc(label)}')")
sql = "\n".join(lines) + "\n" + ",\n".join(vals) + ";\n"

# ---------- 3) OBDA ----------
obda = """
mappingId    xiangzi_rule
target       <sym:{name}> a <sym:XiangZiRule> ;
                 rdfs:label "{label}"@zh ;
                 <sym:xiangZiCategory> "{category}"^^xsd:string ;
                 <sym:xiangZiRuleKey> "{rule_key}"^^xsd:string ;
                 <sym:xiangZiRuleText> "{rule_text}"^^xsd:string ;
                 <sym:xiangZiSourceRef> "{source_ref}"^^xsd:string .
source       SELECT name,
                 COALESCE(category, '') AS category,
                 COALESCE(rule_key, '') AS rule_key,
                 COALESCE(rule_text, '') AS rule_text,
                 COALESCE(source_ref, '') AS source_ref,
                 COALESCE(label, '') AS label
             FROM xiangzi_rule
             WHERE name IS NOT NULL
"""

# ---------- 4) SKOS ----------
ttl = "\n"
for term, pref, iri in [
    ("xiangZiCategory", "相字法诀类目", "symbols/xiangZiCategory"),
    ("xiangZiRuleKey", "相字法诀条目名", "symbols/xiangZiRuleKey"),
    ("xiangZiRuleText", "相字法诀文本", "symbols/xiangZiRuleText"),
    ("xiangZiSourceRef", "相字法诀出处", "symbols/xiangZiSourceRef"),
]:
    ttl += f"""
term:{term}
    a skos:Concept ;
    skos:prefLabel "{pref}"@zh ;
    skos:exactMatch <http://example.org/plumblossom/{iri}> .

"""

# ---------- 5) Java ----------
jlines = []
for rid, cat, key, text, src in rows:
    jlines.append(f'        putXz("{cat}", "{key}", "{text}");')
java = "\n".join(jlines)

os.makedirs(os.path.join(BASE, "_gen", "out"), exist_ok=True)
open(os.path.join(BASE, "_gen", "out", "ddl.txt"), "w", encoding="utf-8").write(ddl)
open(os.path.join(BASE, "_gen", "out", "data.txt"), "w", encoding="utf-8").write(sql)
open(os.path.join(BASE, "_gen", "out", "obda.txt"), "w", encoding="utf-8").write(obda)
open(os.path.join(BASE, "_gen", "out", "ttl.txt"), "w", encoding="utf-8").write(ttl)
open(os.path.join(BASE, "_gen", "out", "java.txt"), "w", encoding="utf-8").write(java)
print("emitted to _gen/out/")
