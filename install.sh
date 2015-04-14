#!/bin/bash

set -eu

case $(whoami) in
	root)	dst="/etc/springboot" ;;
	*)		dst="$HOME/.springboot" ;;
esac

echo "Creating $dst directory..."
mkdir -p $dst

repo="https://raw.githubusercontent.com/patrikbeno/spring-boot"
branch="dist"

echo "Downloading updater..."
wget -q -O $dst/update.sh $repo/$branch/update.sh

echo "Updating launcher..."
chmod a+x $dst/update.sh
$dst/update.sh

echo "Downloading launcher script..."
wget -q -O $dst/springboot.sh $repo/$branch/springboot.sh
chmod a+x $dst/springboot.sh

if [[ "$(whoami)" = "root" ]]; then
	echo "Installing /usr/local/bin/springboot"
	[ -L /etc/springboot/springboot.sh ] && rm /etc/springboot/springboot.sh
	ln -s /etc/springboot/springboot.sh /usr/local/bin/springboot
else
	echo "Skipping installation in /usr/local/bin. Must be root, not $(whoami)"
fi


