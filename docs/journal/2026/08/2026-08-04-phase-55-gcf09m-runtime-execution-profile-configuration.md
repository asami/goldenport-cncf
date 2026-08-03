# Phase 55 GCF-09M — Runtime Execution-Profile Configuration Projection

GCF-09M moves the runtime bootstrap execution-profile authority from direct
execution-family map interpretation to the closed CNCF catalog and one
value-only `RuntimeExecutionProfileConfiguration`.

The catalog admits the existing profile/key, virtual clock/time, random/id/
scheduler/ordering, locale/timezone/charset, line-separator/math-context,
i18n, and environment allow/value families only at `SubsystemInstance` scope.
Each retains its established `textus.runtime.*`, `cncf.*`, and
`cncf.runtime.*` decode-only aliases. The random seed and configured
environment-value map are confidential. Malformed scalars, unsupported enums,
wrong targets, and canonical/alias collisions fail before runtime projection.

`RuntimeExecutionProfileConfiguration` carries decoded values only. It keeps
the existing defaults and virtual-clock compatibility rule, while the typed
resolver preserves cross-field failure for incompatible time values,
controlled-profile activation, missing controlled values, and undeclared
environment-value names. Neither diagnostic records nor profile summaries
render the configured seed or environment values.

Before a Subsystem exists, `CncfRuntime` derives the descriptor-selected
identity, combined runtime/assembly candidates, and resolved collection once.
It uses that result for SystemNode shutdown configuration and typed execution
profile resolution before `RuntimeConfig` and `GlobalRuntimeContext`; final
fixed-user admission reuses the same candidate set. `application-mode` remains
presentation-only and its deletion, CML, Phase 54 topology/pools, generic SPI,
launchers, and unrelated RuntimeConfig families remain out of scope.

Implementation validation: serialized `Test/compile`
`38992-20260803T183350Z` passed. Focused catalog/projection/bootstrap
acceptance validation passed 24/24 at `40686-20260803T183639Z`. Independent
review admitted two P1s: typed time/start combinations bypassed the existing
structured cross-field checks, and typed `textus.operation-mode=test` did not
authorize the controlled Subsystem execution path. REVIEW_FIX restores the
time/start checks before profile construction and derives activation from the
admitted operation-mode binding; it then enables a controlled Subsystem only
when the resolved typed profile is already authorized. The fix also corrects
the public parameter label and two local executable-spec/formatting P3s.
Serialized `Test/compile` passed at `49811-20260803T185335Z`; focused
catalog/projection/bootstrap acceptance validation passed 25/25 at
`50287-20260803T185422Z`. Focused re-review is pending.

The first focused re-review then found only a residual parameter-indentation P3
and the absence of a manual-without-start executable regression. REVIEW_FIX
aligns the declaration and adds the explicit structured-failure assertion.
Serialized `Test/compile` passed at `53110-20260803T185922Z`; focused
catalog/projection/bootstrap validation passed 25/25 at
`53746-20260803T190030Z`. Final focused re-review is clean and accepts
GCF-09M in the current GCF-09 Step accumulator.
