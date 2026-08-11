package org.goldenport.cncf.config

import java.nio.file.Files
import java.util.concurrent.atomic.{AtomicBoolean, AtomicInteger}

import org.goldenport.Consequence
import org.goldenport.cncf.cli.CncfRuntime
import org.goldenport.cncf.context.{ExecutionContext, ExecutionProfileMode}
import org.goldenport.cncf.datastore.DataStore
import org.goldenport.cncf.subsystem.{DefaultSubsystemFactory, Subsystem, SubsystemUserMode}
import org.goldenport.configuration.{ConfigurationOrigin, ConfigurationValue}
import org.goldenport.configuration.source.ConfigurationSource
import org.goldenport.record.Record
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Aug.  3, 2026
 * @version Aug. 11, 2026
 * @author  ASAMI, Tomoharu
 */
final class CncfRuntimeSnapshotBootstrapSpec
  extends AnyWordSpec
    with Matchers
    with GivenWhenThen {
  private val _e1 = afterWord(
    "in spec:phase-55-gcf07c-runtime-source-projection, example:E2, rules:GCF07C-R1,R2,R3,R4, phase:55, slice:GCF-07C"
  )
  private val _e3 = afterWord(
    "in spec:phase-55-gcf07c-runtime-source-projection, example:E3, rules:GCF07C-R1,R2,R3,R4, phase:55, slice:GCF-07C"
  )
  private val _e4 = afterWord(
    "in spec:phase-55-argv-binding-runtime-adoption, example:E1, rules:GCF08F-R1,R2,R6, phase:55, slice:GCF-08F"
  )
  private val _e5 = afterWord(
    "in spec:phase-55-argv-binding-runtime-adoption, example:E5, rules:GCF08F-R1,R3,R5, phase:55, slice:GCF-08F"
  )
  private val _e6 = afterWord(
    "in spec:phase-55-argv-binding-runtime-adoption, example:E6, rules:GCF08F-R3,R4,R6, phase:55, slice:GCF-08F"
  )
  private val _e7 = afterWord(
    "in spec:phase-55-environment-binding-runtime-admission, example:E4, rules:GCF08G-R1,R2,R3,R5, phase:55, slice:GCF-08G"
  )
  private val _e8 = afterWord(
    "in spec:phase-55-environment-binding-runtime-admission, example:E5, rules:GCF08G-R1,R4, phase:55, slice:GCF-08G"
  )
  private val _e9 = afterWord(
    "in spec:phase-55-canonical-split-file-binding-admission, example:E1, rules:GCF08I-R1,R2,R3, phase:55, slice:GCF-08I"
  )
  private val _e10 = afterWord(
    "in spec:phase-55-system-node-shutdown-configuration, example:E5, rules:GCF09H-C1,C2,C3, phase:55, slice:GCF-09H"
  )
  private val _e11 = afterWord(
    "in spec:phase-55-system-node-shutdown-configuration, example:E6, rules:GCF09H-C1,C3, phase:55, slice:GCF-09H"
  )
  private val _e12 = afterWord(
    "in spec:phase-55-startup-import-configuration, example:E5, rules:GCF09I-C1,C2,C3,C4, phase:55, slice:GCF-09I"
  )
  private val _e13 = afterWord(
    "in spec:phase-55-runtime-execution-profile-configuration, example:E4, rules:GCF09M-C2,C3,C4, phase:55, slice:GCF-09M"
  )
  private val _e14 = afterWord(
    "in spec:phase-55-runtime-execution-profile-configuration, example:E5, rules:GCF09M-C3,C4, phase:55, slice:GCF-09M"
  )
  private val _e15 = afterWord(
    "in spec:phase-55-runtime-process-exit-policy, example:E4, rules:GCF09N-C2,C3,C4, phase:55, slice:GCF-09N"
  )
  private val _e16 = afterWord(
    "in spec:phase-55-runtime-process-exit-policy, example:E5, rules:GCF09N-C1,C2,C3, phase:55, slice:GCF-09N"
  )
  private val _e17 = afterWord(
    "in spec:phase-55-runtime-process-exit-policy, example:E6, rules:GCF09N-C2,C4, phase:55, slice:GCF-09N"
  )

  "CncfRuntime bootstrap configuration snapshot" should {
    "E2 retain one physical Textus and CNCF source snapshot for typed projection" must _e1 {
      "when both compatibility scopes configure one stable Subsystem user-mode" in {
        Given("a Textus baseline and a higher-precedence CNCF compatibility override")
        val cwd = Files.createTempDirectory("cncf-runtime-snapshot-bootstrap")
        val textus = cwd.resolve(".textus").resolve("config.conf")
        val cncf = cwd.resolve(".cncf").resolve("config.conf")
        Files.createDirectories(textus.getParent)
        Files.createDirectories(cncf.getParent)
        Files.writeString(textus, "textus.subsystem.user-mode = standalone\n")
        Files.writeString(cncf, "textus.subsystem.user-mode = multi-user\n")

        When("the runtime bootstraps and projects its retained raw source snapshots")
        val bootstrap = CncfRuntime.bootstrap(cwd, Array("command"))
        val snapshot = bootstrap.configurationSnapshot.getOrElse(fail("runtime configuration snapshot is required"))
        val sources = snapshot.sources.filter(x => Set(textus.toString, cncf.toString).contains(x.sourceIdentity))
        val identity = SubsystemInstanceId.default(DefaultSubsystemFactory.subsystemName).getOrElse(fail("Subsystem identity is required"))
        val binding = CncfRuntimeConfigurationProjection.subsystemUserMode(snapshot, identity)
          .getOrElse(fail("typed runtime projection failed"))

        Then("both physical sources occur exactly once, CNCF overrides Textus, and the legacy projection agrees")
        sources.map(_.sourceIdentity).toSet shouldBe Set(textus.toString, cncf.toString)
        sources.size shouldBe 2
        RuntimeConfig.getString(bootstrap.configuration, SubsystemUserMode.CONFIGURATION_KEY) shouldBe Some("multi-user")
        binding.map(_.value) shouldBe Some(SubsystemUserMode.MultiUser)
        binding.flatMap(_.overridden).map(_.value) shouldBe Some(SubsystemUserMode.Standalone)
        binding.map(_.provenance.layer) shouldBe Some("cncf")
        binding.flatMap(_.overridden).map(_.provenance.layer) shouldBe Some("textus")
        binding.map(_.provenance.sourceType) shouldBe Some(Some("file"))
        binding.flatMap(_.overridden).map(_.provenance.sourceType) shouldBe Some(Some("file"))
      }
    }

    "E3 retain only the later explicit occurrence of a standard physical source" must _e3 {
      "when an explicit framework config names the same CNCF compatibility file" in {
        Given("a standard CNCF file also named by the explicit config option")
        val cwd = Files.createTempDirectory("cncf-runtime-snapshot-explicit")
        val textus = cwd.resolve(".textus").resolve("config.conf")
        val cncf = cwd.resolve(".cncf").resolve("config.conf")
        Files.createDirectories(textus.getParent)
        Files.createDirectories(cncf.getParent)
        Files.writeString(textus, "textus.subsystem.user-mode = standalone\n")
        Files.writeString(cncf, "textus.subsystem.user-mode = multi-user\n")

        When("the runtime accepts the explicit occurrence after standard discovery")
        val bootstrap = CncfRuntime.bootstrap(cwd, Array(s"--textus.config.file=$cncf", "command"))
        val snapshot = bootstrap.configurationSnapshot.getOrElse(fail("runtime configuration snapshot is required"))
        val cncfoccurrences = snapshot.sources.filter(_.sourceIdentity == cncf.toString)
        val identity = SubsystemInstanceId.default(DefaultSubsystemFactory.subsystemName).getOrElse(fail("Subsystem identity is required"))
        val binding = CncfRuntimeConfigurationProjection.subsystemUserMode(snapshot, identity)
          .getOrElse(fail("typed runtime projection failed"))

        Then("the physical file occurs once and remains the typed winner")
        cncfoccurrences.size shouldBe 1
        cncfoccurrences.head.origin shouldBe ConfigurationOrigin.Arguments
        cncfoccurrences.head.sourceRank shouldBe ConfigurationSource.Rank.Arguments
        binding.map(_.value) shouldBe Some(SubsystemUserMode.MultiUser)
        binding.map(_.provenance.origin) shouldBe Some(ConfigurationOrigin.Arguments)
      }
    }

    "E4 retain typed pre-sentinel bindings outside compatibility configuration and downstream argv" must _e4 {
      "when one canonical binding envelope and a post-sentinel lookalike are supplied" in {
        Given("a clean runtime directory and a Subsystem-scoped canonical user-mode binding")
        val cwd = Files.createTempDirectory("cncf-runtime-argv-binding")
        val identity = SubsystemInstanceId.default(DefaultSubsystemFactory.subsystemName).getOrElse(fail("Subsystem identity is required"))
        val binding = s"--textus.binding=@s/${identity.subsystem}/${identity.instance}:textus.subsystem.user-mode=multi-user"
        val postsentinel = "--textus.binding=not-an-admitted-pre-sentinel-envelope"
        val postsentinelsourceoption = "--component-file=/command-domain-only.car"

        When("the runtime bootstraps from the raw command argv")
        val bootstrap = CncfRuntime.bootstrap(cwd, Array(binding, "command", "--", postsentinel, postsentinelsourceoption))
        val snapshot = bootstrap.configurationSnapshot.getOrElse(fail("runtime configuration snapshot is required"))
        val arguments = snapshot.sources.filter(_.origin == ConfigurationOrigin.Arguments)
        val collection = CncfRuntimeConfigurationProjection.forSubsystem(
          snapshot,
          identity
        ).getOrElse(fail("baseline runtime projection failed"))
        val candidates = CncfRuntimeConfigurationProjection.candidates(
          snapshot,
          identity,
          bootstrap.configurationArgumentBindings
        ).getOrElse(fail("typed runtime projection failed"))
        val context = CncfConfigurationResolutionContext.forSubsystem(identity).getOrElse(fail("resolution context is required"))
        val resolved = org.goldenport.configuration.ConfigurationBindingResolver.resolve(candidates, context.generic)
          .getOrElse(fail("typed runtime resolution failed"))
        val restored = CncfRuntime.restoreConfigurationArgumentBindings(
          bootstrap.invocation.actualArgs,
          bootstrap.configurationArgumentBindings
        )
        val rebuilt = CncfRuntime.bootstrap(cwd, restored)

        Then("the one final argv source retains no envelope key, the typed binding wins only in the catalog, and post-sentinel argv survives")
        arguments.size shouldBe 1
        bootstrap.configuration.configuration.values.contains("textus.binding") shouldBe false
        collection.value(CncfConfigurationParameterCatalog.subsystemUserMode).toOption.flatten shouldBe None
        resolved.value(CncfConfigurationParameterCatalog.subsystemUserMode).toOption.flatten shouldBe Some(SubsystemUserMode.MultiUser)
        bootstrap.invocation.actualArgs.contains(binding) shouldBe false
        bootstrap.invocation.actualArgs.contains(postsentinel) shouldBe true
        bootstrap.invocation.actualArgs.contains(postsentinelsourceoption) shouldBe true
        rebuilt.configurationArgumentBindings.size shouldBe 1
        rebuilt.invocation.actualArgs.contains(postsentinelsourceoption) shouldBe true
      }
    }

    "E5 retain one final argv source when a test descriptor contributes defaults" must _e5 {
      "when descriptor configuration and one typed Subsystem binding are both supplied" in {
        Given("a test descriptor default, an explicit descriptor option, and a canonical binding envelope")
        val cwd = Files.createTempDirectory("cncf-runtime-argv-binding-test-descriptor")
        val descriptor = cwd.resolve("test.yaml")
        Files.writeString(
          descriptor,
          """kind: test-descriptor
            |config:
            |  textus.web.demo-assist.enabled: true
            |""".stripMargin
        )
        val identity = SubsystemInstanceId.default(DefaultSubsystemFactory.subsystemName).getOrElse(fail("Subsystem identity is required"))
        val binding = s"--textus.binding=@s/${identity.subsystem}/${identity.instance}:textus.subsystem.user-mode=multi-user"

        When("bootstrap resolves descriptor defaults below explicit argv and projects typed bindings")
        val bootstrap = CncfRuntime.bootstrap(
          cwd,
          Array(s"--textus.test.descriptor=$descriptor", binding, "command")
        )
        val snapshot = bootstrap.configurationSnapshot.getOrElse(fail("runtime configuration snapshot is required"))
        val arguments = snapshot.sources.filter(_.origin == ConfigurationOrigin.Arguments)
        val candidates = CncfRuntimeConfigurationProjection.candidates(snapshot, identity, bootstrap.configurationArgumentBindings)
          .getOrElse(fail("typed runtime projection failed"))
        val context = CncfConfigurationResolutionContext.forSubsystem(identity).getOrElse(fail("resolution context is required"))
        val resolved = org.goldenport.configuration.ConfigurationBindingResolver.resolve(candidates, context.generic)
          .getOrElse(fail("typed runtime resolution failed"))

        Then("descriptor defaults and typed argv share one final Arguments source without a collision")
        arguments.size shouldBe 1
        RuntimeConfig.getString(bootstrap.configuration, RuntimeConfig.WEB_DEMO_ASSIST_ENABLED_KEY) shouldBe Some("true")
        resolved.value(CncfConfigurationParameterCatalog.subsystemUserMode).toOption.flatten shouldBe Some(SubsystemUserMode.MultiUser)
      }
    }

    "E6 retain typed argv bindings through runtime initialization and final Subsystem admission" must _e6 {
      "when initialization resolves a command invocation after bootstrap partitioning" in {
        Given("an explicit controlled-test descriptor and one canonical standalone binding")
        val cwd = Files.createTempDirectory("cncf-runtime-argv-binding-initialize")
        val descriptor = cwd.resolve("test.yaml")
        Files.writeString(descriptor, "kind: test-descriptor\n")
        val identity = SubsystemInstanceId.default(DefaultSubsystemFactory.subsystemName).getOrElse(fail("Subsystem identity is required"))
        val binding = s"--textus.binding=@s/${identity.subsystem}/${identity.instance}:textus.subsystem.user-mode=standalone"
        val runtime = new CncfRuntime()

        When("the runtime initializes its Subsystem from the original argv")
        try {
          val subsystem = runtime.initializeForEmbedding(
            cwd = cwd,
            args = Array(s"--textus.test.descriptor=$descriptor", binding, "command"),
            modeHint = Some(org.goldenport.cncf.cli.RunMode.Command),
            extraComponents = _ => Nil
          ).getOrElse(fail("runtime initialization failed"))

          Then("the pre-sentinel envelope survives invocation resolution and selects the typed user mode")
          subsystem.subsystemUserModeC.toOption.map(_.mode) shouldBe Some(SubsystemUserMode.Standalone)
        } finally {
          runtime.closeEmbedding()
        }
      }
    }

    "E7 retain typed environment bindings outside compatibility configuration and legacy traces" must _e7 {
      "when an injected environment supplies one canonical Subsystem binding" in {
        Given("a clean runtime directory, opaque environment secret, and canonical Subsystem binding name")
        val cwd = Files.createTempDirectory("cncf-runtime-environment-binding")
        val identity = SubsystemInstanceId.default(DefaultSubsystemFactory.subsystemName).getOrElse(fail("Subsystem identity is required"))
        val codec = CncfConfigurationEnvironmentBindingCodec.create(CncfConfigurationParameterCatalog.closed)
          .getOrElse(fail("environment binding codec is required"))
        val reference = org.goldenport.configuration.ConfigurationBindingReference.create[CncfConfigurationTarget](
          CncfConfigurationParameterCatalog.subsystemUserMode.id,
          CncfConfigurationTarget.SubsystemInstance.create(identity).getOrElse(fail("Subsystem target is required"))
        ).getOrElse(fail("binding reference is required"))
        val name = codec.encode(reference).getOrElse(fail("binding name is required"))
        val secret = "do-not-persist-environment-secret"

        When("bootstrap partitions the injected environment before snapshot construction")
        val bootstrap = CncfRuntime.bootstrap(cwd, Array("command"), Map(name -> "standalone", "TEXTUS_LOG_LEVEL" -> secret))
        val snapshot = bootstrap.configurationSnapshot.getOrElse(fail("runtime configuration snapshot is required"))
        val candidates = CncfRuntimeConfigurationProjection.candidates(
          snapshot,
          identity,
          bootstrap.configurationArgumentBindings,
          bootstrap.configurationEnvironmentBindings
        ).getOrElse(fail("typed runtime projection failed"))
        val context = CncfConfigurationResolutionContext.forSubsystem(identity).getOrElse(fail("resolution context is required"))
        val resolved = org.goldenport.configuration.ConfigurationBindingResolver.resolve(candidates, context.generic)
          .getOrElse(fail("typed runtime resolution failed"))

        Then("only typed bindings influence the catalog and raw binding names or values do not persist in legacy configuration")
        bootstrap.configurationEnvironmentBindings.size shouldBe 1
        resolved.value(CncfConfigurationParameterCatalog.subsystemUserMode).toOption.flatten shouldBe Some(SubsystemUserMode.Standalone)
        bootstrap.configuration.configuration.values.keys.exists(_.contains("textus.binding")) shouldBe false
        bootstrap.configuration.configuration.values.values.exists(_ == ConfigurationValue.StringValue("standalone")) shouldBe false
      }
    }

    "E8 return a structured non-leaking configuration failure from the runtime bootstrap boundary" must _e8 {
      "when an injected environment contains a malformed binding-prefixed name" in {
        Given("a clean runtime directory and opaque malformed environment name/value")
        val cwd = Files.createTempDirectory("cncf-runtime-environment-binding-invalid")
        val secretname = "TEXTUS_BINDING_G_TEXTUS_DSUBSYSTEM_DUSER_HMODE_secret"
        val secretvalue = "do-not-render-bootstrap-environment-secret"

        When("the Consequence-returning bootstrap boundary admits the supplied environment")
        val result = CncfRuntime.bootstrapC(cwd, Array("command"), Map(secretname -> secretvalue))

        Then("the structured failure preserves neither the input name nor its value")
        result.isSuccess shouldBe false
        result.display.contains(secretname) shouldBe false
        result.display.contains(secretvalue) shouldBe false
      }
    }

    "E9 discover canonical split files once and project their selected Subsystem target" must _e9 {
      "when a current Textus configuration directory contains split canonical YAML paths" in {
        Given("one default Subsystem split file and reserved ComponentClass/ComponentInstance paths")
        val cwd = Files.createTempDirectory("cncf-runtime-split-file-binding")
        val identity = SubsystemInstanceId.default(DefaultSubsystemFactory.subsystemName).getOrElse(fail("Subsystem identity is required"))
        val subsystemfile = cwd.resolve(".textus").resolve("subsystems").resolve(identity.subsystem)
          .resolve("instances").resolve(identity.instance).resolve("config.yaml")
        val componentfile = cwd.resolve(".textus").resolve("components").resolve("org.goldenport.cncf.test.ExampleComponent").resolve("config.yaml")
        val componentinstancefile = cwd.resolve(".textus").resolve("subsystems").resolve(identity.subsystem)
          .resolve("instances").resolve(identity.instance).resolve("components").resolve("org.goldenport.cncf.test.ExampleComponent")
          .resolve("instances").resolve("primary").resolve("config.yaml")
        Files.createDirectories(subsystemfile.getParent)
        Files.createDirectories(componentfile.getParent)
        Files.createDirectories(componentinstancefile.getParent)
        Files.writeString(subsystemfile, s"${SubsystemUserMode.CONFIGURATION_KEY}: standalone\n")
        Files.writeString(componentfile, "# reserved ComponentClass location\n")
        Files.writeString(componentinstancefile, "# reserved ComponentInstance location\n")

        When("the runtime bootstraps from the current directory without an explicit file option")
        val bootstrap = CncfRuntime.bootstrap(cwd, Array("command"))
        val snapshot = bootstrap.configurationSnapshot.getOrElse(fail("runtime configuration snapshot is required"))
        val candidates = CncfRuntimeConfigurationProjection.candidates(
          snapshot,
          identity,
          bootstrap.configurationArgumentBindings,
          bootstrap.configurationEnvironmentBindings
        ).getOrElse(fail("typed runtime projection failed"))
        val context = CncfConfigurationResolutionContext.forSubsystem(identity).getOrElse(fail("resolution context is required"))
        val collection = org.goldenport.configuration.ConfigurationBindingResolver.resolve(candidates, context.generic)
          .getOrElse(fail("typed runtime resolution failed"))

        Then("each canonical path occurs once and only the selected Subsystem file supplies its typed value")
        snapshot.sources.count(_.sourceIdentity == subsystemfile.toString) shouldBe 1
        snapshot.sources.count(_.sourceIdentity == componentfile.toString) shouldBe 1
        snapshot.sources.count(_.sourceIdentity == componentinstancefile.toString) shouldBe 1
        collection.value(CncfConfigurationParameterCatalog.subsystemUserMode).toOption.flatten shouldBe Some(SubsystemUserMode.Standalone)
      }
    }

    "E10 construct the runtime SystemNode from the selected typed Subsystem binding before factory admission" must _e10 {
      "when a canonical split Subsystem configuration declares a bounded timeout" in {
        Given("one controlled-test descriptor and a canonical split Subsystem timeout binding")
        val cwd = Files.createTempDirectory("cncf-runtime-system-node-timeout")
        val descriptor = cwd.resolve("test.yaml")
        val identity = SubsystemInstanceId.default(DefaultSubsystemFactory.subsystemName).getOrElse(fail("Subsystem identity is required"))
        val subsystemfile = cwd.resolve(".textus").resolve("subsystems").resolve(identity.subsystem)
          .resolve("instances").resolve(identity.instance).resolve("config.yaml")
        Files.writeString(descriptor, "kind: test-descriptor\n")
        Files.createDirectories(subsystemfile.getParent)
        Files.writeString(
          subsystemfile,
          s"${CncfConfigurationParameterCatalog.SYSTEM_NODE_SHUTDOWN_DRAIN_TIMEOUT_MILLIS_KEY}: 120\n"
        )
        val runtime = new CncfRuntime()
        var subsystem: Option[Subsystem] = None

        When("runtime initialization resolves the binding and creates its Subsystem")
        try {
          val initialized = runtime.initializeForEmbedding(
            cwd = cwd,
            args = Array(s"--textus.test.descriptor=$descriptor", "command"),
            modeHint = Some(org.goldenport.cncf.cli.RunMode.Command),
            extraComponents = _ => Nil
          ).getOrElse(fail("runtime initialization failed"))
          subsystem = Some(initialized)

          Then("the factory-produced Subsystem retains the one preconstructed SystemNode with the resolved duration")
          initialized.systemNode.drainTimeoutMillis shouldBe 120L
          initialized.systemNode.bindingCount shouldBe 1
        } finally {
          subsystem.foreach(Subsystem.shutdownOwned)
          runtime.closeEmbedding()
        }
      }
    }

    "E11 reject invalid SystemNode timeout input before runtime factory follow-on work" must _e11 {
      "when a canonical split Subsystem configuration declares zero milliseconds" in {
        Given("one controlled-test descriptor, invalid timeout input, and a factory-follow-on sentinel")
        val cwd = Files.createTempDirectory("cncf-runtime-system-node-timeout-invalid")
        val descriptor = cwd.resolve("test.yaml")
        val identity = SubsystemInstanceId.default(DefaultSubsystemFactory.subsystemName).getOrElse(fail("Subsystem identity is required"))
        val subsystemfile = cwd.resolve(".textus").resolve("subsystems").resolve(identity.subsystem)
          .resolve("instances").resolve(identity.instance).resolve("config.yaml")
        Files.writeString(descriptor, "kind: test-descriptor\n")
        Files.createDirectories(subsystemfile.getParent)
        Files.writeString(
          subsystemfile,
          s"${CncfConfigurationParameterCatalog.SYSTEM_NODE_SHUTDOWN_DRAIN_TIMEOUT_MILLIS_KEY}: 0\n"
        )
        val extracomponentsentered = new AtomicBoolean(false)
        val runtime = new CncfRuntime()

        When("runtime initialization admits the invalid typed configuration")
        try {
          val result = runtime.initializeForEmbedding(
            cwd = cwd,
            args = Array(s"--textus.test.descriptor=$descriptor", "command"),
            modeHint = Some(org.goldenport.cncf.cli.RunMode.Command),
            extraComponents = _ => {
              extracomponentsentered.set(true)
              Nil
            }
          )

          Then("it returns structured failure and does not enter later factory follow-on work")
          result.isSuccess shouldBe false
          result.display should include (CncfConfigurationParameterCatalog.SYSTEM_NODE_SHUTDOWN_DRAIN_TIMEOUT_MILLIS_KEY)
          extracomponentsentered.get() shouldBe false
        } finally {
          runtime.closeEmbedding()
        }
      }
    }

    "E4 project process-exit policy from the one admitted Global bootstrap collection" must _e15 {
      "when a Textus runtime file sets process-exit and repository-bootstrap values" in {
        Given("one physical Global source with an admitted false value and a direct CLI override")
        val cwd = Files.createTempDirectory("cncf-runtime-process-exit-policy")
        val config = cwd.resolve(".textus").resolve("config.conf")
        Files.createDirectories(config.getParent)
        Files.writeString(
          config,
          s"${CncfConfigurationParameterCatalog.FORCE_EXIT_KEY} = false\n" +
            s"${CncfConfigurationParameterCatalog.NO_EXIT_KEY} = false\n" +
            s"${CncfConfigurationParameterCatalog.COLLABORATOR_REPOSITORIES_KEY} = configured-collaborator\n"
        )

        When("the runtime admits the source once and consumes external process-exit switches")
        val bootstrap = CncfRuntime.bootstrap(cwd, Array("--force-exit", "--no-exit", "command"))
        val snapshot = bootstrap.configurationSnapshot.getOrElse(fail("runtime configuration snapshot is required"))

        Then("the value-only process policy and repository policy share the admitted bootstrap path")
        snapshot.sources.count(_.sourceIdentity == config.toString) shouldBe 1
        bootstrap.repositoryBootstrapPolicy.collaboratorRepositoryPaths shouldBe Vector(
          cwd.resolve("configured-collaborator").normalize
        )
        bootstrap.front.processExitPolicy shouldBe RuntimeProcessExitPolicy(forceExit = true, noExit = true)
        bootstrap.front.residualArgs.toVector shouldBe Vector("command")
      }
    }

    "E5 retain process-exit failures as structured bootstrap admission results" must _e16 {
      "when malformed, wrong-target, and canonical-alias-collision values enter runtime sources" in {
        Given("three isolated runtime directories containing the invalid process-exit forms")
        val malformedcwd = Files.createTempDirectory("cncf-runtime-process-exit-malformed")
        val wrongtargetcwd = Files.createTempDirectory("cncf-runtime-process-exit-wrong-target")
        val collisioncwd = Files.createTempDirectory("cncf-runtime-process-exit-collision")
        val malformed = malformedcwd.resolve(".textus").resolve("config.conf")
        val identity = SubsystemInstanceId.default(DefaultSubsystemFactory.subsystemName).getOrElse(fail("Subsystem identity is required"))
        val wrongtarget = wrongtargetcwd.resolve(".textus").resolve("subsystems").resolve(identity.subsystem)
          .resolve("instances").resolve(identity.instance).resolve("config.yaml")
        val collision = collisioncwd.resolve(".textus").resolve("config.conf")
        Files.createDirectories(malformed.getParent)
        Files.createDirectories(wrongtarget.getParent)
        Files.createDirectories(collision.getParent)
        Files.writeString(malformed, s"${CncfConfigurationParameterCatalog.FORCE_EXIT_KEY} = sometimes\n")
        Files.writeString(wrongtarget, s"${CncfConfigurationParameterCatalog.NO_EXIT_KEY}: true\n")
        Files.writeString(
          collision,
          s"${CncfConfigurationParameterCatalog.FORCE_EXIT_KEY} = true\n" +
            "cncf.force-exit = false\n"
        )

        When("bootstrapC admits each source through the shared Global projection")
        val malformedresult = CncfRuntime.bootstrapC(malformedcwd, Array("command"), Map.empty)
        val wrongtargetresult = CncfRuntime.bootstrapC(wrongtargetcwd, Array("command"), Map.empty)
        val collisionresult = CncfRuntime.bootstrapC(collisioncwd, Array("command"), Map.empty)

        Then("all failures remain structured consequences rather than uncaught bootstrap exceptions")
        malformedresult.isSuccess shouldBe false
        malformedresult.display should include(CncfConfigurationParameterCatalog.FORCE_EXIT_KEY)
        wrongtargetresult.isSuccess shouldBe false
        wrongtargetresult.display should include(CncfConfigurationParameterCatalog.NO_EXIT_KEY)
        collisionresult.isSuccess shouldBe false
        collisionresult.display.nonEmpty shouldBe true
      }
    }

    "E6 retain the auto-discovered archive through the resolved Global-policy continuation" must _e17 {
      "when bootstrap discovers one component archive after Global policy resolution" in {
        Given("a clean runtime directory containing one candidate component archive")
        val cwd = Files.createTempDirectory("cncf-runtime-auto-component-archive")
        val archive = cwd.resolve("target").resolve("sample.car")
        val globalpolicyresolutions = new AtomicInteger(0)
        val observation = new CncfRuntime.RuntimeBootstrapObservation {
          override def onGlobalPolicyResolution(): Unit = globalpolicyresolutions.incrementAndGet()
        }
        Files.createDirectories(archive.getParent)
        Files.writeString(archive, "")

        When("bootstrap enriches repository arguments without restarting Global resolution")
        val bootstrap = CncfRuntime.bootstrapC(cwd, Array("command"), Map.empty, observation)

        Then("the Global policy projection occurs once, the repository retains the archive, and the residual command is unchanged")
        bootstrap.isSuccess shouldBe true
        val value = bootstrap.getOrElse(fail("bootstrap is required"))
        globalpolicyresolutions.get shouldBe 1
        value.repositoryBootstrapPolicy.componentFiles shouldBe Vector(archive.toString)
        value.front.residualArgs.toVector shouldBe Vector("command")
      }
    }

    "E5 import a canonical split-file startup data source through final Subsystem admission" must _e12 {
      "when a canonical Subsystem config selects one bootstrap-relative data file" in {
        Given("one controlled-test descriptor, one canonical split config, and one data seed")
        val cwd = Files.createTempDirectory("cncf-runtime-startup-import-canonical")
        val descriptor = cwd.resolve("test.yaml")
        val datafile = cwd.resolve("data.yaml")
        val identity = SubsystemInstanceId.default(DefaultSubsystemFactory.subsystemName).getOrElse(fail("Subsystem identity is required"))
        val subsystemfile = cwd.resolve(".textus").resolve("subsystems").resolve(identity.subsystem)
          .resolve("instances").resolve(identity.instance).resolve("config.yaml")
        Files.writeString(descriptor, "kind: test-descriptor\n")
        Files.writeString(
          datafile,
          """datastore:
            |  - collection: startup_people
            |    records:
            |      - id: canonical-p1
            |        name: canonical-import
            |""".stripMargin
        )
        Files.createDirectories(subsystemfile.getParent)
        Files.writeString(
          subsystemfile,
          s"${CncfConfigurationParameterCatalog.STARTUP_IMPORT_DATA_FILE_KEY}: ${datafile.getFileName}\n"
        )
        val runtime = new CncfRuntime()
        var subsystem: Option[Subsystem] = None

        When("the runtime creates and admits its Subsystem before startup import")
        try {
          val initialized = runtime.initializeForEmbedding(
            cwd = cwd,
            args = Array(s"--textus.test.descriptor=$descriptor", "command"),
            modeHint = Some(org.goldenport.cncf.cli.RunMode.Command),
            extraComponents = _ => Nil
          ).getOrElse(fail("runtime initialization failed"))
          subsystem = Some(initialized)
          val collection = DataStore.CollectionId("startup_people")
          given ExecutionContext = ExecutionContext.create()

          Then("the importer receives the admitted canonical value and stores the seed")
          initialized.globalRuntimeContext.dataStoreSpace
            .dataStore(collection)
            .flatMap(_.load(collection, DataStore.StringEntryId("canonical-p1"))) shouldBe
              Consequence.success(Some(Record.dataAuto("id" -> "canonical-p1", "name" -> "canonical-import")))
        } finally {
          subsystem.foreach(Subsystem.shutdownOwned)
          runtime.closeEmbedding()
        }
      }
    }

    "E4 create GlobalRuntimeContext from the selected typed execution-profile binding" must _e13 {
      "when a canonical split Subsystem file supplies a seeded profile" in {
        Given("one test descriptor and a canonical split execution-profile configuration")
        val cwd = Files.createTempDirectory("cncf-runtime-execution-profile-typed")
        val descriptor = cwd.resolve("test.yaml")
        val identity = SubsystemInstanceId.default(DefaultSubsystemFactory.subsystemName).getOrElse(fail("Subsystem identity is required"))
        val subsystemfile = cwd.resolve(".textus").resolve("subsystems").resolve(identity.subsystem)
          .resolve("instances").resolve(identity.instance).resolve("config.yaml")
        Files.writeString(descriptor, "kind: test-descriptor\n")
        Files.createDirectories(subsystemfile.getParent)
        Files.writeString(
          subsystemfile,
          s"${CncfConfigurationParameterCatalog.EXECUTION_PROFILE_KEY}: seeded\n" +
            s"${CncfConfigurationParameterCatalog.EXECUTION_RANDOM_SEED_KEY}: runtime-private-seed\n"
        )
        val runtime = new CncfRuntime()
        var subsystem: Option[Subsystem] = None

        When("runtime preflight resolves its selected Subsystem collection before global context construction")
        try {
          val initialized = runtime.initializeForEmbedding(
            cwd = cwd,
            args = Array(s"--textus.test.descriptor=$descriptor", "command"),
            modeHint = Some(org.goldenport.cncf.cli.RunMode.Command),
            extraComponents = _ => Nil
          ).getOrElse(fail("runtime initialization failed"))
          subsystem = Some(initialized)

          Then("the global runtime and final Subsystem admission retain the same typed profile")
          initialized.globalRuntimeContext.executionProfile.identity.mode shouldBe ExecutionProfileMode.Seeded
          initialized.runtimeExecutionProfileConfigurationC.toOption.map(_.mode) shouldBe Some(ExecutionProfileMode.Seeded)
          initialized.runtimeExecutionProfileConfigurationC.toOption.map(_.randomSeed) shouldBe Some(Some("runtime-private-seed"))
        } finally {
          subsystem.foreach(Subsystem.shutdownOwned)
          runtime.closeEmbedding()
        }
      }
    }

    "E5 activate a controlled profile from the admitted typed test operation mode" must _e14 {
      "when no test descriptor is selected" in {
        Given("one canonical split Subsystem configuration with controlled execution and typed test operation mode")
        val cwd = Files.createTempDirectory("cncf-runtime-execution-profile-test-mode")
        val identity = SubsystemInstanceId.default(DefaultSubsystemFactory.subsystemName).getOrElse(fail("Subsystem identity is required"))
        val subsystemfile = cwd.resolve(".textus").resolve("subsystems").resolve(identity.subsystem)
          .resolve("instances").resolve(identity.instance).resolve("config.yaml")
        Files.createDirectories(subsystemfile.getParent)
        Files.writeString(
          subsystemfile,
          s"${CncfConfigurationParameterCatalog.OPERATION_MODE_KEY}: test\n" +
            s"${CncfConfigurationParameterCatalog.EXECUTION_PROFILE_KEY}: controlled\n" +
            s"${CncfConfigurationParameterCatalog.EXECUTION_KEY}: typed-test-run\n" +
            s"${CncfConfigurationParameterCatalog.EXECUTION_TIME_MODE_KEY}: manual\n" +
            s"${CncfConfigurationParameterCatalog.EXECUTION_TIME_START_AT_KEY}: \"2026-08-04T00:00:00Z\"\n" +
            s"${CncfConfigurationParameterCatalog.EXECUTION_RANDOM_SEED_KEY}: typed-test-seed\n"
        )
        val runtime = new CncfRuntime()
        var subsystem: Option[Subsystem] = None

        When("runtime preflight resolves the operation-mode and execution-profile witnesses together")
        try {
          val result = runtime.initializeForEmbedding(
            cwd = cwd,
            args = Array("command"),
            modeHint = Some(org.goldenport.cncf.cli.RunMode.Command),
            extraComponents = _ => Nil
          )
          val initialized = result.getOrElse(fail(s"runtime initialization failed: ${result.display}"))
          subsystem = Some(initialized)

          Then("typed test operation mode authorizes the controlled global execution profile")
          initialized.globalRuntimeContext.executionProfile.identity.mode shouldBe ExecutionProfileMode.Controlled
        } finally {
          subsystem.foreach(Subsystem.shutdownOwned)
          runtime.closeEmbedding()
        }
      }
    }
  }
}
