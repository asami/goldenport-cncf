# Component Release-Source Projection

Status: Phase 59.3 implementation contract

## Boundary

`cozy.component-release-source.v1` is the deterministic release-source staging
contract. It is distinct from `cozy.component-source-archive.v1`, which remains
inventory-only evidence and does not package source bytes. The release-source
policy is opt-in project metadata at `packaging.car.release_source`:

- `mode: public` and a nonempty safe `license` admit the filtered source bytes;
- `mode: restricted` and a nonempty safe `license` record explicit restricted
  SourceCode identity and policy evidence without source bytes; and
- no policy block is backward-compatible: no release-source stage is created
  and an existing CAR has no release-source entries.

Any present block with an unsupported mode, missing license, or unsupported
field fails deterministically.

## Canonical staging

The stage is exactly `target/cozy/release-source` and contains the canonical
UTF-8 `release-source-manifest.json` plus, only for `public`, the manifest's
declared payload files. The manifest has fixed field order and no trailing
newline:

1. `schema`, with value `cozy.component-release-source.v1`;
2. `policy`, containing exactly `mode` and `license`;
3. `sourceIdentity`, with value `SourceCode`;
4. `sourceDigest`, the SHA-256 inventory digest of all current authored and
   normalized managed-source inputs;
5. `contentDigest`, the SHA-256 inventory digest of the public payload entries
   (the digest of an empty entry set for restricted policy);
6. `buildEvidenceDigest`, followed by `buildEvidence`; and
7. ordered `entries`, each containing `path` and `sha256`.

Authored entries use the existing ComponentSourceArchiveProjection admission:
regular non-symlink files under `src/**` and present root `project.yaml`,
`build.sbt`, and `README.md`, with its established exclusions. SBT supplies
regular non-symlink Scala files from `Compile / managedSources` and
`Test / managedSources`; they are normalized as
`generated-source/main/<relative>` and `generated-source/test/<relative>` under
their respective managed-source roots. Duplicate normalized paths, escaping
or aliased roots, and transient or symbolic-link inputs are rejected.

`buildEvidence` records declared compile/test `scalacOptions`, resolved
dependency coordinates, and generator coordinates. Evidence values are
deterministic identities only: absolute paths, target layouts, caches, hosts,
timestamps, secrets, raw source bytes, and local configuration are forbidden.
Every public payload entry has SHA-256 evidence, and canonical bytes plus
source, managed-source, and build-evidence digests are checked before CAR use.
Restricted staging intentionally has no source entries or content index and
does not claim that source content is publicly available.

## CAR transfer and freshness

The public sbt task is `cozyReleaseSourceArchive`. Existing staging is strictly
verified rather than reused blindly. At package time, the SBT bridge supplies
the current authored projection inputs, Compile/Test managed Scala source lists
and roots, and canonical scalac/dependency/generator build evidence together
with the optional stage. Cozy performs full revalidation of authored entries,
managed inputs, build evidence, canonical manifest bytes, and staged payload
bytes before transfer. The release-source policy is resolved from project
metadata only (`project.yaml` `packaging.car.release_source`); operation
defaults never opt a CAR into source transfer. The SBT bridge transports the
verified optional directory through the `--release-source-dir` `package-car` option;
Cozy is the sole CAR consumer and verifier. Public CAR packaging copies exactly
the verified staged payload files under `source/` and
`source/release-source-manifest.json`. Restricted packaging copies only the
verified manifest at that canonical path. Nothing outside the manifest's
declared public payload is copied.

An unconfigured CAR follows its existing build graph and packaging behavior.
This boundary does not modify Component identity, ABI, runtime, resolver,
dependency substitution, operation mode, activation, authorization, or Phase
58 resource contracts. File membership never grants disclosure authority.

## Validation scope

Executable specifications cover canonical public ordering and digest evidence,
managed-source normalization, hostile/symbolic-link/transient rejection,
duplicate and escaping rejection, restricted manifest-only policy, repeated,
stale, and tampered strict verification, and exact public/restricted CAR
payload transfer. Unit specs cover projection and packaging behavior. An
in-tree current-Cozy bridge integration spec covers end-to-end release-source
staging and CAR transfer because the released Cozy `0.3.2.4` runtime cannot
know the newly introduced bridge action. This is test isolation, not a runtime
fallback or an exception to CAR exact-version authority.
