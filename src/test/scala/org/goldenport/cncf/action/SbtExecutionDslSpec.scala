package org.goldenport.cncf.action

import java.nio.file.{Files, Path, Paths}
import java.util.Comparator
import scala.jdk.CollectionConverters.*
import scala.util.Using
import cats.syntax.all.*
import org.goldenport.{Conclusion, Consequence}
import org.goldenport.configuration.{Configuration, ConfigurationTrace, ConfigurationValue, ResolvedConfiguration}
import org.goldenport.observation.Descriptor
import org.goldenport.cncf.component.{Component, ComponentId, ComponentInstanceId, ComponentOrigin}
import org.goldenport.cncf.context.{ExecutionContext, GlobalContext, ScopeContext, ScopeKind}
import org.goldenport.cncf.cli.InstalledRuntimeDistributionSpec
import org.goldenport.cncf.config.{ResolvedParameters, RuntimeConfig}
import org.goldenport.cncf.effect.ExternalEffectKind
import org.goldenport.cncf.processexecution.*
import org.goldenport.cncf.unitofwork.{UnitOfWork, UnitOfWorkEffectClass, UnitOfWorkInterpreter, UnitOfWorkOp, UnitOfWorkProgramRecorder, UnitOfWorkProgramStubProvider}
import org.goldenport.cncf.workarea.{FileWorkArea, WorkArea, WorkAreaGroup, WorkAreaId, WorkAreaSpace}
import org.goldenport.cncf.workspace.*
import org.goldenport.protocol.Protocol
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Oct. 15, 2026
 * @version Oct. 24, 2026
 * @author  ASAMI, Tomoharu
 */
final class SbtExecutionDslSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import SbtExecutionDslSpec.Fixture

  private val _owner = ComponentId("org.goldenport.spec.BuildOwner")
  private val _workspace_id = WorkspaceId.parseC("project").toOption.get
  private val _compile_capability = ProcessCapabilityId.parseC("project-compile").toOption.get
  private val _test_capability = ProcessCapabilityId.parseC("project-test").toOption.get
  private val _test_only_capability = ProcessCapabilityId.parseC("project-test-only").toOption.get
  private val _fix_check_capability = ProcessCapabilityId.parseC("project-fix-check").toOption.get
  private val _limits = ProcessExecutionLimits(
    Some(5000L), Some(5000L), Some(100L), Some(65536L), Some(65536L), Some(65536L),
    Some(16L), Some(65536L), Some(16L), Some(65536L), Some(65536L)
  )

  "Typed SBT internal DSL" should {
    "record thin attempt intents as External without retaining construction authority" in {
      Given("an unconfigured construction context and explicit admission-rejection stub data")
      val fixture = _fixture(_result(ProcessExecutionTermination.Exited(0)),
        workspaceinstalled = false, processinstalled = false, driverinstalled = false)
      val behavior = new SbtDslBehavior(Behavior.Core(fixture.context, None, None))
      val conclusion = Consequence.serviceUnavailable("Workspace Execution admission is not configured").conclusion
      val provider = new UnitOfWorkProgramStubProvider {
        def resultFor[A](ordinal: Int, operation: UnitOfWorkOp[A]): Consequence[A] = operation match {
          case UnitOfWorkOp.SbtExecAttempt(target, kind) =>
            Consequence.success(SbtExecutionAttempt.AdmissionRejected(target, kind, conclusion))
          case _ => Consequence.notImplemented("unexpected operation in SBT attempt recording")
        }
      }
      try {
        When("Compile, Test, TestOnly and FixCheck attempt programs are recorded")
        val recordings = Vector(behavior.compileAttempt(fixture.target), behavior.testAttempt(fixture.target),
          behavior.testOnlyAttempt(fixture.target), behavior.fixCheckAttempt(fixture.target))
          .map(UnitOfWorkProgramRecorder.record(_, provider))
        Then("each program contains exactly its one typed External intent with no admission or driver work")
        recordings.map(_.occurrences.map(_.operation)) shouldBe Vector(
          Vector(UnitOfWorkOp.SbtExecAttempt(fixture.target, SbtOperation.Compile)),
          Vector(UnitOfWorkOp.SbtExecAttempt(fixture.target, SbtOperation.Test)),
          Vector(UnitOfWorkOp.SbtExecAttempt(fixture.target, SbtOperation.TestOnly)),
          Vector(UnitOfWorkOp.SbtExecAttempt(fixture.target, SbtOperation.FixCheck)))
        recordings.foreach(_.occurrences.map(_.effectClass) shouldBe Vector(UnitOfWorkEffectClass.External))
        recordings.foreach(_.result.isSuccess shouldBe true)
        fixture.profile.driver.executions shouldBe empty
      } finally _close(fixture.root)
    }

    "return actual missing-workspace and missing-build-grant admission diagnostics without a process receipt" in {
      Given("current UnitOfWork contexts missing the workspace service or the independent build grant")
      val fixtures = Vector(_fixture(_result(ProcessExecutionTermination.Exited(0)), workspaceinstalled = false),
        _fixture(_result(ProcessExecutionTermination.Exited(0)), capabilitygranted = false))
      val cases = fixtures.flatMap(fixture => Vector(SbtOperation.Test, SbtOperation.FixCheck).map(fixture -> _))
      try {
        val expected = cases.map { case (fixture, operation) => _failure_conclusion(SbtExecutionAdmission.resolveC(
          fixture.context, fixture.target, operation)) }
        When("the actual UnitOfWork interpreter evaluates each Test and FixCheck attempt")
        val results = cases.map { case (fixture, operation) => new UnitOfWorkInterpreter(new UnitOfWork(fixture.context)).interpret(
          UnitOfWorkOp.SbtExecAttempt(fixture.target, operation)) }
        Then("successful AdmissionRejected values retain the original structured diagnostic and start no driver")
        results.zip(cases).zip(expected).foreach { case ((result, (fixture, kind)), original) =>
          result.toOption.get match {
            case SbtExecutionAttempt.AdmissionRejected(target, operation, conclusion) =>
              target shouldBe fixture.target
              operation shouldBe kind
              conclusion.status shouldBe original.status
              conclusion.observation.taxonomy shouldBe original.observation.taxonomy
              conclusion.observation.cause.kind shouldBe original.observation.cause.kind
              conclusion.observation.cause.descriptor.facets.filterNot(_.isInstanceOf[Descriptor.Facet.SrcPos]) shouldBe
                original.observation.cause.descriptor.facets.filterNot(_.isInstanceOf[Descriptor.Facet.SrcPos])
            case other => fail(s"Expected admission rejection, received $other")
          }
          fixture.profile.driver.executions shouldBe empty
        }
      } finally fixtures.foreach(fixture => _close(fixture.root))
    }

    "observe admitted Compile, Test, TestOnly and FixCheck through the existing managed result decoder" in {
      Given("an authorized current context and complete test-owned selected-suite output")
      val fixture = _fixture(_selection_process(Vector("org.example.SelectedSpec")))
      val behavior = new SbtDslBehavior(Behavior.Core(fixture.context, None, None))
      try {
        When("each thin attempt DSL is interpreted through the owning UnitOfWork")
        val results = Vector(behavior.compileAttempt(fixture.target), behavior.testAttempt(fixture.target),
          behavior.testOnlyAttempt(fixture.target), behavior.fixCheckAttempt(fixture.target)).map(program =>
          new UnitOfWorkInterpreter(new UnitOfWork(fixture.context)).run(program))
        Then("actual process facts remain Observed and TestOnly retains its existing report qualification")
        results.map(_.toOption) shouldBe Vector(
          Some(SbtExecutionAttempt.Observed(SbtExecutionResult(fixture.target, SbtOperation.Compile, fixture.result))),
          Some(SbtExecutionAttempt.Observed(SbtExecutionResult(fixture.target, SbtOperation.Test, fixture.result))),
          Some(SbtExecutionAttempt.Observed(SbtExecutionResult(fixture.target, SbtOperation.TestOnly, fixture.result,
            Some(SbtTestSelectionResult.Verified(Vector("org.example.SelectedSpec"), 1, 7L))))),
          Some(SbtExecutionAttempt.Observed(SbtExecutionResult(fixture.target, SbtOperation.FixCheck, fixture.result))))
        fixture.profile.driver.executions should have size 4
      } finally _close(fixture.root)
    }

    "retain start and post-start driver failures as Uncertain without reclassifying admission or replaying" in {
      Given("authorized intents and actual test-owned drivers failing at start or while awaiting a handle")
      val original = Consequence.serviceUnavailable("test-owned driver observation failed").conclusion
      var starts = 0
      val drivers = Vector(false, true).map { admittedhandle => new ProcessExecutionDriver {
        val safeIdentity = "test-owned-failing-sbt"
        def startC(execution: ResolvedProcessExecution): Consequence[ProcessExecutionHandle] = {
          starts += 1
          if (!admittedhandle) Consequence.Failure(original)
          else Consequence.success(new ProcessExecutionHandle {
            def awaitC: Consequence[ProcessExecutionResult] = Consequence.Failure(original)
            def cancelC: Consequence[Unit] = Consequence.unit
          })
        }
      } }
      val fixtures = drivers.map(driver => _fixture(_result(ProcessExecutionTermination.Exited(0)), driver = Some(driver)))
      val uows = fixtures.map(fixture => new UnitOfWork(fixture.context))
      try {
        When("actual admitted Test and FixCheck attempts reach each failing driver exactly once")
        val results = fixtures.zip(uows).flatMap { case (fixture, uow) =>
          Vector(SbtOperation.Test, SbtOperation.FixCheck).map(operation =>
            (fixture, operation, new UnitOfWorkInterpreter(uow).interpret(UnitOfWorkOp.SbtExecAttempt(fixture.target, operation))))
        }
        Then("each result carries the identical execution failure as Uncertain with no synthetic process result")
        results.foreach { case (fixture, operation, result) =>
          result.toOption shouldBe Some(SbtExecutionAttempt.Uncertain(fixture.target, operation, original))
        }
        starts shouldBe 4
        fixtures.foreach(_.profile.driver.executions shouldBe empty)
      } finally {
        uows.foreach(_.dispose())
        fixtures.foreach(fixture => _close(fixture.root))
      }
    }

    "construct and record one TestOnly intent without runtime services" in {
      Given("a Behavior without workspace, process admission or driver and a deliberate typed selection result")
      val fixture = _fixture(_result(ProcessExecutionTermination.Exited(0)),
        workspaceinstalled = false, processinstalled = false, driverinstalled = false)
      val supplied = SbtExecutionResult(fixture.target, SbtOperation.TestOnly, fixture.result,
        Some(SbtTestSelectionResult.Verified(Vector("org.example.SelectedSpec"), 1, 7L)))
      val behavior = new SbtDslBehavior(Behavior.Core(fixture.context, None, None))
      val provider = new UnitOfWorkProgramStubProvider {
        def resultFor[A](ordinal: Int, operation: UnitOfWorkOp[A]): Consequence[A] = operation match {
          case UnitOfWorkOp.SbtExec(_, SbtOperation.TestOnly) => Consequence.success(supplied)
          case _ => Consequence.notImplemented("unexpected TestOnly recording operation")
        }
      }
      try {
        When("the protected TestOnly program is constructed and recorded")
        val recording = UnitOfWorkProgramRecorder.record(behavior.testOnly(fixture.target), provider)
        Then("exactly one External SbtExec and the supplied value remain without a ProcessExec or driver call")
        recording.result.toOption shouldBe Some(supplied)
        recording.occurrences.map(_.operation) shouldBe Vector(UnitOfWorkOp.SbtExec(fixture.target, SbtOperation.TestOnly))
        recording.occurrences.map(_.ordinal) shouldBe Vector(0)
        recording.occurrences.map(_.effectClass) shouldBe Vector(UnitOfWorkEffectClass.External)
        fixture.profile.driver.executions shouldBe empty
      } finally _close(fixture.root)
    }

    "resolve TestOnly classes and cwd from actual context B and decode its single execution" in {
      Given("construction context A, a distinct B with its own selected classes, and denied context C")
      val contexta = _fixture(_result(ProcessExecutionTermination.Exited(0)))
      val classes = Vector("org.actual.CurrentSpec", "org.actual.OtherSpec")
      val contextb = _fixture(_selection_process(classes), testclasses = classes)
      val contextc = _fixture(_selection_process(classes), capabilitygranted = false, testclasses = classes)
      val behavior = new SbtDslBehavior(Behavior.Core(contexta.context, None, None))
      val program = behavior.testOnly(contexta.target)
      try {
        When("the same logical intent is interpreted under B and denied C")
        val result = new UnitOfWorkInterpreter(new UnitOfWork(contextb.context)).run(program)
        val denied = new UnitOfWorkInterpreter(new UnitOfWork(contextc.context)).run(program)
        Then("B alone runs once with its current trusted selection and shared cwd and retains raw process facts")
        val expected = SbtExecutionResult(contextb.target, SbtOperation.TestOnly, contextb.result,
          Some(SbtTestSelectionResult.Verified(classes, 2, 7L)))
        result.toOption shouldBe Some(expected)
        result.toOption.map(_.outcome) shouldBe Some(SbtExecutionOutcome.Succeeded)
        contextb.profile.driver.executions.map(_.effectiveArguments) shouldBe Vector(Vector(
          "-J-Xmx4294967296", "--batch", "-Dsbt.server.autostart=false", "-Dsbt.supershell=false", "-Dsbt.color=false",
          "testOnly org.actual.CurrentSpec org.actual.OtherSpec"))
        _expect_heap_definitions(contextb.profile.driver.executions, Vector(contextb.definitions.last))
        contextb.workspace.bindingC(_workspace_id).toOption.get.workingDirectoryC.toOption shouldBe Some(contextb.root.resolve("checkout").toRealPath())
        (contextb.workspace.bindingC(_workspace_id).toOption.get eq contexta.workspace.bindingC(_workspace_id).toOption.get) shouldBe false
        contextb.profile.driver.executions.map(_.request.workingDirectory) shouldBe Vector(None)
        contextb.profile.driver.executions.map(_.request.arguments) shouldBe Vector(Vector.empty)
        denied.isFaillure shouldBe true
        contexta.profile.driver.executions shouldBe empty
        contextc.profile.driver.executions shouldBe empty
      } finally {
        _close(contexta.root)
        _close(contextb.root)
        _close(contextc.root)
      }
    }

    "deny TestOnly missing services, authority, targets and replaced bindings before execution" in {
      Given("TestOnly fixtures missing each required service or independent authority and an unknown target")
      val process = _selection_process(Vector("org.example.SelectedSpec"))
      val fixtures = Vector(
        _fixture(process, workspaceinstalled = false), _fixture(process, processinstalled = false),
        _fixture(process, driverinstalled = false), _fixture(process, owner = ComponentId("org.example.Other")),
        _fixture(process, workspacegranted = false), _fixture(process, capabilitygranted = false),
        _fixture(process, permission = WorkspacePermission.ReadOnly), _fixture(process), _fixture(process))
      try {
        val replacement = WorkspaceExecutionAdmission.createC(Vector(_workspace_id -> Files.createDirectory(fixtures.last.root.resolve("other"))),
          Vector(WorkspaceGrant(_owner, _workspace_id, WorkspacePermission.Manage))).toOption.get
        val replaced = _context(Some(replacement), Some(fixtures.last.admission), fixtures.last.profile, _owner)
        When("each TestOnly intent is interpreted using its current context")
        val results = fixtures.zipWithIndex.map { case (fixture, index) =>
          val target = if (index == fixtures.size - 2) SbtTarget(WorkspaceId.parseC("unknown").toOption.get) else fixture.target
          val context = if (index == fixtures.size - 1) replaced else fixture.context
          new UnitOfWorkInterpreter(new UnitOfWork(context)).interpret(UnitOfWorkOp.SbtExec(target, SbtOperation.TestOnly))
        }
        Then("all routes fail before any deterministic driver receives an execution")
        results.foreach(_.isFaillure shouldBe true)
        fixtures.foreach(_.profile.driver.executions shouldBe empty)
      } finally fixtures.foreach(x => _close(x.root))
    }

    "reject an internally admitted tagged TestOnly execution through generic ProcessExec" in {
      Given("an internally admitted TestOnly execution and its retained runtime-owned request")
      val fixture = _fixture(_selection_process(Vector("org.example.SelectedSpec")))
      val execution = SbtExecutionAdmission.resolveC(fixture.context, fixture.target, SbtOperation.TestOnly).toOption.get
      try {
        When("generic interpretation attempts to bypass the logical intent")
        val generic = new UnitOfWorkInterpreter(new UnitOfWork(fixture.context)).interpret(UnitOfWorkOp.ProcessExec(execution))
        val overridden = SbtExecutionAdmission.validateRequestC(execution.request.copy(arguments = Vector("testOnly evil.Other")))
        Then("the generic bypass and caller override are denied and the driver remains idle")
        generic.isFaillure shouldBe true
        overridden.isFaillure shouldBe true
        fixture.profile.driver.executions shouldBe empty
      } finally _close(fixture.root)
    }

    "retain TestOnly terminal facts and refuse success from unsupported zero-exit reports" in {
      Given("all terminal categories, with a positive report present even for nonzero, unexecuted and incomplete outcomes")
      val terminals = Vector(ProcessExecutionTermination.Exited(0), ProcessExecutionTermination.Exited(5),
        ProcessExecutionTermination.NotStarted,
        ProcessExecutionTermination.LaunchFailed, ProcessExecutionTermination.TimedOut, ProcessExecutionTermination.Cancelled,
        ProcessExecutionTermination.OutputLimitExceeded(ProcessExecutionStream.Stdout),
        ProcessExecutionTermination.OutputLimitExceeded(ProcessExecutionStream.Stderr), ProcessExecutionTermination.ArtifactLimitExceeded)
      val fixtures = terminals.map(x => _fixture(_selection_process(Vector("org.example.SelectedSpec")).copy(termination = x)))
      val unsupported = _fixture(_result(ProcessExecutionTermination.Exited(0)))
      try {
        When("the actual interpreter decodes each once and receives an unsupported zero-exit capture")
        val results = fixtures.map(x => new UnitOfWorkInterpreter(new UnitOfWork(x.context)).interpret(
          UnitOfWorkOp.SbtExec(x.target, SbtOperation.TestOnly)).toOption.get)
        val unverified = new UnitOfWorkInterpreter(new UnitOfWork(unsupported.context)).interpret(
          UnitOfWorkOp.SbtExec(unsupported.target, SbtOperation.TestOnly)).toOption.get
        Then("only the supported zero-exit report succeeds and every original process result remains exact")
        results.map(_.outcome) shouldBe terminals.map {
          case ProcessExecutionTermination.Exited(0) => SbtExecutionOutcome.Succeeded
          case ProcessExecutionTermination.Exited(code) => SbtExecutionOutcome.Failed(code)
          case ProcessExecutionTermination.NotStarted => SbtExecutionOutcome.NotExecuted
          case terminal => SbtExecutionOutcome.Incomplete(terminal)
        }
        results.map(_.processResult) shouldBe fixtures.map(_.result)
        results.map(_.target) shouldBe fixtures.map(_.target)
        results.map(_.operation) shouldBe Vector.fill(fixtures.size)(SbtOperation.TestOnly)
        results.tail.foreach(_.testSelection shouldBe None)
        unverified.outcome shouldBe SbtExecutionOutcome.UnverifiedTests(SbtTestSelectionFailure.IncompleteCapture)
        unverified.processResult shouldBe unsupported.result
        (fixtures :+ unsupported).foreach(_.profile.driver.executions should have size 1)
      } finally (fixtures :+ unsupported).foreach(x => _close(x.root))
    }

    "interpret typed Compile and Test SbtExec intents exactly once and return the actual result" in {
      Given("a shared Manage workspace, component Build capabilities and a deterministic driver")
      val fixture = _fixture(_result(ProcessExecutionTermination.Exited(0)))
      val behavior = new SbtDslBehavior(Behavior.Core(fixture.context, None, None))
      try {
        When("the two protected typed SbtExec programs are interpreted")
        val compiled = new UnitOfWorkInterpreter(new UnitOfWork(fixture.context)).run(behavior.compile(fixture.target))
        val tested = new UnitOfWorkInterpreter(new UnitOfWork(fixture.context)).run(behavior.test(fixture.target))

        Then("each typed intent executes once with fixed arguments and its retained result")
        compiled.toOption shouldBe Some(SbtExecutionResult(fixture.target, SbtOperation.Compile, fixture.result))
        tested.toOption shouldBe Some(SbtExecutionResult(fixture.target, SbtOperation.Test, fixture.result))
        fixture.profile.driver.executions.map(_.request.capability) shouldBe Vector(_compile_capability, _test_capability)
        fixture.profile.driver.executions.map(_.effectiveArguments.last) shouldBe Vector("compile", "test")
        fixture.profile.driver.executions.map(_.effectiveArguments) shouldBe Vector("compile", "test").map(task =>
          Vector("-J-Xmx4294967296", "--batch", "-Dsbt.server.autostart=false", "-Dsbt.supershell=false", "-Dsbt.color=false", task))
        _expect_heap_definitions(fixture.profile.driver.executions, fixture.definitions.take(2))
        fixture.profile.driver.executions.map(_.request.arguments) shouldBe Vector(Vector.empty, Vector.empty)
        tested.toOption.map(_.effect.kind) shouldBe Some(ExternalEffectKind.Build)
        tested.toOption.map(_.effect.workspaceId) shouldBe Some(_workspace_id)
      } finally _close(fixture.root)
    }

    "construct and record Compile, Test and FixCheck without workspace or process services" in {
      Given("a Behavior whose runtime has no workspace admission, process admission or process driver")
      val fixture = _fixture(_result(ProcessExecutionTermination.Exited(0)),
        workspaceinstalled = false, processinstalled = false, driverinstalled = false)
      val behavior = new SbtDslBehavior(Behavior.Core(fixture.context, None, None))
      val provider = new UnitOfWorkProgramStubProvider {
        def resultFor[A](ordinal: Int, operation: UnitOfWorkOp[A]): Consequence[A] = operation match {
          case UnitOfWorkOp.SbtExec(target, operation) =>
            Consequence.success(SbtExecutionResult(target, operation, fixture.result))
          case _ => Consequence.notImplemented("unexpected operation in typed SBT construction specification")
        }
      }
      try {
        When("Compile, Test and FixCheck are constructed and recorded using only typed stub results")
        val program = for {
          compiled <- behavior.compile(fixture.target)
          tested <- behavior.test(fixture.target)
          checked <- behavior.fixCheck(fixture.target)
        } yield Vector(compiled, tested, checked)
        val recording = UnitOfWorkProgramRecorder.record(program, provider)

        Then("the recorder sees exactly the three logical typed intents with no real admission or driver invocation")
        recording.result shouldBe Consequence.success(Vector(
          SbtExecutionResult(fixture.target, SbtOperation.Compile, fixture.result),
          SbtExecutionResult(fixture.target, SbtOperation.Test, fixture.result),
          SbtExecutionResult(fixture.target, SbtOperation.FixCheck, fixture.result)
        ))
        recording.occurrences.map(_.operation) shouldBe Vector(
          UnitOfWorkOp.SbtExec(fixture.target, SbtOperation.Compile),
          UnitOfWorkOp.SbtExec(fixture.target, SbtOperation.Test),
          UnitOfWorkOp.SbtExec(fixture.target, SbtOperation.FixCheck)
        )
        recording.occurrences.map(_.ordinal) shouldBe Vector(0, 1, 2)
        recording.occurrences.map(_.effectClass) shouldBe Vector.fill(3)(UnitOfWorkEffectClass.External)
        fixture.profile.driver.executions shouldBe empty
      } finally _close(fixture.root)
    }

    "deny an intent constructed under authorized context A when actual UoW context B lacks its grant" in {
      Given("an authorized construction context A and a distinct actual context B without the Build capability grant")
      val contexta = _fixture(_result(ProcessExecutionTermination.Exited(0)))
      val contextb = _fixture(_result(ProcessExecutionTermination.Exited(0)), capabilitygranted = false)
      val behavior = new SbtDslBehavior(Behavior.Core(contexta.context, None, None))
      val program = behavior.test(contexta.target)
      try {
        When("the program constructed under A is interpreted under B")
        val denied = new UnitOfWorkInterpreter(new UnitOfWork(contextb.context)).run(program)

        Then("B denies admission and neither context starts its driver")
        denied.isFaillure shouldBe true
        contexta.profile.driver.executions shouldBe empty
        contextb.profile.driver.executions shouldBe empty
      } finally {
        _close(contexta.root)
        _close(contextb.root)
      }
    }

    "select actual context B definitions and binding independently of construction context services and roots" in {
      Given("allowed contexts A and B with the same logical ID at different roots and a construction context without services")
      val contexta = _fixture(_result(ProcessExecutionTermination.Exited(3)))
      val contextb = _fixture(_result(ProcessExecutionTermination.Exited(0)))
      val absent = _fixture(_result(ProcessExecutionTermination.Exited(4)),
        workspaceinstalled = false, processinstalled = false, driverinstalled = false)
      val allowedbehavior = new SbtDslBehavior(Behavior.Core(contexta.context, None, None))
      val absentbehavior = new SbtDslBehavior(Behavior.Core(absent.context, None, None))
      val compiled = absentbehavior.compile(absent.target)
      val tested = allowedbehavior.test(contexta.target)
      val checked = absentbehavior.fixCheck(absent.target)
      try {
        When("all three programs are interpreted under actual context B")
        val compileresult = new UnitOfWorkInterpreter(new UnitOfWork(contextb.context)).run(compiled)
        val testresult = new UnitOfWorkInterpreter(new UnitOfWork(contextb.context)).run(tested)
        val checkresult = new UnitOfWorkInterpreter(new UnitOfWork(contextb.context)).run(checked)

        Then("only B executes each typed operation once using B definitions and exact shared binding")
        compileresult.toOption shouldBe Some(SbtExecutionResult(contextb.target, SbtOperation.Compile, contextb.result))
        testresult.toOption shouldBe Some(SbtExecutionResult(contextb.target, SbtOperation.Test, contextb.result))
        checkresult.toOption shouldBe Some(SbtExecutionResult(contextb.target, SbtOperation.FixCheck, contextb.result))
        _expect_heap_definitions(contextb.profile.driver.executions, contextb.definitions.take(3))
        contextb.profile.driver.executions.map(_.request.arguments) shouldBe Vector.fill(3)(Vector.empty)
        contextb.profile.driver.executions.last.effectiveArguments shouldBe Vector(
          "-J-Xmx4294967296", "--batch", "-Dsbt.server.autostart=false", "-Dsbt.supershell=false", "-Dsbt.color=false", "fixCheck")
        contexta.workspace.bindingC(_workspace_id).toOption.get should not be theSameInstanceAs(
          contextb.workspace.bindingC(_workspace_id).toOption.get)
        contexta.profile.driver.executions shouldBe empty
        absent.profile.driver.executions shouldBe empty
      } finally {
        _close(contexta.root)
        _close(contextb.root)
        _close(absent.root)
      }
    }

    "defer malformed typed intent checks to interpretation before any driver invocation" in {
      Given("typed SbtExec values with a missing target or operation and installed valid runtime services")
      val fixture = _fixture(_result(ProcessExecutionTermination.Exited(0)))
      val operations = Vector(
        UnitOfWorkOp.SbtExec(null, SbtOperation.Compile),
        UnitOfWorkOp.SbtExec(fixture.target, null)
      )
      try {
        When("the already constructed malformed intents are interpreted in the current UoW")
        val interpreter = new UnitOfWorkInterpreter(new UnitOfWork(fixture.context))
        val results = operations.map(interpreter.interpret)

        Then("both return structured typed-intent policy failures without allocating a driver execution")
        results.foreach { result =>
          result.isFaillure shouldBe true
          val failures = result match {
            case Consequence.Failure(conclusion) => Vector(conclusion.observation)
            case _ => Vector.empty
          }
          val expected = Consequence.argumentPolicyViolation(
            "sbt", "sbt.execution.intent", "typed target and operation", "invalid").conclusion.observation
          failures.map(_.taxonomy) shouldBe Vector(expected.taxonomy)
          failures.map(_.cause.kind) shouldBe Vector(expected.cause.kind)
          failures.map(_.cause.descriptor.facets.filterNot(_.isInstanceOf[Descriptor.Facet.SrcPos])) shouldBe
            Vector(expected.cause.descriptor.facets.filterNot(_.isInstanceOf[Descriptor.Facet.SrcPos]))
        }
        fixture.profile.driver.executions shouldBe empty
      } finally _close(fixture.root)
    }

    "deny an internally admitted tagged execution in generic ProcessExec while typed SbtExec executes once" in {
      Given("an internally resolved tagged Test execution and its logical typed intent")
      val fixture = _fixture(_result(ProcessExecutionTermination.Exited(0)))
      val execution = SbtExecutionAdmission.resolveC(fixture.context, fixture.target, SbtOperation.Test).toOption.get
      try {
        When("generic ProcessExec interpretation attempts to bypass typed SBT admission")
        val denied = new UnitOfWorkInterpreter(new UnitOfWork(fixture.context)).interpret(UnitOfWorkOp.ProcessExec(execution))

        Then("the generic tagged operation fails structurally before the driver")
        denied.isFaillure shouldBe true
        val failures = denied match {
          case Consequence.Failure(conclusion) => Vector(conclusion.observation)
          case _ => Vector.empty
        }
        val expected = Consequence.argumentPolicyViolation(
          "sbt", "sbt.execution.typed-intent", "typed UnitOfWork SBT intent", "generic ProcessExec").conclusion.observation
        failures.map(_.taxonomy) shouldBe Vector(expected.taxonomy)
        failures.map(_.cause.kind) shouldBe Vector(expected.cause.kind)
        failures.map(_.cause.descriptor.facets.filterNot(_.isInstanceOf[Descriptor.Facet.SrcPos])) shouldBe
          Vector(expected.cause.descriptor.facets.filterNot(_.isInstanceOf[Descriptor.Facet.SrcPos]))
        fixture.profile.driver.executions shouldBe empty

        When("the equivalent typed SbtExec is interpreted in that same authorized context")
        val result = new UnitOfWorkInterpreter(new UnitOfWork(fixture.context)).interpret(
          UnitOfWorkOp.SbtExec(fixture.target, SbtOperation.Test))

        Then("the typed route retains its actual result and executes exactly once")
        result.toOption shouldBe Some(SbtExecutionResult(fixture.target, SbtOperation.Test, fixture.result))
        fixture.profile.driver.executions should have size 1
      } finally _close(fixture.root)
    }

    "preserve every terminal result and bounded capture through the typed outcome mapping" in {
      Given("success, nonzero exit, confirmed unexecuted and every incomplete terminal category with bounded captures")
      val cases = Vector(
        ProcessExecutionTermination.Exited(0) -> SbtExecutionOutcome.Succeeded,
        ProcessExecutionTermination.Exited(3) -> SbtExecutionOutcome.Failed(3),
        ProcessExecutionTermination.NotStarted -> SbtExecutionOutcome.NotExecuted,
        ProcessExecutionTermination.LaunchFailed -> SbtExecutionOutcome.Incomplete(ProcessExecutionTermination.LaunchFailed),
        ProcessExecutionTermination.TerminationUnconfirmed -> SbtExecutionOutcome.Incomplete(ProcessExecutionTermination.TerminationUnconfirmed),
        ProcessExecutionTermination.TimedOut -> SbtExecutionOutcome.Incomplete(ProcessExecutionTermination.TimedOut),
        ProcessExecutionTermination.Cancelled -> SbtExecutionOutcome.Incomplete(ProcessExecutionTermination.Cancelled),
        ProcessExecutionTermination.OutputLimitExceeded(ProcessExecutionStream.Stdout) ->
          SbtExecutionOutcome.Incomplete(ProcessExecutionTermination.OutputLimitExceeded(ProcessExecutionStream.Stdout)),
        ProcessExecutionTermination.OutputLimitExceeded(ProcessExecutionStream.Stderr) ->
          SbtExecutionOutcome.Incomplete(ProcessExecutionTermination.OutputLimitExceeded(ProcessExecutionStream.Stderr)),
        ProcessExecutionTermination.ArtifactLimitExceeded -> SbtExecutionOutcome.Incomplete(ProcessExecutionTermination.ArtifactLimitExceeded)
      )
      val fixtures = cases.map { case (termination, outcome) => _fixture(_result(termination)) -> outcome }
      try {
        When("the typed Test and FixCheck DSLs consume each deterministic Process terminal result")
        val results = fixtures.flatMap { case (fixture, outcome) =>
          val behavior = new SbtDslBehavior(Behavior.Core(fixture.context, None, None))
          Vector(behavior.test(fixture.target), behavior.fixCheck(fixture.target)).zip(
            Vector(SbtOperation.Test, SbtOperation.FixCheck)).map { case (program, operation) =>
            (fixture, outcome, operation, new UnitOfWorkInterpreter(new UnitOfWork(fixture.context)).run(program))
          }
        }

        Then("the adapter maps only the terminal meaning and preserves all original bounded facts")
        results.foreach { case (fixture, outcome, operation, result) =>
          result.toOption.map(_.outcome) shouldBe Some(outcome)
          result.toOption.map(_.processResult) shouldBe Some(fixture.result)
          result.toOption.map(_.target) shouldBe Some(fixture.target)
          result.toOption.map(_.operation) shouldBe Some(operation)
          result.toOption.get.testSelection shouldBe None
          result.toOption.map(_.processResult.stdout.truncated) shouldBe Some(true)
          result.toOption.map(_.processResult.stderr.byteCount) shouldBe Some(3L)
          fixture.profile.driver.executions should have size 2
        }
      } finally fixtures.foreach { case (fixture, _) => _close(fixture.root) }
    }

    "retain a confirmed unexecuted receipt through one External SbtExec in the owning UnitOfWork" in {
      Given("a typed Test program, its current authorized context and an actual NotStarted process receipt")
      val fixture = _fixture(_result(ProcessExecutionTermination.NotStarted))
      val behavior = new SbtDslBehavior(Behavior.Core(fixture.context, None, None))
      val program = behavior.test(fixture.target)
      val expected = SbtExecutionResult(fixture.target, SbtOperation.Test, fixture.result)
      val provider = new UnitOfWorkProgramStubProvider {
        def resultFor[A](ordinal: Int, operation: UnitOfWorkOp[A]): Consequence[A] = operation match {
          case UnitOfWorkOp.SbtExec(_, SbtOperation.Test) => Consequence.success(expected)
          case _ => Consequence.notImplemented("unexpected operation in unexecuted SBT recording")
        }
      }
      val uow = new UnitOfWork(fixture.context)
      try {
        When("the program is recorded and interpreted once in its owning UnitOfWork")
        val recording = UnitOfWorkProgramRecorder.record(program, provider)
        val result = new UnitOfWorkInterpreter(uow).run(program)

        Then("one External SbtExec retains target, operation and process facts with no fallback or automatic replay")
        recording.occurrences.map(_.operation) shouldBe Vector(UnitOfWorkOp.SbtExec(fixture.target, SbtOperation.Test))
        recording.occurrences.map(_.effectClass) shouldBe Vector(UnitOfWorkEffectClass.External)
        recording.occurrences.map(_.ordinal) shouldBe Vector(0)
        recording.result.toOption shouldBe Some(expected)
        result.toOption shouldBe Some(expected)
        result.toOption.map(_.outcome) shouldBe Some(SbtExecutionOutcome.NotExecuted)
        fixture.profile.driver.executions should have size 1
      } finally _close(fixture.root)
    }

    "interpret generated nonzero exit codes as Failed without discarding the process result" in {
      Given("generated nonzero process exit codes and an immutable typed target")
      val target = SbtTarget(_workspace_id)
      val codes = Gen.choose(-128, 128).suchThat(_ != 0)

      When("the typed result interprets each exited terminal outcome")
      val property = Prop.forAll(codes) { code =>
        val process = _result(ProcessExecutionTermination.Exited(code))
        val result = SbtExecutionResult(target, SbtOperation.Compile, process)
        result.outcome == SbtExecutionOutcome.Failed(code) && result.processResult == process
      }
      val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(24), property)

      Then("each nonzero exit remains a typed failed build with its actual captured facts")
      checked.passed shouldBe true
    }

    "deny missing services, unknown targets, wrong owners, missing grants and ReadOnly before the driver" in {
      Given("typed Build fixtures whose workspace or independent component capability admission is denied")
      val terminal = _result(ProcessExecutionTermination.Exited(0))
      val fixtures = Vector(
        _fixture(terminal, workspaceinstalled = false),
        _fixture(terminal, processinstalled = false),
        _fixture(terminal, owner = ComponentId("org.goldenport.spec.WrongBuildOwner")),
        _fixture(terminal, workspacegranted = false),
        _fixture(terminal, capabilitygranted = false),
        _fixture(terminal, permission = WorkspacePermission.ReadOnly),
        _fixture(terminal)
      )
      try {
        When("typed Test and FixCheck intents are interpreted in each current context including an unregistered logical target")
        val results = fixtures.zipWithIndex.flatMap { case (fixture, index) =>
          val target = if (index == fixtures.size - 1) SbtTarget(WorkspaceId.parseC("unknown").toOption.get) else fixture.target
          Vector(SbtOperation.Test, SbtOperation.FixCheck).map(operation =>
            new UnitOfWorkInterpreter(new UnitOfWork(fixture.context)).interpret(UnitOfWorkOp.SbtExec(target, operation)))
        }

        Then("all admissions fail structurally and no driver receives an execution")
        results.foreach(_.isFaillure shouldBe true)
        fixtures.foreach(_.profile.driver.executions shouldBe empty)
      } finally fixtures.foreach(fixture => _close(fixture.root))
    }

    "reject an absent operation and scope-label impersonation even with installed runtime services" in {
      Given("valid typed services and a generic scope carrying the component owner's display label")
      val fixture = _fixture(_result(ProcessExecutionTermination.Exited(0)))
      val runtime = fixture.context.cncfCore.scope.parent.get.parent.get.parent.get
      val impersonated = fixture.context.withScope(runtime.createChildScope(ScopeKind.Component, _owner.name))
      try {
        When("typed SbtExec interpretation receives an unknown operation or the fabricated component scope")
        val unknown = new UnitOfWorkInterpreter(new UnitOfWork(fixture.context)).interpret(
          UnitOfWorkOp.SbtExec(fixture.target, null))
        val denied = new UnitOfWorkInterpreter(new UnitOfWork(impersonated)).interpret(
          UnitOfWorkOp.SbtExec(fixture.target, SbtOperation.Test))

        Then("neither case reaches a resolved process or deterministic driver")
        unknown.isFaillure shouldBe true
        denied.isFaillure shouldBe true
        fixture.profile.driver.executions shouldBe empty
      } finally _close(fixture.root)
    }

    "require the definition to retain the same shared binding object rather than an equal logical ID" in {
      Given("a tagged definition and a replacement shared service using the same logical ID at another root")
      val fixture = _fixture(_result(ProcessExecutionTermination.Exited(0)))
      val second = Files.createDirectory(fixture.root.resolve("other"))
      val replacement = WorkspaceExecutionAdmission.createC(Vector(_workspace_id -> second),
        Vector(WorkspaceGrant(_owner, _workspace_id, WorkspacePermission.Manage))).toOption.get
      val context = _context(Some(replacement), Some(fixture.admission), fixture.profile, _owner)
      try {
        When("direct typed Test interpretation resolves the replacement logical workspace")
        val denied = new UnitOfWorkInterpreter(new UnitOfWork(context)).interpret(
          UnitOfWorkOp.SbtExec(fixture.target, SbtOperation.Test))

        Then("binding identity mismatch is rejected before driver invocation")
        denied.isFaillure shouldBe true
        fixture.profile.driver.executions shouldBe empty
      } finally _close(fixture.root)
    }

    "reject generic Process requests that select a tagged SBT capability" in {
      Given("tagged Compile, Test, TestOnly and FixCheck capabilities with both component and generic admission services")
      val fixture = _fixture(_result(ProcessExecutionTermination.Exited(0)))
      val capabilities = Vector(_compile_capability, _test_capability, _test_only_capability, _fix_check_capability)
      val requests = capabilities.map(ProcessExecutionRequest(_))
      val generic = ProcessExecutionAdmission.createC(fixture.policy, capabilities.map(ProcessExecutionGrant(_))).toOption.get
      val behavior = new SbtDslBehavior(Behavior.Core(fixture.context, None, None))
      try {
        When("generic policy, admission and protected process DSL try to resolve the tagged definition")
        val policyresults = requests.map(request => fixture.policy.resolveC(request, ProcessExecutionGrant(request.capability)))
        val admissionresults = requests.map(generic.admitC)
        val dslresults = requests.map(request => new UnitOfWorkInterpreter(new UnitOfWork(fixture.context)).run(behavior.generic(request)))

        Then("all generic routes fail even though the typed component grant exists")
        policyresults.foreach(_.isFaillure shouldBe true)
        admissionresults.foreach(_.isFaillure shouldBe true)
        dslresults.foreach(_.isFaillure shouldBe true)
        fixture.profile.driver.executions shouldBe empty
      } finally _close(fixture.root)
    }

    "reject duplicate workspace-operation definitions" in {
      Given("a trusted Test definition and a second capability for the same workspace and operation")
      val fixture = _fixture(_result(ProcessExecutionTermination.Exited(0)))
      val duplicate = SbtProgramDefinition.createC(fixture.workspace.bindingC(_workspace_id).toOption.get,
        SbtOperation.Test, ProcessCapabilityId.parseC("second-test").toOption.get, "second-test",
        _sbt_path, _limits, Map.empty).toOption.get
      try {
        When("runtime assembly combines the definitions")
        val denied = ProcessExecutionPolicy.createC(fixture.definitions :+ duplicate)

        Then("the duplicate operation cannot be installed")
        denied.isFaillure shouldBe true
        fixture.profile.driver.executions shouldBe empty
      } finally _close(fixture.root)
    }

    "validate a normal absolute sbt executable without exposing unavailable locations in failures" in {
      Given("one admitted workspace and relative, missing, directory and non-executable host paths")
      val fixture = _fixture(_result(ProcessExecutionTermination.Exited(0)))
      val binding = fixture.workspace.bindingC(_workspace_id).toOption.get
      val plainfile = Files.createFile(fixture.root.resolve("not-executable"))
      try {
        When("trusted definition assembly receives invalid host program locations")
        val relative = SbtProgramDefinition.createC(binding, SbtOperation.Test, _test_capability,
          "project-test", Paths.get("sbt"), _limits, Map.empty)
        val missing = SbtProgramDefinition.createC(binding, SbtOperation.Test, _test_capability,
          "project-test", fixture.root.resolve("missing-sbt"), _limits, Map.empty)
        val directory = SbtProgramDefinition.createC(binding, SbtOperation.Test, _test_capability,
          "project-test", fixture.root, _limits, Map.empty)
        val nonexecutable = SbtProgramDefinition.createC(binding, SbtOperation.Test, _test_capability,
          "project-test", plainfile, _limits, Map.empty)

        Then("each definition fails safely before it can reach a process driver")
        Vector(relative, missing, directory, nonexecutable).foreach { result =>
          result.isFaillure shouldBe true
          result.toString should not include fixture.root.toString
        }
        fixture.profile.driver.executions shouldBe empty
      } finally _close(fixture.root)
    }
  }

  "Fixed check typed intent" should {
    "reject scenarios and generic ProcessExec before the driver" in {
      Given("an admitted FixCheck definition and a target carrying an otherwise valid exact scenario")
      val fixture = _fixture(_result(ProcessExecutionTermination.Exited(0)))
      val target = fixture.target.copy(scenario = Some(SbtScenarioSelection("org.example.SelectedSpec", "selected case")))
      val execution = SbtExecutionAdmission.resolveC(fixture.context, fixture.target, SbtOperation.FixCheck).toOption.get
      val uow = new UnitOfWork(fixture.context)
      try {
        When("the owning interpreter receives the scenario intent and a directly supplied generic execution")
        val scenario = new UnitOfWorkInterpreter(uow).interpret(UnitOfWorkOp.SbtExec(target, SbtOperation.FixCheck))
        val generic = new UnitOfWorkInterpreter(uow).interpret(UnitOfWorkOp.ProcessExec(execution))
        Then("both fail admission while the approved empty request and fixed metadata remain unchanged")
        scenario.isFaillure shouldBe true
        generic.isFaillure shouldBe true
        execution.request.arguments shouldBe empty
        _expect_heap_definitions(Vector(execution), Vector(fixture.definitions(2)))
        fixture.profile.driver.executions shouldBe empty
      } finally { uow.dispose(); _close(fixture.root) }
    }
  }

  "E5 Installed runtime typed SBT binding" should afterWord(
    "in spec:installed-runtime-distribution, example:E5, rules:R4,R5,R6, phase:64") {
    "retain ordinary Compile, Test and TestOnly for default and explicit project mode with an installed application" in {
      Vector(None, Some("project")).foreach { mode =>
        Given("docs/spec/installed-runtime-distribution.md R4-R6 E5 and an installed CLI with a project build target")
        val fixture = _fixture(_selection_process(Vector("org.example.SelectedSpec")))
        fixture.context.runtime.setResolvedParameters(ResolvedParameters.empty())
        val operations = Vector(SbtOperation.Compile, SbtOperation.Test, SbtOperation.TestOnly)
        val original = operations.map(operation => SbtExecutionAdmission.resolveC(fixture.context, fixture.target, operation).toOption.get)
        val manifest = InstalledRuntimeDistributionSpec.writeFixture(fixture.root)
        val parameters = _runtime_parameters(Some(manifest), mode.toVector.map(value =>
          _workspace_id -> ConfigurationValue.StringValue(value)))
        fixture.context.runtime.setResolvedParameters(parameters)
        try {
          When("the same project intents are admitted with the application's manifest still resolved")
          val executions = operations.map(operation => SbtExecutionAdmission.resolveC(fixture.context, fixture.target, operation).toOption.get)

          Then("the complete original executions retain their tagged definitions, arguments, requests and limits")
          executions.map(_.effectiveArguments) shouldBe original.map(_.effectiveArguments)
          executions.map(_.request) shouldBe original.map(_.request)
          executions.map(_.effectiveLimits) shouldBe original.map(_.effectiveLimits)
          fixture.profile.driver.executions shouldBe empty
          parameters.get(RuntimeConfig.installedRuntimeManifestKey).map(_.value) shouldBe
            Some(ConfigurationValue.StringValue(manifest.toString))

          When("the owning UnitOfWork interprets each ordinary project intent")
          val results = operations.map(operation => new UnitOfWorkInterpreter(new UnitOfWork(fixture.context)).interpret(
            UnitOfWorkOp.SbtExecAttempt(fixture.target, operation)))

          Then("the original results and selected-suite interpretation remain unchanged")
          results.map(_.toOption) shouldBe Vector(
            Some(SbtExecutionAttempt.Observed(SbtExecutionResult(fixture.target, SbtOperation.Compile, fixture.result))),
            Some(SbtExecutionAttempt.Observed(SbtExecutionResult(fixture.target, SbtOperation.Test, fixture.result))),
            Some(SbtExecutionAttempt.Observed(SbtExecutionResult(fixture.target, SbtOperation.TestOnly, fixture.result,
              Some(SbtTestSelectionResult.Verified(Vector("org.example.SelectedSpec"), 1, 7L))))))
          _expect_same_execution_values(fixture.profile.driver.executions, original)
        } finally _close(fixture.root)
      }
    }

    "bind Compile, Test, TestOnly and FixCheck after admission while preserving the empty request and limits" in {
      Given("docs/spec/installed-runtime-distribution.md R4-R6 E5 and an admitted current runtime installation")
      val fixture = _fixture(_selection_process(Vector("org.example.SelectedSpec")))
      fixture.context.runtime.setResolvedParameters(ResolvedParameters.empty())
      val operations = Vector(SbtOperation.Compile, SbtOperation.Test, SbtOperation.TestOnly, SbtOperation.FixCheck)
      val original = operations.map(operation => SbtExecutionAdmission.resolveC(fixture.context, fixture.target, operation).toOption.get)
      val manifest = InstalledRuntimeDistributionSpec.writeFixture(fixture.root)
      fixture.context.runtime.setResolvedParameters(_runtime_parameters(Some(manifest),
        Vector(_workspace_id -> ConfigurationValue.StringValue("installed"))))
      try {
        When("the same typed intents are admitted with current resolved configuration")
        val bound = operations.map(operation => SbtExecutionAdmission.resolveC(fixture.context, fixture.target, operation).toOption.get)
        Then("only fixed definition arguments acquire the concrete manifest and required plugin prerequisite")
        bound.zip(original).foreach { case (execution, previous) =>
          execution.request shouldBe previous.request
          execution.request.arguments shouldBe empty
          execution.effectiveLimits shouldBe previous.effectiveLimits
          execution.definition.capability shouldBe previous.definition.capability
          execution.definition.safeProgramIdentity shouldBe previous.definition.safeProgramIdentity
          execution.definition.maximumLimits shouldBe previous.definition.maximumLimits
          execution.effectiveArguments shouldBe previous.effectiveArguments.dropRight(1) ++ Vector(
            s"-D${RuntimeConfig.installedRuntimeManifestKey}=$manifest",
            "-Dcncf.installed-runtime.required=true", "cozyCheckInstalledRuntime",
            previous.effectiveArguments.last)
          SbtExecutionAdmission.validateGenericExecutionC(execution).isFaillure shouldBe true
        }
        fixture.profile.driver.executions shouldBe empty
        When("the owning UnitOfWork interprets TestOnly and FixCheck using that binding")
        val result = new UnitOfWorkInterpreter(new UnitOfWork(fixture.context)).interpret(
          UnitOfWorkOp.SbtExecAttempt(fixture.target, SbtOperation.TestOnly))
        val checked = new UnitOfWorkInterpreter(new UnitOfWork(fixture.context)).interpret(
          UnitOfWorkOp.SbtExecAttempt(fixture.target, SbtOperation.FixCheck))
        Then("the original selected suite is decoded and FixCheck retains its result without test evidence")
        result.toOption shouldBe Some(SbtExecutionAttempt.Observed(SbtExecutionResult(
          fixture.target, SbtOperation.TestOnly, fixture.result,
          Some(SbtTestSelectionResult.Verified(Vector("org.example.SelectedSpec"), 1, 7L)))))
        checked.toOption shouldBe Some(SbtExecutionAttempt.Observed(SbtExecutionResult(
          fixture.target, SbtOperation.FixCheck, fixture.result)))
        fixture.profile.driver.executions.last.effectiveArguments shouldBe bound.last.effectiveArguments
        fixture.profile.driver.executions should have size 2
      } finally _close(fixture.root)
    }

    "select installed binding only for the configured logical workspace" in {
      Given("docs/spec/installed-runtime-distribution.md R4-R6 E5 and two targets sharing the same resolved configuration")
      val otherid = WorkspaceId.parseC("other-project").toOption.get
      val fixtures = Vector(_fixture(_result(ProcessExecutionTermination.Exited(0))),
        _fixture(_result(ProcessExecutionTermination.Exited(0)), workspaceid = otherid))
      fixtures.foreach(_.context.runtime.setResolvedParameters(ResolvedParameters.empty()))
      val operations = Vector(SbtOperation.Compile, SbtOperation.Test, SbtOperation.TestOnly)
      val original = fixtures.map(fixture => operations.map(operation =>
        SbtExecutionAdmission.resolveC(fixture.context, fixture.target, operation).toOption.get))
      val manifest = InstalledRuntimeDistributionSpec.writeFixture(fixtures.head.root)
      val parameters = _runtime_parameters(Some(manifest), Vector(_workspace_id -> ConfigurationValue.StringValue("installed")))
      fixtures.foreach(_.context.runtime.setResolvedParameters(parameters))
      try {
        When("both targets admit their Compile, Test and TestOnly intents")
        val executions = fixtures.map(fixture => operations.map(operation =>
          SbtExecutionAdmission.resolveC(fixture.context, fixture.target, operation).toOption.get))

        Then("only the named target receives the installed prerequisite and the other remains an ordinary project")
        executions.head.zip(original.head).foreach { case (execution, previous) =>
          execution.effectiveArguments shouldBe previous.effectiveArguments.dropRight(1) ++ Vector(
            s"-D${RuntimeConfig.installedRuntimeManifestKey}=$manifest",
            "-Dcncf.installed-runtime.required=true", "cozyCheckInstalledRuntime", previous.effectiveArguments.last)
        }
        _expect_same_execution_values(executions(1), original(1))
        fixtures.foreach(_.profile.driver.executions shouldBe empty)
      } finally fixtures.foreach(fixture => _close(fixture.root))
    }

    "reject explicit installed mode with missing or invalid manifest rather than falling back to project" in {
      Given("docs/spec/installed-runtime-distribution.md R4-R6 E5 and a granted target selecting installed binding")
      val fixture = _fixture(_result(ProcessExecutionTermination.Exited(0)))
      val malformed = Files.writeString(fixture.root.resolve("malformed-manifest.json"), "{}")
      val bindings = Vector(_workspace_id -> ConfigurationValue.StringValue("installed"))
      val parameters = Vector(None, Some(fixture.root.resolve("missing-manifest.json")), Some(malformed))
        .map(manifest => _runtime_parameters(manifest, bindings))
      try {
        parameters.foreach { configuration =>
          fixture.context.runtime.setResolvedParameters(configuration)
          When("the owning UnitOfWork interprets an explicitly installed intent without a valid concrete manifest")
          val results = Vector(SbtOperation.Compile, SbtOperation.Test, SbtOperation.TestOnly).map(operation =>
            new UnitOfWorkInterpreter(new UnitOfWork(fixture.context)).interpret(
              UnitOfWorkOp.SbtExecAttempt(fixture.target, operation)))

          Then("every intent is rejected before process execution and no project-mode retry occurs")
          results.zip(Vector(SbtOperation.Compile, SbtOperation.Test, SbtOperation.TestOnly)).foreach { case (result, operation) =>
            result.toOption.get match {
              case SbtExecutionAttempt.AdmissionRejected(target, kind, _) =>
                target shouldBe fixture.target
                kind shouldBe operation
              case other => fail(s"Expected installed binding rejection, received $other")
            }
          }
          fixture.profile.driver.executions shouldBe empty
        }
      } finally _close(fixture.root)
    }

    "reject unknown modes and non-string modes before driver execution" in {
      Given("docs/spec/installed-runtime-distribution.md R4-R6 E5 and closed binding values with no parser aliases")
      val fixture = _fixture(_result(ProcessExecutionTermination.Exited(0)))
      val manifest = InstalledRuntimeDistributionSpec.writeFixture(fixture.root)
      val invalid = Vector("", "unknown", "Installed", " project", "project,installed")
        .map(ConfigurationValue.StringValue(_)) ++ Vector(
          ConfigurationValue.BooleanValue(true), ConfigurationValue.NumberValue(BigDecimal(1)),
          ConfigurationValue.ListValue(List(ConfigurationValue.StringValue("project"))),
          ConfigurationValue.ObjectValue(Map.empty), ConfigurationValue.NullValue)
      try {
        invalid.foreach { value =>
          fixture.context.runtime.setResolvedParameters(_runtime_parameters(Some(manifest), Vector(_workspace_id -> value)))
          When("the owning UnitOfWork interprets the target with an unsupported binding value or type")
          val result = new UnitOfWorkInterpreter(new UnitOfWork(fixture.context)).interpret(
            UnitOfWorkOp.SbtExecAttempt(fixture.target, SbtOperation.Test))

          Then("mode admission rejects the intent even though the application manifest is valid")
          result.toOption.get match {
            case SbtExecutionAttempt.AdmissionRejected(target, operation, _) =>
              target shouldBe fixture.target
              operation shouldBe SbtOperation.Test
            case other => fail(s"Expected mode rejection, received $other")
          }
          fixture.profile.driver.executions shouldBe empty
        }
      } finally _close(fixture.root)
    }

    "retain denied workspace and process grant precedence over invalid binding configuration" in {
      Given("docs/spec/installed-runtime-distribution.md R4-R6 E5 and contexts missing either independent grant")
      val fixtures = Vector(_fixture(_result(ProcessExecutionTermination.Exited(0)), workspacegranted = false),
        _fixture(_result(ProcessExecutionTermination.Exited(0)), capabilitygranted = false))
      fixtures.foreach(_.context.runtime.setResolvedParameters(ResolvedParameters.empty()))
      val denied = fixtures.map(fixture => _failure_conclusion(
        SbtExecutionAdmission.resolveC(fixture.context, fixture.target, SbtOperation.Test)))
      val parameters = Vector(
        _runtime_parameters(None, Vector(_workspace_id -> ConfigurationValue.StringValue("installed"))),
        _runtime_parameters(None, Vector(_workspace_id -> ConfigurationValue.StringValue("unknown"))),
        _runtime_parameters(None, Vector(_workspace_id -> ConfigurationValue.BooleanValue(true))))
      try {
        parameters.foreach { configuration =>
          fixtures.foreach(_.context.runtime.setResolvedParameters(configuration))
          When("typed Test admission receives invalid configuration after an already denied grant")
          val results = fixtures.map(fixture =>
            SbtExecutionAdmission.resolveC(fixture.context, fixture.target, SbtOperation.Test))

          Then("each original structured grant diagnostic remains authoritative and no driver starts")
          results.zip(denied).foreach { case (result, previous) =>
            val conclusion = _failure_conclusion(result)
            conclusion.status shouldBe previous.status
            conclusion.observation.taxonomy shouldBe previous.observation.taxonomy
            conclusion.observation.cause.kind shouldBe previous.observation.cause.kind
            conclusion.observation.cause.descriptor.facets.filterNot(_.isInstanceOf[Descriptor.Facet.SrcPos]) shouldBe
              previous.observation.cause.descriptor.facets.filterNot(_.isInstanceOf[Descriptor.Facet.SrcPos])
          }
          fixtures.foreach(_.profile.driver.executions shouldBe empty)
        }
      } finally fixtures.foreach(fixture => _close(fixture.root))
    }
  }


  "E-OUTPUT1 Current SBT test capture defaults" must afterWord(
    "in spec:typed-sbt-execution, example:E-OUTPUT1, rules:TEST-OUTPUT-01,TEST-OUTPUT-02, phase:116") {
    "apply both defaults through the actual Test and TestOnly interpreter" in {
      Given("docs/spec/typed-sbt-execution.md TEST-OUTPUT-01/02 E-OUTPUT1: host ceilings above both defaults")
      val limits = _limits.copy(stdoutBytes = Some(134217728L), stderrBytes = Some(16777216L))
      val fixture = _fixture(_selection_process(Vector("org.example.SelectedSpec")), limits = limits)
      val uow = new UnitOfWork(fixture.context)
      try {
        When("the current UnitOfWork interprets both typed test operations")
        val results = Vector(SbtOperation.Test, SbtOperation.TestOnly).map(operation =>
          new UnitOfWorkInterpreter(uow).interpret(UnitOfWorkOp.SbtExec(fixture.target, operation)))
        Then("both drivers receive 64 MiB stdout and 8 MiB stderr without other limit changes")
        results.foreach(_.isSuccess shouldBe true)
        fixture.profile.driver.executions.map(_.effectiveLimits) shouldBe Vector.fill(2)(
          limits.copy(stdoutBytes = Some(67108864L), stderrBytes = Some(8388608L)))
        fixture.profile.driver.executions.foreach(_.request.requestedLimits shouldBe ProcessExecutionLimits.empty)
      } finally { uow.dispose(); _close(fixture.root) }
    }
  }

  "E-OUTPUT2 Current-context properties and host ceilings" must afterWord(
    "in spec:typed-sbt-execution, example:E-OUTPUT2, rules:TEST-OUTPUT-01,TEST-OUTPUT-02, phase:116") {
    "apply properties independently without enlarging the explicit host ceiling" in {
      Given("docs/spec/typed-sbt-execution.md TEST-OUTPUT-01/02 E-OUTPUT2: current properties below stdout and above stderr authority")
      val fixture = _fixture(_selection_process(Vector("org.example.SelectedSpec")))
      val parameters = ResolvedParameters.fromResolvedConfiguration(ResolvedConfiguration(
        Configuration(Map(SbtTestOutputParameters.stdoutKey -> ConfigurationValue.NumberValue(BigDecimal(32768)),
          SbtTestOutputParameters.stderrKey -> ConfigurationValue.NumberValue(BigDecimal(1048576)))), ConfigurationTrace.empty))
      fixture.context.runtime.setResolvedParameters(parameters)
      val uow = new UnitOfWork(fixture.context)
      try {
        When("the actual Test and exact TestOnly scenario run under that current configuration")
        val target = fixture.target.copy(scenario = Some(SbtScenarioSelection("org.example.SelectedSpec", "selected case")))
        val results = Vector(
          new UnitOfWorkInterpreter(uow).interpret(UnitOfWorkOp.SbtExec(fixture.target, SbtOperation.Test)),
          new UnitOfWorkInterpreter(uow).interpret(UnitOfWorkOp.SbtExec(target, SbtOperation.TestOnly)))
        Then("both executions retain the host stderr cap and select the requested smaller stdout cap")
        results.foreach(_.isSuccess shouldBe true)
        fixture.profile.driver.executions.map(_.effectiveLimits) shouldBe Vector.fill(2)(
          _limits.copy(stdoutBytes = Some(32768L)))
        fixture.profile.driver.executions.last.effectiveArguments.last should include("selected case")
      } finally { uow.dispose(); _close(fixture.root) }
    }
  }

  "E-OUTPUT3 Invalid test property admission" must afterWord(
    "in spec:typed-sbt-execution, example:E-OUTPUT3, rules:TEST-OUTPUT-01,TEST-OUTPUT-02, phase:116") {
    "reject invalid Test properties before dispatch while Compile remains usable" in {
      Given("docs/spec/typed-sbt-execution.md TEST-OUTPUT-01/02 E-OUTPUT3: an invalid present numeric test setting")
      val fixture = _fixture(_result(ProcessExecutionTermination.Exited(0)))
      fixture.context.runtime.setResolvedParameters(ResolvedParameters.fromResolvedConfiguration(
        ResolvedConfiguration(Configuration(Map(SbtTestOutputParameters.stderrKey ->
          ConfigurationValue.NumberValue(BigDecimal(0)))), ConfigurationTrace.empty)))
      val uow = new UnitOfWork(fixture.context)
      try {
        When("the owning interpreter admits Test and TestOnly")
        val denied = Vector(SbtOperation.Test, SbtOperation.TestOnly).map(operation =>
          new UnitOfWorkInterpreter(uow).interpret(UnitOfWorkOp.SbtExec(fixture.target, operation)))
        Then("both fail before the driver and expose the key rather than a synthetic process receipt")
        denied.foreach(_.isFaillure shouldBe true)
        denied.foreach(_.toString should include(SbtTestOutputParameters.stderrKey))
        fixture.profile.driver.executions shouldBe empty
        When("Compile and FixCheck are interpreted under the same current configuration")
        val builds = Vector(SbtOperation.Compile, SbtOperation.FixCheck).map(operation =>
          new UnitOfWorkInterpreter(uow).interpret(UnitOfWorkOp.SbtExec(fixture.target, operation)))
        Then("the test-only configuration does not alter their admitted limits")
        builds.foreach(_.isSuccess shouldBe true)
        fixture.profile.driver.executions.map(_.effectiveLimits) shouldBe Vector.fill(2)(_limits)
      } finally { uow.dispose(); _close(fixture.root) }
    }
  }

  private def _expect_same_execution_values(actual: Vector[ResolvedProcessExecution], expected: Vector[ResolvedProcessExecution]): Unit = {
    actual should have size expected.size
    actual.zip(expected).foreach { case (left, right) =>
      left.request shouldBe right.request
      left.effectiveLimits shouldBe right.effectiveLimits
      left.effectiveArguments shouldBe right.effectiveArguments
      left.definition.capability shouldBe right.definition.capability
      left.definition.safeProgramIdentity shouldBe right.definition.safeProgramIdentity
      left.definition.maximumLimits shouldBe right.definition.maximumLimits
    }
  }

  private def _expect_heap_definitions(executions: Vector[ResolvedProcessExecution], originals: Vector[ProcessProgramDefinition]): Unit = {
    executions should have size originals.size
    executions.zip(originals).foreach { case (execution, original) =>
      execution.definition.capability shouldBe original.capability
      execution.definition.safeProgramIdentity shouldBe original.safeProgramIdentity
      execution.definition.maximumLimits shouldBe original.maximumLimits
      execution.definition.allowedArtifacts shouldBe original.allowedArtifacts
      execution.definition.fixedArguments shouldBe ("-J-Xmx4294967296" +: original.fixedArguments)
    }
  }

  private def _runtime_parameters(
    manifest: Option[Path],
    bindings: Vector[(WorkspaceId, ConfigurationValue)]
  ): ResolvedParameters = {
    val values = manifest.map(path => RuntimeConfig.installedRuntimeManifestKey ->
      ConfigurationValue.StringValue(path.toString)).toMap ++ bindings.map { case (workspaceid, value) =>
      RuntimeConfig.sbtRuntimeBindingKey(workspaceid) -> value
    }
    ResolvedParameters.fromResolvedConfiguration(
      ResolvedConfiguration(Configuration(values), ConfigurationTrace.empty))
  }

  private def _failure_conclusion[A](result: Consequence[A]): Conclusion = result match {
    case Consequence.Failure(conclusion) => conclusion
    case Consequence.Success(value) => fail(s"Expected admission failure, received success: $value")
  }

  private def _sbt_path: Path = Paths.get("/usr/bin/true")

  private def _selection_process(classes: Vector[String]): ProcessExecutionResult = {
    val report = (classes.map(x => s"[info] $x:") ++ Vector(
      "[info] Total number of tests run: 7", s"[info] Suites: completed ${classes.size}, aborted 0",
      "[info] Tests: succeeded 7, failed 0, canceled 0, ignored 0, pending 0", "[info] All tests passed.")).mkString("\n")
    val bytes = report.getBytes(java.nio.charset.StandardCharsets.UTF_8).toVector
    ProcessExecutionResult(ProcessExecutionTermination.Exited(0), ProcessExecutionCapture(bytes, bytes.size.toLong, false),
      ProcessExecutionCapture(Vector.empty, 0L, false), Vector.empty, 17L, "project-build")
  }

  private def _fixture(
    result: ProcessExecutionResult,
    permission: WorkspacePermission = WorkspacePermission.Manage,
    owner: ComponentId = _owner,
    workspaceinstalled: Boolean = true,
    processinstalled: Boolean = true,
    capabilitygranted: Boolean = true,
    workspacegranted: Boolean = true,
    driverinstalled: Boolean = true,
    testclasses: Vector[String] = Vector("org.example.SelectedSpec"),
    driver: Option[ProcessExecutionDriver] = None,
    workspaceid: WorkspaceId = _workspace_id,
    limits: ProcessExecutionLimits = _limits
  ): Fixture = {
    val base = Files.createDirectories(Paths.get("target", "sbt-execution-dsl-spec").toAbsolutePath)
    val root = Files.createTempDirectory(base, "case-")
    val checkout = Files.createDirectory(root.resolve("checkout"))
    val workspacegrants = if (workspacegranted) Vector(WorkspaceGrant(_owner, workspaceid, permission)) else Vector.empty
    val workspace = WorkspaceExecutionAdmission.createC(Vector(workspaceid -> checkout), workspacegrants).toOption.get
    val binding = workspace.bindingC(workspaceid).toOption.get
    val definitions = Vector(
      SbtOperation.Compile -> _compile_capability, SbtOperation.Test -> _test_capability,
      SbtOperation.FixCheck -> _fix_check_capability,
      SbtOperation.TestOnly -> _test_only_capability
    ).map { case (operation, capability) =>
      SbtProgramDefinition.createC(binding, operation, capability, capability.print,
        _sbt_path, limits, Map("TEXTUS_BUILD_PROFILE" -> "test"),
        if (operation == SbtOperation.TestOnly) testclasses else Vector.empty).toOption.get
    }
    val policy = ProcessExecutionPolicy.createC(definitions).toOption.get
    val grants = if (capabilitygranted) Vector(_compile_capability, _test_capability, _test_only_capability, _fix_check_capability).map(ProcessExecutionGrant(_)) else Vector.empty
    val admission = ProcessExecutionAdmission.forComponentsC(policy, Map(_owner -> grants)).toOption.get
    val profile = ProcessExecutionTestProfile(Map(_compile_capability -> result, _test_capability -> result,
      _test_only_capability -> result, _fix_check_capability -> result))
    val context = _context(if (workspaceinstalled) Some(workspace) else None,
      if (processinstalled) Some(admission) else None, profile, owner, driverinstalled, driver)
    Fixture(root, SbtTarget(workspaceid), result, workspace, definitions, policy, admission, profile, context)
  }

  private def _context(
    workspace: Option[WorkspaceExecutionAdmission],
    admission: Option[ProcessExecutionAdmission],
    profile: ProcessExecutionTestProfile,
    owner: ComponentId,
    driverinstalled: Boolean = true,
    driver: Option[ProcessExecutionDriver] = None
  ): ExecutionContext = {
    val base = Files.createDirectories(Paths.get("target", "sbt-execution-dsl-spec", "workareas").toAbsolutePath)
    val space = new WorkAreaSpace(WorkAreaGroup(new FileWorkArea(
      WorkAreaId("sbt_spec"), WorkArea.Limit(None, None, None), base
    )))
    GlobalContext.set(GlobalContext(space))
    val execution = ExecutionContext.create()
    val runtime = ScopeContext(ScopeKind.Runtime, "sbt-spec", None, execution.observability,
      processexecutiondriveroption = if (driverinstalled) driver.orElse(Some(profile.driver)) else None,
      processexecutionadmissionoption = admission,
      workspaceExecutionAdmissionOption = workspace)
    val component = Component.create(owner.name, owner, ComponentInstanceId.default(owner), Protocol.empty)
    val scope = Component.Context(owner.name, runtime, component, ComponentOrigin.Unknown)
      .createChildScope(ScopeKind.Service, "build").createChildScope(ScopeKind.Action, "test")
    execution.withScope(scope)
  }

  private def _result(termination: ProcessExecutionTermination): ProcessExecutionResult =
    ProcessExecutionResult(termination,
      ProcessExecutionCapture(Vector(1.toByte, 2.toByte), 2L, truncated = true),
      ProcessExecutionCapture(Vector(3.toByte, 4.toByte, 5.toByte), 3L, truncated = false),
      Vector.empty, 17L, "project-build")

  private def _close(root: Path): Unit =
    Using.resource(Files.walk(root)) { stream =>
      stream.sorted(Comparator.reverseOrder[Path]()).iterator().asScala.foreach(path => Files.deleteIfExists(path))
    }
}

object SbtExecutionDslSpec {
  private final case class Fixture(
    root: Path,
    target: SbtTarget,
    result: ProcessExecutionResult,
    workspace: WorkspaceExecutionAdmission,
    definitions: Vector[ProcessProgramDefinition],
    policy: ProcessExecutionPolicy,
    admission: ProcessExecutionAdmission,
    profile: ProcessExecutionTestProfile,
    context: ExecutionContext
  )
}

private final class SbtDslBehavior(val behaviorCore: Behavior.Core) extends Behavior {
  def compile(target: SbtTarget) = sbt_compile(target)
  def test(target: SbtTarget) = sbt_test(target)
  def testOnly(target: SbtTarget) = sbt_test_only(target)
  def fixCheck(target: SbtTarget) = sbt_fix_check(target)
  def compileAttempt(target: SbtTarget) = sbt_compile_attempt(target)
  def testAttempt(target: SbtTarget) = sbt_test_attempt(target)
  def testOnlyAttempt(target: SbtTarget) = sbt_test_only_attempt(target)
  def fixCheckAttempt(target: SbtTarget) = sbt_fix_check_attempt(target)
  def generic(request: ProcessExecutionRequest) = process_exec(request)
}
