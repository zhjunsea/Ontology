-- ============================================================================
-- data.sql：典型 ABox 数据入库（严格镜像 ontology/neinsight-abox.owl 的 44 个个体断言）
--   * 只灌「断言事实」，不灌 SWRL 派生值（priority/flagged_for_alert/smart_compliant 留 NULL）。
--   * name/*_ref 列值 = 本体个体本地名，OBDA 映射以 IRI 模板 abox# 还原为个体 IRI。
--   * 可重复执行：先按「引用方 → 被引用方」倒序清空，再插入。
-- ============================================================================

DELETE FROM neinsight_strategy;
DELETE FROM neinsight_objective;
DELETE FROM neinsight_control_point;
DELETE FROM neinsight_control_point_type;
DELETE FROM neinsight_opportunity;
DELETE FROM neinsight_span_quadrant;
DELETE FROM neinsight_short_board;
DELETE FROM neinsight_capability;
DELETE FROM neinsight_self_assessment;
DELETE FROM neinsight_competition;
DELETE FROM neinsight_customer_need;
DELETE FROM neinsight_customer_pain;
DELETE FROM neinsight_customer;
DELETE FROM neinsight_market;
DELETE FROM neinsight_macro_factor;
DELETE FROM neinsight_value_transfer;
DELETE FROM neinsight_trend;
DELETE FROM neinsight_domain_element;
DELETE FROM neinsight_domain;

-- ===== 洞察域（IT 空间 / 云空间 / 联合空间）=====
INSERT INTO neinsight_domain (domain_code, name, cls, label) VALUES
('IT_SPACE',      'IT 空间',   'ITSpace',       'IT 空间'),
('CLOUD_SPACE',   '云空间',    'CloudSpace',    '云空间'),
('COMBINED_SPACE','联合空间',  'CombinedSpace', '联合空间');

INSERT INTO neinsight_domain_element (domain_code, element_code) VALUES
('COMBINED_SPACE', 'IT_SPACE'),
('COMBINED_SPACE', 'CLOUD_SPACE');

-- ===== 看行业/趋势 =====
INSERT INTO neinsight_value_transfer (name, label) VALUES
('VT_NewPowerSystem', '价值转移方向-新型电力系统'),
('VT_EdgeToCloud',    '价值转移方向-边缘到云'),
('VT_AICloud',        '价值转移方向-AI 云');

INSERT INTO neinsight_trend (name, domain_code, trend_direction, value_transfer_ref, label) VALUES
('Trend_DoubleCarbon',    'IT_SPACE',    '',           'VT_NewPowerSystem', '双碳政策趋势'),
('Trend_NewPowerSystem',  'CLOUD_SPACE', '',           NULL,                '新型电力系统趋势'),
('Trend_ITInfra',         'IT_SPACE',    '国产化替代', 'VT_EdgeToCloud',    'IT 基础设施国产化趋势'),
('Trend_CloudNative',     'CLOUD_SPACE', '云原生普及', 'VT_AICloud',        '云原生趋势');

INSERT INTO neinsight_macro_factor (name, trend_ref, label) VALUES
('MF_Sovereignty', 'Trend_ITInfra',   '自主可控宏观因素'),
('MF_AIDemand',    'Trend_CloudNative','AI 算力需求因素');

-- ===== 看市场/客户 =====
INSERT INTO neinsight_market (name, cls, market_ref, label) VALUES
('Market_NewEnergyIT', 'Market',         NULL,                 '新能源 IT 市场'),
('MC_1',               'MarketCustomer', 'Market_NewEnergyIT', '市场与客户聚合'),
('Market_SmartGrid',   'Market',         NULL,                 '智能电网市场');

INSERT INTO neinsight_customer (name, label) VALUES
('Customer_PowerGen',     '新能源发电企业'),
('Customer_GridOperator', '电网运营企业');

INSERT INTO neinsight_customer_pain (name, customer_ref, pain_severity, label) VALUES
('Pain_DataIsland', 'Customer_PowerGen',     4, '数据孤岛'),
('Pain_GridSilo',   'Customer_GridOperator', 3, '数据烟囱');

INSERT INTO neinsight_customer_need (name, customer_ref, label) VALUES
('Need_CloudPlatform', 'Customer_PowerGen',     '能源云与算力需求'),
('Need_UnifiedOAM',    'Customer_GridOperator', '统一运维需求');

-- ===== 看竞争 =====
INSERT INTO neinsight_competition (name, cls, parent_ref, label) VALUES
('CL_1',   'CompetitiveLandscape', NULL,   '竞争格局'),
('Comp_1', 'Competitor',           'CL_1', '竞争对手（不指名）'),
('CL_2',   'CompetitiveLandscape', NULL,   '云数据平台竞争格局'),
('Comp_2', 'Competitor',           'CL_2', '竞争对手（不指名）B');

-- ===== 看自己 =====
INSERT INTO neinsight_self_assessment (name, label) VALUES
('Self_1', '自身评估'),
('Self_2', '自身评估（云交付）');

INSERT INTO neinsight_capability (name, assessment_ref, capability_score, label) VALUES
('Cap_HuaweiICT', 'Self_1', 4, '华为 ICT 能力'),
('Cap_Delivery',  'Self_2', 3, '云交付能力');

INSERT INTO neinsight_short_board (name, assessment_ref, label) VALUES
('SB_Talent', 'Self_2', '云人才短板');

-- ===== 看机会（SPAN）=====
INSERT INTO neinsight_span_quadrant (name, label) VALUES
('Q_HighHigh', 'SPAN 象限-高吸引力高地位'),
('Q_LowLow',   'SPAN 象限-低吸引力低地位');

INSERT INTO neinsight_opportunity
  (name, domain_code, market_attractiveness, competitive_position, span_quadrant_ref, priority, flagged_for_alert, label) VALUES
('Opp_SmartEnergyCloud',  'CLOUD_SPACE', 4.5, 4.0, 'Q_HighHigh', NULL, NULL, '智能能源云机会点'),
('Opp_ITLegacy',          'IT_SPACE',    2.0, 1.5, 'Q_LowLow',   NULL, NULL, '传统 IT 集成机会点'),
('Opp_CloudDataPlatform', 'CLOUD_SPACE', 2.8, 2.6, 'Q_LowLow',   NULL, NULL, '云数据平台机会点');

-- ===== 三定 =====
INSERT INTO neinsight_control_point_type (name, cp_type_code, label) VALUES
('Type_TechnologyLead', 'TECH_LEAD', '控制点类型-技术领先'),
('Type_CostAdvantage',  'COST_ADV',  '控制点类型-成本优势');

INSERT INTO neinsight_control_point (name, cp_type_ref, opportunity_ref, label) VALUES
('CP_TechnologyLead', 'Type_TechnologyLead', 'Opp_SmartEnergyCloud', '战略控制点-技术领先'),
('CP_CostAdvantage',  'Type_CostAdvantage',  'Opp_SmartEnergyCloud', '战略控制点-成本优势');

INSERT INTO neinsight_objective
  (name, metric, target_value, timeframe, is_measurable, is_relevant, smart_compliant, label) VALUES
('Obj_1', '云业务行业份额',   NULL, '2028', 'true', 'true', NULL, '目标-云业务行业份额'),
('Obj_2', '云业务收入增长率', 25.0, '2027', 'true', 'true', NULL, '目标-云业务收入增长率');

INSERT INTO neinsight_strategy (name, cls, objective_ref, rationale, label) VALUES
('Strat_1', 'MarketStrategy',  'Obj_1', '',                 '策略-市场策略'),
('Strat_2', 'ProductStrategy', 'Obj_2', '以产品竞争力支撑增长', '策略-产品策略'),
('Strat_3', 'OrgStrategy',     'Obj_1', '组织能力保障落地',   '策略-组织策略');
