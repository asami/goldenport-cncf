# Java 25 JST timezone warning during CNCF test

Date: 2026-09-28
Status: FOCUSED REPAIR VERIFIED
Incident: INC-CNCF-JST-20260928-001
Workflow: cncf-diagnose-fix

## Observation and confirmed cause

CNCF `sbt test` runs Cozy's Information CML generator and provenance validator.
Both use `org.goldenport:goldenport-scala-lib_2.12:2.3.32-SNAPSHOT`.
The library's Java timezone lookups used the legacy three-letter ID `JST`.

The installed Java 25.0.4 source, `java.util.TimeZone.getTimeZone`, writes the
reported warning directly to standard error when an ID is in
`ZoneId.SHORT_IDS`. CNCF's generation process logger labels child standard
error as `[error]`; the warning therefore appeared as an error even though
the process and tests succeeded. No logger filtering or JVM downgrade was
used.

The earlier CNCF clean/test log contained six occurrences, across three
generation/validation rounds. Each round invokes two separate Cozy commands;
the log does not establish two warnings per individual child process.

A direct Java 25 source-file probe using Cozy's actual exported runtime
classpath reproduced two warnings when loading both DateTimeUtils and
DateUtils. Calling the two affected formatting methods added two more.
The `Asia/Tokyo` control emitted none and retained the same current
`+09:00` offset and ZoneId mapping.

## Bounded repair

The owning dependency is `goldenport-scala-library`.

- `src/main/scala/org/goldenport/util/DateTimeUtils.scala`: change three
  `TimeZone.getTimeZone("JST")` lookups to `"Asia/Tokyo"`.
- `src/main/scala/org/goldenport/util/DateUtils.scala`: change the remaining
  lookup to `"Asia/Tokyo"`.
- Maintain both edited files' version history for 2026-09-28.

Method names/signatures, Joda timezone configuration, display formats, and
human-readable `JST` text are retained. Java TimeZone identity is now the
canonical `Asia/Tokyo`; consumers inspecting `DateTimeUtils.jst.getID` see that
canonical ID. The timezone rules and tested date conversions are unchanged.

The producer and consumer SNAPSHOT coordinates are unchanged. The required
`publishLocal` is a dependency-artifact-refresh into the normal local Ivy
repository, so Cozy consumes the corrected jar without a classpath-path
change. Cozy and CNCF build/source code required no changes.

## Verification

| Check | Result |
| --- | --- |
| Existing DateTimeUtilsSpec and DateUtilsSpec | 8 succeeded, 0 failed, 2 suites |
| Library `publishLocal` at 2.3.32-SNAPSHOT | Successful local dependency-artifact-refresh |
| Same Java 25 library load and formatting probe | Four warnings before, zero after |
| Date conversion and formatting probe | Exactly equal output before and after |
| CNCF `verifyInformationCmlGenerationDeterminism` | Successful; zero deprecated JST warnings in full log |
| Generated Scala file count | 34, unchanged |
| Generation provenance | `49634db123d30066a67e7093e50f594f26e6917a626463ddd26a673a3daaa81a`, unchanged |
| Exact source conservation / unrelated JVM changes | PASS / preserved |
| `git diff --check` | PASS |

The formatting probe returned `2026/09/28 09:00:00`,
`2026年9月28日9時0分`, and the same `32400000` millisecond timestamp shift
for the original inputs. The legacy `JST` control still warns under the same
Java 25, demonstrating that warning handling was not suppressed.

SBT was serialized through the registered command runners and shared wrapper.
All three invocations finished with SBT exit 0, wrapper exit 0, and
`lock=released`.

- Date specs: `cncf-jst-date-specs-d7feb45572ae48c7`.
- Dependency refresh: `cncf-jst-refresh-cdf635b04a6144a1`.
- CNCF generation: `cncf-jst-generation-a77c4c9d6fea43d1`.

The full repository suite was not repeated: the direct reproducer, nearest
existing date specs, and exact CNCF generation dependency that emitted this
warning cover the repair. This is focused repair verification.

## Separate records and final state

Current Incident Blocker: resolved by the owning library repair and confirmed
downstream consumption.

Hygiene: Cozy's existing build-date lookup in `cozy/build.sbt` still uses
`TimeZone.getTimeZone("JST")`. It belongs to Cozy's own build task, not to the
CNCF generation processes observed here; retain it as a separate follow-up.
The unrelated existing credentials-file warnings during local publication
were not part of this incident.

Development Candidate: none; no framework capability, architecture, or
public API shape change is required.

At completion of the diagnostic repair, the two library source changes and
this journal were left uncommitted. The subsequent user requests "コミットして"
and "それもコミットして" include these changes, the CNCF warning repair,
`.jvmopts`, developer-guide changes, and JVM runtime profile journal in a local
acceptance commit workflow. Final hashes are reported externally. No push was
part of either request.

## Agent Usage Summary

The bounded edit used GPT-5.6 Luna high; all SBT executions used GPT-5.6 Luna
medium. Parent reasoning effort is unavailable.

| Agent | Model | Reasoning effort | Agent type / runtime source | DIRECT | START | RESUME | Role and scope | Outcome |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| /root | GPT-6 | unknown | parent-task / parent runtime | 1 | 0 | 0 | DIAGNOSE, VERIFY, recordkeeping | Cause confirmed; repair verified |
| /root/jst_timezone_fix | gpt-5.6-luna | high | cncf_fix_worker_luna / registered custom role | 0 | 1 | 0 | FIX; two library files | Manifest satisfied |
| /root/sbt_attempt_24a5b654c2774cf0306e | gpt-5.6-luna | medium | cncf_command_runner / registered custom role | 0 | 1 | 0 | SBT; existing date specs | 8 passed |
| /root/sbt_attempt_6c9b3bac8265920c366a | gpt-5.6-luna | medium | cncf_command_runner / registered custom role | 0 | 1 | 0 | SBT; dependency-artifact-refresh | Successful |
| /root/sbt_attempt_78db1a8f9920ef299319 | gpt-5.6-luna | medium | cncf_command_runner / registered custom role | 0 | 1 | 0 | SBT; CNCF generation determinism | Successful; warning absent |
