# Manual test discipline

How hands-on testing is recorded.  **`triage.db` is the single source of truth**: every test that needs the real
railway or a display, every verdict and comment on one, every bug and feature request in the Inbox, every receipt, and
every review finding is a row in it, and nothing else holds them (Adam: *"why aren't we storing all tests in a
database, why have md files at all?"*).  The old markdown files (`tests.md`, `issues.md` and the four
`tests-archive-*.md`) were imported by `triagedb.py migrate` and deleted; their history is in git.

**Agents: ask the CLI.**  `py -3 docs\manual-tests\triagedb.py show MT-089` prints an entry in full, exactly as it
was written, with its comments; `ledger` what is open, `issues` the Inbox, `check` any query; see "The query API"
below.  The text dump in `dump/` can be grepped, but it is 5 MB and is not where to read an entry.

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

### The store, and its text dump

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

**After every write the store is written out as plain text, in `dump/`**, so git has a diff a person can read.
Nothing reads it back except the Java tests, which have no SQLite driver.  For small diffs it has one file per table
(`tests.txt`, `comments.txt`, `issues.txt`, `receipts.txt`, `notes.txt`, `findings.txt`, `dead-citations.txt`,
`meta.txt`) with records sorted by tag, one field per line, and every line of a text on its own line behind `| `,
exactly as written (trailing spaces kept); comments are in their own file, so answering a test touches its record at
most on its `reopened:` line.  It is never edited: a hand edit is overwritten by the next write and changes nothing in
the store.  `triagedb.py verify` says whether it is the store's own rows.

**A merge conflict in `triage.db`** is never resolved by picking a side blind.  Take one side's store, read what the
other side wrote from its dump diff (`git diff` of `dump/comments.txt` and the rest), and write those again through the
CLI (a comment, a disposition, a filing), which rewrites the dump.  Then `triagedb.py verify`.

**The migration** (`triagedb.py migrate`) read the markdown once; rerun on the same markdown it changes nothing, and it
refuses once the store has been written since or the markdown has gone.  `docs/tools/prove-tracker-migration.py`
proved it lost nothing (every count in the markdown, the store and the dump; every entry's `show` text equal to its
markdown block; the findings unchanged).

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

[triage.py](triage.py) is a companion window for running down the ledger beside the running railway:
`py -3 docs\manual-tests\triage.py`, no build step, no dependency beyond the Python standard library.

**Laid out for a third of the screen, full height**, stacked: the toolbar in two rows, then the list, the entry's
steps and the answer, with draggable dividers.  It starts in the right third; **View > Fill the right third** (or left)
puts it back, and its size and dividers are remembered.  The list leaves out the date (it is in the entry's heading);
steps and comments are reflowed to the window and their Markdown drawn.  **Show** is a menu in the menu bar, and the
status line starts with what it is showing.

**Reopened entries are marked.** An entry Adam has judged that has been worked on since sits at **fixed unvalidated**
like one nobody has run; "reopened" is history, not a state.  It is computed from the Comments (the newest is
Claude's, with an Adam verdict above it), marked in its own column, offered as the `reopened - changed since your
verdict` filter, counted in the status line, and available as `tests --reopened`.

**Three tabs over the list: Tests, Feature requests, Bugs.** The last two list the Inbox items of that kind: pending
ones, and picked-up ones colored by their **State** as the Tests tab colors a disposition.  An item promoted to an
`MT-###` tag hides under the `open` filters (its home is then the Tests tab, so it never shows twice) and reappears
under `everything, validated included`, colored by its linked test's disposition, with an **Open in Tests tab**
button.  Selecting a row shows it read-only, with **Request cancel…**, the one write from these two tabs: it files a
new item asking that the selected one be canceled, and does not touch the target's State.  Filing goes through **New
issue** or the Inbox; answering goes through the Tests tab or a Comment on the receipt table.

On the Tests tab, pick an entry, say whether it worked, write what happened, add anything else noticed, and submit.
**New issue** files a bug or feature request unrelated to the entry on screen.  A button starts TrainControl with the
Simulate + Debug configuration in the language chosen beside it; **Tools > Launch TrainControl in** launches straight
away.  The language is set on Java's command line (`-Duser.language`), is remembered, and opens the build stamp on
every result submitted from that run.

**What it writes.** A result is a comment on that entry, dated, signed and stamped with the commit it was run against,
the same shape as `triagedb.py comment`.  Anything else noticed, and everything from **New issue**, becomes an
`OB-###` (bug) or `FR-###` (feature request) item in the Inbox, cross-referenced to the test it came from if any.
**It never writes the Disposition line**: rule 4 makes that Claude's, and the next round reads what Adam said.

**Every entry is in it**: **Show > everything, validated included** lists them all.  An `MT-###` in an entry's text is
a link to that entry, including old links written as `[MT-123](tests.md#mt-123)`, which stay as written.

When another process writes to the store, the app shows what it holds within two seconds, keeping what has been typed.
**File > Open the text dump folder** shows the record as plain text.

**The store is tracked, and it is a binary.**  Expect `triage.db` and `dump/` to change with any triage action, and
commit them together.  For a merge conflict see "The store, and its text dump" above.

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
py -3 docs\manual-tests\triagedb.py verify               # the record holds together; the dump and findings.tsv are its own
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
   `triagedb.py` (not with SQL, not by editing the dump), because its writes keep the fields true to the text, the
   dump current, and two writers from crossing.

**When a fix lands that changes behavior a validated test covered**, move that test back to **fixed unvalidated** and
say why in its Comments: a test validated against code that has since changed looks finished and is not.

---

## The runners, and the scratch directory they need

`docs/tools/one.sh` runs named classes; `docs/tools/battery.sh` runs the whole suite from `build.xml`.  Both point at
`TC_SCRATCH`.

**`TC_SCRATCH` is one directory per session**, under `%TEMP%/claude/<project>/<session-id>/scratchpad`, and it holds
three things:

- `cp.txt`: the classpath, on one line, as `javac -cp` wants it.
- the TestNG output directory each runner writes into.
- `battery.lock`: the lock that stops two batteries running at once lives INSIDE `TC_SCRATCH`, so two sessions with
  different scratch directories each hold their own lock and neither sees the other.  The JVM probe in `battery.sh`
  covers that: it counts test JVMs machine-wide rather than trusting the lock.

**The lock is a count of test slots** (`docs/tools/slots.sh`), one per four logical processors plus two, from two to
eight, each a lock file beside the old one (which is slot 1).  Every run has its own data folder, preference node and
temp folder, so runs share nothing but the screen:

- `one.sh` takes one slot, and so runs beside a battery's lanes or another worker's run.  Two runs from the
  same `TC_SCRATCH` would write over each other's results, so the second refuses.
- `battery.sh` takes every slot.  With `TC_LANES=auto` (or a number) it runs the classes in that many JVMs at once and
  gives back the slots its lanes do not need, always leaving one for a worker; without it, it runs one class at a
  time.  `TC_CLASSES` names a file of classes to run instead of all.
- The classes in `docs/tools/serial-lane.txt` need the machine to themselves (the keyboard focus, the clipboard): a
  battery runs them first, alone, and `one.sh` takes every slot to run one.
- Every refusal says "nothing was run", which is what a worker's retry loop looks for.
- **A run that needs every slot does not starve.**  A battery, or a `one.sh` of a serial-lane class, refused because
  slots are held leaves a "waiting for every slot" marker (`$TEMP/traincontrol-battery.lock.waiting.<key>`, holding its
  pid and the time, rewritten at every retry).  While a fresh marker stands, a single-slot `one.sh` refuses with "A RUN
  IS WAITING FOR EVERY TEST SLOT ... nothing was run" even if a slot is free, and retries as for a full house.  Running
  runs are never stopped; as they drain, the waiting run takes every slot and removes its marker (so do the exit
  traps).  A marker older than 10 minutes (`TC_WAITING_TTL`, in seconds) with no live pid is ignored and deleted, and
  one older than an hour is ignored whatever its pid.  A run that needs every slot ignores the markers.
  `docs/tools/test-slot-waiting.sh` proves it with simulated holders and a lock of its own.

`docs/tools/compare-batteries.py` compares two batteries' `tng-run` folders claim by claim.

No `.java`, `.properties` or `build.xml` may be edited while either runner is going: both recompile the working tree,
so an edit mid-run is a run that reports on code that never existed.

---

## Why it is worth the ceremony

A test answered, then fixed and never re-run looks exactly like one that passed.  The disposition is the answer to
"does this still mean anything?"
