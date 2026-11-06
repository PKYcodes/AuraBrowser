#!/bin/sh
#
# Gradle start up script for POSIX generated for AuraBrowser
#
APP_HOME=$( cd "${0%/*}" && pwd -P ) || exit
DEFAULT_JVM_OPTS='"-Xmx64m" "-Xms64m"'
CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar

if [ -n "$JAVA_HOME" ] ; then
    JAVACMD="$JAVA_HOME/bin/java"
else
    JAVACMD="java"
fi

if [ ! -f "$CLASSPATH" ]; then
    if command -v gradle >/dev/null 2>&1; then
        exec gradle "$@"
    else
        echo "ERROR: gradle-wrapper.jar not found and system 'gradle' is not installed." >&2
        echo "In GitHub Actions, gradle/actions/setup-gradle@v4 automatically provisions Gradle 8.10.2." >&2
        exit 1
    fi
fi

exec "$JAVACMD" $DEFAULT_JVM_OPTS $JAVA_OPTS $GRADLE_OPTS "-Dorg.gradle.appname=gradlew" -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
