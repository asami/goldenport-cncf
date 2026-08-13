package org.goldenport.cncf.component

import org.goldenport.Consequence
import org.goldenport.cncf.naming.NamingConventions
/*
 * Canonical Component identity admission and the retained Web-path
 * presentation alias projection.
 *
 * @since   Aug.  8, 2026
 * @version Aug. 13, 2026
 * @author  ASAMI, Tomoharu
 */
private[cncf] object ComponentIdentityCompatibilityAdapter {
  enum Surface {
    case AssemblyBinding
    case DescriptorField
    case RuntimeSelector
    case HelpProjection
    case MetaProjection
    case WebPath

    def code: String =
      this match {
        case AssemblyBinding => "assembly-binding"
        case DescriptorField => "descriptor-field"
        case RuntimeSelector => "runtime-selector"
        case HelpProjection => "help-projection"
        case MetaProjection => "meta-projection"
        case WebPath => "web-path"
      }
  }

  enum AliasKind {
    case Qualified
    case Bare
    case Artifact
    case Presentation

    def code: String =
      this match {
        case Qualified => "qualified"
        case Bare => "bare"
        case Artifact => "artifact"
        case Presentation => "presentation"
      }
  }

  final case class Notice(
    kind: String,
    surface: Surface,
    aliaskind: AliasKind,
    alias: String,
    componentid: ComponentId
  ) {
    def message: String =
      s"$kind: surface=${surface.code}; alias-kind=${aliaskind.code}; alias=$alias; canonical=${componentid.name}"
  }

  sealed trait Rejection {
    def surface: Surface
    def aliaskind: AliasKind
    def alias: String
    def diagnostic: String
  }

  final case class Ambiguous(
    surface: Surface,
    aliaskind: AliasKind,
    alias: String,
    candidates: Vector[ComponentId]
  ) extends Rejection {
    def diagnostic: String =
      s"component.identity.compatibility.ambiguous: surface=${surface.code}; alias-kind=${aliaskind.code}; alias=$alias; candidates=${candidates.map(_.name).mkString(",")}"
  }

  final case class Unsupported(
    surface: Surface,
    aliaskind: AliasKind,
    alias: String
  ) extends Rejection {
    def diagnostic: String =
      s"component.identity.compatibility.unsupported: surface=${surface.code}; alias-kind=${aliaskind.code}; alias=$alias"
  }

  sealed trait Result {
    def toConsequence: Consequence[Admission]
  }

  final case class Admission(
    componentid: ComponentId,
    notice: Option[Notice]
  )

  final case class DescriptorProjection(
    descriptor: ComponentDescriptor,
    notices: Vector[Notice]
  )

  /** Runtime presentation aliases supplied by already admitted Components. */
  final case class AliasCandidate(
    componentid: ComponentId,
    aliases: Vector[String]
  )

  final case class Canonical(
    componentid: ComponentId
  ) extends Result {
    def toConsequence: Consequence[Admission] =
      Consequence.success(Admission(componentid, None))
  }

  final case class Adapted(
    componentid: ComponentId,
    notice: Notice
  ) extends Result {
    def toConsequence: Consequence[Admission] =
      Consequence.success(Admission(componentid, Some(notice)))
  }

  final case class Rejected(
    rejection: Rejection
  ) extends Result {
    def toConsequence: Consequence[Admission] =
      Consequence.componentInvalid(rejection.diagnostic)
  }

  def resolve(
    alias: String,
    candidates: Vector[ComponentId],
    surface: Surface
  ): Result = {
    val aliasvalue = Option(alias).getOrElse("")
    val canonicalcandidates = Option(candidates).toVector.flatten.filter(_ != null).distinct.sortBy(_.name)
    _resolve_canonical(aliasvalue, canonicalcandidates, surface)
  }

  def runtimeAliasCandidates(components: Seq[Component]): Vector[AliasCandidate] = {
    val values = Option(components).toVector.flatten.filter(_ != null).map { component =>
      val instancealiases = component.instanceMetadata.toVector.map(_.componentName)
      val metadataaliases = component.artifactMetadata.toVector.flatMap { metadata =>
        Vector(Some(metadata.name), metadata.component).flatten
      }
      AliasCandidate(
        component.componentId,
        (Vector(component.displayName) ++ instancealiases ++ metadataaliases)
          .filter(_.nonEmpty)
          .distinct
      )
    }
    _merge_alias_candidates(values)
  }

  /**
   * Resolves exact canonical identities on every surface. WebPath alone also
   * retains one unique admitted presentation alias for stable Web routes.
   */
  def resolveAliases(
    alias: String,
    candidates: Vector[AliasCandidate],
    surface: Surface,
    allowprefix: Boolean = false
  ): Result = {
    val aliasvalue = Option(alias).getOrElse("")
    val canonicalcandidates = _merge_alias_candidates(Option(candidates).getOrElse(Vector.empty))
    val componentids = canonicalcandidates.map(_.componentid).distinct.sortBy(_.name)
    _resolve_canonical(aliasvalue, componentids, surface) match {
      case canonical: Canonical => canonical
      case _: Rejected if surface == Surface.WebPath && !aliasvalue.contains('.') =>
        _resolve_web_path_alias(aliasvalue, canonicalcandidates)
      case rejected: Rejected => rejected
      case adapted: Adapted => adapted
    }
  }

  def projectDescriptorC(
    descriptor: ComponentDescriptor,
    expectedId: ComponentId
  ): Consequence[DescriptorProjection] =
    if (descriptor == null)
      Consequence.componentInvalid("component.identity.compatibility.descriptor.required")
    else if (expectedId == null)
      Consequence.componentInvalid("component.identity.compatibility.expected-id.required")
    else if (descriptor.schemaVersion.contains(3))
      descriptor.requireCanonicalIdentityC.flatMap { case (actualid, _) =>
        if (actualid == expectedId)
          Consequence.success(DescriptorProjection(descriptor, Vector.empty))
        else
          Consequence.componentInvalid(
            s"component.identity.compatibility.descriptor-id.mismatch: expected=${expectedId.name}; actual=${actualid.name}"
          )
      }
    else
      Consequence.componentInvalid(
        s"component.identity.compatibility.descriptor-schema.required: expected=3; actual=${descriptor.schemaVersion.map(_.toString).getOrElse("missing")}"
      )

  def descriptorClaimsIdentity(
    descriptor: ComponentDescriptor,
    expectedId: ComponentId
  ): Boolean =
    Option(descriptor).exists { source =>
      source.schemaVersion.contains(3) &&
        source.requireCanonicalIdentityC.toOption.exists(_._1 == expectedId)
    }

  private def _resolve_canonical(
    alias: String,
    candidates: Vector[ComponentId],
    surface: Surface
  ): Result =
    ComponentId.parseC(alias) match {
      case Consequence.Success(componentid) if candidates.contains(componentid) =>
        Canonical(componentid)
      case Consequence.Success(_) =>
        Rejected(Unsupported(surface, AliasKind.Qualified, alias))
      case Consequence.Failure(_) =>
        Rejected(Unsupported(surface, _noncanonical_alias_kind(alias), alias))
    }

  private def _resolve_web_path_alias(
    alias: String,
    candidates: Vector[AliasCandidate]
  ): Result = {
    val key = NamingConventions.toNormalizedSegment(alias)
    val matches = candidates.collect {
      case candidate
          if NamingConventions.toNormalizedSegment(candidate.componentid.name) == key ||
            candidate.aliases.exists(x => NamingConventions.toNormalizedSegment(x) == key) =>
        candidate.componentid
    }.distinct.sortBy(_.name)
    matches match {
      case Vector(componentid) =>
        Adapted(
          componentid,
          Notice(
            kind = "component-identity-compatibility",
            surface = Surface.WebPath,
            aliaskind = AliasKind.Presentation,
            alias = alias,
            componentid = componentid
          )
        )
      case Vector() =>
        Rejected(Unsupported(Surface.WebPath, _noncanonical_alias_kind(alias), alias))
      case xs =>
        Rejected(Ambiguous(Surface.WebPath, AliasKind.Presentation, alias, xs))
    }
  }

  private def _noncanonical_alias_kind(alias: String): AliasKind =
    if (Option(alias).exists(_.contains('.'))) AliasKind.Qualified else AliasKind.Bare

  private def _merge_alias_candidates(
    candidates: Vector[AliasCandidate]
  ): Vector[AliasCandidate] =
    candidates.filter(x => x != null && x.componentid != null)
      .groupBy(_.componentid)
      .toVector
      .map { case (componentid, values) =>
        AliasCandidate(componentid, values.flatMap(_.aliases).filter(_.nonEmpty).distinct)
      }
      .sortBy(_.componentid.name)
}
