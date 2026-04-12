# Entity Authorization Remaining Checklist

Date: 2026-04-13

## Context

The first baseline for entity authorization has been implemented and documented.

Baseline documents:

- `docs/design/entity-authorization-model.md`
- `docs/notes/entity-authorization-implementation-note.md`
- `docs/journal/2026/04/entity-authorization-implementation-2026-04-13.md`

This checklist records remaining work after that baseline.

## CML Surface

- [ ] Define CML syntax for entity `operationKind`.
- [ ] Define CML syntax for entity `applicationDomain`.
- [ ] Keep `usageKind` available as a separate classification axis.
- [ ] Define CML syntax for service-level `operationModel`.
- [ ] Define CML syntax for operation-level `operationModel` override.
- [ ] Define CML syntax for relation-based authorization.
- [x] Define first CML syntax for operation-level ABAC natural condition as
      `ACCESS` / `CONDITION`.
- [ ] Define CML syntax for explicit low-level `accessMode` override.
- [ ] Decide whether relation rules belong under entity, service, operation, or a
      separate policy block.
- [x] Update cozy parser/model for the selected operation `ACCESS` syntax.
- [x] Update cozy code generation so generated `CmlOperationAccess` carries the
      new fields.

## Descriptor and Generation

- [ ] Ensure generated CAR/component descriptors emit `operationKind`.
- [ ] Ensure generated CAR/component descriptors emit `applicationDomain`.
- [ ] Decide whether generated descriptors should also emit `usageKind`.
- [ ] Wire descriptor entity classification into runtime create default profile
      derivation.
- [ ] Add descriptor tests for camelCase and snake_case field names.
- [ ] Add generated sample descriptor examples for business and CMS entities.

## Create Defaults

- [ ] Derive create default profiles from `applicationDomain` by default.
- [ ] Derive executable permissions from `operationKind = task` only when the
      entity is genuinely executable.
- [ ] Add an application-level policy for owner id selection.
- [ ] Add an entity-level policy for owner id selection.
- [ ] Add group id default policy.
- [ ] Add tenant/organization default policy.
- [ ] Add tests for business default owner/group/other permissions.
- [ ] Add tests for CMS/public-content default read visibility.

## Relation-Based Authorization

- [ ] Support multiple relation rules per entity/operation.
- [ ] Support relation rules with explicit access kinds.
- [ ] Support relation rules based on principal id.
- [ ] Support relation rules based on subject attributes.
- [ ] Support tenant/organization relation rules.
- [ ] Support customer/account relation rules.
- [ ] Support assignee/participant relation rules.
- [ ] Decide denial precedence when ACL and relation rules disagree.
- [ ] Add result filtering tests for relation-based search/list.
- [ ] Add tests for relation-based update denial unless explicitly allowed.

## ABAC Natural Evaluation

- [ ] Document the authorization model as an ABAC-centered model that connects
      RBAC-style role evaluation, ReBAC-style relation evaluation, and
      DAC-style owner/group/other permission evaluation.
- [ ] Define the ABAC natural evaluation scope that is not reducible to role,
      relation, or owner/group/other permission checks.
- [x] Add a minimal explicit ABAC natural condition carrier to
      `UnitOfWorkAuthorization`.
- [ ] Add a full authorization context structure that clearly exposes subject,
      entity, operation, application, and environment attributes.
- [ ] Add natural ABAC checks for CMS publication attributes such as
      `postStatus`, `visibility`, `publishAt`, and `unpublishAt`.
- [ ] Add natural ABAC checks for business boundaries such as tenant,
      organization, account, or customer scope where they are direct attributes.
- [ ] Add operation/application attribute checks for `operationModel`,
      `entityOperationKind`, and `entityApplicationDomain`.
- [ ] Decide allow/deny/not-applicable/indeterminate result semantics for ABAC
      natural evaluation.
- [ ] Decide how ABAC natural evaluation composes with RBAC-style, ReBAC-style,
      and DAC-style checks.
- [ ] Add diagnostics explaining which ABAC natural condition matched or missed.
- [x] Add a first tenant boundary test for explicit ABAC natural conditions.
- [x] Add parser tests for explicit ABAC natural conditions.
- [ ] Add tests for publication visibility, tenant boundary, and operation
      exposure policies.

## Service/Internal Authorization

- [ ] Define formal semantics for `ServiceInternal`.
- [ ] Define formal semantics for `System`.
- [ ] Add audit records for `ServiceInternal` permission bypass.
- [ ] Add audit records for `System` permission bypass.
- [ ] Add cross-component service grant model.
- [ ] Require explicit grant/capability when `ServiceInternal` touches another
      component's entity.
- [ ] Add tests for same-component internal access.
- [ ] Add tests for cross-component internal access denial.
- [ ] Add tests for cross-component internal access with explicit grant.

## Subject Model

- [ ] Promote `SecuritySubject` from provisional helper to documented internal
      subject model.
- [ ] Decide whether primary group should live directly in `SecurityContext`.
- [ ] Decide whether roles should live directly in `SecurityContext`.
- [ ] Decide whether privileges should live directly in `SecurityContext`.
- [ ] Normalize tenant/account/customer identifiers.
- [ ] Add tests for subject attribute normalization.
- [ ] Add tests for multi-valued subject attributes.

## Object Model

- [ ] Keep CNCF dependent behavior outside simplemodeling-model.
- [ ] Keep `SecurityAttributes` as the canonical object-side value type.
- [ ] Avoid overloading `execute` with special behavior.
- [ ] Define explicit fields/policies if future setuid-like behavior is needed.
- [ ] Decide whether compact `permission` text should remain a compatibility
      parser only.
- [ ] Add tests for `execute=false` default on ordinary resource entities.

## Observability and Audit

- [ ] Emit authorization decision events.
- [ ] Include access mode in authorization observability records.
- [ ] Include relation rule match/miss information in debug-level diagnostics.
- [ ] Include target entity collection/name in audit records.
- [ ] Include subject id and normalized role/group/capability summary.
- [ ] Ensure denied authorization does not emit action execution enter/leave
      events.
- [ ] Add dashboard/admin visibility for authorization counters.

## Documentation

- [ ] Update `docs/design/execution-model.md` with a pointer to the implemented
      entity authorization model.
- [ ] Update `docs/design/free-unitofwork-execution-model.md` with the current
      UoW authorization carrier.
- [ ] Update `docs/notes/entity-runtime-architecture.md` with authorization
      boundary references.
- [ ] Add CML examples once syntax is finalized.
- [ ] Add SalesOrder business example once generator support exists.
- [x] Add first CMS content example using operation `ACCESS` / `CONDITION` in
      the notice-board sample app.

## Verification

- [ ] Add full test suite target for entity authorization.
- [ ] Add sample app scenario exercising business/private entity defaults.
- [ ] Add sample app scenario exercising CMS/public-content defaults.
- [ ] Add sample app scenario exercising relation-based customer read.
- [ ] Add regression test that `--` or low-level CLI paths cannot bypass UoW
      authorization for entity access.

## Current Baseline Verification

The baseline implementation was verified with:

- `sbt 'testOnly org.goldenport.cncf.entity.EntityCreateDefaultsPolicySpec org.goldenport.cncf.unitofwork.UnitOfWorkTargetAuthorizationSpec org.goldenport.cncf.action.ActionCallEntityAccessMetricsSpec'`
- `sbt publishLocal`
- sample app `sbt compile`
