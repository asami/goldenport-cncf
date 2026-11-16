package org.goldenport.cncf.cli

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, LinkOption, Path}
import java.util.Comparator
import java.util.zip.{ZipEntry, ZipOutputStream}
import scala.jdk.CollectionConverters.*
import scala.util.Using
import io.circe.Json
import org.goldenport.Consequence
import org.goldenport.configuration.*
import org.goldenport.protocol.Protocol
import org.goldenport.cncf.component.{Component, ComponentId, ComponentInstanceId, ComponentOrigin}
import org.goldenport.cncf.config.*
import org.goldenport.cncf.context.{ExecutionContext, ScopeContext, ScopeKind}
import org.goldenport.cncf.action.Behavior
import org.goldenport.cncf.processexecution.*
import org.goldenport.cncf.subsystem.*
import org.goldenport.cncf.unitofwork.{UnitOfWork, UnitOfWorkInterpreter, UnitOfWorkOp}
import org.goldenport.cncf.workspace.*
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalacheck.{Gen, Prop, Test}

/*
 * @since   Nov. 16, 2026
 * @version Nov. 16, 2026
 */
final class WorkspaceFirstUseInitializationSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _owner = GitExecutionSpecSupport.owner
  private val _source = GitExecutionSpecSupport.workspaceId
  private val _parent = _take(WorkspaceId.parseC("candidate-parent"))
  private val _alias = _take(WorkspaceId.parseC("candidate-alias"))
  private val _selected = _take(SubsystemInstanceId.default("application"))
  private val _key = CncfConfigurationParameterCatalog.SUBSYSTEM_PROCESS_EXECUTION_PLAN_KEY
  private val _limits = GitExecutionSpecSupport.limits

  private def _take[A](value: Consequence[A]): A = value match {
    case Consequence.Success(result) => result
    case other => fail(s"First-use setup rejected: $other")
  }

  private def _with_root[A](run: Path => A): A = {
    val root = Files.createTempDirectory("workspace-first-use-").toRealPath()
    Files.createDirectory(root.resolve("source"))
    try run(root)
    finally Using.resource(Files.walk(root)) { stream =>
      stream.sorted(Comparator.reverseOrder[Path]()).iterator().asScala.foreach(Files.deleteIfExists(_))
    }
  }

  private def _declaration(root: Path): RuntimeProcessExecutionPlanDeclaration = {
    val ids = Vector(_source, _parent, _alias)
    val programs = Vector(_source, _alias).flatMap { id =>
      Vector(GitOperationKind.Root, GitOperationKind.Head).map { kind =>
        GitExecutionSpecSupport.declaration(kind, root.resolve("remote.git").toString).copy(target = id,
          capability = _take(ProcessCapabilityId.parseC(id.print + "-" + kind.wireName)),
          identity = id.print + "-" + kind.wireName)
      }
    }
    val build = SbtRuntimeDeclaration(_alias, SbtOperation.Compile,
      _take(ProcessCapabilityId.parseC("candidate-compile")), "candidate-compile", Path.of("/usr/bin/true"),
      _limits, Map.empty)
    RuntimeProcessExecutionPlanDeclaration(Vector.empty,
      Map(_owner -> (programs.map(x => ProcessExecutionGrant(x.capability)) :+ ProcessExecutionGrant(build.capability))),
      RuntimeProcessExecutionPlanCodec.SCHEMA_V8,
      Vector(WorkspaceRuntimeDeclaration(_source, root.resolve("source")),
        WorkspaceRuntimeDeclaration(_parent, root.resolve(".sm-workflow/candidates")),
        WorkspaceRuntimeDeclaration(_alias, root.resolve(".sm-workflow/dormant"))),
      ids.map(WorkspaceGrant(_owner, _, WorkspacePermission.Manage)), Vector(build), programs,
      candidatePolicies = Vector(SessionCandidateWorkspacePolicy(_owner, _parent, _alias)),
      workspaceInitializations = Vector(WorkspaceRootInitialization(_owner, root, Vector(_source), _parent, _alias,
        _take(WorkspaceRelativePath.parseC(".sm-workflow/candidates")),
        _take(WorkspaceRelativePath.parseC(".sm-workflow/dormant")))))
  }

  private def _wire(declaration: RuntimeProcessExecutionPlanDeclaration): String =
    _take(RuntimeProcessExecutionPlanDeclarationCodec.encode(declaration)).asInstanceOf[ConfigurationValue.StringValue].value

  private def _bindings(declaration: RuntimeProcessExecutionPlanDeclaration,
    trusted: Boolean = true): ConfigurationBindingCollection[CncfConfigurationTarget] = {
    val values = Map(_key -> _wire(declaration))
    val candidates = if (trusted)
      _take(CncfAssemblyConfigurationProjection.componentDefaultCandidates(values, "selected.car", _selected))
      else _take(CncfAssemblyConfigurationProjection.candidates(values, Some("external.yaml"), _selected))
    _take(ConfigurationBindingResolver.resolve(candidates,
      _take(CncfConfigurationResolutionContext.forSubsystem(_selected)).generic))
  }

  private def _admit(declaration: RuntimeProcessExecutionPlanDeclaration): Consequence[Option[RuntimeProcessExecutionPlan]] =
    SubsystemProcessExecutionConfiguration.admitC(_bindings(declaration), _selected)

  private def _absent(root: Path): Unit =
    Files.notExists(root.resolve(".sm-workflow"), LinkOption.NOFOLLOW_LINKS) shouldBe true

  private def _with_car[A](root: Path, text: String)(run: GenericSubsystemDescriptor => A): A = {
    val path = root.resolve("selected.car")
    Using.resource(new ZipOutputStream(Files.newOutputStream(path))) { zip =>
      val descriptor = """{"schemaVersion":3,"component":{"namespace":"org.example","id":"Application","version":"0.1.0"}}"""
      val assembly = "subsystem: application\ncomponents:\n  - namespace: org.example\n    id: Application\n    version: 0.1.0\nconfig:\n" +
        s"  ${_key}: '${text.replace("'", "''")}'\n"
      Vector("component-descriptor.json" -> descriptor, "assembly-descriptor.yaml" -> assembly).foreach { (name, content) =>
        zip.putNextEntry(new ZipEntry(name))
        zip.write(content.getBytes(UTF_8))
        zip.closeEntry()
      }
    }
    run(_take(GenericSubsystemDescriptor.loadComponentArchive(path)))
  }

  private def _arguments(root: Path): Array[String] = {
    val descriptor = root.resolve("test.yaml")
    Files.writeString(descriptor,
      """kind: test-descriptor
        |execution:
        |  profile: controlled
        |  key: workspace-first-use
        |  time:
        |    mode: manual
        |    start-at: '2026-11-16T00:00:00Z'
        |  random:
        |    mode: seeded
        |    seed: workspace-first-use
        |  ids:
        |    mode: deterministic
        |  scheduler:
        |    mode: manual
        |  ordering:
        |    mode: deterministic
        |""".stripMargin)
    val home = Files.createDirectory(root.resolve("home"))
    Array(s"--textus.test.descriptor=$descriptor", s"--textus.test.home.path=$home",
      "--textus.test.home.inherit.runtime=false")
  }

  private def _context(root: ScopeContext): ExecutionContext = {
    val component = Component.create(_owner.name, _owner, ComponentInstanceId.default(_owner), Protocol.empty)
    val scope = Component.Context(_owner.name, root, component, ComponentOrigin.Embed)
      .createChildScope(ScopeKind.Service, "workspace").createChildScope(ScopeKind.Action, "prepare")
    ExecutionContext.create().withScope(scope)
  }

  private def _run[A](context: ExecutionContext, operation: UnitOfWorkOp[A]): A = {
    val uow = new UnitOfWork(context)
    try _take(new UnitOfWorkInterpreter(uow).interpret(operation)) finally uow.dispose()
  }

  private def _git[A](context: ExecutionContext, operation: GitOperation[A]): GitExecutionResult[A] = {
    val uow = new UnitOfWork(context)
    try _take(new UnitOfWorkInterpreter(uow).run(
      new GitSpecBehavior(Behavior.Core(context, None, None)).program(GitTarget(_alias), operation)))
    finally uow.dispose()
  }

  "Explicit first-use workspace initialization" should {
    "initialize sibling candidate roots from standard CAR path tokens after selected admission" in _with_root { root =>
      Given("an existing source RuntimeRoot and CAR defaults declaring sibling roots through runtime.parent")
      val source = root.resolve("source")
      Files.writeString(source.resolve("retained"), "source content")
      val text = s"""{
        |"schema":"cncf.runtime-process-execution.v8",
        |"programs":[],"grants":[],
        |"workspaces":[
        |  {"identity":"${_source.print}","root":"$${runtime.root}"},
        |  {"identity":"${_parent.print}","root":"$${runtime.parent}/.sm-workflow/session-candidates"},
        |  {"identity":"${_alias.print}","root":"$${runtime.parent}/.sm-workflow/candidate-alias"}
        |],
        |"workspaceGrants":[
        |  {"componentId":"${_owner.name}","workspaceId":"${_source.print}","access":"manage"},
        |  {"componentId":"${_owner.name}","workspaceId":"${_parent.print}","access":"manage"},
        |  {"componentId":"${_owner.name}","workspaceId":"${_alias.print}","access":"manage"}
        |],
        |"sbtPrograms":[],"gitPrograms":[],"gitRepositories":[],"validationChecks":[],
        |"sessionCandidateWorkspaces":[
        |  {"componentId":"${_owner.name}","parent":"${_parent.print}","alias":"${_alias.print}"}
        |],
        |"workspaceInitializations":[{
        |  "componentId":"${_owner.name}","anchor":"$${runtime.parent}","sources":["${_source.print}"],
        |  "parent":"${_parent.print}","alias":"${_alias.print}",
        |  "parentPath":".sm-workflow/session-candidates","aliasPath":".sm-workflow/candidate-alias"
        |}]
        |}""".stripMargin
      _absent(root)
      _with_car(root, text) { descriptor =>
        val configuration = ResolvedConfiguration(Configuration.empty, ConfigurationTrace.empty)
        val policy = RepositoryBootstrapPolicy(componentFiles = Vector(descriptor.path.toString),
          baseDirectory = source, defaultRepositoriesEnabled = false)
        When("normal CAR resolution and catalog selection expand the path-only defaults")
        val resolved = _take(GenericSubsystemFactory.runtimeResolveDescriptorC(configuration, Some(policy))).get
        val admitted = _take(GenericSubsystemFactory.runtimeAdmitDescriptorC(resolved, configuration, Some(policy)))
        val candidates = _take(CncfDiscoveredAssemblyConfigurationProjection.candidates(Map.empty, None, Some(admitted), _selected))
        val bindings = _take(ConfigurationBindingResolver.resolve(candidates,
          _take(CncfConfigurationResolutionContext.forSubsystem(_selected)).generic))
        Then("the selected declaration contains normalized sibling paths and decoding creates no directory")
        val binding = _take(bindings.binding(CncfConfigurationParameterCatalog.subsystemProcessExecutionPlan)).get
        binding.provenance.sourceType shouldBe Some("component-car-defaults")
        binding.value.workspaceInitializations.head.anchor shouldBe source.getParent
        binding.value.workspaceDeclarations.map(x => x.identity -> x.root).toMap shouldBe Map(
          _source -> source, _parent -> root.resolve(".sm-workflow/session-candidates"),
          _alias -> root.resolve(".sm-workflow/candidate-alias"))
        _absent(root)
        When("a different subsystem attempts to activate the selected defaults")
        val other = SubsystemProcessExecutionConfiguration.admitC(bindings, _take(SubsystemInstanceId.default("other")))
        Then("the ordinary ownership gate refuses before creating a sibling root")
        other.isSuccess shouldBe false
        _absent(root)
        When("the normal selected-subsystem admission activates the explicit V8 policy")
        val plan = _take(SubsystemProcessExecutionConfiguration.admitC(bindings, _selected)).get
        Then("actual workspace bindings use the existing sibling layout and preserve the source")
        val workspace = plan.workspaceAdmissionOption.get
        _take(workspace.bindingC(_source).flatMap(_.workingDirectoryC)) shouldBe source
        _take(workspace.bindingC(_parent).flatMap(_.workingDirectoryC)) shouldBe root.resolve(".sm-workflow/session-candidates")
        _take(workspace.bindingC(_alias).flatMap(_.workingDirectoryC)) shouldBe root.resolve(".sm-workflow/candidate-alias")
        Using.resource(Files.list(root.resolve(".sm-workflow")))(_.iterator().asScala.map(_.getFileName.toString).toSet) shouldBe
          Set("session-candidates", "candidate-alias")
        Using.resource(Files.list(source))(_.iterator().asScala.map(_.getFileName.toString).toSet) shouldBe Set("retained")
        Files.readString(source.resolve("retained")) shouldBe "source content"
        Using.resource(Files.list(root.resolve(".sm-workflow/session-candidates")))(_.count()) shouldBe 0L
        Using.resource(Files.list(root.resolve(".sm-workflow/candidate-alias")))(_.count()) shouldBe 0L
      }
    }

    "activate selected trusted CAR defaults only after normal descriptor resolution" in _with_root { root =>
      Given("a selected CAR with V8 defaults and no .sm-workflow, parent or dormant alias")
      _absent(root)
      _with_car(root, _wire(_declaration(root))) { descriptor =>
        val configuration = ResolvedConfiguration(Configuration.empty, ConfigurationTrace.empty)
        val policy = RepositoryBootstrapPolicy(componentFiles = Vector(descriptor.path.toString),
          baseDirectory = root, defaultRepositoriesEnabled = false)
        When("the runtime resolves its descriptor and the catalog selects the parsed declaration")
        val resolved = _take(GenericSubsystemFactory.runtimeResolveDescriptorC(configuration, Some(policy))).get
        val admitted = _take(GenericSubsystemFactory.runtimeAdmitDescriptorC(resolved, configuration, Some(policy)))
        val candidates = _take(CncfDiscoveredAssemblyConfigurationProjection.candidates(Map.empty, None, Some(admitted), _selected))
        val bindings = _take(ConfigurationBindingResolver.resolve(candidates,
          _take(CncfConfigurationResolutionContext.forSubsystem(_selected)).generic))
        Then("discovery and selection retain trusted provenance without creating directories")
        _take(bindings.binding(CncfConfigurationParameterCatalog.subsystemProcessExecutionPlan)).get
          .provenance.sourceType shouldBe Some("component-car-defaults")
        _absent(root)
        When("the selected-subsystem runtime admission activates the opt-in")
        val plan = _take(SubsystemProcessExecutionConfiguration.admitC(bindings, _selected)).get
        Then("only explicit parent and alias prefixes exist and actual typed bindings are installed")
        val workspace = plan.workspaceAdmissionOption.get
        _take(workspace.bindingC(_parent).flatMap(_.workingDirectoryC)) shouldBe root.resolve(".sm-workflow/candidates")
        _take(workspace.bindingC(_alias).flatMap(_.workingDirectoryC)) shouldBe root.resolve(".sm-workflow/dormant")
        Using.resource(Files.list(root.resolve(".sm-workflow")))(_.iterator().asScala.map(_.getFileName.toString).toSet) shouldBe
          Set("candidates", "dormant")
        Files.exists(root.resolve(".sm-workflow/candidates/.git")) shouldBe false
        val driver = new LocalProcessExecutionDriver()
        try {
          val context = GitExecutionSpecSupport.contextFor(root, Some(workspace), Some(plan.admission), driver)
          SbtExecutionAdmission.resolveC(context, SbtTarget(_alias), SbtOperation.Compile).isSuccess shouldBe true
        } finally driver.close()
      }
    }

    "enter the public runtime lifecycle and retain native Session candidate and Git Root and Head behavior" in _with_root { root =>
      Given("a trusted selected file whose V8 parent and alias roots are absent")
      val directory = Files.createDirectory(root.resolve(".textus"))
      Files.createDirectory(root.resolve(".cncf"))
      Files.writeString(directory.resolve("config.json"), Json.obj(_key -> Json.fromString(_wire(_declaration(root)))).noSpaces)
      val runtime = new CncfRuntime()
      val arguments = _arguments(root)
      _absent(root)
      When("initializeForEmbedding uses the ordinary selected-subsystem preflight")
      val subsystem = _take(runtime.initializeForEmbedding(root, arguments))
      try {
        val context = _context(subsystem.globalRuntimeContext)
        Then("the runtime admits actual directories before native candidate preparation")
        _take(context.workspaceExecutionAdmissionOption.get.bindingC(_alias).flatMap(_.workingDirectoryC)) shouldBe
          root.resolve(".sm-workflow/dormant")
        When("the native Session candidate operation allocates its separate retained selection")
        val selected = _run(context, UnitOfWorkOp.SessionCandidatePrepare(_source, _parent, _alias, "session", "candidate"))
        val path = _take(selected.context.workspaceExecutionAdmissionOption.get.bindingC(_alias).flatMap(_.workingDirectoryC))
        Then("the candidate is below the parent and the dormant alias remains empty")
        path.startsWith(root.resolve(".sm-workflow/candidates")) shouldBe true
        Using.resource(Files.list(root.resolve(".sm-workflow/dormant")))(_.count()) shouldBe 0L
        Given("a real Git repository materialized in that selected candidate by its fixture owner")
        GitExecutionSpecSupport.setup(path, "init", "--initial-branch=main")
        GitExecutionSpecSupport.setup(path, "-c", "user.name=Fixture", "-c", "user.email=fixture@example.invalid",
          "commit", "--allow-empty", "-m", "fixture")
        When("typed native Git Root and Head run with the actual selected binding")
        val gitroot = _git(selected.context, GitOperation.Root)
        val head = _git(selected.context, GitOperation.Head)
        Then("both real results remain usable through the normal process admission")
        _take(gitroot.valueC) shouldBe path.toRealPath().toString
        head.outcome shouldBe GitExecutionOutcome.Succeeded
        _take(head.valueC).value.length should (be(40) or be(64))
      } finally { Subsystem.shutdownOwned(subsystem); runtime.closeEmbedding() }
    }

    "keep declaration decoding, catalog roundtrips and IO-default resolution free of initialization" in _with_root { root =>
      Given("valid first-use data with an omitted SBT stdout default")
      val original = _declaration(root).copy(_io_default_omissions = Map(("sbtPrograms" -> 0) -> Set("stdoutBytes")))
      When("the actual catalog parses, re-encodes and resolves its selected declaration")
      val bindings = _bindings(original)
      val parsed = _take(bindings.binding(CncfConfigurationParameterCatalog.subsystemProcessExecutionPlan)).get.value
      val encoded = _take(RuntimeProcessExecutionPlanDeclarationCodec.encode(parsed))
      val restored = _take(RuntimeProcessExecutionPlanDeclarationCodec.decode(encoded))
      val resolved = _take(RuntimeProcessExecutionPlanDeclarationCodec.resolveIoDefaultsC(restored, 17L))
      Then("typed declaration data roundtrips and fills defaults without admitting missing roots")
      restored.workspaceInitializations shouldBe original.workspaceInitializations
      resolved.sbtDeclarations.head.limits.stdoutBytes shouldBe Some(17L)
      RuntimeProcessExecutionPlanCodec.decode(encoded).isSuccess shouldBe false
      _absent(root)
    }

    "gate both unselected and untrusted declarations before initialization" in _with_root { root =>
      Given("a valid V8 declaration with both selected-CAR and external-assembly provenance")
      val declaration = _declaration(root)
      val selected = _bindings(declaration)
      val untrusted = _bindings(declaration, trusted = false)
      When("another subsystem or an untrusted source attempts admission")
      val other = SubsystemProcessExecutionConfiguration.admitC(selected, _take(SubsystemInstanceId.default("other")))
      val external = SubsystemProcessExecutionConfiguration.admitC(untrusted, _selected)
      Then("both fail without provisioning a root")
      other.isSuccess shouldBe false
      external.isSuccess shouldBe false
      _absent(root)
    }

    "reject late invalid semantic programs and grants before any directory effect" in {
      val mutations: Vector[RuntimeProcessExecutionPlanDeclaration => RuntimeProcessExecutionPlanDeclaration] = Vector(
        d => d.copy(sbtDeclarations = d.sbtDeclarations.map(_.copy(sbtExecutable = Path.of("/missing/sbt")))),
        d => d.copy(gitDeclarations = d.gitDeclarations.updated(3, d.gitDeclarations(3).copy(remote = "-invalid"))),
        d => d.copy(sbtDeclarations = d.sbtDeclarations.map(_.copy(environment = Map("BAD=KEY" -> "bad")))),
        d => {
          val capability = _take(ProcessCapabilityId.parseC("late-check"))
          d.copy(validationCheckDeclarations = Vector(ValidationCheckRuntimeDeclaration(_alias,
            _take(ValidationCheckId.parseC("late-check")), ValidationCheckPurpose.Compilation,
            ValidationCheckProgramDefinition.ADAPTER, capability, "late-check", Path.of("/bin/sh"),
            Vector("-c", "true"), _limits, Map.empty)),
            grants = d.grants.updated(_owner, d.grants(_owner) :+ ProcessExecutionGrant(capability)))
        },
        d => d.copy(sbtDeclarations = d.sbtDeclarations.map(_.copy(limits = _limits.copy(argumentCount = Some(1L))))),
        d => d.copy(grants = d.grants.updated(_owner, d.grants(_owner) :+ ProcessExecutionGrant(_take(ProcessCapabilityId.parseC("unknown"))))),
        d => d.copy(workspaceGrants = d.workspaceGrants.filterNot(_.workspaceId == _alias)),
        d => d.copy(gitDeclarations = d.gitDeclarations.updated(3, d.gitDeclarations(3).copy(identity = d.gitDeclarations.head.identity)))
      )
      mutations.foreach { mutate => _with_root { root =>
        Given("an otherwise valid first-use declaration with an invalid late program or authority")
        val declaration = mutate(_declaration(root))
        When("selected trusted runtime admission prepares the whole declaration")
        val result = _admit(declaration)
        Then("failure precedes all root creation")
        result.isSuccess shouldBe false
        _absent(root)
      } }
    }

    "preserve existing parent contents and accept the same safe initialization repeatedly" in _with_root { root =>
      Given("an existing parent with retained content and a missing dormant alias")
      Files.createDirectory(root.resolve(".sm-workflow"))
      val parent = Files.createDirectory(root.resolve(".sm-workflow/candidates"))
      Files.writeString(parent.resolve("retained"), "keep")
      val declaration = _declaration(root)
      When("selected admission initializes twice")
      val first = _take(_admit(declaration)).get
      val second = _take(_admit(declaration)).get
      Then("existing content survives and both admissions bind the same ordinary paths")
      Files.readString(parent.resolve("retained")) shouldBe "keep"
      _take(first.workspaceAdmissionOption.get.bindingC(_parent).flatMap(_.workingDirectoryC)) shouldBe
        _take(second.workspaceAdmissionOption.get.bindingC(_parent).flatMap(_.workingDirectoryC))
      RuntimeProcessExecutionPlanCodec.decode(_take(RuntimeProcessExecutionPlanCodec.encode(second))).isSuccess shouldBe true
    }

    "fail closed when an inspected prefix is rebound before the first mkdir" in _with_root { root =>
      Given("a fully inspected ordinary prefix and absent target directories")
      val prefix = Files.createDirectory(root.resolve(".sm-workflow"))
      val declaration = _declaration(root)
      val prepared = _take(WorkspaceRootInitialization.prepareC(
        declaration.workspaceDeclarations.map(x => x.identity -> x.root), declaration.workspaceGrants,
        declaration.candidatePolicies, declaration.workspaceInitializations))
      Files.move(prefix, root.resolve("retained-prefix"))
      Files.createDirectory(prefix)
      When("initialization rechecks the prefix identity")
      val result = prepared.initializeC
      Then("the observed replacement is refused and no target is created")
      result.isSuccess shouldBe false
      Files.exists(prefix.resolve("candidates")) shouldBe false
      Files.exists(prefix.resolve("dormant")) shouldBe false
      Files.isDirectory(root.resolve("retained-prefix")) shouldBe true
    }

    "require an existing ordinary explicit anchor and existing separated sources" in {
      Vector("missing-anchor", "linked-anchor", "missing-source", "linked-source").foreach { kind => _with_root { root =>
        Given(s"an invalid $kind host relationship")
        val original = _declaration(root)
        val declaration = if (kind.endsWith("anchor")) {
          val anchor = root.resolve("anchor")
          if (kind == "linked-anchor") Files.createSymbolicLink(anchor, root)
          original.copy(workspaceInitializations = original.workspaceInitializations.map(_.copy(anchor = anchor)),
            workspaceDeclarations = original.workspaceDeclarations.map(x => if (x.identity == _source) x
              else x.copy(root = anchor.resolve(root.relativize(x.root)))))
        } else {
          val source = root.resolve("alternate-source")
          if (kind == "linked-source") Files.createSymbolicLink(source, root.resolve("source"))
          original.copy(workspaceDeclarations = original.workspaceDeclarations.map(x =>
            if (x.identity == _source) x.copy(root = source) else x))
        }
        When("selected admission checks all ordinary directories")
        val result = _admit(declaration)
        Then("no implicit anchor or source allocation occurs")
        result.isSuccess shouldBe false
        _absent(root)
      } }
    }

    "reject symlink and file prefixes and occupied aliases before creating sibling targets" in {
      Vector("link", "file", "occupied").foreach { kind => _with_root { root =>
        Given(s"an unsafe $kind occupant in an opted-in target path")
        if (kind == "link") Files.createSymbolicLink(root.resolve(".sm-workflow"), root.resolve("source"))
        else if (kind == "file") Files.writeString(root.resolve(".sm-workflow"), "file")
        else {
          Files.createDirectory(root.resolve(".sm-workflow"))
          val alias = Files.createDirectory(root.resolve(".sm-workflow/dormant"))
          Files.writeString(alias.resolve("foreign"), "retain")
        }
        When("selected runtime admission inspects the complete initialization plan")
        val result = _admit(_declaration(root))
        Then("it refuses the occupant and creates no parent or source child")
        result.isSuccess shouldBe false
        Files.exists(root.resolve(".sm-workflow/candidates"), LinkOption.NOFOLLOW_LINKS) shouldBe false
        Files.exists(root.resolve("source/candidates"), LinkOption.NOFOLLOW_LINKS) shouldBe false
      } }
    }

    "reject source omission, overlap, escaped paths and mismatched policy roots without effects" in {
      val mutations: Vector[RuntimeProcessExecutionPlanDeclaration => RuntimeProcessExecutionPlanDeclaration] = Vector(
        d => d.copy(workspaceInitializations = d.workspaceInitializations.map(_.copy(sources = Vector(_alias)))),
        d => d.copy(workspaceDeclarations = d.workspaceDeclarations.map(x => if (x.identity == _source) x.copy(root = d.workspaceInitializations.head.anchor) else x)),
        d => d.copy(workspaceInitializations = d.workspaceInitializations.map(_.copy(aliasPath = _take(WorkspaceRelativePath.parseC(".sm-workflow/candidates/alias"))))),
        d => d.copy(workspaceDeclarations = d.workspaceDeclarations.map(x => if (x.identity == _alias) x.copy(root = x.root.resolve("different")) else x)),
        d => d.copy(workspaceInitializations = d.workspaceInitializations.map(_.copy(componentId = ComponentId("org.example.Other"))))
      )
      mutations.foreach { mutate => _with_root { root =>
        Given("a malformed explicit initialization relationship")
        When("selected admission validates it")
        val result = _admit(mutate(_declaration(root)))
        Then("it fails without creating prefixes")
        result.isSuccess shouldBe false
        _absent(root)
      } }
      Given("an invalid relative path in an otherwise correctly encoded V8 declaration")
      _with_root { root =>
        val json = io.circe.parser.parse(_wire(_declaration(root))).toOption.get
        val invalid = json.mapObject(_.add("workspaceInitializations", Json.arr(json.hcursor
          .downField("workspaceInitializations").downArray.focus.get.mapObject(_.add("parentPath", Json.fromString("../escape"))))))
        When("declaration parsing sees the escape")
        val result = RuntimeProcessExecutionPlanDeclarationCodec.decode(ConfigurationValue.StringValue(invalid.noSpaces))
        Then("even pure parsing refuses it without creating a root")
        result.isSuccess shouldBe false
        _absent(root)
      }
      Vector(Vector.empty[WorkspaceId], Vector(_source, _source)).foreach { sources => _with_root { root =>
        Given("an empty or duplicate explicit source list")
        val declaration = _declaration(root)
        val invalid = declaration.copy(workspaceInitializations = declaration.workspaceInitializations.map(_.copy(sources = sources)))
        When("the pure declaration codec parses the source list")
        val result = RuntimeProcessExecutionPlanDeclarationCodec.decode(ConfigurationValue.StringValue(_wire(invalid)))
        Then("it refuses the malformed source list without creating a prefix")
        result.isSuccess shouldBe false
        _absent(root)
      } }
    }

    "retain V7 preexisting-root requirements and its existing source-alias compatibility" in _with_root { root =>
      Given("legacy V7 data without the V8 opt-in")
      val declaration = _declaration(root).copy(schemaVersion = RuntimeProcessExecutionPlanCodec.SCHEMA_V7,
        workspaceInitializations = Vector.empty)
      When("selected admission encounters absent legacy roots")
      val missing = _admit(declaration)
      Then("it refuses without implicitly initializing")
      missing.isSuccess shouldBe false
      _absent(root)
      Given("a legacy parent exists and the dormant alias intentionally shares the existing source")
      Files.createDirectory(root.resolve(".sm-workflow"))
      Files.createDirectory(root.resolve(".sm-workflow/candidates"))
      Files.writeString(root.resolve("source/retained"), "source content")
      val compatible = declaration.copy(workspaceDeclarations = declaration.workspaceDeclarations.map(x =>
        if (x.identity == _alias) x.copy(root = root.resolve("source")) else x))
      When("the original V7 codec admits this preexisting configuration")
      val legacy = RuntimeProcessExecutionPlanCodec.decode(ConfigurationValue.StringValue(_wire(compatible)))
      Then("the legacy source-alias compatibility remains available")
      legacy.isSuccess shouldBe true
      Files.readString(root.resolve("source/retained")) shouldBe "source content"
    }

    "keep generated safe relative prefixes literal across declaration roundtrips" in {
      Given("generated normalized names below the explicit anchor")
      val names = Gen.choose(1, 20).map(n => "prefix-" + n)
      When("declarations roundtrip with each generated prefix")
      val property = Prop.forAll(names) { name => _with_root { root =>
        val original = _declaration(root)
        val policy = original.workspaceInitializations.head
        val updated = policy.copy(parentPath = _take(WorkspaceRelativePath.parseC(name + "/parent")),
          aliasPath = _take(WorkspaceRelativePath.parseC(name + "/alias")))
        val declaration = original.copy(workspaceInitializations = Vector(updated))
        val restored = _take(RuntimeProcessExecutionPlanDeclarationCodec.decode(ConfigurationValue.StringValue(_wire(declaration))))
        restored.workspaceInitializations == declaration.workspaceInitializations && Files.notExists(root.resolve(name))
      } }
      Then("no generated prefix is created while normalized policy data is retained")
      Test.check(Test.Parameters.default.withMinSuccessfulTests(20), property).passed shouldBe true
    }
  }
}
