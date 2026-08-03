package org.goldenport.cncf.config

import org.goldenport.Consequence
import org.goldenport.configuration.ConfigurationBindingReference

/*
 * @since   Aug.  3, 2026
 * @version Aug.  3, 2026
 * @author  ASAMI, Tomoharu
 */
final class CncfConfigurationEnvironmentBindingAssignment private (
  val reference: ConfigurationBindingReference[CncfConfigurationTarget],
  val rawValue: String
)

object CncfConfigurationEnvironmentBindingAssignment {
  def create(
    reference: ConfigurationBindingReference[CncfConfigurationTarget],
    rawValue: String
  ): Consequence[CncfConfigurationEnvironmentBindingAssignment] =
    if (reference == null)
      Consequence.configurationInvalid("CNCF configuration environment binding reference is required")
    else if (rawValue == null)
      Consequence.configurationInvalid("CNCF configuration environment binding raw value is required")
    else
      Consequence.success(new CncfConfigurationEnvironmentBindingAssignment(reference, rawValue))
}

final class CncfConfigurationEnvironmentBindingAdmission private (
  val assignments: Vector[CncfConfigurationEnvironmentBindingAssignment],
  val residualEnvironment: Map[String, String]
)

object CncfConfigurationEnvironmentBindingAdmission {
  private val _prefix = "TEXTUS_BINDING_"

  def admit(
    environment: Map[String, String],
    catalog: CncfConfigurationParameterCatalog = CncfConfigurationParameterCatalog.closed
  ): Consequence[CncfConfigurationEnvironmentBindingAdmission] =
    if (environment == null || environment.exists { case (key, value) => key == null || value == null } || catalog == null)
      Consequence.configurationInvalid("CNCF configuration environment binding admission is invalid")
    else
      CncfConfigurationEnvironmentBindingCodec.create(catalog).flatMap { codec =>
        environment.toVector.sortBy(_._1).foldLeft(
          Consequence.success((Vector.empty[CncfConfigurationEnvironmentBindingAssignment], Map.empty[String, String]))
        ) { case (acc, (name, value)) =>
          acc.flatMap { case (assignments, residual) =>
            if (name.startsWith(_prefix))
              for {
                reference <- codec.decode(name)
                _ <- if (assignments.exists(_.reference == reference))
                  Consequence.configurationInvalid("CNCF configuration environment binding is duplicated")
                else
                  Consequence.success(())
                assignment <- CncfConfigurationEnvironmentBindingAssignment.create(reference, value)
              } yield (assignments :+ assignment) -> residual
            else
              Consequence.success(assignments -> (residual + (name -> value)))
          }
        }.map { case (assignments, residual) =>
          new CncfConfigurationEnvironmentBindingAdmission(assignments, residual)
        }
      }.recoverWith(_ => Consequence.configurationInvalid("CNCF configuration environment binding admission failed"))
}
