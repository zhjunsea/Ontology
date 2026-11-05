-- ============================================================================
-- DDL：创建数据库与表（幂等，可重复执行）
--   * 只负责「结构」：建库 + 删除旧表 + 建表，不含任何业务数据。
--   * 四柱命理（SiZhuBaZi）第 11 步（闸门⑩）：把「查表型历法/神煞起例/十神映射/运行态命例」
--     典型 ABox 数据入库 MySQL，经 Ontop OBDA 虚拟访问。
--   * 依据：《本体核心设计》§8「规划表」（11 张）+「数据边界」注（DB 为唯一真源）；
--     第 15 步（六典籍覆盖补全）新增 2 张：day_hour_clause（日时断 720）/ tiaohou_clause（调候 120）；
--     第 16 步（六典籍正文级复核 H1~H4 补录）新增 4 张：pattern_catalog（具名格局 95→97）/
--     lady_fortune_catalog（女命分类 17）/ technique_catalog（技法篇章 38）/ sexagenary_nature（六十甲子性质 60）；
--     第 17 步（第二轮正文级复核 I1~I3 补录）新增 3 张：auspice_clause（十干坐支/十二月支/五行时地/孕生男女 25）/
--     theory_clause（病药/动静/盖头/六亲诸说 12）/ dts_clause（滴天髓通神论/六亲论诸篇 26）；
--     第 18 步（第三轮正文级复核 R3 补录）新增 1 张：fu_clause（赋/歌/诗诀/独步/诀/论/篇/说/图 205 篇，全文照录），共 21 张；
--     第 20 步（第五轮正文级复核 P1~P8 补录）：结构不变、仍 21 张，仅既有表追加内容行——technique_catalog 38→47 /
--     pattern_catalog 97→99 / fu_clause 205→208（起胎息变通法/进交退伏/十干分配天文/十二支分配地理/月令详辨/五行总论/
--     支后一字/凤凰地/附论墓运/地支造化图/阳顺阴逆生旺死绝图）；
--     第 21 步（第五轮正文级复核 P9/P10 补录）：结构不变、仍 21 张，仅 technique_catalog 追加 19 条——
--     P9「穷通宝鉴·十干分季总论」13 条（三春/三夏/三秋/三冬甲木、三春/三夏/三秋乙木、三春/三夏丙火、十一二月戊土、
--     三春/三秋/十月十一月十二月己土）＋ P10「穷通宝鉴·五行分论」6 条（论木/论火/论土/四季月土性质之分别/论金/论水，
--     P7 声明范围补全）；technique_catalog 47→66；
--     第 22 步（第六轮正文级复核 P11 补录）：结构不变、仍 21 张，仅 pattern_catalog 追加 3 条具名格——
--     渊海子平卷二·杂气财官格/杂气印绶格/月上偏官格；pattern_catalog 99→102；
--     第 23 步（第六轮正文级复核·子平真诠正文补录）新增 1 张：ziping_clause（《子平真诠》47 篇正文，全文照录），共 22 张；
--     第 26 步（第九轮正文内容级复核·六典籍全文补齐）新增 1 张：classic_text（滴天髓/穷通宝鉴/神峰通考/三命通会/渊海子平
--     逐篇正文，全文照录，共 921 篇）；
--     第 31 步（节气精度升级，闸门）：新增 1 张：solar_term_time（二十四节气精确交节时刻，1950–2050，共 2424 行），共 24 张；
--     《本体顶层设计》§1/§ABox（查表型历法/神煞/名录数据入库，不迁入本体文件，★铁律 13/14）。
--   * name 等名称列与本体个体本地名一致（如 Stem_Jia / JiaZi / NaYin_HaiZhongJin / Term_LiChun），
--     本体实体 IRI 为短前缀形式，映射时由 <core:{name}> / <calendar:{name}> 拼回。
--   * 数据边界（A 迁移口径）：天干/地支/六十甲子/纳音/藏干/节气/月建等「查表型历法」个体
--     已自 sizhubazi-abox.owl 迁出，DB 为唯一真源；阴阳/五行/十神名录/宫位/六亲/层次/告警标志/
--     格局名录/出处/命例结构/神煞名录/长生十二宫等「静态术语」仍留 ABox。
--   * 表间逻辑外键：sexagenary_cycle.{stem,branch,nayin}_local → ganzhi_stem/ganzhi_branch/nayin.name；
--     ganzhi_*._local / nayin.five_phase → 五行留 ABox（core:{Mu..Shui}），此处仅存本地名。
--   * ten_god_map 为「内置映射/查表」（非本体个体，§5/§8 注），供程序/规则层查取，无 RDF 映射。
--   * deity_rule 自第 49 轮扩展起经 OBDA 暴露为 deity:DeityRule（本体化），运行期按本命四柱匹配起例。
--   * 灌数据请使用同目录 data.sql（createdb 会先执行本文件、再执行 data.sql）。
-- ============================================================================

CREATE DATABASE IF NOT EXISTS `sizhubazidb`
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE `sizhubazidb`;

SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS `classic_text`;
DROP TABLE IF EXISTS `ziping_clause`;
DROP TABLE IF EXISTS `fu_clause`;
DROP TABLE IF EXISTS `dts_clause`;
DROP TABLE IF EXISTS `theory_clause`;
DROP TABLE IF EXISTS `auspice_clause`;
DROP TABLE IF EXISTS `sexagenary_nature`;
DROP TABLE IF EXISTS `technique_catalog`;
DROP TABLE IF EXISTS `lady_fortune_catalog`;
DROP TABLE IF EXISTS `pattern_catalog`;
DROP TABLE IF EXISTS `tiaohou_clause`;
DROP TABLE IF EXISTS `day_hour_clause`;
DROP TABLE IF EXISTS `alert_flag`;
DROP TABLE IF EXISTS `case_record`;
DROP TABLE IF EXISTS `category_verdict`;
DROP TABLE IF EXISTS `ten_god_map`;
DROP TABLE IF EXISTS `deity_rule`;
DROP TABLE IF EXISTS `month_order`;
DROP TABLE IF EXISTS `solar_term_time`;
DROP TABLE IF EXISTS `solar_term`;
DROP TABLE IF EXISTS `hidden_stem`;
DROP TABLE IF EXISTS `nayin`;
DROP TABLE IF EXISTS `sexagenary_cycle`;
DROP TABLE IF EXISTS `ganzhi_branch`;
DROP TABLE IF EXISTS `ganzhi_stem`;
SET FOREIGN_KEY_CHECKS = 1;

-- 1) 十天干（core:HeavenlyStem）
CREATE TABLE `ganzhi_stem` (
  `name`         VARCHAR(64) NOT NULL COMMENT '个体本地名（与本体一致，如 Stem_Jia）',
  `stem_name`    VARCHAR(16) DEFAULT '' COMMENT '天干名（甲…癸）→ core:stemName',
  `stem_yinyang` VARCHAR(8)  DEFAULT '' COMMENT '天干阴阳（阳/阴）→ core:stemYinYang',
  `yinyang`      VARCHAR(32) DEFAULT '' COMMENT '阴阳个体本地名（Yang/Yin）→ core:hasYinYang',
  `five_phase`   VARCHAR(32) DEFAULT '' COMMENT '五行个体本地名（Mu/Huo/Tu/Jin/Shui）→ core:hasFivePhase',
  `stem_order`   INT         DEFAULT 0  COMMENT '天干序（1..10；展示列，暂不映射）',
  `label`        VARCHAR(32) DEFAULT '' COMMENT '标签（甲…癸）',
  PRIMARY KEY (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='十天干表';

-- 2) 十二地支（core:EarthlyBranch）
CREATE TABLE `ganzhi_branch` (
  `name`          VARCHAR(64) NOT NULL COMMENT '个体本地名（如 Branch_Zi）',
  `branch_name`   VARCHAR(16) DEFAULT '' COMMENT '地支名（子…亥）→ core:branchName',
  `branch_animal` VARCHAR(16) DEFAULT '' COMMENT '生肖 → core:branchAnimal',
  `branch_order`  INT         DEFAULT 0  COMMENT '地支序（1..12）→ core:branchOrder',
  `five_phase`    VARCHAR(32) DEFAULT '' COMMENT '五行个体本地名 → core:hasFivePhase',
  `label`         VARCHAR(32) DEFAULT '' COMMENT '标签（子…亥）',
  PRIMARY KEY (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='十二地支表';

-- 3) 六十甲子（core:SexagenaryCycle）
CREATE TABLE `sexagenary_cycle` (
  `name`         VARCHAR(64) NOT NULL COMMENT '个体本地名（如 JiaZi）',
  `ganzhi`       VARCHAR(16) DEFAULT '' COMMENT '干支（甲子…）→ rdfs:label',
  `stem_local`   VARCHAR(64) DEFAULT '' COMMENT '天干个体本地名（ganzhi_stem.name）→ core:hasStem',
  `branch_local` VARCHAR(64) DEFAULT '' COMMENT '地支个体本地名（ganzhi_branch.name）→ core:hasBranch',
  `nayin_local`  VARCHAR(64) DEFAULT '' COMMENT '纳音个体本地名（nayin.name）→ core:hasNaYin',
  `label`        VARCHAR(32) DEFAULT '' COMMENT '标签（甲子…）',
  PRIMARY KEY (`name`),
  KEY `idx_stem` (`stem_local`),
  KEY `idx_branch` (`branch_local`),
  KEY `idx_nayin` (`nayin_local`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='六十甲子表';

-- 4) 纳音（core:NaYin）
CREATE TABLE `nayin` (
  `name`         VARCHAR(64) NOT NULL COMMENT '个体本地名（如 NaYin_HaiZhongJin）',
  `nayin_name`   VARCHAR(32) DEFAULT '' COMMENT '纳音名（海中金…）→ core:naYinName',
  `nayin_nature` VARCHAR(16) DEFAULT '' COMMENT '纳音吉凶（吉/平/凶）→ core:naYinNature',
  `five_phase`   VARCHAR(32) DEFAULT '' COMMENT '纳音五行个体本地名 → core:hasFivePhase',
  `label`        VARCHAR(64) DEFAULT '' COMMENT '标签',
  PRIMARY KEY (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='纳音表';

-- 5) 地支藏干（core:HiddenStem + core:hasHiddenStem 关系）
CREATE TABLE `hidden_stem` (
  `name`         VARCHAR(64) NOT NULL COMMENT '个体本地名（如 ZiHidden / YinHiddenJia）',
  `role`         VARCHAR(64) DEFAULT '' COMMENT '藏干/人元司事说明 → core:hiddenStemRole',
  `branch_local` VARCHAR(64) DEFAULT '' COMMENT '所属地支个体本地名（ganzhi_branch.name）→ core:hasHiddenStem',
  `label`        VARCHAR(64) DEFAULT '' COMMENT '标签',
  PRIMARY KEY (`name`),
  KEY `idx_branch` (`branch_local`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='地支藏干表';

-- 6) 二十四节气（calendar:SolarTerm；代表性）
CREATE TABLE `solar_term` (
  `name`            VARCHAR(64) NOT NULL COMMENT '个体本地名（如 Term_LiChun）',
  `solar_term_name` VARCHAR(16) DEFAULT '' COMMENT '节气名（立春…大寒）→ calendar:solarTermName',
  `term_order`      INT         DEFAULT 0  COMMENT '节气序（1..24；展示列，暂不映射）',
  `label`           VARCHAR(32) DEFAULT '' COMMENT '标签',
  PRIMARY KEY (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='二十四节气表';

-- 6b) 二十四节气·精确交节时刻（calendar:SolarTermInstant；第 31 步：节气精度升级，1950–2050）
CREATE TABLE `solar_term_time` (
  `name`          VARCHAR(64) NOT NULL COMMENT '个体本地名（如 TermTime_2024_LiChun）',
  `term_year`     SMALLINT    NOT NULL COMMENT '公历年 → calendar:termYear',
  `term_idx`      INT         NOT NULL COMMENT '节气序（0..23；0=小寒，与程序 S_TERM 同序）',
  `term_local`    VARCHAR(64) NOT NULL COMMENT '→ calendar:SolarTerm 个体本地名（如 Term_LiChun）',
  `term_name`     VARCHAR(16) NOT NULL COMMENT '节气名（小寒…冬至）',
  `term_datetime` DATETIME    NOT NULL COMMENT '精确交节时刻（北京时间 UTC+8）→ calendar:termDatetime',
  PRIMARY KEY (`name`),
  UNIQUE KEY `uk_year_idx` (`term_year`, `term_idx`),
  KEY `idx_term_local` (`term_local`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='二十四节气精确交节时刻表';

-- 7) 十二月建（calendar:MonthOrder）
CREATE TABLE `month_order` (
  `name`             VARCHAR(64) NOT NULL COMMENT '个体本地名（如 MonthYin）',
  `month_order_name` VARCHAR(16) DEFAULT '' COMMENT '月建地支名（寅…丑）→ calendar:monthOrderName',
  `label`            VARCHAR(32) DEFAULT '' COMMENT '标签',
  PRIMARY KEY (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='十二月建表';

-- 8) 神煞起例（内置查表，非本体个体；供程序/规则层按柱干支查神煞）
CREATE TABLE `deity_rule` (
  `rule_id`     VARCHAR(64) NOT NULL COMMENT '起例编号（如 DR_TianYi_Year）',
  `base_pillar` VARCHAR(16) DEFAULT '' COMMENT '起例基准柱（年/月/日/时/日干）',
  `base_ganzhi` VARCHAR(64) DEFAULT '' COMMENT '基准干支或日干（如 甲/戊/庚；细煞多组并陈可较长）',
  `deity_name`  VARCHAR(32) DEFAULT '' COMMENT '神煞名（天乙贵人…）',
  `deity_alias` VARCHAR(128) DEFAULT '' COMMENT '异名（异名异法并陈，细煞可较长）',
  `branch_hit`  VARCHAR(128) DEFAULT '' COMMENT '命中地支（如 丑未；细煞多组并陈可较长）',
  `category`    VARCHAR(32) DEFAULT '' COMMENT '类别（贵人吉神/禄马/凶煞/空亡）',
  `source_note` VARCHAR(128) DEFAULT '' COMMENT '出处（三命通会卷二·诸神煞篇 等）',
  PRIMARY KEY (`rule_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='神煞起例查找表（非本体个体）';

-- 9) 十神映射（内置查表，非本体个体；日干×目标干→十神）
CREATE TABLE `ten_god_map` (
  `map_id`      VARCHAR(64) NOT NULL COMMENT '映射编号（如 TGM_Jia_Jia）',
  `day_stem`    VARCHAR(16) DEFAULT '' COMMENT '日干名（甲…癸）',
  `target_stem` VARCHAR(16) DEFAULT '' COMMENT '目标干名（甲…癸）',
  `ten_god`     VARCHAR(16) DEFAULT '' COMMENT '所主十神名（比肩…正印）',
  `source_note` VARCHAR(128) DEFAULT '' COMMENT '出处（渊海子平卷之一·论日为主 等）',
  PRIMARY KEY (`map_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='十神映射查找表（非本体个体）';

-- 9b) 分类事项白话断语模板（reading:CategoryVerdict；经 OBDA 暴露；运行期按本命条件键取文）
CREATE TABLE `category_verdict` (
  `verdict_id`   VARCHAR(64)  NOT NULL COMMENT '个体本地名（如 CV_ShiYe_HasGuan）',
  `category`     VARCHAR(32)  DEFAULT '' COMMENT '分类（事业/财运/婚姻/寿夭/飘荡）→ reading:readingCategory',
  `condition_key` VARCHAR(32) DEFAULT '' COMMENT '条件键（hasGuan_true 等）→ reading:conditionKey',
  `verdict_text` VARCHAR(512) DEFAULT '' COMMENT '白话断语文本 → reading:readingText',
  `source_note`  VARCHAR(128) DEFAULT '' COMMENT '出处 → reading:readingSource',
  PRIMARY KEY (`verdict_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='分类事项白话断语模板（经 OBDA 暴露为 reading:CategoryVerdict）';

-- 10) 运行态命例记录（reading:CaseRecord；由 persistAndReturn 经 OBDA addComponent 落库）
CREATE TABLE `case_record` (
  `case_id`     INT          NOT NULL AUTO_INCREMENT COMMENT '命例自增主键',
  `birth_data`  VARCHAR(255) DEFAULT '' COMMENT '生辰（含出生地、不脱敏，㉓）→ reading:caseBirthData',
  `birth_place` VARCHAR(128) DEFAULT '' COMMENT '出生地（展示列，暂不映射）',
  `four_pillars` VARCHAR(64) DEFAULT '' COMMENT '四柱干支串（展示列，暂不映射）',
  `reading`     TEXT COMMENT '批断结果 → reading:caseResult',
  `created_at`  DATE DEFAULT '2026-10-29' COMMENT '生成日期',
  PRIMARY KEY (`case_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='运行态命例记录表';

-- 11) 运行态告警标志（rules:AlertFlag；由流程按需落库）
CREATE TABLE `alert_flag` (
  `flag_id`         INT          NOT NULL AUTO_INCREMENT COMMENT '告警自增主键',
  `alert_type`      VARCHAR(32) DEFAULT '' COMMENT '告警类型（大凶/寿元关/重大疾病）→ rules:alertType',
  `alert_triggered` TINYINT     DEFAULT 0  COMMENT '是否触发 → rules:alertTriggered',
  `case_ref`        VARCHAR(64) DEFAULT '' COMMENT '关联命例（展示列，暂不映射）',
  `created_at`      DATE DEFAULT '2026-10-29' COMMENT '生成日期',
  PRIMARY KEY (`flag_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='运行态告警标志表';

-- 12) 日时断条文库（reading:DayHourClause；《三命通会》卷八/卷九·六X日Y时断，720 条）
--     大条文库经 MySQL + Ontop OBDA 虚拟访问（★铁律 13/14：查表/大条文外置，DB 为唯一真源）。
CREATE TABLE `day_hour_clause` (
  `clause_id`   VARCHAR(64)  NOT NULL COMMENT '条文编号（如 DHC_JiaZi_JiaZi）',
  `day_pillar`  VARCHAR(8)   DEFAULT '' COMMENT '日柱干支（六十甲子）→ reading:clauseDayPillar',
  `hour_pillar` VARCHAR(8)   DEFAULT '' COMMENT '时柱干支 → reading:clauseHourPillar',
  `day_stem`    VARCHAR(8)   DEFAULT '' COMMENT '日干（展示列，暂不映射）',
  `day_branch`  VARCHAR(8)   DEFAULT '' COMMENT '日支（展示列，暂不映射）',
  `hour_branch` VARCHAR(8)   DEFAULT '' COMMENT '时支（展示列，暂不映射）',
  `clause_text` VARCHAR(512) DEFAULT '' COMMENT '日时断条文（含所忌月分）→ reading:readingText',
  `source_note` VARCHAR(128) DEFAULT '' COMMENT '出处（三命通会卷八·六X日Y时断）→ reading:clauseSource',
  PRIMARY KEY (`clause_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='日时断条文库（非本体个体，经 OBDA 暴露）';

-- 13) 调候逐月条文库（yongshen:TiaohouClause；《穷通宝鉴》十天干生十二月，120 条）
CREATE TABLE `tiaohou_clause` (
  `clause_id`      VARCHAR(64)  NOT NULL COMMENT '条文编号（如 THC_Jia_Yin）',
  `day_stem`       VARCHAR(8)   DEFAULT '' COMMENT '日干（甲…癸；展示列，暂不映射）',
  `month_branch`   VARCHAR(8)   DEFAULT '' COMMENT '月支（寅…丑）→ yongshen:tiaohouMonthBranch',
  `month_order`    INT          DEFAULT 0  COMMENT '月序（1..12；展示列，暂不映射）',
  `primary_use`    VARCHAR(16)  DEFAULT '' COMMENT '主调候用神 → yongshen:tiaohouPrimaryUse',
  `secondary_use`  VARCHAR(32)  DEFAULT '' COMMENT '辅调候用神 → yongshen:tiaohouSecondaryUse',
  `clause_text`    VARCHAR(512) DEFAULT '' COMMENT '调候条文文本 → yongshen:tiaohouText',
  `source_note`    VARCHAR(128) DEFAULT '' COMMENT '出处（穷通宝鉴·X月Y干）→ yongshen:clauseSource',
  PRIMARY KEY (`clause_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='调候逐月条文库（非本体个体，经 OBDA 暴露）';

-- 14) 具名格局名录（pattern:Pattern；第 16 步 H2：三命通会卷五/卷六、神峰通考、渊海子平卷二 具名格，95 条）
--     原本体 pattern 域仅有 3 个正格个体，H2 补录后经 OBDA 暴露为 pattern:Pattern 个体（★铁律 13/14）。
CREATE TABLE `pattern_catalog` (
  `pattern_id`    VARCHAR(64)  NOT NULL COMMENT '个体本地名（如 PAT_TianFuGuiRen）',
  `pattern_name`  VARCHAR(32)  DEFAULT '' COMMENT '格局名（天福贵人…）→ pattern:patternName',
  `pattern_class` VARCHAR(32)  DEFAULT '' COMMENT '格局类（正官格/偏官格/外格/杂格/专旺格…）→ pattern:patternClass',
  `source_note`   VARCHAR(128) DEFAULT '' COMMENT '出处（三命通会卷五·天福贵人 等）→ pattern:patternSource',
  `meaning`       VARCHAR(255) DEFAULT '' COMMENT '格局释义 → pattern:patternMeaning',
  `label`         VARCHAR(32)  DEFAULT '' COMMENT '标签（格局名）→ rdfs:label',
  PRIMARY KEY (`pattern_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='具名格局名录（经 OBDA 暴露为 pattern:Pattern）';

-- 15) 女命细分名录（reading:LadyFortune；第 16 步 H3：三命通会卷七·论女命、渊海子平卷三 16+ 分类，17 条）
CREATE TABLE `lady_fortune_catalog` (
  `lady_id`     VARCHAR(64)  NOT NULL COMMENT '个体本地名（如 LF_Chun）',
  `lady_name`   VARCHAR(32)  DEFAULT '' COMMENT '女命分类名（纯/和/清/贵…）→ rdfs:label',
  `category`    VARCHAR(32)  DEFAULT '' COMMENT '类别（女命分类）→ reading:readingCategory',
  `source_note` VARCHAR(128) DEFAULT '' COMMENT '出处（三命通会卷七·论女命 等）→ reading:readingSource',
  `meaning`     VARCHAR(255) DEFAULT '' COMMENT '释义 → reading:readingText',
  PRIMARY KEY (`lady_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='女命细分名录（经 OBDA 暴露为 reading:LadyFortune）';

-- 16) 技法/篇章名录（reading:ReadingItem；第 16 步 H4：神趣八法/十干体象/十二支体象/神峰通考技法/滴天髓天道地道人道，38 条）
CREATE TABLE `technique_catalog` (
  `tech_id`     VARCHAR(64)  NOT NULL COMMENT '个体本地名（如 TEC_SQ_Lei）',
  `tech_name`   VARCHAR(32)  DEFAULT '' COMMENT '技法/篇章名（类/甲木体象/天道…）→ rdfs:label',
  `category`    VARCHAR(32)  DEFAULT '' COMMENT '类别（神趣八法/十干体象/十二支体象/技法/通神论）→ reading:readingCategory',
  `source_note` VARCHAR(128) DEFAULT '' COMMENT '出处（渊海子平卷三·神趣八法 等）→ reading:readingSource',
  `meaning`     VARCHAR(255) DEFAULT '' COMMENT '释义 → reading:readingText',
  PRIMARY KEY (`tech_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='技法/篇章名录（经 OBDA 暴露为 reading:ReadingItem）';

-- 17) 六十甲子性质吉凶（core:SexagenaryCycle 补充属性；第 16 步 H4：三命通会卷一·释六十甲子性质吉凶，60 条）
--     不新增个体：以 core:{name}（sexagenary_cycle 已有）为宿主，追加 core:sexagenaryNatureText。
CREATE TABLE `sexagenary_nature` (
  `name`         VARCHAR(64)  NOT NULL COMMENT '个体本地名（与 sexagenary_cycle.name 一致，如 JiaZi）',
  `ganzhi`       VARCHAR(16)  DEFAULT '' COMMENT '干支（甲子…；展示列，暂不映射）',
  `nature_wuxing` VARCHAR(8)  DEFAULT '' COMMENT '五行取象（金木水火土；展示列，暂不映射）',
  `nature_text`  VARCHAR(512) DEFAULT '' COMMENT '性质吉凶文本（为宝物，喜金木旺地。进神喜…）→ core:sexagenaryNatureText',
  PRIMARY KEY (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='六十甲子性质吉凶（经 OBDA 追加至 core:SexagenaryCycle）';

-- 18) 十干坐支/十二月支/五行时地/孕生男女 断语条文库（reading:StemBranchAuspice / MonthBranchDayStemAuspice /
--     TimePlaceFortune / ProgenyPrediction；第 17 步 I3：三命通会卷四/卷七 正文断语，25 条）
--     缘起：第 15 步为 G1/G2 仅建 OWL 类 + 占位 Demo 个体，正文断语未落库；本轮下沉到内容层（仿 G3/G8）。
CREATE TABLE `auspice_clause` (
  `clause_id`    VARCHAR(64)   NOT NULL COMMENT '条文编号（如 AC_StemSeat_JiaYi）',
  `clause_group` VARCHAR(32)   DEFAULT '' COMMENT '条文组（十干坐支/十二月支/五行时地/孕生男女）→ reading:readingCategory',
  `subject`      VARCHAR(64)   DEFAULT '' COMMENT '条目名（如 甲乙日坐支得月时吉凶）→ rdfs:label',
  `clause_text`  VARCHAR(1024) DEFAULT '' COMMENT '断语正文（照录典籍原文）→ reading:readingText',
  `source_note`  VARCHAR(128)  DEFAULT '' COMMENT '出处（三命通会卷四·论十干坐支兼得月时及行运吉凶 等）→ reading:readingSource',
  PRIMARY KEY (`clause_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='十干坐支/十二月支/五行时地/孕生男女 断语条文库（经 OBDA 暴露为 reading 诸类）';

-- 19) 病药/动静/盖头/六亲诸说 断语条文库（yongshen:BingYao / reading:DynamicStaticTheory / HeadCoveringTheory /
--     KinshipTheory；第 17 步 I3：渊海子平卷之六·神峰通考 正文断语，12 条）
CREATE TABLE `theory_clause` (
  `clause_id`    VARCHAR(64)   NOT NULL COMMENT '条文编号（如 TC_BingYao_Shuo）',
  `clause_group` VARCHAR(32)   DEFAULT '' COMMENT '条文组（病药说/动静说/盖头说/六亲说）→ 分类属性',
  `subject`      VARCHAR(64)   DEFAULT '' COMMENT '条目名（如 病药说 / 雕枯旺弱四病·雕）→ rdfs:label',
  `clause_text`  VARCHAR(1024) DEFAULT '' COMMENT '断语正文（照录典籍原文）→ reading:readingText / rdfs:comment',
  `source_note`  VARCHAR(128)  DEFAULT '' COMMENT '出处（渊海子平卷之六·病药说；神峰通考·六亲说 等）',
  PRIMARY KEY (`clause_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='病药/动静/盖头/六亲诸说 断语条文库（经 OBDA 暴露为 yongshen/reading 诸类）';

-- 20) 滴天髓通神论/六亲论诸篇 断语条文库（pattern:ImagePattern/DirectionPattern/ZhenDuiPattern/KanLiPattern/
--     AssimilationPattern/SequencePattern/SovereignPattern、yongshen:IdleGod、reading:IlluminatingChapter、
--     fortune:ZhenYuan；第 17 步 I3：滴天髓正文断语，26 条）
CREATE TABLE `dts_clause` (
  `clause_id`    VARCHAR(64)   NOT NULL COMMENT '条文编号（如 DC_ImagePattern）',
  `clause_group` VARCHAR(32)   DEFAULT '' COMMENT '条文组（形象/方局/震兑/坎离/何知章/闲神/从象化象假从假化/顺反战合局/君象臣象母象子象/贞元）',
  `subject`      VARCHAR(64)   DEFAULT '' COMMENT '条目名（如 形象 / 何知章·富）→ rdfs:label',
  `clause_text`  VARCHAR(1024) DEFAULT '' COMMENT '断语正文（照录典籍原文）→ reading:readingText / pattern:patternMeaning / rdfs:comment',
  `source_note`  VARCHAR(128)  DEFAULT '' COMMENT '出处（滴天髓·通神论·十、形象 等）',
  PRIMARY KEY (`clause_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='滴天髓通神论/六亲论诸篇 断语条文库（经 OBDA 暴露为 pattern/yongshen/reading/fortune 诸类）';

-- 21) 赋/歌/诗诀/独步/诀/论/篇/说/图 条文库（reading:CategoryFortune；第三轮 R3：
--     渊海子平卷一~卷七 + 神峰通考附录前段 成篇正文，205 条）
--     缘起：第三轮正文级复核发现成篇韵文/散论（赋/歌/诗诀/独步/诀/论/篇/说/图）内容层未落地；
--     客户裁定「全额入库·彻底无遗漏」。clause_text 用 LONGTEXT 全文照录（不截断，最长约 2.3 万字）。
CREATE TABLE `fu_clause` (
  `clause_id`    VARCHAR(64)   NOT NULL COMMENT '条文编号（如 FU_YH_094 / FU_SF_019）',
  `clause_group` VARCHAR(32)   DEFAULT '' COMMENT '条文组（赋/歌/诗诀/独步/诀/论/篇/说/图/文）→ reading:readingCategory',
  `subject`      VARCHAR(64)   DEFAULT '' COMMENT '篇名（如 珞琭子消息赋 / 玉井奥诀 / 继善篇）→ rdfs:label',
  `clause_text`  LONGTEXT      COMMENT '篇目正文（照录典籍原文，全文不截断）→ reading:readingText',
  `source_note`  VARCHAR(128)  DEFAULT '' COMMENT '出处（渊海子平卷之四·金玉赋；神峰通考·喜忌篇 等）→ reading:readingSource',
  PRIMARY KEY (`clause_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='赋/歌/诗诀/独步/诀/论/篇/说/图 条文库（经 OBDA 暴露为 reading:CategoryFortune）';

-- 22) 子平真诠 47 篇正文断语库（reading:ReadingItem；第 23 步·正文级复核补录：
--     沈孝瞻《子平真诠》原文 47 篇，47 条）
--     缘起：正文级复核发现《子平真诠》47 篇正文整体未落库（其余五书均有专表承载）；
--     客户裁定「新增子平真诠专表，逐篇录入 47 篇正文 + 出处行号」。
--     clause_text 用 LONGTEXT 全文照录（不截断，最长约 1514 字）。
CREATE TABLE `ziping_clause` (
  `clause_id`    VARCHAR(64)   NOT NULL COMMENT '条文编号（如 ZP_01 / ZP_47）',
  `clause_group` VARCHAR(32)   DEFAULT '' COMMENT '条文组（子平真诠）→ reading:readingCategory',
  `subject`      VARCHAR(64)   DEFAULT '' COMMENT '篇名（如 论十干十二支 / 论用神）→ rdfs:label',
  `clause_text`  LONGTEXT      COMMENT '篇目正文（照录典籍原文，全文不截断）→ reading:readingText',
  `source_note`  VARCHAR(128)  DEFAULT '' COMMENT '出处（子平真诠·论十干十二支（原文第 52 行））→ reading:readingSource',
  PRIMARY KEY (`clause_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='子平真诠 47 篇正文断语库（经 OBDA 暴露为 reading:ReadingItem）';

-- 23) 典籍正文全文库（reading:ReadingItem；第 26 步·第九轮正文内容级复核补齐：
--     滴天髓 / 穷通宝鉴 / 神峰通考 / 三命通会 / 渊海子平 逐篇正文，全文照录）
--     缘起：第九轮「正文内容级」复核（逐句比对全库）发现多书散文体正文大量未入本体
--     （滴天髓 6.8% / 穷通宝鉴 3.9% / 神峰通考 46.7% / 三命通会 23.5% / 渊海子平 85.9%）；客户裁定
--     「逐篇全文入库，彻底消除内容遗漏（六书不得有遗漏）」。
--     clause_text 用 LONGTEXT 全文照录（不截断）；清洗页眉/页码/目录噪声行。
CREATE TABLE `classic_text` (
  `text_id`     VARCHAR(64)   NOT NULL COMMENT '条文编号（如 CT_DTS_01 / CT_QT_142 / CT_SF_001 / CT_SMTH_298）',
  `book_name`   VARCHAR(32)   DEFAULT '' COMMENT '典籍名（滴天髓/穷通宝鉴/神峰通考/三命通会）→ reading:readingCategory',
  `chapter`     VARCHAR(128)  DEFAULT '' COMMENT '篇章名（如 通神论·一、天道 / 卷一·论五行生成）→ rdfs:label',
  `clause_text` LONGTEXT      COMMENT '篇目正文（照录典籍原文，全文不截断）→ reading:readingText',
  `source_note` VARCHAR(128)  DEFAULT '' COMMENT '出处（书·篇名·整理稿实际行号 Lxx）→ reading:readingSource',
  PRIMARY KEY (`text_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='典籍正文全文库（经 OBDA 暴露为 reading:ReadingItem）';
