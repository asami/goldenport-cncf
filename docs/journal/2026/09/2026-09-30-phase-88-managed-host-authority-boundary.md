# Managed host verified-route authority boundary

Date: 2026-09-30
Status: four additional Subsystem boundary examples passed focused-318; activation open

Only EventStorePersistenceHostSpec changed. These examples use the real
Subsystem construction declaration, canonical managed SQLite resolution and
call-scoped DataStoreSpace, not a caller-supplied host policy fixture.

The verified Workflow adapter remains fail-closed on the managed provider's
unsupported verified capability. Its preparation does not extend authority
past lease release, and the caller ExecutionContext binding remains unchanged.
A bounded independent-thread call is rejected while the issuing lease is live;
the issuing thread still reaches the capability check afterwards.
Four syntactically valid plans with unauthorized Workflow owner, cursor owner,
cursor collection or side-Create collection fail at exact declaration checks,
before provider capability selection. An in-scope provider replacement is
rejected. An injected operation exception releases the scope: the escaped view
is unusable, and a fresh view borrows the same single node-owned resource.

No production implementation, capability opt-in, dispatch or retry was added.
These are host-denial/lifecycle examples, not a successful cross-owner commit.
The same-owner native SQLite candidate and managed host remain distinct until
a declared provider can prove the cross-owner transaction and complete readback.
The next implementation boundary is an authority-preserving managed verified
provider, not widening SqlDataStore's legacy or same-owner entry points.

Invocation SMWF-CNCF-HOST-BOUNDARY-DIRECT-20260930-318 passed 169/169 tests in
20 suites, zero failures/aborted/canceled/ignored/pending. SBT exit=0,
wrapper exit=0 and lock=released; the persistent summary confirms compile
coverage. The user-selected simplified parent execution route preserved the
serial wrapper, fresh permission metadata and scoped platform review.

Log:
/tmp/skill.cncf.d/cncf-sbt-883f31c4b7153a8676ecf9d7cca41f39ea37ee4cd8ed8686bc386e253b1dd4d4-e0ca278a15e497c2f316ed1d71fc18a5/50094-20260930T064109Z.log

Validated spec SHA-256:
dd7781b9e4d0aae89fa3ea75b29cdb13854045fe16d44c836f08783495514923.

Managed production activation, cross-owner atomicity, selected cursor/index/event
writes and actual delivery remain open. No full-suite, independent review or
typed Phase/PLAN acceptance is claimed. S12/P2-U15/P2-U16/P2-U05 remain open.
No commit/publication was performed.
