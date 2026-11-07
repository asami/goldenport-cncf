# ID types and the ownership boundary

Discussion note, 2026-11-06. Start from the existing [ID design](../design/id.md)
and [Phase 52 consumer inventory](phase-52-eid05-identity-consumers-and-built-ins.md).
The scope here is the NICT participant path and the shared types it exercises.
Follow-up is planned in [Phase 120](../phase/phase-120.md), after the November
10 demo. See the [demo work inventory](../journal/2026/11/2026-11-06-id-phase-demo-boundary.md)
for interim handling and the observed Studio qualification boundary.

Use the [owner-level typed identity contract](../design/id.md#owner-level-typed-identity-contract)
to classify each use: a dedicated UniversalId-based class by default, or an ID
with a specific purpose and scope. Apply this rule to the principal/owner review:
identify the durable subject ID first, then document how any short or external
representation resolves to it. The existing string wrappers are observations;
their intended contracts and any migration remain review items.

OwnerId and GroupId follow the PrincipalId abstract-class / concrete-case-class
pattern as distinct roles. Inventory the actual owner and group variants and the
membership/authorization comparisons before replacing current ObjectId fields.
The CNCF Group concept is open. The existing owner-valued group default records
a value on the entity; Group identity, membership and its authority still need
a domain decision. Keep that decision separate from the user-owner fix.

| Type / value | Observed use | Contract to preserve or decide |
| --- | --- | --- |
| Core `UniversalId` | Structured ID with a canonical string. The default opaque part (`entropy` in code) uses CompactUuid; callers may supply values within the format. | Validate the entire ID at its boundary. Label grammar and opaque-value grammar are separate. Compare in the declared identity scope. |
| Core `CanonicalId` | UniversalId subtype used by CNCF for correlation. | Preserve its core-owned lifecycle and opaque use by consumers. |
| Model `EntityCollectionId` / `EntityId` | UniversalId subtypes for collections / entities. UserAccount has a complete EntityId; namespace, collection and timestamp are absent from its short form. | Document where a short value is unique before using it alone as an identity key. |
| Model `shortid` | External, human-facing short form for display and entry when the full UniversalId is too long for a UI. The current entity implementation derives it from `id.parts.entropy` and wraps it in `Identifier`. | The short value does not identify the object kind. Use it only where the interaction supplies an unambiguous target context. |
| Core `Identifier` | Restricted name/code string. Model also applies it directly to entity shortid and audit actors. | Retain its existing name/code role and grammar. Review internal-ID conversions at their callers. External IDs follow their issuer's grammar. |
| Core `org.goldenport.datatype.ObjectId` | Wraps Identifier; used for media object IDs and security owner/group/privilege references. | Obsolete. Use purpose-specific types in new code and migrate existing fields by role, preserving public API and stored-data compatibility. The similarly named event ObjectId is outside this decision. |
| CNCF `PrincipalId` / `PublicPrincipalId` | PrincipalId wraps a string. For local users, User Account derives the public principal from account entropy and carries the complete ID separately. Other subject kinds exist. | PrincipalId identifies the security subject; for a user the underlying identity is UserId. Review the mapping from the account ID to UserId, how the public representation resolves to it, and the contracts for other subject kinds. |
| Session ID / login name | Session locates an authentication session; login name or email locates an account during login. | Keep credential/session rotation and name changes independent of durable ownership. |

## Using shortid in a UI

For example, a book lookup screen can display and accept a shortid because the
screen already specifies books as the target collection. Resolve the input
within that collection and applicable namespace to the object's full EntityId.
If several objects match, require more context or the full ID. For a search
across object kinds, carry the kind/context explicitly or use the full ID.

This UI role gives no uniqueness guarantee for shortid across collections.
Using account entropy as a principal requires its own identity and scope
contract. The current `Identifier(id.parts.entropy)` conversion is an
implementation point to review against the intended display/input repertoire.

## Concrete inconsistency

In the Studio Phase 5 producer worktrees, self-registration calls
`idGeneration.entityId(UserAccountCreate.collectionId)` independently of the
login name. That generator explicitly supplies 16 entropy bytes rendered as
hex. Check its agreement with the intended compressed-UUID default separately
from the ownership fix. User Account places account entropy in PrincipalId.
Model SecurityAttributes and CNCF create/restore paths convert an owner to
Identifier using name rules. Model role resolution additionally normalizes
values before comparison; CNCF owner authorization directly compares with the
subject. The generator also emits an owner setter constructing Identifier
directly. Studio's Book Material receipt scope separately uses the full account ID.

## Decision and verification order

1. Specify creation authority, identity scope, canonical value, external/wire
   form and exact comparison at the account → principal → owner boundaries.
   Check anonymous/service identities, explicit owner overrides and fallback
   to the shared string `user` when public-principal construction fails.
2. Trace that decision through SecurityAttributes, generated helpers, CNCF
   write/read/authorize, User Account sessions and Studio. Specify behavior
   for existing rows and wire/API consumers; require attributable evidence to
   resolve any ambiguous legacy owner.
3. Exercise two distinct non-manager accounts: numeric-leading opaque ID,
   permitted special opaque values, save/reload and owner versus other-user
   access. Include rename and session renewal. Distinguish identities whose
   old name normalization coincided. Verify scope if different issuers or
   namespaces may supply the same short value.
4. Inventory remaining generated models, media codecs, query/update APIs,
   shortid and createdBy/updatedBy before proposing a generic type change.
   Record public API, storage and migration impact in the owning project.

This note records observations and open decisions. Product verification and
changes to shared type definitions require their own implementation work.

## Additional IDs: readers, decisions and review priority

WorkAreaId already extends UniversalId. Check that two FileWorkArea allocations
named system can be distinguished by ID and that allocation and cleanup for
each area refer to its original ID.

ConcurrencyScopeId currently wraps the normalized scope name. Adopt a dedicated
UniversalId and resolve ConcurrencyScopeName in the admission registry. Verify
that work sharing a scope uses one limit, and that releasing a lease restores
capacity in that same scope independently of other scopes.

ProcessCapabilityId currently wraps a normalized name used for program and
grant lookup. Adopt a dedicated UniversalId and resolve ProcessCapabilityName
in configuration. Verify that repeated requests use the same capability ID,
and a configured program without a matching grant still cannot execute.

DecisionTableId and DecisionTableRowId currently wrap strings. Target dedicated
UniversalId types for table and row definitions. Verify that repeated evaluation
refers to the same table and that row identity survives reordering. Reproducing
the conditions and output in effect before a row edit remains a versioning
question.

The rule model currently wraps strings in FactId and FactName. Adopt a
dedicated UniversalId-based FactId per rule fact. Verify that two order
amounts with the same FactName remain distinguishable and a derived discount
fact has its own ID and references the actual input FactIds in its provenance.

RuleActivationId currently wraps a string. Target a dedicated UniversalId
for one rule activation. Verify that two distinct matches of the same rule
have separate activation IDs and that reloading one match preserves its ID
and references to the rule and matched facts.

RuleActionPlanId completes the Rule-prefixed ID inventory. It currently wraps
a string and identifies an operation, event, job or recommendation plan.
Use a dedicated UniversalId-based type. Verify that reloading the same plan
preserves its ID and that a job's ActionId identifies the resulting action.

RuleSetId and RuleId currently wrap strings read from descriptor id fields.
Target dedicated UniversalId types with RuleSetName/version and scoped RuleName
selection. Verify that a revised set retains its identity and that a rule name
resolves within the requested set version. Review existing descriptor references
before migrating the string-based lookup.

Operation evaluation already uses four UniversalId types. Verify one failed
attempt followed by success shares one invocation ID, with two attempt IDs and
distinct start/terminal fact IDs. A nested invocation refers to its parent.
Check that redelivering a fact preserves its fact ID and processing the same
supplemental staging request retains its intent ID. Job/task correlation is a
separate reference when evaluation runs through the job engine.

JobId, TaskId and ActionId already extend UniversalId. Verify the retry
timeline across job control, scheduling and execution: a failed action and its
retry have the same JobId and ActionId and distinct TaskIds. For each attempt,
its queued and running records share the assigned TaskId. Check a new job
gets a fresh JobId and a distinct compensation action gets a fresh ActionId.

WorkflowDefinitionId and WorkflowInstanceId already extend UniversalId.
WorkflowDefinition generates an ID if omitted; check that persisted definition
reload restores its original ID. Verify that two independent starts share
the definition ID and have different instance IDs, and that resuming or
retrying the first execution preserves its instance ID.

SagaId: observability and job records currently carry optional strings.
EventReception._with_saga_identity may fall back to the correlation value for
SameSaga and generates a string for NewSaga. Target one dedicated UniversalId
per saga execution. Inventory event attributes, persisted job records and
replay consumers before migrating existing strings. Verify that a retry and
its submitted job keep the original SagaId and that a new saga has a distinct
ID even when it belongs to the same correlation scope.

Distributed tracing follow-up: ObservabilityContext.createChild retains the
trace but can allocate correlation in a child when the parent has none. Check
fan-out at the intended correlation boundary. EventReception's new-saga
handoff has distinct correlation construction paths for the event and job;
verify they agree. OpenTelemetryExporterSupport.tracePayload currently
derives a trace ID from operation and start time and emits spans with empty
parent references. Verify continuity with the actual runtime IDs and recorded
parentage through ingress, message delivery and collector output.

Acceptance example: a traced request fans out to siblings and publishes a
message consumed in another service. Inspect the exported spans for one
trace, distinct sibling spans, the correct parent chain and the active span
on error logs. Under a shared correlation boundary all branches retain one
correlation value; a new saga's event and job use the same new correlation.

ExecutionContextId already extends UniversalId. Review issuance in
OperationEvaluationContext, CommittedTransition, TransitionLifecycleEvent
and ActionEngine (authorization denial). Distinguish actual context creation
from emission of records within an active context. Verify that records from
one execution agree on its ExecutionContextId across these paths, including
failure paths, while distinct events have distinct EventIds.

ServiceContainerOwnerId currently wraps a logical owner string paired with
an owner kind. The target role type carries the responsible ComponentInstanceId
or SubsystemInstanceId. During migration check existing registry keys and
provider ownership labels: sibling instances must remain isolated, while a
restarted logical instance can reclaim its compatible service.

ServiceContainerId currently stores the logical service name. Migrate to a
dedicated UniversalId-based service identity and owner-scoped selection by
ServiceContainerName. Check same-name services under distinct owners and
registry-key continuity when a provider replaces the backing container.

The gateway currently calls the ID read from Docker's inspect `Id` field
ServiceContainerInstanceId. The target name is ProviderContainerId; preserve
the external value used by the provider's lifecycle operations. Review gateway
callers during the rename. Reserve ServiceContainerInstanceId for a separately
issued CNCF UniversalId only if a concrete internal tracking need arises.

Subsystem inventory: the Scala definitions are SubsystemCapabilityId (today
a versioned catalog key in style requirements) and SubsystemInstanceId
(today a subsystem/instance label pair in configuration). Both target dedicated
UniversalId-based identities. Keep name-based selection compatible, including
the default instance label and persisted style snapshot requirements.

ComponentId and ComponentInstanceId: retain compatibility with the shared
cncf-collaborator-api Java types. Defer UniversalId migration until both API
owners and consumers can validate existing name and instance references,
including equality and lookup, through the same transition.

Component style: adopt a dedicated UniversalId-based ComponentStyleId and
support catalog selection by ComponentStyleName. Today the versioned name
is the map key and is serialized in the descriptor snapshot's id field.
Review existing snapshot reads and name-to-ID resolution together at migration.

Style provider: adopt a dedicated UniversalId-based ComponentStyleProviderId.
Configuration selects a provider by ComponentStyleProviderName. The existing
catalog and snapshot provider fields carry strings; include those reads and
their provider consistency check in the migration review.

Component capability: adopt a dedicated UniversalId-based ComponentCapabilityId
and select by ComponentCapabilityName plus major version. Review versioned
name references in styles and bundles and the expanded capability list in
descriptor snapshots when migrating the existing catalog.

| Area | Observed writer / reader | Implication or open decision |
| --- | --- | --- |
| Privilege | The CNCF create default takes the owner value for privilegeId. Model SecurityAttributes.ownedBy does the same; decoding a missing privilege falls back to owner. OperationAccessPolicy accepts a match against subject privileges, capabilities **or security level** before checking owner/group/other rights. SecuritySubject normalizes names (case, hyphens, underscores). | Use numeric levels with baseline capabilities. The Entity definition determines the required level: ordinary types default to user and management-only types declare a higher level. For missing or demonstrably owner-derived legacy values, apply the Entity definition. Preserve valid explicit assignments and review ambiguous values without widening access. Numeric assignments remain open. |
| Audit actor | CNCF writes the name-converted principal into createdBy/updatedBy. LifecycleAttributes types both as Identifier, converts strings on read, and supplies defaults for missing actor values. SecurityAttributes also reads createdBy as a fallback for missing ownerId. | Record the acting PrincipalId separately from OwnerId. On reload, verify that a self-owned record retains the user's owner ID and a record created by an administrator for a user retains that user as owner and the administrator as creator. |
| Existing UserId / ResourceId | cncf.datatype.DataType.scala declares string wrappers with validate returning true. A source search of this checkout's src/main found no other direct references to these names. | Reconcile the existing UserId name with the intended dedicated user identity before adding or reusing a class. Check generated and downstream consumers before changing public types. |
| Tenant / organization | CNCF creation policies supply optional tenantId/organizationId strings, with no value under the default selectors. | Use dedicated UniversalId types TenantId and OrganizationId. Bind tenant identity to the deployment namespace; (major, minor) is the planned cluster placement key. Reuse the organization identity when referencing it in a group role, and evaluate membership separately. |
| Media | Image, Audio, Video and Attachment have an ObjectId field; model encoding tests supply independent IDs. The core encoder delegates ObjectId to its inner Identifier. | Use ImageId, AudioId, VideoId and AttachmentId as dedicated UniversalId-based domain types. Keep provider file IDs as separate storage references. Verify that relocating a file preserves the media ID; review persisted and wire formats before migration. |
| Session | User Account issues separate access and refresh sessions linked to an account. Its public session ID uses the session entity's entropy; the access-session lookup adapter restores the expected entity ID in its known collection. | Use a dedicated UniversalId-based SessionId issued per session, with a separate UserId reference. Verify two concurrent sessions have distinct IDs for the same user. Review the existing public handle and token contracts before migration. |
| Event type and occurrence | EventId already extends UniversalId. EventTypeId currently wraps a string used in event persistence rules and job event logs. | Adopt a dedicated UniversalId-based EventTypeId for the type definition. Two occurrences of the same type retain one type ID and distinct event IDs. Review how existing configured names and persisted logs resolve to the stable type ID during migration. |
| Knowledge node | KnowledgeNodeId wraps a string. InformationToKnowledgeProjection constructs keys from Information IDs and sometimes falls back to titles. RdfNodeName is separate. | Use a dedicated UniversalId-based KnowledgeNodeId for each independent node. On migration verify that repeated projection of the same Information in the same context and role retains its reference, and that equal titles or RDF targets across Commons do not merge distinct nodes. |
| Knowledge relationship | KnowledgeRelationshipId currently wraps a string; InformationToKnowledgeProjection constructs relationship keys from projection inputs. | Adopt a dedicated UniversalId-based KnowledgeRelationshipId. Issuance, reuse and cross-context identity semantics remain open. |
| Knowledge frame | KnowledgeFrameId currently wraps a string; InformationToKnowledgeProjection uses `frame-<Information ID>`. | Adopt a dedicated UniversalId-based KnowledgeFrameId. Keep issuance and reuse across different purposes or queries for the same Information under review. |
| Knowledge fact | KnowledgeFactId currently wraps a string; the title projection uses `fact-<Information ID>-title`. | Adopt a dedicated UniversalId-based KnowledgeFactId for each recorded fact. Keep the identity and revision rules for corrections under review. |
| Knowledge evidence | KnowledgeEvidenceId wraps a string and KnowledgeEvidence holds a separate source reference. | Adopt a dedicated UniversalId-based ID for the evidence record; review issuance and reuse per cited source or passage. |
| Knowledge provenance | KnowledgeProvenanceId wraps a string; records can refer to it to trace origin or generation. | Adopt a dedicated UniversalId-based ID for the provenance record; determine when a change yields another record. |
| Knowledge fragment (textus-mcp-rag) | The string-backed KnowledgeFragmentId is used in projection, index lookup and deletion; the component maps it to EntityId. | Adopt a dedicated UniversalId-based fragment ID. Review the entity mapping and existing index references when specifying issuance, re-projection and revision. |
| Git object | GitObjectId validates a Git-issued object ID as 40 or 64 lowercase hexadecimal characters. Git operations use it for commit, tree and blob references. | Retain the current type and Git-native storage format as an external-ID contract. |
| Git path selection | GitSelectionId currently wraps a name-like string and identifies the selected paths across Git operations. | Use a dedicated UniversalId throughout staging, checking and committing. Resolve GitSelectionName within scope and enforce path and operation restrictions. |
| MCP server registration | The Codex importer derives McpServerId from the normalized configuration name. Sessions and tool identities use the ID. | Issue a dedicated UniversalId at registration and retain it across reloads and renames. Resolve McpServerName for configuration lookup. |
| MCP server set | The configured id selects a client service and appears in the client and transport contract names. | Issue a dedicated McpServerSetId (UniversalId) per set registration and retain it across membership changes. Select by McpServerSetName and preserve lookup through existing configuration values and contract names during migration. |
| Operation tool set | The configured id selects a service with an allowed operation list and execution limits via the operation-tool/<id> contract. | Issue a dedicated OperationToolSetId (UniversalId) per registration and select by OperationToolSetName. Retain the ID across policy changes, enforce current permissions and limits on each call, and preserve existing configuration and contract lookup. |
| Web display format policy | WebDisplayFormatPolicyId currently wraps a name used to select a date or date-time display policy. | Model the selector as WebDisplayFormatPolicyName and preserve existing configuration values and naming rules. Reserve a UniversalId-based WebDisplayFormatPolicyId for registered policies that need stable references across renames. |
| Job definition | Registration issues a dedicated JobDefinitionId independently of the business key. Each submitted job carries a definition snapshot and has its own JobId. | Retain the existing types. Jobs submitted from the same definition share its JobDefinitionId and are tracked individually by JobId. |
| Other existing families | TraceId, SpanId, CorrelationId, JobId, TaskId, ActionId, WorkflowDefinitionId and WorkflowInstanceId are examples of dedicated UniversalId subclasses. Related knowledge references also carry strings. | Retain established dedicated types. For string-backed types classify each as a scoped name, external identifier, or durable internal identity needing a dedicated class. Inspect issuance, consumers and compatibility before proposing conversions. |

### Batch disposition of remaining CNCF implementation types

Scope: ID declarations in this repository's production sources, including
opaque types. The decisions below are migration targets.

| Types | Disposition |
| --- | --- |
| DataStore.EntryId, DataStoreEntryId, UniversalEntryId, StringEntryId | Keep the storage boundary and its three representations. Preserve a supplied UniversalId as typed data and a literal key verbatim. Issue a DataStoreEntryId when storage must supply durable identity. |
| DataStore.CollectionId | Preserve the distinct instance-name and EntityCollectionId namespaces used in provider lookup. |
| TransactionContextId, ContextIssuedEntityId | Retain the transaction's dedicated UniversalId and the private generic entity issuance adapter respectively. |
| ComponentCapabilityBundleId | Target a dedicated UniversalId; retain family/name/major selection and existing serialized bundle references during migration. |
| WorkspaceId, ValidationCheckId | For a registered workspace use a stable ID and scoped name; preserve root binding and grants when resolving existing names. Treat the check selector as an exact name in its workspace. |
| ModelElementId, TermId, RelationId, ProfileId (ComponentCapabilityReference) | Preserve model or vocabulary scope together with the source local key for references and wire compatibility. |
| PublicPrincipalId, MCP transport _SessionId | Keep public display handles separate from authenticated subjects. Preserve the peer-issued MCP session handle in the protocol. |
| ById, EntityStoreUpdateById | These are operation shapes taking an identity, so their names introduce no identity issuance contract. |

**Migration issues to verify**

- Storage: DataStoreSpace injection and StartupImport stringify a supplied
  UniversalId. Without an ID, one issues a DataStoreEntryId and the other a
  counter-based string. The agreed design allows issuance only on ordinary
  create. Both repeatable seed paths require explicit keys and share the same
  create-or-update behavior. For migration, verify each existing row's key
  and put it in the seed. Acceptance: import A and B, restart, then reimport B
  and A with only A changed. The same two keys must remain and only A must
  change. A missing seed ID must fail before any entry is written, and an I/O
  failure must propagate without triggering an alternate write.
- Missing ownerId: remove the read-time fallback to createdBy from the target
  design. Backfill only if the historical creation contract guaranteed self-
  ownership and the account is uniquely identifiable. Otherwise retain the
  audit fields, leave ownership unresolved and grant no owner rights. Evaluate
  administrative privileges separately. Acceptance: reloading a legacy record
  with only an administrator's createdBy cannot make that administrator its owner.
- Authentication: textus-user-account currently passes the account's shortid
  (or the shared fallback `"user"`) as both the authenticated principal and
  the public handle. AuthComponent also falls back to login name, email or the
  internal principal in the public response. The agreed design authenticates
  with UserPrincipalId over the full UserId and fails for an invalid UserId.
  Expose only an optional user-scoped shortid as the handle. Inventory public
  principalId consumers before migration. Convert legacy owners only with
  unique account attribution; route ambiguous values to individual review.
  Acceptance: the same account retains owner access after a login rename and
  across sessions. Another account with the same public handle cannot gain
  owner access. An invalid UserId cannot authenticate as a shared subject.
- Event delivery: use the issued EventId as identity and resolve a retry by
  exact delivery-key equality. Remove hash-derived index addresses and body
  diagnostics from the target design. Preserve existing key-to-EventId mappings
  and concurrency semantics. Physical indexing and migration remain open.

For privilege assignments, verify baseline-only access, an added capability,
an excluded baseline capability, overlap (exclusion wins), and isolation across
application scopes. Use the same cases for the fixed policy and later capability
evaluator.

**ResourceId disposition: obsolete (2026-11-06).** Retain the existing
declaration for compatibility and exclude it from new ID design and migration
work for now. Reconsider it when a concrete consumer establishes what resource
it identifies, who issues the ID and where it is resolved.

ResourceId provenance: CNCF's initial source commit `d4c4ad57` already
declares the string wrapper. No direct use of the current CNCF type was found
in the inspected workspace sources or the selected generated Scala outputs.
The older OFAD reservation example uses a similarly named component-framework
type for the reservable resource: its reservation record and query carry
resourceId, userId and interval, and RoomId can be constructed from ResourceId.
The SimpleModeling reservation tutorial declares its own ResourceId. These
examples establish a reservation-domain use of the name, not a current CNCF
contract for all entity, media or authorization targets.

Source starting points in CNCF: entity/EntityCreateDefaultsPolicy.scala,
security/OperationAccessPolicy.scala, security/SecuritySubject.scala and
datatype/DataType.scala under src/main/scala/org/goldenport/cncf. In
simplemodeling-model inspect value/SecurityAttributes.scala and
value/LifecycleAttributes.scala; in the Studio producer worktree inspect
User Account ComponentFactory's sessionIdValue and _canonical_access_session_entity_id.
These findings are source-level inventory; persistence and access outcomes
need verification against a selected implementation.
