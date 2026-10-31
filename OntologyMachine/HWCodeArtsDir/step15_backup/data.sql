-- ============================================================================
-- 数据：全集 ABox 入库（幂等，可重复执行）
--   第 12 步（闸门⑪，数据来源裁定：AI 依四书生成 + 客户校订；落地裁定：表格族入库 DB、结构个体留 abox.owl）。
--   查表型历法（天干/地支/六十甲子/纳音/藏干/节气/月建）全集入库 sizhubazidb；
--   神煞起例 / 十神映射为「内置查表」（非本体个体），亦全集入库。
--   出处：渊海子平卷之一（论六十花甲子纳音并注解 / 又地支藏遁歌 / 论节候歌 / 论日为主 / 诸神煞篇）、
--         三命通会卷一（论纳音取象 / 论人元司事 / 论二十四节气）、卷二（论遁月时 / 论驿马 / 论天乙贵人 /
--         论羊刃 / 论咸池 / 论华盖 / 论将星 / 论禄 / 论文昌 / 论金舆 / 论天德月德 / 论劫煞 / 论亡神 / 论孤辰寡宿 / 论空亡）。
--   ★8/10 禁编造：四书无明确体系出处者一律标【待考】（如纳音吉凶三分），留待客户校订。
-- ============================================================================

-- 清空旧数据（便于本文件重复执行）
DELETE FROM `alert_flag`;
DELETE FROM `case_record`;
DELETE FROM `ten_god_map`;
DELETE FROM `deity_rule`;
DELETE FROM `month_order`;
DELETE FROM `solar_term`;
DELETE FROM `hidden_stem`;
DELETE FROM `nayin`;
DELETE FROM `sexagenary_cycle`;
DELETE FROM `ganzhi_branch`;
DELETE FROM `ganzhi_stem`;

-- ============================================================================
-- 1. 十天干（10）｜core:HeavenlyStem｜出处：渊海子平卷之一·论天干阴阳（甲乙丙丁戊己庚辛壬癸）
-- ============================================================================
INSERT INTO `ganzhi_stem` (name, stem_name, stem_yinyang, yinyang, five_phase, stem_order, label) VALUES
('Stem_Jia', '甲', '阳', 'Yang', 'Mu', 1, '甲'),
('Stem_Yi', '乙', '阴', 'Yin', 'Mu', 2, '乙'),
('Stem_Bing', '丙', '阳', 'Yang', 'Huo', 3, '丙'),
('Stem_Ding', '丁', '阴', 'Yin', 'Huo', 4, '丁'),
('Stem_Wu', '戊', '阳', 'Yang', 'Tu', 5, '戊'),
('Stem_Ji', '己', '阴', 'Yin', 'Tu', 6, '己'),
('Stem_Geng', '庚', '阳', 'Yang', 'Jin', 7, '庚'),
('Stem_Xin', '辛', '阴', 'Yin', 'Jin', 8, '辛'),
('Stem_Ren', '壬', '阳', 'Yang', 'Shui', 9, '壬'),
('Stem_Gui', '癸', '阴', 'Yin', 'Shui', 10, '癸');

-- ============================================================================
-- 2. 十二地支（12）｜core:EarthlyBranch｜出处：渊海子平卷之一；三命通会卷二·论地支生肖
-- ============================================================================
INSERT INTO `ganzhi_branch` (name, branch_name, branch_animal, branch_order, five_phase, label) VALUES
('Branch_Zi', '子', '鼠', 1, 'Shui', '子'),
('Branch_Chou', '丑', '牛', 2, 'Tu', '丑'),
('Branch_Yin', '寅', '虎', 3, 'Mu', '寅'),
('Branch_Mao', '卯', '兔', 4, 'Mu', '卯'),
('Branch_Chen', '辰', '龙', 5, 'Tu', '辰'),
('Branch_Si', '巳', '蛇', 6, 'Huo', '巳'),
('Branch_Wu', '午', '马', 7, 'Huo', '午'),
('Branch_Wei', '未', '羊', 8, 'Tu', '未'),
('Branch_Shen', '申', '猴', 9, 'Jin', '申'),
('Branch_You', '酉', '鸡', 10, 'Jin', '酉'),
('Branch_Xu', '戌', '狗', 11, 'Tu', '戌'),
('Branch_Hai', '亥', '猪', 12, 'Shui', '亥');

-- ============================================================================
-- 3. 六十甲子（60）｜core:SexagenaryCycle｜出处：渊海子平卷之一·论六十花甲子纳音并注解
-- ============================================================================
INSERT INTO `sexagenary_cycle` (name, ganzhi, stem_local, branch_local, nayin_local, label) VALUES
('JiaZi', '甲子', 'Stem_Jia', 'Branch_Zi', 'NaYin_HaiZhongJin', '甲子'),
('YiChou', '乙丑', 'Stem_Yi', 'Branch_Chou', 'NaYin_HaiZhongJin', '乙丑'),
('BingYin', '丙寅', 'Stem_Bing', 'Branch_Yin', 'NaYin_LuZhongHuo', '丙寅'),
('DingMao', '丁卯', 'Stem_Ding', 'Branch_Mao', 'NaYin_LuZhongHuo', '丁卯'),
('WuChen', '戊辰', 'Stem_Wu', 'Branch_Chen', 'NaYin_DaLinMu', '戊辰'),
('JiSi', '己巳', 'Stem_Ji', 'Branch_Si', 'NaYin_DaLinMu', '己巳'),
('GengWu', '庚午', 'Stem_Geng', 'Branch_Wu', 'NaYin_LuPangTu', '庚午'),
('XinWei', '辛未', 'Stem_Xin', 'Branch_Wei', 'NaYin_LuPangTu', '辛未'),
('RenShen', '壬申', 'Stem_Ren', 'Branch_Shen', 'NaYin_JianFengJin', '壬申'),
('GuiYou', '癸酉', 'Stem_Gui', 'Branch_You', 'NaYin_JianFengJin', '癸酉'),
('JiaXu', '甲戌', 'Stem_Jia', 'Branch_Xu', 'NaYin_ShanTouHuo', '甲戌'),
('YiHai', '乙亥', 'Stem_Yi', 'Branch_Hai', 'NaYin_ShanTouHuo', '乙亥'),
('BingZi', '丙子', 'Stem_Bing', 'Branch_Zi', 'NaYin_JianXiaShui', '丙子'),
('DingChou', '丁丑', 'Stem_Ding', 'Branch_Chou', 'NaYin_JianXiaShui', '丁丑'),
('WuYin', '戊寅', 'Stem_Wu', 'Branch_Yin', 'NaYin_ChengTouTu', '戊寅'),
('JiMao', '己卯', 'Stem_Ji', 'Branch_Mao', 'NaYin_ChengTouTu', '己卯'),
('GengChen', '庚辰', 'Stem_Geng', 'Branch_Chen', 'NaYin_BaiLaJin', '庚辰'),
('XinSi', '辛巳', 'Stem_Xin', 'Branch_Si', 'NaYin_BaiLaJin', '辛巳'),
('RenWu', '壬午', 'Stem_Ren', 'Branch_Wu', 'NaYin_YangLiuMu', '壬午'),
('GuiWei', '癸未', 'Stem_Gui', 'Branch_Wei', 'NaYin_YangLiuMu', '癸未'),
('JiaShen', '甲申', 'Stem_Jia', 'Branch_Shen', 'NaYin_QuanZhongShui', '甲申'),
('YiYou', '乙酉', 'Stem_Yi', 'Branch_You', 'NaYin_QuanZhongShui', '乙酉'),
('BingXu', '丙戌', 'Stem_Bing', 'Branch_Xu', 'NaYin_WuShangTu', '丙戌'),
('DingHai', '丁亥', 'Stem_Ding', 'Branch_Hai', 'NaYin_WuShangTu', '丁亥'),
('WuZi', '戊子', 'Stem_Wu', 'Branch_Zi', 'NaYin_PiLiHuo', '戊子'),
('JiChou', '己丑', 'Stem_Ji', 'Branch_Chou', 'NaYin_PiLiHuo', '己丑'),
('GengYin', '庚寅', 'Stem_Geng', 'Branch_Yin', 'NaYin_SongBaiMu', '庚寅'),
('XinMao', '辛卯', 'Stem_Xin', 'Branch_Mao', 'NaYin_SongBaiMu', '辛卯'),
('RenChen', '壬辰', 'Stem_Ren', 'Branch_Chen', 'NaYin_ChangLiuShui', '壬辰'),
('GuiSi', '癸巳', 'Stem_Gui', 'Branch_Si', 'NaYin_ChangLiuShui', '癸巳'),
('JiaWu', '甲午', 'Stem_Jia', 'Branch_Wu', 'NaYin_ShaZhongJin', '甲午'),
('YiWei', '乙未', 'Stem_Yi', 'Branch_Wei', 'NaYin_ShaZhongJin', '乙未'),
('BingShen', '丙申', 'Stem_Bing', 'Branch_Shen', 'NaYin_ShanXiaHuo', '丙申'),
('DingYou', '丁酉', 'Stem_Ding', 'Branch_You', 'NaYin_ShanXiaHuo', '丁酉'),
('WuXu', '戊戌', 'Stem_Wu', 'Branch_Xu', 'NaYin_PingDiMu', '戊戌'),
('JiHai', '己亥', 'Stem_Ji', 'Branch_Hai', 'NaYin_PingDiMu', '己亥'),
('GengZi', '庚子', 'Stem_Geng', 'Branch_Zi', 'NaYin_BiShangTu', '庚子'),
('XinChou', '辛丑', 'Stem_Xin', 'Branch_Chou', 'NaYin_BiShangTu', '辛丑'),
('RenYin', '壬寅', 'Stem_Ren', 'Branch_Yin', 'NaYin_JinBoJin', '壬寅'),
('GuiMao', '癸卯', 'Stem_Gui', 'Branch_Mao', 'NaYin_JinBoJin', '癸卯'),
('JiaChen', '甲辰', 'Stem_Jia', 'Branch_Chen', 'NaYin_FuDengHuo', '甲辰'),
('YiSi', '乙巳', 'Stem_Yi', 'Branch_Si', 'NaYin_FuDengHuo', '乙巳'),
('BingWu', '丙午', 'Stem_Bing', 'Branch_Wu', 'NaYin_TianHeShui', '丙午'),
('DingWei', '丁未', 'Stem_Ding', 'Branch_Wei', 'NaYin_TianHeShui', '丁未'),
('WuShen', '戊申', 'Stem_Wu', 'Branch_Shen', 'NaYin_DaYiTu', '戊申'),
('JiYou', '己酉', 'Stem_Ji', 'Branch_You', 'NaYin_DaYiTu', '己酉'),
('GengXu', '庚戌', 'Stem_Geng', 'Branch_Xu', 'NaYin_ChaiChuanJin', '庚戌'),
('XinHai', '辛亥', 'Stem_Xin', 'Branch_Hai', 'NaYin_ChaiChuanJin', '辛亥'),
('RenZi', '壬子', 'Stem_Ren', 'Branch_Zi', 'NaYin_SangZheMu', '壬子'),
('GuiChou', '癸丑', 'Stem_Gui', 'Branch_Chou', 'NaYin_SangZheMu', '癸丑'),
('JiaYin', '甲寅', 'Stem_Jia', 'Branch_Yin', 'NaYin_DaXiShui', '甲寅'),
('YiMao', '乙卯', 'Stem_Yi', 'Branch_Mao', 'NaYin_DaXiShui', '乙卯'),
('BingChen', '丙辰', 'Stem_Bing', 'Branch_Chen', 'NaYin_ShaZhongTu', '丙辰'),
('DingSi', '丁巳', 'Stem_Ding', 'Branch_Si', 'NaYin_ShaZhongTu', '丁巳'),
('WuWu', '戊午', 'Stem_Wu', 'Branch_Wu', 'NaYin_TianShangHuo', '戊午'),
('JiWei', '己未', 'Stem_Ji', 'Branch_Wei', 'NaYin_TianShangHuo', '己未'),
('GengShen', '庚申', 'Stem_Geng', 'Branch_Shen', 'NaYin_ShiLiuMu', '庚申'),
('XinYou', '辛酉', 'Stem_Xin', 'Branch_You', 'NaYin_ShiLiuMu', '辛酉'),
('RenXu', '壬戌', 'Stem_Ren', 'Branch_Xu', 'NaYin_DaHaiShui', '壬戌'),
('GuiHai', '癸亥', 'Stem_Gui', 'Branch_Hai', 'NaYin_DaHaiShui', '癸亥');

-- ============================================================================
-- 4. 纳音（30）｜core:NaYin｜出处：渊海子平卷之一·论六十花甲子纳音并注解；三命通会卷一·论纳音取象
--   note：nayin_nature（吉/平/凶）四书无明确体系出处，统一标【待考】待客户校订（★8/10）。
-- ============================================================================
INSERT INTO `nayin` (name, nayin_name, nayin_nature, five_phase, label) VALUES
('NaYin_HaiZhongJin', '海中金', '待考', 'Jin', '海中金'),
('NaYin_LuZhongHuo', '炉中火', '待考', 'Huo', '炉中火'),
('NaYin_DaLinMu', '大林木', '待考', 'Mu', '大林木'),
('NaYin_LuPangTu', '路旁土', '待考', 'Tu', '路旁土'),
('NaYin_JianFengJin', '剑锋金', '待考', 'Jin', '剑锋金'),
('NaYin_ShanTouHuo', '山头火', '待考', 'Huo', '山头火'),
('NaYin_JianXiaShui', '涧下水', '待考', 'Shui', '涧下水'),
('NaYin_ChengTouTu', '城头土', '待考', 'Tu', '城头土'),
('NaYin_BaiLaJin', '白蜡金', '待考', 'Jin', '白蜡金'),
('NaYin_YangLiuMu', '杨柳木', '待考', 'Mu', '杨柳木'),
('NaYin_QuanZhongShui', '泉中水', '待考', 'Shui', '泉中水'),
('NaYin_WuShangTu', '屋上土', '待考', 'Tu', '屋上土'),
('NaYin_PiLiHuo', '霹雳火', '待考', 'Huo', '霹雳火'),
('NaYin_SongBaiMu', '松柏木', '待考', 'Mu', '松柏木'),
('NaYin_ChangLiuShui', '长流水', '待考', 'Shui', '长流水'),
('NaYin_ShaZhongJin', '砂中金', '待考', 'Jin', '砂中金'),
('NaYin_ShanXiaHuo', '山下火', '待考', 'Huo', '山下火'),
('NaYin_PingDiMu', '平地木', '待考', 'Mu', '平地木'),
('NaYin_BiShangTu', '壁上土', '待考', 'Tu', '壁上土'),
('NaYin_JinBoJin', '金箔金', '待考', 'Jin', '金箔金'),
('NaYin_FuDengHuo', '覆灯火', '待考', 'Huo', '覆灯火'),
('NaYin_TianHeShui', '天河水', '待考', 'Shui', '天河水'),
('NaYin_DaYiTu', '大驿土', '待考', 'Tu', '大驿土'),
('NaYin_ChaiChuanJin', '钗钏金', '待考', 'Jin', '钗钏金'),
('NaYin_SangZheMu', '桑柘木', '待考', 'Mu', '桑柘木'),
('NaYin_DaXiShui', '大溪水', '待考', 'Shui', '大溪水'),
('NaYin_ShaZhongTu', '沙中土', '待考', 'Tu', '沙中土'),
('NaYin_TianShangHuo', '天上火', '待考', 'Huo', '天上火'),
('NaYin_ShiLiuMu', '石榴木', '待考', 'Mu', '石榴木'),
('NaYin_DaHaiShui', '大海水', '待考', 'Shui', '大海水');

-- ============================================================================
-- 5. 地支藏干（28）｜core:HiddenStem｜出处：渊海子平卷之一·又地支藏遁歌；三命通会卷一·论人元司事
--   命名：单藏干支 <Zhi>Hidden；多藏干支 <Zhi>Hidden<Stem>（与第 11 步典型数据一致）
-- ============================================================================
INSERT INTO `hidden_stem` (name, role, branch_local, label) VALUES
('ZiHidden', '子藏癸（人元·本气）', 'Branch_Zi', '子藏癸'),
('ChouHiddenJi', '丑藏己（人元·本气）', 'Branch_Chou', '丑藏己'),
('ChouHiddenGui', '丑藏癸（人元·中气）', 'Branch_Chou', '丑藏癸'),
('ChouHiddenXin', '丑藏辛（人元·余气）', 'Branch_Chou', '丑藏辛'),
('YinHiddenJia', '寅藏甲（人元·本气）', 'Branch_Yin', '寅藏甲'),
('YinHiddenBing', '寅藏丙（人元·中气）', 'Branch_Yin', '寅藏丙'),
('YinHiddenWu', '寅藏戊（人元·余气）', 'Branch_Yin', '寅藏戊'),
('MaoHidden', '卯藏乙（人元·本气）', 'Branch_Mao', '卯藏乙'),
('ChenHiddenWu', '辰藏戊（人元·本气）', 'Branch_Chen', '辰藏戊'),
('ChenHiddenYi', '辰藏乙（人元·中气）', 'Branch_Chen', '辰藏乙'),
('ChenHiddenGui', '辰藏癸（人元·余气）', 'Branch_Chen', '辰藏癸'),
('SiHiddenBing', '巳藏丙（人元·本气）', 'Branch_Si', '巳藏丙'),
('SiHiddenWu', '巳藏戊（人元·中气）', 'Branch_Si', '巳藏戊'),
('SiHiddenGeng', '巳藏庚（人元·余气）', 'Branch_Si', '巳藏庚'),
('WuHiddenDing', '午藏丁（人元·本气）', 'Branch_Wu', '午藏丁'),
('WuHiddenJi', '午藏己（人元·中气）', 'Branch_Wu', '午藏己'),
('WeiHiddenJi', '未藏己（人元·本气）', 'Branch_Wei', '未藏己'),
('WeiHiddenDing', '未藏丁（人元·中气）', 'Branch_Wei', '未藏丁'),
('WeiHiddenYi', '未藏乙（人元·余气）', 'Branch_Wei', '未藏乙'),
('ShenHiddenGeng', '申藏庚（人元·本气）', 'Branch_Shen', '申藏庚'),
('ShenHiddenRen', '申藏壬（人元·中气）', 'Branch_Shen', '申藏壬'),
('ShenHiddenWu', '申藏戊（人元·余气）', 'Branch_Shen', '申藏戊'),
('YouHidden', '酉藏辛（人元·本气）', 'Branch_You', '酉藏辛'),
('XuHiddenWu', '戌藏戊（人元·本气）', 'Branch_Xu', '戌藏戊'),
('XuHiddenXin', '戌藏辛（人元·中气）', 'Branch_Xu', '戌藏辛'),
('XuHiddenDing', '戌藏丁（人元·余气）', 'Branch_Xu', '戌藏丁'),
('HaiHiddenRen', '亥藏壬（人元·本气）', 'Branch_Hai', '亥藏壬'),
('HaiHiddenJia', '亥藏甲（人元·中气）', 'Branch_Hai', '亥藏甲');

-- ============================================================================
-- 6. 二十四节气（24）｜calendar:SolarTerm｜出处：渊海子平卷之一·论节候歌 / 又论节气歌
-- ============================================================================
INSERT INTO `solar_term` (name, solar_term_name, term_order, label) VALUES
('Term_LiChun', '立春', 1, '立春'),
('Term_YuShui', '雨水', 2, '雨水'),
('Term_JingZhe', '惊蛰', 3, '惊蛰'),
('Term_ChunFen', '春分', 4, '春分'),
('Term_QingMing', '清明', 5, '清明'),
('Term_GuYu', '谷雨', 6, '谷雨'),
('Term_LiXia', '立夏', 7, '立夏'),
('Term_XiaoMan', '小满', 8, '小满'),
('Term_MangZhong', '芒种', 9, '芒种'),
('Term_XiaZhi', '夏至', 10, '夏至'),
('Term_XiaoShu', '小暑', 11, '小暑'),
('Term_DaShu', '大暑', 12, '大暑'),
('Term_LiQiu', '立秋', 13, '立秋'),
('Term_ChuShu', '处暑', 14, '处暑'),
('Term_BaiLu', '白露', 15, '白露'),
('Term_QiuFen', '秋分', 16, '秋分'),
('Term_HanLu', '寒露', 17, '寒露'),
('Term_ShuangJiang', '霜降', 18, '霜降'),
('Term_LiDong', '立冬', 19, '立冬'),
('Term_XiaoXue', '小雪', 20, '小雪'),
('Term_DaXue', '大雪', 21, '大雪'),
('Term_DongZhi', '冬至', 22, '冬至'),
('Term_XiaoHan', '小寒', 23, '小寒'),
('Term_DaHan', '大寒', 24, '大寒');

-- ============================================================================
-- 7. 十二月建（12）｜calendar:MonthOrder｜出处：三命通会卷二·论遁月时（五虎遁，正月建寅）
-- ============================================================================
INSERT INTO `month_order` (name, month_order_name, label) VALUES
('MonthYin', '寅', '寅'),
('MonthMao', '卯', '卯'),
('MonthChen', '辰', '辰'),
('MonthSi', '巳', '巳'),
('MonthWu', '午', '午'),
('MonthWei', '未', '未'),
('MonthShen', '申', '申'),
('MonthYou', '酉', '酉'),
('MonthXu', '戌', '戌'),
('MonthHai', '亥', '亥'),
('MonthZi', '子', '子'),
('MonthChou', '丑', '丑');

-- ============================================================================
-- 8. 神煞起例（95；内置查表，非本体个体）｜三命通会卷二·诸神煞篇；渊海子平卷之一·诸神煞篇
--   异名异法并陈（阴干羊刃/天乙贵人庚辛等）；四书无明确出处者已并入异名或用【待考】标注。
-- ============================================================================
INSERT INTO `deity_rule` (rule_id, base_pillar, base_ganzhi, deity_name, deity_alias, branch_hit, category, source_note) VALUES
('DR_TianYi_JiaWuGeng', '日干', '甲戊庚', '天乙贵人', '', '丑未', '贵人吉神', '三命通会卷二·论天乙贵人'),
('DR_TianYi_YiJi', '日干', '乙己', '天乙贵人', '', '子申', '贵人吉神', '三命通会卷二·论天乙贵人'),
('DR_TianYi_BingDing', '日干', '丙丁', '天乙贵人', '', '亥酉', '贵人吉神', '三命通会卷二·论天乙贵人'),
('DR_TianYi_RenGui', '日干', '壬癸', '天乙贵人', '', '卯巳', '贵人吉神', '三命通会卷二·论天乙贵人'),
('DR_TianYi_GengXin', '日干', '庚辛', '天乙贵人', '', '午寅', '贵人吉神', '三命通会卷二·论天乙贵人（庚辛逢虎马）'),
('DR_YangRen_Jia', '日干', '甲', '羊刃', '阳刃', '卯', '凶煞', '三命通会卷二·论羊刃'),
('DR_YangRen_BingWu', '日干', '丙戊', '羊刃', '阳刃', '午', '凶煞', '三命通会卷二·论羊刃'),
('DR_YangRen_Geng', '日干', '庚', '羊刃', '阳刃', '酉', '凶煞', '三命通会卷二·论羊刃'),
('DR_YangRen_Ren', '日干', '壬', '羊刃', '阳刃', '子', '凶煞', '三命通会卷二·论羊刃'),
('DR_YangRen_Yi', '日干', '乙', '羊刃', '阴刃（异法）', '寅', '凶煞', '渊海子平卷之一·诸神煞篇（阴干刃异法并陈）'),
('DR_YangRen_DingJi', '日干', '丁己', '羊刃', '阴刃（异法）', '巳', '凶煞', '渊海子平卷之一·诸神煞篇'),
('DR_YangRen_Xin', '日干', '辛', '羊刃', '阴刃（异法）', '申', '凶煞', '渊海子平卷之一·诸神煞篇'),
('DR_YangRen_Gui', '日干', '癸', '羊刃', '阴刃（异法）', '亥', '凶煞', '渊海子平卷之一·诸神煞篇'),
('DR_YiMa_ShenZiChen', '年支/日支', '申子辰', '驿马', '', '寅', '禄马', '三命通会卷二·论驿马'),
('DR_YiMa_YinWuXu', '年支/日支', '寅午戌', '驿马', '', '申', '禄马', '三命通会卷二·论驿马'),
('DR_YiMa_SiYouChou', '年支/日支', '巳酉丑', '驿马', '', '亥', '禄马', '三命通会卷二·论驿马'),
('DR_YiMa_HaiMaoWei', '年支/日支', '亥卯未', '驿马', '', '巳', '禄马', '三命通会卷二·论驿马'),
('DR_Void_JiaZi', '日柱', '甲子旬', '旬空', '空亡', '戌亥', '空亡', '三命通会卷二·论空亡'),
('DR_Void_JiaXu', '日柱', '甲戌旬', '旬空', '空亡', '申酉', '空亡', '三命通会卷二·论空亡'),
('DR_Void_JiaShen', '日柱', '甲申旬', '旬空', '空亡', '午未', '空亡', '三命通会卷二·论空亡'),
('DR_Void_JiaWu', '日柱', '甲午旬', '旬空', '空亡', '辰巳', '空亡', '三命通会卷二·论空亡'),
('DR_Void_JiaChen', '日柱', '甲辰旬', '旬空', '空亡', '寅卯', '空亡', '三命通会卷二·论空亡'),
('DR_Void_JiaYin', '日柱', '甲寅旬', '旬空', '空亡', '子丑', '空亡', '三命通会卷二·论空亡'),
('DR_TaoHua_ShenZiChen', '年支/日支', '申子辰', '桃花', '咸池', '酉', '凶煞', '三命通会卷二·论咸池'),
('DR_TaoHua_YinWuXu', '年支/日支', '寅午戌', '桃花', '咸池', '卯', '凶煞', '三命通会卷二·论咸池'),
('DR_TaoHua_SiYouChou', '年支/日支', '巳酉丑', '桃花', '咸池', '午', '凶煞', '三命通会卷二·论咸池'),
('DR_TaoHua_HaiMaoWei', '年支/日支', '亥卯未', '桃花', '咸池', '子', '凶煞', '三命通会卷二·论咸池'),
('DR_HuaGai_ShenZiChen', '年支/日支', '申子辰', '华盖', '', '辰', '贵人吉神', '三命通会卷二·论华盖'),
('DR_HuaGai_YinWuXu', '年支/日支', '寅午戌', '华盖', '', '戌', '贵人吉神', '三命通会卷二·论华盖'),
('DR_HuaGai_SiYouChou', '年支/日支', '巳酉丑', '华盖', '', '丑', '贵人吉神', '三命通会卷二·论华盖'),
('DR_HuaGai_HaiMaoWei', '年支/日支', '亥卯未', '华盖', '', '未', '贵人吉神', '三命通会卷二·论华盖'),
('DR_JiangXing_ShenZiChen', '年支/日支', '申子辰', '将星', '', '子', '禄马', '三命通会卷二·论将星'),
('DR_JiangXing_YinWuXu', '年支/日支', '寅午戌', '将星', '', '午', '禄马', '三命通会卷二·论将星'),
('DR_JiangXing_SiYouChou', '年支/日支', '巳酉丑', '将星', '', '酉', '禄马', '三命通会卷二·论将星'),
('DR_JiangXing_HaiMaoWei', '年支/日支', '亥卯未', '将星', '', '卯', '禄马', '三命通会卷二·论将星'),
('DR_Lu_Jia', '日干', '甲', '禄', '临官', '寅', '禄马', '三命通会卷二·论禄'),
('DR_Lu_Yi', '日干', '乙', '禄', '临官', '卯', '禄马', '三命通会卷二·论禄'),
('DR_Lu_BingWu', '日干', '丙戊', '禄', '临官', '巳', '禄马', '三命通会卷二·论禄'),
('DR_Lu_DingJi', '日干', '丁己', '禄', '临官', '午', '禄马', '三命通会卷二·论禄'),
('DR_Lu_Geng', '日干', '庚', '禄', '临官', '申', '禄马', '三命通会卷二·论禄'),
('DR_Lu_Xin', '日干', '辛', '禄', '临官', '酉', '禄马', '三命通会卷二·论禄'),
('DR_Lu_Ren', '日干', '壬', '禄', '临官', '亥', '禄马', '三命通会卷二·论禄'),
('DR_Lu_Gui', '日干', '癸', '禄', '临官', '子', '禄马', '三命通会卷二·论禄'),
('DR_WenChang_Jia', '日干', '甲', '文昌贵人', '', '巳', '贵人吉神', '三命通会卷二·论文昌'),
('DR_WenChang_Yi', '日干', '乙', '文昌贵人', '', '午', '贵人吉神', '三命通会卷二·论文昌'),
('DR_WenChang_Bing', '日干', '丙', '文昌贵人', '', '申', '贵人吉神', '三命通会卷二·论文昌'),
('DR_WenChang_Ding', '日干', '丁', '文昌贵人', '', '酉', '贵人吉神', '三命通会卷二·论文昌'),
('DR_WenChang_Wu', '日干', '戊', '文昌贵人', '', '申', '贵人吉神', '三命通会卷二·论文昌'),
('DR_WenChang_Ji', '日干', '己', '文昌贵人', '', '酉', '贵人吉神', '三命通会卷二·论文昌'),
('DR_WenChang_Geng', '日干', '庚', '文昌贵人', '', '亥', '贵人吉神', '三命通会卷二·论文昌'),
('DR_WenChang_Xin', '日干', '辛', '文昌贵人', '', '子', '贵人吉神', '三命通会卷二·论文昌'),
('DR_WenChang_Ren', '日干', '壬', '文昌贵人', '', '寅', '贵人吉神', '三命通会卷二·论文昌'),
('DR_WenChang_Gui', '日干', '癸', '文昌贵人', '', '卯', '贵人吉神', '三命通会卷二·论文昌'),
('DR_JinYu_Jia', '日干', '甲', '金舆', '', '辰', '贵人吉神', '三命通会卷二·论金舆'),
('DR_JinYu_Yi', '日干', '乙', '金舆', '', '巳', '贵人吉神', '三命通会卷二·论金舆'),
('DR_JinYu_Bing', '日干', '丙', '金舆', '', '未', '贵人吉神', '三命通会卷二·论金舆'),
('DR_JinYu_Ding', '日干', '丁', '金舆', '', '申', '贵人吉神', '三命通会卷二·论金舆'),
('DR_JinYu_Wu', '日干', '戊', '金舆', '', '未', '贵人吉神', '三命通会卷二·论金舆'),
('DR_JinYu_Ji', '日干', '己', '金舆', '', '申', '贵人吉神', '三命通会卷二·论金舆'),
('DR_JinYu_Geng', '日干', '庚', '金舆', '', '戌', '贵人吉神', '三命通会卷二·论金舆'),
('DR_JinYu_Xin', '日干', '辛', '金舆', '', '亥', '贵人吉神', '三命通会卷二·论金舆'),
('DR_JinYu_Ren', '日干', '壬', '金舆', '', '丑', '贵人吉神', '三命通会卷二·论金舆'),
('DR_JinYu_Gui', '日干', '癸', '金舆', '', '寅', '贵人吉神', '三命通会卷二·论金舆'),
('DR_TianDe_Yin', '月支', '寅月', '天德', '', '丁', '贵人吉神', '三命通会卷二·论天德（命中或为干或为支）'),
('DR_TianDe_Mao', '月支', '卯月', '天德', '', '申', '贵人吉神', '三命通会卷二·论天德（命中或为干或为支）'),
('DR_TianDe_Chen', '月支', '辰月', '天德', '', '壬', '贵人吉神', '三命通会卷二·论天德（命中或为干或为支）'),
('DR_TianDe_Si', '月支', '巳月', '天德', '', '辛', '贵人吉神', '三命通会卷二·论天德（命中或为干或为支）'),
('DR_TianDe_Wu', '月支', '午月', '天德', '', '亥', '贵人吉神', '三命通会卷二·论天德（命中或为干或为支）'),
('DR_TianDe_Wei', '月支', '未月', '天德', '', '甲', '贵人吉神', '三命通会卷二·论天德（命中或为干或为支）'),
('DR_TianDe_Shen', '月支', '申月', '天德', '', '癸', '贵人吉神', '三命通会卷二·论天德（命中或为干或为支）'),
('DR_TianDe_You', '月支', '酉月', '天德', '', '寅', '贵人吉神', '三命通会卷二·论天德（命中或为干或为支）'),
('DR_TianDe_Xu', '月支', '戌月', '天德', '', '丙', '贵人吉神', '三命通会卷二·论天德（命中或为干或为支）'),
('DR_TianDe_Hai', '月支', '亥月', '天德', '', '乙', '贵人吉神', '三命通会卷二·论天德（命中或为干或为支）'),
('DR_TianDe_Zi', '月支', '子月', '天德', '', '巳', '贵人吉神', '三命通会卷二·论天德（命中或为干或为支）'),
('DR_TianDe_Chou', '月支', '丑月', '天德', '', '庚', '贵人吉神', '三命通会卷二·论天德（命中或为干或为支）'),
('DR_YueDe_YinWuXu', '月支', '寅午戌月', '月德', '', '丙', '贵人吉神', '三命通会卷二·论月德'),
('DR_YueDe_ShenZiChen', '月支', '申子辰月', '月德', '', '壬', '贵人吉神', '三命通会卷二·论月德'),
('DR_YueDe_HaiMaoWei', '月支', '亥卯未月', '月德', '', '甲', '贵人吉神', '三命通会卷二·论月德'),
('DR_YueDe_SiYouChou', '月支', '巳酉丑月', '月德', '', '庚', '贵人吉神', '三命通会卷二·论月德'),
('DR_JieSha_ShenZiChen', '年支/日支', '申子辰', '劫煞', '', '巳', '凶煞', '三命通会卷二·论劫煞'),
('DR_JieSha_YinWuXu', '年支/日支', '寅午戌', '劫煞', '', '亥', '凶煞', '三命通会卷二·论劫煞'),
('DR_JieSha_SiYouChou', '年支/日支', '巳酉丑', '劫煞', '', '寅', '凶煞', '三命通会卷二·论劫煞'),
('DR_JieSha_HaiMaoWei', '年支/日支', '亥卯未', '劫煞', '', '申', '凶煞', '三命通会卷二·论劫煞'),
('DR_WangShen_ShenZiChen', '年支/日支', '申子辰', '亡神', '', '亥', '凶煞', '三命通会卷二·论亡神'),
('DR_WangShen_YinWuXu', '年支/日支', '寅午戌', '亡神', '', '巳', '凶煞', '三命通会卷二·论亡神'),
('DR_WangShen_SiYouChou', '年支/日支', '巳酉丑', '亡神', '', '申', '凶煞', '三命通会卷二·论亡神'),
('DR_WangShen_HaiMaoWei', '年支/日支', '亥卯未', '亡神', '', '寅', '凶煞', '三命通会卷二·论亡神'),
('DR_GuChen_HaiZiChou', '年支', '亥子丑', '孤辰', '', '寅', '凶煞', '三命通会卷二·论孤辰寡宿'),
('DR_GuaSu_HaiZiChou', '年支', '亥子丑', '寡宿', '', '戌', '凶煞', '三命通会卷二·论孤辰寡宿'),
('DR_GuChen_YinMaoChen', '年支', '寅卯辰', '孤辰', '', '巳', '凶煞', '三命通会卷二·论孤辰寡宿'),
('DR_GuaSu_YinMaoChen', '年支', '寅卯辰', '寡宿', '', '丑', '凶煞', '三命通会卷二·论孤辰寡宿'),
('DR_GuChen_SiWuWei', '年支', '巳午未', '孤辰', '', '申', '凶煞', '三命通会卷二·论孤辰寡宿'),
('DR_GuaSu_SiWuWei', '年支', '巳午未', '寡宿', '', '辰', '凶煞', '三命通会卷二·论孤辰寡宿'),
('DR_GuChen_ShenYouXu', '年支', '申酉戌', '孤辰', '', '亥', '凶煞', '三命通会卷二·论孤辰寡宿'),
('DR_GuaSu_ShenYouXu', '年支', '申酉戌', '寡宿', '', '未', '凶煞', '三命通会卷二·论孤辰寡宿');

-- ============================================================================
-- 9. 十神映射（100）｜内置查表，非本体个体｜出处：渊海子平卷之一·论日为主（日干生克同类，同异性分正偏）
-- ============================================================================
INSERT INTO `ten_god_map` (map_id, day_stem, target_stem, ten_god, source_note) VALUES
('TGM_Jia_Jia', '甲', '甲', '比肩', '渊海子平卷之一·论日为主'),
('TGM_Jia_Yi', '甲', '乙', '劫财', '渊海子平卷之一·论日为主'),
('TGM_Jia_Bing', '甲', '丙', '食神', '渊海子平卷之一·论日为主'),
('TGM_Jia_Ding', '甲', '丁', '伤官', '渊海子平卷之一·论日为主'),
('TGM_Jia_Wu', '甲', '戊', '偏财', '渊海子平卷之一·论日为主'),
('TGM_Jia_Ji', '甲', '己', '正财', '渊海子平卷之一·论日为主'),
('TGM_Jia_Geng', '甲', '庚', '偏官', '渊海子平卷之一·论日为主'),
('TGM_Jia_Xin', '甲', '辛', '正官', '渊海子平卷之一·论日为主'),
('TGM_Jia_Ren', '甲', '壬', '偏印', '渊海子平卷之一·论日为主'),
('TGM_Jia_Gui', '甲', '癸', '正印', '渊海子平卷之一·论日为主'),
('TGM_Yi_Jia', '乙', '甲', '劫财', '渊海子平卷之一·论日为主'),
('TGM_Yi_Yi', '乙', '乙', '比肩', '渊海子平卷之一·论日为主'),
('TGM_Yi_Bing', '乙', '丙', '伤官', '渊海子平卷之一·论日为主'),
('TGM_Yi_Ding', '乙', '丁', '食神', '渊海子平卷之一·论日为主'),
('TGM_Yi_Wu', '乙', '戊', '正财', '渊海子平卷之一·论日为主'),
('TGM_Yi_Ji', '乙', '己', '偏财', '渊海子平卷之一·论日为主'),
('TGM_Yi_Geng', '乙', '庚', '正官', '渊海子平卷之一·论日为主'),
('TGM_Yi_Xin', '乙', '辛', '偏官', '渊海子平卷之一·论日为主'),
('TGM_Yi_Ren', '乙', '壬', '正印', '渊海子平卷之一·论日为主'),
('TGM_Yi_Gui', '乙', '癸', '偏印', '渊海子平卷之一·论日为主'),
('TGM_Bing_Jia', '丙', '甲', '偏印', '渊海子平卷之一·论日为主'),
('TGM_Bing_Yi', '丙', '乙', '正印', '渊海子平卷之一·论日为主'),
('TGM_Bing_Bing', '丙', '丙', '比肩', '渊海子平卷之一·论日为主'),
('TGM_Bing_Ding', '丙', '丁', '劫财', '渊海子平卷之一·论日为主'),
('TGM_Bing_Wu', '丙', '戊', '食神', '渊海子平卷之一·论日为主'),
('TGM_Bing_Ji', '丙', '己', '伤官', '渊海子平卷之一·论日为主'),
('TGM_Bing_Geng', '丙', '庚', '偏财', '渊海子平卷之一·论日为主'),
('TGM_Bing_Xin', '丙', '辛', '正财', '渊海子平卷之一·论日为主'),
('TGM_Bing_Ren', '丙', '壬', '偏官', '渊海子平卷之一·论日为主'),
('TGM_Bing_Gui', '丙', '癸', '正官', '渊海子平卷之一·论日为主'),
('TGM_Ding_Jia', '丁', '甲', '正印', '渊海子平卷之一·论日为主'),
('TGM_Ding_Yi', '丁', '乙', '偏印', '渊海子平卷之一·论日为主'),
('TGM_Ding_Bing', '丁', '丙', '劫财', '渊海子平卷之一·论日为主'),
('TGM_Ding_Ding', '丁', '丁', '比肩', '渊海子平卷之一·论日为主'),
('TGM_Ding_Wu', '丁', '戊', '伤官', '渊海子平卷之一·论日为主'),
('TGM_Ding_Ji', '丁', '己', '食神', '渊海子平卷之一·论日为主'),
('TGM_Ding_Geng', '丁', '庚', '正财', '渊海子平卷之一·论日为主'),
('TGM_Ding_Xin', '丁', '辛', '偏财', '渊海子平卷之一·论日为主'),
('TGM_Ding_Ren', '丁', '壬', '正官', '渊海子平卷之一·论日为主'),
('TGM_Ding_Gui', '丁', '癸', '偏官', '渊海子平卷之一·论日为主'),
('TGM_Wu_Jia', '戊', '甲', '偏官', '渊海子平卷之一·论日为主'),
('TGM_Wu_Yi', '戊', '乙', '正官', '渊海子平卷之一·论日为主'),
('TGM_Wu_Bing', '戊', '丙', '偏印', '渊海子平卷之一·论日为主'),
('TGM_Wu_Ding', '戊', '丁', '正印', '渊海子平卷之一·论日为主'),
('TGM_Wu_Wu', '戊', '戊', '比肩', '渊海子平卷之一·论日为主'),
('TGM_Wu_Ji', '戊', '己', '劫财', '渊海子平卷之一·论日为主'),
('TGM_Wu_Geng', '戊', '庚', '食神', '渊海子平卷之一·论日为主'),
('TGM_Wu_Xin', '戊', '辛', '伤官', '渊海子平卷之一·论日为主'),
('TGM_Wu_Ren', '戊', '壬', '偏财', '渊海子平卷之一·论日为主'),
('TGM_Wu_Gui', '戊', '癸', '正财', '渊海子平卷之一·论日为主'),
('TGM_Ji_Jia', '己', '甲', '正官', '渊海子平卷之一·论日为主'),
('TGM_Ji_Yi', '己', '乙', '偏官', '渊海子平卷之一·论日为主'),
('TGM_Ji_Bing', '己', '丙', '正印', '渊海子平卷之一·论日为主'),
('TGM_Ji_Ding', '己', '丁', '偏印', '渊海子平卷之一·论日为主'),
('TGM_Ji_Wu', '己', '戊', '劫财', '渊海子平卷之一·论日为主'),
('TGM_Ji_Ji', '己', '己', '比肩', '渊海子平卷之一·论日为主'),
('TGM_Ji_Geng', '己', '庚', '伤官', '渊海子平卷之一·论日为主'),
('TGM_Ji_Xin', '己', '辛', '食神', '渊海子平卷之一·论日为主'),
('TGM_Ji_Ren', '己', '壬', '正财', '渊海子平卷之一·论日为主'),
('TGM_Ji_Gui', '己', '癸', '偏财', '渊海子平卷之一·论日为主'),
('TGM_Geng_Jia', '庚', '甲', '偏财', '渊海子平卷之一·论日为主'),
('TGM_Geng_Yi', '庚', '乙', '正财', '渊海子平卷之一·论日为主'),
('TGM_Geng_Bing', '庚', '丙', '偏官', '渊海子平卷之一·论日为主'),
('TGM_Geng_Ding', '庚', '丁', '正官', '渊海子平卷之一·论日为主'),
('TGM_Geng_Wu', '庚', '戊', '偏印', '渊海子平卷之一·论日为主'),
('TGM_Geng_Ji', '庚', '己', '正印', '渊海子平卷之一·论日为主'),
('TGM_Geng_Geng', '庚', '庚', '比肩', '渊海子平卷之一·论日为主'),
('TGM_Geng_Xin', '庚', '辛', '劫财', '渊海子平卷之一·论日为主'),
('TGM_Geng_Ren', '庚', '壬', '食神', '渊海子平卷之一·论日为主'),
('TGM_Geng_Gui', '庚', '癸', '伤官', '渊海子平卷之一·论日为主'),
('TGM_Xin_Jia', '辛', '甲', '正财', '渊海子平卷之一·论日为主'),
('TGM_Xin_Yi', '辛', '乙', '偏财', '渊海子平卷之一·论日为主'),
('TGM_Xin_Bing', '辛', '丙', '正官', '渊海子平卷之一·论日为主'),
('TGM_Xin_Ding', '辛', '丁', '偏官', '渊海子平卷之一·论日为主'),
('TGM_Xin_Wu', '辛', '戊', '正印', '渊海子平卷之一·论日为主'),
('TGM_Xin_Ji', '辛', '己', '偏印', '渊海子平卷之一·论日为主'),
('TGM_Xin_Geng', '辛', '庚', '劫财', '渊海子平卷之一·论日为主'),
('TGM_Xin_Xin', '辛', '辛', '比肩', '渊海子平卷之一·论日为主'),
('TGM_Xin_Ren', '辛', '壬', '伤官', '渊海子平卷之一·论日为主'),
('TGM_Xin_Gui', '辛', '癸', '食神', '渊海子平卷之一·论日为主'),
('TGM_Ren_Jia', '壬', '甲', '食神', '渊海子平卷之一·论日为主'),
('TGM_Ren_Yi', '壬', '乙', '伤官', '渊海子平卷之一·论日为主'),
('TGM_Ren_Bing', '壬', '丙', '偏财', '渊海子平卷之一·论日为主'),
('TGM_Ren_Ding', '壬', '丁', '正财', '渊海子平卷之一·论日为主'),
('TGM_Ren_Wu', '壬', '戊', '偏官', '渊海子平卷之一·论日为主'),
('TGM_Ren_Ji', '壬', '己', '正官', '渊海子平卷之一·论日为主'),
('TGM_Ren_Geng', '壬', '庚', '偏印', '渊海子平卷之一·论日为主'),
('TGM_Ren_Xin', '壬', '辛', '正印', '渊海子平卷之一·论日为主'),
('TGM_Ren_Ren', '壬', '壬', '比肩', '渊海子平卷之一·论日为主'),
('TGM_Ren_Gui', '壬', '癸', '劫财', '渊海子平卷之一·论日为主'),
('TGM_Gui_Jia', '癸', '甲', '伤官', '渊海子平卷之一·论日为主'),
('TGM_Gui_Yi', '癸', '乙', '食神', '渊海子平卷之一·论日为主'),
('TGM_Gui_Bing', '癸', '丙', '正财', '渊海子平卷之一·论日为主'),
('TGM_Gui_Ding', '癸', '丁', '偏财', '渊海子平卷之一·论日为主'),
('TGM_Gui_Wu', '癸', '戊', '正官', '渊海子平卷之一·论日为主'),
('TGM_Gui_Ji', '癸', '己', '偏官', '渊海子平卷之一·论日为主'),
('TGM_Gui_Geng', '癸', '庚', '正印', '渊海子平卷之一·论日为主'),
('TGM_Gui_Xin', '癸', '辛', '偏印', '渊海子平卷之一·论日为主'),
('TGM_Gui_Ren', '癸', '壬', '劫财', '渊海子平卷之一·论日为主'),
('TGM_Gui_Gui', '癸', '癸', '比肩', '渊海子平卷之一·论日为主');

-- ============================================================================
-- 10. 运行态命例记录｜case_record
--     运行态数据（由 persistAndReturn 经 OBDA addComponent 落库），全集不含历史命例，留空。
-- ============================================================================

-- ============================================================================
-- 11. 运行态告警标志｜alert_flag
--     运行态数据（由流程按需落库），全集不含历史告警，留空。
-- ============================================================================
