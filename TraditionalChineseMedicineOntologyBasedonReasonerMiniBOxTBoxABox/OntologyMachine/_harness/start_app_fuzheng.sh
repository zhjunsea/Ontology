#!/usr/bin/env bash
# 启动 TCM 应用（Zeebe Worker），输出到 app_run_fuzheng.log
export PATH="/usr/bin:/bin:/c/Windows/System32:/c/Windows:$PATH"
ROOT="/d/work/Ontology/TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox/OntologyMachine"
JAVA="/c/Program Files/Java/jdk-26.0.1/bin/java.exe"
cd "$ROOT"
"$JAVA" "@D:/work/Ontology/TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox/OntologyMachine/run_app.args" > "$ROOT/app_run_fuzheng.log" 2>&1
echo "APP_EXIT=$?"
