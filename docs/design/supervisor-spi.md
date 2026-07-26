# Supervisor SPI Design

## Decision

Lifecycle authority is modeled as a CNCF standard SPI rather than a Launcher
private endpoint or a Textus-specific client class. `textus-supervisor` is the
first provider component. Textus Control Center consumes the SPI through an
assembly-bound socket.

## Boundary

```
Textus Control Center -- Supervisor SPI --> textus-supervisor
                                         --> local / Compose / Kubernetes driver
```

The Control Center persists its own request audit and derives operational state
from launcher evidence. `textus-supervisor` owns lifecycle request execution
and any child/deployment ownership record. Launcher evidence is observational;
it is not lifecycle authority.

## Deployment

Standalone assembly embeds the `textus-supervisor` provider with Control
Center. A distributed assembly binds the same SPI to an independently placed
provider. The consumer contract remains unchanged; only assembly selection and
provider driver configuration differ.

## Safety

The contract deliberately excludes filesystem locators, shell commands,
credentials, ports, and PIDs. A provider must reject a request it cannot own;
neither it nor a consumer may infer ownership by scanning the process table.

## Verification

`SupervisorSpiSpec` specifies safe request/result facts, ordinary socket
installation, structural exclusion of process/locator fields, and structured
no-provider failure through the safe socket accessor. Component-level provider
and consumer specifications verify the Textus implementation and Control
Center integration.
