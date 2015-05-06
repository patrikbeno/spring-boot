#!/bin/bash
file=~/.springboot/springboot.jar
file=$(which cygpath > /dev/null 2>&1 && cygpath -w $file || realpath $file)
java -jar $file $*
