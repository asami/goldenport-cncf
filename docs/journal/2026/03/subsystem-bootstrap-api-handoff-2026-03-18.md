# CNCF Handoff: Subsystem Bootstrap API for Embedding

- Date: 2026-03-18
- From: cozy side
- To: cloud-native-component-framework (CNCF)
- Priority: High

## 背景

現状、テスト/埋め込み用途で CLI (`cncf command ...`) または隠し `serverEmulator` に依存する場面がある。
しかし外部アプリケーションから CNCF を組み込むユースケースでは、CLI コマンド追加よりも「CNCF を初期化して `Subsystem` を取得する API」を公式提供する方が適切。

## 目的

- 外部アプリが **同一プロセス内** で CNCF を初期化し、`Subsystem`/実行機能を安全に利用できる API を提供する。
- CLI 実装は将来的にこの API の薄いラッパへ寄せられる構造にする。
- `scripted`/integration test での安定利用を可能にする。

## 要件（MVP）

### 1. Bootstrap API

新規に埋め込み向け公開 API を追加する。

例（名前は調整可）:

- `org.goldenport.cncf.bootstrap.CncfBootstrap`
- `CncfBootstrap.initialize(...) : CncfHandle`

`CncfHandle` は最低限以下を提供:

- `def subsystem: Subsystem`
- `def executeCommand(args: Array[String]): Consequence[org.goldenport.protocol.Response]`
- `def close(): Unit`

### 2. 初期化責務のカプセル化

`CncfRuntime` 内に分散している以下の責務を、埋め込み API から再利用できる形で整理する。

- configuration resolve
- runtime config build
- global context / global runtime context 設定
- subsystem build/setup
- observability/log backend 初期化

### 3. 非CLI運用での安全性

- `System.exit` を呼ばない
- stdout/stderr への過度な副作用を避ける（戻り値で扱える）
- 連続実行 (`executeCommand` を複数回) で安定

### 4. 後方互換性

- 既存 `CncfRuntime.run/execute*` の public behavior は維持
- まずは内部重複を許容してよいが、最終的には bootstrap API を共通基盤に寄せる

## 提案 API スケッチ

```scala
package org.goldenport.cncf.bootstrap

import org.goldenport.Consequence
import org.goldenport.protocol.Response
import org.goldenport.cncf.subsystem.Subsystem

final case class BootstrapConfig(
  cwd: java.nio.file.Path,
  args: Array[String] = Array.empty,
  modeHint: Option[org.goldenport.cncf.cli.RunMode] = None,
  extraComponents: org.goldenport.cncf.subsystem.Subsystem => Seq[org.goldenport.cncf.component.Component] = _ => Nil
)

trait CncfHandle {
  def subsystem: Subsystem
  def executeCommand(args: Array[String]): Consequence[Response]
  def close(): Unit
}

object CncfBootstrap {
  def initialize(config: BootstrapConfig): Consequence[CncfHandle] = ???
}
```

注: 既存型/命名に合わせて調整可。重要なのは「初期化済み handle の lifecycle」を明確にすること。

## 実装ガイド

1. `CncfRuntime` の初期化処理を分解し、再利用可能な internal helper を抽出
2. `CncfBootstrap.initialize` 実装
3. `CncfHandle.executeCommand` で `parseCommandArgs + subsystem.execute` を提供
4. `close()` で global/scope の後片付け（少なくとも再初期化可能性を確保）
5. 可能なら `CncfRuntime.executeCommand(...)` を bootstrap API 経由に寄せる（段階的で可）

## 受け入れ条件

- [ ] 外部コードから `initialize -> handle.subsystem` が取得できる
- [ ] `handle.executeCommand(Array("domain.entity.savePerson", ...))` が `Consequence[Response]` を返す
- [ ] 同一 handle で複数コマンド連続実行できる
- [ ] CLI を使わずに integration test を書ける
- [ ] 既存 CLI (`run`, `executeCommand`) の互換性を壊さない

## テスト観点

1. Unit
- bootstrap 初期化成功/失敗
- invalid config 時の failure
- handle close 後の挙動

2. Integration
- simple component を extraComponents で追加し command 実行
- save -> load -> search の連続実行
- output format (yaml/json) を Response 経由で検証

3. Regression
- 既存 `CncfRuntime.executeCommand` 系 spec が通る

## cozy 側で期待している利用形

cozy/scripted や生成プロジェクト test から、CLI プロセス呼び出しを減らして:

- CNCF を初期化
- command を in-process 実行
- 生成物の振る舞いを直接検証

これにより、テスト速度・安定性・デバッグ性が向上する。

## 非目標（今回スコープ外）

- 新しい大規模 CLI サブコマンド体系の追加
- observability 設定体系の全面刷新
- distributed/multi-process orchestration

## 備考

- 既存の hidden `serverEmulator` は当面残してよい
- ただし test/embedding の正規導線は bootstrap API に寄せる
