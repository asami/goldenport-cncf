# Operational identity and ingress context correspondence

Document role: normative operational identity and common ingress contract.
The [paired design](../design/ingress-context-correspondence.md) assigns existing
framework responsibilities. This contract does not change security authority,
execution profile admission or transaction ownership.

## Full canonical operational identities

`TraceId`, `SpanId` and `CorrelationId` retain core `UniversalId` format, labels,
kind and full-value identity. Trace and Correlation have no subkind; Span has
its required declared subkind. Their optional public `timestamp` and `entropy`
constructor arguments default to `None`, retaining the existing two-argument
Trace/Correlation and three-argument Span generation calls. Explicit inputs use
the existing core constructors. Equality and hashing use the entire canonical
value, including timestamp and entropy, rather than only readable labels.
Different full values with the same labels remain different identities.

The canonical form remains `major-minor-kind-epochMillis-entropy`, or
`major-minor-span-subkind-epochMillis-entropy` for Span. Core owns validation,
generation, canonical timestamp representation and parsing. CNCF adds no
normalization or alternate parser. Source construction with existing arguments
and defaults remains supported; appending case-class fields supplies no blanket
binary, extractor-arity or product-arity compatibility promise. These constructor
Options are generation inputs, not a nullable storage or domain Option API.

## Restoration and existing fallback

Common `IngressSecurityResolver` request and attribute entry points restore IDs
from core `parseParts` using the expected kind and complete parsed timestamp
and entropy. Trace/Correlation reject an unexpected subkind; Span requires its
subkind. Malformed and wrong-kind IDs do not restore and do not grant authority.

A valid supplied trace remains necessary for observability restoration. With
no valid trace, base observability is retained even if optional IDs are supplied.
With a valid trace, an absent/invalid span yields no restored span; an absent/
invalid correlation retains the base correlation. Existing saga and calltree
policy, canonical/legacy key selection and Request argument precedence over
properties remain unchanged. Job/Task/Action restoration keeps its existing
policy and reflects the chosen complete observability values in minimal job
trace metadata.

## Final current-context binding

Security, runtime profile/formatting and all context restoration precede the
final ingress UnitOfWork binding. The returned ExecutionContext is the same
object as its current `unitOfWork.executionContext`; security, clock, operational
IDs, job metadata and runtime formatting therefore correspond. The existing
RuntimeContext lazy context/interpreter link remains authoritative. Fixed-user
formatting retains its existing final rebinding when that formatting changes.
There is no additional rebinding after execute, capture or commit and no new
UnitOfWork lifecycle, writer, commit or replay behavior.

Authentication providers, Fixed/Authenticated/ControlledTest profiles, roles,
capabilities and authentication provenance retain their existing authority.
Valid operational IDs supply neither authentication nor capabilities. A client
`cncf.security.authenticated` flag remains an explicit ControlledTest fact only;
ordinary ingress cannot turn it into provider authentication or a system grant.

## Executable correspondence

- [ObservabilityIdentitySpec](../../src/test/scala/org/goldenport/cncf/context/ObservabilityIdentitySpec.scala)
  fixes independent canonical parts/strings, default construction, full-value
  equality/hash and a total reducing construction law with at least 100 successes
  and zero discards.
- [IngressContextCorrespondenceSpec](../../src/test/scala/org/goldenport/cncf/security/IngressContextCorrespondenceSpec.scala)
  covers every ordinary resolver entry, a fresh Global/manual profile, exact
  current-context and security/job correspondence, precedence, partial/invalid
  fallback, authority rejection and the total full-ID restoration law.
- Existing [IngressSecurityResolverSpec](../../src/test/scala/org/goldenport/cncf/security/IngressSecurityResolverSpec.scala),
  [ExecutionContextSpec](../../src/test/scala/org/goldenport/cncf/context/ExecutionContextSpec.scala)
  and [ExecutionProfileSpec](../../src/test/scala/org/goldenport/cncf/context/ExecutionProfileSpec.scala)
  retain provider, profile, clock and UnitOfWork regression contracts.
- [Ordinary Display mutation](display-mutation-service.md) composes this common
  boundary through actual normal HTTP/MCP ingress and canonical persistence.
