# Citation load - retired 2026-09-08

This measured how much the code DEPENDS on the review documents - which comments stop making sense
without them. The catalogue answers it directly: every row carries a **Cited by** column naming the
source files that reference that finding, so a finding nothing cites is prose and a finding cited from
four files is load-bearing whatever its disposition says.

See **[`docs/reviews/findings.md`](../reviews/findings.md)**, or query it:

```sql
SELECT ref, disposition, cited_by FROM finding WHERE cited_by IS NOT NULL AND disposition LIKE '%pen%'
```

The retired version is in git history at `1b3d7b7d`.
