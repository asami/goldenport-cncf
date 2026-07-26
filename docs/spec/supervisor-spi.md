# Supervisor SPI

## Scope

`Supervisor` is the CNCF-owned provider-neutral lifecycle SPI. A consuming
component submits or looks up durable lifecycle work through this contract; it
does not discover a process, interpret a PID, or construct a shell command.

## Request

`SupervisorRequest` contains only:

- a stable `requestId` and idempotency key;
- an opaque managed `targetId` and optional deployment identity;
- `start`, `stop`, or `restart` intent;
- the authenticated operator subject ID; and
- an absolute deadline.

It never contains a development directory, executable command, environment,
credential, port, or process identifier.

## Result

`SupervisorResult` echoes the request ID and reports one lifecycle state,
optional safe diagnostic, selected supervisor identity, optional instance
correlation ID, and acceptance/completion times. A consumer accepts only a
matching request ID. Repeated submission with the same durable ID and
idempotency key must not create another managed instance.

## Provider selection

`SupervisorSocket` and `SupervisorSocketSet` use normal CNCF SPI resolution.
An assembly chooses a provider by the ordinary component selector and does not
silently select among equal providers. Missing, unhealthy, or ambiguous
providers are structured `Consequence` failures.

`SupervisorSocket.supervisorC` is the safe single-socket accessor. It returns a
structured service-unavailable failure when assembly has not installed a
provider. Component logic must not use the throwing convenience accessor when
provider absence can reach an operational path.

## Execution boundary

The SPI is an ownership boundary, not a process API. A provider may use a
standalone local driver, a Compose driver, or a Kubernetes driver internally,
but those details remain provider-private. The provider must preserve durable
request/ownership facts so that a later lookup is safe after a consumer restart.
