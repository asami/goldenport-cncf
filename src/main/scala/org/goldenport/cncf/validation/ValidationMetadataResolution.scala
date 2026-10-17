package org.goldenport.cncf.validation

import java.util.concurrent.TimeUnit.NANOSECONDS
import scala.concurrent.duration.FiniteDuration
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.WorkflowProtocolV1.ExecutionRequirement
import ValidationMetadataV1.*

/** Pure admission and composition of the V1 declaration contract.
 * @since   Oct. 18, 2026
 * @version Oct. 18, 2026
 */
private[validation] object ValidationMetadataResolution {
  private val _required_semantics = Set(
    "purposes", "features", "expectedDuration", "durationClass", "executionRequirement"
  )

  def validateFeatureC(feature: FeatureReference): Consequence[FeatureReference] =
    if (feature == null) {
      _failure("features", "feature", "scoped feature reference", "null")
    } else {
      Consequence.map2(
        _feature_text(feature.scope, "features.scope"),
        _feature_text(feature.identity, "features.identity")
      )((_, _) => feature)
    }

  def validateDeclarationC(declaration: Declaration): Consequence[Declaration] =
    if (declaration == null) {
      _failure("declaration", "declaration", "declaration", "null")
    } else {
      Consequence.map6(
        _compatibility(declaration),
        _optional(declaration.purposes, "purposes")(_purposes),
        _features(declaration.features),
        _optional(declaration.expectedDuration, "expectedDuration")(_duration),
        _optional(declaration.durationClass, "durationClass")(Consequence.success(_)),
        _optional(declaration.executionRequirement, "executionRequirement")(_requirement)
      ) { (_, purposes, features, duration, durationclass, requirement) =>
        declaration.copy(
          purposes = purposes, features = features, expectedDuration = duration,
          durationClass = durationclass, executionRequirement = requirement
        )
      }
    }

  def resolveC(
    specification: Option[Declaration],
    operation: Option[Declaration]
  ): Consequence[Metadata] =
    Consequence.map2(
      _optional(specification, "specification")(validateDeclarationC),
      _optional(operation, "operation")(validateDeclarationC)
    )((_, _)).flatMap { case (specification, operation) =>
      val defaults = specification.getOrElse(Declaration())
      val overrides = operation.getOrElse(Declaration())
      _compose_requirement(defaults.executionRequirement, overrides.executionRequirement).map { requirement =>
        Metadata(
          overrides.purposes.orElse(defaults.purposes).getOrElse(Set.empty),
          defaults.features ++ overrides.features,
          overrides.expectedDuration.orElse(defaults.expectedDuration),
          overrides.durationClass.orElse(defaults.durationClass).getOrElse(DurationClass.NORMAL),
          requirement,
          Origins(specification.nonEmpty, operation.nonEmpty)
        )
      }
    }

  private def _feature_text(value: String, field: String): Consequence[String] =
    if (value == null || value.isEmpty || value != value.trim ||
        value.length > 512 || value.exists(_.isControl)) {
      _failure(field, "feature", "nonempty trimmed text without controls, at most 512 characters", value)
    } else {
      Consequence.success(value)
    }

  private def _compatibility(value: Declaration): Consequence[Unit] = {
    val abi = if (value.abiVersion == ABI_VERSION) Consequence.unit
      else _failure[Unit]("abiVersion", "abi", ABI_VERSION, value.abiVersion)
    val semantics = if (value.requiredSemantics == null) {
      _failure[Unit]("requiredSemantics", "required-semantics", "known required semantic names", "null")
    } else {
      val unknown = value.requiredSemantics.filter(x => x == null || !_required_semantics.contains(x))
      if (unknown.isEmpty) Consequence.unit
      else _failure[Unit]("requiredSemantics", "required-semantics",
        _required_semantics.toVector.sorted, unknown.toVector.map(x => String.valueOf(x)).sorted)
    }
    Consequence.map2(abi, semantics)((_, _) => ())
  }

  private def _purposes(value: Set[Purpose]): Consequence[Set[Purpose]] =
    if (value.contains(null)) {
      _failure("purposes", "purposes", "purpose members", "null member")
    } else {
      Consequence.success(value)
    }

  private def _features(value: Set[FeatureReference]): Consequence[Set[FeatureReference]] =
    if (value == null) {
      _failure("features", "feature", "feature set", "null")
    } else {
      val ordered = value.toVector.sortBy { feature =>
        if (feature == null) ("", "")
        else (Option(feature.scope).getOrElse(""), Option(feature.identity).getOrElse(""))
      }
      ordered.foldLeft(Consequence.unit) { (result, feature) =>
        Consequence.map2(result, validateFeatureC(feature))((_, _) => ())
      }.map(_ => value)
    }

  private def _duration(value: FiniteDuration): Consequence[FiniteDuration] = {
    val nanos = BigInt(value.length) * BigInt(value.unit.toNanos(1L))
    if (nanos <= 0 || !nanos.isValidLong) {
      _failure("expectedDuration", "duration", "positive duration representable in nanoseconds", value.toString)
    } else {
      Consequence.success(FiniteDuration(nanos.longValue, NANOSECONDS))
    }
  }

  private def _requirement(value: ExecutionRequirement): Consequence[ExecutionRequirement] =
    value.validateC.recoverWith { _ =>
      _failure("executionRequirement", "execution-requirement", "valid WorkflowProtocolV1.ExecutionRequirement", "invalid")
    }.map { admitted =>
      admitted.copy(capabilities = admitted.capabilities.distinct.sortBy(_.identity))
    }

  private def _compose_requirement(
    defaults: Option[ExecutionRequirement],
    overrides: Option[ExecutionRequirement]
  ): Consequence[Option[ExecutionRequirement]] = (defaults, overrides) match {
    case (None, value) => Consequence.success(value)
    case (value, None) => Consequence.success(value)
    case (Some(left), Some(right)) =>
      Consequence.map3(
        if (left.risk == right.risk) Consequence.success(left.risk)
        else _failure("executionRequirement.risk", "execution-requirement.risk", left.risk, right.risk),
        _matching_optional(left.disposition, right.disposition, "disposition"),
        _matching_optional(left.reasoningClass, right.reasoningClass, "reasoningClass")
      ) { (risk, disposition, reasoningclass) =>
        Some(ExecutionRequirement(
          (left.capabilities ++ right.capabilities).distinct.sortBy(_.identity),
          risk,
          if (left.reasoning.ordinal >= right.reasoning.ordinal) left.reasoning else right.reasoning,
          left.reviewRequired || right.reviewRequired,
          disposition,
          reasoningclass
        ))
      }
  }

  private def _matching_optional[A](left: Option[A], right: Option[A], field: String): Consequence[Option[A]] =
    (left, right) match {
      case (Some(a), Some(b)) if a != b =>
        _failure(s"executionRequirement.$field", s"execution-requirement.$field", a, b)
      case _ => Consequence.success(right.orElse(left))
    }

  private def _optional[A](value: Option[A], field: String)(validate: A => Consequence[A]): Consequence[Option[A]] =
    if (value == null) {
      _failure(field, "null", "None or Some(non-null value)", "null option")
    } else {
      value match {
        case None => Consequence.success(None)
        case Some(null) => _failure(field, "null", "non-null value", "null")
        case Some(present) => validate(present).map(Some(_))
      }
    }

  private def _failure[A](field: String, policy: String, expected: Any, actual: Any): Consequence[A] =
    Consequence.argumentPolicyViolation(field, s"validation.metadata.$policy", expected, actual)
}
