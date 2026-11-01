#!/bin/bash
# Usage, from the repository root: TC_SCRATCH=<C:/ dir> bash docs/tools/coverage.sh <base ref> [--report-only]
#
# A battery with the JaCoCo agent in every test JVM, then the lines of src/ changed since <base ref> (the last
# release's dev commit, say) that no test ran: $TC_SCRATCH/coverage/changed-uncovered.md, with JaCoCo's HTML report in
# html/ beside it.  It measures HEAD: uncommitted edits are not in it.  TC_LANES, TC_CLASSES and the rest pass through to
# battery.sh.  --report-only skips the battery and reports again from the exec files the last run left.
#
# THE RUN IS IN A THROWAWAY WORKTREE of HEAD (tc-coverage-tree in %TEMP%, or TC_COVERAGE_TREE), because JaCoCo cannot
# instrument TrainControlUI as written: its probes push the GUI Builder's initComponents past the JVM's 64 KB method
# limit, and the whole class goes unmeasured.  split-initcomponents.py cuts that method into a chain there, keeping every line number,
# so the report reads against the real file.  StartupSplash is left out of the agent, as in the 3.0.5 run.
#
# JaCoCo is not in the repository: TC_JACOCO names its lib folder (default C:/Users/adamo/tools/jacoco-0.8.13/lib,
# unpacked from https://repo1.maven.org/maven2/org/jacoco/jacoco/0.8.13/jacoco-0.8.13.zip).  The report needs the
# class files the battery ran, byte for byte, so they are compiled again exactly as battery.sh does (src and test
# together, its flags); JaCoCo prints "does not match" for any class where that failed.
set -u

BASE="${1:?usage: TC_SCRATCH=<dir> bash docs/tools/coverage.sh <base ref> [--report-only]}"
JACOCO="${TC_JACOCO:-C:/Users/adamo/tools/jacoco-0.8.13/lib}"
OUT="${TC_SCRATCH:?TC_SCRATCH must name a scratch folder}/coverage"
JAVA="${TC_JAVA:-/c/Program Files/Java/jdk1.8.0_361/bin/java}"
JAVAC="${TC_JAVAC:-/c/Program Files/Java/jdk1.8.0_361/bin/javac}"
REPO=$(pwd)
# Short, because the fixtures' paths pass Windows' 260-character limit under a scratch folder
TREE="${TC_COVERAGE_TREE:-$(cygpath -u "${TEMP:-/tmp}")/tc-coverage-tree}"

if [ ! -f "$JACOCO/jacocoagent.jar" ]; then echo "no JaCoCo agent in $JACOCO (TC_JACOCO)"; exit 1; fi
if [ ! -f "$REPO/docs/tools/battery.sh" ]; then echo "run this from the repository root"; exit 1; fi

if [ "${2:-}" != "--report-only" ]
then
    if [ -d "$TREE" ]; then git worktree remove --force "$TREE" 2>/dev/null || rm -rf "$TREE"; fi
    rm -rf "$OUT"
    mkdir -p "$OUT/exec"

    git worktree add --detach "$TREE" HEAD > /dev/null || exit 1
    python "$REPO/docs/tools/split-initcomponents.py" "$TREE/src/org/traincontrol/gui/TrainControlUI.java" || exit 1
    sh "$REPO/docs/tools/env.sh" "$TREE" "" "$OUT/scratch" > /dev/null || exit 1

    # One exec file per class: battery.sh puts the class name where %s stands
    (cd "$TREE" && TC_SCRATCH="$(cygpath -m "$OUT/scratch")" \
        TC_JVM_ARGS="-javaagent:$JACOCO/jacocoagent.jar=destfile=$(cygpath -m "$OUT/exec")/%s.exec,includes=org.traincontrol.*,excludes=org.traincontrol.gui.widgets.StartupSplash" \
        bash docs/tools/battery.sh)

    echo "battery exit $?"
fi

rm -rf "$OUT/classes" "$OUT/html"
mkdir -p "$OUT/classes"

CP=$(sed -n 's/^CP="\(.*\)"$/\1/p' "$TREE/docs/tools/one.sh" | head -1)

if ! (cd "$TREE" && find src test -name "*.java" > "$OUT/files.txt" &&
      "$JAVAC" -nowarn -encoding UTF-8 -d "$OUT/classes" -cp "$CP" @"$OUT/files.txt" 2> "$OUT/javac.log")
then
    echo "the coverage copy did not compile; see $OUT/javac.log"
    exit 1
fi

# From inside the exec folder, so the command line holds short names: 380 absolute paths overflow it
(cd "$OUT/exec" && "$JAVA" -jar "$JACOCO/jacococli.jar" merge *.exec --destfile "$OUT/merged.exec" > /dev/null) || exit 1

"$JAVA" -jar "$JACOCO/jacococli.jar" report "$OUT/merged.exec" --classfiles "$OUT/classes/org" \
    --sourcefiles "$TREE/src" --encoding UTF-8 --xml "$OUT/report.xml" --html "$OUT/html" 2>&1 | grep -i "not match" | head -5

python "$REPO/docs/tools/coverage-changed.py" "$OUT/report.xml" "$BASE" "$OUT/changed-uncovered.md"
