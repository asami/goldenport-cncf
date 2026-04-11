# Component Port Wiring

Status: Fixed
Scope: CNCF component wiring and sample author guidance

## 1. Purpose

This document defines the sample-facing wiring path for CNCF Component ports.

The goal is to make adapter/provider wiring explicit without moving runtime
invocation onto `Port` itself.

## 2. Runtime Boundary

`Port` is a wiring-time model, not a runtime invocation boundary.

The canonical runtime execution boundary remains `OperationCall` / action
execution. Operation logic may consume a service injected into `Component.Port`,
but it should not invoke a `Port` as a runtime client.

## 3. Naming and Registration Rules

Sample components should use stable, capability-oriented binding names.

Examples:

- `generate`
- `chat`
- `embed`
- `knowledge_source_adapters`

The binding name passed to `withBinding(name, binding)` must be the same name
used by `install_binding(name, req)`.

The legacy single-binding accessor remains available for compatibility, but
new sample code should use named bindings.

## 4. Minimal Wiring Flow

The minimal sample-facing flow is:

1. Define a service trait consumed by operation logic.
2. Define a requirement type carrying capability and variation values.
3. Implement `PortApi[Req, S]` to resolve the requirement to a
   `ServiceContract[S]`.
4. Implement `VariationPoint[Req]` to expose and inject `VariationSelection`.
5. Implement one or more `ExtensionPoint[S]` providers.
6. Create `Port(api, spi, variation)`.
7. Wrap it as `Component.Binding(port)`.
8. Register it with `component.withBinding(name, binding)`.
9. Install it with `component.install_binding[Req, S](name, req)`.
10. Read the injected service from `component.port.get[S]` in operation logic.

## 5. Responsibility Split

`PortApi` resolves the required-side contract. It must not construct concrete
providers.

`VariationPoint` is configuration-facing. It exposes current variation state
and accepts injected variation state. It is not only a route selector.

`ExtensionPoint` is the adapter/provider realization boundary. It decides
whether it supports a contract and variation, then provides the concrete
service implementation.

`Component.Binding` coordinates contract resolution, variation resolution, and
adapter/provider realization.

`Component.Port` stores the resolved service instance for the owning Component.

## 6. Failure Behavior

Binding failures are explicit:

- unsupported `PortApi` requirement returns a failure;
- incompatible `ExtensionPoint` candidates return a failure;
- missing named binding returns a failure;
- operation logic should fail explicitly if the expected service is absent from
  `Component.Port`.

## 7. Validation

The canonical executable coverage is:

- `org.goldenport.cncf.component.PortBindingSpec`
- `org.goldenport.cncf.subsystem.GenericSubsystemFactorySpec`

The sample-side draft guide is retained as journal history:

- `/Users/asami/src/dev2026/cncf-samples/docs/journal/2026/04/port-wiring-guide-for-samples-2026-04-07.md`
