# Textus Public Release Project

## Summary

This checklist coordinates the first public release wave for:

- `sbt-cozy`
- `cozy`
- `cozy-launcher`
- `textus`
- `cncf`
- `textus-knowledge-editor`
- `textus-user-account`
- `textus-user-notification`

The release is intentionally projectized because launcher/runtime/channel compatibility must be validated as one unit. Human operator steps such as credential setup, public repository upload, and final smoke approval are allowed and are called out explicitly.

## Policy

- `sbt-cozy` owns Coursier channel JSON update support.
- `cozy`, `cozy-launcher`, `textus`, and `cncf` use `cozyPublishCoursierChannel`; they must not carry ad hoc JSON merge logic in `build.sbt`.
- `cozy` and `cozy-launcher` are separate release artifacts.
  - `cozy-launcher` publishes the `cozy` channel entry.
  - `cozy` publishes the `cozy-runtime` channel entry.
- `textus` and `cncf` publish launcher entries into the Textus channel.
- Component applications publish CAR artifacts after launcher/runtime channels are valid.
- Local absolute paths and private credentials stay outside committed configuration.

## Release Graph

1. Release `sbt-cozy`.
2. Release `cozy` runtime with the released `sbt-cozy` plugin.
3. Release `cozy-launcher` with the released `sbt-cozy` plugin.
4. Release `cncf` launcher/runtime entry with the released `sbt-cozy` plugin.
5. Release `textus` launcher entry with the released `sbt-cozy` plugin.
6. Release application CARs:
   - `textus-knowledge-editor`
   - `textus-user-account`
   - `textus-user-notification`
7. Install from the public channel and run smoke checks.

## Preflight

- [ ] Choose final non-SNAPSHOT versions for every release target.
- [ ] Confirm public Maven repository credentials are present.
- [ ] Confirm warehouse target and channel publication target.
- [ ] Confirm no unplanned dirty files are present in release repos.
- [ ] Confirm the public release does not depend on local `publishLocal` artifacts.
- [ ] Save current public channel JSON files for rollback.

## sbt-cozy

- [ ] Remove SNAPSHOT version.
- [ ] Run `sbt --batch test`.
- [ ] Publish `sbt-cozy` to the public Maven repository.
- [ ] Verify the published plugin can be resolved by a clean dependent project.

## Cozy Runtime

- [ ] Update `project/plugins.sbt` to the released `sbt-cozy` version.
- [ ] Remove SNAPSHOT version.
- [ ] Run `sbt --batch test`.
- [ ] Publish `org.simplemodeling:cozy_2.12`.
- [ ] Verify `repository/cozy/coursier-channel.json` contains or preserves `cozy-runtime`.
- [ ] Verify `cozy-runtime` points to `org.simplemodeling:cozy_2.12:<release-version>`.

## Cozy Launcher

- [ ] Update `project/plugins.sbt` to the released `sbt-cozy` version.
- [ ] Remove SNAPSHOT version.
- [ ] Run `sbt --batch test`.
- [ ] Publish `org.simplemodeling:cozy-launcher_3`.
- [ ] Verify `repository/cozy/coursier-channel.json` contains or preserves `cozy`.
- [ ] Verify `cozy` points to `org.simplemodeling:cozy-launcher_3:<release-version>`.
- [ ] Confirm the channel contains both `cozy` and `cozy-runtime` entries.

## CNCF Launcher

- [ ] Update `project/plugins.sbt` to the released `sbt-cozy` version.
- [ ] Remove SNAPSHOT version.
- [ ] Run `sbt --batch test`.
- [ ] Publish `org.goldenport:cncf_3`.
- [ ] Verify `repository/textus/coursier-channel.json` contains or preserves `cncf`.
- [ ] Verify `cncf` points to `org.goldenport:cncf_3:<release-version>`.

## Textus Launcher

- [ ] Update `project/plugins.sbt` to the released `sbt-cozy` version.
- [ ] Remove SNAPSHOT version.
- [ ] Run `sbt --batch test`.
- [ ] Publish `org.goldenport:textus_3`.
- [ ] Verify `repository/textus/coursier-channel.json` contains or preserves `textus`.
- [ ] Verify `textus` points to `org.goldenport:textus_3:<release-version>`.
- [ ] Confirm the channel contains both `cncf` and `textus` entries.

## Application CARs

- [ ] Release `textus-knowledge-editor`.
  - [ ] Run `sbt --batch test`.
  - [ ] Run `sbt --batch cozyBuildCar`.
  - [ ] Publish CAR using the released Cozy/CNCF launcher stack.
- [ ] Release `textus-user-account`.
  - [ ] Run `sbt --batch test`.
  - [ ] Run CAR build/publish validation.
- [ ] Release `textus-user-notification`.
  - [ ] Run `sbt --batch test`.
  - [ ] Run CAR build/publish validation.

## Public Install Smoke

- [ ] Install `cozy` from the public channel.
- [ ] Run `cozy --help`.
- [ ] Run a Cozy operation that requires the runtime and confirm `cozy-runtime` resolves.
- [ ] Install `cncf` from the public Textus channel.
- [ ] Run `cncf --help`.
- [ ] Start TKE from published artifacts.
- [ ] Confirm TKE shell loads.
- [ ] Confirm textus-user-account login/debug-auth behavior works as configured.
- [ ] Confirm textus-user-notification basic surface starts.

## Channel Verification

- [ ] `repository/cozy/coursier-channel.json` has `cozy`.
- [ ] `repository/cozy/coursier-channel.json` has `cozy-runtime`.
- [ ] `cozy` depends on `cozy-launcher_3`.
- [ ] `cozy-runtime` depends on `cozy_2.12`.
- [ ] `repository/textus/coursier-channel.json` has `cncf`.
- [ ] `repository/textus/coursier-channel.json` has `textus`.
- [ ] No release channel entry points to a SNAPSHOT.

## Rollback

- [ ] Keep previous channel JSON files until smoke passes.
- [ ] If public install fails, restore prior channel entries first.
- [ ] If CAR install fails, leave launcher/runtime entries in place only if their standalone smoke passed.
- [ ] Record failed artifact coordinates and channel entries before retrying.

## Done Criteria

- [ ] All release artifacts are non-SNAPSHOT.
- [ ] Public channel entries resolve without local Ivy/Maven artifacts.
- [ ] `cozy`, `cncf`, and `textus` launchers start from public install.
- [ ] TKE, user-account, and user-notification published CARs start with the public launcher/runtime stack.
- [ ] Release notes list artifact coordinates, channel URLs, smoke results, and rollback location.
