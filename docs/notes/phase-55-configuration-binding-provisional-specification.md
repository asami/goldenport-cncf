# Phase 55 Configuration Binding Provisional Specification

Date: 2026-07-31

Status: provisional planning contract

This note records the implementation-facing Phase 55 contract before
failing-first specifications and implementation establish normative behavior.
GCF-01 froze the names and boundaries on 2026-08-02; this note remains a
non-normative implementation contract and does not override verified design or
implementation.

## Objective

Represent each effective configuration value as one typed binding that carries
its semantic parameter, selected target, provenance, and direct override
history. Keep one resolved binding authority and derive trace from it.

## GCF-02 Scenario SPI

GCF-02 adds only a non-authoritative executable-contract seam. The generic
library exposes a target-parametrized scenario request/report SPI whose sole
current outcome is `ConfigurationBindingScenarioReport.NotImplemented` carrying
the original scenario identifier. CNCF specializes that SPI with exactly four
request families: concrete target, external document, alias, and fixed-user
identity change. These requests are attributable to the frozen invariant but
do not decode, canonicalize, validate, resolve, project, or migrate anything.
Those effects remain owned by GCF-03 through GCF-07.

## Processing Model

```text
ConfigurationSource
  -> decode and validate once
  -> ConfigurationBindingCandidates
  -> resolve for selected runtime / Component context
  -> ConfigurationBindingCollection
  -> typed lookup / sanitized trace
```

## Provisional Types

```scala
enum CncfConfigurationTarget {
  case Global
  case ComponentClass(id: ComponentId)
  case SubsystemInstance(id: SubsystemInstanceId)
  case ComponentInstance(
    subsystem: SubsystemInstanceId,
    component: ComponentInstanceId
  )
}

final class SubsystemInstanceId private (
  val subsystem: String,
  val instance: String
)

final class CanonicalParameterId private (val value: String)

trait ConfigurationValueCodec[A] {
  def decode(value: ConfigurationValue): Consequence[A]
  def encode(value: A): Consequence[ConfigurationValue]
}

final class ConfigurationParameter[A] private (
  val id: CanonicalParameterId,
  val codec: ConfigurationValueCodec[A]
)

final class ConfigurationProvenance private (
  val origin: ConfigurationOrigin,
  val layer: String,
  val sourceIdentity: String,
  val evidence: Vector[String],
  val omittedEvidenceCount: Int
)

final class ConfigurationBinding[A, T] private (
  val parameter: ConfigurationParameter[A],
  val target: T,
  val value: A,
  val provenance: ConfigurationProvenance,
  val overridden: Option[ConfigurationBinding[A, T]]
)

final class ConfigurationBindingCandidates[T] private (
  val bindings: Vector[ConfigurationBindingCandidate[?, T]]
)

final class ConfigurationBindingCollection[T] private (
  val bindings: Map[CanonicalParameterId, ConfigurationBinding[?, T]]
)
```

`ConfigurationBindingCandidate[A, T]` is separate from the effective
`ConfigurationBinding[A, T]` and never exposes `overridden`. The latter alone
records direct effective-value history.

GCF-03 implements these core types through private constructors and
`Consequence`-returning factories. `CanonicalParameterId` accepts the generic
dotted lowercase grammar only; String, Boolean, and BigDecimal codecs are
strict and never coerce raw `ConfigurationValue`s. Provenance accepts at most
16 stable-order evidence entries, bounds each retained entry to 256 UTF-16 code
units without splitting a surrogate pair, and records omitted entry count. This
is construction-time evidence bounding, separate from the GCF-06 diagnostic
projection contract.

Both generic binding collection companions provide typed canonical `empty`
values and a zero-argument `apply()` for the repository Collection idiom.

## GCF-04 Source Snapshots and Candidate Construction

GCF-04 adds an occurrence-preserving `ConfigurationDocument`: object members
remain ordered fields rather than a `Map`, so duplicate external spellings are
still visible to structural validation. A runtime-owned
`ConfigurationSourceAdmission` supplies origin, layer, physical identity,
rank, collision domain, and one loader; capture invokes that loader once and
assigns its stable ordinal from admission order. Neither a physical source nor
a document supplies rank or ordinal.

`ConfigurationBindingCandidateConstructor` receives exact typed parameter
witnesses, raw `ConfigurationValue`s, concrete targets, and source snapshots.
It strictly decodes through the witness codec, constructs bounded provenance,
and rejects a repeated `(collision domain, canonical parameter, target)`
before resolution. It does not choose a winner, create override history, or
mutate a trace.

The CNCF boundary maps consolidated and admitted split documents to the four
existing concrete targets. Its request-scoped schema maps canonical and alias
external spellings to an exact parameter witness; the original spelling and
typed document path remain provenance. `ExternalDocument` scenarios execute
the actual decoder and report either candidates or its structured failure.
This is not a production Textus catalog, target selection, fixed-user
behavior, or resolved-binding authority; those remain GCF-05 through GCF-07.

`SubsystemInstanceId` NFC-normalizes labels and admits only a leading Unicode
letter followed by Unicode letters, digits, `_`, or `-`; `/`, `.`, control
characters, and whitespace are not identity syntax. ComponentInstance target
construction admits only the existing canonical component-label grammar
`[A-Za-z][A-Za-z0-9_]*`, so two raw spellings cannot normalize to one target
identity after admission.

## Phase 53 Fixed-User Intake

Phase 55 receives stable fixed-user identity change diagnosis,
explicit-migration-or-isolation behavior, fixed-user formatting, and
secret-safe derived diagnostics. A fixed UserId is a typed profile value, not a
new `User` configuration qualifier. This intake preserves Phase 53 HOME
admission/multi-user exclusion and never permits silent data reuse or migration.

## Binding Invariants

- A parameter definition owns one canonical identity and one admitted value
  codec/type.
- A binding's value must conform to its parameter.
- A binding has one validated semantic target.
- A binding always has provenance.
- An effective binding may reference only the directly overridden effective
  binding.
- Winner and overridden binding have the same canonical parameter identity and
  admitted value type.
- Override chains are immutable and acyclic.
- Invalid, rejected, duplicate, or context-ineligible candidates do not become
  overridden winners.
- Confidential values may exist inside authorized runtime bindings but all
  trace and diagnostic projections redact them.

## Candidate and Resolved Collection Invariants

- Candidates may contain several sources and targets for one parameter.
- A resolved collection contains at most one effective binding per canonical
  parameter identity.
- Typed lookup accepts `ConfigurationParameter[A]` and returns only a binding
  or value of `A`.
- Callers do not supply or reconstruct the winning target to perform lookup.
- Different selected Subsystem instances and Component instances resolve
  independently from one immutable candidate collection.
- The resolved collection, not a trace map, is the effective-value authority.

## Provenance

Provenance must explain at least:

- origin kind;
- Textus/CNCF or other admitted layer;
- physical source identity;
- original external key or typed-document field path;
- Global, ComponentClass, SubsystemInstance, or ComponentInstance target;
- runtime-assigned source rank and stable ordinal;
- alias spelling before canonicalization;
- bounded contract/default evidence; and
- confidentiality/redaction classification.

Source order is runtime-owned. A physical source cannot raise its rank or
choose an ordinal.

## Resolution

For one parameter and a selected Component instance with its Component class
and containing Subsystem instance:

1. admit sources and load each exactly once;
2. canonicalize aliases and decode typed bindings;
3. reject malformed definitions and conflicting definitions inside one source
   or for the same target across equivalent documents in one layer;
4. retain only Global and targets matching the selected Component class,
   Subsystem instance, and Component instance;
5. inside one source, apply target specificity from Global through
   ComponentClass and SubsystemInstance to the exact ComponentInstance;
6. fold source winners from low to high precedence;
7. attach the previous effective winner as the new winner's direct
   `overridden` binding; and
8. publish the final winner under its canonical parameter identity.

Source precedence dominates semantic specificity across sources. A
higher-precedence Global binding may override a lower-precedence
ComponentClass, SubsystemInstance, or ComponentInstance binding. Target
specificity applies only within the same source.

### GCF-05 Implementation Boundary

The generic resolver receives only an immutable ordered context of eligible
targets, from least to most specific. It compares target equality and source
rank/ordinal only; it neither knows nor infers CNCF target forms. It emits one
winner per canonical parameter and records only source winners in the direct
override chain. Candidates outside the supplied context are excluded, while
GCF-04 structural rejections remain separate diagnostics.

The CNCF adapter constructs either the Global-only context or exactly this
chain for a selected deployment identity:

```text
Global -> ComponentClass -> SubsystemInstance -> qualified ComponentInstance
```

The ComponentInstance is always qualified by its containing
`SubsystemInstanceId`; contexts for distinct resident Subsystems resolve from
the same immutable candidate collection without sharing a winner.

### Verified GCF-05 Evidence (2026-08-03)

GCF-05 implements this boundary with a generic ordered-context resolver and a
CNCF context adapter. The resolver rejects null, empty, duplicate, or
null-containing target contexts; selects the uniquely most-specific eligible
target inside each physical `(sourceRank, sourceOrdinal)` source; then folds
only those source winners from lower to higher `(sourceRank, sourceOrdinal)`.
The resulting direct `overridden` chain excludes same-source losers and
context-ineligible candidates. A same-source specificity tie or a canonical-id
group containing different parameter witnesses is a structured configuration
failure. Final generic focused evidence was 11 succeeded and 0 failed at
serial invocation `7884-20260802T155302Z`, with `Test/compile` passing at
`8391-20260802T155356Z`; the independent review and two focused re-reviews
are clean. This remains a provisional implementation record, not a normative
design or specification promotion.

## External Boundaries

Files, typed documents, environment variables, arguments, launchers, and
serialized diagnostics remain String boundaries. They use explicit reversible
codecs to enter or leave the typed model.

An absent external qualifier does not intrinsically mean Global. The owning
document location supplies a target-selection context, and binding
construction must materialize one concrete `CncfConfigurationTarget`.
`Unqualified` is syntax requiring contextual target inference, not a semantic
target.

Phase 55 admits two equivalent physical organization styles for normal Textus
HOME configuration:

1. all configuration may be written in the consolidated
   `~/.textus/config.yaml`; and
2. the same target-oriented configuration may be split into the reserved
   `~/.textus/components/*` and `~/.textus/subsystems/*` trees.

The consolidated document mirrors this target structure:

```yaml
global:
  config: {}
components:
  <component-id>:
    config: {}
subsystems:
  <subsystem-id>:
    instances:
      <subsystem-instance>:
        config: {}
        components:
          <component-id>:
            instances:
              <component-instance>:
                config: {}
```

The canonical split paths are:

```text
~/.textus/components/<component-id>/config.yaml
~/.textus/subsystems/<subsystem-id>/instances/<subsystem-instance>/config.yaml
~/.textus/subsystems/<subsystem-id>/instances/<subsystem-instance>/
  components/<component-id>/instances/<component-instance>/config.yaml
```

The top-level `components` tree supplies ComponentClass configuration. A
`components` tree nested under one Subsystem instance supplies
ComponentInstance configuration. The full ComponentInstance target therefore
contains both `SubsystemInstanceId` and the existing `ComponentInstanceId`.

An explicit Subsystem and an implicit Component Subsystem use the same
SubsystemInstance and nested ComponentInstance model. The implicit form is not
a separate target kind: CNCF first projects its stable Subsystem identity and
instance name, then addresses it through
`CncfConfigurationTarget.SubsystemInstance`. When no named instance is supplied,
the runtime identity is `default`.

The initial canonical file syntax keeps `instances/default` explicit. A
shorter default-instance spelling may be considered later as syntax sugar, but
it must normalize to the same instance identity and cannot form another
authority.

Both physical forms decode into the same canonical
`(parameter, CncfConfigurationTarget)` candidates. File layout does not create a
second value authority. If the consolidated and split forms define the same
canonical parameter for the same target in the same admitted layer, the input
is a structural duplicate/conflict rather than an implicit override. Explicit
overrides use the admitted source/layer precedence and remain visible in the
binding chain.

String convenience APIs may exist temporarily for migration or permanently as
explicit codecs at external boundaries. They must not form a second internal
configuration authority.

### GCF-08A Canonical Binding-String Codec (2026-08-03)

The generic boundary owns only this outer grammar:

```text
<canonical-id>
@<qualifier>:<canonical-id>
```

`<canonical-id>` is the existing generic `CanonicalParameterId`; a bare key
uses the target supplied by the qualifier codec's `None` value. CNCF assigns
that value to Global and owns the only four qualifier forms:

```text
c/<pct(component)>
s/<pct(subsystem)>/<pct(instance)>
i/<pct(subsystem)>/<pct(subsystem-instance)>/<pct(component)>/<pct(component-instance)>
```

Each segment is canonical UTF-8: `[A-Za-z0-9_-]` bytes remain literal and every
other byte is `%HH` with uppercase hexadecimal. Decode rejects raw non-ASCII
or reserved syntax, lowercase hex, percent-encoded unreserved bytes, malformed
or non-UTF-8 byte sequences, wrong tags/arity, invalid target identities, and
non-NFC Subsystem labels. Re-encoding is therefore the unique spelling.

CNCF admits a decoded reference only when its parameter identity is a catalog
canonical spelling (never a decode-only alias) and its target kind is allowed
by that definition. The codec neither decodes values nor selects/resolves a
binding. Environment, argument, file/launcher adapter, diagnostic serializer,
and consumer migration remain later GCF-08/GCF-09 work.

### GCF-08B Environment Binding-Name Codec (2026-08-03)

The CNCF-only name codec uses `TEXTUS_BINDING_` and one marker: `G`, `C`, `S`,
or `I`. Parameter bytes are not normalized: canonical letters are uppercase,
digits remain literal, `.` is `_D`, and `-` is `_H`. Each target identity field
is its canonical UTF-8 byte sequence rendered as uppercase hexadecimal. The
fixed target field arity makes the trailing parameter token unambiguous even
though it contains underscores.

Decode rejects alternate prefixes, lowercase/invalid tokens or hex, malformed
UTF-8, non-NFC subsystem identities, invalid component labels, aliases,
unknown canonical ids, and disallowed catalog target kinds. This codec maps a
name to/from a typed reference only: it never reads `sys.env`, accepts a value,
assigns environment provenance, creates candidates, resolves bindings, or
changes runtime/launcher behavior.

### GCF-08C Command-Argument Binding Codec (2026-08-03)

The CNCF-only atomic argv envelope is:

```text
--textus.binding=<canonical-binding-reference>=<raw-value>
```

The reference is decoded and admitted only by the GCF-08A codec. The argument
codec splits at the first `=` after its fixed prefix; the rest is an opaque raw
value, including empty text and further `=` characters. It never trims,
normalizes, value-decodes, logs, redacts, or otherwise interprets that text.
It does not scan argv collections, alter existing argument source ingestion,
assign provenance, create a candidate, resolve a binding, or forward launchers.

### GCF-08D Command-Argument Collection Admission (2026-08-03)

The collection adapter inspects only tokens before `--`. It delegates each
atomic GCF-08C binding option, preserves admitted assignment encounter order,
and rejects any duplicate canonical `(parameterId, target)` pair without a
last-write-wins map. The sentinel and every later token, including malformed
binding-looking text, are residual command-domain text and are never inspected.

This remains an argv partition only. It does not strip or mutate runtime argv,
construct a source/provenance/candidate, value-decode, resolve, or forward a
launcher.

### GCF-08E Argument Candidate Bridge (2026-08-03)

The bridge accepts only runtime-created `ConfigurationSourceAdmission` values
whose loaded value is an admitted GCF-08D assignment vector. Each source loads
once per bridge decode, then exact catalog definitions construct typed candidate
inputs from `StringValue` raw values. The generic constructor retains source
rank, ordinal, identity, layer, collision domain, source type, confidentiality,
and duplicate rejection. Any downstream structured failure is normalized to a
fixed configuration failure so opaque argument values cannot appear in output.

The bridge does not discover, activate, resolve, trace, or mutate runtime
arguments and does not contact environment, files, or launchers.

### GCF-08F Runtime argv Admission (2026-08-03)

`CncfRuntime.bootstrap` applies GCF-08D exactly once after its source/test
normalization and before compatibility configuration, front, invocation, and
repository parsing. Only pre-sentinel envelopes leave the residual argv.
Malformed pre-sentinel envelopes fail as a fixed structured argument error;
the sentinel and every later token are preserved without inspection.

The final runtime snapshot contains one explicit Arguments source. Test
descriptor argument defaults are merged below explicit CLI keys, so ordinary
existing CLI precedence is unchanged. A binding envelope is never represented
as legacy `textus.binding` configuration. Instead, its typed candidate input
is appended to the existing Arguments source batch in
`CncfRuntimeConfigurationProjection`. Consequently, ordinary argv document
inputs and typed envelope inputs use exactly the same source identity, rank,
ordinal, collision domain, and `arguments` source type. Candidate construction
therefore rejects a duplicate canonical parameter/target in one argv source
without an alternate precedence path.

This is the sole runtime activation for the argv adapter. The runtime candidate
set continues through execution-profile selection and final Subsystem binding
admission; no environment, file, launcher, or diagnostic adapter is activated
by this slice.

### GCF-08G Runtime Environment Binding Admission (2026-08-03)

`CncfRuntime.bootstrap` partitions its supplied environment before it creates
either initial or final compatibility sources. Only a canonical
`TEXTUS_BINDING_...` name admitted by GCF-08B becomes a typed `(reference,
opaque raw value)` assignment. The adapter neither trims nor logs the value;
the existing catalog candidate construction remains the sole typed-value
decoder. A malformed, non-canonical, aliased, or unadmitted binding name fails
before legacy environment construction through a fixed structured failure that
does not render the input name or value. The `CncfMain` CLI entry point
consumes that `Consequence` boundary and renders the structured failure rather
than converting it to an untyped bootstrap exception.

Admitted binding variables are removed from the map supplied to the legacy
`ConfigurationSource.Env`; ordinary environment variables remain unchanged.
The typed inputs attach to that one retained Environment source batch, keeping
its origin, rank, ordinal, identity, collision domain, and `environment` source
type. Consequently an ordinary and typed environment value for the same
canonical `(parameter, target)` follows the existing same-source collision
rule; different targets remain distinct. The assignments participate in both
Global bootstrap and final Subsystem resolution, but no launcher parses their
names or receives parameter semantics. File syntax, external launcher
transport, and diagnostic serialization remain later boundary work.

### GCF-08H Consolidated File Binding Admission (2026-08-03)

A retained `ConfigurationSource.File` snapshot whose root contains `global`,
`components`, or `subsystems` is interpreted as the documented consolidated
configuration document. Its immutable `ConfigurationValue.ObjectValue` tree is projected to
`ConfigurationDocument` once and decoded through the existing catalog
hierarchy. A flat source continues to use its explicit runtime location; a
consolidated source may therefore contribute Global and several concrete
Subsystem/Component targets from the same physical source without a second
file read or a flattened String map becoming typed authority. Environment,
argument, and resource snapshots always retain their existing flat boundary,
even when their values happen to use one of those root names.

The candidate batch retains the existing file source's rank, ordinal,
identity, layer, collision domain, and `file` type. Resolution selects the
binding for the requested context from that one candidate graph. This slice
does not add split-document discovery, alternate file syntax, launcher
transport, diagnostic serialization, or same-file duplicate-member detection:
the current source snapshot intentionally contains a `Map`, after parser-level
duplicate keys have already been collapsed. Preserving raw duplicate members is
separate generic source-snapshot work and must not be faked through a re-read.

### GCF-08I Canonical Split-File Binding Admission (2026-08-03)

Runtime source discovery admits only these canonical files beneath a `.textus`
configuration directory, in deterministic path order:

```text
components/<component-id>/config.yaml
subsystems/<subsystem-id>/instances/<instance>/config.yaml
subsystems/<subsystem-id>/instances/<instance>/components/<component-id>/instances/<component-instance>/config.yaml
```

Each recognized path supplies its typed ComponentClass, SubsystemInstance, or
ComponentInstance document location. Its content is decoded as a flat,
path-bound document, even if a source happens to contain `global`, `components`,
or `subsystems`; hierarchy-shaped content cannot retarget a reserved split
file. The canonical `.textus/config.yaml` consolidated document and canonical
split files at the same origin/rank share one collision domain, so a duplicate
canonical `(parameter,target)` fails structurally. Other retained files,
including `.cncf/config.yaml`, retain their source identity and normal
Textus-to-CNCF precedence.

This slice does not add alternate split names or extensions, arbitrary file
admission, non-file split semantics, launcher transport, diagnostic
serialization, reload behavior, or raw duplicate-member preservation.

### GCF-08J Opaque Launcher Binding-Envelope Transport (2026-08-03)

`cncf-launcher` and `textus-launcher` forward a
`--textus.binding=<envelope>` token as an opaque runtime token. Neither
launcher decodes, validates, canonicalizes, redacts, or assigns semantics to
the reference or value. Canonical-looking and noncanonical-looking envelopes,
empty values, and values containing additional `=` remain runtime data; CNCF
runtime admission remains the only receiver that assigns binding semantics.

Launcher-owned configuration and target/artifact activation retain their
existing behavior. Apart from those explicit additions and the established
`--` command-domain delimiter semantics, the forwarded runtime token order and
spelling are unchanged. This slice does not add launcher binding grammar,
diagnostic serialization, file behavior, or raw duplicate-member preservation.

### GCF-08K Serialized Binding-Diagnostic Boundary (2026-08-03)

`CncfConfigurationBindingDiagnosticCodec` accepts only an already-resolved
generic `ConfigurationBindingTrace[CncfConfigurationTarget]` and projects it
to the fixed compact JSON format `textus.configuration-binding-diagnostic.v1`.
It accepts no candidates, resolved collection, source snapshot, loader, or raw
runtime configuration, and it does not expose a decoder or CLI command.

Every explanation uses the existing canonical binding-string codec for its
reference. The JSON retains trace order, history omission count, and bounded
provenance limited to origin, layer, source identity, input path/spelling, rank,
and ordinal. It excludes raw evidence, source documents, candidate rejections,
and source type. Visible values use deterministic tagged JSON with sorted
object fields. Confidential current and historical values are represented only
by `{"state":"redacted"}`; their payload never enters output or failure text.

### GCF-08L Raw YAML Duplicate-Member Preservation (2026-08-03)

`ConfigurationSourceLoad` carries the legacy `Configuration` value with an
optional `ConfigurationDocument.Object` raw document. Standard YAML file
loading parses that document from the already-read source text using mapping
nodes, preserving every mapping-member occurrence and its encounter order;
the compatibility `Configuration` remains the established last-wins map.
YAML-native scalar tags reuse the established SnakeYAML scalar conversion, so
raw capture cannot change accepted values such as `yes` or `0x10`.
The default snapshot method for custom source implementations delegates to the
existing `load` method once, so resource, environment, argument, and custom
sources intentionally have no raw document unless they explicitly provide one.

Only a retained `ConfigurationSource.File` raw document is used by the CNCF
projection. Consolidated files pass it to the existing hierarchy decoder;
canonical split files are flattened in encounter order without collapsing
repeated paths. A repeated spelling that becomes the same canonical
`(parameter,target)` therefore reaches the existing candidate duplicate check
and fails structurally. CNCF does not reread a path, synthesize raw evidence
from a map, or broaden raw-document semantics to non-file sources.

The public resolver's snapshot method has an additive compatibility default:
existing resolver implementations continue to resolve normally and report an
empty source-snapshot vector unless they opt into physical snapshot retention.

## Trace

Trace is derived from:

```text
effective binding
  + effective provenance
  + overridden binding chain
```

It is not consulted to choose a value. No independent update path may allow
trace key, final value, provenance, or history to diverge from the effective
binding.

## GCF-06 Implementation Evidence (2026-08-03)

GCF-06 implements the diagnostic projection exclusively from
`ConfigurationBindingCollection`. The projection orders explanations by
canonical parameter identity, and a lookup accepts only the exact original
typed parameter witness. Each explanation carries the selected target,
visible-or-structurally-redacted value, provenance, and direct override chain
from the same effective binding graph.

The diagnostic view retains at most the newest 16 entries and records the
omitted count. Source identity retains at most 256 UTF-16 code units without
splitting a surrogate pair and records the truncation count. Confidential
effective and historical values are represented by structural `Redacted`
entries without trace-time value encoding. CNCF qualified targets pass through
the generic projection unchanged. This evidence records implementation state;
this note remains provisional and non-normative.

Focused generic validation passed with 27 succeeded and 0 failed; generic
`Test/compile` and development `publishLocal` passed. CNCF `Test/compile` and
the focused trace/resolver/Phase 55 contract suites passed with 8 succeeded, 0
failed, and 3 intentional pending. Independent review and focused re-review
are clean. Full suites remain reserved for the Phase 55 release gate.

## GCF-07A Catalog Evidence (2026-08-03)

GCF-07A adds a CNCF-owned closed catalog without placing namespace or
target-policy semantics in the generic core. The catalog contains one exact
`ConfigurationParameter[SubsystemUserMode]` witness for
`textus.subsystem.user-mode`. Only `standalone` and `multi-user` decode, and
only SubsystemInstance targets are admitted. Its three decode-only aliases
preserve their original spelling and document path in provenance; canonical and
  alias spellings collide structurally after canonicalization for one
  `(collisionDomain, canonical parameter, target)` identity.

Unknown `textus.*`/`cncf.*` spellings, obsolete Web selectors, malformed
user-mode values, and non-Subsystem targets fail structurally. This slice does
not adopt the catalog in RuntimeConfig, StandaloneUserProfile, Web execution,
ExecutionContext, or legacy consumers; those remain subsequent GCF-07 and
GCF-09 work. This note remains provisional and non-normative.

## GCF-07B Runtime Codec Adoption (2026-08-03)

GCF-07B makes the existing Subsystem user-mode runtime boundary decode its
canonical effective `ConfigurationValue` with the registered
`ConfigurationParameter[SubsystemUserMode]` witness. It deliberately retains
the existing `ResolvedConfiguration` value and `ConfigurationResolution` trace
as the Phase 53 authority for effective value, default behavior, and
provenance. A malformed or non-string present value therefore fails through
the catalog codec without deriving standalone.

This is not physical-source migration: it does not load `.textus` or `.cncf`
again, construct typed candidate batches from a flattened legacy result, or
change profile admission, Web selection, `ExecutionContext`, or Components.
Those operations require a later single-load source-projection boundary so they
cannot create a second provenance authority. This note remains provisional and
non-normative.

## GCF-07C Runtime Source Snapshot Projection (2026-08-03)

GCF-07C establishes the boundary that GCF-07B deliberately left open. The
runtime resolves its final source sequence once into an immutable
`ConfigurationResolutionSnapshot`; its `ResolvedConfiguration` remains the
compatibility projection for legacy consumers. The catalog projection receives
only the retained per-source values, never the flattened merged result, and
does not invoke a physical loader.

The runtime retains only the later occurrence of an identical physical file
identity, so project-root and cwd discovery cannot create duplicate candidates
for one source. It preserves distinct `.textus` and `.cncf` identities, with
the former as baseline and the latter as compatibility override. Nested,
intermediate, and flat representations of one spelling within an already
loaded configuration document become one source-local candidate. After the
Subsystem has a stable default instance identity, the typed binding is admitted
before user-mode evaluation; a late replacement fails structurally. Components
continue to receive no raw source, candidate, or trace authority. This note
remains provisional and non-normative.

## GCF-07D Runtime Catalog Collection Authority (2026-08-03)

The runtime resolves its retained source snapshot once into the closed CNCF
catalog collection for the stable Subsystem target. The Subsystem keeps that
collection private and uses only the original registered parameter witness for
lookup. If the collection is admitted but lacks a catalog key, the key is absent
in the authoritative snapshot; a later legacy compatibility value cannot regain
authority. Collection replacement and admission after first evaluation fail
structurally. This note remains provisional and non-normative.

## GCF-07E StandaloneUserProfile Catalog Projection (2026-08-03)

The four frozen fixed-user fields have CNCF-owned typed catalog witnesses:
normalized non-empty identity/display-name strings, a BCP 47 `Locale`, and an
IANA `ZoneId`.
Their candidate projection accepts only already-parsed HOME profile admissions,
preserves `.textus`/`.cncf` provenance and Global/Subsystem target scope, and
does not reread physical files. The ordinary runtime source schema remains
user-mode-only. This slice neither consumes the profile collection at runtime
nor decides fixed-identity migration or formatting behavior.

## GCF-07G Fixed-User Execution Adoption (2026-08-03)

The runtime derives the execution profile from retained runtime candidates,
admits HOME StandaloneUserProfile only for fixed execution, combines those
candidates with the retained runtime candidates, and admits the resulting final
collection exactly once. The Subsystem resolves identity as required and display
name, `Locale`, and `ZoneId` as optional fields from exact catalog witnesses;
changing identity through a conflicting binding history fails structurally.

Fixed ingress obtains principal identity and display name from that resolved
profile after local-subject authorization. Its locale and timezone replace
request-restored formatting, and the Unit of Work is rebound so outer and unit
of-work execution contexts observe identical formatting. Authenticated and
controlled profiles neither retain nor apply a fixed-user profile. This note
remains provisional and non-normative.

## GCF-07H Typed Web Execution Policy Adoption (2026-08-03)

The seven existing Web execution fields are SubsystemInstance-only typed catalog
witnesses: locale, timezone, date/date-time format policy, display override,
HTTP language negotiation, and ordered public capabilities. The final admitted
collection is authoritative for these values and for an admitted absence; the
legacy configuration reader remains a compatibility path only before runtime
admission.

Bootstrap projects already-loaded, otherwise-missing assembly descriptor
defaults once as weak Resource `assembly-descriptor` candidates and resolves
them with retained runtime and fixed-user profile candidates. It neither reloads
the descriptor nor exposes source or binding details to Components. Native
boolean configuration values and established string forms share the Boolean
codec. This note remains provisional and non-normative.

## GCF-07I Runtime Projection Boundary Closure (2026-08-03)

The final generic collection remains private to Subsystem. Runtime consumers
receive the value-only Web policy projection, the resolved user mode, and the
resolved fixed profile as appropriate; they receive no binding collection,
candidate, target, source, layer, or raw trace. `ExecutionContext` remains free
of generic binding authority. The pre-admission `ResolvedConfiguration`
compatibility path is deferred consumer migration work, not runtime authority.
This note remains provisional and non-normative.

## GCF-09H SystemNode Shutdown Configuration (2026-08-03)

`textus.system-node.shutdown.drain-timeout-millis` is the one canonical,
alias-free `Long` parameter for SystemNode shutdown draining. It is admitted
only at `SubsystemInstance` scope. Values must be integral milliseconds in the
closed interval 1..300000. Missing binding selects 30000 ms. Invalid,
non-numeric, zero, negative, and over-bound values fail structurally during
typed configuration admission; they never fall back to the default.

Effective precedence is a typed per-SystemNode override, then the resolved
SubsystemInstance binding, then the default. Runtime resolves the same
collection before creating a SystemNode and transfers that pre-created physical
pool owner through the Subsystem factory boundary. `Subsystem` does not read
the raw `ResolvedConfiguration` for this value. Runtime observation is limited
to the safe source kind (`Default`, `Resolved`, or `Override`) and the bounded
millisecond duration.

This preserves the Phase 54 ownership model: a Subsystem owns its logical
binding and lease, while its SystemNode owns pool reclamation and final close.
It does not widen the current one-Node/one-Subsystem deployment adapter into a
multi-resident runtime implementation.

## GCF-09I Startup Import Configuration (2026-08-04)

`textus.import.data.file` and `textus.import.entity.file` are optional
`String` parameters admitted only at `SubsystemInstance` scope. Each preserves
`textus.runtime.*`, `cncf.*`, and `cncf.runtime.*` as decode-only aliases;
canonical-plus-alias input for one collision domain, non-string values, and
all other target scopes fail during catalog admission.

The final admitted collection is the only runtime authority. `Subsystem`
projects it into the value-only `StartupImportConfiguration`; no raw
`ResolvedConfiguration`, binding, candidate, target, provenance, or alias
crosses into `StartupImport`. Source text is trimmed and blank becomes absent.
Entity seed import receives only an entity-collection resolver capability, so
the enclosing Subsystem cannot carry raw configuration authority across the
importer boundary.
Absent values retain the existing `data.d` and `entity.d` fallback; explicit
URLs, files, directories, bootstrap-relative paths, and deterministic
directory traversal remain unchanged. Runtime use before binding admission
fails structurally. This remains provisional and non-normative.

`application-mode` is intentionally unchanged: it remains presentation-only
vocabulary, while its removal is separate compatibility/CML work.

## GCF-09J Collaborator Repository Bootstrap Projection (2026-08-04)

`textus.collaborator.repositories` is an optional `Vector[String]` value
admitted only at Global scope. Canonical and established decode-only aliases
are `textus.collaborator.repositories`,
`textus.runtime.collaborator.repositories`, `cncf.collaborator.repositories`,
and `cncf.runtime.collaborator.repositories`. It accepts only string values;
comma is the only separator, outer whitespace is trimmed, empty elements are
dropped, and interior path whitespace remains data.

`RepositoryBootstrapPolicy` owns normalization, deduplication, and relative
resolution against its bootstrap directory. The runtime passes only resulting
`Path` values to collaborator discovery; it does not pass a raw configuration,
binding collection, alias, provenance, or policy object. Missing or blank input
uses `<bootstrap-directory>/collaborator.d`; explicit nonexistent paths yield
no repositories and do not revive the default. The legacy raw-configuration
factory remains compatibility-only. `application-mode` remains presentation-only
vocabulary; its removal is separate compatibility/CML work.

## GCF-09K Deprecated CNCF Configuration Stack Retirement (2026-08-04)

The obsolete CNCF-local `config.model`, `config.source`, `config.trace`,
`ConfigResolver`, `MergePolicy`, and `ResolvedConfig` stack is retired as one
closed component with its self-contained tests. It is not a compatibility
adapter for the current runtime: the authoritative path is the generic typed
configuration parameter, candidate, resolution, and trace model. This removal
does not revise key aliases, runtime admission, `application-mode`, generic
SPI ownership, or Phase 54 pool/Subsystem ownership.

## GCF-09L Runtime Operation and Web-Authorization Policy (2026-08-04)

GCF-09L replaces the runtime authorization consumers' direct
`RuntimeConfig.from(ResolvedConfiguration)` selection with one admitted,
value-only `RuntimeOperationSecurityPolicy`. Its seven
`SubsystemInstance`-only witnesses are `textus.operation-mode`,
`textus.web.develop.anonymous-admin`, `textus.web.demo-assist.enabled`,
`textus.web.production.admin.enabled`, and the `system.roles`,
`component.roles`, and `jobs.roles` production-admin keys. Each accepts its
established `textus.runtime.*`, `cncf.*`, and `cncf.runtime.*` spellings only
as decode aliases; canonical-plus-alias collisions fail during admission.

The policy preserves the current operation-mode names (including `prod` and
`dev` normalization), Boolean and role-list behavior, and defaults. Invalid
mode or Boolean input is a structured admission failure. An admitted absence
selects the existing default and must not consult a conflicting raw
`ResolvedConfiguration`. `Subsystem` exposes only the final policy, and
retains it atomically with final binding admission. Operation authorization plus
its HTTP consumers must fail structurally before binding admission rather than
reconstruct raw configuration authority.

This slice intentionally excludes `DescribeApplication.mode` and CML
`ApplicationMode` removal, which remain presentation-only compatibility work;
all other RuntimeConfig families, generic SPI ownership, launchers, and Phase
54 pool/lifecycle ownership are also outside its scope.

## GCF-09M Runtime Execution-Profile Configuration Projection (accepted 2026-08-04)

GCF-09M replaces the runtime bootstrap execution profile resolver's production
`ResolvedConfiguration` map scanning with one value-only
`RuntimeExecutionProfileConfiguration`. The closed SubsystemInstance catalog
will cover the existing profile/key, virtual-start/time, random/id/scheduler/
ordering, locale/timezone/charset, line-separator/math-context, i18n, and
environment allow/value families. Every canonical `textus.*` spelling retains
only its established `textus.runtime.*`, `cncf.*`, and `cncf.runtime.*`
decode-only aliases; canonical-plus-alias collisions and wrong scopes fail
during admission. Random seeds and configured environment values are
confidential and must not enter diagnostics.

The value object keeps typed optional inputs and existing defaults, never raw
configuration, bindings, aliases, targets, provenance, or an ambient process
environment. The resolver receives that object plus explicit activation and
ambient-environment input, preserving existing controlled/profile cross-field
failures. In particular, `textus.clock.virtual-start-at` remains a distinct
compatibility semantic rather than an alias of `textus.execution.time.start-at`.

Before a Subsystem exists, runtime will derive the descriptor-selected
Subsystem identity, candidates, and resolved collection once, project the
execution configuration, and carry the same result into final fixed-user
admission. A separate Global authority or raw fallback is forbidden. This
slice excludes `application-mode`/CML removal, Phase 54 topology and pools,
generic SPI changes, launchers, and datastore, observability, blob/resource,
MCP/tool, renderer, or other RuntimeConfig migration families.

The implemented preflight result is the only runtime-bootstrap authority for
the selected Subsystem execution profile: it contains the selected identity,
combined runtime/assembly candidates, and resolved collection. Runtime uses it
for SystemNode shutdown configuration, typed execution-profile resolution
before `GlobalRuntimeContext`, and final fixed-user admission. The final
Subsystem retains the same value-only projection. Focused catalog, projection,
and bootstrap integration validation is green; independent review remains the
acceptance gate. REVIEW_FIX also makes typed time/start conflicts and missing
or incompatible time values fail structurally before profile construction, and
uses the admitted `operation-mode=test` value to authorize controlled profile
activation and its associated Subsystem test execution. Focused re-review is
the remaining slice acceptance gate. Its first pass found only a formatting
detail and missing manual-without-start executable regression; both are now
corrected. Final focused re-review is clean and accepts GCF-09M.

## GCF-09N Runtime Process-Exit Policy Projection (review-fix validated; focused re-review pending 2026-08-04)

GCF-09N replaces the runtime process-exit path's direct
`ResolvedConfiguration` reads with one Global, value-only
`RuntimeProcessExitPolicy`. It covers exactly `textus.force-exit` and
`textus.no-exit`: Boolean values default false only when absent; established
`textus.runtime.*`, `cncf.*`, and `cncf.runtime.*` spellings remain decode-only
aliases; malformed Boolean input, wrong target, and canonical-plus-alias
collisions fail during admission.

The selected Global binding collection resolves once and projects both
`RepositoryBootstrapPolicy` and the exit policy. Direct `--force-exit` and
`--no-exit` remain external CLI adapter controls and can enable their policy
field without introducing another internal String-keyed authority. Existing
force-exit dominance when both values are true, and `noExit` treatment of a
nonzero result as `CliFailed`, remain unchanged. This slice excludes
`application-mode`/CML deletion, other front controls, launchers, generic SPI,
Phase 54 ownership, and unrelated RuntimeConfig families.

The review-fix preserves failures through `CncfRuntime.bootstrapC`: malformed
values, canonical-plus-alias collisions, and process-exit values in an
inadmissible split-file target remain structured configuration failures. Its
auto-component-archive continuation re-admits only the enriched repository
argument envelope while reusing the already resolved Global collection and its
two value policies. Serialized `Test/compile` and the catalog/policy/bootstrap
focused suites pass. The auto-archive regression observes and requires one
Global-policy projection, so a recursive bootstrap restart cannot pass only by
producing the same final repository policy. A fresh focused re-review remains
required.

## Ownership

- `simplemodeling-lib`: generic identities, typed parameters, bindings,
  candidates, resolver, provenance, generic catalog mechanism, codec
  abstractions, and trace projection.
- CNCF/Textus: parameter definitions, closed namespace policy, aliases,
  source admission, typed-document mapping, and runtime selection.
- launchers: exact external forwarding only.
- Components: typed resolved values and capabilities only.

## Deferred Generalization

The provisional contract does not admit:

- User or WebApplication semantic qualifiers;
- arbitrary multidimensional qualifier sets;
- a general heterogeneous object-graph merge language;
- permanent alias compatibility;
- Metadata Factory ComponentStyle contribution; or
- Component access to raw candidates, source paths, or trace authority.

## GCF-01 Frozen Decisions

- The public names in this note are final for Phase 55 implementation;
  heterogeneous storage remains private behind the exact parameter witness.
- `simplemodeling-lib` owns the generic target-parametrized core; CNCF owns
  the four concrete target cases, catalog, document mapping, and aliases.
- The canonical external namespace is `textus.*`; `textus.runtime.*`,
  `cncf.*`, and `cncf.runtime.*` decode only. Co-present canonical and alias
  spellings for the same source/target fail structurally. GCF-09 removes them.
- Runtime history is complete and immutable. The external projection contains
  at most the newest 16 entries, source identity has a 256-UTF-16-code-unit
  maximum without splitting a surrogate pair, a truncated-code-unit count is
  recorded, and confidential current/history values are redacted.
- Structured configuration failures are used for malformed input, aliases in
  conflict, duplicates, type/identity invalidity, and fixed-user identity
  changes that lack explicit migration or isolation.
- The admitted repositories are `simplemodeling-lib`, CNCF,
  `cncf-launcher`, `textus-launcher`, and `textus-art-scene`.
