#!/usr/bin/env bash

APP_HOME=$(pwd)

CLASSPATH="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"

JAVA_CMD="java"
if [ -n "$JAVA_HOME" ]; then
  JAVA_CMD="$JAVA_HOME/bin/java"
fi

exec "$JAVA_CMD" -classpath "\( CLASSPATH" org.gradle.wrapper.GradleWrapperMain " \)@"
