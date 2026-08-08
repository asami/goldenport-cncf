package org.goldenport.cncf.config

import org.goldenport.{Conclusion, Consequence}
import org.goldenport.cncf.component.{ComponentId, ComponentInstanceId}
import org.goldenport.observation.{Cause, Descriptor}
import org.goldenport.record.Record

/*
 * Payload-safe diagnostics for component initialization parameter resolution.
 *
 * @since   Jul. 22, 2026
 * @version Aug.  8, 2026
 * @author  ASAMI, Tomoharu
 */
private[cncf] enum ComponentParameterDiagnosticOutcome(val token: String) {
  case Resolved extends ComponentParameterDiagnosticOutcome("resolved")
  case Absent extends ComponentParameterDiagnosticOutcome("absent")
  case Empty extends ComponentParameterDiagnosticOutcome("empty")
  case Missing extends ComponentParameterDiagnosticOutcome("missing")
  case Malformed extends ComponentParameterDiagnosticOutcome("malformed")
  case Ambiguous extends ComponentParameterDiagnosticOutcome("ambiguous")
  case Rejected extends ComponentParameterDiagnosticOutcome("rejected")
}

private[cncf] final case class ComponentParameterDiagnosticSummary(
  parameterName: String,
  requirement: ComponentParameterRequirement,
  confidentiality: ComponentParameterConfidentiality,
  provenance: ComponentParameterProvenance,
  outcome: ComponentParameterDiagnosticOutcome
) {
  def toRecord: Record = Record.data(
    "parameter" -> parameterName,
    "requirement" -> ComponentParameterDiagnostics.requirementToken(requirement),
    "confidentiality" -> ComponentParameterDiagnostics.confidentialityToken(confidentiality),
    "provenance" -> provenance.token,
    "outcome" -> outcome.token
  )
}

private[cncf] object ComponentParameterDiagnostics {
  val POLICY = "component-initialization-parameter"

  def summary[A](
    key: ComponentParameterKey[A],
    resolution: ComponentParameterResolution[A]
  ): ComponentParameterDiagnosticSummary =
    ComponentParameterDiagnosticSummary(
      key.name,
      key.requirement,
      key.confidentiality,
      resolution.provenance,
      if (resolution.value.isDefined)
        ComponentParameterDiagnosticOutcome.Resolved
      else
        ComponentParameterDiagnosticOutcome.Absent
    )

  def missing[A](
    key: ComponentParameterKey[A]
  ): Consequence.Failure[ComponentParameterResolution[A]] =
    _parameter_failure(
      s"required component initialization parameter is missing: ${key.name}",
      Cause.Kind.Policy,
      ComponentParameterDiagnosticOutcome.Missing,
      key
    )

  def malformed[A](
    key: ComponentParameterKey[A],
    provenance: ComponentParameterProvenance
  ): Consequence.Failure[ComponentParameterResolution[A]] =
    _parameter_failure(
      s"component initialization parameter is malformed: ${key.name}",
      Cause.Kind.Format,
      ComponentParameterDiagnosticOutcome.Malformed,
      key,
      Vector(Descriptor.Facet.Key(s"${_provenance_key_prefix}${provenance.token}"))
    )

  def rejected[A](
    key: ComponentParameterKey[A],
    message: String
  ): Consequence.Failure[ComponentParameterResolution[A]] =
    _parameter_failure(
      message,
      Cause.Kind.Policy,
      ComponentParameterDiagnosticOutcome.Rejected,
      key
    )

  def duplicateDeclaration[A](name: String): Consequence.Failure[A] =
    _failure(
      s"duplicate component initialization parameter declaration: $name",
      Cause.Kind.Conflict,
      ComponentParameterDiagnosticOutcome.Ambiguous,
      Vector(Descriptor.Facet.Parameter.argument(name))
    )

  def undeclared[A](name: String): Consequence.Failure[A] =
    _failure(
      s"component initialization parameter was not declared in this snapshot: $name",
      Cause.Kind.Policy,
      ComponentParameterDiagnosticOutcome.Rejected,
      Vector(Descriptor.Facet.Parameter.argument(name))
    )

  def contextMissing[A](
    message: String,
    componentid: ComponentId,
    instanceid: ComponentInstanceId
  ): Consequence.Failure[A] =
    _context_failure(
      message,
      Cause.Kind.Policy,
      ComponentParameterDiagnosticOutcome.Missing,
      componentid,
      instanceid
    )

  def contextAmbiguous[A](
    message: String,
    componentid: ComponentId,
    instanceid: ComponentInstanceId
  ): Consequence.Failure[A] =
    _context_failure(
      message,
      Cause.Kind.Conflict,
      ComponentParameterDiagnosticOutcome.Ambiguous,
      componentid,
      instanceid
    )

  def contextRejected[A](
    message: String,
    componentid: ComponentId,
    instanceid: ComponentInstanceId
  ): Consequence.Failure[A] =
    _context_failure(
      message,
      Cause.Kind.Policy,
      ComponentParameterDiagnosticOutcome.Rejected,
      componentid,
      instanceid
    )

  def requirementToken(
    requirement: ComponentParameterRequirement
  ): String = requirement match {
    case ComponentParameterRequirement.Required => "required"
    case ComponentParameterRequirement.Optional => "optional"
  }

  def confidentialityToken(
    confidentiality: ComponentParameterConfidentiality
  ): String = confidentiality match {
    case ComponentParameterConfidentiality.Public => "public"
    case ComponentParameterConfidentiality.Confidential => "confidential"
    case ComponentParameterConfidentiality.Secret => "secret"
  }

  def isParameterFailure(conclusion: Conclusion): Boolean =
    conclusion.observation.cause.descriptor.facets.exists {
      case Descriptor.Facet.Policy(POLICY) => true
      case _ => false
    }

  def provenance(conclusion: Conclusion): Option[ComponentParameterProvenance] = {
    val tokens = conclusion.observation.cause.descriptor.facets.collect {
      case Descriptor.Facet.Key(key) if key.startsWith(_provenance_key_prefix) =>
        key.drop(_provenance_key_prefix.length)
    }.toSet
    ComponentParameterProvenance.values.find(x => tokens == Set(x.token))
  }

  private def _parameter_failure[A, B](
    message: String,
    kind: Cause.Kind,
    outcome: ComponentParameterDiagnosticOutcome,
    key: ComponentParameterKey[A],
    facets: Vector[Descriptor.Facet] = Vector.empty
  ): Consequence.Failure[B] =
    _failure(
      message,
      kind,
      outcome,
      Vector(Descriptor.Facet.Parameter.argument(key.name)) ++ facets
    )

  private def _provenance_key_prefix: String =
    "component-parameter-provenance:"

  private def _context_failure[A](
    message: String,
    kind: Cause.Kind,
    outcome: ComponentParameterDiagnosticOutcome,
    componentid: ComponentId,
    instanceid: ComponentInstanceId
  ): Consequence.Failure[A] =
    _failure(
      message,
      kind,
      outcome,
      Vector(
        Descriptor.Facet.Id(componentid.name),
        Descriptor.Facet.Id(instanceid.canonicalKey)
      )
    )

  private def _failure[A](
    message: String,
    kind: Cause.Kind,
    outcome: ComponentParameterDiagnosticOutcome,
    facets: Vector[Descriptor.Facet]
  ): Consequence.Failure[A] =
    Consequence.configurationInvalid(
      message,
      kind,
      Vector(
        Descriptor.Facet.Policy(POLICY),
        Descriptor.Facet.Reason(outcome.token)
      ) ++ facets
    )
}
