#!/bin/sh
#
# launch the parrot server

APP_NAME="server"
CONFIG="config/target/parrot-server.scala"
MAIN_CLASS="com.twitter.parrot.server.ServerMain"

HEAP_OPTS="-Xmx#{serverXmx}m -Xms#{serverXmx}m -XX:NewSize=512m"
GC_OPTS="-XX:+UseConcMarkSweepGC -verbosegc -XX:+PrintGCDetails -XX:+PrintGCTimeStamps -XX:+PrintGCDateStamps -XX:+UseParNewGC -Xloggc:$LOG_HOME/gc-server.log"
#DEBUG_OPTS="-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=y"
JAVA_OPTS="-server $GC_OPTS $HEAP_OPTS $PROFILE_OPTS $DEBUG_OPTS"

. scripts/common.sh "$@"
