#!/bin/sh
#
# parrot feeder init.d script.
#
# Parrot, and all new java services, require the same directory structure
#   /usr/local/$APP_NAME should contain 'releases' directory and be able to create a symlink
#   /var/log/$APP_NAME
#

APP_NAME="feeder"
PROD_HOME=/usr/local/parrot/current

APP_HOME=`pwd`
PIDFILE=$APP_HOME/$APP_NAME.pid
LOG_HOME=$APP_HOME

MAIN_CLASS="com.twitter.parrot.feeder.FeederMain"
HEAP_OPTS="-Xmx2000m -Xms2000m -XX:NewSize=512m"
GC_OPTS="-XX:+UseConcMarkSweepGC -verbosegc -XX:+PrintGCDetails -XX:+PrintGCTimeStamps -XX:+PrintGCDateStamps -XX:+UseParNewGC -Xloggc:$LOG_HOME/gc.log"
DEBUG_OPTS="-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=8008"
JAVA_OPTS="-server $GC_OPTS $HEAP_OPTS $PROFILE_OPTS" #$DEBUG_OPTS"

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
    ${JAVA_HOME}/bin/java ${JAVA_OPTS} -cp ${APP_HOME}/${MAIN_JAR} ${MAIN_CLASS} -f ${APP_HOME}/config/dev-feeder.scala $2 $3 $4 $5 $6 $7 $8 $9 ${10}
  ;;

  start-mesos)
    echo "Starting Parrot Feeder with mesos-feeder.scala v2"
    ${JAVA_HOME}/bin/java ${JAVA_OPTS} -cp "${APP_HOME}/*:${APP_HOME}/libs/*" ${MAIN_CLASS} -f ${APP_HOME}/config/target/mesos-feeder.scala $2 $3 $4 $5 $6 $7 $8 $9 ${10}
    echo "done."
  ;;
esac

exit 0
