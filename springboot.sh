#!/bin/bash
file=~/.springboot/springboot.jar
file=$(cygpath -w $file || realpath $file)
java -jar $file $*
