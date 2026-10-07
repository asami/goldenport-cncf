package org.goldenport.cncf.processexecution

import java.nio.file.Paths
import scala.util.control.NonFatal
import io.circe.{Json, JsonObject}
import io.circe.jawn.JawnParser
import org.goldenport.Consequence
import org.goldenport.cncf.component.ComponentId
import org.goldenport.configuration.{ConfigurationValue, ConfigurationValueCodec}

/** Host wire contract: complete literal/lower-hex vectors only, never arbitrary shell text. */
/*
 * @since Oct.  7, 2026
 * @version Oct.  7, 2026
 */
private[cncf] object RuntimeProcessExecutionPlanCodec extends ConfigurationValueCodec[RuntimeProcessExecutionPlan] {
  val SCHEMA = "cncf.runtime-process-execution.v1"
  private val _parser = JawnParser(allowDuplicateKeys = false)
  private val _limit_names = Vector("launchTimeoutMillis", "executionTimeoutMillis", "terminationGraceMillis",
    "stdinBytes", "stdoutBytes", "stderrBytes", "argumentCount", "argumentBytes", "artifactCount", "artifactBytes", "workAreaBytes")

  def decode(value: ConfigurationValue): Consequence[RuntimeProcessExecutionPlan] = value match {
    case ConfigurationValue.StringValue(text) =>
      val result = for {
        json <- try _parser.parse(text).left.map(_ => "Invalid runtime process JSON")
          catch { case NonFatal(_) => Left("Invalid runtime process JSON") }
        root <- _fields(json, Set("schema", "programs", "grants"))
        schema <- _string(root, "schema")
        _ <- Either.cond(schema == SCHEMA, (), "Unsupported runtime process schema")
        programvalues <- _array(root, "programs")
        programs <- _sequence(programvalues.map(_program))
        grantvalues <- _array(root, "grants")
        grants <- _sequence(grantvalues.map(_grant))
        _ <- Either.cond(grants.map(_._1).distinct.size == grants.size, (), "Duplicate process grant owner")
        plan <- _valid(RuntimeProcessExecutionPlan.createC(programs, grants.toMap))
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
    val encoded = ConfigurationValue.StringValue(Json.obj("schema" -> Json.fromString(SCHEMA),
      "programs" -> Json.fromValues(programs), "grants" -> Json.fromValues(grants)).noSpaces)
    Consequence.success(encoded)
  }

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
  private def _sequence[A](values: Vector[Either[String, A]]): Either[String, Vector[A]] =
    values.foldLeft[Either[String, Vector[A]]](Right(Vector.empty)) { (acc, next) => for {
      prior <- acc
      value <- next
    } yield prior :+ value }
}
