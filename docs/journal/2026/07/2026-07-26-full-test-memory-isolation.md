# CNCF Full Test Memory Isolation

## Observation

The CNCF full test suite had been run with `sbt -J-Xmx4G --batch test`.
Investigation showed that the build used:

- `Test / fork := false`;
- `Test / classLoaderLayeringStrategy := ClassLoaderLayeringStrategy.Flat`; and
- one JVM for sbt, Zinc compiler state, 388 test source files, and the complete
  sequential test runtime.

A constrained run completed all 369 suites and 2580 tests with an sbt JVM
limited to 1 GiB. It reported existing test failures but did not run out of
memory or abort a suite. A post-GC class histogram was dominated by Zinc
incremental compiler structures, class metadata, strings, and byte arrays. It
did not identify a CNCF runtime object family as a dominant retained heap.

## Decision

Routine full validation uses:

```text
sbt --batch test
```

No 4 GiB override is required. The existing full-suite execution model remains
unchanged until a forked run can be validated against a stable source tree.

A trial forked run used approximately 130 MiB RSS for sbt and 337 MiB RSS for
the test JVM at one sample, but did not complete cleanly while concurrent source
and target changes were occurring. That result is useful diagnostic evidence,
not sufficient reason to change the build contract.

## Remaining Work

The current dirty Entity changes cause independent compile/test failures and
must be stabilized separately. After those changes are stable, a forked full
suite can be evaluated as a separate build improvement. If memory growth
returns, capture heap dumps at fixed suite intervals and compare retained CNCF
runtime object families against the Zinc/class metadata baseline.
