import sys
import os
import time
import subprocess
from pathlib import Path

# ==========================================
# 1. 依赖检查
# ==========================================
try:
    import psutil
except ImportError:
    print("[ERROR] 缺少 psutil 库，请在当前虚拟环境中执行: pip install psutil")
    sys.exit(1)

# ==========================================
# 2. 读取 startup-config.properties
# ==========================================
def load_properties(filepath: str) -> dict:
    """解析 Java 风格的 .properties 配置文件（支持 # 注释）"""
    props = {}
    path = Path(filepath)
    if not path.exists():
        print(f"[ERROR] 配置文件不存在: {filepath}")
        sys.exit(1)

    with open(path, "r", encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line or line.startswith("#"):
                continue
            if "=" in line:
                key, value = line.split("=", 1)
                props[key.strip()] = value.strip()
    return props

def to_file_uri(path_str: str) -> str:
    """将 Windows/Unix 路径转换为 Ontop 要求的 file:/// URI 格式"""
    # 统一转为正斜杠
    normalized = path_str.replace("\\", "/")
    # 如果已经是 file: URI 则直接返回，避免重复拼接
    if normalized.lower().startswith("file:"):
        return normalized
    # 确保以 / 开头（Windows 绝对路径 D:/... → /D:/...）
    if not normalized.startswith("/"):
        normalized = "/" + normalized
    return "file://" + normalized

# 配置文件必须与 start_all.py 在同一目录下
SCRIPT_DIR = Path(__file__).resolve().parent
CONFIG_FILE = SCRIPT_DIR / "startup-config.properties"
CONFIG = load_properties(str(CONFIG_FILE))

# 校验必要配置项
# 将 REQUIRED_KEYS 替换为以下内容
REQUIRED_KEYS = [
    "JAVA_HOME",
    "ONTOP_HOME",
    "ONTOP_PROPERTIES",   # ★ 新增
    "ONTOP_MAPPING",      # ★ 新增
    "ONTOLOGY_FILE",      # ★ 新增
    "CAMUNDA_HOME",
    "RABBITMQ_HOME",
]
missing = [k for k in REQUIRED_KEYS if k not in CONFIG]
if missing:
    print(f"[ERROR] startup-config.properties 缺少必要配置项: {', '.join(missing)}")
    sys.exit(1)

# ★ 统一日志目录：Python 脚本所在目录下的 logs 文件夹
LOG_DIR = SCRIPT_DIR / "logs"

print(f"[INFO] 已加载配置: {CONFIG_FILE}")
for k, v in CONFIG.items():
    print(f"       {k} = {v}")
print(f"[INFO] 统一日志目录: {LOG_DIR}")

# ==========================================
# 3. 进程管理工具函数
# ==========================================
def kill_process_tree(proc: psutil.Process) -> None:
    """安全地终止进程及其所有子进程"""
    try:
        children = proc.children(recursive=True)
        for child in children:
            try:
                child.terminate()
            except psutil.NoSuchProcess:
                pass
        gone, alive = psutil.wait_procs(children, timeout=3)
        for p in alive:
            try:
                p.kill()
            except psutil.NoSuchProcess:
                pass
        proc.terminate()
        proc.wait(timeout=3)
    except psutil.NoSuchProcess:
        pass
    except Exception as e:
        print(f"       ⚠️ 终止进程树异常 PID={proc.pid}: {e}")


def find_processes_by_name(name: str):
    """根据进程名模糊查找"""
    targets = []
    name_lower = name.lower()
    for proc in psutil.process_iter(["pid", "name"]):
        try:
            if proc.info["name"] and name_lower in proc.info["name"].lower():
                targets.append(proc)
        except (psutil.NoSuchProcess, psutil.AccessDenied):
            continue
    return targets


# ==========================================
# 4. 停止服务逻辑
# ==========================================
def stop_camunda():
    print("[STOP] Camunda: 执行 c8run.exe stop ...")
    cmd = [str(Path(CONFIG["CAMUNDA_HOME"]) / "c8run.exe"), "stop"]
    try:
        subprocess.run(
            cmd,
            cwd=CONFIG["CAMUNDA_HOME"],
            timeout=30,
            creationflags=subprocess.CREATE_NO_WINDOW,
        )
        print("       ✅ Camunda 停止指令已发送")
    except Exception as e:
        print(f"       ⚠️ Camunda 停止异常: {e}")


def stop_ontop():
    print("[STOP] Ontop: 查找并终止进程树 ...")
    targets = find_processes_by_name("ontop")
    for proc in psutil.process_iter(["pid", "name", "cmdline"]):
        try:
            if proc.info["name"] and proc.info["name"].lower() == "java.exe":
                cmdline = " ".join(proc.info["cmdline"]).lower()
                if "ontop" in cmdline:
                    targets.append(proc)
        except (psutil.NoSuchProcess, psutil.AccessDenied, TypeError):
            continue

    seen = set()
    unique = [p for p in targets if not (p.pid in seen or seen.add(p.pid))]

    if not unique:
        print("       ℹ️ 未发现运行中的 Ontop 进程")
        return

    for p in unique:
        print(f"       正在终止 Ontop 进程树 PID={p.pid} ...")
        kill_process_tree(p)
    print("       ✅ Ontop 已完全停止")


def stop_rabbitmq():
    print("[STOP] RabbitMQ: 查找并终止 Erlang 节点 ...")
    targets = []
    for proc in psutil.process_iter(["pid", "name", "cmdline"]):
        try:
            if proc.info["name"] and proc.info["name"].lower() == "erl.exe":
                cmdline = " ".join(proc.info["cmdline"]).lower()
                if "rabbit" in cmdline or "25672" in cmdline:
                    targets.append(proc)
        except (psutil.NoSuchProcess, psutil.AccessDenied, TypeError):
            continue

    targets.extend(find_processes_by_name("rabbitmq"))
    seen = set()
    unique = [p for p in targets if not (p.pid in seen or seen.add(p.pid))]

    if not unique:
        print("       ℹ️ 未发现运行中的 RabbitMQ/Erlang 进程")
        return

    for p in unique:
        print(f"       正在终止 PID={p.pid} ({p.name()}) ...")
        kill_process_tree(p)

    time.sleep(2)
    port_freed = all(
        conn.laddr.port != 25672
        for conn in psutil.net_connections(kind="inet")
        if conn.laddr
    )
    if port_freed:
        print("       ✅ RabbitMQ 已完全停止，端口 25672 已释放")
    else:
        print("       🔴 警告: 端口 25672 仍被占用")


# ==========================================
# 5. 启动服务逻辑
# ==========================================
def start_service(name: str, cmd: list, cwd: str = None, log_file: str = None, env=None):
    print(f"[START] {name}: {' '.join(cmd)}")

    # ★ Windows 下 CREATE_NO_WINDOW 不能直接运行 .bat/.cmd
    executable = cmd[0]
    if executable.lower().endswith(".bat") or executable.lower().endswith(".cmd"):
        cmd = ["cmd.exe", "/c"] + cmd

    # ★ 校验 cwd 有效性
    if cwd is not None:
        cwd_path = Path(cwd)
        if not cwd_path.exists():
            raise FileNotFoundError(f"[{name}] cwd 路径不存在: {cwd}")
        if not cwd_path.is_dir():
            raise NotADirectoryError(f"[{name}] cwd 不是有效目录: {cwd}")

    kwargs = {"cwd": cwd, "creationflags": subprocess.CREATE_NO_WINDOW}
    if env is not None:
        kwargs["env"] = env

    f_handle = None
    if log_file:
        Path(log_file).parent.mkdir(parents=True, exist_ok=True)
        f_handle = open(log_file, "a", encoding="utf-8")
        kwargs["stdout"] = f_handle
        kwargs["stderr"] = subprocess.STDOUT
    else:
        kwargs["stdout"] = subprocess.PIPE
        kwargs["stderr"] = subprocess.STDOUT

    try:
        proc = subprocess.Popen(cmd, **kwargs)
        print(f"       ✅ {name} 已启动 (PID={proc.pid})")
        if log_file:
            print(f"       📄 日志输出: {log_file}")
        return proc, f_handle
    except FileNotFoundError as e:
        print(f"       🔴 {name} 启动失败: 找不到可执行文件")
        print(f"          命令: {' '.join(cmd)}")
        print(f"          CWD: {cwd}")
        raise e


# ==========================================
# 6. 主流程
# ==========================================
def main():
    print("=" * 50)
    print(" 🚀 开始初始化本体环境服务 ")
    print("=" * 50)

    # 1. 清理旧服务
    stop_camunda()
    stop_ontop()
    stop_rabbitmq()
    time.sleep(2)

    # 2. 确保统一日志目录存在
    LOG_DIR.mkdir(parents=True, exist_ok=True)

    processes = []
    file_handles = []

    # 3. 启动 RabbitMQ
    rmq_cmd = [str(Path(CONFIG["RABBITMQ_HOME"]) / "sbin" / "rabbitmq-server.bat")]
    p, f = start_service(
        "RabbitMQ",
        rmq_cmd,
        cwd=CONFIG["RABBITMQ_HOME"],
        log_file=str(LOG_DIR / "rabbitmq.log"),
    )
    processes.append(p)
    if f:
        file_handles.append(f)

        # 4. 启动 Ontop（注入 JAVA_HOME 环境变量）
    ontop_env = os.environ.copy()
    ontop_env["JAVA_HOME"] = CONFIG["JAVA_HOME"]

    # ★ 将所有文件路径转为 file:/// URI 格式
    ontop_properties = to_file_uri(CONFIG["ONTOP_PROPERTIES"])
    ontop_mapping = to_file_uri(CONFIG["ONTOP_MAPPING"])
    ontology_file = to_file_uri(CONFIG["ONTOLOGY_FILE"])

    print(f"       📎 Properties: {ontop_properties}")
    print(f"       📎 Mapping:    {ontop_mapping}")
    print(f"       📎 Ontology:   {ontology_file}")

    ontop_cmd = [
        str(Path(CONFIG["ONTOP_HOME"]) / "ontop.bat"),
        "endpoint",
        "--properties", ontop_properties,
        "-m", ontop_mapping,
        "-t", ontology_file,
    ]

    p, f = start_service(
        "Ontop",
        ontop_cmd,
        cwd=CONFIG["ONTOP_HOME"],
        log_file=str(LOG_DIR / "ontop.log"),
        env=ontop_env,
    )
    processes.append(p)
    if f:
        file_handles.append(f)

    # 5. 启动 Camunda（★ 必须指定 cwd）
    camunda_cmd = [
        str(Path(CONFIG["CAMUNDA_HOME"]) / "c8run.exe"),
        "start",
        "--port",
        "9080",
    ]
    p, _ = start_service("Camunda", camunda_cmd, cwd=CONFIG["CAMUNDA_HOME"])
    processes.append(p)

    print("\n" + "=" * 50)
    print(" ✅ 所有服务已启动。")
    print(f" 📂 统一日志目录: {LOG_DIR}")
    print(" ⏹️  按 Ctrl+C 停止所有服务并退出。")
    print("=" * 50 + "\n")

    # 6. 保持主进程运行并监听退出信号
    try:
        while True:
            time.sleep(1)
    except KeyboardInterrupt:
        print("\n[EXIT] 收到退出信号，正在清理所有进程...")
        for p in processes:
            if p.poll() is None:
                try:
                    kill_process_tree(psutil.Process(p.pid))
                except psutil.NoSuchProcess:
                    pass
        for f in file_handles:
            f.close()
        stop_camunda()
        print("✅ 清理完成，已安全退出。")


if __name__ == "__main__":
    main()