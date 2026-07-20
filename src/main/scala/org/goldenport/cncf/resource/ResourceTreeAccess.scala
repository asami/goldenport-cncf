package org.goldenport.cncf.resource

import java.nio.file.{Files, LinkOption, Path}
import java.util.Locale
import scala.jdk.CollectionConverters.*
import scala.util.Try
import scala.util.control.NonFatal
import org.goldenport.Consequence

/*
 * @since   Jul. 17, 2026
 * @version Jul. 20, 2026
 * @author  ASAMI, Tomoharu
 */
final case class ResourceTreeReference private[resource] (name: String)

object ResourceTreeReference {
  private val _name_pattern = "^[a-z][a-z0-9-]{0,63}$".r

  def parseC(value: String): Consequence[ResourceTreeReference] = {
    val name = Option(value).map(_.trim.toLowerCase(Locale.ROOT)).getOrElse("")
    if (_name_pattern.matches(name))
      Consequence.success(ResourceTreeReference(name))
    else
      Consequence.argumentFormatError("resourceTree", "a lowercase logical resource tree name", value)
  }
}

final case class ResourceTreeLimits(
  maxDepth: Int = ResourceTreeLimits.DefaultMaxDepth,
  maxEntries: Int = ResourceTreeLimits.DefaultMaxEntries,
  maxFileBytes: Long = ResourceTreeLimits.DefaultMaxFileBytes,
  maxTotalBytes: Long = ResourceTreeLimits.DefaultMaxTotalBytes
) {
  def validateC: Consequence[ResourceTreeLimits] =
    if (maxDepth < 0)
      Consequence.argumentInvalid("maxDepth", "a non-negative resource tree depth", maxDepth)
    else if (maxEntries < 0)
      Consequence.argumentInvalid("maxEntries", "a non-negative resource tree entry count", maxEntries)
    else if (maxFileBytes < 0)
      Consequence.argumentInvalid("maxFileBytes", "a non-negative resource tree byte limit", maxFileBytes)
    else if (maxTotalBytes < 0)
      Consequence.argumentInvalid("maxTotalBytes", "a non-negative resource tree byte limit", maxTotalBytes)
    else
      Consequence.success(this)

  /**
   * Applies a caller request without permitting it to broaden the admitted
   * resource-tree limits.
   */
  def tightenC(requested: ResourceTreeLimits): Consequence[ResourceTreeLimits] =
    for {
      _ <- validateC
      _ <- requested.validateC
      depth <- _tighten_c("maxDepth", maxDepth.toLong, requested.maxDepth.toLong)
      entries <- _tighten_c("maxEntries", maxEntries.toLong, requested.maxEntries.toLong)
      filebytes <- _tighten_c("maxFileBytes", maxFileBytes, requested.maxFileBytes)
      totalbytes <- _tighten_c("maxTotalBytes", maxTotalBytes, requested.maxTotalBytes)
    } yield ResourceTreeLimits(depth.toInt, entries.toInt, filebytes, totalbytes)

  /** Applies a runtime/program cap to already admitted limits. */
  def narrowC(maximum: ResourceTreeLimits): Consequence[ResourceTreeLimits] =
    for {
      _ <- validateC
      _ <- maximum.validateC
    } yield ResourceTreeLimits(
      math.min(maxDepth, maximum.maxDepth),
      math.min(maxEntries, maximum.maxEntries),
      math.min(maxFileBytes, maximum.maxFileBytes),
      math.min(maxTotalBytes, maximum.maxTotalBytes)
    )

  private def _tighten_c(
    name: String,
    maximum: Long,
    requested: Long
  ): Consequence[Long] =
    if (requested > maximum)
      Consequence.argumentLimitExceeded(name, maximum, requested, "resource.tree.limit")
    else
      Consequence.success(requested)
}

object ResourceTreeLimits {
  val DefaultMaxDepth: Int = 16
  val DefaultMaxEntries: Int = 1000
  val DefaultMaxFileBytes: Long = 16L * 1024L * 1024L
  val DefaultMaxTotalBytes: Long = 64L * 1024L * 1024L

  val default: ResourceTreeLimits = ResourceTreeLimits()
}

sealed abstract class ResourceTreeEntrySelector {
  def kind: String

  private[resource] def matches(entry: ResourceTreeEntry): Boolean
}

object ResourceTreeEntrySelector {
  final case class ExactLeafName private[resource] (name: String) extends ResourceTreeEntrySelector {
    val kind = "exact-leaf-name"

    private[resource] def matches(entry: ResourceTreeEntry): Boolean =
      entry.relativePath.split("/").lastOption.contains(name)
  }

  def exactLeafNameC(value: String): Consequence[ResourceTreeEntrySelector] =
    _exact_leaf_name_c(value).map(ExactLeafName(_))

  private def _exact_leaf_name_c(value: String): Consequence[String] = {
    val name = Option(value).map(_.trim).getOrElse("")
    if (
      name.isEmpty ||
      name == "." ||
      name == ".." ||
      name.contains("/") ||
      name.contains("\\") ||
      name.exists(_.isControl)
    )
      Consequence.argumentFormatError("leafName", "a safe bare resource-tree file name", value)
    else
      Consequence.success(name)
  }
}

final case class ResourceTreeQueryLimits(
  maxDepth: Int = ResourceTreeQueryLimits.DefaultMaxDepth,
  maxVisitedDirectories: Int = ResourceTreeQueryLimits.DefaultMaxVisitedDirectories,
  maxEntries: Int = ResourceTreeQueryLimits.DefaultMaxEntries,
  maxEntryBytes: Long = ResourceTreeQueryLimits.DefaultMaxEntryBytes,
  maxTotalBytes: Long = ResourceTreeQueryLimits.DefaultMaxTotalBytes
) {
  def validateC: Consequence[ResourceTreeQueryLimits] =
    if (maxDepth < 0)
      Consequence.argumentInvalid("maxDepth", "a non-negative resource tree query depth", maxDepth)
    else if (maxVisitedDirectories < 0)
      Consequence.argumentInvalid("maxVisitedDirectories", "a non-negative resource tree query directory count", maxVisitedDirectories)
    else if (maxEntries < 0)
      Consequence.argumentInvalid("maxEntries", "a non-negative resource tree query entry count", maxEntries)
    else if (maxEntryBytes < 0)
      Consequence.argumentInvalid("maxEntryBytes", "a non-negative resource tree query byte limit", maxEntryBytes)
    else if (maxTotalBytes < 0)
      Consequence.argumentInvalid("maxTotalBytes", "a non-negative resource tree query byte limit", maxTotalBytes)
    else
      Consequence.success(this)

  def tightenC(requested: ResourceTreeQueryLimits): Consequence[ResourceTreeQueryLimits] =
    for {
      _ <- validateC
      _ <- requested.validateC
      depth <- _tighten_c("maxDepth", maxDepth.toLong, requested.maxDepth.toLong)
      directories <- _tighten_c("maxVisitedDirectories", maxVisitedDirectories.toLong, requested.maxVisitedDirectories.toLong)
      entries <- _tighten_c("maxEntries", maxEntries.toLong, requested.maxEntries.toLong)
      entrybytes <- _tighten_c("maxEntryBytes", maxEntryBytes, requested.maxEntryBytes)
      totalbytes <- _tighten_c("maxTotalBytes", maxTotalBytes, requested.maxTotalBytes)
    } yield ResourceTreeQueryLimits(
      depth.toInt,
      directories.toInt,
      entries.toInt,
      entrybytes,
      totalbytes
    )

  /** Applies a runtime/program cap to already admitted query limits. */
  def narrowC(maximum: ResourceTreeQueryLimits): Consequence[ResourceTreeQueryLimits] =
    for {
      _ <- validateC
      _ <- maximum.validateC
    } yield ResourceTreeQueryLimits(
      math.min(maxDepth, maximum.maxDepth),
      math.min(maxVisitedDirectories, maximum.maxVisitedDirectories),
      math.min(maxEntries, maximum.maxEntries),
      math.min(maxEntryBytes, maximum.maxEntryBytes),
      math.min(maxTotalBytes, maximum.maxTotalBytes)
    )

  private def _tighten_c(
    name: String,
    maximum: Long,
    requested: Long
  ): Consequence[Long] =
    if (requested > maximum)
      Consequence.argumentLimitExceeded(name, maximum, requested, "resource.tree.query.limit")
    else
      Consequence.success(requested)
}

object ResourceTreeQueryLimits {
  val DefaultMaxDepth: Int = 16
  val DefaultMaxVisitedDirectories: Int = 1000
  val DefaultMaxEntries: Int = 1000
  val DefaultMaxEntryBytes: Long = 16L * 1024L * 1024L
  val DefaultMaxTotalBytes: Long = 64L * 1024L * 1024L

  val default: ResourceTreeQueryLimits = ResourceTreeQueryLimits()
}

final case class ResourceTreeQuery private[resource] (
  reference: ResourceTreeReference,
  selector: ResourceTreeEntrySelector,
  limits: ResourceTreeQueryLimits
) {
  /** Rebinds this immutable query to a narrower component request. */
  def tightenC(requested: ResourceTreeQueryLimits): Consequence[ResourceTreeQuery] =
    limits.tightenC(requested).map(x => copy(limits = x))

  /** Rebinds this immutable query to a runtime/program cap. */
  def narrowC(maximum: ResourceTreeQueryLimits): Consequence[ResourceTreeQuery] =
    limits.narrowC(maximum).map(x => copy(limits = x))
}

object ResourceTreeQuery {
  def exactLeafNameC(
    reference: ResourceTreeReference,
    name: String,
    limits: ResourceTreeQueryLimits = ResourceTreeQueryLimits.default
  ): Consequence[ResourceTreeQuery] =
    for {
      selector <- ResourceTreeEntrySelector.exactLeafNameC(name)
      checkedlimits <- limits.validateC
    } yield ResourceTreeQuery(reference, selector, checkedlimits)
}

final case class ResourceTreeQueryResult private[resource] (
  query: ResourceTreeQuery,
  entries: Vector[ResourceTreeEntry],
  totalByteSize: Long,
  visitedDirectoryCount: Int
)

object ResourceTreeQueryResult {
  private[resource] def admitC(
    query: ResourceTreeQuery,
    entries: Vector[ResourceTreeEntry],
    visitedDirectoryCount: Int
  ): Consequence[ResourceTreeQueryResult] =
    query.limits.validateC.flatMap { _ =>
      val sorted = entries.sortBy(_.relativePath)
      val paths = sorted.map(_.relativePath)
      if (visitedDirectoryCount < 0 || visitedDirectoryCount > query.limits.maxVisitedDirectories)
        Consequence.resourceInvalid("resource tree query exceeds the configured directory visit limit")
      else if (paths.distinct.size != paths.size)
        Consequence.resourceInvalid("resource tree query contains duplicate logical paths")
      else if (sorted.exists(entry => !query.selector.matches(entry)))
        Consequence.resourceInvalid("resource tree query provider returned an entry outside the selector")
      else
        _validate_entries_c(query, sorted).map { total =>
          ResourceTreeQueryResult(query, sorted, total, visitedDirectoryCount)
        }
    }

  private def _validate_entries_c(
    query: ResourceTreeQuery,
    entries: Vector[ResourceTreeEntry]
  ): Consequence[Long] =
    if (entries.size > query.limits.maxEntries)
      Consequence.resourceInvalid("resource tree query exceeds the configured entry limit")
    else {
      entries.foldLeft(Consequence.success(0L)) { (z, entry) =>
        z.flatMap { total =>
          ResourceTreeEntry.relativePathC(entry.relativePath).flatMap { _ =>
            if (_depth(entry.relativePath) > query.limits.maxDepth)
              Consequence.resourceInvalid("resource tree query exceeds the configured depth limit")
            else if (entry.byteSize > query.limits.maxEntryBytes)
              Consequence.resourceInvalid("resource tree query entry exceeds the configured byte limit")
            else
              _add_size_c(total, entry.byteSize, query.limits.maxTotalBytes)
          }
        }
      }
    }

  private def _depth(path: String): Int =
    path.split("/").length

  private def _add_size_c(
    total: Long,
    size: Long,
    maximum: Long
  ): Consequence[Long] =
    Try(Math.addExact(total, size)).toOption match {
      case Some(next) if next <= maximum => Consequence.success(next)
      case _ => Consequence.resourceInvalid("resource tree query exceeds the configured aggregate byte limit")
    }
}

final case class ResourceTreeEntry private[resource] (
  relativePath: String,
  bytes: Vector[Byte],
  mediaType: Option[String] = None
) {
  def byteSize: Long = bytes.size.toLong
}

object ResourceTreeEntry {
  def createC(
    relativePath: String,
    bytes: Vector[Byte],
    mediaType: Option[String] = None
  ): Consequence[ResourceTreeEntry] =
    _relative_path_c(relativePath).map(path => ResourceTreeEntry(path, bytes, mediaType))

  private[resource] def fromValidated(
    relativePath: String,
    bytes: Vector[Byte],
    mediaType: Option[String]
  ): ResourceTreeEntry =
    ResourceTreeEntry(relativePath, bytes, mediaType)

  private[resource] def relativePathC(value: String): Consequence[String] =
    _relative_path_c(value)

  private def _relative_path_c(value: String): Consequence[String] = {
    val path = Option(value).map(_.trim).getOrElse("")
    val segments = path.split("/").toVector
    if (
      path.isEmpty ||
      path.startsWith("/") ||
      path.contains("\\") ||
      path.exists(_.isControl) ||
      segments.exists(segment => segment.isEmpty || segment == "." || segment == "..")
    )
      Consequence.argumentFormatError("relativePath", "a safe relative resource tree path", value)
    else
      Consequence.success(path)
  }
}

final case class ResourceTreeSnapshot private[resource] (
  reference: ResourceTreeReference,
  entries: Vector[ResourceTreeEntry],
  totalByteSize: Long,
  limits: ResourceTreeLimits
) {
  /**
   * Revalidates this opaque snapshot under a narrower request. This preserves
   * the resource admission boundary while allowing a consumer to request less.
   */
  def tightenC(requested: ResourceTreeLimits): Consequence[ResourceTreeSnapshot] =
    limits.tightenC(requested).flatMap(ResourceTreeSnapshot._revalidate_c(this, _))

  /** Revalidates this opaque snapshot under a runtime/program cap. */
  def narrowC(maximum: ResourceTreeLimits): Consequence[ResourceTreeSnapshot] =
    limits.narrowC(maximum).flatMap(ResourceTreeSnapshot._revalidate_c(this, _))
}

object ResourceTreeSnapshot {
  private[resource] def admitC(
    reference: ResourceTreeReference,
    entries: Vector[ResourceTreeEntry],
    limits: ResourceTreeLimits
  ): Consequence[ResourceTreeSnapshot] =
    limits.validateC.flatMap { checkedlimits =>
      val sorted = entries.sortBy(_.relativePath)
      val paths = sorted.map(_.relativePath)
      if (paths.distinct.size != paths.size)
        Consequence.resourceInvalid("resource tree contains duplicate logical paths")
      else
        _validate_entries_c(sorted, checkedlimits).map { total =>
          ResourceTreeSnapshot(reference, sorted, total, checkedlimits)
        }
    }

  private[resource] def _revalidate_c(
    snapshot: ResourceTreeSnapshot,
    limits: ResourceTreeLimits
  ): Consequence[ResourceTreeSnapshot] =
    admitC(snapshot.reference, snapshot.entries, limits)

  private def _validate_entries_c(
    entries: Vector[ResourceTreeEntry],
    limits: ResourceTreeLimits
  ): Consequence[Long] =
    if (entries.size > limits.maxEntries)
      Consequence.resourceInvalid("resource tree exceeds the configured entry limit")
    else {
      entries.foldLeft(Consequence.success(0L)) { (z, entry) =>
        z.flatMap { total =>
          ResourceTreeEntry.relativePathC(entry.relativePath).flatMap { _ =>
            if (_depth(entry.relativePath) > limits.maxDepth)
              Consequence.resourceInvalid("resource tree exceeds the configured depth limit")
            else if (entry.byteSize > limits.maxFileBytes)
              Consequence.resourceInvalid("resource tree entry exceeds the configured file byte limit")
            else
              _add_size_c(total, entry.byteSize, limits.maxTotalBytes)
          }
        }
      }
    }

  private def _depth(path: String): Int =
    path.split("/").length

  private def _add_size_c(
    total: Long,
    size: Long,
    maximum: Long
  ): Consequence[Long] =
    Try(Math.addExact(total, size)).toOption match {
      case Some(next) if next <= maximum => Consequence.success(next)
      case _ => Consequence.resourceInvalid("resource tree exceeds the configured aggregate byte limit")
    }
}

final case class ResourceTreeProviderMetadata(
  family: String,
  identity: String,
  configured: Boolean
) {
  def safeAttributes: Vector[(String, String)] =
    Vector(
      "resource_tree.provider.family" -> family,
      "resource_tree.provider.identity" -> identity,
      "resource_tree.provider.configured" -> configured.toString
    )
}

object ResourceTreeProviderMetadata {
  def unconfigured(reference: ResourceTreeReference): ResourceTreeProviderMetadata =
    ResourceTreeProviderMetadata("unconfigured", reference.name, configured = false)
}

trait ResourceTreeAccess {
  def snapshot(
    reference: ResourceTreeReference,
    limits: ResourceTreeLimits = ResourceTreeLimits.default
  ): Consequence[ResourceTreeSnapshot]

  def query(query: ResourceTreeQuery): Consequence[ResourceTreeQueryResult] =
    Consequence.notImplemented("resource tree query is not implemented for this provider")

  def providerMetadata(reference: ResourceTreeReference): ResourceTreeProviderMetadata =
    ResourceTreeProviderMetadata.unconfigured(reference)
}

object ResourceTreeAccess {
  val unavailable: ResourceTreeAccess = new ResourceTreeAccess {
    def snapshot(
      reference: ResourceTreeReference,
      limits: ResourceTreeLimits
    ): Consequence[ResourceTreeSnapshot] =
      Consequence.serviceUnavailable(
        s"resource tree access is not configured for ${reference.name}"
      )

    override def query(query: ResourceTreeQuery): Consequence[ResourceTreeQueryResult] =
      Consequence.serviceUnavailable(
        s"resource tree access is not configured for ${query.reference.name}"
      )
  }

  def inMemory(
    trees: Map[ResourceTreeReference, Vector[ResourceTreeEntry]]
  ): ResourceTreeAccess =
    new InMemoryResourceTreeAccess(trees)

  def local(policy: ResourceTreePolicy): ResourceTreeAccess =
    new LocalResourceTreeAccess(policy)
}

final case class ResourceTreePolicy(
  fileRoots: Map[String, Path] = Map.empty
) {
  lazy val normalizedFileRoots: Map[String, Path] =
    fileRoots.map { case (name, root) =>
      name.toLowerCase(Locale.ROOT) -> root.toAbsolutePath.normalize
    }
}

object ResourceTreePolicy {
  def fromValuesC(values: Vector[String]): Consequence[ResourceTreePolicy] =
    values.foldLeft(Consequence.success(Map.empty[String, Path])) { (z, value) =>
      for {
        roots <- z
        binding <- _binding_c(value)
        _ <- if (roots.contains(binding._1))
          Consequence.argumentInvalid(
            "textus.resource.tree.file-roots",
            "each logical resource tree exactly once",
            binding._1
          )
        else
          Consequence.unit
      } yield roots + binding
    }.map(ResourceTreePolicy(_))

  private def _binding_c(value: String): Consequence[(String, Path)] =
    value.trim.split("=", 2).toVector match {
      case Vector(rawname, rawroot) =>
        for {
          reference <- ResourceTreeReference.parseC(rawname)
          root <- Try(Path.of(rawroot.trim)).toOption match {
            case Some(path) if path.isAbsolute => Consequence.success(path.normalize)
            case _ => Consequence.argumentFormatError(
              "textus.resource.tree.file-roots",
              "<logical-tree-name>=<absolute filesystem path>",
              value
            )
          }
        } yield reference.name -> root
      case _ =>
        Consequence.argumentFormatError(
          "textus.resource.tree.file-roots",
          "<logical-tree-name>=<absolute filesystem path>",
          value
        )
    }
}

private final class InMemoryResourceTreeAccess(
  trees: Map[ResourceTreeReference, Vector[ResourceTreeEntry]]
) extends ResourceTreeAccess {
  private val _trees = trees

  def snapshot(
    reference: ResourceTreeReference,
    limits: ResourceTreeLimits
  ): Consequence[ResourceTreeSnapshot] =
    _trees.get(reference) match {
      case Some(entries) => ResourceTreeSnapshot.admitC(reference, entries, limits)
      case None => Consequence.resourceNotFound("configured logical resource tree is not available")
    }

  override def query(query: ResourceTreeQuery): Consequence[ResourceTreeQueryResult] =
    _trees.get(query.reference) match {
      case Some(entries) =>
        ResourceTreeQueryResult.admitC(
          query,
          entries.filter(query.selector.matches),
          visitedDirectoryCount = 0
        )
      case None => Consequence.resourceNotFound("configured logical resource tree is not available")
    }

  override def providerMetadata(reference: ResourceTreeReference): ResourceTreeProviderMetadata =
    ResourceTreeProviderMetadata("in-memory", reference.name, _trees.contains(reference))
}

private final class LocalResourceTreeAccess(
  policy: ResourceTreePolicy
) extends ResourceTreeAccess {
  private val _roots = policy.normalizedFileRoots

  private final case class _LocalCandidate(
    relativePath: String,
    path: Path,
    byteSize: Long
  )

  def snapshot(
    reference: ResourceTreeReference,
    limits: ResourceTreeLimits
  ): Consequence[ResourceTreeSnapshot] =
    _roots.get(reference.name) match {
      case Some(root) => _entries_c(root, limits).flatMap(ResourceTreeSnapshot.admitC(reference, _, limits))
      case None => Consequence.resourceNotFound("configured logical resource tree is not available")
    }

  override def providerMetadata(reference: ResourceTreeReference): ResourceTreeProviderMetadata =
    ResourceTreeProviderMetadata("local", reference.name, _roots.contains(reference.name))

  private def _entries_c(
    root: Path,
    limits: ResourceTreeLimits
  ): Consequence[Vector[ResourceTreeEntry]] =
    limits.validateC.flatMap { _ =>
      if (Files.isSymbolicLink(root))
        Consequence.resourceUnsupported("configured resource tree root must not be a symbolic link")
      else if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS))
        Consequence.resourceNotFound("configured logical resource tree is not available")
      else
        _walk_c(root, limits)
    }

  private def _walk_c(
    root: Path,
    limits: ResourceTreeLimits
  ): Consequence[Vector[ResourceTreeEntry]] =
    try {
      val stream = Files.walk(root)
      try {
        val paths = stream.iterator.asScala.toVector.sortBy(_.toString)
        val candidates = paths.foldLeft(Consequence.success(Vector.empty[_LocalCandidate])) { (z, path) =>
          z.flatMap { entries =>
            if (path == root)
              Consequence.success(entries)
            else if (Files.isSymbolicLink(path))
              Consequence.resourceUnsupported("configured resource tree contains a symbolic link")
            else if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS))
              Consequence.success(entries)
            else if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
              Consequence.resourceUnsupported("configured resource tree contains a non-regular file")
            else if (entries.size >= limits.maxEntries)
              Consequence.resourceInvalid("resource tree exceeds the configured entry limit")
            else
              _candidate_c(root, path, limits).flatMap(_append_candidate_c(entries, _, limits))
          }
        }
        candidates.flatMap(_read_entries_c)
      } finally {
        stream.close()
      }
    } catch {
      case NonFatal(_) =>
        Consequence.resourceInvalid("configured logical resource tree cannot be read")
    }

  private def _candidate_c(
    root: Path,
    path: Path,
    limits: ResourceTreeLimits
  ): Consequence[_LocalCandidate] =
    try {
      val relative = root.relativize(path).iterator.asScala.map(_.toString).mkString("/")
      ResourceTreeEntry.relativePathC(relative).flatMap { safePath =>
        if (safePath.split("/").length > limits.maxDepth)
          Consequence.resourceInvalid("resource tree exceeds the configured depth limit")
        else {
          val bytesize = Files.size(path)
          if (bytesize > limits.maxFileBytes)
            Consequence.resourceInvalid("resource tree entry exceeds the configured file byte limit")
          else
            Consequence.success(_LocalCandidate(safePath, path, bytesize))
        }
      }
    } catch {
      case NonFatal(_) =>
        Consequence.resourceInvalid("configured resource tree entry cannot be read")
    }

  private def _append_candidate_c(
    entries: Vector[_LocalCandidate],
    candidate: _LocalCandidate,
    limits: ResourceTreeLimits
  ): Consequence[Vector[_LocalCandidate]] = {
    val total = entries.foldLeft(0L) { (z, entry) => Math.addExact(z, entry.byteSize) }
    Try(Math.addExact(total, candidate.byteSize)).toOption match {
      case Some(next) if next <= limits.maxTotalBytes =>
        Consequence.success(entries :+ candidate)
      case _ =>
        Consequence.resourceInvalid("resource tree exceeds the configured aggregate byte limit")
    }
  }

  private def _read_entries_c(
    candidates: Vector[_LocalCandidate]
  ): Consequence[Vector[ResourceTreeEntry]] =
    candidates.foldLeft(Consequence.success(Vector.empty[ResourceTreeEntry])) { (z, candidate) =>
      z.flatMap { entries =>
        _read_entry_c(candidate).map(entries :+ _)
      }
    }

  private def _read_entry_c(
    candidate: _LocalCandidate
  ): Consequence[ResourceTreeEntry] =
    try {
      Consequence.success(ResourceTreeEntry.fromValidated(
        candidate.relativePath,
        Files.readAllBytes(candidate.path).toVector,
        Option(Files.probeContentType(candidate.path))
      ))
    } catch {
      case NonFatal(_) =>
        Consequence.resourceInvalid("configured resource tree entry cannot be read")
    }
}
