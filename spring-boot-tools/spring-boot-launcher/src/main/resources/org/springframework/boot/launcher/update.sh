#!/bin/bash
set -eu

echo "Updating SpringBoot Launcher..."
cd $(dirname $0)
url=$(curl -sI 'http://greenhorn.sk/nexus/service/local/artifact/maven/redirect?r=snapshots&g=org.springframework.boot&a=spring-boot-launcher&v=1.3.0.BUILD-SNAPSHOT&e=jar' | perl -n -e '/^Location: (.*)$/ && print "$1\n"' | sed 's/\r//')
wget -q -N $url
[ -L springboot.jar ] && rm springboot.jar
ln -s $(ls -1 spring-boot-launcher-*.jar | tail -n1) springboot.jar
ls -l springboot.jar
