-- ============================================================================
-- DDL：创建数据库与表（幂等，可重复执行）
--   * 只负责「结构」：建库 + 删除旧表 + 建表，不含任何业务数据。
--   * 紫微斗数（ZiWeiDouShu）第 11 步（闸门⑩）：把「查表型历法个体 + 安星/庙陷/四化/宫干/流曜起例 +
--     运行态命例/告警」典型 ABox 数据入库 MySQL，经 Ontop OBDA 虚拟访问。
--   * 依据：《本体核心设计》§8「规划表」（14 张）+「数据边界」注（DB 为唯一真源）；
--     《应用架构设计》§1「实现载体总则」、§9「API 复用声明」（★铁律 13/14：查表型外置、不建模冗余个体）。
--   * 闸门⑩裁决（2026-10-31·陈希夷）：使用 MySQL + Ontop OBDA；表结构「新建」，以 §8 规划表为准；
--     库名 ziweidoushudb。迁移边界＝查表型历法个体（天干/地支/六十甲子/纳音/五行局/十二长生/闰月/真太阳时）
--     一并自 ziweidoushu-abox.owl 迁出，DB 为唯一真源；静态术语（阴阳/五行/星曜名录/化气/四化类型/
--     十二宫义/告警标志）仍留 ABox。
--   * name 等名称列与本体个体本地名一致（如 Stem_Jia / Branch_Zi / Ju_Shui2 / Stage_ChangSheng /
--     JiaZi / NaYin_HaiZhongJin），本体实体 IRI 为短前缀形式，映射时由 <core:{name}> 拼回。
--     映射目标 IRI 以「裸写前缀名」生成（core:{name}），不加尖括号（§8 注：加尖括号会被 Ontop 误判为绝对 IRI）。
--   * 表间逻辑外键：sexagenary_cycle.{stem,branch,nayin}_local → ganzhi_stem/ganzhi_branch/nayin.name。
--   * 起例查找表（star_layout / brightness_map / sihua_map / palace_stem_map / deity_rule）为「非本体个体」
--     的内置映射/查表（§8 注、§5 内置映射表清单），供程序/规则层与 OBDA 查取，不在本体建冗余个体。
--   * 灌数据请使用同目录 data.sql（createdb 会先执行本文件、再执行 data.sql）。
-- ============================================================================

CREATE DATABASE IF NOT EXISTS `ziweidoushudb`
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE `ziweidoushudb`;

SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS `alert_flag`;
DROP TABLE IF EXISTS `case_record`;
DROP TABLE IF EXISTS `deity_rule`;
DROP TABLE IF EXISTS `palace_stem_map`;
DROP TABLE IF EXISTS `sihua_map`;
DROP TABLE IF EXISTS `brightness_map`;
DROP TABLE IF EXISTS `star_layout`;
DROP TABLE IF EXISTS `leap_month`;
DROP TABLE IF EXISTS `true_solar_time`;
DROP TABLE IF EXISTS `bureau_stage`;
DROP TABLE IF EXISTS `nayin`;
DROP TABLE IF EXISTS `sexagenary_cycle`;
DROP TABLE IF EXISTS `ganzhi_branch`;
DROP TABLE IF EXISTS `ganzhi_stem`;
SET FOREIGN_KEY_CHECKS = 1;

-- 1) 十天干（core:HeavenlyStem）
CREATE TABLE `ganzhi_stem` (
  `name`         VARCHAR(64) NOT NULL COMMENT '个体本地名（与本体一致，如 Stem_Jia）',
  `stem_name`    VARCHAR(16) DEFAULT '' COMMENT '天干名（甲…癸）→ core:stemName',
  `stem_yinyang` VARCHAR(8)  DEFAULT '' COMMENT '天干阴阳（阳/阴）→ core:stemYinYang',
  `yinyang`      VARCHAR(32) DEFAULT '' COMMENT '阴阳个体本地名（Yang/Yin）→ core:hasYinYang',
  `five_phase`   VARCHAR(32) DEFAULT '' COMMENT '五行个体本地名（Mu/Huo/Tu/Jin/Shui）→ core:hasFivePhase / core:stemFivePhase',
  `stem_order`   INT         DEFAULT 0  COMMENT '天干序（1..10；展示列，暂不映射）',
  `label`        VARCHAR(32) DEFAULT '' COMMENT '标签（甲…癸；展示列，暂不映射）',
  PRIMARY KEY (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='十天干表';

-- 2) 十二地支（core:EarthlyBranch）
CREATE TABLE `ganzhi_branch` (
  `name`          VARCHAR(64) NOT NULL COMMENT '个体本地名（如 Branch_Zi）',
  `branch_name`   VARCHAR(16) DEFAULT '' COMMENT '地支名（子…亥）→ core:branchName',
  `branch_animal` VARCHAR(16) DEFAULT '' COMMENT '生肖（鼠…猪）→ core:branchAnimal',
  `branch_order`  INT         DEFAULT 0  COMMENT '地支序（1..12）→ core:branchOrder',
  `five_phase`    VARCHAR(32) DEFAULT '' COMMENT '五行个体本地名 → core:hasFivePhase',
  `label`         VARCHAR(32) DEFAULT '' COMMENT '标签（子…亥；展示列，暂不映射）',
  PRIMARY KEY (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='十二地支表';

-- 3) 六十甲子（core:SexagenaryCycle；代表性，全集第 12 步）
CREATE TABLE `sexagenary_cycle` (
  `name`         VARCHAR(64) NOT NULL COMMENT '个体本地名（如 JiaZi）',
  `ganzhi`       VARCHAR(16) DEFAULT '' COMMENT '干支（甲子…；展示列，暂不映射）',
  `stem_local`   VARCHAR(64) DEFAULT '' COMMENT '天干个体本地名（ganzhi_stem.name）→ core:hasStem',
  `branch_local` VARCHAR(64) DEFAULT '' COMMENT '地支个体本地名（ganzhi_branch.name）→ core:hasBranch',
  `nayin_local`  VARCHAR(64) DEFAULT '' COMMENT '纳音个体本地名（nayin.name）→ core:hasNaYin',
  `label`        VARCHAR(32) DEFAULT '' COMMENT '标签（甲子…；展示列，暂不映射）',
  PRIMARY KEY (`name`),
  KEY `idx_stem` (`stem_local`),
  KEY `idx_branch` (`branch_local`),
  KEY `idx_nayin` (`nayin_local`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='六十甲子表（代表性；全集第 12 步）';

-- 4) 纳音（core:NaYin；代表性，全集第 12 步）
CREATE TABLE `nayin` (
  `name`       VARCHAR(64) NOT NULL COMMENT '个体本地名（如 NaYin_HaiZhongJin）',
  `nayin_name` VARCHAR(32) DEFAULT '' COMMENT '纳音名（海中金…）→ core:naYinName',
  `label`      VARCHAR(64) DEFAULT '' COMMENT '标签（展示列，暂不映射）',
  PRIMARY KEY (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='纳音表（代表性；全集第 12 步）';

-- 5) 五行局 / 十二长生（core:FivePhaseBureau / core:TwelveStage；kind 区分）
--     五行局→长生所起支：水二申/木三亥/金四巳/土五申/火六寅（卷二·起五行寅例/十二长生）
CREATE TABLE `bureau_stage` (
  `name`         VARCHAR(64) NOT NULL COMMENT '个体本地名（如 Ju_Shui2 / Stage_ChangSheng）',
  `kind`         VARCHAR(16) NOT NULL COMMENT '类别（bureau=五行局 / stage=十二长生）',
  `item_name`    VARCHAR(32) DEFAULT '' COMMENT '名（水二局… / 长生…）→ core:bureauName / core:stageName',
  `item_number`  INT         DEFAULT 0  COMMENT '序号（局数 2..6 / 长生序 1..12）→ core:bureauNumber',
  `start_branch` VARCHAR(16) DEFAULT '' COMMENT '长生所起支（申/亥/巳/申/寅）→ core:stageStartBranch（仅 bureau）',
  `label`        VARCHAR(32) DEFAULT '' COMMENT '标签（展示列，暂不映射）',
  PRIMARY KEY (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='五行局/十二长生表';

-- 6) 真太阳时校正参数（calendar:TrueSolarTime）
CREATE TABLE `true_solar_time` (
  `name`      VARCHAR(64) NOT NULL COMMENT '个体本地名（如 TrueSolar_Demo01）',
  `longitude` VARCHAR(32) DEFAULT '' COMMENT '出生地经度（度）→ calendar:longitude',
  `label`     VARCHAR(64) DEFAULT '' COMMENT '标签（展示列，暂不映射）',
  PRIMARY KEY (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='真太阳时校正参数表';

-- 7) 闰月归属（calendar:LeapMonth）
CREATE TABLE `leap_month` (
  `name`            VARCHAR(64) NOT NULL COMMENT '个体本地名（如 LeapMonth_ZhengYue）',
  `leap_month_rule` VARCHAR(32) DEFAULT '' COMMENT '闰月归属规则（按下月/按当月）→ calendar:leapMonthRule',
  `label`           VARCHAR(64) DEFAULT '' COMMENT '标签（展示列，暂不映射）',
  PRIMARY KEY (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='闰月归属表';

-- 8) 安星起例 / 紫微定位查找表（非本体个体；代表性，全集第 12 步）
--     紫微定位：由五行局与农历日安紫微（卷二·安紫微星定位表；★金四局初一在亥/火六局初一在酉）
CREATE TABLE `star_layout` (
  `rule_id`      VARCHAR(64) NOT NULL COMMENT '规则编号（如 ZLP_Jin4_C1）',
  `rule_kind`    VARCHAR(16) DEFAULT '' COMMENT '规则类（紫微定位/安星）',
  `bureau_name`  VARCHAR(32) DEFAULT '' COMMENT '五行局名（金四局…）',
  `lunar_day`    INT         DEFAULT 0  COMMENT '农历日（1..30）',
  `star_local`   VARCHAR(64) DEFAULT '' COMMENT '星个体本地名（ZiWei…）',
  `to_branch`    VARCHAR(16) DEFAULT '' COMMENT '所落地支（亥…）',
  `source_note`  VARCHAR(128) DEFAULT '' COMMENT '出处（卷二·安紫微星定位表 等）',
  PRIMARY KEY (`rule_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='安星起例/紫微定位查找表（非本体个体；代表性）';

-- 9) 庙陷表（非本体个体；星×宫→庙陷；代表性，全集第 12 步）
CREATE TABLE `brightness_map` (
  `map_id`          VARCHAR(64) NOT NULL COMMENT '映射编号（如 BM_ZiWei_Ming）',
  `star_local`      VARCHAR(64) DEFAULT '' COMMENT '星个体本地名（ZiWei…）',
  `palace_local`    VARCHAR(64) DEFAULT '' COMMENT '宫个体本地名（Palace_Ming…）',
  `brightness_name` VARCHAR(16) DEFAULT '' COMMENT '庙陷（庙/旺/得地/利益/平和/不得地/陷）',
  `source_note`     VARCHAR(128) DEFAULT '' COMMENT '出处（卷二·星辰屬性诸条；卷三·诸星庙陷）',
  PRIMARY KEY (`map_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='庙陷表（星×宫→庙陷；非本体个体；代表性）';

-- 10) 四化表（非本体个体；生年干→化禄/权/科/忌 之星）
CREATE TABLE `sihua_map` (
  `map_id`              VARCHAR(64) NOT NULL COMMENT '映射编号（如 SH_Jia_Lu）',
  `year_stem`           VARCHAR(16) DEFAULT '' COMMENT '生年干（甲…癸）',
  `transformation_type` VARCHAR(16) DEFAULT '' COMMENT '四化（化禄/化权/化科/化忌）',
  `star_local`          VARCHAR(64) DEFAULT '' COMMENT '所化星个体本地名（LianZhen…）',
  `source_note`         VARCHAR(128) DEFAULT '' COMMENT '出处（卷一·諸星問答論·四化）【庚干四化有异说·待考】',
  PRIMARY KEY (`map_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='四化表（生年干→四化星；非本体个体）';

-- 11) 宫干五鼠遁表（非本体个体；年干→起宫干）
CREATE TABLE `palace_stem_map` (
  `map_id`          VARCHAR(64) NOT NULL COMMENT '映射编号（如 PSM_JiaJi）',
  `year_stem_group` VARCHAR(32) DEFAULT '' COMMENT '年干组（甲己/乙庚/丙辛/丁壬/戊癸）',
  `start_stem`      VARCHAR(16) DEFAULT '' COMMENT '起宫干（丙/戊/庚/壬/甲）',
  `source_note`     VARCHAR(128) DEFAULT '' COMMENT '出处（卷二·推算宫干·五鼠遁）【待考·中】',
  PRIMARY KEY (`map_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='宫干五鼠遁表（年干→起宫干；非本体个体）';

-- 12) 流曜起例查找表（非本体个体；代表性，全集第 12 步）
CREATE TABLE `deity_rule` (
  `rule_id`     VARCHAR(64) NOT NULL COMMENT '起例编号（如 DR_Lu_Jia）',
  `rule_kind`   VARCHAR(32) DEFAULT '' COMMENT '起例类（流禄/流羊/流陀/十二神/斗君）',
  `base`        VARCHAR(64) DEFAULT '' COMMENT '基准（年干/年支/生月生时）',
  `deity_name`  VARCHAR(32) DEFAULT '' COMMENT '神煞名（流禄…）',
  `to_branch`   VARCHAR(16) DEFAULT '' COMMENT '所落地支（寅…）',
  `source_note` VARCHAR(128) DEFAULT '' COMMENT '出处（卷二·起例诸条）【待考·中】',
  PRIMARY KEY (`rule_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='流曜起例查找表（非本体个体；代表性）';

-- 13) 运行态命例记录（reading:CaseRecord；由 persistAndReturn 经 OBDA addComponent 落库，脱敏）
--     列名与程序写入契约一致：addComponent("case_record", ["birth_year","gender","chart_summary","reading"], ...)
CREATE TABLE `case_record` (
  `case_id`       INT          NOT NULL AUTO_INCREMENT COMMENT '命例自增主键',
  `birth_year`    VARCHAR(8)   DEFAULT '' COMMENT '生年（脱敏，仅年粒度）→ reading:caseBirthData',
  `gender`        VARCHAR(8)   DEFAULT '' COMMENT '性别（展示列，暂不映射）',
  `chart_summary` TEXT         COMMENT '命盘摘要（展示列，暂不映射）',
  `reading`       TEXT         COMMENT '批断结果 → reading:caseResult',
  `created_at`    DATE DEFAULT '2026-10-31' COMMENT '生成日期',
  PRIMARY KEY (`case_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='运行态命例记录表';

-- 14) 运行态告警标志（rules:AlertFlag；由流程按需落库）
CREATE TABLE `alert_flag` (
  `flag_id`         INT         NOT NULL AUTO_INCREMENT COMMENT '告警自增主键',
  `alert_type`      VARCHAR(32) DEFAULT '' COMMENT '告警类型（大凶/寿元关/重大疾病）→ rules:alertType',
  `alert_triggered` TINYINT     DEFAULT 0  COMMENT '是否触发 → rules:alertTriggered',
  `case_ref`        VARCHAR(64) DEFAULT '' COMMENT '关联命例（展示列，暂不映射）',
  `created_at`      DATE DEFAULT '2026-10-31' COMMENT '生成日期',
  PRIMARY KEY (`flag_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='运行态告警标志表';
