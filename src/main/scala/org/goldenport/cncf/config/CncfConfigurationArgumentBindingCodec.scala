package org.goldenport.cncf.config

import org.goldenport.Consequence
import org.goldenport.configuration.ConfigurationBindingReference

/*
 * @since   Aug.  3, 2026
 * @version Aug.  3, 2026
 * @author  ASAMI, Tomoharu
 */
final class CncfConfigurationArgumentBindingAssignment private (
  val reference: ConfigurationBindingReference[CncfConfigurationTarget],
  val rawValue: String
)

object CncfConfigurationArgumentBindingAssignment {
  def create(
    reference: ConfigurationBindingReference[CncfConfigurationTarget],
    rawValue: String
  ): Consequence[CncfConfigurationArgumentBindingAssignment] =
    if (reference == null)
      Consequence.configurationInvalid("CNCF configuration argument binding reference is required")
    else if (rawValue == null)
      Consequence.configurationInvalid("CNCF configuration argument binding raw value is required")
    else
      Consequence.success(new CncfConfigurationArgumentBindingAssignment(reference, rawValue))
}

final class CncfConfigurationArgumentBindingAdmission private (
  val assignments: Vector[CncfConfigurationArgumentBindingAssignment],
  val residualArguments: Vector[String]
)

object CncfConfigurationArgumentBindingAdmission {
  def create(
    assignments: Vector[CncfConfigurationArgumentBindingAssignment],
    residualArguments: Vector[String]
  ): Consequence[CncfConfigurationArgumentBindingAdmission] =
    if (assignments == null || assignments.exists(_ == null) || residualArguments == null || residualArguments.exists(_ == null))
      Consequence.configurationInvalid("CNCF configuration argument binding admission is invalid")
    else
      Consequence.success(new CncfConfigurationArgumentBindingAdmission(assignments, residualArguments))
}

final class CncfConfigurationArgumentBindingCodec private (
  bindingCodec: CncfConfigurationBindingStringCodec
) {
  private val _prefix = "--textus.binding="

  def decode(
    value: String
  ): Consequence[CncfConfigurationArgumentBindingAssignment] =
    Option(value).filter(_.startsWith(_prefix)).fold[Consequence[CncfConfigurationArgumentBindingAssignment]](
      Consequence.configurationInvalid("CNCF configuration binding argument is invalid")
    ) { argument =>
      val payload = argument.drop(_prefix.length)
      val delimiter = payload.indexOf('=')
      if (delimiter <= 0)
        Consequence.configurationInvalid("CNCF configuration binding argument is invalid")
      else
        for {
          reference <- bindingCodec.decode(payload.substring(0, delimiter))
          assignment <- CncfConfigurationArgumentBindingAssignment.create(reference, payload.substring(delimiter + 1))
        } yield assignment
    }

  def encode(
    value: CncfConfigurationArgumentBindingAssignment
  ): Consequence[String] =
    if (value == null)
      Consequence.configurationInvalid("CNCF configuration argument binding assignment is required")
    else
      bindingCodec.encode(value.reference).map(x => s"$_prefix$x=${value.rawValue}")

  def admit(
    arguments: Vector[String]
  ): Consequence[CncfConfigurationArgumentBindingAdmission] =
    if (arguments == null || arguments.exists(_ == null))
      Consequence.configurationInvalid("CNCF configuration argument binding input is invalid")
    else
      arguments.foldLeft(
        Consequence.success((Vector.empty[CncfConfigurationArgumentBindingAssignment], Vector.empty[String], false))
      ) { case (acc, argument) =>
        acc.flatMap { case (assignments, residual, afterSentinel) =>
          if (afterSentinel)
            Consequence.success((assignments, residual :+ argument, true))
          else if (argument == "--")
            Consequence.success((assignments, residual :+ argument, true))
          else if (argument.startsWith(_prefix))
            decode(argument).flatMap { assignment =>
              if (assignments.exists(x => x.reference.parameterId == assignment.reference.parameterId && x.reference.target == assignment.reference.target))
                Consequence.configurationInvalid("CNCF configuration argument binding is duplicated")
              else
                Consequence.success((assignments :+ assignment, residual, false))
            }
          else if (argument == "--textus.binding")
            Consequence.configurationInvalid("CNCF configuration binding argument is invalid")
          else
            Consequence.success((assignments, residual :+ argument, false))
        }
      }.flatMap { case (assignments, residual, _) =>
        CncfConfigurationArgumentBindingAdmission.create(assignments, residual)
      }
}

object CncfConfigurationArgumentBindingCodec {
  def create(
    catalog: CncfConfigurationParameterCatalog
  ): Consequence[CncfConfigurationArgumentBindingCodec] =
    CncfConfigurationBindingStringCodec.create(catalog).map(new CncfConfigurationArgumentBindingCodec(_))
}
