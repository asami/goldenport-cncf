# Phase 43 - Bounded Resource Tree Query

Stage Status:
- Current status: ACTIVE
- Current step: RQ-06 verification and closure
- Owner: CNCF resource-tree runtime, with CBD Support as the first downstream
  driver.

status = active

## 1. Purpose

Allow a component to discover a bounded set of named files below an admitted
logical resource tree without materializing the complete tree or gaining
access to its physical root.

## 2. Selected Contract

- Keep `ResourceTreeAccess.snapshot` strict and unchanged for complete,
  immutable snapshots.
- Add a separate generic `ResourceTreeQuery` operation.
- The first selector is exact leaf name. CNCF does not assign semantics to
  `project.yaml`; CBD Support selects that name through the generic query.
- A query result contains only deterministic logical relative paths and
  bounded bytes.
- Query limits cover maximum depth, directory visits, matched entries,
  per-entry bytes, and aggregate result bytes.
- A symbolic configured root is invalid. Nested symbolic links are never
  followed and are skipped when they are unrelated to a result candidate.

## 3. Runtime Boundary

- Query access is installed through `ExecutionContext.resourceTrees` and the
  protected ActionCall/Behavior internal DSL.
- Local traversal is runtime-owned. Component code receives no `Path`, root,
  provider handle, or unrestricted directory enumeration primitive.
- In-memory query support supplies deterministic executable evidence.
- CallTree and metrics expose only logical tree identity, selector kind,
  provider family, limits, visit/match counts, outcome, and structured
  diagnostics. They do not expose physical roots or file content.

## 4. Work Stack

- RQ-01: Promote the handoff into normative resource-tree design/spec
  amendments and freeze the query vocabulary.
- RQ-02: Add query selector, limits, result, provider API, and conservative
  limit-composition models.
- RQ-03: Implement deterministic in-memory and local query providers without
  changing snapshot behavior.
- RQ-04: Add ExecutionContext, protected DSL, CallTree, and runtime metric
  integration.
- RQ-05: Migrate CBD Support's `working` source to query every bounded
  `project.yaml` result and initialize status/catalog reads consistently.
- RQ-06: Verify CNCF, CBD Support, and a live admitted development-workspace
  acceptance path, then close the phase.

## 5. Acceptance

- A tree containing unrelated symbolic links can return bounded exact-name
  matches without following those links.
- A symbolic configured root, traversal limit exhaustion, excessive match,
  oversized descriptor, and aggregate-byte overflow fail deterministically.
- Returned entries are sorted by logical relative path and contain no physical
  root information.
- Existing snapshot symlink and completeness semantics remain unchanged.
- CBD Support reports its logical `working` source ready and emits one
  observation per discovered project descriptor.

## 6. Non-goals

- General filesystem search, glob/regex selectors, content indexing, watch,
  mutation, or host-path access.
- Relaxing Process Execution tree-input admission.
- Treating a development workspace as one complete immutable snapshot.

## 7. Source

- `docs/journal/2026/07/2026-07-20-cbd-working-resource-tree-handoff.md`
- `docs/design/component-runtime-boundary-capabilities.md`
- `docs/spec/component-runtime-boundary-capabilities.md`

## 8. Resume Point

RQ-01 completed Jul. 20, 2026. The resource-tree design and static
specification now define the separate bounded query contract, initial exact
leaf-name selector, limit dimensions, symlink handling, and safe diagnostics.
RQ-02 completed Jul. 20, 2026. CNCF now has validated query selector and
limit models, an immutable result model, conservative limit tightening, and
deterministic in-memory exact-name query evidence. Start RQ-03. Local provider
traversal must preserve strict snapshot behavior before DSL and CBD adoption.
RQ-03 completed Jul. 20, 2026. In-memory and local providers now apply finite
provider caps, return deterministic exact-name results, preserve strict
snapshot symlink rejection, and fail rather than silently truncating discovery
when local traversal limits prevent complete evaluation. Start RQ-04. Query
access needs the protected DSL, CallTree, and metrics boundary before CBD uses
it. RQ-04 completed Jul. 20, 2026. Component behavior can query an admitted
tree only through the protected internal DSL, while the ExecutionContext
decorator emits payload-safe `resource-tree.query` CallTree spans and runtime
metrics from structured `Consequence` outcomes. Start RQ-05.
RQ-05 completed Jul. 20, 2026. CBD Support now queries every bounded
`project.yaml` under a declared development tree, projects each result as
independent `working` evidence, and initializes local inputs before its
catalog/status ActionCall projections. Start RQ-06.
