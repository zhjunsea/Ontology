#!/usr/bin/env bash
# 对比 raw（合并顺序随 HashSet 迭代序变化）与 sorted（按 toString 稳定排序）两种公理装载顺序
export PATH="/usr/bin:/bin:/c/Windows/System32:/c/Windows:$PATH"
H="/d/work/Ontology/TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox/OntologyMachine/_harness"
cd "$H"
N=${N:-6}
export TMO=${TMO:-75}

for mode in sort nosort; do
  echo "########## MODE: $mode ##########"
  for i in $(seq 1 "$N"); do
    start=$(date +%s)
    out=$(bash run_pc4.sh "$mode" 2>&1 | grep -a "RESULT")
    end=$(date +%s)
    if [ -z "$out" ]; then
      echo "  run$i: HANG/TIMEOUT (wall=$((end-start))s)"
    else
      echo "  run$i: $out (wall=$((end-start))s)"
    fi
  done
done
echo "########## CMP DONE ##########"
