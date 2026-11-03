# Example 本体应用 · Python 集成端到端验证记录

- **日期**：2026-11-02
- **应用**：`OntologyFrameworkExample`（profile `ExampleBPMN`），端口 `9083`
- **流程**：`PizzaMakingStandardProcess`（`ontology/bpmn/myPizzaBakingProcess.bpmn`）
- **脚本**：`OntologyFrameworkPythonClient/verify_example.py`（正式归属；本次亦已从 `HWCodeArtsDir/example-verify/verify_example.py` 与模块 `.venv` 两处分别实跑通过），复用 `Utilities/python/ontology_client.py`
- **结论**：✅ **通过**——Python 输入 → 本体应用执行 → 结果回传 → Python 继续，双通道（Python 客户端 / 原始 HTTP）均验证成功。

## 1. 环境

| 依赖 | 端口 | 状态 | 说明 |
|---|---|---|---|
| Camunda 8（c8run-8.9.6） | 26500 / 9080 | 复用（已在运行，PID 36100） | EnvPrepare 归属判定为「本环境实例」 |
| MySQL 8.0（服务 MySQL80） | 3306 | 复用（已在运行，PID 16344） | 库 `mypizzadb`：`pizza_components`=70 / `crust_components`=16 / `mypizza`=10 |
| Ontop 5.5.0 | 8080 | 启动成功 | 手工按 EnvPrepare 参数启动（`endpoint`）；SPARQL 探测返回 pizza 数据 |
| RabbitMQ 4.3.4 | 5672 | 启动成功 | `ERLANG_HOME=D:\work\Ontology\otp_win64_28.5.0.3` |
| Example 应用 | 9083 | 启动成功 | `--server.port=9083`（命令行覆盖，规避 PORT 注入） |

> **构建**：`./mvnw -pl OntologyFrameworkExample -am package -DskipTests`（铁律 1 带 `-am`），产物 `target/OntologyFrameworkExample-0.0.1-SNAPSHOT.jar`，内含 `ontology.invoke.enabled=true`。

> **环境启动注记（含一处勘误）**：
> - 在本次自动化会话中，EnvPrepare `start` 因就绪检查调用 `sc.exe`（被本机安全策略拦截）而失败回滚，故一度改为手工拉起 Ontop / RabbitMQ；MySQL 为系统服务（已运行），跳过。
> - **勘误**：手工启动 Ontop 时记录的「`--properties` 不接受 `file:` URI」结论**不成立**。核对 `logs/ontop.log`（EnvPrepare 托管、追加式）可见：`Illegal character in scheme` 出现次数为 **0**，且 `15:59:03` 有成功启动（"Ontop has completed the setup and it is ready for query answering!" + "Tomcat started on port(s): 8080"）。EnvPrepare 正是以 `Path.toUri()` 生成的 `file:///…` URI 传 `--properties`/`-m`，工作正常。
> - **真实原因**：手工命令经 Git Bash → cmd 时**引号/反斜杠被转义破坏**（`ontop-manual.log` 中可见 `"file:///…` 多了前导引号、`D://work//…` 被反转义），导致 Ontop 收到非法参数。规避办法：用 `.cmd` 包装脚本传参，或 `MSYS_NO_PATHCONV=1` 配合正斜杠 `file:` URI。
> - **结论**：Ontop `--properties` **同时接受** `file:` URI 与纯路径（EnvPrepare 生产路径用的是 `file:` URI）。

## 2. 健康探测

```
GET /api/ontology/health → {"component":"OntologyInvocationController","engineAvailable":true}
GET /api/example/health  → {"engineAvailable":true,"processId":"PizzaMakingStandardProcess",
                            "bpmnPath":"ontology/bpmn/myPizzaBakingProcess.bpmn"}
```

## 3. 端到端调用（Python 客户端）

```
python verify_example.py   → exit 0

[1] 健康探测 → {"component": "OntologyInvocationController", "engineAvailable": true, "_httpStatus": 200}
[2] inputs = {"pizzaType": "http://example.org/pizza/core/MargheritaPizza"}
[3] status=COMPLETED  success=True  elapsed=8576 ms  key=2251799818153834
    outputs 键 = ['propertyValues', 'componentName', 'componentPrice', 'matchedWord']
    componentName = BuffaloMozzarellaInstance   matchedWord = 封层
✅ 端到端验证通过：Python 输入 → Example 本体应用执行 → 结果回传 → Python 继续。
```

## 4. 端到端调用（原始 HTTP，独立复核）

```
POST /api/ontology/invoke
{"processId":"PizzaMakingStandardProcess",
 "inputs":{"pizzaType":"http://example.org/pizza/core/VegetarianPizza"},
 "timeoutMillis":120000,"resultKeys":["componentName","componentPrice","matchedWord"]}

→ {
    "success": true,
    "status": "COMPLETED",
    "processId": "PizzaMakingStandardProcess",
    "processInstanceKey": 2251799818154066,
    "outputs": { "componentName": "ProvelCheeseInstance", "componentPrice": 9.0, "matchedWord": "封层" },
    "elapsedMillis": 2079,
    "message": "流程已完成"
  }
```

## 5. 观察与结论

- 通用端点随应用启动即生效（`@ComponentScan("com.ocean")` 装配 `OntologyInvocationController`），**Example 零业务代码改动**即被 Python 集成。
- 流程实际由 **2 类 jobType**（`query-property-value-ontology` ×11、`get-component` ×4）驱动，与设计文档 §5 一致；旧文档「9 类 / `pizza-summary-generator`」描述已在本次修订中更正。
- 返回值 `componentName`/`matchedWord`（如 `封层`）符合 Example 契约（末段「获取封层 / 补铺表层奶酪封层」环节）。
