# APK解析を前提としたTextus/CNCFのセキュリティ対応

Date: 2026-10-16
Status: design note
Related:
- ../design/textus-security-architecture.md
- ../design/entity-authorization-model.md
- entity-authorization-implementation-note.md
- ../phase/phase-107.md

## 目的

AndroidアプリのAPKは利用者・攻撃者の端末へ配布されるため、アプリのコード、APIの
呼び出し方、Entity ID、画面遷移、入力形式などが解析され得ることを前提にする。

Textus/CNCFでは「正規APKだから信用する」「API URLやIDが秘密だから安全」という
設計を採らない。クライアントを完全には信頼せず、サーバー側のOperation/Entity境界で
認証・認可・入力契約を強制する。

APKの難読化、改造検知、端末attestation等はdefense in depthとして利用できるが、
それらが破られても業務データ・業務Operationの権限境界が破られないことを基本とする。

## Threat Model: Untrusted Client

攻撃者は少なくとも以下を実行できるものと仮定する。

- APKを取得して逆コンパイル・静的解析する。
- 通常画面には現れないAPI endpoint、Operation名、Entity ID形式を知る。
- 正規クライアントが送るrequestを観察し、値・ID・HTTP method等を変更して再送する。
- 正規UIの入力制限や画面遷移を迂回し、APIを直接呼び出す。
- クライアント内に埋め込まれたsecret/API key等を取得する。
- 改造APKや独自clientから正規APIと同じ形式のrequestを生成する。

したがってクライアントが送った userId、tenantId、ownerId、role、Entity ID、
authority-bearing property等は、それ自体を認可根拠として信用しない。

## CNCFの基本境界

外部clientからの操作は次の経路を通る。

```text
Untrusted Client
  -> authentication
  -> SecuritySubject
  -> declared Operation
  -> Capability / Guard / authorization
  -> target Entity / relation evaluation
  -> handler
  -> EntityRealm / Repository / DataStore
```

handlerが「必要なら認可helperを呼ぶ」のではなく、認可を通過しなければhandlerへ
到達しない構造をSafe-by-Constructionの標準経路とする。

## IDOR / BOLAへの対応

APK解析でEntity IDが判明すること自体はsecurity failureではない。

例えば client が

```text
GET /materials/{materialId}
```

の `materialId` を他人のIDへ変更できることを前提とする。

CNCF Runtimeは、IDが存在するかだけでなく、現在の `SecuritySubject` がそのEntityに
対して要求Operationを実行できるかを評価する。

```text
SecuritySubject
  + Operation
  + target Entity
  + relation / tenant / organization / ownership / policy context
       -> authorize / deny
```

「IDを知っている」「UUIDが推測困難」「一覧画面に表示されなかった」は認可ではない。
Universal ID等の識別方式はauthorization boundaryの代替にしない。

現在のEntity Authorizationにあるrelation rule、natural ABAC condition、
SecuritySubject、Operation access policyをこの標準経路へ統合する。

## Object Property Level Authorization

Entity全体へのアクセスだけでなく、propertyの参照・更新にも権限差があり得る。

例:

- Materialのtitleは利用者が更新可能。
- owner / tenant / approval status等は通常clientから変更不可。
- 管理者専用propertyは一般利用者へresponse projectionしない。

requestはpersistent Entityへ直接bindせず、Operation Input Modelへbindする。
入力可能propertyの制限と、subject/entity relationに応じたproperty-level authorizationを
組み合わせる。

response側も「Entityを取得できたから全propertyを返す」とはせず、公開View/Projectionの
契約に従う。

## Function Level Authorization

APK解析によって管理Operationや未使用endpointの存在が知られても問題がない構造にする。

URL path、画面からの到達可能性、HTTP methodの分かりにくさではなく、
OperationごとのCapability/Guardで実行権限を判定する。

deny-by-defaultを基本とし、管理Operation・system Operation等は明示的authorityなしに
実行できない。

## Input ValidationとSafe-by-Construction

APK側の入力制限はUXでありsecurity boundaryではない。

サーバー側はOperation Input Model、logical type、codec、typed DataStore query等によって
入力契約を強制する。SQL injection対策は危険文字列の検査を主防御とせずparameter
binding、XSSはDisplay Model/rendererのcontext-aware escapingを標準経路とする。

mass assignmentはrequest -> Operation Input -> commandという境界で防ぎ、clientが
JSONへ任意propertyを追加してもpersistent Entityの権限属性へ直接反映されないようにする。

## SecretとClient Credential

APKへサーバーmaster credential、共有管理secret、長寿命の高権限API keyを埋め込まない。

クライアントが保持する必要があるcredentialは、そのclient/user/sessionに必要な最小権限と
lifetimeに限定する。秘密にできない識別情報やpublic configurationを「隠す」ための
独自暗号化・難読化をsecurity boundaryにしない。

## APK側Defense in Depth

以下は採用可能だが、サーバー認可の代替にしない。

- release buildの標準的な難読化・最適化。
- platform提供の署名・配布整合性。
- Play Integrity等のdevice/app attestation。
- debugger/root/tamper signal。
- certificate/network policy等、platform標準のhardening。

attestation結果は必要ならGuardのrisk/context inputにできる。ただし「attestation成功 =
対象Entityへの権限あり」とはしない。

独自hash比較、自己改竄監視、複雑なanti-debug machinery等をTextus標準機能として
増やす場合は、具体的なthreat/benefitを別途示す。解析を完全に防止することは目標にしない。

## API Abuse / Resource Consumption

正当に認証・認可されたOperationでも、大量呼び出しや高コストOperationの自動実行は
別の脅威になる。

Operation単位のrate/resource policyを宣言できる方向を検討し、API Gateway等の
deployment controlとCNCF Operation semanticsを接続する。CNCFが汎用WAFや独自rate
limiterを再実装することは目的ではない。

## Executable Security Specification候補

Phase 107等では少なくとも以下を実行可能なsecurity specification候補とする。

1. User Aが自分のEntityをread/updateできる。
2. 同じrequestのEntity IDだけUser B所有Entityへ変更するとdenyされる。
3. tenant/organization境界を越えるID変更がdenyされる。
4. 一般userが管理Operationを直接呼んでもdenyされる。
5. requestにowner/role/approval等の未宣言propertyを追加しても変更できない。
6. property-level read/update policyがresponse/input projectionへ反映される。
7. client側validationを迂回した不正値がserver-side input contractで拒否される。
8. attestation等のclient signalがなくても、それだけを理由に認可をgrantしない。

## 結論

APK解析対策の中心はAPKを秘密に保つことではない。

Textus/CNCFでは、clientが解析され、API仕様とEntity IDが知られ、requestが自由に
再構成されても、SecuritySubject + Operation + Capability/Guard + Entity relationによる
サーバー側authorizationを通過しない限り業務資源へ到達できない構造を標準とする。

APK側hardeningは攻撃コストを上げる補助策として扱い、CNCFの安全性はその成否に依存
しない。

## Authorization Enforcement Architecture

APK解析対策を実効的にするには、ABAC/ReBACのpolicy evaluatorを用意するだけでは足りない。
難しいのは、すべての業務処理が必ずauthorization enforcement pointを通り、その判断に
必要なsubject/authority/contextが処理の途中で失われないruntimeを作ることである。

一般的なapplication library方式では、handler/serviceごとに
`authorize(subject, action, resource)` を呼ぶ構造になりやすい。この方式では呼び忘れ、
対象Entity resolve前後の評価漏れ、内部service callでのcontext消失、別adapterからの迂回が
起こり得る。

CNCFではauthorizationをapplication helperではなくruntime invariantとして扱う。

```text
Ingress
  -> ExecutionContext / SecuritySubject
  -> Operation Dispatcher
  -> target/resource context
  -> mandatory authorization enforcement
       -> Capability
       -> Guard
       -> ABAC / ReBAC / ownership / relation
  -> handler
  -> EntityRealm / Repository / DataStore
```

通常の公開Operationにはauthorization enforcementを迂回してhandlerへ到達する経路を
作らない。application developerはpolicy/relationshipを宣言するが、各handlerで
authorization実行コードを書くことを標準にはしない。

### ExecutionContext propagation

認可判断にはuser identityだけでなく、tenant/organization、roles、capabilities、
delegation、authentication facts、target relation、environment/risk等が必要になり得る。

これらを個々のmethod parameterとしてapplication codeが手作業で持ち回る方式は採らず、
CNCF ExecutionContext/SecuritySubject等のruntime contextとして伝播させる。

同期callだけでなく以下の境界でもauthority provenanceを失わない設計が必要である。

- nested Operation / service call;
- Job作成とworker実行;
- Workflow / StateMachine / Continuation resume;
- Event / Service Bus delivery;
- scheduled execution;
- AI/agent等へのbounded delegation。

非同期処理では「requestを開始したuser」と「worker process identity」を混同しない。
Job/Continuation等に必要なauthority reference/snapshot/delegationをdurableに保持し、
resume時にruntimeが再構成・再評価できる契約が必要になる。

### PDPとPEP

OPA/OpenFGA/Cerbos等の外部policy/relationship engineは将来Guardのdecision provider
(PDP)として利用できる可能性がある。しかし外部PDPを導入しただけでは安全にならない。

CNCFの主要責務は、全Operationが必ずPolicy Enforcement Point (PEP)を通り、必要な
ExecutionContextとtarget informationをPDPへ供給し、denyならhandler/DataStoreへ
進ませないAuthorization Enforcement Architectureを提供することである。

したがってTextusの差別化点は独自policy languageそのものより、業務runtime全体へ
authorization enforcementとcontext propagationを埋め込めることにある。

### APK threatとの関係

攻撃者がAPKから全API、Operation名、Entity ID形式、画面外の管理機能を把握しても、
それらはauthorityを与えない。

改造clientが正規UIを完全に迂回して直接Operationを呼ぶ場合こそ、CNCF Runtimeの
mandatory PEPがSecuritySubject + Capability/Guard + target relationを評価する。

したがってAPK解析耐性の根本はclient codeを秘密にすることではなく、
**clientを完全に理解した攻撃者でも迂回できないserver-side authorization route**
を構築することである。
