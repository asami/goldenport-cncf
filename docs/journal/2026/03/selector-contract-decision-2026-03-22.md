# Selector Contract Decision Record

status=accepted
date=2026-03-22
owner=cncf-runtime
scope=cli-http-selector-contract

## Purpose

Record the usage decision for selector/path input contracts and
compatibility behavior.

This is a decision log, not an implementation work instruction.

## Decision

Public contract:

- Command mode canonical selector is `x.y.z` (`component.service.operation`).
- Server HTTP canonical path is `/x/y/z`.

Compatibility behavior (non-contract):

- Some non-canonical forms currently working in implementation may remain
  available as hidden compatibility behavior.
- These forms are intentionally undocumented in user-facing manuals.
- Clients/scripts/automation must not rely on them.

Future policy:

- Non-contract compatibility forms may be rejected in future releases.
- When rejected, they should fail explicitly as `BadRequest` class errors.

## Rationale

- Keep the public API surface simple and deterministic.
- Avoid expanding manual/CLI surface with accidental historical inputs.
- Preserve short-term operational continuity while not promoting legacy forms.
- Keep room for tightening syntax later without breaking the declared contract.

## Current Compatibility Snapshot (2026-03-22)

- Command mode:
  - `x.y.z` works (contract).
  - Some slash forms are interpreted by current parser paths.
- Server HTTP mode:
  - `/x/y/z` works (contract).
  - Dot path forms may also resolve in current implementation.

This snapshot is observational and non-normative.

## Documentation Policy

- Manual/design documents must describe only the public contract.
- Compatibility-only forms must not be promoted in top-level usage docs.
- Spec may mention compatibility policy as non-contract behavior and
  deprecation candidate.

## Reflected Documents

- `docs/design/cncf-cli-spec.md`
- `docs/spec/path-resolution.md`

## Revisit Triggers

Revisit this decision when one of the following occurs:

1. PathResolution staged rollout moves beyond command mode.
2. Compatibility forms cause support/debug cost or ambiguity.
3. Major version planning includes syntax hardening.

## Change Rule

Any change to this contract requires:

1. update this decision record (new dated revision or successor record),
2. update spec/design documents,
3. update executable specifications for observable behavior.
