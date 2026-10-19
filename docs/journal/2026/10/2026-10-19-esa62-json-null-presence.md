# App6.2 HTTP JSON Root Null Presence — 2026-10-19

Current status: limited contribution accepted in closed App Phase 6.2. The final
entry below owns current scoped status. All following earlier authoring, pending
and contribution-acceptance entries retain their historical stage outcomes;
no producer Phase transition follows from this App acceptance.


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

## Final scoped App Phase 6.2 acceptance — 2026-10-19

App Phase 6.2 is closed with all twelve criteria accepted. App ESA62-01/02/03
are accepted/local committed at 9ca6a61, 16161aa and 45aa435. This limited
contribution was locally committed in Step 2 at `8cc98d8baf6941d47c638e458f551a516015f203`.

CNCF final full `test` retry 067: 6,854 succeeded, zero failed; five canceled, one ignored, 46 pending; 860 completed suites, zero aborted. This run explicitly selected `Test / fork := true`, `CNCF_LIVE_POSTGRESQL_TEST=true` and `CNCF_LIVE_MYSQL_TEST=true`. Earlier focused and full counts are not additive; nonpass outcomes remain distinct from succeeded tests.

Original full 056 and retry 065 remain failures. Approved existing-suite context/entity/SPI/display-observer fixtures and the subsequent HTTP 409/context/clock expectations reflect the authoritative current contracts. Both fresh independent focused repair closures passed before successful final full retry 067. These test/fixture corrections add no production or public-contract change.

The sole comprehensive Phase review `esa62-full-phase-review-result-20261019@1` retains its original FINDINGS verdict. The prescribed server README correction closed it through the static M0 amendment `esa62-full-readme-m0-amendment-20261019@1`. Both fresh independent focused test-repair closures, `esa62-full-validation-repair-focused-result-20261019@1` and `esa62-conflict-expectation-focused-result-20261019@1`, are PASS. Phase repair cycles 1 and 2 are accepted, with zero consecutive nonconverging repairs; no second comprehensive review occurred.

The selected distinct local release binding is `esa62-phase-release-closure-20261019@1`. Its native commit receipt is the authority for the actual release revision. No remote publication is claimed.

Native 001 uninstall/data loss remains a preservation violation without waiver. Only 52 October 9 capture files have proven capture recovery; later audio, configuration, credentials and full product-data recovery remain unproven. Original native 006 and host 001 failures and cleanup false remain failures. The real native ISBN-only/title-only Commits through default Core HTTPS and durable ACKs, separate server/App restart continuation 5770, cleanup 8433 and restoration 60573 retain their scopes. Host 002 establishes unknown-state/fresh-handle recovery and actual common Retry, scoped duplicate/conflict/negative rejection and exactly four stopped SQLite rows. Physical radio interruption and Android OS-kill recovery remain unproved.

Both strict server/KnowledgeHub CAR lint exit-1 results and existing HYG/DEV, publication/domain debt remain separate. No new CAR-lint PASS, packaging, device or runtime rerun is claimed. Server Phase 3 ESS3-02/03/04, media/editor/enrichment and formation, and KnowledgeHub domain work remain open. This contribution grants no whole producer Phase closure and does not reopen Flutter Core or the previously closed TFAF Phase 3.
