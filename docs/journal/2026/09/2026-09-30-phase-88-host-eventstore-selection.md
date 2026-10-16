# Captured host EventStore selection

Date: 2026-09-30
Status: bounded source implementation validated; no publication or Phase acceptance

Trusted EventStoreConstruction captures one service when Subsystem is created,
independently of storage declarations/grants. Legacy EventStore.inMemory remains
the default. Verified managed host views carry the captured service; raw views
have neither read-policy nor service admission. observeHostEventsC takes only
ordinary records, selects through that service and captures the request inside
the live host scope. Sender persistent flags do not decide durability.

Snapshot provenance checks require the original provider, live lease and exact
captured service identity. Another store with the same revision rejects, as does
caller-supplied selection from even the installed store. Nested bootstrap cannot
replace a service already captured by Subsystem. Escaped snapshots and legacy
no-policy hosts reject the new route; construction ThreadLocal is restored.
This check is internal observation provenance, not immutable write authority.

Attempt 342 stopped before CNCF compile because the Cozy development classpath
was stale; no product tests ran. Attempt 343 ran ordinary Cozy compile and
cozyExportRuntimeClasspath successfully, changing derived output only, with no
Cozy source edit or publishLocal. Retry 344 passed 225/225 tests in 26 suites,
zero failed/aborted/canceled/ignored/pending cases. SBT/wrapper exits 0/0 and
lock=released; final summary/log invocation, cwd and exact testOnly argv match.
No source edits followed validation. The existing simplified parent recovery
route retained the shared serial wrapper and scoped platform permissions; no
runner/history approval loop, agent/skill edit or sandbox relaxation was added.

Final invocation: SMWF-CNCF-HOST-SELECTION-DIRECT-20260930-344.
Final cwd: /Users/asami/src/dev2025/cloud-native-component-framework.
Final log: /tmp/skill.cncf.d/cncf-sbt-2db96626f2685ca6b37e357c37c4602229eba6e5f440d43a87e83d2c50a1fd6b-2547b3cd5e7492ead236d02acfc247bf/32974-20260930T111302Z.log

Validated SHA-256:
- EventStoreConstruction.scala: 2ddfab7a52f6fa90a559e10b273ce72f2df70ac4cc410b25e48b4f201662ca80
- Subsystem.scala: 3c7dc98f83ab5d3e9725aeb0c0097dc6e38cd68ac1d801789b9641e2781264b8
- DataStoreSpace.scala: 6ce7aecc15342b41cde44a87d12e4f7fab4d3af0716545c5376451666a7c7d66
- DataStoreObservation.scala: 3e455a13b09be707b01d36ff43236ac2ccd62e0176d50866c1c3374648916703
- DataStoreObservationSnapshotSpec.scala: d022c98566100a9401cff5bfde5ed186f1438c21dfb936abf362b95840dacff5

Next: immutable host-bound preparation that freezes cursor/envelope/index/absence
facts and genuinely new templates without consulting mutable source projections.
Then native write-time validation of all exact observations, retained readback,
validation-only all-retained completion and all-transient completion. No new
publication API, automatic retry, cursor reset, dispatch or application kind
catalog was introduced. S12/P2-U15/P2-U16/P2-U05 remain open. Production assembly,
launcher/CLI/MCP, restart and management remain independent requirements.
No full review, typed acceptance, commit, publication or goal completion claimed.
