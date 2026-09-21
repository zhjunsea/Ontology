#!/usr/bin/env bash
# 直接以 classworlds 启动器调用 Maven（绕开损坏的 mvn shim）
export PATH="/usr/bin:/bin:/c/Windows/System32:/c/Windows:$PATH"
MVN_HOME="D:/work/Ontology/apache-maven-3.9.16"
JAVA="/c/Program Files/Java/jdk-26.0.1/bin/java"
"$JAVA" \
  -classpath "$MVN_HOME/boot/plexus-classworlds-2.11.0.jar" \
  "-Dclassworlds.conf=$MVN_HOME/bin/m2.conf" \
  "-Dmaven.home=$MVN_HOME" \
  "-Dmaven.multiModuleProjectDirectory=$PWD" \
  org.codehaus.plexus.classworlds.launcher.Launcher "$@"
