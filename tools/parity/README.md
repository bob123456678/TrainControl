# Autonomy parity: 2.8.1 against 3.0.0

Does the diagram-derived graph offer at least the routes the hand-built one did?

```bash
sh tools/parity/setup-env.sh && sh tools/parity/run.sh
```

The first builds `../traincontrol-parity/`; the second writes `out/report.md`. Neither touches
`cs2_sample_layout` - each engine gets its own copy, because that folder is the real railway and
autonomy writes to it every time a train moves.

## What it does

| | |
|---|---|
| `setup-env.sh` | downloads the 2.8.1 release jar, copies the 3.0.0 jar from `dist/` **with its `lib/`**, copies `LocDB.data`, `UIState.data` and the layout once per engine, and compiles the driver against **both** jars |
| `BuildDiagramSetup.java` | 3.0.0 only: emits the diagram-derived setup as the same JSON the old engine reads |
| `ParityDriver.java` | places four DCC trains (901-904) on BottomMainA/B/C and BottomInner and records every route each may take, with its lock set |
| `compare.py` | the three questions, and the report |

`ParityDriver` compiles against 2.8.1 as well as against the current tree, and that is the point: it
uses no API newer than 2.8.1, so a difference in the output is a difference in the engine rather than
in the questions asked of it. If it ever stops compiling against the old jar, the comparison has
stopped being like for like.

## Three things that are easy to get wrong here

**The two graphs do not share a namespace.** 2.8.1 has one Point per station; 3.0.0 splits each by
facing - `BottomMainB (westbound, reverse)`. `BottomMainA` does not exist in the new graph. Facings of
one station are unioned before comparing, so a route counts as offered if it is available from *any*
facing: stricter fails a train for being pointed the wrong way, looser credits a route no real train
could take.

**Edge sequences are not comparable.** Edges are named after their endpoints, so splitting renames
them all - and 3.0.0 carries intermediate points 2.8.1 never had:

```
2.8.1   BottomMainPost -> RampUp
3.0.0   BottomMainPost (northbound) -> 1 - Main 6,1 (westbound) -> RampUp
```

Compared literally that reads as a lost route, and the first version of this report announced 17 of
them while simultaneously reporting that every destination survived. Routes are compared by the
**named places** they pass through, with facings and coordinate-named squares dropped.

**Simulate mode does not move trains** - it says so: *"Auto layout development / simulation mode
enabled. Trains will not run."* So the timing Adam asked for records nothing here, and the evidence is
enumeration instead: `getPossiblePaths` answers "which routes will this engine consider" exactly, for
every train and facing, where a timed run only ever samples. Pass a `runSeconds` argument to
`run.sh` if you point it at something that does move trains.

## Reading the report

1. **Destinations** - can each train still get everywhere it could? A loss here is part of the railway
   3.0.0 cannot use.
2. **Routes** - is each individual route still offered? A destination can survive while three of the
   four ways of reaching it have gone, which is what over-eager locking looks like.
3. **Concurrency** - can pairs that used to run together still do so? Computed from lock sets: two
   routes coexist exactly when the edges they lock do not intersect.

Reversing points are not counted against 3.0.0 as destinations - Adam: *"you may ignore reversing
stations in the 2.8.1 setup, as these are used for parking."* They still count as places a route may
pass through.

`compare.py` exits non-zero when the superset claim fails, so this can gate something later.

## Helper points

The hand-built graph carries points that exist only to make the modelling work - `TopMainR1Bypass`,
`BottomSecondaryPre`, `BottomExitVIrt` - and the derived graph does not need them. Counting their
absence described the modelling rather than the railway, so routes are compared only on places **both**
graphs know about, dropped symmetrically. What that still catches, deliberately: a place both graphs
have that a route no longer passes through.

## Differences already ruled on

**Routing through TopMainR1 / TopMainR2 rather than past them is EXPECTED.** 2.8.1 went
`TopMainR1Pre -> TopMainPost`, around the platform; 3.0.0 goes through it. Adam: "The TopMainR1 pre
bypass is expected, because previously, we had no way of switching the guard signal green, but now we
do." The old graph needed a bypass because the guard signal could not be set; the derived graph can
set it, so the platform road is available and the bypass is not a separate place any more. Do not
re-open this one - it is the new engine being able to do something the old one could not.

**Helper points absent from the derived graph are expected** - `TopMainR1Bypass`, `BottomSecondaryPre`,
`BottomExitVIrt` and the rest exist only to make the hand-built model work. Where the old graph had
both a place and its parking twin - `TopMainR0` and `TopMainR0Park` - the derived graph needs only the
one it kept. Routes are compared on places both graphs know, so these cost nothing either way.

**Parking destinations that exist but are not offered** are expected too: reversing points are parking,
and `pickPath` excludes them from fully autonomous selection by design.

Still open: `BottomInner -> Tunnel` has lost its alternative via `BottomCrossover` and `TunnelPre`, and
both of those exist in the derived graph.

## PathPreferenceProbe

3.0.0 only, because the preference is. It asks each routing-logic setting what it would actually
choose, rather than reading the code and inferring. Two ways the setting can silently not apply, both
worth knowing:

- `Layout.pathPreference` is **static** and defaults to RANDOM, and the only thing that loads the saved
  value into it is the menu builder in the window. Anything running autonomy without building that
  menu - a script, an example - is on RANDOM whatever is saved.
- Length-based options rank by `lengthOf`, so on edges with no length they all score zero and
  SHORTEST_LENGTH and LONGEST_LENGTH become the same setting. 18 of 132 edges in the derived graph
  carry a length.
