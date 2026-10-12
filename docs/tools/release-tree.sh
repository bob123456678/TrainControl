#!/bin/bash
# Writes a release commit: a development commit's tree, less what only development needs, on top of master.
#
#   bash docs/tools/release-tree.sh <development commit> <file holding the commit message>
#
# Left on development: everything under docs/ except the test runners in docs/tools, the store's catalogue tool,
# this script, the notes beside the guide's pictures, and the three test classes that check the tracker's records
# (with their lines in build.xml, which testEveryTestIsInTheBattery holds to the files).
#
# The tree is computed, not merged, so a release never conflicts: the commit's parents are master and the
# development commit, and its tree is the development tree less those paths.  The commit goes to the local branch
# `release`; master is not moved, and moving it, pushing and tagging are separate steps.  Nothing in the working
# tree is touched.
set -euo pipefail

DEV=$(git rev-parse --verify "${1:?a development commit}^{commit}")
MESSAGE=${2:?a file holding the commit message}
MASTER=$(git rev-parse --verify refs/heads/master)

RECORDS=(testEveryCitationResolves testTheRecordsCountTheStore testTheSuppliedManualTestFilesAreWhatTheySay)

OUT=()
while IFS= read -r path
do
    [ "$path" = docs/tools ] || OUT+=("$path")
done < <(git ls-tree --name-only "$DEV" docs/)
OUT+=(docs/tools/catalog-findings.py docs/tools/release-tree.sh assets/automation/README.md)
for test in "${RECORDS[@]}"; do OUT+=("test/regression/$test.java"); done

INDEX="$(git rev-parse --absolute-git-dir)/release-tree-index"
trap 'rm -f "$INDEX"' EXIT
export GIT_INDEX_FILE="$INDEX"

git read-tree "$DEV"
git rm -r -q --cached -- "${OUT[@]}"

names=$(IFS='|'; echo "${RECORDS[*]}")
BUILD=$(git show "$DEV:build.xml" | grep -vE "<test-one-class class=\"($names)\"/>" | git hash-object -w --stdin)
git update-index --cacheinfo "100644,$BUILD,build.xml"

TREE=$(git write-tree)
COMMIT=$(git commit-tree "$TREE" -p "$MASTER" -p "$DEV" -F "$MESSAGE")
git branch -f release "$COMMIT"

echo "release: $COMMIT"
git diff --stat=100 "$DEV" "$COMMIT" | tail -1
