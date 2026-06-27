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
  `status` VARCHAR(50) DEFAULT '可用' COMMENT '状态（可用/过期/待检/停用）',
  `purchase_date` DATE DEFAULT NULL COMMENT '进货日期',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_name` (`name`),
  KEY `idx_type` (`type`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci 
  COMMENT='披萨组件信息表';

-- 可选：插入示例数据（请根据实际需要调整）
-- INSERT INTO component (name, type, price, supplier, shelf_life_days, batch_number, status, purchase_date) VALUES
-- ('NeapolitanCrust', '饼底', 10.00, '面粉供应商A', 7, 'B2025001', '可用', '2025-06-01'),
-- ('MozzarellaCheese', '奶酪', 15.00, '奶酪供应商B', 30, 'C2025001', '可用', '2025-06-05');

-- 插入披萨饼底测试数据
INSERT INTO pizza_components (name, type, price, supplier, shelf_life_days, batch_number, status, purchase_date) VALUES
('NeapolitanCrust', '饼底', 10.00, '意大利面粉厂', 7, 'BN20250601', '可用', '2025-06-01'),
('NewYorkCrust', '饼底', 8.50, '纽约面粉公司', 5, 'BN20250602', '可用', '2025-06-02'),
('ChicagoDeepDishCrust', '饼底', 12.00, '芝加哥谷物供应商', 6, 'BN20250603', '可用', '2025-06-03'),
('DetroitCrust', '饼底', 9.00, '底特律原料商', 5, 'BN20250604', '可用', '2025-06-04'),
('StLouisCrackerCrust', '饼底', 7.50, '圣路易斯面粉厂', 4, 'BN20250605', '可用', '2025-06-05'),
('SicilianCrust', '饼底', 11.00, '西西里面粉供应商', 6, 'BN20250606', '可用', '2025-06-06'),
('RomanTondaCrust', '饼底', 9.50, '罗马面粉公司', 5, 'BN20250607', '可用', '2025-06-07'),
('RomanAlTaglioCrust', '饼底', 10.50, '罗马原料商', 5, 'BN20250608', '可用', '2025-06-08'),
('FrenchThinCrust', '饼底', 8.00, '法国面粉厂', 4, 'BN20250609', '可用', '2025-06-09'),
('ArgentinianThickCrust', '饼底', 11.50, '阿根廷谷物供应商', 6, 'BN20250610', '可用', '2025-06-10');

-- 插入酱汁测试数据
INSERT INTO pizza_components (name, type, price, supplier, shelf_life_days, batch_number, status, purchase_date) VALUES
('NeapolitanTomatoSauce', '酱汁', 4.50, '圣马扎诺番茄农场', 14, 'SC20250601', '可用', '2025-06-01'),
('AmericanTomatoSauce', '酱汁', 3.50, '加州番茄加工厂', 21, 'SC20250602', '可用', '2025-06-02'),
('WhiteSauce', '酱汁', 5.00, '乳品供应商A', 10, 'SC20250603', '可用', '2025-06-03'),
('OtherSauce', '酱汁', 4.00, '通用调味品公司', 30, 'SC20250604', '可用', '2025-06-04');

-- 插入奶酪测试数据
INSERT INTO pizza_components (name, type, price, supplier, shelf_life_days, batch_number, status, purchase_date) VALUES
('BuffaloMozzarella', '奶酪', 15.00, '坎帕尼亚水牛乳品', 14, 'CH20250601', '可用', '2025-06-01'),
('LowMoistureMozzarella', '奶酪', 10.00, '威斯康星奶酪厂', 30, 'CH20250602', '可用', '2025-06-02'),
('Parmesan', '奶酪', 12.00, '帕尔马奶酪公司', 90, 'CH20250603', '可用', '2025-06-03'),
('ProvelCheese', '奶酪', 9.00, '圣路易斯乳制品', 20, 'CH20250604', '可用', '2025-06-04');

-- 插入肉类配料测试数据
INSERT INTO pizza_components (name, type, price, supplier, shelf_life_days, batch_number, status, purchase_date) VALUES
('Pepperoni', '配料', 8.00, '肉类供应商C', 30, 'MT20250601', '可用', '2025-06-01'),
('Salami', '配料', 7.50, '萨拉米加工厂', 30, 'MT20250602', '可用', '2025-06-02'),
('Ham', '配料', 9.00, '火腿制造商', 25, 'MT20250603', '可用', '2025-06-03'),
('Bacon', '配料', 10.00, '培根供应商', 20, 'MT20250604', '可用', '2025-06-04'),
('ChickenTeriyaki', '配料', 9.50, '鸡肉加工厂', 15, 'MT20250605', '可用', '2025-06-05'),
('RoastDuck', '配料', 18.00, '烤鸭专供店', 10, 'MT20250606', '可用', '2025-06-06'),
('SpicyBeef', '配料', 14.00, '牛肉供应商', 20, 'MT20250607', '可用', '2025-06-07'),
('Anchovy', '配料', 8.50, '海鲜供应商A', 14, 'MT20250608', '可用', '2025-06-08');

-- 插入蔬菜配料测试数据
INSERT INTO pizza_components (name, type, price, supplier, shelf_life_days, batch_number, status, purchase_date) VALUES
('Mushroom', '配料', 3.00, '蘑菇农场', 7, 'VT20250601', '可用', '2025-06-01'),
('Olive', '配料', 2.50, '橄榄供应商', 30, 'VT20250602', '可用', '2025-06-02'),
('BellPepper', '配料', 5.00, '蔬菜配送公司', 7, 'VT20250603', '可用', '2025-06-03'),
('Onion', '配料', 2.00, '洋葱种植户', 14, 'VT20250604', '可用', '2025-06-04'),
('Spinach', '配料', 3.50, '绿叶蔬菜农场', 5, 'VT20250605', '待检', '2025-06-05'),
('Corn', '配料', 3.00, '玉米供应商', 10, 'VT20250606', '可用', '2025-06-06');

-- 插入海鲜配料测试数据
INSERT INTO pizza_components (name, type, price, supplier, shelf_life_days, batch_number, status, purchase_date) VALUES
('Shrimp', '配料', 15.00, '海鲜供应商B', 5, 'SF20250601', '可用', '2025-06-07'),
('Squid', '配料', 12.00, '海鲜供应商C', 5, 'SF20250602', '可用', '2025-06-08'),
('Clam', '配料', 11.00, '贝类供应商', 5, 'SF20250603', '可用', '2025-06-09');

-- 插入香草配料测试数据
INSERT INTO pizza_components (name, type, price, supplier, shelf_life_days, batch_number, status, purchase_date) VALUES
('Basil', '配料', 1.50, '香草园', 5, 'HB20250601', '可用', '2025-06-01'),
('Oregano', '配料', 1.00, '香草园', 7, 'HB20250602', '可用', '2025-06-02'),
('Garlic', '配料', 2.00, '大蒜农场', 20, 'HB20250603', '可用', '2025-06-03');

-- 插入水果配料测试数据
INSERT INTO pizza_components (name, type, price, supplier, shelf_life_days, batch_number, status, purchase_date) VALUES
('Pineapple', '配料', 4.00, '热带水果公司', 10, 'FR20250601', '可用', '2025-06-01'),
('Durian', '配料', 20.00, '榴莲进口商', 5, 'FR20250602', '可用', '2025-06-02');