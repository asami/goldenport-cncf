# SBT JVM runtime profile for ordinary Terminal use

On 2026-09-28, `time sbt test` on mac-mini-2026 failed during test compilation
with `OutOfMemoryError: Java heap space`. The supplied log showed 658 main Scala
sources compiling successfully, followed by compilation of 538 Scala test
sources and one Java source. The SBT JVM reached its 1 GiB maximum and spent
over 90% of the last sampled intervals collecting garbage before failing.

The installed SBT shell launcher defaults to 1024 MiB when no JVM memory
options are supplied. The Codex serialized runner supplies a 4096 MiB profile;
the earlier `clean test` at the same source revision completed 3,809 tests
successfully with that profile. This difference explained the Terminal and
Codex outcomes. The observations do not establish a runtime memory leak.

The user requested that memory options not have to be entered for every SBT
invocation. The repository now owns `.jvmopts`, containing the previously
successful profile: 4096 MiB initial and maximum heap, 4 MiB thread stack,
512 MiB reserved code cache, and G1GC. Ordinary `sbt test` reads these options
from the repository root. The developer guide was updated to match this
configuration. The historical July observation about a smaller suite fitting
in 1 GiB remains unchanged.

Verification exercised the installed shell launcher with an intercepted Java
executable that only records arguments. An isolated control project without
`.jvmopts` selected `-Xmx1024m`; this repository selected every option in the
new `.jvmopts`, including `-Xmx4096m`, with no command-line memory override.
No real JVM or SBT runtime was started during this launcher inspection.
`git diff --check` passed. The product suite was not repeated for this launcher
configuration change; the prior identical-source 3,809-test result is the
known successful 4 GiB control, rather than a new test result for this edit.

Changed files: `.jvmopts`, `docs/notes/cncf-developer-guide.md`, and this journal.
There were no Scala source changes or source-version updates in this profile
repair. The changes were initially left uncommitted. The subsequent user
requests "コミットして" and "それもコミットして" include this configuration,
the timezone repair, and the CNCF compiler warning repair in a local
acceptance commit workflow. Final hashes are reported externally.

Current incident disposition: persistent launcher configuration is verified.
No new Hygiene or Development Candidate was admitted. The previous guide's
obsolete 1 GiB guidance was corrected as part of this configuration change.

The parent owned diagnosis, the fixed profile, diff inspection, launcher
verification, and this journal. The registered repair worker applied the two
prescribed configuration/documentation edits without validation or commits.

| Agent identity | Model | Reasoning effort | Agent type | Role/state | START | RESUME | DIRECT | Outcome |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| /root | unknown | unknown | parent-task | diagnosis and verification | 0 | 0 | 1 | profile and launcher verification complete |
| /root/cncf_jvmopts_fix | gpt-5.6-luna | high | cncf_fix_worker_luna | FIX | 1 | 0 | 0 | frozen repair satisfied |
