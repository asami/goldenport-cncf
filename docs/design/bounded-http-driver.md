# Bounded HTTP Driver

Status: normative design

The behavioral contract is [bounded-http-driver](../spec/bounded-http-driver.md).
Runtime capability ownership remains governed by
[component-runtime-boundary-capabilities](component-runtime-boundary-capabilities.md)
and [CNCF architecture](cncf-architecture-overview.md).

## Ownership and dispatch

`UrlConnectionHttpDriver` remains the runtime-selected transport. Its existing
URL construction selects absolute and base-relative destinations. Absence of
the total-budget property enters the unchanged URL connection branch.
Presence first admits the complete bounded policy, then selects the private
bounded helper for GET and string POST. PUT and `postBag` reject before
transport or Bag access.

Ordinary Component UnitOfWork admission owns host authority before driver
selection. One canonical controlled-key lookup uses `String.equalsIgnoreCase`,
matching the bounded driver for ASCII and Unicode-equivalent spellings. That
lookup governs recognition, value pairing, duplicate refusal and removal of
caller controls. It recognizes an optional total as a controlled property,
rejects equivalent duplicates in either order, and emits one canonical total capped by both effective
connect/read limits after existing caller aliases have narrowed host ceilings.
The canonical total precedes the other four controls after unrelated properties.
No equivalent caller time or byte key survives ahead of those canonical controls.
Absence leaves the existing four controls and legacy dispatch unchanged. This
conservative cap preserves host ceilings under the helper's single total-only
timing policy; it adds no independent connection or idle timer. Direct driver
calls retain first-match selection, including invalid-first refusal without
fallback, outside ordinary admission's duplicate-key contract.

One lazy helper belongs to each driver. It owns one reusable JDK 17
`java.net.http.HttpClient`, uses the ordinary default executor, and configures
redirect policy `NEVER`. There is no JVM-global client or application executor
infrastructure. JDK 17 has no client close/shutdown API; no newer API is used.
The helper and raw response model are `private[http]`, not component APIs.

The existing UnitOfWork operations, interpreter, Behavior DSL, driver selection,
fake substitution, and CallTree ownership are unchanged. Authentication and
secret provisioning remain runtime responsibilities outside this prerequisite.
An external Supervisor provider must consume ordinary UoW operations and the
shared protected Service/SPI under its own accepted implementation scope.

## Deadline and body completion

The helper takes one `System.nanoTime` starting point at exchange entry.
Every remaining duration subtracts elapsed time from that same budget. The
request receives only its remaining timeout, and the caller uses a timed wait
on `sendAsync`'s complete-body future. The body stage resolves only from
subscriber completion, so receiving headers does not end the deadline.

A finite subscriber owns accepted chunks and a byte count. It sums each
incoming batch's `ByteBuffer.remaining` before allocating or copying any bytes
from that batch. Accepted chunks and the final byte array each remain within
the admitted ceiling. The subscriber requests batches incrementally and never
publishes partial body storage. No unbounded body handler, stream read loop,
per-chunk deadline, or application retry is used.

Subscription arrival, accepted bytes, terminal state, and completion are
synchronized. Cancellation marks terminal state before a late subscription
can be requested. The late subscription is cancelled immediately. Every
non-success exchange exit cancels both the subscriber and the send future if
one exists; oversize and subscriber errors also terminate the body stage.
Interruption preserves the caller flag. Response materialization runs only
after the body has completed and uses the existing driver's status, header,
content-type, charset, text, and binary interpretation.

## Phase 4 failure model

The concrete peer may stall before headers, stall or trickle during the body,
exceed the byte ceiling, redirect a credential-bearing request, close abruptly,
return an error status, or return unusable metadata. The caller may supply a
malformed explicit policy, endpoint, or header, or may be interrupted before
or during the exchange. Existing public-network-only admission may reject the
initial destination. Failures must finish within the admitted transport
budget, cancel pending transport, and reveal no request or peer secrets.

The selected mechanisms are the single monotonic deadline, timed future wait,
finite body subscriber, terminal cancellation, redirect refusal, existing
network admission, and constant cause-free transport exceptions. They directly
address those failures. Fatal JVM errors propagate. No DNS redesign, retry,
token resolver, streaming upload, custom scheduler, alternate UoW path, or
component clock is introduced.

## Executable evidence resources

The executable specification owns real ephemeral `127.0.0.1` servers, their
executors, scheduled finite stream tasks, and caller threads. Latches and
bounded waits describe observable boundaries. Cleanup always cancels tasks,
closes exchanges, stops servers, interrupts owned callers, and joins or awaits
owned executors. No detached process, user-home artifact, or external service
is required. Generated cases exercise actual transport and observable wire
bytes rather than source text or workflow state.
