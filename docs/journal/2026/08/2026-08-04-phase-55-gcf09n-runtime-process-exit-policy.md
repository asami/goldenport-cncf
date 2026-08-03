# Phase 55 GCF-09N — Runtime Process-Exit Policy Projection

GCF-09N migrates the process-adapter exit controls from direct runtime map
reads to the closed CNCF catalog and one value-only
`RuntimeProcessExitPolicy`.

The catalog registers `textus.force-exit` and `textus.no-exit` as Global-only
Boolean values. Both retain the established `textus.runtime.*`, `cncf.*`, and
`cncf.runtime.*` spellings as decode-only aliases. Wrong targets, malformed
Boolean values, and canonical-plus-alias collisions fail structurally during
catalog admission. Missing values alone default to false.

`CncfRuntime` now resolves its Global candidates once, projects both
`RepositoryBootstrapPolicy` and `RuntimeProcessExitPolicy` from that one
collection, and transports only the latter value object to `CncfMain`. The
external `--force-exit` and `--no-exit` switches remain process-adapter inputs:
they only enable the corresponding policy field and are removed from residual
runtime arguments. The established force-exit dominance and nonzero `noExit`
failure behavior are encoded by the policy disposition.

Initial implementation validation: serialized `Test/compile` passed at
`69068-20260803T192838Z`; direct catalog, policy, and bootstrap focused
validation passed 27/27 at `66441-20260803T192352Z`. An attempted broader
`testOnly` selection including `CommandExecuteComponentSpec` is not evidence
against this slice: that suite requires the `Test / test` setup that installs
`textus.test=true`, which `testOnly` does not apply. Full phase validation will
exercise it through the correct test setup.

`application-mode` and CML deletion remain separate compatibility work. This
slice does not migrate other runtime front controls, launcher behavior, generic
SPI, Phase 54 ownership, or unrelated RuntimeConfig families.

## Review-fix evidence

Independent review found three material boundary defects: a Global-only
process-exit key in a Subsystem split file was discarded before target
admission; `bootstrapC` could throw rather than preserve malformed, wrong-
target, or collision failures as a `Consequence`; and auto-archive enrichment
restarted bootstrap and could resolve the Global collection twice. The fix
rejects catalog-recognized keys inadmissible for an actual split-file target,
composes bootstrap and Global-policy admission with `Consequence`, and
re-admits only the enriched repository argument envelope while carrying the
already resolved Global policies forward.

The regression suite covers all three structured bootstrap failures and the
auto-discovered archive retention path. Serialized `Test/compile` passed at
`80231-20260803T194935Z`; the catalog, policy, and bootstrap focused suites
passed 29/29 at `80963-20260803T195053Z`. A fresh focused re-review is the
next acceptance gate.

That re-review accepted the structural repair but found that the auto-archive
test could not distinguish one Global resolution from a recursive reload that
happened to produce the same final policy. The package-visible bootstrap
observation seam now counts Global-policy projection in the integration path;
the auto-archive example requires exactly one occurrence. The same review also
identified and corrected four private-name flatcase/snake-case violations in
the affected source/specification files. Serialized `Test/compile` passed at
`86642-20260803T200044Z`; the focused suites passed 29/29 at
`87500-20260803T200203Z`. The final focused re-review returned PASS with no
actionable findings and accepts GCF-09N at clean implementation baseline
`61fc63ea09fce0c13b9a366393948cda20a19d07`.
