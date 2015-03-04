#!/bin/bash
set -eu
mkdir -p ~/.springboot
wget -O ~/.springboot/update.sh https://raw.githubusercontent.com/patrikbeno/spring-boot/MvnLauncher/spring-boot-tools/spring-boot-launcher/src/main/resources/org/springframework/boot/launcher/update.sh
chmod u+x ~/.springboot/update.sh
~/.springboot/update.sh

