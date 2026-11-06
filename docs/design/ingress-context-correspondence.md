# Operational identity and ingress context design

Document role: selected common ingress responsibility decisions.
The [specification](../spec/ingress-context-correspondence.md) owns observable
behavior; [ordinary Display mutation](display-mutation-service.md) consumes it.

## Existing identity ownership

Core `UniversalId` owns canonical parts, generation, validation, parsing and
full-value equality/hash. Trace/Span/Correlation specialize only their existing
kind, labels and Span subkind. Appended optional timestamp/entropy inputs follow
the existing Job/Task/Action explicit-generation pattern, and explicit
equals/hashCode overrides delegate to core so case-class field comparison
cannot replace operational identity. Existing short source construction keeps
its defaults; binary and extractor/product arity are not promised unchanged.

Ingress consumes core `parseParts` and passes all parsed generation inputs into
these existing constructors. Shape guards enforce no Trace/Correlation subkind
and the required Span subkind. Parsing remains private ingress restoration;
there is no competing public parser, mutable parsed-value override, reflective
construction, format normalization or upstream core/model change.

## Existing security and runtime ownership

IngressSecurityResolver retains provider/local subject/profile selection,
capability checks, provenance and all existing restoration precedence. It first
establishes security, production-runtime assumptions, formatting and restored
observability/job/debug context. Each ordinary resolver path then calls the
existing runtime UnitOfWork binding helper on that final value. Fixed profile
formatting keeps its established additional binding only when its final
formatting changes.

RuntimeContext.withUnitOfWorkContext supplies the canonical lazy link between
that final ExecutionContext, current UnitOfWork and interpreter. ExecutionContext
continues to compose immutable contexts; UnitOfWork owns its existing active
state and enclosing lifecycle. Neither gains a new API or lifecycle rule.
Moving the ordinary binding after restoration prevents an earlier context from
remaining captured by the UnitOfWork while the caller receives a later copy.
No rebinding is introduced after operation execution or capture.

## Original credential-transport provenance

Http4s owns observation of physical Authorization, Bearer scheme, session header
and known session-cookie presence before normalization. It admits only immutable
boolean facts against the actual physical request, retaining them across its
canonical-route and revision-transport copies. No synthetic public header,
client attribute, credential value or ambient ThreadLocal carries these facts.

The existing SecurityAuthenticationProvenance identity/weak-reference mechanism
retains an optional transport value independently of optional authentication
origin. Its normal inheritance keeps both; a dedicated transport-only handoff
never grants an authentication origin. Subsystem supplies that handoff from its
retained physical HttpRequest to freshly resolved security before the ordinary
operation executes. Resolver calls with an admitted base security context retain
its transport facts without trusting request attributes. Static/Web ingress uses
the same physical observer and final resolved context.

SecurityContext exposes a typed read method and companion value, preserving its
existing constructor/copy/extractor products and those of SessionContext. This
keeps precompiled component consumers on the ordinary API path. Authentication
providers remain authoritative for identity and sessions; transport observation
is evidence of input shape only. The unchanged final UnitOfWork holds the same
SecurityContext object and therefore observes the same admitted transport facts.

## Explicit development composition

Correspondence specifications create fresh runtime datastore/entity-store spaces
and a fresh explicit GlobalRuntimeContext with a manually resolved controlled
execution profile. They do not mutate GlobalRuntimeContext.current. ID and
principal strings are predeclared input facts; expected canonical values do not
come from operation responses. The resolver specs perform no real writes or
capture/commit simulation.

The Display fixture uses the same existing composition plus normal Subsystem
admission and component bootstrap. Its unbound component datastore supplies the
single fallback provider; managed SQL binding and its native independent state
oracle remain authoritative. Explicit signed-in owner facts belong to the
ControlledTest profile, and false authentication must reject that same owner.
They never synthesize System, ServiceInternal or provider provenance.

## Scope limits

Full operational-ID restoration does not change Job/Task/Action parsing, storage
NULL/Option semantics, provider policy or canonical formats. Existing absent/
partial observability fallback and saga/calltree behavior remain. The paired
[identity](../../src/test/scala/org/goldenport/cncf/context/ObservabilityIdentitySpec.scala)
and [ingress](../../src/test/scala/org/goldenport/cncf/security/IngressContextCorrespondenceSpec.scala)
specifications document the direct boundary; existing security/profile specs
and actual Display adapters document its consumers.
