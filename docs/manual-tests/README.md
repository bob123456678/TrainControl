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

**4. Every entry has a disposition, set by Claude and only by Claude.** Three states, and no others:

| | |
|---|---|
| **needs test** | Nobody has run it since it was written, or it was run and deferred, or it is waiting on something that does not exist yet. |
| **fixed unvalidated** | The code changed after the last run, so the previous result no longer stands. Claude believes it is right; nobody has confirmed it on the railway. |
| **fixed validated** | Run by Adam AFTER the change, and correct. This is the only state that means anything is finished. |

A test moves to **fixed validated** only on Adam's word in the Comments. Claude never promotes its own
work to validated, however sure it is - that is the whole point of the state existing.

**5. Append only.** Entries are never deleted, never reordered, and their instructions are never
rewritten. Two things may change on an existing entry: its **Disposition** line, and its **Comments**
section, which is added to at the bottom. Everything else is history.

If a test turns out to be wrong or obsolete, write that in its Comments and leave the entry where it
is. If it needs to be done differently, write a new entry and reference the old tag.

**6. Every entry has a Comments section, demarcated.** Under `#### Comments`, below the instruction.
Adam writes there. Claude reads there and never edits what is already in it - a reply goes underneath,
dated, not over the top.

### The ledger

At the top of the file, a table of every entry NOT in **fixed validated**: tag, date, one line about
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

**Emptying it means:** each item becomes an entry in `tests.md` with a new `MT-###` tag and the
disposition **needs test**, its **From** line naming the item's `OB-###` reference (or reading
`feature request` for the handful filed before that numbering existed). A one-line receipt goes in the
table at the bottom of the inbox, and the item leaves the Inbox section.

**Filing is not asking for it to be built.** An item that has been picked up sits in the ledger like
anything else and is worked when Adam asks for it. The two are separated on purpose: it lets him write
something down the moment he thinks of it without having to decide then and there whether it is worth
doing now, and it stops Claude reordering his priorities by picking up whatever is newest. The
exception is his own judgement - an item that says it is urgent in its own text is treated that way.

An issue reaches **fixed validated** the same way every other entry does - by Adam saying it works.
"Built" is not "validated", and a feature nobody has used is exactly the kind of thing that gets marked
finished and turns out to be half of what was wanted.

---

## The triage app

[triage.py](triage.py) is a companion window for this file, for running down the ledger without
alt-tabbing between a long markdown document and the running railway. `py -3 docs\manual-tests\triage.py`
- no build step, no dependency beyond the Python standard library.

Pick an entry from the list, say whether it worked, write what happened, add anything else noticed
along the way, and submit. **New issue** files a bug or a feature request that has nothing to do with
the entry on screen - a problem spotted in passing, or an idea, with nowhere else that fits it. It has
a button that starts TrainControl itself, using the Simulate + Debug configuration, so the two windows
can sit side by side.

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

**For Claude, at the start of a round:**

1. Run `triage.py stats` and `triage.py tests --open` rather than reading the ledger table by eye -
   the table is for Adam; the command is the same information without a chance of a mis-scan.
2. Read the Comments on every entry that is not validated.
3. A comment saying the test passed moves that entry to **fixed validated**.
4. A comment describing something wrong becomes a finding in `docs/reviews/` under that round's prefix,
   fixed there, and the entry moves to **fixed unvalidated** with the new finding tag added to its
   **From** line.
5. A comment asking for something new gets a NEW entry at the bottom, referencing the tag it came from.
6. Run `triage.py issues` for anything filed as an `OB-###` that is not already referenced from a
   test's Comments - a **New issue** has nowhere else to be found. Pick it up the same way a direct
   entry in `issues.md` would be.
7. Run `triage.py verify-ledger` after the ledger is updated, to catch a row that was missed.
8. Never mark anything validated. Never edit an instruction. Never reorder.

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
