package org.goldenport.cncf.datastore.sql

import java.sql.ResultSet
import io.circe.Json
import io.circe.parser.parse
import org.goldenport.Consequence
import org.goldenport.convert.StringEncodable
import org.goldenport.text.Presentable
import org.goldenport.record.{Field, Record, RecordKeyNaming, RecordPresentable}
import org.goldenport.cncf.datastore.DataStore.CollectionId
import org.goldenport.cncf.entity.SimpleEntityStorageShapePolicy
import org.simplemodeling.model.datatype.EntityCollectionId

/*
 * @since   Oct.  1, 2026
 * @version Oct. 17, 2026
 * @author  ASAMI, Tomoharu
 */
private[sql] final class SqlDataStoreRecordCodec(config: SqlDataStore.Config, postgresql: Boolean) {
  def recordColumns(
    record: Record
  ): Vector[(String, Any)] = {
    val columns = record.fields.collect {
      case field if field.key != "id" =>
        val k = field.key
        val v = field.value.single
        val key = columnName(k)
        key -> columnValue(v)
    }
    if (postgresql) columns
    else columns.foldLeft(Vector.empty[(String, Any)]) { case (z, (k, v)) =>
        z.indexWhere(_._1 == k) match {
          case -1 => z :+ (k -> v)
          case i => z.updated(i, k -> v)
        }
      }
  }

  def columnValue(
    value: Any
  ): Any =
    value match {
      case null if postgresql => null
      case org.simplemodeling.model.directive.Update.SetNull =>
        null
      case m: StringEncodable =>
        given org.goldenport.context.ExecutionContext = org.goldenport.convert.StringEncoder.storageExecutionContext
        m.encode
      case m: Record =>
        m.toJsonString
      case m: RecordPresentable =>
        m.toRecord().toJsonString
      case xs: Iterable[?] =>
        Json.fromValues(xs.iterator.map(_json_from_value).toVector).noSpaces
      case m: Byte => m.toInt
      case m: Short => m.toInt
      case m: Int => m
      case m: Long => m
      case m: Boolean => if (m) 1 else 0
      case m: Float => m.toDouble
      case m: Double => m
      case m: BigInt => m.toDouble
      case m: BigDecimal => m.toDouble
      case other =>
        Presentable.print(other)
    }

  private def _json_from_value(
    value: Any
  ): Json =
    value match {
      case null => Json.Null
      case m: StringEncodable =>
        given org.goldenport.context.ExecutionContext = org.goldenport.convert.StringEncoder.storageExecutionContext
        Json.fromString(m.encode)
      case m: Record =>
        parse(m.toJsonString).getOrElse(Json.fromString(m.toJsonString))
      case m: RecordPresentable =>
        val jsontext = m.toRecord().toJsonString
        parse(jsontext).getOrElse(Json.fromString(jsontext))
      case xs: Iterable[?] =>
        Json.fromValues(xs.iterator.map(_json_from_value).toVector)
      case m: Byte => Json.fromInt(m.toInt)
      case m: Short => Json.fromInt(m.toInt)
      case m: Int => Json.fromInt(m)
      case m: Long => Json.fromLong(m)
      case m: Boolean => Json.fromBoolean(m)
      case m: Float => Json.fromFloatOrNull(m)
      case m: Double => Json.fromDoubleOrNull(m)
      case m: BigInt => Json.fromBigInt(m)
      case m: BigDecimal => Json.fromBigDecimal(m)
      case other => Json.fromString(Presentable.print(other))
    }

  def decodeColumnValue(
    value: Any
  ): Any = {
    value match {
      case s: String =>
        val trimmed = s.trim
        if (trimmed.startsWith("{") && trimmed.endsWith("}"))
          parse(trimmed).toOption.map(json => _json_to_value(json, number => number.toBigDecimal.getOrElse(BigDecimal(number.toDouble)))).getOrElse(s)
        else if (trimmed.startsWith("[") && trimmed.endsWith("]"))
          parse(trimmed).toOption.map(json => _json_to_value(json, number => number.toLong.getOrElse(number.toDouble))).getOrElse(s)
        else
          s
      case other =>
        other
    }
  }

  private def _json_to_value(
    json: Json,
    numberdecoder: io.circe.JsonNumber => Any
  ): Any =
    json.fold(
      jsonNull = null,
      jsonBoolean = identity,
      jsonNumber = numberdecoder,
      jsonString = identity,
      jsonArray = _.map(value => _json_to_value(value, numberdecoder)).toVector,
      jsonObject = obj => Record.createFull(obj.toVector.map { case (k, v) => k -> _json_to_value(v, numberdecoder) })
    )

  def recordFromResultSetC(collection: CollectionId, resultSet: ResultSet): Consequence[Record] =
    Consequence {
      val md = resultSet.getMetaData
      val count = md.getColumnCount
      (1 to count).toVector.map { i =>
        val rawname = md.getColumnLabel(i)
        val name =
          if (SimpleEntityStorageShapePolicy.isConcurrencyRevisionStorageField(rawname))
            rawname
          else if (config.normalizeColumnNames)
            RecordKeyNaming.toCanonicalCamelName(rawname)
          else
            rawname
        val rawvalue = resultSet.getObject(i)
        val value =
          if (_preserve_native_text(collection, name))
            rawvalue
          else
            decodeColumnValue(rawvalue)
        name -> value
      }
    }.flatMap { values =>
      if (postgresql && values.map(_._1).distinct.size != values.size)
        Consequence.configurationInvalid("PostgreSQL readback has ambiguous decoded Record keys")
      else Consequence {
        val ordered = if (postgresql) values.sortBy { case (name, _) =>
          if (name == "id") 0 else if (name == "revision" || name == "cncf_revision") 1 else 2
        } else values
        Record(ordered.filterNot { case (_, value) => java.util.Objects.isNull(value) }
          .map { case (name, value) => Field(name, Field.Value.Single(value)) })
      }
    }

  def columnName(name: String): String =
    if (config.normalizeColumnNames) RecordKeyNaming.toSnakeColumnName(name) else name

  private def _preserve_native_text(collection: CollectionId, name: String): Boolean =
    collection match {
      case CollectionId.EntityStore(id)
          if id == EntityCollectionId("cncf", "builtin", "durableJob") =>
        name == "canonicalJson" || name == "jobId"
      case CollectionId.EntityStore(id)
          if id == EntityCollectionId("cncf", "builtin", "durableJobStorageQuota") ||
            id == EntityCollectionId("cncf", "builtin", "durableJobOwnedBlobStorageQuota") ||
            id == EntityCollectionId("cncf", "builtin", "durableJobLifetimeQuota") =>
        name == "canonicalJson"
      case _ => false
    }
}
