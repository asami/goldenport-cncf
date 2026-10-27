# 軽量 File Store SPI と Drive 統合の提案

日付: 2026-10-20

本稿は ESS3-CNCF-STORE-BOUNDARY-001 の親作業で選択した方向を残す非規範的な提案である。
規範的な spec/design、実装済み API、実行報告、受入完了を表さない。
判断の経緯は [同日の journal](../journal/2026/10/2026-10-20-ess3-lightweight-file-store-boundary.md) に記録する。

## 現在の境界と根拠

- [BlobModel.scala](../../src/main/scala/org/goldenport/cncf/blob/BlobModel.scala) の
  `org.goldenport.cncf.blob.BlobStore` は `name`、`put`、`get`、`delete`、
  `accessUrl`、`status` を公開し、操作結果を `Consequence`、内容を `BinaryBag` で扱う。
  `BlobPutRequest` は `EntityId`、`kind`、`contentType` を必要とし、
  `BlobPutResult` は digest と access URL を持つ。これは managed Blob の契約であり、
  任意の既存ファイルをそのまま読む契約ではない。
- [BlobRepository.scala](../../src/main/scala/org/goldenport/cncf/blob/BlobRepository.scala) は
  内容保存と別に、native Entity を通して Blob のメタデータを永続化する。
- [BlobStoreConfig.scala](../../src/main/scala/org/goldenport/cncf/blob/BlobStoreConfig.scala) は
  `BlobStoreProvider` と `BlobStoreFactory` を提供する。既存の責務を再利用し、
  同等の store/provider 機構を第三の仕組みとして重複させない方向とする。
- textus-aws の `S3BlobStore` は同パッケージ独自の `BlobStore` を実装している。
  `BlobKey(bucket, key)`、`BlobObject` の `Array[Byte]`、`Either[String, ...]`、
  `put/get/list/delete` と注入された `S3BlobClient` を使う。
  native CNCF `BlobStore` と既に互換な provider ではなく、実 AWS 接続の証拠でもない。
  外部 checkout の証拠所在は以下であり、本リポジトリ内のリンク先ではない。
  `/Users/asami/src/dev2026/textus-aws/src/main/scala/org/simplemodeling/textus/aws/blobstore/BlobStore.scala`、
  `/Users/asami/src/dev2026/textus-aws/src/main/scala/org/simplemodeling/textus/aws/blobstore/S3BlobStore.scala`。
- simplemodeling-lib の `org.goldenport.vfs.FileSystemView` は
  `list/get/getFile/exists` を持つ read-only な lazy view で、書込み操作を持たない。
  パスで見せる view であり、OS の FileSystem ではない。
  外部 checkout の証拠所在:
  `/Users/asami/src/dev2025/simplemodeling-lib/src/main/scala/org/goldenport/vfs/FileSystemView.scala`。

## 親作業が選択した責務の提案

既存の managed Blob 層には canonical Entity identity、メタデータ、digest/integrity 方針、
managed lifecycle、表示用 access URL を残す。本提案で既存 public `BlobStore` の
削除、改名、振舞いの移行は行わない。

その下の軽量 SPI は、設定済み store、不透明な物理参照、内容メタデータ、Bag と
resource lifetime を表現する方向とする。Material/Information/Entity ID、派生した意味属性、
公開 URL、自動 hash、新しい registry、汎用 POSIX filesystem 意味論は要求しない。
最小操作は利用側が必要とするメタデータ参照、内容取得、新規 object 作成である。
`put` という名前だけで overwrite/update/delete/list/copy を許可せず、
実際の利用要求と意味が凍結された場合に限って追加を判断する。
原本を保持し、破壊的な操作を自動再試行しない。

Store/backend の選択と不透明な物理 locator は権限付与ではない。
強い資格情報によるアクセスに先立ち、native Operation と所有 Entity/source の認可を行う。
クライアントが渡した Drive/file/connection ID を権限根拠にしない。
Drive/S3 adapter は provider 固有のアドレス方式と資格情報を隠蔽する。
OAuth/token lifecycle は outbound integration の責務であり、ingress の
`AuthenticationProvider` に持ち込まない。
CNCF の既存 HTTP/UoW の実行許可、制限、構造化失敗を使い、これを迂回する
独自 network client を作らない。ログに資格情報や内容を出さない。

Source-aware Material と TKL package/manifest/asset の論理配置は Studio/TKL が担う。
App ID を自動的に別の意味に解釈しない。
VFS は directory 形の表示が必要な利用側のための任意の薄い view とし、
第二の保存 authority、mount、OS emulation、再帰的管理 API を追加しない。
全利用側を VFS 経由に統一する必要もない。

## ソース実装前に親作業が凍結する未決事項

- 実際の public API/effect の形、および同期 `BlobStore` と `ExecUowM` HTTP の適法な統合。
- Streaming Bag の所有者、利用期間、close の責任。
- 物理参照とメタデータの正確な意味。size/version が未知の場合の表現を含む。
- Create-only の命名と衝突、書込み結果が不明な場合の扱い、再実行禁止の規則。
- 信頼できる runtime credentials と source binding。
- 実際の native operation/resource 認可。
- Source 選択と物理 TKL materialization。

これらは編集担当への設計委任ではない。本稿から、そのまま実行できる API、config、
OAuth callback、設定 property が確立したと解釈してはならない。

## 次の実装・受入の順序

親作業が実 runtime API に基づいて未決事項を解決した後、小さな共通 capability、
実 Drive adapter、Studio consumer を一緒に凍結する。
続いて focused behavioral tests と実質的な provider-neutral compatibility checks、
独立した protected Step review、対象を限定した local Step commits、
最後に既存 Phase 履歴の下で CNCF と server の full validation/release を行う。
これらの実行・受入 gate は未完了である。

提案や mock test だけで実 Drive/S3/media/device/TKL アクセス、Phase 完了を主張しない。
新しい CNCF Phase を作らず、server Phase3 の受入条件を変更しない。
main への統合、push、外部 publish は本作業の範囲外である。
