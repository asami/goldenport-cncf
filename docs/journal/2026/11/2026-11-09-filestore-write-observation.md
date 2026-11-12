# FileStore write observation: original execution evidence

The retained `register-native-002.json` records the execution of the selected
registration supervisor and a failed terminal session, not a successful
registration. `private/register-receipt-001.json` records readiness and cleanup,
registration started but not confirmed, and failure at `upload-one-original`.
The original `private/register-run-001/first.stdout.log` records the
`MaterialDelivery.uploadOriginal` action failing with the structured policy
`http.execution.unconfigured`. This is a host managed-HTTP admission refusal;
the HTTP 400 alone would not establish that cause.

The executed supervisor selected CNCF runtime `0.5.6-SNAPSHOT`. Its retained
runtime selection names the CLI and selected CAR paths, but the selection does
not bind that mutable runtime coordinate to a particular binary digest or
source commit. The current CNCF development base `c6c6f09b` must not be
represented as the original executing source. The log does not supply an
owner-native receipt tying the refusal to the exact collection create target,
caller and operation. Consequently the legacy collection effect remains
**UNKNOWN**. A new observation facility cannot retroactively supply that
receipt. A separate operational decision, based on additional authoritative
evidence or explicit human direction, is needed before any action that could
repeat the uncertain write.

Current FileStore semantics permit duplicate display names, and independent
child pages do not establish creation attribution or absence. The existing
reserved direct-upload capability binds a file identity and session, but it is
not a receipt for this earlier collection operation. The current HTTP
interpreter admits each HTTP operation before invoking the driver, but reports
admission failure through the ordinary failure channel rather than returning an
operation-bound FileStore observation. A response lost after driver invocation
cannot be equated with non-dispatch. Authorization and host admission remain
independent requirements.

Evidence paths relative to the Studio worktree:

- `target/ess51-mail-registration/target/register-native-002.json`
- `target/ess51-mail-registration/target/register002-supervisor-source.py`
- `target/ess51-mail-registration/runtime-selection.json`
- `target/ess51-mail-registration/private/register-receipt-001.json`
- `target/ess51-mail-registration/private/register-run-001/first.stdout.log`
