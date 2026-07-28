-- 创建数据库
CREATE DATABASE IF NOT EXISTS `myPizzaDB` 
  DEFAULT CHARACTER SET utf8mb4 
  COLLATE utf8mb4_unicode_ci;

USE `myPizzaDB`;

-- 创建披萨组件表
CREATE TABLE `pizza_components` (
  `id` INT NOT NULL AUTO_INCREMENT COMMENT '自增主键',
  `name` VARCHAR(100) NOT NULL COMMENT '组件名称（与本体个体名称一致）',
  `type` VARCHAR(50) DEFAULT NULL COMMENT '组件类型（饼底/酱汁/奶酪/配料）',
  `price` DECIMAL(10,2) NOT NULL COMMENT '进货单价（人民币元）',
  `supplier` VARCHAR(100) DEFAULT NULL COMMENT '供应商名称',
  `shelf_life_days` INT DEFAULT NULL COMMENT '保质期（天数）',
  `batch_number` VARCHAR(100) DEFAULT NULL COMMENT '批次编号',
  `status` VARCHAR(50) DEFAULT '不可用' COMMENT '状态（可用/过期/待检/停用）',
  `purchase_date` DATE DEFAULT NULL COMMENT '进货日期',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_name` (`name`),
  KEY `idx_type` (`type`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci 
  COMMENT='披萨组件信息表';
  
-- 新增存量列（若已存在可跳过）
ALTER TABLE pizza_components 
  ADD COLUMN stock_quantity INT DEFAULT 0 COMMENT '当前库存数量（个或克）';
  
ALTER TABLE pizza_components 
MODIFY COLUMN type VARCHAR(255) NOT NULL;
-- 为确保映射时 COALESCE 有效，以下列均设置默认值（可选，但推荐）
ALTER TABLE pizza_components 
  MODIFY COLUMN supplier VARCHAR(100) DEFAULT '' COMMENT '供应商',
  MODIFY COLUMN batch_number VARCHAR(100) DEFAULT '' COMMENT '批次编号',
  MODIFY COLUMN price DECIMAL(10,2) DEFAULT 0 COMMENT '进货单价',
  MODIFY COLUMN purchase_date DATE DEFAULT '1970-01-01' COMMENT '进货日期',
  MODIFY COLUMN shelf_life_days INT DEFAULT 0 COMMENT '保质期天数',
  MODIFY COLUMN stock_quantity INT DEFAULT 0 COMMENT '存量',
  MODIFY COLUMN status VARCHAR(20) DEFAULT '不可用' COMMENT '状态：可用/不可用';

CREATE TABLE crust_components (
    name VARCHAR(100) PRIMARY KEY COMMENT '饼底名称（与本体个体本地名一致，如 NeapolitanCrust）',
    crust_thickness_mm FLOAT COMMENT '饼底厚度(毫米)',
    baking_temperature_celsius INT COMMENT '烘烤温度(°C)',
    baking_time_seconds INT COMMENT '烘烤时间(秒)',
    flour_type VARCHAR(100) COMMENT '面粉种类',
    fermentation VARCHAR(100) COMMENT '发酵方式',
    status VARCHAR(20) DEFAULT '可用' COMMENT '状态：可用/过期/待检/停用/其他',
    supplier VARCHAR(100) DEFAULT '' COMMENT '供应商',
    batch_number VARCHAR(100) DEFAULT '' COMMENT '批次编号',
    price DECIMAL(10,2) DEFAULT 0 COMMENT '进货单价（元）',
    purchase_date DATE DEFAULT '1970-01-01' COMMENT '进货日期',
    shelf_life_days INT DEFAULT 0 COMMENT '保质期（天）',
    stock_quantity INT DEFAULT 0 COMMENT '当前库存数量'
);

UPDATE pizza_components
SET 
    type = name,           -- 第一步：先将原始 name 赋值给 type
    name = CONCAT(name, 'Instance'); -- 第二步：此时等号右边的 name 仍是原始值，拼接后缀
    
ALTER TABLE crust_components
    MODIFY COLUMN crust_thickness_mm FLOAT NOT NULL,
    ADD CONSTRAINT chk_crust_thickness_positive CHECK (crust_thickness_mm > 0);
    
ALTER TABLE crust_components 
    MODIFY COLUMN crust_thickness_mm FLOAT NULL,
    DROP CONSTRAINT chk_crust_thickness_positive,
    ADD CONSTRAINT chk_crust_thickness_positive CHECK (crust_thickness_mm IS NULL OR crust_thickness_mm > 0);
    
CREATE TABLE myPizza (
    name            VARCHAR(100)    NOT NULL COMMENT '披萨名称',
    type            VARCHAR(50)     NOT NULL COMMENT '披萨种类',
    price           DECIMAL(10,2)   COMMENT '出售单价（人民币元）',
    production_date DATE            COMMENT '生产日期',
    crust_name      VARCHAR(100)    NOT NULL COMMENT 'Pizza饼底',
    cheese_name     VARCHAR(100)    COMMENT 'Pizza奶酪',
    sauce_name      VARCHAR(100)    COMMENT 'Pizza酱汁',
    topping_name    VARCHAR(100)    COMMENT 'Pizza配料'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='披萨产品表';