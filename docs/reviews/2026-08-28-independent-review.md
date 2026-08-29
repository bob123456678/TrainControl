# Independent review - the Central Station ingestion path

**Status:** open

**Prefix for citing this document: `IC`.** The brief for this pass asked for `IND`, but `IND` already
names the 2026-07-26 independent review - it never declared a prefix of its own, and
[2026-07-cycle-summary.md](2026-07-cycle-summary.md) cites its findings as `IND-B1`, `IND-M4`,
`IND-D5`. Reusing it would recreate exactly the collision the prefix convention exists to prevent, so
this document declares `IC` (Independent, Central-station ingestion) instead and says so here rather
than deciding it silently.

**Version reviewed:** HEAD `eac0e392`, branch `autonomy-diagram-r0`. **Reviewed:** 2026-08-28.
**No code was changed as part of this review.**

## Scope, and why this scope

Deliberately unguided, so it went where the guided passes said they had not. The 2026-08-25
independent pass ([FB](2026-08-25-independent-fable.md)) lists its own gaps: "`LayoutDiagram`,
`CS2File`, `NetworkProxy` and the CS3 backup changes - their tests ran green but were not read line
by line." This pass took the data-ingestion half of that list and read it line by line:

- `src/org/traincontrol/marklin/file/CS2File.java` - all 2,583 lines: the CS2 flat-file parsers, the
  CS3 JSON parsers, the layout page importer, the backup download path.
- `src/org/traincontrol/marklin/MarklinControlStation.java` - `syncWithCS2` and `syncLayouts`, the
  consumers that turn parsed records into the live databases.
- `src/org/traincontrol/marklin/udp/NetworkProxy.java` - all 306 lines, the UDP transport to the
  physical railway.

Wrong behaviour here is silent data loss against the real railway's configuration, which is why it
was chosen over another pass at the autonomy code. Note the prior coverage this overlaps:
[RS-D4](2026-08-02-pre-release-sweep.md) read `CS2File` end to end on 2026-08-02 and recorded it
clean, and [SWC](2026-07-29-switch-command-review.md) reworked both route importers' three-way
handling. The findings below are in code both of those passes summarized - which is calibration data
for how much an "otherwise clean" verdict covers, not a criticism of either.

**Method.** Reading, plus real-data checks wherever a claim depended on what actual files contain:
the CS2 fixtures (`test/fahrstrassen.cs2`, `test/magnetartikel.cs2`), both CS3 locomotive fixtures
(136 and 154 records), both CS3 route fixtures, and the real layout's route file
(`Oles kreds/config/fahrstrassen.cs2`). No tests were run and no builds made (shared build
directory; several agents concurrent). Every finding names the layer that enforces the rule and
traces whether a caller reaches it.

---

## A - high

None found. The one candidate that would have been an A (B1, if it fired on this railway) does not
reach Adam's configuration: his layout is CS2-format, and the defect is in the CS3-only branch.

## B - medium

| | Finding | Disposition |
|---|---------|-------------|
| **B1** | CS3 route import stores every condition S88 with inverted polarity | declined by Adam 2026-08-28 |

### IC-B1. CS3 condition S88s are imported with inverted polarity

**Declined by Adam, 2026-08-28:** "I don’t think it’s an inconsistency if you read carefully.
We can test it again later." The prose below is left as it was written - it is the record of what was
believed at the time, and the disposition above is the one place the outcome lives.

Verified independently before the ruling, and recorded here so a later reader knows what WAS checked:
the trigger branch three lines above carries `// value key won’t be present if unoccupied`, and the
CS2 branch passes `s88Status != 0` for the same argument, so `true` there means occupied. That is the
whole of the apparent contradiction; it is not evidence about what the Central Station actually sends,
which is what the ruling turns on. Neither CS3 fixture in this repository contains a condition S88 -
the only s88 item in either is a trigger (`id 39`, `mode 2`, no `value`) - so nothing here could settle
it either way.

`CS2File.parseRoutesCS3`, [CS2File.java:1440](../../src/org/traincontrol/marklin/file/CS2File.java):

```java
r.addConditionS88(item.getInt("id"), !item.has("value"));
```

The boolean is the feedback state the condition requires: `Route.evaluate`
([Route.java:305](../../src/org/traincontrol/base/Route.java)) tests
`getFeedbackState(...) == rc.getSetting()`, and `getFeedbackState` returns `MarklinFeedback.isSet()`
- true means occupied. So this line says: **value key absent → the route requires the sensor
occupied**.

Three lines up, the same method reads the same key on the same item type the other way. The trigger
branch (lines 1419-1429) documents `// value key won't be present if unoccupied` and maps
value-present → `OCCUPIED_THEN_CLEAR`. And the CS2 branch - the one exercised against this
repository's real railway for years - is consistent with the trigger reading, not the condition
reading: `s88Ein` present (Ein = on/occupied) → `OCCUPIED_THEN_CLEAR`
(line 780-783), and a condition's `hi=1` → require occupied
(`addConditionS88(conditionS88, s88Status != 0)`, lines 836/852, `hi` defaulting to 1). In the CS2
vocabulary, key-present/1 = occupied for both trigger arming and condition requirement. Applying the
same vendor semantics to the CS3's `value` key, the condition line should pass `item.has("value")`,
not its negation.

What it costs, where it fires: the standard use of a condition - every condition in the real
`fahrstrassen.cs2` is `hi=0`, "only fire while this block is FREE" - would be stored on a CS3 as
value-absent and imported as "only fire while this block is OCCUPIED". An imported conditional route,
once enabled (reachable: `applyAutonomyRouteActivations` calls `r.enable()` at
[MarklinControlStation.java:849](../../src/org/traincontrol/marklin/MarklinControlStation.java) for
routes named in an autonomy config, locked or not), then refuses to fire while its guarded block is
free and fires while the block is occupied - the reverse of the interlock the operator built.

Why B and not A, and what a fix needs first:

- CS3-only, condition-S88-only. Adam's railway is CS2-format; the CS2 branch is correct.
- **The polarity claim rests on vendor consistency plus the method's own trigger comment, not on an
  observed CS3 file.** Neither CS3 route fixture contains a condition S88 (both hold exactly one
  route with one s88 item, the trigger - checked), and no test covers condition polarity. Per
  "distinguish could-happen from does-happen": the inversion is certain relative to the CS2
  semantics; that the CS3 encodes conditions the same way as its own triggers is inference.
- Before fixing, obtain a real CS3 `automatics` JSON containing a conditional route (or have a CS3
  owner confirm the `value` key on a condition item), add it to the fixtures, and write the failing
  test against it - a test written from the same inference the fix is based on would agree with the
  fix whether or not it is right.

The line dates to commit `8349217e` ("Simplify logic", 2026-04-17) - long-untouched code, never
previously flagged (grep across `docs/reviews/` for `addConditionS88` / `item.has("value")` finds
only the July A5 loop-scope finding, which is about the CS2 branch and is fixed).

## C - low

| | Finding | Disposition |
|---|---------|-------------|
| **C1** | One malformed accessory record aborts the whole CS2 route+locomotive sync | open |
| **C2** | Two logging calls NPE on a parser built without a control station - unswept siblings of a recorded fix | open |
| **C3** | A route renamed on the Central Station keeps its stale name locally forever | open |

### IC-C1. `parseMags` is the one per-record parser without a per-record guard

`CS2File.parseMags` ([CS2File.java:697-731](../../src/org/traincontrol/marklin/file/CS2File.java))
guards `id`/`typ` against absence but parses with no per-record catch:
`Integer.parseInt(m.get("id"))` at line 715. A non-numeric `.id=` line throws
`NumberFormatException` out of `parseMags` → `getMagList(false)` → `parseRoutes()` (line 573) →
`syncWithCS2`'s outer catch ([MarklinControlStation.java:1383](../../src/org/traincontrol/marklin/MarklinControlStation.java)),
which abandons the **entire** import - every route and every locomotive - logging only
"dbSyncFailed".

This is precisely the failure class every sibling was hardened against, with the reasoning recorded
at each site: `parseRoutes` catches per route ("Letting this propagate would reach syncWithCS2's
outer catch and abandon the entire import"), `parseLocomotives` per locomotive, `parseLayout` per
page. Even the *other caller of the same method* is guarded: `syncLayouts` wraps `getMagList(true)`
in a try/catch and continues without accessory data
([MarklinControlStation.java:512-523](../../src/org/traincontrol/marklin/MarklinControlStation.java)).
Only the route-import call is bare.

C, not B, on the real-data rule: the file is machine-written by the Central Station and served over
HTTP - both fixtures and the real layout's copy carry clean numeric ids, and the unguarded call
never reads the hand-editable local copy (that is the guarded `getMagList(true)`). Worth fixing as a
trap - one `try` per `artikel` record, matching `parseRoutes` - not worth a changelog entry.

### IC-C2. `control.logf` at lines 707 and 760 - the sweep that fixed line 2346 missed its twins

`parseLayout` carries a fix with its reasoning recorded:
"Null-checked, as every other logging call in this class is - they all go through the null-safe
logMessage and this one did not. A CS2File built without a control station is a perfectly ordinary
thing to make" ([CS2File.java:2340-2350](../../src/org/traincontrol/marklin/file/CS2File.java)).
The premise of that comment is false: `parseMags` (line 707) and `parseRoutes` (line 760) still call
`control.logf` directly. A `CS2File(ip, null)` handed a mag file with a missing `id`/`typ`, or a
route file with a missing `id`/`item`/`name`, throws NPE from the error-reporting path itself - in
`parseRoutes` that NPE escapes the record guard (which catches only `NumberFormatException` and
`ArrayIndexOutOfBoundsException`) and kills the whole parse.

Unreachable in the application (the station always constructs the parser with `this`), reachable
from tests and the standalone-parse use the 2346 comment describes. This is the
"fix one site, sweep the siblings" pattern the July cycle recorded five times - noted here mostly so
the eventual fix sweeps all three, and so the comment at 2346 stops overclaiming.

### IC-C3. Renaming a route on the Central Station never propagates

`syncWithCS2`'s route import
([MarklinControlStation.java:1220-1262](../../src/org/traincontrol/marklin/MarklinControlStation.java))
re-imports a route when its *name* collides at a different id, or when its *content* (items, s88,
trigger, conditions - delays included, since `RouteCommand.equals` hashes the whole config map)
changed at the same id. A route whose id and content are unchanged but whose **name** changed on the
station matches neither branch: `hasName(newName)` is false, `hasId` is true, content compares
equal - so nothing happens, every sync, forever. TrainControl keeps displaying the old name; the
route still fires correctly by id and s88.

Cosmetic in effect (a stale label on a working route), so C. A fix is one more clause in the
changed-route test; the delete-and-re-add machinery it would trigger already exists and already
preserves the lock.

## D - not defects

Checks that came back clean, recorded so the reader knows what this pass actually covered.

| | Verdict |
|---|---------|
| **D1** | CS3 parser's throw-on-missing-key style: no real record misses a key |
| **D2** | Unconditional uid base subtraction in `parseLocomotivesCS3`: safe against all real uids |
| **D3** | `NetworkProxy` read end to end: clean |
| **D4** | CS3 id caches cannot go stale across syncs |
| **D5** | The `.S88Flag` fold-into-item oddity is load-bearing, and correct for the format |
| **D6** | Duplicate page names in the backup download: deliberate, and cannot occur on a real station |

### IC-D1. CS3 locomotive parsing would skip a record missing `icon`/`dectyp`/function keys - none exists

`parseLocomotivesCS3` reaches for `uid`, `name`, `icon`, `dectyp`, `internname` with `getString`,
and `typ`/`typ2`/`isMoment`/`dauer` with typed getters - any absent key throws `JSONException` and
the per-record catch drops the whole locomotive (a locomotive lost over a missing *icon*). Checked
against both fixtures - 290 locomotive records, v2.5 and v2.6 formats, multi-units included: every
record carries every key, including `dectyp` on multi-units (`"mm"` pre-2.6, `"trac"` from 2.6).
Guard-worthy someday; not a defect on any data this repository has ever seen. The `if (icon != null)`
check below the `getString` is dead (`getString` never returns null) and mildly misleading, nothing
more.

### IC-D2. The CS3 branch subtracts decoder bases unconditionally - verified safe

The CS2 branch guards its base subtraction (`if (address > MFX_MAX_ADDR)`), the CS3 branch does not
(`uid -= MFX_BASE` etc., lines 1617-1631). Checked both fixtures: every multi-unit uid is
≥ `0x2c01` (`MULTI_UNIT_BASE` is `0x2c00`), every mfx uid ≥ `0x4000`, every dcc uid ≥ `0xc000` - the
CS3 always reports offset uids, so the subtraction cannot go negative on real data. The asymmetry
with the CS2 branch is explained by what each parses: the CS2 file's `adresse` field is already
unoffset, its `uid` fallback is not, so only the CS2 branch meets both forms.

### IC-D3. `NetworkProxy` - clean

All 306 lines. Probed: the reader thread's interplay with `sendMessage`'s socket replacement (the
loop re-reads the volatile field and distinguishes closed-for-shutdown from replaced-after-failure);
the exact-length datagram guard against parsing a stale tail; the daemon/backoff/interrupt handling;
the deliberate non-close in the reader's `finally`. Every hazard found was already fixed with the
reasoning recorded at the site. One theoretical gap - a send after `stopListening` reopens the
socket with no reader attached, transmission alive and reception dead - requires using a control
station after tearing it down, which nothing does.

### IC-D4. The CS3 `magList`/`locList` caches are per-sync by construction

They are never invalidated - but `syncWithCS2` constructs a fresh `CS2File` on every call
(line 1164), so the caches live exactly one import. `refreshLayouts` reuses the old parser, but the
layout path never touches these caches.

### IC-D5. `.S88Flag` never matching the key regex is load-bearing

Recorded as an adjacent oddity under the July A5 finding: `.S88Flag` fails `^ \.[a-z]+$` (capitals),
so `lastKey` stays `item` and condition groups are appended into the item string. That is not a
dormant bug - it is how conditions reach `parseRoutes` at all: the route parser finds `kont`/`hi`
inside the `item` pieces (lines 831-845) and nowhere else. A "fix" to the regex would silently break
condition import. Left as-is is correct; a comment at the regex would be kind.

### IC-D6. Duplicate page names collapse to one file in the backup download - deliberate, unreachable

`downloadCS2Layout` writes each page to `gleisbilder/<sanitized-name>.cs2`, so two pages with one
name would leave only the second. `parseLayoutIndex`'s comment shows this was weighed ("collapsing
them here would silently drop one from every caller") and `layoutDB` documents duplicate names as
aliasing by decision. And a real Central Station cannot serve the case: it stores pages under the
same name-derived file paths this download uses, so two pages with one name cannot coexist on the
station either.

---

## What this pass did not cover

- `LayoutDiagram` (1,253 lines) - the *writer* half of the ingestion round-trip. Read only where
  `CS2File` referenced it (`pageIdOrPosition`). The FB gap list names it too, and it is still open.
- `CS2Message`/`CANMessage`/`CSDetect` - covered by [WP](2026-08-17-whole-project-review.md)
  (sign-extension, length-nibble, `getSubCommand` guard) and not re-read.
- `syncWithCS2`'s locomotive reconciliation beyond the route/name/address branches quoted - the
  address-update-deferred-while-running machinery has its own recent review trail.
- The CS3 backup additions (`CS3_mags.json` etc.) beyond reading `downloadCS2Layout` - the fetch
  paths were read, the restore side was not.
- Anything needing a display, the railway, or a running test battery.
