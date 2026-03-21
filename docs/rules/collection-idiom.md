# Collection Idiom (CNCF)

## Scope

- `Collection` represents a plain container of elements.
- `Group` represents a semantic grouping and must be distinguished from `Collection`.
- Only types with a natural `empty` value are in scope.

## CNCF usage

- Apply this idiom to Protocol / Handler / Context collections.
- Share the idiom with core and keep CNCF differences limited to contextualization.

## Required idiom

- default empty arguments
- canonical empty value
- zero-arg `apply()` (for binary compatibility)

## Notes

- Group / NonEmpty families are out of scope.
- Types that should prohibit `empty` are out of scope.
