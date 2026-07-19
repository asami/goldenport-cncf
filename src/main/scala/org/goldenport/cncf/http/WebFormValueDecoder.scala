package org.goldenport.cncf.http

import org.goldenport.Consequence
import org.goldenport.record.Record
import org.goldenport.record.io.RecordDecoder

/*
 * @since   Jul. 16, 2026
 * @version Jul. 20, 2026
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
    controls.foldLeft(Consequence.success(form)) { case (z, (name, control)) =>
      z.flatMap(_decode_control_field(name, control, _))
    }
  }

  private def _decode_control_field(
    name: String,
    control: WebDescriptor.FormControl,
    form: Record
  ): Consequence[Record] =
    control.controlType.map(_.trim.toLowerCase(java.util.Locale.ROOT)) match {
      case Some("checkbox") => Consequence.success(_decode_checkbox_field(name, form))
      case _ if control.multiple => Consequence.success(_decode_multiple_field(name, form))
      case Some(kind) if _is_record_control(kind) => _decode_record_field(name, form)
      case _ => Consequence.success(form)
    }

  private def _decode_checkbox_field(
    name: String,
    form: Record
  ): Record = {
    val values = _field_values(name, form)
    if (values.isEmpty)
      form
    else
      form.removeKeys(Set(name)).upsertSingle(name, values.exists(_is_truthy))
  }

  private def _decode_multiple_field(
    name: String,
    form: Record
  ): Record = {
    val occurrences = form.fields.filter(_.key == name)
    if (occurrences.isEmpty)
      form
    else {
      val values = occurrences.toVector
        .flatMap(field => _values(field.value.single))
        .flatMap(x => Option(x).map(_.toString.trim).filter(_.nonEmpty))
      form.removeKeys(Set(name)).upsertSingle(name, values)
    }
  }

  private def _field_values(
    name: String,
    form: Record
  ): Vector[Any] =
    form.fields.filter(_.key == name).toVector.flatMap(field => _values(field.value.single))

  private def _values(value: Any): Vector[Any] =
    value match {
      case xs: Vector[?] => xs.flatMap(_values)
      case xs: Seq[?] => xs.toVector.flatMap(_values)
      case x => Vector(x)
    }

  private def _is_truthy(value: Any): Boolean =
    Option(value).exists { x =>
      Set("true", "on", "1", "yes").contains(x.toString.trim.toLowerCase(java.util.Locale.ROOT))
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
    value match {
      case "json" | "record" => true
      case _ => false
    }
}
