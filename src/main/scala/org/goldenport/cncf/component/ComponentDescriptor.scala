package org.goldenport.cncf.component

import org.goldenport.Consequence
import org.goldenport.record.Record
import org.goldenport.record.RecordDecoder
import org.goldenport.cncf.entity.runtime.EntityRuntimeDescriptor
import org.goldenport.cncf.entity.runtime.{EntityMemoryPolicy, PartitionStrategy}
import org.simplemodeling.model.datatype.EntityCollectionId

/*
 * Static descriptor for a component as packaged in CAR or provided through a
 * CAR-style local override.
 *
 * @since   Mar. 27, 2026
 * @version Apr.  8, 2026
 * @author  ASAMI, Tomoharu
 */
final case class ComponentDescriptor(
  name: Option[String] = None,
  version: Option[String] = None,
  componentName: Option[String] = None,
  subsystemName: Option[String] = None,
  entityRuntimeDescriptors: Vector[EntityRuntimeDescriptor] = Vector.empty,
  extensionBindings: Record = Record.empty,
  extensions: Map[String, String] = Map.empty,
  config: Map[String, String] = Map.empty
)

object ComponentDescriptor {
  given RecordDecoder[EntityRuntimeDescriptor] with
    def fromRecord(rec: Record): Consequence[EntityRuntimeDescriptor] =
      for {
        entityName <- _string(rec, "entity", "entityName").map(Consequence.success).getOrElse(Consequence.failure("missing entity/entityName"))
        major = _string(rec, "collectionMajor", "major").getOrElse("sys")
        minor = _string(rec, "collectionMinor", "minor").getOrElse("sys")
        name = _string(rec, "collectionName", "name").getOrElse(entityName)
        memory = _memory_policy(_string(rec, "memoryPolicy", "memory_policy").getOrElse("LoadToMemory"))
        partition = _partition_strategy(_string(rec, "partitionStrategy", "partition_strategy").getOrElse("byOrganizationMonthUTC"))
        maxPartitions = _int_value(rec, List("maxPartitions", "max_partitions"), 64)
        maxEntities = _int_value(rec, List("maxEntitiesPerPartition", "max_entities_per_partition"), 10000)
      } yield EntityRuntimeDescriptor(
        entityName = entityName,
        collectionId = EntityCollectionId(major, minor, name),
        memoryPolicy = memory,
        partitionStrategy = partition,
        maxPartitions = maxPartitions,
        maxEntitiesPerPartition = maxEntities
      )

  given RecordDecoder[ComponentDescriptor] with
    def fromRecord(rec: Record): Consequence[ComponentDescriptor] = {
      val componentName = _string(rec, "component", "componentName").orElse(_string(rec, "name"))
      val entitiesC = _entity_descriptors(rec)
      val extensionBindings = _record_value(rec, List("extension_bindings", "extensionBindings", "extension_binding")).getOrElse(Record.empty)
      entitiesC.map(xs =>
        ComponentDescriptor(
          name = _string(rec, "name").orElse(componentName),
          version = _string(rec, "version"),
          componentName = componentName,
          subsystemName = _string(rec, "subsystem", "subsystemName"),
          entityRuntimeDescriptors = xs,
          extensionBindings = extensionBindings,
          extensions = _string_map_value(rec, List("extension", "extensions")),
          config = _string_map_value(rec, List("config"))
        )
      )
    }

  private def _entity_descriptors(rec: Record): Consequence[Vector[EntityRuntimeDescriptor]] =
    rec.getAny("entities") match {
      case Some(xs: Seq[?]) =>
        xs.foldLeft(Consequence.success(Vector.empty[EntityRuntimeDescriptor])) { (z, x) =>
          for {
            acc <- z
            r <- _any_to_record(x).map(Consequence.success).getOrElse(Consequence.failure("invalid entities entry"))
            d <- summon[RecordDecoder[EntityRuntimeDescriptor]].fromRecord(r)
          } yield acc :+ d
        }
      case _ =>
        if (_string(rec, "entity", "entityName").isDefined)
          summon[RecordDecoder[EntityRuntimeDescriptor]].fromRecord(rec).map(Vector(_))
        else
          Consequence.success(Vector.empty)
    }

  private def _string(rec: Record, keys: String*): Option[String] =
    keys.iterator.map(rec.getString).collectFirst { case Some(s) if s.trim.nonEmpty => s.trim }

  private def _record_value(rec: Record, keys: List[String]): Option[Record] =
    keys.iterator.map(rec.getAny).collectFirst {
      case Some(r: Record) => r
      case Some(m: Map[?, ?]) => Record.create(m.iterator.map { case (k, v) => k.toString -> v }.toMap)
    }

  private def _string_map_value(rec: Record, keys: List[String]): Map[String, String] =
    _record_value(rec, keys).map(_.asMap.collect { case (k, v: String) => k -> v }).getOrElse(Map.empty)

  private def _any_to_record(value: Any): Option[Record] =
    value match {
      case r: Record => Some(r)
      case m: Map[?, ?] => Some(Record.create(m.iterator.map { case (k, v) => k.toString -> v }.toMap))
      case _ => None
    }

  private def _int_value(rec: Record, keys: List[String], fallback: Int): Int =
    keys.iterator.flatMap(k => rec.getInt(k).orElse(rec.getString(k).flatMap(s => scala.util.Try(s.trim.toInt).toOption))).toSeq.headOption.getOrElse(fallback)

  private def _memory_policy(p: String): EntityMemoryPolicy =
    p.trim.toLowerCase match {
      case "storeonly" | "store_only" | "store-only" => EntityMemoryPolicy.StoreOnly
      case _ => EntityMemoryPolicy.LoadToMemory
    }

  private def _partition_strategy(p: String): PartitionStrategy =
    p.trim match {
      case "byOrganizationMonthUTC" => PartitionStrategy.byOrganizationMonthUTC
      case _ => PartitionStrategy.byOrganizationMonthUTC
    }
}
