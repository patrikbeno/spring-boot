#!/bin/bash
set -eu

repo="${1:-http://repo.greenhorn.sk}"
version="${2:-1.3.0.BUILD-SNAPSHOT}"

echo "Updating SpringBoot Launcher..."
cd $(dirname $0)

url=$(curl -sk --head "${repo}/service/local/artifact/maven/redirect?r=snapshots&g=org.springframework.boot&a=spring-boot-launcher&v=${version}&e=jar" | perl -n -e '/^Location: (.*)$/ && print "$1\n"' | sed 's/\r//')
curl -sk --remote-name $url
[ -L springboot.jar ] && rm springboot.jar
ln -s $(ls -1 spring-boot-launcher-*.jar | tail -n1) springboot.jar
ls -l springboot.jar
chmod 640 *.jar

wget -q -N https://raw.githubusercontent.com/patrikbeno/spring-boot/dist/springboot.sh
chmod a+x springboot.sh
