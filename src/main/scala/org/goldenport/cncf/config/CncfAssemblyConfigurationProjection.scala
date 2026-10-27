package org.goldenport.cncf.config

import org.goldenport.Consequence
import org.goldenport.configuration.{ConfigurationBindingCandidates, ConfigurationDocument, ConfigurationOrigin, ConfigurationSourceAdmission, ConfigurationValue}

/** Projects already-loaded assembly descriptor defaults without reloading the descriptor.
 * @since Aug.  4, 2026
 * @version Oct. 27, 2026
 */
object CncfAssemblyConfigurationProjection {
  val ResourceRank: Int = 0
  private[cncf] val ComponentDefaultsSourceType = "component-car-defaults"

  def globalCandidates(
    values: Map[String, String],
    sourceIdentity: Option[String]
  ): Consequence[ConfigurationBindingCandidates[CncfConfigurationTarget]] =
    if (values == null || values.exists { case (key, value) => key == null || value == null })
      Consequence.configurationInvalid("CNCF global assembly configuration projection is invalid")
    else
      for {
        admission <- _admission(values, sourceIdentity, CncfConfigurationTargetKind.Global)
        candidates <- CncfConfigurationCandidateDecoder.decodeCatalog(Vector(
          CncfConfigurationDocumentBatch(
            CncfConfigurationDocumentLocation.Global,
            admission
          )
        ))
      } yield candidates

  def candidates(
    values: Map[String, String],
    sourceIdentity: Option[String],
    subsystem: SubsystemInstanceId
  ): Consequence[ConfigurationBindingCandidates[CncfConfigurationTarget]] =
    _candidates(values, sourceIdentity, subsystem, "assembly-descriptor")

  private[cncf] def componentDefaultCandidates(
    values: Map[String, String], sourceIdentity: String, subsystem: SubsystemInstanceId
  ): Consequence[ConfigurationBindingCandidates[CncfConfigurationTarget]] =
    _candidates(values, Some(sourceIdentity), subsystem, ComponentDefaultsSourceType)

  private def _candidates(
    values: Map[String, String], sourceIdentity: Option[String],
    subsystem: SubsystemInstanceId, sourceType: String
  ): Consequence[ConfigurationBindingCandidates[CncfConfigurationTarget]] =
    if (values == null || values.exists { case (key, value) => key == null || value == null } || subsystem == null)
      Consequence.configurationInvalid("CNCF assembly configuration projection is invalid")
    else for {
      target <- CncfConfigurationTarget.SubsystemInstance.create(subsystem)
      admission <- _admission(values, sourceIdentity, CncfConfigurationTargetKind.SubsystemInstance, sourceType)
      candidates <- CncfConfigurationCandidateDecoder.decodeCatalog(Vector(
        CncfConfigurationDocumentBatch(
          new CncfConfigurationDocumentLocation.SubsystemInstance(target),
          admission
        )
      ))
    } yield candidates

  private def _admission(
    values: Map[String, String],
    sourceIdentity: Option[String],
    targetKind: CncfConfigurationTargetKind,
    sourceType: String = "assembly-descriptor"
  ): Consequence[ConfigurationSourceAdmission[ConfigurationDocument]] =
    ConfigurationSourceAdmission.create(
      ConfigurationOrigin.Resource,
      "assembly-descriptor",
      sourceIdentity.getOrElse("assembly-descriptor"),
      ResourceRank,
      sourceIdentity.getOrElse("assembly-descriptor"),
      () => Consequence.success(_document(values, targetKind)),
      Some(sourceType)
    )

  private def _document(
    values: Map[String, String],
    targetKind: CncfConfigurationTargetKind
  ): ConfigurationDocument = {
    val spellings = CncfConfigurationParameterCatalog.closed.definitions
      .filter(_.allowedTargetKinds.contains(targetKind))
      .flatMap(_.spellings)
      .toSet
    ConfigurationDocument.Object(
      values.toVector.collect { case (key, value) if spellings.contains(key) =>
        ConfigurationDocument.Field(key, ConfigurationDocument.Scalar(ConfigurationValue.StringValue(value)))
      }.sortBy(_.name)
    )
  }
}
