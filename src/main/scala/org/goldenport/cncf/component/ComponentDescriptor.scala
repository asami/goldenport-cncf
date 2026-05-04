package org.goldenport.cncf.component

import org.goldenport.Consequence
import org.goldenport.record.Record
import org.goldenport.record.RecordDecoder
import org.goldenport.cncf.entity.runtime.EntityRuntimeDescriptor
import org.goldenport.cncf.entity.runtime.{EntityKind, EntityMemoryPolicy, PartitionStrategy, WorkingSetPolicy, WorkingSetPolicySource}
import org.goldenport.cncf.security.{EntityApplicationDomain, EntityOperationKind, EntityUsageKind}
import org.simplemodeling.model.datatype.EntityCollectionId

/*
 * Static descriptor for a component as packaged in CAR or provided through a
 * CAR-style local override.
 *
 * @since   Mar. 27, 2026
 *  version Apr. 24, 2026
 * @version May.  4, 2026
 * @author  ASAMI, Tomoharu
 */
final case class ComponentletDescriptor(
  name: String,
  kind: Option[String] = None,
  isPrimary: Option[Boolean] = None,
  archiveScope: Option[String] = None,
  implementationClass: Option[String] = None,
  factoryObject: Option[String] = None,
  extensions: Map[String, String] = Map.empty,
  config: Map[String, String] = Map.empty
)

final case class ComponentDescriptor(
  name: Option[String] = None,
  version: Option[String] = None,
  componentName: Option[String] = None,
  subsystemName: Option[String] = None,
  componentlets: Vector[ComponentletDescriptor] = Vector.empty,
  entityRuntimeDescriptors: Vector[EntityRuntimeDescriptor] = Vector.empty,
  extensionBindings: Record = Record.empty,
  extensions: Map[String, String] = Map.empty,
  config: Map[String, String] = Map.empty
)

object ComponentDescriptor {
  given RecordDecoder[EntityRuntimeDescriptor] with
    def fromRecord(rec: Record): Consequence[EntityRuntimeDescriptor] =
      for {
        entityName <- _string(rec, "entity", "entityName").map(Consequence.success).getOrElse(Consequence.argumentMissing("entity/entityName"))
        major = _string(rec, "collectionMajor", "major").getOrElse("sys")
        minor = _string(rec, "collectionMinor", "minor").getOrElse("sys")
        name = _string(rec, "collectionName", "name").getOrElse(entityName)
        memory = _memory_policy(_string(rec, "memoryPolicy", "memory_policy").getOrElse("LoadToMemory"))
        partition = _partition_strategy(_string(rec, "partitionStrategy", "partition_strategy").getOrElse("byOrganizationMonthUTC"))
        maxPartitions = _int_value(rec, List("maxPartitions", "max_partitions"), 64)
        maxEntities = _int_value(rec, List("maxEntitiesPerPartition", "max_entities_per_partition"), 10000)
        entityKindText = _string(rec, "entityKind", "entity_kind", "entityKindName", "entity_kind_name")
        operationKindText = _string(rec, "operationKind", "operation_kind", "entityOperationKind", "entity_operation_kind")
        entityKind <- entityKindText
          .map(EntityKind.parseC)
          .getOrElse(Consequence.success(operationKindText.map(x => EntityRuntimeDescriptor.legacyEntityKind(EntityOperationKind.parse(x))).getOrElse(EntityKind.default)))
        usageKind = _string(rec, "usageKind", "usage_kind", "entityUsage", "entity_usage").map(EntityUsageKind.parse).getOrElse(EntityUsageKind.default)
        operationKind = operationKindText
          .map(EntityOperationKind.parse)
          .orElse(entityKindText.map(_ => entityKind.legacyOperationKind))
          .getOrElse(EntityOperationKind.default)
        applicationDomain = _string(rec, "applicationDomain", "application_domain", "entityApplicationDomain", "entity_application_domain").map(EntityApplicationDomain.parse).getOrElse(EntityApplicationDomain.default)
        workingsetpolicy <- _working_set_policy(rec)
      } yield EntityRuntimeDescriptor(
        entityName = entityName,
        collectionId = EntityCollectionId(major, minor, name),
        memoryPolicy = memory,
        partitionStrategy = partition,
        maxPartitions = maxPartitions,
        maxEntitiesPerPartition = maxEntities,
        workingSetPolicy = workingsetpolicy,
        workingSetPolicySource = workingsetpolicy.map(_ => WorkingSetPolicySource.Cml),
        entityKind = entityKind,
        usageKind = usageKind,
        operationKind = operationKind,
        applicationDomain = applicationDomain,
        entityKindExplicit = entityKindText.nonEmpty
      )

  given RecordDecoder[ComponentletDescriptor] with
    def fromRecord(rec: Record): Consequence[ComponentletDescriptor] =
      _string(rec, "name")
        .map { name =>
          Consequence.success(
            ComponentletDescriptor(
              name = name,
              kind = _string(rec, "kind"),
              isPrimary = _boolean(rec, "isPrimary", "is_primary"),
              archiveScope = _string(rec, "archiveScope", "archive_scope"),
              implementationClass = _string(rec, "implementationClass", "implementation_class"),
              factoryObject = _string(rec, "factoryObject", "factory_object"),
              extensions = _string_map_value(rec, List("extension", "extensions")),
              config = _string_map_value(rec, List("config"))
            )
          )
        }
        .getOrElse(Consequence.argumentMissing("componentlets.name"))

  given RecordDecoder[ComponentDescriptor] with
    def fromRecord(rec: Record): Consequence[ComponentDescriptor] = {
      val componentrec = _component_record(rec)
      val componentName = _string(componentrec, "component", "componentName").orElse(_string(componentrec, "name"))
      val entitiesC = _entity_descriptors(componentrec)
      val componentletsC = _componentlet_descriptors(rec)
      val extensionBindings = _record_value(componentrec, List("extension_bindings", "extensionBindings", "extension_binding")).getOrElse(Record.empty)
      for {
        xs <- entitiesC
        componentlets <- componentletsC
      } yield
        ComponentDescriptor(
          name = _string(componentrec, "name").orElse(componentName),
          version = _string(componentrec, "version").orElse(_string(rec, "version")),
          componentName = componentName,
          subsystemName = _string(componentrec, "subsystem", "subsystemName").orElse(_string(rec, "subsystem", "subsystemName")),
          componentlets = componentlets,
          entityRuntimeDescriptors = xs,
          extensionBindings = extensionBindings,
          extensions = _component_extensions(componentrec),
          config = _string_map_value(componentrec, List("config"))
        )
    }

  private def _component_record(rec: Record): Record =
    _record_value(rec, List("component")).getOrElse(rec)

  private def _entity_descriptors(rec: Record): Consequence[Vector[EntityRuntimeDescriptor]] =
    rec.getAny("entities") match {
      case Some(xs: Seq[?]) =>
        xs.foldLeft(Consequence.success(Vector.empty[EntityRuntimeDescriptor])) { (z, x) =>
          for {
            acc <- z
            r <- _any_to_record(x).map(Consequence.success).getOrElse(Consequence.argumentInvalid("invalid entities entry"))
            d <- summon[RecordDecoder[EntityRuntimeDescriptor]].fromRecord(r)
          } yield acc :+ d
        }
      case _ =>
        if (_string(rec, "entity", "entityName").isDefined)
          summon[RecordDecoder[EntityRuntimeDescriptor]].fromRecord(rec).map(Vector(_))
        else
          Consequence.success(Vector.empty)
    }

  private def _componentlet_descriptors(rec: Record): Consequence[Vector[ComponentletDescriptor]] =
    rec.getAny("componentlets") match {
      case Some(xs: Seq[?]) =>
        xs.foldLeft(Consequence.success(Vector.empty[ComponentletDescriptor])) { (z, x) =>
          for {
            acc <- z
            descriptor <- _componentlet_descriptor(x)
          } yield acc :+ descriptor
        }
      case Some(xs: Array[?]) =>
        _componentlet_descriptors(Record.data("componentlets" -> xs.toVector))
      case Some(_: String) =>
        Consequence.success(Vector.empty)
      case Some(_) =>
        Consequence.argumentInvalid("invalid componentlets entry")
      case None =>
        Consequence.success(Vector.empty)
    }

  private def _componentlet_descriptor(value: Any): Consequence[ComponentletDescriptor] =
    value match {
      case s: String if s.trim.nonEmpty =>
        Consequence.success(ComponentletDescriptor(name = s.trim))
      case r: Record =>
        summon[RecordDecoder[ComponentletDescriptor]].fromRecord(r)
      case m: Map[?, ?] =>
        summon[RecordDecoder[ComponentletDescriptor]].fromRecord(Record.create(m.iterator.map { case (k, v) => k.toString -> v }.toMap))
      case _ =>
        Consequence.argumentInvalid("invalid componentlet entry")
    }

  private def _string(rec: Record, keys: String*): Option[String] =
    keys.iterator.map(rec.getString).collectFirst { case Some(s) if s.trim.nonEmpty => s.trim }

  private def _working_set_policy(
    rec: Record
  ): Consequence[Option[WorkingSetPolicy]] = {
    val nested = _record_value(rec, List("workingSetPolicy", "working_set_policy"))
    val source = nested.getOrElse(rec)
    val kind = _string(source, "kind", "workingSetPolicyKind", "working_set_policy_kind")
      .orElse(_string(rec, "workingSetPolicyKind", "working_set_policy_kind"))
    val duration = _string(source, "duration", "window", "workingSetPolicyDuration", "working_set_policy_duration")
      .orElse(_string(rec, "workingSetPolicyDuration", "working_set_policy_duration"))
    val timestampfield = _string(source, "timestampField", "timestamp_field", "field")
      .orElse(_string(rec, "workingSetPolicyTimestampField", "working_set_policy_timestamp_field"))
    kind match {
      case Some(value) =>
        WorkingSetPolicy.parse(value, duration, timestampfield).map(Some(_))
      case None =>
        Consequence.success(None)
    }
  }

  private def _boolean(rec: Record, keys: String*): Option[Boolean] =
    keys.iterator.map(rec.getBoolean).collectFirst {
      case Some(b) => b
    }.orElse {
      keys.iterator.map(rec.getString).collectFirst {
        case Some(s) if s.trim.equalsIgnoreCase("true") => true
        case Some(s) if s.trim.equalsIgnoreCase("false") => false
      }
    }

  private def _record_value(rec: Record, keys: List[String]): Option[Record] =
    keys.iterator.map(rec.getAny).collectFirst {
      case Some(r: Record) => r
      case Some(m: Map[?, ?]) => Record.create(m.iterator.map { case (k, v) => k.toString -> v }.toMap)
    }

  private def _component_extensions(rec: Record): Map[String, String] = {
    val reserved = Set(
      "name",
      "version",
      "component",
      "componentName",
      "subsystem",
      "subsystemName",
      "entities",
      "componentlets",
      "entity",
      "entityName",
      "extension",
      "extensions",
      "extension_bindings",
      "extensionBinding",
      "extensionBindings",
      "extension_binding",
      "config"
    )
    _string_map_value(rec, List("extension", "extensions")) ++
      rec.asMap.collect {
        case (k, v) if !reserved.contains(k) =>
          _scalar_string(v).map(k -> _)
      }.flatten.toMap
  }

  private def _string_map_value(rec: Record, keys: List[String]): Map[String, String] =
    _record_value(rec, keys).map(_.asMap.collect { case (k, v: String) => k -> v }).getOrElse(Map.empty)

  private def _scalar_string(value: Any): Option[String] =
    value match {
      case null => None
      case s: String => Option.when(s.trim.nonEmpty)(s.trim)
      case b: Boolean => Some(b.toString)
      case n: Byte => Some(n.toString)
      case n: Short => Some(n.toString)
      case n: Int => Some(n.toString)
      case n: Long => Some(n.toString)
      case n: Float => Some(n.toString)
      case n: Double => Some(n.toString)
      case n: BigInt => Some(n.toString)
      case n: BigDecimal => Some(n.toString)
      case _ => None
    }

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
