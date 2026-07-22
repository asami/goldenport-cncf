# Codex Skill Bundle Contract Direction — 2026-07-21

## Context

Textus Launcher must install released CAR skills, while CNCF Launcher must
install the corresponding skills from a component development directory. A
CAR-specific installer would duplicate policy and prevent all Textus components
from using one distribution mechanism.

## Direction

CNCF will define a versioned, transport-neutral `SkillBundleManifest` contract
for CAR-associated Codex skills. It is a packaging and compatibility contract,
not a runtime MCP invocation contract and not a mechanism for granting a skill
new authority.

The v1 contract needs, at minimum:

```text
schemaVersion
bundle { id, version, description }
skills { id, relativePath, digest, description }
requires { codex, cncf?, cozy? }
mcp { optional named endpoint requirements and public tool requirements }
```

The contract will define a canonical CAR archive location and a corresponding
development-source location, normalized relative paths, digest semantics,
identity/name collision rules, and compatibility outcomes. A bundle may
declare dependencies, but installation must not activate them implicitly.

## Ownership Boundary

- CNCF owns the model, schema/versioning, identity, archive/source-location,
  compatibility, digest, and deterministic validation rules.
- Cozy owns source validation and CAR projection of the declared bundle.
- Textus Launcher owns published/local/cache CAR resolution and local Codex
  installation.
- CNCF Launcher owns admitted development-directory resolution and
  source-freshness diagnostics.
- A CAR owns its domain-specific skill contents. CBD Support is one such CAR;
  it does not define a special global installer path.

## Safety Direction

The manifest can describe optional MCP requirements but cannot mutate Codex
configuration or cause installation by itself. Launchers may offer an explicit
configuration merge after validation. Installation must be staged and
non-destructive, and no bundle file is executed during validation or install.

## Follow-up

Promote this direction to CNCF design/specification before either Launcher
implements command parsing. Add an executable manifest codec and source/archived
equivalence tests, then let Cozy and the launchers depend on that versioned
contract.
