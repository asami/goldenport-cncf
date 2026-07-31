package org.goldenport.cncf.component

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import io.circe.Json
import io.circe.parser.parse
import org.goldenport.Consequence
import org.goldenport.record.Record
import org.goldenport.record.io.RecordSourceLoader

/*
 * @since   Apr.  8, 2026
 * @version Jul. 31, 2026
 * @author  ASAMI, Tomoharu
 */
object DescriptorRecordLoader {
  def load(path: Path): Consequence[Vector[Record]] =
    if (path.getFileName.toString.toLowerCase.endsWith(".json"))
      parse(Files.readString(path, StandardCharsets.UTF_8)).fold(
        error => Consequence.argumentInvalid(s"Invalid JSON descriptor $path: ${error.message}"),
        json => _record(json).map(record => Consequence.success(Vector(record))).getOrElse(
          Consequence.argumentInvalid(s"JSON descriptor must be an object: $path")
        )
      )
    else
      RecordSourceLoader.loadRecords(path)

  private def _record(json: Json): Option[Record] =
    json.asObject.map { value =>
      Record.createFull(value.toVector.map { case (key, entry) => key -> _value(entry) })
    }

  private def _value(json: Json): Any =
    json.asObject.map(value => Record.createFull(value.toVector.map { case (key, entry) => key -> _value(entry) })).orElse(
      json.asArray.map(_.toVector.map(_value))
    ).orElse(json.asString).orElse(json.asBoolean).orElse(json.asNumber.flatMap(_.toBigDecimal)).getOrElse(null)
}
