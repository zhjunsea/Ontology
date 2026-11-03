"""本体应用 Python 集成客户端（零依赖，仅用标准库）。

场景：让 Python 程序调用「本体应用」——
    Python 给出输入  →  调用本体应用（经其 REST 端点）  →  取回结果  →  Python 继续执行。

对应本体应用侧的通用调用端点由 `Utilities` 提供
（`com.ocean.utilities.OntologyInvocationController`，默认基路径 `/api/ontology`，
默认启用；可用 application.yaml 的 `ontology.invoke.enabled` / `ontology.invoke.base-path` 调整）。

最小用法::

    from ontology_client import OntologyClient

    client = OntologyClient("http://localhost:9083")
    if not client.health()["engineAvailable"]:
        raise SystemExit("本体应用/引擎未就绪")

    result = client.invoke(
        "PizzaMakingStandardProcess",
        {"pizzaType": "Margherita"},
        timeout_millis=120_000,
        result_keys=["pizzaSummary", "myPizzaInstanceIri"],
    )
    if result["success"]:
        print("本体输出：", result["outputs"])
        # ... Python 拿到结果继续执行 ...
    else:
        print("未完成：", result["status"], result["message"])

异步用法::

    key = client.invoke_async("PizzaMakingStandardProcess", {"pizzaType": "Margherita"})
    result = client.wait(key, timeout_millis=120_000)

说明：`invoke` 返回的字典即服务端 `InvokeResult` 的 JSON（含 `success` / `status` /
`outputs` / `processInstanceKey` / `elapsedMillis` / `message`），并附带 `_httpStatus`。
HTTP 传输失败或响应非 JSON 时抛 `OntologyInvokeError`；而 `TIMEOUT` / `INCIDENT`
属正常业务结果，不抛异常，由调用方检查 `status` 处理。
"""

from __future__ import annotations

import json
import time
import urllib.error
import urllib.request
from typing import Any, Dict, Iterable, List, Mapping, Optional

__all__ = ["OntologyClient", "OntologyInvokeError"]


class OntologyInvokeError(RuntimeError):
    """调用本体应用失败：连接异常、超时或响应无法解析为 JSON。"""

    def __init__(self, message: str, http_status: Optional[int] = None, body: Optional[str] = None) -> None:
        super().__init__(message)
        self.http_status = http_status
        self.body = body


class OntologyClient:
    """本体应用 HTTP 客户端（同步阻塞语义）。

    :param base_url: 本体应用根地址，如 ``http://localhost:9083``。
    :param base_path: 通用调用端点基路径，默认 ``/api/ontology``。
    :param timeout: 单次 HTTP 请求的网络超时（秒）。注意它应大于服务端 ``timeout_millis``，
        否则网络层会先于业务层超时。
    """

    def __init__(
        self,
        base_url: str = "http://localhost:9083",
        base_path: str = "/api/ontology",
        timeout: float = 600.0,
    ) -> None:
        self.base_url = base_url.rstrip("/")
        self.base_path = "/" + base_path.strip("/")
        self.timeout = timeout

    # ------------------------------------------------------------------ 公开 API

    def health(self) -> Dict[str, Any]:
        """探测本体应用与引擎是否就绪。"""
        return self._request("GET", "/health")

    def invoke(
        self,
        process_id: str,
        inputs: Optional[Mapping[str, Any]] = None,
        *,
        timeout_millis: Optional[int] = None,
        poll_interval_millis: Optional[int] = None,
        bpmn_paths: Optional[Iterable[str]] = None,
        result_keys: Optional[Iterable[str]] = None,
    ) -> Dict[str, Any]:
        """同步调用：提交输入并等待流程结束，返回结果字典（含 ``outputs``）。"""
        payload: Dict[str, Any] = {"processId": process_id, "inputs": dict(inputs or {})}
        self._put_optional(payload, "timeoutMillis", timeout_millis)
        self._put_optional(payload, "pollIntervalMillis", poll_interval_millis)
        self._put_optional(payload, "bpmnPaths", list(bpmn_paths) if bpmn_paths is not None else None)
        self._put_optional(payload, "resultKeys", list(result_keys) if result_keys is not None else None)
        return self._request("POST", "/invoke", payload)

    def invoke_async(
        self,
        process_id: str,
        inputs: Optional[Mapping[str, Any]] = None,
        *,
        bpmn_paths: Optional[Iterable[str]] = None,
    ) -> int:
        """异步调用：启动流程并立即返回 ``processInstanceKey``。"""
        payload: Dict[str, Any] = {"processId": process_id, "inputs": dict(inputs or {})}
        self._put_optional(payload, "bpmnPaths", list(bpmn_paths) if bpmn_paths is not None else None)
        body = self._request("POST", "/invoke-async", payload)
        key = body.get("processInstanceKey")
        if key is None:
            raise OntologyInvokeError("响应缺少 processInstanceKey", body=json.dumps(body, ensure_ascii=False))
        return int(key)

    def query(self, process_instance_key: int, result_keys: Optional[Iterable[str]] = None) -> Dict[str, Any]:
        """查询快照：不等待，返回当前状态与已产生输出。"""
        path = f"/invoke/{int(process_instance_key)}"
        if result_keys:
            from urllib.parse import urlencode

            path += "?" + urlencode([("resultKeys", k) for k in result_keys])
        return self._request("GET", path)

    def wait(
        self,
        process_instance_key: int,
        *,
        timeout_millis: int = 300_000,
        poll_interval_millis: int = 500,
    ) -> Dict[str, Any]:
        """客户端侧轮询等待（配合 :meth:`invoke_async` 使用），直到终态或超时。"""
        deadline = time.monotonic() + max(0, timeout_millis) / 1000.0
        last: Dict[str, Any] = {"status": "RUNNING", "outputs": {}}
        while True:
            last = self.query(process_instance_key)
            if last.get("status") != "RUNNING":
                return last
            if time.monotonic() >= deadline:
                last["status"] = "TIMEOUT"
                last["message"] = f"等待超时（{timeout_millis} ms）"
                return last
            time.sleep(max(0.05, poll_interval_millis / 1000.0))

    # ------------------------------------------------------------------ 内部实现

    @staticmethod
    def _put_optional(payload: Dict[str, Any], key: str, value: Any) -> None:
        if value is not None:
            payload[key] = value

    def _url(self, path: str) -> str:
        return f"{self.base_url}{self.base_path}{path}"

    def _request(self, method: str, path: str, payload: Optional[Dict[str, Any]] = None) -> Dict[str, Any]:
        data = None
        if payload is not None:
            data = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        req = urllib.request.Request(self._url(path), data=data, method=method)
        req.add_header("Accept", "application/json")
        if data is not None:
            req.add_header("Content-Type", "application/json")

        try:
            with urllib.request.urlopen(req, timeout=self.timeout) as resp:
                raw = resp.read().decode("utf-8", "replace")
                status = resp.status
        except urllib.error.HTTPError as e:  # 非 2xx：仍读取响应体（可能含业务结果 JSON）
            raw = e.read().decode("utf-8", "replace")
            status = e.code
        except urllib.error.URLError as e:
            raise OntologyInvokeError(f"无法连接本体应用 {self.base_url}: {e.reason}") from e

        if not raw:
            body: Any = {}
        else:
            try:
                body = json.loads(raw)
            except json.JSONDecodeError as e:
                raise OntologyInvokeError("本体应用返回非 JSON 响应", http_status=status, body=raw) from e

        if not isinstance(body, dict):
            body = {"data": body}
        body["_httpStatus"] = status
        return body


if __name__ == "__main__":
    # 演示：Python 给出输入 → 本体应用执行 → Python 取回结果继续处理。
    demo = OntologyClient("http://localhost:9083")
    print("health:", demo.health())
    out = demo.invoke(
        "PizzaMakingStandardProcess",
        {"pizzaType": "Margherita"},
        timeout_millis=120_000,
        result_keys=["pizzaSummary", "myPizzaInstanceIri", "ontologyValidationStatus"],
    )
    print("invoke:", json.dumps(out, ensure_ascii=False, indent=2))
