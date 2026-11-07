# ID Design — Canonical, Self-Describing Identifiers

status: decided

Status (as of 2025-12-31):
- CanonicalId is provided by goldenport core
- CNCF treats CanonicalId as opaque and uses it for correlation only
- CNCF does not generate CanonicalId

This document defines the canonical ID design for this system.
It records an architectural decision that has already been reached
through prior discussions and exists to prevent accidental reintroduction
of known anti-patterns.

---

## Core Principle

**IDs are opaque to program logic, but self-describing to humans and observability tools.**

UniversalId labels (`major`, `minor`, `kind`, `subkind`) use the restricted grammar `[A-Za-z][A-Za-z0-9_]*`.
This restriction exists for transport safety: these identifiers are expected to pass through HTTP and to
appear in URLs in their canonical form without additional escaping. Labels must begin with an ASCII letter. Hyphen is reserved as the structural
separator of the canonical UniversalId format and therefore MUST NOT appear inside labels. Parsers must
reject out-of-grammar inputs rather than infer alternative label boundaries.

IDs must support:
- observability
- debugging
- log correlation
- AI-assisted inspection

Observability identifiers that reach CLI adapters are rendered through the Presentable stdout/stderr contract locked in `docs/notes/phase-2.8-infrastructure-hygiene.md#purpose-aware-string-rendering-candidate` (see A-3 for the Phase 2.8 policy).

while remaining **non-semantic for program logic**.

---

## EntityId Runtime Namespace

`EntityId.major` and `EntityId.minor` are operational partition labels. They
are not system, subsystem, or component layer names.

The canonical interpretation is:

- `major`: the largest operating scope, such as customer, tenant,
  organization, or the default single operating scope.
- `minor`: the operating segment inside `major`, such as region, district,
  site, or the default global segment.

The intended multi-tenant deployment uses a tenant as the `major` partition;
other deployment policies may choose a different top-level operating scope.
The pair `(major, minor)` identifies a logical partition. Future physical
placement will map that pair to a cluster, with physical isolation per tenant
as the intended deployment scenario. Keep the placement mapping in deployment
or routing configuration so moving a partition to a different cluster
preserves existing entity IDs. This describes planned routing; the current
namespace alone does not establish physical isolation.

`TenantId` and `OrganizationId` are dedicated `UniversalId` types. A
`TenantId` identifies the tenant itself; the deployment associates that
tenant with its operational namespace. An `OrganizationId` identifies a
business organization. When membership in that organization governs access,
the group reference carries the same `OrganizationId`.

The standard CNCF runtime namespace is:

```text
major = single
minor = global
```

This `single/global` pair is valid for production systems that intentionally
run as one operating partition. Deployments that separate customers, tenants,
districts, regions, or sites must override the namespace in runtime
configuration.

The primary configuration keys are:

```hocon
textus.id.namespace.major = single
textus.id.namespace.minor = global
```

Runtime namespace resolution uses this precedence:

1. runtime configuration
2. SAR descriptor default
3. CAR deemed-subsystem default
4. CNCF default `single/global`

`public/global` may be used explicitly for public shared content, but it is not
the CNCF default because `public` describes publication semantics rather than a
neutral operating partition.

The old `sys/sys` namespace is not part of new design. Existing implementation
or fixture uses are compatibility debt and should be replaced by
`single/global` or by an explicit runtime namespace.

System, subsystem, and component identity belongs in descriptors, collection
namespace, runtime metadata, and observability context. Application logic must
not parse `EntityId.major` or `EntityId.minor` to recover component structure.

## Owner-Level Typed Identity Contract

Issue one `ExecutionContextId` when an execution context begins and propagate
it for the lifetime of that context. Transition, authorization and audit
records produced in that context carry the same ID, including when access is
denied. Each distinct event retains its own `EventId`. A trace can span
several execution contexts; each context keeps its own identity within it.

### Distributed tracing identity

| Identity | Issuance and propagation rule |
| --- | --- |
| TraceId | Continue the accepted incoming trace across services, nested calls and asynchronous handoffs in the same trace. Start a new trace at a root without valid incoming context or an explicitly configured restart boundary. |
| SpanId | Each instrumented unit of work creates its own span ID and records the active parent span ID. Siblings have distinct span IDs and the same parent. Events and logs within a span carry its current ID. |
| CorrelationId | CNCF-specific optional identifier for a related operation or workflow. Establish it once at the chosen correlation boundary before fan-out, then propagate it through branches, messages and retries. A policy that opens a new correlation boundary (such as a new saga) creates one value for that boundary and shares it with the event and submitted job. |

At an instrumented HTTP boundary, propagate the active span context using
[W3C Trace Context](https://www.w3.org/TR/trace-context/). On receipt,
start the local span with the accepted remote context as parent. Carry trace
context in asynchronous message metadata; when an operation combines multiple
causes, represent additional causal relationships with span links. Keep any
CNCF correlation metadata separate from the standard trace and span fields.
The adapter between typed UniversalId values and wire trace/span IDs must
preserve identity through propagation and export. Export the recorded span
and parent IDs so the receiver and collector reconstruct the same trace tree.
See [OpenTelemetry Tracing API](https://opentelemetry.io/docs/specs/otel/trace/api/).

### Saga execution identity

`SagaId` is a dedicated UniversalId-based identity for one saga execution.
Issue it once when the saga starts. Under `SameSaga`, carry that ID through
events, submitted jobs and retries. Under `NewSaga`, issue a distinct ID at
the new saga's start and pass that value to all its participants.
`CorrelationId` follows its own propagation policy so observers can relate
multiple sagas. A correlation value cannot supply a missing saga identity.

### Workflow definition and execution identity

`WorkflowDefinitionId` and `WorkflowInstanceId` are dedicated UniversalId
types. Issue the definition ID when creating a workflow definition and
restore the same ID when reloading it. Each new execution of that definition
gets a distinct instance ID and refers to the same definition ID. Persist
the instance ID with the execution state and reuse it on resume or retry
within that execution.

### Job, action and task attempt identity

`JobId`, `ActionId` and `TaskId` are dedicated UniversalId types. Issue a
`JobId` for each new job and retain it across retries of that job. Issue an
`ActionId` for each logical action; retrying that action retains its ID,
while starting a distinct action, such as compensation, gets a new one.
Issue a `TaskId` for each execution attempt. If the scheduler assigns the
ID before enqueueing an attempt, the worker uses that ID when it runs it.
For example, a failed action and its retry share the job and action IDs but
have distinct task IDs, so both outcomes can be traced.

### Operation evaluation identities

All four operation evaluation IDs are dedicated UniversalId types. Issue one
`OperationEvaluationExecutionId` per operation invocation and retain it across
retries. A nested invocation has its own ID and refers to its parent execution.
Issue a fresh `OperationEvaluationAttemptId` when each attempt begins.

Give each recorded fact, including start and terminal facts in the same attempt,
its own `OperationEvaluationFactId`. Preserve that ID on redelivery or reload.
`OperationEvaluationIntentId` identifies one request to stage a supplemental
fact in the unit of work. Retain the intent ID while processing that request;
the enclosed fact keeps its own fact ID.

### Rule identities

`RuleSetId` and `RuleId` are dedicated UniversalId types for a rule set
and an individual rule definition. A revised version of the same set keeps
its `RuleSetId` and carries a new `RuleSetVersion`. Configuration selects
the set by `RuleSetName` and version, then resolves `RuleName` within that
selected set version to the corresponding `RuleId`.

`RuleActivationId` is a dedicated UniversalId-based identity for one
activation of a rule on matched facts. Distinct activations of the same rule
carry distinct activation IDs and refer to the same `RuleId`. Reloading or
resuming an existing activation preserves its ID.

`RuleActionPlanId` is a dedicated UniversalId-based identity for one action
plan associated with a rule. Reloading or referencing the same plan retains
its ID. A job started from that plan refers to its executing action by the
separate `ActionId`.

`FactId` is a dedicated UniversalId-based identity for one fact in rule
evaluation. Rules declare their inputs and outputs by `FactName`. Facts with
the same name can have distinct IDs. Issue a new `FactId` for a newly derived
fact and retain the input fact IDs in its provenance (`sourceFactIds`).
The knowledge graph uses its own `KnowledgeFactId` for a knowledge fact.

`DecisionTableId` identifies a decision table and `DecisionTableRowId`
identifies one row that defines conditions and output. Both are dedicated
UniversalId types. Each evaluation references the table and, when a match is
selected, its row ID. Reordering rows preserves their IDs. Retaining the
historical content of an edited row requires a separate versioning policy.

### Work area identity

`WorkAreaId` is a dedicated UniversalId for one allocated work area. Issue
it at creation and retain it through cleanup. Two independently allocated
areas with the same logical name, such as `system`, have distinct IDs.

### Concurrency scope identity

`ConcurrencyScopeId` is a dedicated UniversalId for the scope whose members
share a concurrency limit. Resolve the configured `ConcurrencyScopeName` to
that ID within the runtime's admission registry. Acquire and release against
the same scope. Each successful acquisition returns its own `ConcurrencyLease`.

### Component and service identities

`ProcessCapabilityId` is a dedicated UniversalId for a process execution
capability. Configuration selects it by `ProcessCapabilityName`. Repeated
invocations of the same capability use the same ID. The runtime uses that
identity to resolve the program definition and grant, then evaluates the
execution policy for each request.

`ComponentId` and `ComponentInstanceId` follow the existing shared Java
identity contract in `cncf-collaborator-api`. Preserve the public API,
qualified-name and instance-label formats, parsing and equality semantics for
current callers. Migration to dedicated UniversalId-based identities is a
future joint change for the shared API, CNCF and their consumers. Before
selecting that work, define how existing descriptors and stored references
resolve to the same components and instances across the transition.

`ServiceContainerOwnerId` references the logical runtime instance responsible
for a managed service's lifecycle. It carries a `ComponentInstanceId` for a
component-owned service or a `SubsystemInstanceId` for a subsystem-owned
service. Reuse the existing instance identity in this role. A restart that
resumes responsibility for the same service retains the logical instance ID.

`ServiceContainerId` is a dedicated UniversalId-based identity for a logical
service. Configuration selects the service by `ServiceContainerName` within
the owner's scope. Recreating the backing container preserves the service ID;
the provider's external ID identifies the replacement container.

Use `ProviderContainerId` for the external ID issued by the container
provider (for example, Docker's container ID). The gateway uses that value
in the issuing provider's scope. Reserve `ServiceContainerInstanceId` for
a future dedicated UniversalId-based type if CNCF needs to issue its own
identity for a container instance.

`ComponentStyleId` is a dedicated UniversalId-based identity for a component
style. Support selection by `ComponentStyleName`: the catalog resolves the
configured name and major version to the style's ID. Keep the existing
`family.name@major` and `name@major` forms as name-based selectors.

`ComponentStyleProviderId` is a dedicated UniversalId-based identity for the
style provider. Use `ComponentStyleProviderName` for name-based selection;
resolve the configured name to the provider ID when matching a style snapshot
to its catalog.

`ComponentCapabilityId` is a dedicated UniversalId-based identity for a
capability provided by a component. In configuration, select the capability
by `ComponentCapabilityName` and major version; the catalog resolves the pair
to the capability ID.

Subsystem identities follow the same rule. `SubsystemCapabilityId` is a
dedicated UniversalId-based ID for a capability required from the subsystem;
the catalog resolves `SubsystemCapabilityName` and major version to this ID.
`SubsystemInstanceId` is a dedicated UniversalId-based ID for a subsystem
instance. Configuration can select an instance by subsystem name and instance
label. Preserve existing `family.name@major` catalog entries and
`subsystem/instance` configuration references when implementing this change.

`EventTypeId` and `EventId` are dedicated UniversalId-based types.
`EventTypeId` identifies an event type definition. Each occurrence of that
type refers to the same `EventTypeId` and has its own `EventId`.

`KnowledgeNodeId` is a dedicated UniversalId-based type for an independently
identified knowledge node. Record the source Information, its context and the
typed relation to an RDF node explicitly. For a display or search projection,
reuse the stable correspondence to the source Information when projecting it
again in the same context and role. A title can help find candidates; equal
titles or a shared RDF target alone do not establish node identity across
contexts or Commons.

`KnowledgeRelationshipId` is a dedicated UniversalId-based type. Its usage
remains open, including when to issue or reuse an ID during projection and
how to determine relationship identity across contexts.

`KnowledgeFrameId` is a dedicated UniversalId-based type identifying a
knowledge frame. When frames are projected from the same Information for
different purposes or queries, the rules for issuing or reusing a frame ID
remain open.

`KnowledgeFactId` is a dedicated UniversalId-based type identifying one
recorded fact about a node or relationship. Whether a correction revises the
fact under its existing ID or creates a new fact with its own ID remains open.

`KnowledgeEvidenceId` identifies an evidence record; its source reference
identifies the material cited. `KnowledgeProvenanceId` identifies a record of
origin or derivation. Both are dedicated UniversalId-based types. The RAG
component owns the seventh type, `KnowledgeFragmentId`, a dedicated
UniversalId-based identity for an indexed knowledge fragment.

For these seven types, the owning model defines issuance granularity, reuse
on projection and identity through correction. RDF node and predicate names
and identifiers issued by external systems retain their separate contracts.

`SessionId` is a dedicated UniversalId-based type issued independently for
each session. A user session records its associated `UserId`; one user can
have multiple concurrent sessions. For example, logging in on a phone and a
browser creates two distinct session IDs linked to the same user. Access and
refresh tokens are credentials with their own lifecycles.

Each media object has its own dedicated UniversalId-based type: `ImageId`,
`AudioId`, `VideoId` or `AttachmentId`. Store the storage provider's file ID
separately as an external reference in the context of that provider. Moving
an existing image to another provider updates its storage reference and
preserves its `ImageId`; the same rule applies to audio, video and attachments.

By default, define a dedicated ID class for each identity purpose, based on
`UniversalId`. For entities, use the `EntityId` specialization shown below.
The opaque part normally uses a compressed UUID. A purpose may supply another
value within the UniversalId format while retaining the full typed ID.

Retain `org.goldenport.datatype.Identifier` for names and codes that follow
its existing grammar. Preserve external identifiers under their issuer's rules.

`org.goldenport.datatype.ObjectId` is obsolete. Use purpose-specific types
for new fields. Migrate existing uses according to what each field represents,
preserving existing public API and stored-data compatibility during migration.

`GitObjectId` is an external identifier defined by Git. Keep its current
type and preserve the Git object ID verbatim in storage and when referring
to Git objects. Its parser accepts exactly 40 or 64 lowercase hexadecimal
characters.

`GitSelectionId` is a CNCF-issued UniversalId for a selection of repository
paths. Staging, checking the selected changes, and committing carry the same
ID. Configuration may refer to the selection by `GitSelectionName`.
After resolving the name, check the repository scope, allowed paths, and
permitted operation before execution.

`McpServerId` identifies an MCP server registration in CNCF. Issue a dedicated
UniversalId when the server is registered and retain it across configuration
reloads and renames of that registration. Configuration selects a registration
by `McpServerName`. Session management and tool references use the resolved ID.

`McpServerSetId` identifies a registered set of MCP servers. Issue a dedicated
UniversalId when the set is registered. Adding or removing a member preserves
the set ID. Separately registered sets receive distinct IDs even when they have
the same members. Configuration selects a set by `McpServerSetName`.
During migration, the existing configuration `id` and the service contract
names `mcp-client/<id>` and `mcp-client-transport/<id>` must continue to
resolve to the same registered set.

`OperationToolSetId` identifies a registered set of operations exposed as tools.
Issue a dedicated UniversalId at registration and retain it when the allowed
operations or execution limits change. Configuration selects the set by
`OperationToolSetName`. Each call must satisfy the resolved set's current
operation permissions and limits. During migration, existing configuration
IDs and `operation-tool/<id>` contracts must resolve to the same registered set.

`WebDisplayFormatPolicyName` selects a Web date or date-time display policy
by name. Preserve existing configuration values such as `localized-medium`
and `application-default`, along with the current parsing and naming rules.
Introduce a dedicated UniversalId-based `WebDisplayFormatPolicyId` only if
individually registered policies need stable references across renames.

Event delivery uses the issued EventId as the event identity. Resolve retries
by the complete structural delivery key and verify the referenced EventId.
The index requires no separately issued or hash-derived identity.

For DataStore, retain `DataStoreEntryId` as the dedicated UniversalId for a
record that needs a store-issued identity. Carry an existing typed UniversalId
in `UniversalEntryId`. Preserve literal storage keys in `StringEntryId`.
Both normal injection and startup import must address the same existing row
on read, update and delete after a migration. Keep collection lookup scoped
to its existing namespace.

For an ordinary create without an ID, issue a DataStoreEntryId once. For
repeatable seed imports, require a fixed ID for every entry and validate all
entries in the seed before its first write. Issue new UniversalIds once when
authoring the seed. For existing rows, record the verified storage keys in
the seed so that subsequent imports update the same rows in any input order.

Startup import and `DataStoreSpace.importSeed` must share key resolution and
create-or-update behavior. Only an explicit missing-row or duplicate-key
result may trigger the alternate operation. Propagate other storage errors.

Apply the same registration and name-selection rules to capability bundles
and registered workspaces. Preserve family, name and major version for bundle
lookup. Resolve a workspace name within its registry and check the grant and
trusted directory binding for that workspace at execution. A validation check
selected by name within a workspace keeps its exact configured spelling.

Model-element and vocabulary references retain both their source namespace and
local key. Transport session handles retain the value issued by the peer.
For user authentication, textus-user-account must return
`UserPrincipalId(userId: UserId)` built from the account's complete UserId.
If that ID cannot be restored, authentication fails. Authorization, stored
ownership and audit attribution use the same authenticated subject.

The public session response may expose a shortid for display within a user
context. Omit an unavailable handle. Never substitute a login name, email,
internal principal or shared default for it. Review existing consumers of the
public `principalId` field before changing its wire contract.

Migrate a legacy owner value only when it maps unambiguously to one account
in the applicable scope. Leave ambiguous values such as `"user"` unresolved
for individual review and grant no ownership through that ambiguity.

ID forms outside the UniversalId family serve special purposes. For example,
`shortid` supports compact display and entry where the UI already establishes
the object kind and lookup scope. For each such form, specify its purpose, the
scope in which it identifies an object, and how it resolves to the full ID
when used as an object reference.

`PrincipalId` identifies the acting subject in the security context. For a
user subject, `UserId` is its underlying identity. When a user acts through
different sessions, each session refers to the same user principal. An owner
reference to that subject must preserve the identity used for authorization
through storage and retrieval. A UI shortid may be used to look up the user
within a known context; authorization uses the resolved subject identity.

Proposed Scala type shape:

```scala
abstract class PrincipalId
case class UserPrincipalId(userId: UserId) extends PrincipalId
```

Lifecycle audit attributes use `PrincipalId`: `createdBy` records the
authenticated actor at creation and retains that identity; `updatedBy`
records the actor responsible for the latest update. Keep `OwnerId` independent.
For example, when an administrator creates a record for a user, `createdBy`
identifies the administrator and the record's owner identifies the user.
For self-owned creation, set `ownerId` from the authenticated user's full
`UserId`. For creation on behalf of another user, set `ownerId` from the
intended owner's full `UserId`.

Reading a legacy record with a missing `ownerId` must never grant ownership
from `createdBy` alone. Migration may fill the missing owner only when the
creation contract in effect for that record guaranteed self-ownership and
the account can be uniquely recovered in scope. Otherwise keep ownership
unresolved and deny access through owner rights. Evaluate administrative
access independently under the privilege policy. Preserve the audit history.

Define system-actor representation and migration of missing historical
attribution explicitly; reading audit data must not grant ownership.

`UserPrincipalId` carries the existing typed `UserId` into the security model.
Case-class equality compares the wrapped `UserId` values across sessions. Subject-specific
subclasses can carry their own typed identities when their contracts are defined.
This sketch describes the intended model; the current runtime wraps a string.

Use the same pattern for ownership and group references. Define separate
abstract classes `OwnerId` and `GroupId`; each concrete case class carries
the typed identity of the entity referenced in that role. For example, a user
acting as principal and the same user recorded as owner can both wrap the same
`UserId`. Owner authorization resolves these two roles to the same typed user
identity for comparison. Separate case classes have separate equality domains.

**Open design question: Group.** The role-type pattern above gives the
intended shape of a future `GroupId`. CNCF still needs to define what a group
represents, who issues its identity, and how membership participates in
authorization. Those decisions determine the concrete variants and comparison
rules. Today the default entity creation policy copies the owner value into
`groupId`; this is a storage default, not evidence of group membership.

Interim default: when the application supplies no group, use a distinguished
`GroupId.Unassigned` value meaning "no group assigned". All subjects fail the
group-membership match for this value; owner and other permission checks
continue under their own rules. The implementation must distinguish this case
from real group IDs and subject group claims. Decide its storage encoding and
compatibility with existing owner-valued defaults before changing persistence.
An explicitly configured application group remains subject to its own policy.

Candidate group references include a company acting as a group
(`CompanyGroupId(companyId: CompanyId)`) and an organization acting as a
group (`OrganizationGroupId(organizationId: OrganizationId)`). A chat room
could also serve as a group (`ChatRoomGroupId(chatRoomId: ChatRoomId)`) when
room membership is the basis for access. Each candidate case of `GroupId`
retains the referenced entity's ID.
The membership policy must separately establish whether a given subject
belongs to the referenced company, organization or room.

Use a numeric privilege level for operational authorization. Map representative
levels to names such as `root`, `admin`, `application admin` and `user`.
The privilege reference identifies a level, and each level has a baseline set
of capabilities. Ownership and level assignment are independent.

For a given assignment and scope, start with the level's baseline, add the
explicitly granted capabilities and remove the explicitly excluded ones:
`effective = (baseline(level) ∪ additions) ∖ exclusions`. Exclusion wins
when both lists contain the same capability. An adjustment changes the
assignment's effective permissions while preserving the numeric level and
the baseline shared by other assignments.

The Entity definition determines the required privilege level at creation.
Ordinary Entity types default to `user`; management-only types must declare
their required higher level. Creation on another user's behalf uses the same
Entity policy regardless of the creator's level or the owner's identity.

For a legacy record, apply the Entity definition's level when `privilegeId`
is absent or the old writer is known to have copied it from the owner.
Equality with the owner value alone does not establish that provenance.
Preserve valid explicit assignments. If the stored value's origin is unclear,
refer it for individual review without lowering access requirements on read.

Owner, group, other and operation checks still apply. An entity type may additionally
declare that application access is prohibited (for example, management-only
records). Enforce this on the trusted server before positive authorization:
an application route cannot return such records even for their owner, an
application administrator, or an assignment with extra capabilities. Apply
the restriction to both direct reads and indirect disclosure through lists,
search or projections. A separately admitted management route checks the
management privilege for the requested operation. Determine the access route
from trusted runtime context, including when an application acts for a human;
a level number or client-supplied flag alone cannot express this boundary.

A subject exercising the `root` privilege through the trusted management
route can read and update every entity, regardless of owner, group or entity
privilege level. Include these rights in the root contract for both the
initial fixed policy and the capability evaluator. To operate on an entity
hidden from applications, a root operator uses the management route. Check
both outcomes: root can read and update that entity in management, and an
application request on behalf of the same user cannot expose it.

Until capability evaluation is available, implement the level-to-operation
policy as a fixed mapping in the authorization layer. When capability support
is ready, evaluate the baseline capabilities assigned to the same level.
Use the same allow/deny examples to verify both implementations, including
the target application scope for an application administrator. Define the
numeric assignments, ordering convention, defaults and legacy storage mapping
before implementation. A change of owner preserves the assigned level.

The current typed identity design focuses on user subjects. CNCF can carry a
service or subsystem subject kind, and Studio has a provider-specific service
principal. General support for other typed subject IDs is future scope: each
such ID needs a defined issuer, identity scope, and resolution through
authentication, ownership and authorization.

The accepted entity-identity hierarchy is:

```text
UniversalId -> abstract EntityId -> concrete XxxId
```

`JobDefinitionId` is issued when a job definition is registered. The definition
stores its business key separately. Jobs submitted from the same definition
reference its `JobDefinitionId` in their definition snapshots, while each job
has its own `JobId` for tracking execution.

Bare `EntityId` remains valid at
generic `EntityStore`, common persistence, decode, parse, restoration, and
declared special-bridge boundaries. It is not an ordinary durable issuance
surface: normal issuance uses a concrete typed issue API or
`IdGenerationContext`, which never derives an ID from a business key.

A business key never generates a durable entity ID. The saved entity is the
sole key-to-ID correspondence authority; no hash, companion integrity field,
or dedicated key-to-ID correspondence table is needed.

---

## EntityId Physical Key and Collection Identity

`EntityId.value` is the canonical physical datastore entry key. Complete
in-memory Entity identity is that physical value together with the owning
`EntityCollectionId`.

A scalar ID cannot prove the complete owning collection because its serialized
form does not retain the independent collection namespace. Runtime storage
operations therefore restore collection ownership from the exact selected
`EntityCollection` context and validate it before resident projection.
Logical-name equality is not the final ownership contract.

Parsed/materialized and newly constructed IDs compare equal after the same
exact owner is restored. The same physical key in two different collections
remains two different Entity identities. Identity-sensitive map use must occur
after collection canonicalization.

Registration, lookup, persistence-adapter, ambiguity, compatibility, and
migration boundaries are defined by
[Entity Collection Identity](entity-collection-identity.md) and its
[normative specification](../spec/entity-collection-identity.md).

### Phase 52 planned replacement

[Phase 52](../phase/phase-52.md) replaces the preceding physical-key-plus-
context model with complete canonical serialization.

The target contract is:

```text
EntityId.parse(EntityId.serialize(id)) == id
```

`EntityId.value` will contain the complete exact `EntityCollectionId` and the
Entity-local identity fields. The same complete String will be used for the
stored Entity `id` and datastore entry key. Parsing will require no
`EntitySpace`, selected collection, datastore, application metadata, or decode
context.

Phase 52 is a clean break. The old incomplete scalar format, synthetic
collection construction, context rebinding, compatibility parsing, migration,
and mixed old/new operation are not retained. CARs are corrected individually
when an incompatibility is observed; CNCF does not preserve the old format for
them.

Until Phase 52 closes, the preceding Phase 51 section records current
implemented behavior.

---

## Anti-Patterns: Semantic ID / Smart ID

### Semantic ID (informal term)

A **Semantic ID** is an identifier whose internal structure is:

- interpreted as structured information, and
- used by application logic for branching or decision-making.

In other words:

> The contents of the ID are treated as data by the program.

This is not a formally standardized term.
It is used informally among architects as shorthand for a recurring design failure.

---

### Smart ID (colloquial, intentionally negative)

**Smart ID** is a more explicit and intentionally negative term
used to describe the same anti-pattern.

A Smart ID is an ID that is:
- “clever”
- information-rich
- and therefore tempting to parse in code

Typical examples:

```scala
if (id.startsWith("sie-query-exec")) { ... }
