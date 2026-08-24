# Component Source Archive Projection

Status: stable design

## Purpose

The Component source archive projection is deterministic source-inventory
evidence for one local sbt project. Its source-of-truth JSON format is schema
`cozy.component-source-archive.v1`. The artifact records the admitted
project-relative source identities and their SHA-256 digests; that ordered
digest inventory is the artifact's provenance.

This contract does not identify a Component release or define a package. It is
available to an ordinary sbt project and does not require a CAR identity,
`project.yaml`, generation, compilation, or packaging.

## Canonical Format

The canonical UTF-8 JSON document has no trailing newline or discretionary
whitespace. Its root fields are written in this exact order:

1. `schema`, with value `cozy.component-source-archive.v1`;
2. `archiveDigest`, a lower-case SHA-256 value; and
3. `entries`, an array in lexical ascending project-relative `/` path order.

Each entry has fields in the exact order `path`, then `sha256`. `path` is a
safe project-relative slash-separated path and `sha256` is the lower-case
SHA-256 digest of that file's bytes. `archiveDigest` is the lower-case SHA-256
digest of the UTF-8 concatenation of every canonical entry record in array
order:

```
path + "\t" + sha256 + "\n"
```

The JSON deliberately writes these known fields rather than deriving their
order from map iteration. It contains no absolute path, timestamp, output
root, host value, file content, or separate provenance text.

## Admission Algorithm

The producer considers regular, non-symbolic-link files beneath `src/**` plus
an existing root `project.yaml`, `build.sbt`, and `README.md`. It excludes a
symbolic link, any path resolving outside the project root, `.git`, `.env` and
`.env.*`, `cache`, `classes`, `download`, `host-local`, `jars`, `local.conf`,
`log`, `secrets`, `target`, `temp`, and `tmp`; it also excludes names ending
in `.class`, `.jar`, `.log`, `.temp`, or `.tmp`. The producer normalizes
accepted paths to slash-separated project-relative identities, calculates
file digests, sorts entries lexically, then calculates and renders the archive
digest.

The writer accepts only a destination inside the project root's `target/`
tree. It creates required output parents, writes the canonical UTF-8 bytes,
and rejects a destination that escapes that tree or resolves outside the
project root. Because `target` is excluded from input admission, a written
projection cannot include itself.

## Producer and Consumer Boundary

Cozy's package-private `ComponentSourceArchiveProjection` is the canonical
producer and writer. The private `CozySbtBridge` action
`write-component-source-archive` transports only `--project-dir` and `--save`
to that producer. sbt-cozy exposes `cozySourceArchive`, which writes
`target/cozy/component-source-archive.json`; it is the sole public task
consumer in this stage.

The representative scripted fixture is an ordinary non-CAR sbt project. It
checks structural JSON, expected entry/digest values, exclusion of generated
and host material, target-only output, and byte-identical output after
`clean`.

## Validation Scope

Focused Cozy specifications cover canonical entry ordering and JSON field
order, hostile and host-state exclusion, symbolic-link exclusion, target-only
writer admission, and repeated writer bytes and digests. The sbt-cozy scripted
fixture covers the bridge and public task from a separate ordinary sbt project.

## Non-Goals

This artifact is source-inventory evidence only. It is not a release-source
payload, SourceCode Subcomponent, CAR entry, Phase 58 resource contract,
actual source package, or a license, disclosure, or restricted-access policy.
It does not capture managed sources, source content, Scaladoc, SmartDox,
runtime/package behavior, resolver behavior, or CAR packaging behavior.
