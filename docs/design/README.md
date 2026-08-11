# Design Directory

Purpose: host immutable design decisions that explain boundaries, responsibilities, and intent.
Belongs: approved architecture notes, stable diagrams, and explanations that must not contradict rules or specs.
Not allowed: exploratory chatter, unvetted requirements, or implementation how-to material.
Refer to docs/rules/document-lifecycle.md for the expected flow from notes through design to spec.

Current security/authorization design:

- `authorization-concepts.md`
- `operation-authorization-model.md`
- `entity-authorization-model.md`

Current Web/admin design:

- `web-layer.md`
- `management-console.md`
- `web-form-api-schema.md`
- `web-operation-dispatcher.md`

Current Entity/Blob usage design:

- `entity-collection-identity.md`
- `entity-image-binding-usage-contract.md`

Current component composition design:

- `generation-compatibility-contract.md`
- `typed-component-api-and-multi-instance-spi.md`

Current component persistence design:

- `component-local-datastore-layout.md` (non-normative rationale; see
  `docs/spec/component-local-datastore-layout.md` for the contract)

Current operation contract design:

- `predefined-result-catalog.md`
- `operation-evaluation-capture.md`

Current execution-platform design:

- `process-execution-runtime.md`
