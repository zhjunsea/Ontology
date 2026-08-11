-- 创建数据库
CREATE DATABASE IF NOT EXISTS tcmdb DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE tcmdb;

-- 药物主表：存储所有数据属性与注释属性
CREATE TABLE herb (
    uri VARCHAR(200) NOT NULL COMMENT '本体URI，如 http://www.tcm-classics.org/yaowu#GuiZhi',
    label VARCHAR(50) NOT NULL COMMENT 'rdfs:label 标准名',
    original_taste VARCHAR(20) DEFAULT NULL COMMENT '原味',
    original_nature VARCHAR(20) DEFAULT NULL COMMENT '原性',
    shennong_category VARCHAR(20) DEFAULT NULL COMMENT '本经品级',
    earliest_source VARCHAR(50) DEFAULT NULL COMMENT '最早出处',
    guilin_dosage_unit VARCHAR(20) DEFAULT NULL COMMENT '桂林古本剂量单位',
    processing_method VARCHAR(100) DEFAULT NULL COMMENT '炮制法',
    shennong_original_text TEXT DEFAULT NULL COMMENT '本经原文',
    bielu_original_text TEXT DEFAULT NULL COMMENT '别录原文',
    huxishu_herb_note TEXT DEFAULT NULL COMMENT '胡老药性按语',
    clinical_key_points TEXT DEFAULT NULL COMMENT '临床眼目',
    modern_application TEXT DEFAULT NULL COMMENT '现代应用',
    PRIMARY KEY (uri),
    INDEX idx_label (label),
    INDEX idx_category (shennong_category),
    INDEX idx_source (earliest_source)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='经方药物主表（87味）';

-- 药物-八纲关联表（多对多）
CREATE TABLE herb_bagang (
    herb_uri VARCHAR(200) NOT NULL,
    bagang_uri VARCHAR(200) NOT NULL COMMENT '如 http://www.tcm-classics.org/bagang#Biao',
    PRIMARY KEY (herb_uri, bagang_uri),
    FOREIGN KEY (herb_uri) REFERENCES herb(uri) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='药物-八纲药性关联';

-- 药物-主治症状关联表（多对多，预留）
CREATE TABLE herb_symptom (
    herb_uri VARCHAR(200) NOT NULL,
    symptom_uri VARCHAR(200) NOT NULL COMMENT 'zhengzhuangtizheng.owl 中的症状URI',
    PRIMARY KEY (herb_uri, symptom_uri),
    FOREIGN KEY (herb_uri) REFERENCES herb(uri) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='药物-主治症状关联';

-- 十八反关联表（多对多）
CREATE TABLE herb_antagonistic (
    herb_uri VARCHAR(200) NOT NULL COMMENT '药物URI',
    antagonist_uri VARCHAR(200) NOT NULL COMMENT '相反药物URI',
    PRIMARY KEY (herb_uri, antagonist_uri),
    FOREIGN KEY (herb_uri) REFERENCES herb(uri) ON DELETE CASCADE,
    FOREIGN KEY (antagonist_uri) REFERENCES herb(uri) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='十八反药物关联';

-- 十九畏关联表（多对多）
CREATE TABLE herb_fearing (
    herb_uri VARCHAR(200) NOT NULL COMMENT '药物URI',
    feared_uri VARCHAR(200) NOT NULL COMMENT '相畏药物URI',
    PRIMARY KEY (herb_uri, feared_uri),
    FOREIGN KEY (herb_uri) REFERENCES herb(uri) ON DELETE CASCADE,
    FOREIGN KEY (feared_uri) REFERENCES herb(uri) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='十九畏药物关联';