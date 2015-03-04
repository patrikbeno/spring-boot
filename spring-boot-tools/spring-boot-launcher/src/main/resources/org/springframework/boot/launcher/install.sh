#!/bin/bash
set -eu
mkdir ~/.springboot
wget -O ~/.springboot/update.sh https://raw.githubusercontent.com/patrikbeno/spring-boot/MvnLauncher/spring-boot-tools/spring-boot-launcher/src/main/resources/org/springframework/boot/launcher/update.sh
~/.springboot/update.sh
