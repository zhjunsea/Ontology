# Openllet 推理器调优说明

> 适用组件：`OntologyMachine/OpenlletResolver` 的 `com.ocean.openlletresolver.OpenlletTuning`
> 文档版本：2026-09-21 ｜ 实测环境：本机（JDK 26 + Openllet 2.6.5 + 本工程 TBox 7463 公理）
>
> ⚠️ **路径变更（2026-09-22，铁律 66）**：`_harness/` 已迁移到
> `WorkBuddyDir/OntologyMachine/_harness/`。本文档中所有 `OntologyMachine/_harness/...` 与 `_harness/...`
> 均指新位置。

---

## 1. 一句话概述

`OpenlletTuning` 是 Openllet 推理器的**全局调优配置点**。它只做一件事：在**第一个推理器创建之前**
调用 `OpenlletOptions.setOptions(Properties)`，把若干 Openllet 优化开关设成指定值。
设置一次、全局生效（同一 JVM 内之后创建的所有推理器都继承），幂等可重复调用。

**默认已调好**：内置默认值就是本工程实测最优的组合，**不改任何配置即可获得稳定启动**。
本文档面向「需要改参数 / 排查 / 换环境」的场景。

---

## 2. 为什么需要调优

### 2.1 现象

同一本体、同一代码、同一输入，`ReasonerService` 的 `precomputeInferences`（分类）耗时在
**秒级到小时级之间随机波动**，偶发彻底挂起 —— 表现为 **Worker 启动卡死、永远起不来**。
不是死循环，不是内存问题，也不是本体不一致。

### 2.2 根因

1. Openllet 对非 EL 本体统一使用 `openllet.core.taxonomy.CDOptimizedTaxonomyBuilder` 做分类。
2. 该类 `reset()` 里有一个开关：

   ```java
   _useCD = OpenlletOptions.USE_CD_CLASSIFICATION
            && !_kb.getTBox().unfold(ATermUtils.TOP).hasNext()
            && !_kb.getExpressivity().hasNominal();
   ```

3. 当本体**不含名义量**（`owl:hasValue` 不进入 TBox）时，`_useCD` 变为 `true`，
   启用 **CD 优化分类路径**。
4. 该路径对「含大量 `owl:equivalentClass` 充要定义」的本体呈**强非确定性耗时**
   （tableau 展开顺序敏感）。本工程 TBox 有约 **366 条** `owl:equivalentClass`。

### 2.3 对策

关闭 CD 优化分类器（回退到通用分类算法）→ 耗时稳定、不再挂起。

> **语义安全性**：`USE_CD_CLASSIFICATION` 与 `USE_ADVANCED_CACHING` 都是**优化开关**，
> 不改变推理语义。关闭 CD 只是换回通用分类算法；关闭高级缓存只是不做模型缓存。
> 上线前请用使用方自己的全量回归确认结果不变（本工程 279/279 通过）。

---

## 3. 快速上手

### 3.1 什么都不做（推荐）

默认值即最优，无需任何配置。

### 3.2 改参数

三种方式，任选其一（优先级见第 4 节）：

| 方式 | 操作 | 适用 |
|---|---|---|
| **应用参数** | 改 `OntologyFramework/src/main/resources/application.yml` 的 `openllet.tuning.*` | 本应用常规调参 |
| **配置文件** | 改 classpath 资源 `openllet-tuning.properties` | 随包发布、多环境共用 |
| **系统属性** | 启动加 `-Dopenllet.tuning.USE_CD_CLASSIFICATION=true` | 临时回退 / 排障 |

> ⚠️ **改完 `src/main/resources/*` 必须手动同步到 `target/classes/`**
> （`_harness/build.sh` 只跑 `javac`，**不拷资源**）：
> ```bash
> cp OntologyFramework/src/main/resources/application.yml \
>    OntologyFramework/target/classes/application.yml
> cp OntologyFramework/src/main/resources/openllet-tuning.properties \
>    OntologyFramework/target/classes/openllet-tuning.properties
> ```

### 3.3 本应用里的调用点

单点调用，位于 `TCMOntologyJobWorker.init()`，在 `BackendService.getInstance(...)` **之前**：

```java
Properties openlletOverrides = new Properties();
if (openlletUseCdClassification != null && !openlletUseCdClassification.isBlank()) {
    openlletOverrides.setProperty("USE_CD_CLASSIFICATION", openlletUseCdClassification.trim());
}
if (openlletUseAdvancedCaching != null && !openlletUseAdvancedCaching.isBlank()) {
    openlletOverrides.setProperty("USE_ADVANCED_CACHING", openlletUseAdvancedCaching.trim());
}
OpenlletTuning.apply(openlletOverrides);
```

---

## 4. 三级配置源与优先级

**后者覆盖前者**：

| 级别 | 来源 | 位置 | 说明 |
|---|---|---|---|
| 1 | **内置默认** | `OpenlletTuning` 的 `DEFAULT_*` 常量 | 代码兜底，永远存在 |
| 2 | **配置文件** | classpath `openllet-tuning.properties` | 可选；不存在则跳过该级 |
| 3 | **应用参数** | `application.yml` 的 `openllet.tuning.*`（由调用方注入） | 留空则该级不生效 |
| 3+ | **系统属性** | `-Dopenllet.tuning.<选项名>=<值>` | **最高优先级**，运维临时覆盖 |

### 关键规则

- **留空即跳过**：某一级的值是 `null` 或空串时，该级不生效，自动回落到上一级。
  例如 `application.yml` 里写 `use-cd-classification:`（空值），则用第 2 级的配置文件值。
- **键名大小写敏感**：必须与 `OpenlletOptions` 字段名完全一致（全大写下划线），
  如 `USE_CD_CLASSIFICATION`。`application.yml` 里用 kebab-case（`use-cd-classification`），
  由调用方翻译成大写下划线键。
- **只认白名单键**：`OpenlletTuning` 只处理 `KEYS` 数组里列出的选项，其它键会被忽略。

---

## 5. 可调选项清单

| 键名（Openllet 选项） | Openllet 默认 | 本工程默认 | 作用 | 取值 | 风险 |
|---|---|---|---|---|---|
| `USE_CD_CLASSIFICATION` | `true` | **`false`** | 是否启用 CD 优化分类器。关闭 = 回退通用分类算法 | `true` / `false` | 设为 `true` 可能恢复非确定性耗时/挂起 |
| `USE_ADVANCED_CACHING` | `true` | **`false`** | 是否启用高级（模型）缓存。关闭 = 不做模型缓存 | `true` / `false` | 设为 `true` 与 CD 叠加时耗时方差变大 |

### 扩展新选项

如需把别的 Openllet 选项也纳入配置点，改 `OpenlletTuning` 的 `KEYS` 数组与 `DEFAULT_*` 常量即可：

```java
private static final String KEY_USE_SMART_RESTORE = "USE_SMART_RESTORE";
private static final String[] KEYS = {
    KEY_USE_CD_CLASSIFICATION, KEY_USE_ADVANCED_CACHING, KEY_USE_SMART_RESTORE
};
private static final String DEFAULT_USE_SMART_RESTORE = "true";
```

> 注意：`OpenlletTuning` 位于 `OpenlletResolver` 工程（铁律 51 禁区）。
> 修改它需要**用户的专门指示**，默认不得改动。

---

## 6. 常见场景配方

| 场景 | 配方 |
|---|---|
| **默认（推荐）** | `USE_CD_CLASSIFICATION=false` + `USE_ADVANCED_CACHING=false` |
| **只想关 CD、保留缓存** | `USE_CD_CLASSIFICATION=false`，`USE_ADVANCED_CACHING=true` |
| **临时回退到 Openllet 原版行为** | 启动加 `-Dopenllet.tuning.USE_CD_CLASSIFICATION=true -Dopenllet.tuning.USE_ADVANCED_CACHING=true` |
| **换到别的本体后想重测** | 先回退到 `true/true` 跑基线，再逐项关闭做 A/B（见第 8 节方法） |
| **怀疑是调优导致结果变化** | 用系统属性回退，对比回归结果 |

---

## 7. 如何验证

### 7.1 看启动日志

调优生效时，日志会打印三级链路与最终值：

```
[OpenlletTuning] 配置文件 openllet-tuning.properties 覆盖 USE_CD_CLASSIFICATION=false
[OpenlletTuning] 应用参数 覆盖 USE_ADVANCED_CACHING=false
[OpenlletTuning] 已应用 Openllet 选项: USE_CD_CLASSIFICATION=false USE_ADVANCED_CACHING=false
                 (USE_CACHING=true USE_SMART_RESTORE=true ORDERED_CLASSIFICATION=ENABLED)
```

**若完全看不到 `[OpenlletTuning]` 日志** → 说明 `apply()` 没被调用，或调用点在推理器创建之后。
**若只看到「已应用」而没有「配置文件/应用参数 覆盖」** → 说明第 2、3 级没生效
（最常见原因：资源文件没同步到 `target/classes/`）。

### 7.2 看分类耗时

```
[ReasonerService] TBox 层 precompute 完成，耗时 15816 ms
Started TCMApplication in 25.62 seconds
```

健康区间：`precompute` **12–36s**、`Started` **≤ 30s**。
若 `precompute` 超过 60s 且反复波动 → 调优未生效或本体已变。

### 7.3 跑全量回归（正确性）

```bash
cd /d/work/Ontology/TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox/WorkBuddyDir/OntologyMachine/_harness
bash run_suite.sh
# 期望：合计 279 / 279 通过，SUITE_EXIT=0
```

> 前置条件：Camunda(9080/26500) + Ontop(8080) + Worker(9081) 均已启动，
> 且 **Ontop 必须先于 Worker 启动**，否则十八反/方剂查询会失败。

---

## 8. 实测数据（选型依据）

方法（铁律 54：单次测量不可作结论）：同机、背靠背、**交错取样** A→B→C→A→B→C…共 4 轮，
取中位数与最大值。日志：`_harness/_sweep_ab.log`。

| 配置 | precompute 4 次（s） | 中位数 | 最大 |
|---|---|---|---|
| A 默认（CD=on, 缓存=on） | 94.4 / 17.1 / 86.7 / 82.7 | 84.7 | 94.4 |
| B 仅关 CD | 53.0 / 19.2 / 14.5 / 35.4 | 27.3 | 53.0 |
| **C 关 CD + 关高级缓存（本工程默认）** | 14.1 / 19.9 / 35.7 / 17.4 | **18.7** | **35.7** |

**结论**：C 的中位数最低、方差最小 → 选为默认。

### 复现方法

```bash
cd /d/work/Ontology/TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox/WorkBuddyDir/OntologyMachine/_harness
ROUNDS=4 TMO=120 bash sweep_ab.sh > _sweep_ab.log 2>&1
```

探针 `PrecomputeProbe3` 支持任意选项组合：

```bash
bash run_pc3.sh "USE_CD_CLASSIFICATION=false;USE_ADVANCED_CACHING=false"
```

---

## 9. 回退与排障

| 现象 | 排查 |
|---|---|
| 启动仍偶发挂起 | ① 确认日志有 `[OpenlletTuning] 已应用 … false false`；② 确认资源已同步到 `target/classes/`；③ 确认没有别处提前创建了推理器（调优必须在第一个推理器之前） |
| 改了参数但没生效 | 检查是否被更高优先级覆盖（系统属性 > 应用参数 > 配置文件 > 内置默认）；检查键名拼写 |
| 回归结果与调优前不一致 | 用 `-Dopenllet.tuning.*=true` 回退后重跑，确认是否为调优引入；若是，说明该本体确实依赖 CD 路径的某种行为，需另行分析 |
| 想彻底停用调优 | 启动加 `-Dopenllet.tuning.USE_CD_CLASSIFICATION=true -Dopenllet.tuning.USE_ADVANCED_CACHING=true` |

---

## 10. 适用范围与限制

- **类**位于 `OpenlletResolver`（通用，与业务本体无关），任何依赖该工程的 app 都可复用。
- **效果是 JVM 全局**：`OpenlletOptions` 是库级静态配置，设置一次，该 JVM 内所有 Openllet 推理器都继承。
- **调用需要显式**：类本身不会自动执行。使用方必须在**第一个推理器创建之前**调用一次
  `OpenlletTuning.apply(...)`。本工程在 `TCMOntologyJobWorker.init()` 里调用。
- **未覆盖**：不走该调用点的入口（如 `PizzaApplication` 的 `@SpringBootTest`、独立探针）不会自动继承。
- **这是库级配置层的规避，不是本体层根治**。本体层根因是「大量 `equivalentClass` × 个体」的相互作用，
  受工程约束未做改动。
- **铁律 51**：`OpenlletTuning` 位于禁区工程 `OpenlletResolver`。按用户 2026-09-21 澄清，
  **禁区只在用户专门指示时才能打破，否则严格执行**。修改本类前须先报改动清单并获确认。

---

## 11. 相关文件

| 文件 | 说明 |
|---|---|
| `OntologyMachine/OpenlletResolver/src/main/java/com/ocean/openlletresolver/OpenlletTuning.java` | 调优配置点（通用类） |
| `OntologyMachine/OntologyFramework/src/main/resources/openllet-tuning.properties` | 第 2 级配置源 |
| `OntologyMachine/OntologyFramework/src/main/resources/application.yml` | 第 3 级配置源（`openllet.tuning.*`） |
| `OntologyMachine/OntologyFramework/src/main/java/com/ocean/ontologyframework/TCMOntologyJobWorker.java` | 调用点（`init()`） |
| `OntologyMachine/_harness/sweep_ab.sh` | 交错取样脚本 |
| `OntologyMachine/_harness/run_pc3.sh` | 任意选项组合的离线探针 |
| `OntologyMachine/_harness/_sweep_ab.log` | 选型实测日志 |
