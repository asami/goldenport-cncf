# CNCF Selector Resolution Specification

Status: draft

This document defines selector grammar and runtime resolution behavior for `cncf command`.

## Selector Grammar

<component>.<service>.<operation>
<component>.meta.<operation>
<component>.<service>.meta.<operation>
meta.<operation>

Examples:

domain.entity.createPerson
domain.meta.help
domain.entity.meta.operations
meta.tree

## Preprocessing

Before resolution:

1. Runtime flags are removed (`--json`, `--debug`, `--no-exit`).
2. Selector is tokenized by `.`.
3. Help aliases are normalized (`--help`, `-h`).

## Resolution Precedence

Resolution order:

1. subsystem meta
2. component meta
3. service meta
4. service operation

## Rewrite Rules

Command-layer help rewrites:

help -> meta.help
help <target> -> help <target> (meta.help delegation path)

Selector help alias rewrites:

<selector> --help -> help <selector>
<selector> -h -> help <selector>

Service meta transform:

<component>.<service>.meta.<operation>
-> <component>.meta.<operation>
   with forwarded argument: <component>.<service>

## Subsystem Meta Resolution Note

`meta.<operation>` is resolved through the default component's `meta` service
(the first component by sorted name). This is the current runtime behavior.

## Error Behavior

Resolver errors are returned as one of:

- not found (component/service/operation)
- ambiguous selector
- invalid selector

Exact message formatting may differ by call path, but the semantic categories
above are stable.
