#!/bin/bash
# Usage: TC_SCRATCH=<C:/ dir> bash docs/tools/spotbugs.sh [--update]
#
# SpotBugs over src, held to "no new findings": compiles src into $TC_SCRATCH/spotbugs, runs SpotBugs at every rank,
# and compares with docs/tools/spotbugs-baseline.txt (spotbugs-check.py says how).  Exits 1 on a new finding.  About a
# minute.  --update rewrites the baseline once findings are fixed, or when a new one is judged harmless.
#
# Neither tool is in the repository.  SpotBugs needs Java 11 or later: TC_JAVA21 names a java (default: the one that
# ships with Android Studio), and TC_SPOTBUGS its folder (default C:/Users/adamo/tools/spotbugs-4.9.3, unpacked from
# https://github.com/spotbugs/spotbugs/releases/download/4.9.3/spotbugs-4.9.3.zip).  The classes are compiled with the
# project's Java 8 javac.
set -u

OUT="${TC_SCRATCH:?TC_SCRATCH must name a scratch folder}/spotbugs"
SPOTBUGS="${TC_SPOTBUGS:-C:/Users/adamo/tools/spotbugs-4.9.3}"
JAVA21="${TC_JAVA21:-/c/Program Files/Android/Android Studio/jbr/bin/java}"
JAVAC="${TC_JAVAC:-/c/Program Files/Java/jdk1.8.0_361/bin/javac}"
LIBS="resources/flatlaf-3.7.2.jar;resources/json-20260814.jar"

if [ ! -f "$SPOTBUGS/lib/spotbugs.jar" ]; then echo "no SpotBugs in $SPOTBUGS (TC_SPOTBUGS)"; exit 1; fi

rm -rf "$OUT"
mkdir -p "$OUT/classes"

find src -name "*.java" > "$OUT/files.txt"

# -g: SpotBugs names fields and locals from the debug information
if ! "$JAVAC" -nowarn -encoding UTF-8 -g -d "$OUT/classes" -cp "src;$LIBS" @"$OUT/files.txt" 2> "$OUT/javac.log"
then
    echo "src did not compile; see $OUT/javac.log"
    exit 1
fi

"$JAVA21" -Xmx3g -jar "$SPOTBUGS/lib/spotbugs.jar" -textui -effort:max -low -xml:withMessages -output "$OUT/spotbugs.xml" \
    -auxclasspath "$LIBS" -sourcepath src "$OUT/classes" > "$OUT/spotbugs.log" 2>&1

python docs/tools/spotbugs-check.py "$OUT/spotbugs.xml" "$@"
