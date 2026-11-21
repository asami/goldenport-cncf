package org.goldenport.cncf.processexecution

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Path
import org.goldenport.cncf.workspace.WorkspaceId
import org.goldenport.cncf.validation.ValidationExecutionModeV1 as M
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** Exact supported export framing and complete per-variant batch observations.
 * @since   Nov.  6, 2026
 * @version Nov. 21, 2026
 */
class SbtValidationDiscoverySpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _workspace = WorkspaceId.parseC("ordinary-project").toOption.get
  private val _binding = SbtValidationDiscovery.Binding("project", "project-tests", "1", "project.spec")
  private def _result(text: String, exit: Int = 0): ProcessExecutionResult = {
    val bytes = text.getBytes(UTF_8).toVector
    ProcessExecutionResult(ProcessExecutionTermination.Exited(exit), ProcessExecutionCapture(bytes, bytes.size.toLong, false),
      ProcessExecutionCapture(Vector.empty, 0L, false), Vector.empty, 13L, "ordinary-build")
  }
  private def _report(selected: SbtValidationMode.Case, provider: String = "fixture"): String = {
    val observation = M.Observation(selected.selector.suiteClassName, selected.selector.testName, selected.mode, provider)
    s"[info] ${selected.selector.suiteClassName}:\n" + observation.line + "\n" +
      "[info] Total number of tests run: 1\n[info] Suites: completed 1, aborted 0\n" +
      "[info] Tests: succeeded 1, failed 0, canceled 0, ignored 0, pending 0\n[info] All tests passed.\n"
  }

  "Ordinary TestCompile discovery" should {
    "interpret both actual export records without substituting a convenient log line" in {
      Given("a complete successful SBT 1.x occurrence with ordered fullClasspath and exportedProducts exports")
      val raw = _result("[info] compiling tests\n/project/target/test-classes:/cache/scalatest.jar\n[success] Total time: 1 s\n/project/target/test-classes\n")
      When("the producer interprets its closed reporting projection")
      val result = SbtValidationDiscovery.decode(_workspace, _binding, raw)
      Then("actual products and full classpath stay distinct under the existing declared artifact identity")
      result shouldBe SbtValidationDiscovery.Result.Available(SbtValidationDiscovery.Build(_workspace, _binding,
        Vector(Path.of("/project/target/test-classes")), Vector(Path.of("/project/target/test-classes"), Path.of("/cache/scalatest.jar"))))
      SbtValidationDiscovery.tasks shouldBe Vector("Test / compile", "export Test / fullClasspath", "export Test / exportedProducts")
    }

    "refuse missing, duplicate, ambiguous, partial and failed build projections" in {
      Given("unsupported records, incomplete capture and a failed actual build")
      val valid = "/project/classes:/cache/tests.jar\n/project/classes\n"
      val malformed = Vector("", "/project/classes\n", valid + "/other/last-convenient-line\n",
        "/project/classes\n/foreign/classes\n", "/project/classes:\n/project/classes\n", "relative\nrelative\n")
      val records = malformed.map(_result(_)) ++ Vector(_result(valid, 1),
        _result(valid).copy(stdout = _result(valid).stdout.copy(truncated = true)),
        _result(valid).copy(stdout = ProcessExecutionCapture(Vector(0xc3.toByte), 1L, false)))
      When("the same producer projection handles every unsupported occurrence")
      val results = records.map(SbtValidationDiscovery.decode(_workspace, _binding, _))
      Then("none supplies a successful or partial artifact view")
      results.foreach(_ shouldBe a[SbtValidationDiscovery.Result.Unavailable])
    }
  }

  "Exact managed batch observations" should {
    "retain more than sixteen selected operations and all required modes in one process result" in {
      Given("forty distinct selected operation/mode variants and their complete synthetic report segments")
      val cases = (1 to 20).toVector.flatMap(index => Vector("develop", "test").map(mode =>
        SbtValidationMode.Case(SbtScenarioSelection("project.spec.ProductSpec", "case " + index), mode)))
      When("the batch decoder interprets every exact child observation and successful single-test report")
      val decoded = SbtValidationMode.decodeBatch(cases, _result(cases.map(_report(_)).mkString), Some("fixture"))
      Then("all variants keep their exact identity and no operation is dropped to meet a logical-check cap")
      decoded.map(_.selected) shouldBe cases
      decoded.map(_.status).distinct shouldBe Vector("passed")
      decoded.flatMap(_.observation.map(_.mode)).toSet shouldBe Set("develop", "test")
    }

    "keep passed prefixes, actual failure and remaining not-executed variants separate" in {
      Given("one passed task followed by an observed failing task in the same ordered invocation")
      val cases = (1 to 3).toVector.map(index => SbtValidationMode.Case(SbtScenarioSelection("project.spec.ProductSpec", "case " + index), "develop"))
      val selected = cases(1)
      val observation = M.Observation(selected.selector.suiteClassName, selected.selector.testName, "develop", "fixture").line
      val failed = s"[info] ${selected.selector.suiteClassName}:\n" + observation + "\n" +
        s"[info] - ${selected.selector.testName} *** FAILED ***\n" +
        "[info] Total number of tests run: 1\n[info] Suites: completed 1, aborted 0\n" +
        "[info] Tests: succeeded 0, failed 1, canceled 0, ignored 0, pending 0\n[info] *** 1 TEST FAILED ***\n"
      When("the actual nonzero process and captured prefix are interpreted")
      val decoded = SbtValidationMode.decodeBatch(cases, _result(_report(cases.head) + failed, 1), Some("fixture"))
      Then("failure cannot turn the preceding pass or the unexecuted tail into aggregate success")
      decoded.map(_.status) shouldBe Vector("passed", "failed", "not-executed")
      val foreign = SbtValidationMode.decodeBatch(Vector(cases.head), _result(_report(cases.head, "live")), Some("fixture"))
      foreign.head.status shouldBe "unverified"
    }

    "leave non-test failures and incomplete failure reports unverified" in {
      Given("an observed exact case and nonzero exits without a complete matching test failure")
      val selected = SbtValidationMode.Case(SbtScenarioSelection("project.spec.ProductSpec", "case 1"), "develop")
      val observation = M.Observation(selected.selector.suiteClassName, selected.selector.testName, "develop", "fixture").line
      val header = s"[info] ${selected.selector.suiteClassName}:\n" + observation + "\n"
      val counts = "[info] Total number of tests run: 1\n[info] Suites: completed 1, aborted 0\n" +
        "[info] Tests: succeeded 0, failed 1, canceled 0, ignored 0, pending 0\n[info] *** 1 TEST FAILED ***\n"
      val reports = Vector(header + "[info] *** SUITE ABORTED ***\n", header + "[error] sbt failed\n",
        header + "[info] - unrelated *** FAILED ***\n" + counts,
        header + "[info] - case 1 *** FAILED ***\n" + counts.replace("[info] *** 1 TEST FAILED ***\n", ""))
      val complete = _result(header + "[info] - case 1 *** FAILED ***\n" + counts, 1)
      val truncated = complete.copy(stdout = complete.stdout.copy(truncated = true))
      When("the batch decoder assesses complete captures and a truncated capture")
      val results = reports.map(text => _result(text, 1)) :+ truncated
      Then("none is attributed as a formal failure of the selected case")
      results.foreach(result => SbtValidationMode.decodeBatch(Vector(selected), result, Some("fixture")).head.status shouldBe "unverified")
    }

    "reject generated missing child observations even when every test summary says PASS" in {
      Given("generated exact selectors with a report lacking its required child mode/provider observation")
      When("the decoder compares expected variants with that configured-only report")
      val property = Prop.forAll(Gen.choose(1, 100)) { index =>
        val selected = SbtValidationMode.Case(SbtScenarioSelection("project.spec.ProductSpec", "case " + index), "develop")
        val text = _report(selected).linesIterator.filterNot(_.startsWith(M.observationPrefix)).mkString("\n")
        SbtValidationMode.decodeBatch(Vector(selected), _result(text), Some("fixture")).head.status != "passed"
      }
      val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(32), property)
      Then("a successful process or selected label never supplies actual mode evidence")
      checked.passed shouldBe true
    }
  }

  "Scoped managed discovery and FULL selection" should {
    "use one immutable consumer selection for the actual compile, discovery and selected test" taggedAs(org.scalatest.tagobjects.Slow) in {
      import java.net.URLClassLoader
      import java.nio.file.{Files, Paths}
      import org.goldenport.cncf.component.{Component, ComponentId, ComponentInstanceId, ComponentOrigin}
      import org.goldenport.cncf.context.{ExecutionContext, GlobalContext, ScopeContext, ScopeKind}
      import org.goldenport.cncf.unitofwork.{UnitOfWork, UnitOfWorkInterpreter, UnitOfWorkOp}
      import org.goldenport.cncf.validation.{ValidationDiscoveryV1 as D, ValidationMetadataV1 as V}
      import org.goldenport.cncf.workarea.{FileWorkArea, WorkArea, WorkAreaGroup, WorkAreaId, WorkAreaSpace}
      import org.goldenport.cncf.workspace.{WorkspaceExecutionAdmission, WorkspaceGrant, WorkspaceId, WorkspacePermission, WorkspaceRelativePath}
      import org.goldenport.protocol.Protocol

      Given("a private managed build and one consumer selection owned by a separately granted workspace")
      val base = Files.createDirectories(Paths.get("target", "p7d-scoped-discovery").toAbsolutePath)
      val root = Files.createTempDirectory(base, "observation-")
      val checkout = Files.createDirectory(root.resolve("checkout"))
      val ownerroot = Files.createDirectory(root.resolve("selection-owner"))
      val project = Files.createDirectories(checkout.resolve("project"))
      val source = Files.createDirectories(checkout.resolve("src/test/scala/project/spec"))
      val owner = ComponentId("org.goldenport.spec.ScopedRealBuildOwner")
      val workspaceid = WorkspaceId.parseC("real-build").toOption.get
      val ownerid = WorkspaceId.parseC("selection-owner").toOption.get
      val selectionpath = ownerroot.resolve("selection.json")
      val selection = SbtScopedInput(owner, workspaceid, ownerid, "scope-1", "selection-1",
        WorkspaceRelativePath.parseC("selection.json").toOption.get)
      val envelope = s"""{"schema":"cncf.scoped-sbt-input.v1","kind":"consumer","component":"${owner.name}","buildWorkspace":"real-build","ownerWorkspace":"selection-owner","scope":"scope-1","selection":"selection-1","pluginSettings":{"fixture":true}}"""
      Files.writeString(selectionpath, envelope)
      Files.writeString(project.resolve("build.properties"), "sbt.version=1.9.7\n")
      val runtimepaths = _runtime_classpath
      val quotedpath = io.circe.Json.fromString(selectionpath.toString).noSpaces
      val unmanaged = runtimepaths.map(path => s"file(${io.circe.Json.fromString(path.toString).noSpaces})").mkString(", ")
      Files.writeString(checkout.resolve("build.sbt"), s"""scalaVersion := "3.3.8"
        |libraryDependencies += "org.scalatest" %% "scalatest" % "3.2.18" % Test
        |Test / unmanagedClasspath ++= {
        |  require(sys.props.get("sm.artifact.selection").contains($quotedpath), "wrong selected consumer envelope")
        |  Seq($unmanaged).map(Attributed.blank)
        |}
        |""".stripMargin)
      Files.writeString(source.resolve("SelectedSpec.scala"), s"""package project.spec
        |import org.scalatest.wordspec.AnyWordSpec
        |import org.scalatest.Outcome
        |import org.goldenport.cncf.config.OperationMode
        |import org.goldenport.cncf.validation.{ValidationDeclarationProvider, ValidationExecutionModeV1 as M}
        |import org.goldenport.cncf.validation.ValidationMetadataV1.{Declaration, Purpose}
        |import org.goldenport.cncf.validation.annotation.ValidationSpecification
        |class FullDeclaration extends ValidationDeclarationProvider {
        |  def declaration: Declaration = Declaration(purposes = Some(Set(Purpose.FULL)),
        |    requiredSemantics = Set("executionModes"), executionModes = Some(Set("test")))
        |}
        |@ValidationSpecification(declaration = classOf[FullDeclaration])
        |class SelectedSpec extends AnyWordSpec {
        |  "selected consumer" should { "run the full case" in {
        |    assert(sys.props.get("sm.artifact.selection").contains($quotedpath))
        |    val input = scala.io.Source.fromFile($quotedpath)
        |    try assert(input.mkString.contains("selection-1")) finally input.close()
        |  }}
        |  override protected def withFixture(test: NoArgTest): Outcome = {
        |    val actual = M.consumeC(test.configMap.toMap, getClass.getName, test.name, OperationMode.Test, "fixture")
        |    assert(actual.isSuccess)
        |    println(actual.toOption.get.line)
        |    super.withFixture(test)
        |  }
        |}
        |""".stripMargin)
      val workspace = WorkspaceExecutionAdmission.createC(Vector(workspaceid -> checkout, ownerid -> ownerroot),
        Vector(WorkspaceGrant(owner, workspaceid, WorkspacePermission.Manage),
          WorkspaceGrant(owner, ownerid, WorkspacePermission.ReadOnly))).toOption.get
      val binding = workspace.bindingC(workspaceid).toOption.get
      val compilecap = ProcessCapabilityId.parseC("scoped-test-compile").toOption.get
      val testcap = ProcessCapabilityId.parseC("scoped-test-only").toOption.get
      val limits = ProcessExecutionLimits(Some(60000L), Some(600000L), Some(5000L), Some(65536L),
        Some(67108864L), Some(8388608L), Some(64L), Some(65536L), Some(16L), Some(65536L), Some(65536L))
      val environment = Vector("PATH", "HOME", "JAVA_HOME").flatMap(key => Option(System.getenv(key)).map(key -> _)).toMap
      val declaration = SbtValidationDiscovery.Binding("project", "project-tests", "1", "project.spec")
      val compile = SbtProgramDefinition.createC(binding, SbtOperation.TestCompile, compilecap,
        "scoped-test-compile", Paths.get("sbt"), limits, environment, discovery = Some(declaration)).toOption.get
      val test = SbtProgramDefinition.createC(binding, SbtOperation.TestOnly, testcap,
        "scoped-test-only", Paths.get("sbt"), limits, environment,
        validationMode = Some(SbtValidationMode.Binding(Set("test"), "fixture")),
        discovery = Some(declaration), discoveredTests = true).toOption.get
      val policy = ProcessExecutionPolicy.createC(Vector(compile, test)).toOption.get
      val admission = ProcessExecutionAdmission.forComponentsC(policy,
        Map(owner -> Vector(ProcessExecutionGrant(compilecap), ProcessExecutionGrant(testcap)))).toOption.get
      val space = new WorkAreaSpace(WorkAreaGroup(new FileWorkArea(WorkAreaId("scoped_real_build_spec"),
        WorkArea.Limit(None, None, None), Files.createDirectory(root.resolve("workareas")))))
      GlobalContext.set(GlobalContext(space))
      val execution = ExecutionContext.create()
      val runtime = ScopeContext(ScopeKind.Runtime, "scoped-real-build-spec", None, execution.observability,
        processexecutiondriveroption = Some(new LocalProcessExecutionDriver()),
        processexecutionadmissionoption = Some(admission), workspaceExecutionAdmissionOption = Some(workspace))
      val component = Component.create(owner.name, owner, ComponentInstanceId.default(owner), Protocol.empty)
      val scope = Component.Context(owner.name, runtime, component, ComponentOrigin.Unknown)
        .createChildScope(ScopeKind.Service, "build").createChildScope(ScopeKind.Action, "test")
      val context = execution.withScope(scope)
      val uow = new UnitOfWork(context)
      var loader: Option[URLClassLoader] = None
      try {
        When("the real typed UoW runs TestCompile and the metadata-selected FULL case")
        val compiletarget = SbtTarget(workspaceid, validationDiscovery = true, scopedInput = Some(selection))
        val compileargs = SbtExecutionAdmission.resolveC(context, compiletarget, SbtOperation.TestCompile).toOption.get.effectiveArguments
        val compiled = new UnitOfWorkInterpreter(uow).interpret(UnitOfWorkOp.SbtExec(compiletarget, SbtOperation.TestCompile)).toOption.get
        Files.write(root.resolve("compile-stdout.bin"), compiled.processResult.stdout.content.toArray)
        Files.write(root.resolve("compile-stderr.bin"), compiled.processResult.stderr.content.toArray)
        compiled.processResult.termination shouldBe ProcessExecutionTermination.Exited(0)
        compiled.outcome shouldBe SbtExecutionOutcome.Succeeded
        compiled.target.scopedInput shouldBe Some(selection)
        val build = compiled.discovery.get.asInstanceOf[SbtValidationDiscovery.Result.Available].build
        val product = build.products.find(path => Files.exists(path.resolve("project/spec/SelectedSpec.class"))).get.toRealPath()
        build.fullClasspath should contain (product)
        val actualLoader = new URLClassLoader(build.fullClasspath.map(_.toUri.toURL).toArray, getClass.getClassLoader)
        loader = Some(actualLoader)
        val artifacts = Vector(D.ArtifactScope(declaration.componentIdentity, declaration.artifactIdentity,
          declaration.artifactRevision, "source-1", product, declaration.packagePrefix))
        val inventory = D.discoverC(D.DiscoveryScope("scoped-real-build", "1", artifacts), actualLoader).toOption.get
        inventory.completeness shouldBe D.Completeness.COMPLETE
        inventory.diagnostics shouldBe empty
        val selected = D.selectC(inventory, D.SelectionQuery(purposes = Set(V.Purpose.FULL), standardFramework = true)).toOption.get
        selected.map(_.selector.testName) shouldBe Vector("selected consumer should run the full case")
        val operation = selected.head
        operation.metadata.get.executionModes shouldBe Some(Set("test"))
        val exact = SbtScenarioSelection(operation.selector.suiteClassName, operation.selector.testName)
        val variant = SbtValidationMode.Case(exact, "test", Some("fixture"))
        val testtarget = SbtTarget(workspaceid, validationBatch = Vector(variant), scopedInput = Some(selection))
        val testargs = SbtExecutionAdmission.resolveC(context, testtarget, SbtOperation.TestOnly).toOption.get.effectiveArguments
        val tested = new UnitOfWorkInterpreter(uow).interpret(UnitOfWorkOp.SbtExec(testtarget, SbtOperation.TestOnly)).toOption.get
        Files.write(root.resolve("test-stdout.bin"), tested.processResult.stdout.content.toArray)
        Files.write(root.resolve("test-stderr.bin"), tested.processResult.stderr.content.toArray)
        Files.writeString(root.resolve("result.txt"), s"compile=$compileargs\ntest=$testargs\ndiscovery=${compiled.discovery}\nselected=$selected\ncompileTermination=${compiled.processResult.termination}\ntestTermination=${tested.processResult.termination}\nbatch=${tested.batch}\n")
        Then("the exact selected path precedes all tasks and both real processes retain their results")
        val property = "-Dsm.artifact.selection=" + selectionpath.toString
        compileargs.count(_ == property) shouldBe 1
        compileargs.indexOf(property) should be < compileargs.indexOf("Test / compile")
        compileargs.takeRight(3) shouldBe SbtValidationDiscovery.tasks
        testargs.count(_ == property) shouldBe 1
        testargs.indexOf(property) should be < testargs.indexWhere(_.startsWith(";testOnly "))
        tested.target.scopedInput shouldBe Some(selection)
        tested.processResult.termination shouldBe ProcessExecutionTermination.Exited(0)
        tested.outcome shouldBe SbtExecutionOutcome.Succeeded
        tested.batch.get.map(_.selected) shouldBe Vector(variant)
        tested.batch.get.map(_.status) shouldBe Vector("passed")
        Files.readString(selectionpath) shouldBe envelope
        info(s"Scoped managed SBT evidence: $root; selected=${operation.selector}; compile=${compiled.processResult.termination}; test=${tested.processResult.termination}; batch=${tested.batch}")
      } finally {
        loader.foreach(_.close())
        uow.dispose()
      }
    }
  }

  private def _runtime_classpath: Vector[Path] = {
    import java.io.File
    import java.net.URLClassLoader
    import java.nio.file.{Files, Paths}
    val properties = Option(System.getProperty("java.class.path")).toVector
      .flatMap(_.split(java.util.regex.Pattern.quote(File.pathSeparator)).toVector).filter(_.nonEmpty)
      .map(value => Paths.get(value).toAbsolutePath.normalize())
    var current = Option(getClass.getClassLoader)
    var entries = Vector.empty[Path]
    while (current.nonEmpty) {
      current.foreach {
        case loader: URLClassLoader => entries ++= loader.getURLs.toVector.filter(_.getProtocol == "file")
          .flatMap(url => scala.util.Try(Paths.get(url.toURI).toAbsolutePath.normalize()).toOption)
        case _ => ()
      }
      current = current.flatMap(value => Option(value.getParent))
    }
    val main = Paths.get(classOf[org.goldenport.cncf.validation.ValidationDeclarationProvider]
      .getProtectionDomain.getCodeSource.getLocation.toURI).toAbsolutePath.normalize()
    (Vector(main) ++ properties ++ entries).distinct.filter(path => Files.exists(path)).filterNot { path =>
      val name = path.getFileName.toString
      name.startsWith("sbt-launch") || name.startsWith("scala-library-2.12") ||
        name.startsWith("scala-reflect-2.12") || name.startsWith("scala-compiler-2.12")
    }
  }

  "An ordinary typed TestCompile build" should {
    "retain its real export streams and select the compiled test product" taggedAs(org.scalatest.tagobjects.Slow) in {
      Given("a private SBT 1.9.7 Java test source and independently admitted workspace and process grants")
      import java.nio.file.{Files, Paths}
      import org.goldenport.cncf.component.{Component, ComponentId, ComponentInstanceId, ComponentOrigin}
      import org.goldenport.cncf.context.{ExecutionContext, GlobalContext, ScopeContext, ScopeKind}
      import org.goldenport.cncf.unitofwork.{UnitOfWork, UnitOfWorkInterpreter, UnitOfWorkOp}
      import org.goldenport.cncf.workarea.{FileWorkArea, WorkArea, WorkAreaGroup, WorkAreaId, WorkAreaSpace}
      import org.goldenport.cncf.workspace.{WorkspaceExecutionAdmission, WorkspaceGrant, WorkspaceId, WorkspacePermission}
      import org.goldenport.protocol.Protocol

      val base = Files.createDirectories(Paths.get("target", "p611-real-sbt-discovery").toAbsolutePath)
      val root = Files.createTempDirectory(base, "observation-")
      val checkout = Files.createDirectory(root.resolve("checkout"))
      val project = Files.createDirectories(checkout.resolve("project"))
      val source = Files.createDirectories(checkout.resolve("src/test/java/project/spec"))
      Files.writeString(project.resolve("build.properties"), "sbt.version=1.9.7\n")
      Files.writeString(checkout.resolve("build.sbt"), "scalaVersion := \"2.12.18\"\n")
      Files.writeString(source.resolve("Product.java"), "package project.spec; public class Product {}\n")
      val owner = ComponentId("org.goldenport.spec.RealBuildOwner")
      val workspaceid = WorkspaceId.parseC("real-build").toOption.get
      val capability = ProcessCapabilityId.parseC("real-build-test-compile").toOption.get
      val workspace = WorkspaceExecutionAdmission.createC(Vector(workspaceid -> checkout),
        Vector(WorkspaceGrant(owner, workspaceid, WorkspacePermission.Manage))).toOption.get
      val binding = workspace.bindingC(workspaceid).toOption.get
      val limits = ProcessExecutionLimits(Some(60000L), Some(600000L), Some(5000L), Some(65536L),
        Some(67108864L), Some(8388608L), Some(64L), Some(65536L), Some(16L), Some(65536L), Some(65536L))
      val environment = Vector("PATH", "HOME", "JAVA_HOME").flatMap(key => Option(System.getenv(key)).map(key -> _)).toMap
      val declaration = SbtValidationDiscovery.Binding("project", "project-tests", "1", "project.spec")
      val definition = SbtProgramDefinition.createC(binding, SbtOperation.TestCompile, capability,
        "real-build-test-compile", Paths.get("sbt"), limits, environment, discovery = Some(declaration)).toOption.get
      val policy = ProcessExecutionPolicy.createC(Vector(definition)).toOption.get
      val admission = ProcessExecutionAdmission.forComponentsC(policy,
        Map(owner -> Vector(ProcessExecutionGrant(capability)))).toOption.get
      val space = new WorkAreaSpace(WorkAreaGroup(new FileWorkArea(WorkAreaId("real_build_spec"),
        WorkArea.Limit(None, None, None), Files.createDirectory(root.resolve("workareas")))))
      GlobalContext.set(GlobalContext(space))
      val execution = ExecutionContext.create()
      val runtime = ScopeContext(ScopeKind.Runtime, "real-build-spec", None, execution.observability,
        processexecutiondriveroption = Some(new LocalProcessExecutionDriver()),
        processexecutionadmissionoption = Some(admission), workspaceExecutionAdmissionOption = Some(workspace))
      val component = Component.create(owner.name, owner, ComponentInstanceId.default(owner), Protocol.empty)
      val scope = Component.Context(owner.name, runtime, component, ComponentOrigin.Unknown)
        .createChildScope(ScopeKind.Service, "build").createChildScope(ScopeKind.Action, "test")
      val context = execution.withScope(scope)
      val target = SbtTarget(workspaceid, validationDiscovery = true)
      val resolved = SbtExecutionAdmission.resolveC(context, target, SbtOperation.TestCompile).toOption.get
      val uow = new UnitOfWork(context)
      try {
        When("the actual UnitOfWork executes the ordinary compile and export tasks once")
        val actual = new UnitOfWorkInterpreter(uow).interpret(UnitOfWorkOp.SbtExec(target, SbtOperation.TestCompile)).toOption.get
        val result = actual.processResult
        Files.write(root.resolve("stdout.bin"), result.stdout.content.toArray)
        Files.write(root.resolve("stderr.bin"), result.stderr.content.toArray)
        val evidence = Vector("workspace=" + checkout, "program=" + resolved.definition._executable_location,
          "arguments=" + resolved.effectiveArguments.mkString("\n  "), "termination=" + result.termination,
          "stdoutBytes=" + result.stdout.byteCount, "stdoutTruncated=" + result.stdout.truncated,
          "stderrBytes=" + result.stderr.byteCount, "stderrTruncated=" + result.stderr.truncated,
          "elapsedMillis=" + result.elapsedMillis, "safeProgramIdentity=" + result.safeProgramIdentity,
          "artifacts=" + result.artifacts, "discovery=" + actual.discovery).mkString("\n")
        Files.writeString(root.resolve("result.txt"), evidence + "\n")
        Then("complete real evidence identifies the compiled product in the exact classpath")
        withClue("retained evidence: " + root + "\n") {
          result.termination shouldBe ProcessExecutionTermination.Exited(0)
          actual.discovery shouldBe a[Some[?]]
          withClue("discovery: " + actual.discovery + "\n") {
            actual.discovery.get shouldBe a[SbtValidationDiscovery.Result.Available]
          }
          val build = actual.discovery.get.asInstanceOf[SbtValidationDiscovery.Result.Available].build
          build.products.exists(path => Files.exists(path.resolve("project/spec/Product.class"))) shouldBe true
          build.products.forall(build.fullClasspath.contains) shouldBe true
        }
      } finally uow.dispose()
    }
  }
}
