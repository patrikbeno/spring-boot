#!/bin/bash

set -eu
error() { echo "ERROR on line $1 : ${2:-?}"; exit ${3:-1}; }
trap 'error ${LINENO}' ERR

path() {
	case $(uname) in
		CYGWIN*)	cygpath -w "$1" ;;
		*)       echo "$1" ;;
	esac
}

# determine default JDK/JRE home directory
javahome="$(readlink -fe $(dirname $(readlink -fe $(which java)))/..)"

# determine application name and home folder
apphome="$(dirname $(readlink -fe $0))"

# determine app name: get base name, strip extension, if any http://goo.gl/W6ekZN
appname="${0##*/}"
appname="${appname%.*}"

# spring boot launcher binary (spring-boot-loader-$VERSION.jar)
springboot="$apphome/springboot.jar"

export JAVA_HOME="${JAVA_HOME:-$javahome}"

XX="
-XX:+CMSClassUnloadingEnabled
-XX:+ExplicitGCInvokesConcurrentAndUnloadsClasses
-XX:+UnlockDiagnosticVMOptions
-XX:+UnlockCommercialFeatures
-XX:+FlightRecorder
"
JMX="
-Dcom.sun.management.jmxremote
"

export JAVA_OPTS="${JAVA_OPTS:-}"

export PATH="$JAVA_HOME/bin:$PATH"

java $JAVA_OPTS $XX $JMX -jar "$(path $springboot)" --MvnLauncher.apphome="$apphome" --MvnLauncher.appname="$appname" $*
