package org.goldenport.cncf.component

import java.nio.file.{Files, Path}
import scala.util.Using
import scala.util.control.NonFatal
import org.goldenport.Consequence
import org.goldenport.record.Record
import org.goldenport.record.io.RecordDecoder
import org.goldenport.cncf.entity.runtime.{EntityMemoryPolicy, EntityRuntimeDescriptor, PartitionStrategy}
import org.simplemodeling.model.datatype.EntityCollectionId

/*
 * Loader for component descriptors.
 *
 * Canonical direction:
 * - ComponentDescriptor is packaged in CAR
 * - local override uses CAR-style layout such as car.d/meta/component-descriptor.yaml
 * - explicit path may point at a descriptor file or a CAR-style directory
 *
 * Embedded CAR descriptor loading is a later step.
 *
 * @since   Mar. 27, 2026
 * @version Mar. 27, 2026
 * @author  ASAMI, Tomoharu
 */
object ComponentDescriptorLoader {
  private val _decoder = RecordDecoder()
  private val _canonical_meta_files = Vector(
    "meta/component-descriptor.yaml",
    "meta/component-descriptor.yml"
  )

  def load(path: Path): Consequence[Vector[ComponentDescriptor]] =
    if (!Files.exists(path))
      Consequence.failure(s"component descriptor path does not exist: ${path}")
    else if (Files.isDirectory(path))
      _load_directory(path)
    else if (Files.isRegularFile(path))
      _load_file(path)
    else
      Consequence.failure(s"component descriptor path is not a file or directory: ${path}")

  private def _load_directory(path: Path): Consequence[Vector[ComponentDescriptor]] = {
    val canonical = _resolve_canonical_descriptor_files(path)
    if (canonical.nonEmpty)
      _load_files(canonical)
    else
      Consequence.failure(s"component descriptor not found under canonical CAR layout: ${path}")
  }

  private def _resolve_canonical_descriptor_files(path: Path): Vector[Path] =
    _canonical_meta_files
      .map(path.resolve(_).normalize)
      .filter(Files.isRegularFile(_))
      .distinct

  private def _load_files(files: Vector[Path]): Consequence[Vector[ComponentDescriptor]] =
    files.foldLeft(Consequence.success(Vector.empty[ComponentDescriptor])) { (z, file) =>
      for {
        xs <- z
        ys <- _load_file(file)
      } yield xs ++ ys
    }

  private def _load_file(path: Path): Consequence[Vector[ComponentDescriptor]] =
    _read_text(path).flatMap(_decode(path.toString, _))

  private def _decode(origin: String, text: String): Consequence[Vector[ComponentDescriptor]] =
    _decoder.yamlAutoRecords(text).flatMap { records =>
      records.foldLeft(Consequence.success(Vector.empty[ComponentDescriptor])) { (z, rec) =>
        for {
          xs <- z
          x <- _to_descriptor(origin, rec)
        } yield xs :+ x
      }
    }

  private def _to_descriptor(origin: String, rec: Record): Consequence[ComponentDescriptor] = {
    val componentName = rec.getString("component").orElse(rec.getString("componentName"))
    val entities = _entity_descriptors(origin, rec)
    entities.map(xs => ComponentDescriptor(componentName = componentName, entityRuntimeDescriptors = xs))
  }

  private def _entity_descriptors(origin: String, rec: Record): Consequence[Vector[EntityRuntimeDescriptor]] =
    _to_entity_descriptor(origin, rec).map(Vector(_))

  private def _to_entity_descriptor(origin: String, rec: Record): Consequence[EntityRuntimeDescriptor] =
    for {
      entityName <- rec.getString("entity").orElse(rec.getString("entityName")).map(Consequence.success).getOrElse(Consequence.failure(s"missing entity/entityName in ${origin}"))
      major = rec.getString("collectionMajor").orElse(rec.getString("major")).getOrElse("sys")
      minor = rec.getString("collectionMinor").orElse(rec.getString("minor")).getOrElse("sys")
      name = rec.getString("collectionName").orElse(rec.getString("name")).getOrElse(entityName)
      memory = _memory_policy(rec.getString("memoryPolicy").orElse(rec.getString("memory_policy")).getOrElse("LoadToMemory"))
      partition = _partition_strategy(rec.getString("partitionStrategy").orElse(rec.getString("partition_strategy")).getOrElse("byOrganizationMonthUTC"))
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

  private def _int_value(rec: Record, keys: List[String], fallback: Int): Int =
    keys.iterator.flatMap(k => rec.getString(k).flatMap(s => scala.util.Try(s.trim.toInt).toOption)).toSeq.headOption.getOrElse(fallback)

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

  private def _read_text(path: Path): Consequence[String] =
    try {
      Using.resource(Files.newBufferedReader(path)) { reader =>
        val sb = new StringBuilder
        val buf = new Array[Char](8192)
        var read = reader.read(buf)
        while (read != -1) {
          sb.appendAll(buf, 0, read)
          read = reader.read(buf)
        }
        Consequence.success(sb.toString)
      }
    } catch {
      case NonFatal(e) => Consequence.failure(s"failed to read component descriptor ${path}: ${e.getMessage}")
    }
}
