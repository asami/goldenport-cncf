# Phase 61.4 Checklist - Information DSL, Transport, Help, and Editor Projections

status=closed
phase=[Phase 61.4 - Information DSL, Transport, Help, and Editor Projections](phase-61.4.md)
predecessor=[Phase 61.3 Checklist](phase-61.3-checklist.md)
successor=[Phase 61.5 Checklist](phase-61.5-checklist.md)

## IC-06: DSL, Transport, Help, and Editor Projections

Stage Status:
- Current status: DONE
- Owner: CNCF Behavior, projection, HTTP/Web, and Help maintainers
- Update rule: Update only from accepted IC-06 evidence; preserve IC-05
  lifecycle and authorization behavior.
- Entry rule: IC-05 is DONE in Phase 61.3.
- Completion rule: Every CNCF access surface uses the canonical Entity and
  preserves revision, authorization, and managed-input boundaries.

- [x] Migrate protected `information_*` Behavior DSL operations.
- [x] Preserve ExecutionContext and CallTree recording for every operation.
- [x] Project revision as system/read-only output metadata.
- [x] Exclude revision from application Create input.
- [x] Require and validate observed revision on applicable edit/update forms
  and requests.
- [x] Migrate Information editor descriptors, field projections, actions, and
  disabled reasons.
- [x] Migrate the existing System Admin Information HTTP/Web projection;
  classify Help, generic HTTP JSON/YAML/XML/Form, schema, OpenAPI, and MCP as
  inapplicable because no Information projection seam exists.
- [x] Preserve raw provider-payload exclusion.
- [x] Preserve structured stale-conflict presentation.
- [x] Add projection parity and authorization-isolation specifications.

Evidence:
- The accepted IC-06 Step commit is `79c5e6b4a3c7591353ae05d79ccd60a5402bafd9`.
- The mandatory Phase review found four projection-boundary blockers. Repair
  cycle 1 narrowed applicability to actual seams, sanitizes profile output,
  retains structured Entity stale-write metadata, and proves component
  isolation plus anonymous denial. Focused validation passed 374 tests in 3
  suites (`90686-20260831T220225Z`); the independent focused re-review passed
  with no Current Phase Blocker.
- The distinct release commit is bound under `phase61.4-clb-ic06-20260901`.
  Phase 61.5 remains planned and unstarted.
