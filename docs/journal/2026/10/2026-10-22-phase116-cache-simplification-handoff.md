# Phase 116 — キャッシュ容量予約と通常経路の過剰処理を撤去する引継ぎ

Date: 2026-10-22
Status: user-authorized specification/plan revision implemented; focused development validation passed; repository-wide failures and formal Session adoption remain open
Owner: CNCF Phase 116 / CC-04 → CC-05

## 決定

キャッシュ容量は回収の目安とし、厳密な共有クォータにはしない。
通常のファイルI/Oを行い、容量不足は書込み時のI/Oエラーとして扱う。
容量予約ファイル、スロット、予約ハンドル、予約用ロック、読取ごとの容量
確認は撤去する。予約量をメモリに覚える高速化だけでは今回の完了にならない。
空き容量の事前確認も必須にしない。別プロセスの書込みを防ぐ保証にはならず、
失敗処理はどのみち必要である。

残すものは、実際の利用中世代・stagingの保護、短い現在版切替の保護、完成後の
atomic publication、失敗時の後始末である。新しい予約・台帳機構に置き換えない。
既存のarchive安全性・runtime admissionは既存の責任境界で維持する。

正本:

- [仕様 C2/C5](../../../spec/car-sar-cache-management.md)
- [設計](../../../design/car-sar-cache-management.md)
- [Phase 116](../../../phase/phase-116.md)
- [チェックリスト](../../../phase/phase-116-checklist.md)
- [従来の実行履歴](2026-10-21-car-sar-managed-cache-plan.md)

## 調査結果と採用範囲

以下は2026-10-22に作業ツリーを読んだ結果であり、時間計測結果ではない。
実装位置は変更されるため、行番号ではなく処理名も引継ぎの識別に用いる。

| ID | 観察した問題 | 今回の対応 |
| --- | --- | --- |
| 起点 | `ManagedCacheCapacity._reserve` が予約ファイルを走査し、source/extractedとstagingの枠を確保する | 予約自体を撤去。`ReservationProgress`による呼出し削減のみでは解決としない |
| CB-01 | `_checkpoint` → `_collect` が全 `_inventory` を作成・sortしてから件数制限する | 探索から限定する。通常hitに全体回収を接続しない |
| CB-02 | hit時の`adopt`が全予約情報と対象payloadを再走査する | hit時の容量再登録・treeサイズ再集計を撤去 |
| CB-03 | `preparePublication`が全量hashを保存し、復旧側は`publicationIdentity`の存在だけを見る | 使わないhashを撤去し、実際の公開状態を扱う |
| CB-04 | `bindPolicy`が保存済み容量設定との完全一致を要求する | 正常な設定変更を拒否する永続一致ゲートを撤去 |
| CB-05 | `commit`/`_add_reference`が同一世代・同一参照でもJSONを再作成・forceする | 対象の保護下で変更不要ならno-op。必要な欠落修復は維持 |
| DEV-01 → CC04-S2採用 | `_capture_source`が再利用判定前に原本を全量コピーする | 保護済み管理下immutable世代は既存identityを再利用。外部mutable入力は正規取込みを維持 |

予約用staging/entry容量上限、quantum、reserved-byte結果、PolicyConflict等の
予約専用契約も整理する。既存のextractorが別の具体的要件で提供する安全性確認を
一括削除する話ではない。未リリースの予約契約に互換性層を残す必要はない。

## 開発チャットへの指示

2026-10-22の追加判断: CNCF 116の実装・独立検証と、sm-workflowの継続処理修正を
並行して進め、互いの完了を開発の前提にしない。成果が揃った時点で既存Sessionとの
整合と正式な継続・受入れを行う。Goal・Session・候補・変更・履歴・修正回数を保持し、
実装の進捗と正式な受入れを区別する。追加の復旧機構は必須とせず必要最小限とする。

現在のGoal/SessionとCC-04を継続して、このユーザー承認済みの仕様改訂を採用する。
新規Start、履歴の空台帳への置換、修正回数のリセット、受入れ済みSubmitの再送は
行わない。進行中の実装・レビュー・検証結果は保存し、採用記録に旧方針と新方針の
差分を残す。既発行作業に旧予約要件が残る場合は、通常の計画採用手続で今回の差分を
適用し、別の「予約方式を完成させる」作業を続けない。未確定な実行結果を成功・未実行
と決め付けて再実行することもない。

主経路を先に通す順序:

1. **CC04-S1:** capture/extract/publishを普通のI/Oへ戻す。予約だけの管理処理を
   外し、実際の利用保護と公開境界を残す。書込み・rename失敗時は既存currentを保持し、
   未完成出力をhitにしない。cleanup失敗は元エラーを隠さず、残留物を回収可能にする。
   rename成功後の後処理失敗を「未公開」と誤認して正常な公開先を壊さない。
2. **CC04-S2:** 完成済み管理下世代の取得を軽くする。予約adopt、全体inventory、
   tree再集計、管理下immutable原本の再コピー/再hash、同一参照/retirementの
   durable writeを外す。外部原本が変わった場合の新世代検出は保持する。
3. **CC04-S3:** 回収・診断を独立した限定処理にする。候補列挙を全件materialize
   してから絞らず、探索段階から制限する。残りは次の処理へ繰り越す。部分的な集計を
   全体の正確な容量と表示しない。既知の旧世代は対象を直接指定して回収を試みる。
4. **CC-05:** 実際のrepository/runtimeによる起動→利用→終了→再利用の主経路へ
   統合する。CAR/SAR、旧reader保護、offline再展開の既存証拠を再利用し、変更が
   影響するシナリオだけ追加・再実行する。最後にrepository所定の統合受入れを行う。

現行cacheへ旧実装のプロセスが接続している可能性がある。通常の修正でその
使用中ファイルや旧lockを削除しない。この引継ぎはcache sweep、旧tmpの一括削除、
公開artifactの更新を許可するものではない。旧予約情報は新実装の恒久的な利用拒否
条件にしない。必要な旧残留物整理は、利用終了が確認できる範囲で別途扱う。

## 既存成果と検証の扱い

- CC-01の受入れcommit `0f3a433809e2f9ee6b575d114d1fcb7ba8c7b5cc`、
  CC-02 `9da7d491d87d0e01ab630cbdd7cd05ba2181816a`、
  CC-03 `69284867c58915f2b6a0d138e5fdfb608247b03d`は保持する。
  閉じたStepをやり直さず、旧容量設計の変更を残作業の差分として扱う。
- 従来journalに記録されたCAR 10例、SAR/SNAPSHOT 10例、およびCC-04の
  21例成功（receipt `06016330fa63b5eac6097ff2b8ff42c2:ordered-validation:355:validation`）
  は当時の対象に対する結果として保持する。新方針の成功結果には読み替えない。
- E22/E23を含む23例のauthoring記録は検証成功記録ではない。後続結果が既に
  得られていればその実記録を優先し、このメモを理由に進捗を巻き戻さない。
- 予約数・枠の丸め・設定一致だけを検証するテストは旧契約の証拠として履歴を残し、
  新実装に同じ振る舞いを強制しない。partial cleanup、rename前後、プロセス終了、
  current保護の実質的なシナリオは再利用する。
- WorkArea修正とその検証、無関係なGit実行DSL等の並行作業には手を入れない。

追加受入れは、既存fixture/注入点を使い、(a)write failureとcleanup failure、
(b)同一管理下世代のhitに不要I/Oがないこと、(c)未変更参照の再書込みがないこと、
(d)大量の無関係entryがあっても通常hitで走査しないこと、(e)回収探索の上限と
truncation、(f)設定変更後の再利用を確認する。時間閾値・ソース文字列検査ではなく、
実際のI/O呼出し数や処理結果で確認する。ディスクを本当に満杯にする必要はない。
新しい巨大な計測基盤や同じ範囲の反復レビューを追加しない。

## この文書作成時の変更範囲

仕様・設計・Phase 116計画・チェックリストとjournalのみ更新した。
実装、テストコード、staging/index、Goal/Session記録は変更していない。
製品テストは実行しておらず、新方針の実装完了・受入れは未確認。
開発チャットへの送信はこの作業には含めず、本journalを引継ぎ正本とする。

## 開発側の継続状況

上記の文書作成後、CNCF開発側で承認範囲の実装と関連シナリオの独立検証を
進めた。容量予約・永続設定一致ゲートを撤去し、管理下immutable世代の再利用、
未変更参照のno-op、限定探索と繰越、I/O失敗・部分回収の処理を実装した。
CAR不在時の既存resource-not-foundへの変換も維持した。関連するCAR/SAR、
復旧およびrepositoryシナリオは成功している。

全体テストには未解決の失敗が残る。キャッシュなしでも再現したHTTP/起動の
失敗と、全体実行時だけ観測したdatastore lease等の失敗を分けて保持し、
Phase 116による回帰か既存の問題かを一括で断定しない。詳細な実行記録は
既存task配下の`cc04-simplification-development-validation-20261022.md`へ保持する。
この結果を理由にチェックリストを閉じたり、旧候補・受入れ・修正回数を
書き換えたりはしていない。正式な計画採用・独立レビュー・受入れは、
修正された公開継続経路と既存Sessionとの整合後に行う。
