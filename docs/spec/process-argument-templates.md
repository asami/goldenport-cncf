# Runtime-declared Process argument templates

Status: normative contract; executable validation passed 2026-10-06

This additive contract extends ProcessArgumentPolicy without changing the
default Registered or explicitly selected BoundedText admission. A trusted
runtime may select `ProcessArgumentAdmission.Template` and provide complete
ordered `ProcessArgumentTemplate` values. Each template contains literal slots
or a `LowerHex` slot with a nonempty finite set of positive token lengths and
runtime-fixed prefix/suffix. LowerHex accepts only ASCII `0-9a-f`, without trim,
case folding, options, ref interpretation or shell interpolation. Prefix and
suffix are literal argument content, not executable expressions or regexes.

Template matching requires the exact argument count, position and literal
content of one whole declared template. Registered values/vectors must not
serve as fallback in Template mode. Conversely, supplying templates does not
enable them under Registered/BoundedText mode. Runtime construction refuses
that inconsistent declaration, an empty template set, a nonpositive or absent
hex length, null slots/text, or unsafe control content. An explicitly declared
zero-slot template may admit an empty dynamic vector.

Existing runtime-owned fixed-prefix equality, capability grants, finite
argument-count/UTF-8-byte limits, request-level tightening, scoped admission,
canonical ProcessExec/UoW and driver lifecycle still apply. A matching shape
does not prove a Git object exists, is the selected tip, or is authorized for a
workflow transition; the owning workflow checks those native facts separately.
Diagnostics never echo argument/template/executable content. No arbitrary
regex, shell text, host path override or component-created driver is added.

`ProcessArgumentTemplateSpec` is the paired Executable Specification: generated
40/64-character lower-hex tokens, fixed target/ref slots and literal-affixed
refspec tokens; wrong positions/count/options/refs/controls/case; construction
refusal; capability/limit refusal; and deterministic driver non-invocation.
This framework spec uses no live Git/SBT, external remote or credential.

The selected process-execution package validation ran all seven suites:
57/57 tests passed, including six Template examples; zero failures, aborted
suites or skipped tests. This is framework behavior evidence, not downstream
Workflow integration or validation of previously compiled external consumers.
