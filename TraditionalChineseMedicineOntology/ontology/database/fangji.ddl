USE tcmdb;

-- 1. 方剂类别字典表（无外键依赖，最先创建）
CREATE TABLE IF NOT EXISTS formula_category (
    uri VARCHAR(200) NOT NULL COMMENT '类别URI',
    label VARCHAR(50) NOT NULL COMMENT '类别名称',
    PRIMARY KEY (uri)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='方剂类别字典';

-- 2. 方剂主表（父表，必须在 formula_herb 之前创建）
CREATE TABLE IF NOT EXISTS formula (
    uri VARCHAR(200) NOT NULL COMMENT '本体URI，如 http://www.tcm-classics.org/fangji#GuiZhiTang',
    label VARCHAR(100) NOT NULL COMMENT 'rdfs:label 标准名',
    source_clause TEXT DEFAULT NULL COMMENT '出处条文',
    original_dosage TEXT DEFAULT NULL COMMENT '原方剂量',
    decoction_method TEXT DEFAULT NULL COMMENT '煎煮法',
    huxishu_note TEXT DEFAULT NULL COMMENT '胡老按语',
    clinical_key_points TEXT DEFAULT NULL COMMENT '临床眼目',
    category_uri VARCHAR(200) DEFAULT NULL COMMENT '归属类别URI',
    pattern_uri VARCHAR(200) DEFAULT NULL COMMENT '主治病证URI',
    PRIMARY KEY (uri),
    INDEX idx_label (label),
    INDEX idx_category (category_uri),
    INDEX idx_pattern (pattern_uri)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='经方方剂主表（113首）';

-- 3. 补全 herb_formula 表的外键约束
-- 该表此前已创建但缺少对 formula 表的外键，因当时 formula 表尚不存在
-- 若该表已有数据或确认无需级联约束，可跳过此步
ALTER TABLE herb_formula 
    ADD CONSTRAINT fk_herb_formula_formula 
    FOREIGN KEY (formula_uri) REFERENCES formula(uri) ON DELETE CASCADE;

-- 4. 方剂-药物组成及剂量表（子表，依赖 formula 和 herb）
DROP TABLE IF EXISTS formula_herb;
CREATE TABLE formula_herb (
    formula_uri VARCHAR(200) NOT NULL COMMENT '方剂URI',
    herb_uri VARCHAR(200) NOT NULL COMMENT '药物URI，关联herb表',
    dosage_text VARCHAR(100) NOT NULL COMMENT '原方剂量原文，如"三两""半升""十二枚"',
    processing_in_formula VARCHAR(100) DEFAULT NULL COMMENT '方中特殊炮制/处理，如"去皮""炙""洗""擘""碎绵裹"',
    sort_order INT NOT NULL DEFAULT 0 COMMENT '药物在原方中的排列顺序',
    PRIMARY KEY (formula_uri, herb_uri, sort_order),
    CONSTRAINT fk_fh_formula FOREIGN KEY (formula_uri) REFERENCES formula(uri) ON DELETE CASCADE,
    CONSTRAINT fk_fh_herb FOREIGN KEY (herb_uri) REFERENCES herb(uri) ON DELETE RESTRICT,
    INDEX idx_herb (herb_uri),
    INDEX idx_formula (formula_uri)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='方剂-药物组成及剂量表';