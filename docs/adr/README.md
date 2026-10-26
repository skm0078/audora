# Architecture Decision Records

The decision log lives in [BUILD-SPEC.md §11](../BUILD-SPEC.md#11-decision-log-adrs) (ADR-001 to ADR-009).
A decision that changes after the spec lock gets its own file here, with: context, decision, alternatives considered,
consequences.

**New ADRs are named `<yyyy-mm-dd>-<slug>.md`** (the day it was written, lowercase words, e.g.
`2026-10-25-parallel-prs-merge-cleanly.md`) and cited by that name ("ADR 2026-10-25-parallel-prs-merge-cleanly").
Two branches never pick the same name, so they never clash. This folder is the list: add no line to `docs/README.md`
and no row to the §11 table. CI fails a new numbered file ([why](2026-10-25-parallel-prs-merge-cleanly.md)).

The numbered ADRs (010 to 077) keep their numbers.
