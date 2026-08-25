# Closed-Network Documentation Hub SAR Profile Specification

Status: normative DOC04-B/S02 contract

## Contract

`ClosedNetworkDocumentationHubSarProfile.createC(sarArtifactId, logicalRelease,
constituents)` shall accept only SAR descriptive metadata and ordered typed
constituents. `sarArtifactId` shall match
`[A-Za-z0-9][A-Za-z0-9._-]*`. `logicalRelease` shall be nonempty, trimmed, and
free of control characters.

The constituent roles shall equal the complete declared enum sequence exactly:
`Cncf`, `Cml`, `Cozy`, `TextusBok`, and `ApprovedRetrievalProvider`. Therefore
absence, duplication, and reordering shall be rejected. Each constituent shall
be an S01 `Present` profile, retain a documentation-component snapshot, and
have `Local`, `Installed`, or `Cached` snapshot availability. `Online` and
`Unavailable` snapshot availability shall be rejected. A canonical framework
publication may remain `Online`; it records source-of-truth origin rather than
closed-network snapshot availability.

For every constituent, the factory shall reconstruct a manifest from only the
retained framework documentation and optional Guide/Skill entry evidence, using
the retained snapshot Component ID/release and framework publication, and shall
invoke `FrameworkDocumentationProfile.createC`. The reconstructed valid profile
shall exactly equal the supplied retained `Present` snapshot. Snapshot
`(componentId, logicalRelease)` pairs shall be unique across all five slots.
No role shall coerce or rewrite framework product/version/generation/URL/digest.

## Exclusions

The contract accepts no target Component, `ComponentDevelopmentContext`,
operation mode, GenericSubsystemDescriptor, SAR extractor, path, URI,
resolver, resource reader, runtime activation, Help, HTTP, CLI, CBD, BoK,
configuration, schema, codec, artifact lookup, package lookup, source scan, or
network input. It constructs no SAR artifact and grants no runtime permission.
The approved retrieval-provider role remains an unprivileged declarative slot:
it provides no credentials, URLs, activation, installation, execution, MCP,
disclosure authority, or retrieval behavior, and the type does not grant
approval.

## Executable Specification

`org.goldenport.cncf.knowledge.ClosedNetworkDocumentationHubSarProfileSpec`
specifies:

- `P594-DOC04B-S02-AC01`: accepted deterministic five-slot composition using
  Local, Installed, and Cached Documentation Component snapshots.
- `P594-DOC04B-S02-AC02`: rejection of missing, duplicate, reordered roles and
  duplicate Documentation Component snapshot identities.
- `P594-DOC04B-S02-AC03`: rejection of absent, Online, or Unavailable snapshots
  and malformed SAR artifact ID or logical release.
- `P594-DOC04B-S02-AC04`: construction from only caller-supplied typed profile
  evidence, with no runtime descriptor, operation, or resolver input.
