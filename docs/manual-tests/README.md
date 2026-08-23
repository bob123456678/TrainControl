# Manual test discipline

How hands-on testing is recorded. [tests.md](tests.md) is the single source of truth: every test that
needs the real railway or a display lives there and nowhere else.

Set 2026-08-22, at Adam's request, after the tests had spread across three documents and their results
had to be pieced together from comments interleaved with the instructions.

This changes nothing about `docs/reviews/` - findings, severities and dispositions carry on exactly as
[docs/reviews/README.md](../reviews/README.md) describes. What changes is where the HANDS-ON half is
written down.

---

## The file

One file, [tests.md](tests.md), holding every manual test ever written, in one format.

### Six rules

**1. Sorted by date.** Entries appear in the order they were written, oldest first, and the date is on
the entry. A test written today goes at the bottom whatever it is about.

**2. Every entry has a tag, and keeps it for life.** `MT-001`, `MT-002`, and so on, never reused and
never renumbered. Commits, reviews and other entries refer to tests by tag, and a renumbering breaks
all of them silently.

**3. Every entry names where it came from.** The finding it was written for - `LT-A8`, `FR-A1`,
`AR-12`, `TR-A22` - or "hands-on testing" when nobody found it, somebody just tried it. This is what
makes a test traceable back to the defect that earned it.

**4. Every entry has a disposition, set by Claude and only by Claude.** Four states, and no others:

| | |
|---|---|
| **needs test** | Nobody has run it since it was written, or it was run and deferred, or it is waiting on something that does not exist yet. |
| **fixed unvalidated** | The code changed after the last run, so the previous result no longer stands. Claude believes it is right; nobody has confirmed it on the railway. |
| **fixed validated** | Run by Adam AFTER the change, and correct. This is the only state that means anything is finished. |
| **superseded** | This should never have been an entry, or another entry has replaced it. It is not run, not counted, and not on the ledger - but it stays in the file, because its tag is cited elsewhere. |

A test moves to **fixed validated** only on Adam's word in the Comments. Claude never promotes its own
work to validated, however sure it is - that is the whole point of the state existing.

**superseded** was added 2026-08-22, after MT-094 had nowhere to go twice. It was a feature request
promoted to a test entry by a rule that has since been retired; the live tracking moved to a receipt in
[issues.md](issues.md) with its own State, and the test entry stayed on the ledger asking Adam to run
something nobody would ever run. The three states above had no honest answer: it is not waiting to be
tested, it is not fixed, and calling it validated would have been a lie to get it off a list.

**It is not a way to retire work nobody fancies.** An entry is superseded when it is the WRONG SHAPE -
tracked in the wrong place, or replaced by a later entry that covers the same ground - never because it
is hard or unwelcome. Say which entry or receipt took it over, by tag, in the Comments. If nothing
took it over, it is not superseded; it is outstanding.

**5. Append only.** Entries are never deleted, never reordered, and their instructions are never
rewritten. Two things may change on an existing entry: its **Disposition** line, and its **Comments**
section, which is added to at the bottom. Everything else is history.

If a test turns out to be wrong or obsolete, write that in its Comments and leave the entry where it
is. If it needs to be done differently, write a new entry and reference the old tag.

**6. Every entry has a Comments section, demarcated.** Under `#### Comments`, below the instruction.
Adam writes there. Claude reads there and never edits what is already in it - a reply goes underneath,
dated, not over the top.

### The ledger

At the top of the file, a table of every entry NOT in **fixed validated** and not **superseded**: tag, date, one line about
it, its disposition and where it came from. That is the whole of the outstanding work in one place,
and it is where to start.

Claude regenerates the ledger whenever a disposition changes. It is editable - crossing something out
or adding a note to it is fine, and Claude will not overwrite a note it did not write.

---

## Asking for something new

[issues.md](issues.md) is the inbox, for a bug and a feature request alike - the two are the same
mechanism with one Kind field between them, in one file so a bug filed while looking for a feature (or
the reverse) has a single place to land. Adam writes what he wants there, in whatever form suits him,
or files it through [triage.py](triage.py)'s **New issue** button; Claude empties it at the start of
the next round.

**Emptying it means a one-line receipt in the table at the bottom of the inbox, and the item leaves
the Inbox section - but where it goes from there depends on what it is.**

A **bug** becomes a finding in `docs/reviews/` under that round's prefix, gets fixed there, and gets
an entry in `tests.md` with a new `MT-###` tag and the disposition **needs test** - a bug fix needs a
repeatable hands-on check that the regression stays fixed, which is exactly what `tests.md` is for.

A **feature request** is tracked directly in the receipt table instead: a **State** column, in the
same three words `tests.md`'s disposition uses (plus a fourth - see cancelling, below), set by Claude
the same way. It gets an `MT-###` tag only if the eventual work turns out to need a genuine repeatable
hands-on test the way a bug fix does - not as the default. `MT-094` is what the default used to
produce: a feature nobody had even designed yet, filed the moment it was picked up as if it were a
regression test, sitting in the Tests ledger indistinguishable from one. See its own entry for the
retirement.

**A bug's ref is `OB-###`; a feature request's is `FR-###`** - separate counters, so the ref alone
says which of the two paths above an item is on. `OB` is the older of the two and kept its numbering
when the split happened; nothing already filed was renumbered.

**Filing is not asking for it to be built.** An item that has been picked up sits in the ledger like
anything else and is worked when Adam asks for it. The two are separated on purpose: it lets him write
something down the moment he thinks of it without having to decide then and there whether it is worth
doing now, and it stops Claude reordering his priorities by picking up whatever is newest. The
exception is his own judgement - an item that says it is urgent in its own text is treated that way.

An issue reaches **fixed validated** the same way every other entry does - by Adam saying it works.
"Built" is not "validated", and a feature nobody has used is exactly the kind of thing that gets marked
finished and turns out to be half of what was wanted.

**Cancelling one works the same way filing does - request, then Claude acts.** **Request cancel…** in
triage.py's Feature requests/Bugs tabs files a new structured item naming what it is cancelling and
why (optional). Nothing changes immediately - the target's State (or, for an already-promoted bug,
its `MT-###` entry) is only set to **declined** once Claude reads the request at the start of the next
round, the same rule as every other write to either file's authoritative fields.

**The same check applies to `tests.md`, and it was missed there first.** Before writing a new `MT-###`
for a bug being picked up, look for an entry that already asks the question. MT-125 was filed from
OB-039 for the caption's direction arrow, which MT-077 had been asking since 2026-08-18 - so Adam was
handed the same test twice and said so: "i got two MT tickets for the same test. please avoid
duplication and don't reopen tickets already validated." The rule below was written about the Inbox and
is just as true one file over: a duplicate costs somebody a second run of a test they have already done,
and the second copy is indistinguishable from a new requirement. Where two entries do turn out to cover
one thing, the later one wins and the earlier is marked **superseded** naming it.

**Before filing anything - by hand, through the app, or as an automated round working through the
tracker on its own - check whether it is already there.** `py -3 docs\manual-tests\triage.py issues`
lists every pending item; read it, or read the Inbox section of `issues.md` directly, before adding to
it. This is not a courtesy - it is how two `OB-###` entries for one idea happen: a round reads the
tracker, decides something is missing, files it, and a later round (or the same one, run again) reads
the tracker again and reaches the same conclusion, because nothing recorded that the first round had
already acted. Filing is cheap and irreversible-by-convention (Inbox items are append-only, same as
`tests.md`), so the check has to happen before the write, not after. If two items turn out to describe
the same thing anyway, say so in whichever is read second rather than leaving both to look independent.

---

## The triage app

[triage.py](triage.py) is a companion window for this file, for running down the ledger without
alt-tabbing between a long markdown document and the running railway. `py -3 docs\manual-tests\triage.py`
- no build step, no dependency beyond the Python standard library.

**Reopened entries are marked, and can be filtered to.** An entry Adam has already judged, and which
has been worked on since, is the one thing the list could not tell apart from an entry nobody has ever
run - and they mean opposite things: "look at this again, it should be different now" versus "nobody has
tried this yet". Both sit at **fixed unvalidated**, and rightly: the four dispositions say what is TRUE
of an entry, and "he looked at it before" is a fact about its history rather than about its state.

So it is computed from the Comments, which already hold that history - the newest comment is Claude's,
and there is an Adam verdict above it. Shown as a mark in its own column, offered as a **"reopened -
changed since your verdict"** filter, counted in the status line, and available as `tests --reopened`.
Added 2026-08-23, when 41 of the 63 open entries were in that state and nothing on screen said so.

**Three tabs on the left: Tests, Feature requests, Bugs.** A feature request used to be reachable only
by finding the `MT-###` row it got picked up into, indistinguishable there from an actual hands-on
test - which is exactly backwards, since "does this behave correctly" and "should this exist at all"
are different questions with different owners. Feature requests and Bugs list `issues.md`'s Inbox
items of that kind: pending ones, and picked-up ones tracked directly by their own **State** -
colored the same way the Tests tab colors a disposition, same three words (plus **declined**).

**A picked-up item promoted to an `MT-###` tag hides under the `open` filters and reappears under
`everything, validated included`.** While it's active work its home is the Tests tab, not here - the
`open` filters enforce that regardless of the item's own disposition, so an in-progress bug never
shows up twice. But a bug or feature request should still be traceable from filing to close from its
own tab, not only by knowing to go look in `tests.md`, so the broad `everything` view brings it back,
colored by its linked test's actual disposition, with an **Open in Tests tab** button to jump
straight there.

Selecting a row shows it read-only underneath, alongside **Request cancel…** - the one exception to
"nothing is written from these two tabs": it files a new item asking that the selected one be
cancelled, the same request-then-Claude-acts shape as everything else here, so it does not touch the
target's State itself. Filing still goes through **New issue** or the Inbox directly, and
answering still goes through the Tests tab or a Comment on the receipt table.

Pick an entry from the Tests tab, say whether it worked, write what happened, add anything else
noticed along the way, and submit. **New issue** files a bug or a feature request that has nothing to
do with the entry on screen - a problem spotted in passing, or an idea, with nowhere else that fits it.
It has a button that starts TrainControl itself, using the Simulate + Debug configuration, so the two
windows can sit side by side.

**What it writes, and to where.** A result is appended under that entry's `#### Comments` in
`tests.md` - dated, signed, and stamped with the commit it was run against - the same shape a comment
typed by hand would take. Anything else noticed, and everything from **New issue**, becomes a
structured `OB-###` item in [issues.md](issues.md), cross-referenced back to the test it came from if
it came from one.

**What it deliberately never writes: the Disposition line, or the ledger.** Rule 4 above says the
disposition is Claude's to set, and that stays true whether the comment arrived by hand or through the
app - the app records what Adam said, nothing more, and the next round reads it the same way it reads
anything typed directly into the file.

Every write backs the target file up first and replaces it atomically, and the app checks the file has
not changed on disk since it was loaded before writing to it, so a round running in one window and
triage.py open in another cannot silently overwrite each other.

### The query API

Everything triage.py can show on screen it can also print as JSON, from a terminal, with no GUI
involved - this is what a round should call instead of re-reading `tests.md` or `issues.md` by hand:

```bash
py -3 docs\manual-tests\triage.py stats                 # counts: tests by disposition, issues pending
py -3 docs\manual-tests\triage.py tests --open          # every entry not fixed validated
py -3 docs\manual-tests\triage.py tests --all           # every entry, validated included
py -3 docs\manual-tests\triage.py tests --reopened      # ... only those changed since you judged them
py -3 docs\manual-tests\triage.py test MT-089           # one entry in full, including its Comments
py -3 docs\manual-tests\triage.py issues                # every Inbox item not yet picked up
py -3 docs\manual-tests\triage.py issues --kind bug     # ... just the bugs (or --kind feature)
py -3 docs\manual-tests\triage.py verify-ledger          # ledger table vs. actual open entries - a diff
```

Structured entries parse in full - tag, dates, disposition, from, the instruction, the comments, and
for an issue, its kind and what it was raised from. Anything hand-written that does not match the
structured shape (a freeform note dropped straight into the Inbox, say) is still returned, under
`"freeform"`, rather than silently dropped - the parser is exhaustive, not lossy, even where it cannot
be exact.

`verify-ledger` is read-only: it reports which open entries are missing from the ledger table, which
ledger rows no longer belong there, and where a row's Disposition or From has drifted from the entry
itself. It does not rewrite the table - the ledger allows hand notes on individual rows (crossing
something out, adding a comment), and a wholesale regeneration would erase them. Use it to know exactly
what to change, then change it the same way as always.

---

## Working from it

**For Adam.** Read the ledger. Work down it. Write what happened under the entry's Comments - "OK",
or what went wrong, or a change you want instead. There is no need to update the disposition; Claude
does that from what you wrote.

**For Claude, or any automated round working from this file, at the start of a round:**

1. Run `triage.py stats` and `triage.py tests --open` rather than reading the ledger table by eye -
   the table is for Adam; the command is the same information without a chance of a mis-scan.
2. Read the Comments on every entry that is not validated.
3. A comment saying the test passed moves that entry to **fixed validated**.
4. A comment describing something wrong becomes a finding in `docs/reviews/` under that round's prefix,
   fixed there, and the entry moves to **fixed unvalidated** with the new finding tag added to its
   **From** line.
5. **Before a comment asking for something new becomes a new entry, run `triage.py issues` and check
   the existing ledger for one that already covers it.** Only if nothing does, add a NEW entry at the
   bottom, referencing the tag it came from. This is the step that goes missing when a round is in a
   hurry to file rather than to check first, and it is exactly how one idea gets two tags: a round
   reads a comment, doesn't find a matching entry because it never looked, and files a duplicate that
   the NEXT round then also has to notice and merge.
6. Run `triage.py issues` for anything filed as an `OB-###` or `FR-###` that is not already
   referenced from a test's Comments - a **New issue** has nowhere else to be found. **Before
   picking one up, check the OTHER pending items and the existing `MT-###` entries for the same idea
   already there** - two pending items with the same summary are a sign a previous round already
   filed this and the check in step 5 was skipped, not a sign there are two separate things to build.
   Pick up the survivor; note the collision in the other one's place instead of pretending both are
   independent. A bug gets an `MT-###` tag; a feature request gets a **State** in the receipt table
   instead, and only gets a tag if the work turns out to need a genuine hands-on test - see "Asking
   for something new" above.
7. Watch for a cancellation request among the items step 6 surfaces - filed from **Request cancel…**,
   it names what it is cancelling. Set that target's State to **declined** (or record the decision in
   its `MT-###` entry, for a bug already promoted), then close out both the request and the target.
8. Run `triage.py verify-ledger` after the ledger is updated, to catch a row that was missed.
9. Never mark anything validated. Never edit an instruction. Never reorder. Never write into
   `tests.md` or `issues.md` by any path that skips these files' own rules - a script, a bulk edit, or
   a hand-patch that "just adds the row" is exactly how the append-only guarantee and the ledger's
   accuracy both quietly stop being true. If a tool other than `triage.py` needs to touch either file,
   it must follow the same shapes: **6.** for `tests.md`, and the Inbox format in `issues.md` for a new
   item - not a shortcut that happens to parse.

**When a fix lands that changes behaviour a validated test covered**, move that test back to
**fixed unvalidated** and say why in its Comments. A test validated against code that has since changed
is worse than an untested one, because it looks finished.

---

## Why it is worth the ceremony

The three documents this replaced disagreed about what had been tested. A test written on the 20th,
answered on the 21st, fixed on the 22nd and never re-run looked exactly like one that had passed - the
comment said "OK" and nothing said the code had moved underneath it. Two defects shipped that way in a
week, both in the same method, and both would have been caught by re-running a test somebody thought
was already green.

The disposition is the whole point. It is not a status field; it is the answer to "does this still
mean anything?"
