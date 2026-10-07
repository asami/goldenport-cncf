# Component Knowledge Contribution Hub Specification

Status: frozen main-route contract; implementation and executable scenarios authored, focused validation passed, independent review and Step acceptance pending

Identity: PHASE-73 / P73-MAIN-ROUTE / P73-MR-02-main-route

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
API remains reserved for later parent-controlled branch work if needed.

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

These are active scenarios with passing focused evidence; independent review
and Step acceptance remain pending.
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
generated cases. SBT and wrapper exited 0 with `lock=released`. Independent
review, Step acceptance and broader Phase full validation/closure remain pending.

| Example | Requirement allocation | Executable source and observable outcome |
| --- | --- | --- |
| E1 | R1, R2, R3, R4 | [ComponentKnowledgeHubSpec](../../src/test/scala/org/goldenport/cncf/knowledge/ComponentKnowledgeHubSpec.scala): installed nonactivating provider delivers all opaque bytes, complete metadata and actual instance attribution through the hub. |
| E2 | R4, R6 | Same contract suite: g1 handle passed to supplier; returned g2 is rejected with `snapshot-binding-invalid`, without latest fallback. |
| E3 | R2, R4 | Same contract suite: active ScalaCheck `Prop.forAll` over nonempty arbitrary signed byte vectors and opaque generation labels; `Test.check` requires at least 50 successful exact-delivery cases. |
| E4 | R1, R3 | Same contract suite: unmatched domain is successful empty discovery, with no provider read or unrelated activation callback. |
| E5 | R1, R2, R4, R5 | [ComponentKnowledgeHubAssemblySpec](../../src/test/scala/org/goldenport/cncf/knowledge/ComponentKnowledgeHubAssemblySpec.scala): ordinary standard Server assembly discovers before activation, rejects early read without supplier callback, then one independent consumer receives the full attributed snapshot after production coordinator success. |
| E6 | R1, R4, R5 | Same assembled suite: all six provider/consumer1/consumer2 admission permutations reject during-coordinator probes without supplier read, then two independent consumers read equal complete snapshots, including reuse of one handle through separate owner facades. |

The assembled fixtures explicitly select a temporary standard test descriptor
with system time/random and use `CncfRuntime.buildSubsystem` with runtime
extras and `--no-default-components`. `controlledTestExecutionEnabled` is
false. Temporary descriptors and owned Subsystems are cleaned, `textus.test`
and logging are restored, and `user.home` is preserved. No network server or
launcher is needed.

## Reserved subsequent work

The full Phase execution-stage E3 replacement/failure/compatibility/isolation
matrix, second provider, generation retention and replacement between fresh
assemblies remain subsequent parent-controlled work. Executable example E3
above is the opaque-byte property, not closure of that broader Phase stage.
No hot swapping, automatic retry, update loop, central store, search, reasoner,
domain mapping, persistence, remote service or downstream mutation is added.
TKE Phase 2 and BoK Phase 7.5 handoffs remain supplier obligations of later
closure work; this specification claims no downstream acceptance.
