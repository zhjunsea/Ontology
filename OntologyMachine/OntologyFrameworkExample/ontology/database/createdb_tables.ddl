-- ============================================================================
-- DDL：创建数据库与表（幂等，可重复执行）
--   * 只负责「结构」：建库 + 删除旧表 + 建表，不含任何业务数据。
--   * 若库/表已存在：先删除旧表再重建（DROP TABLE IF EXISTS），保证每次都是干净结构。
--   * 表间依赖（逻辑外键）：myPizza 引用 pizza_components / crust_components，
--     故删除按「引用方 → 被引用方」倒序、创建按「被引用方 → 引用方」正序。
--   * 灌数据请使用同目录 data.sql（createdb.py 会先执行本文件、再执行 data.sql）。
-- ============================================================================

-- 1) 创建数据库（不存在时）
CREATE DATABASE IF NOT EXISTS `mypizzadb`
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE `mypizzadb`;

-- 2) 删除旧表（存在则删除）
--    顺序：先删引用方 myPizza，再删被引用方 crust_components / pizza_components。
--    临时关闭外键检查，避免存在外键约束时因删除顺序而失败。
SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS `myPizza`;
DROP TABLE IF EXISTS `crust_components`;
DROP TABLE IF EXISTS `pizza_components`;
SET FOREIGN_KEY_CHECKS = 1;

-- 3) 创建被引用方：披萨组件表
CREATE TABLE `pizza_components` (
  `id` INT NOT NULL AUTO_INCREMENT COMMENT '自增主键',
  `name` VARCHAR(100) NOT NULL COMMENT '组件名称（与本体个体名称一致）',
  `type` VARCHAR(255) NOT NULL COMMENT '组件类型（饼底/酱汁/奶酪/配料）',
  `price` DECIMAL(10,2) DEFAULT 0 COMMENT '进货单价（人民币元）',
  `supplier` VARCHAR(100) DEFAULT '' COMMENT '供应商名称',
  `shelf_life_days` INT DEFAULT 0 COMMENT '保质期（天数）',
  `batch_number` VARCHAR(100) DEFAULT '' COMMENT '批次编号',
  `status` VARCHAR(20) DEFAULT '不可用' COMMENT '状态（可用/过期/待检/停用）',
  `purchase_date` DATE DEFAULT '1970-01-01' COMMENT '进货日期',
  `stock_quantity` INT DEFAULT 0 COMMENT '当前库存数量（个或克）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_name` (`name`),
  KEY `idx_type` (`type`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='披萨组件信息表';

-- 4) 创建被引用方：饼底专属属性表
CREATE TABLE `crust_components` (
  `name` VARCHAR(100) NOT NULL COMMENT '饼底名称（与本体个体本地名一致，如 NeapolitanCrustInstance）',
  `crust_thickness_mm` FLOAT NULL COMMENT '饼底厚度(毫米)',
  `baking_temperature_celsius` INT COMMENT '烘烤温度(°C)',
  `baking_time_seconds` INT COMMENT '烘烤时间(秒)',
  `flour_type` VARCHAR(100) COMMENT '面粉种类',
  `fermentation` VARCHAR(100) COMMENT '发酵方式',
  `status` VARCHAR(20) DEFAULT '可用' COMMENT '状态：可用/过期/待检/停用/其他',
  `supplier` VARCHAR(100) DEFAULT '' COMMENT '供应商',
  `batch_number` VARCHAR(100) DEFAULT '' COMMENT '批次编号',
  `price` DECIMAL(10,2) DEFAULT 0 COMMENT '进货单价（元）',
  `purchase_date` DATE DEFAULT '1970-01-01' COMMENT '进货日期',
  `shelf_life_days` INT DEFAULT 0 COMMENT '保质期（天）',
  `stock_quantity` INT DEFAULT 0 COMMENT '当前库存数量',
  PRIMARY KEY (`name`),
  CONSTRAINT `chk_crust_thickness_positive` CHECK (`crust_thickness_mm` IS NULL OR `crust_thickness_mm` > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='饼底专属属性表';

-- 5) 创建引用方：披萨产品表（放在最后，确保被引用表已存在）
CREATE TABLE `myPizza` (
  `name` VARCHAR(100) NOT NULL COMMENT '披萨名称',
  `type` VARCHAR(50) NOT NULL COMMENT '披萨种类',
  `price` DECIMAL(10,2) COMMENT '出售单价（人民币元）',
  `production_date` DATE COMMENT '生产日期',
  `crust_name` VARCHAR(100) NOT NULL COMMENT 'Pizza饼底',
  `cheese_name` VARCHAR(100) COMMENT 'Pizza奶酪',
  `sauce_name` VARCHAR(100) COMMENT 'Pizza酱汁',
  `topping_name` VARCHAR(100) COMMENT 'Pizza配料'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='披萨产品表';
