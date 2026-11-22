# 紫微斗数应用 · C类 crafted 补测 与 A类真环境验证报告

> 范围：判定族**之外**的编排层增强。完成「真正可补项（C 类罕见格局组合）」与「A 类（编排/基础设施）」两类收尾。
> 口径：仅覆盖**真值可达**的业务判定支；数据防御/异常兜底/逻辑不可达死支**据实保留、不虚报 100%**（沿用已确立「口径 B」）。

## 一、结论

| 项 | 结果 |
|---|---|
| 新增测试资产 | 3 个测试类（2 离线 + 1 真环境带守卫）共 9 用例，**全绿（9/9）** |
| 整类 BRANCH | **4173/4374 → 4212/4374（95.40% → 96.30%，missed 201 → 162，净覆盖 +39 支）** |
| 整类 LINE | 3986/4066 → 3998/4066（missed 80 → 68，+12 行） |
| C 类（可补） | handleDeterminePattern **+14 支**、isJinYuFuJia **+1 支**、palaceStarRelation 0（**逻辑死行**） |
| A 类 | verifyObdaTableCoverage **+3**、loadPalaceClassic **+8**、handlePersistAndReturn **+2**、handleSendAlert **+4**、extractAlertTypeFromReading **+1**；loadStateFromObda 真跑**功能验证通过**（LINE 195/195 全覆盖，813 行语料/同源自检全一致） |
| 真实环境 | 全栈在线实测：MySQL 3306 / Ontop 5.5.0 8080 / Camunda-Zeebe 26500 / RabbitMQ 5672 / 应用 9080·9089 |

## 二、新增测试资产

1. `src/test/java/.../ZiWeiDouShuCClassCraftedCoverageTest.java`（**离线·Mockito+反射**，3 用例）
   - `handleDeterminePattern`：24 张 crafted 命盘（全 14 主星铺设+局部改位，不校验安星真伪，只求真值命中判据），覆盖罕见具名格：机月同梁(命申/寅)、杀破狼、财荫夹印(rel=1/11/假)、荫印拱身、金灿光辉、坐贵向贵(同宫)、贪火相逢、巨机居卯、羊刃入庙(丑/辰)、兼文武、禄逢两杀、马落空亡、明珠出海三支、生年干解析异常兜底(catch)等。
   - `palaceStarRelation`：遍历 14 主星 × 12 支，覆盖比和/宫生星/宫克星/星生宫/星克宫及非法入参。
   - `isJinYuFuJia`：金舆扶驾两侧支 + 三处非法早返回 + 边界(yr=0/sr=0/sr∈1..5且yr<7)。
2. `src/test/java/.../ZiWeiDouShuAClassOfflineCoverageTest.java`（**离线·临时文件/注入/Mockito**，5 用例）
   - `verifyObdaTableCoverage`：空路径 / 文件不可读降级 / 全映射 INFO / 孤儿表 fail-fast 抛错。
   - `loadPalaceClassic`：缓存快路径 / OBDA 语料注入(null/短行/有效) / 资源 TSV 兜底。
   - `handlePersistAndReturn`：backendService 空跳过 / 就绪(桩)写 case_record / 短生年脱敏。
   - `handleSendAlert`：未触发 → SKIPPED_NO_ALERT（离线不触 MQ）。
   - `extractAlertTypeFromReading`：null/空/无命中=/命中=类别/命中=无/无括号 全分支。
3. `src/test/java/.../ZiWeiDouShuObdaRealLoadTest.java`（**真环境·带 assumeTrue 守卫**，1 用例）
   - 进程内 `OBDAHandler.init(真 properties/obda)` → 反射调用 `loadStateFromObda`，断言 `ONTO_BRIGHTNESS`/`ONTO_SIHUA` 被真实数据填充；Ontop/MySQL 任一不可达即 `assumeTrue` 跳过，不阻断离线构建。
   - 实跑日志：`卷二语料库=813 行`、`同源一致性自检 庙陷/四化/紫微定位/流曜/五鼠遁/十二神/同垣/断语星情 异0 ✔ 全部一致`、`取数覆盖自检 27 类自 OBDA 载入`。

## 三、逐方法覆盖率 Delta（基线 → 终态）

| 方法 | 基线 BRANCH | 终态 BRANCH | 覆盖增量 | 备注 |
|---|---|---|---|---|
| handleDeterminePattern | 361/388 (miss 27) | **375/388 (miss 13)** | **+14** | 罕见格局 crafted 盘驱动 |
| isJinYuFuJia | 27/30 (miss 3) | **28/30 (miss 2)** | **+1** | 余 2 为 `sr<=11`/`yr<=11` 恒真冗余（不可达） |
| palaceStarRelation | 15/16 (miss 1) | 15/16 (miss 1) | 0 | 余 1 为「→无明显生克」逻辑死行（详见六） |
| verifyObdaTableCoverage | 12/18 (miss 6) | **15/18 (miss 3)** | **+3** | 临时文件驱动 |
| loadPalaceClassic | 8/20 (miss 12) | **16/20 (miss 4)** | **+8** | 注入语料/资源兜底 |
| handleSendAlert | 6/16 (miss 10) | **10/16 (miss 6)** | **+4** | 离线 SKIP 路径；余为真 MQ 触发路径 |
| handlePersistAndReturn | 3/6 (miss 3) | **5/6 (miss 1)** | **+2** | null/桩写两路；余为 catch 分支 |
| extractAlertTypeFromReading | 10/12 (miss 2) | **11/12 (miss 1)** | **+1** | 全覆盖解析分支 |
| loadStateFromObda | 236/274 (miss 38) | 236/274 (miss 38) | 0（LINE 195/195） | 成功路径真跑已验；余 38 为各表完整性自检**分支偏侧**（详见六） |
| **整类合计** | **4173/4374 (miss 201)** | **4212/4374 (miss 162)** | **+39** | LINE miss 80→68 |

## 四、C 类（可补项）结果

- **handleDeterminePattern 由 27 缺 → 13 缺（覆盖 14 支）**：以 24 张 crafted 命盘驱动，命中机月同梁、杀破狼、财荫夹印(rel=1/11)、荫印拱身、金灿光辉、坐贵向贵、贪火相逢、巨机居卯、羊刃入庙、兼文武、禄逢两杀、马落空亡、明珠出海三支、生年干解析异常兜底等真实可达判据；另以「无身宫」盘覆盖 `shaCntShen` 三元假侧、以畸形/缺失/空白 SOLAR 覆盖生年干解析的复合假侧与 catch。
- **isJinYuFuJia**：新增两侧支真值与三处非法早返回 + 边界用例，覆盖 +1；余 2 为恒真冗余守卫（不可达）。
- **palaceStarRelation**：以 14 主星 × 12 支穷举，五类生克关系（比和/宫生星/宫克星/星生宫/星克宫）全覆盖；唯一剩余「→无明显生克」回落行为**值域穷尽·数学不可达**（非缺陷，已加穷尽性铁证测试，见 §八）。
- 结论：C 类「罕见格局组合」**已达/超原估 12–15 支目标（本族合计覆盖 15 支真值分支）**。

## 五、A 类（编排/基础设施）结果

- **离线可补（不依赖真实环境）**：verifyObdaTableCoverage（+3）、loadPalaceClassic（+8）、handlePersistAndReturn（+2）、handleSendAlert SKIP 路径（+4）、extractAlertTypeFromReading（+1），合计 **+18 支**，纯离线、确定性、可复跑。
- **真实环境验证**：全栈在线，`ZiWeiDouShuObdaRealLoadTest` 进程内真连 Ontop/MySQL 驱动 `loadStateFromObda` **实跑通过**：真实载入 `卷二语料库=813 行`、`流曜/十二神/五鼠遁 满额(10/10/10/12/10)`、**同源一致性自检 8 类全部「异0」一致**，证明 A 类真实链路功能正确（`loadStateFromObda` LINE 195/195 全覆盖）。

## 六、剩余未覆盖分支的诚实归类（非业务缺口）

| 方法 | 剩余 | 归类 |
|---|---|---|
| palaceStarRelation | 1 支 + 1 行 | **值域穷尽·数学不可达（非缺陷）**：`se,pe∈{0..4}`，Z₅ 差值 `d=(pe−se)mod5` 五类完备划分，`→无明显生克`(L4780) 与 L4777 的 false 侧永不触达；已加 `palaceStarRelationResidueExhaustive` 铁证测试（5元素×12支穷尽断言无回落，§八） |
| isJinYuFuJia | 2 支 | **恒真冗余守卫**：`sr<=11`/`yr<=11` 因 sr,yr=floorMod(...,12)∈0..11 恒真，其 false 侧不可达 |
| handleDeterminePattern | 13 支 + 2 行 | 2 整行为 `catch/fail` 异常兜底（正常不可达）；其余为复合 `&&` 判据的**短路偏侧**与特定边界组合（如需再补可继续构造更极端真实盘，本轮不为凑数堆砌非真实盘） |
| loadStateFromObda | 38 支（LINE 全覆） | **数据完整性自检分支偏侧**：各表 `size()!=N → 告警回退` 的布尔对侧，属数据形态防御；成功路径真跑已验、同源自检一致 |
| verifyObdaTableCoverage | 3 支 + 2 行 | 复合文件可读判定/嵌套三元边界偏侧（临时文件已覆盖主分支） |
| loadPalaceClassic | 4 支 + 4 行 | 资源缺失 catch / 复合守卫偏侧 |
| handleSendAlert | 6 支 + 4 行 | 触发真 MQ 外发路径（`SENT`/`FAILED_LOGGED`/`mq.destroy`）；RabbitMQ 在线，属环境触发项，可后续补 |
| handlePersistAndReturn | 1 支 + 4 行 | 落库 `catch(Throwable)` 分支（需后端写入抛错） |

> 总判：整类剩余 missed 162 支中，本报告覆盖族已尽数说明为「死支/恒真冗余/数据完整性防御/异常兜底/真 MQ 触发」；**无未解释的业务逻辑缺口**。

## 七、复现命令

```powershell
# 目录：D:\work\Ontology\OntologyMachine\OntologyFrameworkZiWeiDouShu
$env:JAVA_HOME='D:\work\Ontology\jdk-27'
# 三新类联合（含 1 真环境用例，Ontop/MySQL 不在线则自动 skip）
mvn -o -q test "-Dtest=ZiWeiDouShuCClassCraftedCoverageTest,ZiWeiDouShuAClassOfflineCoverageTest,ZiWeiDouShuObdaRealLoadTest"
# 覆盖率 Delta
python ..\HWCodeArtsDir\_cov_delta.py
# 未覆盖行定位（按方法行区间）
python ..\HWCodeArtsDir\_miss_lines.py 2159 2500
```

> 说明：本轮**未改动任何主代码**（仅新增 3 个测试类 + 1 条穷尽性审计用例），类哈希不变，既有 jacoco exec 有效；新增用例以 jacoco 代理 **append** 累积，`report` 随 `test` 阶段重生成。

## 八、业务忠实性专项复核（「死支/不可达是否违背业务」审计）

> 结论：**未发现任何因业务写错而造成的死支/不可达**。全部可归为「值域穷尽 / 恒真冗余 / 复合短路偏侧 / 数据防御 / 异常兜底 / 尚未触发的真实生产路径」，均附证据；**本轮未改动主代码**。

### 8.1 palaceStarRelation L4780「→无明显生克」——值域穷尽，忠实原文，非 bug

- 判定式：五行 `se`(星)、`pe`(宫) ∈ {0木,1火,2土,3金,4水}；依次判 比和(`pe==se`)、宫生星(`se==pe+1`)、宫克星(`se==pe+2`)、星生宫(`pe==se+1`)、星克宫(`pe==se+2`)。
- 对任意 `se≠pe`，令 `d=(pe−se) mod 5 ∈ {1,2,3,4}`：`d=1`→星生宫、`d=2`→星克宫、`d=3`→宫克星、`d=4`→宫生星；`d=0`→比和。**25 组 (se,pe) 全部落入五关系之一，五行理论本身不存在第 6 种「两元素无生克」态。**
- 故 `return base + "→无明显生克"`（L4780）与最后一个 `if (pe==(se+2)%5)` 的 **false 侧（L4777）恒不可达**。这不是程序写错，而是**原始星命理论中「两个有五行之物必有其一生克关系」**——原文（卷二·论星辰生克制化 / 卷一·星垣论）只列「生 / 克 / 受制」，并无「无明显生克」之说，故该行为**防御性冗余返回**，忠实原文，按口径 B 据实保留。
- 已加铁证回归 `ZiWeiDouShuCClassCraftedCoverageTest#palaceStarRelationResidueExhaustive`：木/火/土/金/水各一代表星 × 12 支 = **60 组（覆盖全部 25 组 (se,pe)）**，断言任一组合**均非空且永不含「无明显生克」**、且 5 类关系齐现；测试通过（C 类 4/4 绿）。

### 8.2 isJinYuFuJia `sr<=11`/`yr<=11` —— 恒真冗余，非业务缺陷

`sr,yr = floorMod(...,12) ∈ 0..11`，故 `<=11` 恒真、其 false 侧不可达。夹宫业务逻辑（一后 7..11、一前 1..5）完整且两侧支均已覆盖，**不违背业务**；冗余上限判断仅作可读性，保留。

### 8.3 handleDeterminePattern 13 个未覆盖点 —— 复合短路偏侧/守卫/格式/兜底，非「不可成之格」

- 逐条核对：L2222 杀破狼、L2289 金灿光辉、L2343 贪火相逢、L2349 巨机居卯、L2480–2482 明珠出海三支、L2259 财荫夹印等，其**真值侧（成格）均已由 crafted 盘命中**——说明诸格皆可成立，**不存在「恒 false 不可达格」**；未覆盖的仅是 `&&/||` 的短路另一侧；
- L2259 `liangPos!=null`（天梁恒被安星，null 侧不可达，纯防御）；L2542 三元 `tongYuanBar>0 ? … : …` 的格式兜底侧；L2585–2586 `catch(Exception)+fail` 异常兜底（无异常时不触达）。
- 结论：均为**组合数据形态/异常防御**偏侧，无一为业务规则写死或漏枚举。强求覆盖需人为构造"半个短路"的病态盘（凑数），按口径 B 不做。

### 8.4 A 类剩余（verifyObda 3 / loadPalaceClassic 4 / handleSendAlert 6 / handlePersist 1）

均为**文件可读/资源守卫偏侧**或**真实生产路径尚未触发**（MQ 外发 `SENT/FAILED_LOGGED/destroy`、落库 `catch(Throwable)`）；后者在生产会真实发生，**不是死支**，可在需要时以真 MQ / 抛错桩补测（本轮未纳入，避免联网/时序抖动）。无业务违规。

