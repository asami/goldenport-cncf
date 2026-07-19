# CBD Working Resource Tree Handoff — 2026-07-20

## Context

`textus-cbd-support` must expose `/Users/asami/src/dev2026` as the logical
resource tree `working` through the local CNCF MCP server. The CBD source is
declared with:

```text
--textus.resource.tree.file-roots=working=/Users/asami/src/dev2026
--textus.cbd.development.trees=working=working
```

The server accepts the declaration, but CBD's `working` source remains
unavailable. `listCatalogs` and a source-aware search report that the logical
tree cannot be resolved.

## Observed Cause

The current `LocalResourceTreeAccess.snapshot` constructs a complete immutable
snapshot of every regular file below the configured root. It rejects the whole
snapshot when it encounters a symbolic link and applies general entry and byte
limits to all files.

`/Users/asami/src/dev2026` is a development workspace rather than one project:

- it contains more than 127,000 files;
- it contains symbolic links, including local Maven/repository links; and
- its `project.yaml` files occur below individual project directories, not at
  the workspace root.

CBD only needs bounded `project.yaml` evidence for development-directory
observations. A complete file snapshot is therefore both too broad and unable
to represent the intended workspace use case.

## Required CNCF Direction

Add a read-only, bounded logical-tree query for named development descriptors
instead of broadening the existing full-snapshot contract. The operation must:

1. discover only entries named `project.yaml` below an explicitly admitted
   logical tree;
2. enforce explicit bounds on directory visits, descriptor count, descriptor
   byte size, aggregate bytes, and depth;
3. refuse to follow symbolic links, while skipping unrelated symlink entries
   rather than rejecting a workspace solely because one exists; and
4. expose only logical relative paths and file bytes, never the physical root.

The existing `snapshot` behavior should remain strict for consumers that need
a complete, immutable tree. The new query should be an explicit narrower
capability, usable by CBD without host-path access.

## CBD Follow-up

After CNCF exposes the bounded descriptor query:

- CBD's local development source adapter should consume all returned
  `project.yaml` entries and emit one `working` observation per project;
- `status` and `listCatalogs` should initialize their local inputs before
  projecting readiness; and
- a live MCP run using the two declarations above must show `working=ready`.

The CBD repository already contains the status/list initialization adjustment
and a launcher adjustment that places runtime configuration options before the
`server` command. The remaining blocker is the CNCF resource-tree capability.
