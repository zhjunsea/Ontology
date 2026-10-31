-- ============================================================================
-- DDL：创建数据库与表（幂等，可重复执行）
--   * 只负责「结构」：建库 + 删除旧表 + 建表，不含任何业务数据。
--   * 四柱命理（SiZhuBaZi）第 11 步（闸门⑩）：把「查表型历法/神煞起例/十神映射/运行态命例」
--     典型 ABox 数据入库 MySQL，经 Ontop OBDA 虚拟访问。
--   * 依据：《本体核心设计》§8「规划表」（11 张）+「数据边界」注（DB 为唯一真源）；
--     《本体顶层设计》§1/§ABox（查表型历法/神煞数据入库，不迁入本体文件，★铁律 13/14）。
--   * name 等名称列与本体个体本地名一致（如 Stem_Jia / JiaZi / NaYin_HaiZhongJin / Term_LiChun），
--     本体实体 IRI 为短前缀形式，映射时由 <core:{name}> / <calendar:{name}> 拼回。
--   * 数据边界（A 迁移口径）：天干/地支/六十甲子/纳音/藏干/节气/月建等「查表型历法」个体
--     已自 sizhubazi-abox.owl 迁出，DB 为唯一真源；阴阳/五行/十神名录/宫位/六亲/层次/告警标志/
--     格局名录/出处/命例结构/神煞名录/长生十二宫等「静态术语」仍留 ABox。
--   * 表间逻辑外键：sexagenary_cycle.{stem,branch,nayin}_local → ganzhi_stem/ganzhi_branch/nayin.name；
--     ganzhi_*._local / nayin.five_phase → 五行留 ABox（core:{Mu..Shui}），此处仅存本地名。
--   * deity_rule / ten_god_map 为「内置映射/查表」（非本体个体，§5/§8 注），供程序/规则层查取，无 RDF 映射。
--   * 灌数据请使用同目录 data.sql（createdb 会先执行本文件、再执行 data.sql）。
-- ============================================================================

CREATE DATABASE IF NOT EXISTS `sizhubazidb`
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE `sizhubazidb`;

SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS `alert_flag`;
DROP TABLE IF EXISTS `case_record`;
DROP TABLE IF EXISTS `ten_god_map`;
DROP TABLE IF EXISTS `deity_rule`;
DROP TABLE IF EXISTS `month_order`;
DROP TABLE IF EXISTS `solar_term`;
DROP TABLE IF EXISTS `hidden_stem`;
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
  `five_phase`   VARCHAR(32) DEFAULT '' COMMENT '五行个体本地名（Mu/Huo/Tu/Jin/Shui）→ core:hasFivePhase',
  `stem_order`   INT         DEFAULT 0  COMMENT '天干序（1..10；展示列，暂不映射）',
  `label`        VARCHAR(32) DEFAULT '' COMMENT '标签（甲…癸）',
  PRIMARY KEY (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='十天干表';

-- 2) 十二地支（core:EarthlyBranch）
CREATE TABLE `ganzhi_branch` (
  `name`          VARCHAR(64) NOT NULL COMMENT '个体本地名（如 Branch_Zi）',
  `branch_name`   VARCHAR(16) DEFAULT '' COMMENT '地支名（子…亥）→ core:branchName',
  `branch_animal` VARCHAR(16) DEFAULT '' COMMENT '生肖 → core:branchAnimal',
  `branch_order`  INT         DEFAULT 0  COMMENT '地支序（1..12）→ core:branchOrder',
  `five_phase`    VARCHAR(32) DEFAULT '' COMMENT '五行个体本地名 → core:hasFivePhase',
  `label`         VARCHAR(32) DEFAULT '' COMMENT '标签（子…亥）',
  PRIMARY KEY (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='十二地支表';

-- 3) 六十甲子（core:SexagenaryCycle）
CREATE TABLE `sexagenary_cycle` (
  `name`         VARCHAR(64) NOT NULL COMMENT '个体本地名（如 JiaZi）',
  `ganzhi`       VARCHAR(16) DEFAULT '' COMMENT '干支（甲子…）→ rdfs:label',
  `stem_local`   VARCHAR(64) DEFAULT '' COMMENT '天干个体本地名（ganzhi_stem.name）→ core:hasStem',
  `branch_local` VARCHAR(64) DEFAULT '' COMMENT '地支个体本地名（ganzhi_branch.name）→ core:hasBranch',
  `nayin_local`  VARCHAR(64) DEFAULT '' COMMENT '纳音个体本地名（nayin.name）→ core:hasNaYin',
  `label`        VARCHAR(32) DEFAULT '' COMMENT '标签（甲子…）',
  PRIMARY KEY (`name`),
  KEY `idx_stem` (`stem_local`),
  KEY `idx_branch` (`branch_local`),
  KEY `idx_nayin` (`nayin_local`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='六十甲子表';

-- 4) 纳音（core:NaYin）
CREATE TABLE `nayin` (
  `name`         VARCHAR(64) NOT NULL COMMENT '个体本地名（如 NaYin_HaiZhongJin）',
  `nayin_name`   VARCHAR(32) DEFAULT '' COMMENT '纳音名（海中金…）→ core:naYinName',
  `nayin_nature` VARCHAR(16) DEFAULT '' COMMENT '纳音吉凶（吉/平/凶）→ core:naYinNature',
  `five_phase`   VARCHAR(32) DEFAULT '' COMMENT '纳音五行个体本地名 → core:hasFivePhase',
  `label`        VARCHAR(64) DEFAULT '' COMMENT '标签',
  PRIMARY KEY (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='纳音表';

-- 5) 地支藏干（core:HiddenStem + core:hasHiddenStem 关系）
CREATE TABLE `hidden_stem` (
  `name`         VARCHAR(64) NOT NULL COMMENT '个体本地名（如 ZiHidden / YinHiddenJia）',
  `role`         VARCHAR(64) DEFAULT '' COMMENT '藏干/人元司事说明 → core:hiddenStemRole',
  `branch_local` VARCHAR(64) DEFAULT '' COMMENT '所属地支个体本地名（ganzhi_branch.name）→ core:hasHiddenStem',
  `label`        VARCHAR(64) DEFAULT '' COMMENT '标签',
  PRIMARY KEY (`name`),
  KEY `idx_branch` (`branch_local`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='地支藏干表';

-- 6) 二十四节气（calendar:SolarTerm；代表性）
CREATE TABLE `solar_term` (
  `name`            VARCHAR(64) NOT NULL COMMENT '个体本地名（如 Term_LiChun）',
  `solar_term_name` VARCHAR(16) DEFAULT '' COMMENT '节气名（立春…大寒）→ calendar:solarTermName',
  `term_order`      INT         DEFAULT 0  COMMENT '节气序（1..24；展示列，暂不映射）',
  `label`           VARCHAR(32) DEFAULT '' COMMENT '标签',
  PRIMARY KEY (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='二十四节气表';

-- 7) 十二月建（calendar:MonthOrder）
CREATE TABLE `month_order` (
  `name`             VARCHAR(64) NOT NULL COMMENT '个体本地名（如 MonthYin）',
  `month_order_name` VARCHAR(16) DEFAULT '' COMMENT '月建地支名（寅…丑）→ calendar:monthOrderName',
  `label`            VARCHAR(32) DEFAULT '' COMMENT '标签',
  PRIMARY KEY (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='十二月建表';

-- 8) 神煞起例（内置查表，非本体个体；供程序/规则层按柱干支查神煞）
CREATE TABLE `deity_rule` (
  `rule_id`     VARCHAR(64) NOT NULL COMMENT '起例编号（如 DR_TianYi_Year）',
  `base_pillar` VARCHAR(16) DEFAULT '' COMMENT '起例基准柱（年/月/日/时/日干）',
  `base_ganzhi` VARCHAR(32) DEFAULT '' COMMENT '基准干支或日干（如 甲/戊/庚）',
  `deity_name`  VARCHAR(32) DEFAULT '' COMMENT '神煞名（天乙贵人…）',
  `deity_alias` VARCHAR(64) DEFAULT '' COMMENT '异名（异名异法并陈）',
  `branch_hit`  VARCHAR(64) DEFAULT '' COMMENT '命中地支（如 丑未）',
  `category`    VARCHAR(32) DEFAULT '' COMMENT '类别（贵人吉神/禄马/凶煞/空亡）',
  `source_note` VARCHAR(128) DEFAULT '' COMMENT '出处（三命通会卷二·诸神煞篇 等）',
  PRIMARY KEY (`rule_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='神煞起例查找表（非本体个体）';

-- 9) 十神映射（内置查表，非本体个体；日干×目标干→十神）
CREATE TABLE `ten_god_map` (
  `map_id`      VARCHAR(64) NOT NULL COMMENT '映射编号（如 TGM_Jia_Jia）',
  `day_stem`    VARCHAR(16) DEFAULT '' COMMENT '日干名（甲…癸）',
  `target_stem` VARCHAR(16) DEFAULT '' COMMENT '目标干名（甲…癸）',
  `ten_god`     VARCHAR(16) DEFAULT '' COMMENT '所主十神名（比肩…正印）',
  `source_note` VARCHAR(128) DEFAULT '' COMMENT '出处（渊海子平卷之一·论日为主 等）',
  PRIMARY KEY (`map_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='十神映射查找表（非本体个体）';

-- 10) 运行态命例记录（reading:CaseRecord；由 persistAndReturn 经 OBDA addComponent 落库）
CREATE TABLE `case_record` (
  `case_id`     INT          NOT NULL AUTO_INCREMENT COMMENT '命例自增主键',
  `birth_data`  VARCHAR(255) DEFAULT '' COMMENT '生辰（含出生地、不脱敏，㉓）→ reading:caseBirthData',
  `birth_place` VARCHAR(128) DEFAULT '' COMMENT '出生地（展示列，暂不映射）',
  `four_pillars` VARCHAR(64) DEFAULT '' COMMENT '四柱干支串（展示列，暂不映射）',
  `reading`     TEXT COMMENT '批断结果 → reading:caseResult',
  `created_at`  DATE DEFAULT '2026-10-29' COMMENT '生成日期',
  PRIMARY KEY (`case_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='运行态命例记录表';

-- 11) 运行态告警标志（rules:AlertFlag；由流程按需落库）
CREATE TABLE `alert_flag` (
  `flag_id`         INT          NOT NULL AUTO_INCREMENT COMMENT '告警自增主键',
  `alert_type`      VARCHAR(32) DEFAULT '' COMMENT '告警类型（大凶/寿元关/重大疾病）→ rules:alertType',
  `alert_triggered` TINYINT     DEFAULT 0  COMMENT '是否触发 → rules:alertTriggered',
  `case_ref`        VARCHAR(64) DEFAULT '' COMMENT '关联命例（展示列，暂不映射）',
  `created_at`      DATE DEFAULT '2026-10-29' COMMENT '生成日期',
  PRIMARY KEY (`flag_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='运行态告警标志表';
