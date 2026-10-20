-- ============================================================================
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
  `name`        VARCHAR(64) NOT NULL COMMENT '个体本地名（如 Dictum_Hex_01）',
  `dictum_text` TEXT COMMENT '卦辞文本',
  `label`       VARCHAR(64) DEFAULT '' COMMENT '标签',
  PRIMARY KEY (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='周易卦辞表';

-- 8) 周易爻辞
CREATE TABLE `yijing_line_dictum` (
  `name`        VARCHAR(64) NOT NULL COMMENT '个体本地名（如 LineDictum_01_1）',
  `position`    INT         DEFAULT 0  COMMENT '爻位（1..6）',
  `dictum_text` TEXT COMMENT '爻辞文本',
  `label`       VARCHAR(64) DEFAULT '' COMMENT '标签',
  PRIMARY KEY (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='周易爻辞表';

-- 9) 运行态占断结果（典型样本；jud:PB_*）
CREATE TABLE `divination_result` (
  `pb_name`            VARCHAR(64)  NOT NULL COMMENT '占断实例名（jud:PB_<...>）',
  `divination_system`  VARCHAR(16)  DEFAULT '' COMMENT '先天/后天体系（jud:divinationSystem）',
  `method`             VARCHAR(32)  DEFAULT '' COMMENT '起卦法本地名（jud:hasMethod；div:DivinationMethod 子类，如 YearMonthDayHourMethod/YicuoJinMethod）',
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
