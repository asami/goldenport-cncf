package org.goldenport.cncf.processexecution

import io.circe.Json
import io.circe.parser.parse
import java.nio.file.{Files, Path, Paths}
import java.util.Comparator
import scala.jdk.CollectionConverters.*
import scala.util.Using
import org.goldenport.Consequence
import org.goldenport.cncf.component.ComponentId
import org.goldenport.cncf.observability.ConclusionDiagnostics
import org.goldenport.cncf.workspace.*
import org.goldenport.configuration.ConfigurationValue
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalacheck.{Gen, Prop, Test}

/*
 * @since   Oct.  7, 2026
 *  version Oct. 29, 2026
 * @version Nov.  6, 2026
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

  private def _v5_root(root: Path): Json = {
    val wire = _v3_root(root)
    val git = wire.hcursor.get[Vector[Json]]("gitPrograms").toOption.get
    val selected = git.filter(x => GitOperationKind.values.find(_.wireName == x.hcursor.get[String]("operation").toOption.get).get.selected)
    val source = git.find(_.hcursor.get[String]("operation").toOption.contains("commit-selected")).get
    val fields = Set("target", "gitExecutable", "localBranch", "remote", "remoteBranch", "endpoint", "commitIdentity", "limits", "environment")
    val repository = Json.fromJsonObject(source.asObject.get.filter { case (name, _) => fields.contains(name) }
      .add("capability", Json.fromString("repository")).add("identity", Json.fromString("repository")))
    val removed = git.filterNot(selected.contains).map(_.hcursor.get[String]("capability").toOption.get).toSet
    val grants = wire.hcursor.get[Vector[Json]]("grants").toOption.get.map { grant =>
      grant.mapObject(_.add("capabilities", _json_strings(grant.hcursor.get[Vector[String]]("capabilities").toOption.get
        .filterNot(removed.contains) :+ "repository")))
    }
    wire.mapObject(_.add("schema", Json.fromString(RuntimeProcessExecutionPlanCodec.SCHEMA_V5))
      .add("gitPrograms", Json.fromValues(selected)).add("gitRepositories", Json.arr(repository)).add("grants", Json.fromValues(grants)))
  }

  private def _json_strings(values: Vector[String]): Json = Json.fromValues(values.map(Json.fromString))
  private def _repository_name(wire: Json, capability: String, identity: String): Json = {
    val changed = _changed_entry(wire, "gitRepositories", _.mapObject(_.add("capability", Json.fromString(capability))
      .add("identity", Json.fromString(identity))))
    _changed_entry(changed, "grants", grant => grant.mapObject(_.add("capabilities",
      _json_strings(grant.hcursor.get[Vector[String]]("capabilities").toOption.get.map(x => if (x == "repository") capability else x)))))
  }

  private def _without_stream_limits(entry: Json): Json = entry.mapObject(fields => fields.add("limits",
    fields("limits").get.mapObject(_.remove("stdinBytes").remove("stdoutBytes").remove("stderrBytes"))))

  private def _with_v6_root(body: Json => Unit): Unit = {
    val root = _directory()
    try {
      val v5 = _v5_root(root)
      val target = v5.hcursor.downField("workspaces").downArray.get[String]("identity").toOption.get
      val limits = v5.hcursor.downField("sbtPrograms").downArray.downField("limits").focus.get
      val check = Json.obj("target" -> Json.fromString(target), "checkIdentity" -> Json.fromString("exact content"),
        "purpose" -> Json.fromString("behavior"),
        "adapter" -> Json.fromString("fixed-process"), "capability" -> Json.fromString("selected-check"),
        "identity" -> Json.fromString("selected-check"), "executable" -> Json.fromString("/usr/bin/grep"),
        "fixedArguments" -> Json.arr(Json.fromString("-Fx"), Json.fromString("expected"), Json.fromString("value.txt")),
        "limits" -> limits, "environment" -> Json.obj("CHECK_PROFILE" -> Json.fromString("declared")))
      val wire = v5.mapObject(_.add("schema", Json.fromString(RuntimeProcessExecutionPlanCodec.SCHEMA_V6))
        .add("validationChecks", Json.arr(check)))
      body(_changed_entry(wire, "grants", grant => grant.mapObject(_.add("capabilities",
        _json_strings(grant.hcursor.get[Vector[String]]("capabilities").toOption.get :+ "selected-check")))))
    } finally Using.resource(Files.walk(root)) { stream =>
      stream.sorted(Comparator.reverseOrder[Path]()).iterator().asScala.foreach(path => Files.deleteIfExists(path))
    }
  }

  private def _check_only_v6(wire: Json): Json =
    _changed_entry(wire.mapObject(_.add("programs", Json.arr()).add("sbtPrograms", Json.arr())
      .add("gitPrograms", Json.arr()).add("gitRepositories", Json.arr())), "grants", grant =>
      grant.mapObject(_.add("capabilities", Json.arr(Json.fromString("selected-check")))))

  "V6 declared validation check host schema" should {
    "round-trip exact fixed checks with all V5 families and workspace admission" in _with_v6_root { wire =>
      Given("an additive V6 declaration with generic, SBT, selected Git, repository and validation check entries")
      When("the strict host codec decodes, encodes and decodes it")
      val first = _take(_decode(wire))
      val encoded = _take(RuntimeProcessExecutionPlanCodec.encode(first))
      val restored = _take(RuntimeProcessExecutionPlanCodec.decode(encoded))
      val declaration = restored.validationCheckDeclarations.head
      val binding = _take(restored.workspaceAdmissionOption.get.bindingC(declaration.target))
      Then("all prior declaration forms survive and the check remains exact fixed-process host configuration")
      restored.schemaVersion shouldBe RuntimeProcessExecutionPlanCodec.SCHEMA_V6
      restored.programs.map(_.capability) shouldBe first.programs.map(_.capability)
      restored.sbtDeclarations shouldBe first.sbtDeclarations
      restored.gitDeclarations shouldBe first.gitDeclarations
      restored.gitRepositoryDeclarations shouldBe first.gitRepositoryDeclarations
      restored.workspaceDeclarations shouldBe first.workspaceDeclarations
      restored.workspaceGrants shouldBe first.workspaceGrants
      restored.grants shouldBe first.grants
      restored.validationCheckDeclarations shouldBe first.validationCheckDeclarations
      declaration.checkIdentity.value shouldBe "exact content"
      declaration.purpose shouldBe ValidationCheckPurpose.Behavior
      declaration.adapter shouldBe "fixed-process"
      declaration.fixedArguments shouldBe Vector("-Fx", "expected", "value.txt")
      declaration.environment shouldBe Map("CHECK_PROFILE" -> "declared")
      _take(binding.workingDirectoryC) shouldBe restored.workspaceDeclarations.find(_.identity == declaration.target).get.root.toRealPath()
      restored.admission.admitC(ProcessExecutionRequest(declaration.capability)).isFaillure shouldBe true
      val json = parse(encoded.asInstanceOf[ConfigurationValue.StringValue].value).toOption.get
      json.asObject.get.keys.toSet shouldBe wire.asObject.get.keys.toSet
      json.hcursor.downField("validationChecks").downArray.focus.get.asObject.get.keys.toSet shouldBe
        Set("target", "checkIdentity", "purpose", "adapter", "capability", "identity", "executable", "fixedArguments", "limits", "environment")
    }

    "encode and decode only the exact explicit compilation, behavior and lint purposes" in _with_v6_root { wire =>
      Given("the three tool-independent host purpose values")
      ValidationCheckPurpose.values.foreach { purpose =>
        val selected = _changed_entry(wire, "validationChecks", _.mapObject(_.add("purpose", Json.fromString(purpose.wireName))))
        When("the host declaration round-trips through the unpublished initial V6 contract")
        val plan = _take(_decode(selected))
        val encoded = _take(RuntimeProcessExecutionPlanCodec.encode(plan))
        val json = parse(encoded.asInstanceOf[ConfigurationValue.StringValue].value).toOption.get
        val restored = _take(RuntimeProcessExecutionPlanCodec.decode(encoded))
        Then("the purpose is retained exactly without inferring it from the executable or a claimed result")
        plan.validationCheckDeclarations.head.purpose shouldBe purpose
        restored.validationCheckDeclarations.head.purpose shouldBe purpose
        json.hcursor.downField("validationChecks").downArray.get[String]("purpose").toOption shouldBe Some(purpose.wireName)
      }
      When("an unknown purpose or alias is declared")
      val rejected = Vector("compile", "Compilation", "Behavior", "Lint", "lint ", "test", "unknown", "").map(value =>
        _decode(_changed_entry(wire, "validationChecks", _.mapObject(_.add("purpose", Json.fromString(value))))))
      Then("there is no implicit purpose or alias")
      rejected.foreach(_.isFaillure shouldBe true)
    }

    "reject missing, unknown, extra and explicitly null check schema fields" in _with_v6_root { wire =>
      Given("one strict V6 check with a mandatory explicit fixed-process adapter")
      val names = Set("target", "checkIdentity", "purpose", "adapter", "capability", "identity", "executable", "fixedArguments", "limits", "environment")
      val missing = names.toVector.map(name => _changed_entry(wire, "validationChecks", _.mapObject(_.remove(name))))
      val nulls = names.toVector.map(name => _changed_entry(wire, "validationChecks", _.mapObject(_.add(name, Json.Null))))
      val invalid = missing ++ nulls ++ Vector(
        wire.mapObject(_.remove("validationChecks")), wire.mapObject(_.add("validationChecks", Json.Null)),
        wire.mapObject(_.add("extra", Json.True)),
        _changed_entry(wire, "validationChecks", _.mapObject(_.add("extra", Json.True))),
        _changed_entry(wire, "validationChecks", _.mapObject(_.add("adapter", Json.fromString("unknown")))),
        _changed_entry(wire, "validationChecks", _.mapObject(_.add("adapter", Json.fromString("FIXED-PROCESS")))),
        _changed_entry(wire, "validationChecks", _.mapObject(_.add("checkIdentity", Json.fromString(" exact content")))),
        _changed_entry(wire, "validationChecks", _.mapObject(_.add("checkIdentity", Json.fromString("exact\ncontent")))),
        _changed_entry(wire, "validationChecks", _.mapObject(_.add("executable", Json.fromString("grep")))),
        _changed_entry(wire, "validationChecks", _.mapObject(_.add("executable", Json.fromString("/bin/sh")))),
        _changed_entry(wire, "validationChecks", _.mapObject(_.add("target", Json.fromString("unknown")))))
      When("each malformed host configuration is decoded")
      val results = invalid.map(_decode)
      Then("none installs a fallback adapter, widened caller target or incomplete declaration")
      results.foreach(_.isFaillure shouldBe true)
      Vector("programs", "grants", "workspaces", "workspaceGrants", "sbtPrograms", "gitPrograms", "gitRepositories")
        .foreach(name => _decode(wire.mapObject(_.remove(name))).isFaillure shouldBe true)
      _decode(wire.mapObject(_.add("schema", Json.fromString(RuntimeProcessExecutionPlanCodec.SCHEMA_V5)))).isFaillure shouldBe true
    }

    "reject duplicate logical checks and capability or safe identity collisions across program families" in _with_v6_root { wire =>
      Given("one declared check alongside every existing semantic family")
      val check = wire.hcursor.downField("validationChecks").downArray.focus.get
      val duplicate = check.mapObject(_.add("capability", Json.fromString("another-check")).add("identity", Json.fromString("another-check")))
      val capabilities = Vector("programs", "sbtPrograms", "gitPrograms", "gitRepositories").map(name =>
        wire.hcursor.downField(name).downArray.get[String]("capability").toOption.get) :+ "repository-head"
      val identities = Vector("programs", "sbtPrograms", "gitPrograms", "gitRepositories").map(name =>
        wire.hcursor.downField(name).downArray.get[String]("identity").toOption.get) :+ "repository-head"
      val invalid = Vector(wire.mapObject(_.add("validationChecks", Json.arr(check, duplicate)))) ++
        capabilities.map(value => _changed_entry(wire, "validationChecks", _.mapObject(_.add("capability", Json.fromString(value))))) ++
        identities.map(value => _changed_entry(wire, "validationChecks", _.mapObject(_.add("identity", Json.fromString(value)))))
      When("the same policy assembly receives each ambiguous declaration")
      val results = invalid.map(_decode)
      Then("logical checks, public and expanded capabilities, and safe identities remain unique")
      results.foreach(_.isFaillure shouldBe true)
    }

    "require same-owner Manage grants for check capabilities independently of other program families" in _with_v6_root { wire =>
      Given("a check-only V6 plan and independent workspace and process permissions")
      val only = _check_only_v6(wire)
      val invalid = Vector(only.mapObject(_.add("workspaceGrants", Json.arr())),
        _changed_entry(only, "workspaceGrants", _.mapObject(_.add("access", Json.fromString("read-only")))),
        _changed_entry(only, "workspaceGrants", _.mapObject(_.add("componentId", Json.fromString("org.example.Other")))))
      When("the host preflights each check grant")
      val denied = invalid.map(_decode)
      Then("the capability grant cannot create workspace Manage authority")
      denied.foreach(_.isFaillure shouldBe true)
      _decode(only).isSuccess shouldBe true
      val workspaceonly = _take(_decode(only.mapObject(_.add("grants", Json.arr()))))
      workspaceonly.grants shouldBe empty
      workspaceonly.validationCheckDeclarations should have size 1
    }

    "preserve check stream omissions and explicit values through common I/O default activation" in _with_v6_root { wire =>
      Given("a check declaration omitting all stream bounds except explicit stdout")
      val omitted = _changed_entry(wire, "validationChecks", entry => _without_stream_limits(entry).mapObject(obj =>
        obj.add("limits", obj("limits").get.mapObject(_.add("stdoutBytes", Json.fromLong(2097152L))))))
      When("the plan round-trips before resolving the common default")
      val plan = _take(_decode(omitted))
      val encoded = _take(RuntimeProcessExecutionPlanCodec.encode(plan))
      val json = parse(encoded.asInstanceOf[ConfigurationValue.StringValue].value).toOption.get
      val limits = json.hcursor.downField("validationChecks").downArray.downField("limits").focus.get.asObject.get
      Then("only omitted fields remain omitted and the explicit override remains exact")
      limits.contains("stdinBytes") shouldBe false
      limits.contains("stderrBytes") shouldBe false
      limits("stdoutBytes").get.asNumber.flatMap(_.toLong) shouldBe Some(2097152L)
      When("the decoded plan resolves its common I/O default")
      val activated = _take(RuntimeProcessExecutionPlanCodec.resolveIoDefaultsC(
        _take(RuntimeProcessExecutionPlanCodec.decode(encoded)), 23L))
      Then("activation fills only omitted bounds and preserves the declared grants and workspaces")
      activated.validationCheckDeclarations.head.limits shouldBe _limits.copy(
        stdinBytes = Some(23L), stdoutBytes = Some(2097152L), stderrBytes = Some(23L))
      activated.grants shouldBe plan.grants
      activated.workspaceDeclarations shouldBe plan.workspaceDeclarations
      activated.workspaceGrants shouldBe plan.workspaceGrants
      activated._io_default_omissions shouldBe empty
      When("a check declaration omits every stream bound")
      val allomitted = _take(_decode(_changed_entry(wire, "validationChecks", _without_stream_limits)))
      Then("the ordinary decode retains the built-in default until activation")
      allomitted.validationCheckDeclarations.head.limits.stdoutBytes shouldBe
        Some(org.goldenport.cncf.config.IoDefaultParameters.BUILTIN_MAXIMUM_BYTES.toLong)
    }
  }

  "E4 Omitted host stream limits" should afterWord(
    "in spec:process-execution-runtime, example:E4, rules:R5, phase:sync-runtime-20261103-01") {
    "retain generic and typed stream omissions until activation resolves the common default" in {
      Given("docs/spec/process-execution-runtime.md R5 E4: generic, SBT, Git and V5 repository declarations omit all three streams")
      val genericwire = _changed_program(_without_stream_limits)
      val repositorywire = _changed_entry(_v5_root(_directory()), "gitRepositories", _without_stream_limits)
      val sbtwire = _changed_entry(_v2_root(_directory()), "sbtPrograms", _without_stream_limits)
      val gitwire = _v3_root(_directory()).mapObject(obj => obj.add("gitPrograms",
        Json.fromValues(obj("gitPrograms").get.asArray.get.map(_without_stream_limits))))
      When("the ordinary host codec decodes both plans")
      val generic = _take(_decode(genericwire))
      val repository = _take(_decode(repositorywire))
      val sbt = _take(_decode(sbtwire))
      val git = _take(_decode(gitwire))
      val defaultbytes = org.goldenport.cncf.config.IoDefaultParameters.BUILTIN_MAXIMUM_BYTES.toLong
      val limits = Vector(generic.programs.head.maximumLimits, repository.gitRepositoryDeclarations.head.limits,
        sbt.sbtDeclarations.head.limits, git.gitDeclarations.head.limits)
      Then("only the omitted stream quantities acquire the finite one MiB default")
      defaultbytes shouldBe 1048576L
      limits.foreach { value =>
        value.stdinBytes shouldBe Some(defaultbytes)
        value.stdoutBytes shouldBe Some(defaultbytes)
        value.stderrBytes shouldBe Some(defaultbytes)
        value.requireFiniteC.isSuccess shouldBe true
      }
      generic.programs.head.maximumLimits shouldBe _limits.copy(stdinBytes = Some(defaultbytes), stdoutBytes = Some(defaultbytes),
        stderrBytes = Some(defaultbytes))
      for ((plan, field) <- Vector(generic -> "programs", repository -> "gitRepositories", sbt -> "sbtPrograms", git -> "gitPrograms")) {
        When("the unresolved declaration round-trips before the runtime supplies its common value")
        val encoded = _take(RuntimeProcessExecutionPlanCodec.encode(plan))
        val wire = parse(encoded.asInstanceOf[ConfigurationValue.StringValue].value).toOption.get
        val encodedlimits = wire.hcursor.downField(field).downArray.downField("limits").focus.get
        val restored = _take(RuntimeProcessExecutionPlanCodec.decode(encoded))
        Then("the wire preserves precisely the three omissions and keeps the finite provisional meaning")
        encodedlimits.asObject.get.size shouldBe 8
        Set("stdinBytes", "stdoutBytes", "stderrBytes").foreach(name => encodedlimits.asObject.get.contains(name) shouldBe false)
        restored.programs.map(_.maximumLimits) shouldBe plan.programs.map(_.maximumLimits)
        restored.gitRepositoryDeclarations shouldBe plan.gitRepositoryDeclarations
        When("activation resolves the common byte policy")
        val activated = _take(RuntimeProcessExecutionPlanCodec.resolveIoDefaultsC(restored, 23L))
        val activatedwire = parse(_take(RuntimeProcessExecutionPlanCodec.encode(activated))
          .asInstanceOf[ConfigurationValue.StringValue].value).toOption.get
        val activatedlimits = activatedwire.hcursor.downField(field).downArray.downField("limits").focus.get
        Then("all three streams become explicit finite values and authority remains unchanged")
        activatedlimits.asObject.get.size shouldBe 11
        Set("stdinBytes", "stdoutBytes", "stderrBytes").foreach(name =>
          activatedlimits.hcursor.get[Long](name).toOption shouldBe Some(23L))
        activated._io_default_omissions shouldBe empty
        activated.grants shouldBe plan.grants
        activated.workspaceDeclarations shouldBe plan.workspaceDeclarations
        activated.workspaceGrants shouldBe plan.workspaceGrants
      }
    }
  }

  "E5 Explicit host stream limits" should afterWord(
    "in spec:process-execution-runtime, example:E5, rules:R5, phase:sync-runtime-20261103-01") {
    "honor smaller and larger explicit limits independently of omitted peer streams" in {
      Given("docs/spec/process-execution-runtime.md R5 E5: finite explicit stream overrides above and below one MiB")
      val repositorywire = _v5_root(_directory())
      for (field <- Vector("stdinBytes", "stdoutBytes", "stderrBytes"); size <- Vector(32L, 1048576L, 2097152L)) {
        def _override_(entry: Json): Json = _without_stream_limits(entry).mapObject(fields => fields.add("limits",
          fields("limits").get.mapObject(_.add(field, Json.fromLong(size)))))
        When("each generic or normal V5 declaration supplies one explicit stream and omits the other")
        val generic = _take(RuntimeProcessExecutionPlanCodec.resolveIoDefaultsC(
          _take(_decode(_changed_program(_override_))), 17L)).programs.head.maximumLimits
        val repository = _take(RuntimeProcessExecutionPlanCodec.resolveIoDefaultsC(
          _take(_decode(_changed_entry(repositorywire, "gitRepositories", _override_))), 17L))
          .gitRepositoryDeclarations.head.limits
        Then("the explicit quantity survives and its omitted peer alone uses the CNCF default")
        Vector(generic, repository).foreach { value =>
          value.stdinBytes shouldBe Some(if (field == "stdinBytes") size else 17L)
          value.stdoutBytes shouldBe Some(if (field == "stdoutBytes") size else 17L)
          value.stderrBytes shouldBe Some(if (field == "stderrBytes") size else 17L)
        }
      }
    }
  }

  "E6 Host stream default strictness" should afterWord(
    "in spec:process-execution-runtime, example:E6, rules:R5, phase:sync-runtime-20261103-01") {
    "reject missing other limits, unknown fields and every malformed explicit quantity" in {
      Given("docs/spec/process-execution-runtime.md R5 E6: all eleven limits retain strict finite integer validation")
      val source = _root.hcursor.downField("programs").downArray.downField("limits").focus.get
      val names = source.asObject.get.keys.toVector
      val malformed = Vector(Json.Null, Json.True, Json.fromString("1024"), Json.fromDoubleOrNull(1.5),
        Json.fromLong(0L), Json.fromLong(-1L), Json.fromBigInt(BigInt(Long.MaxValue) + 1))
      val missing = names.filterNot(Set("stdinBytes", "stdoutBytes", "stderrBytes")).map(name => source.mapObject(_.remove(name)))
      val invalid = missing ++ names.flatMap(name => malformed.map(value => source.mapObject(_.add(name, value)))) ++
        Vector(source.mapObject(_.add("unknown", Json.fromLong(1L))), Json.Null, Json.True, Json.arr())
      val repositorywire = _v5_root(_directory())
      When("the ordinary generic and normal V5 host paths decode each invalid limit object")
      val results = invalid.flatMap { limits =>
        def _replace_(entry: Json): Json = entry.mapObject(_.add("limits", limits))
        Vector(_decode(_changed_program(_replace_)), _decode(_changed_entry(repositorywire, "gitRepositories", _replace_)))
      }
      Then("omission defaults do not admit unknown fields, missing mandatory quantities or invalid explicit streams")
      results.foreach(_.isFaillure shouldBe true)
    }
  }

  "E7 Stream defaults preserve limit tightening" should afterWord(
    "in spec:process-execution-runtime, example:E7, rules:R5, phase:sync-runtime-20261103-01") {
    "leave empty overrides absent and retain grant and request narrowing" in {
      Given("docs/spec/process-execution-runtime.md R5 E7: a normalized host maximum and independently optional grants")
      val maximum = _take(_decode(_changed_program(_without_stream_limits))).programs.head.maximumLimits
      val grant = ProcessExecutionLimits(stdinBytes = Some(128L), stdoutBytes = Some(256L))
      val request = ProcessExecutionLimits(stdoutBytes = Some(64L))
      When("the existing finite-limit algebra resolves empty overrides and explicit tightening")
      val unchanged = _take(ProcessExecutionLimits.effectiveC(maximum, ProcessExecutionLimits.empty, ProcessExecutionLimits.empty))
      val narrowed = _take(ProcessExecutionLimits.effectiveC(maximum, grant, request))
      Then("wire defaults never become implicit grant or request overrides and widening still fails")
      ProcessExecutionLimits().stdinBytes shouldBe None
      ProcessExecutionLimits.empty.stdoutBytes shouldBe None
      unchanged shouldBe maximum
      narrowed shouldBe maximum.copy(stdinBytes = Some(128L), stdoutBytes = Some(64L))
      ProcessExecutionLimits.effectiveC(maximum, grant, ProcessExecutionLimits(stdinBytes = Some(129L))).isFaillure shouldBe true
      ProcessExecutionLimits.effectiveC(maximum, ProcessExecutionLimits(stdoutBytes = Some(2097152L)),
        ProcessExecutionLimits.empty).isFaillure shouldBe true
    }
  }

  "Explicit selected committed-tree authority" should {
    "round-trip a sole read-only TreeEntries declaration through V3 and V5 without granting repository-family access" in {
      Given("an explicit finite selected-tree host declaration with no generic, SBT or repository-family grant")
      val fixture = GitExecutionSpecSupport.create()
      try {
        val wires = Vector(_v3_root(fixture.checkout), _v5_root(fixture.checkout)).map { wire =>
          val entry = wire.hcursor.get[Vector[Json]]("gitPrograms").toOption.get.find(
            _.hcursor.get[String]("operation").toOption.contains("tree-entries")).get
          val minimal = wire.mapObject(_.add("programs", Json.arr()).add("sbtPrograms", Json.arr())
            .add("gitPrograms", Json.arr(entry)).add("grants", Json.arr(Json.obj(
              "componentId" -> Json.fromString(_owner.name),
              "capabilities" -> Json.arr(Json.fromString("git-tree-entries"))))))
          val selected = if (wire.asObject.get.contains("gitRepositories"))
            minimal.mapObject(_.add("gitRepositories", Json.arr())) else minimal
          _changed_entry(selected, "workspaceGrants", _.mapObject(_.add("access", Json.fromString("read-only"))))
        }
        When("both strict host codecs install and restore that read-only selection")
        val plans = wires.map(wire => _take(RuntimeProcessExecutionPlanCodec.decode(
          _take(RuntimeProcessExecutionPlanCodec.encode(_take(_decode(wire)))))))
        Then("only the explicit read operation resolves and its literal ordered selection remains host-owned")
        plans.foreach { plan =>
          plan.gitDeclarations.map(_.operation) shouldBe Vector(GitOperationKind.TreeEntries)
          plan.gitDeclarations.head.selectedPaths.map(_.value) shouldBe Vector("selected.txt")
          val context = GitExecutionSpecSupport.contextFor(fixture.root, plan.workspaceAdmissionOption, Some(plan.admission),
            fixture.profile.driver, _owner)
          val admitted = _take(GitExecutionAdmission.resolveC(context, fixture.target,
            GitOperation.TreeEntries(GitExecutionSpecSupport.objectId, GitExecutionSpecSupport.selectionId)))
          admitted.effectiveArguments shouldBe GitProgramDefinition.prefix ++ Vector("--literal-pathspecs", "ls-tree",
            "--full-tree", "-z", GitExecutionSpecSupport.objectId.value, "--", "selected.txt")
          GitExecutionAdmission.resolveC(context, fixture.target, GitOperation.Head).isFaillure shouldBe true
          GitExecutionAdmission.resolveC(context, fixture.target,
            GitOperation.StageSelectedAll(GitExecutionSpecSupport.selectionId)).isFaillure shouldBe true
        }
        fixture.profile.driver.executions shouldBe empty
      } finally fixture.close()
    }

    "refuse TreeEntries under a repository-family grant alone and under an unknown selection" in {
      Given("a V5 repository family with no selected declarations and an independently registered explicit selection")
      val fixture = GitExecutionSpecSupport.create()
      try {
        val wire = _v5_root(fixture.checkout)
        val family = _changed_entry(wire.mapObject(_.add("gitPrograms", Json.arr())), "grants", grant =>
          grant.mapObject(_.add("capabilities", _json_strings(grant.hcursor.get[Vector[String]]("capabilities").toOption.get
            .filterNot(_.startsWith("git-"))))))
        val plan = _take(_decode(family))
        val context = GitExecutionSpecSupport.contextFor(fixture.root, plan.workspaceAdmissionOption, Some(plan.admission),
          fixture.profile.driver, _owner)
        When("typed admission requests the newly added operation without its own declaration or with another selection")
        val absent = GitExecutionAdmission.resolveC(context, fixture.target,
          GitOperation.TreeEntries(GitExecutionSpecSupport.objectId, GitExecutionSpecSupport.selectionId))
        val explicit = _take(_decode(wire))
        val selectedcontext = GitExecutionSpecSupport.contextFor(fixture.root, explicit.workspaceAdmissionOption, Some(explicit.admission),
          fixture.profile.driver, _owner)
        val unknown = GitExecutionAdmission.resolveC(selectedcontext, fixture.target,
          GitOperation.TreeEntries(GitExecutionSpecSupport.objectId, GitSelectionId.parseC("unknown").toOption.get))
        Then("adding an enum member grants no selected authority and neither denied intent starts a process")
        absent.isFaillure shouldBe true
        unknown.isFaillure shouldBe true
        fixture.profile.driver.executions shouldBe empty
      } finally fixture.close()
    }
  }

  "E3 Repository Git V5 codec" must afterWord(
    "in spec:typed-git-execution, example:E3, rules:R1,R2,R4,R5,R6, phase:GIT-ROUTE-20261026-01") {
    "round-trip one public repository entry alongside unchanged generic, SBT and selected declarations" in {
      Given("R1 R4 R5: a V5 host with one repository family, independent selected scopes and generic and SBT programs")
      val wire = _v5_root(_directory())
      When("the host decodes and encodes the public configuration")
      val plan = _take(_decode(wire))
      val encoded = _take(RuntimeProcessExecutionPlanCodec.encode(plan)).asInstanceOf[ConfigurationValue.StringValue]
      val restored = _take(RuntimeProcessExecutionPlanCodec.decode(encoded))
      Then("derived operations and grants remain internal and only one repository declaration is serialized")
      parse(encoded.value).toOption.get shouldBe wire
      plan.gitRepositoryDeclarations should have size 1
      plan.gitDeclarations.map(_.operation).toSet shouldBe GitOperationKind.values.filter(_.selected).toSet
      plan.grants(_owner).map(_.capability.print) should contain("repository")
      plan.grants(_owner).map(_.capability.print).exists(_.startsWith("repository-")) shouldBe false
      restored.gitRepositoryDeclarations shouldBe plan.gitRepositoryDeclarations
      restored.grants shouldBe plan.grants
      _root.asObject.get.contains("gitRepositories") shouldBe false
      _v2_root(_directory()).asObject.get.contains("gitRepositories") shouldBe false
      _v3_root(_directory()).asObject.get.contains("gitRepositories") shouldBe false
    }

    "reject every missing or null family field, unknown scope fields and duplicate JSON keys" in {
      Given("R5: ten required repository fields, optional executable override and strict V5 root")
      val wire = _v5_root(_directory())
      val repository = wire.hcursor.downField("gitRepositories").downArray.focus.get
      val names = repository.asObject.get.keys.toVector.filterNot(_ == "gitExecutable")
      val invalid = names.flatMap(name => Vector(
        _changed_entry(wire, "gitRepositories", _.mapObject(_.remove(name))),
        _changed_entry(wire, "gitRepositories", _.mapObject(_.add(name, Json.Null))))) ++
        Vector("operation", "arguments", "selectionId", "selectedPath", "selectedPaths", "commitMessage").map(name =>
          _changed_entry(wire, "gitRepositories", _.mapObject(_.add(name, Json.Null)))) ++ Vector(
          wire.mapObject(_.remove("gitRepositories")), wire.mapObject(_.add("gitRepositories", Json.Null)),
          wire.mapObject(_.add("unknown", Json.Null)),
          _changed_entry(wire, "gitRepositories", _.mapObject(_.add("gitExecutable", Json.Null))),
          _changed_entry(wire, "gitRepositories", _.mapObject(_.add("gitExecutable", Json.fromInt(1)))),
          _changed_entry(wire, "gitRepositories", _.mapObject(_.add("commitIdentity", Json.obj(
            "name" -> Json.fromString("Fixture"), "email" -> Json.fromString("fixture@example.invalid"), "extra" -> Json.Null)))))
      val duplicate = ConfigurationValue.StringValue(wire.noSpaces.replace("\"gitRepositories\":", "\"gitRepositories\":[],\"gitRepositories\":"))
      val duplicatefield = ConfigurationValue.StringValue(wire.noSpaces.replace("\"identity\":\"repository\"",
        "\"identity\":\"repository\",\"identity\":\"repository\""))
      When("the strict decoder receives incomplete, extended or ambiguous authority objects")
      val results = invalid.map(_decode) ++ Vector(duplicate, duplicatefield).map(RuntimeProcessExecutionPlanCodec.decode)
      Then("none produces a usable host plan")
      results.foreach(_.isFaillure shouldBe true)
    }

    "preserve omission of the default executable while rejecting unavailable Git and invalid explicit overrides" in {
      Given("R6: V5 with no executable override and a valid configured PATH")
      val root = _directory()
      val explicit = _v5_root(root)
      val wire = _changed_entry(explicit, "gitRepositories", _.mapObject(_.remove("gitExecutable")))
      val missing = _changed_entry(wire, "gitRepositories", _.mapObject(_.add("environment",
        Json.obj("PATH" -> Json.fromString(root.toString)))))
      val invalid = _changed_entry(explicit, "gitRepositories", _.mapObject(_.add("gitExecutable",
        Json.fromString(root.resolve("missing-git").toString))))
      When("the public default plan round-trips and unavailable host choices are decoded")
      val plan = _take(_decode(wire))
      val encoded = _take(RuntimeProcessExecutionPlanCodec.encode(plan)).asInstanceOf[ConfigurationValue.StringValue]
      val failures = Vector(missing, invalid).map(_decode)
      Then("the resolved internal executable never becomes a mandatory serialized override")
      plan.gitRepositoryDeclarations.head.gitExecutable shouldBe None
      parse(encoded.value).toOption.get shouldBe wire
      failures.foreach(_.isFaillure shouldBe true)
      failures.head.toString should include("git")
      failures.head.toString should include("PATH")
    }

    "reject duplicate targets, namespace collisions, nonselected overlap and forged internal grants" in {
      Given("R2 R4 R5: a repository family with explicit selected declarations")
      val root = _directory()
      val wire = _v5_root(root)
      val repository = wire.hcursor.downField("gitRepositories").downArray.focus.get
      val explicit = _v3_root(root).hcursor.get[Vector[Json]]("gitPrograms").toOption.get
        .find(_.hcursor.get[String]("operation").toOption.contains("root")).get
      val overlap = wire.mapObject(_.add("gitPrograms", Json.fromValues(
        wire.hcursor.get[Vector[Json]]("gitPrograms").toOption.get :+ explicit)))
      val spoof = _changed_entry(wire, "grants", grant => grant.mapObject(_.add("capabilities",
        _json_strings(grant.hcursor.get[Vector[String]]("capabilities").toOption.get :+ "repository-root"))))
      val invalid = Vector(overlap, spoof,
        wire.mapObject(_.add("gitRepositories", Json.arr(repository, repository))),
        _repository_name(wire, "selected-token", "repository"),
        _repository_name(wire, "repository", "selected-token"),
        _repository_name(wire, "v3-test", "repository"),
        _repository_name(wire, "repository", "v3-test"),
        _changed_entry(wire, "gitPrograms", _.mapObject(_.add("capability", Json.fromString("repository-root")))),
        _changed_entry(wire, "gitPrograms", _.mapObject(_.add("identity", Json.fromString("repository-root")))),
        _changed_entry(wire, "gitPrograms", _.mapObject(_.add("identity", Json.fromString("repository")))))
      When("the host attempts to install ambiguous public or internal authorities")
      val results = invalid.map(_decode)
      Then("family expansion cannot hide collisions or accept public grants to generated capabilities")
      results.foreach(_.isFaillure shouldBe true)
    }

    "bound safe family names and retain strict executable, identity, environment and limit validation" in {
      Given("R5: safe family bases of at most 32 characters and existing closed Git host validators")
      val wire = _v5_root(_directory())
      val names = Gen.choose(1, 32).map(n => "r" * n)
      When("generated safe names and malformed host declarations are interpreted")
      val generated = Test.check(Test.Parameters.default.withMinSuccessfulTests(32), Prop.forAll(names) { name =>
        _decode(_repository_name(wire, name, name)).isSuccess
      })
      val invalid = Vector("r" * 33, "Upper", "has_space", "").flatMap(name => Vector(
        _repository_name(wire, name, "repository"), _repository_name(wire, "repository", name))) ++ Vector(
        _changed_entry(wire, "gitRepositories", _.mapObject(_.add("gitExecutable", Json.fromString("git")))),
        _changed_entry(wire, "gitRepositories", _.mapObject(_.add("endpoint", Json.fromString("-unsafe")))),
        _changed_entry(wire, "gitRepositories", _.mapObject(_.add("commitIdentity", Json.obj(
          "name" -> Json.fromString(""), "email" -> Json.fromString("fixture@example.invalid"))))),
        _changed_entry(wire, "gitRepositories", _.mapObject(_.add("environment", Json.obj("HOME" -> Json.fromString("/tmp"))))),
        _changed_entry(wire, "gitRepositories", entry => entry.mapObject(_.add("limits",
          entry.hcursor.downField("limits").focus.get.mapObject(_.add("stdoutBytes", Json.fromLong(0)))))))
      val results = invalid.map(_decode)
      Then("valid bounds cover the full family while malformed declarations remain denied")
      generated.passed shouldBe true
      results.foreach(_.isFaillure shouldBe true)
    }

    "retain V3 denial when RemoteTip is absent and reject V5-only fields under every legacy schema" in {
      Given("R4: a valid V3 host lacking RemoteTip and the additive V5 repository field")
      val fixture = GitExecutionSpecSupport.create()
      try {
        val wire = _v3_root(fixture.checkout)
        val legacy = wire.mapObject(_.add("gitPrograms", Json.fromValues(wire.hcursor.get[Vector[Json]]("gitPrograms").toOption.get
          .filterNot(_.hcursor.get[String]("operation").toOption.contains("remote-tip")))))
        val selected = _changed_entry(legacy, "grants", grant => grant.mapObject(_.add("capabilities",
          _json_strings(grant.hcursor.get[Vector[String]]("capabilities").toOption.get.filterNot(_ == "git-remote-tip")))))
        val plan = _take(_decode(selected))
        val context = GitExecutionSpecSupport.contextFor(fixture.root, plan.workspaceAdmissionOption, Some(plan.admission),
          fixture.profile.driver, _owner)
        val legacyfields = Vector(_root, _v2_root(_directory()), wire).map(_.mapObject(_.add("gitRepositories", Json.arr())))
        val retired = _v5_root(fixture.checkout).mapObject(_.add("schema", Json.fromString("cncf.runtime-process-execution.v4")))
        When("ordinary typed interpretation requests the undeclared operation and old schemas receive new fields")
        val denied = GitExecutionSpecSupport.execute(fixture.copy(context = context), GitOperation.RemoteTip)
        val results = (legacyfields :+ retired).map(_decode)
        Then("legacy plans neither infer repository authority nor accept a new schema field")
        denied.isFaillure shouldBe true
        fixture.profile.driver.executions shouldBe empty
        plan.gitRepositoryDeclarations shouldBe empty
        results.foreach(_.isFaillure shouldBe true)
      } finally fixture.close()
    }
  }

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

    "require independent dynamic TestOnly declaration beside the opted-in ordinary TestCompile owner" in {
      Given("ordinary TestCompile and TestOnly programs with separate capabilities on the same workspace")
      val directory = _directory()
      val original = _v2_root(directory)
      val base = original.hcursor.get[Vector[Json]]("sbtPrograms").toOption.get.head
      val scope = Json.obj("componentIdentity" -> Json.fromString("project"), "artifactIdentity" -> Json.fromString("project-tests"),
        "artifactRevision" -> Json.fromString("1"), "packagePrefix" -> Json.fromString("project.spec"))
      val compile = base.mapObject(_.add("operation", Json.fromString("test-compile")).add("validationDiscovery", scope))
      val test = base.mapObject(_.add("operation", Json.fromString("test-only")).add("capability", Json.fromString("ordinary-exact-tests"))
        .add("identity", Json.fromString("ordinary-exact-tests")).add("testClasses", Json.arr()).add("discoveredTests", Json.True)
        .add("validationMode", Json.obj("modes" -> Json.arr(Json.fromString("develop")),
          "provider" -> Json.fromString("fixture"), "livePrerequisites" -> Json.arr())))
      val wire = original.mapObject(fields => fields.add("sbtPrograms", Json.arr(compile, test)).add("grants",
        fields("grants").get.mapArray(_.map(_.mapObject(grant => grant.add("capabilities",
          grant("capabilities").get.mapArray(_ :+ Json.fromString("ordinary-exact-tests"))))))))
      When("the host codec binds and round-trips both independent normal programs")
      val decoded = _take(_decode(wire))
      val encoded = _take(RuntimeProcessExecutionPlanCodec.encode(decoded)).asInstanceOf[ConfigurationValue.StringValue]
      Then("the build projection is distinct from explicit dynamic TestOnly scope and mode authority")
      parse(encoded.value).toOption.get shouldBe wire
      decoded.sbtDeclarations.find(_.operation == SbtOperation.TestCompile).get.validationDiscovery.nonEmpty shouldBe true
      decoded.sbtDeclarations.find(_.operation == SbtOperation.TestOnly).get.discoveredTests shouldBe true
      val noowner = wire.mapObject(_.add("sbtPrograms", Json.arr(test)))
      _decode(noowner).isFaillure shouldBe true
      val nooptin = wire.mapObject(_.add("sbtPrograms", Json.arr(compile, test.mapObject(_.remove("discoveredTests")))))
      _decode(nooptin).isFaillure shouldBe true
    }

    "retain optional shared mode bindings and refuse unsupported declarations without changing legacy bytes" in {
      Given("an ordinary TestOnly host binding with canonical modes and an explicit actual fixture adapter")
      val directory = _directory()
      val legacy = _changed_entry(_v2_root(directory), "sbtPrograms", _.mapObject(_.add("operation", Json.fromString("test-only"))
        .add("testClasses", Json.arr(Json.fromString("project.spec.ProductSpec")))))
      val mode = Json.obj("modes" -> Json.arr(Json.fromString("develop"), Json.fromString("production")),
        "provider" -> Json.fromString("fixture"), "livePrerequisites" -> Json.arr())
      val wire = _changed_entry(legacy, "sbtPrograms", _.mapObject(_.add("validationMode", mode)))
      When("the ordinary host codec resolves and encodes the extension")
      val restored = _take(_decode(wire))
      val encoded = _take(RuntimeProcessExecutionPlanCodec.encode(restored)).asInstanceOf[ConfigurationValue.StringValue]
      Then("canonical selected-mode support is retained separately from absent historical declarations")
      parse(encoded.value).toOption.get shouldBe wire
      restored.sbtDeclarations.head.validationMode shouldBe Some(SbtValidationMode.Binding(Set("develop", "production"), "fixture"))
      _take(_decode(legacy)).sbtDeclarations.head.validationMode shouldBe None
      val live = _changed_entry(wire, "sbtPrograms", _.mapObject(_.add("validationMode",
        mode.mapObject(_.add("provider", Json.fromString("live"))))))
      _decode(live).isFaillure shouldBe true
      val invalid = _changed_entry(wire, "sbtPrograms", _.mapObject(_.add("validationMode", Json.Null)))
      _decode(invalid).isFaillure shouldBe true
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

    "round-trip exact seven-field Compile, Test and FixCheck declarations and reject selectors or argv" in {
      Given("V2 and V3 Compile/Test/FixCheck host declarations without TestOnly fields")
      val wires = Vector(_v2_root(_directory()), _v3_root(_directory())).flatMap(root =>
        Vector("compile", "test", "fix-check", "publish-local").map(operation => _changed_entry(root, "sbtPrograms",
          _.mapObject(_.add("operation", Json.fromString(operation))))))
      val extras = Vector("testClasses" -> Json.arr(), "testClasses" -> Json.Null,
        "testClasses" -> Json.arr(Json.fromString("org.example.SelectedSpec")),
        "scenario" -> Json.Null, "arguments" -> Json.arr(), "extraArgv" -> Json.arr(Json.fromString("test")))
      When("each strict host plan round-trips and the decoder receives forbidden selector or argv fields")
      val accepted = wires.map(wire => _take(RuntimeProcessExecutionPlanCodec.encode(_take(_decode(wire)))))
      val restored = accepted.map(encoded => _take(RuntimeProcessExecutionPlanCodec.decode(encoded)))
      val rejected = wires.flatMap(wire => extras.map { case (name, value) =>
        _decode(_changed_entry(wire, "sbtPrograms", _.mapObject(_.add(name, value)))) })
      Then("all finite operation tokens retain their seven-field shape and extra fields fail closed")
      restored.map(_.sbtDeclarations.head.operation) shouldBe Vector.fill(2)(
        Vector(SbtOperation.Compile, SbtOperation.Test, SbtOperation.FixCheck, SbtOperation.PublishLocal)).flatten
      accepted.zip(wires).foreach { case (encoded, original) =>
        parse(encoded.asInstanceOf[ConfigurationValue.StringValue].value).toOption.get shouldBe original
        val entry = parse(encoded.asInstanceOf[ConfigurationValue.StringValue].value).toOption.get
          .hcursor.downField("sbtPrograms").downArray.focus.get.asObject.get
        entry.size shouldBe 7
        entry.contains("testClasses") shouldBe false
      }
      restored.foreach { plan =>
        val declaration = plan.sbtDeclarations.head
        val binding = _take(plan.workspaceAdmissionOption.get.bindingC(declaration.target))
        val tagged = _take(SbtProgramDefinition.createC(binding, declaration.operation, declaration.capability,
          declaration.identity, declaration.sbtExecutable, declaration.limits, declaration.environment))
        tagged._sbt_metadata.get.testClasses shouldBe empty
        tagged._sbt_metadata.get.scenario shouldBe None
        tagged.fixedArguments.last shouldBe (declaration.operation match {
          case SbtOperation.Compile => "compile"
          case SbtOperation.Test => "test"
          case SbtOperation.FixCheck => "fixCheck"
          case SbtOperation.PublishLocal => "publishLocal"
          case SbtOperation.TestOnly => fail("Seven-field declaration cannot be TestOnly")
        })
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

    "round-trip all V3 Git declarations and SBT through one identical shared binding" in {
      Given("one V3 host plan with all supported Git operations, a Test declaration and generic templates")
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

    "round-trip only the seven operation-specific bounded selected path arrays" in {
      Given("all V3 Git declarations and fixed literal paths containing colon, backslash and LF")
      val wire = _v3_root(_directory())
      val kinds = Set(GitOperationKind.CommitSelected, GitOperationKind.CheckSelectedIndex, GitOperationKind.SelectedIndexChanges, GitOperationKind.TreeEntries,
        GitOperationKind.PreparePurposeCandidate, GitOperationKind.MaterializePurposeCandidate, GitOperationKind.CommitPurposeCandidate)
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
        Given("R1: seven V3 declarations share a workspace, selection ID and ordered two-path selection")
        val kinds = Vector(GitOperationKind.CommitSelected, GitOperationKind.CheckSelectedIndex, GitOperationKind.SelectedIndexChanges, GitOperationKind.TreeEntries,
          GitOperationKind.PreparePurposeCandidate, GitOperationKind.MaterializePurposeCandidate, GitOperationKind.CommitPurposeCandidate)
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
        val kinds = Vector(GitOperationKind.CommitSelected, GitOperationKind.CheckSelectedIndex, GitOperationKind.SelectedIndexChanges, GitOperationKind.TreeEntries)
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
      Given("a V3 host plan with the seven exact applicable operations and strict nonapplicable operations")
      val wire = _v3_root(_directory())
      val kinds = Vector(GitOperationKind.CommitSelected, GitOperationKind.CheckSelectedIndex, GitOperationKind.SelectedIndexChanges, GitOperationKind.TreeEntries,
        GitOperationKind.PreparePurposeCandidate, GitOperationKind.MaterializePurposeCandidate, GitOperationKind.CommitPurposeCandidate)
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
      Given("all nine selected kinds at and above their individual per-workspace bounds")
      val root = _directory()
      val id = GitExecutionSpecSupport.workspaceId
      val cases = Vector(GitOperationKind.StageSelected -> 128, GitOperationKind.StageSelectedAll -> 256,
        GitOperationKind.CommitSelected -> 64, GitOperationKind.CheckSelectedIndex -> 64, GitOperationKind.SelectedIndexChanges -> 64,
        GitOperationKind.TreeEntries -> 64, GitOperationKind.PreparePurposeCandidate -> 64,
        GitOperationKind.MaterializePurposeCandidate -> 64, GitOperationKind.CommitPurposeCandidate -> 64)
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
      results shouldBe Vector.fill(9)(true -> true)
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

    "round-trip a fixed TestCompile host declaration without acquiring TestOnly authority" in {
      Given("a normal V2 workspace with a separately granted test-compile operation")
      val wire = _changed_entry(_v2_root(_directory()), "sbtPrograms", _.mapObject(
        _.add("operation", Json.fromString("test-compile"))))
      When("the standard host codec decodes, encodes and decodes it")
      val first = _take(_decode(wire))
      val restored = _take(RuntimeProcessExecutionPlanCodec.decode(_take(RuntimeProcessExecutionPlanCodec.encode(first))))
      Then("the operation remains TestCompile with no selected test classes")
      restored.sbtDeclarations.head.operation shouldBe SbtOperation.TestCompile
      restored.sbtDeclarations.head.testClasses shouldBe empty
      val declaration = restored.sbtDeclarations.head
      val binding = _take(restored.workspaceAdmissionOption.get.bindingC(declaration.target))
      val program = _take(SbtProgramDefinition.createC(binding, declaration.operation, declaration.capability,
        declaration.identity, declaration.sbtExecutable, declaration.limits, declaration.environment))
      program.fixedArguments.last shouldBe "Test / compile"
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

    "resolve the reserved normal sbt literal through the declaration PATH" in {
      Given("a V2 declaration and an executable fixture on its declared PATH")
      val root = _directory()
      val directory = Files.createDirectory(root.resolve("tools"))
      val executable = Files.writeString(directory.resolve("sbt"), "fixture, never executed")
      executable.toFile.setExecutable(true) shouldBe true
      val wire = _changed_entry(_v2_root(root), "sbtPrograms", _.mapObject(
        _.add("sbtExecutable", Json.fromString("sbt"))
          .add("environment", Json.obj("PATH" -> Json.fromString(directory.toString)))))
      When("the standard codec decodes the declaration and builds its fixed typed program")
      val plan = _take(_decode(wire))
      val declaration = plan.sbtDeclarations.head
      val binding = _take(plan.workspaceAdmissionOption.get.bindingC(declaration.target))
      val program = _take(SbtProgramDefinition.createC(binding, declaration.operation, declaration.capability,
        declaration.identity, declaration.sbtExecutable, declaration.limits, declaration.environment))
      Then("the fixed executable is the canonical absolute fixture and the normal arguments remain fixed")
      program._executable_location shouldBe executable.toRealPath().toString
      program.fixedArguments shouldBe Vector("--batch", "-Dsbt.server.autostart=false",
        "-Dsbt.supershell=false", "-Dsbt.color=false", "test")
    }

    "retain the bounded environment diagnosis for an unavailable normal sbt" in {
      Given("V2, V3 and V6 host declarations with an explicitly empty PATH")
      val root = _directory()
      def _check_(wire: Json): Unit = {
        val selected = _changed_entry(wire, "sbtPrograms", _.mapObject(
          _.add("sbtExecutable", Json.fromString("sbt")).add("environment", Json.obj("PATH" -> Json.fromString("")))))
        When("the standard codec preflights the normal executable")
        val result = _decode(selected)
        Then("the failure reports only the bounded environment diagnosis")
        result.isFaillure shouldBe true
        result.toString should include ("Normal sbt executable is unavailable on PATH")
        result.toString should not include root.toString
      }
      try {
        _check_(_v2_root(root))
        _check_(_v3_root(root))
        _with_v6_root(_check_)
      } finally Using.resource(Files.walk(root)) { stream =>
        stream.sorted(Comparator.reverseOrder[Path]()).iterator().asScala.foreach(path => Files.deleteIfExists(path))
      }
    }

    "reject unavailable workspace roots and invalid normal sbt executables with safe failures" in {
      Given("a real V2 checkout and a regular file with no executable permission")
      val root = _directory()
      val plainfile = Files.createFile(root.resolve("plain"))
      val wire = _v2_root(root)
      val invalid = Vector("relative", root.resolve("missing").toString, plainfile.toString).map(path =>
        _changed_entry(wire, "workspaces", _.mapObject(_.add("root", Json.fromString(path))))) ++
        Vector("bin/sbt", root.resolve("missing-sbt").toString, root.toString, plainfile.toString).map(path =>
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
