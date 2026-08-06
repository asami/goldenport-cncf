# Phase 55 Configuration Binding Migration Guide

Status: current developer guidance (GCF-10A)

This guide turns the normative generic and CNCF documents into a small
consumer migration checklist. It does not add a new API or admit a new key.

## Replace raw reads with one typed projection

For each admitted setting:

1. select the registered canonical parameter witness;
2. admit source values as candidates through the closed catalog (including only
   documented decode-only aliases);
3. resolve the candidates once for the exact Global, ComponentClass,
   SubsystemInstance, or fully qualified ComponentInstance target; and
4. pass only the narrow value or policy object to the consumer.

Remove local `String`-key scans, `ResolvedConfiguration.get*` calls, local merge
or alias tables, and consumer-owned trace/provenance maps. A compatibility
adapter may remain only at an explicit external boundary and must not become an
internal authority.

## Fixed-user and profile values

Use the admitted typed fixed-user/profile projection for identity, display name,
locale, and timezone. An identity change is not an ordinary override: it must
be rejected before Subsystem binding unless the operator supplies explicit data
migration or an isolated datastore. The resolver never moves data, chooses a
datastore, or records automatic identity migration. A clean isolated datastore
with one effective new identity is admissible. Formatting assumptions come from
the resolved profile, not request-restored or host defaults. The diagnostic
must not reveal either identity, profile path, or descriptor `local_subject`;
that descriptor value is capability wiring only and cannot become fixed-user
identity authority.

## Secret-safe diagnostics

Declare credential locators with the secret-reference contract. Components
receive an opaque reference or a structured unavailable result, never secret
material. Do not include secret references, confidential values, raw source
paths, aliases, candidates, provenance, or trace internals in logs, responses,
CallTree attributes, or exceptions. Use the sanitized trace projection; it
redacts current and overridden confidential values and cannot reload resources.

## Completion check

A migration is coherent when the consumer receives one immutable value-only
projection, no production path reads raw sources/candidates/aliases, and all
compatibility behavior is exercised at the admitted boundary. Full validation,
review, and release evidence remain Phase 55 closure gates.
