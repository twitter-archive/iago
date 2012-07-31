#!/bin/sh
#
# echo mesos startup script.

APP_NAME="echo"
MAIN_JAR="parrot-examples-1.0.jar"

APP_HOME=`pwd`
PIDFILE=$APP_HOME/$APP_NAME.pid
LOG_HOME=$APP_HOME

MAIN_CLASS="com.twitter.example.EchoServer"
HEAP_OPTS="-Xmx128m -Xms128m -XX:NewSize=64m"
GC_OPTS="-XX:+UseConcMarkSweepGC -verbosegc -XX:+PrintGCDetails -XX:+PrintGCTimeStamps -XX:+PrintGCDateStamps -XX:+UseParNewGC -Xloggc:$LOG_HOME/gc.log"
JAVA_OPTS="-server $GC_OPTS $HEAP_OPTS $PROFILE_OPTS"

# Used to set JAVA_HOME sanely if not already set.
function find_java() {
  if [ ! -z $JAVA_HOME ]; then
    return
  fi
  potential=$(ls -r1d /opt/jdk /System/Library/Frameworks/JavaVM.framework/Versions/CurrentJDK/Home /usr/java/default /usr/java/j* 2>/dev/null)
  for p in $potential; do
    if [ -x $p/bin/java ]; then
      JAVA_HOME=$p
      break
    fi
  done
}

find_java

case "$1" in

  # start-local is meant for development and runs your server in the foreground.
  start-local)
    ${JAVA_HOME}/bin/java ${JAVA_OPTS} -cp ${APP_HOME}/${MAIN_JAR} ${MAIN_CLASS}
  ;;

  start)
    echo "Starting Echo Server v1"
    ${JAVA_HOME}/bin/java ${JAVA_OPTS} -cp ${APP_HOME}/${MAIN_JAR} ${MAIN_CLASS} $2 $3 $4 $5 $6 $7 $8 $9 ${10}
    echo "done."
  ;;

esac

exit 0
