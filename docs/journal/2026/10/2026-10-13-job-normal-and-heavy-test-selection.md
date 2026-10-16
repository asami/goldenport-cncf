# Job test budget: normal representatives and opt-in HEAVY

The user requested a light taste of Job management in normal FULL validation,
with expensive/deep/process acceptance run only when needed. Use ScalaTest's
standard Slow tag and SBT's standard `test` / `testOnly` selection. Normal `test`
keeps five representative Job suites plus the ordinary cases of the record and
terminal-projection suites. Detailed suites and the two deep cases are opt-in
with `testOnly * -- -n org.scalatest.tags.Slow`. A selected suite without tag
arguments still runs all its cases. The earlier dedicated classifier and list
command proposal was withdrawn; no custom classification code or test remains.

The 5,000-level record validation, 2,048-level terminal projection and generated
cross-process cases are retained unchanged. Omission from FULL is NOT RUN, not
PASS. This changes coverage scheduling, not Job behavior/spec assertions, and
does not trigger HEAVY after every repair. See [test policy](../../../spec/test-policy.md).

The Slow tag here expresses the optional detailed-validation budget; it does
not assert that every individual tagged case is slow. Future suites declare
tags in their test definitions, with no classification registry or new ABI.
