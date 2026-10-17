# Component Usage Capability Package Contract

Status: authored main integration pending validation; historical reference
CAP-78-PKG01 Step acceptance retained; Phase closure pending
Adopted: 2026-10-15 (supersedes the Application-owned package proposal)
Owner: CNCF Phase 78 / CAP-78-PKG01
Producer: Cozy Phase 64 / CAP-64-PKG01
Consumer: Textus CBD Support Phase 11 / CAP-11-PKG01

Integration status (2026-10-17): this selected existing contract and its
implementation/specifications are authored in the admitted main checkout for
sm-workflow PHASE-6 / S6-01D35. Normal focused validation, actual consumer
command use, and independent Step acceptance remain pending. Step acceptance
claims below retain their historical reference-implementation attribution and
do not accept the current integration or Phase 78 as a whole.

## Meaning and authority

The Component under review owns its metadata and its CAR/generated Directory.
`ComponentUsageCapability` is its inductive usage model; `ComponentCapability`
is its separate deductive provided/required contract model. The family distinction
is semantic, not two different package owners. Application/Domain denote the
model/tier distinction and introduce no Application-owned package here.
Cozy `docs/spec/component-usage-capability.md` owns Usage model/source/wire meaning.
The [Component contract](component-capability.md) retains the existing Component
Capability ABI, native admission and discovery. Usage records do not become
Component ABI lifecycle data or executable contracts by sharing a package.

CNCF owns the common Directory/CAR metadata acquisition route, not the Usage
semantic interpreter. It must not depend on Cozy/CBD implementations or duplicate
their semantic checks. The review metadata set is larger than these two families;
this change adds no general metadata registry or new unrelated metadata format.

## Canonical Directory/CAR layout

```text
<component-package-root>/              # same relative paths inside CAR
  component-descriptor.json
  config/default.conf                 # when the Component supplies config
  meta/
    component-capability/             # existing deductive contract family
      index.json
      <indexed-component-projection>.json
    component-usage-capability/       # optional inductive usage family
      index.json
      usage.json
```

The Usage index is exactly:

```json
{"schemaVersion":1,"projections":["usage.json"]}
```

`usage.json` carries `cozy.component-usage-capability.v1`. Its selected Component
coordinate/package context and explicit Usage owners describe this Component.
The producer partitions by explicit Component ownership; it does not package a
whole-system model in each dependency. Cross-Component relationships remain
explicit references. No Application owner, `applicationPackage`, arbitrary output
filename, or `meta/application-capability/` normal path is defined.

Directory is a first-class development input. Acquiring metadata from an already
generated image requires neither a CAR, runtime JAR packaging, SBT, shell nor
local publication. Component/native discovery retains its existing independent
native-provider/target requirements. A real Component may have Usage metadata
without Component Capability resources; no fake contract or native target is
required just to acquire that metadata. The canonical descriptor identifies the
Component. Producing a distributable CAR still follows ordinary CAR requirements.

## R15 Acquisition and failures

Use the existing `ComponentCapabilityPackageSource.PackageInput` and its
`Selected`/`Rejected`, `Location.Directory`/`Location.Car`, Component-coordinate
and package-coordinate domains. The public Scala transport API is:

```scala
ComponentUsageCapabilityPackageLoader.load(input: PackageInput): Either[Diagnostic, Option[ComponentUsageCapabilityPackageLoader.Acquired]]
ComponentFactory.acquireUsageCapabilities(input: PackageInput): Either[Diagnostic, Option[ComponentUsageCapabilityPackageLoader.Acquired]]
```

Both the `ComponentFactory` companion and instance expose the matching facade,
delegating directly to the loader. No runtime repository, collaborator, bootstrap,
`ComponentCapabilityMetadataProvider`, native target or CML parser is required.
The final `Acquired` class is constructed only by the Usage loader; there is no
public constructor, case-class `apply` or `copy` conferring accepted status.

| Acquired public immutable field | Retained evidence |
| --- | --- |
| `input: PackageInput.Selected` | Exact selected input, including physical location and independent coordinates. |
| `descriptor: ComponentDescriptor` | Original canonical schema-3 descriptor with matching Component identity/release. |
| `resourcePath: String` | Exactly `meta/component-usage-capability/usage.json`. |
| `content: String` | Original strictly decoded UTF-8 text, with whitespace and Unicode retained. |

`Right(Some(acquired))` accepts transport only. CNCF MUST NOT parse the Usage
JSON envelope, model identity, owner, mapping, source header, producer version
or ABI, or compare Usage envelope/package semantics. CBD interprets these Usage
contracts and keeps the original Component discovery route. Even non-JSON text
can pass UTF-8 transport; this does not establish Usage semantic acceptance.

`Right(None)` means only that the optional family has no entries at the selected
canonical package location; it reports Usage unavailable, not an empty model.
An absent Directory `meta` or Usage-family directory, or a CAR without Usage
entries, is ordinary optional absence. The shared read retains canonical
descriptor evidence in this case; the loader MUST validate selected Component
identity/release before returning `None`, without rereading the package.
Missing root, absent/invalid descriptor or mismatched Component remain failures.

A present family without index or payload, malformed/unsupported index,
unreadable bytes, unsafe aliases, symlinks or ambiguous entries returns
`Left(Diagnostic)`, never `None`. Directory absence MUST use actual no-follow
path attributes and only a missing-path observation; access errors and other
IO exceptions MUST NOT imply absence. Existing classification remains:
ordinary IO/missing selected resources are `Unavailable`, access denied is
`Unauthorized`, malformed structure/UTF-8 is `Malformed`, duplicate entries
are `Ambiguous`, and descriptor mismatch is `Incompatible`. Produced diagnostics
retain their pointer plus known selected scope/Component; an explicitly supplied
`Rejected` diagnostic is returned by identity without wrapping.

The shared strict index decoder requires exactly the two keys and schema-1
integer shown above. Its original unknown/missing/duplicate-key/type/filename/
count/inventory rules run before requiring exactly `projections=["usage.json"]`.
A future integer schema remains `Unsupported` at `/index/schemaVersion`;
empty or different Usage selection is `Malformed` at `/index/projections`.
There is no arbitrary filename, fallback or loose-JSON search. Ordinary Component
admission does not require Usage metadata, and its required index and explicit
empty-index behavior are unchanged. Old Application types/paths/ABIs receive
no alias, dual reader or converter.

The archive/directory reader and index decoder use exactly two fixed internal
family selections, Component and Usage; there is no public/general metadata
registry or second ZIP/filesystem reader. Reuse current resource limits (1 MiB
index, 16 MiB resource, 256 entries, 64 MiB aggregate), strict UTF-8 REPORT,
stream closure, inventory, alias/symlink and duplicate-entry handling. Shared
selection validation retains the original Component error pointers and failure
precedence, including Component acquisition before any native provider access.
No hash-based management, additional safety ledger, replay, broad scans, or
expanding blacklist of normal Modeler outputs is introduced. CNCF transport
does not activate Components, execute procedures or parse CML.

The finite admission scope uses `ModelKind.ComponentUsageCapability` with tag
`COMPONENT_USAGE_CAPABILITY` in place of the removed Application Capability
kind. Component projection `kind=COMPONENT`, PROVIDED/REQUIRED, ModelRole,
version compatibility and unrelated information Application APIs stay unchanged.

## Accepted executable specifications

The [Usage loader suite](../../src/test/scala/org/goldenport/cncf/component/capability/ComponentUsageCapabilityPackageLoaderSpec.scala)
uses the existing lower-layer Directory/CAR fixture helpers. It adds focused
boundary cases rather than duplicating the full resource abnormal-state matrix.
The [Factory suite](../../src/test/scala/org/goldenport/cncf/component/capability/ComponentFactoryCapabilitySpec.scala)
also supplies an external-namespace public client and poison runtime inputs.

| Case | Accepted observable behavior |
| --- | --- |
| CUP01 | Directory/CAR retain identical original opaque text, exact distinct selected inputs and resource path without writes. |
| CUP02 | Component and Usage families coexist with ordinary config in both representations. |
| CUP03 | Usage-only canonical Component requires no fake Component projection or native target. |
| CUP04 | Descriptor-only and Component-only Directory/CAR distinguish genuine optional absence. |
| CUP05 | Absent family still validates selected descriptor/identity; invalid or missing inputs remain typed failures. |
| CUP06 | Present missing index, wrong fixed selection and missing payload retain actual reader diagnostics in Directory/CAR. |
| CUP07 | Explicit original package rejection survives by identity. |
| CUP08 | Empty Usage selection is Malformed; future integer schema is Unsupported. |
| CPL17 | Existing Component acquisition remains independent of coexisting opaque Usage and config. |
| CPF07 | External client consumes the exact public acquired type through companion API despite poison CAR runtime payloads. |
| CPF08 | Instance acquisition succeeds with poison runtime repository/collaborators and no provider. |

Focused Step validation, the complete Capability accumulator and independent
protected CAP-78-PKG01 Step review are complete. The existing CPL16 property
retains its active minimum 100 successes and zero discards.

## Step acceptance criteria

1. Normal Cozy generation -> the Component's Directory -> public CNCF acquisition
   -> CBD Usage and Component models, without an external Application JSON input.
2. Normal CAR packaging of the same image -> equivalent semantic/query results;
   acquisition locations legitimately differ. Config and both families coexist.
3. Explicit multi-Component ownership is preserved; no indiscriminate Usage copy
   and no merging of inductive observations with deductive contracts.
4. Focused optional-absence and actual acquisition-failure wiring; reuse lower-layer
   tests. Delete old typed bridge symbols so stale callers fail compilation.

The old Application-file proof and earlier package proposal are historical only.
No backward-compatible Application format is part of this pre-release change.
