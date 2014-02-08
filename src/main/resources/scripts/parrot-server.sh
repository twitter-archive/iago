#!/bin/sh
#
# launch the parrot server

APP_NAME="server"
CONFIG="config/target/parrot-server.scala"
MAIN_CLASS="com.twitter.parrot.server.ServerMain"

HEAP_OPTS="-Xmx#{serverXmx}m -Xms#{serverXmx}m -XX:NewSize=512m"
GC_OPTS="-XX:+UseConcMarkSweepGC -verbosegc -XX:+PrintGCDetails -XX:+PrintGCTimeStamps -XX:+PrintGCDateStamps -XX:+UseParNewGC -Xloggc:gc-server.log"
#DEBUG_OPTS="-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=y"

# see https://confluence.twitter.biz/display/RUNTIMESYSTEMS/Linux+Perf+Support

# PROFILE_OPTS='-agentlib:perfagent'

JAVA_OPTS="-server $GC_OPTS $HEAP_OPTS $PROFILE_OPTS $DEBUG_OPTS"

. scripts/common.sh "$@"
