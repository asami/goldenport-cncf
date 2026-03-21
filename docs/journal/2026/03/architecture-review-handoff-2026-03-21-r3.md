# Architecture Review Handoff (2026-03-21, Round 3)

## Purpose

再レビューで検出したアーキテクチャ上の残課題を、開発スレッドで実装・検証するための受け渡しメモ。

## Scope

- `ARCH-R3-P1-01`: `EntityRuntimePlan` 経路が discover 起動で実質無効
- `ARCH-R3-P2-01`: legacy bootstrap で `descriptor.plan` と実メモリ設定が不一致

## Findings

### ARCH-R3-P1-01

- Severity: `P1`
- Summary:
  - `discover()` 起動時、`plans` は常に空になり、planベース初期化分岐に到達しない。
  - 結果として、runtime plan によるメモリ/working set 制御が実運用で効かない。
  - 先行修正で追加した plan 経由の制御（例: `maxEntitiesPerPartition` 反映）は、現行 discover 経路では活性化されない。
- Primary references:
  - `src/main/scala/org/goldenport/cncf/component/ComponentFactory.scala:69`
  - `src/main/scala/org/goldenport/cncf/component/ComponentFactory.scala:72`
  - `src/main/scala/org/goldenport/cncf/component/ComponentFactory.scala:81`
  - `src/main/scala/org/goldenport/cncf/component/ComponentFactory.scala:446`
  - `src/main/scala/org/goldenport/cncf/component/ComponentFactory.scala:449`

### ARCH-R3-P2-01

- Severity: `P2`
- Summary:
  - legacy bootstrap で `EntityDescriptor.plan` に設定した値と、実際に生成される `PartitionedMemoryRealm` の設定が一致していない。
  - `descriptor.plan` は `maxPartitions=1, maxEntitiesPerPartition=0` だが、実体はデフォルト引数により `maxPartitions=64, maxEntitiesPerPartition=10000` で動作する。
- Primary references:
  - `src/main/scala/org/goldenport/cncf/component/ComponentFactory.scala:224`
  - `src/main/scala/org/goldenport/cncf/component/ComponentFactory.scala:230`
  - `src/main/scala/org/goldenport/cncf/component/ComponentFactory.scala:235`
  - `src/main/scala/org/goldenport/cncf/component/ComponentFactory.scala:238`
  - `src/main/scala/org/goldenport/cncf/entity/runtime/PartitionedMemoryRealm.scala:17`
  - `src/main/scala/org/goldenport/cncf/entity/runtime/PartitionedMemoryRealm.scala:18`

## Implementation Direction

### For ARCH-R3-P1-01

- `EntityRuntimePlan` を `Component` から取得できる明示的な拡張点を追加する。
- `ComponentFactory` の plan 解決をその拡張点経由に変更し、`discover()` の通常経路で `plans.nonEmpty` 分岐が到達可能になることを保証する。
- `private` で閉じた固定 `Vector.empty` 実装を廃止し、将来の metadata 取り込み経路と両立させる。

### For ARCH-R3-P2-01

- legacy bootstrap で生成する `EntityDescriptor.plan` と `PartitionedMemoryRealm` の実引数を同一ソースから構築する。
- 少なくとも以下のどちらかで整合を取る:
  - `descriptor.plan` 値を実体生成にも明示的に渡す。
  - 実体の既定値を採用するなら `descriptor.plan` 側も同値に揃える。

## Acceptance Criteria

- [x] `ARCH-R3-P1-01`:
  - `discover()` 経路で plan が供給された場合に plan 分岐が実行されること。
  - plan 由来の `maxPartitions` / `maxEntitiesPerPartition` / `workingSet` が実挙動に反映されること。
- [x] `ARCH-R3-P2-01`:
  - legacy bootstrap で `descriptor.plan` と実メモリ設定が一致すること。
  - introspection で観測される plan 値と実際の eviction/partition 挙動が矛盾しないこと。
- [x] 追加/更新する Executable Specification は Given/When/Then + PBT 方針に従うこと。

## Suggested Spec Additions

- `ComponentFactoryRuntimePlanActivationSpec` (仮)
  - discover 通常経路で runtime plan が有効化されること。
- `ComponentFactoryLegacyPlanConsistencySpec` (仮)
  - legacy bootstrap における plan 記述値と実体挙動の一致を検証すること。

## Dev Kickoff Template

```md
# Dev Kickoff: Architecture Fix Round 3 (2026-03-21)

## Target IDs
- ARCH-R3-P1-01
- ARCH-R3-P2-01

## Implementation policy
- rules -> spec -> design -> code の順序で実施
- P1 を先行、P2 は同PRまたは後続PRで分割可

## Validation
- sbt "testOnly org.goldenport.cncf.component.ComponentFactoryRuntimePlanActivationSpec org.goldenport.cncf.component.ComponentFactoryLegacyPlanConsistencySpec"
- sbt compile
```

## Review Validation Snapshot (2026-03-21)

- `sbt "testOnly org.goldenport.cncf.action.ActionCallAggregateResolveSpec org.goldenport.cncf.component.ComponentFactoryStoreSnapshotIsolationSpec org.goldenport.cncf.component.ComponentFactoryWorkingSetIterableOnceSpec org.goldenport.cncf.entity.runtime.PartitionedMemoryRealmConcurrentPutEvictSpec org.goldenport.cncf.entity.runtime.PartitionedMemoryRealmLimitSpec org.goldenport.cncf.entity.runtime.EntityCollectionStoreFallbackSpec org.goldenport.cncf.datastore.DataStoreSpaceInjectSpec"`
  - 結果: 15 tests all passed
- `sbt compile`
  - 結果: 成功
