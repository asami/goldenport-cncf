# Component Knowledge Contribution Hub Specification

Status: main-route and R7–R9 branch Steps accepted 2026-10-08; supplier handoff prepared, handoff Step acceptance and Phase closure pending

Identity: PHASE-73 / P73-MAIN-ROUTE / P73-MR-02-main-route

Branch identity: PHASE-73 / P73-BRANCHES / P73-BR-01-replacement-failure-isolation

## Authority and scope

This specification records the parent-frozen initial pull route of
[Phase 73](../phase/phase-73.md). Its reuse baseline is the accepted
[P73-A-01 inventory](../journal/2026/10/2026-10-08-phase-73-knowledge-contribution-inventory.md),
including the M0 `instanceId` terminology amendment. Inventory acceptance is
not executable route acceptance. The existing
[Phase 70 activation contract](component-activation-lifecycle.md) remains authoritative.

The route exchanges complete immutable opaque snapshots from installed
Components. CNCF owns discovery, trusted attribution and read binding; the
supplier owns domain meaning and snapshot preparation. The contract adds no
Operation, HTTP endpoint or authorization route. Normal enclosing Operation
authority remains unchanged.

## R1 Installed membership and opt-in

`Subsystem.components` is the sole provider membership source. An already
installed and injected `Component` opts in through the supplementary
`ComponentKnowledgeProvider` capability:

```scala
def knowledgeContributionDeclarations: Vector[KnowledgeContributionDeclaration]
def readKnowledgeContributionC(declaration: KnowledgeContributionDeclaration): Consequence[KnowledgeContributionSnapshot]
```

No second registry, service locator, public provider lookup or direct supplier
implementation binding is introduced. Non-opting Components retain their
ordinary behavior. The facade reads its owning assembly's membership live;
initial-route assembly/configuration membership is stable.

## R2 Separate identities, contract v1 and opaque values

The public immutable values are declared in
[ComponentKnowledgeContribution.scala](../../src/main/scala/org/goldenport/cncf/knowledge/ComponentKnowledgeContribution.scala).

| Value | Public meaning |
| --- | --- |
| `KnowledgeContributionSchema(id, version)` | Exact schema identity and version; both nonblank. |
| `KnowledgeContributionAvailability` | Closed alternatives `Ready`, `Unavailable`, `Failed`. |
| `KnowledgeContributionDeclaration` | Nonblank `knowledgeDomainId` and `generation`, schema, `Vector[KnowledgeSourceRef]` provenance, availability and `contractVersion` defaulting to `1`. |
| `KnowledgeContributionSnapshot(declaration, payload)` | Supplier's complete immutable declaration and opaque `Vector[Byte]`. |
| `ComponentKnowledgeDescriptor(providerComponent, declaration)` | CNCF-owned actual `ComponentInstanceId` attribution plus the admitted supplier declaration. |
| `ComponentKnowledgeContribution` | Abstract discovered reference with a public `descriptor` and no public constructor or executable provider field. |
| `ComponentKnowledgeSnapshot(descriptor, payload)` | Trusted captured descriptor plus exact opaque bytes delivered to the consumer. |

Descriptor accessors `knowledgeDomainId`, `generation`, `schema`, `provenance`,
`availability` and `contractVersion` forward to its declaration, without
duplicating supplier identity. The stable domain and actual provider component
instance are separate identities. Suppliers cannot supply or spoof the latter.

Only `contractVersion = 1` is supported. Null declaration, domain, generation,
schema, schema id/version, provenance vector or availability is invalid;
provenance elements must be non-null. Domain, generation and schema id/version
must be nonblank. Existing `KnowledgeSourceRef` meanings are unchanged.
The hub does not parse, normalize, merge, hash or convert payload bytes to
knowledge nodes. No domain interpretation or persistence codec is supplied.

## R3 Discovery and unique-only selection

The public hub surface is:

```scala
object ComponentKnowledgeHub {
  def createC(subsystem: Subsystem): Consequence[ComponentKnowledgeHub]
}
abstract class ComponentKnowledgeHub {
  def discoverC(knowledgeDomainId: String): Consequence[Option[ComponentKnowledgeContribution]]
  def readC(contribution: ComponentKnowledgeContribution, expectedSchema: KnowledgeContributionSchema): Consequence[ComponentKnowledgeSnapshot]
}
```

`createC` rejects a null Subsystem with a structured failure and otherwise
creates a stateless assembly-scoped facade. There is no static/global hub or
cached provider membership.

`discoverC` rejects null/blank domain IDs. It reads each installed provider's
declaration vector once, validates declarations and matches exact domain
equality. Discovery never invokes a provider read callback or fetches payload.
Null/malformed declaration data and throwing declaration getters fail closed.
Zero matches produce successful `None`; exactly one produces a bound reference;
more than one, including two generations from one provider, produces conflict.
Admission or presentation order cannot choose a winner. There is no prefix,
case, name, hash or latest-generation inference. An explicit provider-selection
API, priority or fallback is outside this contract.

## R4 Descriptor, generation and read binding

A discovered reference captures the exact immutable admitted declaration,
actual provider object and its instance identity, and exact owning Subsystem
object. A private bound subtype implements that reference.

`readC` rejects null, unknown and foreign references. A facade must own the
reference's exact Subsystem object; the captured provider must still be
installed and have matching `Component.subsystem`. Separate facades for the
same owner can consume the same handle. Reads never resolve a replacement
provider or fall back to a current/latest generation.

Read requires a `Ready` declaration and exact requested schema id/version
equality. `Unavailable` and `Failed` fail distinctly without executing the
supplier callback. The selected public capability receives the captured
declaration. Its successful snapshot must be non-null, retain exact declaration
equality (generation, schema, provenance, availability and version included),
and carry a non-null immutable byte vector. Otherwise the read fails with
`snapshot-binding-invalid`. A valid delivery uses the trusted captured
descriptor and exact opaque payload.

## R5 Activation readiness and consumer read timing

Discovery and `Ready` metadata alone do not establish assembly activation
completion. For providers mixing in `ComponentActivation`, read additionally
requires the existing coordinator's successful terminal result with
`inProgress = false`. The package-private read-only observation
`ComponentActivation.isCompleteFor(owner)` returns false for null, absent,
in-progress and failing terminal state. It looks up existing state under the
existing map monitor and observes that state under its monitor, without
creating state, invoking callbacks, waiting or adding lifecycle ownership.

Nonactivating Ready providers need no coordinator call. Ordinary automatic
activation remains Server-only. Command, Client, Script and ServerEmulator gain
no automatic activation; controlled tests retain the existing explicit seam.
Independent consumers may probe during activation, but successful reads occur
only after coordinator success and rediscovery. Provider/consumer callback
ordering cannot determine successful delivery.

## R6 Structured failures and diagnostic scope

Failures use `Conclusion.serviceUnavailable(Observation.failure(
Taxonomy.serviceUnavailable, Cause.message(...)))`. NonFatal provider
exceptions and supplier `Consequence` failures are sanitized; raw exceptions,
conclusions and payload details are discarded. A diagnostic contains only a
fixed code and trusted provider instance identity when known. Payload, domain,
generation, caller argument and exception text must not appear.

The stable codes all use the `knowledge-contribution.` prefix:
`argument-invalid`, `declaration-invalid`, `conflict`, `unavailable`, `failed`,
`activation-not-ready`, `provider-failed`, `schema-incompatible`,
`snapshot-binding-invalid`, `foreign-contribution`, `provider-not-installed`.

## Executable evidence allocation

E1–E6 are accepted active scenarios with passing focused evidence.
The first suite owns public contract meaning; the assembled suite owns ordinary
Server wiring, read placement and independent consumers. Parent-recorded
Stage A expected-red invocation `p73-mr02-red2-20261008-01a10b3f` compiled and
completed: 11 tests in two suites, one absence pass and ten intended
missing-route/property failures, no aborted suites and `lock=released`.
This authoring result grants no acceptance of the implemented route.

Parent-verified focused invocation `p73-mr02-final-20261008-01a10b3f` compiled
and passed `ComponentKnowledgeHubAssemblySpec` (7/7), then
`ComponentKnowledgeHubSpec` plus `ComponentActivationLifecycleSpec` (16/16).
The total is 23/23 tests across three suites, zero failures and zero aborted
suites. The active ScalaCheck property passed with a minimum of 50 successful
generated cases. SBT and wrapper exited 0 with `lock=released`.
P73-MAIN-ROUTE is accepted at commit
`5111e3238a452008d08a6c1c41d68efd0ed77e16`, following independent review
`p73-mr02-review-20261008-01a10b3f@1` by `/root/p73_mr02_main_route_review`.
Parent M0 `p73-mr02-m0-amendment-20261008-01a10b3f@1` resolved
`CPB-P73-MR02-001`; its metadata evidence passed 11/11, retaining main 23/23.
Branch acceptance is recorded below; Phase full validation/closure remain pending.

### Branch focused evidence 2026-10-08

Parent-verified Stage A invocation `p73-e3-red3-20261008-01a10b3f` compiled
and passed `ComponentKnowledgeHubBranchSpec` 52/52, followed by
`ComponentKnowledgeHubAssemblyBranchSpec` 15/16. Only E12 failed: B public
discovery returned `Success(None)` at assembled branch line 72 after A's full
exchange and B's ordinary activation succeeded. Total: 68 tests, 67 passed,
one failed, two suites and zero aborted suites; SBT and wrapper exited 1 with
`lock=released`. An earlier output-only Cozy stale-classpath refresh and separate
exact anonymous concrete Component compile correction were prerequisites;
neither was the compiled expected-red result.

After parent admission of that red, Stage B added SecondProvider's supplementary
capability, advertising its own declaration and returning its complete retained
B snapshot only for exact captured declaration equality. No consumer action,
expected result or production code changed in that Stage B implementation.

Parent-verified final invocation `p73-e3-final-20261008-01a10b3f` compiled
and passed these commands in one serialized invocation:

```text
testOnly org.goldenport.cncf.knowledge.ComponentKnowledgeHubAssemblyBranchSpec
testOnly org.goldenport.cncf.knowledge.ComponentKnowledgeHubBranchSpec org.goldenport.cncf.knowledge.ComponentKnowledgeHubSpec org.goldenport.cncf.knowledge.ComponentKnowledgeHubAssemblySpec org.goldenport.cncf.component.ComponentActivationLifecycleSpec
```

The representative assembled branches passed 16/16. The four-suite accumulator
passed 75/75, retaining the main-route and existing lifecycle scenarios.
Total: 91/91 across five suites, with zero failed, aborted, canceled, ignored or
pending tests. The existing active opaque-byte property passed at least 50
successful generated cases. SBT and wrapper exited 0 with `lock=released`.
Independent E3 review `p73-e3-review-20261008-01a10b3f@1` by
`/root/p73_e3_branch_review` returned PASS. P73-BRANCHES is accepted at exact
commit `1c96221e90eea92f73ab1a82d45275ea15436103`. This accepts the E3
allocation without granting full Phase validation or downstream acceptance.
The [supplier package version 1](component-knowledge-contribution-hub-supplier-handoff.md)
is prepared; its independent handoff Step acceptance, the sole full Phase
review, full validation, release and closure remain pending.

| Example | Rules | Checklist allocation | Public input and observable outcome / executable source |
| --- | --- | --- | --- |
| E1 | R1,R2,R3,R4 | P73-A-02,B-01,B-02 | [ComponentKnowledgeHubSpec](../../src/test/scala/org/goldenport/cncf/knowledge/ComponentKnowledgeHubSpec.scala): installed nonactivating provider delivers normal complete metadata, opaque bytes and trusted instance attribution. Accepted main-route evidence. |
| E2 | R4,R6 | P73-A-03,B-03 | Same contract suite: g1 handle passed to supplier; returned g2 fails `snapshot-binding-invalid` without latest fallback. Accepted main-route evidence, retained by accepted E3 Step. |
| E3 | R2,R4 | P73-B-02,A-03 | Same contract suite: active `Prop.forAll` over arbitrary nonempty signed byte vectors and opaque generation labels; `Test.check` minimum 50 exact-delivery cases. Accepted regression property, retained by accepted E3 Step. |
| E4 | R1,R3 | P73-B-01,B-03 | Same contract suite: unmatched domain gives successful absence with no read or unrelated activation. Accepted main-route portion. |
| E5 | R1,R2,R4,R5 | P73-B-04,PREP-01 | [ComponentKnowledgeHubAssemblySpec](../../src/test/scala/org/goldenport/cncf/knowledge/ComponentKnowledgeHubAssemblySpec.scala): ordinary standard Server discovers pre-activation and rejects early reads, then one consumer reads after coordinator success. Accepted main-route portion. |
| E6 | R1,R4,R5 | P73-B-04,C-01,PREP-01 | Same assembled suite: all six provider/two-consumer admission orders reject during-coordinator probes, then two independent consumers receive equal full snapshots and same-owner facade reuse succeeds. Accepted main-route portion. |
| E7 absence | R1,R3,R4,R8 | P73-A-03,B-03,PREP-03 | [ComponentKnowledgeHubBranchSpec](../../src/test/scala/org/goldenport/cncf/knowledge/ComponentKnowledgeHubBranchSpec.scala): no exact match gives `Success(None)`, callback 0. |
| E7 ready-empty | R1,R3,R4,R8 | P73-A-03,B-03,PREP-03 | Same branch suite: Ready empty vector gives exact attributed complete empty snapshot, callback 1. |
| E7 unavailable/failed | R1,R3,R4,R8 | P73-A-03,B-03,PREP-03 | Same branch suite: both states are discoverable and yield their distinct fixed read failure, callback 0. |
| E8 schema | R2,R3,R4,R8 | P73-A-03,B-03,PREP-03 | Same branch suite: valid unequal id/version gives `schema-incompatible`; null/blank schemas give `argument-invalid`, callback 0. |
| E8 admission | R2,R3,R4,R8 | P73-A-03,B-03,PREP-03 | Same branch suite: null owner/domain, blank query and every malformed declaration vector/member/field/version are rejected. An unrelated malformed opted-in getter also fails discovery, callback 0. |
| E9 conflict | R1,R3,R8 | P73-A-03,B-03,PREP-02 | Same branch suite: distinct suppliers in both orders or one supplier advertising g1/g2 conflict without a winner or read; unique queried supplier among unrelated/non-opt-in components binds exactly. |
| E10 complete replacement | R2,R4,R7 | P73-A-03,B-03,PREP-02 | Same branch suite: complete g2 replaces advertised bytes/schema/provenance together; retained captured g1 still reads exactly and stored results remain unchanged. |
| E10 preparation failure | R2,R4,R7 | P73-A-03,B-03,PREP-02 | Same branch suite: injected pre-publication failure leaves captured and rediscovered complete g1 unchanged. |
| E10 retention lifetime | R2,R4,R7 | P73-A-03,B-03,PREP-02 | Same branch suite: explicit g1 retirement gives `provider-failed` without fallback; g2 and returned immutable results stay intact. |
| E11 supplier failure | R4,R6,R8 | P73-A-03,B-03,PREP-03 | Same branch suite: thrown getter gives `declaration-invalid`/read 0; thrown, failed or null callback gives `provider-failed`/read 1; display/show/toString and recursive public causes retain no fake private sentinels or exceptions. |
| E11 result binding | R4,R6,R8 | P73-A-03,B-03,PREP-02 | Same branch suite: null snapshot/payload/declaration or any altered declaration field gives sanitized `snapshot-binding-invalid`, no returned snapshot. |
| E12 provider succession | R1,R2,R4,R5,R9 | P73-A-04,C-02,PREP-02,PREP-03 | [ComponentKnowledgeHubAssemblyBranchSpec](../../src/test/scala/org/goldenport/cncf/knowledge/ComponentKnowledgeHubAssemblyBranchSpec.scala): close Server A then construct Server B with different supplier implementation/identity, same domain/schema and unchanged two consumers. B delivers its full bytes; A handle is foreign and stored A results stay intact. Stage A missing-B red was observed; Stage B supplementary capability now passes the dated focused invocation above. |
| E13 ordinary modes | R1,R4,R5,R9 | P73-B-03,PREP-01 | Same assembly branch suite: Command/Client/Script/ServerEmulator Ready activating suppliers cannot read or activate automatically; nonactivating Ready suppliers read after assembly in all five ordinary modes. |
| E14 controlled seam | R4,R5,R9 | P73-B-03,PREP-01 | Same assembly branch suite: admitted controlled owner rejects early read, then explicit supplied mode/deadline/cancellation/probe succeeds once and delivers full bytes. |
| E14 failed terminal | R4,R5,R9 | P73-B-03,PREP-01 | Same assembly branch suite: failed ordinary Server captured and rediscovered Ready handles reject reads/callback 0; equal-identity independent healthy owner activates and reads. |
| E15 owner/consumer isolation | R3,R4,R9 | P73-B-03,PREP-02,PREP-03 | Same assembly branch suite: equal descriptor values across different owners deliver only their own bytes; foreign/null/unknown handles reject before callbacks, same-owner facades accept, and direct adversarial supplier scope rebinding gives `provider-not-installed`. |

E7–E15 are accepted active scenarios with the dated focused validation,
independent E3 PASS and exact Step commit above. Their domain-specific shared fixture is
[ComponentKnowledgeFixtures](../../src/test/scala/org/goldenport/cncf/testutil/ComponentKnowledgeFixtures.scala).
It owns supplier selection and initialization; consumers know only the public
hub/domain/schema. SecondProvider is a separate ordinary activating Component
with its own complete prepared state and supplementary supplier capability,
implemented after the parent admitted compiled missing-B red. E4 reuses this
same matrix for supplier handoff; P73-C-03/C-04 still require their own handoff,
independent review, final validation and release evidence.

The assembled fixtures explicitly select a temporary standard test descriptor
with system time/random and use `CncfRuntime.buildSubsystem` with runtime
extras and `--no-default-components`. `controlledTestExecutionEnabled` is
false. Temporary descriptors and owned Subsystems are cleaned, `textus.test`
and logging are restored, and `user.home` is preserved. No network server or
launcher is needed.

## R7 Complete publication, opaque generation scope and retention

Generation is an opaque token scoped by exact owning Subsystem object, actual
provider object/`ComponentInstanceId`, domain and captured declaration. Equal
labels across suppliers/assemblies are unrelated and do not imply order/latest.
A supplier advertises only one current declaration per domain. Older retained
snapshots are not additional discovery declarations.

A supplier prepares one complete immutable candidate before atomically publishing
advertised declaration plus retained snapshots as one state. Successful g1→g2
publication replaces complete bytes/schema/provenance together, without merge or
patch. Failed preparation before mutation leaves advertised and retained g1
unchanged. This obligation is supplier-owned; the ABI adds no publication API,
registry, cache, persistence, watcher, scheduler or retry.

A captured reader requests exactly g1 from the same installed supplier. Retained
g1 and current g2 can both succeed independently. After explicit g1 retirement,
old reads fail `provider-failed` without g2 fallback; new reads and previously
returned immutable snapshots stay intact. CNCF guarantees binding, not a retention
lifetime. Altered declaration or malformed snapshot retains R4 rejection.

## R8 Availability, compatibility, conflict and boundary failure

Successful `None` means absence. Ready with `Vector.empty[Byte]` is valid complete
empty data. Unavailable/Failed remain discoverable; their fixed read failures occur
before callbacks. Valid unequal expected schema id or version fails
`schema-incompatible`; null/blank expected schema fields fail `argument-invalid`.
R2 admits only version 1 and nonblank identities with non-null metadata vectors
and provenance elements; `KnowledgeSourceRef` meanings are unchanged.

Every duplicate exact domain fails `conflict`, including two generations from
one supplier. Readiness, name, admission order or generation labels select no
winner. Invalid metadata or a throwing getter in any installed opted-in supplier
fails discovery `declaration-invalid`, even for an unrelated domain.

The R6 structured service-unavailable codes remain fixed. Selected callback
NonFatal, null Consequence or supplier Failure maps to `provider-failed`.
Null snapshot/payload or any changed declaration field maps to
`snapshot-binding-invalid`. Public Conclusion display/show/toString and direct
or recursive causal forms/accessors retain only fixed code and trusted provider
instance when known, never source conclusion/exception, bytes, domain, generation,
caller schema, URL or private fixture locator.

## R9 Assembly/provider/consumer isolation and read timing

Contribution owner identity is exact Subsystem object identity. Equal descriptor
values in different owners never share handles. Foreign/null/unknown handles fail
before callback; same-owner separate facades accept the same handle. Directly
rebinding a captured provider object's scope to another owner fails
`provider-not-installed`; the adversarial fixture performs no live membership change.

Provider replacement closes owned Server A, then constructs fresh Server B with
one selected supplier, same domain/schema/configuration and unchanged consumers.
Two independent concrete supplier classes have different actual identities and
complete opaque snapshots. B attribution changes while domain stays stable;
stored A results stay immutable and A handles remain foreign to B.

| Assembly mode / supplier | Coordinator/read timing | Public result |
| --- | --- | --- |
| Standard Server / activating Ready | Ordinary assembly discovers metadata; read only after production coordinator succeeds | Before completion `activation-not-ready`; afterwards exact snapshot |
| Standard Command, Client, Script, ServerEmulator / activating Ready | No coordinator from build or read | Discoverable; `activation-not-ready`, callback 0 |
| Every ordinary mode including Server / nonactivating Ready | Post-assembly read needs no coordinator | Complete snapshot |
| Controlled admitted owner / activating Ready | Explicit existing `activateForControlledTestC` with supplied mode/deadline/cancellation/probe | Before seam `activation-not-ready`; after success exact snapshot |
| Standard Server / failed activation terminal | Captured and rediscovered Ready handles cannot read | `activation-not-ready`, callback 0; independent healthy owner still succeeds |

Phase 70 owns unchanged once-only activation and cleanup. Ready metadata is not
successful activation. Equal logical instance/domain/generation values cannot
contaminate another owner's terminal state. Final owned shutdown is safe even
when failure cleanup already ran.

## Subsequent closure work

The Phase execution-stage E3 branch contract is frozen above. Stage A's compiled
missing-B red and Stage B's complete capability have the dated focused evidence
above, followed by independent E3 PASS and the exact accepted Step commit.
Executable example E3 remains the opaque-byte property, not broader Phase closure.
No hot swapping, automatic retry, update loop, central store, search, reasoner,
domain mapping, persistence, remote service or downstream mutation is added.
The prepared [supplier package version 1](component-knowledge-contribution-hub-supplier-handoff.md)
contains TKE Phase 2 provider and BoK Phase 7.5 consumer instructions using this
unchanged contract. Handoff Step acceptance and final Phase closure work remain
pending; this specification claims no downstream acceptance.
