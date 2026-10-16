# Bounded HTTP Driver

Status: normative static contract

## Runtime boundary

Components use the existing Behavior HTTP DSL or `UnitOfWorkOp.HttpGet` and
`UnitOfWorkOp.HttpPost`. The selected runtime `HttpDriver` owns transport.
This contract adds no component client, public HTTP algebra, secret resolver,
or lifecycle authority. The ordinary UnitOfWork interpreter and its CallTree
remain the dispatch path, including configured fake-driver substitution.

## Explicit policy

`UrlConnectionHttpDriver` selects bounded mode only when the exact property
name `http.total-timeout-ms` is present, compared case-insensitively. Property
selection retains the existing first-match convention. There are no aliases
or new duplicate-key rules. Absence alone selects legacy mode.

The present value is trimmed and parsed as a decimal integer in
`1..Int.MaxValue`. Empty, malformed, zero, negative, or overflowing values
fail before network I/O. They never select legacy mode.

Bounded mode requires `http.max-response-bytes`, trimmed and parsed as a
decimal integer in `0..Int.MaxValue`. Missing, malformed, negative, or
overflowing values fail before network I/O. The limit counts received body
bytes before text decoding; zero admits only an empty body.

In bounded mode, absent `http.follow-redirects` and explicit `false` are
accepted. Explicit `true` and malformed values fail before network I/O.
Redirects are always disabled. The initial destination is still subject to
the existing `http.public-network-only` policy and `PublicNetworkAdmission`.
This does not introduce a different DNS admission policy.

## Supported operations and compatibility

Bounded mode supports the existing driver's GET and string POST operations.
POST uses UTF-8 bytes for a supplied string and an empty publisher for no body.
Bounded PUT and `postBag` validate the declared bounded policy and fail as
unsupported before opening a connection or a Bag input stream.

Without `http.total-timeout-ms`, GET, POST, PUT, and `postBag` retain their
legacy URL connection behavior, connect/read timeout properties, redirect
policy, optional byte ceiling, and public-network admission. Default driver
selection, public signatures, and the UnitOfWork algebra remain unchanged.

## Whole-exchange time and cancellation

One elapsed millisecond budget starts at bounded exchange entry, before
request initiation, and ends only when the full accepted body is available.
Header arrival and each body batch consume the same budget. A stalled header,
stalled body, or slow trickle cannot reset it. The implementation waits only
for the remaining monotonic budget on the complete-body future. A request
timeout alone is insufficient evidence of this contract.

The total budget is bounded mode's timing policy. Existing legacy connect and
read timeout settings do not imply additional independent bounded-mode idle
or connection deadlines. Runtime elapsed timing is distinct from component
execution clocks.

The body subscriber checks aggregate incoming batch bytes before copying or
allocating accepted body storage. It completes successfully only after body
completion. Any excess fails the exchange without a partial `HttpResponse`.
Timeout, interruption, excess bytes, and transport errors cancel the send
future and body subscription as applicable. A subscription arriving after
cancellation is immediately cancelled. Interrupted callers receive an
`InterruptedIOException` and retain their interrupt flag.

## Response and failure meaning

HTTP statuses, including 201, 307, 401, 403, and 500, remain HTTP responses.
Bounded transport does not reinterpret non-2xx results or Supervisor JSON.
Redirect status and headers remain observable, and no redirect target receives
a request or credentials. No application retry is added.

Response conversion retains the existing case-insensitive header lookup,
text/binary distinction, content-type and charset defaults, and empty body
semantics. Malformed response metadata fails safely.

Driver failures remain `IOException` values at the existing transport boundary;
the ordinary interpreter wraps them into `Consequence`. Timeouts use a
constant safe `SocketTimeoutException`; interruption uses a constant safe
`InterruptedIOException`; other bounded failures use constant safe
`IOException` messages. Returned exception messages, `toString`, and cause
chains contain no URL, endpoint, authorization, body, credential, or nested raw
transport diagnostics. Fatal JVM errors are not converted into transport
failures.

## Executable specifications

[BoundedHttpDriverSpec](../../src/test/scala/org/goldenport/cncf/http/BoundedHttpDriverSpec.scala)
defines BH1 legacy compatibility, BH2 pre-I/O admission, BH3 actual UnitOfWork
dispatch and fake substitution, BH4 whole-body deadlines and cancellation,
BH5 raw-byte ceilings, BH6 redirect isolation, BH7 interruption, BH8 status and
safe failure behavior, and BH9 at least 50 successful generated real HTTP
round trips with no discarded cases.

Existing accumulators remain
[HttpDriverSpec](../../src/test/scala/org/goldenport/cncf/http/HttpDriverSpec.scala)
and [UnitOfWorkHttpSpec](../../src/test/scala/org/goldenport/cncf/unitofwork/UnitOfWorkHttpSpec.scala).
