package org.goldenport.cncf.action

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}
import java.util.Comparator
import scala.jdk.CollectionConverters.*
import scala.util.Using
import cats.syntax.all.*
import io.circe.Json
import org.goldenport.Consequence
import org.goldenport.cncf.cli.CncfRuntime
import org.goldenport.cncf.component.{Component, ComponentId, ComponentInstanceId, ComponentOrigin}
import org.goldenport.cncf.config.CncfConfigurationParameterCatalog
import org.goldenport.cncf.context.{ExecutionContext, ScopeKind}
import org.goldenport.cncf.effect.ExternalEffectKind
import org.goldenport.cncf.processexecution.{RuntimeProcessExecutionPlan, RuntimeProcessExecutionPlanCodec, WorkspaceRuntimeDeclaration}
import org.goldenport.cncf.subsystem.Subsystem
import org.goldenport.cncf.unitofwork.{UnitOfWork, UnitOfWorkEffectClass, UnitOfWorkInterpreter, UnitOfWorkOp, UnitOfWorkProgramRecorder, UnitOfWorkProgramStubProvider}
import org.goldenport.cncf.workspace.*
import org.goldenport.cncf.resource.{ResourceReadLimits, ResourceTreeSelection, ResourceTreeQueryLimits}
import org.goldenport.configuration.ConfigurationValue
import org.goldenport.protocol.Protocol
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Oct. 15, 2026
 *  version Oct. 21, 2026
 *  version Oct. 29, 2026
 * @version Nov.  9, 2026
 * @author  ASAMI, Tomoharu
 */
final class FileExecutionDslSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import FileExecutionDslSpec.Fixture

  private val _owner = ComponentId("org.goldenport.spec.FileDslOwner")
  private val _id = WorkspaceId.parseC("project").toOption.get
  private val _target = FileTarget(_id)
  private val _path = WorkspaceFilePath.parseC("message\n\\.txt").toOption.get

  "Purpose-specific File budgets" should {
    "acquire a complete large artifact while both ordinary limits remain in force" in {
      Given("ReadOnly workspaces with 1 MiB and 16 MiB generic limits and one 37.5 MiB artifact")
      val small = _fixture(permission = WorkspacePermission.ReadOnly, cap = 1024 * 1024)
      val large = _fixture(permission = WorkspacePermission.ReadOnly, cap = 16 * 1024 * 1024)
      try {
        val payload = Array.fill[Byte](375 * 1024 * 1024 / 10)(42)
        Files.write(small.checkout.resolve(_path.value), payload)
        Files.write(large.checkout.resolve(_path.value), payload)
        val behavior = new FileDslBehavior(Behavior.Core(small.context, None, None))
        val policy = FileReadPolicy(ResourceReadLimits(64 * 1024 * 1024))
        val first = new UnitOfWorkInterpreter(new UnitOfWork(small.context))
        val second = new UnitOfWorkInterpreter(new UnitOfWork(large.context))
        When("both owning interpreters execute ordinary reads and the latter executes a selected read")
        val ordinary = Vector(first, second).map(_.run(behavior.readBytes(_target, _path, FileRepresentation.Identity)))
        val selected = second.run(behavior.readBytes(_target, _path, FileRepresentation.Identity, policy))
        val denied = second.run(behavior.write(_target, _path, "new", FileWriteMode.CreateNew,
          FileWritePolicy(ResourceReadLimits(64 * 1024 * 1024))))
        Then("both ordinary reads fail, the selected payload is complete and no write grant is inferred")
        ordinary.foreach(_.isFaillure shouldBe true)
        selected.toOption.get.bytes.get.toArray.toVector shouldBe payload.toVector
        selected.toOption.get.effect.kind shouldBe ExternalEffectKind.FileRead
        denied.isFaillure shouldBe true
      } finally { _close(large); _close(small) }
    }

    "interpret all explicit budgeted operations and keep read and write capacity independent" in {
      Given("a Manage workspace with four-byte generic limits and separately selected budgets")
      val fixture = _fixture(cap = 4)
      try {
        val behavior = new FileDslBehavior(Behavior.Core(fixture.context, None, None))
        val interpreter = new UnitOfWorkInterpreter(new UnitOfWork(fixture.context))
        val read = FileReadPolicy(ResourceReadLimits(64))
        val write = FileWritePolicy(ResourceReadLimits(64))
        val textpath = WorkspaceFilePath.parseC("text").toOption.get
        val gzipath = WorkspaceFilePath.parseC("bytes.gz").toOption.get
        val genericpath = WorkspaceFilePath.parseC("generic").toOption.get
        val bytes = "expanded payload".getBytes(StandardCharsets.UTF_8).toVector
        Files.writeString(fixture.checkout.resolve(textpath.value), "long text", StandardCharsets.UTF_8)
        When("the actual UoW reads with a selected policy and executes independently chosen writes")
        val text = interpreter.run(behavior.read(_target, textpath, read))
        val denied = interpreter.run(behavior.write(_target, genericpath, "long text", FileWriteMode.CreateNew))
        val written = interpreter.run(behavior.write(_target, genericpath, "long text", FileWriteMode.CreateNew, write))
        val compressed = interpreter.run(behavior.writeBytes(_target, gzipath, bytes, FileRepresentation.Gzip, write))
        val expanded = interpreter.run(behavior.readBytes(_target, gzipath, FileRepresentation.Gzip, read))
        Then("only explicitly selected write capacity permits larger output and all typed results carry actual effects")
        text.toOption.get.text shouldBe Some("long text")
        text.toOption.get.effect.kind shouldBe ExternalEffectKind.FileRead
        denied.isFaillure shouldBe true
        written.toOption.get.outcome shouldBe FileWriteOutcome.Created
        written.toOption.get.effect.kind shouldBe ExternalEffectKind.FileWrite
        compressed.toOption.get.outcome shouldBe FileWriteOutcome.Created
        compressed.toOption.get.effect.kind shouldBe ExternalEffectKind.FileWrite
        expanded.toOption.get.bytes shouldBe Some(bytes)
        expanded.toOption.get.effect.kind shouldBe ExternalEffectKind.FileRead
        Files.readString(fixture.checkout.resolve(genericpath.value), StandardCharsets.UTF_8) shouldBe "long text"
      } finally _close(fixture)
    }

    "record budgeted File operations as distinct external occurrences" in {
      Given("an unconfigured behavior and explicit independent policies")
      val behavior = new FileDslBehavior(Behavior.Core(ExecutionContext.create(), None, None))
      val readpolicy = FileReadPolicy(ResourceReadLimits(8))
      val writepolicy = FileWritePolicy(ResourceReadLimits(4))
      val provider = new UnitOfWorkProgramStubProvider {
        def resultFor[A](ordinal: Int, operation: UnitOfWorkOp[A]): Consequence[A] = operation match {
          case UnitOfWorkOp.FileReadTextWithPolicy(_, _, _) => Consequence.success(FileReadTextResult(_target, _path, None))
          case UnitOfWorkOp.FileWriteTextWithPolicy(_, _, _, _, _) => Consequence.success(FileWriteTextResult(_target, _path, FileWriteOutcome.Created))
          case _ => Consequence.notImplemented("unexpected File operation")
        }
      }
      When("the two intents are recorded")
      val recording = UnitOfWorkProgramRecorder.record(for {
        read <- behavior.read(_target, _path, readpolicy)
        write <- behavior.write(_target, _path, "text", FileWriteMode.CreateNew, writepolicy)
      } yield (read, write), provider)
      Then("the policies survive and each operation remains external")
      recording.occurrences.map(_.operation) shouldBe Vector(
        UnitOfWorkOp.FileReadTextWithPolicy(_target, _path, readpolicy),
        UnitOfWorkOp.FileWriteTextWithPolicy(_target, _path, "text", FileWriteMode.CreateNew, writepolicy))
      recording.occurrences.map(_.effectClass) shouldBe Vector.fill(2)(UnitOfWorkEffectClass.External)
    }
  }

  "Typed File DSL under the owning UoW" should {
    "read selected snapshot bytes from the actual interpreting UoW rather than the construction workspace" in {
      Given("a File program built under A and a read-only interpreting workspace B")
      val contexta = _fixture()
      val contextb = _fixture(permission = WorkspacePermission.ReadOnly, cap = 32)
      try {
        Files.writeString(contexta.checkout.resolve("source.scala"), "construction")
        Files.writeString(contextb.checkout.resolve("source.scala"), "owning\nlast")
        val rule = ResourceTreeSelection(Vector("."), suffixes = Vector(".scala"))
        val behavior = new FileDslBehavior(Behavior.Core(contexta.context, None, None))
        val program = behavior.snapshot(_target, rule)
        When("B's UoW interprets the already built typed snapshot intent")
        val result = new UnitOfWorkInterpreter(new UnitOfWork(contextb.context)).run(program).toOption.get
        Then("the result associates B's complete immutable bytes with the exact target and rule")
        result.entries.map(_.relativePath) shouldBe Vector("source.scala")
        result.entries.head.bytes shouldBe "owning\nlast".getBytes(StandardCharsets.UTF_8).toVector
        result.target shouldBe _target
        result.selection shouldBe rule
        result.limits.maxEntryBytes shouldBe 32L
        result.effect.kind shouldBe ExternalEffectKind.FileRead
        val crossed = behavior.snapshot(FileTarget(WorkspaceId.parseC("unbound").toOption.get), rule)
        new UnitOfWorkInterpreter(new UnitOfWork(contextb.context)).run(crossed).isFaillure shouldBe true
      } finally { _close(contextb); _close(contexta) }
    }

    "retain repeated snapshot reads as payload-safe external occurrences without construction effects" in {
      Given("an unconfigured construction context and an explicitly supplied snapshot stub")
      val context = ExecutionContext.create()
      val behavior = new FileDslBehavior(Behavior.Core(context, None, None))
      val rule = ResourceTreeSelection(Vector("private-source"))
      val result = FileReadSnapshotResult(_target, rule, ResourceTreeQueryLimits.default, Vector.empty)
      val provider = new UnitOfWorkProgramStubProvider {
        def resultFor[A](ordinal: Int, operation: UnitOfWorkOp[A]): Consequence[A] = operation match {
          case UnitOfWorkOp.FileReadSnapshot(_, _, _) => Consequence.success(result)
          case _ => Consequence.notImplemented("unexpected snapshot operation")
        }
      }
      When("the recorder consumes two equal typed reads without a workspace service")
      val recording = UnitOfWorkProgramRecorder.record(for {
        first <- behavior.snapshot(_target, rule)
        second <- behavior.snapshot(_target, rule)
      } yield (first, second), provider)
      Then("both original external boundaries survive and generic displays omit selected source paths")
      recording.result.toOption shouldBe Some((result, result))
      recording.occurrences.map(_.effectClass) shouldBe Vector.fill(2)(UnitOfWorkEffectClass.External)
      recording.occurrences.map(_.ordinal) shouldBe Vector(0, 1)
      recording.occurrences.mkString should not include "private-source"
      result.toString should not include "private-source"
    }

    "prepare and store exact-name gzip using B's root and byte policy for a program constructed under A" in {
      Given("independent normally assembled A and B workspaces with different current limits")
      val contexta = _fixture(cap = 128)
      val contextb = _fixture(cap = 32)
      val behavior = new FileDslBehavior(Behavior.Core(contexta.context, None, None))
      val directory = WorkspaceFilePath.parseC("archive/nested").toOption.get
      val path = WorkspaceFilePath.parseC("archive/nested/selected.gz").toOption.get
      val bytes = Vector[Byte](0, -1, -128, 42)
      val program = for {
        prepared <- behavior.prepare(_target, directory)
        written <- behavior.writeBytes(_target, path, bytes, FileRepresentation.Gzip)
        read <- behavior.readBytes(_target, path, FileRepresentation.Gzip)
      } yield (prepared, written, read)
      val beforeinterpretation = _names(contexta.checkout) -> _names(contextb.checkout)
      val oversized = behavior.writeBytes(_target, WorkspaceFilePath.parseC("too-large").toOption.get,
        Vector.fill[Byte](33)(1), FileRepresentation.Identity)
      try {
        When("B's actual UoW interprets the already constructed directory and byte programs")
        val interpreter = new UnitOfWorkInterpreter(new UnitOfWork(contextb.context))
        val result = interpreter.run(program)
        val rejected = interpreter.run(oversized)
        Then("construction has no IO, B alone changes and B's smaller current cap rejects the oversized input")
        beforeinterpretation shouldBe (Set.empty[String] -> Set.empty[String])
        val (prepared, written, read) = result.toOption.get
        prepared.outcome shouldBe FilePrepareDirectoryOutcome.Created
        written.outcome shouldBe FileWriteOutcome.Created
        read.bytes shouldBe Some(bytes)
        read.representation shouldBe FileRepresentation.Gzip
        _names(contexta.checkout) shouldBe empty
        _names(contextb.checkout) shouldBe Set("archive")
        _names(contextb.checkout.resolve(directory.value)) shouldBe Set("selected.gz")
        rejected.isFaillure shouldBe true
        rejected.toString should not include contextb.checkout.toString
      } finally {
        _close(contextb)
        _close(contexta)
      }
    }

    "record directory and repeated byte intents as separate payload-safe External occurrences without services" in {
      Given("an unconfigured construction context and typed stub results for every new File intent")
      val context = ExecutionContext.create()
      val behavior = new FileDslBehavior(Behavior.Core(context, None, None))
      val bytes = "private binary payload".getBytes(StandardCharsets.UTF_8).toVector
      val directoryresult = FilePrepareDirectoryResult(_target, _path, FilePrepareDirectoryOutcome.Created)
      val writeresult = FileWriteBytesResult(_target, _path, FileRepresentation.Gzip, FileWriteOutcome.Created)
      val readresult = FileReadBytesResult(_target, _path, FileRepresentation.Gzip, Some(bytes))
      val provider = new UnitOfWorkProgramStubProvider {
        def resultFor[A](ordinal: Int, operation: UnitOfWorkOp[A]): Consequence[A] = operation match {
          case UnitOfWorkOp.FilePrepareDirectory(_, _) => Consequence.success(directoryresult)
          case UnitOfWorkOp.FileWriteBytes(_, _, _, _) => Consequence.success(writeresult)
          case UnitOfWorkOp.FileReadBytes(_, _, _) => Consequence.success(readresult)
          case _ => Consequence.notImplemented("unexpected File recording operation")
        }
      }
      When("construction and recording consume directory, equal repeated writes and equal repeated reads")
      val program = for {
        prepared <- behavior.prepare(_target, _path)
        first <- behavior.writeBytes(_target, _path, bytes, FileRepresentation.Gzip)
        second <- behavior.writeBytes(_target, _path, bytes, FileRepresentation.Gzip)
        read <- behavior.readBytes(_target, _path, FileRepresentation.Gzip)
        again <- behavior.readBytes(_target, _path, FileRepresentation.Gzip)
      } yield (prepared, first, second, read, again)
      val recording = UnitOfWorkProgramRecorder.record(program, provider)
      Then("five original occurrences survive separately and no authority or private payload is captured in display")
      recording.result.toOption shouldBe Some((directoryresult, writeresult, writeresult, readresult, readresult))
      recording.occurrences.map(_.operation) shouldBe Vector(
        UnitOfWorkOp.FilePrepareDirectory(_target, _path),
        UnitOfWorkOp.FileWriteBytes(_target, _path, bytes, FileRepresentation.Gzip),
        UnitOfWorkOp.FileWriteBytes(_target, _path, bytes, FileRepresentation.Gzip),
        UnitOfWorkOp.FileReadBytes(_target, _path, FileRepresentation.Gzip),
        UnitOfWorkOp.FileReadBytes(_target, _path, FileRepresentation.Gzip))
      recording.occurrences.map(_.ordinal) shouldBe Vector(0, 1, 2, 3, 4)
      recording.occurrences.map(_.effectClass) shouldBe Vector.fill(5)(UnitOfWorkEffectClass.External)
      context.workspaceExecutionAdmissionOption shouldBe None
      context.resourceReadLimitsC.isFaillure shouldBe true
      Vector(recording.occurrences.mkString, readresult.toString, writeresult.toString, directoryresult.toString).foreach { display =>
        display should not include "private binary payload"
        display should not include bytes.toString
        display should not include _path.value
      }
    }

    "use B's actual ReadOnly grant and actual Component ancestry for every new File intent" in {
      Given("a program owner A and ReadOnly B with one Identity leaf")
      val contexta = _fixture()
      val contextb = _fixture(permission = WorkspacePermission.ReadOnly)
      Files.write(contextb.checkout.resolve(_path.value), Array[Byte](0, -1))
      val behavior = new FileDslBehavior(Behavior.Core(contexta.context, None, None))
      val prepare = behavior.prepare(_target, WorkspaceFilePath.parseC("new/nested").toOption.get)
      val write = behavior.writeBytes(_target, WorkspaceFilePath.parseC("new.gz").toOption.get, Vector.empty, FileRepresentation.Gzip)
      val read = behavior.readBytes(_target, _path, FileRepresentation.Identity)
      val scope = contextb.subsystem.globalRuntimeContext.createChildScope(ScopeKind.Component, _owner.name)
      val impostor = ExecutionContext.create().withScope(scope)
      try {
        When("B and a same-labelled generic Component scope interpret A's programs")
        val interpreter = new UnitOfWorkInterpreter(new UnitOfWork(contextb.context))
        val denied = Vector(interpreter.run(prepare).isFaillure, interpreter.run(write).isFaillure)
        val allowed = interpreter.run(read)
        val rejected = new UnitOfWorkInterpreter(new UnitOfWork(impostor)).run(read)
        Then("B permits only reads and a label cannot substitute for actual Component ancestry")
        denied shouldBe Vector(true, true)
        allowed.toOption.get.bytes shouldBe Some(Vector[Byte](0, -1))
        rejected.isFaillure shouldBe true
        _names(contexta.checkout) shouldBe empty
        _names(contextb.checkout) shouldBe Set(_path.value)
      } finally {
        _close(contextb)
        _close(contexta)
      }
    }

    "compose read, create, read, update and read through a normal workspace-only V3 runtime" in {
      Given("normal embedding assembly with a V3 workspace/grant declaration, a small runtime cap and no process definitions")
      val fixture = _fixture()
      val behavior = new FileDslBehavior(Behavior.Core(fixture.context, None, None))
      val initial = "日本🚀\n"
      val replacement = "更新\n"
      val program = for {
        absent <- behavior.read(_target, _path)
        created <- behavior.write(_target, _path, initial, FileWriteMode.CreateNew)
        present <- behavior.read(_target, _path)
        updated <- behavior.write(_target, _path, replacement, FileWriteMode.UpdateExisting(initial))
        finalread <- behavior.read(_target, _path)
      } yield (absent, created, present, updated, finalread)
      try {
        When("one real UnitOfWorkInterpreter evaluates the ordinary protected File program")
        val result = new UnitOfWorkInterpreter(new UnitOfWork(fixture.context)).run(program)
        Then("typed results reflect the actual filesystem sequence and only the selected file changes")
        val (absent, created, present, updated, finalread) = result.toOption.get
        absent.text shouldBe None
        created.outcome shouldBe FileWriteOutcome.Created
        present.text shouldBe Some(initial)
        updated.outcome shouldBe FileWriteOutcome.Updated
        finalread.text shouldBe Some(replacement)
        finalread.effect.kind shouldBe ExternalEffectKind.FileRead
        updated.effect.kind shouldBe ExternalEffectKind.FileWrite
        updated.effect.workspaceId shouldBe _id
        Files.readAllBytes(fixture.checkout.resolve(_path.value)).toVector shouldBe replacement.getBytes(StandardCharsets.UTF_8).toVector
        _names(fixture.checkout) shouldBe Set(_path.value)
        fixture.subsystem.globalRuntimeContext.config.resourceReadLimits.maximumBytes shouldBe 64
        fixture.context.workspaceExecutionAdmissionOption.get should be theSameInstanceAs(
          fixture.subsystem.globalRuntimeContext.workspaceExecutionAdmissionOption.get)
        fixture.programcount shouldBe 0
        fixture.processgrantcount shouldBe 0
      } finally _close(fixture)
    }

    "construct and record both typed intents without runtime services or filesystem effects" in {
      Given("an unconfigured construction context and real typed results supplied only by the recorder stub")
      val context = ExecutionContext.create()
      val behavior = new FileDslBehavior(Behavior.Core(context, None, None))
      val readresult = FileReadTextResult(_target, _path, Some("private source\n"))
      val writeresult = FileWriteTextResult(_target, _path, FileWriteOutcome.Created)
      val provider = new UnitOfWorkProgramStubProvider {
        def resultFor[A](ordinal: Int, operation: UnitOfWorkOp[A]): Consequence[A] = operation match {
          case UnitOfWorkOp.FileReadText(_, _) => Consequence.success(readresult)
          case UnitOfWorkOp.FileWriteText(_, _, _, _) => Consequence.success(writeresult)
          case _ => Consequence.notImplemented("unexpected File recording operation")
        }
      }
      When("the protected read and write are constructed and recorded without interpretation")
      val program = for {
        read <- behavior.read(_target, _path)
        written <- behavior.write(_target, _path, "private replacement\n", FileWriteMode.CreateNew)
      } yield (read, written)
      val recording = UnitOfWorkProgramRecorder.record(program, provider)
      Then("exactly two External intents and their supplied typed results survive without authority acquisition")
      recording.result.toOption shouldBe Some(readresult -> writeresult)
      recording.occurrences.map(_.operation) shouldBe Vector(
        UnitOfWorkOp.FileReadText(_target, _path),
        UnitOfWorkOp.FileWriteText(_target, _path, "private replacement\n", FileWriteMode.CreateNew))
      recording.occurrences.map(_.ordinal) shouldBe Vector(0, 1)
      recording.occurrences.map(_.effectClass) shouldBe Vector.fill(2)(UnitOfWorkEffectClass.External)
      context.workspaceExecutionAdmissionOption shouldBe None
      context.cncfCore.scope.processExecutionDriverOption shouldBe None
      context.resourceReadLimitsC.isFaillure shouldBe true
      recording.occurrences.mkString should not include "private replacement"
      readresult.toString should not include "private source"
      FileWriteMode.UpdateExisting("private expected").toString should not include "private expected"
    }

    "deny a write constructed under authorized A when actual context B has only ReadOnly" in {
      Given("separately assembled contexts A and B with the same logical target at different roots and ReadOnly B")
      val contexta = _fixture()
      val contextb = _fixture(permission = WorkspacePermission.ReadOnly)
      val behavior = new FileDslBehavior(Behavior.Core(contexta.context, None, None))
      val program = behavior.write(_target, _path, "private replacement", FileWriteMode.CreateNew)
      try {
        When("the already constructed A program is interpreted under B's owning UnitOfWork")
        val result = new UnitOfWorkInterpreter(new UnitOfWork(contextb.context)).run(program)
        Then("B denies Manage before any write and neither root gains a file")
        result.isFaillure shouldBe true
        _names(contexta.checkout) shouldBe empty
        _names(contextb.checkout) shouldBe empty
        result.toString should not include "private replacement"
      } finally {
        _close(contextb)
        _close(contexta)
      }
    }

    "select B's root and lower configured cap for programs constructed under A or without services" in {
      Given("authorized A and B runtimes with different roots and byte limits for the same logical ID")
      val contexta = _fixture()
      val contextb = _fixture(cap = 4)
      Files.writeString(contexta.checkout.resolve(_path.value), "source A\n", StandardCharsets.UTF_8)
      Files.writeString(contextb.checkout.resolve(_path.value), "B\n", StandardCharsets.UTF_8)
      val behavior = new FileDslBehavior(Behavior.Core(contexta.context, None, None))
      val unconfigured = new FileDslBehavior(Behavior.Core(ExecutionContext.create(), None, None))
      val readprogram = behavior.read(_target, _path)
      val writeprogram = unconfigured.write(_target, _path, "éé\n", FileWriteMode.UpdateExisting("B\n"))
      try {
        When("B interprets the reads and a replacement exceeding only B's current cap")
        val interpreter = new UnitOfWorkInterpreter(new UnitOfWork(contextb.context))
        val selected = interpreter.run(readprogram)
        val oversized = interpreter.run(writeprogram)
        val allowed = interpreter.run(unconfigured.write(_target, _path, "éé", FileWriteMode.UpdateExisting("B\n")))
        Then("B supplies actual text and policy, permitting exact-cap replacement only in its own root")
        selected.toOption.get.text shouldBe Some("B\n")
        oversized.isFaillure shouldBe true
        allowed.toOption.get.outcome shouldBe FileWriteOutcome.Updated
        Files.readString(contexta.checkout.resolve(_path.value), StandardCharsets.UTF_8) shouldBe "source A\n"
        Files.readString(contextb.checkout.resolve(_path.value), StandardCharsets.UTF_8) shouldBe "éé"
        contexta.context.workspaceExecutionAdmissionOption.get.bindingC(_id).toOption.get should not be theSameInstanceAs(
          contextb.context.workspaceExecutionAdmissionOption.get.bindingC(_id).toOption.get)
      } finally {
        _close(contextb)
        _close(contexta)
      }
    }

    "defer invalid intents and missing workspace or global-limit services until interpretation" in {
      Given("an unconfigured Behavior, a normally assembled runtime and malformed typed File values")
      val fixture = _fixture()
      val absent = ExecutionContext.create()
      val behavior = new FileDslBehavior(Behavior.Core(absent, None, None))
      val readprogram = behavior.read(_target, _path)
      val writeprogram = behavior.write(_target, _path, "new", FileWriteMode.CreateNew)
      val invalid = Vector[UnitOfWorkOp[?]](
        UnitOfWorkOp.FileReadText(null, _path), UnitOfWorkOp.FileReadText(FileTarget(null.asInstanceOf[WorkspaceId]), _path),
        UnitOfWorkOp.FileReadText(_target, null), UnitOfWorkOp.FileReadText(_target, _forged_path("../private")),
        UnitOfWorkOp.FileWriteText(_target, _path, null, FileWriteMode.CreateNew),
        UnitOfWorkOp.FileWriteText(_target, _path, "new", null),
        UnitOfWorkOp.FilePrepareDirectory(null, _path),
        UnitOfWorkOp.FilePrepareDirectory(_target, _forged_path("../private")),
        UnitOfWorkOp.FileWriteBytes(_target, _path, null, FileRepresentation.Identity),
        UnitOfWorkOp.FileWriteBytes(_target, _path, Vector.empty, null),
        UnitOfWorkOp.FileReadBytes(_target, _path, null),
        UnitOfWorkOp.FileReadBytes(_target, null, FileRepresentation.Gzip)
      )
      val admission = fixture.context.workspaceExecutionAdmissionOption.get
      val nonglobal = org.goldenport.cncf.context.ScopeContext(ScopeKind.Runtime, "no-global", None,
        absent.observability, workspaceExecutionAdmissionOption = Some(admission))
      val component = Component.create(_owner.name, _owner, ComponentInstanceId.default(_owner), Protocol.empty)
      val missingglobal = absent.withScope(Component.Context(_owner.name, nonglobal, component, ComponentOrigin.Unknown))
      try {
        When("the ordinary interpreter consumes missing-service programs and malformed operations")
        val unconfigured = new UnitOfWorkInterpreter(new UnitOfWork(absent))
        val missingservice = Vector(unconfigured.run(readprogram).isFaillure, unconfigured.run(writeprogram).isFaillure,
          unconfigured.run(behavior.prepare(_target, _path)).isFaillure,
          unconfigured.run(behavior.writeBytes(_target, _path, Vector.empty, FileRepresentation.Identity)).isFaillure,
          unconfigured.run(behavior.readBytes(_target, _path, FileRepresentation.Identity)).isFaillure)
        val noglobal = new UnitOfWorkInterpreter(new UnitOfWork(missingglobal)).run(writeprogram)
        val interpreter = new UnitOfWorkInterpreter(new UnitOfWork(fixture.context))
        val rejected = invalid.map(operation => interpreter.interpret(operation).isFaillure)
        Then("construction requires no services and every invalid interpretation fails before filesystem effects")
        missingservice shouldBe Vector.fill(5)(true)
        noglobal.isFaillure shouldBe true
        rejected shouldBe Vector.fill(invalid.size)(true)
        _names(fixture.checkout) shouldBe empty
      } finally _close(fixture)
    }
  }

  private def _forged_path(value: String): WorkspaceFilePath = {
    val constructor = classOf[WorkspaceFilePath].getDeclaredConstructor(classOf[String])
    constructor.setAccessible(true)
    constructor.newInstance(value)
  }

  private def _fixture(permission: WorkspacePermission = WorkspacePermission.Manage, cap: Int = 64): Fixture = {
    val base = Files.createDirectories(Paths.get("target", "file-execution-dsl-spec").toAbsolutePath)
    val root = Files.createTempDirectory(base, "case-").toRealPath()
    val checkout = Files.createDirectory(root.resolve("checkout"))
    val plan = RuntimeProcessExecutionPlan.createV3C(Vector.empty, Map.empty,
      Vector(WorkspaceRuntimeDeclaration(_id, checkout)), Vector(WorkspaceGrant(_owner, _id, permission)),
      Vector.empty, Vector.empty).toOption.get
    val text = RuntimeProcessExecutionPlanCodec.encode(plan).toOption.get.asInstanceOf[ConfigurationValue.StringValue].value
    val config = Files.createDirectories(root.resolve(".textus")).resolve("config.json")
    Files.writeString(config, Json.obj(
      CncfConfigurationParameterCatalog.SUBSYSTEM_PROCESS_EXECUTION_PLAN_KEY -> Json.fromString(text),
      "textus.resource.read.max-bytes" -> Json.fromString(cap.toString),
      "cncf.resource.write.max-bytes" -> Json.fromString(cap.toString)).noSpaces, StandardCharsets.UTF_8)
    val descriptor = root.resolve("test.yaml")
    Files.writeString(descriptor,
      """kind: test-descriptor
        |execution:
        |  profile: controlled
        |  key: file-runtime
        |  time:
        |    mode: manual
        |    start-at: '2026-10-15T00:00:00Z'
        |  random:
        |    mode: seeded
        |    seed: file-runtime
        |  ids:
        |    mode: deterministic
        |  scheduler:
        |    mode: manual
        |  ordering:
        |    mode: deterministic
        |""".stripMargin, StandardCharsets.UTF_8)
    val runtime = new CncfRuntime()
    try {
      val subsystem = runtime.initializeForEmbedding(root, Array(s"--textus.test.descriptor=$descriptor")).toOption.get
      val component = Component.create(_owner.name, _owner, ComponentInstanceId.default(_owner), Protocol.empty)
      val scope = Component.Context(_owner.name, subsystem.globalRuntimeContext, component, ComponentOrigin.Embed)
        .createChildScope(ScopeKind.Service, "files").createChildScope(ScopeKind.Action, "edit")
      Fixture(root, checkout, runtime, subsystem, ExecutionContext.create().withScope(scope),
        plan.programs.size, plan.grants.values.map(_.size).sum)
    } catch {
      case error: Throwable => runtime.closeEmbedding(); throw error
    }
  }

  private def _names(root: Path): Set[String] =
    Using.resource(Files.list(root))(_.iterator().asScala.map(_.getFileName.toString).toSet)

  private def _close(fixture: Fixture): Unit = {
    try Subsystem.shutdownOwned(fixture.subsystem) finally {
      try fixture.runtime.closeEmbedding() finally Using.resource(Files.walk(fixture.root)) { stream =>
        stream.sorted(Comparator.reverseOrder[Path]()).iterator().asScala.foreach(path => Files.deleteIfExists(path))
      }
    }
  }
}

object FileExecutionDslSpec {
  private final case class Fixture(
    root: Path,
    checkout: Path,
    runtime: CncfRuntime,
    subsystem: Subsystem,
    context: ExecutionContext,
    programcount: Int,
    processgrantcount: Int
  )
}

private final class FileDslBehavior(val behaviorCore: Behavior.Core) extends Behavior {
  def snapshot(target: FileTarget, selection: ResourceTreeSelection,
    limits: ResourceTreeQueryLimits = FileSnapshotLimits.default) = file_read_snapshot(target, selection, limits)
  def prepare(target: FileTarget, path: WorkspaceFilePath) = file_prepare_directory(target, path)
  def writeBytes(target: FileTarget, path: WorkspaceFilePath, bytes: Vector[Byte], representation: FileRepresentation) =
    file_write_bytes(target, path, bytes, representation)
  def readBytes(target: FileTarget, path: WorkspaceFilePath, representation: FileRepresentation) =
    file_read_bytes(target, path, representation)
  def writeBytes(target: FileTarget, path: WorkspaceFilePath, bytes: Vector[Byte], representation: FileRepresentation, policy: FileWritePolicy) =
    file_write_bytes(target, path, bytes, representation, policy)
  def readBytes(target: FileTarget, path: WorkspaceFilePath, representation: FileRepresentation, policy: FileReadPolicy) =
    file_read_bytes(target, path, representation, policy)
  def read(target: FileTarget, path: WorkspaceFilePath, policy: FileReadPolicy) = file_read_text(target, path, policy)
  def write(target: FileTarget, path: WorkspaceFilePath, text: String, mode: FileWriteMode, policy: FileWritePolicy) =
    file_write_text(target, path, text, mode, policy)
  def read(target: FileTarget, path: WorkspaceFilePath) = file_read_text(target, path)
  def write(target: FileTarget, path: WorkspaceFilePath, text: String, mode: FileWriteMode) = file_write_text(target, path, text, mode)
}
