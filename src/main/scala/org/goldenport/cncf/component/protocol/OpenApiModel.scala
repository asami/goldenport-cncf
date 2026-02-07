package org.goldenport.cncf.component.protocol

import org.goldenport.Consequence
import org.goldenport.record.{Field, Record}
import org.goldenport.http.HttpRequest

/*
 * Minimal OpenAPI model for CNCF AutoRest.
 *
 * This implementation intentionally performs
 * structural projection only:
 *   OpenAPI Record -> OpenApiModel
 *
 * No semantic interpretation (schema, parameters, responses)
 * is performed at this stage.
 *
 * @since   Feb.  7, 2026
 * @version Feb.  7, 2026
 * @author  ASAMI, Tomoharu
 */
final case class OpenApiModel(
  version: String,
  services: Vector[OpenApiService]
)

final case class OpenApiService(
  basePath: String,
  operations: Vector[OpenApiOperation]
)

final case class OpenApiOperation(
  method: HttpRequest.Method,
  path: String,
  operationId: String
)

object OpenApiModel {

  def fromRecord(r: Record): Consequence[OpenApiModel] =
    for {
      version <- r.getString("openapi") match {
        case Some(ver) => Consequence.success(ver)
        case None => Consequence.failure("openapi version missing")
      }
      pathsRec <- r.getFieldAsRecord("paths")
      services <- _services(pathsRec)
    } yield OpenApiModel(version, services)

  private def _services(paths: Record): Consequence[Vector[OpenApiService]] = {
    val builder = Vector.newBuilder[OpenApiService]

    paths.fields.foreach { field =>
      val path = field.key
      field.value.single match {
        case record: Record =>
          val ops = _operations(path, record)
          builder += OpenApiService(path, ops)
        case _ => ()
      }
    }

    Consequence.success(builder.result())
  }

  private def _operations(
    path: String,
    r: Record
  ): Vector[OpenApiOperation] = {
    val builder = Vector.newBuilder[OpenApiOperation]

    r.fields.foreach { field =>
      _httpMethod(field.key).foreach { method =>
        field.value.single match {
          case record: Record =>
            record.getString("operationId").foreach { opId =>
              builder += OpenApiOperation(
                method = method,
                path = path,
                operationId = opId
              )
            }
          case _ => ()
        }
      }
    }

    builder.result()
  }

  private def _httpMethod(name: String): Option[HttpRequest.Method] =
    name.toLowerCase match {
      case "get"    => Some(HttpRequest.GET)
      case "post"   => Some(HttpRequest.POST)
      case "put"    => Some(HttpRequest.PUT)
      case "delete" => Some(HttpRequest.DELETE)
      case _         => None
    }

  private implicit class RecordOps(record: Record) {
    def getFieldAsRecord(key: String): Consequence[Record] =
      record.asMap.get(key) match {
        case Some(rec: Record) => Consequence.success(rec)
        case _ => Consequence.failure(s"Record field '$key' is missing or not a Record")
      }
  }
}
