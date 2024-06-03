#!/bin/bash

while getopts f: flag
do
    case "${flag}" in
        f) WORKING_DIR=${OPTARG};;
    esac
done
echo "$WORKING_DIR"
if [ -z "$WORKING_DIR" ]; then
  echo "No folder containing rcp_gen specified"
  exit 1
fi
# Run the Maven commands with the specified options
mvn -f "pom.xml" \
    package \
    -q \
    exec:java \
    -Dexec.args="-eclipse.version \${eclipse-version} -config $WORKING_DIR/rcp-gen.properties -projectsFolder $WORKING_DIR/../ -eclipse $WORKING_DIR/../dbeaver-workspace/dependencies -output $WORKING_DIR/../dbeaver-workspace/products/"
