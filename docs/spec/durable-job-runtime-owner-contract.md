# Durable Job Runtime Owner Contract

status=authoring_test_pending
phase=69.7
step=JM69-09
slice=JM69-09D1B
manifest=PHASE-69.7-configured-durable-runtime-implementation-revision3
document_revision=4
repair_manifest=PHASE-69.7-D1B-cli-recovery-fix01-revision1

This normative contract fixes configured production composition on the existing
Subsystem-owned engine and native lifetime/storage/Blob boundaries. Trusted provider
composition is internal. The only additive public data is the closed operational
state enum and immutable Boolean health projection; the native signed wire format
is unchanged. Independent review and parent-selected validation remain pending.

JM69-09 remains **IN_PROGRESS**; JM69-10 remains **OPEN**; Phase 69.7 remains
in progress. This is initial authoring with tests and independent review pending.
No Step, Slice, Phase, predecessor handoff, review closure or commit acceptance is
claimed. Original C/D coverage remains required, including all 17 native entities,
security/hostile-access, retention, integrity, backup/restore and migration fixtures.
All original native conformance suites and assertions remain authoritative.

The unwaived C1/C2/C3/C4 M2 assurance obligations and current blockers, including
`NRT-DISPATCH-REFUSAL` and `NRT-CANCEL-SETTLEMENT`, remain open. Original review,
repair and local commit obligations, real two-process JM69-10 acceptance, CBD Support
fresh-runtime/downstream acceptance, predecessor ancestry audit, aggregate final
validation, epoch-1 full-Phase review and release remain required. No successor,
push, publication or release is authorized by this authored boundary.

Historical D1A receipts remain immutable: REP-001 and REP-002 recorded compiler
failures; REP-003 passed 66/68 with two opaque-ID expectation failures; exactly three
frozen M1 fixture repairs preceded REP-004's 68/68. `PH697-D1A-ACC-001` discovered
77 suites and passed 620 tests, with one original SqliteDataStore pending example.
Those are prior-tree results, not D1B results. Step Repair Attempt 1 and limit 3 retain
their cumulative history; no counter reset or M2 waiver is introduced.

## Canonical native terminal publication composition

The [terminal publication contract](durable-job-operation-response-publication-contract.md)
owns opted-in framework successful-result production. The actual owner creates
its original owned `_payloads`, one `_result_payloads` from the same native
payloads, then the original bridge with that capability. Only this pure
constructor dependency order changes. Original engine/State/store/SystemNode
lease, policy, timer, scheduler, recovery/startup, health and shutdown ordering
remain authoritative. No new runtime input, public API, configuration, provider
or signed schema is added. The optional bridge capability preserves old callers.

Independent source evidence carries only closed intent; default Supplied retains
legacy absence. Actual successful owned publication follows signed preflight,
native producer and result-only original checkpoint. Checkpoint refusal or lost
acknowledgement after Published success grants no caller rollback/refund/retry.
Fresh configured owners can read actual bytes through their actual lease.
The separate [current-caller query contract](durable-job-operation-response-query-contract.md)
appends optional private callerAccessResolver after migrateV1OnStartup; null
container/element refuses startup, None preserves compatibility. The original
ready owner resolves only the actual caller once inside the admitted real lease
and invokes its existing result reader explicitly using that caller. Brief
Ready/engine/identity checks end before callbacks, and admitted work may finish
through logical close without a second ready gate. Only a detached visible model
escapes; projection/raw/getResult/management remain None/UnavailableAfterRestart,
with no cache, startup grant, admin bypass or physical ownership change.
The [query design](../design/durable-job-operation-response-query.md) fixes these
boundaries and the [query journal](../journal/2026/10/2026-10-04-phase-69.7-job-result-query.md)
owns query execution. Actual completed CBD fresh-runtime acceptance remains a
separate required downstream responsibility. See the
[design](../design/durable-job-operation-response-publication.md) and
[execution journal](../journal/2026/10/2026-10-04-phase-69.7-job-result-publication.md).

## R1

Original JM69-09D1B production composition/lifecycle boundary only. Bind durable operations to the original Subsystem-owned InMemoryJobEngine, State, TimeSource and JobTimer. Never create a replacement engine, secondary worker pool/timer/physical SQL pool, global provider registry, standalone fallback or paid-authority reconstruction. All original native signed OCC/payment/protected-replay/task settlement/cancellation invariants and CPBs remain unchanged.

Executable examples: [DO1](../../src/test/scala/org/goldenport/cncf/job/JobDurableRuntimeOwnerSpec.scala), [DI1](../../src/test/scala/org/goldenport/cncf/job/JobDurableRuntimeOwnerIntegrationSpec.scala), [DL5](../../src/test/scala/org/goldenport/cncf/job/JobDurableRuntimeLifecycleSpec.scala).

## R2

Add private[cncf] immutable JobDurableRuntimeConfiguration(providerid:Option[String],periodmillis:Long,maxcandidates:Int), projected only from admitted typed bindings. Catalog append exactly textus.job.durable.provider (confidential=true, exact ASCII [A-Za-z][A-Za-z0-9_]{0,63}, no trim/normalization/reflection), textus.job.durable.maintenance-period-millis (whole integer1000..86400000, absent60000), textus.job.durable.max-candidates (whole integer1..1024, absent100). SubsystemInstance-only allowed target kind; existing document alias spellings textus.runtime/cncf/cncf.runtime. Use dedicated nonreflecting codecs, bounded decimal digit strings before numeric conversion; NumberValue must be whole and within bounds. Missing all means unconfigured; numeric values without provider refuse. Invalid input diagnostics contain only fixed parameter labels, never raw strings. No raw config/sys/env parsing in the owner. The catalog is statically closed independent of whether a Subsystem enables durable operations. Existing CncfConfigurationParameterCatalogSpec E1 exact cardinality becomes55 (=52+3); update only that assertion and current version header, preserving other existing behavior/metadata/assertions.

Executable examples: [Q1](../../src/test/scala/org/goldenport/cncf/config/JobDurableRuntimeConfigurationSpec.scala), [Q2](../../src/test/scala/org/goldenport/cncf/config/JobDurableRuntimeConfigurationSpec.scala), [Q3](../../src/test/scala/org/goldenport/cncf/config/JobDurableRuntimeConfigurationSpec.scala), [Q4](../../src/test/scala/org/goldenport/cncf/config/JobDurableRuntimeConfigurationSpec.scala), [Q5](../../src/test/scala/org/goldenport/cncf/config/JobDurableRuntimeConfigurationSpec.scala), [Q6](../../src/test/scala/org/goldenport/cncf/config/JobDurableRuntimeConfigurationSpec.scala), [DL1](../../src/test/scala/org/goldenport/cncf/job/JobDurableRuntimeLifecycleSpec.scala).

## R3

Trusted application composition registers a private[cncf] abstract class DurableJobRuntimeProvider(val id:String) on one Subsystem before final initialization. Its private[job] abstract _inputs_c(subsystem):Consequence[DurableJobRuntimeInputs] is the sole input factory; final private[cncf] _start_c(subsystem,configuration,onready:DurableJobRuntimeOwner=>Consequence[Unit]) routes the fixed owner algorithm. Private[job] final ConfiguredDurableJobRuntimeProvider(id,build:Subsystem=>Consequence[DurableJobRuntimeInputs]) implements only inputs. Private[job] inputs explicitly contain EntityStore, BlobStore, DurableJobKeyedEvidenceSource, startup candidate source, runtime rehydration port, retention source, ExecutionContext, storage/blob/lifetime policies. Sources are independently supplied immutable closed facts; never synthesize authorization/definition/input/retention from live Job/Task/context. Per-Subsystem exact-ID registration rejects null/invalid/duplicate/late/initializing/shutdown, no replace, scan or fallback. D2 appends `migrateV1OnStartup:Boolean=false` to these private inputs. Only trusted registered application composition may explicitly supply true; there is no configuration/catalog/environment/JCL/HTTP migration flag, discovery, or privilege synthesis. Enabled composition must supply an internal context with explicit `job_admin` or `content_admin` Operator capability. Default and explicit false retain the original startup effect order and behavior. The [migration checkpoint contract](durable-job-migration-checkpoint-contract.md) M2/M3/M11 governs this opt-in preparation.

Executable examples: [DO1](../../src/test/scala/org/goldenport/cncf/job/JobDurableRuntimeOwnerSpec.scala), [DO2](../../src/test/scala/org/goldenport/cncf/job/JobDurableRuntimeOwnerSpec.scala), [DL1](../../src/test/scala/org/goldenport/cncf/job/JobDurableRuntimeLifecycleSpec.scala), [DL5](../../src/test/scala/org/goldenport/cncf/job/JobDurableRuntimeLifecycleSpec.scala).

## R4

Subsystem keeps value-only durable config admission inside existing admitRuntimeConfigurationBindingsC; no storage/source/effect activation there. Private[cncf] _initialize_durable_job_runtime_c selects that admitted exact provider once, idempotently returns ready without re-running its factory, rejects concurrent init/missing provider with fixed durable runtime startup refused. Entire provider build+owner start is under existing _with_managed_datastore_lease_c outside Subsystem monitor. Activate at the very end of CncfRuntimeInstanceLifecyclePart._initialize after component bootstrap/extras, descriptor verification, SPI and StartupImport, before returning Subsystem, within original startup-cleanup bracket. Actual application bootstrap registers the provider; no unconditional production source discovery.

Executable examples: [DO1](../../src/test/scala/org/goldenport/cncf/job/JobDurableRuntimeOwnerSpec.scala), [DO2](../../src/test/scala/org/goldenport/cncf/job/JobDurableRuntimeOwnerSpec.scala), [DL1](../../src/test/scala/org/goldenport/cncf/job/JobDurableRuntimeLifecycleSpec.scala).

## R5

New InMemoryJobEngineDurableRuntimePart owns engine-local initializing/failed/owner state; brief _state_monitor transitions only. Begin requires original fresh engine: no workers/bridge/native durable or runtime/terminal bindings/queued or pending/bootstrap work, resourceSnapshot retained/pending/executing all0, original admission/force flags false. Begin precedes factory/source/native work. Original submit fast guard and _reserve_submission refuse while initializing before IDs/reserve/store/body; preserve existing preflight order. _claim_work(false) under brief monitor returns None while initializing, including manual drain; blocking worker path stays original because begin forbids started workers. _ensure_scheduler_workers guard+CAS under brief monitor cannot start until ready. Existing unconfigured constructor/bootstrap/admission behavior is unchanged. The new composition code must not add an enclosing engine, Subsystem or runtime-owner monitor around provider factory, startup candidate source, rehydration port/coordinator, store/Blob calls, actual datastore lease acquisition, maintenance sweep or business Task body. It uses brief monitors only for its own state transitions and releases them before these calls. This rule governs newly introduced composition synchronization; it does not move, remove or broaden the original engine synchronization. Preserve the original registration critical sections, including their read-only native authentication under State monitor, and the original projection/cancellation/due-state algorithms. Original inner engine monitor ownership is permitted exactly as required by the pre-existing native contract. Do not edit RetryRecoveryPart, ProjectionPart, ControlPart or other original native owners to enforce the superseded blanket sentence.

Executable examples: [DO3](../../src/test/scala/org/goldenport/cncf/job/JobDurableRuntimeOwnerSpec.scala), [DI3](../../src/test/scala/org/goldenport/cncf/job/JobDurableRuntimeOwnerIntegrationSpec.scala), [DL2](../../src/test/scala/org/goldenport/cncf/job/JobDurableRuntimeLifecycleSpec.scala).

## R6

The owner constructs the original Store, LifecycleWriteBridge(keyed source), owned-payload blob quota, lifetime quota, retention, cleanup and maintenance using one leased EntityStore object, explicit context and lower-only valid policies. Attached engine lifetime policy must equal supplied lifetime policy; do not mutate State policy. Probe actual native store capability and persistent BlobStore status before Ready; a successful available persistent local Blob fixture is required for positives. Original acknowledged resource accounting, signed format/proofs/OCC and original Store/Quota/Lifetime algorithms remain unmodified.

Executable examples: [DO3](../../src/test/scala/org/goldenport/cncf/job/JobDurableRuntimeOwnerSpec.scala), [DI1](../../src/test/scala/org/goldenport/cncf/job/JobDurableRuntimeOwnerIntegrationSpec.scala), [DI2](../../src/test/scala/org/goldenport/cncf/job/JobDurableRuntimeOwnerIntegrationSpec.scala), [DI4](../../src/test/scala/org/goldenport/cncf/job/JobDurableRuntimeOwnerIntegrationSpec.scala), [DI7](../../src/test/scala/org/goldenport/cncf/job/JobDurableRuntimeOwnerIntegrationSpec.scala).

## R7

Fetch startup candidate source once under actual lease and supply the same cached vector through the original DurableJobStartupRecovery terminal and runtime coordinator/selected port, preserving all existing bounds/order/ordinal/authentication/protected replay rules. Require every original candidate to have exactly one Registered terminal XOR Registered runtime result with matching original identity/ordinal/order. Any missing/corrupt/refused/foreign candidate or refused port fails the complete owner. No silent truncation/skip/repair/implicit migration/replay of absent context. Earlier registered prefix may retain genuine native acknowledgements/payments; it must never execute a Task body before the complete vector succeeds, and failed startup must not invent a rollback/refund. Call the original engine recoverDurableTerminalFacts/recoverDurableStartup entry points from the leased owner without an additional surrounding monitor. Each original _register_durable_terminal_candidate/_register_durable_runtime_candidate retains its existing State-monitor sequence: pure/local validation and collision/capacity checks, exact read-only DurableJobLifetimeRecovery.prepare authentication, safe runtime construction, provisional physical reservation, atomic bridge adoption, exact binding/Seed and record/due publication. No snapshot authentication or authority/payment is relocated or reconstructed. Whole-vector startup barrier remains active across every original registration and any retained prefix; Ready and worker release occur only after the complete vector succeeds. With D2 explicitly enabled, capability probes and this one original source fetch precede complete migration of that immutable cached vector, using the owner's original Store/payloads/lifetime policy and actual engine resource policy under the same original provider-start SystemNode lease and begin barrier. Migration preflight, physical finally-close, sequential native checkpoints and exact acknowledged paid post-proof must succeed before bridge binding, original recovery, owner publication, workers or timer activation. The same entire cached vector still passes the original terminal and runtime recovery independently; migration does not replace either pass. With false the original source -> bridge bind -> recovery order remains unchanged. A failed migration suffix or ambiguous acknowledgement may retain native migrated prefix/current state and conservative storage debt; original cleanup and close flags prevent Ready without rollback, refund or retry. See M4..M11 and MI1..MI5 in the [migration checkpoint contract](durable-job-migration-checkpoint-contract.md).

Executable examples: [DI2](../../src/test/scala/org/goldenport/cncf/job/JobDurableRuntimeOwnerIntegrationSpec.scala), [DI3](../../src/test/scala/org/goldenport/cncf/job/JobDurableRuntimeOwnerIntegrationSpec.scala), [DI4](../../src/test/scala/org/goldenport/cncf/job/JobDurableRuntimeOwnerIntegrationSpec.scala), [DL2](../../src/test/scala/org/goldenport/cncf/job/JobDurableRuntimeLifecycleSpec.scala).

## R8

Complete startup atomically under brief engine _state_monitor: verify still initializing/not admissionclosed/not forcecancel; scheduler.start registers first using its documented nonblocking/no-before-due timer; onready(owner) takes brief Subsystem synchronized, rejects actual _shutdown_in_progress or _shutdown_result.nonEmpty and publishes the owner before workers become available; attach engine owner, set ready and clear initializing, recheck closed flags and start original workers once if eligible. Scheduler-specific lease adapter checks Ready under brief engine monitor then releases it before real lease and sweep; a due callback concurrent with commit cannot do prefix effects. Lock order engine_state->Subsystem only; original shutdown releases Subsystem monitor before quiesce/drain. Any registration/onready/startup Failure or NonFatal exception closes owned registration/gate, marks failed/admissionclosed and returns fixed startup refusal; no body/fallback/unblock. Preserve interrupt flag. Existing startup cleanup remains responsible for logical cancellation/drain and physical finalization. The startup commit monitor covers only the new bounded state/registration/publication sequence described here; it must not encompass provider construction, native probing, source/port/recovery coordinator calls, datastore lease acquisition, maintenance sweep or Task body. The earlier native recovery calls complete before entering this final publication section. Original inner engine critical sections are preserved under R1/R5/R7; there is no claim that all existing engine I/O runs outside State monitor.

Executable examples: [DI3](../../src/test/scala/org/goldenport/cncf/job/JobDurableRuntimeOwnerIntegrationSpec.scala), [DI4](../../src/test/scala/org/goldenport/cncf/job/JobDurableRuntimeOwnerIntegrationSpec.scala), [DL2](../../src/test/scala/org/goldenport/cncf/job/JobDurableRuntimeLifecycleSpec.scala), [DL3](../../src/test/scala/org/goldenport/cncf/job/JobDurableRuntimeLifecycleSpec.scala).

## R9

Private[job] DurableJobRuntimeLease is a generic use[A](body:=>Consequence[A]) utility with close/available. Subsystem-backed lease blocks new outer operations after close, enters actual _with_managed_datastore_lease_c, tracks entered lease in its own ThreadLocal only for nesting and clears in finally. Already-admitted nested operations continue under the same real SystemNode binding lease while drain waits. No fabricated resource lease/pool ownership, physical close, counters or retry. All actual provider I/O and complete Task body execute under this lease.

Executable examples: [DO4](../../src/test/scala/org/goldenport/cncf/job/JobDurableRuntimeOwnerSpec.scala), [DO6](../../src/test/scala/org/goldenport/cncf/job/JobDurableRuntimeOwnerSpec.scala), [DI1](../../src/test/scala/org/goldenport/cncf/job/JobDurableRuntimeOwnerIntegrationSpec.scala), [DI6](../../src/test/scala/org/goldenport/cncf/job/JobDurableRuntimeOwnerIntegrationSpec.scala), [DI7](../../src/test/scala/org/goldenport/cncf/job/JobDurableRuntimeOwnerIntegrationSpec.scala), [DL4](../../src/test/scala/org/goldenport/cncf/job/JobDurableRuntimeLifecycleSpec.scala).

## R10

DurableJobLeasedEntityStore transparently delegates all31 current EntityStore overloads/effect methods (including private save/update, expectedrevision/Option policy variants, managed/authoritative/detached/unversioned/by-id, conditional transition, delete/restore/hard delete/search/searchInternal/unique/resolveIdentity), each once under actual lease; preserve exact typeclasses/argument labels/named overload contract, original Consequence Failure identity and thrown exception, no default delegation synthesis/fallback/retry. name/isAccept pure delegates. DurableJobLeasedBlobStore covers put/get/delete/accessUrl/status once under lease; name pure. Fault observation can only set sticky owner degraded state, never translate success/failure or add I/O. EntityStore/BlobStatus/raw backend fields never become public health.

Executable examples: [DO4](../../src/test/scala/org/goldenport/cncf/job/JobDurableRuntimeOwnerSpec.scala).

## R11

ResourcePart wraps entire async/manual _execute_reserved_work body in the new engine _execute_with_durable_runtime_c(body:=>Unit):Consequence[Unit], encompassing original _with_execution_lease and _run_scheduler_work; no change to execution/canonical settlement algorithms. AdmissionPart synchronous submit tail uses flatMap with that same wrapper, preserving successful original JobId and original lease Failure when refused after already acknowledged admission. On actual lease refusal before body entry, release the original engine resource lease exactly once via entered flag/finally; after entry the existing _with_execution_lease owns its release. Never refund native lifetime paid work or fabricate canonical success. Unconfigured None runs original body exactly as before. This covers business Task managed-datastore resolution after ingress caller returns, not only Job record writes.

Executable examples: [DI1](../../src/test/scala/org/goldenport/cncf/job/JobDurableRuntimeOwnerIntegrationSpec.scala), [DI6](../../src/test/scala/org/goldenport/cncf/job/JobDurableRuntimeOwnerIntegrationSpec.scala).

## R12

Append optional lease:Option[DurableJobRuntimeLease]=None to existing MaintenanceScheduler constructor. None preserves legacy effects/behavior; Some(nonnull) wraps entire clock+candidate source+sweep under lease. Existing own monitor protects registration/generation, never held across sweep/lease. Same fixed-delay engine timer/time source/period and one registration; no overlap/rearm after close. Owner stop closes registration and then outer lease gate before Subsystem datastore drain, allows already admitted callback to finish under its real lease, no thread join or shared timer shutdown inside owner.

Executable examples: [DO6](../../src/test/scala/org/goldenport/cncf/job/JobDurableRuntimeOwnerSpec.scala), [DI5](../../src/test/scala/org/goldenport/cncf/job/JobDurableRuntimeOwnerIntegrationSpec.scala), [DI7](../../src/test/scala/org/goldenport/cncf/job/JobDurableRuntimeOwnerIntegrationSpec.scala), [DL4](../../src/test/scala/org/goldenport/cncf/job/JobDurableRuntimeLifecycleSpec.scala).

## R13

Subsystem shutdown preserves original quiesce first, then idempotent durable owner _stop_c before existing owned cleanup/binding drain/release/classloader cleanup. Aggregate owner shutdown result with all existing failures, retain cached shutdown result/idempotence. Never close caller-owned SystemNode physical pool in logical owner; bare Subsystem shutdown leaves physical Running, handle-owned SystemNode finalizes Stopped. Startup publication/close races cannot leave an unowned maintenance registration or newly admitted body.

Executable examples: [DI6](../../src/test/scala/org/goldenport/cncf/job/JobDurableRuntimeOwnerIntegrationSpec.scala), [DL3](../../src/test/scala/org/goldenport/cncf/job/JobDurableRuntimeLifecycleSpec.scala), [DL4](../../src/test/scala/org/goldenport/cncf/job/JobDurableRuntimeLifecycleSpec.scala), [DL5](../../src/test/scala/org/goldenport/cncf/job/JobDurableRuntimeLifecycleSpec.scala).

## R14

Add public enum JobDurableOperationalState Unconfigured/Starting/Ready/Degraded/Closing/Closed and immutable JobDurableOperationalHealth(state,configured,storageReady,startupReady,maintenanceRegistered,maintenanceHealthy,executionAvailable). Optional JobEngine.durableOperationalHealth:Option[...] default None preserves other implementers; InMemory reports Some(Unconfigured) before begin. Ready requires actual probes+all recovery+registered maintenance+atomic publication, never mere config/metric flags. Degraded is sticky actual provider Failure/throw or adverse closed maintenance result. Getter performs no I/O/source/time/ID/retry/recovery; closing/closed readiness false and configured retained true.

Executable examples: [DO5](../../src/test/scala/org/goldenport/cncf/job/JobDurableRuntimeOwnerSpec.scala), [DO6](../../src/test/scala/org/goldenport/cncf/job/JobDurableRuntimeOwnerSpec.scala), [DL6](../../src/test/scala/org/goldenport/cncf/job/JobDurableRuntimeLifecycleSpec.scala).

## R15

Maintenance bad retention facets: Missing/Corrupt/Refused/Stale/InvalidTransition/RevisionExhausted/WriteRefused. Bad cleanup facets: Missing/Corrupt/Refused/Stale/RevisionExhausted/InvalidTime/OwnershipRefused/ResourceBoundRefused/PayloadDeleteRefused/CheckpointRefused. Other normal closed retained/notdue/held/shared/acknowledged facets remain healthy. A canonical Blob referenced as both owned and shared is protected by the original ownership guard: cleanup reports OwnershipRefused, preserves the shared bytes and conservative occupied charge, and makes maintenance unhealthy. This adverse refusal is distinct from a normal healthy shared acknowledgement. Use actual enum cases, preserve original TTL/legal hold/shared references/expiry-before-delete/OCC/ambiguous ack conservative charges. No exception/status message or provider credential/path/caller/Job/Task/proof crosses closed health.

Executable examples: [DO5](../../src/test/scala/org/goldenport/cncf/job/JobDurableRuntimeOwnerSpec.scala), [DI5](../../src/test/scala/org/goldenport/cncf/job/JobDurableRuntimeOwnerIntegrationSpec.scala).

## R16

JobExperienceService operator authorization remains first, before engine access. Add fixed durable-health record and durable-health/durable-maintenance categories reflecting explicit actual durable availability; retain legacy health runtime-only counts/category and interactive retention unavailable/handoff. Do not edit original JobExperience/Audit spec leaves to conceal changed legacy behavior. New public fields contain only closed enum and Boolean facets, never providerid/path/subject/tenant/identity/clock/reference/message/projection.

Executable examples: [DO5](../../src/test/scala/org/goldenport/cncf/job/JobDurableRuntimeOwnerSpec.scala), [DL6](../../src/test/scala/org/goldenport/cncf/job/JobDurableRuntimeLifecycleSpec.scala).

## R17

Native fixture is target/configured-durable-runtime/work/case-* unique child, owned descriptor/config/blob/database only, cleanup finally. Positive owner factory resolves actual managed SQLite via Subsystem.resolveManagedComponentDataStoreC under real active lease, using explicit Environment/ResolvedParameters and ComponentDataStoreSpace.Request(component-a), actual ExecutionContext DataStoreSpace and StandardEntityStore/Job entity realm. Reuse unmodified DurableJobKeyedEvidenceFixtures Source/recipe/originalRecipe/pair and JobDurableLifetimeRecoveryFixtures genuine publish/publishTerminal/descriptor/installJobEntity/Task/Port. Fresh recovery preseed may use original Database then close it before real managed reopen; never replace positive runtime provider with standalone DB/fake Seed/paid counter. Bounded5sec latch/join/timeout with finally release, no sleeps. All preseeded DI2/DI3/DI4 fixture identifiers must roundtrip unchanged native JobId.parse; structural hyphens in test entropy are replaced with underscores before genuine publication, preserving every fault mode and payment/authority assertion.

Executable examples: [DI1](../../src/test/scala/org/goldenport/cncf/job/JobDurableRuntimeOwnerIntegrationSpec.scala), [DI2](../../src/test/scala/org/goldenport/cncf/job/JobDurableRuntimeOwnerIntegrationSpec.scala), [DI5](../../src/test/scala/org/goldenport/cncf/job/JobDurableRuntimeOwnerIntegrationSpec.scala).

## R18

Actual CLI fixtures use new CncfRuntime.initializeHandle with BootstrapConfig(cwd,args,extraComponents), canonical --textus.binding=@s/platform/default:textus.job.durable.provider=fixture_provider etc before sentinel, --no-default-components and explicit target child test home (--textus.test.home.path=..., --textus.test.home.inherit.runtime=false, --textus.test.home.inherit.repositories=false, --textus.test.home.inherit.local-data=false) plus original --textus.test.descriptor=<owned yaml> for manual cases. Use existing controlled manual/seeded/deterministic descriptor schema. Do not mutate HOME/user.home/global bootstrap state or actual user directories; normal managed caller fixtures use explicit typed admission, not RuntimeBindingAdmissionFixture auto-key guessing. Runtime source assembly merges framework-selected isolated local-data redirection into its single actual Args map after descriptor/CLI/normalized-path maps, retaining existing isolated-root precedence. Initial descriptor discovery removes the empty Args placeholder; final assembly places explicit files before the one effective Args source. Existing Home/Project/Cwd/Env and CNCF-file-before-Textus-file order remain unchanged. CLI > config file precedence is the existing contract. File/Env provenance and exactly-one-Args typed projection remain intact. inherit.local-data=true produces no redirection. IC1..IC3 and DL1/DL3 exercise actual native bootstrap/projection.

Executable examples: [Q5](../../src/test/scala/org/goldenport/cncf/config/JobDurableRuntimeConfigurationSpec.scala), [DL1](../../src/test/scala/org/goldenport/cncf/job/JobDurableRuntimeLifecycleSpec.scala), [IC1..IC3](../../src/test/scala/org/goldenport/cncf/cli/CncfRuntimeIsolatedConfigurationSpec.scala).

## R19

All28 scenarios below are actual AnyWordSpec/Matchers/GivenWhenThen leaves with semantic afterWord metadata spec:durable-job-runtime-owner, example:<id>, rules, phase69.7/sliceJM69-09D1B and adjacent GWT around actual setup/action/oracles. Each of four new Specs has ordinary shrinking seeded property minimum24 successful cases seed69709702; Q6/DO6/DI7/DL6. No source/manifest/phase-status mirror tests, snapshot print, GWT suppression, weakened native assertions or fabricated success totals. Preserve all original77 suites and pending1. Preserve the original25 authored leaves and four properties; add IC1..IC3 as actual CLI leaves in the fifth new spec. IC3 uses ordinary shrinking, seed69709702/min24, over nonempty ASCII alpha strings of length1..8. CncfRuntimeConfigFileSpec is selected read-only for regression.

Executable examples: [Q6](../../src/test/scala/org/goldenport/cncf/config/JobDurableRuntimeConfigurationSpec.scala), [DO6](../../src/test/scala/org/goldenport/cncf/job/JobDurableRuntimeOwnerSpec.scala), [DI7](../../src/test/scala/org/goldenport/cncf/job/JobDurableRuntimeOwnerIntegrationSpec.scala), [DL6](../../src/test/scala/org/goldenport/cncf/job/JobDurableRuntimeLifecycleSpec.scala), [IC3](../../src/test/scala/org/goldenport/cncf/cli/CncfRuntimeIsolatedConfigurationSpec.scala).

## R20

This uncommitted slice does not close JM69-09 or Phase. Original C/D all17 native correlation/security/retention/integrity/backup/restore/migration/hostile fixtures, unwaived M2/CPBs, complete independent protected Step review/local acceptance commits, actual JM69-10 two-process/CBD fresh-runtime and predecessor69.4--69.6 audit, aggregate final full validation, sole original epoch1 full Phase review/final local release remain. Documents show authored/test-pending until actual receipts. No push/publish/deploy/successor/upstream/skill edits.

Executable examples: .

## Actual executable specification traceability

Every row is an actual AnyWordSpec/Matchers/GivenWhenThen leaf with semantic
afterWord metadata `spec:durable-job-runtime-owner`, its example and rules, phase
69.7 and slice JM69-09D1B. Adjacent Given/When/Then clauses surround native setup,
action and assertions. Q6, DO6, DI7, DL6 and IC3 use ordinary ScalaCheck shrinking, seed
69709702 and at least 24 successful generated cases; no result is claimed until
parent validation runs.

| Example | Rules | Specification | Required native behavior |
| --- | --- | --- | --- |
| Q1 | R2 | [Q1](../../src/test/scala/org/goldenport/cncf/config/JobDurableRuntimeConfigurationSpec.scala) | Absent typed bindings give unconfigured; exact provider-only defaults60000/100, no source/factory effect. |
| Q2 | R2 | [Q2](../../src/test/scala/org/goldenport/cncf/config/JobDurableRuntimeConfigurationSpec.scala) | Exact valid ASCII provider including inclusive64chars and both inclusive numeric bounds project unchanged. |
| Q3 | R2 | [Q3](../../src/test/scala/org/goldenport/cncf/config/JobDurableRuntimeConfigurationSpec.scala) | Leading/trailing whitespace, punctuation/classname, empty/null and overlong providers refuse with fixed nonreflecting label; no trim/reflection. |
| Q4 | R2 | [Q4](../../src/test/scala/org/goldenport/cncf/config/JobDurableRuntimeConfigurationSpec.scala) | Negative, fractional, overflow, overlong, nondecimal, below/above limits refuse before parse/effect; numeric-without-provider refuses even otherwise valid. |
| Q5 | R2,R18 | [Q5](../../src/test/scala/org/goldenport/cncf/config/JobDurableRuntimeConfigurationSpec.scala) | Actual closed catalog codec/document resolution recognizes registered aliases and canonical argv; Global/Component targets refuse, only same SubsystemInstance projects values and confidential provider provenance redacts. |
| Q6 | R2,R19 | [Q6](../../src/test/scala/org/goldenport/cncf/config/JobDurableRuntimeConfigurationSpec.scala) | Seeded shrinking valid/invalid whole-value bounds property checks exactly decoded values versus fixed refusal and redaction, at least24 cases. |
| DO1 | R1,R3,R4 | [DO1](../../src/test/scala/org/goldenport/cncf/job/JobDurableRuntimeOwnerSpec.scala) | Unconfigured original Subsystem engine submits/drains normally, no durable provider build/source/native probe; health unconfigured; all previous catalog definitions retained and the three static additions present independently of runtime activation. |
| DO2 | R3,R4 | [DO2](../../src/test/scala/org/goldenport/cncf/job/JobDurableRuntimeOwnerSpec.scala) | Two logical Subsystems choose only their exact registered provider; null/duplicate/invalid/foreign/missing/late registration and concurrent init refuse; ready init calls factory once, no discovery/fallback. |
| DO3 | R5,R6 | [DO3](../../src/test/scala/org/goldenport/cncf/job/JobDurableRuntimeOwnerSpec.scala) | Supplied policies cannot raise original maxima or mismatch attached lifetime; actual fresh engine guard rejects existing work/worker/bridge before factory, no Task body/native publication. |
| DO4 | R9,R10 | [DO4](../../src/test/scala/org/goldenport/cncf/job/JobDurableRuntimeOwnerSpec.scala) | Current full EntityStore overload delegation and Blob five effects each exactlyonce through real lease; original Failure object/throw preserved; close denies new calls without delegate while nested admitted call finishes. |
| DO5 | R14,R15,R16 | [DO5](../../src/test/scala/org/goldenport/cncf/job/JobDurableRuntimeOwnerSpec.scala) | Actual owner probe/startup/registration drives fixed health; provider fault/adverse maintenance degrades sticky, normal notdue/held/shared results healthy; getter adds no source/store/clock/ID call and exposes no raw secret field. |
| DO6 | R9,R12,R14,R19 | [DO6](../../src/test/scala/org/goldenport/cncf/job/JobDurableRuntimeOwnerSpec.scala) | Seeded shrinking bounded real owner operation sequence (ready/query/fault/close/query) retains sticky degradation, one registration and closed gate with native lease counts; at least24 cases. |
| DI1 | R1,R6,R9,R11,R17 | [DI1](../../src/test/scala/org/goldenport/cncf/job/JobDurableRuntimeOwnerIntegrationSpec.scala) | Actual managed SQLite+local persistent Blob owner binds same original engine, genuine native root payment, exact keyed signed revisions1..6 and one business body, complete typed health Ready. |
| DI2 | R6,R7,R17 | [DI2](../../src/test/scala/org/goldenport/cncf/job/JobDurableRuntimeOwnerIntegrationSpec.scala) | Genuine acknowledged Submitted/Pending root from first physical owner closes and reopens fresh managed SQLite/Subsystem; same protected candidate and replay source/port register, next original revision continues, terminal projection/native paid authority retained, no fake Seed. |
| DI3 | R5,R7,R8 | [DI3](../../src/test/scala/org/goldenport/cncf/job/JobDurableRuntimeOwnerIntegrationSpec.scala) | Actual vector eligible first candidate then signed unauthorized/foreign or refused port suffix: first can register/retain conservative paid authority but neither auto worker nor concurrent manual drain starts any body before complete failure; no fallback/refund. |
| DI4 | R6,R7,R8 | [DI4](../../src/test/scala/org/goldenport/cncf/job/JobDurableRuntimeOwnerIntegrationSpec.scala) | Actual missing/corrupt/refused source, malformed ordinal/vector, unavailable Blob and persisted lifetime mismatch fail startup with fixed redacted output before body; real native integrity guards remain original. |
| DI5 | R12,R15,R17 | [DI5](../../src/test/scala/org/goldenport/cncf/job/JobDurableRuntimeOwnerIntegrationSpec.scala) | Genuine terminal TTL record and owned Blob execute registered maintenance callback using real controlled time: acknowledged tombstone then owned cleanup; legal hold/shared bytes remain, the same canonical owned/shared Blob returns OwnershipRefused with adverse maintenance health, native charges reflect only acknowledged transitions. |
| DI6 | R9,R11,R13 | [DI6](../../src/test/scala/org/goldenport/cncf/job/JobDurableRuntimeOwnerIntegrationSpec.scala) | Real async business Task after ingress returns resolves managed datastore through Subsystem under actual body lease; bounded latch overlaps logical shutdown, drain waits for body, pool remains physically open until SystemNode finalizes. |
| DI7 | R6,R9,R12,R19 | [DI7](../../src/test/scala/org/goldenport/cncf/job/JobDurableRuntimeOwnerIntegrationSpec.scala) | Seeded shrinking finite real native admission/drain/maintenance/refusal sequence compares signed original outcomes and genuine paid snapshots without fakecredits; at least24 isolated managed cases. |
| DL1 | R2,R3,R4,R18 | [DL1](../../src/test/scala/org/goldenport/cncf/job/JobDurableRuntimeLifecycleSpec.scala) | Actual CncfRuntime.initializeHandle canonical typed config+extras provider sees final assembly/SPI/import, successful owned native runtime Ready, and handle close finalizes exactly its physical SystemNode. |
| DL2 | R5,R7,R8 | [DL2](../../src/test/scala/org/goldenport/cncf/job/JobDurableRuntimeLifecycleSpec.scala) | Actual startup source blocked latch overlaps submit/manual drain and real timer callback; no ID/reserve/body/sweep before all source candidates succeed/commit, original workers release once afterward (manual and realtime profiles). |
| DL3 | R8,R13 | [DL3](../../src/test/scala/org/goldenport/cncf/job/JobDurableRuntimeLifecycleSpec.scala) | Actual timer registration refuses/throws on owner start; fixed startup failure and ordinary cleanup closes registration/gate/physical handle, no prefix Task body and no unconfigured retry. |
| DL4 | R9,R12,R13 | [DL4](../../src/test/scala/org/goldenport/cncf/job/JobDurableRuntimeLifecycleSpec.scala) | Actual maintenance callback source/sweep is in flight on real binding lease while Subsystem shutdown closes gate/future registration; new callback denied, admitted nested work finishes, drain completes without deadlock or physical close. |
| DL5 | R1,R3,R13 | [DL5](../../src/test/scala/org/goldenport/cncf/job/JobDurableRuntimeLifecycleSpec.scala) | Two logical Subsystems on one real SystemNode resolve same physical managed pool independently; closing one durable owner leaves other ready and usable, only explicit physical owner finalization stops shared pool. |
| DL6 | R14,R16,R19 | [DL6](../../src/test/scala/org/goldenport/cncf/job/JobDurableRuntimeLifecycleSpec.scala) | Actual operator catalog unconfigured/ready/degraded/closed fixtures and unauthorized caller: permission refuses before engine health getter/I/O, legacy categories preserved; seeded shrinking bounded health/query/close sequences min24, no raw provider/path/identity leaks. |
| IC1 | R18 | [IC1](../../src/test/scala/org/goldenport/cncf/cli/CncfRuntimeIsolatedConfigurationSpec.scala) | Actual bootstrap with empty environment, isolated home and canonical provider; single Args/Env, CLI web wins both files, isolated root overrides every local-data value, user.home unchanged. |
| IC2 | R18 | [IC2](../../src/test/scala/org/goldenport/cncf/cli/CncfRuntimeIsolatedConfigurationSpec.scala) | Actual bootstrap with runtime/repository isolation and inherited local data; Textus file wins CNCF file for web/root, no redirection, exact provider and source provenance retained. |
| IC3 | R18,R19 | [IC3](../../src/test/scala/org/goldenport/cncf/cli/CncfRuntimeIsolatedConfigurationSpec.scala) | Seed69709702/min24 shrinking real bootstrap over alpha length1..8; CLI beats file/descriptor, normalized descriptor, isolated root, one Args/Env and exact provider, unique finally-cleaned child and unchanged user.home. |

The original GCF07 E1 catalog witness preserves every prior alias/target/strict-codec
assertion and Phase-55 metadata. Its only changes are cardinality 52 to 55 and the
Oct. 3 version header. Original 77 suites and one pending example stay in the
accumulator; the five new suites add 28 authored leaves without invented totals.

## Evidence and ownership

Positive fixtures own unique `target/configured-durable-runtime/work/case-*` roots,
explicit managed SQLite configuration, persistent local Blob bytes, descriptors and
child test homes. Factories call actual Subsystem managed resolution while its real
binding lease is active, then install the original EntityStore/Job realm into the
explicit context. Original keyed evidence recipes/pairs and genuine native root
publication remain independent from live Task authorization or runtime getters.
Recovery closes the original physical SQLite owner before a fresh managed reopen.
Barriers and joins are bounded to five seconds, release in finally, and use no sleeps.

Actual CLI fixtures call new `CncfRuntime.initializeHandle(BootstrapConfig(...))`
with canonical `--textus.binding=@s/platform/default:textus.job.durable.provider=fixture_provider`,
`--no-default-components`, an owned descriptor for manual execution, and child home
inheritance disabled for runtime/repositories/local-data. DL3 uses the original
realtime ScheduledJobTimer closed inside the begun factory; original scheduler
registration throws, and ordinary owned CLI failure cleanup performs physical close.
No replacement timer, injection API, reflection or standalone positive runtime is used.

## Revision 4 frozen CLI and native recovery correction

The frozen `PHASE-69.7-D1B-cli-recovery-fix01` revision 1 authors this nine-path
correction at control revision 33 IMPLEMENT, pre-review native batch 2 attempt 2/3.
The protected CLI source compatibility boundary is parent-decided; the selected
`cncf_fix_worker_terra` uses GPT-6.1 Sol high. M2 remains unwaived. Step repair cycle 1,
Phase repair cycle 0 and original epoch 1 retain their cumulative history.
The other 23 candidate paths and 142 preserved paths remain read-only.

| Retained D1B receipt | Actual outcome before this correction | Compiler warnings |
| --- | --- | --- |
| PH697-D1B-REP-001 | Generation stopped before tests: stale project-owned Cozy development classpath; generator exit 2. | 0 |
| PH697-D1B-REP-002 | Test/compile failed: raw compiler total **41 errors found**. No tests ran. | **31 warnings found** |
| PH697-D1B-REP-003 | Test/compile failed: raw compiler total **5 errors found**. No tests ran. | 0 |
| PH697-D1B-REP-004 | Test/compile failed: raw compiler total **one error found** (abstract Component). No tests ran. | 0 |
| PH697-D1B-REP-005 | 15 suites, 158 tests: **146 succeeded, 12 failed**, no canceled/ignored/pending. | 0 |
| PH697-D1B-REP-006 | 15 suites, 158 tests: **153 succeeded, 5 failed**, no canceled/ignored/pending. | 0 |

Every retained receipt ended with sbt/wrapper exit 1 and `lock=released`; failed
receipts establish no acceptance. The actual native failure reduction is 12 to 5.
The earlier compile batch consumed three attempts; native batch 2 attempt 1 preceded
this reserved attempt 2/3. Neither the failure history nor any counter is reset.

`VAL-PH697-D1B-CLI-ARGS-SOURCE-01` merges framework-selected isolated local-data
redirection into the single effective native Args map, removes the initial empty Args
placeholder, and places explicit files before that final Args source. This restores
the existing CLI > file precedence while retaining CNCF-file-before-Textus-file order,
lower-source order and provenance. IC1..IC3 exercise actual `bootstrapC` with an empty
environment; DL1/DL3 retain actual `initializeHandle` coverage.
`VAL-PH697-D1B-RECOVERY-ID-ROUNDTRIP-01` replaces only four fixture entropy tokens in
DI2/DI3/DI4 with native-roundtrippable underscores, retaining genuine publication,
paid-prefix, schedule, revision, authentication and fault oracles.
`VAL-PH697-D1B-SHARED-OWNERSHIP-ORACLE-01` records DI5's same canonical Blob as
protected by the owned/shared conflict: `OwnershipRefused`, preserved bytes and
occupancy 2, unchanged native payment/Seed with no refund, and adverse maintenance
health. The original Blob guard and production parser remain authoritative.

There are now **28 authored leaves in five new specs**, preserving the original 25
and adding IC1/IC2/IC3. IC3 uses ordinary shrinking over nonempty ASCII alpha strings
of length 1..8, seed 69709702, minimum 24 successes, with a unique owned absolute
workspace and finally cleanup for each actual bootstrap. These are authored oracles,
not new execution totals. The parent selects **17 representative suites** (the
original 15 plus CncfRuntimeIsolatedConfigurationSpec and the read-only
CncfRuntimeConfigFileSpec); only after an actual representative pass may it run the
**88-suite accumulator** (original 86 plus those two), retaining the original 77 in
order and their one pending example. Same-tree Test/compile is included in testOnly.
Fresh representative/accumulator execution, independent review and acceptance remain
pending. Full validation remains pending at the original aggregate/final release gate.
JM69-09 remains IN_PROGRESS, JM69-10 OPEN, and Phase 69.7 in progress. All original
CPBs/M2, all-17 native correlation/security/retention/integrity/backup/restore/migration
coverage, complete-Step review/local commits, real JM69-10 and CBD fresh-runtime
acceptance, predecessor audit, sole epoch-1 Phase review and final local release
obligations remain open.

## Revision 4 adjacent Given/When/Then correction matrix

These are authored source locations and clauses, without an execution or acceptance
claim. IC3 includes the leaf clauses and the adjacent clauses inside each generated
real-bootstrap case. DI2/DI3/DI4/DI5 preserve their original scenario identities and
native setup/action/oracles; only the frozen identifier or ownership predicates change.

| Example | Actual leaf | Clause line locations | Adjacent Given / When / Then evidence |
| --- | --- | --- | --- |
| IC1 | [IC1](../../src/test/scala/org/goldenport/cncf/cli/CncfRuntimeIsolatedConfigurationSpec.scala#L24) | Given 25; When 35; Then 37 | Given("docs/spec/durable-job-runtime-owner-contract.md R18 IC1: an isolated home, canonical provider binding, and conflicting CNCF/Textus files and CLI values") / When("the actual runtime bootstrap resolves and projects the isolated configuration") / Then("bootstrap succeeds with one Args and Env, exact typed provider, CLI web precedence, isolated local data, and unchanged user.home") |
| IC2 | [IC2](../../src/test/scala/org/goldenport/cncf/cli/CncfRuntimeIsolatedConfigurationSpec.scala#L54) | Given 55; When 65; Then 67 | Given("docs/spec/durable-job-runtime-owner-contract.md R18 IC2: isolated runtime/repositories, inherited local data, canonical binding, and two distinct explicit files") / When("the actual bootstrap projects both explicit files without a direct web or root override") / Then("one Args and Env retain the exact provider, Textus file wins, inherited root stays unredirected, and user.home is unchanged") |
| IC3 | [IC3](../../src/test/scala/org/goldenport/cncf/cli/CncfRuntimeIsolatedConfigurationSpec.scala#L85) | Given 86,90; When 110,129; Then 112,131 | Given("docs/spec/durable-job-runtime-owner-contract.md R18,R19 IC3: bounded nonempty ASCII letters with seed 69709702 and normal shrinking") / Given("docs/spec/durable-job-runtime-owner-contract.md R18,R19 IC3 generated case: unique owned config, test descriptor, isolated home, and canonical binding") / When("the generated case crosses actual native bootstrap and typed projection with an empty environment") / When("ScalaCheck executes the ordinary shrinking property with at least 24 successful examples") / Then("one Args and Env retain CLI precedence, normalized descriptor, isolated root, exact provider binding, and unchanged user.home") / Then("every generated actual bootstrap preserves the isolated configuration contract") |
| DI2 | [DI2](../../src/test/scala/org/goldenport/cncf/job/JobDurableRuntimeOwnerIntegrationSpec.scala#L58) | Given 59; When 71; Then 80 | Given("docs/spec/durable-job-runtime-owner-contract.md DI2: a Published pending root and closed original physical SQLite owner") / When("the new managed binding authenticates the original candidate then advances the original manual timer") / Then("next-revision native checkpoints retain prepaid authority and execute exactly once without Admission reconstruction") |
| DI3 | [DI3](../../src/test/scala/org/goldenport/cncf/job/JobDurableRuntimeOwnerIntegrationSpec.scala#L109) | Given 110; When 137; Then 143 | Given("docs/spec/durable-job-runtime-owner-contract.md DI3: two genuinely Published roots, one eligible prefix and an unauthorized or refused-port suffix") / When("discovery is still incomplete and ingress plus manual drain overlap startup") / Then("the fixed startup refusal closes execution without rolling back or refunding the authenticated paid prefix") |
| DI4 | [DI4](../../src/test/scala/org/goldenport/cncf/job/JobDurableRuntimeOwnerIntegrationSpec.scala#L163) | Given 164; When 194; Then 196 | Given("docs/spec/durable-job-runtime-owner-contract.md DI4: actual managed inputs with independent discovery, Blob, persisted-policy, and native record faults") / When(s"the configured native owner starts with the $mode fault") / Then("failure is fixed and redacted, no canonical body executes, and original recovery guards remain in force") |
| DI5 | [DI5](../../src/test/scala/org/goldenport/cncf/job/JobDurableRuntimeOwnerIntegrationSpec.scala#L208) | Given 209; When 246; Then 248 | Given("docs/spec/durable-job-runtime-owner-contract.md DI5: genuine terminal paid jobs, native owned Blob metadata, one hold and one shared reference") / When("the original registered timer crosses the exact terminal TTL") / Then("one owned payload is physically acknowledged and released, held/shared bytes remain, and native lifetime payments never refund") |


## Current D2 selected evidence

The opt-in preparation governed by R3/R7 is selected-validation-passed /
independent-complete-Step-review-pending. The
[canonical migration journal](../journal/2026/10/2026-10-04-phase-69.7-migration-checkpoints.md#actual-d2-selected-validation-and-retained-execution-history)
records actual results separately from initial authoring and D1B/D1C receipts.
JM69-09/Phase remain IN_PROGRESS, JM69-10 OPEN; original whole-Step obligations,
unwaived M2, both native CPBs and epoch-1 Step 1/3 / Phase 0/3 counters remain.
# D3 original native backup-source binding — 2026-10-04

Status: selected-validation-passed.
review_status=independent-complete-Step-review-pending
initial_authoring_status=authored_test_pending
The private owner receives its actual Subsystem
from the existing provider constructor call. The original Ready owner resolves
the five actual native routes under its original context/lease and binds an opaque
offline source only for a shared original managed Hikari SQLite file and actual
LocalBlobStore. Preparation does not stop the owner. Backup observes actual
successful owner/Subsystem/node/resource completion and never borrows a closed
lease or initiates live shutdown. Original default startup/recovery/constructor
semantics are preserved apart from this private binding.

The [Backup and Restore Contract](durable-job-backup-restore-contract.md) BR3/BR4
owns the behavior; the [D3 journal](../journal/2026/10/2026-10-04-phase-69.7-backup-restore.md#actual-d3-selected-validation-and-retained-execution-history)
records actual selected-validation-passed evidence. Physical restore only awaits independently
trusted fresh normal startup and native integrity; it never publishes Ready.
Original complete-Step/Phase acceptance and all outstanding obligations remain
open, with D2 selected results separate from current D3 passes and initial
authored_test_pending history.
