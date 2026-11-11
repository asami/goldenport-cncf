# Scoped SBT transport v1

This contract is the CNCF transport seam, not an implementation of artifact
publication, selection state, or sbt-cozy resolution. A host grants a distinct
`scoped-publish` SBT program whose fixed task is `smDevelopmentArtifactPublish`.
Selected consumer compilation uses the ordinary `compile` program. Both go
through the existing typed SBT UnitOfWork; an ordinary invocation remains
unchanged. Callers can issue `UnitOfWorkOp.SbtExecAttempt(target,
SbtOperation.ScopedPublish)` directly; no new convenience DSL is required.

The optional typed input identifies the actual component, build workspace,
owner resource workspace, scope and selection IDs, and a workspace-relative
execution-input file under that owner resource workspace. Producer input additionally identifies the publication,
repository and staging paths under the owner resource workspace. The owner resource workspace requires an explicit workspace grant. Paths are
resolved through the workspace admission boundary, including its symlink checks;
the owner resource may be nested in the build workspace.

The UTF-8 JSON execution input is at most 64 KiB. Its `schema` is
`cncf.scoped-sbt-input.v1`; `kind` is `consumer` or `producer`; `component`,
`buildWorkspace`, `ownerWorkspace`, `scope`, and `selection` match the typed
input exactly. Producer JSON also has `publication`, `repository`, and `staging`
matching the typed paths. The file may include adapter-owned additional fields; CNCF does not validate
those fields. The future sbt-cozy adapter must validate its own settings before
use and reject invalid explicit settings without ordinary fallback.
All IDs are nonempty ASCII letters, digits, hyphen, underscore or dot, up to
128 characters. The input file must already exist and be a regular file;
producer repository and staging must already exist as directories. Missing,
invalid, foreign or contradictory input rejects admission before process launch.

The transport passes precisely `-Dsm.artifact.selection=<resolved-input-path>`
as a per-build SBT argument. It neither rewrites this input nor proves its bytes
unchanged after admission. The producer task's process result describes execution
only: zero exit does not certify completed or selectable publication. The
separate sbt-cozy integration owns interpretation of adapter settings, metadata,
coordinate allocation, staging and publication. sm-workflow owns lifecycle and
selection records; their completion and actual consumer resolution require
separate evidence.
