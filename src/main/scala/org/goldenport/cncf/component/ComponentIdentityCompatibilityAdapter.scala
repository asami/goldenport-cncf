package org.goldenport.cncf.component

import org.goldenport.Consequence
import org.goldenport.cncf.naming.NamingConventions
/*
 * Bounded adaptation from a retained presentation spelling to an already
 * admitted canonical Component identity.
 *
 * @since   Aug.  8, 2026
 * @version Aug. 30, 2026
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
    ComponentId.parseC(aliasvalue) match {
      case Consequence.Success(componentid) if canonicalcandidates.contains(componentid) =>
        Canonical(componentid)
      case Consequence.Success(_) =>
        Rejected(Unsupported(surface, AliasKind.Qualified, aliasvalue))
      case Consequence.Failure(_) if aliasvalue.contains('.') =>
        Rejected(Unsupported(surface, AliasKind.Qualified, aliasvalue))
      case Consequence.Failure(_) =>
        val localmatching = canonicalcandidates.filter(_local_id_matches(aliasvalue, _))
        if (localmatching.nonEmpty)
          _legacy_result(aliasvalue, surface, AliasKind.Bare, localmatching)
        else
          _legacy_result(
            aliasvalue,
            surface,
            AliasKind.Artifact,
            canonicalcandidates.filter(_artifact_aliases(_).contains(aliasvalue))
          )
    }
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
   * Resolves selectors only against runtime candidates that assembly has
   * already admitted. Exact canonical identities always win; presentation
   * adaptation is constrained to a unique local or registered alias.
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
    ComponentId.parseC(aliasvalue) match {
      case Consequence.Success(componentid) if componentids.contains(componentid) =>
        Canonical(componentid)
      case Consequence.Success(_) =>
        Rejected(Unsupported(surface, AliasKind.Qualified, aliasvalue))
      case Consequence.Failure(_) if aliasvalue.contains('.') =>
        Rejected(Unsupported(surface, AliasKind.Qualified, aliasvalue))
      case Consequence.Failure(_) =>
        val localmatching = componentids.filter(_local_id_matches(aliasvalue, _))
        if (localmatching.nonEmpty)
          _legacy_result(aliasvalue, surface, AliasKind.Bare, localmatching)
        else {
          val presentationmatching = _presentation_matches(aliasvalue, canonicalcandidates, surface)
          if (presentationmatching.nonEmpty)
            _legacy_result(aliasvalue, surface, AliasKind.Presentation, presentationmatching)
          else {
            val artifactmatching = componentids.filter(_artifact_aliases(_).contains(aliasvalue))
            if (artifactmatching.nonEmpty)
              _legacy_result(aliasvalue, surface, AliasKind.Artifact, artifactmatching)
            else if (allowprefix && aliasvalue.nonEmpty)
              _prefix_result(aliasvalue, canonicalcandidates, surface)
            else
              Rejected(Unsupported(surface, AliasKind.Presentation, aliasvalue))
          }
        }
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
      _project_legacy_descriptor_c(descriptor, expectedId)

  def descriptorClaimsIdentity(
    descriptor: ComponentDescriptor,
    expectedId: ComponentId
  ): Boolean =
    Option(descriptor).exists { source =>
      source.componentId.contains(expectedId) ||
        _descriptor_fields(source).exists { case (_, alias) =>
          resolve(alias, Vector(expectedId), Surface.DescriptorField) match {
            case _: Canonical => true
            case _: Adapted => true
            case _: Rejected => false
          }
        }
    }

  def descriptorAliases(componentId: ComponentId): Vector[String] =
    if (componentId == null)
      Vector.empty
    else
      (Vector(componentId.name, componentId.localId.value()) ++ _artifact_aliases(componentId)).distinct

  private def _presentation_matches(
    alias: String,
    candidates: Vector[AliasCandidate],
    surface: Surface
  ): Vector[ComponentId] = {
    val key = NamingConventions.toNormalizedSegment(alias)
    candidates.collect {
      case candidate
          if (surface == Surface.WebPath &&
              NamingConventions.toNormalizedSegment(candidate.componentid.name) == key) ||
            candidate.aliases.exists(x => NamingConventions.toNormalizedSegment(x) == key) =>
        candidate.componentid
    }.distinct.sortBy(_.name)
  }

  private def _local_id_matches(alias: String, componentid: ComponentId): Boolean =
    NamingConventions.toNormalizedSegment(componentid.localId.value()) ==
      NamingConventions.toNormalizedSegment(alias)

  private def _prefix_result(
    alias: String,
    candidates: Vector[AliasCandidate],
    surface: Surface
  ): Result = {
    val key = NamingConventions.toComparisonKey(alias)
    val localmatching = candidates.collect {
      case candidate
          if NamingConventions.toComparisonKey(candidate.componentid.localId.value()).startsWith(key) =>
        candidate.componentid
    }.distinct.sortBy(_.name)
    if (localmatching.nonEmpty)
      _legacy_result(alias, surface, AliasKind.Bare, localmatching)
    else {
      val presentationmatching = candidates.collect {
        case candidate
            if candidate.aliases.exists(x => NamingConventions.toComparisonKey(x).startsWith(key)) =>
          candidate.componentid
      }.distinct.sortBy(_.name)
      _legacy_result(alias, surface, AliasKind.Presentation, presentationmatching)
    }
  }

  private def _project_legacy_descriptor_c(
    descriptor: ComponentDescriptor,
    expectedid: ComponentId
  ): Consequence[DescriptorProjection] =
    descriptor.componentId match {
      case Some(actualid) if actualid != expectedid =>
        Consequence.componentInvalid(
          s"component.identity.compatibility.descriptor-id.mismatch: expected=${expectedid.name}; actual=${actualid.name}"
        )
      case _ =>
        val fields = _descriptor_fields(descriptor)
        if (fields.isEmpty)
          Consequence.componentInvalid(
            s"component.identity.compatibility.descriptor-field.required: expected=${expectedid.name}"
          )
        else
          fields.foldLeft(Consequence.success(Vector.empty[Notice])) { case (z, (field, alias)) =>
            z.flatMap { notices =>
              resolve(alias, Vector(expectedid), Surface.DescriptorField) match {
                case result: Canonical =>
                  result.toConsequence.map(admission => notices ++ admission.notice)
                case result: Adapted =>
                  result.toConsequence.map(admission => notices ++ admission.notice)
                case Rejected(rejection) =>
                  Consequence.componentInvalid(
                    s"component.identity.compatibility.descriptor-field.rejected: field=$field; " +
                      s"expected=${expectedid.name}; actual=$alias; reason=${rejection.diagnostic}"
                  )
              }
            }
          }.map { notices =>
            DescriptorProjection(
              descriptor.copy(
                name = Some(expectedid.name),
                componentName = Some(expectedid.name),
                componentId = Some(expectedid)
              ),
              notices.distinct
            )
          }
    }

  private def _descriptor_fields(descriptor: ComponentDescriptor): Vector[(String, String)] =
    Vector(
      descriptor.name.map("name" -> _),
      descriptor.componentName.map("componentName" -> _)
    ).flatten.distinct

  private def _legacy_result(
    alias: String,
    surface: Surface,
    aliaskind: AliasKind,
    matching: Vector[ComponentId]
  ): Result =
    matching match {
      case Vector(componentid) =>
        Adapted(
          componentid,
          Notice(
            kind = "component-identity-compatibility",
            surface = surface,
            aliaskind = aliaskind,
            alias = alias,
            componentid = componentid
          )
        )
      case Vector() =>
        Rejected(Unsupported(surface, aliaskind, alias))
      case xs =>
        Rejected(Ambiguous(surface, aliaskind, alias, xs.sortBy(_.name)))
    }

  private def _artifact_aliases(componentid: ComponentId): Vector[String] = {
    val localid = NamingConventions.toNormalizedSegment(componentid.localId.value())
    val namespaceleaf = componentid.namespace.value().split("\\.").toVector.lastOption.getOrElse("")
    Vector(
      localid,
      s"${NamingConventions.toNormalizedSegment(namespaceleaf)}-$localid"
    ).filter(_.nonEmpty).distinct
  }

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
