#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""由 plumblossom-abox.owl 生成第 11 步数据库工件 createdb_tables.ddl / data.sql。
目标：历法(38) + 周易文本(78) 入库（本体会同步移除这 116 个体，DB 为唯一真源）。
本体实体 IRI 为短前缀形式（如 cal:Stem_Jia / yijing:Seq_01），DB 的 name 列存「本地名」
（冒号后），由 OBDA 映射用 <cal:{name}> / <yijing:{name}> 拼回同一 IRI。
"""
import os
from rdflib import Graph, RDF, RDFS, OWL, URIRef

BASE = r"D:\work\Ontology\OntologyMachine\OntologyFrameworkPlumBlossom\ontology"
OUT = os.path.join(BASE, "database")
os.makedirs(OUT, exist_ok=True)

RULES_NS = "http://example.org/plumblossom/rules#"
NAMED_NS = ("http://example.org/plumblossom/core/", "http://example.org/plumblossom/divination/",
            "http://example.org/plumblossom/yijing/", "http://example.org/plumblossom/relations/",
            "http://example.org/plumblossom/symbols/", "http://example.org/plumblossom/omens/",
            "http://example.org/plumblossom/judgement/", "http://example.org/plumblossom/calendars/")

def short(uri):
    if not isinstance(uri, URIRef):
        return uri
    s = str(uri)
    if s.startswith(RULES_NS):
        return s[len(RULES_NS):]
    for ns in NAMED_NS:
        if s.startswith(ns):
            return s[len(ns):]
    return s

def local(uri):
    s = str(uri)
    return s.split(":", 1)[1] if ":" in s else s

g = Graph()
g.parse(os.path.join(BASE, "plumblossom-abox.owl"), format="xml")

def val(ind, prop):
    for o in g.objects(ind, URIRef(prop)):
        return o
    return None

def sval(ind, prop):
    o = val(ind, prop)
    return None if o is None else str(o)

def lval(ind, prop):
    o = val(ind, prop)
    return None if o is None else local(o)

def sq(x):
    if x is None:
        return "NULL"
    return "'" + str(x).replace("\\", "\\\\").replace("'", "''") + "'"

def inds_of(type_local):
    for ind in g.subjects(RDF.type, OWL.NamedIndividual):
        for t in g.objects(ind, RDF.type):
            if t == OWL.NamedIndividual:
                continue
            if short(t) == type_local:
                yield ind

CAL = "http://example.org/plumblossom/calendars/"
YJ = "http://example.org/plumblossom/yijing/"

stems = sorted(inds_of("cal:HeavenlyStem"), key=str)
branches = sorted(inds_of("cal:EarthlyBranch"), key=lambda i: int(sval(i, CAL + "zhiOrder") or 0))
nayins = sorted(inds_of("cal:NaYin"), key=str)
cycles = sorted(inds_of("cal:SexagenaryCycle"), key=str)
lifes = sorted(inds_of("cal:LifeStage"), key=str)
seqs = sorted(inds_of("yijing:HexagramSequence"), key=str)
dictums = sorted(inds_of("yijing:HexagramDictum"), key=str)
lines = sorted(inds_of("yijing:LineDictum"), key=str)
print("counts:", len(stems), len(branches), len(nayins), len(cycles), len(lifes),
      len(seqs), len(dictums), len(lines))

# ---------------- DDL ----------------
ddl = """-- ============================================================================
-- DDL：创建数据库与表（幂等，可重复执行）
--   * 只负责「结构」：建库 + 删除旧表 + 建表，不含任何业务数据。
--   * 梅花易数（PlumBlossom）第 11 步：把「历法/干支」与「周易文本（卦名/卦辞/爻辞）」
--     典型 ABox 数据入库（设计《本体核心设计》§8、《本体顶层设计》§1：历法不迁入本体文件）。
--   * name / pb_name 等名称列与本体个体本地名一致（如 Seq_01 / NaYin_HaiZhongJin）；
--     本体实体 IRI 为短前缀形式，映射时由 <cal:{name}> / <yijing:{name}> 拼回。
--   * 表间逻辑外键：sexagenary_cycle.nayin_name → nayin.name。
--   * 灌数据请使用同目录 data.sql（createdb 会先执行本文件、再执行 data.sql）。
-- ============================================================================

CREATE DATABASE IF NOT EXISTS `plumblossomdb`
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE `plumblossomdb`;

SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS `divination_result`;
DROP TABLE IF EXISTS `yijing_line_dictum`;
DROP TABLE IF EXISTS `yijing_hexagram_dictum`;
DROP TABLE IF EXISTS `yijing_hexagram_sequence`;
DROP TABLE IF EXISTS `calendar_sexagenary_cycle`;
DROP TABLE IF EXISTS `calendar_nayin`;
DROP TABLE IF EXISTS `calendar_life_stage`;
DROP TABLE IF EXISTS `calendar_earthly_branch`;
DROP TABLE IF EXISTS `calendar_heavenly_stem`;
SET FOREIGN_KEY_CHECKS = 1;

-- 1) 十天干
CREATE TABLE `calendar_heavenly_stem` (
  `name`       VARCHAR(64) NOT NULL COMMENT '个体本地名（与本体一致，如 Stem_Jia）',
  `stem_name`  VARCHAR(16) DEFAULT '' COMMENT '天干名（甲…癸）',
  `five_phase` VARCHAR(32) DEFAULT '' COMMENT '天干五行本地名（rel: 个体，如 Mu）',
  `label`      VARCHAR(32) DEFAULT '' COMMENT '标签',
  PRIMARY KEY (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='十天干表';

-- 2) 十二地支
CREATE TABLE `calendar_earthly_branch` (
  `name`          VARCHAR(64) NOT NULL COMMENT '个体本地名（如 Branch_Zi）',
  `branch_name`   VARCHAR(16) DEFAULT '' COMMENT '地支名（子…亥）',
  `branch_animal` VARCHAR(16) DEFAULT '' COMMENT '生肖',
  `zhi_order`     INT         DEFAULT 0  COMMENT '地支序（1..12）',
  `five_phase`    VARCHAR(32) DEFAULT '' COMMENT '地支五行本地名（rel: 个体）',
  `label`         VARCHAR(32) DEFAULT '' COMMENT '标签',
  PRIMARY KEY (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='十二地支表';

-- 3) 六十甲子
CREATE TABLE `calendar_sexagenary_cycle` (
  `name`       VARCHAR(64) NOT NULL COMMENT '个体本地名（如 Cycle_JiaZi）',
  `ganzhi`     VARCHAR(16) DEFAULT '' COMMENT '干支（甲子…）',
  `nayin_name` VARCHAR(64) DEFAULT '' COMMENT '纳音本地名（calendar_nayin.name，逻辑外键）',
  `label`      VARCHAR(32) DEFAULT '' COMMENT '标签',
  PRIMARY KEY (`name`),
  KEY `idx_nayin` (`nayin_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='六十甲子表';

-- 4) 纳音
CREATE TABLE `calendar_nayin` (
  `name`       VARCHAR(64) NOT NULL COMMENT '个体本地名（如 NaYin_HaiZhongJin）',
  `nayin_name` VARCHAR(32) DEFAULT '' COMMENT '纳音名（海中金…）',
  `five_phase` VARCHAR(32) DEFAULT '' COMMENT '纳音五行本地名（rel: 个体）',
  `label`      VARCHAR(32) DEFAULT '' COMMENT '标签',
  PRIMARY KEY (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='纳音表';

-- 5) 五行长生
CREATE TABLE `calendar_life_stage` (
  `name`            VARCHAR(64) NOT NULL COMMENT '个体本地名（如 Life_Mu）',
  `life_stage_text` VARCHAR(64) DEFAULT '' COMMENT '长生文本（木生在亥…）',
  `label`           VARCHAR(64) DEFAULT '' COMMENT '标签',
  PRIMARY KEY (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='五行长生表';

-- 6) 六十四卦序/名录
CREATE TABLE `yijing_hexagram_sequence` (
  `name`          VARCHAR(64) NOT NULL COMMENT '个体本地名（如 Seq_01）',
  `sequence_no`   INT         DEFAULT 0  COMMENT '卦序（1..64）',
  `hexagram_name` VARCHAR(32) DEFAULT '' COMMENT '卦名（乾为天…）',
  `label`         VARCHAR(64) DEFAULT '' COMMENT '标签（1.乾为天）',
  PRIMARY KEY (`name`),
  KEY `idx_seq` (`sequence_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='六十四卦序名录表';

-- 7) 周易卦辞
CREATE TABLE `yijing_hexagram_dictum` (
  `name`        VARCHAR(64) NOT NULL COMMENT '个体本地名（如 Dictum_Qian）',
  `dictum_text` TEXT COMMENT '卦辞文本',
  `label`       VARCHAR(64) DEFAULT '' COMMENT '标签',
  PRIMARY KEY (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='周易卦辞表';

-- 8) 周易爻辞
CREATE TABLE `yijing_line_dictum` (
  `name`        VARCHAR(64) NOT NULL COMMENT '个体本地名（如 LineDictum_Qian_1）',
  `position`    INT         DEFAULT 0  COMMENT '爻位（1..6）',
  `dictum_text` TEXT COMMENT '爻辞文本',
  `label`       VARCHAR(64) DEFAULT '' COMMENT '标签',
  PRIMARY KEY (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='周易爻辞表';

-- 9) 运行态占断结果（典型样本；jud:PB_*）
CREATE TABLE `divination_result` (
  `pb_name`            VARCHAR(64)  NOT NULL COMMENT '占断实例名（jud:PB_<...>）',
  `divination_system`  VARCHAR(16)  DEFAULT '' COMMENT '先天/后天体系（jud:divinationSystem）',
  `method`             VARCHAR(32)  DEFAULT '' COMMENT '起卦法本地名（jud:hasMethod；div: 个体，如 TimeCast）',
  `subject_category`   VARCHAR(32)  DEFAULT '' COMMENT '事项类别本地名（jud:Cat_*）',
  `auspiciousness`     VARCHAR(32)  DEFAULT '' COMMENT '吉凶等级本地名（jud:DaJi/…）',
  `is_major_result`    TINYINT      DEFAULT 0  COMMENT '重大结果（jud:isMajorResult）',
  `uses_zhouyi_dictum` TINYINT      DEFAULT 0  COMMENT '是否引周易卦爻辞（jud:usesZhouyiDictum）',
  `response_period`    VARCHAR(16)  DEFAULT '' COMMENT '应期档位（近/中/远；展示列，暂不映射）',
  `process_flag`       VARCHAR(16)  DEFAULT '' COMMENT '过程标记（jud:processFlag）',
  `description`        VARCHAR(255) DEFAULT '' COMMENT '判词/过程描述（jud:processDescription）',
  `created_at`         DATE DEFAULT '2026-10-20' COMMENT '生成日期',
  PRIMARY KEY (`pb_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='运行态占断结果表（典型样本）';
"""
with open(os.path.join(OUT, "createdb_tables.ddl"), "w", encoding="utf-8", newline="\n") as f:
    f.write(ddl)

# ---------------- data.sql ----------------
s = []
s.append("-- 清空旧数据（便于本文件重复执行）")
for t in ["divination_result", "yijing_line_dictum", "yijing_hexagram_dictum", "yijing_hexagram_sequence",
          "calendar_sexagenary_cycle", "calendar_nayin", "calendar_life_stage",
          "calendar_earthly_branch", "calendar_heavenly_stem"]:
    s.append(f"DELETE FROM {t};")
s.append("")
s.append("-- ============================================================================")
s.append("-- 1. 十天干（10） / 十二地支（12） / 纳音（6） / 六十甲子（6） / 五行长生（4）")
s.append("-- ============================================================================")

s.append("INSERT INTO calendar_heavenly_stem (name, stem_name, five_phase, label) VALUES")
s.append(",\n".join(f"({sq(local(i))}, {sq(sval(i, CAL+'stemName'))}, {sq(lval(i, CAL+'stemFivePhase'))}, {sq(sval(i, RDFS.label))})" for i in stems) + ";")
s.append("")
s.append("INSERT INTO calendar_earthly_branch (name, branch_name, branch_animal, zhi_order, five_phase, label) VALUES")
s.append(",\n".join(f"({sq(local(i))}, {sq(sval(i, CAL+'branchName'))}, {sq(sval(i, CAL+'branchAnimal'))}, {sval(i, CAL+'zhiOrder') or 0}, {sq(lval(i, CAL+'branchFivePhase'))}, {sq(sval(i, RDFS.label))})" for i in branches) + ";")
s.append("")
s.append("INSERT INTO calendar_nayin (name, nayin_name, five_phase, label) VALUES")
s.append(",\n".join(f"({sq(local(i))}, {sq(sval(i, CAL+'naYinName'))}, {sq(lval(i, CAL+'naYinPhase'))}, {sq(sval(i, RDFS.label))})" for i in nayins) + ";")
s.append("")
s.append("INSERT INTO calendar_sexagenary_cycle (name, ganzhi, nayin_name, label) VALUES")
s.append(",\n".join(f"({sq(local(i))}, {sq(sval(i, RDFS.label))}, {sq(lval(i, CAL+'hasNaYin'))}, {sq(sval(i, RDFS.label))})" for i in cycles) + ";")
s.append("")
s.append("INSERT INTO calendar_life_stage (name, life_stage_text, label) VALUES")
s.append(",\n".join(f"({sq(local(i))}, {sq(sval(i, CAL+'lifeStageText'))}, {sq(sval(i, RDFS.label))})" for i in lifes) + ";")
s.append("")
s.append("-- ============================================================================")
s.append("-- 2. 周易文本：六十四卦序（64） / 卦辞（2） / 爻辞（12）")
s.append("-- ============================================================================")
s.append("INSERT INTO yijing_hexagram_sequence (name, sequence_no, hexagram_name, label) VALUES")
rows = []
for i in seqs:
    labels = [str(o) for o in g.objects(i, RDFS.label)]
    dotted = [l for l in labels if "." in l]
    plain = [l for l in labels if "." not in l]
    label = dotted[0] if dotted else (labels[0] if labels else "")
    name = plain[0] if plain else (label.split(".", 1)[-1] if "." in label else label)
    rows.append(f"({sq(local(i))}, {sval(i, YJ+'hexagramSequence') or 0}, {sq(name)}, {sq(label)})")
s.append(",\n".join(rows) + ";")
s.append("")
s.append("INSERT INTO yijing_hexagram_dictum (name, dictum_text, label) VALUES")
s.append(",\n".join(f"({sq(local(i))}, {sq(sval(i, YJ+'hexagramDictumText'))}, {sq(sval(i, RDFS.label))})" for i in dictums) + ";")
s.append("")
s.append("INSERT INTO yijing_line_dictum (name, position, dictum_text, label) VALUES")
s.append(",\n".join(f"({sq(local(i))}, {sval(i, YJ+'lineDictumPosition') or 0}, {sq(sval(i, YJ+'lineDictumText'))}, {sq(sval(i, RDFS.label))})" for i in lines) + ";")
s.append("")
s.append("-- ============================================================================")
s.append("-- 3. 运行态占断结果（典型样本；jud:PB_*）")
s.append("-- ============================================================================")
s.append("INSERT INTO divination_result (pb_name, divination_system, method, subject_category, auspiciousness, is_major_result, uses_zhouyi_dictum, response_period, process_flag, description, created_at) VALUES")
pb = [
    ("PB_QianBodyUse_Bihe",    "先天", "TimeCast", "Cat_TianShi", "Ji",      0, 1, "近", "体用比和", "乾为天卦体用比和，百事顺遂，所谋可成。", "2026-10-20"),
    ("PB_DuiYongShengTi",      "先天", "TimeCast", "Cat_QiuCai",  "DaJi",    1, 1, "近", "用生体",   "兑为泽卦用生体，进益之喜，财帛有得。", "2026-10-20"),
    ("PB_ZhenTiKeYong",        "先天", "TimeCast", "Cat_ChuXing", "Ji",      0, 1, "中", "体克用",   "震为雷卦体克用，诸事小吉，出行顺利。", "2026-10-20"),
    ("PB_LiTiShengYong",       "先天", "TimeCast", "Cat_WuShe",   "Ping",    0, 1, "中", "体生用",   "离为火卦体生用，有耗失之患，宜守不宜进。", "2026-10-20"),
    ("PB_KanYongKeTi",         "先天", "TimeCast", "Cat_JiBing",  "DaXiong", 1, 1, "远", "用克体",   "坎为水卦用克体，诸事凶险，病讼当防。", "2026-10-20"),
    ("PB_YiCuoJin_ShangShang", "后天", "YicuoJin", "Cat_JiaoYi",  "DaJi",    1, 0, "近", "一撮金上上", "一撮金得上上之数，交关大利。", "2026-10-20"),
]
s.append(",\n".join(f"({sq(a)}, {sq(b)}, {sq(c)}, {sq(d)}, {sq(e)}, {f_}, {g_}, {sq(h)}, {sq(i_)}, {sq(j)}, {sq(k)})"
                    for (a, b, c, d, e, f_, g_, h, i_, j, k) in pb) + ";")
s.append("")

with open(os.path.join(OUT, "data.sql"), "w", encoding="utf-8", newline="\n") as f:
    f.write("\n".join(s) + "\n")

print("WROTE createdb_tables.ddl + data.sql")
