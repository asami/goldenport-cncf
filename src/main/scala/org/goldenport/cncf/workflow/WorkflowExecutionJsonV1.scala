package org.goldenport.cncf.workflow

import io.circe.Json
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.goldenport.cncf.workflow.WorkflowResultJsonV1.{_fields, _string, _expect, _returned_fields, _text}

/** Shared requirement/evidence projection for issued work and returned results.
  * Missing optional legacy fields remain absent; decoding grants no placement.
  * @since   Oct.  8, 2026
  * @version Oct.  9, 2026
  */
private[workflow] object WorkflowExecutionJsonV1 {
  def requirement(value: ExecutionRequirement): Json = {
    val fields = Vector(
      "capabilities" -> Json.fromValues(value.capabilities.map(x => Json.fromString(x.identity))),
      "risk" -> Json.fromString(value.risk.value),
      "reasoning" -> Json.fromString(value.reasoning.value),
      "reviewRequired" -> Json.fromBoolean(value.reviewRequired)
    ) ++ value.disposition.toVector.map(x => "disposition" -> Json.obj(
      "placement" -> Json.fromString(x.placement.value),
      "independence" -> Json.fromString(x.independence.value),
      "originContextIdentity" -> Json.fromString(x.originContextIdentity)
    )) ++ value.reasoningClass.toVector.map(x => "reasoningClass" -> Json.fromString(x.value))
    Json.obj(fields*)
  }

  def decodeRequirement(json: Json): Either[String, ExecutionRequirement] = for {
    fields <- _declared_fields(json, Set("capabilities", "risk", "reasoning", "reviewRequired"),
      Set("disposition", "reasoningClass"))
    values <- fields("capabilities").asArray.toRight("capabilities must be an array")
    capabilities <- values.foldLeft[Either[String, Vector[CapabilityRequirement]]](Right(Vector.empty)) {
      case (prior, value) => for {
        accumulated <- prior
        identity <- value.asString.filter(_.trim.nonEmpty).toRight("invalid capability")
      } yield accumulated :+ CapabilityRequirement(identity)
    }
    risk <- _string(fields, "risk")
    reasoning <- _string(fields, "reasoning")
    level <- ReasoningLevel.values.find(_.value == reasoning).toRight("unsupported reasoning level")
    review <- fields("reviewRequired").asBoolean.toRight("reviewRequired must be boolean")
    disposition <- fields.get("disposition") match {
      case None => Right(None)
      case Some(value) => _decode_disposition(value).map(Some(_))
    }
    reasoningclass <- fields.get("reasoningClass") match {
      case None => Right(None)
      case Some(value) => value.asString.filter(_.trim.nonEmpty).map(x => Some(ReasoningClassReference(x)))
        .toRight("reasoningClass must be a nonempty semantic class reference")
    }
  } yield ExecutionRequirement(capabilities, RiskLevel(risk), level, review, disposition, reasoningclass)

  /** Convert returned diagnostic copies without applying issued-selection rules. */
  def decodeReturnedRequirement(json: Json): Either[String, ExecutionRequirement] = for {
    fields <- _returned_fields(json, Set("capabilities", "risk", "reasoning", "reviewRequired"))
    values <- fields("capabilities").asArray.toRight("capabilities must be an array")
    capabilities <- values.foldLeft[Either[String, Vector[CapabilityRequirement]]](Right(Vector.empty)) {
      case (prior, value) => for {
        accumulated <- prior
        identity <- value.asString.toRight("capability must be a string")
      } yield accumulated :+ CapabilityRequirement(identity)
    }
    risk <- _text(fields, "risk")
    reasoning <- _text(fields, "reasoning")
    level <- ReasoningLevel.values.find(_.value == reasoning).toRight("unsupported reasoning level")
    review <- fields("reviewRequired").asBoolean.toRight("reviewRequired must be boolean")
    disposition <- fields.get("disposition") match {
      case None => Right(None)
      case Some(value) => for {
        data <- _returned_fields(value, Set("placement", "independence", "originContextIdentity"))
        place <- _text(data, "placement")
        placement <- ExecutionPlacement.values.find(_.value == place).toRight("unsupported placement")
        mode <- _text(data, "independence")
        independence <- ExecutionIndependence.values.find(_.value == mode).toRight("unsupported independence")
        origin <- _text(data, "originContextIdentity")
      } yield Some(ExecutionDisposition(placement, independence, origin))
    }
    reasoningclass <- fields.get("reasoningClass") match {
      case None => Right(None)
      case Some(value) => value.asString.map(x => Some(ReasoningClassReference(x)))
        .toRight("reasoningClass must be a string")
    }
  } yield ExecutionRequirement(capabilities, RiskLevel(risk), level, review, disposition, reasoningclass)

  def decodeReturnedObservation(json: Json): Either[String, ExecutionObservation] = for {
    fields <- _returned_fields(json, Set("placement", "originContextIdentity", "executorContextIdentity", "profileIdentity"))
    place <- _text(fields, "placement")
    placement <- ExecutionPlacement.values.find(_.value == place).toRight("unsupported placement")
    origin <- _text(fields, "originContextIdentity")
    executor <- _text(fields, "executorContextIdentity")
    profile <- _text(fields, "profileIdentity")
  } yield ExecutionObservation(placement, origin, executor, profile)

  def observation(value: ExecutionObservation): Json = Json.obj(
    "placement" -> Json.fromString(value.placement.value),
    "originContextIdentity" -> Json.fromString(value.originContextIdentity),
    "executorContextIdentity" -> Json.fromString(value.executorContextIdentity),
    "profileIdentity" -> Json.fromString(value.profileIdentity)
  )

  def decodeObservation(json: Json): Either[String, ExecutionObservation] = for {
    fields <- _fields(json, Set("placement", "originContextIdentity", "executorContextIdentity", "profileIdentity"))
    placement <- _decode_placement(fields)
    origin <- _string(fields, "originContextIdentity")
    executor <- _string(fields, "executorContextIdentity")
    profile <- _string(fields, "profileIdentity")
  } yield ExecutionObservation(placement, origin, executor, profile)

  def evidenceFields(json: Json): Either[String, Map[String, Json]] =
    _declared_fields(json, Set("references", "workerIdentity", "modelIdentity", "skillDispatch"), Set("execution"))

  private def _decode_disposition(json: Json): Either[String, ExecutionDisposition] = for {
    fields <- _fields(json, Set("placement", "independence", "originContextIdentity"))
    placement <- _decode_placement(fields)
    independent <- _string(fields, "independence")
    independence <- ExecutionIndependence.values.find(_.value == independent).toRight("unsupported independence")
    origin <- _string(fields, "originContextIdentity")
  } yield ExecutionDisposition(placement, independence, origin)

  private def _decode_placement(fields: Map[String, Json]): Either[String, ExecutionPlacement] =
    _string(fields, "placement").flatMap(x => ExecutionPlacement.values.find(_.value == x).toRight("unsupported placement"))

  private def _declared_fields(json: Json, required: Set[String], optional: Set[String]): Either[String, Map[String, Json]] =
    json.asObject.toRight("execution projection must be an object").flatMap { fields =>
      _expect(required.subsetOf(fields.keys.toSet) && fields.keys.toSet.subsetOf(required ++ optional),
        "execution projection fields are missing or unknown").map(_ => fields.toMap)
    }
}
