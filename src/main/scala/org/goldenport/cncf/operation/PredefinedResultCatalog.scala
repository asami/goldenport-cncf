package org.goldenport.cncf.operation

import java.nio.charset.StandardCharsets
import io.circe.{ACursor, Decoder, HCursor}
import io.circe.parser.parse

/*
 * @since   Jul. 15, 2026
 * @version Jul. 15, 2026
 * @author  ASAMI, Tomoharu
 */
final case class PredefinedResultDefinition(
  name: String,
  runtimeClassName: String,
  resultFields: Vector[CmlOperationField]
)

final case class PredefinedResultCatalog(
  schemaVersion: String,
  results: Vector[PredefinedResultDefinition]
) {
  private lazy val _result_map: Map[String, PredefinedResultDefinition] = {
    val pairs = results.map(x => x.name -> x)
    require(pairs.map(_._1).distinct.size == pairs.size, "Predefined Result names must be unique.")
    pairs.toMap
  }

  def get(name: String): Option[PredefinedResultDefinition] =
    _result_map.get(name)

  def contains(name: String): Boolean =
    _result_map.contains(name)
}

object PredefinedResultCatalog {
  val RESOURCE_PATH: String = "META-INF/cncf/predefined-results.json"

  lazy val default: PredefinedResultCatalog =
    _load_resource()

  private def _load_resource(): PredefinedResultCatalog = {
    val loader = Option(Thread.currentThread.getContextClassLoader).getOrElse(getClass.getClassLoader)
    val stream = Option(loader.getResourceAsStream(RESOURCE_PATH)).getOrElse {
      throw new IllegalStateException(s"Missing CNCF predefined Result catalog resource: $RESOURCE_PATH")
    }
    try {
      val text = new String(stream.readAllBytes(), StandardCharsets.UTF_8)
      val json = parse(text).fold(
        failure => throw new IllegalArgumentException(s"Invalid CNCF predefined Result catalog JSON: ${failure.message}"),
        identity
      )
      _from_cursor(json.hcursor)
    } finally {
      stream.close()
    }
  }

  private def _from_cursor(cursor: HCursor): PredefinedResultCatalog = {
    val schemaversion = _required[String](cursor, "schemaVersion", "catalog")
    val names = _required[Vector[String]](cursor, "resultNames", "catalog")
    val definitions = cursor.downField("results")
    val results = names.map { name =>
      val definition = definitions.downField(name)
      val runtimeclassname = _required[String](definition, "runtimeClassName", s"Result $name")
      val fieldnames = _required[Vector[String]](definition, "fields", s"Result $name")
      val fielddefinitions = definition.downField("fieldDefinitions")
      val fields = fieldnames.map { fieldname =>
        val field = fielddefinitions.downField(fieldname)
        CmlOperationField(
          name = fieldname,
          datatype = _required[String](field, "datatype", s"Result $name field $fieldname"),
          multiplicity = field.get[String]("multiplicity").getOrElse("1")
        )
      }
      PredefinedResultDefinition(name, runtimeclassname, fields)
    }
    PredefinedResultCatalog(schemaversion, results)
  }

  private def _required[A: Decoder](cursor: ACursor, field: String, context: String): A =
    cursor.get[A](field).fold(
      failure => throw new IllegalArgumentException(s"CNCF predefined Result $context requires $field: ${failure.message}"),
      identity
    )
}
