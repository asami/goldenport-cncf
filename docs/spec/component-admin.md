# Component Admin Behavioral Specification

Status: normative Phase 60.8 ADM-09A specification

This specification is the authoritative behavioral contract for the
operator-facing projection of one loaded Component and its Subsystem context.
It promotes the accepted Phase 60.1–60.7 contracts; it does not add behavior.
The paired [design boundary](../design/component-admin.md) defines ownership
and intent. The historical [implementation note](../notes/component-admin-documentation-visibility-implementation.md)
is non-normative.

## Contract and proof map

Each rule below is testable by the named production module and executable
specification. The specifications use Given/When/Then boundaries and
property-based checks where the admitted value space is finite or generated.

| Rule | Required behavior | Production evidence | Executable evidence |
| --- | --- | --- | --- |
| ADM09-R1 | Identity, selections, provenance, strict schema/codec, and typed safe absence are retained exactly; no axis fallback is allowed. | [`ComponentAdminViewModel`](../../src/main/scala/org/goldenport/cncf/component/admin/ComponentAdminViewModel.scala), [`ComponentAdminViewModelCodec`](../../src/main/scala/org/goldenport/cncf/component/admin/ComponentAdminViewModelCodec.scala) | [`ComponentAdminViewModelSpec`](../../src/test/scala/org/goldenport/cncf/component/admin/ComponentAdminViewModelSpec.scala) |
| ADM09-R2 | Configuration trace and already-resolved primary/Documentation/SourceCode composition are projected without scanning or resolving. | [`ComponentAdminConfigurationCompositionProjection`](../../src/main/scala/org/goldenport/cncf/component/admin/ComponentAdminConfigurationCompositionProjection.scala) | [`ComponentAdminConfigurationCompositionProjectionSpec`](../../src/test/scala/org/goldenport/cncf/component/admin/ComponentAdminConfigurationCompositionProjectionSpec.scala) |
| ADM09-R3 | Contract/model metadata and Phase 59 visibility are exact descriptions and never invoke or grant authority. | [`ComponentAdminContractModelProjection`](../../src/main/scala/org/goldenport/cncf/component/admin/ComponentAdminContractModelProjection.scala) | [`ComponentAdminContractModelProjectionSpec`](../../src/test/scala/org/goldenport/cncf/component/admin/ComponentAdminContractModelProjectionSpec.scala) |
| ADM09-R4 | Runtime/datastore facts retain selected instance, lifecycle, context, and exact declared collection ownership/equality. | [`ComponentAdminRuntimeDatastoreProjection`](../../src/main/scala/org/goldenport/cncf/component/admin/ComponentAdminRuntimeDatastoreProjection.scala) | [`ComponentAdminRuntimeDatastoreProjectionSpec`](../../src/test/scala/org/goldenport/cncf/component/admin/ComponentAdminRuntimeDatastoreProjectionSpec.scala) |
| ADM09-R5 | Documentation selections are exact known manifest resources and reuse existing human Help metadata/routes without scan, read, generation, or fallback. | [`ComponentAdminDocumentationNavigationProjection`](../../src/main/scala/org/goldenport/cncf/component/admin/ComponentAdminDocumentationNavigationProjection.scala) | [`ComponentAdminDocumentationNavigationSpec`](../../src/test/scala/org/goldenport/cncf/component/admin/ComponentAdminDocumentationNavigationSpec.scala) |
| ADM09-R6 | Management catalog contains only exact known Component-owned selections; authorization precedes availability/replay, lifecycle and identity are exact, input is validated, and every outcome is auditable/idempotent where applicable. | [`ComponentAdminAuthorizedManagement`](../../src/main/scala/org/goldenport/cncf/component/admin/ComponentAdminAuthorizedManagement.scala) | [`ComponentAdminAuthorizedManagementSpec`](../../src/test/scala/org/goldenport/cncf/component/admin/ComponentAdminAuthorizedManagementSpec.scala) |
| ADM09-R7 | One validated view projects identically through Web, HTTP, CLI, and machine surfaces; redaction and canonical path/security checks fail closed. | [`ComponentAdminSurfaceSecurity`](../../src/main/scala/org/goldenport/cncf/component/admin/ComponentAdminSurfaceSecurity.scala) | [`ComponentAdminSurfaceSecuritySpec`](../../src/test/scala/org/goldenport/cncf/component/admin/ComponentAdminSurfaceSecuritySpec.scala) |
| ADM09-R8 | Declared component Admin pages and downstream dispatch preserve canonical identity and deterministic absence for aliases, reserved, undeclared, or missing-template pages. | [`WebDescriptor`](../../src/main/scala/org/goldenport/cncf/http/WebDescriptor.scala), [`ComponentAdminSurfaceSecurity`](../../src/main/scala/org/goldenport/cncf/component/admin/ComponentAdminSurfaceSecurity.scala) | [`WebDescriptorSpec`](../../src/test/scala/org/goldenport/cncf/http/WebDescriptorSpec.scala), [`Http4sHttpServerDispatchSpec`](../../src/test/scala/org/goldenport/cncf/http/Http4sHttpServerDispatchSpec.scala) |

## R1 — identity and provenance

Given a caller-supplied view, when it is created, validated, or codec
round-tripped, then Component class, selected logical release and release
candidates, selected loaded instance and instance candidates, Subsystem class
and instance, implicit Component Subsystem, and typed resource state remain
exact. Every field has safe source provenance; a logical identity is validated
and contains no physical path, content, repository, credential, or authority.
The only accepted schema is `cncf.component-admin-view.v1`; unknown fields,
duplicate keys, malformed roles/URIs/text, duplicate candidates, omitted
selection membership, and cross-axis ownership mismatch fail through
`Consequence` rather than selecting a fallback.

## R2 — configuration and composition without scan/resolve

Given a validated view, a `ConfigurationBindingCollection`, and already
resolved `ResolvedComponentResources`, when the configuration/composition
projection runs, then it retains the typed winner, overridden chain, target
scope, redacted values, source ordering/provenance, and exact resource order,
identity, availability, integrity, authorization, and safe physical
provenance. A null or otherwise absent required resolved resource is rejected.
The projection does not inspect files, walk archives, call a resolver, read a
resource, load a Component, or grant authority.

## R3 — contract/model visibility without invocation

Given a validated view and one supplied Phase 59
`ComponentKnowledgeManifest`, when the contract/model projection runs, then
it derives the existing consumer contract only after exact Component and
logical-release equality. Service, Operation, SPI, capability, dependency,
schema, model, relationship, and diagram evidence remains descriptive and
retains its existing identity/provenance. Visibility never calls an Operation,
changes a contract, generates a model, or grants management authority; null,
foreign, and different-release manifests fail closed.

## R4 — runtime/datastore identity and collection equality

Given one validated view and supplied runtime/datastore facts, when projected,
then operational, lifecycle, health, dependency, ClassLoader, datastore,
schema, collection, Entity-ID, and `ExecutionContext` evidence remains exact
for the selected loaded instance. Standalone and MultiUser context evidence is
retained without Component-mode branching. For every Entity-ID input, the
declared entity name must select exactly one existing owner through
`EntitySpace.entityByNameC`; the resulting canonical `EntityId` and its
`EntityCollectionId` must equal the declared backing collection exactly.
Scalar locators, foreign IDs, blank/missing/ambiguous owners, collection
mismatches, and facts for another loaded instance fail deterministically.

## R5 — documentation navigation uses existing Help metadata

Given one validated view, caller-supplied resolved knowledge, the existing
Phase 59 Help contract, and explicit selections for User Guide, Reference
Manual, Scaladoc, model diagrams, examples, source availability, and
troubleshooting, when navigation is projected, then every selected identity
must be an exact member of the same manifest and its route must be the exact
existing human Help route. Admin preserves Help's canonical order and the
complete Help inventory while exposing selected metadata only. Availability,
integrity, authorization, media type, language, digest, logical identity, and
safe provenance remain separate exact facts, including restricted,
unavailable, incompatible, or denied evidence. Foreign Help identity, unknown
resource, category conflict, duplicate selection, and noncanonical/traversal
route fail closed. No scan, resource read, generation, cache, resolution,
Direct-AI invocation, or documentation ownership is introduced.

## R6 — authorized management catalog and outcomes

Given an exact target identity and finite Component-owned registration catalog,
when the catalog is created, then it contains exactly the six admitted
management selectors and no selector duplicated as an ordinary query. Given a
request, when admission is evaluated, then it must match Component,
`ComponentInstanceId`, selected release, Subsystem class/instance, implicit
Subsystem, and current lifecycle evidence; its input must name the same
selector, be explicitly validated, retain safe provenance, and carry safe
representation text and an idempotency key. Current stored Operation
authorization is checked before availability and replay; an action is
display/admission-eligible only when authorized, available, and lifecycle
Active. Each result is one typed outcome — `Admitted`, `Forbidden`,
`Conflict`, `Unavailable`, `StaleInstance`, `RetryRequired`, or
`IdempotentReplay` — with principal, subject, target, selector, input,
idempotency, lifecycle, and disposition audit evidence. The boundary catalogs
and admits only; it never invokes, discovers, resolves, or executes an action.

## R7 — common secured surfaces

Given one validated identity view, accepted documentation navigation, accepted
management catalog, Web descriptor, and caller `ExecutionContext`, when the
surface projection runs, then Web, HTTP, CLI, and machine descriptors retain
the same exact view. Machine JSON must be the strict v1 codec representation.
Documentation output is metadata-only and redacts raw content, physical or
normalized paths, repository locations, credentials, and operational/
activation/MCP/deployment/disclosure authority. Help routes must be canonical
encoded `/help/...` paths without controls, backslashes, query/fragment,
encoded separators, or traversal. Management display eligibility additionally
requires exact target identity, Active lifecycle, visibility, availability,
and current stored authorization; visibility alone is never invocation or
authority.

## R8 — canonical page and downstream absence

Given a Web descriptor, when component Admin pages are projected, then only
exact declared canonical pages under `/web/{component}/admin/{page}` with safe
lower-case canonical segments are projected. Case and underscore aliases,
foreign components, traversal/query/fragment or encoded-separator variants,
reserved dispatcher names (`descriptor`, `entities`, `data`, `aggregates`,
`views`), and undeclared pages are absent from descriptor projection. A
declared canonical page without a template may remain descriptor metadata; when
that page is dispatched, the downstream HTTP dispatcher independently requires
a present template and returns deterministic absence (HTTP 404) if it is
missing. Neither boundary falls back to another page or invokes an alternate
route. `WebDescriptorSpec` proves descriptor-level canonical page admission and
lookup; `Http4sHttpServerDispatchSpec` proves the downstream template
checkpoint and deterministic HTTP dispatch/absence.

## Existing closure evidence

The contract consumes the accepted [Phase 60.1 identity closure](../phase/phase-60.1.md),
[60.2 configuration/composition closure](../phase/phase-60.2.md), [60.3
contract/model closure](../phase/phase-60.3.md), [60.4 runtime/datastore
closure](../phase/phase-60.4.md), [60.5 Help navigation closure](../phase/phase-60.5.md),
[60.6 management closure](../phase/phase-60.6.md), and [60.7 surface/security
closure](../phase/phase-60.7.md). Their upstream ownership is the closed
[Phase 58.9 resource contract](../phase/phase-58.9.md) and [Phase 59.10
knowledge contract](../phase/phase-59.10.md). These links identify accepted
scope and evidence; they do not claim new validation or reopen prior closure.

This promotion changes documentation only. It does not alter source,
executable specifications, schemas, codecs, resolvers, Help, dispatch,
authorization, lifecycle, publication, or deployment.
