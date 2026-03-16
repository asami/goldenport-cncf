# Architecture Review Handoff (2026-03-16)

## Purpose

このメモは、CNCFアーキテクチャレビューで確定した指摘を
開発スレッドへ受け渡すための実行単位ドキュメント。

## Scope

- `ARCH-P1-01`: Entity runtime の store fallback が実質無効
- `ARCH-P1-02`: `PartitionedMemoryRealm` の並行安全性不足
- `ARCH-P2-01`: `DataStoreSpace.inject` の ID 生成衝突
- `ARCH-P2-02`: `maxEntitiesPerPartition` が未反映

## Findings

### ARCH-P1-01

- Severity: `P1`
- Summary: store ロード経路が常に `None` となり、working set 未投入データを解決できない。
- Primary references:
  - `src/main/scala/org/goldenport/cncf/component/ComponentFactory.scala:170`
  - `src/main/scala/org/goldenport/cncf/entity/runtime/Collection.scala:29`

### ARCH-P1-02

- Severity: `P1`
- Summary: `PartitionedMemoryRealm` で `_order: mutable.LinkedHashSet` を同期なしで更新しており、並行アクセス時に破損リスクがある。
- Primary references:
  - `src/main/scala/org/goldenport/cncf/entity/runtime/PartitionedMemoryRealm.scala:28`
  - `src/main/scala/org/goldenport/cncf/entity/runtime/PartitionedMemoryRealm.scala:51`
  - `src/main/scala/org/goldenport/cncf/entity/runtime/PartitionedMemoryRealm.scala:56`

### ARCH-P2-01

- Severity: `P2`
- Summary: `inject` 時に `id` 欠損レコードへ固定IDを付与するため、同一 collection で重複が発生する。
- Primary references:
  - `src/main/scala/org/goldenport/cncf/datastore/DataStoreSpace.scala:55`
  - `src/main/scala/org/goldenport/cncf/datastore/DataStore.scala:292`

### ARCH-P2-02

- Severity: `P2`
- Summary: runtime plan に `maxEntitiesPerPartition` が存在するが実装経路で未適用。
- Primary references:
  - `src/main/scala/org/goldenport/cncf/entity/runtime/EntityRuntimePlan.scala:19`
  - `src/main/scala/org/goldenport/cncf/component/ComponentFactory.scala:115`

## Acceptance Criteria

- [x] `ARCH-P1-01`: cache miss 時に store からの entity load が成立すること。
- [x] `ARCH-P1-02`: partition order 管理で data race が発生しないこと。
- [x] `ARCH-P2-01`: `id` なし seed/inject の複数投入で衝突しないこと。
- [x] `ARCH-P2-02`: `maxEntitiesPerPartition` が実装に反映されること。
- [x] 各修正に対応する Executable Specification (Given/When/Then + PBT) を追加/更新すること。

## Development Thread Kickoff Template

```md
# Dev Kickoff: Architecture Fix Batch (2026-03-16)

## Target IDs
- ARCH-P1-01
- ARCH-P1-02
- (optional) ARCH-P2-01
- (optional) ARCH-P2-02

## Implementation policy
- rules -> spec -> design -> code の順序で進める。
- まず P1 2件を先行修正し、P2は同PRか後続PRで分離。

## Validation
- sbt "testOnly org.goldenport.cncf.action.ActionCallAggregateResolveSpec org.goldenport.cncf.entity.aggregate.AggregateSpaceResolveSpec"
- sbt "testOnly <new_or_updated_specs>"
```

## Review Trace

- 確認済み既存 spec:
  - `org.goldenport.cncf.action.ActionCallAggregateResolveSpec`
  - `org.goldenport.cncf.entity.aggregate.AggregateSpaceResolveSpec`
- 実行結果: いずれも pass (2026-03-16)

## Completion Record (2026-03-16)

- 対応完了ID:
  - `ARCH-P1-01`
  - `ARCH-P1-02`
  - `ARCH-P2-01`
  - `ARCH-P2-02`
- 追加した Executable Specification:
  - `org.goldenport.cncf.entity.runtime.EntityCollectionStoreFallbackSpec`
  - `org.goldenport.cncf.entity.runtime.PartitionedMemoryRealmLimitSpec`
  - `org.goldenport.cncf.datastore.DataStoreSpaceInjectSpec`
- 検証:
  - `sbt "testOnly org.goldenport.cncf.action.ActionCallAggregateResolveSpec org.goldenport.cncf.entity.aggregate.AggregateSpaceResolveSpec org.goldenport.cncf.entity.runtime.EntityCollectionStoreFallbackSpec org.goldenport.cncf.entity.runtime.PartitionedMemoryRealmLimitSpec org.goldenport.cncf.datastore.DataStoreSpaceInjectSpec"`
  - 結果: 9 tests all passed
  - `sbt compile`
  - 結果: 成功
