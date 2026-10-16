-- ============================================================================
-- DDL：创建数据库与表（幂等，可重复执行）
--   * 只负责「结构」：建库 + 删除旧表 + 建表，不含任何业务数据。
--   * 若库/表已存在：先删除旧表再重建（DROP TABLE IF EXISTS），保证每次都是干净结构。
--   * NEInsight 不内置业务实例数据（内部知识基线不通用）：仅保留洞察域相关表
--     （neinsight_domain / neinsight_domain_element）；业务数据由应用按方法论联网检索采集。
--   * 灌数据请使用同目录 data.sql（createdb 会先执行本文件、再执行 data.sql）。
--   * 命名对齐本体 ABox：个体本地名 → 各行 name/*_ref 列（IRI 模板 abox#）。
-- ============================================================================

-- 1) 创建数据库（不存在时）
CREATE DATABASE IF NOT EXISTS `neinsightdb`
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE `neinsightdb`;

-- 2) 删除旧表（存在则删除；先引用方后被引用方，关闭外键检查避免顺序问题）
SET FOREIGN_KEY_CHECKS = 0;
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
