# Static Form Web Metadata Source and Packaging Policy

Date: 2026-05-21

## Summary

Static Form Web App の基本は、`src/main/web` に置く HTML ページと Web
resource でアプリケーションを記述することにある。`web.yaml` や
`form.yaml` はアプリケーション本体ではなく、Web app / form / admin
surface を補助する metadata である。

今回の検討で、`src/main/web/WEB-INF/form.yaml` や
`src/main/form/form.yaml` のような配置にはそれぞれ問題があることが
分かった。前者は CAR 内の `web/WEB-INF/form.yaml` にそのまま置くことを
意図しているように見え、後者は `src/main` 直下の用途分類として自然で
ない。したがって、source metadata と generated packaged metadata の
境界を先に固定する必要がある。

## Current Concern

`web/WEB-INF/form.yaml` は CAR runtime が読む packaged output としては
自然である。一方で、source 側に同じ相対位置
`src/main/web/WEB-INF/form.yaml` を置くと、source file をそのまま CAR に
コピーする設計に見える。

しかし form metadata は本来、複数の source から導出される。

- CML / generated Component metadata
- Entity schema / Operation input schema
- Entity / Operation / Value に付与された Web 向け property
- Web app 固有の override
- package / publication policy

このため、CAR 内の `web/WEB-INF/form.yaml` は source file の単純コピー
ではなく、Cozy package-car が生成する derived metadata であるべきで
ある。

## Working Separation

### Public Web App Source

`src/main/web` は Web application source tree とする。

- `src/main/web/*.html`
- `src/main/web/assets/...`

ここに置かれた通常ファイルは、CAR 内の `web/...` に public Web resource
として配置される。

### Private Web App Copy Source

`src/main/web/WEB-INF` は、Web app 内の非公開 resource をそのまま CAR 内
`web/WEB-INF` にコピーする source tree とする。

対象:

- layouts
- partials
- private template fragments
- Static Form renderer helper resources

ここに置くものは authored runtime resource であり、package 時に意味変換
しない。WAR の `WEB-INF` と同様、HTTP で直接公開しない Web app 内部
resource として扱う。

`src/main/web/WEB-INF` に置くべきでないもの:

- CML / Entity / Operation metadata から生成される runtime descriptor
- `form.yaml` の完成形
- generated `web.yaml` / `admin.yaml`

これらは source of truth ではなく generated descriptor なので、同じ
relative path に source として置くと混同を招く。

### Descriptor Generation Source

`src/main/web-inf` は、CAR 内 `web/WEB-INF/*.yaml` を生成するための
descriptor generation source とする。

正規ファイル名:

```text
src/main/web-inf/web.yaml
src/main/web-inf/form.yaml
src/main/web-inf/admin.yaml
```

ここに置く情報は「Web app resource としてそのまま公開 copy される
resource」ではなく、Cozy package-car が runtime descriptor として
`web/WEB-INF/*.yaml` に配置する入力である。v1 では CML / Entity /
Operation schema から導出した情報は、Cozy scaffold / generation 段階で
`src/main/web-inf/*.yaml` に materialize してから package する。
package-car 時点での semantic merge は後続課題とする。

`-overrides` suffix は付けない。source 側も packaged 側も同じ
`web.yaml` / `form.yaml` / `admin.yaml` という名前を使い、source input
か generated runtime descriptor かは directory path で区別する。

### Web App Metadata

`src/main/web-inf/web.yaml` は Web app の基本情報を置く候補として
妥当である。

対象:

- `apps`
- `routes`
- `pages`
- `shell`
- `theme`
- `assets`

これらは Web app の構成情報だが、CAR 内 `web/WEB-INF/web.yaml` は
package-car が配置する runtime descriptor とする。source 側では
`src/main/web-inf/web.yaml` に置き、CML / project metadata 由来の補完は
scaffold / generation 段階で反映する。

### Form Metadata

`form.yaml` は Web app source tree にそのまま置く metadata ではなく、
packaged output としての性格が強い。

理由:

- Entity CRUD form は Entity schema / Entity metadata から導かれる。
- Operation form は Operation input value / parameter schema から導かれる。
- Web 向け property は Entity / Operation / Value 側の metadata として
  持つのが筋である。
- `form.yaml` はそれらを Static Form runtime 用に集約した projection で
  あり、単独の source of truth ではない。

したがって、CAR 内の `web/WEB-INF/form.yaml` は generated output とする。
source 側で補完が必要な場合は `src/main/web-inf/form.yaml` に置く。
これは Web resource として単純 copy される file ではなく、descriptor
generation source である。CML / Entity / Operation / Value metadata 由来の
form projection は、v1 では generation 段階で `src/main/web-inf/form.yaml`
へ反映する。

### Admin Metadata

`admin.yaml` も同様に、Entity / Data / View / Aggregate / Information /
Knowledge などの runtime metadata から導出される部分が大きい。

必要になった場合、CAR 内の `web/WEB-INF/admin.yaml` は generated output
として扱う。source 側で補完が必要な場合は `src/main/web-inf/admin.yaml`
に置く。これも `form.yaml` と同様、descriptor generation source であり、
Web resource としての単純 copy 対象ではない。

## CML Relationship

CML の `# WEB` に `form` / `expose` を raw YAML として持たせるのは
bad smell である。

理由:

- `# WEB` という名前は app/page/route/shell/theme のような Web app
  metadata を想起させる。
- `form` / `expose` は Operation / Entity の Web projection であり、
  CML の raw WebDescriptor block に埋めるべきではない。
- Operation / Entity に関する Web property は、その Operation / Entity
  の metadata として持たせる方が source of truth が明確である。

方針:

- 新規生成では CML `# WEB` raw bridge を使わない。
- 既存互換として読む場合も legacy bridge と明記する。
- 将来 CML で form hint を扱う場合は、`# WEB` ではなく Entity /
  Operation / Value の property として設計する。

## Packaged CAR Output

CAR runtime が読む最終形は以下を候補とする。

```text
web/index.html
web/books.html
web/WEB-INF/web.yaml
web/WEB-INF/form.yaml
web/WEB-INF/admin.yaml
web/WEB-INF/layouts/...
web/WEB-INF/partials/...
```

ここで `web/WEB-INF/web.yaml` / `form.yaml` / `admin.yaml` は source file
copy ではなく、Cozy が生成・集約した runtime descriptor である。

一方、`web/WEB-INF/layouts/...` や `partials/...` は
`src/main/web/WEB-INF/...` からそのままコピーされた authored private
Web resources である。

## Decisions

- Source metadata root は `src/main/web-inf` とする。
- Source metadata filenames は `web.yaml` / `form.yaml` / `admin.yaml` とし、
  `-overrides` suffix は使わない。
- CAR runtime descriptor は `web/WEB-INF/web.yaml` /
  `web/WEB-INF/form.yaml` / `web/WEB-INF/admin.yaml` とする。
- CAR runtime descriptor は `src/main/web-inf/*.yaml` から生成される。
  CML / component metadata 由来の補完は scaffold / generation 段階で
  source descriptor に反映する。
- `src/main/web/WEB-INF` は private Web resource の copy source とし、
  generated descriptor source にはしない。
- `app.yaml` は導入せず、Web app 基本情報は `web.yaml` に置く。

## Remaining Questions

- CML legacy `# WEB` bridge をいつまで維持するか。
- `expose` は form metadata に含めるか、operation publication /
  authorization metadata 側へ寄せるか。
- `web.yaml` / `form.yaml` / `admin.yaml` の分割粒度が大きくなった場合、
  v2 で directory 分割を許すか。

## Provisional Implementation Note

現在の作業途中の実装には、`src/main/form/form.yaml` を source override と
して扱う案が含まれている。しかしこの配置は不採用とし、この journal では
`src/main/web-inf` を descriptor generation source として固定した。

次の実装作業では、まずこの source / generated output 境界を決めてから、
Cozy scaffold、Cozy package-car、CNCF WebDescriptor loader、
`textus-knowledge-editor` の配置を合わせる。
