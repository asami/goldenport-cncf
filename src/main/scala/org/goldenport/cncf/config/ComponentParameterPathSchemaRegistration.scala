package org.goldenport.cncf.config

import org.goldenport.Consequence
import org.goldenport.configuration.{Configuration, ConfigurationValue}

/*
 * Expands component-declared dynamic parameter paths inside the CNCF-owned
 * configuration boundary. Only concrete typed declarations and bounded
 * candidates leave this registration step.
 *
 * @since   Aug.  5, 2026
 * @version Aug.  5, 2026
 * @author  ASAMI, Tomoharu
 */
private[cncf] final class RegisteredComponentParameterPathSchema private[config] (
  val parameters: Vector[ComponentParameterPathParameters],
  val declarations: Vector[ComponentParameterKey[?]]
)

private[cncf] object ComponentParameterPathSchemaRegistration {
  private final case class RegisteredLeaf[A](
    segment: ComponentParameterPathSegment,
    leaf: ComponentParameterPathLeaf[A],
    key: ComponentParameterKey[A]
  )

  private final case class RegisteredRoute(
    route: ComponentParameterPathRoute,
    segments: Vector[ComponentParameterPathSegment],
    leaves: Vector[RegisteredLeaf[?]]
  )

  private final class CandidateResolver(
    candidates: Map[String, ComponentParameterCandidate]
  ) extends ComponentParameterResolver() {
    private val _candidates = candidates

    protected def lookup_parameter(
      name: String
    ): Consequence[Option[ComponentParameterCandidate]] =
      Consequence.success(_candidates.get(name))
  }

  def registerC(
    routes: Vector[ComponentParameterPathRoute],
    layers: ComponentParameterResolutionLayers,
    staticDeclarations: Vector[ComponentParameterKey[?]] = Vector.empty
  ): Consequence[RegisteredComponentParameterPathSchema] =
    if (routes.isEmpty)
      Consequence.success(
        new RegisteredComponentParameterPathSchema(Vector.empty, Vector.empty)
      )
    else
      for {
        _ <- _validate_routes(routes)
        registered <- _sequence(routes.map(_register_route(_, layers)))
        _ <- ComponentInitializationParameters.validateDeclarations(
          staticDeclarations ++ registered.flatMap(_.leaves.map(_.key))
        )
        _ <- _validate_expanded_limit(registered)
        candidates = registered.flatMap(_route_candidates(_, layers)).toMap
        resolver = new CandidateResolver(candidates)
        parameters <- _sequence(registered.map(_resolve_route(_, resolver)))
      } yield new RegisteredComponentParameterPathSchema(
        parameters,
        registered.flatMap(_.leaves.map(_.key))
      )

  private def _validate_routes(
    routes: Vector[ComponentParameterPathRoute]
  ): Consequence[Unit] = {
    val duplicate = routes.indices.combinations(2).collectFirst {
      case Seq(left, right) if routes(left) eq routes(right) => routes(left).prefix
    }
    val prefixes = routes.zipWithIndex.flatMap { case (route, index) =>
      route.prefixes.map(prefix => (prefix, index))
    }
    val overlap = duplicate.orElse(prefixes.combinations(2).collectFirst {
      case Vector((left, leftindex), (right, rightindex))
          if leftindex != rightindex && (
            left == right ||
            left.startsWith(s"$right.") ||
            right.startsWith(s"$left.")) =>
        if (left.length <= right.length) left else right
    })
    overlap.fold(Consequence.unit) { prefix =>
      ComponentParameterPathDiagnostics.ambiguous(
        s"component initialization parameter path routes overlap: $prefix",
        prefix
      )
    }
  }

  private def _register_route(
    route: ComponentParameterPathRoute,
    layers: ComponentParameterResolutionLayers
  ): Consequence[RegisteredRoute] =
    for {
      segments <- _discover_segments(route, layers)
      _ <- if (segments.size <= route.limits.maximumSegments)
        Consequence.unit
      else
        ComponentParameterPathDiagnostics.rejected(
          s"component initialization parameter path route exceeds its segment limit: ${route.prefix}",
          route.prefix
        )
      identities = segments.map(new ComponentParameterPathSegment(_))
      leaves = identities.flatMap(segment => route.leaves.map(_registered_leaf(route, segment, _)))
    } yield RegisteredRoute(route, identities, leaves)

  private def _discover_segments(
    route: ComponentParameterPathRoute,
    layers: ComponentParameterResolutionLayers
  ): Consequence[Vector[String]] = {
    val paths = layers.parameterLayersHighToLow.flatMap { layer =>
      _flatten(layer._configuration).keys
    }.distinct
    val matched = paths.flatMap(_match_path(route, _))
    matched.collectFirst { case Left((path, reason)) => path -> reason } match {
      case Some((path, reason)) =>
        ComponentParameterPathDiagnostics.rejected(
          s"component initialization parameter path is rejected ($reason): $path",
          path
        )
      case None =>
        Consequence.success(
          matched.collect { case Right(segment) => segment }.distinct.sorted
        )
    }
  }

  private def _match_path(
    route: ComponentParameterPathRoute,
    path: String
  ): Option[Either[(String, String), String]] =
    route.prefixes.iterator.map { prefix =>
      if (path == prefix)
        Some(Left(path -> "invalid-shape"))
      else {
        val marker = s"$prefix."
        Option.when(path.startsWith(marker)) {
          val remainder = path.drop(marker.length)
          val parts = remainder.split("\\.", -1).toVector
          parts match {
            case Vector(segment, _) if !ComponentParameterPathRoute.validSegment(segment) =>
              Left(path -> "invalid-segment")
            case Vector(_, leafname) if _leaf(route, leafname).isEmpty =>
              Left(path -> "unknown-leaf")
            case Vector(segment, _) => Right(segment)
            case _ => Left(path -> "invalid-shape")
          }
        }
      }
    }.collectFirst { case Some(value) => value }

  private def _leaf(
    route: ComponentParameterPathRoute,
    name: String
  ): Option[ComponentParameterPathLeaf[?]] =
    route.leaves.find(leaf => leaf.name == name || leaf.aliases.contains(name))

  private def _registered_leaf(
    route: ComponentParameterPathRoute,
    segment: ComponentParameterPathSegment,
    leaf: ComponentParameterPathLeaf[?]
  ): RegisteredLeaf[?] =
    _registered_typed_leaf(route, segment, leaf)

  private def _registered_typed_leaf[A](
    route: ComponentParameterPathRoute,
    segment: ComponentParameterPathSegment,
    leaf: ComponentParameterPathLeaf[A]
  ): RegisteredLeaf[A] = {
    val name = s"${route.prefix}.${segment.value}.${leaf.name}"
    RegisteredLeaf(segment, leaf, leaf.concreteKey(name))
  }

  private def _route_candidates(
    registered: RegisteredRoute,
    layers: ComponentParameterResolutionLayers
  ): Vector[(String, ComponentParameterCandidate)] =
    registered.leaves.flatMap { value =>
      _candidate(registered.route, value.segment, value.leaf, layers)
        .map(value.key.name -> _)
    }

  private def _candidate(
    route: ComponentParameterPathRoute,
    segment: ComponentParameterPathSegment,
    leaf: ComponentParameterPathLeaf[?],
    layers: ComponentParameterResolutionLayers
  ): Option[ComponentParameterCandidate] = {
    if (leaf.confidentiality == ComponentParameterConfidentiality.Confidential)
      None
    else {
      val names = for {
        prefix <- route.prefixes
        leafname <- leaf.name +: leaf.aliases
      } yield s"$prefix.${segment.value}.$leafname"
      layers.parameterLayersHighToLow.iterator.flatMap { layer =>
        val values = _flatten(layer._configuration)
        names.iterator.flatMap(values.get).nextOption().map { value =>
          ComponentParameterCandidate(value, layer._provenance)
        }
      }.nextOption()
    }
  }

  private def _resolve_route(
    registered: RegisteredRoute,
    resolver: ComponentParameterResolver
  ): Consequence[ComponentParameterPathParameters] =
    _sequence(registered.leaves.map(_resolve_leaf(_, resolver))).map { entries =>
      new ComponentParameterPathParameters(
        registered.route,
        registered.segments,
        entries
      )
    }

  private def _resolve_leaf(
    registered: RegisteredLeaf[?],
    resolver: ComponentParameterResolver
  ): Consequence[ComponentParameterPathParameters.Entry] =
    _resolve_typed_leaf(registered, resolver)

  private def _resolve_typed_leaf[A](
    registered: RegisteredLeaf[A],
    resolver: ComponentParameterResolver
  ): Consequence[ComponentParameterPathParameters.Entry] =
    resolver.resolve(registered.key).map { resolution =>
      new ComponentParameterPathParameters.TypedEntry(
        registered.segment,
        registered.leaf,
        registered.key,
        resolution
      )
    }

  private def _validate_expanded_limit(
    routes: Vector[RegisteredRoute]
  ): Consequence[Unit] = {
    val size = routes.map(_.leaves.size).sum
    if (size <= 4096)
      Consequence.unit
    else
      ComponentParameterPathDiagnostics.rejected(
        "component initialization parameter path schema exceeds 4096 expanded keys",
        "expanded-path-schema"
      )
  }

  private def _flatten(
    configuration: Configuration
  ): Map[String, ConfigurationValue] = {
    def _nested_(
      prefix: Vector[String],
      values: Map[String, ConfigurationValue]
    ): Vector[(String, ConfigurationValue)] =
      values.toVector.flatMap {
        case (name, ConfigurationValue.ObjectValue(children)) =>
          _nested_(prefix :+ name, children)
        case (name, value) => Vector((prefix :+ name).mkString(".") -> value)
      }

    val nested = _nested_(Vector.empty, configuration.values).toMap
    val direct = configuration.values.collect {
      case (name, value) if !value.isInstanceOf[ConfigurationValue.ObjectValue] =>
        name -> value
    }
    nested ++ direct
  }

  private def _sequence[A](
    consequences: Vector[Consequence[A]]
  ): Consequence[Vector[A]] =
    consequences.foldLeft(Consequence.success(Vector.empty[A])) { (acc, consequence) =>
      acc.flatMap(values => consequence.map(values :+ _))
    }
}
