    # Phase 2.8 — Suffix / Egress Design
    status = finalized
    scope = infrastructure hygiene (Phase 2.8)

    ## Purpose

    This document finalizes the design of the **Suffix / Egress mechanism** introduced
    in Phase 2.8 as part of infrastructure hygiene.

    The goals are to:

    - Separate output representation concerns from operation logic.
    - Prevent ad-hoc or operation-level format conversion.
    - Establish a stable output boundary before Phase 2.9 feature development.
    - Avoid future interrupt-driven infrastructure changes.

    This design introduces **Suffix** as an output intent marker and **Egress**
    as the sole owner of output parsing, conversion, and rendering.

    ---

    ## Design Summary

    - **Suffix** specifies *how* an operation result should be rendered.
    - **Egress** performs parsing, conversion, and rendering.
    - **Operation implementations never perform format conversion.**
    - json → AST → xml / yaml / hocon is provided as an **Egress pipeline**.

    ---

    ## Responsibility Separation

    | Layer | Responsibility |
    |------|----------------|
    | Alias / Canonical | Resolve which operation is executed |
    | Operation | Produce semantic result |
    | Suffix | Declare desired output representation |
    | Egress | Parse, convert, and render output |

    Principles:

    - Suffix does **not** parse or convert.
    - Operation does **not** format-convert.
    - Egress is the **only** conversion boundary.

    ---

    ## Operation Output Contract

    ### Canonical Output Format

    Each operation defines exactly one **canonical output format**
    as part of its contract.

    Examples:

    - admin.system.ping → json
    - config.dump → hocon

    ---

    ### Allowed Return Forms

    Operation implementations may return either:

    1. **Canonical-format string**
       - Example: JSON string when canonical format is json
       - Lowest implementation cost

    2. **Structured value (AST / Tree / Value)**
       - Enables maximal conversion flexibility
       - AST is **not mandatory**

    The framework must support both forms.

    ---

    ## Suffix Semantics

    ### Meaning

    A suffix expresses **output representation intent** only.

    Examples:

    - .json
    - .yaml
    - .hocon
    - .xml

    ---

    ### Scope of Influence

    Suffix affects:

    - Output rendering

    Suffix does **not** affect:

    - Operation selection
    - Business logic
    - Canonical Path or Alias resolution

    ---

    ## Egress Pipeline

    ### Role of Egress

    Egress is the output boundary layer responsible for:

    - Parsing canonical-format strings when needed
    - Converting structured representations
    - Rendering final external representations

    ---

    ### Conceptual Flow

    Operation Result
        ↓
    Egress Ingress
      - result type inspection
      - canonical format validation
        ↓
    Parser
      - json → AST (when required)
        ↓
    Converter
      - AST → AST (yaml / hocon / xml)
        ↓
    Renderer
      - AST → String

    ---

    ### Phase 2.8 Minimal Supported Routes

    MUST:

    - json → AST → yaml
    - json → AST → hocon
    - json → AST (xml entry point)

    INTENTIONALLY LIMITED:

    - XML output quality optimization
    - Schema-aware conversion
    - Streaming support

    ---

    ## Error Handling Policy

    - Conversion failures must be explicit errors.
    - No silent fallback or implicit format guessing is allowed.
    - If requested suffix equals canonical format, no conversion occurs.

    ---

    ## Phase 2.8 Scope Boundary

    ### In Scope

    - Explicit introduction of Egress as a responsibility layer
    - Suffix → Egress linkage
    - Minimal json/yaml/hocon conversion support

    ### Out of Scope

    - Full XML support
    - DisplayIntent / Printable integration
    - Lossless round-trip guarantees
    - Format-specific rendering options

    ---

    ## DONE Criteria (Suffix / Egress)

    This item is considered DONE in Phase 2.8 when:

    - Suffix semantics and scope are explicitly defined
    - Egress owns all parsing and conversion logic
    - Operation implementations are not forced to return ASTs
    - json → yaml / hocon conversion is functional
    - Canonical Path and Alias semantics remain unaffected

    ---

    ## Strategic Impact

    - Prevents infrastructure work from interrupting Phase 2.9 feature development
    - Centralizes all output representation concerns
    - Improves long-term maintainability and tooling compatibility

    ---

    ## Conclusion

    - Suffix expresses output intent.
    - Egress performs conversion and rendering.
    - Operations focus solely on semantics.
    - Phase 2.8 establishes a clean and stable output boundary.

    This design is **finalized and locked for Phase 2.8**.
