# Track diagram and editor rendering: where the time actually goes

Measured on 2026-08-19 against `cs2_sample_layout`, which is the biggest diagram this repository can
run against: **502 tiles on the pages autonomy uses, 58 reduced points.** The numbers come from
`test/testRenderingCost.java`, which prints them and is part of the battery — so this report can be
re-checked rather than believed.

Every figure below is a measurement, not an estimate.

---

## What it costs today

| Step | Cost | When it happens |
| --- | --- | --- |
| Build the tile graph from parsed pages | **0.15 ms** | Opening a diagram, every rebuild |
| Reduce the tile graph | **1.65 ms** | Same |
| Name every point (`uniqueNames`) | **0.01 ms** | Same |
| Map names back to squares (`tilesByName`) | **0.59 ms** | Same |
| Decode one tile image | **1.70 ms** | Once per *distinct appearance*, then cached |
| Decode a whole page, cold cache | **110 ms** for 65 decodes | First time a diagram is drawn |
| Build the Swing grid for one page | **613 labels for 384 cells** | Every time a page is drawn |

**The model side is not the problem.** A complete reduction of a five-hundred-tile layout takes under
two milliseconds. Anything that felt slow was never this.

**The image cache is doing its job.** 502 tiles need only **41 distinct appearances** — the cache is
keyed by type, state and orientation, not by tile — so a page decodes about one image for every twelve
squares it shows.

---

## The stale concern, closed

The manual test plan carried this, deferred out of the disposition audit's C6:

> **The station-label rebuild is roughly cubic on the feedback path.** `AutonomyBuilder` and
> `uniqueNames()` are rebuilt per point per feedback event.

**That is no longer true, and the fix is already in.** `AutonomySession.getStationIndex()` returns a
cached, eagerly-derived `StationIndex`, and `updateStationLabels` reaches it through
`tileForPointName` → `squareOf` — a map read. `uniqueNames` measures 0.01 ms and is not on the
feedback path at all. The entry should be struck; it is left here so the next person to find it knows
it was checked rather than forgotten.

---

## Where the time really is: Swing, not the model

110 ms of decoding and 2 ms of model work do not account for a diagram that takes a noticeable moment
to appear. What is left is the Swing tree: **502 `LayoutLabel` components**, each a `JLabel` in a
`GridBagLayout`, each applying its icon through a posted `invokeLater`.

Two things were measured about that, and one of them is a surprise.

### 1. The grid builds about 1.6 labels for every cell it has

Measured on the sample layout's largest page: **384 cells, 613 `LayoutLabel` constructions, 696 icon
applications.**

The construction loop itself is correct — one label per cell, either the real tile or the dummy the
GridBagLayout fix needs at the last row and column. So the extra 60% is built somewhere else on the
same path, and 696 applications against 613 labels says some labels apply their image more than once
as well.

That overhead is the largest single lever on this page. Everything else measured here is under two
milliseconds; this is hundreds of Swing components built and thrown away, each one decoding or
cache-reading an image on the way.

**Recommended next piece of work**, and not a freebie: it means understanding where the extra
constructions come from before changing anything. `testRenderingCost.testLabelsBuiltPerCell` prints
the ratio and fails above two per cell, so the number cannot drift without somebody noticing.

*A correction worth recording:* the first draft of this report said "five times more labels than
cells", from a diagnostic that was counting icon applications across five renders rather than
constructions. The real ratio is 1.6. The conclusion — that this is the main lever — survives; the
number did not, and a report that keeps its conclusion when its evidence changes is worth less than
one that says so.

### 2. One `invokeLater` per tile — and why the obvious fix is NOT free

`LayoutLabel.setImageOnEDT` posts an `invokeLater` for every tile, including cache hits. For a
five-hundred-tile page that is five hundred queued events, all of them deferred until after the grid
has finished building. It is the two-phase draw the readme describes: the text labels appear, and the
track arrives a moment later.

The obvious fix is to apply the icon inline when already on the event thread. **It was tried, and it
is wrong.** With it in place:

- `testAnExportedDiagramIsNotBlank` failed reproducibly.
- Instrumentation showed the inline branch running 323 times across the export test's renders, each
  one setting a genuine `ImageIcon`.
- The same instrumentation at paint time counted **0 labels carrying an image** on the second and
  later renders, against 4 on the first.

So the icon is set, on a real label, and is gone by the time anything paints. That points straight at
finding 1: an icon applied *during construction* lands on whichever object is being built, while an
icon applied *later* lands on whichever object is in the tree when the queue drains. With 1.6 labels
built per cell, those are not always the same object — and the old posting behaviour was accidentally
papering over the difference.

**Reverted, and recorded here rather than left as a comment**, because the next person to look at this
will have the same idea, and this is the evidence that saves them the afternoon. The right order is:
understand the construction count first, then this becomes free.

---

## What is already fast, and should be left alone

- **The image cache.** Twelve-to-one is a good ratio and the key is the right shape.
- **The decode pool.** Decoding is off the event thread with a proper settle-latch, and the spinner is
  covering a real 110 ms rather than an imaginary cost.
- **`StationIndex`.** Cached, eagerly derived, published through a volatile — and the comment on
  `deriveStationIndex` explains exactly which race that avoids.
- **The reduction.** 1.65 ms. There is nothing here to win.

---

## Recommendations, in order

1. **Find and remove the extra label constructions** (finding 1). 1.6 per cell is the whole story of
   this page; everything else measured here is rounding.
2. **Then** apply icons inline on the event thread (finding 2), which becomes correct once 1 is
   understood, and removes the two-phase draw.
3. **Do not** optimise the model side. It is under two milliseconds and any effort there is wasted.
4. **Strike the cubic entry** from the manual test plan; it is fixed.

## What was done in this pass

- `testRenderingCost` added, printing every number above; it is in the battery, so the report cannot
  quietly stop being true.
- The inline-icon optimisation attempted, measured, found to break the export, and reverted with the
  evidence recorded above.
- No other change. Nothing here was fast enough to be worth the risk of changing it blind.
