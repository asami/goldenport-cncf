package org.goldenport.cncf.config

import java.nio.file.{Files, Path, Paths}
import java.util.Comparator

import org.goldenport.Consequence
import org.goldenport.configuration.{Configuration, ConfigurationBindingCandidates, ConfigurationBindingResolver, ConfigurationOrigin, ConfigurationResolver, ConfigurationValue}
import org.goldenport.configuration.source.{ConfigurationSource, ResourceConfigurationSource}
import org.goldenport.configuration.source.file.FileConfigLoader
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Aug.  3, 2026
 * @version Aug.  4, 2026
 * @author  ASAMI, Tomoharu
 */
final class CncfRuntimeConfigurationProjectionSpec
  extends AnyWordSpec
    with Matchers
    with GivenWhenThen {
  private val _e1 = afterWord(
    "in spec:config-resolution, example:E1, rules:R3, phase:55, slice:GCF-07C"
  )
  private val _e2 = afterWord(
    "in spec:config-resolution, example:E2, rules:R4, phase:55, slice:GCF-07F"
  )
  private val _e3 = afterWord(
    "in spec:config-resolution, example:E3, rules:R5, phase:55, slice:GCF-07H"
  )
  private val _e4 = afterWord(
    "in spec:config-resolution, example:E4, rules:R4, phase:55, slice:GCF-08F"
  )
  private val _e5 = afterWord(
    "in spec:config-resolution, example:E5, rules:R4, phase:55, slice:GCF-08G"
  )
  private val _e6 = afterWord(
    "in spec:config-resolution, example:E6, rules:R2,R3,R6, phase:55, slice:GCF-08H"
  )
  private val _e7 = afterWord(
    "in spec:config-resolution, example:E7, rules:R1,R6, phase:55, slice:GCF-08H"
  )
  private val _e8 = afterWord(
    "in spec:config-resolution, example:E8, rules:R7, phase:55, slice:GCF-08I"
  )
  private val _e9 = afterWord(
    "in spec:config-resolution, example:E9, rules:R8, phase:55, slice:GCF-08I"
  )
  private val _e10 = afterWord(
    "in spec:config-resolution, example:E10, rules:R9, phase:55, slice:GCF-08I"
  )
  private val _e11 = afterWord(
    "in spec:config-resolution, example:E11, rules:R10, phase:55, slice:GCF-08L"
  )
  private val _e12 = afterWord(
    "in spec:config-resolution, example:E12, rules:R11, phase:55, slice:GCF-10A"
  )

  "CNCF runtime configuration projection" should {
    "E1 resolve catalog candidates from the same single-load runtime snapshots" must _e1 {
      "when baseline and override file sources supply a Subsystem user-mode" in {
        Given("Spec: docs/spec/config-resolution.md; Rules: R3; Example: E1; counting Textus baseline and CNCF override sources for one stable Subsystem identity")
        var textusloads = 0
        var cncfsloads = 0
        val textus = _file_source(".textus/config.conf", ConfigurationSource.Rank.Home, "standalone", () => textusloads += 1)
        val cncf = _file_source(".cncf/config.conf", ConfigurationSource.Rank.Home + 1, "multi-user", () => cncfsloads += 1)
        val snapshot = _take(ConfigurationResolver.default.resolveSnapshot(Vector(textus, cncf)))
        val subsystem = _take(SubsystemInstanceId.create("platform", "default"))

        When("the catalog projects the already-loaded source snapshots")
        val result = _take(CncfRuntimeConfigurationProjection.subsystemUserMode(snapshot, subsystem))

        Then("no physical source reload occurs and the higher-precedence CNCF value retains the Textus override history")
        textusloads shouldBe 1
        cncfsloads shouldBe 1
        result.map(_.value) shouldBe Some(org.goldenport.cncf.subsystem.SubsystemUserMode.MultiUser)
        result.flatMap(_.overridden).map(_.value) shouldBe Some(org.goldenport.cncf.subsystem.SubsystemUserMode.Standalone)
      }
    }

    "E2 compose retained runtime and already-admitted HOME profile candidates before final resolution" must _e2 {
      "when a fixed standalone Subsystem has one runtime user-mode and both HOME profile layers" in {
        Given("Spec: docs/spec/config-resolution.md; Rules: R4; Example: E2; one loaded runtime source, one stable Subsystem identity, and parsed HOME profile documents")
        val loads = scala.collection.mutable.ArrayBuffer.empty[String]
        val runtime = _file_source(".textus/config.conf", ConfigurationSource.Rank.Home, "standalone", () => loads += "runtime")
        val snapshot = _take(ConfigurationResolver.default.resolveSnapshot(Vector(runtime)))
        val subsystem = _take(SubsystemInstanceId.create("platform", "default"))
        val profiles = Vector(
          StandaloneUserProfileResolver.Admitted(
            StandaloneUserProfileResolver.Layer.TextusHome,
            Path.of("gcf07f-home/.textus/user-profile.yaml"),
            ConfigurationOrigin.Home,
            StandaloneUserProfile.Document(Some(StandaloneUserProfile.User(id = Some("textus"))))
          ),
          StandaloneUserProfileResolver.Admitted(
            StandaloneUserProfileResolver.Layer.CncfHome,
            Path.of("gcf07f-home/.cncf/user-profile.yaml"),
            ConfigurationOrigin.Home,
            StandaloneUserProfile.Document(Some(StandaloneUserProfile.User(displayName = Some("CNCF"))))
          )
        )

        When("the two candidate groups are combined before the generic resolver runs")
        val runtimecandidates = _take(CncfRuntimeConfigurationProjection.candidates(snapshot, subsystem))
        val profilecandidates = _take(StandaloneUserProfileBindingProjection.candidates(profiles, subsystem))
        val candidates = _take(ConfigurationBindingCandidates.from(runtimecandidates.bindings ++ profilecandidates.bindings))
        val context = _take(CncfConfigurationResolutionContext.forSubsystem(subsystem))
        val collection = _take(ConfigurationBindingResolver.resolve(candidates, context.generic))

        Then("the final collection retains the exact runtime witness and typed HOME profile witnesses without reload")
        loads.toVector shouldBe Vector("runtime")
        collection.value(CncfConfigurationParameterCatalog.subsystemUserMode).toOption.flatten shouldBe Some(org.goldenport.cncf.subsystem.SubsystemUserMode.Standalone)
        collection.value(CncfConfigurationParameterCatalog.fixedUserId).toOption.flatten shouldBe Some("textus")
        collection.value(CncfConfigurationParameterCatalog.fixedUserDisplayName).toOption.flatten shouldBe Some("CNCF")
      }
    }

    "E3 accept native boolean runtime values and retain their higher-precedence winner" must _e3 {
      "when a project boolean overrides an assembly-default-compatible lower source" in {
        Given("Spec: docs/spec/config-resolution.md; Rules: R5; Example: E3; two loaded runtime sources whose selected Web boolean is native rather than text")
        val low = _source(
          ".textus/config.conf",
          ConfigurationSource.Rank.Home,
          Map(CncfConfigurationParameterCatalog.WEB_EXECUTION_DISPLAY_OVERRIDE_ENABLED_KEY -> ConfigurationValue.BooleanValue(false))
        )
        val high = _source(
          ".cncf/config.yaml",
          ConfigurationSource.Rank.Project,
          Map(CncfConfigurationParameterCatalog.WEB_EXECUTION_DISPLAY_OVERRIDE_ENABLED_KEY -> ConfigurationValue.BooleanValue(true))
        )
        val snapshot = _take(ConfigurationResolver.default.resolveSnapshot(Vector(low, high)))
        val subsystem = _take(SubsystemInstanceId.create("platform", "default"))

        When("the closed catalog projects and resolves the retained snapshots")
        val collection = _take(CncfRuntimeConfigurationProjection.forSubsystem(snapshot, subsystem))
        val binding = _take(collection.binding(CncfConfigurationParameterCatalog.webExecutionDisplayOverrideEnabled)).getOrElse(fail("boolean binding is required"))

        Then("the native boolean decodes and the higher-precedence source remains the winner")
        binding.value shouldBe true
        binding.provenance.origin shouldBe ConfigurationOrigin.Project
        binding.overridden.map(_.value) shouldBe Some(false)
      }
    }

    "E4 attach typed argv bindings to the one existing argument source batch" must _e4 {
      "when direct and typed argv values name the same or different Subsystem targets" in {
        Given("Spec: docs/spec/config-resolution.md; Rules: R4; Example: E4; one argv runtime snapshot and canonical command binding envelopes")
        val source = ConfigurationSource.Args(Map(
          "textus.subsystem.user-mode" -> "multi-user"
        ))
        val snapshot = _take(ConfigurationResolver.default.resolveSnapshot(Vector(source)))
        val subsystem = _take(SubsystemInstanceId.create("platform", "default"))
        val codec = _take(CncfConfigurationArgumentBindingCodec.create(CncfConfigurationParameterCatalog.closed))
        val duplicate = _take(codec.admit(Vector(
          "--textus.binding=@s/platform/default:textus.subsystem.user-mode=standalone"
        ))).assignments
        val distinct = _take(codec.admit(Vector(
          "--textus.binding=@s/platform/other:textus.subsystem.user-mode=standalone"
        ))).assignments

        When("the projection constructs catalog candidates from the retained argv snapshot")
        val duplicateresult = CncfRuntimeConfigurationProjection.candidates(snapshot, subsystem, duplicate)
        val distinctresult = _take(CncfRuntimeConfigurationProjection.candidates(snapshot, subsystem, distinct))

        Then("same-source canonical collisions fail, while a distinct target retains the shared argv provenance")
        duplicateresult.isSuccess shouldBe false
        distinctresult.bindings.map(_.provenance.sourceIdentity).distinct shouldBe Vector("arguments-0")
        distinctresult.bindings.map(_.provenance.sourceOrdinal).distinct shouldBe Vector(0)
        distinctresult.bindings.map(_.provenance.sourceType).distinct shouldBe Vector(Some("arguments"))
      }
    }

    "E5 attach typed environment bindings to the one existing environment source batch" must _e5 {
      "when direct and typed environment values name the same or different Subsystem targets" in {
        Given("Spec: docs/spec/config-resolution.md; Rules: R4; Example: E5; one residual environment snapshot and canonical environment binding names")
        val codec = _take(CncfConfigurationEnvironmentBindingCodec.create(CncfConfigurationParameterCatalog.closed))
        val subsystem = _take(SubsystemInstanceId.create("platform", "default"))
        val target = _take(CncfConfigurationTarget.SubsystemInstance.create(subsystem))
        val duplicatename = _take(codec.encode(_take(org.goldenport.configuration.ConfigurationBindingReference.create(
          CncfConfigurationParameterCatalog.subsystemUserMode.id,
          target
        ))))
        val distinctname = _take(codec.encode(_take(org.goldenport.configuration.ConfigurationBindingReference.create(
          CncfConfigurationParameterCatalog.subsystemUserMode.id,
          _take(CncfConfigurationTarget.SubsystemInstance.create(_take(SubsystemInstanceId.create("platform", "other"))))
        ))))
        val duplicateadmission = _take(CncfConfigurationEnvironmentBindingAdmission.admit(Map(
          "TEXTUS_SUBSYSTEM_USER-MODE" -> "multi-user",
          duplicatename -> "standalone"
        )))
        val distinctadmission = _take(CncfConfigurationEnvironmentBindingAdmission.admit(Map(
          "TEXTUS_SUBSYSTEM_USER-MODE" -> "multi-user",
          distinctname -> "standalone"
        )))
        val duplicatesnapshot = _take(ConfigurationResolver.default.resolveSnapshot(Vector(
          ConfigurationSource.env(duplicateadmission.residualEnvironment, "textus").get
        )))
        val distinctsnapshot = _take(ConfigurationResolver.default.resolveSnapshot(Vector(
          ConfigurationSource.env(distinctadmission.residualEnvironment, "textus").get
        )))

        When("the projection adds admitted bindings to the retained environment source")
        val duplicateresult = CncfRuntimeConfigurationProjection.candidates(
          duplicatesnapshot,
          subsystem,
          Vector.empty,
          duplicateadmission.assignments
        )
        val distinctresult = _take(CncfRuntimeConfigurationProjection.candidates(
          distinctsnapshot,
          subsystem,
          Vector.empty,
          distinctadmission.assignments
        ))

        Then("same-source canonical collisions fail while distinct targets retain environment provenance")
        duplicateresult.isSuccess shouldBe false
        distinctresult.bindings.map(_.provenance.origin).distinct shouldBe Vector(ConfigurationOrigin.Environment)
        distinctresult.bindings.map(_.provenance.sourceType).distinct shouldBe Vector(Some("environment"))
        distinctresult.bindings.map(_.provenance.sourceRank).distinct shouldBe Vector(ConfigurationSource.Rank.Environment)
        distinctresult.bindings.map(_.provenance.sourceOrdinal).distinct shouldBe Vector(0)
      }
    }

    "E6 decode one already-loaded consolidated file document into Global and selected Subsystem bindings" must _e6 {
      "when a canonical YAML-shaped configuration tree supplies one Global and two Subsystem values" in {
        Given("Spec: docs/spec/config-resolution.md; Rules: R2,R3,R6; Example: E6; a counting file source retaining the consolidated global/subsystems hierarchy")
        var loads = 0
        val source = ConfigurationSource.File(
          ConfigurationOrigin.Home,
          Paths.get(".textus/config.yaml"),
          ConfigurationSource.Rank.Home,
          new FileConfigLoader {
            override def load(path: java.nio.file.Path): Consequence[Configuration] = {
              loads += 1
              Consequence.success(Configuration(Map(
                "global" -> ConfigurationValue.ObjectValue(Map(
                  "config" -> ConfigurationValue.ObjectValue(Map(
                    CncfConfigurationParameterCatalog.REPOSITORY_DIR_KEY -> ConfigurationValue.StringValue("global-repository")
                  ))
                )),
                "subsystems" -> ConfigurationValue.ObjectValue(Map(
                  "platform" -> ConfigurationValue.ObjectValue(Map(
                    "instances" -> ConfigurationValue.ObjectValue(Map(
                      "default" -> ConfigurationValue.ObjectValue(Map(
                        "config" -> ConfigurationValue.ObjectValue(Map(
                          org.goldenport.cncf.subsystem.SubsystemUserMode.CONFIGURATION_KEY -> ConfigurationValue.StringValue("standalone")
                        ))
                      )),
                      "other" -> ConfigurationValue.ObjectValue(Map(
                        "config" -> ConfigurationValue.ObjectValue(Map(
                          org.goldenport.cncf.subsystem.SubsystemUserMode.CONFIGURATION_KEY -> ConfigurationValue.StringValue("multi-user")
                        ))
                      ))
                    ))
                  ))
                ))
              )))
            }
          }
        )
        val snapshot = _take(ConfigurationResolver.default.resolveSnapshot(Vector(source)))
        val subsystem = _take(SubsystemInstanceId.create("platform", "default"))

        When("the runtime projection reuses the source snapshot without reloading the file")
        val collection = _take(CncfRuntimeConfigurationProjection.forSubsystem(snapshot, subsystem))

        Then("the Global value and selected Subsystem value are typed with their original file provenance")
        loads shouldBe 1
        collection.value(CncfConfigurationParameterCatalog.repositoryDir).toOption.flatten shouldBe Some(Vector("global-repository"))
        collection.value(CncfConfigurationParameterCatalog.subsystemUserMode).toOption.flatten shouldBe Some(org.goldenport.cncf.subsystem.SubsystemUserMode.Standalone)
        val binding = _take(collection.binding(CncfConfigurationParameterCatalog.subsystemUserMode)).getOrElse(fail("Subsystem binding is required"))
        binding.provenance.sourceType shouldBe Some("file")
        binding.provenance.sourceIdentity shouldBe ".textus/config.yaml"
      }
    }

    "E7 reserve consolidated hierarchy interpretation for file snapshots" must _e7 {
      "when resource and argument sources contain global-shaped values" in {
        Given("Spec: docs/spec/config-resolution.md; Rules: R1,R6; Example: E7; a hierarchical non-file resource source and an ordinary global argument key")
        _with_fixture_root("gcf08h-resource") { workroot =>
          val resourcepath = Files.createTempFile(workroot, "resource", ".yaml")
          Files.writeString(
            resourcepath,
            s"""global:
               |  config:
               |    ${CncfConfigurationParameterCatalog.REPOSITORY_DIR_KEY}: must-not-be-admitted
               |""".stripMargin
          )
          val resource = ResourceConfigurationSource("gcf08h-resource.yaml", resourcepath.toUri.toURL)
          val arguments = ConfigurationSource.Args(Map("global" -> "not-a-consolidated-document"))
          val snapshot = _take(ConfigurationResolver.default.resolveSnapshot(Vector(resource, arguments)))
          val subsystem = _take(SubsystemInstanceId.create("platform", "default"))

          When("the runtime projection preserves non-file sources as their existing flat boundary")
          val collection = _take(CncfRuntimeConfigurationProjection.forSubsystem(snapshot, subsystem))

          Then("neither non-file source gains consolidated target semantics")
          collection.value(CncfConfigurationParameterCatalog.repositoryDir).toOption.flatten shouldBe None
          collection.value(CncfConfigurationParameterCatalog.subsystemUserMode).toOption.flatten shouldBe None
        }
      }
    }

    "E8 reject same-layer canonical duplicates across consolidated and split file forms" must _e8 {
      "when both physical forms define one Subsystem parameter for the same target" in {
        Given("Spec: docs/spec/config-resolution.md; Rules: R7; Example: E8; one consolidated Textus YAML source and one canonical split Subsystem source at the same rank")
        val subsystem = _take(SubsystemInstanceId.create("platform", "default"))
        val consolidated = ConfigurationSource.File(
          ConfigurationOrigin.Home,
          Paths.get(".textus/config.yaml"),
          ConfigurationSource.Rank.Home,
          new FileConfigLoader {
            override def load(path: java.nio.file.Path): Consequence[Configuration] =
              Consequence.success(Configuration(Map(
                "subsystems" -> ConfigurationValue.ObjectValue(Map(
                  "platform" -> ConfigurationValue.ObjectValue(Map(
                    "instances" -> ConfigurationValue.ObjectValue(Map(
                      "default" -> ConfigurationValue.ObjectValue(Map(
                        "config" -> ConfigurationValue.ObjectValue(Map(
                          org.goldenport.cncf.subsystem.SubsystemUserMode.CONFIGURATION_KEY -> ConfigurationValue.StringValue("standalone")
                        ))
                      ))
                    ))
                  ))
                ))
              )))
          }
        )
        val split = _file_source(
          ".textus/subsystems/platform/instances/default/config.yaml",
          ConfigurationSource.Rank.Home,
          "multi-user",
          () => ()
        )
        val snapshot = _take(ConfigurationResolver.default.resolveSnapshot(Vector(consolidated, split)))

        When("the runtime projection builds one candidate graph from both physical file snapshots")
        val result = CncfRuntimeConfigurationProjection.candidates(snapshot, subsystem)

        Then("the same layer and canonical target are a structural duplicate, not an implicit override")
        result.isSuccess shouldBe false
      }
    }

    "E9 retain normal CNCF override semantics beside a canonical Textus consolidated file" must _e9 {
      "when equally-ranked Textus and CNCF consolidated files set one Subsystem parameter" in {
        Given("Spec: docs/spec/config-resolution.md; Rules: R8; Example: E9; a canonical Textus consolidated file and a same-rank CNCF consolidated file")
        val subsystem = _take(SubsystemInstanceId.create("platform", "default"))
        val textus = _consolidated_subsystem_source(
          ".textus/config.yaml",
          ConfigurationSource.Rank.Home,
          "standalone"
        )
        val cncf = _consolidated_subsystem_source(
          ".cncf/config.yaml",
          ConfigurationSource.Rank.Home,
          "multi-user"
        )
        val snapshot = _take(ConfigurationResolver.default.resolveSnapshot(Vector(textus, cncf)))

        When("the runtime projection resolves the conventional Textus to CNCF override")
        val collection = _take(CncfRuntimeConfigurationProjection.forSubsystem(snapshot, subsystem))

        Then("CNCF wins normally instead of entering the canonical split collision domain")
        collection.value(CncfConfigurationParameterCatalog.subsystemUserMode).toOption.flatten shouldBe Some(org.goldenport.cncf.subsystem.SubsystemUserMode.MultiUser)
        collection.binding(CncfConfigurationParameterCatalog.subsystemUserMode).toOption.flatten.flatMap(_.overridden).map(_.value) shouldBe Some(org.goldenport.cncf.subsystem.SubsystemUserMode.Standalone)
      }
    }

    "E10 keep a canonical split file path-bound when its content looks consolidated" must _e10 {
      "when a split Subsystem file contains both its flat value and a foreign hierarchy" in {
        Given("Spec: docs/spec/config-resolution.md; Rules: R9; Example: E10; a canonical split path whose flat value targets default while hierarchy names another target")
        val subsystem = _take(SubsystemInstanceId.create("platform", "default"))
        val split = _source(
          ".textus/subsystems/platform/instances/default/config.yaml",
          ConfigurationSource.Rank.Home,
          Map(
            org.goldenport.cncf.subsystem.SubsystemUserMode.CONFIGURATION_KEY -> ConfigurationValue.StringValue("standalone"),
            "subsystems" -> ConfigurationValue.ObjectValue(Map(
              "platform" -> ConfigurationValue.ObjectValue(Map(
                "instances" -> ConfigurationValue.ObjectValue(Map(
                  "default" -> ConfigurationValue.ObjectValue(Map(
                    "config" -> ConfigurationValue.ObjectValue(Map(
                      org.goldenport.cncf.subsystem.SubsystemUserMode.CONFIGURATION_KEY -> ConfigurationValue.StringValue("multi-user")
                    ))
                  ))
                ))
              ))
            ))
          )
        )
        val snapshot = _take(ConfigurationResolver.default.resolveSnapshot(Vector(split)))

        When("the projection decodes the reserved split path")
        val collection = _take(CncfRuntimeConfigurationProjection.forSubsystem(snapshot, subsystem))

        Then("only the path-bound flat value is admitted and the hierarchy cannot retarget it")
        collection.value(CncfConfigurationParameterCatalog.subsystemUserMode).toOption.flatten shouldBe Some(org.goldenport.cncf.subsystem.SubsystemUserMode.Standalone)
      }
    }

    "E11 reject duplicate canonical bindings from one retained raw YAML split-file document" must _e11 {
      "when the same binding member occurs twice in the physical file" in {
        Given("Spec: docs/spec/config-resolution.md; Rules: R10; Example: E11; a real canonical split YAML file with two user-mode members")
        _with_fixture_root("cncf-gcf08l-raw-yaml") { workroot =>
          val path = workroot.resolve(".textus/subsystems/default/instances/default/config.yaml")
          Files.createDirectories(path.getParent)
          Files.writeString(
            path,
            """textus:
              |  subsystem:
              |    user-mode: standalone
              |    user-mode: multi-user
              |""".stripMargin
          )
          val source = ConfigurationSource.File(
            ConfigurationOrigin.Home,
            path,
            ConfigurationSource.Rank.Home,
            new RuntimeFileConfigLoader
          )
          val snapshot = _take(ConfigurationResolver.default.resolveSnapshot(Vector(source)))
          val subsystem = _take(SubsystemInstanceId.create("default", "default"))

          When("the CNCF projection builds candidates without reloading the file")
          val result = CncfRuntimeConfigurationProjection.candidates(snapshot, subsystem)

          Then("duplicate canonical binding admission fails structurally rather than silently selecting one")
          result.isSuccess shouldBe false
          result.display.toLowerCase(java.util.Locale.ROOT) should include ("duplicate")
          snapshot.sources.head.rawDocument.map(_.fields.map(_.name)) shouldBe Some(Vector("textus"))
        }
      }
    }

    "E12 handle an empty runtime source snapshot gracefully at the CNCF projection boundary" must _e12 {
      "when no optional configuration sources are provided for a valid Subsystem instance" in {
        Given("Spec: docs/spec/config-resolution.md; Rules: R11; Example: E12; an empty runtime source snapshot and one valid Subsystem identity")
        val snapshot = _take(ConfigurationResolver.default.resolveSnapshot(Vector.empty[ConfigurationSource]))
        val subsystem = _take(SubsystemInstanceId.create("platform", "default"))

        When("the CNCF projection resolves the empty snapshot for that Subsystem")
        val collection = _take(CncfRuntimeConfigurationProjection.forSubsystem(snapshot, subsystem))
        val binding = _take(collection.binding(CncfConfigurationParameterCatalog.subsystemUserMode))
        val value = _take(collection.value(CncfConfigurationParameterCatalog.subsystemUserMode))

        Then("the source snapshot and typed projection remain empty without a subsystem user-mode binding or value")
        snapshot.sources shouldBe Vector.empty
        binding shouldBe None
        value shouldBe None
      }
    }
  }

  private def _consolidated_subsystem_source(
    path: String,
    rank: Int,
    value: String
  ): ConfigurationSource.File =
    _source(
      path,
      rank,
      Map(
        "subsystems" -> ConfigurationValue.ObjectValue(Map(
          "platform" -> ConfigurationValue.ObjectValue(Map(
            "instances" -> ConfigurationValue.ObjectValue(Map(
              "default" -> ConfigurationValue.ObjectValue(Map(
                "config" -> ConfigurationValue.ObjectValue(Map(
                  org.goldenport.cncf.subsystem.SubsystemUserMode.CONFIGURATION_KEY -> ConfigurationValue.StringValue(value)
                ))
              ))
            ))
          ))
        ))
      )
    )

  private def _file_source(
    path: String,
    rank: Int,
    value: String,
    count: () => Unit
  ): ConfigurationSource.File =
    ConfigurationSource.File(
      ConfigurationOrigin.Home,
      Paths.get(path),
      rank,
      new FileConfigLoader {
        override def load(path: java.nio.file.Path): Consequence[Configuration] = {
          count()
          Consequence.success(Configuration(Map(
            "textus.subsystem.user-mode" -> ConfigurationValue.StringValue(value)
          )))
        }
      }
    )

  private def _source(
    path: String,
    rank: Int,
    values: Map[String, ConfigurationValue]
  ): ConfigurationSource.File =
    ConfigurationSource.File(
      if (rank >= ConfigurationSource.Rank.Project) ConfigurationOrigin.Project else ConfigurationOrigin.Home,
      Paths.get(path),
      rank,
      new FileConfigLoader {
        override def load(path: java.nio.file.Path): Consequence[Configuration] =
          Consequence.success(Configuration(values))
      }
    )

  private val _fixture_work_root = Paths.get("target", "cncf-runtime-configuration-projection-spec")

  private def _with_fixture_root[A](prefix: String)(body: Path => A): A = {
    Files.createDirectories(_fixture_work_root)
    val workroot = Files.createTempDirectory(_fixture_work_root, prefix)
    try body(workroot)
    finally _delete_recursively(workroot)
  }

  private def _delete_recursively(path: Path): Unit = {
    if (Files.exists(path)) {
      val stream = Files.walk(path)
      try stream.sorted(Comparator.reverseOrder()).forEach(entry => Files.deleteIfExists(entry))
      finally stream.close()
    }
  }

  private def _take[A](result: Consequence[A]): A =
    result.getOrElse(fail(result.display))
}
