# SAR Archetype and Capability Boundary Consideration

Date: 2026-10-20

## Trigger

While considering KnowledgeHub security, a specialized HTTPS-oriented system was introduced as a reference. Its notable security posture is to narrow the target use case and strip away facilities that are unnecessary for that use case. If shell, SSH, unnecessary filesystem operations, unnecessary network protocols, or legacy transport features do not exist, those facilities cannot become attack paths.

This prompted a comparison with CNCF's existing Zero Trust, Capability, Authorization, ExecutionContext, and Safety Sandbox direction.

## Observation: CNCF already covers much of the intent

CNCF is a general-purpose runtime, so removing generic runtime facilities is not an acceptable global strategy. However, CNCF already uses an opt-in capability model: a CAR/Operation that is not granted filesystem, process, network, datastore mutation, or another external-effect capability must not be able to use that facility through the CNCF execution model.

Therefore the specialized system's physical "feature does not exist" property has a close CNCF analogue:

```text
feature exists in generic CNCF runtime
  + capability is absent from this execution boundary
  = feature is semantically unavailable to this SAR/CAR/Operation
```

This is not identical to physically removing code, but it is the appropriate mechanism for a reusable enterprise runtime.

## Decision direction: Capability is also an upper bound

Capability should be used not only as a declaration/grant but also as an explicit upper bound at SAR and CAR boundaries.

```text
Effective Capability
  <= CAR Capability Boundary
  <= SAR Capability Boundary
  <= Runtime Available Capability
```

The relation is monotonic: a lower layer may narrow but must not enlarge an upper boundary.

This means that even when a bug or exploit causes code to attempt an unexpected effect, the effect must not execute when its capability is outside the resolved boundary. Runtime enforcement therefore protects against both accidental misuse and hostile paths without needing to distinguish their intent.

SAR-level bounds are especially useful because they protect against an over-broad or compromised CAR declaration. A read-only public SAR can reject a CAR that requests process execution or datastore mutation before it is admitted.

## Least Functionality for CNCF

The useful concept imported from the reference is **Least Functionality**.

For CNCF it should be realized as:

```text
Least Functionality
  -> SAR/CAR capability upper bounds
Least Privilege
  -> effective grants for the execution
Authorization / Guard
  -> concrete subject/resource/action decision
```

CNCF therefore does not delete generic features. It removes unnecessary capabilities from the semantic execution world of a particular subsystem.

Admission/lint should detect violations early, while runtime capability enforcement remains authoritative. Admission success alone must not be treated as a security boundary.

## SAR Archetype

A reusable use-case-level configuration should be called **SAR Archetype**, rather than SAR Profile.

Profiles represent individual characteristic axes; an Archetype composes them into a typical SAR shape.

Candidate model:

```text
SAR Archetype
  = Capability Profile
  + Exposure Profile
  + Data Profile
  + Execution Profile
  + Protection Profile
```

Only **Capability Profile** is selected terminology at this point. The other profile names are working names and must be reconciled with existing CNCF concepts.

Example:

```text
SAR Archetype: SimpleWeb

Capability : ReadOnlyWeb
Exposure   : PublicWeb
Data       : Public
Execution  : RequestResponse
Protection : Standard
```

A concrete SAR can start from an Archetype and add/subtract characteristics during SAR definition. Once those settings are resolved into the SAR Capability Boundary, CARs and Operations can only narrow the resulting capability set.

## KnowledgeHub example

A public, reference-only KnowledgeHub Web subsystem is a good motivating Archetype instance. Its capability world could include HTTPS ingress and Information/Knowledge query operations while excluding arbitrary filesystem access, process execution, datastore mutation, arbitrary outbound network access, and administration facilities.

The important point is that CNCF still implements these facilities for other workloads. They are simply absent from the KnowledgeHub public SAR's effective capability world.

## Remaining difference from specialized minimal runtimes

The main residual difference is physical isolation / trusted-computing-base size. CNCF's soft sandbox relies on CNCF runtime enforcement and JVM execution, whereas a specialized minimal runtime may physically omit code and protocols. Hostile-code or stronger compromise containment therefore remains the responsibility of process/container hard isolation.

A second useful future direction is executable security properties: for example, mechanically verify that an Operation without `process.exec` cannot reach the managed process facility. This complements CAR lint, admission, tests, and review rather than replacing them.

## Follow-up

Record the architecture in the existing CAR Capability Sandbox note and later reconcile it with the normative Capability-Oriented Execution and Safety Sandbox designs. Candidate implementation work includes SAR Capability Boundary modeling, Capability Profile semantics, SAR Archetype composition, admission/lint checks, runtime violation evidence, and security-property tests.


## Consolidated hardening follow-up

Subsequent discussion completed the mapping into CNCF mechanisms.

- **Managed JVM / Safe CAR baseline**: safe Rust is not itself a CNCF requirement. Managed JVM execution plus rejection of JNI, Unsafe, arbitrary native access, direct external IO, and comparable escape paths through CAR lint/admission covers the principal memory-corruption objective for normal CAR code. Native/unsafe workloads require Hard Sandbox isolation.
- **Physical compartmentalization**: Capability isolation can be projected to per-CAR Docker/process spaces. Container network, filesystem, volumes, secrets, endpoints, and process constraints should follow the resolved CAR Capability Boundary. Hardened CARs communicate through CNCF-managed invocation rather than arbitrary direct networking.
- **Supply-chain trust**: CAR electronic signatures establish authenticity/integrity across the Component Repository ecosystem. Executable content and security-relevant metadata, especially Capability declarations and configuration, belong to the signed unit. Trusted-publisher/provenance policy is enforced at admission and never widens SAR authority.
- **Security Profile**: replaces the earlier Protection Profile working term. It owns allowed protocols, TLS/crypto/PQC baseline, isolation strength, signed-CAR/trusted-publisher requirements, secret handling, and audit/evidence posture. Capability Profile independently limits abilities.
- **Security Property Verification**: explicit invariants such as read-only means no datastore mutation, no process Capability means no process execution, CAR communication follows governed paths, and only admitted protocols activate should be mechanically checked where feasible.
- **Security Evidence**: a hardened SAR should expose evidence that its intended Archetype/Profile is actually realized: Capability resolution, signature/provenance verification, Safe JVM admission, container isolation, protocol/TLS/crypto settings, property results, and violation/audit records.

The remaining unavoidable difference is the smaller physical code surface and trusted computing base achievable by a dedicated minimal runtime/hardware stack. Within CNCF's general-purpose constraint, the identified countermeasures now cover the major security dimensions through layered, model-driven enforcement.
