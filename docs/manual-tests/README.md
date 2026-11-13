# Manual test discipline

How hands-on testing is recorded.  **`triage.db` is the single source of truth**: every test that needs the real
railway or a display, every verdict and comment on one, every bug and feature request in the Inbox, every receipt, and
every review finding is a row in it, and nothing else holds them (Adam: *"why aren't we storing all tests in a
database, why have md files at all?"*).

**Agents: ask the CLI.**  `py -3 docs\manual-tests\triagedb.py show MT-089` prints an entry in full, exactly as it
was written, with its comments; `ledger` what is open, `issues` the Inbox, `check` any query; see "The query API"
below.  `triagedb.py changes` says what changed since the last commit, a triage's verdicts among it.

Findings are recorded as [docs/reviews/README.md](../reviews/README.md) describes: a round's reviews are cataloged
into the same store and deleted in the round that wrote them.  This file covers the HANDS-ON half.

---

## The record

Every manual test ever written is a row in `triage.db`, in one format, open or closed, and stays there under its tag
for life.

### Six rules

**1. In the order written.** Entries are listed oldest first, with the date on the entry.  A new one
(`triagedb.py add-test`) takes the next tag whatever it is about.

**2. Every entry has a tag, and keeps it for life.** `MT-001`, `MT-002`, and so on, never reused and never renumbered:
commits, reviews and other entries refer to tests by tag.

**3. Every entry names where it came from.** The finding it was written for (`LT-A8`, `FR-A1`, `AR-12`, `TR-A22`), or
"hands-on testing" when nobody found it and somebody just tried it.

**4. Every entry has a disposition, set by Claude and only by Claude.** Four states, and no others:

| | |
|---|---|
| **needs test** | Nobody has run it since it was written, or it was run and deferred, or it is waiting on something that does not exist yet. |
| **fixed unvalidated** | The code changed after the last run, so the previous result no longer stands. Claude believes it is right; nobody has confirmed it on the railway. |
| **fixed validated** | Run by Adam AFTER the change, and correct. This is the only state that means anything is finished. |
| **superseded** | This should never have been an entry, or another entry has replaced it. It is not run, not counted, and not on the ledger, but it stays in the record, because its tag is cited elsewhere. |

A test moves to **fixed validated** only on Adam's word in the Comments.  Claude never promotes its own work to
validated, however sure it is.

**Superseded is not a way to retire work nobody fancies.** An entry is superseded when it is the WRONG SHAPE (tracked
in the wrong place, or replaced by a later entry covering the same ground), never because it is hard or unwelcome.
Name the entry or receipt that took it over, by tag, in the Comments.  If nothing took it over, it is outstanding.

**Or an automated test that answers it** (Adam: *"let automated tests supersede those MTs (if you are 100% confident in
the result)"*), only where the test drives the same door the steps do (the menu action or the method it runs, not a
helper beneath it) with the same inputs (the supplied file, the same squares of the frozen railway), asserts every
outcome the Expected names as the Comments amend it, and is green in a full battery.  If any of that is in doubt, or
the check is how something looks or reads, the entry stays Adam's.  The Comments name the test class and method and
what it asserts; the session report lists every entry superseded this way for Adam to review, and one he reopens goes
back to what it was.

**ONE TEST PER ENTRY** (Adam: *"make each MT be one test, not a laundry list of different things"*): a bundle has one
disposition, so a verdict on any item is a verdict on all.  One set of steps and one thing checked; several things
true for one outcome are one entry, outcomes judged apart are several.  A question for Adam to rule on is an entry
too, and says so at the top.  A bundle is split by superseding it with a note naming its replacements, new entries
with new tags.

**5. Append only.** Entries are never deleted, never reordered, and their instructions are never rewritten.  Two things
may change on an existing entry: its **Disposition** line (`triagedb.py set`), and its comments, added at the bottom
(`triagedb.py comment`).  The store has no command that changes anything else.  If a test turns out wrong or obsolete,
say so in its Comments and leave it; if it needs doing differently, write a new entry referencing the old tag.

**6. Every comment is signed, dated, and added, never edited.** Each opens with `**Adam, YYYY-MM-DD.**` or
`**Claude, YYYY-MM-DD.**` and is a row of its own, under the entry, in the order said.  Adam writes them through the
triage app; Claude never edits one: a reply is a new comment.  (Older entries carry a `#### Comments` heading, and some
an unsigned paragraph under it; both are kept as written.)

### The ledger

Every entry NOT in **fixed validated** and not **superseded**: tag, date, one line about it, its disposition and where
it came from.  That is the whole of the outstanding work, and where to start.

**It is a query, and nothing can drift from it** (Adam: *"regenerate it based on authoritative data."*).
`py -3 docs\manual-tests\triagedb.py ledger` prints it, and the triage app's **Show > open** filters are the same
list.  A note you want kept against an entry goes in that entry's comments.

### The store

**`triage.db` is the record, and every reader and writer goes through `triagedb.py`.**  A test is kept as written: its
text up to its first comment, each comment as a row of its own, and the rule that closes it.  Joined, they are the entry
exactly as written, which is what `show` prints; the date, title, disposition, From and "reopened" are read out of that
text whenever it changes, never set on their own, so a field cannot disagree with the text.  An Inbox item is its text
as filed, and a receipt is a row of the old receipt table.  The prose `issues.md` carried around them is kept word for
word (`triagedb.py notes`).

**Every write is one short transaction**, begun with `BEGIN IMMEDIATE`, so the triage app and a script writing at the
same moment queue for the lock instead of overwriting each other; a busy store is waited on for 30 seconds.  A comment
is added as a row, never by reading the entry and writing it back.  The app re-reads the store within two seconds of
another process writing to it.

**What changed is a command, not a file.**  `triagedb.py changes` compares two stores record by record and prints the
records added, gone and changed: by default the store the last commit holds and the one on disk, so after a triage it
lists Adam's new verdicts and comments, and every disposition set.  `--from` and `--to` take a git revision or a `.db`
file; `--date 2026-11-01` lists instead the tests written, comments made and items filed on or after a day.  A record is
a `[header]` line and one field per line, every line of a text behind `| `; `triagedb.py text --all` prints them whole (the
Java tests read the store this way, having no SQLite driver).  Nothing is written out beside the store.

**A merge conflict in `triage.db`** is never resolved by picking a side blind.  Take one side's store, list what the
other side wrote (`triagedb.py changes --from <the merge base> --to MERGE_HEAD`), and write those again through the
CLI (a comment, a disposition, a filing).  Then `triagedb.py verify`.

---

## Asking for something new

The Inbox holds bugs and feature requests alike, one list with a Kind field between them.  Adam files through
[triage.py](triage.py)'s **New issue** button, or asks for it to be filed (`triagedb.py file`); Claude empties it at
the start of the next round.

**Emptying it means a one-line receipt (`triagedb.py receipt`), and the item leaves the Inbox (`triagedb.py clear`,
which keeps it and its text); where it goes from there depends on what it is.**

A **bug** becomes a finding under the round's prefix (in the round's review document while it is worked, in the
`finding` table of `triage.db` once the round is cataloged), gets fixed, and gets a new `MT-###` entry
(`triagedb.py add-test`) with the disposition **needs test**: a bug fix needs a repeatable hands-on check that it stays
fixed.

A **feature request** is tracked by its receipt's **State**: three of the four disposition words (not **superseded**)
plus **pending** (picked up, not built yet) and **declined** (see cancelling, below), set by Claude the same way.

**Anything an `MT-###` tests is tracked through that test, not its own State** (Adam: *"make sure other FR's get
updated once their MT's are implemented"*).  Its receipt names every such tag in **Became** and leaves State empty, so
the row's state is the worst of those tests' and moves the moment Adam validates them.  Never type a State beside a
Became.  A feature request gets an `MT-###` tag only if the work turns out to need a genuine repeatable hands-on test,
the way a bug fix does; not by default.

**An entry added to since it was filed says so, per addition.** The original filing stands as written and is never
edited.  Everything after it opens with who and when (`**Claude, 2026-08-24.**`, `**Adam, 2026-08-24.**`), oldest
first, as rule 6 has for tests.  `verify-ledger` checks it: an Inbox entry whose body carries more than one
paragraph-leading bold run must have a dated attribution on every one after the first.

**A bug's ref is `OB-###`; a feature request's is `FR-###`**, separate counters, so the ref says which path an item is
on.  Nothing already filed was renumbered.

**Filing is not asking for it to be built.** A picked-up item sits in the ledger and is worked when Adam asks for it,
so Claude does not reorder his priorities by picking up whatever is newest.  The exception: an item that says it is
urgent in its own text is treated that way.

An issue reaches **fixed validated** only by Adam saying it works; "built" is not "validated".

**Cancelling works the same way filing does: request, then Claude acts.** **Request cancel…** in triage.py's Feature
requests/Bugs tabs files a new item naming what it cancels and why (optional).  Nothing changes immediately: the
target's State (or, for a bug already promoted, its `MT-###` entry) is set to **declined** once Claude reads the
request at the start of the next round.

**Do not duplicate a test.** Before writing a new `MT-###` for a bug being picked up, look for an entry that already
asks the question (Adam: *"please avoid duplication and don't reopen tickets already validated."*).  Where two entries
turn out to cover one thing, the later one wins and the earlier is marked **superseded** naming it.

**Before filing anything (by hand, through the app, or as an automated round), check whether it is already there.**
`py -3 docs\manual-tests\triage.py issues` lists every pending item; read it before adding to it.  Inbox items are
append-only, like the tests, so the check has to happen before the write.  If two items describe the same thing
anyway, say so in whichever is read second.

---

## The triage app

[triage.py](triage.py) (`py -3 docs\manual-tests	riage.py`) is a companion window for running down the ledger beside
the running railway; its Help menu describes the screen.  A result written there is a comment on the entry, and
anything else noticed becomes an `OB-###` or `FR-###`.  **It never writes the Disposition line**: rule 4 makes that
Claude's, and the next round reads what Adam said.  Expect `triage.db` to change with any triage action; `triagedb.py
changes` reads the change.  For a merge conflict see "The store" above.

### The query API

Everything the window shows, the command line prints; a round calls this instead of reading anything by eye.
`triagedb.py` reads and writes the store; `triage.py` with an argument prints the same data as the window, as JSON:

```bash
py -3 docs\manual-tests\triagedb.py show MT-089          # one entry, exactly as written, then its comments
py -3 docs\manual-tests\triagedb.py show OB-324          # an Inbox item, or a receipt, likewise
py -3 docs\manual-tests\triagedb.py ledger               # every entry still asking Adam for something
py -3 docs\manual-tests\triagedb.py check --disposition "needs test" --json   # any query of the tests
py -3 docs\manual-tests\triagedb.py issues               # the Inbox items not yet picked up (--all: every one, and every receipt)
py -3 docs\manual-tests\triagedb.py comment MT-089 "Fixed in abc123." --who Claude
py -3 docs\manual-tests\triagedb.py set MT-089 "fixed unvalidated"
py -3 docs\manual-tests\triagedb.py add-test "What it is about" --from OB-324 --steps steps.md
py -3 docs\manual-tests\triagedb.py file bug "One line" "The detail." --raised-from MT-089
py -3 docs\manual-tests\triagedb.py receipt OB-324 "What was done." --became MT-773
py -3 docs\manual-tests\triagedb.py update-receipt FR-131 --state "fixed unvalidated"
py -3 docs\manual-tests\triagedb.py clear OB-324         # out of the Inbox, once it has a receipt; it is kept
py -3 docs\manual-tests\triagedb.py changes              # what changed since the last commit (--from, --to, --date)
py -3 docs\manual-tests\triagedb.py text comments        # every record of a kind, as text (no kind: counts; --all: every record)
py -3 docs\manual-tests\triagedb.py findings DY3-C7      # a review finding in full (no ref: a count; --all: the whole catalogue)
py -3 docs\manual-tests\triagedb.py verify               # the record holds together
py -3 docs\manual-tests\triagedb.py selftest             # exercises the store on copies
py -3 docs\manual-tests\triage.py stats                  # counts: tests by disposition, issues pending
py -3 docs\manual-tests\triage.py tests --open           # every entry not fixed validated, as JSON
py -3 docs\manual-tests\triage.py tests --reopened       # ... only those changed since Adam judged them
py -3 docs\manual-tests\triage.py test MT-089            # one entry, as JSON
py -3 docs\manual-tests\triage.py verify-ledger          # the record's own checks
```

`add-test` takes its steps and expected result from a file (or `-` for stdin), and writes the anchor, heading,
Disposition, From and Written lines itself, numbered after the last entry.

`triage.py issues` returns every pending item in full (tag, kind, what it was raised from, its detail), and `--all`
adds the receipt table.

`verify-ledger` is read-only.  It checks the record: a disposition that is not one of rule 4's four words, an Inbox
item with no Kind, an addition to an Inbox item that does not say who wrote it and when, a field that is not what its
entry's text says, an entry that does not end with its rule, and a comment on nothing the store holds.  It exits 3 when
the record is merely not clean, and 1 only for an actual error running the check, so a script can tell them apart.

---

## Working from it

**For Adam.** Read the ledger (the triage app's open list) and work down it.  Say what happened: "OK", what went
wrong, or a change you want instead.  Claude updates the disposition from what you wrote.

**For Claude, or any automated round working from the record, at the start of a round:**

1. Run `triage.py stats` and `triagedb.py ledger`.
2. Read the comments on every entry that is not validated (`triagedb.py show MT-###`).
3. A comment saying the test passed moves that entry to **fixed validated**.
4. A comment describing something wrong becomes a finding under the round's prefix, cataloged into `triage.db` like
   every other, is fixed, and the entry moves to **fixed unvalidated**, with a comment naming the new finding.
5. **Before a comment asking for something new becomes a new entry, run `triage.py issues` and check the existing
   ledger for one that already covers it.** Only if nothing does, add a NEW entry (`triagedb.py add-test`),
   referencing the tag it came from.
6. Run `triage.py issues` for anything filed as an `OB-###` or `FR-###` not already referenced from a test's Comments.
   **Before picking one up, check the OTHER pending items and the existing `MT-###` entries for the same idea**; two
   pending items with one summary are one thing filed twice.  Pick up the survivor and note the collision in the
   other.  A bug gets an `MT-###` tag; a feature request gets a **State** (see "Asking for something new" above).
7. Watch for a cancellation request among the items step 6 surfaces (filed from **Request cancel…**, it names what it
   cancels).  Set that target's State to **declined** (or record the decision in its `MT-###` entry, for a bug already
   promoted), then close out both the request and the target.
8. Run `triage.py verify-ledger` and `triagedb.py verify` at the end.
9. Never mark anything validated.  Never edit an instruction.  Never reorder.  Never write to the store except through
   `triagedb.py` (not with SQL), because its writes keep the fields true to the text, and two writers from
   crossing.

**When a fix lands that changes behavior a validated test covered**, move that test back to **fixed unvalidated** and
say why in its Comments: a test validated against code that has since changed looks finished and is not.

---

## The runners

CLAUDE.md ("Working efficiently") says how to run them; `docs/tools/slots.sh`'s header says how the scratch directory
and the test slots work.  No `.java`, `.properties` or `build.xml` may be edited while a runner is going: both
recompile the working tree, so an edit mid-run is a run that reports on code that never existed.

---

## Why it is worth the ceremony

A test answered, then fixed and never re-run looks exactly like one that passed.  The disposition is the answer to
"does this still mean anything?"
