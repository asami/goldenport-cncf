# APK解析・Untrusted Client Threat Modelのプロジェクト反映候補

Date: 2026-10-16
Status: proposal / journal
Source note: ../../notes/apk-analysis-untrusted-client-security.md

## 背景

Android APKは配布物であり、API endpoint、Operation、Entity ID形式、request shape等が
解析されることを前提にする必要がある。この観点は既存Textus Security Architectureの
zero-trust / Safe-by-Construction方針と整合するが、mobile/API固有のBOLA(IDOR)、
property-level authorization、API abuseを明示すると実装・Executable Specificationが
具体化できる。

本journalは各projectへの対応候補を記録する。各projectの既存Phase scopeを無条件に
拡張するものではなく、採用時は既存計画との依存・優先順位を確認する。

## CNCF

### Phase 107候補

P107-05 Authorization-before-handlerを以下まで具体化する候補:

- Untrusted Clientを明示的threat modelとして扱う。
- client supplied Entity IDごとにobject-level authorizationを強制する。
- SecuritySubject + Operation + target Entity + relation/ABAC contextを標準評価経路にする。
- property-level read/update authorizationを検討する。
- function-level authorizationをOperation/Capability/Guardでdeny-by-defaultにする。
- IDOR/BOLAを代表Executable Specificationに追加する。

P107-04 Input Bindingではowner/tenant/role/approval等のauthority-bearing propertyが
requestの追加fieldだけでmutationされないことをAcceptanceへ追加する候補。

### Entity Authorization

既存の `entity-authorization-model.md` と
`entity-authorization-implementation-note.md` をPhase 107実装時に再確認し、
relation rule / naturalConditions / SecuritySubject / OperationAccessPolicyが別々の
optional helperとして残らず、公開Operationの強制境界へ収束するか確認する。

property-level authorizationはEntity-level policyを無理に肥大化させず、
Operation Input / View projectionとの責務分担を設計する。

### API resource policy

Operation metadataからresource/rate policyを宣言し、deployment側API Gateway等へ
projectionできる可能性をnotes/design候補として検討する。汎用rate limiterのCNCF内製は
避ける。

## Cozy

Phase 83への候補:

- generated API/adapterがEntity IDを受けてもCNCF authorization boundaryを迂回しない。
- requestをpersistent Entityへ直接bindしない。
- Operation InputとView/Display projectionにproperty-level policyを反映できる生成経路。
- 管理OperationをURL/画面の非表示だけで保護しない。
- client-side validationをsecurity guaranteeとして生成物が依存しない。

CML側にauthorization metadataを追加する場合も、CNCFのcanonical modelへcompileする
形とし、Cozy独自authorization modelを作らない。

## textus-cbd-support

Phase 16への候補:

- client supplied IDからEntity/DataStoreへ到達する経路でobject authorizationが欠落する
  BOLA/IDOR候補をreview対象にする。
- Operation Inputを越えたmass assignment / authority-bearing property mutationを検出する。
- public/admin OperationのCapability/Guard欠落をreviewする。
- property-level exposure/update policyの欠落を、十分なmodel evidenceがある場合に指摘する。
- APK難読化や独自tamper checkの不足を一律blockingにしない。

静的解析だけで「IDORなし」を証明したと主張せず、CNCFのruntime invariantと
Executable Specificationを主要Assuranceとする。

## sm-workflow

Phase 6 Security Review Profileへの候補:

- mobile/API surfaceを変更したStepでは、関連するobject/function/property authorizationの
  focused security evidenceを要求可能にする。
- Phase reviewではBOLA/IDORのconnected scenarioをUsage Contract/Acceptance Boundaryに
  応じて確認する。
- client-side hardeningの追加だけでserver-side authorization findingをcloseしない。
- security reviewがAPK anti-tamper/hash machineryへ発散しないよう既存の
  overengineering constraintを維持する。

## Mobile Applications / NICT Editing Studio App

アプリ側projectへの候補:

- APK解析可能性を設計前提にする。
- server master secret、高権限共有API keyをbundleしない。
- Material / Candidate / Information / Entity IDが知られてもserver authorizationで守る。
- client validationはUX、server Operation Input validationがsecurity boundary。
- release難読化やPlay Integrity等は必要性・運用コストを評価したdefense in depth。
- Material等について「自分のID -> 他user/tenantのIDへ差し替え」のnegative testを
  server integration testに含める。

Flutter共通コードとAndroid固有hardeningを分離し、Android固有機構がdomain/API security
contractへ侵入しないようにする。

## Textus Control Center

直接のAPK防御ownerではない。将来的にAPI authorization denial、abuse/rate-limit signal、
attestation/risk signal等がCNCFのsecurity eventとして標準化された場合に、既存Security
Operationsへ可視化する候補がある。

## textus-orchestrator

直接のmobile client ownerではない。Untrusted Client原則はagent/tool clientにも類似する
ため、外部participantがOperation IDやresource IDを知っていることをauthorityとみなさない
既存least-authority方針を維持する。

## 優先順位案

最優先はCNCF Phase 107のBOLA/IDOR Executable Specificationと
authorization-before-handler invariantの具体化。

次にCozy generated surface、CBD Review、sm-workflow evidence routeを接続する。

APK難読化・attestation等のclient hardeningは、server-side authorizationが成立した後の
defense in depthとしてmobile projectごとに選択する。
