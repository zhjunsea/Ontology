# HWCodeArtsDir 历史与记忆总结

> 视角：张仲景 / 胡希恕（经方派）。铁律 26/61：医书医理 > 本体 > 代码。
> 本文是对 `HWCodeArtsDir/` 工作区历史痕迹与阶段性记忆的**如实汇总**，所有结论均可回溯到该目录内的具体文件（见第九节溯源索引）。
> 与 `design/本体固化记录.md` 互补：后者是结论性固化声明，本文是过程性记录与依据汇编。

---

## 一、目录定位与作用

`HWCodeArtsDir/` 是项目的**工作区 / 临时产物区**（对应铁律 66「临时产物一律写入工作区」，CodeArts 版）。

- 一切分析脚本、dump、日志、快照、备份均落在此处，**不改动正式产物**；
- 正式产物位于 `ontology/`（TBox/ABox 本体）、`OntologyMachine/`（应用与框架）、`design/`（设计文档）；
- 本目录是**可丢弃的中间层**，但记录了本体医理复审、规则 SWRL 化、启动性能测量、回归测试的完整证据链。

---

## 二、目录构成速览

| 子项 | 规模 | 内容 |
|---|---|---|
| 根目录可读产物 | 40 项 | `adjudicate_dump.txt`、`pairs_dump.txt`、`ws37.txt`、`noreason_comments.txt`、`fz_audit_readable.txt`、`tests37.txt`、`taiyang.diff`、`head_taiyang.owl` |
| `scripts/` | 95 项 | Python/PS1 脚本与 JSON/XML 结构化产物（解析、审计、转换、dump、套件运行） |
| `logs/` | 68 项 | 套件运行日志、worker/Camunda 日志、启动测量、构建日志、规则备份 |
| `head_fz/` | 7 个 owl | 修订前的按六经拆分历史快照：`duli / hebing / jueyin / shaoyang_yangming / shaoyin_taiyin / taiyang / zabing` |
| `probe/` | 8 项 | `TBoxProbe.java`（独立 TBox 分类耗时探针）+ `.class` + 规则快照 |
| `backup/` | 2 项 | `rules.owl.bak`、`shaoyin_taiyin.owl.bak` |
| `build/` | 空 | — |
| `.merkle-snapshot.json` | 466 文件 | 全仓文件哈希清单（rootHash + 逐文件 hash/size/modified） |

---

## 三、工作主线时间线（文件时间戳 2026-09-22 → 09-27）

| 时间 | 阶段 | 代表产物 |
|---|---|---|
| 09-22 | 方后注加减规则 SWRL 化 | `scripts/convert_rules_to_swrl.py`、`rules_swrl_fragment.xml`、`rules_analysis.json` |
| 09-23 ~ 09-24 | 全量方证医理复审，eq 六经由「笛卡尔积全出」收敛为按医理裁定 | `fz_audit_readable.txt`、`fangzheng_rules.json`、`e2e_cases.json` |
| 09-24 ~ 09-25 | 焦点方证逐条裁定与 dump/diff | `dump15.txt`、`pairs_dump.txt`、`adjudicate_dump.txt`、`fz_diff.txt`、`taiyang.diff` |
| 09-26 ~ 09-27 | 本体固化 + 固化后全量回归 | `本体固化记录.md`、`logs/suite_regression_frozen.log` |

---

## 四、本体医理复审与修订（核心记忆）

本轮修订以**六经归属、八纲属性、主证/或然症**三类为主，统一定式：**eq 六经 = 病位 × 病性**，并由 `bagangAttr` 配对解释（铁律 22 补充）。依据均取医书原文或经方大师（胡希恕、冯世纶）论断，来源见 `fz_audit_readable.txt` 的 `[REASON: ...]` 与 `noreason_comments.txt` 注释。

### 4.1 六经归属裁定（依医书 / 经方大师）

- **白头翁汤证 / 白头翁加甘草阿胶汤证** → 阳明。冯世纶「当属阳明病证」；胡希恕「下利，渴欲饮水者，为里有热」（371/373 条）；《金匮》产后「产后下利虚极」。
- **黄连阿胶汤证** → 阳明。冯世纶「治里热兼养血之剂…属阳明病」（303 条）。
- **苦酒汤证 / 桔梗汤证 / 甘草汤证 / 猪肤汤证** → 少阳。胡希恕「少阴病传入半表半里而发为少阳病」（310/311/312 条），少阴咽痛由表及半表半里。
- **麻黄升麻汤证 / 乌梅丸证** → 厥阴。冯世纶「当属厥阴病证」（357 条）；338 条蛔厥「上热下寒」。
- **桂枝加附子汤证 / 桂枝附子汤证 / 甘草附子汤证 / 白术附子汤证** → 少阴。胡希恕痹证六经归类「属少阴病（即表阴证）」（174 条）；20 条误治由表阳陷入表阴。
- **大青龙汤证** → 太阳阳明。胡希恕「辨六经为太阳阳明合病」（38 条 表实无汗 + 烦躁里热）。
- **小青龙汤证 / 苓桂枣甘汤证 / 厚朴麻黄汤证** → 太阳太阴（外邪里饮）；厚朴麻黄汤证为太阳太阴阳明合病（《金匮》「咳而脉浮者」+ 石膏清里热）。
- **三泻心汤证（半夏/生姜/甘草）/ 黄连汤证 / 干姜黄芩黄连人参汤证 / 附子泻心汤证 / 栀子干姜汤证** → 寒热错杂，里 × {阴,阳} = 太阴阳明，补 `Han`（149/155/157/158/173/359/80 条）。
- **白通汤证 / 白通加猪胆汁汤证** → 太阴。胡希恕「少阴病下利，这是里虚寒…」（314/315 条），干呕烦为虚阳上浮之假热，故去 `Re/Yang`。
- **牡蛎汤证** → 厥阴。《金匮》疟病篇「疟多寒者，名曰牡疟」——半表半里 × 阴。
- **半夏散及汤证** → 少阴（表阴寒证），313 条「少阴病，咽中痛」。
- **文蛤汤证** → 胡希恕「文蛤汤是发汗药，是解热发汗」；**桂枝二越婢一汤证** → 胡希恕「清肃其表里」（27 条）。
- **越婢汤证** → 太阳阳明。胡希恕「越婢汤…他就有里热」（风水，麻黄 + 石膏）。
- **桂枝加桂汤证 / 桂枝加黄芪汤证 / 桂枝去芍药加附子汤证** → 太阳（117 条 / 金匮黄汗 / 22 条）。

### 4.2 八纲修正

- **去 Yang**：半夏泻心汤证 / 生姜泻心汤证 / 甘草泻心汤证——原 `belongsToLiujing` 兼挂阳明与自身八纲 `{Li,Yin,Shi,Re}`（无 Yang）矛盾，撤除，仅保留太阴（见 `ws37.txt` cmt）。
- **补 Han**：苓桂术甘汤证（《金匮·痰饮》「病痰饮者，当以温药和之」、67 条误治伤中阳、方全温、同类方证一致，见 `logs/dump15.txt`）。
- **补 Re**：苓甘五味加姜辛半杏大黄汤证（《金匮·痰饮》「面热如醉…胃热上冲」，寒饮夹胃热，Han 与 Re 并标）。
- **补核心主证**：白虎加桂枝汤证补白虎汤里热核心（大热大汗大渴 + 但热无寒 + 骨节疼烦，铁律 53）。

### 4.3 主证 / 或然症校正（依条文明文）

- **四逆散证**（318 条）：主证仅「四逆」，咳/悸/小便不利/腹中痛/泄利下重为或然症；原列「往来寒热、胸胁苦满、腹痛、下利」条文无据，已删/改列或然。
- **麻黄汤证**（35 条）：条文无「或」字，原或然症（喘、恶寒、发热、头痛）皆明文主证，升入主证并清空或然症（35 条作「恶风」非「恶寒」）。
- **葛根汤证**（31/32 条）：补「下利」入主证；原或然「发热、恶寒」无据删除。
- **栝楼薤白白酒汤证**（《金匮·胸痹》）：补「胸背痛」「咳唾」入主证，删脉象或然症。
- **白头翁汤证**：371/373 条「欲饮水」即口渴，原误列或然症，升入主证。
- **通脉四逆加猪胆汁汤证**（390 条）：补「四肢拘急」「脉微欲绝」。
- **四逆散证/诸条**：`axis`（鉴别轴）由「主证 + 或然症」明确改写。

### 4.4 OWL 工程性修正（医理不变的等价改写）

- **否定约束失效**：含 `¬症状` 的 eq 在 OWL 开放世界下患者未断言即无法推出否定，致方证**永不 realize**。已改为**纯正向定义**，鉴别保留于 `differentialAxis` 注释。涉及：桂枝甘草龙骨牡蛎汤证（118 条）、苓桂术甘汤证（67 条，原含 ¬Shenrundong/¬Xiali）。
- **布尔恒等去重**：茯苓甘草汤证原主证 `(心下悸∧不渴∧小便不利)∨(手足冷∧心下悸∧不渴)` 依 `(A∧B∧C)∨(D∧A∧B) ≡ A∧B∧(C∨D)` 改写去重（`noreason_comments.txt` [23]）。

### 4.5 重复类合并与新增判据

- **删除重复类**：`taiyang.owl` 的 `Fulingguizhibaizhugancaotangzheng` 与 `shaoyin_taiyin.owl` 的 `Lingguizhugantangzheng` 同方证，删除前者、保留后者（太阴），并删除过时用例（280 → 279）。
- **新增八纲判据 `Panju_B10`**（`tcm-core.owl`，B9 后 C1 前）：`⊑ Li ⊓ Shi`，条件 = `Xinxiajian`（心下坚）+ `Chenxianmai`（沉弦脉）或 `Fumai_Yin`（伏脉）。依据《金匮·痰饮》「病者脉伏…心下续坚满，此为留饮」、《濒湖脉学》「沉弦悬饮内痛」，用于支撑甘遂半夏汤证（太阴阳明合病）。解析工具见 `scripts/parse_panju.py`。
- **甘遂半夏汤证**：eq 由 `Xinxiapi` 改 `Xinxiajian`（忠实《金匮》「心下续坚满」）。

> 注：`design/方证-兼夹本体设计.md` L322 曾列「甘遂半夏汤证｜太阴」，与本裁定「太阴阳明合病」不一致；以医理复审结论为准，该设计行为历史快照（见 `本体固化记录.md` §3.3）。

---

## 五、脚本工具集（scripts/）

| 类别 | 代表脚本 | 作用 |
|---|---|---|
| 判据解析 | `parse_panju.py` | 解析 `tcm-core.owl` 中 `Panju_*` 判据的八纲与症状组（union/leaf） |
| 本体解析 | `parse_defs.py`、`parse_fangzheng.py`、`parse_rules*.py` | 拆解方证定义、规则文本 |
| 规则 SWRL 化 | `convert_rules_to_swrl.py`、`merge_swrl_to_rules.py`、`fix_swrl_method.py` | `rules.owl` 方后注加减规则 → SWRL XML |
| 审计 | `audit_eq*.py`、`audit_comment_vs_eq.py`、`audit_eq_vs_bagang.py`、`audit_struct68.py`、`verify_zhengs.py`、`stat_pulse_rules.py` | eq 六经 vs 八纲一致性、注释 vs eq 矛盾、结构校验、脉象规则统计 |
| dump/比对 | `dump_*.py`、`cmp18/19.py`、`head_vs_cur.py`、`eqdiff.py` | HEAD/CUR 差异导出 |
| 查询 | `query_*.py`、`q_*.py` | 针对汉方/理中/或然症等的定向查询 |
| 导出 | `export_fz_table.py`、`extract_*.py` | 全量方证表导出（264 方证） |
| 套件运行 | `run_all_fangzheng_tests.ps1/.cmd`、`restart_worker*.ps1`、`measure_startup.ps1` | 全量套件、worker 重启、启动测量 |

结构化产物：`fangzheng_defs.json`、`fangzheng_rules.json`(1.25 MB)、`rules_full.json`、`fz_audit_table.json`、`e2e_cases.json`、`e2e_generated.json`、`e2e_actual.json`、`eq_worksheet.json`、`rule_analysis.json`、`rules_prop_decl.xml`、`rules_swrl_fragment.xml`(1.58 MB)。

---

## 六、运行与性能结论

### 6.1 全量套件（`logs/suite_regression_frozen.log`，固化后回归）

- **用例总数 279，通过 279，失败 0，跳过 0**；总墙钟 **150.3 s**（固化前 213.6 s），单用例最大 4.22 s。
- 逐类：HerbRuleEngineTest 8/8、DuliFangzhengTest 49/49、HebingFangzhengTest 7/7、JianjiaFangzhengTest 0（无用例）、JueyinFangzhengTest 13/13、ShaoyangYangmingFangzhengTest 46/46、ShaoyinTaiyinFangzhengTest 55/55、TaiyangFangzhengTest 39/39、ZabingFangzhengTest 62/62。
- 规则规模：`rules.owl` 共 **46 个方证 / 84 条规则**（`suite_final.log`，均为**原始注解式**方后注规则）；
  加减方生成示例含「小柴胡汤证 + 渴 → 去半夏，加人参、栝楼根」等。
  > **注（2026-09-28）**：`rules.owl` 现为 **v2.9**——84 条注解式规则已 **SWRL 化**（按 OR / 多动作拆分展开为
  > **259 条 `swrl` 规则**，由 Openllet 自动激发）；原注解属性仅保留作文档，不再由应用层解析执行。

### 6.2 启动与推理耗时（`本体固化记录.md` §5）

| 口径 | 实测 |
|---|---|
| OntologyService 加载（探针 3 取样） | 2844 / 2638 / 2604 ms（中位 ~2638 ms） |
| ReasonerService 初始化（TBox 分类，3 取样） | 18354 / 16929 / 14637 ms（中位 16929 ms） |
| worker 进程启动（多次取样） | 24.160 ~ 34.857 s（中位 30.632 s） |

**铁律 57 达标判定**：① 启动 ≤ 240 s（中位 30.6 s）达标；② 每用例 ≤ 10 s（最大 4.22 s）达标；③ 全绿（279/279）达标。

---

## 七、已知问题与处置

1. **启动「推理爆炸」= Openllet 固有非确定性（pre-existing）**
   - 证据：独立探针 `probe/TBoxProbe.java` 反复运行耗时随机漂移，HEAD 干净基线同样出现爆炸。
   - 结论：Openllet 对含名义量（nominal）的 SROIQ 本体分类存在固有非确定性（tableau 阻塞/展开顺序敏感，铁律 54），**非本体缺陷**。
   - 处置：**禁止**为消除爆炸而改 `eq` 或 `rules.owl`；偶发卡死按 kill + retry（判卡死须看内存是否增长）。

2. **worker 端口 9082 未就绪**：`logs/worker_restart_auto.log` 记录多次「port 9082 did not come up in 180s」；`logs/startup_measure.log` 因端口占用/未释放导致 3 次测量全部失败。

3. **Camunda/Zeebe 网关告警**：`logs/camunda_restart_stdout.log` 出现 `NoRemoteHandler: No remote message handler registered`（任务类型含 `fangzheng-classification`、`bagang-classification`、`herb-modification` 等），系 worker 未注册任务处理器时的网关轮询告警。

---

## 八、固化后的约束与后续动作（铁律 68/69）

1. `ontology/**.owl` **禁止再修改**（铁律 68.3、69.2）；回归期只允许改 `OntologyMachine/OntologyFramework/**`。
2. 回归暴露本体错误不得当场改本体，须记「本体缺陷」、暂停回归，走铁律 68 复审后重新固化。
3. `OpenlletResolver/**`、`OntopOBDAHandler/**` 的 `src/main` 禁区始终有效（铁律 51）。
4. 固化前置校验（均零违反）：`audit_eq_vs_bagang.py` 一致 264 / 不一致 0；`audit_comment_vs_eq.py` 矛盾 0；`export_fz_table.py` 导出 264 方证。
5. 临时产物一律写入 `HWCodeArtsDir/`（铁律 66）。

---

## 九、溯源索引（文件 → 内容）

| 文件 | 内容 |
|---|---|
| `fz_audit_readable.txt` | 全量 264 方证逐条审计表（eq 六经 / belongs / 八纲 / 主证脉），含 `<<< HEAD=` 与 `[REASON: ...]` 医理依据 |
| `noreason_comments.txt` | 无 REASON 标注方证的补齐清单（含人工注释与依据） |
| `adjudicate_dump.txt`、`pairs_dump.txt` | 焦点方证 HEAD（修改前）/ CUR（当前）XML 对比 |
| `ws37.txt`、`tests37.txt`、`ws37.json`、`ws37.py` | 约 37 项方证/用例的修订比对与源码片段 |
| `logs/dump15.txt`、`logs/fz_diff.txt` | 本轮裁定焦点方证 dump 与 diff |
| `taiyang.diff`、`head_taiyang.owl`、`head_fz/*.owl` | 修订前历史快照（按六经拆分） |
| `scripts/*.py`、`*.json`、`*.xml` | 脚本工具与结构化中间产物 |
| `logs/suite_regression_frozen.log` | 固化后全量回归（279/279，150.3 s） |
| `logs/suite_final.log`、`logs/run_suite*.log`、`logs/run_*.log` | 各阶段套件/四诊运行日志 |
| `logs/worker_*.log`、`logs/startup_measure.log`、`logs/jstack_49256.txt` | worker 进程、启动测量、线程栈 |
| `logs/camunda_*.log` | Camunda/Zeebe 网关运行日志 |
| `probe/TBoxProbe.java` | 独立 TBox 分类耗时探针（铁律 54 权威方式） |
| `.merkle-snapshot.json` | 全仓 466 文件哈希快照 |
