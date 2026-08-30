# Phase 61.6 Checklist - Information Canonical Closure

status=planned
phase=[Phase 61.6 - Information Canonical Closure](phase-61.6.md)
predecessor=[Phase 61.5 Checklist](phase-61.5-checklist.md)
successor=[Phase 62 Checklist](phase-62-checklist.md)

## IC-08: Duplicate Removal and Canonical Closure

Stage Status:
- Current status: OPEN
- Owner: CNCF and affected downstream maintainers
- Update rule: Update only from accepted IC-08 closure evidence; do not reopen
  earlier IC contracts.
- Entry rule: IC-07 is DONE in Phase 61.5.
- Completion rule: No competing Information model remains, all required
  validation passes, and canonical documentation matches verified behavior.

- [ ] Remove the handwritten root Information case class.
- [ ] Remove duplicated handwritten CML value classes.
- [ ] Remove expired compatibility adapters and aliases.
- [ ] Remove generated-only fixture assumptions that no longer describe
  runtime behavior.
- [ ] Search source, tests, docs, generated inputs, and downstream repositories
  for obsolete runtime type references.
- [ ] Run cold CML generation and focused Phase 61 suites.
- [ ] Run full CNCF validation.
- [ ] Run full affected downstream validation.
- [ ] Perform read-only review, review-fix, and clean re-review.
- [ ] Promote verified architecture to `docs/design`.
- [ ] Promote public and persistence/migration contracts to `docs/spec`.
- [ ] Update strategy, phase, checklist, Help, and generated documentation.
- [ ] Record final version, dependency, migration, and release evidence.
- [ ] Close the Phase 61 series only after all completion rules and
  documentation gates pass.

Evidence:
- Pending.
