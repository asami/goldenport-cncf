# Native event observation closure and pure classification

Date: 2026-09-30
Status: focused-339 passed; native publication remains disconnected

ManagedEventObservationRequest captures ordered selected lookup facts, canonical
durable keys and policy revision. EventRecord values are copied and durable keys
are restored through the canonical codec so later hidden-key mutation cannot
change lookup targets. Retained incoming payloads need not encode; new facts
must pass the classifier's canonical admission. All-transient/empty selection
rejects this cursor-bearing route. Existing ordinary duplicate-ID selection
rules remain unchanged.

DataStoreSpace.observeSelectedEventsC creates the request from its admitted
read policy and binds it to an opaque invocation with the same provider, live
lease, issuing thread and captured declaration/grant domain. Ordinary/raw spaces
remain denied. No caller supplies SQL or a transaction callback.

SQL reads cursor + incoming event/key probes, then referenced events, then the
intrinsic indexes of all observed events, on ONE native connection/read
transaction. Per-stage target bounds and host checks apply; repeated targets
reuse only observations from this transaction. No fixed-target snapshot is
called again. Missing tables/cursor reject; row absence in existing collections
is explicit. Referenced-event/intrinsic-index absence rejects; unexpected index
references are not traversed further or repaired.

After successful read cleanup the final capability seam interprets complete
coverage, physical EventId/envelope equality, strict cursor and event codecs,
required closure and full index integrity, then invokes the pure classifier.
Fixed-target snapshots have no event classification. Classification and row
facts are available only in the live issuing host scope; classification is
pure output, not commit, application completion or dispatch authority.

Four added examples cover:
- Key-first retained alias with unsupported payload, EventId-first retention
  without binding a new key, one new durable fact and one unprobed transient.
  The strengthened final fixture explicitly requires a referenced event and
  intrinsic index that were NOT in the initial manifest.
- Missing intrinsic index, dangling event reference, mismatched physical event
  identity and wrong digest reject without modifying index/cursor.
- All-retained classification consumes no capacity at Long.MaxValue counters;
  transient-only selection rejects this route.
- Source hidden-key mutation after pure request capture does not alter target
  identities; unsupported genuinely new payload still rejects.

Focused-338 passed 221/221 tests in 25 suites. After strengthening the closure
fixture and exact new-event absence assertion, focused-339 passed the same
221/221 in 25 suites; zero failures/aborts/canceled/ignored/pending.
Both SBT/wrapper exits=0, lock=released. Exact invocation/cwd/argv and terminal
log/summary were read for each attempt. No code edits followed final validation.
Final invocation: SMWF-CNCF-EVENT-CLOSURE-DIRECT-20260930-339.
Final log: /tmp/skill.cncf.d/cncf-sbt-dda56ba03b17feae3f21176f230f128eeb60331a56686fad186238b8b7fe89f9-f1289db44fa69aa8d159e567bf68df07/92966-20260930T092635Z.log

Validated SHA-256:
- ManagedEventObservationRequest.scala: 7776dd83e0009c6a9505d896fdb0b8b80333aa8e378eceec19c236be9e372da8
- DataStoreObservation.scala: 58b1c1213f898a6af0a7d25ac3499146f42aaa1a2bcf060f0b78462acf847066
- DataStoreSpace.scala: 5e21d5a239d2effc35d0092c4879086e4b93c17ba183c1b9cc63420ec603176c
- SqlDataStore.scala: 2ca180375dbccc8674387368f91f008fe6a1a30c6c99726fc50ee86cb8a4d321
- ManagedSelectedEventV2Spec.scala: be0f82a494b8a755670b2ab974961682e800c0226da3737afb9dee1a7d206b06

Still open: independent-writer competition and mid-read revocation executable
evidence on this new closure route; actual configured EventStore selection
provenance beyond a captured revision label; full publication handoff freezing
and native write-time validation of observations; all-retained validation-only
completion, retained postcommit readback, all-transient completion and dispatch.
Request capture is lookup-fact freezing, not deep canonical admission of every
incoming payload. The classifier freezes genuinely new facts after lookup.

Next bounded step: competition/revocation specs and trusted selection/handoff
requirements, BEFORE adding native publication. No automatic reclassification,
retry, alias repair, cursor mutation or dispatch was added.
S12/P2-U15/P2-U16/P2-U05 remain open. No full-suite/independent review/typed Phase
acceptance, commit, publish, public Operation/CLI activation or skill/agent edit.
Serial-execution skill retained shared lock and evidence with the previously
authorized simplified parent execution route.
