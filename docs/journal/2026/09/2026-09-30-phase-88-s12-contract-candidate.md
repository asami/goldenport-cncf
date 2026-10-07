# Phase 88 S12 pure multi-guard contract candidate

Date: 2026-09-30
Status: pure input contract validated; provider capability not implemented

`DataStoreConditionalMultiGuardPlan` adds a bounded pure admission value
without connecting it to the existing two-record execution API. It requires
one or more additional validated revision guards, exact host-owned
owner/collection bindings, unique targets across root, successor, guards and
side Creates, and Create-only side effects. The scope constructor is limited
to the CNCF package and intended for host composition, but does not itself
authenticate a caller; a label alone is not physical-store or write authority.
Memory/SQL transaction execution, provider-domain enforcement and
post-commit readback remain to be implemented. P2-U15 and P2-U05 are open.

The new executable specification exercises exact cross-component input,
missing/mismatched scope, overlap, null guard, Save rejection and bounds. The
current source SHA-256 values are
`245955b79dfb2b18c0fbd84bc9f4d174c2690d44272574c681ac3f95b9ee73e7`
for the main plan and
`a82a4a3723c0aaedf716bf7953d4a3fdc0d92e13f7d3a3d23e33705593b88048`
for its specification.
The main-source comment was clarified after retest-284 to state that the
package-limited scope is not itself caller authentication. The passing
focused-286 receipt below binds these current exact bytes.

Focused attempt `SMWF-P2-CNCF-P88-S12-CONTRACT-FOCUSED-20260930-283`
started through the registered serial SBT runner after one same-runner
pre-execution request-retention recovery. Its accepted failure receipt is
`/tmp/skill.cncf.d/cncf-command-execution-21f353df889830e420a7642a840b4e77d55e6be983068a56e405a7527b9d03f9-fc0d5859c95e797aad3e9af19fc9afe9/command-execution-sha256-3318f70e1511bcc271098ccdfc8487073e910801e17c1d883c561e411b1afd4f`.
The test fixture tried to call the private `DataStoreConditionalRoot.copy` and
failed during Test compilation. That fixture was corrected; no product-source
error was reported by this attempt.

Retest `SMWF-P2-CNCF-P88-S12-CONTRACT-RETEST-20260930-284` has an accepted
terminal failure receipt at
`/tmp/skill.cncf.d/cncf-command-execution-1ac74b3ae8a511415be1810d2c5058a5f0f6eec4eee11ad59c8a1d0972a0cb04-4f1389134bc4fc3c3e5b257dd8a9259b/command-execution-sha256-9a1985637ec9e700568d138647cbf13d091f0586253d418421a6d982d71ad9dc`.
Its SBT/wrapper exits were 1/1 with the serialization lock released. CNCF's
Information generation stopped before Scala test compilation because the Cozy
generator process could not load `org/goldenport/collection/TreeMap$`.
Read-only inspection found that class absent from the current local
`goldenport-scala-lib_2.12:2.3.32-SNAPSHOT` jar on Cozy's runtime classpath.
The old `kaleidox_2.12:0.6.19` jar still referred to TreeMap, while the
already locally available `0.6.20-SNAPSHOT` jar refers to PathMap. Cozy's
`build.sbt` had been changed to the latter coordinate outside this slice.

Cozy validation-only prerequisite
`SMWF-P2-COZY-P88-S12-PATHMAP-CLASSPATH-20260930-285` ran `compile` and
`cozyExportRuntimeClasspath` through the serial SBT runner. Its accepted
success receipt is
`/tmp/skill.cncf.d/cncf-command-execution-e3f452e3e7ba531a726a32a08376dc02e399efdd2d74abf50bd27ef6120a98be-8a723ae4e9f4f5f5d0b2708c4d0d4a5e/command-execution-sha256-a1ac48d783606ee54470ba699db60cde322200fa0b1d394c774dc4eeb8cacd57`.
The generated Cozy runtime classpath now names
`kaleidox_2.12:0.6.20-SNAPSHOT` and the PathMap-capable local Scala library.

Focused contract attempt
`SMWF-P2-CNCF-P88-S12-CONTRACT-PATHMAP-20260930-286` then passed 12/12 tests
in two suites, with SBT/wrapper exits 0/0 and the serialization lock released.
Its accepted receipt is
`/tmp/skill.cncf.d/cncf-command-execution-e8b5d4bc96e3e52fefa17bc30e7c0659bc71f2ad2e641d2b32b10aabac3c6887-feffbc2191c5ff2babce50c0b481a2e2/command-execution-sha256-edfd66d3ea87ca849ee854d16a024bc786cc028fefd6e3dfc32f1c9fa7169d0a`.
The complete validation tree SHA-256 is
`de0e68d6d6a4f55bc5e0e866d99fdf399e4963143d1acd22212e7d668760c3dc`.
This accepts only the pure input contract, not the S12 provider capability or
P2-U15. No source edit, commit or publication was performed by either SBT
attempt.
