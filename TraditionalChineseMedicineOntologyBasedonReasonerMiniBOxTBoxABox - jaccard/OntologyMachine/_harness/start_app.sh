#!/usr/bin/env bash
# 启动 TCM 应用（Zeebe Worker），输出到 app_run_v4.log
ROOT="/d/work/Ontology/TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox/OntologyMachine"
JAVA="/c/Program Files/Java/jdk-26.0.1/bin/java.exe"
cd "$ROOT"
"$JAVA" "@D:/work/Ontology/TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox/OntologyMachine/run_app.args" > "$ROOT/app_run_v4.log" 2>&1
echo "APP_EXIT=$?"
