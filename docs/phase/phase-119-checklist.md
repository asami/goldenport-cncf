# Phase 119 checklist: Data Supply, Report Generation and Print Integration

status=planned

[Phase 119](phase-119.md)の進捗正本。全項目は未着手。詳細な検討内容は[仮仕様journal](../journal/2026/11/2026-11-02-data-report-print-provisional-contracts.md)を参照する。

## Stage: 119.1 Contract alignment

Stage Status:
- Current status: OPEN
- Owner: CNCF
- Update rule: P119-01の全項目の実際の成果に基づいて更新する。

- [ ] P119-01a: 既存Query/View/Record Export、Job、Provider、Blob、認可の再利用範囲と不足機能の所有先が記録されている。
- [ ] P119-01b: 3契約の入力・結果・失敗・権限・ロケール・保存期間と、初期対応範囲がdesign/specに定義されている。
- [ ] P119-01c: 初期プロバイダー、受入れ例、実機確認を要する保証と非対応機能が決まっている。

## Stage: 119.2 Data supply

Stage Status:
- Current status: OPEN
- Owner: CNCF
- Update rule: P119-02の実装と実行可能仕様の結果に基づいて更新する。

- [ ] P119-02a: 型・意味・スキーマ版・取得条件を伴うデータが公開契約から取得できる。
- [ ] P119-02b: 主体・企業／組織の境界と取得量・時間の制限を、権限外取得と大量取得の例で確認できる。
- [ ] P119-02c: ページ取得中の更新に対して宣言した整合性を保持し、未対応の時点・差分指定を明示的に拒否する。差分対応時は削除・再取得も確認できる。

## Stage: 119.3 Report and print integration

Stage Status:
- Current status: OPEN
- Owner: CNCF
- Update rule: P119-03／04の実装、接続結果と受入れ証拠に基づいて更新する。

- [ ] P119-03a: 専門帳票エンジンへの最小接続でデータ時点・テンプレート版・ロケールを渡し、生成物と業務対象を関連付けられる。
- [ ] P119-03b: 生成物の取得・保存・外部送付を認可し、機微な内容を診断ログに含めない。
- [ ] P119-03c: 保存済み原本の再出力と最新データによる再生成を区別し、生成成功を印刷・業務発行の完了と誤表示しない。
- [ ] P119-04a: 許可された印刷先と出力条件だけを受け付け、未対応の重要な条件を黙って置き換えない。
- [ ] P119-04b: キュー受付、確認可能な物理完了、失敗、結果不明を区別し、応答断後に無条件再送しない。
- [ ] P119-04c: 取消し要求と取消し確認、一部印刷を区別し、明示的な再印刷を新しい依頼として元依頼へ関連付ける。

## Stage: 119.4 Qualification and handoff

Stage Status:
- Current status: OPEN
- Owner: CNCF
- Update rule: P119-05と全先行項目の実際の完了を確認して更新する。

- [ ] P119-05a: データ供給→帳票生成→保存→印刷→結果照会→明示的な再印刷の統合受入れと、失敗・権限拒否の例が通る。
- [ ] P119-05b: 必要なリポジトリ検証と独立した統合レビューが完了し、指摘事項が閉じている。
- [ ] P119-05c: technical guide・最小接続例・利用可能な成果物／版・実プロバイダーで確認した範囲と制限が引き渡されている。

計画登録ではチェックを付けない。既存Phaseの状態・実行履歴を変更しない。
