# Phase 66 - CAR Skill Bundle Contract

status=planned
planned_at=2026-08-13
depends_on=[Phase 65](phase-65.md)
strategy=[CNCF Development Strategy](../strategy/cncf-development-strategy.md)
checklist=[Phase 66 Checklist](phase-66-checklist.md)
direction=[Codex Skill Bundle Contract Direction](../journal/2026/07/2026-07-21-codex-skill-bundle-contract.md)

## Purpose

Define and implement the CNCF-owned, versioned, transport-neutral
`SkillBundleManifest` contract used to carry Component-owned Codex Skills from
one development source tree into one CAR without changing their logical
identity or content integrity.

Phase 66 supplies the shared model, schema, codec, deterministic validator,
canonical source/archive locations, compatibility semantics, and normative
fixtures required by Cozy Phase 24. It does not implement CAR projection or
local Skill installation.

## Dependency

Phase 66 begins after Phase 65 closes.

Its technical foundations are the canonical Component/CAR identity and
repository contracts, Phase 58 logical Component resource and integrity
contracts, Phase 59 Skill catalog and non-activation boundaries, and the
accepted Skill bundle direction recorded on 2026-07-21. Phase 66 must consume
those identities without introducing a second Component, CAR, repository, or
resource resolver.

## Cozy Phase 24 Relationship

- Phase 66 is the CNCF supplier phase for Cozy Phase 24 `SK24-01: CNCF Skill
  Bundle Contract`.
- Cozy Phase 24 remains planned and blocked at `SK24-01` while Phase 66 is
  incomplete. Cozy must not invent a private manifest schema, codec, path
  convention, digest rule, compatibility interpretation, or fixture format.
- Phase 66 closure must publish an exact contract identity, accepted CNCF
  commit/artifact identity, normative design/specification, and shared fixture
  identities that Cozy can consume.
- Cozy may mark `SK24-01` DONE only after recording and verifying that Phase 66
  closure evidence against every `SK24-01` checklist item. Planning Phase 66
  alone does not change the Cozy ledger.
- After that handoff, Cozy Phase 24 `SK24-02` owns source validation,
  `cozy lint skill`, deterministic CAR projection, package provenance, and
  source/archive equivalence at the real packaging boundary.
- CNCF Launcher and Textus Launcher installation remain Cozy Phase 24
  `SK24-03` and `SK24-04` integration responsibilities. Phase 66 defines the
  common contract they consume but does not implement either command surface.
- Phase 66 completion unblocks Cozy Phase 24; it does not close or absorb any
  Cozy implementation stage.
- Strategy item 9.38 remains cross-repository work after Phase 66 closes and is
  complete only after the downstream Cozy Phase 24 packaging, launcher, and
  end-to-end stages close.

Repository-qualified Cozy references:

- `cozy:docs/phase/phase-24.md`
- `cozy:docs/phase/phase-24-checklist.md`
- `SK24-01` is the contract handoff gate.
- `SK24-02` is the first Cozy implementation stage after the handoff.

## Selected Direction

- `SkillBundleManifest` is a packaging, integrity, compatibility, and
  discovery contract. It is not an executable Skill format or an authority
  grant.
- One logical bundle has one stable identity and one deterministic content
  view in development-source and CAR-archive representations.
- Bundle and Skill identity, version, description, relative path, digest,
  compatibility, dependency declaration, and optional logical MCP requirement
  semantics are explicit and versioned.
- Canonical paths are relative, normalized, relocation-stable, and validated
  against traversal, absolute paths, symlink escape, duplicate identity, and
  ambiguous spelling.
- Digest input, encoding, ordering, canonical serialization, and source/archive
  equivalence are fixed by normative specification and property-based
  Executable Specifications.
- Required unsupported schema or compatibility declarations fail admission;
  they never degrade to silently inactive metadata.
- A declared dependency is information only. Validation or installation never
  activates it implicitly.
- An MCP requirement describes a required logical endpoint/tool contract but
  cannot configure Codex, start a server, invoke a tool, or grant authority.
- Validation never executes a bundled file and performs no network, process,
  credential, or user-configuration mutation.
- CNCF publishes one normative valid/invalid fixture family and one stable
  consumer API so Cozy and both launchers do not duplicate contract logic.

## Work Stack

| ID | Stage | Outcome | Status |
| --- | --- | --- | --- |
| CSB-01 | Inventory and ownership freeze | Current CAR resource, Skill metadata, compatibility, digest, path, and consumer boundaries are reconciled with Cozy Phase 24 and fixed by failing-first acceptance identities. | planned |
| CSB-02 | Manifest model and schema | A versioned transport-neutral manifest model and schema define bundle, Skill, compatibility, dependency, and optional MCP requirement semantics. | planned |
| CSB-03 | Canonical path, digest, and equivalence contract | Development-source and CAR-archive locations, normalization, ordering, canonical bytes, digests, and relocation-stable equivalence are fixed. | planned |
| CSB-04 | Codec, validator, and failure semantics | CNCF supplies one deterministic codec/validator with structured rejection outcomes and no execution or authority side effects. | planned |
| CSB-05 | Normative fixtures and consumer API | Shared valid/invalid fixtures, property specifications, and a stable consumer surface are available to Cozy and both launchers. | planned |
| CSB-06 | Contract promotion and Cozy handoff | Design/specification, full CNCF validation, review, release evidence, and an item-by-item `SK24-01` handoff are complete. | planned |

## Acceptance

- One logical bundle has the same bundle identity, Skill set, canonical Skill
  bytes, and digests when viewed from its development source and its CAR
  archive location.
- Canonical encoding and ordering are deterministic across cold and repeated
  codec/validation runs and across supported JVM environments.
- Absolute paths, traversal, symlink escape, duplicate bundle or Skill
  identity, missing content, undeclared content where the validated view is
  closed, digest mismatch, unsupported required schema, incompatible required
  versions, and malformed requirements fail with stable structured outcomes.
- Compatibility and dependency declarations have explicit required/optional
  semantics and never trigger implicit installation or activation.
- MCP declarations cannot mutate configuration, start or contact an endpoint,
  invoke a tool, disclose credentials, or grant runtime authority.
- Validation executes no Skill file and has no ambient network, process,
  filesystem-outside-input, credential, or user-configuration side effect.
- CNCF design/specification and Executable Specifications define the complete
  contract consumed by Cozy; the journal is no longer the implementation
  authority after Phase 66 closes.
- Normative valid/invalid fixtures cover every required Cozy `SK24-01`
  acceptance class and are addressable by stable identity and digest.
- The accepted API/artifact and fixture handoff lets Cozy start `SK24-02`
  without redefining any CNCF-owned rule.
- Focused and full CNCF validation, clean review, required review-fix/re-review,
  documentation promotion, version evidence, and the Phase 66 release commit
  complete before the Cozy gate is reported satisfied.

## Non-Goals

- Cozy source validation, `cozy lint skill`, CAR build projection, or package
  provenance implementation.
- CNCF Launcher or Textus Launcher install, update, status, uninstall,
  rollback, source-freshness, repository-resolution, or Codex-scope mutation.
- Automatic installation because a CAR is downloaded, resolved, or executed.
- Executing bundled scripts or using a manifest as an Operation/MCP invocation
  contract.
- Automatic dependency activation or unrestricted Codex/MCP configuration
  merge.
- A remote Skill marketplace independent of CAR distribution.
- Component-specific Skill content authoring or application-specific authoring
  UI.
- Reopening Phase 59 Component documentation/knowledge packaging or Phase 60
  Admin implementation.

## Planning References

- [Phase 66 Checklist](phase-66-checklist.md)
- [CNCF Development Strategy](../strategy/cncf-development-strategy.md)
- [Codex Skill Bundle Contract Direction](../journal/2026/07/2026-07-21-codex-skill-bundle-contract.md)
- [Phase 58 series, closing in Phase 58.9](phase-58.9.md)
- [Phase 59](phase-59.md)
- [Phase 65](phase-65.md)
- `cozy:docs/phase/phase-24.md`
- `cozy:docs/phase/phase-24-checklist.md`
