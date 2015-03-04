#!/bin/bash
set -eu
echo "Creating ~/.springboot directory..."
mkdir -p ~/.springboot

echo "Downloading updater..."
wget -q -O ~/.springboot/update.sh https://raw.githubusercontent.com/patrikbeno/spring-boot/MvnLauncher/spring-boot-tools/spring-boot-launcher/src/main/resources/org/springframework/boot/launcher/update.sh

echo "Updating..."
chmod u+x ~/.springboot/update.sh
~/.springboot/update.sh

