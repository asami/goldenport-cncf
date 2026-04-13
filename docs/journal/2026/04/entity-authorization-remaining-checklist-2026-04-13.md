# Entity Authorization Remaining Checklist

Date: 2026-04-13

## Context

The first baseline for entity authorization has been implemented and documented.

Baseline documents:

- `docs/design/entity-authorization-model.md`
- `docs/notes/entity-authorization-implementation-note.md`
- `docs/journal/2026/04/entity-authorization-implementation-2026-04-13.md`

This checklist records remaining work after that baseline.

Status update on 2026-04-13:

- SimpleEntity datastore output is now flat for `SimpleEntity` and
  `SimpleEntityCreate`.
- Entity create defaults now write flat security fields instead of a nested
  `security_attributes` implementation structure.
- `SecurityAttributes` can read flat `rights.owner/group/other` records.
- The notice-board sample verifies the first CMS/public-content scenario with
  operation-level `ACCESS` / `CONDITION`.

## CML Surface

- [ ] Define CML syntax for entity `operationKind`.
- [ ] Define CML syntax for entity `applicationDomain`.
- [ ] Keep `usageKind` available as a separate classification axis.
- [ ] Define CML syntax for service-level `operationModel`.
- [ ] Define CML syntax for operation-level `operationModel` override.
- [ ] Define CML syntax for relation-based authorization.
- [x] Define first stable CML syntax for operation-level ABAC natural condition
      as `ACCESS` / `CONDITION`, with `;` as the multi-condition delimiter.
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

- [x] Derive create default profiles from `applicationDomain` by default.
- [ ] Derive executable permissions from `operationKind = task` only when the
      entity is genuinely executable.
- [ ] Add an application-level policy for owner id selection.
- [ ] Add an entity-level policy for owner id selection.
- [ ] Add group id default policy.
- [ ] Add tenant/organization default policy.
- [ ] Add tests for business default owner/group/other permissions.
- [x] Add sample-app verification for CMS/public-content default read
      visibility.
- [ ] Add tests for CMS/public-content default read visibility.

## Relation-Based Authorization

- [x] Support multiple relation rules per entity/operation.
- [x] Support relation rules with explicit access kinds.
- [x] Support relation rules based on principal id.
- [x] Support relation rules based on subject attributes.
- [x] Support tenant/organization relation rules.
- [x] Support customer/account relation rules.
- [x] Support assignee/participant relation rules.
- [ ] Decide denial precedence when ACL and relation rules disagree.
- [x] Add result filtering tests for relation-based search/list.
- [ ] Add tests for relation-based update denial unless explicitly allowed.

## ABAC Natural Evaluation

- [x] Document the authorization model as an ABAC-centered model that connects
      RBAC-style role evaluation, ReBAC-style relation evaluation, and
      DAC-style owner/group/other permission evaluation.
- [x] Define the ABAC natural evaluation scope that is not reducible to role,
      relation, or owner/group/other permission checks.
- [x] Add a minimal explicit ABAC natural condition carrier to
      `UnitOfWorkAuthorization`.
- [ ] Add a full authorization context structure that clearly exposes subject,
      entity, operation, application, and environment attributes.
- [x] Add the first natural ABAC check for CMS publication status
      (`postStatus`) through explicit operation `ACCESS` / `CONDITION`.
- [x] Add the first natural ABAC time-window checks for CMS publication windows
      (`publishAt<=now`, `closeAt>now`) through explicit operation `ACCESS` /
      `CONDITION`.
- [ ] Add full natural ABAC checks for CMS publication attributes such as
      `visibility`, `publicAt`, `startAt`, `endAt`, and `unpublishAt`.
- [ ] Add natural ABAC checks for business boundaries such as tenant,
      organization, account, or customer scope where they are direct attributes.
- [ ] Add operation/application attribute checks for `operationModel`,
      `entityOperationKind`, and `entityApplicationDomain`.
- [ ] Decide allow/deny/not-applicable/indeterminate result semantics for ABAC
      natural evaluation.
- [ ] Decide how ABAC natural evaluation composes with RBAC-style, ReBAC-style,
      and DAC-style checks.
- [x] Add minimal diagnostics explaining which ABAC natural condition missed on
      direct authorization denial.
- [x] Add minimal search/list diagnostics that summarize ABAC natural-condition
      filtering and report the first missed condition.
- [ ] Add full diagnostics explaining all ABAC natural conditions that matched
      or missed for each candidate entity.
- [x] Add a first tenant boundary test for explicit ABAC natural conditions.
- [x] Add parser tests for explicit ABAC natural conditions.
- [x] Add tests for publication status and publication time-window policies.
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

- [x] Keep CNCF dependent behavior outside simplemodeling-model.
- [x] Keep `SecurityAttributes` as the canonical object-side value type.
- [x] Avoid overloading `execute` with special behavior.
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
- [x] Add sample app scenario exercising CMS/public-content defaults.
- [ ] Add sample app scenario exercising relation-based customer read.
- [ ] Add regression test that `--` or low-level CLI paths cannot bypass UoW
      authorization for entity access.

## Current Baseline Verification

The baseline implementation was verified with:

- `sbt 'testOnly org.goldenport.cncf.entity.EntityCreateDefaultsPolicySpec org.goldenport.cncf.unitofwork.UnitOfWorkTargetAuthorizationSpec org.goldenport.cncf.action.ActionCallEntityAccessMetricsSpec'`
- `sbt publishLocal`
- sample app `sbt compile`

The 2026-04-13 flat datastore/security update was verified with:

- `simple-modeler`: `sbt compile publishLocal`
- `simplemodeling-model`: `sbt compile publishLocal`
- `cloud-native-component-framework`: `sbt compile publishLocal`
- sample app `sbt compile`
- sample app `./scripts/update-runtime-classpath.sh`
- sample app `./scripts/run-demo.sh`
- manual sample app flow: `postNotice` -> `await_job_result` -> `getNotice` ->
  `searchNotices`, including recipient-specific, text search, and recipientless
  broadcast notice cases.
