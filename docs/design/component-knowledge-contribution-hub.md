# Component Knowledge Contribution Hub Design

Status: frozen main-route decisions; implementation authored, focused validation passed, independent review and Step acceptance pending

Identity: PHASE-73 / P73-MAIN-ROUTE / P73-MR-02-main-route

## Responsibility and reuse baseline

The accepted [P73-A-01 reuse inventory](../journal/2026/10/2026-10-08-phase-73-knowledge-contribution-inventory.md)
establishes the existing installed membership, assembly, instance attribution,
Phase 70 activation and provenance facilities. Its M0 `instanceId` amendment
is incorporated. The [public specification](../spec/component-knowledge-contribution-hub.md)
owns the frozen R1–R6 contracts and E1–E6 evidence allocation.

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
executable evidence; independent review and Step acceptance remain pending.

Full replacement, failure, compatibility and isolation branch evidence,
second-provider interchangeability, retention policy and final TKE/BoK handoffs
remain later parent-controlled work. No hot swapping, watcher, update loop,
automatic retry, persistence, remote service or domain mapping is admitted.
Broader Phase full validation and closure remain pending.
