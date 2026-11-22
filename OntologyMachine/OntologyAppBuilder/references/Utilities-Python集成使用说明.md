# 本体应用 Python 集成 · 使用说明

> 面向读者：需要让 **Python 程序调用「本体应用」**（Python 给输入 → 本体应用执行 → 回传结果 → Python 继续）的开发者。
> 交付模块：`com.ocean:Utilities`（通用能力，与业务无关）。
> 示例载体：**本体应用框架示例 Example**（`OntologyFrameworkExample`，端口 `9083`，流程 ID `PizzaMakingStandardProcess`）。
> 相关文档：模块 API 见 `Reference/Utilities-API.md`（§16 Python 集成）；应用侧见 `OntologyFrameworkExample/design/Example应用-应用架构设计.md`（§10）；本文为其**操作级使用说明**。

---

## 1. 能力概述

本体应用本身就是一个 Spring Boot 服务。`Utilities` 模块提供了一层**与业务无关**的通用调用能力，使任何本体应用都能被外部程序以 **HTTP REST + 同步阻塞**语义集成：

```
   Python 程序                    本体应用（Spring Boot）
 ┌──────────────┐   POST /invoke  ┌────────────────────────────┐
 │ 组装 inputs  │ ──────────────► │ OntologyInvocationController│
 │              │                 │   └─ OntologyInvocationSupport│
 │              │                 │        └─ ProcessOrchestrator │
 │              │                 │             └─ Camunda 流程   │
 │ 拿到 outputs │ ◄────────────── │  （跑完 → 回收流程变量）     │
 │ 继续执行     │  InvokeResult   └────────────────────────────┘
 └──────────────┘
```

- **零侵入**：不修改任何业务应用代码。各应用均以 `@ComponentScan("com.ocean")` 扫描 `Utilities`，控制器自动装配。
- **零依赖客户端**：`Utilities/python/ontology_client.py` 仅用 Python 标准库（`urllib`/`json`/`time`），可直接拷贝进项目。
- **默认启用**：`/api/ontology/*` 端点随应用启动即生效；不需要时可用配置关闭。
- **业务无关**：流程 ID、输入变量、输出变量全部由调用方透传，`Utilities` 不感知任何领域语义。

> **以 Example 为例**：Example 是一个「披萨制作与烘焙」本体应用，Python 传入披萨类型 IRI，流程按本体取组件、按标签匹配分支走完烘焙链，回传所选组件（名称/价格/匹配标签）等结果，Python 再继续处理。

---

## 2. 前置条件与启动

### 2.1 环境要求

| 组件 | 说明 |
| --- | --- |
| JDK / Maven | 与 `OntologyMachine` 工程一致（Java 26 / Spring Boot 4.1.0） |
| Camunda 8（Zeebe 引擎） | 本体流程的编排引擎，必须先启动（gRPC `26500` / REST `9080`） |
| MySQL | Example 运行态数据（库 `mypizzadb`，`3306`） |
| Ontop | SPARQL 虚拟端点（`8080`） |
| RabbitMQ | 低库存告警外发（`5672` / `15672`） |
| 本体应用 | `OntologyFrameworkExample`，本示例端口 `9083` |

> 与其他轻量应用不同，**Example 需完整环境**（Camunda + MySQL + Ontop + RabbitMQ）；一键备环境见 §2.3。

### 2.2 构建（铁律 1：必须带 `-am`）

```bash
# 在 OntologyMachine 工程根目录
./mvnw -pl OntologyFrameworkExample -am package -DskipTests
# -am = also make 上游依赖，连带编译三个基础库（OpenlletResolver / OntopOBDAHandler / Utilities）
```

产物：`OntologyFrameworkExample/target/OntologyFrameworkExample-0.0.1-SNAPSHOT.jar`（可执行 fat jar）。

### 2.3 启动环境与应用

```bash
# ① 一键备环境：模块根双击 start-env.cmd（内部 EnvPrepare start，按 application.yaml 拉起
#    Camunda / MySQL / Ontop / RabbitMQ；未声明者自动 [SKIP]；交互询问是否 init 重建）
# ② 启动本体应用（显式指定端口，优先级高于环境变量）
java --enable-native-access=ALL-UNNAMED --sun-misc-unsafe-memory-access=allow \
     -jar OntologyFrameworkExample/target/OntologyFrameworkExample-0.0.1-SNAPSHOT.jar --server.port=9083
```

> ⚠️ **端口注入提示**：在部分托管 / CI 环境中，宿主会向进程注入 `PORT` / `SERVER_PORT` 环境变量，Spring Boot 会将其读取为 `server.port` 并**覆盖** `application.yaml`。此时请始终在命令行显式传 `--server.port=9083`（命令行参数优先级最高）。

### 2.4 自检（集成前必做）

```bash
curl http://localhost:9083/api/ontology/health
# 期望：{"component":"OntologyInvocationController","engineAvailable":true}

curl http://localhost:9083/api/example/health
# 期望：{"engineAvailable":true,"processId":"PizzaMakingStandardProcess","bpmnPath":"ontology/bpmn/..."}
```

`engineAvailable` 为 `true` 表示 `CamundaClient` 已装配、引擎可达，可开始调用。

---

## 3. 五分钟快速上手

把 `Utilities/python/ontology_client.py` 拷到你的工程，即可用：

```python
from ontology_client import OntologyClient

client = OntologyClient("http://localhost:9083")     # Example 根地址
assert client.health()["engineAvailable"], "引擎未就绪"

# Python 给出输入 → 同步调用本体流程 → 一次拿到结果
result = client.invoke(
    "PizzaMakingStandardProcess",                   # 流程 ID（必填）
    {"pizzaType": "http://example.org/pizza/core/MargheritaPizza"},   # 输入变量（透传）
    timeout_millis=300_000,                          # 最长等待（默认 5 分钟）
    result_keys=["componentName", "componentPrice", "matchedWord", "propertyValues"],
)

if result["success"]:
    print("本体输出：", result["outputs"])            # ← Python 拿到结果继续执行
else:
    print("未完成：", result["status"], result["message"])
```

以上即完整闭环：**Python 输入 → 本体应用执行 → 结果回传 → Python 继续**。

---

## 4. REST 端点参考

基路径默认 `/api/ontology`（可用 `ontology.invoke.base-path` 覆盖）。以 Example 为例即 `http://localhost:9083/api/ontology`。

| 方法 | 路径 | 请求体 / 参数 | 返回 | 用途 |
| --- | --- | --- | --- | --- |
| `GET` | `/health` | — | `{component, engineAvailable}` | 引擎就绪探测 |
| `POST` | `/invoke` | `InvokeRequest` JSON | `InvokeResult` JSON | **同步**：提交并等待终态，一次拿到结果 |
| `POST` | `/invoke-async` | `InvokeRequest` JSON | `{processInstanceKey, processId, status:"STARTED"}` | **异步**：仅启动流程 |
| `GET` | `/invoke/{key}` | query `resultKeys`（可重复） | `InvokeResult` 快照 | **查询**：不等待，取当前状态与已产生输出 |

**原始 HTTP 示例（同步调用）：**

```bash
curl -X POST http://localhost:9083/api/ontology/invoke \
  -H "Content-Type: application/json" \
  -d '{
        "processId": "PizzaMakingStandardProcess",
        "inputs": {"pizzaType": "http://example.org/pizza/core/MargheritaPizza"},
        "timeoutMillis": 300000,
        "resultKeys": ["componentName", "componentPrice", "matchedWord"]
      }'
```

---

## 5. 数据结构字段参考

### 5.1 请求 `InvokeRequest`

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `processId` | string | ✅ | Camunda BPMN 流程定义 ID（如 `PizzaMakingStandardProcess`） |
| `inputs` | object | 否 | 输入变量，**原样透传**给流程实例（即 Python 给出的输入） |
| `timeoutMillis` | long | 否 | 同步等待终态的最长时长；缺省 `300000`（5 分钟） |
| `pollIntervalMillis` | long | 否 | 轮询终态的间隔；缺省 `500`，最小 `50` |
| `bpmnPaths` | string[] | 否 | 可选，调用前对本地 BPMN 文件做**幂等部署** |
| `resultKeys` | string[] | 否 | 输出变量白名单；为空则回传**全部**流程变量 |

> JSON 直传即形如：`{"processId":"...","inputs":{...},"timeoutMillis":300000,"resultKeys":[...]}`。

### 5.2 结果 `InvokeResult`

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `success` | boolean | 仅当 `status == "COMPLETED"` 时为 `true` |
| `status` | string | 统一状态：`COMPLETED` / `INCIDENT` / `TERMINATED` / `RUNNING` / `TIMEOUT` / `ERROR` |
| `processId` | string | 流程定义 ID |
| `processInstanceKey` | long \| null | 流程实例 key（启动失败时可能为 `null`） |
| `outputs` | object | **本体程序给出的结果**（流程结束变量，可由 `resultKeys` 裁剪） |
| `elapsedMillis` | long | 从提交到返回的耗时（毫秒） |
| `message` | string | 人类可读说明（成功/失败/超时原因） |

Python 客户端返回的字典即上述 JSON，并额外附带 `_httpStatus`（HTTP 状态码）。

---

## 6. 状态语义与处置

| `status` | 含义 | HTTP | Python 侧建议处置 |
| --- | --- | --- | --- |
| `COMPLETED` | 流程正常完成 | `200` | `success=True`，直接消费 `outputs` |
| `TIMEOUT` | 等待超时（流程仍在跑，返回当时输出快照） | `504` | 携带 `processInstanceKey` 改用 `query`/`wait` 继续轮询 |
| `INCIDENT` | 作业失败等导致流程中断 | `409` | 读 `outputs` 中的提示变量，或查 `message` 定位 |
| `TERMINATED` | 流程被终止 | `202` | 按需重试 |
| `RUNNING` | 仍在运行（查询快照时可能出现） | `202` | 继续轮询 |
| `ERROR` | 调用侧错误（未就绪 / `processId` 空 / 启动失败） | `503` | 检查 `engineAvailable`、`processId`、引擎连通性 |

> **关键约定**：`TIMEOUT` / `INCIDENT` 属**正常业务结果**，客户端**不抛异常**，由调用方检查 `status` 处理；仅**传输层失败**（连不上、超时、响应非 JSON）才抛 `OntologyInvokeError`。

---

## 7. Example 应用契约（示例）

### 7.1 输入变量（`inputs`）

| 变量 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `pizzaType` | string | ✅ | 披萨类型 IRI（见下表） |

| 披萨类型 | IRI |
| --- | --- |
| ItalianStyleWhiteSeafoodPizza | `http://example.org/pizza/core/ItalianStyleWhiteSeafoodPizza` |
| MargheritaPizza | `http://example.org/pizza/core/MargheritaPizza` |
| VegetarianPizza | `http://example.org/pizza/core/VegetarianPizza` |

> 其余流程变量（如 `individualIri` / `propertyIri` / `targetProperty` / `candidateLabels`）可视需要一并透传；仅传 `pizzaType` 即可跑通烘焙主链。

### 7.2 输出变量（`outputs`，常用子集）

| 变量 | 说明 |
| --- | --- |
| `componentName` | 选中的组件实例名（`get-component` 产出；如「没有该组件」为降级） |
| `componentPrice` | 选中组件价格（最低价，`get-component` 产出） |
| `matchedWord` | 标签匹配结果（用于网关分支，`get-component` 产出） |
| `propertyValues` | 本体属性值列表（`query-property-value-ontology` 产出） |

> Example 实际 Worker 为 2 个（`get-component`、`query-property-value-ontology`）；`pizzaSummary` / `myPizzaInstanceIri` 等变量**当前不在 Example 内产出**（仅 `OntologyFrameworkPizza` 有），故查询结果中它们可能为空串。建议在 `resultKeys` 中只声明上表已产出的输出。

---

## 8. 完整示例

### 8.1 同步调用（首选）

```python
from ontology_client import OntologyClient, OntologyInvokeError

client = OntologyClient("http://localhost:9083", timeout=600.0)  # 网络超时 > 业务 timeoutMillis

try:
    r = client.invoke(
        "PizzaMakingStandardProcess",
        {"pizzaType": "http://example.org/pizza/core/MargheritaPizza"},
        timeout_millis=300_000,
        result_keys=["componentName", "componentPrice", "matchedWord", "propertyValues"],
    )
except OntologyInvokeError as e:
    raise SystemExit(f"调用失败：{e} | http_status={e.http_status}")

if r["success"]:
    outs = r["outputs"]
    print("选中组件：", outs.get("componentName"), "价格：", outs.get("componentPrice"))
    print("匹配标签：", outs.get("matchedWord"))
    # …… Python 拿到结果继续后续业务 ……
else:
    print("未完成：", r["status"], r["message"])
```

### 8.2 异步调用 + 客户端轮询

```python
key = client.invoke_async("PizzaMakingStandardProcess",
                          {"pizzaType": "http://example.org/pizza/core/VegetarianPizza"})
print("已启动，processInstanceKey =", key)

r = client.wait(key, timeout_millis=300_000, poll_interval_millis=1000)  # 轮询到终态或超时
print(r["status"], r["outputs"])
```

### 8.3 仅查询快照

```python
snap = client.query(key, result_keys=["componentName"])
print("当前状态：", snap["status"])   # COMPLETED / RUNNING / ...
```

---

## 9. 端到端验证复现

验证脚本：`OntologyFrameworkPythonClient/verify_example.py`（模块自带客户端 `ontology_client.py`，镜像 `Utilities/python/ontology_client.py`）。
它完整走通「Python 输入 → Example 本体应用执行 → 结果回传 → Python 继续」。

> `OntologyFrameworkPythonClient/` 是独立的 Python 客户端模块（IntelliJ Python 模块，含 `.venv`），
> 按应用各放一个调用/验证脚本：`verify_example.py`（Example）、`verify_latticetower.py`（LatticeTower），
> 另有 `latticetower_client.py`（LatticeTower 触发设计 + 结果文件下载，见 §9.4）。
> 可用模块自带解释器运行：`OntologyFrameworkPythonClient/.venv/Scripts/python.exe verify_example.py`。

### 9.1 复现步骤

```bash
# 1) 启动环境（Camunda + MySQL + Ontop + RabbitMQ）—— 见 §2.3
# 2) 启动 Example 应用，端口 9083
java --enable-native-access=ALL-UNNAMED --sun-misc-unsafe-memory-access=allow \
     -jar OntologyFrameworkExample/target/OntologyFrameworkExample-0.0.1-SNAPSHOT.jar --server.port=9083
# 3) 运行验证脚本（默认地址 http://localhost:9083）
python OntologyFrameworkPythonClient/verify_example.py
```

### 9.2 脚本行为

```
[1] 健康探测 http://localhost:9083/api/ontology/health  → 断言 component=OntologyInvocationController、engineAvailable=true
[2] Python 提交输入 {"pizzaType": ".../MargheritaPizza"} 并同步调用 PizzaMakingStandardProcess
[3] 打印 status / success / elapsed / key / outputs 键 / componentName / matchedWord
[4] Python 拿到结果继续执行（读取 componentName / matchedWord）
✅ 端到端验证通过：Python 输入 → Example 本体应用执行 → 结果回传 → Python 继续。
```

> **前置**：Camunda + MySQL + Ontop + RabbitMQ 已就绪，且 `ExampleApplication`（Profile=`ExampleBPMN`）已在 9083 启动。缺任一服务时，流程无法完成（`status` 非 `COMPLETED`）。

### 9.3 实测结论（2026-11-02）

已在本机对完整环境实跑通过，**Python 客户端与原始 HTTP 双通道均成功**：

```
# Python 客户端
$ python verify_example.py
status=COMPLETED  success=True  key=2251799818155940
outputs = {propertyValues, componentName=BuffaloMozzarellaInstance, componentPrice=15.0, matchedWord=封层}
✅ 端到端验证通过

# 原始 HTTP（独立复核）
POST /api/ontology/invoke  {"processId":"PizzaMakingStandardProcess",
  "inputs":{"pizzaType":".../VegetarianPizza"}, ...}
→ {"success":true,"status":"COMPLETED","outputs":{"componentName":"ProvelCheeseInstance",
   "componentPrice":9.0,"matchedWord":"封层"},"elapsedMillis":2079}
```

- 通用端点随应用启动即生效，**Example 零业务代码改动**即可被 Python 集成。
- 详见 `HWCodeArtsDir/example-verify/VERIFICATION-2026-11-02.md`。

### 9.4 结果文件（ZIP）下载：通用端点只回传路径，文件本体由应用端点提供

**关键认知**：通用端点 `/api/ontology/invoke` 回传的是**流程变量**，其中「产物路径」类变量（如 `resultXlsxPaths`）是服务端产出文件的**本地路径**，**不是文件字节**。要拿到文件本体，须再调**应用侧的文件导出端点**——这是「通用能力（业务无关，只认流程变量）」与「应用能力（领域产物打包）」的分层。

以 **LatticeTower** 为例：

| 端点 | 返回 | 说明 |
| --- | --- | --- |
| `GET /api/latticetower/design/{key}/export-xlsx` | `application/zip` | 本次运行**全部 Excel**（节点 7 逐有解底宽几何 XLSX `resultXlsxPaths` + 节点 3'' 逐放宽底宽 XLSX `relaxResultXlsxPaths`）打包；无任何可下载文件时 `404` |
| `GET /api/latticetower/design/{key}/export?format=md\|csv` | `text/markdown` / `text/csv` | 结果报告；优先读落盘文件，缺文件回退流程变量 `designResultSummary` |

Python 侧两步走（`latticetower_client.py` 已在通用客户端之上封装）：

```python
from latticetower_client import LatticeTowerClient

cli = LatticeTowerClient("http://localhost:9086")           # 默认即此地址
res = cli.design(100000, dis_bott_min=25, dis_bott_max=25, facade_type="斜直线型")
key = res["processInstanceKey"]                             # 第 1 步：触发并拿 key
cli.download_excel_zip(key, "out/latticetower-excel.zip")   # 第 2 步：按 key 下载 ZIP 字节
```

**实测结论（2026-11-02，`latticetower_client.py`）**：

```
[2] 触发设计流程 LatticeTowerDesignProcess
    status=COMPLETED | success=True | key=2251799818159322
    resultXlsxPaths = ['...\logs\latticetower\20261102-162917\latticetower-result-...-geo-bw25.xlsx']
[3] 下载 Excel 结果压缩包（ZIP）
    -> out\latticetower-excel-2251799818159322.zip  (12526 bytes)
    ZIP 条目: ['latticetower-result-2251799818159322-geo-bw25.xlsx']
[4] 下载 Markdown 报告 -> out\latticetower-result-2251799818159322.md  (839 bytes)
```

- ZIP 完整性校验 `zipfile.testzip()` 返回 `None`（无损坏）。
- **运行前提**：`OntologyFrameworkLatticeTower` 已在 `9086` 启动（该应用仅需 Camunda，无 OBDA/RabbitMQ）。

> ⚠️ **`designResultSummary` 截断坑**：该流程变量在结果较大（如 15+ 个底宽，约 >8191 字符）时**超过引擎变量上限会被截断**，
> 经 `/invoke` 读回只剩**首尾引号 + 字面 `\n`**（无法 `splitlines()`）。**完整报告须从落盘 `resultMdPath` 或下载端点取**——
> `verify_latticetower.py` 即「先下载 MD 文件、再从文件打印」，规避截断。

---

## 10. 常见问题与注意事项

1. **`engineAvailable=false`**：说明应用的 `CamundaClient` 未装配（或引擎不可达）。检查 Camunda 是否已在 `26500/9080` 启动、应用 `application.yaml` 的 `camunda.client` 配置是否匹配。即使引擎不可达，端点仍可访问，但调用会返回 `status=ERROR`，**不影响应用启动**。
2. **Example 需完整环境**：与只用 Camunda 的应用不同，Example 的烘焙链经 `get-component` 走 Ontop→MySQL 取数、低库存告警走 RabbitMQ；**MySQL/Ontop 未就绪会导致取数失败**，RabbitMQ 未就绪会影响 SWRL 外发。
3. **超时分层**：客户端网络 `timeout`（秒）必须**大于**服务端 `timeoutMillis`，否则网络层会先于业务层超时。例：`timeout_millis=300000` ⇒ 客户端 `timeout≥600`。
4. **端口被注入覆盖**：见 §2.3，务必命令行显式 `--server.port=9083`。
5. **`TIMEOUT` 不等于失败**：流程仍在运行，用返回的 `processInstanceKey` 调 `query`/`wait` 继续取结果即可。
6. **`resultKeys` 建议显式声明**：只取已产出的输出（`componentName`/`componentPrice`/`matchedWord`/`propertyValues`），避免空值混淆。
7. **幂等部署**：默认不部署（假定流程已在引擎中）。若流程未部署，可在 `InvokeRequest.bpmnPaths` 传入 BPMN 文件做幂等部署（`deployIfAbsent`）。
8. **进程内直调**：Java 侧亦可直接调用 `OntologyInvocationSupport.invoke(client, request)`，与 REST 通道共用同一门面。

---

## 11. 接入新本体应用清单

对每个需被 Python 调用的本体应用，只需：

1. **依赖 `Utilities`**（各应用已依赖，无需改动）。
2. `application.yaml` 可选配置（默认即启用，可跳过）：
   ```yaml
   ontology:
     invoke:
       enabled: true               # 默认 true；设 false 可关闭通用端点
       base-path: /api/ontology    # 可自定义基路径
   ```
3. 启动该应用所需环境 + 本体应用（见 §2.3）。
4. Python 侧 `OntologyClient` 指向该应用端口，传入其**流程 ID** 与**输入变量**，按需声明 `resultKeys`。
5. 集成前自检：`GET /api/ontology/health` → `engineAvailable: true`。

> 本能力与业务无关，不改变任何流程语义：Python 输入最终作为流程变量注入，输出即流程结束变量（可由 `resultKeys` 裁剪）。

---

## 12. 相关文件清单

| 文件 | 位置 | 说明 |
| --- | --- | --- |
| 调用门面 | `Utilities/src/main/java/com/ocean/utilities/OntologyInvocationSupport.java` | 同步/异步/查询，业务无关 |
| REST 控制器 | `Utilities/src/main/java/com/ocean/utilities/OntologyInvocationController.java` | `/api/ontology/*` 端点 |
| 请求/结果 | `Utilities/src/main/java/com/ocean/utilities/InvokeRequest.java` · `InvokeResult.java` | 数据结构 |
| Python 客户端 | `Utilities/python/ontology_client.py` | 零依赖客户端（可直接复用） |
| Python 客户端模块 | `OntologyFrameworkPythonClient/` | 独立客户端模块（含 `.venv` 与自带 `ontology_client.py`），按应用放调用脚本 |
| 验证脚本 | `OntologyFrameworkPythonClient/verify_example.py` | Example 端到端验证（同模块另有 `verify_latticetower.py`） |
| 结果文件下载示例 | `OntologyFrameworkPythonClient/latticetower_client.py` | 触发设计 + 下载结果 **ZIP**/CSV/MD（见 §9.4） |
| 实测记录 | `HWCodeArtsDir/example-verify/VERIFICATION-2026-11-02.md` | Example 端到端实测结果（通过） |
| 应用架构 | `OntologyFrameworkExample/design/Example应用-应用架构设计.md`（§10） | Example 集成说明 |
| 模块 API 文档 | `Reference/Utilities-API.md`（§16） | 类/方法级参考 |
