#!/usr/bin/env bash
# 编译 OpenlletResolver + OntologyFramework 主源码到各自 target/classes
# （绕开 maven repackage/依赖解析问题，与 run_suite.sh 的 classpath 口径一致）
export PATH="/usr/bin:/bin:/c/Windows/System32:/c/Windows:$PATH"
ROOT="/d/work/Ontology/TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox/OntologyMachine"
WIN="D:/work/Ontology/TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox/OntologyMachine"
JAVAC="/c/Program Files/Java/jdk-26.0.1/bin/javac"
MOD="$ROOT/OntologyFramework"
H="$ROOT/_harness"

DEPS=$(cat "$MOD/target/test-classpath.txt" | tr -d '\r\n')
CP_BASE="$WIN/OntopOBDAHandler/target/classes;$DEPS"

mkdir -p "$H/_build"

# ---- 1. OpenlletResolver ----
find "$ROOT/OpenlletResolver/src/main/java" -name '*.java' \
  | sed -E 's#^/d/#D:/#' > "$H/_build/ol_src.txt"
{
  echo '-encoding'; echo 'UTF-8'
  echo '-nowarn'
  echo '-d'; echo "\"$WIN/OpenlletResolver/target/classes\""
  echo '-cp'; echo "\"${CP_BASE//\\/\\\\}\""
  cat "$H/_build/ol_src.txt"
} > "$H/_build/ol.args"
echo "== javac OpenlletResolver =="
"$JAVAC" "@$WIN/_harness/_build/ol.args" 2>&1 | head -40
OL_EXIT=${PIPESTATUS[0]}

# ---- 2. OntologyFramework ----
CP2="$WIN/OpenlletResolver/target/classes;$CP_BASE"
find "$ROOT/OntologyFramework/src/main/java" -name '*.java' \
  | sed -E 's#^/d/#D:/#' > "$H/_build/of_src.txt"
{
  echo '-encoding'; echo 'UTF-8'
  echo '-nowarn'
  echo '-d'; echo "\"$WIN/OntologyFramework/target/classes\""
  echo '-cp'; echo "\"${CP2//\\/\\\\}\""
  cat "$H/_build/of_src.txt"
} > "$H/_build/of.args"
echo "== javac OntologyFramework =="
"$JAVAC" "@$WIN/_harness/_build/of.args" 2>&1 | head -40
OF_EXIT=${PIPESTATUS[0]}

echo "OL_EXIT=$OL_EXIT OF_EXIT=$OF_EXIT"
