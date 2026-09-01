#!/usr/bin/env bash

set -euo pipefail

if [ "$#" -lt 1 ] || [ "$#" -gt 2 ]; then
  echo "Usage: $0 <github-ticket-url> [repositories-root-dir]" >&2
  exit 2
fi

ticket_url="$1"
script_dir="$(realpath "$(dirname "$0")")"
repositories_root_dir="$(realpath "${2:-$script_dir/..}")"

if [ -z "${GITHUB_TOKEN:-}" ] && [ -z "${GH_TOKEN:-}" ]; then
  if command -v gh >/dev/null 2>&1; then
    export GITHUB_TOKEN="$(gh auth token)"
  else
    echo "GITHUB_TOKEN or GH_TOKEN environment variable is required" >&2
    exit 2
  fi
fi

if [ -x "$repositories_root_dir/dbeaver-common/mvnw" ]; then
  mvn_cmd="$repositories_root_dir/dbeaver-common/mvnw"
else
  mvn_cmd="mvn"
fi

find_repository_dir() {
  local repository="$1"
  local repository_name="${repository#*/}"
  local candidate="$repositories_root_dir/$repository_name"

  if [ -d "$candidate/.git" ] && repository_matches_origin "$candidate" "$repository"; then
    printf '%s\n' "$candidate"
    return 0
  fi

  while IFS= read -r candidate; do
    if repository_matches_origin "$candidate" "$repository"; then
      printf '%s\n' "$candidate"
      return 0
    fi
  done < <(find "$repositories_root_dir" -mindepth 1 -maxdepth 1 -type d -exec test -d '{}/.git' \; -print)

  return 1
}

repository_matches_origin() {
  local directory="$1"
  local repository="$2"
  local origin_url

  origin_url="$(git -C "$directory" remote get-url origin 2>/dev/null || true)"
  case "$origin_url" in
    "https://github.com/$repository"|"https://github.com/$repository.git"|"git@github.com:$repository.git"|"ssh://git@github.com/$repository.git")
      return 0
      ;;
    *)
      return 1
      ;;
  esac
}

checkout_branch() {
  local directory="$1"
  local branch="$2"

  if git -C "$directory" show-ref --verify --quiet "refs/heads/$branch"; then
    git -C "$directory" checkout "$branch"
    return 0
  fi

  if git -C "$directory" ls-remote --exit-code --heads origin "$branch" >/dev/null; then
    git -C "$directory" fetch origin "refs/heads/$branch:refs/heads/$branch"
    git -C "$directory" checkout "$branch"
    return 0
  fi

  echo "Branch '$branch' was not found on origin in $directory" >&2
  return 1
}

branches="$(
  "$mvn_cmd" -q \
    -f "$script_dir/pom.xml" \
    compile >&2

  java \
    -cp "$script_dir/target/classes" \
    org.jkiss.tools.rcplaunchconfig.github.GitHubTicketBranchResolver \
    "$ticket_url"
)"

if [ -z "$branches" ]; then
  echo "No branches are attached to $ticket_url"
  exit 0
fi

while IFS=$'\t' read -r repository branch; do
  [ -n "$repository" ] || continue
  repository_dir="$(find_repository_dir "$repository" || true)"
  if [ -z "$repository_dir" ]; then
    echo "Repository '$repository' is not cloned under $repositories_root_dir" >&2
    exit 1
  fi

  echo "Checkout $repository -> $branch"
  checkout_branch "$repository_dir" "$branch"
done <<< "$branches"
