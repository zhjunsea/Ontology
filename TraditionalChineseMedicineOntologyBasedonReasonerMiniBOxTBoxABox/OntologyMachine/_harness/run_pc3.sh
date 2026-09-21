#!/usr/bin/env bash
# 运行 PrecomputeProbe3（可切换 Openllet 选项）
# 用法: bash run_pc3.sh "USE_ADVANCED_CACHING=false;USE_CD_CLASSIFICATION=false"
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

# 编译（幂等）。注意：javac argfile 中 classpath 不能加引号，@ 路径须用 Windows 形式
printf -- '-cp\n%s\n-d\n%s/_satout\n%s/PrecomputeProbe3.java\n' "$CP" "$HW" "$HW" > "$OUT/pc3_javac.args"
"$JAVAC" "@$HW/_satout/pc3_javac.args" || { echo "COMPILE FAILED"; exit 2; }

TBOX="D:/work/Ontology/TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox/ontology/tcm-all.owl"
OPTS="$1"
{
  echo '-Dfile.encoding=UTF-8'
  echo "-Dpc3.opts=$OPTS"
  echo '-cp'
  echo "\"_satout;${CP//\\/\\\\}\""
  echo 'PrecomputeProbe3'
  echo "\"$TBOX\""
} > "$OUT/pc3.args"

cd "$H"
TMO="${TMO:-0}"
if [ "$TMO" -gt 0 ] 2>/dev/null; then
  timeout -k 5 "$TMO" "$JAVA" "@$HW/_satout/pc3.args"
else
  "$JAVA" "@$HW/_satout/pc3.args"
fi
