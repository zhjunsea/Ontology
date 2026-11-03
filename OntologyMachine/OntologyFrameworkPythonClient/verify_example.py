# -*- coding: utf-8 -*-
"""Example 本体应用 · Python 集成调用/验证脚本。

验证目标：Python 程序给出输入并调用 Example 本体应用 → 应用跑完烘焙流程（PizzaMakingStandardProcess）
          → Python 取回结果并继续处理。

前置：Camunda(26500/9080) + MySQL(mypizzadb) + Ontop(8080) + RabbitMQ(5672) 已就绪，
      且 OntologyFrameworkExample（Profile=ExampleBPMN）已在 http://localhost:9083 启动。

用法：
    python verify_example.py [应用地址，默认 http://localhost:9083]

说明：客户端随本模块自带（`ontology_client.py`，镜像自
      OntologyMachine/Utilities/python/ontology_client.py，仅用 Python 标准库）。
"""
import json
import os
import sys

# 客户端随本模块自带（ontology_client.py，镜像自 Utilities/python/ontology_client.py）
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from ontology_client import OntologyClient, OntologyInvokeError  # noqa: E402

BASE = sys.argv[1] if len(sys.argv) > 1 else os.environ.get("EXAMPLE_BASE_URL", "http://localhost:9083")
PROCESS_ID = "PizzaMakingStandardProcess"
PIZZA = "http://example.org/pizza/core/MargheritaPizza"


def main() -> int:
    client = OntologyClient(BASE)

    print(f"[1] 健康探测 {BASE}/api/ontology/health")
    h = client.health()
    print("    ->", json.dumps(h, ensure_ascii=False))
    assert h.get("engineAvailable") is True, "引擎未就绪"
    assert h.get("component") == "OntologyInvocationController", "通用控制器未装配"

    print(f"[2] Python 提交输入并同步调用本体流程 {PROCESS_ID}")
    inputs = {"pizzaType": PIZZA}  # 披萨类型 IRI（Python 给出的输入）
    print("    输入 inputs =", json.dumps(inputs, ensure_ascii=False))

    result = client.invoke(
        PROCESS_ID,
        inputs,
        timeout_millis=300_000,
        result_keys=["propertyValues", "componentName", "componentPrice", "matchedWord"],
    )
    print("[3] 本体应用返回：")
    print("    status  =", result.get("status"))
    print("    success =", result.get("success"))
    print("    elapsed =", result.get("elapsedMillis"), "ms")
    print("    key     =", result.get("processInstanceKey"))
    outputs = result.get("outputs") or {}
    print("    outputs 键 =", list(outputs.keys()))
    print("    componentName  =", outputs.get("componentName"))
    print("    componentPrice =", outputs.get("componentPrice"))
    print("    matchedWord    =", outputs.get("matchedWord"))

    assert result.get("success") is True, f"流程未成功：{result.get('status')} / {result.get('message')}"
    # 至少应回传一个业务输出（本次烘焙链由 get-component / query-property-value-ontology 产出）
    assert outputs, "缺少任何输出变量"

    print("[4] Python 拿到结果继续执行（示例：读取封层组件名与匹配标签）")
    print("    | componentName =", outputs.get("componentName"))
    print("    | matchedWord   =", outputs.get("matchedWord"))

    print("\n✅ 端到端验证通过：Python 输入 → Example 本体应用执行 → 结果回传 → Python 继续。")
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except OntologyInvokeError as e:
        print("❌ 调用失败：", e, "| http_status=", e.http_status)
        sys.exit(2)
    except AssertionError as e:
        print("❌ 断言失败：", e)
        sys.exit(3)
