# Phase 119: Data Supply, Report Generation and Print Integration

status=planned
planned_at=2026-11-02
owner=CNCF
checklist=[Phase 119 checklist](phase-119-checklist.md)

## Goal

OLAP／BI・業務帳票・印刷を専門家へ任せるため、データ供給、帳票生成接続、印刷依頼・結果の3契約と最小接続経路を整備する。

[仮仕様journal](../journal/2026/11/2026-11-02-data-report-print-provisional-contracts.md)を設計入力とする。備忘録用の開発候補を正式なPhase計画へ登録したものであり、実装・Sessionを開始しない。既存Phaseの優先順位と進捗は維持する。

## Scope and ownership

CNCFは公開契約、実行コンテキスト、権限、共通実行・記録への接続を担当する。プロバイダーは専門ツールへの接続、業務アプリケーションと専門家は分析モデル・指標・帳票様式・発行規則・印刷運用を担当する。

| Existing scope | Use in this Phase |
| --- | --- |
| Record Import／Export、Query/View、公開Operation | 構造化データと用途別投影を再利用する |
| SimpleEntity、ExecutionContext、既存認可 | 主体・組織・ロケール・追跡情報を引き継ぐ。revisionと履歴保存を区別する |
| Job／Workflow／Provider／Blob／監査 | 大量・非同期処理、外部呼出し、生成物保存、記録を再利用する |
| EAIと外部実行の既存契約 | 必要な接続・実行方式だけを採用する。Phase 118全体を一律の前提にしない |

## Delivery groups

| Subphase | Step | Observable outcome |
| --- | --- | --- |
| 119.1 Contract alignment | P119-01 | 既存対応範囲、実装所有者、初期プロバイダーと3契約の設計・仕様を確定する |
| 119.2 Data supply | P119-02 | 権限と宣言済み整合性を保つ構造化データ供給経路を実装・検証する |
| 119.3 Report and print integration | P119-03 | データ時点・テンプレート版・ロケールを伴う帳票生成と保護された生成物取得を実装・検証する |
| 119.3 Report and print integration | P119-04 | 印刷受付・確認可能な完了・不明・取消し・再印刷を扱う接続を実装・検証する |
| 119.4 Qualification and handoff | P119-05 | 統合受入れ、必要な検証と独立レビュー、専門家向け利用文書と接続例を揃える |

P119-02はP119-01に従う。P119-03はデータ供給契約を利用する。P119-04は保存済み文書も入力とし、特定の帳票エンジンを必須にしない。利用可能な契約単位で引き渡せるが、部分的な引渡しをPhase全体の完了と扱わない。

## Boundary

分析指標・キューブ・ダッシュボード、個別帳票のレイアウト、プリンタードライバーや汎用スプーラー自体の開発は対象外とする。専用CDC・履歴ストアの新設は前提にしない。初期対応の方式と不足する前提はP119-01で所有先を明らかにする。

印刷キュー受付を物理的印刷完了と扱わない。応答断で結果が不明な依頼は無条件再送せず、照会・照合と明示的な再印刷を区別する。実機確認が必要な保証は、シミュレーターの成功で代替しない。

## Completion and resume

進捗と完了はchecklistを正本とする。全項目はOPEN、再開点はP119-01。既存開発候補DVL-DATA-REPORT-PRINT-01／02／03は、それぞれP119-02／03／04へ対応付ける。共通設計と受入れをP119-01／05が担う。

専門家へ、利用可能な契約、生成物・接続方法、権限と対応範囲、実際に確認したプロバイダーの制限を引き渡す。旧[notesの候補計画](../notes/data-report-print-foundation-development-plan.md)は計画経緯として残す。
