# Phase 109: CAR Security and Vulnerability Metadata

status=planned
planned_at=2026-10-14
depends_on=[Phase 108]
authority=[CAR Security and Vulnerability Metadata](../design/car-security-metadata.md)
consumers=[Cozy Phase 84, Textus Control Center Phase 7]

## Goal

Implement the minimal CAR/runtime metadata and provider boundary needed to bind
deployed CAR/dependency versions to SBOM/security advisory information and feed
safe update decisions.

## Work

1. Define released/deployed CAR software identity and dependency inventory/SBOM
   reference contract.
2. Define provider-neutral advisory projection with source/provenance,
   affected version range, severity/status, fixed release reference and
   freshness/unknown state.
3. Resolve applicable advisory state across the relevant CAR dependency closure.
4. Expose deployed CAR security status through CNCF operational projection.
5. Join a fixed-release candidate to existing release admission, DataStore ABI
   classification and Normal/Maintenance Update decision.
6. Feed eligible fixes to the existing Deployment Transaction; do not implement
   a second security-only deploy engine.
7. Preserve Unknown/stale/unscanned distinctly from no-known-applicable finding.

## Boundary

Phase 109 does not create a proprietary CVE database, vulnerability scanner or
package ecosystem. Prefer external standard advisory/SBOM providers/formats and
adapt their attributable evidence.

## Acceptance

A representative deployed CAR with an affected dependency is shown as affected;
a fixed compatible CAR is identified as Normal Update eligible and can enter
the ordinary Deployment Transaction; a fixed CAR with breaking DataStore ABI is
reported as Maintenance Update required; missing/stale advisory evidence is not
reported as secure.
