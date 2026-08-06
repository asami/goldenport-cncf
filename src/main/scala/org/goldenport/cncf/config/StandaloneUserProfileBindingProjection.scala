package org.goldenport.cncf.config

import org.goldenport.Consequence
import org.goldenport.configuration.{ConfigurationBindingCandidate, ConfigurationBindingCandidates, ConfigurationOrigin, ConfigurationParameter, ConfigurationProvenance, ConfigurationValue}
import org.goldenport.configuration.source.ConfigurationSource

/** Projects already-admitted HOME standalone-user profiles into typed candidates.
 *  It never reads a profile path and leaves runtime collection admission to GCF-07F.
 */
object StandaloneUserProfileBindingProjection {
  def runtimeCandidates(
    admitted: Vector[StandaloneUserProfileResolver.Admitted],
    subsystem: SubsystemInstanceId,
    localSubjectId: Option[String]
  ): Consequence[ConfigurationBindingCandidates[CncfConfigurationTarget]] =
    if (admitted == null || localSubjectId == null)
      Consequence.configurationInvalid("standalone runtime user-profile projection is invalid")
    else if (admitted.nonEmpty)
      candidates(admitted, subsystem)
    else
      localSubjectId
        .map(_default_candidates(_, subsystem))
        .getOrElse(candidates(Vector.empty, subsystem))

  def candidates(
    admitted: Vector[StandaloneUserProfileResolver.Admitted],
    subsystem: SubsystemInstanceId
  ): Consequence[ConfigurationBindingCandidates[CncfConfigurationTarget]] =
    if (admitted == null || admitted.exists(_ == null) || subsystem == null)
      Consequence.configurationInvalid("standalone user-profile binding projection is invalid")
    else
      for {
        _ <- _validate(admitted)
        global <- Consequence.success(CncfConfigurationTarget.Global)
        local <- CncfConfigurationTarget.SubsystemInstance.create(subsystem)
        values <- admitted.zipWithIndex.foldLeft(Consequence.success(Vector.empty[ConfigurationBindingCandidate[?, CncfConfigurationTarget]])) {
          case (acc, (entry, ordinal)) =>
            acc.flatMap(xs => _candidates(entry, ordinal, subsystem, global, local).map(xs ++ _))
        }
        result <- ConfigurationBindingCandidates.from(values)
      } yield result

  private def _default_candidates(
    localsubjectid: String,
    subsystem: SubsystemInstanceId
  ): Consequence[ConfigurationBindingCandidates[CncfConfigurationTarget]] =
    for {
      localtarget <- CncfConfigurationTarget.SubsystemInstance.create(subsystem)
      target: CncfConfigurationTarget = localtarget
      value <- CncfConfigurationParameterCatalog.fixedUserId.codec.decode(
        ConfigurationValue.StringValue(localsubjectid)
      )
      provenance <- ConfigurationProvenance.create(
        ConfigurationOrigin.Default,
        "subsystem-local-subject-default",
        subsystem.subsystem,
        Some("security.authentication.local_subject.id"),
        Some(CncfConfigurationParameterCatalog.fixedUserId.id.value),
        0,
        0,
        Vector("standalone runtime default from descriptor local_subject"),
        false,
        Some("descriptor-default")
      )
      candidate <- ConfigurationBindingCandidate.create(
        CncfConfigurationParameterCatalog.fixedUserId,
        target,
        value,
        provenance
      )
      result <- ConfigurationBindingCandidates.from(Vector(candidate))
    } yield result

  private def _candidates(
    entry: StandaloneUserProfileResolver.Admitted,
    ordinal: Int,
    subsystem: SubsystemInstanceId,
    global: CncfConfigurationTarget,
    local: CncfConfigurationTarget
  ): Consequence[Vector[ConfigurationBindingCandidate[?, CncfConfigurationTarget]]] = {
    val layer = entry.layer match {
      case StandaloneUserProfileResolver.Layer.TextusHome => "textus"
      case StandaloneUserProfileResolver.Layer.CncfHome => "cncf"
    }
    val rank = entry.layer match {
      case StandaloneUserProfileResolver.Layer.TextusHome => ConfigurationSource.Rank.Home
      case StandaloneUserProfileResolver.Layer.CncfHome => ConfigurationSource.Rank.Home + 1
    }
    val source = entry.path.toAbsolutePath.normalize.toString
    for {
      common <- entry.document.user.map(_user_candidates(_, global, "user", entry, layer, source, rank, ordinal)).getOrElse(Consequence.success(Vector.empty))
      selected <- entry.document.subsystems.get(subsystem.subsystem).map(
        _user_candidates(_, local, s"subsystems.${subsystem.subsystem}.user", entry, layer, source, rank, ordinal)
      ).getOrElse(Consequence.success(Vector.empty))
    } yield common ++ selected
  }

  private def _user_candidates(
    user: StandaloneUserProfile.User,
    target: CncfConfigurationTarget,
    prefix: String,
    entry: StandaloneUserProfileResolver.Admitted,
    layer: String,
    source: String,
    rank: Int,
    ordinal: Int
  ): Consequence[Vector[ConfigurationBindingCandidate[?, CncfConfigurationTarget]]] =
    for {
      id <- _candidate(user.id, CncfConfigurationParameterCatalog.fixedUserId, target, s"$prefix.id", entry, layer, source, rank, ordinal)
      displayname <- _candidate(user.displayName, CncfConfigurationParameterCatalog.fixedUserDisplayName, target, s"$prefix.displayName", entry, layer, source, rank, ordinal)
      locale <- _candidate(user.locale, CncfConfigurationParameterCatalog.fixedUserLocale, target, s"$prefix.locale", entry, layer, source, rank, ordinal)
      timezone <- _candidate(user.timezone, CncfConfigurationParameterCatalog.fixedUserTimezone, target, s"$prefix.timezone", entry, layer, source, rank, ordinal)
    } yield id.toVector ++ displayname.toVector ++ locale.toVector ++ timezone.toVector

  private def _candidate[A](
    raw: Option[String],
    parameter: ConfigurationParameter[A],
    target: CncfConfigurationTarget,
    path: String,
    entry: StandaloneUserProfileResolver.Admitted,
    layer: String,
    source: String,
    rank: Int,
    ordinal: Int
  ): Consequence[Option[ConfigurationBindingCandidate[A, CncfConfigurationTarget]]] =
    raw.fold[Consequence[Option[ConfigurationBindingCandidate[A, CncfConfigurationTarget]]]](Consequence.success(None)) { text =>
      for {
        value <- parameter.codec.decode(ConfigurationValue.StringValue(text))
        provenance <- ConfigurationProvenance.create(
          entry.origin, layer, source, Some(path), Some(parameter.id.value), rank, ordinal,
          Vector("phase-53: StandaloneUserProfile HOME admission"), false, Some("file")
        )
        candidate <- ConfigurationBindingCandidate.create(parameter, target, value, provenance)
      } yield Some(candidate)
    }

  private def _validate(admitted: Vector[StandaloneUserProfileResolver.Admitted]): Consequence[Unit] = {
    if (admitted.exists(x => !_well_formed(x)))
      Consequence.configurationInvalid("StandaloneUserProfile projection admissions are structurally invalid")
    else {
      val layers = admitted.map(_.layer)
      val ordered = layers.sortBy {
        case StandaloneUserProfileResolver.Layer.TextusHome => 0
        case StandaloneUserProfileResolver.Layer.CncfHome => 1
      }
      if (admitted.size > 2 || layers.distinct.size != layers.size || layers != ordered)
        Consequence.configurationInvalid("StandaloneUserProfile admissions must be Textus HOME then CNCF HOME without duplicates")
      else if (admitted.exists(x => x.origin != ConfigurationOrigin.Home || !_matches(x)))
        Consequence.configurationInvalid("StandaloneUserProfile projection accepts only canonical HOME profile admissions")
      else
        Consequence.unit
    }
  }

  private def _well_formed(entry: StandaloneUserProfileResolver.Admitted): Boolean =
    entry != null && entry.layer != null && entry.path != null && entry.origin != null &&
      _document_well_formed(entry.document)

  private def _document_well_formed(document: StandaloneUserProfile.Document): Boolean =
    document != null && document.user != null && document.subsystems != null &&
      document.user.forall(_user_well_formed) &&
      document.subsystems.forall { case (name, user) =>
        name != null && user != null && _user_well_formed(user)
      }

  private def _user_well_formed(user: StandaloneUserProfile.User): Boolean =
    user != null && _optional_text_well_formed(user.id) &&
      _optional_text_well_formed(user.displayName) &&
      _optional_text_well_formed(user.locale) &&
      _optional_text_well_formed(user.timezone)

  private def _optional_text_well_formed(value: Option[String]): Boolean =
    value != null && value.forall(_ != null)

  private def _matches(entry: StandaloneUserProfileResolver.Admitted): Boolean = {
    scala.util.Try {
      val path = entry.path.toAbsolutePath.normalize
      val parent = Option(path.getParent).flatMap(x => Option(x.getFileName)).map(_.toString)
      val filename = Option(path.getFileName).map(_.toString)
      filename.contains("user-profile.yaml") && parent.contains(entry.layer match {
        case StandaloneUserProfileResolver.Layer.TextusHome => ".textus"
        case StandaloneUserProfileResolver.Layer.CncfHome => ".cncf"
      })
    }.toOption.contains(true)
  }
}
