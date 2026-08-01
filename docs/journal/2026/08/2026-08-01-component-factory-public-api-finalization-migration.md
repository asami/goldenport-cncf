# Component.Factory Public API Finalization Migration

Date: 2026-08-01

## Scope

Phase 53 CS-05D renames and finalizes these public `Component.Factory`
methods:

| Previous | Replacement |
| --- | --- |
| `aggregate_collection_bindings` | `aggregateCollectionBindings` |
| `aggregate_behavior_bindings` | `aggregateBehaviorBindings` |
| `create_aggregate_from_record` | `createAggregateFromRecord` |
| `create_aggregate_behavior` | `createAggregateBehavior` |
| `authorize_operation_access` | `authorizeOperationAccess` |
| `authorize_operation_entity` | `authorizeOperationEntity` |
| `authorize_unit_of_work` | `authorizeUnitOfWork` |
| `entity_usage_kind` | `entityUsageKind` |
| `entity_operation_kind` | `entityOperationKind` |
| `entity_application_domain` | `entityApplicationDomain` |
| `service_operation_model` | `serviceOperationModel` |
| `entity_access_mode` | `entityAccessMode` |
| `entity_access_relations` | `entityAccessRelations` |

## External CAR impact

This is a deliberate source and binary compatibility break at the public CNCF
Component boundary. An external CAR that calls one of the former methods must
rename the call and rebuild/repackage against this framework version. An
external CAR that overrides one of them must remove that unsupported override;
there is no compatibility alias or forwarding method.

`serviceFactory`, `initializationParameterDeclarations`, and the protected
construction/initialization hooks remain the supported extension points. If a
real Component-development need later requires one of the finalized internal
DSL operations to be extensible, it must introduce a separately reviewed,
narrow visibility and input contract rather than restoring a general override
surface.

No external CAR repository was changed or validated by CS-05D. Release notes
and downstream rebuild coordination remain part of the Phase 53 release
workflow.

## CS-05G Component.Config removal

CS-05G removes the public nested `Component.Config` type and its
`from(ResolvedConfiguration)` parser. This is also a deliberate source and
binary compatibility break: an external CAR that constructs `Component.Config`
or calls its parser must remove that obsolete mode/configuration path and
rebuild against the framework. No compatibility alias is retained.

The same Phase 53 Component-boundary finalization changes the case-class
primary field and generated constructor/accessor surface of `ComponentCreate`
and `ComponentInit`: the former public `subsystem: Subsystem` field is now a
`private[cncf]` assembly carrier. Companion `apply` overloads retain common
source construction, but an external CAR that reads `.subsystem`, uses
`copy(subsystem = ...)`, pattern-matches the former case-class shape, or links
against the former primary constructor/accessor must migrate to supported
construction and rebuild. These callers have a source and/or binary
compatibility break even if they never used `Component.Config`.

The removal does not change CLI `RunMode` routing or the internal
`ExecutionContext.operationMode` carrier. No external CAR repository was
changed or validated in this slice; downstream coordination remains a Phase 53
release task.
