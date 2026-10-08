# Component Knowledge Contribution Hub Design

Status: completed 2026-10-08; CNCF supplier acceptance only

Identity: PHASE-73 / P73-MAIN-ROUTE / P73-MR-02-main-route

Branch identity: PHASE-73 / P73-BRANCHES / P73-BR-01-replacement-failure-isolation

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

## Responsibility and reuse baseline

The accepted [P73-A-01 reuse inventory](../journal/2026/10/2026-10-08-phase-73-knowledge-contribution-inventory.md)
establishes the existing installed membership, assembly, instance attribution,
Phase 70 activation and provenance facilities. Its M0 `instanceId` amendment
is incorporated. The [public specification](../spec/component-knowledge-contribution-hub.md)
owns the frozen R1–R9 contracts and shared E1–E15 evidence allocation.
The [supplier package version 1](../spec/component-knowledge-contribution-hub-supplier-handoff.md)
applies those contracts to TKE Phase 2 provider and BoK Phase 7.5 consumer
responsibilities without changing this design.

The contribution hub fills only the missing opaque discovery/read route.
Suppliers prepare complete immutable snapshots and own domain semantics;
CNCF binds declarations to installed provider identity and owning assembly;
consumers receive only the public hub, reference, descriptor and snapshot.
Resource resolution, knowledge manifests and typed KnowledgeSpace retain
their current responsibilities. Payloads do not become CNCF knowledge nodes.

## R1 Membership and supplementary provider capability

An installed `Component` opts in through `ComponentKnowledgeProvider`, a
supplementary self-typed trait. The owning `Subsystem.components` remains the
sole enumeration source. This reuses ordinary admission and scope injection
without changing Component or Subsystem constructors, introducing a second
registry or exposing supplier implementation lookup to consumers.

## R2 Immutable identities and opaque transport

Final case classes hold supplier declaration, schema, provenance and opaque
byte-vector values. A closed enum holds availability. Stable domain identity
and actual Component instance attribution stay separate. The descriptor adds
trusted `component.instanceId` to the immutable declaration and forwards its
metadata accessors, avoiding duplicated identity fields or spoofable supplier
attribution. Existing `KnowledgeSourceRef` is reused without expanded meaning.
The initial ABI supports contract version 1 and exact schema id/version.

## R3 Stateless facade and unique selection

`ComponentKnowledgeHub` is an abstract core role with a private facade
implementation for one Subsystem. Each discovery reads live installed
membership and one declaration vector per provider; it never reads payload.
Unique exact-domain selection prevents admission or presentation order from
becoming precedence. No global/static hub, mutable central store or cached
membership is required. Multiple facades may coexist for one owning assembly.

## R4 Private bound reference and exact delivery

`ComponentKnowledgeContribution` is an abstract core reference with a
package-restricted constructor. Its private bound subtype holds the exact
admitted declaration, actual provider object/instance identity and owning
Subsystem object. Its descriptor is the only public attribution surface.

Read checks owner identity and current installed-provider membership before
using the public supplier capability. It supplies the captured declaration
and admits only a snapshot with exactly equal declaration and a non-null
immutable payload vector. A captured generation therefore succeeds exactly or
fails; neither a replacement provider nor a current/latest generation becomes
an implicit fallback. No persistent generation store or retention policy is
introduced by this route.

## R5 Consume existing successful activation terminality

For an activating provider, read consumes the accepted Phase 70 terminal
state through one package-private read-only observation in
`ComponentActivation`. Existing map/state monitors retain their ownership.
The observation initializes no state and starts no callback, wait or scheduler.
The coordinator and its cleanup, once-only and Server-mode policies remain
unchanged. A nonactivating Ready supplier requires no coordinator activity.

Discoverability and supplier Ready metadata are independent from successful
assembly activation completion. Consumer activation callbacks may perform
negative probes and still return `Consequence.unit`; successful public reads
follow complete coordinator success and rediscovery. All six admission orders
exercise this placement so callback order cannot select successful delivery.

## R6 Structured boundary diagnostics

Provider failures and NonFatal exceptions are converted to service-unavailable
Observation/Taxonomy/Cause/Conclusion values with fixed codes and, when known,
trusted provider instance identity. Supplier conclusion, exception, payload,
domain, generation and caller argument details are discarded. This protects
the exchange boundary while retaining ordinary enclosing Operation authority.
It creates no new HTTP route or security authority.

## Executable allocation and reserved work

The [contract suite](../../src/test/scala/org/goldenport/cncf/knowledge/ComponentKnowledgeHubSpec.scala)
owns normal nonactivating delivery, generation mismatch, an active 50-case
opaque-byte ScalaCheck property and absence. The
[assembled suite](../../src/test/scala/org/goldenport/cncf/knowledge/ComponentKnowledgeHubAssemblySpec.scala)
owns ordinary Server assembly, early-read rejection, one consumer and then
two independent consumers in all six admission permutations. Consumers receive
domain/schema and a public facade; their code has no provider class, object,
factory or lookup dependency. Separate same-owner facades share one handle.

The standard descriptor uses system time/random and the existing production
activation route. Owned Subsystems and temporary descriptors are scoped and
cleaned; `textus.test` and logging are restored without changing `user.home`.
The executable route needs no launcher, network server or downstream checkout.

Parent-verified focused invocation `p73-mr02-final-20261008-01a10b3f` compiled
and passed the assembled suite (7/7), then the contract and existing activation
suites (16/16): 23/23 tests across three suites, zero failures or aborted suites.
The active ScalaCheck property passed with a minimum of 50 successful cases.
SBT and wrapper exited 0 with `lock=released`. This establishes focused
executable evidence. P73-MAIN-ROUTE is accepted at commit
`5111e3238a452008d08a6c1c41d68efd0ed77e16`, after independent review
`p73-mr02-review-20261008-01a10b3f@1` by `/root/p73_mr02_main_route_review`.
Parent M0 `p73-mr02-m0-amendment-20261008-01a10b3f@1` resolved
`CPB-P73-MR02-001`; the metadata amendment passed 11/11 with retained main
evidence 23/23. This accepts the main-route allocation only.

Replacement, failure, compatibility and isolation are frozen in R7–R9 below.
Parent-verified Stage A invocation `p73-e3-red3-20261008-01a10b3f` compiled
on 2026-10-08: pure branches passed 52/52 and assembled branches passed 15/16.
The only failure was E12 B public discovery absent at line 72, after A exchange
and ordinary B activation succeeded: 67/68 across two suites, no aborted suite,
SBT/wrapper exit 1 and `lock=released`. An earlier output-only Cozy stale-classpath
refresh and separate anonymous concrete Component compile correction were
prerequisites, not expected-red evidence.

Stage B added only SecondProvider's supplementary declaration/read capability
over its own prepared state. Parent-verified `p73-e3-final-20261008-01a10b3f`
compiled and passed assembled branches 16/16, then the four-suite accumulator
75/75: 91/91 across five suites, with zero failed, aborted, canceled, ignored or
pending tests. The existing active opaque-byte property passed its minimum 50
successful cases; SBT/wrapper exited 0 with `lock=released`.
Independent E3 review `p73-e3-review-20261008-01a10b3f@1` by
`/root/p73_e3_branch_review` returned PASS. P73-BRANCHES is accepted at
`1c96221e90eea92f73ab1a82d45275ea15436103`. The following preparation-era
status is historical and superseded by the closure evidence above: the supplier
package is prepared;
independent handoff Step acceptance and Phase closure remain open. These local
Step results grant neither full Phase validation nor external consumer acceptance.
No hot swapping, watcher, update loop,
automatic retry, persistence, remote service or domain mapping is admitted.
Broader Phase full validation and closure remain pending.

## R7 Supplier publication and retained generations

Generation is an opaque token scoped by the owning Subsystem object, actual
provider object/instance, domain and captured declaration. Equal labels in
different suppliers or assemblies are unrelated; CNCF never orders labels.
One advertised declaration per domain denotes the supplier's current complete
snapshot. Retained older snapshots are storage for captured readers, not extra
discovery declarations.

The supplier prepares a complete candidate before publishing. The fixture uses
one `AtomicReference` of an immutable advertised declaration plus retained
complete snapshots. Publication replaces bytes, schema and provenance together;
it neither merges nor patches bytes. Preparation failure occurs before mutation
and leaves the complete published g1 intact. This is a supplier obligation,
not a CNCF publication API, cache or persistence service.

Old references continue to request exact captured g1 from the same installed
supplier. While retained, g1 and g2 can both be read independently. Explicit
retirement makes old reads fail `provider-failed` without fallback; previously
returned immutable snapshots and new g2 reads remain intact. The ABI provides
no retention lifetime guarantee. CNCF enforces exact declaration/payload binding,
not supplier storage duration.

## R8 Availability, compatibility, conflict and sanitized failure

Absence is successful `None`; Ready with an empty byte vector is a complete
valid snapshot. Unavailable and Failed remain discoverable and fail reads before
the callback. Exact schema id/version is required. Valid unequal schemas fail
`schema-incompatible`; malformed caller schemas fail `argument-invalid`.
R2 admission remains version 1 with nonblank identities and non-null metadata.

Unique exact-domain discovery rejects every duplicate, including g1/g2 from one
supplier, independent of readiness or admission order. Malformed metadata or a
throwing getter from any installed opted-in supplier fails the whole discovery,
including an unrelated-domain supplier. No selection, priority or fallback API
is introduced.

R6 fixed structured service-unavailable diagnostics remain authoritative.
Getter failures map to `declaration-invalid`; thrown/failed/null callback results
to `provider-failed`; null snapshot/payload or any changed declaration field to
`snapshot-binding-invalid`. Only a fixed code and known trusted provider identity
survive in public rendered and recursive causal forms; fake payload, domain,
generation, schema, exception, URL and private-path sentinels exercise redaction.

## R9 Assembly succession, consumer isolation and inherited readiness

Provider interchangeability closes owned Server A before constructing Server B
with exactly one differently named concrete supplier, the same domain/schema
and two unchanged hub-only consumers. B supplies its complete opaque bytes and
its own trusted attribution. This is successive configuration, not live replacement.
Stored A results remain immutable and A handles are foreign to B before callbacks.

Owner identity is the Subsystem object, even when descriptor values are equal.
Separate same-owner facades accept the same handle. Null/unknown handles fail
`foreign-contribution`; a directly reinitialized captured provider scope fails
`provider-not-installed`. That adversarial fixture does not modify live membership.

Standard Server activating suppliers are readable after ordinary coordinator
success. Other ordinary modes do not invoke that coordinator, even with Ready
metadata. Nonactivating Ready suppliers read after assembly in every ordinary
mode. Controlled admission stays explicit through the existing test seam with
supplied mode/deadline/cancellation/probe. Failed Server terminal state rejects
captured and rediscovered Ready handles without a supplier read, while an
independent healthy owner can succeed with equal logical identity values.
Phase 70 retains all lifecycle, once-only and cleanup ownership.

The public specification owns the shared mode table and E1–E15 checklist matrix,
which also supplies later E4 handoff evidence. The original E3 byte property
remains the regression property; Phase execution-stage E3 is the broader branch
work. No additional property over metadata or duplicate acceptance driver is added.

Focused branch source allocation: [pure E7–E11 branches](../../src/test/scala/org/goldenport/cncf/knowledge/ComponentKnowledgeHubBranchSpec.scala),
[assembled E12–E15 branches](../../src/test/scala/org/goldenport/cncf/knowledge/ComponentKnowledgeHubAssemblyBranchSpec.scala)
and [opaque fixtures](../../src/test/scala/org/goldenport/cncf/testutil/ComponentKnowledgeFixtures.scala).
These sources have the dated focused evidence, independent E3 PASS and exact
accepted Step commit above. The canonical
[Hygiene journal](../journal/2026/10/2026-10-08-phase-73-hygiene-follow-up.md)
retains the accepted separate follow-up debt; it is not branch acceptance.
