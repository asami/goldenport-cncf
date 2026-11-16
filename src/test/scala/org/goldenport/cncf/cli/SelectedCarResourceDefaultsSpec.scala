package org.goldenport.cncf.cli

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}
import java.util.zip.{ZipEntry, ZipOutputStream}
import scala.jdk.CollectionConverters.*
import scala.util.Using
import org.goldenport.Consequence
import org.goldenport.configuration.*
import org.goldenport.cncf.config.*
import org.goldenport.cncf.context.{ExecutionContext, GlobalRuntimeContext, ScopeKind}
import org.goldenport.cncf.path.AliasResolver
import org.goldenport.cncf.resource.{ResourceReadLimits, ResourceReference}
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Nov. 16, 2026
 * @version Nov. 16, 2026
 * @author  ASAMI, Tomoharu
 */
final class SelectedCarResourceDefaultsSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _roots_key = RuntimeConfig.resourceUrlFileRootsKey
  private val _limit_key = RuntimeConfig.RESOURCE_READ_MAX_BYTES_KEY
  private val _car_values = Map(_roots_key -> "${runtime.root}", _limit_key -> "3")
  private val _e1 = afterWord("in spec:selected-car-resource-defaults, example:E1, rules:R1,R3, phase:INIT1")
  private val _e2 = afterWord("in spec:selected-car-resource-defaults, example:E2, rules:R2, phase:INIT1")
  private val _e3 = afterWord("in spec:selected-car-resource-defaults, example:E3, rules:R1,R2,R3, phase:INIT1")

  "Selected CAR resource defaults" should {
    "E1 reach ordinary execution resource enforcement" must _e1 {
      "read the selected RuntimeRoot and deny outside paths and symbolic links without user configuration" in {
        Given("R1,R3; a packaged CAR with resource defaults and no user .textus configuration")
        _with_car(_car_values) { (workspace, root, car) =>
          val inside = Files.writeString(root.resolve("inside.txt"), "日", UTF_8)
          val outside = Files.writeString(workspace.resolve("outside.txt"), "日", UTF_8)
          val link = Files.createSymbolicLink(root.resolve("link.txt"), outside)
          val linkdir = Files.createSymbolicLink(root.resolve("linked-directory"), workspace)
          val supplied = _resolved()
          val defaults = _preflight_defaults(supplied, _policy(root, Vector(car)))

          When("normal global runtime configuration binds scoped execution resource access")
          val runconfig = RuntimeConfig.from(RuntimeConfig._with_selected_resource_defaults(supplied, defaults))
          val context = _context(runconfig, supplied)
          val content = context.readResourceText(_reference(inside), runconfig.resourceReadLimits, Some(UTF_8))
          val denied = Vector(outside, link, linkdir.resolve("outside.txt")).map { path =>
            context.readResourceText(_reference(path), runconfig.resourceReadLimits, Some(UTF_8))
          }

          Then("only the admitted in-root regular file is readable")
          defaults.values(_roots_key) shouldBe ConfigurationValue.StringValue(root.toString)
          runconfig.resourceUrlPolicy.normalizedFileRoots shouldBe Vector(root)
          content.toOption shouldBe Some("日")
          denied.foreach { result =>
            result.isSuccess shouldBe false
            result.toOption shouldBe None
          }
          Files.exists(root.resolve(".textus")) shouldBe false
          supplied.configuration.values shouldBe empty
        }
      }

      "enforce the CAR byte limit at equality and overflow on the normal resource owner" in {
        Given("R1,R3; a packaged three-byte limit and files at and above that limit")
        _with_car(_car_values) { (_, root, car) =>
          val equal = Files.writeString(root.resolve("equal.txt"), "日", UTF_8)
          val excess = Files.writeString(root.resolve("excess.txt"), "日本", UTF_8)
          val supplied = _resolved()
          val defaults = _preflight_defaults(supplied, _policy(root, Vector(car)))

          When("ordinary execution reads through the selected global runtime policy")
          val runconfig = RuntimeConfig.from(RuntimeConfig._with_selected_resource_defaults(supplied, defaults))
          val context = _context(runconfig, supplied)
          val accepted = context.readResourceText(_reference(equal), runconfig.resourceReadLimits, Some(UTF_8))
          val rejected = context.readResourceText(_reference(excess), runconfig.resourceReadLimits, Some(UTF_8))
          val ordinary = context.resources.readText(_reference(excess))

          Then("the exact UTF-8 boundary succeeds and overflow returns no partial text")
          runconfig.resourceReadLimits shouldBe ResourceReadLimits(3)
          accepted.toOption shouldBe Some("日")
          rejected.toOption shouldBe None
          _failure(rejected) should include("resource exceeds the runtime byte limit")
          _failure(ordinary) should include("resource exceeds the runtime byte limit")
        }
      }
    }

    "E2 preserve explicit configuration across each existing spelling" must _e2 {
      "prefer generated supplied canonical and alias groups over every default spelling" in {
        Given("R2; all canonical/runtime and textus/cncf spelling combinations with distinct finite limits")
        val spellings = for {
          suppliedroot <- Gen.oneOf(_spellings(_roots_key))
          defaultroot <- Gen.oneOf(_spellings(_roots_key))
          suppliedlimit <- Gen.oneOf(_spellings(_limit_key))
          defaultlimit <- Gen.oneOf(_spellings(_limit_key))
          limit <- Gen.choose(4, 4096)
        } yield (suppliedroot, defaultroot, suppliedlimit, defaultlimit, limit)
        val trace = ConfigurationTrace(Map("application.marker" -> ConfigurationResolution(
          "application.marker", ConfigurationValue.StringValue("explicit"), ConfigurationOrigin.Resource, Nil
        )))

        When("the limited overlay resolves generated explicit and default spelling combinations")
        val result = Test.check(Test.Parameters.default.withMinSuccessfulTests(100), Prop.forAll(spellings) {
          case (suppliedroot, defaultroot, suppliedlimit, defaultlimit, limit) =>
            val supplied = ResolvedConfiguration(_configuration(Map(
              suppliedroot -> "/explicit/root", suppliedlimit -> limit.toString
            )), trace)
            val defaults = _configuration(Map(defaultroot -> "/car/root", defaultlimit -> "3"))
            val merged = RuntimeConfig._with_selected_resource_defaults(supplied, defaults)
            val settings = RuntimeConfig.settingsFrom(merged)
            Prop.all(
              Prop(merged.configuration.values == supplied.configuration.values),
              Prop(merged.trace eq trace),
              Prop(settings.resourceUrlPolicy.normalizedFileRoots == Vector(Path.of("/explicit/root"))),
              Prop(settings.resourceReadLimits.maximumBytes == limit)
            )
        })

        Then("explicit values and their trace survive regardless of CAR spelling")
        result.passed shouldBe true
      }

      "suppress only the supplied group and inherit absent canonical or alias defaults" in {
        Given("R2; each group supplied independently and each CAR default spelling")
        val cases = for {
          rootkey <- _spellings(_roots_key)
          limitkey <- _spellings(_limit_key)
        } yield (rootkey, limitkey)

        cases.foreach { case (rootkey, limitkey) =>
          val defaults = _configuration(Map(rootkey -> "/car/root", limitkey -> "3"))
          val suppliedroots = _resolved(Map(_roots_key -> ConfigurationValue.StringValue("/explicit/root")))
          val suppliedlimit = _resolved(Map(_limit_key -> ConfigurationValue.StringValue("7")))

          When("the root and limit groups are overlaid independently")
          val roots = RuntimeConfig.settingsFrom(RuntimeConfig._with_selected_resource_defaults(suppliedroots, defaults))
          val limit = RuntimeConfig.settingsFrom(RuntimeConfig._with_selected_resource_defaults(suppliedlimit, defaults))
          val absent = RuntimeConfig.settingsFrom(RuntimeConfig._with_selected_resource_defaults(_resolved(), defaults))

          Then("the other group inherits its CAR value through the existing alias parser")
          roots.resourceUrlPolicy.normalizedFileRoots shouldBe Vector(Path.of("/explicit/root"))
          roots.resourceReadLimits.maximumBytes shouldBe 3
          limit.resourceUrlPolicy.normalizedFileRoots shouldBe Vector(Path.of("/car/root"))
          limit.resourceReadLimits.maximumBytes shouldBe 7
          absent.resourceUrlPolicy.normalizedFileRoots shouldBe Vector(Path.of("/car/root"))
          absent.resourceReadLimits.maximumBytes shouldBe 3
        }
      }

      "keep explicit empty roots disabled on the normal resource path for every alias" in {
        Given("R2; readable CAR content and explicit empty roots in each existing spelling")
        _with_car(_car_values) { (_, root, car) =>
          val file = Files.writeString(root.resolve("content.txt"), "日", UTF_8)
          val defaults = _preflight_defaults(_resolved(), _policy(root, Vector(car)))
          _spellings(_roots_key).foreach { key =>
            val supplied = _resolved(Map(key -> ConfigurationValue.StringValue("")))

            When("the explicit empty roots participate in global runtime construction")
            val runconfig = RuntimeConfig.from(RuntimeConfig._with_selected_resource_defaults(supplied, defaults))
            val content = _context(runconfig, supplied).readResourceText(_reference(file), runconfig.resourceReadLimits)

            Then("the CAR roots cannot restore disabled file reads")
            runconfig.resourceUrlPolicy.fileRoots shouldBe empty
            content.toOption shouldBe None
            _failure(content) should include("file resource access is not configured")
          }
        }
      }

      "retain malformed explicit limit failures for every existing alias" in {
        Given("R2; valid CAR defaults and an explicit malformed limit")
        val defaults = _configuration(Map(_roots_key -> "/car/root", _limit_key -> "3"))
        _spellings(_limit_key).foreach { key =>
          val supplied = _resolved(Map(key -> ConfigurationValue.StringValue("invalid")))

          When("the ordinary parser consumes the limited overlay")
          val merged = RuntimeConfig._with_selected_resource_defaults(supplied, defaults)
          val failure = intercept[IllegalArgumentException](RuntimeConfig.settingsFrom(merged))

          Then("explicit invalid input is not rescued by a valid CAR limit")
          merged.configuration.values.get(_limit_key) shouldBe supplied.configuration.values.get(_limit_key)
          failure.getMessage shouldBe s"${_limit_key} must be an integer between 1 and 268435456"
        }
      }

      "retain present typed and null values without contributing another spelling" in {
        Given("R2; explicit non-string values and defaults under every spelling of the same group")
        val values = Vector(ConfigurationValue.NumberValue(BigDecimal(7)), ConfigurationValue.BooleanValue(false),
          ConfigurationValue.NullValue, ConfigurationValue.ObjectValue(Map.empty), ConfigurationValue.ListValue(Nil))
        val groups = Vector(_roots_key, _limit_key)
        groups.foreach { group =>
          val defaults = _configuration(_spellings(group).map(_ -> "/car/default").toMap)
          _spellings(group).foreach { key =>
            values.foreach { value =>
              val supplied = _resolved(Map(key -> value))

              When("presence rather than successful string parsing determines default eligibility")
              val merged = RuntimeConfig._with_selected_resource_defaults(supplied, defaults)

              Then("the original typed value is preserved and all CAR spellings are suppressed")
              merged.configuration.values shouldBe supplied.configuration.values
            }
          }
        }
      }

      "retain narrow roots and a three-byte JVM limit across all canonical and alias spellings" in {
        Given("docs/spec/selected-car-resource-defaults.md, E2,R2; JVM roots within the CAR root and a three-byte limit versus nine CAR bytes")
        val defaults = _configuration(Map(_roots_key -> "/explicit", _limit_key -> "9"))
        val cases = for {
          rootkey <- _spellings(_roots_key)
          limitkey <- _spellings(_limit_key)
        } yield (rootkey, limitkey)
        cases.foreach { case (rootkey, limitkey) =>
          _with_properties(Map(rootkey -> "/explicit/narrow", limitkey -> "3")) {
            val supplied = _resolved()

            When("ordinary settings resolve the effective JVM inputs with and without selected CAR defaults")
            val original = RuntimeConfig.settingsFrom(supplied)
            val merged = RuntimeConfig._with_selected_resource_defaults(supplied, defaults)
            val settings = RuntimeConfig.settingsFrom(merged)

            Then("the effective narrow roots and finite limit remain unchanged without copying JVM inputs")
            original.resourceUrlPolicy.normalizedFileRoots shouldBe Vector(Path.of("/explicit/narrow"))
            original.resourceReadLimits.maximumBytes shouldBe 3
            settings.resourceUrlPolicy shouldBe original.resourceUrlPolicy
            settings.resourceReadLimits shouldBe original.resourceReadLimits
            merged.configuration.values shouldBe supplied.configuration.values
            merged.trace should be theSameInstanceAs supplied.trace
          }
        }
      }

      "retain invalid JVM limit failures for every canonical and alias spelling" in {
        Given("docs/spec/selected-car-resource-defaults.md, E2,R2; a malformed JVM limit and a valid nine-byte CAR default")
        val defaults = _configuration(Map(_limit_key -> "9"))
        _spellings(_limit_key).foreach { key =>
          _with_properties(Map(key -> "invalid")) {
            val supplied = _resolved()

            When("ordinary settings parse the invalid property with and without CAR defaults")
            val original = intercept[IllegalArgumentException](RuntimeConfig.settingsFrom(supplied))
            val merged = RuntimeConfig._with_selected_resource_defaults(supplied, defaults)
            val failure = intercept[IllegalArgumentException](RuntimeConfig.settingsFrom(merged))

            Then("the existing validation error survives without a CAR rescue")
            original.getMessage shouldBe s"${_limit_key} must be an integer between 1 and 268435456"
            failure.getMessage shouldBe original.getMessage
            merged.configuration.values shouldBe supplied.configuration.values
          }
        }
      }

      "ignore blank JVM properties and inherit the CAR defaults through the existing resolver" in {
        Given("docs/spec/selected-car-resource-defaults.md, E2,R2; only empty or whitespace JVM values for each spelling")
        val defaults = _configuration(Map(_roots_key -> "/car/root", _limit_key -> "9"))
        Vector("", "   ").foreach { blank =>
          val properties = (_spellings(_roots_key) ++ _spellings(_limit_key)).map(_ -> blank).toMap
          _with_properties(properties) {
            When("the ordinary resolver ignores blank JVM inputs before selected defaults are parsed")
            val merged = RuntimeConfig._with_selected_resource_defaults(_resolved(), defaults)
            val settings = RuntimeConfig.settingsFrom(merged)

            Then("ignored JVM inputs do not suppress the CAR root and limit groups")
            settings.resourceUrlPolicy.normalizedFileRoots shouldBe Vector(Path.of("/car/root"))
            settings.resourceReadLimits.maximumBytes shouldBe 9
          }
        }
      }

      "retain effective file settings selected by either existing property across every alias spelling" in {
        Given("docs/spec/selected-car-resource-defaults.md, E2,R2; a property-selected file with narrow roots and three bytes versus nine CAR bytes")
        val defaults = _configuration(Map(_roots_key -> "/explicit", _limit_key -> "9"))
        val path = Files.createTempFile("selected-car-resource-config-", ".yaml")
        val cases = for {
          selector <- Vector("textus.config.file", "cncf.config.file")
          rootkey <- _spellings(_roots_key)
          limitkey <- _spellings(_limit_key)
        } yield (selector, rootkey, limitkey)
        try {
          cases.foreach { case (selector, rootkey, limitkey) =>
            Files.writeString(path, s"$rootkey: '/explicit/narrow'\n$limitkey: '3'\n", UTF_8)
            _with_properties(Map(selector -> path.toString)) {
              val supplied = _resolved()

              When("ordinary settings resolve the selected configuration file with and without CAR defaults")
              val original = RuntimeConfig.settingsFrom(supplied)
              val merged = RuntimeConfig._with_selected_resource_defaults(supplied, defaults)
              val settings = RuntimeConfig.settingsFrom(merged)

              Then("the original file-selected root and limit survive without promoting fallback values")
              original.resourceUrlPolicy.normalizedFileRoots shouldBe Vector(Path.of("/explicit/narrow"))
              original.resourceReadLimits.maximumBytes shouldBe 3
              settings.resourceUrlPolicy shouldBe original.resourceUrlPolicy
              settings.resourceReadLimits shouldBe original.resourceReadLimits
              merged.configuration.values shouldBe supplied.configuration.values
              merged.trace should be theSameInstanceAs supplied.trace
            }
          }
        } finally Files.deleteIfExists(path)
      }

      "retain valid nested resolved roots and limits across each existing spelling" in {
        Given("docs/spec/selected-car-resource-defaults.md, E2,R2; nested resolved roots and a three-byte limit versus nine CAR bytes")
        val cases = for {
          rootkey <- _spellings(_roots_key)
          limitkey <- _spellings(_limit_key)
        } yield (rootkey, limitkey)
        _with_properties(Map.empty) {
          cases.foreach { case (rootkey, limitkey) =>
            // Merge at each object level so the common namespace retains both resource settings.
            val rootsegments = rootkey.split('.').toVector
            val limitsegments = limitkey.split('.').toVector
            val supplied = _resolved(_nested_values(Vector(
              rootsegments -> ConfigurationValue.StringValue("/explicit/narrow"),
              limitsegments -> ConfigurationValue.NumberValue(BigDecimal(3))
            )))
            val defaults = _configuration(Map(_roots_key -> "/explicit", _limit_key -> "9"))

            When("ordinary settings resolve the nested input with and without selected CAR defaults")
            val original = RuntimeConfig.settingsFrom(supplied)
            val merged = RuntimeConfig._with_selected_resource_defaults(supplied, defaults)
            val settings = RuntimeConfig.settingsFrom(merged)

            Then("both effective groups and the original nested configuration survive")
            original.resourceUrlPolicy.normalizedFileRoots shouldBe Vector(Path.of("/explicit/narrow"))
            original.resourceReadLimits.maximumBytes shouldBe 3
            settings.resourceUrlPolicy shouldBe original.resourceUrlPolicy
            settings.resourceReadLimits shouldBe original.resourceReadLimits
            merged.configuration.values shouldBe supplied.configuration.values
            merged.trace should be theSameInstanceAs supplied.trace
          }
        }
      }

      "preserve raw nested typed null and blank presence with its original trace for every alias" in {
        Given("docs/spec/selected-car-resource-defaults.md, E2,R2; nested explicit values and CAR defaults under every spelling of that group")
        val trace = ConfigurationTrace(Map("application.marker" -> ConfigurationResolution(
          "application.marker", ConfigurationValue.StringValue("explicit"), ConfigurationOrigin.Resource, Nil
        )))
        val values = Vector(ConfigurationValue.NumberValue(BigDecimal(3)), ConfigurationValue.BooleanValue(false),
          ConfigurationValue.NullValue, ConfigurationValue.StringValue(""), ConfigurationValue.StringValue("   "),
          ConfigurationValue.StringValue("invalid"), ConfigurationValue.ObjectValue(Map.empty), ConfigurationValue.ListValue(Nil))
        _with_properties(Map.empty) {
          Vector(_roots_key, _limit_key).foreach { group =>
            val defaults = _configuration(_spellings(group).map(_ -> "/car/default").toMap)
            _spellings(group).foreach { key =>
              values.foreach { value =>
                val supplied = ResolvedConfiguration(Configuration(_nested_values(Vector(
                  key.split('.').toVector -> value
                ))), trace)

                When("raw nested presence determines eligibility independently of string parsing")
                val merged = RuntimeConfig._with_selected_resource_defaults(supplied, defaults)

                Then("the entire CAR group is suppressed while the nested value and trace remain intact")
                merged.configuration.values shouldBe supplied.configuration.values
                merged.trace should be theSameInstanceAs trace
              }
            }
          }
        }
      }
    }

    "E3 require selected CAR provenance and keep a closed contribution boundary" must _e3 {
      "leave a runtime without CAR defaults unchanged" in {
        Given("R1; a packaged CAR without resource settings and a runtime without a selected descriptor")
        _with_car(Map.empty) { (_, root, car) =>
          val supplied = _resolved()

          When("real preflight resolves each absence case")
          val emptycar = _preflight_defaults(supplied, _policy(root, Vector(car)))
          val noselected = _preflight_defaults(supplied, _policy(root, Vector.empty))

          Then("neither provides an implicit file grant or resource limit")
          Vector(emptycar, noselected).foreach { defaults =>
            defaults.values shouldBe empty
            RuntimeConfig._with_selected_resource_defaults(supplied, defaults) shouldBe supplied
          }
        }
      }

      "ignore unselected CAR settings and arbitrary external descriptor or assembly values" in {
        Given("R1; a CAR with resource defaults and an explicitly selected external descriptor without CAR provenance")
        _with_car(_car_values) { (workspace, root, car) =>
          val external = Files.writeString(workspace.resolve("external.yaml"),
            "subsystem: external\ncomponents:\n  - namespace: org.example\n    id: External\n    version: 0.1.0\nconfig:\n" +
              s"  ${_roots_key}: '${root}'\n  ${_limit_key}: '9'\n", UTF_8)
          val supplied = _resolved(Map(RuntimeConfig.subsystemDescriptorKey -> ConfigurationValue.StringValue(external.toString)))
          val direct = CncfRuntime.AssemblyConfigurationContribution(_resolved(),
            Map(_roots_key -> root.toString, _limit_key -> "11"),
            Some("external-assembly"))

          When("real preflight selects the external descriptor while the CAR is available but unselected")
          val defaults = _preflight_defaults(supplied, _policy(root, Vector(car)), Some(direct))
          val merged = RuntimeConfig._with_selected_resource_defaults(supplied, defaults)

          Then("neither the unselected CAR nor external values become selected resource defaults")
          defaults.values shouldBe empty
          RuntimeConfig.settingsFrom(merged).resourceUrlPolicy.fileRoots shouldBe empty
          merged.configuration.values shouldBe supplied.configuration.values
        }
      }

      "exclude resource entries whose CAR provenance was replaced by an external assembly override" in {
        Given("R1; a selected CAR and an external assembly overriding both resource groups")
        _with_car(_car_values) { (workspace, root, car) =>
          val external = Files.writeString(workspace.resolve("override.yaml"),
            s"config:\n  ${_roots_key}: '${root}'\n  ${_limit_key}: '9'\n", UTF_8)
          val supplied = _resolved(Map(RuntimeConfig.assemblyDescriptorKey -> ConfigurationValue.StringValue(external.toString)))

          When("real preflight applies ordinary assembly override handling")
          val defaults = _preflight_defaults(supplied, _policy(root, Vector(car)))

          Then("overridden descriptor values carry no CAR resource default authority")
          defaults.values shouldBe empty
          RuntimeConfig._with_selected_resource_defaults(supplied, defaults).configuration.values shouldBe supplied.configuration.values
        }
      }

      "exclude every other resource, security, network, process, and data setting from global configuration" in {
        Given("R1,R3; eligible roots and byte limits mixed with unrelated CAR defaults")
        val excluded = Map(
          RuntimeConfig.resourceUrlHttpsHostsKey -> "example.test",
          RuntimeConfig.resourceTextusUrnFileRootsKey -> "/car/urn",
          RuntimeConfig.resourceUrnProvidersKey -> "invalid-provider",
          RuntimeConfig.resourceTreeFileRootsKey -> "/car/tree",
          RuntimeConfig.RESOURCE_TREE_QUERY_MAX_ENTRIES_KEY -> "1",
          RuntimeConfig.RESOURCE_WRITE_MAX_BYTES_KEY -> "1",
          RuntimeConfig.DEBUG_AUTH_ENABLED_KEY -> "true",
          RuntimeConfig.operationModeKey -> "develop",
          RuntimeConfig.commandExecutionModeKey -> "sync",
          "textus.datastore.kind" -> "sqlite",
          "textus.datastore.path" -> "/car/database.sqlite",
          CncfConfigurationParameterCatalog.SUBSYSTEM_PROCESS_EXECUTION_PLAN_KEY -> "invalid-process",
          CncfConfigurationParameterCatalog.SUBSYSTEM_STORAGE_PLAN_KEY -> "invalid-storage"
        )
        val defaults = _configuration(excluded ++ Map(_roots_key -> "/car/root", _limit_key -> "3"))

        When("the resource-only overlay is parsed as ordinary runtime settings")
        val merged = RuntimeConfig._with_selected_resource_defaults(_resolved(), defaults)
        val settings = RuntimeConfig.settingsFrom(merged)

        Then("only the two admitted setting groups contribute")
        merged.configuration.values.keySet shouldBe Set(_roots_key, _limit_key)
        settings.resourceUrlPolicy.normalizedFileRoots shouldBe Vector(Path.of("/car/root"))
        settings.resourceReadLimits.maximumBytes shouldBe 3
        settings.resourceUrlPolicy.httpsHosts shouldBe empty
        settings.textusUrnResourcePolicy shouldBe RuntimeConfig.settingsFrom(_resolved()).textusUrnResourcePolicy
        settings.urnResourceProviders shouldBe empty
        settings.resourceTreePolicy shouldBe RuntimeConfig.settingsFrom(_resolved()).resourceTreePolicy
        settings.debugAuthConfig.enabled shouldBe false
        settings.operationMode shouldBe RuntimeConfig.defaultOperationMode
        settings.commandExecutionMode shouldBe None
      }
    }
  }

  private def _take[A](value: Consequence[A]): A = value match {
    case Consequence.Success(result) => result
    case Consequence.Failure(conclusion) => fail(conclusion.show)
  }

  private def _failure[A](value: Consequence[A]): String = value match {
    case Consequence.Failure(conclusion) => conclusion.show
    case _ => fail("resource access unexpectedly succeeded")
  }

  private def _configuration(values: Map[String, String]): Configuration =
    Configuration(values.map { case (key, value) => key -> ConfigurationValue.StringValue(value) })

  private def _resolved(values: Map[String, ConfigurationValue] = Map.empty): ResolvedConfiguration =
    ResolvedConfiguration(Configuration(values), ConfigurationTrace.empty)

  private def _spellings(key: String): Vector[String] =
    Vector(key, key.replace("textus.", "textus.runtime."), key.replace("textus.", "cncf."),
      key.replace("textus.", "cncf.runtime."))

  private def _nested_values(entries: Vector[(Vector[String], ConfigurationValue)]): Map[String, ConfigurationValue] =
    entries.groupBy(_._1.head).map { case (key, grouped) =>
      val value = grouped.find(_._1.size == 1).map(_._2).getOrElse {
        ConfigurationValue.ObjectValue(_nested_values(grouped.map { case (segments, nested) =>
          segments.tail -> nested
        }))
      }
      key -> value
    }

  private def _with_properties[A](values: Map[String, String])(run: => A): A = {
    val keys = _spellings(_roots_key) ++ _spellings(_limit_key) ++ Vector("textus.config.file", "cncf.config.file")
    val previous = keys.map(key => key -> Option(System.getProperty(key)))
    try {
      keys.foreach(System.clearProperty)
      values.foreach { case (key, value) => System.setProperty(key, value) }
      run
    } finally {
      previous.foreach {
        case (key, Some(value)) => System.setProperty(key, value)
        case (key, None) => System.clearProperty(key)
      }
    }
  }

  private def _policy(root: Path, cars: Vector[Path]): RepositoryBootstrapPolicy =
    RepositoryBootstrapPolicy(componentFiles = cars.map(_.toString), baseDirectory = root, defaultRepositoriesEnabled = false)

  private def _preflight_defaults(
    supplied: ResolvedConfiguration,
    policy: RepositoryBootstrapPolicy,
    assembly: Option[CncfRuntime.AssemblyConfigurationContribution] = None
  ): Configuration = {
    val snapshot = _take(ConfigurationResolver.default.resolveSnapshot(Vector.empty))
    val contribution = assembly.getOrElse(CncfRuntime.AssemblyConfigurationContribution(_resolved(), Map.empty, None))
    _take(new CncfRuntime()._runtime_configuration_preflight(snapshot, contribution, supplied, policy,
      Vector.empty, Vector.empty)).resourcedefaults
  }

  private def _reference(path: Path): ResourceReference =
    _take(ResourceReference.parseC(path.toUri.toString))

  private def _context(runconfig: RuntimeConfig, supplied: ResolvedConfiguration): ExecutionContext = {
    val base = ExecutionContext.create()
    val global = GlobalRuntimeContext.create("selected-car-resource-defaults", runconfig, supplied,
      base.observability, AliasResolver.empty)
    ExecutionContext.create(global, base.runtime).withScope(global.createChildScope(ScopeKind.Action, "read-resource"))
  }

  private def _with_car[A](values: Map[String, String])(run: (Path, Path, Path) => A): A = {
    // Resolve the system temporary directory's symlinks before exercising file policy.
    val workspace = Files.createTempDirectory("selected-car-resource-defaults-").toRealPath()
    try {
      val root = Files.createDirectory(workspace.resolve("runtime root"))
      val car = workspace.resolve("application.car")
      Using.resource(new ZipOutputStream(Files.newOutputStream(car))) { zip =>
        val descriptor = """{"schemaVersion":3,"component":{"namespace":"org.example","id":"Application","version":"0.1.0"}}"""
        val config = if (values.isEmpty) "" else "config:\n" + values.toVector.sortBy(_._1)
          .map { case (key, value) => s"  $key: '${value.replace("'", "''")}'\n" }.mkString
        val assembly = "subsystem: application\ncomponents:\n  - namespace: org.example\n    id: Application\n    version: 0.1.0\n" + config
        Vector("component-descriptor.json" -> descriptor, "assembly-descriptor.yaml" -> assembly).foreach { case (name, content) =>
          zip.putNextEntry(new ZipEntry(name))
          zip.write(content.getBytes(UTF_8))
          zip.closeEntry()
        }
      }
      run(workspace, root, car)
    } finally {
      Using.resource(Files.walk(workspace)) { paths =>
        paths.iterator().asScala.toVector.reverse.foreach(Files.deleteIfExists(_))
      }
    }
  }
}
