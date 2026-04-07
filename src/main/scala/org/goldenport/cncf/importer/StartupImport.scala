package org.goldenport.cncf.importer

import cats.~>
import java.util.concurrent.atomic.AtomicLong
import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*
import scala.util.Using
import org.goldenport.Consequence
import org.goldenport.configuration.ResolvedConfiguration
import org.goldenport.cncf.config.ConfigurationAccess
import org.goldenport.cncf.config.RuntimeConfig
import org.goldenport.cncf.context.{DataStoreContext, EntityStoreContext, ExecutionContext, RuntimeContext, ScopeContext, ScopeKind}
import org.goldenport.cncf.datastore.{DataStore, DataStoreSeed, DataStoreSeedEntry, DataStoreSpace}
import org.goldenport.cncf.entity.{EntityPersistent, EntityStoreSeed, EntityStoreSeedEntry, EntityStoreSpace}
import org.goldenport.cncf.entity.runtime.EntityCollection
import org.goldenport.cncf.subsystem.Subsystem
import org.goldenport.cncf.unitofwork.{UnitOfWork, UnitOfWorkOp}
import org.goldenport.record.Record
import org.goldenport.record.io.RecordDecoder
import org.simplemodeling.model.datatype.EntityCollectionId

/*
 * @since   Mar. 27, 2026
 * @author  ASAMI, Tomoharu
 * @version Mar. 28, 2026
 */
object StartupImport {
  val DataFileKey = "cncf.import.data.file"
  val EntityFileKey = "cncf.import.entity.file"
  val DefaultDataDirName = "data.d"
  val DefaultEntityDirName = "entity.d"
  private val _url_source_path = Path.of("startup-import-url")
  private val _data_import_sequence = new AtomicLong(0L)
  private val _record_decoder = RecordDecoder()
  private sealed trait _ImportSource
  private object _ImportSource {
    final case class Local(path: Path) extends _ImportSource
    final case class Url(url: String) extends _ImportSource
  }

  def run(
    cwd: Path,
    configuration: ResolvedConfiguration,
    runtimeConfig: RuntimeConfig,
    subsystem: Subsystem
  ): Consequence[Unit] = {
    val context = _bootstrap_execution_context(runtimeConfig)
    given ExecutionContext = context
    for {
      _ <- _import_data(cwd, configuration, runtimeConfig.dataStoreSpace)
      _ <- _import_entity(cwd, configuration, runtimeConfig.entityStoreSpace, subsystem)
    } yield ()
  }

  private def _import_data(
    cwd: Path,
    configuration: ResolvedConfiguration,
    dataStoreSpace: DataStoreSpace
  )(using ctx: ExecutionContext): Consequence[Unit] =
    _resolve_data_sources(cwd, configuration).flatMap { paths =>
      paths.foldLeft(Consequence.unit) { (z, path) =>
        z.flatMap(_ => _import_data_file(path, dataStoreSpace))
      }
    }

  private def _import_entity(
    cwd: Path,
    configuration: ResolvedConfiguration,
    entityStoreSpace: EntityStoreSpace,
    subsystem: Subsystem
  )(using ctx: ExecutionContext): Consequence[Unit] =
    _resolve_entity_sources(cwd, configuration).flatMap { paths =>
      paths.foldLeft(Consequence.unit) { (z, path) =>
        z.flatMap(_ => _import_entity_file(path, entityStoreSpace, subsystem))
      }
    }

  private def _resolve_data_sources(
    cwd: Path,
    configuration: ResolvedConfiguration
  )(using ctx: ExecutionContext): Consequence[Vector[_ImportSource]] =
    _resolve_sources(cwd, configuration, DataFileKey, DefaultDataDirName)

  private def _resolve_entity_sources(
    cwd: Path,
    configuration: ResolvedConfiguration
  )(using ctx: ExecutionContext): Consequence[Vector[_ImportSource]] =
    _resolve_sources(cwd, configuration, EntityFileKey, DefaultEntityDirName)

  private def _resolve_sources(
    cwd: Path,
    configuration: ResolvedConfiguration,
    key: String,
    defaultDirName: String
  )(using ctx: ExecutionContext): Consequence[Vector[_ImportSource]] = {
    val explicit = ConfigurationAccess.getString(configuration, key).filter(_.trim.nonEmpty)
    val default = _default_import_path(cwd, defaultDirName)
    (explicit, default) match {
      case (Some(raw), Some(dir)) =>
        _resolve_source(cwd, raw, allowDirectory = true).flatMap { explicitPaths =>
          _resolve_source(cwd, dir.toString, allowDirectory = true).map(defaultPaths => (explicitPaths ++ defaultPaths).distinct)
        }
      case (Some(raw), None) =>
        _resolve_source(cwd, raw, allowDirectory = true)
      case (None, Some(dir)) =>
        _resolve_source(cwd, dir.toString, allowDirectory = true)
      case (None, None) =>
        Consequence.success(Vector.empty)
    }
  }

  private def _resolve_source(
    cwd: Path,
    raw: String,
    allowDirectory: Boolean
  )(using ctx: ExecutionContext): Consequence[Vector[_ImportSource]] =
    if (_is_url(raw))
      Consequence.success(Vector(_ImportSource.Url(raw.trim)))
    else
      _resolve_paths(cwd, raw).map(_.map(_ImportSource.Local.apply))

  private def _import_data_file(
    source: _ImportSource,
    dataStoreSpace: DataStoreSpace
  )(using ctx: ExecutionContext): Consequence[Unit] =
    source match {
      case _ImportSource.Local(path) =>
        _load_data_seed(path).flatMap(seed => _store_data_seed(dataStoreSpace, seed))
      case _ImportSource.Url(url) =>
        _load_text_from_url(url).flatMap(text => _decode_data_seed(url, text)).flatMap(seed => _store_data_seed(dataStoreSpace, seed))
    }

  private def _import_entity_file(
    source: _ImportSource,
    entityStoreSpace: EntityStoreSpace,
    subsystem: Subsystem
  )(using ctx: ExecutionContext): Consequence[Unit] =
    source match {
      case _ImportSource.Local(path) =>
        _load_entity_seed(path).flatMap(seed => _store_entity_seed(path, entityStoreSpace, subsystem, seed))
      case _ImportSource.Url(url) =>
        _load_text_from_url(url).flatMap(text => _decode_entity_seed(url, text)).flatMap(seed => _store_entity_seed(_url_source_path, entityStoreSpace, subsystem, seed))
    }

  private def _store_data_seed(
    dataStoreSpace: DataStoreSpace,
    seed: DataStoreSeed
  )(using ctx: ExecutionContext): Consequence[Unit] =
    seed.entries.foldLeft(Consequence.unit) { (z, entry) =>
      z.flatMap(_ => _store_data_entry(dataStoreSpace, entry))
    }

  private def _store_data_entry(
    dataStoreSpace: DataStoreSpace,
    entry: DataStoreSeedEntry
  )(using ctx: ExecutionContext): Consequence[Unit] = {
    val cid = entry.collection
    val id = _entry_id(entry.record, cid)
    for {
      ds <- dataStoreSpace.dataStore(cid)
      r <- ds.save(cid, id, entry.record).recoverWith { case _ =>
        ds.create(cid, id, entry.record)
      }
    } yield r
  }

  private def _load_data_seed(
    path: Path
  ): Consequence[DataStoreSeed] =
    _read_text(path).flatMap(text => _decode_data_seed(path.toString, text))

  private def _load_entity_seed(
    path: Path
  ): Consequence[Vector[_EntityImportEntry]] =
    _read_text(path).flatMap(text => _decode_entity_seed(path.toString, text))

  private def _resolve_paths(
    cwd: Path,
    raw: String
  ): Consequence[Vector[Path]] = {
    val path = _resolve_path(cwd, raw)
    if (!Files.exists(path))
      Consequence.failure(s"configured import path does not exist: ${path}")
    else if (Files.isRegularFile(path)) {
      if (!_is_supported_import_file(path))
        Consequence.failure(s"unsupported import file extension: ${path}")
      else if (!Files.isReadable(path))
        Consequence.failure(s"configured import file is not readable: ${path}")
      else
        Consequence.success(Vector(path.toAbsolutePath.normalize))
    } else if (Files.isDirectory(path)) {
      if (!Files.isReadable(path))
        Consequence.failure(s"configured import directory is not readable: ${path}")
      else
        _collect_yaml_files(path)
    } else {
      Consequence.failure(s"configured import path is not a file or directory: ${path}")
    }
  }

  private def _collect_yaml_files(
    dir: Path
  ): Consequence[Vector[Path]] =
    try {
      Using.resource(Files.walk(dir)) { stream =>
        val xs =
          stream.iterator().asScala
            .filter(Files.isRegularFile(_))
            .filter(_is_supported_import_file)
            .map(_.toAbsolutePath.normalize)
            .toVector
            .sortBy(_.toString)
        Consequence.success(xs)
      }
    } catch {
      case e: Throwable if scala.util.control.NonFatal(e) =>
        Consequence.failure(s"failed to scan import directory ${dir}: ${e.getMessage}")
    }

  private def _resolve_path(
    cwd: Path,
    raw: String
  ): Path = {
    val path = Path.of(raw.trim)
    if (path.isAbsolute)
      path.normalize
    else
      cwd.resolve(path).normalize
  }

  private def _default_import_path(
    cwd: Path,
    dirname: String
  ): Option[Path] = {
    val path = cwd.resolve(dirname).normalize
    if (Files.exists(path) && Files.isDirectory(path) && Files.isReadable(path))
      Some(path)
    else
      None
  }

  private def _is_supported_import_file(
    path: Path
  ): Boolean = {
    val lower = path.getFileName.toString.toLowerCase
    lower.endsWith(".yaml") || lower.endsWith(".yml") ||
    lower.endsWith(".json") || lower.endsWith(".xml") ||
    lower.endsWith(".csv") || lower.endsWith(".tsl")
  }

  private def _read_text(
    path: Path
  ): Consequence[String] =
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
      case e: Throwable if scala.util.control.NonFatal(e) =>
        Consequence.failure(s"failed to load import text ${path}: ${e.getMessage}")
    }

  private def _load_text_from_url(
    url: String
  )(using ctx: ExecutionContext): Consequence[String] = {
    val uow = new UnitOfWork(ctx)
    val response = uow.execute(UnitOfWorkOp.HttpGet(url))
    if (response.code != 200) {
      Consequence.failure(s"startup import URL fetch failed: ${url} (HTTP ${response.code})")
    } else {
      response.getString match {
        case Some(body) =>
          Consequence.success(body)
        case None =>
          Consequence.failure(s"startup import URL returned empty body: ${url}")
      }
    }
  }

  private def _is_url(raw: String): Boolean =
    val s = raw.trim.toLowerCase
    s.startsWith("http://") || s.startsWith("https://")

  private def _decode_data_seed(
    sourceName: String,
    content: String
  ): Consequence[DataStoreSeed] =
    _decode_documents(sourceName, content).flatMap { roots =>
      roots.foldLeft(Consequence.success(Vector.empty[DataStoreSeedEntry])) { (z, root) =>
        z.flatMap { xs =>
          _asMap(root) match {
            case None =>
              Consequence.failure(s"startup import root must be a mapping: ${sourceName}")
            case Some(map) =>
              _parse_data_sections(map).map(xs ++ _)
          }
        }
      }.flatMap { entries =>
        if (entries.isEmpty)
          Consequence.failure(s"startup import file has no datastore entries: ${sourceName}")
        else
          Consequence.success(DataStoreSeed(entries))
      }
    }

  private def _decode_entity_seed(
    sourceName: String,
    content: String
  ): Consequence[Vector[_EntityImportEntry]] =
    _decode_documents(sourceName, content).flatMap { roots =>
      roots.foldLeft(Consequence.success(Vector.empty[_EntityImportEntry])) { (z, root) =>
        z.flatMap { xs =>
          _asMap(root) match {
            case None =>
              Consequence.failure(s"startup import root must be a mapping: ${sourceName}")
            case Some(map) =>
              _parse_entity_sections(map).map(xs ++ _)
          }
        }
      }.flatMap { entries =>
        if (entries.isEmpty)
          Consequence.failure(s"startup import file has no entitystore entries: ${sourceName}")
        else
          Consequence.success(entries)
      }
    }

  private def _decode_documents(
    sourceName: String,
    content: String
  ): Consequence[Vector[Record]] = {
    val lower = sourceName.toLowerCase
    if (lower.endsWith(".json")) {
      _record_decoder.jsonAutoRecords(content)
    } else if (lower.endsWith(".xml")) {
      _record_decoder.xmlAutoRecords(content)
    } else if (lower.endsWith(".csv")) {
      _record_decoder.csvRecords(content)
    } else if (lower.endsWith(".tsl")) {
      _record_decoder.tslRecords(content)
    } else {
      _record_decoder.yamlAutoRecords(content)
    }
  }

  private def _parse_data_sections(
    root: Map[String, Any]
  ): Consequence[Vector[DataStoreSeedEntry]] =
    root.get("datastore") match {
      case None =>
        root.get("collection") match {
          case None =>
            Consequence.failure("startup import file has no datastore entries")
          case Some(collectionValue) =>
            _parse_data_collection_value(collectionValue).map { collection =>
              Vector(DataStoreSeedEntry(collection, _record_without_key(Record.create(root), "collection")))
            }
        }
      case Some(value) =>
        _parse_data_section(value)
    }

  private def _parse_entity_sections(
    root: Map[String, Any]
  ): Consequence[Vector[_EntityImportEntry]] =
    root.get("entitystore") match {
      case None =>
        root.get("collection") match {
          case None =>
            Consequence.failure("startup import file has no entitystore entries")
          case Some(collectionValue) =>
            _parse_entity_collection_value(collectionValue).map { collection =>
              Vector(_EntityImportEntry(collection, _record_without_key(Record.create(root), "collection")))
            }
        }
      case Some(value) =>
        _parse_entity_section(value)
    }

  private def _parse_data_section(
    value: Any
  ): Consequence[Vector[DataStoreSeedEntry]] =
    _asSeq(value) match {
      case None =>
        Consequence.failure("datastore section must be a list")
      case Some(entries) =>
        entries.foldLeft(Consequence.success(Vector.empty[DataStoreSeedEntry])) { (acc, entry) =>
          acc.flatMap { xs =>
            _parse_data_section_entry(entry).map(xs ++ _)
          }
        }
    }

  private def _parse_entity_section(
    value: Any
  ): Consequence[Vector[_EntityImportEntry]] =
    _asSeq(value) match {
      case None =>
        Consequence.failure("entitystore section must be a list")
      case Some(entries) =>
        entries.foldLeft(Consequence.success(Vector.empty[_EntityImportEntry])) { (acc, entry) =>
          acc.flatMap { xs =>
            _parse_entity_section_entry(entry).map(xs ++ _)
          }
        }
    }

  private def _parse_data_section_entry(
    value: Any
  ): Consequence[Vector[DataStoreSeedEntry]] =
    _asMap(value) match {
      case None =>
        Consequence.failure("datastore entry must be a mapping")
      case Some(map) =>
        for {
          collectionText <- _requiredString(map, "collection")
          collection <- _parse_data_collection(collectionText)
          recordsValue <- _requiredValue(map, "records")
          records <- _parse_records(recordsValue)
        } yield records.map(DataStoreSeedEntry(collection, _))
    }

  private def _parse_entity_section_entry(
    value: Any
  ): Consequence[Vector[_EntityImportEntry]] =
    _asMap(value) match {
      case None =>
        Consequence.failure("entitystore entry must be a mapping")
      case Some(map) =>
        for {
          collectionText <- _requiredString(map, "collection")
          collection <- _parse_entity_collection(collectionText)
          recordsValue <- _requiredValue(map, "records")
          records <- _parse_records(recordsValue)
        } yield records.map(_EntityImportEntry(collection, _))
    }

  private def _parse_records(
    value: Any
  ): Consequence[Vector[Record]] =
    _asSeq(value) match {
      case None =>
        Consequence.failure("records must be a list")
      case Some(entries) =>
        entries.foldLeft(Consequence.success(Vector.empty[Record])) { (acc, entry) =>
          acc.flatMap(xs => _parse_record(entry).map(xs :+ _))
        }
    }

  private def _parse_record(
    value: Any
  ): Consequence[Record] =
    _asMap(value) match {
      case None =>
        Consequence.failure("record must be a mapping")
      case Some(map) =>
        Consequence.success(Record.dataAuto(map.toVector.sortBy(_._1).map { case (k, v) => k -> _convert_value(v) }*))
    }

  private def _parse_data_collection(
    value: String
  ): Consequence[DataStore.CollectionId] = {
    val normalized = value.trim
    if (normalized.isEmpty)
      Consequence.failure("collection is required")
    else if (normalized.startsWith("entity:"))
      _parse_entity_collection(normalized.drop("entity:".length)).map(DataStore.CollectionId.EntityStore.apply)
    else
      Consequence.success(DataStore.CollectionId(normalized))
  }

  private def _parse_data_collection_value(
    value: Any
  ): Consequence[DataStore.CollectionId] =
    _parse_data_collection(value.toString)

  private def _parse_entity_collection(
    value: String
  ): Consequence[EntityCollectionId] = {
    val segments = value.split("\\.").toVector.map(_.trim).filter(_.nonEmpty)
    segments match {
      case Vector(major, minor, name) =>
        Consequence.success(EntityCollectionId(major, minor, name))
      case _ =>
        Consequence.failure(s"invalid entity collection id: ${value}")
    }
  }

  private def _parse_entity_collection_value(
    value: Any
  ): Consequence[EntityCollectionId] =
    _parse_entity_collection(value.toString)

  private def _requiredString(
    map: Map[String, Any],
    key: String
  ): Consequence[String] =
    map.get(key) match {
      case Some(v: String) if v.trim.nonEmpty => Consequence.success(v)
      case Some(v) => Consequence.success(v.toString)
      case None => Consequence.failure(s"missing required key: ${key}")
    }

  private def _requiredValue(
    map: Map[String, Any],
    key: String
  ): Consequence[Any] =
    map.get(key) match {
      case Some(v) => Consequence.success(v)
      case None => Consequence.failure(s"missing required key: ${key}")
    }

  private def _asMap(
    value: Any
  ): Option[Map[String, Any]] =
    value match {
      case r: Record =>
        Some(r.asMap)
      case m: java.util.Map[?, ?] =>
        Some(m.asScala.toMap.asInstanceOf[Map[String, Any]])
      case m: Map[?, ?] =>
        Some(m.asInstanceOf[Map[String, Any]])
      case _ =>
        None
    }

  private def _asSeq(
    value: Any
  ): Option[Vector[Any]] =
    value match {
      case xs: java.util.List[?] =>
        Some(xs.asScala.toVector.asInstanceOf[Vector[Any]])
      case xs: Seq[?] =>
        Some(xs.toVector.asInstanceOf[Vector[Any]])
      case _ =>
        None
    }

  private def _convert_value(
    value: Any
  ): Any =
    value match {
      case m: java.util.Map[?, ?] =>
        val entries = m.asScala.toVector.map { case (k, v) => k.toString -> _convert_value(v) }
        Record.dataAuto(entries*)
      case m: Map[?, ?] =>
        val entries = m.toVector.map { case (k, v) => k.toString -> _convert_value(v) }
        Record.dataAuto(entries*)
      case xs: java.util.List[?] =>
        xs.asScala.toVector.map(_convert_value)
      case xs: Seq[?] =>
        xs.toVector.map(_convert_value)
      case other =>
        other
    }

  private def _record_without_key(
    record: Record,
    key: String
  ): Record =
    Record.create(record.asMap.toVector.filterNot(_._1 == key).sortBy(_._1))

  private def _store_entity_seed(
    path: Path,
    entityStoreSpace: EntityStoreSpace,
    subsystem: Subsystem,
    seed: Vector[_EntityImportEntry]
  )(using ctx: ExecutionContext): Consequence[Unit] =
    seed.foldLeft(Consequence.unit) { (z, entry) =>
      z.flatMap(_ => _store_entity_entry(path, entityStoreSpace, subsystem, entry))
    }

  private def _store_entity_entry(
    path: Path,
    entityStoreSpace: EntityStoreSpace,
    subsystem: Subsystem,
    entry: _EntityImportEntry
  )(using ctx: ExecutionContext): Consequence[Unit] =
    _resolve_entity_collection(subsystem, entry.collection) match {
      case None =>
        Consequence.failure(s"startup entity import collection is not registered: ${entry.collection.print}")
      case Some(collection) =>
        _import_entity_collection(path, entityStoreSpace, collection, entry.entity)
    }

  private def _resolve_entity_collection(
    subsystem: Subsystem,
    collectionId: EntityCollectionId
  ): Option[EntityCollection[?]] = {
    subsystem.components.iterator
      .flatMap(component =>
        component.entitySpace.entityOption(collectionId).orElse(component.entitySpace.entityOption(collectionId.name))
      )
      .toSeq
      .headOption
  }

  private def _import_entity_collection[E](
    path: Path,
    entityStoreSpace: EntityStoreSpace,
    collection: EntityCollection[E],
    record: Record
  )(using ctx: ExecutionContext): Consequence[Unit] = {
    val persistent = collection.descriptor.persistent
    given EntityPersistent[E] = persistent
    persistent.fromRecord(record).flatMap { entity =>
      val seed = EntityStoreSeed(Vector(EntityStoreSeedEntry(entity)))
      entityStoreSpace.importSeed(seed).map { _ =>
        collection.storage.storeRealm.put(entity)
        collection.storage.memoryRealm.foreach(_.put(entity))
      }
    }
  }

  private def _bootstrap_execution_context(
    runtimeConfig: RuntimeConfig
  ): ExecutionContext = {
    val observability = ExecutionContext.create().observability
    lazy val context: ExecutionContext = ExecutionContext.create(runtime)
    lazy val runtime: RuntimeContext = new RuntimeContext(
      core = ScopeContext.Core(
        kind = ScopeKind.Runtime,
        name = "startup-import",
        parent = None,
        observabilityContext = observability,
        httpDriverOption = Some(runtimeConfig.httpDriver),
        datastore = Some(DataStoreContext(runtimeConfig.dataStoreSpace)),
        entitystore = Some(EntityStoreContext(runtimeConfig.entityStoreSpace))
      ),
      unitOfWorkSupplier = () => new UnitOfWork(context),
      unitOfWorkInterpreterFn = new (UnitOfWorkOp ~> Consequence) {
        def apply[A](fa: UnitOfWorkOp[A]): Consequence[A] = {
          val _ = fa
          throw new UnsupportedOperationException("startup import does not use unit of work")
        }
      },
      commitAction = _ => (),
      abortAction = _ => (),
      disposeAction = _ => (),
      token = "startup-import"
    )
    context
  }

  private def _entry_id(
    record: Record,
    cid: DataStore.CollectionId
  ): DataStore.EntryId =
    record.getAny("id") match {
      case Some(m: org.goldenport.id.UniversalId) => DataStore.StringEntryId(m.print)
      case Some(s: String) => DataStore.StringEntryId(s)
      case Some(v) => DataStore.StringEntryId(v.toString)
      case None =>
        DataStore.StringEntryId(
          s"startup-import-${cid.collectionName}-${_data_import_sequence.incrementAndGet()}"
        )
    }

  private final case class _EntityImportEntry(
    collection: EntityCollectionId,
    entity: Record
  )
}
