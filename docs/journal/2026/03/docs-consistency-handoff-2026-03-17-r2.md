# Docs Consistency Handoff (2026-03-17, Round 2)

## Purpose

前回対応後の再確認で残った不整合2件を、開発スレッドで修正・検証するための受け渡し文書。

## Scope

- `DOC-R2-P2-01`: `docs/spec/config-resolution.md` の責務記述が実装と逆向き
- `DOC-R2-P2-02`: Executable Specification の PBT (ScalaCheck) 強化不足

## Findings

### DOC-R2-P2-01

- Severity: `P2`
- Summary:
  - `config-resolution.md` では「discovery of configuration sources」を resolver の提供責務として記載。
  - 実装 `ConfigResolver` 側では source discovery を非責務として明記。
- References:
  - `docs/spec/config-resolution.md:22`
  - `src/main/scala/org/goldenport/cncf/config/ConfigResolver.scala:23`

### DOC-R2-P2-02

- Severity: `P2`
- Summary:
  - `TableDrivenPropertyChecks` は導入済みだが、方針で求める ScalaCheck ベースの性質検証が未導入。
- References:
  - `AGENTS.md:56`
  - `src/test/scala/org/goldenport/cncf/component/ComponentFactoryStoreSnapshotIsolationSpec.scala`
  - `src/test/scala/org/goldenport/cncf/component/ComponentFactoryWorkingSetIterableOnceSpec.scala`

## Work Items

### W1: config-resolution 文書の責務整合化

- 対象:
  - `docs/spec/config-resolution.md`
- 対応:
  - resolver の責務説明から source discovery を外すか、`ConfigSources.standard` 側責務へ明示的に分離する。
  - 「実装優先」方針を崩さず、`ConfigResolver.scala` と矛盾しない文面に統一する。

### W2: PBT (ScalaCheck) の追加

- 対象:
  - `ComponentFactoryStoreSnapshotIsolationSpec`
  - `ComponentFactoryWorkingSetIterableOnceSpec`
- 対応:
  - 少なくとも1つ以上の ScalaCheck property を各Specへ追加。
  - Given/When/Then の可読性を維持しつつ、入力空間を property で拡張する。

## Acceptance Criteria

- [ ] `DOC-R2-P2-01`: `config-resolution.md` に resolver が discovery を担う記述が残っていない。
- [ ] `DOC-R2-P2-02`: 対象2Specの双方で ScalaCheck property が実装されている。
- [ ] 既存意図（snapshot分離 / IterableOnce安全処理）を維持したまま test が通る。
- [ ] `sbt compile` が成功する。

## Validation

- 文書整合確認:
  - `rg -n "discovery of configuration sources|source discovery" docs/spec/config-resolution.md src/main/scala/org/goldenport/cncf/config/ConfigResolver.scala`
- テスト:
  - `sbt "testOnly org.goldenport.cncf.component.ComponentFactoryStoreSnapshotIsolationSpec org.goldenport.cncf.component.ComponentFactoryWorkingSetIterableOnceSpec"`
- ビルド:
  - `sbt compile`
