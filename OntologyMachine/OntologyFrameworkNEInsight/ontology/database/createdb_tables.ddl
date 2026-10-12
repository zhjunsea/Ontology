-- ============================================================================
-- DDL：创建数据库与表（幂等，可重复执行）
--   * 只负责「结构」：建库 + 删除旧表 + 建表，不含任何业务数据。
--   * 若库/表已存在：先删除旧表再重建（DROP TABLE IF EXISTS），保证每次都是干净结构。
--   * 表间依赖（逻辑外键）：引用方（如 neinsight_opportunity 引用 neinsight_domain）
--     删除按「引用方 → 被引用方」倒序、创建按「被引用方 → 引用方」正序。
--   * 灌数据请使用同目录 data.sql（createdb 会先执行本文件、再执行 data.sql）。
--   * 命名对齐本体（第 6/9 步 ABox）：个体本地名 → 各行 name/*_ref 列（IRI 模板 abox#）。
--   * 派生值（opportunityPriority / flaggedForAlert / smartCompliant）由 SWRL 规则推导，
--     非 ABox 断言；此处仅预留回写列（默认 NULL，不灌典型值），保持 .owl 与库两侧一致。
-- ============================================================================

-- 1) 创建数据库（不存在时）
CREATE DATABASE IF NOT EXISTS `neinsightdb`
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE `neinsightdb`;

-- 2) 删除旧表（存在则删除；先引用方后被引用方，关闭外键检查避免顺序问题）
SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS `neinsight_strategy`;
DROP TABLE IF EXISTS `neinsight_objective`;
DROP TABLE IF EXISTS `neinsight_control_point_type`;
DROP TABLE IF EXISTS `neinsight_control_point`;
DROP TABLE IF EXISTS `neinsight_span_quadrant`;
DROP TABLE IF EXISTS `neinsight_opportunity`;
DROP TABLE IF EXISTS `neinsight_capability`;
DROP TABLE IF EXISTS `neinsight_short_board`;
DROP TABLE IF EXISTS `neinsight_self_assessment`;
DROP TABLE IF EXISTS `neinsight_competition`;
DROP TABLE IF EXISTS `neinsight_customer_need`;
DROP TABLE IF EXISTS `neinsight_customer_pain`;
DROP TABLE IF EXISTS `neinsight_customer`;
DROP TABLE IF EXISTS `neinsight_market`;
DROP TABLE IF EXISTS `neinsight_value_transfer`;
DROP TABLE IF EXISTS `neinsight_macro_factor`;
DROP TABLE IF EXISTS `neinsight_trend`;
DROP TABLE IF EXISTS `neinsight_domain_element`;
DROP TABLE IF EXISTS `neinsight_domain`;
SET FOREIGN_KEY_CHECKS = 1;

-- 3) 洞察域（core#InsightDomain 子类）—— 被引用方
CREATE TABLE `neinsight_domain` (
  `domain_code` VARCHAR(50) NOT NULL COMMENT '洞察域编码（与个体本地名一致，如 IT_SPACE）',
  `name` VARCHAR(100) NOT NULL COMMENT '洞察域名称',
  `cls` VARCHAR(50) NOT NULL COMMENT '本体类本地名（ITSpace/CloudSpace/CombinedSpace）',
  `label` VARCHAR(100) DEFAULT '' COMMENT 'rdfs:label（中文）',
  PRIMARY KEY (`domain_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='洞察域表（IT 空间/云空间/联合空间）';

-- 4) 联合空间组成（core#hasElement，CombinedSpace → InsightDomain）
CREATE TABLE `neinsight_domain_element` (
  `domain_code` VARCHAR(50) NOT NULL COMMENT '联合空间编码（如 COMBINED_SPACE）',
  `element_code` VARCHAR(50) NOT NULL COMMENT '组成的洞察域编码（如 IT_SPACE/CLOUD_SPACE）',
  PRIMARY KEY (`domain_code`, `element_code`),
  KEY `idx_element` (`element_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='洞察域组成（hasElement）';

-- 5) 行业趋势（env#IndustryTrend）
CREATE TABLE `neinsight_trend` (
  `id` INT NOT NULL AUTO_INCREMENT COMMENT '自增主键',
  `name` VARCHAR(100) NOT NULL COMMENT '趋势个体本地名',
  `domain_code` VARCHAR(50) NOT NULL COMMENT '所属洞察域（belongsToDomain）',
  `trend_direction` VARCHAR(50) DEFAULT '' COMMENT '趋势方向',
  `value_transfer_ref` VARCHAR(100) DEFAULT NULL COMMENT '指示的价值转移（indicatesValueTransfer）',
  `label` VARCHAR(100) DEFAULT '' COMMENT 'rdfs:label（中文）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_name` (`name`),
  KEY `idx_domain` (`domain_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='行业趋势表';

-- 5b) 宏观因素（env#MacroFactor，⊂ env#IndustryTrend）
CREATE TABLE `neinsight_macro_factor` (
  `name` VARCHAR(100) NOT NULL COMMENT '宏观因素个体本地名',
  `trend_ref` VARCHAR(100) NOT NULL COMMENT '所属趋势（hasMacroFactor，Trend → MacroFactor）',
  `label` VARCHAR(100) DEFAULT '' COMMENT 'rdfs:label（中文）',
  PRIMARY KEY (`name`),
  KEY `idx_trend` (`trend_ref`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='宏观因素表';

-- 6) 价值转移（env#ValueTransfer）
CREATE TABLE `neinsight_value_transfer` (
  `name` VARCHAR(100) NOT NULL COMMENT '价值转移个体本地名',
  `label` VARCHAR(100) DEFAULT '' COMMENT 'rdfs:label（中文）',
  PRIMARY KEY (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='价值转移表';

-- 7) 市场与客户（env#Market / env#MarketCustomer）
CREATE TABLE `neinsight_market` (
  `name` VARCHAR(100) NOT NULL COMMENT '市场/市场客户个体本地名',
  `cls` VARCHAR(50) NOT NULL COMMENT '本体类本地名（Market/MarketCustomer）',
  `market_ref` VARCHAR(100) DEFAULT NULL COMMENT 'targetsMarket 目标（MarketCustomer → Market）',
  `label` VARCHAR(100) DEFAULT '' COMMENT 'rdfs:label（中文）',
  PRIMARY KEY (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='市场与市场客户表';

-- 8) 客户（env#Customer）
CREATE TABLE `neinsight_customer` (
  `name` VARCHAR(100) NOT NULL COMMENT '客户个体本地名',
  `label` VARCHAR(100) DEFAULT '' COMMENT 'rdfs:label（中文）',
  PRIMARY KEY (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='客户表';

-- 9) 客户痛点（env#CustomerPainPoint）
CREATE TABLE `neinsight_customer_pain` (
  `name` VARCHAR(100) NOT NULL COMMENT '痛点个体本地名',
  `customer_ref` VARCHAR(100) NOT NULL COMMENT '所属客户（hasPainPoint）',
  `pain_severity` INT DEFAULT 0 COMMENT '痛点严重度',
  `label` VARCHAR(100) DEFAULT '' COMMENT 'rdfs:label（中文）',
  PRIMARY KEY (`name`),
  KEY `idx_customer` (`customer_ref`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='客户痛点表';

-- 10) 客户需求（env#CustomerNeed）
CREATE TABLE `neinsight_customer_need` (
  `name` VARCHAR(100) NOT NULL COMMENT '需求个体本地名',
  `customer_ref` VARCHAR(100) NOT NULL COMMENT '所属客户（hasNeed）',
  `label` VARCHAR(100) DEFAULT '' COMMENT 'rdfs:label（中文）',
  PRIMARY KEY (`name`),
  KEY `idx_customer` (`customer_ref`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='客户需求表';

-- 11) 竞争格局与竞争对手（env#CompetitiveLandscape / env#Competitor）
CREATE TABLE `neinsight_competition` (
  `name` VARCHAR(100) NOT NULL COMMENT '竞争格局/对手个体本地名',
  `cls` VARCHAR(50) NOT NULL COMMENT '本体类本地名（CompetitiveLandscape/Competitor）',
  `parent_ref` VARCHAR(100) DEFAULT NULL COMMENT '竞争对手所属格局（competitorIn）',
  `label` VARCHAR(100) DEFAULT '' COMMENT 'rdfs:label（中文）',
  PRIMARY KEY (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='竞争格局与竞争对手表';

-- 12) 自身评估（env#SelfAssessment）
CREATE TABLE `neinsight_self_assessment` (
  `name` VARCHAR(100) NOT NULL COMMENT '自身评估个体本地名',
  `label` VARCHAR(100) DEFAULT '' COMMENT 'rdfs:label（中文）',
  PRIMARY KEY (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='自身评估表';

-- 13) 能力（env#Capability）
CREATE TABLE `neinsight_capability` (
  `name` VARCHAR(100) NOT NULL COMMENT '能力个体本地名',
  `assessment_ref` VARCHAR(100) NOT NULL COMMENT '所属评估（hasCapability）',
  `capability_score` INT DEFAULT 0 COMMENT '能力评分',
  `label` VARCHAR(100) DEFAULT '' COMMENT 'rdfs:label（中文）',
  PRIMARY KEY (`name`),
  KEY `idx_assessment` (`assessment_ref`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='能力表';

-- 13b) 短板（env#ShortBoard，⊂ env#SelfAssessment）
CREATE TABLE `neinsight_short_board` (
  `name` VARCHAR(100) NOT NULL COMMENT '短板个体本地名',
  `assessment_ref` VARCHAR(100) NOT NULL COMMENT '所属评估（hasShortBoard，SelfAssessment → ShortBoard）',
  `label` VARCHAR(100) DEFAULT '' COMMENT 'rdfs:label（中文）',
  PRIMARY KEY (`name`),
  KEY `idx_assessment` (`assessment_ref`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='短板表';

-- 14) SPAN 象限（env#SPANQuadrant，分类值类）
CREATE TABLE `neinsight_span_quadrant` (
  `name` VARCHAR(100) NOT NULL COMMENT '象限个体本地名',
  `label` VARCHAR(100) DEFAULT '' COMMENT 'rdfs:label（中文）',
  PRIMARY KEY (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='SPAN 象限表';

-- 15) 机会点（env#Opportunity）
CREATE TABLE `neinsight_opportunity` (
  `name` VARCHAR(100) NOT NULL COMMENT '机会点个体本地名',
  `domain_code` VARCHAR(50) NOT NULL COMMENT '所属洞察域（belongsToDomain）',
  `market_attractiveness` DECIMAL(4,2) DEFAULT NULL COMMENT '市场吸引力评分（SPAN 纵轴）',
  `competitive_position` DECIMAL(4,2) DEFAULT NULL COMMENT '竞争地位评分（SPAN 横轴）',
  `span_quadrant_ref` VARCHAR(100) NOT NULL COMMENT 'SPAN 象限（hasSpanQuadrant）',
  `priority` VARCHAR(20) DEFAULT NULL COMMENT '机会点优先级（SWRL 派生，回写列）',
  `flagged_for_alert` VARCHAR(5) DEFAULT NULL COMMENT '是否外发标记（SWRL 派生，回写列）',
  `label` VARCHAR(100) DEFAULT '' COMMENT 'rdfs:label（中文）',
  PRIMARY KEY (`name`),
  KEY `idx_domain` (`domain_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='机会点表（SPAN 两轴 + 象限）';

-- 16) 控制点类型（decision#ControlPointType，分类值类）
CREATE TABLE `neinsight_control_point_type` (
  `name` VARCHAR(100) NOT NULL COMMENT '控制点类型个体本地名',
  `cp_type_code` VARCHAR(50) NOT NULL COMMENT '控制点类型编码',
  `label` VARCHAR(100) DEFAULT '' COMMENT 'rdfs:label（中文）',
  PRIMARY KEY (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='战略控制点类型表';

-- 17) 战略控制点（decision#StrategicControlPoint）
CREATE TABLE `neinsight_control_point` (
  `name` VARCHAR(100) NOT NULL COMMENT '战略控制点个体本地名',
  `cp_type_ref` VARCHAR(100) NOT NULL COMMENT '控制点类型（hasControlPointType）',
  `opportunity_ref` VARCHAR(100) NOT NULL COMMENT '针对的机会点（addressesOpportunity）',
  `label` VARCHAR(100) DEFAULT '' COMMENT 'rdfs:label（中文）',
  PRIMARY KEY (`name`),
  KEY `idx_type` (`cp_type_ref`),
  KEY `idx_opportunity` (`opportunity_ref`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='战略控制点表';

-- 18) 目标（decision#Objective）
CREATE TABLE `neinsight_objective` (
  `name` VARCHAR(100) NOT NULL COMMENT '目标个体本地名',
  `metric` VARCHAR(200) DEFAULT '' COMMENT '衡量指标（hasMetric）',
  `target_value` DECIMAL(12,2) DEFAULT NULL COMMENT '目标值（targetValue）',
  `timeframe` VARCHAR(50) DEFAULT '' COMMENT '目标时限（hasTimeframe）',
  `is_measurable` VARCHAR(5) DEFAULT 'false' COMMENT '可衡量（isMeasurable）',
  `is_relevant` VARCHAR(5) DEFAULT 'false' COMMENT '相关性（isRelevant）',
  `smart_compliant` VARCHAR(5) DEFAULT NULL COMMENT '符合 SMART（SWRL 派生，回写列）',
  `label` VARCHAR(100) DEFAULT '' COMMENT 'rdfs:label（中文）',
  PRIMARY KEY (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='目标表（SMART）';

-- 19) 策略（decision#Strategy 子类）
CREATE TABLE `neinsight_strategy` (
  `name` VARCHAR(100) NOT NULL COMMENT '策略个体本地名',
  `cls` VARCHAR(50) NOT NULL COMMENT '本体类本地名（OrgStrategy/ProductStrategy/MarketStrategy）',
  `objective_ref` VARCHAR(100) NOT NULL COMMENT '所属目标（strategyOf）',
  `rationale` VARCHAR(200) DEFAULT '' COMMENT '策略依据（hasRationale）',
  `label` VARCHAR(100) DEFAULT '' COMMENT 'rdfs:label（中文）',
  PRIMARY KEY (`name`),
  KEY `idx_objective` (`objective_ref`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='策略表';
