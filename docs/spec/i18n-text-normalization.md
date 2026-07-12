# I18N Text Normalization

Status: normative target contract

## Purpose

CNCF owns locale-aware text canonicalization shared by components. Components
must not implement incompatible Unicode, character-width, script, whitespace,
or comparison-key defaults when this contract applies.

This specification separates display-safe canonicalization from lossy matching
normalization. It does not define translation, domain aliases, scraping rules,
or application-specific name selection.

## Runtime Contract

The standard API accepts:

- source text;
- a normalization profile identifier;
- an optional locale, defaulting to the CNCF `ExecutionContext` locale;
- an option to collect bounded transformation provenance.

The result contains:

- canonical text;
- the effective profile and locale;
- ordered transformation identifiers when provenance is enabled;
- a structured error when the profile or locale cannot be applied.

The standard API belongs to the CNCF I18N/runtime boundary. A component may
invoke it directly through the CNCF public API or through a CNCF-provided port,
but must not depend on another CAR's implementation classes.

## Display-Safe Profile

The canonical profile identifier is `display-safe-v1`.

It performs transformations intended to preserve displayed linguistic meaning:

- Unicode NFC composition;
- conversion of Unicode spacing characters to ordinary space;
- outer whitespace trimming and repeated internal whitespace collapse;
- removal of byte-order marks and non-linguistic zero-width formatting;
- locale-specific display-safe width normalization defined below.

For Japanese text, `display-safe-v1` additionally performs:

- full-width Latin letters to ASCII Latin letters;
- full-width decimal digits to ASCII decimal digits;
- half-width katakana to full-width katakana, including composition of voiced
  and semi-voiced mark combinations.

The profile does not apply blanket NFKC because NFKC also rewrites compatibility
characters whose distinctions can matter in names and titles.

## Comparison-Key Profiles

Lossy transforms belong to explicitly selected comparison-key profiles and
must not overwrite display text. Candidate transforms include:

- locale-aware case folding;
- punctuation-insensitive comparison;
- dash and wave-dash equivalence;
- hiragana and katakana comparison equivalence;
- selected compatibility-character folding.

Comparison-key profiles do not perform aliases, transliteration, old/new kanji
replacement, or domain terminology replacement unless a future profile defines
that behavior explicitly.

## Layering

Consumers apply normalization in this order:

```text
CNCF I18N canonicalization
  -> component-specific normalization
  -> application/domain normalization
```

For Textus Scraper and ArtScene this means:

```text
display-safe-v1
  -> scraper extraction rules, URL/date parsing, configured aliases
  -> exhibition title policy, notice rejection, and period validation
```

Each layer retains the raw source value when diagnostics or provenance require
it. A higher layer must not silently compensate for a missing lower-layer
canonicalization rule.

## Compatibility

Profile identifiers are versioned. Existing profile behavior must not change
incompatibly; a changed normalization contract requires a new profile version.
The runtime default is `display-safe-v1` once the standard API is implemented.

Until that implementation is available, dependent components must report the
missing capability or keep their existing behavior explicitly. They must not
claim conformance by introducing a private replacement under the same profile
identifier.

## Acceptance Criteria

- Japanese full-width Latin letters and digits normalize to ASCII.
- Japanese half-width katakana and combining marks normalize to composed
  full-width katakana.
- NFC, whitespace, and invisible-character behavior is deterministic.
- Display-safe output does not apply case folding or punctuation equivalence.
- Locale defaults come from `ExecutionContext`.
- Provenance identifies the effective profile, locale, and applied transforms.
- Components can invoke the standard without importing implementation classes.
