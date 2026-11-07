#!/usr/bin/env sh

set -e

mvn_args="-T1C -Djdk.xml.maxGeneralEntitySizeLimit=2097152 -Djdk.xml.totalEntitySizeLimit=2097152"
target_repo="$1"
if [ -z "$target_repo" ]; then
  echo "target repository is not specified"
  exit 1
fi

script_dir="$(realpath "$(dirname "$0")")"
repositories_root_dir="$(realpath "$script_dir/..")"

projects=":idea-rcp-launch-config-generator"
if [ -f "$repositories_root_dir/checkstyle-nullability-annotations/pom.xml" ]; then
  projects="$projects,../../checkstyle-nullability-annotations"
fi

echo "Build and run workspace generator"
# shellcheck disable=SC2086
"$repositories_root_dir/dbeaver-common/mvnw" \
    $mvn_args \
    -DskipTests=true \
    -f "$script_dir/aggregate/pom.xml" \
    -pl "$projects" -am verify -Pgenerate-workspace \
    -Dexec.args="-eclipse.version \${eclipse-version} -updateWorkspace -config $target_repo/osgi-app.properties -projectsFolder $repositories_root_dir -eclipse $repositories_root_dir/.workspace/dependencies -output $repositories_root_dir/.workspace/products/"
