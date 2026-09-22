#!/usr/bin/env bash
# 对照实验：-XX:TieredStopAtLevel=1（IDEA 默认注入）对 Openllet TBox precompute 的影响
# 顺序执行两次，避免 CPU 争用；用日志里的 Windows PID 精确回收，不误伤其它 java 进程。
export PATH="/usr/bin:/bin:/c/Windows/System32:/c/Windows:$PATH"
ROOT="/d/work/Ontology/TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox/OntologyMachine"
WIN="D:/work/Ontology/TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox/OntologyMachine"
JAVA="/c/Program Files/Java/jdk-26.0.1/bin/java.exe"
H="$ROOT/_harness"
cd "$ROOT" || exit 1

run_one () {
  local name="$1" argsfile="$2" log="$H/_exp_$1.log"
  rm -f "$log"
  "$JAVA" "@$WIN/_harness/$argsfile" > "$log" 2>&1 &
  local i
  for i in $(seq 1 150); do
    if grep -q "precompute 完成" "$log" 2>/dev/null; then break; fi
    sleep 5
  done
  echo "===== $name ====="
  grep -E "推理器创建 \+ flush 完成|precompute 完成|初始化完成，总耗时" "$log" | sed 's/.*ReasonerService - //'
  local pid
  pid=$(grep -o "with PID [0-9]*" "$log" | head -1 | grep -o "[0-9]*")
  if [ -n "$pid" ]; then MSYS_NO_PATHCONV=1 taskkill /F /PID "$pid" >/dev/null 2>&1; echo "killed PID=$pid"; fi
  sleep 6
}

run_one ctl _ctl.args
run_one tiered _tiered.args
echo "ALL_DONE"
