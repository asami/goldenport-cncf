package org.example.phase100.nested.entity.update

import scala.language.strictEquality
import cats.*
import cats.implicits.*
import cats.syntax.all.*
import io.circe.Codec
import io.circe.generic.semiauto.*
import org.goldenport.Consequence
import org.goldenport.ConsequenceT
import org.goldenport.datatype.*
import org.goldenport.schema.Schema
import org.goldenport.record.Record
import org.goldenport.protocol.*
import org.goldenport.protocol.spec.*
import org.goldenport.protocol.operation.*
import org.simplemodeling.model.datatype.*
import org.simplemodeling.model.value.{given, *}
import org.simplemodeling.model.directive.*
import org.goldenport.cncf.entity.*
import cats.data.NonEmptyVector
import io.circe.Json
import org.goldenport.record.Record
import org.simplemodeling.model.directive.Update
import scala.List
import scala.Vector

case class Proposal(payload: Update[Vector[Vector[Json]]], evidence: Update[List[Vector[Record]]], nonempty: Update[NonEmptyVector[Vector[Json]]]) extends EntityPersistableUpdate{
  import Proposal.*




  def schema(): Schema = {
    Proposal.schema
  }


  def withPayload(payload: Update[Vector[Vector[Json]]]): Proposal = {
    copy(payload = payload)
  }


  def withEvidence(evidence: Update[List[Vector[Record]]]): Proposal = {
    copy(evidence = evidence)
  }


  def withNonempty(nonempty: Update[NonEmptyVector[Vector[Json]]]): Proposal = {
    copy(nonempty = nonempty)
  }
  // lenslikeupdate_methods
  // validate_method
  // iri_method
  // properties_method

  def toRecord(): Record = {
    Record.dataAuto(
      "payload" -> _to_external_value(payload),
      "evidence" -> _to_external_value(evidence),
      "nonempty" -> _to_external_value(nonempty)
    )
  }

  def toDisplayRecord(view: String, fields: Vector[String]): Record = {
    val source = toRecord()
    val sourcemap = source.asMap
    val rows = fields.flatMap { field =>
      sourcemap.find { case (key, _) => _normalized_field_name(key) == _normalized_field_name(field) }.map { case (_, value) => field -> value }
    }
    Record.dataAuto(rows*)
  }

  private def _normalized_field_name(name: String): String =
    name.filter(_.isLetterOrDigit).toLowerCase(java.util.Locale.ROOT)

  def toDataStore(): Record = {
    Record.dataAuto(
      "payload" -> _to_data_store_value(payload),
      "evidence" -> _to_data_store_value(evidence),
      "nonempty" -> _to_data_store_value(nonempty)
    )
  }
  private def _logical_store_record: Record = {
    Record(Vector(
      org.goldenport.record.Field("payload", org.goldenport.record.Field.Value.Single(payload)),
      org.goldenport.record.Field("evidence", org.goldenport.record.Field.Value.Single(evidence)),
      org.goldenport.record.Field("nonempty", org.goldenport.record.Field.Value.Single(nonempty))
    ))
  }
  private def _to_external_value(v: Any): Any = v match {
    case m if java.util.Objects.isNull(m) => null
    case m: String => m
    case m: java.lang.Number => m
    case m: java.lang.Boolean => m
    case m: java.lang.Character => m.toString
    case m: org.goldenport.datatype.I18nLabel => m.toI18nString.toRecord
    case m: org.goldenport.datatype.I18nTitle => m.toI18nString.toRecord
    case m: org.goldenport.datatype.I18nBrief => m.toI18nString.toRecord
    case m: org.goldenport.datatype.I18nSummary => m.toI18nString.toRecord
    case m: org.goldenport.datatype.I18nDescription => m.toI18nString.toRecord
    case m: org.goldenport.datatype.I18nText => m.toI18nString.toRecord
    case m: org.goldenport.datatype.MimeType => m.print
    case m: java.nio.charset.Charset => m.name()
    case m: java.util.Locale => m.toLanguageTag
    case m: java.util.TimeZone => m.getID
    case m: org.goldenport.value.ContentBody => m.value
    case m: org.goldenport.value.ContentMarkup => m.value
    case m: org.goldenport.datatype.ObjectId => _to_external_value(m.id)
    case _: org.simplemodeling.model.directive.Condition.Any.type => null
    case org.simplemodeling.model.directive.Condition.Is(expected) => _to_external_value(expected)
    case org.simplemodeling.model.directive.Condition.In(candidates) => candidates.toVector.map(_to_external_value)
    case m: Record => m
    case m: org.goldenport.value.NameAttributes => Record.dataAuto("name" -> _to_external_value(m.name), "label" -> _to_external_value(m.label), "title" -> _to_external_value(m.title))
    case m: org.goldenport.value.DescriptiveAttributes => Record.dataAuto("headline" -> _to_external_value(m.headline), "summary" -> _to_external_value(m.summary), "description" -> _to_external_value(m.description))
    case m: org.goldenport.value.ContentReferenceOccurrence => m.toRecord()
    case m: org.goldenport.value.ContentAttributes => m.toRecord()
    case m: org.simplemodeling.model.value.LifecycleAttributes => Record.dataAuto("createdAt" -> _to_external_value(m.createdAt), "updatedAt" -> _to_external_value(m.updatedAt), "createdBy" -> _to_external_value(m.createdBy), "updatedBy" -> _to_external_value(m.updatedBy), "postStatus" -> _to_external_value(m.postStatus), "aliveness" -> _to_external_value(m.aliveness))
    case m: org.simplemodeling.model.value.PublicationAttributes => Record.dataAuto("publishAt" -> _to_external_value(m.publishAt), "publicAt" -> _to_external_value(m.publicAt), "closeAt" -> _to_external_value(m.closeAt), "startAt" -> _to_external_value(m.startAt), "endAt" -> _to_external_value(m.endAt))
    case m: org.simplemodeling.model.value.SecurityAttributes => Record.dataAuto("ownerId" -> _to_external_value(m.ownerId), "groupId" -> _to_external_value(m.groupId), "rights" -> _to_external_value(m.rights), "privilegeId" -> _to_external_value(m.privilegeId))
    case m: org.simplemodeling.model.value.SecurityAttributes.Rights => Record.dataAuto("owner" -> _to_external_value(m.owner), "group" -> _to_external_value(m.group), "other" -> _to_external_value(m.other))
    case m: org.simplemodeling.model.value.SecurityAttributes.Rights.Permissions => Record.dataAuto("read" -> _to_external_value(m.read), "write" -> _to_external_value(m.write), "execute" -> _to_external_value(m.execute))
    case m: org.simplemodeling.model.value.ResourceAttributes => Record.dataAuto("activatedAt" -> _to_external_value(m.activatedAt), "deactivatedAt" -> _to_external_value(m.deactivatedAt), "expiresAt" -> _to_external_value(m.expiresAt), "activationStatus" -> _to_external_value(m.activationStatus))
    case m: org.simplemodeling.model.value.AuditAttributes => Record.empty
    case m: org.simplemodeling.model.value.MediaAttributes => Record.dataAuto("url" -> _to_external_value(m.url), "images" -> _to_external_value(m.images), "audios" -> _to_external_value(m.audios), "videos" -> _to_external_value(m.videos), "attachments" -> _to_external_value(m.atathments))
    case m: org.simplemodeling.model.value.ContextualAttributes => Record.empty
    case m: org.simplemodeling.model.value.SimpleObjectContent => Record.dataAuto("nameAttributes" -> _to_external_value(m.nameAttributes), "descriptiveAttributes" -> _to_external_value(m.descriptiveAttributes), "contentAttributes" -> _to_external_value(m.contentAttributes), "lifecycleAttributes" -> _to_external_value(m.lifecycleAttributes), "publicationAttributes" -> _to_external_value(m.publicationAttributes), "securityAttributes" -> _to_external_value(m.securityAttributes), "resourceAttributes" -> _to_external_value(m.resourceAttributes), "auditAttributes" -> _to_external_value(m.auditAttributes), "mediaAttributes" -> _to_external_value(m.mediaAttributes), "contextualAttribute" -> _to_external_value(m.contextualAttribute))
    case m: org.simplemodeling.model.value.Image => Record.dataAuto("id" -> _to_external_value(m.id), "simpleobject" -> _to_external_value(m.simpleobject))
    case m: org.simplemodeling.model.value.Audio => Record.dataAuto("id" -> _to_external_value(m.id), "simpleobject" -> _to_external_value(m.simpleobject))
    case m: org.simplemodeling.model.value.Video => Record.dataAuto("id" -> _to_external_value(m.id), "simpleobject" -> _to_external_value(m.simpleobject))
    case m: org.simplemodeling.model.value.Attachment => Record.dataAuto("id" -> _to_external_value(m.id), "simpleobject" -> _to_external_value(m.simpleobject))
    case m: org.simplemodeling.model.value.NominalScalar => _to_external_value(m.value)
    case m: org.goldenport.record.RecordPresentable => m.toRecord()
    case m: Option[?] => m.map(_to_external_value)
    case m: cats.data.NonEmptyVector[?] => m.toVector.map(_to_external_value)
    case m: Seq[?] => m.map(_to_external_value)
    case m: Set[?] => m.toVector.map(_to_external_value)
    case m: Array[?] => m.toVector.map(_to_external_value)
    case m: Map[?, ?] => m.iterator.map { case (k, value) => k.toString -> _to_external_value(value) }.toMap
    case m: org.goldenport.text.Presentable => m.print
    case other => other.toString
  }

  private def _to_data_store_value(v: Any): Any = v match {
    case _: org.simplemodeling.model.directive.Update.Noop.type => org.simplemodeling.model.directive.Update.Noop
    case _: org.simplemodeling.model.directive.Update.SetNull.type => org.simplemodeling.model.directive.Update.SetNull
    case org.simplemodeling.model.directive.Update.SetValue(value) => org.simplemodeling.model.directive.Update.SetValue(_to_data_store_value(value))
    case m: org.goldenport.datatype.I18nLabel => org.goldenport.convert.StringEncoder.encodeForStorage(m)
    case m: org.goldenport.datatype.I18nTitle => org.goldenport.convert.StringEncoder.encodeForStorage(m)
    case m: org.goldenport.datatype.I18nBrief => org.goldenport.convert.StringEncoder.encodeForStorage(m)
    case m: org.goldenport.datatype.I18nSummary => org.goldenport.convert.StringEncoder.encodeForStorage(m)
    case m: org.goldenport.datatype.I18nDescription => org.goldenport.convert.StringEncoder.encodeForStorage(m)
    case m: org.goldenport.datatype.I18nText => org.goldenport.convert.StringEncoder.encodeForStorage(m)
    case m: org.goldenport.datatype.MimeType => m.print
    case m: java.nio.charset.Charset => m.name()
    case m: java.util.Locale => m.toLanguageTag
    case m: java.util.TimeZone => m.getID
    case m: org.goldenport.value.ContentBody => m.value
    case m: org.goldenport.value.ContentMarkup => m.value
    case m: org.simplemodeling.model.statemachine.StateMachine => m.dbValue
    case m: org.simplemodeling.model.powertype.Powertype => m.dbValue.getOrElse(m.value)
    case m: org.goldenport.value.NameAttributes => Record.dataAuto("name" -> _to_data_store_value(m.name), "label" -> _to_data_store_value(m.label), "title" -> _to_data_store_value(m.title))
    case m: org.goldenport.value.DescriptiveAttributes => Record.dataAuto("headline" -> _to_data_store_value(m.headline), "summary" -> _to_data_store_value(m.summary), "description" -> _to_data_store_value(m.description))
    case m: org.goldenport.value.ContentAttributes => _to_external_value(m)
    case m: org.simplemodeling.model.value.LifecycleAttributes => _to_external_value(m)
    case m: org.simplemodeling.model.value.PublicationAttributes => _to_external_value(m)
    case m: org.simplemodeling.model.value.SecurityAttributes => _to_external_value(m)
    case m: org.simplemodeling.model.value.ResourceAttributes => _to_external_value(m)
    case m: org.simplemodeling.model.value.AuditAttributes => _to_external_value(m)
    case m: org.simplemodeling.model.value.MediaAttributes => _to_external_value(m)
    case m: org.simplemodeling.model.value.ContextualAttributes => _to_external_value(m)
    case m: org.simplemodeling.model.value.SimpleObjectContent => _to_external_value(m)
    case m: Option[?] => m.map(_to_data_store_value)
    case m: cats.data.NonEmptyVector[?] => m.toVector.map(_to_data_store_value)
    case m: Seq[?] => m.map(_to_data_store_value)
    case m: Set[?] => m.toVector.map(_to_data_store_value)
    case m: Array[?] => m.toVector.map(_to_data_store_value)
    case m: Map[?, ?] => m.iterator.map { case (k, value) => k.toString -> _to_data_store_value(value) }.toMap
    case other => _to_external_value(other)
  }

  private def _permission_json(rights: org.simplemodeling.model.value.SecurityAttributes.Rights): String = {
    def p(x: org.simplemodeling.model.value.SecurityAttributes.Rights.Permissions): io.circe.Json =
      io.circe.Json.obj("read" -> io.circe.Json.fromBoolean(x.read), "write" -> io.circe.Json.fromBoolean(x.write), "execute" -> io.circe.Json.fromBoolean(x.execute))
    io.circe.Json.obj("owner" -> p(rights.owner), "group" -> p(rights.group), "other" -> p(rights.other)).noSpaces
  }

}

object Proposal {
  final val PROP_PAYLOAD = "payload"
  final val INPUT_KEYS_PAYLOAD: List[String] = List("payload").distinct
  final val PROP_EVIDENCE = "evidence"
  final val INPUT_KEYS_EVIDENCE: List[String] = List("evidence").distinct
  final val PROP_NONEMPTY = "nonempty"
  final val INPUT_KEYS_NONEMPTY: List[String] = List("nonempty").distinct

  val schema: org.goldenport.schema.Schema = org.example.phase100.nested.entity.Proposal.schema

  given CanEqual[Proposal,Proposal] = CanEqual.derived
  // Domain semantic equality = equality of identity
  given Eq[Proposal] = Eq.fromUniversalEquals
  given org.goldenport.convert.ValueReader[Proposal] with
    def readC(v: Any): Consequence[Proposal] = v match
      case m: Proposal => { Consequence.success(m) }
      case m: Record => createC(m)
      case _ => Consequence.valueInvalid(v, org.goldenport.schema.XString)
  import io.circe.generic.auto.*
  private given _declared_record_json_codec: io.circe.Codec[org.goldenport.record.Record] = io.circe.Codec.from(
    io.circe.Decoder.instance(cursor => org.goldenport.schema.XSemiStructuredData.fromJson(cursor.value) match {
      case org.goldenport.Consequence.Success(record) => Right(record)
      case org.goldenport.Consequence.Failure(_) => Left(io.circe.DecodingFailure("Invalid declared Record JSON", cursor.history))
    }),
    io.circe.Encoder.instance(record => org.goldenport.schema.XSemiStructuredData.toJson(record).TAKE)
  )
  private val _store_row_mapping: EntityStoreRowMapping = EntityStoreRowMapping(
    fields = Vector(
      EntityStoreField("payload", "payload", org.goldenport.schema.ValueDomain(org.goldenport.schema.XJson, org.goldenport.schema.Multiplicity.ZeroMore), EntityStoreFieldBinding.array(EntityStoreFieldBinding.array(EntityStoreFieldBinding.json))),
      EntityStoreField("evidence", "evidence", org.goldenport.schema.ValueDomain(org.goldenport.schema.XSemiStructuredData, org.goldenport.schema.Multiplicity.ZeroMore), EntityStoreFieldBinding.array(EntityStoreFieldBinding.array(EntityStoreFieldBinding.semiStructuredData))),
      EntityStoreField("nonempty", "nonempty", org.goldenport.schema.ValueDomain(org.goldenport.schema.XJson, org.goldenport.schema.Multiplicity.OneMore), EntityStoreFieldBinding.array(EntityStoreFieldBinding.array(EntityStoreFieldBinding.json)))
    ),
    physicalFields = (Set() ++ (org.goldenport.cncf.entity.SimpleEntityStorageShapePolicy.managementLogicalFields ++ org.goldenport.cncf.entity.SimpleEntityStorageShapePolicy.securityIdentityLogicalFields ++ Vector("name", "permission", "revision")).flatMap(name => Vector(name, org.goldenport.cncf.entity.SimpleEntityStorageShapePolicy.targetName(name))).toSet ++ Set("content_byte_size", "content_digest", "content_charset", "content_storage", "content_ref", "contentByteSize", "contentDigest", "contentCharset", "contentStorage", "contentRef")).diff(Set("payload", "evidence", "nonempty"))
  )
  val collectionId: EntityCollectionId = EntityCollectionId("major", "minor", "proposal")
  given EntityPersistentUpdate[Proposal] with
    override def storeRowMapping: Option[EntityStoreRowMapping] = { Some(_store_row_mapping) }
    override def toLogicalStoreRecord(e: Proposal): Consequence[Record] = { Consequence.success(e._logical_store_record) }
    def toRecord(e: Proposal): Record = e.toRecord()
    def fromRecord(r: Record): Consequence[Proposal] = createC(r)
    override def toStoreRecord(e: Proposal): Record = e.toDataStore()
    override def fromStoreRecord(r: Record): Consequence[Proposal] = createC(r)
    def collection(e: Proposal): EntityCollectionId = collectionId

  private def _record_get_as_c[A](
    record: Record,
    keys: List[String]
  )(using vr: org.goldenport.convert.ValueReader[A]): Consequence[Option[A]] = {
    keys.foldLeft(Consequence.success(Option.empty[A])) { (z, key) =>
      z.flatMap {
        case s @ Some(_) => Consequence.success(s)
        case None => record.getAsC[A](key)
      }
    }
  }

  private def _record_get_update_set_null[A](
    record: Record,
    keys: List[String]
  ): Option[org.simplemodeling.model.directive.Update[A]] =
    keys.iterator.flatMap(record.getAny).collectFirst {
      case _: org.simplemodeling.model.directive.Update.SetNull.type => org.simplemodeling.model.directive.Update.setNull[A]
    }

  private def _record_get_as_context_c[A](
    record: Record,
    keys: List[String]
  )(using vr: org.goldenport.convert.ValueReader[A], ctx: org.goldenport.context.ExecutionContext): Consequence[Option[A]] = {
    keys.foldLeft(Consequence.success(Option.empty[A])) { (z, key) =>
      z.flatMap {
        case s @ Some(_) => Consequence.success(s)
        case None => record.getAsContextC[A](key)
      }
    }
  }

  private def _record_with_derived_target_aliases(
    record: Record,
    target: String,
    keys: List[String]
  ): Record = {
    val current = record.getAny(target)
    val alias = keys.iterator.filterNot(_ == target).flatMap(record.getAny).nextOption()
    if (current.isEmpty && alias.isDefined) record ++ Record.dataAuto(target -> alias.get) else record
  }

  private def _record_get_record(
    record: Record,
    keys: List[String]
  ): Consequence[Option[Record]] = {
    keys.foldLeft(Consequence.success(Option.empty[Record])) { (z, key) =>
      z.flatMap {
        case s @ Some(_) => Consequence.success(s)
        case None =>
          record.getAny(key) match {
            case Some(m: Record) => Consequence.success(Some(m))
            case Some(other) => Consequence.valueInvalid(other, org.goldenport.schema.XString)
            case None => Consequence.success(None)
          }
      }
    }
  }

  private def _decode_ordered_c[A](value: Any)(decode: Any => Consequence[A]): Consequence[Vector[A]] = {
    val values = value match {
      case xs: Seq[?] => Some(xs.toVector)
      case xs: Array[?] => Some(xs.toVector)
      case xs: cats.data.NonEmptyVector[?] => Some(xs.toVector)
      case _ => None
    }
    values match {
      case Some(xs) => xs.foldLeft(Consequence.success(Vector.empty[A])) { (result, item) =>
        result.flatMap(values => decode(item).map(values :+ _))
      }
      case None => Consequence.valueInvalid("Expected declared ordered collection")
    }
  }

  private def _record_get_nested_c[A](record: Record, keys: List[String])(decode: Any => Consequence[A]): Consequence[Option[A]] = {
    keys.foldLeft(Consequence.success(Option.empty[A])) { (result, key) =>
      result.flatMap {
        case value @ Some(_) => Consequence.success(value)
        case None => record.getAny(key) match {
          case Some(value) => decode(value).map(Some(_))
          case None => Consequence.success(None)
        }
      }
    }
  }
  private def _record_get_vector_as_c[A](
    record: Record,
    keys: List[String]
  )(using vr: org.goldenport.convert.ValueReader[A]): Consequence[Option[Vector[A]]] = {
    def decode_all(xs: Seq[?]): Consequence[Vector[A]] =
      xs.foldLeft(Consequence.success(Vector.empty[A])) { (z, x) =>
        z.flatMap(zs => vr.readC(x).map(a => zs :+ a))
      }
    keys.foldLeft(Consequence.success(Option.empty[Vector[A]])) { (z, key) =>
      z.flatMap {
        case s @ Some(_) => Consequence.success(s)
        case None =>
          record.getAny(key) match {
            case Some(xs: Seq[?]) => decode_all(xs).map(Some(_))
            case Some(xs: Array[?]) => decode_all(xs.toVector).map(Some(_))
            case Some(other) => vr.readC(other).map(x => Some(Vector(x)))
            case None => Consequence.success(None)
          }
      }
    }
  }

  private def _record_get_vector_as_context_c[A](
    record: Record,
    keys: List[String]
  )(using vr: org.goldenport.convert.ValueReader[A], ctx: org.goldenport.context.ExecutionContext): Consequence[Option[Vector[A]]] = {
    def decode_all(xs: Seq[?]): Consequence[Vector[A]] =
      xs.foldLeft(Consequence.success(Vector.empty[A])) { (z, x) =>
        z.flatMap(zs => vr.readContextC(x).map(a => zs :+ a))
      }
    keys.foldLeft(Consequence.success(Option.empty[Vector[A]])) { (z, key) =>
      z.flatMap {
        case s @ Some(_) => Consequence.success(s)
        case None =>
          record.getAny(key) match {
            case Some(xs: Seq[?]) => decode_all(xs).map(Some(_))
            case Some(xs: Array[?]) => decode_all(xs.toVector).map(Some(_))
            case Some(other) => vr.readContextC(other).map(x => Some(Vector(x)))
            case None => Consequence.success(None)
          }
      }
    }
  }

  private def _record_get_vector_of_record_c[A](
    record: Record,
    keys: List[String]
  )(decode: Record => Consequence[A]): Consequence[Option[Vector[A]]] = {
    def decode_all(xs: Seq[?]): Consequence[Vector[A]] =
      xs.foldLeft(Consequence.success(Vector.empty[A])) { (z, x) =>
        z.flatMap { zs =>
          x match {
            case m: Record => decode(m).map(a => zs :+ a)
            case other => Consequence.valueInvalid(other, org.goldenport.schema.XString)
          }
        }
      }
    keys.foldLeft(Consequence.success(Option.empty[Vector[A]])) { (z, key) =>
      z.flatMap {
        case s @ Some(_) => Consequence.success(s)
        case None =>
          record.getAny(key) match {
            case Some(xs: Seq[?]) => decode_all(xs).map(Some(_))
            case Some(xs: Array[?]) => decode_all(xs.toVector).map(Some(_))
            case Some(other) => Consequence.valueInvalid(other, org.goldenport.schema.XString)
            case None => Consequence.success(None)
          }
      }
    }
  }

  case class Builder(payload: Option[Update[Vector[Vector[Json]]]] = None, evidence: Option[Update[List[Vector[Record]]]] = None, nonempty: Option[Update[NonEmptyVector[Vector[Json]]]] = None, _failures: Vector[Consequence.Failure[_]] = Vector.empty) {

    def withPayload(payload: Vector[Vector[Json]]): Proposal.Builder = {
      copy(payload = Some(Update.set(payload)), _failures = _failures)
    }

    def withPayload(payload: Update[Vector[Vector[Json]]]): Proposal.Builder = {
      copy(payload = Some(payload), _failures = _failures)
    }


    def withEvidence(evidence: List[Vector[Record]]): Proposal.Builder = {
      copy(evidence = Some(Update.set(evidence)), _failures = _failures)
    }

    def withEvidence(evidence: Update[List[Vector[Record]]]): Proposal.Builder = {
      copy(evidence = Some(evidence), _failures = _failures)
    }


    def withNonempty(nonempty: NonEmptyVector[Vector[Json]]): Proposal.Builder = {
      copy(nonempty = Some(Update.set(nonempty)), _failures = _failures)
    }

    def withNonempty(nonempty: Update[NonEmptyVector[Vector[Json]]]): Proposal.Builder = {
      copy(nonempty = Some(nonempty), _failures = _failures)
    }


    def buildC(): Consequence[Proposal] = {
      Consequence.ValidatedBuilder.run { builder =>
        val v0 = builder.read(Consequence.success(payload.getOrElse(Update.noop[Vector[Vector[Json]]])))
        val v1 = builder.read(Consequence.success(evidence.getOrElse(Update.noop[List[Vector[Record]]])))
        val v2 = builder.read(Consequence.success(nonempty.getOrElse(Update.noop[NonEmptyVector[Vector[Json]]])))
        builder.build {
          Proposal(
            v0.value,
            v1.value,
            v2.value
          )
        }
      }
    }


    def build(): Proposal = {
      buildC().TAKE
    }


    def buildC(record: Record): Consequence[Proposal] = {
      def _build_changes_(record: Record): Consequence[Proposal] = {
        Consequence.ValidatedBuilder.run { builder =>
          val v0 = builder.read(_record_get_update_set_null[scala.Vector[scala.Vector[io.circe.Json]]](record, INPUT_KEYS_PAYLOAD) match {
            case Some(s) => Consequence.success(s)
            case None => _record_get_nested_c[scala.Vector[scala.Vector[io.circe.Json]]](record, INPUT_KEYS_PAYLOAD)(value => _decode_ordered_c[scala.Vector[io.circe.Json]](value)(item => _decode_ordered_c[io.circe.Json](item)(item => summon[org.goldenport.convert.ValueReader[io.circe.Json]].readC(item)))).flatMap { case Some(value) => Consequence.success(Update.set(value)); case None => Consequence.success(payload.getOrElse(Update.noop[scala.Vector[scala.Vector[io.circe.Json]]])) }
          })
          val v1 = builder.read(_record_get_update_set_null[scala.List[scala.Vector[org.goldenport.record.Record]]](record, INPUT_KEYS_EVIDENCE) match {
            case Some(s) => Consequence.success(s)
            case None => _record_get_nested_c[scala.List[scala.Vector[org.goldenport.record.Record]]](record, INPUT_KEYS_EVIDENCE)(value => _decode_ordered_c[scala.Vector[org.goldenport.record.Record]](value)(item => _decode_ordered_c[org.goldenport.record.Record](item)(item => (item match { case record: Record => Consequence.success(record); case _ => Consequence.valueInvalid("Expected declared Record element") }))).map(_.toList)).flatMap { case Some(value) => Consequence.success(Update.set(value)); case None => Consequence.success(evidence.getOrElse(Update.noop[scala.List[scala.Vector[org.goldenport.record.Record]]])) }
          })
          val v2 = builder.read(_record_get_update_set_null[cats.data.NonEmptyVector[scala.Vector[io.circe.Json]]](record, INPUT_KEYS_NONEMPTY) match {
            case Some(s) => Consequence.success(s)
            case None => _record_get_nested_c[cats.data.NonEmptyVector[scala.Vector[io.circe.Json]]](record, INPUT_KEYS_NONEMPTY)(value => _decode_ordered_c[scala.Vector[io.circe.Json]](value)(item => _decode_ordered_c[io.circe.Json](item)(item => summon[org.goldenport.convert.ValueReader[io.circe.Json]].readC(item))).flatMap(values => Consequence.successOrPropertyNotFound("nested collection", cats.data.NonEmptyVector.fromVector(values)))).flatMap { case Some(value) => Consequence.success(Update.set(value)); case None => Consequence.success(nonempty.getOrElse(Update.noop[cats.data.NonEmptyVector[scala.Vector[io.circe.Json]]])) }
          })
          builder.build {
            Proposal(
              v0.value,
              v1.value,
              v2.value
            )
          }
        }
      }
      _build_changes_(org.simplemodeling.model.directive.Update.toChangesRecord(record))
    }



    def build(record: Record): Proposal = {
      buildC(record).TAKE
    }
  }

  object Builder {
  }

  def createC(): Consequence[Proposal] = {
    val builder = Builder()
    builder.buildC()
  }


  def create(): Proposal = {
    createC().TAKE
  }



  def createC(record: Record): Consequence[Proposal] = {
    val builder = Builder()
    builder.buildC(record)
  }



  def create(record: Record): Proposal = {
    createC(record).TAKE
  }

}

