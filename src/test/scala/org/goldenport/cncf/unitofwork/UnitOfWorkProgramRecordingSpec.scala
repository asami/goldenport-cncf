package org.goldenport.cncf.unitofwork

import java.nio.file.Path
import cats.free.Free
import cats.syntax.all.*
import org.goldenport.{Consequence, ConsequenceT, Conclusion}
import org.goldenport.cncf.processexecution.{ProcessExecutionCapture, ProcessExecutionResult, ProcessExecutionTermination, SbtExecutionOutcome, SbtExecutionResult, SbtOperation, SbtTarget, SbtTestSelectionResult}
import org.goldenport.cncf.workspace.WorkspaceId
import org.goldenport.cncf.processexecution.{GitExecutionOutcome, GitExecutionResult, GitOperation, GitTarget}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * Executable specification for deterministic UnitOfWork program recording.
 *
 * @since   Sep.  8, 2026
 * @version Oct. 15, 2026
 * @author  ASAMI, Tomoharu
 */
final class UnitOfWorkProgramRecordingSpec
    extends AnyWordSpec
    with Matchers
    with GivenWhenThen {
  private val _authorization = UnitOfWorkAuthorization(
    resourceFamily = "component",
    accessKind = "read"
  )
  private val _sbt_target = SbtTarget(WorkspaceId.parseC("recording").toOption.get)

  "UnitOfWorkProgramRecorder" should {
    "record one TestOnly External occurrence and a deliberately supplied selected-suite result without services" in {
      Given("one logical TestOnly declaration and a typed result supplied independently of runtime services")
      val intent = UnitOfWorkOp.SbtExec(_sbt_target, SbtOperation.TestOnly)
      val supplied = SbtExecutionResult(_sbt_target, SbtOperation.TestOnly,
        _process_result(ProcessExecutionTermination.Exited(0)),
        Some(SbtTestSelectionResult.Verified(Vector("org.example.RecordedSpec"), 1, 9L)))
      val program: ExecUowM[SbtExecutionResult] = ConsequenceT.liftF(Free.liftF(intent))
      val provider = new UnitOfWorkProgramStubProvider {
        def resultFor[A](ordinal: Int, operation: UnitOfWorkOp[A]): Consequence[A] = operation match {
          case UnitOfWorkOp.SbtExec(_, SbtOperation.TestOnly) => Consequence.success(supplied)
          case _ => Consequence.notImplemented("unexpected TestOnly recorder operation")
        }
      }
      When("the existing recorder consumes the supplied value without interpreting runtime services")
      val recording = UnitOfWorkProgramRecorder.record(program, provider)
      Then("the original TestOnly intent and supplied result occupy exactly one External boundary")
      recording.result.toOption shouldBe Some(supplied)
      recording.occurrences.map(_.operation) shouldBe Vector(intent)
      recording.occurrences.map(_.ordinal) shouldBe Vector(0)
      recording.occurrences.map(_.effectClass) shouldBe Vector(UnitOfWorkEffectClass.External)
      recording.plan.segments shouldBe recording.plan.occurrences.map(UnitOfWorkPlanSegment.ExternalBoundary.apply)
    }

    "record one typed Git declaration and supplied result without checking runtime authority" in {
      Given("a Git Status declaration and a typed stub result without any runtime services")
      val target = GitTarget(WorkspaceId.parseC("recording").toOption.get)
      val intent = UnitOfWorkOp.GitExec(target, GitOperation.Status)
      val process = _process_result(ProcessExecutionTermination.Exited(0))
      val supplied = GitExecutionResult(target, GitOperation.Status, GitExecutionOutcome.Succeeded, Some(Vector.empty), process)
      val program: ExecUowM[GitExecutionResult[Vector[org.goldenport.cncf.processexecution.GitStatusEntry]]] =
        ConsequenceT.liftF(Free.liftF(intent))
      val provider = new UnitOfWorkProgramStubProvider {
        def resultFor[A](ordinal: Int, operation: UnitOfWorkOp[A]): Consequence[A] = operation match {
          case UnitOfWorkOp.GitExec(_, GitOperation.Status) => Consequence.success(supplied)
          case _ => Consequence.notImplemented("unexpected operation in Git recording")
        }
      }
      When("the existing generic recorder visits the typed intent using only its StubProvider")
      val recording = UnitOfWorkProgramRecorder.record(program, provider)
      Then("one original Git intent and its supplied typed value remain in the External plan")
      recording.result.toOption shouldBe Some(supplied)
      recording.occurrences.map(_.operation) shouldBe Vector(intent)
      recording.occurrences.map(_.effectClass) shouldBe Vector(UnitOfWorkEffectClass.External)
      recording.plan.segments shouldBe recording.plan.occurrences.map(UnitOfWorkPlanSegment.ExternalBoundary.apply)
    }

    "record typed Compile and Test outcomes as one external occurrence each without runtime admission" in {
      Given("Compile and Test programs with stubbed success, nonzero and incomplete process outcomes")
      val compile = UnitOfWorkOp.SbtExec(_sbt_target, SbtOperation.Compile)
      val test = UnitOfWorkOp.SbtExec(_sbt_target, SbtOperation.Test)
      val cases = Vector(
        ProcessExecutionTermination.Exited(0) -> SbtExecutionOutcome.Succeeded,
        ProcessExecutionTermination.Exited(7) -> SbtExecutionOutcome.Failed(7),
        ProcessExecutionTermination.TimedOut -> SbtExecutionOutcome.Incomplete(ProcessExecutionTermination.TimedOut)
      )
      val program: ExecUowM[Vector[SbtExecutionResult]] = for {
        compiled <- ConsequenceT.liftF(Free.liftF(compile))
        tested <- ConsequenceT.liftF(Free.liftF(test))
      } yield Vector(compiled, tested)

      When("the existing recorder traverses each program using typed StubProvider outcomes alone")
      val recordings = cases.map { case (termination, outcome) =>
        val process = _process_result(termination)
        val provider = new UnitOfWorkProgramStubProvider {
          def resultFor[A](ordinal: Int, operation: UnitOfWorkOp[A]): Consequence[A] = operation match {
            case UnitOfWorkOp.SbtExec(target, operation) =>
              Consequence.success(SbtExecutionResult(target, operation, process))
            case _ => Consequence.notImplemented("unexpected operation in SBT outcome recording")
          }
        }
        (UnitOfWorkProgramRecorder.record(program, provider), process, outcome)
      }

      Then("each result retains its supplied typed outcome and raw facts with exactly two semantic external boundaries")
      recordings.foreach { case (recording, process, outcome) =>
        recording.result.toOption shouldBe Some(Vector(
          SbtExecutionResult(_sbt_target, SbtOperation.Compile, process),
          SbtExecutionResult(_sbt_target, SbtOperation.Test, process)
        ))
        recording.result.toOption.map(_.map(_.outcome)) shouldBe Some(Vector(outcome, outcome))
        recording.occurrences.map(_.operation) shouldBe Vector(compile, test)
        recording.occurrences.map(_.ordinal) shouldBe Vector(0, 1)
        recording.occurrences.map(_.effectClass) shouldBe Vector.fill(2)(UnitOfWorkEffectClass.External)
        recording.occurrences.map(_.outcome) shouldBe Vector(
          Consequence.success(SbtExecutionResult(_sbt_target, SbtOperation.Compile, process)),
          Consequence.success(SbtExecutionResult(_sbt_target, SbtOperation.Test, process))
        )
        recording.plan.segments shouldBe recording.plan.occurrences.map(UnitOfWorkPlanSegment.ExternalBoundary.apply)
      }
    }

    "visit Test only when the supplied Compile outcome selects that branch" in {
      Given("a typed Compile result that determines whether Test belongs to the visited program")
      val compile = UnitOfWorkOp.SbtExec(_sbt_target, SbtOperation.Compile)
      val test = UnitOfWorkOp.SbtExec(_sbt_target, SbtOperation.Test)
      val program: ExecUowM[SbtExecutionOutcome] = for {
        compiled <- ConsequenceT.liftF(Free.liftF(compile))
        selected <- if (compiled.outcome == SbtExecutionOutcome.Succeeded)
          ConsequenceT.liftF(Free.liftF(test))
        else
          ConsequenceT.pure[[X] =>> org.goldenport.cncf.Program[UnitOfWorkOp, X], SbtExecutionResult](compiled)
      } yield selected.outcome

      When("success and nonzero Compile stub outcomes choose their respective branches")
      val recordings = Vector(0, 9).map { exitcode =>
        val provider = new UnitOfWorkProgramStubProvider {
          def resultFor[A](ordinal: Int, operation: UnitOfWorkOp[A]): Consequence[A] = operation match {
            case UnitOfWorkOp.SbtExec(target, operation) =>
              Consequence.success(SbtExecutionResult(target, operation,
                _process_result(ProcessExecutionTermination.Exited(exitcode))))
            case _ => Consequence.notImplemented("unexpected operation in SBT branch recording")
          }
        }
        UnitOfWorkProgramRecorder.record(program, provider)
      }

      Then("the successful build visits Test and the failed build retains only Compile without admission or lowering")
      recordings.map(_.result) shouldBe Vector(
        Consequence.success(SbtExecutionOutcome.Succeeded),
        Consequence.success(SbtExecutionOutcome.Failed(9))
      )
      recordings.map(_.occurrences.map(_.operation)) shouldBe Vector(Vector(compile, test), Vector(compile))
      recordings.foreach { recording =>
        recording.occurrences.map(_.ordinal) shouldBe recording.occurrences.indices.toVector
        recording.occurrences.map(_.effectClass) shouldBe Vector.fill(recording.occurrences.size)(UnitOfWorkEffectClass.External)
        recording.plan.segments shouldBe recording.plan.occurrences.map(UnitOfWorkPlanSegment.ExternalBoundary.apply)
      }
    }

    "propagate supplied Compile, Test or TestOnly failures and halt before any later operation" in {
      Given("a Compile then Test then TestOnly then local program and a structured failure for each typed SBT operation")
      val compile = UnitOfWorkOp.SbtExec(_sbt_target, SbtOperation.Compile)
      val test = UnitOfWorkOp.SbtExec(_sbt_target, SbtOperation.Test)
      val testonly = UnitOfWorkOp.SbtExec(_sbt_target, SbtOperation.TestOnly)
      val subsequent = UnitOfWorkOp.LocalDataDir("unvisited")
      val stubfailure = Consequence.serviceUnavailable[Nothing]("supplied typed SBT failure")
      val program: ExecUowM[Path] = for {
        _ <- ConsequenceT.liftF(Free.liftF(compile))
        _ <- ConsequenceT.liftF(Free.liftF(test))
        _ <- ConsequenceT.liftF(Free.liftF(testonly))
        path <- ConsequenceT.liftF(Free.liftF(subsequent))
      } yield path

      When("each operation is selected in turn as the typed StubProvider failure")
      val recordings = SbtOperation.values.toVector.map { failing =>
        val provider = new UnitOfWorkProgramStubProvider {
          def resultFor[A](ordinal: Int, operation: UnitOfWorkOp[A]): Consequence[A] = operation match {
            case UnitOfWorkOp.SbtExec(target, operation) =>
              if (operation == failing) stubfailure
              else Consequence.success(SbtExecutionResult(target, operation,
                _process_result(ProcessExecutionTermination.Exited(0))))
            case UnitOfWorkOp.LocalDataDir(_) => Consequence.success(Path.of("unvisited"))
            case _ => Consequence.notImplemented("unexpected operation in SBT failure recording")
          }
        }
        UnitOfWorkProgramRecorder.record(program, provider)
      }

      Then("the supplied failure is final and only visited typed external operations are recorded")
      recordings.map(_.result) shouldBe Vector.fill(3)(stubfailure)
      recordings.map(_.occurrences.map(_.operation)) shouldBe Vector(Vector(compile), Vector(compile, test), Vector(compile, test, testonly))
      recordings.foreach { recording =>
        recording.occurrences.last.outcome shouldBe stubfailure
        recording.occurrences.map(_.ordinal) shouldBe recording.occurrences.indices.toVector
        recording.occurrences.map(_.effectClass) shouldBe Vector.fill(recording.occurrences.size)(UnitOfWorkEffectClass.External)
        recording.plan.segments shouldBe recording.plan.occurrences.map(UnitOfWorkPlanSegment.ExternalBoundary.apply)
      }
    }

    "record ordered typed outcomes and align the plan with the visited operations" in {
      Given("a program containing a typed local path result and an authorization operation")
      val local = UnitOfWorkOp.LocalDataDir("component")
      val authorization = UnitOfWorkOp.Authorize(_authorization)
      val program: ExecUowM[String] = for {
        path <- ConsequenceT.liftF(Free.liftF(local))
        _ <- ConsequenceT.liftF(Free.liftF(authorization))
      } yield path.toString
      val provider = new UnitOfWorkProgramStubProvider {
        def resultFor[A](
          ordinal: Int,
          operation: UnitOfWorkOp[A]
        ): Consequence[A] = operation match {
          case UnitOfWorkOp.LocalDataDir("component") =>
            Consequence.success(Path.of("selected"))
          case UnitOfWorkOp.Authorize(_) =>
            Consequence.success(())
          case _ =>
            Consequence.notImplemented("unexpected operation in recording specification")
        }
      }

      When("the program is recorded using only its typed stub outcomes")
      val recording = UnitOfWorkProgramRecorder.record(program, provider)

      Then("the final result and ordered occurrences retain the supplied typed values")
      recording.result shouldBe Consequence.success("selected")
      recording.occurrences.map(_.operation) shouldBe Vector(local, authorization)
      recording.occurrences.map(_.ordinal) shouldBe Vector(0, 1)
      recording.occurrences.map(_.effectClass) shouldBe Vector(
        UnitOfWorkEffectClass.Local,
        UnitOfWorkEffectClass.Control
      )
      recording.occurrences.map(_.outcome) shouldBe Vector(
        Consequence.success(Path.of("selected")),
        Consequence.success(())
      )
      recording.plan shouldBe UnitOfWorkProgramPlanner.plan(
        Vector(local, authorization)
      )
    }

    "omit an unvisited branch selected by a supplied typed result" in {
      Given("a branch whose selected path depends on the first typed operation result")
      val local = UnitOfWorkOp.LocalDataDir("component")
      val selected = UnitOfWorkOp.Authorize(_authorization)
      val unvisited = UnitOfWorkOp.ContentValidateReferences(Vector.empty)
      val program: ExecUowM[String] = for {
        path <- ConsequenceT.liftF(Free.liftF(local))
        _ <- if (path == Path.of("selected"))
          ConsequenceT.liftF(Free.liftF(selected))
        else
          ConsequenceT.liftF(Free.liftF(unvisited))
      } yield path.toString
      val provider = new UnitOfWorkProgramStubProvider {
        def resultFor[A](
          ordinal: Int,
          operation: UnitOfWorkOp[A]
        ): Consequence[A] = operation match {
          case UnitOfWorkOp.LocalDataDir("component") =>
            Consequence.success(Path.of("selected"))
          case UnitOfWorkOp.Authorize(_) =>
            Consequence.success(())
          case UnitOfWorkOp.ContentValidateReferences(_) =>
            Consequence.success(())
          case _ =>
            Consequence.notImplemented("unexpected operation in branch specification")
        }
      }

      When("the deterministic recording traverses the selected branch")
      val recording = UnitOfWorkProgramRecorder.record(program, provider)

      Then("only the selected branch occurrence is retained")
      recording.result shouldBe Consequence.success("selected")
      recording.occurrences.map(_.operation) shouldBe Vector(local, selected)
      recording.occurrences.map(_.operation) should not contain unvisited
      recording.plan.occurrences.map(_.operation) shouldBe Vector(local, selected)
    }

    "propagate a supplied structured stub failure and halt subsequent traversal" in {
      Given("a sequential program whose second typed operation outcome is a structured failure")
      val first = UnitOfWorkOp.Authorize(_authorization)
      val failing = UnitOfWorkOp.ContentValidateReferences(Vector.empty)
      val subsequent = UnitOfWorkOp.Authorize(_authorization)
      val stubfailure: Consequence[Unit] = Consequence.notImplemented("supplied stub failure")
      val program: ExecUowM[Unit] = for {
        _ <- ConsequenceT.liftF(Free.liftF(first))
        _ <- ConsequenceT.liftF(Free.liftF(failing))
        _ <- ConsequenceT.liftF(Free.liftF(subsequent))
      } yield ()
      val provider = new UnitOfWorkProgramStubProvider {
        def resultFor[A](
          ordinal: Int,
          operation: UnitOfWorkOp[A]
        ): Consequence[A] = operation match {
          case UnitOfWorkOp.Authorize(_) =>
            Consequence.success(())
          case UnitOfWorkOp.ContentValidateReferences(_) =>
            stubfailure
          case _ =>
            Consequence.notImplemented("unexpected operation in supplied-failure specification")
        }
      }

      When("the program is recorded")
      val recording = UnitOfWorkProgramRecorder.record(program, provider)

      Then("the supplied failure is final and the later operation is not visited")
      recording.result shouldBe stubfailure
      recording.occurrences.map(_.operation) shouldBe Vector(first, failing)
      recording.occurrences.last.outcome shouldBe stubfailure
      recording.plan.occurrences.map(_.operation) shouldBe Vector(first, failing)
    }

    "replace one supplied outcome with the selected structured failure and halt" in {
      Given("a three-operation program and one selected ordinal failure injection")
      val first = UnitOfWorkOp.Authorize(_authorization)
      val injected = UnitOfWorkOp.ContentValidateReferences(Vector.empty)
      val subsequent = UnitOfWorkOp.Authorize(_authorization)
      val injection = UnitOfWorkFailureInjection(
        ordinal = 1,
        failure = Conclusion.operationInvalid("injected recording failure")
      )
      val program: ExecUowM[Unit] = for {
        _ <- ConsequenceT.liftF(Free.liftF(first))
        _ <- ConsequenceT.liftF(Free.liftF(injected))
        _ <- ConsequenceT.liftF(Free.liftF(subsequent))
      } yield ()
      val provider = new UnitOfWorkProgramStubProvider {
        def resultFor[A](
          ordinal: Int,
          operation: UnitOfWorkOp[A]
        ): Consequence[A] = operation match {
          case UnitOfWorkOp.Authorize(_) =>
            Consequence.success(())
          case UnitOfWorkOp.ContentValidateReferences(_) =>
            Consequence.success(())
          case _ =>
            Consequence.notImplemented("unexpected operation in injection specification")
        }
      }

      When("the selected ordinal is recorded with failure injection")
      val recording = UnitOfWorkProgramRecorder.record(
        program,
        provider,
        Some(injection)
      )

      Then("the injected failure replaces the supplied result and later operations are absent")
      recording.result shouldBe Consequence.Failure[Unit](injection.failure)
      recording.occurrences.map(_.operation) shouldBe Vector(first, injected)
      recording.occurrences.last.outcome shouldBe Consequence.Failure[Unit](
        injection.failure
      )
      recording.plan.occurrences.map(_.operation) shouldBe Vector(first, injected)
    }
  }

  private def _process_result(termination: ProcessExecutionTermination): ProcessExecutionResult =
    ProcessExecutionResult(termination,
      ProcessExecutionCapture(Vector.empty, 0L, truncated = false),
      ProcessExecutionCapture(Vector.empty, 0L, truncated = false),
      Vector.empty, 1L, "recorded-build")
}
