#!/usr/bin/env sh

set -e

target_repo="$1"
if [ -z "$target_repo" ]; then
  echo "target repository is not specified"
  exit 1
fi

script_dir="$(realpath "$(dirname "$0")")"
repositories_root_dir="$(realpath "$script_dir/..")"

echo "Compiling workspace generator dependencies..."

"$repositories_root_dir/dbeaver-common/mvnw" install \
    -T1C \
    -q \
    -f "$script_dir/aggregate"

"$repositories_root_dir/dbeaver-common/mvnw" package \
    -T1C \
    -q \
    -f "$script_dir/pom.xml" \
    exec:java \
    -Dexec.args="-eclipse.version \${eclipse-version} -updateWorkspace -config $target_repo/osgi-app.properties -projectsFolder $repositories_root_dir -eclipse $repositories_root_dir/dbeaver-workspace/dependencies -output $repositories_root_dir/dbeaver-workspace/products/"
