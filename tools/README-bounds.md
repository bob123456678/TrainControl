# Diagram bounds harness

Answers one question: **did anything on the diagram move that was not supposed to?**

It builds the real `LayoutGrid` for every page of a layout, at every tile size worth checking, and
writes out the bounds of every component. Two runs — one per build — are then diffed as bags of
rectangles, so "the labels drifted three pixels" is a measurement rather than an impression.

Written for OB-115, where FR-028's station pills moved four unrelated text labels by three pixels, and
kept for FR-028's horizontal centring, where the requirement was that nothing else move at all.

## Running it

```
javac -d <out> -cp <build>;<classpath> tools/Bounds.java
java -Dtraincontrol.anyReceivePort=true -cp <out>;<build>;<classpath> Bounds <layout folder> <dump dir>
python tools/compare-bounds.py <before dump dir> <after dump dir>
```

`<layout folder>` must be a **copy**. The harness points the window's layout preference at it and puts
the preference back afterwards, but the window WRITES to whatever it opens — see
`test/support/LayoutSandbox.java` and OB-111.

To get a "before", make a worktree at the commit you are comparing against and compile that:

```
git worktree add <path> <commit>
```

## What it caught, and what nearly fooled it

**It was not deterministic at first.** A tile's preferred size depends on whether its icon has
arrived, the icons decode on a pool, and so the same build measured twice disagreed about forty
tiles. Anything read from that would have been attributed to the change under test. It waits for
`tilesAreSettled` now.

**Run the control first.** Compare a build against itself before comparing it against anything else.
If that is not zero, nothing else the harness says means anything.

**The class name is not part of the answer.** FR-028 changed the caption class from `JLabel` to
`StationCaption`, which reordered every line of a sorted dump without moving a pixel.
`compare-bounds.py` normalises it.

## Reading the output

```
1___Main-30.txt          tiles differing: 0    labels differing: 34
      gone : Inner Loop                     at (390,120) 71x21
      new  : Inner Loop                     at (390,123) 71x21
```

`tiles differing: 0` is the line that matters most: the track itself did not move. A named label in
both `gone` and `new` moved; one in only `gone` was removed or renamed.
