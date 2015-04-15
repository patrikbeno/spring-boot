#!/bin/bash
file=~/.springboot/springboot.jar
file=$(which cygpath > /dev/null && cygpath -w $file || realpath $file)
java -jar $file $*
