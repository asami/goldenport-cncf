package org.goldenport.cncf.config

import org.goldenport.Consequence
import org.goldenport.configuration.{
  ConfigurationBindingCandidateBatch,
  ConfigurationBindingCandidateConstructor,
  ConfigurationBindingCandidateInput,
  ConfigurationBindingCandidates,
  ConfigurationSourceAdmission,
  ConfigurationSourceSnapshot,
  ConfigurationSourceSnapshots,
  ConfigurationValue
}

/*
 * @since   Aug.  3, 2026
 * @version Aug.  3, 2026
 * @author  ASAMI, Tomoharu
 */
object CncfConfigurationArgumentBindingCandidateDecoder {
  def decode(
    admissions: Vector[ConfigurationSourceAdmission[Vector[CncfConfigurationArgumentBindingAssignment]]],
    catalog: CncfConfigurationParameterCatalog
  ): Consequence[ConfigurationBindingCandidates[CncfConfigurationTarget]] =
    if (admissions == null || admissions.isEmpty || admissions.exists(_ == null) || catalog == null)
      Consequence.configurationInvalid("CNCF configuration argument binding admissions are invalid")
    else {
      val result = for {
        snapshots <- ConfigurationSourceSnapshots.load(admissions)
        batches <- _batches(snapshots.snapshots, catalog)
        candidates <- ConfigurationBindingCandidateConstructor.construct(batches)
      } yield candidates
      result.recoverWith(_ => Consequence.configurationInvalid("CNCF configuration argument binding candidate construction failed"))
    }

  private def _batches(
    snapshots: Vector[ConfigurationSourceSnapshot[Vector[CncfConfigurationArgumentBindingAssignment]]],
    catalog: CncfConfigurationParameterCatalog
  ): Consequence[Vector[ConfigurationBindingCandidateBatch[CncfConfigurationTarget]]] =
    snapshots.foldLeft(Consequence.success(Vector.empty[ConfigurationBindingCandidateBatch[CncfConfigurationTarget]])) {
      case (acc, snapshot) =>
        for {
          batches <- acc
          inputs <- inputs(snapshot.value, catalog)
          batch <- ConfigurationBindingCandidateBatch.create(snapshot, inputs)
        } yield batches :+ batch
    }

  private[config] def inputs(
    assignments: Vector[CncfConfigurationArgumentBindingAssignment],
    catalog: CncfConfigurationParameterCatalog
  ): Consequence[Vector[ConfigurationBindingCandidateInput[CncfConfigurationTarget]]] =
    if (assignments == null || assignments.exists(_ == null))
      Consequence.configurationInvalid("CNCF configuration argument binding assignments are invalid")
    else
      CncfConfigurationBindingStringCodec.create(catalog).flatMap { codec =>
        assignments.foldLeft(Consequence.success(Vector.empty[ConfigurationBindingCandidateInput[CncfConfigurationTarget]])) {
        case (acc, assignment) =>
          for {
            inputs <- acc
            reference <- codec.encode(assignment.reference)
            definition <- catalog.canonicalDefinition(assignment.reference.parameterId)
            input <- definition.input(
              assignment.reference.parameterId.value,
              assignment.reference.target,
              ConfigurationValue.StringValue(assignment.rawValue),
              reference
            )
          } yield inputs :+ input
        }
      }
}
