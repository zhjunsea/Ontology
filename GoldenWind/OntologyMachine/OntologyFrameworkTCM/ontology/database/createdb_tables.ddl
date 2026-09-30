-- =============================================
-- 经方数据库 MySQL 脚本
-- 依据：tcm-yaowu-abox.owl + tcm-fangji-abox.owl
-- IRI 与本体 ABox 完全一致
-- =============================================

CREATE DATABASE IF NOT EXISTS jingfangdb DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE jingfangdb;

-- ---------- 建表 ----------
CREATE TABLE IF NOT EXISTS yaowu (
    id INT AUTO_INCREMENT PRIMARY KEY,
    iri VARCHAR(255) NOT NULL UNIQUE,
    label VARCHAR(255) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS fangji (
    id INT AUTO_INCREMENT PRIMARY KEY,
    iri VARCHAR(255) NOT NULL UNIQUE,
    label VARCHAR(255) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS fangji_yaowu (
    fangji_id INT NOT NULL,
    yaowu_id INT NOT NULL,
    PRIMARY KEY (fangji_id, yaowu_id),
    FOREIGN KEY (fangji_id) REFERENCES fangji(id) ON DELETE CASCADE,
    FOREIGN KEY (yaowu_id) REFERENCES yaowu(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS shibafan (
    yaowu_id INT NOT NULL,
    related_yaowu_id INT NOT NULL,
    PRIMARY KEY (yaowu_id, related_yaowu_id),
    FOREIGN KEY (yaowu_id) REFERENCES yaowu(id) ON DELETE CASCADE,
    FOREIGN KEY (related_yaowu_id) REFERENCES yaowu(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS shijiuwei (
    yaowu_id INT NOT NULL,
    related_yaowu_id INT NOT NULL,
    PRIMARY KEY (yaowu_id, related_yaowu_id),
    FOREIGN KEY (yaowu_id) REFERENCES yaowu(id) ON DELETE CASCADE,
    FOREIGN KEY (related_yaowu_id) REFERENCES yaowu(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

