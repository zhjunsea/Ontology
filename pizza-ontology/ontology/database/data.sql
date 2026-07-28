DELETE from pizza_components;
DELETE from crust_component;
-- ============================================================================
-- 1. 插入所有披萨组件（pizza_components），name 均以 Instance 结尾
-- ============================================================================
INSERT INTO pizza_components (name, type, status, supplier, batch_number, price, purchase_date, shelf_life_days, stock_quantity) VALUES

-- -------------------- 饼底（Crust 子类） --------------------
('NeapolitanCrustInstance',      'NeapolitanCrust',      '可用', '那不勒斯面粉厂', 'C001', 10.00, '2025-01-01', 7,  50),
('NewYorkCrustInstance',         'NewYorkCrust',         '可用', '纽约面粉公司',   'C002',  8.50, '2025-01-01', 5,  40),
('ChicagoDeepDishCrustInstance', 'ChicagoDeepDishCrust', '可用', '芝加哥谷物供应商','C003', 12.00, '2025-01-01', 6,  15),
('DetroitCrustInstance',         'DetroitCrust',         '可用', '底特律原料商',   'C004',  9.00, '2025-01-01', 5,  35),
('StLouisCrackerCrustInstance',  'StLouisCrackerCrust',  '可用', '圣路易斯面粉厂', 'C005',  7.50, '2025-01-01', 4,  60),
('SicilianCrustInstance',        'SicilianCrust',        '可用', '西西里面粉供应商','C006', 11.00, '2025-01-01', 6,  25),
('RomanTondaCrustInstance',      'RomanTondaCrust',      '可用', '罗马面粉公司',   'C007',  9.50, '2025-01-01', 5,  45),
('RomanAlTaglioCrustInstance',   'RomanAlTaglioCrust',   '可用', '罗马原料商',     'C008', 10.50, '2025-01-01', 5,  20),
('FrenchThinCrustInstance',      'FrenchThinCrust',      '可用', '法国面粉厂',     'C009',  8.00, '2025-01-01', 4,  50),
('ArgentinianThickCrustInstance','ArgentinianThickCrust','可用', '阿根廷谷物供应商','C010', 11.50, '2025-01-01', 6,  15),

-- -------------------- 酱汁（Sauce 子类） --------------------
('NeapolitanTomatoSauceInstance', 'NeapolitanTomatoSauce', '可用', '圣马扎诺番茄农场', 'S001', 4.50, '2025-01-01', 14, 100),
('AmericanTomatoSauceInstance',   'AmericanTomatoSauce',   '可用', '加州番茄加工厂',   'S002', 3.50, '2025-01-01', 21, 200),
('WhiteSauceInstance',            'WhiteSauce',            '可用', '乳品供应商A',      'S003', 5.00, '2025-01-01', 10,  80),
('OtherSauceInstance',            'OtherSauce',            '可用', '通用调味品公司',    'S004', 4.00, '2025-01-01', 30,  50),

-- -------------------- 奶酪（Cheese 子类） --------------------
('BuffaloMozzarellaInstance',     'BuffaloMozzarella',     '可用', '坎帕尼亚水牛乳品', 'CH01', 15.00, '2025-01-01', 14,  8),
('LowMoistureMozzarellaInstance', 'LowMoistureMozzarella', '可用', '威斯康星奶酪厂',   'CH02', 10.00, '2025-01-01', 30, 100),
('ParmesanInstance',              'Parmesan',              '可用', '帕尔马奶酪公司',    'CH03', 12.00, '2025-01-01', 90,  30),
('ProvelCheeseInstance',          'ProvelCheese',          '可用', '圣路易斯乳制品',    'CH04',  9.00, '2025-01-01', 20,  25),

-- -------------------- 肉类配料（MeatTopping） --------------------
('PepperoniInstance',       'MeatTopping', '可用', '肉类供应商C', 'M001',  5.0, '2025-01-01', 365, 100),
('SalamiInstance',          'MeatTopping', '可用', '萨拉米加工厂','M002',  7.5, '2025-01-01', 30,   80),
('HamInstance',             'MeatTopping', '可用', '火腿制造商',  'M003',  9.0, '2025-01-01', 25,  120),
('BaconInstance',           'MeatTopping', '可用', '培根供应商',  'M004', 10.0, '2025-01-01', 20,   90),
('ChickenTeriyakiInstance', 'MeatTopping', '可用', '鸡肉加工厂',  'M005',  9.5, '2025-01-01', 15,   70),
('RoastDuckInstance',       'MeatTopping', '可用', '烤鸭专供店',  'M006', 18.0, '2025-01-01', 10,   60),
('SpicyBeefInstance',       'MeatTopping', '可用', '牛肉供应商',  'M007', 14.0, '2025-01-01', 20,   65),

-- -------------------- 海鲜配料（SeafoodTopping） --------------------
('AnchovyInstance',   'SeafoodTopping', '可用', '海鲜供应商A', 'SF01',  8.5, '2025-01-01', 14,  50),
('ShrimpInstance',    'SeafoodTopping', '可用', '海鲜供应商B', 'SF02',  9.0, '2025-01-01', 30,  40),
('SquidInstance',     'SeafoodTopping', '可用', '海鲜供应商C', 'SF03', 12.0, '2025-01-01',  5,  35),
('ClamInstance',      'SeafoodTopping', '可用', '贝类供应商',  'SF04', 11.0, '2025-01-01',  5,  45),

-- -------------------- 蔬菜配料（VegetableTopping） --------------------
('MushroomInstance',    'VegetableTopping', '可用', '蘑菇农场',      'V001', 3.0, '2025-01-01', 90, 150),
('OliveInstance',       'VegetableTopping', '可用', '橄榄供应商',    'V002', 2.5, '2025-01-01', 30, 200),
('BellPepperInstance',  'VegetableTopping', '可用', '蔬菜配送公司',  'V003', 5.0, '2025-01-01',  7, 110),
('OnionInstance',       'VegetableTopping', '可用', '洋葱种植户',    'V004', 2.0, '2025-01-01', 14, 300),
('SpinachInstance',     'VegetableTopping', '可用', '绿叶蔬菜农场',  'V005', 3.5, '2025-01-01',  5, 130),
('CornInstance',        'VegetableTopping', '可用', '玉米供应商',    'V006', 3.0, '2025-01-01', 10, 170),

-- -------------------- 香草配料（HerbTopping） --------------------
('BasilInstance',   'HerbTopping', '可用', '香草园',     'H001', 1.0, '2025-01-01', 30, 200),
('OreganoInstance', 'HerbTopping', '可用', '香草园',     'H002', 1.2, '2025-01-01', 30, 180),
('GarlicInstance',  'HerbTopping', '可用', '大蒜农场',   'H003', 2.0, '2025-01-01', 20, 150),

-- -------------------- 水果配料（FruitTopping） --------------------
('PineappleInstance', 'FruitTopping', '可用', '热带水果公司', 'F001', 2.8, '2025-01-01', 60,  90),
('DurianInstance',    'FruitTopping', '可用', '榴莲进口商',   'F002',20.0, '2025-01-01',  5,  25);


-- ============================================================================
-- 2. 插入饼底专属属性（crust_component），name 同步加 Instance 后缀
-- ============================================================================
INSERT INTO crust_components (name, crust_thickness_mm, baking_temperature_celsius, baking_time_seconds, flour_type, fermentation) VALUES
('NeapolitanCrustInstance',      5.0,  465,  75,  '00细面粉',   '长时间低温发酵'),
('NewYorkCrustInstance',         4.5,  250, 300,  '高筋粉',     '常温发酵'),
('ChicagoDeepDishCrustInstance', 60.0, 220, 1800, '高筋面包粉', '长时间发酵'),
('DetroitCrustInstance',         25.0, 260, 600,  '高筋粉',     '短时间发酵'),
('StLouisCrackerCrustInstance',  4.0,  230, 240,  '中筋粉',     '无发酵'),
('SicilianCrustInstance',        25.0, 220, 900,  '高筋粉',     '长时间发酵'),
('RomanTondaCrustInstance',      4.0,  280, 180,  '中筋粉',     '短时间发酵'),
('RomanAlTaglioCrustInstance',   25.0, 230, 600,  '高筋粉',     '长时间发酵'),
('FrenchThinCrustInstance',      3.0,  250, 120,  '中筋粉',     '短时间发酵'),
('ArgentinianThickCrustInstance',30.0, 220, 900,  '高筋粉',     '长时间发酵');

INSERT INTO myPizza (name, type, price, production_date, crust_ID, cheese_ID, sauce_ID, topping_ID) VALUES
-- 1. MargheritaPizza: NeapolitanCrust + NeapolitanTomatoSauce + BuffaloMozzarella + Basil(唯一)
('经典玛格丽特',       'MargheritaPizza',        68.00, '2026-07-25', 1461, 1475, 1471, 1496),

-- 2. GenericNeapolitanPizza: NeapolitanCrust + NeapolitanTomatoSauce + BuffaloMozzarella + Mushroom(≥1)
('那不勒斯蘑菇披萨',    'GenericNeapolitanPizza',  72.00, '2026-07-26', 1461, 1475, 1471, 1490),

-- 3. ChicagoDeepDishPizza: ChicagoDeepDishCrust + AmericanTomatoSauce + LowMoistureMozzarella + Salami
('芝加哥深盘经典',      'ChicagoDeepDishPizza',    88.00, '2026-07-24', 1463, 1476, 1472, 1480),

-- 4. DetroitPizza: DetroitCrust + AmericanTomatoSauce + LowMoistureMozzarella + Bacon
('底特律方形培根披萨',   'DetroitPizza',            78.00, '2026-07-27', 1464, 1476, 1472, 1482),

-- 5. NewYorkPizza: NewYorkCrust + AmericanTomatoSauce + LowMoistureMozzarella + Pepperoni
('纽约薄饼辣肠披萨',    'NewYorkPizza',            65.00, '2026-07-26', 1462, 1476, 1472, 1479),

-- 6. StLouisPizza: StLouisCrackerCrust + OtherSauce + ProvelCheese + Ham
('圣路易斯火腿披萨',    'StLouisPizza',            62.00, '2026-07-25', 1465, 1478, 1474, 1481),

-- 7. SicilianPizza: SicilianCrust + AmericanTomatoSauce + Parmesan + Onion
('西西里厚底洋葱披萨',   'SicilianPizza',           75.00, '2026-07-23', 1466, 1477, 1472, 1493),

-- 8. PekingDuckPizza: FrenchThinCrust(中式融合) + OtherSauce + LowMoistureMozzarella + RoastDuck
('北京烤鸭披萨',        'PekingDuckPizza',         98.00, '2026-07-27', 1469, 1476, 1474, 1484),

-- 9. DurianPizza: ArgentinianThickCrust(中式融合) + OtherSauce + LowMoistureMozzarella + Durian
('猫山王榴莲披萨',      'DurianPizza',             108.00,'2026-07-26', 1470, 1476, 1474, 1500),

-- 10. MarinaraPizza: RomanTondaCrust + NeapolitanTomatoSauce + NULL(无奶酪) + Garlic
('玛瑞纳拉素食披萨',    'MarinaraPizza',           48.00, '2026-07-27', 1467, NULL, 1471, 1498);