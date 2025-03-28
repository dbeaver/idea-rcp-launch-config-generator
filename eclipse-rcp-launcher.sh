#!/bin/sh

../dbeaver-common/mvnw package exec:java -q -Dexec.args="$1 $2 $3 $4 $5 $6 $7 $8 $9 $10"
