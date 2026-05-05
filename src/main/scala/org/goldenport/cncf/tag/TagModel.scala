package org.goldenport.cncf.tag

import java.time.Instant
import java.util.Locale
import java.util.UUID
import org.goldenport.Consequence
import org.goldenport.cncf.association.{Association, AssociationBindingWorkflow, AssociationDomain, AssociationFilter, AssociationRecordCodec, AssociationRepository, AssociationStoragePolicy}
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.directive.{Query, SearchResult}
import org.goldenport.cncf.entity.{EntityPersistent, EntityPersistentCreate, EntityQuery, EntitySearchScope, EntityStore, EntityVisibilityScope}
import org.goldenport.record.Record
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityId}

/*
 * Built-in hierarchical Tag master and Entity-to-Tag association workflow.
 *
 * @since   May.  5, 2026
 * @version May.  5, 2026
 * @author  ASAMI, Tomoharu
 */
enum TagUsageKind(val value: String) {
  case Powertype extends TagUsageKind("powertype")
  case Cms extends TagUsageKind("cms")
  case Navigation extends TagUsageKind("navigation")
  case General extends TagUsageKind("general")

  def print: String = value
}

object TagUsageKind {
  def parse(value: String): Consequence[TagUsageKind] =
    Option(value).map(_.trim.toLowerCase(Locale.ROOT)) match {
      case Some("powertype") => Consequence.success(TagUsageKind.Powertype)
      case Some("cms") => Consequence.success(TagUsageKind.Cms)
      case Some("navigation") => Consequence.success(TagUsageKind.Navigation)
      case Some("general") => Consequence.success(TagUsageKind.General)
      case Some(other) if other.nonEmpty => Consequence.argumentInvalid(s"unknown tag usage kind: $other")
      case _ => Consequence.success(TagUsageKind.General)
    }
}

object TagEntityCollections {
  val Tag: EntityCollectionId =
    EntityCollectionId("cncf", "builtin", "tag")
}

final case class Tag(
  id: EntityId,
  tagSpace: String,
  key: String,
  parentTagId: Option[EntityId],
  path: String,
  usageKind: TagUsageKind,
  sortOrder: Option[Int],
  title: Option[String],
  description: Option[String],
  createdAt: Instant,
  updatedAt: Instant,
  attributes: Map[String, String] = Map.empty
) {
  def toRecord: Record =
    TagRecordCodec.toRecord(this)
}

final case class TagCreate(
  id: Option[EntityId],
  key: String,
  parentTagId: Option[EntityId],
  tagSpace: String = TagRepository.DefaultTagSpace,
  usageKind: TagUsageKind = TagUsageKind.General,
  sortOrder: Option[Int] = None,
  title: Option[String] = None,
  description: Option[String] = None,
  attributes: Map[String, String] = Map.empty
)

trait TagRepository {
  def create(create: TagCreate)(using ExecutionContext): Consequence[Tag]
  def load(id: EntityId)(using ExecutionContext): Consequence[Option[Tag]]
  def list()(using ExecutionContext): Consequence[Vector[Tag]]
  def list(tagSpace: String)(using ExecutionContext): Consequence[Vector[Tag]]
  def list(tagSpaces: Vector[String])(using ExecutionContext): Consequence[Vector[Tag]]
  def tree()(using ExecutionContext): Consequence[TagTree]
  def tree(tagSpace: String)(using ExecutionContext): Consequence[TagTree]
  def tree(tagSpaces: Vector[String])(using ExecutionContext): Consequence[TagTree]
  def resolve(ref: String)(using ExecutionContext): Consequence[Tag]
}

object TagRepository {
  val DefaultTagSpace: String = "default"

  def entityStore(): TagRepository =
    new EntityStoreTagRepository()

  given EntityPersistent[Tag] with {
    def id(e: Tag): EntityId = e.id
    def toRecord(e: Tag): Record = TagRecordCodec.toRecord(e)
    override def toStoreRecord(e: Tag): Record = TagRecordCodec.toStoreRecord(e)
    def fromRecord(r: Record): Consequence[Tag] = TagRecordCodec.fromRecord(r)
    override def fromStoreRecord(r: Record): Consequence[Tag] = TagRecordCodec.fromStoreRecord(r)
  }

  given EntityPersistentCreate[TagCreate] with {
    def id(e: TagCreate): Option[EntityId] = e.id
    def collection(e: TagCreate): EntityCollectionId = TagEntityCollections.Tag
    def toRecord(e: TagCreate): Record = TagRecordCodec.toRecord(e)
    override def toStoreRecord(e: TagCreate): Record = TagRecordCodec.toStoreRecord(e)
  }
}

final class EntityStoreTagRepository extends TagRepository {
  import TagRepository.given

  def create(create: TagCreate)(using ctx: ExecutionContext): Consequence[Tag] =
    for {
      normalized <- _normalize_create(create)
      result <- EntityStore.standard().create(normalized)
      loaded <- EntityStore.standard().load[Tag](result.id)
      created <- loaded.map(Consequence.success).getOrElse(Consequence.operationNotFound(s"tag:${result.id.value}"))
    } yield {
      TagTreeCache.invalidate(normalized.tagSpace)
      _normalize_collection(created)
    }

  def load(id: EntityId)(using ctx: ExecutionContext): Consequence[Option[Tag]] =
    EntityStore.standard().load[Tag](_tag_id(id)).map(_.map(_normalize_collection))

  def list()(using ctx: ExecutionContext): Consequence[Vector[Tag]] =
    EntityStore.standard()
      .search[Tag](EntityQuery(TagEntityCollections.Tag, Query.plan(Record.empty), EntitySearchScope.Store, Some(EntityVisibilityScope.Admin)))
      .map(_.data.map(_normalize_collection).filterNot(_is_deleted).sortBy(x => (x.path, x.sortOrder.getOrElse(Int.MaxValue), x.key)))

  def list(tagSpace: String)(using ctx: ExecutionContext): Consequence[Vector[Tag]] =
    list().map(_.filter(_.tagSpace == TagSpace.normalize(tagSpace)))

  def list(tagSpaces: Vector[String])(using ctx: ExecutionContext): Consequence[Vector[Tag]] = {
    val spaces = tagSpaces.map(TagSpace.normalize).filter(_.nonEmpty).distinct
    list().map(_.filter(tag => spaces.contains(tag.tagSpace)))
  }

  def tree()(using ctx: ExecutionContext): Consequence[TagTree] =
    list().flatMap(TagTree.create)

  def tree(tagSpace: String)(using ctx: ExecutionContext): Consequence[TagTree] =
    TagTreeCache.getOrLoad(tagSpace) {
      list(tagSpace).flatMap(TagTree.create)
    }

  def tree(tagSpaces: Vector[String])(using ctx: ExecutionContext): Consequence[TagTree] =
    {
      val spaces = tagSpaces.map(TagSpace.normalize).filter(_.nonEmpty).distinct
      val targets = if (spaces.nonEmpty) spaces else Vector(TagRepository.DefaultTagSpace)
      targets.foldLeft(Consequence.success(Vector.empty[Tag])) { (z, space) =>
        z.flatMap(xs => tree(space).map(tree => xs ++ tree.tags))
      }.flatMap(xs => TagTree.create(xs.distinctBy(_.id.value)))
    }

  def resolve(ref: String)(using ctx: ExecutionContext): Consequence[Tag] =
    tree().flatMap(_.resolve(ref))

  private def _normalize_create(create: TagCreate)(using ctx: ExecutionContext): Consequence[TagCreate] =
    {
      val tagSpace = TagSpace.normalize(create.tagSpace)
      for {
      key <- TagPath.validateKey(create.key)
      parent <- create.parentTagId.map(load).map(_.flatMap {
        case Some(value) if value.tagSpace == tagSpace => Consequence.success(Some(value))
        case Some(value) => Consequence.argumentInvalid(s"parent tag belongs to a different tag space: ${value.tagSpace}")
        case None => Consequence.argumentInvalid(s"missing parent tag: ${create.parentTagId.map(_.value).getOrElse("")}")
      }).getOrElse(Consequence.success(None))
      values <- list(tagSpace)
      _ <- _reject_duplicate_sibling(values, parent.map(_.id), key)
      path = TagPath.childPath(parent.map(_.path), key)
      id = create.id.map(_tag_id)
      } yield create.copy(id = id, tagSpace = tagSpace, key = key, parentTagId = parent.map(_.id), attributes = create.attributes + ("path" -> path))
    }

  private def _reject_duplicate_sibling(values: Vector[Tag], parent: Option[EntityId], key: String): Consequence[Unit] =
    if (values.exists(x => x.parentTagId.map(_.value) == parent.map(_.value) && x.key == key))
      Consequence.argumentInvalid(s"duplicate sibling tag key: $key")
    else
      Consequence.unit

  private def _normalize_collection(tag: Tag): Tag =
    tag.copy(id = _tag_id(tag.id), parentTagId = tag.parentTagId.map(_tag_id))

  private def _tag_id(id: EntityId): EntityId =
    if (id.collection == TagEntityCollections.Tag)
      id
    else
      id.copy(collection = TagEntityCollections.Tag)

  private def _is_deleted(tag: Tag): Boolean =
    tag.toRecord.getString("aliveness").exists(_.equalsIgnoreCase("dead"))
}

object TagSpace {
  val Operational: String = "operational"
  val Blog: String = "blog"

  def normalize(value: String): String =
    Option(value).map(_.trim).filter(_.nonEmpty).getOrElse(TagRepository.DefaultTagSpace)

  def userSpace(userId: String): String =
    s"user.${normalize(userId)}"
}

object TagTreeCache {
  private val _cache =
    scala.collection.concurrent.TrieMap.empty[String, TagTree]

  def getOrLoad(
    tagSpace: String
  )(
    load: => Consequence[TagTree]
  ): Consequence[TagTree] = {
    val space = TagSpace.normalize(tagSpace)
    _cache.get(space).map(Consequence.success).getOrElse {
      load.map { tree =>
        _cache.put(space, tree)
        tree
      }
    }
  }

  def invalidate(tagSpace: String): Unit =
    _cache.remove(TagSpace.normalize(tagSpace))

  def invalidateAll(): Unit =
    _cache.clear()
}

final case class TagTree(tags: Vector[Tag]) {
  private val byId: Map[String, Tag] =
    tags.map(x => x.id.value -> x).toMap
  private val byEntropy: Map[String, Tag] =
    tags.map(x => x.id.parts.entropy -> x).toMap
  private val byPath: Map[String, Vector[Tag]] =
    tags.groupBy(x => TagPath.normalizePath(x.path))
  private val byKey: Map[String, Vector[Tag]] =
    tags.groupBy(_.key)

  def resolve(ref: String): Consequence[Tag] = {
    val value = Option(ref).map(_.trim).getOrElse("")
    if (value.isEmpty)
      Consequence.argumentMissing("tagRef")
    else {
      val entityId = EntityId.parse(value).toOption
      entityId.flatMap(id => byId.get(id.copy(collection = TagEntityCollections.Tag).value))
        .orElse(byId.get(value))
        .orElse(byEntropy.get(value))
        .map(Consequence.success)
        .getOrElse {
          byPath.get(TagPath.normalizePath(value)) match {
            case Some(xs) => _unique(Some(xs), value)
            case None => _unique(byKey.get(value), value)
          }
        }
    }
  }

  def descendantsOf(tag: Tag, includeSelf: Boolean = true): Vector[Tag] = {
    val children = tags.filter(_.parentTagId.map(_.value).contains(tag.id.value)).sortBy(x => (x.sortOrder.getOrElse(Int.MaxValue), x.key))
    val xs = children.flatMap(x => descendantsOf(x, includeSelf = true))
    if (includeSelf) tag +: xs else xs
  }

  def toRecord: Record =
    Record.dataAuto(
      "data" -> tags.map(_.toRecord),
      "roots" -> tags.filter(_.parentTagId.isEmpty).map(_.toRecord)
    )

  private def _unique(values: Option[Vector[Tag]], ref: String): Consequence[Tag] =
    values match {
      case Some(Vector(x)) => Consequence.success(x)
      case Some(xs) if xs.nonEmpty => Consequence.argumentInvalid(s"ambiguous tag reference across tag spaces: ${xs.map(x => s"${x.tagSpace}:${x.path}").mkString(", ")}")
      case _ => Consequence.operationNotFound(s"tag not found: $ref")
    }
}

object TagTree {
  def create(tags: Vector[Tag]): Consequence[TagTree] =
    _validate_tree(tags).map(_ => TagTree(tags))

  private def _validate_tree(tags: Vector[Tag]): Consequence[Unit] = {
    val byId = tags.map(x => x.id.value -> x).toMap
    val missing = tags.collectFirst {
      case tag if tag.parentTagId.exists(id => !byId.contains(id.value)) =>
        s"missing parent tag: ${tag.parentTagId.map(_.value).getOrElse("")}"
    }
    missing.map(Consequence.argumentInvalid).getOrElse {
      tags.collectFirst {
        case tag if _has_cycle(tag, byId, Set.empty) => s"tag cycle detected: ${tag.id.value}"
      }.map(Consequence.argumentInvalid).getOrElse(Consequence.unit)
    }
  }

  private def _has_cycle(tag: Tag, byId: Map[String, Tag], seen: Set[String]): Boolean =
    tag.parentTagId.exists { parent =>
      val key = parent.value
      seen.contains(key) || byId.get(key).exists(_has_cycle(_, byId, seen + tag.id.value))
    }
}

object TagPath {
  private val KeyRegex = "^[A-Za-z0-9][A-Za-z0-9_-]*(?:\\.[A-Za-z0-9][A-Za-z0-9_-]*)*$".r

  def validateKey(value: String): Consequence[String] =
    Option(value).map(_.trim.toLowerCase(Locale.ROOT)) match {
      case Some(KeyRegex()) => Consequence.success(value.trim.toLowerCase(Locale.ROOT))
      case Some(other) if other.nonEmpty => Consequence.argumentInvalid(s"invalid tag key: $other")
      case _ => Consequence.argumentMissing("key")
    }

  def childPath(parent: Option[String], key: String): String =
    parent.map(normalizePath).filter(_.nonEmpty).map(p => s"$p.$key").getOrElse(key)

  def normalizePath(value: String): String = {
    val raw = Option(value).map(_.trim).getOrElse("")
    raw.stripPrefix(".").stripSuffix(".").replaceAll("\\.+", ".") match {
      case "" => ""
      case x => x
    }
  }
}

final case class TagAttachmentSummary(
  sourceEntityId: String,
  tags: Vector[Tag],
  associations: Vector[Association]
) {
  def toRecord: Record =
    Record.dataAuto(
      "sourceEntityId" -> sourceEntityId,
      "tags" -> tags.map(_.toRecord),
      "associations" -> associations.map(AssociationRecordCodec.toRecord)
    )
}

final class TaggingWorkflow(
  tags: TagRepository = TagRepository.entityStore(),
  associations: AssociationRepository = AssociationRepository.entityStore(AssociationStoragePolicy.tagAttachmentDefault),
  tagSpace: String = "",
  tagSpaces: Vector[String] = Vector.empty
) {
  private val binding =
    AssociationBindingWorkflow(associations, AssociationStoragePolicy.tagAttachmentDefault)

  def attach(
    sourceEntityId: String,
    tagRef: String,
    role: String = "tag",
    sortOrder: Option[Int] = None
  )(using ExecutionContext): Consequence[Association] =
    for {
      tag <- _tree.flatMap(_.resolve(tagRef))
      result <- binding.attachExistingTargetResult(
        sourceEntityId = sourceEntityId,
        domain = AssociationDomain.TagAttachment,
        targetKind = Some("tag"),
        targetEntityId = tag.id,
        role = role,
        sortOrder = sortOrder
      )
    } yield result.association

  def detach(
    sourceEntityId: String,
    tagRef: String,
    role: Option[String] = None
  )(using ExecutionContext): Consequence[Int] =
    for {
      tag <- _tree.flatMap(_.resolve(tagRef))
      values <- associations.list(AssociationFilter(
        domain = AssociationDomain.TagAttachment,
        sourceEntityId = Some(sourceEntityId),
        targetEntityId = Some(tag.id.value),
        targetKind = Some("tag"),
        role = role
      ))
      _ <- values.foldLeft(Consequence.unit)((z, association) => z.flatMap(_ => associations.delete(association)))
    } yield values.size

  def listEntityTags(
    sourceEntityId: String,
    role: Option[String] = None
  )(using ExecutionContext): Consequence[TagAttachmentSummary] =
    for {
      values <- associations.list(AssociationFilter(
        domain = AssociationDomain.TagAttachment,
        sourceEntityId = Some(sourceEntityId),
        targetKind = Some("tag"),
        role = role
      ))
      loaded <- values.foldLeft(Consequence.success(Vector.empty[Tag])) { (z, association) =>
        z.flatMap(xs => _tree.flatMap(_.resolve(association.targetEntityId)).map(xs :+ _))
      }
    } yield TagAttachmentSummary(sourceEntityId, loaded, values)

  def sync(
    sourceEntityId: String,
    tagRefs: Vector[String],
    role: String = "tag"
  )(using ExecutionContext): Consequence[TagAttachmentSummary] =
    for {
      desired <- tagRefs.distinct.foldLeft(Consequence.success(Vector.empty[Tag])) { (z, ref) =>
        z.flatMap(xs => _tree.flatMap(_.resolve(ref)).map(xs :+ _))
      }
      current <- associations.list(AssociationFilter(
        domain = AssociationDomain.TagAttachment,
        sourceEntityId = Some(sourceEntityId),
        targetKind = Some("tag"),
        role = Some(role)
      ))
      desiredIds = desired.map(_.id.value).toSet
      stale = current.filterNot(a => desiredIds.contains(a.targetEntityId))
      _ <- stale.foldLeft(Consequence.unit)((z, association) => z.flatMap(_ => associations.delete(association)))
      created <- desired.zipWithIndex.foldLeft(Consequence.success(Vector.empty[Association])) {
        case (z, (tag, index)) =>
          z.flatMap(xs =>
            binding.attachExistingTargetResult(
              sourceEntityId = sourceEntityId,
              domain = AssociationDomain.TagAttachment,
              targetKind = Some("tag"),
              targetEntityId = tag.id,
              role = role,
              sortOrder = Some(index)
            ).map(result => xs :+ result.association)
          )
      }
    } yield TagAttachmentSummary(sourceEntityId, desired, created)

  def searchSourceIds(
    tagRef: String,
    includeDescendants: Boolean = true,
    role: Option[String] = None
  )(using ExecutionContext): Consequence[Set[String]] =
    for {
      tree <- _tree
      tag <- tree.resolve(tagRef)
      targets = if (includeDescendants) tree.descendantsOf(tag).map(_.id.value).toSet else Set(tag.id.value)
      values <- associations.list(AssociationFilter(
        domain = AssociationDomain.TagAttachment,
        targetKind = Some("tag"),
        role = role
      ))
    } yield values.filter(a => targets.contains(a.targetEntityId)).map(_.sourceEntityId).toSet

  def resolveTagOption(
    tagRef: String
  )(using ExecutionContext): Consequence[Option[Tag]] =
    _tree.flatMap { tree =>
      tree.resolve(tagRef) match {
        case Consequence.Success(tag) => Consequence.success(Some(tag))
        case Consequence.Failure(_) => Consequence.success(None)
      }
    }

  def tagSummaryRecords(
    sourceEntityId: String
  )(using ExecutionContext): Consequence[Vector[Record]] =
    listEntityTags(sourceEntityId).map(_.tags.map(_.toRecord))

  private def _effective_tag_spaces(using ctx: ExecutionContext): Vector[String] =
    {
      val explicit = Vector(tagSpace).filter(_.trim.nonEmpty) ++ tagSpaces
      val configured = explicit ++ ctx.tagSpaces.effective
      val values = if (configured.nonEmpty) configured else Vector(TagRepository.DefaultTagSpace)
      values.map(TagSpace.normalize).filter(_.nonEmpty).distinct
    }

  private def _tree(using ctx: ExecutionContext): Consequence[TagTree] =
    tags.tree(_effective_tag_spaces)
}

object TagRecordCodec {
  def toRecord(tag: Tag): Record =
    _record(
      id = Some(tag.id),
      tagSpace = tag.tagSpace,
      key = tag.key,
      parent = tag.parentTagId,
      path = tag.path,
      usage = tag.usageKind,
      sortorder = tag.sortOrder,
      title = tag.title,
      description = tag.description,
      createdat = Some(tag.createdAt),
      updatedat = Some(tag.updatedAt),
      attributes = tag.attributes
    )

  def toStoreRecord(tag: Tag): Record =
    toRecord(tag)

  def toRecord(create: TagCreate): Record = {
    val path = create.attributes.get("path").getOrElse(TagPath.childPath(None, create.key))
    _record(
      id = create.id,
      tagSpace = create.tagSpace,
      key = create.key,
      parent = create.parentTagId,
      path = path,
      usage = create.usageKind,
      sortorder = create.sortOrder,
      title = create.title,
      description = create.description,
      createdat = None,
      updatedat = None,
      attributes = create.attributes
    )
  }

  def toStoreRecord(create: TagCreate): Record =
    toRecord(create)

  def fromRecord(record: Record): Consequence[Tag] =
    fromStoreRecord(record)

  def fromStoreRecord(record: Record): Consequence[Tag] =
    for {
      id <- EntityId.createC(record)
      key <- _string(record, "key").map(TagPath.validateKey).getOrElse(Consequence.argumentMissing("key"))
      tagSpace = _string(record, "tagSpace", "tag_space").map(TagSpace.normalize).getOrElse(TagRepository.DefaultTagSpace)
      path = _string(record, "path").map(TagPath.normalizePath).getOrElse(TagPath.childPath(None, key))
      usage <- _string(record, "usageKind", "usage_kind").map(TagUsageKind.parse).getOrElse(Consequence.success(TagUsageKind.General))
      createdat <- _instant(record, "createdAt", "created_at").map(Consequence.success).getOrElse(Consequence.argumentMissing("createdAt"))
      updatedat <- _instant(record, "updatedAt", "updated_at").map(Consequence.success).getOrElse(Consequence.argumentMissing("updatedAt"))
    } yield Tag(
      id = id.copy(collection = TagEntityCollections.Tag),
      tagSpace = tagSpace,
      key = key,
      parentTagId = _entity_id(record, "parentTagId", "parent_tag_id").map(_.copy(collection = TagEntityCollections.Tag)),
      path = path,
      usageKind = usage,
      sortOrder = _int(record, "sortOrder", "sort_order"),
      title = _string(record, "title"),
      description = _string(record, "description"),
      createdAt = createdat,
      updatedAt = updatedat,
      attributes = _attributes(record)
    )

  private def _record(
    id: Option[EntityId],
    tagSpace: String,
    key: String,
    parent: Option[EntityId],
    path: String,
    usage: TagUsageKind,
    sortorder: Option[Int],
    title: Option[String],
    description: Option[String],
    createdat: Option[Instant],
    updatedat: Option[Instant],
    attributes: Map[String, String]
  ): Record =
    Record.dataAuto(
      "id" -> id.map(_.value),
      "tagSpace" -> TagSpace.normalize(tagSpace),
      "key" -> key,
      "name" -> key,
      "parentTagId" -> parent.map(_.value),
      "path" -> path,
      "usageKind" -> usage.value,
      "sortOrder" -> sortorder,
      "title" -> title,
      "description" -> description,
      "createdAt" -> createdat.map(_.toString),
      "updatedAt" -> updatedat.map(_.toString),
      "attributes" -> Record.data(attributes.toVector.sortBy(_._1)*)
    )

  private def _string(record: Record, names: String*): Option[String] =
    names.iterator.flatMap(record.getAny).map(_.toString.trim).find(_.nonEmpty)

  private def _int(record: Record, names: String*): Option[Int] =
    names.iterator.flatMap(record.getAny).flatMap {
      case n: java.lang.Number => Some(n.intValue)
      case s: String => scala.util.Try(s.trim.toInt).toOption
      case other => scala.util.Try(other.toString.trim.toInt).toOption
    }.nextOption()

  private def _instant(record: Record, names: String*): Option[Instant] =
    names.iterator.flatMap(record.getAny).flatMap {
      case i: Instant => Some(i)
      case s: String => scala.util.Try(Instant.parse(s.trim)).toOption
      case other => scala.util.Try(Instant.parse(other.toString.trim)).toOption
    }.nextOption()

  private def _entity_id(record: Record, names: String*): Option[EntityId] =
    names.iterator.flatMap(record.getAny).flatMap {
      case id: EntityId => Some(id)
      case s: String => EntityId.parse(s).toOption
      case other => EntityId.parse(other.toString).toOption
    }.nextOption()

  private def _attributes(record: Record): Map[String, String] =
    record.getAny("attributes") match {
      case Some(r: Record) => r.asMap.map { case (k, v) => k -> v.toString }
      case Some(m: scala.collection.Map[?, ?]) => m.collect { case (k, v) => k.toString -> v.toString }.toMap
      case _ => Map.empty
    }
}
