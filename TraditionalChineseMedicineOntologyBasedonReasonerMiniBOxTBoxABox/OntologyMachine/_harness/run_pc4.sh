#!/usr/bin/env bash
# 运行 PrecomputeProbe4（验证「公理插入顺序」是否为分类耗时非确定性的根因）
# 用法: bash run_pc4.sh [sort|nosort]
export PATH="/usr/bin:/bin:/c/Windows/System32:/c/Windows:$PATH"
ROOT="/d/work/Ontology/TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox/OntologyMachine"
JAVA="/c/Program Files/Java/jdk-26.0.1/bin/java"
JAVAC="/c/Program Files/Java/jdk-26.0.1/bin/javac"
MOD="$ROOT/OntologyFramework"
H="$ROOT/_harness"
HW="D:/work/Ontology/TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox/OntologyMachine/_harness"
OUT="$H/_satout"
mkdir -p "$OUT"

DEPS=$(cat "$MOD/target/test-classpath.txt" | tr -d '\r\n')
CP="$MOD/target/classes;$ROOT/OntopOBDAHandler/target/classes;$ROOT/OpenlletResolver/target/classes;$DEPS"
CP=$(echo "$CP" | sed -E 's#(^|;)/([a-zA-Z])/#\1\U\2:/#g')

printf -- '-cp\n%s\n-d\n%s/_satout\n%s/PrecomputeProbe4.java\n' "$CP" "$HW" "$HW" > "$OUT/pc4_javac.args"
"$JAVAC" "@$HW/_satout/pc4_javac.args" || { echo "COMPILE FAILED"; exit 2; }

TBOX="D:/work/Ontology/TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox/ontology/tcm-all.owl"
MODE="${1:-sort}"
{
  echo '-Dfile.encoding=UTF-8'
  echo '-cp'
  echo "\"_satout;${CP//\\/\\\\}\""
  echo 'PrecomputeProbe4'
  echo "\"$TBOX\""
  echo "\"$MODE\""
} > "$OUT/pc4.args"

cd "$H"
TMO="${TMO:-0}"
if [ "$TMO" -gt 0 ] 2>/dev/null; then
  timeout -k 5 "$TMO" "$JAVA" "@$HW/_satout/pc4.args"
else
  "$JAVA" "@$HW/_satout/pc4.args"
fi
