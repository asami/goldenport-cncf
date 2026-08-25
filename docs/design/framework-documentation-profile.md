# Framework Documentation Profile

Status: current DOC04-B/S01 design boundary

## Purpose

`FrameworkDocumentationProfile` provides a typed, read-only view of
caller-supplied framework publication evidence and an optional Documentation
Component snapshot manifest. It is a framework profile, not a target-Component
documentation owner, selector, or operational dependency.

The framework identity remains the supplied `FrameworkPublicationContext`:
product/version, publication generation, canonical URL, and publication digest.
The profile accepts no target Component ID or `ComponentDevelopmentContext`.
It does not turn a Documentation Component snapshot identity into target
Component identity or ownership.

## Snapshot States

When the supplied valid framework publication has no Documentation Component
snapshot and there is no snapshot manifest, the profile is successfully
`Absent`. This state is not an error and creates no precondition for startup or
Component-specific Help/manual access. Those runtime behaviors are not
implemented by this pure value contract.

A present snapshot requires both the publication snapshot reference and its
manifest evidence. Their Component ID and logical release must match exactly.
The manifest's optional framework publication must equal the supplied framework
publication exactly. The manifest must contain at least one
`FrameworkDocumentation` / `framework-documentation` / `text/markdown`
resource with `Framework` metadata authority.

## Descriptive Public Metadata

The optional public AI Guide and Skill Catalog remain existing descriptive
metadata values. If present, each must reference exactly one snapshot-manifest
resource with `Framework` authority and have `Public` or `Ecosystem`
visibility. The profile introduces no content text, raw directive/profile/rule/
prompt text, paths, credentials, activation, installation, execution, MCP, or
disclosure authority.

## Non-Operational Boundary

The factory validates only caller-supplied typed values. It performs no physical
scan, resource read, resolver call, network operation, target-Component
fallback, OperationMode selection, runtime activation, Help, HTTP, CLI, CBD,
BoK, or SAR behavior. It adds no schema, codec, or serialization surface.
