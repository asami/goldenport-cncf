package org.goldenport.cncf.unitofwork

import java.nio.file.Path
import cats.free.Free
import cats.syntax.all.*
import org.goldenport.{Consequence, ConsequenceT, Conclusion}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * Executable specification for deterministic UnitOfWork program recording.
 *
 * @since   Sep.  8, 2026
 * @version Sep.  8, 2026
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

  "UnitOfWorkProgramRecorder" should {
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
}
