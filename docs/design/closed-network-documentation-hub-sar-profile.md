# Closed-Network Documentation Hub SAR Profile

Status: current DOC04-B/S02 design boundary

## Purpose

`ClosedNetworkDocumentationHubSarProfile` is a typed, read-only composition of
five retained S01 framework-documentation snapshots. Its deterministic slots
are `Cncf`, `Cml`, `Cozy`, `TextusBok`, and
`ApprovedRetrievalProvider`, exactly once and in that declaration order. Role
names are composition positions only; they do not rewrite, select, or infer a
publication product, version, generation, canonical URL, or digest.

The supplied SAR artifact ID and logical release are descriptive metadata. The
artifact ID is a safe index-style token, while the logical release is nonempty,
trimmed, control-character-free text. Neither value is an artifact descriptor,
path, URI, package lookup, or runtime identity.

## S01 Revalidation and Closed Network

Each constituent must retain an S01 `Present` snapshot. The Hub extracts only
the retained framework-documentation entries and optional Guide and Skill
Catalog entry evidence, reconstructs a caller-supplied `ComponentKnowledgeManifest`
using the retained context snapshot Component ID/release and framework
publication, and invokes `FrameworkDocumentationProfile.createC` again. This
does not trust a hand-constructed case-class value and performs no read, scan,
resolver, or network operation.

A constituent snapshot reference must be `Local`, `Installed`, or `Cached`.
`Online` and `Unavailable` are not closed-network snapshot sources. This
condition applies to the retained Documentation Component snapshot, not the
canonical framework publication reference: an online canonical reference may
remain valid evidence of origin. Snapshot `(componentId, logicalRelease)` pairs
must be unique across slots.

## Source of Truth and Operational Exclusions

S01 remains the source of truth for publication, documentation, and optional
public Guide/Skill metadata validation. This profile introduces no source
content, credentials, directives, profiles, rules, prompts, or authority
configuration.

`ApprovedRetrievalProvider` is declarative only. Its presence does not grant
approval, credentials, URLs, activation, installation, execution, MCP,
disclosure authority, or retrieval behavior. The profile creates no run
permission and implements no SAR file, descriptor, package, startup, target
Component, development context, operation mode, resolver, resource reader,
Help, HTTP, CLI, CBD, BoK, configuration, schema, or codec behavior.
