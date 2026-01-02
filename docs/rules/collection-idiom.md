# Collection Idiom (CNCF)

## Scope

- Collection は単なる集まりを表す型である。
- Group は意味論的な集まりであり、Collection と区別する。
- empty が自然に存在する型のみ対象とする。

## CNCF usage

- Protocol / Handler / Context 系 Collection に適用する。
- core と idiom を共有し、差分は CNCF の文脈化に留める。

## Required idiom

- default empty 引数
- canonical empty 値
- zero-arg apply()（binary compatibility のため）

## Notes

- Group / NonEmpty 系は対象外。
- empty を禁止すべき型は対象外。
