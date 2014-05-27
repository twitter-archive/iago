# common.sh

if [ -z $JAVA_HOME ]; then
  potential=$(ls -r1d /usr/lib/jvm/java-1.7.0-openjdk7 /opt/jdk /System/Library/Frameworks/JavaVM.framework/Versions/CurrentJDK/Home /usr/java/default /usr/java/j* 2>/dev/null)
  for p in $potential; do
    if [ -x $p/bin/java ]; then
      JAVA_HOME=$p
      break
    fi
  done
fi

export CLASSPATH="#{classPath}"

print_and_run() {
  echo "$@"
  eval "$@"
}

go() {
  print_and_run ${JAVA_HOME}/bin/java ${JAVA_OPTS} ${MAIN_CLASS} -f ${CONFIG} "$@"
}

VERB=$1
shift

case "$VERB" in

  start-local)
    go "$@"
    ;;

  start-mesos)
    echo launching a parrot $APP_NAME
    echo current working directory is $PWD
    go "$@" 2>&1
    ;;

  *)
    echo "unrecognized verb '$VERB'"
    exit 1

esac
