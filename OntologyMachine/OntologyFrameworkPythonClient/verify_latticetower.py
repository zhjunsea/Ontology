# -*- coding: utf-8 -*-
"""格构塔本体应用 · Python 集成端到端验证脚本。

验证目标：Python 程序给出输入并调用本体应用 → 本体应用跑完流程 → Python 取回结果
（流程变量 + **结果文件**：Excel 压缩包 ZIP / Markdown 报告）。

⚠️ 注意：流程变量 `designResultSummary` 在结果较大（如 15+ 个底宽）时**超过引擎变量上限会被截断**，
读回只剩首尾引号与字面 ``\\n``。**完整报告应以落盘文件 `resultMdPath` 为准**——本脚本正是
「先下载结果文件，再从文件打印报告」，规避截断。

用法：python verify_latticetower.py [应用地址，默认 http://localhost:9086]
"""
import json
import os
import sys
import zipfile

# 客户端随本模块自带（latticetower_client.py / ontology_client.py）
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from latticetower_client import LatticeTowerClient, DEFAULT_BASE, PROCESS_ID  # noqa: E402
from ontology_client import OntologyInvokeError  # noqa: E402

# 结果文件落地目录（脚本同级 out/）
OUT_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "out")


def pretty(text) -> str:
    """还原「被 JSON 转义/截断」的多行文本（含字面 ``\\n``、首尾引号）为真实换行。"""
    s = str(text)
    if len(s) >= 2 and s[0] == '"' and s[-1] == '"':
        try:
            return json.loads(s)
        except Exception:
            pass
    if "\\n" in s:                       # 截断/转义形态：手动还原
        s = s.replace("\\r\\n", "\n").replace("\\n", "\n").replace("\\t", "\t")
    if s[:1] == '"':
        s = s[1:]
    if s[-1:] == '"':
        s = s[:-1]
    return s


def print_report(title: str, text: str, max_lines: int = 16) -> None:
    lines = pretty(text).splitlines()
    print(title)
    for line in lines[:max_lines]:
        print("    |", line)
    if len(lines) > max_lines:
        print(f"    | ……（共 {len(lines)} 行，此处仅显示前 {max_lines} 行）")


def main() -> int:
    base = sys.argv[1] if len(sys.argv) > 1 else DEFAULT_BASE
    cli = LatticeTowerClient(base)

    print(f"[1] 健康探测 {base}/api/ontology/health")
    h = cli.health()
    print("    ->", json.dumps(h, ensure_ascii=False))
    assert h.get("engineAvailable") is True, "引擎未就绪"
    assert h.get("component") == "OntologyInvocationController", "通用控制器未装配"

    print(f"[2] Python 提交输入并同步调用本体流程 {PROCESS_ID}")
    inputs = {"height": 115253, "disBottMin": 15, "disBottMax": 30, "facadeType": "斜直线型"}
    print("    输入 inputs =", json.dumps(inputs, ensure_ascii=False))

    result = cli.design(
        inputs["height"],
        dis_bott_min=inputs["disBottMin"],
        dis_bott_max=inputs["disBottMax"],
        facade_type=inputs["facadeType"],
        timeout_millis=300_000,
        result_keys=["designResultSummary", "solutionCount", "optimizedBwCount",
                     "resultCsvPath", "resultMdPath", "resultXlsxPaths",
                     "ontologyValidationStatus"],
    )
    outputs = result.get("outputs") or {}
    key = result.get("processInstanceKey")

    print("[3] 本体应用返回（流程变量）：")
    print("    status  =", result.get("status"))
    print("    success =", result.get("success"))
    print("    elapsed =", result.get("elapsedMillis"), "ms")
    print("    key     =", key)
    print("    outputs 键 =", list(outputs.keys()))
    print("    solutionCount =", outputs.get("solutionCount"))
    print("    ontologyValidationStatus =", outputs.get("ontologyValidationStatus"))

    assert result.get("success") is True, f"流程未成功：{result.get('status')} / {result.get('message')}"
    assert "resultXlsxPaths" in outputs, "缺少 resultXlsxPaths 输出"

    # ---- 第 4 步：Python 取回结果文件（按 key 再调应用侧导出端点）----------------
    print("[4] Python 取回结果文件（按 processInstanceKey 调应用侧导出端点）")
    os.makedirs(OUT_DIR, exist_ok=True)
    xlsx_paths = outputs.get("resultXlsxPaths") or []
    print(f"    resultXlsxPaths（服务端本地路径，共 {len(xlsx_paths)} 个） =")
    for p in xlsx_paths:
        print("      -", p)

    zip_path = os.path.join(OUT_DIR, f"latticetower-excel-{key}.zip")
    n = cli.download_excel_zip(key, zip_path)
    print(f"    ✅ Excel 压缩包 → {zip_path}")
    print(f"       {n} bytes")
    with zipfile.ZipFile(zip_path) as z:
        names = z.namelist()
        print(f"       ZIP 条目（{len(names)} 个）:")
        for nm in names:
            print("         -", nm)

    md_path = os.path.join(OUT_DIR, f"latticetower-result-{key}.md")
    try:
        mn = cli.download_export(key, md_path, fmt="md")
        print(f"    ✅ Markdown 报告 → {md_path}  ({mn} bytes)")
    except OntologyInvokeError as e:
        print("    （跳过 Markdown）", e)
        md_path = None

    # ---- 第 5 步：从「下载落盘的报告文件」打印（完整、真实换行；规避变量截断）----
    print("[5] Python 拿到结果继续执行（逐行打印结果报告；源＝下载落盘的 MD 文件）")
    if md_path and os.path.isfile(md_path):
        with open(md_path, "r", encoding="utf-8") as f:
            print_report("    designResultSummary（完整报告）:", f.read())
    else:
        print("    （回退）流程变量摘要（可能被截断）:")
        print_report("    designResultSummary:", outputs.get("designResultSummary", ""))

    print("\n✅ 端到端验证通过：Python 输入 → 本体应用执行 → 结果回传（含文件下载）→ Python 继续。")
    print(f"   下载目录：{OUT_DIR}")
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
