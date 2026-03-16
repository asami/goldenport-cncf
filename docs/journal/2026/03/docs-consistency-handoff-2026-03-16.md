# Docs Consistency Handoff (2026-03-16)

## Purpose

`docs/` 配下の整合性チェック結果に対する対応方針を確定し、
開発スレッドで実装・検証するための受け渡し文書。

## Decisions (Fixed)

1. `AGENTS.md` の `protocol-core.md` / `protocol-introspection.md` 参照は、
   core からのコピー時の削除漏れとして扱う（誤導線を除去または現行文書へ置換）。
2. `docs/spec/config-resolution.md` と実装の不整合は、
   **実装優先**でドキュメントを合わせる。
3. `docs/notes/helloworld-bootstrap.md` のリンク切れは修正する。
4. Executable Specification は **PBT (ScalaCheck) をできるだけ使う**方針へ寄せる。

## Scope

- `DOC-P1-01`: AGENTS 必読導線の不整合解消
- `DOC-P1-02`: config resolution 仕様文書の実装整合化（実装優先）
- `DOC-P2-01`: notes 内リンク切れ修正
- `DOC-P2-02`: 追加済み Executable Specification の PBT 化/強化

## Work Items

### DOC-P1-01 (AGENTS 導線の修正)

- 対象:
  - `AGENTS.md`
- 対応:
  - 存在しない `docs/design/protocol-core.md` / `docs/design/protocol-introspection.md` 参照を削除。
  - 代替として現行の設計入口へ置換（例: `docs/design/cncf-architecture-overview.md`, `docs/design/cncf-introspection-spec.md`）。

### DOC-P1-02 (config spec を実装に合わせる)

- 対象:
  - `docs/spec/config-resolution.md`
  - 必要なら `docs/design/configuration-model.md`（関連整合）
- 対応:
  - API 契約を現行実装に合わせる（`sources` ベース、deprecated 扱い、責務境界）。
  - 旧契約（`cwd/args/env` 単一エントリ）を維持しない。必要なら「過去仕様」または「deferred」に明示。

### DOC-P2-01 (リンク切れ修正)

- 対象:
  - `docs/notes/helloworld-bootstrap.md`
- 対応:
  - `subsystem-execution-modes.md` -> `subsystem-execution-mode.md` へ修正。
  - 必要なら同様の参照を `docs/` 全体検索して一括修正。

### DOC-P2-02 (PBT 強化)

- 対象候補:
  - `src/test/scala/org/goldenport/cncf/component/ComponentFactoryStoreSnapshotIsolationSpec.scala`
  - `src/test/scala/org/goldenport/cncf/component/ComponentFactoryWorkingSetIterableOnceSpec.scala`
  - `src/test/scala/org/goldenport/cncf/entity/runtime/PartitionedMemoryRealmConcurrentPutEvictSpec.scala`
- 対応:
  - 単発例テストのみの箇所に ScalaCheck property を追加。
  - Given/When/Then の読みやすさを維持したまま、入力空間を拡張した性質検証へ置換/併用。

## Acceptance Criteria

- [ ] `DOC-P1-01`: AGENTS の必読導線に不在ファイル参照がない。
- [ ] `DOC-P1-02`: `config-resolution.md` が現行実装の責務/APIと矛盾しない。
- [ ] `DOC-P2-01`: `docs/notes/helloworld-bootstrap.md` のリンク切れが解消される。
- [ ] `DOC-P2-02`: 対象 Executable Specification に PBT が追加され、既存意図を保持する。
- [ ] 変更後に対象 testOnly と `sbt compile` が通る。

## Validation

- リンク確認:
  - `rg -n "subsystem-execution-modes\\.md|protocol-core\\.md|protocol-introspection\\.md" AGENTS.md docs -g '*.md'`
- テスト:
  - `sbt "testOnly org.goldenport.cncf.component.ComponentFactoryStoreSnapshotIsolationSpec org.goldenport.cncf.component.ComponentFactoryWorkingSetIterableOnceSpec org.goldenport.cncf.entity.runtime.PartitionedMemoryRealmConcurrentPutEvictSpec"`
- ビルド:
  - `sbt compile`
