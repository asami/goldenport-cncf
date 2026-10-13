package org.goldenport.cncf.processexecution

import org.goldenport.Consequence

/*
 * Extracted unchanged Registered/BoundedText policy responsibility from
 * ProcessExecutionModel with additive runtime-declared Template admission.
 * @since   Jul. 17, 2026
 *  version Jul. 22, 2026
 * @version Oct.  6, 2026
 * @author  ASAMI, Tomoharu
 */
enum ProcessArgumentAdmission {
  case Registered
  case BoundedText
  case Template
}

final case class ProcessArgumentPolicy(
  fixedPrefix: Vector[String],
  permittedArguments: Set[String] = Set.empty,
  permittedArgumentVectors: Set[Vector[String]] = Set.empty,
  admission: ProcessArgumentAdmission = ProcessArgumentAdmission.Registered,
  permittedArgumentTemplates: Set[ProcessArgumentTemplate] = Set.empty
) {
  def validateDeclarationC: Consequence[Unit] =
    if (admission == ProcessArgumentAdmission.Template) {
      if (permittedArgumentTemplates.isEmpty || permittedArgumentTemplates.exists(_ == null))
        ProcessArgumentTemplate.invalidC
      else permittedArgumentTemplates.foldLeft(Consequence.unit)((z, template) =>
        z.flatMap(_ => template.validateC))
    } else if (permittedArgumentTemplates.nonEmpty) ProcessArgumentTemplate.invalidC
    else Consequence.unit

  def validateC(arguments: Vector[String], limits: ProcessExecutionLimits): Consequence[Unit] =
    for {
      _ <- _validate_arguments_c(arguments)
      _ <- _validate_count_c(arguments, limits.argumentCount)
      _ <- _validate_bytes_c(arguments, limits.argumentBytes)
    } yield ()

  private def _validate_arguments_c(arguments: Vector[String]): Consequence[Unit] = {
    if (admission == ProcessArgumentAdmission.Template)
      if (permittedArgumentTemplates.exists(template => template != null && template.matches(arguments)))
        Consequence.unit
      else ProcessArgumentTemplate.invalidC
    else if (admission == ProcessArgumentAdmission.BoundedText)
      _validate_bounded_text_c(arguments)
    else if (permittedArgumentVectors.nonEmpty)
      if (permittedArgumentVectors.contains(arguments))
        Consequence.unit
      else
        Consequence.argumentPolicyViolation(
          "arguments",
          "process.execution.argument-policy",
          "registered argument vector",
          "unapproved"
        )
    else {
      val rejected = arguments.find(x => !permittedArguments.contains(x))
      rejected match {
        case Some(_) =>
          Consequence.argumentPolicyViolation(
            "arguments",
            "process.execution.argument-policy",
            "registered argument value",
            "unapproved"
          )
        case None => Consequence.unit
      }
    }
  }

  private def _validate_bounded_text_c(arguments: Vector[String]): Consequence[Unit] = {
    val rejected = arguments.find(argument =>
      argument.trim.isEmpty ||
      argument.exists(character => character.isControl && character != '\n' && character != '\r' && character != '\t')
    )
    if (arguments.isEmpty)
      Consequence.argumentPolicyViolation(
        "arguments",
        "process.execution.argument-policy",
        "at least one bounded text argument",
        "absent"
      )
    else rejected match {
      case Some(_) =>
        Consequence.argumentFormatError(
          "arguments",
          "non-empty bounded text arguments without unsafe control characters",
          "invalid"
        )
      case None => Consequence.unit
    }
  }

  private def _validate_count_c(
    arguments: Vector[String],
    maximum: Option[Long]
  ): Consequence[Unit] =
    maximum match {
      case Some(value) if arguments.length.toLong > value =>
        Consequence.argumentLimitExceeded(
          "arguments",
          value,
          arguments.length.toLong,
          "process.execution.argument-count"
        )
      case _ => Consequence.unit
    }

  private def _validate_bytes_c(
    arguments: Vector[String],
    maximum: Option[Long]
  ): Consequence[Unit] = {
    val size = arguments.foldLeft(0L)((z, x) => z + x.getBytes("UTF-8").length.toLong)
    maximum match {
      case Some(value) if size > value =>
        Consequence.argumentLimitExceeded(
          "arguments",
          value,
          size,
          "process.execution.argument-bytes"
        )
      case _ => Consequence.unit
    }
  }
}
