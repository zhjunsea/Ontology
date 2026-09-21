#!/usr/bin/env bash
# 铁律 54：同机、背靠背、交错（A B C A B C ...）取样，判定哪个配置稳定不挂起。
# A = baseline(无选项)  B = CD=false  C = CD=false + ADVANCED_CACHING=false
export PATH="/usr/bin:/bin:/c/Windows/System32:/c/Windows:$PATH"
H="/d/work/Ontology/TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox/OntologyMachine/_harness"
cd "$H"
ROUNDS=${ROUNDS:-4}
export TMO=${TMO:-120}

A=""
B="USE_CD_CLASSIFICATION=false"
C="USE_CD_CLASSIFICATION=false;USE_ADVANCED_CACHING=false"

for r in $(seq 1 "$ROUNDS"); do
  for tag in A B C; do
    case $tag in
      A) cfg="$A" ;;
      B) cfg="$B" ;;
      C) cfg="$C" ;;
    esac
    start=$(date +%s)
    out=$(bash run_pc3.sh "$cfg" 2>&1 | grep -a "RESULT")
    end=$(date +%s)
    if [ -z "$out" ]; then
      echo "round$r $tag [${cfg:-baseline}]: HANG/TIMEOUT (wall=$((end-start))s)"
    else
      echo "round$r $tag [${cfg:-baseline}]: $out (wall=$((end-start))s)"
    fi
  done
done
echo "########## AB SWEEP DONE ##########"
