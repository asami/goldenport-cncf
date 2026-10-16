# ExecutionContext propagation as runtime infrastructure

Date: 2026-10-15

## Decision

Client -> Server ExecutionContext propagation is a general CNCF runtime concern, not a Safety Sandbox-specific transport.

CNCF Client builds its normal local ExecutionContext and projects only registered propagatable fields into an ExecutionContextEnvelope. Server ingress validates and admits that envelope and constructs a new server-local ExecutionContext. ExecutionContext objects, runtime capabilities, providers, secrets, physical handles and mutable runtime facilities are never serialized across the boundary.

Safety Sandbox is an important first consumer. The Client Sandbox Definition propagates as a restriction/assumption and is intersected with the server deployment definition and Operation definition. Propagation can narrow server authority but cannot mint or widen it.

The same architecture is intended to support registered identity/session references, observability correlation, locale/timezone, experiment arm and other immutable execution assumptions with field-specific trust and merge rules.

Multi-hop forwarding and async/Job boundaries perform a fresh projection/reconstruction rather than blindly forwarding an original envelope.
