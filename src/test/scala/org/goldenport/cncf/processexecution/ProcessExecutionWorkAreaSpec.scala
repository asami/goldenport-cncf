package org.goldenport.cncf.processexecution

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import org.goldenport.cncf.config.RuntimeConfig
import org.goldenport.cncf.workarea.WorkAreaSpace
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Jul. 17, 2026
 * @version Jul. 17, 2026
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
    inputFiles: Vector[ProcessExecutionInputFile] = Vector.empty
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
      allowedinputfiles = inputFiles.map(_.name).toSet
    ).toOption.get
    ResolvedProcessExecution(ProcessExecutionRequest(capability, inputFiles = inputFiles, outputs = outputs), definition, limits)
  }
}
