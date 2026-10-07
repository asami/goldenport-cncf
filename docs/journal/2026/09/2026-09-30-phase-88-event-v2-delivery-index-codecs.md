# Typed delivery key, V2 envelope and delivery-index codecs

Date: 2026-09-30
Status: bounded pure codecs passed focused-329

This slice adds pure bounded codecs/integrity checks, not runtime activation.
EventRecordJsonV1, legacy memory EventStore and cncf-* skills are unchanged.

Producer ABI admission:
- simplemodeling-model/src/main/scala/org/simplemodeling/model/datatype/EntityId.scala
  supplies public major/minor/collection/timestamp/entropy and EntityId.restore.
- javap of the consumed local Ivy 0.2.2-SNAPSHOT jar confirmed the exact public
  restore(String,String,EntityCollectionId,Instant,String) Consequence method.
- Collection reconstruction uses EntityCollectionId(major,minor,name), never
  print/toString parsing. Restore materializes saved timestamp/entropy without
  issuance. Generic identity restoration preserves existing EntityId equality;
  no domain-specific entity behavior or authority is restored.
- CmlStateMachineDefinition.scala constructor invariants enforce shared machine,
  transition/source/trigger/target identity, context trigger/version and ordered
  unique fields. Those equal references are reconstructed from shared typed
  identities. Every scalar type and target variant has an explicit wire tag.

CommittedDeliveryKeyJsonV1 (event-internal) stores a canonical structural schema
with exact fields, array order, bounded paths/context/text and a 65536 UTF-8 byte
cap. Invocation, structured entity, full CML binding and operation ID survive.
Unknown/missing/duplicate/noncanonical representations reject via exact reencode;
constructor violations and unsupported scalar/target tags have no fallback.
These are inert declarations, never executable CML admission.

EventRecordJsonV2 uses a separate prefix/envelope containing exact canonical V1
body plus complete typed delivery discriminator or null. Only this encoder has
a local key-free body copy while the discriminator remains in the envelope.
Strict bounded decoding restores the key and checks the entire envelope bytes.
It never accepts V1 storage as a fallback; V1 retains its keyed-fact rejection.
V2 Create remains pure, persistent/transactional and sequence-checked.

EventDeliveryIndexRecordV1 has exactly five logical text fields. Event reference
uses separately prefixed canonical issued EventId constituent JSON, preventing
SQL JSON auto-decoding. Sequence is positive canonical decimal Long text; digest
is lowercase SHA-256 of the complete canonical stored V2 envelope, including
prefix. Entry identity is delivery-v1- plus SHA-256 of exact key storage bytes.
Lookup validation requires full requested key bytes even when the same index
row identity is supplied. Referenced-event validation requires exact ID,
sequence, key, persistence/lane and envelope digest. Missing/corrupt evidence
rejects without repair. Transient/key-free facts generate no index.
Caller-owned snapshot/authorization and physical identity checks remain required.

Eight executable examples include 18 target/option/order round-trips, distinct
lookup constituents, malformed representations, V1/V2 compatibility/rejection,
full index identity/reference checks, no-index cases and SQLite close/reopen.
The SQLite example uses two ordinary Creates only to prove storage text recovery;
it is not an atomic event+index production commit, managed deduplication or a
dispatch test. Managed selected-event composition still uses its existing V1
codec and rejects keyed durable events until an explicit V2/index route is added.

Focused-328 compiled production sources, then failed test compilation because
the helper named key collided with ScalaTest's key DSL overload (lines 86/89).
No tests ran. Only the helper name/call sites changed to lookupKey for 329.
Focused-329 passed 199/199 tests in 22 suites, no failed/aborted/canceled/
ignored/pending cases. SBT and wrapper exit=0; lock=released.
Invocation: SMWF-CNCF-V2-INDEX-CODEC-DIRECT-20260930-329.
Log: /tmp/skill.cncf.d/cncf-sbt-5ca5af204d1bdc0c754e514c7bfa053d5ac94070f9c57c9afd8be635b5ceba5b-aaccd5bd0793a28f009c69ca50ca4f44/56861-20260930T080526Z.log
Adjacent .summary.json was read and matched invocation, exact cwd/argv,
counts, exits and lock evidence.
Validated SHA-256:
- CommittedDeliveryKeyJsonV1.scala: f83d18ff37f260aa150e50df5ff1d09cab8b9e9be2c280258e13ffa2e316c070
- EventRecordJsonV2.scala: 6beeda3a46bf4c050e021a9cf3faa34d9e627c49f73d4587adf120c8228fa8eb
- EventDeliveryIndexRecordV1.scala: 008f915c3ea6465b54fd0f51c7c0ee712698e14fd57aecae8a7b89a15a39cc90
- EventRecordJsonV2Spec.scala: 06ca819fa6c6599706c150c129f9bd2833146e9cf049b93ae6f81572c4508099
The user's simplified parent execution preserves scoped platform review, fresh
helper metadata, serial lock and persistent log/summary. No full-suite,
independent review, typed Phase/PLAN acceptance, commit or publication is claimed.

Next: V2 selected-event/index Create composition through the managed cursor
transaction and complete index readback; coherent duplicate classification,
competition/revocation, verified all-transient completion and actual dispatch.
S12/P2-U15/P2-U16/P2-U05 remain open.
