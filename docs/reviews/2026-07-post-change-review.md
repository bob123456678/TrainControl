# TrainControl - Post-change review, July 2026

A second pass over the changes made during the July 2026 review, looking specifically for side effects
and wrong assumptions **in the fixes themselves** rather than for new defects in untouched code.

Scope: 21 commits, `b47b6ed~1..HEAD`, covering 22 production files and 14 test files. No code was
changed during this pass.

**Result: no defect was found in any of the changes.** Five observations are recorded below - four are
consequences that were not considered when the original fix was made, one is an inconsistency that was
deliberately left alone. None warrants a change; all are recorded so the next person does not have to
rediscover them. P4 was amended after publication - its original wording overstated the exposure and
recommended a guard that could never fire.

The "verified clean" section at the end lists the assumptions that were checked and hold. It is the more
useful half of this document: it says which reasoning has been tested and which has merely not been
contradicted yet.

---

## Status

| # | Observation | Severity | Action |
|---|---|---|---|
| P1 | C2 silently discards a persisted `arrivalFunc` / `departureFunc` equal to `numF` | Low | None - the value was already inert |
| P2 | `actuationConfirmed` is not carried across accessory re-creation, though `numActuations` is | Low | None - self-heals on the next command |
| P3 | `setLogicalAddress`'s feedback/link branch remains inconsistent with the parser and with `getLogicalAddress` | Low | None - deliberately outside B2's scope, recorded as a trap |
| P4 | `NodeExpression.parseLine` has no null guard | None | **No action - guarded three times over.** The original entry overstated this; see the amendment |
| P5 | The A7 lock-edge release rests on a layout convention, not an enforced invariant | Low | Already documented at the call site |

---

## P1. C2 silently discards a persisted arrival or departure function equal to `numF`

`base/Locomotive.java` - `setArrivalFunc`, `setDepartureFunc`

C2 tightened four bounds from `<= numF` to `< numF`. Two of those are the arrival and departure function
setters, and **those values are persisted**: `Point.toJSON` writes `arrivalFunc` into the autonomy file
and `Layout` restores it with `l.setArrivalFunc(locInfo.getInt("arrivalFunc"))`.

A file written before this change could therefore hold `arrivalFunc == numF`. On load the setter now
rejects it and the field stays null, where previously it was stored.

Behaviour is unchanged either way: `_setF` has always bounds-checked, so a function numbered `numF` never
fired - it was accepted and then silently ignored, which is what C2 was about. The only difference is
that the dead value is now dropped rather than carried, and `GraphLocAssign` shows "none" instead of an
out-of-range selection. That is arguably the correct outcome.

Recorded because it was not considered when the change was made. The change was assessed on the live
call path only, and the persistence path was not traced.

The other two bounds C2 changed - `getLocalFunctionImageURL` / `setLocalFunctionImageURL` - turn out to
be completely inert; see the verified-clean list.

## P2. Accessory re-creation preserves the actuation count but not the confirmation

`marklin/MarklinControlStation.java:2107`, `base/Accessory.java`

B7 added `actuationConfirmed`, set by any Central Station echo, so that path validation cannot pass on an
accessory the CS has never acknowledged.

`newAccessory` deliberately carries `numActuations` from any existing accessory at that address into the
replacement object. There is no equivalent for `actuationConfirmed`, so an accessory that `syncLayouts`
re-creates - the switch/signal type-flip path - reverts to unconfirmed.

Harmless in practice: `configureEdge` always transmits, so the next command produces an echo and
restores the flag. And re-creation only happens during a layout sync, not during a run. But the
asymmetry is arbitrary rather than reasoned, and if the confirmation is ever used somewhere that does not
command first, this becomes the failure mode.

## P3. The feedback and link branch of `setLogicalAddress` disagrees with the parser

`base/LayoutDiagramComponent.java`

B2 fixed the accessory branch so `address` holds the logical address, matching the parser's
`address = rawAddress / 2`. The `else` branch, used for feedback, link and route components, was left
alone: it sets `address` and `rawAddress` to the same value.

The parser, however, halves for **everything except `"fahrstrasse"`** - including feedback and link. And
`getLogicalAddress()` uses a third convention: `rawAddress` for feedback, `rawAddress + 1` for link,
`rawAddress / 2` otherwise.

So for a feedback component there are three different notions of its address depending on whether it was
parsed, edited, or read through `getLogicalAddress()`. This was noticed while fixing B2 and deliberately
left outside its scope, since B2's symptom was confined to accessories and the feedback paths were not
traced. Recorded here because someone "tidying" either side could easily break the other.

Not investigated: whether the disagreement is observable. `syncLayouts` reads `getRawAddress()` on one
feedback path and `getAddress()` on another, which is where to start if it is ever chased.

## P4. `NodeExpression.parseLine` dereferences a possibly-null parse result

`base/NodeExpression.java`

```java
RouteCommand rc = RouteCommand.fromLine(line, false);

if (!rc.isConditionCommand())   // NPE if fromLine returned null
```

`fromLine` returns null for a blank line, and in no other case - every non-blank line either returns a
command or throws.

**Amended.** This entry originally said the NPE was "one edit away" and suggested adding a guard. That
overstated it: the path is defended three times over, not once.

```java
String line = lines.get(i).trim();              // 2. trimmed on the way in
...
    if (!line.isEmpty())                        // 3. checked at the call site
    {
        stack.push(parseLine(line));
    }
```

together with `preprocessText`, which drops whitespace-only lines before the loop begins (1). Two of the
three are inside `fromTextRepresentation`, within fifty lines of each other, so removing both without
noticing is not a realistic edit.

**No guard should be added.** A fourth check inside `parseLine` could never fire, and dead defensive code
is not free: it tells the next reader that blank lines *do* reach this method, which is exactly the sort
of code-says-one-thing-truth-says-another problem that C15 was. That distinguishes this from the other
unreachable findings that *were* fixed - C15's guard was semantically wrong rather than merely redundant,
and C7's protection was diffuse across six call sites in three files rather than sitting in the enclosing
`if`.

Recorded, so that anyone removing the `!line.isEmpty()` check knows what it is holding up.

## P5. The A7 lock-edge release rests on a convention, not an invariant

`automation/Layout.java` - `unlockPath`

Releasing the skipped edge's lock edges is safe when a crossing is declared symmetrically, because the
crossing edge is then part of any conflicting path and `isPathClear` rejects it on that edge's own
occupancy flag. It does **not** hold for a hand-edited `autonomy.json` in which two edges name a third as
a lock edge without either traversing it, because `isPathClear` does not inspect lock edges and occupancy
is a flag rather than a count.

This is already stated in full at the call site. It is repeated here only because it is the one place in
the session's changes where correctness depends on how the data is written rather than on something the
code enforces.

---

## Verified clean

Assumptions that were checked during this pass and hold. Listed because "checked and correct" is
information, and because a future reviewer should not have to redo them.

**C3 - `getF` delegating to `validF`.** `validF` is not overridden anywhere; `MarklinLocomotive` calls it
but does not redefine it. The delegation cannot change behaviour in a subclass.

**C2 - the function-image accessors are inert.** `getLocalFunctionImageURL` / `setLocalFunctionImageURL`
are reached only from a combo box populated `for (i = 0; i < loc.getNumF(); i++)` and from loops over the
same range. `numF` is never passed to either, and since the only writer uses that same combo, no entry at
index `numF` could ever have been created. The `<=` was unreachable slack.

**C10 - the lock ordering is complete.** `speedMonitor` appears in `Locomotive.java` and nowhere else in
`src/`, so the enumeration of what can be held while acquiring it is exhaustive. Nothing takes
`speedMonitor` and then a locomotive lock; the reverse order already existed via
`MarklinLocomotive.setSpeed`.

**MarklinRoute's non-volatile monitor fields.** FR1 made `enabled` volatile but left `triggerType`, `s88`
and `conditions`, all of which the monitor thread reads. Not a hazard: their only mutators are
`CS2File.parseRoutes` and `parseRoutesCS3`, which operate on objects built by the three-argument
constructor - and that constructor sets `enabled = false`, `s88 = 0` and never calls `executeAutoRoute`,
so no monitor exists yet. Live routes are never mutated; `editRoute` deletes and recreates.

**B15 - the exception wrapper.** All three callers of `RouteCommand.fromLine` propagate `throws
Exception`; none catches a specific unchecked type that the wrapper would have swallowed.

**C7 - `Point.getX()` / `getY()`.** All six call sites are guarded, directly or transitively, by
`coordinatesSet()`. Returning 0 rather than throwing cannot change any current behaviour.

**C18 - `checkWebServer`.** `disconnect()` is in a `finally` guarded by a null check, and the connection
is assigned before any statement that can throw with it non-null.

---

## Method

Each observation above came from tracing a call path that the original fix did not trace - the
persistence path for C2, the re-creation path for B7, the sibling branch for B2. The pattern is that a
fix assessed on its live call path can still have a consequence on a save/load path or a sibling code
path that looks unrelated.

The one guard that would have caught P1 and P2 at the time: after changing a value's validation or
lifetime, ask where else that value is written and read - specifically whether it is persisted, copied
into another object, or restored.

P4 is a different lesson, and this document got it wrong on the first pass. It was written from the
knowledge that `preprocessText` filters blank lines, without reading the call site - which carries its
own `!line.isEmpty()` check. The entry recommended a guard that could never fire. It was corrected only
because someone asked whether to act on it, which forced the call site to be read.

Reviewing a finding is not the same as reviewing the code, and a review of one's own review needs the
same evidence standard as the original. The severity of an unreachable finding depends entirely on how
well defended the path is, and that cannot be judged without reading every guard on it.
