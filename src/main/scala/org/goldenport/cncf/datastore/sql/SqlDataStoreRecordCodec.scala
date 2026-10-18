package org.goldenport.cncf.datastore.sql

import java.sql.ResultSet
import org.goldenport.Consequence
import org.goldenport.convert.StringEncodable
import org.goldenport.record.{Field, Record, RecordKeyNaming}
import org.goldenport.observation.Descriptor
import org.goldenport.cncf.datastore.DataStore.CollectionId
import org.goldenport.cncf.entity.SimpleEntityStorageShapePolicy

/*
 * @since   Oct.  1, 2026
 * @version Oct. 19, 2026
 * @author  ASAMI, Tomoharu
 */
private[sql] final class SqlDataStoreRecordCodec(config: SqlDataStore.Config, postgresql: Boolean) {
  def recordColumns(
    record: Record
  ): Vector[(String, Any)] = {
    val fields = record.fields.filterNot(_.key == "id")
    val identities = fields.map { field =>
      val column = columnName(field.key)
      val key = if (postgresql) column
        else column.map(character => if (character >= 'A' && character <= 'Z') (character + 32).toChar else character)
      key -> field.key
    }
    if (identities.groupMap(_._1)(_._2).values.exists(_.distinct.size > 1))
      Consequence.argumentInvalid[Vector[(String, Any)]]("record", "unique SQL columns", "normalized column aliases").RAISE
    else fields.foldLeft(Vector.empty[(String, Any)]) { (columns, field) =>
      val column = columnName(field.key)
      val value = columnValue(field.value.single)
      columns.indexWhere(_._1 == column) match {
        case -1 => columns :+ (column -> value)
        case index => columns.updated(index, column -> value)
      }
    }
  }

  def columnValue(
    value: Any
  ): Any =
    value match {
      case null => null
      case org.simplemodeling.model.directive.Update.SetNull =>
        null
      case m: StringEncodable =>
        given org.goldenport.context.ExecutionContext = org.goldenport.convert.StringEncoder.storageExecutionContext
        m.encode
      case m: String => m
      case m: Char => m.toString
      case m: Array[Byte] => m
      case m: org.goldenport.util.SmEnum => m.value
      case m: java.time.temporal.TemporalAccessor => m.toString
      case m: java.time.temporal.TemporalAmount => m.toString
      case m: java.util.Date => m.toString
      case m: java.net.URI => m.toString
      case m: java.net.URL => m.toString
      case m: java.util.UUID => m.toString
      case m: java.nio.charset.Charset => m.name()
      case m: Byte => m.toInt
      case m: Short => m
      case m: Int => m
      case m: Long => m
      case m: Boolean => if (m) 1 else 0
      case m: Float if m.isFinite => m.toDouble
      case m: Double if m.isFinite => m
      case m: BigInt => m.toString
      case m: BigDecimal => m.toString
      case m: java.math.BigInteger => m.toString
      case m: java.math.BigDecimal => m.toString
      case _ =>
        Consequence.valueInvalid[Any]("SQL physical values require an explicit scalar or encoded text").leftMap { conclusion =>
          val observation = conclusion.observation
          val cause = observation.cause
          conclusion.copy(observation = observation.copy(cause = cause.copy(
            descriptor = cause.descriptor.copy(facets = cause.descriptor.facets ++ Vector(
              Descriptor.Facet.Policy("datastore.physical-row"),
              Descriptor.Facet.State("encode"),
              Descriptor.Facet.Reason("unsupported-physical-value"))))))
        }.RAISE
    }

  def decodeColumnValue(value: Any): Any = value

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
        val value = Option(resultSet.getObject(i))
        name -> value
      }
    }.flatMap { values =>
      if (values.map(_._1).distinct.size != values.size)
        Consequence.configurationInvalid("SQL readback has ambiguous decoded Record keys")
      else Consequence {
        val ordered = if (postgresql) values.sortBy { case (name, _) =>
          if (name == "id") 0 else if (name == "revision" || name == "cncf_revision") 1 else 2
        } else values
        Record(ordered.flatMap { case (name, value) =>
          value.map(present => Field(name, Field.Value.Single(present)))
        })
      }
    }

  def columnName(name: String): String =
    if (config.normalizeColumnNames) RecordKeyNaming.toSnakeColumnName(name) else name

}
