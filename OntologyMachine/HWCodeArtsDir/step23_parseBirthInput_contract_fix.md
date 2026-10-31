# step23 · parseBirthInput 生辰契约不一致修复记录

日期：2026-10-30
模块：`OntologyFrameworkSiZhuBaZi`

## 1. 症状

启动推演流程 `SiZhuBaZiProcess` 后，`parseBirthInput` 节点抛：

```
IllegalArgumentException: 无法解析生辰：{calendar=公历, year=1978, month=7, day=4, hourZhi=午, minute=null}
    at SiZhuBaZiOntologyJobWorker.parseDateTime(SiZhuBaZiOntologyJobWorker.java:1265)
```

## 2. 根因

前端 `src/main/resources/static/index.html:234` 将 `birth` 作为**结构化对象**
`{calendar, year, month, day, hourZhi, minute}` 提交；
`web/SiZhuBaZiController.java:33,53` 原样透传进流程变量；
`SiZhuBaZiOntologyJobWorker.handleParseBirthInput`（L188）用 `str(vars.get("birth"))` 得到
`Map.toString()`，再交给只识别数字 `yyyy-MM-dd HH:mm` 的 `parseDateTime` → 解析失败。

**即前端与后端对 `birth` 的契约不一致**：设计（测试设计 L112 / 流程设计 L233）规定 `birth` 为字符串，
前端实际提交为对象。`partnerBirth`（合婚）同病（L948）。

## 3. 方案（客户裁定）

- **后端容错**（前端不动）：`handleParseBirthInput` / `handleMarriageMatch` 同时支持
  字符串与结构化对象；向后兼容，测试（字符串）仍通过。
- **子时口径**：前端选「子」映射为**晚子 23:xx**（沿用前端文案「子初换日 23:00」，客户裁定 ④）。

## 4. 改动

`src/main/java/.../SiZhuBaZiOntologyJobWorker.java`

- 新增常量 `ZHI_START_HOUR = {23,1,3,5,7,9,11,13,15,17,19,21}`（时辰地支→起始小时；子=23 晚子）。
- 新增协助方法 `resolveBirthDateTime(Object)`、`birthCalendar(Object)`、`zhiToStartHour(String)`、`toInt(Object)`。
- `handleParseBirthInput`：读原始 `birth` 变量，`calendar` 缺省时回退取 `birth` 对象内 `calendar`；
  改调 `resolveBirthDateTime`。
- `handleMarriageMatch`：`partnerBirth` 改调 `resolveBirthDateTime`。
- 缺 `birth` / 缺 `partnerBirth` 仍抛原异常（N-1/N-2 语义不变）；无法识别的地支抛错（不臆造）。

## 5. 验证

- `mvn -o -f OntologyFrameworkSiZhuBaZi/pom.xml test`
  - 离线：本体合规 8/8、SKOS 8/8、新增 `SiZhuBaZiBirthParsingTest` 4/4 —— 全绿。
  - 新增 `SiZhuBaZiBirthParsingTest` 覆盖报错原样入参 `{1978,7,4,午,minute=null}` → `1978-07-04 11:00`。
  - 新增内存引擎 E2E `B-4`（结构化≈字符串同柱）、`B-5`（结构化「子」≈字符串 23:30 同柱）—— 单跑 2/2 通过。
- 说明：`SiZhuBaZiBpmnInMemoryTest` 的 N-1/N-2 负向用例与真实引擎用例存在环境/时序抖动
  （需 RabbitMQ + Camunda；本机 5672 未监听），失败与本次改动无关，改动前即如此。

## 6. 未决 / 备注

- 前端仍以对象提交 `birth`；若后续统一为字符串，可删本容错分支。
- 真太阳时校正、农历转换（`lunarToSolar` 仍返回 null，标【待考】）等口径未动。
- 禁区模块（OpenlletResolver/OntologyResolver/OntopOBDAHandler）零改动。
