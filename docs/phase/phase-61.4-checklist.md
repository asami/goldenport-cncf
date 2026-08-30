# Phase 61.4 Checklist - Information DSL, Transport, Help, and Editor Projections

status=planned
phase=[Phase 61.4 - Information DSL, Transport, Help, and Editor Projections](phase-61.4.md)
predecessor=[Phase 61.3 Checklist](phase-61.3-checklist.md)
successor=[Phase 61.5 Checklist](phase-61.5-checklist.md)

## IC-06: DSL, Transport, Help, and Editor Projections

Stage Status:
- Current status: OPEN
- Owner: CNCF Behavior, projection, HTTP/Web, and Help maintainers
- Update rule: Update only from accepted IC-06 evidence; preserve IC-05
  lifecycle and authorization behavior.
- Entry rule: IC-05 is DONE in Phase 61.3.
- Completion rule: Every CNCF access surface uses the canonical Entity and
  preserves revision, authorization, and managed-input boundaries.

- [ ] Migrate protected `information_*` Behavior DSL operations.
- [ ] Preserve ExecutionContext and CallTree recording for every operation.
- [ ] Project revision as system/read-only output metadata.
- [ ] Exclude revision from application Create input.
- [ ] Require and validate observed revision on applicable edit/update forms
  and requests.
- [ ] Migrate Information editor descriptors, field projections, actions, and
  disabled reasons.
- [ ] Migrate system admin/debug Information projections.
- [ ] Migrate static Web form, HTTP, JSON/YAML/XML/Form, schema, OpenAPI, and
  MCP projections.
- [ ] Preserve raw provider-payload exclusion.
- [ ] Preserve structured stale-conflict presentation.
- [ ] Add projection parity and authorization-isolation specifications.

Evidence:
- Pending.
