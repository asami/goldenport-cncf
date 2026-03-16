# Architecture Review Handoff (2026-03-16, Round 2)

## Purpose

再レビューで検出した残課題を、開発スレッドで実装・検証するための受け渡しメモ。

## Scope

- `ARCH-R2-P1-01`: component間で store fallback のスナップショットが混線する可能性
- `ARCH-R2-P2-01`: `WorkingSetDefinition.entities: IterableOnce` の二重消費
- `ARCH-R2-P2-02`: `PartitionedMemoryRealm.put` の eviction 競合による書き込み喪失リスク

## Findings

### ARCH-R2-P1-01

- Severity: `P1`
- Summary:
  - `_store_snapshot` が `ComponentFactory` 単位で共有されるため、component境界を跨いで `EntityId` 衝突時に誤解決の可能性がある。
  - `collectionId` が `("sys","sys",name)` 固定で、同名 collection の衝突耐性が低い。
- Primary references:
  - `src/main/scala/org/goldenport/cncf/component/ComponentFactory.scala:32`
  - `src/main/scala/org/goldenport/cncf/component/ComponentFactory.scala:104`
  - `src/main/scala/org/goldenport/cncf/component/ComponentFactory.scala:138`
  - `src/main/scala/org/goldenport/cncf/component/ComponentFactory.scala:173`

### ARCH-R2-P2-01

- Severity: `P2`
- Summary:
  - `_prime_store(..., spec.entities)` と `initializer.preload(spec)` で同一 `IterableOnce` を2回走査している。
  - `Iterator` など単回消費データでは preload が空になる。
- Primary references:
  - `src/main/scala/org/goldenport/cncf/component/ComponentFactory.scala:307`
  - `src/main/scala/org/goldenport/cncf/component/ComponentFactory.scala:320`
  - `src/main/scala/org/goldenport/cncf/component/ComponentFactory.scala:326`
  - `src/main/scala/org/goldenport/cncf/entity/runtime/WorkingSetDefinition.scala:10`

### ARCH-R2-P2-02

- Severity: `P2`
- Summary:
  - `PartitionedMemoryRealm.put` で partition取得後の `realm.put(entity)` が lock 外。
  - 別スレッドの eviction と競合すると、到達不能 realm への書き込み喪失が発生しうる。
- Primary references:
  - `src/main/scala/org/goldenport/cncf/entity/runtime/PartitionedMemoryRealm.scala:36`
  - `src/main/scala/org/goldenport/cncf/entity/runtime/PartitionedMemoryRealm.scala:59`
  - `src/main/scala/org/goldenport/cncf/entity/runtime/PartitionedMemoryRealm.scala:76`

## Acceptance Criteria

- [x] `ARCH-R2-P1-01`:
  - store fallback の参照スコープが component 境界で分離されること。
  - 同名 collection を持つ複数 component でも誤解決しないこと。
- [x] `ARCH-R2-P2-01`:
  - `IterableOnce` を安全に扱い、単回走査データでも `_prime_store` と preload の両方が成立すること。
- [x] `ARCH-R2-P2-02`:
  - eviction 競合下でも `put` の書き込み喪失が起きないこと。
- [x] 各修正に対応する Executable Specification (Given/When/Then + PBT) を追加/更新すること。

## Suggested Spec Additions

- `ComponentFactoryStoreIsolationSpec` (仮)
  - 複数component・同名collection・同minorのIDでも誤解決しないこと。
- `WorkingSetIterableOnceSpec` (仮)
  - `Iterator` を working set に渡しても `_prime_store` と preload が両立すること。
- `PartitionedMemoryRealmConcurrentPutEvictSpec` (仮)
  - 並行 `put` + eviction で消失が起きないこと。

## Dev Kickoff Template

```md
# Dev Kickoff: Architecture Fix Round 2 (2026-03-16)

## Target IDs
- ARCH-R2-P1-01
- ARCH-R2-P2-01
- ARCH-R2-P2-02

## Implementation policy
- rules -> spec -> design -> code の順序で実施
- P1 を先行、P2 は同PRまたは後続PRで分割可

## Validation
- sbt "testOnly <new_or_updated_specs>"
- sbt compile
```

## Completion Record (2026-03-16)

- 対応完了ID:
  - `ARCH-R2-P1-01`
  - `ARCH-R2-P2-01`
  - `ARCH-R2-P2-02`
- 追加した Executable Specification:
  - `org.goldenport.cncf.component.ComponentFactoryStoreSnapshotIsolationSpec`
  - `org.goldenport.cncf.component.ComponentFactoryWorkingSetIterableOnceSpec`
  - `org.goldenport.cncf.entity.runtime.PartitionedMemoryRealmConcurrentPutEvictSpec`
- 検証:
  - `sbt "testOnly org.goldenport.cncf.component.ComponentFactoryStoreSnapshotIsolationSpec org.goldenport.cncf.component.ComponentFactoryWorkingSetIterableOnceSpec org.goldenport.cncf.entity.runtime.PartitionedMemoryRealmConcurrentPutEvictSpec org.goldenport.cncf.action.ActionCallAggregateResolveSpec org.goldenport.cncf.entity.aggregate.AggregateSpaceResolveSpec org.goldenport.cncf.entity.runtime.EntityCollectionStoreFallbackSpec org.goldenport.cncf.entity.runtime.PartitionedMemoryRealmLimitSpec org.goldenport.cncf.datastore.DataStoreSpaceInjectSpec"`
  - 結果: 13 tests all passed
  - `sbt compile`
  - 結果: 成功
