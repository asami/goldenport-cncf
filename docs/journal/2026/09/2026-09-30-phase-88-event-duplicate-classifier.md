# Pure EventId-first retained-fact classification

Date: 2026-09-30
Status: pure classification passed focused-335 after Cozy prerequisite-334

ManagedEventDuplicateClassifier is package-internal pure interpretation of
bounded supplied observations. It is not proof of a coherent provider snapshot,
absence, host admission, commit, application completion or delivery. There is
deliberately no adapter connecting it to a managed plan.

The currently available native snapshot seam serves postcommit plan readback,
not cursor + EventId/key lookup + absent-row observation. A generic load loop
would not prove that boundary. This slice therefore freezes the semantic
classifier and tests first, preserving existing new-only managed composition.

Inputs: selected transactional unissued facts, decoded cursor facts, up to
1024 retained canonical envelopes and 1024 logical index rows with entry IDs.
Inventory validation requires exact V2/index integrity, bounded cursor sequences,
unique EventIds/sequences/index targets, non-dangling references and an exact
intrinsic index for every keyed retained event. Key-free V1 envelopes have
explicit read-only recovery; failed V2 never falls back or migrates.
Physical provider IDs and completeness/absence remain future host obligations.

Classification checks EventId first, then complete typed delivery key. If both
lookups identify different facts, it rejects structural inconsistency rather
than selecting either or repairing indexes. A retained fact preserves exact
canonical bytes/payload/sequence; an incoming payload does not overwrite it.
An EventId match never binds an additional key; a key match never binds the
incoming EventId. Those aliases are not inserted into batch-local lookup maps.
Only the earliest new durable facts enter maps and consume contiguous slots.

Results are New(slot), RetainedExisting(canonicalBytes), RetainedBatch(newSlot)
or Transient(inputSlot), in original order. New facts are frozen canonical V2
shape templates and returned as fresh decoded unissued clones retaining hidden
keys. Private template sequence 1 is discarded, never reserved, persisted or
exposed as a committed record. Only newDurableCount drives the pure cursor
proposal/overflow check; retained/transient-only results consume no capacity,
including at exhausted counters. No event/index Create or dispatch is emitted.

Eight pure specs cover EventId priority/no key alias, key lookup/no ID alias,
batch-local first retention and immutability, divergent indexes, malformed/
missing/dangling/duplicated inventories, cursor bounds/exhaustion, explicit V1
read-only facts, and invalid/bounded input. Existing policy selection still
rejects duplicate EventIds in one batch; duplicate-ID classifier fixtures test
future semantics only and do not weaken that current contract. Batch-key reuse
is likewise still rejected by the existing new-only managed route.

Focused-332 failed production compilation because enum field ordinal conflicts
with Scala's standard ordinal method. No tests ran. Only that field name changed
to newSlot for focused-333. Attempt-333 stopped before CNCF compile due to the
Cozy development classpath becoming stale. Ordinary Cozy compile/export-334
refreshed validation outputs successfully, SBT/wrapper exit=0, lock=released;
no Cozy source edits. Focused-335 then passed 212/212 tests in 24 suites, no
failed/aborted/canceled/ignored/pending cases. SBT/wrapper exit=0, lock=released.
All terminal summaries were read; exact invocations/cwd/argv and final counts
matched. No code changed after the successful focused run.
Final invocation: SMWF-CNCF-DUPLICATE-CLASSIFIER-DIRECT-20260930-335.
Final log: /tmp/skill.cncf.d/cncf-sbt-a1b08c369400ca478d743a11faa3277c683c1a4c814b5dc90ef7621a7ee99a3b-c59714b13636c4a0f23d726f9ce83991/71334-20260930T083640Z.log
Cozy prerequisite log: /tmp/skill.cncf.d/cncf-sbt-7a80a3f82ac26219ea9895c0beb85313b83d27b2ea414c1ca8677e53693b5a75-8eb2601947e78bf0dab12052d5ff72e2/70855-20260930T083549Z.log
Validated SHA-256:
- ManagedEventDuplicateClassifier.scala: 1534452e077fb91d33a9a7ea6af1422dcf8d1bfc21ba1279fce89389392d7eef
- ManagedEventDuplicateClassifierSpec.scala: 2d65a77916c221db52d56db96346f0d09e7dc4ceb5de0dc695e39b2cd4cfbe74
Simplified parent execution retains fresh helper metadata, scoped platform
review, serialized SBT, lock release and persistent log/summary.

Next host boundary (not implemented):
1. Exact host-generated event/key lookup requests and referenced-event closure,
   with explicit absent observations, declared physical identity and cursor,
   collected in one managed native read transaction under the live lease.
2. Opaque host provenance binding observations to batch/policy/domain/thread/
   lease; pure vectors or decoded facts must never grant absence authority.
3. Native commit validates the observation cursor predicate before publication;
   retained/absent observations and complete retained readback require an admitted
   route. All-retained batches need validation without cursor increment, distinct
   from all-transient no-cursor work. No arbitrary transaction callback.
4. Stale/revoked/inconsistent evidence rejects; no automatic reclassification,
   retry, alias repair or sequence reuse after uncertainty.

S12/P2-U15/P2-U16/P2-U05 remain open. No full-suite, independent review,
typed Phase/PLAN acceptance, commit, publication or configured service activation.
