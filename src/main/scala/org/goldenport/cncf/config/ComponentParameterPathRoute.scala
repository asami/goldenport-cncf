package org.goldenport.cncf.config

import org.goldenport.Consequence

/*
 * @since   Aug.  5, 2026
 * @version Aug.  5, 2026
 * @author  ASAMI, Tomoharu
 */
final class ComponentParameterPathSegment private[config] (
  val value: String
) {
  override def toString: String = value
}

final case class ComponentParameterPathLimits private (
  maximumSegments: Int
)

object ComponentParameterPathLimits {
  val standard: ComponentParameterPathLimits =
    new ComponentParameterPathLimits(256)

  def createC(maximumSegments: Int): Consequence[ComponentParameterPathLimits] =
    if (maximumSegments > 0 && maximumSegments <= 256)
      Consequence.success(new ComponentParameterPathLimits(maximumSegments))
    else
      ComponentParameterPathDiagnostics.rejected(
        "component initialization parameter path segment limit must be between 1 and 256",
        "path-route-limit"
      )
}

final class ComponentParameterPathLeaf[A] private (
  val name: String,
  val aliases: Vector[String],
  val requirement: ComponentParameterRequirement,
  val confidentiality: ComponentParameterConfidentiality,
  decoder: ComponentParameterDecoder[A]
) {
  private val _decoder = decoder

  private[config] def concreteKey(name: String): ComponentParameterKey[A] =
    ComponentParameterKey._create(
      name,
      _decoder,
      requirement,
      confidentiality
    )
}

object ComponentParameterPathLeaf {
  def required[A](
    name: String,
    decoder: ComponentParameterDecoder[A],
    aliases: Vector[String] = Vector.empty
  ): Consequence[ComponentParameterPathLeaf[A]] =
    _create(name, aliases, ComponentParameterRequirement.Required, ComponentParameterConfidentiality.Public, decoder)

  def optional[A](
    name: String,
    decoder: ComponentParameterDecoder[A],
    aliases: Vector[String] = Vector.empty
  ): Consequence[ComponentParameterPathLeaf[A]] =
    _create(name, aliases, ComponentParameterRequirement.Optional, ComponentParameterConfidentiality.Public, decoder)

  def requiredString(
    name: String,
    aliases: Vector[String] = Vector.empty
  ): Consequence[ComponentParameterPathLeaf[String]] =
    required(name, ComponentParameterDecoder.string, aliases)

  def optionalString(
    name: String,
    aliases: Vector[String] = Vector.empty
  ): Consequence[ComponentParameterPathLeaf[String]] =
    optional(name, ComponentParameterDecoder.string, aliases)

  def requiredInt(
    name: String,
    aliases: Vector[String] = Vector.empty
  ): Consequence[ComponentParameterPathLeaf[Int]] =
    required(name, ComponentParameterDecoder.int, aliases)

  def optionalInt(
    name: String,
    aliases: Vector[String] = Vector.empty
  ): Consequence[ComponentParameterPathLeaf[Int]] =
    optional(name, ComponentParameterDecoder.int, aliases)

  def requiredBoolean(
    name: String,
    aliases: Vector[String] = Vector.empty
  ): Consequence[ComponentParameterPathLeaf[Boolean]] =
    required(name, ComponentParameterDecoder.boolean, aliases)

  def optionalBoolean(
    name: String,
    aliases: Vector[String] = Vector.empty
  ): Consequence[ComponentParameterPathLeaf[Boolean]] =
    optional(name, ComponentParameterDecoder.boolean, aliases)

  def requiredSecretReference(
    name: String,
    aliases: Vector[String] = Vector.empty
  ): Consequence[ComponentParameterPathLeaf[SecretReference]] =
    _create(
      name,
      aliases,
      ComponentParameterRequirement.Required,
      ComponentParameterConfidentiality.Secret,
      ComponentParameterDecoder._secret_reference
    )

  def optionalSecretReference(
    name: String,
    aliases: Vector[String] = Vector.empty
  ): Consequence[ComponentParameterPathLeaf[SecretReference]] =
    _create(
      name,
      aliases,
      ComponentParameterRequirement.Optional,
      ComponentParameterConfidentiality.Secret,
      ComponentParameterDecoder._secret_reference
    )

  def confidentialRequired(
    name: String,
    aliases: Vector[String] = Vector.empty
  ): Consequence[ComponentParameterPathLeaf[Nothing]] =
    _create(
      name,
      aliases,
      ComponentParameterRequirement.Required,
      ComponentParameterConfidentiality.Confidential,
      ComponentParameterDecoder(_ =>
        Consequence.configurationInvalid(
          "confidential component initialization parameter is not available through the component boundary"
        )
      )
    )

  private def _create[A](
    name: String,
    aliases: Vector[String],
    requirement: ComponentParameterRequirement,
    confidentiality: ComponentParameterConfidentiality,
    decoder: ComponentParameterDecoder[A]
  ): Consequence[ComponentParameterPathLeaf[A]] = {
    val normalized = Option(name).map(_.trim).getOrElse("")
    val normalizedaliases = aliases.map(x => Option(x).map(_.trim).getOrElse(""))
    val names = normalized +: normalizedaliases
    if (!_valid_static_segment(normalized))
      ComponentParameterPathDiagnostics.rejected(
        s"component initialization parameter path leaf is invalid: $normalized",
        normalized
      )
    else if (normalizedaliases.size > 8)
      ComponentParameterPathDiagnostics.rejected(
        s"component initialization parameter path leaf has too many aliases: $normalized",
        normalized
      )
    else if (normalizedaliases.exists(!_valid_static_segment(_)))
      ComponentParameterPathDiagnostics.rejected(
        s"component initialization parameter path leaf alias is invalid: $normalized",
        normalized
      )
    else if (names.distinct.size != names.size)
      ComponentParameterPathDiagnostics.ambiguous(
        s"component initialization parameter path leaf aliases are duplicated: $normalized",
        normalized
      )
    else
      Consequence.success(
        new ComponentParameterPathLeaf(
          normalized,
          normalizedaliases,
          requirement,
          confidentiality,
          decoder
        )
      )
  }

  private def _valid_static_segment(value: String): Boolean =
    value.nonEmpty && value.matches("[A-Za-z0-9][A-Za-z0-9_-]{0,127}")
}

final class ComponentParameterPathRoute private (
  val prefix: String,
  val segmentName: String,
  val leaves: Vector[ComponentParameterPathLeaf[?]],
  val prefixAliases: Vector[String],
  val limits: ComponentParameterPathLimits
) {
  private[config] def prefixes: Vector[String] = prefix +: prefixAliases
}

object ComponentParameterPathRoute {
  def createC(
    prefix: String,
    segmentName: String,
    leaves: Vector[ComponentParameterPathLeaf[?]],
    prefixAliases: Vector[String] = Vector.empty,
    limits: ComponentParameterPathLimits = ComponentParameterPathLimits.standard
  ): Consequence[ComponentParameterPathRoute] = {
    val normalizedprefix = Option(prefix).map(_.trim).getOrElse("")
    val normalizedsegmentname = Option(segmentName).map(_.trim).getOrElse("")
    val normalizedaliases = prefixAliases.map(x => Option(x).map(_.trim).getOrElse(""))
    val prefixes = normalizedprefix +: normalizedaliases
    val leafnames = leaves.flatMap(x => x.name +: x.aliases)
    if (!_valid_prefix(normalizedprefix))
      ComponentParameterPathDiagnostics.rejected(
        s"component initialization parameter path prefix is invalid: $normalizedprefix",
        normalizedprefix
      )
    else if (!_valid_segment_name(normalizedsegmentname))
      ComponentParameterPathDiagnostics.rejected(
        s"component initialization parameter path segment name is invalid: $normalizedsegmentname",
        normalizedprefix
      )
    else if (leaves.isEmpty || leaves.size > 64)
      ComponentParameterPathDiagnostics.rejected(
        s"component initialization parameter path route must declare between 1 and 64 leaves: $normalizedprefix",
        normalizedprefix
      )
    else if (normalizedaliases.size > 8 || normalizedaliases.exists(!_valid_prefix(_)))
      ComponentParameterPathDiagnostics.rejected(
        s"component initialization parameter path prefix aliases are invalid: $normalizedprefix",
        normalizedprefix
      )
    else if (_has_overlapping_prefixes(prefixes) || leafnames.distinct.size != leafnames.size)
      ComponentParameterPathDiagnostics.ambiguous(
        s"component initialization parameter path route aliases are ambiguous: $normalizedprefix",
        normalizedprefix
      )
    else
      Consequence.success(
        new ComponentParameterPathRoute(
          normalizedprefix,
          normalizedsegmentname,
          leaves,
          normalizedaliases,
          limits
        )
      )
  }

  private[config] def validSegment(value: String): Boolean =
    value.matches("[A-Za-z0-9][A-Za-z0-9_-]{0,127}")

  private def _valid_prefix(value: String): Boolean =
    value.nonEmpty && value.split("\\.", -1).toVector.forall(_valid_prefix_segment)

  private def _valid_prefix_segment(value: String): Boolean =
    value.matches("[A-Za-z0-9][A-Za-z0-9_-]{0,127}")

  private def _valid_segment_name(value: String): Boolean =
    value.matches("[A-Za-z][A-Za-z0-9_-]{0,63}")

  private def _has_overlapping_prefixes(prefixes: Vector[String]): Boolean =
    prefixes.combinations(2).exists {
      case Vector(left, right) =>
        left == right || left.startsWith(s"$right.") || right.startsWith(s"$left.")
      case _ => false
    }
}

final class ComponentParameterPathParameters private[config] (
  private val _route: ComponentParameterPathRoute,
  private val _segments: Vector[ComponentParameterPathSegment],
  private val _entries: Vector[ComponentParameterPathParameters.Entry]
) {
  def segments: Vector[ComponentParameterPathSegment] = _segments

  def size: Int = _entries.size

  def isEmpty: Boolean = _entries.isEmpty

  def resolve[A](
    segment: ComponentParameterPathSegment,
    leaf: ComponentParameterPathLeaf[A]
  ): Consequence[ComponentParameterResolution[A]] =
    _entries.iterator.flatMap(_.resolutionFor(segment, leaf)).nextOption() match {
      case Some(resolution) => Consequence.success(resolution)
      case None =>
        ComponentParameterPathDiagnostics.rejected(
          "component initialization parameter path identity was not declared in this snapshot",
          _route.prefix
        )
    }

  private[config] def route: ComponentParameterPathRoute = _route

  private[cncf] def diagnosticSummaries: Vector[ComponentParameterDiagnosticSummary] =
    _entries.map(_.diagnosticSummary)
}

private[config] object ComponentParameterPathParameters {
  sealed abstract class Entry {
    def diagnosticSummary: ComponentParameterDiagnosticSummary

    def resolutionFor[A](
      segment: ComponentParameterPathSegment,
      leaf: ComponentParameterPathLeaf[A]
    ): Option[ComponentParameterResolution[A]]
  }

  final class TypedEntry[A](
    storedsegment: ComponentParameterPathSegment,
    storedleaf: ComponentParameterPathLeaf[A],
    storedkey: ComponentParameterKey[A],
    resolution: ComponentParameterResolution[A]
  ) extends Entry {
    def diagnosticSummary: ComponentParameterDiagnosticSummary =
      ComponentParameterDiagnostics.summary(storedkey, resolution)

    def resolutionFor[B](
      segment: ComponentParameterPathSegment,
      leaf: ComponentParameterPathLeaf[B]
    ): Option[ComponentParameterResolution[B]] =
      if ((storedsegment eq segment) && (storedleaf eq leaf))
        Some(resolution.asInstanceOf[ComponentParameterResolution[B]])
      else
        None
  }
}
