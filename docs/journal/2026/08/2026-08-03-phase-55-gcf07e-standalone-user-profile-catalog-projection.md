# Phase 55 GCF-07E StandaloneUserProfile Catalog Projection

Date: 2026-08-03

GCF-07E adds four fixed-user parameter witnesses in a profile-only catalog and
projects the already-admitted HOME profile documents into immutable candidates.
Identity/display-name are normalized non-empty Strings; locale and timezone are
respectively BCP 47 `Locale` and IANA `ZoneId` values in the generic binding
model.
The regular runtime catalog remains user-mode-only, so PROJECT/CWD sources
cannot contribute fixed-user bindings. The projection preserves HOME origin,
Textus/CNCF layer, file identity/type, exact profile path, source ordering, and
Global versus selected Subsystem target.

Validation: CNCF `Test/compile` passed at `48373-20260802T201305Z` and
`50260-20260802T201717Z`; focused profile/catalog/runtime regression suites
passed 11/11 at `53610-20260802T202312Z`. Review fixes added canonical HOME
input validation, complete provenance/target coverage, and the Locale/ZoneId
codecs; `Test/compile` plus the focused suites passed 13/13 at
`63370-20260802T203855Z`. Runtime consumption, identity migration, and
formatting remain later GCF-07 work. Independent review added forged null/
root-path admission coverage and strict Locale.Builder parsing for malformed
language tags; `Test/compile` plus the focused suites passed 13/13 again at
`70792-20260802T205136Z`. The final independent re-review is clean. No Phase
53 CS-01--CS-07 historical source/spec was changed.
