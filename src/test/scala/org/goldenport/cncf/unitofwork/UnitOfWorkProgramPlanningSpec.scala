package org.goldenport.cncf.unitofwork

import org.scalacheck.{Gen, Prop, Test}
import org.goldenport.cncf.processexecution.{GitOperation, GitTarget, SbtOperation, SbtTarget}
import org.goldenport.cncf.workspace.{FileRepresentation, FileTarget, FileWriteMode, WorkspaceFilePath, WorkspaceId}
import org.goldenport.cncf.workflow.{CompletionContract, ContextBundle, ContextContract, ContextSnapshot, EvidenceContract, ProviderExecutionRequest, StateMachineOperationIdentity, StateMachineProvidedApiRequest, StateMachineRequiredOperation, StateMachineRequiredOperationIdentity, StateMachineRequiredOperationMetadata, StateMachineRunIdentity}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * Executable specification for metadata-independent UnitOfWork planning.
 *
 * @since   Sep.  8, 2026
 *  version Sep. 28, 2026
 * @version Oct. 24, 2026
 * @author  ASAMI, Tomoharu
 */
final class UnitOfWorkProgramPlanningSpec
    extends AnyWordSpec
    with Matchers
    with GivenWhenThen {
  private val _authorization = UnitOfWorkAuthorization(
    resourceFamily = "component",
    accessKind = "read"
  )
  private val _control = UnitOfWorkOp.Authorize(_authorization)
  private val _local = UnitOfWorkOp.LocalDataDir("component")
  private val _external = UnitOfWorkOp.HttpGet("/external")
  private val _sbt_target = SbtTarget(WorkspaceId.parseC("planning").toOption.get)
  private val _sbt_compile = UnitOfWorkOp.SbtExec(_sbt_target, SbtOperation.Compile)
  private val _sbt_test = UnitOfWorkOp.SbtExec(_sbt_target, SbtOperation.Test)
  private val _sbt_fix_check = UnitOfWorkOp.SbtExec(_sbt_target, SbtOperation.FixCheck)
  private val _context = ContextBundle(
    "planning context",
    Vector.empty,
    Vector.empty,
    ContextSnapshot("1")
  )
  private val _required_operation = StateMachineRequiredOperation(
    StateMachineRequiredOperationIdentity("planning.required"),
    "planning.action",
    StateMachineOperationIdentity("planning.service", "required"),
    None,
    None,
    StateMachineRequiredOperationMetadata(
      ContextContract("planning.context", Vector.empty, Vector.empty),
      CompletionContract("planning.completion", Vector.empty),
      EvidenceContract("planning.evidence", Vector.empty),
      Vector.empty
    )
  )
  private val _provider_request = ProviderExecutionRequest(
    StateMachineRunIdentity("planning.run"),
    _required_operation,
    None,
    _context
  )
  private val _provider_operation = UnitOfWorkOp.StateMachineProviderExecute(
    _provider_request
  )
  private val _provided_api_request = StateMachineProvidedApiRequest(
    "planning.workflow",
    "1",
    StateMachineRunIdentity("planning.run"),
    StateMachineOperationIdentity("planning.service", "provided"),
    None,
    _context
  )
  private val _provided_api_operation = UnitOfWorkOp.StateMachineProvidedApiExecute(
    _provided_api_request
  )

  "UnitOfWorkProgramPlanner" should {
    "retain repeated FixCheck and attempt intents as individual External boundaries" in {
      Given("fixed check and attempt intents between local operations, without runtime services")
      val attempt = UnitOfWorkOp.SbtExecAttempt(_sbt_target, SbtOperation.FixCheck)
      val operations = Vector(_local, _sbt_fix_check, _sbt_fix_check, attempt, _local)
      When("the planner interprets the intent sequence without lowering it to ProcessExec")
      val plan = UnitOfWorkProgramPlanner.plan(operations)
      Then("each typed check keeps its own ordinal and External segment")
      plan.occurrences.map(_.operation) shouldBe operations
      plan.occurrences.map(_.ordinal) shouldBe operations.indices.toVector
      plan.occurrences.map(_.effectClass) shouldBe Vector(UnitOfWorkEffectClass.Local) ++
        Vector.fill(3)(UnitOfWorkEffectClass.External) ++ Vector(UnitOfWorkEffectClass.Local)
      plan.segments shouldBe Vector(UnitOfWorkPlanSegment.LocalAtomic(plan.occurrences.take(1))) ++
        plan.occurrences.slice(1, 4).map(UnitOfWorkPlanSegment.ExternalBoundary.apply) ++
        Vector(UnitOfWorkPlanSegment.LocalAtomic(plan.occurrences.drop(4)))
    }

    "retain directory preparation and repeated byte operations as individual External boundaries" in {
      Given("a directory intent and repeated equal binary intents between local operations")
      val target = FileTarget(WorkspaceId.parseC("planning").toOption.get)
      val path = WorkspaceFilePath.parseC("private archive/exact.gz").toOption.get
      val bytes = Vector[Byte](0, -1, -128, 127)
      val prepare = UnitOfWorkOp.FilePrepareDirectory(target, path)
      val write = UnitOfWorkOp.FileWriteBytes(target, path, bytes, FileRepresentation.Gzip)
      val read = UnitOfWorkOp.FileReadBytes(target, path, FileRepresentation.Gzip)
      val operations = Vector(_local, prepare, write, write, read, read, _local)
      When("the metadata-independent planner classifies every occurrence without interpretation")
      val plan = UnitOfWorkProgramPlanner.plan(operations)
      Then("each new occurrence stays External in order, outside either local atomic segment")
      plan.occurrences.map(_.operation) shouldBe operations
      plan.occurrences.map(_.ordinal) shouldBe operations.indices.toVector
      plan.occurrences.map(_.effectClass) shouldBe
        Vector(UnitOfWorkEffectClass.Local) ++ Vector.fill(5)(UnitOfWorkEffectClass.External) ++ Vector(UnitOfWorkEffectClass.Local)
      plan.segments shouldBe Vector(UnitOfWorkPlanSegment.LocalAtomic(plan.occurrences.take(1))) ++
        plan.occurrences.slice(1, 6).map(UnitOfWorkPlanSegment.ExternalBoundary.apply) ++
        Vector(UnitOfWorkPlanSegment.LocalAtomic(plan.occurrences.drop(6)))
      plan.occurrences.mkString should not include bytes.toString
      plan.occurrences.mkString should not include "private archive"
    }

    "retain File reads and repeated writes in order beside local, control, Git and SBT intents" in {
      Given("logical File read and write intents mixed with existing control, local, Git and SBT operations")
      val id = WorkspaceId.parseC("planning").toOption.get
      val target = FileTarget(id)
      val path = WorkspaceFilePath.parseC("literal\n\\name").toOption.get
      val read = UnitOfWorkOp.FileReadText(target, path)
      val write = UnitOfWorkOp.FileWriteText(target, path, "replacement\n", FileWriteMode.UpdateExisting("expected\n"))
      val git = UnitOfWorkOp.GitExec(GitTarget(id), GitOperation.Status)
      val operations = Vector(_local, _control, read, write, write, _local, git, _sbt_test, read, _control)

      When("the original mixed sequence is planned without interpreting or lowering File intents")
      val plan = UnitOfWorkProgramPlanner.plan(operations)

      Then("every File occurrence is External at its own ordinal and equal writes remain distinct boundaries")
      plan.occurrences.map(_.operation) shouldBe operations
      plan.occurrences.map(_.ordinal) shouldBe operations.indices.toVector
      plan.occurrences.map(_.effectClass) shouldBe Vector(
        UnitOfWorkEffectClass.Local, UnitOfWorkEffectClass.Control,
        UnitOfWorkEffectClass.External, UnitOfWorkEffectClass.External, UnitOfWorkEffectClass.External,
        UnitOfWorkEffectClass.Local, UnitOfWorkEffectClass.External, UnitOfWorkEffectClass.External,
        UnitOfWorkEffectClass.External, UnitOfWorkEffectClass.Control)
      plan.segments shouldBe Vector(
        UnitOfWorkPlanSegment.LocalAtomic(plan.occurrences.take(2)),
        UnitOfWorkPlanSegment.ExternalBoundary(plan.occurrences(2)),
        UnitOfWorkPlanSegment.ExternalBoundary(plan.occurrences(3)),
        UnitOfWorkPlanSegment.ExternalBoundary(plan.occurrences(4)),
        UnitOfWorkPlanSegment.LocalAtomic(plan.occurrences.slice(5, 6)),
        UnitOfWorkPlanSegment.ExternalBoundary(plan.occurrences(6)),
        UnitOfWorkPlanSegment.ExternalBoundary(plan.occurrences(7)),
        UnitOfWorkPlanSegment.ExternalBoundary(plan.occurrences(8)),
        UnitOfWorkPlanSegment.LocalAtomic(plan.occurrences.drop(9)))
    }

    "retain one declarative Git intent as one External boundary without runtime services" in {
      Given("a logical Git target and Status intent without process or workspace admission")
      val intent = UnitOfWorkOp.GitExec(GitTarget(WorkspaceId.parseC("planning").toOption.get), GitOperation.Status)
      When("the intent is planned between two local operations")
      val plan = UnitOfWorkProgramPlanner.plan(Vector(_local, intent, _local))
      Then("the original intent occupies exactly one external segment at its own ordinal")
      plan.occurrences.map(_.operation) shouldBe Vector(_local, intent, _local)
      plan.occurrences.map(_.effectClass) shouldBe Vector(UnitOfWorkEffectClass.Local, UnitOfWorkEffectClass.External, UnitOfWorkEffectClass.Local)
      plan.segments(1) shouldBe UnitOfWorkPlanSegment.ExternalBoundary(plan.occurrences(1))
    }

    "retain Compile and Test as single external occurrences between local runs including equal repeated intents" in {
      Given("typed Compile and Test intents mixed with local, control and HTTP operations")
      val operations = Vector(_local, _control, _sbt_compile, _local, _sbt_test, _sbt_test, _external, _control, _local)

      When("the mixed sequence is planned without lowering its typed SBT intents")
      val plan = UnitOfWorkProgramPlanner.plan(operations)

      Then("each typed SBT occurrence retains its order and distinct ordinal in an isolated external segment")
      plan.occurrences.map(_.operation) shouldBe operations
      plan.occurrences.map(_.ordinal) shouldBe operations.indices.toVector
      plan.occurrences.map(_.effectClass) shouldBe Vector(
        UnitOfWorkEffectClass.Local, UnitOfWorkEffectClass.Control, UnitOfWorkEffectClass.External,
        UnitOfWorkEffectClass.Local, UnitOfWorkEffectClass.External, UnitOfWorkEffectClass.External,
        UnitOfWorkEffectClass.External, UnitOfWorkEffectClass.Control, UnitOfWorkEffectClass.Local
      )
      plan.segments shouldBe Vector(
        UnitOfWorkPlanSegment.LocalAtomic(plan.occurrences.take(2)),
        UnitOfWorkPlanSegment.ExternalBoundary(plan.occurrences(2)),
        UnitOfWorkPlanSegment.LocalAtomic(plan.occurrences.slice(3, 4)),
        UnitOfWorkPlanSegment.ExternalBoundary(plan.occurrences(4)),
        UnitOfWorkPlanSegment.ExternalBoundary(plan.occurrences(5)),
        UnitOfWorkPlanSegment.ExternalBoundary(plan.occurrences(6)),
        UnitOfWorkPlanSegment.LocalAtomic(plan.occurrences.drop(7))
      )
    }

    "classify representative control, local, and external operations" in {
      Given("one representative operation from each base effect category")
      val operations = Vector(_control, _local, _external)

      When("the operation classifier classifies them")
      val classes = operations.map(UnitOfWorkOperationClassifier.classify)

      Then("the metadata-independent base classes are Control, Local, and External")
      classes shouldBe Vector(
        UnitOfWorkEffectClass.Control,
        UnitOfWorkEffectClass.Local,
        UnitOfWorkEffectClass.External
      )
    }

    "isolate external occurrences between contiguous local atomic segments" in {
      Given("control and local operations on both sides of one external operation")
      val operations = Vector(_control, _local, _external, _local, _control)

      When("the ordered operations are planned")
      val plan = UnitOfWorkProgramPlanner.plan(operations)

      Then("only contiguous control/local runs coalesce and the external boundary stays isolated")
      plan.segments shouldBe Vector(
        UnitOfWorkPlanSegment.LocalAtomic(
          Vector(
            UnitOfWorkOperationOccurrence(
              _control,
              0,
              UnitOfWorkEffectClass.Control
            ),
            UnitOfWorkOperationOccurrence(_local, 1, UnitOfWorkEffectClass.Local)
          )
        ),
        UnitOfWorkPlanSegment.ExternalBoundary(
          UnitOfWorkOperationOccurrence(_external, 2, UnitOfWorkEffectClass.External)
        ),
        UnitOfWorkPlanSegment.LocalAtomic(
          Vector(
            UnitOfWorkOperationOccurrence(_local, 3, UnitOfWorkEffectClass.Local),
            UnitOfWorkOperationOccurrence(
              _control,
              4,
              UnitOfWorkEffectClass.Control
            )
          )
        )
      )
    }

    "retain typed state-machine operations in mixed local and external segments" in {
      Given("well-formed Provider and Provided API requests in a mixed operation sequence")
      val operations = Vector(
        _local,
        _provider_operation,
        _provided_api_operation,
        _external,
        _provider_operation,
        _local
      )

      When("the mixed operation sequence is planned")
      val plan = UnitOfWorkProgramPlanner.plan(operations)

      Then("state-machine dispatch remains Local and the external occurrence remains an isolated boundary")
      plan.occurrences.map(_.operation) shouldBe operations
      plan.occurrences.map(_.ordinal) shouldBe Vector(0, 1, 2, 3, 4, 5)
      plan.occurrences.map(_.effectClass) shouldBe Vector(
        UnitOfWorkEffectClass.Local,
        UnitOfWorkEffectClass.Local,
        UnitOfWorkEffectClass.Local,
        UnitOfWorkEffectClass.External,
        UnitOfWorkEffectClass.Local,
        UnitOfWorkEffectClass.Local
      )
      plan.segments shouldBe Vector(
        UnitOfWorkPlanSegment.LocalAtomic(plan.occurrences.take(3)),
        UnitOfWorkPlanSegment.ExternalBoundary(plan.occurrences(3)),
        UnitOfWorkPlanSegment.LocalAtomic(plan.occurrences.drop(4))
      )
    }

    "retain repeated equal operations with their input order and ordinals" in {
      Given("two equal occurrences of one local operation")
      val repeated = UnitOfWorkOp.LocalDataDir("same")
      val operations = Vector(repeated, repeated)

      When("the repeated operations are planned")
      val plan = UnitOfWorkProgramPlanner.plan(operations)

      Then("both occurrences remain ordered and receive distinct zero-based ordinals")
      plan.occurrences.map(_.operation) shouldBe operations
      plan.occurrences.map(_.ordinal) shouldBe Vector(0, 1)
      plan.segments shouldBe Vector(
        UnitOfWorkPlanSegment.LocalAtomic(plan.occurrences)
      )
    }

    "produce no occurrences or segments for empty input" in {
      Given("an empty ordered operation sequence")
      val operations = Vector.empty[UnitOfWorkOp[?]]

      When("the empty sequence is planned")
      val plan = UnitOfWorkProgramPlanner.plan(operations)

      Then("the immutable plan is empty")
      plan.occurrences shouldBe empty
      plan.segments shouldBe empty
    }

    "preserve arbitrary safe category order without merging across external occurrences" in {
      Given("generated sequences made from bounded control, local, HTTP, typed SBT and state-machine representatives")
      val operations = Gen.listOf(
        Gen.oneOf(
          _control,
          _local,
          _external,
          _sbt_compile,
          _sbt_test,
          _sbt_fix_check,
          _provider_operation,
          _provided_api_operation
        )
      ).map(_.toVector)

      When("each generated sequence is planned")
      val property = Prop.forAll(operations) { input =>
        val plan = UnitOfWorkProgramPlanner.plan(input)
        val planned = plan.segments.flatMap {
          case UnitOfWorkPlanSegment.LocalAtomic(occurrences) => occurrences
          case UnitOfWorkPlanSegment.ExternalBoundary(occurrence) => Vector(occurrence)
        }
        val externalordinals = planned.collect {
          case occurrence if occurrence.effectClass == UnitOfWorkEffectClass.External =>
            occurrence.ordinal
        }
        val localsegmentordinals = plan.segments.collect {
          case UnitOfWorkPlanSegment.LocalAtomic(occurrences) =>
            occurrences.map(_.ordinal)
        }
        planned == plan.occurrences &&
        planned.map(_.ordinal) == input.indices.toVector &&
        externalordinals.forall { ordinal =>
          !localsegmentordinals.exists(ordinals =>
            ordinals.minOption.exists(_ < ordinal) && ordinals.maxOption.exists(_ > ordinal)
          )
        }
      }

      Then("the bounded ScalaCheck run proves order preservation and external separation")
      val result = Test.check(
        Test.Parameters.default.withMinSuccessfulTests(50),
        property
      )
      result.passed shouldBe true
    }
  }
}
