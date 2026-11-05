-- ============================================================================
-- DDL：创建数据库与表（幂等，可重复执行）
--   * 只负责「结构」：建库 + 删除旧表 + 建表，不含任何业务数据。
--   * 梅花易数（PlumBlossom）第 11 步：把「历法/干支」与「周易文本（卦名/卦辞/爻辞）」
--     典型 ABox 数据入库（设计《本体核心设计》§8、《本体顶层设计》§1：历法不迁入本体文件）。
--   * name / pb_name 等名称列与本体个体本地名一致（如 Seq_01 / NaYin_HaiZhongJin）；
--     本体实体 IRI 为短前缀形式，映射时由 <cal:{name}> / <yijing:{name}> 拼回。
--   * 表间逻辑外键：sexagenary_cycle.nayin_name → nayin.name。
--   * 灌数据请使用同目录 data.sql（createdb 会先执行本文件、再执行 data.sql）。
-- ============================================================================

CREATE DATABASE IF NOT EXISTS `plumblossomdb`
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE `plumblossomdb`;

SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS `yicuo_jin_yao`;
DROP TABLE IF EXISTS `divination_result`;
DROP TABLE IF EXISTS `yijing_line_dictum`;
DROP TABLE IF EXISTS `yijing_hexagram_dictum`;
DROP TABLE IF EXISTS `yijing_hexagram_sequence`;
DROP TABLE IF EXISTS `calendar_sexagenary_cycle`;
DROP TABLE IF EXISTS `calendar_nayin`;
DROP TABLE IF EXISTS `calendar_life_stage`;
DROP TABLE IF EXISTS `calendar_earthly_branch`;
DROP TABLE IF EXISTS `calendar_heavenly_stem`;
DROP TABLE IF EXISTS `appendix_text`;
DROP TABLE IF EXISTS `xiangzi_rule`;
DROP TABLE IF EXISTS `trigram`;
SET FOREIGN_KEY_CHECKS = 1;

-- 1) 十天干
CREATE TABLE `calendar_heavenly_stem` (
  `name`       VARCHAR(64) NOT NULL COMMENT '个体本地名（与本体一致，如 Stem_Jia）',
  `stem_name`  VARCHAR(16) DEFAULT '' COMMENT '天干名（甲…癸）',
  `five_phase` VARCHAR(32) DEFAULT '' COMMENT '天干五行本地名（rel: 个体，如 Mu）',
  `label`      VARCHAR(32) DEFAULT '' COMMENT '标签',
  PRIMARY KEY (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='十天干表';

-- 2) 十二地支
CREATE TABLE `calendar_earthly_branch` (
  `name`          VARCHAR(64) NOT NULL COMMENT '个体本地名（如 Branch_Zi）',
  `branch_name`   VARCHAR(16) DEFAULT '' COMMENT '地支名（子…亥）',
  `branch_animal` VARCHAR(16) DEFAULT '' COMMENT '生肖',
  `zhi_order`     INT         DEFAULT 0  COMMENT '地支序（1..12）',
  `five_phase`    VARCHAR(32) DEFAULT '' COMMENT '地支五行本地名（rel: 个体）',
  `label`         VARCHAR(32) DEFAULT '' COMMENT '标签',
  PRIMARY KEY (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='十二地支表';

-- 3) 六十甲子
CREATE TABLE `calendar_sexagenary_cycle` (
  `name`       VARCHAR(64) NOT NULL COMMENT '个体本地名（如 Cycle_JiaZi）',
  `ganzhi`     VARCHAR(16) DEFAULT '' COMMENT '干支（甲子…）',
  `nayin_name` VARCHAR(64) DEFAULT '' COMMENT '纳音本地名（calendar_nayin.name，逻辑外键）',
  `label`      VARCHAR(32) DEFAULT '' COMMENT '标签',
  PRIMARY KEY (`name`),
  KEY `idx_nayin` (`nayin_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='六十甲子表';

-- 4) 纳音
CREATE TABLE `calendar_nayin` (
  `name`       VARCHAR(64) NOT NULL COMMENT '个体本地名（如 NaYin_HaiZhongJin）',
  `nayin_name` VARCHAR(32) DEFAULT '' COMMENT '纳音名（海中金…）',
  `five_phase` VARCHAR(32) DEFAULT '' COMMENT '纳音五行本地名（rel: 个体）',
  `label`      VARCHAR(32) DEFAULT '' COMMENT '标签',
  PRIMARY KEY (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='纳音表';

-- 5) 五行长生
CREATE TABLE `calendar_life_stage` (
  `name`            VARCHAR(64) NOT NULL COMMENT '个体本地名（如 Life_Mu）',
  `life_stage_text` VARCHAR(64) DEFAULT '' COMMENT '长生文本（木生在亥…）',
  `label`           VARCHAR(64) DEFAULT '' COMMENT '标签',
  PRIMARY KEY (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='五行长生表';

-- 6) 六十四卦序/名录
CREATE TABLE `yijing_hexagram_sequence` (
  `name`          VARCHAR(64) NOT NULL COMMENT '个体本地名（如 Seq_01）',
  `sequence_no`   INT         DEFAULT 0  COMMENT '卦序（1..64）',
  `hexagram_name` VARCHAR(32) DEFAULT '' COMMENT '卦名（乾为天…）',
  `label`         VARCHAR(64) DEFAULT '' COMMENT '标签（1.乾为天）',
  PRIMARY KEY (`name`),
  KEY `idx_seq` (`sequence_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='六十四卦序名录表';

-- 7) 周易卦辞
CREATE TABLE `yijing_hexagram_dictum` (
  `name`           VARCHAR(64) NOT NULL COMMENT '个体本地名（如 Dictum_Hex_01）',
  `dictum_text`    TEXT COMMENT '卦辞文本',
  `interpretation` TEXT COMMENT '卦辞解释（《周易》彖传 + 大象传）',
  `label`          VARCHAR(64) DEFAULT '' COMMENT '标签',
  PRIMARY KEY (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='周易卦辞表';

-- 8) 周易爻辞
CREATE TABLE `yijing_line_dictum` (
  `name`           VARCHAR(64) NOT NULL COMMENT '个体本地名（如 LineDictum_01_1）',
  `position`       INT         DEFAULT 0  COMMENT '爻位（1..6）',
  `dictum_text`    TEXT COMMENT '爻辞文本',
  `interpretation` TEXT COMMENT '爻辞解释（《周易》小象传）',
  `label`          VARCHAR(64) DEFAULT '' COMMENT '标签',
  PRIMARY KEY (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='周易爻辞表';

-- 8b) 附录·邵康节易数一撮金·爻诀（64卦×3爻＝192首）
--     原著附录「乃以爻诀断之」；与周易卦辞/爻辞（第 7/8 表）来源、引用条件不同，别系不混。
CREATE TABLE `yicuo_jin_yao` (
  `name`           VARCHAR(64) NOT NULL COMMENT '个体本地名（如 YicuoJin_01_2）',
  `hexagram_name`  VARCHAR(64) DEFAULT ''  COMMENT '所属卦名（如 乾为天）',
  `hexagram_seq`   INT         DEFAULT 0   COMMENT '卦序（1..64，依卷之五·六十四卦次第歌）',
  `line_position`  TINYINT     DEFAULT 0   COMMENT '爻位（1..6）',
  `yao_label`      VARCHAR(16) DEFAULT ''  COMMENT '爻题（如 九二二爻）',
  `grade`          VARCHAR(8)  DEFAULT ''  COMMENT '一撮金六等（上上/上中/中中/中平/下中/下下）',
  `grade_iri`      VARCHAR(32) DEFAULT ''  COMMENT '等级个体本地名（如 Grade_ShangShang）',
  `dictum_text`    TEXT                    COMMENT '爻诀断辞文本',
  `label`          VARCHAR(64) DEFAULT ''  COMMENT '显示名（如 一撮金·乾为天九二二爻）',
  PRIMARY KEY (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='附录·一撮金爻诀表（64卦×3爻＝192首）';

-- 9) 运行态占断结果（典型样本；jud:PB_*）
CREATE TABLE `divination_result` (
  `pb_name`            VARCHAR(64)  NOT NULL COMMENT '占断实例名（jud:PB_<...>）',
  `divination_system`  VARCHAR(16)  DEFAULT '' COMMENT '先天/后天体系（jud:divinationSystem）',
  `method`             VARCHAR(32)  DEFAULT '' COMMENT '起卦法本地名（jud:hasMethod；div:DivinationMethod 子类，如 YearMonthDayHourMethod/YicuoJinMethod）',
  `subject_category`   VARCHAR(32)  DEFAULT '' COMMENT '事项类别本地名（jud:Cat_*）',
  `auspiciousness`     VARCHAR(32)  DEFAULT '' COMMENT '吉凶等级本地名（jud:DaJi/…）',
  `is_major_result`    TINYINT      DEFAULT 0  COMMENT '重大结果（jud:isMajorResult）',
  `uses_zhouyi_dictum` TINYINT      DEFAULT 0  COMMENT '是否引周易卦爻辞（jud:usesZhouyiDictum）',
  `response_period`    VARCHAR(16)  DEFAULT '' COMMENT '应期档位（近/中/远；展示列，暂不映射）',
  `process_flag`       VARCHAR(16)  DEFAULT '' COMMENT '过程标记（jud:processFlag）',
  `description`        VARCHAR(255) DEFAULT '' COMMENT '判词/过程描述（jud:processDescription）',
  `created_at`         DATE DEFAULT '2026-10-20' COMMENT '生成日期',
  PRIMARY KEY (`pb_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='运行态占断结果表（典型样本）';

-- 10) 相字（卷之四·字画指迷 / 卷之五·拆字杂编）—— 字画/拆字断法条目
--     依《本体顶层设计》§56（节点6 类象来源含「卷之四/卷之五」）与 §112（symbols.owl ← 万物类象…拆字）。
--     与「以数起卦」别系；仅作节点 9 取象与补断（C12：节点 16 不设独立栏）。
CREATE TABLE `xiangzi_rule` (
  `name`        VARCHAR(64) NOT NULL COMMENT '个体本地名（如 XiangZi_01_01）',
  `category`    VARCHAR(32) DEFAULT ''  COMMENT '类目（六神笔式/五行笔式/八卦断/神煞/…/赋文）',
  `rule_key`    VARCHAR(64) DEFAULT ''  COMMENT '条目名（如 青龙/甲/水/乾/贵神/…）',
  `rule_text`   TEXT                    COMMENT '法诀文本（照录原著）',
  `source_ref`  VARCHAR(64) DEFAULT ''  COMMENT '出处（卷之四/卷之五·篇名）',
  `label`       VARCHAR(96) DEFAULT ''  COMMENT '显示名（相字·类目·条目）',
  PRIMARY KEY (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='相字（字画/拆字）断法条目表';

-- 11) 附录·渔樵对问 / 宋史·邵雍传 —— 附录文献（义理与史料）；另承载原著正卷全文（批次3）
--     依《本体顶层设计》§原著范围（附录 = 邵康节易数一撮金 · 渔樵对问 · 宋史·邵雍传）；
--     一撮金已入 `yicuo_jin_yao`（第 8b 表），此表承载余二篇全文，为唯一真源（ABox 仅引用）。
--     批次3 起，原著各卷篇全文（Java 原大文本常量）亦外置入本表：name=常量名、work_name=卷别、text_body=全文；
--     Java 侧同名常量作内置兜底，装载后经反射覆盖（本体/DB 优先）。
CREATE TABLE `appendix_text` (
  `name`           VARCHAR(64) NOT NULL COMMENT '个体本地名（如 ATXT_YQ_01；批次3 为原 Java 常量名如 JUAN_SAN_GUA_YING）',
  `work_name`      VARCHAR(32) DEFAULT ''  COMMENT '篇名/卷别（渔樵对问 / 宋史·邵雍传 / 卷之一…卷之五 / 梅花易数）',
  `section_index`  INT         DEFAULT 0   COMMENT '段序（篇内 1..N）',
  `section_topic`  VARCHAR(32) DEFAULT ''  COMMENT '段主题（角色/标目；批次3 为常量名）',
  `text_body`      TEXT                    COMMENT '原文（照录）',
  `source_ref`     VARCHAR(64) DEFAULT ''  COMMENT '出处（附录·渔樵对问 / 附录·宋史·邵雍传 / 原著·卷之X）',
  `label`          VARCHAR(96) DEFAULT ''  COMMENT '显示名（附录·渔樵对问·第N则 / 原著·卷之X·常量名）',
  PRIMARY KEY (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='附录文献 + 原著正卷全文文献表（唯一真源）';

-- 12) 八卦（先天数 / 五行 / 方位）——本体八卦域知识外置唯一真源。
--     八经卦（乾兑离震巽坎艮坤）的先天数、五行、方位由本表承载，经 OBDA 映射为 core:Trigram
--     个体（rdfs:label / core:trigramNumber / rel:hasFivePhase / core:hasDirection），
--     使 Ontop 虚拟知识图谱可投影本体侧标签（OWL2QL 不投影 ABox 纯 rdfs:label 注解）。
--     数据与 plumblossom-abox.owl 中 8 个 Trigram ABox 个体逐项一致（同源）。
CREATE TABLE `trigram` (
  `local`       VARCHAR(32) NOT NULL COMMENT '个体本地名（QianTrigram…KunTrigram）',
  `label`       VARCHAR(16) DEFAULT ''  COMMENT '卦名（乾…坤）→ rdfs:label',
  `number`      INT         DEFAULT 0   COMMENT '先天数（1..8）→ core:trigramNumber',
  `five_local`  VARCHAR(16) DEFAULT ''  COMMENT '五行个体本地名（Jin/Mu/Huo/Shui/Tu）',
  `five_label`  VARCHAR(8)  DEFAULT ''  COMMENT '五行名（金/木/火/水/土）→ rdfs:label',
  `dir_local`   VARCHAR(24) DEFAULT ''  COMMENT '方位个体本地名（Dir_NorthWest…）',
  `dir_label`   VARCHAR(8)  DEFAULT ''  COMMENT '方位名（西北…）→ rdfs:label',
  `role`        VARCHAR(16) DEFAULT ''  COMMENT '长幼男女（父/少女…）→ core:trigramFamilyRole',
  `nature`      VARCHAR(16) DEFAULT ''  COMMENT '性情（健/说/丽/起/入/陷/止/顺）→ core:trigramNature',
  `main_image`  VARCHAR(16) DEFAULT ''  COMMENT '主象（天/泽/火/雷/风/水/山/地）→ core:trigramMainImage',
  `source_note` VARCHAR(128) DEFAULT '' COMMENT '出处（说卦传/先天八卦方位）',
  PRIMARY KEY (`local`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='八卦域知识表（先天数/五行/方位/长幼/性情/主象；本体八卦外置真源）';

-- 13) 五行属性字典（方位 / 五色 / 时令 / 器物）——五行域知识外置唯一真源。
--     经 OBDA 映射为五行个体（rel:Jin…Tu）的数据属性 fiveDirection/fiveColor/fiveSeason/fiveObject，
--     供节点 14（generateAdvice 十应取材）与节点 7/8 五行解读使用；本体为唯一真源、内置表兜底。
CREATE TABLE `five_phase_attr` (
  `local`       VARCHAR(16) NOT NULL COMMENT '五行个体本地名（Jin/Mu/Huo/Tu/Shui）',
  `phase_label` VARCHAR(8)  DEFAULT ''  COMMENT '五行名（金/木/火/土/水）',
  `direction`   VARCHAR(24) DEFAULT ''  COMMENT '方位 → rel:fiveDirection',
  `color`       VARCHAR(24) DEFAULT ''  COMMENT '五色 → rel:fiveColor',
  `season`      VARCHAR(32) DEFAULT ''  COMMENT '时令 → rel:fiveSeason',
  `object`      VARCHAR(48) DEFAULT ''  COMMENT '器物 → rel:fiveObject',
  `source_note` VARCHAR(128) DEFAULT '' COMMENT '出处（卷之五·五行所属 / 卷之二·十应目论）',
  PRIMARY KEY (`local`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='五行属性字典表（方位/五色/时令/器物；五行域外置真源）';

-- 14) 卦气旺衰规则（五行 × 季节 → 旺相休囚死）——旺衰域知识外置唯一真源。
--     经 OBDA 映射为 rel:SeasonVitality 个体（rel:forFivePhase / rel:inSeason / rel:vitality），
--     供节点 8（analyzeVitality）按季节取体卦旺衰；本体为唯一真源、内置算法兜底。
CREATE TABLE `season_vitality` (
  `phase_local` VARCHAR(16) NOT NULL COMMENT '五行个体本地名（Jin/Mu/Huo/Tu/Shui）',
  `season`      VARCHAR(8)  NOT NULL COMMENT '季节（春/夏/秋/冬/四季）',
  `phase_label` VARCHAR(8)  DEFAULT ''  COMMENT '五行名（金/木/火/土/水）',
  `vitality`    VARCHAR(8)  DEFAULT ''  COMMENT '旺/相/休/囚/死 → rel:vitality',
  PRIMARY KEY (`phase_local`,`season`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='卦气旺衰规则表（五行×季节→旺相休囚死；旺衰域外置真源）';

-- 15) 体用五级映射（生克类型 level → 名称 → 吉凶标签）——体用生克外置唯一真源。
--     经 OBDA 映射为 rel:BodyUseLevel 个体（rel:shengKeType / rel:shengKeName / rel:auspiciousnessLabel），
--     供节点 7（analyzeShengKe）与节点 16（judgeAuspiciousness）统一口径；本体优先、内置兜底。
CREATE TABLE `bodyuse_level` (
  `level`          INT         NOT NULL COMMENT '体用 5 级（1 用生体 / 2 比和 / 3 体克用 / 4 体生用 / 5 用克体）',
  `shengke_name`   VARCHAR(16) DEFAULT ''  COMMENT '生克类型名 → rel:shengKeName',
  `auspiciousness` VARCHAR(8)  DEFAULT ''  COMMENT '吉凶标签（大吉/吉/平/凶/大凶）→ rel:auspiciousnessLabel',
  PRIMARY KEY (`level`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='体用五级映射表（生克类型→名称→吉凶标签；体用域外置真源）';

-- 16) 吉凶等级（大凶/凶/平/吉/大吉）——judgement 域吉凶等级外置唯一真源。
--     经 OBDA 映射为 jud:Auspiciousness 个体（rdfs:label / jud:auspiciousnessLevel）。
--     修复原 ABox 纯类断言「a jud:Auspiciousness」在 Ontop 下不可查（类实例须由 DB 映射产生）之隐患。
CREATE TABLE `auspiciousness_level` (
  `local` VARCHAR(16) NOT NULL COMMENT '吉凶个体本地名（DaXiong/Xiong/Ping/Ji/DaJi）',
  `label` VARCHAR(8)  DEFAULT ''  COMMENT '吉凶名（大凶/凶/平/吉/大吉）→ rdfs:label',
  `level` INT         DEFAULT 0   COMMENT '吉凶等级（1 大凶 … 5 大吉）→ jud:auspiciousnessLevel',
  PRIMARY KEY (`local`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='吉凶等级表（大凶/凶/平/吉/大吉；judgement 域外置真源）';

-- 17) 四不占戒律（无事/不诚/不义/重卦）——divination 域戒律外置唯一真源。
--     经 OBDA 映射为 div:Precept 个体（rdfs:label / div:preceptName / div:preceptAdmonition）。
--     节点 1（preceptCheck）与节点 2'（preceptAlert）由此驱动；纯 ABox 类断言在 Ontop 下不可查，故经 DB 映射保证可查。
CREATE TABLE `precept` (
  `local`      VARCHAR(16) NOT NULL COMMENT '戒律个体本地名（WuShi/BuCheng/BuYi/ChongGua）',
  `label`      VARCHAR(8)  DEFAULT '' COMMENT '戒律名（无事不占/不诚不占/不义不占/重卦不占）→ rdfs:label / div:preceptName',
  `admonition` VARCHAR(64) DEFAULT '' COMMENT '告示语 → div:preceptAdmonition',
  PRIMARY KEY (`local`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='四不占戒律表（戒律名+告示语；本体驱动告警文案）';

-- 18) 坐端八方应兆（卷之三·占卜坐端之诀）——divination 域外置真源。
--     经 OBDA 映射为 div:ZuoDuanEntry 个体（rdfs:label / div:zuoDuanText）。
CREATE TABLE `zuo_duan` (
  `name` VARCHAR(8)  NOT NULL COMMENT '宫/方位卦名（乾/坤/震/坎/离/艮/兑）',
  `text` TEXT COMMENT '坐端应兆断语 → div:zuoDuanText',
  PRIMARY KEY (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='坐端八方应兆表（本体驱动断语）';

-- 19) 论事十大应（论日辰秘文，卷之三）——omens 域外置真源。
--     经 OBDA 映射为 ome:TenResponses 个体（rdfs:label / ome:omenText）。
CREATE TABLE `shi_da_ying` (
  `name` VARCHAR(16) NOT NULL COMMENT '条目名（裹头/跣足/担/券/卧/一行/二立/三坐/九喜/十怒）',
  `text` TEXT COMMENT '十大应断语 → ome:omenText',
  PRIMARY KEY (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='论事十大应表（本体驱动断语）';

-- 20) 卦应逐卦类象（卷之三·卦应）——symbols 域外置真源。
--     经 OBDA 映射为 sym:ImageSymbol 个体（rdfs:label / sym:symbolText）。
CREATE TABLE `gua_ying` (
  `name` VARCHAR(8)  NOT NULL COMMENT '经卦名（乾/坤/震/巽/坎/离/艮/兑）',
  `text` TEXT COMMENT '卦应逐卦类象 → sym:symbolText',
  PRIMARY KEY (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='卦应逐卦类象表（本体驱动类象）';

-- 21) 通用域知识文本载体（key→text 型域知识统一外置真源；日应/鸟兽时日兆/观物洞玄歌/万物赋等）
--     经 OBDA 映射为 div:LoreTextEntry 个体（div:loreCollection / div:loreKey / div:loreText）。
CREATE TABLE `domain_lore` (
  `collection` VARCHAR(32) NOT NULL COMMENT '知识集名（RI_YING/NIAO_SHOU_SHI_RI/DONG_XUAN_GE/WAN_WU_GE）',
  `k`          VARCHAR(64) NOT NULL COMMENT '条目键 → div:loreKey',
  `text`       TEXT COMMENT '条目文本（断语）→ div:loreText',
  PRIMARY KEY (`collection`, `k`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='通用域知识文本表（本体驱动 key→text 域知识）';

-- 22) 吉凶评分权重（judgeAuspiciousness 基准分映射 + 生克/旺衰/外应各项加减 + 上下限）——judgement 域外置真源。
--     经 OBDA 映射为 jud:JudgeWeight 个体（jud:judgeWeightItem / jud:judgeWeightValue）。
--     纯 ABox 类断言在 Ontop 下不可查，故经 DB 映射保证可查；Java 侧仅保留同源兜底常量。
CREATE TABLE `judge_weight` (
  `item`   VARCHAR(48) NOT NULL COMMENT '评分项键（base_1..base_5 / sheng_body / bihe_body / ke_body / tishi_body / vitality_wang_xiang / vitality_si / omen_good / omen_bad / clamp_min / clamp_max）',
  `weight` INT         DEFAULT 0   COMMENT '分值/权重（可为负）→ jud:judgeWeightValue',
  `label`  VARCHAR(64) DEFAULT ''  COMMENT '说明',
  PRIMARY KEY (`item`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='吉凶评分权重表（本体域知识外置真源）';

-- 23) 五卷照录正文（卷之一至卷之五，逐章照录）——divination 域外置真源（第 52 轮 C-2 下沉）。
--     经 OBDA 映射为 div:JuanTextEntry 个体（div:juanCode / div:juanVolume / div:juanTitle / div:juanText）。
--     Java 侧 58 个 JUAN_* 照录常量仅保留「同源兜底」（离线可用）；运行期由本表覆盖，_gen/verify_new_tables 逐字证 Java≡DB。
CREATE TABLE `juan_text` (
  `code`    VARCHAR(64)  NOT NULL COMMENT '章节编码（如 JUAN_SAN_GUAN_WU_ZHU_JUE）→ div:juanCode',
  `juan`    VARCHAR(8)   DEFAULT '' COMMENT '卷次（一/二/三/四/五）→ div:juanVolume',
  `title`   VARCHAR(255) DEFAULT '' COMMENT '篇目标题 → div:juanTitle',
  `content` LONGTEXT     COMMENT '照录正文 → div:juanText',
  PRIMARY KEY (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='五卷照录正文表（58 章；本体/DB 唯一真源，Java 同源兜底）';
