# OntologyFrameworkPythonClient

本体应用 **Python 调用客户端**模块：以 Python 程序调用「本体应用」，完成
**Python 给出输入 → 本体应用执行 → 结果回传 → Python 继续** 的闭环。

模块**自带零依赖客户端** `ontology_client.py`（仅用 Python 标准库
`urllib`/`json`/`time`），镜像自 `Utilities/python/ontology_client.py`，可直接拷贝到任意工程使用。
本模块按应用各放一个调用/验证脚本。

## 目录结构

```
OntologyFrameworkPythonClient/
├── .venv/                   # 本地虚拟环境（Python 3.13，已排除出版本控制）
├── ontology_client.py       # 零依赖客户端（镜像 Utilities/python/ontology_client.py）
├── verify_example.py        # Example 应用调用/验证（披萨烘焙流程）
├── verify_latticetower.py   # LatticeTower 应用调用/验证（格构塔设计流程）
├── latticetower_client.py   # LatticeTower 专属客户端：触发设计 + 下载结果文件（ZIP / CSV / MD）
└── README.md
```

## 前置条件

1. 本体应用侧环境就绪：**Camunda 8**（`26500`/`9080`）必选；
   应用若走 OBDA 取数还需 **MySQL** + **Ontop**，若走事件外发还需 **RabbitMQ**。
2. 目标本体应用已启动，并提供通用调用端点 `/api/ontology/*`
   （由 `Utilities#OntologyInvocationController` 提供，**默认启用**）。
3. 运行前用健康端点自检：`GET /api/ontology/health` → `{"engineAvailable": true, ...}`。

## 运行

用模块自带解释器（推荐）：

```bash
# Example：默认 http://localhost:9083
OntologyFrameworkPythonClient/.venv/Scripts/python.exe OntologyFrameworkPythonClient/verify_example.py

# LatticeTower：默认 http://localhost:9086
OntologyFrameworkPythonClient/.venv/Scripts/python.exe OntologyFrameworkPythonClient/verify_latticetower.py
```

> `verify_latticetower.py` 现已包含**结果文件下载**：运行结束会打印 ZIP / MD 的**绝对路径**（默认落 `out/`），
> 并从下载落盘的 MD 文件逐行打印完整报告。
> ⚠️ 不要直接打印流程变量 `designResultSummary`：结果较大（15+ 底宽）时**超过引擎变量上限会被截断**，
> 读回只剩首尾引号与字面 `\n`。**完整报告以落盘 `resultMdPath` / 下载端点为准。**

也可传入自定义应用地址（第 1 个参数），或设置环境变量 `EXAMPLE_BASE_URL`。

带结果文件下载的完整示例（LatticeTower）：

```bash
# 触发设计 → 下载 Excel 结果 ZIP + Markdown 报告（默认 http://localhost:9086）
OntologyFrameworkPythonClient/.venv/Scripts/python.exe OntologyFrameworkPythonClient/latticetower_client.py
```

产物写入 `OntologyFrameworkPythonClient/out/`（`latticetower-excel-<key>.zip`、`latticetower-result-<key>.md`）。

## 客户端

- 客户端 `ontology_client.py` **就在本模块内**，脚本以 `from ontology_client import OntologyClient` 就地导入
  （运行时脚本所在目录自动在 `sys.path` 上，IDE 亦可直接解析）。
- 该文件为 `Utilities/python/ontology_client.py` 的**镜像**（内容一致）；上游更新时同步拷贝即可：
  ```bash
  cp Utilities/python/ontology_client.py OntologyFrameworkPythonClient/ontology_client.py
  ```

## 取回结果文件（ZIP / CSV / MD）

**背景：通用端点 `/api/ontology/invoke` 只回传「流程变量」**（如 `resultXlsxPaths` 是产出 Excel 的**本地路径**），
**不回传文件字节**。要拿到文件本体，需再调**应用侧的文件导出端点**（由应用自己提供）。

LatticeTower 应用提供两类导出端点：

| 端点 | 返回 | 说明 |
| --- | --- | --- |
| `GET /api/latticetower/design/{key}/export-xlsx` | `application/zip` | 本次运行**全部 Excel**（节点 7 逐有解底宽几何 XLSX + 节点 3'' 放宽底宽 XLSX）打包；无文件时 `404` |
| `GET /api/latticetower/design/{key}/export?format=md\|csv` | `text/markdown` / `text/csv` | 结果报告；优先读落盘文件，缺文件回退流程变量 |

`latticetower_client.py` 在通用客户端之上封装了这两步：

```python
from latticetower_client import LatticeTowerClient

cli = LatticeTowerClient("http://localhost:9086")     # 默认即此地址

# 1) 触发设计并同步拿到终态（含 processInstanceKey 与 resultXlsxPaths）
res = cli.design(100000, dis_bott_min=25, dis_bott_max=25, facade_type="斜直线型")
key = res["processInstanceKey"]

# 2) 用 key 取回结果文件本体
n = cli.download_excel_zip(key, "out/latticetower-excel.zip")   # → 返回写入字节数
cli.download_export(key, "out/latticetower-result.md", fmt="md")
```

> 为什么不能一次拿到？这是**通用能力与应用能力的分层**：`Utilities#/api/ontology/*` 业务无关、只认流程变量；
> 文件打包属**领域产物**，由各应用自建端点（见 `Reference/Utilities-Python集成使用说明.md` §9.4）。

## 编写新应用的调用脚本

参照 `verify_example.py`：

```python
from ontology_client import OntologyClient

client = OntologyClient("http://localhost:<端口>")
assert client.health()["engineAvailable"], "引擎未就绪"

result = client.invoke(
    "<流程 ID>",
    {"<输入变量>": "<值>"},
    timeout_millis=300_000,
    result_keys=["<输出键1>", "<输出键2>"],
)
if result["success"]:
    print(result["outputs"])
```

> 说明：`invoke` 返回服务端 `InvokeResult` 的 JSON（`success`/`status`/`outputs`/
> `processInstanceKey`/`elapsedMillis`/`message`）并附带 `_httpStatus`；
> `TIMEOUT`/`INCIDENT` 属正常业务结果不抛异常，仅传输层失败抛 `OntologyInvokeError`。

## 相关文档

- 操作级说明：`Reference/Utilities-Python集成使用说明.md`
- 模块 API：`Reference/Utilities-API.md`（§16 Python 集成）
- Example 端到端实测记录：`HWCodeArtsDir/example-verify/VERIFICATION-2026-11-02.md`
