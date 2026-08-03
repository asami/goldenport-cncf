package org.goldenport.cncf.config

import org.goldenport.Consequence
import org.goldenport.configuration.{ConfigurationBindingCandidateInput, ConfigurationValue}

/*
 * @since   Aug.  3, 2026
 * @version Aug.  3, 2026
 * @author  ASAMI, Tomoharu
 */
object CncfConfigurationEnvironmentBindingCandidateDecoder {
  private[config] def inputs(
    assignments: Vector[CncfConfigurationEnvironmentBindingAssignment],
    catalog: CncfConfigurationParameterCatalog
  ): Consequence[Vector[ConfigurationBindingCandidateInput[CncfConfigurationTarget]]] =
    if (assignments == null || assignments.exists(_ == null) || catalog == null)
      Consequence.configurationInvalid("CNCF configuration environment binding assignments are invalid")
    else
      CncfConfigurationEnvironmentBindingCodec.create(catalog).flatMap { codec =>
        assignments.foldLeft(Consequence.success(Vector.empty[ConfigurationBindingCandidateInput[CncfConfigurationTarget]])) {
          case (acc, assignment) =>
            for {
              inputs <- acc
              name <- codec.encode(assignment.reference)
              definition <- catalog.canonicalDefinition(assignment.reference.parameterId)
              input <- definition.input(
                assignment.reference.parameterId.value,
                assignment.reference.target,
                ConfigurationValue.StringValue(assignment.rawValue),
                name
              )
            } yield inputs :+ input
        }
      }.recoverWith(_ => Consequence.configurationInvalid("CNCF configuration environment binding candidate construction failed"))
}
