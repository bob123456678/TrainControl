# Provenance and looking up a citation

Part of the behavior reference: [`../behaviour.md`](../behaviour.md) is the index.  Sections keep the numbers and headings they had in the single file, so a citation such as "behaviour.md §5c" still resolves.

## Where this document came from

Written at Adam's request after a run of sessions in which the same rules were re-litigated because nothing stated them
in one place. Several were reversed during that work (the Return Home tier, the meaning of an unmeasured segment, and
whether a compulsory turn is a question), and the rules here are the settled answers.

The review documents were the working record of how these rules were arrived at, and they were deleted in this
reference's favor (Adam: *"I don't want more reviews living in the repo."*). Their findings are in the store, below. One
question is still Adam's to answer: whether the written evaluation of replacing the two copies by following the edges
changes anything (§3). Nothing has been built on it.

## Looking up a citation

Older comments, the tracker and these files cite review findings (`RGD-B2`, `MON-C6`; `EN57-203` is a locomotive but
`DY3-C7` is a finding). The documents those ids came from are gone. **The findings are not.**

All of them are in `docs/manual-tests/triage.db`, in the `finding` table - **5,705 rows for 5,348
findings**, because a finding written up in two documents has a row for each, and reading the row count as a finding
count is a mistake three documents have made - with the document they came from, the line in it, the severity, what it
was about, the file and line of the evidence, the commit that fixed it where one is named, and the source files that
cite it. `docs/manual-tests/findings.tsv` is a plain-text mirror of the same rows, rendered from the database, for the
two readers that cannot open one: a person with a `grep`, and `regression.testEveryCitationResolves`, which resolves
every citation in `src/` and `test/` against it and fails if a new comment names a finding that does not exist. The
counts above are rewritten from the store by `triagedb.py counts` and checked by `regression.testTheRecordsCountTheStore`.

So, having found `DY3-C7` in a comment:

```
grep "^DY3-C7" docs/manual-tests/findings.tsv
```

or, for everything the database holds about it:

```
python -c "import sys; sys.path.insert(0, 'docs/manual-tests'); import triagedb; \
           print(triagedb.findings(triagedb.connect(), ref='DY3-C7'))"
```

The questions the review folder could not answer:

```sql
-- what does this comment's citation refer to
SELECT * FROM finding WHERE ref = 'DY3-C7';

-- which findings is the code still leaning on
SELECT ref, title, cited_by FROM finding WHERE cited_by IS NOT NULL ORDER BY ref;

-- everything one document found
SELECT ref, severity, title FROM finding WHERE document LIKE '%test-suite%' ORDER BY ref;
```

**`disposition` is a quotation, `status` is the answer to it.** The disposition column is what a document said on the
day it was written, and nothing ever updated one. Every row therefore carries a `status`, set deliberately, with its
evidence in `status_note`. **Read a row to find out what a citation referred to, never whether it is still true.** For
that, read the code, or write the test.

A status is decided one of five ways: a D finding (a clean check, nothing to do); cited from `src/` or `test/`, where a
fix landed and left a comment naming it; its own document's closing word; named by a commit message; or checked by hand
against the code. **`Open - unverified`** means no evidence either way: a status invented to tidy a row is worse than a
row that admits nobody has looked. It is counted in rows and in findings separately. Two documents (VD12 and VD15) were
written in a scratchpad and never committed, so their own evidence is gone.

`Open - deferred until the MT retests` is work that is real and understood and must not land until Adam has re-run the
manual tests it would change under; each one names the test it waits on. Nothing reads status except `LIKE 'Open%'` and
`= 'Closed'`, so a new word costs nothing but has to be written down here. Work verified still true is in the Inbox as
an OB. The whole open list:

```sql
SELECT ref, severity, status, status_note FROM finding WHERE status LIKE 'Open%';
```

Citations that resolve to no finding at all are rolled in the `dead_citation` table and at the foot of the mirror. The
CITING FILES are in the table, not the mirror (the mirror's roll is `ref` and four dashes): `SELECT * FROM
dead_citation` says who cites what. They cluster into whole prefixes whose declaring document never existed (`RC` above
A5, all of `LE2` and `LD`).

[`docs/reviews/README.md`](../../reviews/README.md) survives, and is now the convention rather than an index: how a
round is run, and the rule that its documents are catalogued and deleted in the same round.

Regenerate the mirror after any change to the store:

```
python -c "import sys; sys.path.insert(0, 'docs/manual-tests'); import triagedb; \
           triagedb.render_findings(triagedb.connect())"
```
