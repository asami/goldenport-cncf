# Help Selector Format Mini-Low Instruction

Status: Active Instruction

## Goal

Adjust help output so it distinguishes:

- formal model names
- runtime selectors used by CLI and REST

Do not change selector acceptance rules. This task is about help rendering and
sample documentation.

## Read First

- [/Users/asami/src/dev2025/cloud-native-component-framework/docs/journal/2026/03/help-canonical-name-and-selector-direction.md](/Users/asami/src/dev2025/cloud-native-component-framework/docs/journal/2026/03/help-canonical-name-and-selector-direction.md)
- [/Users/asami/src/dev2025/cloud-native-component-framework/src/main/scala/org/goldenport/cncf/projection/HelpProjection.scala](/Users/asami/src/dev2025/cloud-native-component-framework/src/main/scala/org/goldenport/cncf/projection/HelpProjection.scala)
- [/Users/asami/src/dev2025/cloud-native-component-framework/src/main/scala/org/goldenport/cncf/projection/MetaProjectionSupport.scala](/Users/asami/src/dev2025/cloud-native-component-framework/src/main/scala/org/goldenport/cncf/projection/MetaProjectionSupport.scala)
- [/Users/asami/src/dev2025/cloud-native-component-framework/src/main/scala/org/goldenport/cncf/naming/NamingConventions.scala](/Users/asami/src/dev2025/cloud-native-component-framework/src/main/scala/org/goldenport/cncf/naming/NamingConventions.scala)
- [/Users/asami/src/dev2026/cncf-samples/samples/02.a-crud-seed-import-lab/README.md](/Users/asami/src/dev2026/cncf-samples/samples/02.a-crud-seed-import-lab/README.md)

## Required Outcome

For operation help, formal name and runtime selectors are shown separately.

Target shape:

```yaml
type: operation
name: searchItemRecord
component: Crud
service: Entity
selector:
  canonical: Crud.Entity.searchItemRecord
  cli: crud.entity.search-item-record
  rest: /crud/entity/search-item-record
usage:
  - command crud.entity.search-item-record
```

Exact field names may vary slightly, but the meaning must match.

## Work Steps

1. Update help projection so `name` means the formal model name.
2. Add separate selector information for CLI/REST addressing.
3. Make `usage` prefer kebab-case selector examples.
4. Keep current selector resolution behavior unchanged.
5. Update at least one sample README to follow the new help style.

## Do Not

- Do not change resolver matching rules unless a concrete bug is found.
- Do not rename model-level component/service/operation names.
- Do not remove compatibility acceptance of mixed-case selectors.
- Do not rewrite all samples in one pass.

## Minimum Verification

Verify at least one help command such as:

```bash
sbt --batch "runMain org.goldenport.cncf.CncfMain --discover=classes command help crud.entity.search-item-record"
```

and confirm:

- formal name is still visible
- CLI kebab-case selector is shown explicitly
- `usage` prefers kebab-case

## Report Back Only

- what files you changed
- how help output changed
- what command you used to verify it
- what still remains, if anything
