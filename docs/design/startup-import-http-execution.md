# Startup Import Source Reading

Status: settled design

The behavioral contract is [HTTP Execution Runtime](../spec/http-execution-runtime.md).

## Responsibility

Startup seed acquisition is internal runtime initialization. Admission of the
existing startup configuration precedes execution; acquiring a configured
source does not require an ordinary Component HTTP grant.

`StartupImport` discovers configured and default sources, preserves their
deterministic order, decodes source text, and persists data and entities.
It constructs one package-internal `StartupImportReader` from the selected
`RuntimeConfig` for each run and passes it to the private reading paths.
The bootstrap execution context continues to support persistence.

`StartupImportReader` owns both local-file and URL text acquisition. It is the
single boundary for any future startup-specific read restriction, whether the
source is local or remote. The helper keeps runtime configuration internal and
uses its selected `HttpDriver` for URL acquisition. It does not use
`ExecutionContext`, Component HTTP UnitOfWork admission, or a separate network
client.

## Failure and lifecycle behavior

Both reader entry points return `Consequence`. Local reads retain resource
closure and the existing `resourceInvalid` diagnostic on I/O failure. URL
reads capture driver exceptions in `Consequence`, then retain the existing
non-200, empty-body, and successful-body handling. Runtime lifecycle ownership
and cleanup remain unchanged.

## Operation modes

This release preserves existing startup behavior across operation modes,
including configured local sources, default `data.d` and `entity.d`, and
explicit URL sources. A production restriction is a possible later explicit
specification, not current behavior. No policy object, unused flag,
configuration key, admission mechanism, capability, or new transport policy
is introduced here.

Ordinary Component HTTP execution continues to use the existing admission
boundary and transport controls. The startup reader does not grant authority
to Component operations.
