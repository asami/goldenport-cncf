package org.goldenport.cncf.processexecution

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import org.goldenport.Consequence
import org.goldenport.cncf.config.RuntimeConfig
import org.goldenport.cncf.context.{ExecutionContext, GlobalContext, ScopeContext, ScopeKind}
import org.goldenport.cncf.resource.{ResourceTreeAccess, ResourceTreeEntry, ResourceTreeLimits, ResourceTreeReference}
import org.goldenport.cncf.unitofwork.{UnitOfWork, UnitOfWorkInterpreter, UnitOfWorkOp}
import org.goldenport.cncf.workarea.WorkAreaSpace
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Jul. 17, 2026
 * @version Jul. 30, 2026
 * @author  ASAMI, Tomoharu
 */
final class ProcessExecutionWorkAreaSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen {
  private val _quota_excesses = Gen.choose(1, 32)

  "Process Execution WorkArea" should {
    "materialize only declared bounded input files inside the execution root" in {
      Given("an execution-scoped WorkArea and an admitted schema input file")
      val workspace = ProcessExecutionWorkArea.allocateC(WorkAreaSpace.create(RuntimeConfig.default)).toOption.get
      val name = ProcessArtifactName.parseC("schema").toOption.get
      val path = WorkAreaRelativePath.parseC("schema.json").toOption.get
      val input = ProcessExecutionInputFile.createC(name, path, "{}".getBytes(StandardCharsets.UTF_8).toVector, 16L).toOption.get
      val execution = _execution(Vector.empty, artifactBytes = 16L, workAreaBytes = 1024L, inputFiles = Vector(input))
      val root = workspace.root

      When("the runtime materializes the declared input")
      val materialized = try {
        workspace.materializeInputsC(execution).map { _ =>
          new String(Files.readAllBytes(root.resolve("schema.json")), StandardCharsets.UTF_8)
        }
      } finally {
        workspace.close()
      }

      Then("the file is confined to the WorkArea and is removed during cleanup")
      materialized.toOption shouldBe Some("{}")
      Files.exists(root) shouldBe false
    }

    "materialize an admitted logical resource tree only beneath its WorkArea target" in {
      Given("an admitted resource tree and an execution-scoped WorkArea target")
      val workspace = ProcessExecutionWorkArea.allocateC(WorkAreaSpace.create(RuntimeConfig.default)).toOption.get
      val reference = ResourceTreeReference.parseC("fixtures").toOption.get
      val entry = ResourceTreeEntry.createC("schema/input.json", "{}".getBytes(StandardCharsets.UTF_8).toVector).toOption.get
      val limits = ResourceTreeLimits(maxDepth = 2, maxEntries = 2, maxFileBytes = 16L, maxTotalBytes = 16L)
      val snapshot = ResourceTreeAccess.inMemory(Map(reference -> Vector(entry))).snapshot(reference, limits).toOption.get
      val target = WorkAreaRelativePath.parseC("admitted").toOption.get
      val tree = ProcessExecutionResourceTreeInput.createC(snapshot, target).toOption.get
      val execution = _execution(
        Vector.empty,
        artifactBytes = 16L,
        workAreaBytes = 1024L,
        resourceTrees = Vector(tree),
        allowedresourcetrees = Map(reference -> limits)
      )
      val root = workspace.root

      When("the runtime materializes the resolved tree before process launch")
      val materialized = try {
        workspace.materializeInputsC(execution).map { _ =>
          new String(Files.readAllBytes(root.resolve("admitted/schema/input.json")), StandardCharsets.UTF_8)
        }
      } finally {
        workspace.close()
      }

      Then("only the WorkArea-relative tree target is written and cleanup removes the host root")
      materialized.toOption shouldBe Some("{}")
      Files.exists(root) shouldBe false
    }

    "materialize a tree before driver start through UnitOfWork and reclaim it after terminal success" in {
      Given("a resolved tree input and a driver that can inspect only its supplied WorkArea")
      val reference = ResourceTreeReference.parseC("fixtures").toOption.get
      val target = WorkAreaRelativePath.parseC("fixtures").toOption.get
      val driverpath = WorkAreaRelativePath.parseC("fixtures/request.txt").toOption.get
      val content = "admitted-tree-content"
      val limits = ResourceTreeLimits(maxDepth = 1, maxEntries = 1, maxFileBytes = 64L, maxTotalBytes = 64L)
      val entry = ResourceTreeEntry.createC("request.txt", content.getBytes(StandardCharsets.UTF_8).toVector).toOption.get
      val snapshot = ResourceTreeAccess.inMemory(Map(reference -> Vector(entry))).snapshot(reference, limits).toOption.get
      val tree = ProcessExecutionResourceTreeInput.createC(snapshot, target).toOption.get
      val execution = _execution(
        Vector.empty,
        artifactBytes = 64L,
        workAreaBytes = 1024L,
        resourceTrees = Vector(tree),
        allowedresourcetrees = Map(reference -> limits)
      )
      val driver = new _WorkAreaObservingDriver(_result(execution), driverpath)
      val context = _context(driver)

      When("the UnitOfWork interprets the already-admitted Process Execution")
      val completed = new UnitOfWorkInterpreter(new UnitOfWork(context)).interpret(UnitOfWorkOp.ProcessExec(execution))
      val root = driver.workAreaRoot.getOrElse(fail("missing Process Execution WorkArea"))

      Then("materialization precedes driver invocation and terminal resource cleanup removes the WorkArea")
      completed.toOption.map(_.termination) shouldBe Some(ProcessExecutionTermination.Exited(0))
      driver.materializedContent shouldBe Some(content)
      Files.exists(root) shouldBe false
    }

    "collect only declared bounded artifacts and remove the execution root" in {
      Given("an execution-scoped WorkArea and one declared output")
      val workspace = ProcessExecutionWorkArea.allocateC(WorkAreaSpace.create(RuntimeConfig.default)).toOption.get
      val path = WorkAreaRelativePath.parseC("output/result.txt").toOption.get
      val name = ProcessArtifactName.parseC("result").toOption.get
      val declaration = ProcessExecutionOutputDeclaration.createC(name, path, ProcessArtifactKind.File, 16L).toOption.get
        val execution = _execution(Vector(declaration), artifactBytes = 16L, workAreaBytes = 1024L)
      val root = workspace.root

      try {
        When("the declared file and an undeclared file are written beneath the WorkArea")
        Files.createDirectories(root.resolve("output"))
        Files.write(root.resolve("output/result.txt"), "ok".getBytes(StandardCharsets.UTF_8))
        Files.write(root.resolve("output/secret.txt"), "secret".getBytes(StandardCharsets.UTF_8))

        Then("only the declared logical artifact is collected")
        val collected = workspace.collectArtifactsC(execution).toOption.get
        collected.limitExceeded shouldBe false
        collected.artifacts.map(_.name.print) shouldBe Vector("result")
        collected.artifacts.map(_.byteCount) shouldBe Vector(2L)
      } finally {
        workspace.close()
      }

      And("cleanup removes the execution-scoped host root")
      Files.exists(root) shouldBe false
    }

    "reject symbolic-link traversal and artifact quota overflow" in {
      Given("a WorkArea containing a symbolic link to a foreign directory")
      val workspace = ProcessExecutionWorkArea.allocateC(WorkAreaSpace.create(RuntimeConfig.default)).toOption.get
      val foreign = Files.createTempDirectory("process-exec-foreign")
      val escaped = WorkAreaRelativePath.parseC("escape/output").toOption.get
      val output = WorkAreaRelativePath.parseC("output/result.bin").toOption.get
      val name = ProcessArtifactName.parseC("result").toOption.get
      val declaration = ProcessExecutionOutputDeclaration.createC(name, output, ProcessArtifactKind.File, 2L).toOption.get
      val execution = _execution(Vector(declaration), artifactBytes = 2L, workAreaBytes = 1024L)

      try {
        Files.createSymbolicLink(workspace.root.resolve("escape"), foreign)

        When("a working directory attempts to cross the symbolic link")
        val escapedResult = workspace.workingDirectoryC(Some(escaped))

        Then("the WorkArea rejects the host-path escape")
        escapedResult.isFaillure shouldBe true
        Files.exists(foreign.resolve("output")) shouldBe false

        And("an oversized declared artifact is reported as a bounded collection")
        Files.delete(workspace.root.resolve("escape"))
        Files.createDirectories(workspace.root.resolve("output"))
        Files.write(workspace.root.resolve("output/result.bin"), Array[Byte](1, 2, 3))
        val collected = workspace.collectArtifactsC(execution).toOption.get
        collected.limitExceeded shouldBe true
        collected.artifacts shouldBe Vector.empty
      } finally {
        workspace.close()
        Files.deleteIfExists(foreign)
      }
    }

    "enforce the aggregate WorkArea quota even for undeclared process files" in {
      Given("generated undeclared payloads larger than the admitted WorkArea quota")
      val property = Prop.forAll(_quota_excesses) { excess =>
        val workspace = ProcessExecutionWorkArea.allocateC(WorkAreaSpace.create(RuntimeConfig.default)).toOption.get
        val output = WorkAreaRelativePath.parseC("output/result.txt").toOption.get
        val name = ProcessArtifactName.parseC("result").toOption.get
        val declaration = ProcessExecutionOutputDeclaration.createC(name, output, ProcessArtifactKind.File, 64L).toOption.get
        val execution = _execution(Vector(declaration), artifactBytes = 64L, workAreaBytes = 64L)
        try {
          Files.createDirectories(workspace.root.resolve("output"))
          Files.write(workspace.root.resolve("output/result.txt"), "ok".getBytes(StandardCharsets.UTF_8))
          Files.write(workspace.root.resolve("output/undeclared.bin"), Array.fill[Byte](64 + excess)(1))
          workspace.collectArtifactsC(execution).toOption.exists { collected =>
            collected.limitExceeded && collected.artifacts.isEmpty
          }
        } finally {
          workspace.close()
        }
      }

      When("each process leaves a payload beyond its bounded declaration set")
      val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(8), property)

      Then("the runtime suppresses all logical artifacts with a deterministic limit result")
      checked.passed shouldBe true
    }
  }

  private def _execution(
    outputs: Vector[ProcessExecutionOutputDeclaration],
    artifactBytes: Long,
    workAreaBytes: Long,
    inputFiles: Vector[ProcessExecutionInputFile] = Vector.empty,
    resourceTrees: Vector[ProcessExecutionResourceTreeInput] = Vector.empty,
    allowedresourcetrees: Map[ResourceTreeReference, ResourceTreeLimits] = Map.empty
  ): ResolvedProcessExecution = {
    val capability = ProcessCapabilityId.parseC("workarea-test").toOption.get
    val limits = ProcessExecutionLimits(
      Some(100L), Some(100L), Some(100L), Some(100L), Some(100L), Some(100L),
      Some(10L), Some(1024L), Some(10L), Some(artifactBytes), Some(workAreaBytes)
    )
    val definition = ProcessProgramDefinition.fromRuntimeC(
      capability,
      "workarea-test",
      "runtime-owned-test",
      Vector.empty,
      ProcessArgumentPolicy(Vector.empty, Set.empty),
      limits,
      outputs.map(_.name).toSet,
      allowedinputfiles = inputFiles.map(_.name).toSet,
      allowedresourcetrees = allowedresourcetrees
    ).toOption.get
    ResolvedProcessExecution(
      ProcessExecutionRequest(capability, inputFiles = inputFiles, outputs = outputs, resourceTrees = resourceTrees),
      definition,
      limits
    )
  }

  private def _context(driver: ProcessExecutionDriver): ExecutionContext = {
    GlobalContext.set(GlobalContext(WorkAreaSpace.create(RuntimeConfig.default)))
    val base = ExecutionContext.create()
    val scope = ScopeContext(
      kind = ScopeKind.Runtime,
      name = "process-workarea-tree-test",
      parent = None,
      observabilityContext = base.observability,
      processExecutionDriverOption = Some(driver)
    )
    base.withScope(scope)
  }

  private def _result(execution: ResolvedProcessExecution): ProcessExecutionResult = {
    val capture = ProcessExecutionCapture(Vector.empty, 0L, truncated = false)
    ProcessExecutionResult(
      ProcessExecutionTermination.Exited(0),
      capture,
      capture,
      Vector.empty,
      elapsedMillis = 1L,
      safeProgramIdentity = execution.definition.safeProgramIdentity
    )
  }
}

private final class _WorkAreaObservingDriver(
  result: ProcessExecutionResult,
  path: WorkAreaRelativePath
) extends ProcessExecutionDriver {
  var materializedContent: Option[String] = None
  var workAreaRoot: Option[Path] = None

  val safeIdentity: String = "workarea-observing-test-driver"

  def startC(execution: ResolvedProcessExecution): Consequence[ProcessExecutionHandle] = {
    val _ = execution
    Consequence.operationIllegal("process_exec", "Process Execution WorkArea is required")
  }

  override def startC(
    execution: ResolvedProcessExecution,
    workarea: ProcessExecutionWorkArea
  ): Consequence[ProcessExecutionHandle] = {
    val _ = execution
    workAreaRoot = Some(workarea.root)
    workarea.inputPathC(path).map { materialized =>
      materializedContent = Some(new String(Files.readAllBytes(materialized), StandardCharsets.UTF_8))
      new ProcessExecutionHandle {
        def awaitC: Consequence[ProcessExecutionResult] = Consequence.success(result)
        def cancelC: Consequence[Unit] = Consequence.unit
      }
    }
  }
}
