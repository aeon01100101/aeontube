#!/bin/bash
set -e
cd "$(dirname "$0")"
JDK="$HOME/workspace/java/jdk-17.0.20.1+1"
mkdir -p out
"$JDK/bin/javac" -encoding UTF-8 -d out ../app/src/main/java/com/aeontube/app/ParseUtil.java TestParseUtil.java
"$JDK/bin/java" -cp out TestParseUtil
