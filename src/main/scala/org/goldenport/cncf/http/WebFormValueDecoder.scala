package org.goldenport.cncf.http

import org.goldenport.Consequence
import org.goldenport.record.Record
import org.goldenport.record.io.RecordDecoder

/*
 * @since   Jul. 16, 2026
 * @version Jul. 16, 2026
 * @author  ASAMI, Tomoharu
 */
private[http] object WebFormValueDecoder {
  def decode(
    descriptor: WebDescriptor,
    componentname: String,
    servicename: String,
    operationname: String,
    form: Record
  ): Consequence[Record] = {
    val controls = descriptor.form
      .get(WebDescriptor.formSelector(componentname, servicename, operationname))
      .toVector
      .flatMap(_.controls)
      .filter { case (_, control) =>
        control.controlType.exists(_is_record_control)
      }
    controls.foldLeft(Consequence.success(form)) { case (z, (name, _)) =>
      z.flatMap(_decode_record_field(name, _))
    }
  }

  private def _decode_record_field(
    name: String,
    form: Record
  ): Consequence[Record] =
    form.getAny(name) match {
      case None => Consequence.success(form)
      case Some(_: Record) => Consequence.success(form)
      case Some(value: String) if value.trim.isEmpty =>
        Consequence.success(form.removeKeys(Set(name)))
      case Some(value: String) =>
        new RecordDecoder()
          .json(value)
          .recoverWith(_ => Consequence.argumentFormatError(name, "JSON object", value))
          .map(form.upsertSingle(name, _))
      case Some(value) =>
        Consequence.argumentInvalid(name, "JSON object", value.getClass.getSimpleName)
    }

  private def _is_record_control(value: String): Boolean =
    value.trim.toLowerCase(java.util.Locale.ROOT) match {
      case "json" | "record" => true
      case _ => false
    }
}
