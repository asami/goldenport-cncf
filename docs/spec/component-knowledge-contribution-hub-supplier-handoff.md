# Component Knowledge Contribution Hub Supplier Handoff

Package version: 1

Contribution contractVersion: 1

Identity: PHASE-73 / P73-HANDOFF / P73-HO-01-supplier-package-and-status

Status: completed 2026-10-08; CNCF supplier acceptance only

## Phase 73 closure evidence — 2026-10-08

The supplier handoff Step is accepted through independent
`p73-e4-review-20261008-01a10b3f@1` and exact local commit
`ca3045d5bce3f52abc2d6582900f505fb0387ae7`. It completes P73-C-03;
the main-route and branch Step commits and their E1–E15 allocation remain valid.

The sole epoch-1 full review `p73-full-review-20261008-01a10b3f@1`
raised `CPB-P73-FULL-001`. Its two-fixture target-relative descriptor repair
and the expressly admitted global-runtime-context restoration were independently
accepted by `p73-global-context-focused-review-20261008-01a10b3f@2`
from `/root/p73_global_context_focused_review2`, with no remaining blocker.

Current focused validation passed factory 7/7, assembly branches followed by
factory 23/23, and the Phase/lifecycle accumulator 91/91. Required serialized
`sbt --batch test` (`p73-global-context-full-test-20261008-01a10b3f`) passed
4,673 tests across 608 completed suites, zero failed or aborted, with the
reported 13 canceled, one ignored and 46 pending cases retained. SBT/wrapper
exited 0 and the serial lock was released. Seven context-restoration checks
and the active opaque-byte property with at least 50 successes executed.
Validation used Java 25.0.4; no separate Java 17 run is claimed.

All 15 checklist outcomes are complete in the release tree. Four accepted
nonblocking Hygiene records remain open in their original canonical journal;
none is silently repaired or discarded. There are no Development Candidate
records and no empty journal is created. Shared Phase-index/strategy projections
retain concurrent planning with synchronization deferred to their owner.
This is CNCF supplier acceptance only; downstream TKE/BoK acceptance is not claimed.

Release binding: `Phase-Closure-Binding: p73-phase-closure-20261008-01a10b3f@1`.
The completed status takes effect in the distinct local release commit carrying
this binding and its verified committed-tree closure receipt.

## Package authority and recipients

This supplier-owned local package applies the accepted
[R1–R9 specification](component-knowledge-contribution-hub.md) and
[responsibility design](../design/component-knowledge-contribution-hub.md).
Those documents and the executable specifications own contract meaning.
Package version 1 identifies this document; it does not release an artifact,
convert dependency versions or publish anything. [Phase 73](../phase/phase-73.md)
has the CNCF supplier closure recorded above. No downstream acceptance is claimed.

Recipients are repository identities `textus-knowledge-editor` (TKE Phase 2,
provider) and `textus-bok` (BoK Phase 7.5, consumer). Their exact plain portable
plan-document references are `textus-knowledge-editor:docs/phase/phase-2.md`
and `textus-bok:docs/phase/phase-7.5.md`. These target plan-document locations
are recipient identities; their current contents remain unverified because
the checkouts were unavailable. The parent
searched current `/Users/asami/src/dev2026`, `dev2025` and backup repository
containers: neither checkout was available; dev2025 contained only cozy and
backup contained only Maven repositories. This limits verification of their
current plans and implementations. It does not establish a stale repository
identity or an incorrect cross-environment link. No current downstream plan
status is inferred, and historical shared links are preserved.

## Common public contract

The exact ABI is declared by
[ComponentKnowledgeProvider](../../src/main/scala/org/goldenport/cncf/component/ComponentKnowledgeProvider.scala),
[immutable contribution values](../../src/main/scala/org/goldenport/cncf/knowledge/ComponentKnowledgeContribution.scala)
and [ComponentKnowledgeHub](../../src/main/scala/org/goldenport/cncf/knowledge/ComponentKnowledgeHub.scala).
An installed `Component` may implement the optional self-typed capability:

```scala
def knowledgeContributionDeclarations: Vector[KnowledgeContributionDeclaration]
def readKnowledgeContributionC(declaration: KnowledgeContributionDeclaration): Consequence[KnowledgeContributionSnapshot]
```

Consumers use these public entry points:

```scala
ComponentKnowledgeHub.createC(subsystem: Subsystem): Consequence[ComponentKnowledgeHub]
hub.discoverC(knowledgeDomainId: String): Consequence[Option[ComponentKnowledgeContribution]]
hub.readC(contribution: ComponentKnowledgeContribution, expectedSchema: KnowledgeContributionSchema): Consequence[ComponentKnowledgeSnapshot]
```

`Subsystem.components` is the sole membership source. Discovery reads installed
opted-in suppliers' declaration getters once per discovery; it never invokes a
read callback or fetches payload. Zero exact-domain matches return successful
`None`; one binds a handle; any duplicate, including two generations advertised
by one provider, fails conflict. Admission order, name and generation cannot
choose a winner. A malformed opted-in supplier fails discovery even when its
domain would be unrelated to the query.

| Public value | Required content and ownership |
| --- | --- |
| `KnowledgeContributionSchema(id, version)` | Exact nonblank schema identity and version agreed by the domain owners. CNCF does not infer compatibility. |
| `KnowledgeContributionDeclaration(knowledgeDomainId, generation, schema, provenance, availability, contractVersion = 1)` | Complete immutable supplier metadata. Domain and generation are nonblank; schema and its fields are valid; provenance is `Vector[KnowledgeSourceRef]` with no null vector or elements; availability is non-null `Ready`, `Unavailable` or `Failed`; only contract version 1 is admitted. |
| `KnowledgeContributionSnapshot(declaration, payload)` | Complete supplier result with exactly the captured declaration and non-null immutable `Vector[Byte]`. Empty bytes are valid. |
| `ComponentKnowledgeDescriptor(providerComponent, declaration)` | CNCF adds actual installed `ComponentInstanceId` from `provider.instanceId`. Its domain/generation/schema/provenance/availability/version accessors forward to the declaration. |
| `ComponentKnowledgeContribution` | Discovered bound reference exposing `descriptor`; no public constructor or supplier execution field. |
| `ComponentKnowledgeSnapshot(descriptor, payload)` | Consumer result retaining the trusted captured descriptor and exact delivered opaque bytes. |

The stable `knowledgeDomainId` is separate from trusted `providerComponent`.
Suppliers author domain identity and provenance, but cannot supply or spoof
provider attribution. Existing `KnowledgeSourceRef` meaning is unchanged.
CNCF does not parse, normalize, merge, hash, serialize, decode or map the opaque
payload to knowledge nodes. Domain/schema/codec meanings belong to recipients.

Generation is opaque and scoped by the exact owning Subsystem object, actual
provider object/instance, domain and captured declaration. Equal labels in
different suppliers or owners imply neither equality of data nor ordering.
A handle captures that owner, provider and complete declaration. Same-owner
facades may reuse it; foreign/null/unknown handles cannot read. The provider
must still be installed and scoped to that exact owner. Read passes the captured
declaration to its supplier, requiring exact returned declaration equality,
including generation, schema, provenance, availability and version. It never
substitutes a replacement supplier or current/latest generation.

## Publication, availability and lifecycle

Suppliers prepare one complete immutable candidate before publishing advertised
declaration plus retained snapshots as one state. Advertise one current
declaration per selected domain; retained old generations are not additional
discovery declarations. Complete replacement changes bytes/schema/provenance
together, without merge or patch. Failed preparation before mutation leaves
advertised and retained g1 intact. A captured g1 reader can succeed while g1 is
retained alongside g2; explicit retirement can make it fail `provider-failed`
without g2 fallback. Stored immutable consumer results and g2 remain intact.
CNCF guarantees exact binding, not retention duration. Supplier-local policy
owns retention and retirement; this package introduces no publication API,
central cache, persistence, scheduler, watcher or retry contract.

Absence is successful `None`. Ready-empty is a complete attributed snapshot
with an empty byte vector and one selected supplier read. `Unavailable` and
`Failed` declarations remain discoverable but fail distinctly before the read
callback. Ready metadata alone does not prove activation readiness. The
[shared mode/read table](component-knowledge-contribution-hub.md#r9-assemblyproviderconsumer-isolation-and-read-timing)
and [Phase 70 lifecycle](component-activation-lifecycle.md) remain authoritative:
ordinary Server activating providers read only after coordinator success with
`inProgress = false`; Command, Client, Script and ServerEmulator gain no
automatic activation. Nonactivating Ready suppliers need no coordinator after
assembly in any ordinary mode. Controlled tests use only the existing explicit
admitted seam. Failed terminal activation rejects captured and rediscovered
Ready handles before supplier reads. Consumer callbacks may probe early, but
successful delivery follows complete coordinator success; a previously captured
same-owner handle remains readable when its exact declaration is retained;
callback ordering cannot determine success.

Phase 70 retains once-only activation, deadlines/cancellation and managed
cleanup. Use existing owned Subsystem shutdown; final owned shutdown is safe
even if failure cleanup already ran. Contribution discovery/read starts no
activation, wait, server or lifecycle ownership.

## Fixed failures and callback boundaries

Each code below has the exact prefix `knowledge-contribution.`. Diagnostics use
`Conclusion.serviceUnavailable(Observation.failure(Taxonomy.serviceUnavailable,
Cause.message(...)))`. They retain only a fixed code and trusted provider
instance identity when known. Raw supplier failures, exceptions, payload,
domain, generation, caller schema/arguments, URLs and private locators do not
survive display/show/toString or recursive public causes.

| Code | Boundary outcome |
| --- | --- |
| `argument-invalid` | Null owner or null/blank discovery domain; malformed expected schema on an owned installed handle. No supplier read. |
| `declaration-invalid` | Null/malformed declaration vector/member/field/version or NonFatal declaration getter exception. No supplier read. |
| `conflict` | More than one exact-domain declaration; no winner or supplier read. |
| `foreign-contribution` | Foreign, null or unknown handle; rejected before supplier read. |
| `provider-not-installed` | Captured provider no longer installed or its injected scope differs from owner; no supplier read. |
| `unavailable` | Captured Unavailable declaration; no supplier read. |
| `failed` | Captured Failed declaration; no supplier read. |
| `schema-incompatible` | Ready declaration and valid unequal exact expected schema id/version; no supplier read. |
| `activation-not-ready` | Ready activating supplier lacks successful completed owner activation; no supplier read. |
| `provider-failed` | Selected supplier callback throws NonFatal, returns Failure/null Consequence, or cannot return a retired exact generation; callback was invoked. NonFatal guarded read failures also map here. |
| `snapshot-binding-invalid` | Invoked callback returns Success with null snapshot/payload or any altered declaration field; no snapshot delivered. |

Follow the accepted read check order in R4/R8 and the hub source: owner binding,
installed scope, caller schema validity, availability, exact schema, activation,
then supplier callback/result binding. Supplier callback failures are sanitized,
not forwarded as a domain-specific diagnostic contract.

## TKE Phase 2 provider handoff

TKE owns domain authoring, codec and term-to-model meaning. Its installed
Component implements the existing optional capability; no second registration
route or supplier-specific consumer lookup is needed. Agree domain/schema
identity with consumers in the domain-owned layer. The fixture's
`sentinel-domain-73`, `sentinel-schema-73`, generation labels, provenance and
byte vectors are examples only, not downstream IDs, formats or UI contracts.

TKE prepares and validates a complete immutable payload/declaration before
publication, advertises one current declaration per selected domain, supplies
truthful availability and existing provenance, and owns retained-reader policy.
The read method uses its exact captured declaration to locate the complete
retained snapshot. It must not silently return current bytes for an old handle.
Retirement/failure follows the common boundaries above. If preparation uses
activation, TKE uses the unchanged Phase 70 readiness and cleanup contract.

Before downstream acceptance, TKE must verify its actual installed capability,
domain/schema/codec agreement, complete publication and failed-preparation
preservation, exact retained-generation reads/retirement, provenance and
availability, and its admitted mode/readiness/shutdown integration. Supplier-local
validation of domain content remains TKE-owned. The shared CNCF matrix provides
transport evidence; it does not verify TKE's actual implementation or domain
semantics. No downstream commands or modifications are part of this package.

## BoK Phase 7.5 consumer handoff

BoK obtains the public facade with `createC` for its admitted owning assembly,
uses only agreed domain/schema and `discoverC`, and passes the returned bound
handle to `readC` after admitted assembly readiness. It retains the exact
returned descriptor/attribution and opaque bytes; domain-owned code interprets
the payload. It does not bind a TKE class, object, factory or implementation
lookup. Successful absence and each fixed failure are handled explicitly,
without choosing a duplicate provider or latest generation as fallback.

The accepted E12 fixture demonstrates unchanged two hub-only consumers across
successive suppliers: close Server A, construct fresh Server B with one different
supplier implementation/identity and the same domain/schema, then consume B's
complete bytes and B attribution. Stored A snapshots remain intact; an A handle
is foreign to B. This demonstrates configured succession rather than hot swapping.
E6 and E15 cover independent consumers and exact owner isolation without another
acceptance driver.

Before actual consumer acceptance, BoK must verify its public-facade integration,
agreed schema/codec interpretation and provenance use, read placement in its
actual admitted lifecycle, explicit absence/failure handling, unchanged consumer
behavior across selected suppliers, attribution preservation and owner isolation.
BoK owns MCP/search/mapping/persistence and downstream acceptance independently;
none is supplied or accepted by CNCF's opaque exchange evidence.

## Shared executable evidence and runnable selectors

Use the single [E1–E15 matrix](component-knowledge-contribution-hub.md#executable-evidence-allocation),
which links public contract, assembly, pure branch and assembled branch suites,
plus [ComponentKnowledgeFixtures](../../src/test/scala/org/goldenport/cncf/testutil/ComponentKnowledgeFixtures.scala).
The existing [activation lifecycle suite](../../src/test/scala/org/goldenport/cncf/component/ComponentActivationLifecycleSpec.scala)
retains inherited readiness/cleanup coverage. No scenarios or fixture driver are
duplicated here. Fixtures own temporary standard descriptors and Subsystems,
restore `textus.test` and logging, preserve `user.home`, and need no launcher or
network server.

The exact focused logical SBT argv retained by E3 is:

```json
["sbt", "--batch", "testOnly org.goldenport.cncf.knowledge.ComponentKnowledgeHubAssemblyBranchSpec", "testOnly org.goldenport.cncf.knowledge.ComponentKnowledgeHubBranchSpec org.goldenport.cncf.knowledge.ComponentKnowledgeHubSpec org.goldenport.cncf.knowledge.ComponentKnowledgeHubAssemblySpec org.goldenport.cncf.component.ComponentActivationLifecycleSpec"]
```

The first item names the executable, `--batch` is the selected option, and each
`testOnly` string is one SBT task argument, submitted from the CNCF repository through the
registered `cncf_command_runner` and
`/Users/asami/.agents/skills/cncf-sbt-serial-execution/scripts/run-sbt-serial.sh`.
The runner uses scoped escalation on its first attempt for normal caches and
repositories, and completion requires `lock=released` before another independent
top-level SBT. These are runnable selectors, not execution in this documentation
Slice; no isolated cache, publication or direct SBT route is introduced.

Accepted local evidence, in chronological order:

- Main-route expected red `p73-mr02-red2-20261008-01a10b3f`: compiled 11 tests,
  one absence pass and ten intended missing-route/property failures; no aborted
  suites, `lock=released`.
- Main-route final `p73-mr02-final-20261008-01a10b3f`: 7/7 assembled plus 16/16
  contract/lifecycle, total 23/23; active property minimum 50 successes; exit 0
  and `lock=released`. Independent review `p73-mr02-review-20261008-01a10b3f@1`
  by `/root/p73_mr02_main_route_review`, followed by parent M0
  `p73-mr02-m0-amendment-20261008-01a10b3f@1`, resolved `CPB-P73-MR02-001`.
  Metadata evidence passed 11/11, retaining main 23/23. Accepted exact commit:
  `5111e3238a452008d08a6c1c41d68efd0ed77e16`.
- E3 Stage A `p73-e3-red3-20261008-01a10b3f`: compiled 52/52 pure plus 15/16
  assembled, actual 67/68; only E12 missing-B discovery failed after A exchange
  and B activation. Exit 1, zero aborted suites and `lock=released`. Earlier
  output-only Cozy stale-classpath refresh and separate anonymous Component
  compile correction were prerequisites, not the compiled expected red.
- E3 final `p73-e3-final-20261008-01a10b3f`: after SecondProvider's optional
  capability, 16/16 assembled branches plus 75/75 four-suite accumulator, total
  91/91 across five suites; zero failed/aborted/canceled/ignored/pending tests,
  retained property minimum 50 successes, exit 0 and `lock=released`.
  Independent review `p73-e3-review-20261008-01a10b3f@1` by
  `/root/p73_e3_branch_review` returned PASS. Accepted exact commit:
  `1c96221e90eea92f73ab1a82d45275ea15436103`.

Historical preparation status, superseded by the closure evidence above:

These accepted Steps support 13 of the 15 stable checklist outcomes. C-03 awaits
this handoff Step's independent acceptance; C-04 and both Phase status headers
remain in progress. The sole full Phase review, final full serialized CNCF suite,
release commit and closure are still required. No focused result grants those
credits or actual external provider/consumer acceptance. The two canonical
[Hygiene records](../journal/2026/10/2026-10-08-phase-73-hygiene-follow-up.md)
remain separate accepted follow-up debt without reinterpretation.
