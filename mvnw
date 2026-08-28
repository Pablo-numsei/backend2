#!/bin/sh
# ----------------------------------------------------------------------------
# Apache Maven Wrapper startup batch script
# ----------------------------------------------------------------------------
MAVEN_PROJECTBASEDIR=$(cd "$(dirname "$0")" && pwd)
WRAPPER_JAR="$MAVEN_PROJECTBASEDIR/.mvn/wrapper/maven-wrapper.jar"
WRAPPER_LAUNCHER=org.apache.maven.wrapper.MavenWrapperMain

find_java() {
  if [ -n "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/java" ]; then
    echo "$JAVA_HOME/bin/java"
  else
    echo "java"
  fi
}

JAVA_EXE=$(find_java)

exec "$JAVA_EXE" \
  -classpath "$WRAPPER_JAR" \
  "-Dmaven.multiModuleProjectDirectory=$MAVEN_PROJECTBASEDIR" \
  $WRAPPER_LAUNCHER "$@"
