package org.goldenport.cncf.processexecution

import java.nio.file.{Files, Path, Paths}
import java.util.Comparator
import scala.jdk.CollectionConverters.*
import scala.util.Using
import org.goldenport.Consequence
import org.goldenport.cncf.effect.{ExternalEffect, ExternalEffectKind, ExternalEffectRoute}
import org.goldenport.cncf.workspace.*
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Nov.  5, 2026
 * @version Nov.  5, 2026
 */
final class ValidationCheckExecutionSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _workspace = WorkspaceId.parseC("checks").toOption.get
  private val _check = ValidationCheckId.parseC("content check").toOption.get
  private val _target = ValidationCheckTarget(_workspace, _check)
  private val _capability = ProcessCapabilityId.parseC("content-check").toOption.get
  private val _limits = ProcessExecutionLimits(Some(5000), Some(5000), Some(100), Some(65536), Some(65536),
    Some(65536), Some(16), Some(65536), Some(16), Some(65536), Some(65536))
  private val _complete = ProcessExecutionCapture(Vector(1.toByte, 2.toByte), 2L, false)
  private val _empty = ProcessExecutionCapture(Vector.empty, 0L, false)

  "Declared validation check model" should {
    "retain exact logical identities without imposing a length or count ceiling" in {
      Given("exact case-sensitive check names, a long name and invalid edge whitespace or controls")
      val names = Vector("Compile", "compile", "content check", "x" * 10000)
      val invalid = Vector(null, "", " name", "name ", "\tname", "name\n", "a\u0000b", "a\u001fb", "\u2003name")
      When("the public logical identity parser is used")
      val values = names.map(ValidationCheckId.parseC(_).toOption.get.value)
      Then("accepted values are unchanged and invalid names never acquire a typed identity")
      values shouldBe names
      invalid.foreach(value => ValidationCheckId.parseC(value).isFaillure shouldBe true)
      values.distinct.size shouldBe names.size
    }

    "derive outcome from actual termination and complete captures while retaining every receipt field" in {
      Given("actual receipts for every process termination, including negative and nonzero exit codes")
      val cases = Vector(
        ProcessExecutionTermination.NotStarted -> ValidationCheckExecutionOutcome.NotExecuted,
        ProcessExecutionTermination.Exited(0) -> ValidationCheckExecutionOutcome.Succeeded,
        ProcessExecutionTermination.Exited(7) -> ValidationCheckExecutionOutcome.Failed(7),
        ProcessExecutionTermination.Exited(-1) -> ValidationCheckExecutionOutcome.Failed(-1)) ++
        Vector(ProcessExecutionTermination.LaunchFailed, ProcessExecutionTermination.TimedOut,
          ProcessExecutionTermination.Cancelled, ProcessExecutionTermination.TerminationUnconfirmed,
          ProcessExecutionTermination.OutputLimitExceeded(ProcessExecutionStream.Stdout),
          ProcessExecutionTermination.OutputLimitExceeded(ProcessExecutionStream.Stderr),
          ProcessExecutionTermination.ArtifactLimitExceeded).map(t => t -> ValidationCheckExecutionOutcome.Incomplete(t))
      When("the typed result derives each outcome")
      val results = cases.map { case (termination, expected) =>
        val receipt = _receipt(termination)
        val result = ValidationCheckExecutionResult(_target, ValidationCheckOperation.Run, receipt, ValidationCheckPurpose.Behavior)
        (result, receipt, expected)
      }
      Then("outcomes match while captures, safe identity, artifacts, elapsed time and termination remain the original receipt")
      results.foreach { case (result, receipt, expected) =>
        result.processResult should be theSameInstanceAs receipt
        result.outcome shouldBe expected
        result.effect shouldBe ExternalEffect(ExternalEffectKind.Build, _workspace)
        result.purpose shouldBe ValidationCheckPurpose.Behavior
        result.processResult.stdout shouldBe _complete
        result.processResult.stderr shouldBe _empty
        result.processResult.safeProgramIdentity shouldBe "actual-check-program"
        result.processResult.elapsedMillis shouldBe 17L
        result.processResult.artifacts shouldBe _receipt(result.processResult.termination).artifacts
      }
    }

    "require both exact complete captures for exited zero and keep nonzero exits failed" in {
      Given("truncated, null and contradictory captures with an actual zero exit")
      val incomplete = Vector(null, _complete.copy(content = null), _complete.copy(truncated = true),
        _complete.copy(byteCount = 1L), _complete.copy(byteCount = 3L), _complete.copy(byteCount = -1L))
      When("each capture occupies either stream")
      val receipts = incomplete.flatMap(capture => Vector(
        _receipt(ProcessExecutionTermination.Exited(0)).copy(stdout = capture),
        _receipt(ProcessExecutionTermination.Exited(0)).copy(stderr = capture)))
      Then("zero exit is incomplete output and never an invented success")
      receipts.foreach { receipt =>
        ValidationCheckExecutionResult(_target, ValidationCheckOperation.Run, receipt, ValidationCheckPurpose.Behavior).outcome shouldBe
          ValidationCheckExecutionOutcome.IncompleteOutput
        ValidationCheckExecutionResult(_target, ValidationCheckOperation.Run,
          receipt.copy(termination = ProcessExecutionTermination.Exited(2)), ValidationCheckPurpose.Behavior).outcome shouldBe ValidationCheckExecutionOutcome.Failed(2)
      }
    }

    "carry explicit tool-independent purpose without making it an outcome or source coverage claim" in {
      Given("all three declared purposes and an incomplete observed process receipt")
      val purposes = Vector(ValidationCheckPurpose.Compilation, ValidationCheckPurpose.Behavior, ValidationCheckPurpose.Lint)
      When("typed results retain each explicit purpose with that receipt")
      val results = purposes.map(purpose => ValidationCheckExecutionResult(_target, ValidationCheckOperation.Run,
        _receipt(ProcessExecutionTermination.TimedOut), purpose))
      Then("the exact wires remain distinct while each outcome still depends on the process result")
      purposes.map(_.wireName) shouldBe Vector("compilation", "behavior", "lint")
      results.map(_.purpose) shouldBe purposes
      results.foreach(_.outcome shouldBe ValidationCheckExecutionOutcome.Incomplete(ProcessExecutionTermination.TimedOut))
    }
  }

  "Fixed validation check admission" should {
    "bind only the same admitted workspace object and exact declared check identity" in _with_root { root =>
      Given("one fixed grep definition and another binding for the same workspace ID and directory")
      val binding = WorkspaceBinding.createC(_workspace, root).toOption.get
      val alternate = WorkspaceBinding.createC(_workspace, root).toOption.get
      val definition = _definition(binding)
      val policy = ProcessExecutionPolicy.createC(Vector(definition)).toOption.get
      When("lookup uses the original binding, an alternate binding or an undeclared identity")
      val actual = policy.validationCheckDefinitionC(binding, _check)
      Then("only the original object and exact check resolve the runtime definition")
      actual.toOption.get should be theSameInstanceAs definition
      policy.validationCheckDefinitionC(alternate, _check).isFaillure shouldBe true
      policy.validationCheckDefinitionC(binding, ValidationCheckId.parseC("Content check").toOption.get).isFaillure shouldBe true
      ProcessExecutionPolicy.createC(Vector(definition, _definition(binding,
        capability = ProcessCapabilityId.parseC("another-capability").toOption.get))).isFaillure shouldBe true
      val execution = policy.resolveValidationCheckC(definition, ProcessExecutionGrant(_capability)).toOption.get
      execution.request shouldBe ProcessExecutionRequest(_capability)
      execution.effectiveArguments shouldBe Vector("-Fx", "expected", "value.txt")
      execution.effectiveLimits shouldBe _limits
      execution.definition._validation_check_metadata.get.purpose shouldBe ValidationCheckPurpose.Behavior
      ValidationCheckExecutionAdmission.decode(_target, ValidationCheckOperation.Run, execution,
        _receipt(ProcessExecutionTermination.Exited(0))).purpose shouldBe ValidationCheckPurpose.Behavior
    }

    "refuse generic admission and every request override before local driver launch" in _with_root { root =>
      Given("a tagged fixed definition and a resolved typed request")
      val definition = _definition(WorkspaceBinding.createC(_workspace, root).toOption.get)
      val policy = ProcessExecutionPolicy.createC(Vector(definition)).toOption.get
      val grant = ProcessExecutionGrant(_capability)
      val execution = policy.resolveValidationCheckC(definition, grant).toOption.get
      val path = WorkAreaRelativePath.parseC("elsewhere").toOption.get
      val name = ProcessArtifactName.parseC("extra").toOption.get
      val input = ProcessExecutionInputFile.createC(name, path, Vector(1.toByte), 1L).toOption.get
      val output = ProcessExecutionOutputDeclaration.createC(name, path, ProcessArtifactKind.File, 1L).toOption.get
      val extras = Vector(execution.request.copy(arguments = Vector("--help")),
        execution.request.copy(arguments = Vector("ENV=value")),
        execution.request.copy(workingDirectory = Some(path)),
        execution.request.copy(input = ProcessExecutionInput.Bytes(Vector(1.toByte))),
        execution.request.copy(input = ProcessExecutionInput.WorkAreaFile(path)),
        execution.request.copy(inputFiles = Vector(input)), execution.request.copy(outputs = Vector(output)),
        execution.request.copy(resourceTrees = Vector(null)),
        execution.request.copy(requestedLimits = ProcessExecutionLimits(stdoutBytes = Some(1L))))
      val driver = new LocalProcessExecutionDriver()
      try {
        When("generic routes or a tampered resolved request are presented")
        val denied = extras.map(request => driver.startC(execution.copy(request = request)))
        Then("no generic route admits the tag and no override starts a process")
        policy.resolveC(execution.request, grant).isFaillure shouldBe true
        ProcessExecutionAdmission.createC(policy, Vector(grant)).toOption.get.admitC(execution.request).isFaillure shouldBe true
        ValidationCheckExecutionAdmission.validateGenericExecutionC(execution).isFaillure shouldBe true
        ProcessExternalEffectPolicy.classifyDefinition(definition).route shouldBe ExternalEffectRoute.Managed
        ProcessExternalEffectPolicy.classifyDefinition(definition).effectKinds shouldBe Set(ExternalEffectKind.Build)
        ProcessExternalEffectPolicy.validateGenericDefinitionC(definition).isFaillure shouldBe true
        extras.foreach(request => definition.validateRequestC(request, _limits).isFaillure shouldBe true)
        denied.foreach(_.isFaillure shouldBe true)
        driver.activeProcessCount shouldBe 0
        driver.activeHandleCount shouldBe 0
      } finally driver.close()
    }

    "reject missing adapters, unavailable executables, launchers and invalid fixed configuration" in _with_root { root =>
      Given("one admitted directory and ordinary fixed grep configuration")
      val binding = WorkspaceBinding.createC(_workspace, root).toOption.get
      def _create_(adapter: String = "fixed-process", executable: Path = Paths.get("/usr/bin/grep"),
        arguments: Vector[String] = Vector.empty, limits: ProcessExecutionLimits = _limits,
        environment: Map[String, String] = Map.empty,
        purpose: ValidationCheckPurpose = ValidationCheckPurpose.Behavior) = ValidationCheckProgramDefinition.createC(
        binding, _check, purpose, adapter, _capability, "content-check", executable, arguments, limits, environment)
      val link = root.resolve("env")
      Files.createSymbolicLink(link, Paths.get("/usr/bin/grep"))
      When("the host tries unsupported adapters, missing programs or command launchers")
      val failures = Vector(_create_(purpose = null), _create_(adapter = "unknown"), _create_(adapter = null),
        _create_(executable = root.resolve("missing")), _create_(executable = Paths.get("grep")),
        _create_(executable = root), _create_(executable = Paths.get("/bin/sh")), _create_(executable = link),
        _create_(arguments = Vector("bad\u0000argument")), _create_(limits = ProcessExecutionLimits.empty),
        _create_(environment = Map("invalid-name" -> "value")))
      Then("all fail without substituting another adapter, executable or result")
      failures.foreach(_.isFaillure shouldBe true)
      _create_().isSuccess shouldBe true
      _create_(executable = Paths.get(System.getProperty("java.home"), "bin", "java"),
        arguments = Vector("-version")).isSuccess shouldBe true
    }
  }

  private def _definition(binding: WorkspaceBinding, capability: ProcessCapabilityId = _capability): ProcessProgramDefinition =
    ValidationCheckProgramDefinition.createC(binding, _check, ValidationCheckPurpose.Behavior, "fixed-process", capability, capability.print,
      Paths.get("/usr/bin/grep"), Vector("-Fx", "expected", "value.txt"), _limits, Map.empty).toOption.get

  private def _receipt(termination: ProcessExecutionTermination): ProcessExecutionResult =
    ProcessExecutionResult(termination, _complete, _empty,
      Vector(ProcessExecutionArtifact(ProcessArtifactName.parseC("observed").toOption.get, ProcessArtifactKind.File, 3L)),
      17L, "actual-check-program")

  private def _with_root(body: Path => Unit): Unit = {
    val root = Files.createTempDirectory("validation-check-model-")
    try body(root) finally Using.resource(Files.walk(root)) { stream =>
      stream.sorted(Comparator.reverseOrder[Path]()).iterator().asScala.foreach(path => Files.deleteIfExists(path))
    }
  }
}
