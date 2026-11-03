# -*- coding: utf-8 -*-
"""格构塔本体应用 · Python 集成端到端验证脚本。

验证目标：Python 程序给出输入并调用本体应用 → 本体应用跑完流程 → Python 取回结果。
用法：python verify_latticetower.py [应用地址，默认 http://localhost:9086]
"""
import json
import sys
import os

# 复用 Utilities 交付的零依赖客户端
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", "..",
                                                "OntologyMachine", "Utilities", "python")))
from ontology_client import OntologyClient, OntologyInvokeError  # noqa: E402

BASE = sys.argv[1] if len(sys.argv) > 1 else "http://localhost:9086"


def main() -> int:
    client = OntologyClient(BASE)
    print(f"[1] 健康探测 {BASE}/api/ontology/health")
    h = client.health()
    print("    ->", json.dumps(h, ensure_ascii=False))
    assert h.get("engineAvailable") is True, "引擎未就绪"
    assert h.get("component") == "OntologyInvocationController", "通用控制器未装配"

    print("[2] Python 提交输入并同步调用本体流程 LatticeTowerDesignProcess")
    inputs = {
        "height": 100000,        # 塔架高度（mm）
        "disBottMin": 25,        # 底面宽度下限（米，自然数）
        "disBottMax": 25,        # 底面宽度上限（单底宽，快速验证）
        "facadeType": "斜直线型",
    }
    print("    输入 inputs =", json.dumps(inputs, ensure_ascii=False))

    result = client.invoke(
        "LatticeTowerDesignProcess",
        inputs,
        timeout_millis=300_000,
        result_keys=[
            "designResultSummary", "solutionCount", "optimizedBwCount",
            "resultCsvPath", "resultMdPath", "ontologyValidationStatus",
        ],
    )
    print("[3] 本体应用返回：")
    print("    status  =", result.get("status"))
    print("    success =", result.get("success"))
    print("    elapsed =", result.get("elapsedMillis"), "ms")
    print("    key     =", result.get("processInstanceKey"))
    outputs = result.get("outputs") or {}
    print("    outputs 键 =", list(outputs.keys()))
    print("    solutionCount =", outputs.get("solutionCount"))
    print("    ontologyValidationStatus =", outputs.get("ontologyValidationStatus"))

    assert result.get("success") is True, f"流程未成功：{result.get('status')} / {result.get('message')}"
    assert "designResultSummary" in outputs, "缺少 designResultSummary 输出"

    print("[4] Python 拿到结果继续执行（示例：打印摘要前 3 行）")
    summary = str(outputs.get("designResultSummary", ""))
    for line in summary.splitlines()[:3]:
        print("    |", line)

    print("\n✅ 端到端验证通过：Python 输入 → 本体应用执行 → 结果回传 → Python 继续。")
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
