# Explicit first-use workspace initialization

`cncf.runtime-process-execution.v8` opts a selected runtime process plan into
bounded host workspace initialization. V8 contains every V7 field and the
required `workspaceInitializations` array. It does not change the File DSL or
automatically opt in an existing consumer.

## Declaration and selected activation

The private catalog parameter stores an immutable
`RuntimeProcessExecutionPlanDeclaration`, without workspace bindings or process
admission. Candidate parsing, selection, declaration encoding/decoding and IO
default filling create no directories. The direct runtime-plan codec retains
V1–V7 admission behavior. Direct V8 decoding also requires existing roots and
never initializes them.

The normal `CncfRuntimeInstanceLifecyclePart._initialize` route calls
`SubsystemProcessExecutionConfiguration.admitC`. That boundary first checks
the selected Subsystem and trusted provenance: a Home, Project or Cwd file,
or Resource provenance with source type `component-car-defaults`. A different
Subsystem, command-line value, environment value or external assembly override
cannot activate provisioning. After those gates, the runtime resolves normal
IO defaults and prepares the whole declaration before any directory creation.

Preparation checks workspace IDs, grants and candidate policies, program
capabilities and safe identities, repository-family expansion and collisions,
same-owner workspace permissions, discovery/mode bindings, executables,
arguments/templates, environment and finite limits. SBT, Git and validation
checks share their normal builders' binding-independent preparation. Prepared
programs are internal, untagged data, never installed or executed. Only after
successful root admission does the runtime attach the actual nonnull
`WorkspaceBinding` and install process admission. No temporary or substitute
workspace binding participates.

## Exact V8 policy

Each initialization object has exactly these fields:

| Field | Meaning |
| --- | --- |
| `componentId` | Existing component owner of the matching candidate policy |
| `anchor` | Explicit existing absolute ordinary directory |
| `sources` | Nonempty distinct logical IDs enumerating all noninitialized workspaces |
| `parent` | Candidate-parent workspace ID |
| `alias` | Dormant candidate-alias workspace ID |
| `parentPath` | Normalized safe `WorkspaceRelativePath` below the anchor |
| `aliasPath` | Normalized safe `WorkspaceRelativePath` below the anchor |

There are at most 16 policies. Each matches exactly one
`sessionCandidateWorkspaces` owner/parent/alias policy. The same owner needs
Manage grants for parent, alias and every source. Source roots must exist and
pass directory admission. The two target declarations must equal the explicit
anchor resolved with the respective relative paths. Parent and alias are
distinct and disjoint; all initialized targets are pairwise disjoint, including
across policies. No initialized root may equal, contain or lie under any
noninitialized source root. Every policy explicitly enumerates the complete
source set; there is no source inference.

For example, `/work` and `/work/source` already exist, while `.sm-workflow`
and its two children may be absent:

```json
{
  "schema": "cncf.runtime-process-execution.v8",
  "programs": [],
  "grants": [],
  "workspaces": [
    {"identity": "source", "root": "/work/source"},
    {"identity": "candidate-parent", "root": "/work/.sm-workflow/candidates"},
    {"identity": "candidate-alias", "root": "/work/.sm-workflow/dormant"}
  ],
  "workspaceGrants": [
    {"componentId": "org.example.Workflow", "workspaceId": "source", "access": "manage"},
    {"componentId": "org.example.Workflow", "workspaceId": "candidate-parent", "access": "manage"},
    {"componentId": "org.example.Workflow", "workspaceId": "candidate-alias", "access": "manage"}
  ],
  "sbtPrograms": [],
  "gitPrograms": [],
  "gitRepositories": [],
  "validationChecks": [],
  "sessionCandidateWorkspaces": [
    {"componentId": "org.example.Workflow", "parent": "candidate-parent", "alias": "candidate-alias"}
  ],
  "workspaceInitializations": [
    {
      "componentId": "org.example.Workflow",
      "anchor": "/work",
      "sources": ["source"],
      "parent": "candidate-parent",
      "alias": "candidate-alias",
      "parentPath": ".sm-workflow/candidates",
      "aliasPath": ".sm-workflow/dormant"
    }
  ]
}
```

The anchor grants no general workspace authority. Neither `runtime.root.parent`,
the nearest existing ancestor nor a Manage grant supplies an implicit anchor
or opts a plan into V8.

## Filesystem effects and retained state

All existing anchor/prefix/source directory identities, missing target prefixes
and alias occupancy are inspected before the first effect. Initialization uses
one `Files.createDirectory` per explicitly planned prefix, never recursive
`createDirectories`. Existing directories are checked with `NOFOLLOW_LINKS`,
real-path containment and file keys. Identity and missing-path expectations are
rechecked around every creation; observed rebinding, symlinks, file occupants,
escapes or races fail closed. An existing dormant alias must be an empty
ordinary directory. Existing parent contents are retained, and repeating the
same safe initialization is permitted.

Only prefixes needed for the declared parent and alias are created. No source,
candidate repository, Git metadata, worktree or unrelated root is created.
Native Session candidate allocation, retained records, reacquisition, foreign
occupants and UNKNOWN handling keep their established behavior.

This is not an atomic filesystem transaction against arbitrary external
actors. An IO failure or observed race returns failure with no successful
runtime admission. Prefixes already created remain on disk; there is no
rollback, deletion, lock or reservation store.

V7 and earlier continue to require preexisting roots. Their established
existing-source alias compatibility remains available. Consumers must adopt
the explicit V8 policy through their own owner; this producer contract does
not establish that a consumer has been fixed or migrated.

Executable specifications: `WorkspaceFirstUseInitializationSpec`,
`SelectedCarConfigurationDefaultsSpec`, `RuntimeProcessExecutionPlanCodecSpec`,
and the unchanged `SessionCandidateWorkspaceSpec` / `WorkspaceExecutionSpec`.
