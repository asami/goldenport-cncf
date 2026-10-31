# Retired Installed Runtime Distribution

The application-specific copied runtime/UUID image binding is retired.
Normal CLI bootstrap uses the ordinary CNCF component/repository configuration.
Typed SBT runs the admitted ordinary project program and its normal dependency
resolution. Neither route requires a private installed-runtime manifest.

## Current execution boundary

- Ordinary resolved configuration, repository activation and Component admission
  continue to own CLI startup. There is no extra copied-CAR-only activation
  profile and no automatic binding to a UUID image.
- Typed Compile, TestCompile, Test, TestOnly, FixCheck and PublishLocal retain
  their existing UnitOfWork, workspace, process-grant and fixed-program
  admission. CNCF does not add an installed-manifest property,
  an installed-runtime-required property or a cozyCheckInstalledRuntime task.
  Normal project dependency resolution is used; basic SBT execution does not
  require sbt-cozy merely to satisfy a runtime-binding prerequisite.
- The retired cncf.installed-runtime.manifest and
  cncf.sbt.runtime-binding.<WorkspaceId> settings do not select an execution
  mode. No installed-to-project fallback, new reference/selection scheme,
  replacement runtime copy layer or additional resolver is introduced.
- CNCF's existing CAR/SAR managed cache, current-SNAPSHOT selection, normal
  extraction and in-use generation protection remain their own established
  contract. This change does not reimplement or remove those mechanisms.
  SBT-native locking and actual process-result retention remain unchanged.

## Historical metadata only

InstalledRuntimeDistribution retains the old metadata structure and constants
for existing evidence. Its only decoder is:

readHistoricalMetadataC(manifest: Path, text: String):
Consequence[InstalledRuntimeDistribution]

This is a pure decoder. The supplied manifest locator, copied runtime JARs,
CAR and original source files need not exist. It does not read files, archives
or links and does not create, copy, select, activate or execute anything. There
is no operational readC or admitBootstrapC compatibility path.

The decoder preserves the old v1 metadata contract: kind
cncf.installed-runtime.v1; canonical installation/update UUIDs; the original
absolute manifest locator and UUID directory spelling; application/root
coordinate; ordered repositories and unique module coordinates/relative
runtime/NNNN.jar paths; and the original component identity, coordinate,
components/textus-sm-workflow.car path and absolute source provenance.
Classifier spelling remains exact. These paths describe the original evidence,
not instructions to reconstruct the old installation. Malformed metadata is
rejected without resolving or inspecting any binary.

A historical decode does not establish current applicability, successful
execution, publication authority, recovery permission or byte identity.
Existing Session/history references and outcomes remain unchanged. Existing
runtime copies are not deleted by this contract.

Executable specifications:
org.goldenport.cncf.cli.InstalledRuntimeDistributionSpec and
org.goldenport.cncf.action.SbtExecutionDslSpec.
