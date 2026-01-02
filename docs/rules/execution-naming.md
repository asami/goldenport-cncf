# Execution Naming Rules (CNCF)

## Core guidance

- ActionCall.execute が基本であること
- Scenario / Program / UnitOfWork は run を使うこと
- prepare が存在する理由（Executable Spec / 検証 / 将来拡張）
- execute と run を混在させない指針

## Rules

- prepare は意味構造の構築を指す。実行はしない。
- execute は prepared な ActionCall を実行する。
- run は interpreter / scenario / workflow を駆動する。

混在禁止:
- prepare / execute / run の意味を混在させない。
- ActionCall の実行は execute に集約する。
- Scenario の駆動は run に集約する。

## Rationale

- ActionCall は実行単位であり、execute が責務を示す。
- Scenario / Program は実行経路の集合であり、run が責務を示す。
- prepare は Executable Spec と将来拡張のために保持する。
