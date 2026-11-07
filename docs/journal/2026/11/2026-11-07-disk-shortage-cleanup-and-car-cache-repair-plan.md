# ディスク逼迫の整理手順とCARキャッシュ修正計画

Date: 2026-11-07
Status: 調査・作業手順を記録。CNCFの修正と既存キャッシュの回収は未実施

## 今回の判断

不要ファイルを整理し、CARキャッシュの残留原因もCNCF側で修正する。
削除だけで一時的に空きを作って終わりにはしない。既存のGoal、Session、
履歴、未コミット変更、必要な検証記録を保持して進める。

開発は一人で行っており、発行・更新の衝突は開発者側で管理する。
**通常の作業ツリーから `publishLocal` したSNAPSHOT版を使うことを基本**とする。
本格的な並行開発はユーザーが予定するPhase 7で扱い、それまではシンプルな
運用を維持する。今回の整理を理由に、独自の並行開発基盤を先行実装しない。

- 通常の依存更新・動作確認のたびに専用checkoutやランタイム全体の複製を作らない。
- 必要な依存を通常経路でローカル発行し、利用側が実際に選んだ座標・成果物を確認する。
  古い専用checkoutへ修正だけを戻して、正式な最新版から乖離したランタイムを維持しない。
- `publishLocal` の実行は既存のsm-workflow LocalPublicationService／typed SBT経路を使う。
  この方針を、直接SBT実行・失敗した実行の別経路での再実行の許可に読み替えない。
- 開発者が発行順序を管理する。追加のAI承認ゲート、マシン全体の直列化ロック、
  SNAPSHOTの発行ごとの環境固定・複製は導入しない。
- 実際に稼働しているRuntimeのファイル利用保護、既存のsbtロック、失敗記録は保持する。

このjournalは調査と作業引継ぎであり、新しいキャッシュ仕様を単独で定義しない。
製品修正の正本は以下とし、必要な仕様差分が判明した場合は対応する正本へ反映する。

- [CAR/SAR仕様](../../../spec/car-sar-cache-management.md)
- [キャッシュ設計](../../../design/car-sar-cache-management.md)
- [容量予約を撤去した判断](../10/2026-10-22-phase116-cache-simplification-handoff.md)

## 調査時点の状況

2026-11-07のアクセス可能な範囲を調査した概数。ディレクトリ別の`du`と
APFSボリュームの空き容量は意味が異なるため、足し算をそのまま回収可能量にしない。

| 対象 | 観測値 | 扱い |
| --- | ---: | --- |
| Dataボリューム | 調査開始時の空き約6.3 GiB、旧worktree 1件整理後は約10 GiB | 実際の効果は同じボリュームの空き容量で確認する |
| `~/src/dev2026` | 約501 GiB | checkout、生成物、実Session記録を分類する |
| `~/.cncf/cache/extracted` | 約157 GiB | ほぼCAR。既存の世代保護を使って回収する |
| `~/.cncf/cache/sources` | 約26 GiB | 元アーカイブの管理世代。offline再展開と利用状態を考慮する |
| `~/.cncf/installations/sm-workflow` | 約16 GiB | 旧ランタイム複製。現在のlauncherとの関係を確認してから整理する |
| `sm-workflow-p610-publication-repair-20261029` | 約152 GiB | 大半がtarget。cleanだがprimary HEADへ未統合のため、丸ごと削除しない |
| `textus-control-center` | 約122 GiB | target約91 GiB、`.codex-workflow`約31 GiB。実Session記録を保護する |

worktree一覧にはsecondary登録110件があり、108件が存在した。
cleanかつprimary HEADに統合済みのものは53件あったが、これは削除候補の条件であり、
不要であることの確定ではない。dirtyなもの、別作業で利用中のもの、主checkoutは別扱いとする。

### CARの残留について確認した事実

- 完成したCAR展開のうち81世代が同一の
  `org.simplemodeling.textus.SmWorkflow:0.1.0-SNAPSHOT`を示し、記録された展開サイズは
  合計約156.58 GiBだった。80世代は10月30〜31日、1世代は11月7日の作成だった。
  これだけで現在も同じ速度で増加中とは断定しない。
- 81世代すべてにretirement記録があり、同時に各世代を指すcurrent参照も残っていた。
  current参照は合計152件だった。retirement記録があるだけでは削除可能とは判断できない。
- 代表例は本体約130.5 MiBに対し展開結果約1.97 GiBで、ScalaDocが約1.9 GiBを占めた。
  CARに含まれる文書を展開していることが、世代残留の影響を大きくしている。
- `ManagedGenerationSelection.coordinate`は入力アーカイブの正規化絶対URIとkindから
  座標キーを生成する。複数の入力配置場所が、別々のcurrent参照として残る可能性がある。

これはディスクとコードを読んだ観察であり、81世代が未使用であることや、
実行中のleaseがないことを証明するものではない。現状の仕様にも「別座標でcurrentの
世代は保持する」という規則があるため、参照が多いこと自体を直ちにバグと扱わない。
通常のリポジトリ解決で論理座標が入力パスへ置き換わっていないかを再現・確認する。

## 手順1: 削除候補と保持対象を確定する

1. 保存済み一覧を起点に、対象のGit identity、primary/linked worktreeの別、HEAD、
   branch、dirty状態、統合先を再確認する。調査後の変更を古い一覧で上書きしない。
2. 現在の作業、launcher/dev-dir、選択済み成果物、既存Sessionが対象パスを使うか確認する。
   chatが停止中、またはプロセスのcwdに見当たらないことだけで不要とは判断しない。
3. 各候補を「不要worktree」「再生成可能な生成物」「要保持の記録・入力」
   「使用中／用途未確認」に分け、削除する具体的なパスと理由を残す。
   `.codex-workflow`、fixtureのDB、実行入力、ログをtarget内という理由だけで捨てない。
4. 未統合コミットやdirtyな変更を持つcheckoutは、内容と所有者を確認するまで保持する。
   `.git`がディレクトリである別cloneを、linked worktreeと誤認して削除しない。

## 手順2: 不要worktreeと生成物から空きを確保する

1. 不要と確定したlinked worktreeから整理する。必要な入力・記録の退避とbranch保持を
   確認し、通常の`git worktree remove <確定したパス>`を使う。`--force`は使わない。
   Codex管理のworktreeには該当する管理経路を使う。主checkoutやbranchは削除しない。
2. 152 GiBのP610 checkoutは優先調査対象とする。未統合履歴と必要なSession記録を
   確保したうえで、廃止可能か、生成物だけを削除するかを決める。
   152 GiB全体を同一ディスクの退避先へ移すだけでは、空きは増えない。
3. 稼働中の主checkoutでは、再生成可能と確認したScalaDoc等だけを対象にする。
   `target`や`.codex-workflow`の一括削除、全プロジェクトへの一律cleanは行わない。
4. 小さい単位で処理し、パスの消失、branch/必要記録の保持、Dataボリュームの空きを確認する。
   他の開発作業のプロセスを終了させて空きを作らない。

## 手順3: CNCFの再現と修正

最初の対象は`goldenport-cncf`の既存ComponentRepositoryからmanaged cacheへの接続とする。
新しいresolverや終了管理機構は作らない。閉じたPhase 116の過去の受入れ・履歴を
書き換えず、今回の不具合と回帰確認を後続修正として扱う。

1. 現在のmain、関連する既存作業、キャッシュ用worktreeの履歴を照合する。
   `goldenport-cncf-managed-cache-20261031`は調査時点ではcleanだったが、branch名を
   根拠に修正済みとは判断しない。別の修正と重複する実装を始めない。
2. 小さいローカルCAR fixtureで、同じ解決済みリポジトリ／SNAPSHOT座標の入力配置が
   変わった場合を再現する。旧Runtimeを生かしたまま新世代を取得し、current参照、
   lease、最終利用終了後の本体・展開結果の回収を観察する。
3. 設計が区別する二つの入力を保つ。通常のリポジトリ取得では既存の解決済み
   repository/coordinate identityを引き継ぎ、明示的な外部ファイルでは正規化URIを使う。
   descriptorの名前だけで別リポジトリや独立した外部アーカイブを同一視しない。
4. 再現で因果を確認してから、必要な接続箇所と回帰specに修正範囲を絞る。
   世代の内容identity、短い座標更新ロック、利用lease、ClassLoader終了後の解放、
   既存のadmissionを維持する。全キャッシュ走査を通常hit経路に追加しない。
5. 既に生成された旧参照の扱いも確認する。新規取得の修正だけで既存152参照が消えると
   仮定しない。自動的に同一性を証明できない旧参照は推測で統合せず、管理された限定回収の
   対象・手順を決める。移行が既存契約を変える場合は仕様・設計に明示してから実装する。

修正候補の読取起点:

- `src/main/scala/org/goldenport/cncf/component/repository/ComponentRepository.scala`
- `src/main/scala/org/goldenport/cncf/component/cache/ManagedGenerationSelection.scala`
- `src/main/scala/org/goldenport/cncf/component/cache/ManagedArchiveCache.scala`
- `src/test/scala/org/goldenport/cncf/component/cache/ManagedCarCacheSpec.scala`
- `src/test/scala/org/goldenport/cncf/component/cache/ManagedSarCacheSpec.scala`
- `src/test/scala/org/goldenport/cncf/component/cache/ManagedCacheRecoverySpec.scala`

この一覧は調査起点であり、全ファイルを機械的に変更する指示ではない。
容量予約・厳密クォータを復活させず、既存のsoft targetと限定回収を維持する。
ScalaDocの同梱は既存の契約であるため、容量対策として無断で除外しない。
文書用の別展開profileが必要なら、今回の参照残留修正と混ぜず別の設計として扱う。

## 手順4: 修正を検証し、通常のSNAPSHOTへ反映する

ローカルfixtureを基本に、原因の再現と最も近い既存のspecを検証する。
製品コードのコンパイル・テストを伴わない文書更新だけを、バグ修正完了とはしない。

| 確認 | 期待する結果 |
| --- | --- |
| 同一内容の再取得 | 完成した同一世代を再利用し、不要な展開や履歴を増やさない |
| 同じ論理SNAPSHOTの更新 | 新規利用は新世代、旧利用者は旧世代を継続する |
| 同一JVM・別JVMの二つのRuntime | 一方終了後も他方の遅延ロードが成功し、使用中回収をスキップする |
| 最終利用終了 | obsoleteな本体と展開結果を、それぞれの利用状態で回収する |
| 独立した座標・外部入力 | 別currentを誤って上書き・回収しない |
| 中断・回収失敗・旧メタデータ | 通常の管理処理で再試行でき、lockファイルを削除しない |
| offline再利用 | 保持したcurrent本体からネットワークなしで再展開できる |

検証は通常の所有経路で行い、既存の検証結果は実際の対象範囲に限って再利用する。
履歴や修正回数をリセットせず、新しい修正が通ったと推測もしない。
実装・関連検証後、必要なCNCFのSNAPSHOTを通常の`publishLocal`経路で発行し、
利用側が修正版を選択したことを確認する。作業専用の巨大なランタイム複製は作らない。

## 手順5: 既存キャッシュと旧installationを回収する

1. 修正版を利用できる状態にしてから、既存の`inspectC()`と`collectC(dryRun = true)`で
   対象・保留理由・使用中skip・探索範囲を確認する。現在確認できたのはScala APIであり、
   未確認のCLIコマンド名を手順として捏造しない。操作用入口が不足する場合は、既存の
   管理APIへの最小の接続を修正範囲として明示する。
2. これらの集計は件数制限のある部分集計であり、全体の正確な容量ではない。
   `truncated`と繰越を確認する。同じ先頭部分のdry-run反復を全件確認と数えない。
3. 最初に未使用の展開結果を、既存の世代単位の排他保護を取得して限定回収する。
   使用中はスキップする。current参照、世代メタデータ、lockファイルを手で消して
   削除可能に見せかけない。`~/.cncf/cache`全体の`rm -rf`は行わない。
4. sourcesは独立して判断する。current/reusable本体はoffline再展開用に保持する。
   obsoleteな本体は最終利用終了後に回収する。`~/.cncf/local`や元の外部ファイルは対象外。
5. 旧`installations/sm-workflow`は、現行launcherが選ぶ成果物と照合する。
   `selected`ファイルや経過日数だけで不要判定せず、不要な具体的installationだけ整理する。
6. 各回収後にRuntimeの利用継続・次の通常起動と空き容量を確認する。
   10月の残留解消と、新しい通常運用での再増加防止を別々に確認する。

## 手順6: 一時退避を終える

今回の退避先は、ユーザー指定の **`~/backup/work.d/disk-shortage`** に限定する。
別の恒久バックアップ置場や、処理後も残る独自trashを増やさない。
この場所も同じボリューム上の容量を使うため、復旧に必要なものだけを一時退避する。
移動だけを空き容量の改善とは報告しない。

削除した対象、理由、保持branch、退避の有無を一覧に追記する。
必要な永続記録は既存の正規の置場へ保持し、今回の退避先を稼働環境の参照先にしない。
作業が中断した場合は、未完了の理由と残った退避物を明示する。

作業完了時は、復旧待ち・未統合データ・必要なSession記録が退避先にしか残っていない
状態でないことを確認する。その後、指定ディレクトリの実体と内容を最終確認し、
**`~/backup/work.d/disk-shortage`全体を一括削除する**。
親の`~/backup/work.d`や既存の別バックアップは削除しない。
最終的な空き容量と、退避先が残っていないことを報告して整理を完了とする。

## この記録時点の実施範囲

- 先行調査で旧worktree `sm-workflow-force-closure-20261030`を1件削除した。
  clean・統合済みを確認し、branchを保持した。ScalaDoc等の再生成物を除いた
  一時復旧用アーカイブと一覧は、指定退避先に約229 MiB残している。
- 約152 GiBのP610 checkout、CARキャッシュ、旧installationは削除していない。
- このjournal作成では製品コード・テストコードを変更していない。
  CNCFの不具合修正、コンパイル・回帰テスト、ローカル発行、実キャッシュ回収は未実施。
  バグ修正完了・整理完了とは判定しない。
- 今後の実施結果は、修正したファイル、実際の検証範囲、選択されたSNAPSHOT、
  回収結果、退避先の削除結果を区別して追記する。既存のPhase完了を今回の検証結果へ
  読み替えない。

## 後続実装の引継ぎ

CARの解決済みrepository/coordinateをmanaged selectionへ渡す変更と、
既知の旧入力を明示指定するowner APIを追加した。旧currentの自動統合は行わない。
対象pathを`inspectLegacyCurrentC`で確認し、返されたkeyとgenerationを指定して
`retireLegacyCurrentC`をdry-runし、競合・不一致なら再確認する。apply後の
回収は既存のbounded `collectC`で行う。詳細は仕様と設計を参照。

回帰仕様は追加したが、この記録時点ではnative fix-check/testは未実行。
実cacheの参照変更・回収、SNAPSHOT発行、利用側の選択確認、退避先の整理も
未実施であり、後続の所有者へ引き継ぐ。Phase 116の履歴は変更しない。

操作入口は `cncf cache inspect --root PATH`、`cncf cache collect --root PATH`、
`cncf cache legacy-inspect --root PATH --kind car --archive PATH`、
`cncf cache legacy-retire --root PATH --kind car --key KEY --generation DIGEST`。
collectとretireは既定でdry-run、実行には`--apply`が必要。collectの継続は
`--apply --batches N`で同じownerの中で最大128回まで行う。対象と期待世代を
毎回確認し、不一致や使用中の拒否は再確認後に再試行する。
