# Component Scaladoc Projection

Status: stable design

## Purpose

The Component Scaladoc projection is deterministic public API documentation
staged by sbt-cozy for one CAR build. Its canonical manifest schema is
`cozy.component-scaladoc.v1`. The projection is build evidence and package
input; it does not grant authority to disclose source or restricted material.

## Canonical Staging Format

The staging directory is `target/cozy/scaladoc`. It contains the regular
public Scaladoc files and one UTF-8 `scaladoc-manifest.json`. The manifest has
no trailing newline or discretionary whitespace. Its root fields are written
in this exact order:

1. `schema`, with value `cozy.component-scaladoc.v1`;
2. `sourceDigest`;
3. `contentDigest`; and
4. `entries`.

Each entry is ordered lexically by slash-separated path and has fields in the
exact order `path`, then `sha256`. Paths are safe relative paths. Both digest
fields and every entry digest are lower-case SHA-256 values. `sourceDigest` is
the SHA-256 digest of the sorted inventory of regular Scala files under
`src/main/scala`; each inventory record is `path + "\t" + sha256 + "\n"`.
`contentDigest` uses that same canonical record form for the sorted staged
content entries. Consequently a changed source, an added, missing, or changed
staged file, or a changed manifest is observable at the consumer boundary.

## Public Documentation Policy

`cozyScaladocArchive` invokes ordinary `Compile / doc` only when staging does
not already exist. It rejects `-private` and source-link options, excludes the
generator's `src-html` source pages while staging, and then verifies the staged
result. The staged output must contain `index.html` and generated
search-or-symbol evidence. It must not contain source pages, symbolic links,
unsafe paths, or source links.

When a staging directory already exists, `cozyScaladocArchive` is a strict
verification task. It does not silently regenerate it. This makes stale source
inputs and target-only tampering fail clearly before a package can be made.

## Producer and Consumer Boundary

sbt-cozy owns normal ScalaDoc invocation, deterministic staging, and public
task `cozyScaladocArchive`. `cozyBuildCar` consumes the verified directory and
transports only its optional `--scaladoc-dir` to Cozy's existing package-car
bridge. The bridge does not select resources or establish disclosure policy.

Cozy owns strict staging admission for package-car and copies every verified
manifest entry byte-for-byte to `scaladoc/<path>` in the CAR, plus the
manifest at `scaladoc/scaladoc-manifest.json`. No file outside the verified
manifest is admitted under `scaladoc/`.

## Strict Failures

Verification rejects a missing or unsafe staging directory, manifest, or
`index.html`; an invalid or noncanonical manifest; unsorted or unsafe paths;
a symbolic link; a missing, extra, or changed staged entry; a changed Scala
source input; a content digest mismatch; source-page material; and source
links. These failures are intentionally strict and do not trigger
regeneration in the verification path.

## Non-Goals

This projection does not package SourceCode payloads, provide restricted
access, introduce a runtime resolver, modify Phase 58 resource contracts, or
authorize publication or deployment. Inventory and package evidence remain
evidence only; disclosure authorization is outside this contract.
