-- ============================================================================
-- data.sql：ABox 数据入库（严格镜像 ontology/neinsight-abox.owl 的个体断言）
--   * NEInsight 不内置业务实例数据（内部知识基线不通用）；仅保留洞察域个体。
--   * 只灌「断言事实」，不灌任何派生值。
--   * name/*_ref 列值 = 本体个体本地名，OBDA 映射以 IRI 模板 abox# 还原为个体 IRI。
--   * 可重复执行：先按「引用方 → 被引用方」倒序清空，再插入。
-- ============================================================================

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
