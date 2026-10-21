# Installed Runtime Distribution

The installed command and typed SBT admission use one concrete installation of
the `sm-workflow` application. Installation construction and atomic selection
belong to the workstation updater; CNCF interprets its versioned manifest.

## Rules

- R1: `runtime-installation.json` has kind `cncf.installed-runtime.v1`, canonical
  UUID `installation_id` and `update_id`, application `sm-workflow`, root coordinate
  `org.goldenport:goldenport-cncf_3:0.5.3-SNAPSHOT`, ordered nonempty `repositories`
  and `modules`, and one `component`. The containing directory is the installation
  UUID, including during staged bootstrap.
- R2: Every module has a unique canonical `coordinate`, unique relative
  `runtime/NNNN.jar` path and absolute actual resolved `source` provenance.
  Coordinates are `organization:name:version` or
  `organization:name:jar:classifier:version`; classifier spelling is preserved.
  The root coordinate occurs in the module list. Order is retained.
- R3: The component identity is `org.simplemodeling.textus.SmWorkflow`, coordinate
  `org.simplemodeling.textus:textus-sm-workflow:0.1.0-SNAPSHOT`, relative path
  `components/textus-sm-workflow.car`, with absolute actual task archive `source`.
  All installed inputs are available regular files confined to the installation,
  without symbolic or hard links. Source provenance need not remain available.
  External component dependency manifests are unsupported and rejected.
- R4: The normal resolved configuration key `cncf.installed-runtime.manifest`
  contains an absolute concrete manifest filename. Bootstrap validates it before
  runtime launch, including Help. Missing configuration preserves existing
  behavior. A JVM property alone does not create this resolved binding.
  After final ordinary configuration and argument admission, the installed
  profile requires an active component-file source resolving to the pinned CAR;
  every effective active/search source must be that CAR. Additional component,
  repository, development, SAR, collaborator, factory, class-discovery and
  subsystem file/descriptor sources reject. External default repositories must
  be disabled, either explicitly or through existing isolated test-home repository
  inheritance suppression. Normal assembly-descriptor configuration remains
  supported; any activation sources it contributes receive the same admission.
- R5: Typed SBT admission first performs the existing workspace and process grant
  admission. An explicit current resolved binding derives a tagged definition
  using the same capability, executable, environment, metadata and limits. Before
  its original final task it adds fixed
  `-Dcncf.installed-runtime.manifest=<concrete filename>`,
  `-Dcncf.installed-runtime.required=true` and `cozyCheckInstalledRuntime`.
  The prerequisite fails when sbt-cozy support or its classpath mapping is absent.
  The admitted request remains empty and the effective limits remain unchanged.
  No process request override or generic execution route is introduced.
- R6: Operational binding neither changes persisted program declarations nor
  asserts historical execution facts. It grants no workflow recovery, retry,
  publication or effect authority. No selected-reference lookup, resolver fallback,
  hashes, locks, leases, pruning or automatic replay is part of CNCF admission.

Construction checks fully read copied archives and perform ordinary staged CAR
identity/ABI admission and Help before activation. Bootstrap continues to use
existing CAR admission; the manifest reader checks the bounded distribution
shape and rejects unsupported external component closure. SBT classpath
substitution and preservation of Attributed metadata belong to sbt-cozy.

Executable specifications:
`org.goldenport.cncf.cli.InstalledRuntimeDistributionSpec` and
`org.goldenport.cncf.action.SbtExecutionDslSpec`.
