package org.goldenport.cncf.processexecution

import java.nio.file.Paths
import scala.util.control.NonFatal
import io.circe.{Json, JsonObject}
import io.circe.jawn.JawnParser
import org.goldenport.Consequence
import org.goldenport.cncf.component.ComponentId
import org.goldenport.cncf.workspace.*
import org.goldenport.configuration.{ConfigurationValue, ConfigurationValueCodec}

/** Host wire contract: complete literal/lower-hex vectors only, never arbitrary shell text. */
/*
 * @since   Oct.  7, 2026
 * @version Oct. 26, 2026
 * @author  ASAMI, Tomoharu
 */
private[cncf] object RuntimeProcessExecutionPlanCodec extends ConfigurationValueCodec[RuntimeProcessExecutionPlan] {
  val SCHEMA = "cncf.runtime-process-execution.v1"
  val SCHEMA_V2 = "cncf.runtime-process-execution.v2"
  val SCHEMA_V3 = "cncf.runtime-process-execution.v3"
  val SCHEMA_V5 = "cncf.runtime-process-execution.v5"
  private val _parser = JawnParser(allowDuplicateKeys = false)
  private val _limit_names = Vector("launchTimeoutMillis", "executionTimeoutMillis", "terminationGraceMillis",
    "stdinBytes", "stdoutBytes", "stderrBytes", "argumentCount", "argumentBytes", "artifactCount", "artifactBytes", "workAreaBytes")

  def decode(value: ConfigurationValue): Consequence[RuntimeProcessExecutionPlan] = value match {
    case ConfigurationValue.StringValue(text) =>
      val result = for {
        json <- try _parser.parse(text).left.map(_ => "Invalid runtime process JSON")
          catch { case NonFatal(_) => Left("Invalid runtime process JSON") }
        schema <- json.hcursor.get[String]("schema").left.map(_ => "Runtime schema requires a string")
        names <- schema match {
          case SCHEMA => Right(Set("schema", "programs", "grants"))
          case SCHEMA_V2 => Right(Set("schema", "programs", "grants", "workspaces", "workspaceGrants", "sbtPrograms"))
          case SCHEMA_V3 => Right(Set("schema", "programs", "grants", "workspaces", "workspaceGrants", "sbtPrograms", "gitPrograms"))
          case SCHEMA_V5 => Right(Set("schema", "programs", "grants", "workspaces", "workspaceGrants", "sbtPrograms", "gitPrograms", "gitRepositories"))
          case _ => Left("Unsupported runtime process schema")
        }
        root <- _fields(json, names)
        programvalues <- _array(root, "programs")
        programs <- _sequence(programvalues.map(_program))
        grantvalues <- _array(root, "grants")
        grants <- _sequence(grantvalues.map(_grant))
        _ <- Either.cond(grants.map(_._1).distinct.size == grants.size, (), "Duplicate process grant owner")
        plan <- if (schema == SCHEMA) _valid(RuntimeProcessExecutionPlan.createC(programs, grants.toMap))
          else for {
            workspacevalues <- _array(root, "workspaces")
            workspaces <- _sequence(workspacevalues.map(x => _workspace(x, schema == SCHEMA_V3 || schema == SCHEMA_V5)))
            workspacegrantvalues <- _array(root, "workspaceGrants")
            workspacegrants <- _sequence(workspacegrantvalues.map(_workspace_grant))
            sbtvalues <- _array(root, "sbtPrograms")
            sbt <- _sequence(sbtvalues.map(_sbt_program))
            git <- if (schema == SCHEMA_V3 || schema == SCHEMA_V5) _array(root, "gitPrograms").flatMap(xs => _sequence(xs.map(_git_program)))
              else Right(Vector.empty[GitRuntimeDeclaration])
            repositories <- if (schema == SCHEMA_V5) _array(root, "gitRepositories").flatMap(xs => _sequence(xs.map(_git_repository)))
              else Right(Vector.empty[GitRepositoryRuntimeDeclaration])
            value <- if (schema == SCHEMA_V5)
              _valid_v5(RuntimeProcessExecutionPlan.createV5C(programs, grants.toMap, workspaces, workspacegrants, sbt, git, repositories))
              else _valid(if (schema == SCHEMA_V3)
              RuntimeProcessExecutionPlan.createV3C(programs, grants.toMap, workspaces, workspacegrants, sbt, git)
              else RuntimeProcessExecutionPlan.createV2C(programs, grants.toMap, workspaces, workspacegrants, sbt))
          } yield value
      } yield plan
      result.fold(Consequence.configurationInvalid, Consequence.success)
    case _ => Consequence.configurationInvalid("Runtime process plan requires a JSON string")
  }

  def encode(plan: RuntimeProcessExecutionPlan): Consequence[ConfigurationValue] = {
    val programs = plan.programs.map { p => Json.obj(
      "capability" -> Json.fromString(p.capability.print), "identity" -> Json.fromString(p.safeProgramIdentity),
      "executable" -> Json.fromString(p._executable_location), "fixedArguments" -> _strings(p.fixedArguments),
      "argumentTemplates" -> Json.fromValues(p.argumentPolicy.permittedArgumentTemplates.toVector.map(t =>
        Json.fromValues(t.slots.map {
          case ProcessArgumentSlot.Literal(value) => Json.obj("literal" -> Json.fromString(value))
          case ProcessArgumentSlot.LowerHex(lengths, prefix, suffix) => Json.obj(
            "lowerHex" -> Json.fromValues(lengths.toVector.sorted.map(Json.fromInt)),
            "prefix" -> Json.fromString(prefix), "suffix" -> Json.fromString(suffix))
        }))),
      "limits" -> _limits_json(p.maximumLimits),
      "environment" -> Json.obj(p._environment.values.toVector.map { case (k, v) => k -> Json.fromString(v) }*),
      "allowedArtifacts" -> _strings(p.allowedArtifacts.toVector.map(_.value)),
      "allowedInputFiles" -> Json.obj(p.allowedInputFilePaths.toVector.map { case (name, path) =>
        name.value -> Json.fromString(path.value) }*),
      "allowsWorkingDirectory" -> Json.fromBoolean(p.allowsWorkingDirectory)) }
    val grants = plan.grants.toVector.map { case (owner, values) => Json.obj(
      "componentId" -> Json.fromString(owner.name), "capabilities" -> _strings(values.map(_.capability.print))) }
    val generic = Json.obj("schema" -> Json.fromString(plan.schemaVersion),
      "programs" -> Json.fromValues(programs), "grants" -> Json.fromValues(grants))
    val semantic = if (plan.schemaVersion == SCHEMA) generic else generic.mapObject(_.add("workspaces",
      Json.fromValues(plan.workspaceDeclarations.map(x => Json.obj(
        "identity" -> Json.fromString(x.identity.print), "root" -> Json.fromString(x.root.toString)))))
      .add("workspaceGrants", Json.fromValues(plan.workspaceGrants.map(x => Json.obj(
        "componentId" -> Json.fromString(x.componentId.name), "workspaceId" -> Json.fromString(x.workspaceId.print),
        "access" -> Json.fromString(x.permission match {
          case WorkspacePermission.ReadOnly => "read-only"
          case WorkspacePermission.Manage => "manage"
        })))))
      .add("sbtPrograms", Json.fromValues(plan.sbtDeclarations.map(_sbt_json))))
    val gitroot = if (plan.schemaVersion == SCHEMA_V3 || plan.schemaVersion == SCHEMA_V5)
      semantic.mapObject(_.add("gitPrograms", Json.fromValues(plan.gitDeclarations.map(_git_json)))) else semantic
    val root = if (plan.schemaVersion == SCHEMA_V5)
      gitroot.mapObject(_.add("gitRepositories", Json.fromValues(plan.gitRepositoryDeclarations.map(_git_repository_json)))) else gitroot
    val encoded = ConfigurationValue.StringValue(root.noSpaces)
    Consequence.success(encoded)
  }

  private def _sbt_json(x: SbtRuntimeDeclaration): Json = {
    val base = Json.obj(
        "target" -> Json.fromString(x.target.print), "operation" -> Json.fromString(x.operation match {
          case SbtOperation.Compile => "compile"
          case SbtOperation.Test => "test"
          case SbtOperation.TestOnly => "test-only"
          case SbtOperation.FixCheck => "fix-check"
        }), "capability" -> Json.fromString(x.capability.print), "identity" -> Json.fromString(x.identity),
        "sbtExecutable" -> Json.fromString(x.sbtExecutable.toString), "limits" -> _limits_json(x.limits),
        "environment" -> Json.obj(x.environment.toVector.map { case (k, v) => k -> Json.fromString(v) }*))
    if (x.operation == SbtOperation.TestOnly) base.mapObject(_.add("testClasses", _strings(x.testClasses))) else base
  }

  private def _workspace(json: Json, gitroot: Boolean): Either[String, WorkspaceRuntimeDeclaration] = for {
    obj <- _fields(json, Set("identity", "root"))
    text <- _string(obj, "identity")
    identity <- _valid(WorkspaceId.parseC(text))
    roottext <- _string(obj, "root")
    root <- if (gitroot) try {
      val path = Paths.get(roottext)
      Either.cond(path.isAbsolute, path, "Runtime location must be absolute")
    } catch { case NonFatal(_) => Left("Invalid runtime location") } else _absolute_path(roottext)
  } yield WorkspaceRuntimeDeclaration(identity, root)

  private def _workspace_grant(json: Json): Either[String, WorkspaceGrant] = for {
    obj <- _fields(json, Set("componentId", "workspaceId", "access"))
    ownertext <- _string(obj, "componentId")
    owner <- _valid(ComponentId.parseC(ownertext))
    targettext <- _string(obj, "workspaceId")
    target <- _valid(WorkspaceId.parseC(targettext))
    access <- _string(obj, "access")
    permission <- access match {
      case "read-only" => Right(WorkspacePermission.ReadOnly)
      case "manage" => Right(WorkspacePermission.Manage)
      case _ => Left("Invalid workspace permission")
    }
  } yield WorkspaceGrant(owner, target, permission)

  private def _sbt_program(json: Json): Either[String, SbtRuntimeDeclaration] = for {
    operationtext <- json.hcursor.get[String]("operation").left.map(_ => "Invalid typed build operation")
    names = Set("target", "operation", "capability", "identity", "sbtExecutable", "limits", "environment") ++
      (if (operationtext == "test-only") Set("testClasses") else Set.empty[String])
    obj <- _fields(json, names)
    targettext <- _string(obj, "target")
    target <- _valid(WorkspaceId.parseC(targettext))
    operation <- operationtext match {
      case "compile" => Right(SbtOperation.Compile)
      case "test" => Right(SbtOperation.Test)
      case "test-only" => Right(SbtOperation.TestOnly)
      case "fix-check" => Right(SbtOperation.FixCheck)
      case _ => Left("Invalid typed build operation")
    }
    capabilitytext <- _string(obj, "capability")
    capability <- _valid(ProcessCapabilityId.parseC(capabilitytext))
    identity <- _string(obj, "identity")
    executabletext <- _string(obj, "sbtExecutable")
    executable <- _absolute_path(executabletext)
    limits <- _decode_limits(obj("limits").get)
    environment <- _string_map(obj("environment").get)
    classes <- if (operation == SbtOperation.TestOnly) _string_array(obj, "testClasses") else Right(Vector.empty[String])
    _ <- _valid(SbtTestSelectionPolicy.validateC(operation, classes))
  } yield SbtRuntimeDeclaration(target, operation, capability, identity, executable, limits, environment, classes)

  private def _git_program(json: Json): Either[String, GitRuntimeDeclaration] = for {
    operationtext <- json.hcursor.get[String]("operation").left.map(_ => "Invalid typed Git operation")
    operation <- GitOperationKind.values.find(_.wireName == operationtext).toRight("Invalid typed Git operation")
    bounded = Set(GitOperationKind.CommitSelected, GitOperationKind.CheckSelectedIndex, GitOperationKind.SelectedIndexChanges).contains(operation)
    names = Set("target", "operation", "capability", "identity", "gitExecutable", "localBranch",
      "remote", "remoteBranch", "endpoint", "commitIdentity", "selectionId", "selectedPath", "commitMessage", "limits", "environment") ++
      (if (bounded) Set("selectedPaths") else Set.empty[String])
    obj <- _fields(json, names)
    targettext <- _string(obj, "target")
    target <- _valid(WorkspaceId.parseC(targettext))
    capabilitytext <- _string(obj, "capability")
    capability <- _valid(ProcessCapabilityId.parseC(capabilitytext))
    _ <- Either.cond(capability.print == capabilitytext, (), "Noncanonical Git capability")
    identity <- _string(obj, "identity")
    executabletext <- _string(obj, "gitExecutable")
    executable <- _absolute_path(executabletext)
    localtext <- _string(obj, "localBranch")
    localbranch <- _valid(GitBranchName.parseC(localtext))
    remote <- _string(obj, "remote")
    remotetext <- _string(obj, "remoteBranch")
    remotebranch <- _valid(GitBranchName.parseC(remotetext))
    endpoint <- _string(obj, "endpoint")
    commitidentity <- _optional(obj("commitIdentity").get, _commit_identity)
    selection <- _optional(obj("selectionId").get, x => x.asString.toRight("Invalid Git selection").flatMap(s => _valid(GitSelectionId.parseC(s))))
    path <- _optional(obj("selectedPath").get, x => x.asString.toRight("Invalid Git path").flatMap(s => _valid(GitPath.parseC(s))))
    message <- _optional(obj("commitMessage").get, _.asString.toRight("Invalid Git message"))
    limits <- _decode_limits(obj("limits").get)
    environment <- _string_map(obj("environment").get)
    pathtexts <- if (bounded) _string_array(obj, "selectedPaths") else Right(Vector.empty[String])
    paths <- _sequence(pathtexts.map(x => _valid(GitPath.parseC(x))))
  } yield GitRuntimeDeclaration(target, operation, capability, identity, executable, localbranch, remote,
    remotebranch, endpoint, commitidentity, selection, path, message, limits, environment, paths)

  private def _optional[A](json: Json, decode: Json => Either[String, A]): Either[String, Option[A]] =
    if (json.isNull) Right(None) else decode(json).map(Some(_))

  private def _git_repository(json: Json): Either[String, GitRepositoryRuntimeDeclaration] = {
    val names = Set("target", "capability", "identity", "localBranch", "remote",
      "remoteBranch", "endpoint", "commitIdentity", "limits", "environment")
    for {
    obj <- _fields(json, names ++ (if (json.asObject.exists(_.contains("gitExecutable"))) Set("gitExecutable") else Set.empty[String]))
    targettext <- _string(obj, "target")
    target <- _valid(WorkspaceId.parseC(targettext))
    capabilitytext <- _string(obj, "capability")
    capability <- _valid(ProcessCapabilityId.parseC(capabilitytext))
    _ <- Either.cond(capability.print == capabilitytext, (), "Noncanonical Git repository capability")
    identity <- _string(obj, "identity")
    executable <- if (obj.contains("gitExecutable"))
      _string(obj, "gitExecutable").flatMap(_absolute_path).map(Some(_)) else Right(None)
    localtext <- _string(obj, "localBranch")
    localbranch <- _valid(GitBranchName.parseC(localtext))
    remote <- _string(obj, "remote")
    remotetext <- _string(obj, "remoteBranch")
    remotebranch <- _valid(GitBranchName.parseC(remotetext))
    endpoint <- _string(obj, "endpoint")
    commitidentity <- _commit_identity(obj("commitIdentity").get)
    limits <- _decode_limits(obj("limits").get)
    environment <- _string_map(obj("environment").get)
  } yield GitRepositoryRuntimeDeclaration(target, capability, identity, executable, localbranch, remote,
    remotebranch, endpoint, commitidentity, limits, environment)
  }

  private def _git_repository_json(value: GitRepositoryRuntimeDeclaration): Json = {
    val base = Json.obj(
    "target" -> Json.fromString(value.target.print), "capability" -> Json.fromString(value.capability.print),
    "identity" -> Json.fromString(value.identity),
    "localBranch" -> Json.fromString(value.localBranch.value), "remote" -> Json.fromString(value.remote),
    "remoteBranch" -> Json.fromString(value.remoteBranch.value), "endpoint" -> Json.fromString(value.endpoint),
    "commitIdentity" -> Json.obj("name" -> Json.fromString(value.commitIdentity.name), "email" -> Json.fromString(value.commitIdentity.email)),
    "limits" -> _limits_json(value.limits),
    "environment" -> Json.obj(value.environment.toVector.map { case (k, v) => k -> Json.fromString(v) }*))
    value.gitExecutable.fold(base)(x => base.mapObject(_.add("gitExecutable", Json.fromString(x.toString))))
  }

  private def _commit_identity(json: Json): Either[String, GitCommitIdentity] = for {
    obj <- _fields(json, Set("name", "email"))
    name <- _string(obj, "name")
    email <- _string(obj, "email")
  } yield GitCommitIdentity(name, email)

  private def _git_json(value: GitRuntimeDeclaration): Json = {
    val base = Json.obj(
    "target" -> Json.fromString(value.target.print), "operation" -> Json.fromString(value.operation.wireName),
    "capability" -> Json.fromString(value.capability.print), "identity" -> Json.fromString(value.identity),
    "gitExecutable" -> Json.fromString(value.gitExecutable.toString), "localBranch" -> Json.fromString(value.localBranch.value),
    "remote" -> Json.fromString(value.remote), "remoteBranch" -> Json.fromString(value.remoteBranch.value),
    "endpoint" -> Json.fromString(value.endpoint),
    "commitIdentity" -> value.commitIdentity.fold(Json.Null)(x => Json.obj("name" -> Json.fromString(x.name), "email" -> Json.fromString(x.email))),
    "selectionId" -> value.selectionId.fold(Json.Null)(x => Json.fromString(x.value)),
    "selectedPath" -> value.selectedPath.fold(Json.Null)(x => Json.fromString(x.value)),
    "commitMessage" -> value.commitMessage.fold(Json.Null)(Json.fromString),
    "limits" -> _limits_json(value.limits), "environment" -> Json.obj(value.environment.toVector.map { case (k, v) => k -> Json.fromString(v) }*))
    if (Set(GitOperationKind.CommitSelected, GitOperationKind.CheckSelectedIndex, GitOperationKind.SelectedIndexChanges).contains(value.operation))
      base.mapObject(_.add("selectedPaths", _strings(value.selectedPaths.map(_.value)))) else base
  }

  private def _absolute_path(text: String): Either[String, java.nio.file.Path] =
    try {
      val path = Paths.get(text)
      Either.cond(path.isAbsolute && !text.exists(_.isControl), path, "Runtime location must be absolute")
    } catch { case NonFatal(_) => Left("Invalid runtime location") }

  private def _program(json: Json): Either[String, ProcessProgramDefinition] = for {
    obj <- _fields(json, Set("capability", "identity", "executable", "fixedArguments", "argumentTemplates",
      "limits", "environment", "allowedArtifacts", "allowedInputFiles", "allowsWorkingDirectory"))
    capabilitytext <- _string(obj, "capability")
    capability <- _valid(ProcessCapabilityId.parseC(capabilitytext))
    identity <- _string(obj, "identity")
    executable <- _string(obj, "executable")
    _ <- try Either.cond(Paths.get(executable).isAbsolute, (), "Runtime executable must be absolute")
      catch { case NonFatal(_) => Left("Invalid runtime executable") }
    prefix <- _string_array(obj, "fixedArguments")
    templatevalues <- _array(obj, "argumentTemplates")
    templates <- _sequence(templatevalues.map(_template))
    limits <- _decode_limits(obj("limits").get)
    environment <- _string_map(obj("environment").get)
    artifacttexts <- _string_array(obj, "allowedArtifacts")
    artifacts <- _sequence(artifacttexts.map(x => _valid(ProcessArtifactName.parseC(x))))
    filetexts <- _string_map(obj("allowedInputFiles").get)
    files <- _sequence(filetexts.toVector.map { case (name, path) => for {
      namevalue <- _valid(ProcessArtifactName.parseC(name))
      pathvalue <- _valid(WorkAreaRelativePath.parseC(path))
    } yield namevalue -> pathvalue })
    directory <- obj("allowsWorkingDirectory").flatMap(_.asBoolean).toRight("Working-directory policy requires a boolean")
    definition <- _valid(ProcessProgramDefinition.fromRuntimeC(capability, identity, executable, prefix,
      ProcessArgumentPolicy(prefix, admission = ProcessArgumentAdmission.Template, permittedArgumentTemplates = templates.toSet),
      limits, artifacts.toSet, allowsworkingdirectory = directory, allowedinputfiles = files.map(_._1).toSet,
      allowedinputfilepaths = files.toMap, environment = environment))
  } yield definition

  private def _template(json: Json): Either[String, ProcessArgumentTemplate] =
    json.asArray.toRight("Process template requires an ordered array").flatMap { values =>
      _sequence(values.map(_slot)).map(ProcessArgumentTemplate.apply)
    }
  private def _slot(json: Json): Either[String, ProcessArgumentSlot] = json.asObject match {
    case Some(obj) if obj.keys.toSet == Set("literal") => _string(obj, "literal").map(ProcessArgumentSlot.Literal.apply)
    case Some(obj) if obj.keys.toSet == Set("lowerHex", "prefix", "suffix") => for {
      values <- _array(obj, "lowerHex")
      lengths <- _sequence(values.map(_.asNumber.flatMap(_.toInt).toRight("Lower-hex lengths require integers")))
      prefix <- _string(obj, "prefix")
      suffix <- _string(obj, "suffix")
    } yield ProcessArgumentSlot.LowerHex(lengths.toSet, prefix, suffix)
    case _ => Left("Invalid process argument slot")
  }
  private def _grant(json: Json): Either[String, (ComponentId, Vector[ProcessExecutionGrant])] = for {
    obj <- _fields(json, Set("componentId", "capabilities"))
    ownertext <- _string(obj, "componentId")
    owner <- _valid(ComponentId.parseC(ownertext))
    texts <- _string_array(obj, "capabilities")
    capabilities <- _sequence(texts.map(x => _valid(ProcessCapabilityId.parseC(x))))
    _ <- Either.cond(capabilities.distinct.size == capabilities.size, (), "Duplicate process grant")
  } yield owner -> capabilities.map(ProcessExecutionGrant(_))
  private def _decode_limits(json: Json): Either[String, ProcessExecutionLimits] = for {
    obj <- _fields(json, _limit_names.toSet)
    values <- _sequence(_limit_names.map(name => obj(name).flatMap(_.asNumber).flatMap(_.toLong)
      .filter(_ > 0L).toRight("Process limits require finite positive integers")))
  } yield ProcessExecutionLimits(Some(values(0)), Some(values(1)), Some(values(2)), Some(values(3)), Some(values(4)),
    Some(values(5)), Some(values(6)), Some(values(7)), Some(values(8)), Some(values(9)), Some(values(10)))
  private def _limits_json(l: ProcessExecutionLimits): Json = {
    val values = Vector(l.launchTimeoutMillis, l.executionTimeoutMillis, l.terminationGraceMillis, l.stdinBytes,
      l.stdoutBytes, l.stderrBytes, l.argumentCount, l.argumentBytes, l.artifactCount, l.artifactBytes, l.workAreaBytes)
    Json.obj(_limit_names.zip(values).map { case (name, value) => name -> Json.fromLong(value.get) }*)
  }
  private def _fields(json: Json, names: Set[String]): Either[String, JsonObject] =
    json.asObject.filter(_.keys.toSet == names).toRight("Invalid runtime process object fields")
  private def _string(obj: JsonObject, name: String): Either[String, String] =
    obj(name).flatMap(_.asString).toRight("Process field requires a string")
  private def _array(obj: JsonObject, name: String): Either[String, Vector[Json]] =
    obj(name).flatMap(_.asArray).toRight("Process field requires an array")
  private def _string_array(obj: JsonObject, name: String): Either[String, Vector[String]] =
    _array(obj, name).flatMap(values => _sequence(values.map(_.asString.toRight("Process array requires strings"))))
  private def _string_map(json: Json): Either[String, Map[String, String]] =
    json.asObject.toRight("Process field requires an object").flatMap(obj =>
      _sequence(obj.toVector.map { case (k, v) => v.asString.toRight("Process map requires strings").map(k -> _) }).map(_.toMap))
  private def _strings(values: Vector[String]): Json = Json.fromValues(values.map(Json.fromString))
  private def _valid[A](value: Consequence[A]): Either[String, A] = value.toOption.toRight("Invalid runtime process declaration")
  private def _valid_v5[A](value: Consequence[A]): Either[String, A] = value match {
    case Consequence.Success(result) => Right(result)
    case Consequence.Failure(conclusion) => Left(conclusion.toString)
  }
  private def _sequence[A](values: Vector[Either[String, A]]): Either[String, Vector[A]] =
    values.foldLeft[Either[String, Vector[A]]](Right(Vector.empty)) { (acc, next) => for {
      prior <- acc
      value <- next
    } yield prior :+ value }
}
