# Phase 55 GCF-09D: Service-Container Runtime Policy

Date: 2026-08-03

GCF-09D makes `textus.service-container.driver` and
`textus.service-container.docker.executable` Subsystem-scoped catalog
parameters. Their historical `textus.runtime.*`, `cncf.*`, and
`cncf.runtime.*` spellings remain decode-only aliases: decoding preserves the
original spelling and path in provenance while resolving the canonical typed
parameter witness.

`Subsystem.serviceContainerRuntimeC` now resolves the driver and executable
only from its admitted runtime binding collection. No admission fails
structurally. An admitted collection without a driver does not revive raw
configuration and therefore leaves service-container resolution unavailable;
when the admitted driver is Docker, an absent executable uses the `docker`
default. The compatibility `ResolvedConfiguration` factory remains
confined inside the service-container package; the Subsystem installation
boundary is CNCF-internal, so an external raw-configured runtime cannot be
installed before the admitted path runs.

The executable regression uses a malformed raw Docker executable and a valid
admitted executable. It exercises the Subsystem boundary and confirms that the
installed observed runtime retains `/opt/textus/docker`, proving that the raw
value cannot replace the admitted one.

Serialized compile and focused validation passed 27/27 at
`7875-20260803T051001Z`; final independent re-review is clean. This record
does not change Phase 53 history, Phase 54 lifecycle design, or Phase 55
deferrals.
