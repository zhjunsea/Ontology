#!/usr/bin/env bash
# 聚焦测试：绕开 CD 优化分类器的配置是否稳定（不挂起）
export PATH="/usr/bin:/bin:/c/Windows/System32:/c/Windows:$PATH"
H="/d/work/Ontology/TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox/OntologyMachine/_harness"
cd "$H"
N=${N:-6}
export TMO=${TMO:-75}

CONFIGS=(
  "USE_CD_CLASSIFICATION=false"
  "USE_CD_CLASSIFICATION=false;USE_ADVANCED_CACHING=false"
  "USE_CD_CLASSIFICATION=false;ORDERED_CLASSIFICATION=ENABLED_LEGACY_ORDERING"
)

for cfg in "${CONFIGS[@]}"; do
  echo "########## CONFIG: [$cfg] ##########"
  for i in $(seq 1 "$N"); do
    start=$(date +%s)
    out=$(bash run_pc3.sh "$cfg" 2>&1 | grep -a "RESULT")
    end=$(date +%s)
    if [ -z "$out" ]; then
      echo "  run$i: HANG/TIMEOUT (wall=$((end-start))s)"
    else
      echo "  run$i: $out (wall=$((end-start))s)"
    fi
  done
done
echo "########## CD SWEEP DONE ##########"
