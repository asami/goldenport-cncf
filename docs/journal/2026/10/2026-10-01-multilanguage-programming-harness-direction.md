# CNCF 多言語 Programming Harness の方向性

日付: 2026-10-01
状態: 設計方向 / 将来拡張メモ

## 背景

AI駆動開発ではProgramの主要な作成主体がAIになるため、Programming Language選定では人間の好みや記述の容易さだけでなく、必要な意味を表現できる記述力と、AIが許可された実装空間から逸脱したときに機械的に拒否できるHarness Strengthを重視する。

CNCFはJVM上で動作し、現在のCozyはCMLからScalaコードを生成する。このScala生成経路は今後もcanonicalなmodel-down / executable representationとして活用する。

## 当面の正式対象

当面はScalaとJavaを正式なProgramming Language対象とする。

### Scala

Scalaでは二つのProgramming Profileを利用できる。

- Monadic DSL Profile: pure functional programming、ADT、Algebra、Free Monad、Interpreterを利用する。Textus/CNCFのreference / strongest harness profileとする。
- Procedural DSL Profile: CNCFが提供する手続き版DSLを利用する。Scalaで手続き的な記述を望む場合の正式な選択肢とする。

### Java

Javaは最初から正式ターゲットとする。JavaからはCNCFのProcedural DSLを利用する。

JavaにFree Monadを要求しない。nominal typing、JVM、CNCF Component/Operation境界、Procedural DSL、Executable Specification、Admissionを組み合わせてProgramming Harnessを構成する。

重要なのは、Monadic DSLとProcedural DSLが別の意味論を持つことではなく、可能な限り同じCNCF Algebra / Component Modelが定める操作空間を異なるProgramming Styleへ投影することである。

```text
CML / Component Model
        |
      Algebra
        |
   +----+----------------+
   |                     |
Monadic DSL          Procedural DSL
   |                     |
Scala / Free Monad    Scala / Java
   |                     |
Interpreter               |
   +----------+-----------+
              |
         CNCF Runtime
```

## Cozy生成コードの位置付け

Cozyを各言語向けの完全なcode generatorへ拡張することを当面の前提にしない。

```text
CML
 |
Cozy
 |
Canonical Scala Generation
 |
CNCF Runtime / Component Contract
 |
+----------------+----------------+
|                |                |
JVM Binding   Polyglot Adapter   External Protocol
|                |                |
Java          future languages  external services
```

CMLの意味論を各言語generatorへ重複実装するより、現在のScala生成コードをcanonical representationとして維持し、Javaや将来言語はbinding / adapterから利用する構成を優先する。

必要なら他言語向けには型定義、schema、client/facadeなどの薄いlanguage bindingを生成することはあり得るが、canonical model-down semanticsはScala/CNCF側に保持する。

## 将来のPython / TypeScript

PythonとTypeScriptは現時点では正式実装対象にしない。ただし案件要件で指定される可能性が高いため、将来拡張を妨げない境界を維持する。

### Python

Pythonには少なくとも二つの方向がある。

1. Managed Python
   - GraalPy等をJVM applicationへembedする。
   - restricted Polyglot ContextからCNCF Procedural DSL / Capabilityだけを公開する。
   - 「Python言語を使うこと」が要求の場合の候補。
2. External CPython
   - NumPy、pandas、PyTorch等のCPython ecosystemが要求される場合。
   - process/service boundaryを置き、Protocol / Schema / Executable Specification / Admission / OS or container isolationでHarnessを構成する。

実装方式は現時点で固定しない。

### TypeScript

TypeScriptはTypeScript compilerによるstatic checkの後、JavaScriptへ変換し、将来的にはGraalJSをJVM applicationへembedするManaged Profileを検討できる。

```text
TypeScript
   |
TS compiler / restricted TS profile
   |
JavaScript
   |
GraalJS / restricted Polyglot Context
   |
CNCF Procedural DSL
```

TypeScriptのstructural typing、type assertion (`as`)、`any`、過度なtype-level programmingはAIが型エラーを意味的に修正せず回避する経路になり得るため、Managed Profileを設計する場合はlint / admission等の追加Harnessを前提とする。

## その他の言語

将来候補として次の実行経路を意識する。

- JVM native: Kotlin、Clojure等。JVM bytecodeとしてCNCFへ直接統合可能。
- Graal Polyglot: Ruby / TruffleRuby等。
- LLVM / Sulong: Rust、C/C++等のLLVM bitcode経路。ただしLLVM自体はsandboxではない。
- WebAssembly: 将来のlanguage-neutralなmanaged execution boundary候補。限定されたimports / capabilitiesをCNCFへ接続できる可能性がある。
- External Protocol: .NETその他、任意の外部runtimeをREST / MCP / Event等で接続する最終的な汎用経路。

これらは可能性の整理であり、現時点の実装計画ではない。

## CNCF nativeの意味

将来の「CNCF native」は「JVM bytecodeで直接実装されること」だけに限定しない。

CNCFの管理された実行境界の内側で、CNCF Component Contract、Capability、Operation、Failure Model、Executable Specification、Evidence / Observability、Admissionの意味論に従って実行できるものをManaged Language Profileとして扱える可能性がある。

ただしLanguage / Execution ProfileごとにHarness Strengthは異なる。対応言語であることと同じ保証強度を持つことを混同しない。

概念的には次で評価する。

```text
Language intrinsic harness
        +
Programming Profile
        +
Execution Profile
        +
CNCF / Textus constraints
        =
Effective Harness Strength
```

## 設計原則

1. Scala + Monadic DSLをreference / strongest Programming Harnessとして維持する。
2. Java + Procedural DSLを最初から正式ターゲットにする。
3. ScalaからProcedural DSLを利用することも正式に認める。
4. Monadic / Proceduralの両DSLは可能な限り同じAlgebra / Component semanticsを共有する。
5. CozyのScala生成をcanonical model-down pathとして維持する。
6. 多言語化のためにCMLの意味論を各言語generatorへ重複させない。
7. CML、Component Contract、Capability、Operation、Failure、Workflow / State Machine、Executable Specification、Evidence、Admission、Model Upを特定言語へ不要に固定しない。
8. Python / TypeScriptは当面の実装対象ではないが、Graal Polyglotや外部Protocolによる将来統合を阻害しない。
9. 対応可否だけでなくLanguage / Programming / Execution ProfileごとのHarness Strengthを明示する。
10. 多言語で実装されても、最終的なEvidenceをModel Upし、Use Caseからのtraceabilityを含む同じモデル空間でreviewできることを目標とする。

## AI開発との関係

多言語化の目的は、人間の好みごとに別々の開発方法を作ることではない。AIが実装に利用する言語が異なっても、モデル、実装境界、検証、実行時Evidence、Model Up、Human Reviewを同じTextus/CNCF開発ループへ載せることである。

```text
Use Case / CML
      |
Component Model
      |
AI implementation
      |
Language-specific Harness Profile
      |
Executable Specification
      |
CNCF Runtime / Admission
      |
Runtime Evidence
      |
Model Up
      |
CBD Support / Traceability
      |
Human Review
```

人間が各言語のProgramを通常の主レビュー対象にするのではなく、低い抽象度はProgramming Harnessで機械的に検査し、Model Upされた高い抽象度とtraceabilityをレビューする方針と整合させる。
