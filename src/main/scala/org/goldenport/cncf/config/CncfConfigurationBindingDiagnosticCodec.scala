package org.goldenport.cncf.config

import java.util.Locale

import org.goldenport.Consequence
import org.goldenport.configuration.{ConfigurationBindingReference, ConfigurationBindingTrace, ConfigurationBindingTraceEntry, ConfigurationBindingTraceValue, ConfigurationValue}

/*
 * @since   Aug.  3, 2026
 * @version Aug.  3, 2026
 * @author  ASAMI, Tomoharu
 */
object CncfConfigurationBindingDiagnosticCodec {
  val FORMAT = "textus.configuration-binding-diagnostic.v1"

  /**
   * Projects only the already-sanitized generic trace.  It intentionally has
   * no API accepting candidates, resolved collections, sources, or loaders.
   */
  def encode(
    trace: ConfigurationBindingTrace[CncfConfigurationTarget]
  ): Consequence[String] =
    if (trace == null)
      Consequence.configurationInvalid("CNCF configuration binding diagnostic trace is required")
    else
      CncfConfigurationBindingStringCodec.create(CncfConfigurationParameterCatalog.closed).flatMap { codec =>
        trace.entries.foldLeft(Consequence.success(Vector.empty[String])) { (acc, explanation) =>
          for {
            entries <- acc
            diagnostic <- _diagnostic(codec, explanation)
          } yield entries :+ diagnostic
        }.map { diagnostics =>
          _object(Vector(
            "format" -> _string(FORMAT),
            "bindings" -> _array(diagnostics)
          ))
        }
      }

  private def _diagnostic(
    codec: CncfConfigurationBindingStringCodec,
    explanation: org.goldenport.configuration.ConfigurationBindingExplanation[CncfConfigurationTarget]
  ): Consequence[String] =
    if (explanation == null || explanation.entries.isEmpty)
      Consequence.configurationInvalid("CNCF configuration binding diagnostic explanation is invalid")
    else
      for {
        reference <- ConfigurationBindingReference.create(explanation.parameterId, explanation.effective.target)
        spelling <- codec.encode(reference)
        history <- explanation.entries.foldLeft(Consequence.success(Vector.empty[String])) { (acc, entry) =>
          for {
            entries <- acc
            diagnostic <- _entry(entry)
          } yield entries :+ diagnostic
        }
      } yield _object(Vector(
        "reference" -> _string(spelling),
        "history" -> _array(history),
        "omittedHistoryCount" -> explanation.omittedEntryCount.toString
      ))

  private def _entry(
    entry: ConfigurationBindingTraceEntry[CncfConfigurationTarget]
  ): Consequence[String] =
    if (entry == null || entry.provenance == null)
      Consequence.configurationInvalid("CNCF configuration binding diagnostic entry is invalid")
    else
      _value(entry.value).map { value =>
        val provenance = entry.provenance
        _object(Vector(
          "value" -> value,
          "provenance" -> _object(Vector(
            "origin" -> _string(provenance.origin.toString.toLowerCase(Locale.ROOT)),
            "layer" -> _string(provenance.layer),
            "sourceIdentity" -> _string(provenance.sourceIdentity),
            "sourceIdentityTruncatedCodeUnits" -> provenance.sourceIdentityTruncatedCount.toString,
            "inputPath" -> _optional_string(provenance.inputPath),
            "inputSpelling" -> _optional_string(provenance.inputSpelling),
            "sourceRank" -> provenance.sourceRank.toString,
            "sourceOrdinal" -> provenance.sourceOrdinal.toString
          ))
        ))
      }

  private def _value(
    value: ConfigurationBindingTraceValue
  ): Consequence[String] =
    value match {
      case ConfigurationBindingTraceValue.Redacted =>
        Consequence.success(_object(Vector("state" -> _string("redacted"))))
      case ConfigurationBindingTraceValue.Visible(configuration) if configuration != null =>
        Consequence.success(_visible_value(configuration))
      case _ =>
        Consequence.configurationInvalid("CNCF configuration binding diagnostic value is invalid")
    }

  private def _visible_value(value: ConfigurationValue): String =
    value match {
      case ConfigurationValue.StringValue(text) =>
        _object(Vector("state" -> _string("visible"), "kind" -> _string("string"), "text" -> _string(text)))
      case ConfigurationValue.NumberValue(number) =>
        _object(Vector("state" -> _string("visible"), "kind" -> _string("number"), "text" -> _string(number.bigDecimal.toPlainString)))
      case ConfigurationValue.BooleanValue(boolean) =>
        _object(Vector("state" -> _string("visible"), "kind" -> _string("boolean"), "text" -> _string(boolean.toString)))
      case ConfigurationValue.ListValue(values) =>
        _object(Vector("state" -> _string("visible"), "kind" -> _string("list"), "items" -> _array(values.toVector.map(_visible_value))))
      case ConfigurationValue.ObjectValue(values) =>
        _object(Vector(
          "state" -> _string("visible"),
          "kind" -> _string("object"),
          "fields" -> _array(values.toVector.sortBy(_._1).map { case (name, child) =>
            _object(Vector("name" -> _string(name), "value" -> _visible_value(child)))
          })
        ))
      case ConfigurationValue.NullValue =>
        _object(Vector("state" -> _string("visible"), "kind" -> _string("null")))
    }

  private def _object(fields: Vector[(String, String)]): String =
    fields.map { case (name, value) => s"${_string(name)}:$value" }.mkString("{", ",", "}")

  private def _array(values: Vector[String]): String =
    values.mkString("[", ",", "]")

  private def _optional_string(value: Option[String]): String =
    value.fold("null")(_string)

  private def _string(value: String): String = {
    val text = Option(value).getOrElse("")
    val builder = new StringBuilder(text.length + 2)
    builder.append('"')
    text.foreach {
      case '"' => builder.append("\\\"")
      case '\\' => builder.append("\\\\")
      case '\b' => builder.append("\\b")
      case '\f' => builder.append("\\f")
      case '\n' => builder.append("\\n")
      case '\r' => builder.append("\\r")
      case '\t' => builder.append("\\t")
      case character if character < ' ' => builder.append(f"\\u${character.toInt}%04X")
      case character => builder.append(character)
    }
    builder.append('"')
    builder.toString
  }
}
