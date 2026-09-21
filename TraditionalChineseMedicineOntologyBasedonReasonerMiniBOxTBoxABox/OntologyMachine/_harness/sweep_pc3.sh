#!/usr/bin/env bash
# 扫描 Openllet 选项组合，每个组合跑 N 次，记录 precompute 耗时（超时=HANG）
# 注意：超时必须直接施加在 java 进程上（run_pc3.sh 内部用 TMO），
#       否则 timeout 只杀 bash、java 存活导致管道不关闭而永久挂起。
export PATH="/usr/bin:/bin:/c/Windows/System32:/c/Windows:$PATH"
H="/d/work/Ontology/TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox/OntologyMachine/_harness"
cd "$H"
RUNS=${RUNS:-3}
export TMO=${TMO:-75}

CONFIGS=(
  ""
  "USE_ADVANCED_CACHING=false"
  "USE_CD_CLASSIFICATION=false"
  "USE_ADVANCED_CACHING=false;USE_CD_CLASSIFICATION=false"
  "USE_SMART_RESTORE=false"
  "USE_BACKJUMPING=false"
  "USE_CACHING=false"
)

for cfg in "${CONFIGS[@]}"; do
  echo "########## CONFIG: [${cfg:-baseline}] ##########"
  for i in $(seq 1 "$RUNS"); do
    start=$(date +%s)
    out=$(bash run_pc3.sh "$cfg" 2>&1 | grep -a "RESULT")
    rc=$?
    end=$(date +%s)
    if [ -z "$out" ]; then
      echo "  run$i: HANG/TIMEOUT (rc=$rc, wall=$((end-start))s)"
    else
      echo "  run$i: $out (wall=$((end-start))s)"
    fi
  done
done
echo "########## SWEEP DONE ##########"
