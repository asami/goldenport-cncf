# Structured Error Utility Adoption Note

Date: Apr. 14, 2026

## Context

simplemodeling-lib now treats semantic failure utilities as the preferred
interface for recurring structured errors.  CNCF consumes that error model
and should avoid defining parallel taxonomy/cause/facet construction rules
for the same meanings.

## Work Recorded

The CNCF error semantics notes were updated to state that CNCF should use
core semantic utilities when they exist.  If CNCF discovers a repeated
failure shape without a semantic utility, the preferred path is to add the
utility in simplemodeling-lib and then use it from CNCF.

The helper-first guideline was also updated:

- semantic helpers first
- conversion helpers and combinators next
- low-level `Consequence.fail(...)` only for explicit structured cases
- `Consequence.failure(...)` only as a last-resort unstructured fallback
- no new use of legacy `Consequence.failXxx(...)` or `Conclusion.failXxx(...)`

## Verification Context

Before this note, CNCF had already been checked against the locally published
simplemodeling-lib changes.  Test compilation succeeded, and no runtime call
sites using legacy `failXxx(...)` aliases remained.
