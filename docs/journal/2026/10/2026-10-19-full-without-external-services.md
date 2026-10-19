# FULL without external service or Docker dependencies

Date: 2026-10-19
Status: implementation updated; validation recorded below

The user requests normal FULL tests that do not depend on external server
availability or MySQL/PostgreSQL container startup/cleanup cost. The user
explicitly retains small test-owned same-JVM loopback HTTP servers in FULL.
Embedded SQLite and in-memory providers remain normal test resources.

The [test policy](../../../spec/test-policy.md) extends the existing Slow-based
selection without a new tag, registry or runner. Twenty-four external-service
suites now carry `org.scalatest.tags.Slow`: PostgreSQL acceptance across datastore,
entity, action, UnitOfWork and display; MySQL acceptance; the cross-provider SQL
acknowledgement suite; and live Docker service-container acceptance. Their
existing real-backend assertions and live opt-in requirements remain intact.

`UnitOfWorkPlainMutationProviderParitySpec` remains mixed: memory and SQLite
cases stay FULL, while its MySQL case is Slow. MySQL startup/cleanup moves from
suite-wide hooks into that selected case, so even an inherited live opt-in
cannot start Docker when FULL runs the memory/SQLite cases. The cross-provider
acknowledgement suite remains HEAVY because its cases compare actual PostgreSQL,
MySQL and SQLite together; standalone SQLite coverage is not reclassified.

Ordinary `test` already excludes Slow, ManualSpec and TimingSpec. `testOnly`
without a tag exclusion still runs all cases in a selected suite: consumers
must not call that an external-service-free selection. HEAVY is explicit, not
an automatic sequel to FULL or each repair. Keep existing prescribed SBT
serialization. No assertions, property counts, production code, active Phase
state or pre-existing validation-metadata work is changed.

Initial authoring validation: static inspection confirmed all 24 identified external-service suites
carry the suite-level Slow tag; their executable bodies/assertions are unchanged
apart from annotations and header comments. The mixed provider suite retains
untagged memory/SQLite cases and has no suite-wide container startup hook. The
existing FULL exclusion is unchanged. No test source overrides ScalaTest's
`invokeBeforeAllAndAfterAllEvenIfNoTestsAreExpected`; its default skips lifecycle
hooks when the filter excludes every test, as documented in the
[ScalaTest lifecycle contract](https://www.scalatest.org/scaladoc/3.2.9/org/scalatest/BeforeAndAfterAll.html).
`git diff --check` passes. No SBT compilation, focused execution, FULL or live
HEAVY run was performed during that initial authoring; runtime acceptance was
unverified at that point.

## Commit validation — October 19

Focused validation passed compilation and the Slow-excluding selection over all
25 changed suites with both PostgreSQL and MySQL live flags enabled. The two
untagged in-memory/SQLite provider-parity cases passed; no native DB or Docker
startup occurred. SBT and wrapper exited successfully and the shared lock was
released. This accepts the exclusion/lifecycle adjustment without claiming a new
repository-full run or execution of the opt-in HEAVY cases. The original
real-provider assertions remain unchanged.
