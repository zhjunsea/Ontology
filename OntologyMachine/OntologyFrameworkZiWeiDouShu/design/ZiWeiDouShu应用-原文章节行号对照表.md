# 紫微斗數全書 · 原文位置与「章节↔行号↔代码方法」对照表

> 本文件为**引用台账/参考件**（非人工闸门产物）。所有行号均来自权威原著电子书的抽取文本，逐字可核，**未臆造**。
> 用途：为「依原著条件构造命例（B 类断诀分支覆盖）」「核对断法落地」提供可检索的原文定位。

## 0. 原著位置（权威源头 + 抽取链）
| 层级 | 路径 | 说明 |
|---|---|---|
| ★原著电子书（权威） | `D:\Documents\中国传统\紫微斗數全書.epub` | 标准 EPUB（含 `OPS/cN_*.xhtml` 分卷、toc.ncx、图片） |
| EPUB 抽取副本 | `D:\work\Ontology\OntologyMachine\HWCodeArtsDir\zwds-corpus\OPS\` | 与上面 EPUB 内文件**同名同大小**（同一书抽取） |
| 分行纯文本（`cN L…` 行号基准） | `D:\work\Ontology\OntologyMachine\HWCodeArtsDir\zwds-txt\cN_*.txt` | 由各卷 xhtml 转成的按行编号 txt |

**卷次代号映射（`cN`）**：`c2＝卷一`、`c3＝卷二`、`c4＝卷三`（`c0`书名页、`c1`全览）。
> 代码/design/台账里的 `cN L行` 一律指 `zwds-txt\cN_*.txt` 的行号；其文字即权威 EPUB 对应章节内容。

| 代号 | 卷 | txt 文件 | 行数 |
|---|---|---|---|
| c2 | 卷一 | `c2_zi_wei_dou_shu_quan_shu_juan_yi.txt` | 649 |
| c3 | 卷二 | `c3_zi_wei_dou_shu_quan_shu_juan_er.txt` | 1283 |
| c4 | 卷三 | `c4_zi_wei_dou_shu_quan_shu_juan_san.txt` | 477 |

---

## 1. 卷一（c2）章节行号 — 赋/论/问答/格局
| 行号 | 章节 | 对应代码（主要） |
|---|---|---|
| L11 | 羅序 | — |
| L16 | 太微賦 | `judgeTaiweiMingGe`（日丽中天/水澄桂萼/众水朝东/金舆捧栉） |
| L63 | 形性賦 | `handleInterpretStarNature`（starXingXing 形性） |
| L96 | 星垣論 | 星垣生克/落宫（辅助） |
| L105 | 斗數準繩 | — |
| L119 | 斗數發微論 | — |
| L140 | 重補斗數彀率 | `judgeTaiweiMingGe*`（金舆捧栉等具名格） |
| L161 | 增補太微賦 | `judgeTaiweiMingGeExtended`（魁钺同行/禄文拱命/日月夹财/刑囚夹印…） |
| L196–390 | 諸星問答論（37问 + 羊陀二星总论 L342 / 羊陀火铃四星总论 L366） | `STAR_CHARACTER`/`STAR_QA_CHARACTER`（化气·所主），`handleInterpretStarNature` |
| L392 | 斗數骨髓賦 | `judgeMingGeGrade`/斗数骨髓赋夹拱格（紫府同宫/七杀朝斗/科权禄拱…） |
| L477 | 女命骨髓賦 | `judgeFemaleBoneMarrow`（女命逐句） |
| L545 | 定富贵贫贱十等论 | `judgeTenGrades`（十等论） |
| L556 | 十二宫诸星得地合格诀 | `judgePalaceZhiGrid`（得地合格） |
| L569 | 十二宫诸星失陷破格诀 | `judgePalaceZhiGrid`（失陷破格） |
| L579 | 十二宫诸星得地富贵论 | 得地富贵四论 |
| L590 | 十二宫诸星失陷贫贱论 | 失陷贫贱四论 |
| L597 | 定富局 | `handleDeterminePattern`（富局） |
| L604 | 定贵局 | `handleDeterminePattern`（贵局：禄马佩印/权禄生逢/科权禄拱…） |
| L632 | 定贫贱局 | `handleDeterminePattern`（日月藏辉/财与囚仇/马落空亡…） |
| L641 | 定杂局 | 步数无依/水上驾星/锦上添花（`handleAnalyzeMajorLimit` 释读） |

> 注：代码 `BRIGHTNESS` 表注为「卷二·星辰庙陷」；经检索 c1/c2/c3/c4 四卷，**未见独立『星辰庙陷』章节标题**（现存“庙陷”皆正文句，如 c4 L105『论太岁小限星辰庙陷遇十二宫中吉凶』）。该 240 格庙陷表应为 EPUB 内嵌 HTML 表格（非 h3 章节），具体表位置需按 EPUB 表格定位〔待核，不臆定〕。

---

## 2. 卷二（c3）章节行号 — 安星诀 / 生克 / 十二宫吉凶诀
| 行号 | 章节 | 对应代码（主要） |
|---|---|---|
| L12 | 安身命例 | `handleDetermineLifePalaces`（命身宫/五行局） |
| L19 | 安十二宫例（男女逆布） | 十二宫布列 |
| L22 | 起五行寅例 | `bureauOf`（纳音→五行局） |
| L24 | 六十花甲子纳音歌 | `NAYIN/NAYIN_ELEM` |
| L31 | 安南北斗诸星诀 | `ZIWEI_SERIES_OFF`/`TIANFU_SERIES_OFF`/`placeMainStars` |
| L34/39/43/47/51/54/57 | 安昌曲/辅弼/魁钺/天马/禄存/羊陀/火铃诀 | `placeAuxiliaryStars`（KUI_YUE/LUCUN/TIANMA…） |
| L64/68/71/76/78/80/83/85/87/91/94/97/100/107/109 | 天空地劫/天伤天使/太岁杀禄/刑姚/三台八座/哭虚/龙池凤阁/台辅封/封诰/红鸾天喜/丧门白虎/斗君/天德月德/截路空亡/旬中空亡 诀 | `placeMiscStars`、`handlePlaceFourTransform`（斗君…） |
| L112/116 | 安大限诀／安童限诀 | `daXianPalace`/大限 |
| L118/121 | 安命主／安身主 | `LIFE_LORD`/`BODY_LORD` |
| L123 | 论安命金锁铁蛇关 | `judgeXiaoErMing`（金锁铁蛇关） |
| L126 | 定男女竹萝三限 | `handleAnalyzeMajorLimit`（竹萝三限·释读） |
| L129 | 定十二宫弱强 | `palaceStrength`（宫强弱） |
| L132 | 定十二宫星辰落闲 | `isIdleStar`（落闲） |
| L135 | 安流禄流羊流陀诀 | 流曜（流年重算） |
| L137 | 论星辰生克制化 | `palaceStarRelation`（生克制化 5 类） |
| L141–189 | 论诸星分属南北斗化吉凶并分属五行 | `starElement`/`STAR_CHARACTER`（星五行/化气归属） |
| L458–1276 | 十二宫各星吉凶诀：一命宫 L458｜二兄弟 L977｜三妻妾 L1000｜四子女 L1024｜五财帛 L1057｜六疾厄 L1087｜七迁移 L1111｜八奴仆 L1136｜九官禄 L1161｜十田宅 L1199｜十一福德 L1223｜十二父母 L1251 | `palace_classic.tsv`、`judgeMingGongGe`、`handleInterpretStarNature.palaceClassic` |

---

## 3. 卷三（c4）章节行号 — 谈星要论 / 运限断诀
| 行号 | 章节 | 对应代码（主要） |
|---|---|---|
| L11 | 谈星要论 | `judgeTanXingYaoLun`（八座六宫聚吉/僧道等） |
| L30 | 论人命入格 | `judgeMingGeGrade`/`judgeLongevity`（职业格/五级） |
| L37 | 论格星数高下 | `judgeGeXingShu`（星数三档） |
| L55 | 论男女命同异 | `judgeNanNvTongYi`、`judgeFemaleBoneMarrow`（并参） |
| L58 | 论小儿命 | `judgeXiaoErMing` |
| L62 | 定小儿生时诀 | 术者验时法 |
| L66/L68 | 论人生时安命吉凶／论人生时要审的确 | `birthTimeVerifyText` |
| L70 | 论小儿克亲 | `judgeKeQin`（寅亥巳生人见午申酉亥时） |
| L72 | 论命先贫后富 | 辅助断语 |
| L75 | 论大限十年祸福何如 | `judgeDaXianShiNian` |
| L77 | 论二限太岁吉凶 | `judgeTwoLimitsTaiSui`（岁限俱凶则凶） |
| **L79** | **论行限分南北斗** | **`nanBeiDouSong`（已恢复接线）** |
| **L83** | **论流年太岁吉凶星杀** | **`judgeLiuNianTaiSui`（已恢复接线）** |
| L85 | 论阴骘延寿 | `handleAggregateReading`（阴骘劝诫） |
| L87 | 论羊陀迭并 | `judgeYangTuoDieBing` |
| L89 | 论七杀重逢 | `judgeQiShaReFeng` |
| L92 | 论大小限星辰过十二宫遇十二支所忌诀 | `LIMIT_TABOO`（逐支所忌） |
| **L102** | **论立命行限宫歌** | **`XINGXIAN_GE`（已恢复接线，`verse("PV_XINGXIAN_GE")`）** |
| L178 | 论诸星同垣各司所宜 分别富贵贫贱夭寿 | `judgeTongYuan`/`tongYuanObjective(Full)`、`STAR_TONGYUAN` |
| L105 起 | 论太岁小限星辰庙陷遇十二宫吉凶 | `judgeLiuNianTaiSui`（流年庙陷参断） |

> 卷三 L79–82「论行限分南北斗」、L83–84「论流年太岁吉凶星杀」、L102–104「论立命行限宫歌」三段原文已在本文件 §0 所述 `c4_*.txt` 逐字核对；且 `nanBeiDouSong/judgeLiuNianTaiSui/XINGXIAN_GE` 已按此恢复接线（见代码 `handleAnalyzePalaceOverlay.limitPalaceSong`）。

---

## 4. 用法
- 构造 B 类“可命中”命例时，按上表**回原文对应行**确认客观条件（星曜组合/宫位/生干/生时），据此选定出生数据。
- 核对 C 类“待考/不可达”时，若上表对应章查无客观条文，即依《全書》判为原文未载，保留〔待考〕不臆造。
- `cN L行` 一律以 `zwds-txt\cN_*.txt` 为准（权威 EPUB 对应章节的同文分行）。

