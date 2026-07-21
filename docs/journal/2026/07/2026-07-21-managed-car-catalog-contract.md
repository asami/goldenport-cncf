# Managed CAR Catalog Contract Handoff

## Context

Textus Control Center needs to manage a user's important CARs before every CAR
is running.  That includes development projects under `src`, CARs installed in
the local CNCF repository, and selected CARs published through
SimpleModeling.org.

This is not a request to add a Control Center-specific CAR list to CNCF now.
The immediate Control Center work remains an application-level Phase 3.  This
journal records the generic CNCF contracts that Phase 3 should eventually be
able to use instead of reimplementing repository and launcher knowledge.

## Problem Boundary

A managed CAR and a registered runtime subsystem are different facts:

```text
managed CAR/source                 runtime instance
------------------                 ----------------
development project                launcher registration
local repository entry      <-->   health / endpoint / process state
public repository entry            currently running version
```

A CAR that is known but not running must be represented as `not-running`; it
must not be represented as a stale launcher registration.  Conversely, a
runtime registration can exist before the catalog has a corresponding managed
CAR.

## Required Future CNCF Capabilities

### 1. Canonical CAR Descriptor Projection

Provide a stable, machine-readable projection for a development CAR project
and for its packaged form.  It must derive identity from the CAR descriptor,
not from the directory name.

The minimum facts are:

- `artifactId` from `project.name`;
- `componentName` from `project.component.name`;
- component and CAR versions;
- declared default server port when present;
- runtime requirements and dependency identities when declared;
- descriptor schema/version and a source location suitable for diagnostics.

The projection may be generated from `project.yaml`, but its consumer contract
must not require each application to parse project files independently.

### 2. CAR Catalog Source SPI and Snapshot

Define a generic read/refresh contract for logical CAR catalog sources.  The
initial source kinds are:

- development descriptor;
- local CNCF component repository catalog;
- public repository catalog;
- an explicitly configured artifact subscription.

The contract should return normalized identities, available versions, source
kind, refresh timestamp, diagnostics, and source availability.  Credentials,
private file-system locations, and repository implementation details must not
be exposed to untrusted Web clients.

Catalog refresh should produce a controlled persisted snapshot.  It must not
perform arbitrary directory crawling, remote crawling, or process discovery.

### 3. Public Repository Discovery Manifest

The current SimpleModeling.org shape can expose a catalog for a known artifact:

```text
repository/catalog/car/<artifactId>.yaml
```

That is enough to resolve a subscription, but it cannot enumerate public CARs
without knowing each artifact ID first.  Define a versioned public index or
manifest for explicit discovery, with enough metadata to select an artifact
before downloading its CAR archive.  A client must be able to keep a curated
subscription list when global discovery is disabled.

### 4. Optional Runtime-to-Catalog Correlation

Extend the launcher registration/event payload with an optional `artifactId`.
Canonical CNCF and Textus launch paths should populate it from the descriptor.
The field is correlation metadata: registrations without it remain valid and
must continue to be displayed and diagnosed normally.

This permits a management client to link a runtime instance to a logical CAR
without guessing from a development directory or component display name.

## Suggested Runtime Surface

After the contracts above are stable, CNCF may offer an operation/service such
as `CarCatalog` or `ComponentRepositoryCatalog` with:

- list logical CAR records and their source snapshots;
- get one CAR by canonical artifact ID;
- refresh explicitly configured sources;
- report source/descriptor diagnostics separately from runtime health.

The exact names and transport are intentionally undecided.  It must remain a
generic runtime/repository surface rather than importing Control Center UI,
ownership, or local policy concepts into CNCF.

## Acceptance Criteria

- Development identity comes from descriptor content, not a directory basename.
- Local, development, and public entries normalize to one artifact identity.
- A known inactive CAR is distinguishable from an unhealthy runtime instance.
- Known-artifact public catalog resolution works without a global index.
- Public enumeration is possible only when a versioned manifest is supplied.
- Runtime registrations with and without `artifactId` remain compatible.
- Refresh is explicit, bounded, diagnostic, and does not scan arbitrary user
  paths or manage processes.
- Executable specs cover descriptor projection, source normalization, public
  manifest/subscription behavior, and launcher compatibility.

## Out Of Scope

- Implementing a CAR list, Control Center page, or lifecycle controls now.
- Starting, stopping, or discovering arbitrary local processes.
- Defining Control Center ownership, favorites, labels, or user-specific UI.
- Fetching or publishing CAR archives as a side effect of catalog refresh.

## Consumer Relationship

Textus Control Center Phase 3 can initially own its configured sources and
snapshots.  When CNCF supplies this generic contract, Control Center should
migrate its source adapters rather than retain application-specific parsing of
development descriptors and repository catalog files.
