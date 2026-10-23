package org.goldenport.cncf.processexecution

import io.circe.Json
import io.circe.parser.parse
import java.nio.file.{Files, Path, Paths}
import org.goldenport.Consequence
import org.goldenport.cncf.component.ComponentId
import org.goldenport.cncf.observability.ConclusionDiagnostics
import org.goldenport.cncf.workspace.*
import org.goldenport.configuration.ConfigurationValue
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Oct.  7, 2026
 * @version Oct. 23, 2026
 * @author  ASAMI, Tomoharu
 */
final class RuntimeProcessExecutionPlanCodecSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private def _take[A](value: Consequence[A]): A = value match {
    case Consequence.Success(result) => result
    case failure => fail(s"Process configuration rejected: $failure")
  }
  private val _capability = _take(ProcessCapabilityId.parseC("selected-token"))
  private val _owner = ComponentId("org.example.Workflow")
  private val _limits = ProcessExecutionLimits(Some(100), Some(100), Some(100), Some(64), Some(64),
    Some(64), Some(4), Some(512), Some(1), Some(64), Some(128))
  private val _slots = Vector(ProcessArgumentSlot.Literal("show"), ProcessArgumentSlot.LowerHex(Set(40, 64)))
  private val _definition = _take(ProcessProgramDefinition.fromRuntimeC(_capability, "selected-token", "/runtime/bin/selected-tool",
    Vector("-C", "/runtime/selected"), ProcessArgumentPolicy(Vector("-C", "/runtime/selected"),
      admission = ProcessArgumentAdmission.Template, permittedArgumentTemplates = Set(ProcessArgumentTemplate(_slots))),
    _limits, Set.empty, environment = Map("PATH" -> "/runtime/bin")))
  private val _plan = _take(RuntimeProcessExecutionPlan.createC(Vector(_definition),
    Map(_owner -> Vector(ProcessExecutionGrant(_capability)))))
  private val _encoded = _take(RuntimeProcessExecutionPlanCodec.encode(_plan)).asInstanceOf[ConfigurationValue.StringValue]
  private val _root = parse(_encoded.value).toOption.get
  private def _decode(value: Json) = RuntimeProcessExecutionPlanCodec.decode(ConfigurationValue.StringValue(value.noSpaces))
  private def _changed_program(f: Json => Json): Json = _root.mapObject(_.add("programs",
    Json.arr(f(_root.hcursor.downField("programs").downArray.focus.get))))
  private def _v2_root(root: Path): Json = {
    val workspace = _take(WorkspaceId.parseC("selected-checkout"))
    val capability = _take(ProcessCapabilityId.parseC("selected-test"))
    val plan = _take(RuntimeProcessExecutionPlan.createV2C(Vector(_definition),
      Map(_owner -> Vector(ProcessExecutionGrant(_capability), ProcessExecutionGrant(capability))),
      Vector(WorkspaceRuntimeDeclaration(workspace, root)), Vector(WorkspaceGrant(_owner, workspace, WorkspacePermission.Manage)),
      Vector(SbtRuntimeDeclaration(workspace, SbtOperation.Test, capability, "selected-test",
        Paths.get("/usr/bin/true"), _limits, Map("BUILD_PROFILE" -> "selected")))))
    parse(_take(RuntimeProcessExecutionPlanCodec.encode(plan)).asInstanceOf[ConfigurationValue.StringValue].value).toOption.get
  }
  private def _directory(): Path = Files.createTempDirectory(
    Files.createDirectories(Paths.get("target", "cncf", "work", "runtime-plan-codec").toAbsolutePath), "case-")
  private def _changed_entry(root: Json, field: String, change: Json => Json): Json =
    root.mapObject(_.add(field, Json.arr(change(root.hcursor.downField(field).downArray.focus.get))))

  private def _v3_root(root: Path): Json = {
    val id = GitExecutionSpecSupport.workspaceId
    val sbtcapability = _take(ProcessCapabilityId.parseC("v3-test"))
    val git = GitOperationKind.values.toVector.map(kind => GitExecutionSpecSupport.declaration(kind, root.resolve("fixed-remote.git").toString))
    val plan = _take(RuntimeProcessExecutionPlan.createV3C(Vector(_definition),
      Map(_owner -> (Vector(ProcessExecutionGrant(_capability), ProcessExecutionGrant(sbtcapability)) ++ git.map(x => ProcessExecutionGrant(x.capability)))),
      Vector(WorkspaceRuntimeDeclaration(id, root)), Vector(WorkspaceGrant(_owner, id, WorkspacePermission.Manage)),
      Vector(SbtRuntimeDeclaration(id, SbtOperation.Test, sbtcapability, "v3-test", Paths.get("/usr/bin/true"), _limits, Map.empty)), git))
    parse(_take(RuntimeProcessExecutionPlanCodec.encode(plan)).asInstanceOf[ConfigurationValue.StringValue].value).toOption.get
  }

  private def _changed_git(root: Json, kind: GitOperationKind, change: Json => Json): Json = root.mapObject(_.add("gitPrograms",
    Json.fromValues(root.hcursor.get[Vector[Json]]("gitPrograms").toOption.get.map { entry =>
      if (entry.hcursor.get[String]("operation").toOption.contains(kind.wireName)) change(entry) else entry
    })))

  private def _v4_root(root: Path): Json = _v3_root(root).mapObject(
    _.add("schema", Json.fromString("cncf.runtime-process-execution.v4"))
      .add("registeredSbtHost", Json.obj("requestFile" -> Json.fromString(root.resolve("request.json").toString),
        "responseFile" -> Json.fromString(root.resolve("response.json").toString))))

  "E9 Host-source V3 registration" must afterWord("in spec:host-source-read, example:E9, rules:R3,R6, phase:6.4") {
    "round-trip a sole empty argument template while preserving all existing generic, Git, SBT and component grants" in {
      Given("docs/spec/host-source-read.md R3 R6 E9, an existing V3 plan with independent Git and SBT authority")
      val wire = _v3_root(_directory())
      val original = _take(_decode(wire))
      val identity = HostSourceReadSpecSupport.capability.print
      val program = wire.hcursor.downField("programs").downArray.focus.get.mapObject(
        _.add("capability", Json.fromString(identity)).add("identity", Json.fromString(identity))
          .add("executable", Json.fromString("/runtime/bin/native-goal-terminal-source.py"))
          .add("fixedArguments", Json.arr(Json.fromString("--codex-executable"), Json.fromString("/runtime/bin/codex")))
          .add("argumentTemplates", Json.arr(Json.arr()))
          .add("limits", wire.hcursor.downField("programs").downArray.downField("limits").focus.get
            .mapObject(_.add("stdinBytes", Json.fromLong(65536)))))
      val programs = wire.hcursor.get[Vector[Json]]("programs").toOption.get :+ program
      val grants = wire.hcursor.get[Vector[Json]]("grants").toOption.get.map { grant =>
        val capabilities = grant.hcursor.get[Vector[String]]("capabilities").toOption.get :+ identity
        grant.mapObject(_.add("capabilities", Json.fromValues(capabilities.map(Json.fromString))))
      }
      val selected = wire.mapObject(_.add("programs", Json.fromValues(programs)).add("grants", Json.fromValues(grants)))
      When("the host installs the additive registration and round-trips the unchanged V3 schema")
      val first = _take(_decode(selected))
      val encoded = _take(RuntimeProcessExecutionPlanCodec.encode(first)).asInstanceOf[ConfigurationValue.StringValue]
      val restored = _take(RuntimeProcessExecutionPlanCodec.decode(encoded))
      val definition = restored.programs.find(_.capability == HostSourceReadSpecSupport.capability).get
      Then("the exact empty vector template survives and all previous declarations and grants remain present")
      parse(encoded.value).toOption.get shouldBe selected
      restored.schemaVersion shouldBe RuntimeProcessExecutionPlanCodec.SCHEMA_V3
      restored.gitDeclarations shouldBe original.gitDeclarations
      restored.sbtDeclarations shouldBe original.sbtDeclarations
      restored.workspaceDeclarations shouldBe original.workspaceDeclarations
      restored.workspaceGrants shouldBe original.workspaceGrants
      original.grants.foreach { case (owner, grants) => restored.grants(owner).take(grants.size) shouldBe grants }
      restored.programs.map(_.capability) shouldBe original.programs.map(_.capability) :+ HostSourceReadSpecSupport.capability
      definition.argumentPolicy.admission shouldBe ProcessArgumentAdmission.Template
      definition.argumentPolicy.permittedArgumentTemplates shouldBe Set(ProcessArgumentTemplate(Vector.empty))
      definition.argumentPolicy.validateC(Vector.empty, definition.maximumLimits).isSuccess shouldBe true
      definition.argumentPolicy.validateC(Vector("extra"), definition.maximumLimits).isFaillure shouldBe true
    }
  }

  "Runtime process host wire contract" should {
    "reject retired V4 even when its underlying V3 authority is valid" in {
      Given("a valid V3 authority object extended by the retired V4 file routing fields")
      val root = _directory()
      val wire = _v4_root(root)
      When("the complete V4 object and a V4 schema without routing fields are decoded")
      val results = Vector(wire, wire.mapObject(_.remove("registeredSbtHost"))).map(_decode)
      Then("both fail explicitly as unsupported without silently falling back to V3")
      results.foreach { result =>
        result.isFaillure shouldBe true
        result match {
          case Consequence.Failure(conclusion) =>
            conclusion.toString should include ("Unsupported runtime process schema")
          case _ => fail("Retired V4 must fail as an unsupported runtime process schema")
        }
      }
    }

    "preserve V1 through V3 encoding without inferring registered-host configuration" in {
      Given("the existing generic V1, semantic V2 and Git-capable V3 host representations")
      val wires = Vector(_root, _v2_root(_directory()), _v3_root(_directory()))
      When("each existing representation completes its ordinary decode and encode round trip")
      val results = wires.map { wire =>
        val plan = _take(_decode(wire))
        val encoded = _take(RuntimeProcessExecutionPlanCodec.encode(plan)).asInstanceOf[ConfigurationValue.StringValue]
        plan -> parse(encoded.value).toOption.get
      }
      Then("all old schemas retain their fields and ordinary local execution selection")
      results.map(_._1.schemaVersion) shouldBe Vector(RuntimeProcessExecutionPlanCodec.SCHEMA,
        RuntimeProcessExecutionPlanCodec.SCHEMA_V2, RuntimeProcessExecutionPlanCodec.SCHEMA_V3)
      results.map(_._2) shouldBe wires
      results.foreach(_._2.asObject.get.contains("registeredSbtHost") shouldBe false)
    }

    "retain V3 grant requirements and reject retired host fields in earlier schemas" in {
      Given("valid local schemas and the retired routing field, plus V3 declarations lacking required authority")
      val wire = _v4_root(_directory())
      val host = wire.hcursor.downField("registeredSbtHost").focus.get
      val earlier = Vector(_root, _v2_root(_directory()), _v3_root(_directory())).map(
        _.mapObject(_.add("registeredSbtHost", host)))
      val v3 = _v3_root(_directory())
      val invalid = Vector(_changed_entry(v3, "workspaceGrants", _.mapObject(_.add("access", Json.fromString("read-only")))),
        v3.mapObject(_.add("workspaceGrants", Json.arr())),
        _changed_entry(v3, "sbtPrograms", _.mapObject(_.add("target", Json.fromString("unknown")))))
      When("the host decodes incompatible schema extensions and denied V3 authorities")
      val results = (earlier ++ invalid).map(_decode)
      Then("routing fields cannot create an external route and ordinary V3 admission retains its workspace requirements")
      results.foreach(_.isFaillure shouldBe true)
    }

    "refuse ordinary shell and known capability laundering in V1, V2 and V3 despite exact declarations and grants" in {
      Given("valid selected-tool wires for all three schemas, retaining separately tagged SBT and Git declarations")
      val wires = Vector(_root, _v2_root(_directory()), _v3_root(_directory()))
      val secret = "confidential-codec-effect-sentinel"
      val expecteddiagnostic = ConclusionDiagnostics.classify(
        Consequence.configurationInvalid("Invalid runtime process declaration").conclusion)
      val names = Vector("bash" -> "external.effect.direct", "cat" -> "external.effect.capability-laundering",
        "curl" -> "external.effect.capability-laundering", "git" -> "external.effect.capability-laundering")
      val deniedwires = wires.flatMap(wire => names.map { case (name, policy) =>
        _changed_entry(wire, "programs", _.mapObject(_.add("executable", Json.fromString(s"/runtime/$secret/$name")))) -> policy
      })
      When("natural host decoding receives the original exact tool and each forbidden generic replacement")
      val accepted = wires.map(wire => _take(_decode(wire)))
      val rejected = deniedwires.map { case (wire, policy) => _decode(wire) -> policy }
      Then("generic selected-tool and separate semantic tools remain installable while recognized bypasses fail with redacted route policies")
      accepted.foreach { plan =>
        plan.programs.head._executable_location shouldBe "/runtime/bin/selected-tool"
        plan.grants(_owner).map(_.capability) should contain (_capability)
      }
      accepted.map(_.sbtDeclarations.size) shouldBe Vector(0, 1, 1)
      accepted.map(_.gitDeclarations.size) shouldBe Vector(0, 0, GitOperationKind.values.length)
      rejected.foreach { case (result, _) =>
        result match {
          case Consequence.Failure(conclusion) =>
            ConclusionDiagnostics.classify(conclusion) shouldBe expecteddiagnostic
            conclusion.toString should include ("Invalid runtime process declaration")
            conclusion.toString should not include secret
          case _ => fail("Known ordinary effects must not install a host plan")
        }
      }
    }

    "round-trip exact TestOnly declarations through V2 and V3 without mixing generic programs" in {
      Given("V2 and V3 host objects with a trusted exact two-class TestOnly selection")
      val classes = Vector("org.example.SelectedSpec", "org.other.OtherSpec")
      val wires = Vector(_v2_root(_directory()), _v3_root(_directory())).map(root =>
        _changed_entry(root, "sbtPrograms", _.mapObject(_.add("operation", Json.fromString("test-only"))
          .add("testClasses", Json.fromValues(classes.map(Json.fromString))))))
      When("each strict host plan is decoded, encoded and decoded again")
      val plans = wires.map { wire =>
        val first = _take(_decode(wire))
        val encoded = _take(RuntimeProcessExecutionPlanCodec.encode(first))
        val restored = _take(RuntimeProcessExecutionPlanCodec.decode(encoded))
        (first, restored, parse(encoded.asInstanceOf[ConfigurationValue.StringValue].value).toOption.get)
      }
      Then("the exact eight-field declaration and selected classes survive in private metadata with shared binding")
      plans.foreach { case (first, restored, wire) =>
        restored.sbtDeclarations shouldBe first.sbtDeclarations
        restored.sbtDeclarations.head.operation shouldBe SbtOperation.TestOnly
        restored.sbtDeclarations.head.testClasses shouldBe classes
        wire.hcursor.downField("sbtPrograms").downArray.focus.get.asObject.get.keys.toSet shouldBe
          Set("target", "operation", "capability", "identity", "sbtExecutable", "limits", "environment", "testClasses")
        val binding = _take(restored.workspaceAdmissionOption.get.bindingC(restored.sbtDeclarations.head.target))
        restored.programs should have size 1
        restored.programs.foreach(_._sbt_metadata shouldBe None)
        val declaration = restored.sbtDeclarations.head
        val tagged = _take(SbtProgramDefinition.createC(binding, declaration.operation, declaration.capability,
          declaration.identity, declaration.sbtExecutable, declaration.limits, declaration.environment, declaration.testClasses))
        tagged._sbt_metadata.get.testClasses shouldBe classes
        (tagged._sbt_metadata.get.binding eq binding) shouldBe true
        tagged.fixedArguments.last shouldBe "testOnly org.example.SelectedSpec org.other.OtherSpec"
      }
    }

    "retain exact seven-field Compile and Test declarations and reject even empty selection fields" in {
      Given("existing V2 and V3 Compile/Test host declarations without TestOnly fields")
      val wires = Vector(_v2_root(_directory()), _v3_root(_directory())).flatMap(root =>
        Vector("compile", "test").map(operation => _changed_entry(root, "sbtPrograms",
          _.mapObject(_.add("operation", Json.fromString(operation))))))
      When("the decoder accepts existing shapes and receives an added empty or null testClasses field")
      val accepted = wires.map(wire => _take(RuntimeProcessExecutionPlanCodec.encode(_take(_decode(wire)))))
      val rejected = wires.flatMap(wire => Vector(Json.arr(), Json.Null).map(classes =>
        _decode(_changed_entry(wire, "sbtPrograms", _.mapObject(_.add("testClasses", classes))))))
      Then("existing declarations remain seven fields and conditional selection fields fail closed")
      accepted.foreach { encoded =>
        val entry = parse(encoded.asInstanceOf[ConfigurationValue.StringValue].value).toOption.get
          .hcursor.downField("sbtPrograms").downArray.focus.get.asObject.get
        entry.size shouldBe 7
        entry.contains("testClasses") shouldBe false
      }
      rejected.foreach(_.isFaillure shouldBe true)
    }

    "refuse absent, malformed, oversized, ambiguous and duplicated TestOnly host declarations" in {
      Given("both supported semantic schema versions and invalid conditional TestOnly fields or authority")
      val invalidclasses = Vector(Json.Null, Json.arr(), Json.True, Json.arr(Json.True), Json.arr(Json.fromString("")),
        Json.arr(Json.fromString("Simple")), Json.arr(Json.fromString("a.B*")), Json.arr(Json.fromString("a.B$")),
        Json.arr(Json.fromString(" a.B")), Json.arr(Json.fromString("a.B ")), Json.arr(Json.fromString("a.Ü")),
        Json.arr(Json.fromString("a.B;test")), Json.arr(Json.fromString("a/B")), Json.arr(Json.fromString("a.B-C")),
        Json.arr(Json.fromString("a.B\n")), Json.arr(Json.fromString("a.B"), Json.fromString("a.B")),
        Json.arr(Json.fromString("a.B"), Json.fromString("b.B")),
        Json.arr(Json.fromString("a." + "B" * 255)), Json.fromValues((1 to 17).map(x => Json.fromString(s"a.Suite$x"))))
      val wires = Vector(_v2_root(_directory()), _v3_root(_directory())).map(root =>
        _changed_entry(root, "sbtPrograms", _.mapObject(_.add("operation", Json.fromString("test-only"))
          .add("testClasses", Json.arr(Json.fromString("a.B"))))))
      val malformed = wires.flatMap { wire =>
        val entry = wire.hcursor.downField("sbtPrograms").downArray.focus.get
        invalidclasses.map(classes => _changed_entry(wire, "sbtPrograms", _.mapObject(_.add("testClasses", classes)))) ++ Vector(
          _changed_entry(wire, "sbtPrograms", _.mapObject(_.remove("testClasses"))),
          _changed_entry(wire, "sbtPrograms", _.mapObject(_.add("extra", Json.True))),
          wire.mapObject(_.add("sbtPrograms", Json.arr(entry, entry))),
          wire.mapObject(_.add("programs", Json.arr(entry))),
          wire.mapObject(_.add("workspaceGrants", Json.arr())),
          _changed_entry(wire, "workspaceGrants", _.mapObject(_.add("access", Json.fromString("read-only")))),
          _changed_entry(wire, "workspaceGrants", _.mapObject(_.add("componentId", Json.fromString("org.example.Other")))))
      }
      When("strict wire decoding receives these objects and a duplicate testClasses JSON key")
      val results = malformed.map(_decode)
      val duplicatekeys = wires.map(wire => RuntimeProcessExecutionPlanCodec.decode(ConfigurationValue.StringValue(
        wire.noSpaces.replace("\"testClasses\":[\"a.B\"]", "\"testClasses\":[\"a.B\"],\"testClasses\":[\"a.C\"]"))))
      Then("none installs a plan or widens TestOnly authority")
      results.foreach(_.isFaillure shouldBe true)
      duplicatekeys.foreach(_.isFaillure shouldBe true)
    }

    "round-trip V3 thirty-three Git declarations and SBT through one identical shared binding" in {
      Given("one V3 host plan with all thirty-three Git operations, a Test declaration and generic templates")
      val root = _directory()
      val wire = _v3_root(root)
      When("the strict host codec round-trips the plan and actual owning semantic admission resolves both tools")
      val first = _take(_decode(wire))
      val restored = _take(RuntimeProcessExecutionPlanCodec.decode(_take(RuntimeProcessExecutionPlanCodec.encode(first))))
      val profile = ProcessExecutionTestProfile(Map.empty)
      val context = GitExecutionSpecSupport.contextFor(root, restored.workspaceAdmissionOption, Some(restored.admission), profile.driver, _owner)
      val target = GitExecutionSpecSupport.workspaceId
      val git = _take(GitExecutionAdmission.resolveC(context, GitTarget(target), GitOperation.Head))
      val sbt = _take(SbtExecutionAdmission.resolveC(context, SbtTarget(target), SbtOperation.Test))
      val binding = _take(restored.workspaceAdmissionOption.get.bindingC(target))
      Then("typed declarations remain separate from generic programs and both metadata tags retain that exact binding object")
      restored.schemaVersion shouldBe RuntimeProcessExecutionPlanCodec.SCHEMA_V3
      restored.programs.map(_.capability) shouldBe Vector(_capability)
      restored.gitDeclarations shouldBe first.gitDeclarations
      restored.gitDeclarations.map(_.operation) shouldBe GitOperationKind.values.toVector
      (git.definition._git_metadata.get.binding eq binding) shouldBe true
      (sbt.definition._sbt_metadata.get.binding eq binding) shouldBe true
      restored.grants shouldBe first.grants
      GitExecutionAdmission.validateGenericExecutionC(git).isFaillure shouldBe true
    }

    "round-trip only the three operation-specific bounded selected path arrays" in {
      Given("all V3 Git declarations and fixed literal paths containing colon, backslash and LF")
      val wire = _v3_root(_directory())
      val kinds = Set(GitOperationKind.CommitSelected, GitOperationKind.CheckSelectedIndex, GitOperationKind.SelectedIndexChanges)
      val paths = Vector("selected.txt", "C:literal\\name\n.txt")
      val selected = kinds.foldLeft(wire)((root, kind) => _changed_git(root, kind,
        _.mapObject(_.add("selectedPaths", Json.fromValues(paths.map(Json.fromString))))))
      When("strict V3 decoding and canonical encoding reconstruct each operation's exact field set")
      val first = _take(_decode(selected))
      val encoded = _take(RuntimeProcessExecutionPlanCodec.encode(first))
      val restored = _take(RuntimeProcessExecutionPlanCodec.decode(encoded))
      val entries = parse(encoded.asInstanceOf[ConfigurationValue.StringValue].value).toOption.get
        .hcursor.get[Vector[Json]]("gitPrograms").toOption.get
      Then("bounded selections round-trip exactly and all other operations omit selectedPaths")
      restored.gitDeclarations shouldBe first.gitDeclarations
      restored.gitDeclarations.foreach { declaration =>
        declaration.selectedPaths.map(_.value) shouldBe (if (kinds.contains(declaration.operation)) paths else Vector.empty)
        val entry = entries.find(_.hcursor.get[String]("operation").toOption.contains(declaration.operation.wireName)).get
        entry.asObject.get.contains("selectedPaths") shouldBe kinds.contains(declaration.operation)
        entry.asObject.get.size shouldBe (if (kinds.contains(declaration.operation)) 16 else 15)
      }
    }

    "E1 Selected Git declarations with a shared selection ID" should afterWord(
      "in spec:runtime-git-selection-binding, example:E1, rules:R1, phase:CC-02") {
      "reject different paths or vector order between the selected commit and read declarations" in {
        Given("R1: three V3 declarations share a workspace, selection ID and ordered two-path selection")
        val kinds = Vector(GitOperationKind.CommitSelected, GitOperationKind.CheckSelectedIndex, GitOperationKind.SelectedIndexChanges)
        val paths = Vector("selected.txt", "other.txt")
        val wire = kinds.foldLeft(_v3_root(_directory()))((root, kind) => _changed_git(root, kind,
          _.mapObject(_.add("selectedPaths", Json.fromValues(paths.map(Json.fromString))))))
        val crossed = kinds.flatMap(kind => Vector(Vector("different.txt", "other.txt"), paths.reverse).map(selection =>
          _changed_git(wire, kind, _.mapObject(_.add("selectedPaths", Json.fromValues(selection.map(Json.fromString)))))))
        When("the public runtime-plan decoder receives each crossed declaration and the consistent plan")
        val rejected = crossed.map(_decode)
        val accepted = _decode(wire)
        Then("every crossed path or order fails configuration admission while the identical ordered selection is admitted")
        rejected.foreach { result =>
          result.isFaillure shouldBe true
          result.toString should include("Invalid runtime process declaration")
        }
        accepted.isSuccess shouldBe true
        accepted.toOption.get.gitDeclarations.filter(x => kinds.contains(x.operation))
          .foreach(_.selectedPaths.map(_.value) shouldBe paths)
      }
    }

    "E2 Partial selected Git authority" should afterWord(
      "in spec:runtime-git-selection-binding, example:E2, rules:R2, phase:CC-02") {
      "admit every consistent subset including read-only selections without a selected commit grant" in {
        Given("R2: selected declarations may be partial and independent selection IDs may bind different vectors")
        val wire = _v3_root(_directory())
        val kinds = Vector(GitOperationKind.CommitSelected, GitOperationKind.CheckSelectedIndex, GitOperationKind.SelectedIndexChanges)
        val subsets = kinds.toSet.subsets().filter(_.nonEmpty).toVector
        val entries = wire.hcursor.get[Vector[Json]]("gitPrograms").toOption.get
        val partial = subsets.map { subset =>
          val selected = entries.filter(entry => subset.exists(kind => entry.hcursor.get[String]("operation").toOption.contains(kind.wireName)))
          val programs = wire.mapObject(_.add("gitPrograms", Json.fromValues(selected)))
          val grants = _changed_entry(programs, "grants", _.mapObject(_.add("capabilities",
            Json.fromValues(selected.map(_.hcursor.downField("capability").focus.get)))))
          if (subset.contains(GitOperationKind.CommitSelected)) grants
          else _changed_entry(grants, "workspaceGrants", _.mapObject(_.add("access", Json.fromString("read-only"))))
        }
        val independent = _changed_git(wire, GitOperationKind.CommitSelected, _.mapObject(
          _.add("selectionId", Json.fromString("another-choice"))
            .add("selectedPaths", Json.arr(Json.fromString("different.txt")))))
        When("the public runtime-plan decoder receives all partial selections and the independently named selection")
        val decoded = partial.map(_decode)
        val separate = _decode(independent)
        Then("consistent subsets remain usable under their declared authority and distinct IDs do not share a path constraint")
        decoded.foreach(_.isSuccess shouldBe true)
        decoded.zip(subsets).foreach { case (result, subset) =>
          val plan = result.toOption.get
          plan.gitDeclarations.map(_.operation).toSet shouldBe subset
          plan.gitDeclarations.foreach(_.selectedPaths.map(_.value) shouldBe Vector("selected.txt"))
          if (!subset.contains(GitOperationKind.CommitSelected)) {
            plan.workspaceGrants.map(_.permission) shouldBe Vector(WorkspacePermission.ReadOnly)
            plan.grants.values.flatten.map(_.capability).toSet shouldBe
              subset.map(GitExecutionSpecSupport.capability)
          }
        }
        separate.isSuccess shouldBe true
      }
    }

    "reject absent null empty duplicate escaping or widened selectedPaths for each bounded kind" in {
      Given("a V3 host plan with the three exact applicable operations and strict nonapplicable operations")
      val wire = _v3_root(_directory())
      val kinds = Vector(GitOperationKind.CommitSelected, GitOperationKind.CheckSelectedIndex, GitOperationKind.SelectedIndexChanges)
      val invalidpaths = Vector(Json.Null, Json.arr(), Json.True, Json.arr(Json.Null), Json.arr(Json.True),
        Json.arr(Json.fromString("")), Json.arr(Json.fromString("../escape")), Json.arr(Json.fromString("/absolute")),
        Json.arr(Json.fromString(".git/config")), Json.arr(Json.fromString("nul\u0000path")),
        Json.arr(Json.fromString("x"), Json.fromString("x")), Json.fromValues((0 to 256).map(n => Json.fromString(s"file$n"))))
      val malformed = kinds.flatMap(kind => invalidpaths.map(paths => _changed_git(wire, kind,
        _.mapObject(_.add("selectedPaths", paths)))) ++ Vector(
        _changed_git(wire, kind, _.mapObject(_.remove("selectedPaths"))),
        _changed_git(wire, kind, _.mapObject(_.add("extra", Json.True)))))
      val unrelated = GitOperationKind.values.toVector.filterNot(kinds.contains).flatMap(kind =>
        Vector(Json.Null, Json.arr(), Json.arr(Json.fromString("selected.txt"))).map(paths =>
          _changed_git(wire, kind, _.mapObject(_.add("selectedPaths", paths)))))
      When("the operation-first decoder receives missing fields, invalid arrays and fields outside their operation")
      val results = (malformed ++ unrelated).map(_decode)
      val duplicatekey = RuntimeProcessExecutionPlanCodec.decode(ConfigurationValue.StringValue(wire.noSpaces.replace(
        "\"selectedPaths\":[\"selected.txt\"]", "\"selectedPaths\":[\"selected.txt\"],\"selectedPaths\":[\"other.txt\"]")))
      Then("no old unbounded commit, ambiguous selection or unrelated field installs authority")
      results.foreach(_.isFaillure shouldBe true)
      duplicatekey.isFaillure shouldBe true
    }

    "reject V3 unknown fields, schema, duplicate keys, duplicate semantic capabilities and duplicate selections" in {
      Given("a strict complete V3 object and ambiguous or widened declarations")
      val wire = _v3_root(_directory())
      val entries = wire.hcursor.get[Vector[Json]]("gitPrograms").toOption.get
      val invalid = Vector(wire.mapObject(_.add("schema", Json.fromString("cncf.runtime-process-execution.v4"))),
        wire.mapObject(_.add("unknown", Json.True)), wire.mapObject(_.remove("gitPrograms")),
        _changed_git(wire, GitOperationKind.Head, _.mapObject(_.add("argv", Json.arr()))),
        wire.mapObject(_.add("gitPrograms", Json.fromValues(entries :+ entries.head))),
        _changed_git(wire, GitOperationKind.Head, _.mapObject(_.add("capability", Json.fromString("git-branch")))),
        _changed_git(wire, GitOperationKind.Head, _.mapObject(_.add("operation", Json.fromString("merge")))),
        _changed_git(wire, GitOperationKind.Head, _.mapObject(_.add("capability", Json.fromString("GIT-HEAD")))))
      val duplicate = ConfigurationValue.StringValue(wire.noSpaces.replace("\"schema\":", "\"schema\":\"duplicate\",\"schema\":"))
      When("the V3 decoder receives each malformed authority object")
      val results = invalid.map(_decode) :+ RuntimeProcessExecutionPlanCodec.decode(duplicate)
      Then("every unknown or ambiguous declaration fails before runtime installation")
      results.foreach(_.isFaillure shouldBe true)
    }

    "reject malformed V3 references, selected paths, identity fields, environment and finite limits" in {
      Given("a complete V3 plan and invalid fixed host-only inputs")
      val root = _directory()
      val wire = _v3_root(root)
      val invalid = Vector(
        _changed_git(wire, GitOperationKind.Head, _.mapObject(_.add("gitExecutable", Json.fromString("git")))),
        _changed_git(wire, GitOperationKind.Head, _.mapObject(_.add("gitExecutable", Json.fromString(root.resolve("missing").toString)))),
        _changed_git(wire, GitOperationKind.Head, _.mapObject(_.add("localBranch", Json.fromString("a..b")))),
        _changed_git(wire, GitOperationKind.Head, _.mapObject(_.add("remote", Json.fromString("a/b")))),
        _changed_git(wire, GitOperationKind.Head, _.mapObject(_.add("remoteBranch", Json.fromString("-x")))),
        _changed_git(wire, GitOperationKind.Head, _.mapObject(_.add("endpoint", Json.fromString("--upload-pack=bad")))),
        _changed_git(wire, GitOperationKind.Head, _.mapObject(_.add("identity", Json.fromString(" Bad identity ")))),
        _changed_git(wire, GitOperationKind.Head, _.mapObject(_.add("environment", Json.obj("GIT_DIR" -> Json.fromString("/other"))))),
        _changed_git(wire, GitOperationKind.Head, _.mapObject(_.add("environment", Json.obj("LC_ALL" -> Json.fromString("en_US"))))),
        _changed_git(wire, GitOperationKind.Head, _.mapObject(_.add("environment", Json.obj("PATH" -> Json.fromString("bad\npath"))))),
        _changed_git(wire, GitOperationKind.Head, _.mapObject(_.add("limits", Json.obj()))),
        _changed_git(wire, GitOperationKind.Head, _.mapObject(_.add("selectionId", Json.fromString("irrelevant")))),
        _changed_git(wire, GitOperationKind.StageSelected, _.mapObject(_.add("selectionId", Json.fromString("Bad/selector")))),
        _changed_git(wire, GitOperationKind.StageSelected, _.mapObject(_.add("selectedPath", Json.fromString(".git/config")))),
        _changed_git(wire, GitOperationKind.StageSelectedAll, _.mapObject(_.add("selectedPath", Json.Null))),
        _changed_git(wire, GitOperationKind.CommitSelected, _.mapObject(_.add("commitMessage", Json.fromString("x" * 4097)))),
        _changed_git(wire, GitOperationKind.CommitSelected, _.mapObject(_.add("commitMessage", Json.fromString("bad\u0000message")))),
        _changed_git(wire, GitOperationKind.CommitPending, _.mapObject(_.add("commitIdentity", Json.Null))),
        _changed_git(wire, GitOperationKind.PendingMerge, _.mapObject(_.add("commitIdentity", Json.obj("name" -> Json.fromString(" "), "email" -> Json.fromString("x"))))),
        _changed_git(wire, GitOperationKind.CreateCheckpoint, _.mapObject(_.add("commitIdentity", Json.obj("name" -> Json.fromString("x"), "email" -> Json.fromString("x"), "extra" -> Json.True)))),
        _changed_git(wire, GitOperationKind.Head, _.mapObject(_.add("commitIdentity", Json.obj("name" -> Json.fromString("x"), "email" -> Json.fromString("x"))))))
      When("V3 validates the fixed declarations at bootstrap")
      val results = invalid.map(_decode)
      Then("none of the invalid host fields create authority or disclose host paths in failure text")
      results.foreach(_.isFaillure shouldBe true)
      results.mkString should not include root.toString
    }

    "require matching V3 component workspace authority independently of process grants and reject semantic generic spoofing" in {
      Given("a V3 plan whose Git and SBT capabilities require the actual owner's Manage grant")
      val root = _directory()
      val wire = _v3_root(root)
      val invalid = Vector(_changed_entry(wire, "workspaceGrants", _.mapObject(_.add("access", Json.fromString("read-only")))),
        _changed_entry(wire, "workspaceGrants", _.mapObject(_.add("componentId", Json.fromString("org.example.Other")))),
        wire.mapObject(_.add("workspaceGrants", Json.arr())),
        _changed_entry(wire, "grants", _.mapObject(_.add("componentId", Json.fromString("bad owner!")))),
        _changed_git(wire, GitOperationKind.Head, _.mapObject(_.add("target", Json.fromString("unknown")))))
      val fixture = GitExecutionSpecSupport.create()
      try {
        When("the host sees mismatched authority or a tagged definition supplied as a generic program")
        val results = invalid.map(_decode)
        val spoof = RuntimeProcessExecutionPlan.createV3C(Vector(fixture.definitions.head), Map.empty,
          Vector.empty, Vector.empty, Vector.empty, Vector.empty)
        val withoutprocess = _decode(wire.mapObject(_.add("grants", Json.arr())))
        Then("mismatches and generic semantic spoofing fail while workspace-only declarations create no process grant")
        results.foreach(_.isFaillure shouldBe true)
        spoof.isFaillure shouldBe true
        withoutprocess.toOption.get.grants shouldBe empty
      } finally fixture.close()
    }

    "enforce each fixed selected-declaration count per workspace without introducing a selector-family field" in {
      Given("all five selected kinds at and above their individual per-workspace bounds")
      val root = _directory()
      val id = GitExecutionSpecSupport.workspaceId
      val cases = Vector(GitOperationKind.StageSelected -> 128, GitOperationKind.StageSelectedAll -> 256,
        GitOperationKind.CommitSelected -> 64, GitOperationKind.CheckSelectedIndex -> 64, GitOperationKind.SelectedIndexChanges -> 64)
      When("V3 assembly receives unique declarations at each bound and one additional unique selection")
      val results = cases.map { case (kind, bound) =>
        val declarations = (0 to bound).toVector.map { n =>
          GitExecutionSpecSupport.declaration(kind, root.resolve("remote.git").toString).copy(
            capability = _take(ProcessCapabilityId.parseC(s"selected-$n")),
            selectionId = Some(_take(GitSelectionId.parseC(s"choice-$n"))))
        }
        def _plan_(selected: Vector[GitRuntimeDeclaration]) = RuntimeProcessExecutionPlan.createV3C(Vector.empty, Map.empty,
          Vector(WorkspaceRuntimeDeclaration(id, root)), Vector.empty, Vector.empty, selected)
        _plan_(declarations.take(bound)).isSuccess -> _plan_(declarations).isFaillure
      }
      Then("each exact bound is admitted and each excess fails for that logical workspace")
      results shouldBe Vector.fill(5)(true -> true)
    }

    "round-trip fixed programs, exact templates, finite limits and independently selected component grants" in {
      Given("a trusted selected-tool plan with complete native-token argument shapes")
      val wire = _encoded
      When("the host codec decodes its wire representation")
      val restored = _take(RuntimeProcessExecutionPlanCodec.decode(wire))
      Then("no source executable, prefix, environment, bound or owner is lost")
      restored.programs.head._executable_location shouldBe "/runtime/bin/selected-tool"
      restored.programs.head.fixedArguments shouldBe Vector("-C", "/runtime/selected")
      restored.programs.head._environment.values shouldBe Map("PATH" -> "/runtime/bin")
      restored.programs.head.argumentPolicy.permittedArgumentTemplates shouldBe Set(ProcessArgumentTemplate(_slots))
      restored.programs.head.maximumLimits shouldBe _limits
      restored.grants.keySet shouldBe Set(_owner)
      restored.admission.admitC(ProcessExecutionRequest(_capability, Vector("show", "a" * 40))).isSuccess shouldBe false
    }

    "refuse unknown grants and duplicate owners before any runtime installation" in {
      Given("an encoded host plan and two invalid grant declarations")
      val grant = _root.hcursor.downField("grants").downArray.focus.get
      val unknown = _root.mapObject(_.add("grants", Json.arr(grant.mapObject(_.add("capabilities",
        Json.arr(Json.fromString("undeclared")))))))
      val duplicate = _root.mapObject(_.add("grants", Json.arr(grant, grant)))
      When("the host decodes both declarations")
      val results = Vector(_decode(unknown), _decode(duplicate))
      Then("neither plan creates an admission with undeclared or ambiguous authority")
      results.forall(_.isFaillure) shouldBe true
    }

    "refuse relative executables, free-text policy and absent or widened structural inputs" in {
      Given("a valid plan and malformed program objects")
      val invalid = Vector(_changed_program(_.mapObject(_.add("executable", Json.fromString("git")))),
        _changed_program(_.mapObject(_.add("argumentAdmission", Json.fromString("bounded-text")))),
        _changed_program(_.mapObject(_.add("argumentTemplates", Json.arr()))),
        _changed_program(_.mapObject(_.add("limits", Json.obj()))),
        _changed_program(_.mapObject(_.add("limits", _root.hcursor.downField("programs").downArray
          .downField("limits").focus.get.mapObject(_.add("executionTimeoutMillis", Json.fromLong(0)))))))
      When("each program is decoded at the natural host configuration boundary")
      val results = invalid.map(_decode)
      Then("incomplete templates or limits cannot grant arbitrary command selection")
      results.forall(_.isFaillure) shouldBe true
      results.mkString should not include "/runtime/selected"
    }

    "round-trip V2 generic programs separately from typed SBT declarations using one shared binding" in {
      Given("a selected real checkout with one generic template and one typed Test declaration")
      val root = _directory()
      val wire = _v2_root(root)
      When("V2 is decoded, encoded and decoded again")
      val first = _take(_decode(wire))
      val encoded = _take(RuntimeProcessExecutionPlanCodec.encode(first))
      val restored = _take(RuntimeProcessExecutionPlanCodec.decode(encoded))
      Then("only the generic program occupies programs and typed declarations retain host paths and independent grants")
      restored.schemaVersion shouldBe RuntimeProcessExecutionPlanCodec.SCHEMA_V2
      restored.programs.map(_.capability) shouldBe Vector(_capability)
      restored.sbtDeclarations shouldBe first.sbtDeclarations
      restored.workspaceDeclarations shouldBe first.workspaceDeclarations
      restored.workspaceGrants shouldBe first.workspaceGrants
      restored.grants shouldBe first.grants
      restored.sbtDeclarations.head.sbtExecutable shouldBe Paths.get("/usr/bin/true")
      val binding = _take(restored.workspaceAdmissionOption.get.bindingC(restored.sbtDeclarations.head.target))
      _take(binding.workingDirectoryC) shouldBe root.toRealPath()
      val tagged = _take(SbtProgramDefinition.createC(binding, SbtOperation.Test,
        restored.sbtDeclarations.head.capability, "selected-test", Paths.get("/usr/bin/true"), _limits, Map.empty))
      (tagged._sbt_metadata.get.binding eq binding) shouldBe true
      encoded.asInstanceOf[ConfigurationValue.StringValue].value should not include "javaExecutable"
      encoded.asInstanceOf[ConfigurationValue.StringValue].value should not include "launcher"
    }

    "reject unknown fields, duplicate keys, malformed identities and duplicate V2 declarations or grants" in {
      Given("a strict V2 host object with several ambiguous or widened declarations")
      val root = _directory()
      val wire = _v2_root(root)
      def _duplicate_(field: String): Json = wire.mapObject(_.add(field,
        Json.arr(wire.hcursor.downField(field).downArray.focus.get, wire.hcursor.downField(field).downArray.focus.get)))
      val invalid = Vector(
        wire.mapObject(_.add("unknown", Json.True)),
        _changed_entry(wire, "workspaces", _.mapObject(_.add("unknown", Json.True))),
        _changed_entry(wire, "workspaceGrants", _.mapObject(_.add("unknown", Json.True))),
        _changed_entry(wire, "sbtPrograms", _.mapObject(_.add("unknown", Json.True))),
        _changed_entry(wire, "sbtPrograms", _.mapObject(_.add("javaExecutable", Json.fromString("/usr/bin/true")))),
        _changed_entry(wire, "sbtPrograms", _.mapObject(_.add("launcher", Json.fromString("/host/sbt-launch.jar")))),
        _changed_entry(wire, "workspaces", _.mapObject(_.add("identity", Json.fromString("../checkout")))),
        _changed_entry(wire, "workspaceGrants", _.mapObject(_.add("componentId", Json.fromString("invalid owner!")))),
        _changed_entry(wire, "grants", _.mapObject(_.add("componentId", Json.fromString("invalid owner!")))),
        _changed_entry(wire, "sbtPrograms", _.mapObject(_.add("target", Json.fromString("unknown")))),
        _changed_entry(wire, "sbtPrograms", _.mapObject(_.add("operation", Json.fromString("publish")))),
        _changed_entry(wire, "sbtPrograms", _.mapObject(_.add("capability", Json.fromString("invalid capability!")))),
        _changed_entry(wire, "sbtPrograms", _.mapObject(_.add("limits", Json.obj()))),
        _changed_entry(wire, "sbtPrograms", _.mapObject(_.add("limits", wire.hcursor.downField("sbtPrograms").downArray
          .downField("limits").focus.get.mapObject(_.add("executionTimeoutMillis", Json.fromLong(0)))))),
        _changed_entry(wire, "sbtPrograms", _.mapObject(_.add("environment", Json.obj("PATH" -> Json.True)))),
        _changed_entry(wire, "workspaceGrants", _.mapObject(_.add("workspaceId", Json.fromString("unknown")))),
        _changed_entry(wire, "workspaceGrants", _.mapObject(_.add("access", Json.fromString("write")))),
        _duplicate_("workspaces"), _duplicate_("workspaceGrants"), _duplicate_("sbtPrograms"), _duplicate_("grants"),
        _changed_entry(wire, "grants", _.mapObject(_.add("capabilities", Json.arr(Json.fromString("selected-test"), Json.fromString("selected-test"))))),
        _changed_entry(wire, "grants", _.mapObject(_.add("capabilities", Json.arr(Json.fromString("unknown"))))))
      val duplicatekeys = ConfigurationValue.StringValue(wire.noSpaces.replace("\"schema\":", "\"schema\":\"duplicate\",\"schema\":"))
      When("the host decoder receives every invalid wire object and the raw duplicate-key JSON")
      val results = invalid.map(_decode) :+ RuntimeProcessExecutionPlanCodec.decode(duplicatekeys)
      Then("no ambiguous authority or compatibility route is installed and failure text reveals no checkout")
      results.foreach(_.isFaillure shouldBe true)
      results.mkString should not include root.toString
    }

    "reject unavailable workspace roots and invalid normal sbt executables with safe failures" in {
      Given("a real V2 checkout and a regular file with no executable permission")
      val root = _directory()
      val plainfile = Files.createFile(root.resolve("plain"))
      val wire = _v2_root(root)
      val invalid = Vector("relative", root.resolve("missing").toString, plainfile.toString).map(path =>
        _changed_entry(wire, "workspaces", _.mapObject(_.add("root", Json.fromString(path))))) ++
        Vector("sbt", root.resolve("missing-sbt").toString, root.toString, plainfile.toString).map(path =>
          _changed_entry(wire, "sbtPrograms", _.mapObject(_.add("sbtExecutable", Json.fromString(path)))))
      When("the selected host codec preflights each unavailable location")
      val results = invalid.map(_decode)
      Then("all locations fail without disclosing the host path")
      results.foreach(_.isFaillure shouldBe true)
      results.mkString should not include root.toString
    }

    "require same-owner Manage independently of process grants and allow ReadOnly declarations without build authority" in {
      Given("one V2 Test capability and host configurations with independent workspace and process grants")
      val wire = _v2_root(_directory())
      val readonly = _changed_entry(wire, "workspaceGrants", _.mapObject(_.add("access", Json.fromString("read-only"))))
      val wrongowner = _changed_entry(wire, "workspaceGrants", _.mapObject(_.add("componentId", Json.fromString("org.example.Other"))))
      val withoutprocess = wire.mapObject(_.add("grants", Json.arr()))
      val readonlyonly = readonly.mapObject(_.add("grants", Json.arr()))
      When("the host preflights missing Manage, a different owner and workspace-only authority")
      val denied = Vector(_decode(readonly), _decode(wrongowner), _decode(wire.mapObject(_.add("workspaceGrants", Json.arr()))))
      val workspaceonly = _take(_decode(withoutprocess))
      val readplan = _take(_decode(readonlyonly))
      Then("a process grant cannot widen workspace access and workspace permission never creates a process grant")
      denied.foreach(_.isFaillure shouldBe true)
      workspaceonly.grants shouldBe empty
      readplan.grants shouldBe empty
      readplan.workspaceGrants.head.permission shouldBe WorkspacePermission.ReadOnly
      readplan.workspaceAdmissionOption.isDefined shouldBe true
    }
  }
}
