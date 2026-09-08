# How much the code leans on `docs/reviews/`

Measured 2026-09-07, to answer one question before `docs/reviews/` is retired into git history: **which
comments stop making sense once the documents are gone?**

The rule this is measured against is the one in [`../reviews/README.md`](../reviews/README.md) under
*Comments: authoritative and self-contained* - a comment must state the rule, name the case, and stand
on its own; a finding id after it is provenance rather than the explanation.

The companion file [`citation-index.md`](citation-index.md) lists every id, what it is cited from, and
what the document said about it. This one is the judgement: how many of those citations are load-bearing.

## The headline

**None found.** Across 353 citations examined by hand - 22.6% of the 1,564 in the tree, chosen so that
the two places a load-bearing comment could hide are covered completely rather than sampled - not one
comment needed its id to be understood. Six are borderline and are listed in full at the end. They are
the work item, and it is an afternoon's work rather than a project.

That is a stronger claim than a sample supports on its own, so the method is written out below and the
part that is still uncovered is named.

## What is in the tree

Scanning `src/` and `test/` for `[A-Z][A-Z0-9]{1,6}-[A-Z][0-9]{1,2}`:

| | |
|---|---|
| distinct ids | 587 |
| citations | 1,564 (805 in `src/`, 759 in `test/`, across 159 files) |
| in a comment | 1,378 |
| in code - almost all assertion-message prose | 186 |
| not citations at all | 3 |

The three that are not citations are `F0-F19` once and `F20-F31` twice, in `TrainControlUI`: they are
Swing tab titles - `FunctionTabs.addTab("F0-F19", functionPanel)` - and the regex cannot tell a
function-key range from a finding id. The review corpus knows about them already; `2026-08-17-unread-half-review.md`
calls the second one *"a literal `F20-F31` tab title"* in a cosmetics list. Nothing needs doing about
them except knowing they are there when the numbers are counted again.

Of the 186 in code, nine are the id used as a **value** rather than as prose: three route names in
`testMockCentralStation` (`"TA-B5 route one"`), a route name and a window title in `testRouteEditorShading`
(`"IPR-B1"`), a deliberately unknown route name in `testRoutes` (`"UC-C6 no such route"`), and the three
tab titles. Those become meaningless strings when the documents go, but they are fixture labels: nothing
reads them for meaning and nothing breaks.

### How much can still be looked up

The brief for this work said 86 ids resolve to nothing. The real number is **28 ids carrying 58
citations**, and the difference is a convention rather than a correction to anybody's arithmetic. Sixty
review documents declare a prefix once at the top - `**Prefix:** \`LE\`` - and then number their findings
short, `### A1 - a group cut and paste left the setup on the squares it emptied`. A search for the literal
string `LE-A1` finds only the places that cite it, never the place that defines it. 271 of the 559
resolvable ids are findable only that way; a full-string search reports about three times as many orphans
as there are. No prefix is claimed by two documents, so reading a short form back is unambiguous.

A further **16 ids (36 citations)** resolve to a passing mention in somebody else's document but have no
entry of their own. Those and the 28 together - 44 ids, 94 citations - are the ones a reader cannot look
up **today**, before anything is deleted. They are the first thing checked below.

Where the 28 came from is worth one line, because it is not carelessness: they cluster by prefix -
`RC-A6` and `RC-A8` through `RC-B11`, all of `LE2`, `TSX-B3`, `V33-C5`, `VAL9-B3..B5`, `D24-C3/C4/C6`. The
`RC` document defines A1-A5 and B1-B5 and stops; the code cites up to B11. A second round under the same
prefix was clearly run and its document either was never written or never landed. `LE2`, `V33` and `D24`
have no declaring document at all - `D24-` survives only in `docs/manual-tests/.triage-backups/*.bak`.
There is nothing to retire there; it is already gone.

## Method

Reading 1,564 comments carefully is not possible, so the sample was built to make the *absence* of
load-bearing comments checkable rather than to estimate a proportion precisely.

**Two censuses, not samples.** A load-bearing comment is by definition one that says too little. So:

1. **Every citation whose surrounding comment is 40 words or shorter** - 147 of them - was read
   individually. The median citation sits in a comment of **115 words** and the mean is 151; the tenth
   percentile is 42. This census is the entire short tail, which is where a comment that leans on its id
   has to live.
2. **Every citation of the 44 ids that resolve to nothing or to a mention only** - 94 of them - was read
   individually, because those comments are already broken today if any of them lean.

**Two samples on top.** Drawn with a fixed seed (20260907) so they can be redrawn:

3. **100 of the 396 citations of the top 40 ids by count.** Those 40 ids carry 25% of all citations, so
   they are where a single bad habit would be repeated most.
4. **60 of the remaining 1,168.** 262 ids are cited exactly once; this stratum is the long tail where
   nobody would notice a thin comment.

The four overlap. **353 distinct citations were read** - 22.6% of the corpus.

**Two targeted greps as a backstop**, over the whole corpus rather than a sample: for a citation sentence
of twelve words or fewer, and for the provenance-only phrasings that would give the habit away
(`fixed for X`, `per X`, `see X`, `X fix`, a comment line containing nothing but the id). The first
returned 513 hits and every one inspected was an ALL-CAPS heading opening a paragraph that then explains
itself - `// NOT RESET (REL-A2).` followed by six lines of reason. The second returned six, all of which
state their case in the same sentence.

## What the classification found

| | self-contained | borderline | load-bearing |
|---|---|---|---|
| Short-comment census (147) | 138 | 6 | 0 |
| Unresolvable-id census (94) | 91 | 0 | 0 |
| Top-40 sample (100 of 396) | 98 | 2 | 0 |
| Long-tail sample (60 of 1,168) | 60 | 0 | 0 |

The rows overlap and the three tab titles are excluded from the first row's counts, so these do not sum
to 353. The six borderline citations are five distinct comment sites; two of them also fall in the
top-40 sample, which is why they appear twice.

**What the number rests on, and what it does not.** The two censuses are complete over their populations,
so within them the count of zero is a fact rather than an estimate. The two samples are not: 0 in 100 and
0 in 60 puts the 95% upper bound at 3% of the top-40 stratum and 5% of the tail, which weights to about
4.5% overall - *fewer than 70 citations in the tree, and most likely none*. That bound, and no tighter
one, is what the sampling can honestly buy; a point estimate off 160 draws would be arithmetic theatre. It
is the censuses that make me willing to say none rather than few, because a comment cannot both be
load-bearing and be 115 words of stated rule, and 1,211 of the unread citations sit in comments of
exactly that kind.

The residual risk is a long comment that explains something else at length and then leans on an id for
the one sentence that matters. I looked for that shape with the twelve-word-sentence grep and did not
find it. I cannot prove it is not there.

## The borderline six

None of these is broken. Each states *what* the code does and leaves the *why* with the document. They
are what to fix if the retirement is to cost nothing at all.

**1-3. `// DR-B10: the answer is shown rather than dropped.`**

- `src/org/traincontrol/gui/AutonomyMenu.java:753`
- `src/org/traincontrol/gui/AutonomyViewerPanel.java:1387`
- `src/org/traincontrol/gui/LayoutRightclickAutonomyMenu.java:996`

Three copies of one line, each above `AutonomyReport.show(ui, session.save())`. "The answer" is the
`Reconciliation` that `save()` returns, and the reader cannot tell from the comment what is in it or why
dropping it mattered. The document says: *"The absent-page rule is enforced four different ways and
reported to the user at none of its six doors."* Each would stand alone as something like *"`save()`
returns what it reconciled away - settings for pages whose files were not there - and this door used to
throw that answer on the floor, so a save could quietly discard a page's stations and say nothing."*
`GraphLocAssign.java:199` is the same finding done properly two lines longer, and is a good model.

**4. `// Through the shared rule rather than inline (DR-B4).`** - `src/org/traincontrol/base/LayoutDiagram.java:926`

Above `out.put(trimmed.substring(6), pageIdOrPosition(idText, position))`. It states the choice and not
the reason. It is nearly rescued by the javadoc on `pageIdOrPosition` at line 836 in the same file, which
gives the whole argument - two parsers of `gleisbild.cs2` that had already drifted on a corrupt `.id=`
line. Either add *"- see `pageIdOrPosition`, which is the one statement of it"*, or accept that the
same-file pointer is enough. The sibling at line 1178 of the same file already does this correctly.

**5. `// And the timetable, as the rename dialog does (DAY-B1).`** - `src/org/traincontrol/gui/TrainControlUI.java:24014`

The three lines above it explain the whole shape - *"This block is a copy of that one, so it had the same
gap"* - so in context it is fine. Listed only because the sentence alone does not say what happens if
`repaintTimetable()` is not called here.

**6. `// The kept collections (OB-025 / DD-A1)`** - `src/org/traincontrol/automationui/AutonomyCompanionStore.java:4039`

A section banner rather than an explanation, and the javadoc immediately under it is 25 lines that say
everything. Harmless. Named for completeness, because it is the only place in the sample where an id sits
in a heading with nothing beside it.

## What this means for the retirement

The citations are not a dependency. On the evidence above they are what the README says they should be:
a trail somebody may follow rather than one they must. Deleting `docs/reviews/` costs the ability to look
up *where an argument was had* - which is real, and which git history keeps - and costs no comment its
meaning.

Two things are worth doing first, and neither blocks anything:

- Fix the three `DR-B10` one-liners. They are the only place the habit appears more than once, and they
  are three copies of the same line, so it is one edit repeated.
- Note in `citation-index.md`, or wherever the retirement is recorded, that 44 ids never resolved. A
  future reader who cannot find `RC-B11` should learn that from a file rather than from twenty minutes of
  searching a git history that never had it.

## Regenerating

Both files come from three scripts in
`C:/Users/adamo/AppData/Local/Temp/claude/C--Users-adamo-Downloads-ClaudeProjects/c6c874b0-959b-45ae-a00d-c70a9696e777/scratchpad/`:
`scan_citations.py` (walks `src/` and `test/`, resolves against `docs/`, writes `citations.json`),
`make_index.py` (renders the index), and `make_sample.py` (draws the strata and dumps the comment blocks
for reading). The temp directory is not durable; the scripts are short and the regexes are stated above
if they need rebuilding.

The tree moved while this was measured - the count went from 1,562 to 1,564 during the run, another agent
being at work in the same repository - so treat these figures as a snapshot of 2026-09-07 rather than as
a ratchet. The shape of the answer does not depend on two citations.
