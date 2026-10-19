# App6.2 HTTP JSON Root Null Presence — 2026-10-19

## Cause And Authority

App6.2 producer participation uses the parent-frozen
`esa62-cncf-json-null-implementation-20261019@1` manifest for
PHASE-6.2 / ESA62-02 / ESA62-02B. The selected user policy requires external
explicit JSON null to remain `Json.Null`, distinguishable from absence, with
no Scala null or Record-core change.

The HTTP adapter previously reconstructed ordinary root entries solely from
Core Record conversion. Because semantic conversion omits null object
properties, that reconstruction lost explicit root null keys, including
unknown keys needed by a receiver's original-input validation. The successful
typed HTTP path then had no raw body from which the receiver could recover
that evidence.

## Design And Ownership

CNCF now restores syntax presence during ordered root reconstruction: declared
raw values take priority, original root null becomes `Json.Null`, and other
ordinary values retain Core normalization. The existing Entity update refusal
runs before this reconstruction. This is a generic HTTP ingress boundary with
no App6.2 packet-key knowledge.

Canonical `XJson` continues to own complete raw document trees. Core continues
to own nested semantic Record conversion, nested null omission and semantic
null-sequence rejection. The selected Operation continues to own key and type
validation. The integer-reader scenario records that a retained null remains
invalid for an ordinary integer parameter.

The normative boundary and connected executable scenarios are documented in
[HTTP JSON Body Admission](../../../spec/http-json-body-admission.md), with the
existing [typed-update contract](../../../spec/http-form-typed-update-parameters.md)
retaining explicit-clear and carrier ownership.

## Specification Authorship

The decoder specification updates E1 for root presence and adds E2–E4 for
ordinary omission, unknown null evidence and actual typed rejection. E5 adds
independent generated key names, disjoint nonempty key roles, null-presence
choices, ordinary values and root-order variations. It checks paired absent
and present objects, unchanged raw children and the absence of Scala null,
with a minimum of 100 successful shrinking samples and zero discards required.

The owning parent retains focused validation, dependency-artifact refresh,
integrated producer/consumer review and real-consumer acceptance authority.

## Contribution acceptance — 2026-10-19

The preceding authoring/validation handoff is historical. Earlier scoped CNCF
ingress/affected evidence records 7 and 65 tests at their respective scopes;
overlapping runs are not additive. No new CNCF test execution or full-suite
result is claimed. The actual host 002 route rejected extra-root-null with 400,
preserved exactly four complete stopped SQLite rows and passed strict cleanup.
Independent focused result `esa62-structured-operation-focused-result-20261019@1`
is PASS, accepting this limited ingress contribution with both downstream
structured-operation blockers resolved.

App Phase 6.2 remains OPEN/`in_progress`; Step2/3 local commits, sole
comprehensive Phase review, owning final full validation and distinct release
remain due. Original native 001 uninstall/data loss and native 006/host 001
cleanup false remain history. Only 52 October 9 capture files have proven capture
recovery; later audio/configuration/credentials and full product-data recovery
remain unproven. Strict CAR lint exit-1/publication and domain debt remains
separate and nonblocking.
