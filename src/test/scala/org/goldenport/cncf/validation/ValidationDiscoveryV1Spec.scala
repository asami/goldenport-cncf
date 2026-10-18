package org.goldenport.cncf.validation

import java.net.URLClassLoader
import java.nio.file.{Files, Path}
import java.util.jar.{JarEntry, JarFile, JarOutputStream}
import scala.concurrent.duration.*
import scala.jdk.CollectionConverters.*
import org.goldenport.Consequence
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.{Args, GivenWhenThen, Reporter}
import org.scalatest.events.{Event, TestSucceeded}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.goldenport.cncf.validation.fixtures.phase103.valid.*
import org.goldenport.cncf.validation.fixtures.phase103.invalid.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.ReasoningLevel
import ValidationDiscoveryV1.*
import ValidationMetadataV1.*

/** Executable scope, framework registration, annotation and provenance contract.
 * @since   Oct. 18, 2026
 * @version Oct. 18, 2026
 */
class ValidationDiscoveryV1Spec extends AnyWordSpec with Matchers with GivenWhenThen {
  "ValidationDiscoveryV1" should {
    "enumerate real FunSuite and WordSpec registrations in the explicit package" in {
      Given("the actual compiled test artifact, a bounded package and the caller's loader")
      val scope = _scope()
      val fun = new FunMetadataFixtureSpec
      val word = new WordMetadataFixtureSpec
      When("the public API discovers actual registered test names")
      val inventory = _discover(scope)
      Then("complete inventory selectors equal each framework's registration without invented suite membership")
      inventory.completeness shouldBe Completeness.COMPLETE
      inventory.diagnostics shouldBe empty
      inventory.operations.filter(_.selector.suiteClassName == fun.getClass.getName).map(_.selector.testName).toSet shouldBe fun.testNames
      inventory.operations.filter(_.selector.suiteClassName == word.getClass.getName).map(_.selector.testName).toSet shouldBe word.testNames
      inventory.operations.forall(_.metadata.nonEmpty) shouldBe true
    }

    "interpret all default and override fields exclusively through typed declarations" in {
      Given("registered default, focused, HEAVY and explicit clearing scenarios")
      val scope = _scope()
      When("discovery normalizes the actual registrations through their providers")
      val inventory = _discover(scope)
      val defaults = _named(inventory, "inherits defaults").metadata.get
      val focused = _named(inventory, "focused union").metadata.get
      val heavy = _named(inventory, "heavy replacement").metadata.get
      val cleared = _named(inventory, "cleared purposes").metadata.get
      Then("purposes replace, features union, duration/class override and requirements strengthen")
      defaults.purposes shouldBe Set(Purpose.ADMISSION, Purpose.FULL)
      defaults.origins shouldBe Origins(true, false)
      defaults.expectedDuration shouldBe Some(2000000000.nanos)
      defaults.durationClass shouldBe DurationClass.LONG_RUNNING
      focused.purposes shouldBe Set(Purpose.SMOKE, Purpose.FOCUSED)
      focused.features shouldBe Set(FeatureReference("component:fixture", "feature-a"), FeatureReference("component:fixture", "feature-b"))
      focused.expectedDuration shouldBe Some(500000000.nanos)
      focused.durationClass shouldBe DurationClass.NORMAL
      focused.executionRequirement.get.capabilities.map(_.identity) shouldBe Vector("base", "extra")
      focused.executionRequirement.get.reasoning shouldBe ReasoningLevel.Deep
      focused.executionRequirement.get.reviewRequired shouldBe true
      heavy.purposes shouldBe Set(Purpose.HEAVY)
      heavy.expectedDuration shouldBe defaults.expectedDuration
      heavy.durationClass shouldBe defaults.durationClass
      cleared.purposes shouldBe empty
      cleared.features shouldBe defaults.features
    }

    "use inherited effective class and method annotations without annotating overrides" in {
      Given("a concrete WordSpec inheriting defaults and one anchor while overriding another without annotation")
      val scope = _scope()
      When("discovery interprets registered full WordSpec names")
      val inventory = _discover(scope)
      val inherited = _named(inventory, "word behavior should inherit its anchor")
      val overridden = _named(inventory, "word behavior should use an unannotated override")
      Then("the inherited anchor applies and the effective unannotated override only receives class defaults")
      inherited.reference.specification shouldBe "word:spec"
      inherited.reference.operation shouldBe "inherited"
      inherited.metadata.get.purposes shouldBe Set(Purpose.SMOKE, Purpose.FOCUSED)
      inherited.metadata.get.origins shouldBe Origins(true, true)
      overridden.metadata.get.purposes shouldBe Set(Purpose.ADMISSION, Purpose.FULL)
      overridden.metadata.get.origins shouldBe Origins(true, false)
      overridden.provenance.operationRevision shouldBe "spec-2"
    }

    "retain absent and explicit empty declarations as distinct valid origins" in {
      Given("an unannotated suite and explicit empty providers at class and scenario level")
      val scope = _scope()
      When("discovery interprets the actual registrations")
      val inventory = _discover(scope)
      val absent = _named(inventory, "unannotated scenario")
      val classonly = _named(inventory, "empty class declaration")
      val operation = _named(inventory, "explicit empty provider")
      Then("no purpose is inferred and a provider returning Declaration() records its presence")
      absent.metadata.get.origins shouldBe Origins(false, false)
      absent.metadata.get.purposes shouldBe empty
      absent.metadata.get.features shouldBe empty
      classonly.metadata.get.origins shouldBe Origins(true, false)
      classonly.metadata.get.purposes shouldBe empty
      operation.metadata.get.origins shouldBe Origins(true, true)
      operation.metadata.get.purposes shouldBe Set(Purpose.ADMISSION, Purpose.FULL)
      absent.reference.specification shouldBe classOf[UnannotatedFixtureSpec].getName
      absent.reference.operation shouldBe absent.selector.testName
    }

    "execute exactly one discovered parameter case through its actual ScalaTest selector" in {
      Given("two concrete registered names with independent annotated helpers and a shared body")
      val inventory = _discover()
      val first = _named(inventory, "parameter case 1")
      val second = _named(inventory, "parameter case 2")
      val events = scala.collection.mutable.ArrayBuffer.empty[Event]
      val reporter = new Reporter {
        def apply(event: Event): Unit = { events += event; () }
      }
      val suite = Class.forName(first.selector.suiteClassName, false, _loader).getConstructor().newInstance()
        .asInstanceOf[org.scalatest.Suite]
      When("the typed selector is passed to Suite.run in process")
      val status = suite.run(Some(first.selector.testName), Args(reporter))
      status.waitUntilCompleted()
      Then("only that real parameter scenario reports TestSucceeded")
      first.reference.operation shouldBe "parameter:1"
      second.reference.operation shouldBe "parameter:2"
      first.selector.testName should not be second.selector.testName
      events.collect { case event: TestSucceeded => event.testName }.toVector shouldBe Vector("parameter case 1")
      status.succeeds() shouldBe true
    }

    "preserve deterministic output across repeated discovery and artifact permutations" in {
      Given("the same real artifact admitted for two distinct components")
      val artifacts = Vector(_artifact("component:a", "artifact:a"), _artifact("component:b", "artifact:b"))
      val baseline = _discover(_scope(artifacts))
      val permutations = Gen.oneOf(artifacts, artifacts.reverse)
      val property = Prop.forAll(permutations) { ordered =>
        val inventory = _discover(_scope(ordered))
        inventory == baseline
      }
      When("repetitions and generated permutations are discovered through the same caller loader")
      val repeated = Vector.fill(3)(_discover(_scope(artifacts)))
      val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(8), property)
      Then("rows and provenance are stable and equal scenario names remain distinct by component")
      repeated.distinct shouldBe Vector(baseline)
      checked.passed shouldBe true
      baseline.operations.map(_.reference).distinct.size shouldBe baseline.operations.size
      baseline.operations.filter(_.selector.testName == "parameter case 1").map(_.reference.component).toSet shouldBe Set("component:a", "component:b")
    }

    "keep logical references stable when only explicit revision provenance changes" in {
      Given("one admitted artifact with independent artifact/source revision inputs")
      val original = _artifact()
      val changed = original.copy(artifactRevision = "artifact-2", sourceRevision = "source-9")
      When("the metadata revision changes and discovery repeats")
      val before = _discover(_scope(Vector(original)))
      val after = _discover(_scope(Vector(changed)))
      Then("IDs and selectors remain equal while artifact/source and fallback operation revisions reflect input")
      after.operations.map(_.reference) shouldBe before.operations.map(_.reference)
      after.operations.map(_.selector) shouldBe before.operations.map(_.selector)
      after.operations.map(_.metadata) shouldBe before.operations.map(_.metadata)
      _named(after, "inherits defaults").provenance shouldBe Provenance("artifact:tests", "artifact-2", "source-9", "artifact-2", 1)
      _named(after, "focused union").provenance.operationRevision shouldBe "scenario-3"
      _named(after, "word behavior should use an unannotated override").provenance.operationRevision shouldBe "spec-2"
    }

    "preserve actual invalid ABI, provider and shadowed-default operations without selecting them" in {
      Given("public passing suites with malformed typed declaration providers")
      val classes = Vector(classOf[UnsupportedAbiFixtureSpec], classOf[BadDefaultFixtureSpec],
        classOf[UnsupportedSemanticsFixtureSpec], classOf[WrongProviderTypeFixtureSpec],
        classOf[AbstractProviderFixtureSpec], classOf[ArgumentProviderFixtureSpec], classOf[PrivateProviderFixtureSpec],
        classOf[ThrowingProviderFixtureSpec], classOf[NullProviderFixtureSpec], classOf[NegativeDurationFixtureSpec])
      When("the invalid fixture namespace is discovered")
      val inventory = _invalid_inventory()
      val rows = classes.map(clazz => inventory.operations.find(_.selector.suiteClassName == clazz.getName).get)
      Then("all actual registered rows retain selectors/provenance and fail metadata admission")
      rows.foreach { operation =>
        operation.selector.testName shouldBe "actual scenario"
        operation.metadata shouldBe None
        operation.diagnostics.map(_.code) should contain ("provider")
        operation.provenance.abiVersion shouldBe 1
      }
      val selected = selectC(inventory, SelectionQuery()).toOption.get
      selected.map(_.selector.suiteClassName).toSet.intersect(classes.map(_.getName).toSet) shouldBe empty
    }

    "invalidate only the affected actual operation for a bad scenario provider" in {
      Given("one bad operation provider and a valid sibling in the same real suite")
      val scope = _scope(Vector(_artifact().copy(packagePrefix = _invalid_prefix)))
      When("discovery interprets the suite's two registered rows")
      val inventory = _discover(scope)
      val rows = inventory.operations.filter(_.selector.suiteClassName == classOf[BadScenarioFixtureSpec].getName)
      Then("the bad row remains visible and the sibling remains normalized and selectable")
      rows.find(_.selector.testName == "actual scenario").get.metadata shouldBe None
      rows.find(_.selector.testName == "valid sibling").get.metadata.get.origins shouldBe Origins(false, false)
      selectC(inventory, SelectionQuery()).toOption.get should contain (rows.find(_.selector.testName == "valid sibling").get)
    }

    "invalidate suite operations for orphan, duplicate or malformed anchors without inventing operations" in {
      Given("fixtures with orphan names, duplicate anchors, arguments or non-void return types")
      val classes = Vector(classOf[OrphanAnchorFixtureSpec], classOf[DuplicateAnchorFixtureSpec],
        classOf[InvalidAnchorFixtureSpec], classOf[NonVoidAnchorFixtureSpec])
      When("anchors are matched against actual registration")
      val inventory = _invalid_inventory()
      val rows = inventory.operations.filter(x => classes.exists(_.getName == x.selector.suiteClassName))
      Then("only real scenarios survive and none of the containing suites is executable through selection")
      rows.size shouldBe classes.size
      rows.map(_.selector.testName).toSet shouldBe Set("actual scenario")
      rows.foreach(_.metadata shouldBe None)
      rows.foreach(_.diagnostics.map(_.code) should contain ("anchor"))
      inventory.operations.exists(_.selector.testName == "not a registered name") shouldBe false
    }

    "retain real fallback identities for malformed annotation identity and revision text" in {
      Given("malformed class and scenario annotation identity text")
      val scope = _scope(Vector(_artifact().copy(packagePrefix = _invalid_prefix)))
      When("invalid declarations are inventoried for static inspection")
      val inventory = _discover(scope)
      val classrow = inventory.operations.find(_.selector.suiteClassName == classOf[InvalidIdentityFixtureSpec].getName).get
      val scenariorow = inventory.operations.find(_.selector.suiteClassName == classOf[InvalidScenarioIdentityFixtureSpec].getName).get
      Then("real class/testName fallbacks remain inspectable without becoming selectable normalized entries")
      classrow.reference.specification shouldBe classOf[InvalidIdentityFixtureSpec].getName
      scenariorow.reference.operation shouldBe "actual scenario"
      classrow.metadata shouldBe None
      scenariorow.metadata shouldBe None
      scenariorow.provenance.operationRevision shouldBe "artifact-1"
    }

    "preserve colliding real rows and reject their shared logical reference" in {
      Given("two registered suites declaring the same specification and scenario identity")
      val scope = _scope(Vector(_artifact().copy(packagePrefix = _invalid_prefix)))
      val reference = OperationReference("component:fixture", "collision:spec", "collision:operation")
      When("the ambiguity is inventoried and directly resolved")
      val inventory = _discover(scope)
      val rows = inventory.operations.filter(_.reference == reference)
      val resolved = ValidationDiscoveryV1.resolveC(inventory, reference)
      Then("both rows survive with distinct selectors and neither has executable metadata")
      rows.size shouldBe 2
      rows.map(_.selector.suiteClassName).distinct.size shouldBe 2
      rows.foreach(_.metadata shouldBe None)
      rows.foreach(_.diagnostics.map(_.code) should contain ("ambiguity"))
      resolved shouldBe a[Consequence.Failure[?]]
    }

    "reject incompatible existing execution requirements through the S1 annotation path" in {
      Given("valid typed class and scenario providers carrying incompatible opaque risks")
      val scope = _scope(Vector(_artifact().copy(packagePrefix = _invalid_prefix)))
      When("the actual operation's declaration sources are resolved during discovery")
      val inventory = _discover(scope)
      val operation = inventory.operations.find(_.selector.suiteClassName == classOf[IncompatibleRequirementFixtureSpec].getName).get
      Then("the real operation survives as invalid metadata without selecting a weaker requirement")
      operation.selector.testName shouldBe "actual scenario"
      operation.metadata shouldBe None
      operation.diagnostics.map(_.code) should contain ("metadata")
      selectC(inventory, SelectionQuery()).toOption.get should not contain operation
    }

    "report unsupported nonconstructible and nested suites at scope level" in {
      Given("public suites with required/private constructors and an ordinary nested-suite registration")
      val scope = _scope(Vector(_artifact().copy(packagePrefix = _invalid_prefix)))
      When("the namespace is discovered without executing any body")
      val inventory = _discover(scope)
      Then("omitted unsupported suite execution is explicit and makes the scope partial")
      inventory.completeness shouldBe Completeness.PARTIAL
      inventory.diagnostics.map(_.code) should contain ("unsupported-suite")
      inventory.diagnostics.exists(_.source == classOf[NestedSuiteFixtureSpec].getName) shouldBe true
      inventory.operations.exists(_.selector.suiteClassName == classOf[ArgumentSuiteFixtureSpec].getName) shouldBe false
    }

    "distinguish complete explicit empty scope and an enumerated empty namespace" in {
      Given("no artifacts and a valid artifact with an absent but valid package namespace")
      val emptyscope = _scope(Vector.empty)
      val namespace = _scope(Vector(_artifact().copy(packagePrefix = _valid_prefix + ".absent")))
      When("each explicit scope is discovered")
      val results = Vector(_discover(emptyscope), _discover(namespace))
      Then("successful empty enumeration is complete with no fabricated coverage claim")
      results.foreach(_.completeness shouldBe Completeness.COMPLETE)
      results.foreach(_.operations shouldBe empty)
      results.foreach(_.diagnostics shouldBe empty)
    }

    "retain usable rows in a partial scope and report unknown when no artifact can be enumerated" in {
      Given("a corrupt local JAR and an independently usable compiled directory")
      val jar = Files.createTempFile("p103-invalid-", ".jar").toRealPath()
      Files.writeString(jar, "not a jar")
      val bad = _artifact(artifact = "artifact:bad").copy(root = jar)
      try {
        When("mixed and solely unreadable JAR scopes are enumerated")
        val partial = _discover(_scope(Vector(_artifact(), bad)))
        val unknown = _discover(_scope(Vector(bad)))
        Then("partial preserves good operations and unknown preserves the scope failure")
        partial.completeness shouldBe Completeness.PARTIAL
        partial.operations should not be empty
        partial.diagnostics.map(_.code) should contain ("enumeration")
        unknown.completeness shouldBe Completeness.UNKNOWN
        unknown.operations shouldBe empty
        unknown.diagnostics.map(_.code) should contain ("enumeration")
      } finally {
        Files.deleteIfExists(jar)
      }
    }

    "report a caller loader failure as partial without owning or replacing the loader" in {
      Given("a caller-owned loader that refuses one actual suite class")
      val loader = new ClassLoader(_loader) {
        override def loadClass(name: String, resolve: Boolean): Class[?] =
          if (name == classOf[FunMetadataFixtureSpec].getName) throw new ClassNotFoundException("private loader detail")
          else super.loadClass(name, resolve)
      }
      When("discovery uses that exact loader")
      val inventory = _discover(loader = loader)
      Then("other real suites survive and a bounded loading diagnostic records incomplete scope")
      inventory.completeness shouldBe Completeness.PARTIAL
      inventory.operations.exists(_.selector.suiteClassName == classOf[WordMetadataFixtureSpec].getName) shouldBe true
      inventory.diagnostics.exists(x => x.code == "loading" && x.source == classOf[FunMetadataFixtureSpec].getName) shouldBe true
      inventory.diagnostics.exists(_.message.contains("private loader detail")) shouldBe false
    }

    "discover actual local JAR registrations and reject parent-loader artifact shadowing" in {
      Given("a local JAR containing the compiled fixture namespace and a caller-owned child-first loader")
      val jar = _fixture_jar()
      val artifact = _artifact().copy(root = jar)
      val loader = _jar_loader(jar)
      try {
        When("the same admitted JAR is discovered with its loader and with a parent that shadows it")
        val admitted = _discover(_scope(Vector(artifact)), loader)
        val shadowed = _discover(_scope(Vector(artifact)), _loader)
        Then("only matching code-source suites supply inventory rows")
        admitted.completeness shouldBe Completeness.COMPLETE
        admitted.operations.map(_.selector) shouldBe _discover().operations.map(_.selector)
        admitted.operations.forall(_.metadata.nonEmpty) shouldBe true
        shadowed.completeness shouldBe Completeness.PARTIAL
        shadowed.operations shouldBe empty
        shadowed.diagnostics.map(_.code) should contain ("provenance")
      } finally {
        loader.close()
        Files.deleteIfExists(jar)
      }
    }

    "retain ordinary registrations and report partial discovery for a linked descendant directory" in {
      Given("real fixture classes in a canonical directory and an external directory of compiled registrations")
      val directory = _fixture_directory()
      try {
        val outside = _fixture_directory()
        try {
          val loader = _jar_loader(directory)
          try {
            val artifact = _artifact().copy(root = directory)
            val scope = _scope(Vector(artifact))
            When("the ordinary directory namespace is discovered through its caller-owned loader")
            val baseline = _discover(scope, loader)
            Then("the baseline contains complete real registered operations")
            baseline.completeness shouldBe Completeness.COMPLETE
            baseline.operations should not be empty
            Given("a descendant link to the external namespace")
            val link = directory.resolve(_valid_prefix.replace('.', '/')).resolve("outside")
            Files.createSymbolicLink(link, outside.resolve(_valid_prefix.replace('.', '/')))
            When("discovery encounters the descendant directory link beside ordinary registrations")
            val inventory = _discover(scope, loader)
            Then("ordinary operations survive unchanged and the skipped external namespace is explicit")
            inventory.operations shouldBe baseline.operations
            inventory.completeness shouldBe Completeness.PARTIAL
            inventory.diagnostics shouldBe Vector(Diagnostic("enumeration", artifact.artifactIdentity,
              "Symbolic links within the package namespace were skipped"))
          } finally {
            loader.close()
          }
        } finally {
          _delete_fixture_directory(outside)
        }
      } finally {
        _delete_fixture_directory(directory)
      }
    }

    "retain ordinary registrations and report partial discovery for a linked class file" in {
      Given("real fixture classes in a canonical directory and an external compiled suite class")
      val directory = _fixture_directory()
      try {
        val outside = _fixture_directory()
        try {
          val loader = _jar_loader(directory)
          try {
            val artifact = _artifact().copy(root = directory)
            val scope = _scope(Vector(artifact))
            When("the ordinary directory namespace is discovered through its caller-owned loader")
            val baseline = _discover(scope, loader)
            Then("the baseline contains complete real registered operations")
            baseline.completeness shouldBe Completeness.COMPLETE
            baseline.operations should not be empty
            Given("a descendant class link to the external suite bytes")
            val namespace = _valid_prefix.replace('.', '/')
            val link = directory.resolve(namespace).resolve("OutsideFixtureSpec.class")
            Files.createSymbolicLink(link, outside.resolve(namespace).resolve("FunMetadataFixtureSpec.class"))
            When("discovery encounters the descendant class link beside ordinary registrations")
            val inventory = _discover(scope, loader)
            Then("ordinary operations survive unchanged and the skipped external class is explicit")
            inventory.operations shouldBe baseline.operations
            inventory.completeness shouldBe Completeness.PARTIAL
            inventory.diagnostics shouldBe Vector(Diagnostic("enumeration", artifact.artifactIdentity,
              "Symbolic links within the package namespace were skipped"))
          } finally {
            loader.close()
          }
        } finally {
          _delete_fixture_directory(outside)
        }
      } finally {
        _delete_fixture_directory(directory)
      }
    }

    "admit providers by the annotated suite artifact before invoking their declarations" in {
      Given("JAR-loaded suites whose declaration providers are deliberately resolved from the parent directory")
      val jar = _fixture_jar()
      val loader = _jar_loader(jar, providers = false)
      try {
        When("the annotated suite and mismatching providers are inspected")
        val inventory = _discover(_scope(Vector(_artifact().copy(root = jar))), loader)
        Then("provider provenance failures invalidate metadata while executable enumeration stays complete")
        inventory.completeness shouldBe Completeness.COMPLETE
        _named(inventory, "inherits defaults").metadata shouldBe None
        _named(inventory, "unannotated scenario").metadata should not be empty
        _named(inventory, "inherits defaults").diagnostics.map(_.code) should contain ("provider")
      } finally {
        loader.close()
        Files.deleteIfExists(jar)
      }
    }

    "bound artifact and overall diagnostics with explicit truncation" in {
      Given("many admitted artifact namespaces and a caller loader failing every class load")
      val artifacts = (1 to 30).toVector.map(x => _artifact(s"component:$x", s"artifact:$x").copy(packagePrefix = _invalid_prefix))
      val loader = new ClassLoader(_loader) {
        override def loadClass(name: String, resolve: Boolean): Class[?] =
          if (name.startsWith(_invalid_prefix + ".")) throw new ClassNotFoundException("sensitive" * 1000)
          else super.loadClass(name, resolve)
      }
      When("all failure sources are enumerated")
      val inventory = _discover(_scope(artifacts), loader)
      Then("diagnostics remain bounded, explicit and free of raw exception detail")
      inventory.completeness shouldBe Completeness.PARTIAL
      inventory.diagnostics.size shouldBe 256
      inventory.diagnostics.last.code shouldBe "truncated"
      inventory.diagnostics.foreach { diagnostic =>
        diagnostic.source.length should be <= 512
        diagnostic.message.length should be <= 512
        diagnostic.message should not include "sensitive"
      }
      val one = _discover(_scope(artifacts.take(1)), loader)
      one.diagnostics.size shouldBe 32
      one.diagnostics.last.code shouldBe "truncated"
    }

    "reject malformed discovery inputs as structured domain outcomes" in {
      Given("null, duplicate, malformed-text, package and local-root inputs")
      val artifact = _artifact()
      val scopes = Vector(null, _scope().copy(identity = " scope"), _scope().copy(revision = "r\n"),
        _scope().copy(artifacts = null), _scope(Vector(null)), _scope(Vector(artifact, artifact)),
        _scope(Vector(artifact.copy(componentIdentity = null))), _scope(Vector(artifact.copy(artifactRevision = ""))),
        _scope(Vector(artifact.copy(sourceRevision = "r" * 513))), _scope(Vector(artifact.copy(packagePrefix = "a.*"))),
        _scope(Vector(artifact.copy(packagePrefix = "../bad"))), _scope(Vector(artifact.copy(root = null))),
        _scope(Vector(artifact.copy(root = Path.of("relative")))))
      When("the public boundary admits each input without constructing suites")
      val outcomes = scopes.map(discoverC(_, _loader)) :+ discoverC(_scope(), null)
      Then("all malformed inputs fail through Consequence rather than an exception")
      outcomes.foreach(_ shouldBe a[Consequence.Failure[?]])
    }

    "reject symlink, noncanonical and missing artifact roots before discovery" in {
      Given("a caller-owned temporary directory and a symlink referring to it")
      val directory = Files.createTempDirectory("p103-root-").toRealPath()
      val link = directory.resolve("alias")
      Files.createSymbolicLink(link, directory)
      val artifact = _artifact()
      val roots = Vector(link, directory.resolve("."), directory.resolve("missing"))
      try {
        When("each unadmitted root is supplied to discovery")
        val outcomes = roots.map(root => discoverC(_scope(Vector(artifact.copy(root = root))), _loader))
        Then("root admission returns structured failures without traversing a different artifact")
        outcomes.foreach(_ shouldBe a[Consequence.Failure[?]])
      } finally {
        Files.deleteIfExists(link)
        Files.deleteIfExists(directory)
      }
    }
  }

  private val _valid_prefix = "org.goldenport.cncf.validation.fixtures.phase103.valid"
  private val _invalid_prefix = "org.goldenport.cncf.validation.fixtures.phase103.invalid"

  private def _loader: ClassLoader = getClass.getClassLoader
  private def _root: Path = Path.of(getClass.getProtectionDomain.getCodeSource.getLocation.toURI).toRealPath()
  private def _artifact(component: String = "component:fixture", artifact: String = "artifact:tests"): ArtifactScope =
    ArtifactScope(component, artifact, "artifact-1", "source-1", _root, _valid_prefix)
  private def _scope(artifacts: Vector[ArtifactScope] = Vector(_artifact())): DiscoveryScope =
    DiscoveryScope("fixture-scope", "scope-1", artifacts)
  private def _discover(scope: DiscoveryScope = _scope(), loader: ClassLoader = _loader): Inventory =
    discoverC(scope, loader).toOption.getOrElse(fail("valid scope must produce an inventory"))
  private def _invalid_inventory(): Inventory = _discover(_scope(Vector(_artifact().copy(packagePrefix = _invalid_prefix))))
  private def _named(inventory: Inventory, name: String): Operation =
    inventory.operations.find(_.selector.testName == name).getOrElse(fail(s"real scenario missing: $name"))

  private def _fixture_jar(): Path = {
    val path = Files.createTempFile("p103-fixtures-", ".jar").toRealPath()
    val output = new JarOutputStream(Files.newOutputStream(path))
    try {
      if (Files.isDirectory(_root)) {
        val stream = Files.walk(_root.resolve(_valid_prefix.replace('.', '/')))
        try {
          stream.iterator().asScala.filter(x => Files.isRegularFile(x) && x.toString.endsWith(".class")).foreach { entry =>
            output.putNextEntry(new JarEntry(_root.relativize(entry).toString.replace(java.io.File.separatorChar, '/')))
            Files.copy(entry, output)
            output.closeEntry()
          }
        } finally {
          stream.close()
        }
      } else {
        val sourcejar = new JarFile(_root.toFile)
        try {
          val prefix = _valid_prefix.replace('.', '/') + "/"
          sourcejar.entries().asScala.filter(x => !x.isDirectory && x.getName.startsWith(prefix) && x.getName.endsWith(".class")).foreach { entry =>
            val input = sourcejar.getInputStream(entry)
            try {
              output.putNextEntry(new JarEntry(entry.getName))
              input.transferTo(output)
              output.closeEntry()
            } finally {
              input.close()
            }
          }
        } finally {
          sourcejar.close()
        }
      }
    } finally {
      output.close()
    }
    path
  }

  private def _fixture_directory(): Path = {
    val directory = Files.createTempDirectory("p103-directory-").toRealPath()
    var copied = false
    try {
      val fixturejar = _fixture_jar()
      try {
        val sourcejar = new JarFile(fixturejar.toFile)
        try {
          val prefix = _valid_prefix.replace('.', '/') + "/"
          sourcejar.entries().asScala.filter(x => !x.isDirectory && x.getName.startsWith(prefix) && x.getName.endsWith(".class")).foreach { entry =>
            val target = directory.resolve(entry.getName)
            Files.createDirectories(target.getParent)
            val input = sourcejar.getInputStream(entry)
            try {
              Files.copy(input, target)
            } finally {
              input.close()
            }
          }
        } finally {
          sourcejar.close()
        }
      } finally {
        Files.deleteIfExists(fixturejar)
      }
      copied = true
      directory
    } finally {
      if (!copied) _delete_fixture_directory(directory)
    }
  }

  private def _delete_fixture_directory(directory: Path): Unit = {
    val stream = Files.walk(directory)
    try {
      stream.iterator().asScala.toVector.reverse.foreach(path => Files.deleteIfExists(path))
    } finally {
      stream.close()
    }
  }

  private def _jar_loader(jar: Path, providers: Boolean = true): URLClassLoader =
    new URLClassLoader(Array(jar.toUri.toURL), _loader) {
      override def loadClass(name: String, resolve: Boolean): Class[?] = {
        val child = name.startsWith(_valid_prefix + ".") && (providers || !name.endsWith("Declarations"))
        val clazz = if (child) {
          Option(findLoadedClass(name)).getOrElse(findClass(name))
        } else super.loadClass(name, false)
        if (resolve) resolveClass(clazz)
        clazz
      }
    }
}
