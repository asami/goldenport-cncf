# CAR Security and Vulnerability Metadata

status=initial
authority=canonical
updated_at=2026-10-14
related=[Textus Security Architecture](textus-security-architecture.md), [DataStore ABI](datastore-abi.md), [Availability Architecture](availability-architecture.md)

## Purpose

CAR security metadata provides the runtime/operations contract needed to know
what software is actually deployed, what dependencies it contains or references,
which known security advisories affect it, whether a fixed CAR is available,
and whether that fix can enter the ordinary immutable update path.

CNCF owns the CAR/runtime metadata model. Control Center is a consumer and must
not become a second vulnerability database or invent dependency truth.

## Software identity and dependency evidence

A released CAR should expose or reference sufficient immutable release metadata
to identify:

- CAR identity and version;
- dependent CAR identities/version constraints;
- packaged/runtime software dependencies needed for vulnerability assessment;
- an SBOM or equivalent dependency inventory/reference where produced;
- release/admission identity needed to bind findings to the deployed artifact.

The model should prefer standard SBOM/advisory formats where practical rather
than defining a Textus-only vulnerability ecosystem.

## Vulnerability/advisory projection

A security advisory projection binds an external or Textus advisory identity to
the affected CAR/dependency/version range and records severity/status,
known-fixed version or remediation reference where available, source/provenance,
and observation/update time.

Absence of a known advisory is not proof of security. Unknown/unscanned/stale
status remains distinguishable from no currently known applicable advisory.

## Dependency closure

Operational vulnerability state is evaluated over the relevant deployed
dependency closure, not only the top-level CAR. A CAR may require remediation
because an included library or dependent CAR is affected even when the
top-level CAR has no direct advisory.

Exact transitive-resolution ownership follows the CAR dependency/runtime model;
Control Center consumes the authoritative projection rather than rescanning
arbitrary runtime files.

## Update decision integration

Vulnerability information connects to release compatibility rather than ending
at an alert:

```text
affected deployed CAR
 -> fixed CAR/release available?
 -> release/admission compatibility
 -> DataStore ABI compatible?
 -> Normal Update / Maintenance Update
 -> Deployment Transaction
```

A fixed CAR that satisfies the Normal Update contract, including required
DataStore ABI backward compatibility, may be eligible for
automatic-compatible deployment policy. A breaking fix is surfaced as
Maintenance Update required.

Security severity may influence urgency/approval policy but does not silently
override DataStore ABI or runtime admission constraints.

## Operations projection

The runtime/operations API should make available at least deployed CAR version,
security/advisory status, dependency/SBOM evidence freshness, fixed-release
reference when known, DataStore ABI update classification, and current
Deployment Transaction state.

Credentials, private repository secrets and unnecessary dependency source
content are not part of this projection.
