# -*- coding: utf-8 -*-
"""格构塔（LatticeTower）本体应用 · Python 客户端。

在通用零依赖客户端 `OntologyClient`（`ontology_client.py`）之上，补充格构塔应用专属能力：

- 触发设计流程（`LatticeTowerDesignProcess`）；
- 下载本次运行的结果文件：
    - **Excel 结果压缩包（ZIP）** → `GET /api/latticetower/design/{key}/export-xlsx`
    - CSV / Markdown 报告        → `GET /api/latticetower/design/{key}/export?format=md|csv`

为何需要「额外一步」：通用端点 `/api/ontology/invoke` 只回传**流程变量**
（其中 `resultXlsxPaths` / `relaxResultXlsxPaths` 是产出 XLSX 的**本地路径**），
**不回传文件字节**；ZIP 由应用侧端点**即时**从这些路径打包返回，故需再调该端点。

前置：Camunda(26500/9080) 就绪，且 `OntologyFrameworkLatticeTower` 已在 http://localhost:9086 启动。
用法（默认地址 http://localhost:9086，可传第 1 个参数覆盖）：

    python latticetower_client.py
"""
import json
import os
import sys
import urllib.error
import urllib.request

# 客户端随本模块自带（ontology_client.py，镜像自 Utilities/python/ontology_client.py）
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from ontology_client import OntologyClient, OntologyInvokeError  # noqa: E402

DEFAULT_BASE = "http://localhost:9086"
PROCESS_ID = "LatticeTowerDesignProcess"


class LatticeTowerClient:
    """格构塔设计应用的调用客户端（通用调用 + 结果文件下载）。"""

    def __init__(self, base_url: str = DEFAULT_BASE, base_path: str = "/api/ontology",
                 timeout: float = 600.0) -> None:
        self.base_url = base_url.rstrip("/")
        self._client = OntologyClient(self.base_url, base_path=base_path, timeout=timeout)

    # ------------------------------------------------------------------ 通用能力
    def health(self):
        """引擎/端点就绪探测（`/api/ontology/health`）。"""
        return self._client.health()

    def design(self, height, *, dis_bott_min=15, dis_bott_max=30, facade_type="斜直线型",
               timeout_millis=300_000, result_keys=None):
        """同步触发格构塔设计流程，返回 `InvokeResult` 字典（含 `processInstanceKey` / `outputs`）。"""
        inputs = {
            "height": height,
            "disBottMin": dis_bott_min,
            "disBottMax": dis_bott_max,
            "facadeType": facade_type,
        }
        return self._client.invoke(PROCESS_ID, inputs, timeout_millis=timeout_millis,
                                   result_keys=result_keys)

    # ------------------------------------------------------------------ 结果文件下载
    def download_excel_zip(self, process_instance_key, dest_path) -> int:
        """下载本次运行的 **Excel 结果压缩包（ZIP）** 到 `dest_path`，返回写入字节数。

        端点：`GET /api/latticetower/design/{key}/export-xlsx`
        （内容＝`resultXlsxPaths` + `relaxResultXlsxPaths` 中的 XLSX 打包；无文件时服务端返回 404）。
        """
        url = (f"{self.base_url}/api/latticetower/design/{int(process_instance_key)}"
               f"/export-xlsx")
        return self._download(url, dest_path)

    def download_export(self, process_instance_key, dest_path, fmt: str = "md") -> int:
        """下载 CSV / Markdown 结果报告（`fmt`=``md``|``csv``）。"""
        url = (f"{self.base_url}/api/latticetower/design/{int(process_instance_key)}"
               f"/export?format={fmt}")
        return self._download(url, dest_path)

    # ------------------------------------------------------------------ 内部
    @staticmethod
    def _download(url: str, dest_path: str) -> int:
        req = urllib.request.Request(url, method="GET")
        try:
            with urllib.request.urlopen(req, timeout=120) as resp:
                data = resp.read()
        except urllib.error.HTTPError as e:  # 404/503 等：读取错误体
            body = e.read().decode("utf-8", "replace")
            raise OntologyInvokeError(f"下载失败 HTTP {e.code}: {body[:200]}",
                                      http_status=e.code, body=body) from e
        except urllib.error.URLError as e:
            raise OntologyInvokeError(f"无法连接 {url}: {e.reason}") from e

        dest_path = os.path.abspath(dest_path)
        os.makedirs(os.path.dirname(dest_path), exist_ok=True)
        with open(dest_path, "wb") as f:
            f.write(data)
        return len(data)


if __name__ == "__main__":
    import zipfile

    base = sys.argv[1] if len(sys.argv) > 1 else os.environ.get("LATTICETOWER_BASE_URL", DEFAULT_BASE)
    cli = LatticeTowerClient(base)

    print(f"[1] 健康探测 {base}/api/ontology/health")
    print("    ->", json.dumps(cli.health(), ensure_ascii=False))

    print(f"[2] 触发设计流程 {PROCESS_ID}")
    res = cli.design(100000, dis_bott_min=25, dis_bott_max=25, facade_type="斜直线型",
                     timeout_millis=300_000,
                     result_keys=["designResultSummary", "solutionCount",
                                  "resultXlsxPaths", "ontologyValidationStatus"])
    key = res.get("processInstanceKey")
    outs = res.get("outputs") or {}
    print("    status  =", res.get("status"), "| success =", res.get("success"),
          "| key =", key)
    print("    resultXlsxPaths =", outs.get("resultXlsxPaths"))

    if not (res.get("success") and key):
        print("❌ 流程未成功，无法下载结果：", res.get("status"), res.get("message"))
        sys.exit(2)

    print("[3] 下载 Excel 结果压缩包（ZIP）")
    dest = os.path.join(os.path.dirname(os.path.abspath(__file__)), "out",
                        f"latticetower-excel-{key}.zip")
    n = cli.download_excel_zip(key, dest)
    print(f"    -> {dest}  ({n} bytes)")
    with zipfile.ZipFile(dest) as z:
        print("    ZIP 条目:", z.namelist())

    print("[4]（可选）下载 Markdown 报告")
    md = os.path.join(os.path.dirname(os.path.abspath(__file__)), "out",
                      f"latticetower-result-{key}.md")
    try:
        mn = cli.download_export(key, md, fmt="md")
        print(f"    -> {md}  ({mn} bytes)")
    except OntologyInvokeError as e:
        print("    （跳过）", e)

    print("\n✅ 完成：设计结果文件已下载到", os.path.dirname(dest))
