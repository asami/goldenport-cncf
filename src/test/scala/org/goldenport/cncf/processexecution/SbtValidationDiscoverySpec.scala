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
 * @version Nov.  7, 2026
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
