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
    clause_number VARCHAR(50) DEFAULT NULL COMMENT '条文编号（纯编号，如：第12条）',
    source_clause TEXT DEFAULT NULL COMMENT '出处条文摘要/原文节选',
    original_dosage TEXT DEFAULT NULL COMMENT '原方剂量原文（概览）',
    -- 以下为精细化煎服参数（对应tcm:水-煮-服三维）
    water_volume VARCHAR(50) DEFAULT NULL COMMENT '入煎水量（如：七升）',
    final_yield_volume VARCHAR(50) DEFAULT NULL COMMENT '煎出总量（如：三升）',
    single_dose_volume VARCHAR(50) DEFAULT NULL COMMENT '单次服量（如：一升）',
    decoction_heat VARCHAR(50) DEFAULT NULL COMMENT '煎煮火候（如：微火）',
    administration_method VARCHAR(100) DEFAULT NULL COMMENT '服法（如：温服、日三服）',
    dietary_contraindications VARCHAR(200) DEFAULT NULL COMMENT '饮食禁忌（如：禁生冷）',
    post_decoction_note TEXT DEFAULT NULL COMMENT '方后注（服药反应与调护）',
    -- 学术信息
    huxishu_note TEXT DEFAULT NULL COMMENT '胡老按语',
    clinical_key_points TEXT DEFAULT NULL COMMENT '临床眼目',
    -- 分类与方证映射（核心）
    category_uri VARCHAR(200) DEFAULT NULL COMMENT '归属类别URI',
    pattern_uri VARCHAR(200) DEFAULT NULL COMMENT '主治病证URI（指向bingzheng.owl）',
    PRIMARY KEY (uri),
    INDEX idx_label (label),
    INDEX idx_category (category_uri),
    INDEX idx_pattern (pattern_uri)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='经方方剂主表（118首）';

-- 3. 方剂-药物组成及剂量表（子表，依赖 formula 和 herb）
DROP TABLE IF EXISTS formula_herb;
CREATE TABLE formula_herb (
    formula_uri VARCHAR(200) NOT NULL COMMENT '方剂URI',
    herb_uri VARCHAR(200) NOT NULL COMMENT '药物URI，关联herb表',
    dosage_text VARCHAR(100) NOT NULL COMMENT '原方剂量原文，如"三两""半升""十二枚"',
    modern_dosage_gram DECIMAL(10,2) DEFAULT NULL COMMENT '现代剂量（克）',
    processing_in_formula VARCHAR(100) DEFAULT NULL COMMENT '方中特殊炮制/处理，如"去皮""炙""洗""擘"',
    special_decoction_handling VARCHAR(100) DEFAULT NULL COMMENT '特殊煎法（如：先煎、后下、烊化）',
    sort_order INT NOT NULL DEFAULT 0 COMMENT '药物在原方中的排列顺序',
    PRIMARY KEY (formula_uri, herb_uri, sort_order),
    CONSTRAINT fk_fh_formula FOREIGN KEY (formula_uri) REFERENCES formula(uri) ON DELETE CASCADE,
    CONSTRAINT fk_fh_herb FOREIGN KEY (herb_uri) REFERENCES herb(uri) ON DELETE RESTRICT,
    INDEX idx_herb (herb_uri),
    INDEX idx_formula (formula_uri)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='方剂-药物组成及剂量表';

-- r2rml_bridge.ddl
-- 用于R2RML映射的桥接表，存放本体层面的关系数据

USE tcmdb;

-- 方剂-六经关联表
CREATE TABLE IF NOT EXISTS formula_channel (
    uri VARCHAR(200) NOT NULL COMMENT '方剂URI',
    channel_uri VARCHAR(200) NOT NULL COMMENT '六经URI',
    PRIMARY KEY (uri, channel_uri),
    FOREIGN KEY (uri) REFERENCES formula(uri) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='方剂-六经关联表（R2RML桥接）';

-- 方剂-八纲关联表
CREATE TABLE IF NOT EXISTS formula_bagang (
    uri VARCHAR(200) NOT NULL COMMENT '方剂URI',
    bagang_uri VARCHAR(200) NOT NULL COMMENT '八纲URI',
    PRIMARY KEY (uri, bagang_uri),
    FOREIGN KEY (uri) REFERENCES formula(uri) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='方剂-八纲关联表（R2RML桥接）';

-- 症状-方剂佐证关联表
CREATE TABLE IF NOT EXISTS symptom_formula_evidence (
    symptom_uri VARCHAR(200) NOT NULL COMMENT '症状/舌象/脉象URI',
    formula_uri VARCHAR(200) NOT NULL COMMENT '方剂URI',
    evidence_type VARCHAR(50) DEFAULT NULL COMMENT '佐证类型：primary/secondary/direct',
    PRIMARY KEY (symptom_uri, formula_uri),
    FOREIGN KEY (formula_uri) REFERENCES formula(uri) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='症状-方剂佐证关联表（R2RML桥接）';