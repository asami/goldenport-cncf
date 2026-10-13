# Managed fixed-target observation primitive

Date: 2026-09-30
Status: focused-337 passed; full event observation contract remains open

Implemented a bounded native read primitive through Subsystem -> DataStoreSpace
-> the same borrowed managed SQLite view. DataStoreObservationPolicy is a
separate read policy derived from the captured EventStore declaration and grant;
write-side collections alone do not grant reads. Only the explicit verified
host route receives this policy. Ordinary/raw views remain unchanged.

The opaque host invocation validates issuing thread, live lease and exact
provider. Targets begin with the pinned cursor, are distinct and limited to
declared event/index collections, with 512/768 respective bounds (1281 total).
Null, empty, padded and oversized entry identities reject. SQL reads all fixed
targets in one native read transaction, checks authority before each probe and
again after cleanup, rejects missing tables/cursor, checks exact physical id
and duplicate fields, and removes only reserved id from logical rows.
Existing-table missing rows are explicit None. Read cleanup rolls back without
invoking a write-commit hook. Snapshot facts require live issuing host scope.

Five executable examples cover presence/absence and unchanged cursor/storage,
missing collection/cursor rejection, undeclared/duplicate/misplaced targets,
wrong-thread and escaped-scope rejection, ordinary/raw view denial, and exact
target-class limits/noncanonical identity rejection.
Focused-336 passed 216/216 in 25 suites. After adding per-class bounds and
identity admission, focused-337 passed 217/217 in 25 suites, with no failures,
aborts, canceled, ignored or pending tests. Both SBT/wrapper exits were zero,
lock=released; both summaries/logs were read with matching cwd/argv/invocation.
Final invocation: SMWF-CNCF-OBSERVATION-SNAPSHOT-DIRECT-20260930-337.
Final log: /tmp/skill.cncf.d/cncf-sbt-f268c2676627cfba87807314067e13b83120dc5b106920997253ae470178c5df-e72c758d7c53e5b165bc88a7af0690a8/84635-20260930T090635Z.log

Validated SHA-256:
- DataStoreObservation.scala: 2cadef39dbade92eac01120d1eb3c35e2e1f66568b146a3313e89e16143a65e0
- DataStoreSpace.scala: a3a08c73559a9abe0993b0d8bdda8d60717538c09c62882dac64dfc28d47c584
- SqlDataStore.scala: ffbcb1beb582bbea853db90ab641639354f301a38c7adbadc8436d573504bdd4
- Subsystem.scala: c7fc63b0f3204a9957eb6adc71ef8010a4c325f1f539886ff0fdcd4ff089ac7e
- DataStoreObservationSnapshotSpec.scala: a31b590731c1d4deadba3f615f24a64ba967a141f98dc5d9d6f38c398c0235fb

This is a fixed manifest read primitive, NOT the complete event observation:
no selected-batch request capture/policy provenance, referenced-event/intrinsic
index closure, strict event/cursor interpretation, classifier or publication
adapter. Independent-writer competition and explicit mid-read revocation remain
untested on this new route. Returned logical records are facts, not commit or
dispatch authority. All-retained validation-only completion and all-transient
verified completion still require separate routes.

Next: build frozen selected-batch requests and bounded semantic closure INSIDE
the same native transaction (not successive calls to this fixed-target method),
then bind opaque host provenance before classifier integration.
S12/P2-U15/P2-U16/P2-U05 remain open. No full-suite/independent review/typed Phase
acceptance, commit, publish, configured service activation, or skill/agent edit.
Simplified parent execution retained shared serial lock and platform escalation.
