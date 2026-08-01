# Phase 53 CS-05C: Stable Subsystem Identity

Date: 2026-08-01

## Decision and implementation

Direct implicit-Subsystem bootstrap now derives its identity only from
descriptor-owned evidence. The selected order is:

1. an adjacent explicit Subsystem assembly descriptor's `subsystemName`;
2. `ComponentDescriptor.subsystemName`;
3. `ComponentDescriptor.componentName`;
4. `ComponentDescriptor.name`.

No path, directory name, CAR filename, coordinate, or launcher spelling may
be promoted to a stable Subsystem identity in this qualified bootstrap route.
If all descriptor-owned sources are absent, the projection returns a structured
resource-invalid consequence. General repository discovery retains its
separate legacy path fallback; this CS-05C decision does not make it bootstrap
authority.

Both direct packaged CAR and explicit development-directory resolution now use
the same `GenericSubsystemDescriptor.fromComponentDescriptor` consequence
path. The resolved identity is therefore available before the factory can
construct the Subsystem and before profile or Web-operation consumers receive
it.

## Boundaries preserved

This slice does not modify `SubsystemExecutionProfile`, add a configuration
field, select user profiles, resolve precedence, add provenance, convert OS
environment variables, or change datastore/Web behavior. In particular, the
Phase 55 ConfigurationBinding deferral recorded by CS-05B remains unchanged.

## Executable evidence

`Phase53StableSubsystemIdentitySpec` proves descriptor-owned precedence, the
rejection of a path-derived identity in strict bootstrap, and path-form
independence for equivalent descriptor evidence. The common factory projection
is exercised alongside descriptor, factory, assembly-admission,
execution-profile, and standalone-user admission specifications.

Focused serialized validation on 2026-08-01 passed:

```text
Test/compile
testOnly org.goldenport.cncf.subsystem.Phase53StableSubsystemIdentitySpec \
  org.goldenport.cncf.subsystem.GenericSubsystemDescriptorSpec \
  org.goldenport.cncf.subsystem.GenericSubsystemFactorySpec \
  org.goldenport.cncf.subsystem.SubsystemAssemblyAdmissionSpec \
  org.goldenport.cncf.subsystem.SubsystemExecutionProfileSpec \
  org.goldenport.cncf.config.StandaloneUserProfileResolverSpec

63 tests succeeded; 0 failed; 0 aborted.
```

The factory security-wiring specification was also made deterministic by using
an explicit empty repository rather than discovering ambient user-home CAR
state. This is fixture isolation only; it does not alter runtime admission.
